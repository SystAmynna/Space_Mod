package fr.systamynna.spacemod.core.sky;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.joml.Vector3d;

import fr.systamynna.spacemod.core.SpaceNet;
import fr.systamynna.spacemod.core.SpaceNet.Star;
import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.StrategicLayer;
import fr.systamynna.spacemod.core.relevance.PlayerSpacePos;
import fr.systamynna.spacemod.core.render.RenderMath;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Réplique aux joueurs les résumés de ciel (étoiles lointaines) visibles depuis leur position, par cellules de l'index spatial du store ;
 * ne charge aucun objet. Repère = le plus proche ancêtre de l'ancre qui a des résumés visibles ; une étoile est retenue si {@code lum / d² >= FLUX_LIMIT}.
 */
public final class SkyStreamer {
    public record CellKey(UUID parent, int cx, int cy, int cz) {}

    private final StrategicLayer layer;
    /** Joueur → cellules envoyées et leur nombre d'étoiles. */
    private final Map<UUID, Map<CellKey, Integer>> sent = new HashMap<>();

    public SkyStreamer(StrategicLayer layer) { this.layer = layer; }

    /** Recalcule le ciel du joueur depuis {@code pos} ({@code null} : plus de ciel) et envoie la différence. */
    public void send(ServerPlayer p, @Nullable PlayerSpacePos pos) { update(p.getUUID(), pos, packet -> PacketDistributor.sendToPlayer(p, packet)); }

    // shortcut: une cellule déjà envoyée n'est renvoyée que si son nombre d'étoiles retenues change (pas de comparaison du contenu) ; à affiner si des étoiles bougent.
    void update(UUID player, @Nullable PlayerSpacePos pos, Consumer<CustomPacketPayload> out) {
        Map<CellKey, Integer> known = sent.get(player);
        if (known == null) out.accept(new SpaceNet.SkyReset());
        Map<CellKey, Integer> prev = known == null ? Map.of() : known;
        Map<CellKey, List<SkySummary>> next = select(pos);
        for (CellKey k : prev.keySet()) if (!next.containsKey(k)) out.accept(new SpaceNet.SkyDrop(k.parent, k.cx, k.cy, k.cz));
        Map<CellKey, Integer> now = new HashMap<>();
        next.forEach((k, stars) -> {
            now.put(k, stars.size());
            if (!Integer.valueOf(stars.size()).equals(prev.get(k))) chunks(k, stars, out);
        });
        sent.put(player, now);
    }

    public void logout(UUID player) { sent.remove(player); }

    public void clear() { sent.clear(); }

    /** Étoiles à répliquer, groupées par cellule. */
    private Map<CellKey, List<SkySummary>> select(@Nullable PlayerSpacePos pos) {
        if (pos == null) return Map.of();
        List<SpaceObject> chain;
        try {
            chain = RenderMath.chain(layer::peek, layer.peek(pos.anchor()));
        } catch (SpaceObjectException e) { // ancre supprimée
            return Map.of();
        }
        Vector3d p = new Vector3d(pos.x(), pos.y(), pos.z());
        for (int i = 0; i < chain.size(); i++) {
            // shortcut: poses des ancêtres non avancées au temps courant (l'erreur de position est négligeable à l'échelle des années-lumière)
            List<SkySummary> stars = around(chain.get(i).getUuid(), RenderMath.toAncestor(chain, i).apply(p));
            if (stars.isEmpty()) continue;
            Map<CellKey, List<SkySummary>> out = new LinkedHashMap<>();
            for (SkySummary s : stars) out.computeIfAbsent(new CellKey(s.parent(), SkyParams.cell(s.x()), SkyParams.cell(s.y()), SkyParams.cell(s.z())), k -> new ArrayList<>()).add(s);
            return out;
        }
        return Map.of();
    }

    /**
     * Étoiles visibles depuis {@code o} (km, repère de {@code parent}), par boîtes de cellules emboîtées de rayon doublé : un résumé hors de la boîte
     * précédente est à au moins son rayon, donc n'est visible que s'il dépasse {@code FLUX_LIMIT * rayon²} (le {@code minLum} élague la requête).
     * shortcut: l'index ne couvre pas la luminosité, une boîte lit toutes les lignes de sa tranche ; à indexer/partitionner si le store dépasse ~1 M d'étoiles.
     */
    private List<SkySummary> around(UUID parent, Vector3d o) {
        int[] c = {SkyParams.cell(o.x), SkyParams.cell(o.y), SkyParams.cell(o.z)};
        int rMax = (int) Math.ceil(Math.sqrt(SkyParams.LUM_MAX / SkyParams.FLUX_LIMIT) / SkyParams.SKY_CELL_UNITS) + 1;
        Map<UUID, SkySummary> out = new LinkedHashMap<>();
        int prevR = 0;
        for (int r = 1;; r = Math.min(2 * r, rMax)) {
            double ly = prevR * (double) SkyParams.SKY_CELL_UNITS;
            int[] lo = {c[0] - r, c[1] - r, c[2] - r}, hi = {c[0] + r, c[1] + r, c[2] + r};
            for (SkySummary s : layer.store().skyInBox(parent, lo, hi, SkyParams.FLUX_LIMIT * ly * ly)) {
                double dx = s.x() - o.x, dy = s.y() - o.y, dz = s.z() - o.z;
                if (SkyParams.visible(s.lum(), dx * dx + dy * dy + dz * dz)) out.put(s.uuid(), s);
            }
            if (r == rMax) break;
            prevR = r;
        }
        return new ArrayList<>(out.values());
    }

    static void chunks(CellKey k, List<SkySummary> stars, Consumer<CustomPacketPayload> out) {
        double ox = k.cx * SkyParams.CELL_KM, oy = k.cy * SkyParams.CELL_KM, oz = k.cz * SkyParams.CELL_KM;
        for (int i = 0; i < stars.size(); i += SkyParams.MAX_CHUNK_STARS) {
            List<Star> rows = new ArrayList<>();
            for (SkySummary s : stars.subList(i, Math.min(stars.size(), i + SkyParams.MAX_CHUNK_STARS)))
                rows.add(new Star(s.uuid(), (float) (s.x() - ox), (float) (s.y() - oy), (float) (s.z() - oz), s.color(), s.lum()));
            out.accept(new SpaceNet.SkyChunk(k.parent, k.cx, k.cy, k.cz, i == 0, rows));
        }
    }
}
