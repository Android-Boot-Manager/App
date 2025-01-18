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
import java.io.File
import java.io.IOException

class FixDroidBootFlow: WizardFlow() {
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
	LoadDroidBootJson(vm, false) {
		if (!vm.deviceInfo.isBooted(vm.logic) && !vm.idNeeded.contains("droidboot")) {
			Text(stringResource(R.string.install_bl_first))
			return@LoadDroidBootJson
		}
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
	WizardTerminalWork(vm, logFile = "blfix_${System.currentTimeMillis()}.txt") { terminal ->
		vm.logic.extractToolkit(terminal)
		vm.downloadRemainingFiles(terminal)
		val tmpFile = if (vm.deviceInfo.postInstallScript) {
			vm.chosen["_install.sh_"]!!.toFile(vm).also {
				it.setExecutable(true)
			}
		} else null
		terminal.add(vm.activity.getString(R.string.term_flashing_droidboot))
		val f = SuFile.open(vm.deviceInfo.blBlock)
		if (!f.canWrite())
			terminal.add(vm.activity.getString(R.string.term_cant_write_bl))
		vm.copyPriv(SuFileInputStream.open(vm.deviceInfo.blBlock), vm.logic.lkBackupSecondary)
		try {
			vm.copyPriv(vm.chosen["droidboot"]!!.openInputStream(vm), File(vm.deviceInfo.blBlock))
		} catch (e: IOException) {
			terminal.add(vm.activity.getString(R.string.term_bl_failed))
			terminal.add(e.message ?: "(null)")
			terminal.add(vm.activity.getString(R.string.term_consult_doc))
			return@WizardTerminalWork
		}
		if (vm.deviceInfo.postInstallScript) {
			terminal.add(vm.activity.getString(R.string.term_device_setup))
			vm.logic.runShFileWithArgs(
				"BOOTED=${vm.deviceInfo.isBooted(vm.logic)} SETUP=false " +
						"BL_BACKUP=${vm.logic.lkBackupSecondary.absolutePath} " +
						"${tmpFile!!.absolutePath} real"
			).to(terminal).exec()
			tmpFile.delete()
		}
		terminal.add(vm.activity.getString(R.string.term_success))
	}
}