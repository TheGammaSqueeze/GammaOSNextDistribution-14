#!/bin/bash
# Verify that a mounted share actually surfaces where the user was promised it would:
#   - Photo / Music / Video > "Search for Media Servers" (the shared folder picker)
#   - Settings > File Explorer
#
# This is the part that cannot be checked from a shell. The mount existing at /mnt/shares proves
# the daemon works; it does not prove nano lists it, and listing it is the whole point. Both
# screens read /proc/self/mountinfo, and the default share names contain a space, so this is also
# the real test of the \040 unescaping.

set -u
SHOTDIR=/tmp/shots
mkdir -p $SHOTDIR
NAV()  { adb shell setprop sys.gammaos.nano.nav "$1" >/dev/null 2>&1; sleep "${2:-0.5}"; }
DOWN() { for _ in $(seq 1 "$1"); do NAV down 0.35; done; }
CAP()  { adb shell setprop sys.gammaos.nano.shot 1 >/dev/null 2>&1; sleep 1.3
         adb pull /data/local/tmp/nano_shot.ppm $SHOTDIR/$1.ppm >/dev/null 2>&1
         [ -s $SHOTDIR/$1.ppm ] && convert $SHOTDIR/$1.ppm $SHOTDIR/$1.png 2>/dev/null && echo "    shot: $1.png"; }

echo "== what the menu should be seeing =="
adb shell 'cat /proc/self/mountinfo | grep /mnt/shares' 2>/dev/null | tr -d '\r' | sed 's/^/  /'

echo
echo "== Photo > Search for Media Servers > folder picker =="
for _ in $(seq 1 6); do NAV back 0.3; done
sleep 1.5
# Walk to the Photo column: fully left, then right past Power, Settings.
for _ in $(seq 1 8); do NAV left 0.3; done
NAV right 0.5; NAV right 0.5
sleep 1
CAP "pick_00_photo_column"
# "Search for Media Servers" is the first Photo row.
NAV enter 2.0
CAP "pick_01_photo_folders"
# The folders screen offers "Add Folder"; entering it opens the storage-roots browser, which is
# where a mounted share has to appear as "Share: <name>".
NAV enter 2.0
CAP "pick_02_storage_roots"
NAV down 0.4; CAP "pick_03_roots_scrolled"
NAV down 0.4; CAP "pick_04_roots_scrolled2"
NAV down 0.4; CAP "pick_05_roots_scrolled3"

echo
echo "== Settings > File Explorer =="
for _ in $(seq 1 8); do NAV back 0.35; done
sleep 1.5
for _ in $(seq 1 8); do NAV left 0.3; done
NAV right 0.5
sleep 1
DOWN 13     # File Explorer sits one row above Network Shares
CAP "pick_06_file_explorer_row"
NAV enter 2.5
CAP "pick_07_explorer_storage"
NAV down 0.4; CAP "pick_08_explorer_scrolled"
NAV down 0.4; CAP "pick_09_explorer_scrolled2"

echo
echo "  Screenshots in $SHOTDIR/pick_*.png - a share should read as 'Share: Share N'"
