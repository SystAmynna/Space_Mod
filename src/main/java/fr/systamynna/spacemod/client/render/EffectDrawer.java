package fr.systamynna.spacemod.client.render;

/** Base des effets du système local à shader (étoile, trou noir, anneau…) : dessinés dans l'ordre loin→près avec les éléments texturés. */
abstract class EffectDrawer extends SkyShaderDrawer {
    @Override
    public boolean custom() { return false; }

    @Override
    public boolean overlay() { return true; }
}
