package org.androidbootmanager.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.ScaffoldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.rememberScaffoldState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.androidbootmanager.app.ui.theme.AbmTheme
import kotlin.random.Random

class MainActivity : ComponentActivity() {
	@OptIn(ExperimentalMaterial3Api::class)
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val vm = MainActivityState()
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
			NavigationDrawer(
					drawerState = drawerState,
					drawerContent = {
						Button(
								modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp),
								onClick = { scope.launch { drawerState.close() } },
								content = { Text("Close Drawer") }
						)
					},
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
		composable("start") { Start(vm) }
		composable("settings") { Settings(vm) }
	}
}

@Composable
fun Start(vm: MainActivityState) {
	Greeting(vm)
}

@Composable
fun Settings(vm: MainActivityState) {
	Text("hi")
}

@Composable
fun Greeting(vm: MainActivityState) {
	ClickableText(text = AnnotatedString("Hello ${vm.name}!"), onClick = {
		vm.name = Random.nextLong().toString()
	})
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