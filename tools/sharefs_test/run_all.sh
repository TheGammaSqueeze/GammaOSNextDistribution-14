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
