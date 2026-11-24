package com.hkbuscal.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hkbuscal.app.data.calendar.CalendarRepository
import com.hkbuscal.app.data.local.SettingsRepository
import com.hkbuscal.app.data.location.LocationHelper
import com.hkbuscal.app.data.repository.TransitCommuteRepository
import java.util.concurrent.TimeUnit

/**
 * Periodically checks upcoming Android Calendar events.
 * Whenever an event starts within the user's configured lead-time window,
 * it fetches the user's live GPS location, queries KMB/LWB & Citybus real-time ETA APIs,
 * and posts/updates a rich commute notification.
 */
class CommuteMonitorWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val calendarRepo = CalendarRepository(applicationContext)
        val locationHelper = LocationHelper(applicationContext)
        val transitRepo = TransitCommuteRepository(applicationContext)
        val settings = SettingsRepository(applicationContext).getCurrentSettings()

        if (!settings.backgroundMonitorEnabled) return Result.success()
        if (!calendarRepo.hasCalendarPermission() || !locationHelper.hasLocationPermission()) {
            return Result.success()
        }

        val upcomingEvents = calendarRepo.getUpcomingEvents(hoursAhead = 4)
        val imminentEvents = upcomingEvents.filter { event ->
            event.minutesUntilStart in 0..settings.leadTimeMinutes.toLong()
        }

        if (imminentEvents.isEmpty()) return Result.success()

        val location = locationHelper.getCurrentLocation() ?: return Result.retry()

        imminentEvents.take(2).forEach { event ->
            val suggestion = transitRepo.buildCommuteSuggestionForEvent(event, location, settings)
            NotificationHelper.showCommuteNotification(
                context = applicationContext,
                suggestion = suggestion,
                useTc = settings.useTraditionalChinese
            )
        }

        return Result.success()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "hk_bus_calendar_periodic_monitor"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<CommuteMonitorWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<CommuteMonitorWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
