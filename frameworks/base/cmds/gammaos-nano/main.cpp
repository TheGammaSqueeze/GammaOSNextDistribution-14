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
#include <sys/syscall.h>
#include <unistd.h>

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

using namespace android;

// Wait for SurfaceFlinger to be available (same pattern as bootanimation)
static void waitForSurfaceFlinger() {
    int64_t waitStartTime = elapsedRealtime();
    sp<IServiceManager> sm = defaultServiceManager();
    const String16 name("SurfaceFlinger");
    const int SERVICE_WAIT_SLEEP_MS = 10;
    const int LOG_PER_RETRIES = 100;
    int retry = 0;
    while (sm->checkService(name) == nullptr) {
        retry++;
        if ((retry % LOG_PER_RETRIES) == 0) {
            ALOGW("Waiting for SurfaceFlinger, waited for %" PRId64 " ms",
                  elapsedRealtime() - waitStartTime);
        }
        usleep(SERVICE_WAIT_SLEEP_MS * 1000);
    }
    int64_t totalWaited = elapsedRealtime() - waitStartTime;
    if (totalWaited > SERVICE_WAIT_SLEEP_MS) {
        ALOGI("Waiting for SurfaceFlinger took %" PRId64 " ms", totalWaited);
    }
}

// Drastic in-process quick-resume smoke test.
//
// Gated on persist.gammaos.nano.drastic_smoke=1 so it's dormant in
// normal boots. Runs a full DrasticRunner::init() against the DE
// cache at /data/system/nano_cache/drastic/, which exercises:
//   Phase 1 (FakeJNI JNI_OnLoad)
//   Phase 3 (onInit + applyConfig + startGame)
//
// On success, drastic's main DS CPU thread and rasterizer pool will
// be running in the background when this returns. We deliberately
// do NOT tear them down here -- NanoMenu continues to its normal
// boot path afterward, and the drastic threads just hum along until
// the process exits at framework handoff.
//
// The smoke property is left set for repeat testing -- it's harmless
// on devices without drastic installed (runner returns false and
// we just continue).
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

static void runDrasticSmokeTestIfRequested() {
    char val[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.drastic_smoke", val, "0");
    if (val[0] == 0 || strcmp(val, "0") == 0) return;

    // val is one of:
    //   "1"            -> run immediately (original behavior)
    //   "wait_audio"   -> wait for audioserver, then run
    //   "wait_system"  -> wait for system_server, then run
    //   "wait_vendor"  -> wait for vendor.hwcomposer-2-1 or similar
    //   "wait_boot"    -> wait for sys.boot_completed=1
    //
    // Used to A/B test whether the boot-time 15s stall is caused
    // by drastic waiting for a specific service to be ready.
    if (strcmp(val, "wait_audio") == 0) {
        ALOGI("drastic smoke: waiting for audioserver...");
        waitForService("audioserver", 30000);
    } else if (strcmp(val, "wait_system") == 0) {
        ALOGI("drastic smoke: waiting for system_server...");
        waitForService("system_server", 60000);
    } else if (strcmp(val, "wait_boot") == 0) {
        ALOGI("drastic smoke: waiting for sys.boot_completed...");
        char bc[PROPERTY_VALUE_MAX] = {};
        int elapsed = 0;
        while (elapsed < 90000) {
            property_get("sys.boot_completed", bc, "");
            if (strcmp(bc, "1") == 0) break;
            usleep(100 * 1000);
            elapsed += 100;
        }
        ALOGI("drastic smoke: boot_completed after %dms", elapsed);
    } else if (strcmp(val, "wait_sf") == 0) {
        ALOGI("drastic smoke: waiting for surfaceflinger...");
        waitForService("surfaceflinger", 30000);
    } else if (strcmp(val, "wait_audio_hal") == 0) {
        ALOGI("drastic smoke: waiting for vendor.audio-hal...");
        waitForService("vendor.audio-hal", 30000);
    } else if (strcmp(val, "wait_media") == 0) {
        ALOGI("drastic smoke: waiting for media stack...");
        waitForService("vendor.audio-hal", 30000);
        waitForService("audioserver", 30000);
        waitForService("mediaserver", 30000);
        waitForService("media", 30000);
    } else if (strcmp(val, "wait_zygote_secondary") == 0) {
        // zygote_secondary = the 64-bit zygote on split-zygote devices,
        // or the only zygote on pure 64-bit. Often the last init svc
        // to come up before ActivityManager starts.
        ALOGI("drastic smoke: waiting for zygote_secondary...");
        waitForService("zygote_secondary", 60000);
    }

    ALOGI("drastic smoke: starting");

    const std::string cacheDir = "/data/system/nano_cache/drastic";

    // Discover the ROM by scanning the cache/rom/ subdir for the
    // first .nds file. populate_drastic only keeps one ROM at a time
    // so this gives us the currently-staged ROM without needing to
    // plumb another property through.
    std::string romPath;
    {
        std::string romDir = cacheDir + "/rom";
        DIR* d = opendir(romDir.c_str());
        if (d) {
            struct dirent* e;
            while ((e = readdir(d)) != nullptr) {
                std::string name(e->d_name);
                if (name == "." || name == "..") continue;
                if (name.size() >= 4 &&
                    name.compare(name.size() - 4, 4, ".nds") == 0) {
                    romPath = romDir + "/" + name;
                    break;
                }
            }
            closedir(d);
        }
    }
    if (romPath.empty()) {
        ALOGW("drastic smoke: no ROM in %s/rom -- skipping", cacheDir.c_str());
        return;
    }
    ALOGI("drastic smoke: rom=%s", romPath.c_str());

    // Heap-allocate the runner so the background threads drastic
    // spawns during startGame keep their state alive after this
    // function returns. We intentionally never delete it.
    auto* runner = new android::DrasticRunner();
    bool ok = runner->init(cacheDir, romPath);
    ALOGI("drastic smoke: DrasticRunner::init returned %s",
          ok ? "true" : "false");
    if (!ok) {
        ALOGW("drastic smoke: init failed -- see earlier logs");
        // Don't delete the runner even on failure -- we don't know
        // which stage failed, and if drastic already spawned threads
        // before the failure, destroying the runner would race them.
    }

    ALOGI("drastic smoke: done");
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
                                while ((e = readdir(d)) != nullptr) {
                                    std::string name(e->d_name);
                                    if (name == "." || name == "..") continue;
                                    if (name.size() >= 4 &&
                                        name.compare(name.size() - 4,
                                                     4, ".nds") == 0) {
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

        // (A) libdrastic's static DT_NEEDED heavy dep. libOpenSLES
        // pulls in libwilhelm → libmedia → ~200 lib media stack
        // transitively. Warming it in background hides the cold
        // page cache cost from the user-visible QR window.
        //
        // Note: the slCreateEngine binder wait that would normally
        // stall here for ~15s is avoided by the separate
        // patchDrasticAudioInit() in main() which short-circuits
        // drastic's initialize_audio function to `ret`. Drastic
        // never calls slCreateEngine, so this warm load is pure
        // page cache warmup with zero semantic effect on drastic.
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

        // libdrastic_{cpu,arm64}.so: if the cache is populated, warm
        // those too so the user-visible dlopen in DrasticRunner::init
        // is a pure cache hit. If the cache isn't there, silently
        // skip — populate_drastic hasn't run yet.
        const char* cpuPath =
                "/data/system/nano_cache/drastic/libdrastic_cpu.so";
        const char* arm64Path =
                "/data/system/nano_cache/drastic/libdrastic_arm64.so";
        bool cacheReady = (access(arm64Path, R_OK) == 0);
        if (cacheReady) {
            warmLib(cpuPath);
            warmLib(arm64Path);
        } else {
            ALOGI("drastic preload: drastic libs not cached yet, "
                  "skipping runner .so warmup");
        }

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
        }

        int64_t t_end = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
        ALOGI("drastic preload: total %lldms", t_end - t0);
    }).detach();
}

int main() {
    setpriority(PRIO_PROCESS, 0, ANDROID_PRIORITY_DISPLAY);

    ALOGI("GammaOS Nano starting...");

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

    runDrasticSmokeTestIfRequested();
    startDrasticQrTestWatcher();

    sp<ProcessState> proc(ProcessState::self());
    ProcessState::self()->startThreadPool();

    sp<NanoMenu> nano = new NanoMenu();

    waitForSurfaceFlinger();

    nano->run("GammaOSNano", PRIORITY_DISPLAY);

    ALOGI("GammaOS Nano running. Joining thread pool.");

    IPCThreadState::self()->joinThreadPool();

    return 0;
}
