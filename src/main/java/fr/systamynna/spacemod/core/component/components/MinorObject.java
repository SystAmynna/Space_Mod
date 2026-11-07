package fr.systamynna.spacemod.core.component.components;

import java.util.List;

import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.component.SpaceComponent;
import net.minecraft.nbt.CompoundTag;

/** Objet mineur : planète, lune... d'un système stellaire. Chargé et répliqué d'office tant que son parent majeur l'est (voir {@code Relevance}). */
public class MinorObject implements SpaceComponent {
    public static final String ID = "minor_object";

    @Override
    public String id() { return ID; }

    @Override
    public CompoundTag save() { return new CompoundTag(); }

    @Override
    public String describe() { return "(marqueur)"; }

    @Override
    public void edit(String args) {
        if (!args.isBlank()) throw new SpaceObjectException("amynnaspacemod.component.usage", "(aucun argument)");
    }

    @Override
    public List<String> suggest(String args) { return List.of(); }

    public static MinorObject load(CompoundTag t) { return new MinorObject(); }
}
