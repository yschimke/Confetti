@file:OptIn(InternalRoborazziApi::class, ExperimentalRoborazziApi::class)

package dev.johnoreilly.confetti.wear.roborazzi

import com.github.takahirom.roborazzi.AndroidComposePreviewTester
import com.github.takahirom.roborazzi.ComposePreviewTester
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import org.koin.core.context.stopKoin
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

class ConfettiComposePreviewTester(
    val delegate: AndroidComposePreviewTester = AndroidComposePreviewTester()
): ComposePreviewTester<AndroidPreviewInfo> by delegate {
    override fun test(preview: ComposablePreview<AndroidPreviewInfo>) {
        try {
            delegate.test(preview)
        } finally {
            stopKoin()
        }
    }
}