package org.androidbootmanager.app

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavHostController
import java.util.function.Consumer

class WizardActivityState(val codename: String) {
	lateinit var navController: NavHostController
	lateinit var activity: WizardActivity
	val deviceInfo = HardcodedDeviceInfoFactory.get(codename)
	var prev: MutableState<String?> = mutableStateOf(null)
	var current = mutableStateOf("start")
	var next: MutableState<String?> = mutableStateOf(null)
	var prevText = mutableStateOf("Prev")
	var nextText = mutableStateOf("Next")
	var onPrev: MutableState<Consumer<WizardActivity>> = mutableStateOf(Consumer {
		it.finish()
	})
	var onNext: MutableState<Consumer<WizardActivity>> = mutableStateOf(Consumer {
		it.finish()
	})

	fun navigateUp() {
		current.value = next.value!!
		navController.navigate(current.value)
	}
	fun navigateDown() {
		current.value = prev.value!!
		navController.navigate(current.value)
	}
}