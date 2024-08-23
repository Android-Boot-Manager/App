package org.andbootmgr.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.nio.ExtendedFile
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.math.abs

open class ActionAbortedError(e: Exception?) : Exception(e)
class ActionAbortedCleanlyError(e: Exception?) : ActionAbortedError(e)

@Composable
fun LoadingCircle(text: String, modifier: Modifier = Modifier, paddingBetween: Dp = 20.dp) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceAround,
		modifier = modifier
	) {
		CircularProgressIndicator(Modifier.padding(end = paddingBetween))
		Text(text)
	}
}

fun JSONObject.getStringOrNull(key: String) = if (has(key)) getString(key) else null

val safeFsRegex = Regex("\\A[A-Za-z0-9_-]+\\z")
val asciiNonEmptyRegex = Regex("\\A\\p{ASCII}+\\z")
val numberRegex = Regex("\\A\\d+\\z")
val partitionTypeCodes = listOf(
	Pair("0700", R.string.portable_part),
	Pair("8302", R.string.os_userdata),
	Pair("8305", R.string.os_system))