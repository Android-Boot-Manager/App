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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.util.Supplier
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.andbootmgr.app.R

class StayAliveService : LifecycleService() {
	companion object {
		private const val TAG = "ABM_StayAlive"
		private const val SERVICE_CHANNEL = "service"
		private const val FG_SERVICE_ID = 1001
		var instance by mutableStateOf<StayAliveService?>(null)
			private set
	}
	private lateinit var wakeLock: WakeLock
	private var work: (suspend (Context) -> Unit)? = null
	var isWorkDone by mutableStateOf(false)
		private set
	private var extra: Any? = null
	val workExtra: Any
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
	fun startWork(work: suspend (Context) -> Unit, extra: Any) {
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
		if (instance != null) {
			throw IllegalStateException("expected instance to be null for non-running service")
		}
		instance = this
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
		startForeground(FG_SERVICE_ID, NotificationCompat.Builder(this, SERVICE_CHANNEL)
			.setSmallIcon(R.drawable.abm_notif)
			.setContentTitle(getString(R.string.abm_processing_title))
			.setContentText(getString(R.string.abm_processing_text))
			.setOngoing(true)
			.setOnlyAlertOnce(true)
			.setLocalOnly(true)
			.build())
		return START_NOT_STICKY
	}

	override fun onBind(intent: Intent): IBinder {
		super.onBind(intent)
		if (work != null) {
			throw IllegalStateException("Work was already set on this StayAliveService.")
		}
		return object : Binder(), Supplier<StayAliveService> {
			override fun get(): StayAliveService {
				return this@StayAliveService
			}
		}
	}

	override fun onDestroy() {
		Log.i(TAG, "Goodbye!")
		if (!isWorkDone)
			throw IllegalStateException("work isn't done but destroying?")
		super.onDestroy()
		if (!destroyed) {
			if (instance != this)
				throw IllegalStateException("excepted instance to be this for non-destroyed service")
			instance = null
			destroyed = true
		}
	}
}

class StayAliveConnection(inContext: Context,
                          private val work: suspend (Context) -> Unit,
                          private val extra: Any)
	: ServiceConnection {
	private val context = inContext.applicationContext

	init {
		context.bindService(
			Intent(context, StayAliveService::class.java),
			this,
			Context.BIND_IMPORTANT or Context.BIND_AUTO_CREATE
		)
	}

	override fun onServiceConnected(name: ComponentName?, inService: IBinder?) {
		val provider = inService as Supplier<*>
		val service = provider.get() as StayAliveService
		service.startWork(work, extra)
		context.unbindService(this)
	}

	override fun onServiceDisconnected(name: ComponentName?) {
		// do nothing
	}
}