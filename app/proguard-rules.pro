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
-keep class com.emabuia.pokevault.data.model.** { *; }
-keep class com.emabuia.pokevault.data.firebase.CollectionStats { *; }

# ── ALL API/network classes: Retrofit interfaces + Gson DTOs + Limitless ──
# Single wildcard rule prevents R8 from stripping generic signatures,
# renaming fields, or removing classes used by Gson/Retrofit via reflection.
-keep class com.emabuia.pokevault.data.remote.** { *; }

# ── Retrofit / OkHttp ──
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class com.squareup.okhttp3.** { *; }

# ── Attributes required by Retrofit + Gson for generic type resolution ──
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses,EnclosingMethod
-keepattributes *Annotation*

# ── Gson ──
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# Keep generic signatures for Gson TypeToken (fixes ParameterizedType crash)
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

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

# ── Coil (Image Loading) ──
-keep class coil.** { *; }
-dontwarn coil.**

# ── Google Play Billing ──
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**
-keep class com.android.vending.billing.** { *; }

# ── Credentials / Google ID ──
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.googleid.**

# ── CameraX ──
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ── Accompanist ──
-keep class com.google.accompanist.** { *; }
-dontwarn com.google.accompanist.**
