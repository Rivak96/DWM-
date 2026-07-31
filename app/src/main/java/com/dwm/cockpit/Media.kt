package com.dwm.cockpit

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

/**
 * Whatever is playing, and the buttons to control it.
 *
 * Reads the real [android.media.session.MediaSession] rather than mirroring a
 * notification, because a session carries artwork, duration and actual transport
 * controls — a notification only carries text. The cost is that it needs
 * notification-listener access, which DWM already declares and already knows how
 * to check ([NotifStore.accessGranted]).
 *
 * Three states, and all three have to look deliberate. After four releases of
 * shipping screens that turned out to be empty, "no music" must never render as a
 * blank box:
 *
 *  - [State.NoAccess]  — permission not granted; the card offers to open the
 *                        settings screen that grants it.
 *  - [State.Idle]      — access granted, nothing playing. Likely if audio runs
 *                        through the CarPlay dongle instead of an Android app,
 *                        since the dongle may publish no session at all. The card
 *                        becomes a labelled launch tile.
 *  - [State.Playing]   — a real session; artwork, title, artist, controls.
 */
object Media {

    sealed class State {
        object NoAccess : State()
        object Idle : State()
        data class Playing(
            val pkg: String,
            val title: String,
            val artist: String,
            val art: Bitmap?,
            val playing: Boolean
        ) : State()
    }

    /** Preferred when several apps hold a session — the user asked for Apple Music. */
    private val PREFERRED = listOf(
        "com.apple.android.music",
        "com.spotify.music",
        "com.google.android.apps.youtube.music"
    )

    private var controller: MediaController? = null

    fun state(c: Context): State {
        if (!NotifStore.accessGranted(c)) return State.NoAccess
        val ctl = pick(c) ?: run { controller = null; return State.Idle }
        controller = ctl

        val md = runCatching { ctl.metadata }.getOrNull()
        val ps = runCatching { ctl.playbackState }.getOrNull()
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = (md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty()
        // Nothing worth showing yet: treat as idle rather than drawing an empty card.
        if (title.isBlank() && artist.isBlank()) return State.Idle

        val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        return State.Playing(
            pkg = ctl.packageName,
            title = title,
            artist = artist,
            art = art,
            playing = ps?.state == PlaybackState.STATE_PLAYING
        )
    }

    /**
     * The session to show. Prefers a genuinely playing one, then a known music
     * app, then whatever is first — so a paused podcast doesn't outrank live
     * music.
     */
    private fun pick(c: Context): MediaController? = runCatching {
        val msm = c.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val comp = ComponentName(c, DwmNotificationListener::class.java)
        // Throws SecurityException the moment access is revoked, hence runCatching
        // around the whole thing rather than a pre-check that can go stale.
        val sessions = msm.getActiveSessions(comp)
        sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.packageName in PREFERRED }
            ?: sessions.firstOrNull()
    }.getOrNull()

    fun playPause() {
        val ctl = controller ?: return
        runCatching {
            if (ctl.playbackState?.state == PlaybackState.STATE_PLAYING) ctl.transportControls.pause()
            else ctl.transportControls.play()
        }
    }

    fun next() {
        runCatching { controller?.transportControls?.skipToNext() }
    }

    fun previous() {
        runCatching { controller?.transportControls?.skipToPrevious() }
    }

    /** The app to open when the card is tapped: whatever holds the session, or
     *  Apple Music if nothing does. */
    fun launchTarget(c: Context): String? {
        (controller?.packageName)?.let { return it }
        val installed = Apps.all(c).map { it.pkg }
        return PREFERRED.firstOrNull { it in installed }
    }
}
