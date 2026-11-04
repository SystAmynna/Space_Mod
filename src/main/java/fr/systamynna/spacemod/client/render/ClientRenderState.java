package fr.systamynna.spacemod.client.render;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.core.render.RenderConfig;
import net.minecraft.resources.ResourceLocation;

/** Config de ciel reçue du serveur pour la dimension courante du client (sans classe client : chargeable partout). */
public final class ClientRenderState {
    private ClientRenderState() {}

    private static RenderConfig active;

    public static void set(@Nullable RenderConfig c) { active = c; }

    /** Config active si elle concerne la dimension {@code dim}. */
    @Nullable
    public static RenderConfig forDimension(ResourceLocation dim) { return active != null && active.dimension.equals(dim) ? active : null; }
}
