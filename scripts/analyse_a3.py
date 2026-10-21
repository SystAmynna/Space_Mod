"""Analyse du journal client du test A3 : python3 -I scripts/analyse_a3.py test-results/A3/client-frames.csv

Un événement commence quand la Mini ou le joueur bouge de plus de 0,5 bloc entre deux images (saut de pose) et
se termine quand les deux sont immobiles depuis 15 images (la Mini peut rester immobile plusieurs images avant d'être interpolée). Pour chaque événement : nombre d'images où le joueur
est décalé de plus de 0,05 bloc par rapport à sa position relative d'avant, décalage maximal, durée, et plus
longue image (gel du client). Critères du plan : au plus 1 image décalée = RÉUSSI ; 2 à 6 images (environ 1 tick) =
PARTIEL ; au-delà ou gel de plus de 200 ms ou joueur laissé en arrière = ÉCHEC.
"""
import csv
import math
import sys

JUMP = 0.5
TOL = 0.05


def load(path):
    rows = []
    with open(path, newline="") as f:
        for r in csv.DictReader(f):
            rows.append({k: (int(v) if k in ("t_ns", "suivi") else float(v)) for k, v in r.items()})
    return rows


def sub(r):
    return (r["sx"], r["sy"], r["sz"])


def ply(r):
    return (r["px"], r["py"], r["pz"])


def rel(r):
    return tuple(ply(r)[i] - sub(r)[i] for i in range(3))


def main(path):
    rows = load(path)
    print(f"{len(rows)} images")
    i, n, events = 1, len(rows), 0
    while i < n:
        a, b = rows[i - 1], rows[i]
        if math.dist(sub(a), sub(b)) <= JUMP and math.dist(ply(a), ply(b)) <= JUMP:
            i += 1
            continue
        events += 1
        ref = rel(a)
        j, calm, bad, worst, longest = i, 0, 0, 0.0, 0.0
        while j < n and calm < 15:
            moved = math.dist(sub(rows[j]), sub(rows[j - 1])) + math.dist(ply(rows[j]), ply(rows[j - 1]))
            calm = calm + 1 if moved < 0.01 else 0
            if j - i > 240:
                break  # Mini en mouvement continu (move) : fenêtre limitée à 240 images
            err = math.dist(rel(rows[j]), ref)
            if err > TOL:
                bad += 1
            worst = max(worst, err)
            longest = max(longest, (rows[j]["t_ns"] - rows[j - 1]["t_ns"]) / 1e6)
            j += 1
        final_err = math.dist(rel(rows[min(j, n) - 1]), ref)
        jump = math.dist(sub(a), sub(rows[min(j, n) - 1]))
        if final_err > TOL or longest > 200:
            verdict = "ÉCHEC"
        else:
            verdict = "OK" if bad <= 1 else ("PARTIEL" if bad <= 6 else "ÉCHEC")
        print(f"événement {events} à l'image {i} : Mini déplacée de {jump:.1f} blocs, images décalées={bad}, "
              f"décalage max={worst:.2f} bloc, écart final={final_err:.2f}, plus longue image={longest:.0f} ms -> {verdict}")
        i = j
    print(f"{events} événement(s)")


if __name__ == "__main__":
    main(sys.argv[1])
