@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.widgetapp

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Ids of the widgets currently on the home screen, re-read whenever the app comes forward. */
    private var widgetIds by mutableStateOf(emptyList<Int>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(this)

        // Make sure widgets are current and their refresh alarm is running.
        lifecycleScope.launch { WidgetRefresh.refreshAndSchedule(applicationContext) }

        setContent {
            AppTheme { App(store, widgetIds) }
        }
    }

    override fun onResume() {
        super.onResume()
        widgetIds = AppWidgetManager.getInstance(this)
            .getAppWidgetIds(ComponentName(this, HomeWidgetReceiver::class.java))
            .sorted()
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun App(store: Store, widgetIds: List<Int>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val savedDates by store.dates.collectAsState(initial = null)
    val now by rememberNow()
    val dates = savedDates?.let { Countdown.sorted(it, now) }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ImportantDate?>(null) }
    var editingWidget by rememberSaveable { mutableStateOf<Int?>(null) }

    // Customizing a widget takes over the whole screen.
    val widgetBeingEdited = editingWidget
    if (widgetBeingEdited != null) {
        BackHandler { editingWidget = null }
        WidgetEditorScreen(store, widgetBeingEdited, onDone = { editingWidget = null })
        return
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("Day Counter") })
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Dates") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Widgets") })
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(onClick = { editing = null; editorOpen = true }) {
                    Text("Add date")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (tab == 0) {
            DatesTab(
                dates = dates,
                now = now,
                onEdit = { editing = it; editorOpen = true },
                modifier = Modifier.padding(padding),
            )
        } else {
            WidgetsTab(
                store = store,
                widgetIds = widgetIds,
                dates = dates.orEmpty(),
                now = now,
                onEdit = { editingWidget = it },
                onPin = {
                    scope.launch {
                        val asked = GlanceAppWidgetManager(context)
                            .requestPinGlanceAppWidget(HomeWidgetReceiver::class.java)
                        if (!asked) {
                            snackbar.showSnackbar(
                                "Your launcher can't add it from here. Long-press the home screen and choose Widgets.",
                            )
                        }
                    }
                },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (editorOpen) {
        val existing = editing
        DateEditorDialog(
            initial = existing,
            onDismiss = { editorOpen = false },
            onSave = { date ->
                editorOpen = false
                scope.launch { store.saveDate(date) }
            },
            onDelete = if (existing == null) null else {
                {
                    editorOpen = false
                    scope.launch { store.deleteDate(existing.id) }
                }
            },
        )
    }
}

// ---------------------------------------------------------------- Dates tab

val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** The app's own list always counts in whole days; each widget has its own unit. */
private val LIST_STYLE = WidgetSettings()

@Composable
private fun DatesTab(
    dates: List<ImportantDate>?,
    now: ZonedDateTime,
    onEdit: (ImportantDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        dates == null -> Box(modifier.fillMaxSize())

        dates.isEmpty() -> Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "No important dates yet.\nTap “Add date” to create your first one.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(dates, key = { it.id }) { date ->
                Card(onClick = { onEdit(date) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(date.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = date.date.format(DATE_FORMAT) +
                                (date.time?.let { " · " + it.format(TIME_FORMAT) } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = Countdown.describe(date, LIST_STYLE, now).oneLine,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { Countdown.progress(date, now) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------- Widgets tab

@Composable
private fun WidgetsTab(
    store: Store,
    widgetIds: List<Int>,
    dates: List<ImportantDate>,
    now: ZonedDateTime,
    onEdit: (Int) -> Unit,
    onPin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (widgetIds.isEmpty()) {
            Text(
                "No widgets on your home screen yet. Add one, then choose its dates and style here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        widgetIds.forEachIndexed { index, widgetId ->
            key(widgetId) {
                val settings by remember { store.settings(widgetId) }.collectAsState(initial = null)
                settings?.let { current ->
                    Card(onClick = { onEdit(widgetId) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            WidgetPreview(previewDate(current, dates, now.toLocalDate()), current, now)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Widget ${index + 1}", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = if (current.dateIds == null) "All dates"
                                    else "${current.visible(dates).size} of ${dates.size} dates",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        OutlinedButton(onClick = onPin, modifier = Modifier.fillMaxWidth()) {
            Text("Add widget to home screen")
        }
        Text(
            "Each widget keeps its own dates and style. Tap one above to change it, " +
                "or long-press it on the home screen and use its edit button.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ------------------------------------------------------------ Add / edit dialog

private enum class Picker { NONE, DATE, TIME, START }

@Composable
private fun DateEditorDialog(
    initial: ImportantDate?,
    onDismiss: () -> Unit,
    onSave: (ImportantDate) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now().plusDays(1)) }
    var time by remember { mutableStateOf(initial?.time) }
    var start by remember { mutableStateOf(initial?.startDate ?: LocalDate.now()) }
    var picker by remember { mutableStateOf(Picker.NONE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add important date" else "Edit date") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                PickerButton("Date", date.format(DATE_FORMAT)) { picker = Picker.DATE }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PickerButton("Time", time?.format(TIME_FORMAT) ?: "All day", Modifier.weight(1f)) {
                        picker = Picker.TIME
                    }
                    if (time != null) {
                        TextButton(onClick = { time = null }) { Text("Clear") }
                    }
                }
                PickerButton("Progress starts", start.format(DATE_FORMAT)) { picker = Picker.START }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val name = title.trim()
                    onSave(
                        initial?.copy(title = name, date = date, time = time, startDate = start)
                            ?: ImportantDate(title = name, date = date, time = time, startDate = start),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    when (picker) {
        Picker.DATE -> DatePickerPopup(date, onDismiss = { picker = Picker.NONE }) { date = it }
        Picker.START -> DatePickerPopup(start, onDismiss = { picker = Picker.NONE }) { start = it }
        Picker.TIME -> TimePickerPopup(time ?: LocalTime.of(9, 0), onDismiss = { picker = Picker.NONE }) { time = it }
        Picker.NONE -> Unit
    }
}

@Composable
private fun PickerButton(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Text(label)
        Spacer(Modifier.weight(1f))
        Text(value)
    }
}

@Composable
private fun DatePickerPopup(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    // The Material date picker works in UTC midnights.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun TimePickerPopup(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(LocalTime.of(state.hour, state.minute))
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}
