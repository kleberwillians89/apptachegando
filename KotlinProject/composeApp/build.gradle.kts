import java.util.Properties
import java.util.Base64
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// Material3 uses datetime 0.7.x while Supabase 3.1.2 still links the old Instant ABI.
// The official compatibility artifact preserves it without downgrading Material3.
val datetimeCompatibilityVersion = libs.versions.datetime.get()
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-datetime")) {
            useVersion(datetimeCompatibilityVersion)
            because("Supabase 3.1.2 needs the pre-0.7 Instant ABI on Kotlin/Native")
        }
    }
}

// Public client configuration only. Private signing credentials are never generated into Kotlin.
val appEnvironment = providers.gradleProperty("app.environment")
    .orElse(providers.environmentVariable("APP_ENVIRONMENT")).getOrElse("development")
require(appEnvironment in setOf("development", "homologation", "production")) {
    "app.environment must be development, homologation or production"
}
val environmentProperties = Properties().apply {
    val configFile = rootProject.file("config/$appEnvironment.properties")
    if (configFile.exists()) configFile.inputStream().use { load(it) }
}
fun clientSetting(name: String, fallback: String = ""): String =
    providers.gradleProperty(name).orElse(providers.environmentVariable(name))
        .getOrElse(environmentProperties.getProperty(name, fallback))
val configuredSupabaseUrl = clientSetting("SUPABASE_URL", if (appEnvironment == "development") "http://127.0.0.1:54321" else "")
val configuredSupabaseKey = clientSetting("SUPABASE_PUBLISHABLE_KEY")
val pushContractsInstalled = clientSetting("INTEREST_PUSH_CONTRACTS_INSTALLED", "false") == "true"
val firebaseAppId = clientSetting("FIREBASE_APPLICATION_ID")
val firebaseProjectId = clientSetting("FIREBASE_PROJECT_ID")
val firebaseApiKey = clientSetting("FIREBASE_API_KEY")
val firebaseSenderId = clientSetting("FIREBASE_SENDER_ID")
val configuredMapsKey = clientSetting("GOOGLE_MAPS_API_KEY")
require(!configuredSupabaseKey.startsWith("sb_secret_")) { "Only public Supabase keys belong in client builds." }
val jwtRole = runCatching {
    String(Base64.getUrlDecoder().decode(configuredSupabaseKey.split('.').getOrElse(1) { "" }))
}.getOrDefault("")
require(!jwtRole.contains("service_role")) { "Server keys must never be embedded in the application." }
require(!configuredSupabaseUrl.contains("wzgorvljvogicctpqyli")) {
    "The previous infrastructure is disabled. Configure the client's authorized environment."
}
if (appEnvironment != "development") {
    require(configuredSupabaseUrl.startsWith("https://") && configuredSupabaseKey.isNotBlank()) {
        "Configure HTTPS SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY for this environment."
    }
}
// Homologation can validate the backend before Maps is provisioned. The map's existing
// unavailable state remains visible; production still requires a real Maps configuration.
if (appEnvironment == "production") {
    require(configuredMapsKey.isNotBlank()) { "Configure GOOGLE_MAPS_API_KEY for production." }
} else if (appEnvironment == "homologation" && configuredMapsKey.isBlank()) {
    logger.warn("Homologation: Google Maps is disabled; map integration is NOT validated.")
}
fun kotlinString(value: String): String = "\"" + value.replace("\\", "\\\\")
    .replace("\"", "\\\"").replace("$", "\\$").replace("\n", "\\n").replace("\r", "\\r") + "\""
val generatedClientDirectory = layout.buildDirectory.dir("generated/clientConfiguration")
val generatedClientSource = """
    package org.example.project.core.data
    internal object GeneratedAppConfiguration {
        const val pushBackendConfigured = ${pushContractsInstalled}
        const val pushConfigured = ${pushContractsInstalled && listOf(firebaseAppId, firebaseProjectId, firebaseApiKey, firebaseSenderId).all { it.isNotBlank() }}
        const val firebaseAppId = ${kotlinString(firebaseAppId)}
        const val firebaseProjectId = ${kotlinString(firebaseProjectId)}
        const val firebaseApiKey = ${kotlinString(firebaseApiKey)}
        const val firebaseSenderId = ${kotlinString(firebaseSenderId)}
        const val mapsConfigured = ${configuredMapsKey.isNotBlank()}
        const val environment = ${kotlinString(appEnvironment)}
        const val supabaseUrl = ${kotlinString(configuredSupabaseUrl)}
        const val supabaseKey = ${kotlinString(configuredSupabaseKey)}
    }
""".trimIndent()
val generateClientConfiguration by tasks.registering {
    inputs.property("configuration", generatedClientSource)
    outputs.dir(generatedClientDirectory)
    val outputDirectory = generatedClientDirectory
    val source = generatedClientSource
    doLast {
        outputDirectory.get().file("org/example/project/core/data/GeneratedAppConfiguration.kt")
            .asFile.apply { parentFile.mkdirs(); writeText(source) }
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "tachegando.js"
            }
        }
        binaries.executable()
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain { kotlin.srcDir(generateClientConfiguration) }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.maps.compose)
            implementation(libs.play.services.maps)
            implementation(libs.play.services.location)
            implementation("com.google.firebase:firebase-messaging:25.0.2")
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.supabase.auth)
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.realtime)
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}

android {
    namespace = "org.example.project"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.example.project"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        manifestPlaceholders["googleMapsApiKey"] = configuredMapsKey
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    val signingPath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
    val signingStorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
    val signingAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
    val signingKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
    val hasReleaseSigning = listOf(signingPath, signingStorePassword, signingAlias, signingKeyPassword)
        .all { !it.isNullOrBlank() }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(signingPath!!)
                storePassword = signingStorePassword
                keyAlias = signingAlias
                keyPassword = signingKeyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
        // External QA APK: isolated identity, optimized, and explicitly signed with the local debug key.
        create("homologation") {
            initWith(getByName("release"))
            applicationIdSuffix = ".homologation"
            versionNameSuffix = "-homologation"
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            resValue("string", "app_name", "TáChegando Homologação")
        }
        // Local measurements only: isolated app, never uses the client's release identity.
        create("performance") {
            initWith(getByName("release"))
            applicationIdSuffix = ".performance"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    debugImplementation(libs.compose.uiTooling)
}


// Never silently fall back to debug signing for a release artifact.
val validateReleaseCredentials by tasks.registering {
    val complete = listOf("ANDROID_KEYSTORE_PATH", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD")
        .all { !providers.environmentVariable(it).orNull.isNullOrBlank() }
    doLast { check(complete) { "Configure the client's release signing credentials outside source control." } }
}
tasks.matching { it.name == "packageRelease" || it.name == "signReleaseBundle" }.configureEach {
    dependsOn(validateReleaseCredentials)
}

// Never distribute the QA package with development/production or a localhost backend.
val validateHomologationConfiguration by tasks.registering {
    val valid = appEnvironment == "homologation" &&
        configuredSupabaseUrl == "https://vhnkezwjknwpddqhlnfk.supabase.co" &&
        configuredSupabaseKey.startsWith("sb_publishable_")
    doLast { check(valid) { "Homologation APK requires app.environment=homologation and the authorized remote project/public key." } }
}
tasks.matching { it.name == "preHomologationBuild" }.configureEach {
    dependsOn(validateHomologationConfiguration)
}
