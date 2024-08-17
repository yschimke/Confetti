package dev.johnoreilly.confetti.wear.tile

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.app.TaskStackBuilder
import androidx.core.net.toUri
import androidx.wear.compose.material.Text
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.apollographql.apollo.cache.normalized.FetchPolicy
import com.google.android.horologist.tiles.SuspendingTileService
import dev.johnoreilly.confetti.ConfettiRepository
import dev.johnoreilly.confetti.analytics.AnalyticsLogger
import dev.johnoreilly.confetti.auth.Authentication
import dev.johnoreilly.confetti.toTimeZone
import dev.johnoreilly.confetti.wear.settings.PhoneSettingsSync
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toLocalDateTime
import org.koin.android.ext.android.inject
import java.time.Instant

class CurrentSessionsTileService : SuspendingTileService() {
    private val renderer = GraphicsLayerTileRenderer(this)

    private val repository: ConfettiRepository by inject()

    private val analyticsLogger: AnalyticsLogger by inject()

    private val phoneSettingsSync: PhoneSettingsSync by inject()

    private val authentication: Authentication by inject()

    private val tileSync: TileSync by inject()

    override suspend fun resourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ResourceBuilders.Resources {
        println("resourceRequest")
        val bitmap =
            useVirtualDisplay(this@CurrentSessionsTileService) { display ->
                println("useVirtualDisplay")
                captureComposable(this@CurrentSessionsTileService, DpSize(100.dp, 100.dp), display = display) {
                    println("captureComposable")
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Gray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Hello hello")
                    }
                }
            }

        println("produceRequestedResources")

        return renderer.produceRequestedResources(bitmap, requestParams)
    }

    private suspend fun tileState(): ConfettiTileData {
        val user = authentication.currentUser.value

        val conference = phoneSettingsSync.conferenceFlow.first().conference

        if (conference.isBlank()) {
            return ConfettiTileData.NoConference
        }

        val responseData = repository.bookmarkedSessionsQuery(
            conference, user?.uid, user, FetchPolicy.CacheOnly
        ).execute().data

        if (user == null) {
            return ConfettiTileData.NotLoggedIn(
                responseData?.config,
            )
        }

        return if (responseData != null) {
            val timeZone = responseData.config.timezone.toTimeZone()
            val now = Instant.now().toKotlinInstant().toLocalDateTime(timeZone)

            val bookmarks =
                responseData.bookmarkConnection?.nodes?.map { it.sessionDetails }?.filter {
                    it.startsAt > now
                }?.sortedBy { it.startsAt }.orEmpty()

            ConfettiTileData.CurrentSessionsData(responseData.config, bookmarks)
        } else {
            ConfettiTileData.NoConference
        }
    }

    override suspend fun tileRequest(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        val lastClickableId = requestParams.currentState.lastClickableId
        if (lastClickableId.isNotBlank()) {
            handleClick("confetti://confetti/$lastClickableId")
        }

        return renderer.renderTimeline(Unit, requestParams)
    }

    private fun handleClick(uri: String) {
        TaskStackBuilder.create(this)
            .addNextIntentWithParentStack(
                Intent(
                    Intent.ACTION_VIEW,
                    uri.toUri()
                )
            )
            .startActivities()
    }

    override fun onTileAddEvent(requestParams: EventBuilders.TileAddEvent) {
        super.onTileAddEvent(requestParams)

        analyticsLogger.logEvent(TileAnalyticsEvent(TileAnalyticsEvent.Type.Add))
    }

    override fun onTileRemoveEvent(requestParams: EventBuilders.TileRemoveEvent) {
        super.onTileRemoveEvent(requestParams)

        analyticsLogger.logEvent(TileAnalyticsEvent(TileAnalyticsEvent.Type.Remove))
    }

    override fun onTileEnterEvent(requestParams: EventBuilders.TileEnterEvent) {
        super.onTileEnterEvent(requestParams)

        analyticsLogger.logEvent(TileAnalyticsEvent(TileAnalyticsEvent.Type.Enter, getConference()))
    }

    override fun onTileLeaveEvent(requestParams: EventBuilders.TileLeaveEvent) {
        super.onTileLeaveEvent(requestParams)

        analyticsLogger.logEvent(TileAnalyticsEvent(TileAnalyticsEvent.Type.Leave, getConference()))
    }

    private fun getConference(): String = runBlocking {
        // Not ideal, but runs on the Binder Thread
        repository.getConference()
    }
}