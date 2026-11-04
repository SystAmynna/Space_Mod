package fr.systamynna.spacemod.core.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.StrategicLayer;
import fr.systamynna.spacemod.core.Subscriptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;

/** Configs de ciel par dimension (côté serveur), persistées dans le monde et poussées aux joueurs de la dimension. */
public final class RenderConfigs {
    private final StrategicLayer layer;
    private final Subscriptions subs;
    private final RenderAssets assets = new RenderAssets();
    private final Map<ResourceLocation, RenderConfig> byDim = new HashMap<>();
    private Path file;
    private boolean dirty;

    public RenderConfigs(StrategicLayer layer, Subscriptions subs) {
        this.layer = layer;
        this.subs = subs;
    }

    public RenderAssets assets() { return assets; }

    public void start(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT);
        file = root.resolve("spacemod").resolve("render.dat");
        assets.start(root);
        byDim.clear();
        try {
            if (Files.isRegularFile(file)) {
                for (Tag e : NbtIo.read(file).getList("configs", Tag.TAG_COMPOUND)) {
                    RenderConfig c = RenderConfig.load((CompoundTag) e);
                    if (c != null) byDim.put(c.dimension, c);
                }
            }
        } catch (IOException e) {
            AmynnaSpaceMod.LOGGER.error("Lecture de {} impossible, configs de rendu ignorées", file, e);
        }
        dirty = false;
    }

    public void flush() {
        if (!dirty || file == null) return;
        net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
        byDim.values().forEach(c -> l.add(c.save()));
        CompoundTag t = new CompoundTag();
        t.put("configs", l);
        try {
            Files.createDirectories(file.getParent());
            NbtIo.write(t, file);
            dirty = false;
        } catch (IOException e) {
            AmynnaSpaceMod.LOGGER.error("Écriture de {} impossible", file, e);
        }
    }

    public void stop() {
        flush();
        assets.stop();
        byDim.clear();
        file = null;
    }

    @Nullable
    public RenderConfig get(ResourceLocation dim) { return byDim.get(dim); }

    /** Crée la config de {@code dim} ou change son objet de référence (le décalage est conservé). */
    public RenderConfig setObject(ResourceLocation dim, java.util.UUID object) {
        RenderConfig c = byDim.get(dim);
        if (c == null) byDim.put(dim, c = new RenderConfig(dim, object));
        else c.object = object;
        dirty = true;
        return c;
    }

    public boolean remove(ResourceLocation dim) {
        dirty = true;
        return byDim.remove(dim) != null;
    }

    public void markDirty() { dirty = true; }

    /** Envoie la config de la dimension du joueur (ou son absence) et l'abonne à ce qu'il faut pour la rendre. */
    public void send(ServerPlayer p) {
        ResourceLocation dim = p.level().dimension().location();
        RenderConfig c = byDim.get(dim);
        if (c != null) {
            try {
                subs.subscribeAround(p, layer.getOrThrow(c.object));
            } catch (SpaceObjectException e) { // objet supprimé depuis : le client recevra la config sans objet à rendre
                AmynnaSpaceMod.LOGGER.warn("Objet de rendu {} de {} introuvable", c.object, dim);
            }
        }
        PacketDistributor.sendToPlayer(p, new RenderNet.Config(dim, c == null ? null : c.save()));
    }

    public void broadcast(ServerLevel level) { level.players().forEach(this::send); }
}
