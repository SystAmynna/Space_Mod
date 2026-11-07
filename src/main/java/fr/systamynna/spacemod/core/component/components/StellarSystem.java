package fr.systamynna.spacemod.core.component.components;

import java.util.List;

import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.component.SpaceComponent;
import net.minecraft.nbt.CompoundTag;

/** Système stellaire (tag, sans logique) : le repère qui contient les astres d'une étoile. Sert de référentiel au rendu des étoiles : un observateur dont un ancêtre (ou lui-même) le porte voit les étoiles de ce système comme locales (voir {@code RenderMath.stellarSystemOf}). */
public class StellarSystem implements SpaceComponent {
    public static final String ID = "stellar_system";

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

    public static StellarSystem load(CompoundTag t) { return new StellarSystem(); }
}
