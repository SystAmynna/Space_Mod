package fr.systamynna.spacemod.strategic.store;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import fr.systamynna.spacemod.strategic.SpaceObject;

/**
 * Un objet tel qu'il est écrit dans, ou relu depuis, le magasin, avec l'unité de chargement à laquelle il appartient.
 *
 * @param object l'objet
 * @param unitId identifiant du système stellaire dont l'objet est un corps (chargé et déchargé avec lui), ou
 *        {@code null} pour un objet résident : cadre (galaxie, système) ou corps hors de tout système
 */
public record StoredObject(SpaceObject object, @Nullable UUID unitId) {}
