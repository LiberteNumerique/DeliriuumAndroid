DELIRIIUM ANDROID — AUDIT D'INSTALLATION

OBJECTIF
- Launch screen Deliriuum.
- Consentement VPN Deliriuum existant.
- Popup système Android VpnService.prepare().
- Écran "Finalisation de votre installation".
- 1 audit AVANT uniquement, tunnel NON connecté.
- Cache local persistant du snapshot AVANT.
- WelcomeView seulement après succès.
- 1 audit APRÈS uniquement lors de la première protection réelle.
- Cache local persistant du snapshot APRÈS.
- Aucun nouvel audit aux connexions/déconnexions suivantes.

FICHIERS À SUPPRIMER S'ILS EXISTENT
app/src/main/java/com/deliriuum/app/data/PrivacyAuditBootstrap.kt
app/src/main/java/com/deliriuum/app/data/HomePrivacyAuditContract.kt
app/src/main/java/com/deliriuum/app/data/BaselineAuditService.kt
app/src/main/java/com/deliriuum/app/data/ProtectedAuditActivity.kt

IMPORTANT
HomePrivacyAuditContract est déclaré UNE SEULE FOIS dans BaselineAuditActivity.kt.
Il ne faut pas créer de ProtectedAuditActivity : l'audit APRÈS utilise le vrai
DeliriumGeckoRuntime via la GeckoView invisible de HomeView.

COPIE
Depuis la racine du projet Android, extraire/coller le contenu de cette archive
en conservant les chemins app/src/main/...

COMPILATION
./gradlew clean
./gradlew :app:compileDebugKotlin

TEST PREMIÈRE INSTALLATION
Pour simuler une installation neuve sur un appareil de test :
adb shell pm clear com.deliriuum.app.debug

Puis lancer l'application.

FLUX ATTENDU
Launch screen
→ consentement Deliriuum
→ popup système Android VPN
→ Finalisation de votre installation
→ Analyse de référence terminée
→ Welcome

APRÈS PREMIÈRE CONNEXION
La première fois que tunnelManager.isProtected devient true :
→ une seule GeckoView d'audit protégée
→ snapshot APRÈS sauvegardé
→ aucune nouvelle mesure ensuite.

LOGS UTILES
adb logcat -s PrivacyBaseline PrivacyAudit PrivacyAuditCache PrivacyBootstrapUI PrivacyAuditUI
