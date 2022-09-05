package org.andbootmgr.app

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.net.toFile
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFileInputStream
import java.io.File
import java.io.IOException

class BackupRestoreWizardPageFactory(private val vm: WizardActivityState) {
    fun get(): List<IWizardPage> {
        val c = CreateBackupDataHolder(vm)
        return listOf(WizardPage("start",
            NavButton("Cancel") { it.startActivity(Intent(it, MainActivity::class.java)); it.finish() },
            NavButton("") {})
        {
            ChooseAction(c)
        }, WizardPage("select",
            NavButton("Prev") { it.navigate("start") },
            NavButton("") {}
        ) {
            Select(c)
        }, WizardPage("go",
            NavButton("") {},
            NavButton("") {}
        ) {
            Flash(c)
        })
    }
}

private class CreateBackupDataHolder(val vm: WizardActivityState){
    var pi: Int = -1
    var action: Int = 0
    var path: Uri? = null
    var meta: SDUtils.SDPartitionMeta? = null

}

@Composable
private fun ChooseAction(c: CreateBackupDataHolder) {
    c.meta = remember { SDUtils.generateMeta(c.vm.deviceInfo!!.bdev, c.vm.deviceInfo.pbdev) }
    c.pi = remember { c.vm.activity.intent.getIntExtra("partitionid", -1) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(stringResource(id = R.string.backup_msg, c.meta!!.dumpKernelPartition(c.pi).name), textAlign = TextAlign.Center)
        Button(onClick = { c.action=1; c.vm.navigate("select") }) {
            Text("Backup")
        }
        Button(onClick = { c.action=2; c.vm.navigate("select") }) {
            Text("Restore")
        }
        Button(onClick = { c.action=3; c.vm.navigate("select") }) {
            Text("Flash sparse image")
        }
    }
}

@Composable
private fun Select(c: CreateBackupDataHolder) {
    val nextButtonAvailable = remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        if (nextButtonAvailable.value) {
            Text("Successfully selected.")
            c.vm.nextText.value = "Next"
            c.vm.onNext.value = { it.navigate("go") }
        } else {
            Text(
                if (c.action == 1)
                    "Create backup file"
                else if (c.action == 2)
                    "Select file to restore"
                else if (c.action == 3)
                    "Select sparse file to write to the partition"
                else
                    ""
            )
            Button(onClick = {
                if (c.action != 1) {
                    c.vm.activity.chooseFile("*/*") {
                        c.path = it
                        nextButtonAvailable.value = true
                    }
                } else {
                    c.vm.activity.createFile("${c.meta!!.dumpKernelPartition(c.pi).name}.img") {
                        c.path = it
                        nextButtonAvailable.value = true
                    }
                }
            }) {
                Text(
                    if (c.action != 1) {
                        "Choose file"
                    } else {
                        "Create file"
                    }
                )
            }
        }
    }
}

@Composable
private fun Flash(c: CreateBackupDataHolder) {
    Terminal(c.vm) { terminal ->
        terminal.add("-- Starting...")
        try {
            if (!Shell.cmd(SDUtils.umsd(c.meta!!.dumpKernelPartition(c.pi))).to(terminal).exec().isSuccess)
                throw IOException("Cannot umount. Nothing has been changed")
            if (c.action == 1) {
                c.vm.copy(
                    SuFileInputStream.open(
                        File(
                            c.vm.deviceInfo!!.pbdev + (c.pi)
                        )
                    ),
                    c.vm.activity.contentResolver.openOutputStream(c.path!!)!!
                )
            } else if (c.action == 2) {
                c.vm.copyPriv(
                    c.vm.activity.contentResolver.openInputStream(c.path!!)!!,
                    File(
                        c.vm.deviceInfo!!.pbdev + (c.pi)
                    )
                )
            } else if (c.action == 3) {
                val f = File(c.vm.logic.cacheDir, System.currentTimeMillis().toString())
                c.vm.copyUnpriv(c.vm.activity.contentResolver.openInputStream(c.path!!)!!, f)
                val result2 = Shell.cmd(
                    File(
                        c.vm.logic.assetDir,
                        "Toolkit/simg2img"
                    ).absolutePath + " ${f.absolutePath} ${
                        c.vm.deviceInfo!!.pbdev + (c.pi)
                    }"
                ).to(terminal).exec()
                if (!result2.isSuccess) {
                    terminal.add("-- FAILURE!")
                    return@Terminal
                }
            } else {
                throw IOException("Invalid action, nothing has been changed")
            }
        } catch (e: IOException) {
            terminal.add("-- Failed to backup/restore, cause:")
            terminal.add(if (e.message != null) e.message!! else "(null)")
            terminal.add("-- Please contact support")
        }
        terminal.add("-- Successful!")
        c.vm.btnsOverride = true
        c.vm.nextText.value = "Finish"
        c.vm.onNext.value = { it.startActivity(Intent(it, MainActivity::class.java)); it.finish() }
    }
}