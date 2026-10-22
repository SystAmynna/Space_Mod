# fr.systamynna.dynamicdimensions

Fork intégré, dans son propre source set Gradle `src/dyndim` (compilé à part, même mod et même jar que `src/main`, ne peut pas voir `fr.systamynna.spacemod`), de [TeamGalacticraft/DynamicDimensions](https://github.com/TeamGalacticraft/DynamicDimensions) (licence MIT, voir `LICENSE-DynamicDimensions`), branche `minecraft/1.21`, commit `ecfbdf6`. Création, chargement, déchargement et suppression de dimensions pendant que le serveur tourne. **Cette bibliothèque est séparée du code du mod (`fr.systamynna.spacemod`) : le mod n'importe que `api/`.** Seul `AmynnaSpaceMod` appelle `impl.DynamicDimensionsInit`.

## Modifications par rapport à l'amont
- Une seule plateforme (NeoForge 21.1.262, Minecraft 1.21.1) : plus de modules Fabric/common, plus de `ServiceLoader` (`impl/platform/Services` instancie `ForgePlatformHelper`), plus de config, de commandes `/dynamicdimensions` ni de gametests.
- Réseau : charges utiles NeoForge natives (`impl/network/S2CPackets`) à la place de Bad Packets ; identifiants `amynnaspacemod:create_dimension` et `amynnaspacemod:remove_dimension`.
- `MinecraftServerMixin` : `markWorldsDirty()` après ajout et retrait d'un niveau (NeoForge met en cache le tableau des niveaux à ticker, sinon une dimension créée après le démarrage n'est jamais ticquée) ; enregistrement d'un niveau immédiat, même pendant `tickChildren`.
- `PlayerRemover.REFUSE_IF_OCCUPIED` : `unload`/`delete` renvoient false tant qu'un joueur est dans la dimension, revérifié au moment (différé d'un tick) du retrait effectif. Règle du mod : une dimension occupée n'est jamais retirée.
- `api/event/PlayerLoginDimensionEvent` + `PlayerListMixin` : annonce, pendant le login et avant la synchronisation des registres du client, la dimension sauvegardée du joueur, pour la charger avant son arrivée (sinon NeoForge le replace dans l'Overworld à ses coordonnées d'origine).
- Compat Sable 2.0.6 : constructeur `DimensionPhysics` à 8 paramètres, détection de Sable par `FMLLoader.getLoadingModList()` (`ModList.get()` est null au chargement des mixins). Sable reste facultatif à l'exécution.
- Ne mémorise pas les dimensions d'un redémarrage à l'autre : à l'appelant de les recharger (`loadDynamicDimension`). `createDynamicDimension` efface les données existantes.
- Le type de dimension est enregistré sous le même identifiant que la dimension : cet identifiant ne doit pas déjà être pris par un type de datapack. Une instance `DimensionType` neuve par dimension.

Mixins : `amynnaspacemod.dyndim.mixins.json` et `amynnaspacemod.dyndim.sable.mixins.json` (dans `src/main/resources`).

Note : le message de `PlayerRemover.DEFAULT` utilise la clé de langue `amynnaspacemod.dimension.removed`, fournie par `src/main/resources/assets/amynnaspacemod/lang/en_us.json` (deux dossiers de ressources du même mod ne peuvent pas porter le même fichier).
