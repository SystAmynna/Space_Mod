package fr.systamynna.spacemod.client;

import fr.systamynna.spacemod.AmynnaSpaceMod;
import fr.systamynna.spacemod.client.render.KnowledgeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Affiche, sous le viseur, le nom et la distance de l'astre regardé (état « connaissance de l'espace »). */
@EventBusSubscriber(modid = AmynnaSpaceMod.MODID, value = Dist.CLIENT)
public final class KnowledgeLabelEvents {
    private static final double AU = 1.495978707e11;
    private static final double LIGHT_YEAR = 9.4607e15;

    /** Classe utilitaire. */
    private KnowledgeLabelEvents() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        KnowledgeRenderer.Looked looked = KnowledgeRenderer.looked();
        if (looked == null || !ClientKnowledgeState.on()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        GuiGraphics graphics = event.getGuiGraphics();
        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2 + 14;
        graphics.drawCenteredString(mc.font, looked.name(), x, y, 0xFFFFFF);
        graphics.drawCenteredString(mc.font, distance(looked.distance()), x, y + 11, 0xAAAAAA);
    }

    /** Distance lisible : km, UA ou années-lumière. */
    static String distance(double meters) {
        if (meters >= LIGHT_YEAR / 10) return String.format("%.2f al", meters / LIGHT_YEAR);
        if (meters >= AU / 10) return String.format("%.2f UA", meters / AU);
        return String.format("%,.0f km", meters / 1000);
    }
}
