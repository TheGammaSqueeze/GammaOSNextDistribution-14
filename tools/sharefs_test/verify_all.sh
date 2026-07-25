#!/bin/bash
# The whole verification, in the order the results depend on each other.
#
# Run this after a flash. It exists because the individual scripts have to happen in a particular
# order and with the rig re-established first, and doing that by hand is how a step gets skipped
# and the run gets reported as better than it was.
#
#   1. postflash   - rebuild the adb reverse tunnels (they do not survive a reboot) and confirm the
#                    new binaries and policy are actually in the image, so a failure later cannot be
#                    blamed on having tested the old build
#   2. run_all     - create each protocol's share through the nano menu, then correctness,
#                    throughput, soak, outage and the UI removal test
#   3. verify_saf  - the three consumers: /mnt/shares, /storage/<name>, and the SAF root
#   4. enforcing   - the same reachability checks with SELinux actually enforcing
#
# Enforcement goes last on purpose: it flips global state, and anything it perturbs should not be
# able to affect the measurements taken before it.

set -u
cd "$(dirname "$0")"
LOG=/tmp/sharefs_run
mkdir -p $LOG
SOAK_MIN="${SOAK_MIN:-2}"

step() { echo; echo "################################################################"; \
         echo "#  $1"; echo "################################################################"; }

step "1. post-flash rig + image contents"
bash ./postflash.sh 2>&1 | tee $LOG/final_postflash.log

step "2. all four protocols, created through the menu"
SOAK_MIN=$SOAK_MIN bash ./run_all.sh 2>&1 | tee $LOG/final_all.log

# run_all removes the last share through the UI, so pick one that is still configured for the
# consumer checks rather than assuming a name.
NAME=$(adb shell 'for i in 1 2 3 4; do n=$(getprop persist.gammaos.share.$i.name); [ -n "$n" ] && echo $n; done' \
       2>/dev/null | tr -d '\r' | head -1)
if [ -z "$NAME" ]; then
    echo "no share left configured, cannot run the consumer checks"
    exit 1
fi

step "3. the three consumers ('$NAME')"
bash ./verify_saf.sh "$NAME" 2>&1 | tee $LOG/final_saf.log
SAF_RC=${PIPESTATUS[0]}

step "4. the same, with SELinux enforcing ('$NAME')"
bash ./enforcing.sh "$NAME" 2>&1 | tee $LOG/final_enforcing.log
ENF_RC=${PIPESTATUS[0]}

step "summary"
echo "  protocols:   $(grep -c '^PASS' $LOG/all_results.txt 2>/dev/null || echo 0) passed, $(grep -c '^FAIL' $LOG/all_results.txt 2>/dev/null || echo 0) failed"
echo "  consumers:   exit $SAF_RC"
echo "  enforcing:   exit $ENF_RC"
echo
echo "  protocol failures:"
grep '^FAIL' $LOG/all_results.txt 2>/dev/null | sed 's/^/    /' || echo "    none"
echo
echo "  denials raised while enforcing:"
grep -A40 'denials raised while enforcing' $LOG/final_enforcing.log 2>/dev/null | tail -20 | sed 's/^/    /'
