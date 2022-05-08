package org.androidbootmanager.app

import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope

class MainActivityState {
	var currentNav: String = "start"
	var isReady = false
	var name by mutableStateOf("Android")
	var navController: NavHostController? = null
	@OptIn(ExperimentalMaterial3Api::class)
	var drawerState: DrawerState? = null
	var scope: CoroutineScope? = null
	var root = false
}