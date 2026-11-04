package fr.systamynna.spacemod.core.relevance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.joml.Vector3d;

import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.StrategicLayer;
import fr.systamynna.spacemod.core.render.RenderMath;
import fr.systamynna.spacemod.core.render.SpaceRender;
import fr.systamynna.spacemod.core.render.SpaceRenderObject;

/**
 * Quels objets un joueur doit avoir loadés et répliqués. Fonction pure de l'arbre, de la position du joueur et du temps :
 * l'ancre et ses ancêtres toujours ; pour chaque ancêtre, les enfants proches ou assez gros dans le ciel ; et tous les enfants d'un objet
 * dans la sphère d'influence duquel se trouve l'observateur (le joueur dans un système voit ses planètes).
 * Ne charge rien : les candidats sont lus avec {@code tree().children} (instance loadée ou copie du store dont la pose est avancée au temps voulu).
 */
public final class Relevance {
    private Relevance() {}

    /** Période (ticks) du recalcul par joueur. */
    public static final int PERIOD_TICKS = 20;

    /**
     * Seuils de chargement ; le déchargement d'un objet déjà chargé se fait à {@code hysteresis} fois ces seuils (pas d'aller-retour à la frontière).
     * shortcut: constantes, pas dans le fichier de config du serveur ; à brancher sur un ModConfigSpec quand il faudra les calibrer en jeu.
     *
     * @param thetaLoad  rayon apparent (rad) au-delà duquel un enfant est chargé
     * @param rNearKm    distance (km) en dessous de laquelle un enfant est chargé quelle que soit sa taille
     * @param k          sphère d'influence = k x 1,5 x rayon d'orbite max des enfants
     * @param maxExpand  au-delà de ce nombre d'enfants, un objet n'est jamais « étendu » (une galaxie ne charge pas tous ses systèmes)
     * @param soiMaxKm   un objet plus loin que ça ne peut pas contenir l'observateur : on ne lit pas ses enfants
     */
    public record Params(double thetaLoad, double rNearKm, double k, double hysteresis, int maxExpand, double soiMaxKm) {
        public static final Params DEFAULT = new Params(1e-4, 1e10, 1.0, 1.5, 256, 1e13);
    }

    /**
     * @param ticks temps spatial (ticks de SpaceClock) auquel les poses sont évaluées
     * @param prev  ensemble renvoyé la fois précédente (vide la première fois) : applique l'hystérésis
     * @throws fr.systamynna.spacemod.core.SpaceObjectException si l'ancre n'existe plus
     */
    public static Set<UUID> needed(StrategicLayer layer, PlayerSpacePos pos, double ticks, Set<UUID> prev, Params P) {
        Set<UUID> out = new HashSet<>();
        List<SpaceObject> chain = new ArrayList<>();
        for (SpaceObject c = posed(layer, layer.peek(pos.anchor()), ticks); c != null;
                c = c.getParent() == null ? null : posed(layer, layer.peek(c.getParent()), ticks)) {
            chain.add(c);
            out.add(c.getUuid());
        }
        Vector3d p = new Vector3d(pos.x(), pos.y(), pos.z()); // l'observateur dans le référentiel de a
        for (SpaceObject a : chain) {
            walk(layer, a, p, out, prev, ticks, P);
            var l = RenderMath.local(a);
            l.q().transform(p).add(l.t()); // ... puis dans celui de son parent
        }
        return out;
    }

    /**
     * Ajoute à {@code out} les enfants de {@code a} à charger ; {@code p} = observateur dans le référentiel de {@code a}.
     * Renvoie vrai si l'observateur est dans la sphère d'influence de {@code a} (tous ses enfants sont alors chargés).
     */
    private static boolean walk(StrategicLayer layer, SpaceObject a, Vector3d p, Set<UUID> out, Set<UUID> prev, double ticks, Params P) {
        List<SpaceObject> kids = layer.tree().children(a);
        if (kids.isEmpty()) return false;
        Vector3d[] at = new Vector3d[kids.size()];
        double extent = 0;
        boolean was = true;
        for (int i = 0; i < at.length; i++) {
            SpaceObject c = posed(layer, kids.get(i), ticks);
            at[i] = new Vector3d(RenderMath.local(c).t());
            extent = Math.max(extent, at[i].length());
            was &= prev.contains(c.getUuid());
        }
        // shortcut: étendue = 1,5 x orbite max des enfants à défaut de composant dédié ; à remplacer par un composant « sphère d'influence » si les données l'exigent.
        boolean inside = kids.size() <= P.maxExpand() && p.length() < P.k() * 1.5 * extent * (was ? P.hysteresis() : 1);
        for (int i = 0; i < at.length; i++) {
            SpaceObject c = kids.get(i);
            double d = p.distance(at[i]), f = prev.contains(c.getUuid()) ? P.hysteresis() : 1;
            boolean show = inside || d < P.rNearKm() * f || radiusKm(c) > d * P.thetaLoad() / f;
            // Les ancêtres de l'ancre sont parcourus par needed() ; les autres ne peuvent contenir l'observateur que s'ils sont assez proches.
            if (d < P.soiMaxKm() && !out.contains(c.getUuid())) {
                var l = RenderMath.local(c);
                if (walk(layer, c, l.q().transformInverse(new Vector3d(p).sub(l.t())), out, prev, ticks, P)) show = true;
            }
            if (show) out.add(c.getUuid());
        }
        return inside;
    }

    /** Plus grand rayon de rendu de l'objet (km), 0 sans composant de rendu. shortcut: ignore l'échelle propre de chaque élément de rendu. */
    private static double radiusKm(SpaceObject o) {
        return o.getComponent(SpaceRender.class).map(r -> r.objects.stream().mapToDouble(SpaceRenderObject::radius).max().orElse(0)).orElse(0.0);
    }

    /** Les copies du store ont une pose périmée : on l'avance au temps voulu (les controllers sont des fonctions pures). Une instance loadée est déjà tickée. */
    private static SpaceObject posed(StrategicLayer layer, SpaceObject o, double ticks) {
        if (!layer.isLoaded(o.getUuid())) o.step(ticks);
        return o;
    }
}
