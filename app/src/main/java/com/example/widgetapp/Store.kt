package com.example.widgetapp

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "day_counter")

private val DATES = stringPreferencesKey("dates")

/** Each widget's settings live under their own key, named after the widget's id. */
private fun widgetKey(widgetId: Int) = stringPreferencesKey("widget_$widgetId")

/**
 * A widget's saved settings. One that was never customized starts from the style
 * saved by the first version of the app, which had a single style for all widgets.
 */
private fun Preferences.settingsOf(widgetId: Int): WidgetSettings =
    this[widgetKey(widgetId)]?.let(::widgetSettingsFromJson) ?: sharedStyleFromFirstVersion()

private fun Preferences.sharedStyleFromFirstVersion(): WidgetSettings {
    val defaults = WidgetSettings()
    return WidgetSettings(
        showCountdown = this[booleanPreferencesKey("show_countdown")] ?: defaults.showCountdown,
        unit = CountdownUnit.entries.firstOrNull { it.name == this[stringPreferencesKey("unit")] } ?: defaults.unit,
        decimals = (this[intPreferencesKey("decimals")] ?: defaults.decimals).coerceIn(0, 3),
        showProgress = this[booleanPreferencesKey("show_progress")] ?: defaults.showProgress,
        font = WidgetFont.entries.firstOrNull { it.name == this[stringPreferencesKey("font")] } ?: defaults.font,
        bold = this[booleanPreferencesKey("bold")] ?: defaults.bold,
        textScale = this[floatPreferencesKey("text_scale")] ?: defaults.textScale,
        textColor = this[intPreferencesKey("text_color")] ?: defaults.textColor,
        backgroundColor = this[intPreferencesKey("bg_color")] ?: defaults.backgroundColor,
        backgroundAlpha = (this[floatPreferencesKey("bg_alpha")] ?: defaults.backgroundAlpha).coerceIn(0f, 1f),
    )
}

/** Saved dates and per-widget settings. Every write also refreshes the widgets. */
class Store(context: Context) {
    private val context = context.applicationContext

    val dates: Flow<List<ImportantDate>> = this.context.dataStore.data
        .map { importantDatesFromJson(it[DATES]) }
        .distinctUntilChanged()

    /** Settings of one widget. */
    fun settings(widgetId: Int): Flow<WidgetSettings> = context.dataStore.data
        .map { it.settingsOf(widgetId) }
        .distinctUntilChanged()

    /** Adds the date, or replaces the one with the same id. */
    suspend fun saveDate(date: ImportantDate) = editDates { list ->
        if (list.any { it.id == date.id }) list.map { if (it.id == date.id) date else it } else list + date
    }

    suspend fun deleteDate(id: String) = editDates { list -> list.filterNot { it.id == id } }

    suspend fun updateSettings(widgetId: Int, transform: (WidgetSettings) -> WidgetSettings) {
        context.dataStore.edit { prefs ->
            prefs[widgetKey(widgetId)] = transform(prefs.settingsOf(widgetId)).toJson()
        }
        WidgetRefresh.refreshAndSchedule(context)
    }

    /** Forgets the settings of widgets that were removed from the home screen. */
    suspend fun forgetWidgets(widgetIds: IntArray) {
        context.dataStore.edit { prefs -> widgetIds.forEach { prefs.remove(widgetKey(it)) } }
    }

    private suspend fun editDates(transform: (List<ImportantDate>) -> List<ImportantDate>) {
        context.dataStore.edit { prefs ->
            prefs[DATES] = transform(importantDatesFromJson(prefs[DATES])).toJson()
        }
        WidgetRefresh.refreshAndSchedule(context)
    }
}
