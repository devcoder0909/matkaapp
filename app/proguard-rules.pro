# ProGuard & R8 optimization rules for Matka Booking WebView App

# Preserve WebView Javascript interfaces and callbacks
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve WebChromeClient and WebViewClient subclasses
-keep public class * extends android.webkit.WebChromeClient {
    public *;
}
-keep public class * extends android.webkit.WebViewClient {
    public *;
}

# Preserve line numbers for stacktraces
-keepattributes SourceFile,LineNumberTable
