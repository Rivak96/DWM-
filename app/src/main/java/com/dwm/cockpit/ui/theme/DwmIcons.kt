package com.dwm.cockpit.ui.theme

import androidx.annotation.DrawableRes
import com.dwm.cockpit.R

/**
 * The icon family, and the rule that no foreign icon is ever drawn.
 *
 * ### Why this exists
 *
 * The home screen was showing a photorealistic RCA cable for AUX, a glossy blue
 * squircle for Bluetooth and an orange gradient badge for TPMS, side by side. Three
 * unrelated visual languages in one row, none of them DWM's, each arriving with its
 * own palette, lighting model and corner treatment. No OEM system has ever shown a
 * foreign icon, and nothing else on the screen — not the colours, not the type, not
 * the grid — could survive sitting next to them.
 *
 * So DWM draws its own, and where it has not drawn one yet it draws a letter instead.
 * Falling back to `packageManager.getApplicationIcon` is the one thing this file
 * exists to prevent.
 *
 * ### Why keyword matching rather than a package list
 *
 * A head unit like this one is a grab bag of vendor apps with unguessable package
 * names — the AUX input, the TPMS reader and the CarPlay dongle app are all
 * whitelabelled builds whose identifiers vary by production batch. Matching on
 * keywords across the package *and* the label catches them without needing the deck
 * in front of you, and when it misses, it misses into the monogram rather than into
 * someone else's artwork. That is a curated system with a safe default, which is
 * what an OEM actually ships.
 */
object DwmIcons {

    /**
     * The glyph for an installed app, or `null` to use [monogram].
     *
     * Order matters: the specific tests come before the general ones, so a "Reverse
     * Camera" app does not get claimed by the media rule on the word "camera" the
     * way it would if the general cases ran first.
     */
    @DrawableRes
    fun forApp(pkg: String, label: String): Int? {
        val k = "$pkg $label".lowercase()
        return when {
            has(k, "tpms", "tyre", "tire", "pressure") -> R.drawable.ic_dwm_tpms
            has(k, "aux", "reverse", "rearview", "avin", "cvbs") -> R.drawable.ic_dwm_camera
            has(k, "carplay", "zlink", "carlink", "autokit", "easyconn", "carbit",
                "projection") -> R.drawable.ic_dwm_carplay
            has(k, "bluetooth", "btmusic", "btphone") -> R.drawable.ic_dwm_bluetooth
            has(k, "obd", "torque", "diag", "elm327") -> R.drawable.ic_dwm_gauge
            // Navigation and positioning are two different things and were sharing
            // one glyph, which put identical icons on the Maps and Waze tiles.
            has(k, "maps", "waze", "navi", "route") -> R.drawable.ic_dwm_nav
            has(k, "gps", "location", "speed") -> R.drawable.ic_dwm_speed
            has(k, "spotify", "music", "media", "audio", "player",
                "radio") -> R.drawable.ic_dwm_media
            has(k, "youtube", "video", "movie", "cinema") -> R.drawable.ic_dwm_play
            has(k, "chrome", "browser", "webview", "internet") -> R.drawable.ic_dwm_web
            has(k, "gallery", "photo", "image", "picture") -> R.drawable.ic_dwm_image
            has(k, "clock", "alarm", "timer") -> R.drawable.ic_dwm_clock
            has(k, "camera") -> R.drawable.ic_dwm_camera
            has(k, "setting", "config") -> R.drawable.ic_dwm_settings
            else -> null
        }
    }

    private fun has(haystack: String, vararg needles: String) =
        needles.any { it in haystack }

    /**
     * The typographic fallback: one or two letters, set in the display face.
     *
     * Two letters, not one, because a grid of single capitals is ambiguous the
     * moment two apps share an initial — and not three, because the tiles are on a
     * grid and a monogram that changes width would break the rhythm the grid exists
     * to create. Short labels are kept whole: "AUX" reads as itself, and "AU" would
     * not.
     */
    fun monogram(label: String): String {
        val clean = label.trim()
        if (clean.isEmpty()) return "?"
        if (clean.length <= 3 && ' ' !in clean) return clean.uppercase()

        val words = clean.split(' ', '-', '_').filter { it.isNotBlank() }
        return when {
            words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase()
            else -> clean.take(2).uppercase()
        }
    }
}
