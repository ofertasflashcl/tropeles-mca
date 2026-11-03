#!/usr/bin/env bash
# Installs Forge 1.20.1, drops in MCA + Architectury, boots the server, runs the
# in-game checks (/mca-prueba) and fails if the server crashes or a check fails.
set -euo pipefail
JAR="$(realpath "$1")"
CI_DIR="$(cd "$(dirname "$0")" && pwd)"
python3 "$CI_DIR/test_verify_checks.py"
FORGE=1.20.1-47.4.20
ARCH=9.2.14
mkdir -p server/mods && cd server
curl -fsSL -o installer.jar "https://maven.minecraftforge.net/net/minecraftforge/forge/$FORGE/forge-$FORGE-installer.jar"
java -jar installer.jar --installServer > install.log 2>&1 || { tail -50 install.log; exit 1; }
curl -fsSL -o mods/architectury-forge.jar "https://maven.architectury.dev/dev/architectury/architectury-forge/$ARCH/architectury-forge-$ARCH.jar"
cp "$JAR" mods/
echo "eula=true" > eula.txt
cat > server.properties <<P
online-mode=false
level-type=minecraft\:flat
spawn-protection=0
max-tick-time=-1
P
CODE_SHA="$(git rev-parse HEAD)"
run_phase() {
  local phase="$1" commands="$2" manifest="$3"
  rm -f in.fifo logs/latest.log
  mkfifo in.fifo
  exec 3<>in.fifo
  timeout 1200 bash run.sh nogui < in.fifo > "console-$phase.log" 2>&1 &
  local server_pid=$!
  (
    local ready=false
    for i in $(seq 1 600); do
      if grep -q 'Done (' logs/latest.log 2>/dev/null; then ready=true; break; fi
      kill -0 "$server_pid" 2>/dev/null || exit 1
      sleep 1
    done
    [ "$ready" = true ] || exit 1
    sleep 5
    while IFS= read -r line; do
      case "$line" in
        sleep*) sleep "${line#sleep }";;
        ''|'#'*) ;;
        *) echo "$line" >&3;;
      esac
    done < "$commands"
    echo stop >&3
  ) &
  local feeder=$! status=0
  wait "$server_pid" || status=$?
  kill "$feeder" 2>/dev/null || true
  wait "$feeder" 2>/dev/null || true
  exec 3>&-
  rm -f in.fifo
  [ -f logs/latest.log ] || { tail -100 "console-$phase.log"; return 1; }
  cp logs/latest.log "logs/phase-$phase.log"
  [ "$status" -eq 0 ] || { tail -100 "console-$phase.log"; return 1; }
  grep -q 'Done (' logs/latest.log || { tail -100 "console-$phase.log"; return 1; }
  if ls crash-reports/*.txt >/dev/null 2>&1; then cat crash-reports/*.txt; return 1; fi
  if grep -qE 'invalid mod file|Illegal version number|PRUEBA FALLA|Exception in server tick|Encountered an unexpected exception' logs/latest.log; then
    grep -nE 'PRUEBA|ERROR|Exception|invalid mod' logs/latest.log; return 1
  fi
  grep -F "[Tropeles] jar $CODE_SHA version " logs/latest.log || { echo "Missing/mismatched jar SHA"; return 1; }
  python3 "$CI_DIR/verify-checks.py" logs/latest.log "$commands" "$manifest"
  grep -n 'PRUEBA' logs/latest.log || true
  echo "Server phase $phase booted and stopped cleanly with MCA loaded"
}
run_phase first "$CI_DIR/commands.txt" "$CI_DIR/expected-checks.tsv"
# Same saved world, new Java process: only reading, never reseeding the fixtures.
run_phase restart "$CI_DIR/restart-commands.txt" "$CI_DIR/restart-checks.tsv"
