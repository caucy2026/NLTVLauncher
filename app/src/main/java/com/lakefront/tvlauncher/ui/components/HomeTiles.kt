package com.lakefront.tvlauncher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.lakefront.tvlauncher.R
import com.lakefront.tvlauncher.logic.AppEntry
import com.lakefront.tvlauncher.ui.LakeIcons

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LabeledTile(
    label: String,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    container: Color = TilePlate,
    checked: Boolean = false,
    onFocused: () -> Unit = {},
    fillWidth: Boolean = false,
    labelLines: Int = 1,
    content: @Composable () -> Unit,
) {
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var labelFocused by remember { mutableStateOf(false) }
    Column(
        modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(width))
            .padding(vertical = 10.dp)
            .bringIntoViewRequester(bringIntoView),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier) {
            val shape = RoundedCornerShape(16.dp)
            val tileModifier = if (fillWidth) {
                modifier.fillMaxWidth().height(height)
            } else {
                modifier.size(width, height)
            }
            LakeCard(
                onClick = onClick,
                onLongClick = onLongClick,
                onFocused = onFocused,
                onFocusChange = {
                    labelFocused = it
                    if (it) scope.launch { runCatching { bringIntoView.bringIntoView() } }
                },
                modifier = tileModifier,
                shape = shape,
                container = container,
                focusedContainer = container,
            ) {
                Box(
                    modifier = Modifier
                        .then(if (fillWidth) Modifier.fillMaxWidth().height(height) else Modifier.size(width, height))
                        .clip(shape),
                    contentAlignment = Alignment.Center,
                ) {
                    content()
                }
            }
            if (checked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                        .size(22.dp)
                        .background(LakeCheck, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = LakeIcons.Check,
                        contentDescription = stringResource(R.string.pinned_badge),
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = if (labelFocused) 1 else labelLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(width))
                .then(if (labelFocused) Modifier.basicMarquee() else Modifier),
        )
    }
}

@Composable
fun DesignedGlyph(color: Color, icon: ImageVector, glyph: Dp = 32.dp) {
    Box(Modifier.fillMaxSize().background(color), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(glyph))
    }
}

@Composable
fun InstalledGlyph(entry: AppEntry, padding: Dp = 12.dp) {
    Box(Modifier.fillMaxSize().background(TilePlate), contentAlignment = Alignment.Center) {
        AppIconImage(
            packageName = entry.packageName,
            activityName = entry.activityName,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@Composable
fun DashedAddGlyph() {
    Canvas(Modifier.fillMaxSize()) {
        val stroke = Stroke(
            width = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.9f),
            style = stroke,
            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = LakeIcons.Plus,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
    }
}
