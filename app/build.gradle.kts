val compose_version: String by rootProject.extra

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mikimn.polaroid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mikimn.polaroid"
        minSdk = 24
        targetSdk = 33
        // Single source of truth for the version: VERSION_NAME in gradle.properties (MAJOR.MINOR.PATCH[-suffix]).
        val version = providers.gradleProperty("VERSION_NAME").get()
        require(Regex("""\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?""").matches(version)) {
            "VERSION_NAME in gradle.properties must look like MAJOR.MINOR.PATCH[-suffix], but was '$version'"
        }
        val (major, minor, patch) = version.substringBefore('-').split('.').map { it.toInt() }
        versionCode = major * 10000 + minor * 100 + patch
        versionName = version

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Release signing is configured from the environment so no secrets live in the repository.
    // Without RELEASE_KEYSTORE_FILE, the release build stays unsigned (CI then ships the debug APK).
    val releaseKeystore = System.getenv("RELEASE_KEYSTORE_FILE")
    if (releaseKeystore != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        named("release") {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            setProguardFiles(listOf(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"))
        }
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.2.0"
    }
    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":libpolaroid"))
    implementation("androidx.core:core-ktx:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.3.1")
    implementation("androidx.activity:activity-compose:1.3.1")
    implementation("androidx.compose.ui:ui:$compose_version")
    implementation("androidx.compose.ui:ui-tooling-preview:$compose_version")
    implementation("androidx.compose.material3:material3:1.0.0-alpha11")
    implementation("androidx.compose.ui:ui-tooling-preview-android:1.6.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:$compose_version")
    debugImplementation("androidx.compose.ui:ui-tooling:$compose_version")
    debugImplementation("androidx.compose.ui:ui-test-manifest:$compose_version")
}