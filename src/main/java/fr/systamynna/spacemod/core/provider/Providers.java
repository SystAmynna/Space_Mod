package fr.systamynna.spacemod.core.provider;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.core.SpaceObject;
import fr.systamynna.spacemod.core.SpaceObjectException;
import fr.systamynna.spacemod.core.StrategicLayer;
import fr.systamynna.spacemod.core.StrategicLayer.UpsertResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/** Alimente la base d'objets depuis le provider choisi. */
public final class Providers {
    private Providers() {}

    /** Ressources DEFAULT, dans l'ordre d'import (un parent doit exister avant ses enfants, même d'un fichier à l'autre). */
    private static final List<String> DEFAULT_RESOURCES = Stream.of("galaxies", "solar_system", "nearby_stars", "compact_objects")
            .map(n -> "/data/" + AmynnaSpaceMod.MODID + "/default/" + n + ".json").toList();

    /** Bilan d'un refresh : adresses créées/modifiées, nombre d'inchangés, erreurs (clé de traduction + arguments). */
    public record Report(List<String> created, List<String> updated, int unchanged, List<SpaceObjectException> errors) {
        static Report error(SpaceObjectException e) { return new Report(List.of(), List.of(), 0, List.of(e)); }

        Report plus(Report o) {
            return new Report(Stream.concat(created.stream(), o.created.stream()).toList(), Stream.concat(updated.stream(), o.updated.stream()).toList(),
                    unchanged + o.unchanged, Stream.concat(errors.stream(), o.errors.stream()).toList());
        }
    }

    public static Report refresh(StrategicLayer layer, ProviderType type) {
        return switch (type) {
            case NONE -> new Report(List.of(), List.of(), 0, List.of());
            case DEFAULT -> DEFAULT_RESOURCES.stream().map(r -> importResource(layer, r)).reduce(Report::plus).orElseThrow();
            case DATAPACK, WEB -> Report.error(new SpaceObjectException("amynnaspacemod.provider.not_implemented", type.name()));
        };
    }

    /**
     * Importe un tableau JSON d'objets : payload de {@link SpaceObject#save} + {@code address} ("universe/sol/earth").
     * Parents avant enfants ; une entrée invalide est signalée et n'arrête pas l'import.
     */
    private static Report importResource(StrategicLayer layer, String path) {
        List<String> created = new ArrayList<>(), updated = new ArrayList<>();
        List<SpaceObjectException> errors = new ArrayList<>();
        int unchanged = 0;
        try (InputStream in = Providers.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("missing resource " + path);
            Reader r = new InputStreamReader(in, StandardCharsets.UTF_8);
            for (JsonElement e : JsonParser.parseReader(r).getAsJsonArray()) {
                String address = "?";
                try {
                    Tag tag = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, e);
                    if (!(tag instanceof CompoundTag data) || !data.contains("address")) throw new IllegalArgumentException("no address");
                    address = data.getString("address");
                    data.remove("address");
                    int cut = address.lastIndexOf('/');
                    SpaceObject parent = cut < 0 ? null : layer.tree().resolve(address.substring(0, cut));
                    UpsertResult res = layer.upsert(parent, address.substring(cut + 1), data);
                    switch (res) {
                        case CREATED -> created.add(address);
                        case UPDATED -> updated.add(address);
                        case UNCHANGED -> unchanged++;
                    }
                } catch (SpaceObjectException ex) {
                    errors.add(ex);
                } catch (RuntimeException ex) { // JSON mal formé pour cette entrée
                    errors.add(new SpaceObjectException("amynnaspacemod.provider.bad_entry", address, String.valueOf(ex.getMessage())));
                }
            }
        } catch (IOException | RuntimeException ex) {
            errors.add(new SpaceObjectException("amynnaspacemod.provider.bad_resource", path, String.valueOf(ex.getMessage())));
        }
        layer.flush();
        return new Report(created, updated, unchanged, errors);
    }
}
