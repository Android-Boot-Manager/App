package org.androidbootmanager.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun AbmTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
	val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
	val colorScheme = when {
		dynamicColor && darkTheme -> {
			dynamicDarkColorScheme(LocalContext.current)
		}
		dynamicColor && !darkTheme -> {
			dynamicLightColorScheme(LocalContext.current)
		}
		darkTheme -> darkColorScheme()
		else -> lightColorScheme()
	}

	MaterialTheme(
			colorScheme = colorScheme,
			typography = Typography,
			content = content
	)
}