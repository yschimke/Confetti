package dev.johnoreilly.confetti.livery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.materialkolor.dynamicColorScheme
import dev.johnoreilly.confetti.ui.LocalDynamicColorInUse
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.johnoreilly.confetti.di.KoinTestApp
import dev.johnoreilly.confetti.preview.sessionDetails
import dev.johnoreilly.confetti.ui.livery.LocalLiveryStyling
import dev.johnoreilly.confetti.ui.livery.PromoSlot
import dev.johnoreilly.confetti.ui.livery.SessionCardSlot
import kotlinx.datetime.LocalDateTime
import dev.johnoreilly.confetti.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.After
import org.junit.Rule
import org.koin.core.context.stopKoin
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * The real app path: the catalog bundled into the APK's assets by the Livery Gradle plugin,
 * selected by conference series, filled through the contract and played by the RC player inside
 * Confetti's own composables. A conference without a series keeps Confetti's row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = KoinTestApp::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiverySlotsTest {
    @get:Rule val compose = createComposeRule()

    // During the droidcon London party placement's window.
    private val clock = Instant.parse("2026-10-29T18:00:00Z").toEpochMilli()
    private val styling = confettiLiveryStyling(ApplicationProvider.getApplicationContext()) { clock }

    // Each test's application starts Koin (KoinTestApp); stop it so the next test can.
    @After fun stopKoinAfterTest() = stopKoin()

    /** The app in light mode: each conference's light theme pack. */
    @Test fun lightMode() = slots("livery-slots-light", lightColorScheme())

    /** The app in dark mode: each conference's dark theme pack. */
    @Test fun darkMode() = slots("livery-slots-dark", darkColorScheme())

    /**
     * Material 3 dynamic colour on: the user's wallpaper scheme (here one materialkolor derives from
     * a teal wallpaper) replaces the conference palettes; each conference keeps its layout.
     */
    @Test fun dynamicColor() = slots(
        "livery-slots-dynamic",
        dynamicColorScheme(seedColor = Color(0xFF00897B), isDark = false, isAmoled = false),
        dynamic = true,
    )

    private fun slots(name: String, scheme: ColorScheme, dynamic: Boolean = false) {
        // The slots only exist when the feature is on (the default; -Pconfetti.livery=false is off).
        assumeTrue(BuildConfig.LIVERY_ENABLED)
        val live = LocalDateTime.parse("2023-04-13T14:10")
        compose.setContent {
            MaterialTheme(colorScheme = scheme) {
                CompositionLocalProvider(LocalLiveryStyling provides styling, LocalDynamicColorInUse provides dynamic) {
                    Column(
                        Modifier.background(MaterialTheme.colorScheme.background).width(400.dp).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for ((conference, bookmarked) in listOf("kotlinconf2026" to false, "droidconlondon2025" to true, "devfestnantes2025" to false)) {
                            SessionCardSlot(conference, sessionDetails, live, bookmarked, onOpen = {}, onToggleBookmark = {}) {
                                Text("Confetti's own row for $conference", Modifier.testTag("native"), color = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                        PromoSlot("home.feed", "droidconlondon2026")
                    }
                }
            }
        }
        // Planning reads the bundled catalog off the main thread; until it lands, slots draw their fallback.
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("native").fetchSemanticsNodes().size == 1 }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
        // Only the conference without a design kept the native row.
        assertEquals(1, compose.onAllNodesWithTag("native").fetchSemanticsNodes().size)
    }
}
