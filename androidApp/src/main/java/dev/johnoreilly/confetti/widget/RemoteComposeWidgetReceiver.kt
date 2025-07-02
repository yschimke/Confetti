package dev.johnoreilly.confetti.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.RequiresApi

class RemoteComposeWidgetReceiver : AppWidgetProvider() {

    /** Called when widgets must provide remote views. */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onUpdate(context: Context, wm: AppWidgetManager, widgetIds: IntArray) {
        super.onUpdate(context, wm, widgetIds)

        val bytes = byteArrayOf()
        val widget = RemoteViews(RemoteViews.DrawInstructions.Builder(listOf(bytes)).build())

        widgetIds.forEach { widgetId -> wm.updateAppWidget(widgetId, widget) }
    }
}
