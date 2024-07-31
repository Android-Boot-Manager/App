package org.andbootmgr.app.util

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.Shell.FLAG_NON_ROOT_SHELL
import java.io.*
import java.util.function.Consumer

// Manage & extract Toolkit
class Toolkit(private val ctx: Context) {
	private var fail = false
	private val targetPath = File(ctx.filesDir.parentFile, "assets")

	fun copyAssets(uinf: Runnable, callback: Consumer<Boolean>) {
		val shell = Shell.Builder.create().setFlags(FLAG_NON_ROOT_SHELL).setTimeout(30).setContext(ctx).build()
		var input: InputStream
		var b: ByteArray
		try {
			input = ctx.assets.open("cp/_ts")
			b = input.readBytes()
			input.close()
		} catch (e: IOException) {
			e.printStackTrace()
			fail = true
			callback.accept(true)
			return
		}
		val s = String(b).trim()
		try {
			input = FileInputStream(File(targetPath, "_ts"))
			b = input.readBytes()
			input.close()
		} catch (e: IOException) {
			b = ByteArray(0)
		}
		val s2 = String(b).trim()
		if (s != s2) {
			uinf.run()
			shell.newJob().add("rm -rf " + targetPath.absolutePath).exec()
			if (!targetPath.exists()) fail = fail or !targetPath.mkdir()
			if (!File(ctx.filesDir.parentFile, "files").exists()) fail = fail or !File(ctx.filesDir.parentFile, "files").mkdir()
			if (!File(ctx.filesDir.parentFile, "cache").exists()) fail = fail or !File(ctx.filesDir.parentFile, "cache").mkdir()
			copyAssets("Toolkit", "Toolkit")
			copyAssets("Scripts", "Scripts")
			copyAssets("cp", "")
		}
		shell.newJob().add("chmod -R +x " + targetPath.absolutePath).exec()
		callback.accept(fail)
	}

	private fun copyAssets(src: String, outp: String) {
		val assetManager: AssetManager = ctx.assets
		var files: Array<String>? = null
		try {
			files = assetManager.list(src)
		} catch (e: IOException) {
			Log.e("ABM_AssetCopy", "Failed to get asset file list.", e)
			fail = true
		}
		assert(files != null)
		for (filename in files!!) {
			copyAssets(src, outp, assetManager, filename)
		}
	}

	private fun copyAssets(
		src: String,
		outp: String,
		assetManager: AssetManager,
		filename: String
	) {
		val `in`: InputStream
		val out: OutputStream
		try {
			`in` = assetManager.open("$src/$filename")
			val outFile = File(File(targetPath, outp), filename)
			out = FileOutputStream(outFile)
			copyFile(`in`, out)
			`in`.close()
			out.flush()
			out.close()
		} catch (e: FileNotFoundException) {
			Log.d(
				"ABM_AssetCopy",
				"Result of mkdir #1: " + File(targetPath, outp).mkdir()
			)
			Log.d("ABM_AssetCopy", Log.getStackTraceString(e))
			try {
				assetManager.open(src + File.separator + filename).close()
				copyAssets(src, outp, assetManager, filename)
			} catch (e2: FileNotFoundException) {
				Log.d(
					"ABM_AssetCopy",
					"Result of mkdir #2: " + File(File(targetPath, outp), filename).mkdir()
				)
				Log.d("ABM_AssetCopy", Log.getStackTraceString(e2))
				copyAssets(src + File.separator + filename, outp + File.separator + filename)
			} catch (ex: IOException) {
				Log.e("ABM_AssetCopy", "Failed to copy asset file: $filename", ex)
				fail = true
			}
		} catch (e: IOException) {
			Log.e("ABM_AssetCopy", "Failed to copy asset file: $filename", e)
			fail = true
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