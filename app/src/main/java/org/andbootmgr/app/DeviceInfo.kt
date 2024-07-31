package org.andbootmgr.app

import android.annotation.SuppressLint
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import org.andbootmgr.app.util.SDUtils
import java.io.File
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
	override val metaonsd: Boolean = true
	override fun isInstalled(logic: DeviceLogic): Boolean {
		return SuFile.open(bdev).exists() && run {
			val meta = SDUtils.generateMeta(this)
			meta?.let { (meta.p.isNotEmpty()) && (meta.dumpKernelPartition(0).type == SDUtils.PartitionType.RESERVED) } == true
		}
	}
	override fun isCorrupt(logic: DeviceLogic): Boolean {
		return !SuFile.open(logic.abmDb, "db.conf").exists()
	}
	override fun getAbmSettings(logic: DeviceLogic): String? {
		if (SuFile.open(bdev).exists())
			SDUtils.generateMeta(this)?.let { meta ->
				if (meta.p.isNotEmpty()) {
					val part = meta.dumpKernelPartition(0)
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
			override val codename: String = "yggdrasil"
			override val blBlock: String = "/dev/block/by-name/lk"
			override val bdev: String = "/dev/block/mmcblk1"
			override val pbdev: String = bdev + "p"
			override val postInstallScript: Boolean = false
			override val havedtbo: Boolean = false
		}
	}

	private fun getCedric(): DeviceInfo {
		return object : MetaOnSdDeviceInfo() {
			override val codename: String = "cedric"
			override val blBlock: String = "/dev/block/bootdevice/by-name/boot"
			override val bdev: String = "/dev/block/mmcblk1"
			override val pbdev: String = bdev + "p"
			override val postInstallScript: Boolean = true
			override val havedtbo: Boolean = false
		}
	}

	private fun getMimameid(): DeviceInfo {
		return object : MetaOnSdDeviceInfo() {
			override val codename: String = "mimameid"
			override val blBlock: String = "/dev/block/by-name/lk"
			override val bdev: String = "/dev/block/mmcblk1"
			override val pbdev: String = bdev + "p"
			override val postInstallScript: Boolean = true
			override val havedtbo: Boolean = false
		}
	}

	private fun getYggdrasilx(): DeviceInfo {
		return object : MetaOnSdDeviceInfo() {
			override val codename: String = "yggdrasilx"
			override val blBlock: String = "/dev/block/by-name/lk"
			override val bdev: String = "/dev/block/mmcblk1"
			override val pbdev: String = bdev + "p"
			override val postInstallScript: Boolean = true
			override val havedtbo: Boolean = false
		}
	}

	private fun getVidofnir(): DeviceInfo {
		return object : MetaOnSdDeviceInfo() {
			override val codename: String = "vidofnir"
			override val blBlock: String = "/dev/block/by-name/lk"
			override val bdev: String = "/dev/block/mmcblk0"
			override val pbdev: String = bdev + "p"
			override val postInstallScript: Boolean = false
			override val havedtbo: Boolean = false
		}
	}

	private fun getVayu(): DeviceInfo {
		return object : MetaOnSdDeviceInfo() {
			override val codename: String = "vayu"
			override val blBlock: String = "/dev/block/by-name/boot"
			override val bdev: String = "/dev/block/mmcblk0"
			override val pbdev: String = bdev + "p"
			override val postInstallScript: Boolean = true
			override val havedtbo: Boolean = true
		}
	}

	fun get(codename: String): DeviceInfo? {
		return when (codename) {
			"yggdrasil" -> getYggdrasil()
			"yggdrasilx" -> getYggdrasilx()
			"cedric" -> getCedric()
			"mimameid" -> getMimameid()
			"vidofnir" -> getVidofnir()
			"vayu" -> getVayu()
			else -> null
		}
	}
}
