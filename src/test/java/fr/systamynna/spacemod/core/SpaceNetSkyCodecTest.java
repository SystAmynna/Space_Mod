package fr.systamynna.spacemod.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

class SpaceNetSkyCodecTest {
    private static <T> T roundTrip(StreamCodec<FriendlyByteBuf, T> codec, T p) {
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(b, p);
        T out = codec.decode(b);
        assertEquals(0, b.readableBytes());
        return out;
    }

    @Test
    void skyPacketsRoundTrip() {
        UUID parent = UUID.randomUUID();
        var chunk = new SpaceNet.SkyChunk(parent, -3, 0, 7, true,
                List.of(new SpaceNet.Star(UUID.randomUUID(), 1.5f, -2e14f, 0, 0xFFF4EA, 25.4f), new SpaceNet.Star(UUID.randomUUID(), 0, 0, 3f, 0, 1e-3f)));
        assertEquals(chunk, roundTrip(SpaceNet.SkyChunk.CODEC, chunk));
        var empty = new SpaceNet.SkyChunk(parent, 0, 0, 0, false, List.of());
        assertEquals(empty, roundTrip(SpaceNet.SkyChunk.CODEC, empty));
        var drop = new SpaceNet.SkyDrop(parent, Integer.MIN_VALUE, 0, Integer.MAX_VALUE);
        assertEquals(drop, roundTrip(SpaceNet.SkyDrop.CODEC, drop));
        assertEquals(new SpaceNet.SkyReset(), roundTrip(SpaceNet.SkyReset.CODEC, new SpaceNet.SkyReset()));
    }
}
