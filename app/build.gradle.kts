import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appSecrets = Properties().apply {
    val secretsFile = rootProject.file("secrets.properties")
    if (secretsFile.exists()) secretsFile.inputStream().use { load(it) }
}
val emailOtpApiBaseUrl = appSecrets.getProperty("emailOtpApiBaseUrl", "")
val agentApiBaseUrl = appSecrets.getProperty(
    "agentApiBaseUrl",
    "https://asia-south1-ello-744e4.cloudfunctions.net",
)

android {
    namespace = "com.ello.litertchat"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.ello.litertchat"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "1.10"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "EMAIL_OTP_API_BASE_URL", "\"$emailOtpApiBaseUrl\"")
        buildConfigField("String", "AGENT_API_BASE_URL", "\"$agentApiBaseUrl\"")
    }
    packaging { jniLibs.useLegacyPackaging = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.composables:icons-lucide-android:2.2.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.24.1")
    implementation("com.googlecode.libphonenumber:libphonenumber:9.0.40")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("io.modelcontextprotocol:kotlin-sdk:0.8.0")
    implementation("io.ktor:ktor-client-android:3.4.3")
    implementation("io.ktor:ktor-client-core:3.4.3")
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
