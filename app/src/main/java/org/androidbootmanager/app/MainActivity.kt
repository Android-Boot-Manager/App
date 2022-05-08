package org.androidbootmanager.app

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.launch
import org.androidbootmanager.app.ui.theme.AbmTheme
import java.io.File

class MainActivity : ComponentActivity() {

	@OptIn(ExperimentalMaterial3Api::class)
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val vm = MainActivityState()

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

@Composable
fun Start(vm: MainActivityState) {
	ClickableText(AnnotatedString((if (vm.root) "root" else "no root") + vm.name)) {
		Shell.cmd("echo a; ls").submit {
			vm.name = " S: " + it.isSuccess() + " C: " + it.getCode() + " O: " + it.out.join("\n") + " E: " + it.err.join("\n")
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
			Settings(vm)
		}
	}
}