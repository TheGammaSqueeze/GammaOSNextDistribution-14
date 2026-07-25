#!/bin/bash
# Create and enable a network share by driving the nano menu.
#
# Every action here goes through sys.gammaos.nano.nav, which feeds the same handleUp/handleSelect/
# oskType path the physical buttons do (NanoMenuInput.cpp). Nothing writes persist.gammaos.share.*
# directly, so this exercises the real screens: the shares list, the per-share editor, the type
# chooser dialog and the on-screen keyboard. The properties are only read back afterwards, as
# evidence of what the UI did.
#
#   usage: ui_add_share.sh <smb|nfs|dav|ftp>
#
# Row indices below come from buildShareEditor(): the editor shows different fields per protocol
# (NFS has no account fields and no port, only SMB has a workgroup, only WebDAV/FTP have the TLS
# toggle), so each protocol has its own walk.

set -u
PROTO="$1"
SHOTDIR=/tmp/shots
mkdir -p $SHOTDIR

NAV()  { adb shell setprop sys.gammaos.nano.nav "$1" >/dev/null 2>&1; sleep "${2:-0.5}"; }
DOWN() { for _ in $(seq 1 "$1"); do NAV down 0.35; done; }
UP()   { for _ in $(seq 1 "$1"); do NAV up 0.35; done; }
CAP()  { adb shell setprop sys.gammaos.nano.shot 1 >/dev/null 2>&1; sleep 1.3
         adb pull /data/local/tmp/nano_shot.ppm $SHOTDIR/$1.ppm >/dev/null 2>&1
         [ -s $SHOTDIR/$1.ppm ] && convert $SHOTDIR/$1.ppm $SHOTDIR/$1.png 2>/dev/null && echo "    shot: $1.png"; }
# Type into the on-screen keyboard and commit it.
TYPE() { adb shell setprop sys.gammaos.nano.nav "type:$1" >/dev/null 2>&1; sleep 0.9; NAV submit 1.0; }

echo "== $PROTO: opening Settings > Network Shares =="
# Get to a known place: close anything open, then walk to the Settings column.
for _ in $(seq 1 6); do NAV back 0.3; done
sleep 1.5
CAP "${PROTO}_00_home"

# Settings is the toolbox column. Walk fully left, then right one (past Power).
for _ in $(seq 1 8); do NAV left 0.3; done
NAV right 0.6
sleep 1
CAP "${PROTO}_01_settings_column"

# "Network Shares" is row 14 with the legacy rows hidden (NANO_XMB_HIDE_LEGACY=1):
# System Update, Game, Video, Music, System, Developer, Theme, Date/Time, Power Save, Accessory,
# Gamepad, Slide Behaviour, GammaOS Toolbox, File Explorer, Network Shares.
#
# Go to the top of the column first, because the row index has to be absolute. The XMB remembers
# the selected row per column and "back" only leaves submenus, it does not return the column to its
# first row: on the second and later protocols this screen opens with the previous run's row still
# selected, so a bare DOWN 14 walks 14 rows past it and lands in the Internet settings. That is
# exactly what happened before this UP was added, and because the slot readback below used to fall
# back to the newest existing share, the suite went on to re-test the previous protocol's share and
# reported it as a pass.
UP 25
DOWN 14
CAP "${PROTO}_02_network_shares_row"
NAV enter 1.5
CAP "${PROTO}_03_shares_list"

echo "== $PROTO: Add Share =="
# The list shows existing shares then "Add Share"; selection starts at the top, so step past any
# share already configured.
EXISTING=$(adb shell 'for i in 1 2 3 4; do getprop persist.gammaos.share.$i.name; done' 2>/dev/null | tr -d '\r' | grep -c .)
echo "    $EXISTING share(s) already configured, stepping past them"
# Which slots are taken before the editor runs, so the new one can be identified positively
# afterwards rather than guessed at.
SLOTS_BEFORE=$(adb shell 'for i in 1 2 3 4; do n=$(getprop persist.gammaos.share.$i.name); [ -n "$n" ] && echo $i; done' 2>/dev/null | tr -d '\r' | tr '\n' ' ')
# Absolute row again: the list reopens with the previously selected row still highlighted.
UP 10
DOWN "$EXISTING"
CAP "${PROTO}_04_add_row"
NAV enter 2.0
CAP "${PROTO}_05_editor_new"

# The editor opens on row 0 (Enabled). Field rows differ per protocol.
case "$PROTO" in
  smb)
    TYPE_STEPS=0            # SMB is already the default for a new share
    R_SERVER=3; R_PORT=4; R_PATH=5; R_USER=6; R_PASS=7
    SERVER="127.0.0.1"; PORT="4450"; PATHV="media"; USER="gammatest"; PASS='GammaTest123!'
    ;;
  nfs)
    TYPE_STEPS=1
    R_SERVER=3; R_PORT=""; R_PATH=4; R_USER=""; R_PASS=""
    SERVER="127.0.0.1"; PORT=""; PATHV="/srv/gammashare"; USER=""; PASS=""
    ;;
  dav)
    TYPE_STEPS=2
    R_SERVER=3; R_PORT=4; R_PATH=5; R_USER=6; R_PASS=7
    SERVER="127.0.0.1"; PORT="8088"; PATHV=""; USER="gammatest"; PASS='GammaTest123!'
    ;;
  ftp)
    TYPE_STEPS=3
    R_SERVER=3; R_PORT=4; R_PATH=5; R_USER=6; R_PASS=7
    SERVER="127.0.0.1"; PORT="2121"; PATHV=""; USER="gammatest"; PASS='GammaTest123!'
    ;;
esac

# --- Type (row 2). Opens a chooser dialog: up/down to move, enter to confirm. ---
if [ "$TYPE_STEPS" -gt 0 ]; then
  echo "== $PROTO: setting type =="
  DOWN 2
  NAV enter 1.2
  CAP "${PROTO}_06_type_chooser"
  DOWN "$TYPE_STEPS"
  NAV enter 1.5
  CAP "${PROTO}_07_type_set"
  # The editor rebuilds after a type change and the selection is clamped, so go back to the top.
  UP 12
else
  UP 4
fi
sleep 0.5

# Set one field and confirm it actually landed.
#
# Driving the menu through the nav property is a scripted channel, and a press occasionally does
# not register: the first field edit after the editor opens was silently dropped, leaving Server
# empty while every later field set fine. The share then correctly refused to enable ("Enter the
# server address"), so the failure surfaced as a share that never mounted rather than as anything
# pointing at the input. Reading the property back and retrying closes that loop. Retries are
# logged rather than hidden, because a field that consistently needs two attempts is a real problem
# with the screen and not something the harness should quietly absorb.
#
# The value is only ever entered through the on-screen keyboard; the property is read as evidence,
# never written.
SETFIELD() {
  local label="$1" row="$2" prop="$3" value="$4"
  local attempt got
  for attempt in 1 2 3; do
    UP 12; DOWN "$row"
    NAV enter 1.2
    TYPE "$value"
    got=$(adb shell "getprop persist.gammaos.share.$SLOT_GUESS.$prop" 2>/dev/null | tr -d '\r')
    if [ "$prop" = "pass" ]; then
      [ -n "$got" ] && { echo "    $label set (attempt $attempt)"; return 0; }
    else
      [ "$got" = "$value" ] && { echo "    $label =$got (attempt $attempt)"; return 0; }
    fi
    echo "    RETRY $label: expected '$value', got '$got' after attempt $attempt"
    sleep 1
  done
  echo "    ERROR: $label never took after 3 attempts"
  return 1
}

# The editor writes into the first free slot, which is the one to read fields back from.
SLOT_GUESS=$(adb shell 'for i in 1 2 3 4; do n=$(getprop persist.gammaos.share.$i.name); [ -n "$n" ] && echo $i; done' 2>/dev/null | tr -d '\r' | tail -1)
[ -z "$SLOT_GUESS" ] && SLOT_GUESS=1

echo "== $PROTO: server =$SERVER"
SETFIELD "server" "$R_SERVER" host "$SERVER" || exit 1
CAP "${PROTO}_08_server"

if [ -n "$R_PORT" ] && [ -n "$PORT" ]; then
  echo "== $PROTO: port =$PORT"
  SETFIELD "port" "$R_PORT" port "$PORT" || exit 1
  CAP "${PROTO}_09_port"
fi

if [ -n "$PATHV" ]; then
  echo "== $PROTO: path =$PATHV"
  SETFIELD "path" "$R_PATH" path "$PATHV" || exit 1
  CAP "${PROTO}_10_path"
fi

if [ -n "$R_USER" ] && [ -n "$USER" ]; then
  echo "== $PROTO: username =$USER"
  SETFIELD "username" "$R_USER" user "$USER" || exit 1
  CAP "${PROTO}_11_user"

  echo "== $PROTO: password"
  SETFIELD "password" "$R_PASS" pass "$PASS" || exit 1
  CAP "${PROTO}_12_pass"
fi

# --- What the UI wrote, as evidence ---
echo "== $PROTO: properties the UI produced =="
# The slot has to be the one that appeared during this run, not simply the highest one in use.
# Taking the newest existing slot meant that when the navigation missed the Add Share row entirely,
# this quietly returned the previous protocol's share and the whole suite then tested that instead,
# reporting a full set of passes for a protocol it had never touched.
SLOTS_AFTER=$(adb shell 'for i in 1 2 3 4; do n=$(getprop persist.gammaos.share.$i.name); [ -n "$n" ] && echo $i; done' 2>/dev/null | tr -d '\r' | tr '\n' ' ')
SLOT=""
for s in $SLOTS_AFTER; do
  case " $SLOTS_BEFORE " in
    *" $s "*) ;;                 # already existed before this run
    *) SLOT="$s" ;;
  esac
done
if [ -z "$SLOT" ]; then
  echo "    ERROR: the UI created no new share (slots before: '$SLOTS_BEFORE', after: '$SLOTS_AFTER')"
  echo "    the navigation did not reach Add Share; see $SHOTDIR/${PROTO}_04_add_row.png"
  rm -f /tmp/slot_${PROTO}.txt
  exit 1
fi
adb shell "for f in name type host port path user domain tls ro enabled; do echo \"    \$f=\$(getprop persist.gammaos.share.$SLOT.\$f)\"; done" 2>/dev/null | tr -d '\r'

# The share existing is not the same as the share being the protocol that was asked for. If the
# type chooser was mis-navigated the editor happily produces a perfectly working share of the wrong
# kind, and every test after this would pass while measuring the wrong backend.
case "$PROTO" in dav) WANT_TYPE=webdav ;; *) WANT_TYPE="$PROTO" ;; esac
GOT_TYPE=$(adb shell "getprop persist.gammaos.share.$SLOT.type" 2>/dev/null | tr -d '\r')
if [ "$GOT_TYPE" != "$WANT_TYPE" ]; then
  echo "    ERROR: asked for '$WANT_TYPE' but the UI produced type '$GOT_TYPE' in slot $SLOT"
  echo "    the type chooser was mis-navigated; see $SHOTDIR/${PROTO}_07_type_set.png"
  rm -f /tmp/slot_${PROTO}.txt
  exit 1
fi
echo "    type confirmed: $GOT_TYPE"

# --- Enable (row 0) ---
echo "== $PROTO: enabling =="
UP 12
CAP "${PROTO}_13_before_enable"
NAV enter 2.5
CAP "${PROTO}_14_after_enable"

echo "    enabled=$(adb shell getprop persist.gammaos.share.$SLOT.enabled 2>/dev/null | tr -d '\r')"
echo "    slot=$SLOT"
echo "$SLOT" > /tmp/slot_${PROTO}.txt
