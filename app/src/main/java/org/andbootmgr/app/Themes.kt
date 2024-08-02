package org.andbootmgr.app

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import org.andbootmgr.app.util.ConfigFile
import java.io.File
import java.util.Random

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
	uint8_t button_radius;
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
	val e = remember { mutableStateMapOf<Long, Boolean>() }
	val col = { it: String -> it.startsWith("0x") && (it.toIntOrNull(16) ?: -1) in 0..0xffffff }
	Column {
		ConfigTextField(c, e, stringResource(id = R.string.win_bg_color), "win_bg_color", validate = col)
		ConfigTextField(c, e, stringResource(id = R.string.win_radius), "win_radius") { it.toShortOrNull() != null }
		ConfigTextField(c, e, stringResource(id = R.string.win_border_size), "win_border_size") { it.toShortOrNull() != null }
		ConfigTextField(c, e, stringResource(id = R.string.win_border_color), "win_border_color", validate = col)
		ConfigTextField(c, e, stringResource(id = R.string.list_bg_color), "list_bg_color", validate = col)
		ConfigTextField(c, e, stringResource(id = R.string.list_radius), "list_radius") { it.toShortOrNull() != null }
		ConfigTextField(c, e, stringResource(id = R.string.list_border_size), "list_border_size") { it.toShortOrNull() != null }
		ConfigTextField(c, e, stringResource(id = R.string.list_border_color), "list_border_color", validate = col)
		ConfigTextField(c, e, stringResource(id = R.string.global_font_size), "global_font_size") { it.toIntOrNull() != null }
		ConfigTextField(c, e, stringResource(id = R.string.global_font_name), "global_font_name") { true /* should check if exists later */ }
		ConfigTextField(c, e, stringResource(id = R.string.button_unselected_color), "button_unselected_color", validate = col)
		ConfigTextField(c, e, stringResource(id = R.string.button_unselected_text_color), "button_unselected_text_color", validate = col)
		ConfigTextField(c, e, stringResource(id = R.string.button_unselected_size), "button_unselected_size") { it.toIntOrNull() != null }
		ConfigTextField(c, e, stringResource(id = R.string.button_selected_color), "button_selected_color", validate = col)
		ConfigTextField(c, e, stringResource(id = R.string.button_selected_text_color), "button_selected_text_color", validate = col)
		ConfigTextField(c, e, stringResource(id = R.string.button_selected_size), "button_selected_size") { it.toIntOrNull() != null }
		ConfigTextField(c, e, stringResource(id = R.string.button_radius), "button_radius") { it.toShortOrNull() != null }
		ConfigTextField(c, e, stringResource(id = R.string.button_grow_default), "button_grow_default") { it.toBooleanStrictOrNull() != null } // TODO checkbox
		ConfigTextField(c, e, stringResource(id = R.string.button_border_unselected_color), "button_border_unselected_color", validate = col)
		ConfigTextField(c, e, stringResource(id = R.string.button_border_unselected_size), "button_border_unselected_size") { it.toIntOrNull() != null }
		ConfigTextField(c, e, stringResource(id = R.string.button_border_selected_color), "button_border_selected_color", validate = col)
		ConfigTextField(c, e, stringResource(id = R.string.button_border_selected_size), "button_border_selected_size") { it.toIntOrNull() != null }


		Button(onClick = {
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
		}, enabled = !e.containsValue(false)) {
			Text(stringResource(R.string.save_changes))
		}
		Button(onClick = {
			vm.activity!!.startActivity(Intent(vm.activity!!, Simulator::class.java).apply {
				putExtra("sdCardBlock", vm.deviceInfo!!.bdev)
			})
		}) {
			Text(text = stringResource(id = R.string.simulator))
		}
	}
}

@Composable
private fun ConfigTextField(c: ConfigFile, e: SnapshotStateMap<Long, Boolean>, text: String, configKey: String, default: String = "", validate: (String) -> Boolean) {
	val id = remember { Random().nextLong() }
	var value by remember { mutableStateOf(c[configKey] ?: default) }
	TextField(
		value = value,
		onValueChange = {
			value = it
			c[configKey] = it.trim()
			e[id] = validate(text)
		},
		label = { Text(text) },
		isError = !(e[id] ?: true)
	)
	if (e[id] == false) {
		Text(stringResource(id = R.string.invalid_in), color = MaterialTheme.colorScheme.error)
	} else {
		Text("") // Budget spacer
	}
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
			Themes(vm)
		}
	}
}