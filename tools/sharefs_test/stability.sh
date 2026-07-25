#!/bin/bash
# gammaos-sharefs stability tests.
#
# The functional suite proves a share works once. This proves it keeps working: through a sustained
# read, through the server going away and coming back, through concurrent readers, and without the
# daemon leaking memory or file descriptors while it does.
#
# The server-drop test is the one that matters most in practice. A NAS that sleeps, a laptop that
# closes, wifi that drops for ten seconds - all of these happen on a home network, and the failure
# mode we must not have is a mount that is dead until the user goes and re-adds the share.
#
#   usage: stability.sh <mount-name> <protocol-label> <server-restart-cmd> [minutes]

set -u
MNT="/mnt/shares/$1"
PROTO="$2"
RESTART_CMD="$3"
MINUTES="${4:-3}"
PASS=0; FAIL=0
RESULTS=""

ok()   { PASS=$((PASS+1)); printf "  PASS  %s\n" "$1"; RESULTS="${RESULTS}PASS|${PROTO}-stab|$1|${2:-}\n"; }
bad()  { FAIL=$((FAIL+1)); printf "  FAIL  %s -- %s\n" "$1" "${2:-}"; RESULTS="${RESULTS}FAIL|${PROTO}-stab|$1|${2:-}\n"; }
info() { printf "  ....  %s: %s\n" "$1" "$2"; RESULTS="${RESULTS}INFO|${PROTO}-stab|$1|$2\n"; }
D() { adb shell "$@" 2>&1; }

# The daemon serving THIS share, so its memory and descriptors can be watched. There is one process
# per share, so match on the share name: picking the first gammaos-sharefs would measure whichever
# protocol happened to start first and quietly report the wrong numbers.
SHARE_NAME="$1"
PID=$(D "ps -A -o PID,ARGS | grep -- '--share $SHARE_NAME' | grep -v grep | head -1 | awk '{print \$1}'" | tr -d '[:space:]')
[ -n "$PID" ] && ok "daemon process found for '$SHARE_NAME' (pid $PID)" \
    || bad "daemon process found for '$SHARE_NAME'" "no matching gammaos-sharefs process"

rss()  { D "grep VmRSS /proc/$PID/status 2>/dev/null | awk '{print \$2}'" | tr -d '[:space:]'; }
nfd()  { D "ls /proc/$PID/fd 2>/dev/null | wc -l" | tr -d '[:space:]'; }
nthr() { D "grep Threads /proc/$PID/status 2>/dev/null | awk '{print \$2}'" | tr -d '[:space:]'; }

echo "=============================================================="
echo " $PROTO stability  ($MNT, ${MINUTES}min soak)"
echo "=============================================================="

RSS0=$(rss); FD0=$(nfd); TH0=$(nthr)
info "baseline" "rss=${RSS0}kB fds=${FD0} threads=${TH0}"

# ---- 1. sustained read soak --------------------------------------------------
# Repeated full reads of a 4MB file. Checksummed every pass, so a connection that degrades into
# returning wrong bytes fails loudly instead of just getting slower.
echo "-- soak (${MINUTES} min of repeated verified reads) --"
EXPECT="ec1407c3860bab86b16c866ab6b1d5094203967cd27ea52277632a9f4be5f5ed"
END=$(( $(date +%s) + MINUTES*60 ))
ITER=0; ERRS=0
while [ "$(date +%s)" -lt "$END" ]; do
  got=$(D "sha256sum '$MNT/media/medium.bin' 2>/dev/null | cut -d' ' -f1" | tr -d '[:space:]')
  ITER=$((ITER+1))
  [ "$got" = "$EXPECT" ] || { ERRS=$((ERRS+1)); echo "    iteration $ITER: checksum mismatch (${got:0:16})"; }
done
[ $ERRS -eq 0 ] && ok "soak: $ITER verified reads, 0 errors" || bad "soak" "$ERRS/$ITER reads returned wrong data"

RSS1=$(rss); FD1=$(nfd); TH1=$(nthr)
info "after soak" "rss=${RSS1}kB fds=${FD1} threads=${TH1}"
# A backend that leaks a handle per read shows up here long before it exhausts anything.
if [ -n "$FD0" ] && [ -n "$FD1" ]; then
  DELTA=$((FD1-FD0))
  [ "$DELTA" -le 4 ] && ok "no descriptor leak (delta $DELTA over $ITER reads)" \
      || bad "descriptor leak" "fds grew by $DELTA over $ITER reads"
fi
if [ -n "$RSS0" ] && [ -n "$RSS1" ]; then
  GROW=$((RSS1-RSS0))
  # Some growth is just the allocator and the attr cache reaching steady state.
  [ "$GROW" -lt 20000 ] && ok "no runaway memory growth (+${GROW}kB)" \
      || bad "memory growth" "rss grew ${GROW}kB over $ITER reads"
fi

# ---- 2. concurrent readers ---------------------------------------------------
# The backends serialise on one connection lock, so this checks that concurrency is merely slow and
# not wrong: two readers must not interleave into each other's buffers.
echo "-- concurrent readers --"
D "( sha256sum '$MNT/media/medium.bin' > /data/local/tmp/c1.txt 2>&1 & sha256sum '$MNT/media/small.bin' > /data/local/tmp/c2.txt 2>&1 & wait )" >/dev/null
C1=$(D "cut -d' ' -f1 /data/local/tmp/c1.txt" | tr -d '[:space:]')
C2=$(D "cut -d' ' -f1 /data/local/tmp/c2.txt" | tr -d '[:space:]')
[ "$C1" = "$EXPECT" ] && ok "concurrent read A correct" || bad "concurrent read A" "${C1:0:16}"
[ "$C2" = "2e16a7b1fc0b50ebf936aa3aef94c2a016815b2f11275e5aa1266369644df8c3" ] \
    && ok "concurrent read B correct" || bad "concurrent read B" "${C2:0:16}"

# ---- 3. server drop and recovery ---------------------------------------------
# Stop the server under the mount, confirm the failure is a clean error rather than a hang, then
# bring it back and confirm the share heals itself with no user action.
echo "-- server drop and recovery --"
eval "$RESTART_CMD stop" >/dev/null 2>&1
sleep 3
T0=$(date +%s)
DROP_OUT=$(D "timeout 25 cat '$MNT/media/small.bin' >/dev/null 2>&1; echo rc=\$?")
T1=$(date +%s)
ELAPSED=$((T1-T0))
info "access while server down" "rc from ${DROP_OUT}, took ${ELAPSED}s"
if [ "$ELAPSED" -lt 25 ]; then ok "fails fast when server is down (${ELAPSED}s, no indefinite hang)"
else bad "fails fast when server is down" "still blocked after ${ELAPSED}s"; fi

eval "$RESTART_CMD start" >/dev/null 2>&1
sleep 5
RECOVERED=0
for attempt in 1 2 3 4 5 6 7 8 9 10; do
  got=$(D "sha256sum '$MNT/media/medium.bin' 2>/dev/null | cut -d' ' -f1" | tr -d '[:space:]')
  if [ "$got" = "$EXPECT" ]; then RECOVERED=$attempt; break; fi
  sleep 4
done
[ "$RECOVERED" -gt 0 ] && ok "recovers after server returns (attempt $RECOVERED, <= $((RECOVERED*4))s)" \
    || bad "recovers after server returns" "still failing 40s after the server came back"

# Still mounted at all? A backend that gave up and let the mount die would fail here.
D "mountpoint -q \"$MNT\" && echo yes" | grep -q yes \
    && ok "mount survived the outage" || bad "mount survived the outage" "no longer a mount point"

RSS2=$(rss); FD2=$(nfd)
info "after outage" "rss=${RSS2}kB fds=${FD2}"

# ---- 4. selinux denials ------------------------------------------------------
echo "-- selinux --"
DENIALS=$(D "dmesg 2>/dev/null | grep -c 'avc: denied.*gammaos_sharefs'" | tr -d '[:space:]')
[ "${DENIALS:-0}" = "0" ] && ok "no SELinux denials for gammaos_sharefs" \
    || bad "SELinux denials" "$DENIALS denials in dmesg"

printf "%b" "$RESULTS" > "/tmp/results_${PROTO}_stability.txt"
echo
echo "  $PROTO stability: $PASS passed, $FAIL failed"
exit $FAIL
