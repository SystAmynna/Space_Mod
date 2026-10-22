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

package fr.systamynna.dynamicdimensions.impl.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Dynamic;
import fr.systamynna.dynamicdimensions.api.event.PlayerLoginDimensionEvent;
import fr.systamynna.dynamicdimensions.impl.Constants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Shadow
    @org.spongepowered.asm.mixin.Final
    private MinecraftServer server;

    /** Appelé pendant le login (puis une seconde fois à la fin de la configuration) : avant la synchro des registres. */
    @Inject(method = "canPlayerLogin", at = @At("HEAD"))
    private void dynamicdimensions$announceLogin(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Component> cir) {
        Optional<ResourceKey<Level>> saved = Optional.empty();
        Path file = this.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(profile.getId() + ".dat");
        try {
            if (Files.isRegularFile(file)) {
                CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
                if (tag.contains("Dimension")) {
                    saved = DimensionType.parseLegacy(new Dynamic<>(NbtOps.INSTANCE, tag.get("Dimension"))).result();
                }
            }
        } catch (Exception e) {
            Constants.LOGGER.warn("Données du joueur {} illisibles pour retrouver sa dimension", profile.getId(), e);
        }
        NeoForge.EVENT_BUS.post(new PlayerLoginDimensionEvent(this.server, profile, saved));
    }
}
