package fr.systamynna.spacemod.strategic;

import fr.systamynna.spacemod.strategic.component.hostedPropriety.PortPoint;
import net.neoforged.bus.api.Event;

/**
 * Posté sur le bus du jeu quand un objet visiteur entre dans le volume logique d'un port : le transport vers la
 * dimension de l'hôte doit alors être déclenché (voir {@link DimensionManager#enter}).
 */
public final class PortEntryEvent extends Event {
    private final SpaceObject visitor;
    private final SpaceObject host;
    private final PortPoint port;
    private final double[] offset;

    /**
     * @param visitor objet qui est entré dans le volume
     * @param host objet hôte de la dimension
     * @param port port dont le volume logique a été franchi
     * @param offset position du visiteur moins celle du port, dans le référentiel du parent
     */
    public PortEntryEvent(SpaceObject visitor, SpaceObject host, PortPoint port, double[] offset) {
        this.visitor = visitor;
        this.host = host;
        this.port = port;
        this.offset = offset.clone();
    }

    /** Objet entré dans le volume. */
    public SpaceObject visitor() {
        return visitor;
    }

    /** Hôte de la dimension. */
    public SpaceObject host() {
        return host;
    }

    /** Position du visiteur moins celle du port, à l'entrée ; devient son décalage dans la dimension. */
    public double[] offset() {
        return offset.clone();
    }

    /** Port franchi. */
    public PortPoint port() {
        return port;
    }
}
