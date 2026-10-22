package fr.systamynna.spacemod.spaceprovider;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.strategic.component.RailFollower;
import fr.systamynna.spacemod.strategic.component.Render;
import fr.systamynna.spacemod.strategic.component.Transform;

/**
 * Description, venue d'une source (JSON), d'un objet de l'espace : uniquement ses données générales. Une dimension
 * hébergée n'en fait jamais partie, elle appartient au monde.
 *
 * @param id identifiant texte stable (par exemple {@code sol/earth}) dont dérive l'UUID de l'objet
 * @param parent identifiant texte du parent, ou {@code null} pour une galaxie
 * @param archetype {@code galaxy}, {@code system}, {@code star} ou {@code planet}
 * @param name nom affiché
 * @param transform position (mètres, dans le référentiel du parent) et orientation
 * @param rail orbite et rotation propre, ou {@code null}
 * @param render modèle et rayon apparent, ou {@code null}
 * @param exclusionRadius rayon d'exclusion d'une étoile, ou {@code null} (alors le double du rayon apparent)
 * @param mainStar identifiant texte de l'étoile principale d'un système, ou {@code null}
 */
public record ProviderEntry(String id, @Nullable String parent, String archetype, String name, Transform transform,
        @Nullable RailFollower rail, @Nullable Render render, @Nullable Double exclusionRadius, @Nullable String mainStar) {

    /** UUID de l'objet pour un identifiant texte : le même à chaque import, ce qui permet de retrouver l'objet en base. */
    public static UUID uuid(String id) {
        return UUID.nameUUIDFromBytes((AmynnaSpaceMod.MODID + ":" + id).getBytes(StandardCharsets.UTF_8));
    }

    /** UUID de l'objet décrit. */
    public UUID uuid() {
        return uuid(id);
    }
}
