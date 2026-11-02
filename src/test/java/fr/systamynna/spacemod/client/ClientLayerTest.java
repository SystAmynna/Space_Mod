package fr.systamynna.spacemod.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import fr.systamynna.spacemod.core.SpaceObject;
import net.minecraft.nbt.CompoundTag;

class ClientLayerTest {
    private static CompoundTag data(String name) { return new SpaceObject(UUID.randomUUID(), null, name).save(); }

    @Test
    void applyDropChildrenAncestors() {
        ClientLayer c = new ClientLayer();
        UUID root = UUID.randomUUID(), mid = UUID.randomUUID(), leaf = UUID.randomUUID();
        c.apply(root, null, data("universe"));
        c.apply(mid, root, data("mid"));
        c.apply(leaf, mid, data("leaf"));
        assertTrue(c.has(leaf));
        assertEquals("leaf", c.get(leaf).getName());
        assertEquals(1, c.children(mid).size());
        assertEquals(java.util.List.of("mid", "universe"), c.ancestors(leaf).stream().map(SpaceObject::getName).toList());
        c.drop(mid);
        assertFalse(c.has(mid));
        assertTrue(c.ancestors(leaf).isEmpty()); // la chaîne s'arrête au premier absent
        c.apply(leaf, root, data("leaf")); // upsert : remplace
        assertEquals(root, c.get(leaf).getParent());
        c.clear();
        assertFalse(c.has(root));
    }
}
