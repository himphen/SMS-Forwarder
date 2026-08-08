import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val releaseKeystorePath = providers.gradleProperty("releaseKeystorePath").orNull
    ?: providers.environmentVariable("RELEASE_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.gradleProperty("releaseStorePassword").orNull
    ?: providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.gradleProperty("releaseKeyAlias").orNull
    ?: providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.gradleProperty("releaseKeyPassword").orNull
    ?: providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
val releaseSigningReady = listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }
val versionNamePrefix = "Hong Kong Style French Toast"
val releaseVersion = (providers.environmentVariable("ANDROID_VERSION_NAME").orNull ?: "1.0.0")
    .removePrefix("v")
require(Regex("""\d+\.\d+\.\d+""").matches(releaseVersion)) {
    "ANDROID_VERSION_NAME must use MAJOR.MINOR.PATCH format after an optional 'v' prefix."
}
val versionCodeInput = providers.environmentVariable("ANDROID_VERSION_CODE").orNull ?: "1"
val releaseVersionCode = versionCodeInput.toIntOrNull()
require(releaseVersionCode != null && releaseVersionCode > 0) {
    "ANDROID_VERSION_CODE must be a positive integer."
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.himphen.playground.smsforwarder"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.himphen.playground.smsforwarder"
        minSdk = 26
        targetSdk = 37
        versionCode = releaseVersionCode
        versionName = "$versionNamePrefix $releaseVersion"
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.tink.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
