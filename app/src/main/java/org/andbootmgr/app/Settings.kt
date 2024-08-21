package org.andbootmgr.app

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@Composable
fun Settings(vm: MainActivityState) {
	val ctx = LocalContext.current
	val changes = remember { mutableStateMapOf<String, String>() }
	val defaultErr by remember { derivedStateOf {
		val defaultText = changes["default"] ?: vm.defaultCfg["default"] ?: "Entry 01"
		defaultText.isBlank() || !defaultText.matches(Regex("[\\dA-Za-z ]+")) } }
	val timeoutErr by remember { derivedStateOf {
		val timeoutText = changes["timeout"] ?: vm.defaultCfg["timeout"] ?: "20"
		timeoutText.isBlank() || !timeoutText.matches(Regex("\\d+")) } }
	Column(modifier = Modifier.padding(horizontal = 5.dp)) {
		OutlinedTextField(
			value = changes["default"] ?: vm.defaultCfg["default"] ?: "Entry 01",
			onValueChange = {
				changes["default"] = it
			},
			label = { Text(stringResource(R.string.default_entry)) },
			isError = defaultErr,
			modifier = Modifier.fillMaxWidth()
				.padding(vertical = with(LocalDensity.current) { 8.sp.toDp() }),
			keyboardOptions = KeyboardOptions(
				keyboardType = KeyboardType.Text,
				capitalization = KeyboardCapitalization.None,
				imeAction = ImeAction.Next,
				autoCorrect = false
			)
		)
		OutlinedTextField(
			value = changes["timeout"] ?: vm.defaultCfg["timeout"] ?: "20",
			onValueChange = {
				changes["timeout"] = it
			},
			label = { Text(stringResource(R.string.timeout_secs)) },
			isError = timeoutErr,
			modifier = Modifier.fillMaxWidth()
				.padding(vertical = with(LocalDensity.current) { 8.sp.toDp() }),
			keyboardOptions = KeyboardOptions(
				keyboardType = KeyboardType.Decimal,
				capitalization = KeyboardCapitalization.None,
				imeAction = ImeAction.Next,
				autoCorrect = false
			)
		)
		Button(onClick = {
			if (defaultErr || timeoutErr)
				Toast.makeText(vm.activity!!, vm.activity.getString(R.string.invalid_in), Toast.LENGTH_LONG).show()
			else CoroutineScope(Dispatchers.Main).launch {
				vm.editDefaultCfg(changes)
				changes.clear()
			}
		}, enabled = !(defaultErr || timeoutErr)) {
			Text(stringResource(R.string.save_changes))
		}
		Button(onClick = {
			vm.currentWizardFlow = UpdateDroidBootFlow()
		}) {
			Text(stringResource(R.string.update_droidboot))
		}
		OutlinedButton(onClick = {
			vm.unmountBootset()
			vm.activity!!.finish()
		}) {
			Text(stringResource(R.string.umount))
		}
		Row(horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp)) {
			Text(stringResource(R.string.noob_mode))
			Switch(checked = vm.noobMode, onCheckedChange = {
				ctx.getSharedPreferences("abm", 0).edit().putBoolean("noob_mode", it).apply()
				vm.noobMode = it
			})
		}
	}
}