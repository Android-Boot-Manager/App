package org.andbootmgr.app

import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.nio.ExtendedFile
import java.io.File
import java.io.IOException
import kotlin.math.abs

open class ActionAbortedError(e: Exception?) : Exception(e)
class ActionAbortedCleanlyError(e: Exception?) : ActionAbortedError(e)

@Throws(IOException::class)
private fun generateFile(prefix: String, suffix: String, dir: File?): File {
	var n = (Math.random() * Long.MAX_VALUE).toLong()
	n = if (n == Long.MIN_VALUE) {
		0 // corner case
	} else {
		abs(n.toDouble()).toLong()
	}
	val name = prefix + n.toString() + suffix
	val f = File(dir, name)
	if (name != f.name)
		throw IOException("Unable to create temporary file, $f")

	return f
}

@Throws(IOException::class)
fun createTempFileSu(
	prefix: String, suffix: String?,
	directory: File?
): ExtendedFile {
	require(prefix.length >= 3) { "Prefix string too short" }

	val tmpdir = if ((directory != null)) directory else File(System.getProperty("java.io.tmpdir", ".")!!)
	var f: File
	do {
		f = generateFile(prefix, suffix ?: ".tmp", tmpdir)
	} while (f.exists())
	val ff = SuFile.open(f.toURI())

	if (!ff.createNewFile()) throw IOException("Unable to create temporary file")

	return ff
}