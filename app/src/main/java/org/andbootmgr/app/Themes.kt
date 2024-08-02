package org.andbootmgr.app

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
    uint8_t win_border_width;
    uint32_t win_border_color;
    uint32_t global_font_size;
    char* global_font_name;
    uint32_t button_unselected_color;
    uint32_t button_unselected_size;
    uint32_t button_unselected_text_color;
    uint32_t button_selected_color;
    uint32_t button_selected_size;
    uint32_t button_selected_text_color;
    uint8_t button_radius;
    bool button_grow_default;
    uint8_t button_border_unselected_width;
    uint32_t button_border_unselected_color;
    uint8_t button_border_selected_width;
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
	ConfigTextField(c, e, "COLOR", "win_bg_color", "hmm")
	Button(onClick = {
		if (e.containsValue(false))
			Toast.makeText(vm.activity!!, vm.activity!!.getString(R.string.invalid_in), Toast.LENGTH_LONG).show()
		else {
			try {
				c.exportToFile(File(vm.logic!!.abmDb, "db.conf"))
			} catch (e: ActionAbortedError) {
				Toast.makeText(vm.activity!!, vm.activity!!.getString(R.string.failed2save), Toast.LENGTH_LONG)
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

@Composable
private fun ConfigTextField(c: ConfigFile, e: SnapshotStateMap<Long, Boolean>, text: String, configKey: String, default: String = "") {
	val id = remember { Random().nextLong() }
	var value by remember { mutableStateOf(c[configKey] ?: default) }
	TextField(
		value = value,
		onValueChange = {
			value = it
			c[configKey] = it.trim()
			e[id] = true
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