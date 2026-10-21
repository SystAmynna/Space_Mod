package fr.systamynna.spacemod.strategic;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import fr.systamynna.spacemod.strategic.component.Component;
import fr.systamynna.spacemod.strategic.component.ComponentType;
import fr.systamynna.spacemod.strategic.component.ComponentTypes;
import fr.systamynna.spacemod.strategic.component.Controller;
import fr.systamynna.spacemod.strategic.component.ControllerTypes;
import fr.systamynna.spacemod.strategic.component.Destroyable;
import fr.systamynna.spacemod.strategic.component.Transform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Objet de la couche stratégique : un identifiant, un parent, ses enfants, un contrôleur et une liste extensible de
 * composants, pas une classe fixe. Les objets forment un arbre (galaxie → système → planète → lune…) : chacun connaît son
 * parent et la liste complète de ses enfants, <i>chargés ou non</i> (un système liste donc tous ses corps même quand ils
 * sont déchargés). Le Transform est exprimé dans le référentiel du parent. Thread serveur uniquement.
 * <p>
 * Les liens parent et enfants ne sont modifiés que par {@link StrategicLayer}, qui les garde synchrones des deux côtés.
 * Tant qu'un objet n'est pas ajouté à une couche, modifier ses composants ne notifie personne. Quand l'unité d'un objet est déchargée de la mémoire, l'instance devient
 * périmée : les modifications qu'on lui apporte ensuite ne sont plus écrites. Redemander l'objet par
 * {@link StrategicLayer#find}.
 */
public final class SpaceObject {
    /** Identifiant unique et stable de l'objet, clé dans la couche et dans la base. */
    private final UUID id;
    /**
     * Composants de l'objet, par identifiant de type. Un seul composant par type ; l'ordre d'insertion est conservé
     * pour que la sauvegarde soit stable.
     */
    private final Map<ResourceLocation, Component> components = new LinkedHashMap<>();
    /** Identifiant du parent, ou {@code null} pour une racine (galaxie). Géré par la couche. */
    @Nullable
    private UUID parent;
    /**
     * Identifiants des enfants directs, dans l'ordre d'ajout, chargés ou non. Géré par la couche. Ensemble (et non liste)
     * pour retirer un enfant en temps constant, même d'une galaxie de 100 000 systèmes.
     */
    private final Set<UUID> children = new LinkedHashSet<>();
    /** Ce qui fait bouger l'objet ; {@code null} = immobile (position = Transform stocké). */
    @Nullable
    private Controller controller;
    /** Rappel de la couche appelé à chaque modification, ou {@code null} tant que l'objet n'est pas dans une couche (ou vient d'être relu). */
    @Nullable
    private Consumer<SpaceObject> changeListener; // TODO : à quoi ça sert ?

    /** Crée un objet sans parent, sans contrôleur ni composant. */
    public SpaceObject(UUID id) {
        this.id = id;
    }

    /** Identifiant unique de l'objet. */
    public UUID id() {
        return id;
    }

    /** Identifiant du parent ; {@code null} pour une racine (galaxie). Modifié par {@link StrategicLayer} seulement. */
    @Nullable
    public UUID parent() {
        return parent;
    }

    /** Change le parent sans rien notifier ni mettre à jour les index : réservé à {@link StrategicLayer}, qui s'en charge. */
    void setParent(@Nullable UUID parent) {
        this.parent = parent;
    }

    /**
     * Identifiants de tous les enfants directs de l'objet, dans l'ordre d'ajout, qu'ils soient chargés ou non. Vue en
     * lecture seule ; pour obtenir un enfant, {@link StrategicLayer#find}.
     */
    public Set<UUID> children() {
        return Collections.unmodifiableSet(children);
    }

    /** Ajoute un enfant et marque l'objet à écrire. Réservé à {@link StrategicLayer}, qui met aussi le parent de l'enfant à jour. */
    void addChild(UUID childId) {
        if (children.add(childId)) changed();
    }

    /** Retire un enfant et marque l'objet à écrire. Réservé à {@link StrategicLayer}, qui met aussi le parent de l'enfant à jour. */
    void removeChild(UUID childId) {
        if (children.remove(childId)) changed();
    }

    /** Branche le rappel de modification de la couche (qui y marque l'objet à écrire). Appelé par {@link StrategicLayer} seulement. */
    void attach(Consumer<SpaceObject> listener) {
        this.changeListener = listener;
    }

    /** Ce qui fait bouger l'objet ; {@code null} = immobile. */
    @Nullable
    public Controller controller() {
        return controller;
    }

    /** Remplace le contrôleur ({@code null} = immobile) et notifie la couche ; renvoie l'objet pour chaîner les appels. */
    public SpaceObject controller(@Nullable Controller controller) {
        this.controller = controller;
        changed();
        return this;
    }

    /** Composant de ce type, ou {@code null} s'il est absent. Le type garantit la classe renvoyée, sans cast à l'appel. */
    @Nullable
    public <T extends Component> T get(ComponentType<T> type) {
        // l'identifiant n'est associé qu'à un seul ComponentType : le cast est sûr
        @SuppressWarnings("unchecked")
        T component = (T) components.get(type.id());
        return component;
    }

    /** {@code true} si l'objet porte un composant de ce type. */
    public boolean has(ComponentType<?> type) {
        return components.containsKey(type.id());
    }

    /** Transform stocké (dans le référentiel du parent), à l'origine s'il n'y en a pas. */
    public Transform transform() {
        Transform transform = get(ComponentTypes.TRANSFORM);
        return transform == null ? Transform.ORIGIN : transform;
    }

    /**
     * Ajoute un composant, ou remplace celui du même type (les composants sont immuables : on les remplace, on ne les
     * modifie pas). Notifie la couche ; renvoie l'objet pour chaîner les appels.
     */
    public SpaceObject put(Component component) {
        components.put(component.typeId(), component);
        changed();
        return this;
    }

    /** Retire le composant de ce type s'il existe (et notifie alors la couche) ; renvoie l'objet pour chaîner. */
    public SpaceObject remove(ComponentType<?> type) {
        if (components.remove(type.id()) != null) changed();
        return this;
    }

    /** Vue en lecture seule des composants de l'objet. */
    public Collection<Component> components() {
        return Collections.unmodifiableCollection(components.values());
    }

    /** Un objet détruit reste en place, marqué. Un objet sans {@link Destroyable} est indestructible, donc jamais détruit. */
    public boolean isDestroyed() {
        Destroyable destroyable = get(ComponentTypes.DESTROYABLE);
        return destroyable != null && destroyable.destroyed();
    }

    /** Prévient la couche (si l'objet y est) qu'il a changé : il sera réécrit à la prochaine sauvegarde, et l'index des dimensions mis à jour. */
    private void changed() {
        if (changeListener != null) changeListener.accept(this);
    }

    /**
     * Sérialise l'objet : identifiant, parent (absent pour une galaxie), enfants (absents s'il n'y en a pas, écrits en
     * tableau de {@code long}, deux par identifiant), contrôleur (absent s'il est {@code null}), puis les composants
     * regroupés par identifiant de type.
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        if (parent != null) tag.putUUID("parent", parent);
        if (!children.isEmpty()) {
            long[] ids = new long[children.size() * 2];
            int i = 0;
            for (UUID child : children) {
                ids[i++] = child.getMostSignificantBits();
                ids[i++] = child.getLeastSignificantBits();
            }
            tag.put("children", new LongArrayTag(ids));
        }
        if (controller != null) tag.put("controller", Controller.write(controller));
        CompoundTag comps = new CompoundTag();
        for (Component component : components.values()) {
            comps.put(component.typeId().toString(), component.save());
        }
        tag.put("components", comps);
        return tag;
    }

    /**
     * Relit un objet écrit par {@link #save()}. Un composant ou un contrôleur d'un type non enregistré est conservé tel
     * quel (voir {@code UnknownComponent}, {@code UnknownController}). L'objet n'est branché à aucune couche.
     */
    public static SpaceObject load(CompoundTag tag) {
        SpaceObject object = new SpaceObject(tag.getUUID("id"));
        if (tag.hasUUID("parent")) object.parent = tag.getUUID("parent");
        long[] ids = tag.getLongArray("children");
        for (int i = 0; i + 1 < ids.length; i += 2) object.children.add(new UUID(ids[i], ids[i + 1]));
        if (tag.contains("controller")) object.controller = ControllerTypes.read(tag.getCompound("controller"));
        CompoundTag comps = tag.getCompound("components");
        for (String key : comps.getAllKeys()) {
            ResourceLocation typeId = ResourceLocation.parse(key);
            Component component = ComponentTypes.read(typeId, comps.getCompound(key));
            // écrit directement dans la map, sans put() : pas encore de couche à notifier
            object.components.put(typeId, component);
        }
        return object;
    }
}
