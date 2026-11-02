package fr.systamynna.spacemod.core.clock;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistance de l'horloge serveur (données de l'overworld) : elle ne progresse pas serveur éteint. */
public final class ClockData extends SavedData {
    private static volatile ClockData current;

    private ClockData(double time, double multiplier) {
        SpaceClock.SERVER.reset(time, multiplier);
    }

    /** À appeler au démarrage du serveur. */
    public static void load(MinecraftServer server) {
        current = server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(() -> new ClockData(0, 1),
                        (tag, p) -> new ClockData(tag.getDouble("time"), tag.getDouble("multiplier"))),
                "amynnaspacemod_clock");
    }

    /** À appeler à chaque sauvegarde du monde (la valeur écrite est celle de {@code save}). */
    public static void markDirty() {
        if (current != null) current.setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putDouble("time", SpaceClock.SERVER.now());
        tag.putDouble("multiplier", SpaceClock.SERVER.multiplier());
        return tag;
    }
}
