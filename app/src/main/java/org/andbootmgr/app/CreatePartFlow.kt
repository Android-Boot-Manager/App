package org.andbootmgr.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.collection.ArrayMap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import okhttp3.*
import okio.*
import org.andbootmgr.app.util.AbmTheme
import org.andbootmgr.app.util.ConfigFile
import org.andbootmgr.app.util.SDUtils
import org.andbootmgr.app.util.SOUtils
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.json.JSONTokener
import java.io.FileNotFoundException
import java.net.URL

class CreatePartWizardPageFactory(private val vm: WizardActivityState) {
	fun get(): List<IWizardPage> {
		val c = CreatePartDataHolder(vm)
		return listOf(WizardPage("start",
			NavButton(vm.activity.getString(R.string.cancel)) {
				it.startActivity(Intent(it, MainActivity::class.java))
				it.finish()
			},
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
			NavButton(vm.activity.getString(R.string.cancel)) {
				it.startActivity(Intent(it, MainActivity::class.java))
				it.finish()
		},
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

private class CreatePartDataHolder(val vm: WizardActivityState): ProgressListener {

	var meta: SDUtils.SDPartitionMeta? = null
	lateinit var p: SDUtils.Partition.FreeSpace
	lateinit var l: String
	lateinit var u: String
	var f = 0L
	var t: String? = null
	var noobMode: Boolean = false

	var painter: @Composable (() -> Painter)? = null
	var rtype by mutableStateOf("")
	var cmdline by mutableStateOf("")
	var shName by mutableStateOf("")
	val dmaMeta = ArrayMap<String, String>()
	val count = mutableStateOf(0)
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
	val client = OkHttpClient().newBuilder().readTimeout(1L, TimeUnit.HOURS).addNetworkInterceptor {
		val originalResponse: Response = it.proceed(it.request())
		return@addNetworkInterceptor originalResponse.newBuilder()
			.body(ProgressResponseBody(originalResponse.body!!, this))
			.build()
	}.build()
	var pl: ProgressListener? = null
	var cl: (() -> Unit)? = null
	lateinit var t2: MutableState<String>
	val t3 = mutableStateOf("")
	var availableSize: Long = 0

	override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
		pl?.update(bytesRead, contentLength, done)
	}

	fun addDefault(i: Long, sel: Int, code: String, id: String, needUnsparse: Boolean) {
		if (idVals.contains(id)) return
		count.value++
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

	@SuppressLint("ComposableNaming")
	@Composable
	fun lateInit() {
		noobMode = LocalContext.current.getSharedPreferences("abm", 0).getBoolean("noob_mode", BuildConfig.DEFAULT_NOOB_MODE)
		meta = SDUtils.generateMeta(vm.deviceInfo!!.bdev, vm.deviceInfo.pbdev)
		(meta?.s?.find { vm.activity.intent.getLongExtra("part_sid", -1L) == it.startSector } as SDUtils.Partition.FreeSpace?)?.also { p = it }
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

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun Start(c: CreatePartDataHolder) {
	if (c.meta == null) {
		c.lateInit()
	}
	val ctx = LocalContext.current

	val s = rememberScrollState()
	var et by remember { mutableStateOf(false) }
	var el by remember { mutableStateOf(false) }
	var eu by remember { mutableStateOf(false) }
	var e by remember { mutableStateOf(false) }
	var t by remember { mutableStateOf(c.p.name) }
	var l by remember { mutableStateOf("0") }
	var u by remember { mutableStateOf(c.p.size.toString()) }
	var lu by remember { mutableStateOf(l.toFloat()..u.toFloat()) }
	Column(
		Modifier
			.fillMaxWidth()
			.verticalScroll(s)) {
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
					TextField(modifier = Modifier.fillMaxWidth(), value = l, onValueChange = {
						l = it
						el = !l.matches(Regex("\\d+"))
						e = el || eu
						if (!e) lu = l.toFloat()..u.toFloat()
					}, isError = el, label = {
						Text(stringResource(R.string.start_sector))
					})
					TextField(modifier = Modifier.fillMaxWidth(), value = u, onValueChange = {
						u = it
						eu = !u.matches(Regex("\\d+"))
						e = el || eu
						if (!e) lu = l.toFloat()..u.toFloat()
					}, isError = eu, label = {
						Text(stringResource(R.string.end_sector))
					})
					RangeSlider(modifier = Modifier.fillMaxWidth(), value = lu, onValueChange = {
						l = it.start.toLong().toString()
						u = it.endInclusive.toLong().toString()
						el = !l.matches(Regex("\\d+"))
						eu = !u.matches(Regex("\\d+"))
						e = el || eu
						if (!e) lu = l.toFloat()..u.toFloat()
					}, valueRange = 0F..c.p.size.toFloat())

					Text(stringResource(R.string.approx_size, if (!e) SOUtils.humanReadableByteCountBin((u.toLong() - l.toLong()) * c.meta!!.logicalSectorSizeBytes) else stringResource(R.string.invalid_input)))
					Text(stringResource(R.string.available_space, c.p.sizeFancy, c.p.size))
				}
			}
		}

		if (remember { ctx.getSharedPreferences("abm", 0).getBoolean("noob_mode", BuildConfig.DEFAULT_NOOB_MODE) }) {
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
				TextField(value = t, onValueChange = {
					t = it
					et = !t.matches(Regex("\\A\\p{ASCII}*\\z"))
				}, isError = et, label = {
					Text(stringResource(R.string.part_name))
				})
				Row(horizontalArrangement = Arrangement.End, modifier = Modifier
					.fillMaxWidth()
					.padding(5.dp)) {
					Button(enabled = !(e || et), onClick = {
						c.l = l
						c.u = u
						c.t = t
						c.f = c.p.size - c.u.toLong()
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
					Button(enabled = !e, onClick = {
						c.l = l
						c.u = u
						c.t = null
						c.f = c.p.size - c.u.toLong()
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
	var json: JSONObject? by remember { mutableStateOf(null) }
	var	error by remember { mutableStateOf(false) }
	val ctx = LocalContext.current
	LaunchedEffect(Unit) {
		c.run {
			Thread {
				try {
					val jsonText = try {
						ctx.assets.open("abm.json").readBytes().toString(Charsets.UTF_8)
					} catch (e: FileNotFoundException) {
						URL("https://raw.githubusercontent.com/Android-Boot-Manager/ABM-json/master/devices/" + c.vm.codename + ".json").readText()
					}
					json = JSONTokener(jsonText).nextValue() as JSONObject
					//Log.i("ABM shop:", jsonText)
				} catch (e: Exception) {
					Log.e("ABM shop", Log.getStackTraceString(e))
				}
				if (json == null) error = true
 			}.start()
		}
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
								dmaMeta["updateJson"] = o.getString("updateJson")
								rtype = o.getString("rtype")
								cmdline =
									o.getString("cmdline")
								shName = o.getString("scriptname")
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
	} else if (error) {
		Text(stringResource(R.string.shop_error))
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Os(c: CreatePartDataHolder) {
	if (c.availableSize == 0L) {
		c.availableSize = c.u.toLong() - c.l.toLong()
	}

	LaunchedEffect(Unit) {
		val a = SuFile.open("/data/abm/bootset/db/entries/").list()!!.toMutableList()
		a.removeIf { c -> !(c.startsWith("rom") && c.endsWith(".conf") && c.substring(3, c.length - 5).matches(Regex("\\d+"))) }
		a.sortWith(Comparator.comparingInt { c -> c.substring(3, c.length - 5).toInt() })
		val b = if (a.size > 0) a.last().substring(3, a.last().length - 5).toInt() + 1 else 0
		c.t2 = mutableStateOf("rom$b")
		c.t3.value = c.dmaMeta["name"]!!
	}

	val s = rememberScrollState()
	var expanded by remember { mutableStateOf(0) }
	var e by remember { mutableStateOf(false) }
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
		if (!c.noobMode)
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
		if (expanded == 1 || c.noobMode) {
			Column(
				Modifier
					.fillMaxWidth()
					.padding(5.dp)
			) {
				var et2 by remember { mutableStateOf(false) }
				var et3 by remember { mutableStateOf(false) }
				if (!c.noobMode)
					TextField(value = c.t2.value, onValueChange = {
						c.t2.value = it
						et2 = !(c.t2.value.matches(Regex("\\A\\p{ASCII}*\\z")))
						e = et2 || et3
					}, isError = et2, label = {
						Text(stringResource(R.string.internal_id))
					})
				TextField(value = c.t3.value, onValueChange = {
					c.t3.value = it
					et3 = !(c.t3.value.matches(Regex("\\A\\p{ASCII}*\\z")))
					e = et2 || et3
				}, isError = et3, label = {
					Text(stringResource(R.string.name_in_boot))
				})
			}
		}
		if (!c.noobMode)
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

				for (i in 1..c.count.value) {
					val selectedValue = remember { mutableStateOf(c.selVals.getOrElse(i-1) { 1 }) }
					val intValue = remember { mutableStateOf(c.intVals.getOrElse(i-1) { 100L }) }
					var codeValue by remember { mutableStateOf(c.codeVals.getOrElse(i-1) { "8305" }) }
					var idValue by remember { mutableStateOf(c.idVals.getOrElse(i-1) { "" }) }
					var d by remember { mutableStateOf(false) }
					var sparse by remember { mutableStateOf(c.sparseVals.getOrElse(i-1) { false }) }
					val items = listOf("bytes", "percent")
					val items2 = listOf("0700", "8302", "8305")
					val codes = listOf(stringResource(R.string.portable_part), stringResource(R.string.os_userdata), stringResource(R.string.os_system))
					val isSelectedItem: (String) -> Boolean = { items[selectedValue.value] == it }
					val onChangeState: (String) -> Unit = { selectedValue.value = items.indexOf(it) }

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
							var remaining = c.availableSize
							if (i-1 < c.selVals.size) {
								c.selVals[i - 1] = selectedValue.value
							} else {
								c.selVals.add(i - 1, selectedValue.value)
							}
							if (i-1 < c.intVals.size) {
								c.intVals[i - 1] = intValue.value
							} else {
								c.intVals.add(i - 1, intValue.value)
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

							Text(text = stringResource(R.string.sector_used, intValue.value, items[selectedValue.value], sts, remaining))
							TextField(value = intValue.value.toString(), onValueChange = {
								if (it.matches(Regex("\\d+"))) {
									intValue.value = it.toLong()
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
					Button(onClick = { c.count.value += 1 }) {
						Text("+")
					}
					Button(onClick = { c.count.value -= 1 }, enabled = (c.count.value > 1)) {
						Text("-")
					}
					var remaining = c.availableSize
					for (j in 1 .. c.count.value) {
						val k = c.intVals.getOrElse(j-1) { 0L }
						val l = c.selVals.getOrElse(j-1) { 1 }
						val sts = if (l == 0 /*bytes*/) {
							k / c.meta!!.logicalSectorSizeBytes
						} else /*percent*/ {
							(BigDecimal(remaining).multiply(BigDecimal(k).divide(BigDecimal(100)))).toLong()
						}
						remaining -= sts
					}
					Text(stringResource(R.string.remaining_sector, remaining, c.availableSize))
				}
			}
		}
	}
	c.vm.btnsOverride = true
	if (e) {
		c.vm.onNext.value = {}
		c.vm.nextText.value = ""
	} else {
		c.vm.onNext.value = { it.navigate("dload") }
		c.vm.nextText.value = stringResource(R.string.install)
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
		var downloading by remember { mutableStateOf(false) }
		var progressText by remember { mutableStateOf(c.vm.activity.getString(R.string.connecting_text)) }
		if (downloading) {
			AlertDialog(
				onDismissRequest = {},
				confirmButton = {
					Button(onClick = { c.cl?.invoke() }) {
						Text(stringResource(id = R.string.cancel))
					}
				},
				title = { Text(stringResource(R.string.downloading)) },
				text = {
					Row(verticalAlignment = Alignment.CenterVertically) {
						CircularProgressIndicator(Modifier.padding(end = 10.dp))
						Text(progressText)
					}
				})
		}
		for (i in c.idNeeded) {
			val inet = c.inetAvailable.containsKey(i)
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
				modifier = Modifier.fillMaxWidth()
			) {
				Column {
					Text(i)
					Text(
						if (c.inetDesc.containsKey(i)) c.inetDesc[i]!! else stringResource(R.string.user_selected),
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
						if (inet) {
							Button(onClick = {
								downloading = true
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
								Thread {
									try {
										val downloadedFile = File(c.vm.logic.cacheDir, i)
										val request =
											Request.Builder().url(c.inetAvailable[i]!!).build()
										val call = c.client.newCall(request)
										val response = call.execute()

										c.cl = {
											call.cancel()
											downloadedFile.delete()
											downloading = false
										}

										val sink = downloadedFile.sink().buffer()
										sink.writeAll(response.body!!.source())
										sink.close()

										if (!call.isCanceled())
											c.chosen[i] = DledFile(null, downloadedFile)
									} catch (e: Exception) {
										Log.e("ABM", Log.getStackTraceString(e))
										c.vm.activity.runOnUiThread {
											Toast.makeText(
												c.vm.activity,
												c.vm.activity.getString(R.string.dl_error),
												Toast.LENGTH_LONG
											).show()
										}
									}
									c.pl = null
									downloading = false
								}.start()
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
		c.vm.btnsOverride = true
		if (c.idNeeded.find { !c.chosen.containsKey(it) } == null) {
			c.vm.onNext.value = { it.navigate("flash") }
			c.vm.nextText.value = stringResource(id = R.string.install)
		} else {
			c.vm.onNext.value = {}
			c.vm.nextText.value = ""
		}
	}
}

@Composable
private fun Flash(c: CreatePartDataHolder) {
	val vm = c.vm
	Terminal(vm, logFile = "install_${System.currentTimeMillis()}.txt") { terminal ->
		if (c.t == null) { // OS install
			val parts = ArrayMap<Int, String>()
			val fn = c.t2.value
			val gn = c.t3.value
			terminal.add(vm.activity.getString(R.string.term_f_name, fn))
			terminal.add(vm.activity.getString(R.string.term_g_name, gn))
			terminal.add(vm.activity.getString(R.string.term_creating_pt))

			// After creating partitions:
			fun installMore() {
				terminal.add(vm.activity.getString(R.string.term_building_cfg))

				val entry = ConfigFile()
				entry["title"] = gn
				entry["linux"] = "$fn/zImage"
				entry["initrd"] = "$fn/initrd.cpio.gz"
				entry["dtb"] = "$fn/dtb.dtb"
				if (vm.deviceInfo!!.havedtbo)
					entry["dtbo"] = "$fn/dtbo.dtbo"
				entry["options"] = c.cmdline
				entry["xtype"] = c.rtype
				entry["xpart"] = parts.values.join(":")
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
					val tp = File(c.vm.deviceInfo!!.pbdev + parts[j])
					if (c.sparseVals[j]) {
						val f2 = f.toFile(c.vm)
						val result2 = Shell.cmd(
							File(
								c.vm.logic.assetDir,
								"Toolkit/simg2img"
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
				var cmd = "FORMATDATA=true " + File(
					c.vm.logic.assetDir,
					"Scripts/add_os/${c.vm.deviceInfo!!.codename}/${c.shName}"
				).absolutePath + " $fn"
				for (i in c.extraIdNeeded) {
					cmd += " " + c.chosen[i]!!.toFile(vm).absolutePath
				}
				for (i in parts) {
					cmd += " " + i.value
				}
				val result = Shell.cmd(cmd).to(terminal).exec()
				if (!result.isSuccess) {
					terminal.add(vm.activity.getString(R.string.term_failure))
					return
				}

				terminal.add(vm.activity.getString(R.string.term_success))
				vm.btnsOverride = true
				vm.nextText.value = vm.activity.getString(R.string.finish)
				vm.onNext.value = {
					it.startActivity(Intent(it, MainActivity::class.java))
					it.finish()
				}
			}

			// Fucking complicated code to fairly and flexibly partition space based on preset percentage & bytes values
			var offset = c.l.toLong()

			var makeOne: (Int) -> Unit = {}
			makeOne = {
				terminal.add(vm.activity.getString(R.string.term_create_part))

				val b = c.intVals.getOrElse(it) { 0L }
				val l = c.selVals.getOrElse(it) { 1 }
				val code = c.codeVals.getOrElse(it) { "8305" }
				val k = if (l == 0 /*bytes*/) {
					b / c.meta!!.logicalSectorSizeBytes
				} else /*percent*/ {
					(BigDecimal(c.p.size - (offset + c.f)).multiply(BigDecimal(b).divide(BigDecimal(100)))).toLong()
				}

				vm.logic.unmount(vm.deviceInfo!!)
				val r = Shell.cmd(SDUtils.umsd(c.meta!!) + " && " + c.p.create(offset, offset + k, code, "")).to(terminal).exec()
				try {
					if (r.out.join("\n").contains("kpartx")) {
						terminal.add(vm.activity.getString(R.string.term_reboot_asap))
					}
					parts[it] = c.meta!!.nid.toString()
					c.meta = SDUtils.generateMeta(c.vm.deviceInfo!!.bdev, c.vm.deviceInfo.pbdev)
					if (it + 1 < c.count.value) {
						c.p = c.meta!!.s.find { it1 -> it1.type == SDUtils.PartitionType.FREE && (offset + k) < it1.startSector } as SDUtils.Partition.FreeSpace
					}
					if (r.isSuccess) {
						terminal.add(vm.activity.getString(R.string.term_created_part))
						offset = 0L
						if (it + 1 < c.count.value) {
							makeOne(it + 1)
						} else {
							terminal.add(vm.activity.getString(R.string.term_created_pt))
							vm.logic.mount(vm.deviceInfo)
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
			val r = Shell.cmd(
				SDUtils.umsd(c.meta!!) + " && " + c.p.create(
					c.l.toLong(),
					c.u.toLong(),
					"0700",
					c.t!!
				)
			).to(terminal).exec()
			if (r.out.join("\n").contains("kpartx")) {
				terminal.add(vm.activity.getString(R.string.term_reboot_asap))
			}
			if (r.isSuccess) {
				vm.btnsOverride = true
				vm.nextText.value = c.vm.activity.getString(R.string.finish)
				vm.onNext.value = {
					it.startActivity(Intent(it, MainActivity::class.java))
					it.finish()
				}
				terminal.add(vm.activity.getString(R.string.term_success))
			} else {
				terminal.add(vm.activity.getString(R.string.term_failure))
			}
		}
	}
}

@Composable
@Preview
private fun Preview() {
	val vm = WizardActivityState("null")
	val c = CreatePartDataHolder(vm)
	AbmTheme {
		Surface(
			modifier = Modifier.fillMaxSize(),
			color = MaterialTheme.colorScheme.background
		) {
			Start(c)
		}
	}
}