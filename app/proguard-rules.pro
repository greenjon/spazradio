# 1. ESSENTIAL: Keep line numbers and names for debugging (Open Source friendly)
-keepattributes SourceFile,LineNumberTable
-dontobfuscate

# 2. XR Warnings: Keep these to prevent build failures on newer AGP versions
-dontwarn android.xr.**
-dontwarn androidx.xr.**
-dontwarn com.google.android.gles_jecon.**

# 3. Media3 / ExoPlayer: Prevent R8 from over-optimizing the player logic
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }

# 4. Gson: Protect your data classes so JSON parsing still works
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# Protect models in the data package
-keep class llm.slop.spazradio.data.** { *; }
-keep class llm.slop.spazradio.models.** { *; }

# Protect models defined in the main package (like RawShow, ScheduleItem, TrackInfo)
-keep class llm.slop.spazradio.RawShow { *; }
-keep class llm.slop.spazradio.ScheduleItem { *; }
-keep class llm.slop.spazradio.TrackInfo { *; }

# 5. MQTT: Paho is notorious for reflection issues
-keep class org.eclipse.paho.client.mqttv3.** { *; }
-dontwarn org.eclipse.paho.client.mqttv3.**
