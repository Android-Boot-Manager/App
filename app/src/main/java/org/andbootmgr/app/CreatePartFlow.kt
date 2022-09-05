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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import okhttp3.*
import okio.*
import org.andbootmgr.app.ui.theme.AbmTheme
import org.andbootmgr.app.util.ConfigFile
import org.andbootmgr.app.util.SOUtils
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.math.BigDecimal
import java.util.concurrent.TimeUnit


class CreatePartWizardPageFactory(private val vm: WizardActivityState) {
	fun get(): List<IWizardPage> {
		val c = CreatePartDataHolder(vm)
		return listOf(WizardPage("start",
			NavButton("Cancel") {
				it.startActivity(Intent(it, MainActivity::class.java))
				it.finish()
			},
			NavButton("") {}
		) {
			Start(c)
		}, WizardPage("shop",
			NavButton("Prev") { it.navigate("start") },
			NavButton("") {}
		) {
			Shop(c)
		}, WizardPage("os",
			NavButton("Prev") { it.navigate("shop") },
			NavButton("Next") { it.navigate("dload") }
		) {
			Os(c)
		}, WizardPage("dload",
			NavButton("Cancel") {
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
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun Start(c: CreatePartDataHolder) {
	if (c.meta == null) {
		c.lateInit()
	}

	// Material3 colors for old RangeSlider
	val i = SliderDefaults.colors()
	val sc = androidx.compose.material.SliderDefaults.colors(activeTickColor = i.tickColor(enabled = true, active = true).value, disabledActiveTickColor = i.tickColor(enabled = false, active = true).value, disabledInactiveTickColor = i.tickColor(enabled = false, active = false).value, activeTrackColor = i.trackColor(enabled = true, active = true).value, disabledActiveTrackColor = i.trackColor(enabled = false, active = true).value, disabledInactiveTrackColor = i.trackColor(enabled = false, active = false).value, disabledThumbColor = i.thumbColor(enabled = false).value, inactiveTickColor = i.tickColor(enabled = true, active = false).value, inactiveTrackColor = i.trackColor(enabled = true, active = false).value, thumbColor = i.thumbColor(enabled = true).value)

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
					Icon(painterResource(id = R.drawable.ic_settings), "Icon", Modifier.padding(end = 10.dp))
					Text("General settings")
				}
				Column(
					Modifier
						.fillMaxWidth()
						.padding(5.dp)) {
					TextField(modifier = Modifier.fillMaxWidth(), value = l, onValueChange = {
						l = it
						el = !l.matches(Regex("[0-9]+"))
						e = el || eu
						if (!e) lu = l.toFloat()..u.toFloat()
					}, isError = el, label = {
						Text("Start sector (relative)")
					})
					TextField(modifier = Modifier.fillMaxWidth(), value = u, onValueChange = {
						u = it
						eu = !u.matches(Regex("[0-9]+"))
						e = el || eu
						if (!e) lu = l.toFloat()..u.toFloat()
					}, isError = eu, label = {
						Text("End sector (relative)")
					})
					// Material3 RangeSlider is absolutely buggy trash
					androidx.compose.material.RangeSlider(modifier = Modifier.fillMaxWidth(), colors = sc, values = lu, onValueChange = {
						l = it.start.toLong().toString()
						u = it.endInclusive.toLong().toString()
						el = !l.matches(Regex("[0-9]+"))
						eu = !u.matches(Regex("[0-9]+"))
						e = el || eu
						if (!e) lu = l.toFloat()..u.toFloat()
					}, valueRange = 0F..c.p.size.toFloat())

					Text("Approximate size: " + if (!e) SOUtils.humanReadableByteCountBin((u.toLong() - l.toLong()) * c.meta!!.logicalSectorSizeBytes) else "(invalid input)")
					Text("Available space: ${c.p.sizeFancy} (${c.p.size} sectors)")
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
					Icon(painterResource(id = R.drawable.ic_sd), "Icon", Modifier.padding(end = 10.dp))
					Text("Portable partition")
				}
				TextField(value = t, onValueChange = {
					t = it
					et = !t.matches(Regex("\\A\\p{ASCII}*\\z"))
				}, isError = et, label = {
					Text("Partition name")
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
						Text("Create")
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
					Icon(painterResource(id = R.drawable.ic_droidbooticon), "Icon", Modifier.padding(end = 10.dp))
					Text("Install OS")
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
						Text("Continue")
					}
				}
			}
		}
	}
}

@Composable
private fun Shop(c: CreatePartDataHolder) {
	LaunchedEffect(Unit) {
		c.run {
			//TODO: Load from .dma
			dmaMeta["name"] = "Sailfish OS"
			dmaMeta["creator"] = "ABM Open ROM Project"
			inetAvailable["vendor"] = "https://temp.nift4.org/vendor.img"
			inetDesc["vendor"] = "VollaOS 10 vendor image"
			addDefault(838860288L, 0, "8305", "vendor", false)
			inetAvailable["system"] = "https://temp.nift4.org/system.img"
			inetDesc["system"] = "VollaOS 10 system image"
			addDefault(3758095872L, 0, "8305", "system", false)
			inetAvailable["sfos"] = "https://gitlab.com/sailfishos-porters-ci/yggdrasil-ci/-/jobs/2114113029/artifacts/raw/sfe-yggdrasil/Sailfish_OS/sailfish.img001"
			inetDesc["sfos"] = "Sailfish OS system image"
			addDefault(100L, 1, "8302", "sfos", true)
			//idUnneeded.add("sfos") not needed for sfos, but needed for ut data which cant be flashed with img
			inetAvailable["boot"] = "https://gitlab.com/sailfishos-porters-ci/yggdrasil-ci/-/jobs/2114113029/artifacts/raw/sfe-yggdrasil/Sailfish_OS/hybris-boot.img"
			inetDesc["boot"] = "Boot image"
			idNeeded.add("boot")
			painter = @Composable { painterResource(id = R.drawable.ic_sailfish_os_logo) }
			rtype = "SFOS"
			cmdline = "bootopt=64S3,32N2,64N2 androidboot.selinux=permissive audit=0 loop.max_part=7"
			shName = "add_sailfish.sh"
		}
	}
	Text("This is an placeholder Store implementation that supplies SailfishOS. To continue, press Next.") //TODO: download .dma from github or smth
	c.vm.btnsOverride = true
	c.vm.nextText.value = "Next"
	c.vm.onNext.value = { c.vm.navigate("os") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Os(c: CreatePartDataHolder) {
	if (c.availableSize == 0L) {
		c.availableSize = c.u.toLong() - c.l.toLong()
	}

	LaunchedEffect(Unit) {
		val a = SuFile.open("/data/abm/bootset/db/entries/").list()!!.toMutableList()
		a.removeIf { c -> !(c.startsWith("rom") && c.endsWith(".conf") && c.substring(3, c.length - 5).matches(Regex("[0-9]+"))) }
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
				Icon(c.painter!!(), contentDescription = "ROM logo", modifier = Modifier.size(256.dp))
				Text(c.dmaMeta["name"]!!)
				Text(c.dmaMeta["creator"]!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
				Card {
					Row(
						Modifier
							.fillMaxWidth()
							.padding(20.dp)
					) {
						Icon(painterResource(id = R.drawable.ic_about), "Icon")
						Text("You almost installed this ROM! There's only a few more steps to go. Below, you have some optional settings to further customize your install...")
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
				Text("Bootloader options")
				if (expanded == 1) {
					Icon(painterResource(id = R.drawable.ic_baseline_keyboard_arrow_up_24), "Close")
				} else {
					Icon(painterResource(id = R.drawable.ic_baseline_keyboard_arrow_down_24), "Expand")
				}
			}
		if (expanded == 1 || c.noobMode) {
			Column(
				Modifier
					.fillMaxWidth()
			) {
				var et2 by remember { mutableStateOf(false) }
				var et3 by remember { mutableStateOf(false) }
				if (!c.noobMode)
					TextField(value = c.t2.value, onValueChange = {
						c.t2.value = it
						et2 = !(c.t2.value.matches(Regex("\\A\\p{ASCII}*\\z")))
						e = et2 || et3
					}, isError = et2, label = {
						Text("ROM internal ID (don't touch if unsure)")
					})
				TextField(value = c.t3.value, onValueChange = {
					c.t3.value = it
					et3 = !(c.t3.value.matches(Regex("\\A\\p{ASCII}*\\z")))
					e = et2 || et3
				}, isError = et3, label = {
					Text("ROM name in bootmenu")
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
				Text("Partition layout (advanced users)")
				if (expanded == 2) {
					Icon(painterResource(id = R.drawable.ic_baseline_keyboard_arrow_up_24), "Close")
				} else {
					Icon(painterResource(id = R.drawable.ic_baseline_keyboard_arrow_down_24), "Expand")
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
					val codes = listOf("Portable data partition", "OS userdata",  "OS system data")
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

							Text(text = "Selected value: ${intValue.value} ${items[selectedValue.value]} (using $sts from $remaining sectors)")
							TextField(value = intValue.value.toString(), onValueChange = {
								if (it.matches(Regex("[0-9]+"))) {
									intValue.value = it.toLong()
								}
							}, label = {
								Text("Size")
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
									label = { Text("Type") },
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
								label = { Text("ID") }
							)
							Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { sparse = !sparse }) {
								Checkbox(checked = sparse, onCheckedChange = { sparse = it })
								Text("Sparsed")
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
					Text("Remaining $remaining from ${c.availableSize}")
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
		c.vm.nextText.value = "Install"
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
				Icon(painterResource(id = R.drawable.ic_about), "Icon")
				Text("Please now provide images for all required IDs. You can use the recommended ones using the \"Download\" button!")
			}
		}
		var downloading by remember { mutableStateOf(false) }
		var progressText by remember { mutableStateOf("(Connecting...)") }
		if (downloading) {
			AlertDialog(
				onDismissRequest = {},
				confirmButton = {
					Button(onClick = { c.cl?.invoke() }) {
						Text("Cancel")
					}
				},
				title = { Text("Downloading...") },
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
						if (inet) c.inetDesc[i]!! else "User-selected",
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				Column {
					if (c.chosen.containsKey(i)) {
						Button(onClick = {
							c.chosen[i]!!.delete()
							c.chosen.remove(i)
						}) {
							Text("Undo")
						}
					} else {
						if (inet) {
							Button(onClick = {
								downloading = true
								progressText = "(Connecting...)"
								c.pl = object : ProgressListener {
									override fun update(
										bytesRead: Long,
										contentLength: Long,
										done: Boolean
									) {
										progressText =
											SOUtils.humanReadableByteCountBin(bytesRead) + " of " + SOUtils.humanReadableByteCountBin(
												contentLength
											) + " downloaded"
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
												"Error downloading file :(",
												Toast.LENGTH_LONG
											).show()
										}
									}
									c.pl = null
									downloading = false
								}.start()
							}) {
								Text("Download")
							}
						}
						Button(onClick = {
							c.vm.activity.chooseFile("*/*") {
								c.chosen[i] = DledFile(it, null)
							}
						}) {
							Text("Choose")
						}
					}
				}
			}
		}
		c.vm.btnsOverride = true
		if (c.idNeeded.find { !c.chosen.containsKey(it) } == null) {
			c.vm.onNext.value = { it.navigate("flash") }
			c.vm.nextText.value = "Install"
		} else {
			c.vm.onNext.value = {}
			c.vm.nextText.value = ""
		}
	}
}

@Composable
private fun Flash(c: CreatePartDataHolder) {
	val vm = c.vm
	Terminal(vm) { terminal ->
		if (c.t == null) { // OS install
			val parts = ArrayMap<Int, String>()
			val fn = c.t2.value
			val gn = c.t3.value
			terminal.add("-- Folder name: $fn")
			terminal.add("-- GUI name: $gn")
			terminal.add("-- Creating partition layout...")

			// After creating partitions:
			fun installMore() {
				terminal.add("-- Building configuration...")

				val entry = ConfigFile()
				entry["title"] = gn
				entry["linux"] = "$fn/zImage"
				entry["initrd"] = "$fn/initrd.cpio.gz"
				entry["dtb"] = "$fn/dtb.dtb"
				entry["options"] = c.cmdline
				entry["xtype"] = c.rtype
				entry["xpart"] = parts.values.join(":")
				entry.exportToFile(File(vm.logic.abmEntries, "$fn.conf"))
				if (!SuFile.open(File(vm.logic.abmBootset, fn).toURI()).mkdir()) {
					terminal.add("-- FAILED to mkdir")
					return
				}

				terminal.add("-- Flashing images...")
				for (i in c.idVals) {
					if (c.idUnneeded.contains(i)) continue
					val j = c.idVals.indexOf(i)
					terminal.add("Flashing $i")
					val f = c.chosen[i]!!
					val tp = File(c.vm.deviceInfo!!.pbdev + parts[j])
					if (c.sparseVals[j]) {
						val f2 = f.toFile(c.vm)
						val result2 = Shell.cmd(
							File(
								c.vm.logic.assetDir,
								"Toolkit/simg2img"
							).absolutePath + " ${f2.absolutePath} ${tp.absolutePath}"
						).exec()
						if (!result2.isSuccess) {
							terminal.add("-- FAILURE!")
							return
						}
					} else {
						val f2 = f.openInputStream(c.vm)
						c.vm.copyPriv(f2, tp)
					}
					terminal.add("Done.")
				}

				terminal.add("-- Patching operating system...")
				val boot = c.chosen["boot"]!!.toFile(vm)
				var cmd = File(
					c.vm.logic.assetDir,
					"Scripts/add_os/${c.vm.deviceInfo!!.codename}/${c.shName}"
				).absolutePath + " $fn ${boot.absolutePath}"
				for (i in parts) {
					cmd += " " + i.value
				}
				val result = Shell.cmd(cmd).to(terminal).exec()
				if (!result.isSuccess) {
					terminal.add("-- FAILURE!")
					return
				}

				terminal.add("-- Done, try it out :)")
				vm.btnsOverride = true
				vm.nextText.value = "Finish"
				vm.onNext.value = {
					it.startActivity(Intent(it, MainActivity::class.java))
					it.finish()
				}
			}

			// Fucking complicated code to fairly and flexibly partition space based on preset percentage & bytes values
			var offset = c.l.toLong()

			var makeOne: (Int) -> Unit = {}
			makeOne = {
				terminal.add("Creating partition..")

				val b = c.intVals.getOrElse(it) { 0L }
				val l = c.selVals.getOrElse(it) { 1 }
				val code = c.codeVals.getOrElse(it) { "8305" }
				val k = if (l == 0 /*bytes*/) {
					b / c.meta!!.logicalSectorSizeBytes
				} else /*percent*/ {
					(BigDecimal(c.p.size - (offset + c.f)).multiply(BigDecimal(b).divide(BigDecimal(100)))).toLong()
				}

				val r = Shell.cmd(SDUtils.umsd(c.meta!!) + " && " + c.p.create(offset, offset + k, code, "")).to(terminal).exec()
				try {
					if (r.out.join("\n").contains("old")) {
						terminal.add("-- Please reboot AS SOON AS POSSIBLE!!!")
					}
					parts[it] = c.meta!!.nid.toString()
					c.meta = SDUtils.generateMeta(c.vm.deviceInfo!!.bdev, c.vm.deviceInfo.pbdev)
					if (it + 1 < c.count.value) {
						c.p = c.meta!!.s.find { it1 -> (offset + k) < it1.startSector } as SDUtils.Partition.FreeSpace
					}
					if (r.isSuccess) {
						terminal.add("Created partition.")
						offset = 0L
						if (it + 1 < c.count.value) {
							makeOne(it + 1)
						} else {
							terminal.add("-- Created partition layout.")
							installMore()
						}
					} else {
						terminal.add("-- Failure.")
					}
				} catch (e: Exception) {
					terminal.add("-- FAILURE --")
					terminal.add(Log.getStackTraceString(e))
				}
			}
			makeOne(0)
		} else { // Portable partition
			terminal.add("-- Creating partition...")
			val r = Shell.cmd(
				SDUtils.umsd(c.meta!!) + " && " + c.p.create(
					c.l.toLong(),
					c.u.toLong(),
					"0700",
					c.t!!
				)
			).to(terminal).exec()
			if (r.out.join("\n").contains("old")) {
				terminal.add("-- Please reboot AS SOON AS POSSIBLE!!!")
			}
			if (r.isSuccess) {
				vm.btnsOverride = true
				vm.nextText.value = "Finish"
				vm.onNext.value = {
					it.startActivity(Intent(it, MainActivity::class.java))
					it.finish()
				}
				terminal.add("-- Done.")
			} else {
				terminal.add("-- Failure.")
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