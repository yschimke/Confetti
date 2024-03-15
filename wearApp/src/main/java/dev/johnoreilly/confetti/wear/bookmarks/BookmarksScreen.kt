package dev.johnoreilly.confetti.wear.bookmarks

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults.ItemType
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults.listTextPadding
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults.padding
import com.google.android.horologist.compose.layout.ScalingLazyColumnState
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import dev.johnoreilly.confetti.R
import dev.johnoreilly.confetti.utils.QueryResult
import dev.johnoreilly.confetti.wear.components.SectionHeader
import dev.johnoreilly.confetti.wear.components.SessionCard
import dev.johnoreilly.confetti.wear.preview.TestFixtures
import dev.johnoreilly.confetti.wear.ui.ConfettiThemeFixed
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.LocalDateTime


@Composable
fun BookmarksScreen(
    uiState: QueryResult<BookmarksUiState>,
    sessionSelected: (String) -> Unit,
    addBookmark: (sessionId: String) -> Unit,
    removeBookmark: (sessionId: String) -> Unit,
) {
    val columnState: ScalingLazyColumnState = rememberResponsiveColumnState(
        contentPadding = padding(
            first = ItemType.Text,
            last = ItemType.Card
        )
    )

    ScreenScaffold(scrollState = columnState) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            columnState = columnState,
        ) {
            when (uiState) {
                is QueryResult.Success -> {
                    item { SectionHeader(text = stringResource(R.string.upcoming_sessions)) }

                    items(uiState.result.upcoming) { session ->
                        SessionCard(
                            session = session,
                            sessionSelected = {
                                sessionSelected(it)
                            },
                            currentTime = uiState.result.now,
                            isBookmarked = true,
                            addBookmark = {},
                            removeBookmark = {}
                        )
                    }

                    if (!uiState.result.hasUpcomingBookmarks) {
                        item {
                            Text(
                                stringResource(id = R.string.no_upcoming),
                                modifier = Modifier.listTextPadding()
                            )
                        }
                    }

                    item { SectionHeader(text = stringResource(id = R.string.past_sessions)) }

                    items(uiState.result.past) { session ->
                        SessionCard(
                            session = session,
                            sessionSelected = {
                                sessionSelected(it)
                            },
                            currentTime = uiState.result.now,
                            isBookmarked = true,
                            addBookmark = {},
                            removeBookmark = {}
                        )
                    }

                    if (uiState.result.past.isEmpty()) {
                        item {
                            Text(
                                stringResource(id = R.string.no_past),
                                modifier = Modifier.listTextPadding()
                            )
                        }
                    }
                }

                else -> {
                    // TODO
                }
            }
        }
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun BookmarksPreview() {
    ConfettiThemeFixed {
        BookmarksScreen(
            uiState = QueryResult.Success(
                BookmarksUiState(
                    conference = TestFixtures.kotlinConf2023.id,
                    upcoming = listOf(TestFixtures.sessionDetails),
                    past = listOf(),
                    now = LocalDateTime.of(2022, 1, 1, 1, 1).toKotlinLocalDateTime()
                )
            ),
            sessionSelected = {},
            addBookmark = {},
            removeBookmark = {}
        )
    }
}
