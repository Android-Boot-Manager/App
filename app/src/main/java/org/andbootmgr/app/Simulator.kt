package org.andbootmgr.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.nio.ExtendedFile
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Arrays
import kotlin.math.min


class Simulator : AppCompatActivity() {
	init {
		Log.i("Simulator","going to load library")
		System.loadLibrary("app")
	}
	external fun start(bitmap: Bitmap, w: Int, h: Int)
	external fun stop()
	external fun key(key: Int)
	private lateinit var bitmap: Bitmap
	private var w: Int = 0
	private var h: Int = 0
	private lateinit var f: File

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		w = 1080
		h = 1920
		bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
		f = File("/dev/block/mmcblk1")
		val l = LinearLayout(this)
		l.addView(object : View(this) {
			override fun onDraw(canvas: Canvas) {
				super.onDraw(canvas)
				canvas.drawBitmap(this@Simulator.bitmap, 0f, 0f, null)
				invalidate() //TODO
			}

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				setMeasuredDimension(when (MeasureSpec.getMode(widthMeasureSpec)) {
					MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec)
					MeasureSpec.AT_MOST -> min(Int.MAX_VALUE, MeasureSpec.getSize(widthMeasureSpec))
					else -> Int.MAX_VALUE
				}, when (MeasureSpec.getMode(heightMeasureSpec)) {
					MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
					MeasureSpec.AT_MOST -> min(Int.MAX_VALUE, MeasureSpec.getSize(heightMeasureSpec))
					else -> Int.MAX_VALUE
				})
			}
		}, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
		setContentView(l)
		Thread {
			Log.i("Simulator","going to call start()")
			start(bitmap, w, h)
		}.run {
			name = "droidboot0"
			start()
		}
	}

	private fun blockCount(): Long {
		return Shell.cmd("blockdev --getsz ${f.absolutePath}").exec().out.join("\n").toLong().also {
			Log.i("Simulator", "block count: $it")
		}
	}

	private fun readBlocks(offset: Long, count: Int): ByteArray {
		val fo = SuFileInputStream.open(f)
		fo.skipNBytesSupport(offset)
		val b = fo.readNBytesSupport(count)!!
		fo.close()
		return b
	}

	@Throws(IOException::class)
	private fun InputStream.skipNBytesSupport(nn: Long) {
		var n = nn
		while (n > 0) {
			val ns: Long = skip(n)
			if (ns in 1..n) {
				// adjust number to skip
				n -= ns
			} else if (ns == 0L) { // no bytes skipped
				// read one byte to check for EOS
				if (read() == -1) {
					throw EOFException()
				}
				// one byte read so decrement number to skip
				n--
			} else { // skipped negative or too many bytes
				throw IOException("Unable to skip exactly")
			}
		}
	}

	@Throws(IOException::class)
	fun InputStream.readNBytesSupport(len: Int): ByteArray? {
		require(len >= 0) { "len < 0" }

		var bufs: MutableList<ByteArray>? = null
		var result: ByteArray? = null
		var total = 0
		var remaining = len
		var n: Int
		do {
			var buf = ByteArray(
				min(remaining, 8192)
			)
			var nread = 0

			// read to EOF which may read more or less than buffer size
			while ((read(
					buf, nread,
					min((buf.size - nread), remaining)
				).also { n = it }) > 0
			) {
				nread += n
				remaining -= n
			}

			if (nread > 0) {
				if (8192 - total < nread) {
					throw OutOfMemoryError("Required array size too large")
				}
				if (nread < buf.size) {
					buf = Arrays.copyOfRange(buf, 0, nread)
				}
				total += nread
				if (result == null) {
					result = buf
				} else {
					if (bufs == null) {
						bufs = ArrayList()
						bufs.add(result)
					}
					bufs.add(buf)
				}
			}
			// if the last call to read returned -1 or the number of bytes
			// requested have been read then break
		} while (n >= 0 && remaining > 0)

		if (bufs == null) {
			if (result == null) {
				return ByteArray(0)
			}
			return if (result.size == total) result else result.copyOf(total)
		}

		result = ByteArray(total)
		var offset = 0
		remaining = total
		for (b in bufs) {
			val count = min(b.size.toDouble(), remaining.toDouble()).toInt()
			System.arraycopy(b, 0, result, offset, count)
			offset += count
			remaining -= count
		}

		return result
	}
}