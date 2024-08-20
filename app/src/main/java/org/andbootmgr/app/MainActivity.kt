package org.andbootmgr.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.Shell.FLAG_MOUNT_MASTER
import com.topjohnwu.superuser.Shell.FLAG_REDIRECT_STDERR
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.andbootmgr.app.themes.ThemeViewModel
import org.andbootmgr.app.themes.Themes
import org.andbootmgr.app.util.AbmTheme
import org.andbootmgr.app.util.ConfigFile
import org.andbootmgr.app.util.SDUtils
import org.andbootmgr.app.util.StayAliveService
import org.andbootmgr.app.util.Terminal
import java.util.concurrent.atomic.AtomicBoolean

class MainActivityState(val activity: MainActivity?) {
	var wizardCompat by mutableStateOf<String?>(null)

	fun startFlow(flow: String) {
		wizardCompat = flow
	}

	var wizardCompatSid: Long? = null
	fun startCreateFlow(freeSpace: SDUtils.Partition.FreeSpace) {
		wizardCompat = "create_part"
		wizardCompatSid = freeSpace.startSector
	}

	var wizardCompatE: String? = null
	fun startUpdateFlow(e: String) {
		wizardCompat = "update"
		wizardCompatE = e
	}

	var wizardCompatPid: Int? = null
	fun startBackupAndRestoreFlow(partition: SDUtils.Partition) {
		wizardCompat = "backup_restore"
		wizardCompatPid = partition.id
	}

	var noobMode by mutableStateOf(false)
	var deviceInfo: DeviceInfo? = null
	val theme = ThemeViewModel(this)
	var defaultCfg = mutableStateMapOf<String, String>()
	var isOk = false
	var logic: DeviceLogic? = null

	private fun loadDefaultCfg() {
		CoroutineScope(Dispatchers.IO).launch {
			val cfg = ConfigFile.importFromFile(logic!!.abmDbConf).toMap()
			withContext(Dispatchers.Main) {
				defaultCfg.clear()
				defaultCfg.putAll(cfg)
			}
		}
	}

	suspend fun editDefaultCfg(changes: Map<String, String?>) {
		val changesSafe = changes.toMutableMap() // multi threading may mean parameter is edited
		if (!logic!!.mounted) throw IllegalStateException("bootset not mounted")
		withContext(Dispatchers.Main) {
			changesSafe.forEach { (t, u) ->
				if (u != null) defaultCfg[t] = u else defaultCfg.remove(t)
			}
			try {
				val cfg = defaultCfg.toMutableMap()
				withContext(Dispatchers.IO) {
					ConfigFile(cfg).exportToFile(logic!!.abmDbConf)
				}
			} catch (e: ActionAbortedError) {
				Log.e("ABM", Log.getStackTraceString(e))
				Toast.makeText(
					activity!!,
					activity.getString(R.string.failed2save), Toast.LENGTH_LONG
				).show()
			}
		}
	}

	fun mountBootset() {
		logic!!.mountBootset(deviceInfo!!)
		loadDefaultCfg()
	}

	fun unmountBootset() {
		defaultCfg.clear()
		logic!!.unmountBootset()
	}

	fun remountBootset() {
		logic!!.unmountBootset()
		logic!!.mountBootset(deviceInfo!!)
	}
}


class MainActivity : ComponentActivity() {
	private lateinit var newFile: ActivityResultLauncher<String>
	private var onFileCreated: ((Uri) -> Unit)? = null
	private lateinit var chooseFile: ActivityResultLauncher<String>
	private var onFileChosen: ((Uri) -> Unit)? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		val ready = AtomicBoolean(false)
		installSplashScreen().setKeepOnScreenCondition { !ready.get() }
		super.onCreate(savedInstanceState)
		chooseFile =
			registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
				if (uri == null) {
					Toast.makeText(
						this,
						getString(R.string.file_unavailable),
						Toast.LENGTH_LONG
					).show()
					onFileChosen = null
					return@registerForActivityResult
				}
				if (onFileChosen != null) {
					onFileChosen!!(uri)
					onFileChosen = null
				} else {
					throw IllegalStateException("expected onFileChosen to not be null")
				}
			}
		newFile =
			registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
				if (uri == null) {
					Toast.makeText(
						this,
						getString(R.string.file_unavailable),
						Toast.LENGTH_LONG
					).show()
					onFileCreated = null
					return@registerForActivityResult
				}
				if (onFileCreated != null) {
					onFileCreated!!(uri)
					onFileCreated = null
				} else {
					throw IllegalStateException("expected onFileCreated to not be null")
				}
			}
		val vm = MainActivityState(this)
		vm.logic = DeviceLogic(this)
		CoroutineScope(Dispatchers.IO).launch {
			if (Shell.getCachedShell() == null) {
				Shell.enableVerboseLogging = BuildConfig.DEBUG
				Shell.setDefaultBuilder(
					Shell.Builder.create()
						.setFlags(FLAG_MOUNT_MASTER or FLAG_REDIRECT_STDERR)
						.setTimeout(30)
				)
			}
			launch {
				vm.noobMode =
					this@MainActivity.getSharedPreferences("abm", 0)
						.getBoolean("noob_mode", BuildConfig.DEFAULT_NOOB_MODE)
			}
			// TODO I/O on app startup is meh, but can we avoid it?
			val di = async { JsonDeviceInfoFactory(vm.activity!!).get(Build.DEVICE) }
			val shell = Shell.getShell() // blocking
			if (shell.isRoot && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				Shell.cmd("pm grant $packageName ${android.Manifest.permission.POST_NOTIFICATIONS}")
					.submit()
			}
			// == temp migration code start ==
			launch {
				if (Shell.cmd("mountpoint -q /data/abm/bootset").exec().isSuccess) {
					Shell.cmd("umount /data/abm/bootset").exec()
				}
				SuFile.open("/data/abm").let {
					if (it.exists())
						Shell.cmd("rm -rf /data/abm").exec()
				}
			}
			// == temp migration code end ==
			vm.deviceInfo = di.await() // blocking
			val installed = vm.deviceInfo?.isInstalled(vm.logic!!)
			if (installed == true) {
				vm.mountBootset()
			} else {
				Log.i("ABM", "not installed, not trying to mount")
			}
			if (vm.deviceInfo != null) {
				vm.isOk = installed!! && vm.deviceInfo!!.isBooted(vm.logic!!) &&
						!(!vm.logic!!.mounted || vm.deviceInfo!!.isCorrupt(vm.logic!!))
			}
			withContext(Dispatchers.Main) {
				setContent {
					// TODO allow rotating device while viewing logs without loosing logs (will require rememberSavable)
					if (remember { StayAliveService.isRunning }) {
						DisposableEffect(Unit) {
							window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
							onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
						}
						Terminal(null, null)
					} else if (vm.wizardCompat != null) {
						WizardCompat(vm, vm.wizardCompat!!)
					} else {
						val navController = rememberNavController()
						AppContent(vm, navController) {
							NavGraph(vm, navController, it)
						}
					}
				}
				ready.set(true)
			}
		}
	}

	fun chooseFile(mime: String, callback: (Uri) -> Unit) {
		if (onFileChosen != null) {
			throw IllegalStateException("expected onFileChosen to be null")
		}
		onFileChosen = callback
		chooseFile.launch(mime)
	}

	fun createFile(name: String, callback: (Uri) -> Unit) {
		if (onFileCreated != null) {
			throw IllegalStateException("expected onFileCreated to be null")
		}
		onFileCreated = callback
		newFile.launch(name)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(vm: MainActivityState, navController: NavHostController,
               view: @Composable (PaddingValues) -> Unit) {
	val drawerState = rememberDrawerState(DrawerValue.Closed)
	val scope = rememberCoroutineScope()
	var fabhint by remember { mutableStateOf(false) }
	val navBackStackEntry by navController.currentBackStackEntryAsState()
	val fab = @Composable {
		if (vm.noobMode && vm.isOk && navBackStackEntry!!.destination.route == "start") {
			FloatingActionButton(onClick = { fabhint = true }) {
				Icon(Icons.Default.Add, stringResource(R.string.add_icon_content_desc))
			}
		}
	}
	AbmTheme {
		// A surface container using the 'background' color from the theme
		Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
			ModalNavigationDrawer(
				drawerContent = {
					ModalDrawerSheet {
						NavigationDrawerItem(
							label = { Text(stringResource(R.string.home)) },
							selected = navBackStackEntry!!.destination.route == "start",
							onClick = {
								scope.launch {
									navController.navigate("start") {
										launchSingleTop = true
									}
									drawerState.close()
								}
							},
							modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp)
						)
						if (vm.isOk) {
							NavigationDrawerItem(
								label = { Text(stringResource(R.string.themes)) },
								selected = navBackStackEntry!!.destination.route == "themes",
								onClick = {
									scope.launch {
										navController.navigate("themes") {
											launchSingleTop = true
										}
										drawerState.close()
									}
								},
								modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp)
							)
							NavigationDrawerItem(
								label = { Text(stringResource(R.string.settings)) },
								selected = navBackStackEntry!!.destination.route == "settings",
								onClick = {
									scope.launch {
										navController.navigate("settings") {
											launchSingleTop = true
										}
										drawerState.close()
									}
								},
								modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp)
							)
						}
					}
				},
				drawerState = drawerState,
				content = {
					if (fabhint) {
						AlertDialog(onDismissRequest = { fabhint = false }, text = {
							Text(stringResource(R.string.select_free_space))
						}, confirmButton = {
							Button(onClick = { fabhint = false }) {
								Text(stringResource(R.string.ok))
							}
						})
					}
					Scaffold(
						topBar = {
							CenterAlignedTopAppBar(
								title = {
									Text(stringResource(R.string.app_name))
								},
								colors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
								navigationIcon = {
									IconButton(content = {
										Icon(
											imageVector = Icons.Filled.Menu,
											contentDescription = stringResource(R.string.menu)
										)
									}, onClick = {
										scope.launch { drawerState.open() }
									})
								})
						},
						content = view,
						floatingActionButton = fab,
						modifier = Modifier.fillMaxWidth()
					)
				}
			)
		}
	}
}

@Composable
private fun NavGraph(vm: MainActivityState, navController: NavHostController, it: PaddingValues) {
	NavHost(navController = navController, startDestination = "start", modifier = Modifier.padding(it).fillMaxSize()) {
		composable("start") {
			Start(vm)
		}
		composable("themes") {
			Themes(vm.theme)
		}
		composable("settings") {
			Settings(vm)
		}
	}
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
	val vm = MainActivityState(null)
	AppContent(vm, rememberNavController()) {
		Box(modifier = Modifier.padding(it)) {
			Start(vm)
		}
	}
}