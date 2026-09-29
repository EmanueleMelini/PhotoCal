import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
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

android {
    namespace = "it.emanuelemelini.photocal"
    compileSdk = 37

    defaultConfig {
        applicationId = "it.emanuelemelini.photocal"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"
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
    implementation(libs.play.services.code.scanner)

    testImplementation(libs.junit)
}
