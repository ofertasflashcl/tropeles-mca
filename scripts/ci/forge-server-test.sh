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
# Forge's default launch file has no explicit heap. Keep the two-process suite within a known CI budget,
# and fail immediately on OOM instead of leaving an apparently running process with no test commands.
cat > user_jvm_args.txt <<'J'
-Xms512M
-Xmx2G
-XX:+ExitOnOutOfMemoryError
J
java @user_jvm_args.txt -XX:+PrintFlagsFinal -version 2>&1 | awk '/InitialHeapSize|MaxHeapSize/ {print}'
curl -fsSL -o mods/architectury-forge.jar "https://maven.architectury.dev/dev/architectury/architectury-forge/$ARCH/architectury-forge-$ARCH.jar"
cp "$JAR" mods/
echo "eula=true" > eula.txt
# The CI world uses local replies and loopback HTTP fixtures. The live provider
# smoke test is still available separately as /mca-prueba ia-red.
mkdir -p config
cat > config/mca.json <<'MCA_CONFIG'
{
  "version": 1,
  "tropelesChatDefaults": 2,
  "tropelesMinds": ["custom"],
  "villagerChatAIEndpoint": "",
  "enableOnlineTTS": false
}
MCA_CONFIG
TROPELES_TEST_NETWORK='-Djava.net.useSystemProxies=false -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=1 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=1 -Dhttp.nonProxyHosts=localhost|127.*'
cat > server.properties <<P
online-mode=false
server-ip=127.0.0.1
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
  JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} $TROPELES_TEST_NETWORK" timeout 1200 bash run.sh nogui < in.fifo > "console-$phase.log" 2>&1 &
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
    echo "CI feeding phase $phase from $commands"
    while IFS= read -r line; do
      case "$line" in
        await-checks*)
          local expected terminal=0
          expected=$(awk 'NF && $1 !~ /^#/ {n++} END {print n}' "$manifest")
          for i in $(seq 1 "${line#await-checks }"); do
            terminal=$(grep -cE 'PRUEBA (OK|FALLA) ' logs/latest.log || true)
            if [ "$terminal" -ge "$expected" ]; then break; fi
            kill -0 "$server_pid" 2>/dev/null || exit 1
            sleep 1
          done
          ;;
        sleep*) sleep "${line#sleep }";;
        ''|'#'*) ;;
        *)
          if [ "$phase" = restart ]; then echo "CI restart command: $line"; fi
          echo "$line" >&3
          ;;
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
  mkdir -p logs
  cp "console-$phase.log" "logs/console-$phase.log"
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
python3 - <<'PY' > logs/world-sizes.log
from pathlib import Path
files = [(p.stat().st_size, str(p)) for p in Path('world').rglob('*') if p.is_file()]
print('World bytes:', sum(n for n, _ in files), 'files:', len(files))
for n, p in sorted(files, reverse=True)[:20]: print(n, p)
PY
cat logs/world-sizes.log
# Same saved world, new Java process: only reading, never reseeding the fixtures.
run_phase restart "$CI_DIR/restart-commands.txt" "$CI_DIR/restart-checks.tsv"
