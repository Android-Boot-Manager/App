package org.andbootmgr.app.util

import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.Shell.FLAG_NON_ROOT_SHELL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*

// Manage & extract Toolkit
class Toolkit(private val ctx: Context) {
	companion object {
		private const val TAG = "ABM_AssetCopy"
		private const val DEBUG = false
	}

	val targetPath = File(ctx.cacheDir, "tools")

	suspend fun copyAssets(extractingNotification: suspend () -> Unit) {
		val shell = withContext(Dispatchers.IO) {
			Shell.Builder.create().setFlags(FLAG_NON_ROOT_SHELL).setTimeout(90).setContext(ctx).build()
		}
		var b = withContext(Dispatchers.IO) {
			ctx.assets.open("cp/_ts").use { it.readBytes() }
		}
		val s = String(b).trim()
		b = try {
			withContext(Dispatchers.IO) {
				FileInputStream(File(targetPath, "_ts")).use { it.readBytes() }
			}
		} catch (e: IOException) {
			ByteArray(0)
		}
		val s2 = String(b).trim()
		if (s != s2) {
			extractingNotification.invoke()
			withContext(Dispatchers.IO) {
				targetPath.deleteRecursively()
				if (!ctx.filesDir.exists() && !ctx.filesDir.mkdir())
					throw IOException("mkdir failed ${ctx.filesDir}")
				if (!targetPath.exists() && !targetPath.mkdir())
					throw IOException("mkdir failed $targetPath")
				if (!ctx.cacheDir.exists() && !ctx.cacheDir.mkdir())
					throw IOException("mkdir failed ${ctx.cacheDir}")
				copyAssets("Toolkit", "Toolkit")
				copyAssets("cp", "")
			}
		}
		withContext(Dispatchers.IO) {
			shell.newJob().add("chmod -R +x " + targetPath.absolutePath).exec()
		}
	}

	private fun copyAssets(src: String, outp: String) {
		for (filename in ctx.assets.list(src)!!) {
			copyAssets(src, outp, filename)
		}
	}

	private fun copyAssets(
		src: String,
		outPath: String,
		filename: String
	) {
		val `in`: InputStream
		val out: OutputStream
		try {
			`in` = ctx.assets.open("$src/$filename")
			val outFile = File(File(targetPath, outPath), filename)
			out = FileOutputStream(outFile)
			copyFile(`in`, out)
			`in`.close()
			out.close()
		} catch (e: FileNotFoundException) {
			val r = File(targetPath, outPath).mkdir()
			if (DEBUG) Log.d(TAG, "Result of mkdir #1: $r")
			if (DEBUG) Log.d(TAG, Log.getStackTraceString(e))
			try {
				ctx.assets.open(src + File.separator + filename).close()
				copyAssets(src, outPath, filename)
			} catch (e2: FileNotFoundException) {
				val r2 = File(File(targetPath, outPath), filename).mkdir()
				if (DEBUG) Log.d(TAG, "Result of mkdir #2: $r2")
				if (DEBUG) Log.d(TAG, Log.getStackTraceString(e2))
				copyAssets(src + File.separator + filename, outPath + File.separator + filename)
			}
		}
	}

	@Throws(IOException::class)
	fun copyFile(`in`: InputStream, out: OutputStream) {
		val buffer = ByteArray(1024)
		var read: Int
		while (`in`.read(buffer).also { read = it } != -1) {
			out.write(buffer, 0, read)
		}
	}
}