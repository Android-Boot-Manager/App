package org.andbootmgr.app

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.topjohnwu.superuser.Shell
import org.andbootmgr.app.ui.theme.AbmTheme
import org.andbootmgr.app.util.SOUtils
import java.math.BigDecimal

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
		}, WizardPage("os",
			NavButton("Prev") {
				it.navigate("start")
			},
			NavButton("") {}
		) {
			Os(c)
		}, WizardPage("flash",
			NavButton("") {},
			NavButton("") {}
		) {
			Flash(c)
		})
	}
}

private class CreatePartDataHolder(val vm: WizardActivityState) {
	var meta: SDUtils.SDPartitionMeta? = null
	lateinit var p: SDUtils.Partition.FreeSpace

	lateinit var l: String
	lateinit var u: String
	var t: String? = null

	fun lateInit() {
		meta = SDUtils.generateMeta(vm.deviceInfo!!.bdev, vm.deviceInfo.pbdev)
		(meta?.s?.find { vm.activity.intent.getLongExtra("part_sid", -1L) == it.startSector } as SDUtils.Partition.FreeSpace?)?.also { p = it }
	}
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun Start(c: CreatePartDataHolder) {
	remember {
		c.lateInit()
		return@remember 0 // sorry but LaunchedEffect is not what I need, I need remember without value
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
						c.vm.navigate("os")
					}) {
						Text("Continue")
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Os(c: CreatePartDataHolder) {
	// Custom OS page only for now
	val count = remember { mutableStateOf(1) }
	val intVals = remember { mutableStateListOf<Long>() }
	val selVals = remember { mutableStateListOf<Int>() }
	val availableSize = c.u.toLong() - c.l.toLong()

	val vm = c.vm
	val s = rememberScrollState()
	Column(
		Modifier
			.fillMaxSize()
			.verticalScroll(s)) {

		for (i in 1..count.value) {
			val selectedValue = remember { mutableStateOf("percent") }
			val intValue = remember { mutableStateOf("100") }
			val intValid = remember { mutableStateOf(true) }

			val isSelectedItem: (String) -> Boolean = { selectedValue.value == it }
			val onChangeState: (String) -> Unit = { selectedValue.value = it }

			val items = listOf("bytes", "percent")
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
					if (intValid.value) {
						var sts: Long = -1
						var remaining = availableSize
						if (i-1 < selVals.size) {
							selVals[i - 1] = items.indexOf(selectedValue.value)
						} else {
							selVals.add(i - 1, items.indexOf(selectedValue.value))
						}
						if (i-1 < intVals.size) {
							intVals[i - 1] = intValue.value.toLong()
						} else {
							intVals.add(i - 1, intValue.value.toLong())
						}
						for (j in 1 .. i) {
							val k = intVals.getOrElse(j-1) { 0L }
							val l = selVals.getOrElse(j-1) { 1 }
							sts = if (l == 0 /*bytes*/) {
								k / c.meta!!.logicalSectorSizeBytes
							} else /*percent*/ {
								(BigDecimal(remaining).multiply(BigDecimal(k).divide(BigDecimal(100)))).toLong()
							}
							remaining -= sts
						}
						remaining += sts

						Text(text = "Selected value: ${intValue.value} ${selectedValue.value} (using $sts from $remaining sectors)")
					} else {
						Text(text = "Selected value: (invalid value)")
					}
					TextField(value = intValue.value, onValueChange = {
						intValue.value = it
						intValid.value = intValue.value.matches(Regex("[0-9]+"))
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
				}
			}
		}
		Row(verticalAlignment = Alignment.CenterVertically) {
			Button(onClick = { count.value += 1 }) {
				Text("+")
			}
			Button(onClick = { count.value -= 1 }, enabled = (count.value > 1)) {
				Text("-")
			}
			var remaining = availableSize
			for (j in 1 .. count.value) {
				val k = intVals.getOrElse(j-1) { 0L }
				val l = selVals.getOrElse(j-1) { 1 }
				val sts = if (l == 0 /*bytes*/) {
					k / c.meta!!.logicalSectorSizeBytes
				} else /*percent*/ {
					(BigDecimal(remaining).multiply(BigDecimal(k).divide(BigDecimal(100)))).toLong()
				}
				remaining -= sts
			}
			Text("Remaining $remaining from $availableSize")
		}
	}
}

@Composable
private fun Flash(c: CreatePartDataHolder) {
	val vm = c.vm
	Terminal(vm) { terminal ->
		if (c.t == null) { // OS install
			//TODO
		} else { // Portable partition
			terminal.add("-- Creating partition...")
			Shell.cmd(SDUtils.umsd(c.meta!!) + " && " + c.p.create(c.l.toLong(), c.u.toLong(), "0700", c.t!!)).to(terminal).submit {
				if (it.out.join("\n").contains("old")) {
					terminal.add("-- Please reboot AS SOON AS POSSIBLE!!!")
				}
				if (it.isSuccess) {
					terminal.add("-- Done.")
				} else {
					terminal.add("-- Failure.")
				}
				vm.activity.runOnUiThread {
					vm.btnsOverride = true
					vm.nextText.value = "Finish"
					vm.onNext.value = {
						it.startActivity(Intent(it, MainActivity::class.java))
						it.finish()
					}
				}
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