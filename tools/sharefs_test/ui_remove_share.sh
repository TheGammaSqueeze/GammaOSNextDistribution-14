#!/bin/bash
# Remove a share through the nano menu and check the mount actually goes away.
#
# Adding a share is only half of what the UI owns. Removing one has to stop the daemon, unmount
# /mnt/shares/<name> and take down the /storage bind with it; a share that disappears from the list
# but leaves its mount behind would keep serving a share the user believes they deleted, and a
# stale FUSE mount is exactly the thing that used to strand a slot permanently.
#
# The navigation leans on two properties of the editor rather than on counted rows: "Remove Share"
# is always the last item in buildShareEditor(), so stepping past the end clamps onto it, and the
# confirm dialog is Cancel then Remove, so one step down selects Remove.
#
#   usage: ui_remove_share.sh <share-name>

set -u
NAME="$1"
SHOTDIR=/tmp/shots
mkdir -p $SHOTDIR

NAV()  { adb shell setprop sys.gammaos.nano.nav "$1" >/dev/null 2>&1; sleep "${2:-0.5}"; }
DOWN() { for _ in $(seq 1 "$1"); do NAV down 0.35; done; }
UP()   { for _ in $(seq 1 "$1"); do NAV up 0.35; done; }
CAP()  { adb shell setprop sys.gammaos.nano.shot 1 >/dev/null 2>&1; sleep 1.3
         adb pull /data/local/tmp/nano_shot.ppm $SHOTDIR/$1.ppm >/dev/null 2>&1
         [ -s $SHOTDIR/$1.ppm ] && convert $SHOTDIR/$1.ppm $SHOTDIR/$1.png 2>/dev/null && echo "    shot: $1.png"; }

echo "== removing '$NAME' through the menu =="
BEFORE=$(adb shell "mountpoint -q '/mnt/shares/$NAME' && echo mounted || echo no" 2>/dev/null | tr -d '\r')
echo "    before: $BEFORE"

for _ in $(seq 1 6); do NAV back 0.3; done
sleep 1.5
for _ in $(seq 1 8); do NAV left 0.3; done
NAV right 0.6
sleep 1
UP 25
DOWN 14
NAV enter 1.5
CAP "remove_00_list"

# Select the share by walking from the top of the list to its position.
#
# Count only slots that actually hold a share. The list shows configured shares in slot order, so
# an empty slot before this one would make the slot number and the row number disagree and the walk
# would land on the wrong share, or past the end onto Add Share.
IDX=$(adb shell 'for i in 1 2 3 4; do getprop persist.gammaos.share.$i.name; done' 2>/dev/null \
      | tr -d '\r' | grep -v '^$' | grep -n "^${NAME}$" | cut -d: -f1)
if [ -z "$IDX" ]; then echo "    ERROR: '$NAME' is not in the share list"; exit 1; fi
UP 10
DOWN $((IDX - 1))
CAP "remove_01_selected"
NAV enter 2.0
CAP "remove_02_editor"

# Past the end clamps onto "Remove Share", whatever the protocol's row count is.
DOWN 20
CAP "remove_03_remove_row"
NAV enter 1.5
CAP "remove_04_confirm"
DOWN 1            # Cancel -> Remove
NAV enter 2.5
CAP "remove_05_after"

sleep 5

# Ask the kernel, not the path.
#
# The obvious checks are `mountpoint -q` and `ls -d`, and both are wrong here, which cost a false
# pass: they stat the path, and a stale FUSE mount fails a stat with ENOTCONN exactly as a missing
# one fails with ENOENT. So the single worst outcome - the mount still in the table with no daemon
# behind it, leaving /storage/<name> as a directory that errors on every access - was being scored
# as "gone". The mount table and a listing of the parent cannot be fooled that way.
# The mount table is pulled and matched here rather than grepped on the device: mountinfo escapes a
# space as \040, share names contain spaces by default, and quoting that through adb shell into grep
# is where this went wrong the first time. Unescaping in awk and comparing whole fields is exact.
MI=$(adb shell cat /proc/self/mountinfo 2>/dev/null | tr -d '\r')
MOUNTS=$(printf '%s\n' "$MI" | awk -v n="/mnt/shares/$NAME" \
    '{mp=$5; gsub(/\\040/," ",mp); if (mp==n || index(mp, n "/")==1) c++} END{print c+0}')
BINDS=$(printf '%s\n' "$MI" | awk -v n="$NAME" \
    '{mp=$5; gsub(/\\040/," ",mp); if (mp ~ ("/" n "$") && mp !~ "^/mnt/shares/") c++} END{print c+0}')
INDIR=$(adb shell "ls /mnt/shares/ 2>/dev/null" | tr -d '\r' | grep -c "^${NAME}$")
INSTORAGE=$(adb shell "ls /storage/ 2>/dev/null" | tr -d '\r' | grep -c "^${NAME}$")
NAMEPROP=$(adb shell 'for i in 1 2 3 4; do getprop persist.gammaos.share.$i.name; done' 2>/dev/null \
           | tr -d '\r' | grep -c "^${NAME}$")
STALE=$(adb shell "ls -d '/mnt/shares/$NAME' 2>&1 >/dev/null" | tr -d '\r')

RC=0
[ "${MOUNTS:-1}" = "0" ] && echo "  PASS  no mount table entry under /mnt/shares" \
    || { echo "  FAIL  $MOUNTS mount table entries remain for '$NAME' (${STALE:-no error})"; RC=1; }
[ "${BINDS:-1}" = "0" ] && echo "  PASS  no bind left in any /storage view" \
    || { echo "  FAIL  $BINDS bind entries remain (storage/user/installer/androidwritable)"; RC=1; }
[ "${INDIR:-1}" = "0" ] && echo "  PASS  gone from /mnt/shares listing" \
    || { echo "  FAIL  '/mnt/shares/$NAME' still listed"; RC=1; }
[ "${INSTORAGE:-1}" = "0" ] && echo "  PASS  gone from /storage listing" \
    || { echo "  FAIL  '/storage/$NAME' still listed"; RC=1; }
[ "$NAMEPROP" = "0" ] && echo "  PASS  removed from the configuration" \
    || { echo "  FAIL  still configured"; RC=1; }
exit $RC
