package com.dwm.cockpit

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

/**
 * Grid of installed apps with search. Doubles as an app picker (pick mode).
 * Tap = launch fullscreen. Long-press = window / dock / app info / uninstall.
 */
class AppDrawerActivity : DwmActivity() {

    private var pickMode = false
    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_drawer)

        pickMode = intent.getBooleanExtra(EXTRA_PICK, false)
        findViewById<TextView>(R.id.title).text = if (pickMode) "Choose an app" else "All apps"
        findViewById<View>(R.id.close).setOnClickListener { finish() }

        val all = Apps.all(this)
        adapter = AppAdapter(this, all)
        val grid = findViewById<GridView>(R.id.grid)
        grid.adapter = adapter

        grid.setOnItemClickListener { _, _, pos, _ ->
            val entry = adapter.getItem(pos)
            if (pickMode) {
                setResult(RESULT_OK, Intent().putExtra(EXTRA_PKG, entry.pkg))
                finish()
            } else {
                LaunchEngine.launchFullscreen(this, entry.pkg)
            }
        }
        grid.setOnItemLongClickListener { _, _, pos, _ ->
            if (!pickMode) appMenu(adapter.getItem(pos))
            true
        }

        val search = findViewById<EditText>(R.id.search)
        search.background = Ui.chipBg(this)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim()?.lowercase() ?: ""
                adapter.filter(all, q)
            }
        })

        Ui.themeWindow(this)
        Ui.skin(this, findViewById(android.R.id.content))
    }

    private fun appMenu(entry: AppEntry) {
        val inDock = entry.pkg in Apps.effectiveFavorites(this)
        val dockItem = if (inDock) "Remove from dock" else "Add to dock"
        Ui.dialog(this)
            .setTitle(entry.label)
            .setItems(arrayOf("Open in window", dockItem, "App info", "Uninstall")) { _, w ->
                when (w) {
                    0 -> {
                        val s = LaunchEngine.displaySize(this)
                        LaunchEngine.launchWindow(this, entry.pkg, Rect(s.x / 6, s.y / 6, s.x * 5 / 6, s.y * 5 / 6))
                    }
                    1 -> {
                        val cur = Apps.effectiveFavorites(this).toMutableList()
                        if (inDock) cur.remove(entry.pkg)
                        else if (entry.pkg !in cur) {
                            cur.add(entry.pkg)
                            if (cur.size > 8) cur.removeAt(0)
                        }
                        Prefs.saveFavorites(this, cur)
                        Toast.makeText(this, if (inDock) "Removed from dock" else "Added to dock", Toast.LENGTH_SHORT).show()
                    }
                    2 -> runCatching {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${entry.pkg}"))
                        )
                    }
                    3 -> runCatching {
                        startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${entry.pkg}")))
                    }
                }
            }
            .show()
    }

    companion object {
        const val EXTRA_PICK = "pick"
        const val EXTRA_PKG = "pkg"
    }
}

private class AppAdapter(
    private val ctx: Context,
    initial: List<AppEntry>
) : BaseAdapter() {
    private val inflater = LayoutInflater.from(ctx)
    private var items: List<AppEntry> = initial

    fun filter(all: List<AppEntry>, q: String) {
        items = if (q.isEmpty()) all else all.filter { it.label.lowercase().contains(q) }
        notifyDataSetChanged()
    }

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val v = convertView ?: inflater.inflate(R.layout.item_app, parent, false)
        val item = items[position]
        v.findViewById<ImageView>(R.id.icon).setImageDrawable(Apps.icon(ctx, item.pkg))
        v.findViewById<TextView>(R.id.label).apply {
            text = item.label
            setTextColor(Ui.th(ctx).text)
        }
        return v
    }
}
