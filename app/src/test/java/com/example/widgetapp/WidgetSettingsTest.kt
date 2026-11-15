package com.example.widgetapp

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSettingsTest {
    private fun date(id: String) = ImportantDate(
        id = id,
        title = "Date $id",
        date = LocalDate.parse("2026-12-25"),
        startDate = LocalDate.parse("2026-01-01"),
    )

    @Test
    fun settingsSurviveSaving() {
        val custom = WidgetSettings(
            dateIds = setOf("a", "c"),
            showCountdown = false,
            unit = CountdownUnit.MONTHS,
            decimals = 2,
            showProgress = true,
            font = WidgetFont.CURSIVE,
            bold = true,
            textScale = 1.3f,
            textColor = 0xFFE53935.toInt(),
            backgroundColor = 0xFF1E88E5.toInt(),
            backgroundAlpha = 0.4f,
        )
        assertEquals(custom, widgetSettingsFromJson(custom.toJson()))
    }

    @Test
    fun allDatesAndNoDatesAreDifferentThings() {
        // null = every date; an empty set = the user unticked everything
        assertEquals(null, widgetSettingsFromJson(WidgetSettings(dateIds = null).toJson()).dateIds)
        assertEquals(emptySet<String>(), widgetSettingsFromJson(WidgetSettings(dateIds = emptySet()).toJson()).dateIds)
    }

    @Test
    fun missingOrBrokenSettingsFallBackToDefaults() {
        assertEquals(WidgetSettings(), widgetSettingsFromJson(null))
        assertEquals(WidgetSettings(), widgetSettingsFromJson("not json"))
        assertEquals(WidgetSettings(bold = true), widgetSettingsFromJson("""{"bold":true,"unit":"FORTNIGHTS"}"""))
    }

    @Test
    fun widgetShowsOnlyItsChosenDates() {
        val all = listOf(date("a"), date("b"), date("c"))
        assertEquals(all, WidgetSettings().visible(all))
        assertEquals(listOf("a", "c"), WidgetSettings(dateIds = setOf("c", "a")).visible(all).map { it.id })
        // An id whose date was deleted is simply ignored
        assertEquals(listOf("b"), WidgetSettings(dateIds = setOf("b", "gone")).visible(all).map { it.id })
        assertEquals(emptyList<ImportantDate>(), WidgetSettings(dateIds = emptySet()).visible(all))
    }

    @Test
    fun datesSurviveSaving() {
        val dates = listOf(
            date("a"),
            date("b").copy(title = "Flight \"home\" ✈", time = LocalTime.of(9, 30)),
        )
        assertEquals(dates, importantDatesFromJson(dates.toJson()))
    }
}
