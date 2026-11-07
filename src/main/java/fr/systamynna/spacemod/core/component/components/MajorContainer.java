package fr.systamynna.spacemod.core.component.components;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.SpaceTree;
import fr.systamynna.spacemod.core.component.SpaceComponent;
import fr.systamynna.spacemod.core.render.RenderMath;
import fr.systamynna.spacemod.core.render.RenderMath.Pose;
import net.minecraft.nbt.CompoundTag;

/** Objet qui contient des objets majeurs (une galaxie). Il en liste tout le sous-arbre, par {@link #collect}, pour répliquer les étoiles lointaines. */
public class MajorContainer implements SpaceComponent {
    public static final String ID = "major_container";

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

    /** Un objet majeur : position en km dans le repère du conteneur. */
    public record Entry(UUID uuid, double x, double y, double z, int color, float lum) {}

    /**
     * Tous les {@link MajorObject} du sous-arbre de {@code self}, positionnés dans son repère (km). Ne charge rien ({@code tree.children}).
     * On ne descend pas sous un objet majeur : son sous-arbre est son système local.
     * shortcut: poses lues telles que stockées (non avancées au temps courant), négligeable à l'échelle des années-lumière.
     */
    public static List<Entry> collect(SpaceTree tree, SpaceObject self) {
        List<Entry> out = new ArrayList<>();
        walk(tree, self, Pose.identity(), out);
        return out;
    }

    private static void walk(SpaceTree tree, SpaceObject o, Pose acc, List<Entry> out) {
        for (SpaceObject c : tree.children(o)) {
            Pose p = RenderMath.local(c).then(acc);
            var major = c.getComponent(MajorObject.class);
            if (major.isPresent()) out.add(new Entry(c.getUuid(), p.t().x, p.t().y, p.t().z, major.get().color, (float) major.get().luminosity));
            else walk(tree, c, p, out);
        }
    }

    public static MajorContainer load(CompoundTag t) { return new MajorContainer(); }
}
