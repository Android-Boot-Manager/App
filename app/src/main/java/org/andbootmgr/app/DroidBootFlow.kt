package org.andbootmgr.app

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.andbootmgr.app.util.ConfigFile
import org.andbootmgr.app.util.SDUtils
import org.andbootmgr.app.util.Terminal
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.IOException
import java.net.URL

class DroidBootFlow : WizardFlow() {
	override fun get(vm: WizardActivityState): List<IWizardPage> {
		val d = DroidBootFlowDataHolder(vm)
		return listOf(WizardPage("start",
			NavButton(vm.activity.getString(R.string.cancel)) { it.finish() },
			NavButton(vm.activity.getString(R.string.next)) { it.navigate("input") })
		{
			Start(vm)
		}, WizardPage("input",
			NavButton(vm.activity.getString(R.string.prev)) { it.navigate("start") },
			NavButton("") {}
		) {
			Input(d)
		}, WizardPage("dload",
			NavButton(vm.activity.getString(R.string.cancel)) { it.finish() },
			NavButton("") {}
		) {
			WizardDownloader(vm, "flash")
		}, WizardPage("flash",
			NavButton("") {},
			NavButton("") {}
		) {
			Flash(d)
		})
	}
}

class DroidBootFlowDataHolder(val vm: WizardActivityState) {
	var osName by mutableStateOf(vm.activity.getString(R.string.android))
}

@Composable
private fun Start(vm: WizardActivityState) {
	Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
		modifier = Modifier.fillMaxSize()
	) {
		Text(stringResource(R.string.welcome_text))
		Text(
			if (remember { vm.deviceInfo.isBooted(vm.logic) }) {
				stringResource(R.string.install_abm)
			} else {
				stringResource(R.string.install_abm_dboot)
			}
		)
		if (vm.deviceInfo.metaonsd) {
			Text(stringResource(R.string.sd_erase1))
			Text(stringResource(R.string.sd_erase2))
		}
	}
}

// shared across DroidBootFlow, UpdateDroidBootFlow, FixDroidBootFlow
@Composable
fun LoadDroidBootJson(vm: WizardActivityState, content: @Composable () -> Unit) {
	var loading by remember { mutableStateOf(!vm.deviceInfo.isBooted(vm.logic) || vm.deviceInfo.postInstallScript) }
	var error by remember { mutableStateOf(false) }
	LaunchedEffect(Unit) {
		if (!loading) return@LaunchedEffect
		CoroutineScope(Dispatchers.IO).launch {
			try {
				val jsonText =
					URL("https://raw.githubusercontent.com/Android-Boot-Manager/ABM-json/master/devices/" + vm.codename + ".json").readText()
				val json = JSONTokener(jsonText).nextValue() as JSONObject
				if (BuildConfig.VERSION_CODE < json.getInt("minAppVersion"))
					throw IllegalStateException("please upgrade app")
				if (!vm.deviceInfo.isBooted(vm.logic)) {
					val bl = json.getJSONObject("bootloader")
					val url = bl.getString("url")
					val sha = bl.optString("sha256")
					vm.inetAvailable["droidboot"] = WizardActivityState.Downloadable(
						url, sha, vm.activity.getString(R.string.droidboot_online)
					)
					vm.idNeeded.add("droidboot")
				}
				if (vm.deviceInfo.postInstallScript) {
					val i = json.getJSONObject("installScript")
					val url = i.getString("url")
					val sha = i.optString("sha256")
					vm.inetAvailable["_install.sh_"] = WizardActivityState.Downloadable(
						url, sha, vm.activity.getString(R.string.installer_sh)
					)
					vm.idNeeded.add("_install.sh_")
				}
				loading = false
			} catch (e: Exception) {
				Handler(Looper.getMainLooper()).post {
					Toast.makeText(vm.activity, R.string.dl_error, Toast.LENGTH_LONG).show()
				}
				Log.e("ABM droidboot json", Log.getStackTraceString(e))
				error = true
			}
		}
	}
	if (loading) {
		if (error) {
			Text(stringResource(R.string.dl_error))
		} else {
			LoadingCircle(stringResource(R.string.loading), modifier = Modifier.fillMaxSize())
		}
	} else content()
}

@Composable
private fun Input(d: DroidBootFlowDataHolder) {
	LoadDroidBootJson(d.vm) {
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center,
			modifier = Modifier.fillMaxSize()
		) {
			val e = d.osName.isBlank() || !d.osName.matches(Regex("[\\dA-Za-z]+"))

			Text(
				stringResource(R.string.enter_name_for_current),
				textAlign = TextAlign.Center,
				modifier = Modifier.padding(vertical = 5.dp)
			)
			TextField(
				value = d.osName,
				onValueChange = {
					d.osName = it
				},
				label = { Text(stringResource(R.string.os_name)) },
				isError = e
			)
			if (e) {
				Text(stringResource(R.string.invalid_in), color = MaterialTheme.colorScheme.error)
			} else {
				Text("") // Budget spacer
			}
			LaunchedEffect(e) {
				if (e) {
					d.vm.nextText = ""
					d.vm.onNext = {}
				} else {
					d.vm.nextText = d.vm.activity.getString(R.string.next)
					d.vm.onNext = { it.navigate(if (d.vm.idNeeded.isNotEmpty()) "dload" else "flash") }
				}
			}
		}
	}
}

@Composable
private fun Flash(d: DroidBootFlowDataHolder) {
	val vm = d.vm
	Terminal(logFile = "blflash_${System.currentTimeMillis()}.txt") { terminal ->
		vm.logic.extractToolkit(terminal)
		terminal.add(vm.activity.getString(R.string.term_preparing_fs))
		if (vm.logic.checkMounted()) {
			terminal.add(vm.activity.getString(R.string.term_mount_state_bad))
			return@Terminal
		}
		if (!SuFile.open(vm.logic.abmBootset.toURI()).exists()) {
			if (!SuFile.open(vm.logic.abmBootset.toURI()).mkdir()) {
				terminal.add(vm.activity.getString(R.string.term_cant_create_mount_point))
				return@Terminal
			}
		}
		if (!SuFile.open(File(vm.logic.abmBootset, ".NOT_MOUNTED").toURI()).exists()) {
			if (!SuFile.open(File(vm.logic.abmBootset, ".NOT_MOUNTED").toURI()).createNewFile()) {
				terminal.add(vm.activity.getString(R.string.term_cant_create_placeholder))
				return@Terminal
			}
		}

		if (vm.deviceInfo.metaonsd) {
			var meta = SDUtils.generateMeta(vm.deviceInfo)
			if (meta == null) {
				terminal.add(vm.activity.getString(R.string.term_cant_get_meta))
				return@Terminal
			}
			if (!Shell.cmd(SDUtils.umsd(meta)).to(terminal).exec().isSuccess) {
				terminal.add(vm.activity.getString(R.string.term_failed_umount_drive))
			}
			if (!Shell.cmd("sgdisk --mbrtogpt --clear ${vm.deviceInfo.bdev}").to(terminal)
					.exec().isSuccess
			) {
				terminal.add(vm.activity.getString(R.string.term_failed_create_pt))
				return@Terminal
			}
			meta = SDUtils.generateMeta(vm.deviceInfo)
			if (meta == null) {
				terminal.add(vm.activity.getString(R.string.term_cant_get_meta))
				return@Terminal
			}
			val r = vm.logic.create(meta.s[0] as SDUtils.Partition.FreeSpace,
						0,
						((meta.sectors - 2048) / 41 + 2048) // create meta partition proportional to sd size
							.coerceAtLeast((512L * 1024L * 1024L) / meta.logicalSectorSizeBytes) // but never less than 512mb
							.coerceAtMost((4L * 1024L * 1024L * 1024L) / meta.logicalSectorSizeBytes), // and never more than 4gb
						"8301",
						"abm_settings"
					).to(terminal).exec()
			if (r.out.joinToString("\n").contains("kpartx")) {
				terminal.add(vm.activity.getString(R.string.term_reboot_asap))
			}
			if (r.isSuccess) {
				terminal.add(vm.activity.getString(R.string.term_done))
			} else {
				terminal.add(vm.activity.getString(R.string.term_failed_create_meta))
				return@Terminal
			}
		} else {
			// TODO provision for sdless
		}

		if (!vm.logic.mountBootset(vm.deviceInfo)) {
			terminal.add(vm.activity.getString(R.string.term_failed_mount))
			return@Terminal
		}
		if (SuFile.open(File(vm.logic.abmBootset, ".NOT_MOUNTED").toURI()).exists()) {
			terminal.add(vm.activity.getString(R.string.term_mount_failure_inconsist))
			return@Terminal
		}

		if (!SuFile.open(vm.logic.abmDb.toURI()).exists()) {
			if (!SuFile.open(vm.logic.abmDb.toURI()).mkdir()) {
				terminal.add(vm.activity.getString(R.string.term_failed_create_db_dir))
				vm.logic.unmountBootset()
				return@Terminal
			}
		}
		if (!SuFile.open(vm.logic.abmEntries.toURI()).exists()) {
			if (!SuFile.open(vm.logic.abmEntries.toURI()).mkdir()) {
				terminal.add(vm.activity.getString(R.string.term_failed_create_entries_dir))
				vm.logic.unmountBootset()
				return@Terminal
			}
		}
		val tmpFile = if (vm.deviceInfo.postInstallScript) {
			val tmpFile = createTempFileSu("abm", ".sh", vm.logic.rootTmpDir)
			vm.copyPriv(vm.chosen["_install.sh_"]!!.openInputStream(vm), tmpFile)
			tmpFile.setExecutable(true)
			tmpFile
		} else null

		terminal.add(vm.activity.getString(R.string.term_building_cfg))
		val db = ConfigFile()
		db["default"] = "Entry 01"
		db["timeout"] = "5"
		db.exportToFile(File(vm.logic.abmDb, "db.conf"))
		val entry = ConfigFile()
		entry["title"] = d.osName.trim()
		entry["linux"] = "null"
		entry["initrd"] = "null"
		entry["dtb"] = "null"
		if (vm.deviceInfo.havedtbo)
			entry["dtbo"] = "null"
		entry["options"] = "null"
		entry["xtype"] = "droid"
		entry["xpart"] = "real"
		entry.exportToFile(File(vm.logic.abmEntries, "real.conf"))
		if (!vm.deviceInfo.isBooted(vm.logic)) {
			terminal.add(vm.activity.getString(R.string.term_flashing_droidboot))
			val backupLk = File(vm.logic.fileDir, "backup_lk1.img")
			val f = SuFile.open(vm.deviceInfo.blBlock)
			if (!f.canWrite())
				terminal.add(vm.activity.getString(R.string.term_cant_write_bl))
			vm.copyPriv(SuFileInputStream.open(vm.deviceInfo.blBlock), backupLk)
			try {
				vm.copyPriv(vm.chosen["droidboot"]!!.openInputStream(vm), File(vm.deviceInfo.blBlock))
			} catch (e: IOException) {
				terminal.add(vm.activity.getString(R.string.term_bl_failed))
				terminal.add(e.message ?: "(null)")
				terminal.add(vm.activity.getString(R.string.term_consult_doc))
				return@Terminal
			}
		}
		if (vm.deviceInfo.postInstallScript) {
			terminal.add(vm.activity.getString(R.string.term_device_setup))
			vm.logic.runShFileWithArgs(
				"BOOTED=${vm.deviceInfo.isBooted(vm.logic)} SETUP=true " +
						"${tmpFile!!.absolutePath} real"
			).to(terminal).exec()
			tmpFile.delete()
		}
		terminal.add(vm.activity.getString(R.string.term_success))
		vm.logic.unmountBootset()
		withContext(Dispatchers.Main) {
			vm.nextText = vm.activity.getString(R.string.finish)
			vm.onNext = {
				if (vm.deviceInfo.isBooted(vm.logic)) {
					it.finish()
				} else {
					// TODO prompt user to reboot?
					it.finish()
				}
			}
		}
	}
}