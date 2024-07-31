package org.andbootmgr.app

import android.annotation.SuppressLint
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import org.andbootmgr.app.util.SDUtils
import java.lang.reflect.Method

interface DeviceInfo {
	val codename: String
	val blBlock: String
	val bdev: String
	val pbdev: String
	val metaonsd: Boolean
	val postInstallScript: Boolean
	val havedtbo: Boolean
	fun isInstalled(logic: DeviceLogic): Boolean
	@SuppressLint("PrivateApi")
	fun isBooted(logic: DeviceLogic): Boolean {
		try {
			val c = Class.forName("android.os.SystemProperties")
			val getBoolean: Method = c.getMethod(
				"getBoolean",
				String::class.java,
				Boolean::class.javaPrimitiveType
			)
			if (getBoolean.invoke(c, "ro.boot.has_dualboot", false) as Boolean
					|| getBoolean.invoke(c, "ro.boot.hasdualboot", false) as Boolean)
				return true
		} catch (e: Exception) {
			e.printStackTrace()
		}
		val result = Shell.cmd("grep ABM.bootloader=1 /proc/cmdline").exec()
		return result.isSuccess && result.out.join("\n").contains("ABM.bootloader=1")
	}
	fun isCorrupt(logic: DeviceLogic): Boolean
	fun getAbmSettings(logic: DeviceLogic): String?
}

abstract class MetaOnSdDeviceInfo : DeviceInfo {
	override val metaonsd = true
	override fun isInstalled(logic: DeviceLogic): Boolean {
		return SuFile.open(bdev).exists() && SDUtils.generateMeta(this)?.let { meta ->
			meta.p.isNotEmpty() && meta.dumpKernelPartition(1).type == SDUtils.PartitionType.RESERVED
		} == true
	}
	override fun isCorrupt(logic: DeviceLogic): Boolean {
		return !SuFile.open(logic.abmDb, "db.conf").exists()
	}
	override fun getAbmSettings(logic: DeviceLogic): String? {
		if (SuFile.open(bdev).exists())
			SDUtils.generateMeta(this)?.let { meta ->
				if (meta.p.isNotEmpty()) {
					val part = meta.dumpKernelPartition(1)
					if (part.type == SDUtils.PartitionType.RESERVED)
						return part.path
				}
			}
		return null
	}
}

object HardcodedDeviceInfoFactory {
	private fun getYggdrasil(): DeviceInfo {
		return object : MetaOnSdDeviceInfo() {
			override val codename = "yggdrasil"
			override val blBlock = "/dev/block/by-name/lk"
			override val bdev = "/dev/block/mmcblk1"
			override val pbdev = bdev + "p"
			override val postInstallScript = false
			override val havedtbo = false
		}
	}

	private fun getMimameid(): DeviceInfo {
		return object : MetaOnSdDeviceInfo() {
			override val codename = "mimameid"
			override val blBlock = "/dev/block/by-name/lk"
			override val bdev = "/dev/block/mmcblk1"
			override val pbdev = bdev + "p"
			override val postInstallScript = true
			override val havedtbo = false
		}
	}

	private fun getYggdrasilx(): DeviceInfo {
		return object : MetaOnSdDeviceInfo() {
			override val codename = "yggdrasilx"
			override val blBlock = "/dev/block/by-name/lk"
			override val bdev = "/dev/block/mmcblk1"
			override val pbdev = bdev + "p"
			override val postInstallScript = true
			override val havedtbo = false
		}
	}

	private fun getVidofnir(): DeviceInfo {
		return object : MetaOnSdDeviceInfo() {
			override val codename = "vidofnir"
			override val blBlock = "/dev/block/by-name/lk"
			override val bdev = "/dev/block/mmcblk0"
			override val pbdev = bdev + "p"
			override val postInstallScript = false
			override val havedtbo = false
		}
	}

	private fun getVayu(): DeviceInfo {
		return object : MetaOnSdDeviceInfo() {
			override val codename = "vayu"
			override val blBlock = "/dev/block/by-name/boot"
			override val bdev = "/dev/block/mmcblk0"
			override val pbdev = bdev + "p"
			override val postInstallScript = true
			override val havedtbo = true
		}
	}

	fun get(codename: String): DeviceInfo? {
		return when (codename) {
			"yggdrasil" -> getYggdrasil()
			"yggdrasilx" -> getYggdrasilx()
			"mimameid" -> getMimameid()
			"vidofnir" -> getVidofnir()
			"vayu" -> getVayu()
			else -> null
		}
	}
}
