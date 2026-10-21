# Rapport A3 — Téléportation de pose d'un sub-level portant un joueur
Statut : PARTIEL jusqu'à 300 blocs (balayage de ~6 images, joueur conservé), ÉCHEC à 1000 blocs ; quelques cas de la série `batch` à confirmer
Versions : Minecraft 1.21.1, NeoForge 21.1.262, Sable 2.0.6, Madnessverse 1.0.0 ; intervention manuelle : l'utilisateur (client), avec journal client

| Essai | Résultat mesuré |
| --- | --- |
| tp 10 blocs, Mini immobile | le joueur reste sur la Mini (écart final 0) ; mais la Mini glisse sur ~6 images (~1 tick) pendant que le joueur saute tout de suite de 10 blocs : décalage jusqu'à 9 blocs pendant ~6 images (PARTIEL) |
| tp 1000 blocs | ÉCHEC : le joueur reste en arrière, tombe dans le vide ; gel du client de 26 s puis 118 s (images les plus longues) |
| tp 2000 / 5000 blocs | non exploitable (le joueur avait déjà été perdu) |
| move 5 | RÉUSSI selon l'utilisateur |
| batch 20 20 10 | plantage du serveur (ConcurrentModificationException dans mon code de test), pas de résultat |

Observations :
- Côté client, Sable interpole la pose de la Mini sur ~6 images après un saut ; le joueur, lui, est déplacé d'un coup : une image de décalage suffirait au plan, ici 6.
- Pour un saut de 1000 blocs le joueur n'est plus porté : cause non établie (piste : l'entité n'est portée que sur de courtes distances, ou la zone d'arrivée n'est pas chargée côté joueur). Gel du client de plusieurs dizaines de secondes à investiguer.
- La Mini était sombre en plein jour : le type de dimension avait `has_skylight=false`. Corrigé (`true`).
- Pistes à documenter, sans les implémenter : déplacer le joueur avec la Mini dans la même opération, ou fondu visuel, ou téléporter par petites étapes.
Pièces jointes : server-a3.log, client-frames.csv, scripts/analyse_a3.py

## Seconde série (après corrections : dimension éclairée, file d'actions corrigée)
| Essai | Résultat mesuré (client-frames.csv, scripts/analyse_a3.py) |
| --- | --- |
| tp 10, 100, 300 blocs | PARTIEL : joueur toujours sur la Mini (écart final 0), Mini interpolée sur 6 images (~60 ms, 1 à 2 ticks) pendant que le joueur saute d'un coup (décalage max 8,6 / 90 / 282 blocs pendant ces images) ; plus longue image 11 à 14 ms |
| tp 1000 | ÉCHEC : écart final 1,3 bloc, gel du client de 10 s |
| batch 20 20 10 (1 par seconde) | 14 sauts PARTIEL (6 images décalées, retour à 0) ; 6 sauts avec un écart final de ~10 blocs (événements 10 à 14 et 21) : à confirmer par l'utilisateur (joueur resté en arrière, ou joueur qui marchait/sautait hors de la Mini ?) |
| batch 10 1 10 (1 par tick) | événements fusionnés par l'analyse, écarts finaux de 4,6 à 10 blocs : non concluant, à confirmer visuellement |
Hypothèse non vérifiée : un joueur en l'air (saut) au moment du saut de pose n'est plus « collé » à la Mini et reste en arrière.
Conclusion pour l'architecture : le déplacement d'une Mini par téléportation de pose est utilisable jusqu'à ~300 blocs avec un balayage visible de ~1 tick ; au-delà il faut un autre mécanisme (déplacement du joueur avec la Mini, ou fondu masquant). La Tampon Spatiale groupée à moins de 5 000 blocs de l'origine n'est donc pas atteignable par un simple saut de pose pour des déplacements de 1000+ blocs.

## Précisions de l'utilisateur
- Série `batch` faite en restant immobile : l'hypothèse « joueur en l'air » est écartée ; les 6 sauts à écart final de ~10 blocs restent inexpliqués (artefact possible de l'analyse, car la fenêtre de 120 images chevauche le saut suivant ; non confirmé visuellement).
- Ressenti : balayage discret (seul le mouvement des nuages est perceptible au moment du saut, de 10 à 300 blocs).
- Échec aux grands déplacements (1000 blocs et plus, avec gel du client de 10 s) : NOTIFIÉ comme limite connue. Une autre solution sera cherchée ultérieurement si le saut de pose dans la Tampon Spatiale s'avère impossible.

## Troisième série : protocole `tp` avec préchargement de la destination et déplacement du joueur
- **Préchargement** : la destination est chargée en 1 à 5 ticks (0,1 à 0,3 s) avant le saut ; l'hypothèse « chunks de destination non chargés » n'explique donc pas l'échec des grands sauts (il restait en échec avec le préchargement).
- **Cause réelle (journal serveur)** : à partir de ~500 blocs Sable ne porte plus le joueur ; sa position devient une position de plot décalée (ex. 20 480 537 / 129,8 / 20 481 037 pour un saut de 500 en X) et il tombe. La Mini, elle, arrive toujours à la pose demandée (12585, 12885, 13385… mesurées). Jusqu'à 300 blocs Sable porte le joueur (série 2).
- **Contournement validé par l'utilisateur** : le mod déplace lui-même le joueur du même vecteur ; l'utilisateur confirme que `tp` fonctionne alors (500, 1000, 3000 blocs, aller-retour de 3000 : écart final 0, plus longue image 20 à 62 ms).
- **Défaut observé : flash blanc** de quelques images pendant lesquelles la Mini n'est pas visible (signalé par l'utilisateur). Le journal client montre des images de 61 à 62 ms (au lieu de ~10 ms) aux sauts de 500 et 1000 blocs. À investiguer : chargement des sections de rendu de la Mini côté client après le saut, ou fond de dimension.
- **Régression de ma première correction** : le contrôle de position un tick après le saut rejouait la correction aussi pour les sauts ≤ 300 blocs, que Sable porte avec un tick de retard ; le joueur était donc déplacé deux fois (écart final de 300 ou 30 blocs sur les événements 1, 6 à 9 du journal). Corrigé : déplacement par le mod seulement à partir de 400 blocs (seuil réglable `-Dspacetest.carry.threshold`), contrôle à 4 ticks. À revalider.
- **Seuil exact** non établi (entre 300 et 500).
- **tps (sérialisation)** : fonctionne pour les sauts testés (10 000 blocs inclus) avec le joueur déplacé du même vecteur ; 12 à 30 ms par saut ; analyse visuelle non faite séparément.

## Quatrième série et analyse corrigée (retour de l'utilisateur : « < 300 parfait ; > 300 fonctionnel, frame vide toujours visible »)
- L'ancienne analyse concluait à tort ÉCHEC avec un « écart final » égal à la distance : la fenêtre se fermait pendant que la Mini restait immobile 7 images avant d'être interpolée. Script corrigé (fin d'événement après 15 images immobiles, déclenchement par le joueur ou la Mini). Avec le journal de cette série, l'écart final est 0 pour tous les sauts.
- **Sauts de 30, 100 et 300 blocs** : 5 à 6 images décalées (~60 ms), la Mini glisse sur ~5 images pendant que le joueur est déjà à l'arrivée ; ressenti « parfait » (PARTIEL au sens du plan).
- **Sauts de 500, 1000 et 3000 blocs (joueur déplacé par le mod)** : 12 images décalées (~100 ms) : le joueur est à l'arrivée mais la Mini est encore à l'ancienne pose pendant 7 images (indicateur « suivi » à 0), puis glisse pendant 5 images. C'est la « frame vide » : le joueur voit le vide pendant ~100 ms. Écart final 0 ; images les plus longues 12 à 20 ms (plus de gel).
- Cause probable : l'interpolation côté client de la pose de la Mini démarre environ 7 images après la téléportation du joueur. Pistes (non implémentées) : retarder de quelques ticks le déplacement du joueur pour le synchroniser avec la Mini ; déplacer la Mini par sérialisation (`tps`, la Mini est rechargée sans interpolation) ; fondu au noir/blanc pendant le saut ; masquer l'interpolation (supprimer la dernière pose côté client).
- Correctif de contrôle : en dessous de 400 blocs, plus de contrôle ni de déplacement par le mod (Sable porte le joueur avec plus de 4 ticks de retard) ; au-dessus, déplacement par le mod au même tick et contrôle à 4 ticks.
- Statut A3 : PARTIEL jusqu'à 300 blocs ; FONCTIONNEL avec défaut visuel (~100 ms d'image vide) au-delà de 300 blocs avec le contournement du mod ; la limite de 300 blocs de Sable est établie. Comparaison `tps` (sans interpolation) à faire avant de trancher.

## Cinquième série (version à contrôle limité aux grands sauts) : DÉGRADATION, version précédente restaurée
Retour de l'utilisateur : « TP et TPS sont totalement cassés à plus de 300 blocs ; par moments on est téléporté en avance ou en retard, parfois on glitche, puis on est éjecté de la structure. » Le code précédent (déplacement du joueur par le mod dès 400 blocs, contrôle et correction à +4 ticks pour toutes les distances) a été restauré dans `A3Server.teleport` ; le code de `tps` n'a pas été modifié.
Journaux (server-a3.log et analyse client, 48 événements) :
- **Sable porte parfois le joueur après que le mod l'a déjà déplacé** : plusieurs sauts de 500 et 1000 blocs montrent au contrôle +4 ticks une position brute à +500 ou +1000 de la position attendue (22795 au lieu de 22295, 29295 au lieu de 28295, 31295 au lieu de 30295), signe d'un double déplacement ; le contrôle corrige alors vers la position attendue, ce qui produit une téléportation visible en retard. Sur d'autres sauts identiques, Sable ne porte pas le joueur et le contrôle conclut « bonne position ». Le comportement dépend de l'état de suivi du joueur au moment du saut et n'est pas déterministe : cause non établie.
- Deux commandes `tp` lancées au même tick (tick 1712, deux lignes TELEPORT consécutives de 1000 blocs) produisent deux déplacements et deux corrections contradictoires (28295 puis 29295) : le protocole n'est pas protégé contre les sauts simultanés.
- Série de huit sauts de 500 blocs en rafale (ticks 1390 à 1561) : tous corrects côté serveur (écart 0), mais l'analyse client compte 15 à 17 images décalées par saut (environ 150 ms d'image vide).
- Images longues côté client : 115 à 122 ms sur des sauts de 500 blocs (événements 3 et 11), 903 ms et 2 394 ms sur des sauts de 1000 blocs (événements 47 et 48, serveur occupé par des sérialisations) et une image de 7 354 ms (événement 44).
- **TPS (sérialisation)** : deux sauts de 1000 blocs ont pris 890 ms et 2 379 ms sur le fil serveur (contre 12 à 30 ms lors des essais précédents, dont 10 000 blocs en 30 ms). Cause non établie (hypothèse : génération de chunks à la destination du joueur sans préchargement). `tps` n'a pas de préchargement.
- Événements de l'analyse sans déplacement de la Mini (0 bloc, 40 à 240 images décalées, écart final jusqu'à 129 blocs) : ce sont les périodes où le joueur a été éjecté ou a marché hors de la Mini après un saut (l'utilisateur signale des éjections de la structure) ; à ne pas confondre avec des sauts.
Conclusion : A3 reste PARTIEL. Fiable jusqu'à 300 blocs (Sable porte le joueur, ~6 images de décalage). Au-delà, la combinaison « Sable + déplacement par le mod » n'est pas fiable (doubles déplacements, éjections, images vides de 100 à 150 ms) et `tps` est instable en durée. Le saut de pose dans la Tampon Spatiale ne peut pas être retenu tel quel au-delà de 300 blocs ; une autre solution sera cherchée ultérieurement, comme indiqué par l'utilisateur.


## Non testé / reste à faire (état au 20/10/2026)
- Sauts > 300 blocs : l'image vide de 100 à 150 ms reste non résolue ; comparaison avec `tps` (sérialisation, sans interpolation) non conclue ; seuil exact de Sable entre 300 et 500 blocs non établi.
- Cas simultanés (deux sauts au même tick) : protocole non protégé, non corrigé.
- Quelques cas de la série `batch` à confirmer par rejeu [MANUEL-CLIENT].
- Génération de chunks à destination (hypothèse des sauts de 890 ms à 2,4 s) : non vérifiée avec préchargement.
