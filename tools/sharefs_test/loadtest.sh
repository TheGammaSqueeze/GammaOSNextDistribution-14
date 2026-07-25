#!/bin/bash
# Cross-protocol load test: all four shares under pressure at the same time.
#
# The per-protocol stability suite exercises one share in isolation. This is the case that isolation
# hides: four daemons, four connections and four FUSE mounts competing for the same CPU, the same
# network path and the same memory on a device that has little of any of them. Bugs that only show
# up under contention (a lock held too long, a buffer shared between mounts, memory that grows only
# when everything runs at once) need everything running at once to appear.
#
#   usage: loadtest.sh [minutes]

set -u
MIN="${1:-3}"
D() { adb shell "$@" 2>&1; }
declare -A MNT=( [SMB]=smoketest [NFS]=nfstest [WEBDAV]=davtest [FTP]=ftptest )
MED="ec1407c3860bab86b16c866ab6b1d5094203967cd27ea52277632a9f4be5f5ed"
SMALL="2e16a7b1fc0b50ebf936aa3aef94c2a016815b2f11275e5aa1266369644df8c3"
PASS=0; FAIL=0
ok()  { PASS=$((PASS+1)); printf "  PASS  %s\n" "$1"; }
bad() { FAIL=$((FAIL+1)); printf "  FAIL  %s -- %s\n" "$1" "${2:-}"; }
info(){ printf "  ....  %s: %s\n" "$1" "$2"; }

echo "=============================================================="
echo " cross-protocol load test (${MIN}min, all four shares at once)"
echo "=============================================================="

# Baseline resource use per daemon, so growth under load is attributable.
declare -A RSS0 FD0
for p in "${!MNT[@]}"; do
  PID=$(D "ps -A -o PID,ARGS | grep -- '--share ${MNT[$p]}' | grep -v grep | awk '{print \$1}'" | tr -d '[:space:]')
  RSS0[$p]=$(D "grep VmRSS /proc/$PID/status | awk '{print \$2}'" | tr -d '[:space:]')
  FD0[$p]=$(D "ls /proc/$PID/fd | wc -l" | tr -d '[:space:]')
  info "$p baseline" "pid=$PID rss=${RSS0[$p]}kB fds=${FD0[$p]}"
done

# ---- 1. all four reading concurrently, every read verified ----------------
echo "-- four concurrent verified readers (${MIN}min) --"
END=$(( $(date +%s) + MIN*60 ))
ROUND=0; ERRS=0
while [ "$(date +%s)" -lt "$END" ]; do
  ROUND=$((ROUND+1))
  # One shell, four background readers, so they genuinely overlap on the device.
  OUT=$(D "( for m in ${MNT[SMB]} ${MNT[NFS]} ${MNT[WEBDAV]} ${MNT[FTP]}; do
              ( sha256sum /mnt/shares/\$m/media/medium.bin 2>/dev/null | cut -d' ' -f1 ) &
            done; wait )")
  GOT=$(echo "$OUT" | tr -d '\r' | grep -c "$MED")
  [ "$GOT" = "4" ] || { ERRS=$((ERRS+1)); echo "    round $ROUND: only $GOT/4 reads correct"; }
done
[ $ERRS -eq 0 ] && ok "concurrent readers: $ROUND rounds x4 shares, all verified" \
                || bad "concurrent readers" "$ERRS/$ROUND rounds had a wrong or missing result"

# ---- 2. simultaneous large transfers --------------------------------------
# Every share pulling its own 64MB at the same time. This is the memory-pressure case: four
# read paths in flight on a device with a few hundred MB free.
echo "-- four simultaneous 64MB reads --"
T0=$(date +%s)
D "( for m in ${MNT[SMB]} ${MNT[NFS]} ${MNT[WEBDAV]} ${MNT[FTP]}; do
      ( dd if=/mnt/shares/\$m/media/large.bin of=/dev/null bs=1M 2>/dev/null ) &
    done; wait )" >/dev/null
T1=$(date +%s)
info "4x64MB concurrent" "$((T1-T0))s total ($(( 256 / ((T1-T0)>0 ? (T1-T0) : 1) )) MB/s aggregate)"

# ---- 3. many small operations ---------------------------------------------
# Metadata-heavy load rather than bandwidth: this is what a media scanner does, and it is where a
# per-operation round trip hurts most.
echo "-- metadata storm (200-entry listing on all four at once) --"
T0=$(date +%s%N)
D "( for m in ${MNT[SMB]} ${MNT[NFS]} ${MNT[WEBDAV]} ${MNT[FTP]}; do
      ( ls /mnt/shares/\$m/docs/many >/dev/null 2>&1 ) &
    done; wait )" >/dev/null
T1=$(date +%s%N)
info "4x readdir(200) concurrent" "$(( (T1-T0)/1000000 )) ms"

# ---- 4. concurrent writes --------------------------------------------------
echo "-- four simultaneous writes --"
STAMP="load$(date +%s)"
D "( for m in ${MNT[SMB]} ${MNT[NFS]} ${MNT[WEBDAV]} ${MNT[FTP]}; do
      ( dd if=/mnt/shares/\$m/media/small.bin of=/mnt/shares/\$m/writetest/${STAMP}_\$m.bin bs=64k 2>/dev/null ) &
    done; wait )" >/dev/null
sleep 3
WOK=0
for p in "${!MNT[@]}"; do
  f="/srv/gammashare/writetest/${STAMP}_${MNT[$p]}.bin"
  if [ -f "$f" ] && [ "$(sha256sum "$f" | cut -d' ' -f1)" = "$SMALL" ]; then
    WOK=$((WOK+1))
  else
    bad "concurrent write $p" "$([ -f "$f" ] && echo "wrong content ($(stat -c %s "$f") bytes)" || echo "missing on server")"
  fi
done
[ "$WOK" = "4" ] && ok "four concurrent writes, all byte-exact on the server"
rm -f /srv/gammashare/writetest/${STAMP}_* 2>/dev/null

# ---- 5. resource growth under all of the above -----------------------------
echo "-- resource growth --"
for p in "${!MNT[@]}"; do
  PID=$(D "ps -A -o PID,ARGS | grep -- '--share ${MNT[$p]}' | grep -v grep | awk '{print \$1}'" | tr -d '[:space:]')
  if [ -z "$PID" ]; then bad "$p daemon survived the load" "process is gone"; continue; fi
  R=$(D "grep VmRSS /proc/$PID/status | awk '{print \$2}'" | tr -d '[:space:]')
  F=$(D "ls /proc/$PID/fd | wc -l" | tr -d '[:space:]')
  DR=$(( ${R:-0} - ${RSS0[$p]:-0} )); DF=$(( ${F:-0} - ${FD0[$p]:-0} ))
  info "$p after load" "rss=${R}kB (${DR:+$DR}kB) fds=${F} (${DF:+$DF})"
  [ "$DF" -le 4 ] && ok "$p no descriptor leak (delta $DF)" || bad "$p descriptor leak" "+$DF fds"
  # The staged-upload buffer means the curl backends legitimately grow with the file being written.
  LIMIT=40000
  [ "$DR" -lt "$LIMIT" ] && ok "$p memory bounded (+${DR}kB)" || bad "$p memory growth" "+${DR}kB"
done

# ---- 6. everything still healthy -------------------------------------------
echo "-- post-load health --"
for p in "${!MNT[@]}"; do
  D "mountpoint -q /mnt/shares/${MNT[$p]} && echo y" | grep -q y \
      && ok "$p still mounted" || bad "$p still mounted" "mount is gone"
done
DEN=$(D "dmesg 2>/dev/null | grep -c 'avc:.*gammaos_sharefs'" | tr -d '[:space:]')
[ "${DEN:-0}" = "0" ] && ok "no SELinux denials under load" || bad "SELinux denials" "$DEN"

echo
echo "  load test: $PASS passed, $FAIL failed"
exit $FAIL
