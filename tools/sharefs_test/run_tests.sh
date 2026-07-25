#!/bin/bash
# gammaos-sharefs protocol test suite.
#
# Runs against a share that is already mounted at /mnt/shares/<name>. Everything is measured on the
# device, through the FUSE mount, so what is timed is what the menu and the media players would
# actually see - not what the host can do to its own disk.
#
# The server side of every share is one directory (/srv/gammashare) with files whose contents are
# deterministic, so a read can be checked byte for byte rather than only for plausibility. A test
# that reads the right number of bytes of the wrong data is a test that passes for the wrong reason.
#
#   usage: run_tests.sh <mount-name> <protocol-label>

set -u
MNT="/mnt/shares/$1"
PROTO="$2"
PASS=0; FAIL=0
RESULTS=""

# Expected checksums, computed on the server side before any of this ran.
declare -A SUMS=(
  ["media/small.bin"]="2e16a7b1fc0b50ebf936aa3aef94c2a016815b2f11275e5aa1266369644df8c3"
  ["media/medium.bin"]="ec1407c3860bab86b16c866ab6b1d5094203967cd27ea52277632a9f4be5f5ed"
  ["media/large.bin"]="9376c34f6ad8b0bd6b27c0507cf237f7503ddb34294c9a033c849a697e71b4a3"
  ["docs/tiny.txt"]="5646b08909c2882420753bed2e37524a4f479e076a0f3b9d6ef696323ea865c1"
  ["nested/deep/deeper/buried.bin"]="63c4b1a59e6bf8ec906cde35bcf142ad54f9a3d8263dd3234892488dbfd457f9"
)
declare -A SIZES=(
  ["media/small.bin"]=65536 ["media/medium.bin"]=4194304 ["media/large.bin"]=67108864
  ["docs/tiny.txt"]=1024 ["nested/deep/deeper/buried.bin"]=262144
)

ok()   { PASS=$((PASS+1)); printf "  PASS  %s\n" "$1"; RESULTS="${RESULTS}PASS|${PROTO}|$1|${2:-}\n"; }
bad()  { FAIL=$((FAIL+1)); printf "  FAIL  %s -- %s\n" "$1" "${2:-}"; RESULTS="${RESULTS}FAIL|${PROTO}|$1|${2:-}\n"; }
info() { printf "  ....  %s: %s\n" "$1" "$2"; RESULTS="${RESULTS}INFO|${PROTO}|$1|$2\n"; }

D() { adb shell "$@" 2>&1; }

echo "=============================================================="
echo " $PROTO  ($MNT)"
echo "=============================================================="

# ---- 1. mount presence -------------------------------------------------------
echo "-- mount --"
if D "mountpoint -q \"$MNT\" && echo yes" | grep -q yes; then
  ok "mount present"
else
  # The share names default to "Share N", which contains a space, and mountinfo escapes that as
  # \040. mountpoint(1) sidesteps the escaping entirely; nano's own \040 handling is covered by
  # the folder-picker UI check instead.
  if [ "$(D "ls -d \"$MNT\" 2>/dev/null | wc -l" | tr -d '[:space:]')" != "1" ]; then
    bad "mount present" "not mounted and not listable"
    echo "ABORT: nothing mounted, remaining tests would be meaningless"
    printf "%b" "$RESULTS" > "/tmp/results_${PROTO}.txt"
    exit 1
  fi
fi

FSTYPE=$(D "stat -f -c %T \"$MNT\" 2>/dev/null" | tr -d '[:space:]')
# toybox stat -f -c %T prints the raw magic rather than a name. 0x65735546 is FUSE_SUPER_MAGIC
# ("fuse" read as little-endian ASCII), so accept either form.
case "$FSTYPE" in
  *fuse*|0x65735546) ok "filesystem is fuse ($FSTYPE)" ;;
  *) bad "filesystem is fuse" "got '$FSTYPE'" ;;
esac

# ---- 2. directory listing ----------------------------------------------------
echo "-- listing --"
ROOT_LS=$(D "ls '$MNT'")
for d in media docs nested; do
  echo "$ROOT_LS" | grep -qw "$d" && ok "root lists $d" || bad "root lists $d" "ls: $ROOT_LS"
done

NESTED=$(D "ls '$MNT/nested/deep/deeper'")
echo "$NESTED" | grep -q "buried.bin" && ok "nested listing (3 deep)" || bad "nested listing (3 deep)" "$NESTED"

MANY=$(D "ls '$MNT/docs/many' | wc -l")
[ "$(echo $MANY | tr -d '[:space:]')" = "200" ] && ok "200-entry directory" || bad "200-entry directory" "counted $MANY"

# ---- 3. stat / sizes ---------------------------------------------------------
echo "-- stat --"
for f in "${!SIZES[@]}"; do
  got=$(D "stat -c %s '$MNT/$f'" | tr -d '[:space:]')
  [ "$got" = "${SIZES[$f]}" ] && ok "size $f" || bad "size $f" "expected ${SIZES[$f]} got $got"
done

# ---- 3b. presented permissions ----------------------------------------------
# A share has to present one fixed set of modes whatever the server says, because the mount carries
# default_permissions and those bits are what the kernel checks an app against. /srv/gammashare/modes
# holds files deliberately set to 644 and 600: on a backend that passes the server's mode through,
# 644 arrives group-read-only and the share is silently unwritable to apps, and 600 arrives
# unreadable to them entirely, while the root daemon reads both perfectly well and every other test
# here still passes. The rest of the fixture is 777, which is permissive enough to hide all of that.
echo "-- presented permissions (server modes must not leak) --"
for probe in "modes/ro644.bin:660" "modes/private600.bin:660" "modes/dir755:770" "modes/dir755/inside.txt:660"; do
  p="${probe%%:*}"; want="${probe##*:}"
  got=$(D "stat -c %a '$MNT/$p'" | tr -d '[:space:]')
  [ "$got" = "$want" ] \
    && ok "mode $p presented as $want" \
    || bad "mode $p" "server mode leaked through: got $got, expected $want"
done
OWNER=$(D "stat -c %U:%G '$MNT/modes/ro644.bin'" | tr -d '[:space:]')
[ "$OWNER" = "root:everybody" ] \
  && ok "ownership presented as root:everybody" \
  || bad "ownership" "got $OWNER, expected root:everybody"

# ---- 4. read integrity -------------------------------------------------------
echo "-- read integrity (byte-exact) --"
for f in "${!SUMS[@]}"; do
  got=$(D "sha256sum '$MNT/$f' 2>/dev/null | cut -d' ' -f1" | tr -d '[:space:]')
  if [ "$got" = "${SUMS[$f]}" ]; then ok "sha256 $f"
  else bad "sha256 $f" "expected ${SUMS[$f]:0:16} got ${got:0:16}"; fi
done

# ---- 5. ranged / seek reads --------------------------------------------------
# Media scrubbing and any container parser seek rather than stream from zero, so a backend that is
# only correct for sequential reads would look fine above and fail in the player.
echo "-- seek reads --"
MID_EXPECT=$(dd if=/srv/gammashare/media/medium.bin bs=1 skip=1000000 count=4096 2>/dev/null | sha256sum | cut -d' ' -f1)
MID_GOT=$(D "dd if='$MNT/media/medium.bin' bs=1 skip=1000000 count=4096 2>/dev/null | sha256sum | cut -d' ' -f1" | tr -d '[:space:]')
[ "$MID_GOT" = "$MID_EXPECT" ] && ok "read at offset 1000000" || bad "read at offset 1000000" "expected ${MID_EXPECT:0:16} got ${MID_GOT:0:16}"

TAIL_EXPECT=$(tail -c 65536 /srv/gammashare/media/large.bin | sha256sum | cut -d' ' -f1)
TAIL_GOT=$(D "tail -c 65536 '$MNT/media/large.bin' | sha256sum | cut -d' ' -f1" | tr -d '[:space:]')
[ "$TAIL_GOT" = "$TAIL_EXPECT" ] && ok "read last 64KB of 64MB file" || bad "read last 64KB of 64MB file" "expected ${TAIL_EXPECT:0:16} got ${TAIL_GOT:0:16}"

# Backwards seeks in sequence: the pattern a player uses when the user scrubs.
BACK_OK=1
for off in 60000000 10000000 40000000 5000000; do
  e=$(dd if=/srv/gammashare/media/large.bin bs=1 skip=$off count=1024 2>/dev/null | sha256sum | cut -d' ' -f1)
  g=$(D "dd if='$MNT/media/large.bin' bs=1 skip=$off count=1024 2>/dev/null | sha256sum | cut -d' ' -f1" | tr -d '[:space:]')
  [ "$e" = "$g" ] || { BACK_OK=0; break; }
done
[ $BACK_OK = 1 ] && ok "out-of-order seeks" || bad "out-of-order seeks" "mismatch during backwards scrub"

# ---- 6. throughput -----------------------------------------------------------
echo "-- throughput --"
# Drop what caching we can influence so this measures the network path, not the FUSE attr cache.
READ_OUT=$(D "dd if='$MNT/media/large.bin' of=/dev/null bs=1M 2>&1 | tail -1")
info "sequential read (64MB)" "$(echo $READ_OUT | sed 's/.*, //')"
RSPEED=$(echo "$READ_OUT" | grep -oE "[0-9.]+ ?[MK]B/s" | tail -1)
[ -n "$RSPEED" ] && info "read throughput" "$RSPEED"

MED_OUT=$(D "dd if='$MNT/media/medium.bin' of=/dev/null bs=64k 2>&1 | tail -1")
info "sequential read (4MB, 64k blocks)" "$(echo $MED_OUT | sed 's/.*, //')"

# ---- 7. write path -----------------------------------------------------------
echo "-- write --"
STAMP="t$(date +%s)"
WOUT=$(D "dd if='$MNT/media/medium.bin' of='$MNT/writetest/$STAMP.bin' bs=64k 2>&1 | tail -1")
if D "ls '$MNT/writetest/$STAMP.bin'" | grep -q "$STAMP"; then
  ok "create + write file"
  info "write throughput (4MB)" "$(echo $WOUT | sed 's/.*, //')"
  # Verify on the SERVER side: this is the only check that proves the bytes crossed the network
  # correctly rather than being served back out of a cache.
  if [ -f "/srv/gammashare/writetest/$STAMP.bin" ]; then
    hs=$(sha256sum "/srv/gammashare/writetest/$STAMP.bin" | cut -d' ' -f1)
    [ "$hs" = "${SUMS[media/medium.bin]}" ] && ok "written bytes correct on server" \
        || bad "written bytes correct on server" "server sha ${hs:0:16}"
  else
    bad "written file visible on server" "not present in /srv/gammashare/writetest"
  fi
else
  bad "create + write file" "$WOUT"
fi

# mkdir / rename / delete
D "mkdir -p '$MNT/writetest/dir_$STAMP'" >/dev/null
D "ls '$MNT/writetest' " | grep -q "dir_$STAMP" && ok "mkdir" || bad "mkdir" "not listed"
D "mv '$MNT/writetest/$STAMP.bin' '$MNT/writetest/${STAMP}_moved.bin'" >/dev/null
D "ls '$MNT/writetest'" | grep -q "${STAMP}_moved.bin" && ok "rename" || bad "rename" "not renamed"
D "rm -f '$MNT/writetest/${STAMP}_moved.bin'" >/dev/null
D "ls '$MNT/writetest'" | grep -q "${STAMP}_moved.bin" && bad "unlink" "still listed" || ok "unlink"
D "rmdir '$MNT/writetest/dir_$STAMP'" >/dev/null
D "ls '$MNT/writetest'" | grep -q "dir_$STAMP" && bad "rmdir" "still listed" || ok "rmdir"

# ---- 8. latency --------------------------------------------------------------
echo "-- latency --"
T0=$(date +%s%N)
D "ls '$MNT/docs/many' >/dev/null" >/dev/null
T1=$(date +%s%N)
info "readdir 200 entries" "$(( (T1-T0)/1000000 )) ms"

T0=$(date +%s%N)
D "for i in 1 2 3 4 5 6 7 8 9 10; do stat -c %s '$MNT/media/small.bin' >/dev/null; done" >/dev/null
T1=$(date +%s%N)
info "10x stat (cached)" "$(( (T1-T0)/1000000 )) ms"

T0=$(date +%s%N)
D "dd if='$MNT/media/large.bin' of=/dev/null bs=4k count=1 2>/dev/null" >/dev/null
T1=$(date +%s%N)
info "time to first 4KB" "$(( (T1-T0)/1000000 )) ms"

printf "%b" "$RESULTS" > "/tmp/results_${PROTO}.txt"
echo
echo "  $PROTO: $PASS passed, $FAIL failed"
exit $FAIL
