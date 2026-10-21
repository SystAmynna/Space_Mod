#!/bin/bash
# Orchestration de C5 : arrêt propre, plantage (kill -9) et plantage en pleine écriture. Usage : scripts/c5.sh
cd "$(dirname "$0")/.." || exit 1
R=test-results/C5
mkdir -p $R
rm -f $R/c5-run.log
rm -rf run-dev-server/world
run() { ./gradlew runServerDevMods "$@" > /tmp/claude-1000/c5-gradle.out 2>&1; }
for action in ${C5_ACTIONS:-stop kill killser}; do
  rm -f $R/READY
  rm -rf run-dev-server/world
  echo "=== setup $action ==="
  echo "$action" > $R/last-action.txt
  run -Pc5=setup -Pc5action=$action &
  GP=$!
  if [ "$action" = stop ]; then
    wait $GP
  else
    until [ -f $R/READY ]; do sleep 1; done
    sleep 1
    pkill -9 -f '[f]ml.modFolders'
    wait $GP 2>/dev/null
    sleep 3
  fi
  echo "=== check après $action ==="
  run -Pc5=check
done
echo FINI
