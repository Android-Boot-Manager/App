package org.andbootmgr.app

import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import java.io.File

interface DeviceInfo {
	val codename: String
	val blBlock: String
	fun isInstalled(logic: DeviceLogic): Boolean
	fun isBooted(logic: DeviceLogic): Boolean
	fun isCorrupt(logic: DeviceLogic): Boolean
}

object HardcodedDeviceInfoFactory {
	private fun getYggdrasil(): DeviceInfo {
		return object : DeviceInfo {
			override val codename: String = "yggdrasil"
			override val blBlock: String = "/dev/block/by-name/lk"
			override fun isInstalled(logic: DeviceLogic): Boolean {
				return SuFile.open(logic.abmDir, "codename.cfg").exists()
			}
			override fun isBooted(logic: DeviceLogic): Boolean {
				val result = Shell.cmd(File(logic.assetDir, "Scripts/is_installed.sh").absolutePath).exec()
				return result.isSuccess && result.out.join("\n").contains("ABM.bootloader=1")
			}
			override fun isCorrupt(logic: DeviceLogic): Boolean {
				return !SuFile.open(logic.abmDb, "db.conf").exists()
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