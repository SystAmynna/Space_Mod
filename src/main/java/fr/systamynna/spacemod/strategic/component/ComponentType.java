package fr.systamynna.spacemod.strategic.component;

import java.util.function.Function;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Identifiant d'un type de composant et son lecteur NBT ; l'égalité est l'identité de l'instance.
 *
 * @param <T> classe des composants de ce type
 */
public final class ComponentType<T extends Component> {
    /** Identifiant du type, unique dans le registre. */
    private final ResourceLocation id;
    /** Fabrique qui reconstruit un composant de ce type depuis son NBT. */
    private final Function<CompoundTag, T> reader;

    // TODO : Erreur commentaire (ligne en dessous)
    /** Réservé à {@link ComponentTypes#register}, qui garantit l'unicité de l'identifiant. */
    ComponentType(ResourceLocation id, Function<CompoundTag, T> reader) {
        this.id = id;
        this.reader = reader;
    }

    /** @return l'identifiant du type */
    public ResourceLocation id() {
        return id;
    }

    /**
     * Reconstruit un composant de ce type depuis son NBT.
     *
     * @param tag données écrites par {@link Component#save()}
     * @return le composant relu
     */
    public T read(CompoundTag tag) {
        return reader.apply(tag);
    }

    /** L'identifiant du type, pour les messages d'erreur et les journaux. */
    @Override
    public String toString() {
        return id.toString();
    }
}
