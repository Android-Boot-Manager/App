package org.andbootmgr.app

import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
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

	companion object {
		fun importFromString(s: String): ConfigFile {
			val out = ConfigFile()
			var line: String
			for (lline in s.split("\n").toTypedArray()) {
				line = lline.trim { it <= ' ' }
				val delim = line.indexOf(" ")
				out[line.substring(0, delim)] =
					line.substring(delim).trim { it <= ' ' }
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
