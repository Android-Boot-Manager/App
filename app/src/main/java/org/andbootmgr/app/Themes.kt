package org.andbootmgr.app

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import org.andbootmgr.app.util.ConfigFile
import java.io.File

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
fun Themes(vm: MainActivityState) {
	val c = remember {
		try {
			if (vm.logic == null)
				throw ActionAbortedCleanlyError(Exception("Compose preview special-casing"))
			ConfigFile.importFromFile(File(vm.logic!!.abmDb, "db.conf"))
		} catch (e: ActionAbortedCleanlyError) {
			if (vm.activity != null) // Compose preview special-casing
				Toast.makeText(vm.activity, vm.activity!!.getString(R.string.malformed_dbcfg), Toast.LENGTH_LONG).show()
			ConfigFile().also {
				it["default"] = "Entry 01"
				it["timeout"] = "5"
			}
		}
	}
	val e = remember { mutableStateMapOf<String, Boolean>() }
	var resetCounter by remember { mutableIntStateOf(0) }
	val configs = listOf(
		ColorConfig(stringResource(id = R.string.win_bg_color), "win_bg_color", "0x000000"),
		Config(stringResource(id = R.string.win_radius), "win_radius", "0") { it.toShortOrNull() != null },
		Config(stringResource(id = R.string.win_border_size), "win_border_size", "0") { it.toShortOrNull() != null },
		ColorConfig(stringResource(id = R.string.win_border_color), "win_border_color", "0xffffff"),
		ColorConfig(stringResource(id = R.string.list_bg_color), "list_bg_color", "0x000000"),
		Config(stringResource(id = R.string.list_radius), "list_radius", "0") { it.toShortOrNull() != null },
		Config(stringResource(id = R.string.list_border_size), "list_border_size", "0") { it.toShortOrNull() != null },
		ColorConfig(stringResource(id = R.string.list_border_color), "list_border_color", "0xffffff"),
		Config(stringResource(id = R.string.global_font_size), "global_font_size", "0") { it.toIntOrNull() != null },
		Config(stringResource(id = R.string.global_font_name), "global_font_name", "") { true /* should check if exists later */ },
		ColorConfig(stringResource(id = R.string.button_unselected_color), "button_unselected_color", "0x000000"),
		ColorConfig(stringResource(id = R.string.button_unselected_text_color), "button_unselected_text_color","0xffffff"),
		Config(stringResource(id = R.string.button_unselected_radius), "button_unselected_radius", "0") { it.toShortOrNull() != null },
		ColorConfig(stringResource(id = R.string.button_selected_color), "button_selected_color", "0xff9800"),
		ColorConfig(stringResource(id = R.string.button_selected_text_color), "button_selected_text_color", "0x000000"),
		Config(stringResource(id = R.string.button_selected_radius), "button_selected_radius", "0") { it.toShortOrNull() != null },
		Config(stringResource(id = R.string.button_grow_default), "button_grow_default", "true") { it.toBooleanStrictOrNull() != null }, // TODO checkbox
		ColorConfig(stringResource(id = R.string.button_border_unselected_color), "button_border_unselected_color", "0xffffff"),
		Config(stringResource(id = R.string.button_border_unselected_size), "button_border_unselected_size", "1") { it.toIntOrNull() != null },
		ColorConfig(stringResource(id = R.string.button_border_selected_color), "button_border_selected_color", "0xffffff"),
		Config(stringResource(id = R.string.button_border_selected_size), "button_border_selected_size", "1") { it.toIntOrNull() != null }
	)
	val state = rememberScrollState()
	Column(modifier = Modifier
		.verticalScroll(state)
		.fillMaxSize()) {
		val saveChanges = {
			if (e.containsValue(false))
				Toast.makeText(
					vm.activity!!,
					vm.activity!!.getString(R.string.invalid_in),
					Toast.LENGTH_LONG
				).show()
			else {
				try {
					c.exportToFile(File(vm.logic!!.abmDb, "db.conf"))
				} catch (e: ActionAbortedError) {
					Toast.makeText(
						vm.activity!!,
						vm.activity!!.getString(R.string.failed2save),
						Toast.LENGTH_LONG
					)
						.show()
				}
			}
		}
		Card(modifier = Modifier
			.fillMaxWidth()
			.padding(10.dp)) {
			Column(modifier = Modifier.padding(10.dp)) {
				Text(stringResource(id = R.string.simulator_info))
				Button(onClick = {
					saveChanges()
					// sync does not work :/
					vm.logic!!.unmountBootset()
					vm.logic!!.mountBootset(vm.deviceInfo!!)
					vm.activity!!.startActivity(Intent(vm.activity!!, Simulator::class.java).apply {
						putExtra("sdCardBlock", vm.deviceInfo!!.bdev)
					})
				}) {
					Text(text = stringResource(id = R.string.simulator))
				}
			}
		}
		Row {
			Button(onClick = saveChanges, enabled = !e.containsValue(false)) {
				Text(stringResource(R.string.save_changes))
			}
			Button(onClick = {
				for (cfg in configs) {
					c[cfg.configKey] = cfg.default
				}
				resetCounter++
				saveChanges()
			}) {
				Text(stringResource(R.string.reset))
			}
		}
		for (cfg in configs) {
			if (cfg is ColorConfig) {
				var value by remember(resetCounter) {
					if (!c.has(cfg.configKey)) c[cfg.configKey] = cfg.default
					mutableStateOf(c[cfg.configKey] ?: cfg.default)
				}
				var edit by remember(resetCounter) { mutableStateOf(false) }
				Row(verticalAlignment = Alignment.CenterVertically) {
					Text(cfg.text, modifier = Modifier.padding(end = 8.dp))
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
					Button(onClick = { edit = true }, modifier = Modifier.padding(start = 8.dp)) {
						Text(stringResource(id = R.string.edit))
					}
				}
				if (edit) {
					val color = rememberColorPickerController()
					AlertDialog(onDismissRequest = { edit = false }, confirmButton = {
						Button(onClick = {
							value = "0x" + (color.selectedColor.value.toArgb() and (0xff shl 24).inv()).toHexString().substring(2, 8)
							c[cfg.configKey] = value
							e[cfg.configKey] = cfg.validate(value)
							edit = false
						}) {
							Text(stringResource(id = R.string.ok))
						}
					}, dismissButton = {
						Button(onClick = { edit = false }) {
							Text(stringResource(id = R.string.cancel))
						}
					}, title = { Text(cfg.text) }, text = {
						Column {
							HsvColorPicker(modifier = Modifier.height(200.dp), controller = color, initialColor = Color(
								(value.substring(2).toIntOrNull(16) ?: 0) or (0xff shl 24)
							))
							BrightnessSlider(modifier = Modifier.height(35.dp), controller = color)
						}
					})
				}

			} else {
				var value by remember(key1 = cfg.configKey, key2 = resetCounter) {
					if (!c.has(cfg.configKey)) c[cfg.configKey] = cfg.default
					mutableStateOf(c[cfg.configKey] ?: cfg.default)
				}
				TextField(
					value = value,
					onValueChange = {
						value = it
						c[cfg.configKey] = it.trim()
						e[cfg.configKey] = cfg.validate(it)
					},
					label = { Text(cfg.text) },
					isError = !(e[cfg.configKey] ?: true)
				)
				if (e[cfg.configKey] == false) {
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

private open class Config(val text: String, val configKey: String, val default: String,
                          val validate: (String) -> Boolean)
private class ColorConfig(text: String, configKey: String, default: String) : Config(text, configKey,
	default, { it.startsWith("0x") && it.length == 8 &&
			(it.substring(2).toIntOrNull(16) ?: -1) in 0..0xffffff })

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
			Themes(vm)
		}
	}
}