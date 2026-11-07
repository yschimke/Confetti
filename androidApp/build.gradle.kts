@file:Suppress("UnstableApiUsage")

import java.io.FileInputStream
import java.util.*

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("io.github.takahirom.roborazzi")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.screenshot)
    // Design-catalog sticker sheet: discovers the @Preview functions in ComponentCatalog.kt /
    // AndroidScreenPreviews.kt and renders them for the published mobile catalog (see
    // catalog.mobile.spec.json + the design-artifacts workflow). Mirrors :wearApp.
    alias(libs.plugins.composeai.preview)
    id("androidx.baselineprofile")
    // Livery's catalog builder (`livery build --inspect` as a Gradle task). Not applied: the
    // plugin bundles one catalog into every variant, and Confetti bundles one per conference into
    // sduiDebug only (see "Livery" below).
    alias(libs.plugins.livery.catalog) apply false
}

configureCompilerOptions()

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

val versionMajor = 1
val versionMinor = 0

val versionNum: String? by project

fun versionCode(): Int {
    versionNum?.let {
        val code: Int = (versionMajor * 1000000) + (versionMinor * 1000) + it.toInt()
        println("versionCode is set to $code")
        return code
    } ?: return 1
}

fun versionName(): String {
    versionNum?.let {
        val name = "${versionMajor}.${versionMinor}.${versionNum}"
        println("versionName is set to $name")
        return name
    } ?: return "1.0"
}

android {
    compileSdk = AndroidSdk.compile
    defaultConfig {
        applicationId = "dev.johnoreilly.confetti"
        minSdk = AndroidSdk.min
        targetSdk = AndroidSdk.target

        versionCode = versionCode()
        versionName = versionName()

        // Conference-styled slots (Livery): only in the sduiDebug build type. Every other variant
        // draws Confetti's own UI. See livery-catalog/README.md.
        buildConfigField("boolean", "LIVERY_ENABLED", "false")
        buildConfigField("String", "LIVERY_TEMPLATES_URL", "\"\"")
        buildConfigField("String", "LIVERY_FORCED_ARM", "\"\"")
    }

    androidResources {
        localeFilters += listOf("en", "fr")
    }

    signingConfigs {
        create("confetti") {
            keyAlias = "confetti"
            keyPassword = "confetti"
            storeFile = file("confetti.keystore")
            storePassword = "confetti"
        }
        create("release") {
            (keystoreProperties["keyPath"] as String?)?.let {
                storeFile = file(it)
            }
            keyAlias = keystoreProperties["keyAlias"] as String?
            keyPassword = keystoreProperties["keyPassword"] as String?
            storePassword = keystoreProperties["storePassword"] as String?
            enableV2Signing = true
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // One JVM per test class: SessionNotificationTest (SDK 30) and the native-graphics
            // screenshot tests (SDK 35) both make Robolectric load its native runtime, and in a shared
            // JVM the second load fails with FileSystemAlreadyExistsException (maybeCopyFonts) — the
            // "flaky" SpeakerDetailsScTest, which failed whenever Gradle ran it after the other.
            all { it.forkEvery = 1 }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        getByName("release") {
            isShrinkResources = true
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            )
        }
        create("githubRelease") {
            isShrinkResources = true
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("confetti")
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            )

            matchingFallbacks += listOf("release")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("confetti")
        }
        // Server-driven UI: debug, plus each conference's Livery catalog bundled into the assets,
        // a newer one fetched from the livery-templates branch when there is one, and the sdui
        // experiment (Livery vs Confetti's own composables) assigned on the device. Same
        // applicationId as debug: google-services.json has no client for a suffixed one.
        create("sduiDebug") {
            initWith(getByName("debug"))
            versionNameSuffix = "-sdui"
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "LIVERY_ENABLED", "true")
            // Each series is served from <url>/<series>/manifest.json. -Pconfetti.livery.templates=
            // points at another host or branch (a PR's channel, joreilly/confetti-templates).
            val templates = (findProperty("confetti.livery.templates") as String?)
                ?: "https://raw.githubusercontent.com/yschimke/Confetti/livery-templates/catalogs"
            buildConfigField("String", "LIVERY_TEMPLATES_URL", "\"$templates\"")
            // -Pconfetti.livery.arm=native|livery pins the sdui arm (screenshots, QA).
            buildConfigField("String", "LIVERY_FORCED_ARM", "\"${findProperty("confetti.livery.arm") ?: ""}\"")
        }
    }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/AL2.0",
                "/META-INF/LGPL2.1",
                "/META-INF/versions/**"
            )
        }
    }

    namespace = "dev.johnoreilly.confetti"

    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}


kotlin {
    compilerOptions {
        optIn.add("androidx.compose.material.ExperimentalMaterialApi")
    }
}

dependencies {
    baselineProfile(project(":androidBenchmark"))
    implementation(enforcedPlatform(libs.compose.bom))
    implementation(platform(libs.coil.bom))
    implementation(platform(libs.coil3.bom))
    implementation(platform(libs.koin.bom))

    implementation(projects.common.car)
    implementation(projects.shared)
    implementation(libs.livery.client)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.material)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.coil.compose)
    // coil3 (used by the shared CMP components) — needed for the design-catalog preview image handler.
    implementation(libs.coil3.compose)
    // @ThemeCatalog, for the alternative-theme providers in ConfettiThemeCatalogs.kt — BINARY
    // retention, scanned off the compiled classes by the preview plugin. Mirrors :wearApp.
    implementation(libs.composeai.preview.annotations)

    implementation(libs.decompose.decompose)
    implementation(libs.decompose.extensions.compose)

    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.material3.core)
    implementation(libs.splash.screen)

    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.horologist.datalayer)
    implementation(libs.horologist.datalayer.phone)

    val excludeCrashlyticsConflicts = Action<ExternalModuleDependency> {
        // Crashlytics and PerfMon depend on datastore v1.0 but we're using v1.1
        exclude(group = "androidx.datastore", module = "datastore-preferences")
        // https://github.com/firebase/firebase-android-sdk/issues/5997
        exclude(group = "com.google.firebase", module = "protolite-well-known-types")
    }
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics, excludeCrashlyticsConflicts)
    implementation(libs.firebase.analytics, excludeCrashlyticsConflicts)
    implementation(libs.firebase.performance, excludeCrashlyticsConflicts)
    implementation(libs.play.services.auth)
    coreLibraryDesugaring(libs.desugar)

    implementation(libs.googleid)
    implementation(libs.androidx.credentials.play.services.auth)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.koin.test)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.rule)
    testImplementation(libs.coil.test)
    debugImplementation(libs.compose.ui.manifest)
    "sduiDebugImplementation"(libs.compose.ui.manifest)

    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
}

baselineProfile {
    // Write the generated profile to src/main/generated/baselineProfiles so every build type uses it
    mergeIntoMain = true
}

// The plugin's profiling builds copy release's signing config, whose keystore isn't checked in.
// They're only installed on the generator's emulator, so sign them with the committed key.
// Livery: each conference series' catalog (livery-catalog/catalogs/<series>), built and checked
// like `livery build --inspect`, into sduiDebug's assets at livery/<series>/, where
// AndroidBundled.fromAssets(context, "livery/<series>") reads it back. The bundled copy has serial
// 0, so a catalog the app downloads always replaces it.
val liverySeries = listOf("droidcon", "kotlinconf")
val liveryCatalogTasks = liverySeries.map { series ->
    tasks.register<ee.schimke.livery.gradle.BuildLiveryCatalog>("buildLiveryCatalog${series.replaceFirstChar { it.uppercase() }}") {
        group = "build"
        description = "Builds the $series Livery catalog into generated assets"
        source.set(rootProject.layout.projectDirectory.dir("livery-catalog/catalogs/$series"))
        assetsPath.set("livery/$series")
        inspect.set(true)
        outputDir.set(layout.buildDirectory.dir("generated/livery/$series"))
    }
}

androidComponents {
    // AGP only creates unit tests for the test build type (debug); LiverySlotsTest runs here.
    beforeVariants(selector().withBuildType("sduiDebug")) { variant ->
        variant.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
    onVariants(selector().withBuildType("sduiDebug")) { variant ->
        liveryCatalogTasks.forEach { variant.sources.assets?.addGeneratedSourceDirectory(it, ee.schimke.livery.gradle.BuildLiveryCatalog::outputDir) }
    }
    onVariants(selector().withBuildType("nonMinifiedRelease")) {
        it.signingConfig.setConfig(android.signingConfigs.getByName("confetti"))
    }
    onVariants(selector().withBuildType("benchmarkRelease")) {
        it.signingConfig.setConfig(android.signingConfigs.getByName("confetti"))
    }
}
