import java.util.*;

/**
 * B4 — Chargement et déchargement d'un système stellaire (prototype hors jeu).
 * Règle : un système est chargé si au moins un joueur s'y trouve ; après le départ du dernier joueur il attend
 * le délai de déchargement (60 s = 1200 ticks en test) avant d'être déchargé. À chaque chargement, le rattrapage
 * est appelé une fois avec la durée de jeu écoulée depuis la dernière simulation du système.
 * L'horloge est le temps de jeu : elle ne court pas pendant un arrêt du serveur.
 */
public class B4 {
    static final long DELAY = 1200;

    static final class Sys {
        boolean loaded;
        int players;
        long lastSimulated = 0;      // dernier tick de jeu où le système a été simulé
        long unloadAt = -1;
    }

    static final class Manager {
        final Map<String, Sys> systems = new TreeMap<>();
        final List<String> log = new ArrayList<>();
        final Map<String, Integer> loads = new TreeMap<>(), unloads = new TreeMap<>(), catchUps = new TreeMap<>();
        long now;

        Sys sys(String id) { return systems.computeIfAbsent(id, k -> new Sys()); }

        void arrive(String id) {
            Sys s = sys(id);
            s.players++;
            s.unloadAt = -1;                      // annule un déchargement en attente
            if (!s.loaded) load(id, s);
        }

        void leave(String id) {
            Sys s = sys(id);
            if (--s.players == 0) s.unloadAt = now + DELAY;
        }

        /** Saut : le système d'arrivée est chargé avant que le joueur ne quitte l'ancien. */
        void jump(String from, String to) { arrive(to); leave(from); }

        void tick(long t) {
            for (long k = now + 1; k <= t; k++) {
                now = k;
                for (var e : systems.entrySet()) {
                    Sys s = e.getValue();
                    if (s.loaded && s.unloadAt >= 0 && now >= s.unloadAt) unload(e.getKey(), s);
                    else if (s.loaded) s.lastSimulated = now;
                }
            }
        }

        void stopServer() {
            for (var e : systems.entrySet()) if (e.getValue().loaded) unload(e.getKey(), e.getValue());
            for (Sys s : systems.values()) s.players = 0;
        }

        void load(String id, Sys s) {
            s.loaded = true;
            long elapsed = now - s.lastSimulated;
            loads.merge(id, 1, Integer::sum);
            catchUps.merge(id, 1, Integer::sum);
            log.add(now + " charge " + id + " rattrapage de " + elapsed + " ticks");
            s.lastSimulated = now;
            lastElapsed.put(id, elapsed);
        }

        void unload(String id, Sys s) {
            s.loaded = false;
            s.unloadAt = -1;
            s.lastSimulated = now;               // le tick de déchargement est le dernier tick simulé
            unloads.merge(id, 1, Integer::sum);
            log.add(now + " décharge " + id);
        }

        final Map<String, Long> lastElapsed = new TreeMap<>();
    }

    static final StringBuilder OUT = new StringBuilder();
    static int fails;
    static void say(String s) { System.out.println(s); OUT.append(s).append('\n'); }
    static void check(String what, boolean ok) { if (!ok) fails++; say((ok ? "OK      " : "ÉCHEC   ") + what); }

    public static void main(String[] args) throws Exception {
        { // premier joueur, départ, déchargement après le délai, retour (déchargement puis chargement)
            Manager m = new Manager();
            m.tick(100);
            m.arrive("sol");
            check("premier joueur : chargement, rattrapage une fois, durée depuis le début du monde = 100", m.loads.get("sol") == 1 && m.lastElapsed.get("sol") == 100);
            m.tick(500); m.leave("sol");
            m.tick(500 + DELAY - 1);
            check("délai non écoulé (1199 ticks) : toujours chargé", m.systems.get("sol").loaded);
            m.tick(500 + DELAY);
            check("délai écoulé : déchargé exactement à t+1200", !m.systems.get("sol").loaded && m.log.contains((500 + DELAY) + " décharge sol"));
            m.tick(5000); m.arrive("sol");
            long expected = 5000 - (500 + DELAY);
            check("retour après déchargement : 2e chargement, rattrapage = durée depuis le déchargement (" + expected + ")", m.loads.get("sol") == 2 && m.lastElapsed.get("sol") == expected);
        }
        { // reconnexion avant le délai
            Manager m = new Manager();
            m.arrive("sol"); m.tick(300); m.leave("sol"); m.tick(800); m.arrive("sol"); m.tick(5000);
            check("reconnexion avant le délai : déchargement annulé, aucun deuxième chargement ni rattrapage", m.loads.get("sol") == 1 && m.unloads.getOrDefault("sol", 0) == 0 && m.systems.get("sol").loaded);
        }
        { // deux joueurs dans deux systèmes
            Manager m = new Manager();
            m.arrive("sol"); m.arrive("alpha"); m.tick(400); m.leave("sol"); m.tick(400 + DELAY + 1);
            check("deux joueurs, deux systèmes : celui qui part est déchargé, l'autre reste chargé", !m.systems.get("sol").loaded && m.systems.get("alpha").loaded);
        }
        { // saut d'un système à l'autre
            Manager m = new Manager();
            m.arrive("sol"); m.tick(1000);
            m.jump("sol", "alpha");
            int loadedAlphaBeforeSolUnload = m.loads.get("alpha");
            m.tick(1000 + DELAY - 1);
            boolean solStillLoaded = m.systems.get("sol").loaded;
            m.tick(1000 + DELAY);
            check("saut sol -> alpha : alpha chargé au moment du saut, sol encore chargé pendant le délai puis déchargé",
                loadedAlphaBeforeSolUnload == 1 && solStillLoaded && !m.systems.get("sol").loaded && m.systems.get("alpha").loaded);
        }
        { // arrêt du serveur pendant le délai, puis redémarrage
            Manager m = new Manager();
            m.arrive("sol"); m.tick(1000); m.leave("sol"); m.tick(1300);
            m.stopServer();
            check("arrêt pendant le délai : système déchargé proprement, une seule fois", !m.systems.get("sol").loaded && m.unloads.get("sol") == 1);
            m.tick(1300); // l'horloge de jeu ne court pas pendant l'arrêt
            m.arrive("sol");
            check("redémarrage : un seul rattrapage, durée = temps de jeu écoulé entre l'arrêt et le rechargement (0 ici, horloge arrêtée)", m.loads.get("sol") == 2 && m.lastElapsed.get("sol") == 0);
        }
        { // jamais de double chargement : arrivées multiples
            Manager m = new Manager();
            m.arrive("sol"); m.arrive("sol"); m.arrive("sol"); m.tick(10); m.leave("sol"); m.leave("sol"); m.tick(2000);
            check("trois joueurs arrivent puis deux repartent : un seul chargement, aucun déchargement tant qu'un joueur reste", m.loads.get("sol") == 1 && m.unloads.getOrDefault("sol", 0) == 0);
            m.leave("sol"); m.tick(2000 + DELAY + 1);
            check("le dernier joueur part : déchargement après le délai", !m.systems.get("sol").loaded && m.unloads.get("sol") == 1);
        }
        { // propriété : séquences aléatoires, invariants (graine 4)
            Random r = new Random(4);
            int violations = 0;
            for (int run = 0; run < 2000; run++) {
                Manager m = new Manager();
                String[] ids = { "sol", "alpha", "beta" };
                int[] pl = new int[3];
                Map<String, Long> unloadedAt = new HashMap<>();
                for (int step = 0; step < 60; step++) {
                    int i = r.nextInt(3);
                    int act = r.nextInt(3);
                    if (act == 0) { m.arrive(ids[i]); pl[i]++; }
                    else if (act == 1 && pl[i] > 0) { m.leave(ids[i]); pl[i]--; }
                    m.tick(m.now + r.nextInt(900));
                    for (int k = 0; k < 3; k++) {
                        Sys s = m.systems.get(ids[k]);
                        if (s == null) continue;
                        if (pl[k] > 0 && !s.loaded) violations++;                 // un système avec joueur doit être chargé
                        if (s.loaded && pl[k] == 0 && s.unloadAt < 0) violations++; // sans joueur il doit avoir un déchargement planifié
                    }
                }
                for (String id : ids) if (m.loads.getOrDefault(id, 0) != m.catchUps.getOrDefault(id, 0)) violations++;
                for (String id : ids) if (m.loads.getOrDefault(id, 0) - m.unloads.getOrDefault(id, 0) > 1) violations++;
            }
            check("2000 séquences aléatoires : " + violations + " violation(s) des invariants (chargé si joueur ; déchargement planifié sinon ; rattrapage = nombre de chargements ; pas de double chargement)", violations == 0);
        }
        say("\nFIN B4 : " + (fails == 0 ? "RÉUSSI" : "ÉCHEC (" + fails + ")"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("../test-results/B4/b4-run.log"), OUT.toString());
    }
}
