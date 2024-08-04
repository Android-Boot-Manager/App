package org.andbootmgr.app.themes

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.navigation.compose.rememberNavController
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import org.andbootmgr.app.AppContent
import org.andbootmgr.app.MainActivityState
import org.andbootmgr.app.R

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
			if (errors.find { it.value } == null)
				Toast.makeText(
					vm.mvm.activity!!,
					vm.mvm.activity!!.getString(R.string.invalid_in),
					Toast.LENGTH_LONG
				).show()
			else {
				vm.mvm.editDefaultCfg(changes)
				changes.clear()
			}
		} }
		Card(modifier = Modifier
			.fillMaxWidth()
			.padding(5.dp)) {
			Column(modifier = Modifier.padding(10.dp)) {
				Text(stringResource(id = R.string.simulator_info))
				Button(onClick = {
					saveChanges()
					// sync does not work :/
					vm.mvm.unmountBootset()
					vm.mvm.mountBootset()
					vm.mvm.activity!!.startActivity(Intent(vm.mvm.activity!!, Simulator::class.java).apply {
						putExtra("sdCardBlock", vm.mvm.deviceInfo!!.bdev)
					})
				}) {
					Text(text = stringResource(id = R.string.simulator))
				}
			}
		}
		Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
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
				Text(stringResource(cfg.text))
				Row(verticalAlignment = Alignment.CenterVertically) {
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
						modifier = Modifier.padding(start = 8.dp)
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
				Text(stringResource(id = cfg.text))
				Checkbox(checked = value.toBooleanStrictOrNull() == true, onCheckedChange = {
					changes[cfg.configKey] = it.toString()
				})
			} else {
				Text(stringResource(id = cfg.text))
				OutlinedTextField(
					value = value,
					onValueChange = {
						changes[cfg.configKey] = it.trim()
					},
					isError = error
				)
				if (error) {
					Text(
						stringResource(id = R.string.invalid_in),
						color = MaterialTheme.colorScheme.error
					)
				} else {
					Text("") // Budget spacer
				}
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

class ThemeViewModel(val mvm: MainActivityState) : ViewModel() {
	val configs = listOf(
		ColorConfig(R.string.win_bg_color, "win_bg_color", "0x000000"),
		Config(R.string.win_radius, "win_radius", "0") { it.toShortOrNull() != null },
		Config(R.string.win_border_size, "win_border_size", "0") { it.toShortOrNull() != null },
		ColorConfig(R.string.win_border_color, "win_border_color", "0xffffff"),
		ColorConfig(R.string.list_bg_color, "list_bg_color", "0x000000"),
		Config(R.string.list_radius, "list_radius", "0") { it.toShortOrNull() != null },
		Config(R.string.list_border_size, "list_border_size", "0") { it.toShortOrNull() != null },
		ColorConfig(R.string.list_border_color, "list_border_color", "0xffffff"),
		Config(R.string.global_font_size, "global_font_size", "0") { it.toIntOrNull() != null },
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
		Config(
			R.string.button_unselected_radius,
			"button_unselected_radius",
			"0"
		) { it.toShortOrNull() != null },
		ColorConfig(R.string.button_selected_color, "button_selected_color", "0xff9800"),
		ColorConfig(R.string.button_selected_text_color, "button_selected_text_color", "0x000000"),
		Config(
			R.string.button_selected_radius,
			"button_selected_radius",
			"0"
		) { it.toShortOrNull() != null },
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
		Config(
			R.string.button_border_unselected_size,
			"button_border_unselected_size",
			"1"
		) { it.toIntOrNull() != null },
		ColorConfig(
			R.string.button_border_selected_color,
			"button_border_selected_color",
			"0xffffff"
		),
		Config(
			R.string.button_border_selected_size,
			"button_border_selected_size",
			"1"
		) { it.toIntOrNull() != null }
	)
}

@Preview
@Composable
fun ThemePreview() {
	val vm = MainActivityState()
	val navController = rememberNavController()
	val drawerState = rememberDrawerState(DrawerValue.Closed)
	val scope = rememberCoroutineScope()
	vm.navController = navController
	vm.drawerState = drawerState
	vm.scope = scope
	AppContent(vm) {
		Box(modifier = Modifier.padding(it)) {
			Themes(vm.theme)
		}
	}
}