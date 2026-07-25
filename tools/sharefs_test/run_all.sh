#!/bin/bash
# Full unattended run: add each share through the nano UI, wait for it to mount, then test it.
#
# Order matters. SMB goes first because it is the protocol most people will actually use, so if the
# run has to be cut short that is the one with results. NFS second (no credentials, so it isolates
# whether a failure is about auth), then WebDAV and FTP, which share the curl backend and therefore
# tend to fail together when they fail.

set -u
cd "$(dirname "$0")"
LOG=/tmp/sharefs_run
mkdir -p $LOG
SUMMARY=$LOG/summary.txt
: > $SUMMARY

SOAK_MIN="${SOAK_MIN:-2}"

wait_for_mount() {
  # The daemon connects after init starts it, so the mount appears a moment after the toggle.
  local name="$1" tries="${2:-30}"
  for i in $(seq 1 "$tries"); do
    if adb shell "mountpoint -q \"/mnt/shares/$name\" && echo y" 2>/dev/null | tr -d '\r' | grep -q y; then
      echo "  mounted after ~$((i*2))s"; return 0
    fi
    sleep 2
  done
  echo "  NOT MOUNTED after $((tries*2))s"
  echo "  -- daemon log --"
  adb shell "logcat -d -s gammaos-sharefs gammaos-sharefs-smb gammaos-sharefs-nfs gammaos-sharefs-curl 2>/dev/null | tail -25" 2>/dev/null | tr -d '\r' | sed 's/^/    /'
  return 1
}

# Start from no shares at all.
#
# There are only kMaxShares (4) slots, so a share left over from an earlier run plus one per
# protocol does not fit and the last protocol would fail with "All 4 shares are in use" rather than
# for any reason to do with the protocol. Clearing first makes the four fit exactly.
#
# This is the one place properties are written directly, and it is teardown rather than the thing
# being tested: every share the suite goes on to measure is created, configured and enabled through
# the real menu by ui_add_share.sh. Removal through the UI is covered separately at the end.
echo "== clearing any existing shares =="
for i in 1 2 3 4; do
  adb shell "setprop persist.gammaos.share.$i.enabled 0" >/dev/null 2>&1
done
sleep 3
for i in 1 2 3 4; do
  for f in name type host port path user pass domain tls ro enabled; do
    adb shell "setprop persist.gammaos.share.$i.$f ''" >/dev/null 2>&1
  done
done
sleep 2
LEFTOVER=$(adb shell "ls /mnt/shares/ 2>/dev/null | wc -l" 2>/dev/null | tr -d '\r')
echo "  shares configured: $(adb shell 'for i in 1 2 3 4; do getprop persist.gammaos.share.$i.name; done' 2>/dev/null | tr -d '\r' | grep -c .), mount points left: ${LEFTOVER:-?}"

for P in smb nfs dav ftp; do
  echo
  echo "################################################################"
  echo "#  $P"
  echo "################################################################"
  bash ./ui_add_share.sh "$P" 2>&1 | tee $LOG/${P}_ui.log
  SLOT=$(cat /tmp/slot_${P}.txt 2>/dev/null || echo "")
  NAME=$(adb shell "getprop persist.gammaos.share.${SLOT}.name" 2>/dev/null | tr -d '\r')
  echo "  share name: '$NAME' (slot $SLOT)"
  echo "$P slot=$SLOT name='$NAME'" >> $SUMMARY

  if [ -z "$NAME" ]; then
    echo "  ABORT $P: the UI did not produce a share"
    echo "FAIL|$P|ui created share|no name property" >> $SUMMARY
    continue
  fi

  if wait_for_mount "$NAME" 30; then
    echo "PASS|$P|mounted|" >> $SUMMARY
    bash ./run_tests.sh "$NAME" "$P" 2>&1 | tee $LOG/${P}_func.log
    bash ./stability.sh "$NAME" "$P" "/tmp/testsrv/ctl.sh $P" "$SOAK_MIN" 2>&1 | tee $LOG/${P}_stab.log
  else
    echo "FAIL|$P|mounted|daemon never published the mount" >> $SUMMARY
  fi
  # Collect anything the policy would have blocked on an enforcing device.
  adb shell "dmesg 2>/dev/null | grep 'avc:.*gammaos_sharefs' | tail -20" 2>/dev/null | tr -d '\r' > $LOG/${P}_avc.log
done

echo
echo "################################################################"
echo "#  UI surfaces (folder picker + file explorer)"
echo "################################################################"
bash ./ui_verify_pickers.sh 2>&1 | tee $LOG/pickers.log

echo
echo "################################################################"
echo "#  removing a share through the UI"
echo "################################################################"
# Whichever share ended up in the last slot; removing it has to stop the daemon and take both the
# /mnt/shares mount and the /storage bind down with it.
LAST=$(adb shell 'for i in 1 2 3 4; do n=$(getprop persist.gammaos.share.$i.name); [ -n "$n" ] && echo $n; done' 2>/dev/null | tr -d '\r' | tail -1)
if [ -n "$LAST" ]; then
  bash ./ui_remove_share.sh "$LAST" 2>&1 | tee $LOG/remove.log
  grep -q '^  FAIL' $LOG/remove.log && echo "FAIL|ui|remove share|" >> $SUMMARY \
                                    || echo "PASS|ui|remove share|" >> $SUMMARY
else
  echo "  no share left to remove"
fi

echo
echo "################################################################"
echo "#  results"
echo "################################################################"
cat /tmp/results_*.txt 2>/dev/null > $LOG/all_results.txt
echo "  $(grep -c '^PASS' $LOG/all_results.txt 2>/dev/null || echo 0) passed"
echo "  $(grep -c '^FAIL' $LOG/all_results.txt 2>/dev/null || echo 0) failed"
echo
echo "  failures:"
grep '^FAIL' $LOG/all_results.txt 2>/dev/null | sed 's/^/    /' || echo "    none"
echo
echo "  measurements:"
grep '^INFO' $LOG/all_results.txt 2>/dev/null | awk -F'|' '{printf "    %-6s %-32s %s\n", $2, $3, $4}'
