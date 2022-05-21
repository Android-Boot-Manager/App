package org.andbootmgr.app

import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import java.io.File

object HardcodedDeviceInfoFactory {
	private fun getYggdrasil(): DeviceInfo {
		return object : DeviceInfo {
			override val codename: String = "yggdrasil"
			override fun isInstalled(logic: MainActivityLogic): Boolean {
				val result = Shell.cmd(File(logic.assetDir, "Scripts/is_installed.sh").absolutePath).exec()
				return result.isSuccess && result.out.join("\n").contains("ABM.bootloader=1") && SuFile.open(logic.abmDir, "codename.cfg").exists()
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