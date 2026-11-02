package com.talotv.os.core.media

import com.talotv.os.R

object ContentRepository {

    val fallbackSpotlight = MediaSpotlight(
        id = "spot-talo-home",
        appKey = "talo-home",
        title = "Talo TV",
        badge = "Système d'Exploitation TV • 4K HDR",
        tagline = "Bienvenue sur Talo OS. Accédez à vos applications, divertissements et paramètres en toute fluidité.",
        category = "Accueil Salon",
        year = "2026",
        rating = "Tous publics",
        gradientColors = listOf(0xFF1E3A8A, 0xFF0F172A, 0xFF020617),
        logoText = "Talo OS",
        heroSuggestion = null
    )

    // Curated catalog of known TV streaming & media applications with signature hero spotlights.
    // Note: An app ONLY appears in the dock if it is ACTUALLY INSTALLED on the user's TV system!
    val knownDockApps = listOf(
        DockAppItem(
            id = "dock-tv",
            name = "Télévision Direct",
            category = "Chaînes TV & TNT",
            packageName = "com.android.tv",
            iconType = DockIconType.TV,
            bgGradient = listOf(0xFF1E3A8A, 0xFF0F172A),
            spotlight = MediaSpotlight(
                id = "spot-tv",
                appKey = "dock-tv",
                title = "Télévision en Direct",
                badge = "TNT HD • Guide EPG • Zapping Instantané",
                tagline = "Accédez instantanément à toutes vos chaînes télévisées en direct avec le guide électronique des programmes.",
                category = "Télévision Numérique",
                year = "2026",
                rating = "Tous publics",
                gradientColors = listOf(0xFF1565C0, 0xFF0D47A1, 0xFF051E44),
                logoText = "Direct TV"
            ),
            heroSuggestion = null
        ),
        DockAppItem(
            id = "dock-youtube",
            name = "YouTube",
            category = "Vidéos & Streaming",
            packageName = "com.google.android.youtube.tv",
            iconType = DockIconType.YOUTUBE,
            bgGradient = listOf(0xFFFF0000, 0xFFB30000),
            spotlight = MediaSpotlight(
                id = "spot-youtube",
                appKey = "dock-youtube",
                title = "YouTube",
                badge = "4K HDR • 60 FPS • Dolby Digital",
                tagline = "Regardez vos chaînes favorites, les créateurs mondiaux, documentaires et diffusions en direct sur grand écran.",
                category = "Vidéos & Séries Web",
                year = "2026",
                rating = "Tous publics",
                gradientColors = listOf(0xFF8B0000, 0xFF4A0000, 0xFF1A0000),
                logoText = "YouTube"
            ),
            heroSuggestion = null // Base default spotlight (no movie suggestion)
        ),
        DockAppItem(
            id = "dock-music",
            name = "YouTube Music",
            category = "Musique & Concerts",
            packageName = "com.google.android.youtube.tvmusic",
            iconType = DockIconType.MUSIC,
            bgGradient = listOf(0xFFFF2D55, 0xFFCC113B),
            spotlight = MediaSpotlight(
                id = "spot-music",
                appKey = "dock-music",
                title = "YouTube Music",
                badge = "Audio Hi-Fi • Paroles Synchronisées • Clips 4K",
                tagline = "Plongez dans des millions de titres, concerts live, sessions acoustiques et playlists personnalisées pour votre salon.",
                category = "Diffusion Musicale Salon",
                year = "2026",
                rating = "Tous publics",
                gradientColors = listOf(0xFFB71C1C, 0xFF5D0B0B, 0xFF1F0303),
                logoText = "Musique"
            ),
            heroSuggestion = AppHeroSuggestion(
                title = "LIVE ACOUSTIC SESSIONS",
                backdropResId = R.drawable.backdrop_concert
            )
        ),
        DockAppItem(
            id = "dock-netflix",
            name = "Netflix",
            category = "Films & Séries",
            packageName = "com.netflix.ninja",
            iconType = DockIconType.NETFLIX,
            bgGradient = listOf(0xFFE50914, 0xFF831010),
            spotlight = MediaSpotlight(
                id = "spot-netflix",
                appKey = "dock-netflix",
                title = "Netflix",
                badge = "4K Dolby Vision • Dolby Atmos",
                tagline = "Films, séries et documentaires primés en streaming illimité.",
                category = "Cinéma & Séries",
                year = "2026",
                rating = "16+",
                gradientColors = listOf(0xFFE50914, 0xFF540707, 0xFF180101),
                logoText = "Netflix"
            ),
            heroSuggestion = AppHeroSuggestion(
                title = "STRANGER THINGS",
                backdropResId = R.drawable.backdrop_stranger_things
            )
        ),
        DockAppItem(
            id = "dock-prime",
            name = "Prime Video",
            category = "Cinéma & Divertissement",
            packageName = "com.amazon.amazonvideo.livingroom",
            iconType = DockIconType.PRIME,
            bgGradient = listOf(0xFF2C2E33, 0xFF181A1E),
            spotlight = MediaSpotlight(
                id = "spot-prime",
                appKey = "dock-prime",
                title = "Prime Video",
                badge = "Prime Originals • 4K HDR",
                tagline = "Regardez les séries exclusives Prime Originals ainsi que les derniers films à succès.",
                category = "Streaming Vidéo",
                year = "2026",
                rating = "16+",
                gradientColors = listOf(0xFF24262C, 0xFF141519, 0xFF000000),
                logoText = "Prime Video"
            ),
            heroSuggestion = AppHeroSuggestion(
                title = "DUNE",
                backdropResId = R.drawable.backdrop_dune
            )
        ),
        DockAppItem(
            id = "dock-disney",
            name = "Disney+",
            category = "Animation & Cinéma",
            packageName = "com.disney.disneyplus",
            iconType = DockIconType.DISNEY,
            bgGradient = listOf(0xFF262830, 0xFF161820),
            spotlight = MediaSpotlight(
                id = "spot-disney",
                appKey = "dock-disney",
                title = "Disney+",
                badge = "Disney • Pixar • Marvel • Star Wars",
                tagline = "Les plus grandes histoires du monde réunies au même endroit pour toute la famille.",
                category = "Famille & Blockbusters",
                year = "2026",
                rating = "Tous publics",
                gradientColors = listOf(0xFF22242B, 0xFF12141A, 0xFF000000),
                logoText = "Disney+"
            ),
            heroSuggestion = AppHeroSuggestion(
                title = "THE MANDALORIAN",
                backdropResId = R.drawable.backdrop_mandalorian
            )
        ),
        DockAppItem(
            id = "dock-canal",
            name = "CANAL+",
            category = "Cinéma, Séries & Sport",
            packageName = "com.canal.android.canal",
            iconType = DockIconType.TV,
            bgGradient = listOf(0xFF1C1D24, 0xFF0B0C10),
            spotlight = MediaSpotlight(
                id = "spot-canal",
                appKey = "dock-canal",
                title = "CANAL+",
                badge = "Cinéma Ultra Récent • UEFA • Formule 1",
                tagline = "Le meilleur du cinéma mondial, des créations originales et du sport en direct.",
                category = "Premium TV",
                year = "2026",
                rating = "16+",
                gradientColors = listOf(0xFF2C2D35, 0xFF15161A, 0xFF000000),
                logoText = "CANAL+"
            ),
            heroSuggestion = AppHeroSuggestion(
                title = "DUNE",
                backdropResId = R.drawable.backdrop_dune
            )
        ),
        DockAppItem(
            id = "dock-molotov",
            name = "Molotov",
            category = "Télévision & Replay",
            packageName = "tv.molotov.app",
            iconType = DockIconType.TV,
            bgGradient = listOf(0xFFFFB300, 0xFFFF8F00),
            spotlight = MediaSpotlight(
                id = "spot-molotov",
                appKey = "dock-molotov",
                title = "Molotov TV",
                badge = "Direct TV • Replay • Enregistrement Cloud",
                tagline = "La télévision réinventée : toutes les chaînes françaises gratuites et thématiques.",
                category = "Télévision & Replay",
                year = "2026",
                rating = "Tous publics",
                gradientColors = listOf(0xFFFF8F00, 0xFFE65100, 0xFF3E1200),
                logoText = "Molotov"
            ),
            heroSuggestion = AppHeroSuggestion(
                title = "LE GRAND FILM DU SOIR",
                backdropResId = R.drawable.backdrop_nature
            )
        ),
        DockAppItem(
            id = "dock-games",
            name = "Play Jeux",
            category = "Jeux & Arcade TV",
            packageName = "com.google.android.play.games",
            iconType = DockIconType.ARCADE,
            bgGradient = listOf(0xFF0F9D58, 0xFF0B8043),
            spotlight = MediaSpotlight(
                id = "spot-games",
                appKey = "dock-games",
                title = "Google Play Jeux",
                badge = "Manette Compatible • Cloud Gaming • Arcade TV",
                tagline = "Jouez instantanément sur grand écran avec manette sans fil ou télécommande TV.",
                category = "Jeux TV",
                year = "2026",
                rating = "Tous publics",
                gradientColors = listOf(0xFF0F9D58, 0xFF0B8043, 0xFF03381B),
                logoText = "Play Jeux"
            ),
            heroSuggestion = AppHeroSuggestion(
                title = "ASPHALT LEGENDS",
                backdropResId = R.drawable.backdrop_racing
            )
        ),
        DockAppItem(
            id = "dock-smarttube",
            name = "SmartTube",
            category = "Streaming & Vidéo TV",
            packageName = "org.smarttube.beta",
            iconType = DockIconType.YOUTUBE,
            bgGradient = listOf(0xFFC2185B, 0xFF880E4F),
            spotlight = MediaSpotlight(
                id = "spot-smarttube",
                appKey = "dock-smarttube",
                title = "SmartTube TV",
                badge = "4K HDR • 60 FPS • SponsorBlock",
                tagline = "Profitez de vos vidéos en streaming avec lecture optimisée, haute résolution et fonctionnalités avancées pour grand écran.",
                category = "Lecteur Multimédia TV",
                year = "2026",
                rating = "Tous publics",
                gradientColors = listOf(0xFF880E4F, 0xFF4A148C, 0xFF1A0033),
                logoText = "SmartTube"
            ),
            heroSuggestion = AppHeroSuggestion(
                title = "EXPLORATION SPATIALE",
                backdropResId = R.drawable.backdrop_space
            )
        ),
        DockAppItem(
            id = "dock-jellyfin",
            name = "Jellyfin",
            category = "Serveur Médias Salon",
            packageName = "org.jellyfin.androidtv",
            iconType = DockIconType.TV,
            bgGradient = listOf(0xFF00838F, 0xFF006064),
            spotlight = MediaSpotlight(
                id = "spot-jellyfin",
                appKey = "dock-jellyfin",
                title = "Jellyfin Media Center",
                badge = "Serveur Personnel • 4K HDR • Audio Passthrough",
                tagline = "Diffusez tous vos films, séries, musiques et photos stockés sur votre serveur multimédia personnel directement dans votre salon.",
                category = "Centre Multimédia TV",
                year = "2026",
                rating = "Tous publics",
                gradientColors = listOf(0xFF006064, 0xFF004D40, 0xFF00251A),
                logoText = "Jellyfin"
            ),
            heroSuggestion = AppHeroSuggestion(
                title = "CIVIL WAR",
                backdropResId = R.drawable.backdrop_cinema
            )
        ),
        DockAppItem(
            id = "dock-vlc",
            name = "VLC",
            category = "Lecteur Vidéo & Réseau",
            packageName = "org.videolan.vlc",
            iconType = DockIconType.TV,
            bgGradient = listOf(0xFFFF8800, 0xFFCC6600),
            spotlight = MediaSpotlight(
                id = "spot-vlc",
                appKey = "dock-vlc",
                title = "VLC Media Player",
                badge = "Tous Formats • Réseau Local SMB / DLNA • Sous-titres",
                tagline = "Le lecteur multimédia le plus universel au monde sur votre téléviseur.",
                category = "Lecteur Universel",
                year = "2026",
                rating = "Tous publics",
                gradientColors = listOf(0xFFFF8800, 0xFFB35900, 0xFF3D1F00),
                logoText = "VLC"
            ),
            heroSuggestion = null
        ),
        DockAppItem(
            id = "app-store",
            name = "Google Play Store",
            category = "Boutique d'Applications",
            packageName = "com.android.vending",
            iconType = DockIconType.STORE,
            bgGradient = listOf(0xFF2C2C2E, 0xFF1C1C1E),
            spotlight = MediaSpotlight(
                id = "spot-store",
                appKey = "app-store",
                title = "Google Play Store",
                badge = "Applications • Jeux • Divertissement TV",
                tagline = "Découvrez et téléchargez des milliers d'applications de streaming, jeux et utilitaires optimisés pour votre salon.",
                category = "Boutique d'Applications",
                year = "2026",
                rating = "Tous publics",
                gradientColors = listOf(0xFF2C2C2E, 0xFF1C1C1E, 0xFF0D0D0E),
                logoText = "Play Store"
            ),
            heroSuggestion = null // Strictly no suggestions for store
        ),
        DockAppItem(
            id = "app-settings",
            name = "Paramètres",
            category = "Système & Préférences",
            packageName = "com.talotv.settings",
            iconType = DockIconType.SETTINGS,
            bgGradient = listOf(0xFFFFFFFF, 0xFFFFFFFF),
            spotlight = MediaSpotlight(
                id = "spot-settings",
                appKey = "app-settings",
                title = "Paramètres",
                badge = "Système • Réseau • Affichage & Son • Profils",
                tagline = "Configurez votre téléviseur, connectez vos périphériques Bluetooth et personnalisez votre expérience Talo OS.",
                category = "Préférences Système",
                year = "2026",
                rating = "Système",
                gradientColors = listOf(0xFF3A3D45, 0xFF23252A, 0xFF101114),
                logoText = "Paramètres"
            ),
            heroSuggestion = null // Strictly no suggestions for settings ("Quand c'est pas le cas met rien")
        )
    )

    fun getCuratedSuggestionForPackage(packageName: String): AppHeroSuggestion? {
        return knownDockApps.find { it.packageName == packageName }?.heroSuggestion
    }

    // Backward-compatible dockApps alias
    val dockApps: List<DockAppItem> get() = knownDockApps.take(5)

    // 2. Up Next / À Suivre Row (All functional, targeting YouTube / Live TV)
    val upNextList = listOf(
        UpNextItem(
            id = "up-1",
            title = "Les Grands Reportages",
            subtitle = "Nouveau documentaire • 4K HDR",
            progress = null,
            gradientColors = listOf(0xFFB45309, 0xFF78350F, 0xFF1C1917),
            targetPackage = "com.google.android.youtube.tv"
        ),
        UpNextItem(
            id = "up-2",
            title = "Concerts & Live Sessions",
            subtitle = "Épisode suivant • Audio Hi-Fi",
            progress = null,
            gradientColors = listOf(0xFF1E3A8A, 0xFF172554, 0xFF090D1A),
            targetPackage = "com.google.android.youtube.tvmusic"
        ),
        UpNextItem(
            id = "up-3",
            title = "Exploration Spatiale",
            subtitle = "Reprendre la lecture",
            progress = 0.65f,
            gradientColors = listOf(0xFF047857, 0xFF064E3B, 0xFF021E17),
            targetPackage = "com.google.android.youtube.tv"
        ),
        UpNextItem(
            id = "up-4",
            title = "Journal Télévisé en Direct",
            subtitle = "Direct en continu",
            progress = null,
            gradientColors = listOf(0xFF374151, 0xFF1F2937, 0xFF111827),
            targetPackage = "com.android.tv"
        ),
        UpNextItem(
            id = "up-5",
            title = "Compétition E-Sport & Gaming",
            subtitle = "Reprendre • Finale",
            progress = 0.40f,
            gradientColors = listOf(0xFF0369A1, 0xFF0C4A6E, 0xFF082F49),
            targetPackage = "com.google.android.play.games"
        ),
        UpNextItem(
            id = "up-6",
            title = "Cinéma & Coulisses",
            subtitle = "Nouveautés de la semaine",
            progress = null,
            gradientColors = listOf(0xFF4338CA, 0xFF312E81, 0xFF1E1B4B),
            targetPackage = "com.google.android.youtube.tv"
        )
    )

    // Legacy appLibrary fallback referencing dockApps
    val appLibrary: List<DockAppItem> get() = dockApps
}
