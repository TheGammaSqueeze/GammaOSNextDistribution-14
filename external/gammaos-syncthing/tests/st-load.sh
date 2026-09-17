#!/bin/bash
# Load run: many small files then a few large ones from the host, watching the daemon's RSS, the
# device's memory pressure and whether nano / system_server survive. usage: st-load.sh <folderId> <devicePath> [hostPath] [smallCount] [bigMB]
S=${ST_TEST_DIR:-$(dirname $0)/work}
FID=$1; DPATH=$2; HPATH=${3:-$S/host_share/$FID}; N=${4:-10000}; BIG=${5:-200}; D=aeaef76817ce3b95
mon(){ adb -s $D shell 'P=""; B=0; for q in $(pidof syncthing); do r=$(grep VmRSS /proc/$q/status | awk "{print \$2}"); [ "${r:-0}" -gt "$B" ] && { B=$r; P=$q; }; done; echo "$(date +%T) rss=$(grep VmRSS /proc/$P/status | awk "{print \$2}")kB hwm=$(grep VmHWM /proc/$P/status | awk "{print \$2}")kB memavail=$(grep MemAvailable /proc/meminfo | awk "{print \$2}")kB cpu=$(awk "{print \$14+\$15}" /proc/$P/stat) nano=$(pidof gammaos-nano) psi=$(head -1 /proc/pressure/memory | cut -d" " -f2)"' | tr -d '\r'; }
echo "== baseline"; mon
echo "== $N small files"; mkdir -p "$HPATH/load/small"; python3 - "$HPATH/load/small" "$N" <<'PY'
import os,sys,random
d,n=sys.argv[1],int(sys.argv[2]); random.seed(1)
for i in range(n):
    sub=os.path.join(d,"d%03d"%(i%200)); os.makedirs(sub,exist_ok=True)
    with open(os.path.join(sub,"f%05d.txt"%i),"wb") as f: f.write(os.urandom(random.randint(100,4000)))
PY
t0=$(date +%s); $S/st-dev.sh host POST "/rest/db/scan?folder=$FID" >/dev/null
for i in $(seq 1 90); do sleep 10; mon; $S/st-wait.sh $FID 1 >/dev/null 2>&1 && break; done; echo "small files synced in $(( $(date +%s) - t0 )) s"; mon
echo "== big files (3 x ${BIG} MB)"; mkdir -p "$HPATH/load/big"; for i in 1 2 3; do head -c $((BIG*1024*1024)) /dev/urandom > "$HPATH/load/big/big$i.bin"; done
t0=$(date +%s); $S/st-dev.sh host POST "/rest/db/scan?folder=$FID" >/dev/null
for i in $(seq 1 120); do sleep 10; mon; $S/st-wait.sh $FID 1 >/dev/null 2>&1 && break; done; echo "big files synced in $(( $(date +%s) - t0 )) s"; mon
echo "== verify"; h=$(cd "$HPATH/load/big" && sha256sum big*.bin | cut -c1-16 | tr '\n' ' '); d=$(adb -s $D shell "cd '$DPATH/load/big' && sha256sum big*.bin | cut -c1-16 | tr '\n' ' '" | tr -d '\r'); [ "$h" = "$d" ] && echo "PASS big files identical" || echo "FAIL big files differ: [$h] vs [$d]"
c=$(adb -s $D shell "cd '$DPATH/load/small' && find . -type f | wc -l" | tr -d '\r'); [ "$c" = "$N" ] && echo "PASS $c small files present" || echo "FAIL small files: $c of $N"
echo "== kills / denials"; adb -s $D shell "logcat -d | grep -ciE 'lowmemorykiller.*(gammaos-nano|system_server)|Killing .* to free'; dmesg | grep -c 'avc: denied.*syncthing'"
