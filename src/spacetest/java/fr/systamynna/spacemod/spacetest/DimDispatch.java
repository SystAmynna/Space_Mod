package fr.systamynna.spacemod.spacetest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Crée une dimension de test vide avec la bibliothèque choisie (-Pbackend). */
final class DimDispatch {
    private DimDispatch() {}

    static ServerLevel create(MinecraftServer server, ResourceLocation id) {
        ServerLevel l = DimBackend.select().create(server, id, A1Runner.voidGenerator(server), A1Runner.dimensionType(0f));
        if (l == null) throw new IllegalStateException("création de " + id + " impossible");
        return l;
    }
}
