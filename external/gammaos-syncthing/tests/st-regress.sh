#!/bin/bash
# Regression run over a paired folder: create, modify, rename, delete in both directions and a
# concurrent-edit conflict; verify content by sha256 on both sides. usage: st-regress.sh <folderId> <devicePath> [hostPath]
S=${ST_TEST_DIR:-$(dirname $0)/work}
FID=$1; DPATH=$2; HPATH=${3:-$S/host_share/$FID}; D=aeaef76817ce3b95
fail=0; ok(){ echo "PASS $1"; }; bad(){ echo "FAIL $1"; fail=1; }
hsum(){ (cd "$HPATH" && find . -type f -not -path './.stfolder*' -not -path './.stversions*' -not -name '.syncthing.*.tmp' -print0 | sort -z | xargs -0 sha256sum | sed 's#  \./#  #'); }
dsum(){ adb -s $D shell "cd '$DPATH' && find . -type f -not -path './.stfolder*' -not -path './.stversions*' -not -name '.syncthing.*.tmp' -print0 | sort -z | xargs -0 sha256sum | sed 's#  \./#  #'" | tr -d '\r'; }
scan(){ $S/st-dev.sh host POST "/rest/db/scan?folder=$FID" >/dev/null; $S/st-dev.sh dev POST "/rest/db/scan?folder=$FID" >/dev/null; sleep 3; }
same(){ if [ "$(hsum)" = "$(dsum)" ] && [ -n "$(hsum)" ]; then ok "$1 (both sides identical, $(hsum | wc -l) files)"; else bad "$1"; diff <(hsum) <(dsum) | head -5; fi; }
$S/st-dev.sh dev POST "/rest/db/scan?folder=$FID" >/dev/null; $S/st-dev.sh host POST "/rest/db/scan?folder=$FID" >/dev/null
echo "== host -> device: create"; mkdir -p "$HPATH/h2d/sub"; for i in 1 2 3; do head -c $((i*100000)) /dev/urandom > "$HPATH/h2d/file$i.bin"; done; echo "hello from host" > "$HPATH/h2d/sub/note.txt"
scan; $S/st-wait.sh $FID 120 || bad "wait h2d create"; same "h2d create"
echo "== device -> host: create"; adb -s $D shell "mkdir -p '$DPATH/d2h'; for i in 1 2 3; do head -c \$((i*150000)) /dev/urandom > '$DPATH/d2h/dev\$i.bin'; done; echo 'hello from device' > '$DPATH/d2h/note.txt'" >/dev/null
scan; $S/st-wait.sh $FID 120 || bad "wait d2h create"; same "d2h create"
echo "== modify both directions"; echo "changed on host" >> "$HPATH/h2d/sub/note.txt"; adb -s $D shell "echo 'changed on device' >> '$DPATH/d2h/note.txt'" >/dev/null
scan; $S/st-wait.sh $FID 120 || bad "wait modify"; same "modify"
echo "== rename + delete"; mv "$HPATH/h2d/file1.bin" "$HPATH/h2d/renamed1.bin"; rm -f "$HPATH/h2d/file2.bin"; adb -s $D shell "mv '$DPATH/d2h/dev1.bin' '$DPATH/d2h/moved1.bin'; rm -f '$DPATH/d2h/dev2.bin'" >/dev/null
scan; $S/st-wait.sh $FID 120 || bad "wait rename/delete"; same "rename+delete"
echo "== conflict (edit the same file on both sides while paused)"
$S/st-dev.sh dev PATCH /rest/config/folders/$FID '{"paused":true}' >/dev/null; sleep 2
echo "host edit" > "$HPATH/h2d/sub/note.txt"; adb -s $D shell "echo 'device edit' > '$DPATH/h2d/sub/note.txt'" >/dev/null
$S/st-dev.sh dev PATCH /rest/config/folders/$FID '{"paused":false}' >/dev/null; sleep 3; scan
$S/st-wait.sh $FID 120 || bad "wait conflict"
nconf=$(hsum | grep -c "sync-conflict"); if [ "$nconf" -ge 1 ]; then ok "conflict produced a sync-conflict copy ($nconf)"; else bad "conflict: no sync-conflict file"; fi; same "conflict resolution"
adb -s $D shell 'P=""; B=0; for q in $(pidof syncthing); do r=$(grep VmRSS /proc/$q/status | awk "{print \$2}"); [ "${r:-0}" -gt "$B" ] && { B=$r; P=$q; }; done; grep -E "VmRSS|VmHWM" /proc/$P/status | tr "\n" " "; echo; dmesg | grep -c "scontext=u:r:syncthing"' 
[ $fail = 0 ] && echo "REGRESSION: ALL PASS" || echo "REGRESSION: FAILURES"; exit $fail
