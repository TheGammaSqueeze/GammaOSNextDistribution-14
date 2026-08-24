# Build, flash, and on-device verification

The workflow to build GammaOS Nano, put it on a device, and verify a change. Written for
the TrimUI Brick (Allwinner A133 "ceres", 1024x768 4:3), which is the reference low-end
target; other devices differ only in the image variant and flash transport.

## Build

- **Fast compile check** (catches C++ errors in ~5-10 min):
  ```
  source build/envsetup.sh
  lunch lineage_tv_arm64_bvN-ap2a-userdebug
  m gammaos-nano
  ```
- **Flashable image** (authoritative; the LTO image build can fail where `m gammaos-nano`
  passes, so this is the real check):
  ```
  bash buildtv_cc.sh nosync      # from the repo root
  ```
  `buildtv_cc.sh` builds the CC (read-barrier) boot image variant for old-kernel devices
  (the Brick). `buildtv.sh` builds the uffd/CMC variant for modern kernels. They share
  `out/`, so alternating between them rebuilds the boot image. Output:
  `out/home/build-output/lineage-21.0-<date>-UNOFFICIAL-tv_arm64_bvN.img` (raw ext4).

A `soong` re-analysis runs whenever `Android.bp` changes (adding a source or a lib); on
WSL2 this analysis phase is filesystem-bound and can take several minutes before any
compilation starts. That is normal, not a hang.

## Compress to EROFS

The Brick runs an lz4hc EROFS system image (read-only, ~740 MB smaller than ext4, faster
cold boot). Convert the newest build:
```
sudo bash /work/brick/system_erofs/build_system_erofs.sh
```
It auto-selects the newest `lineage-*-tv_arm64_bvN.img` and writes
`/work/brick/system_erofs/system.img.erofs`. It reads SELinux xattrs directly off the
mounted ext4, so no sidecar contexts are needed. The Brick's boot fstab falls back to
ext4 if the EROFS mount fails.

## Flash via fastbootd

Only the `system` partition changes for a nano-only edit; fastbootd auto-resizes the
logical slot inside `super`. No `super.img` repack unless the LP metadata is corrupted.

```
adb -s <target> reboot fastboot          # ONLY the target enters fastbootd
# wait for fastbootd (it re-enumerates USB, can take 60-90 s)
fastboot flash system /work/brick/system_erofs/system.img.erofs
fastboot reboot
```

**Operational rules learned the hard way:**

- **Wrap every `adb`/`fastboot` call in `timeout`.** On this WSL2 + Windows-USB setup the
  `adb`/`fastboot` binaries are the Windows `.exe`s (the device is on the Windows USB
  stack); a call can block indefinitely if the device is mid-transition. `timeout 8 ...`
  makes polling loops safe.
- **Issue exactly one `reboot fastboot`, then poll patiently.** Firing it repeatedly (or
  concurrently from a script plus a manual retry) can wedge the device: adbd still
  handshakes ("device") but shell and every reboot request hang, and it never enters
  fastbootd. Recovery from that state needs a physical power cycle.
- **With two devices connected, protect the control.** Reboot only the target to
  fastbootd; the control stays in Android and never appears in `fastboot devices`. A flash
  script should abort unless exactly one fastboot device is present.
- Confirm userspace fastboot before flashing: `fastboot getvar is-userspace` returns `yes`.
- Do not lock the bootloader; never touch `env.fex` / `boot_package` on the Brick.

## Two-device A/B testing

Run an identical control device alongside the flash target. Flash only the target; keep
the control on the baseline build. Diff their screenshots for the same theme/screen to
catch regressions objectively. Switching themes or settings on the control for a
comparison is fine; only flashing is off-limits.

## Screenshot capture

The DRM-direct path is black to SurfaceFlinger/fbdev capture, so nano has its own dump:
```
adb -s <dev> shell setprop sys.gammaos.nano.shot 1
adb -s <dev> pull /data/local/tmp/nano_shot.ppm shot.ppm
convert shot.ppm shot.png      # ImageMagick; the PPM is raw-oriented, do not rotate
```
Wake the panel first if the device may have slept (`input keyevent KEYCODE_WAKEUP`), and
sanity-check the clock in the shot against `date` so you are not reading a stale frame.

## Theme selection for testing

`persist.gammaos.nano.ndstheme` selects the home theme (`0` XMB, `1` DSi, `2` Minima,
`3` ES-DE). It is read at startup, so `setprop` then reboot, or switch live via Settings
-> Theme Settings -> Home Theme. For the ES-DE engine also deploy a theme set to
`/data/system/nano_esde_themes/<name>/` (see docs/THEME_ENGINE.md).
