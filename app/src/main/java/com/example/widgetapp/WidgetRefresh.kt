package com.example.widgetapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.glance.appwidget.updateAll
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps the widgets' countdowns current while using as little battery as possible.
 *
 * - One alarm is set for the moment a widget's content next changes (see
 *   [RefreshPlan]); nothing runs in between.
 * - The alarm is non-wakeup: it never wakes a sleeping phone, and simply fires as
 *   soon as the phone is awake again.
 * - If the alarm fires while the screen is off and another one is due shortly, the
 *   redraw is skipped, since nobody can see it.
 */
object WidgetRefresh {
    private const val ACTION_TICK = "com.example.widgetapp.action.TICK"
    private const val EXTRA_FROM_ALARM = "com.example.widgetapp.extra.FROM_ALARM"

    /** Int array extra on the tick intent: ids of widgets that were just removed. */
    const val EXTRA_REMOVED_WIDGETS = "com.example.widgetapp.extra.REMOVED_WIDGETS"

    /** Changes on every refresh, so a widget that is still composed re-reads the clock. */
    val tick = MutableStateFlow(0L)

    fun tickIntent(context: Context): Intent =
        Intent(context, TickReceiver::class.java).setAction(ACTION_TICK)

    fun isFromAlarm(intent: Intent): Boolean = intent.getBooleanExtra(EXTRA_FROM_ALARM, false)

    /**
     * Redraws every widget and sets the alarm for the next change.
     *
     * [fromAlarm] marks routine timer refreshes, which may be skipped with the screen
     * off. Refreshes caused by the user (saving a date, changing a style) never are.
     */
    suspend fun refreshAndSchedule(context: Context, fromAlarm: Boolean = false) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 0, tickIntent(context).putExtra(EXTRA_FROM_ALARM, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val widgetIds = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, HomeWidgetReceiver::class.java))
        if (widgetIds.isEmpty()) {
            alarms.cancel(pending)
            return
        }

        // Set the next alarm first, so a failed redraw can never break the chain.
        val store = Store(context)
        val now = ZonedDateTime.now()
        val next = RefreshPlan.nextRefresh(
            widgets = widgetIds.map { store.settings(it).first() },
            dates = store.dates.first(),
            now = now,
        )
        setAlarm(alarms, next, pending)

        // With the screen off there is nothing to see. If another refresh is due within
        // a couple of minutes, leave the drawing to that one. Rarer refreshes (such as
        // the one at midnight) always draw, so the widget is right when the screen
        // comes back on.
        val screenOn = context.getSystemService(PowerManager::class.java).isInteractive
        val nextIsSoon = next - now.toInstant().toEpochMilli() <= 2 * RefreshPlan.MINUTE
        if (fromAlarm && !screenOn && nextIsSoon) return

        tick.value = System.currentTimeMillis()
        HomeWidget().updateAll(context)
    }

    private fun setAlarm(alarms: AlarmManager, at: Long, pending: PendingIntent) {
        // RTC (not RTC_WAKEUP): the alarm waits for the phone to be awake.
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (exactAllowed) alarms.setExact(AlarmManager.RTC, at, pending)
            else alarms.set(AlarmManager.RTC, at, pending)
        } catch (_: SecurityException) {
            alarms.set(AlarmManager.RTC, at, pending)
        }
    }
}

/**
 * Fired by the alarm above; whenever a widget is added, removed or restored; and
 * when the clock or time zone is changed, which moves every countdown.
 */
class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val app = context.applicationContext
                intent.getIntArrayExtra(WidgetRefresh.EXTRA_REMOVED_WIDGETS)?.let { Store(app).forgetWidgets(it) }
                WidgetRefresh.refreshAndSchedule(app, fromAlarm = WidgetRefresh.isFromAlarm(intent))
            } finally {
                result.finish()
            }
        }
    }
}
