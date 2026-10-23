package fr.systamynna.spacemod.strategic;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import fr.systamynna.spacemod.strategic.component.Controller;
import fr.systamynna.spacemod.strategic.component.Destroyable;
import fr.systamynna.spacemod.strategic.component.ExclusionVolume;
import fr.systamynna.spacemod.strategic.component.Frame;
import fr.systamynna.spacemod.strategic.component.HostedDimension;
import fr.systamynna.spacemod.strategic.component.Identity;
import fr.systamynna.spacemod.strategic.component.Pilotable;
import fr.systamynna.spacemod.strategic.component.hostedPropriety.PortPoint;
import fr.systamynna.spacemod.strategic.component.RailFollower;
import fr.systamynna.spacemod.strategic.component.Render;
import fr.systamynna.spacemod.strategic.component.StellarSystem;
import fr.systamynna.spacemod.strategic.component.Transform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Assemblages de composants : ceux du tableau des archétypes (architecture §6), plus la galaxie et le système stellaire,
 * qui sont des cadres de l'arbre. Des modèles de départ, pas des classes : on peut ajouter ou retirer des composants à
 * l'objet obtenu. Chaque fabrique crée un objet neuf à identifiant aléatoire, sans parent ni couche.
 */
public final class Archetypes {
    /** Archétype d'une galaxie : référentiel à l'échelle galactique. Valeur stockée dans {@code Identity.archetype}. */
    public static final String GALAXY = "galaxy";
    /** Archétype d'un système stellaire : référentiel à l'échelle stellaire. Valeur stockée dans {@code Identity.archetype}. */
    public static final String SYSTEM = "system";
    /** Archétype d'un astre sans dimension. Valeur stockée dans {@code Identity.archetype}. */
    public static final String STAR = "star";
    /** Archétype d'une planète ou d'une lune. Valeur stockée dans {@code Identity.archetype}. */
    public static final String PLANET = "planet";
    /** Archétype d'une mégastructure (anneau, sphère de Dyson). Valeur stockée dans {@code Identity.archetype}. */
    public static final String MEGASTRUCTURE = "megastructure";
    /** Archétype d'une station sur rail orbital. Valeur stockée dans {@code Identity.archetype}. */
    public static final String STATION = "station";
    /** Archétype d'un croiseur piloté. Valeur stockée dans {@code Identity.archetype}. */
    public static final String CRUISER = "cruiser";
    /** Archétype d'un grand vaisseau piloté. Valeur stockée dans {@code Identity.archetype}. */
    public static final String SHIP = "ship";
    /** Archétype d'une navette pilotée : ignore les volumes d'exclusion {@code LARGE_ONLY}. Valeur stockée dans {@code Identity.archetype}. */
    public static final String SHUTTLE = "shuttle";
    /** Préfixe de {@code Identity.id} d'un vaisseau : l'id texte d'un vaisseau n'est pas un chemin. */
    public static final String SHIP_ID_PREFIX = "ship:";

    /** Type de couche de rendu du vaisseau : la sphère (côté client, {@code RenderObjects}). */
    private static final ResourceLocation SPHERE = ResourceLocation.fromNamespaceAndPath("amynnaspacemod", "sphere");

    /** Classe utilitaire : uniquement des fabriques statiques. */
    private Archetypes() {}

    /**
     * Galaxie : un référentiel à l'échelle galactique, racine de l'arbre ; son Transform est dans le référentiel global.
     * Composants : Identity, Transform, {@link Frame}. Résidente en permanence.
     *
     * @param name nom d'affichage
     * @param transform position, rotation et échelle de la galaxie dans le référentiel global
     * @param controller mouvement de la galaxie ({@code null} = immobile)
     * @return l'objet créé, sans parent ni couche
     */
    public static SpaceObject galaxy(String name, Transform transform, @Nullable Controller controller) {
        return base(name, GALAXY, transform, controller).put(new Frame());
    }

    /**
     * Système stellaire : un référentiel à l'échelle stellaire. Composants : Identity, Transform, {@link Frame} et le
     * composant {@link StellarSystem}, créé sans astre principal (son identifiant n'existe pas avant l'ajout de l'étoile :
     * remplacer le composant ensuite). Le système est résident ; ses corps forment une unité de chargement.
     *
     * @param name nom d'affichage
     * @param transform position, rotation et échelle du système dans sa galaxie (l'échelle convertit les unités)
     * @param controller mouvement du système dans la galaxie ({@code null} = immobile)
     * @return l'objet créé, sans parent ni couche
     */
    public static SpaceObject system(String name, Transform transform, @Nullable Controller controller) {
        return base(name, SYSTEM, transform, controller).put(new Frame()).put(new StellarSystem(null));
    }

    /**
     * Astre : Transform, Render, exclusion, contrôleur facultatif (rail ou rotation). Pas de dimension, indestructible.
     *
     * @param name nom d'affichage
     * @param transform position, rotation et échelle dans le référentiel du parent
     * @param render données de rendu dans le ciel
     * @param exclusionRadius rayon du volume d'exclusion, dans l'unité du référentiel de l'objet (une étoile exclut sans avoir de dimension)
     * @return l'objet créé, sans parent ni couche
     */
    public static SpaceObject star(String name, Transform transform, @Nullable Controller controller, Render render,
            double exclusionRadius) {
        return base(name, STAR, transform, controller).put(render).put(new ExclusionVolume(exclusionRadius));
    }

    /**
     * Planète ou lune : astre + dimension hébergée + sites d'atterrissage. Indestructible. Le Transform part de
     * l'origine ; un contrôleur à rail le remplace.
     *
     * @param name nom d'affichage
     * @param controller mouvement ({@code null} = immobile, à la position du Transform)
     * @param render données de rendu dans le ciel
     * @param radius rayon logique R du port « principal » ajouté si {@code ports} est vide, aussi taille de la dimension
     * @param ports portes de la dimension ; vide : un port omnidirectionnel de rayon R
     * @param dimension dimension persistante hébergée (une dimension n'a qu'un hôte)
     * @return l'objet créé, sans parent ni couche
     */
    public static SpaceObject planet(String name, @Nullable Controller controller, Render render, ResourceLocation dimension,
            double radius, List<PortPoint> ports) {
        return hosted(base(name, PLANET, Transform.ORIGIN, controller).put(render), dimension, ports.isEmpty() ? List.of(PortPoint.main(radius)) : ports);
    }

    /**
     * Mégastructure : dimension, rail, ports avec capacité d'approche. Indestructible. Mêmes paramètres que
     * {@link #planet}, avec un rail obligatoire et sans Render.
     *
     * @param name nom d'affichage
     * @param rail rail orbital suivi par l'objet
     * @param dimension dimension persistante hébergée
     * @param radius rayon logique R du port « principal » ajouté si {@code ports} est vide
     * @param ports portes de la dimension (avec capacité d'approche) ; vide : un port omnidirectionnel de rayon R
     * @return l'objet créé, sans parent ni couche
     */
    public static SpaceObject megastructure(String name, RailFollower rail, ResourceLocation dimension,
            double radius, List<PortPoint> ports) {
        return hosted(base(name, MEGASTRUCTURE, Transform.ORIGIN, rail), dimension,
                ports.isEmpty() ? List.of(PortPoint.main(radius)) : ports);
    }

    /**
     * Station : dimension, rail orbital, un port à 360°. Destructible. Mêmes paramètres que {@link #planet}, avec un
     * rail obligatoire, sans Render ni ports (le port omnidirectionnel est ajouté).
     *
     * @param name nom d'affichage
     * @param rail rail orbital suivi par l'objet
     * @param dimension dimension persistante hébergée
     * @param radius rayon logique R du port, aussi taille de la dimension
     * @return l'objet créé, sans parent ni couche
     */
    public static SpaceObject station(String name, RailFollower rail, ResourceLocation dimension, double radius) {
        return hosted(base(name, STATION, Transform.ORIGIN, rail), dimension, List.of(PortPoint.main(radius)))
                .put(Destroyable.alive());
    }

    /**
     * Croiseur : dimension, pilotage, un port à 360°. Destructible.
     *
     * @param name nom d'affichage
     * @param transform position de départ, dans le référentiel du parent auquel il sera ajouté
     * @param dimension dimension persistante hébergée
     * @param radius rayon logique R du port, aussi taille de la dimension
     * @return l'objet créé, sans parent ni couche
     */
    public static SpaceObject cruiser(String name, Transform transform, ResourceLocation dimension, double radius) {
        return hosted(base(name, CRUISER, transform, Pilotable.AT_REST), dimension, List.of(PortPoint.main(radius)))
                .put(Destroyable.alive());
    }

    /**
     * Vaisseau piloté, nommé par son seul nom (unique parmi les vaisseaux) : {@code Identity.id} vaut {@code ship:<nom>}.
     * Pilotage, dimension (créée au premier accès), un port à 360°, exclusion, rendu en sphère grise. Destructible.
     * Une navette ({@code large == false}) n'est pas repoussée par les volumes d'exclusion {@link ExclusionVolume.Scope#LARGE_ONLY}.
     *
     * @param id identifiant de l'objet (déterministe, voir {@code ShipRegistry.uuid})
     * @param name nom du vaisseau
     * @param transform position de départ, dans le référentiel du parent auquel il sera ajouté
     * @param radius rayon logique R du port, aussi taille de la dimension
     * @param large {@code true} pour un grand vaisseau, {@code false} pour une navette
     * @return l'objet créé, sans parent ni couche
     */
    public static SpaceObject ship(UUID id, String name, Transform transform, double radius, boolean large) {
        CompoundTag hull = new CompoundTag();
        hull.putString("color", "#9aa4b0");
        SpaceObject ship = new SpaceObject(id)
                .put(new Identity(name, SHIP_ID_PREFIX + name, large ? SHIP : SHUTTLE))
                .put(transform)
                .controller(Pilotable.AT_REST)
                .put(new Render(radius, List.of(new Render.Layer(SPHERE, hull))));
        return hosted(ship, null, List.of(PortPoint.main(radius))).put(Destroyable.alive());
    }

    /** Objet de base commun à tous les archétypes : identifiant aléatoire, Identity, Transform et contrôleur. */
    private static SpaceObject base(String name, String archetype, Transform transform, @Nullable Controller controller) {
        return new SpaceObject(UUID.randomUUID())
                .put(new Identity(name, archetype))
                .put(transform)
                .controller(controller);
    }

    /**
     * Ajoute à un objet ce qu'il faut pour héberger une dimension : la dimension et ses ports (volume logique de rayon
     * R), et l'exclusion à {@code DEFAULT_FACTOR} fois R.
     */
    private static SpaceObject hosted(SpaceObject object, @Nullable ResourceLocation dimension, List<PortPoint> ports) {
        return object
                .put(new HostedDimension(dimension, HostedDimension.Properties.SPACE, Transform.ORIGIN,
                        HostedDimension.DEFAULT_RENDER, ports))
                .put(ExclusionVolume.scaled(ports.get(0).logical(), ExclusionVolume.DEFAULT_FACTOR));
    }
}
