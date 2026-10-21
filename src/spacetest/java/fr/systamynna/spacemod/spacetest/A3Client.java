package fr.systamynna.spacemod.spacetest;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.joml.Quaterniondc;
import org.joml.Vector3dc;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Journal client du test A3 : à chaque image, pose affichée du premier sub-level (position, orientation) et
 * position du joueur. Écrit test-results/A3/client-frames.csv. Chargé uniquement côté client.
 */
final class A3Client {
    private static final Path FILE = Path.of(System.getProperty("spacetest.results", "../test-results"), "A3", "client-frames.csv");
    private static BufferedWriter out;
    private static long frames;

    private A3Client() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post e) -> frame(e.getPartialTick().getGameTimeDeltaPartialTick(false)));
    }

    private static void frame(float partial) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        ClientSubLevelContainer c = SubLevelContainer.getContainer(level);
        if (c == null || c.getAllSubLevels().isEmpty()) return;
        try {
            if (out == null) {
                Files.createDirectories(FILE.getParent());
                out = Files.newBufferedWriter(FILE);
                out.write("t_ns,partial,sx,sy,sz,qx,qy,qz,qw,px,py,pz,suivi\n");
            }
            ClientSubLevel s = c.getAllSubLevels().get(0);
            Pose3dc pose = s.renderPose(partial);
            Vector3dc p = pose.position();
            Quaterniondc q = pose.orientation();
            var pl = mc.player;
            out.write(String.format(java.util.Locale.ROOT, "%d,%.3f,%.5f,%.5f,%.5f,%.6f,%.6f,%.6f,%.6f,%.5f,%.5f,%.5f,%d%n",
                System.nanoTime(), partial, p.x(), p.y(), p.z(), q.x(), q.y(), q.z(), q.w(),
                pl.getX(), pl.getY(), pl.getZ(), Sable.HELPER.getTrackingSubLevel(pl) != null ? 1 : 0));
            if (++frames % 120 == 0) out.flush();
        } catch (IOException e) {
            SpaceTestMod.LOGGER.error("[A3] journal client", e);
        }
    }
}
