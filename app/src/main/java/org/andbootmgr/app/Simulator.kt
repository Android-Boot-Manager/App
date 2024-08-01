package org.andbootmgr.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class Simulator : AppCompatActivity() {
	init {
		System.loadLibrary("app")
	}
	external fun start(bitmap: Bitmap, w: Int, h: Int)
	external fun stop()
	external fun key(key: Int)
	private lateinit var bitmap: Bitmap
	private var w: Int = 0
	private var h: Int = 0

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		w = 1080
		h = 1920
		bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
		setContentView(object : View(this) {
			override fun onDraw(canvas: Canvas) {
				super.onDraw(canvas)
				canvas.drawBitmap(this@Simulator.bitmap, 0f, 0f, null)
			}
		})
		Thread {
			start(bitmap, w, h)
		}.run {
			name = "droidboot0"
			start()
		}
	}
}