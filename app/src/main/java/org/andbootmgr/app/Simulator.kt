package org.andbootmgr.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
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

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		w = 1080
		h = 1920
		bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
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
}