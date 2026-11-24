package com.lakefront.tvlauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lakefront.tvlauncher.logic.Poster

@Composable
fun PosterCard(
    poster: Poster,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFocused: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    LakeCard(
        onClick = onClick,
        onFocused = onFocused,
        modifier = modifier.width(204.dp),
        shape = shape,
        container = PlaceholderArt,
        focusedContainer = PlaceholderArt,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp)
                .clip(shape),
        ) {
            val art = poster.artUri
            if (art != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(art)
                        .crossfade(160)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                        ),
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                val label = if (poster.detail.isNullOrBlank()) {
                    poster.title
                } else {
                    "${poster.title} · ${poster.detail}"
                }
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val progress = poster.progress
                if (progress != null) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .height(3.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(ProgressRed, RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PosterPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(204.dp)
            .height(116.dp)
            .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp)),
    )
}
