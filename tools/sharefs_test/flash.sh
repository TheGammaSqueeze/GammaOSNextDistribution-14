#!/bin/bash
# Build the EROFS image from the freshly built system.img and flash it to the Brick.
#
# A full flash rather than an adb-remount overlay push: the SELinux policy for gammaos_sharefs is
# compiled into the image, so an overlay would give the new binaries with the old policy and every
# mount would die on a denial that has nothing to do with the code being tested.

set -euo pipefail

IMG=$(ls -1t /work/GammaOSNextDistribution-A14/out/home/build-output/lineage-21.0-*-tv_arm64_bvN.img 2>/dev/null | head -1)
[ -n "$IMG" ] || { echo "no system.img found in build-output"; exit 1; }
echo "== source image =="
ls -la "$IMG"

echo "== building erofs =="
sudo bash /work/brick/system_erofs/build_system_erofs.sh "$IMG"
EROFS=$(ls -1t /work/brick/system_erofs/*.erofs 2>/dev/null | head -1)
[ -n "$EROFS" ] || { echo "erofs build produced nothing"; exit 1; }
ls -la "$EROFS"

# The Brick must be flashed with the NEWER Windows fastboot. /usr/bin/fastboot is a symlink to an
# old build that mis-resolves this no-slot device (current-slot empty, slot-count 0) and sends the
# partition as "system_no", which fails with "Partition not found". v31.0.2 resolves bare "system".
# The AOSP Linux fastboot handles no-slot correctly but cannot see the device at all, because it is
# on the Windows USB stack and usbipd is not installed.
FB="/mnt/c/fastboot/fastboot.exe"
[ -x "$FB" ] || { echo "missing $FB - the old /usr/bin/fastboot will flash system_no and fail"; exit 1; }
echo "== fastboot: $("$FB" --version 2>/dev/null | head -1) =="

# The EROFS has to be somewhere the Windows binary can read.
WINIMG="/mnt/c/brick/gammashare_system.erofs"
sudo mkdir -p /mnt/c/brick 2>/dev/null || mkdir -p /mnt/c/brick
cp "$EROFS" "$WINIMG"
echo "  staged at $WINIMG ($(du -h "$WINIMG" | cut -f1))"

echo "== rebooting to fastbootd =="
adb reboot fastboot
# fastbootd re-enumerates USB on entry so the device drops off both adb and fastboot briefly.
# Poll generously rather than concluding failure early.
for i in $(seq 1 60); do
  if "$FB" devices 2>/dev/null | grep -q .; then break; fi
  sleep 2
done
"$FB" devices

# Size guard. This Brick's physical super is 3254779904 bytes (~3.03GB). Flashing a system image
# too large for it does not merely fail: it can delete the system logical partition mid-resize and
# leave the device unbootable. Refuse rather than risk that on an unattended device.
SUPER=$("$FB" getvar partition-size:super 2>&1 | grep -oE "0x[0-9a-fA-F]+|[0-9]+" | head -1)
IMGSZ=$(stat -c %s "$EROFS")
echo "== size check =="
echo "  image: $IMGSZ bytes ($(du -h "$EROFS" | cut -f1))"
echo "  super: ${SUPER:-unknown}"
if [ -n "$SUPER" ]; then
  case "$SUPER" in 0x*) SUPERDEC=$((SUPER)) ;; *) SUPERDEC=$SUPER ;; esac
  if [ "$IMGSZ" -ge "$SUPERDEC" ]; then
    echo "  ABORT: image does not fit in super ($IMGSZ >= $SUPERDEC)"
    "$FB" reboot
    exit 1
  fi
  echo "  fits, with $(( (SUPERDEC-IMGSZ)/1048576 ))MB of super to spare"
fi

echo "== flashing system =="
"$FB" flash system "C:\\brick\\gammashare_system.erofs"
echo "== rebooting =="
"$FB" reboot

echo "== waiting for boot =="
adb wait-for-device
for i in $(seq 1 90); do
  BC=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  [ "$BC" = "1" ] && { echo "  boot_completed after ~$((i*2))s"; break; }
  sleep 2
done
echo "== build id on device =="
adb shell getprop ro.build.version.incremental
