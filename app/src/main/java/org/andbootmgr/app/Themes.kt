package org.andbootmgr.app

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.compose.rememberNavController
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
	val col = { it: String -> it.startsWith("0x") && it.length == 8 && (it.substring(2).toIntOrNull(16) ?: -1) in 0..0xffffff }
	val configs = listOf(
		Config(stringResource(id = R.string.win_bg_color), "win_bg_color", "0x000000", col),
		Config(stringResource(id = R.string.win_radius), "win_radius", "0") { it.toShortOrNull() != null },
		Config(stringResource(id = R.string.win_border_size), "win_border_size", "0") { it.toShortOrNull() != null },
		Config(stringResource(id = R.string.win_border_color), "win_border_color", "0xffffff", col),
		Config(stringResource(id = R.string.list_bg_color), "list_bg_color", "0x000000", col),
		Config(stringResource(id = R.string.list_radius), "list_radius", "0") { it.toShortOrNull() != null },
		Config(stringResource(id = R.string.list_border_size), "list_border_size", "0") { it.toShortOrNull() != null },
		Config(stringResource(id = R.string.list_border_color), "list_border_color", "0xffffff", col),
		Config(stringResource(id = R.string.global_font_size), "global_font_size", "0") { it.toIntOrNull() != null },
		Config(stringResource(id = R.string.global_font_name), "global_font_name", "") { true /* should check if exists later */ },
		Config(stringResource(id = R.string.button_unselected_color), "button_unselected_color", "0x000000", col),
		Config(stringResource(id = R.string.button_unselected_text_color), "button_unselected_text_color","0xffffff", col),
		Config(stringResource(id = R.string.button_unselected_radius), "button_unselected_radius", "0") { it.toShortOrNull() != null },
		Config(stringResource(id = R.string.button_selected_color), "button_selected_color", "0xff9800", col),
		Config(stringResource(id = R.string.button_selected_text_color), "button_selected_text_color", "0x000000", col),
		Config(stringResource(id = R.string.button_selected_radius), "button_selected_radius", "0") { it.toShortOrNull() != null },
		Config(stringResource(id = R.string.button_grow_default), "button_grow_default", "true") { it.toBooleanStrictOrNull() != null }, // TODO checkbox
		Config(stringResource(id = R.string.button_border_unselected_color), "button_border_unselected_color", "0xffffff", col),
		Config(stringResource(id = R.string.button_border_unselected_size), "button_border_unselected_size", "1") { it.toIntOrNull() != null },
		Config(stringResource(id = R.string.button_border_selected_color), "button_border_selected_color", "0xffffff", col),
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
			ConfigTextField(c, e, resetCounter, cfg.text, cfg.configKey, cfg.default, cfg.validate)
		}
	}
}

@Composable
private fun ConfigTextField(c: ConfigFile, e: SnapshotStateMap<String, Boolean>, resetCounter: Int, text: String, configKey: String, default: String, validate: (String) -> Boolean) {
	var value by remember(key1 = configKey, key2 = resetCounter) {
		if (!c.has(configKey)) c[configKey] = default
		mutableStateOf(c[configKey] ?: default)
	}
	TextField(
		value = value,
		onValueChange = {
			value = it
			c[configKey] = it.trim()
			e[configKey] = validate(it)
		},
		label = { Text(text) },
		isError = !(e[configKey] ?: true)
	)
	if (e[configKey] == false) {
		Text(stringResource(id = R.string.invalid_in), color = MaterialTheme.colorScheme.error)
	} else {
		Text("") // Budget spacer
	}
}

private data class Config(val text: String, val configKey: String, val default: String,
                          val validate: (String) -> Boolean)

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