package dev.veil.launcher.data

import android.content.Context
import dev.veil.launcher.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Plex, signed in with a link code (plex.tv/link). Continue Watching is the server's own Continue Watching
 * hub (paused items and the next episode of shows you're watching), read straight from your Plex Media
 * Server(s). Items open in the Plex app. Artwork comes from TMDB like every other source, with the
 * server's own artwork as the fallback for things TMDB doesn't know (home videos, unmatched items).
 *
 * No client ID is needed: Plex identifies an app by a random id made on the TV.
 */
class Plex(context: Context) : Tracker {
    override val service = Service.PLEX
    private val prefs = context.getSharedPreferences("auth_plex", Context.MODE_PRIVATE)

    override val hasKeys get() = true
    override val isConnected get() = prefs.getString("token", null) != null

    /** Random id for this install; Plex lists Veil under it in Settings > Authorized devices. */
    private val clientId: String
        @Synchronized get() = prefs.getString("client", null)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString("client", it).apply() }

    private fun headers(token: String?): Map<String, String> = buildMap {
        put("X-Plex-Product", "Veil")
        put("X-Plex-Version", BuildConfig.VERSION_NAME)
        put("X-Plex-Client-Identifier", clientId)
        put("X-Plex-Platform", "Android")
        put("X-Plex-Device", "Android TV")
        put("X-Plex-Device-Name", "Veil")
        if (token != null) put("X-Plex-Token", token)
    }

    // ---- Sign-in: a 4-character code typed in at plex.tv/link ----

    override fun requestDeviceCode(): DeviceCode {
        val (code, text) = Http.call(
            "POST", "$TV/pins",
            headers(null) + ("Content-Type" to "application/x-www-form-urlencoded"), "strong=false",
        )
        if (code !in 200..299) throw IOException("Plex returned $code")
        val j = JSONObject(text)
        return DeviceCode(
            deviceCode = j.getLong("id").toString(),
            userCode = j.getString("code"),
            url = LINK,
            qrUrl = LINK, // plex.tv/link has no "code filled in" address; the QR just opens the page
            interval = 2,
            expiresIn = j.optInt("expiresIn", 900),
        )
    }

    override fun pollDeviceToken(dc: DeviceCode): Poll {
        val (code, text) = Http.call("GET", "$TV/pins/${dc.deviceCode}", headers(null))
        return when {
            code == 200 -> {
                val token = JSONObject(text).str("authToken") ?: return Poll.PENDING
                prefs.edit().putString("token", token).remove("servers").apply()
                Poll.AUTHORIZED
            }
            code == 404 -> Poll.FAILED // the code expired
            code == 429 -> Poll.SLOW_DOWN
            else -> Poll.PENDING
        }
    }

    /** Signs out here. The install id is kept, so signing in again doesn't add a second device on Plex. */
    override fun disconnect() {
        prefs.edit().remove("token").remove("servers").apply()
        showIds.clear()
    }

    // ---- Servers ----

    /** A Plex Media Server and the one address that answered for it. */
    private data class Server(val id: String, val name: String, val uri: String, val token: String)

    private fun cachedServers(): List<Server> {
        val arr = runCatching { JSONArray(prefs.getString("servers", null) ?: return emptyList()) }.getOrNull()
            ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Server(o.str("id") ?: return@mapNotNull null, o.str("name") ?: "Plex", o.str("uri") ?: return@mapNotNull null, o.str("token") ?: return@mapNotNull null)
        }
    }

    /**
     * Asks plex.tv for your servers and finds an address that answers for each: on your network first,
     * then from outside, then Plex's relay. Saved, so this only runs again when a server stops answering.
     */
    private fun discoverServers(): List<Server> {
        val token = prefs.getString("token", null) ?: return emptyList()
        val (code, text) = Http.call("GET", "$TV/resources?includeHttps=1&includeRelay=1", headers(token))
        if (code == 401) { disconnect(); throw IOException("Plex sign-in is no longer valid") }
        if (code != 200) throw IOException("Plex returned $code")
        val arr = JSONArray(text)
        val out = ArrayList<Server>()
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            if (r.str("provides")?.split(',')?.contains("server") != true) continue
            val id = r.str("clientIdentifier") ?: continue
            val access = r.str("accessToken") ?: token
            val conns = r.optJSONArray("connections") ?: continue
            val uris = (0 until conns.length()).mapNotNull { conns.optJSONObject(it) }
                .filter { it.str("protocol") == "https" } // Veil doesn't send your token over plain http
                .sortedBy { c -> if (c.optBoolean("relay")) 2 else if (c.optBoolean("local")) 0 else 1 }
                .mapNotNull { it.str("uri") }
            val uri = uris.firstOrNull { u ->
                runCatching { Http.call("GET", "$u/identity", headers(access), timeoutMs = 4_000).first == 200 }.getOrDefault(false)
            } ?: continue
            out += Server(id, r.str("name") ?: "Plex", uri, access)
        }
        val json = JSONArray()
        out.forEach { json.put(JSONObject().put("id", it.id).put("name", it.name).put("uri", it.uri).put("token", it.token)) }
        prefs.edit().putString("servers", json.toString()).apply()
        return out
    }

    private fun get(s: Server, path: String): String {
        val (code, text) = Http.call("GET", s.uri + path, headers(s.token))
        if (code !in 200..299) throw IOException("${s.name} returned $code")
        return text
    }

    // ---- Continue Watching ----

    override suspend fun continueWatching(): List<WatchItem> {
        var servers = cachedServers()
        var lists = servers.map { s -> runCatching { load(s) } }
        if (servers.isEmpty() || lists.any { it.isFailure }) {
            // A server moved (new address) or was added: look them up again, once.
            servers = discoverServers()
            lists = servers.map { s -> runCatching { load(s) } }
        }
        if (servers.isNotEmpty() && lists.all { it.isFailure }) throw IOException("No Plex server answered")
        val all = lists.flatMap { it.getOrDefault(emptyList()) }
        // One server: keep its own order (it already is "most recent first"). Several: newest first.
        val ordered = if (servers.size > 1) all.sortedByDescending { it.sortTime } else all
        return ordered.distinctBy { it.key }.take(MAX_ITEMS)
    }

    private fun load(s: Server): List<WatchItem> {
        val text = runCatching { get(s, "/hubs/continueWatching/items?includeGuids=1&X-Plex-Container-Start=0&X-Plex-Container-Size=30") }
            .getOrElse { get(s, "/library/onDeck?includeGuids=1") } // older servers
        val arr = metadata(text)
        val now = System.currentTimeMillis()
        return (0 until arr.length()).mapNotNull { i ->
            val m = arr.optJSONObject(i) ?: return@mapNotNull null
            // Up-next episodes were never played themselves: keep the server's order for those.
            val t = m.optLong("lastViewedAt", 0L).takeIf { it > 0 }?.times(1000) ?: (now - i * 60_000L)
            item(s, m, sortTime = t, added = false)
        }
    }

    private fun metadata(text: String): JSONArray =
        JSONObject(text).obj("MediaContainer")?.optJSONArray("Metadata") ?: JSONArray()

    /** A movie or episode as a row item, or null for anything else (or when it's all but finished). */
    private fun item(s: Server, m: JSONObject, sortTime: Long, added: Boolean): WatchItem? {
        val ratingKey = m.str("ratingKey") ?: return null
        val isMovie = when (m.str("type")) { "movie" -> true; "episode" -> false; else -> return null }
        val duration = m.optLong("duration", 0L)
        val offset = m.optLong("viewOffset", 0L)
        val progress = if (duration > 0) (offset.toFloat() / duration).coerceIn(0f, 1f) else 0f
        if (progress >= 0.95f && !added) return null
        // Movies carry their own ids. For an episode the ids that TMDB artwork needs are the show's.
        val ids = if (isMovie) Ids.of(m) else m.str("grandparentRatingKey")?.let { showIds(s, it) } ?: Ids.NONE
        val art = (if (isMovie) m.str("art") else m.str("grandparentArt") ?: m.str("art"))
        val poster = (if (isMovie) m.str("thumb") else m.str("grandparentThumb") ?: m.str("parentThumb"))
        return WatchItem(
            isMovie = isMovie,
            title = (if (isMovie) m.str("title") else m.str("grandparentTitle") ?: m.str("title")) ?: "Untitled",
            traktId = null,
            imdb = ids.imdb,
            tmdb = ids.tmdb,
            tvdb = ids.tvdb,
            serviceId = "${s.id}/$ratingKey",
            season = m.int("parentIndex") ?: 0,
            episode = m.int("index") ?: 0,
            episodeTitle = if (isMovie) null else m.str("title"),
            progress = if (progress >= 0.95f) 0f else progress,
            runtime = (duration / 60_000L).toInt().takeIf { it > 0 },
            upNext = offset == 0L && !added,
            added = added,
            sortTime = sortTime,
            backdrop = art?.let { image(s, it, 1920, 1080) },
            thumb = art?.let { image(s, it, 780, 439) },
            poster = poster?.let { image(s, it, 342, 513) },
        )
    }

    // ---- Recently added ----

    override val hasRecentlyAdded get() = true

    override suspend fun recentlyAdded(): List<WatchItem> {
        var servers = cachedServers()
        var lists = servers.map { s -> runCatching { loadAdded(s) } }
        if (servers.isEmpty() || lists.any { it.isFailure }) {
            servers = discoverServers()
            lists = servers.map { s -> runCatching { loadAdded(s) } }
        }
        if (servers.isNotEmpty() && lists.all { it.isFailure }) throw IOException("No Plex server answered")
        return lists.flatMap { it.getOrDefault(emptyList()) }.sortedByDescending { it.sortTime }.take(MAX_ITEMS)
    }

    /**
     * The server's "recently added" across its movie and TV libraries: movies as they are, and one entry
     * per show (Plex reports new TV by season). A show's entry is its first unwatched episode of the new
     * season, so pressing OK plays the right thing.
     */
    private fun loadAdded(s: Server): List<WatchItem> {
        val arr = metadata(get(s, "/library/recentlyAdded?includeGuids=1&X-Plex-Container-Start=0&X-Plex-Container-Size=50"))
        val out = ArrayList<WatchItem>()
        val seenShows = HashSet<String>()
        for (i in 0 until arr.length()) {
            if (out.size >= MAX_ITEMS) break
            val m = arr.optJSONObject(i) ?: continue
            val t = m.optLong("addedAt", 0L) * 1000
            val key = m.str("ratingKey") ?: continue
            when (m.str("type")) {
                "movie" -> item(s, m, t, added = true)?.let { out += it }
                "episode" -> if (seenShows.add(m.str("grandparentRatingKey") ?: key)) item(s, m, t, added = true)?.let { out += it }
                "season", "show" -> {
                    val isSeason = m.str("type") == "season"
                    if (!seenShows.add((if (isSeason) m.str("parentRatingKey") else key) ?: key)) continue
                    val episodes = runCatching {
                        metadata(get(s, "/library/metadata/$key/" + if (isSeason) "children" else "allLeaves"))
                    }.getOrNull() ?: continue
                    val list = (0 until episodes.length()).mapNotNull { episodes.optJSONObject(it) }.filter { it.str("type") == "episode" }
                    val ep = list.firstOrNull { it.optInt("viewCount", 0) == 0 } ?: list.firstOrNull() ?: continue
                    item(s, ep, t, added = true)?.let { out += it }
                }
            }
        }
        return out
    }

    /** The server resizes its own artwork, so the TV never downloads a huge original. */
    private fun image(s: Server, path: String, w: Int, h: Int) =
        "${s.uri}/photo/:/transcode?width=$w&height=$h&minSize=1&upscale=1&url=${DeviceAuth.enc(path)}" +
            "&X-Plex-Token=${DeviceAuth.enc(s.token)}"

    private data class Ids(val imdb: String?, val tmdb: Int?, val tvdb: Int?) {
        companion object {
            val NONE = Ids(null, null, null)

            /** From the new agents' Guid list (tmdb://1, imdb://tt1, tvdb://1), else the old agents' single guid. */
            fun of(m: JSONObject): Ids {
                var imdb: String? = null; var tmdb: Int? = null; var tvdb: Int? = null
                val guids = m.optJSONArray("Guid")
                val all = (0 until (guids?.length() ?: 0)).mapNotNull { guids?.optJSONObject(it)?.str("id") } + listOfNotNull(m.str("guid"))
                for (g in all) {
                    val v = g.substringAfter("://", "").substringBefore('?').substringBefore('/')
                    when {
                        g.startsWith("imdb://") || "agents.imdb://" in g -> imdb = imdb ?: v.takeIf { it.startsWith("tt") }
                        g.startsWith("tmdb://") || "agents.themoviedb://" in g -> tmdb = tmdb ?: v.toIntOrNull()
                        g.startsWith("tvdb://") || "agents.thetvdb://" in g -> tvdb = tvdb ?: v.toIntOrNull()
                    }
                }
                return Ids(imdb, tmdb, tvdb)
            }
        }
    }

    private val showIds = ConcurrentHashMap<String, Ids>()

    private fun showIds(s: Server, showKey: String): Ids =
        showIds.getOrPut("${s.id}/$showKey") {
            runCatching {
                JSONObject(get(s, "/library/metadata/$showKey?includeGuids=1"))
                    .obj("MediaContainer")?.optJSONArray("Metadata")?.optJSONObject(0)?.let { Ids.of(it) }
            }.getOrNull() ?: return Ids.NONE // not cached: try again next time
        }

    // ---- Finding a title from elsewhere (the Request screen) on your servers ----

    /**
     * Looks a movie or show up on your servers and returns something to play, as a [WatchItem.serviceId]:
     * the movie itself, or the show's first unwatched episode. Matched by its TMDB id; if the server's
     * entry carries no ids, by exact title (and year). Null when it isn't there.
     */
    fun findPlayable(isMovie: Boolean, title: String, year: String?, tmdb: Int): String? {
        val servers = cachedServers().ifEmpty { discoverServers() }
        val type = if (isMovie) "movie" else "show"
        val q = DeviceAuth.enc(title).replace("+", "%20")
        for (s in servers) {
            val candidates = ArrayList<JSONObject>()
            runCatching {
                val arr = metadata(get(s, "/library/all?type=${if (isMovie) 1 else 2}&title=$q&includeGuids=1&X-Plex-Container-Start=0&X-Plex-Container-Size=30"))
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { candidates += it }
            }
            if (candidates.isEmpty()) runCatching {
                val hubs = JSONObject(get(s, "/hubs/search?query=$q&limit=20&includeGuids=1")).obj("MediaContainer")?.optJSONArray("Hub")
                for (h in 0 until (hubs?.length() ?: 0)) {
                    val arr = hubs?.optJSONObject(h)?.optJSONArray("Metadata") ?: continue
                    for (i in 0 until arr.length()) arr.optJSONObject(i)?.takeIf { it.str("type") == type }?.let { candidates += it }
                }
            }
            val match = candidates.firstOrNull { it.str("type") == type && Ids.of(it).tmdb == tmdb }
                ?: candidates.firstOrNull {
                    it.str("type") == type && it.str("title").equals(title, ignoreCase = true) &&
                        (year == null || it.int("year") == null || it.int("year").toString() == year)
                }
            android.util.Log.i("VeilPlex", "find $type tmdb=$tmdb on ${s.name}: ${candidates.size} candidates, match=${match?.str("ratingKey")}")
            var key = match?.str("ratingKey") ?: continue
            if (!isMovie) {
                val eps = metadata(get(s, "/library/metadata/$key/allLeaves"))
                val list = (0 until eps.length()).mapNotNull { eps.optJSONObject(it) }.filter { it.str("type") == "episode" }
                key = (list.firstOrNull { it.optInt("viewCount", 0) == 0 } ?: list.firstOrNull())?.str("ratingKey") ?: continue
            }
            return "${s.id}/$key"
        }
        return null
    }

    // ---- Hold-menu actions, done on the server ----

    private fun target(item: WatchItem): Pair<Server, String>? {
        val (serverId, ratingKey) = item.serviceId?.split('/', limit = 2)?.takeIf { it.size == 2 } ?: return null
        val server = cachedServers().firstOrNull { it.id == serverId } ?: return null
        return server to ratingKey
    }

    override fun markWatched(item: WatchItem): Boolean {
        val (s, key) = target(item) ?: return false
        return Http.call(
            "GET", "${s.uri}/:/scrobble?identifier=com.plexapp.plugins.library&key=${DeviceAuth.enc(key)}", headers(s.token),
        ).first in 200..299
    }

    /** Plex's own "Remove from Continue Watching" (it comes back when you play the item again). */
    override fun removeFromProgress(item: WatchItem): Boolean {
        val (s, key) = target(item) ?: return false
        return Http.call(
            "PUT", "${s.uri}/actions/removeFromContinueWatching?ratingKey=${DeviceAuth.enc(key)}", headers(s.token),
        ).first in 200..299
    }

    companion object {
        private const val TV = "https://plex.tv/api/v2"
        private const val LINK = "https://plex.tv/link"
        private const val MAX_ITEMS = 10
        const val APP_PKG = "com.plexapp.android"

        /**
         * plex:// address that starts playing an item in the Plex app, from a [WatchItem.serviceId]. This is
         * the Plex app's own "watch" link (checked on Plex for Android TV 2026.22); the older
         * plex://server://... form only opens the app's home screen there.
         */
        fun deepLink(serviceId: String?): String? {
            val (serverId, ratingKey) = serviceId?.split('/', limit = 2)?.takeIf { it.size == 2 } ?: return null
            val item = "server://$serverId/com.plexapp.plugins.library/library/metadata/$ratingKey"
            return "plex://watch/video?uri=" + DeviceAuth.enc(item)
        }
    }
}
