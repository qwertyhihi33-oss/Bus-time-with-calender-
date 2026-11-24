package com.hkbuscal.app.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.hkbuscal.app.domain.CalendarEventItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CalendarRepository(private val context: Context) {

    fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Queries upcoming calendar event instances starting between [now - 10 min] and [now + hoursAhead].
     * Uses CalendarContract.Instances so recurring events are properly expanded.
     */
    suspend fun getUpcomingEvents(hoursAhead: Int = 18): List<CalendarEventItem> =
        withContext(Dispatchers.IO) {
            if (!hasCalendarPermission()) return@withContext emptyList()

            val now = System.currentTimeMillis()
            val windowStart = now - 10 * 60_000L
            val windowEnd = now + hoursAhead * 3_600_000L

            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, windowStart)
            ContentUris.appendId(builder, windowEnd)

            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                CalendarContract.Instances.ALL_DAY
            )

            val results = mutableListOf<CalendarEventItem>()
            val cursor = context.contentResolver.query(
                builder.build(),
                projection,
                "${CalendarContract.Instances.ALL_DAY} = 0",
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )

            cursor?.use { c ->
                val idIdx = c.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleIdx = c.getColumnIndex(CalendarContract.Instances.TITLE)
                val locIdx = c.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val descIdx = c.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
                val beginIdx = c.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = c.getColumnIndex(CalendarContract.Instances.END)
                val calIdx = c.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)

                while (c.moveToNext()) {
                    val startMs = c.getLong(beginIdx)
                    if (startMs < windowStart) continue
                    results.add(
                        CalendarEventItem(
                            id = c.getLong(idIdx),
                            title = c.getString(titleIdx)?.takeIf { it.isNotBlank() } ?: "Untitled Event",
                            location = c.getString(locIdx).orEmpty(),
                            description = c.getString(descIdx).orEmpty(),
                            startTimeMillis = startMs,
                            endTimeMillis = c.getLong(endIdx),
                            calendarName = c.getString(calIdx).orEmpty()
                        )
                    )
                }
            }
            results
        }
}
