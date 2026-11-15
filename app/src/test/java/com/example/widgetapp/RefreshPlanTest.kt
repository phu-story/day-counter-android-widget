package com.example.widgetapp

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshPlanTest {
    private val zone = ZoneId.of("Asia/Bangkok")
    private fun at(date: String, time: String) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone)

    private fun event(date: String, time: String? = null, start: String = "2026-01-01") = ImportantDate(
        id = date + time,
        title = "Event",
        date = LocalDate.parse(date),
        time = time?.let(LocalTime::parse),
        startDate = LocalDate.parse(start),
    )

    /** The planned refresh, as a local date-time string for easy reading. */
    private fun next(now: ZonedDateTime, dates: List<ImportantDate>, vararg widgets: WidgetSettings): String {
        val millis = RefreshPlan.nextRefresh(widgets.toList(), dates, now)
        return java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime().toString()
    }

    private val christmas = listOf(event("2026-12-25"))
    private val christmasMorning = listOf(event("2026-12-25", "09:00"))

    @Test
    fun wholeDaysOnlyRefreshAtMidnight() {
        assertEquals("2026-11-16T00:00", next(at("2026-11-15", "08:30"), christmas, WidgetSettings()))
    }

    @Test
    fun titleOnlyRefreshesAtMidnight() {
        val titleOnly = WidgetSettings(showCountdown = false, unit = CountdownUnit.MINUTES)
        assertEquals("2026-11-16T00:00", next(at("2026-11-15", "08:30"), christmas, titleOnly))
    }

    @Test
    fun minutesRefreshExactlyWhenTheNumberChanges() {
        val minutes = WidgetSettings(unit = CountdownUnit.MINUTES)
        assertEquals("2026-12-25T08:31", next(at("2026-12-25", "08:30:15"), christmasMorning, minutes))
        // After the date has passed it counts up, still on the minute
        assertEquals("2026-12-25T09:01", next(at("2026-12-25", "09:00:20"), christmasMorning, minutes))
        // A date at an odd second changes at that second of each minute
        val odd = listOf(event("2026-12-25", "09:00:40"))
        assertEquals("2026-12-25T08:30:40", next(at("2026-12-25", "08:30:15"), odd, minutes))
    }

    @Test
    fun hoursFollowTheirDecimals() {
        val now = at("2026-12-25", "06:10")
        val morning = listOf(event("2026-12-25", "09:30"))
        // Whole hours left change at half past
        assertEquals("2026-12-25T06:30", next(now, morning, WidgetSettings(unit = CountdownUnit.HOURS)))
        // One decimal changes every six minutes
        assertEquals("2026-12-25T06:12", next(now, morning, WidgetSettings(unit = CountdownUnit.HOURS, decimals = 1)))
    }

    @Test
    fun fastChangingNumbersAreCappedAtOncePerMinute() {
        val now = at("2026-12-25", "08:30:15")
        assertEquals("2026-12-25T08:31", next(now, christmasMorning, WidgetSettings(unit = CountdownUnit.HOURS, decimals = 3)))
    }

    @Test
    fun secondsWidgetsAreRestartedOnceAMinute() {
        val seconds = WidgetSettings(unit = CountdownUnit.SECONDS)
        assertEquals("2026-12-25T08:31", next(at("2026-12-25", "08:30:15"), christmasMorning, seconds))
    }

    @Test
    fun arrivingAtTheDateTriggersARefresh() {
        val evening = listOf(event("2026-11-15", "18:00"))
        assertEquals("2026-11-15T18:00", next(at("2026-11-15", "12:00"), evening, WidgetSettings()))
    }

    @Test
    fun slowUnitsWaitForMidnight() {
        val now = at("2026-11-15", "08:30")
        assertEquals("2026-11-16T00:00", next(now, christmas, WidgetSettings(unit = CountdownUnit.YEARS, decimals = 2)))
        assertEquals("2026-11-16T00:00", next(now, christmas, WidgetSettings(unit = CountdownUnit.MONTHS, decimals = 1)))
        assertEquals("2026-11-16T00:00", next(now, christmas, WidgetSettings(unit = CountdownUnit.WEEKS)))
    }

    @Test
    fun progressBarRefreshesOnlyWhenItVisiblyMoves() {
        val bar = WidgetSettings(showCountdown = false, showProgress = true)
        // 10 days in 200 steps = one step every 72 minutes
        val tenDays = listOf(event("2026-01-11", start = "2026-01-01"))
        assertEquals("2026-01-05T13:12", next(at("2026-01-05", "12:00"), tenDays, bar))
        // A year-long bar moves too slowly to matter before midnight
        val year = listOf(event("2027-01-01", start = "2026-01-01"))
        assertEquals("2026-06-02T00:00", next(at("2026-06-01", "12:00"), year, bar))
    }

    @Test
    fun theBusiestWidgetSetsThePace() {
        val now = at("2026-12-25", "08:30:15")
        val days = WidgetSettings()
        val minutes = WidgetSettings(unit = CountdownUnit.MINUTES)
        assertEquals("2026-12-25T08:31", next(now, christmasMorning, days, minutes))
        // A minutes widget that shows none of the dates costs nothing
        assertEquals("2026-12-25T09:00", next(now, christmasMorning, days, minutes.copy(dateIds = emptySet())))
    }

    @Test
    fun theRefreshIsAlwaysInTheFuture() {
        val minutes = WidgetSettings(unit = CountdownUnit.MINUTES)
        // Exactly on a change point: come back a second later, not immediately
        assertEquals("2026-12-25T08:30:01", next(at("2026-12-25", "08:30"), christmasMorning, minutes))
    }
}
