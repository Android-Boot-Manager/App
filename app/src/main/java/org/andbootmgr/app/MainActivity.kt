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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.andbootmgr.app.util.AbmTheme
import org.andbootmgr.app.util.ConfigFile
import org.andbootmgr.app.util.SDUtils
import org.andbootmgr.app.util.Toolkit
import java.io.File
import java.util.stream.Collectors

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

	fun startUpdateFlow(e: String) {
		val i = Intent(activity!!, WizardActivity::class.java)
		i.putExtra("codename", deviceInfo!!.codename)
		i.putExtra("flow", "update")
		i.putExtra("entryFilename", e)
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

	var noobMode by mutableStateOf(false)
	var activity: MainActivity? = null
	var deviceInfo: DeviceInfo? = null
	var currentNav by mutableStateOf("start")
	var isReady = false
	var name by mutableStateOf("") /* default value moved to onCreate() */
	var navController: NavHostController? = null
	var drawerState: DrawerState? = null
	var scope: CoroutineScope? = null
	var root = false
	var isOk = false
	var logic: DeviceLogic? = null
}


class MainActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val vm = MainActivityState()
		vm.name = getString(R.string.android)
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
			Toast.makeText(this, getString(R.string.toolkit_extracting), Toast.LENGTH_LONG)
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
						Thread {
							vm.root = shell.isRoot
							vm.deviceInfo = JsonDeviceInfoFactory(vm.activity!!).get(Build.DEVICE)
							// == temp migration code start ==
							if (Shell.cmd("mountpoint -q /data/abm/bootset").exec().isSuccess) {
								Shell.cmd("umount /data/abm/bootset").exec()
							}
							SuFile.open("/data/abm").let {
								if (it.exists())
									Shell.cmd("rm -rf /data/abm").exec()
							}
							// == temp migration code end ==
							if (vm.deviceInfo != null && vm.deviceInfo!!.isInstalled(vm.logic!!)) {
								vm.logic!!.mountBootset(vm.deviceInfo!!)
							} else {
								Log.i("ABM", "not installed, not trying to mount")
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
									vm.noobMode =
										LocalContext.current.getSharedPreferences("abm", 0)
											.getBoolean("noob_mode", BuildConfig.DEFAULT_NOOB_MODE)
									AppContent(vm) {
										NavGraph(vm, it)
									}
								}
								vm.isReady = true
							}
						}.start()
					}
				} else {
					setContent {
						AlertDialog(
							onDismissRequest = {},
							title = {
								Text(text = getString(R.string.error))
							},
							text = {
								Text(getString(R.string.toolkit_error))
							},
							confirmButton = {
								Button(
									onClick = {
										finish()
									}) {
									Text(getString(R.string.quit))
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
	var fabhint by remember { mutableStateOf(false) }
	val fab = @Composable {
		if (vm.noobMode && vm.currentNav == "start") {
			FloatingActionButton(onClick = { fabhint = true }) {
				Icon(Icons.Default.Add, stringResource(R.string.add_icon_content_desc))
			}
		}
	}
	AbmTheme {
		// A surface container using the 'background' color from the theme
		Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
			ModalNavigationDrawer(drawerContent = {
				ModalDrawerSheet {
					NavigationDrawerItem(
						label = { Text(stringResource(R.string.home)) },
						selected = vm.currentNav == "start",
						onClick = {
							scope.launch {
								vm.navController!!.navigate("start")
								drawerState.close()
							}
						},
						modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp)
					)
					if (vm.isOk) {
						NavigationDrawerItem(
							label = { Text(stringResource(R.string.settings)) },
							selected = vm.currentNav == "settings",
							onClick = {
								scope.launch {
									vm.navController!!.navigate("settings")
									drawerState.close()
								}
							},
							modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp)
						)
					}
				}
			},
				drawerState = drawerState,
				content = {
					if (fabhint) {
						AlertDialog(onDismissRequest = { fabhint = false }, text = {
							Text(stringResource(R.string.select_free_space))
						}, confirmButton = {
							Button(onClick = { fabhint = false }) {
								Text(stringResource(R.string.ok))
							}
						})
					}
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
					}, content = view, floatingActionButton = fab)
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
	val metaonsd: Boolean
	if (vm.deviceInfo != null) {
		installed = remember { vm.deviceInfo!!.isInstalled(vm.logic!!) }
		booted = remember { vm.deviceInfo!!.isBooted(vm.logic!!) }
		corrupt = remember { vm.deviceInfo!!.isCorrupt(vm.logic!!) }
		mounted = vm.logic!!.mounted
		sdpresent = SuFile.open(vm.deviceInfo!!.bdev).exists()
		metaonsd = vm.deviceInfo!!.metaonsd
	} else {
		installed = false
		booted = false
		corrupt = true
		mounted = false
		sdpresent = false
		metaonsd = false
	}
	val notOkColor = CardDefaults.cardColors(
		containerColor = Color(0xFFFF0F0F)
	)
	val okColor = CardDefaults.cardColors(
		containerColor = Color(0xFF0DDF0F)
	)
	val notOkIcon = R.drawable.ic_baseline_error_24
	val okIcon = R.drawable.ic_baseline_check_circle_24
	val okText = stringResource(R.string.installed)
	val partiallyOkText = stringResource(R.string.installed_deactivated)
	val corruptDeactivatedText = stringResource(R.string.deactivated_corrupt)
	val corruptText = stringResource(R.string.activated_corrupt)
	val notOkText = stringResource(R.string.not_installed)
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
					Text(stringResource(id = R.string.activated, stringResource(if (booted) R.string.yes else R.string.no)))
					Text(stringResource(id = R.string.mounted_b, stringResource(if (mounted) R.string.yes else R.string.no)))
					if (metaonsd) {
						Text(stringResource(id = R.string.sd_inserted, stringResource(if (sdpresent) R.string.yes else R.string.no)))
						Text(stringResource(id = R.string.sd_formatted, stringResource(if (installed) R.string.yes else R.string.no)))
					} else {
						Text(stringResource(id = R.string.installed_status, stringResource(if (installed) R.string.yes else R.string.no)))
					}
					if (mounted) {
						Text(stringResource(id = R.string.corrupt_b, stringResource(if (corrupt) R.string.yes else R.string.no)))
					}
					Text(stringResource(R.string.device, if (vm.deviceInfo == null) stringResource(id = R.string.unsupported) else vm.deviceInfo!!.codename))
				}
			}
		}
		if (Shell.isAppGrantedRoot() != true) {
			Text(
				stringResource(R.string.need_root),
				textAlign = TextAlign.Center
			)
		} else if (metaonsd && !sdpresent) {
			Text(stringResource(R.string.need_sd), textAlign = TextAlign.Center)
		} else if (!installed && !mounted) {
			Button(onClick = { vm.startFlow("droidboot") }) {
				Text(stringResource(if (metaonsd) R.string.setup_sd else R.string.install))
			}
		} else if (!booted && mounted) {
			Text(stringResource(R.string.installed_not_booted), textAlign = TextAlign.Center)
			Button(onClick = {
				vm.startFlow("fix_droidboot")
			}) {
				Text(stringResource(R.string.repair_droidboot))
			}
		} else if (!mounted) {
			Text(stringResource(R.string.cannot_mount), textAlign = TextAlign.Center)
		} else if (vm.isOk) {
			PartTool(vm)
		} else {
			Text(stringResource(R.string.invalid), textAlign = TextAlign.Center)
		}
	}
}

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
				label = { Text(stringResource(R.string.unified)) },
				Modifier.padding(start = 5.dp),
				leadingIcon = if (filterUnifiedView) {
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
			FilterChip(
				selected = filterPartView,
				onClick = {
					filterPartView = true; filterUnifiedView = false; filterEntryView = false
				},
				label = { Text(stringResource(R.string.partitions)) },
				Modifier.padding(start = 5.dp),
				leadingIcon = if (filterPartView) {
					{
						Icon(
							imageVector = Icons.Filled.Done,
							contentDescription = stringResource(R.string.enabled_content_desc),
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
				label = { Text(stringResource(R.string.entries)) },
				Modifier.padding(start = 5.dp),
				leadingIcon = if (filterEntryView) {
					{
						Icon(
							imageVector = Icons.Filled.Done,
							contentDescription = stringResource(R.string.enabled_content_desc),
							modifier = Modifier.size(FilterChipDefaults.IconSize)
						)
					}
				} else {
					null
				}
			)
		}

	var parts by remember { mutableStateOf(SDUtils.generateMeta(vm.deviceInfo!!)) }
	if (parts == null) {
		Text(stringResource(R.string.part_wizard_err))
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
	var rename by remember { mutableStateOf(false) }
	var delete by remember { mutableStateOf(false) }
	var result: String? by remember { mutableStateOf(null) }
	var editPartID: SDUtils.Partition? by remember { mutableStateOf(null) }
	var editEntryID: ConfigFile? by remember { mutableStateOf(null) }
	Column(
		Modifier
			.verticalScroll(rememberScrollState())
			.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
		if (vm.noobMode) {
			Card(modifier = Modifier
				.fillMaxWidth()
				.padding(5.dp)) {
				Row(
					Modifier
						.fillMaxWidth()
						.padding(20.dp)
				) {
					Icon(painterResource(id = R.drawable.ic_about), stringResource(id = R.string.icon_content_desc))
					Text(stringResource(R.string.click2inspect))
				}
			}
		}
		if (filterUnifiedView) {
			var i = 0
			while (i < parts!!.s.size) {
				var found = false
				if (parts!!.s[i].type != SDUtils.PartitionType.FREE) {
					for (e in entries.keys) {
						if (e.has("xpart") && e["xpart"] != null && e["xpart"]!!.isNotBlank()) {
							for (j in e["xpart"]!!.split(":")) {
								if ("${parts!!.s[i].id}" == j) {
									found = true
									Row(horizontalArrangement = Arrangement.SpaceEvenly,
										verticalAlignment = Alignment.CenterVertically,
										modifier = Modifier
											.fillMaxWidth()
											.clickable { editEntryID = e }) {
										Text(
											if (e.has("title")) {
												stringResource(R.string.entry_title, e["title"]!!)
											} else {
												stringResource(R.string.invalid_entry)
											}
										)
									}
									while (e["xpart"]!!.split(":").contains("${parts!!.s[i].id}")) {
										if (i + 1 == parts!!.s.size) break
										if (!e["xpart"]!!.split(":").contains("${parts!!.s[++i].id}")) {
											i--; break
										}
									}
									break
								}
							}
						}
						if (found) break
					}
				}
				if (!found) {
					val p = parts!!.s[i]
					Row(horizontalArrangement = Arrangement.SpaceEvenly,
						verticalAlignment = Alignment.CenterVertically,
						modifier = Modifier
							.fillMaxWidth()
							.clickable { editPartID = p }) {
						Text(
							if (p.type == SDUtils.PartitionType.FREE)
								stringResource(id = R.string.free_space_item, p.sizeFancy)
							else
								stringResource(id = R.string.part_item, p.id, p.name)
						)
					}
				}
				i++
			}
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
							stringResource(id = R.string.free_space_item, p.sizeFancy)
						else
							stringResource(id = R.string.part_item, p.id, p.name)
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
					entry["xupdate"] = uri(str)
					 */
					Text(
						if (e.has("title")) {
							stringResource(R.string.entry_title, e["title"]!!)
						} else {
							stringResource(R.string.invalid_entry)
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
				Text(stringResource(R.string.new_entry))
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
							stringResource(R.string.free_space)
						else if (p.name.isBlank())
							stringResource(R.string.part_title, p.id)
						else
							p.name.trim()

						Text(text = "\"${name}\"")
					},
					icon = {
						Icon(painterResource(id = R.drawable.ic_sd), stringResource(id = R.string.icon_content_desc))
					},
					text = {
						Column {
							val fancyType = stringResource(when (p.type) {
								SDUtils.PartitionType.RESERVED -> R.string.reserved
								SDUtils.PartitionType.ADOPTED -> R.string.adoptable_meta
								SDUtils.PartitionType.PORTABLE -> R.string.portable_part
								SDUtils.PartitionType.UNKNOWN -> R.string.unknown
								SDUtils.PartitionType.FREE -> R.string.free_space
								SDUtils.PartitionType.SYSTEM -> R.string.os_system
								SDUtils.PartitionType.DATA -> R.string.os_userdata
							})
							if (p.type != SDUtils.PartitionType.FREE && !filterUnifiedView)
								Text(stringResource(id = R.string.detail_id, p.id, p.major, p.minor))
							Text(stringResource(id = R.string.detail_type, fancyType, if (p.type != SDUtils.PartitionType.FREE && !filterUnifiedView) stringResource(id = R.string.detail_type_code, p.code) else ""))
							Text(stringResource(id = R.string.detail_size, p.sizeFancy, if (!filterUnifiedView) stringResource(id = R.string.detail_size_sectors, p.size) else ""))
							if (!filterUnifiedView)
								Text(stringResource(
									id = R.string.detail_position,
									p.startSector,
									p.endSector
								))
							if (p.type != SDUtils.PartitionType.FREE) {
								Row {
									Button(onClick = {
										rename = true
									}, Modifier.padding(end = 5.dp)) {
										Text(stringResource(R.string.rename))
									}
									Button(onClick = {
										delete = true
									}) {
										Text(stringResource(R.string.delete))
									}
								}
								if (!filterUnifiedView) {
									Row {
										Button(onClick = {
											processing = true
											vm.logic!!.mount(p).submit {
												processing = false
												result = it.out.join("\n") + it.err.join("\n")
											}
										}, Modifier.padding(end = 5.dp)) {
											Text(stringResource(R.string.mount))
										}
										Button(onClick = {
											processing = true
											vm.logic!!.unmount(p).submit {
												processing = false
												result = it.out.join("\n") + it.err.join("\n")
											}
										}) {
											Text(stringResource(R.string.umount))
										}
									}
								}
								Button(onClick = { vm.startBackupAndRestoreFlow(p) }) {
									Text(stringResource(R.string.backupnrestore))
								}
							} else {
								Button(onClick = { vm.startCreateFlow(p as SDUtils.Partition.FreeSpace) }) {
									Text(stringResource(R.string.create))
								}
							}
						}
					},
					confirmButton = {
						Button(
							onClick = {
								editPartID = null
							}) {
							Text(stringResource(R.string.cancel))
						}
					}
				)
			if (rename) {
				var e by remember { mutableStateOf(false) }
				var t by remember { mutableStateOf(p.name) }
				AlertDialog(
					onDismissRequest = {
						rename = false
					},
					title = {
						Text(stringResource(R.string.rename))
					},
					text = {
						TextField(value = t, onValueChange = {
							t = it
							e = !t.matches(Regex("\\A\\p{ASCII}*\\z"))
						}, isError = e, label = {
							Text(stringResource(R.string.part_name))
						})
					},
					dismissButton = {
						Button(onClick = { rename = false }) {
							Text(stringResource(R.string.cancel))
						}
					},
					confirmButton = {
						Button(onClick = {
							if (!e) {
								processing = true
								rename = false
								vm.logic!!.rename(p, t).submit { r ->
									result = r.out.join("\n") + r.err.join("\n")
									parts = SDUtils.generateMeta(vm.deviceInfo!!)
									editPartID = parts?.s!!.findLast { it.id == p.id }
									processing = false
								}
							}
						}, enabled = !e) {
							Text(stringResource(R.string.rename))
						}
					}
				)
			} else if (delete) {
				AlertDialog(
					onDismissRequest = {
						delete = false
					},
					title = {
						Text(stringResource(R.string.delete))
					},
					text = {
						Text(stringResource(R.string.really_delete_part))
					},
					dismissButton = {
						Button(onClick = { delete = false }) {
							Text(stringResource(R.string.cancel))
						}
					},
					confirmButton = {
						Button(onClick = {
							processing = true
							delete = false
							val wasMounted = vm.logic!!.mounted
							vm.logic!!.unmountBootset()
							vm.logic!!.delete(p).submit {
								vm.logic!!.mountBootset(vm.deviceInfo!!)
								if (wasMounted != vm.logic!!.mounted) vm.activity!!.finish()
								processing = false
								editPartID = null
								parts = SDUtils.generateMeta(vm.deviceInfo!!)
								result = it.out.join("\n") + it.err.join("\n")
							}
						}) {
							Text(stringResource(R.string.delete))
						}
					}
				)
			}
		}
		if (editEntryID != null && !filterUnifiedView) {
			val ctx = LocalContext.current
			val fn = Regex("[\\da-zA-Z]+\\.conf")
			val ascii = Regex("\\A\\p{ASCII}+\\z")
			val xtype = arrayOf("droid", "SFOS", "UT", "")
			val xpart = Regex("^$|^real$|^\\d(:\\d+)*$")
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
			var xupdateT by remember { mutableStateOf(e["xupdate"] ?: "") }
			val isOk = !(newFileNameErr || titleE || linuxE || initrdE || dtbE || optionsE || xtypeE || xpartE)
			AlertDialog(
				onDismissRequest = {
					editEntryID = null
				},
				title = {
					Text(text = if (e.has("title")) "\"${e["title"]}\"" else if (f != null) stringResource(id = R.string.invalid_entry2) else stringResource(id = R.string.new_entry2))
				},
				icon = {
					Icon(painterResource(id = R.drawable.ic_roms), stringResource(R.string.icon_content_desc))
				},
				text = {
					Column(Modifier.verticalScroll(rememberScrollState())) {
						TextField(value = newFileName, onValueChange = {
							if (f != null) return@TextField
							newFileName = it
							newFileNameErr = !(newFileName.matches(fn))
						}, isError = newFileNameErr, enabled = f == null, label = {
							Text(stringResource(R.string.file_name))
						})

						TextField(value = titleT, onValueChange = {
							titleT = it
							titleE = !(titleT.matches(ascii))
						}, isError = titleE, label = {
							Text(stringResource(R.string.title))
						})

						TextField(value = linuxT, onValueChange = {
							linuxT = it
							linuxE = !(linuxT.matches(ascii))
						}, isError = linuxE, label = {
							Text(stringResource(R.string.linux))
						})

						TextField(value = initrdT, onValueChange = {
							initrdT = it
							initrdE = !(initrdT.matches(ascii))
						}, isError = initrdE, label = {
							Text(stringResource(R.string.initrd))
						})

						TextField(value = dtbT, onValueChange = {
							dtbT = it
							dtbE = !(dtbT.matches(ascii))
						}, isError = dtbE, label = {
							Text(stringResource(R.string.dtb))
						})

						TextField(value = optionsT, onValueChange = {
							optionsT = it
							optionsE = !(optionsT.matches(ascii))
						}, isError = optionsE, label = {
							Text(stringResource(R.string.options))
						})

						TextField(value = xtypeT, onValueChange = {
							xtypeT = it
							xtypeE = !(xtype.contains(xtypeT))
						}, isError = xtypeE, label = {
							Text(stringResource(R.string.rom_type))
						})

						TextField(value = xpartT, onValueChange = {
							xpartT = it
							xpartE = !(xpartT.matches(xpart))
						}, isError = xpartE, label = {
							Text(stringResource(R.string.assigned_parts))
						})

						TextField(value = xupdateT, onValueChange = {
							xupdateT = it
						}, label = {
							Text(stringResource(R.string.updater_url))
						})
					}
				},
				confirmButton = {
					if (f != null && e["xpart"] != "real") {
						Button(
							onClick = {
								f!!.delete()
								entries.remove(e)
								editEntryID = null
							}) {
							Text(stringResource(R.string.delete))
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
										vm.activity!!.getString(R.string.file_already_exists),
										Toast.LENGTH_LONG
									).show()
									f = null
									return@Button
								}
							}
							entries[e] = f!!
							e["title"] = titleT
							e["linux"] = linuxT
							e["initrd"] = initrdT
							e["dtb"] = dtbT
							e["options"] = optionsT
							e["xtype"] = xtypeT
							e["xpart"] = xpartT
							e["xupdate"] = xupdateT
							e.exportToFile(f!!)
							editEntryID = null
						}, enabled = isOk
					) {
						Text(stringResource(if (f != null) R.string.update else R.string.create))
					}
					Button(
						onClick = {
							editEntryID = null
						}) {
						Text(stringResource(R.string.cancel))
					}
				}
			)
		} else if (editEntryID != null) {
			val e = editEntryID!!
			AlertDialog(
				onDismissRequest = {
					editEntryID = null
				},
				title = {
					Text(text = if (e.has("title")) "\"${e["title"]}\"" else stringResource(R.string.invalid_entry2))
				},
				icon = {
					Icon(painterResource(id = R.drawable.ic_roms), stringResource(id = R.string.icon_content_desc))
				},
				text = {
					Column(Modifier.verticalScroll(rememberScrollState())) {
						Button(
							onClick = {
								if (e.has("xupdate") && !e["xupdate"].isNullOrBlank())
									vm.startUpdateFlow(entries[e]!!.absolutePath)
							}, enabled = e.has("xupdate") && !e["xupdate"].isNullOrBlank()) {
							Text(stringResource(R.string.update))
						}
						Button(
							onClick = {
								delete = true
							}) {
							Text(stringResource(R.string.delete))
						}
					}
				},
				confirmButton = {
					Button(
						onClick = {
							editEntryID = null
						}) {
						Text(stringResource(R.string.cancel))
					}
				}
			)

			if (delete) {
				AlertDialog(
					onDismissRequest = {
						delete = false
					},
					title = {
						Text(stringResource(R.string.delete))
					},
					text = {
						Text(stringResource(R.string.really_delete_os))
					},
					dismissButton = {
						Button(onClick = { delete = false }) {
							Text(stringResource(id = R.string.cancel))
						}
					},
					confirmButton = {
						Button(onClick = {
							processing = true
							delete = false
							Thread {
								var tresult = ""
								if (e.has("xpart") && !e["xpart"].isNullOrBlank()) {
									val allp = e["xpart"]!!.split(":")
										.map { parts!!.dumpKernelPartition(Integer.valueOf(it)) }
									vm.logic!!.unmountBootset()
									for (p in allp) { // Do not chain, but regenerate meta and unmount every time. Thanks void
										val r = vm.logic!!.delete(p).exec()
										parts = SDUtils.generateMeta(vm.deviceInfo!!)
										tresult += r.out.join("\n") + r.err.join("\n") + "\n"
									}
									vm.logic!!.mountBootset(vm.deviceInfo!!)
								}
								val f = entries[e]!!
								val f2 = SuFile(vm.logic!!.abmBootset, f.nameWithoutExtension)
								if (!f2.deleteRecursive())
									tresult += vm.activity!!.getString(R.string.cannot_delete, f2.absolutePath)
								entries.remove(e)
								if (!f.delete())
									tresult += vm.activity!!.getString(R.string.cannot_delete, f.absolutePath)
								editEntryID = null
								processing = false
								parts = SDUtils.generateMeta(vm.deviceInfo!!)
								result = tresult
							}.start()
						}) {
							Text(stringResource(R.string.delete))
						}
					}
				)
			}
		}
		if (processing) {
			AlertDialog(
				onDismissRequest = {},
				title = {
					Text(stringResource(R.string.please_wait))
				},
				text = {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceAround
					) {
						CircularProgressIndicator(Modifier.padding(end = 20.dp))
						Text(stringResource(R.string.loading))
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
					Text(stringResource(R.string.done))
				},
				text = {
					result?.let {
						Text(it)
					}
				},
				confirmButton = {
					Button(onClick = { result = null }) {
						Text(stringResource(id = R.string.ok))
					}
				}
			)
		}
	}
}

@Composable
private fun Settings(vm: MainActivityState) {
	val ctx = LocalContext.current
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
	var defaultText by remember { mutableStateOf(c["default"]!!) }
	val defaultErr = defaultText.isBlank() || !defaultText.matches(Regex("[\\dA-Za-z ]+"))
	var timeoutText by remember { mutableStateOf(c["timeout"]!!) }
	val timeoutErr = timeoutText.isBlank() || !timeoutText.matches(Regex("\\d+"))
	Column {
		TextField(
			value = defaultText,
			onValueChange = {
				defaultText = it
				c["default"] = it.trim()
			},
			label = { Text(stringResource(R.string.default_entry)) },
			isError = defaultErr
		)
		if (defaultErr) {
			Text(stringResource(id = R.string.invalid_in), color = MaterialTheme.colorScheme.error)
		} else {
			Text("") // Budget spacer
		}
		TextField(
			value = timeoutText,
			onValueChange = {
				timeoutText = it
				c["timeout"] = it.trim()
			},
			label = { Text(stringResource(R.string.timeout_secs)) },
			isError = timeoutErr
		)
		if (timeoutErr) {
			Text(stringResource(id = R.string.invalid_in), color = MaterialTheme.colorScheme.error)
		} else {
			Text("") // Budget spacer
		}
		Button(onClick = {
			if (defaultErr || timeoutErr)
				Toast.makeText(vm.activity!!, vm.activity!!.getString(R.string.invalid_in), Toast.LENGTH_LONG).show()
			else {
				try {
					c.exportToFile(File(vm.logic!!.abmDb, "db.conf"))
				} catch (e: ActionAbortedError) {
					Toast.makeText(vm.activity!!, vm.activity!!.getString(R.string.failed2save), Toast.LENGTH_LONG)
						.show()
				}
			}
		}, enabled = !(defaultErr || timeoutErr)) {
			Text(stringResource(R.string.save_changes))
		}
		Button(onClick = {
			vm.startFlow("update_droidboot")
		}) {
			Text(stringResource(R.string.update_droidboot))
		}
		Button(onClick = {
			vm.logic!!.unmountBootset()
			vm.activity!!.finish()
		}) {
			Text(stringResource(R.string.umount))
		}
		Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
			Text(stringResource(R.string.noob_mode))
			Switch(checked = vm.noobMode, onCheckedChange = {
				vm.noobMode = it
				ctx.getSharedPreferences("abm", 0).edit().putBoolean("noob_mode", it).apply()
			})
		}
	}
}

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