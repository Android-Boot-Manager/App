package org.andbootmgr.app

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
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

class UpdateFlow(private val entryName: String): WizardFlow() {
    override fun get(vm: WizardActivityState): List<IWizardPage> {
        val c = UpdateFlowDataHolder(vm, entryName)
        return listOf(WizardPage("start",
            NavButton(vm.activity.getString(R.string.cancel)) {
                it.finish()
            },
            if (c.vm.mvm.noobMode) NavButton("") {} else NavButton(vm.activity.getString(R.string.local_update)) { vm.navigate("local") }
        ) {
            Start(c)
        }, WizardPage("local",
            NavButton(vm.activity.getString(R.string.cancel)) { it.finish() },
            NavButton(vm.activity.getString(R.string.online_update)) { vm.navigate("start") }
        ) {
            Local(c)
            // TODO add dl
        }, WizardPage("flash",
            NavButton("") {},
            NavButton("") {}
        ) {
            Flash(c)
        })
    }
}

private class UpdateFlowDataHolder(val vm: WizardActivityState, val entryFilename: String) {
    val client = OkHttpClient().newBuilder().readTimeout(1L, TimeUnit.HOURS).build()
    var json: JSONObject? = null
    var currentDl: Call? = null
    var sbootfile = mutableStateListOf<Uri>()

    var e: ConfigFile? = null
    var ef: File? = null
    var hasUpdate = false
    val partMapping = HashMap<Int, String>()
    var extraParts = ArrayList<String>()
    var updateJson: String? = null
    val sparse = ArrayList<Int>()
}

@Composable
private fun Start(u: UpdateFlowDataHolder) {
    var hasChecked by remember { mutableStateOf(false) }
    val ioDispatcher = rememberCoroutineScope { Dispatchers.IO }
    LaunchedEffect(Unit) {
        ioDispatcher.launch {
            u.e = ConfigFile.importFromFile(
                SuFile.open(
                    u.vm.logic.abmEntries.absolutePath,
                    u.entryFilename
                )
            )
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
            hasChecked = true
        }
    }
    if (hasChecked) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (u.json == null) {
                Text(stringResource(R.string.update_check_failed))
            } else {
                if (u.hasUpdate) {
                    Text(stringResource(id = R.string.found_update))
                    Button(onClick = {
                        try {
                            u.run {
                                val j = json!!
                                if (j.has("extraIds") && j.has("script")) {
                                    val extraIdNeeded = j.getJSONArray("extraIds")
                                    var i = 0
                                    while (i < extraIdNeeded.length()) {
                                        extraParts.add(extraIdNeeded.get(i) as String)
                                        i++
                                    }
                                    vm.inetAvailable["install"] = WizardActivityState.Downloadable(
                                        j.getString("script"),
                                        j.optString("scriptSha256"),
                                        vm.activity.getString(R.string.installer_sh)
                                    )
                                    vm.idNeeded.add("install")
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
    } else {
        LoadingCircle(stringResource(R.string.checking_for_update), modifier = Modifier.fillMaxSize())
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
            if (u.vm.chosen.containsKey("install")) {
                Button(onClick = {
                    u.vm.chosen.remove("install")
                }) {
                    Text(stringResource(id = R.string.undo))
                }
            } else {
                Button(onClick = {
                    u.vm.activity.chooseFile("*/*") {
                        u.vm.chosen["install"] = DledFile(it, null)
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
                    if (u.vm.chosen.containsKey("install")) {
                        u.hasUpdate = false
                        u.vm.navigate("flash")
                    }
                }, enabled = u.vm.chosen.containsKey("install")) {
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
        u.vm.logic.extractToolkit(terminal)
        val sp = u.e!!["xpart"]!!.split(":")
        val meta = SDUtils.generateMeta(u.vm.deviceInfo)!!
        Shell.cmd(SDUtils.umsd(meta)).exec()

        if (u.hasUpdate) { // online
            u.vm.nextText = u.vm.activity.getString(R.string.cancel)
            u.vm.onNext = { u.currentDl?.cancel() }
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
                u.vm.copyPriv(u.vm.chosen["install"]!!.openInputStream(u.vm), tmpFile)
                tmpFile.setExecutable(true)
                u.vm.nextText = ""
                u.vm.onNext = {}

                for (p in u.partMapping.entries) {
                    val v = sp.find { p.key.toString() == it }
                    terminal.add(u.vm.activity.getString(R.string.term_flashing_p, v))
                    val f2 = pmap[p.key]!!
                    val tp = File(meta.dumpKernelPartition(p.key).path)
                    if (u.sparse.contains(p.key)) {
                        val result2 = Shell.cmd(
                            File(
                                u.vm.logic.toolkitDir,
                                "simg2img"
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
            u.vm.copyPriv(u.vm.chosen["flashes"]!!.openInputStream(u.vm), tmpFile)
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
        } else {
            terminal.add(u.vm.activity.getString(R.string.term_update_failed_prep))
        }
        u.vm.nextText = u.vm.activity.getString(R.string.finish)
        u.vm.onNext = { it.finish() }
    }
}