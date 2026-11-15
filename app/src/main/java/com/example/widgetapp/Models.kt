package com.example.widgetapp

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** One entry in the list of important dates. */
data class ImportantDate(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val date: LocalDate,
    /** Null means an all-day event. */
    val time: LocalTime? = null,
    /** Where the progress bar starts from (0%). */
    val startDate: LocalDate = LocalDate.now(),
) {
    val isAllDay: Boolean get() = time == null

    fun target(zone: ZoneId): ZonedDateTime = date.atTime(time ?: LocalTime.MIDNIGHT).atZone(zone)

    fun start(zone: ZoneId): ZonedDateTime = startDate.atStartOfDay(zone)
}

enum class CountdownUnit(val label: String, val singular: String, val plural: String) {
    YEARS("Years", "year", "years"),
    MONTHS("Months", "month", "months"),
    WEEKS("Weeks", "week", "weeks"),
    DAYS("Days", "day", "days"),
    HOURS("Hours", "hour", "hours"),
    MINUTES("Minutes", "minute", "minutes"),
    SECONDS("Seconds", "second", "seconds"),
}

enum class WidgetFont(val label: String) {
    SANS("Sans"),
    SERIF("Serif"),
    MONOSPACE("Mono"),
    CURSIVE("Cursive"),
}

/** How one home screen widget looks and which dates it shows. Each widget has its own. */
data class WidgetSettings(
    /** Ids of the dates this widget shows. Null means all of them, including future ones. */
    val dateIds: Set<String>? = null,
    val showCountdown: Boolean = true,
    val unit: CountdownUnit = CountdownUnit.DAYS,
    /** Decimal places on the countdown number, 0 to 3. */
    val decimals: Int = 0,
    val showProgress: Boolean = false,
    val font: WidgetFont = WidgetFont.SANS,
    val bold: Boolean = false,
    val textScale: Float = 1f,
    /** Opaque ARGB. */
    val textColor: Int = 0xFFFFFFFF.toInt(),
    /** Opaque ARGB; transparency comes from [backgroundAlpha]. */
    val backgroundColor: Int = 0xFF1C1B1F.toInt(),
    /** 0 = fully transparent, 1 = solid. */
    val backgroundAlpha: Float = 1f,
) {
    /** True when the widget redraws every second: a seconds countdown is showing. */
    val isLive: Boolean get() = showCountdown && unit == CountdownUnit.SECONDS

    /** The dates this widget shows, out of everything saved. */
    fun visible(dates: List<ImportantDate>): List<ImportantDate> =
        if (dateIds == null) dates else dates.filter { it.id in dateIds }
}

fun WidgetSettings.toJson(): String {
    val obj = JSONObject()
        .put("showCountdown", showCountdown)
        .put("unit", unit.name)
        .put("decimals", decimals)
        .put("showProgress", showProgress)
        .put("font", font.name)
        .put("bold", bold)
        .put("textScale", textScale.toDouble())
        .put("textColor", textColor)
        .put("backgroundColor", backgroundColor)
        .put("backgroundAlpha", backgroundAlpha.toDouble())
    if (dateIds != null) obj.put("dateIds", JSONArray(dateIds))
    return obj.toString()
}

/** Anything missing or unreadable falls back to the default. */
fun widgetSettingsFromJson(json: String?): WidgetSettings {
    val defaults = WidgetSettings()
    if (json.isNullOrEmpty()) return defaults
    return runCatching {
        val obj = JSONObject(json)
        WidgetSettings(
            dateIds = obj.optJSONArray("dateIds")?.let { ids ->
                (0 until ids.length()).map(ids::getString).toSet()
            },
            showCountdown = obj.optBoolean("showCountdown", defaults.showCountdown),
            unit = CountdownUnit.entries.firstOrNull { it.name == obj.optString("unit") } ?: defaults.unit,
            decimals = obj.optInt("decimals", defaults.decimals).coerceIn(0, 3),
            showProgress = obj.optBoolean("showProgress", defaults.showProgress),
            font = WidgetFont.entries.firstOrNull { it.name == obj.optString("font") } ?: defaults.font,
            bold = obj.optBoolean("bold", defaults.bold),
            textScale = obj.optDouble("textScale", defaults.textScale.toDouble()).toFloat(),
            textColor = obj.optInt("textColor", defaults.textColor),
            backgroundColor = obj.optInt("backgroundColor", defaults.backgroundColor),
            backgroundAlpha = obj.optDouble("backgroundAlpha", defaults.backgroundAlpha.toDouble())
                .toFloat().coerceIn(0f, 1f),
        )
    }.getOrDefault(defaults)
}

fun List<ImportantDate>.toJson(): String {
    val array = JSONArray()
    for (item in this) {
        val obj = JSONObject()
            .put("id", item.id)
            .put("title", item.title)
            .put("date", item.date.toString())
            .put("start", item.startDate.toString())
        if (item.time != null) obj.put("time", item.time.toString())
        array.put(obj)
    }
    return array.toString()
}

fun importantDatesFromJson(json: String?): List<ImportantDate> {
    if (json.isNullOrEmpty()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            ImportantDate(
                id = obj.getString("id"),
                title = obj.getString("title"),
                date = LocalDate.parse(obj.getString("date")),
                time = if (obj.has("time")) LocalTime.parse(obj.getString("time")) else null,
                startDate = LocalDate.parse(obj.getString("start")),
            )
        }
    }.getOrDefault(emptyList())
}
