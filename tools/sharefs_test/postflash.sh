#!/bin/bash
# Re-establish the test rig after a flash and confirm the new bits are actually on the device.
#
# adb reverse does not survive a reboot, so the tunnels that let the device reach the test servers
# have to be rebuilt before anything else. This also captures the "after" half of the FTP
# before/after: on the old image the device's curl reported FTP as an unsupported protocol.

set -u
echo "== re-establishing reverse tunnels =="
for p in 445 2049 111 20048 2121 8088 30000 30001 30002 30003 30004 30005 30006 30007 30008 30009; do
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
# Permissive means denials are logged but not enforced. That is the more useful mode for this pass:
# every rule the policy is missing shows up in the log instead of silently killing a mount.

echo
echo "== nano running? =="
adb shell 'ps -A | grep -c gammaos-nano' 2>/dev/null | tr -d '\r' | sed 's/^/  nano procs: /'
echo "  minimal_boot=$(adb shell getprop sys.gammaos.minimal_boot 2>/dev/null | tr -d '\r')  ps3xmb=$(adb shell getprop persist.gammaos.nano.ps3xmb 2>/dev/null | tr -d '\r')"
