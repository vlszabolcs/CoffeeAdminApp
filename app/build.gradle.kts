plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.coffeeadmin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.coffeeadmin"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing comes from the environment (GitHub secrets in the release workflow).
    // Without it the release APK is signed with the debug key so it still installs.
    val releaseKeystore = System.getenv("ADMINAPP_KEYSTORE_FILE")
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("ADMINAPP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ADMINAPP_KEY_ALIAS")
                keyPassword = System.getenv("ADMINAPP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth)
    implementation("com.google.firebase:firebase-analytics")

    implementation(project(":userview"))

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}

// ---- Screenshot tests (branch "screenshots") ----------------------------------------------
// Rendered on the JVM with Robolectric + Roborazzi, with sample data only (no Firebase).
// Normal builds skip them; run with: ./gradlew -Pscreenshots :<module>:testDebugUnitTest
android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}
tasks.withType<Test>().configureEach {
    if (project.hasProperty("screenshots")) {
        systemProperty("roborazzi.test.record", "true")
        filter { includeTestsMatching("*ScreenshotTest") }
    } else {
        exclude("**/*ScreenshotTest*")
    }
}
dependencies {
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.+")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.+")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
