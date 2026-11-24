@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.lakefront.tvlauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lakefront.tvlauncher.R
import com.lakefront.tvlauncher.logic.AppEntry
import com.lakefront.tvlauncher.logic.ComponentKey
import androidx.compose.ui.graphics.vector.ImageVector
import com.lakefront.tvlauncher.ui.DialogModel
import com.lakefront.tvlauncher.ui.LakeIcons

@Composable
fun Scrim(alpha: Float) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = alpha)))
}

@Composable
fun AddAppsPanel(
    apps: List<AppEntry>,
    pinned: Set<ComponentKey>,
    fixedPackages: Set<String> = emptySet(),
    onPin: (AppEntry) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = androidx.compose.runtime.remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) {
        runCatching { if (apps.isNotEmpty()) first.requestFocus() }
    }
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(LakePanel)
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(horizontal = 28.dp, vertical = 26.dp)
            .focusProperties { exit = { FocusRequester.Cancel } },
    ) {
        Text(
            text = stringResource(R.string.add_apps_title),
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.add_apps_subtitle),
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(18.dp))
        if (apps.isEmpty()) {
            Text(
                text = stringResource(R.string.no_apps),
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 72.dp),
            ) {
                itemsIndexed(apps, key = { _, entry -> entry.id }) { index, entry ->
                    LabeledTile(
                        label = entry.label,
                        width = 128.dp,
                        height = 84.dp,
                        container = Color.Transparent,
                        checked = entry.key in pinned || entry.packageName in fixedPackages,
                        onClick = { onPin(entry) },
                        onFocused = {
                            scope.launch { runCatching { gridState.animateScrollToItem(index) } }
                        },
                        modifier = if (entry.id == apps.first().id) {
                            Modifier.focusRequester(first)
                        } else {
                            Modifier
                        },
                    ) {
                        InstalledGlyph(entry, padding = 14.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun WallpaperPanel(
    customUri: String?,
    onChoose: () -> Unit,
    onUseDefault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = androidx.compose.runtime.remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .width(560.dp)
            .clip(shape)
            .background(LakePanel)
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(28.dp)
            .focusProperties { exit = { FocusRequester.Cancel } },
    ) {
        Text(
            text = stringResource(R.string.wallpaper),
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(14.dp)),
        ) {
            Image(
                painter = painterResource(R.drawable.bg_default_wallpaper),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (customUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(customUri).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        MenuRow(
            label = if (customUri == null) {
                stringResource(R.string.wallpaper_default)
            } else {
                stringResource(R.string.wallpaper_use_default)
            },
            selected = customUri == null,
            onClick = onUseDefault,
            modifier = Modifier.focusRequester(first),
        )
        Spacer(Modifier.height(8.dp))
        MenuRow(
            label = stringResource(R.string.wallpaper_choose),
            selected = false,
            onClick = onChoose,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.wallpaper_hint),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
        )
    }
}

@Composable
fun ActionMenu(
    title: String,
    icon: @Composable () -> Unit,
    rows: List<MenuAction>,
    modifier: Modifier = Modifier,
) {
    val first = androidx.compose.runtime.remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { first.requestFocus() }
    }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .width(300.dp)
            .clip(shape)
            .background(LakeMenu)
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .padding(14.dp)
            .focusProperties { exit = { FocusRequester.Cancel } },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(12.dp))
        rows.forEachIndexed { index, action ->
            MenuRow(
                label = action.label,
                detail = action.detail,
                leading = action.icon,
                danger = action.danger,
                onClick = action.onClick,
                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
            )
            if (index != rows.lastIndex) Spacer(Modifier.height(6.dp))
        }
    }
}

data class MenuAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val detail: String? = null,
    val danger: Boolean = false,
)

@Composable
private fun MenuRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    leading: ImageVector? = null,
    selected: Boolean = false,
    danger: Boolean = false,
) {
    LakeCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        container = if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent,
        focusedContainer = if (danger) LakeUninstall else Color.White.copy(alpha = 0.12f),
        focusedScale = 1.02f,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                Icon(
                    imageVector = leading,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (!detail.isNullOrBlank()) {
                    Text(detail, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun NoticeDialog(
    dialog: DialogModel,
    onChoice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = androidx.compose.runtime.remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .widthIn(min = 420.dp, max = 640.dp)
            .clip(shape)
            .background(LakePanel)
            .border(1.dp, Color.White.copy(alpha = 0.1f), shape)
            .padding(28.dp)
            .focusProperties { exit = { FocusRequester.Cancel } },
    ) {
        Text(dialog.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        if (dialog.message.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(dialog.message, color = Color.White.copy(alpha = 0.84f), fontSize = 16.sp)
        }
        Spacer(Modifier.height(18.dp))
        dialog.choices.forEachIndexed { index, choice ->
            MenuRow(
                label = choice.label,
                onClick = { onChoice(choice.id) },
                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
            )
            if (index != dialog.choices.lastIndex) Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun PanelFrame(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        content()
    }
}
