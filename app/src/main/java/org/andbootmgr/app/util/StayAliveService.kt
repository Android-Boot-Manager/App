package org.andbootmgr.app.util

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

class StayAliveService : Service() {
	override fun onCreate() {
		super.onCreate()
		startForeground(FG_SERVICE_ID, notif) // TODO
	}

	override fun onBind(intent: Intent?): IBinder {
		return object : Binder(), Provider {
			override val service
				get() = this@StayAliveService
		}
	}

	interface Provider {
		val service: StayAliveService
	}
}