package dev.johnoreilly.confetti.ui.livery

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import dev.johnoreilly.confetti.decompose.SessionsUiState
import dev.johnoreilly.confetti.fragment.SessionDetails
import dev.johnoreilly.confetti.fragment.SpeakerDetails
import dev.johnoreilly.confetti.livery.ConferenceCard
import dev.johnoreilly.confetti.livery.ConferenceContract
import dev.johnoreilly.confetti.livery.SessionCard
import dev.johnoreilly.confetti.livery.SessionContract
import dev.johnoreilly.confetti.livery.SpeakerCard
import dev.johnoreilly.confetti.livery.SpeakerContract
import kotlinx.datetime.LocalDateTime
import sessionTimeFormat

/*
 * Confetti's data, copied onto the contracts' data classes (livery-contracts/ContractData.kt).
 * These mappings are the only place the app's model (Apollo's fragments) meets a design, and the
 * native composables are handed a SlotTracker so both arms of the sdui experiment report taps
 * under the same contract action names.
 */

/** The conference header, from the agenda's state. */
fun SessionsUiState.Success.toConferenceCard(ticketsUrl: String? = null): ConferenceCard? {
    val dates = confDates.sorted()
    val first = dates.firstOrNull() ?: return null
    val last = dates.last()
    val dateText = if (first == last) "${first.day} ${first.month.name.take(3)} ${first.year}"
        else "${first.day}–${last.day} ${last.month.name.take(3)} ${last.year}"
    return ConferenceCard(name = conferenceName, dates = dateText, live = now.date in dates, ticketsUrl = ticketsUrl)
}

fun SessionDetails.toSessionCard(now: LocalDateTime, isBookmarked: Boolean) = SessionCard(
    title = title,
    speakers = speakers.joinToString { it.sessionSpeakerDetails.name },
    room = room?.name,
    time = "${startsAt.sessionTimeFormat()} – ${endsAt.sessionTimeFormat()}",
    tag = tags.firstOrNull(),
    live = now >= startsAt && now < endsAt,
    bookmarked = isBookmarked,
)

fun SpeakerDetails.toSpeakerCard() = SpeakerCard(
    name = name,
    tagline = listOfNotNull(tagline, company).filter { it.isNotBlank() }.joinToString(" · "),
    photoUrl = photoUrlThumbnail ?: photoUrl,
    sessions = when (val n = sessions.size) {
        0 -> null
        1 -> "1 session"
        else -> "$n sessions"
    },
)

/**
 * The conference header above the agenda; nothing (the agenda as today) without a design, or in
 * the native arm. The designs draw their tickets button only when there is a ticketsUrl
 * (Confetti's data has none yet).
 */
@Composable
fun ConferenceHeaderSlot(state: SessionsUiState.Success, ticketsUrl: String? = null) {
    val card = state.toConferenceCard(ticketsUrl) ?: return
    val uriHandler = LocalUriHandler.current
    ConferenceSlot(
        conference = state.conference,
        data = card,
        actions = mapOf(ConferenceContract.tickets to { args -> args["url"]?.let(uriHandler::openUri) }),
    ) {}
}

/**
 * A talk in the agenda, in the conference's session design, or [fallback] (Confetti's row),
 * handed the tracker to report its taps with.
 */
@Composable
fun SessionCardSlot(
    conference: String,
    session: SessionDetails,
    now: LocalDateTime,
    isBookmarked: Boolean,
    onOpen: () -> Unit,
    onToggleBookmark: () -> Unit,
    fallback: @Composable (SlotTracker) -> Unit,
) {
    ConferenceSlot(
        conference = conference,
        data = session.toSessionCard(now, isBookmarked),
        identity = session.id,
        actions = mapOf(
            SessionContract.open to { _ -> onOpen() },
            SessionContract.bookmark to { _ -> onToggleBookmark() },
        ),
        fallback = fallback,
    )
}

/** A speaker, in the conference's speaker design, or [fallback]. */
@Composable
fun SpeakerCardSlot(
    conference: String,
    speaker: SpeakerDetails,
    onOpen: () -> Unit = {},
    fallback: @Composable (SlotTracker) -> Unit = {},
) {
    ConferenceSlot(
        conference = conference,
        data = speaker.toSpeakerCard(),
        identity = speaker.id,
        actions = mapOf(SpeakerContract.open to { _ -> onOpen() }),
        fallback = fallback,
    )
}
