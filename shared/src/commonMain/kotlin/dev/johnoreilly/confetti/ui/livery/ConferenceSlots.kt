package dev.johnoreilly.confetti.ui.livery

import androidx.compose.runtime.Composable
import dev.johnoreilly.confetti.decompose.SessionsUiState
import dev.johnoreilly.confetti.fragment.SessionDetails
import dev.johnoreilly.confetti.fragment.SpeakerDetails
import dev.johnoreilly.confetti.livery.ConferenceContract
import dev.johnoreilly.confetti.livery.Input
import dev.johnoreilly.confetti.livery.SessionContract
import dev.johnoreilly.confetti.livery.SpeakerContract
import dev.johnoreilly.confetti.livery.fit
import kotlinx.datetime.LocalDateTime
import sessionTimeFormat

/*
 * Confetti's data, mapped onto the Livery contracts. These are the only places the app's model
 * meets a design: the contract types keep both sides honest (see livery-contracts/Contracts.kt).
 */

/** The conference header above the agenda; nothing (the agenda as today) without a design. */
@Composable
fun ConferenceHeaderSlot(state: SessionsUiState.Success) {
    val dates = state.confDates.sorted()
    val first = dates.firstOrNull() ?: return
    val last = dates.last()
    val c = ConferenceContract
    val dateText = if (first == last) "${first.day} ${first.month.name.take(3)} ${first.year}"
        else "${first.day}–${last.day} ${last.month.name.take(3)} ${last.year}"
    ConferenceSlot(
        contract = c,
        conference = state.conference,
        data = buildMap {
            put(c.name, c.name.fit(state.conferenceName))
            put(c.dates, c.dates.fit(dateText))
            put(c.live, state.now.date in dates)
        },
    ) {}
}

/** A talk in the agenda, in the conference's session design, or [fallback] (Confetti's row). */
@Composable
fun SessionCardSlot(
    conference: String,
    session: SessionDetails,
    now: LocalDateTime,
    isBookmarked: Boolean,
    onOpen: () -> Unit,
    onToggleBookmark: () -> Unit,
    fallback: @Composable () -> Unit,
) {
    val c = SessionContract
    ConferenceSlot(
        contract = c,
        conference = conference,
        data = sessionData(session, now, isBookmarked),
        actions = mapOf(
            c.open.kind to { _ -> onOpen() },
            c.bookmark.kind to { _ -> onToggleBookmark() },
        ),
        fallback = fallback,
    )
}

fun sessionData(session: SessionDetails, now: LocalDateTime, isBookmarked: Boolean): Map<Input, Any> {
    val c = SessionContract
    return buildMap {
        put(c.title, c.title.fit(session.title))
        session.speakers.joinToString { it.sessionSpeakerDetails.name }.takeIf { it.isNotBlank() }
            ?.let { put(c.speakers, c.speakers.fit(it)) }
        session.room?.name?.takeIf { it.isNotBlank() }?.let { put(c.room, c.room.fit(it)) }
        put(c.time, c.time.fit("${session.startsAt.sessionTimeFormat()} – ${session.endsAt.sessionTimeFormat()}"))
        session.tags.firstOrNull()?.let { put(c.tag, c.tag.fit(it)) }
        put(c.live, now >= session.startsAt && now < session.endsAt)
        put(c.bookmarked, isBookmarked)
    }
}

/** A speaker, in the conference's speaker design, or [fallback]. */
@Composable
fun SpeakerCardSlot(
    conference: String,
    speaker: SpeakerDetails,
    onOpen: () -> Unit = {},
    fallback: @Composable () -> Unit = {},
) {
    val c = SpeakerContract
    ConferenceSlot(
        contract = c,
        conference = conference,
        data = buildMap {
            put(c.name, c.name.fit(speaker.name))
            listOfNotNull(speaker.tagline, speaker.company).filter { it.isNotBlank() }.joinToString(" · ")
                .takeIf { it.isNotBlank() }?.let { put(c.tagline, c.tagline.fit(it)) }
            // Only https images bind (Livery PROTOCOL §6.1); anything else draws the placeholder.
            (speaker.photoUrlThumbnail ?: speaker.photoUrl)?.takeIf { it.startsWith("https://") }?.let { put(c.photo, it) }
            when (val n = speaker.sessions.size) {
                0 -> {}
                1 -> put(c.sessions, "1 session")
                else -> put(c.sessions, "$n sessions")
            }
        },
        actions = mapOf(c.open.kind to { _ -> onOpen() }),
        fallback = fallback,
    )
}
