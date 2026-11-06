package dev.johnoreilly.confetti.livery

import androidx.compose.runtime.Composable

/** One playable document of a design, for one Livery target. */
class TemplateDef(
    /** File stem: catalog/designs/<design>/<name>.rc and .json. */
    val name: String,
    /** Livery `Target` (PROTOCOL §3), as JSON. */
    val target: String,
    /** Capture size in dp. Layout happens in the player; this is the document's nominal size. */
    val widthDp: Int,
    val heightDp: Int,
    val content: @Composable () -> Unit,
)

class DesignDef(
    val id: String,
    val contract: ConfettiContract,
    val brand: ConferenceBrand,
    val title: String,
    /** Which of the contract's examples is this design's preview data. */
    val example: String,
    val templates: List<TemplateDef>,
    val install: String = "Always",
    val variant: String = "default",
)

private const val IN_APP = """{ "surface": "in-app", "rcApiLevel": 7, "profiles": ["androidx"], "size": { "minWidthDp": 280 } }"""
private const val LAUNCHER = """{ "surface": "launcher-widget", "rcApiLevel": 7, "profiles": ["widgets"], "size": { "minWidthDp": 110, "maxWidthDp": 400, "minHeightDp": 80, "maxHeightDp": 400 } }"""
private const val WEAR_LARGE = """{ "surface": "wear-widget", "rcApiLevel": 7, "profiles": ["glance-wear"], "container": "wear:large", "theme": "baked:dark" }"""

private fun inApp(heightDp: Int, content: @Composable () -> Unit) = TemplateDef("in-app", IN_APP, 360, heightDp, content)
private fun launcher(content: @Composable () -> Unit) = TemplateDef("launcher", LAUNCHER, 250, 120, content)
private fun wear(content: @Composable () -> Unit) = TemplateDef("wear-large", WEAR_LARGE, 200, 112, content)

private fun designs(brand: ConferenceBrand, example: String, title: String, header: @Composable () -> Unit,
    session: @Composable () -> Unit, sessionWidget: @Composable () -> Unit, sessionWear: @Composable () -> Unit,
    speaker: @Composable () -> Unit, promo: @Composable () -> Unit) = listOf(
    DesignDef("${brand.series}-header", ConferenceContract, brand, "$title header", example, listOf(inApp(210, header))),
    DesignDef("${brand.series}-session", SessionContract, brand, "$title session", example,
        listOf(inApp(150, session), launcher(sessionWidget), wear(sessionWear))),
    DesignDef("${brand.series}-speaker", SpeakerContract, brand, "$title speaker", example, listOf(inApp(if (brand == Droidcon) 110 else 230, speaker))),
    DesignDef("${brand.series}-promo", PromoContract, brand, "$title promotion", example, listOf(inApp(200, promo)), install = "OnDemand"),
)

val AllDesigns: List<DesignDef> =
    designs(Droidcon, "default", "droidcon", { DroidconHeader() }, { DroidconSession() }, { DroidconSessionWidget() },
        { DroidconSessionWear() }, { DroidconSpeaker() }, { DroidconPromo() }) +
    designs(KotlinConf, "kotlinconf", "KotlinConf", { KotlinConfHeader() }, { KotlinConfSession() }, { KotlinConfSessionWidget() },
        { KotlinConfSessionWear() }, { KotlinConfSpeaker() }, { KotlinConfPromo() })
