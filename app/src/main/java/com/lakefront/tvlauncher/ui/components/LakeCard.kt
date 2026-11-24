package com.lakefront.tvlauncher.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow

@Composable
fun LakeCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape,
    focusedScale: Float = 1.06f,
    container: Color = Color.Transparent,
    focusedContainer: Color = container,
    enabled: Boolean = true,
    onFocused: () -> Unit = {},
    onFocusChange: (Boolean) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) {
        onFocusChange(focused)
        if (focused) onFocused()
    }
    val scale = animateFocusScale(focused, focusedScale)
    Card(
        onClick = { if (enabled) onClick() },
        onLongClick = if (enabled) onLongClick else null,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        interactionSource = interaction,
        shape = CardDefaults.shape(shape = shape, focusedShape = shape, pressedShape = shape),
        colors = CardDefaults.colors(
            containerColor = container,
            contentColor = Color.White,
            focusedContainerColor = focusedContainer,
            focusedContentColor = Color.White,
            pressedContainerColor = focusedContainer,
            pressedContentColor = Color.White,
        ),
        scale = CardDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 1f),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(width = 3.dp, color = Color.White),
                shape = shape,
            ),
        ),
        glow = CardDefaults.glow(glow = Glow.None, focusedGlow = Glow.None, pressedGlow = Glow.None),
        content = content,
    )
}

val LakePanel = Color(0xF0141822)
val LakeMenu = Color(0xF21B202A)
val LakeUninstall = Color(0xFF6E2A30)
val LakeCheck = Color(0xFF2ECC71)
val LakeBlue = Color(0xFF3A74F7)
val LakeGold = Color(0xFFE2A423)
val LakeRed = Color(0xFFE3483D)
val ProgressRed = Color(0xFFFF3B30)
val TilePlate = Color(0x66101820)
val PlaceholderArt = Color(0xFF1A1E26)
