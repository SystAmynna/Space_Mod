package fr.systamynna.spacemod.spacetest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.joml.Quaterniond;
import org.joml.Vector3d;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Test A9 du plan : transfert d'un sub-level entre dimensions. Mécanisme 1 : copie de blocs en trois passes ;
 * mécanisme 3 : sérialisation et rechargement ; mécanisme 2 (demande n° 1474 de Sable) : non exécuté, en attente de
 * l'accord de l'utilisateur et de Docker. Lancé par -Pa9=1 ; écrit test-results/A9/a9-run.log.
 */
final class A9Runner {
    private static final ResourceLocation A = ResourceLocation.fromNamespaceAndPath(SpaceTestMod.MODID, "a9a");
    private static final ResourceLocation B = ResourceLocation.fromNamespaceAndPath(SpaceTestMod.MODID, "a9b");
    private static final ResourceLocation MID = ResourceLocation.fromNamespaceAndPath(SpaceTestMod.MODID, "mid_a9");
    private static final ResourceLocation SHORT = ResourceLocation.fromNamespaceAndPath(SpaceTestMod.MODID, "short_a9");
    private static final Path DIR = Path.of(System.getProperty("spacetest.results", "../test-results"), "A9");
    private static final int REPEAT = 20;

    private record Step(int delayAfter, Runnable run) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static final Map<String, List<Double>> TIMES = new HashMap<>();
    private static MinecraftServer server;
    private static ServerLevel a, b, shortLevel, midLevel;
    /** Si vrai, le mécanisme 3 ne retire pas la source : l'appelant le fait (A8 : après le changement de dimension du joueur). */
    static boolean keepSource;
    private static ServerSubLevel pendingBody;
    private static Vector3d pendingVelocity;
    private static boolean pendingReset;
    private static ServerSubLevel current;
    private static ServerSubLevel diagMoved;
    private static SubLevelData diagData;

    private static String tree(net.minecraft.nbt.Tag t, int depth) {
        if (!(t instanceof CompoundTag c)) return t instanceof net.minecraft.nbt.ListTag l ? "[" + l.size() + (l.isEmpty() ? "" : " × " + tree(l.get(0), depth + 1)) + "]" : t.getClass().getSimpleName();
        if (depth > 4) return "{…}";
        StringBuilder sb = new StringBuilder("{");
        for (String k : c.getAllKeys()) sb.append(k).append(':').append(tree(c.get(k), depth + 1)).append(' ');
        return sb.append('}').toString();
    }
    private static ServerLevel currentLevel;
    private static String before;
    private static int next = -1;
    private static int wait;
    private static int fails;

    private A9Runner() {}

    static void registerPhysics() {
        for (ResourceLocation id : List.of(A, B, SHORT, MID)) {
            DimensionPhysicsData.putPhysics(A1Runner.levelKey(id), new DimensionPhysics(id, 0,
                Optional.of(0f), Optional.of(new org.joml.Vector3f(0, 0, 0)), Optional.of(0.0), Optional.empty(), Optional.empty(), false));
        }
    }

    static void start(MinecraftServer s) {
        server = s;
        try {
            Files.createDirectories(DIR);
            Files.writeString(DIR.resolve("a9-run.log"), "");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        a = DimDispatch.create(s, A);
        b = DimDispatch.create(s, B);
        shortLevel = DimDispatch.create(s, SHORT);
        midLevel = DimDispatch.create(s, MID);
        log("DÉBUT A9 ; mécanisme 2 (demande n° 1474) : BLOQUÉ, accord de l'utilisateur et Docker requis");

        if (Boolean.getBoolean("spacetest.a9.diag")) {
            STEPS.add(new Step(10, () -> { currentLevel = a; current = make(a, false); before = describe(current); }));
            STEPS.add(new Step(0, () -> { log("diag arbre NBT : " + tree(SubLevelSerializer.toData(current, List.of()).fullTag(), 0)); }));
            STEPS.add(new Step(0, () -> { diagData = SubLevelSerializer.toData(current, List.of()); log("diag entités avant (A) : " + describe(current)); }));
            STEPS.add(new Step(0, () -> { diagMoved = serializeReload(current, a, b); log("diag +0 tick (B) : " + describe(diagMoved)); }));
            for (int k : new int[] { 1, 5, 20 }) {
                STEPS.add(new Step(k == 1 ? 1 : (k == 5 ? 4 : 15), () -> log("diag +" + k + " ticks (B) : " + describe(diagMoved))));
            }
            STEPS.add(new Step(5, () -> remove(b, diagMoved)));
        }
        for (boolean large : new boolean[] { false, true }) {
            String size = large ? "L" : "S";
            for (int mech : new int[] { 1, 3 }) {
                String key = "méca " + mech + " " + size;
                STEPS.add(new Step(10, () -> { currentLevel = a; current = make(a, large); before = describe(current); }));
                STEPS.add(new Step(10, A9Runner::kick));
                STEPS.add(new Step(10, () -> {}));
                for (int i = 0; i < REPEAT; i++) {
                    final boolean toB = i % 2 == 0;
                    STEPS.add(new Step(6, () -> transfer(mech, key, toB ? b : a, i0(key))));
                }
                STEPS.add(new Step(6, () -> verify(key)));
                STEPS.add(new Step(4, () -> remove(currentLevel, current)));
            }
        }
        STEPS.add(new Step(6, A9Runner::heightTest));
        STEPS.add(new Step(6, A9Runner::failureTest));
        STEPS.add(new Step(6, A9Runner::finish));
        next = 0;
        wait = 5;
    }

    private static int i0(String k) { return TIMES.getOrDefault(k, List.of()).size(); }

    static void tick() {
        if (next < 0 || next >= STEPS.size()) return;
        if (wait-- > 0) return;
        if (pendingBody != null && !pendingBody.isRemoved()) {
            if (pendingReset) SubLevelPhysicsSystem.get(pendingBody.getLevel()).getPipeline().resetVelocity(pendingBody);
            RigidBodyHandle.of(pendingBody).applyLinearImpulse(new Vector3d(pendingVelocity).mul(pendingBody.getMassTracker().getMass()));
        }
        pendingBody = null;
        pendingReset = false;
        Step st = STEPS.get(next++);
        try {
            st.run().run();
        } catch (Throwable t) {
            fail("exception à l'étape " + next + " : " + t);
            SpaceTestMod.LOGGER.error("A9", t);
            server.halt(false);
        }
        wait = st.delayAfter();
    }

    // --- préparation ----------------------------------------------------------------------------

    private static void purgeStands() {
        for (ServerLevel l : List.of(a, b, shortLevel, midLevel)) {
            List<Entity> all = new ArrayList<>();
            l.getAllEntities().forEach(all::add);
            for (Entity e : all) if (e instanceof ArmorStand) e.discard();
        }
    }

    private static ServerSubLevel make(ServerLevel level, boolean large) {
        purgeStands();
        BlockPos origin = new BlockPos(0, 64, 0);
        ServerSubLevel s = large ? A4Runner.assembleLarge(level, origin) : A4Runner.assembleSmall(level, origin);
        A4Runner.spawnStands(level, s, 1);
        CompoundTag user = new CompoundTag();
        user.putInt("spacemod_test", 9);
        s.setUserDataTag(user);
        return s;
    }

    private static void kick() {
        RigidBodyHandle.of(current).applyLinearImpulse(new Vector3d(current.getMassTracker().getMass() * 2, 0, 0));
    }

    private static String describe(ServerSubLevel s) {
        BoundingBox3ic bb = s.getPlot().getBoundingBox();
        AABB box = AABB.encapsulatingFullBlocks(new BlockPos(bb.minX(), bb.minY(), bb.minZ()), new BlockPos(bb.maxX(), bb.maxY(), bb.maxZ())).inflate(2);
        int stands = entitiesOf(s).size();
        return A2Runner.fingerprint(s.getLevel(), s) + ",entités=" + stands + ",user_data=" + s.getUserDataTag();
    }

    // --- transferts -----------------------------------------------------------------------------

    private static void transfer(int mech, String key, ServerLevel dest, int index) {
        ServerLevel src = currentLevel;
        Vector3d velBefore = new Vector3d(RigidBodyHandle.of(current).getLinearVelocity());
        UUID uuid = current.getUniqueId();
        long t0 = System.nanoTime();
        ServerSubLevel moved = mech == 1 ? copyBlocks(current, src, dest) : serializeReload(current, src, dest);
        double ms = (System.nanoTime() - t0) / 1e6;
        TIMES.computeIfAbsent(key, k -> new ArrayList<>()).add(ms);
        if (moved == null) {
            fail("[" + key + "] transfert #" + index + " : résultat null, source conservée=" + (SubLevelContainer.getContainer(src).getSubLevel(uuid) != null));
            return;
        }
        current = moved;
        currentLevel = dest;
        if (index == 0 || index == REPEAT - 1) {
            log(String.format("[%s] transfert #%d : %.2f ms, identifiant conservé=%b, vitesse avant=%s", key, index, ms, moved.getUniqueId().equals(uuid), A1Runner.fmt(velBefore)));
        }
    }

    /** Mécanisme 1 : trois passes (blocs sans effets de bord, block entities, entités), puis assemblage dans la destination. */
    private static ServerSubLevel copyBlocks(ServerSubLevel src, ServerLevel from, ServerLevel to) {
        BoundingBox3ic bb = src.getPlot().getBoundingBox();
        BlockPos origin = new BlockPos(0, 64, 0);
        Vector3d pose = new Vector3d(src.logicalPose().position());
        Vector3d vel = new Vector3d(RigidBodyHandle.of(src).getLinearVelocity());
        List<BlockPos> placed = new ArrayList<>();
        List<BlockPos[]> withEntity = new ArrayList<>();
        // passe 1 : blocs sans notification de voisins (drapeaux 2 | 16)
        for (int x = bb.minX(); x <= bb.maxX(); x++) for (int y = bb.minY(); y <= bb.maxY(); y++) for (int z = bb.minZ(); z <= bb.maxZ(); z++) {
            BlockPos p = new BlockPos(x, y, z);
            BlockState st = from.getBlockState(p);
            if (st.isAir()) continue;
            BlockPos dst = origin.offset(x - bb.minX(), y - bb.minY(), z - bb.minZ());
            to.setBlock(dst, st, 2 | 16);
            placed.add(dst);
            if (from.getBlockEntity(p) != null) withEntity.add(new BlockPos[] { p, dst });
        }
        // passe 2 : block entities (contenu des coffres)
        for (BlockPos[] pair : withEntity) {
            BlockEntity src2 = from.getBlockEntity(pair[0]);
            BlockEntity dst2 = to.getBlockEntity(pair[1]);
            if (src2 != null && dst2 != null) dst2.loadWithComponents(src2.saveWithFullMetadata(from.registryAccess()), to.registryAccess());
        }
        // passe 3 : entités du plot, puis assemblage
        AABB box = AABB.encapsulatingFullBlocks(new BlockPos(bb.minX(), bb.minY(), bb.minZ()), new BlockPos(bb.maxX(), bb.maxY(), bb.maxZ())).inflate(2);
        List<ArmorStand> stands = from.getEntitiesOfClass(ArmorStand.class, box);
        ServerSubLevel moved = SubLevelAssemblyHelper.assembleBlocks(to, origin, placed, BoundingBox3i.from(placed));
        SubLevelContainer.getContainer(to).addForceLoadTicket(moved, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
        for (ArmorStand st : stands) {
            ArmorStand copy = EntityType.ARMOR_STAND.create(to);
            CompoundTag tag = st.saveWithoutId(new CompoundTag());
            tag.remove("UUID");
            copy.load(tag);
            BoundingBox3ic nb = moved.getPlot().getBoundingBox();
            copy.setPos(nb.minX() + 1, nb.maxY() + 1, nb.minZ() + 1);
            to.addFreshEntity(copy);
        }
        if (src.getUserDataTag() != null) moved.setUserDataTag(src.getUserDataTag().copy());
        pendingReset = false;
        SubLevelPhysicsSystem.get(to).getPipeline().teleport(moved, pose, new Quaterniond(src.logicalPose().orientation()));
        pendingBody = moved;
        pendingVelocity = vel; // appliqué quelques ticks plus tard : une impulsion donnée au tick de création est ignorée
        SubLevelContainer.getContainer(from).removeSubLevel(src, SubLevelRemovalReason.REMOVED);
        stands.forEach(Entity::discard);
        return moved;
    }

    /**
     * Mécanisme 3 : sérialisation et rechargement dans la destination. Ordre sûr (par défaut) : charger la copie,
     * puis seulement retirer la source. Ordre initial (safe = false) : retirer puis charger, pour mesurer la perte en cas d'échec.
     */
    static ServerSubLevel serializeReload(ServerSubLevel src, ServerLevel from, ServerLevel to) {
        return serializeReload(src, from, to, !Boolean.getBoolean("spacetest.a9.unsafe"));
    }

    private static ServerSubLevel serializeReload(ServerSubLevel src, ServerLevel from, ServerLevel to, boolean safe) {
        if (to != null) {
            BoundingBox3ic bb = src.getPlot().getBoundingBox();
            // les chunks du plot sont à Y fixe (~130) : refuser proprement une dimension qui ne les contient pas
            // et un min_y différent décale les sections : blocs conservés mais contenu des coffres perdu (constaté, essai -64..320 vers 0..256)
            if (to.getMinBuildHeight() != from.getMinBuildHeight() || to.isOutsideBuildHeight(bb.minY()) || to.isOutsideBuildHeight(bb.maxY())) {
                log("refus : plage verticale de " + to.dimension().location() + " (" + to.getMinBuildHeight() + ".." + to.getMaxBuildHeight() + ") incompatible avec la source (" + from.getMinBuildHeight() + ".." + from.getMaxBuildHeight() + ", plot Y " + bb.minY() + ".." + bb.maxY() + ")");
                return null;
            }
        }
        // Sable ne sérialise pas les entités du plot (SubLevelData n'a que chunks, pose, bornes, user_data, uuid) :
        // on les range dans user_data, positions relatives au coin du plot, et on les recrée à l'arrivée.
        List<Entity> inPlot = entitiesOf(src);
        ListTag saved = new ListTag();
        BoundingBox3ic sb = src.getPlot().getBoundingBox();
        for (Entity e : inPlot) {
            CompoundTag t = e.saveWithoutId(new CompoundTag());
            t.putString("spacemod_type", EntityType.getKey(e.getType()).toString());
            t.putDouble("spacemod_rx", e.getX() - sb.minX());
            t.putDouble("spacemod_ry", e.getY() - sb.minY());
            t.putDouble("spacemod_rz", e.getZ() - sb.minZ());
            saved.add(t);
        }
        // getUserDataTag() est null tant qu'aucune donnée n'a été posée (cas d'une Mini créée sans user_data)
        CompoundTag originalUserData = src.getUserDataTag() == null ? null : src.getUserDataTag().copy();
        CompoundTag ud = originalUserData == null ? new CompoundTag() : originalUserData.copy();
        ud.put("spacemod_entities", saved);
        src.setUserDataTag(ud);
        Vector3d vel = new Vector3d(RigidBodyHandle.of(src).getLinearVelocity());

        SubLevelData data = SubLevelSerializer.toData(src, List.of());
        ServerSubLevel moved;
        if (safe) {
            moved = SubLevelSerializer.fullyLoad(to, SubLevelSerializer.fromData(data.fullTag()));
            if (moved != null && !keepSource) SubLevelContainer.getContainer(from).removeSubLevel(src, SubLevelRemovalReason.REMOVED);
        } else {
            SubLevelContainer.getContainer(from).removeSubLevel(src, SubLevelRemovalReason.REMOVED);
            moved = SubLevelSerializer.fullyLoad(to, SubLevelSerializer.fromData(data.fullTag()));
        }
        if (moved == null) {
            if (originalUserData != null) src.setUserDataTag(originalUserData); // source inchangée pour l'appelant
            return null;
        }
        SubLevelContainer.getContainer(to).addForceLoadTicket(moved, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
        inPlot.forEach(Entity::discard);
        BoundingBox3ic nb = moved.getPlot().getBoundingBox();
        CompoundTag clean = moved.getUserDataTag() == null ? new CompoundTag() : moved.getUserDataTag().copy();
        for (net.minecraft.nbt.Tag t : clean.getList("spacemod_entities", 10)) {
            CompoundTag tag = ((CompoundTag) t).copy();
            tag.remove("UUID"); // nouvel UUID : un doublon serait refusé tant que l'ancienne entité n'est pas purgée
            Optional<EntityType<?>> type = EntityType.byString(tag.getString("spacemod_type"));
            if (type.isEmpty()) continue;
            Entity e = type.get().create(to);
            if (e == null) continue;
            e.load(tag);
            e.setPos(nb.minX() + tag.getDouble("spacemod_rx"), nb.minY() + tag.getDouble("spacemod_ry"), nb.minZ() + tag.getDouble("spacemod_rz"));
            to.addFreshEntity(e);
        }
        clean.remove("spacemod_entities");
        moved.setUserDataTag(clean);
        // Sable multiplie la vitesse par 0,9 au rechargement : on la restitue quelques ticks plus tard
        pendingBody = moved;
        pendingVelocity = vel;
        pendingReset = true;
        return moved;
    }

    private static List<Entity> entitiesOf(ServerSubLevel s) {
        BoundingBox3ic bb = s.getPlot().getBoundingBox();
        AABB box = AABB.encapsulatingFullBlocks(new BlockPos(bb.minX(), bb.minY(), bb.minZ()), new BlockPos(bb.maxX(), bb.maxY(), bb.maxZ())).inflate(1);
        return new ArrayList<>(s.getLevel().getEntitiesOfClass(Entity.class, box, e -> !(e instanceof net.minecraft.world.entity.player.Player)));
    }

    private static void verify(String key) {
        String after = describe(current);
        Vector3d v = new Vector3d(RigidBodyHandle.of(current).getLinearVelocity());
        boolean same = after.equals(before);
        log("[" + key + "] après " + REPEAT + " aller-retours : " + after + " v=" + A1Runner.fmt(v) + " -> " + (same ? "contenu identique" : "CONTENU DIFFÉRENT (avant : " + before + ")"));
        if (!same) fail("[" + key + "] contenu modifié par les transferts");
        List<Double> t = TIMES.get(key);
        List<Double> sorted = new ArrayList<>(t);
        sorted.sort(Double::compare);
        double p50 = sorted.get((int) Math.ceil(0.5 * sorted.size()) - 1), p95 = sorted.get((int) Math.ceil(0.95 * sorted.size()) - 1);
        log(String.format("[%s] durée sur le fil serveur : p50=%.2f ms p95=%.2f ms (max %.2f)", key, p50, p95, sorted.get(sorted.size() - 1)));
    }

    private static void remove(ServerLevel level, ServerSubLevel s) {
        s.deleteAllEntities(); // sinon les armor stands du plot survivent au retrait et faussent le test suivant
        SubLevelContainer.getContainer(level).removeSubLevel(s, SubLevelRemovalReason.REMOVED);
    }

    private static void heightTest() {
        for (ServerLevel dest : List.of(shortLevel, midLevel)) {
            for (int mech : new int[] { 3, 1 }) {
                boolean expectAccept = mech == 1; // méca 3 doit refuser une plage différente ; méca 1 reconstruit les blocs et accepte
                ServerSubLevel s = make(a, false);
                UUID id = s.getUniqueId();
                String ref = describe(s);
                String label = "hauteur -64..320 vers " + dest.getMinBuildHeight() + ".." + dest.getMaxBuildHeight() + ", méca " + mech;
                try {
                    ServerSubLevel moved = mech == 1 ? copyBlocks(s, a, dest) : serializeReload(s, a, dest);
                    boolean kept = SubLevelContainer.getContainer(a).getSubLevel(id) != null;
                    if (moved == null) {
                        log(label + " : transfert refusé, source conservée=" + kept + (expectAccept ? " -> INATTENDU" : " -> refus propre attendu"));
                        if (expectAccept || !kept) fail(label + " : refus inattendu ou source perdue");
                        if (kept) remove(a, s);
                    } else {
                        String after = describe(moved);
                        boolean same = after.equals(ref);
                        log(label + " : accepté, " + after + (same ? " -> identique" : " -> DIFFÉRENT (avant : " + ref + ")"));
                        if (!same || !expectAccept) fail(label + " : " + (same ? "accepté alors qu'un refus était attendu" : "contenu différent"));
                        remove(dest, moved);
                    }
                } catch (Throwable t) {
                    boolean kept = SubLevelContainer.getContainer(a).getSubLevel(id) != null;
                    fail(label + " : exception " + t + " ; source conservée=" + kept);
                    if (kept) remove(a, s);
                }
            }
        }
    }

    private static void failureTest() {
        for (boolean safe : new boolean[] { true, false }) {
            ServerSubLevel s = make(a, false);
            UUID id = s.getUniqueId();
            try {
                serializeReload(s, a, null, safe); // destination absente
                log("échec provoqué (ordre " + (safe ? "sûr" : "initial") + ") : aucune exception");
            } catch (Throwable t) {
                boolean kept = SubLevelContainer.getContainer(a).getSubLevel(id) != null;
                log("échec provoqué, destination absente, ordre " + (safe ? "« charger puis retirer »" : "« retirer puis charger »") + " : exception "
                    + t.getClass().getSimpleName() + ", source conservée=" + kept);
                if (safe && !kept) fail("ordre sûr : source perdue");
                if (kept) remove(a, s);
            }
        }
    }

    private static void finish() {
        log("mécanisme 2 (demande n° 1474) : BLOQUÉ (accord et Docker requis)");
        log("FIN A9 : " + (fails == 0 ? "RÉUSSI" : "ÉCHEC/PARTIEL (" + fails + ")"));
        server.halt(false);
    }

    private static void fail(String m) {
        fails++;
        log("ÉCHEC : " + m);
    }

    private static void log(String line) {
        SpaceTestMod.LOGGER.info("[A9] {}", line);
        try {
            Files.writeString(DIR.resolve("a9-run.log"), line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            SpaceTestMod.LOGGER.error("[A9] log", e);
        }
    }
}
