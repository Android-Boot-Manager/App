package org.andbootmgr.app.themes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuRandomAccessFile
import org.andbootmgr.app.join
import java.io.File
import kotlin.math.min
import kotlin.system.exitProcess


class Simulator : AppCompatActivity() {
	init {
		Log.i("Simulator","going to load library")
		System.loadLibrary("app")
	}
	external fun start(bitmap: Bitmap, w: Int, h: Int)
	external fun stop()
	external fun key(key: Int)
	private lateinit var v: View
	private lateinit var bitmap: Bitmap
	private var w: Int = 0
	private var h: Int = 0
	private lateinit var f: File
	private val handler = Handler(Looper.getMainLooper())

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		Log.i("Simulator", "welcome")
		onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				stop()
			}
		})
		f = File(intent.getStringExtra("sdCardBlock")!!)
		val l = LinearLayout(this)
		v = object : View(this) {
			private var firstTime = true
			override fun onDraw(canvas: Canvas) {
				super.onDraw(canvas)
				if (firstTime) {
					w = width
					h = height
					bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
					Thread {
						Log.i("Simulator","going to call start()")
						start(bitmap, w, h)
					}.run {
						name = "droidboot0"
						start()
					}
					firstTime = false
				}
				canvas.drawColor(Color.BLACK)
				canvas.drawBitmap(this@Simulator.bitmap, 0f, 0f, null)
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
		}
		l.addView(v, LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
		setContentView(l)
		WindowCompat.setDecorFitsSystemWindows(window, true)
		WindowInsetsControllerCompat(window, l).apply {
			systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
			hide(WindowInsetsCompat.Type.systemBars())
		}
	}

	@Suppress("unused") // jni
	private fun blockCount(): Long {
		return Shell.cmd("blockdev --getsz ${f.absolutePath}").exec().out.join("\n").toLong().also {
			Log.i("Simulator", "block count: $it")
		}
	}

	@Suppress("unused") // jni
	private fun readBlocks(offset: Long, count: Int): ByteArray {
		Log.i("Simulator", "read $count bytes at $offset")
		val fo = SuRandomAccessFile.open(f, "r")
		fo.seek(offset)
		val b = ByteArray(count)
		fo.read(b)
		fo.close()
		return b
	}

	@Suppress("unused") // jni
	private fun redraw() {
		Log.i("Simulator", "redrawing")
		v.invalidate()
	}

	@Suppress("unused") // jni
	private fun screenPrint(str: String) {
		handler.post {
			Toast.makeText(this, str.trim(), Toast.LENGTH_SHORT).show()
		}
	}

	override fun onPause() {
		stop()
		super.onPause()
	}

	override fun onStop() {
		Log.i("Simulator", "goodbye")
		super.onStop()
		// droidboot cannot cope with starting twice in same process due to static variables
		exitProcess(0)
	}

	override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
		return if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
			Log.i("Simulator", "key down: $keyCode")
			key(if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) 1 else 2)
			true
		} else {
			super.onKeyDown(keyCode, event)
		}
	}

	override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
		return if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
			Log.i("Simulator", "key up: $keyCode")
			key(if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) 8 else 16)
			true
		} else {
			super.onKeyUp(keyCode, event)
		}
	}

	override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
		when (ev?.action) {
			MotionEvent.ACTION_UP -> key(32)
			MotionEvent.ACTION_DOWN -> key(4)
			else -> return false
		}
		return true
	}
}