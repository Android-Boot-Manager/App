package org.andbootmgr.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import org.andbootmgr.app.util.AbmTheme
import java.io.File
import java.io.IOException

class FixDroidBootWizardPageFactory(private val vm: WizardActivityState) {
	fun get(): List<IWizardPage> {
		return listOf(WizardPage("start",
			NavButton(vm.activity.getString(R.string.cancel)) { it.finish() },
			NavButton(vm.activity.getString(R.string.next)) { it.navigate(if (vm.deviceInfo.postInstallScript) "shSel" else "select") })
		{
			Start(vm)
		}, WizardPage("shSel",
			NavButton(vm.activity.getString(R.string.prev)) { it.navigate("start") },
			NavButton("") {}
		) {
			SelectInstallSh(vm)
		},WizardPage("select",
			NavButton(vm.activity.getString(R.string.prev)) { it.navigate(if (vm.deviceInfo.postInstallScript) "shSel" else "start") },
			NavButton("") {}
		) {
			SelectDroidBoot(vm)
		}, WizardPage("flash",
			NavButton("") {},
			NavButton("") {}
		) {
			Flash(vm)
		})
	}
}

@Composable
private fun Start(vm: WizardActivityState) {
	Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
		modifier = Modifier.fillMaxSize()
	) {
		Text(stringResource(id = R.string.welcome_text))
		Text(stringResource(R.string.reinstall_dboot))
	}
}

@Composable
private fun Flash(vm: WizardActivityState) {
	val flashType = "DroidBootFlashType"
	Terminal(vm, logFile = "blfix_${System.currentTimeMillis()}.txt") { terminal ->
		terminal.add(vm.activity.getString(R.string.term_flashing_droidboot))
		val f = SuFile.open(vm.deviceInfo!!.blBlock)
		if (!f.canWrite())
			terminal.add(vm.activity.getString(R.string.term_cant_write_bl))
		vm.copyPriv(SuFileInputStream.open(vm.deviceInfo.blBlock), File(vm.logic.fileDir, "backup_lk.img"))
		try {
			vm.copyPriv(vm.flashStream(flashType), File(vm.deviceInfo.blBlock))
		} catch (e: IOException) {
			terminal.add(vm.activity.getString(R.string.term_bl_failed))
			terminal.add(if (e.message != null) e.message!! else "(null)")
			terminal.add(vm.activity.getString(R.string.term_consult_doc))
			return@Terminal
		}
		if (vm.deviceInfo.postInstallScript) {
			terminal.add(vm.activity.getString(R.string.term_device_setup))
			val tmpFile = createTempFileSu("abm", ".sh", vm.logic.rootTmpDir)
			vm.copyPriv(vm.flashStream("InstallShFlashType"), tmpFile)
			tmpFile.setExecutable(true)
			vm.logic.runShFileWithArgs(
				"BOOTED=${vm.deviceInfo.isBooted(vm.logic)} SETUP=true " +
						"${tmpFile.absolutePath} real"
			).to(terminal).exec()
			tmpFile.delete()
		}
		terminal.add(vm.activity.getString(R.string.term_success))
		vm.activity.runOnUiThread {
			vm.btnsOverride = true
			vm.nextText.value = vm.activity.getString(R.string.finish)
			vm.onNext.value = {
				it.finish()
			}
		}
	}
}

@Composable
@Preview
private fun Preview() {
	val vm = WizardActivityState("null")
	AbmTheme {
		Surface(
			modifier = Modifier.fillMaxSize(),
			color = MaterialTheme.colorScheme.background
		) {
			SelectDroidBoot(vm)
		}
	}
}