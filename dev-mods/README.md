Déposez ici des jars (ajoutez `.disabled` au nom pour désactiver) de mods NeoForge 1.21.1 à charger en plus du mod pour tester.
Lancer avec `./gradlew runClientDevMods` (ou `runServerDevMods`).

Note : DynamicDimensions est maintenant intégré au mod (`src/dyndim/java/fr/systamynna/dynamicdimensions`, voir son README) : ne pas y déposer de jar DynamicDimensions. Les tests utilisent cette bibliothèque par défaut ; pour comparer avec Madnessverse, renommer `madnessverse-neoforge-1.0.0.jar.disabled` en `.jar` et lancer avec `-Pbackend=mv`.
