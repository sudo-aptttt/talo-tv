package com.talotv.os.core.media

data class AppHeroSuggestion(
    val title: String,
    val backdropResId: Int? = null,
    val backdropUrl: String? = null,
    val intentUri: String? = null,
    val hasTitleInImage: Boolean = true
)

data class MediaSpotlight(
    val id: String,
    val appKey: String,
    val title: String,
    val badge: String = "",
    val tagline: String = "",
    val category: String = "",
    val year: String = "2026",
    val rating: String = "12+",
    val gradientColors: List<Long> = listOf(0xFF1E3A8A, 0xFF0F172A, 0xFF020617),
    val backdropUrl: String? = null,
    val backdropResId: Int? = null,
    val logoText: String = title,
    val heroSuggestion: AppHeroSuggestion? = null
)

data class UpNextItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val progress: Float? = null, // 0.0 to 1.0 (draws progress bar)
    val gradientColors: List<Long>,
    val targetPackage: String = ""
)

data class DockAppItem(
    val id: String,
    val name: String,
    val category: String,
    val packageName: String,
    val iconType: DockIconType,
    val bgGradient: List<Long>,
    val spotlight: MediaSpotlight,
    val iconBitmap: androidx.compose.ui.graphics.ImageBitmap? = null,
    val isBanner: Boolean = false,
    val heroSuggestion: AppHeroSuggestion? = null
)

enum class DockIconType {
    TV,
    ARCADE,
    FITNESS,
    CALLS,
    MUSIC,
    DISNEY,
    NETFLIX,
    PRIME,
    YOUTUBE,
    OQEE,
    SETTINGS,
    SEARCH,
    PHOTOS,
    PODCASTS,
    STORE
}
