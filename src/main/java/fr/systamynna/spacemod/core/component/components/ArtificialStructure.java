package fr.systamynna.spacemod.core.component.components;

import java.util.List;

import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.component.SpaceComponent;
import net.minecraft.nbt.CompoundTag;

/** Structure artificielle (vaisseau, station) : tag sans logique. Elle n'est pas un objet notable : elle et son sous-arbre sont ignorés pour la distance de l'objet le plus éloigné d'un système stellaire (voir {@code RenderMath.farthestFrom}). */
public class ArtificialStructure implements SpaceComponent {
    public static final String ID = "artificial_structure";

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

    public static ArtificialStructure load(CompoundTag t) { return new ArtificialStructure(); }
}
