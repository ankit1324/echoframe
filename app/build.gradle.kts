import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// --- Release signing -------------------------------------------------------
// Credentials live OUTSIDE the repo: either <root>/keystore.properties (git-ignored,
// see keystore.properties.example) or environment variables (for CI). Nothing here
// is committed. If none are present the release build is simply left unsigned —
// assembleDebug and the unit tests keep working for developers without the keystore.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) keystorePropertiesFile.inputStream().use { load(it) }
}

fun signingValue(key: String, envName: String): String? =
    (keystoreProperties.getProperty(key) ?: System.getenv(envName))?.trim()?.takeIf { it.isNotEmpty() }

val releaseStoreFile = signingValue("storeFile", "ECHOFRAME_STORE_FILE")?.let { rootProject.file(it) }
val releaseStorePassword = signingValue("storePassword", "ECHOFRAME_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "ECHOFRAME_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "ECHOFRAME_KEY_PASSWORD")

val hasReleaseSigning = releaseStoreFile?.isFile == true &&
    releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null

android {
    namespace = "com.nothingai.capture"
    compileSdk = 35
    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = "com.nothingai.capture"
        minSdk = 31
        targetSdk = 35
        // CI passes the workflow run number so every release build carries a distinct, increasing
        // versionCode. Local builds stay at 1.
        versionCode = System.getenv("ECHOFRAME_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true      // R8 tree-shakes material-icons-extended (~10k unused icon classes)
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Never fall back to the debug keystore: Play rejects APKs/AABs signed with
            // CN=Android Debug. Without credentials the release output stays unsigned,
            // which fails loudly at install/upload time instead of shipping a debug key.
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
        }
    }
    // ML Kit ships ~47MB of x86/x86_64 native pipeline libraries that only emulators ever load.
    // Splitting by ABI takes the phone-sized download from ~92MB to ~26MB. The universal APK is
    // still produced as the fallback for anyone unsure of their device's ABI.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    buildFeatures { compose = true }
    composeOptions { }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

// Warn only when a release artifact is actually being built, so debug builds and unit
// tests stay quiet for developers who do not hold the keystore.
if (!hasReleaseSigning) {
    tasks.configureEach {
        if (name == "assembleRelease" || name == "bundleRelease") {
            doFirst {
                logger.warn(
                    "Echoframe: no release signing credentials found — this output will be UNSIGNED. " +
                        "Create keystore.properties at the repo root (see keystore.properties.example) or set " +
                        "ECHOFRAME_STORE_FILE / ECHOFRAME_STORE_PASSWORD / ECHOFRAME_KEY_ALIAS / ECHOFRAME_KEY_PASSWORD."
                )
            }
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:image-labeling:17.0.9")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("androidx.room:room-testing:2.6.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.work:work-testing:2.9.1")
    androidTestImplementation("com.google.truth:truth:1.4.4")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
