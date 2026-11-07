package dev.johnoreilly.confetti.livery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.materialkolor.dynamicColorScheme
import dev.johnoreilly.confetti.BuildConfig
import dev.johnoreilly.confetti.analytics.AnalyticsEvent
import dev.johnoreilly.confetti.analytics.AnalyticsLogger
import dev.johnoreilly.confetti.di.KoinTestApp
import dev.johnoreilly.confetti.fragment.SessionDetails
import dev.johnoreilly.confetti.preview.sessionDetails
import dev.johnoreilly.confetti.ui.LocalDynamicColorInUse
import dev.johnoreilly.confetti.ui.components.ConferenceComponents
import dev.johnoreilly.confetti.ui.components.NativeComponents
import dev.johnoreilly.confetti.ui.components.SduiComponents
import dev.johnoreilly.confetti.ui.components.SduiTracking
import dev.johnoreilly.confetti.ui.components.SessionCardModel
import dev.johnoreilly.confetti.ui.livery.LiveryComponents
import kotlinx.datetime.LocalDateTime
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * The real app path: each conference's catalog bundled into the APK's assets by the build, picked
 * by conference series, filled through the contract and played by the RC player inside Confetti's
 * agenda, through [ConferenceComponents] as the screens use it. A conference without a series
 * keeps Confetti's row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = KoinTestApp::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiverySlotsTest {
    @get:Rule val compose = createComposeRule()

    // During the droidcon London party placement's window.
    private val clock = Instant.parse("2026-10-29T18:00:00Z").toEpochMilli()
    private val live = LocalDateTime.parse("2023-04-13T14:10")

    private val logged = mutableListOf<AnalyticsEvent>()
    private val analytics = object : AnalyticsLogger {
        override fun logEvent(event: AnalyticsEvent) { synchronized(logged) { logged += event } }
    }

    /** Livery from the bundled catalogs only (no network in tests), on the app's OkHttp client. */
    private fun livery() = LiveryComponents(
        confettiLiveryStyling(ApplicationProvider.getApplicationContext(), liveryHttpClient(OkHttpClient()), analytics, clock = { clock }, refresh = false),
        NativeComponents(SduiTracking(analytics), SduiExperiment.LIVERY),
    )

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

    /**
     * The native arm: Confetti's own rows, no Livery built at all (no repository, no catalog read),
     * and the row's impression and taps reported as the experiment's events.
     */
    @Test fun nativeArmNeverBuildsLivery() {
        val components = SduiComponents(
            arms = { SduiExperiment.NATIVE },
            native = NativeComponents(SduiTracking(analytics), SduiExperiment.NATIVE),
            livery = { error("the native arm must not build Livery") },
        )
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Column {
                    for (conference in listOf("kotlinconf2026", "droidconlondon2025")) components.SessionCard(card(conference))
                    components.Promo("home.feed", "droidconlondon2026", Modifier.fillMaxWidth())
                }
            }
        }
        compose.waitForIdle()
        assertEquals(2, nativeRows())
        assertEquals(0, compose.onAllNodes(liverySlot).fetchSemanticsNodes().size)
        compose.onAllNodesWithText(sessionDetails.title)[0].performClick()
        compose.waitForIdle()
        assertFalse(components.liveryStarted)
        val events = synchronized(logged) { logged.toList() }
        assertEquals(2, events.count { it.id == "livery_impression" })
        val tap = events.single { it.id == "livery_action" }
        assertEquals("open", tap.properties["action"])
        assertEquals(SduiExperiment.NATIVE, tap.properties["exp_${SduiExperiment.ID}"])
        assertEquals(SduiExperiment.NATIVE_DESIGN, tap.properties["design_id"])
    }

    /** The arms are per conference: KotlinConf in Livery's designs, droidcon in Confetti's own rows. */
    @Test fun armsArePerConference() {
        assumeTrue(BuildConfig.LIVERY_ENABLED)
        val components = SduiComponents(
            arms = { series -> if (series == "kotlinconf") SduiExperiment.LIVERY else SduiExperiment.NATIVE },
            native = NativeComponents(),
            livery = ::livery,
        )
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Column {
                    components.SessionCard(card("kotlinconf2026"))
                    components.SessionCard(card("droidconlondon2025"))
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(liverySlot).fetchSemanticsNodes().size == 1 }
        assertTrue(components.liveryStarted)
        // The droidcon row is Confetti's own; the KotlinConf card is a template.
        assertEquals(1, nativeRows())
    }

    /** A row a lazy list reuses for another session reports that session's impression too. */
    @Test fun nativeArmReportsAnImpressionPerSessionShown() {
        val native = NativeComponents(SduiTracking(analytics), SduiExperiment.NATIVE)
        var session by mutableStateOf(sessionDetails)
        compose.setContent { native.SessionCard(card("kotlinconf2026", session)) }
        fun impressions() = synchronized(logged) { logged.count { it.id == "livery_impression" } }
        compose.waitUntil(10_000) { impressions() == 1 }
        session = sessionDetails.copy(id = "another-session", title = "Another session")
        compose.waitForIdle()
        compose.waitUntil(10_000) { impressions() == 2 }
    }

    private fun slots(name: String, scheme: ColorScheme, dynamic: Boolean = false) {
        // The bundled catalogs are only in the sduiDebug build type's assets.
        assumeTrue(BuildConfig.LIVERY_ENABLED)
        val components = livery()
        compose.setContent {
            MaterialTheme(colorScheme = scheme) {
                CompositionLocalProvider(LocalDynamicColorInUse provides dynamic) {
                    Column(
                        Modifier.background(MaterialTheme.colorScheme.background).width(400.dp).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for ((conference, bookmarked) in listOf("kotlinconf2026" to false, "droidconlondon2025" to true, "devfestnantes2025" to false)) {
                            components.SessionCard(card(conference, bookmarked = bookmarked))
                        }
                        components.Promo("home.feed", "droidconlondon2026", Modifier.fillMaxWidth())
                    }
                }
            }
        }
        // Planning reads the bundled catalog off the main thread; until it lands, slots draw their fallback.
        compose.waitUntil(10_000) { compose.onAllNodes(liverySlot).fetchSemanticsNodes().size == 3 }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
        // Only the conference without a design kept Confetti's own row.
        assertEquals(1, nativeRows())
    }

    private fun card(conference: String, session: SessionDetails = sessionDetails, bookmarked: Boolean = false) = SessionCardModel(
        conference = conference, session = session, now = live, isBookmarked = bookmarked, isLoggedIn = true,
        trackColor = null, tags = emptyList(), sessionSelected = {}, addBookmark = {}, removeBookmark = {}, onNavigateToSignIn = {},
    )

    /**
     * Confetti's own session rows: the title as text outside any template (the RC player exposes a
     * template's text to accessibility too, so the title alone would count both).
     */
    private fun nativeRows() =
        compose.onAllNodes(hasText(sessionDetails.title) and !hasAnyAncestor(liverySlot)).fetchSemanticsNodes().size

    /** A node a Livery template drew: LiveryHost tags each player `livery:<placement or contract>:<design>`. */
    private val liverySlot = SemanticsMatcher("is a Livery slot") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("livery:") == true
    }
}
