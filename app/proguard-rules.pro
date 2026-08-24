
# ================================================================
# 1. MODÈLES GSON  <-  LE POINT CRITIQUE
# ================================================================
#
# Gson mappe les champs Java par leur NOM.
#
# R8 renomme les champs non annotés @SerializedName.
# Le JSON du master ne correspond alors plus à rien, et Gson
# instancie l'objet via Unsafe en laissant les champs à null —
# y compris des String Kotlin déclarés non-nullables.
#
# Symptôme typique : UserOut.email == null en release,
# donc "Mon compte" reste vide alors que /auth/me a répondu 200.
#
# Champs actuellement NON annotés et donc vulnérables :
#   UserOut        : id, email, plan
#   DeviceOut      : id, name
#   SessionOut     : id
#   TokenPair      : (tous annotés)
#   GenericMessage : detail
#
# GenericMessage est particulièrement piégeux : il sert à
# extractErrorDetail(), donc sa casse rendrait TOUS les messages
# d'erreur illisibles.

-keep class com.deliriuum.app.data.UserOut { *; }
-keep class com.deliriuum.app.data.TokenPair { *; }
-keep class com.deliriuum.app.data.DeviceOut { *; }
-keep class com.deliriuum.app.data.SessionOut { *; }
-keep class com.deliriuum.app.data.WireGuardConfigOut { *; }
-keep class com.deliriuum.app.data.GenericMessage { *; }

# Filet de sécurité : tout champ annoté reste préservé même si
# un nouveau modèle est ajouté sans mettre à jour ce fichier.
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}


# ================================================================
# 2. GSON
# ================================================================

-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

-dontwarn sun.misc.**

-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Gson passe par Unsafe quand il n'y a pas de constructeur
# sans argument — cas de toutes les data classes Kotlin.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}


# ================================================================
# 3. RETROFIT
# ================================================================
#
# Les signatures génériques (Response<UserOut>, List<DeviceOut>)
# doivent survivre : Retrofit les lit par réflexion pour choisir
# le converter.

-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations

-keep,allowobfuscation interface com.deliriuum.app.data.DeliriuumApiService { *; }

-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-dontwarn retrofit2.**
-dontwarn javax.annotation.**


# ================================================================
# 4. OKHTTP
# ================================================================

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**


# ================================================================
# 5. WIREGUARD  <-  DEUXIÈME POINT CRITIQUE
# ================================================================
#
# GoBackend appelle du code Kotlin DEPUIS le natif (JNI).
#
# R8 ne voit aucun appelant Java pour ces méthodes et les
# considère comme mortes. En release, onStateChange() ne serait
# alors jamais invoqué : le tunnel tomberait sans que l'UI
# repasse en DISCONNECTED, et sans reconnexion automatique.

-keep class com.wireguard.** { *; }
-keep interface com.wireguard.** { *; }

-keepclassmembers class * implements com.wireguard.android.backend.Tunnel {
    public *;
}

-keep class com.deliriuum.app.data.TunnelManager { *; }

-dontwarn com.wireguard.**


# ================================================================
# 6. KOTLIN / COROUTINES / COMPOSE
# ================================================================

-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

-keepclassmembers class **$WhenMappings { <fields>; }

-keep class kotlin.Metadata { *; }


# ================================================================
# 7. GECKOVIEW
# ================================================================
#
# À conserver si GeckoBrowserActivity utilise GeckoView :
# la lib passe elle aussi par des callbacks natifs.

-keep class org.mozilla.geckoview.** { *; }
-dontwarn org.mozilla.geckoview.**


# ================================================================
# 8. TRACES D'ERREUR LISIBLES
# ================================================================
#
# Sans ces lignes, les crashs remontés par les testeurs sont
# inexploitables. Pense à archiver le mapping.txt de CHAQUE
# build publié (app/build/outputs/mapping/release/mapping.txt).

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile


# ================================================================
# 9. SUPPRESSION DES LOGS EN RELEASE
# ================================================================
#
# Complément — et non remplacement — du wrapper DebugLog.
#
# Ne fonctionne QUE si proguard-android-optimize.txt est utilisé
# comme fichier de base (l'optimisation doit être activée).
#
# Attention : R8 supprime l'appel, mais PAS le calcul de ses
# arguments. Une concaténation coûteuse reste exécutée.
# D'où l'intérêt de passer par DebugLog.

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}