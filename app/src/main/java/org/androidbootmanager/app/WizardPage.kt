package org.androidbootmanager.app

import androidx.compose.runtime.Composable
import java.util.function.Consumer

interface WizardPage {
	var name: String
	var prev: String?
	var next: String?
	var prevText: String
	var nextText: String
	var onPrev: Consumer<WizardActivity>
	var onNext: Consumer<WizardActivity>
	var run: @Composable () -> Unit
}