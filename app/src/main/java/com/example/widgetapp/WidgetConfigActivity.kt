package com.example.widgetapp

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * The customize screen for a single widget. Android opens it when a widget is first
 * placed, and again from the edit button shown when long-pressing a widget.
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Until "Done" is tapped, leaving this screen tells the launcher not to place the widget.
        setResult(RESULT_CANCELED)

        val widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val store = Store(this)
        setContent {
            AppTheme {
                WidgetEditorScreen(store, widgetId, onDone = {
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                    // A newly placed widget is not drawn by the system; this draws it.
                    sendBroadcast(WidgetRefresh.tickIntent(this))
                    finish()
                })
            }
        }
    }
}
