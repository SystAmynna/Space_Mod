package fr.systamynna.spacemod.spaceprovider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import fr.systamynna.spacemod.strategic.Archetypes;
import fr.systamynna.spacemod.strategic.SpaceObject;
import fr.systamynna.spacemod.strategic.component.ExclusionVolume;
import fr.systamynna.spacemod.strategic.component.Frame;
import fr.systamynna.spacemod.strategic.component.Identity;
import fr.systamynna.spacemod.strategic.component.RailFollower;
import fr.systamynna.spacemod.strategic.component.RailFollower.Orbit;
import fr.systamynna.spacemod.strategic.component.RailFollower.Spin;
import fr.systamynna.spacemod.strategic.component.Render;
import fr.systamynna.spacemod.strategic.component.StellarSystem;
import fr.systamynna.spacemod.strategic.component.Transform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Passage entre le JSON des sources et les objets de l'espace.
 *
 * <pre>{@code
 * {"id": "sol/earth", "parent": "sol", "archetype": "planet", "name": "Terre",
 *  "position": [x, y, z],                                   // mètres, optionnel
 *  "orbit": {"period": ticks, "radius": m, "phase": ticks, "inclination": rad},
 *  "spin": {"period": ticks, "phase": ticks},
 *  "render": {"model": "ns:path", "radius": m}, "exclusion_radius": m, "main_star": "sol/sun"}
 * }</pre>
 */
public final class SpaceObjectJson {
    /** Classe utilitaire. */
    private SpaceObjectJson() {}

    /**
     * Lit une entrée.
     *
     * @throws com.google.gson.JsonParseException si un champ requis manque, est mal typé, ou si l'archétype est inconnu
     * @throws IllegalArgumentException si une période est nulle ou négative
     */
    public static ProviderEntry fromJson(JsonObject json) {
        String archetype = GsonHelper.getAsString(json, "archetype");
        if (!java.util.List.of(Archetypes.GALAXY, Archetypes.SYSTEM, Archetypes.STAR, Archetypes.PLANET).contains(archetype)) {
            throw new com.google.gson.JsonParseException("archétype non fourni par les sources : " + archetype);
        }
        Transform transform = Transform.ORIGIN;
        if (json.has("position")) {
            JsonArray p = GsonHelper.getAsJsonArray(json, "position");
            if (p.size() != 3) throw new com.google.gson.JsonParseException("position : 3 nombres attendus");
            transform = Transform.at(p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble());
        }
        Orbit orbit = null;
        if (json.has("orbit")) {
            JsonObject o = GsonHelper.getAsJsonObject(json, "orbit");
            orbit = new Orbit(GsonHelper.getAsLong(o, "period"), GsonHelper.getAsDouble(o, "radius"),
                    GsonHelper.getAsLong(o, "phase", 0), GsonHelper.getAsDouble(o, "inclination", 0));
        }
        Spin spin = null;
        if (json.has("spin")) {
            JsonObject s = GsonHelper.getAsJsonObject(json, "spin");
            spin = new Spin(GsonHelper.getAsLong(s, "period"), GsonHelper.getAsLong(s, "phase", 0));
        }
        Render render = null;
        if (json.has("render")) {
            JsonObject r = GsonHelper.getAsJsonObject(json, "render");
            render = new Render(ResourceLocation.parse(GsonHelper.getAsString(r, "model")), GsonHelper.getAsDouble(r, "radius"));
        }
        return new ProviderEntry(GsonHelper.getAsString(json, "id"), GsonHelper.getAsString(json, "parent", null), archetype,
                GsonHelper.getAsString(json, "name"), transform, orbit == null && spin == null ? null : new RailFollower(orbit, spin),
                render, json.has("exclusion_radius") ? GsonHelper.getAsDouble(json, "exclusion_radius") : null,
                GsonHelper.getAsString(json, "main_star", null));
    }

    /** Écrit une entrée ; {@link #fromJson} relit le même contenu. */
    public static JsonObject toJson(ProviderEntry e) {
        JsonObject json = new JsonObject();
        json.addProperty("id", e.id());
        if (e.parent() != null) json.addProperty("parent", e.parent());
        json.addProperty("archetype", e.archetype());
        json.addProperty("name", e.name());
        Transform t = e.transform();
        JsonArray position = new JsonArray();
        position.add(t.tx());
        position.add(t.ty());
        position.add(t.tz());
        json.add("position", position);
        RailFollower rail = e.rail();
        if (rail != null && rail.orbit() != null) {
            JsonObject o = new JsonObject();
            o.addProperty("period", rail.orbit().period());
            o.addProperty("radius", rail.orbit().radius());
            o.addProperty("phase", rail.orbit().phaseTicks());
            o.addProperty("inclination", rail.orbit().inclination());
            json.add("orbit", o);
        }
        if (rail != null && rail.spin() != null) {
            JsonObject s = new JsonObject();
            s.addProperty("period", rail.spin().period());
            s.addProperty("phase", rail.spin().phaseTicks());
            json.add("spin", s);
        }
        if (e.render() != null) {
            JsonObject r = new JsonObject();
            r.addProperty("model", e.render().model().toString());
            r.addProperty("radius", e.render().apparentRadius());
            json.add("render", r);
        }
        if (e.exclusionRadius() != null) json.addProperty("exclusion_radius", e.exclusionRadius());
        if (e.mainStar() != null) json.addProperty("main_star", e.mainStar());
        return json;
    }

    /** Nouvel objet, avec l'UUID stable de l'entrée ; ni parent ni dimension (ajouté ensuite par la couche). */
    public static SpaceObject toObject(ProviderEntry e) {
        SpaceObject object = new SpaceObject(e.uuid());
        boolean galaxy = e.archetype().equals(Archetypes.GALAXY);
        boolean system = e.archetype().equals(Archetypes.SYSTEM);
        if (galaxy || system) object.put(new Frame());
        if (system) object.put(new StellarSystem(null));
        update(object, e);
        return object;
    }

    /**
     * Applique à un objet existant les données générales de l'entrée (nom, position, rail, rendu, étoile principale).
     * Ne touche ni à la dimension hébergée, ni aux ports, volumes logiques ou état de destruction, ni au parent.
     */
    public static void update(SpaceObject object, ProviderEntry e) {
        object.put(new Identity(e.name(), e.archetype())).put(e.transform()).controller(e.rail());
        if (e.render() != null) {
            object.put(e.render());
            if (e.archetype().equals(Archetypes.STAR)) {
                object.put(new ExclusionVolume(exclusion(e)));
            }
        }
        if (e.mainStar() != null && e.archetype().equals(Archetypes.SYSTEM)) {
            object.put(new StellarSystem(ProviderEntry.uuid(e.mainStar())));
        }
    }

    private static double exclusion(ProviderEntry e) {
        @Nullable Double radius = e.exclusionRadius();
        return radius != null ? radius : 2 * e.render().apparentRadius();
    }
}
