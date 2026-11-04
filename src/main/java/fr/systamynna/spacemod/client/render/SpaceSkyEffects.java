package fr.systamynna.spacemod.client.render;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;

/**
 * Enrobage des effets de dimension du niveau client : délègue à l'original, sauf quand un ciel spatial est actif
 * (ciel vanilla, nuages et précipitations annulés, le ciel spatial est dessiné à la place).
 */
final class SpaceSkyEffects extends DimensionSpecialEffects {
    private final DimensionSpecialEffects base;

    SpaceSkyEffects(DimensionSpecialEffects base) {
        super(base.getCloudHeight(), base.hasGround(), base.skyType(), base.forceBrightLightmap(), base.constantAmbientLight());
        this.base = base;
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) { return base.getBrightnessDependentFogColor(color, brightness); }

    @Override
    public boolean isFoggyAt(int x, int y) { return base.isFoggyAt(x, y); }

    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) { return base.getSunriseColor(timeOfDay, partialTicks); }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix, Camera camera, Matrix4f projectionMatrix,
            boolean isFoggy, Runnable setupFog) {
        if (SpaceRenderer.config(level) == null) return base.renderSky(level, ticks, partialTick, modelViewMatrix, camera, projectionMatrix, isFoggy, setupFog);
        SpaceRenderer.INSTANCE.draw(level, modelViewMatrix);
        return true;
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY, double camZ,
            Matrix4f modelViewMatrix, Matrix4f projectionMatrix) {
        return SpaceRenderer.config(level) != null
                || base.renderClouds(level, ticks, partialTick, poseStack, camX, camY, camZ, modelViewMatrix, projectionMatrix);
    }

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ) {
        return SpaceRenderer.config(level) != null || base.renderSnowAndRain(level, ticks, partialTick, lightTexture, camX, camY, camZ);
    }

    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) { return SpaceRenderer.config(level) != null || base.tickRain(level, ticks, camera); }
}
