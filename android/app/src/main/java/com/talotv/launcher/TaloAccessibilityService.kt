package com.talotv.launcher

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class TaloAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var userRequestedTvSettings: Boolean = false
            set(value) {
                field = value
                if (value) {
                    lastRequestedTime = System.currentTimeMillis()
                }
            }

        @Volatile
        private var lastRequestedTime: Long = 0L

        @Volatile
        private var lastInterceptTime: Long = 0L

        fun isSettingsRequestValid(): Boolean {
            if (!userRequestedTvSettings) return false
            if (System.currentTimeMillis() - lastRequestedTime > 30_000L) {
                userRequestedTvSettings = false
                return false
            }
            return true
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intercepting system settings packages here previously caused Android TV's settings app
        // to be hijacked and pulled away while the user was toggling the switch ON,
        // which caused Android to immediately uncheck the accessibility service.
        // Remote button overrides (Settings, Menu, Live, Guide, Watchlist, Home) are fully
        // and reliably handled below in onKeyEvent().
    }

    override fun onInterrupt() {
        // Not used
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action

        Log.i("TaloAccessibility", "onKeyEvent: keyCode=$keyCode, action=$action, repeat=${event.repeatCount}")

        // 1. Settings / Menu / Quick settings / Setup
        if (keyCode == KeyEvent.KEYCODE_SETTINGS ||
            keyCode == 302 /* KEYCODE_QUICK_SETTINGS */ ||
            keyCode == 197 /* KEYCODE_SETUP */ ||
            keyCode == KeyEvent.KEYCODE_MENU ||
            keyCode == KeyEvent.KEYCODE_INFO ||
            keyCode == KeyEvent.KEYCODE_PAIRING ||
            keyCode == KeyEvent.KEYCODE_HELP) {
            if (action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                MainActivity.handleRemoteAction(this, "settings")
            }
            return true // Consume to block Android TV's system settings from opening!
        }

        // 2. Watchlist (Favorites)
        if (keyCode == KeyEvent.KEYCODE_BOOKMARK ||
            keyCode == 174 /* KEYCODE_BOOKMARK */ ||
            keyCode == KeyEvent.KEYCODE_PROG_YELLOW ||
            keyCode == 185 ||
            keyCode == KeyEvent.KEYCODE_BUTTON_Y) {
            if (action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                MainActivity.handleRemoteAction(this, "watchlist")
            }
            return true
        }

        // 3. Live Channels (TV / Direct)
        if (keyCode == KeyEvent.KEYCODE_TV ||
            keyCode == KeyEvent.KEYCODE_GUIDE ||
            keyCode == 170 ||
            keyCode == 172 ||
            keyCode == KeyEvent.KEYCODE_WINDOW ||
            keyCode == KeyEvent.KEYCODE_TV_DATA_SERVICE ||
            keyCode == KeyEvent.KEYCODE_PROG_RED ||
            keyCode == 183 ||
            keyCode == KeyEvent.KEYCODE_PROG_BLUE ||
            keyCode == KeyEvent.KEYCODE_PROG_GREEN ||
            keyCode == KeyEvent.KEYCODE_DVR ||
            keyCode == KeyEvent.KEYCODE_CAPTIONS) {
            if (action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                MainActivity.handleRemoteAction(this, "live")
            }
            return true
        }

        // 4. Home button
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            if (action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                MainActivity.handleRemoteAction(this, "home")
            }
            return true
        }

        return super.onKeyEvent(event)
    }
}
