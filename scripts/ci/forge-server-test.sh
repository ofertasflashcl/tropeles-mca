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
mkfifo in.fifo
( exec 3>in.fifo
  for i in $(seq 1 600); do
    if grep -q 'Done (' logs/latest.log 2>/dev/null; then break; fi; sleep 1
  done
  sleep 5
  if [ -f ../scripts/ci/commands.txt ]; then
    while IFS= read -r line; do
      case "$line" in
        sleep*) sleep "${line#sleep }";;
        ''|'#'*) ;;
        *) echo "$line" >&3;;
      esac
    done < ../scripts/ci/commands.txt
  fi
  echo stop >&3 ) &
server_status=0
timeout 1200 bash run.sh nogui < in.fifo > console.log 2>&1 || server_status=$?
wait || true
grep -q 'Done (' logs/latest.log || { echo "Server never finished starting"; tail -200 console.log; exit 1; }
if ls crash-reports/*.txt >/dev/null 2>&1; then echo "Crash report:"; cat crash-reports/*.txt | head -200; exit 1; fi
if grep -qE 'invalid mod file|Illegal version number' logs/latest.log; then echo "Forge rejected a mod file:"; grep -nE -A2 'invalid mod file|Illegal version' logs/latest.log; exit 1; fi
if grep -q 'PRUEBA FALLA' logs/latest.log; then grep -n 'PRUEBA' logs/latest.log; exit 1; fi
grep -n 'PRUEBA' logs/latest.log || true
[ "$server_status" -eq 0 ] || { echo "Server exited with status $server_status"; tail -200 console.log; exit 1; }
python3 "$CI_DIR/verify-checks.py" logs/latest.log "$CI_DIR/commands.txt" "$CI_DIR/expected-checks.tsv"
grep -nE 'Exception in server tick|Encountered an unexpected exception' logs/latest.log && exit 1
echo "Server booted and stopped cleanly with MCA loaded"
grep -nE "Found valid mod file minecraft-comes-alive|mca" logs/latest.log | head -5 || true
