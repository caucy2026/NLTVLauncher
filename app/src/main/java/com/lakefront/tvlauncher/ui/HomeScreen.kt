package com.lakefront.tvlauncher.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.lakefront.tvlauncher.R
import com.lakefront.tvlauncher.logic.AppEntry
import com.lakefront.tvlauncher.logic.ComponentKey
import com.lakefront.tvlauncher.logic.HomeArrangement
import com.lakefront.tvlauncher.logic.PosterRow
import com.lakefront.tvlauncher.logic.TvSnapshot
import com.lakefront.tvlauncher.logic.TvUnavailable
import com.lakefront.tvlauncher.ui.components.EdgeNudge
import com.lakefront.tvlauncher.ui.components.DashedAddGlyph
import com.lakefront.tvlauncher.ui.components.DesignedGlyph
import com.lakefront.tvlauncher.ui.components.InstalledGlyph
import com.lakefront.tvlauncher.ui.components.LabeledTile
import com.lakefront.tvlauncher.ui.components.LakeBlue
import com.lakefront.tvlauncher.ui.components.LakeGold
import com.lakefront.tvlauncher.ui.components.LakeRed
import com.lakefront.tvlauncher.ui.components.PosterCard
import com.lakefront.tvlauncher.ui.components.PosterPlaceholder
import com.lakefront.tvlauncher.ui.LakeIcons

@Composable
fun HomeScreen(
    state: LauncherUiState,
    requesters: MutableMap<String, FocusRequester>,
    statusRequester: FocusRequester,
    onPoster: (com.lakefront.tvlauncher.logic.Poster) -> Unit,
    onOpenAllApps: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenStore: () -> Unit,
    onOpenAdd: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onPinMenu: (ComponentKey) -> Unit,
    onHomeFocus: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val posterRows = (state.tv as? TvSnapshot.Ready)?.rows.orEmpty()
    val showGuidance = HomeArrangement.guidanceSitsAboveApps(
        loading = state.tvLoading,
        posterRowCount = posterRows.size,
    )
    val scroll = rememberScrollState()
    Column(modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(top = HomeArrangement.TOP_CLEARANCE_DP.dp, bottom = 8.dp),
        ) {
            when {
                state.tvLoading -> LoadingBlock()
                posterRows.isNotEmpty() -> {
                    posterRows.forEachIndexed { index, row ->
                        PosterRowBlock(
                            row = row,
                            upToStatus = index == 0,
                            statusRequester = statusRequester,
                            requesters = requesters,
                            onPoster = onPoster,
                            onHomeFocus = onHomeFocus,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
        }
        if (showGuidance) {
            val reason = (state.tv as? TvSnapshot.Unavailable)?.reason ?: TvUnavailable.Empty
            Guidance(
                when (reason) {
                    TvUnavailable.Empty -> stringResource(R.string.empty_tv)
                    TvUnavailable.PermissionDenied -> stringResource(R.string.permission_tv)
                    TvUnavailable.ProviderMissing -> stringResource(R.string.provider_tv)
                },
            )
        }
        AppsBlock(
            state = state,
            upToStatus = state.tv !is TvSnapshot.Ready,
            statusRequester = statusRequester,
            requesters = requesters,
            onOpenAllApps = onOpenAllApps,
            onOpenFiles = onOpenFiles,
            onOpenStore = onOpenStore,
            onOpenAdd = onOpenAdd,
            onLaunch = onLaunch,
            onPinMenu = onPinMenu,
            onHomeFocus = onHomeFocus,
            modifier = Modifier.padding(bottom = 28.dp),
        )
    }
}

@Composable
private fun LoadingBlock() {
    SectionTitle(stringResource(R.string.watch_next))
    Row(
        modifier = Modifier.padding(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(4) { PosterPlaceholder() }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.loading_programs),
        color = Color.White.copy(alpha = 0.8f),
        fontSize = 14.sp,
        modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
    )
}

@Composable
private fun Guidance(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            shadow = Shadow(Color.Black.copy(alpha = 0.65f), Offset(0f, 1f), 8f),
        ),
        modifier = Modifier.padding(
            start = 48.dp,
            end = HomeArrangement.STATUS_END_GUARD_DP.dp,
            bottom = 8.dp,
        ),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 26.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 48.dp, end = 220.dp, bottom = 10.dp),
    )
}

@Composable
private fun PosterRowBlock(
    row: PosterRow,
    upToStatus: Boolean,
    statusRequester: FocusRequester,
    requesters: MutableMap<String, FocusRequester>,
    onPoster: (com.lakefront.tvlauncher.logic.Poster) -> Unit,
    onHomeFocus: (String) -> Unit,
) {
    SectionTitle(row.title)
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 48.dp, vertical = 6.dp)
            .then(if (upToStatus) Modifier.upTo(statusRequester) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        row.posters.forEach { poster ->
            val requester = rememberRequester(requesters, poster.id)
            PosterCard(
                poster = poster,
                modifier = Modifier.focusRequester(requester),
                onClick = { onPoster(poster) },
                onFocused = { onHomeFocus(poster.id) },
            )
        }
    }
}

@Composable
private fun AppsBlock(
    state: LauncherUiState,
    upToStatus: Boolean,
    statusRequester: FocusRequester,
    requesters: MutableMap<String, FocusRequester>,
    onOpenAllApps: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenStore: () -> Unit,
    onOpenAdd: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onPinMenu: (ComponentKey) -> Unit,
    onHomeFocus: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
    SectionTitle(stringResource(R.string.apps))
    val scroll = rememberScrollState()
    val appsByKey = state.apps.associateBy { it.key }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 48.dp, vertical = 6.dp)
            .then(if (upToStatus) Modifier.upTo(statusRequester) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val visiblePins = state.pins.mapNotNull { key -> appsByKey[key] }
        val rowCount = 4 + visiblePins.size
        val all = rememberRequester(requesters, "tile:all")
        EdgeNudge(index = 0, count = rowCount) {
        LabeledTile(
            label = stringResource(R.string.all_apps),
            width = 108.dp,
            height = 74.dp,
            container = Color.Transparent,
            onClick = onOpenAllApps,
            onFocused = { onHomeFocus("tile:all") },
            modifier = Modifier.focusRequester(all),
        ) {
            DesignedGlyph(LakeBlue, LakeIcons.AllApps)
        }
        }
        val files = state.files.tile
        val filesKey = rememberRequester(requesters, "tile:files")
        EdgeNudge(index = 1, count = rowCount) {
        LabeledTile(
            label = files?.label ?: stringResource(R.string.file_manager),
            width = 108.dp,
            height = 74.dp,
            container = Color.Transparent,
            onClick = onOpenFiles,
            onFocused = { onHomeFocus("tile:files") },
            modifier = Modifier.focusRequester(filesKey),
        ) {
            if (files != null) InstalledGlyph(files) else DesignedGlyph(LakeGold, LakeIcons.Folder, 30.dp)
        }
        }
        val store = state.store.tile
        val storeKey = rememberRequester(requesters, "tile:store")
        EdgeNudge(index = 2, count = rowCount) {
        LabeledTile(
            label = store?.label ?: stringResource(R.string.app_store),
            width = 108.dp,
            height = 74.dp,
            container = Color.Transparent,
            onClick = onOpenStore,
            onFocused = { onHomeFocus("tile:store") },
            modifier = Modifier.focusRequester(storeKey),
        ) {
            if (store != null) InstalledGlyph(store) else DesignedGlyph(LakeRed, LakeIcons.Bag, 30.dp)
        }
        }
        visiblePins.forEachIndexed { pinIndex, entry ->
            val key = entry.key
            val focusKey = "pin:${key.flatten()}"
            val requester = rememberRequester(requesters, focusKey)
            EdgeNudge(index = 3 + pinIndex, count = rowCount) {
            LabeledTile(
                label = entry.label,
                width = 108.dp,
                height = 74.dp,
                container = Color.Transparent,
                onClick = { onLaunch(entry) },
                onLongClick = { onPinMenu(key) },
                onFocused = { onHomeFocus(focusKey) },
                modifier = Modifier.focusRequester(requester),
            ) {
                InstalledGlyph(entry)
            }
            }
        }
        val add = rememberRequester(requesters, "tile:add")
        EdgeNudge(index = rowCount - 1, count = rowCount) {
        LabeledTile(
            label = stringResource(R.string.add),
            width = 108.dp,
            height = 74.dp,
            container = Color.Transparent,
            onClick = onOpenAdd,
            onFocused = { onHomeFocus("tile:add") },
            modifier = Modifier.focusRequester(add),
        ) {
            DashedAddGlyph()
        }
        }
    }
    }
}

@Composable
private fun rememberRequester(
    map: MutableMap<String, FocusRequester>,
    key: String,
): FocusRequester {
    val requester = remember(key) { FocusRequester() }
    DisposableEffect(key) {
        map[key] = requester
        onDispose { map.remove(key) }
    }
    return requester
}

private fun Modifier.upTo(status: FocusRequester): Modifier = onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
        status.requestFocus()
        true
    } else {
        false
    }
}
