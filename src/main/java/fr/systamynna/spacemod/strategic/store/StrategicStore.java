package fr.systamynna.spacemod.strategic.store;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import fr.systamynna.spacemod.strategic.SpaceClock;
import net.minecraft.resources.ResourceLocation;

/**
 * Persistance de la couche stratégique. La couche garde en mémoire les objets résidents et ceux des systèmes chargés ;
 * le magasin sait relire un objet isolé, une unité entière, et les index globaux (dimension → hôte) sans tout charger.
 * Les implémentations ne sont appelées que depuis le thread serveur.
 */
public interface StrategicStore extends AutoCloseable {
    /**
     * Où se trouve un objet : l'unité dont il est un corps, ou {@code null} s'il est résident.
     *
     * @param unitId identifiant de l'unité, ou {@code null}
     */
    record Placement(@Nullable UUID unitId) {}

    /** Valeur d'une métadonnée du monde, ou {@code null} si elle n'existe pas. */
    @Nullable
    String meta(String key);

    /** Écrit une métadonnée du monde (mise à jour ou création), immédiatement. */
    void setMeta(String key, String value);

    /** Horloge sauvée, ou une horloge neuve (temps spatial = temps de jeu) si rien n'a encore été écrit. */
    SpaceClock loadClock();

    /** Objets résidents : cadres et corps hors de tout système. Lus une fois au démarrage. */
    List<StoredObject> loadResident();

    /** Corps d'un système (les objets dont l'unité est {@code unitId}), sans le système lui-même, qui est résident. */
    List<StoredObject> loadUnit(UUID unitId);

    /** Emplacement d'un objet, ou {@code null} s'il n'existe pas dans le magasin. */
    @Nullable
    Placement placementOf(UUID objectId);

    /** Toutes les dimensions hébergées, avec l'identifiant de leur hôte, sans lire les objets. */
    Map<ResourceLocation, UUID> hostedDimensions();

    /**
     * Écrit des objets (mise à jour, ou insertion s'ils sont nouveaux) et, si fournie, l'horloge, dans une seule
     * transaction : tout est écrit ou rien ne l'est.
     *
     * @param clock horloge à écrire, ou {@code null} si elle n'a pas changé
     * @throws StrategicStoreException si l'écriture échoue (par exemple dimension déjà hébergée par un autre objet)
     */
    void save(Collection<StoredObject> objects, @Nullable SpaceClock clock);

    /** Ferme la connexion. Idempotent. */
    @Override
    void close();
}
