package dev.johnoreilly.confetti.ui.livery

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler
import dev.johnoreilly.confetti.livery.Action
import dev.johnoreilly.confetti.livery.ConferenceSeries
import dev.johnoreilly.confetti.livery.ConfettiContract
import dev.johnoreilly.confetti.livery.ContractData
import dev.johnoreilly.confetti.livery.PromoContract
import dev.johnoreilly.confetti.livery.SduiExperiment
import dev.johnoreilly.confetti.livery.materialThemeValues
import dev.johnoreilly.confetti.ui.LocalDynamicColorInUse
import ee.schimke.livery.client.ActionRouter
import ee.schimke.livery.client.Bindings
import ee.schimke.livery.client.Clock
import ee.schimke.livery.client.DecisionContext
import ee.schimke.livery.client.EventSink
import ee.schimke.livery.client.Experiments
import ee.schimke.livery.client.ImageResolver
import ee.schimke.livery.client.LocalBucketing
import ee.schimke.livery.client.LocalRulesDecider
import ee.schimke.livery.client.MemoryFrequencyStore
import ee.schimke.livery.client.PlanResult
import ee.schimke.livery.client.SlotPlanner
import ee.schimke.livery.client.TemplateQuery
import ee.schimke.livery.client.TemplateRepository
import ee.schimke.livery.compose.LiveryHost
import ee.schimke.livery.compose.LiverySlot
import ee.schimke.livery.protocol.NamedValue
import ee.schimke.livery.protocol.PlayerCapabilities
import ee.schimke.livery.protocol.Surfaces
import ee.schimke.livery.protocol.TemplateEvent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What a platform hands the shared UI to draw conference-styled slots. Absent (the default), every
 * slot draws Confetti's own UI, so a platform opts in by providing [LocalLiveryStyling] and nothing
 * else changes.
 *
 * Each conference series is its own Livery catalog ([catalogs]): published to its own URL and
 * bundled into the app as its own copy, so one conference's designs, theme packs, promotions and
 * experiments ship (or break) without touching another's.
 */
class LiveryStyling(
    /** Series (`droidcon`, `kotlinconf`) → its catalog: downloaded when it can be, else bundled. */
    catalogs: Map<String, TemplateRepository>,
    val clock: Clock,
    /** `android`, `ios`, `desktop` or `web`: what placement rules and experiments target. */
    val platform: String,
    val locale: String,
    val appVersion: Long,
    /** A stable, app-scoped id for sticky rollouts and arms; never an account id. */
    val bucketingId: String,
    /** Every slot's events: Livery's analytics shape, a log, a test probe. */
    val events: EventSink = EventSink {},
    val images: (TemplateRepository) -> ImageResolver? = { null },
    val capabilities: PlayerCapabilities = InApp,
    /** Hosts an `open-url` action may open. */
    val urlAllowlist: Set<String> = setOf("droidcon.com", "kotlinconf.com", "jetbrains.com", "confetti-app.dev"),
    /** Whether slots ask each catalog's server for a newer catalog (at most every [refreshEveryMs]). */
    val refresh: Boolean = true,
    val refreshEveryMs: Long = 5 * 60_000L,
    /** A forced arm of [SduiExperiment] (a debug setting, a test); null lets the catalog's experiment decide. */
    val forcedArm: String? = null,
) {
    val catalogs: Map<String, SeriesCatalog> = catalogs.mapValues { (series, repo) -> SeriesCatalog(series, repo) }

    fun catalog(conference: String): SeriesCatalog? = ConferenceSeries.of(conference)?.let { catalogs[it] }

    fun context(conference: String, segments: Set<String> = emptySet()) = DecisionContext(
        nowEpochMs = clock.nowEpochMs(), platform = platform, locale = locale, appVersion = appVersion,
        bucketingId = bucketingId, segments = segments + ConferenceSeries.segment(conference),
    )

    /** Whether an `open-url` may go to [url]: https only, by the host it really reaches (Livery's own parser). */
    fun allowsUrl(url: String): Boolean = urlAllowed(url, urlAllowlist)

    /** One series' catalog and everything planned from it. */
    inner class SeriesCatalog(val series: String, val repository: TemplateRepository) {
        /**
         * The catalog's experiments, assigned on the device (the same arm on every platform for the
         * same [bucketingId]). Every slot event goes through [Experiments.sink], which adds the
         * arm's exposure the first time it draws.
         */
        val experiments = Experiments(
            manifest = { repository.current() },
            context = { context(conference = series) },
            assigner = LocalBucketing,
            events = events,
        )
        val planner = SlotPlanner(repository, experiments.sink, clock, experiments)
        val decider = LocalRulesDecider({ repository.current() }, MemoryFrequencyStore(), LocalBucketing)
        val imageResolver: ImageResolver? = images(repository)

        private val refreshLock = Mutex()
        private var lastRefreshEpochMs = Long.MIN_VALUE / 2

        /**
         * Asks the server for a newer catalog, at most every [refreshEveryMs]. The bundled copy has
         * serial 0, so anything downloaded wins; slots replan on the repository's revision, so a
         * server design replaces a bundled one while the screen is open.
         */
        suspend fun refreshIfDue() {
            if (!refresh) return
            refreshLock.withLock {
                val now = clock.nowEpochMs()
                if (now - lastRefreshEpochMs < refreshEveryMs) return
                lastRefreshEpochMs = now
            }
            runCatching { repository.refresh() }
        }

        /** The user's arm of [SduiExperiment] for [conference]: [SduiExperiment.LIVERY] when not enrolled. */
        suspend fun sduiArm(conference: String): String =
            forcedArm ?: runCatching { experimentsFor(conference).arm(SduiExperiment.ID) }.getOrNull() ?: SduiExperiment.LIVERY

        // Arms are per conference (segment conditions), so the context carries the conference.
        private fun experimentsFor(conference: String) =
            Experiments({ repository.current() }, { context(conference) }, LocalBucketing)

        fun theme(dark: Boolean): suspend () -> Bindings? = { repository.theme(ConferenceSeries.themePack(series, dark)) ?: repository.theme(series) }

        fun query(contract: ConfettiContract) =
            TemplateQuery(contract.ref, capabilities, tags = mapOf(ConferenceSeries.TAG to series), minor = contract.minor)

        /**
         * Events tagged with the arm that drew them, through [Experiments.sink]: an `Impression`
         * reports the arm's exposure once, and every tap and leave carries `exp_sdui`.
         */
        fun events(arm: String): EventSink = EventSink { e ->
            experiments.sink.emit(e.copy(experiments = e.experiments + (SduiExperiment.ID to arm)))
        }
    }

    companion object {
        val InApp = PlayerCapabilities(Surfaces.IN_APP, maxRcApiLevel = 7, profiles = setOf("androidx"))
    }
}

/** [url] is https and the host it really reaches is one of [hosts] or under one of them. */
internal fun urlAllowed(url: String, hosts: Set<String>): Boolean {
    val host = ImageResolver.remoteHost(url) ?: return false
    return hosts.any { host == it || host.endsWith(".$it") }
}

val LocalLiveryStyling = staticCompositionLocalOf<LiveryStyling?> { null }

/** What a slot does with each contract action, by the action itself (not a string). */
typealias ActionHandlers = Map<Action, suspend (args: Map<String, String>) -> Unit>

/**
 * How a native composable reports what the user did, under the contract's action names: a tap on
 * Confetti's own session row is `open`, the same as a tap on a design's card. With it, both arms of
 * [SduiExperiment] are counted alike. [None] outside Livery.
 */
fun interface SlotTracker {
    fun action(action: Action, args: Map<String, String>)

    fun action(action: Action) = action(action, emptyMap())

    companion object {
        val None = SlotTracker { _, _ -> }
    }
}

/**
 * One contract-typed slot for one conference: the conference's design when Livery has one that
 * plays here and the user is in the [SduiExperiment.LIVERY] arm, otherwise [fallback] (Confetti's
 * own composable), handed a [SlotTracker] for its taps. [data] is the contract's own data class,
 * so it can't carry a field the designs don't know.
 */
@Composable
fun ConferenceSlot(
    conference: String,
    data: ContractData,
    modifier: Modifier = Modifier.fillMaxWidth(),
    actions: ActionHandlers = emptyMap(),
    fallback: @Composable (SlotTracker) -> Unit,
) {
    val styling = LocalLiveryStyling.current
    val catalog = styling?.catalog(conference)
    if (styling == null || catalog == null) {
        fallback(SlotTracker.None)
        return
    }
    LaunchedEffect(catalog) { catalog.refreshIfDue() }
    val revision by catalog.repository.revision.collectAsState()
    val arm by produceState<String?>(null, catalog, conference, revision) { value = catalog.sduiArm(conference) }
    val contract = data.contract
    when (val a = arm) {
        // Deciding: draw what the slot would fall back to, untracked.
        null -> fallback(SlotTracker.None)
        SduiExperiment.NATIVE -> NativeSlot(styling, catalog, contract, a, fallback)
        else -> {
            val json = remember(data) { data.toJson() }
            val host = rememberHost(styling, catalog, a, actions)
            val colors = rememberSlotColors()
            // When this conference has no template for the slot, the composable draws, still tracked.
            val native = remember(catalog, contract, a) { nativeTracker(styling, catalog, contract, a) }
            // Re-planned when the data or the colours change: a mode switch or a new wallpaper recolours live.
            LiverySlot(key = json to colors, host = host, modifier = modifier, fallback = { fallback(native) }) {
                catalog.planner.forAppData(catalog.query(contract), json, extra = colors.app ?: catalog.theme(colors.dark)() ?: Bindings.EMPTY)
            }
        }
    }
}

/**
 * The native arm: Confetti's composable, reporting the impression a template would and its taps
 * (the composable calls the app's callbacks directly, and the [SlotTracker] beside them).
 */
@Composable
private fun NativeSlot(
    styling: LiveryStyling,
    catalog: LiveryStyling.SeriesCatalog,
    contract: ConfettiContract,
    arm: String,
    content: @Composable (SlotTracker) -> Unit,
) {
    val tracker = remember(catalog, contract, arm) { nativeTracker(styling, catalog, contract, arm) }
    LaunchedEffect(tracker) {
        catalog.events(arm).emit(TemplateEvent(
            TemplateEvent.Type.Impression, styling.clock.nowEpochMs(), designId = SduiExperiment.NATIVE_DESIGN, detail = contract.ref,
        ))
    }
    content(tracker)
}

private fun nativeTracker(
    styling: LiveryStyling,
    catalog: LiveryStyling.SeriesCatalog,
    contract: ConfettiContract,
    arm: String,
): SlotTracker {
    val events = catalog.events(arm)
    return SlotTracker { action, args ->
        require(contract.actions[action.name] === action) { "${action.name} is not an action of ${contract.ref}" }
        // Only an argument the contract declares reaches analytics, as with a template (PROTOCOL §7).
        val declared = args.filterKeys { it == action.arg }
        events.emit(TemplateEvent(
            TemplateEvent.Type.Action, styling.clock.nowEpochMs(), designId = SduiExperiment.NATIVE_DESIGN,
            action = action.name, detail = contract.ref, args = declared,
        ))
    }
}

/**
 * A growth placement (`home.feed`, `session.footer`, `speaker.footer`): whatever the conference
 * catalog's rules decide for its attendees right now. Draws nothing when nothing is decided, when
 * it is dismissed, in the [SduiExperiment.NATIVE] arm, or on a platform without Livery.
 */
@Composable
fun PromoSlot(
    placement: String,
    conference: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    segments: Set<String> = emptySet(),
) {
    val styling = LocalLiveryStyling.current ?: return
    val catalog = styling.catalog(conference) ?: return
    var dismissed by rememberSaveable(placement, conference) { mutableStateOf(false) }
    if (dismissed) return
    LaunchedEffect(catalog) { catalog.refreshIfDue() }
    val revision by catalog.repository.revision.collectAsState()
    val arm by produceState<String?>(null, catalog, conference, revision) { value = catalog.sduiArm(conference) }
    val a = arm ?: return
    if (a == SduiExperiment.NATIVE) return
    val uriHandler = LocalUriHandler.current
    val host = rememberHost(styling, catalog, a, mapOf(
        PromoContract.ctaAction to { args -> args["url"]?.let(uriHandler::openUri) },
        PromoContract.dismiss to { _ -> dismissed = true },
    ))
    val colors = rememberSlotColors()
    LiverySlot(key = Triple(placement, conference, colors), host = host, modifier = modifier) {
        val decision = catalog.decider.decide(placement, styling.context(conference, segments))
            ?: return@LiverySlot PlanResult.Fallback("no decision for $placement")
        catalog.planner.forDecision(catalog.query(PromoContract), decision, extra = colors.app ?: catalog.theme(colors.dark)() ?: Bindings.EMPTY)
    }
}

/**
 * Whether the app around a slot is dark, and the user's Material 3 colours when dynamic colour is
 * in use ([LocalDynamicColorInUse]); read from the [MaterialTheme] the slot sits in, so the app's
 * own light/dark setting wins over the system's.
 */
private data class SlotColors(val dark: Boolean, val app: Bindings?)

@Composable
private fun rememberSlotColors(): SlotColors {
    val scheme = MaterialTheme.colorScheme
    val dynamic = LocalDynamicColorInUse.current
    return remember(scheme, dynamic) {
        SlotColors(dark = scheme.surface.luminance() < 0.5f, app = if (dynamic) scheme.toLiveryTheme() else null)
    }
}

/** A Material 3 scheme as the designs' theme roles (the catalog's renders use the same mapping). */
fun ColorScheme.toLiveryTheme(): Bindings {
    fun Color.argb() = toArgb().toLong() and 0xFFFFFFFFL
    return Bindings(materialThemeValues(
        primary = primary.argb(), onPrimary = onPrimary.argb(), secondary = secondary.argb(),
        tertiary = tertiary.argb(), surface = surfaceContainerHigh.argb(), onSurface = onSurface.argb(),
        onSurfaceVariant = onSurfaceVariant.argb(),
    ).mapValues { NamedValue.Color(it.value) })
}

/** Every action kind a Confetti contract declares; the router dispatches by kind (PROTOCOL §7). */
private val ActionKinds = listOf("app:open-session", "app:toggle-bookmark", "app:open-speaker", "open-url", "dismiss")

@Composable
private fun rememberHost(styling: LiveryStyling, catalog: LiveryStyling.SeriesCatalog, arm: String, actions: ActionHandlers): LiveryHost {
    // The latest handlers, so a recomposition with new lambdas never rebuilds the host.
    val current by rememberUpdatedState(actions)
    return remember(styling, catalog, arm) {
        val events = catalog.events(arm)
        LiveryHost(
            repository = catalog.repository,
            actions = ActionRouter(
                handlers = ActionKinds.associateWith { kind ->
                    { _, _, args -> current.entries.firstOrNull { it.key.kind == kind }?.value?.invoke(args) ?: Unit }
                },
                urlAllowlist = styling::allowsUrl,
                events = events,
                clock = styling.clock,
            ),
            events = events,
            clock = styling.clock,
            images = catalog.imageResolver,
        )
    }
}
