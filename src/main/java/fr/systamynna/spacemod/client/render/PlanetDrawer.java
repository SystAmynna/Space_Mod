package fr.systamynna.spacemod.client.render;

import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.BufferBuilder;

import fr.systamynna.spacemod.core.render.RenderMath.Placed;
import fr.systamynna.spacemod.core.render.PlanetRender;
import fr.systamynna.spacemod.core.render.SpaceRenderObject;
import net.minecraft.resources.ResourceLocation;

/** Sphère UV texturée, maillage unitaire calculé une fois. */
final class PlanetDrawer implements SpaceRenderObjectDrawer {
    private static final int LON = 48, LAT = 24;
    /** Triangles : 3 sommets × (x, y, z, u, v). */
    private static final float[] MESH = build();

    private final Vector3d tmp = new Vector3d();
    private final Vector3f out = new Vector3f();

    private static float[] build() {
        float[] m = new float[LON * LAT * 6 * 5];
        int n = 0;
        for (int la = 0; la < LAT; la++)
            for (int lo = 0; lo < LON; lo++) {
                int[][] c = {{lo, la}, {lo + 1, la}, {lo + 1, la + 1}, {lo, la}, {lo + 1, la + 1}, {lo, la + 1}};
                for (int[] v : c) {
                    double th = Math.PI * v[1] / LAT, ph = 2 * Math.PI * v[0] / LON;
                    m[n++] = (float) (Math.sin(th) * Math.cos(ph));
                    m[n++] = (float) Math.cos(th);
                    m[n++] = (float) (Math.sin(th) * Math.sin(ph));
                    m[n++] = (float) v[0] / LON;
                    m[n++] = (float) v[1] / LAT;
                }
            }
        return m;
    }

    @Override
    @Nullable
    public ResourceLocation texture(SpaceRenderObject ro) { return AssetCache.texture(((PlanetRender) ro).texture); }

    @Override
    public void emit(BufferBuilder bb, Matrix4f mv, Placed p, double k, SpaceRenderObject ro) {
        for (int i = 0; i < MESH.length; i += 5) {
            tmp.set(MESH[i] * p.scale().x, MESH[i + 1] * p.scale().y, MESH[i + 2] * p.scale().z).mul(p.radius());
            p.orientation().transform(tmp).add(p.pos()).mul(k);
            mv.transformPosition((float) tmp.x, (float) tmp.y, (float) tmp.z, out);
            bb.addVertex(out.x, out.y, out.z).setUv(MESH[i + 3], MESH[i + 4]).setColor(-1);
        }
    }
}
