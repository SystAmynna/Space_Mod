package fr.systamynna.spacemod.core.store;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

import fr.systamynna.spacemod.core.SpaceObject;

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

    void delete(UUID uuid);

    @Override
    void close();
}
