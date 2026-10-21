package fr.systamynna.spacemod.strategic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import fr.systamynna.spacemod.strategic.component.ComponentTypes;
import fr.systamynna.spacemod.strategic.component.Controller;
import fr.systamynna.spacemod.strategic.component.RailFollower;
import fr.systamynna.spacemod.strategic.component.Transform;

/**
 * Positions dans l'arbre des objets : fonctions du temps spatial seul, continu (un {@code double} de ticks, donc lisse
 * entre deux ticks : on passe {@code temps + partialTick}). La phase est calculée par {@code floorMod} sur la partie
 * entière, jamais accumulée (une accumulation dérive de 0,91 bloc à 10^9 ticks, voir B1), et les fonctions
 * trigonométriques sont des {@code StrictMath} pour que serveur et clients obtiennent les mêmes bits.
 * <p>
 * Chaque objet est exprimé dans le référentiel de son parent. On ne calcule jamais une position globale pour comparer
 * deux objets proches : {@link #relativePosition} remonte au plus proche ancêtre commun, pour ne pas perdre de précision
 * entre échelles.
 * <p>
 * Toutes les positions sont des tableaux {@code {x, y, z}} de trois doubles, dans l'unité du référentiel considéré.
 * Les fonctions ne modifient ni l'arbre ni les objets : elles sont sûres à appeler depuis n'importe quel thread tant
 * que l'arbre ne change pas pendant l'appel.
 */
public final class RailMath {
    /**
     * Profondeur maximale tolérée d'une chaîne de parents. Au-delà, on suppose un cycle de parents (donnée corrompue)
     * et on échoue plutôt que de boucler indéfiniment.
     */
    private static final int MAX_DEPTH = 64;

    /** Classe utilitaire : uniquement des fonctions statiques. */
    private RailMath() {}

    /**
     * Fraction de période écoulée, dans [0, 1), pour un temps entier.
     *
     * @param ticks temps en ticks (peut être négatif)
     * @param phaseTicks décalage de phase, en ticks
     * @param period période, en ticks, strictement positive
     */
    public static double fraction(long ticks, long phaseTicks, long period) {
        // floorMod (et non %) : le résultat reste dans [0, period) même pour un temps négatif
        return (double) Math.floorMod(ticks + phaseTicks, period) / (double) period;
    }

    /**
     * Fraction de période écoulée, dans [0, 1), pour un temps fractionnaire. La partie entière passe par
     * {@code floorMod} et la partie fractionnaire est ajoutée à la fin : la précision ne dépend donc pas de la taille du
     * temps (un {@code double} perdrait la fraction en divisant d'abord un grand temps par la période). Pour un temps
     * entier, le résultat est identique bit à bit à la surcharge {@code long}.
     *
     * @throws IllegalArgumentException si le temps n'est pas fini (NaN ou infini)
     */
    public static double fraction(double ticks, long phaseTicks, long period) {
        if (!Double.isFinite(ticks)) throw new IllegalArgumentException("temps non fini : " + ticks);
        double whole = Math.floor(ticks);
        // fraction = partie fractionnaire du temps, dans [0, 1) : elle s'ajoute à un reste entier dans [0, period)
        return ((double) Math.floorMod((long) whole + phaseTicks, period) + (ticks - whole)) / (double) period;
    }

    /**
     * Décalage d'un objet par rapport au centre de son orbite, dans le référentiel de ce centre (son parent).
     * Cercle de rayon {@code radius} parcouru à vitesse constante ; l'inclinaison fait pivoter le plan du cercle autour
     * de l'axe X (le plan contient donc toujours l'axe X).
     */
    public static double[] orbitOffset(RailFollower.Orbit orbit, double ticks) {
        double angle = 2 * Math.PI * fraction(ticks, orbit.phaseTicks(), orbit.period());
        double c = StrictMath.cos(angle);
        double s = StrictMath.sin(angle);
        double ci = StrictMath.cos(orbit.inclination());
        double si = StrictMath.sin(orbit.inclination());
        // cercle dans le plan XY (c, s, 0), puis rotation d'`inclination` autour de X : y' = s·cos(i), z' = s·sin(i)
        return new double[] { orbit.radius() * c, orbit.radius() * s * ci, orbit.radius() * s * si };
    }

    /** Angle de rotation (radians, [0, 2π)) d'un objet en rotation sur place, autour de l'axe Y. */
    public static double spinAngle(RailFollower.Spin spin, double ticks) {
        return 2 * Math.PI * fraction(ticks, spin.phaseTicks(), spin.period());
    }

    /**
     * Transform local effectif de l'objet (dans le référentiel de son parent) au temps donné : celui que calcule son
     * contrôleur, ou le Transform stocké s'il est immobile ({@code controller == null}).
     */
    public static Transform local(SpaceObject object, double ticks) {
        Controller controller = object.controller();
        return controller == null ? object.transform() : controller.resolve(object.transform(), ticks);
    }

    /**
     * Position de l'objet dans le référentiel racine (le plus global). Pour comparer deux objets, préférer
     * {@link #relativePosition}, qui garde la précision entre échelles.
     */
    public static double[] positionAt(StrategicLayer layer, SpaceObject object, double ticks) {
        return positionIn(layer, null, object, ticks);
    }

    /**
     * Position de {@code object} dans le référentiel de l'ancêtre {@code frame} ({@code null} = racine) : zéro si c'est
     * lui-même, sinon on additionne, du plus haut au plus bas, l'origine de chaque ancêtre. Un ancêtre {@code Frame}
     * convertit les coordonnées de ses descendants complètement (T + R·S·p), un corps ne transmet que sa translation.
     *
     * @param frame ancêtre dont le référentiel sert de repère, ou {@code null} pour la racine de la couche
     * @throws IllegalStateException si {@code frame} n'est pas un ancêtre de l'objet, ou si la chaîne de parents est
     *         invalide (parent absent, cycle)
     */
    public static double[] positionIn(StrategicLayer layer, @Nullable SpaceObject frame, SpaceObject object, double ticks) {
        if (object == frame) return new double[3];
        // ancêtres du plus proche au plus lointain, `frame` exclu
        List<SpaceObject> chain = ancestorsUpTo(layer, object, frame);
        // On descend du plus haut ancêtre vers l'objet. `origin` est l'origine du nœud courant exprimée dans le repère
        // de `frame` ; `linear` empile les parties linéaires (rotation·échelle) des cadres déjà traversés, qui
        // s'appliquent à tout ce qui est situé en dessous d'eux.
        double[] origin = new double[3];
        List<Transform> linear = new ArrayList<>();
        for (int i = chain.size() - 1; i >= 0; i--) {
            SpaceObject node = chain.get(i);
            Transform t = local(node, ticks);
            // la translation du nœud est dans le repère de son parent : on la fait passer par les cadres au-dessus
            origin = add(origin, through(linear, t.translation()));
            // un cadre déforme ensuite tout ce qui est en dessous de lui ; un corps ne transmet que sa translation
            if (isFrame(node)) linear.add(t);
        }
        // l'objet lui-même : sa translation locale passe par tous les cadres traversés
        return add(origin, through(linear, local(object, ticks).translation()));
    }

    /**
     * Position de {@code a} moins celle de {@code b}, dans le référentiel de leur plus proche ancêtre commun. Les deux
     * positions sont calculées dans ce repère commun et non dans le repère global : deux objets proches l'un de l'autre
     * mais très éloignés de l'origine gardent toute la précision d'un double sur leur écart.
     */
    public static double[] relativePosition(StrategicLayer layer, SpaceObject a, SpaceObject b, double ticks) {
        SpaceObject common = commonAncestor(layer, a, b);
        double[] pa = positionIn(layer, common, a, ticks);
        double[] pb = positionIn(layer, common, b, ticks);
        return new double[] { pa[0] - pb[0], pa[1] - pb[1], pa[2] - pb[2] };
    }

    /**
     * Transform stocké de {@code object} réécrit pour qu'il garde sa position (au temps donné) une fois rattaché à
     * {@code newParent} ({@code null} = racine). Seule la translation change ; rotation et échelle sont reprises telles
     * quelles. Sert à {@code StrategicLayer.reparent}.
     */
    static Transform rebased(StrategicLayer layer, SpaceObject object, @Nullable SpaceObject newParent, double ticks) {
        // On travaille dans le repère du plus proche ancêtre commun de l'objet et du nouveau parent : la position de
        // l'objet y est connue sans passer par le repère global.
        SpaceObject common = commonAncestor(layer, object, newParent);
        double[] p = positionIn(layer, common, object, ticks);
        // Chaîne qui descend de l'ancêtre commun jusqu'au nouveau parent (nouveau parent en premier).
        List<SpaceObject> chain = new ArrayList<>();
        for (SpaceObject current = newParent; current != common; current = parentOf(layer, current)) {
            if (current == null || chain.size() > MAX_DEPTH) throw new IllegalStateException("Arbre invalide autour de " + object.id());
            chain.add(current);
        }
        // On exprime p dans le repère du nouveau parent en défaisant, de l'ancêtre commun vers le nouveau parent, le
        // passage de chaque nœud : inverse complète pour un cadre, simple soustraction de la translation pour un corps.
        for (int i = chain.size() - 1; i >= 0; i--) {
            Transform t = local(chain.get(i), ticks);
            p = isFrame(chain.get(i)) ? t.invert(p) : new double[] { p[0] - t.tx(), p[1] - t.ty(), p[2] - t.tz() };
        }
        return object.transform().withTranslation(p[0], p[1], p[2]);
    }

    /**
     * Plus proche ancêtre (ou l'objet lui-même) commun à {@code a} et {@code b} ; {@code null} = racine de la couche.
     *
     * @param b peut être {@code null} (la racine) : le résultat est alors toujours {@code null}
     */
    static @Nullable SpaceObject commonAncestor(StrategicLayer layer, SpaceObject a, @Nullable SpaceObject b) {
        // TODO : C'est quoi un Ancestor ?
        // On recense tous les ancêtres de b (lui compris), puis on remonte depuis a jusqu'au premier qui y figure.
        Set<UUID> ancestorsOfB = new HashSet<>();
        int depth = 0;
        for (SpaceObject current = b; current != null; current = parentOf(layer, current)) {
            if (++depth > MAX_DEPTH) throw new IllegalStateException("Arbre trop profond ou cycle de parents : " + b.id());
            ancestorsOfB.add(current.id());
        }
        depth = 0;
        for (SpaceObject current = a; current != null; current = parentOf(layer, current)) {
            if (ancestorsOfB.contains(current.id())) return current;
            if (++depth > MAX_DEPTH) throw new IllegalStateException("Arbre trop profond ou cycle de parents : " + a.id());
        }
        // aucun ancêtre en commun : seule la racine virtuelle les réunit
        return null;
    }

    /**
     * Ancêtres de {@code object}, du plus proche au plus lointain, jusqu'à {@code frame} exclu ({@code null} = racine,
     * donc toute la chaîne jusqu'à la galaxie).
     *
     * @throws IllegalStateException si {@code frame} n'est pas dans la chaîne, ou si la chaîne est trop profonde (cycle)
     */
    private static List<SpaceObject> ancestorsUpTo(StrategicLayer layer, SpaceObject object, @Nullable SpaceObject frame) {
        List<SpaceObject> chain = new ArrayList<>();
        for (SpaceObject current = parentOf(layer, object); current != frame; current = parentOf(layer, current)) {
            // la racine a été atteinte sans croiser `frame` : ce n'est pas un ancêtre
            if (current == null) throw new IllegalStateException(frame.id() + " n'est pas un ancêtre de " + object.id());
            if (chain.size() >= MAX_DEPTH) throw new IllegalStateException("Arbre trop profond ou cycle de parents : " + object.id());
            chain.add(current);
        }
        return chain;
    }

    /**
     * Fait passer le point {@code p} par les parties linéaires (rotation·échelle) de plusieurs cadres, du plus bas
     * (dernier de la liste) au plus haut (premier).
     *
     * @param linear transforms des cadres traversés, du plus haut au plus bas
     */
    private static double[] through(List<Transform> linear, double[] p) {
        for (int i = linear.size() - 1; i >= 0; i--) p = linear.get(i).linear(p);
        return p;
    }

    /** Somme de deux vecteurs de trois composantes, dans un nouveau tableau. */
    private static double[] add(double[] a, double[] b) {
        return new double[] { a[0] + b[0], a[1] + b[1], a[2] + b[2] };
    }

    /** {@code true} si l'objet est un référentiel (composant {@code Frame}) : il transmet son Transform complet. */
    private static boolean isFrame(SpaceObject object) {
        return object.has(ComponentTypes.FRAME);
    }

    /**
     * Parent de l'objet dans la couche, ou {@code null} s'il est à la racine.
     *
     * @throws IllegalStateException si l'identifiant de parent ne désigne aucun objet de la couche
     */
    @Nullable
    private static SpaceObject parentOf(StrategicLayer layer, SpaceObject object) {
        UUID parentId = object.parent();
        if (parentId == null) return null;
        SpaceObject parent = layer.find(parentId);
        if (parent == null) throw new IllegalStateException("Parent " + parentId + " absent de la couche (objet " + object.id() + ")");
        return parent;
    }
}
