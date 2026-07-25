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
DOWN 14
CAP "${PROTO}_02_network_shares_row"
NAV enter 1.5
CAP "${PROTO}_03_shares_list"

echo "== $PROTO: Add Share =="
# The list shows existing shares then "Add Share"; selection starts at the top, so step past any
# share already configured.
EXISTING=$(adb shell 'for i in 1 2 3 4; do getprop persist.gammaos.share.$i.name; done' 2>/dev/null | tr -d '\r' | grep -c .)
echo "    $EXISTING share(s) already configured, stepping past them"
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

# --- Server (row 3) ---
echo "== $PROTO: server =$SERVER"
UP 12; DOWN "$R_SERVER"
NAV enter 1.2
TYPE "$SERVER"
CAP "${PROTO}_08_server"

# --- Port, where the protocol has one and we are not on its default ---
if [ -n "$R_PORT" ] && [ -n "$PORT" ]; then
  echo "== $PROTO: port =$PORT"
  UP 12; DOWN "$R_PORT"
  NAV enter 1.2
  TYPE "$PORT"
  CAP "${PROTO}_09_port"
fi

# --- Path / share name ---
if [ -n "$PATHV" ]; then
  echo "== $PROTO: path =$PATHV"
  UP 12; DOWN "$R_PATH"
  NAV enter 1.2
  TYPE "$PATHV"
  CAP "${PROTO}_10_path"
fi

# --- Credentials, where the protocol uses them ---
if [ -n "$R_USER" ] && [ -n "$USER" ]; then
  echo "== $PROTO: username =$USER"
  UP 12; DOWN "$R_USER"
  NAV enter 1.2
  TYPE "$USER"
  CAP "${PROTO}_11_user"

  echo "== $PROTO: password"
  UP 12; DOWN "$R_PASS"
  NAV enter 1.2
  TYPE "$PASS"
  CAP "${PROTO}_12_pass"
fi

# --- What the UI wrote, as evidence ---
echo "== $PROTO: properties the UI produced =="
SLOT=$(adb shell 'for i in 1 2 3 4; do n=$(getprop persist.gammaos.share.$i.name); [ -n "$n" ] && echo $i; done' 2>/dev/null | tr -d '\r' | tail -1)
adb shell "for f in name type host port path user domain tls ro enabled; do echo \"    \$f=\$(getprop persist.gammaos.share.$SLOT.\$f)\"; done" 2>/dev/null | tr -d '\r'

# --- Enable (row 0) ---
echo "== $PROTO: enabling =="
UP 12
CAP "${PROTO}_13_before_enable"
NAV enter 2.5
CAP "${PROTO}_14_after_enable"

echo "    enabled=$(adb shell getprop persist.gammaos.share.$SLOT.enabled 2>/dev/null | tr -d '\r')"
echo "    slot=$SLOT"
echo "$SLOT" > /tmp/slot_${PROTO}.txt
