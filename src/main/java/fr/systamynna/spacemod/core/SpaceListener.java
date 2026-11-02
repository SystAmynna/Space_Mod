package fr.systamynna.spacemod.core;

import java.util.UUID;

/** Observateur des changements de la couche stratégique (thread serveur). */
public interface SpaceListener {
    SpaceListener NONE = new SpaceListener() {};

    /** Objet loadé, créé ou modifié (état à jour, déjà persisté). */
    default void updated(SpaceObject o) {}

    /** Objet retiré de la mémoire (unload), il existe toujours dans le store. */
    default void dropped(UUID uuid) {}

    /** Objet supprimé définitivement. */
    default void removed(UUID uuid) {}
}
