@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.widgetapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The current time, refreshed on every second, but only while the screen is being shown. */
@Composable
fun rememberNow(): State<ZonedDateTime> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(ZonedDateTime.now(), lifecycle) {
        // Stops ticking when the app goes to the background and resumes on return.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = ZonedDateTime.now()
                delay(1_000 - System.currentTimeMillis() % 1_000)
            }
        }
    }
}

/** A made-up date for previews when nothing is saved yet. */
fun sampleDate(today: LocalDate) = ImportantDate(
    id = "sample",
    title = "New Year",
    date = LocalDate.of(today.year + 1, 1, 1),
    startDate = LocalDate.of(today.year, 1, 1),
)

/** What a widget's preview should show: its first date, a sample if nothing is saved, or nothing. */
fun previewDate(settings: WidgetSettings, sortedDates: List<ImportantDate>, today: LocalDate): ImportantDate? =
    settings.visible(sortedDates).firstOrNull() ?: if (sortedDates.isEmpty()) sampleDate(today) else null

/**
 * Full-screen editor for one widget: which dates it shows and how it looks.
 * Every change is saved, and pushed to the widget, as it is made.
 */
@Composable
fun WidgetEditorScreen(store: Store, widgetId: Int, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings by remember(widgetId) { store.settings(widgetId) }.collectAsState(initial = null)
    val savedDates by store.dates.collectAsState(initial = null)
    val now by rememberNow()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customize widget") },
                actions = { TextButton(onClick = onDone) { Text("Done") } },
            )
        },
    ) { padding ->
        val current = settings
        val dates = savedDates
        // Wait for both to load so sliders and switches start at the saved values.
        if (current != null && dates != null) {
            WidgetEditor(
                settings = current,
                dates = Countdown.sorted(dates, now),
                now = now,
                onChange = { change -> scope.launch { store.updateSettings(widgetId, change) } },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

private val PALETTE: List<Pair<String, Int>> = listOf(
    "White" to 0xFFFFFFFF, "Black" to 0xFF000000, "Charcoal" to 0xFF1C1B1F,
    "Red" to 0xFFE53935, "Pink" to 0xFFD81B60, "Purple" to 0xFF8E24AA,
    "Indigo" to 0xFF3949AB, "Blue" to 0xFF1E88E5, "Teal" to 0xFF00897B,
    "Green" to 0xFF43A047, "Amber" to 0xFFFFB300, "Orange" to 0xFFFB8C00,
    "Brown" to 0xFF6D4C41, "Slate" to 0xFF546E7A,
).map { (name, argb) -> name to argb.toInt() }

@Composable
private fun WidgetEditor(
    settings: WidgetSettings,
    dates: List<ImportantDate>,
    now: ZonedDateTime,
    onChange: ((WidgetSettings) -> WidgetSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Sliders move a local copy for the preview and only save when released.
    var textScale by remember(settings.textScale) { mutableFloatStateOf(settings.textScale) }
    var alpha by remember(settings.backgroundAlpha) { mutableFloatStateOf(settings.backgroundAlpha) }
    val shown = settings.copy(textScale = textScale, backgroundAlpha = alpha)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WidgetPreview(previewDate(shown, dates, now.toLocalDate()), shown, now)

        SectionTitle("Dates")
        val chosen = settings.dateIds
        SwitchRow("Show all dates", "Including any you add later", chosen == null) { all ->
            // Switching to a hand-picked list starts with everything ticked.
            onChange { it.copy(dateIds = if (all) null else dates.map { date -> date.id }.toSet()) }
        }
        if (chosen != null) {
            if (dates.isEmpty()) {
                Text(
                    "You haven't added any dates yet. Add some in the Day Counter app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for (date in dates) {
                CheckRow(date.title, date.date.format(DATE_FORMAT), date.id in chosen) { on ->
                    onChange {
                        val ids = it.dateIds.orEmpty()
                        it.copy(dateIds = if (on) ids + date.id else ids - date.id)
                    }
                }
            }
        }

        SectionTitle("Content")
        SwitchRow("Show countdown", "Turn off to show only the title", settings.showCountdown) { on ->
            onChange { it.copy(showCountdown = on) }
        }
        if (settings.showCountdown) {
            FieldLabel("Count in")
            ChipRow(CountdownUnit.entries, settings.unit, { it.label }) { unit ->
                onChange { it.copy(unit = unit) }
            }
            if (settings.unit == CountdownUnit.SECONDS) {
                Text(
                    "Seconds redraw the widget every second while the screen is on, " +
                        "which uses more battery than the other units.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (settings.unit != CountdownUnit.SECONDS) {
                FieldLabel("Decimal places")
                ChipRow(listOf(0, 1, 2, 3), settings.decimals, { it.toString() }) { decimals ->
                    onChange { it.copy(decimals = decimals) }
                }
            }
        }
        SwitchRow("Progress bar", "Fills from each date's start to the date itself", settings.showProgress) { on ->
            onChange { it.copy(showProgress = on) }
        }

        SectionTitle("Text")
        FieldLabel("Font")
        ChipRow(WidgetFont.entries, settings.font, { it.label }) { font ->
            onChange { it.copy(font = font) }
        }
        SwitchRow("Bold", null, settings.bold) { on -> onChange { it.copy(bold = on) } }
        FieldLabel("Size ${(textScale * 100).roundToInt()}%")
        Slider(
            value = textScale,
            onValueChange = { textScale = it },
            onValueChangeFinished = { onChange { it.copy(textScale = textScale) } },
            valueRange = 0.7f..1.6f,
        )
        FieldLabel("Color")
        ColorRow(settings.textColor) { color -> onChange { it.copy(textColor = color) } }

        SectionTitle("Background")
        SwitchRow("Transparent", "Show your wallpaper behind the text", alpha == 0f) { on ->
            onChange { it.copy(backgroundAlpha = if (on) 0f else 1f) }
        }
        if (alpha > 0f) {
            FieldLabel("Color")
            ColorRow(settings.backgroundColor) { color -> onChange { it.copy(backgroundColor = color) } }
            FieldLabel("Opacity ${(alpha * 100).roundToInt()}%")
            Slider(
                value = alpha,
                onValueChange = { alpha = it },
                onValueChangeFinished = { onChange { it.copy(backgroundAlpha = alpha) } },
                valueRange = 0.1f..1f,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * A stand-in for the real widget, drawn over a fake wallpaper so transparency shows.
 * A null [date] means the widget has dates to choose from but none chosen.
 */
@Composable
fun WidgetPreview(date: ImportantDate?, settings: WidgetSettings, now: ZonedDateTime) {
    val color = Color(settings.textColor)
    val family = when (settings.font) {
        WidgetFont.SANS -> FontFamily.SansSerif
        WidgetFont.SERIF -> FontFamily.Serif
        WidgetFont.MONOSPACE -> FontFamily.Monospace
        WidgetFont.CURSIVE -> FontFamily.Cursive
    }
    val weight = if (settings.bold) FontWeight.Bold else FontWeight.Normal
    val scale = settings.textScale

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3A6EA5), Color(0xFFB45F9E), Color(0xFFF2A65A)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(230.dp)
                .height(130.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(settings.backgroundColor).copy(alpha = settings.backgroundAlpha))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (date == null) {
                Text(
                    text = "No dates chosen",
                    color = color,
                    fontSize = (14 * scale).sp,
                    fontFamily = family,
                    fontWeight = weight,
                    textAlign = TextAlign.Center,
                )
                return@Column
            }

            Text(
                text = date.title,
                color = color,
                fontSize = ((if (settings.showCountdown) 14 else 20) * scale).sp,
                fontFamily = family,
                fontWeight = weight,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (settings.showCountdown) {
                val countdown = Countdown.describe(date, settings, now)
                val size = when {
                    countdown.value.length <= 6 -> 32
                    countdown.value.length <= 9 -> 26
                    else -> 20
                }
                Text(
                    text = countdown.value,
                    color = color,
                    fontSize = (size * scale).sp,
                    fontFamily = family,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                if (countdown.label.isNotEmpty()) {
                    Text(
                        text = countdown.label,
                        color = color.copy(alpha = 0.8f),
                        fontSize = (12 * scale).sp,
                        fontFamily = family,
                        fontWeight = weight,
                        maxLines = 1,
                    )
                }
            }
            if (settings.showProgress) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { Countdown.progress(date, now) },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = color,
                    trackColor = color.copy(alpha = 0.25f),
                    drawStopIndicator = {},
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp),
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title, subtitle, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CheckRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        RowText(title, subtitle, Modifier.weight(1f))
    }
}

@Composable
private fun RowText(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (option in options) {
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

@Composable
private fun ColorRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for ((name, argb) in PALETTE) {
            val isSelected = argb == selected
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(argb) }
                    .semantics {
                        contentDescription = name
                        this.selected = isSelected
                    },
            )
        }
    }
}
