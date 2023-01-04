package org.andbootmgr.app

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.http2.StreamResetException
import okio.buffer
import okio.sink
import org.andbootmgr.app.util.ConfigFile
import org.andbootmgr.app.util.SDUtils
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit

class UpdateFlowWizardPageFactory(private val vm: WizardActivityState) {
    fun get(): List<IWizardPage> {
        val c = UpdateFlowDataHolder(vm)
        val noobMode = c.vm.activity.getSharedPreferences("abm", 0).getBoolean("noob_mode", BuildConfig.DEFAULT_NOOB_MODE)
        return listOf(WizardPage("start",
            NavButton(vm.activity.getString(R.string.cancel)) {
                it.startActivity(Intent(it, MainActivity::class.java))
                it.finish()
            },
            if (noobMode) NavButton("") {} else NavButton(vm.activity.getString(R.string.local_update)) { vm.navigate("local") }
        ) {
            Start(c)
        }, WizardPage("local",
            NavButton(vm.activity.getString(R.string.cancel)) {
                it.startActivity(Intent(it, MainActivity::class.java))
                it.finish()
            },
            NavButton(vm.activity.getString(R.string.online_update)) { vm.navigate("start") }
        ) {
            Local(c)
        }, WizardPage("flash",
            NavButton("") {},
            NavButton("") {}
        ) {
            Flash(c)
        })
    }
}

private class UpdateFlowDataHolder(val vm: WizardActivityState) {
    val client = OkHttpClient().newBuilder().readTimeout(1L, TimeUnit.HOURS).build()
    var json: JSONObject? = null
    var currentDl: Call? = null
    var sbootfile: Uri? by mutableStateOf(null)

    var e: ConfigFile? = null
    var ef: File? = null
    var hasUpdate = false
    var hasChecked by mutableStateOf(false)
    val partMapping = HashMap<Int, String>()
    var updateBoot: String? = null
    var updateJson: String? = null
    var script: String? = null
    val sparse = ArrayList<Int>()
}

@Composable
private fun Start(u: UpdateFlowDataHolder) {
    LaunchedEffect(Unit) {
        val entries = mutableMapOf<ConfigFile, File>()
        val list = SuFile.open(u.vm.logic.abmEntries.absolutePath).listFiles()
        for (i in list!!) {
            try {
                entries[ConfigFile.importFromFile(i)] = i
            } catch (e: ActionAbortedCleanlyError) {
                Log.e("ABM", Log.getStackTraceString(e))
            }
        }
        val toFind = u.vm.activity.intent.getStringExtra("entryFilename") ?: "null"
        u.e = entries.entries.find { it.value.absolutePath == toFind }!!.also { u.ef = it.value }.key

        Thread {
            try {
                val json_text =
                    URL(u.e!!["xupdate"]).readText()
                u.json = JSONTokener(json_text).nextValue() as JSONObject
            } catch (e: Exception) {
                Log.e("ABM", Log.getStackTraceString(e))
            }
            if (u.json != null) {
                u.hasUpdate = u.json!!.optBoolean("hasUpdate", false)
            }
            u.hasChecked = true
        }.start()
    }
    Column {
        if (u.hasChecked) {
            if (u.json == null) {
                Text(stringResource(R.string.update_check_failed))
            } else {
                if (u.hasUpdate) {
                    Text(stringResource(id = R.string.found_update))
                    Button(onClick = {
                        try {
                            u.run {
                                val j = json!!
                                if (j.has("boot") && j.has("script")) {
                                    updateBoot = j.getString("boot")
                                    script = j.getString("script")
                                }
                                if (j.has("parts")) {
                                    val sp = u.e!!["xpart"]!!.split(":")
                                    val p = j.getJSONObject("parts")
                                    for (k in p.keys()) {
                                        val v = p.getString(k)
                                        partMapping[sp[k.toInt()].toInt()] = v
                                    }
                                }
                                updateJson = j.optString("updateJson")
                                val a = j.optJSONArray("sparse")
                                if (a != null) {
                                    for (i in 0 until a.length()) {
                                        val v = a.getInt(i)
                                        sparse.add(v)
                                    }
                                }
                            }
                            u.vm.navigate("flash")
                        } catch (e: Exception) {
                            Log.e("ABM", u.json?.toString() ?: "(null json)")
                            Log.e("ABM", Log.getStackTraceString(e))
                            u.json = null
                        }
                    }) {
                        Text(stringResource(R.string.install_update))
                    }
                } else {
                    Text(stringResource(R.string.up2date))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Local(u: UpdateFlowDataHolder) {
    Column(verticalArrangement = Arrangement.SpaceEvenly) {
        Column {
            Text(stringResource(R.string.local_updater_1))
            Text(stringResource(R.string.local_updater_2))
            Text(stringResource(R.string.local_updater_3))
        }
        Column {
            var text by remember { mutableStateOf("") }
            var error by remember { mutableStateOf(true) }
            TextField(modifier = Modifier.fillMaxWidth(), value = text, isError = error, onValueChange = {
                text = it
                error = text.isBlank() || !File(u.vm.logic.assetDir, "Scripts/add_os/${u.vm.deviceInfo!!.codename}/${text}").exists()
            }, label = {
                Text(stringResource(R.string.script_name))
            })
            if (u.sbootfile == null) {
                Text(stringResource(R.string.no_file_selected))
                Button(onClick = { u.vm.activity.chooseFile("*/*") {
                    u.sbootfile = it
                } }) {
                    Text(stringResource(id = R.string.choose_file))
                }
            } else {
                Text(stringResource(R.string.file_selected))
                Button(onClick = { u.sbootfile = null }) {
                    Text(stringResource(id = R.string.undo))
                }
                Button(onClick = {
                    if (!error) {
                        u.hasUpdate = false
                        u.script = text
                        u.vm.navigate("flash")
                    }
                }, enabled = !error) {
                    Text(stringResource(R.string.install_update))
                }
            }
        }
    }
}

private fun dlFile(u: UpdateFlowDataHolder, l: String): File? {
    val downloadedFile = File(u.vm.logic.cacheDir, System.currentTimeMillis().toString())
    try {
        val request =
            Request.Builder().url(l).build()
        u.currentDl = u.client.newCall(request)
        val response = u.currentDl!!.execute()

        val sink = downloadedFile.sink().buffer()
        try {
            sink.writeAll(response.body!!.source())
        } catch (e: StreamResetException) {
            if (e.message == "stream was reset: CANCEL")
                throw ActionAbortedCleanlyError(Exception("The installation was canceled. Nothing has been changed."))
            else
                throw e
        }
        sink.close()

        if (u.currentDl!!.isCanceled())
            throw ActionAbortedCleanlyError(Exception("The installation was canceled. Nothing has been changed."))

        u.currentDl = null
        return downloadedFile
    } catch (e: ActionAbortedCleanlyError) {
        throw e
    } catch (e: Exception) {
        Log.e("ABM", Log.getStackTraceString(e))
        downloadedFile.delete()
        u.currentDl = null
        return null
    }
}

@Composable
private fun Flash(u: UpdateFlowDataHolder) {
    Terminal(u.vm) { terminal ->
        val sp = u.e!!["xpart"]!!.split(":")

        if (u.hasUpdate) { // online
            u.vm.btnsOverride = true
            u.vm.nextText.value = u.vm.activity.getString(R.string.cancel)
            u.vm.onNext.value = { u.currentDl?.cancel() }
            try {
                var bootfile: File? = null
                if (!u.updateBoot.isNullOrBlank()) {
                    terminal.add("-- Downloading updated boot image...")
                    bootfile = dlFile(u, u.updateBoot!!)
                    if (bootfile == null) {
                        terminal.add("Download failed, trying again...")
                        bootfile = dlFile(u, u.updateBoot!!)
                        if (bootfile == null) {
                            terminal.add("Download failed, giving up.")
                            throw ActionAbortedCleanlyError(Exception("The installation has been canceled. Nothing has been changed."))
                        }
                    }
                }
                val pmap = HashMap<Int, File>()
                for (p in u.partMapping.entries) {
                    terminal.add("Downloading updated partition image ...")
                    var f = dlFile(u, p.value)
                    if (f == null) {
                        terminal.add("Download failed, trying again...")
                        f = dlFile(u, p.value)
                        if (f == null) {
                            terminal.add("Download failed, giving up.")
                            bootfile?.delete()
                            pmap.values.forEach { it.delete() }
                            throw ActionAbortedCleanlyError(Exception("The installation has been canceled. Nothing has been changed."))
                        }
                    }
                    pmap[p.key] = f
                }
                u.vm.nextText.value = ""
                u.vm.onNext.value = {}

                for (p in u.partMapping.entries) {
                    val v = sp.find { p.key.toString() == it }
                    terminal.add("-- Flashing partition $v...")
                    val k = u.vm.deviceInfo!!.pbdev + p.key
                    val f2 = pmap[p.key]!!
                    val tp = File(k)
                    if (u.sparse.contains(p.key)) {
                        val result2 = Shell.cmd(
                            File(
                                u.vm.logic.assetDir,
                                "Toolkit/simg2img"
                            ).absolutePath + " ${f2.absolutePath} ${tp.absolutePath}"
                        ).to(terminal).exec()
                        if (!result2.isSuccess) {
                            throw IllegalStateException("simg2img returned failure")
                        }
                    } else {
                        u.vm.copyPriv(f2.inputStream(), tp)
                    }
                    terminal.add("Done.")

                }
                if (!u.updateBoot.isNullOrBlank()) {
                    terminal.add("-- Patching update...")
                    var cmd = "FORMATDATA=false " + File(
                        u.vm.logic.assetDir,
                        "Scripts/add_os/${u.vm.deviceInfo!!.codename}/${u.script}"
                    ).absolutePath + " ${u.ef!!.nameWithoutExtension} ${bootfile!!.absolutePath}"
                    for (i in sp) {
                        cmd += " " + i
                    }
                    val r = Shell.cmd(SDUtils.umsd(SDUtils.generateMeta(u.vm.deviceInfo.bdev, u.vm.deviceInfo.pbdev)!!) + " && " + cmd).to(terminal).exec()
                    bootfile.delete()
                    if (!r.isSuccess) {
                        throw IllegalStateException("Script returned failure")
                    }
                }
                u.e!!["xupdate"] = u.updateJson ?: ""
                u.e!!.exportToFile(u.ef!!)
                terminal.add("-- Done.")
            } catch (e: ActionAbortedCleanlyError) {
                terminal.add("-- " + e.message)
            }
        } else if (u.sbootfile != null) {
            val bootfile = File(u.vm.logic.cacheDir, System.currentTimeMillis().toString())
            u.vm.copyUnpriv(u.vm.activity.contentResolver.openInputStream(u.sbootfile!!)!!, bootfile)
            terminal.add("-- Patching update...")
            var cmd = "FORMATDATA=false " + File(
                u.vm.logic.assetDir,
                "Scripts/add_os/${u.vm.deviceInfo!!.codename}/${u.script}"
            ).absolutePath + " ${u.ef!!.nameWithoutExtension} ${bootfile!!.absolutePath}"
            for (i in sp) {
                cmd += " " + i
            }
            val r = Shell.cmd(SDUtils.umsd(SDUtils.generateMeta(u.vm.deviceInfo.bdev, u.vm.deviceInfo.pbdev)!!) + " && " + cmd).to(terminal).exec()
            bootfile.delete()
            if (!r.isSuccess) {
                throw IllegalStateException("Script returned failure")
            }
            terminal.add("-- Done.")
            u.vm.btnsOverride = true
        } else {
            terminal.add("-- Failed to prepare update. Nothing has been changed.")
        }
        u.vm.nextText.value = u.vm.activity.getString(R.string.finish)
        u.vm.onNext.value = {
            it.startActivity(Intent(it, MainActivity::class.java))
            it.finish()
        }
    }
}