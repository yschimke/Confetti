@file:OptIn(ExperimentalWearMaterialApi::class)

package dev.johnoreilly.confetti.wear.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ChipDefaults
import androidx.wear.compose.material3.ExperimentalWearMaterialApi
import androidx.wear.compose.material3.OutlinedChip
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.placeholder
import androidx.wear.compose.material3.rememberPlaceholderState
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import dev.johnoreilly.confetti.R
import dev.johnoreilly.confetti.utils.QueryResult
import dev.johnoreilly.confetti.wear.bookmarks.BookmarksUiState
import dev.johnoreilly.confetti.wear.components.ScreenHeader
import dev.johnoreilly.confetti.wear.components.SectionHeader
import dev.johnoreilly.confetti.wear.components.SessionCard
import dev.johnoreilly.confetti.wear.preview.TestFixtures
import dev.johnoreilly.confetti.wear.ui.ConfettiThemeFixed
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    uiState: QueryResult<HomeUiState>,
    bookmarksUiState: QueryResult<BookmarksUiState>,
    sessionSelected: (String) -> Unit,
    daySelected: (LocalDate) -> Unit,
    onSettingsClick: () -> Unit,
    onBookmarksClick: () -> Unit,
) {
    val dayFormatter = remember { DateTimeFormatter.ofPattern("cccc") }

    val columnState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = columnState) {
        SectionedList(
            modifier = Modifier.fillMaxSize(),
            columnState = columnState,
        ) {
            titleSection(uiState)

            bookmarksSection(uiState, bookmarksUiState, sessionSelected, onBookmarksClick)

            conferenceDaysSection(uiState, daySelected, dayFormatter)

            bottomMenuSection(onSettingsClick)
        }
    }
}

private fun SectionedListScope.titleSection(uiState: QueryResult<HomeUiState>) {
    val titleSectionState = when (uiState) {
        is QueryResult.Success -> Section.State.Loaded(listOf(uiState.result.conferenceName))
        QueryResult.Loading -> Section.State.Loading
        is QueryResult.Error -> Section.State.Failed
        QueryResult.None -> Section.State.Empty
    }

    section(state = titleSectionState) {
        loaded { conferenceName ->
            ScreenHeader(conferenceName)
        }

        loading {
            val chipPlaceholderState = rememberPlaceholderState { false }
            ScreenHeader(
                "",
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .placeholder(chipPlaceholderState)
            )
        }
    }
}

private fun SectionedListScope.bookmarksSection(
    uiState: QueryResult<HomeUiState>,
    bookmarksUiState: QueryResult<BookmarksUiState>,
    sessionSelected: (String) -> Unit,
    onBookmarksClick: () -> Unit
) {
    val bookmarksSectionState = when (bookmarksUiState) {
        is QueryResult.Success -> {
            if (bookmarksUiState.result.hasUpcomingBookmarks) {
                Section.State.Loaded(bookmarksUiState.result.upcoming.take(3))
            } else {
                Section.State.Empty
            }
        }

        QueryResult.Loading -> Section.State.Loading
        is QueryResult.Error -> Section.State.Failed
        QueryResult.None -> Section.State.Failed // handling "None" as a failure
    }

    section(state = bookmarksSectionState) {
        header(visibleStates = ALL_STATES.copy(failed = false)) {
            SectionHeader(stringResource(R.string.home_bookmarked_sessions))
        }

        loaded { session ->
            key(session.id) {
                SessionCard(session, sessionSelected = {
                    if (uiState is QueryResult.Success) {
                        sessionSelected(it)
                    }
                }, (bookmarksUiState as QueryResult.Success).result.now)
            }
        }

        // TODO placeholders
        // loading {}

        empty {
            Text(
                stringResource(id = R.string.no_upcoming),
                modifier = Modifier.listTextPadding()
            )
        }


        footer(visibleStates = NO_STATES.copy(loaded = true, empty = true)) {
            OutlinedChip(
                label = { Text(stringResource(id = R.string.all_bookmarks)) },
                onClick = {
                    if (uiState is QueryResult.Success) {
                        onBookmarksClick()
                    }
                }
            )
        }
    }
}

private fun SectionedListScope.conferenceDaysSection(
    uiState: QueryResult<HomeUiState>,
    daySelected: (LocalDate) -> Unit,
    dayFormatter: DateTimeFormatter
) {
    val conferenceDaysSectionState = when (uiState) {
        is QueryResult.Success -> Section.State.Loaded(uiState.result.confDates)
        QueryResult.Loading -> Section.State.Loading
        is QueryResult.Error -> Section.State.Failed
        QueryResult.None -> Section.State.Empty
    }

    section(state = conferenceDaysSectionState) {
        header {
            SectionHeader(stringResource(id = R.string.conference_days))
        }

        loaded { date ->
            DayChip(dayFormatter, date, daySelected = { daySelected(date) })
        }

        loading(count = 2) {
            PlaceholderChip(contentDescription = "")
        }
    }
}

@Composable
fun DayChip(
    dayFormatter: DateTimeFormatter,
    date: LocalDate,
    daySelected: () -> Unit
) {
    Chip(
        label = dayFormatter.format(date.toJavaLocalDate()),
        onClick = daySelected,
        colors = ChipDefaults.secondaryChipColors(),
    )
}

private fun SectionedListScope.bottomMenuSection(onSettingsClick: () -> Unit) {
    section {
        loaded {
            Button(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.home_settings_content_description),
                onClick = onSettingsClick
            )
        }
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun HomeListViewPreview() {
    ConfettiThemeFixed {
        HomeScreen(
            uiState = QueryResult.Success(
                HomeUiState(
                    conference = TestFixtures.kotlinConf2023.id,
                    conferenceName = TestFixtures.kotlinConf2023.name,
                    confDates = TestFixtures.kotlinConf2023.days,
                )
            ),
            bookmarksUiState = QueryResult.Success(
                BookmarksUiState(
                    conference = TestFixtures.kotlinConf2023.id,
                    upcoming = listOf(
                        TestFixtures.sessionDetails
                    ),
                    past = listOf(),
                    now = LocalDateTime.of(2022, 1, 1, 1, 1).toKotlinLocalDateTime()
                )
            ),
            sessionSelected = {},
            onSettingsClick = {},
            onBookmarksClick = {},
            daySelected = {},
        )
    }
}
