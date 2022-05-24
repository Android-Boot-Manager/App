package org.andbootmgr.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import org.andbootmgr.app.ui.theme.AbmTheme
import java.io.File
import java.io.IOException

class FixDroidBootWizardPageFactory(private val vm: WizardActivityState) {
	fun get(): List<IWizardPage> {
		return listOf(WizardPage("start",
			NavButton("Cancel") { it.finish() },
			NavButton("Next") { it.navigate("select") })
		{
			Start(vm)
		}, WizardPage("select",
			NavButton("Prev") { it.navigate("start") },
			NavButton("") {}
		) {
			Select(vm)
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
		Text("Welcome to ABM!")
		Text("This will reinstall DroidBoot")
	}
}

@Composable
private fun Select(vm: WizardActivityState) {
	val nextButtonAvailable = remember { mutableStateOf(false) }
	val flashType = "DroidBootFlashType"

	Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
		modifier = Modifier.fillMaxSize()
	) {
		Icon(
			painterResource(R.drawable.ic_droidbooticon),
			"DroidBoot Icon",
			Modifier.defaultMinSize(32.dp, 32.dp)
		)

		if (nextButtonAvailable.value) {
			Text("Successfully selected.")
			vm.nextText.value = "Next"
			vm.onNext.value = { it.navigate("flash") }
		} else {
			Text("Please choose DroidBoot image!")
			Button(onClick = {
				vm.activity.chooseFile("*/*") {
					vm.flashes[flashType] = it
					nextButtonAvailable.value = true
				}
			}) {
				Text("Choose file")
			}
		}
	}
}

@Composable
private fun Flash(vm: WizardActivityState) {
	val flashType = "DroidBootFlashType"
	Terminal(vm) { terminal ->
		terminal.add("Flashing DroidBoot...")
		val f = SuFile.open(vm.deviceInfo!!.blBlock)
		if (!f.canWrite())
			terminal.add("Note: probably cannot write to bootloader")
		vm.copyPriv(SuFileInputStream.open(vm.deviceInfo.blBlock), File(vm.logic.abmDir, "backup_lk.img"))
		try {
			vm.copyPriv(vm.flashStream(flashType), File(vm.deviceInfo.blBlock))
		} catch (e: IOException) {
			terminal.add("-- Failed to flash bootloader, cause:")
			terminal.add(if (e.message != null) e.message!! else "(null)")
			terminal.add("-- Please consult documentation to finish the install.")
		}
		terminal.add("-- Done.")
		vm.activity.runOnUiThread {
			vm.btnsOverride = true
			vm.nextText.value = "Finish"
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
			Select(vm)
		}
	}
}