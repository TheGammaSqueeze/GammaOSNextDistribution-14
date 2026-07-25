#!/bin/bash
# Cross-protocol load test: every mounted share under pressure at the same time.
#
# The per-protocol stability suite exercises one share in isolation. This is the case that isolation
# hides: several daemons, several connections and several FUSE mounts competing for the same CPU,
# the same network path and the same memory on a device that has little of any of them. Bugs that
# only appear under contention (a lock held too long, a buffer shared between mounts, memory that
# grows only when everything runs at once) need everything running at once to show up. The FTP
# control-connection desync was exactly this shape.
#
#   usage: loadtest.sh [minutes]

set -u
MIN="${1:-3}"
MED="ec1407c3860bab86b16c866ab6b1d5094203967cd27ea52277632a9f4be5f5ed"
SMALL="2e16a7b1fc0b50ebf936aa3aef94c2a016815b2f11275e5aa1266369644df8c3"
PASS=0; FAIL=0
ok()  { PASS=$((PASS+1)); printf "  PASS  %s\n" "$1"; }
bad() { FAIL=$((FAIL+1)); printf "  FAIL  %s -- %s\n" "$1" "${2:-}"; }
info(){ printf "  ....  %s: %s\n" "$1" "$2"; }

# Every adb call closes stdin. Without this, an adb shell inside a `while read` loop swallows the
# rest of the loop's input and the loop silently runs once - which is exactly how an earlier version
# of this file "discovered" one share out of four and then reported the rest as failures.
D() { adb shell "$@" </dev/null 2>&1; }

# ---- discover what is actually mounted -------------------------------------
# The names come from whatever the menu created, and the removal test deliberately takes one away,
# so a hardcoded list tests either nothing or a share that no longer exists. Names are user-chosen
# and contain a space by default, which is why every remote command below quotes them rather than
# relying on word splitting.
declare -A PROTO=()
NAMES=()
for slot in 1 2 3 4; do
  NAME=$(D "getprop persist.gammaos.share.$slot.name" | tr -d '\r')
  TYPE=$(D "getprop persist.gammaos.share.$slot.type" | tr -d '\r')
  EN=$(D "getprop persist.gammaos.share.$slot.enabled" | tr -d '\r')
  [ -n "$NAME" ] && [ "$EN" = "1" ] || continue
  D "ls '/mnt/shares/$NAME' >/dev/null 2>&1 && echo y" | grep -q y || continue
  NAMES+=("$NAME")
  PROTO["$NAME"]="$TYPE"
done
N=${#NAMES[@]}
[ "$N" -gt 0 ] || { echo "no mounted shares to load-test"; exit 1; }

echo "=============================================================="
echo " cross-protocol load test (${MIN}min, $N shares at once)"
echo "=============================================================="
for n in "${NAMES[@]}"; do printf "  ....  %-8s %s\n" "${PROTO[$n]}" "$n"; done

# Run one command per share concurrently on the device. @M@ becomes the quoted mount path, so a
# name with a space stays a single argument.
par() {
  local tmpl="$1" body="" n cmd
  for n in "${NAMES[@]}"; do
    cmd=${tmpl//@M@/\'/mnt/shares/$n\'}
    body+="( $cmd ) & "
  done
  D "$body wait"
}

pid_of() { D "ps -A -o PID,ARGS | grep -- \"--share $1\" | grep -v grep | awk '{print \$1}'" | tr -d '[:space:]'; }

declare -A RSS0 FD0
for n in "${NAMES[@]}"; do
  PID=$(pid_of "$n")
  RSS0["$n"]=$(D "grep VmRSS /proc/$PID/status | awk '{print \$2}'" | tr -d '[:space:]')
  FD0["$n"]=$(D "ls /proc/$PID/fd | wc -l" | tr -d '[:space:]')
  info "${PROTO[$n]} baseline" "pid=$PID rss=${RSS0[$n]}kB fds=${FD0[$n]}"
done

# ---- 1. all shares reading concurrently, every read verified ---------------
echo "-- $N concurrent verified readers (${MIN}min) --"
END=$(( $(date +%s) + MIN*60 ))
ROUND=0; ERRS=0
while [ "$(date +%s)" -lt "$END" ]; do
  ROUND=$((ROUND+1))
  OUT=$(par "sha256sum @M@/media/medium.bin 2>/dev/null | cut -d' ' -f1")
  GOT=$(echo "$OUT" | tr -d '\r' | grep -c "$MED")
  [ "$GOT" = "$N" ] || { ERRS=$((ERRS+1)); echo "    round $ROUND: only $GOT/$N reads correct"; }
done
[ $ERRS -eq 0 ] && ok "concurrent readers: $ROUND rounds x$N shares, all verified" \
                || bad "concurrent readers" "$ERRS/$ROUND rounds had a wrong or missing result"

# ---- 2. simultaneous large transfers --------------------------------------
echo "-- $N simultaneous 64MB reads --"
T0=$(date +%s)
par "dd if=@M@/media/large.bin of=/dev/null bs=1M 2>/dev/null" >/dev/null
T1=$(date +%s)
SECS=$(( T1-T0 )); [ "$SECS" -gt 0 ] || SECS=1
info "${N}x64MB concurrent" "${SECS}s total ($(( (N*64) / SECS )) MB/s aggregate)"

# ---- 3. many small operations ---------------------------------------------
echo "-- metadata storm (200-entry listing on all $N at once) --"
T0=$(date +%s%N)
par "ls @M@/docs/many >/dev/null 2>&1" >/dev/null
T1=$(date +%s%N)
info "${N}x readdir(200) concurrent" "$(( (T1-T0)/1000000 )) ms"

# ---- 4. concurrent writes --------------------------------------------------
echo "-- $N simultaneous writes --"
STAMP="load$(date +%s)"
BODY=""; i=0
for n in "${NAMES[@]}"; do
  i=$((i+1))
  BODY+="( dd if='/mnt/shares/$n/media/small.bin' of='/mnt/shares/$n/writetest/${STAMP}_$i.bin' bs=64k 2>/dev/null ) & "
done
D "$BODY wait" >/dev/null
sleep 5
WOK=0; i=0
for n in "${NAMES[@]}"; do
  i=$((i+1))
  f="/srv/gammashare/writetest/${STAMP}_$i.bin"
  if [ -f "$f" ] && [ "$(sha256sum "$f" | cut -d' ' -f1)" = "$SMALL" ]; then
    WOK=$((WOK+1))
  else
    bad "concurrent write ${PROTO[$n]}" \
        "$([ -f "$f" ] && echo "wrong content ($(stat -c %s "$f") bytes)" || echo "missing on server")"
  fi
done
[ "$WOK" = "$N" ] && ok "$N concurrent writes, all byte-exact on the server"
rm -f /srv/gammashare/writetest/${STAMP}_* 2>/dev/null

# ---- 5. resource growth under all of the above -----------------------------
echo "-- resource growth --"
for n in "${NAMES[@]}"; do
  PID=$(pid_of "$n")
  if [ -z "$PID" ]; then bad "${PROTO[$n]} daemon survived the load" "process is gone"; continue; fi
  R=$(D "grep VmRSS /proc/$PID/status | awk '{print \$2}'" | tr -d '[:space:]')
  F=$(D "ls /proc/$PID/fd | wc -l" | tr -d '[:space:]')
  DR=$(( ${R:-0} - ${RSS0[$n]:-0} )); DF=$(( ${F:-0} - ${FD0[$n]:-0} ))
  info "${PROTO[$n]} after load" "rss=${R}kB (${DR}kB) fds=${F} (${DF})"
  [ "$DF" -le 4 ] && ok "${PROTO[$n]} no descriptor leak (delta $DF)" \
                  || bad "${PROTO[$n]} descriptor leak" "+$DF fds"
  # The staged-upload buffer means the curl backends legitimately grow with the file being written.
  [ "$DR" -lt 40000 ] && ok "${PROTO[$n]} memory bounded (+${DR}kB)" \
                      || bad "${PROTO[$n]} memory growth" "+${DR}kB"
done

# ---- 6. everything still healthy -------------------------------------------
echo "-- post-load health --"
MI=$(D "cat /proc/self/mountinfo" | tr -d '\r')
for n in "${NAMES[@]}"; do
  # Ask the mount table AND read through it. mountpoint -q and ls both stat, and a stale FUSE mount
  # fails a stat with ENOTCONN exactly as a missing one fails with ENOENT, so the worst outcome -
  # the mount still listed with no daemon behind it - would otherwise score as a clean pass.
  CNT=$(printf '%s\n' "$MI" | awk -v want="/mnt/shares/$n" \
        '{mp=$5; gsub(/\\040/," ",mp); if (mp==want) c++} END{print c+0}')
  ALIVE=$(D "ls '/mnt/shares/$n' >/dev/null 2>&1 && echo y" | grep -c y)
  if [ "$CNT" -ge 1 ] && [ "$ALIVE" = "1" ]; then ok "${PROTO[$n]} still mounted and serving"
  elif [ "$CNT" -ge 1 ]; then bad "${PROTO[$n]} still serving" "mount entry present but stale"
  else bad "${PROTO[$n]} still mounted" "mount entry is gone"; fi
done
DEN=$(D "dmesg 2>/dev/null | grep -c 'avc:.*gammaos_sharefs'" | tr -d '[:space:]')
[ "${DEN:-0}" = "0" ] && ok "no SELinux denials under load" || bad "SELinux denials" "$DEN"

echo
echo "  load test: $PASS passed, $FAIL failed"
exit $FAIL
