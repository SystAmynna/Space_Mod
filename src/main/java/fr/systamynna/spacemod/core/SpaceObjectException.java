package fr.systamynna.spacemod.core;

/** Erreur de règle métier sur l'arbre des objets spatiaux ; le message est une clé de traduction. */
public class SpaceObjectException extends RuntimeException {
    private final transient Object[] args;

    public SpaceObjectException(String langKey, Object... args) {
        super(langKey);
        this.args = args;
    }

    public Object[] args() { return args; }
}
