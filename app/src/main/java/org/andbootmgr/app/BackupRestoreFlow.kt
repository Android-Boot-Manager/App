package org.andbootmgr.app

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFileInputStream
import org.andbootmgr.app.util.SDUtils
import org.andbootmgr.app.util.Terminal
import java.io.File
import java.io.IOException

class BackupRestoreFlow(private val partitionId: Int): WizardFlow() {
    override fun get(vm: WizardActivityState): List<IWizardPage> {
        val c = CreateBackupDataHolder(vm, partitionId)
        return listOf(WizardPage("start",
            NavButton(vm.activity.getString(R.string.cancel)) { it.finish() },
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

private class CreateBackupDataHolder(val vm: WizardActivityState, val pi: Int) {
    var action: Int = 0
    var path: Uri? = null
    var meta: SDUtils.SDPartitionMeta? = null
}

@Composable
private fun ChooseAction(c: CreateBackupDataHolder) {
    LaunchedEffect(Unit) {
        c.meta = SDUtils.generateMeta(c.vm.deviceInfo)
    }

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
    var nextButtonAvailable by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        if (nextButtonAvailable) {
            Text(stringResource(R.string.successfully_selected))
        } else {
            Text(
                when (c.action) {
                    1 -> stringResource(R.string.make_backup)
                    2 -> stringResource(R.string.restore_backup)
                    3 -> stringResource(R.string.restore_backup_sparse)
                    else -> ""
                }
            )
            val next = { it: Uri ->
                c.path = it
                nextButtonAvailable = true
                c.vm.nextText = c.vm.activity.getString(R.string.next)
                c.vm.onNext = { i -> i.navigate("go") }
            }
            Button(onClick = {
                if (c.action != 1) {
                    c.vm.activity.chooseFile("*/*", next)
                } else {
                    c.vm.activity.createFile("${c.meta!!.dumpKernelPartition(c.pi).name}.img", next)
                }
            }) {
                Text(stringResource(if (c.action != 1) R.string.choose_file else R.string.create_file))
            }
        }
    }
}

@Composable
private fun Flash(c: CreateBackupDataHolder) {
    Terminal(logFile = "flash_${System.currentTimeMillis()}.txt") { terminal ->
        c.vm.logic.extractToolkit(terminal)
        terminal.add(c.vm.activity.getString(R.string.term_starting))
        try {
            val p = c.meta!!.dumpKernelPartition(c.pi)
            if (!c.vm.logic.unmount(p).to(terminal).exec().isSuccess)
                throw IOException(c.vm.activity.getString(R.string.term_cant_umount))
            if (c.action == 1) {
                c.vm.copy(
                    SuFileInputStream.open(File(p.path)),
                    c.vm.activity.contentResolver.openOutputStream(c.path!!)!!
                )
            } else if (c.action == 2) {
                c.vm.copyPriv(
                    c.vm.activity.contentResolver.openInputStream(c.path!!)!!,
                    File(p.path)
                )
            } else if (c.action == 3) {
                val f = File(c.vm.logic.cacheDir, System.currentTimeMillis().toString())
                c.vm.copyUnpriv(c.vm.activity.contentResolver.openInputStream(c.path!!)!!, f)
                val result2 = Shell.cmd(
                    File(
                        c.vm.logic.toolkitDir,
                        "simg2img"
                    ).absolutePath + " ${f.absolutePath} ${p.path}"
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
        c.vm.nextText = c.vm.activity.getString(R.string.finish)
        c.vm.onNext = { it.finish() }
    }
}