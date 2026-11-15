plugins {
    // AGP 9 compiles Kotlin itself; no separate kotlin-android plugin needed
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.widgetapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.widgetapp"
        // 26 so java.time (LocalDate, ZonedDateTime) is available without desugaring
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Home screen widget
    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("androidx.glance:glance-material3:1.2.0")

    // App UI
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.13.0")

    // Saved dates and widget settings
    implementation("androidx.datastore:datastore-preferences:1.3.0")

    testImplementation("junit:junit:4.13.2")
    // Real JSON for unit tests; Android's built-in one is only a stub off-device
    testImplementation("org.json:json:20240303")
}
