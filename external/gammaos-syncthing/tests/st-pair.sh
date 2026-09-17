#!/bin/bash
# Share a folder both ways between the host peer and the RG DS Plus daemon.
# usage: st-pair.sh <folderId> <devicePath> [hostPath]
# The two devices must already know each other (see plan section 3); this adds the folder on
# both sides, shared with the other, and waits for both to report it idle.
S=${ST_TEST_DIR:-$(dirname $0)/work}
FID=$1; DPATH=$2; HPATH=${3:-$S/host_share/$FID}
DEV=$(cat $S/dev.id); HOST=$(cat $S/host.id)
mkdir -p "$HPATH"
adb -s aeaef76817ce3b95 shell "mkdir -p '$DPATH'; chown -R media_rw:media_rw '$DPATH'; chmod 2775 '$DPATH'" >/dev/null
hf=$($S/st-dev.sh host GET /rest/config/defaults/folder | python3 -c "
import sys,json; f=json.load(sys.stdin); f['id']='$FID'; f['label']='$FID'; f['path']='$HPATH'; f['devices']=[{'deviceID':'$DEV'}]; f['fsWatcherEnabled']=True; print(json.dumps(f))")
$S/st-dev.sh host PUT /rest/config/folders/$FID "$hf" >/dev/null
df=$($S/st-dev.sh dev GET /rest/config/defaults/folder | python3 -c "
import sys,json; f=json.load(sys.stdin); f['id']='$FID'; f['label']='$FID'; f['path']='$DPATH'; f['devices']=[{'deviceID':'$HOST'}]; f['fsWatcherEnabled']=True; f['ignorePerms']=True; print(json.dumps(f))")
$S/st-dev.sh dev PUT /rest/config/folders/$FID "$df" >/dev/null
for i in $(seq 1 30); do
  a=$($S/st-dev.sh host GET "/rest/db/status?folder=$FID" | python3 -c "import sys,json;print(json.load(sys.stdin).get('state'))" 2>/dev/null)
  b=$($S/st-dev.sh dev GET "/rest/db/status?folder=$FID" | python3 -c "import sys,json;print(json.load(sys.stdin).get('state'))" 2>/dev/null)
  [ "$a" = idle ] && [ "$b" = idle ] && { echo "paired $FID: host=$HPATH device=$DPATH"; exit 0; }
  sleep 2
done
echo "pair timeout: host=$a device=$b"; exit 1
