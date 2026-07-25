#!/bin/bash
# Re-establish the test rig after a flash and confirm the new bits are actually on the device.
#
# adb reverse does not survive a reboot, so the tunnels that let the device reach the test servers
# have to be rebuilt before anything else. This also captures the "after" half of the FTP
# before/after: on the old image the device's curl reported FTP as an unsupported protocol.

set -u
echo "== re-establishing reverse tunnels =="
# 4450, NOT 445. adb here is the Windows adb.exe, so a reverse for 445 terminates on the Windows
# side where Windows' own SMB server is listening (PID 4), and the device authenticates against
# that instead of the test Samba - which correctly rejects an account it has never heard of. The
# test Samba runs on 4450 for exactly this reason and the share config uses that port.
#
# 30000-30059 are the FTP passive data ports. Only the first ten were listed here, which is enough
# for a handful of transfers and not enough for a soak: FTP opens a fresh data connection per read
# window, so the range has to cover what the server is configured to hand out.
PORTS="4450 2049 111 20048 2121 8088 $(seq 30000 30059)"
for p in $PORTS; do
  adb reverse tcp:$p tcp:$p >/dev/null 2>&1 || echo "  FAILED to forward $p"
done
echo "  $(adb reverse --list | wc -l) tunnels up"

echo
echo "== new components present? =="
for f in /system/bin/gammaos-sharefs /system/lib64/libnfs.so /system/lib64/libsmb2.so /system/bin/gammaos-nano; do
  printf "  %-34s " "$f"
  adb shell "ls -la $f 2>/dev/null | awk '{print \$5\" bytes\"}'" 2>/dev/null | tr -d '\r' | grep -q bytes \
    && adb shell "ls -la $f | awk '{print \$5\" bytes\"}'" 2>/dev/null | tr -d '\r' \
    || echo "MISSING"
done

echo
echo "== FTP in libcurl (was 'Protocol \"ftp\" not supported' before the flash) =="
adb shell 'curl -s -m 10 -u gammatest:GammaTest123! ftp://127.0.0.1:2121/ 2>&1 | head -4' | tr -d '\r' | sed 's/^/  /'

echo
echo "== init service definitions present? =="
adb shell 'grep -c gammaos-sharefs /system/etc/init/gammaos-sharefs.rc 2>/dev/null' 2>/dev/null | tr -d '\r' | sed 's/^/  rc lines: /'

echo
echo "== selinux =="
echo "  enforcing: $(adb shell getenforce 2>/dev/null | tr -d '\r')"
# seinfo is not on the device, so check the label that file_contexts should have applied to the
# daemon binary. If that came out as gammaos_sharefs_exec the policy made it into the image.
echo "  daemon label: $(adb shell 'ls -Z /system/bin/gammaos-sharefs 2>/dev/null | awk "{print \$1}"' 2>/dev/null | tr -d '\r')"
# This device boots permissive (ro.boot.selinux=permissive, set by the bootloader and not
# changeable from the system image), so denials are logged but never enforced. That is useful for
# collecting missing rules in one pass, but it is NOT a test of the policy: "it works" and "the
# policy is correct" are different claims, and only the first is being measured here. Run
# enforcing.sh afterwards, which flips to enforcing at runtime and re-runs the checks for real.

echo
echo "== nano running? =="
adb shell 'ps -A | grep -c gammaos-nano' 2>/dev/null | tr -d '\r' | sed 's/^/  nano procs: /'
echo "  minimal_boot=$(adb shell getprop sys.gammaos.minimal_boot 2>/dev/null | tr -d '\r')  ps3xmb=$(adb shell getprop persist.gammaos.nano.ps3xmb 2>/dev/null | tr -d '\r')"
