import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Confetti's Livery catalog: conference-styled Remote Compose templates (droidcon, KotlinConf, ...)
// authored as @RemoteComposable functions with rc-players' Compose Multiplatform fork of
// remote-creation-compose, captured to .rc on the JVM (no Android runtime, no emulator), and laid
// out as a Livery catalog source tree under catalog/ that `livery build` publishes.
//
//   ./gradlew :livery-catalog:test                     captures every template and checks it plays
//   ./gradlew :livery-catalog:generateLiveryCatalog    (re)writes catalog/designs/**.rc + renders/
plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    alias(libs.plugins.compose.compiler)
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(platform(libs.rc.players.bom))
    // Authoring: the CMP fork of androidx remote-creation-compose (JVM + Android + iOS).
    implementation(libs.rc.remote.creation.compose)
    // Playing back (previews, tests): the Compose player and Livery's contract binding.
    implementation(libs.rc.player.compose)
    implementation(projects.liveryContracts)
    implementation(libs.livery.compose)
    implementation(libs.livery.render)
    implementation(compose.desktop.currentOs)
    // Material 3 dynamic schemes, for the renders of a user's dynamic colours.
    implementation(compose.material3)
    implementation(libs.materialkolor)
    implementation(platform(libs.kotlinx.serialization.bom))
    implementation(libs.kotlinx.serialization)

    testImplementation(kotlin("test"))
}

val catalogDir = layout.projectDirectory.dir("catalog")

tasks.register<JavaExec>("generateLiveryCatalog") {
    group = "livery"
    description = "Captures every Confetti design to .rc into catalog/ and renders previews."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.johnoreilly.confetti.livery.GenerateCatalogKt")
    args(catalogDir.asFile.absolutePath, layout.projectDirectory.dir("renders").asFile.absolutePath)
}
