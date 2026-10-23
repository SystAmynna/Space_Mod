package fr.systamynna.spacemod.client;

import com.mojang.blaze3d.platform.InputConstants;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.network.PilotInputPacket;
import fr.systamynna.spacemod.ship.PilotJoystick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Capture des entrées du pilote (côté client). Tant que le serveur le déclare pilote : la vue reste verrouillée vers
 * l'avant du vaisseau (le nord) et les mouvements de souris deviennent un joystick ({@link PilotJoystick}) ; les touches
 * avancer/reculer (Z/S) donnent la poussée, gauche/droite (Q/D) le roulis, saut (espace) le frein ; clics, molette et
 * déplacements du joueur sont neutralisés. ECHAP n'ouvre pas le menu : il rend les commandes.
 */
@EventBusSubscriber(modid = AmynnaSpaceMod.MODID, value = Dist.CLIENT)
public final class PilotClient {
    /** Lacet vers le nord (−Z) en degrés Minecraft. */
    private static final float LOCK_YAW = 180;
    private static final float LOCK_PITCH = 0;
    /** Intervalle minimal entre deux envois, en ticks. */
    private static final int MIN_SEND_INTERVAL = 2;
    /** Intervalle, en ticks, du battement de cœur : le serveur coupe les commandes après 60 ticks de silence. */
    private static final int HEARTBEAT_INTERVAL = 10;

    private static final PilotJoystick STICK = new PilotJoystick();
    private static boolean wasPiloting;
    private static int ticksSinceSend;
    private static PilotInputPacket lastSent;

    /** Classe utilitaire. */
    private PilotClient() {}

    /** Vrai quand les entrées sont capturées : pilote, en jeu, sans écran ouvert, souris saisie. */
    private static boolean capturing() {
        Minecraft mc = Minecraft.getInstance();
        return ClientPilotState.piloting() && mc.player != null && mc.screen == null && mc.mouseHandler.isMouseGrabbed();
    }

    /** Mesure ce que le jeu a tourné depuis le verrou, le donne au joystick, puis remet la vue sur le verrou. */
    private static void applyLock() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !ClientPilotState.piloting()) return;
        if (capturing()) STICK.move(Mth.wrapDegrees(player.getYRot() - LOCK_YAW), player.getXRot() - LOCK_PITCH);
        player.setYRot(LOCK_YAW);
        player.setXRot(LOCK_PITCH);
        player.yRotO = LOCK_YAW;
        player.xRotO = LOCK_PITCH;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        boolean piloting = ClientPilotState.piloting();
        if (piloting != wasPiloting) {
            STICK.reset();
            lastSent = null;
            wasPiloting = piloting;
        }
        if (!piloting) return;
        applyLock();
        Minecraft mc = Minecraft.getInstance();
        boolean active = capturing();
        double throttle = active ? (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0) : 0;
        double roll = active ? (mc.options.keyRight.isDown() ? 1 : 0) - (mc.options.keyLeft.isDown() ? 1 : 0) : 0;
        byte flags = active && mc.options.keyJump.isDown() ? PilotInputPacket.BRAKE : 0;
        PilotInputPacket packet = new PilotInputPacket(PilotInputPacket.quantize(throttle), PilotInputPacket.quantize(roll),
                PilotInputPacket.quantize(active ? STICK.yaw() : 0), PilotInputPacket.quantize(active ? STICK.pitch() : 0), flags);
        ticksSinceSend++;
        boolean changed = !packet.equals(lastSent);
        if ((changed && ticksSinceSend >= MIN_SEND_INTERVAL) || ticksSinceSend >= HEARTBEAT_INTERVAL) {
            PacketDistributor.sendToServer(packet);
            lastSent = packet;
            ticksSinceSend = 0;
        }
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        applyLock();
    }

    /** Même si la souris tourne la vue entre deux verrous, la caméra reste sur l'avant du vaisseau. */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!ClientPilotState.piloting()) return;
        event.setYaw(LOCK_YAW);
        event.setPitch(LOCK_PITCH);
    }

    /** Le joueur ne marche, ne saute et ne se baisse pas : seules les commandes du vaisseau comptent. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!ClientPilotState.piloting()) return;
        var input = event.getInput();
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (capturing()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (capturing()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (ClientPilotState.piloting()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** ECHAP ouvrirait le menu pause : on l'annule et on rend les commandes. */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!ClientPilotState.piloting() || !(event.getNewScreen() instanceof PauseScreen)) return;
        event.setCanceled(true);
        PacketDistributor.sendToServer(new PilotInputPacket((byte) 0, (byte) 0, (byte) 0, (byte) 0, PilotInputPacket.RELEASE));
        ClientPilotState.set(false, ClientPilotState.ship());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPilotState.set(false, null);
    }
}
