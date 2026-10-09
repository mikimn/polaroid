plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.mikimn.droiduvc.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mikimn.droiduvc.app"
        minSdk = 24
        targetSdk = 36
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
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // The Compose helpers (and, through them, the camera API) come from the SDK modules like in any other app.
    implementation(project(":droiduvc-compose"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
