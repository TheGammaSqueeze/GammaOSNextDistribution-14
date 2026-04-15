# GammaOS Drastic-Nano Shim - Status, Plan, and Gotchas

Last updated: 2026-04-15 (late evening, post-context-compact handover).

## POST-COMPACT HANDOVER - 2026-04-15 ~16:50 BST

**If you are resuming this work fresh, read this first. Then read
`memory/drastic_nano_shim.md` for the operational checklist.**

State going into this handover:
- v20 APK (`2f2d8b4e8ab6c76b232b0b7a03db3cbb`) and v20
  `libdrastic_nano_shim.so` (`ef6037a1394f2786194ad9cedc621bcf`)
  are staged in `packages/apps/GammaDrasticNanoShim/`.
- Fresh system image built 16:48 BST at
  `out/home/build-output/lineage-21.0-20260415-UNOFFICIAL-arm64_bvN.img`
  (3.52 GB). This image contains the v20 libs. It has NOT been
  flashed yet.
- Device lock still held (do not release).
- Previous flash on device (~16:25 BST) used an image built BEFORE
  the v20 lib was copied into the repo - that image has the v19
  lib. It cannot validate v20's claims.

v20 targets two blockers identified during the v19 test run
(2026-04-15 16:25 BST), both expert-side fixes:

1. **Vtable layout**: shim's `ANativeWindowVtable` redeclaration was
   136 bytes vs AOSP's 192 - missing 56 bytes of flags, min/max
   swap interval, xdpi/ydpi, padding, `oem[4]` between
   `android_native_base_t` common and the function pointers. Every
   slot was offset by 56 bytes; shim was dispatching through the
   wrong slots. Evidence from v19 logs: `setSwapInterval` received
   `0x9119f0a0` (pointer-shaped, not an int), and
   `nano_dequeueBuffer` entry log had zero hits across 25k lines
   despite the same vtable's `setSwapInterval` and `perform` firing.
   v20 adds a `static_assert(sizeof == 192)` to prevent regression.
2. **DraSticPathCache init**: v19 fired `initFilesystemRoots` and
   set `d.a=/data/data/com.dsemu.drastic/files/DraStic` correctly,
   but `DraSticJNI.fxLoad` still NPE'd at `DraSticPathCache.getRealPath`
   on interface method `b.u(String)` (field `b` uninitialised).
   v20 adds `d.s(root)` and `b.s(ctx)` calls after
   `initFilesystemRoots`.

Immediate next actions (in order):
1. `./device-lock.sh status` → confirm LOCKED.
2. `cp out/home/build-output/lineage-21.0-20260415-UNOFFICIAL-arm64_bvN.img
   /mnt/c/next/lineage.img`
3. `adb reboot fastboot`, `fastboot flash system ...`, `fastboot reboot`.
4. Re-verify QR props (`qr_prepared=1` auto-clears to 0 after failed QR runs).
5. Clear logcat, reboot once more to re-trigger auto-QR.
6. Capture ~35s logcat; grep
   `NanoAnw|drastic_nano_shim|initFilesystemRoots|setupFbosForRing|OP_SET_SWAP|OP_QUEUE|FATAL|DraSticPathCache|startGame|nano_dequeueBuffer`.
7. Expected passes: `nano_dequeueBuffer entry` appears,
   `setSwapInterval` has a sensible int (0/1/-1), no NPE at
   `fxLoad`, `OP_QUEUE` lands on NanoBridgeServer.
8. Append a new dated section to
   `/mnt/c/rgds/drastic/nano-startup-timing-answers.md` with
   findings. Mirror summary to Discord `#claude` channel
   (`1484637782608711865`) via `mcp__plugin_discord_discord__reply`
   (the param is `text`, not `content`).

User lesson reinforced this session (keep observing): when a new
shim APK lands from the expert, extract its libs and update the
staged `.so` files under `packages/apps/GammaDrasticNanoShim/lib/arm64-v8a/`
- they are NOT auto-extracted by the build. Clean extract command:
`cd /tmp && rm -rf ex && mkdir ex && cd ex && unzip -o /mnt/c/rgds/drastic/drastic-nano-shim.apk 'lib/arm64-v8a/*'`;
then diff MD5s against the staged copies and only overwrite what changed.

## Earlier state - Option C validation (2026-04-15 15:45 BST)

**Where we are**: Option C (SF AIDL stub binary) is VALIDATED on
device 2026-04-15 15:45 BST. Shim no longer hangs in
`SurfaceView.<init>` - it flows past the Option B blocker and
reaches `DraSticJNI.startGame`. Build
`lineage-21.0-20260415-UNOFFICIAL-arm64_bvN.img` (built 15:33)
contains all Option C artefacts + v18 shim APK; device is
flashed and the lock is held by the current session.

**Test outcome summary**:
- `gammaos_sf_stub` PID 1513 registers `SurfaceFlingerAIDL` +
  legacy `SurfaceFlinger` with servicemanager. `ComposerServiceAIDL
  reconnected` line confirms shim picked up the stub.
- Shim Java progresses through `DraSticGlView` ctor,
  `surfaceCreated`, `surfaceChanged`, then `NanoFactory
  .createWindowSurface mode=auto` → SF path fails gracefully
  (`Surface$OutOfResourcesException: NO_INIT`, expected) →
  pbuffer fallback for FBO mode.
- NanoBridge allocates 4 AHBs (640x960 RGBA8888), handshake
  done, NanoAnw returned to shim.
- Shim reaches `DraSticJNI.startGame rom=/proc/self/fd/5`
  (previous farthest progress, now reached consistently).
- **Failure point:** FATAL NullPointerException in GLThread:
  `DraSticPathCache.getRealPath -> interface method 'b.u(String)'
  on null object reference` during `DraSticJNI.fxLoad` on
  `onSurfaceCreated`. Also `setupFbosForRing[0]: dequeueBuffer
  rc=0 buf=0x0` warning immediately before.

**Both new failures are shim/drastic-side**, not stub-side.
Expert (drastic-android-mod Claude) has been notified via
`/mnt/c/rgds/drastic/nano-startup-timing-answers.md`
(section `## 2026-04-15 evening - Option C stub TEST RESULT`).
Standing by for v19 APK.

**What the stub does**: registers an empty `BBinder` under the
service names `SurfaceFlingerAIDL` and `SurfaceFlinger` with
servicemanager. Its sole job is to satisfy the shim's blocking
`waitForService("SurfaceFlingerAIDL")` call inside
`SurfaceComposerClient::onFirstRef()` (which `SurfaceView.<init>`
triggers via JNI `nativeCreate`). Once registered, the shim's
SCC ctor completes with `mStatus == NO_INIT` (graceful, because
`createConnection()` returns UNKNOWN_TRANSACTION against our
empty binder; the condition `status.isOk() && conn != nullptr`
is false and the ctor just moves on).

**Files changed this session**:
1. NEW `frameworks/base/cmds/gammaos-nano/SfAidlStub.cpp` (~90 lines)
2. MOD `frameworks/base/cmds/gammaos-nano/Android.bp` - added
   `cc_binary { name: "gammaos_sf_stub" ... }` and added
   `"gammaos_sf_stub"` to gammaos-nano's `required:` list
3. MOD `frameworks/base/cmds/gammaos-nano/gammaos-nano.rc` -
   added `service gammaos_sf_stub /system/bin/gammaos_sf_stub`
   in class `nano_sf_stub`, user=system, group=graphics,
   seclabel `u:r:gammaos_sf_stub:s0`, NOT oneshot
4. NEW `system/sepolicy/private/gammaos_sf_stub.te` (permissive
   domain; `binder_use` + `add_service(surfaceflinger_service)`
   + init transition allow)
5. NEW `system/sepolicy/prebuilts/api/34.0/private/gammaos_sf_stub.te`
   (identical mirror)
6. NEW `system/sepolicy/prebuilts/api/202404/private/gammaos_sf_stub.te`
   (identical mirror)
7. MOD `frameworks/base/cmds/gammaos-nano/NanoMenu.cpp` around
   line 8420 - handoff now: `ctl.stop hwcomposer-3` → 200ms →
   `ctl.stop surfaceflinger` → 300ms → `ctl.start
   gammaos_sf_stub` → 300ms → `ctl.start gammaos_drastic_shim`.
   Comment updated to "Option C: SF AIDL stub".

**Next steps, in order**:
1. Copy v18 APK into the shim landing slot FIRST (expert shipped
   it post-handover; see section "v18 APK ready" below):
   - `cp /mnt/c/rgds/drastic/drastic-nano-shim.apk
     packages/apps/GammaDrasticNanoShim/drastic-nano-shim.apk`
   - Verify MD5 is `e492be72fd6078968cbd6c902d1cb043` (v18).
     v17 was `5f1b78d28d5a985d2f45de5688eba314`.
2. `./device-lock.sh force-release` (orphan holder)
3. `./device-lock.sh claim`
4. `./build-queued.sh` (mobile build)
5. `cp out/home/build-output/lineage*.img /mnt/c/next/`
6. `adb reboot fastboot; /mnt/c/Windows/System32/cmd.exe /C "fastboot flash system c:\next\<image>.img && fastboot reboot"`
7. Set nano-shim triggers:
   ```
   adb shell setprop persist.gammaos.nano.qr_prepared 1
   adb shell setprop persist.gammaos.nano.qr_core drastic
   adb shell setprop persist.gammaos.nano.drastic_app 1
   adb shell setprop persist.bootanim.skip_nano 0
   # Leave drastic_gfx_mode UNSET for first test (defaults to
   # `auto` -- v18's NanoFactory tries SF, falls back to
   # pbuffer). This just validates the stub unblocks the hang.
   # After success, set it to `fbo` to exercise the rendering
   # path via NanoBridge AHB ring.
   # adb shell setprop persist.gammaos.nano.drastic_gfx_mode fbo
   adb shell 'echo /storage/00000000-0000-0000-0000-000000000001/nds/0177\ -\ Sonic\ Rush\ \(USA\)\ \(En,Ja,Fr,De,Es,It\).nds > /data/system/nano_qr_rom.txt'
   adb reboot
   ```
8. After boot, capture logs:
   ```
   adb logcat -d 2>&1 | grep -E "gammaos_sf_stub|NanoDraStic|NanoFactory|drastic_nano_shim|ComposerServiceAIDL|waitForService|FATAL"
   ```
9. Expected success markers:
   - `gammaos_sf_stub: registered SurfaceFlingerAIDL`
   - Shim log advances past `DraSticGlView.<init>` (i.e., past
     SCC.onFirstRef)
   - NanoFactory.tryCreateSfSurface logs Builder.build()
     failing gracefully, falls back to pbuffer
   - `DraSticJNI.startGame` entered (previous farthest progress)
10. Expected "not yet visible" marker:
    - No visible rendering on screen because v17's fallback is
      1x1 pbuffer. That is the intended v17 behaviour; expert
      will ship v18 with FBO mode reactivation that actually
      renders via NanoBridge AHB ring.
11. Append log excerpt + next-step ask to expert at
    `/mnt/c/rgds/drastic/nano-startup-timing-answers.md`
    ("### 2026-04-15 (or later) -- Option C stub test result")

**v18 APK ready** (shipped by expert after the initial handover
note was written):
- Dropped at `/mnt/c/rgds/drastic/drastic-nano-shim.apk`
  (MD5 `e492be72fd6078968cbd6c902d1cb043`).
- `NanoFactory.createWindowSurface` now reads
  `persist.gammaos.nano.drastic_gfx_mode`:
  - `sf`: only SF path; no pbuffer fallback (renders never
    start if SC.Builder fails).
  - `fbo`: skip SF; directly pbuffer + expects nano ANW for
    FBO ring setup.
  - `auto` (default): try SF first, fall back to pbuffer on
    failure.
- With stub + `auto`: SC.Builder fails silently (stub returns
  UNKNOWN_TRANSACTION), falls back to pbuffer,
  `setupFbosForRing` tries and fails (no ANW because nano's
  NanoBridge server isn't being fed by anything yet). Shim
  still runs past the hang -- that alone validates the stub
  mechanism.
- With stub + `fbo` and NanoBridge ANW alive: FBOs bind to
  AHB-backed EGLImages, drastic renders to ring, nano
  drmFlipRingSlot. This is the actual rendering milestone.

**Shim APK's bundled libdrastic_arm64.so** has 3 binary patches
(MD5 `80308fd29b8051460b8c3f06b0ea9dae`): audio `0x1d760` ret,
plus two longjmp-error-bail sites at `0x17304` and `0x1b4d0`
changed to ret. All 3 are essential -- DraSticJNI.<clinit> will
SIGSEGV at siglongjmp+392 without them. If the expert ever
rebuilds the .so, verify the MD5 matches before flashing.

**Known hazards to re-check on the next boot**:
- `gammaos_sf_stub` may fail to register if SELinux blocks
  `add_service(gammaos_sf_stub, surfaceflinger_service)` in
  enforcing. Domain is marked `permissive` so denials log but
  shouldn't block.
- init's `seclabel` transition requires `allow init
  gammaos_sf_stub:process { transition }` which is in the .te.
- `gammaos_sf_stub` binary file label is default `system_file`
  (no explicit file_contexts entry); the .te allows
  `system_file:file { rx_file_perms entrypoint }`.
- If `sm->addService` returns an error, the stub exits (main
  returns 1) and the shim will hang again. Logcat for
  `gammaos_sf_stub` tag is the diagnostic.
- The 300ms sleep between stub start and shim start may be too
  short on slow boot. Prefer polling `service check
  SurfaceFlingerAIDL` (via service manager binder) with a
  timeout; left as sleep for v1.

**Do NOT**:
- Retry EGL_BAD_NATIVE_WINDOW tuning with the custom NanoAnw
  against Mali's eglCreateWindowSurface. Mali preflight rejects
  the struct regardless of version/concrete_type/vtable. FBO
  mode (v18) sidesteps this entirely.
- Go back to Option A cohabit (SF alive). User explicitly
  rejected that on 2026-04-15: "I need to ensure that nano is
  driving the drm".
- Try to smali-patch `android.view.SurfaceView` itself. It's
  framework code outside the shim APK.
- Skip the HWC stop before SF stop. `hwcomposer-3` has
  `onrestart restart surfaceflinger` so SF re-spawns if you
  kill it first.

**Collaboration contract with expert (drastic-android-mod
Claude session)**:
- Expert reads/writes `/mnt/c/rgds/drastic/nano-startup-timing-
  answers.md` (15674 lines as of now, search `## 2026-04-15`).
- Expert ships shim APK + patched libdrastic_*.so as three
  files into `packages/apps/GammaDrasticNanoShim/` (copied from
  expert's output dir). Current shipped: v17. Expert has v18
  queued with `persist.gammaos.nano.drastic_gfx_mode` =
  `sf|fbo|auto` switch (waiting on stub validation signal).
- Mirror all user-facing text to Discord channel
  `1484637782608711865` (#claude) via `mcp__plugin_discord_
  discord__reply`.

**Key memory files to re-read if compacted**:
- `~/.claude/projects/-work-GammaOSNextDistribution-A14/memory/
  drastic_nano_shim.md`
- `~/.claude/projects/-work-GammaOSNextDistribution-A14/memory/
  nano_drastic_qr.md` (QR preview path; Option C reuses the
  same NanoBridge server and AHB ring infrastructure)
- `~/.claude/projects/-work-GammaOSNextDistribution-A14/memory/
  nano-boot.md` (minimal boot props and class ordering)
- `~/.claude/projects/-work-GammaOSNextDistribution-A14/memory/
  MEMORY.md` (index)

---

## Goal

Run the full stock `com.dsemu.drastic` APK out-of-process from
gammaos-nano so nano owns the rendering pipeline (DRM PRIME zero-copy
ring) instead of going through SurfaceFlinger + HWC. User benefits:
lower latency, lower overhead, the same display fast path that the
existing QR preview (`DrasticRunner` fake-JNI) already proves works
beautifully on RG DS.

The drastic APK runs unmodified except for a smali overlay
(`packages/apps/GammaDrasticNanoShim/drastic-nano-shim.apk`,
package `com.dsemu.drastic.nano`) that swaps `DraSticGlView`'s
default surface factory for one that hands drastic a buffer the
nano process can scan out directly.

## Architecture

```
nano XMB selects NDS game
  -> NanoMenu.cpp QR-handoff branch
     (gated by persist.gammaos.nano.drastic_app=1)
  -> sets persist.gammaos.nano.drastic_rom / drastic_apk / drastic_shim
  -> stops hwcomposer-3 + surfaceflinger (DRM master release; OPEN)
  -> setprop ctl.start gammaos_drastic_shim
init starts gammaos_drastic_shim_launcher (root, gammaos_drastic_nano domain)
  -> opens ROM as fd while root (sidesteps stock drastic UID's lack of media_rw)
  -> stages /system/etc/drastic_nano/libdrastic_*.so to
     /data/data/com.dsemu.drastic/drastic_nano_libs/ (namespace-permitted dir)
  -> setprop persist.gammaos.nano.drastic_rom_fd=N
  -> setgid+setuid to stock drastic UID (10186 on this device)
  -> capset drop, setexeccon u:r:gammaos_drastic_nano:s0
  -> execve /system/bin/app_process with shim+drastic classpath, entry
     gammaos.drastic.NanoDraSticEntry
app_process inside shim
  -> loads classes from shim.apk + drastic.apk (PathClassLoader)
  -> NanoDraSticEntry.main runs: stub Context, preload patched .so,
     instantiate DraSticGlView (smali-replaced), wire factories,
     fireSurfaceCreated + fireSurfaceChanged
  -> $j.onDrawFrame calls DraSticJNI.renderFrame in tight loop
NanoBridge cross-process IPC (libgammaos_nano_bridge.so in shim,
  NanoBridgeServer in nano)
  -> AHardwareBuffer ring transferred via SCM_RIGHTS
  -> shim queueBuffer signals nano via socket
  -> nano drmFlipRingSlot -> DRM PRIME -> panel
```

## What's working

- Build chain: `packages/apps/GammaDrasticNanoShim/Android.bp` with
  `android_app_import` + `prebuilt_etc` for the patched .so files.
- Init service `gammaos_drastic_shim` defined in
  `frameworks/base/cmds/gammaos-nano/gammaos-nano.rc`.
- `ShimLauncher.cpp`: opens ROM fd, stages libs, drops to drastic UID,
  exec app_process. Verified end-to-end on device.
- Real ROM path discovery (`/data/system/nano_qr_rom.txt` source of
  truth, written by `setQrRomPath` from XMB selection).
- ROM fd inherit via `persist.gammaos.nano.drastic_rom_fd` - shim
  reads `/proc/self/fd/N` as the path passed to
  `DraSticJNI.startGame`. Drastic's native open accepts pseudo-paths.
- Patched libdrastic_arm64.so bundled in shim APK and staged at
  runtime to `/data/data/com.dsemu.drastic/drastic_nano_libs/`. The
  v10 patches (longjmp + audio) cleared the SIGSEGV at bionic
  siglongjmp+392 that stock drastic hit.
- Shim runs ART under stock drastic UID, all class probes pass
  (`NativePathHandle`, `DraSticPathCache`, `DraSticJNI`,
  `DraSticGlView`).
- Java-side init: `DraSticJNI.<clinit>` ok, `new DraSticGlView`
  ok, `onInit` ok, `applyConfig` ok, `startGame` enters main loop.
- NanoBridge socket handshake: `/dev/socket/gammaos_nano_bridge`
  declared via init `socket`, picked up by server via
  `android_get_control_socket`. Shim connects via libgammaos_nano_
  bridge.so. `OP_HELLO` round-trip works (`handshake done,
  dims=640x960 format=1`).
- Custom NanoFactory wired via smali (replaces drastic's default
  factory in `DraSticGlView.<init>`).
- SELinux: `gammaos_drastic_nano` domain in
  `system/sepolicy/private/gammaos_drastic_nano.te` (mirrored to
  `prebuilts/api/{34.0,202404}/private/`). Permissive during
  bringup. App_data_file rw, dalvikcache access, bridge socket
  connect.

## What's NOT working (current blocker)

`eglCreateWindowSurface` returns 0x300b (`EGL_BAD_NATIVE_WINDOW`)
when given our custom NanoBridge ANativeWindow. Mali driver
(`/vendor/lib64/egl/libGLES_mali.so`, RK3568) rejects the ANW
**before** calling any of its perform/query vtable slots - meaning
our custom ANW fails struct-level validation in libEGL_android.so
or Mali's ANW preflight, not a perform op we could fix.

Verified clean by exhaustive checks:
- Dimensions populated eagerly via HELLO handshake (640x960).
- Format set (RGBA8888 = 1).
- `common.magic` = `ANDROID_NATIVE_WINDOW_MAGIC`.
- `common.version` tried 192 (our build's sizeof) and 184 (older
  AOSP guess).
- `query(NATIVE_WINDOW_CONCRETE_TYPE)` tried both `SURFACE` (1)
  and `FRAMEBUFFER` (0).
- `incRef`/`decRef` lambdas non-null.
- Vtable fully populated (10 function pointers).
- Hex dump of struct shows clean layout.

## Things tried that did NOT fix EGL_BAD_NATIVE_WINDOW

- Lying about `common.version` (184, 192).
- Switching `CONCRETE_TYPE` between SURFACE and FRAMEBUFFER.
- Eager handshake before EGL queries.
- Loading the right `libdrastic_nano_shim.so`.

The Mali binary contains the string
`EGL_BAD_NATIVE_WINDOW:nullptr Window` - Mali sees a "null Window"
ref from somewhere in its preflight and bails. Likely some RTTI
or pointer-table check that recognises a real `Surface`/
BufferQueueProducer chain and rejects ours.

## Architectural dilemma surfaced

To use Mali's standard EGL, the shim must hand it a real Android
Surface. Real Surface needs SurfaceFlinger's BufferQueueConsumer.
SF needs HWC. HWC owns DRM master.

So:
- If SF + HWC alive → DRM master held by HWC, nano can't drive
  the panel. Cohabit mode works for rendering but loses the
  perf/latency benefit.
- If SF + HWC stopped → DRM master free for nano, but the shim's
  ART runtime hangs forever on `ServiceManagerCppClient: Waited
  one second for SurfaceFlingerAIDL`. ART (or libgui from
  inside Mali EGL) makes a binder call to SF that blocks
  forever when SF is dead, even with our binderDied tolerance
  flag set in nano.

## Three viable options to unblock

### Option A - Cohabit (current pivot direction)

- SF + HWC stay alive
- Nano forks shim, doesn't touch DRM
- Shim's NanoFactory uses `SurfaceComposerClient` to create a
  real Android Surface, then `eglCreateWindowSurface` on its
  ANativeWindow. SF accepts.
- Drastic renders into SF's BufferQueue, SF composites to panel
  via HWC.
- LOSES: nano's DRM PRIME zero-copy fast path. Adds the SF +
  HWC composite cost we wanted to avoid. Volume slider + system
  UI also composite (which user noted as evidence SF was still
  active).

This is what APK v17 (in `/mnt/c/rgds/drastic/drastic-nano-shim.
apk`, MD5 5f1b78d28d5a985d2f45de5688eba314) implements.

### Option B - Strip every SF binder dep from the shim

- Nano kills SF + HWC, takes DRM master, drives the AHB ring.
- Shim runs without SF available.
- Requires identifying every `IServiceManager::checkService(
  "SurfaceFlinger" or "SurfaceFlingerAIDL")` call inside ART
  / Mali EGL / libgui and stubbing it.

The hang we observed in v42 was inside `ServiceManagerCppClient
::waitForService("SurfaceFlingerAIDL")` from pid 1494, called
during EGL or GLSurfaceView early init. Need a strace or stack
trace to identify the exact frame.

Possibly fixable if it's a single call site - replace with
returns-null. Probably MULTIPLE call sites, since libgui is
heavily intertwined with SF.

### Option C - Stub SurfaceFlingerAIDL responder

- Keep SF + HWC stopped (free DRM for nano).
- Spawn a minimal binder service that registers as
  `SurfaceFlingerAIDL` and returns harmless defaults for the
  calls ART makes during EGL init.
- Shim's ART thinks SF exists, proceeds. Real composition
  happens via NanoBridge to nano's DRM ring.

Largest engineering scope but most decoupled. Could land as a
new gammaos-nano subprocess that runs alongside nano during the
shim session.

## User direction (2026-04-15)

After seeing the cohabit decision in this turn, user said:
> "actually I need to ensure that nano is driving the drm, is
>  this still possible? nano does a better of lower overheads
>  and reducing the latency to get content on screen"

So Option A is OFF the long-term roadmap. Need Option B or C.

## Recommendation for next session

1. **First test Option A as a baseline** (current build, APK v17)
   to confirm the rest of the flow (Java init, ROM load, drastic
   main loop, save/load) is correct. Validates everything except
   the final compositing path.

2. **Then implement Option B**: instrument the shim's hang point
   to find the exact `waitForService("SurfaceFlingerAIDL")` call
   site, then patch around it (either stub the binder, stub the
   service lookup at the app_process level, or LD_PRELOAD a
   shim that returns synthetic SF binder).

3. Option C is a fallback if Option B is too whack-a-mole.

## Files to read first in a fresh session

- `nano-startup-timing-answers.md` (the multi-thousand-line
  expert dialog, in `/mnt/c/rgds/drastic/`). Search for `## 2026-04-15`
  to see the day's iterations.
- `frameworks/base/cmds/gammaos-nano/`:
  - `NanoMenu.cpp` - XMB handoff branch (search for
    `nano-shim handoff path selected`)
  - `ShimLauncher.cpp` - init service binary
  - `NanoBridge.h` / `Server.cpp` / `Client.cpp` - bridge IPC
  - `NanoAnw.cpp` - custom ANativeWindow (currently failing
    Mali validation; lives for Option B/C revival)
- `packages/apps/GammaDrasticNanoShim/` - APK landing slot +
  prebuilt_etc for the patched .so files
- `system/sepolicy/private/gammaos_drastic_nano.te` (mirrored
  in prebuilts/api/{34.0,202404}/private/)

## Gotchas, concrete

- `android_app_import` does NOT extract native libs from the APK
  zip. Hence `prebuilt_etc` shipping libdrastic_*.so to
  `/system/etc/drastic_nano/`, plus `ShimLauncher` runtime stage
  to `/data/data/com.dsemu.drastic/drastic_nano_libs/` (the
  classloader namespace permits this path; `/system/etc/...` is
  outside the namespace).
- `/storage/<UUID>/nds/*.nds` is `0770:root:media_rw`. Stock
  drastic UID has media_rw via zygote, but our shim spawned via
  setuid in ShimLauncher does NOT inherit groups, so direct
  open by path fails. Workaround: open as root in ShimLauncher
  before setuid, clear FD_CLOEXEC, pass fd via prop, shim opens
  `/proc/self/fd/N`.
- ART hangs on `ServiceManagerCppClient::waitForService(
  "SurfaceFlingerAIDL")` if SF is stopped. Discovered in v42
  test.
- `NanoMenu::binderDied` SIGKILL'd nano when SF was stopped.
  Fixed via static `sShimHandoffActive` flag that the handoff
  branch sets before issuing `ctl.stop surfaceflinger`.
- `service hwcomposer-3` is RK's vendor composer; it has
  `onrestart restart surfaceflinger` so order matters: stop
  HWC first, then SF, otherwise SF auto-restarts when HWC
  goes down separately.
- `ctl.stop` from bootanim domain works with permissive SELinux
  but may need explicit prop perms in enforcing.
- bionic linker rejects exec of `app_process` from a multi-
  threaded fork in a non-init context with `CANNOT LINK
  EXECUTABLE: library "libnativeloader.so" not found`. Fixed by
  having init spawn `gammaos_drastic_shim_launcher` (clean
  single-threaded init context) which then setuids + execs
  app_process.
- `NanoContext` ContextWrapper(null) needs many overrides:
  `getDataDir/Files/Cache/SharedPreferences` proxy to stock
  drastic's `/data/data/com.dsemu.drastic/`; `getDisplayId`
  -> 0; `getTheme` -> Resources.newTheme; `getResources/
  Assets` -> built from drastic.apk via reflection on
  `addAssetPath`; `checkCallingOrSelfPermission` -> GRANTED;
  `getMainLooper` -> Looper.getMainLooper; etc.
- `DraSticGlView$j.smali`'s `OrientationEventListener` ($a)
  inner class NPE'd on null SensorManager. Fixed by smali-
  patching `p0()` to skip the orientation block (sensor / orient
  / SensorManager fields all set to null in v9).
- Save/load confirm flags `f0/h.h0` and `f0/h.i0`: shim's
  replaced `DraSticGlView.m()`/`l()` calls
  `DraSticJNI.saveState/loadState(slot, false)` directly,
  bypassing the dialog Activities.
- `DraSticJNI.startGame` arg shape mirrored from
  `DrasticRunner::init`'s known-good values:
  `(romPath, 0, kDefaultConfigBits, 0, 0, 0)` where
  `kDefaultConfigBits = 0x10000000 | 0x10000000000 |
  0x20000000000 | 0x4000000000000` (_Threaded3D | _DisableEdge
  | _Hires3D | _m0).
- `persist.gammaos.nano.qr_rom` prop truncates long paths
  (PROP_VALUE_MAX = 92 bytes). Source of truth is
  `/data/system/nano_qr_rom.txt`. `getQrRomPath()` reads file
  first.

## APK history (drastic-nano-shim.apk evolution)

| Ver | MD5                                  | Key change |
|-----|--------------------------------------|------------|
| v1  | f9db13e800ad8eb48fb2144ec4a4550b     | Initial skeleton, debug-signed |
| v2  | 679a995a4b5618365c9893e9ccca1b49     | nativeLibraryDir from argv[1] + getDisplayId |
| v3  | 0260dc1787251827b2a34e60fd27f598     | getTheme override |
| v4  | 000d6eb49e828cfd8409f8a686155133     | Comprehensive Context overrides |
| v5  | a9bf6d10d879feba9467a85c0a0f3ab5     | Orientation skip + System.load preload |
| v6  | 506b36f45dd966d2a64cf37da63b9364     | Class.forName probes for diagnosis |
| v7  | d14e172250de918fc6425f71916ddfac     | Bundled patched libdrastic_*.so (incomplete fix) |
| v8  | 26e3a0d151b90a72cdc7798bcf222e23     | drastic_libdir prop reader |
| v9  | (interim, not separately tested)     | Longjmp patches in libdrastic_arm64.so |
| v10 | c282ff3352180db442ba2b6c9a69f220     | ROM fd inherit via /proc/self/fd/N |
| v11 | eaf023047835a106ad5ad874c53e4ae6     | Per-step main() logging |
| v12 | 8a40b3b4419417a32a8968f507a866c9     | fireSurfaceChanged + factory entry logs |
| v13 | f92daf7a8ab7f35ff9f7db668d5fb663     | libdrastic_nano_shim.so preload via System.load |
| v14 | bfafd28f81b63e8000510b3a931f5200     | 640x960 canvas (was 1280x720) |
| v15 | 1742f39211e83d9e438ee455934d4687     | EGL diagnostics in createFromAhb |
| v16 | 6459420f942ffae884d2cf1d37e18e3e     | FBO mode (pbuffer + EGLImage + smali patches) |
| v17 | 5f1b78d28d5a985d2f45de5688eba314     | SF cohabit: SurfaceComposerClient-backed Surface |

## Build/flash checklist for a new session

1. Claim device: `./device-lock.sh claim`
2. Verify staged files:
   - `packages/apps/GammaDrasticNanoShim/drastic-nano-shim.apk`
   - `packages/apps/GammaDrasticNanoShim/lib/arm64-v8a/libdrastic_*.so`
3. Build: `./build-queued.sh`
4. Copy: `cp out/home/build-output/lineage*.img /mnt/c/next/`
5. Flash:
   - `adb reboot fastboot && sleep 1.5`
   - `cd /mnt/c/next && /mnt/c/Windows/System32/cmd.exe /C "fastboot flash system <img> && fastboot reboot"`
6. Trigger nano-shim:
   - `adb shell setprop persist.gammaos.nano.qr_prepared 1`
   - `setprop persist.gammaos.nano.qr_core drastic`
   - `setprop persist.gammaos.nano.drastic_app 1`
   - `setprop persist.bootanim.skip_nano 0`
   - `reboot`
7. After boot, check logs:
   - `adb logcat -d 2>&1 | grep -E "NanoDraStic|NanoFactory|drastic_nano_shim|FATAL"`

## Current state on device (2026-04-15 13:30)

Image flashed: v43 (cohabit mode without SF/HWC stops). APK on
device is v16 (FBO mode, nominally - but in cohabit mode the
fallback paths route around the FBO logic). Need to flash with
APK v17 (SF-backed Surface) to validate cohabit baseline.

## Open questions

1. Can SurfaceComposerClient succeed from `gammaos_drastic_nano`
   SELinux domain without explicit allow rules? (Likely needs
   `binder_call(gammaos_drastic_nano, surfaceflinger)` + the
   service_manager find perm.)
2. How big is the SF binder dep surface in ART/EGL/libgui?
   (Required for Option B feasibility.)
3. Is RK's vendor HWC the only DRM master holder, or does
   anything else open `/dev/dri/card0` with SET_MASTER too?
   (Determines what else needs killing for Option B.)
4. Does Mali EGL respect `EGL_PLATFORM_GBM` for direct GBM
   rendering, bypassing ANW entirely? (Alternative path for
   Option B.)

---

## 2026-04-15 evening update -- Option B investigation + Option C plan

### Option B backtrace captured

User direction: **"I need to ensure that nano is driving the DRM"**
-- Option A cohabit is OFF the long-term roadmap.

Re-enabled SF/HWC stops in NanoMenu handoff, let shim hang,
captured native backtrace via `debuggerd <shim_pid>`:

```
#06 libgui.so   waitForService<gui::ISurfaceComposer>("SurfaceFlingerAIDL")
#07 libgui.so   ComposerServiceAIDL::connectLocked()
#08 libgui.so   ComposerServiceAIDL::ComposerServiceAIDL()
#09 libgui.so   Singleton<ComposerServiceAIDL>::getInstance()
#10 libgui.so   ComposerServiceAIDL::getComposerService()
#11 libgui.so   SurfaceComposerClient::onFirstRef()
#12 libandroid_runtime.so   nativeCreate()  <-- JNI
#13 .oat        art_jni_trampoline
#14 .oat        android.view.SurfaceView.<init>
...
#19 shim.apk    com.dsemu.drastic.DraSticGlView.<init>
#21 shim.apk    gammaos.drastic.NanoDraSticEntry.main
```

**Single call site**. `SurfaceView.<init>` (inherited by
`GLSurfaceView`, drastic's `DraSticGlView` extends that) creates a
`SurfaceComposerClient` in its ctor via JNI `nativeCreate`. SCC's
`onFirstRef()` calls `ComposerServiceAIDL::connectLocked()` which
does a blocking `waitForService("SurfaceFlingerAIDL")`. Once SF
is dead, this never returns.

### Option B feasibility

Not viable without framework surgery. The call site is inside
`libandroid_runtime.so` / `libgui.so` (framework native code,
outside our shim APK). We can't smali-patch or LD_PRELOAD into
`android_runtime_SurfaceView_nativeCreate` cleanly.

Ruled out sub-options:
- **Smali-patch SurfaceView class hierarchy**: make DraSticGlView
  extend View instead of GLSurfaceView. Huge rewrite -- would need
  to reimplement GLSurfaceView semantics (EGL thread, ctx
  lifecycle, render modes). Not worth it.
- **LD_PRELOAD wrapper for waitForService**: possible but fragile.
  SCC then gets null binder -> graceful path exists
  (`mStatus == NO_INIT`), but any subsequent SCC use would crash.

### Plan: Option C -- SurfaceFlingerAIDL stub binary

Replace the real SF with a tiny binder that satisfies
`waitForService` so the shim's `SurfaceView.<init>` proceeds past
SCC construction. No rendering methods are actually called
because drastic's render path goes through our custom ANW / FBO
path via NanoBridge.

Architecture:

```
NanoMenu.cpp handoff branch:
  ctl.stop hwcomposer-3        # free DRM master
  ctl.stop surfaceflinger
  ctl.start gammaos_sf_stub    # register empty binder as SurfaceFlingerAIDL
  (nano drmSetMaster + DRM ring init -- TODO)
  ctl.start gammaos_drastic_shim

gammaos_sf_stub process:
  - ProcessState::startThreadPool
  - Construct BBinder subclass, override getInterfaceDescriptor to
    "android.gui.ISurfaceComposer"
  - Register via IServiceManager::addService("SurfaceFlingerAIDL",
    stub, false, DUMP_FLAG_PRIORITY_DEFAULT)
  - Also register "SurfaceFlinger" (old descriptor) for safety
  - joinThreadPool

Shim side (SurfaceView.<init>):
  - waitForService("SurfaceFlingerAIDL") -> returns our stub
  - asInterface<gui::ISurfaceComposer>(stub) -> descriptor matches
    ("android.gui.ISurfaceComposer"), wraps in proxy
  - sf->createConnection(&conn) -> BBinder::onTransact returns
    UNKNOWN_TRANSACTION for the unknown code
  - binder::Status is not-ok, mStatus stays NO_INIT
  - SurfaceView ctor completes (null/NO_INIT SCC is acceptable
    state; subsequent render path doesn't touch it)
```

### Expert acknowledgement (drastic-android-mod session)

From expert reply at `nano-startup-timing-answers.md`:

> Backtrace is conclusive: `SurfaceView.<init>` transitively
> constructs `SurfaceComposerClient`, which blocks on
> `waitForService("SurfaceFlingerAIDL")`.
>
> APK v17's `NanoFactory.tryCreateSfSurface` will call
> `new SurfaceControl.Builder().setName/setBufferSize/setFormat.
> build()`. Against the stub, Builder.build() either returns null
> or throws. My code catches both via try/throwable and falls back
> to pbuffer. Shim runs without hanging, can confirm end-to-end.
>
> Once SF stub works for the SurfaceView ctor hurdle, to get
> actual rendering flip back to FBO mode (v16's NanoFboManager +
> $j.onDrawFrame smali patch). Offer build-time
> `persist.gammaos.nano.drastic_gfx_mode` = `sf|fbo|auto`.

Expert is waiting on stub-ready signal to retest v17.

### Implementation steps (in progress)

1. `frameworks/base/cmds/gammaos-nano/SfAidlStub.cpp` -- new
   standalone binary. ~50 lines.
2. Add `cc_binary gammaos_sf_stub` to `Android.bp`.
3. Add service decl to `gammaos-nano.rc`:
   ```
   service gammaos_sf_stub /system/bin/gammaos_sf_stub
       class nano_sf_stub
       user system
       group graphics
       disabled
       oneshot
       seclabel u:r:gammaos_sf_stub:s0
   ```
4. Add SELinux domain `gammaos_sf_stub` in
   `system/sepolicy/private/gammaos_sf_stub.te`. Needs:
   - `binder_use(gammaos_sf_stub)`
   - `add_service(gammaos_sf_stub, surfaceflinger_service)`
   - permissive during bringup
   Mirror to prebuilts/api/{34.0,202404}/private/.
5. Update `NanoMenu.cpp` handoff: between `ctl.stop
   surfaceflinger` and `ctl.start gammaos_drastic_shim`, insert
   `ctl.start gammaos_sf_stub` and wait briefly for the service
   to register (poll `service check SurfaceFlingerAIDL` or
   ~200ms sleep).
6. Also re-enable SF/HWC stops in NanoMenu handoff (currently
   left disabled pending this work).

### After stub works -- FBO mode re-activation

Expert's v18 will reinstate FBO mode when stub is detected
(persist.gammaos.nano.drastic_gfx_mode=fbo). That path:

- `NanoFactory` grabs ANW from
  `NanoBridge.getCurrentAhbNativeWindow()`
- Creates EGL context on pbuffer (for context currency)
- `NanoFboManager.setupFbosForRing(anw)` creates FBOs bound to
  AHB-backed EGLImages for each ring slot
- Drastic's `glBindFramebuffer(0)` in `$j.onDrawFrame` smali-
  patched to `glBindFramebuffer(NanoFboManager.getCurrentFboId())`
- Drastic renders into ring slot -> NanoBridge signals nano ->
  nano drmFlipRingSlot -> panel

### Anti-patterns not to revisit

- Don't try to patch SurfaceView itself -- framework code.
- Don't try Option A cohabit as final answer (user wants DRM in
  nano).
- Don't waste cycles on EGL_BAD_NATIVE_WINDOW tuning with
  custom ANW against Mali's standard EGL window surface path.
  The FBO approach sidesteps Mali's ANW validation entirely by
  using `eglCreateImage(AHB)` + framebuffer objects.

### Current state (2026-04-15 late afternoon)

- Docs updated (this file).
- Memory `drastic_nano_shim.md` to be updated next.
- `gammaos_sf_stub` NOT YET IMPLEMENTED -- next step.
- Device NOT currently claimed.
- Last flashed image: v43 (cohabit, SF alive). Needs reflash
  after stub work lands.
- APK on device: v17 (SurfaceComposerClient backed Surface).
  Expert will ship v18 with FBO-mode reactivation when we signal
  stub ready.

### Collaboration status

- Expert is idle, waiting on our stub implementation.
- Our next exchange: flash image with stub, run shim, capture
  logs. Expert will adjust NanoFactory based on what happens.
