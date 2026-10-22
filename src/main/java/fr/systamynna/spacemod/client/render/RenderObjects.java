package fr.systamynna.spacemod.client.render;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.strategic.component.Render;
import net.minecraft.resources.ResourceLocation;

/** Registre des types de couche : un autre mod ajoute les siens avec {@link #register}. */
public final class RenderObjects {
    /** Sphère texturée. */
    public static final ResourceLocation SPHERE = ResourceLocation.fromNamespaceAndPath(AmynnaSpaceMod.MODID, "sphere");

    /** Halo brillant d'étoile. */
    public static final ResourceLocation STAR = ResourceLocation.fromNamespaceAndPath(AmynnaSpaceMod.MODID, "star");
    /** Galaxie lointaine. */
    public static final ResourceLocation GALAXY = ResourceLocation.fromNamespaceAndPath(AmynnaSpaceMod.MODID, "galaxy");
    /** Nuage de la galaxie de l'observateur. */
    public static final ResourceLocation GALACTIC_CLOUD = ResourceLocation.fromNamespaceAndPath(AmynnaSpaceMod.MODID, "galactic_cloud");

    private static final Map<ResourceLocation, Function<Render.Layer, RenderObject>> TYPES = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> WARNED = new HashSet<>();

    static {
        register(SPHERE, SphereRenderObject::new);
        register(STAR, StarRenderObject::new);
        register(GALAXY, GalaxyRenderObject::new);
        register(GALACTIC_CLOUD, GalacticCloudRenderObject::new);
    }

    /** Classe utilitaire. */
    private RenderObjects() {}

    public static void register(ResourceLocation type, Function<Render.Layer, RenderObject> factory) {
        TYPES.put(type, factory);
    }

    /** Instance de la couche, ou {@code null} (journalisé une fois) si son type est inconnu. */
    public static @Nullable RenderObject create(Render.Layer layer) {
        Function<Render.Layer, RenderObject> factory = TYPES.get(layer.type());
        if (factory == null) {
            if (WARNED.add(layer.type())) AmynnaSpaceMod.LOGGER.warn("Type de rendu inconnu : {}", layer.type());
            return null;
        }
        return factory.apply(layer);
    }
}
