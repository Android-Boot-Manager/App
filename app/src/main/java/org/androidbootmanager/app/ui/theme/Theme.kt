package org.androidbootmanager.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun AbmTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
	//val context = LocalContext.current
	val colors = if (darkTheme) {
		darkColorScheme()
	} else {
		lightColorScheme()
	}

	MaterialTheme(
			colorScheme = colors,
			typography = Typography,
			content = content
	)
}