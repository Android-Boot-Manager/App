package org.andbootmgr.app

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFileInputStream
import org.andbootmgr.app.util.SDUtils
import java.io.File
import java.io.IOException

class BackupRestoreWizardPageFactory(private val vm: WizardActivityState) {
    fun get(): List<IWizardPage> {
        val c = CreateBackupDataHolder(vm)
        return listOf(WizardPage("start",
            NavButton(vm.activity.getString(R.string.cancel)) { it.startActivity(Intent(it, MainActivity::class.java)); it.finish() },
            NavButton("") {})
        {
            ChooseAction(c)
        }, WizardPage("select",
            NavButton(vm.activity.getString(R.string.prev)) { it.navigate("start") },
            NavButton("") {}
        ) {
            SelectDroidBoot(c)
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
            Text(stringResource(R.string.backup))
        }
        Button(onClick = { c.action=2; c.vm.navigate("select") }) {
            Text(stringResource(R.string.restore))
        }
        Button(onClick = { c.action=3; c.vm.navigate("select") }) {
            Text(stringResource(R.string.flash_sparse))
        }
    }
}

@Composable
private fun SelectDroidBoot(c: CreateBackupDataHolder) {
    val nextButtonAvailable = remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        if (nextButtonAvailable.value) {
            Text(stringResource(R.string.successfully_selected))
            c.vm.nextText.value = stringResource(R.string.next)
            c.vm.onNext.value = { it.navigate("go") }
        } else {
            Text(
                if (c.action == 1)
                    stringResource(R.string.make_backup)
                else if (c.action == 2)
                    stringResource(R.string.restore_backup)
                else if (c.action == 3)
                    stringResource(R.string.restore_backup_sparse)
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
                        stringResource(R.string.choose_file)
                    } else {
                        stringResource(R.string.create_file)
                    }
                )
            }
        }
    }
}

@Composable
private fun Flash(c: CreateBackupDataHolder) {
    Terminal(c.vm, logFile = "flash_${System.currentTimeMillis()}.txt") { terminal ->
        terminal.add(c.vm.activity.getString(R.string.term_starting))
        try {
            if (!Shell.cmd(SDUtils.umsd(c.meta!!.dumpKernelPartition(c.pi))).to(terminal).exec().isSuccess)
                throw IOException(c.vm.activity.getString(R.string.term_cant_umount))
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
                    terminal.add(c.vm.activity.getString(R.string.term_failure))
                    return@Terminal
                }
            } else {
                throw IOException(c.vm.activity.getString(R.string.term_invalid_action))
            }
        } catch (e: IOException) {
            terminal.add(c.vm.activity.getString(R.string.term_backup_restore_fail))
            terminal.add(if (e.message != null) e.message!! else "(null)")
            terminal.add(c.vm.activity.getString(R.string.term_contact_support))
        }
        terminal.add(c.vm.activity.getString(R.string.term_success))
        c.vm.btnsOverride = true
        c.vm.nextText.value = c.vm.activity.getString(R.string.finish)
        c.vm.onNext.value = { it.startActivity(Intent(it, MainActivity::class.java)); it.finish() }
    }
}