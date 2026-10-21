package fr.systamynna.spacemod.strategic.component;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Marqueur : l'objet est un référentiel (galaxie, système stellaire…). Son {@link Transform} est la conversion complète
 * (translation, rotation, échelle) de ses coordonnées locales vers celles de son parent ; l'échelle sert de conversion
 * d'unités entre deux échelles. Un objet sans ce marqueur (corps) ne transmet que sa translation à ses enfants, si bien
 * que la rotation propre d'une planète n'entraîne ni ses lunes ni les vaisseaux en orbite.
 * <p>
 * Un cadre est toujours résident en mémoire ; il ne peut descendre que d'un cadre qui n'est pas un système
 * ({@code StrategicLayer.addObject} et {@code reparent} le vérifient).
 */
public record Frame() implements Component {
    /** Identifiant du type de ce composant dans le registre {@link ComponentTypes}. */
    @Override
    public ResourceLocation typeId() {
        return ComponentTypes.FRAME.id();
    }

    /** Écrit le composant en NBT ; relu à l'identique par {@link #load}. */
    @Override
    public CompoundTag save() {
        return new CompoundTag();
    }

    /** Relit un composant écrit par {@link #save()}. */
    static Frame load(CompoundTag tag) {
        return new Frame();
    }
}
