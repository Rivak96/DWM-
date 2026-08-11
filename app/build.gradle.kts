import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.paparazzi")
}

// Release signing lives in keystore.properties (gitignored). Back up the
// keystore file — losing it means future updates can't install over the app.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.dwm.cockpit"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dwm.cockpit"
        minSdk = 26          // Android 8.0 — well below the deck's Android 12
        targetSdk = 33
        versionCode = 54
        versionName = "0.35.0"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true

        // AGP 8 does not generate BuildConfig unless asked. The debug tweak panel
        // — live sliders for accent, radius, type scale, density, gutter and motion,
        // so the design can be dialled in sitting in the truck rather than guessed
        // at on a laptop — is gated on BuildConfig.DEBUG and cannot compile without
        // this. It costs one generated class in release.
        buildConfig = true

        // AGP 8 defaults AIDL off. On for the vendor's own CarServiceAidl /
        // CarServiceCallBack definitions in src/main/aidl, copied byte-for-byte
        // out of com.tw.carinfoservice.apk. Letting the build generate the stubs
        // is the whole point: AIDL numbers transactions by declaration order, so
        // generated code cannot disagree with the service the way hand-written
        // transaction codes could.
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Jetpack Compose (BOM-managed versions) + Material 3
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.13.1")

    // @Preview. Until these were added, every visual change cost a full build, a
    // sideload and a walk out to the van, and the last two guesses at what was
    // wrong were both wrong.
    //
    // Debug-only, and the previews themselves live in src/debug/java rather than
    // src/main — shipping ui-tooling-preview in release added ~260 KB to an APK
    // this project has twice cut dependencies to keep near 6 MB, for annotations
    // that do nothing on a deck.
    debugImplementation(composeBom)
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-tooling-preview")

    // JVM-only, never packaged. Proves the AXML reader against a real APK before
    // it ships to a deck we can't debug on.
    testImplementation("junit:junit:4.13.2")
}
