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

package fr.systamynna.dynamicdimensions.impl.mixin.compat.sable;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

public class SableMixinPlugin implements IMixinConfigPlugin {

    // Logger local : Constants contient des ResourceLocation, qui seraient chargées avant les mixins d'Iris.
    private static final Logger LOGGER = LoggerFactory.getLogger("dynamicdimensions");

    private boolean sableLoaded;

    @Override
    public void onLoad(final String mixinPackage) {
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object instance = loaderClass.getMethod("getInstance").invoke(null);
            this.sableLoaded = (boolean) loaderClass.getMethod("isModLoaded", String.class).invoke(instance, "sable");
            LOGGER.info("Sable detected via FabricLoader: {}", this.sableLoaded);
        } catch (final Exception e) {
            // Not on Fabric, fall back to checking for NeoForge mod list
            try {
                // ModList.get() est encore null au chargement des mixins : on interroge la liste de chargement.
                Class<?> loader = Class.forName("net.neoforged.fml.loading.FMLLoader");
                Object list = loader.getMethod("getLoadingModList").invoke(null);
                this.sableLoaded = list.getClass().getMethod("getModFileById", String.class).invoke(list, "sable") != null;
                LOGGER.info("Sable detected via LoadingModList: {}", this.sableLoaded);
            } catch (final Exception ex) {
                this.sableLoaded = false;
                LOGGER.info("Could not determine if Sable is loaded: {}", ex.getMessage());
            }
        }
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        return this.sableLoaded;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}