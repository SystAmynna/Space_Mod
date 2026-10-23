package fr.systamynna.spacemod.ship;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import fr.systamynna.spacemod.spaceprovider.ProviderEntry;
import fr.systamynna.spacemod.strategic.Archetypes;
import fr.systamynna.spacemod.strategic.SpaceObject;
import fr.systamynna.spacemod.strategic.StrategicLayer;
import fr.systamynna.spacemod.strategic.component.ComponentTypes;

/**
 * Noms des vaisseaux. Un vaisseau n'a pas de chemin dans l'arbre : son nom est simple et unique parmi les vaisseaux (sans
 * tenir compte de la casse). L'identifiant de l'objet en découle, donc la recherche par nom est un {@code find} qui marche
 * même si le système du vaisseau est déchargé ; seul l'index des noms (complétion, redémarrage) est gardé dans les
 * métadonnées du monde.
 */
public final class ShipRegistry {
    /** Clé de métadonnée de l'index : les noms, un par ligne. */
    private static final String META_KEY = "ships";
    /** Forme d'un nom : un mot Brigadier ({@code StringArgumentType.word()}), 48 caractères au plus. */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.+-]{1,48}");

    /** Classe utilitaire. */
    private ShipRegistry() {}

    /** {@code true} si le nom est acceptable : lettres, chiffres et {@code _ . + -}, de 1 à 48 caractères. */
    public static boolean validName(String name) {
        return NAME.matcher(name).matches();
    }

    /** Identifiant déterministe du vaisseau de ce nom. */
    public static UUID uuid(String name) {
        return ProviderEntry.uuid("ship:" + name.toLowerCase(Locale.ROOT));
    }

    /** Le vaisseau de ce nom, chargé au besoin, ou {@code null}. */
    @Nullable
    public static SpaceObject find(StrategicLayer layer, String name) {
        SpaceObject object = layer.find(uuid(name));
        return object != null && isShip(object) ? object : null;
    }

    /** {@code true} si l'objet est un vaisseau ou une navette. */
    public static boolean isShip(SpaceObject object) {
        String archetype = object.get(ComponentTypes.IDENTITY).archetype();
        return archetype.equals(Archetypes.SHIP) || archetype.equals(Archetypes.SHUTTLE);
    }

    /** Noms connus, dans l'ordre d'ajout. */
    public static List<String> names(StrategicLayer layer) {
        String raw = layer.meta(META_KEY);
        List<String> names = new ArrayList<>();
        if (raw != null) for (String line : raw.split("\n")) if (!line.isEmpty()) names.add(line);
        return names;
    }

    /** Ajoute un nom à l'index (sans effet s'il y est déjà, à la casse près). */
    static void add(StrategicLayer layer, String name) {
        List<String> names = names(layer);
        if (names.stream().anyMatch(n -> n.equalsIgnoreCase(name))) return;
        names.add(name);
        layer.setMeta(META_KEY, String.join("\n", names));
    }

    /** Retire un nom de l'index, à la casse près. */
    static void remove(StrategicLayer layer, String name) {
        List<String> names = names(layer);
        if (names.removeIf(n -> n.equalsIgnoreCase(name))) layer.setMeta(META_KEY, String.join("\n", names));
    }
}
