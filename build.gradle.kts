plugins {
    id("base")
    alias(libs.plugins.composeai.preview) apply false
}

buildscript {
    dependencies {
        classpath("build-logic:build-logic")
    }
}

tasks.register("setupCredentials") {
    val gcpServiceAccountKeyFile = file("backend/datastore/src/jvmMain/resources/gcp_service_account_key.json")
    val firebaseServiceAccountKeyFile = file("backend/service-graphql/src/main/resources/firebase_service_account_key.json")
    val apolloKeyFile = file("backend/service-graphql/src/main/resources/apollo.key")
    doLast {
        fun File.writeEnv(name: String) {
            parentFile.mkdirs()
            writeText(System.getenv(name))
        }
        if (System.getenv("CI")?.isNotEmpty() == true) {
            println("setting up google services...")
            gcpServiceAccountKeyFile.writeEnv("GOOGLE_SERVICES_JSON")
            firebaseServiceAccountKeyFile.writeEnv("FIREBASE_SERVICES_JSON")
            apolloKeyFile.writeEnv("APOLLO_KEY")
        }
    }
}

tasks.register("quickChecks") {
    dependsOn(
        ":backend:service-graphql:build",
        ":backend:service-import:build",
        ":androidApp:assembleDebug",
        ":wearApp:assembleDebug",
        ":wearApp:assembleDebugAndroidTest",
    )
}

// Livery 0.1.1 is compiled against Compose Multiplatform 1.13.0-beta02 and asks for it, which would
// raise this repo's stable Compose (and with it androidx core, collection and tracing) in every app
// that depends on :shared. Keep the repo's own version. The Livery slots are off until a Livery
// release built on stable Compose (yschimke/livery#14), so no app runs Livery code on this runtime.
// :livery-catalog is a JVM build tool that captures and renders designs and ships in no app; it
// keeps what Livery asks for.
subprojects {
    if (path == ":livery-catalog") return@subprojects
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group.startsWith("org.jetbrains.compose") && requested.version == "1.13.0-beta02") {
                useVersion(libs.versions.compose.multiplatform.get())
                because("Livery 0.1.1 asks for a beta Compose Multiplatform; keep the repo's stable line")
            }
        }
    }
}
