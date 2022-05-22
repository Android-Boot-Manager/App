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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import java.lang.Exception

class MainActivityState {
	fun startFlow(flow: String) {
		val i = Intent(activity!!, WizardActivity::class.java)
		i.putExtra("codename", deviceInfo!!.codename)
		i.putExtra("flow", flow)
		activity!!.startActivity(i)
		activity!!.finish()
	}

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
							vm.isOk = (vm.deviceInfo!!.isInstalled(vm.logic!!) &&
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Start(vm: MainActivityState) {
	val installed: Boolean
	val booted: Boolean
	val mounted: Boolean
	val corrupt: Boolean
	if (vm.deviceInfo != null) {
		installed = remember { vm.deviceInfo!!.isInstalled(vm.logic!!) }
		booted = remember { vm.deviceInfo!!.isBooted(vm.logic!!) }
		corrupt = remember { vm.deviceInfo!!.isCorrupt(vm.logic!!) }
		mounted = vm.logic!!.mounted
	} else {
		installed = false
		booted = false
		corrupt = true
		mounted = false
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
					Text("Installed: $installed")
					Text("Activated: $booted")
					Text("Mounted bootset: $mounted")
					if (mounted) {
						Text("Bootset corrupt: $corrupt")
					}
					Text("Device: ${if (vm.deviceInfo == null) "(unsupported)" else vm.deviceInfo!!.codename}")
				}
			}
		}
		if (!installed) {
			Button(onClick = { vm.startFlow("droidboot") }) {
				Text("Install")
			}
		} else if (installed and !booted) {
			Text("The device did not boot using DroidBoot, but configuration files are present. If you just installed ABM, you need to reboot. If the bootloader installation repeatedly fails, please consult the documentation.", textAlign = TextAlign.Center)
			Button(onClick = {
				vm.startFlow("fix_droidboot")
			}) {
				Text("Repair DroidBoot")
			}
		}
		if (mounted && corrupt) {
			Text("Configuration files are not present. You can restore them from an automated backup. For more information, please consult the documentation.", textAlign = TextAlign.Center)
			Button(onClick = {
				vm.startFlow("repair_cfg")
			}) {
				Text("Repair bootset")
			}
		} else if (!mounted) {
			Text("Bootset could not be mounted, please consult the documentation.", textAlign = TextAlign.Center)
		}
	}
}

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
			Settings(vm)
		}
	}
}