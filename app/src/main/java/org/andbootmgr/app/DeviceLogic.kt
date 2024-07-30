package org.andbootmgr.app

import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import org.andbootmgr.app.util.SDUtils
import java.io.File

class DeviceLogic(ctx: Context) {
	private val rootDir = ctx.filesDir.parentFile!!
	val assetDir = File(rootDir, "assets")
	val fileDir = File(rootDir, "files")
	val cacheDir = File(rootDir, "cache")
	val abmBootset = File(fileDir, "bootset")
	val abmDb = File(abmBootset, "db")
	val abmEntries = File(abmDb, "entries")
	var mounted = false
	fun mountBootset(d: DeviceInfo): Boolean {
		when (val code = Shell.cmd("mountpoint -q ${abmBootset.absolutePath}").exec().code) {
			0 -> {
				mounted = true
				return true
			}
			1 -> mounted = false
			else -> throw IllegalStateException("mountpoint returned exit code $code, expected 0 or 1")
		}
		val ast = d.getAbmSettings(this) ?: return false
		val result = Shell
			.cmd("mount $ast ${abmBootset.absolutePath}")
			.exec()
		if (!result.isSuccess) {
			val out = result.out.join("\n") + result.err.join("\n")
			if (out.contains("Device or resource busy")) {
				mounted = false
			}
			if (out.contains("Invalid argument")) {
				mounted = false
			}
			Log.e("ABM_MOUNT", out)
			return mounted
		}
		mounted = true
		return true
	}
	fun unmountBootset(): Boolean {
		when (val code = Shell.cmd("mountpoint -q ${abmBootset.absolutePath}").exec().code) {
			0 -> mounted = true
			1 -> {
				mounted = false
				return true
			}
			else -> throw IllegalStateException("mountpoint returned exit code $code, expected 0 or 1")
		}
		val result = Shell.cmd("umount ${abmBootset.absolutePath}").exec()
		if (!result.isSuccess) {
			val out = result.out.join("\n") + result.err.join("\n")
			if (out.contains("Device or resource busy")) {
				mounted = true
			}
			if (out.contains("Invalid argument")) {
				mounted = false
			}
			Log.e("ABM_UMOUNT", out)
			return !mounted
		}
		mounted = false
		return true
	}
	fun mount(p: SDUtils.Partition): Shell.Job {
		return Shell.cmd(p.mount())
	}
	fun unmount(p: SDUtils.Partition): Shell.Job {
		return Shell.cmd(p.unmount())
	}
	fun delete(p: SDUtils.Partition): Shell.Job {
		return Shell.cmd(SDUtils.umsd(p.meta) + " && " + p.delete())
	}
}