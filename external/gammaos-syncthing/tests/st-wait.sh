#!/bin/bash
# Wait until a folder is idle with nothing needed on both peers. usage: st-wait.sh <folderId> [timeoutS]
S=${ST_TEST_DIR:-$(dirname $0)/work}
FID=$1; T=${2:-300}; t0=$(date +%s)
while :; do
  a=$($S/st-dev.sh host GET "/rest/db/status?folder=$FID" | python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('state'),d.get('needBytes'))" 2>/dev/null)
  b=$($S/st-dev.sh dev GET "/rest/db/status?folder=$FID" | python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('state'),d.get('needBytes'))" 2>/dev/null)
  [ "$a" = "idle 0" ] && [ "$b" = "idle 0" ] && { echo "in sync after $(( $(date +%s) - t0 )) s"; exit 0; }
  [ $(( $(date +%s) - t0 )) -ge $T ] && { echo "TIMEOUT host=[$a] device=[$b]"; exit 1; }
  sleep 2
done
