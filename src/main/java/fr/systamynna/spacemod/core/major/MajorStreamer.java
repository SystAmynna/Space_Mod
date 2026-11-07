package fr.systamynna.spacemod.core.major;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import fr.systamynna.spacemod.core.SpaceNet;
import fr.systamynna.spacemod.core.SpaceNet.Star;
import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.StrategicLayer;
import fr.systamynna.spacemod.core.component.components.MajorContainer;
import fr.systamynna.spacemod.core.component.components.MajorContainer.Entry;
import fr.systamynna.spacemod.core.relevance.PlayerSpacePos;
import fr.systamynna.spacemod.core.render.RenderMath;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Réplique aux joueurs toutes les étoiles majeures du {@link MajorContainer} où ils se trouvent (le plus proche ancêtre de leur ancre qui en est un),
 * par cellules : la cellule est l'unité de diff, un client ne reçoit que les cellules nouvelles ou changées. La position du joueur n'intervient pas.
 * Ne charge aucun objet ({@link MajorContainer#collect}) ; la liste d'un conteneur est gardée tant que l'arbre ne change pas.
 */
public final class MajorStreamer {
    public record CellKey(UUID parent, int cx, int cy, int cz) {}

    /** Étoiles d'un conteneur par cellule, et l'empreinte de chaque cellule (détecte un changement de contenu). */
    private record Cells(long mods, Map<CellKey, List<Entry>> stars, Map<CellKey, Integer> hashes) {}

    private static final Cells NONE = new Cells(-1, Map.of(), Map.of());

    private final StrategicLayer layer;
    /** Joueur → empreintes des cellules qu'il a reçues. */
    private final Map<UUID, Map<CellKey, Integer>> sent = new HashMap<>();
    private final Map<UUID, Cells> cache = new HashMap<>();

    public MajorStreamer(StrategicLayer layer) { this.layer = layer; }

    /** Recalcule les étoiles du joueur depuis {@code pos} ({@code null} : plus d'étoiles) et envoie la différence. */
    public void send(ServerPlayer p, @Nullable PlayerSpacePos pos) { update(p.getUUID(), pos, packet -> PacketDistributor.sendToPlayer(p, packet)); }

    void update(UUID player, @Nullable PlayerSpacePos pos, Consumer<CustomPacketPayload> out) {
        Cells next = pos == null ? NONE : select(pos);
        Map<CellKey, Integer> known = sent.get(player);
        if (known == null) out.accept(new SpaceNet.MajorReset());
        else if (known.equals(next.hashes)) return;
        Map<CellKey, Integer> prev = known == null ? Map.of() : known;
        for (CellKey k : prev.keySet()) if (!next.hashes.containsKey(k)) out.accept(new SpaceNet.MajorDrop(k.parent, k.cx, k.cy, k.cz));
        next.stars.forEach((k, stars) -> {
            if (!next.hashes.get(k).equals(prev.get(k))) chunks(k, stars, out);
        });
        sent.put(player, next.hashes);
    }

    public void logout(UUID player) { sent.remove(player); }

    public void clear() {
        sent.clear();
        cache.clear();
    }

    private Cells select(PlayerSpacePos pos) {
        List<SpaceObject> chain;
        try {
            chain = RenderMath.chain(layer::peek, layer.peek(pos.anchor()));
        } catch (SpaceObjectException e) { // ancre supprimée
            return NONE;
        }
        for (SpaceObject o : chain) if (o.getComponent(MajorContainer.class).isPresent()) return cells(o);
        return NONE;
    }

    // shortcut: toute modification de l'arbre invalide tous les conteneurs (pas seulement ceux touchés) ; à affiner si les éditions deviennent fréquentes.
    private Cells cells(SpaceObject container) {
        Cells c = cache.get(container.getUuid());
        if (c == null || c.mods != layer.modCount()) cache.put(container.getUuid(), c = build(container));
        return c;
    }

    private Cells build(SpaceObject container) {
        List<Entry> all = MajorContainer.collect(layer.tree(), container);
        if (all.size() > MajorParams.MAX_STARS) {
            all.sort(Comparator.comparingDouble(Entry::lum).reversed());
            all = new ArrayList<>(all.subList(0, MajorParams.MAX_STARS));
        }
        Map<CellKey, List<Entry>> stars = new LinkedHashMap<>();
        for (Entry e : all) stars.computeIfAbsent(new CellKey(container.getUuid(), MajorParams.cell(e.x()), MajorParams.cell(e.y()), MajorParams.cell(e.z())), k -> new ArrayList<>()).add(e);
        Map<CellKey, Integer> hashes = new HashMap<>();
        stars.forEach((k, v) -> hashes.put(k, v.hashCode()));
        return new Cells(layer.modCount(), stars, hashes);
    }

    static void chunks(CellKey k, List<Entry> stars, Consumer<CustomPacketPayload> out) {
        double ox = k.cx * MajorParams.CELL_KM, oy = k.cy * MajorParams.CELL_KM, oz = k.cz * MajorParams.CELL_KM;
        for (int i = 0; i < stars.size(); i += MajorParams.MAX_CHUNK_STARS) {
            List<Star> rows = new ArrayList<>();
            for (Entry s : stars.subList(i, Math.min(stars.size(), i + MajorParams.MAX_CHUNK_STARS)))
                rows.add(new Star(s.uuid(), (float) (s.x() - ox), (float) (s.y() - oy), (float) (s.z() - oz), s.color(), s.lum()));
            out.accept(new SpaceNet.MajorChunk(k.parent, k.cx, k.cy, k.cz, i == 0, rows));
        }
    }
}
