package fr.systamynna.spacemod.strategic.component;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * Donnée de rendu côté ciel : une pile de couches (planète, anneau, atmosphère…) dessinées dans l'ordre, et le rayon
 * apparent. Aucune logique de rendu ici : chaque couche est résolue côté client par son type.
 *
 * @param apparentRadius rayon apparent de l'objet, dans l'unité du parent ; sert au seuil de visibilité
 * @param layers couches dessinées dans l'ordre (la première au fond)
 */
public record Render(double apparentRadius, List<Layer> layers) implements Component {
    /**
     * Une couche de rendu.
     *
     * @param type type de {@code RenderObject} client qui la dessine
     * @param params paramètres propres au type (texture, échelle…)
     */
    public record Layer(ResourceLocation type, CompoundTag params) {}

    public Render {
        layers = List.copyOf(layers);
    }

    /** Raccourci : une couche unique de ce type, sans paramètre. */
    public Render(ResourceLocation type, double apparentRadius) {
        this(apparentRadius, List.of(new Layer(type, new CompoundTag())));
    }

    /** Identifiant du type de ce composant dans le registre {@link ComponentTypes}. */
    @Override
    public ResourceLocation typeId() {
        return ComponentTypes.RENDER.id();
    }

    /** Écrit le composant en NBT ; relu à l'identique par {@link #load}. */
    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("apparent_radius", apparentRadius);
        ListTag list = new ListTag();
        for (Layer layer : layers) {
            CompoundTag l = layer.params().copy();
            l.putString("type", layer.type().toString());
            list.add(l);
        }
        tag.put("layers", list);
        return tag;
    }

    /** Relit un composant écrit par {@link #save()} ; l'ancien format ({@code model}) donne une couche unique. */
    static Render load(CompoundTag tag) {
        double radius = tag.getDouble("apparent_radius");
        if (!tag.contains("layers")) return new Render(ResourceLocation.parse(tag.getString("model")), radius);
        List<Layer> layers = new ArrayList<>();
        for (Tag t : tag.getList("layers", Tag.TAG_COMPOUND)) {
            CompoundTag params = ((CompoundTag) t).copy();
            ResourceLocation type = ResourceLocation.parse(params.getString("type"));
            params.remove("type");
            layers.add(new Layer(type, params));
        }
        return new Render(radius, layers);
    }
}
