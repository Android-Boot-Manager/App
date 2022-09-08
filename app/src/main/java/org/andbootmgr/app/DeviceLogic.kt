package org.andbootmgr.app

import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import java.io.File

class DeviceLogic(ctx: Context) {
	val rootDir = ctx.filesDir.parentFile!!
	val assetDir = File(rootDir, "assets")
	val fileDir = File(rootDir, "files")
	val cacheDir = File(rootDir, "cache")
	val abmDir = File("/data/abm/")
	val abmBootset = File(abmDir, "bootset")
	val abmDb = File(abmBootset, "db")
	val abmEntries = File(abmDb, "entries")
	var mounted = false
	fun mount(d: DeviceInfo): Boolean {
		if (mounted)
			return true
		if (SuFile.open(abmDb.toURI()).exists()) {
			mounted = true
			return true
		}
		val result: Shell.Result = Shell
			.cmd(assetDir.absolutePath + "/Scripts/config/mount/" + d.codename + ".sh")
			.exec()
		if (!result.isSuccess) {
			val out = result.out.join("\n")
			val err = result.err.join("\n")
			if (err.contains("Device or resource busy")) {
				mounted = false
			}
			if (err.contains("Invalid argument")) {
				mounted = false
			}
			Log.e("ABM_MOUNT_out", out)
			Log.e("ABM_MOUNT_err", err)
			return mounted
		}
		mounted = true
		return true
	}
	fun unmount(d: DeviceInfo): Boolean {
		//if (!mounted)
			//return true
		val result: Shell.Result = Shell
			.cmd(assetDir.absolutePath + "/Scripts/config/umount/" + d.codename + ".sh")
			.exec()
		if (!result.isSuccess) {
			val out = result.out.join("\n")
			val err = result.err.join("\n")
			if (err.contains("Device or resource busy")) {
				mounted = true
			}
			if (err.contains("Invalid argument")) {
				mounted = false
			}
			Log.e("ABM_UMOUNT_out", out)
			Log.e("ABM_UMOUNT_err", err)
			return !mounted
		}
		mounted = false
		return true
	}
}