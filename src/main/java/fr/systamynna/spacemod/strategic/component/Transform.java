package fr.systamynna.spacemod.strategic.component;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Translation, rotation (quaternion) et échelle d'un objet dans le référentiel de son parent. Tout en double :
 * jamais de float (NBT range -0.0 en +0.0 : sans effet sur une position). Un objet dont le contrôleur détermine la
 * position ({@link Controller#determinesPosition()}) ignore la translation stockée.
 * <p>
 * Pour un objet qui est un {@link Frame}, le Transform est la conversion complète vers le référentiel du parent
 * ({@link #apply}), l'échelle étant la conversion d'unités. Pour un corps, le parent ne reçoit que la translation.
 * <p>
 * L'ordre de composition est celui d'un graphe de scène : un point {@code p} du référentiel local devient
 * {@code T + R·(S·p)} dans celui du parent (échelle d'abord, puis rotation, puis translation).
 *
 * @param tx translation en X, dans l'unité du parent
 * @param ty translation en Y
 * @param tz translation en Z
 * @param qx composante X du quaternion de rotation
 * @param qy composante Y du quaternion de rotation
 * @param qz composante Z du quaternion de rotation
 * @param qw composante réelle du quaternion de rotation ; le quaternion n'a pas besoin d'être unitaire (normalisé à
 *        l'usage) mais ne doit pas être nul
 * @param sx échelle en X (1 = même unité que le parent ; pour un cadre, nombre d'unités du parent par unité locale)
 * @param sy échelle en Y
 * @param sz échelle en Z
 */
public record Transform(
        double tx, double ty, double tz,
        double qx, double qy, double qz, double qw,
        double sx, double sy, double sz) implements Component {

    /** Transform neutre : à l'origine, sans rotation, à l'échelle 1. */
    public static final Transform ORIGIN = at(0, 0, 0);

    /** @throws IllegalArgumentException si le quaternion est nul (aucune rotation ne lui correspond) */
    public Transform {
        if (qx == 0 && qy == 0 && qz == 0 && qw == 0) throw new IllegalArgumentException("quaternion nul");
    }

    /** Transform à la position donnée, sans rotation et à l'échelle 1. */
    public static Transform at(double x, double y, double z) {
        return new Transform(x, y, z, 0, 0, 0, 1, 1, 1, 1);
    }

    /** Copie avec une autre translation ; rotation et échelle sont conservées. */
    public Transform withTranslation(double x, double y, double z) {
        return new Transform(x, y, z, qx, qy, qz, qw, sx, sy, sz);
    }

    /** Copie avec une autre rotation (quaternion x, y, z, w) ; translation et échelle sont conservées. */
    public Transform withRotation(double x, double y, double z, double w) {
        return new Transform(tx, ty, tz, x, y, z, w, sx, sy, sz);
    }

    /** Copie avec une autre échelle ; translation et rotation sont conservées. */
    public Transform withScale(double x, double y, double z) {
        return new Transform(tx, ty, tz, qx, qy, qz, qw, x, y, z);
    }

    /** Translation seule, sous la forme {@code {x, y, z}}. */
    public double[] translation() {
        return new double[] { tx, ty, tz };
    }

    /**
     * Copie avec, en plus, une rotation d'{@code angle} radians autour de l'axe Y du référentiel du parent, appliquée
     * après la rotation stockée. Sert à la rotation propre d'un astre (spin).
     */
    public Transform spun(double angle) {
        // quaternion de la rotation autour de Y : (0, sin(a/2), 0, cos(a/2))
        double sa = StrictMath.sin(angle / 2);
        double ca = StrictMath.cos(angle / 2);
        // produit de Hamilton (0, sa, 0, ca) ⊗ (qx, qy, qz, qw) : la nouvelle rotation s'applique après la stockée
        return withRotation(
                ca * qx + sa * qz,
                ca * qy + sa * qw,
                ca * qz - sa * qx,
                ca * qw - sa * qy);
    }

    /**
     * Partie linéaire seule (sans translation) : {@code R·(S·p)}. C'est ce qu'un cadre applique aux vecteurs de ses
     * descendants.
     */
    public double[] linear(double[] p) {
        return rotate(new double[] { sx * p[0], sy * p[1], sz * p[2] }, false);
    }

    /** Point {@code p} de ce référentiel, exprimé dans celui du parent : {@code T + R·(S·p)}. */
    public double[] apply(double[] p) {
        double[] r = linear(p);
        return new double[] { tx + r[0], ty + r[1], tz + r[2] };
    }

    /**
     * Réciproque de {@link #apply} : point {@code p} du référentiel du parent, exprimé dans celui-ci
     * ({@code S⁻¹·R⁻¹·(p − T)}).
     *
     * @throws IllegalStateException si une des échelles est nulle (la transformation n'est pas inversible)
     */
    public double[] invert(double[] p) {
        if (sx == 0 || sy == 0 || sz == 0) throw new IllegalStateException("échelle nulle : transformation non inversible");
        // on retire d'abord la translation, on défait la rotation (quaternion conjugué), puis l'échelle
        double[] r = rotate(new double[] { p[0] - tx, p[1] - ty, p[2] - tz }, true);
        return new double[] { r[0] / sx, r[1] / sy, r[2] / sz };
    }

    /**
     * Applique la rotation du quaternion au vecteur {@code v}, ou la rotation inverse.
     *
     * @param inverse {@code true} pour la rotation réciproque (quaternion conjugué)
     */
    private double[] rotate(double[] v, boolean inverse) {
        // le quaternion est normalisé ici : il n'est pas imposé unitaire à la construction
        double n = Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
        // pour un quaternion unitaire, le conjugué (partie vectorielle opposée) est l'inverse
        double x = (inverse ? -qx : qx) / n;
        double y = (inverse ? -qy : qy) / n;
        double z = (inverse ? -qz : qz) / n;
        double w = qw / n;
        // rotation de v par q : v' = v + w·a + (u × a) avec u = (x, y, z) et a = 2·(u × v) — formule à deux produits
        // vectoriels, sans construire de matrice
        double a = 2 * (y * v[2] - z * v[1]);
        double b = 2 * (z * v[0] - x * v[2]);
        double c = 2 * (x * v[1] - y * v[0]);
        return new double[] { v[0] + w * a + (y * c - z * b), v[1] + w * b + (z * a - x * c), v[2] + w * c + (x * b - y * a) };
    }

    /** Identifiant du type de ce composant dans le registre {@link ComponentTypes}. */
    @Override
    public ResourceLocation typeId() {
        return ComponentTypes.TRANSFORM.id();
    }

    /** Écrit le composant en NBT ; relu à l'identique par {@link #load}. */
    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("tx", tx);
        tag.putDouble("ty", ty);
        tag.putDouble("tz", tz);
        tag.putDouble("qx", qx);
        tag.putDouble("qy", qy);
        tag.putDouble("qz", qz);
        tag.putDouble("qw", qw);
        tag.putDouble("sx", sx);
        tag.putDouble("sy", sy);
        tag.putDouble("sz", sz);
        return tag;
    }

    /** Relit un Transform écrit par {@link #save()}. */
    static Transform load(CompoundTag tag) {
        return new Transform(
                tag.getDouble("tx"), tag.getDouble("ty"), tag.getDouble("tz"),
                tag.getDouble("qx"), tag.getDouble("qy"), tag.getDouble("qz"), tag.getDouble("qw"),
                tag.getDouble("sx"), tag.getDouble("sy"), tag.getDouble("sz"));
    }
}
