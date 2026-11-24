package com.hkbuscal.app

import android.app.Application
import com.hkbuscal.app.worker.CommuteMonitorWorker
import com.hkbuscal.app.worker.NotificationHelper

class HKBusCalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        CommuteMonitorWorker.schedulePeriodic(this)
    }
}
