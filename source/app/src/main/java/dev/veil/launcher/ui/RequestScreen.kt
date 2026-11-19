@file:OptIn(ExperimentalTvMaterial3Api::class)

package dev.veil.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.veil.launcher.LauncherState
import dev.veil.launcher.SeasonPick
import dev.veil.launcher.data.Seerr
import kotlinx.coroutines.delay

private val PosterShape = RoundedCornerShape(14.dp)

/**
 * The top bar's shortcut to the Request screen, next to the veil button, with Seerr's own icon (only to
 * identify the service). [short] when the bar is crowded (a "now playing" pill is showing).
 */
@Composable
fun RequestPill(onClick: () -> Unit, short: Boolean, modifier: Modifier = Modifier) {
    Pill(onClick = onClick, modifier = modifier, padding = PaddingValues(start = 5.dp, end = 13.dp, top = 4.dp, bottom = 4.dp), soundPitch = 1.12f) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(dev.veil.launcher.R.drawable.logo_seerr), contentDescription = null,
            modifier = Modifier.size(22.dp).clip(CircleShape),
        )
        Text(
            if (short) "Request" else "Request movie or TV show on Seerr",
            fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
    }
}

/**
 * Search for a movie or show and request it from your Seerr server. Until you search, it shows what's
 * trending. Posters carry a badge when the title is already in your library or already requested.
 */
@Composable
fun RequestScreen(state: LauncherState) {
    val searchFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    // The keyboard only opens when you press OK on the search pill, not just because the screen opened.
    var typing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(state.requestQuery) }
    LaunchedEffect(state.requestQuery) { text = state.requestQuery } // picking a service clears the search
    val submit = {
        typing = false
        state.searchRequests(text)
    }
    LaunchedEffect(Unit) { delay(80); runCatching { searchFocus.requestFocus() } }
    // Back while browsing the posters: focus returns to the filters (the chip that is on).
    val filterFocus = remember { FocusRequester() }
    val providerRow = androidx.compose.foundation.lazy.rememberLazyListState()
    // The chip you used last, else the type that is on. Chips are told apart by these keys.
    val backTarget = state.requestLastFilter ?: "type:${state.requestType.name}"
    LaunchedEffect(state.requestFilterTick) {
        if (state.requestFilterTick == 0) return@LaunchedEffect
        // A service chip that has scrolled out of the row isn't on screen to take focus: bring it back first.
        if (backTarget.startsWith("provider:")) {
            val id = backTarget.removePrefix("provider:").toIntOrNull()
            val index = if (id == null) 0 else state.requestProviders.indexOfFirst { it.id == id } + 1
            runCatching { providerRow.scrollToItem(index.coerceAtLeast(0)) }
            delay(60)
        }
        runCatching { filterFocus.requestFocus() }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { state.requestGridFocused = false } }
    LaunchedEffect(typing) {
        delay(80)
        runCatching { (if (typing) fieldFocus else searchFocus).requestFocus() }
    }

    Column(Modifier.fillMaxSize().background(Veil.Bg).padding(start = 48.dp, end = 48.dp, top = 26.dp)) {
        TopBar(state, Modifier.zIndex(1f))
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Request", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp)
            Spacer(Modifier.width(22.dp))
            if (typing) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Veil.Text, fontSize = 13.sp, fontFamily = Manrope),
                    cursorBrush = SolidColor(Veil.Accent),
                    keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }, onDone = { submit() }),
                    modifier = Modifier.width(420.dp).focusRequester(fieldFocus)
                        .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Veil.Accent, RoundedCornerShape(50))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) Text("Movie or show", fontSize = 13.sp, color = Veil.Text.copy(alpha = 0.45f))
                            inner()
                        }
                    },
                )
            } else {
                Pill(
                    onClick = { typing = true },
                    modifier = Modifier.focusRequester(searchFocus),
                    padding = PaddingValues(start = 14.dp, end = 20.dp, top = 9.dp, bottom = 9.dp),
                ) {
                    Icon(VeilIcons.Search, contentDescription = null, modifier = Modifier.size(13.dp), tint = LocalContentColor.current)
                    Text(
                        state.requestQuery.ifEmpty { "Search movies and shows" },
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!typing) {
                Spacer(Modifier.width(8.dp))
                Pill(
                    onClick = state::startVoiceSearch,
                    padding = PaddingValues(start = 12.dp, end = 18.dp, top = 9.dp, bottom = 9.dp),
                ) {
                    Icon(VeilIcons.Mic, contentDescription = null, modifier = Modifier.size(13.dp), tint = LocalContentColor.current)
                    Text("Voice", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.width(16.dp))
            Text(
                when {
                    state.requestLoading -> "Loading…"
                    state.requestError != null -> state.requestError.orEmpty()
                    state.requestItems.isEmpty() -> "Nothing found"
                    else -> state.requestSummary
                },
                fontSize = 11.sp, color = Veil.Text.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(14.dp))
        // Filters: what kind, in which order, and (when Seerr knows your country's services) on which service.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            LauncherState.RequestType.entries.forEach { t ->
                val key = "type:${t.name}"
                FilterChip(
                    t.title, state.requestType == t,
                    modifier = if (backTarget == key) Modifier.focusRequester(filterFocus) else Modifier,
                ) { state.noteRequestFilter(key); state.chooseRequestType(t) }
            }
            Spacer(Modifier.width(14.dp))
            FilterChip(
                "Popular", !state.requestLatest,
                modifier = if (backTarget == "order:popular") Modifier.focusRequester(filterFocus) else Modifier,
            ) { state.noteRequestFilter("order:popular"); state.chooseRequestLatest(false) }
            FilterChip(
                "Latest", state.requestLatest,
                modifier = if (backTarget == "order:latest") Modifier.focusRequester(filterFocus) else Modifier,
            ) { state.noteRequestFilter("order:latest"); state.chooseRequestLatest(true) }
            Spacer(Modifier.width(14.dp))
            FilterChip(
                "In my library", state.requestInLibrary,
                modifier = if (backTarget == "library") Modifier.focusRequester(filterFocus) else Modifier,
            ) { state.noteRequestFilter("library"); state.toggleRequestInLibrary() }
        }
        if (state.requestProviders.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyRow(
                state = providerRow,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            ) {
                item(key = "any") {
                    FilterChip(
                        "Any service", state.requestProvider == null,
                        modifier = if (backTarget == "provider:any") Modifier.focusRequester(filterFocus) else Modifier,
                    ) { state.noteRequestFilter("provider:any"); state.chooseRequestProvider(null) }
                }
                items(state.requestProviders.size, key = { state.requestProviders[it].id }) { i ->
                    val p = state.requestProviders[i]
                    FilterChip(
                        p.name, state.requestProvider?.id == p.id,
                        modifier = if (backTarget == "provider:${p.id}") Modifier.focusRequester(filterFocus) else Modifier,
                    ) { state.noteRequestFilter("provider:${p.id}"); state.chooseRequestProvider(p) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
        // Close to the end of the list (the last two rows are on screen): load the next titles by itself.
        val nearEnd by androidx.compose.runtime.remember {
            androidx.compose.runtime.derivedStateOf {
                val info = grid.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                last >= info.totalItemsCount - 13
            }
        }
        LaunchedEffect(nearEnd, state.requestItems.size, state.requestLoading, state.requestHasMore) {
            if (nearEnd && state.requestItems.isNotEmpty() && state.requestHasMore && !state.requestLoading) state.moreRequests()
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            state = grid,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 40.dp),
            modifier = Modifier.fillMaxSize().onFocusChanged { state.requestGridFocused = it.hasFocus },
        ) {
            items(state.requestItems, key = { it.key }) { m ->
                // Scores are fetched for the posters actually on screen, not the whole list.
                LaunchedEffect(m.key) { state.loadRatings(m) }
                PosterTile(m, state.requestRatings[m.key], onClick = { state.requestSheet = m })
            }
            // Under the last row while the next titles load.
            if (state.requestItems.isNotEmpty() && state.requestHasMore) {
                item(key = "loading", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
                        if (state.requestLoading) Text("Loading more\u2026", fontSize = 10.sp, color = Veil.Text.copy(alpha = 0.55f))
                    }
                }
            }
        }
    }
}

/** A filter choice: outlined in the accent colour when it's the one in use. */
@Composable
private fun FilterChip(label: String, selected: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    androidx.tv.material3.Surface(
        onClick = { Sfx.select(); onClick() },
        modifier = modifier.focusSound(1.12f),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Veil.Accent.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.06f),
            contentColor = Veil.Text,
            focusedContainerColor = Veil.Text, focusedContentColor = Veil.Ink,
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(1.5.dp, Veil.Accent), shape = RoundedCornerShape(50)) else Border.None,
            focusedBorder = Border.None,
        ),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Text(
            label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun PosterTile(m: Seerr.Media, ratings: Seerr.Ratings?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = { Sfx.select(); onClick() },
        modifier = modifier.fillMaxWidth().aspectRatio(2f / 3f).focusSound(),
        shape = CardDefaults.shape(PosterShape),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.5.dp, Veil.Text), shape = PosterShape)),
        colors = CardDefaults.colors(containerColor = Color(0xFF22252C), focusedContainerColor = Color(0xFF2C3038)),
    ) {
        Box(Modifier.fillMaxSize()) {
            m.poster?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            // The title always sits on a dark fade, so it reads on any poster (and when there is none).
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .padding(start = 9.dp, end = 9.dp, top = 22.dp, bottom = 8.dp),
            ) {
                Text(m.title, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 13.sp)
                Text(
                    listOfNotNull(if (m.isMovie) "Movie" else "Show", m.year).joinToString(" · "),
                    fontSize = 8.5.sp, color = Color.White.copy(alpha = 0.7f),
                )
                // Scores: IMDb (or TMDB's own score when IMDb has none, e.g. for shows) and Rotten Tomatoes.
                val imdb = ratings?.imdb
                val rt = ratings?.rt
                val tmdb = m.tmdbScore.takeIf { imdb == null && it > 0 }
                if (imdb != null || rt != null || tmdb != null) {
                    Row(
                        Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (imdb != null) Score("IMDb", String.format(java.util.Locale.US, "%.1f", imdb), Color(0xFFF5C518))
                        if (tmdb != null) Score("TMDB", String.format(java.util.Locale.US, "%.1f", tmdb), Color(0xFF6CC6E8))
                        if (rt != null) Score("RT", "$rt%", if (rt >= 60) Color(0xFFFA5A3C) else Color(0xFF8BC34A))
                    }
                }
            }
            m.badge?.let { badge ->
                Text(
                    badge.uppercase(), fontSize = 7.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp,
                    color = if (m.available) Veil.Ink else Color.White,
                    modifier = Modifier.align(Alignment.TopStart).padding(7.dp).clip(RoundedCornerShape(50))
                        .background(if (m.available) Veil.Accent else Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** "IMDb 8.4": the source in its own colour, the score in white. */
@Composable
private fun Score(source: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(source, fontSize = 7.5.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(value, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/**
 * OK on a poster: everything about the title on the left (description, release date, genres, cast...), and
 * on the right what you can do with it (request it, choose seasons, play it).
 */
@Composable
fun RequestMenu(state: LauncherState, m: Seerr.Media) {
    val d = state.requestDetails[m.key]
    val first = remember { FocusRequester() }
    LaunchedEffect(m.key) {
        state.loadRequestDetails(m)
        Sfx.open()
        delay(80)
        runCatching { first.requestFocus() }
    }
    val choose = SheetAction("Choose seasons\u2026", VeilIcons.Grid) { state.chooseSeasons(m) }
    val actions = when {
        m.available -> listOf(SheetAction("Play on Plex", VeilIcons.Play) { state.playOnPlex(m) })
        m.status == Seerr.PARTIAL -> listOf(
            SheetAction("Play on Plex", VeilIcons.Play) { state.playOnPlex(m) },
            SheetAction("Request all missing seasons", VeilIcons.Search) { state.requestMissing(m) },
            choose,
        )
        // A show with one season requested can still get more seasons.
        m.requested -> if (m.isMovie) emptyList() else listOf(SheetAction("Request more seasons\u2026", VeilIcons.Grid) { state.chooseSeasons(m) })
        m.isMovie -> listOf(SheetAction(if (m.seen) "Request again" else "Request this movie", VeilIcons.Search) { state.request(m) })
        else -> listOf(SheetAction("Request all seasons", VeilIcons.Search) { state.request(m) }, choose)
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { Sfx.back(); state.requestSheet = null },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Row(
            Modifier.width(780.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min)
                .clip(RoundedCornerShape(20.dp)).background(Veil.Sheet),
        ) {
            // ---- The title ----
            Row(Modifier.weight(1f).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(120.dp, 180.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f))) {
                    m.poster?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(m.title, fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    // 2024 · 2 h 47 min · PG-13   /   2024 · 2 seasons · 14 episodes · TV-MA
                    val meta = listOfNotNull(
                        (d?.date ?: m.date)?.take(4),
                        d?.runtime?.let { durationText(it) },
                        d?.seasons?.let { if (it == 1) "1 season" else "$it seasons" },
                        d?.episodes?.let { "$it episodes" },
                        d?.certification,
                    ).joinToString(" \u00B7 ")
                    if (meta.isNotEmpty()) Text(meta, fontSize = 10.5.sp, color = Veil.Text.copy(alpha = 0.75f))
                    d?.genres?.takeIf { it.isNotEmpty() }?.let {
                        Text(it.joinToString(" \u00B7 "), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Veil.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    ScoreLine(m, state.requestRatings[m.key])
                    d?.tagline?.let {
                        Text(it, fontSize = 10.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = Veil.Text.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        d?.overview ?: m.overview ?: if (d == null) "" else "No description available.",
                        fontSize = 10.5.sp, lineHeight = 14.5.sp, color = Veil.Text.copy(alpha = 0.88f),
                        maxLines = 6, overflow = TextOverflow.Ellipsis,
                    )
                    if (d == null) {
                        Text("Loading details\u2026", fontSize = 9.sp, color = Veil.Text.copy(alpha = 0.5f))
                    } else {
                        Column(Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Fact("Release date", d.date?.let(::longDate))
                            Fact("Status", d.status)
                            Fact(d.creditLabel, d.credit.joinToString(", "))
                            Fact("Cast", d.cast.take(5).joinToString(", "))
                            Fact(d.makerLabel, d.makers.joinToString(", "))
                            Fact("Language", d.language)
                        }
                    }
                }
            }
            // ---- What you can do ----
            Column(
                Modifier.width(240.dp).fillMaxHeight().background(Color.Black.copy(alpha = 0.22f)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    when {
                        m.available -> "IN YOUR LIBRARY"
                        m.status == Seerr.PARTIAL -> "PARTLY IN YOUR LIBRARY"
                        m.requested -> "REQUESTED"
                        m.seen -> "ALREADY SEEN"
                        else -> "NOT IN YOUR LIBRARY"
                    },
                    fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                    color = if (m.available) Veil.Accent else Veil.Text.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                if (actions.isEmpty()) {
                    Text("This movie has already been requested.", fontSize = 10.5.sp, color = Veil.Text.copy(alpha = 0.7f), modifier = Modifier.padding(start = 4.dp))
                }
                actions.forEachIndexed { i, a ->
                    androidx.tv.material3.Surface(
                        onClick = { Sfx.select(); state.requestSheet = null; a.run() },
                        modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier).focusSound(1.12f),
                        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f), contentColor = Veil.Text,
                            focusedContainerColor = Veil.Text, focusedContentColor = Veil.Ink,
                        ),
                        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(a.icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = LocalContentColor.current)
                            Text(a.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** "Cast   Timoth\u00E9e Chalamet, Zendaya": a dim label and its value; nothing when there is no value. */
@Composable
private fun Fact(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 9.5.sp, color = Veil.Text.copy(alpha = 0.5f), modifier = Modifier.width(66.dp))
        Text(value, fontSize = 9.5.sp, color = Veil.Text.copy(alpha = 0.9f), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

/** IMDb (or TMDB's own score when IMDb has none) and Rotten Tomatoes, like on the poster. */
@Composable
private fun ScoreLine(m: Seerr.Media, ratings: Seerr.Ratings?) {
    val imdb = ratings?.imdb
    val rt = ratings?.rt
    val tmdb = m.tmdbScore.takeIf { imdb == null && it > 0 }
    if (imdb == null && rt == null && tmdb == null) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        if (imdb != null) Score("IMDb", String.format(java.util.Locale.US, "%.1f", imdb), Color(0xFFF5C518))
        if (tmdb != null) Score("TMDB", String.format(java.util.Locale.US, "%.1f", tmdb), Color(0xFF6CC6E8))
        if (rt != null) Score("RT", "$rt%", if (rt >= 60) Color(0xFFFA5A3C) else Color(0xFF8BC34A))
    }
}

/** "27 Feb 2024" in the TV's language, or the date as Seerr wrote it. */
private fun longDate(iso: String): String = runCatching {
    java.time.LocalDate.parse(iso).format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
}.getOrDefault(iso)

/**
 * Pick the seasons of a show to request. Seasons already in your library or already requested are shown
 * but can't be ticked; OK on a season ticks or unticks it, then "Request N" sends the choice to Seerr.
 */
@Composable
fun SeasonPicker(state: LauncherState, pick: SeasonPick) {
    val open = remember(pick) { pick.seasons.filter { it.open } }
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    val firstSeason = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { firstSeason.requestFocus() } }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { Sfx.back(); state.seasonPicker = null },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.width(340.dp).clip(RoundedCornerShape(18.dp)).background(Veil.Sheet).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(pick.media.title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp))
            Text("Which seasons do you want?", fontSize = 10.sp, color = Veil.Text.copy(alpha = 0.65f), modifier = Modifier.padding(start = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                val allPicked = selected.size == open.size
                FilterChip(if (allPicked) "Clear" else "Select all") {
                    selected = if (allPicked) emptySet() else open.map { it.number }.toSet()
                }
                FilterChip(if (selected.isEmpty()) "Pick a season" else "Request ${selected.size}", selected = selected.isNotEmpty()) {
                    if (selected.isNotEmpty()) {
                        state.seasonPicker = null
                        state.request(pick.media, selected.sorted())
                    }
                }
            }
            androidx.compose.foundation.lazy.LazyColumn(
                Modifier.heightIn(max = 300.dp),
                // Room around the rows: the focused row grows and draws a border, and a list clips whatever
                // reaches past its edge, which cut the outline off on the first and last season.
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listItems(pick.seasons, key = { it.number }) { s ->
                    SeasonRow(
                        s, s.number in selected,
                        Modifier.then(if (s.number == open.firstOrNull()?.number) Modifier.focusRequester(firstSeason) else Modifier),
                    ) { if (s.open) selected = if (s.number in selected) selected - s.number else selected + s.number }
                }
            }
        }
    }
}

@Composable
private fun SeasonRow(s: Seerr.Season, ticked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    androidx.tv.material3.Surface(
        onClick = { if (s.open) Sfx.select(); onClick() },
        modifier = modifier.fillMaxWidth().focusSound(1.1f),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = if (s.open) 0.06f else 0.02f), contentColor = Veil.Text,
            focusedContainerColor = Color.White.copy(alpha = 0.16f), focusedContentColor = Veil.Text,
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(1.5.dp, Veil.Accent), shape = RoundedCornerShape(12.dp)),
        ),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(16.dp).clip(CircleShape)
                    .background(if (ticked) Veil.Accent else Color.Transparent)
                    .border(1.5.dp, if (ticked) Veil.Accent else Veil.Text.copy(alpha = if (s.open) 0.5f else 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (ticked) Icon(VeilIcons.Check, contentDescription = null, modifier = Modifier.size(10.dp), tint = Veil.Ink)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Season ${s.number}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Veil.Text.copy(alpha = if (s.open) 1f else 0.5f))
                val info = listOfNotNull(
                    s.episodes.takeIf { it > 0 }?.let { "$it episodes" },
                    s.airDate?.take(4),
                ).joinToString(" \u00B7 ")
                if (info.isNotEmpty()) Text(info, fontSize = 8.5.sp, color = Veil.Text.copy(alpha = 0.55f))
            }
            val tag = when {
                s.taken -> "Available"
                s.requested -> "Requested"
                s.status == Seerr.PARTIAL -> "Partly available"
                else -> null
            }
            if (tag != null) Text(tag, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = if (s.taken) Veil.Accent else Veil.Text.copy(alpha = 0.7f))
        }
    }
}

/**
 * Voice search: the microphone circle pulses with your voice, and what has been understood so far shows
 * under it. When you stop speaking the search runs by itself; Back (or Cancel) stops listening.
 */
@Composable
fun VoiceDialog(state: LauncherState) {
    val first = remember { FocusRequester() }
    LaunchedEffect(state.voiceProblem) {
        if (state.voiceProblem == null) Sfx.open()
        delay(80)
        runCatching { first.requestFocus() }
    }
    val level by androidx.compose.animation.core.animateFloatAsState(state.voiceLevel, androidx.compose.animation.core.tween(120), label = "mic-level")
    val listening = state.voiceListening
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { Sfx.back(); state.stopVoice() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.width(340.dp).clip(RoundedCornerShape(20.dp)).background(Veil.Sheet).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                // A ring that grows with your voice.
                Box(
                    Modifier.size(60.dp)
                        .graphicsLayer { val s = 1f + 0.4f * level; scaleX = s; scaleY = s; alpha = if (listening) 0.28f else 0f }
                        .clip(CircleShape).background(Veil.Accent),
                )
                Box(
                    Modifier.size(60.dp).clip(CircleShape).background(if (listening) Veil.Accent else Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(VeilIcons.Mic, contentDescription = null, modifier = Modifier.size(26.dp), tint = if (listening) Veil.Ink else Veil.Text)
                }
            }
            Text(if (listening) "Listening\u2026" else "Voice search", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                state.voiceProblem ?: state.voiceHeard.ifEmpty { "Say the name of a movie or show" },
                fontSize = 11.sp, lineHeight = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = Veil.Text.copy(alpha = if (state.voiceProblem != null || state.voiceHeard.isEmpty()) 0.65f else 1f),
                modifier = Modifier.heightIn(min = 32.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.voiceProblem != null) {
                    FilterChip("Try again", modifier = Modifier.focusRequester(first)) { state.startVoiceSearch() }
                    FilterChip("Cancel") { state.stopVoice() }
                } else {
                    FilterChip("Cancel", modifier = Modifier.focusRequester(first)) { state.stopVoice() }
                }
            }
        }
    }
}
