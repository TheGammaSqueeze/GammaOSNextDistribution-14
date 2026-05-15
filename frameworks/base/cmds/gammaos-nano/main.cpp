/*
 * Copyright (C) 2026 GammaOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "GammaOSNano"

#include <stdint.h>
#include <inttypes.h>
#include <string.h>
#include <dlfcn.h>
#include <dirent.h>
#include <fcntl.h>
#include <jni.h>
#include <pthread.h>
#include <sys/ioctl.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <drm.h>
#include <drm_mode.h>

#include <atomic>
#include <string>
#include <thread>

#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>
#include <binder/IServiceManager.h>
#include <cutils/properties.h>
#include <sys/resource.h>
#include <utils/Log.h>
#include <utils/SystemClock.h>

#include "DrasticRunner.h"
#include "FakeJNI.h"
#include "NanoMenu.h"
#include "NanoMenuDrm.h"

using namespace android;

// Early DRM master fd, grabbed in main() on non-Qualcomm SoCs before
// HWC starts. On Qualcomm, this stays -1 (grab deferred to readyToRun).
int gEarlyDrmFd = -1;

// waitForSurfaceFlinger removed: readyToRun() handles SF wait internally
// when the SF path is needed (restart case). On the DRM boot path, SF is
// not required at all.

// Drastic in-process quick-resume init.
//
// Fires on either of:
//   (1) persist.gammaos.nano.drastic_smoke=1     -- debug smoke test
//       (also accepts wait_audio / wait_system / wait_boot / ... for
//       service-gate A/B experiments)
//   (2) persist.gammaos.nano.qr_prepared=1 AND
//       persist.gammaos.nano.qr_core="drastic"   -- real QR path
//       primed by launchXmbGame() when the user opens a Nintendo DS
//       game from NanoMenu's XMB.
//
// In either case the function runs DrasticRunner::init() against the
// DE cache at /data/system/nano_cache/drastic/, which exercises:
//   Phase 1 (FakeJNI JNI_OnLoad)
//   Phase 3 (onInit + applyConfig + startGame)
//
// On success, drastic's main DS CPU thread and rasterizer pool will
// be running in the background when this returns. We deliberately
// do NOT tear them down here -- NanoMenu continues to its normal
// boot path afterward, and the drastic threads just hum along until
// the process exits at framework handoff.
//
// The QR path deliberately skips the wait_* service-gate modes: we
// want drastic running ASAP (before system_server is up) so the
// first frame lands at ~T+1.5s, not T+5s.
//
// Wait-for-service helper. Polls an init.svc.<name> property every
// 25 ms until it reports "running", up to the given timeout. Used by
// the smoke test's service-gate mode so we can A/B test whether the
// boot-time drastic stall is caused by waiting for a specific
// service to be ready.
// Apply a one-shot binary patch to libdrastic_arm64.so in the
// nano cache that short-circuits drastic's initialize_audio
// function to return immediately.
//
// WHY
// Drastic's initialize_audio (libdrastic_arm64.so:0x1d760) calls
// libOpenSLES::slCreateEngine, which internally does a binder
// waitForService("media.audio_flinger"). On a cold boot, that wait
// blocks for ~15 seconds while audioserver finishes HAL enumeration
// and registers its binder service. Drastic's DS CPU emulation is
// gated on initialize_audio returning, so the user-visible "first
// drastic frame" lands at T+15s cold.
//
// The other-agent disasm showed:
//   1. initialize_audio has NO graceful failure path -- if
//      slCreateEngine fails, drastic NULL-derefs the engine pointer
//      at 0x1d81c.
//   2. The caller at 0x7304c does NOT check initialize_audio's return
//      value, so making the function return immediately is safe.
//   3. Drastic's per-frame audio mix loop is gated on the
//      _SoundEnabled config bit, so the NULL audio objects at
//      master+0x10..0x28 are never touched after init when sound is
//      disabled.
//
// WHAT
// One 4-byte patch: replace the function entry with `ret`.
//   Offset: 0x1d760 (function entry of initialize_audio)
//   Original: ff 43 03 d1  (sub sp, sp, #0xd0)
//   Patched:  c0 03 5f d6  (ret)
//
// The patch is idempotent: if we've already patched the cached
// file, the first 4 bytes are already the ret sequence and we skip
// the write. We verify the pre-patch bytes so we don't corrupt a
// future drastic build with a different function layout.
//
// NOTE
// This patches the CACHED copy at /data/system/nano_cache/drastic/
// libdrastic_arm64.so, not the original under /data/app/. The
// original APK is untouched.
static void patchDrasticAudioInit() {
    const char* path =
            "/data/system/nano_cache/drastic/libdrastic_arm64.so";
    int fd = open(path, O_RDWR);
    if (fd < 0) {
        ALOGW("drastic patch: cannot open %s: %s",
              path, strerror(errno));
        return;
    }
    const off_t kOffset = 0x1d760;
    const uint32_t kOriginal = 0xd10343ff;  // sub sp, sp, #0xd0
    const uint32_t kRet      = 0xd65f03c0;  // ret
    uint32_t cur = 0;
    if (pread(fd, &cur, 4, kOffset) != 4) {
        ALOGW("drastic patch: pread at 0x%lx failed: %s",
              (long)kOffset, strerror(errno));
        close(fd);
        return;
    }
    if (cur == kRet) {
        ALOGI("drastic patch: libdrastic already patched "
              "(initialize_audio=ret)");
        close(fd);
        return;
    }
    if (cur != kOriginal) {
        ALOGW("drastic patch: unexpected bytes at 0x%lx: 0x%08x "
              "(expected 0x%08x). Library version may have "
              "changed -- refusing to patch.",
              (long)kOffset, cur, kOriginal);
        close(fd);
        return;
    }
    if (pwrite(fd, &kRet, 4, kOffset) != 4) {
        ALOGE("drastic patch: pwrite at 0x%lx failed: %s",
              (long)kOffset, strerror(errno));
        close(fd);
        return;
    }
    // Sync to disk so the next mmap+exec sees the patched bytes.
    // Without this, bionic's mmap of the file during dlopen could
    // still see the pre-patched bytes if the pagecache hasn't
    // flushed back to the underlying file.
    fsync(fd);
    close(fd);
    ALOGW("drastic patch: libdrastic_arm64.so initialize_audio "
          "short-circuited at 0x%lx (was 0x%08x -> 0x%08x)",
          (long)kOffset, kOriginal, kRet);
}

static bool waitForService(const char* serviceName, int timeoutMs) {
    char key[PROPERTY_KEY_MAX] = {};
    snprintf(key, sizeof(key), "init.svc.%s", serviceName);
    char val[PROPERTY_VALUE_MAX] = {};
    int elapsed = 0;
    while (elapsed < timeoutMs) {
        property_get(key, val, "");
        if (strcmp(val, "running") == 0) {
            ALOGI("drastic smoke: %s running at waited %dms",
                  serviceName, elapsed);
            return true;
        }
        usleep(25 * 1000);
        elapsed += 25;
    }
    ALOGW("drastic smoke: timed out waiting for %s (%dms)",
          serviceName, timeoutMs);
    return false;
}

static void runDrasticInitIfNeeded() {
    // Persist props can race with our early start. gammaos-nano begins
    // around T+7s on the Brick while persistent_properties loads at
    // ~T+9-10s. Without waiting here, qr_prepared reads as "0" and
    // DrasticRunner never gets created, even though NanoMenu's
    // readyToRun later correctly detects the fast-path.
    {
        char ready[PROPERTY_VALUE_MAX] = {};
        property_get("ro.persistent_properties.ready", ready, "");
        if (strcmp(ready, "true") != 0) {
            int waited = 0;
            for (int i = 0; i < 200; i++) {
                usleep(10000);
                waited += 10;
                property_get("ro.persistent_properties.ready",
                             ready, "");
                if (!strcmp(ready, "true")) break;
            }
            ALOGI("runDrasticInitIfNeeded: persist props ready "
                  "after %d ms", waited);
        }
    }

    // Read both triggers up front so we know which mode we're in.
    char smoke[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.drastic_smoke", smoke, "0");
    bool smokeActive = !(smoke[0] == 0 || strcmp(smoke, "0") == 0);

    char qp[PROPERTY_VALUE_MAX] = {};
    char qc[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.qr_prepared", qp, "0");
    property_get("persist.gammaos.nano.qr_core", qc, "");
    bool qrActive = (strcmp(qp, "1") == 0) && (strcmp(qc, "drastic") == 0);

    if (!smokeActive && !qrActive) return;

    // Tag used in all subsequent logs. Smoke wins when both are set
    // so A/B tests stay reproducible.
    const char* tag = smokeActive ? "drastic smoke" : "drastic QR init";

    if (smokeActive) {
        // smoke values are one of:
        //   "1"            -> run immediately (original behavior)
        //   "wait_audio"   -> wait for audioserver, then run
        //   "wait_system"  -> wait for system_server, then run
        //   "wait_vendor"  -> wait for vendor.hwcomposer-2-1 or similar
        //   "wait_boot"    -> wait for sys.boot_completed=1
        //
        // Used to A/B test whether the boot-time 15s stall is caused
        // by drastic waiting for a specific service to be ready. The
        // QR path deliberately skips these gates -- we want drastic
        // up ASAP, not after audioserver or system_server.
        if (strcmp(smoke, "wait_audio") == 0) {
            ALOGI("%s: waiting for audioserver...", tag);
            waitForService("audioserver", 30000);
        } else if (strcmp(smoke, "wait_system") == 0) {
            ALOGI("%s: waiting for system_server...", tag);
            waitForService("system_server", 60000);
        } else if (strcmp(smoke, "wait_boot") == 0) {
            ALOGI("%s: waiting for sys.boot_completed...", tag);
            char bc[PROPERTY_VALUE_MAX] = {};
            int elapsed = 0;
            while (elapsed < 90000) {
                property_get("sys.boot_completed", bc, "");
                if (strcmp(bc, "1") == 0) break;
                usleep(100 * 1000);
                elapsed += 100;
            }
            ALOGI("%s: boot_completed after %dms", tag, elapsed);
        } else if (strcmp(smoke, "wait_sf") == 0) {
            ALOGI("%s: waiting for surfaceflinger...", tag);
            waitForService("surfaceflinger", 30000);
        } else if (strcmp(smoke, "wait_audio_hal") == 0) {
            ALOGI("%s: waiting for vendor.audio-hal...", tag);
            waitForService("vendor.audio-hal", 30000);
        } else if (strcmp(smoke, "wait_media") == 0) {
            ALOGI("%s: waiting for media stack...", tag);
            waitForService("vendor.audio-hal", 30000);
            waitForService("audioserver", 30000);
            waitForService("mediaserver", 30000);
            waitForService("media", 30000);
        } else if (strcmp(smoke, "wait_zygote_secondary") == 0) {
            // zygote_secondary = the 64-bit zygote on split-zygote devices,
            // or the only zygote on pure 64-bit. Often the last init svc
            // to come up before ActivityManager starts.
            ALOGI("%s: waiting for zygote_secondary...", tag);
            waitForService("zygote_secondary", 60000);
        }
    }

    ALOGI("%s: starting (smoke=%d qr=%d)", tag,
          smokeActive ? 1 : 0, qrActive ? 1 : 0);

    const std::string cacheDir = "/data/system/nano_cache/drastic";

    // Check if drastic nano mode is active. When enabled, we load the
    // ROM from its real storage path instead of the cache copy, and
    // wait for external storage to mount if needed.
    char dnProp[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.drastic_nano", dnProp, "0");
    bool drasticNano = (dnProp[0] == '1');

    std::string romPath;

    if (drasticNano) {
        // Drastic nano: read real ROM path from the nano drastic file.
        {
            int fd = open("/data/system/nano_drastic_nano_rom.txt",
                          O_RDONLY);
            if (fd >= 0) {
                char buf[4096];
                ssize_t n = read(fd, buf, sizeof(buf));
                close(fd);
                if (n > 0) {
                    romPath.assign(buf, (size_t)n);
                    while (!romPath.empty() &&
                           (romPath.back() == '\n' ||
                            romPath.back() == '\r' ||
                            romPath.back() == ' ')) {
                        romPath.pop_back();
                    }
                }
            }
        }
        if (romPath.empty()) {
            ALOGW("%s: drastic nano active but no ROM path in "
                  "nano_drastic_nano_rom.txt -- skipping", tag);
            return;
        }
        // Wait for external storage to mount. SD cards can take a few
        // seconds after boot. We check if the ROM file is accessible,
        // polling up to 15s. Internal storage (/data/) is always
        // available, so this only blocks for external paths.
        if (access(romPath.c_str(), R_OK) != 0) {
            ALOGI("%s: drastic nano ROM not accessible yet, "
                  "waiting for storage mount...", tag);
            int waitMs = 0;
            while (waitMs < 15000) {
                usleep(100 * 1000);
                waitMs += 100;
                if (access(romPath.c_str(), R_OK) == 0) break;
            }
            if (access(romPath.c_str(), R_OK) != 0) {
                // Real path not accessible (e.g. FUSE not mounted in
                // this mount namespace during mid-session restart).
                // Fall back to the cached ROM copy which populate_drastic
                // already placed in the cache dir.
                ALOGW("%s: drastic nano ROM not accessible after 15s: "
                      "%s -- trying cache fallback",
                      tag, romPath.c_str());
                std::string romDir = cacheDir + "/rom";
                DIR* d = opendir(romDir.c_str());
                if (d) {
                    struct dirent* e;
                    while ((e = readdir(d)) != nullptr) {
                        std::string name(e->d_name);
                        if (name == "." || name == "..") continue;
                        if (name.size() >= 4 &&
                            strcasecmp(name.c_str() + name.size() - 4,
                                       ".nds") == 0) {
                            romPath = romDir + "/" + name;
                            break;
                        }
                    }
                    closedir(d);
                }
                if (access(romPath.c_str(), R_OK) != 0) {
                    ALOGW("%s: cache fallback also failed -- skipping",
                          tag);
                    return;
                }
                ALOGI("%s: using cached ROM: %s", tag,
                      romPath.c_str());
            } else {
                ALOGI("%s: drastic nano ROM accessible after %dms",
                      tag, waitMs);
            }
        }
        ALOGI("%s: drastic nano rom=%s", tag, romPath.c_str());
    } else {
        // Normal QR/smoke: discover ROM from cache directory.
        auto hasExt = [](const std::string& name, const char* ext) {
            size_t elen = strlen(ext);
            return name.size() >= elen &&
                   strcasecmp(name.c_str() + name.size() - elen,
                              ext) == 0;
        };
        std::string romDir = cacheDir + "/rom";
        DIR* d = opendir(romDir.c_str());
        if (d) {
            struct dirent* e;
            while ((e = readdir(d)) != nullptr) {
                std::string name(e->d_name);
                if (name == "." || name == "..") continue;
                if (hasExt(name, ".nds") || hasExt(name, ".zip") ||
                    hasExt(name, ".7z")  || hasExt(name, ".rar")) {
                    romPath = romDir + "/" + name;
                    break;
                }
            }
            closedir(d);
        }
        if (romPath.empty()) {
            ALOGW("%s: no ROM in %s/rom -- skipping",
                  tag, cacheDir.c_str());
            return;
        }
        ALOGI("%s: rom=%s", tag, romPath.c_str());
    }

    // Heap-allocate the runner so the background threads drastic
    // spawns during startGame keep their state alive after this
    // function returns. We intentionally never delete it.
    auto* runner = new android::DrasticRunner();
    bool ok = runner->init(cacheDir, romPath);
    ALOGI("%s: DrasticRunner::init returned %s", tag,
          ok ? "true" : "false");
    if (!ok) {
        ALOGW("%s: init failed -- see earlier logs", tag);
        // Don't delete the runner even on failure -- we don't know
        // which stage failed, and if drastic already spawned threads
        // before the failure, destroying the runner would race them.
    }

    ALOGI("%s: done", tag);
}

// On-demand drastic QR preview trigger for interactive debugging.
//
// Usage from adb:
//
//   # Attach strace to gammaos-nano (via run-as or root shell)
//   adb shell 'strace -f -p $(pidof gammaos-nano) -o /sdcard/drastic.strace' &
//
//   # Start a fresh drastic init (first-frame race reproduced)
//   adb shell setprop sys.gammaos.nano.drastic_qr_test start
//
//   # ... wait for first frame, capture whatever you want ...
//
//   # Tear down so strace output stops growing
//   adb shell setprop sys.gammaos.nano.drastic_qr_test stop
//
// The watcher runs a dedicated thread that polls the trigger
// property ~every 250 ms. Transitions are edge-triggered: setting
// the same value twice is a no-op.
//
// One start/stop cycle per gammaos-nano process is safe. A second
// start after stop is BEST-EFFORT because libdrastic's global
// rasterizer state + detached DS-CPU thread may still be live after
// pauseSystem/quitSystem. If you need a clean slate between runs,
// bounce the service:
//   adb shell 'stop gammaos-nano; start gammaos-nano'
//
// The watcher is independent from the boot-time smoke test: the
// smoke test (persist.gammaos.nano.drastic_smoke=1) runs once at
// process entry before NanoMenu spins up. The on-demand watcher
// also reads from /data/system/nano_cache/drastic/ so the cache
// must be populated first. If drastic_smoke already ran, the
// watcher's "start" command will create a SECOND runner --
// harmless but wastes memory; disable drastic_smoke for clean
// on-demand testing.
static void startDrasticQrTestWatcher() {
    std::thread([]() {
        pthread_setname_np(pthread_self(), "drastic-qr-watch");

        // Owned outside any shared pointer on purpose: we don't want
        // the watcher and NanoMenu::getInstance() stepping on the same
        // object. If the user also ran the smoke test, there will be
        // two runners alive at once; logs will make the difference
        // obvious (ALOGI lines tagged with different instance addrs).
        android::DrasticRunner* runner = nullptr;
        char last[PROPERTY_VALUE_MAX] = {};

        ALOGI("drastic_qr_test: watcher thread started");

        while (true) {
            char val[PROPERTY_VALUE_MAX] = {};
            property_get("sys.gammaos.nano.drastic_qr_test", val, "");

            if (strcmp(val, last) != 0) {
                ALOGW("drastic_qr_test: transition \"%s\" -> \"%s\"",
                      last, val);
                strncpy(last, val, sizeof(last) - 1);
                last[sizeof(last) - 1] = 0;

                if (strcmp(val, "start") == 0) {
                    if (runner) {
                        ALOGW("drastic_qr_test: runner already alive, "
                              "ignoring start");
                    } else {
                        const std::string cacheDir =
                                "/data/system/nano_cache/drastic";
                        std::string romPath;
                        {
                            std::string romDir = cacheDir + "/rom";
                            DIR* d = opendir(romDir.c_str());
                            if (d) {
                                struct dirent* e;
                                auto endsWith = [](const std::string& s,
                                                   const char* ext) {
                                    size_t elen = strlen(ext);
                                    return s.size() >= elen &&
                                           strcasecmp(s.c_str() + s.size() - elen,
                                                      ext) == 0;
                                };
                                while ((e = readdir(d)) != nullptr) {
                                    std::string name(e->d_name);
                                    if (name == "." || name == "..") continue;
                                    // Accept drastic-loadable containers:
                                    // raw .nds and .zip/.7z/.rar archives
                                    // drastic unpacks internally. Without
                                    // this the scanner only saw .nds and
                                    // archive ROMs silently dropped QR.
                                    if (endsWith(name, ".nds") ||
                                        endsWith(name, ".zip") ||
                                        endsWith(name, ".7z")  ||
                                        endsWith(name, ".rar")) {
                                        romPath = romDir + "/" + name;
                                        break;
                                    }
                                }
                                closedir(d);
                            }
                        }
                        if (romPath.empty()) {
                            ALOGW("drastic_qr_test: no ROM in %s/rom "
                                  "-- ignoring start", cacheDir.c_str());
                        } else {
                            ALOGW("drastic_qr_test: START init, "
                                  "rom=%s", romPath.c_str());
                            runner = new android::DrasticRunner();
                            bool ok = runner->init(cacheDir, romPath);
                            ALOGW("drastic_qr_test: init returned %s",
                                  ok ? "true" : "false");
                            if (!ok) {
                                delete runner;
                                runner = nullptr;
                            }
                        }
                    }
                } else if (strcmp(val, "stop") == 0) {
                    if (!runner) {
                        ALOGW("drastic_qr_test: no runner to stop");
                    } else {
                        ALOGW("drastic_qr_test: STOP shutdown");
                        runner->shutdown();
                        // Delay delete by a beat so the detached
                        // pixel-pull thread observes the running
                        // flag flip before we free the object it
                        // still has a pointer to.
                        usleep(50 * 1000);
                        delete runner;
                        runner = nullptr;
                        ALOGW("drastic_qr_test: shutdown done");
                    }
                }
                // "reset" = clear the property so the next START is
                // ready without having to go through STOP first. Useful
                // when the user just wants to re-arm the trigger.
                if (strcmp(val, "reset") == 0) {
                    property_set("sys.gammaos.nano.drastic_qr_test", "");
                    last[0] = 0;
                }
            }

            usleep(250 * 1000);
        }
    }).detach();
}

// Detached preload thread that warms the drastic dependency chain.
//
// Motivation (strace findings 2026-04-11): when gammaos-nano triggers
// a drastic QR init for the first time, the dynamic linker and drastic
// internals spend several seconds cold-loading libraries:
//
//   A) libdrastic_arm64.so DT_NEEDED: libOpenSLES. libOpenSLES transitively
//      drags in the entire Android media subsystem via libwilhelm
//      (libmedia, libstagefright, libcamera_client, libandroid_runtime,
//      ~200 libs via libandroid_runtime's tree).
//
//   B) libdrastic's startGame lazily dlopens, at RUNTIME, a second set
//      of libraries we can't see from libdrastic's DT_NEEDED:
//        - libandroid.so (sensor / ALooper / NDK surface)
//        - libxml2.so (game_database.xml parsing)
//        - android.hardware.power@1.1.so (HIDL power hint)
//        - android.hardware.power-V5-ndk.so (NDK power hint)
//      These show up INSIDE the startGame thread's syscall trace as
//      openat/mmap/mprotect chains. They are NOT covered by a static
//      preload of libdrastic_arm64.so because they're dlopen'd at
//      runtime when startGame actually runs.
//
// The fix is to do ALL the dlopens in the background at gammaos-nano
// startup, so the linker work + page cache warming for both A and B
// overlap with NanoMenu shader compile / DRM splash setup. By the time
// the user triggers drastic QR, every library drastic will need is
// warm in the page cache and the dlopen from DrasticRunner + drastic's
// internal dlopens resolve in milliseconds.
//
// Strace before the fix: ~10.9s from trigger to first frame.
// Strace after the fix (OpenSLES + libdrastic only): ~767ms at boot+1m.
// At boot time itself: ~15.8s (dominated by CPU contention from system
// services, NOT dlopen). Adding the secondary preload should close the
// boot-time gap further by warming the page cache for the libs
// drastic's startGame needs during the contention window.
//
// We dlopen everything with RTLD_NOW | RTLD_GLOBAL so all transitive
// deps are resolved immediately AND stay resident in the global
// namespace so libdrastic's later dlopen sees them without re-resolving.
//
// If libdrastic itself isn't in the cache, we still preload the
// Android-side libs because they're useful for any future cached run.
static void startDrasticLibPreloadThread() {
    std::thread([]() {
        pthread_setname_np(pthread_self(), "drastic-preload");

        int64_t t0 = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
        ALOGI("drastic preload: starting at T+%lldms", t0);

        auto warmLib = [&](const char* name) {
            int64_t s = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
            void* h = dlopen(name, RTLD_NOW | RTLD_GLOBAL);
            int64_t e = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
            if (h) {
                ALOGI("drastic preload: %s warm (+%lldms)", name, e - s);
            } else {
                ALOGW("drastic preload: %s failed: %s", name, dlerror());
            }
        };

        // Gate the heavy preloads on the drastic cache being populated.
        // The libOpenSLES.so warm-up pulls in ~200 transitive libs from
        // the media stack and takes ~5 s on a cold f2fs page cache
        // (TrimUI Brick / Allwinner A133 + slow eMMC). Because dlopen
        // takes bionic's linker lock, that 5 s blocks NanoMenu's
        // eglChooseConfig in readyToRun() (libEGL needs the linker to
        // load libEGL_POWERVR_ROGUE.so), which delays first paint by the
        // same 5 s. There is no point paying that cost when the cache
        // isn't populated, because the user can't launch drastic via QR
        // anyway - the cache is what feeds DrasticRunner. Once they
        // launch drastic the first time and populate the cache, the
        // next boot will preload everything.
        //
        // Note: drastic itself never calls slCreateEngine (patched out
        // by patchDrasticAudioInit), so the libOpenSLES preload is pure
        // page cache warmup with zero semantic effect on drastic.
        const char* cpuPath =
                "/data/system/nano_cache/drastic/libdrastic_cpu.so";
        const char* arm64Path =
                "/data/system/nano_cache/drastic/libdrastic_arm64.so";
        bool cacheReady = (access(arm64Path, R_OK) == 0);
        if (!cacheReady) {
            ALOGI("drastic preload: cache not populated, skipping "
                  "libOpenSLES + runner warmup to keep linker lock off "
                  "the NanoMenu EGL init path");
            int64_t t_end = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
            ALOGI("drastic preload: total %lldms", t_end - t0);
            return;
        }

        // (A) libdrastic's static DT_NEEDED heavy dep. libOpenSLES
        // pulls in libwilhelm → libmedia → ~200 lib media stack
        // transitively. Warming it in background hides the cold
        // page cache cost from the user-visible QR window.
        warmLib("libOpenSLES.so");

        // (B) libdrastic's runtime-dlopen'd deps, observed via strace
        // inside the startGame thread. Each of these is a system lib
        // that drastic opens LATER (not at JNI_OnLoad time), so our
        // static preload of libdrastic_arm64.so doesn't cover them.
        // They're cheap individually (<10ms each warm) but hit the
        // critical path inside drastic's startGame when cold.
        warmLib("libandroid.so");
        warmLib("libxml2.so");
        warmLib("android.hardware.power@1.1.so");
        warmLib("android.hardware.power-V5-ndk.so");

        // libdrastic_{cpu,arm64}.so: cache is populated (we early-
        // returned above when it wasn't), so warm them too so the
        // user-visible dlopen in DrasticRunner::init is a pure cache
        // hit.
        warmLib(cpuPath);
        warmLib(arm64Path);

        // Prefetch drastic's cold-read data files into the page cache.
        // At boot time these are on f2fs that hasn't been touched yet,
        // so drastic's first read blocks on I/O. We need the data
        // ACTUALLY in the page cache when drastic reads it — not just
        // "the kernel agrees to read it soon."
        //
        // readahead(2) and posix_fadvise(WILLNEED) are both async on
        // Linux: they schedule IO and return. For boot-time prefetch
        // this is worthless because drastic's sequential read happens
        // ~1ms later, before the kernel has actually pulled the
        // pages. The only reliable way to guarantee the file is hot
        // is to actually read it into a throwaway buffer, which
        // forces synchronous IO and populates the page cache.
        //
        // game_database.xml is ~1.6 MB and is read+parsed linearly by
        // drastic during startGame. BIOS/firmware files are small
        // (~20 KB total) but hit the critical path.
        //
        // For the ROM file we only warm the first 16 MB. A full read
        // of a 512 MB ROM would waste bandwidth, and drastic only
        // needs the header + ARM9/ARM7 binaries + a few data sections
        // to boot. 16 MB covers all of that for every DS title.
        auto warmFile = [](const char* path, off_t len) {
            int fd = open(path, O_RDONLY | O_CLOEXEC);
            if (fd < 0) {
                ALOGI("drastic preload: warm %s: skip (no file)",
                      path);
                return;
            }
            off_t size = lseek(fd, 0, SEEK_END);
            if (size < 0) size = 0;
            lseek(fd, 0, SEEK_SET);
            off_t want = (len == 0 || len > size) ? size : len;
            int64_t s = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
            // Force synchronous read into a throwaway buffer. We
            // don't care about the data, only that the kernel page
            // cache is populated afterwards.
            static constexpr size_t kChunk = 64 * 1024;
            char buf[kChunk];
            off_t total = 0;
            while (total < want) {
                off_t remaining = want - total;
                size_t req = (remaining < (off_t)kChunk)
                        ? (size_t)remaining : kChunk;
                ssize_t got = read(fd, buf, req);
                if (got <= 0) break;
                total += got;
            }
            int64_t e = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
            ALOGI("drastic preload: warm %s -> %lld / %lld bytes "
                  "(+%lldms)", path, (long long)total, (long long)want,
                  e - s);
            close(fd);
        };
        auto fadviseWillneed = warmFile;  // alias, kept for clarity

        if (cacheReady) {
            fadviseWillneed(
                "/data/system/nano_cache/drastic/game_database.xml", 0);
            fadviseWillneed(
                "/data/system/nano_cache/drastic/system/drastic_bios_arm9.bin", 0);
            fadviseWillneed(
                "/data/system/nano_cache/drastic/system/drastic_bios_arm7.bin", 0);
            fadviseWillneed(
                "/data/system/nano_cache/drastic/system/nds_firmware_modified.bin", 0);

            // ROM: prefetch first 16 MB. Scan the rom/ subdir for the
            // single staged .nds (populate_drastic only keeps one).
            const char* romDir =
                    "/data/system/nano_cache/drastic/rom";
            DIR* d = opendir(romDir);
            if (d) {
                struct dirent* e;
                while ((e = readdir(d)) != nullptr) {
                    std::string name(e->d_name);
                    if (name == "." || name == "..") continue;
                    if (name.size() >= 4 &&
                        name.compare(name.size() - 4, 4, ".nds") == 0) {
                        std::string p =
                                std::string(romDir) + "/" + name;
                        fadviseWillneed(p.c_str(), 16 * 1024 * 1024);
                        break;
                    }
                }
                closedir(d);
            }

            // GammaOS Nano: warm the drastic APK + dex + native libs.
            // DraSticActivity.onCreate took 8.2s on the cold-boot QR path,
            // largely due to f2fs I/O for /data/app/.../base.apk and
            // base.vdex. Reading these into the page cache here (while
            // the QR preview is rendering) cuts ~3-5s off the Java
            // launch when drastic actually starts after handoff.
            //
            // The APK directory has a random hash so we glob via opendir.
            const char* appsRoot = "/data/app";
            DIR* appsDir = opendir(appsRoot);
            if (appsDir) {
                struct dirent* outerEnt;
                while ((outerEnt = readdir(appsDir)) != nullptr) {
                    std::string outer(outerEnt->d_name);
                    if (outer == "." || outer == "..") continue;
                    // Top-level hashed dir, e.g. "~~e64G4SqpZ10aXMSOy6RMyQ=="
                    std::string outerPath =
                            std::string(appsRoot) + "/" + outer;
                    DIR* inner = opendir(outerPath.c_str());
                    if (!inner) continue;
                    struct dirent* innerEnt;
                    while ((innerEnt = readdir(inner)) != nullptr) {
                        std::string innerName(innerEnt->d_name);
                        // Inner dir, e.g. "com.dsemu.drastic-o1Z3oDew..."
                        if (innerName.compare(
                                0, strlen("com.dsemu.drastic"),
                                "com.dsemu.drastic") != 0) {
                            continue;
                        }
                        std::string pkgPath = outerPath + "/" + innerName;
                        warmFile((pkgPath + "/base.apk").c_str(), 0);
                        warmFile((pkgPath + "/oat/arm64/base.vdex").c_str(),
                                 0);
                        warmFile((pkgPath + "/oat/arm64/base.odex").c_str(),
                                 0);
                        warmFile((pkgPath + "/lib/arm64/libdrastic_arm64.so")
                                         .c_str(), 0);
                        warmFile((pkgPath + "/lib/arm64/libdrastic_cpu.so")
                                         .c_str(), 0);
                        break;
                    }
                    closedir(inner);
                }
                closedir(appsDir);
            }
        }

        int64_t t_end = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
        ALOGI("drastic preload: total %lldms", t_end - t0);
    }).detach();
}

int main() {
    // Grab DRM master early on non-Qualcomm SoCs to beat HWC.
    // On Qualcomm SDE (ro.board.platform=bengal etc), SET_MASTER
    // disrupts the backlight controller even without modeset, so we
    // defer the grab to readyToRun() (after skip_nano check). On
    // other SoCs (Rockchip, MediaTek), SET_MASTER is safe and we
    // need it early because HWC grabs master before readyToRun().
    {
        char platform[PROPERTY_VALUE_MAX] = {};
        property_get("ro.board.platform", platform, "");
        bool isQualcomm = (strstr(platform, "bengal") != nullptr ||
                           strstr(platform, "msm") != nullptr ||
                           strstr(platform, "sdm") != nullptr ||
                           strstr(platform, "sm") == platform ||
                           strstr(platform, "lahaina") != nullptr ||
                           strstr(platform, "taro") != nullptr ||
                           strstr(platform, "kalama") != nullptr);
        if (!isQualcomm) {
            // Bounded wait for /dev/dri/card0. On render-only DRM nodes
            // (e.g. Allwinner A133 + PowerVR Rogue, where /dev/dri/card0
            // is the GPU device with NO KMS / no CRTCs and the display
            // is driven through the sunxi /dev/disp character device by
            // hwcomposer.ceres.so) the early SET_MASTER grab buys us
            // nothing — drmEarlySplash will fail in readyToRun anyway
            // because MODE_GETRESOURCES returns count_crtcs==0. Cap the
            // poll at 600 ms so devices that never expose KMS don't
            // burn boot time waiting for a card that, even if it shows
            // up, won't be useful for direct rendering. Devices with
            // proper KMS (Rockchip, MediaTek non-A133) typically have
            // card0 ready before gammaos-nano even starts, so this cap
            // does not regress them. After the poll, peek
            // MODE_GETRESOURCES; if count_crtcs is 0 we skip SET_MASTER
            // entirely and close the fd, leaving the SF path alone.
            int fd = -1;
            int polls = 0;
            for (int i = 0; i < 60; i++) {
                fd = open("/dev/dri/card0", O_RDWR | O_CLOEXEC);
                if (fd >= 0) break;
                usleep(10000);
                polls++;
            }
            if (fd >= 0) {
                struct drm_mode_card_res res = {};
                int hasCrtcs =
                    (ioctl(fd, DRM_IOCTL_MODE_GETRESOURCES, &res) == 0 &&
                     res.count_crtcs > 0);
                if (!hasCrtcs) {
                    ALOGI("GammaOS Nano: /dev/dri/card0 has no CRTCs "
                          "(render-only DRM node, e.g. PVR on A133); "
                          "skipping early DRM master grab");
                    close(fd);
                } else if (ioctl(fd, DRM_IOCTL_SET_MASTER, 0) == 0) {
                    gEarlyDrmFd = fd;
                    ALOGI("GammaOS Nano: early DRM master fd=%d after %d polls (%dms)",
                          fd, polls, polls * 10);
                } else {
                    ALOGW("GammaOS Nano: SET_MASTER failed after %d polls: %s",
                          polls, strerror(errno));
                    close(fd);
                }
            } else {
                ALOGW("GammaOS Nano: card0 not found after %d polls (600ms)", polls);
            }
        } else {
            ALOGI("GammaOS Nano: Qualcomm SoC (%s), deferring DRM grab", platform);
        }
    }

    setpriority(PRIO_PROCESS, 0, ANDROID_PRIORITY_DISPLAY);

    ALOGI("GammaOS Nano starting... (earlyDrmFd=%d)", gEarlyDrmFd);

    property_set("sys.gammaos.nano.drm_active", "0");

    // SYNCHRONOUSLY patch libdrastic_arm64.so in the cache BEFORE
    // any other drastic work. The patch short-circuits drastic's
    // initialize_audio function to a single `ret`, which prevents
    // the 15-second boot-time stall caused by slCreateEngine's
    // binder waitForService("media.audio_flinger"). See the
    // patchDrasticAudioInit() comment block for the full rationale.
    //
    // Must happen on the main thread before the smoke-test (or the
    // preload thread) can possibly dlopen libdrastic_arm64.so —
    // otherwise bionic will mmap the unpatched .text segment and
    // the patch won't take effect until the next populate cycle.
    patchDrasticAudioInit();

    // Kick the drastic library preload in the background to warm
    // the rest of the dependency chain (libandroid, libxml2, power
    // HIDL, libdrastic itself, and sync prefetch of BIOS/ROM files).
    // These are safe to load in parallel with the main thread's
    // NanoMenu boot path.
    startDrasticLibPreloadThread();

    runDrasticInitIfNeeded();
    startDrasticQrTestWatcher();

    sp<ProcessState> proc(ProcessState::self());
    ProcessState::self()->startThreadPool();

    sp<NanoMenu> nano = new NanoMenu();

    // waitForSurfaceFlinger() removed: on the DRM boot path, SF is not
    // needed at all (headless EGL + DRM direct).  On restart (boot_completed=1),
    // readyToRun() creates SurfaceComposerClient which waits internally.

    nano->run("GammaOSNano", PRIORITY_DISPLAY);

    ALOGI("GammaOS Nano running. Joining thread pool.");

    IPCThreadState::self()->joinThreadPool();

    return 0;
}
