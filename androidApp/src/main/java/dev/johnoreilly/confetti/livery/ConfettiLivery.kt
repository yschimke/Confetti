package dev.johnoreilly.confetti.livery

import android.content.Context
import dev.johnoreilly.confetti.BuildConfig
import dev.johnoreilly.confetti.ui.livery.LiveryStyling
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
 * Livery on Android: the conference designs from :livery-catalog, bundled into the APK's assets at
 * build time by Livery's Gradle plugin (see build.gradle.kts), so they work offline and on first
 * launch.
 *
 * The remote channel is *planned*: [CATALOG_URL] isn't hosted yet, and with no trusted key nothing
 * remote would be accepted anyway. Hosting it means a signing key (`livery keygen confetti`), its
 * public half here in [TrustPolicy.Signed], `livery build` in CI, and a [TemplateRepository.refresh]
 * from WorkManager next to the conference refresh. Then a design change ships without an app
 * release.
 */
fun confettiLiveryStyling(context: Context, clock: Clock = Clock { System.currentTimeMillis() }): LiveryStyling {
    val app = context.applicationContext
    val fetcher = KtorFetcher()
    val bundled = AndroidBundled.fromAssets(app)
    val repository = TemplateRepository(
        source = StaticSource(CATALOG_URL, id = "confetti"),
        fetcher = fetcher,
        crypto = JavaCrypto,
        trust = TrustPolicy.Signed(emptyList()),
        blobs = FileBlobStore(File(app.cacheDir, "livery/blobs")),
        kv = FileKeyValueStore(File(app.filesDir, "livery")),
        clock = clock,
        targets = listOf(LiveryStyling.InApp),
        bundled = bundled,
    )
    return LiveryStyling(
        repository = repository,
        clock = clock,
        platform = "android",
        locale = Locale.getDefault().toLanguageTag(),
        appVersion = BuildConfig.VERSION_CODE.toLong(),
        bucketingId = bucketingId(app),
        // Speaker photos come from the conference data's own hosts; a catalog image proxy would
        // replace this list with one trusted host.
        images = ImageResolver(
            repository, fetcher, JavaCrypto,
            directHosts = setOf("sessionize.com", "storage.googleapis.com", "githubusercontent.com"),
        ),
    )
}

private const val CATALOG_URL = "https://confetti-app.dev/livery/"

/** A random id for sticky rollouts, scoped to this install: never the account. */
private fun bucketingId(context: Context): String {
    val file = File(context.filesDir, "livery/bucketing-id")
    return file.takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
        ?: UUID.randomUUID().toString().also { file.parentFile?.mkdirs(); file.writeText(it) }
}
