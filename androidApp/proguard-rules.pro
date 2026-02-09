# ============================================================================
# ProGuard/R8 rules for ListenUp Android client
# ============================================================================

# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep serializable classes and their serializers
-keep,includedescriptorclasses class com.calypsan.listenup.**$$serializer { *; }
-keepclassmembers class com.calypsan.listenup.** {
    *** Companion;
}
-keepclasseswithmembers class com.calypsan.listenup.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Ktor ---
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# --- OkHttp (used by Ktor engine) ---
-dontwarn okhttp3.**
-dontwarn okio.**

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# --- Koin ---
-keep class org.koin.** { *; }

# --- Media3 / ExoPlayer ---
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# --- Coil ---
-dontwarn coil3.**

# --- AndroidX general ---
-dontwarn androidx.**

# --- SLF4J ---
-dontwarn org.slf4j.**

# --- Kotlin ---
-dontwarn kotlin.**
-dontwarn kotlinx.**

# --- Keep R8 from stripping Compose ---
-keep class androidx.compose.** { *; }
