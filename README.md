# Deliriuum — Client Android

Client Android de Deliriuum : tunnel VPN WireGuard et navigateur intégré
avec réduction d'empreinte numérique (Deep Shield).

Un projet de l'**Alliance pour les Libertés Numériques**.

---

## Ce que fait l'application

- **Tunnel VPN WireGuard** — chiffrement du trafic via un nœud Deliriuum,
  avec surveillance active du chemin réseau.
- **Navigateur intégré (GeckoView)** — navigation dans l'application,
  avec réduction de plusieurs sources d'empreinte numérique.
- **Audit de confidentialité** — mesure locale de ce qu'une page Web peut
  réellement observer, sans score synthétique.

## Ce que fait l'application — et ce qu'elle ne fait pas

La protection Deep Shield **concerne la navigation effectuée dans
Deliriuum**. Les applications installées séparément sur le téléphone
passent par le tunnel VPN, mais suivent leurs propres règles pour tout
le reste.

L'audit de confidentialité affiche des résultats mesurés, y compris
lorsqu'ils sont mauvais. Une caractéristique marquée « observable » l'est
réellement.

## Portée de ce dépôt

Ce dépôt contient **uniquement le client Android**.

Le serveur d'orchestration (`master.deliriuum.com`) — attribution
d'adresses, gestion des sessions, comptes utilisateurs — n'est pas publié
ici. Un audit de ce dépôt permet donc de vérifier ce que fait
l'application sur l'appareil, mais pas ce que fait l'infrastructure des
données de connexion.

Cette limite est assumée et signalée plutôt que passée sous silence.

---

## Architecture

```
com.deliriuum.app
├── data/
│   ├── APIClient.kt          Retrofit + Gson, refresh automatique des tokens
│   ├── AuthManager.kt        Session utilisateur, cache profil hors ligne
│   ├── TunnelManager.kt      WireGuard, watchdog réseau, cycle de vie session
│   ├── KeychainStore.kt      Stockage chiffré des tokens
│   └── PrivacyAuditManager   Audit Deep Shield
├── ui/
│   ├── screens/              Écrans Compose
│   ├── components/           Composants partagés
│   └── theme/
└── util/
```

### Points d'architecture notables

**Renouvellement des tokens** — `APIClient.executeCall()` intercepte les
401, renouvelle la paire access/refresh, et rejoue la requête une seule
fois. Une panne réseau ne supprime jamais les tokens : elle est distinguée
d'une session réellement expirée.

**Rafraîchissement du profil** — `AuthManager.refreshProfile()` n'est
volontairement pas `suspend`. Elle délègue à un scope propre au singleton,
de sorte qu'un écran qui quitte la composition n'annule pas la requête en
vol.

**Watchdog du tunnel** — `status == CONNECTED` ne suffit pas à déclarer la
navigation protégée. WireGuard peut rester techniquement monté alors que le
chemin réseau ne répond plus. `isProtected` exige les deux conditions, et le
watchdog vérifie périodiquement l'accessibilité réelle.

**Identité WireGuard** — la paire de clés locale est liée à la session
d'authentification, pas à l'installation. Une déconnexion détruit la clé et
supprime le Device côté serveur ; le login suivant en génère une nouvelle.

---

## Compilation

### Prérequis

- Android Studio (version récente)
- JDK 17
- `minSdk` 26 · `targetSdk` 36

### Build de développement

```bash
./gradlew assembleDebug
```

### Build de production

Le build release active R8. Il nécessite un keystore, déclaré dans un
fichier `keystore.properties` à la racine du projet — **jamais committé** :

```properties
storeFile=/chemin/absolu/vers/votre.jks
storePassword=...
keyAlias=...
keyPassword=...
```

```bash
./gradlew assembleRelease
```

Sans ce fichier, le build produit un APK non signé plutôt que d'échouer.

### Avertissement sur R8

Plusieurs éléments du projet dépendent de règles explicites dans
`app/proguard-rules.pro` :

- **Modèles Gson** — les champs sans annotation `@SerializedName` seraient
  renommés par R8, et Gson les laisserait à `null` sans erreur.
- **Callbacks WireGuard** — `onStateChange()` est appelé depuis le code
  natif via JNI. R8 ne voit aucun appelant Java et le supprimerait, laissant
  l'interface affirmer qu'un tunnel tombé est toujours actif.

Ces deux défaillances sont **invisibles en debug**. Tout build release doit
être testé sur un appareil physique avant distribution.

---

## Contribuer

Les contributions sont bienvenues, en particulier :

- audits de sécurité et de confidentialité
- rapports de fuite (DNS, WebRTC, IPv6)
- traductions
- tests sur constructeurs et versions d'Android variés

Les signalements de vulnérabilité peuvent être adressés en privé avant
publication.

---

## Licence

Ce programme est un logiciel libre, distribué sous les termes de la
**GNU General Public License version 3** ou, à votre choix, toute version
ultérieure.

Il est diffusé dans l'espoir qu'il sera utile, mais **SANS AUCUNE
GARANTIE**, sans même la garantie implicite de qualité marchande ou
d'adéquation à un usage particulier. Voir la GNU General Public License
pour plus de détails.

Voir le fichier [LICENSE](LICENSE).

Copyright © 2026 Alliance pour les Libertés Numériques
