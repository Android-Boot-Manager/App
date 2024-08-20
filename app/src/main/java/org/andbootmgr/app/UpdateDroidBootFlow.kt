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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.andbootmgr.app.util.AbmTheme
import org.andbootmgr.app.util.Terminal
import java.io.File
import java.io.IOException

class UpdateDroidBootWizardPageFactory(private val vm: WizardActivityState) {
	fun get(): List<IWizardPage> {
		return listOf(WizardPage("start",
			NavButton(vm.activity.getString(R.string.cancel)) { it.finish() },
			NavButton(vm.activity.getString(R.string.next)) { it.navigate(if (vm.deviceInfo.postInstallScript) "shSel" else "select") })
		{
			Start()
		}, WizardPage("shSel",
			NavButton(vm.activity.getString(R.string.prev)) { it.navigate("start") },
			NavButton("") {}
		) {
			SelectInstallSh(vm, update = true)
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
private fun Start() {
	Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
		modifier = Modifier.fillMaxSize()
	) {
		Text(stringResource(id = R.string.welcome_text))
		Text(stringResource(R.string.update_droidboot_text))
	}
}

@Composable
private fun Flash(vm: WizardActivityState) {
	Terminal(logFile = "blup_${System.currentTimeMillis()}.txt") { terminal ->
		vm.logic.extractToolkit(terminal)
		val tmpFile = if (vm.deviceInfo.postInstallScript) {
			val tmpFile = createTempFileSu("abm", ".sh", vm.logic.rootTmpDir)
			vm.copyPriv(vm.flashStream("InstallShFlashType"), tmpFile)
			tmpFile.setExecutable(true)
			tmpFile
		} else null
		terminal.add(vm.activity.getString(R.string.term_flashing_droidboot))
		val backupLk = File(vm.logic.fileDir, "backup2_lk.img")
		val f = SuFile.open(vm.deviceInfo.blBlock)
		if (!f.canWrite())
			terminal.add(vm.activity.getString(R.string.term_cant_write_bl))
		vm.copyPriv(SuFileInputStream.open(vm.deviceInfo.blBlock), backupLk)
		try {
			vm.copyPriv(vm.flashStream("DroidBootFlashType"), File(vm.deviceInfo.blBlock))
		} catch (e: IOException) {
			terminal.add(vm.activity.getString(R.string.term_bl_failed))
			terminal.add(e.message ?: "(null)")
			terminal.add(vm.activity.getString(R.string.term_consult_doc))
			return@Terminal
		} catch (e: HashMismatchException) {
			terminal.add(e.message ?: "(null)")
			terminal.add(vm.activity.getString(R.string.restoring_backup))
			vm.copyPriv(SuFileInputStream.open(backupLk), File(vm.deviceInfo.blBlock))
			terminal.add(vm.activity.getString(R.string.term_consult_doc))
			return@Terminal
		}
		if (vm.deviceInfo.postInstallScript) {
			terminal.add(vm.activity.getString(R.string.term_device_setup))
			vm.logic.runShFileWithArgs(
				"BOOTED=${vm.deviceInfo.isBooted(vm.logic)} SETUP=false " +
						"${tmpFile!!.absolutePath} real"
			).to(terminal).exec()
			tmpFile.delete()
		}
		terminal.add(vm.activity.getString(R.string.term_success))
		withContext(Dispatchers.Main) {
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