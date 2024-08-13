package org.andbootmgr.app.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.andbootmgr.app.R

interface IStayAlive {
	fun startWork(work: suspend (Context) -> Unit, extra: Any)
	val isWorkDone: Boolean
	val workExtra: Any
	fun finish()
}

class StayAliveService : LifecycleService(), IStayAlive {
	private var work: (suspend (Context) -> Unit)? = null
	override var isWorkDone = false
		get() {
			if (destroyed) {
				throw IllegalStateException("This StayAliveService was leaked. It is already destroyed.")
			}
			return field
		}
	private var extra: Any? = null
	override val workExtra: Any
		get() {
			if (destroyed) {
				throw IllegalStateException("This StayAliveService was leaked. It is already destroyed.")
			}
			if (work == null) {
				throw IllegalStateException("Tried to access work extra before work was set.")
			}
			return extra!!
		}
	private var destroyed = false
	private var onDone: (() -> Unit)? = null
	override fun startWork(work: suspend (Context) -> Unit, extra: Any) {
		if (destroyed) {
			throw IllegalStateException("This StayAliveService was leaked. It is already destroyed.")
		}
		if (this.work != null) {
			throw IllegalStateException("Work already set on this StayAliveService.")
		}
		this.work = work
		this.extra = extra
		lifecycleScope.launch {
			this@StayAliveService.work!!.invoke(this@StayAliveService)
			isWorkDone = true
			onDone!!.invoke()
		}
	}
	override fun finish() {
		destroyed = true
		stopSelf()
	}

	override fun onCreate() {
		super.onCreate()
		NotificationManagerCompat.from(this).createNotificationChannel(
			NotificationChannelCompat.Builder(SERVICE_CHANNEL,
				NotificationManagerCompat.IMPORTANCE_HIGH)
				.setName(getString(R.string.service_notifications))
				.setShowBadge(true)
				.setLightsEnabled(false)
				.setVibrationEnabled(false)
				.setSound(null, null)
				.build()
		)
		startForeground(FG_SERVICE_ID, NotificationCompat.Builder(this, SERVICE_CHANNEL)
			.setSmallIcon(R.drawable.abm_notif)
			.setContentTitle(getString(R.string.abm_processing_title))
			.setContentText(getString(R.string.abm_processing_text))
			.setOngoing(true)
			.setOnlyAlertOnce(true)
			.setLocalOnly(true)
			.build())
		lifecycleScope.launch {
			delay(10000)
			// If there was nothing started after 10 seconds, there's a bug.
			if (work == null) {
				throw IllegalStateException("No work was submitted to StayAliveService after 10 seconds.")
			}
		}
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		super.onStartCommand(intent, flags, startId)
		return START_NOT_STICKY
	}

	override fun onBind(intent: Intent): IBinder {
		super.onBind(intent)
		if (work != null) {
			throw IllegalStateException("Work was already set on this StayAliveService.")
		}
		// make sure we get promoted to started service
		startService(Intent(this, this::class.java))
		return object : Binder(), Provider {
			override var service = this@StayAliveService
			override var onDone
				get() = this@StayAliveService.onDone!!
				set(value) { this@StayAliveService.onDone = value }
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		destroyed = true
	}

	private interface Provider {
		val service: IStayAlive
		var onDone: () -> Unit
	}

	companion object {
		private const val SERVICE_CHANNEL = "service"
		private const val FG_SERVICE_ID = 1001
		@SuppressLint("StaticFieldLeak") // application context
		private var currentConn: StayAliveConnection? = null
	}

	class StayAliveConnection(inContext: Context,
	                          private val onConnected: (IStayAlive) -> Unit) : ServiceConnection {
		private val context = inContext.applicationContext
		private var service: IStayAlive? = null

		init {
			if (currentConn != null) {
				throw IllegalStateException("There should only be one StayAliveConnection at a time.")
			}
			currentConn = this
			context.bindService(
				Intent(context, StayAliveService::class.java),
				this,
				Context.BIND_IMPORTANT or Context.BIND_AUTO_CREATE
			)
		}

		override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
			val provider = service as Provider
			provider.onDone = {
				context.unbindService(this)
				currentConn = null
			}
			onConnected(provider.service)
		}

		override fun onServiceDisconnected(name: ComponentName?) {
			this.service = null
		}
	}
}