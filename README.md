# Studio Mobile v2 — fondation portable

**État : prototype de fondation, pas encore une version de production.** Cette livraison vise un premier socle Android compilable : canevas 2D, import de médias, sauvegarde/ouverture d'un projet `.studio` autonome. L'export vidéo composé et l'export HTML interactif ne sont pas encore implémentés dans cette version ; ils sont documentés comme étapes suivantes et ne doivent pas être présentés comme fonctionnels.

## Architecture
- `app`: interface Android native Kotlin.
- `ProjectDocument`: modèle indépendant de l'interface.
- `.studio`: archive ZIP contenant `manifest.json` et les médias embarqués.
- Import via Storage Access Framework (SAF), sans permission globale de stockage.
- Détection des doublons à l'import par SHA-256 au sein du projet.
- Ouverture contrôlée du manifeste et résolution des médias intégrés.
- GitHub Actions compile l'APK debug et publie l'APK comme artifact.

## Prérequis
- Android Studio récent, JDK 17, Android SDK.
- Android 8.0+ (API 26), testé par compilation CI uniquement lorsqu'un workflow est vert.
- L'APK debug n'est pas signée pour une distribution Play Store.

## Fonctions dans ce socle
- Créer un canevas.
- Importer images et vidéos.
- Afficher et déplacer les éléments sur le canevas.
- Enregistrer un fichier `.studio`.
- Ouvrir un fichier `.studio` sur un autre appareil sans fichiers source.
- Afficher une progression de l'import, de la sauvegarde et de l'ouverture.

## Limites connues
- Les vidéos sont représentées par une vignette de remplacement dans le canevas ; la lecture interactive complète est à ajouter.
- L'export MP4 avec composition de plusieurs éléments n'est pas livré dans cette première fondation.
- L'export HTML interactif n'est pas livré.
- Pas encore de bibliothèque globale partagée entre projets, historique de versions ou collaboration.
- L'import d'un projet extrait temporairement les médias dans le cache applicatif ; Android peut supprimer ce cache. Le projet `.studio` reste la source durable.
- Le format `.studio` est versionné (`schemaVersion`), mais la migration automatique de versions n'est pas encore implémentée.

## Format `.studio`
Archive ZIP :
- `manifest.json`: dimensions du canevas, liste d'entités, coordonnées normalisées, ordre, type et SHA-256.
- `media/<id>_<nom>`: octets originaux des médias.

Les coordonnées sont normalisées entre 0 et 1, afin de conserver la composition entre tailles d'écran différentes.

## Compiler sur GitHub depuis un téléphone
1. Crée un **nouveau dépôt privé** nommé `StudioMobile-v2`.
2. Télécharge et décompresse le ZIP sur un ordinateur, ou utilise l'interface GitHub web pour ajouter les fichiers et dossiers. L'application GitHub mobile ne permet pas toujours de téléverser un dossier complet ; le navigateur en mode ordinateur peut être plus pratique.
3. Téléverse le contenu du dossier `StudioMobile-v2` à la racine du dépôt, y compris `.github/workflows/build-android.yml`.
4. Ouvre l'onglet **Actions**, active les workflows si GitHub le demande, puis lance `Build Android APK`.
5. Attends le coche vert. Ouvre l'exécution réussie, descends à **Artifacts**, télécharge `StudioMobile-debug-apk`.
6. Décompresse l'archive d'artifact si nécessaire, puis installe l'APK sur Android. Autorise temporairement l'installation depuis cette source si Android le demande.

## Tests minimums avant usage réel
- Importer une image et une vidéo.
- Déplacer les éléments, enregistrer `test.studio`.
- Fermer l'application, puis rouvrir `test.studio`.
- Copier le fichier vers un autre téléphone où les médias sources ne sont pas présents.
- Vérifier les dimensions, positions et le nombre d'entités.
- Ne pas utiliser cette version comme unique copie d'un projet important.

## Justificatif technique
Voir `docs/ARCHITECTURE.md`, `docs/ROADMAP.md` et `docs/CLAUDE_HANDOFF.md`.
