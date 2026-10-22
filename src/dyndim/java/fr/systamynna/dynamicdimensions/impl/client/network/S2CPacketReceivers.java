/*
 * Copyright (c) 2021-2026 Team Galacticraft
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package fr.systamynna.dynamicdimensions.impl.client.network;

import fr.systamynna.dynamicdimensions.impl.network.S2CPackets;
import fr.systamynna.dynamicdimensions.impl.registry.RegistryUtil;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Réception côté client ; les gestionnaires s'exécutent sur le thread principal du client. */
public final class S2CPacketReceivers {
    private S2CPacketReceivers() {}

    public static void registerReceivers() {
        S2CPackets.createHandler = S2CPacketReceivers::createDynamicDimension;
        S2CPackets.removeHandler = S2CPacketReceivers::removeDynamicDimension;
    }

    private static void createDynamicDimension(S2CPackets.CreateDimension payload, IPayloadContext context) {
        ClientPacketListener handler = (ClientPacketListener) context.listener();
        DimensionType type = DimensionType.DIRECT_CODEC.decode(NbtOps.INSTANCE, payload.dimensionType()).getOrThrow().getFirst();
        RegistryUtil.registerUnfreezeExact(handler.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE), payload.id(), type);
        handler.levels().add(ResourceKey.create(Registries.DIMENSION, payload.id()));
    }

    private static void removeDynamicDimension(S2CPackets.RemoveDimension payload, IPayloadContext context) {
        ClientPacketListener handler = (ClientPacketListener) context.listener();
        RegistryUtil.unregister(handler.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE), payload.id());
        handler.levels().remove(ResourceKey.create(Registries.DIMENSION, payload.id()));
    }
}
