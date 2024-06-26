package dev.johnoreilly.confetti.screenshot

import android.graphics.Bitmap
import androidx.compose.ui.test.SemanticsNodeInteraction

fun interface SnapshotTransformer {
    fun transform(node: SemanticsNodeInteraction, bitmap: Bitmap): Bitmap

    companion object {
        val None = SnapshotTransformer { node, bitmap ->
            bitmap
        }
    }
}
