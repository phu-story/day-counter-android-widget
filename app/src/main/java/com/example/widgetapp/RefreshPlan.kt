package com.example.widgetapp

import java.time.ZonedDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Works out when the widgets next need redrawing, so nothing runs in between.
 *
 * A widget only needs a redraw when something it shows changes: a countdown number
 * moves to its next value, a date arrives, or the day rolls over. A widget counting
 * whole days is therefore redrawn once a day, not on a fixed timer.
 */
object RefreshPlan {
    const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /** Numbers that change faster than this are redrawn at this pace. */
    const val SHORTEST_STEP = MINUTE

    /** A progress bar moving by less than this fraction is not visible on a widget. */
    private const val PROGRESS_STEPS = 200

    /**
     * The next moment, in epoch milliseconds, at which any widget's content changes.
     * Always in the future and never later than the coming midnight.
     */
    fun nextRefresh(widgets: List<WidgetSettings>, dates: List<ImportantDate>, now: ZonedDateTime): Long {
        val nowMs = now.toInstant().toEpochMilli()
        // Midnight changes whole-day counts and what counts as "Today".
        var next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()

        for (settings in widgets) {
            for (date in settings.visible(dates)) {
                val target = date.target(now.zone).toInstant().toEpochMilli()

                // Reaching the date flips "left" to "ago" and reorders the pages.
                if (target > nowMs) next = min(next, target)

                if (settings.showCountdown) {
                    next = min(next, nextCountdownChange(settings, nowMs, target))
                }

                if (settings.showProgress) {
                    val start = date.start(now.zone).toInstant().toEpochMilli()
                    if (start > nowMs) next = min(next, start) // the bar starts filling
                    if (nowMs in start until target) {
                        next = min(next, nowMs + max((target - start) / PROGRESS_STEPS, 15 * MINUTE))
                    }
                }
            }
        }
        return max(next, nowMs + 1_000)
    }

    /** When the countdown number next changes; [Long.MAX_VALUE] if only midnight changes it. */
    private fun nextCountdownChange(settings: WidgetSettings, nowMs: Long, target: Long): Long {
        val decimals = if (settings.unit == CountdownUnit.SECONDS) 0 else settings.decimals
        val smallestStep = 10.0.pow(-decimals) // how much of one unit the last shown digit stands for

        val unitMillis = when (settings.unit) {
            // Seconds tick inside the widget while the screen is on. This once-a-minute
            // refresh is what restarts that ticking after the screen has been off.
            CountdownUnit.SECONDS -> 1_000L
            CountdownUnit.MINUTES -> MINUTE
            CountdownUnit.HOURS -> HOUR
            // Whole days follow the calendar, which the midnight refresh already covers.
            CountdownUnit.DAYS -> if (decimals == 0) return Long.MAX_VALUE else DAY
            CountdownUnit.WEEKS -> 7 * DAY
            // Months and years vary in length, so their exact change points are not
            // evenly spaced. Checking four times per step keeps them at most a quarter
            // of the last digit behind.
            CountdownUnit.MONTHS -> return nowMs + max((30.44 * DAY * smallestStep / 4).toLong(), 15 * MINUTE)
            CountdownUnit.YEARS -> return nowMs + max((365.25 * DAY * smallestStep / 4).toLong(), 15 * MINUTE)
        }
        val step = max((unitMillis * smallestStep).toLong(), SHORTEST_STEP)

        // The number shown is the time left (or passed) divided by the step, rounded
        // down, so it changes each time that distance crosses a multiple of the step.
        return if (target > nowMs) {
            nowMs + (target - nowMs) % step
        } else {
            nowMs + step - (nowMs - target) % step
        }
    }
}
