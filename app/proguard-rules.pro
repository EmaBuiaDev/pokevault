# ── PokeVault ProGuard / R8 Rules ──
#
# Regola generale: qui stanno SOLO le regole che nascono dal codice di questa
# app. Le librerie (Firebase, Play Services, CameraX, ML Kit, Billing,
# Credentials, Retrofit, OkHttp, WorkManager, Room, Compose) pubblicano le
# proprie regole consumer dentro l'AAR e AGP le unisce da sola: si vedono in
# app/build/outputs/mapping/release/configuration.txt.
#
# Prima questo file le duplicava con dei `-keep class <libreria>.** { *; }`, e
# il risultato era che R8 girava ma non poteva fare quasi nulla: 12.062 classi
# su 18.779 restavano intoccabili, cioe' il 64% dell'app non veniva ne'
# rinominato ne' ottimizzato ne' potato. E' la ragione dell'avviso di Play
# sull'ottimizzazione R8. I `-dontwarn` restano: non impediscono nulla, evitano
# solo che R8 si fermi su riferimenti assenti a runtime.

# Le righe nello stack trace servono a leggere i crash: senza, i report di Play
# arrivano senza numero di riga.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Modelli serializzati (Gson + Firestore) ──
# Questi si popolano per reflection sui nomi dei campi: rinominarli significa
# leggere oggetti con tutti i campi a null.
-keep class com.emabuia.pokevault.data.model.** { *; }
-keep class com.emabuia.pokevault.data.firebase.CollectionStats { *; }
-keep class com.emabuia.pokevault.data.remote.** { *; }
-keep class com.emabuia.pokevault.data.italian.** { *; }

# I DTO dei codici regalo stanno in data.billing, fuori dai package qui sopra.
#
# La sola @SerializedName NON basta, ed e' stato verificato sull'AAB: con R8 in
# full mode il danno non e' il rinominamento, e' la propagazione dei valori.
# Nessuno *scrive* questi campi nel bytecode -- li popola Gson per reflection,
# che R8 non vede -- quindi R8 li considera costantemente null, riduce
# `payload?.entitled == true` a `false` e da li' pota via tutto il ramo a valle.
#
# Nell'AAB prodotto senza questa regola: RedeemResult$Success risultava
# R8$$REMOVED$$CLASS$$854, RedeemResult$Rejected spariva del tutto e l'unico
# esito sopravvissuto era Unavailable. Tradotto: in release il riscatto di un
# codice falliva sempre, con "non riesco a contattare il server", mentre in
# debug -- dove R8 non gira -- funzionava tutto.
-keep class com.emabuia.pokevault.data.billing.GiftCodeRepository$GiftStatusPayload { *; }
-keep class com.emabuia.pokevault.data.billing.GiftCodeRepository$RedeemPayload { *; }

# ── Attributi richiesti da Retrofit e Gson per i tipi generici ──
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses,EnclosingMethod
-keepattributes *Annotation*

# ── Gson ──
# Gson e' l'unica dipendenza del progetto che NON pubblica regole consumer:
# in configuration.txt non compare nessuna sua sezione.
#
# Queste due righe non sono prudenza, sono cicatrici. Sostituirle con le sole
# regole mirate raccomandate dal progetto Gson (quelle qui sotto, che restano
# perche' servono comunque) non basta con R8 in full mode: la release
# rispondeva con la lista delle espansioni vuota e crashava su ogni schermata
# che fa una chiamata di rete -- Pokedex, Meta Deck, Cardmarket -- mentre in
# debug, dove R8 non gira, funzionava tutto. Verificato su device.
#
# Retrofit e' tenuta insieme a Gson: pubblica regole proprie, ma le parla
# attraverso GsonConverterFactory ed e' l'altro lato della stessa rottura.
# Separarle per scoprire se una delle due basta significa un altro giro di
# build e di prove sul telefono per recuperare meno di 100 KB su un APK di
# 24,9 MB. Insieme costano 138 KB dei 4,4 MB che R8 ha recuperato.
-keep class com.google.gson.** { *; }
-keep class retrofit2.** { *; }

-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# I TypeToken anonimi perdono il tipo generico se vengono toccati: da li' nasce
# il classico crash su ParameterizedType.
-keep class * extends com.google.gson.reflect.TypeToken { *; }
# Rete di sicurezza per eventuali DTO fuori dai package tenuti sopra.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn sun.misc.**

# ── Avvisi da riferimenti opzionali ──
# (androidx.** e' gia' coperto da proguard-android-optimize.txt)
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.tensorflow.**
-dontwarn com.google.mlkit.**
-dontwarn coil.**
-dontwarn com.google.android.libraries.identity.googleid.**
-dontwarn com.android.billingclient.**
-dontwarn com.google.accompanist.**
