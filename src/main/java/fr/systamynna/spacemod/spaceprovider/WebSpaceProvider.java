package fr.systamynna.spacemod.spaceprovider;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import net.minecraft.server.packs.resources.ResourceManager;

/** Source web (bases de données des modules de recherche) : pas encore implémentée, ne fournit rien. */
public final class WebSpaceProvider implements SpaceProvider {
    @Override
    public String name() {
        return "web";
    }

    @Override
    public Data load(ResourceManager resources) {
        AmynnaSpaceMod.LOGGER.warn("WEB_PROVIDER activé mais le fournisseur web n'est pas encore implémenté");
        return Data.EMPTY;
    }
}
