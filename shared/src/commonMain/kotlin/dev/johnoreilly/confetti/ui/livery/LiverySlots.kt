package dev.johnoreilly.confetti.ui.livery

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import dev.johnoreilly.confetti.livery.Action
import dev.johnoreilly.confetti.livery.ConferenceSeries
import dev.johnoreilly.confetti.livery.ConfettiContract
import dev.johnoreilly.confetti.livery.Input
import dev.johnoreilly.confetti.livery.PromoContract
import ee.schimke.livery.client.ActionRouter
import ee.schimke.livery.client.Bindings
import ee.schimke.livery.client.Clock
import ee.schimke.livery.client.Decider
import ee.schimke.livery.client.DecisionContext
import ee.schimke.livery.client.EventSink
import ee.schimke.livery.client.ImageResolver
import ee.schimke.livery.client.LocalRulesDecider
import ee.schimke.livery.client.MemoryFrequencyStore
import ee.schimke.livery.client.PlanResult
import ee.schimke.livery.client.SlotPlanner
import ee.schimke.livery.client.TemplateQuery
import ee.schimke.livery.client.TemplateRepository
import ee.schimke.livery.compose.LiveryHost
import ee.schimke.livery.compose.LiverySlot
import ee.schimke.livery.protocol.PlayerCapabilities
import ee.schimke.livery.protocol.Surfaces

/**
 * What a platform hands the shared UI to draw conference-styled slots. Absent (the default), every
 * slot draws Confetti's own UI, so a platform opts in by providing [LocalLiveryStyling] and nothing
 * else changes.
 */
class LiveryStyling(
    val repository: TemplateRepository,
    val clock: Clock,
    /** `android`, `ios`, `desktop` or `web`: what placement rules target. */
    val platform: String,
    val locale: String,
    val appVersion: Long,
    /** A stable, app-scoped id for sticky rollouts; never an account id. */
    val bucketingId: String,
    val events: EventSink = EventSink {},
    val images: ImageResolver? = null,
    val capabilities: PlayerCapabilities = InApp,
    /** Hosts an `open-url` action may open. */
    val urlAllowlist: Set<String> = setOf("droidcon.com", "kotlinconf.com", "jetbrains.com", "confetti-app.dev"),
    /** Placement rules from the catalog slots draw from: downloaded, else the bundled copy. */
    val decider: Decider = LocalRulesDecider({ repository.current() }, MemoryFrequencyStore()),
) {
    val planner = SlotPlanner(repository, events, clock)

    /** The conference's theme pack, pushed beside the data: the brand colours an admin can change as data. */
    suspend fun theme(series: String): Bindings = repository.theme(series) ?: Bindings.EMPTY

    /** Whether an `open-url` may go to [url]: https only, by the host it really reaches (Livery's own parser). */
    fun allowsUrl(url: String): Boolean = urlAllowed(url, urlAllowlist)

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

/** What a slot does with each action kind its contract declares. Kinds, not names: see Contracts.kt. */
typealias ActionHandlers = Map<String, suspend (args: Map<String, String>) -> Unit>

/**
 * One contract-typed slot for one conference: drawn in the conference's design when Livery has one
 * that plays here, otherwise [fallback]. [data] is keyed by the contract's own [Input]s, so it
 * can't carry a field the designs don't know.
 */
@Composable
fun ConferenceSlot(
    contract: ConfettiContract,
    conference: String,
    data: Map<Input, Any>,
    modifier: Modifier = Modifier.fillMaxWidth(),
    actions: ActionHandlers = emptyMap(),
    fallback: @Composable () -> Unit,
) {
    val styling = LocalLiveryStyling.current
    val series = ConferenceSeries.of(conference)
    if (styling == null || series == null) {
        fallback()
        return
    }
    val json = remember(contract, data) { contract.data(data) }
    val host = rememberHost(styling, actions)
    LiverySlot(key = json, host = host, modifier = modifier, fallback = { fallback() }) {
        styling.planner.forAppData(styling.query(contract, series), json, extra = styling.theme(series))
    }
}

/**
 * A growth placement (`home.feed`, `session.footer`, `speaker.footer`): whatever the catalog's
 * rules, or later a promotion server, decide for this conference's attendees right now. Draws
 * nothing when nothing is decided, when it is dismissed, or on a platform without Livery.
 */
@Composable
fun PromoSlot(
    placement: String,
    conference: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    segments: Set<String> = emptySet(),
) {
    val styling = LocalLiveryStyling.current ?: return
    val series = ConferenceSeries.of(conference) ?: return
    var dismissed by rememberSaveable(placement, conference) { mutableStateOf(false) }
    if (dismissed) return
    val uriHandler = LocalUriHandler.current
    val host = rememberHost(styling, mapOf(
        PromoContract.ctaAction.kind to { args -> args["url"]?.let(uriHandler::openUri) },
        PromoContract.dismiss.kind to { _ -> dismissed = true },
    ))
    LiverySlot(key = placement to conference, host = host, modifier = modifier) {
        val context = DecisionContext(
            nowEpochMs = styling.clock.nowEpochMs(), platform = styling.platform, locale = styling.locale,
            appVersion = styling.appVersion, bucketingId = styling.bucketingId,
            segments = segments + ConferenceSeries.segment(conference),
        )
        val decision = styling.decider.decide(placement, context) ?: return@LiverySlot PlanResult.Fallback("no decision for $placement")
        styling.planner.forDecision(styling.query(PromoContract, series), decision, extra = styling.theme(series))
    }
}

private fun LiveryStyling.query(contract: ConfettiContract, series: String) =
    TemplateQuery(contract.ref, capabilities, tags = mapOf(ConferenceSeries.TAG to series), minor = contract.minor)

@Composable
private fun rememberHost(styling: LiveryStyling, actions: ActionHandlers): LiveryHost {
    // The latest handlers, so a recomposition with new lambdas never rebuilds the host.
    val current by rememberUpdatedState(actions)
    return remember(styling) {
        val kinds = listOf("app:open-session", "app:toggle-bookmark", "app:open-speaker", "open-url", "dismiss")
        LiveryHost(
            repository = styling.repository,
            actions = ActionRouter(
                handlers = kinds.associateWith { kind -> { _, _, args -> current[kind]?.invoke(args) ?: Unit } },
                urlAllowlist = styling::allowsUrl,
                events = styling.events,
                clock = styling.clock,
            ),
            events = styling.events,
            clock = styling.clock,
            images = styling.images,
        )
    }
}

