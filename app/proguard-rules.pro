# Proguard rules for VPSBrowser
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
-keep class com.vpsbrowser.app.model.** { *; }
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
