import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    // Firebase: reads app/google-services.json (not in the repository: from the Firebase console,
    // or the PHOTOCAL_GOOGLE_SERVICES_JSON secret in CI) and uploads the R8 mapping of release
    // builds, so Crashlytics shows readable stack traces
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

/**
 * Release signing: from environment variables in CI (GitHub Actions secrets) or from
 * local.properties on this machine. The keystore and its password never go in the repository.
 */
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signingValue(env: String, property: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: localProperties.getProperty(property)

val releaseKeystore = signingValue("PHOTOCAL_KEYSTORE_PATH", "photocal.keystore.path")

/** Host of the share links (App Links, verified by site/.well-known/assetlinks.json). Public. */
val shareHost: String = providers.gradleProperty("photocal.share.host").get()

/**
 * Key that signs the share links (HMAC). Same value in local.properties and in the
 * PHOTOCAL_SHARE_KEY secret, never in the repository: the code is public, so a key there
 * would let anyone forge links. Unsigned builds fall back to a development key (base64 of
 * "photocal-development-key"), whose links the release builds reject.
 */
val shareKey: String? = signingValue("PHOTOCAL_SHARE_KEY", "photocal.share.key")
if (releaseKeystore != null && shareKey == null) {
    throw GradleException("Signed builds need photocal.share.key in local.properties (or PHOTOCAL_SHARE_KEY)")
}

android {
    namespace = "it.emanuelemelini.photocal"
    compileSdk = 37

    defaultConfig {
        applicationId = "it.emanuelemelini.photocal"
        minSdk = 26
        targetSdk = 37
        versionCode = 8
        versionName = "1.7.0"

        manifestPlaceholders["shareHost"] = shareHost
        buildConfigField("String", "SHARE_HOST", "\"$shareHost\"")
        buildConfigField("String", "SHARE_KEY", "\"${shareKey ?: "cGhvdG9jYWwtZGV2ZWxvcG1lbnQta2V5"}\"")
    }

    androidResources {
        // Declares the supported languages to the system ("App language" on Android 13+)
        generateLocaleConfig = true
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = signingValue("PHOTOCAL_KEYSTORE_PASSWORD", "photocal.keystore.password")
                keyAlias = signingValue("PHOTOCAL_KEY_ALIAS", "photocal.key.alias")
                keyPassword = signingValue("PHOTOCAL_KEYSTORE_PASSWORD", "photocal.keystore.password")
            }
        }
    }

    buildTypes {
        debug {
            // en-XA / ar-XB pseudo-locales to spot hardcoded or truncated text
            isPseudoLocalesEnabled = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Unsigned release APK when no keystore is configured (it can't be installed)
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

room {
    // Exported schemas (committed) document every database version and back the migrations
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.health.connect)
    implementation(libs.play.services.code.scanner)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
}
