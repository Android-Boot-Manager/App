package org.andbootmgr.app

import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import org.andbootmgr.app.util.SDUtils
import org.andbootmgr.app.util.Toolkit
import java.io.File

class DeviceLogic(private val ctx: Context) {
	private val rootDir = ctx.filesDir.parentFile!!
	private val toolkit = Toolkit(ctx)
	val fileDir = File(rootDir, "files")
	val cacheDir = File(rootDir, "cache")
	val toolkitDir = File(toolkit.targetPath, "Toolkit") // will occasionally be pruned by OS, but it's fine
	val rootTmpDir = File("/data/local/tmp")
	val abmBootset = File(rootTmpDir, ".abm_bootset")
	val abmDb = File(abmBootset, "db")
	val abmEntries = File(abmDb, "entries")
	val abmDbConf = File(abmDb, "db.conf")
	var mounted = false
	fun mountBootset(d: DeviceInfo): Boolean {
		if (checkMounted()) return true
		val ast = d.getAbmSettings(this) ?: return false
		val bootsetSu = SuFile.open(abmBootset.toURI())
		if (!bootsetSu.exists()) bootsetSu.mkdir()
		val result = Shell
			.cmd("mount $ast ${abmBootset.absolutePath}")
			.exec()
		if (!result.isSuccess) {
			val out = result.out.joinToString("\n") + result.err.joinToString("\n")
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
		if (!checkMounted()) return true
		val result = Shell.cmd("umount ${abmBootset.absolutePath}").exec()
		if (!result.isSuccess) {
			val out = result.out.joinToString("\n") + result.err.joinToString("\n")
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
	fun checkMounted(): Boolean {
		mounted = when (val code = Shell.cmd("mountpoint -q ${abmBootset.absolutePath}").exec().code) {
			0 -> true
			1 -> false
			else -> throw IllegalStateException("mountpoint returned exit code $code, expected 0 or 1")
		}
		return mounted
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
	fun rename(p: SDUtils.Partition, name: String): Shell.Job {
		return Shell.cmd(SDUtils.umsd(p.meta) + " && " + p.rename(name))
	}
	fun create(p: SDUtils.Partition.FreeSpace, start: Long, end: Long, typeCode: String, name: String): Shell.Job {
		return Shell.cmd(SDUtils.umsd(p.meta) + " && " + p.create(start, end, typeCode, name))
	}
	fun runShFileWithArgs(cmd: String): Shell.Job {
		return Shell.cmd("export PATH=\"${toolkitDir.absolutePath}:\$PATH\" " +
				"TMPDIR=\"${cacheDir.absolutePath}\" BOOTSET=\"${abmBootset.absolutePath}\" " +
				"TK=\"${toolkitDir.absolutePath}\" && cd \"\$TK\" && $cmd")
	}
	suspend fun extractToolkit(terminal: MutableList<String>) {
		try {
			toolkit.copyAssets {
				terminal.add(ctx.getString(R.string.toolkit_extracting))
			}
		} catch (e: Exception) {
			terminal.add(ctx.getString(R.string.toolkit_error))
			throw e
		}
	}
}