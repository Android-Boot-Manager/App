package org.andbootmgr.app.util

import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import org.andbootmgr.app.DeviceLogic
import java.io.File

object SDLessUtils {
	fun getFreeSpaceBytes(): Long {
		return 4L * 1024L * 1024L * 1024L // TODO
	}

	fun getSpaceUsageBytes(logic: DeviceLogic, fn: String): Long? {
		return SuFile.open(logic.abmSdLessBootset, fn).listFiles()?.let {
			it.sumOf { it.length() }
		}
	}

	fun map(logic: DeviceLogic, name: String, mapFile: File, terminal: MutableList<String>? = null): Boolean {
		val dmPath = File(logic.dmBase, name)
		if (SuFile.open(dmPath.toURI()).exists())
			return true
		val tempFile = File(logic.cacheDir, "${System.currentTimeMillis()}.txt")
		if (!Shell.cmd(
				File(logic.toolkitDir, "droidboot_map_to_dm")
					.absolutePath + " " + mapFile.absolutePath + " " + tempFile.absolutePath
			).let {
				if (terminal != null)
					it.to(terminal)
				else it
			}.exec().isSuccess
		) {
			return false
		}
		return Shell.cmd("dmsetup create $name ${tempFile.absolutePath}").let {
			if (terminal != null)
				it.to(terminal)
			else it
		}.exec().isSuccess
	}

	fun unmap(logic: DeviceLogic, name: String, force: Boolean, terminal: MutableList<String>? = null): Boolean {
		val dmPath = File(logic.dmBase, name)
		if (SuFile.open(dmPath.toURI()).exists())
			return !Shell.cmd(
				"dmsetup remove " + (if (force) "-f " else "") +
						"--retry $name"
			).let {
				if (terminal != null)
					it.to(terminal)
				else it
			}.exec().isSuccess || SuFile.open(dmPath.toURI()).exists()
		return true
	}
}