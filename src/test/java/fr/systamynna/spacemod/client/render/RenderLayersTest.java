package fr.systamynna.spacemod.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import fr.systamynna.spacemod.spaceprovider.ProviderEntry;
import fr.systamynna.spacemod.spaceprovider.SpaceObjectJson;
import fr.systamynna.spacemod.strategic.component.ComponentTypes;
import fr.systamynna.spacemod.strategic.component.Render;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** LOD, maillage de sphère et formats de {@code Render} (NBT et JSON, ancien et nouveau). */
class RenderLayersTest {
    private static final float[] UP = { 0.1f, 0.4f, 1.4f };

    @Test
    void lodMontePuisDescendAvecHysteresis() {
        assertEquals(1, Lod.step(0, 0.11f, UP));
        // juste sous le seuil de montée : on reste au niveau 1
        assertEquals(1, Lod.step(1, 0.09f, UP));
        assertEquals(0, Lod.step(1, 0.07f, UP));
        // un seul pas par appel, même pour un saut de taille
        assertEquals(1, Lod.step(0, 5f, UP));
        assertEquals(3, Lod.step(3, 5f, UP));
    }

    @Test
    void maillageDeSphere() {
        SphereMesh m = SphereMesh.build(24, 6);
        assertEquals(25 * 7, m.vertexCount());
        for (int k = 0; k < m.vertexCount(); k++) {
            assertEquals(1.0, Math.sqrt(m.x[k] * m.x[k] + m.y[k] * m.y[k] + m.z[k] * m.z[k]), 1e-5);
            assertTrue(m.u[k] >= 0 && m.u[k] <= 1 && m.v[k] >= 0 && m.v[k] <= 1);
        }
        assertEquals(1f, m.y[m.index(0, 0)], 1e-6);
    }

    @Test
    void renderNbtNouveauEtAncienFormat() {
        CompoundTag params = new CompoundTag();
        params.putString("texture", "amynnaspacemod:textures/space/sol/terre.png");
        params.putDouble("scale", 1.03);
        Render r = new Render(6.4e6, List.of(new Render.Layer(RenderObjects.SPHERE, params)));
        Render back = (Render) ComponentTypes.read(r.typeId(), r.save());
        assertEquals(r, back);

        CompoundTag old = new CompoundTag();
        old.putString("model", "amynnaspacemod:proxy/rock");
        old.putDouble("apparent_radius", 7e8);
        Render legacy = (Render) ComponentTypes.read(r.typeId(), old);
        assertEquals(7e8, legacy.apparentRadius());
        assertEquals(ResourceLocation.parse("amynnaspacemod:proxy/rock"), legacy.layers().get(0).type());
    }

    @Test
    void renderJsonAllerRetour() {
        JsonObject json = JsonParser.parseString("""
                {"id": "x", "archetype": "star", "name": "X",
                 "render": {"radius": 5.0, "layers": [{"type": "amynnaspacemod:sphere", "texture": "a:b.png", "shade": false, "scale": 2}]}}
                """).getAsJsonObject();
        ProviderEntry e = SpaceObjectJson.fromJson(json);
        CompoundTag p = e.render().layers().get(0).params();
        assertEquals("a:b.png", p.getString("texture"));
        assertEquals(false, p.getBoolean("shade"));
        assertEquals(2.0, p.getDouble("scale"));
        assertEquals(e.render(), SpaceObjectJson.fromJson(SpaceObjectJson.toJson(e)).render());
    }

    @Test
    void nouveauxTypesDeCoucheEtCouleur() {
        for (ResourceLocation type : List.of(RenderObjects.STAR, RenderObjects.GALAXY, RenderObjects.GALACTIC_CLOUD)) {
            assertTrue(RenderObjects.create(new Render.Layer(type, new CompoundTag())) != null, type.toString());
        }
        assertEquals(1f, Billboard.color("#ff0000")[0], 1e-6);
        assertEquals(0f, Billboard.color("#ff0000")[1], 1e-6);
        // valeur illisible : blanc
        assertEquals(1f, Billboard.color("zz")[2], 1e-6);
    }

    @Test
    void fondConcentreDansLAnneauGalactique() {
        SkyBackground.Field f = SkyBackground.generate(1234);
        assertEquals(SkyBackground.STARS, f.stars().length / 8);
        assertEquals(java.util.Arrays.toString(f.stars()), java.util.Arrays.toString(SkyBackground.generate(1234).stars()));
        int band = 0, center = 0;
        for (int i = 0; i < f.stars().length; i += 8) {
            if (Math.abs(f.stars()[i + 2]) < Math.sin(0.5)) band++;
            if (f.stars()[i] > Math.cos(0.5)) center++;
        }
        int n = SkyBackground.STARS;
        // uniforme : 48 % dans |lat| < 0,5 rad ; 6 % dans 0,5 rad du centre
        assertTrue(band > 0.7 * n, "anneau : " + band);
        assertTrue(center > 0.15 * n, "centre : " + center);
    }

    @Test
    void visee() {
        assertEquals(0f, fr.systamynna.spacemod.strategic.SkyFrame.lookAngles(new double[] { 0, 0, 5 })[0], 1e-4);
        assertEquals(-90f, fr.systamynna.spacemod.strategic.SkyFrame.lookAngles(new double[] { 3, 0, 0 })[0], 1e-4);
        assertEquals(-90f, fr.systamynna.spacemod.strategic.SkyFrame.lookAngles(new double[] { 0, 2, 0 })[1], 1e-4);
        assertEquals(null, fr.systamynna.spacemod.strategic.SkyFrame.lookAngles(new double[] { 0, 0, 0 }));
    }
}
