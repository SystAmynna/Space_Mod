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

package fr.systamynna.dynamicdimensions.impl.network;

import fr.systamynna.dynamicdimensions.impl.Constants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Paquets serveur vers client qui synchronisent le registre des types de dimension (charge utile NeoForge native). */
public final class S2CPackets {
    public record CreateDimension(ResourceLocation id, CompoundTag dimensionType) implements CustomPacketPayload {
        public static final Type<CreateDimension> TYPE = new Type<>(Constants.CREATE_DIMENSION_PACKET);
        public static final StreamCodec<FriendlyByteBuf, CreateDimension> CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeResourceLocation(p.id); buf.writeNbt(p.dimensionType); },
                buf -> new CreateDimension(buf.readResourceLocation(), buf.readNbt()));

        @Override
        public Type<CreateDimension> type() {
            return TYPE;
        }
    }

    public record RemoveDimension(ResourceLocation id) implements CustomPacketPayload {
        public static final Type<RemoveDimension> TYPE = new Type<>(Constants.REMOVE_DIMENSION_PACKET);
        public static final StreamCodec<FriendlyByteBuf, RemoveDimension> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeResourceLocation(p.id),
                buf -> new RemoveDimension(buf.readResourceLocation()));

        @Override
        public Type<RemoveDimension> type() {
            return TYPE;
        }
    }

    /** Renseignés côté client uniquement (S2CPacketReceivers), pour ne pas charger de classe client sur un serveur dédié. */
    public static volatile IPayloadHandler<CreateDimension> createHandler = (payload, context) -> {};
    public static volatile IPayloadHandler<RemoveDimension> removeHandler = (payload, context) -> {};

    private S2CPackets() {}

    /** À brancher sur le bus du mod. */
    public static void register(RegisterPayloadHandlersEvent event) {
        // Thread réseau : le type doit être enregistré avant le décodage du paquet respawn qui suit (sinon "No value with id N").
        // shortcut: registre client modifié hors thread principal sans verrou, à revoir si crash concurrent.
        PayloadRegistrar registrar = event.registrar("1").optional().executesOn(HandlerThread.NETWORK);
        registrar.playToClient(CreateDimension.TYPE, CreateDimension.CODEC, (p, c) -> createHandler.handle(p, c));
        registrar.playToClient(RemoveDimension.TYPE, RemoveDimension.CODEC, (p, c) -> removeHandler.handle(p, c));
    }

    public static void sendCreateDimension(ServerPlayer player, ResourceLocation id, CompoundTag serializedType) {
        PacketDistributor.sendToPlayer(player, new CreateDimension(id, serializedType));
    }

    public static void sendRemoveDimension(ServerPlayer player, ResourceLocation id) {
        PacketDistributor.sendToPlayer(player, new RemoveDimension(id));
    }
}
