# Preserve line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Room ──────────────────────────────────────────────────────────────────────
# Keep all Room entity classes (annotated with @Entity)
-keep @androidx.room.Entity class * { *; }
# Keep DAO interfaces (R8 generates implementations at compile time, but keep names for reflection)
-keep @androidx.room.Dao interface * { *; }
# Keep Database class
-keep @androidx.room.Database class * { *; }
# Keep TypeConverters
-keep @androidx.room.TypeConverters class * { *; }

# ── Gson ──────────────────────────────────────────────────────────────────────
# Keep all model classes that Gson serializes/deserializes
-keepclassmembers class com.example.nuclearattackautomessageconcept.model.** { *; }
-keep class com.example.nuclearattackautomessageconcept.model.** { *; }
# Generic Gson rules
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ── Compose Reorderable ───────────────────────────────────────────────────────
-keep class org.burnoutcrew.reorderable.** { *; }

# ── Kotlin coroutines ─────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── DataStore ─────────────────────────────────────────────────────────────────
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}
