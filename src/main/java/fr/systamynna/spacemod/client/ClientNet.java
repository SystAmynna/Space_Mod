package fr.systamynna.spacemod.client;

import fr.systamynna.spacemod.core.SpaceNet.Drop;
import fr.systamynna.spacemod.core.SpaceNet.Reset;
import fr.systamynna.spacemod.core.SpaceNet.StateProbe;
import fr.systamynna.spacemod.core.SpaceNet.StateReport;
import fr.systamynna.spacemod.core.SpaceNet.Upsert;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Handlers côté client des paquets de {@link fr.systamynna.spacemod.core.SpaceNet} : alimentent la {@link ClientLayer}. */
public final class ClientNet {
    private ClientNet() {}

    public static void reset(Reset p, IPayloadContext ctx) { ClientLayer.INSTANCE.clear(); }

    public static void upsert(Upsert p, IPayloadContext ctx) { ClientLayer.INSTANCE.apply(p.uuid(), p.parent(), p.data()); }

    public static void drop(Drop p, IPayloadContext ctx) { ClientLayer.INSTANCE.drop(p.uuid()); }

    /** Le client calcule lui-même le statut de chaque abonnement : présent dans sa couche (OK) ou non (NONE). */
    public static void probe(StateProbe p, IPayloadContext ctx) {
        var ok = p.subs().stream().filter(ClientLayer.INSTANCE::has).toList();
        var none = p.subs().stream().filter(u -> !ClientLayer.INSTANCE.has(u)).toList();
        ctx.reply(new StateReport(p.requester(), ok, none));
    }
}
