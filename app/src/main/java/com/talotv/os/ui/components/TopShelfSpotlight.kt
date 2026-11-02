package com.talotv.os.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.talotv.os.core.media.AppHeroSuggestion
import com.talotv.os.core.media.MediaSpotlight

/**
 * Full-screen cinematic hero backdrop showcase.
 *
 * Requirements:
 * 1. Single full-screen 16:9 hero backdrop covering 100% of the screen height (down to the very bottom).
 * 2. Official movie/series logo/title directly on the image with authentic typography, or clean top-left text fallback.
 * 3. Strictly NO metadata: no genre, no season, no duration, no format (4K/HDR), no year, no platform name.
 * 4. Empty state: when an app has no suggestions (Settings, Play Store, etc.), nothing is displayed (clean ambient background).
 * 5. Soft blur / frosted scrim at bottom behind dock for maximum elegance.
 */
@Composable
fun TopShelfSpotlight(
    spotlight: MediaSpotlight,
    suggestion: AppHeroSuggestion?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {

        // 1. Full-screen Cinematic Backdrop or Base App Atmosphere (Extends to bottom edge)
        Crossfade(
            targetState = Pair(spotlight, suggestion),
            animationSpec = tween(durationMillis = 350),
            label = "HeroBackdropCrossfade"
        ) { (spot, hero) ->
            Box(modifier = Modifier.fillMaxSize()) {
                if (hero != null && (hero.backdropResId != null || !hero.backdropUrl.isNullOrBlank())) {
                    // Full-screen 16:9 Cinematic Hero Image down to physical bottom edge
                    if (hero.backdropResId != null) {
                        Image(
                            painter = painterResource(id = hero.backdropResId),
                            contentDescription = hero.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (!hero.backdropUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(hero.backdropUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = hero.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (spot.appKey in listOf("app-store", "dock-settings", "app-settings") ||
                    spot.id in listOf("spot-store", "spot-settings")
                ) {
                    // Empty state for Store & Settings ("Quand c'est pas le cas met rien"): Clean, ambient dark
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF14161B),
                                        Color(0xFF0C0D11),
                                        Color(0xFF050608)
                                    )
                                )
                            )
                    )
                } else {
                    // Default Base App Spotlight ("Laisse le truc de base" - e.g. YouTube atmospheric red radial glow)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(spot.gradientColors.first()),
                                        Color(spot.gradientColors.getOrElse(1) { spot.gradientColors.first() }),
                                        Color.Black
                                    ),
                                    center = androidx.compose.ui.geometry.Offset(1200f, 400f),
                                    radius = 1800f
                                )
                            )
                    )
                }
            }
        }

        // 2. Scrim Overlays
        // Bottom Scrim: soft, smooth frosted gradient to lift the floating dock while keeping backdrop visible
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.40f to Color.Black.copy(alpha = 0.20f),
                        0.75f to Color.Black.copy(alpha = 0.50f),
                        1.0f to Color.Black.copy(alpha = 0.75f)
                    )
                )
        )

        // Top-Right Scrim: subtle shadow for profile / clock widget contrast
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(0.35f)
                .height(100.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.40f),
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(500f, 0f),
                        radius = 400f
                    )
                )
        )

        // 3. Top-Left Title Typography
        // - If suggestion has baked title (like Asphalt Legends or Palmer): no text overlay
        // - If suggestion exists but title is not baked: render suggestion.title
        // - If suggestion is null and NOT settings/store (e.g. YouTube): render spot.title ("YouTube")
        val isUtilityApp = spotlight.appKey in listOf("app-store", "dock-settings", "app-settings") ||
            spotlight.id in listOf("spot-store", "spot-settings")
        val shouldShowTitle = when {
            suggestion != null -> !suggestion.hasTitleInImage && !suggestion.title.isNullOrBlank()
            isUtilityApp -> false
            else -> !spotlight.title.isNullOrBlank()
        }
        val displayTitle = when {
            suggestion != null -> suggestion.title
            else -> spotlight.title
        }

        if (shouldShowTitle && displayTitle != null) {
            Crossfade(
                targetState = displayTitle,
                animationSpec = tween(durationMillis = 300),
                label = "SpotlightTitleCrossfade"
            ) { title ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 56.dp, top = 48.dp)
                        .fillMaxWidth(0.65f)
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1.5).sp,
                        lineHeight = 56.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Backward-compatible overload for MediaSpotlight
 */
@Composable
fun TopShelfSpotlight(
    spotlight: MediaSpotlight,
    modifier: Modifier = Modifier
) {
    TopShelfSpotlight(
        spotlight = spotlight,
        suggestion = spotlight.heroSuggestion,
        modifier = modifier
    )
}

/**
 * Upward Chevron Indicator placed directly above the floating glass dock.
 */
@Composable
fun UpwardChevronIndicator(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(width = 16.dp, height = 8.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, h)
            lineTo(w * 0.5f, 0f)
            lineTo(w, h)
        }
        drawPath(
            path = path,
            color = Color.White.copy(alpha = 0.65f),
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
