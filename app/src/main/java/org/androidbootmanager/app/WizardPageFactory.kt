package org.androidbootmanager.app

import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

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
		})
	}
}

@Composable
fun Start(vm: WizardActivityState) {
	Text(text = "Hello name!")

}

@Composable
fun Select(vm: WizardActivityState) {
	val nextButtonAvailable = remember { mutableStateOf(false) }
	val flashType = "DroidBootFlashType"

	if (nextButtonAvailable.value) {
		vm.nextText.value = "Next"
		vm.onNext.value = { it.navigate("done") }
	} else {
		Button(onClick = { vm.activity.chooseFile("*/*") {
			vm.flashes[flashType] = it
			nextButtonAvailable.value = true
		} }) {
			Text("Choose file")
		}
	}
}