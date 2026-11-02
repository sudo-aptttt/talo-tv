package com.talotv.os.core.media

import android.content.Context
import android.database.Cursor
import android.media.tv.TvContract
import android.net.Uri
import com.talotv.os.R

/**
 * Manages cinematic 16:9 hero backdrop suggestions for focused dock applications.
 *
 * Requirements:
 * 1. Single full-screen 16:9 hero backdrop behind the dock.
 * 2. Large clean typography title in top-left (e.g., "PALMER").
 * 3. Strictly NO metadata: no genre, no season, no duration, no format (4K/HDR), no year, no platform name.
 * 4. Empty state: when an app has no suggestions (Settings, Play Store, etc.), returns null so nothing is displayed.
 */
object ContentSuggestionManager {

    /**
     * Resolves the cinematic 16:9 hero suggestion for a focused dock app.
     * Returns null if the app has no suggestions (e.g. Settings, Play Store, or non-media apps).
     */
    fun getSuggestion(context: Context, app: DockAppItem): AppHeroSuggestion? {
        val pkg = app.packageName
        val appId = app.id

        // 1. Strict blacklist: Settings & App Store NEVER display media suggestions ("Quand c'est pas le cas met rien")
        if (appId == "app-settings" || appId == "dock-settings" ||
            pkg == "com.talotv.settings" || pkg == "com.android.tv.settings" ||
            appId == "app-store" || pkg == "com.android.vending"
        ) {
            return null
        }

        // 2. Query dynamic Android TV Preview Programs via TvContract if available
        try {
            val dynamicSuggestion = queryTvContractForPackage(context, pkg)
            if (dynamicSuggestion != null) {
                return dynamicSuggestion
            }
        } catch (_: Throwable) {
            // TV content provider fallback
        }

        // 3. If the DockAppItem already has an explicit hero suggestion, return it
        if (app.heroSuggestion != null) {
            return app.heroSuggestion
        }

        // 4. Built-in curated 16:9 showcase suggestions for known TV applications
        return when (pkg) {
            // YouTube: Default base spotlight look ("Laisse le truc de base")
            "com.google.android.youtube.tv" -> null
            // YouTube Music: features Live Acoustic Sessions
            "com.google.android.youtube.tvmusic" -> AppHeroSuggestion(
                title = "LIVE ACOUSTIC SESSIONS",
                backdropResId = R.drawable.backdrop_concert
            )
            // Netflix: features Stranger Things
            "com.netflix.ninja" -> AppHeroSuggestion(
                title = "STRANGER THINGS",
                backdropResId = R.drawable.backdrop_stranger_things
            )
            // Prime Video: features Dune
            "com.amazon.amazonvideo.livingroom" -> AppHeroSuggestion(
                title = "DUNE",
                backdropResId = R.drawable.backdrop_dune
            )
            // Disney+: features The Mandalorian
            "com.disney.disneyplus" -> AppHeroSuggestion(
                title = "THE MANDALORIAN",
                backdropResId = R.drawable.backdrop_mandalorian
            )
            // CANAL+: features Dune
            "com.canal.android.canal" -> AppHeroSuggestion(
                title = "DUNE",
                backdropResId = R.drawable.backdrop_dune
            )
            // Molotov / OQEE
            "tv.molotov.app", "net.freebox.oqee" -> AppHeroSuggestion(
                title = "LE GRAND FILM DU SOIR",
                backdropResId = R.drawable.backdrop_nature
            )
            // Play Games: features Asphalt Legends
            "com.google.android.play.games" -> AppHeroSuggestion(
                title = "ASPHALT LEGENDS",
                backdropResId = R.drawable.backdrop_racing
            )
            // SmartTube: features Exploration Spatiale
            "org.smarttube.beta" -> AppHeroSuggestion(
                title = "EXPLORATION SPATIALE",
                backdropResId = R.drawable.backdrop_space
            )
            // Jellyfin: features Civil War / Cinema
            "org.jellyfin.androidtv" -> AppHeroSuggestion(
                title = "CIVIL WAR",
                backdropResId = R.drawable.backdrop_cinema
            )
            // Apple TV app: features Palmer (as in Apple TV showcase)
            "com.apple.atve.androidtv.appletv", "com.apple.atve.sony.appletv" -> AppHeroSuggestion(
                title = "PALMER",
                backdropResId = R.drawable.backdrop_palmer
            )
            // Non-media apps without suggestions return null ("Quand c'est pas le cas met rien")
            else -> null
        }
    }

    private fun queryTvContractForPackage(context: Context, packageName: String): AppHeroSuggestion? {
        val uri = Uri.parse("content://android.media.tv/preview_program")
        val colPreviewImageUri = "preview_image_uri"
        val projection = arrayOf(
            TvContract.PreviewPrograms._ID,
            TvContract.PreviewPrograms.COLUMN_TITLE,
            TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
            colPreviewImageUri,
            TvContract.PreviewPrograms.COLUMN_INTENT_URI,
            TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME
        )
        val selection = "${TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME} = ?"
        val selectionArgs = arrayOf(packageName)
        val sortOrder = "${TvContract.PreviewPrograms._ID} DESC"

        val cursor: Cursor? = context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val titleIndex = it.getColumnIndex(TvContract.PreviewPrograms.COLUMN_TITLE)
                val previewImageIndex = it.getColumnIndex(colPreviewImageUri)
                val posterArtIndex = it.getColumnIndex(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI)
                val intentUriIndex = it.getColumnIndex(TvContract.PreviewPrograms.COLUMN_INTENT_URI)

                val title = if (titleIndex != -1) it.getString(titleIndex) else null
                val previewImage = if (previewImageIndex != -1) it.getString(previewImageIndex) else null
                val posterArt = if (posterArtIndex != -1) it.getString(posterArtIndex) else null
                val intentUri = if (intentUriIndex != -1) it.getString(intentUriIndex) else null

                val imageUrl = previewImage ?: posterArt
                if (!title.isNullOrBlank() && !imageUrl.isNullOrBlank()) {
                    return AppHeroSuggestion(
                        title = title,
                        backdropUrl = imageUrl,
                        intentUri = intentUri
                    )
                }
            }
        }
        return null
    }
}
