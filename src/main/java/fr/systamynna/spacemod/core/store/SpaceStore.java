package fr.systamynna.spacemod.core.store;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.sky.SkySummary;

/** Persistance des objets spatiaux. Les erreurs d'E/S remontent en {@link java.io.UncheckedIOException}. */
public interface SpaceStore extends AutoCloseable {
    /** Insère ou met à jour les objets, en une transaction. */
    void save(Collection<SpaceObject> objects);

    Optional<SpaceObject> load(UUID uuid);

    /** Objets sans parent. */
    List<SpaceObject> loadParentless();

    /** UUID du root désigné (stockage global, unique). */
    Optional<UUID> loadRootUuid();

    /** Désigne le root ; {@code null} l'efface. */
    void saveRootUuid(@javax.annotation.Nullable UUID uuid);

    List<SpaceObject> loadChildren(UUID parent);

    /** Objets auxquels le joueur est abonné. */
    Set<UUID> loadSubs(UUID player);

    /** Remplace les abonnements du joueur. */
    void saveSubs(UUID player, Collection<UUID> objects);

    /** Objet désigné comme parent du joueur. */
    Optional<UUID> loadPlayerParent(UUID player);

    /** {@code null} l'efface. */
    void savePlayerParent(UUID player, @javax.annotation.Nullable UUID parent);

    /** Réglage global (ex. le provider) ; stocké avec les métadonnées. */
    Optional<String> loadSetting(String key);

    /** {@code null} l'efface. */
    void saveSetting(String key, @javax.annotation.Nullable String value);

    /**
     * Résumés de ciel des enfants de {@code parent} dont la cellule est dans [minCell, maxCell] (cx, cy, cz inclus) et de luminosité >= {@code minLum}.
     * Sans lire aucun objet.
     */
    List<SkySummary> skyInBox(UUID parent, int[] minCell, int[] maxCell, double minLum);

    void delete(UUID uuid);

    @Override
    void close();
}
