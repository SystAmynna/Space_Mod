package fr.systamynna.spacemod.core.controller;

import java.util.List;

import fr.systamynna.spacemod.core.Transform;
import net.minecraft.nbt.CompoundTag;

/**
 * Déplace un {@link fr.systamynna.spacemod.core.SpaceObject} : écrit son {@link Transform} (référentiel du parent) en fonction du temps spatial.
 * Répliqué au client, qui recalcule la même pose avec sa {@code SpaceClock} : rien n'est envoyé ni écrit en base à chaque tick.
 * Le temps est exprimé en ticks (50 ms de {@code SpaceClock}).
 */
public interface SpaceController {
    /** Clé de persistance, unique par type. */
    String id();

    /** Payload persisté ; ne contient pas l'id (voir {@link SpaceControllers#save}). */
    CompoundTag save();

    /** Écrit dans {@code out} la pose à l'instant {@code ticks}. */
    void apply(double ticks, Transform out);

    /**
     * Correction au rechargement (ou à la réception côté client) : {@code t} est le transform sauvegardé, valable à {@code savedTicks}.
     * Par défaut le contrôleur est une fonction pure du temps et recalcule ; un contrôleur à état (pilotage) surcharge pour extrapoler depuis {@code t}.
     */
    default void resume(Transform t, double savedTicks, double nowTicks) { apply(nowTicks, t); }

    String describe();

    /** Édite depuis une commande ; syntaxe invalide : {@code SpaceObjectException("amynnaspacemod.controller.usage", …)}. */
    void edit(String args);

    /** Complétions pour le dernier mot de {@code args}. */
    List<String> suggest(String args);
}
