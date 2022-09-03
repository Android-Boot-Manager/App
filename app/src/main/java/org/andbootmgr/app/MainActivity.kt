package org.andbootmgr.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.Shell.FLAG_MOUNT_MASTER
import com.topjohnwu.superuser.Shell.FLAG_REDIRECT_STDERR
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.andbootmgr.app.ui.theme.AbmTheme
import org.andbootmgr.app.util.ConfigFile
import org.andbootmgr.app.util.Toolkit
import java.io.File
import java.io.IOException

class MainActivityState {
	fun startFlow(flow: String) {
		val i = Intent(activity!!, WizardActivity::class.java)
		i.putExtra("codename", deviceInfo!!.codename)
		i.putExtra("flow", flow)
		activity!!.startActivity(i)
		activity!!.finish()
	}

	fun startCreateFlow(freeSpace: SDUtils.Partition.FreeSpace) {
		val i = Intent(activity!!, WizardActivity::class.java)
		i.putExtra("codename", deviceInfo!!.codename)
		i.putExtra("flow", "create_part")
		i.putExtra("part_sid", freeSpace.startSector)
		activity!!.startActivity(i)
		activity!!.finish()
	}

	fun startBackupAndRestoreFlow(partition: SDUtils.Partition) {
		val i = Intent(activity!!, WizardActivity::class.java)
		i.putExtra("codename", deviceInfo!!.codename)
		i.putExtra("flow", "backup_restore")
		i.putExtra("partitionid", partition.id)
		activity!!.startActivity(i)
		activity!!.finish()
	}

	var noobMode: Boolean = false
	var activity: MainActivity? = null
	var deviceInfo: DeviceInfo? = null
	var currentNav: String = "start"
	var isReady = false
	var name by mutableStateOf("Android")
	var navController: NavHostController? = null
	@OptIn(ExperimentalMaterial3Api::class)
	var drawerState: DrawerState? = null
	var scope: CoroutineScope? = null
	var root = false
	var isOk = false
	var logic: DeviceLogic? = null
}


class MainActivity : ComponentActivity() {

	@OptIn(ExperimentalMaterial3Api::class)
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val vm = MainActivityState()
		vm.activity = this
		vm.logic = DeviceLogic(this)

		val content: View = findViewById(android.R.id.content)
		content.viewTreeObserver.addOnPreDrawListener(
			object : ViewTreeObserver.OnPreDrawListener {
				override fun onPreDraw(): Boolean {
					// Check if the initial data is ready.
					return if (vm.isReady) {
						// The content is ready; start drawing.
						content.viewTreeObserver.removeOnPreDrawListener(this)
						true
					} else {
						// The content is not ready; suspend.
						false
					}
				}
			}
		)

		val toast =
			Toast.makeText(this, "Toolkit extracting, please be patient...", Toast.LENGTH_LONG)
		Thread {
			if (Shell.getCachedShell() == null) {
				Shell.enableVerboseLogging = BuildConfig.DEBUG
				Shell.setDefaultBuilder(
					Shell.Builder.create()
						.setFlags(FLAG_MOUNT_MASTER or FLAG_REDIRECT_STDERR)
						.setTimeout(30)
				)
			}
			Toolkit(this).copyAssets({
				runOnUiThread {
					toast.show()
				}
			}) { fail ->
				runOnUiThread {
					toast.cancel()
				}
				if (!fail) {
					Shell.getShell { shell ->
						vm.root = shell.isRoot
						val codename: String
						var b: ByteArray? = null
						if (vm.root) {
							try {
								val f = SuFile.open(vm.logic!!.abmDir, "codename.cfg")
								val s = SuFileInputStream.open(f)
								b = s.readBytes()
								s.close()
							} catch (e: IOException) {
								Log.e("ABM_GetCodeName", Log.getStackTraceString(e))
							}
						}
						codename = if (b != null) {
							String(b).trim()
						} else {
							Build.DEVICE
						}
						vm.deviceInfo = HardcodedDeviceInfoFactory.get(codename)
						if (vm.deviceInfo != null && vm.deviceInfo!!.isInstalled(vm.logic!!)) {
							vm.logic!!.mount(vm.deviceInfo!!)
						}
						if (vm.deviceInfo != null) {
							vm.isOk = ((vm.deviceInfo!!.isInstalled(vm.logic!!)) &&
							 vm.deviceInfo!!.isBooted(vm.logic!!) &&
							 !(!vm.logic!!.mounted || vm.deviceInfo!!.isCorrupt(vm.logic!!)))
						}
						runOnUiThread {
							setContent {
								val navController = rememberNavController()
								val drawerState = rememberDrawerState(DrawerValue.Closed)
								val scope = rememberCoroutineScope()
								vm.navController = navController
								vm.drawerState = drawerState
								vm.scope = scope
								vm.noobMode = LocalContext.current.getSharedPreferences("abm", 0).getBoolean("noob_mode", BuildConfig.DEFAULT_NOOB_MODE)
								AppContent(vm) {
									NavGraph(vm, it)
								}
							}
							vm.isReady = true
						}
					}
				} else {
					setContent {
						AlertDialog(
							onDismissRequest = {},
							title = {
								Text(text = "Error")
							},
							text = {
								Text("Toolkit unpacking has not successfully completed. Please report this to the developers!")
							},
							confirmButton = {
								Button(
									onClick = {
										finish()
									}) {
									Text("Quit")
								}
							}
						)
					}
					vm.isReady = true
				}
			}
		}.start()
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppContent(vm: MainActivityState, view: @Composable (PaddingValues) -> Unit) {
	val drawerState = vm.drawerState!!
	val scope = vm.scope!!
	AbmTheme {
		// A surface container using the 'background' color from the theme
		Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
			ModalNavigationDrawer(drawerContent = {
				Button(
					modifier = Modifier
						.align(Alignment.CenterHorizontally)
						.padding(top = 16.dp),
					onClick = { scope.launch {
						vm.navController!!.navigate("start")
						drawerState.close()
					} },
					content = { Text("Home") }
				)
				Button(
					modifier = Modifier
						.align(Alignment.CenterHorizontally)
						.padding(top = 16.dp),
					onClick = { if (vm.isOk) scope.launch {
						vm.navController!!.navigate("settings")
						drawerState.close()
					} },
					enabled = vm.isOk,
					content = { Text("Settings") }
				)
			},
				drawerState = drawerState,
				content = {
					Scaffold(topBar = {
						CenterAlignedTopAppBar(title = {
							Text(stringResource(R.string.app_name))
						}, colors = TopAppBarDefaults.centerAlignedTopAppBarColors(), navigationIcon = {
							IconButton(content = {
								Icon(
									imageVector = Icons.Filled.Menu,
									contentDescription = stringResource(R.string.menu)
								)
							}, onClick = {
								scope.launch { drawerState.open() }
							})
						})
					}, content = view)
				}
			)
		}
	}
}

@Composable
private fun NavGraph(vm: MainActivityState, it: PaddingValues) {
	NavHost(navController = vm.navController!!, startDestination = "start", modifier = Modifier.padding(it)) {
		composable("start") {
			vm.currentNav = "start"
			Start(vm)
		}
		composable("settings") {
			vm.currentNav = "settings"
			Settings(vm)
		}
	}
}

@Composable
private fun Start(vm: MainActivityState) {
	val installed: Boolean
	val booted: Boolean
	val mounted: Boolean
	val sdpresent: Boolean
	val corrupt: Boolean
	if (vm.deviceInfo != null) {
		installed = remember { vm.deviceInfo!!.isInstalled(vm.logic!!) }
		booted = remember { vm.deviceInfo!!.isBooted(vm.logic!!) }
		corrupt = remember { vm.deviceInfo!!.isCorrupt(vm.logic!!) }
		mounted = vm.logic!!.mounted
		sdpresent = SuFile.open(vm.deviceInfo!!.bdev).exists()
	} else {
		installed = false
		booted = false
		corrupt = true
		mounted = false
		sdpresent = false
	}
	val notOkColor = CardDefaults.cardColors(
		containerColor = Color(0xFFFF0F0F)
	)
	val okColor = CardDefaults.cardColors(
		containerColor = Color(0xFF0DDF0F)
	)
	val notOkIcon = R.drawable.ic_baseline_error_24
	val okIcon = R.drawable.ic_baseline_check_circle_24
	val okText = "Installed"
	val partiallyOkText = "Installed but not activated"
	val corruptDeactivatedText = "Deactivated and corrupt"
	val corruptText = "Activated but corrupt"
	val notOkText = "Not installed"
	val ok = installed and booted and mounted and !corrupt
	val usedText = if (ok) {
		okText
	} else if (installed and booted and (!mounted || corrupt)) {
		corruptText
	} else if (installed and !booted and (!mounted || corrupt)) {
		corruptDeactivatedText
	} else if (installed and !booted) {
		partiallyOkText
	} else {
		notOkText
	}
	Column {
		Card(
			colors = if (ok) okColor else notOkColor, modifier = Modifier
				.fillMaxWidth()
				.padding(5.dp)
		) {
			Box(Modifier.padding(10.dp)) {
				Column {
					Row(
						Modifier
							.padding(5.dp)
							.fillMaxWidth(),
						horizontalArrangement = Arrangement.Center,
						verticalAlignment = Alignment.CenterVertically
					) {
						Icon(
							painterResource(if (ok) okIcon else notOkIcon),
							"",
							Modifier.size(48.dp)
						)
						Text(usedText, fontSize = 32.sp)
					}
					Text("Activated: $booted")
					Text("Mounted bootset: $mounted")
					if (vm.deviceInfo!!.metaonsd) {
						Text("SD Card inserted: $sdpresent")
						Text("SD Card formatted for dualboot: $installed")
					} else {
						Text("Installed: $installed")
					}
					if (mounted) {
						Text("Bootset corrupt: $corrupt")
					}
					Text("Device: ${if (vm.deviceInfo == null) "(unsupported)" else vm.deviceInfo!!.codename}")
				}
			}
		}
		if (Shell.isAppGrantedRoot() == false) {
			Text(
				"Root access is not granted, but required for this app.",
				textAlign = TextAlign.Center
			)
		} else if (vm.deviceInfo!!.metaonsd && !sdpresent) {
			Text("An SD card is required for ABM to work, but none could be detected.", textAlign = TextAlign.Center)
		} else if (!installed) {
			Button(onClick = { vm.startFlow("droidboot") }) {
				Text(if (vm.deviceInfo!!.metaonsd) "Setup SD card" else "Install")
			}
		} else if (!booted) {
			Text("The device did not boot using DroidBoot, but configuration files are present. If you just installed ABM, you need to reboot. If the bootloader installation repeatedly fails, please consult the documentation.", textAlign = TextAlign.Center)
			Button(onClick = {
				vm.startFlow("fix_droidboot")
			}) {
				Text("Repair DroidBoot")
			}
		} else if (!vm.deviceInfo!!.metaonsd && mounted && corrupt) {
			Text("Configuration files are not present. You can restore them from an automated backup. For more information, please consult the documentation.", textAlign = TextAlign.Center)
			Button(onClick = {
				vm.startFlow("repair_cfg")
			}) {
				Text("Repair bootset")
			}
		} else if (!mounted) {
			Text("Bootset could not be mounted, please consult the documentation.", textAlign = TextAlign.Center)
		} else if (vm.isOk) {
			PartTool(vm)
		} else {
			Text("Unknown error, invalid state", textAlign = TextAlign.Center)
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartTool(vm: MainActivityState) {
	var filterUnifiedView by remember { mutableStateOf(true) }
	var filterPartView by remember { mutableStateOf(false) }
	var filterEntryView by remember { mutableStateOf(false) }
	if (!vm.noobMode)
		Row {
			FilterChip(
				selected = filterUnifiedView,
				onClick = {
					filterUnifiedView = true; filterPartView = false; filterEntryView = false
				},
				label = { Text("Unified") },
				Modifier.padding(start = 5.dp),
				leadingIcon = if (filterUnifiedView) {
					{
						Icon(
							imageVector = Icons.Filled.Done,
							contentDescription = "Enabled",
							modifier = Modifier.size(FilterChipDefaults.IconSize)
						)
					}
				} else {
					null
				}
			)
			FilterChip(
				selected = filterPartView,
				onClick = {
					filterPartView = true; filterUnifiedView = false; filterEntryView = false
				},
				label = { Text("Partitions") },
				Modifier.padding(start = 5.dp),
				leadingIcon = if (filterPartView) {
					{
						Icon(
							imageVector = Icons.Filled.Done,
							contentDescription = "Enabled",
							modifier = Modifier.size(FilterChipDefaults.IconSize)
						)
					}
				} else {
					null
				}
			)
			FilterChip(
				selected = filterEntryView,
				onClick = {
					filterPartView = false; filterUnifiedView = false; filterEntryView = true
				},
				label = { Text("Entries") },
				Modifier.padding(start = 5.dp),
				leadingIcon = if (filterEntryView) {
					{
						Icon(
							imageVector = Icons.Filled.Done,
							contentDescription = "Enabled",
							modifier = Modifier.size(FilterChipDefaults.IconSize)
						)
					}
				} else {
					null
				}
			)
		}

	var parts by remember {
		mutableStateOf(
			SDUtils.generateMeta(
				vm.deviceInfo!!.bdev,
				vm.deviceInfo!!.pbdev
			)
		)
	}
	if (parts == null) {
		Text("Partition wizard failed to load")
		return
	}
	val entries = remember {
		val outList = mutableMapOf<ConfigFile, File>()
		val list = SuFile.open(vm.logic!!.abmEntries.absolutePath).listFiles()
		for (i in list!!) {
			try {
				outList[ConfigFile.importFromFile(i)] = i
			} catch (e: ActionAbortedCleanlyError) {
				Log.e("ABM", Log.getStackTraceString(e))
			}
		}
		return@remember outList
	}
	var processing by remember { mutableStateOf(false) }
	var bnr by remember { mutableStateOf(false) }
	var rename by remember { mutableStateOf(false) }
	var delete by remember { mutableStateOf(false) }
	var result: String? by remember { mutableStateOf(null) }
	var editPartID: SDUtils.Partition? by remember { mutableStateOf(null) }
	var editEntryID: ConfigFile? by remember { mutableStateOf(null) }
	if (filterUnifiedView) {
		Text("TODO") //TODO
	}
	if (filterPartView) {
		for (p in parts!!.s) {
			Row(horizontalArrangement = Arrangement.SpaceEvenly,
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier
					.fillMaxWidth()
					.clickable { editPartID = p }) {
				Text(
					if (p.type == SDUtils.PartitionType.FREE)
						"Free space (${p.sizeFancy})"
					else
						"Partition ${p.id} \"${p.name}\""
				)
			}
		}
	}
	if (filterEntryView) {
		for (e in entries.keys) {
			Row(horizontalArrangement = Arrangement.SpaceEvenly,
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier
					.fillMaxWidth()
					.clickable { editEntryID = e }) {
				/* format:
				entry["title"] = str
				entry["linux"] = path(str)
				entry["initrd"] = path(str)
				entry["dtb"] = path(str)
				entry["options"] = str
				entry["xtype"] = str
				entry["xpart"] = array (str.split(":"))
				 */
				Text(
					if (e.has("title")) {
						"Entry \"${e["title"]}\""
					} else {
						"(Invalid entry)"
					}
				)
			}
		}
		val e = ConfigFile()
		Row(horizontalArrangement = Arrangement.SpaceEvenly,
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier
				.fillMaxWidth()
				.clickable { editEntryID = e }) {
			Text("(Create new entry)")
		}
	}
	if (editPartID != null) {
		val p = editPartID!!
		AlertDialog(
			onDismissRequest = {
				editPartID = null
			},
			title = {
				val name = if (p.type == SDUtils.PartitionType.FREE)
					"Free space"
				else if (p.name.isBlank())
					"Partition ${p.id}"
				else
					p.name.trim()

				Text(text = "\"${name}\"")
			},
			icon = {
				Icon(painterResource(id = R.drawable.ic_sd), "Icon")
			},
			text = {
				Column {
					val fancyType = when (p.type) {
						SDUtils.PartitionType.RESERVED -> "Reserved"
						SDUtils.PartitionType.ADOPTED -> "Adoptable Storage Metadata"
						SDUtils.PartitionType.PORTABLE -> "Portable Storage"
						SDUtils.PartitionType.UNKNOWN -> "Unknown"
						SDUtils.PartitionType.FREE -> "Free space"
						SDUtils.PartitionType.SYSTEM -> "OS system data"
						SDUtils.PartitionType.DATA -> "OS user data"
					}
					if (p.type != SDUtils.PartitionType.FREE)
						Text("Id: ${p.id} (${parts?.major}:${p.minor})")
					Text("Type: ${fancyType}${if (p.type != SDUtils.PartitionType.FREE) " (code ${p.code})" else ""}")
					Text("Size: ${p.sizeFancy} (${p.size} sectors)")
					Text("Position: ${p.startSector} -> ${p.endSector}")
					if (p.type != SDUtils.PartitionType.FREE) {
						Row {
							Button(onClick = {
								rename = true
							}, Modifier.padding(end = 5.dp)) {
								Text("Rename")
							}
							Button(onClick = {
								delete = true
							}) {
								Text("Delete")
							}
						}
						Row {
							Button(onClick = {
								processing = true
								Shell.cmd(p.mount()).submit {
									processing = false
									result = it.out.join("\n") + it.err.join("\n")
								}
							}, Modifier.padding(end = 5.dp)) {
								Text("Mount")
							}
							Button(onClick = {
								processing = true
								Shell.cmd(p.unmount()).submit {
									processing = false
									result = it.out.join("\n") + it.err.join("\n")
								}
							}) {
								Text("Unmount")
							}
						}
						Button(onClick = { bnr = true }) {
							Text("Backup & Restore")
						}
					} else {
						Button(onClick = { vm.startCreateFlow(p as SDUtils.Partition.FreeSpace) }) {
							Text("Create")
						}
					}
				}
			},
			confirmButton = {
				Button(
					onClick = {
						editPartID = null
					}) {
					Text("Cancel")
				}
			}
		)
		if (bnr) {
			vm.startBackupAndRestoreFlow(p)
		} else if (rename) {
			var e by remember { mutableStateOf(false) }
			var t by remember { mutableStateOf(p.name) }
			AlertDialog(
				onDismissRequest = {
					rename = false
				},
				title = {
					Text("Rename")
				},
				text = {
					TextField(value = t, onValueChange = {
						t = it
						e = !t.matches(Regex("\\A\\p{ASCII}*\\z"))
					}, isError = e, label = {
						Text("Partition name")
					})
				},
				dismissButton = {
					Button(onClick = { rename = false }) {
						Text("Cancel")
					}
				},
				confirmButton = {
					Button(onClick = {
						if (!e) {
							processing = true
							rename = false
							Shell.cmd(SDUtils.umsd(parts!!) + " && " + p.rename(t)).submit { r ->
								processing = false
								result = r.out.join("\n") + r.err.join("\n")
								parts = SDUtils.generateMeta(
									vm.deviceInfo!!.bdev,
									vm.deviceInfo!!.pbdev
								)
								editPartID = parts?.s!!.findLast { it.id == p.id }
							}
						}
					}, enabled = !e) {
						Text("Rename")
					}
				}
			)
		} else if (delete) {
			AlertDialog(
				onDismissRequest = {
					delete = false
				},
				title = {
					Text("Delete")
				},
				text = {
					Text("Do you REALLY want to delete this partition and loose ALL data on it?")
				},
				dismissButton = {
					Button(onClick = { delete = false }) {
						Text("Cancel")
					}
				},
				confirmButton = {
					Button(onClick = {
						processing = true
						delete = false
						Shell.cmd(SDUtils.umsd(parts!!) + " && " + p.delete()).submit {
							processing = false
							editPartID = null
							parts =
								SDUtils.generateMeta(vm.deviceInfo!!.bdev, vm.deviceInfo!!.pbdev)
							result = it.out.join("\n") + it.err.join("\n")
						}
					}) {
						Text("Delete")
					}
				}
			)
		}
	}
	if (editEntryID != null) {
		val ctx = LocalContext.current
		val fn = Regex("[0-9a-zA-Z]+\\.conf")
		val ascii = Regex("\\A\\p{ASCII}+\\z")
		val xtype = arrayOf("droid", "SFOS", "UT", "")
		val xpart = Regex("^$|^real$|^[0-9](:[0-9]+)*$")
		val e = editEntryID!!
		var f = entries[e]
		var newFileName by remember { mutableStateOf(f?.name ?: "NewEntry.conf") }
		var newFileNameErr by remember { mutableStateOf(!newFileName.matches(fn)) }
		var titleT by remember { mutableStateOf(e["title"] ?: "") }
		var titleE by remember { mutableStateOf(!titleT.matches(ascii)) }
		var linuxT by remember { mutableStateOf(e["linux"] ?: "") }
		var linuxE by remember { mutableStateOf(!linuxT.matches(ascii)) }
		var initrdT by remember { mutableStateOf(e["initrd"] ?: "") }
		var initrdE by remember { mutableStateOf(!initrdT.matches(ascii)) }
		var dtbT by remember { mutableStateOf(e["dtb"] ?: "") }
		var dtbE by remember { mutableStateOf(!dtbT.matches(ascii)) }
		var optionsT by remember { mutableStateOf(e["options"] ?: "") }
		var optionsE by remember { mutableStateOf(!optionsT.matches(ascii)) }
		var xtypeT by remember { mutableStateOf(e["xtype"] ?: "") }
		var xtypeE by remember { mutableStateOf(!xtype.contains(xtypeT)) }
		var xpartT by remember { mutableStateOf(e["xpart"] ?: "") }
		var xpartE by remember { mutableStateOf(!xpartT.matches(xpart)) }
		val isOk = !(newFileNameErr || titleE)
		AlertDialog(
			onDismissRequest = {
				editEntryID = null
			},
			title = {
				Text(text = if (e.has("title")) "\"${e["title"]}\"" else if (f != null) "Invalid entry" else "New entry")
			},
			icon = {
				Icon(painterResource(id = R.drawable.ic_roms), "Icon")
			},
			text = {
				Column {
					TextField(value = newFileName, onValueChange = {
						if (f != null) return@TextField
						newFileName = it
						newFileNameErr = !(newFileName.matches(fn))
					}, isError = newFileNameErr, enabled = f == null, label = {
						Text("File name")
					})

					TextField(value = titleT, onValueChange = {
						titleT = it
						titleE = !(titleT.matches(ascii))
					}, isError = titleE, label = {
						Text("Title")
					})

					TextField(value = linuxT, onValueChange = {
						linuxT = it
						linuxE = !(linuxT.matches(ascii))
					}, isError = linuxE, label = {
						Text("Linux")
					})

					TextField(value = initrdT, onValueChange = {
						initrdT = it
						initrdE = !(initrdT.matches(ascii))
					}, isError = initrdE, label = {
						Text("Initrd")
					})

					TextField(value = dtbT, onValueChange = {
						dtbT = it
						dtbE = !(dtbT.matches(ascii))
					}, isError = dtbE, label = {
						Text("Dtb")
					})

					TextField(value = optionsT, onValueChange = {
						optionsT = it
						optionsE = !(optionsT.matches(ascii))
					}, isError = optionsE, label = {
						Text("Options")
					})

					TextField(value = xtypeT, onValueChange = {
						xtypeT = it
						xtypeE = !(xtype.contains(xtypeT))
					}, isError = xtypeE, label = {
						Text("ROM type")
					})

					TextField(value = xpartT, onValueChange = {
						xpartT = it
						xpartE = !(xpartT.matches(xpart))
					}, isError = xpartE, label = {
						Text("Assigned partitions")
					})
				}
			},
			confirmButton = {
				if (f != null) {
					Button(
						onClick = {
							f!!.delete()
							entries.remove(e)
							editEntryID = null
						}) {
						Text("Delete")
					}
				}
				Button(
					onClick = {
						if (!isOk) return@Button
						if (f == null) {
							f = SuFile.open(vm.logic!!.abmEntries, newFileName)
							if (f!!.exists()) {
								Toast.makeText(
									ctx,
									"File already exists, choose a different name",
									Toast.LENGTH_LONG
								).show()
								f = null
								return@Button
							}
						}
						entries[e] = f!!
						e.exportToFile(f!!)
						editEntryID = null
					}, enabled = isOk
				) {
					Text(if (f != null) "Update" else "Create")
				}
				Button(
					onClick = {
						editEntryID = null
					}) {
					Text("Cancel")
				}
			}
		)
	}
	if (processing) {
		AlertDialog(
			onDismissRequest = {},
			title = {
				Text("Please wait...")
			},
			text = {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceAround
				) {
					CircularProgressIndicator(Modifier.padding(end = 20.dp))
					Text("Loading...")
				}
			},
			confirmButton = {}
		)
	} else if (result != null) {
		AlertDialog(
			onDismissRequest = {
				result = null
			},
			title = {
				Text("Done")
			},
			text = {
				result?.let {
					Text(it)
				}
			},
			confirmButton = {
				Button(onClick = { result = null }) {
					Text("Ok")
				}
			}
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Settings(vm: MainActivityState) {
	val c = remember {
		try {
			if (vm.logic == null)
				throw ActionAbortedCleanlyError(Exception("Compose preview special-casing"))
			ConfigFile.importFromFile(File(vm.logic!!.abmDb, "db.conf"))
		} catch (e: ActionAbortedCleanlyError) {
			if (vm.activity != null) // Compose preview special-casing
				Toast.makeText(vm.activity, "Malformed db.conf - recreating", Toast.LENGTH_LONG).show()
			ConfigFile().also {
				it["default"] = "Entry 01"
				it["timeout"] = "5"
			}
		}
	}
	var defaultText by remember { mutableStateOf(c["default"]!!) }
	val defaultErr = defaultText.isBlank() || !defaultText.matches(Regex("[0-9A-Za-z ]+"))
	var timeoutText by remember { mutableStateOf(c["timeout"]!!) }
	val timeoutErr = timeoutText.isBlank() || !timeoutText.matches(Regex("[0-9]+"))
	Column {
		TextField(
			value = defaultText,
			onValueChange = {
				defaultText = it
				c["default"] = it.trim()
			},
			label = { Text("Default entry") },
			isError = defaultErr
		)
		if (defaultErr) {
			Text("Invalid input", color = MaterialTheme.colorScheme.error)
		} else {
			Text("") // Budget spacer
		}
		TextField(
			value = timeoutText,
			onValueChange = {
				timeoutText = it
				c["timeout"] = it.trim()
			},
			label = { Text("Timeout (seconds)") },
			isError = timeoutErr
		)
		if (timeoutErr) {
			Text("Invalid input", color = MaterialTheme.colorScheme.error)
		} else {
			Text("") // Budget spacer
		}
		Button(onClick = {
			if (defaultErr || timeoutErr)
				Toast.makeText(vm.activity!!, "Invalid input", Toast.LENGTH_LONG).show()
			else {
				try {
					c.exportToFile(File(vm.logic!!.abmDb, "db.conf"))
				} catch (e: ActionAbortedError) {
					Toast.makeText(vm.activity!!, "Failed to save changes...", Toast.LENGTH_LONG)
						.show()
				}
			}
		}, enabled = !(defaultErr || timeoutErr)) {
			Text("Save changes")
		}
		Button(onClick = {
			vm.startFlow("update_droidboot")
		}) {
			Text("Update DroidBoot")
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun Preview() {
	val vm = MainActivityState()
	val navController = rememberNavController()
	val drawerState = rememberDrawerState(DrawerValue.Closed)
	val scope = rememberCoroutineScope()
	vm.navController = navController
	vm.drawerState = drawerState
	vm.scope = scope
	AppContent(vm) {
		Box(modifier = Modifier.padding(it)) {
			Start(vm)
		}
	}
}