package com.example.widgetapp

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.time.ZonedDateTime
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeWidget : GlanceAppWidget() {

    // Exact, so each page can be made precisely as tall as the widget.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = Store(context)
        val power = context.getSystemService(PowerManager::class.java)
        // Every widget on the home screen has its own settings, looked up by its id.
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val settingsFlow = store.settings(widgetId)
        val firstDates = store.dates.first()
        val firstSettings = settingsFlow.first()

        coroutineScope {
            launch { keepAliveWhileLive(context, id, settingsFlow, power) }

            provideContent {
                val allDates by store.dates.collectAsState(firstDates)
                val settings by settingsFlow.collectAsState(firstSettings)
                val tick by WidgetRefresh.tick.collectAsState()
                val dates = settings.visible(allDates)
                val live = settings.isLive && dates.isNotEmpty()

                // The clock the countdown is measured against. It is re-read on every
                // refresh, and once a second while a seconds countdown is on screen.
                val now by produceState(ZonedDateTime.now(), tick, live) {
                    value = ZonedDateTime.now()
                    while (live) {
                        delay(1_000 - System.currentTimeMillis() % 1_000)
                        if (power.isInteractive) value = ZonedDateTime.now()
                    }
                }

                WidgetContent(
                    dates = Countdown.sorted(dates, now),
                    settings = settings,
                    now = now,
                    widgetId = widgetId,
                    hasSavedDates = allDates.isNotEmpty(),
                )
            }
        }
    }

    /**
     * Glance stops composing a widget about 45 seconds after its last update, which
     * would freeze a seconds countdown. Asking for an update every few seconds keeps
     * it going, but only while the screen is on and seconds are actually displayed.
     */
    private suspend fun keepAliveWhileLive(
        context: Context,
        id: GlanceId,
        settings: Flow<WidgetSettings>,
        power: PowerManager,
    ) {
        while (true) {
            delay(3_000)
            if (power.isInteractive && settings.first().isLive) update(context, id)
        }
    }
}

/** Opens the customize screen for one particular widget. */
fun widgetConfigIntent(context: Context, widgetId: Int): Intent =
    Intent(context, WidgetConfigActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        // A distinct address per widget stops Android from reusing one widget's intent for another.
        .setData(Uri.parse("daycounter://widget/$widgetId"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

class HomeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HomeWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // A widget was added, removed, resized, or restored after a reboot:
        // start (or stop) the refresh alarm to match.
        val tick = WidgetRefresh.tickIntent(context)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_DELETED) {
            val removed = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (removed != AppWidgetManager.INVALID_APPWIDGET_ID) {
                tick.putExtra(WidgetRefresh.EXTRA_REMOVED_WIDGETS, intArrayOf(removed))
            }
        }
        context.sendBroadcast(tick)
    }
}

@Composable
private fun WidgetContent(
    dates: List<ImportantDate>,
    settings: WidgetSettings,
    now: ZonedDateTime,
    widgetId: Int,
    hasSavedDates: Boolean,
) {
    val context = LocalContext.current
    val height = LocalSize.current.height
    val background = Color(settings.backgroundColor).copy(alpha = settings.backgroundAlpha)
    val openApp = actionStartActivity<MainActivity>()

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(24.dp)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (dates.isEmpty()) {
            // Nothing saved yet: go to the app to add a date.
            // Dates exist but none are picked for this widget: go to its own settings.
            val message = if (hasSavedDates) "No dates chosen. Tap to pick some." else "Tap to add an important date"
            val onTap = if (hasSavedDates) actionStartActivity(widgetConfigIntent(context, widgetId)) else openApp
            Box(
                modifier = GlanceModifier.fillMaxSize().clickable(onTap).padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = message,
                    style = textStyle(settings, Color(settings.textColor), 14f),
                )
            }
        } else {
            // One full-height page per date: swiping up or down moves between them.
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                itemsIndexed(dates, itemId = { _, date -> date.id.hashCode().toLong() }) { index, date ->
                    DatePage(
                        date = date,
                        settings = settings,
                        now = now,
                        position = if (dates.size > 1) "${index + 1}/${dates.size}" else null,
                        height = height,
                        onClick = openApp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DatePage(
    date: ImportantDate,
    settings: WidgetSettings,
    now: ZonedDateTime,
    position: String?,
    height: Dp,
    onClick: Action,
) {
    // A one-row widget has no room for stacked lines.
    val compact = height < 100.dp
    val color = Color(settings.textColor)

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(height)
            .clickable(onClick)
            .padding(horizontal = 14.dp, vertical = if (compact) 4.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = date.title,
            style = textStyle(settings, color, if (settings.showCountdown) 14f else 20f),
            maxLines = if (compact) 1 else 2,
        )

        if (settings.showCountdown) {
            val countdown = Countdown.describe(date, settings, now)
            if (compact) {
                Text(
                    text = countdown.oneLine,
                    style = textStyle(settings, color, 16f, bold = true),
                    maxLines = 1,
                )
            } else {
                // Long numbers (seconds run to eight digits) get a smaller size to fit.
                val size = when {
                    countdown.value.length <= 6 -> 32f
                    countdown.value.length <= 9 -> 26f
                    else -> 20f
                }
                Text(
                    text = countdown.value,
                    style = textStyle(settings, color, size, bold = true),
                    maxLines = 1,
                )
                if (countdown.label.isNotEmpty()) {
                    Text(
                        text = countdown.label,
                        style = textStyle(settings, color.copy(alpha = 0.8f), 12f),
                        maxLines = 1,
                    )
                }
            }
        }

        if (settings.showProgress) {
            Spacer(GlanceModifier.height(6.dp))
            LinearProgressIndicator(
                progress = Countdown.progress(date, now),
                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                color = ColorProvider(color),
                backgroundColor = ColorProvider(color.copy(alpha = 0.25f)),
            )
        }

        if (position != null && !compact) {
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = position,
                style = textStyle(settings, color.copy(alpha = 0.6f), 10f),
                maxLines = 1,
            )
        }
    }
}

private fun textStyle(
    settings: WidgetSettings,
    color: Color,
    size: Float,
    bold: Boolean = settings.bold,
) = TextStyle(
    color = ColorProvider(color),
    fontSize = (size * settings.textScale).sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    fontFamily = when (settings.font) {
        WidgetFont.SANS -> FontFamily.SansSerif
        WidgetFont.SERIF -> FontFamily.Serif
        WidgetFont.MONOSPACE -> FontFamily.Monospace
        WidgetFont.CURSIVE -> FontFamily.Cursive
    },
    textAlign = TextAlign.Center,
)
