package com.lakefront.tvlauncher.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lakefront.tvlauncher.logic.EdgeFeedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val FOCUS_MS = 160

private class JobHolder {
    var job: Job? = null
}

@Composable
fun focusAnimationSpec(): AnimationSpec<Float> =
    if (ValueAnimator.areAnimatorsEnabled()) tween(durationMillis = FOCUS_MS) else snap()

@Composable
fun animateFocusScale(focused: Boolean, focusedScale: Float = 1.06f): Float {
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        animationSpec = focusAnimationSpec(),
        label = "focusScale",
    )
    return scale
}

/**
 * At a true row end, Left or Right nudges the child and is consumed.
 * Off-screen siblings still count as candidates, so those presses are not nudged.
 */
@Composable
fun EdgeNudge(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val distance = with(LocalDensity.current) { 8.dp.toPx() }
    val nudgeJob = remember { JobHolder() }
    Box(
        modifier = modifier
            .graphicsLayer { translationX = offset.value }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val forward = when (event.key) {
                    Key.DirectionRight -> true
                    Key.DirectionLeft -> false
                    else -> return@onPreviewKeyEvent false
                }
                if (!EdgeFeedback.shouldNudge(index, count, forward)) return@onPreviewKeyEvent false
                nudgeJob.job?.cancel()
                nudgeJob.job = scope.launch {
                    offset.stop()
                    if (!ValueAnimator.areAnimatorsEnabled()) {
                        offset.snapTo(0f)
                        return@launch
                    }
                    val target = if (forward) distance else -distance
                    offset.snapTo(0f)
                    offset.animateTo(target, tween(90))
                    offset.animateTo(0f, tween(140))
                }
                true
            },
    ) {
        content()
    }
}
