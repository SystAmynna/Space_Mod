package fr.systamynna.spacemod.strategic.component;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Ce qui fait bouger un objet ; {@code null} sur le {@code SpaceObject} = immobile. Interface ouverte : un autre mod
 * ajoute un contrôleur en l'implémentant et en l'enregistrant dans {@link ControllerTypes}. Par défaut,
 * {@link RailFollower} (fonction du temps seul) et {@link Pilotable} (déplacé par le jeu).
 */
public interface Controller {
    /** Identifiant du type de contrôleur dans le registre {@link ControllerTypes}. */
    ResourceLocation typeId();

    /** Sérialise les paramètres du contrôleur (sans son type, ajouté par {@link #write}). */
    CompoundTag save();

    /**
     * Transform local effectif (dans le référentiel du parent) au temps spatial donné, à partir du Transform stocké.
     * Fonction pure du temps : serveur et clients obtiennent le même résultat.
     */
    Transform resolve(Transform stored, double spaceTime);

    /**
     * {@code true} si la position ne dépend que du contrôleur (le Transform stocké est ignoré) : l'objet ne peut alors
     * pas changer de parent sans qu'on lui donne un nouveau contrôleur.
     */
    default boolean determinesPosition() {
        return false;
    }

    /** Écrit un contrôleur sous la forme lue par {@link ControllerTypes#read}. */
    static CompoundTag write(Controller controller) {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", controller.typeId().toString());
        tag.put("data", controller.save());
        return tag;
    }
}
