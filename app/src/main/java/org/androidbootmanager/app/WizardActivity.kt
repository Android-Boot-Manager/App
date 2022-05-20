package org.androidbootmanager.app

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.androidbootmanager.app.ui.theme.AbmTheme

class WizardActivity : ComponentActivity() {
	lateinit var vm: WizardActivityState
	private lateinit var chooseFile: ActivityResultLauncher<String>
	private var onFileChosen: ((Uri) -> Unit)? = null
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		vm = WizardActivityState(intent.getStringExtra("codename")!!)
		vm.activity = this
		chooseFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
			if (uri == null) {
				Toast.makeText(this, "File not available, please try again later!", Toast.LENGTH_LONG).show()
				return@registerForActivityResult
			}
			if (onFileChosen != null) {
				onFileChosen!!(uri)
				onFileChosen = null
			} else {
				Toast.makeText(
					this@WizardActivity,
					"Internal error - no file handler added!! Please cancel install",
					Toast.LENGTH_LONG
				).show()
			}

		}
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
									vm.prevText.value = i.prev.text
									vm.nextText.value = i.next.text
									vm.onPrev.value = i.prev.onClick
									vm.onNext.value = i.next.onClick
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

	fun navigate(next: String) {
		vm.navigate(next)
	}
	fun chooseFile(mime: String, callback: (Uri) -> Unit) {
		if (onFileChosen != null) {
			Toast.makeText(
				this,
				"Internal error - double file choose!! Please cancel install",
				Toast.LENGTH_LONG
			).show()
			return
		}
		onFileChosen = callback
		chooseFile.launch(mime)
	}
}

class WizardActivityState(val codename: String) {
	lateinit var navController: NavHostController
	lateinit var activity: WizardActivity
	val deviceInfo = HardcodedDeviceInfoFactory.get(codename)
	var current = mutableStateOf("start")
	var prevText = mutableStateOf("Prev")
	var nextText = mutableStateOf("Next")
	var onPrev: MutableState<(WizardActivity) -> Unit> = mutableStateOf({
		it.finish()
	})
	var onNext: MutableState<(WizardActivity) -> Unit> = mutableStateOf({
		it.finish()
	})

	var flashes: HashMap<String, Uri> = HashMap()

	fun navigate(next: String) {
		current.value = next
		navController.navigate(current.value)
	}
}

class NavButton(val text: String, val onClick: (WizardActivity) -> Unit)
class WizardPage(override val name: String, override val prev: NavButton,
                       override val next: NavButton, override val run: @Composable () -> Unit
) : IWizardPage

interface IWizardPage {
	val name: String
	val prev: NavButton
	val next: NavButton
	val run: @Composable () -> Unit
}

@Composable
fun BtnsRow(vm: WizardActivityState) {
	Row {
		TextButton(onClick = {
			vm.onPrev.value(vm.activity)
		}, modifier = Modifier.weight(1f, true)) {
			Text(vm.prevText.value)
		}
		TextButton(onClick = {
			vm.onNext.value(vm.activity)
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