package fr.systamynna.spacemod.core.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/** Assets (textures PNG) possédés par le serveur, dans {@code <monde>/spacemod/assets/}. Lus à la demande : rien n'est envoyé tant qu'un client ne les demande pas. */
public final class RenderAssets {
    /** Nom d'asset valide : pas de remontée de dossier, pas de chemin absolu. */
    public static final Pattern NAME = Pattern.compile("[a-z0-9_\\-]+(/[a-z0-9_\\-]+)*\\.png");
    /** Un payload clientbound est limité à 1 MiB. */
    // shortcut: pas de découpage en fragments, un asset > ~1 MiB est refusé ; à fragmenter si les textures le dépassent.
    public static final int MAX_BYTES = 1_000_000;

    public record Asset(String hash, byte[] bytes) {}

    private Path dir;

    public void start(Path world) { dir = world.resolve("spacemod").resolve("assets"); }

    public void stop() { dir = null; }

    public Path dir() { return dir; }

    /** {@code null} si le nom est invalide, le fichier absent, illisible ou trop gros. */
    @Nullable
    public Asset read(String name) {
        if (dir == null || !NAME.matcher(name).matches()) return null;
        Path f = dir.resolve(name).normalize();
        if (!f.startsWith(dir)) return null;
        try {
            if (!Files.isRegularFile(f) || Files.size(f) > MAX_BYTES) return null;
            byte[] b = Files.readAllBytes(f);
            return new Asset(hash(b), b);
        } catch (IOException e) {
            return null;
        }
    }

    public static String hash(byte[] b) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(b));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-1 est garanti par la plateforme
        }
    }
}
