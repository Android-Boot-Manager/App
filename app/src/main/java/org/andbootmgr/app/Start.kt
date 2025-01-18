package org.andbootmgr.app

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.andbootmgr.app.util.ConfigFile
import org.andbootmgr.app.util.SDUtils
import java.io.File
import kotlin.collections.set
import kotlin.io.nameWithoutExtension
import kotlin.text.isNullOrBlank
import kotlin.text.matches
import kotlin.text.split

private val configFileNameRegex = Regex("[\\da-zA-Z]+\\.conf")
private val asciiRegex = Regex("\\A\\p{ASCII}+\\z")
private val xtypeValidValues = arrayOf("droid", "SFOS", "UT", "")
private val xpartValidValues = Regex("^$|^real$|^\\d(:\\d+)*$")

@Composable
fun Start(vm: MainActivityState) {
	val installed: Boolean
	val booted: Boolean
	val mounted: Boolean
	val sdPresent: Boolean
	val corrupt: Boolean
	val metaOnSd: Boolean
	if (vm.deviceInfo != null) {
		installed = remember { vm.deviceInfo!!.isInstalled(vm.logic!!) }
		booted = remember { vm.deviceInfo!!.isBooted(vm.logic!!) }
		corrupt = remember { vm.deviceInfo!!.isCorrupt(vm.logic!!) }
		mounted = vm.logic!!.mounted
		metaOnSd = vm.deviceInfo!!.metaonsd
		sdPresent = if (metaOnSd) remember { SuFile.open(vm.deviceInfo!!.asMetaOnSdDeviceInfo().bdev).exists() } else false
	} else {
		installed = false
		booted = false
		corrupt = true
		mounted = false
		sdPresent = false
		metaOnSd = false
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
					if (metaOnSd) {
						Text(stringResource(id = R.string.sd_inserted, stringResource(if (sdPresent) R.string.yes else R.string.no)))
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
		} else if (metaOnSd && !sdPresent) {
			Text(stringResource(R.string.need_sd), textAlign = TextAlign.Center)
		} else if (!installed && !mounted) {
			Button(onClick = {
				vm.currentWizardFlow = DroidBootFlow()
			}) {
				Text(stringResource(if (metaOnSd) R.string.setup_sd else R.string.install))
			}
		} else if (!booted && mounted) {
			Text(stringResource(R.string.installed_not_booted), textAlign = TextAlign.Center)
			Button(onClick = {
				vm.currentWizardFlow = FixDroidBootFlow()
			}) {
				Text(stringResource(R.string.repair_droidboot))
			}
		} else if (!mounted) {
			Text(stringResource(R.string.cannot_mount), textAlign = TextAlign.Center)
		} else if (vm.isOk) {
			if (vm.deviceInfo!!.metaonsd) {
				PartTool(vm)
			} else {
				BootsetTool(vm)
			}
		} else {
			Text(stringResource(R.string.invalid), textAlign = TextAlign.Center)
		}
	}
}

@Composable
private fun PartTool(vm: MainActivityState) {
	var filter by remember { mutableIntStateOf(0) }
	val filterUnifiedView = filter == 0
	val filterPartView = filter == 1
	val filterEntryView = filter == 2
	if (!vm.noobMode)
		MyFilterChipBar(
			filter,
			listOf(
				stringResource(R.string.unified),
				stringResource(R.string.partitions),
				stringResource(R.string.entries)
			)
		) { filter = it }

	var parts by remember { mutableStateOf(SDUtils.generateMeta(vm.deviceInfo!!.asMetaOnSdDeviceInfo())) }
	if (parts == null) {
		Text(stringResource(R.string.part_wizard_err))
		return
	}
	@SuppressLint("MutableCollectionMutableState") // lol
	var entries by remember { mutableStateOf<SnapshotStateMap<ConfigFile, File>?>(null) }
	LaunchedEffect(Unit) {
		withContext(Dispatchers.IO) {
			val outList = mutableStateMapOf<ConfigFile, File>()
			outList.putAll(ConfigFile.importFromFolder(vm.logic!!.abmEntries))
			entries = outList
		}
	}
	var editPartID by remember { mutableStateOf<SDUtils.Partition?>(null) }
	var editEntryID by remember { mutableStateOf<ConfigFile?>(null) }
	Column(
		Modifier
			.verticalScroll(rememberScrollState())
			.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally
	) {
		if (vm.noobMode)
			MyInfoCard(stringResource(R.string.click2inspect), padding = 5.dp)
		if (filterUnifiedView && entries != null) {
			var i = 0
			while (i < parts!!.s.size) {
				var found = false
				if (parts!!.s[i].type != SDUtils.PartitionType.FREE) {
					for (e in entries!!.keys) {
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
										if (!e["xpart"]!!.split(":")
												.contains("${parts!!.s[++i].id}")
										) {
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
		} else if (filterPartView) {
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
		} else if (filterEntryView && entries != null) {
			for (e in entries!!.keys) {
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
			Row(horizontalArrangement = Arrangement.SpaceEvenly,
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier
					.fillMaxWidth()
					.clickable { editEntryID = ConfigFile() }) {
				Text(stringResource(R.string.new_entry))
			}
		}
		if (editPartID != null) {
			PartitionEditor(
				vm, editPartID!!, simplified = filterUnifiedView,
				onClose = { editPartID = null }, onPtChanged = {
					// TODO don't call generateMeta on main thread
					parts = SDUtils.generateMeta(vm.deviceInfo!!.asMetaOnSdDeviceInfo())
					editPartID = if (it) null else
						parts?.s!!.findLast { it.id == editPartID!!.id }
				}
			)
		}
		if (editEntryID != null && !filterUnifiedView) {
			EntryEditor(
				vm, editEntryID!!, entries!![editEntryID!!],
				onClose = { editEntryID = null },
				onDeleted = {
					entries!!.remove(editEntryID!!.also { editEntryID = null })
				},
				onEdited = {
					entries!![editEntryID!!.also { editEntryID = null }] = it
				}
			)
		} else if (editEntryID != null) {
			OsEditor(vm, parts, editEntryID!!, entries!![editEntryID!!]!!, onClose = {
				if (it) {
					entries!!.remove(editEntryID!!.also { editEntryID = null })
					// TODO don't call generateMeta on main thread
					parts = SDUtils.generateMeta(vm.deviceInfo!!.asMetaOnSdDeviceInfo())
				} else
					editEntryID = null
			}, onOpenUpdater = {
				vm.currentWizardFlow = UpdateFlow(entries!![editEntryID!!]!!.name)
				editEntryID = null
			})
		}
	}
}

@Composable
private fun OsEditor(vm: MainActivityState, parts: SDUtils.SDPartitionMeta?, e: ConfigFile, f: File,
                     onClose: (newPt: Boolean) -> Unit, onOpenUpdater: () -> Unit) {
	var processing by remember { mutableStateOf(false) }
	var delete by remember { mutableStateOf(false) }
	var result by remember { mutableStateOf<Pair<String, (() -> Unit)?>?>(null) }
	AlertDialog(
		onDismissRequest = {
			onClose(false)
		},
		title = {
			Text(text = if (e.has("title")) "\"${e["title"]}\"" else stringResource(R.string.invalid_entry2))
		},
		icon = {
			Icon(painterResource(id = R.drawable.ic_roms), stringResource(id = R.string.icon_content_desc))
		},
		text = {
			Column(Modifier.verticalScroll(rememberScrollState())) {
				Button(onClick = onOpenUpdater,
					enabled = e.has("xupdate") && !e["xupdate"].isNullOrBlank()) {
					Text(stringResource(R.string.update))
				}
				// TODO add button to open backup & restore tool (by asking which partition should
				//  be backed up / restored)
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
					onClose(false)
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
					CoroutineScope(Dispatchers.Default).launch {
						var tresult = ""
						if (e.has("xpart") && !e["xpart"].isNullOrBlank() && vm.deviceInfo!!.metaonsd) {
							var parts = parts
							val allp = e["xpart"]!!.split(":")
								.map { parts!!.dumpKernelPartition(Integer.valueOf(it)) }
							vm.unmountBootset()
							for (p in allp) { // Do not chain, but regenerate meta and unmount every time. Thanks vold
								p.meta = if (parts != null) parts.also { parts = null }
								else SDUtils.generateMeta(vm.deviceInfo!!.asMetaOnSdDeviceInfo())!!
								val r = vm.logic!!.delete(p).exec()
								tresult += r.out.joinToString("\n") + r.err.joinToString("\n") + "\n"
							}
							vm.mountBootset()
						} else if (!vm.deviceInfo!!.metaonsd) {
							val f3 = SuFile(vm.logic!!.abmSdLessBootset, f.nameWithoutExtension)
							if (!f3.deleteRecursive())
								tresult += vm.activity!!.getString(R.string.cannot_delete, f3.absolutePath)
						}
						val f2 = SuFile(vm.logic!!.abmBootset, f.nameWithoutExtension)
						if (!f2.deleteRecursive())
							tresult += vm.activity!!.getString(R.string.cannot_delete, f2.absolutePath)
						if (!f.delete())
							tresult += vm.activity!!.getString(R.string.cannot_delete, f.absolutePath)
						processing = false
						result = tresult to {
							onClose(true)
						}
					}
				}) {
					Text(stringResource(R.string.delete))
				}
			}
		)
	}

	if (processing) {
		AlertDialog(
			onDismissRequest = {},
			title = {
				Text(stringResource(R.string.please_wait))
			},
			text = {
				LoadingCircle(stringResource(R.string.loading), paddingBetween = 20.dp)
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
				Text(result!!.first)
			},
			confirmButton = {
				Button(onClick = {
					val next = result!!.second
					result = null
					next?.invoke()
				}) {
					Text(stringResource(id = R.string.ok))
				}
			}
		)
	}
}

@Composable
private fun PartitionEditor(vm: MainActivityState, p: SDUtils.Partition, simplified: Boolean,
                            onClose: () -> Unit, onPtChanged: (close: Boolean) -> Unit) {
	var rename by remember { mutableStateOf(false) }
	var processing by remember { mutableStateOf(false) }
	var delete by remember { mutableStateOf(false) }
	var result by remember { mutableStateOf<Pair<String, (() -> Unit)?>?>(null) }
	AlertDialog(
		onDismissRequest = {
			onClose()
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
				if (p.type != SDUtils.PartitionType.FREE && !simplified)
					Text(stringResource(id = R.string.detail_id, p.id, p.major, p.minor))
				Text(stringResource(id = R.string.detail_type, fancyType, if (p.type != SDUtils.PartitionType.FREE && !simplified) stringResource(id = R.string.detail_type_code, p.code) else ""))
				Text(stringResource(id = R.string.detail_size, p.sizeFancy, if (!simplified) stringResource(id = R.string.detail_size_sectors, p.size) else ""))
				if (!simplified)
					Text(
						stringResource(
							id = R.string.detail_position,
							p.startSector,
							p.endSector
						)
					)
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
					if (!simplified) {
						Row {
							Button(onClick = {
								processing = true
								vm.logic!!.mount(p).submit {
									processing = false
									result = (it.out.joinToString("\n") + it.err.joinToString("\n")) to null
								}
							}, Modifier.padding(end = 5.dp)) {
								Text(stringResource(R.string.mount))
							}
							Button(onClick = {
								processing = true
								vm.logic!!.unmount(p).submit {
									processing = false
									result = (it.out.joinToString("\n") + it.err.joinToString("\n")) to null
								}
							}) {
								Text(stringResource(R.string.umount))
							}
						}
					}
					Button(onClick = {
						vm.currentWizardFlow = BackupRestoreFlow(p.id, null)
					}) {
						Text(stringResource(R.string.backupnrestore))
					}
				} else {
					Button(onClick = {
						vm.currentWizardFlow = CreatePartFlow(p.startSector)
					}) {
						Text(stringResource(R.string.create))
					}
				}
			}
		},
		confirmButton = {
			Button(
				onClick = {
					onClose()
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
							result = (r.out.joinToString("\n") + r.err.joinToString("\n")) to {
								onPtChanged(false) // ! will restart composable !
							}
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
					vm.unmountBootset()
					vm.logic!!.delete(p).submit {
						vm.mountBootset()
						if (wasMounted != vm.logic!!.mounted) vm.activity!!.finish()
						else {
							processing = false
							result = (it.out.joinToString("\n") + it.err.joinToString("\n")) to {
								onPtChanged(true) // close
							}
						}
					}
				}) {
					Text(stringResource(R.string.delete))
				}
			}
		)
	}

	if (processing) {
		AlertDialog(
			onDismissRequest = {},
			title = {
				Text(stringResource(R.string.please_wait))
			},
			text = {
				LoadingCircle(stringResource(R.string.loading), paddingBetween = 20.dp)
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
				Text(result!!.first)
			},
			confirmButton = {
				Button(onClick = {
					val next = result!!.second
					result = null
					next?.invoke()
				}) {
					Text(stringResource(id = R.string.ok))
				}
			}
		)
	}
}

@Composable
private fun EntryEditor(vm: MainActivityState, e: ConfigFile, f: File?, onClose: () -> Unit,
                        onDeleted: () -> Unit, onEdited: (File) -> Unit) {
	val context = LocalContext.current
	var newFileName by remember { mutableStateOf(f?.name ?: "NewEntry.conf") }
	val newFileNameErr by remember(newFileName) { derivedStateOf { !newFileName.matches(configFileNameRegex) } }
	var titleT by remember { mutableStateOf(e["title"] ?: "") }
	val titleE by remember { derivedStateOf { !titleT.matches(asciiRegex) } }
	var linuxT by remember { mutableStateOf(e["linux"] ?: "") }
	val linuxE by remember { derivedStateOf { !linuxT.matches(asciiRegex) } }
	var initrdT by remember { mutableStateOf(e["initrd"] ?: "") }
	val initrdE by remember { derivedStateOf { !initrdT.matches(asciiRegex) } }
	var dtbT by remember { mutableStateOf(e["dtb"] ?: "") }
	val dtbE by remember { derivedStateOf { !dtbT.matches(asciiRegex) } }
	var optionsT by remember { mutableStateOf(e["options"] ?: "") }
	val optionsE by remember { derivedStateOf { !optionsT.matches(asciiRegex) } }
	var xtypeT by remember { mutableStateOf(e["xtype"] ?: "") }
	val xtypeE by remember { derivedStateOf { !xtypeValidValues.contains(xtypeT) } }
	var xpartT by remember { mutableStateOf(e["xpart"] ?: "") }
	val xpartE by remember { derivedStateOf { !xpartT.matches(xpartValidValues) } }
	var xupdateT by remember { mutableStateOf(e["xupdate"] ?: "") }
	// TODO dtbo editing if havedtbo
	val isOk = !(newFileNameErr || titleE || linuxE || initrdE || dtbE || optionsE || xtypeE || xpartE)
	AlertDialog(
		onDismissRequest = {
			onClose()
		},
		title = {
			Text(text = if (e.has("title")) e["title"]!! else if (f != null) stringResource(id = R.string.invalid_entry2) else stringResource(id = R.string.new_entry2))
		},
		icon = {
			Icon(painterResource(id = R.drawable.ic_roms), stringResource(R.string.icon_content_desc))
		},
		text = {
			Column(Modifier.verticalScroll(rememberScrollState())) {
				TextField(value = newFileName, onValueChange = {
					if (f != null) return@TextField
					newFileName = it
				}, isError = newFileNameErr, enabled = f == null, label = {
					Text(stringResource(R.string.file_name))
				})

				TextField(value = titleT, onValueChange = {
					titleT = it
				}, isError = titleE, label = {
					Text(stringResource(R.string.title))
				})

				TextField(value = linuxT, onValueChange = {
					linuxT = it
				}, isError = linuxE, label = {
					Text(stringResource(R.string.linux))
				})

				TextField(value = initrdT, onValueChange = {
					initrdT = it
				}, isError = initrdE, label = {
					Text(stringResource(R.string.initrd))
				})

				TextField(value = dtbT, onValueChange = {
					dtbT = it
				}, isError = dtbE, label = {
					Text(stringResource(R.string.dtb))
				})

				TextField(value = optionsT, onValueChange = {
					optionsT = it
				}, isError = optionsE, label = {
					Text(stringResource(R.string.options))
				})

				TextField(value = xtypeT, onValueChange = {
					xtypeT = it
				}, isError = xtypeE, label = {
					Text(stringResource(R.string.rom_type))
				})

				TextField(value = xpartT, onValueChange = {
					xpartT = it
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
						f.delete()
						onDeleted() // close
					}) {
					Text(stringResource(R.string.delete))
				}
			}
			Button(
				onClick = {
					if (!isOk) return@Button
					val newFile = f ?: SuFile.open(vm.logic!!.abmEntries, newFileName).also {
						if (it.exists()) {
							Toast.makeText(
								context,
								vm.activity!!.getString(R.string.file_already_exists),
								Toast.LENGTH_LONG
							).show()
							return@Button
						}
					}
					e["title"] = titleT
					e["linux"] = linuxT
					e["initrd"] = initrdT
					e["dtb"] = dtbT
					e["options"] = optionsT
					e["xtype"] = xtypeT
					e["xpart"] = xpartT
					e["xupdate"] = xupdateT
					e.exportToFile(f!!)
					onEdited(newFile) // close
				}, enabled = isOk
			) {
				Text(stringResource(if (f != null) R.string.update else R.string.create))
			}
			Button(
				onClick = {
					onClose()
				}) {
				Text(stringResource(R.string.cancel))
			}
		}
	)
}

@Composable
private fun BootsetTool(vm: MainActivityState) {
	var filterEntryView by remember { mutableStateOf(false) }
	if (!vm.noobMode)
		MyFilterChipBar(
			if (filterEntryView) 1 else 0,
			listOf(
				stringResource(R.string.unified),
				stringResource(R.string.entries)
			)
		) { filterEntryView = it == 1 }

	@SuppressLint("MutableCollectionMutableState") // lol
	var entries by remember { mutableStateOf<SnapshotStateMap<ConfigFile, File>?>(null) }
	LaunchedEffect(Unit) {
		withContext(Dispatchers.IO) {
			val outList = mutableStateMapOf<ConfigFile, File>()
			outList.putAll(ConfigFile.importFromFolder(vm.logic!!.abmEntries))
			entries = outList
		}
	}
	var editEntryID by remember { mutableStateOf<ConfigFile?>(null) }
	Column(
		Modifier
			.verticalScroll(rememberScrollState())
			.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally
	) {
		if (vm.noobMode)
			MyInfoCard(stringResource(R.string.click2inspect), padding = 5.dp)
		if (entries != null) {
			for (e in entries!!.keys) {
				val spaceUsage = null // TODO compute space usage of installed OS
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
						(if (e.has("title")) {
							stringResource(R.string.entry_title, e["title"]!!)
						} else {
							stringResource(R.string.invalid_entry)
						}).let {
							if (spaceUsage != null)
								stringResource(R.string.entry_space_usage, spaceUsage, it)
							else it
						}
					)
				}
			}
			if (filterEntryView) {
				Row(horizontalArrangement = Arrangement.SpaceEvenly,
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier
						.fillMaxWidth()
						.clickable { editEntryID = ConfigFile() }) {
					Text(stringResource(R.string.new_entry))
				}
			} else {
				Row(horizontalArrangement = Arrangement.SpaceEvenly,
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier
						.fillMaxWidth()
						.clickable { vm.currentWizardFlow = CreatePartFlow(null) }) {
					Text(stringResource(R.string.install_os))
				}
			}
		}
		// TODO we eventually want portable partitions for !metaonsd, but not supported yet
		if (editEntryID != null && filterEntryView) {
			EntryEditor(
				vm, editEntryID!!, entries!![editEntryID!!],
				onClose = { editEntryID = null },
				onDeleted = {
					entries!!.remove(editEntryID!!.also { editEntryID = null })
				},
				onEdited = {
					entries!![editEntryID!!.also { editEntryID = null }] = it
				}
			)
		} else if (editEntryID != null) {
			OsEditor(vm, null, editEntryID!!, entries!![editEntryID!!]!!, onClose = {
				if (it) {
					entries!!.remove(editEntryID!!.also { editEntryID = null })
				} else
					editEntryID = null
			}, onOpenUpdater = {
				vm.currentWizardFlow = UpdateFlow(entries!![editEntryID!!]!!.name)
				editEntryID = null
			})
		}
	}
}
