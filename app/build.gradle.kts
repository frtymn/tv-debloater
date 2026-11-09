plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.frtymn.tvdebloater"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.frtymn.tvdebloater"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        // Tag builds set VERSION_NAME (v0.1.1 -> 0.1.1); other builds are dev builds of the next version.
        versionName = System.getenv("VERSION_NAME") ?: ("0.1.2-dev." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0"))
    }

    // CI sets DEBUG_KEYSTORE_FILE to the fixed repository debug key, so every release is signed with
    // the same key and a newer APK installs over an older one. Without it, the usual local debug key is used.
    signingConfigs {
        getByName("debug") {
            System.getenv("DEBUG_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let {
                storeFile = file(it)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/previous-compilation-data.bin")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // Screenshot rendering (Robolectric + Roborazzi) only runs when asked for: SCREENSHOTS=1.
            if (System.getenv("SCREENSHOTS") == "1") {
                test.systemProperty("roborazzi.test.record", "true")
                test.maxHeapSize = "3g"
            } else {
                test.exclude("**/screenshots/**")
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.dadb)

    testImplementation(libs.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    debugImplementation(libs.compose.ui.test.manifest)
}
