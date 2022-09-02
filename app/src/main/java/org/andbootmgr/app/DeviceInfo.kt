package org.andbootmgr.app

import android.annotation.SuppressLint
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import java.io.File
import java.lang.reflect.Method

interface DeviceInfo {
	val codename: String
	val blBlock: String
	val bdev: String
	val pbdev: String
	val metaonsd: Boolean
	fun isInstalled(logic: DeviceLogic): Boolean
	fun isBooted(logic: DeviceLogic): Boolean
	fun isCorrupt(logic: DeviceLogic): Boolean
}

object HardcodedDeviceInfoFactory {
	private fun getYggdrasil(): DeviceInfo {
		return object : DeviceInfo {
			override val codename: String = "yggdrasil"
			override val blBlock: String = "/dev/block/by-name/lk"
			override val bdev: String = "/dev/block/mmcblk1"
			override val pbdev: String = bdev + "p"
			override val metaonsd: Boolean = false
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

	private fun getMimameid(): DeviceInfo {
		return object : DeviceInfo {
			override val codename: String = "mimameid"
			override val blBlock: String = "/dev/block/by-name/lk"
			override val bdev: String = "/dev/block/mmcblk1"
			override val pbdev: String = bdev + "p"
			override val metaonsd: Boolean = true
			override fun isInstalled(logic: DeviceLogic): Boolean {
				return SuFile.open(bdev).exists() && run {
					val meta: SDUtils.SDPartitionMeta? =
						SDUtils.generateMeta(bdev, pbdev)
					meta?.let { (meta.countPartitions() > 0) && (meta.dumpPartition(0).type == SDUtils.PartitionType.RESERVED) } == true
				}
			}
			@SuppressLint("PrivateApi")
			override fun isBooted(logic: DeviceLogic): Boolean {
				var hasABM = false
				try {
					val c = Class.forName("android.os.SystemProperties")
					val getBoolean: Method = c.getMethod(
						"getBoolean",
						String::class.java,
						Boolean::class.javaPrimitiveType
					)
					hasABM = getBoolean.invoke(c, "ro.boot.has_dualboot", false) as Boolean
				} catch (e: Exception) {
					e.printStackTrace()
				}
				return hasABM
			}
			override fun isCorrupt(logic: DeviceLogic): Boolean {
				return !SuFile.open(logic.abmDb, "db.conf").exists()
			}
		}
	}
	fun get(codename: String): DeviceInfo? {
		return when (codename) {
			"yggdrasil" -> getYggdrasil()
			"mimameid" -> getMimameid()
			else -> null
		}
	}
}
