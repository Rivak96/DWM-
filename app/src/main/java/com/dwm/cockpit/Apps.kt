package com.dwm.cockpit

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

data class AppEntry(val pkg: String, val label: String)

/** Enumerates launchable apps and fetches their icons/labels. */
object Apps {
    fun all(c: Context): List<AppEntry> {
        val pm = c.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(main, 0)
            .mapNotNull { ri ->
                val p = ri.activityInfo.packageName
                if (p == c.packageName) null else AppEntry(p, ri.loadLabel(pm).toString())
            }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    fun icon(c: Context, pkg: String): Drawable? =
        runCatching { c.packageManager.getApplicationIcon(pkg) }.getOrNull()

    fun label(c: Context, pkg: String): String =
        runCatching {
            val pm = c.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)

    /** The dock list: user-saved favourites, or a sensible default set. */
    fun effectiveFavorites(c: Context): List<String> {
        val saved = Prefs.favorites(c)
        if (saved.isNotEmpty()) return saved
        val prefer = listOf(
            "com.google.android.apps.maps", "com.waze", "com.spotify.music",
            "com.android.chrome", "com.google.android.gm"
        )
        val installed = all(c).map { it.pkg }
        val chosen = prefer.filter { it in installed }.toMutableList()
        for (a in installed) {
            if (chosen.size >= 6) break
            if (a !in chosen) chosen.add(a)
        }
        return chosen.take(6)
    }
}
