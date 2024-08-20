package org.andbootmgr.app.util

import android.annotation.TargetApi
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun AbmTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
	val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
	val colorScheme = when {
		dynamicColor && darkTheme -> @TargetApi(Build.VERSION_CODES.S) {
			dynamicDarkColorScheme(LocalContext.current)
		}

		dynamicColor && !darkTheme -> @TargetApi(Build.VERSION_CODES.S) {
			dynamicLightColorScheme(LocalContext.current)
		}

		darkTheme -> darkColorScheme()
		else -> lightColorScheme()
	}

	MaterialTheme(
		colorScheme = colorScheme,
		typography = Typography(),
		content = {
			// A surface container using the 'background' color from the theme
			Surface(
				modifier = Modifier.fillMaxSize(),
				color = MaterialTheme.colorScheme.background,
				content = content
			)
		}
	)
}