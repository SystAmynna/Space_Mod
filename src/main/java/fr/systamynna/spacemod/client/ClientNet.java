package fr.systamynna.spacemod.client;

import fr.systamynna.spacemod.client.render.MajorStarsDrawer;
import fr.systamynna.spacemod.core.SpaceNet.Drop;
import fr.systamynna.spacemod.core.SpaceNet.Reset;
import fr.systamynna.spacemod.core.SpaceNet.MajorChunk;
import fr.systamynna.spacemod.core.SpaceNet.MajorDrop;
import fr.systamynna.spacemod.core.SpaceNet.MajorReset;
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

    public static void majorReset(MajorReset p, IPayloadContext ctx) { MajorStarsDrawer.INSTANCE.clear(); }

    public static void majorChunk(MajorChunk p, IPayloadContext ctx) { MajorStarsDrawer.INSTANCE.chunk(p); }

    public static void majorDrop(MajorDrop p, IPayloadContext ctx) { MajorStarsDrawer.INSTANCE.drop(p); }

    /** Le client calcule lui-même le statut de chaque abonnement : présent dans sa couche (OK) ou non (NONE). */
    public static void probe(StateProbe p, IPayloadContext ctx) {
        var ok = p.subs().stream().filter(ClientLayer.INSTANCE::has).toList();
        var none = p.subs().stream().filter(u -> !ClientLayer.INSTANCE.has(u)).toList();
        ctx.reply(new StateReport(p.requester(), ok, none));
    }
}
