# ── WebView JS Bridge ──
-keepclassmembers class com.mukbang.roulette.MainActivity$JsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# ── MainActivity (inner class reference) ──
-keep class com.mukbang.roulette.MainActivity { *; }

# ── AndroidX WebKit ──
-keep class androidx.webkit.** { *; }

# ── BuildConfig (API key access) ──
-keep class com.mukbang.roulette.BuildConfig { *; }
