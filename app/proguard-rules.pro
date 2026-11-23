# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.progresspal.app.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.progresspal.app.data.**$$serializer { *; }

# Embedded YouTube player calls these from JavaScript.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
