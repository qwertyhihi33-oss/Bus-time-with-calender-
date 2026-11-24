package com.hkbuscal.app.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RefreshEtaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_REFRESH_ETA) {
            CommuteMonitorWorker.runNow(context)
        }
    }

    companion object {
        const val ACTION_REFRESH_ETA = "com.hkbuscal.app.ACTION_REFRESH_ETA"
    }
}
