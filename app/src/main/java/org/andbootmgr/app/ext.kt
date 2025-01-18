package org.andbootmgr.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.json.JSONObject

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

@Composable
fun MyFilterChipBar(selected: Int, values: List<String>, onSelectionChanged: (Int) -> Unit) {
	Row {
		values.forEachIndexed { i, text ->
			FilterChip(
				selected = selected == i,
				onClick = {
					onSelectionChanged(i)
				},
				label = { Text(text) },
				Modifier.padding(start = 5.dp),
				leadingIcon = if (selected == i) {
					{
						Icon(
							imageVector = Icons.Filled.Done,
							contentDescription = stringResource(id = R.string.enabled_content_desc),
							modifier = Modifier.size(FilterChipDefaults.IconSize)
						)
					}
				} else {
					null
				}
			)
		}
	}
}

@Composable
fun MyInfoCard(text: String, padding: Dp = 0.dp) {
	Card(
		modifier = Modifier
			.fillMaxWidth()
			.padding(padding)
	) {
		Row(
			Modifier
				.fillMaxWidth()
				.padding(20.dp)
		) {
			Icon(
				painterResource(id = R.drawable.ic_about),
				stringResource(id = R.string.icon_content_desc)
			)
			Text(text)
		}
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