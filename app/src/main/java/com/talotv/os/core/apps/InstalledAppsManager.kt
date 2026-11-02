package com.talotv.os.core.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.talotv.os.core.media.ContentRepository
import com.talotv.os.core.media.DockAppItem
import com.talotv.os.core.media.toDockAppItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class InstalledApp(
    val id: String,
    val name: String,
    val packageName: String,
    val className: String,
    val iconBitmap: ImageBitmap?,
    val isBanner: Boolean = false,
    val isStore: Boolean = false,
    val isSettings: Boolean = false
)

class InstalledAppsManager(private val context: Context) {

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            reloadInstalledApps()
        }
    }

    init {
        reloadInstalledApps()
        registerPackageReceiver()
    }

    private fun registerPackageReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(packageReceiver, filter)
            }
        } catch (e: Throwable) {
            // Receiver registration fallback
        }
    }

    fun reloadInstalledApps(): List<InstalledApp> {
        val pm = context.packageManager
        val selfPkg = context.packageName

        val leanbackIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }
        val leanbackResolves = pm.queryIntentActivities(leanbackIntent, 0)

        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val launcherResolves = pm.queryIntentActivities(launcherIntent, 0)

        // Leanback activities take precedence over phone launcher activities
        val combinedResolves = (leanbackResolves + launcherResolves)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != selfPkg }

        val list = mutableListOf<InstalledApp>()

        // 1. Google Play Store (Real com.android.vending with original system name)
        val playStoreResolve = combinedResolves.find { it.activityInfo.packageName == "com.android.vending" }
        val (playStoreDrawable, playIsBanner) = if (playStoreResolve != null) {
            extractAppDrawableAndBanner(playStoreResolve, pm)
        } else {
            val appDrawable: Drawable? = try {
                pm.getApplicationBanner("com.android.vending") ?: pm.getApplicationIcon("com.android.vending")
            } catch (e: Throwable) {
                null
            }
            appDrawable to false
        }

        val originalStoreName = playStoreResolve?.loadLabel(pm)?.toString()?.ifBlank { "Google Play Store" }
            ?: "Google Play Store"

        list.add(
            InstalledApp(
                id = "app-store",
                name = originalStoreName,
                packageName = "com.android.vending",
                className = playStoreResolve?.activityInfo?.name ?: "com.google.android.finsky.tvmainactivity.TvMainActivity",
                iconBitmap = drawableToImageBitmap(playStoreDrawable),
                isBanner = playIsBanner,
                isStore = true
            )
        )

        // 2. All other REAL installed apps on this TV system (excluding Android TV settings to keep only Talo Paramètres)
        combinedResolves
            .filter { 
                it.activityInfo.packageName != "com.android.vending" &&
                it.activityInfo.packageName != "com.android.tv.settings"
            }
            .sortedBy { it.loadLabel(pm).toString() }
            .forEach { resolve ->
                val pkg = resolve.activityInfo.packageName
                val rawLabel = resolve.loadLabel(pm).toString().ifBlank { resolve.activityInfo.name }
                val label = when (pkg) {
                    "com.android.tv" -> "Télévision Direct"
                    "com.google.android.youtube.tv" -> "YouTube"
                    "com.google.android.youtube.tvmusic" -> "YouTube Music"
                    "com.google.android.play.games" -> "Play Jeux"
                    "org.smarttube.beta" -> "SmartTube"
                    "org.videolan.vlc" -> "VLC"
                    "org.jellyfin.androidtv" -> "Jellyfin"
                    "com.limelight" -> "Moonlight"
                    else -> rawLabel
                }

                val (drawable, isBanner) = extractAppDrawableAndBanner(resolve, pm)

                list.add(
                    InstalledApp(
                        id = "pkg-$pkg",
                        name = label,
                        packageName = pkg,
                        className = resolve.activityInfo.name,
                        iconBitmap = drawableToImageBitmap(drawable),
                        isBanner = isBanner,
                        isSettings = false
                    )
                )
            }

        // 3. Talo OS Settings Tile (Built-in)
        list.add(
            InstalledApp(
                id = "app-settings",
                name = "Paramètres",
                packageName = "com.talotv.settings",
                className = "",
                iconBitmap = null,
                isBanner = false,
                isSettings = true
            )
        )

        _installedApps.value = list
        return list
    }

    /**
     * Supplies Floating Dock apps STRICTLY from apps PHYSICALLY INSTALLED on this TV.
     * Takes up to 5 apps for the signature single-line dock.
     * Guaranteed: ZERO phantom or uninstalled apps!
     */
    fun getDockApps(): List<DockAppItem> {
        val currentInstalled = _installedApps.value
        val installedMap = currentInstalled.associateBy { it.packageName }
        val result = mutableListOf<DockAppItem>()

        // 1. First add known apps that are physically installed on this TV (in prioritized order)
        for (known in ContentRepository.knownDockApps) {
            val installed = installedMap[known.packageName]
            if (installed != null) {
                val item = known.copy(
                    id = installed.id,
                    name = installed.name,
                    iconBitmap = installed.iconBitmap,
                    isBanner = installed.isBanner
                )
                result.add(
                    item.copy(
                        heroSuggestion = com.talotv.os.core.media.ContentSuggestionManager.getSuggestion(context, item)
                    )
                )
            }
            if (result.size >= 5) break
        }

        // 2. If fewer than 5 matching known dock apps, fill with other installed apps
        if (result.size < 5) {
            val usedPackages = result.map { it.packageName }.toSet()
            for (app in currentInstalled) {
                if (result.size >= 5) break
                if (app.packageName in usedPackages) continue
                val dockItem = app.toDockAppItem()
                result.add(
                    dockItem.copy(
                        heroSuggestion = com.talotv.os.core.media.ContentSuggestionManager.getSuggestion(context, dockItem)
                    )
                )
            }
        }

        return result
    }

    /**
     * Launches the real Google Play Store for TV (Talo Store)
     */
    fun launchStore(activityContext: Context) {
        val pm = activityContext.packageManager
        val playIntent = pm.getLeanbackLaunchIntentForPackage("com.android.vending")
            ?: pm.getLaunchIntentForPackage("com.android.vending")

        if (playIntent != null) {
            playIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activityContext.startActivity(playIntent)
        } else {
            try {
                val directIntent = Intent().apply {
                    setClassName("com.android.vending", "com.google.android.finsky.tvmainactivity.TvMainActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activityContext.startActivity(directIntent)
            } catch (e: Throwable) {
                try {
                    val marketIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("market://")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activityContext.startActivity(marketIntent)
                } catch (e2: Throwable) {
                    Toast.makeText(activityContext, "Ouverture de Talo Store...", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun launchPackage(activityContext: Context, pkg: String, name: String) {
        if (pkg == "com.android.vending") {
            launchStore(activityContext)
            return
        }
        val pm = activityContext.packageManager
        val launchIntent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activityContext.startActivity(launchIntent)
        } else {
            Toast.makeText(activityContext, "Lancement de $name...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractAppDrawableAndBanner(
        resolve: android.content.pm.ResolveInfo,
        pm: PackageManager
    ): Pair<Drawable?, Boolean> {
        val banner: Drawable? = try {
            resolve.activityInfo.loadBanner(pm) ?: resolve.activityInfo.applicationInfo.loadBanner(pm)
        } catch (e: Throwable) {
            null
        }
        val icon: Drawable? = try {
            resolve.loadIcon(pm) ?: resolve.activityInfo.loadIcon(pm)
        } catch (e: Throwable) {
            null
        }

        return if (banner != null && banner.intrinsicWidth > 0 && banner.intrinsicHeight > 0 &&
            (banner.intrinsicWidth.toFloat() / banner.intrinsicHeight.toFloat()) >= 1.3f
        ) {
            banner to true
        } else {
            (banner ?: icon) to false
        }
    }

    private fun drawableToImageBitmap(drawable: Drawable?): ImageBitmap? {
        if (drawable == null) return null
        return try {
            if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
                return drawable.bitmap.asImageBitmap()
            }
            val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth.coerceIn(48, 720) else 192
            val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight.coerceIn(48, 720) else 192
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (e: Throwable) {
            null
        }
    }
}
