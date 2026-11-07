package fr.systamynna.spacemod.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

class SpaceNetMajorCodecTest {
    private static <T> T roundTrip(StreamCodec<FriendlyByteBuf, T> codec, T p) {
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(b, p);
        T out = codec.decode(b);
        assertEquals(0, b.readableBytes());
        return out;
    }

    @Test
    void majorPacketsRoundTrip() {
        UUID parent = UUID.randomUUID();
        var chunk = new SpaceNet.MajorChunk(parent, -3, 0, 7, true,
                List.of(new SpaceNet.Star(UUID.randomUUID(), 1.5f, -2e14f, 0, 0xFFF4EA, 25.4f), new SpaceNet.Star(UUID.randomUUID(), 0, 0, 3f, 0, 1e-3f)));
        assertEquals(chunk, roundTrip(SpaceNet.MajorChunk.CODEC, chunk));
        var empty = new SpaceNet.MajorChunk(parent, 0, 0, 0, false, List.of());
        assertEquals(empty, roundTrip(SpaceNet.MajorChunk.CODEC, empty));
        var drop = new SpaceNet.MajorDrop(parent, Integer.MIN_VALUE, 0, Integer.MAX_VALUE);
        assertEquals(drop, roundTrip(SpaceNet.MajorDrop.CODEC, drop));
        assertEquals(new SpaceNet.MajorReset(), roundTrip(SpaceNet.MajorReset.CODEC, new SpaceNet.MajorReset()));
    }
}
