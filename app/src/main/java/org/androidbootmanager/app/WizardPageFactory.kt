package org.androidbootmanager.app

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import java.util.function.Consumer

class WizardPageFactory(private val vm: WizardActivityState) {
	fun get(flow: String): List<WizardPage> {
		return listOf(object : WizardPage {
			override var name: String = "start"
			override var prev: String? = null
			override var next: String? = "select"
			override var prevText: String = "Cancel"
			override var nextText: String = "Next"
			override var onPrev: Consumer<WizardActivity> = Consumer { it.finish() }
			override var onNext: Consumer<WizardActivity> = Consumer { it.finish() }
			override var run: @Composable () -> Unit = @Composable {
				Start(vm)
			}
		}, object : WizardPage {
			override var name: String = "select"
			override var prev: String? = "start"
			override var next: String? = null
			override var prevText: String = "Prev"
			override var nextText: String = "Finish"
			override var onPrev: Consumer<WizardActivity> = Consumer { it.finish() }
			override var onNext: Consumer<WizardActivity> = Consumer { it.finish() }
			override var run: @Composable () -> Unit = @Composable {
				Select(vm)
			}
		})
	}
}

@Composable
fun Start(vm: WizardActivityState) {
	Text(text = "Hello name!")
}

@Composable
fun Select(vm: WizardActivityState) {
	Text(text = "Hello 2!")
}