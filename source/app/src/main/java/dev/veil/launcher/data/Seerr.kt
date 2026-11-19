package dev.veil.launcher.data

import org.json.JSONObject
import java.io.IOException

/**
 * Seerr (Overseerr / Jellyseerr): search for a movie or show and request it for your library, from the
 * TV. Talks only to your own Seerr server, with the address and API key from keys.properties.
 */
class Seerr(private val context: android.content.Context, baseUrl: String, private val key: String) {
    private val base = baseUrl.trim().trimEnd('/')
    val enabled: Boolean get() = base.startsWith("https://") && key.isNotBlank()

    data class Media(
        val id: Int,
        val isMovie: Boolean,
        val title: String,
        val year: String?,
        val poster: String?,
        /** Release / first-air date (yyyy-mm-dd) and TMDB popularity, to mix movies and shows in one list. */
        val date: String? = null,
        val popularity: Double = 0.0,
        /** TMDB's own user score (0-10), shown when there is no IMDb score; 0 when nobody voted. */
        val tmdbScore: Double = 0.0,
        /** Seerr's media status: 2-3 requested, 4 partly available, 5 available; anything else: not there. */
        val status: Int,
    ) {
        val key: String get() = (if (isMovie) "m" else "t") + id
        val available: Boolean get() = status == AVAILABLE
        /** All or part of it is in your library, so there's something to play. */
        val inLibrary: Boolean get() = status == AVAILABLE || status == PARTIAL
        val requested: Boolean get() = status == PENDING || status == PROCESSING
        /** Badge on the poster, or null when it's neither in your library nor requested. */
        val badge: String?
            get() = when (status) {
                AVAILABLE -> "Available"
                PARTIAL -> "Partly available"
                PENDING, PROCESSING -> "Requested"
                else -> null
            }
    }

    private fun headers() = mapOf("X-Api-Key" to key)

    /**
     * A request to your server. If its name can't be found (a home-only name, with ad blocking's outside
     * DNS switched on), the address is asked from the router instead and the request is sent there.
     */
    private fun call(method: String, path: String, body: String? = null): Pair<Int, String> {
        val url = "$base/api/v1$path"
        val host = java.net.URL(base).host
        try {
            return Http.call(method, url, headers(), body)
        } catch (e: java.net.UnknownHostException) {
            val address = LocalDns.resolve(context, host) ?: throw e
            try {
                return Http.call(method, url, headers(), body, address = address)
            } catch (e2: java.io.IOException) {
                LocalDns.forget(host) // the server may have moved
                throw e2
            }
        }
    }

    /** What's trending now, for the screen before you search. */
    fun trending(page: Int = 1): List<Media> = parse(get("/discover/trending?page=$page"))

    /** IMDb score (0-10) and Rotten Tomatoes critics score (0-100 %), each null when unknown. */
    data class Ratings(val imdb: Double?, val rt: Int?)

    /**
     * Scores for one title, as Seerr looks them up. Movies get IMDb and Rotten Tomatoes; for shows Seerr
     * only knows Rotten Tomatoes. Unreleased or obscure titles often have neither.
     */
    fun ratings(m: Media): Ratings {
        val (code, text) = call("GET", if (m.isMovie) "/movie/${m.id}/ratingscombined" else "/tv/${m.id}/ratings")
        if (code == 404) return Ratings(null, null) // "No ratings found"
        if (code !in 200..299) throw IOException("Seerr returned $code")
        val j = JSONObject(text)
        val rt = if (m.isMovie) j.obj("rt") else j
        return Ratings(
            imdb = j.obj("imdb")?.optDouble("criticsScore", 0.0)?.takeIf { it > 0 },
            rt = rt?.int("criticsScore")?.takeIf { it in 0..100 && rt.str("criticsRating") != null },
        )
    }

    /** A streaming service (TMDB's "watch provider"), to browse what's on it. */
    data class Provider(val id: Int, val name: String)

    /**
     * The well-known streaming services available in [region] (a country code like NL), in a fixed,
     * familiar order. TMDB lists dozens per country; most are tiny, so only the big names are offered.
     */
    fun providers(region: String): List<Provider> {
        val found = LinkedHashMap<Int, String>()
        for (kind in listOf("movies", "tv")) {
            val arr = runCatching { org.json.JSONArray(get("/watchproviders/$kind?watchRegion=${DeviceAuth.enc(region)}")) }.getOrNull() ?: continue
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                found.putIfAbsent(p.int("id") ?: continue, p.str("name") ?: continue)
            }
        }
        return KNOWN_PROVIDERS.mapNotNull { id -> found[id]?.let { Provider(id, it.replace("Amazon Prime Video", "Prime Video").replace("Disney Plus", "Disney+").replace("Paramount Plus", "Paramount+")) } }
    }

    /**
     * Browse movies or shows: most popular, or newest first ([latest], released up to today and with a few
     * votes, which keeps out the obscure and the unreleased), optionally only what's on one streaming
     * service in [region].
     */
    fun discover(movies: Boolean, latest: Boolean, provider: Int?, region: String, page: Int = 1): List<Media> {
        val dateField = if (movies) "primary_release_date" else "first_air_date"
        val q = StringBuilder(if (movies) "/discover/movies?page=$page" else "/discover/tv?page=$page")
        if (latest) {
            q.append("&sortBy=$dateField.desc&voteCountGte=5")
            q.append(if (movies) "&primaryReleaseDateLte=" else "&firstAirDateLte=").append(java.time.LocalDate.now())
        } else {
            q.append("&sortBy=popularity.desc")
        }
        if (provider != null) q.append("&watchProviders=$provider&watchRegion=${DeviceAuth.enc(region)}")
        return parse(get(q.toString()))
    }

    /** Seerr wants %20 for spaces (it rejects the "+" that form encoding makes). */
    fun search(query: String, page: Int = 1): List<Media> =
        parse(get("/search?query=" + DeviceAuth.enc(query.trim()).replace("+", "%20") + "&page=$page"))

    private fun get(path: String): String {
        val (code, text) = call("GET", path)
        if (code == 401 || code == 403) throw IOException("Seerr didn't accept the API key")
        if (code !in 200..299) throw IOException("Seerr returned $code")
        return text
    }

    private fun parse(text: String): List<Media> {
        val arr = JSONObject(text).optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val r = arr.optJSONObject(i) ?: return@mapNotNull null
            val isMovie = when (r.str("mediaType")) { "movie" -> true; "tv" -> false; else -> return@mapNotNull null } // no people
            Media(
                id = r.int("id") ?: return@mapNotNull null,
                isMovie = isMovie,
                title = (if (isMovie) r.str("title") else r.str("name")) ?: r.str("originalTitle") ?: r.str("originalName") ?: return@mapNotNull null,
                year = (if (isMovie) r.str("releaseDate") else r.str("firstAirDate"))?.take(4),
                poster = r.str("posterPath")?.let { "https://image.tmdb.org/t/p/w342$it" },
                date = if (isMovie) r.str("releaseDate") else r.str("firstAirDate"),
                popularity = r.optDouble("popularity", 0.0),
                tmdbScore = if (r.optInt("voteCount", 0) > 0) r.optDouble("voteAverage", 0.0) else 0.0,
                status = r.obj("mediaInfo")?.int("status") ?: 0,
            )
        }
    }

    /**
     * Requests the movie, or every season of the show that isn't in your library yet. Returns null when
     * it worked, else a short reason to show.
     */
    fun request(m: Media): String? {
        val body = JSONObject().put("mediaType", if (m.isMovie) "movie" else "tv").put("mediaId", m.id)
        if (!m.isMovie) body.put("seasons", "all")
        val (code, text) = call("POST", "/request", body.toString())
        return when {
            code in 200..299 -> null
            code == 409 -> "It has already been requested"
            code == 401 || code == 403 -> "Seerr didn't accept the API key"
            else -> runCatching { JSONObject(text).str("message") }.getOrNull() ?: "Seerr returned $code"
        }
    }

    companion object {
        /** Netflix, Disney+, Prime Video, HBO Max, Apple TV, SkyShowtime, Videoland, Viaplay, Paramount+, Hulu, Peacock, Crunchyroll. */
        private val KNOWN_PROVIDERS = listOf(8, 337, 119, 1899, 350, 1773, 72, 76, 531, 15, 386, 283)
        const val PENDING = 2
        const val PROCESSING = 3
        const val PARTIAL = 4
        const val AVAILABLE = 5
    }
}
