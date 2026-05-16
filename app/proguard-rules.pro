# Keep NewPipeExtractor and its dependencies (uses reflection)
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**

# Media3
-keep class androidx.media3.** { *; }

# Room
-keep class androidx.room.** { *; }
