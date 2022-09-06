package org.andbootmgr.app.util

import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
import org.andbootmgr.app.ActionAbortedCleanlyError
import org.andbootmgr.app.ActionAbortedError
import java.io.*

class ConfigFile {
	val data: MutableMap<String, String> = HashMap()
	operator fun get(name: String): String? {
		return data[name]
	}

	operator fun set(name: String, value: String) {
		data[name] = value
	}

	fun exportToString(): String {
		val out = StringBuilder()
		for (key in data.keys) {
			out.append(key).append(" ").append(get(key)).append("\n")
		}
		return out.toString()
	}

	@Throws(ActionAbortedError::class)
	fun exportToFile(file: File) {
		try {
			val outStream = SuFileOutputStream.open(file)
			outStream.write(exportToString().toByteArray())
			outStream.close()
		} catch (e: IOException) {
			throw ActionAbortedError(e)
		}
	}

	fun has(s: String): Boolean {
		return data.containsKey(s)
	}

	companion object {
		fun importFromString(s: String): ConfigFile {
			val out = ConfigFile()
			var line: String
			for (lline in s.split("\n").toTypedArray()) {
				line = lline.trim()
				val delim = line.indexOf(" ")
				if (delim != -1)
					out[line.substring(0, delim)] =
						line.substring(delim).trim()
			}
			return out
		}

		@Throws(ActionAbortedCleanlyError::class)
		fun importFromFile(f: File): ConfigFile {
			val s = ByteArrayOutputStream()
			val b = ByteArray(1024)
			var o: Int
			val i: InputStream = try {
				SuFileInputStream.open(f)
			} catch (e: FileNotFoundException) {
				throw ActionAbortedCleanlyError(e)
			}
			while (true) {
				try {
					if (i.read(b).also { o = it } <= 1) break
					s.write(b, 0, o)
				} catch (e: IOException) {
					throw ActionAbortedCleanlyError(e)
				}
			}
			return importFromString(s.toString())
		}

		@Throws(ActionAbortedCleanlyError::class)
		fun importFromFile(s: String): ConfigFile {
			return importFromFile(File(s))
		}
	}
}
