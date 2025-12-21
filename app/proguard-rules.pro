# 1. ESSENTIAL: Keep line numbers and names for debugging
-keepattributes SourceFile,LineNumberTable
-dontobfuscate

# 2. Keep ViewModels & State
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <fields>;
    <methods>;
}
-keep class * extends androidx.lifecycle.ViewModel

# 3. XR / Media3 Warnings
-dontwarn android.xr.**
-dontwarn androidx.xr.**
-dontwarn com.google.android.gles_jecon.**
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }

# 4. Gson & Data Models (Very explicit)
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# Keep all classes AND their members in the data package
-keep class llm.slop.spazradio.data.** { *; }
-keepclassmembers class llm.slop.spazradio.data.** { *; }

# Keep other models used for JSON parsing
-keep class llm.slop.spazradio.RawShow { *; }
-keep class llm.slop.spazradio.ScheduleItem { *; }
-keep class llm.slop.spazradio.TrackInfo { *; }
-keepclassmembers class llm.slop.spazradio.RawShow { *; }
-keepclassmembers class llm.slop.spazradio.ScheduleItem { *; }
-keepclassmembers class llm.slop.spazradio.TrackInfo { *; }

# 5. MQTT
-keep class org.eclipse.paho.client.mqttv3.** { *; }
-dontwarn org.eclipse.paho.client.mqttv3.**

# 6. Compose Internal
-keep class androidx.compose.runtime.snapshots.** { *; }
-dontwarn androidx.compose.runtime.snapshots.**
