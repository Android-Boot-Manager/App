package org.androidbootmanager.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.androidbootmanager.app.ui.theme.AbmTheme

class WizardActivity : ComponentActivity() {
	lateinit var vm: WizardActivityState
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		vm = WizardActivityState(intent.getStringExtra("codename")!!)
		vm.activity = this
		val wizardPages = WizardPageFactory(vm).get(intent.getStringExtra("flow")!!)
		setContent {
			vm.navController = rememberNavController()
			AbmTheme {
				// A surface container using the 'background' color from the theme
				Surface(
					modifier = Modifier.fillMaxSize(),
					color = MaterialTheme.colorScheme.background
				) {
					Column(verticalArrangement = Arrangement.SpaceBetween) {
						NavHost(
							navController = vm.navController,
							startDestination = "start",
							modifier = Modifier.fillMaxWidth()
						) {
							for (i in wizardPages) {
								composable(i.name) {
									vm.prev.value = i.prev
									vm.next.value = i.next
									vm.prevText.value = i.prevText
									vm.nextText.value = i.nextText
									vm.onPrev.value = i.onPrev
									vm.onNext.value = i.onNext
									i.run()
								}
							}
						}
						Box(Modifier.fillMaxWidth()) {
							BtnsRow(vm)
						}
					}
				}
			}
		}
	}
}

@Composable
fun BtnsRow(vm: WizardActivityState) {
	Row {
		TextButton(onClick = {
			if (vm.prev.value == null) {
				vm.onPrev.value.accept(vm.activity)
			} else {
				vm.navigateDown()
			}
		}, modifier = Modifier.weight(1f, true)) {
			Text(vm.prevText.value)
		}
		TextButton(onClick = {
			if (vm.next.value == null) {
				vm.onNext.value.accept(vm.activity)
			} else {
				vm.navigateUp()
			}
		}, modifier = Modifier.weight(1f, true)) {
			Text(vm.nextText.value)
		}
	}
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview2() {
	val vm = WizardActivityState("null")
	AbmTheme {
		// A surface container using the 'background' color from the theme
		Surface(
			modifier = Modifier.fillMaxSize(),
			color = MaterialTheme.colorScheme.background
		) {
			Column(verticalArrangement = Arrangement.SpaceBetween) {
				Box(Modifier.fillMaxWidth()) {
					Start(vm)
				}
				Box(Modifier.fillMaxWidth()) {
					BtnsRow(vm)
				}
			}
		}
	}
}