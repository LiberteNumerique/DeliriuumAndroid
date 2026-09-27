#!/usr/bin/env bash
set -euo pipefail

# À lancer depuis la racine du projet Deliriuum-Android,
# après avoir copié le dossier app/ de l'archive sur le projet.

rm -f app/src/main/java/com/deliriuum/app/data/PrivacyAuditBootstrap.kt
rm -f app/src/main/java/com/deliriuum/app/data/HomePrivacyAuditContract.kt
rm -f app/src/main/java/com/deliriuum/app/data/BaselineAuditService.kt
rm -f app/src/main/java/com/deliriuum/app/data/ProtectedAuditActivity.kt

echo "Déclarations audit restantes :"
grep -RIn --include='*.kt' \
  -E 'object HomePrivacyAuditContract|class BaselineAuditActivity|class ProtectedAuditActivity|class BaselineAuditService' \
  app/src/main/java || true

echo
echo "Compilation :"
./gradlew clean
./gradlew :app:compileDebugKotlin
