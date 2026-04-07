# ── PokéVault ProGuard Rules ──

# Keep line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Firebase ──
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ── Firestore model classes ──
-keep class com.example.pokevault.data.model.** { *; }
-keep class com.example.pokevault.data.firebase.CollectionStats { *; }

# ── Retrofit / OkHttp ──
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class com.squareup.okhttp3.** { *; }

# ── Gson ──
-keep class com.google.gson.** { *; }
-keepattributes *Annotation*
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ── Retrofit API interfaces ──
-keep,allowobfuscation interface com.example.pokevault.data.remote.PokeTcgApiService
-keep,allowobfuscation interface com.example.pokevault.data.remote.LimitlessTcgApiService
-keep,allowobfuscation interface com.example.pokevault.data.remote.TranslationService

# ── TensorFlow Lite ──
-keep class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

# ── ML Kit ──
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ── Kotlin serialization / coroutines ──
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ── Enums ──
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
