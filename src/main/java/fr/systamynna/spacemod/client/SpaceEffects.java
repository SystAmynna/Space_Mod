package fr.systamynna.spacemod.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import fr.systamynna.spacemod.client.render.SkyRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;

/**
 * Effets des dimensions d'objets : le rendu vanilla du ciel, des astres, des nuages et de la pluie est annulé (les
 * surcharges renvoient {@code true}, « déjà rendu »), le fond est un noir parfait, sans brouillard. Même schéma que le
 * {@code CancelingEffects} de hololib. Le ciel spatial est dessiné par {@link SkyRenderer} à partir de {@link SkyContext}.
 */
public final class SpaceEffects extends DimensionSpecialEffects {
    /** Altitude de nuages NaN et type de ciel NONE : en plus des surcharges, pour les chemins qui lisent ces valeurs. */
    public SpaceEffects() {
        super(Float.NaN, false, SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return Vec3.ZERO;
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    /** Pas de dégradé de lever et coucher de soleil. */
    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        return null;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f frustumMatrix, Camera camera,
            Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        SkyBlackFill.render();
        SkyRenderer.render(level, frustumMatrix, projectionMatrix, partialTick);
        return true;
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX,
            double camY, double camZ, Matrix4f frustumMatrix, Matrix4f projectionMatrix) {
        return true;
    }

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture,
            double camX, double camY, double camZ) {
        return true;
    }
}
