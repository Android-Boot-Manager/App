package org.andbootmgr.app.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.andbootmgr.app.R

interface IStayAlive {
	fun startWork(work: suspend (Context) -> Unit, extra: Any)
	val workExtra: Any
}

class StayAliveService : LifecycleService(), IStayAlive {
	private lateinit var wakeLock: WakeLock
	private var work: (suspend (Context) -> Unit)? = null
	var isWorkDone = false
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
	@SuppressLint("WakelockTimeout")
	override fun startWork(work: suspend (Context) -> Unit, extra: Any) {
		if (destroyed) {
			throw IllegalStateException("This StayAliveService was leaked. It is already destroyed.")
		}
		if (this.work != null) {
			throw IllegalStateException("Work already set on this StayAliveService.")
		}
		// make sure we get promoted to started service
		startService(Intent(this, this::class.java))
		this.work = work
		this.extra = extra
		lifecycleScope.launch {
			wakeLock.acquire()
			try {
				Log.i(TAG, "Starting work...")
				this@StayAliveService.work!!.invoke(this@StayAliveService)
				Log.i(TAG, "Done working!")
				isWorkDone = true
				onDone?.invoke()
			} finally {
				wakeLock.release()
			}
		}
	}
	fun finish() {
		if (!isWorkDone) {
			Log.e(TAG, "Warning: finishing StayAliveService before work is done.")
		}
		if (!destroyed) {
			if (!isRunning) throw IllegalStateException("excepted isRunning to be true for non-destroyed service")
			isRunning = false
			destroyed = true
		}
		stopSelf()
	}

	override fun onCreate() {
		super.onCreate()
		if (isRunning) {
			throw IllegalStateException("expected isRunning=false for new service")
		}
		isRunning = true
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
		wakeLock = getSystemService(PowerManager::class.java)
			.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ABM::StayAlive(user_task)")
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
		return object : Binder(), Provider {
			override var service = this@StayAliveService
			override var onDone
				get() = this@StayAliveService.onDone
				set(value) { this@StayAliveService.onDone = value }
			override val isWorkDone: Boolean
				get() = this@StayAliveService.isWorkDone
			override fun finish() {
				this@StayAliveService.finish()
			}
		}
	}

	override fun onDestroy() {
		Log.i(TAG, "Goodbye!")
		super.onDestroy()
		if (!destroyed) {
			if (!isRunning) throw IllegalStateException("excepted isRunning to be true for non-destroyed service")
			isRunning = false
			destroyed = true
		}
	}

	companion object {
		private const val TAG = "ABM_StayAlive"
		private const val SERVICE_CHANNEL = "service"
		private const val FG_SERVICE_ID = 1001
		var isRunning = false
			private set
	}
}

private interface Provider {
	val service: IStayAlive
	var onDone: (() -> Unit)?
	val isWorkDone: Boolean
	fun finish()
}

class StayAliveConnection(inContext: Context,
                          lifecycleOwner: LifecycleOwner,
                          private val doWhenDone: (() -> Unit)?,
                          private val onConnected: (IStayAlive) -> Unit)
	: ServiceConnection, DefaultLifecycleObserver {
	companion object {
		@SuppressLint("StaticFieldLeak") // application context
		private var currentConn: StayAliveConnection? = null
	}
	private val context = inContext.applicationContext
	private var provider: Provider? = null

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
		lifecycleOwner.lifecycle.addObserver(this)
	}

	override fun onServiceConnected(name: ComponentName?, inService: IBinder?) {
		val provider = inService as Provider
		this.provider = provider
		val service = provider.service
		val onDone = {
			onServiceDisconnected(null)
			provider.finish()
			doWhenDone?.invoke(); Unit
		}
		onConnected(service)
		if (provider.isWorkDone) {
			onDone()
		} else {
			provider.onDone = onDone
		}
	}

	override fun onServiceDisconnected(name: ComponentName?) {
		context.unbindService(this)
		provider?.onDone = null
		currentConn = null
	}

	override fun onDestroy(owner: LifecycleOwner) {
		onServiceDisconnected(null)
	}
}