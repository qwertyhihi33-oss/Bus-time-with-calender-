package com.hkbuscal.app.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hkbuscal.app.MainActivity
import com.hkbuscal.app.domain.EventCommuteSuggestion
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object NotificationHelper {

    const val CHANNEL_ID = "hk_bus_commute_alerts"
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Calendar Bus ETA Alerts 巴士日程提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time KMB, LWB & Citybus arrival alerts synced with your calendar"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showCommuteNotification(
        context: Context,
        suggestion: EventCommuteSuggestion,
        useTc: Boolean = true
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        ensureChannel(context)

        val event = suggestion.event
        val startTimeStr = Instant.ofEpochMilli(event.startTimeMillis)
            .atZone(ZoneId.systemDefault())
            .format(timeFormatter)

        val title = if (useTc) {
            "📅 $startTimeStr ${event.title}（${event.minutesUntilStart} 分鐘後）"
        } else {
            "📅 $startTimeStr ${event.title} (in ${event.minutesUntilStart} min)"
        }

        val lines = suggestion.recommendedEtas.take(4).map { eta ->
            val minText = eta.etaMinutes?.let { if (it <= 0) "即將到站" else "${it}分鐘" } ?: "--"
            val distText = "${eta.distanceMeters.toInt()}m"
            if (useTc) {
                "[${eta.operator.labelTc}] ${eta.route} → ${eta.destinationTc} | ${eta.stopNameTc} ($distText) • $minText"
            } else {
                "[${eta.operator.labelEn}] ${eta.route} → ${eta.destinationEn} | ${eta.stopNameEn} ($distText) • ${eta.etaMinutes ?: "--"}m"
            }
        }

        val bigText = buildString {
            appendLine(suggestion.statusSummary)
            if (event.location.isNotBlank()) {
                appendLine(if (useTc) "地點：${event.location}" else "Location: ${event.location}")
            }
            lines.forEach { appendLine(it) }
        }.trim()

        val openAppIntent = PendingIntent.getActivity(
            context,
            event.id.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val refreshIntent = PendingIntent.getBroadcast(
            context,
            (event.id.toInt() + 10_000),
            Intent(context, RefreshEtaReceiver::class.java).apply {
                action = RefreshEtaReceiver.ACTION_REFRESH_ETA
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(title)
            .setContentText(suggestion.statusSummary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent)
            .addAction(
                android.R.drawable.ic_popup_sync,
                if (useTc) "更新實時到站" else "Refresh ETA",
                refreshIntent
            )
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(
            (event.id % Int.MAX_VALUE).toInt(),
            notification
        )
    }
}
