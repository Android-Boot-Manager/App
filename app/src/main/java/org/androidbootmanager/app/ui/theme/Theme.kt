package org.androidbootmanager.app.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

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