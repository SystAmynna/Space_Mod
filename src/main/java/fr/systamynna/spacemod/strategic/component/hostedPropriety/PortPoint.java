package fr.systamynna.spacemod.strategic.component.hostedPropriety;

import fr.systamynna.spacemod.strategic.component.HostedDimension;
import net.minecraft.nbt.CompoundTag;

/**
 * Porte d'une {@link HostedDimension}, dans le repère local de l'objet : site d'atterrissage ou d'amarrage, qui porte
 * le volume logique (entrée dans la dimension) et le volume de déclenchement (réveil de la dimension).
 *
 * @param name nom du port, unique dans l'objet
 * @param x position du port, dans le repère local de l'objet
 * @param y idem
 * @param z idem
 * @param dx coordonnées, dans la dimension (en blocs), de l'origine du port : point d'arrivée d'un visiteur sans décalage
 * @param dy idem
 * @param dz idem
 * @param qx composante X du quaternion ajouté à la rotation de la {@link HostedDimension} pour rendre le ciel
 * @param qy idem en Y
 * @param qz idem en Z
 * @param qw composante réelle ; (0, 0, 0, 1) pour une station ou un croiseur
 * @param openingDegrees ouverture d'approche, 360 pour un port omnidirectionnel
 * @param approachCapacity rayon circonscrit maximal d'un objet admis à l'approche, 0 si aucune approche
 * @param logical volume dont l'entrée déclenche le transport
 * @param trigger volume dont l'entrée réveille la dimension ; au moins aussi grand que le volume logique
 */
public record PortPoint(String name, double x, double y, double z, double dx, double dy,
        double dz, double qx, double qy, double qz, double qw,
        double openingDegrees, double approachCapacity, LogicalVolume logical, TriggerVolume trigger) {
    /** Hauteur par défaut de l'origine d'un port dans sa dimension. */
    public static final double DEFAULT_DIMENSION_Y = 64;
    /** Rayon logique d'un port qui n'en déclare pas. */
    public static final double DEFAULT_RADIUS = 512;
    /** Rapport par défaut entre déclenchement et volume logique. */
    public static final double DEFAULT_TRIGGER_FACTOR = 2.0;

    /** @throws IllegalArgumentException si le quaternion est nul ou si le déclenchement est plus petit que le volume logique */
    public PortPoint {
        if (qx == 0 && qy == 0 && qz == 0 && qw == 0) throw new IllegalArgumentException("quaternion nul");
        if (trigger.radius() < logical.radius()) throw new IllegalArgumentException("trigger plus petit que le volume logique");
    }

    /** Port omnidirectionnel « principal » au centre de l'objet, sans rotation ni capacité d'approche. */
    public static PortPoint main(double radius) {
        return new PortPoint("principal", 0, 0, 0, 0, DEFAULT_DIMENSION_Y, 0, 0, 0, 0, 1, 360, 0, new LogicalVolume(radius),
                new TriggerVolume(radius * DEFAULT_TRIGGER_FACTOR));
    }

    /** Écrit le port en NBT. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        tag.putDouble("z", z);
        tag.putDouble("dx", dx);
        tag.putDouble("dy", dy);
        tag.putDouble("dz", dz);
        tag.putDouble("qx", qx);
        tag.putDouble("qy", qy);
        tag.putDouble("qz", qz);
        tag.putDouble("qw", qw);
        tag.putDouble("opening", openingDegrees);
        tag.putDouble("capacity", approachCapacity);
        tag.put("logical", logical.save());
        tag.put("trigger", trigger.save());
        return tag;
    }

    /** Relit un port écrit par {@link #save()}. */
    public static PortPoint load(CompoundTag tag) {
        return new PortPoint(tag.getString("name"), tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"),
                tag.getDouble("dx"), tag.contains("dy") ? tag.getDouble("dy") : DEFAULT_DIMENSION_Y, tag.getDouble("dz"),
                tag.getDouble("qx"), tag.getDouble("qy"), tag.getDouble("qz"), tag.getDouble("qw"),
                tag.getDouble("opening"), tag.getDouble("capacity"), LogicalVolume.load(tag.getCompound("logical")),
                TriggerVolume.load(tag.getCompound("trigger")));
    }
}
