package org.andbootmgr.app

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.http2.StreamResetException
import okio.buffer
import okio.sink
import org.andbootmgr.app.util.ConfigFile
import org.andbootmgr.app.util.SDUtils
import org.andbootmgr.app.util.Terminal
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
    var sbootfile = mutableStateListOf<Uri>()

    var e: ConfigFile? = null
    var ef: File? = null
    var hasUpdate = false
    var hasChecked by mutableStateOf(false)
    val partMapping = HashMap<Int, String>()
    var extraParts = ArrayList<String>()
    var updateJson: String? = null
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

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonText =
                    URL(u.e!!["xupdate"]).readText()
                u.json = JSONTokener(jsonText).nextValue() as JSONObject
            } catch (e: Exception) {
                Log.e("ABM", Log.getStackTraceString(e))
            }
            if (u.json != null) {
                u.hasUpdate = u.json!!.optBoolean("hasUpdate", false)
            }
            u.hasChecked = true
        }
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
                                    val extraIdNeeded = j.getJSONArray("extraIds")
                                    var i = 0
                                    while (i < extraIdNeeded.length()) {
                                        extraParts.add(extraIdNeeded.get(i) as String)
                                        i++
                                    }
                                    vm.flashes["InstallShFlashType"] = Pair(
                                        Uri.parse(j.getString("script")),
                                        if (j.has("scriptSha256")) j.getString("scriptSha256") else null)
                                }
                                if (j.has("parts")) {
                                    val sp = u.e!!["xpart"]!!.split(":")
                                    val p = j.getJSONObject("parts")
                                    for (k in p.keys()) {
                                        partMapping[sp[k.toInt()].toInt()] = p.getString(k)
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

@Composable
private fun Local(u: UpdateFlowDataHolder) {
    Column(verticalArrangement = Arrangement.SpaceEvenly) {
        Column {
            Text(stringResource(R.string.local_updater_1))
            Text(stringResource(R.string.local_updater_2))
            Text(stringResource(R.string.local_updater_3))
        }
        Column {
            Text(stringResource(R.string.script_name))
            if (u.vm.flashes.containsKey("InstallShFlashType")) {
                Button(onClick = {
                    u.vm.flashes.remove("InstallShFlashType")
                }) {
                    Text(stringResource(id = R.string.undo))
                }
            } else {
                Button(onClick = {
                    u.vm.activity.chooseFile("*/*") {
                        u.vm.flashes["InstallShFlashType"] = Pair(it, null)
                    }
                }) {
                    Text(stringResource(id = R.string.choose_file))
                }
            }
            for (i in u.sbootfile) {
                Row {
                    Text(stringResource(R.string.file_selected) + " " + (i.lastPathSegment ?: "(null)"))
                    Button(onClick = { u.sbootfile.remove(i) }) {
                        Text(stringResource(id = R.string.undo))
                    }
                }
            }
            if (u.sbootfile.isNotEmpty()) {
                Button(onClick = {
                    if (u.vm.flashes.containsKey("InstallShFlashType")) {
                        u.hasUpdate = false
                        u.vm.navigate("flash")
                    }
                }, enabled = u.vm.flashes.containsKey("InstallShFlashType")) {
                    Text(stringResource(R.string.install_update))
                }
            }
            Button(onClick = { u.vm.activity.chooseFile("*/*") {
                u.sbootfile.add(it)
            } }) {
                Text(stringResource(id = R.string.choose_file))
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
                throw ActionAbortedCleanlyError(Exception(u.vm.activity.getString(R.string.install_canceled)))
            else
                throw e
        }
        sink.close()

        if (u.currentDl!!.isCanceled())
            throw ActionAbortedCleanlyError(Exception(u.vm.activity.getString(R.string.install_canceled)))

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
    Terminal(logFile = "update_${System.currentTimeMillis()}.txt") { terminal ->
        val sp = u.e!!["xpart"]!!.split(":")
        val meta = SDUtils.generateMeta(u.vm.deviceInfo)!!
        Shell.cmd(SDUtils.umsd(meta)).exec()

        if (u.hasUpdate) { // online
            u.vm.btnsOverride = true
            u.vm.nextText.value = u.vm.activity.getString(R.string.cancel)
            u.vm.onNext.value = { u.currentDl?.cancel() }
            try {
                val bootfile = ArrayList<File>()
                if (u.extraParts.isNotEmpty()) {
                    for (boot in u.extraParts) {
                        terminal.add(u.vm.activity.getString(R.string.term_dl_updated_bi))
                        var bootf = dlFile(u, boot)
                        if (bootf == null) {
                            terminal.add(u.vm.activity.getString(R.string.term_dl_fail1))
                            bootf = dlFile(u, boot)
                            if (bootf == null) {
                                terminal.add(u.vm.activity.getString(R.string.term_dl_fail2))
                                throw ActionAbortedCleanlyError(Exception(u.vm.activity.getString(R.string.install_canceled)))
                            }
                        }
                        bootfile.add(bootf)
                    }
                }
                val pmap = HashMap<Int, File>()
                for (p in u.partMapping.entries) {
                    terminal.add(u.vm.activity.getString(R.string.term_dling_part_image))
                    var f = dlFile(u, p.value)
                    if (f == null) {
                        terminal.add(u.vm.activity.getString(R.string.term_dl_fail1))
                        f = dlFile(u, p.value)
                        if (f == null) {
                            terminal.add(u.vm.activity.getString(R.string.term_dl_fail2))
                            bootfile.forEach { it.delete() }
                            pmap.values.forEach { it.delete() }
                            throw ActionAbortedCleanlyError(Exception(u.vm.activity.getString(R.string.install_canceled)))
                        }
                    }
                    pmap[p.key] = f
                }
                val tmpFile = createTempFileSu("abm", ".sh", u.vm.logic.rootTmpDir)
                u.vm.copyPriv(u.vm.flashStream("InstallShFlashType"), tmpFile)
                tmpFile.setExecutable(true)
                u.vm.nextText.value = ""
                u.vm.onNext.value = {}

                for (p in u.partMapping.entries) {
                    val v = sp.find { p.key.toString() == it }
                    terminal.add(u.vm.activity.getString(R.string.term_flashing_p, v))
                    val f2 = pmap[p.key]!!
                    val tp = File(meta.dumpKernelPartition(p.key).path)
                    if (u.sparse.contains(p.key)) {
                        val result2 = Shell.cmd(
                            File(
                                u.vm.logic.assetDir,
                                "Toolkit/simg2img"
                            ).absolutePath + " ${f2.absolutePath} ${tp.absolutePath}"
                        ).to(terminal).exec()
                        if (!result2.isSuccess) {
                            throw IllegalStateException(u.vm.activity.getString(R.string.term_simg2img_fail))
                        }
                    } else {
                        u.vm.copyPriv(f2.inputStream(), tp)
                    }
                    terminal.add(u.vm.activity.getString(R.string.term_done))
                }
                if (u.extraParts.isNotEmpty()) {
                    terminal.add(u.vm.activity.getString(R.string.term_patch_update))
                    var cmd = "FORMATDATA=false " + tmpFile.absolutePath + " ${u.ef!!.nameWithoutExtension}"
                    for (i in bootfile) {
                        cmd += " " + i.absolutePath
                    }
                    for (i in sp) {
                        cmd += " $i"
                    }
                    val r = u.vm.logic.runShFileWithArgs(cmd).to(terminal).exec()
                    tmpFile.delete()
                    bootfile.forEach { it.delete() }
                    if (!r.isSuccess) {
                        throw IllegalStateException(u.vm.activity.getString(R.string.term_script_fail))
                    }
                }
                u.e!!["xupdate"] = u.updateJson ?: ""
                u.e!!.exportToFile(u.ef!!)
                terminal.add(u.vm.activity.getString(R.string.term_success))
            } catch (e: ActionAbortedCleanlyError) {
                terminal.add("-- " + e.message)
            }
        } else if (u.sbootfile.isNotEmpty()) {
            val bootfile = ArrayList<File>()
            val tmpFile = createTempFileSu("abm", ".sh", u.vm.logic.rootTmpDir)
            u.vm.copyPriv(u.vm.flashStream("InstallShFlashType"), tmpFile)
            tmpFile.setExecutable(true)
            terminal.add(u.vm.activity.getString(R.string.term_patch_update))
            u.sbootfile.forEach {
                val bootf = File(u.vm.logic.cacheDir, System.currentTimeMillis().toString())
                u.vm.copyUnpriv(
                    u.vm.activity.contentResolver.openInputStream(it)!!,
                    bootf
                )
                bootfile.add(bootf)
            }
            var cmd = "FORMATDATA=false " + tmpFile.absolutePath + " ${u.ef!!.nameWithoutExtension}"
            for (i in bootfile) {
                cmd += " " + i.absolutePath
            }
            for (i in sp) {
                cmd += " $i"
            }
            val r = u.vm.logic.runShFileWithArgs(cmd).to(terminal).exec()
            tmpFile.delete()
            bootfile.forEach { it.delete() }
            if (!r.isSuccess) {
                throw IllegalStateException(u.vm.activity.getString(R.string.term_script_fail))
            }
            terminal.add(u.vm.activity.getString(R.string.term_success))
            u.vm.btnsOverride = true
        } else {
            terminal.add(u.vm.activity.getString(R.string.term_update_failed_prep))
        }
        u.vm.nextText.value = u.vm.activity.getString(R.string.finish)
        u.vm.onNext.value = {
            it.startActivity(Intent(it, MainActivity::class.java))
            it.finish()
        }
    }
}