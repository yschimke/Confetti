package dev.johnoreilly.confetti.livery

import android.content.Context
import dev.johnoreilly.confetti.BuildConfig
import dev.johnoreilly.confetti.analytics.AnalyticsLogger
import dev.johnoreilly.confetti.ui.livery.LiveryAnalyticsEvent
import dev.johnoreilly.confetti.ui.livery.LiveryStyling
import ee.schimke.livery.client.AnalyticsEventSink
import ee.schimke.livery.client.AnalyticsSink
import ee.schimke.livery.client.AndroidBundled
import ee.schimke.livery.client.Clock
import ee.schimke.livery.client.FileBlobStore
import ee.schimke.livery.client.FileKeyValueStore
import ee.schimke.livery.client.ImageResolver
import ee.schimke.livery.client.JavaCrypto
import ee.schimke.livery.client.KtorFetcher
import ee.schimke.livery.client.StaticSource
import ee.schimke.livery.client.TemplateRepository
import ee.schimke.livery.client.TrustPolicy
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Livery on Android (the sduiDebug build type): one catalog per conference series, each
 *
 * - bundled into the APK's assets at `livery/<series>/` by the build (see build.gradle.kts), so it
 *   draws offline and on first launch;
 * - refreshed from `<LIVERY_TEMPLATES_URL>/<series>/`, the livery-templates branch, which a merge
 *   to main publishes. Anything downloaded has a higher serial than the bundled copy and wins.
 *
 * The branch isn't signed yet, so this debug-only build accepts unsigned manifests (https from one
 * repository). A release build needs `TrustPolicy.Signed` with the public key of the CI's signing
 * key (`livery keygen confetti`, the private half as a CI secret, `--key` in the publish step).
 *
 * Events (impressions, taps under the contract's action names, exposures of the sdui experiment's
 * arms) go to Confetti's own [AnalyticsLogger] as Livery's flat analytics events: logcat in debug
 * builds, Firebase Analytics in release.
 */
fun confettiLiveryStyling(
    context: Context,
    analytics: AnalyticsLogger = AnalyticsLogger.None,
    clock: Clock = Clock { System.currentTimeMillis() },
    refresh: Boolean = BuildConfig.LIVERY_TEMPLATES_URL.isNotEmpty(),
    forcedArm: String? = BuildConfig.LIVERY_FORCED_ARM.takeIf { it.isNotEmpty() },
): LiveryStyling {
    val app = context.applicationContext
    val fetcher = KtorFetcher()
    val catalogs = ConferenceSeries.ALL.associateWith { series ->
        TemplateRepository(
            source = StaticSource("${BuildConfig.LIVERY_TEMPLATES_URL.trimEnd('/')}/$series/", id = "confetti-$series"),
            fetcher = fetcher,
            crypto = JavaCrypto,
            trust = if (BuildConfig.LIVERY_TEMPLATES_URL.isNotEmpty()) TrustPolicy.AllowUnsigned else TrustPolicy.Signed(emptyList()),
            // Separate stores per catalog: one conference's cache never evicts another's.
            blobs = FileBlobStore(File(app.cacheDir, "livery/$series/blobs")),
            kv = FileKeyValueStore(File(app.filesDir, "livery/$series")),
            clock = clock,
            targets = listOf(LiveryStyling.InApp),
            bundled = AndroidBundled.fromAssets(app, "livery/$series"),
        )
    }
    return LiveryStyling(
        catalogs = catalogs,
        clock = clock,
        platform = "android",
        locale = Locale.getDefault().toLanguageTag(),
        appVersion = BuildConfig.VERSION_CODE.toLong(),
        bucketingId = bucketingId(app),
        events = AnalyticsEventSink(AnalyticsSink { name, params -> analytics.logEvent(LiveryAnalyticsEvent(name, params)) }),
        // Speaker photos come from the conference data's own hosts; a catalog image proxy would
        // replace this list with one trusted host.
        images = { repository ->
            ImageResolver(
                repository, fetcher, JavaCrypto,
                directHosts = setOf("sessionize.com", "storage.googleapis.com", "githubusercontent.com"),
            )
        },
        refresh = refresh,
        forcedArm = forcedArm,
    )
}

/** A random id for sticky rollouts and experiment arms, scoped to this install: never the account. */
private fun bucketingId(context: Context): String {
    val file = File(context.filesDir, "livery/bucketing-id")
    return file.takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
        ?: UUID.randomUUID().toString().also { file.parentFile?.mkdirs(); file.writeText(it) }
}
