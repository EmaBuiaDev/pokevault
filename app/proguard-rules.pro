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

# ── API response / DTO classes (Gson deserialization) ──
-keep class com.example.pokevault.data.remote.SetsResponse { *; }
-keep class com.example.pokevault.data.remote.CardsResponse { *; }
-keep class com.example.pokevault.data.remote.SingleCardResponse { *; }
-keep class com.example.pokevault.data.remote.SingleSetResponse { *; }
-keep class com.example.pokevault.data.remote.TcgSet { *; }
-keep class com.example.pokevault.data.remote.SetImages { *; }
-keep class com.example.pokevault.data.remote.TcgCard { *; }
-keep class com.example.pokevault.data.remote.TcgCardSet { *; }
-keep class com.example.pokevault.data.remote.CardImages { *; }
-keep class com.example.pokevault.data.remote.TcgPlayer { *; }
-keep class com.example.pokevault.data.remote.TcgPriceInfo { *; }
-keep class com.example.pokevault.data.remote.CardMarket { *; }
-keep class com.example.pokevault.data.remote.CardMarketPrices { *; }

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
# Keep generic signatures for Gson TypeToken (fixes ParameterizedType crash)
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# ── Retrofit API interfaces ──
-keep,allowobfuscation interface com.example.pokevault.data.remote.PokeTcgApiService
-keep,allowobfuscation interface com.example.pokevault.data.remote.LimitlessTcgApiService
-keep,allowobfuscation interface com.example.pokevault.data.remote.TranslationService

# ── LimitlessTCG API response classes ──
-keep class com.example.pokevault.data.remote.LimitlessTournament { *; }
-keep class com.example.pokevault.data.remote.LimitlessStanding { *; }
-keep class com.example.pokevault.data.remote.LimitlessRecord { *; }
-keep class com.example.pokevault.data.remote.LimitlessDeckInfo { *; }

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
