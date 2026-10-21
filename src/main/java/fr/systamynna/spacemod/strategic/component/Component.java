package fr.systamynna.spacemod.strategic.component;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Donnée attachée à un {@code SpaceObject}. Immuable : on remplace un composant, on ne le modifie pas. Interface
 * publique : un autre mod ajoute ses composants en l'implémentant et en les enregistrant dans {@link ComponentTypes}.
 */
public interface Component {
    /** @return l'identifiant du type, clé du composant dans l'objet et dans la base (par exemple {@code amynnaspacemod:transform}) */
    ResourceLocation typeId();

    /**
     * Sérialise le composant.
     *
     * @return le NBT du composant. Doit être relu à l'identique par le lecteur enregistré pour {@link #typeId()}, doubles
     * comprises (bit à bit).
     */
    CompoundTag save();
}
