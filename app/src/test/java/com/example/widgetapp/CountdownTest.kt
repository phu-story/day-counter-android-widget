package com.example.widgetapp

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CountdownTest {
    private val zone = ZoneId.of("Asia/Bangkok")
    private fun at(date: String, time: String = "12:00") =
        ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone)

    private fun event(date: String, time: String? = null, start: String = "2026-01-01") = ImportantDate(
        id = date + time,
        title = "Event",
        date = LocalDate.parse(date),
        time = time?.let(LocalTime::parse),
        startDate = LocalDate.parse(start),
    )

    private fun text(e: ImportantDate, unit: CountdownUnit, decimals: Int, now: ZonedDateTime) =
        Countdown.describe(e, WidgetSettings(unit = unit, decimals = decimals), now).oneLine

    @Before
    fun fixLocale() = Locale.setDefault(Locale.US)

    @Test
    fun wholeDaysCountCalendarDays() {
        // 10 and a half days away by the clock, 11 calendar days
        assertEquals("11 days left", text(event("2026-12-25"), CountdownUnit.DAYS, 0, at("2026-12-14")))
        assertEquals("1 day left", text(event("2026-12-25"), CountdownUnit.DAYS, 0, at("2026-12-24", "23:59")))
        assertEquals("3 days ago", text(event("2026-12-25"), CountdownUnit.DAYS, 0, at("2026-12-28")))
    }

    @Test
    fun allDayEventIsTodayAllDayLong() {
        for (unit in CountdownUnit.entries) {
            assertEquals("Today", text(event("2026-12-25"), unit, 1, at("2026-12-25", "18:30")))
        }
        // A timed event later today also reads "Today" when counting whole days
        assertEquals("Today", text(event("2026-12-25", "20:00"), CountdownUnit.DAYS, 0, at("2026-12-25", "08:00")))
    }

    @Test
    fun fractionalDaysTruncate() {
        assertEquals("10.5 days left", text(event("2026-12-25"), CountdownUnit.DAYS, 1, at("2026-12-14")))
        assertEquals("1.5 weeks left", text(event("2026-12-25"), CountdownUnit.WEEKS, 1, at("2026-12-14")))
    }

    @Test
    fun yearsAndMonthsFollowTheCalendar() {
        // Exactly two years apart, across a leap day
        assertEquals("2.00 years left", text(event("2028-03-01", "12:00"), CountdownUnit.YEARS, 2, at("2026-03-01")))
        // One day short of two years
        assertEquals("1.99 years left", text(event("2028-02-29", "12:00"), CountdownUnit.YEARS, 2, at("2026-03-01")))
        assertEquals("1.50 years left", text(event("2028-01-01"), CountdownUnit.YEARS, 2, at("2026-07-02", "00:00")))
        assertEquals("3.0 months left", text(event("2026-12-14", "12:00"), CountdownUnit.MONTHS, 1, at("2026-09-14")))
        assertEquals("1.5 months left", text(event("2026-12-01"), CountdownUnit.MONTHS, 1, at("2026-10-16", "00:00")))
    }

    @Test
    fun smallUnits() {
        val e = event("2026-12-25", "09:00")
        val now = at("2026-12-24", "08:30").plusSeconds(15)
        assertEquals("24.4 hours left", text(e, CountdownUnit.HOURS, 1, now))
        assertEquals("1,469 minutes left", text(e, CountdownUnit.MINUTES, 0, now))
        // Seconds never show decimals
        assertEquals("88,185 seconds left", text(e, CountdownUnit.SECONDS, 3, now))
        assertEquals("1 second ago", text(e, CountdownUnit.SECONDS, 0, at("2026-12-25", "09:00").plusSeconds(1)))
    }

    @Test
    fun progressRunsFromStartToTarget() {
        val e = event("2026-01-11", start = "2026-01-01")
        assertEquals(0f, Countdown.progress(e, at("2025-12-31")), 0f)
        assertEquals(0.5f, Countdown.progress(e, at("2026-01-06", "00:00")), 0.0001f)
        assertEquals(1f, Countdown.progress(e, at("2026-02-01")), 0f)
        // Start on or after the target: empty until the date, full after
        assertEquals(0f, Countdown.progress(event("2026-01-11", start = "2026-03-01"), at("2026-01-05")), 0f)
        assertEquals(1f, Countdown.progress(event("2026-01-11", start = "2026-03-01"), at("2026-01-12")), 0f)
    }

    @Test
    fun upcomingFirstThenMostRecentPast() {
        val now = at("2026-06-15")
        val list = listOf(event("2026-01-01"), event("2026-12-25"), event("2026-06-15"), event("2026-05-01"), event("2026-07-01"))
        assertEquals(
            listOf("2026-06-15", "2026-07-01", "2026-12-25", "2026-05-01", "2026-01-01"),
            Countdown.sorted(list, now).map { it.date.toString() },
        )
    }
}
