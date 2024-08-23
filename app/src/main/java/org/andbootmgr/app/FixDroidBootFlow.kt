package org.andbootmgr.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.andbootmgr.app.util.Terminal
import java.io.File
import java.io.IOException

class FixDroidBootFlow(): WizardFlow() {
	override fun get(vm: WizardState): List<IWizardPage> {
		return listOf(WizardPage("start",
			NavButton(vm.activity.getString(R.string.cancel)) { it.finish() },
			NavButton("") {})
		{
			Start(vm)
		}, WizardPage("dload",
			NavButton(vm.activity.getString(R.string.cancel)) { it.finish() },
			NavButton("") {}
		) {
			WizardDownloader(vm, "flash")
		}, WizardPage("flash",
			NavButton("") {},
			NavButton("") {}
		) {
			Flash(vm)
		})
	}
}

@Composable
private fun Start(vm: WizardState) {
	LoadDroidBootJson(vm) {
		LaunchedEffect(Unit) {
			vm.nextText = vm.activity.getString(R.string.next)
			vm.onNext = { it.navigate(if (vm.idNeeded.isNotEmpty()) "dload" else "flash") }
		}
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center,
			modifier = Modifier.fillMaxSize()
		) {
			Text(stringResource(id = R.string.welcome_text))
			Text(stringResource(R.string.reinstall_dboot))
		}
	}
}

@Composable
private fun Flash(vm: WizardState) {
	Terminal(logFile = "blfix_${System.currentTimeMillis()}.txt") { terminal ->
		vm.logic.extractToolkit(terminal)
		val tmpFile = if (vm.deviceInfo.postInstallScript) {
			vm.chosen["_install.sh_"]!!.toFile(vm).also {
				it.setExecutable(true)
			}
		} else null
		terminal.add(vm.activity.getString(R.string.term_flashing_droidboot))
		val backupLk = File(vm.logic.fileDir, "backup_lk.img")
		val f = SuFile.open(vm.deviceInfo.blBlock)
		if (!f.canWrite())
			terminal.add(vm.activity.getString(R.string.term_cant_write_bl))
		vm.copyPriv(SuFileInputStream.open(vm.deviceInfo.blBlock), backupLk)
		try {
			vm.copyPriv(vm.chosen["droidboot"]!!.openInputStream(vm), File(vm.deviceInfo.blBlock))
		} catch (e: IOException) {
			terminal.add(vm.activity.getString(R.string.term_bl_failed))
			terminal.add(e.message ?: "(null)")
			terminal.add(vm.activity.getString(R.string.term_consult_doc))
			return@Terminal
		}
		if (vm.deviceInfo.postInstallScript) {
			terminal.add(vm.activity.getString(R.string.term_device_setup))
			vm.logic.runShFileWithArgs(
				"BOOTED=${vm.deviceInfo.isBooted(vm.logic)} SETUP=true " +
						"${tmpFile!!.absolutePath} real"
			).to(terminal).exec()
			tmpFile.delete()
		}
		terminal.add(vm.activity.getString(R.string.term_success))
		withContext(Dispatchers.Main) {
			vm.nextText = vm.activity.getString(R.string.finish)
			vm.onNext = {
				it.finish()
			}
		}
	}
}