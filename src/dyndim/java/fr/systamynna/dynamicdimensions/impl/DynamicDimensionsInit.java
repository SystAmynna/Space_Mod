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

package fr.systamynna.dynamicdimensions.impl;

import fr.systamynna.dynamicdimensions.impl.client.network.S2CPacketReceivers;
import fr.systamynna.dynamicdimensions.impl.network.S2CPackets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;

/** Point d'entrée de la bibliothèque, appelé par le mod hôte : enregistre les paquets réseau. */
public final class DynamicDimensionsInit {
    private DynamicDimensionsInit() {}

    public static void init(IEventBus modEventBus, Dist dist) {
        modEventBus.addListener(S2CPackets::register);
        if (dist.isClient()) {
            S2CPacketReceivers.registerReceivers();
        }
    }
}
