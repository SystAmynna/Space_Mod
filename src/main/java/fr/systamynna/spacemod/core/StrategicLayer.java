package fr.systamynna.spacemod.core;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    private Map<UUID, SpaceObject> loaded;
    /**
     * Tous les objets spatiaux qui ont besoin d'être sauvegardés
     */
    private Set<UUID> dirty;



}
