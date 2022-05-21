package org.andbootmgr.app

interface DeviceInfo {
	val codename: String
	fun isInstalled(logic: MainActivityLogic): Boolean
}