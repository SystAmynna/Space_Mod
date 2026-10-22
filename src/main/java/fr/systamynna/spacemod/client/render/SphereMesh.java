package fr.systamynna.spacemod.client.render;

/**
 * Sphère unité en grille longitude/latitude, sommets dupliqués à la couture pour que {@code u} aille de 0 à 1. Le pôle
 * nord est en +Y ; la longitude croît vers la droite vue de l'extérieur (texture équirectangulaire non inversée).
 */
final class SphereMesh {
    final int sectors;
    final int rings;
    final float[] x;
    final float[] y;
    final float[] z;
    final float[] u;
    final float[] v;

    private SphereMesh(int sectors, int rings) {
        this.sectors = sectors;
        this.rings = rings;
        int n = (sectors + 1) * (rings + 1);
        x = new float[n];
        y = new float[n];
        z = new float[n];
        u = new float[n];
        v = new float[n];
    }

    static SphereMesh build(int sectors, int rings) {
        SphereMesh m = new SphereMesh(sectors, rings);
        for (int j = 0; j <= rings; j++) {
            double lat = Math.PI / 2 - Math.PI * j / rings;
            for (int i = 0; i <= sectors; i++) {
                double lon = 2 * Math.PI * i / sectors;
                int k = m.index(i, j);
                m.x[k] = (float) (Math.cos(lat) * Math.cos(lon));
                m.y[k] = (float) Math.sin(lat);
                m.z[k] = (float) -(Math.cos(lat) * Math.sin(lon));
                m.u[k] = (float) i / sectors;
                m.v[k] = (float) j / rings;
            }
        }
        return m;
    }

    int index(int sector, int ring) {
        return ring * (sectors + 1) + sector;
    }

    int vertexCount() {
        return x.length;
    }
}
