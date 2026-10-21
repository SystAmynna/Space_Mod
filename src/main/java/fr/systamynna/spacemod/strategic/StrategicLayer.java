package fr.systamynna.spacemod.strategic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.strategic.component.ComponentTypes;
import fr.systamynna.spacemod.strategic.component.Controller;
import fr.systamynna.spacemod.strategic.component.Destroyable;
import fr.systamynna.spacemod.strategic.component.HostedDimension;
import fr.systamynna.spacemod.strategic.component.Transform;
import fr.systamynna.spacemod.strategic.store.StoredObject;
import fr.systamynna.spacemod.strategic.store.StrategicStore;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Source de vérité des objets : un arbre dont la racine est virtuelle (le référentiel global) et dont les enfants
 * directs sont les galaxies ; un système stellaire n'est qu'un sous-arbre sous un objet portant le composant
 * {@code StellarSystem} (ce n'est pas un conteneur à part). Liée au
 * serveur (donc au monde sauvegardé), pas à une dimension : n'importe quelle dimension y accède par {@link #get()}, et
 * retrouve son objet hôte par {@link #hostOf(ResourceLocation)}. Thread serveur uniquement.
 * <p>
 * <b>Chargement partiel.</b> Tout l'arbre n'est pas en mémoire. Sont <i>résidents</i> en permanence, chargés au
 * démarrage : les cadres ({@code Frame} : galaxies et systèmes), les corps hors de tout système (vaisseaux en espace
 * interstellaire) et l'index dimension → hôte (identifiants seulement). Les <i>corps</i> d'un système (étoiles,
 * planètes, lunes, stations, croiseurs…) forment une <i>unité</i> lue dans le magasin au premier accès ({@link #find})
 * et déchargée, après écriture, quand aucun joueur ne l'occupe depuis un certain délai ({@link #unloadIdleUnits}). Une référence
 * {@code SpaceObject} gardée après le déchargement de son unité est périmée : redemander l'objet par {@link #find}.
 * <p>
 * Les liens de l'arbre sont portés par les objets eux-mêmes ({@link SpaceObject#parent()} et
 * {@link SpaceObject#children()}) ; la couche les garde synchrones des deux côtés, les vérifie au chargement, et ne tient
 * à part que la liste des galaxies (enfants de la racine virtuelle, qui n'est pas un objet).
 * <p>
 * Le suivi des modifications est par objet : {@link #save()} n'écrit que les objets modifiés. Ajouter ou déplacer un
 * objet modifie aussi son (ou ses) parent(s), dont la liste d'enfants change : ils sont réécrits eux aussi.
 */
public final class StrategicLayer {
    /** Profondeur maximale d'une chaîne de parents ; au-delà, on suppose un cycle (donnée corrompue) et on échoue. */
    private static final int MAX_DEPTH = 64;

    /** Couche du serveur en cours, ou {@code null} avant le chargement et après l'arrêt. Un seul serveur par processus. */
    @Nullable
    private static StrategicLayer active;

    /** Lecture et écriture de la base. */
    private final StrategicStore store;
    /** Horloge du temps spatial, sauvée avec la couche. */
    private final SpaceClock clock;
    /** Objets actuellement en mémoire (résidents et corps des unités chargées), par identifiant. */
    private final Map<UUID, SpaceObject> objects = new HashMap<>();
    /** Identifiants des galaxies : objets sans parent, enfants directs de la racine virtuelle. Toujours résidentes. */
    private final Set<UUID> roots = new LinkedHashSet<>();
    /** Unités chargées : identifiant du système → identifiants de ses corps en mémoire. La présence de la clé = unité chargée. */
    private final Map<UUID, Set<UUID>> unitMembers = new HashMap<>(); // TODO : Qu'est-ce qu'une unité ?
    /** Unité de chaque corps chargé (les objets résidents n'y figurent pas). */
    private final Map<UUID, UUID> unitByObject = new HashMap<>(); // TODO : ?
    /** Dernier accès (temps de jeu) à chaque unité chargée, pour décider de son déchargement. */
    private final Map<UUID, Long> unitLastAccess = new HashMap<>(); // TODO : ?
    /** Objets modifiés depuis la dernière écriture, à écrire au prochain {@link #save()}. */
    private final Set<UUID> needsSave = new LinkedHashSet<>();
    /** Dimension hébergée → identifiant de l'objet hôte, pour tous les objets, chargés ou non. Borné par le nombre de dimensions. */
    private final Map<ResourceLocation, UUID> dimensionHosts = new HashMap<>();
    /** Réciproque de {@link #dimensionHosts}. */
    private final Map<UUID, ResourceLocation> hostedBy = new HashMap<>();
    /** Dernier temps de jeu connu ({@link #tick}), utilisé pour dater les accès aux unités. */
    private long now; // TODO :  pourquoi pas dans clock ?

    /**
     * Charge la partie résidente depuis le magasin et vérifie sa cohérence.
     *
     * @throws IllegalStateException si un objet est présent deux fois, si un parent est absent ou si l'arbre contient un
     *         cycle : on refuse de charger plutôt que de réparer en silence
     */
    public StrategicLayer(StrategicStore store) {
        this.store = store;
        this.clock = store.loadClock();
        List<StoredObject> resident = store.loadResident(); // TODO : Résient != tous les SpaceObject, mais seulement ceux à load (ceux qui était load à la fermeture), sinon est en train de load tout l'univers en mémoire ?
        for (StoredObject stored : resident) {
            if (objects.putIfAbsent(stored.object().id(), stored.object()) != null) {
                throw new IllegalStateException("Objet présent deux fois dans la base : " + stored.object().id());
            }
        }
        // Deuxième passe, une fois tous les objets connus : les parents peuvent être lus après leurs enfants.
        for (SpaceObject object : objects.values()) {
            UUID parent = object.parent();
            if (parent != null && !objects.containsKey(parent)) {
                throw new IllegalStateException("Parent " + parent + " absent de la base (objet " + object.id() + ")");
            }
            if (parent == null) roots.add(object.id());
            object.attach(this::onChanged);
        }
        // Troisième passe : les listes d'enfants et les parents doivent se répondre.
        checkLinks(objects.values(), Map.of());
        // Détection de cycle : un objet dans un cycle de parents n'est descendant d'aucune galaxie. Si le parcours depuis
        // les galaxies n'atteint pas tous les objets, certains sont inaccessibles.
        int reachable = 0;
        for (UUID galaxy : roots) reachable += countSubtree(galaxy);
        if (reachable != objects.size()) throw new IllegalStateException("Cycle de parents dans la couche stratégique");
        for (Map.Entry<ResourceLocation, UUID> host : store.hostedDimensions().entrySet()) {
            dimensionHosts.put(host.getKey(), host.getValue());
            hostedBy.put(host.getValue(), host.getKey());
        }
    }

    /** Couche du serveur en cours (un seul par processus) ; échoue avant son chargement ou après l'arrêt. */
    public static StrategicLayer get() {
        if (active == null) throw new IllegalStateException("Couche stratégique non chargée");
        return active;
    }

    /** Désigne la couche active ; appelé au démarrage du serveur par {@code StrategicEvents}. */
    static void bind(StrategicLayer layer) {
        active = layer;
    }

    /** Oublie la couche active ; appelé à l'arrêt du serveur. */
    static void unbind() {
        active = null;
    }

    /** Horloge du temps spatial de cette couche. */
    public SpaceClock clock() {
        return clock;
    }

    /** Les galaxies (toujours en mémoire) : enfants directs de la racine. */
    public List<SpaceObject> galaxies() {
        return roots.stream().map(objects::get).toList();
    }

    /**
     * Objet d'identifiant donné, ou {@code null} s'il n'existe pas. Charge l'unité de l'objet si elle ne l'est pas.
     * Rafraîchit la date d'accès de l'unité.
     */
    @Nullable
    public SpaceObject find(UUID objectId) {
        SpaceObject loaded = objects.get(objectId);
        if (loaded != null) {
            UUID unit = unitByObject.get(objectId);
            if (unit != null) unitLastAccess.put(unit, now);
            return loaded;
        }
        StrategicStore.Placement placement = store.placementOf(objectId);
        // un objet résident absent de la mémoire n'existe pas (les résidents sont tous chargés au démarrage)
        if (placement == null || placement.unitId() == null) return null;
        ensureUnitLoaded(placement.unitId());
        return objects.get(objectId);
    }

    /** {@code true} si l'objet est en mémoire, sans rien charger. */
    public boolean isLoaded(UUID objectId) {
        return objects.containsKey(objectId);
    }

    /** Nombre d'objets en mémoire. */
    public int loadedObjectCount() {
        return objects.size();
    }

    /** Nombre d'unités (systèmes) dont les corps sont en mémoire. */
    public int loadedUnitCount() {
        return unitMembers.size();
    }

    /**
     * Parent d'un objet, ou {@code null} pour une galaxie.
     *
     * @throws IllegalArgumentException si l'objet est inconnu
     */
    @Nullable
    public SpaceObject parentOf(UUID objectId) {
        UUID parent = require(objectId).parent();
        return parent == null ? null : objects.get(parent);
    }

    /**
     * La galaxie qui contient cet objet (lui-même si c'en est une).
     *
     * @throws IllegalArgumentException si l'objet est inconnu
     */
    public SpaceObject galaxyOf(UUID objectId) {
        SpaceObject current = require(objectId);
        // on remonte les parents jusqu'à l'objet sans parent ; la borne protège d'un cycle de parents
        for (int depth = 0; current.parent() != null; depth++) {
            if (depth > MAX_DEPTH) throw new IllegalStateException("Arbre trop profond ou cycle de parents : " + objectId);
            current = objects.get(current.parent());
        }
        return current;
    }

    /**
     * Le système stellaire (objet portant le composant {@code StellarSystem}) qui contient cet objet (lui-même si c'en est
     * un), ou {@code null} dans l'espace interstellaire.
     *
     * @throws IllegalArgumentException si l'objet est inconnu
     */
    @Nullable
    public SpaceObject systemOf(UUID objectId) {
        require(objectId);
        return nearestSystem(objectId);
    }

    /**
     * Ajoute un objet sous {@code parentId} ({@code null} : une galaxie, enfant direct de la racine). Le Transform de
     * l'objet doit déjà être exprimé dans le référentiel de ce parent. Charge l'unité du parent si besoin. Le parent
     * inscrit le nouvel objet dans sa liste d'enfants (il est donc réécrit à la prochaine sauvegarde).
     *
     * @return l'objet ajouté
     * @throws IllegalArgumentException si le parent est inconnu, si l'objet est déjà présent, si la dimension qu'il
     *         héberge l'est déjà par un autre objet, ou si un cadre est placé ailleurs que sous un cadre hors système
     */
    public SpaceObject addObject(@Nullable UUID parentId, SpaceObject object) {
        if (parentId != null && find(parentId) == null) throw new IllegalArgumentException("Parent inconnu : " + parentId);
        if (find(object.id()) != null) throw new IllegalArgumentException("Objet déjà présent : " + object.id());
        HostedDimension hosted = object.get(ComponentTypes.HOSTED_DIMENSION);
        // une dimension n'a qu'un hôte : refusé avant toute modification de l'arbre
        if (hosted != null && dimensionHosts.containsKey(hosted.dimension())) {
            throw new IllegalArgumentException("Dimension déjà hébergée : " + hosted.dimension());
        }
        if (object.has(ComponentTypes.FRAME)) requireFrameParent(parentId);
        UUID unit = object.has(ComponentTypes.FRAME) ? null : unitBelow(parentId);
        // l'unité doit être chargée avant d'y ajouter un corps, sinon elle semblerait chargée avec ce seul corps
        if (unit != null) ensureUnitLoaded(unit);

        object.setParent(parentId);
        register(object, unit);
        object.attach(this::onChanged);
        // marque l'objet à écrire et inscrit sa dimension dans l'index
        onChanged(object);
        // le parent liste son nouvel enfant (et sera donc réécrit) ; une galaxie n'a pas de parent, seulement la racine
        if (parentId != null) objects.get(parentId).addChild(object.id());
        return object;
    }

    /**
     * Change le parent d'un objet en conservant sa position au temps spatial donné : son Transform local est réécrit dans
     * le référentiel du nouveau parent (un croiseur qui adopte une lune la suit sans saut). Un objet dont le contrôleur
     * détermine la position (rail) doit recevoir {@code newController} : le même rail autour d'un autre parent ne passerait
     * pas par le même point. La vitesse et l'orientation ne sont pas converties.
     *
     * @param newParentId nouveau parent ; {@code null} pour faire de l'objet une galaxie
     * @param spaceTime temps spatial auquel la position est conservée
     * @param newController nouveau contrôleur à installer en même temps, ou {@code null} pour garder l'actuel
     * @throws IllegalArgumentException si un objet est inconnu, si le changement crée un cycle (le nouveau parent est
     *         l'objet ou un de ses descendants), si un objet sur rail n'a pas reçu de nouveau contrôleur, ou si un cadre
     *         est placé ailleurs que sous un cadre hors système
     */
    public void reparent(UUID objectId, @Nullable UUID newParentId, double spaceTime, @Nullable Controller newController) {
        // TODO : Pourquoi spaceTime est nécessaire ? définit le delta sur le rail ?
        SpaceObject object = require(objectId);
        SpaceObject newParent = newParentId == null ? null : require(newParentId);
        // Cycle : devenir enfant de soi-même ou d'un de ses descendants couperait le sous-arbre de l'arbre.
        if (newParent != null && (newParent == object || isAncestor(object, newParent))) {
            throw new IllegalArgumentException("Cycle : " + newParentId + " descend de " + objectId);
        }
        if (object.has(ComponentTypes.FRAME)) requireFrameParent(newParentId);
        Controller old = object.controller();
        if (newController == null && old != null && old.determinesPosition()) {
            throw new IllegalArgumentException("Un nouveau contrôleur est nécessaire : " + objectId + " suit un rail");
        }
        // Calculs et chargements faits avant toute modification : si l'un échoue, l'arbre reste inchangé.
        Transform rebased = RailMath.rebased(this, object, newParent, spaceTime);
        UUID oldUnit = unitByObject.get(objectId);
        UUID newUnit = object.has(ComponentTypes.FRAME) ? null : unitBelow(newParentId);
        if (newUnit != null) ensureUnitLoaded(newUnit);

        // les deux parents (ou la racine) mettent leur liste d'enfants à jour : ils seront réécrits
        SpaceObject oldParent = object.parent() == null ? null : objects.get(object.parent());
        if (oldParent != null) oldParent.removeChild(objectId);
        else roots.remove(objectId);
        object.setParent(newParentId);
        if (newParent != null) newParent.addChild(objectId);
        else roots.add(objectId);
        Controller target = newController != null ? newController : old;
        object.controller(target);
        // Un contrôleur qui détermine la position ignore le Transform stocké : inutile (et faux) de le réécrire.
        if (target == null || !target.determinesPosition()) object.put(rebased);
        // Changer de système change l'unité de l'objet et de tous ses descendants (des corps, donc déjà chargés).
        if (!Objects.equals(oldUnit, newUnit)) reassignUnit(object, newUnit);
        needsSave.add(objectId);
    }

    /** Variante de {@link #reparent(UUID, UUID, double, Controller)} qui garde le contrôleur actuel. */
    public void reparent(UUID objectId, @Nullable UUID newParentId, double spaceTime) {
        reparent(objectId, newParentId, spaceTime, null);
    }

    /**
     * Marque un objet comme détruit ; il est conservé et sa dimension reste attribuée.
     *
     * @throws IllegalArgumentException si l'objet est inconnu ou indestructible (sans {@code Destroyable})
     */
    public void destroy(UUID objectId) {
        requireDestroyable(objectId);
        require(objectId).put(new Destroyable(true));
    }

    /**
     * Restauration par un opérateur : l'objet n'est plus marqué détruit.
     *
     * @throws IllegalArgumentException si l'objet est inconnu ou indestructible
     */
    public void restore(UUID objectId) {
        requireDestroyable(objectId);
        require(objectId).put(new Destroyable(false));
    }

    /** Objet qui héberge cette dimension, détruit ou non ({@link SpaceObject#isDestroyed()}), ou {@code null}. Charge son unité. */
    @Nullable
    public SpaceObject hostOf(ResourceLocation dimension) {
        UUID host = dimensionHosts.get(dimension);
        return host == null ? null : find(host);
    }

    /** Variante de {@link #hostOf(ResourceLocation)} pour la clé d'une dimension vanilla. */
    @Nullable
    public SpaceObject hostOf(ResourceKey<Level> dimension) {
        return hostOf(dimension.location());
    }

    /** Dimensions que la couche attend, pour la réconciliation avec celles que la bibliothèque a restaurées. Sans rien charger. */
    public Set<ResourceLocation> hostedDimensions() {
        return Collections.unmodifiableSet(dimensionHosts.keySet());
    }

    /**
     * Unité (système) dont l'hôte de cette dimension est un corps, sans rien charger ; {@code null} si la dimension n'a
     * pas d'hôte ou si son hôte est résident. Sert à savoir quelles unités les joueurs occupent.
     */
    @Nullable
    public UUID unitOfDimension(ResourceLocation dimension) {
        UUID host = dimensionHosts.get(dimension);
        if (host == null) return null;
        if (objects.containsKey(host)) return unitByObject.get(host);
        StrategicStore.Placement placement = store.placementOf(host);
        return placement == null ? null : placement.unitId();
    }

    /** Enregistre le temps de jeu courant, qui date les accès aux unités. À appeler à chaque tick du serveur. */
    public void tick(long gameTime) {
        this.now = gameTime;
    }

    /**
     * Décharge les unités inoccupées sans accès depuis au moins {@code delayTicks} : leurs objets modifiés sont écrits,
     * puis retirés de la mémoire. Une unité occupée est rafraîchie et jamais déchargée.
     *
     * @param occupied unités où se trouve au moins un joueur
     * @return le nombre d'unités déchargées
     */
    public int unloadIdleUnits(long delayTicks, Set<UUID> occupied) {
        List<UUID> idle = new ArrayList<>();
        for (Map.Entry<UUID, Long> access : unitLastAccess.entrySet()) {
            if (occupied.contains(access.getKey())) access.setValue(now);
            else if (now - access.getValue() >= delayTicks) idle.add(access.getKey());
        }
        if (idle.isEmpty()) return 0;
        // écrire avant d'oublier : un objet modifié ne doit jamais disparaître de la mémoire sans être dans la base
        Set<UUID> toFlush = new HashSet<>();
        for (UUID unit : idle) {
            for (UUID member : unitMembers.get(unit)) if (needsSave.contains(member)) toFlush.add(member);
        }
        write(toFlush, false);
        for (UUID unit : idle) unload(unit);
        return idle.size();
    }

    /** Écrit les objets modifiés (et l'horloge si besoin) ; renvoie le nombre d'objets écrits. */
    public int save() {
        return write(new ArrayList<>(needsSave), clock.needsSave());
    }

    /** Ferme le magasin. À appeler après la dernière sauvegarde. */
    public void close() {
        store.close();
    }

    /**
     * Écrit des objets dans le magasin, avec l'horloge si demandé, puis les retire de la liste à écrire. Si le magasin
     * échoue (exception), rien n'est retiré : tout reste à écrire.
     */
    private int write(Collection<UUID> ids, boolean withClock) {
        List<StoredObject> rows = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            SpaceObject object = objects.get(id);
            rows.add(new StoredObject(object, object.has(ComponentTypes.FRAME) ? null : unitBelow(object.parent())));
        }
        if (rows.isEmpty() && !withClock) return 0;
        store.save(rows, withClock ? clock : null);
        if (withClock) clock.clearNeedsSave();
        needsSave.removeAll(ids);
        return rows.size();
    }

    /** Charge les corps d'un système s'ils ne le sont pas, sinon rafraîchit seulement leur date d'accès. */
    private void ensureUnitLoaded(UUID unitId) {
        if (unitMembers.containsKey(unitId)) {
            unitLastAccess.put(unitId, now);
            return;
        }
        SpaceObject system = objects.get(unitId);
        if (system == null || !system.has(ComponentTypes.STELLAR_SYSTEM)) {
            throw new IllegalStateException("Unité inconnue (pas un système stellaire résident) : " + unitId);
        }
        List<StoredObject> rows = store.loadUnit(unitId);
        // Tout est vérifié avant de toucher à la mémoire : une unité corrompue ne doit pas être à moitié chargée.
        Map<UUID, SpaceObject> incoming = new HashMap<>();
        for (StoredObject row : rows) {
            SpaceObject object = row.object();
            if (objects.containsKey(object.id()) || incoming.put(object.id(), object) != null) {
                throw new IllegalStateException("Objet présent deux fois : " + object.id() + " (unité " + unitId + ")");
            }
        }
        for (SpaceObject object : incoming.values()) {
            UUID parent = object.parent();
            if (parent == null || !(objects.containsKey(parent) || incoming.containsKey(parent))) {
                throw new IllegalStateException("Parent " + parent + " absent (objet " + object.id() + ", unité " + unitId + ")");
            }
            // la chaîne de parents doit sortir de l'unité (vers le système, résident) sans tourner en rond
            UUID current = parent;
            for (int depth = 0; incoming.containsKey(current); depth++) {
                if (depth > incoming.size()) throw new IllegalStateException("Cycle de parents dans l'unité " + unitId);
                current = incoming.get(current).parent();
            }
        }
        // les listes d'enfants et les parents doivent se répondre, y compris la liste du système, qui date d'avant le chargement
        List<SpaceObject> toCheck = new ArrayList<>(incoming.values());
        toCheck.add(system);
        checkLinks(toCheck, incoming);
        Set<UUID> members = new LinkedHashSet<>();
        unitMembers.put(unitId, members);
        unitLastAccess.put(unitId, now);
        for (SpaceObject object : incoming.values()) {
            register(object, unitId);
            object.attach(this::onChanged);
        }
    }

    /**
     * Retire de la mémoire les corps d'une unité (déjà écrits). Les références détenues deviennent périmées ; les listes
     * d'enfants des parents (le système, résident) gardent les identifiants des corps déchargés.
     */
    private void unload(UUID unitId) {
        Set<UUID> members = unitMembers.remove(unitId);
        unitLastAccess.remove(unitId);
        for (UUID id : members) {
            objects.remove(id);
            unitByObject.remove(id);
            needsSave.remove(id);
        }
    }

    /** Met l'objet en mémoire : index général, galaxies s'il n'a pas de parent, et unité s'il en a une. */
    private void register(SpaceObject object, @Nullable UUID unitId) {
        objects.put(object.id(), object);
        if (object.parent() == null) roots.add(object.id());
        if (unitId != null) {
            unitMembers.get(unitId).add(object.id());
            unitByObject.put(object.id(), unitId);
        }
    }

    /**
     * Donne une nouvelle unité à un objet et à tous ses descendants chargés (tous des corps) ; ils sont à réécrire,
     * leur colonne d'unité changeant.
     */
    private void reassignUnit(SpaceObject root, @Nullable UUID newUnit) {
        Deque<UUID> pending = new ArrayDeque<>();
        pending.push(root.id());
        while (!pending.isEmpty()) {
            UUID id = pending.pop();
            UUID oldUnit = unitByObject.remove(id);
            if (oldUnit != null) unitMembers.get(oldUnit).remove(id);
            if (newUnit != null) {
                unitMembers.get(newUnit).add(id);
                unitByObject.put(id, newUnit);
            }
            needsSave.add(id);
            for (UUID child : objects.get(id).children()) if (objects.containsKey(child)) pending.push(child);
        }
    }

    /**
     * Unité à laquelle appartient un corps dont le parent est {@code parentId} : le système le plus proche, parent compris ;
     * {@code null} hors de tout système (le corps est alors résident).
     */
    @Nullable
    private UUID unitBelow(@Nullable UUID parentId) {
        SpaceObject system = parentId == null ? null : nearestSystem(parentId);
        return system == null ? null : system.id();
    }

    /**
     * Système stellaire (objet portant le composant {@code StellarSystem}) le plus proche de l'objet {@code startId} en
     * remontant, lui compris ; {@code null} s'il n'y en a pas. L'objet et ses ancêtres doivent être en mémoire, ce qui
     * est toujours le cas pour un objet obtenu par {@link #find}.
     */
    @Nullable
    private SpaceObject nearestSystem(UUID startId) {
        SpaceObject current = objects.get(startId);
        for (int depth = 0; current != null; depth++) {
            if (depth > MAX_DEPTH) throw new IllegalStateException("Arbre trop profond ou cycle de parents : " + startId);
            if (current.has(ComponentTypes.STELLAR_SYSTEM)) return current;
            current = current.parent() == null ? null : objects.get(current.parent());
        }
        return null;
    }

    /**
     * Un cadre ne peut descendre que d'un autre cadre qui n'est pas dans un système (ou être une galaxie) : les cadres
     * sont résidents, alors que tout ce qui est sous un système peut être déchargé avec lui.
     *
     * @throws IllegalArgumentException si le parent ne convient pas
     */
    private void requireFrameParent(@Nullable UUID parentId) {
        if (parentId == null) return;
        SpaceObject parent = require(parentId);
        if (!parent.has(ComponentTypes.FRAME) || nearestSystem(parentId) != null) {
            throw new IllegalArgumentException("Un cadre ne peut descendre que d'un cadre hors système : " + parentId);
        }
    }

    /**
     * Objet d'identifiant donné, chargé au besoin.
     *
     * @throws IllegalArgumentException si l'objet est inconnu
     */
    private SpaceObject require(UUID objectId) {
        SpaceObject object = find(objectId);
        if (object == null) throw new IllegalArgumentException("Objet inconnu : " + objectId);
        return object;
    }

    /** @throws IllegalArgumentException si l'objet est inconnu ou n'a pas de composant {@code Destroyable} */
    private void requireDestroyable(UUID objectId) {
        if (!require(objectId).has(ComponentTypes.DESTROYABLE)) {
            throw new IllegalArgumentException("Objet indestructible : " + objectId);
        }
    }

    /** {@code true} si {@code ancestor} est un ancêtre strict de {@code object} (parent, grand-parent…). */
    private boolean isAncestor(SpaceObject ancestor, SpaceObject object) {
        UUID current = object.parent();
        for (int depth = 0; current != null && depth <= MAX_DEPTH; depth++) {
            if (current.equals(ancestor.id())) return true;
            current = objects.get(current).parent();
        }
        return false;
    }

    /**
     * Vérifie que parents et listes d'enfants se répondent : chaque objet figure dans la liste de son parent, et chaque
     * enfant listé qui est chargé a bien cet objet pour parent. Les enfants listés mais non chargés ne sont pas vérifiés
     * (ils le seront au chargement de leur unité).
     *
     * @param toCheck objets à vérifier
     * @param incoming objets en cours de chargement, pas encore dans {@link #objects}
     * @throws IllegalStateException au premier lien incohérent : on ne répare pas en silence
     */
    private void checkLinks(Collection<SpaceObject> toCheck, Map<UUID, SpaceObject> incoming) {
        for (SpaceObject object : toCheck) {
            if (object.parent() != null) {
                SpaceObject parent = incoming.containsKey(object.parent()) ? incoming.get(object.parent()) : objects.get(object.parent());
                if (parent == null || !parent.children().contains(object.id())) {
                    throw new IllegalStateException("Liens incohérents : " + object.id() + " n'est pas dans les enfants de son parent " + object.parent());
                }
            }
            for (UUID childId : object.children()) {
                SpaceObject child = incoming.containsKey(childId) ? incoming.get(childId) : objects.get(childId);
                if (child != null && !object.id().equals(child.parent())) {
                    throw new IllegalStateException("Liens incohérents : " + childId + " est listé parmi les enfants de " + object.id() + " mais a pour parent " + child.parent());
                }
            }
        }
    }

    /** Nombre d'objets chargés dans le sous-arbre de la racine donnée, elle comprise (les enfants non chargés sont ignorés). */
    private int countSubtree(UUID rootId) {
        int count = 0;
        // parcours en profondeur avec une pile explicite (pas de récursion : l'arbre peut être profond)
        Deque<UUID> pending = new ArrayDeque<>();
        pending.push(rootId);
        while (!pending.isEmpty()) {
            UUID id = pending.pop();
            count++;
            for (UUID child : objects.get(id).children()) if (objects.containsKey(child)) pending.push(child);
        }
        return count;
    }

    /**
     * Appelé par chaque objet de la couche quand il change : l'objet est à réécrire, et sa dimension hébergée (ajoutée,
     * changée ou retirée) est reportée dans l'index.
     */
    private void onChanged(SpaceObject object) {
        needsSave.add(object.id());
        HostedDimension hosted = object.get(ComponentTypes.HOSTED_DIMENSION);
        ResourceLocation current = hosted == null ? null : hosted.dimension();
        ResourceLocation previous = hostedBy.get(object.id());
        if (Objects.equals(previous, current)) return;
        if (previous != null) {
            dimensionHosts.remove(previous, object.id());
            hostedBy.remove(object.id());
        }
        if (current != null) {
            UUID other = dimensionHosts.putIfAbsent(current, object.id());
            // ne devrait pas arriver (addObject refuse le doublon) : un composant remplacé après coup ; la base le refusera aussi
            if (other != null && !other.equals(object.id())) {
                AmynnaSpaceMod.LOGGER.error("Dimension {} hébergée par deux objets ({} et {}) : le premier est conservé",
                        current, other, object.id());
            } else {
                hostedBy.put(object.id(), current);
            }
        }
    }
}
