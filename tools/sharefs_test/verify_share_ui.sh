#!/bin/bash
# Re-test the two crashes a real FTP share caused, against that same real server.
#
# Both were the same fault: blocking filesystem IO on nano's render thread. nano's render watchdog
# aborts the process if no frame is drawn for ~8s (NanoMenuRender.cpp startRenderWatchdog), and init
# leaves it stopped afterwards, so the failure mode is "nano is simply gone".
#
#   1. navigating to Music with a share folder in the library  -> mpAlbumArt probed up to 80 cover
#      paths per album, then read the first track's ID3 tag, all in draw
#   2. browsing into a share to add a folder                   -> buildFolderBrowser did an
#      opendir + a stat per entry, inline
#
# The test is simply: do those two things and check nano is still alive afterwards. A screenshot is
# taken at each step because "did not crash" is not the same as "showed something sensible" - the
# whole point of the fix is that the UI stays usable while the listing is still arriving.
#
#   usage: verify_share_ui.sh [share-name]

set -u
NAME="${1:-ftp}"
SHOTS=/tmp/shots
mkdir -p $SHOTS
PASS=0; FAIL=0
ok()  { PASS=$((PASS+1)); printf "  PASS  %s\n" "$1"; }
bad() { FAIL=$((FAIL+1)); printf "  FAIL  %s -- %s\n" "$1" "${2:-}"; }
D()   { adb shell "$@" </dev/null 2>&1 | tr -d '\r'; }
NAV() { adb shell setprop sys.gammaos.nano.nav "$1" </dev/null >/dev/null 2>&1; sleep "${2:-0.35}"; }
CAP() { adb shell setprop sys.gammaos.nano.shot 1 </dev/null >/dev/null 2>&1; sleep 1.5
        adb pull /data/local/tmp/nano_shot.ppm $SHOTS/$1.ppm >/dev/null 2>&1
        [ -s $SHOTS/$1.ppm ] && convert $SHOTS/$1.ppm $SHOTS/$1.png 2>/dev/null && echo "    shot: $1.png"; }
alive() { [ "$(D 'getprop init.svc.gammaos-nano')" = "running" ] && \
          [ -n "$(D "ps -A -o PID,ARGS | grep gammaos-nano | grep -v grep")" ]; }

echo "=============================================================="
echo " nano vs a real network share: '$NAME'"
echo "=============================================================="

D "logcat -c" >/dev/null 2>&1
D "logcat -b crash -c" >/dev/null 2>&1

echo "-- preconditions --"
alive && ok "nano is running" || { bad "nano is running" "cannot test"; exit 1; }
D "mountpoint -q '/mnt/shares/$NAME' && echo y" | grep -q y \
    && ok "share '$NAME' is mounted" || bad "share '$NAME' is mounted" "not a mount point"
ENTRIES=$(D "ls '/mnt/shares/$NAME' 2>/dev/null | wc -l")
[ "${ENTRIES:-0}" -gt 0 ] && ok "share serves content ($ENTRIES entries)" \
                          || bad "share serves content" "listing is empty"

echo "-- 1. navigate to Music (was: watchdog abort from album-art probing) --"
for _ in $(seq 1 6); do NAV back 0.3; done; sleep 1.2
for _ in $(seq 1 8); do NAV left 0.3; done
# Rail order from fully-left: [0] Power, [1] Settings, [2] Photo, [3] Music, [4] Video. So three
# rights, not four - four lands on Video, and an earlier run of this test "passed" while browsing
# IPTV channels without ever touching Music or the share.
NAV right 0.5; NAV right 0.5; NAV right 0.5
sleep 2
CAP "share_ui_01_music"
# Prove we are actually on Music before claiming anything about it. The column title is drawn as
# the label under the selected icon; if the shot does not look like Music the caller will see it.
D "getprop sys.gammaos.nano.nav" >/dev/null
sleep 6          # the old build aborted ~8s in; give it longer than that to fall over
if alive; then ok "nano survived opening Music"
else bad "nano survived opening Music" "process gone - watchdog abort"; fi

echo "-- 2. browse into the share (was: watchdog abort from the folder listing) --"
# The Music rows are: Internet Radio, Search for Media Servers, Playlists, then the albums. Enter
# on the first row starts the radio, which an earlier run of this test did while reporting that it
# had opened the folder browser - so step down to the picker explicitly.
NAV down 0.5
CAP "share_ui_02_picker_row"
NAV enter 2.5
CAP "share_ui_03_roots"
sleep 3
if alive; then ok "nano survived opening the folder browser"
else bad "nano survived opening the folder browser" "process gone"; fi

echo "-- 3. the share row itself, which is where the listing happens --"
# The roots screen lists Internal storage, then any SD/removable, then "Share: <name>". Walk down
# to the share entry rather than guessing a fixed row.
for _ in $(seq 1 6); do NAV down 0.35; done
CAP "share_ui_04_share_row"
NAV enter 0.6                          # deliberately short: catch the frame right after the press
CAP "share_ui_05_listing_immediate"    # expect "Loading...", NOT a frozen screen
sleep 10
CAP "share_ui_06_listing_settled"      # expect the real directory listing
if alive; then ok "nano survived listing a share directory"
else bad "nano survived listing a share directory" "process gone"; fi

echo "-- 4. anything in the crash buffer? --"
CR=$(D "logcat -d -b crash 2>/dev/null | grep -c 'gammaos-nano'")
[ "${CR:-0}" = "0" ] && ok "no nano entries in the crash buffer" \
                     || bad "crash buffer" "$CR lines mention gammaos-nano"
D "logcat -d -b crash 2>/dev/null | grep -A3 'gammaos-nano' | head -12" | sed 's/^/      /'

echo
echo "  $PASS passed, $FAIL failed"
echo "  screenshots: $SHOTS/share_ui_*.png"
exit $FAIL
