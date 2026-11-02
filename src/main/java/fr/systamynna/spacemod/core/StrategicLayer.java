package fr.systamynna.spacemod.core;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import fr.systamynna.spacemod.core.store.SpaceStore;
import fr.systamynna.spacemod.core.store.SpaceStores;

/**
 * Cette classe décrit la couche stratégique du mod, appartient au serveur.
 */
public class StrategicLayer {

    /**
     * UUID de la racine, de l'univers.
     */
    private UUID rootUUID;
    /**
     * Tous les objets spatiaux loadé
     */
    private final Map<UUID, SpaceObject> loaded = new HashMap<>();
    /**
     * Tous les objets spatiaux qui ont besoin d'être sauvegardés
     */
    private final Set<UUID> dirty = new HashSet<>();
    /** Persistance, ouverte pendant la vie du serveur. */
    private SpaceStore store;

    public void start(Path worldDir) {
        store = SpaceStores.open(worldDir);
    }

    /** Écrit les objets modifiés. */
    public void flush() {
        if (store == null || dirty.isEmpty()) return;
        store.save(dirty.stream().map(loaded::get).filter(java.util.Objects::nonNull).toList());
        dirty.clear();
    }

    public void stop() {
        if (store == null) return;
        flush();
        store.close();
        store = null;
    }



}
