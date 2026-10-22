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

package fr.systamynna.dynamicdimensions.api.event;

import com.mojang.authlib.GameProfile;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;

import java.util.Optional;

/**
 * Fork (Amynna's Space Mod) : publié sur {@code NeoForge.EVENT_BUS}, sur le thread serveur, pendant la phase de login d'un joueur,
 * avant la synchronisation des registres avec son client. C'est le moment où charger la dimension dans laquelle il a été
 * sauvegardé : sinon NeoForge le replace dans l'Overworld avec ses coordonnées d'origine (PlayerList#placeNewPlayer).
 * Peut être publié plusieurs fois pour une même connexion ; les écouteurs doivent être idempotents.
 */
public class PlayerLoginDimensionEvent extends Event {
    private final MinecraftServer server;
    private final GameProfile profile;
    private final Optional<ResourceKey<Level>> savedDimension;

    public PlayerLoginDimensionEvent(MinecraftServer server, GameProfile profile, Optional<ResourceKey<Level>> savedDimension) {
        this.server = server;
        this.profile = profile;
        this.savedDimension = savedDimension;
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    public GameProfile getProfile() {
        return this.profile;
    }

    /** Dimension enregistrée dans les données du joueur ; vide pour un premier login ou des données illisibles. */
    public Optional<ResourceKey<Level>> getSavedDimension() {
        return this.savedDimension;
    }
}
