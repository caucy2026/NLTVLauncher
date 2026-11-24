package com.lakefront.tvlauncher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.lakefront.tvlauncher.R
import com.lakefront.tvlauncher.logic.AppEntry
import com.lakefront.tvlauncher.ui.components.InstalledGlyph
import com.lakefront.tvlauncher.ui.components.LabeledTile
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun AllAppsScreen(
    apps: List<AppEntry>,
    focusSignal: FocusSignal?,
    requesters: MutableMap<String, FocusRequester>,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
    onFocused: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(focusSignal?.nonce) {
        val key = focusSignal?.key ?: return@LaunchedEffect
        val index = apps.indexOfFirst { it.id == key }
        if (index >= 0) gridState.scrollToItem(index)
        val requester = withTimeoutOrNull(700) {
            snapshotFlow { requesters[key] }.filterNotNull().first()
        }
        requester?.requestFocus()
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val columns = when {
            maxWidth >= 1400.dp -> 7
            maxWidth >= 800.dp -> 6
            maxWidth >= 640.dp -> 5
            else -> 4
        }
        if (apps.isEmpty()) {
            Text(
                text = stringResource(R.string.no_apps),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 48.dp, top = 24.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(apps, key = { _, app -> app.id }) { _, app ->
                    val requester = rememberRequester(requesters, app.id)
                    LabeledTile(
                        label = app.label,
                        width = 148.dp,
                        height = 86.dp,
                        fillWidth = true,
                        container = Color.Transparent,
                        onClick = { onLaunch(app) },
                        onLongClick = { onMenu(app) },
                        onFocused = { onFocused(app.id) },
                        modifier = Modifier.focusRequester(requester),
                        labelLines = 2,
                    ) {
                        InstalledGlyph(app, padding = 16.dp)
                    }
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
