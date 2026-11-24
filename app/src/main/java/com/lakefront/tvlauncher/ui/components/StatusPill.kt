package com.lakefront.tvlauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.lakefront.tvlauncher.R
import com.lakefront.tvlauncher.logic.RadioState
import com.lakefront.tvlauncher.logic.SystemStatus
import com.lakefront.tvlauncher.ui.LakeIcons

@Composable
fun StatusPill(
    status: SystemStatus,
    onWifi: () -> Unit,
    onBluetooth: () -> Unit,
    onWallpaper: () -> Unit,
    onSettings: () -> Unit,
    onUsb: () -> Unit = {},
    firstRequester: FocusRequester,
    wallpaperRequester: FocusRequester? = null,
    settingsRequester: FocusRequester? = null,
    usbRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val wifiVisible = status.wifi != RadioState.Absent
    val bluetoothVisible = status.bluetooth != RadioState.Absent
    val usbVisible = status.usbMounted
    val timeDescription = stringResource(R.string.cd_time)
    val controls = buildList {
        if (wifiVisible) {
            add(PillSpec("status:wifi", if (status.wifi == RadioState.Off) LakeIcons.WifiOff else LakeIcons.Wifi, status.wifi == RadioState.Off, stringResource(R.string.cd_wifi), onWifi, firstRequester))
        }
        if (bluetoothVisible) {
            add(
                PillSpec(
                    "status:bluetooth",
                    LakeIcons.Bluetooth,
                    status.bluetooth == RadioState.Off || status.bluetooth == RadioState.NeedsPermission,
                    stringResource(R.string.cd_bluetooth),
                    onBluetooth,
                    if (!wifiVisible) firstRequester else null,
                ),
            )
        }
        if (usbVisible) {
            add(PillSpec("status:usb", LakeIcons.Usb, false, stringResource(R.string.cd_usb), onUsb, usbRequester))
        }
        add(
            PillSpec(
                "status:wallpaper",
                LakeIcons.Image,
                false,
                stringResource(R.string.cd_wallpaper),
                onWallpaper,
                wallpaperRequester ?: if (!wifiVisible && !bluetoothVisible && !usbVisible) firstRequester else null,
            ),
        )
        add(PillSpec("status:settings", LakeIcons.Settings, false, stringResource(R.string.cd_settings), onSettings, settingsRequester))
    }
    var usbFocused by remember { mutableStateOf(false) }
    LaunchedEffect(usbVisible) {
        if (!usbVisible && usbFocused) {
            usbFocused = false
            val fallback = wallpaperRequester ?: settingsRequester ?: firstRequester
            runCatching { fallback.requestFocus() }
        }
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x8C12161C))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = status.time,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .padding(end = 8.dp)
                .semantics { contentDescription = "$timeDescription ${status.time}" },
        )
        controls.forEachIndexed { index, spec ->
            EdgeNudge(index = index, count = controls.size) {
                PillButton(
                    icon = spec.icon,
                    description = spec.description,
                    dim = spec.dim,
                    onClick = spec.onClick,
                    requester = spec.requester,
                    onFocusChange = { focused ->
                        if (spec.key == "status:usb") usbFocused = focused
                    },
                )
            }
        }
    }
}

private data class PillSpec(
    val key: String,
    val icon: ImageVector,
    val dim: Boolean,
    val description: String,
    val onClick: () -> Unit,
    val requester: FocusRequester?,
)

@Composable
private fun PillButton(
    icon: ImageVector,
    description: String,
    dim: Boolean,
    onClick: () -> Unit,
    requester: FocusRequester? = null,
    onFocusChange: (Boolean) -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale = animateFocusScale(focused, 1.12f)
    LaunchedEffect(focused) { onFocusChange(focused) }
    Box(
        modifier = Modifier
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(34.dp)
            .clip(CircleShape)
            .background(if (focused) Color.White.copy(alpha = 0.18f) else Color.Transparent)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = if (dim) 0.45f else 1f),
            modifier = Modifier.size(18.dp),
        )
    }
}
