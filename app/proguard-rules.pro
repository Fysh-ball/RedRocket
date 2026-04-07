# Preserve line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*
-keepattributes Signature

# ── App code — keep everything to prevent R8 breaking receivers, services, Room, ViewModels ──
-keep class site.fysh.redrocket.** { *; }
-keepclassmembers class site.fysh.redrocket.** { *; }

# ── Room ──────────────────────────────────────────────────────────────────────
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.TypeConverters class * { *; }
# Room generates *_Impl classes at compile time — must not be removed
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class **_Impl { *; }

# ── ViewModel ─────────────────────────────────────────────────────────────────
-keep class * extends androidx.lifecycle.ViewModel { *; }

# ── Gson ──────────────────────────────────────────────────────────────────────
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ── Kotlin ────────────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
# Kotlin companion objects and object singletons
-keepclassmembers class * {
    public static ** Companion;
    public static ** INSTANCE;
}

# ── DataStore ─────────────────────────────────────────────────────────────────
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}
