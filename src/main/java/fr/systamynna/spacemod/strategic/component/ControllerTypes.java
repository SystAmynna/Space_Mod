package fr.systamynna.spacemod.strategic.component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Registre ouvert des types de contrôleurs, sur le modèle de {@link ComponentTypes} : identifiant → lecteur NBT.
 * À remplir avant le chargement du monde (constructeur du mod) ; un type inconnu est conservé en {@link UnknownController}.
 */
public final class ControllerTypes {
    /** Lecteurs enregistrés par identifiant de type ; concurrent car d'autres mods s'y inscrivent à l'initialisation. */
    private static final Map<ResourceLocation, Function<CompoundTag, Controller>> REGISTRY = new ConcurrentHashMap<>();

    /** Identifiant de {@link RailFollower}. */
    public static final ResourceLocation RAIL_FOLLOWER = id("rail_follower");
    /** Identifiant de {@link Pilotable}. */
    public static final ResourceLocation PILOTABLE = id("pilotable");

    // contrôleurs par défaut de ce mod
    static {
        register(RAIL_FOLLOWER, RailFollower::load);
        register(PILOTABLE, Pilotable::load);
    }

    /** Classe utilitaire : registre statique. */
    private ControllerTypes() {}

    /** Identifiant {@code amynnaspacemod:<path>}. */
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(AmynnaSpaceMod.MODID, path);
    }

    /**
     * Enregistre un type de contrôleur. À appeler avant le chargement du monde, avec un identifiant dans le namespace du
     * mod appelant.
     *
     * @param reader reconstruit un contrôleur depuis les données écrites par {@link Controller#save()}
     * @throws IllegalStateException si l'identifiant est déjà pris
     */
    public static void register(ResourceLocation id, Function<CompoundTag, Controller> reader) {
        if (REGISTRY.putIfAbsent(id, reader) != null) {
            throw new IllegalStateException("Type de contrôleur déjà enregistré : " + id);
        }
    }

    /** Relit un contrôleur écrit par {@link Controller#write}. */
    public static Controller read(CompoundTag tag) {
        // un type absent du registre (mod retiré) n'est pas une erreur : on garde ses données pour les réécrire
        ResourceLocation id = ResourceLocation.parse(tag.getString("type"));
        CompoundTag data = tag.getCompound("data");
        Function<CompoundTag, Controller> reader = REGISTRY.get(id);
        return reader != null ? reader.apply(data) : new UnknownController(id, data);
    }
}
