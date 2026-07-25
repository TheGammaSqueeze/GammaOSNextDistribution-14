#!/bin/bash
# Re-run the share verification with SELinux actually enforcing.
#
# Why this exists. The test devices boot with ro.boot.selinux=permissive, which comes from the
# bootloader and cannot be changed from the system image. Every result gathered so far was therefore
# collected with the policy switched off: a missing allow rule shows up as a logged denial and the
# operation succeeds anyway. That makes "the feature works" and "the policy is correct" two entirely
# different claims, and only the first was ever tested.
#
# setenforce 1 flips the global state at runtime, so the same checks can be run for real. Anything
# that passes permissive and fails here is a rule that was never written.
#
#   usage: enforcing.sh [share-name]

set -u
cd "$(dirname "$0")"
NAME="${1:-Share 1}"
LOG=/tmp/sharefs_run
mkdir -p $LOG
D() { adb shell "$@" 2>&1 | tr -d '\r'; }

# Denials are counted rather than diffed against a saved copy: the kernel ring buffer wraps, so an
# absolute before/after comparison is more reliable than trying to match individual lines.
denials() { D "dmesg 2>/dev/null | grep -c 'avc: *denied'"; }
denials_for() { D "dmesg 2>/dev/null | grep 'avc: *denied' | grep -c '$1'"; }

restore() {
    echo
    echo "== restoring permissive =="
    D "setenforce 0" >/dev/null
    echo "  getenforce: $(D getenforce)"
}
trap restore EXIT

echo "=============================================================="
echo " enforcing-mode verification: '$NAME'"
echo "=============================================================="
echo "  before: getenforce=$(D getenforce), total denials=$(denials)"

BEFORE_BOOTANIM=$(denials_for 'scontext=u:r:bootanim')
BEFORE_SHAREFS=$(denials_for 'gammaos_sharefs')

echo
echo "== switching to enforcing =="
# Arm a watchdog on the device before flipping, not after.
#
# Enforcing a policy that the running system came up without can break things that were never
# exercised under it, and if one of those is adbd then the trap above cannot run: the restore
# command travels over the very channel that has gone. This runs on the device, survives losing the
# host, and puts it back to permissive on its own. The trap still handles the normal case, and
# putting it back twice is harmless.
adb shell "nohup sh -c 'sleep 420; setenforce 0' >/dev/null 2>&1 &" >/dev/null 2>&1
echo "  watchdog armed: the device returns to permissive by itself after 7 minutes"
D "setenforce 1" >/dev/null
STATE=$(D getenforce)
echo "  getenforce: $STATE"
if [ "$STATE" != "Enforcing" ]; then
    echo "  ABORT: could not enter enforcing mode, results would be meaningless"
    exit 1
fi

echo
echo "== the mount must survive the switch =="
D "mountpoint -q '/mnt/shares/$NAME' && echo y" | grep -q y \
    && echo "  PASS  still mounted" || echo "  FAIL  mount lost"
D "ls -d '/storage/$NAME'" | grep -q "$NAME" \
    && echo "  PASS  still published to /storage" || echo "  FAIL  /storage view lost"

echo
echo "== full reachability suite, enforcing =="
bash ./verify_saf.sh "$NAME" 2>&1 | tee $LOG/enforcing_saf.log
SAF_RC=${PIPESTATUS[0]}

echo
echo "== the menu's own view of /storage =="
# nano's File Explorer opens on /storage (NanoMenuFileExplorer.cpp), so it is the screen that breaks
# first if bootanim cannot read mnt_user_file. An empty list here is the visible symptom.
D "setprop sys.gammaos.nano.nav back" >/dev/null; sleep 0.4
for _ in 1 2 3 4 5 6; do D "setprop sys.gammaos.nano.nav back" >/dev/null; sleep 0.3; done
sleep 1
for _ in 1 2 3 4 5 6 7 8; do D "setprop sys.gammaos.nano.nav left" >/dev/null; sleep 0.3; done
D "setprop sys.gammaos.nano.nav right" >/dev/null; sleep 0.6
# Absolute row. The XMB remembers the selected row per column and "back" does not reset it, so a
# bare DOWN 13 lands wherever the last screen left off - this captured the Internet settings screen
# instead of the File Explorer, which is the one screen this check exists to look at.
for _ in $(seq 1 25); do D "setprop sys.gammaos.nano.nav up" >/dev/null; sleep 0.25; done
for _ in $(seq 1 13); do D "setprop sys.gammaos.nano.nav down" >/dev/null; sleep 0.3; done
D "setprop sys.gammaos.nano.nav enter" >/dev/null; sleep 2.5
D "setprop sys.gammaos.nano.shot 1" >/dev/null; sleep 1.3
adb pull /data/local/tmp/nano_shot.ppm /tmp/shots/enforcing_explorer.ppm >/dev/null 2>&1
[ -s /tmp/shots/enforcing_explorer.ppm ] \
    && convert /tmp/shots/enforcing_explorer.ppm /tmp/shots/enforcing_explorer.png 2>/dev/null \
    && echo "  shot: /tmp/shots/enforcing_explorer.png"

AFTER_BOOTANIM=$(denials_for 'scontext=u:r:bootanim')
AFTER_SHAREFS=$(denials_for 'gammaos_sharefs')
NEW_BOOTANIM=$((AFTER_BOOTANIM - BEFORE_BOOTANIM))
NEW_SHAREFS=$((AFTER_SHAREFS - BEFORE_SHAREFS))

echo
echo "== denials raised while enforcing =="
echo "  bootanim (the menu): $NEW_BOOTANIM new"
echo "  gammaos_sharefs:     $NEW_SHAREFS new"
echo
echo "-- distinct new denials, all domains --"
D "dmesg 2>/dev/null | grep 'avc: *denied' | grep 'permissive=0' | sed 's/.*avc:/avc:/' | sort -u | tail -40"

echo
echo "  SAF suite exit: $SAF_RC (0 = all passed)"
exit $SAF_RC
