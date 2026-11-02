# Production ProGuard / R8 Rules for Talo TV

# The WebView JavaScript bridge calls these methods from JavaScript
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve core Android components and background services
-keep class com.talotv.launcher.MainActivity { *; }
-keep class com.talotv.launcher.TaloVpnService { *; }
-keep class com.talotv.launcher.TaloAccessibilityService { *; }
-keep class com.talotv.launcher.HomeAssistant { *; }
-keep class com.talotv.launcher.Doorbell { *; }

# Prevent stripping reflection or JSON-accessed fields
-keepclassmembers class com.talotv.launcher.** {
    <fields>;
    <init>(...);
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
