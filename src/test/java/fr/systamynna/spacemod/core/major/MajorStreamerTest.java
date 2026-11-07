package fr.systamynna.spacemod.core.major;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.systamynna.spacemod.core.SpaceNet;
import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.StrategicLayer;
import fr.systamynna.spacemod.core.component.components.MajorContainer;
import fr.systamynna.spacemod.core.component.components.MajorContainer.Entry;
import fr.systamynna.spacemod.core.component.components.MajorObject;
import fr.systamynna.spacemod.core.major.MajorStreamer.CellKey;
import fr.systamynna.spacemod.core.relevance.PlayerSpacePos;
import fr.systamynna.spacemod.core.store.JdbcSpaceStore;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 3000 systèmes synthétiques dans une galaxie (MajorContainer) : le joueur reçoit toutes ses étoiles, par cellules, et seulement le diff ensuite. */
class MajorStreamerTest {
    static final double LY = MajorParams.LY_KM;
    static final int N = 3000;

    StrategicLayer layer;
    SpaceObject gal, sys0, star0, empty;
    final List<SpaceObject> systems = new ArrayList<>();
    MajorStreamer s;

    @BeforeEach
    void build() throws Exception {
        layer = new StrategicLayer();
        layer.start(new JdbcSpaceStore(DriverManager.getConnection("jdbc:sqlite::memory:")));
        gal = layer.add("milky_way", layer.tree().resolve("universe"));
        layer.setScale(gal.getUuid(), LY);
        layer.addComponent(gal.getUuid(), new MajorContainer());
        Random r = new Random(7);
        for (int i = 0; i < N; i++) {
            SpaceObject o = layer.add("s" + i, gal);
            double x = 600 * r.nextDouble() - 300, y = 600 * r.nextDouble() - 300, z = 600 * r.nextDouble() - 300;
            layer.setScale(o.getUuid(), LY);
            layer.editTransform(o.getUuid(), t -> { t.x = x; t.y = y; t.z = z; });
            MajorObject m = new MajorObject();
            m.luminosity = Math.pow(10, 6 * r.nextDouble() - 3);
            layer.addComponent(o.getUuid(), m);
            systems.add(o);
        }
        sys0 = systems.get(0);
        star0 = layer.add("star", sys0); // sous un objet majeur : jamais listé, mais son ancêtre galaxie est le conteneur
        empty = layer.add("empty", layer.tree().resolve("universe")); // pas de conteneur dans sa chaîne
        layer.flush();
        s = new MajorStreamer(layer);
    }

    /** Ce que le client croit avoir après les paquets (modèle minimal de {@code MajorStarsDrawer}). */
    static final class Client {
        final Map<CellKey, List<UUID>> cells = new HashMap<>();
        int resets, chunks, drops;

        void apply(CustomPacketPayload p) {
            switch (p) {
                case SpaceNet.MajorReset x -> { cells.clear(); resets++; }
                case SpaceNet.MajorDrop d -> { cells.remove(new CellKey(d.parent(), d.cx(), d.cy(), d.cz())); drops++; }
                case SpaceNet.MajorChunk c -> {
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

    private Set<UUID> allSystems() {
        Set<UUID> out = new HashSet<>();
        systems.forEach(o -> out.add(o.getUuid()));
        return out;
    }

    private void update(Client c, UUID player, PlayerSpacePos pos) { s.update(player, pos, c::apply); }

    @Test
    void streamsEveryStarOfTheContainerAndDiffs() throws Exception {
        UUID player = UUID.randomUUID();
        Client c = new Client();
        update(c, player, new PlayerSpacePos(gal.getUuid(), 0, 0, 0));
        assertEquals(1, c.resets);
        assertEquals(allSystems(), c.stars());
        assertTrue(c.cells.size() > 1, "plusieurs cellules");
        assertTrue(c.cells.keySet().stream().allMatch(k -> k.parent().equals(gal.getUuid())));
        int chunks = c.chunks;

        // la position du joueur n'intervient pas, et rien n'a changé : rien à renvoyer
        update(c, player, new PlayerSpacePos(gal.getUuid(), 900 * LY, 0, 0));
        assertEquals(chunks, c.chunks);
        assertEquals(0, c.drops);
        assertEquals(1, c.resets);

        // une étoile change de luminosité : seule sa cellule est renvoyée
        layer.editComponent(systems.get(5).getUuid(), MajorObject.class, m -> ((MajorObject) m).luminosity = 42);
        update(c, player, new PlayerSpacePos(gal.getUuid(), 0, 0, 0));
        assertEquals(chunks + 1, c.chunks);
        assertEquals(allSystems(), c.stars());

        // une étoile supprimée : sa cellule est mise à jour (ou abandonnée si elle devient vide)
        UUID gone = systems.get(7).getUuid();
        layer.remove(systems.get(7), true);
        update(c, player, new PlayerSpacePos(gal.getUuid(), 0, 0, 0));
        Set<UUID> expected = allSystems();
        expected.remove(gone);
        assertEquals(expected, c.stars());

        // sans position : plus rien
        update(c, player, null);
        assertTrue(c.cells.isEmpty());

        // déconnexion : l'état est oublié, le client repart d'un Reset
        s.logout(player);
        update(c, player, new PlayerSpacePos(gal.getUuid(), 0, 0, 0));
        assertEquals(2, c.resets);
        assertEquals(expected, c.stars());
    }

    @Test
    void containerIsNearestAncestorOfTheAnchor() {
        Client c = new Client();
        update(c, UUID.randomUUID(), new PlayerSpacePos(star0.getUuid(), 0, 0, 0)); // dans un système : le conteneur est la galaxie
        assertEquals(allSystems(), c.stars());
        assertTrue(c.cells.keySet().stream().allMatch(k -> k.parent().equals(gal.getUuid())));

        Client none = new Client();
        update(none, UUID.randomUUID(), new PlayerSpacePos(empty.getUuid(), 0, 0, 0)); // aucun conteneur dans la chaîne
        assertTrue(none.cells.isEmpty());
    }

    @Test
    void chunksStayUnderPacketLimit() {
        UUID parent = UUID.randomUUID();
        List<Entry> stars = new ArrayList<>();
        for (int i = 0; i < 60_000; i++) stars.add(new Entry(UUID.randomUUID(), i, 0, 0, 0xFFFFFF, 1));
        List<CustomPacketPayload> out = new ArrayList<>();
        MajorStreamer.chunks(new CellKey(parent, 0, 0, 0), stars, out::add);
        assertTrue(out.size() >= 3);
        int n = 0;
        for (int i = 0; i < out.size(); i++) {
            var p = (SpaceNet.MajorChunk) out.get(i);
            assertEquals(i == 0, p.first());
            n += p.stars().size();
            FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
            SpaceNet.MajorChunk.CODEC.encode(b, p);
            assertTrue(b.readableBytes() < MajorParams.MAX_CHUNK_BYTES + 100, "taille " + b.readableBytes());
            assertTrue(b.readableBytes() < 1 << 20);
        }
        assertEquals(60_000, n);
    }
}
