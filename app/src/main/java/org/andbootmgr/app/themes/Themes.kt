package org.andbootmgr.app.themes

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.navigation.compose.rememberNavController
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.andbootmgr.app.AppContent
import org.andbootmgr.app.MainActivityState
import org.andbootmgr.app.R
import org.andbootmgr.app.asMetaOnSdDeviceInfo
import org.andbootmgr.app.util.AbmTheme

/*
	uint32_t win_bg_color;
	uint8_t win_radius;
	uint8_t win_border_size;
	uint32_t win_border_color;
	uint32_t list_bg_color;
	uint8_t list_radius;
	uint8_t list_border_size;
	uint32_t list_border_color;
	uint32_t global_font_size;
	char* global_font_name;
	uint32_t button_unselected_color;
	uint32_t button_unselected_text_color;
	uint32_t button_selected_color;
	uint32_t button_selected_text_color;
	uint8_t button_unselected_radius;
	uint8_t button_selected_radius;
	bool button_grow_default;
	uint8_t button_border_unselected_size;
	uint32_t button_border_unselected_color;
	uint8_t button_border_selected_size;
	uint32_t button_border_selected_color;
 */
@OptIn(ExperimentalStdlibApi::class)
@Composable
fun Themes(vm: ThemeViewModel) {
	val changes = remember { mutableStateMapOf<String, String>() }
	val state = rememberScrollState()
	val errors = remember {
		vm.configs.map { cfg ->
			derivedStateOf {
				val value =
					changes[cfg.configKey] ?: vm.mvm.defaultCfg[cfg.configKey] ?: cfg.default
				!cfg.validate(value)
			}
		}
	}
	Column(modifier = Modifier
		.verticalScroll(state)
		.fillMaxSize()
		.padding(horizontal = 5.dp)) {
		val saveChanges = remember { {
			if (errors.find { it.value } != null)
				Toast.makeText(
					vm.mvm.activity!!,
					vm.mvm.activity.getString(R.string.invalid_in),
					Toast.LENGTH_LONG
				).show()
			else CoroutineScope(Dispatchers.Main).launch {
				vm.mvm.editDefaultCfg(changes)
				changes.clear()
			}; Unit
		} }
		Card(modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 5.dp, vertical = with(LocalDensity.current) { 8.sp.toDp() })) {
			Column(modifier = Modifier.padding(10.dp)) {
				Text(stringResource(id = R.string.simulator_info))
				Button(onClick = {
					if (errors.find { it.value } != null)
						Toast.makeText(
							vm.mvm.activity!!,
							vm.mvm.activity.getString(R.string.invalid_in),
							Toast.LENGTH_LONG
						).show()
					else CoroutineScope(Dispatchers.Main).launch {
						vm.mvm.editDefaultCfg(changes)
						changes.clear()
						// sync does not work :/
						vm.mvm.remountBootset()
						vm.mvm.activity!!.startActivity(
							Intent(
								vm.mvm.activity,
								Simulator::class.java
							).apply {
								if (vm.mvm.deviceInfo!!.metaonsd)
									putExtra("sdCardBlock",
										vm.mvm.deviceInfo!!.asMetaOnSdDeviceInfo().bdev)
								else
									putExtra("bootsetBlock", vm.mvm.logic!!.abmSdLessBootsetImg)
							}
						)
					}
				}, modifier = Modifier.align(Alignment.End).padding(top = 5.dp), enabled = errors.find { it.value } == null) {
					Text(text = stringResource(id = R.string.simulator))
				}
			}
		}
		Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()
			.padding(vertical = with(LocalDensity.current) { 8.sp.toDp() })) {
			Button(onClick = saveChanges, enabled = errors.find { it.value } == null) {
				Text(stringResource(R.string.save_changes))
			}
			Button(onClick = {
				for (cfg in vm.configs) {
					changes[cfg.configKey] = cfg.default
				}
			}) {
				Text(stringResource(R.string.reset))
			}
		}
		vm.configs.forEachIndexed { i, cfg ->
			val value = changes[cfg.configKey] ?: vm.mvm.defaultCfg[cfg.configKey] ?: cfg.default
			val error = errors[i].value
			if (cfg is ColorConfig) {
				var edit by remember { mutableStateOf(false) }
				Row(modifier = Modifier.fillMaxWidth().padding(vertical = with(LocalDensity.current) { 8.sp.toDp() }),
					verticalAlignment = Alignment.CenterVertically) {
					Text(stringResource(cfg.text))
					Spacer(modifier = Modifier.weight(1f))
					Box(
						modifier = Modifier
							.drawWithContent {
								drawRect(
									Color(
										(value
											.substring(2)
											.toIntOrNull(16) ?: 0) or (0xff shl 24)
									)
								)
							}
							.width(16.dp)
							.height(16.dp)
					)
					OutlinedButton(
						onClick = { edit = true },
						modifier = Modifier.padding(start = 12.5.dp)
					) {
						Text(stringResource(id = R.string.edit))
					}
				}
				if (edit) {
					val color = rememberColorPickerController()
					AlertDialog(onDismissRequest = { edit = false }, confirmButton = {
						Button(onClick = {
							changes[cfg.configKey] =
								"0x" + (color.selectedColor.value.toArgb() and (0xff shl 24).inv())
									.toHexString().substring(2, 8)
							edit = false
						}) {
							Text(stringResource(id = R.string.ok))
						}
					}, dismissButton = {
						Button(onClick = { edit = false }) {
							Text(stringResource(id = R.string.cancel))
						}
					}, title = { Text(stringResource(id = cfg.text)) }, text = {
						Column {
							HsvColorPicker(
								modifier = Modifier.height(200.dp).padding(bottom = 10.dp),
								controller = color,
								initialColor = Color(
									(value.substring(2).toIntOrNull(16) ?: 0) or (0xff shl 24)
								)
							)
							BrightnessSlider(modifier = Modifier.height(35.dp), controller = color)
						}
					})
				}
			} else if (cfg is BoolConfig) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					Text(stringResource(id = cfg.text))
					Spacer(modifier = Modifier.weight(1f))
					Checkbox(checked = value.toBooleanStrictOrNull() == true, onCheckedChange = {
						changes[cfg.configKey] = it.toString()
					})
				}
			} else {
				OutlinedTextField(
					value = value,
					modifier = Modifier.fillMaxWidth()
						.let {
							if (i > 0 && vm.configs[i - 1] is ColorConfig)
								it.padding(top = with(LocalDensity.current) { 4.sp.toDp() } ,bottom = with(LocalDensity.current) { 8.sp.toDp() })
							else
								it.padding(vertical = with(LocalDensity.current) { 8.sp.toDp() })
						},
					onValueChange = {
						changes[cfg.configKey] = it.trim()
					},
					label = { Text(stringResource(id = cfg.text)) },
					isError = error,
					keyboardOptions = KeyboardOptions(
						keyboardType = if (cfg is IntConfig || cfg is ShortConfig)
							KeyboardType.Decimal else KeyboardType.Text,
						capitalization = KeyboardCapitalization.None,
						imeAction = ImeAction.Next,
						autoCorrect = false
					)
				)
			}
		}
	}
}

open class Config(val text: Int, val configKey: String, val default: String,
                          val validate: (String) -> Boolean)
class ColorConfig(text: Int, configKey: String, default: String) : Config(text, configKey,
	default, { it.startsWith("0x") && it.length == 8 &&
			(it.substring(2).toIntOrNull(16) ?: -1) in 0..0xffffff })
class BoolConfig(text: Int, configKey: String, default: String) : Config(text, configKey,
	default, { it.toBooleanStrictOrNull() != null})
class IntConfig(text: Int, configKey: String, default: String)
	: Config(text, configKey, default, { it.toIntOrNull() != null })
class ShortConfig(text: Int, configKey: String, default: String)
	: Config(text, configKey, default, { it.toShortOrNull() != null })

class ThemeViewModel(val mvm: MainActivityState) : ViewModel() {
	val configs = listOf(
		ColorConfig(R.string.win_bg_color, "win_bg_color", "0x000000"),
		ShortConfig(R.string.win_radius, "win_radius", "0"),
		ShortConfig(R.string.win_border_size, "win_border_size", "0"),
		ColorConfig(R.string.win_border_color, "win_border_color", "0xffffff"),
		ColorConfig(R.string.list_bg_color, "list_bg_color", "0x000000"),
		ShortConfig(R.string.list_radius, "list_radius", "0"),
		ShortConfig(R.string.list_border_size, "list_border_size", "0"),
		ColorConfig(R.string.list_border_color, "list_border_color", "0xffffff"),
		IntConfig(R.string.global_font_size, "global_font_size", "0"),
		Config(
			R.string.global_font_name,
			"global_font_name",
			""
		) { true /* should check if exists later */ },
		ColorConfig(R.string.button_unselected_color, "button_unselected_color", "0x000000"),
		ColorConfig(
			R.string.button_unselected_text_color,
			"button_unselected_text_color",
			"0xffffff"
		),
		ShortConfig(
			R.string.button_unselected_radius,
			"button_unselected_radius",
			"0"
		),
		ColorConfig(R.string.button_selected_color, "button_selected_color", "0xff9800"),
		ColorConfig(R.string.button_selected_text_color, "button_selected_text_color", "0x000000"),
		ShortConfig(
			R.string.button_selected_radius,
			"button_selected_radius",
			"0"
		),
		BoolConfig(
			R.string.button_grow_default,
			"button_grow_default",
			"true"
		),
		ColorConfig(
			R.string.button_border_unselected_color,
			"button_border_unselected_color",
			"0xffffff"
		),
		IntConfig(
			R.string.button_border_unselected_size,
			"button_border_unselected_size",
			"1"
		),
		ColorConfig(
			R.string.button_border_selected_color,
			"button_border_selected_color",
			"0xffffff"
		),
		IntConfig(
			R.string.button_border_selected_size,
			"button_border_selected_size",
			"1"
		)
	)
}

@Preview
@Composable
fun ThemePreview() {
	val vm = MainActivityState(null)
	AbmTheme {
		AppContent(vm, rememberNavController()) {
			Box(modifier = Modifier.padding(it)) {
				Themes(vm.theme)
			}
		}
	}
}