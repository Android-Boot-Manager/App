package org.androidbootmanager.app

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import kotlinx.coroutines.launch
import org.androidbootmanager.app.ui.theme.AbmTheme
import java.io.IOException

class MainActivity : ComponentActivity() {

	@OptIn(ExperimentalMaterial3Api::class)
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val vm = MainActivityState()
		vm.logic = MainActivityLogic(this)

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
			Shell.enableVerboseLogging = BuildConfig.DEBUG
			Shell.setDefaultBuilder(Shell.Builder.create()
				.setFlags(0)
				.setTimeout(30)
			)
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
								val f = SuFile.open(vm.logic.abmDir, "codename.cfg")
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
						if (vm.deviceInfo != null && vm.deviceInfo!!.isInstalled(vm.logic)) {
							vm.logic.mount(vm.deviceInfo!!)
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
fun AppContent(vm: MainActivityState, view: @Composable (PaddingValues) -> Unit) {
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
					onClick = { scope.launch {
						vm.navController!!.navigate("settings")
						drawerState.close()
					} },
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
fun NavGraph(vm: MainActivityState, it: PaddingValues) {
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
fun Start(vm: MainActivityState) {
	val installed: Boolean
	val mounted: Boolean
	if (vm.deviceInfo != null) {
		installed = remember { vm.deviceInfo!!.isInstalled(vm.logic) }
		mounted = vm.logic.mounted
	} else {
		installed = false
		mounted = false
	}
	val notOkColor = CardDefaults.cardColors(
		containerColor = Color(0xFFFF0F0F)
	)
	val okColor = CardDefaults.cardColors(
		containerColor = Color(0xFF0FFF0F)
	)
	val notOkIcon = R.drawable.ic_baseline_error_24
	val okIcon = R.drawable.ic_baseline_check_circle_24
	val okText = "Installed"
	val notOkText = "Not installed"
	val ok = remember { installed and mounted }
	Card(colors = if (ok) okColor else notOkColor, modifier = Modifier
		.fillMaxWidth()
		.padding(5.dp)) {
		Box(Modifier.padding(10.dp)) {
			Column {
				Row(Modifier.padding(5.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
					Icon(painterResource(if (ok) okIcon else notOkIcon), "", Modifier.size(48.dp))
					Text(if (ok) okText else notOkText, fontSize = 32.sp)
				}
				Text("Installed: $installed")
				Text("Mounted bootset: $mounted")
				Text("Device: ${if (vm.deviceInfo == null) "(unsupported)" else vm.deviceInfo!!.codename}")
			}
		}
	}
}

@Composable
fun Settings(vm: MainActivityState) {

}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
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