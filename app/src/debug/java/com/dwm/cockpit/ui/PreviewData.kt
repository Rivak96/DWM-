package com.dwm.cockpit.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import com.dwm.cockpit.ui.theme.CockpitColors
import com.dwm.cockpit.ui.theme.DwmColors
import com.dwm.cockpit.ui.theme.DwmMaterialShapes
import com.dwm.cockpit.ui.theme.LocalDwmColors
import com.dwm.cockpit.ui.theme.dwmTypography

/**
 * Fixtures for `@Preview`.
 *
 * The point of this file is that visual work no longer requires a build, a
 * sideload and a walk out to the van. Three releases in a row were spent guessing
 * what was wrong with a screen from a description, and two of those guesses were
 * wrong; being able to look at the thing on a laptop is worth more than any single
 * change in this rebuild.
 *
 * Nothing here touches `Prefs`, `CarInfo` or `Media`, so previews render without a
 * device, a `SharedPreferences` instance or a bound vendor service.
 */

/* ------------------------------------------------------------------- theming */

/** The Light palette as `Ui.th()` builds it, without reading SharedPreferences. */
val LightPreviewColors = DwmColors(
    bg = Color(0xFFF4F5F6),
    cardTop = Color(0xFFFFFFFF),
    cardBottom = Color(0xFFECEDF0),
    cardBorder = Color(0x14000000),
    surfaceLow = Color(0xFFEEEFF0),
    accent = Color(0xFF3E6AE1),
    textPrimary = Color(0xFF171A20),
    textSecondary = Color(0xFF5C5E62),
    textTertiary = Color(0xFF8A8C90),
    hairline = Color(0x1F000000),
    press = Color(0x14000000),
    light = true
)

/**
 * `DwmTheme` without the `Context`.
 *
 * The real one derives everything from `Ui.th(context)`, which is right at runtime
 * and unusable in a preview. This takes the palette directly, which also means a
 * preview can show the Light theme without changing a stored preference — and the
 * Light theme is exactly what the old hardcoded `Color.White` calls had quietly
 * broken.
 */
@Composable
fun DwmPreviewTheme(
    colors: DwmColors = CockpitColors,
    content: @Composable () -> Unit
) {
    val scheme = if (colors.light) {
        lightColorScheme(
            primary = colors.accent,
            background = colors.bg,
            onBackground = colors.textPrimary,
            surface = colors.cardTop,
            onSurface = colors.textPrimary
        )
    } else {
        darkColorScheme(
            primary = colors.accent,
            background = colors.bg,
            onBackground = colors.textPrimary,
            surface = colors.cardTop,
            onSurface = colors.textPrimary
        )
    }
    CompositionLocalProvider(LocalDwmColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = dwmTypography(),
            shapes = DwmMaterialShapes,
            content = content
        )
    }
}

/* ---------------------------------------------------------------- fake icons */

/** A flat colour square standing in for a launcher icon. Drawn with Compose's own
 *  Canvas rather than `android.graphics`, so it works in a static preview. */
fun swatch(color: Color, size: Int = 128): ImageBitmap {
    val bmp = ImageBitmap(size, size)
    Canvas(bmp).drawRect(
        left = 0f, top = 0f, right = size.toFloat(), bottom = size.toFloat(),
        paint = Paint().apply { this.color = color }
    )
    return bmp
}

/* ----------------------------------------------------------------- fake data */

/** Twelve, because `Apps.FAV_SLOTS` is twelve and the grid is three rows of four. */
val previewFavourites: List<HomeApp> = listOf(
    "Spotify" to Color(0xFF1DB954),
    "Maps" to Color(0xFF4285F4),
    "Waze" to Color(0xFF33CCFF),
    "Chrome" to Color(0xFFEA4335),
    "Music" to Color(0xFFFA243C),
    "YouTube" to Color(0xFFFF0000),
    "Gmail" to Color(0xFFD93025),
    "Podcasts" to Color(0xFF9C27B0),
    "Camera" to Color(0xFF5AC8FA),
    "Torque" to Color(0xFFFFC107),
    "Files" to Color(0xFF34A853),
    "Clock" to Color(0xFFFF9500)
).map { (name, c) -> HomeApp("com.demo.${name.lowercase()}", name, swatch(c), c) }

/**
 * A van that is actually doing something: reversing at walking pace with two rear
 * sensors picking something up, wheel slightly off centre, healthy battery.
 *
 * Deliberately not all-clear — the states worth looking at in a preview are the
 * ones with colour in them.
 */
val previewVehicle: VehicleUi = VehicleUi(
    head = mutableStateOf(HeadState(canLevel = 2, updates = 812, demo = false)),
    drive = mutableStateOf(DriveState(speedKmh = 4, gear = 2, headlight = true)),
    body = mutableStateOf(
        BodyState(
            headlight = true,
            turnSignal = 1,
            reverse = true,
            track = 300,
            radar = listOf(0, 0, 0, 0, 0, 0, 9, 5, 7, 3, 2, 8, 0, 0, 0, 0)
        )
    ),
    vitals = mutableStateOf(VitalsState(voltage = 12.7f, track = 300))
)

/** A van sitting still with nothing to report — the state this deck is in most of
 *  the time, and the one that has to look intentional rather than broken. */
val previewVehicleIdle: VehicleUi = VehicleUi(
    head = mutableStateOf(HeadState(canLevel = 1)),
    drive = mutableStateOf(DriveState(speedKmh = 0)),
    body = mutableStateOf(BodyState(radar = List(16) { 0 })),
    vitals = mutableStateOf(VitalsState(voltage = 12.4f, track = 240))
)

val previewActions = HomeActions(
    carplay = {}, overlayMenu = {}, bluetooth = {}, wifi = {}, apps = {},
    edit = {}, settings = {}, reload = {}, pill = {}, launch = {},
    appMenu = {}, grantNotifications = {}
)
