package fr.systamynna.spacemod.core.store;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import fr.systamynna.spacemod.core.SpaceObject;

/** Persistance des objets spatiaux. Les erreurs d'E/S remontent en {@link java.io.UncheckedIOException}. */
public interface SpaceStore extends AutoCloseable {
    /** Insère ou met à jour les objets, en une transaction. */
    void save(Collection<SpaceObject> objects);

    Optional<SpaceObject> load(UUID uuid);

    List<SpaceObject> loadChildren(UUID parent);

    void delete(UUID uuid);

    @Override
    void close();
}
