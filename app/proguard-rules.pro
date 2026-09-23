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
-keep class com.trainkraft.app.data.TrackedTrainEntity { *; }
-keep class com.trainkraft.app.data.CachedResponseEntity { *; }
-keep class com.trainkraft.app.data.DelayPriorEntity { *; }
-keep class com.trainkraft.app.data.FogOverlayEntity { *; }
-keep class com.trainkraft.app.data.PackMetaEntity { *; }

# User database (privacy split: tracked trains + response cache in user.db)
-keep class com.trainkraft.app.data.UserDatabase { *; }

# NTES keys — strip hardcoded constants
-assumenosideeffects class com.trainkraft.app.data.NtesCrypto {
    public static final java.lang.String KEY;
    public static final java.lang.String IV;
    public static final java.lang.String SCKEY;
}

# WorkManager — keep Worker class for serialization
-keep class com.trainkraft.app.LiveStatusNotificationWorker { *; }

# PNR data classes (used by JSONObject reflection)
-keep class com.trainkraft.app.data.PnrApi$PnrResult { *; }
-keep class com.trainkraft.app.data.PnrApi$PnrPassenger { *; }

# NtesKeys (used by NtesConfig.parseKeys)
-keep class com.trainkraft.app.data.NtesKeys { *; }

# TrackingService + alarms (Phase C foreground service, manifest-referenced)
-keep class com.trainkraft.app.TrackingService { *; }
-keep class com.trainkraft.app.AlarmReceiver { *; }
-keep class com.trainkraft.app.AlarmScheduler { *; }
-keep class com.trainkraft.app.BatteryExemption { *; }
