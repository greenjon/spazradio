# 1. ESSENTIAL: Keep line numbers and names for debugging
-keepattributes SourceFile,LineNumberTable
-dontobfuscate

# 2. Keep ViewModels & State
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <fields>;
    <methods>;
}
-keep class * extends androidx.lifecycle.ViewModel

# 3. Compose Foundation (Pager, Lazy, etc.)
-keep class androidx.compose.foundation.pager.** { *; }
-keep class androidx.compose.foundation.lazy.** { *; }
-keep class androidx.compose.foundation.gestures.** { *; }
-keep class androidx.compose.animation.core.** { *; }

# 4. XR / Media3 Warnings
-dontwarn android.xr.**
-dontwarn androidx.xr.**
-dontwarn com.google.android.gles_jecon.**
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }

# 5. Gson & Data Models
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class llm.slop.spazradio.data.** { *; }
-keepclassmembers class llm.slop.spazradio.data.** { *; }
-keep class llm.slop.spazradio.RawShow { *; }
-keep class llm.slop.spazradio.ScheduleItem { *; }
-keep class llm.slop.spazradio.TrackInfo { *; }
-keepclassmembers class llm.slop.spazradio.RawShow { *; }
-keepclassmembers class llm.slop.spazradio.ScheduleItem { *; }
-keepclassmembers class llm.slop.spazradio.TrackInfo { *; }

# 6. MQTT
-keep class org.eclipse.paho.client.mqttv3.** { *; }
-dontwarn org.eclipse.paho.client.mqttv3.**

# 7. Compose Internal & Snapshot
-keep class androidx.compose.runtime.snapshots.** { *; }
-dontwarn androidx.compose.runtime.snapshots.**
-keep class androidx.compose.runtime.** { *; }

# Ensures that the minifier doesn't use random naming seeds
-repackageclasses ''