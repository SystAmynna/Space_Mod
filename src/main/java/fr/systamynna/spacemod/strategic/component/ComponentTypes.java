package fr.systamynna.spacemod.strategic.component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Registre ouvert des types de composants : en ajouter un ne touche à aucun autre. Trois rôles :
 * <ul>
 * <li>clés typées : {@code object.get(ComponentTypes.TRANSFORM)} renvoie un {@link Transform} sans cast ;</li>
 * <li>identifiant → lecteur NBT : au chargement, la clé {@code amynnaspacemod:transform} désigne la classe à instancier ;</li>
 * <li>point d'extension : un autre mod enregistre ses propres types (identifiant dans son namespace) avant le
 * chargement du monde ; les types inconnus sont conservés en {@link UnknownComponent}.</li>
 * </ul>
 */
public final class ComponentTypes {
    /** Types enregistrés par identifiant ; concurrent car d'autres mods s'y inscrivent pendant leur initialisation. */
    private static final Map<ResourceLocation, ComponentType<?>> REGISTRY = new ConcurrentHashMap<>();

    // Types fournis par ce mod, dans le namespace amynnaspacemod.
    /** Type du composant : nom d'affichage et archétype. */
    public static final ComponentType<Identity> IDENTITY = register("identity", Identity::load);
    /** Type du composant : position, rotation, échelle. */
    public static final ComponentType<Transform> TRANSFORM = register("transform", Transform::load);
    /** Type du composant : données de rendu dans le ciel. */
    public static final ComponentType<Render> RENDER = register("render", Render::load);
    /** Type du composant : marqueur de référentiel. */
    public static final ComponentType<Frame> FRAME = register("frame", Frame::load);
    /** Type du composant : système stellaire et son astre principal. */
    public static final ComponentType<StellarSystem> STELLAR_SYSTEM = register("stellar_system", StellarSystem::load);
    /** Type du composant : objet destructible et son état. */
    public static final ComponentType<Destroyable> DESTROYABLE = register("destroyable", Destroyable::load);
    /** Type du composant : dimension persistante hébergée. */
    public static final ComponentType<HostedDimension> HOSTED_DIMENSION = register("hosted_dimension", HostedDimension::load);
    /** Type du composant : sites d'atterrissage et d'amarrage. */
    public static final ComponentType<PortsPoints> PORTS_POINTS = register("ports_points", PortsPoints::load);
    /** Type du composant : volume logique (taille de la dimension). */
    public static final ComponentType<LogicalVolume> LOGICAL_VOLUME = register("logical_volume", LogicalVolume::load);
    /** Type du composant : volume d'exclusion. */
    public static final ComponentType<ExclusionVolume> EXCLUSION_VOLUME = register("exclusion_volume", ExclusionVolume::load);

    /** Classe utilitaire : registre statique. */
    private ComponentTypes() {}

    /**
     * Enregistre un type de composant. À appeler avant le chargement du monde (constructeur du mod), avec un
     * identifiant dans le namespace du mod appelant.
     *
     * @param <T> classe des composants de ce type
     * @param id identifiant du type, dans le namespace du mod appelant
     * @param reader reconstruit un composant depuis le NBT écrit par {@link Component#save()}
     * @return la clé typée, à conserver pour {@code SpaceObject.get(...)}
     * @throws IllegalStateException si l'identifiant est déjà pris
     */
    public static <T extends Component> ComponentType<T> register(ResourceLocation id, Function<CompoundTag, T> reader) {
        ComponentType<T> type = new ComponentType<>(id, reader);
        if (REGISTRY.putIfAbsent(id, type) != null) {
            throw new IllegalStateException("Type de composant déjà enregistré : " + id);
        }
        return type;
    }

    /** Enregistre un type de ce mod : identifiant {@code amynnaspacemod:<path>}. */
    private static <T extends Component> ComponentType<T> register(String path, Function<CompoundTag, T> reader) {
        return register(ResourceLocation.fromNamespaceAndPath(AmynnaSpaceMod.MODID, path), reader);
    }

    /**
     * Relit un composant ; un type inconnu est conservé tel quel pour ne rien perdre à la réécriture.
     *
     * @param id identifiant du type, lu dans le NBT de l'objet
     * @param tag données du composant
     * @return le composant du type enregistré, ou un {@link UnknownComponent} si le type n'est pas enregistré
     */
    public static Component read(ResourceLocation id, CompoundTag tag) {
        ComponentType<?> type = REGISTRY.get(id);
        return type != null ? type.read(tag) : new UnknownComponent(id, tag);
    }
}
