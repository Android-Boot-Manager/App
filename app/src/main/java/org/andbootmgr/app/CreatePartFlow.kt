package org.andbootmgr.app

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.collection.ArrayMap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okio.*
import org.andbootmgr.app.util.ConfigFile
import org.andbootmgr.app.util.SDUtils
import org.andbootmgr.app.util.SOUtils
import org.andbootmgr.app.util.Terminal
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.math.BigDecimal
import java.net.URL
import java.util.concurrent.TimeUnit

class CreatePartFlow(private val desiredStartSector: Long): WizardFlow() {
	override fun get(vm: WizardActivityState): List<IWizardPage> {
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
			Download(c)
		}, WizardPage("flash",
			NavButton("") {},
			NavButton("") {}
		) {
			Flash(c)
		})
	}
}


private class ProgressResponseBody(
	private val responseBody: ResponseBody,
	private val progressListener: ProgressListener
) :
	ResponseBody() {
	private var bufferedSource: BufferedSource? = null
	override fun contentType(): MediaType? {
		return responseBody.contentType()
	}

	override fun contentLength(): Long {
		return responseBody.contentLength()
	}

	override fun source(): BufferedSource {
		if (bufferedSource == null) {
			bufferedSource = source(responseBody.source()).buffer()
		}
		return bufferedSource!!
	}

	private fun source(source: Source): Source {
		return object : ForwardingSource(source) {
			var totalBytesRead = 0L

			@Throws(IOException::class)
			override fun read(sink: Buffer, byteCount: Long): Long {
				val bytesRead = super.read(sink, byteCount)
				// read() returns the number of bytes read, or -1 if this source is exhausted.
				totalBytesRead += if (bytesRead != -1L) bytesRead else 0
				progressListener.update(
					totalBytesRead,
					responseBody.contentLength(),
					bytesRead == -1L
				)
				return bytesRead
			}
		}
	}
}

internal interface ProgressListener {
	fun update(bytesRead: Long, contentLength: Long, done: Boolean)
}

private class CreatePartDataHolder(val vm: WizardActivityState, val desiredStartSector: Long): ProgressListener {
	var meta by mutableStateOf<SDUtils.SDPartitionMeta?>(null)
	lateinit var p: SDUtils.Partition.FreeSpace
	var startSectorRelative = 0L
	var endSectorRelative = 0L
	var partitionName: String? = null
	var scriptInet: String? = null
	var scriptShaInet: String? = null

	var painter: @Composable (() -> Painter)? = null
	var rtype = ""
	var cmdline = ""
	val dmaMeta = ArrayMap<String, String>()
	var count by mutableIntStateOf(0)
	val intVals = mutableStateListOf<Long>()
	val selVals = mutableStateListOf<Int>()
	val codeVals = mutableStateListOf<String>()
	val idVals = mutableStateListOf<String>()
	val sparseVals = mutableStateListOf<Boolean>()
	val inetAvailable = HashMap<String, String>()
	val inetDesc = HashMap<String, String>()
	val idNeeded = mutableStateListOf<String>()
	val extraIdNeeded = mutableStateListOf<String>()
	val idUnneeded = mutableStateListOf<String>()
	val chosen = mutableStateMapOf<String, DledFile>()
	val client by lazy { OkHttpClient().newBuilder().readTimeout(1L, TimeUnit.HOURS).addNetworkInterceptor {
		val originalResponse: Response = it.proceed(it.request())
		return@addNetworkInterceptor originalResponse.newBuilder()
			.body(ProgressResponseBody(originalResponse.body!!, this))
			.build()
	}.build() }
	var pl: ProgressListener? = null
	var romFolderName by mutableStateOf("")
	var romDisplayName by mutableStateOf("")

	override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
		pl?.update(bytesRead, contentLength, done)
	}

	fun addDefault(i: Long, sel: Int, code: String, id: String, needUnsparse: Boolean) {
		if (idVals.contains(id)) return
		count++
		intVals.add(i)
		selVals.add(sel)
		codeVals.add(code)
		idVals.add(id)
		sparseVals.add(needUnsparse)
	}

	fun onStartDlPage() {
		idNeeded.addAll(idVals)
		idNeeded.removeAll(idUnneeded)
	}

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
				val meta = SDUtils.generateMeta(c.vm.deviceInfo)!!
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

		if (c.vm.mvm.noobMode) {
			Card(
				modifier = Modifier
					.fillMaxWidth()
					.padding(10.dp)
			) {
				Row(
					Modifier
						.fillMaxWidth()
						.padding(20.dp)
				) {
					Icon(painterResource(id = R.drawable.ic_about), "Icon")
					Text(stringResource(R.string.option_select))
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
					Icon(painterResource(id = R.drawable.ic_sd), stringResource(R.string.icon_content_desc), Modifier.padding(end = 10.dp))
					Text(stringResource(R.string.portable_part))
				}
				TextField(value = partitionName, onValueChange = {
					partitionName = it
				}, isError = partitionNameInvalid, label = {
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
					} catch (e: FileNotFoundException) {
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
			Card {
				Row(
					Modifier
						.fillMaxWidth()
						.padding(20.dp)
				) {
					Icon(painterResource(id = R.drawable.ic_about), stringResource(R.string.icon_content_desc))
					Text(stringResource(R.string.select_os))
				}
			}
			//Log.i("ABM shop:", "Found: ${json!!.getJSONArray("oses").length()} oses")
			var i = 0
			while(i < json!!.getJSONArray("oses").length()) {
				val index = i
				Row(horizontalArrangement = Arrangement.SpaceEvenly,
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier
						.fillMaxWidth()
						.clickable {
							//Log.i("ABM shop:", "Selected OS: $index")
							c.run {
								val o = json!!
									.getJSONArray("oses")
									.getJSONObject(index)
								dmaMeta["name"] =
									o.getString("displayname")
								dmaMeta["creator"] = o.getString("creator")
								c.scriptInet = o.getString("scriptname")
								if (o.has("scriptSha256"))
									c.scriptShaInet = o.getString("scriptSha256")
								dmaMeta["updateJson"] = o.getString("updateJson")
								rtype = o.getString("rtype")
								cmdline =
									o.getString("cmdline")
								i = 0
								val partitionParams = o.getJSONArray("partitions")
								while (i < partitionParams.length()) {
									val l = partitionParams.getJSONObject(i)
									addDefault(
										l.getLong("size"),
										if (l.getBoolean("isPercent")) {
											1
										} else {
											0
										},
										l.getString("type"),
										l.getString("id"),
										l.getBoolean("needUnsparse")
									)
									i++
								}
								i = 0
								val inets = o.getJSONArray("inet")
								while (i < inets.length()) {
									val l = inets.getJSONObject(i)
									val li = l.getString("id")
									inetAvailable[li] = l.getString("url")
									inetDesc[li] = l.getString("desc")
									i++
								}
								i = 0
								val extraIdNeeded = o.getJSONArray("extraIdNeeded")
								while (i < extraIdNeeded.length()) {
									this.extraIdNeeded.add(extraIdNeeded.get(i) as String)
									i++
								}
								idNeeded.addAll(this.extraIdNeeded)
								i = 0
								val blockIdNeeded = o.getJSONArray("blockIdNeeded")
								while (i < blockIdNeeded.length()) {
									idUnneeded.add(blockIdNeeded.get(i) as String)
									i++
								}

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
				Card {
					Row(
						Modifier
							.fillMaxWidth()
							.padding(20.dp)
					) {
						Icon(painterResource(id = R.drawable.ic_about), "Icon")
						Text(stringResource(R.string.almost_installed_rom))
					}
				}
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
			Column(
				Modifier
					.fillMaxWidth()) {

				for (i in 1..c.count) {
					val selectedValue = remember { mutableIntStateOf(c.selVals.getOrElse(i-1) { 1 }) }
					val intValue = remember { mutableLongStateOf(c.intVals.getOrElse(i-1) { 100L }) }
					var codeValue by remember { mutableStateOf(c.codeVals.getOrElse(i-1) { "8305" }) }
					var idValue by remember { mutableStateOf(c.idVals.getOrElse(i-1) { "" }) }
					var d by remember { mutableStateOf(false) }
					var sparse by remember { mutableStateOf(c.sparseVals.getOrElse(i-1) { false }) }
					val items = listOf("bytes", "percent")
					val items2 = listOf("0700", "8302", "8305")
					val codes = listOf(stringResource(R.string.portable_part), stringResource(R.string.os_userdata), stringResource(R.string.os_system))
					val isSelectedItem: (String) -> Boolean = { items[selectedValue.intValue] == it }
					val onChangeState: (String) -> Unit = { selectedValue.intValue = items.indexOf(it) }

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
							var sts: Long = -1
							var remaining = c.endSectorRelative - c.startSectorRelative
							if (i-1 < c.selVals.size) {
								c.selVals[i - 1] = selectedValue.intValue
							} else {
								c.selVals.add(i - 1, selectedValue.intValue)
							}
							if (i-1 < c.intVals.size) {
								c.intVals[i - 1] = intValue.longValue
							} else {
								c.intVals.add(i - 1, intValue.longValue)
							}
							if (i-1 < c.codeVals.size) {
								c.codeVals[i - 1] = codeValue
							} else {
								c.codeVals.add(i - 1, codeValue)
							}
							if (i-1 < c.idVals.size) {
								c.idVals[i - 1] = idValue
							} else {
								c.idVals.add(i - 1, idValue)
							}
							if (i-1 < c.sparseVals.size) {
								c.sparseVals[i - 1] = sparse
							} else {
								c.sparseVals.add(i - 1, sparse)
							}
							for (j in 1 .. i) {
								val k = c.intVals.getOrElse(j-1) { 0L }
								val l = c.selVals.getOrElse(j-1) { 1 }
								sts = if (l == 0 /*bytes*/) {
									k / c.meta!!.logicalSectorSizeBytes
								} else /*percent*/ {
									(BigDecimal(remaining).multiply(BigDecimal(k).divide(BigDecimal(100)))).toLong()
								}
								remaining -= sts
							}
							remaining += sts

							Text(text = stringResource(R.string.sector_used, intValue.longValue, items[selectedValue.intValue], sts, remaining))
							TextField(value = intValue.longValue.toString(), onValueChange = {
								if (it.matches(Regex("\\d+"))) {
									intValue.longValue = it.toLong()
								}
							}, label = {
								Text(stringResource(R.string.size))
							})
							Row(Modifier.padding(8.dp)) {
								items.forEach { item ->
									Row(
										verticalAlignment = Alignment.CenterVertically,
										modifier = Modifier
											.selectable(
												selected = isSelectedItem(item),
												onClick = { onChangeState(item) },
												role = Role.RadioButton
											)
											.padding(8.dp)
									) {
										RadioButton(
											selected = isSelectedItem(item),
											onClick = null
										)
										Text(
											text = item
										)
									}
								}
							}
							ExposedDropdownMenuBox(expanded = d, onExpandedChange = { d = it }) {
								TextField(
									readOnly = true,
									value = codes[items2.indexOf(codeValue)],
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

									for (g in codes) {
										DropdownMenuItem(
											onClick = {
												codeValue = items2[codes.indexOf(g)]
												d = false
											}, text = {
												Text(text = g)
											}
										)
									}
								}
							}
							TextField(
								value = idValue,
								onValueChange = { idValue = it },
								label = { Text(stringResource(R.string.id)) }
							)
							Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { sparse = !sparse }) {
								Checkbox(checked = sparse, onCheckedChange = { sparse = it })
								Text(stringResource(R.string.sparse))
							}
						}
					}
				}
				Row(verticalAlignment = Alignment.CenterVertically) {
					Button(onClick = { c.count += 1 }) {
						Text("+")
					}
					Button(onClick = { c.count -= 1 }, enabled = (c.count > 1)) {
						Text("-")
					}
					var remaining = c.endSectorRelative - c.startSectorRelative
					for (j in 1 .. c.count) {
						val inSize = c.intVals.getOrElse(j-1) { 0L }
						val bytesOrPercent = c.selVals.getOrElse(j-1) { 1 }
						val sizeInSectors = if (bytesOrPercent == 0 /*bytes*/) {
							inSize / c.meta!!.logicalSectorSizeBytes
						} else /*percent*/ {
							// remaining * (inSize/100) -> inSize percent of remaining
							(BigDecimal(remaining).multiply(BigDecimal(inSize).divide(BigDecimal(100)))).toLong()
						}
						remaining -= sizeInSectors
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

private class DledFile(val safFile: Uri?, val netFile: File?) {
	fun delete() {
		netFile?.delete()
	}

	fun openInputStream(vm: WizardActivityState): InputStream {
		netFile?.let {
			return FileInputStream(it)
		}
		safFile?.let {
			val istr = vm.activity.contentResolver.openInputStream(it)
			if (istr != null) {
				return istr
			}
		}
		throw IllegalStateException("invalid DledFile OR failure")
	}

	fun toFile(vm: WizardActivityState): File {
		netFile?.let { return it }
		safFile?.let {
			val istr = vm.activity.contentResolver.openInputStream(it)
			if (istr != null) {
				val f = File(vm.logic.cacheDir, System.currentTimeMillis().toString())
				vm.copyUnpriv(istr, f)
				istr.close()
				return f
			}
		}
		throw IllegalStateException("invalid DledFile OR safFile failure")
	}
}

@Composable
private fun Download(c: CreatePartDataHolder) {
	LaunchedEffect(Unit) {
		c.onStartDlPage()
	}
	Column(Modifier.fillMaxSize()) {
		Card {
			Row(
				Modifier
					.fillMaxWidth()
					.padding(20.dp)
			) {
				Icon(painterResource(id = R.drawable.ic_about), stringResource(id = R.string.icon_content_desc))
				Text(stringResource(id = R.string.provide_images))
			}
		}
		var cancelDownload by remember { mutableStateOf<(() -> Unit)?>(null) }
		var progressText by remember { mutableStateOf(c.vm.activity.getString(R.string.connecting_text)) }
		if (cancelDownload != null) {
			AlertDialog(
				onDismissRequest = {},
				confirmButton = {
					Button(onClick = { cancelDownload!!() }) {
						Text(stringResource(id = R.string.cancel))
					}
				},
				title = { Text(stringResource(R.string.downloading)) },
				text = {
					LoadingCircle(progressText, paddingBetween = 10.dp)
				})
		}
		for (i in (c.idNeeded + listOf("_install.sh_"))) {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
				modifier = Modifier.fillMaxWidth()
			) {
				Column {
					Text(i)
					Text(
						if (c.inetDesc.containsKey(i)) c.inetDesc[i]!! else stringResource(
							if (i == "_install.sh_") R.string.installer_sh else R.string.user_selected),
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				Column {
					if (c.chosen.containsKey(i)) {
						Button(onClick = {
							c.chosen[i]!!.delete()
							c.chosen.remove(i)
						}) {
							Text(stringResource(R.string.undo))
						}
					} else {
						if (c.inetAvailable.containsKey(i) || i == "_install.sh_") {
							Button(onClick = {
								CoroutineScope(Dispatchers.IO).launch {
									try {
										val downloadedFile = File(c.vm.logic.cacheDir, i)
										val request =
											Request.Builder().url(if (i == "_install.sh_")
												c.scriptInet!! else c.inetAvailable[i]!!).build()
										val call = c.client.newCall(request)
										progressText = c.vm.activity.getString(R.string.connecting_text)
										c.pl = object : ProgressListener {
											override fun update(
												bytesRead: Long,
												contentLength: Long,
												done: Boolean
											) {
												progressText = c.vm.activity.getString(R.string.download_progress,
													SOUtils.humanReadableByteCountBin(bytesRead), SOUtils.humanReadableByteCountBin(contentLength))
											}
										}
										cancelDownload = {
											call.cancel()
											downloadedFile.delete()
											cancelDownload = null
										}
										val response = call.execute()
										val desiredHash = if (i == "_install.sh_") c.scriptShaInet!! else null

										val rawSink = downloadedFile.sink()
										val sink = if (desiredHash != null) HashingSink.sha256(rawSink) else rawSink
										val buffer = sink.buffer()
										buffer.writeAll(response.body!!.source())
										buffer.close()
										val realHash = if (desiredHash != null)
												(sink as HashingSink).hash.hex() else null
										if (!call.isCanceled()) {
											if (desiredHash != null && realHash != desiredHash)
												throw IllegalStateException("hash $realHash does not match expected hash $desiredHash")
											c.chosen[i] = DledFile(null, downloadedFile)
										}
									} catch (e: Exception) {
										Log.e("ABM", Log.getStackTraceString(e))
										withContext(Dispatchers.Main) {
											Toast.makeText(
												c.vm.activity,
												c.vm.activity.getString(R.string.dl_error),
												Toast.LENGTH_LONG
											).show()
										}
									}
									c.pl = null
									cancelDownload = null
								}
							}) {
								Text(stringResource(R.string.download))
							}
						}
						Button(onClick = {
							c.vm.activity.chooseFile("*/*") {
								c.chosen[i] = DledFile(it, null)
							}
						}) {
							Text(stringResource(R.string.choose))
						}
					}
				}
			}
		}
		val isOk = c.idNeeded.find { !c.chosen.containsKey(it) } == null && c.chosen.containsKey("_install.sh_")
		LaunchedEffect(isOk) {
			if (isOk) {
				c.vm.onNext = { it.navigate("flash") }
				c.vm.nextText = c.vm.activity.getString(R.string.install)
			} else {
				c.vm.onNext = {}
				c.vm.nextText = ""
			}
		}
	}
}

@Composable
private fun Flash(c: CreatePartDataHolder) {
	val vm = c.vm
	Terminal(logFile = "install_${System.currentTimeMillis()}.txt") { terminal ->
		c.vm.logic.extractToolkit(terminal)
		if (c.partitionName == null) { // OS install
			val parts = ArrayMap<Int, Int>()
			val fn = c.romFolderName
			val gn = c.romDisplayName
			terminal.add(vm.activity.getString(R.string.term_f_name, fn))
			terminal.add(vm.activity.getString(R.string.term_g_name, gn))
			val tmpFile = c.chosen["_install.sh_"]!!.toFile(vm)
			tmpFile.setExecutable(true)
			terminal.add(vm.activity.getString(R.string.term_creating_pt))

			// After creating partitions:
			fun installMore() {
				val meta = SDUtils.generateMeta(vm.deviceInfo)
				if (meta == null) {
					terminal.add(vm.activity.getString(R.string.term_cant_get_meta))
					return
				}
				terminal.add(vm.activity.getString(R.string.term_building_cfg))

				val entry = ConfigFile()
				entry["title"] = gn
				entry["linux"] = "$fn/zImage"
				entry["initrd"] = "$fn/initrd.cpio.gz"
				entry["dtb"] = "$fn/dtb.dtb"
				if (vm.deviceInfo.havedtbo)
					entry["dtbo"] = "$fn/dtbo.dtbo"
				entry["options"] = c.cmdline
				entry["xtype"] = c.rtype
				entry["xpart"] = parts.values.joinToString(":")
				if (c.dmaMeta.contains("updateJson") && c.dmaMeta["updateJson"] != null)
					entry["xupdate"] = c.dmaMeta["updateJson"]!!
				entry.exportToFile(File(vm.logic.abmEntries, "$fn.conf"))
				if (!SuFile.open(File(vm.logic.abmBootset, fn).toURI()).mkdir()) {
					terminal.add(vm.activity.getString(R.string.term_mkdir_failed))
					return
				}

				terminal.add(vm.activity.getString(R.string.term_flashing_imgs))
				for (i in c.idVals) {
					if (c.idUnneeded.contains(i)) continue
					val j = c.idVals.indexOf(i)
					terminal.add(vm.activity.getString(R.string.term_flashing_s, i))
					val f = c.chosen[i]!!
					val tp = File(meta.dumpKernelPartition(parts[j]!!).path)
					if (c.sparseVals[j]) {
						val f2 = f.toFile(c.vm)
						val result2 = Shell.cmd(
							File(
								c.vm.logic.toolkitDir,
								"simg2img"
							).absolutePath + " ${f2.absolutePath} ${tp.absolutePath}"
						).to(terminal).exec()
						f.delete()
						if (!result2.isSuccess) {
							terminal.add(vm.activity.getString(R.string.term_failure))
							return
						}
					} else {
						val f2 = f.openInputStream(c.vm)
						c.vm.copyPriv(f2, tp)
					}
					terminal.add(vm.activity.getString(R.string.term_done))
				}

				terminal.add(vm.activity.getString(R.string.term_patching_os))
				var cmd = "FORMATDATA=true " + tmpFile.absolutePath + " $fn"
				for (i in c.extraIdNeeded) {
					cmd += " " + c.chosen[i]!!.toFile(vm).absolutePath
				}
				for (i in parts) {
					cmd += " " + i.value
				}
				val result = vm.logic.runShFileWithArgs(cmd).to(terminal).exec()
				if (!result.isSuccess) {
					terminal.add(vm.activity.getString(R.string.term_failure))
					return
				}

				terminal.add(vm.activity.getString(R.string.term_success))
				vm.nextText = vm.activity.getString(R.string.finish)
				vm.onNext = { it.finish() }
			}

			var offset = c.startSectorRelative

			var makeOne: (Int) -> Unit = {}
			makeOne = {
				terminal.add(vm.activity.getString(R.string.term_create_part))

				val b = c.intVals.getOrElse(it) { 0L }
				val l = c.selVals.getOrElse(it) { 1 }
				val code = c.codeVals.getOrElse(it) { "8305" }
				val k = if (l == 0 /*bytes*/) {
					b / c.meta!!.logicalSectorSizeBytes
				} else /*percent*/ {
					(BigDecimal(c.p.size - (offset + (c.p.size - c.endSectorRelative))).multiply(BigDecimal(b).divide(BigDecimal(100)))).toLong()
				}

				vm.logic.unmountBootset()
				val r = vm.logic.create(c.p, offset, offset + k, code, "").to(terminal).exec()
				try {
					if (r.out.joinToString("\n").contains("kpartx")) {
						terminal.add(vm.activity.getString(R.string.term_reboot_asap))
					}
					parts[it] = c.meta!!.nid
					c.meta = SDUtils.generateMeta(c.vm.deviceInfo)
					if (it + 1 < c.count) {
						c.p = c.meta!!.s.find { it1 -> it1.type == SDUtils.PartitionType.FREE && (offset + k) < it1.startSector } as SDUtils.Partition.FreeSpace
					}
					if (r.isSuccess) {
						terminal.add(vm.activity.getString(R.string.term_created_part))
						offset = 0L
						if (it + 1 < c.count) {
							makeOne(it + 1)
						} else {
							terminal.add(vm.activity.getString(R.string.term_created_pt))
							vm.logic.mountBootset(vm.deviceInfo)
							installMore()
						}
					} else {
						terminal.add(vm.activity.getString(R.string.term_failure))
					}
				} catch (e: Exception) {
					terminal.add(vm.activity.getString(R.string.term_failure))
					terminal.add(Log.getStackTraceString(e))
				}
			}
			makeOne(0)
		} else { // Portable partition
			terminal.add(vm.activity.getString(R.string.term_create_part))
			vm.logic.unmountBootset()
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
				vm.nextText = c.vm.activity.getString(R.string.finish)
				vm.onNext = { it.finish() }
				terminal.add(vm.activity.getString(R.string.term_success))
			} else {
				terminal.add(vm.activity.getString(R.string.term_failure))
			}
		}
	}
}