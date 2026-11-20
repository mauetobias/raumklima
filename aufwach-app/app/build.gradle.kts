plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "cc.maue.aufwachen"
    compileSdk = 35

    defaultConfig {
        applicationId = "cc.maue.aufwachen"
        minSdk = 28
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
        // Store-Version (Play Store): ohne eigenen Updater, Updates kommen über Google Play
        buildConfigField("boolean", "STORE", (project.findProperty("store") ?: "false").toString())
    }

    // Der Schlüssel liegt verschlüsselt im Repo und wird beim Bauen mit dem Secret entschlüsselt
    signingConfigs {
        create("fest") {
            storeFile = file("release.jks")
            storePassword = "aufwachen"
            keyAlias = "aufwachen"
            keyPassword = "aufwachen"
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("fest")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
