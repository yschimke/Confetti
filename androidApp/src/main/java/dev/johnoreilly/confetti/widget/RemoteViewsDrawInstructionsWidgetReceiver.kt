@file:OptIn(ExperimentalStdlibApi::class)

package dev.johnoreilly.confetti.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.compose.remote.core.RemoteComposeBuffer
import androidx.compose.remote.core.RemoteContext
import androidx.compose.remote.core.operations.RootContentBehavior
import androidx.compose.remote.creation.RemoteComposeWriter
import androidx.compose.remote.creation.profile.Profile.WIDGETS_V6
import java.io.ByteArrayOutputStream

class RemoteComposeWidgetReceiver : AppWidgetProvider() {

    /** Called when widgets must provide remote views. */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onUpdate(context: Context, wm: AppWidgetManager, widgetIds: IntArray) {
        println("onUpdate")
        super.onUpdate(context, wm, widgetIds)

        val bytes = encodedDocument()

        val widget = RemoteViews(RemoteViews.DrawInstructions.Builder(listOf(bytes)).build())

        widgetIds.forEach { widgetId -> wm.updateAppWidget(widgetId, widget) }
    }

    private fun encodedDocument(): ByteArray {
        val rcDoc = RemoteComposeWriter.obtain(
            300,
            300,
            "Sessions",
            WIDGETS_V6
        )
        rcDoc.setRootContentBehavior(
            RootContentBehavior.NONE,
            RootContentBehavior.ALIGNMENT_CENTER,
            RootContentBehavior.SIZING_SCALE,
            RootContentBehavior.SCALE_FILL_BOUNDS
        )
        rcDoc.painter.setColor(Color.GRAY).commit()
        rcDoc.drawRoundRect(
            0f,
            0f,
            RemoteContext.FLOAT_WINDOW_WIDTH,
            RemoteContext.FLOAT_WINDOW_HEIGHT,
            10f,
            10f
        )
        rcDoc.painter.setColor(Color.RED).commit()
        rcDoc.drawCircle(
            50f,
            50f,
            10f
        )
        return rcDoc.buffer.toBytes()
    }
}

private fun RemoteComposeBuffer.toBytes(): ByteArray {
    return ByteArrayOutputStream().apply {
        this.write(this@toBytes.buffer.buffer, 0, this@toBytes.buffer.size)
    }.toByteArray()
}
