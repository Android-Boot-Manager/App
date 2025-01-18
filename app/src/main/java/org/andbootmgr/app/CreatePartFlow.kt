package org.andbootmgr.app

import android.util.Log
import androidx.collection.ArrayMap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.andbootmgr.app.CreatePartDataHolder.Part
import org.andbootmgr.app.util.ConfigFile
import org.andbootmgr.app.util.SDUtils
import org.andbootmgr.app.util.SOUtils
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.FileNotFoundException
import java.math.BigDecimal
import java.net.URL

class CreatePartFlow(private val desiredStartSector: Long): WizardFlow() {
	override fun get(vm: WizardState): List<IWizardPage> {
		val c = CreatePartDataHolder(vm, desiredStartSector)
		return listOf(WizardPage("start",
			NavButton(vm.activity.getString(R.string.cancel)) { it.finish() },
			NavButton("") {}
		) {
			Start(c)
		}, WizardPage("shop",
			NavButton(vm.activity.getString(R.string.prev)) { it.navigate("start") },
			NavButton("") {}
		) {
			Shop(c)
		}, WizardPage("os",
			NavButton(vm.activity.getString(R.string.prev)) { it.navigate("shop") },
			NavButton(vm.activity.getString(R.string.next)) { it.navigate("dload") }
		) {
			Os(c)
		}, WizardPage("dload",
			NavButton(vm.activity.getString(R.string.cancel)) { it.finish() },
			NavButton("") {}
		) {
			WizardDownloader(c.vm, "flash")
		}, WizardPage("flash",
			NavButton("") {},
			NavButton("") {}
		) {
			Flash(c)
		})
	}
}

private class CreatePartDataHolder(val vm: WizardState, val desiredStartSector: Long) {
	var meta by mutableStateOf<SDUtils.SDPartitionMeta?>(null)
	lateinit var p: SDUtils.Partition.FreeSpace
	var startSectorRelative = 0L
	var endSectorRelative = 0L
	var partitionName: String? = null

	var painter: @Composable (() -> Painter)? = null
	var rtype = ""
	var cmdline = ""
	val dmaMeta = ArrayMap<String, String>()
	class Part(size: Long, isPercent: Boolean, code: String, id: String, sparse: Boolean) {
		var size by mutableLongStateOf(size)
		var isPercent by mutableStateOf(isPercent)
		var code by mutableStateOf(code)
		var id by mutableStateOf(id)
		var sparse by mutableStateOf(sparse)
		fun resolveSectorSize(c: CreatePartDataHolder, remaining: Long): Long {
			return if (!isPercent /*bytes*/) {
				size / c.meta!!.logicalSectorSizeBytes
			} else /*percent*/ {
				(BigDecimal(remaining).multiply(BigDecimal(size).divide(BigDecimal(100)))).toLong()
			}
		}
	}
	val parts = mutableStateListOf<Part>()
	val extraIdNeeded = mutableListOf<String>()
	var romFolderName by mutableStateOf("")
	var romDisplayName by mutableStateOf("")

	fun painterFromRtype(type: String): @Composable () -> Painter {
		val id = when (type) {
			"SFOS" -> R.drawable.ic_sailfish_os_logo
			"UT" -> R.drawable.ut_logo
			"droid" -> R.drawable.ic_roms
			else -> R.drawable.ic_roms
		}
		return (@Composable { painterResource(id = id) })
	}
}

@Composable
private fun Start(c: CreatePartDataHolder) {
	LaunchedEffect(Unit) {
		if (c.meta == null) {
			withContext(Dispatchers.IO) {
				val meta = SDUtils.generateMeta(c.vm.deviceInfo)!! // TODO !metaonsd
				c.p =
					meta.s.find { c.desiredStartSector == it.startSector } as SDUtils.Partition.FreeSpace
				c.meta = meta
			}
		}
	}
	if (c.meta == null) {
		LoadingCircle(stringResource(R.string.loading), modifier = Modifier.fillMaxSize())
		return
	}

	val verticalScrollState = rememberScrollState()
	var partitionName by remember { mutableStateOf("") }
	var startSectorRelative by remember { mutableStateOf("0") }
	var endSectorRelative by remember { mutableStateOf(c.p.size.toString()) }
	val partitionNameInvalid by remember { derivedStateOf { !partitionName.matches(asciiNonEmptyRegex) } }
	val startSectorInvalid by remember { derivedStateOf { !startSectorRelative.matches(numberRegex) } }
	val endSectorInvalid by remember { derivedStateOf { !endSectorRelative.matches(numberRegex) } }
	val sectorsInvalid by remember { derivedStateOf { startSectorInvalid || endSectorInvalid } }
	Column(
		Modifier
			.fillMaxWidth()
			.verticalScroll(verticalScrollState)) {
		Card(modifier = Modifier
			.fillMaxWidth()
			.padding(10.dp)) {
			Column(modifier = Modifier
				.fillMaxWidth()
				.padding(10.dp)) {
				Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
					Icon(painterResource(id = R.drawable.ic_settings), stringResource(R.string.icon_content_desc), Modifier.padding(end = 10.dp))
					Text(stringResource(R.string.general_settings))
				}
				Column(
					Modifier
						.fillMaxWidth()
						.padding(5.dp)) {
					TextField(modifier = Modifier.fillMaxWidth(), value = startSectorRelative, onValueChange = {
						startSectorRelative = it
					}, isError = startSectorInvalid, label = {
						Text(stringResource(R.string.start_sector))
					})
					TextField(modifier = Modifier.fillMaxWidth(), value = endSectorRelative, onValueChange = {
						endSectorRelative = it
					}, isError = endSectorInvalid, label = {
						Text(stringResource(R.string.end_sector))
					})
					val range = (startSectorRelative.toFloatOrNull() ?: 0f)..(endSectorRelative.toFloatOrNull() ?: c.p.size.toFloat())
					RangeSlider(modifier = Modifier.fillMaxWidth(), value = range, onValueChange = {
						startSectorRelative = it.start.toLong().coerceAtLeast(0).toString()
						endSectorRelative = it.endInclusive.toLong().coerceAtMost(c.p.size).toString()
					}, valueRange = 0F..c.p.size.toFloat())

					Text(stringResource(R.string.approx_size, if (!sectorsInvalid) SOUtils.humanReadableByteCountBin((endSectorRelative.toLong() - startSectorRelative.toLong()) * c.meta!!.logicalSectorSizeBytes) else stringResource(R.string.invalid_input)))
					Text(stringResource(R.string.available_space, c.p.sizeFancy, c.p.size))
				}
			}
		}

		if (c.vm.mvm.noobMode)
			MyInfoCard(stringResource(R.string.option_select), padding = 10.dp)

		Card(modifier = Modifier
			.fillMaxWidth()
			.padding(10.dp)) {
			Column(modifier = Modifier
				.fillMaxWidth()
				.padding(10.dp)) {
				Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
					Icon(painterResource(id = R.drawable.ic_sd), stringResource(R.string.icon_content_desc), Modifier.padding(end = 10.dp))
					Text(stringResource(R.string.portable_part))
				}
				TextField(value = partitionName, onValueChange = {
					partitionName = it
				}, isError = partitionNameInvalid && partitionName.isNotEmpty(), label = {
					Text(stringResource(R.string.part_name))
				})
				Row(horizontalArrangement = Arrangement.End, modifier = Modifier
					.fillMaxWidth()
					.padding(5.dp)) {
					Button(enabled = !(sectorsInvalid || partitionNameInvalid), onClick = {
						c.startSectorRelative = startSectorRelative.toLong()
						c.endSectorRelative = endSectorRelative.toLong()
						c.partitionName = partitionName
						c.vm.navigate("flash")
					}) {
						Text(stringResource(id = R.string.create))
					}
				}
			}
		}
		Card(modifier = Modifier
			.fillMaxWidth()
			.padding(10.dp)) {
			Column(modifier = Modifier
				.fillMaxWidth()
				.padding(10.dp)) {
				Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
					Icon(painterResource(id = R.drawable.ic_droidbooticon), stringResource(R.string.icon_content_desc), Modifier.padding(end = 10.dp))
					Text(stringResource(R.string.install_os))
				}
				Row(horizontalArrangement = Arrangement.End, modifier = Modifier
					.fillMaxWidth()
					.padding(5.dp)) {
					Button(enabled = !sectorsInvalid, onClick = {
						c.startSectorRelative = startSectorRelative.toLong()
						c.endSectorRelative = endSectorRelative.toLong()
						c.partitionName = null
						c.vm.navigate("shop")
					}) {
						Text(stringResource(R.string.cont))
					}
				}
			}
		}
	}
}

@Composable
private fun Shop(c: CreatePartDataHolder) {
	var	loading by remember { mutableStateOf(true) }
	var json by remember { mutableStateOf<JSONObject?>(null) }
	val ctx = LocalContext.current
	LaunchedEffect(Unit) {
		c.run {
			withContext(Dispatchers.IO) {
				try {
					val jsonText = try {
						ctx.assets.open("abm.json").readBytes().toString(Charsets.UTF_8)
					} catch (_: FileNotFoundException) {
						URL("https://raw.githubusercontent.com/Android-Boot-Manager/ABM-json/master/devices/" + c.vm.codename + ".json").readText()
					}
					val jjson = JSONTokener(jsonText).nextValue() as JSONObject
					if (BuildConfig.VERSION_CODE < jjson.getInt("minAppVersion"))
						throw IllegalStateException("please upgrade app")
					json = jjson
					//Log.i("ABM shop:", jsonText)
				} catch (e: Exception) {
					Log.e("ABM shop", Log.getStackTraceString(e))
				}
				loading = false
 			}
		}
	}
	if (loading) {
		LoadingCircle(stringResource(R.string.loading), modifier = Modifier.fillMaxSize())
		return
	}
	if (json != null) {
		Column {
			MyInfoCard(stringResource(R.string.select_os))
			var i = 0
			while(i < json!!.getJSONArray("oses").length()) {
				val index = i
				Row(horizontalArrangement = Arrangement.SpaceEvenly,
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier
						.fillMaxWidth()
						.clickable {
							c.run {
								val o = json!!
									.getJSONArray("oses")
									.getJSONObject(index)
								dmaMeta["name"] =
									o.getString("displayname")
								dmaMeta["creator"] = o.getString("creator")
								dmaMeta["updateJson"] = o.getString("updateJson")
								rtype = o.getString("rtype")
								cmdline =
									o.getString("cmdline")
								i = 0
								val idUnneeded = mutableListOf<String>()
								val blockIdNeeded = o.getJSONArray("blockIdNeeded")
								while (i < blockIdNeeded.length()) {
									idUnneeded.add(blockIdNeeded.get(i) as String)
									i++
								}
								i = 0
								// The order in which stuff is added to idNeeded must not be changed
								val extraIdNeeded = o.getJSONArray("extraIdNeeded")
								while (i < extraIdNeeded.length()) {
									val id = extraIdNeeded.get(i) as String
									if (idUnneeded.contains(id))
										throw IllegalStateException("id in both blockIdNeeded and extraIdNeeded")
									vm.idNeeded.add(id)
									this.extraIdNeeded.add(id)
									i++
								}
								i = 0
								val partitionParams = o.getJSONArray("partitions")
								while (i < partitionParams.length()) {
									val l = partitionParams.getJSONObject(i)
									val id = l.getString("id")
									if (parts.find { it.id == id } != null)
										throw IllegalStateException("duplicate $id in partitions?")
									if (vm.idNeeded.contains(id))
										throw IllegalStateException("duplicate $id in idNeeded?")
									parts.add(Part(l.getLong("size"),
										l.getBoolean("isPercent"),
										l.getString("type"),
										id, l.getBoolean("needUnsparse")))
									if (!idUnneeded.remove(id))
										vm.idNeeded.add(id)
									i++
								}
								if (idUnneeded.isNotEmpty())
									throw IllegalStateException("useless blocked ids $idUnneeded")
								i = 0
								val inets = o.getJSONArray("inet")
								while (i < inets.length()) {
									val l = inets.getJSONObject(i)
									vm.inetAvailable[l.getString("id")] =
										WizardState.Downloadable(
											l.getString("url"),
											l.getStringOrNull("hash"),
											l.getString("desc")
										)
									i++
								}
								vm.idNeeded.add("_install.sh_")
								vm.inetAvailable["_install.sh_"] = WizardState.Downloadable(
									o.getString("scriptname"),
									o.getStringOrNull("scriptSha256"),
									vm.activity.getString(R.string.installer_sh)
								)

								painter = painterFromRtype(rtype)
							}
							c.vm.navigate("os")
						}) {
					val l = json!!.getJSONArray("oses").getJSONObject(i)
					val painter = c.painterFromRtype(l.getString("rtype"))
					Icon(painter = painter(), contentDescription = stringResource(R.string.os_logo_content_desc))
					Text(l.getString("displayname"))
				}
				i++
			}
		}
	} else {
		Text(stringResource(R.string.shop_error))
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Os(c: CreatePartDataHolder) {
	LaunchedEffect(Unit) {
		withContext(Dispatchers.IO) {
			val entries = SuFile.open(c.vm.logic.abmEntries.absolutePath).list()!!.toMutableList()
			entries.removeIf { cfg ->
				!(cfg.startsWith("rom") && cfg.endsWith(".conf") && cfg.substring(
					3,
					cfg.length - 5
				).matches(Regex("\\d+")))
			}
			entries.sortWith(Comparator.comparingInt { cfg -> cfg.substring(3, cfg.length - 5).toInt() })
			val uniqueNumber = if (entries.isNotEmpty())
				entries.last().substring(3, entries.last().length - 5).toInt() + 1 else 0
			c.romFolderName = "rom$uniqueNumber"
			c.romDisplayName = c.dmaMeta["name"]!!
		}
	}

	val s = rememberScrollState()
	var expanded by remember { mutableIntStateOf(0) }
	val romFolderNameInvalid by remember { derivedStateOf { !(c.romFolderName.matches(safeFsRegex)) } }
	val romDisplayNameInvalid by remember { derivedStateOf { !(c.romDisplayName.matches(asciiNonEmptyRegex)) } }
	val invalid by remember { derivedStateOf { romFolderNameInvalid || romDisplayNameInvalid } }
	Column(
		Modifier
			.fillMaxSize()
			.verticalScroll(s)) {
		if (expanded != 2) {
			Column(
				Modifier
					.fillMaxWidth(),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				Icon(c.painter!!(), contentDescription = stringResource(R.string.rom_logo_content_desc), modifier = Modifier.size(256.dp))
				Text(c.dmaMeta["name"]!!)
				Text(c.dmaMeta["creator"]!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
				MyInfoCard(stringResource(R.string.almost_installed_rom))
			}
		}
		if (!c.vm.mvm.noobMode)
			Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier
				.fillMaxWidth()
				.padding(5.dp)
				.clickable {
					expanded = if (expanded == 1) 0 else 1
				}) {
				Text(stringResource(R.string.bl_option))
				if (expanded == 1) {
					Icon(painterResource(id = R.drawable.ic_baseline_keyboard_arrow_up_24), stringResource(R.string.close_content_desc))
				} else {
					Icon(painterResource(id = R.drawable.ic_baseline_keyboard_arrow_down_24), stringResource(R.string.expand_content_desc))
				}
			}
		if (expanded == 1 || c.vm.mvm.noobMode) {
			Column(
				Modifier
					.fillMaxWidth()
					.padding(5.dp)
			) {
				if (!c.vm.mvm.noobMode)
					TextField(value = c.romFolderName, onValueChange = {
						c.romFolderName = it
					}, isError = romFolderNameInvalid, label = {
						Text(stringResource(R.string.internal_id))
					})
				TextField(value = c.romDisplayName, onValueChange = {
					c.romDisplayName = it
				}, isError = romDisplayNameInvalid, label = {
					Text(stringResource(R.string.name_in_boot))
				})
			}
		}
		if (!c.vm.mvm.noobMode)
			Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier
				.fillMaxWidth()
				.padding(5.dp)
				.clickable {
					expanded = if (expanded == 2) 0 else 2
				}) {
				Text(stringResource(R.string.part_layout))
				if (expanded == 2) {
					Icon(painterResource(id = R.drawable.ic_baseline_keyboard_arrow_up_24), stringResource(R.string.close_content_desc))
				} else {
					Icon(painterResource(id = R.drawable.ic_baseline_keyboard_arrow_down_24), stringResource(R.string.expand_content_desc))
				}
			}
		if (expanded == 2) {
			Column(Modifier.fillMaxWidth()) {
				c.parts.forEachIndexed { i, part ->
					var d by remember { mutableStateOf(false) }

					Card(
						modifier = Modifier
							.fillMaxWidth()
							.padding(10.dp)
					) {
						Column(
							modifier = Modifier
								.fillMaxWidth()
								.padding(10.dp)
						) {
							var sizeInSectors: Long = -1
							var remaining = c.endSectorRelative - c.startSectorRelative
							for (iPart in c.parts.slice(0..i)) {
								sizeInSectors = iPart.resolveSectorSize(c, remaining)
								remaining -= sizeInSectors
							}
							remaining += sizeInSectors

							val selUnit = stringResource(if (part.isPercent) R.string.percent else R.string.bytes)
							Text(text = stringResource(R.string.sector_used, part.size, selUnit, sizeInSectors, remaining))
							TextField(value = part.size.toString(), onValueChange = {
								if (it.matches(Regex("\\d+"))) {
									part.size = it.toLong()
								}
							}, label = {
								Text(stringResource(R.string.size))
							})
							Row(Modifier.padding(8.dp)) {
								Row(
									verticalAlignment = Alignment.CenterVertically,
									modifier = Modifier
										.selectable(
											selected = !part.isPercent,
											onClick = { part.isPercent = false },
											role = Role.RadioButton
										)
										.padding(8.dp)
								) {
									RadioButton(
										selected = !part.isPercent,
										onClick = null
									)
									Text(text = stringResource(R.string.bytes))
								}
								Row(
									verticalAlignment = Alignment.CenterVertically,
									modifier = Modifier
										.selectable(
											selected = part.isPercent,
											onClick = { part.isPercent = true },
											role = Role.RadioButton
										)
										.padding(8.dp)
								) {
									RadioButton(
										selected = part.isPercent,
										onClick = null
									)
									Text(text = stringResource(R.string.percent))
								}
							}
							ExposedDropdownMenuBox(expanded = d, onExpandedChange = { d = it }) {
								TextField(
									readOnly = true,
									value = stringResource(partitionTypeCodes.find { it.first == part.code }!!.second),
									onValueChange = { },
									label = { Text(stringResource(R.string.type)) },
									trailingIcon = {
										ExposedDropdownMenuDefaults.TrailingIcon(
											expanded = d
										)
									},
									colors = ExposedDropdownMenuDefaults.textFieldColors()
								)
								ExposedDropdownMenu(
									expanded = d,
									onDismissRequest = {
										d = false
									}
								) {
									for (g in partitionTypeCodes) {
										DropdownMenuItem(
											onClick = {
												part.code = g.first
												d = false
											}, text = {
												Text(text = stringResource(g.second))
											}
										)
									}
								}
							}
							TextField(
								value = part.id,
								onValueChange = { part.id = it },
								label = { Text(stringResource(R.string.id)) }
							)
							Row(verticalAlignment = Alignment.CenterVertically,
								modifier = Modifier.clickable { part.sparse = !part.sparse }) {
								Checkbox(checked = part.sparse, onCheckedChange = { part.sparse = it })
								Text(stringResource(R.string.sparse))
							}
						}
					}
				}
				Row(verticalAlignment = Alignment.CenterVertically) {
					Button(onClick = { c.parts.add(
						Part(
							100L,
							true,
							"8305",
							"",
							false
						)
					) }) {
						Text("+")
					}
					Button(onClick = { c.parts.removeAt(c.parts.lastIndex) }, enabled = (c.parts.size > 1)) {
						Text("-")
					}
					var remaining = c.endSectorRelative - c.startSectorRelative
					for (part in c.parts) {
						remaining -= part.resolveSectorSize(c, remaining)
					}
					Text(stringResource(R.string.remaining_sector, remaining, c.endSectorRelative - c.startSectorRelative))
				}
			}
		}
	}
	LaunchedEffect(invalid) {
		if (invalid) {
			c.vm.onNext = {}
			c.vm.nextText = ""
		} else {
			c.vm.onNext = { it.navigate("dload") }
			c.vm.nextText = c.vm.activity.getString(R.string.install)
		}
	}
}

@Composable
private fun Flash(c: CreatePartDataHolder) {
	val vm = c.vm
	WizardTerminalWork(vm, logFile = "install_${System.currentTimeMillis()}.txt") { terminal ->
		c.vm.logic.extractToolkit(terminal)
		c.vm.downloadRemainingFiles(terminal)
		if (c.partitionName == null) { // OS install
			val createdParts = mutableListOf<Pair<Part, Int>>() // order is important
			val fn = c.romFolderName
			terminal.add(vm.activity.getString(R.string.term_f_name, fn))
			terminal.add(vm.activity.getString(R.string.term_g_name, c.romDisplayName))
			val tmpFile = c.vm.chosen["_install.sh_"]!!.toFile(vm)
			tmpFile.setExecutable(true)
			terminal.add(vm.activity.getString(R.string.term_creating_pt))

			vm.logic.unmountBootset(vm.deviceInfo)
			val startSectorAbsolute = c.p.startSector + c.startSectorRelative
			val endSectorAbsolute = c.p.startSector + c.endSectorRelative
			if (endSectorAbsolute > c.p.endSector)
				throw IllegalArgumentException("$endSectorAbsolute can't be bigger than ${c.p.endSector}")
			c.parts.forEachIndexed { index, part -> // TODO !metaonsd
				terminal.add(vm.activity.getString(R.string.term_create_part))
				val start = c.p.startSector.coerceAtLeast(startSectorAbsolute)
				val end = c.p.endSector.coerceAtMost(endSectorAbsolute)
				val k = part.resolveSectorSize(c, end - start)
				if (start + k > end)
					throw IllegalStateException("$start + $k = ${start + k} shouldn't be bigger than $end")
				if (k < 0)
					throw IllegalStateException("$k shouldn't be smaller than 0")
				// create(start, end) values are relative to the free space area
				val r = vm.logic.create(c.p, start - c.p.startSector,
					(start + k) - c.p.startSector, part.code, "").to(terminal).exec()
				if (r.out.joinToString("\n").contains("kpartx")) {
					terminal.add(vm.activity.getString(R.string.term_reboot_asap))
				}
				createdParts.add(Pair(part, c.meta!!.nid))
				c.meta = SDUtils.generateMeta(c.vm.deviceInfo)
				// do not assert there is leftover space if we just created the last partition we want to create
				if (index < c.parts.size - 1) {
					c.p =
						c.meta!!.s.find { it.type == SDUtils.PartitionType.FREE && start + k < it.startSector } as SDUtils.Partition.FreeSpace
				}
				if (r.isSuccess) {
					terminal.add(vm.activity.getString(R.string.term_created_part))
				} else {
					terminal.add(vm.activity.getString(R.string.term_failure))
					return@WizardTerminalWork
				}
			}
			terminal.add(vm.activity.getString(R.string.term_created_pt))
			vm.logic.mountBootset(vm.deviceInfo)
			val meta = SDUtils.generateMeta(vm.deviceInfo)
			if (meta == null) {
				terminal.add(vm.activity.getString(R.string.term_cant_get_meta))
				return@WizardTerminalWork
			}
			terminal.add(vm.activity.getString(R.string.term_building_cfg))

			val entry = ConfigFile()
			entry["title"] = c.romDisplayName
			entry["linux"] = "$fn/zImage"
			entry["initrd"] = "$fn/initrd.cpio.gz"
			entry["dtb"] = "$fn/dtb.dtb"
			if (vm.deviceInfo.havedtbo)
				entry["dtbo"] = "$fn/dtbo.dtbo"
			entry["options"] = c.cmdline
			entry["xtype"] = c.rtype
			entry["xpart"] = createdParts.map { it.second }.joinToString(":")
			if (c.dmaMeta.contains("updateJson") && c.dmaMeta["updateJson"] != null)
				entry["xupdate"] = c.dmaMeta["updateJson"]!!
			entry.exportToFile(File(vm.logic.abmEntries, "$fn.conf"))
			if (!SuFile.open(File(vm.logic.abmBootset, fn).toURI()).mkdir()) {
				terminal.add(vm.activity.getString(R.string.term_mkdir_failed))
				return@WizardTerminalWork
			}

			terminal.add(vm.activity.getString(R.string.term_flashing_imgs))
			for (part in c.parts) {
				if (!c.vm.idNeeded.contains(part.id)) continue
				terminal.add(vm.activity.getString(R.string.term_flashing_s, part.id))
				val f = c.vm.chosen[part.id]!!
				val tp = File(meta.dumpKernelPartition(createdParts.find { it.first == part }!!.second).path)
				if (part.sparse) {
					val result2 = Shell.cmd(
						File(
							c.vm.logic.toolkitDir,
							"simg2img"
						).absolutePath + " ${f.toFile(c.vm).absolutePath} ${tp.absolutePath}"
					).to(terminal).exec()
					f.delete()
					if (!result2.isSuccess) {
						terminal.add(vm.activity.getString(R.string.term_failure))
						return@WizardTerminalWork
					}
				} else {
					c.vm.copyPriv(f.openInputStream(c.vm), tp)
				}
				terminal.add(vm.activity.getString(R.string.term_done))
			}

			terminal.add(vm.activity.getString(R.string.term_patching_os))
			var cmd = "FORMATDATA=true " + tmpFile.absolutePath + " $fn"
			for (i in c.extraIdNeeded) {
				cmd += " " + c.vm.chosen[i]!!.toFile(vm).absolutePath
			}
			for (i in c.parts) {
				cmd += " " + createdParts.find { it.first == i }!!.second
			}
			val result = vm.logic.runShFileWithArgs(cmd).to(terminal).exec()
			if (!result.isSuccess) {
				terminal.add(vm.activity.getString(R.string.term_failure))
				return@WizardTerminalWork
			}

			terminal.add(vm.activity.getString(R.string.term_success))
		} else { // Portable partition
			terminal.add(vm.activity.getString(R.string.term_create_part))
			vm.logic.unmountBootset(vm.deviceInfo)
			val r = vm.logic.create(c.p,
					c.startSectorRelative,
					c.endSectorRelative,
					"0700",
					c.partitionName!!
				).to(terminal).exec()
			if (r.out.joinToString("\n").contains("kpartx")) {
				terminal.add(vm.activity.getString(R.string.term_reboot_asap))
			}
			if (r.isSuccess) {
				terminal.add(vm.activity.getString(R.string.term_success))
			} else {
				terminal.add(vm.activity.getString(R.string.term_failure))
			}
		}
	}
}