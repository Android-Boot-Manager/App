package org.andbootmgr.app

import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import java.io.File

interface DeviceInfo {
	val codename: String
	val blBlock: String
	fun isInstalled(logic: MainActivityLogic): Boolean
	fun isBooted(logic: MainActivityLogic): Boolean
	fun isCorrupt(logic: MainActivityLogic): Boolean
}

object HardcodedDeviceInfoFactory {
	private fun getYggdrasil(): DeviceInfo {
		return object : DeviceInfo {
			override val codename: String = "yggdrasil"
			override val blBlock: String = "/dev/block/by-name/lk"
			override fun isInstalled(logic: MainActivityLogic): Boolean {
				return SuFile.open(logic.abmDir, "codename.cfg").exists()
			}
			override fun isBooted(logic: MainActivityLogic): Boolean {
				val result = Shell.cmd(File(logic.assetDir, "Scripts/is_installed.sh").absolutePath).exec()
				return result.isSuccess && result.out.join("\n").contains("ABM.bootloader=1")
			}
			override fun isCorrupt(logic: MainActivityLogic): Boolean {
				return SuFile.open(logic.abmDir, "db.cofg").exists()
			}
		}
	}
	fun get(codename: String): DeviceInfo? {
		return if (codename == "yggdrasil")
			getYggdrasil()
		else
			null
	}
}