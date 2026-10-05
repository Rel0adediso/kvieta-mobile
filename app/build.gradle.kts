plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.kvieta.companion"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kvieta.companion"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "1.0.0-alpha1.2"
    }
    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("kvieta-release.jks")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = project.findProperty("KVIETA_KEYSTORE_PASSWORD") as String? ?: "kvieta2026"
                keyAlias = project.findProperty("KVIETA_KEY_ALIAS") as String? ?: "kvieta"
                keyPassword = project.findProperty("KVIETA_KEY_PASSWORD") as String? ?: "kvieta2026"
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        release {
            isMinifyEnabled = false
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.3")
    implementation("androidx.work:work-runtime-ktx:2.10.3")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
