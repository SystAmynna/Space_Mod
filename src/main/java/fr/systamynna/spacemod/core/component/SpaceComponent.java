package fr.systamynna.spacemod.core.component;

import java.util.List;

import net.minecraft.nbt.CompoundTag;

/** Composant attachable à un SpaceObject (un seul par type). */
public interface SpaceComponent {
    /** Clé de persistance, unique par type de composant. */
    String id();

    CompoundTag save();

    /** État lisible, pour {@code /spacecomponent list full}. */
    String describe();

    /** Applique une édition ({@code /spacecomponent edit}) ; syntaxe invalide → {@code SpaceObjectException("amynnaspacemod.component.usage", usage)}. */
    void edit(String args);

    /** Complétions du dernier mot de {@code args} (ce qui a été tapé après le nom du composant). */
    List<String> suggest(String args);
}
