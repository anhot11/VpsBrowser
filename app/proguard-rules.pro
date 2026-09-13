# Proguard rules for VPSBrowser
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
-keep class com.vpsbrowser.app.model.** { *; }
