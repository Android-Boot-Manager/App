package org.andbootmgr.app

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.andbootmgr.app.util.SDUtils
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.FileNotFoundException
import java.lang.reflect.Method
import java.net.URL

interface DeviceInfo {
	val codename: String
	val blBlock: String
	val metaonsd: Boolean
	val realEntryHasKernel: Boolean
	/* Environment variables:
	 * - BOOTED=true SETUP=false BL_BACKUP=<unset> for droidboot update
	 * - BOOTED=false SETUP=false BL_BACKUP=<path> for droidboot fix
	 * - BOOTED=false SETUP=true BL_BACKUP=<path> for droidboot install + sd creation
	 * - BOOTED=true SETUP=true BL_BACKUP=<unset> for sd creation with already installed droidboot
	 */
	val postInstallScript: Boolean
	val havedtbo: Boolean
	fun isInstalled(logic: DeviceLogic): Boolean
	@SuppressLint("PrivateApi")
	fun isBooted(logic: DeviceLogic): Boolean {
		// TODO migrate all modern BL to one of these three variants
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
		return result.isSuccess && result.out.joinToString("\n").contains("ABM.bootloader=1")
	}
	fun isCorrupt(logic: DeviceLogic): Boolean
	fun getAbmSettings(logic: DeviceLogic): String?
}

fun DeviceInfo.asMetaOnSdDeviceInfo() = this as MetaOnSdDeviceInfo

abstract class MetaOnSdDeviceInfo : DeviceInfo {
	abstract val bdev: String
	abstract val pbdev: String
	override val metaonsd = true
	override fun isInstalled(logic: DeviceLogic): Boolean {
		return SuFile.open(bdev).exists() && SDUtils.generateMeta(this)?.let { meta ->
			meta.p.find { it.id == 1 && it.type == SDUtils.PartitionType.RESERVED } != null
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

abstract class SdLessDeviceInfo : DeviceInfo {
	override val metaonsd = false
	override fun isInstalled(logic: DeviceLogic): Boolean {
		return SuFile.open(logic.abmSdLessBootsetImg.toURI()).exists()
	}
	override fun isCorrupt(logic: DeviceLogic): Boolean {
		return !SuFile.open(logic.abmDb, "db.conf").exists()
	}
	override fun getAbmSettings(logic: DeviceLogic): String? {
		return logic.dmPath.absolutePath
	}
}

class JsonMetaOnSdDeviceInfo(
	override val codename: String,
	override val blBlock: String,
	override val bdev: String,
	override val pbdev: String,
	override val postInstallScript: Boolean,
	override val havedtbo: Boolean,
	override val realEntryHasKernel: Boolean
) : MetaOnSdDeviceInfo()

class JsonSdLessDeviceInfo(
	override val codename: String,
	override val blBlock: String,
	override val postInstallScript: Boolean,
	override val havedtbo: Boolean,
	override val realEntryHasKernel: Boolean
) : SdLessDeviceInfo()

class JsonDeviceInfoFactory(private val ctx: Context) {
	suspend fun get(codename: String): DeviceInfo? {
		return try {
			withContext(Dispatchers.IO) {
				var fromNet = true
				val jsonText = try {
					try {
						ctx.assets.open("abm.json").readBytes().toString(Charsets.UTF_8)
					} catch (e: FileNotFoundException) {
						URL("https://raw.githubusercontent.com/Android-Boot-Manager/ABM-json/master/devices/$codename.json").readText()
					}
				} catch (e: Exception) {
					fromNet = false
					Log.e("ABM device info", Log.getStackTraceString(e))
					val f = File(ctx.filesDir, "abm_dd_cache.json")
					if (f.exists()) f.readText() else
						ctx.assets.open("abm_fallback/$codename.json").readBytes()
							.toString(Charsets.UTF_8)
				}
				val jsonRoot = JSONTokener(jsonText).nextValue() as JSONObject? ?: return@withContext null
				val json = jsonRoot.getJSONObject("deviceInfo")
				if (BuildConfig.VERSION_CODE < jsonRoot.getInt("minAppVersion"))
					throw IllegalStateException("please upgrade app")
				if (fromNet) {
					launch {
						val newRoot = JSONObject()
						newRoot.put("deviceInfo", json)
						newRoot.put("minAppVersion", jsonRoot.getInt("minAppVersion"))
						File(ctx.filesDir, "abm_dd_cache.json").writeText(newRoot.toString())
					}
				}
				if (json.getBoolean("metaOnSd")) {
					JsonMetaOnSdDeviceInfo(
						json.getString("codename"),
						json.getString("blBlock"),
						json.getString("sdBlock"),
						json.getString("sdBlockP"),
						json.getBoolean("postInstallScript"),
						json.getBoolean("haveDtbo"),
						json.optBoolean("realEntryHasKernel", false)
					)
				} else {
					JsonSdLessDeviceInfo(
						json.getString("codename"),
						json.getString("blBlock"),
						json.getBoolean("postInstallScript"),
						json.getBoolean("haveDtbo"),
						json.optBoolean("realEntryHasKernel", false)
					)
				}
			}
		} catch (e: Exception) {
			Log.e("ABM device info", Log.getStackTraceString(e))
			null
		}
	}
}