package fr.systamynna.spacemod.strategic.component;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * Sites d'atterrissage et d'amarrage de l'objet, dans son repère local.
 *
 * @param ports les sites, copiés de façon immuable à la construction
 */
public record PortsPoints(List<Port> ports) implements Component {
    /** Copie défensive : la liste reçue ne peut plus être modifiée de l'extérieur. */
    public PortsPoints {
        ports = List.copyOf(ports);
    }

    /**
     * Un site d'atterrissage ou d'amarrage, dans le repère local de l'objet.
     *
     * @param name nom du site, unique dans l'objet
     * @param x position du site, dans le repère local de l'objet
     * @param y idem
     * @param z idem
     * @param openingDegrees ouverture d'approche, 360 pour un port omnidirectionnel
     * @param approachCapacity rayon circonscrit maximal d'un objet admis à l'approche, 0 si aucune approche
     */
    public record Port(String name, double x, double y, double z, double openingDegrees, double approachCapacity) {
        /** Écrit le site en NBT. */
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("name", name);
            tag.putDouble("x", x);
            tag.putDouble("y", y);
            tag.putDouble("z", z);
            tag.putDouble("opening", openingDegrees);
            tag.putDouble("capacity", approachCapacity);
            return tag;
        }

        /** Relit un site écrit par {@link #save()}. */
        static Port load(CompoundTag tag) {
            return new Port(tag.getString("name"), tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"),
                    tag.getDouble("opening"), tag.getDouble("capacity"));
        }
    }

    /** Identifiant du type de ce composant dans le registre {@link ComponentTypes}. */
    @Override
    public ResourceLocation typeId() {
        return ComponentTypes.PORTS_POINTS.id();
    }

    /** Écrit la liste des sites en NBT. */
    @Override
    public CompoundTag save() {
        ListTag list = new ListTag();
        for (Port port : ports) list.add(port.save());
        CompoundTag tag = new CompoundTag();
        tag.put("ports", list);
        return tag;
    }

    /** Relit un composant écrit par {@link #save()}. */
    static PortsPoints load(CompoundTag tag) {
        ListTag list = tag.getList("ports", Tag.TAG_COMPOUND);
        return new PortsPoints(list.stream().map(t -> Port.load((CompoundTag) t)).toList());
    }
}
