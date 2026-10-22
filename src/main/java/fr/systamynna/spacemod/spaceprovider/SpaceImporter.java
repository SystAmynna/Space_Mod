package fr.systamynna.spacemod.spaceprovider;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.strategic.SpaceObject;
import fr.systamynna.spacemod.strategic.StrategicLayer;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Importe une source dans la couche : crée les objets absents et met à jour les données générales de ceux qui existent
 * déjà. Ne supprime jamais rien : un JSON disparu laisse son objet en place.
 */
public final class SpaceImporter {
    /** Clé de métadonnée de la version déjà importée d'une source. */
    static String versionKey(SpaceProvider provider) {
        return "provider_version_" + provider.name();
    }

    /** Résultat d'un import. */
    public record Report(int created, int updated, int skipped) {}

    /** Classe utilitaire. */
    private SpaceImporter() {}

    /**
     * Importe la source si sa version dépasse celle déjà importée (ou si {@code force}). Le tout premier import d'un monde
     * a donc toujours lieu ; ensuite, un objet supprimé par commande ne revient pas tant que le JSON n'est pas incrémenté.
     */
    public static Report run(StrategicLayer layer, SpaceProvider provider, ResourceManager resources, boolean force) {
        SpaceProvider.Data data = provider.load(resources);
        String stored = layer.meta(versionKey(provider));
        if (!force && stored != null && data.version() <= Integer.parseInt(stored)) return new Report(0, 0, 0);

        int created = 0, updated = 0, skipped = 0;
        List<ProviderEntry> pending = new ArrayList<>(data.entries());
        boolean progress = true;
        // un parent peut venir après son enfant dans les fichiers : on rejoue tant que quelque chose avance
        while (progress && !pending.isEmpty()) {
            progress = false;
            for (Iterator<ProviderEntry> it = pending.iterator(); it.hasNext();) {
                ProviderEntry entry = it.next();
                UUID parent = entry.parent() == null ? null : ProviderEntry.uuid(entry.parent());
                try {
                    SpaceObject existing = layer.find(entry.uuid());
                    if (existing != null) {
                        SpaceObjectJson.update(existing, entry);
                        updated++;
                    } else if (parent == null || layer.find(parent) != null) {
                        layer.addObject(parent, SpaceObjectJson.toObject(entry));
                        created++;
                    } else {
                        continue;
                    }
                } catch (IllegalArgumentException e) {
                    AmynnaSpaceMod.LOGGER.warn("Import de {} refusé : {}", entry.id(), e.getMessage());
                    skipped++;
                }
                it.remove();
                progress = true;
            }
        }
        for (ProviderEntry left : pending) {
            AmynnaSpaceMod.LOGGER.warn("Import de {} ignoré : parent {} introuvable", left.id(), left.parent());
            skipped++;
        }
        layer.setMeta(versionKey(provider), Integer.toString(data.version()));
        AmynnaSpaceMod.LOGGER.info("Source {} (version {}) : {} créé(s), {} mis à jour, {} ignoré(s)", provider.name(),
                data.version(), created, updated, skipped);
        return new Report(created, updated, skipped);
    }
}
