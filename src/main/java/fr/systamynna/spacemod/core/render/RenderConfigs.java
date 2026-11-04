package fr.systamynna.spacemod.core.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.StrategicLayer;
import fr.systamynna.spacemod.core.Subscriptions;
import fr.systamynna.spacemod.core.clock.SpaceClock;
import fr.systamynna.spacemod.core.relevance.PlayerSpacePos;
import fr.systamynna.spacemod.core.relevance.Relevance;
import fr.systamynna.spacemod.core.sky.SkyStreamer;
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
    private final SkyStreamer sky;
    private final Map<ResourceLocation, RenderConfig> byDim = new HashMap<>();
    private Path file;
    private boolean dirty;

    public RenderConfigs(StrategicLayer layer, Subscriptions subs) {
        this.layer = layer;
        this.subs = subs;
        this.sky = new SkyStreamer(layer);
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
        sky.clear();
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

    /** Objets de référence des configs : le serveur les garde loadés. */
    public boolean isHost(java.util.UUID object) {
        return byDim.values().stream().anyMatch(c -> c.object.equals(object));
    }

    /** Parent du joueur s'il existe encore ; sinon {@code null} (repli sur la config de la dimension). */
    @Nullable
    private UUID parent(ServerPlayer p) {
        return subs.parentOf(p.getUUID()).filter(u -> {
            try {
                layer.peek(u);
                return true;
            } catch (SpaceObjectException e) {
                return false;
            }
        }).orElse(null);
    }

    private PlayerSpacePos pos(ServerPlayer p) { return PlayerSpacePos.of(parent(p), byDim.get(p.level().dimension().location())); }

    /** Libère l'état de ciel du joueur. */
    public void logout(ServerPlayer p) { sky.logout(p.getUUID()); }

    /**
     * Recalcule les abonnements automatiques du joueur depuis sa position : son parent, sinon la config de sa dimension. Sans l'un ni l'autre,
     * il n'en a plus ; ses abonnements manuels ne sont pas touchés.
     */
    public void refresh(ServerPlayer p) {
        PlayerSpacePos pos = pos(p);
        Set<UUID> next = Set.of();
        try {
            if (pos != null) next = Relevance.needed(layer, pos, SpaceClock.SERVER.now() / 50, subs.auto(p), Relevance.Params.DEFAULT);
        } catch (SpaceObjectException e) { // objet supprimé depuis : le client recevra la config sans objet à rendre
            AmynnaSpaceMod.LOGGER.debug("Objet de rendu {} introuvable", pos.anchor());
        }
        subs.setAuto(p, next);
    }

    /**
     * Envoie la config de la dimension du joueur (ou son absence) et l'abonne à ce qu'il faut pour la rendre. Si le joueur a un parent, la config
     * est rapportée à ce parent (pos/rot/scale de la dimension gardés comme décalage dans le parent).
     */
    public void send(ServerPlayer p) {
        ResourceLocation dim = p.level().dimension().location();
        RenderConfig c = byDim.get(dim);
        UUID parent = parent(p);
        if (c != null && parent != null) c = c.withObject(parent);
        refresh(p);
        sky.send(p, pos(p)); // ciel lointain : recalculé ici seulement (position qui change = cet appel), pas à chaque refresh
        PacketDistributor.sendToPlayer(p, new RenderNet.Config(dim, c == null ? null : c.save()));
    }

    public void broadcast(ServerLevel level) { level.players().forEach(this::send); }
}
