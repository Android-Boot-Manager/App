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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
        }, WizardPage("Complete_action",
            NavButton("") {},
            NavButton("") {}
        ) {
            Flash(c)
        })
    }
}

private class CreateBackupDataHolder(val vm: WizardActivityState){
    var action: Int = 0
    var path: Uri? = null
    var meta: SDUtils.SDPartitionMeta? = null

}

@Composable
private fun ChooseAction(c: CreateBackupDataHolder) {
    c.meta = SDUtils.generateMeta(c.vm.deviceInfo!!.bdev, c.vm.deviceInfo.pbdev)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Text("Backup & Restore")
        Text(stringResource(id = R.string.backup_msg, c.meta!!.dump(c.vm.activity.intent.getIntExtra("partitionid", -1)).name))
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
    Log.i("backupandrestore","Partition is ${c.vm.activity.intent.getIntExtra("partitionid", -1)}")
}

@Composable
private fun Select(c: CreateBackupDataHolder) {
    val nextButtonAvailable = remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        if (nextButtonAvailable.value) {
            Text("Successfully selected.")
            c.vm.nextText.value = "Next"
            c.vm.onNext.value = { it.navigate("Complete_action") }
        } else {
            if(c.action==1) Text("Create backup file") else if(c.action==2) Text("Select file to restore") else Text("Select sparse file to write to the partition")
            Button(onClick = {
                c.vm.activity.chooseFile("*/*") {
                    c.path=it
                    nextButtonAvailable.value = true
                }
            }) {
                if(c.action!=1) {
                    Text("Choose file")
                } else {
                    Text("Create file")
                }
            }
        }
    }
}

@Composable
private fun Flash(c: CreateBackupDataHolder) {
    Terminal(c.vm) { terminal ->
        terminal.add("-- Going to flash ${c.path} to ${c.vm.deviceInfo!!.pbdev+(c.vm.activity.intent.getIntExtra("partitionid", -1))}")
        try {
            c.vm.copyPriv(c.vm.activity.contentResolver.openInputStream(c.path!!)!!, File(c.vm.deviceInfo!!.pbdev+(c.vm.activity.intent.getIntExtra("partitionid", -1))))
        } catch (e: IOException) {
            terminal.add("-- Failed to flash bootloader, cause:")
            terminal.add(if (e.message != null) e.message!! else "(null)")
            terminal.add("-- Please consult documentation to finish the backup")
        }
        terminal.add("-- Flash successful")
        c.vm.nextText.value = "Finish"
        c.vm.onNext.value = { it.startActivity(Intent(it, MainActivity::class.java)); it.finish() }
        c.vm.activity.runOnUiThread {
            c.vm.btnsOverride = true
            c.vm.nextText.value = "Finish"
            c.vm.onNext.value = { it.startActivity(Intent(it, MainActivity::class.java)); it.finish() }
            }
    }
}