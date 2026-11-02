package com.talotv.os.core.boot

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.talotv.os.MainActivity
import com.talotv.os.core.security.TaloSecurityVault
import com.talotv.os.core.system.SettingsLaunchCoordinator

/**
 * Service d'Accessibilité Talo OS :
 * - Intercepte les touches de navigation physique (Home, Menu, Settings).
 * - En mode Perdu/Verrouillage strict (Fortress Lock), bloque toute tentative de fuite.
 * - Capture les déclenchements externes des paramètres système (Extended Controls Android Studio, raccourcis télécommande)
 *   et redirige vers le volet global des Paramètres Talo OS, sauf si explicitement ouvert depuis Talo OS.
 */
class HomeKeyAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: ""

            if (pkg == "com.android.tv.settings") {
                if (SettingsLaunchCoordinator.isAuthorized()) {
                    // Session légitimement ouverte depuis le volet Talo OS ("Paramètres TV")
                    Log.d("TaloAccessibility", "Native Android TV Settings allowed (authorized session).")
                } else {
                    // Déclenchement externe non sollicité (ex: Extended Controls d'Android Studio, raccourci système)
                    Log.d("TaloAccessibility", "Unauthorized external launch of $pkg detected. Redirecting to Talo OS Settings.")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    val intent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("screen", "settings")
                    }
                    startActivity(intent)
                }
            } else if (pkg == packageName || pkg == "com.talotv.os" || pkg == "com.talotv.os.debug") {
                // Retour sur le launcher Talo OS : réinitialiser l'autorisation pour les prochaines requêtes externes
                SettingsLaunchCoordinator.clearAuthorization()
            }
        }
    }

    override fun onInterrupt() {
        Log.w("TaloAccessibility", "Accessibility service interrupted")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action

        val isLocked = TaloSecurityVault.isLocked(this)

        if (isLocked) {
            // When in Lost Mode, lock out all navigation attempts
            if (keyCode == KeyEvent.KEYCODE_HOME ||
                keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_MENU ||
                keyCode == KeyEvent.KEYCODE_SETTINGS ||
                keyCode == KeyEvent.KEYCODE_APP_SWITCH
            ) {
                if (action == KeyEvent.ACTION_UP) {
                    val intent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(intent)
                }
                return true // Consume and block completely
            }
        }

        // Standard Home key interception (Google TV stock override)
        if (keyCode == KeyEvent.KEYCODE_HOME && action == KeyEvent.ACTION_UP) {
            Log.d("TaloAccessibility", "Home Key captured. Launching Talo OS.")
            SettingsLaunchCoordinator.cancelReturnToSettings()
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            return true
        }

        // Settings key interception (Remote control / Extended Controls)
        val isSettingsKey = keyCode == KeyEvent.KEYCODE_SETTINGS ||
                keyCode == 289 /* KEYCODE_TV_SETTINGS */ ||
                keyCode == 312 /* KEYCODE_QUICK_SETTINGS */ ||
                keyCode == KeyEvent.KEYCODE_MENU

        if (isSettingsKey) {
            if (action == KeyEvent.ACTION_UP) {
                Log.d("TaloAccessibility", "Settings Key captured ($keyCode). Launching Talo OS Settings.")
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("screen", "settings")
                }
                startActivity(intent)
            }
            return true
        }

        // Favorite / Star (★) key detection (Google TV Streamer 4K remote customizable button)
        val isFavoriteKey = keyCode == KeyEvent.KEYCODE_BOOKMARK ||
                keyCode == KeyEvent.KEYCODE_STAR ||
                keyCode == KeyEvent.KEYCODE_BUTTON_1 ||
                keyCode == KeyEvent.KEYCODE_PROG_RED ||
                keyCode == 288 /* KEYCODE_TV_INPUT */ ||
                keyCode == 305 /* KEYCODE_BUTTON_C */
        if (isFavoriteKey) {
            Log.d("TaloAccessibility", "Favorite/Star button pressed ($keyCode). Reserved for future action.")
            // Kept ready for future binding as requested
        }

        return super.onKeyEvent(event)
    }
}
