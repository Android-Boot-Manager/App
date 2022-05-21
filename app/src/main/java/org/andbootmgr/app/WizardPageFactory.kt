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
import com.topjohnwu.superuser.Shell
import org.andbootmgr.app.ui.theme.AbmTheme
import java.io.File

class WizardPageFactory(private val vm: WizardActivityState) {
	fun get(flow: String): List<IWizardPage> {
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
fun Start(vm: WizardActivityState) {
	Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
		modifier = Modifier.fillMaxSize()
	) {
		Text("Welcome to ABM!")
		Text("This will install ABM + DroidBoot")
	}
}

@Composable
fun Select(vm: WizardActivityState) {
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
			Text("thanks for choosing")
			vm.nextText.value = "Next"
			vm.onNext.value = { it.navigate("flash") }
		} else {
			Text("please choose lk image")
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
fun Flash(vm: WizardActivityState) {
	val flashType = "DroidBootFlashType"
	Terminal(vm) { terminal ->
		terminal.add("Copying file...")
		val f = File(vm.cacheDir, "dboot.img")
		vm.copyToCache(flashType, f)
		terminal.add("Starting commands...")
		Shell.cmd("echo hi; sleep 3; echo bye; ls;sleep 3;ls; sleep 3;ls;ls; whoami; file ${f.absolutePath}").to(terminal).submit {
			f.delete()
			vm.activity.runOnUiThread {
				vm.nextText.value = "Finish"
				vm.onNext.value = {
					it.finish()
				}
			}
		}
	}
}

@Composable
@Preview
fun Preview() {
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