package com.dwm.cockpit

import android.app.Notification
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Latest-notification-per-app store, fed by [DwmNotificationListener]. Lets a
 *  notification-mirror panel (e.g. TPMS) show an app's notification as an overlay
 *  that stays above CarPlay — the app window itself can't. */
object NotifStore {
    data class Item(val pkg: String, val title: String, val text: String, val time: Long)

    private val map = HashMap<String, Item>()
    private val listeners = LinkedHashSet<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    @Synchronized
    fun put(item: Item) {
        map[item.pkg] = item
        val snapshot = listeners.toList()
        main.post { snapshot.forEach { runCatching { it() } } }
    }

    @Synchronized
    fun get(pkg: String): Item? = map[pkg]

    @Synchronized
    fun addListener(l: () -> Unit) { listeners.add(l) }

    @Synchronized
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    /** True if the user has granted DWM notification access. */
    fun accessGranted(c: Context): Boolean {
        val flat = runCatching {
            Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners")
        }.getOrNull() ?: return false
        return flat.contains(c.packageName)
    }
}

/** Reads notifications so DWM can mirror a chosen app's notification on-screen. */
class DwmNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        runCatching { activeNotifications?.forEach { handle(it) } }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { handle(it) }
    }

    private fun handle(sbn: StatusBarNotification) {
        val ex = sbn.notification?.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        NotifStore.put(NotifStore.Item(sbn.packageName, title, text, sbn.postTime))
    }
}
