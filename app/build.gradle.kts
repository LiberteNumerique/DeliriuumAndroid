import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/*
 * ================================================================
 * SIGNATURE DE RELEASE
 * ================================================================
 *
 * Les identifiants du keystore sont lus depuis un fichier
 * keystore.properties placé À LA RACINE DU PROJET (à côté de
 * settings.gradle.kts), et JAMAIS committé.
 *
 * Contenu attendu :
 *
 *   storeFile=/chemin/absolu/vers/deliriuum-release.jks
 *   storePassword=...
 *   keyAlias=deliriuum
 *   keyPassword=...
 *
 * Ajoute immédiatement à ton .gitignore :
 *
 *   keystore.properties
 *   *.jks
 *   *.keystore
 *
 * Si le fichier est absent (machine de CI, clone frais), le build
 * release reste possible mais produit un APK non signé — au lieu
 * de planter avec une erreur incompréhensible.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(keystorePropertiesFile.inputStream())
    }
}
val hasKeystore = keystorePropertiesFile.exists()

android {
    namespace = "com.deliriuum.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.deliriuum.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        debug {
            /*
             * Suffixe d'applicationId : permet d'avoir la version
             * debug ET la version release installées côté à côte
             * sur le même téléphone.
             *
             * Indispensable pour reproduire un bug qui n'apparaît
             * qu'en release sans désinstaller à chaque fois.
             */
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        // Désactivé pour éviter le bug de 'Record desugaring'
        isCoreLibraryDesugaringEnabled = false

        // Passage en Java 17 pour supporter nativement les structures modernes de WireGuard
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true

        /*
         * AJOUT REQUIS.
         *
         * Depuis AGP 8, la génération de BuildConfig est
         * désactivée par défaut. Sans cette ligne, DebugLog
         * ne compile pas : BuildConfig.DEBUG n'existe pas.
         */
        buildConfig = true
    }

    packaging {
        resources {
            /*
             * Évite les conflits de fichiers META-INF dupliqués
             * entre GeckoView, OkHttp et les libs Kotlin.
             */
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // --- Dépendances d'origine (gérées par la BOM) ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // --- NOS AJOUTS POUR DELIRIUUM ---

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    /*
     * Cycle de vie.
     *
     * lifecycle-runtime-compose fournit LocalLifecycleOwner et
     * LifecycleEventObserver côté Compose : c'est ce dont
     * AccountScreen a besoin pour rafraîchir le profil sur
     * ON_RESUME. Il n'était pas déclaré.
     *
     * Les trois artefacts sont alignés sur la même version.
     */
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    /*
     * Réseau.
     *
     * Retrofit et converter-gson étaient déclarés DEUX FOIS
     * (2.9.0 puis 2.11.0). Gradle résolvait au plus haut, donc
     * le build utilisait bien 2.11.0 — mais une future mise à
     * jour faite sur la mauvaise ligne n'aurait eu aucun effet.
     * Une seule déclaration désormais.
     */
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    /*
     * Stockage sécurisé (équivalent Keychain iOS).
     *
     * Était également déclaré deux fois.
     */
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // WireGuard (le moteur de l'application)
    implementation("com.wireguard.android:tunnel:1.0.20260102")

    // Navigateur intégré
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("org.mozilla.geckoview:geckoview:147.0.20260212191108")

    // --- Tests ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}