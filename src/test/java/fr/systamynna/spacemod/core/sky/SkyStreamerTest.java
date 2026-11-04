package fr.systamynna.spacemod.core.sky;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import fr.systamynna.spacemod.core.SpaceNet;
import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.StrategicLayer;
import fr.systamynna.spacemod.core.component.components.Sky;
import fr.systamynna.spacemod.core.relevance.PlayerSpacePos;
import fr.systamynna.spacemod.core.sky.SkyStreamer.CellKey;
import fr.systamynna.spacemod.core.store.JdbcSpaceStore;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 5000 systèmes synthétiques dans une galaxie : le ciel répliqué = exactement les systèmes au-dessus de la magnitude limite. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SkyStreamerTest {
    static final double LY = SkyParams.LY_KM;

    StrategicLayer layer;
    SpaceObject gal, sys0, star0;
    /** uuid → {x, y, z (km), lum}. */
    final Map<UUID, double[]> all = new HashMap<>();

    SkyStreamer s;

    @BeforeAll
    void build() throws Exception {
        layer = new StrategicLayer();
        layer.start(new JdbcSpaceStore(DriverManager.getConnection("jdbc:sqlite::memory:")));
        gal = layer.add("milky_way", layer.tree().resolve("universe"));
        layer.setScale(gal.getUuid(), LY);
        Random r = new Random(7);
        for (int i = 0; i < 5000; i++) {
            SpaceObject o = layer.add("s" + i, gal);
            double x = 600 * r.nextDouble() - 300, y = 600 * r.nextDouble() - 300, z = 600 * r.nextDouble() - 300, lum = Math.pow(10, 6 * r.nextDouble() - 3);
            layer.setScale(o.getUuid(), LY);
            layer.editTransform(o.getUuid(), t -> { t.x = x; t.y = y; t.z = z; });
            Sky sky = new Sky();
            sky.luminosity = lum;
            layer.addComponent(o.getUuid(), sky);
            all.put(o.getUuid(), new double[] {x * LY, y * LY, z * LY, (float) lum});
            if (i == 0) sys0 = o;
        }
        star0 = layer.add("star", sys0); // étoile du premier système : ses résumés sont chez l'ancêtre galaxie
        layer.flush();
        s = new SkyStreamer(layer);
    }

    /** Ce que le client croit avoir après les paquets (modèle minimal de {@code ClientSky}). */
    static final class Client {
        final Map<CellKey, List<UUID>> cells = new HashMap<>();
        int resets, chunks, drops;

        void apply(CustomPacketPayload p) {
            switch (p) {
                case SpaceNet.SkyReset x -> { cells.clear(); resets++; }
                case SpaceNet.SkyDrop d -> { cells.remove(new CellKey(d.parent(), d.cx(), d.cy(), d.cz())); drops++; }
                case SpaceNet.SkyChunk c -> {
                    CellKey k = new CellKey(c.parent(), c.cx(), c.cy(), c.cz());
                    if (c.first()) cells.put(k, new ArrayList<>());
                    c.stars().forEach(st -> cells.get(k).add(st.uuid()));
                    chunks++;
                }
                default -> fail("paquet inattendu " + p);
            }
        }

        Set<UUID> stars() {
            Set<UUID> out = new HashSet<>();
            cells.values().forEach(out::addAll);
            return out;
        }
    }

    private Set<UUID> expected(double x, double y, double z) {
        Set<UUID> out = new HashSet<>();
        all.forEach((u, a) -> {
            double dx = a[0] - x, dy = a[1] - y, dz = a[2] - z;
            if (SkyParams.visible((float) a[3], dx * dx + dy * dy + dz * dz)) out.add(u);
        });
        return out;
    }

    private void update(Client c, UUID player, PlayerSpacePos pos) { s.update(player, pos, c::apply); }

    @Test
    void streamsOnlyAboveMagnitudeLimitAndDiffs() {
        UUID player = UUID.randomUUID();
        Client c = new Client();
        update(c, player, new PlayerSpacePos(gal.getUuid(), 0, 0, 0));
        assertEquals(1, c.resets);
        Set<UUID> near = expected(0, 0, 0);
        assertEquals(near, c.stars());
        assertTrue(near.size() > 50 && near.size() < 2500, "borné par la magnitude limite, pas 5000 : " + near.size());
        int chunks = c.chunks;

        update(c, player, new PlayerSpacePos(gal.getUuid(), 0, 0, 0)); // même position : rien à renvoyer
        assertEquals(chunks, c.chunks);
        assertEquals(0, c.drops);
        assertEquals(1, c.resets);

        // déplacement de 3 al : seules les cellules dont le contenu change sont renvoyées
        double x = 3 * LY;
        update(c, player, new PlayerSpacePos(gal.getUuid(), x, 0, 0));
        assertEquals(expected(x, 0, 0), c.stars());
        assertTrue(c.chunks - chunks < c.cells.size(), "diff : pas tout renvoyé");

        // loin : le ciel suit (cellules abandonnées -> SkyDrop)
        x = 1500 * LY;
        update(c, player, new PlayerSpacePos(gal.getUuid(), x, 0, 0));
        assertTrue(c.drops > 0);
        assertEquals(expected(x, 0, 0), c.stars());

        // sans position : plus rien
        update(c, player, null);
        assertTrue(c.cells.isEmpty());

        // déconnexion : l'état est oublié, le client repart d'un Reset
        s.logout(player);
        update(c, player, new PlayerSpacePos(gal.getUuid(), 0, 0, 0));
        assertEquals(2, c.resets);
        assertEquals(near, c.stars());
    }

    @Test
    void frameIsNearestAncestorWithSummaries() {
        // l'étoile du système 0 n'a pas de résumé autour d'elle : le repère est la galaxie, l'observateur est à la position de sys0
        double[] a = all.get(sys0.getUuid());
        Client c = new Client();
        update(c, UUID.randomUUID(), new PlayerSpacePos(star0.getUuid(), 0, 0, 0));
        assertEquals(expected(a[0], a[1], a[2]), c.stars());
        assertTrue(c.cells.keySet().stream().allMatch(k -> k.parent().equals(gal.getUuid())));
    }

    @Test
    void chunksStayUnderPacketLimit() {
        UUID parent = UUID.randomUUID();
        List<SkySummary> stars = new ArrayList<>();
        for (int i = 0; i < 60_000; i++) stars.add(new SkySummary(UUID.randomUUID(), parent, i, 0, 0, 1, 0xFFFFFF, 1));
        List<CustomPacketPayload> out = new ArrayList<>();
        SkyStreamer.chunks(new CellKey(parent, 0, 0, 0), stars, out::add);
        assertTrue(out.size() >= 3);
        int n = 0;
        for (int i = 0; i < out.size(); i++) {
            var p = (SpaceNet.SkyChunk) out.get(i);
            assertEquals(i == 0, p.first());
            n += p.stars().size();
            FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
            SpaceNet.SkyChunk.CODEC.encode(b, p);
            assertTrue(b.readableBytes() < SkyParams.MAX_CHUNK_BYTES + 100, "taille " + b.readableBytes());
            assertTrue(b.readableBytes() < 1 << 20);
        }
        assertEquals(60_000, n);
    }
}
