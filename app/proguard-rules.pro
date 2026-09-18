# TrainKraft ProGuard rules

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep Room entities (used reflectively by Room compiler)
-keep class com.trainkraft.app.data.TrainEntity { *; }
-keep class com.trainkraft.app.data.StationEntity { *; }
-keep class com.trainkraft.app.data.TripEntity { *; }
-keep class com.trainkraft.app.data.CalendarEntity { *; }
-keep class com.trainkraft.app.data.StopTimeEntity { *; }

# NTES keys — strip hardcoded constants
-assumenosideeffects class com.trainkraft.app.data.NtesCrypto {
    public static final java.lang.String KEY;
    public static final java.lang.String IV;
    public static final java.lang.String SCKEY;
}
