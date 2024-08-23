package org.andbootmgr.app

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.andbootmgr.app.util.ConfigFile
import org.andbootmgr.app.util.SDUtils
import org.andbootmgr.app.util.Terminal
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.net.URL

class UpdateFlow(private val entryName: String): WizardFlow() {
    override fun get(vm: WizardState): List<IWizardPage> {
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
        }, WizardPage("dload",
            NavButton(vm.activity.getString(R.string.cancel)) { it.finish() },
            NavButton("") {}
        ) {
            WizardDownloader(c.vm, "flash")
        }, WizardPage("flash",
            NavButton("") {},
            NavButton("") {}
        ) {
            Flash(c)
        })
    }
}

private class UpdateFlowDataHolder(val vm: WizardState, val entryFilename: String) {
    var json: JSONObject? = null
    var e: ConfigFile? = null
    var ef: File? = null
    var updateJson: String? = null
    val sparse = ArrayList<Int>()
}

@Composable
private fun Start(u: UpdateFlowDataHolder) {
    var hasChecked by remember { mutableStateOf(false) }
    var hasUpdate by remember { mutableStateOf(false) }
    val ioDispatcher = rememberCoroutineScope { Dispatchers.IO }
    LaunchedEffect(Unit) {
        ioDispatcher.launch {
            u.e = ConfigFile.importFromFile(
                SuFile.open(
                    u.vm.logic.abmEntries.absolutePath,
                    u.entryFilename
                )
            )
            u.ef = u.vm.logic.abmEntries.resolve(u.entryFilename)
            try {
                val jsonText =
                    URL(u.e!!["xupdate"]).readText()
                u.json = JSONTokener(jsonText).nextValue() as JSONObject
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(u.vm.activity, e.message, Toast.LENGTH_LONG).show()
                }
                Log.e("ABM", Log.getStackTraceString(e))
            }
            if (u.json != null) {
                hasUpdate = u.json!!.optBoolean("hasUpdate", false)
            }
            hasChecked = true
        }
    }
    if (hasChecked) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (u.json == null) {
                Text(stringResource(R.string.update_check_failed))
            } else {
                if (hasUpdate) {
                    Text(stringResource(id = R.string.found_update))
                    Button(onClick = {
                        try {
                            u.run {
                                val j = json!!
                                if (j.has("extraIds") && j.has("script")) {
                                    val extraIdNeeded = j.getJSONArray("extraIds")
                                    var i = 0
                                    while (i < extraIdNeeded.length()) {
                                        vm.inetAvailable["boot$i"] = WizardState.Downloadable(
                                            extraIdNeeded.get(i) as String,
                                            null,
                                            ""
                                        )
                                        vm.idNeeded.add("boot$i")
                                        i++
                                    }
                                    vm.inetAvailable["_install.sh_"] = WizardState.Downloadable(
                                        j.getString("script"),
                                        j.getStringOrNull("scriptSha256"),
                                        vm.activity.getString(R.string.installer_sh)
                                    )
                                    vm.idNeeded.add("_install.sh_")
                                }
                                if (j.has("parts")) {
                                    val p = j.getJSONObject("parts")
                                    for (k in p.keys()) {
                                        vm.inetAvailable["part$k"] = WizardState.Downloadable(
                                            p.getString(k),
                                            null,
                                            ""
                                        )
                                        vm.idNeeded.add("part$k")
                                    }
                                }
                                updateJson = j.getStringOrNull("updateJson")
                                val a = j.optJSONArray("sparse")
                                if (a != null) {
                                    for (i in 0 until a.length()) {
                                        val v = a.getInt(i)
                                        sparse.add(v)
                                    }
                                }
                            }
                            u.vm.navigate("dload")
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
            var i by remember { mutableIntStateOf(0) }
            Text(stringResource(R.string.how_many_extras, i))
            Row {
                Button({ i++ }) {
                    Text("+")
                }
                Spacer(Modifier.width(5.dp))
                Button({ i-- }) {
                    Text("-")
                }
            }
            Spacer(Modifier.height(5.dp))
            Button({
                u.vm.idNeeded.add("_install.sh_")
                for (j in 1..i) {
                    u.vm.idNeeded.add("boot${j - 1}")
                }
                u.vm.navigate("dload")
            }) {
                Text(stringResource(R.string.install_update))
            }
        }
    }
}

@Composable
private fun Flash(u: UpdateFlowDataHolder) {
    Terminal(logFile = "update_${System.currentTimeMillis()}.txt") { terminal ->
        u.vm.logic.extractToolkit(terminal)
        val sp = u.e!!["xpart"]!!.split(":")
        val meta = SDUtils.generateMeta(u.vm.deviceInfo)!!
        Shell.cmd(SDUtils.umsd(meta)).exec()
        val tmpFile = if (u.vm.idNeeded.contains("_install.sh_")) {
            u.vm.chosen["_install.sh_"]!!.toFile(u.vm).also {
                it.setExecutable(true)
            }
        } else null
        for (p in u.vm.idNeeded.filter { it.startsWith("part") }.map { it.substring(4) }) {
            val physicalId = sp[p.toInt()].toInt()
            terminal.add(u.vm.activity.getString(R.string.term_flashing_p, p))
            val f2 = u.vm.chosen["part$p"]!!
            val tp = File(meta.dumpKernelPartition(physicalId).path)
            if (u.sparse.contains(p.toInt())) {
                val result2 = Shell.cmd(
                    File(
                        u.vm.logic.toolkitDir,
                        "simg2img"
                    ).absolutePath + " ${f2.toFile(u.vm)} ${tp.absolutePath}"
                ).to(terminal).exec()
                if (!result2.isSuccess) {
                    throw IllegalStateException(u.vm.activity.getString(R.string.term_simg2img_fail))
                }
            } else {
                u.vm.copyPriv(f2.openInputStream(u.vm), tp)
            }
            terminal.add(u.vm.activity.getString(R.string.term_done))
        }
        val bootFiles = u.vm.idNeeded.filter { it.startsWith("boot") }
        if (bootFiles.isNotEmpty()) {
            terminal.add(u.vm.activity.getString(R.string.term_patch_update))
            var cmd =
                "FORMATDATA=false " + tmpFile!!.absolutePath + " ${u.ef!!.nameWithoutExtension}"
            for (i in bootFiles) {
                cmd += " " + u.vm.chosen[i]!!.toFile(u.vm)
            }
            for (i in sp) {
                cmd += " $i"
            }
            val r = u.vm.logic.runShFileWithArgs(cmd).to(terminal).exec()
            if (!r.isSuccess) {
                throw IllegalStateException(u.vm.activity.getString(R.string.term_script_fail))
            }
        }
        u.e!!["xupdate"] = u.updateJson ?: ""
        u.e!!.exportToFile(u.ef!!)
        terminal.add(u.vm.activity.getString(R.string.term_success))
        tmpFile?.delete()
        u.vm.nextText = u.vm.activity.getString(R.string.finish)
        u.vm.onNext = { it.finish() }
    }
}