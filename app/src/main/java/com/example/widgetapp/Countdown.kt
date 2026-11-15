package com.example.widgetapp

import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/** What the widget prints: a number ("12.5") and a caption ("days left"). */
data class CountdownText(val value: String, val label: String) {
    val oneLine: String get() = if (label.isEmpty()) value else "$value $label"
}

object Countdown {

    fun describe(date: ImportantDate, settings: WidgetSettings, now: ZonedDateTime): CountdownText {
        val unit = settings.unit
        val today = now.toLocalDate()
        val wholeDays = unit == CountdownUnit.DAYS && settings.decimals == 0

        // An all-day event lasts the whole day, so on that day it is simply "Today".
        // Whole-day counting compares calendar dates, the way people count days.
        if ((date.isAllDay || wholeDays) && date.date == today) return CountdownText("Today", "")

        val target = date.target(now.zone)
        val past = target.isBefore(now)
        val from = if (past) target else now
        val to = if (past) now else target
        val millis = Duration.between(from, to).toMillis().toDouble()

        val decimals = if (unit == CountdownUnit.SECONDS) 0 else settings.decimals
        val amount = when (unit) {
            CountdownUnit.YEARS -> calendarAmount(from, to, ChronoUnit.YEARS)
            CountdownUnit.MONTHS -> calendarAmount(from, to, ChronoUnit.MONTHS)
            CountdownUnit.WEEKS -> millis / (7 * 86_400_000.0)
            CountdownUnit.DAYS ->
                if (wholeDays) abs(ChronoUnit.DAYS.between(today, date.date)).toDouble()
                else millis / 86_400_000.0
            CountdownUnit.HOURS -> millis / 3_600_000.0
            CountdownUnit.MINUTES -> millis / 60_000.0
            CountdownUnit.SECONDS -> millis / 1_000.0
        }

        // Truncate rather than round, so "0.99 years" never shows as "1.00" early.
        val scale = 10.0.pow(decimals)
        val shown = floor(amount * scale + 1e-9) / scale
        val value = String.format(Locale.getDefault(), "%,.${decimals}f", shown)
        val name = if (shown == 1.0) unit.singular else unit.plural
        return CountdownText(value, if (past) "$name ago" else "$name left")
    }

    /** 0 at the start date, 1 once the target is reached. */
    fun progress(date: ImportantDate, now: ZonedDateTime): Float {
        val start = date.start(now.zone).toInstant().toEpochMilli()
        val end = date.target(now.zone).toInstant().toEpochMilli()
        val current = now.toInstant().toEpochMilli()
        if (end <= start) return if (current >= end) 1f else 0f
        return ((current - start).toDouble() / (end - start)).coerceIn(0.0, 1.0).toFloat()
    }

    /** Upcoming dates first (soonest on top), then past ones (most recent first). */
    fun sorted(dates: List<ImportantDate>, now: ZonedDateTime): List<ImportantDate> {
        val today = now.toLocalDate()
        fun isOver(d: ImportantDate) = if (d.isAllDay) d.date.isBefore(today) else d.target(now.zone).isBefore(now)
        val (past, upcoming) = dates.partition(::isOver)
        return upcoming.sortedBy { it.target(now.zone) } + past.sortedByDescending { it.target(now.zone) }
    }

    /**
     * Calendar-aware years or months between two moments, with a fraction.
     * The fraction is measured against the real length of the final year or month,
     * so an anniversary is exactly 1.00 years whatever the leap years in between.
     */
    private fun calendarAmount(from: ZonedDateTime, to: ZonedDateTime, unit: ChronoUnit): Double {
        val whole = unit.between(from, to)
        val anchor = from.plus(whole, unit)
        val next = anchor.plus(1, unit)
        val partial = Duration.between(anchor, to).toMillis().toDouble()
        val full = Duration.between(anchor, next).toMillis().toDouble()
        return whole + if (full > 0) partial / full else 0.0
    }
}
