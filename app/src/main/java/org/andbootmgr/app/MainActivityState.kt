package org.andbootmgr.app

import android.content.Intent
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineScope

class MainActivityState {
	fun startInstall() {
		val i = Intent(activity, WizardActivity::class.java)
		i.putExtra("codename", deviceInfo!!.codename)
		i.putExtra("flow", "droidboot")
		activity.startActivity(i)
		activity.finish()
	}

	lateinit var activity: MainActivity
	var deviceInfo: DeviceInfo? = null
	var currentNav: String = "start"
	var isReady = false
	var name by mutableStateOf("Android")
	var navController: NavHostController? = null
	@OptIn(ExperimentalMaterial3Api::class)
	var drawerState: DrawerState? = null
	var scope: CoroutineScope? = null
	var root = false
	lateinit var logic: MainActivityLogic
}