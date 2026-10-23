package fr.systamynna.spacemod.knowledge;

import com.mojang.serialization.Codec;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.network.KnowledgeStatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * État « connaissance de l'espace » d'un joueur, indépendant du pilotage : le client dessine alors les astres proches,
 * leurs rails et le nom de l'astre regardé. Gardé sur le joueur (survit à la mort et aux reconnexions), renvoyé au client
 * à chaque connexion, réapparition et changement de dimension.
 */
public final class SpaceKnowledge {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AmynnaSpaceMod.MODID);
    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> STATE = ATTACHMENTS.register("space_knowledge",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

    /** Classe utilitaire. */
    private SpaceKnowledge() {}

    /** À appeler une fois, avec le bus du mod. */
    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    /** {@code true} si le joueur a l'état activé. */
    public static boolean isOn(ServerPlayer player) {
        return player.getData(STATE);
    }

    /** Active ou désactive l'état et prévient le client. */
    public static void set(ServerPlayer player, boolean on) {
        player.setData(STATE, on);
        sync(player);
    }

    private static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new KnowledgeStatePacket(isOn(player)));
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }
}
