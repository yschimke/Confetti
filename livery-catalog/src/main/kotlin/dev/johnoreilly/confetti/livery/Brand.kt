package dev.johnoreilly.confetti.livery

import androidx.compose.ui.graphics.Color

/**
 * A conference's look as data. Every colour here is the *preview* (initial) value of a named
 * value in the document, so a theme pack (catalog/themes/<series>.json) can change it later
 * without re-capturing: that's the knob an admin's agent turns first.
 */
data class ConferenceBrand(
    /** The `series` tag on every design, which the app queries by. */
    val series: String,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val tertiary: Color,
    val surface: Color,
    val onSurface: Color,
    val muted: Color,
)

val Droidcon = ConferenceBrand(
    series = "droidcon",
    primary = Color(0xFF3DDC84), // Android green
    onPrimary = Color(0xFF0E1116),
    secondary = Color(0xFFB4F54A),
    tertiary = Color(0xFF4285F4),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFF5F7FA),
    muted = Color(0xFF9AA4B2),
)

val KotlinConf = ConferenceBrand(
    series = "kotlinconf",
    primary = Color(0xFF7F52FF), // Kotlin purple
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFC811E2),
    tertiary = Color(0xFFE54857),
    surface = Color(0xFF19191C),
    onSurface = Color(0xFFFFFFFF),
    muted = Color(0xFFC7C2D6),
)
