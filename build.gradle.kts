plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    // Renders Compose to PNG on the JVM via LayoutLib — no emulator, no deck.
    // 1.3.5 is the last line that targets Kotlin 2.0.21 and runs on JDK 17;
    // Paparazzi 2.x requires JDK 21+. Debug-only tooling, never packaged.
    id("app.cash.paparazzi") version "1.3.5" apply false
}
