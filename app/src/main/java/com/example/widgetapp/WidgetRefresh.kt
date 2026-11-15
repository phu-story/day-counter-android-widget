package com.example.widgetapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps the widget's countdown current.
 *
 * Android only refreshes widgets every 30 minutes on its own, so an alarm redraws
 * them: every minute for hour/minute/second countdowns, every 15 minutes otherwise.
 * The alarm is non-wakeup, so it never wakes a sleeping phone; it simply fires as
 * soon as the phone is awake again.
 */
object WidgetRefresh {
    private const val ACTION_TICK = "com.example.widgetapp.action.TICK"
    private const val MINUTE = 60_000L

    /** Int array extra on the tick intent: ids of widgets that were just removed. */
    const val EXTRA_REMOVED_WIDGETS = "com.example.widgetapp.extra.REMOVED_WIDGETS"

    /** Changes on every refresh, so a widget that is still composed re-reads the clock. */
    val tick = MutableStateFlow(0L)

    fun tickIntent(context: Context): Intent =
        Intent(context, TickReceiver::class.java).setAction(ACTION_TICK)

    suspend fun refreshAndSchedule(context: Context) {
        tick.value = System.currentTimeMillis()
        HomeWidget().updateAll(context)
        scheduleNext(context)
    }

    private suspend fun scheduleNext(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 0, tickIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val widgetIds = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, HomeWidgetReceiver::class.java))
        if (widgetIds.isEmpty()) {
            alarms.cancel(pending)
            return
        }

        // One alarm serves every widget, so it runs as often as the fastest one needs.
        val store = Store(context)
        val fineGrained = widgetIds.any { store.settings(it).first().needsMinuteRefresh }
        val period = if (fineGrained) MINUTE else 15 * MINUTE

        // Land on a clock boundary (the minute, or :00/:15/:30/:45) so the widget
        // flips at the same moment the clock does, including at midnight.
        val next = (System.currentTimeMillis() / period + 1) * period

        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (exactAllowed) alarms.setExact(AlarmManager.RTC, next, pending)
            else alarms.set(AlarmManager.RTC, next, pending)
        } catch (_: SecurityException) {
            alarms.set(AlarmManager.RTC, next, pending)
        }
    }
}

/** Fired by the alarm above, and whenever a widget is added, removed or restored. */
class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val app = context.applicationContext
                intent.getIntArrayExtra(WidgetRefresh.EXTRA_REMOVED_WIDGETS)?.let { Store(app).forgetWidgets(it) }
                WidgetRefresh.refreshAndSchedule(app)
            } finally {
                result.finish()
            }
        }
    }
}
