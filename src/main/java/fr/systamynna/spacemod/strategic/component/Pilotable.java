package fr.systamynna.spacemod.strategic.component;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Déplacé par un pilote : la position vient du {@link Transform} stocké, modifié par le jeu. La vitesse est exprimée
 * dans le référentiel du parent ; elle sert à extrapoler côté client entre deux instantanés.
 *
 * @param vx vitesse en X, en unités du parent par tick
 * @param vy vitesse en Y
 * @param vz vitesse en Z
 */
public record Pilotable(double vx, double vy, double vz) implements Controller {
    /** Pilotable à l'arrêt. */
    public static final Pilotable AT_REST = new Pilotable(0, 0, 0);

    /** Identifiant du type : {@link ControllerTypes#PILOTABLE}. */
    @Override
    public ResourceLocation typeId() {
        return ControllerTypes.PILOTABLE;
    }

    /** La position vient du jeu, pas du temps : le Transform stocké est renvoyé tel quel. */
    @Override
    public Transform resolve(Transform stored, double spaceTime) {
        return stored;
    }

    /** Écrit la vitesse en NBT. */
    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("vx", vx);
        tag.putDouble("vy", vy);
        tag.putDouble("vz", vz);
        return tag;
    }

    /** Relit un contrôleur écrit par {@link #save()}. */
    static Pilotable load(CompoundTag tag) {
        return new Pilotable(tag.getDouble("vx"), tag.getDouble("vy"), tag.getDouble("vz"));
    }
}
