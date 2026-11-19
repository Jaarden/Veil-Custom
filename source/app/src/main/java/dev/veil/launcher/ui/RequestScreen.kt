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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
                FilterChip(t.title, state.requestType == t) { state.chooseRequestType(t) }
            }
            Spacer(Modifier.width(14.dp))
            FilterChip("Popular", !state.requestLatest) { state.chooseRequestLatest(false) }
            FilterChip("Latest", state.requestLatest) { state.chooseRequestLatest(true) }
            Spacer(Modifier.width(14.dp))
            FilterChip("In my library", state.requestInLibrary) { state.toggleRequestInLibrary() }
        }
        if (state.requestProviders.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            ) {
                item(key = "any") { FilterChip("Any service", state.requestProvider == null) { state.chooseRequestProvider(null) } }
                items(state.requestProviders.size, key = { state.requestProviders[it].id }) { i ->
                    val p = state.requestProviders[i]
                    FilterChip(p.name, state.requestProvider?.id == p.id) { state.chooseRequestProvider(p) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 40.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.requestItems, key = { it.key }) { m ->
                // Scores are fetched for the posters actually on screen, not the whole list.
                LaunchedEffect(m.key) { state.loadRatings(m) }
                PosterTile(m, state.requestRatings[m.key], onClick = { state.requestSheet = m })
            }
        }
    }
}

/** A filter choice: outlined in the accent colour when it's the one in use. */
@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.tv.material3.Surface(
        onClick = { Sfx.select(); onClick() },
        modifier = Modifier.focusSound(1.12f),
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
private fun PosterTile(m: Seerr.Media, ratings: Seerr.Ratings?, onClick: () -> Unit) {
    Card(
        onClick = { Sfx.select(); onClick() },
        modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).focusSound(),
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

/** OK on a poster: confirm the request, or say why there's nothing to request. */
@Composable
fun RequestMenu(state: LauncherState, m: Seerr.Media) {
    val play = SheetAction("Play on Plex", VeilIcons.Play) { state.playOnPlex(m) }
    val actions = when {
        m.available -> listOf(play)
        m.status == Seerr.PARTIAL -> listOf(play, SheetAction("Request the missing seasons", VeilIcons.Search) { state.request(m) })
        m.requested -> listOf(SheetAction("Already requested", VeilIcons.Check) {})
        else -> listOf(
            SheetAction(if (m.isMovie) "Request this movie" else "Request all seasons", VeilIcons.Search) { state.request(m) },
            SheetAction("Cancel", VeilIcons.Unpin) {},
        )
    }
    ActionSheet(
        title = listOfNotNull(m.title, m.year).joinToString(" · "),
        actions = actions,
        onDismiss = { state.requestSheet = null },
    )
}
