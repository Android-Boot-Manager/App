package org.andbootmgr.app

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuRandomAccessFile
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager
import org.andbootmgr.app.util.RootFsService
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
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
	private var firstTime = true
	private var w: Int = 0
	private var h: Int = 0
	private lateinit var f: File
	private var fs: FileSystemManager? = null
	private var fi: FileChannel? = null
	private val handler = Handler(Looper.getMainLooper())

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		Log.i("Simulator", "welcome")
		onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				stop()
			}
		})
		w = 1080 //TODO make size fullscreen and hide sysui
		h = 1920
		bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
		//f = File(intent.getStringExtra("sdCardBlock")!!)
		f = File("/dev/block/mmcblk1")
		val intent = Intent(this, RootFsService::class.java)
		val l = LinearLayout(this)
		v = object : View(this) {
			override fun onDraw(canvas: Canvas) {
				super.onDraw(canvas)
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
		WindowInsetsControllerCompat(window, l).apply {
			systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
			hide(WindowInsetsCompat.Type.systemBars())
		}
		RootService.bind(intent, object : ServiceConnection {
			override fun onServiceConnected(componentName: ComponentName?, service: IBinder?) {
				this@Simulator.fs = FileSystemManager.getRemote(service!!)
				fi = fs!!.openChannel(f, FileSystemManager.MODE_READ_ONLY)
				if (firstTime) {
					Thread {
						Log.i("Simulator","going to call start()")
						start(bitmap, w, h)
					}.run {
						name = "droidboot0"
						start()
					}
					firstTime = false
				}
			}

			override fun onServiceDisconnected(componentName: ComponentName?) {
				this@Simulator.fs = null
			}
		})
	}

	private fun blockCount(): Long {
		return Shell.cmd("blockdev --getsz ${f.absolutePath}").exec().out.join("\n").toLong().also {
			Log.i("Simulator", "block count: $it")
		}
	}

	private fun readBlocks(offset: Long, count: Int): ByteArray {
		Log.i("Simulator", "read $count bytes at $offset")
		val bb = ByteBuffer.allocate(count)
		fi!!.read(bb, offset)
		fi!!.position(0)
		return bb.array()
	}

	private fun redraw() {
		Log.i("Simulator", "redrawing")
		v.invalidate()
	}

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