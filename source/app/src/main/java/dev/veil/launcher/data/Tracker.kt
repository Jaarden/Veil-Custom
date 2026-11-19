package dev.veil.launcher.data

import java.io.IOException

/** The watch-tracking services Veil can read Continue Watching from. Only one is connected at a time. */
enum class Service(val id: String, val title: String, val blurb: String) {
    TRAKT("trakt", "Trakt", "The most widely used tracker. Nuvio syncs to it."),
    SIMKL("simkl", "Simkl", "Tracks TV, anime and movies, with a free API."),
    MDBLIST("mdblist", "MDBList", "Lists, ratings and Up Next, popular with Stremio users."),
    PLEX("plex", "Plex", "Continue Watching from your own Plex server. Opens in the Plex app."),
    ;

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: TRAKT
    }
}

/** The tracker picked on the Connect screen (saved as "tracker" in Veil's prefs), for code outside Home. */
fun currentTracker(context: android.content.Context): Tracker =
    when (Service.of(context.getSharedPreferences("veil", android.content.Context.MODE_PRIVATE).getString("tracker", null))) {
        Service.TRAKT -> Trakt(context)
        Service.SIMKL -> Simkl(context)
        Service.MDBLIST -> MdbList(context)
        Service.PLEX -> Plex(context)
    }

/** A device-code sign-in in progress: show [userCode] and [url], or the QR of [qrUrl] (code filled in). */
data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val url: String,
    val qrUrl: String,
    val interval: Int,
    val expiresIn: Int,
)

enum class Poll { AUTHORIZED, PENDING, SLOW_DOWN, FAILED }

/** Thrown when a service rejects the client ID (wrong, deleted or not set up). */
class BadClientId(service: String) : IOException("$service rejected the client ID")

/** What Veil needs from a tracker: TV sign-in, the Continue Watching row, and the two hold-menu actions. */
interface Tracker {
    val service: Service
    /** A client ID is built in (or typed in, for Trakt). */
    val hasKeys: Boolean
    val isConnected: Boolean
    fun requestDeviceCode(): DeviceCode
    fun pollDeviceToken(dc: DeviceCode): Poll
    fun disconnect()
    /** Paused items and next episodes, newest first, without artwork (see [ContinueWatching.withArt]). */
    suspend fun continueWatching(): List<WatchItem>
    /** The source also knows what was newly added to your library (Plex), for Home's second row. */
    val hasRecentlyAdded: Boolean get() = false
    /** Newly added movies and shows, newest first, without artwork. */
    suspend fun recentlyAdded(): List<WatchItem> = emptyList()
    /** Adds the episode/movie to watch history (watched now) and clears its paused progress. */
    fun markWatched(item: WatchItem): Boolean
    /** Takes the item out of Continue Watching on the service itself. */
    fun removeFromProgress(item: WatchItem): Boolean
}
