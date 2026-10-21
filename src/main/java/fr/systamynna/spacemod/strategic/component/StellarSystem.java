package fr.systamynna.spacemod.strategic.component;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Composant qui fait d'un objet un système stellaire et désigne son astre principal. L'objet doit aussi porter
 * {@link Frame} (c'est ce que fait {@code Archetypes.system}) : la couche suppose que les systèmes sont résidents.
 * Ce n'est qu'une donnée sur un {@code SpaceObject}, pas un conteneur : les objets du système sont ses descendants dans
 * l'arbre de {@code StrategicLayer}. Un système est l'unité de chargement : ses corps sont lus et déchargés ensemble
 * (voir {@code StrategicLayer.systemOf}).
 *
 * @param mainStar identifiant de l'astre principal du système, ou {@code null} tant qu'il n'est pas désigné
 */
public record StellarSystem(@Nullable UUID mainStar) implements Component {
    /** Identifiant du type de ce composant dans le registre {@link ComponentTypes}. */
    @Override
    public ResourceLocation typeId() {
        return ComponentTypes.STELLAR_SYSTEM.id();
    }

    /** Écrit le composant en NBT ; relu à l'identique par {@link #load}. */
    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (mainStar != null) tag.putUUID("main_star", mainStar);
        return tag;
    }

    /** Relit un composant écrit par {@link #save()}. */
    static StellarSystem load(CompoundTag tag) {
        return new StellarSystem(tag.hasUUID("main_star") ? tag.getUUID("main_star") : null);
    }
}
