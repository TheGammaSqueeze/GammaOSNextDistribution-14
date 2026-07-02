/*
 * Copyright (C) 2026 GammaOS
 *
 * drastic-nano: a standalone DS emulator that dlopens libdrastic
 * directly from the installed com.dsemu.drastic APK and reads /
 * writes the user's actual drastic data dir at
 * /data/user/0/com.dsemu.drastic/files/DraStic. No caching,
 * no copies -- the user's real saves, savestates, cheats, config
 * and filters are what drastic-nano sees, and any progress made
 * during a drastic-nano session shows up the next time the real
 * drastic app is launched.
 *
 * We render directly to DRM (no SurfaceFlinger) using the same
 * DRM + AHB zero-copy path as gammaos-nano's Quick Resume preview,
 * minus the preview overlay and desaturation fade.
 *
 * Runs as root so it can read the com.dsemu.drastic UID-owned
 * app data dir and the APK install dir; the process is short-
 * lived (one session per launch) and the dlopen'd library is the
 * same black-box drastic native code we already load inside
 * gammaos-nano (graphics UID). Net security delta is minimal.
 *
 * Life cycle:
 *   1. gammaos-nano writes the ROM path to
 *      /data/system/nano_drastic_nano_rom.txt and sets
 *      sys.gammaos.drastic_nano.start=1.
 *   2. init stops SurfaceFlinger + vendor HWC on our
 *      sys.gammaos.drastic_nano.active=1 trigger and starts us.
 *   3. We grab DRM master, init EGL/GLES on a pbuffer, then
 *      dlopen libdrastic_arm64.so straight from the APK's
 *      lib/arm64 directory. No 4-byte initialize_audio patch --
 *      the real OpenSL ES engine comes up and audio works.
 *   4. DrasticRunner boots the DS and enters the emulator loop.
 *   5. Long-press BACK (3 s) exits cleanly: drastic's pauseSystem
 *      + quitSystem fire via DrasticRunner::shutdown(), which
 *      writes the autosave to the real files/DraStic/backup/
 *      directory. No sync-out needed.
 *   6. We release DRM, signal session_done=1, and init restarts
 *      SurfaceFlinger + gammaos-nano.
 */

#define LOG_TAG "DrasticNano"

#include <android/log.h>

#include <dirent.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <poll.h>
#include <pthread.h>
#include <sched.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <drm.h>
#include <drm_mode.h>

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <thread>
#include <vector>

#include <aidl/android/hardware/light/HwLight.h>
#include <aidl/android/hardware/light/HwLightState.h>
#include <aidl/android/hardware/light/ILights.h>
#include <aidl/android/hardware/light/LightType.h>
#include <android/binder_manager.h>
#include <cutils/properties.h>
#include <system/thread_defs.h>
#include <utils/Log.h>
#include <utils/SystemClock.h>

#include "DrasticRunner.h"
#include "DrasticPrefs.h"
#include "FakeJNI.h"
#include "InputMap.h"
#include "NanoBacklight.h"
#include "NanoMenuDrm.h"
#include "NanoI18n.h"
#include "OverlayGfx.h"
#include "OverlayMenu.h"
#include "NanoRetroAchievements.h"
#include "DisplayBackend.h"
#include "SfDisplayBackend.h"
#include "DsScreenLayout.h"

using android::DrasticRunner;

namespace {

// Load the shared nano UI translations for the current system locale so the
// drastic-nano overlay menu / OSK match the language picked in the XMB. Reads
// the same persist.sys.locale + /system/etc/gammaos-nano/i18n resources as
// gammaos-nano; "en" (and any unshipped language) stays English passthrough.
void initDrasticLocale() {
    char locale[PROPERTY_VALUE_MAX] = {};
    property_get("persist.sys.locale", locale, "en-US");
    char lang[8] = {}, region[8] = {};
    const char* dash = strchr(locale, '-');
    if (dash) {
        size_t len = (size_t)(dash - locale);
        if (len >= sizeof(lang)) len = sizeof(lang) - 1;
        memcpy(lang, locale, len);
        if (dash[1]) strncpy(region, dash + 1, sizeof(region) - 1);
    } else {
        strncpy(lang, locale, sizeof(lang) - 1);
    }
    // Shipped resource languages (see frameworks/base/cmds/gammaos-nano/ps3xmb/i18n).
    static const char* kShipped[] = {
        "es", "fr", "de", "it", "pt", "nl", "ru", "ja", "ko", "ar", "tr", "pl",
    };
    const char* code = "en";
    if (strcmp(lang, "zh") == 0) {
        code = (strcmp(region, "TW") == 0 || strcmp(region, "HK") == 0) ? "zh-tw" : "zh-cn";
    } else {
        for (const char* c : kShipped) {
            if (strcmp(lang, c) == 0) { code = c; break; }
        }
    }
    android::i18nLoad(code);
    ALOGI("drastic-nano: locale=%s -> i18n '%s'", locale, code);
}

// Reuse the file nano's setDrasticNanoRomPath already writes so the
// XMB -> drastic-nano handoff and the post-QR-preview handoff both
// land on the same input channel.
constexpr const char* kRomPathFile       = "/data/system/nano_drastic_nano_rom.txt";
constexpr const char* kSessionDoneProp   = "sys.gammaos.drastic_nano.session_done";

// drastic's installed data dir. FakeJNI points here directly so
// DraStic/system/* and User/config|backup|savestates|cheats|... all
// resolve to the real files drastic writes / reads on the app's own
// runs. Any autosave or savestate produced during a drastic-nano
// session is picked up by the real drastic app on its next launch.
constexpr const char* kDrasticDataDir =
        "/data/user/0/com.dsemu.drastic/files/DraStic";

constexpr int64_t     kBackHoldMs        = 2000;

// ------------------------------------------------------------------
// Small utilities
// ------------------------------------------------------------------

std::string readTrimmed(const char* path) {
    int fd = open(path, O_RDONLY);
    if (fd < 0) return {};
    char buf[4096];
    ssize_t n = read(fd, buf, sizeof(buf));
    close(fd);
    if (n <= 0) return {};
    std::string s(buf, (size_t)n);
    while (!s.empty() && (s.back() == '\n' || s.back() == '\r' ||
                          s.back() == ' ' || s.back() == '\t')) {
        s.pop_back();
    }
    return s;
}

bool exists(const std::string& p) {
    struct stat st;
    return stat(p.c_str(), &st) == 0;
}

// Find the com.dsemu.drastic APK install directory.
// Returns the APK dir (e.g. /data/app/~~<hash>/com.dsemu.drastic-<h>)
// or empty on failure. The caller appends /lib/arm64 or /base.apk.
std::string findDrasticApkDir() {
    const char* root = "/data/app";
    DIR* d = opendir(root);
    if (!d) return {};
    struct dirent* e;
    while ((e = readdir(d)) != nullptr) {
        std::string name = e->d_name;
        if (name == "." || name == "..") continue;
        std::string p = std::string(root) + "/" + name;
        // Direct match (legacy layout without ~~<hash>/ wrapper).
        if (name.rfind("com.dsemu.drastic", 0) == 0) {
            closedir(d);
            return p;
        }
        // A14 layout: /data/app/~~<random>/com.dsemu.drastic-<hash>
        if (name.rfind("~~", 0) == 0) {
            DIR* d2 = opendir(p.c_str());
            if (!d2) continue;
            struct dirent* e2;
            while ((e2 = readdir(d2)) != nullptr) {
                std::string n2 = e2->d_name;
                if (n2.rfind("com.dsemu.drastic", 0) == 0) {
                    std::string full = p + "/" + n2;
                    closedir(d2);
                    closedir(d);
                    return full;
                }
            }
            closedir(d2);
        }
    }
    closedir(d);
    return {};
}

// Return the directory that contains libdrastic_arm64.so (and the
// optional libdrastic_cpu.so). Prefers the APK's extracted lib/arm64
// tree; if drastic was installed with extractNativeLibs="false" the
// libs live inside base.apk and we stage them to /data/local/tmp
// (tmpfs-backed on most devices, wiped on reboot).
std::string resolveDrasticLibsDir(const std::string& apkDir) {
    if (apkDir.empty()) return {};

    std::string extracted = apkDir + "/lib/arm64";
    if (exists(extracted + "/libdrastic_arm64.so")) {
        ALOGI("drastic-nano: libs at %s (APK extracted)",
              extracted.c_str());
        return extracted;
    }

    // Fallback: unzip from base.apk to /data/local/tmp. We run as
    // root so /data/app is readable. /data/local/tmp survives for
    // the process lifetime -- that is enough since dlopen mmaps the
    // file on first access.
    std::string baseApk = apkDir + "/base.apk";
    if (!exists(baseApk)) {
        ALOGE("drastic-nano: no base.apk at %s", baseApk.c_str());
        return {};
    }
    std::string libsTmp = "/data/local/tmp/drastic-nano-libs";
    mkdir(libsTmp.c_str(), 0755);
    const char* entries[] = {
        "lib/arm64-v8a/libdrastic_arm64.so",
        "lib/arm64-v8a/libdrastic_cpu.so",
        nullptr,
    };
    for (int i = 0; entries[i]; i++) {
        std::string cmd = "cd " + libsTmp +
                          " && unzip -o -j -q '" + baseApk +
                          "' '" + entries[i] + "' >/dev/null 2>&1";
        system(cmd.c_str());
    }
    if (!exists(libsTmp + "/libdrastic_arm64.so")) {
        ALOGE("drastic-nano: libdrastic_arm64.so unzip failed");
        return {};
    }
    ALOGI("drastic-nano: libs unzipped to %s", libsTmp.c_str());
    return libsTmp;
}

// Ensure all user-writable subdirectories drastic expects live under
// the installed data dir. Drastic crashes with fclose(NULL) when it
// tries to open-for-write under a missing directory; on a fresh
// drastic install some of these dirs do not exist until the first
// user action that creates them. Creating them up front costs nothing
// and keeps drastic-nano from tripping that crash on brand-new
// installs. Uses mkdir on a path that already exists is a no-op.
void ensureDrasticWritableDirs(uid_t appUid, gid_t appGid) {
    static const char* kDirs[] = {
        "backup", "savestates", "cheats", "slot2",
        "microphone", "input_record", "config", nullptr,
    };
    for (int i = 0; kDirs[i]; i++) {
        std::string p = std::string(kDrasticDataDir) + "/" + kDirs[i];
        if (mkdir(p.c_str(), 0770) == 0) {
            chown(p.c_str(), appUid, appGid);
            chmod(p.c_str(), 0770);
        }
    }
}

// Look up drastic's installed app UID / primary GID by stat'ing the
// top-level data dir. Returns true on success.
bool lookupDrasticUid(uid_t* uid, gid_t* gid) {
    struct stat st;
    if (stat(kDrasticDataDir, &st) != 0) return false;
    *uid = st.st_uid;
    *gid = st.st_gid;
    return true;
}

// ------------------------------------------------------------------
// CPU idle state management
// ------------------------------------------------------------------

// Saved cpu-sleep state so we can restore on exit. One entry per
// possible CPU -- dimensioned larger than needed on purpose.
constexpr int kMaxCpus = 16;
static int sSavedCpuSleepDisable[kMaxCpus];
static int sSavedCpuCount = 0;

// Retrigger the power profile service configured by the user's
// persist.gammaos.performance_mode selection. The init.rc trigger in
// /vendor/etc/init/init.gammaos_power.rc only fires on property
// changes and can race at boot, leaving the GPU / DMC / VOP / CPU
// governors in their default state (schedutil, not performance) even
// though the user has selected max. That surfaces as a non-
// deterministic "auto frameskip" on drastic-nano launches: lucky
// launches hit a hot GPU governor, unlucky ones hit the lingering
// default and fall behind every frame. Firing ctl.start directly
// bypasses the trigger race and guarantees the governors are in the
// chosen profile before drastic's first render.
void retriggerPowerProfile() {
    char mode[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.performance_mode", mode, "max");
    const char* svc = nullptr;
    if (strcmp(mode, "max") == 0) {
        svc = "setclock_max";
    } else if (strcmp(mode, "stock") == 0) {
        svc = "setclock_stock";
    } else if (strcmp(mode, "powersave") == 0) {
        svc = "setclock_powersave";
    } else {
        // Unknown mode: force max for the session since drastic-nano
        // is a game launcher and the player has signalled intent to
        // play. Safer than running the emulator with an unknown
        // governor state.
        svc = "setclock_max";
    }
    property_set("ctl.start", svc);
    ALOGI("drastic-nano: retriggered power profile %s (mode=%s)",
          svc, mode);
}

// Disable the deep cpu-sleep idle state (state1) on every CPU so
// that thread migrations do not pay the ~220 us wake latency. The
// shallow WFI state (state0, 1 us) stays enabled. Saves the prior
// value in sSavedCpuSleepDisable for restore on exit.
void disableDeepCpuIdle() {
    sSavedCpuCount = 0;
    for (int cpu = 0; cpu < kMaxCpus; cpu++) {
        char path[96];
        snprintf(path, sizeof(path),
                 "/sys/devices/system/cpu/cpu%d/cpuidle/state1/disable",
                 cpu);
        int fd = open(path, O_RDWR);
        if (fd < 0) {
            if (cpu == 0) {
                ALOGW("drastic-nano: cpuidle state1 not found (%s)",
                      strerror(errno));
            }
            break;
        }
        char buf[8] = {};
        ssize_t n = read(fd, buf, sizeof(buf) - 1);
        int prior = (n > 0 && buf[0] == '1') ? 1 : 0;
        sSavedCpuSleepDisable[cpu] = prior;
        sSavedCpuCount = cpu + 1;
        if (prior == 0) {
            lseek(fd, 0, SEEK_SET);
            if (write(fd, "1\n", 2) < 0) {
                ALOGW("drastic-nano: cpu%d cpu-sleep disable write "
                      "failed: %s", cpu, strerror(errno));
            }
        }
        close(fd);
    }
    ALOGI("drastic-nano: cpu-sleep disabled on %d core(s)",
          sSavedCpuCount);
}

// Restore cpu-sleep to whatever it was before disableDeepCpuIdle()
// ran. Called from the exit path so leaving the binary doesn't
// permanently kill deep idle (battery impact in the menu).
void restoreDeepCpuIdle() {
    for (int cpu = 0; cpu < sSavedCpuCount; cpu++) {
        char path[96];
        snprintf(path, sizeof(path),
                 "/sys/devices/system/cpu/cpu%d/cpuidle/state1/disable",
                 cpu);
        int fd = open(path, O_WRONLY);
        if (fd < 0) continue;
        const char* v = sSavedCpuSleepDisable[cpu] ? "1\n" : "0\n";
        (void)write(fd, v, 2);
        close(fd);
    }
}

// ------------------------------------------------------------------
// SF stop / start
// ------------------------------------------------------------------

// Crash handler: kicks the nano restart trigger before re-raising
// the signal so tombstoned still grabs the dump. Without this, a
// drastic-nano crash mid-session leaves the user on a black DRM
// framebuffer until they reboot. Called from async-signal-unsafe
// context, so we only touch property_set (bionic does an atomic
// property write) -- no logcat, no locale-dependent functions.
void crashCleanup(int sig) {
    property_set(kSessionDoneProp, "1");
    // On the SurfaceFlinger / overlay-home path a crash otherwise leaves
    // app_launched=1 set (runLoopSf re-asserts it every frame), so the resident
    // overlay stays parked behind the dead app and neither the game nor the home
    // returns -- a stuck / black screen. Hand the home back: clear app_launched
    // and raise the overlay. Both are async-signal-safe atomic property writes,
    // like session_done above. Harmless on the DRM path, where session_done
    // restarts the DRM home regardless.
    property_set("sys.gammaos.nano.app_launched", "0");
    property_set("sys.gammaos.nano.show_overlay", "1");
    // Restore default disposition and re-raise so tombstone catches
    // the crash with a proper stack trace.
    signal(sig, SIG_DFL);
    raise(sig);
}

// Save-state slot 9 path helpers, shared by the pre-load validation, the render
// loop's crash-marker clear, and the shutdown save-and-verify tail. The .dss
// basename is the ROM filename with its extension stripped (matches drastic's
// savestates/<rom>_<slot>.dss layout).
constexpr off_t kMinDssBytes = 4096;   // a valid DS save state is far larger; this only rejects empty/truncated files
static std::string slot9Stem(const std::string& savestatesDir, const std::string& romPath) {
    std::string b = romPath;
    size_t sp = b.find_last_of('/'); if (sp != std::string::npos) b = b.substr(sp + 1);
    size_t dt = b.find_last_of('.'); if (dt != std::string::npos) b = b.substr(0, dt);
    return savestatesDir + "/" + b + "_9";   // caller appends ".dss" / ".loading" / ".dss.bad"
}

// Set by SIGTERM. At device shutdown init may "stop drastic-nano"; without this
// the process would be killed mid-session and slot 9 (the Quick Resume state)
// would never be saved. saveState runs on a DraStic worker thread and is NOT
// async-signal-safe, so the handler only flips a flag the run loop polls -- the
// loop then breaks into the normal save-and-exit tail. volatile sig_atomic_t is
// the only object safe to touch from a signal handler.
volatile sig_atomic_t gTermRequested = 0;
void termCleanup(int) { gTermRequested = 1; }

void installCrashHandler() {
    struct sigaction sa{};
    sa.sa_handler = crashCleanup;
    sigemptyset(&sa.sa_mask);
    // SA_NODEFER so re-raising the same signal inside the handler
    // re-enters with default disposition (SIG_DFL) and aborts the
    // process cleanly.
    sa.sa_flags = SA_NODEFER | SA_RESETHAND;
    // Diagnostic escape hatch: when persist.gammaos.drastic_nano.crash_tombstone=1
    // we do NOT install our crash handler, so debuggerd catches crash signals and
    // writes a full /data/tombstones stack trace (our handler otherwise re-raises
    // with SIG_DFL, which bypasses debuggerd and leaves no tombstone). Default off:
    // production keeps the session_done-then-reraise behavior that returns the home.
    if (!property_get_bool("persist.gammaos.drastic_nano.crash_tombstone", false)) {
        sigaction(SIGBUS,  &sa, nullptr);
        sigaction(SIGSEGV, &sa, nullptr);
        sigaction(SIGABRT, &sa, nullptr);
        sigaction(SIGILL,  &sa, nullptr);
        sigaction(SIGFPE,  &sa, nullptr);
    } else {
        ALOGW("drastic-nano: crash_tombstone=1 -- crash handler DISABLED, debuggerd will tombstone");
    }
    // SIGTERM is a graceful stop request, not a crash: only set the flag (no
    // re-raise), so the run loop can save slot 9 before exiting.
    struct sigaction st{};
    st.sa_handler = termCleanup;
    sigemptyset(&st.sa_mask);
    st.sa_flags = 0;
    sigaction(SIGTERM, &st, nullptr);
}

// ------------------------------------------------------------------
// DRM + EGL bootstrap (no SurfaceFlinger)
// ------------------------------------------------------------------

struct Display {
    EGLDisplay eglDpy;
    EGLContext eglCtx;
    EGLSurface eglSurf;
    int width;
    int height;
};

// Initialize DRM master, pick the primary display, create an EGL
// pbuffer context suitable for AHB FBO rendering, then wire up the
// zero-copy AHB -> DRM PRIME scanout path via drmSetupZeroCopy().
bool setupDisplay(Display* out) {
    // The relaunch handshake ("Restart Game", a hardcore toggle, or a
    // restart-required setting) starts this instance while the previous
    // drastic-nano may still be tearing down and still holding DRM master. The
    // master is exclusive, so our first grab can enumerate zero displays (the
    // modeset that adds a display needs master). drmEarlySplash drops master and
    // closes its fd when it finds no displays, so each attempt is self-contained
    // -- retry briefly to let the outgoing instance release master before we
    // give up and fall back to SurfaceFlinger, which strands offscreen on a
    // DRM-direct panel and would leave the user on a dead home.
    android::drmEarlySplash();
    for (int tries = 0;
         (!android::sDrmActive || android::sDrmDisplays.empty()) && tries < 30;
         tries++) {
        usleep(100 * 1000);   // 100 ms per attempt, up to ~3s total
        android::drmEarlySplash();
    }
    if (!android::sDrmActive || android::sDrmDisplays.empty()) {
        ALOGE("drastic-nano: DRM master / display enumeration failed");
        return false;
    }

    const android::DrmDisplay& prim =
            android::sDrmDisplays[android::sDrmPrimaryIdx];
    out->width  = (int)prim.w;
    out->height = (int)prim.h;
    ALOGI("drastic-nano: primary %dx%d", out->width, out->height);

    out->eglDpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (out->eglDpy == EGL_NO_DISPLAY ||
        !eglInitialize(out->eglDpy, nullptr, nullptr)) {
        ALOGE("drastic-nano: eglInitialize failed: 0x%x", eglGetError());
        return false;
    }

    EGLConfig cfg = android::getEglConfig(out->eglDpy);
    EGLint pbufAttrs[] = {
        EGL_WIDTH,  16,
        EGL_HEIGHT, 16,
        EGL_NONE,
    };
    out->eglSurf = eglCreatePbufferSurface(out->eglDpy, cfg, pbufAttrs);
    if (out->eglSurf == EGL_NO_SURFACE) {
        ALOGE("drastic-nano: eglCreatePbufferSurface failed: 0x%x",
              eglGetError());
        return false;
    }

    EGLint ctxAttrs[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    out->eglCtx = eglCreateContext(out->eglDpy, cfg, EGL_NO_CONTEXT,
                                   ctxAttrs);
    if (out->eglCtx == EGL_NO_CONTEXT) {
        ALOGE("drastic-nano: eglCreateContext failed: 0x%x", eglGetError());
        return false;
    }
    if (!eglMakeCurrent(out->eglDpy, out->eglSurf, out->eglSurf,
                        out->eglCtx)) {
        ALOGE("drastic-nano: eglMakeCurrent failed: 0x%x", eglGetError());
        return false;
    }

    android::drmSetupZeroCopy(out->eglDpy);
    if (!android::sDrmZeroCopy) {
        ALOGE("drastic-nano: DRM zero-copy setup failed; no AHB path");
        return false;
    }

    return true;
}

// Input / overlay wiring now lives in InputMap.{h,cpp} and
// OverlayMenu.{h,cpp}. See runLoop below for the call-site glue.

// ------------------------------------------------------------------
// Audio thread priority boost
// ------------------------------------------------------------------

// Walk audioserver's /proc/<pid>/task and boost its playback
// threads to SCHED_FIFO prio 79 so they drain our BufferQueue
// without getting time-sliced out by drastic-nano's own FIFO 80
// threads.
//
// Root cause: the actual audio bottleneck is NOT our in-process
// OpenSL client thread -- that just binder-posts buffers to
// audioserver. It is audioserver's AudioOut_D / FastMixer / writer
// threads, which run at SCHED_OTHER nice -19. When drastic-nano is
// CPU-saturated (3.3 cores used), SCHED_FIFO threads in our
// process (render, mali helpers) starve audioserver. Drastic's
// BufferQueue fills because audioserver cannot drain. Drastic
// gets SL_RESULT_BUFFER_INSUFFICIENT, the mixer stalls for one
// sample-frame, the user hears a click.
//
// Boosting our own AudioTrack thread (experiment 2026-04-17) made
// this worse: it pulled MORE CPU time toward our process and
// starved audioserver even harder. 13 underruns in 30 s after that
// change, vs ~1 in 5 min before.
//
// Priority 79: one rung below render at 80 so it never preempts
// our per-frame critical path, but above everything else in the
// system. Drastic-nano runs as root with CAP_SYS_NICE so we can
// sched_setscheduler on another process's tids.
//
// Called periodically from the render loop because audioserver may
// spawn / recycle its output thread when the audio device reroutes.
// Boost the output threads of an arbitrary peer process to
// SCHED_FIFO 79. We look up the pid by init's published property
// and iterate its /proc/<pid>/task/<tid>/comm. Needs readproc gid
// in our rc caps so the hidepid=invisible /proc mount lets us
// traverse other UIDs' task trees.
void boostPeerAudioThreads(const char* svcPropName,
                            const char* tag) {
    char pidStr[PROPERTY_VALUE_MAX] = {};
    property_get(svcPropName, pidStr, "");
    pid_t srvPid = (pid_t)atoi(pidStr);
    if (srvPid <= 0) {
        ALOGI("drastic-nano: %s not running (%s empty)",
              tag, svcPropName);
        return;
    }

    char taskDir[64];
    snprintf(taskDir, sizeof(taskDir), "/proc/%d/task", srvPid);
    DIR* d = opendir(taskDir);
    if (!d) {
        ALOGW("drastic-nano: cannot open %s: %s", taskDir,
              strerror(errno));
        return;
    }
    struct dirent* e;
    int boosted = 0;
    while ((e = readdir(d)) != nullptr) {
        if (e->d_name[0] == '.') continue;
        char commPath[128];
        snprintf(commPath, sizeof(commPath),
                 "/proc/%d/task/%s/comm", srvPid, e->d_name);
        int fd = open(commPath, O_RDONLY);
        if (fd < 0) continue;
        char comm[32] = {};
        ssize_t n = read(fd, comm, sizeof(comm) - 1);
        close(fd);
        if (n <= 0) continue;
        for (int i = 0; i < (int)sizeof(comm) && comm[i]; i++) {
            if (comm[i] == '\n') { comm[i] = 0; break; }
        }
        // Boost every thread that matters on the playback path:
        //   AudioOut_D / AudioOut_1 -- AudioFlinger PlaybackThread
        //   FastMixer               -- AudioFlinger fast-path mixer
        //   writer                  -- vendor HAL's ALSA writer
        //   out_write               -- some HAL variants
        if (strcmp(comm, "AudioOut_D") != 0 &&
            strcmp(comm, "FastMixer") != 0 &&
            strcmp(comm, "AudioOut_1") != 0 &&
            strcmp(comm, "writer") != 0 &&
            strcmp(comm, "out_write") != 0) {
            continue;
        }
        pid_t tid = (pid_t)atoi(e->d_name);
        sched_param sp = {};
        sp.sched_priority = 79;
        if (sched_setscheduler(tid, SCHED_FIFO, &sp) == 0) {
            ALOGI("drastic-nano: boosted %s %s (tid=%d) to "
                  "SCHED_FIFO 79", tag, comm, tid);
            boosted++;
        } else {
            ALOGW("drastic-nano: boost %s %s (tid=%d) failed: %s",
                  tag, comm, tid, strerror(errno));
        }
    }
    closedir(d);
    if (boosted == 0) {
        ALOGI("drastic-nano: no %s output threads found (not live "
              "yet)", tag);
    }
}

void boostAudioServer() {
    // AudioFlinger's own mixer thread + HAL writer need the same
    // RT guarantee. Boosting only one side leaves the other as the
    // weak link -- a SCHED_OTHER writer holds up the MIXER, a
    // SCHED_OTHER mixer holds up the track enqueue, either way we
    // still get underruns under CPU contention. Pair them.
    boostPeerAudioThreads("init.svc_debug_pid.audioserver",
                           "audioserver");
    boostPeerAudioThreads("init.svc_debug_pid.vendor.audio-hal",
                           "vendor.audio-hal");
}

// ------------------------------------------------------------------
// Sleep / wake
// ------------------------------------------------------------------

// Set every BACKLIGHT light through the ILights AIDL HAL (same
// convention as the nano home's setBrightnessViaHal: 0-255 packed into
// the RGB channels). The sysfs nodes are driven separately through the
// shared NanoBacklight helper; the HAL covers devices that route the
// panel backlight exclusively through it.
void setBacklightHal(int brightness) {
    using aidl::android::hardware::light::ILights;
    using aidl::android::hardware::light::HwLight;
    using aidl::android::hardware::light::HwLightState;
    using aidl::android::hardware::light::LightType;

    ndk::SpAIBinder binder(
            AServiceManager_checkService("android.hardware.light.ILights/default"));
    if (!binder.get()) return;
    std::shared_ptr<ILights> hal = ILights::fromBinder(binder);
    if (!hal) return;

    std::vector<HwLight> lights;
    hal->getLights(&lights);
    for (const auto& light : lights) {
        if (light.type == LightType::BACKLIGHT) {
            HwLightState state{};
            state.color = 0xFF000000 | (brightness << 16) |
                          (brightness << 8) | brightness;
            hal->setLightState(light.id, state);
        }
    }
}

// Short power press = real system sleep, mirroring the nano home's
// recipe (NanoMenuInput.cpp): pause the emulator, blank both panels,
// kill the backlights, let PowerManager suspend the device via the
// process-agnostic nano-dosleep init trigger, block on the input fds
// until the power button wakes the kernel, then wake PowerManager via
// nano-dowake, re-commit the DRM modeset (resume brings the CRTCs back
// with no planes - without the recommit both panels stay black behind
// a lit backlight and every page flip EBUSYs forever) and restore the
// backlights before resuming emulation.
//
// Runs on the render loop thread, which owns all DRM/GL state.
void doSleep(android::drastic_input::InputState* input,
             DrasticRunner* dr, bool overlayWasOpen) {
    ALOGI("drastic-nano: power short press, sleeping");
    // The in-game menu already paused the emulator when it opened;
    // pauseToggle is absolute so pausing twice would be harmless, but
    // resuming on wake must not undo a menu-held pause.
    if (!overlayWasOpen) dr->pauseToggle(true);

    // Blank both panels: clear AHB ring slot 0 (what drmFlipAll scans
    // out) and present it, so the wake-time recommit relights onto
    // black rather than the last gameplay frame.
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    if (android::sDrmZeroCopy && android::sAhbRingPrimary[0].glFbo) {
        glBindFramebuffer(GL_FRAMEBUFFER, android::sAhbRingPrimary[0].glFbo);
        glClear(GL_COLOR_BUFFER_BIT);
        if (android::sAhbRingSecondary[0].glFbo) {
            glBindFramebuffer(GL_FRAMEBUFFER,
                              android::sAhbRingSecondary[0].glFbo);
            glClear(GL_COLOR_BUFFER_BIT);
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glFinish();
        android::drmFlipAll();
    }
    android::nanobl::nanoBacklightSet(0);
    setBacklightHal(0);

    // Match the home XMB's clean pre-suspend state. The home suspends and
    // resumes reliably; drastic runs the session with deep CPU idle
    // DISABLED, performance governors, and a SCHED_FIFO render thread -
    // and a real suspend-to-RAM (no USB tether) in that state is what
    // destabilises the resume and crashes the session. Before blocking:
    //  - re-enable deep CPU idle so the cores can actually power down,
    //  - relax the governors to powersave (the perf profile keeps clocks
    //    pinned, fighting suspend),
    //  - drop THIS thread (the render loop) off SCHED_FIFO so the kernel's
    //    task-freeze does not have to freeze a running RT task.
    // All restored on wake.
    restoreDeepCpuIdle();
    property_set("ctl.start", "setclock_powersave");
    {
        struct sched_param sp = {};
        sched_setscheduler(0, SCHED_OTHER, &sp);
    }

    // sys.boot_completed is always 1 during a drastic-nano session
    // (the XMB launched us post-boot); the guard is robustness only.
    bool pmSleep = property_get_bool("sys.boot_completed", false);
    if (pmSleep) {
        property_set("sys.gammaos.nano.dosleep", "1");
    }

    bool asleep = true;
    while (asleep) {
        struct pollfd pfds[16];
        int nf = 0;
        for (int fd : input->fds) {
            if (fd >= 0 && nf < 16) {
                pfds[nf].fd = fd;
                pfds[nf].events = POLLIN;
                nf++;
            }
        }
        poll(pfds, nf, -1);
        struct input_event ev;
        for (int fd : input->fds) {
            while (read(fd, &ev, sizeof(ev)) == sizeof(ev)) {
                // Wake on a power-button press OR the lid opening
                // (SW_LID -> 0). Track the lid level so the close that
                // put us to sleep is not mistaken for a wake.
                if (ev.type == EV_KEY && ev.code == KEY_POWER &&
                    ev.value == 1) {
                    asleep = false;
                } else if (ev.type == EV_SW && ev.code == SW_LID) {
                    input->lidClosed = (ev.value != 0);
                    if (ev.value == 0) asleep = false;
                }
            }
        }
    }

    // PowerManager never saw the waking press (we read it off evdev),
    // so wake it explicitly, mirroring the home.
    if (pmSleep) {
        property_set("sys.gammaos.nano.dosleep", "0");
        property_set("sys.gammaos.nano.dowake", "1");
    }
    usleep(200000);
    // Drain everything (including the wake press's release and any
    // touch events) and reset the gesture trackers so the consumed
    // wake press cannot fire sleep/menu/exit on the next poll.
    {
        struct input_event d;
        for (int fd : input->fds) {
            while (read(fd, &d, sizeof(d)) == sizeof(d)) {}
        }
        for (int fd : input->touchFds) {
            while (read(fd, &d, sizeof(d)) == sizeof(d)) {}
        }
    }
    input->powerWasDown = false;
    input->powerPressStartMs = 0;
    input->powerHoldFired = false;
    input->backWasDown = false;
    input->backPressStartMs = 0;

    // Restore the session's performance state (mirror of the pre-sleep
    // relax above): RT render thread, deep-idle disabled, perf governors.
    {
        struct sched_param sp = {};
        sp.sched_priority = 80;
        if (sched_setscheduler(0, SCHED_FIFO, &sp) != 0) {
            // FIFO denied (rare): fall back to nice -20 like boot.
            setpriority(PRIO_PROCESS, 0, -20);
        }
    }
    disableDeepCpuIdle();
    retriggerPowerProfile();

    // Resume re-enables the CRTCs with no planes; re-commit the
    // modeset and reset the flip/ring bookkeeping before relighting.
    // The ring cursors restart at 0, so the render loop's bootstrap
    // re-primes (renders 2 frames before the first flip) automatically.
    android::drmResumeRecommit();

    int level = property_get_int32("persist.gammaos.nano.brightness", 128);
    if (level < 1) level = 1;
    if (level > 255) level = 255;
    android::nanobl::nanoBacklightSet(level);
    setBacklightHal(level);

    if (!overlayWasOpen) dr->pauseToggle(false);
    // audioserver may have respawned its output thread across the
    // suspend at SCHED_OTHER; re-boost so audio does not underrun.
    boostAudioServer();
    ALOGI("drastic-nano: woke up");
}

// ------------------------------------------------------------------
// Render loop
// ------------------------------------------------------------------

// kBackShortMs vs kBackHoldMs: release before kBackShortMs = short
// press = toggle overlay; held past kBackHoldMs = long press = exit.
constexpr int64_t kBackShortMs = 500;

// Power gestures (read straight from evdev: PhoneWindowManager
// consumes KEYCODE_POWER inertly while minimal_boot=1 with no app or
// SF overlay foreground, which is exactly a drastic-nano session).
// Release before kPowerHoldMs = system sleep; held past it = raise the
// in-game overlay menu (drastic owns the DRM panel, so the menu is
// drastic's own DRM-direct OverlayMenu, the same one short-BACK opens;
// the SurfaceFlinger gammaos-nano XMB overlay cannot composite over a
// DRM-master game). 1.5 s matches the home XMB's power convention.
// Deliberate difference: in the home a 1.5 s power hold means SHUTDOWN;
// in-game it raises the menu and there is no in-game power-shutdown
// (exit is the BACK hold, shutdown lives in the XMB).
constexpr int64_t kPowerHoldMs = 1500;
// Hold POWER this long in-game (DRM) to power the device off. Continues past the
// 1.5s overlay-raise, so a long hold escalates: tap = sleep, 1.5s = overlay,
// 5s = graceful power off. The user picked "power off directly" for this gesture.
constexpr int64_t kPowerOffHoldMs = 5000;

struct RunLoopResult {
    bool relaunchRequested;
    bool restartFresh;   // "Restart Game": relaunch + boot fresh, no save
    bool powerOffAfter;  // graceful save then power the device off (no XMB return)
    bool rebootAfter;    // graceful save then reboot the device (no XMB return)
    bool quitShutdown;   // external quit (nano/ShutdownThread) or SIGTERM: save +
                         // exit; the CALLER issues the power action, so we skip
                         // session_done (no home restart racing sys.powerctl).
    bool exitToHome;     // back-hold graceful exit: force the slot-9 save then take
                         // the normal return-to-launcher path (raise the overlay /
                         // DRM home). Mirrors RetroArch's back-hold ESC + app close.
};

// Debug screenshot. When sys.gammaos.drastic_nano.shot=1, read back the bound
// framebuffers and write them as PPMs to /data. The standard VOP framebuffer
// dump cannot capture the overlay plane, so this is the only way to get a true
// picture of the in-game UI (the overlay menu, the Achievements list, and the
// on-screen keyboard, which renders on the bottom panel). Both DS panels are
// captured: the top (primary, with the overlay) and the bottom (secondary,
// with the on-screen keyboard).
static bool shotRequested() {
    char shot[PROPERTY_VALUE_MAX] = {};
    property_get("sys.gammaos.drastic_nano.shot", shot, "");
    return shot[0] == '1';
}

static void captureFboToPpm(int w, int h, const char* path) {
    std::vector<uint8_t> buf((size_t)w * h * 4);
    glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, buf.data());
    FILE* f = fopen(path, "wb");
    if (!f) return;
    fprintf(f, "P6\n%d %d\n255\n", w, h);
    std::vector<uint8_t> row((size_t)w * 3);
    // glReadPixels returns rows bottom-to-top. On the rotated DRM panel the
    // framebuffer is already vertically inverted relative to what the user
    // sees, so the native row order comes out upright; on a non-rotated panel
    // we flip to the usual top-left origin.
    const bool flip = !android::sDrmGlRotation;
    for (int i = 0; i < h; i++) {
        const int y = flip ? (h - 1 - i) : i;
        const uint8_t* src = buf.data() + (size_t)y * w * 4;
        for (int x = 0; x < w; x++) {
            row[x * 3 + 0] = src[x * 4 + 0];
            row[x * 3 + 1] = src[x * 4 + 1];
            row[x * 3 + 2] = src[x * 4 + 2];
        }
        fwrite(row.data(), 1, (size_t)w * 3, f);
    }
    fclose(f);
    ALOGI("drastic-nano: wrote screenshot %s (%dx%d)", path, w, h);
}

// Defined below (shared by the DRM single-panel layout path and runLoopSf).
static drastic_nano::LayoutConfig readSfLayoutConfig(int surfaceW, int surfaceH);

// Draws the virtual touch cursor (a crosshair) over the bottom DS screen. The
// cursor position is in DS-native units (0..255, 0..191); it maps onto the
// bottom screen's on-screen rectangle. Drawn in the overlay's logical pixel
// space (the same space compute()/bottomRect() use), so it tracks the layout
// and any display rotation. Turns green while A is held (touch down).
static void drawTouchCursor(android::drastic_gfx::OverlayGfx& gfx,
                            const drastic_nano::Rect& br,
                            float cursorX, float cursorY, bool pressed) {
    if (br.w <= 0.0f || br.h <= 0.0f) return;
    using android::drastic_gfx::Color;
    const float px = br.x + (cursorX / 256.0f) * br.w;
    const float py = br.y + (cursorY / 192.0f) * br.h;
    float len = br.w * 0.030f;  if (len < 9.0f)  len = 9.0f;
    float thin = len * 0.20f;   if (thin < 2.0f) thin = 2.0f;
    const Color dark = { 0.0f, 0.0f, 0.0f, 0.75f };
    const Color fill = pressed ? Color{ 0.25f, 1.0f, 0.40f, 0.95f }
                               : Color{ 1.0f, 0.85f, 0.20f, 0.95f };
    // Dark backing (1px larger) for contrast, then the bright crosshair + dot.
    gfx.fillRect(px - len - 1.0f, py - thin * 0.5f - 1.0f, 2.0f * len + 2.0f, thin + 2.0f, dark);
    gfx.fillRect(px - thin * 0.5f - 1.0f, py - len - 1.0f, thin + 2.0f, 2.0f * len + 2.0f, dark);
    gfx.fillRect(px - len, py - thin * 0.5f, 2.0f * len, thin, fill);
    gfx.fillRect(px - thin * 0.5f, py - len, thin, 2.0f * len, fill);
    gfx.fillRect(px - thin, py - thin, 2.0f * thin, 2.0f * thin, fill);
}

RunLoopResult runLoop(Display* dpy, DrasticRunner* dr,
                      const android::drastic_prefs::Prefs& initialPrefs,
                      uid_t appUid, gid_t appGid,
                      const std::string& xmlPath,
                      const std::string& savestatesDir,
                      const std::string& romPath,
                      const std::string& shadersDir) {
    RunLoopResult result{false, false, false, false, false};
    bool hasDualDisplay = (android::sDrmActive && android::sDrmZeroCopy &&
                            android::sAhbRingSecondary[0].glFbo != 0);
    dr->initSurface(dpy->width, dpy->height, hasDualDisplay);
    dr->setRotationMatrix(android::sDrmRotMat);

    // Single-panel DRM layout (opt-in via persist.gammaos.drastic_nano.drm_single_layout).
    // The dual-panel branch (RG DS, two DSI panels) is never touched. When enabled on a
    // single-panel device the DS screens are laid out by the advanced_drastic presets into
    // a logical-orientation offscreen, then composited onto the panel-native FBO rotated by
    // the install matrix -- so a rotated (portrait) panel shows the landscape layout without
    // the stretch a direct rotated layout produces. Off by default: the existing
    // renderBothScreens (fixed stack) stays the default for every device that relies on it.
    const bool drmSingleLayout =
            !hasDualDisplay &&
            property_get_bool("persist.gammaos.drastic_nano.drm_single_layout", false);
    // The panel's native FBO size (what the AHB ring scans out).
    const int drmPanelW = (android::sAhbRingPrimary[0].glFbo != 0)
                          ? (int)android::sAhbRingPrimary[0].w : dpy->width;
    const int drmPanelH = (android::sAhbRingPrimary[0].glFbo != 0)
                          ? (int)android::sAhbRingPrimary[0].h : dpy->height;
    // The effective rotation is the panel install orientation plus a live user
    // Display Rotation (persist.gammaos.drastic_nano.display_rotate), so the
    // whole single-panel output can be turned for portrait play. The logical
    // (content) size swaps on a perpendicular rotation; these are mutable and
    // recomputed in the loop when the user changes Display Rotation.
    int drmDisplayRotate = 0;
    {
        char drp[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.drastic_nano.display_rotate", drp, "0");
        drmDisplayRotate = atoi(drp);
    }
    int drmEffRot = ((android::sDrmRotationDeg + drmDisplayRotate) % 360 + 360) % 360;
    bool drmRotated = (drmEffRot == 90 || drmEffRot == 270);
    int drmLogicalW = drmRotated ? drmPanelH : drmPanelW;
    int drmLogicalH = drmRotated ? drmPanelW : drmPanelH;
    GLuint drmLayoutFbo = 0, drmLayoutTex = 0;
    if (drmSingleLayout) {
        glGenTextures(1, &drmLayoutTex);
        glBindTexture(GL_TEXTURE_2D, drmLayoutTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, drmLogicalW, drmLogicalH, 0,
                     GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        // NEAREST, not LINEAR: the offscreen->panel blit is 1:1 (or a 90/180/270
        // turn), so bilinear only adds a half-texel smear that softens the
        // integer-scaled DS pixels (most visible at 2x). NEAREST keeps it crisp.
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glGenFramebuffers(1, &drmLayoutFbo);
        glBindFramebuffer(GL_FRAMEBUFFER, drmLayoutFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                               GL_TEXTURE_2D, drmLayoutTex, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        ALOGI("drastic-nano: DRM single-panel layout ON (logical %dx%d, panel %dx%d, rot=%d)",
              drmLogicalW, drmLogicalH, drmPanelW, drmPanelH, android::sDrmRotationDeg);
    }
    const float drmIdentityMat[4] = {1.0f, 0.0f, 0.0f, 1.0f};

    // Logical->panel "install" matrix (rotation + user flip_h/flip_v), built
    // from the same DRM props the XMB reads so the drastic session honors the
    // panel's orientation and flip correction instead of hard-coding one. The
    // overlay menu draws its geometry straight into the panel FBO like the XMB
    // does, so it uses this matrix directly. The DS layout is composited from a
    // logical-orientation offscreen texture, and sampling a texture inverts one
    // axis versus a direct geometry draw, so its composite matrix is the install
    // matrix with the second column negated (which cancels that inversion -- a
    // pure rotation for a rotated panel, identity for an unrotated one).
    float drmInstallMat[4];
    android::drmBuildInstallMatrix(drmInstallMat, drmEffRot);
    float drmCompositeMat[4] = {
        drmInstallMat[0], drmInstallMat[1], -drmInstallMat[2], -drmInstallMat[3]
    };

    android::drastic_input::InputState input{};
    android::drastic_input::applyPrefs(&input, initialPrefs);
    android::drastic_input::scanInputDevices(&input);
    ALOGI("drastic-nano: found %zu input devices", input.fds.size());

    // Initialize overlay renderer + menu. OverlayGfx binds its own
    // programs; it must run on the same thread/context as drastic.
    // Rotation matrix lives next to the runner's, so pick it up once
    // and update if drastic's rotation ever changes (it doesn't in
    // practice for this binary).
    android::drastic_gfx::OverlayGfx gfx;
    // Overlay viewport size: use primary AHB tex w/h so the logical
    // overlay pixels align with the rotated scanout panel. Fall back
    // to display size if no ring slot is ready yet.
    int overlayW = dpy->width;
    int overlayH = dpy->height;
    if (android::sAhbRingPrimary[0].glFbo != 0) {
        overlayW = android::sAhbRingPrimary[0].w;
        overlayH = android::sAhbRingPrimary[0].h;
    }
    // Default rotation matrix for overlay geometry: the shared sDrmRotMat,
    // correct for non-rotated panels and the dual-panel path. For the single-
    // panel layout, lay the overlay out in the logical (landscape) space and
    // rotate it onto the panel with the install matrix, so its responsive
    // design sees the real on-screen aspect instead of the panel's native
    // portrait dimensions (which stretched it on a rotated panel).
    const float* overlayRotMat = android::sDrmRotMat;
    if (drmSingleLayout) {
        overlayW = drmLogicalW;
        overlayH = drmLogicalH;
        overlayRotMat = drmInstallMat;
    }
    if (!gfx.init(overlayW, overlayH, overlayRotMat)) {
        ALOGW("drastic-nano: OverlayGfx init failed; overlay disabled");
    } else {
        ALOGI("drastic-nano: overlay gfx ready (%dx%d)",
              overlayW, overlayH);
    }

    // Re-lay-out the single-panel output for a new effective rotation (install +
    // live Display Rotation). Only resizes the layout texture + overlay when the
    // logical orientation actually flips, so a no-op frame is cheap. Touch and
    // the render branch read drmLogicalW/H + the matrices, so they follow.
    auto applyDrmRotation = [&](int effRot) {
        const bool rot = (effRot == 90 || effRot == 270);
        const int newLW = rot ? drmPanelH : drmPanelW;
        const int newLH = rot ? drmPanelW : drmPanelH;
        if ((newLW != drmLogicalW || newLH != drmLogicalH) && drmLayoutTex != 0) {
            glBindTexture(GL_TEXTURE_2D, drmLayoutTex);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, newLW, newLH, 0,
                         GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
            glBindTexture(GL_TEXTURE_2D, 0);
        }
        drmEffRot = effRot;
        drmLogicalW = newLW;
        drmLogicalH = newLH;
        drmRotated = rot;
        android::drmBuildInstallMatrix(drmInstallMat, effRot);
        drmCompositeMat[0] = drmInstallMat[0];
        drmCompositeMat[1] = drmInstallMat[1];
        drmCompositeMat[2] = -drmInstallMat[2];
        drmCompositeMat[3] = -drmInstallMat[3];
        gfx.setViewport(drmLogicalW, drmLogicalH);
        gfx.setRotationMatrix(drmInstallMat);
        ALOGI("drastic-nano: display rotation -> eff=%d (logical %dx%d)",
              effRot, drmLogicalW, drmLogicalH);
    };

    android::drastic_overlay::OverlayMenu overlay;
    overlay.init(dr, initialPrefs, appUid, appGid,
                 xmlPath, savestatesDir, romPath, shadersDir);

    // RetroAchievements. Brought up once the core has produced its first frame
    // (so Main RAM is populated). The client runs on its own threads; here we
    // only start it, track pause state for rc_client idle, drain UI events, and
    // read its hardcore state to gate features.
    android::NanoRetroAchievements ra;
    overlay.setRaClient(&ra);
    // No second DS screen for the RA panel unless this is a dual-panel device
    // (RG DS): single-panel devices get the on-screen Achievements drill-in.
    overlay.setSingleScreen(!hasDualDisplay);
    bool raInited = false;
    bool raPrevOverlayOpen = false;

    // Triple-buffered AHB ring: render slot[renderIdx], present
    // slot[renderIdx - 2]. Mirrors the gammaos-nano QR fast-path
    // pacing so the present-side AHB lock observes GPU work
    // submitted ~2 frames earlier and the DRM flip never races an
    // in-progress render. Without this, single-buffering caused
    // visible tearing (observed on RG DS dual DSI 640x480@60).
    bool tripleBuffer = true;
    for (int i = 0; i < android::AHB_RING_DEPTH; i++) {
        if (android::sAhbRingPrimary[i].glFbo == 0 ||
            (hasDualDisplay &&
             android::sAhbRingSecondary[i].glFbo == 0)) {
            tripleBuffer = false;
            ALOGW("drastic-nano: triple_buffer disabled, slot %d "
                  "not fully allocated", i);
            break;
        }
    }
    if (tripleBuffer) {
        android::sRingRenderIdx = 0;
        android::sRingPresentIdx = 0;
        android::sRingPrimedCount = 0;
    }
    ALOGI("drastic-nano: triple_buffer=%d", tripleBuffer ? 1 : 0);

    bool exitRequested = false;
    // Full saturation / no gradient -- drastic-nano has no preview
    // overlay.
    const float saturation = 1.0f;
    const float gradient   = 0.0f;

    // Consumer-side frameskip. drastic-native frameskip is inert on nano
    // (this libdrastic build never reads the frameskip config fields, and
    // our consumer samples the latest slot every vblank regardless), so we
    // honor the user's Frameskip setting here: skip the expensive fxRender
    // upload+shade (renderDsToOffscreen) on N of every (N+1) vblanks while
    // still blitting and page-flipping every vblank to keep the DRM vblank
    // cadence. The DS frame content then updates at the reduced rate (the
    // classic frameskip trade: choppier motion for less GPU work). Read
    // live from the overlay so changes take effect immediately. Auto
    // frameskip is treated as no-skip (nano always runs at full speed, so
    // there is nothing to "catch up").
    int fsCounter = 0;

    // Nano-side screen-swap state. When true, the top DS screen is
    // rendered to the secondary display (or the bottom half of a
    // single panel) and vice versa. Toggled by the Screen Swap action
    // (_KeyMapConfigs_0_17). Drastic has no native equivalent, so we
    // implement it here by swapping which render call goes to which
    // viewport each frame.
    bool screensSwapped = false;

    // audioserver spawns its output thread after our first buffer
    // enqueue. Sweep after 1 s, 3 s, 5 s so we catch it regardless
    // of when drastic's audio engine actually comes up.
    // Audio thread boosting: sweep aggressively during the first 5 s
    // (1 s / 3 s / 5 s) to catch audioserver's output thread as soon
    // as it spawns, then keep sweeping every 30 s for the rest of the
    // session. Drift cause we observed: audioserver respawns its
    // AudioOut_D thread whenever the output route changes (headphone
    // insert, HDMI detect, audio policy reload), and the new thread
    // comes up at SCHED_OTHER nice -19. Our render loop's SCHED_FIFO
    // 80 then starves the playback mixer and frames stretch to drain
    // an undersized audio buffer. A user-visible symptom: drastic-nano
    // becomes choppy after some minutes but feels fresh again right
    // after process restart (because our boot-time sweep catches the
    // current audioserver threads). The periodic resweep is the
    // self-heal for that drift.
    int64_t audioBoostDeadlineMs = android::elapsedRealtime() + 1000;
    int audioBoostSweeps = 0;
    constexpr int kAudioFastSweeps    = 3;
    constexpr int64_t kAudioFastGapMs = 2000;
    constexpr int64_t kAudioSlowGapMs = 30000;

    while (!exitRequested) {
        if (android::elapsedRealtime() >= audioBoostDeadlineMs) {
            boostAudioServer();
            audioBoostSweeps++;
            int64_t gap = (audioBoostSweeps < kAudioFastSweeps)
                            ? kAudioFastGapMs
                            : kAudioSlowGapMs;
            audioBoostDeadlineMs = android::elapsedRealtime() + gap;
        }
        android::drastic_input::InputActions actions{};
        android::drastic_input::pollInputMap(
                &input,
                overlay.isOpen(),
                overlay.isCapturingKey(),
                kBackShortMs, kBackHoldMs, kPowerHoldMs, kPowerOffHoldMs, &actions);
        // Short power press = system sleep. Handled before the overlay
        // update so a sleep press while the menu is open does not also
        // feed the menu; the menu's pause state is preserved across the
        // sleep (doSleep only resumes what it paused itself).
        if (actions.sleepRequested) {
            doSleep(&input, dr, overlay.isOpen());
            continue;
        }
        // Power hold = raise drastic's own in-game overlay menu (the
        // SF gammaos-nano XMB overlay cannot present over a DRM-master
        // game). Feed it through the same toggle short-BACK uses.
        if (actions.xmbOverlayRequested) {
            android::drastic_input::InputActions ov{};
            ov.menuToggle = true;
            overlay.update(ov, &input);
            continue;
        }
        overlay.update(actions, &input);

        // RetroAchievements lifecycle. Start once the first DS frame is ready,
        // mirror the overlay's pause state into the client (so it idles instead
        // of processing frames while the menu is open), and drain achievement UI
        // events for the overlay to draw.
        if (!raInited && dr->isFrameReady()) {
            raInited = true;
            ra.onGameLoaded(dr, romPath);
            // First frame rendered: any slot-9 auto-load succeeded, so clear the
            // crash marker armed before dr.init (slot 9 validation in main). If we
            // crash only AFTER this point the state was good, so it must NOT be
            // quarantined. Harmless no-op when this session did not load slot 9.
            unlink((slot9Stem(savestatesDir, romPath) + ".loading").c_str());
        }
        // One vblank tick per render-loop iteration drives rc_client_do_frame at
        // ~60Hz (the DS frame rate). This replaces a wall-clock pace that
        // under-sampled and missed single-frame achievement triggers.
        if (raInited) ra.onRenderFrame();
        {
            bool ovOpen = overlay.isOpen();
            if (ovOpen != raPrevOverlayOpen) {
                ra.setPaused(ovOpen);
                raPrevOverlayOpen = ovOpen;
            }
        }
        // Drive the live action gates (cheats, save-state loading) off the
        // restrictions signal, which also covers the async login+load window so a
        // save-state load cannot advance the game into an illegitimate state
        // before rc_client begins evaluating it. The Hardcore toggle display in
        // the overlay reads hardcorePref() directly, so this does not mislabel it.
        overlay.setHardcoreActive(ra.hardcoreRestrictionsActive());
        {
            android::RaUiEvent rev;
            while (ra.popUiEvent(&rev)) {
                overlay.onRaUiEvent(rev);
            }
        }

        // Debug: drive a UI-style login from a prop (this platform cannot inject
        // OSK input). Format "user:pass"; fires once then clears the prop. Same
        // entry point the on-screen keyboard uses, so it validates the exact
        // login path (enable RA on demand + start the client). Inert when unset.
        {
            char ld[PROPERTY_VALUE_MAX] = {};
            property_get("persist.gammaos.drastic_nano.ra_login_dbg", ld, "");
            if (ld[0]) {
                std::string s(ld);
                size_t c = s.find(':');
                if (c != std::string::npos && c + 1 < s.size())
                    ra.requestLogin(s.substr(0, c), s.substr(c + 1));
                property_set("persist.gammaos.drastic_nano.ra_login_dbg", "");
            }
        }

        // In-app volume / brightness HUDs (VOL = volume, SELECT+VOL =
        // brightness). The SF system sliders never show on the DRM path.
        if (actions.volAdjust != 0)    overlay.onVolumeAdjust(actions.volAdjust);
        if (actions.brightAdjust != 0) overlay.onBrightnessAdjust(actions.brightAdjust);
        if (actions.exitRequested) {
            ALOGW("drastic-nano: long-press BACK, exiting");
            exitRequested = true;
        }
        if (overlay.exitAppRequested()) {
            ALOGI("drastic-nano: exit requested from overlay menu");
            exitRequested = true;
        }
        if (overlay.relaunchRequested()) {
            ALOGI("drastic-nano: relaunch requested by overlay");
            result.relaunchRequested = true;
            exitRequested = true;
        }
        if (overlay.restartFreshRequested()) {
            ALOGI("drastic-nano: restart-game (fresh relaunch) requested");
            result.restartFresh = true;
            result.relaunchRequested = true;  // reuse the relaunch handshake
            exitRequested = true;
        }
        if (raInited && ra.takeHardcoreRestart()) {
            // Hardcore was just enabled: restart the game fresh into hardcore
            // (RA convention). Reuse the same fresh-relaunch handshake as
            // "Restart Game"; the relaunched process reads ra_hardcore=1 and
            // boots into hardcore. The teardown frees the RA bottom-panel
            // textures first, so the GL/DRM context teardown stays clean.
            ALOGI("drastic-nano: hardcore enabled, restarting fresh into hardcore");
            result.restartFresh = true;
            result.relaunchRequested = true;
            exitRequested = true;
        }
        // Power off / reboot: from the overlay menu rows or the ~5s power-button
        // hold. Break with the power action set so the exit tail saves slot 9
        // (and arms Quick Resume) then powers the device down instead of
        // returning to the XMB.
        if (actions.powerOffRequested || overlay.powerOffRequested()) {
            ALOGI("drastic-nano: power off requested (save + shutdown)");
            result.powerOffAfter = true;
            exitRequested = true;
        }
        if (overlay.rebootRequested()) {
            ALOGI("drastic-nano: reboot requested (save + reboot)");
            result.rebootAfter = true;
            exitRequested = true;
        }
        // External graceful-quit channel: nano / the framework ShutdownThread set
        // sys.gammaos.drastic_nano.quit=1 to ask for a clean save + exit (the
        // caller then issues the power action). A SIGTERM stop at device shutdown
        // breaks here too so slot 9 is saved rather than killed mid-session.
        if (gTermRequested ||
            property_get_bool("sys.gammaos.drastic_nano.quit", false)) {
            property_set("sys.gammaos.drastic_nano.quit", "0");
            ALOGI("drastic-nano: external quit / SIGTERM, saving and exiting");
            result.quitShutdown = true;
            exitRequested = true;
        }
        // Back-hold graceful exit (see the SF loop for the rationale): force the
        // slot-9 save and return to the launcher instead of handing a power action
        // to the caller.
        if (property_get_bool("sys.gammaos.drastic_nano.exit_home", false)) {
            property_set("sys.gammaos.drastic_nano.exit_home", "0");
            ALOGI("drastic-nano: back-hold exit-to-home, saving and returning");
            result.exitToHome = true;
            exitRequested = true;
        }
        if (exitRequested) break;

        // Special action handlers. Fast-forward flips drastic's
        // runtime-only V bit via applyConfig (bit 29). Screen swap
        // toggles our own renderTop/renderBottom routing. Toggle-mic
        // is logged only -- drastic-nano has no mic pipeline today.
        // In RetroAchievements hardcore, fast-forward is disabled (the
        // integration gates it together with cheats, save-state load and
        // auto-resume while hardcore is active). Use the restrictions signal so
        // fast-forward is also blocked during the async login+load window, not
        // just once the game is confirmed loaded.
        dr->setFastForward(ra.hardcoreRestrictionsActive() ? false : actions.actFastFwd);
        if (actions.actSwapScreens) {
            screensSwapped = !screensSwapped;
            ALOGI("drastic-nano: screen swap = %d", screensSwapped);
        }
        // Live Display Rotation: re-lay-out the single-panel output when the user
        // changes it in Video settings. Runs before touch + render so both follow.
        if (drmSingleLayout) {
            char drp[PROPERTY_VALUE_MAX] = {};
            property_get("persist.gammaos.drastic_nano.display_rotate", drp, "0");
            int eff = ((android::sDrmRotationDeg + atoi(drp)) % 360 + 360) % 360;
            if (eff != drmEffRot) applyDrmRotation(eff);
        }
        if (actions.actToggleMic) {
            ALOGI("drastic-nano: toggle-mic action (no mic path)");
        }
        // Forward the possibly-suppressed DS input to drastic. When
        // the overlay is open, pollInputMap zeroes dsBtnMask and
        // touchHeld, leaving the emulator idle until the user closes.
        //
        // Map the stylus to the bottom (touch) DS screen. The input layer
        // collapses the raw panel touch to DS coords across the WHOLE physical
        // panel (touchX/256, touchY/192 = the panel-normalized position). In
        // the dual-panel path that panel IS the bottom screen, so the coords
        // pass through unchanged (RG DS, left untouched). In the single-panel
        // layout the bottom screen occupies only a sub-rect of a possibly-
        // rotated panel, so undo the composite rotation to recover the logical
        // point, then rescale it through the bottom screen's layout rectangle
        // -- the same remap the SF single-window path does, but accounting for
        // the panel rotation so touch and image agree under any layout/flip
        // (including asymmetric big+small).
        int dsTouchX = actions.touchX;
        int dsTouchY = actions.touchY;
        bool dsTouchHeld = actions.touchHeld;
        if (drmSingleLayout && !actions.touchDirect) {
            // The input layer normalizes the raw touch to (touchX/256,
            // touchY/192) across the digitizer's native axes, whose pixel range
            // is read from the evdev node at scan time (input.touchPanelW/H --
            // retrieved from Android, never hard-coded). Handhelds wire the
            // digitizer in the orientation the device is actually used in, so
            // when the digitizer and the logical (layout) space share an
            // orientation the normalized fractions map straight across; when
            // they differ by 90 degrees (e.g. a portrait digitizer under a
            // landscape layout) the axes are swapped. Either way the point is
            // then rescaled through the bottom screen's layout rect, exactly
            // like the SF single-window path, so any preset (incl. big+small)
            // lines up.
            // Digitizer fraction in its own native frame.
            const float fx = actions.touchX / 256.0f;
            const float fy = actions.touchY / 192.0f;
            // The digitizer is wired to the panel's INSTALL (un-rotated) logical
            // frame, so first align the fraction to that frame: when the
            // digitizer and the install-logical space share an orientation the
            // fractions pass straight, otherwise the axes swap (a portrait
            // digitizer under a landscape install, etc). This is the same base
            // the install-only path used; with no Display Rotation it is final.
            const bool digitizerLandscape =
                    (input.touchPanelW >= input.touchPanelH);
            const bool installRot = (android::sDrmRotationDeg == 90 ||
                                     android::sDrmRotationDeg == 270);
            const int instLW = installRot ? drmPanelH : drmPanelW;
            const int instLH = installRot ? drmPanelW : drmPanelH;
            const bool installLandscape = (instLW >= instLH);
            float a, b;
            if (digitizerLandscape == installLandscape) { a = fx; b = fy; }
            else { a = fy; b = fx; }
            // Then turn the touch by the user's Display Rotation (effRot minus
            // the install) so it tracks the rotated image. The content turned by
            // this amount, so the touch turns the same way to recover the point
            // in the now-rotated logical frame. With no rotation (touchRot 0)
            // this is identity, leaving the install-only behaviour unchanged.
            const int touchRot =
                    ((drmEffRot - android::sDrmRotationDeg) % 360 + 360) % 360;
            float p, q;
            switch (touchRot) {
            case 90:  p = b;        q = 1.0f - a; break;
            case 180: p = 1.0f - a; q = 1.0f - b; break;
            case 270: p = 1.0f - b; q = a;        break;
            default:  p = a;        q = b;        break;
            }
            float lx = p * (float)drmLogicalW;
            float ly = q * (float)drmLogicalH;
            drastic_nano::LayoutConfig tc =
                    readSfLayoutConfig(drmLogicalW, drmLogicalH);
            tc.swap = tc.swap ^ screensSwapped;
            drastic_nano::Rect br = drastic_nano::bottomRect(
                    drastic_nano::compute(tc, (uint32_t)drmLogicalW,
                                          (uint32_t)drmLogicalH));
            if (br.w > 0.0f && br.h > 0.0f) {
                if (lx >= br.x && lx < br.x + br.w &&
                    ly >= br.y && ly < br.y + br.h) {
                    dsTouchX = (int)((lx - br.x) / br.w * 256.0f);
                    dsTouchY = (int)((ly - br.y) / br.h * 192.0f);
                    if (dsTouchX < 0)   dsTouchX = 0;
                    if (dsTouchY < 0)   dsTouchY = 0;
                    if (dsTouchX > 255) dsTouchX = 255;
                    if (dsTouchY > 191) dsTouchY = 191;
                } else {
                    dsTouchHeld = false;  // touch outside the bottom screen
                }
            }
        }
        dr->setInputWithTouch(actions.dsBtnMask, dsTouchX, dsTouchY,
                              dsTouchHeld);

        // Consumer-side frameskip (see fsCounter declaration above). Skip
        // the DS upload/shade on N of every (N+1) vblanks; the blit and
        // page-flip below still run every vblank so the panel keeps its
        // cadence and shows the last DS frame until the next render.
        const auto& fsPrefs = overlay.prefs();
        int fsSkip = (fsPrefs.frameskipType == 0) ? fsPrefs.frameskipValue : 0;
        if (fsSkip < 0) fsSkip = 0;
        const bool renderDs = (fsSkip == 0) || (fsCounter % (fsSkip + 1) == 0);
        fsCounter++;
        // The single-panel layout renders the shader per slot, straight into
        // the layout offscreen (renderSlotShaded, below), so it does NOT use
        // the shared stacked offscreen that renderDsToOffscreen fills. Every
        // other path (dual-panel RG DS, fixed stack) still pre-renders here
        // exactly as before -- their render code is left untouched.
        if (renderDs && !drmSingleLayout) dr->renderDsToOffscreen();

        const int renderIdx =
                tripleBuffer ? android::sRingRenderIdx : 0;
        android::AhbRenderTarget& primTgt =
                android::sAhbRingPrimary[renderIdx];
        android::AhbRenderTarget& secTgt =
                android::sAhbRingSecondary[renderIdx];

        if (hasDualDisplay) {
            glBindFramebuffer(GL_FRAMEBUFFER, secTgt.glFbo);
            glViewport(0, 0, (GLsizei)secTgt.w, (GLsizei)secTgt.h);
            if (screensSwapped) {
                dr->renderTopScreen(saturation, gradient);
            } else {
                dr->renderBottomScreen(saturation, gradient);
            }

            glBindFramebuffer(GL_FRAMEBUFFER, primTgt.glFbo);
            if (android::sDrmGlRotation) {
                glViewport(0, 0, (GLsizei)primTgt.w,
                           (GLsizei)primTgt.h);
            } else {
                glViewport(0, 0, dpy->width, dpy->height);
            }
            if (screensSwapped) {
                dr->renderBottomScreen(saturation, gradient);
            } else {
                dr->renderTopScreen(saturation, gradient);
            }
        } else if (drmSingleLayout) {
            // Lay out the DS screens by the advanced_drastic preset into the
            // logical (landscape) offscreen with NO rotation, exactly as the SF
            // single-window path does, then composite that offscreen onto the
            // panel-native FBO rotated by the install matrix. Keeping the layout
            // math in logical space and rotating only the final quad means a
            // rotated portrait panel shows the landscape layout without stretch.
            drastic_nano::LayoutConfig lay = readSfLayoutConfig(drmLogicalW, drmLogicalH);
            lay.swap = lay.swap ^ screensSwapped;
            drastic_nano::LayoutPlan plan =
                    drastic_nano::compute(lay, (uint32_t)drmLogicalW, (uint32_t)drmLogicalH);

            // Render the DS screens into the logical-orientation layout offscreen.
            // Skipped on frameskip vblanks: drmLayoutTex keeps the last frame and
            // is still composited below, so the panel cadence is preserved.
            if (renderDs) {
                glBindFramebuffer(GL_FRAMEBUFFER, drmLayoutFbo);
                glDisable(GL_SCISSOR_TEST);
                glViewport(0, 0, drmLogicalW, drmLogicalH);
                glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                glClear(GL_COLOR_BUFFER_BIT);
                bool sharedOffscreenFilled = false;
                for (int i = 0; i < plan.count; i++) {
                    const drastic_nano::SlotPlan& s = plan.slots[i];
                    const int vx = (int)s.rect.x;
                    const int vy = drmLogicalH - (int)(s.rect.y + s.rect.h);
                    const int vw = (int)s.rect.w;
                    const int vh = (int)s.rect.h;
                    const int which =
                            (s.content == drastic_nano::DsScreen::Top) ? 0 : 1;
                    // Per-slot shader render at the slot's exact size so the
                    // prescale/LCD grid lands on the final pixels (crisp, and
                    // correct for asymmetric big+small slots), matching stock
                    // DraStic. Returns false only when the .dfx path is inactive;
                    // then fall back to the shared-offscreen re-sampled blit (the
                    // old behavior) so output is never blank.
                    if (dr->renderSlotShaded(which, drmLayoutFbo, vx, vy, vw, vh))
                        continue;
                    if (!sharedOffscreenFilled) {
                        dr->renderDsToOffscreen();
                        sharedOffscreenFilled = true;
                    }
                    glBindFramebuffer(GL_FRAMEBUFFER, drmLayoutFbo);
                    glViewport(vx, vy, vw, vh);
                    glEnable(GL_SCISSOR_TEST);
                    glScissor(vx, vy, vw, vh);
                    dr->setRotationMatrix(drmIdentityMat);
                    if (which == 0) dr->renderTopScreen(saturation, gradient);
                    else            dr->renderBottomScreen(saturation, gradient);
                    glDisable(GL_SCISSOR_TEST);
                    dr->setRotationMatrix(android::sDrmRotMat);
                }
                glDisable(GL_SCISSOR_TEST);
            }

            glBindFramebuffer(GL_FRAMEBUFFER, primTgt.glFbo);
            glViewport(0, 0, (GLsizei)primTgt.w, (GLsizei)primTgt.h);
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);
            dr->blitFullTexture(drmLayoutTex, drmCompositeMat);
        } else {
            glBindFramebuffer(GL_FRAMEBUFFER, primTgt.glFbo);
            if (android::sDrmGlRotation) {
                glViewport(0, 0, (GLsizei)primTgt.w,
                           (GLsizei)primTgt.h);
            } else {
                glViewport(0, 0, dpy->width, dpy->height);
            }
            // Single-panel path: renderBothScreens always stacks top
            // on the upper half + bottom on the lower half. No nano-
            // side swap available here without reshuffling the quad
            // layout inside renderBothScreens (the two DS halves come
            // from a single offscreen FBO with a fixed split).
            dr->renderBothScreens(saturation, gradient);
        }

        // Composite the overlay onto the primary AHB tex. Drawing
        // happens even when the menu is closed so toast messages
        // (e.g. from quick save/load hotkeys) still appear.
        glBindFramebuffer(GL_FRAMEBUFFER, primTgt.glFbo);
        if (android::sDrmGlRotation) {
            glViewport(0, 0, (GLsizei)primTgt.w, (GLsizei)primTgt.h);
        } else {
            glViewport(0, 0, dpy->width, dpy->height);
        }
        gfx.beginFrame();
        overlay.draw(gfx);
        if (input.cursorMode && !overlay.isOpen()) {
            drastic_nano::LayoutConfig cc =
                    readSfLayoutConfig(drmLogicalW, drmLogicalH);
            cc.swap = cc.swap ^ screensSwapped;
            drastic_nano::Rect cbr = drastic_nano::bottomRect(
                    drastic_nano::compute(cc, (uint32_t)drmLogicalW,
                                          (uint32_t)drmLogicalH));
            drawTouchCursor(gfx, cbr, input.cursorX, input.cursorY,
                            (input.dsBtnMask & DrasticRunner::kDsBtnA) != 0);
        }
        gfx.endFrame();
        // Debug screenshot: latch the request now (primTgt is bound and holds
        // the DS top screen + overlay), capture the bottom panel after the OSK
        // pass below, then clear the request. This way a single shot grabs both
        // DS panels, including the on-screen keyboard on the bottom screen.
        const bool wantShot = shotRequested();
        if (wantShot)
            captureFboToPpm((int)primTgt.w, (int)primTgt.h,
                            "/data/drastic_nano_shot.ppm");

        // On-screen keyboard: render on the BOTTOM DS panel (secondary FBO)
        // with its own scrim, so it does not cover the cheats menu on the top
        // screen. On a single-panel device there is no separate bottom FBO, so
        // fall back to drawing it over the primary. The keyboard is drawn after
        // the DS frames and the top overlay, before the slot fence.
        { char od[PROPERTY_VALUE_MAX] = {};
          property_get("persist.gammaos.drastic_nano.osk_dbg", od, "0");
          if (od[0] == '1') overlay.debugOpenOsk(); }
        if (overlay.oskActive()) {
            if (hasDualDisplay) {
                glBindFramebuffer(GL_FRAMEBUFFER, secTgt.glFbo);
                glViewport(0, 0, (GLsizei)secTgt.w, (GLsizei)secTgt.h);
                gfx.setViewport((int)secTgt.w, (int)secTgt.h);
                gfx.beginFrame();
                overlay.drawOsk(gfx);
                gfx.endFrame();
                // Restore the primary logical viewport for the next iteration.
                gfx.setViewport((int)primTgt.w, (int)primTgt.h);
            } else {
                glBindFramebuffer(GL_FRAMEBUFFER, primTgt.glFbo);
                // Constrain the keyboard to the bottom (touch) DS screen's rect so
                // it sits on the touch screen and does not cover the top screen,
                // matching the SF single-window path. Under a live Display Rotation
                // the overlay is turned by a matrix and a logical-space glViewport
                // crop would not align, so fall back to the full panel there.
                drastic_nano::Rect obr{};
                if (!android::sDrmGlRotation) {
                    // Stacked-layout bottom rect so the keyboard takes the bottom
                    // half, never the whole panel (matches the SF path above).
                    drastic_nano::LayoutConfig oc;
                    oc.orient = drastic_nano::Orientation::Vertical;
                    oc.scaling = drastic_nano::Scaling::Stretch;
                    oc.swap = false;
                    obr = drastic_nano::bottomRect(drastic_nano::compute(
                            oc, (uint32_t)drmLogicalW, (uint32_t)drmLogicalH));
                }
                if (obr.w > 0.0f && obr.h > 0.0f) {
                    glViewport((int)obr.x, drmLogicalH - (int)(obr.y + obr.h),
                               (int)obr.w, (int)obr.h);
                    gfx.setViewport((int)obr.w, (int)obr.h);
                } else {
                    glViewport(0, 0, dpy->width, dpy->height);
                }
                gfx.beginFrame();
                overlay.drawOsk(gfx);
                gfx.endFrame();
                gfx.setViewport(drmLogicalW, drmLogicalH);   // restore logical
            }
        }

        // RetroAchievements detail + leaderboards on the BOTTOM DS panel while
        // the Achievements section is open (keyboard takes priority above).
        // Dual-panel only: the bottom panel is a real second screen there.
        if (!overlay.oskActive() && hasDualDisplay && overlay.wantsRaBottomPanel()) {
            glBindFramebuffer(GL_FRAMEBUFFER, secTgt.glFbo);
            glViewport(0, 0, (GLsizei)secTgt.w, (GLsizei)secTgt.h);
            gfx.setViewport((int)secTgt.w, (int)secTgt.h);
            gfx.beginFrame();
            overlay.drawRaBottomPanel(gfx);
            gfx.endFrame();
            gfx.setViewport((int)primTgt.w, (int)primTgt.h);
        } else if (!overlay.oskActive() && hasDualDisplay && overlay.isOpen()) {
            // Any other overlay section: dim the bottom DS panel with a scrim so
            // the paused game reads as "the menu is open", matching the keyboard
            // and Achievements passes.
            glBindFramebuffer(GL_FRAMEBUFFER, secTgt.glFbo);
            glViewport(0, 0, (GLsizei)secTgt.w, (GLsizei)secTgt.h);
            gfx.setViewport((int)secTgt.w, (int)secTgt.h);
            gfx.beginFrame();
            overlay.drawBottomScrim(gfx);
            gfx.endFrame();
            gfx.setViewport((int)primTgt.w, (int)primTgt.h);
        }

        // Debug screenshot: bottom panel (secondary AHB slot = DS bottom screen
        // plus the on-screen keyboard when active), then clear the request.
        if (wantShot) {
            if (hasDualDisplay) {
                glBindFramebuffer(GL_FRAMEBUFFER, secTgt.glFbo);
                captureFboToPpm((int)secTgt.w, (int)secTgt.h,
                                "/data/drastic_nano_shot_bot.ppm");
            }
            property_set("sys.gammaos.drastic_nano.shot", "0");
        }

        if (tripleBuffer) {
            // Unbind before fence-create so the kick point is
            // unambiguous. eglCreateSyncKHR with NATIVE_FENCE
            // flushes implicitly, so no glFlush is needed -- the
            // fence IS the kick-and-record. When the slot rotates
            // back in at presentIdx, drmFlipRingSlot dup's the
            // fd and waits on it via AHardwareBuffer_lock, which
            // sidesteps the global glFinish that single-buffer
            // mode relies on (and which is the tearing source).
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            if (android::sEglCreateSyncKHR &&
                android::sRingEglDpy != EGL_NO_DISPLAY) {
                if (android::sAhbRingSyncPrimary[renderIdx] !=
                        EGL_NO_SYNC_KHR &&
                    android::sEglDestroySyncKHR) {
                    android::sEglDestroySyncKHR(
                            android::sRingEglDpy,
                            android::sAhbRingSyncPrimary[renderIdx]);
                }
                android::sAhbRingSyncPrimary[renderIdx] =
                        android::sEglCreateSyncKHR(
                                android::sRingEglDpy,
                                EGL_SYNC_NATIVE_FENCE_ANDROID,
                                nullptr);
                if (android::sAhbRingSyncPrimary[renderIdx] ==
                        EGL_NO_SYNC_KHR) {
                    // Fence creation failed -- fall back to
                    // flush; drmFlipRingSlot's glFinish fallback
                    // then serializes.
                    glFlush();
                }
            } else {
                glFlush();
            }
            android::sRingRenderIdx =
                    (renderIdx + 1) % android::AHB_RING_DEPTH;
            // Bootstrap: first two iterations render only, don't
            // present. Once primed (>= 2 slots rendered), flip the
            // slot we rendered ~2 frames ago. Present-lag = 2 plus
            // ring depth = 5 leaves enough headroom for both baseline
            // pacing (1 slot free) and Frame Sync's delayed-secondary
            // mode (secondary scanning out an older slot adds one
            // more "live" entry to the working set).
            if (android::sRingPrimedCount >= 2) {
                const int presentIdx = android::sRingPresentIdx;
                android::drmFlipRingSlot(presentIdx, false);
                android::sRingPresentIdx =
                        (presentIdx + 1) % android::AHB_RING_DEPTH;
            } else {
                android::sRingPrimedCount++;
            }
        } else {
            android::drmFrameEnd(dpy->eglDpy, dpy->eglSurf);
        }

        if (android::sDrmFd >= 0 && !android::sDrmDisplays.empty() &&
            !android::sDrmVblankBroken && android::sDrmDisplays.size() <= 1) {
            const int64_t vblT0 = android::elapsedRealtimeNano();
            union drm_wait_vblank vbl = {};
            vbl.request.type = (enum drm_vblank_seq_type)(
                    _DRM_VBLANK_RELATIVE
                    | ((android::sDrmPrimaryIdx & 0x1f)
                       << _DRM_VBLANK_HIGH_CRTC_SHIFT));
            vbl.request.sequence = 1;
            ioctl(android::sDrmFd, DRM_IOCTL_WAIT_VBLANK, &vbl);
            // A DSI command-mode panel (phone-class AMOLED) raises no periodic
            // vblank, so this ioctl blocks until its multi-second timeout and
            // collapses output to well under 1 fps. Detect that the first time
            // and switch to page-flip-event pacing (drmDrainPageFlipEvents below)
            // from then on, the same fallback the home's render loop uses.
            const int64_t vblNs = android::elapsedRealtimeNano() - vblT0;
            if (vblNs > 100000000LL) {   // 100 ms
                android::sDrmVblankBroken = true;
                ALOGW("drastic-nano: DRM_IOCTL_WAIT_VBLANK took %lld ms -- "
                      "switching to page-flip-event pacing",
                      (long long)(vblNs / 1000000LL));
            }
        }
        android::drmDrainPageFlipEvents();
    }

    // Stop the RetroAchievements client (joins its threads) before the runner
    // and overlay tear down, since its threads read the runner's memory.
    ra.shutdown();
    overlay.close();
    // Free the overlay's RA badge/banner GL textures while the context is still
    // current and idle, so the GL/DRM teardown below has no outstanding GPU
    // resources to release (an outstanding bottom-panel texture set wedged the
    // GPU driver during a relaunch teardown).
    overlay.freeRaTextures(gfx);
    gfx.shutdown();
    android::drastic_input::closeInputDevices(&input);
    return result;
}

// Reads the single-view layout choice from the layout properties. The values
// mirror the advanced_drastic naming so the menu, the property, and the upstream
// build all line up. "auto" orientation resolves to horizontal on a landscape
// surface and vertical on a portrait one, which reproduces the per-device
// defaults; runLoopSf does that resolve once it knows the window size.
// Reads the live layout choice from the persisted properties, resolving the
// "auto" orientation against the surface aspect. Cheap enough (three property
// reads) to call once per frame so the in-game Screen Layout menu applies its
// changes without a relaunch.
static drastic_nano::LayoutConfig readSfLayoutConfig(int surfaceW, int surfaceH) {
    drastic_nano::LayoutConfig cfg;
    char buf[PROPERTY_VALUE_MAX] = {};

    property_get("persist.gammaos.drastic_nano.orientation", buf, "auto");
    if (!strcmp(buf, "horizontal"))    cfg.orient = drastic_nano::Orientation::Horizontal;
    else if (!strcmp(buf, "vertical")) cfg.orient = drastic_nano::Orientation::Vertical;
    else if (!strcmp(buf, "single"))   cfg.orient = drastic_nano::Orientation::Single;
    else cfg.orient = (surfaceW >= surfaceH) ? drastic_nano::Orientation::Horizontal
                                             : drastic_nano::Orientation::Vertical;  // auto

    property_get("persist.gammaos.drastic_nano.scaling", buf, "stretch");
    if (!strcmp(buf, "none"))        cfg.scaling = drastic_nano::Scaling::None;
    else if (!strcmp(buf, "1x2x"))   cfg.scaling = drastic_nano::Scaling::S1x2x;
    else if (!strcmp(buf, "2x1x"))   cfg.scaling = drastic_nano::Scaling::S2x1x;
    else                             cfg.scaling = drastic_nano::Scaling::Stretch;

    cfg.swap = property_get_bool("persist.gammaos.drastic_nano.swap", false);

    // Screen gap: stored as a percent (0..50) of the leading screen's stacking
    // dimension, applied between the two screens in any two-screen layout.
    property_get("persist.gammaos.drastic_nano.screen_gap", buf, "0");
    int gapPct = atoi(buf);
    if (gapPct < 0) gapPct = 0; else if (gapPct > 50) gapPct = 50;
    cfg.gap = (float)gapPct / 100.0f;

    // Predetermined handheld layout preset. -1 (default) keeps the parametric
    // orientation/scaling above; 0..presetCount-1 selects a preset that overrides
    // them (Full Screen, Side by Side, PiP, Big+Small, Stacked, ...).
    property_get("persist.gammaos.drastic_nano.layout_preset", buf, "-1");
    int presetIdx = atoi(buf);
    if (presetIdx >= 0 && presetIdx < drastic_nano::presetCount()) cfg.preset = presetIdx;

    // PiP inset opacity (percent, 0..100) for the picture-in-picture presets, so
    // the big screen shows through the overlapping inset. Default 100 (opaque).
    property_get("persist.gammaos.drastic_nano.pip_alpha", buf, "100");
    int pipPct = atoi(buf);
    if (pipPct < 0) pipPct = 0; else if (pipPct > 100) pipPct = 100;
    cfg.pipAlpha = (float)pipPct / 100.0f;

    // Which corner the overlapping picture-in-picture inset sits in. Default
    // "br" (bottom-right, the original placement). Only affects the PiP presets
    // when the inset overlaps the big screen (a 4:3 / portrait panel).
    property_get("persist.gammaos.drastic_nano.pip_corner", buf, "br");
    if      (!strcmp(buf, "bl")) cfg.pipCorner = drastic_nano::PipCorner::BottomLeft;
    else if (!strcmp(buf, "tr")) cfg.pipCorner = drastic_nano::PipCorner::TopRight;
    else if (!strcmp(buf, "tl")) cfg.pipCorner = drastic_nano::PipCorner::TopLeft;
    else                         cfg.pipCorner = drastic_nano::PipCorner::BottomRight;
    return cfg;
}

// SurfaceFlinger render loop. This is the twin of runLoop above for the SF
// backend: it reuses every shared piece (the DraStic core, input, overlay, OSK,
// RetroAchievements, sleep) and differs only in how a finished frame reaches the
// panel. A single-display session composites both DS screens into one window
// through the advanced_drastic layout presets; a dual-display session draws one
// DS screen per window. runLoop above stays untouched, so the DRM path is
// unaffected by this code.
RunLoopResult runLoopSf(drastic_nano::IDisplayBackend* backend,
                        DrasticRunner* dr,
                        const android::drastic_prefs::Prefs& initialPrefs,
                        uid_t appUid, gid_t appGid,
                        const std::string& xmlPath,
                        const std::string& savestatesDir,
                        const std::string& romPath,
                        const std::string& shadersDir) {
    RunLoopResult result{false, false, false, false, false};

    uint32_t pw = 0, ph = 0;
    backend->primarySize(&pw, &ph);
    const int W = (int)pw, H = (int)ph;
    const bool dual = backend->hasSecondary();

    // The DS offscreen carries both screens; a dual session sizes it for two full
    // screens, a single session for the one window. The layout quads then sample
    // the matching half into each slot.
    dr->initSurface(W, H, dual);
    float ident[4] = {1.0f, 0.0f, 0.0f, 1.0f};   // SF layer is display-space
    dr->setRotationMatrix(ident);

    // Single-window layout offscreen. renderSlotShaded MUST target a non-zero
    // FBO: the .dfx shader's final-pass redirect (patchFinalPassFbo) treats FBO 0
    // as "no target" and skips, so rendering the shaded slots straight to the
    // window made renderSlotShaded return false every frame and the path fell back
    // to the blurry re-sampled renderTopScreen blit -- the .dfx shaders (prescale/
    // LCD/scanline) never applied. So render the shaded slots into this offscreen,
    // then blit it to the window. Mirrors the DRM single-panel path (drmLayoutFbo).
    GLuint sfLayoutFbo = 0, sfLayoutTex = 0;
    // Display Rotation (persist.gammaos.drastic_nano.display_rotate, 0/90/180/270)
    // for portrait play, the SF analog of the DRM single-panel path. The game,
    // overlay and OSK are all rendered into a LOGICAL-orientation offscreen, then
    // the whole offscreen is blitted to the window rotated, so everything turns
    // together with no per-element rotation math. The logical dims swap for the
    // quarter turns (a portrait offscreen rotated 90 fills the landscape window).
    // SurfaceFlinger still owns the physical panel rotation; this is the extra,
    // user-chosen rotation on top.
    int   sfRot  = 0;            // current display_rotate
    int   sfLogW = W, sfLogH = H;
    // Blit matrix = rotate(sfRot) composed with the texture-sampling Y-flip
    // ([1,0,0,-1] at 0deg, validated). Recomputed by applySfRotation.
    float sfBlitMat[4] = {1.0f, 0.0f, 0.0f, -1.0f};
    auto sfReadRotate = []() -> int {
        char v[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.drastic_nano.display_rotate", v, "0");
        int r = atoi(v);
        return ((r % 360) + 360) % 360;
    };
    if (backend->composeMode() == drastic_nano::ComposeMode::kLayoutPreset) {
        glGenTextures(1, &sfLayoutTex);
        glGenFramebuffers(1, &sfLayoutFbo);
    }
    // (Re)allocate the offscreen for the logical dims of `rot` and set the blit
    // matrix. Called once up front and again whenever display_rotate changes.
    auto applySfRotation = [&](int rot) {
        sfRot  = rot;
        sfLogW = (rot == 90 || rot == 270) ? H : W;
        sfLogH = (rot == 90 || rot == 270) ? W : H;
        if (sfLayoutTex) {
            glBindTexture(GL_TEXTURE_2D, sfLayoutTex);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, sfLogW, sfLogH, 0, GL_RGBA,
                         GL_UNSIGNED_BYTE, nullptr);
            // NEAREST: the offscreen->window blit is 1:1 (or a quarter turn), so
            // bilinear only smears the integer-scaled DS pixels (the "blurry at
            // 2x" the user saw). NEAREST keeps integer scaling crisp.
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glBindFramebuffer(GL_FRAMEBUFFER, sfLayoutFbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                                   GL_TEXTURE_2D, sfLayoutTex, 0);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
        }
        // rotate(rot) * Yflip, column-major [m0,m1,m2,m3] = [[m0,m2],[m1,m3]].
        switch (rot) {
        case 90:  sfBlitMat[0]= 0; sfBlitMat[1]= 1; sfBlitMat[2]= 1; sfBlitMat[3]= 0; break;
        case 180: sfBlitMat[0]=-1; sfBlitMat[1]= 0; sfBlitMat[2]= 0; sfBlitMat[3]= 1; break;
        case 270: sfBlitMat[0]= 0; sfBlitMat[1]=-1; sfBlitMat[2]=-1; sfBlitMat[3]= 0; break;
        default:  sfBlitMat[0]= 1; sfBlitMat[1]= 0; sfBlitMat[2]= 0; sfBlitMat[3]=-1; break;
        }
        ALOGI("drastic-nano: SF display_rotate=%d, logical %dx%d", rot, sfLogW, sfLogH);
    };
    if (sfLayoutTex) applySfRotation(sfReadRotate());

    // The layout choice, re-read each frame so the in-game Screen Layout menu
    // applies live. Seeded here for any pre-loop reference.
    drastic_nano::LayoutConfig layout = readSfLayoutConfig(W, H);

    android::drastic_input::InputState input{};
    android::drastic_input::applyPrefs(&input, initialPrefs);
    // SF runs as a normal foreground app: PhoneWindowManager owns the power
    // gestures (short = sleep, hold = overlay show/hide), so do not open or read
    // the power node here. The framework handles power exactly as it does for any
    // app once this session presents as app-foreground (see app_launched below).
    input.admitPowerKey = false;
    android::drastic_input::scanInputDevices(&input);
    ALOGI("drastic-nano: SF loop %dx%d, %d display(s), found %zu input devices",
          W, H, backend->displayCount(), input.fds.size());

    android::drastic_gfx::OverlayGfx gfx;
    if (!gfx.init(W, H, ident)) {
        ALOGW("drastic-nano: SF OverlayGfx init failed; overlay disabled");
    }

    android::drastic_overlay::OverlayMenu overlay;
    overlay.init(dr, initialPrefs, appUid, appGid,
                 xmlPath, savestatesDir, romPath, shadersDir);

    android::NanoRetroAchievements ra;
    overlay.setRaClient(&ra);
    // The SF single-window path has no second screen for RA, so use the
    // on-screen Achievements drill-in here too.
    overlay.setSingleScreen(true);
    bool raInited = false;
    bool raPrevOverlayOpen = false;

    const float saturation = 1.0f;
    const float gradient   = 0.0f;
    int  fsCounter = 0;
    bool screensSwapped = false;

    // Lightweight present-rate log: count presents and report the measured FPS
    // every ~2 seconds. SurfaceFlinger keeps no latency stats for a
    // device-composited layer, so this is the throughput signal on the SF path.
    // Negligible overhead.
    int64_t fpsWindowStartMs = android::elapsedRealtime();
    int     fpsFrameCount    = 0;

    int64_t audioBoostDeadlineMs = android::elapsedRealtime() + 1000;
    int audioBoostSweeps = 0;
    constexpr int kAudioFastSweeps    = 3;
    constexpr int64_t kAudioFastGapMs = 2000;
    constexpr int64_t kAudioSlowGapMs = 30000;

    // The SF host activity shows a "Loading..." splash from launch until the
    // first frame reaches the panel; the cold start (dlopen libdrastic + ROM/
    // savestate load) is a few seconds of otherwise-blank surface. We raise this
    // signal the instant the first present lands so the activity can drop the
    // splash exactly when there is something to show.
    bool firstPresented = false;
    bool exitRequested = false;
    while (!exitRequested) {
        const int64_t _frameStartNs = android::elapsedRealtimeNano();
        // Pick up live Screen Layout menu changes (orientation / scaling / swap).
        layout = readSfLayoutConfig(W, H);
        if (android::elapsedRealtime() >= audioBoostDeadlineMs) {
            boostAudioServer();
            audioBoostSweeps++;
            audioBoostDeadlineMs = android::elapsedRealtime() +
                    ((audioBoostSweeps < kAudioFastSweeps) ? kAudioFastGapMs : kAudioSlowGapMs);
        }

        // Keep this drastic-SF session presenting to the framework as a normal
        // foreground app. The framework sets sys.gammaos.nano.app_launched=1 when
        // it launches the DrasticSf host, but clears it once that thin host
        // activity goes STOPPED behind the takeover by this separate drastic-nano
        // renderer. While app_launched != 1, PhoneWindowManager swallows the power
        // key in interceptKeyBeforeQueueing and the resident gammaos-nano-overlay
        // service is never (re)started, so the power button appears dead. Re-assert
        // it every frame, guarded so it is a no-op once stable and does not churn
        // the overlay init trigger. This disarms the swallow and lets the framework
        // own power exactly as it does for any app: short press = sleep, power hold
        // = overlay show/hide.
        if (!property_get_bool("sys.gammaos.nano.app_launched", false)) {
            property_set("sys.gammaos.nano.app_launched", "1");
        }

        // The framework XMB overlay (a power-hold in SF mode) sets
        // sys.gammaos.nano.drop_input while it is shown so foreground apps stop
        // acting on input. That isolation is enforced by InputDispatcher, which only
        // covers apps that receive input through the framework -- drastic-nano reads
        // the evdev nodes directly, so the drop never reaches it and the DS game
        // keeps responding to the very dpad/buttons the user is navigating the
        // overlay with. Honor the same contract ourselves: while drop_input is set,
        // feed pollInputMap overlayOpen=true (it zeroes the DS button mask + touch)
        // and then discard every resulting action so drastic neither drives its own
        // menu nor exits/toggles behind the framework overlay. The game keeps
        // rendering (the overlay is translucent over the live app) but ignores input
        // until the overlay hides and clears drop_input. In a real launch drop_input
        // is 0 during play (the overlay's hide() clears it), so this only takes
        // effect while the framework overlay is actually up.
        const bool fwOverlayInput =
                property_get_bool("sys.gammaos.nano.drop_input", false);
        android::drastic_input::InputActions actions{};
        android::drastic_input::pollInputMap(
                &input, overlay.isOpen() || fwOverlayInput, overlay.isCapturingKey(),
                kBackShortMs, kBackHoldMs, kPowerHoldMs, kPowerOffHoldMs, &actions);
        {
            static bool sInputSuppressed = false;
            const bool suppress = fwOverlayInput && !overlay.isOpen();
            if (suppress != sInputSuppressed) {
                ALOGI("drastic-nano: DS input %s (framework overlay drop_input=%d)",
                      suppress ? "suppressed" : "restored", fwOverlayInput ? 1 : 0);
                sInputSuppressed = suppress;
            }
            if (suppress) actions = android::drastic_input::InputActions{};
        }

        // SF does NOT capture the power button: the power node is not opened
        // (admitPowerKey=false above), so pollInputMap never produces power actions
        // here and PhoneWindowManager owns every power gesture, just like for any
        // app. These stay as a defensive no-op for a multi-key device that also
        // happens to report KEY_POWER.
        (void)actions.sleepRequested;
        (void)actions.xmbOverlayRequested;
        // Automation hook: sys.gammaos.drastic_nano.menu=1 toggles the overlay
        // once, then clears the property. Lets a screenshot or test session
        // raise the in-game menu without a physical button, mirroring the
        // sys.gammaos.drastic_nano.shot capture trigger.
        if (property_get_bool("sys.gammaos.drastic_nano.menu", false)) {
            property_set("sys.gammaos.drastic_nano.menu", "0");
            actions.menuToggle = true;
        }
        overlay.update(actions, &input);

        if (!raInited && dr->isFrameReady()) {
            raInited = true;
            ra.onGameLoaded(dr, romPath);
            // First frame rendered: any slot-9 auto-load succeeded, so clear the
            // crash marker armed before dr.init (slot 9 validation in main). If we
            // crash only AFTER this point the state was good, so it must NOT be
            // quarantined. Harmless no-op when this session did not load slot 9.
            unlink((slot9Stem(savestatesDir, romPath) + ".loading").c_str());
        }
        if (raInited) ra.onRenderFrame();
        {
            bool ovOpen = overlay.isOpen();
            if (ovOpen != raPrevOverlayOpen) {
                ra.setPaused(ovOpen);
                raPrevOverlayOpen = ovOpen;
            }
        }
        overlay.setHardcoreActive(ra.hardcoreRestrictionsActive());
        {
            android::RaUiEvent rev;
            while (ra.popUiEvent(&rev)) overlay.onRaUiEvent(rev);
        }

        // Debug: drive a UI-style login from a prop (mirrors the DRM loop). Format
        // "user:pass"; fires once then clears the prop. Validates the exact login
        // path on the SF backend (the path the Brick always uses). Inert when unset.
        {
            char ld[PROPERTY_VALUE_MAX] = {};
            property_get("persist.gammaos.drastic_nano.ra_login_dbg", ld, "");
            if (ld[0]) {
                std::string s(ld);
                size_t c = s.find(':');
                if (c != std::string::npos && c + 1 < s.size())
                    ra.requestLogin(s.substr(0, c), s.substr(c + 1));
                property_set("persist.gammaos.drastic_nano.ra_login_dbg", "");
            }
        }

        // Volume + brightness HUDs. The framework's own NanoVolume window renders
        // BEHIND our own-layer SF surface (we composite on top of it), so it is
        // hidden and we must draw the slider ourselves -- but from the SYSTEM volume
        // (persist.gammaos.nano.volume, what PhoneWindowManager changes from the same
        // shared VOL keys), not the DS core's internal mixer. See
        // OverlayMenu::adjustVolume; the DS mixer is pinned at max in main so the
        // system volume is the single control.
        if (actions.volAdjust != 0)    overlay.onVolumeAdjust(actions.volAdjust);
        if (actions.brightAdjust != 0) overlay.onBrightnessAdjust(actions.brightAdjust);
        if (actions.exitRequested)        exitRequested = true;
        if (overlay.exitAppRequested())   exitRequested = true;
        if (overlay.relaunchRequested()) { result.relaunchRequested = true; exitRequested = true; }
        if (overlay.restartFreshRequested()) {
            result.restartFresh = true; result.relaunchRequested = true; exitRequested = true;
        }
        if (raInited && ra.takeHardcoreRestart()) {
            result.restartFresh = true; result.relaunchRequested = true; exitRequested = true;
        }
        // Power off / reboot from the overlay menu rows (SF does not open the
        // power evdev node, so actions.powerOffRequested never fires here; the
        // physical power gestures are owned by PhoneWindowManager).
        if (overlay.powerOffRequested()) { result.powerOffAfter = true; exitRequested = true; }
        if (overlay.rebootRequested())   { result.rebootAfter = true;   exitRequested = true; }
        // External graceful-quit channel: nano's prepareShutdown (Quick Menu
        // Power off / Reboot) or the framework ShutdownThread set
        // sys.gammaos.drastic_nano.quit=1 and then wait for session_done before
        // issuing the power action. SIGTERM at device shutdown breaks here too.
        if (gTermRequested ||
            property_get_bool("sys.gammaos.drastic_nano.quit", false)) {
            property_set("sys.gammaos.drastic_nano.quit", "0");
            ALOGI("drastic-nano: external quit / SIGTERM, saving and exiting (SF)");
            result.quitShutdown = true;
            exitRequested = true;
        }
        // Back-hold graceful exit. In SF mode drastic-nano is an own-layer surface,
        // not a focusable Activity, so PhoneWindowManager cannot deliver a virtual
        // ESC to it the way it does for RetroArch; instead its backLongPress sets
        // this prop for us. Force the slot-9 save and return to the launcher (unlike
        // the reboot/quit channels, which hand the power action to the caller).
        if (property_get_bool("sys.gammaos.drastic_nano.exit_home", false)) {
            property_set("sys.gammaos.drastic_nano.exit_home", "0");
            ALOGI("drastic-nano: back-hold exit-to-home, saving and returning (SF)");
            result.exitToHome = true;
            exitRequested = true;
        }
        if (exitRequested) break;

        dr->setFastForward(ra.hardcoreRestrictionsActive() ? false : actions.actFastFwd);
        if (actions.actSwapScreens) screensSwapped = !screensSwapped;

        // Touch maps to the bottom (touch) DS screen. In dual mode the touch
        // panel already is the bottom screen, so the input layer's coordinates
        // are correct as-is. In a single window the touch panel covers the whole
        // surface, so a touch is rescaled through the bottom screen's layout
        // rectangle: the input layer reports a panel-normalized position
        // (touchX/256, touchY/192), which maps to a window pixel, and only a
        // window pixel inside the bottom rect counts, rescaled into 256x192.
        int dsTouchX = actions.touchX;
        int dsTouchY = actions.touchY;
        bool dsTouchHeld = actions.touchHeld;
        if (backend->composeMode() == drastic_nano::ComposeMode::kLayoutPreset &&
            !actions.touchDirect) {
            // Map at the LOGICAL dims (which swap under a quarter-turn display
            // rotation), and rotate the window touch fraction into logical space
            // first so a touch lands on the right DS pixel after the display turns.
            // Same inverse-rotation family the DRM single-panel touch map uses.
            drastic_nano::LayoutConfig fc = readSfLayoutConfig(sfLogW, sfLogH);
            fc.swap = fc.swap ^ screensSwapped;
            drastic_nano::Rect br = drastic_nano::bottomRect(
                    drastic_nano::compute(fc, (uint32_t)sfLogW, (uint32_t)sfLogH));
            if (br.w > 0.0f && br.h > 0.0f) {
                const float a = actions.touchX / 256.0f;   // window fraction
                const float b = actions.touchY / 192.0f;
                float la = a, lb = b;                       // -> logical fraction
                switch (sfRot) {
                case 90:  la = b;        lb = 1.0f - a; break;
                case 180: la = 1.0f - a; lb = 1.0f - b; break;
                case 270: la = 1.0f - b; lb = a;        break;
                default:  la = a;        lb = b;        break;
                }
                const float wx = la * (float)sfLogW;
                const float wy = lb * (float)sfLogH;
                if (wx >= br.x && wx < br.x + br.w && wy >= br.y && wy < br.y + br.h) {
                    dsTouchX = (int)((wx - br.x) / br.w * 256.0f);
                    dsTouchY = (int)((wy - br.y) / br.h * 192.0f);
                    if (dsTouchX > 255) dsTouchX = 255;
                    if (dsTouchY > 191) dsTouchY = 191;
                } else {
                    dsTouchHeld = false;   // touch outside the bottom screen
                }
            }
        }
        dr->setInputWithTouch(actions.dsBtnMask, dsTouchX, dsTouchY, dsTouchHeld);

        {
            const auto& lp = overlay.prefs();
            int fsSkip = (lp.frameskipType == 0) ? lp.frameskipValue : 0;
            if (fsSkip < 0) fsSkip = 0;
            bool renderDs = (fsSkip == 0) || (fsCounter % (fsSkip + 1) == 0);
            fsCounter++;
            // The layout-preset path renders the shader per slot (renderSlotShaded)
            // and fills the shared offscreen only on its fallback, so skip the
            // pre-render there. The dual-target path still needs it pre-filled.
            if (renderDs &&
                backend->composeMode() != drastic_nano::ComposeMode::kLayoutPreset)
                dr->renderDsToOffscreen();
        }

        drastic_nano::FrameTargets ft = backend->acquireFrameTargets();

        if (backend->composeMode() == drastic_nano::ComposeMode::kDualTarget) {
            // One DS screen per window, mirroring the DRM dual branch.
            backend->bindSecondary();
            glViewport(0, 0, (GLsizei)ft.secondaryW, (GLsizei)ft.secondaryH);
            if (screensSwapped) dr->renderTopScreen(saturation, gradient);
            else                dr->renderBottomScreen(saturation, gradient);

            backend->bindPrimary();
            glViewport(0, 0, (GLsizei)ft.primaryW, (GLsizei)ft.primaryH);
            if (screensSwapped) dr->renderBottomScreen(saturation, gradient);
            else                dr->renderTopScreen(saturation, gradient);
        } else {
            // Both DS screens in one window, placed by the layout preset. The
            // runtime swap toggle flips which screen leads on top of the property.
            // Render the shaded slots into the layout OFFSCREEN (renderSlotShaded
            // needs a non-zero FBO; see sfLayoutFbo setup above), then blit the
            // offscreen to the window. This is what makes the .dfx shaders apply in
            // SF mode; before this they fell back to the re-sampled blit.
            //
            // Display Rotation: a live display_rotate change re-sizes the offscreen
            // to the LOGICAL orientation and sets the blit matrix. The layout is
            // computed at the logical dims (so a quarter turn lays the screens out
            // for portrait), rendered into the offscreen, then blitted to the window
            // turned by the matrix.
            { int wantRot = sfReadRotate(); if (wantRot != sfRot) applySfRotation(wantRot); }

            drastic_nano::LayoutConfig frameCfg = readSfLayoutConfig(sfLogW, sfLogH);
            frameCfg.swap = frameCfg.swap ^ screensSwapped;
            drastic_nano::LayoutPlan plan =
                    drastic_nano::compute(frameCfg, (uint32_t)sfLogW, (uint32_t)sfLogH);

            glBindFramebuffer(GL_FRAMEBUFFER, sfLayoutFbo);
            glDisable(GL_SCISSOR_TEST);
            glViewport(0, 0, sfLogW, sfLogH);
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);
            bool sharedOffscreenFilled = false;
            for (int i = 0; i < plan.count; i++) {
                const drastic_nano::SlotPlan& s = plan.slots[i];
                const int vx = (int)s.rect.x;
                const int vy = sfLogH - (int)(s.rect.y + s.rect.h);  // top-left -> GL bottom-left
                const int vw = (int)s.rect.w;
                const int vh = (int)s.rect.h;
                const int which =
                        (s.content == drastic_nano::DsScreen::Top) ? 0 : 1;
                // Translucent PiP inset: blend this slot over the big screen already
                // in the offscreen using a constant alpha (the DS frame writes
                // alpha=1, so constant-alpha blending is what makes it see-through).
                const bool blend = (s.alpha < 0.999f);
                if (blend) {
                    glEnable(GL_BLEND);
                    glBlendColor(0.0f, 0.0f, 0.0f, s.alpha);
                    glBlendFunc(GL_CONSTANT_ALPHA, GL_ONE_MINUS_CONSTANT_ALPHA);
                }
                // Per-slot shader render at the slot's exact size into the layout
                // offscreen, so the prescale/LCD grid lands on the final pixels
                // (crisp, correct for asymmetric big+small slots), matching stock
                // DraStic and the DRM single-panel path. Returns false only when
                // the .dfx path is inactive; then fall back to the shared-offscreen
                // re-sampled blit so output is never blank.
                if (!dr->renderSlotShaded(which, sfLayoutFbo, vx, vy, vw, vh)) {
                    if (!sharedOffscreenFilled) {
                        dr->renderDsToOffscreen();
                        sharedOffscreenFilled = true;
                    }
                    glBindFramebuffer(GL_FRAMEBUFFER, sfLayoutFbo);
                    glViewport(vx, vy, vw, vh);
                    glEnable(GL_SCISSOR_TEST);
                    glScissor(vx, vy, vw, vh);
                    dr->setRotationMatrix(ident);
                    if (which == 0) dr->renderTopScreen(saturation, gradient);
                    else            dr->renderBottomScreen(saturation, gradient);
                    glDisable(GL_SCISSOR_TEST);
                }
                if (blend) glDisable(GL_BLEND);
            }
            glDisable(GL_SCISSOR_TEST);

            // Composite the layout offscreen onto the window, turned by the rotation
            // matrix (which folds in the texture-sampling Y-flip; see applySfRotation).
            backend->bindPrimary();
            glViewport(0, 0, W, H);
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);
            dr->blitFullTexture(sfLayoutTex, sfBlitMat);
        }

        // Overlay over the whole primary window.
        backend->bindPrimary();
        glViewport(0, 0, W, H);
        gfx.setViewport(W, H);
        gfx.beginFrame();
        overlay.draw(gfx);
        if (input.cursorMode && !overlay.isOpen()) {
            // Drawn in logical space (matches the bottom-screen rect with no
            // display rotation, the common SF case; under a quarter-turn the SF
            // overlay layer is known not to rotate, same as the menu/OSK).
            drastic_nano::LayoutConfig cc = readSfLayoutConfig(sfLogW, sfLogH);
            cc.swap = cc.swap ^ screensSwapped;
            drastic_nano::Rect cbr = drastic_nano::bottomRect(
                    drastic_nano::compute(cc, (uint32_t)sfLogW, (uint32_t)sfLogH));
            drawTouchCursor(gfx, cbr, input.cursorX, input.cursorY,
                            (input.dsBtnMask & DrasticRunner::kDsBtnA) != 0);
        }
        gfx.endFrame();

        const bool wantShot = shotRequested();
        if (wantShot) {
            captureFboToPpm(W, H, "/data/drastic_nano_shot.ppm");
            if (dual) {
                // The second window holds the bottom DS screen; capture it too so
                // a single shot covers both panels, like the DRM path.
                backend->bindSecondary();
                captureFboToPpm((int)ft.secondaryW, (int)ft.secondaryH,
                                "/data/drastic_nano_shot_bot.ppm");
                backend->bindPrimary();
            }
            // Clear the request so the capture is one-shot. Without this the
            // glReadPixels readback and PPM write run every frame and collapse
            // the frame rate -- the DRM path clears it here for the same reason.
            property_set("sys.gammaos.drastic_nano.shot", "0");
        }

        // On-screen keyboard: on the bottom window for a dual session, anchored to
        // the bottom screen's layout rect for a single window so the keys sit over
        // the touch screen.
        { char od[PROPERTY_VALUE_MAX] = {};
          property_get("persist.gammaos.drastic_nano.osk_dbg", od, "0");
          if (od[0] == '1') overlay.debugOpenOsk(); }
        if (overlay.oskActive()) {
            if (dual) {
                backend->bindSecondary();
                glViewport(0, 0, (GLsizei)ft.secondaryW, (GLsizei)ft.secondaryH);
                gfx.setViewport((int)ft.secondaryW, (int)ft.secondaryH);
                gfx.beginFrame();
                overlay.drawOsk(gfx);
                gfx.endFrame();
                gfx.setViewport(W, H);
            } else {
                // Keyboard on the bottom screen's AREA, never the whole panel:
                // use a STACKED (top/bottom) layout's bottom rect so the OSK takes
                // the bottom half even when the game layout shows the bottom screen
                // full-panel (the user's "OSK takes the whole screen" report). The
                // game keeps rendering its own layout behind/above the keyboard.
                drastic_nano::LayoutConfig fc;
                fc.orient = drastic_nano::Orientation::Vertical;
                fc.scaling = drastic_nano::Scaling::Stretch;
                fc.swap = false;   // Bottom (touch) screen at the physical bottom
                drastic_nano::Rect br = drastic_nano::bottomRect(
                        drastic_nano::compute(fc, (uint32_t)W, (uint32_t)H));
                backend->bindPrimary();
                if (br.w > 0.0f && br.h > 0.0f) {
                    const int vx = (int)br.x, vy = H - (int)(br.y + br.h);
                    const int vw = (int)br.w, vh = (int)br.h;
                    glViewport(vx, vy, vw, vh);
                    gfx.setViewport(vw, vh);
                } else {
                    glViewport(0, 0, W, H);
                    gfx.setViewport(W, H);
                }
                gfx.beginFrame();
                overlay.drawOsk(gfx);
                gfx.endFrame();
                gfx.setViewport(W, H);
            }
        }

        backend->present(ft);
        if (!firstPresented) {
            firstPresented = true;
            property_set("sys.gammaos.drastic_nano.rendering", "1");
        }

        fpsFrameCount++;
        {
            const int64_t nowMs = android::elapsedRealtime();
            const int64_t dtMs = nowMs - fpsWindowStartMs;
            if (dtMs >= 2000) {
                ALOGI("drastic-nano: SF present rate %.1f fps",
                      fpsFrameCount * 1000.0 / (double)dtMs);
                fpsFrameCount = 0;
                fpsWindowStartMs = nowMs;
            }
        }

        // Cap the present rate at the DS-native / panel 60 Hz. The SF window
        // swaps with interval 0 so eglSwapBuffers never blocks; without a cap
        // the loop re-presents the same emulated frame as fast as the GPU
        // allows (hundreds of fps), pinning a CPU core and overheating the
        // handheld for no visible gain. Sleeping the rest of each 60 Hz slice
        // idles the core between frames while keeping the game smooth.
        {
            constexpr int64_t kFrameNs = 16666667;   // 1/60 s
            const int64_t usedNs = android::elapsedRealtimeNano() - _frameStartNs;
            if (usedNs < kFrameNs) {
                struct timespec ts = {0, (long)(kFrameNs - usedNs)};
                nanosleep(&ts, nullptr);
            }
        }
    }

    ra.shutdown();
    overlay.close();
    overlay.freeRaTextures(gfx);
    gfx.shutdown();
    if (sfLayoutFbo) glDeleteFramebuffers(1, &sfLayoutFbo);
    if (sfLayoutTex) glDeleteTextures(1, &sfLayoutTex);
    android::drastic_input::closeInputDevices(&input);
    return result;
}

}  // namespace

int main(int argc, char** argv) {
    setpriority(PRIO_PROCESS, 0, ANDROID_PRIORITY_DISPLAY);
    // Silence process-wide ALOGD. PlayerBase.cpp in libaudioclient
    // leaves LOG_TAG undefined, so its stop/setVolume/setPan debug
    // lines land with tag=getprogname()=drastic-nano and spam logcat
    // many times per second once drastic's OpenSL audio pipeline is
    // running. Our own binary never emits ALOGD, so raising the
    // threshold to INFO kills the spam without losing anything we
    // care about.
    __android_log_set_minimum_priority(ANDROID_LOG_INFO);
    // Install early so any crash inside Drastic's native code or our
    // own init path still unblocks SurfaceFlinger / gammaos-nano.
    installCrashHandler();
    ALOGI("drastic-nano: starting (argc=%d)", argc);
    initDrasticLocale();

    // Render-thread scheduling: SCHED_FIFO prio 80 (+ nice -20 as a
    // fallback when RT is denied). Matches NanoMenu's QR fast-path
    // configuration and was what made the QR preview pacing smooth
    // on this hardware. Without it, the main render thread runs at
    // plain SCHED_OTHER nice -4 -- still elevated, but low enough
    // that audioserver / system_server / init housekeeping preempts
    // us mid-flip, stretching the vblank-wait and producing frame
    // spikes. FIFO-over-RR because drastic's own DS worker threads
    // already run at SCHED_RR 5; at equal priority RR peers
    // time-slice, and a slice expiry inside drmDrainPageFlipEvents
    // blows the 16.67 ms budget. FIFO never gets sliced out but
    // yields cleanly every time the render loop blocks in poll()
    // for vblank, so the RR workers still get wall-clock.
    {
        sched_param sp = {};
        sp.sched_priority = 80;
        int rc = pthread_setschedparam(pthread_self(), SCHED_FIFO, &sp);
        pid_t selfTid = (pid_t)syscall(SYS_gettid);
        setpriority(PRIO_PROCESS, selfTid, -20);
        if (rc == 0) {
            ALOGI("drastic-nano: render thread SCHED_FIFO prio 80 + "
                  "nice -20 ok");
        } else {
            ALOGW("drastic-nano: SCHED_FIFO denied (%s), nice -20 "
                  "applied", strerror(rc));
        }
        // Intentionally no sched_setaffinity here. Pinning the render
        // thread before dr.init() causes every subsequent drastic
        // child thread (emu, audio, mali worker, binder) to inherit
        // the same affinity mask and end up serialised on a single
        // core. SCHED_FIFO 80 already guarantees preemption over
        // drastic's SCHED_RR 5 workers, so explicit core reservation
        // is not required and costs ~2x frame throughput when it
        // leaks into children.
    }

    // Lock current + future pages into RAM so no access faults into
    // demand-paged I/O mid-frame. Pairs with IPC_LOCK +
    // SYS_RESOURCE + rlimit memlock in drastic-nano.rc. Costs ~50 ms
    // of up-front fault work at startup for predictable per-frame
    // timing thereafter.
    if (mlockall(MCL_CURRENT | MCL_FUTURE) == 0) {
        ALOGI("drastic-nano: mlockall done");
    } else {
        ALOGW("drastic-nano: mlockall failed: %s", strerror(errno));
    }

    // Disable deep cpu-sleep idle state on every CPU. On RK3566 the
    // shallow WFI state has 1 us wake latency while cpu-sleep carries
    // ~220 us. Drastic's DS emulator thread (SCHED_RR prio 5)
    // migrates between CPUs several times a second; every landing on
    // a deep-idle core pays the 220 us wake penalty and eats into the
    // 16.67 ms budget, which manifests to the player as an apparent
    // "auto frameskip" even though drastic's _FrameskipType is 0.
    //
    // We leave WFI enabled (state0) so cores can still clock-gate at
    // 1 us cost; we only disable the deep state (state1). Restored
    // to its prior value on exit.
    disableDeepCpuIdle();

    // Re-kick the power profile service so GPU / DMC / VOP / CPU
    // governors are guaranteed to be at the user's chosen profile
    // before drastic renders its first frame. The init.rc trigger
    // that normally fires this races at boot and on screen state
    // flips; forcing it here eliminates the "restart sometimes
    // fixes it" variance.
    retriggerPowerProfile();

    std::string romPath;
    if (argc > 1 && argv[1] && argv[1][0]) {
        romPath = argv[1];
    } else {
        romPath = readTrimmed(kRomPathFile);
    }
    if (romPath.empty()) {
        ALOGE("drastic-nano: no ROM path (argv or %s)", kRomPathFile);
        return 1;
    }
    // Wait up to 15s for the ROM to become accessible (SD card
    // auto-mount delay on boot-time launches).
    if (access(romPath.c_str(), R_OK) != 0) {
        ALOGI("drastic-nano: waiting for ROM %s", romPath.c_str());
        int waited = 0;
        while (waited < 15000 && access(romPath.c_str(), R_OK) != 0) {
            usleep(100 * 1000);
            waited += 100;
        }
        if (access(romPath.c_str(), R_OK) != 0) {
            ALOGE("drastic-nano: ROM still not accessible after %dms: %s",
                  waited, romPath.c_str());
            return 2;
        }
    }
    ALOGI("drastic-nano: rom=%s", romPath.c_str());

    // Verify drastic's installed data dir exists. If drastic has
    // never been launched by the user, the dir is missing and we
    // refuse to start -- without the BIOS + firmware files stored
    // there drastic cannot boot a ROM.
    if (!exists(kDrasticDataDir)) {
        ALOGE("drastic-nano: %s missing -- launch the real drastic "
              "app at least once to seed BIOS / firmware", kDrasticDataDir);
        return 3;
    }

    std::string apkDir = findDrasticApkDir();
    if (apkDir.empty()) {
        ALOGE("drastic-nano: com.dsemu.drastic not installed");
        return 4;
    }
    ALOGI("drastic-nano: apk dir=%s", apkDir.c_str());

    std::string libsDir = resolveDrasticLibsDir(apkDir);
    if (libsDir.empty()) {
        ALOGE("drastic-nano: could not resolve libdrastic_arm64.so");
        return 5;
    }

    // Look up drastic's installed UID/GID from its data dir so any
    // writes we make to the data dir stay readable by the real
    // drastic app process (which runs as that UID on its own
    // launches).
    uid_t appUid = 0;
    gid_t appGid = 0;
    lookupDrasticUid(&appUid, &appGid);
    ALOGI("drastic-nano: drastic uid=%u gid=%u", appUid, appGid);

    // Ensure user-writable subdirs exist so drastic does not
    // fclose(NULL) when opening a new save / cheat file.
    ensureDrasticWritableDirs(appUid, appGid);

    // umask 0002 so files written by drastic's native code through
    // our root process land at mode 0664/0775 -- readable by the
    // drastic app UID's primary group. Without this, a file
    // drastic-nano creates in drastic's data dir ends up 0600
    // root-only and the real drastic app cannot read it.
    umask(0002);

    // FakeJNI resolves DraStic/<rel> and User/<rel> paths directly
    // under drastic's real files dir. setDirectUserMode(true) flips
    // the User/ mapping so it matches drastic's real layout (no
    // /user/ subdir).
    android::fakejni::setDirectUserMode(true);

    // Do NOT stop SurfaceFlinger. system_server's Watchdog has a
    // ~60-80 s timeout on SurfaceFlingerAIDL being reachable on the
    // binder; if SF is stopped for longer than that, Watchdog kills
    // system_server and the whole userspace cascade-dies (observed
    // as a late-session SIGBUS in libdrastic that was actually just
    // the kill cascade hitting us). Instead, just grab DRM master
    // via drmEarlySplash -- SF stays alive, answers binder calls,
    // and only loses the ability to present to the panel because
    // master is ours. Mirrors gammaos-nano's QR preview pattern.

    Display dpy{};
    // Select the display backend.
    //   drm  - grab DRM master and render the panel(s) directly through the AHB
    //          ring (the reliable path on a device that boots DRM-direct).
    //   sf   - render through SurfaceFlinger as an ordinary client layer, for a
    //          true pure-SF device whose panels belong to the compositor and that
    //          has no DRM-direct path of its own.
    //   auto - (default) prefer DRM: a device that can grab DRM master uses it,
    //          and only a device with no DRM-direct path falls back to
    //          SurfaceFlinger. A DRM-capable handheld (the RG units boot
    //          DRM-direct) is reliably served by DRM and never has to wrestle the
    //          panel away from the home through SurfaceFlinger.
    // The DRM path and its runLoop are untouched. The sf path fills the same dpy
    // fields and runs runLoopSf below.
    std::unique_ptr<drastic_nano::IDisplayBackend> sfBackend;
    bool sfMode = false;
    bool drmUp  = false;
    {
        char backendProp[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.drastic_nano.backend", backendProp, "auto");
        const bool forceDrm = (strcmp(backendProp, "drm") == 0);
        const bool forceSf  = (strcmp(backendProp, "sf") == 0);

        // DRM first unless SurfaceFlinger is explicitly forced. setupDisplay grabs
        // DRM master; it succeeds on a DRM-capable panel and returns false on a
        // device with no DRM-direct path.
        if (!forceSf) {
            drmUp = setupDisplay(&dpy);
            if (drmUp) {
                ALOGI("drastic-nano: DRM backend up (%dx%d)", dpy.width, dpy.height);
            }
        }

        // SurfaceFlinger when DRM is not up and was not explicitly forced.
        if (!drmUp && !forceDrm) {
            auto* sfb = new drastic_nano::SfDisplayBackend();
            // backend=sf is the explicit, DRM-boot opt-in: render into the
            // DrasticSf host activity's Surface so SurfaceFlinger actually
            // presents us. The auto-fallback (a pure-SF device whose panels
            // already belong to SF) keeps the own-layer path. Set on the
            // concrete type before it is held by the IDisplayBackend pointer.
            sfb->setHostSurfaceMode(forceSf);
            sfBackend.reset(sfb);
            drastic_nano::DisplayEnv env{};
            if (sfBackend->createContext(&env)) {
                dpy.eglDpy = env.eglDpy;
                dpy.eglCtx = env.eglCtx;
                dpy.eglSurf = env.eglSurf;
                dpy.width = env.width;
                dpy.height = env.height;
                sfMode = true;
                ALOGI("drastic-nano: SurfaceFlinger backend up (%dx%d)",
                      dpy.width, dpy.height);
                // On a DRM-direct device the gammaos-nano overlay is what holds
                // SurfaceFlinger presenting to the panel (it performs the DRM ->
                // SF takeover and drops DRM master). When we are hosted by the
                // DrasticSf activity (backend=sf), that overlay MUST stay up or
                // our SF layer renders to a panel SF is not scanning out -- a
                // black screen. So only suppress the overlay on a pure-SF device
                // (the auto fallback, where SF always owns the panel); there it
                // would otherwise composite its XMB over the emulator.
                if (!forceSf) {
                    property_set("sys.gammaos.nano.overlay_ran", "0");
                    property_set("ctl.stop", "gammaos-nano-overlay");
                }
            } else {
                sfBackend.reset();
            }
        }
    }
    if (!drmUp && !sfMode) {
        ALOGE("drastic-nano: no display backend (DRM and SF both unavailable)");
        property_set(kSessionDoneProp, "1");
        return 6;
    }

    // Read the user's drastic SharedPreferences so the overlay menu
    // starts with the right values and applyConfig uses the user's
    // real video settings (shader, hi-res, threaded 3d, edge marking,
    // etc.). Failure is non-fatal: we fall back to defaults.
    const std::string prefsPath = std::string(
            "/data/user/0/com.dsemu.drastic/shared_prefs/"
            "_Dra$t1c_Pref$_.xml");
    android::drastic_prefs::Prefs prefs;
    android::drastic_prefs::readPrefs(prefsPath, &prefs);
    // Force frameskip off for the drastic-nano session. The real drastic
    // app's _FrameskipType may be 1 (auto) or a fixed value > 0; neither
    // is what we want on nano where the render loop is already RT-paced
    // and frameskipping produces visible judder rather than hiding it.
    // Change is session-local: we don't persist it back to the XML.
    prefs.frameskipType  = 0;
    prefs.frameskipValue = 0;
    prefs.frameskipSafe  = false;
    // Carry the frame-sync flag into the DRM flip path. Read at session
    // start rather than per-iter so the ring-depth assumption (enabled
    // adds one hold-slot to the working set) holds for the whole run.
    // Runtime toggle from the overlay writes this variable too.
    android::sDrmFrameSync = prefs.frameSync;
    long userBits = android::drastic_prefs::applyConfigBitsFrom(prefs);
    const std::string savestatesDir = std::string(kDrasticDataDir) +
                                       "/savestates";
    const std::string shadersDir    = std::string(kDrasticDataDir) +
                                       "/shaders";

    DrasticRunner dr;
    // cacheDir = drastic's installed files dir so every open / write
    // lands on the real files. libsDir points at the APK's
    // nativeLibraryDir so we get the unpatched libdrastic (real
    // audio). soundEnabled sets the _SoundEnabled config bit.
    // configBitsOverride threads the user's XML settings through.
    // Auto-resume: the drastic-android-mod loads its autosave (slot 9)
    // on launch. Default on; the overlay's "Auto Load State on Launch"
    // toggle persists persist.gammaos.drastic_nano.autoload. When on we
    // pass slot 9 so startGame boot-loads it; when off we pass -1 for a
    // fresh boot.
    // boot_fresh is a one-shot signal set by a "Restart Game" relaunch:
    // ignore the auto-load slot this launch so the ROM boots from the
    // title rather than resuming. Cleared immediately so the next normal
    // launch resumes as usual.
    bool bootFresh = property_get_bool(
            "sys.gammaos.drastic_nano.boot_fresh", false);
    if (bootFresh) {
        property_set("sys.gammaos.drastic_nano.boot_fresh", "0");
        ALOGI("drastic-nano: boot_fresh set, forcing fresh boot");
    }
    // RetroAchievements hardcore forbids loading save states, including the
    // launch auto-resume, so a hardcore session always boots fresh. Hardcore is
    // a per-session setting fixed at launch, so reading the props here matches
    // what the client will enforce.
    bool raHardcore = property_get_bool("persist.gammaos.drastic_nano.ra_enabled", false) &&
                      property_get_bool("persist.gammaos.drastic_nano.ra_hardcore", false);
    // Quick Resume boot: nano set sys.gammaos.drastic_nano.qr_resume on the resume
    // handoff. A resume loads slot 9 even if boot_fresh would otherwise force a
    // fresh boot -- but NOT under RA hardcore, which always boots fresh (below).
    // qr_resume is a volatile prop (cleared on reboot); the run loop clears it on
    // exit so an in-session relaunch (Restart Game) boots fresh.
    bool qrResume = property_get_bool("sys.gammaos.drastic_nano.qr_resume", false);
    // RetroAchievements hardcore forbids loading ANY save state, so a hardcore
    // session ALWAYS boots fresh -- even a Quick Resume. Per the user policy a
    // hardcore game does not resume from a state; it relaunches clean and STAYS
    // hardcore. raHardcore therefore takes precedence over both qrResume and
    // boot_fresh. Because no state is loaded under hardcore, the RA client keeps
    // hardcore (see ra_force_softcore below).
    int autoLoadSlot;
    if (raHardcore) {
        autoLoadSlot = -1;
        ALOGI("drastic-nano: RA hardcore - forcing fresh boot (no auto-load, even for Quick Resume)");
    } else if (qrResume) {
        autoLoadSlot = 9;
        ALOGI("drastic-nano: Quick Resume - forcing auto-load slot 9");
    } else if (bootFresh) {
        autoLoadSlot = -1;
    } else {
        autoLoadSlot = property_get_bool(
                "persist.gammaos.drastic_nano.autoload", true) ? 9 : -1;
    }
    // Validate / quarantine slot 9 before we ever hand it to the drastic core. A
    // corrupt (empty, truncated, or half-written) .dss crashes the boot-load, and
    // because auto-load persists the SAME bad state reloads on every relaunch of
    // this ROM -- a launch-crash loop. Two guards:
    //   - a ".loading" crash marker written just before the load and cleared once
    //     the game renders its first frame (in the run loop). If it is still
    //     present now, the PREVIOUS load crashed before drawing anything, so slot 9
    //     is bad even if its size looks plausible (catches content corruption);
    //   - a minimum plausible size (catches an empty / truncated .dss).
    // On either, move the .dss aside to <rom>_9.dss.bad (never silently deleted, so
    // it can be inspected) and boot fresh. The stable-write tail below is what keeps
    // us from PRODUCING a truncated state in the first place.
    if (autoLoadSlot == 9) {
        const std::string stem = slot9Stem(savestatesDir, romPath);
        const std::string dss = stem + ".dss";
        const std::string marker = stem + ".loading";
        struct stat st{};
        const bool crashedLast = (access(marker.c_str(), F_OK) == 0);
        const bool missingOrSmall =
                (stat(dss.c_str(), &st) != 0) || (st.st_size < kMinDssBytes);
        if (crashedLast || missingOrSmall) {
            const std::string bad = dss + ".bad";
            ALOGW("drastic-nano: slot 9 unusable (%s) - quarantining to %s and booting fresh",
                  crashedLast ? "previous load crashed before rendering"
                              : "missing or too small",
                  bad.c_str());
            rename(dss.c_str(), bad.c_str());
            unlink(marker.c_str());
            autoLoadSlot = -1;
        } else {
            // Arm the crash marker. If the load below crashes before the game
            // renders, this survives to the next launch and the state is
            // quarantined; the run loop clears it once the first frame is ready.
            int mfd = open(marker.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
            if (mfd >= 0) close(mfd);
        }
    }
    // Single source of truth for "this session loaded a save state, so the RA
    // client MUST run softcore" (RetroAchievements bars hardcore over a loaded
    // state). main.cpp is the sole decider of whether a state loads; the RA client
    // reads this prop instead of re-deriving from qr_resume, so a hardcore session
    // that boots fresh (autoLoadSlot == -1 above) correctly STAYS hardcore, while
    // any softcore state-load forces softcore. Covers the QR resume and the
    // on-demand in-game RA login alike.
    property_set("sys.gammaos.drastic_nano.ra_force_softcore",
                 autoLoadSlot == 9 ? "1" : "0");
    ALOGI("drastic-nano: auto-load slot = %d (ra_force_softcore=%d)",
          autoLoadSlot, autoLoadSlot == 9 ? 1 : 0);
    // Mark this as a real play session so DrasticRunner enables the fxRender
    // shader path regardless of the persist drastic-nano feature flag. The
    // home's QR preview never sets this, so it stays on the renderFrame path.
    // drastic-nano.rc clears it on session_done (clean exit and crash).
    property_set("sys.gammaos.drastic_nano.session", "1");
    if (!dr.init(kDrasticDataDir, romPath, libsDir,
                 /*soundEnabled=*/prefs.soundEnabled,
                 /*configBitsOverride=*/userBits,
                 /*autosaveIntervalSeconds=*/0,
                 /*initialShader=*/prefs.currentFx,
                 /*autoLoadSlot=*/autoLoadSlot)) {
        ALOGE("drastic-nano: DrasticRunner::init failed");
        property_set(kSessionDoneProp, "1");
        return 7;
    }
    // Pin the DS core's internal mixer to max so the Android system volume
    // (STREAM_MUSIC, which PhoneWindowManager drives from the shared VOL keys and
    // publishes as persist.gammaos.nano.volume) is the SINGLE volume control -- the
    // DS OpenSL output already goes through STREAM_MUSIC, so its own mixer was just
    // a second, unaligned attenuation. The volume HUD now reflects that system
    // level; see OverlayMenu::adjustVolume / drawHud.
    dr.setVolumeRuntime(100);

    RunLoopResult rlr = sfMode
            ? runLoopSf(sfBackend.get(), &dr, prefs, appUid, appGid,
                        prefsPath, savestatesDir, romPath, shadersDir)
            : runLoop(&dpy, &dr, prefs, appUid, appGid,
                      prefsPath, savestatesDir, romPath, shadersDir);
    // Release the SurfaceFlinger layer(s) so the compositor recomposites the home
    // behind them. The DRM teardown below is guarded by sDrmActive and no-ops on
    // the SF path.
    if (sfMode && sfBackend) sfBackend->teardown();

    // Persist the autosave (slot 9) that the next launch auto-loads.
    // The DrasticRunner destructor's quitSystem does NOT reliably
    // refresh slot 9, so without this explicit save every launch
    // reloads a stale state and in-game progress is lost. Gated on the
    // same "Auto Load State on Launch" toggle (default on): when the
    // feature is off we leave slot 9 untouched and boot fresh next
    // time. The save is queued to drastic's worker thread, so wait for
    // the .dss to flush before the destructor pauses/quits the core.
    // "Restart Game" (rlr.restartFresh) must NOT autosave: the whole
    // point is to boot fresh from the title, so we leave slot 9 alone and
    // the relaunch passes auto-load = off (boot_fresh below).
    // A Quick Resume power off / reboot FORCES the slot-9 save even when the
    // Auto Load toggle is off: the next boot resumes via qr_resume (which forces
    // the load regardless of autoload), so the state must exist. Quick Resume is
    // default-on; when it is off we keep the plain autoload-gated behavior.
    // Any shutdown path (our own DRM power tail, or an external quit / SIGTERM from
    // nano prepareShutdown / ShutdownThread that arms qr_prepared before issuing the
    // power action) must leave slot 9 current, else the next boot resumes a stale /
    // missing state when the Auto Load toggle is off.
    bool qrPowerAction = (rlr.powerOffAfter || rlr.rebootAfter || rlr.quitShutdown);
    bool qrEnabled = property_get_bool("persist.gammaos.nano.quick_resume", true);
    // exitToHome (back-hold) is a graceful close, so save slot 9 unconditionally
    // (the point is to preserve progress on the way out), independent of the Quick
    // Resume / Auto Load toggles.
    bool forceSlot9 = (qrPowerAction && qrEnabled) || rlr.exitToHome;
    if (!rlr.restartFresh &&
        (forceSlot9 ||
         property_get_bool("persist.gammaos.drastic_nano.autoload", true))) {
        const std::string slot9 = slot9Stem(savestatesDir, romPath) + ".dss";
        struct stat before {};
        bool had = (stat(slot9.c_str(), &before) == 0);
        time_t beforeM = had ? before.st_mtime : 0;
        off_t  beforeS = had ? before.st_size  : 0;
        if (dr.saveAutosave()) {
            // The save is queued to drastic's worker thread and the core writes the
            // .dss in place (no atomic temp+rename), so we must NOT let the power
            // action proceed until the write has fully finished: cutting power
            // mid-write leaves a truncated state that the next boot auto-loads and
            // crashes on (and, with auto-load on, keeps crashing every launch). Wait
            // for the file to (a) change from its prior mtime/size, (b) reach a
            // plausible minimum size, and (c) hold that size steady across several
            // consecutive polls (write complete, not still growing). Typically this
            // resolves in well under a second; the loop is only an upper bound so a
            // stuck worker can never hang the shutdown. Then fsync and hold ~2 more
            // seconds so the data is durable on storage before we cut power (the
            // extra grace the user asked for, over and above the stable-size check).
            off_t lastSize = -1;
            int stableCount = 0;
            bool stable = false;
            for (int i = 0; i < 120; i++) {   // upper bound ~6s to observe a stable write
                usleep(50 * 1000);
                struct stat now {};
                if (stat(slot9.c_str(), &now) == 0 &&
                    (!had || now.st_mtime != beforeM || now.st_size != beforeS) &&
                    now.st_size >= kMinDssBytes) {
                    if (now.st_size == lastSize) {
                        if (++stableCount >= 4) {   // ~200ms unchanged => write settled
                            stable = true;
                            ALOGI("drastic-nano: autosave slot 9 stable (%lld bytes)",
                                  (long long)now.st_size);
                            break;
                        }
                    } else {
                        stableCount = 0;
                        lastSize = now.st_size;
                    }
                }
            }
            if (stable) {
                int fd = open(slot9.c_str(), O_RDONLY);
                if (fd >= 0) { fsync(fd); close(fd); }
                usleep(2 * 1000 * 1000);   // 2s durability grace before the power action
                ALOGI("drastic-nano: autosave slot 9 fsynced (+2s durability grace)");
            } else {
                ALOGW("drastic-nano: autosave slot 9 never stabilized; leaving prior state, next boot may quarantine it");
            }
        }
    }

    // DrasticRunner destructor -> shutdown() -> pauseSystem +
    // quitSystem, then ownership/teardown below.

    // Root wrote files with UID=root during the session. Restore
    // ownership to drastic's app UID so the real drastic app can
    // read its own saves / savestates / config on its next launch.
    // We do this via a shell fork instead of walking the tree in
    // C++ because chown -R handles cross-filesystem behaviour and
    // SELinux labels identically to how init does it.
    if (appUid != 0) {
        char cmd[256];
        snprintf(cmd, sizeof(cmd),
                 "chown -R %u:%u %s",
                 appUid, appGid, kDrasticDataDir);
        system(cmd);
        ALOGI("drastic-nano: restored ownership on %s", kDrasticDataDir);
    }

    eglMakeCurrent(dpy.eglDpy, EGL_NO_SURFACE, EGL_NO_SURFACE,
                   EGL_NO_CONTEXT);
    if (dpy.eglCtx != EGL_NO_CONTEXT) eglDestroyContext(dpy.eglDpy, dpy.eglCtx);
    if (dpy.eglSurf != EGL_NO_SURFACE) eglDestroySurface(dpy.eglDpy, dpy.eglSurf);
    eglTerminate(dpy.eglDpy);

    if (android::sDrmFd >= 0) {
        ioctl(android::sDrmFd, DRM_IOCTL_DROP_MASTER, 0);
        close(android::sDrmFd);
        android::sDrmFd = -1;
        android::sDrmActive = false;
    }

    // Clear the one-shot Quick Resume marker so an in-session relaunch (Restart
    // Game or a settings change) boots fresh instead of re-resuming slot 9. On a
    // real reboot the volatile prop is gone anyway; this only matters for a same-
    // boot relaunch.
    property_set("sys.gammaos.drastic_nano.qr_resume", "0");

    // When the overlay asked for a relaunch (a restart-required
    // setting changed), set the auto_relaunch prop so gammaos-nano
    // (XMB) re-kicks drastic-nano.start instead of returning to the
    // menu. If the XMB doesn't honor that prop yet, the worst case is
    // a normal return-to-XMB -- the user can relaunch manually and
    // the new XML settings will take effect.
    if (rlr.relaunchRequested) {
        // "Restart Game" boots fresh: the next instance must ignore the
        // auto-load slot and reboot the ROM from the title. A settings
        // relaunch (restartFresh == false) instead resumes slot 9 so the
        // new settings apply mid-game.
        if (rlr.restartFresh) {
            property_set("sys.gammaos.drastic_nano.boot_fresh", "1");
        }
        property_set("sys.gammaos.drastic_nano.auto_relaunch", "1");
        ALOGI("drastic-nano: requesting auto-relaunch (fresh=%d)",
              rlr.restartFresh ? 1 : 0);
    }

    restoreDeepCpuIdle();

    // Quick Resume power off / reboot. drastic-nano owns the save + power action
    // here because gammaos-nano is stopped during a DRM session (and in SF the
    // framework owns the power gestures, so a power row / external quit routes
    // through here too). Slot 9 was force-saved above; arm the resume descriptor
    // when Quick Resume is enabled -- the ROM path file nano_drastic_nano_rom.txt
    // is already current from launch, so qr_prepared + qr_core=drastic is all the
    // next boot needs. Then issue the power action via init's nano_action hook and
    // return WITHOUT setting session_done: setting it would restart the home and
    // race sys.powerctl (init acts on nano_action synchronously). This also skips
    // the SF overlay hand-back below, which would fight an in-progress shutdown.
    if (rlr.powerOffAfter || rlr.rebootAfter) {
        if (property_get_bool("persist.gammaos.nano.quick_resume", true)) {
            // Point the resume at THIS game. nano's boot handoff reads
            // /data/system/nano_qr_rom.txt (getQrRomPath), which can be stale from a
            // prior libretro/other launch, so write the current ROM there durably
            // (temp+fsync+rename) before the power action, plus the prop mirror.
            {
                const char* dst = "/data/system/nano_qr_rom.txt";
                std::string tmp = std::string(dst) + ".tmp";
                int fd = open(tmp.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0666);
                if (fd >= 0) {
                    if (write(fd, romPath.c_str(), romPath.size()) ==
                            (ssize_t)romPath.size()) {
                        fsync(fd); close(fd); chmod(tmp.c_str(), 0666);
                        if (rename(tmp.c_str(), dst) != 0) unlink(tmp.c_str());
                    } else { close(fd); unlink(tmp.c_str()); }
                }
            }
            property_set("persist.gammaos.nano.qr_rom", romPath.c_str());
            property_set("persist.gammaos.nano.qr_prepared", "1");
            property_set("persist.gammaos.nano.qr_core", "drastic");
            // Keep the boot preview pointing at THIS game: the preview shows
            // qr_game_name and, when storage is slow to mount, falls back to the
            // single .nds staged in the drastic cache. This own-power path (an
            // in-game overlay Restart / Power Off) issues the power action right
            // below, so unlike the framework prepareShutdown path -- which overlaps
            // its drastic-nano quit wait with the async copy -- we set the name and
            // re-stage the cache HERE and wait (bounded) for the copy before cutting
            // power. populate_drastic reads nano_drastic_nano_rom.txt (current from
            // launch) and evicts any previously-cached ROM.
            {
                std::string gameName = romPath;
                size_t ls = gameName.rfind('/');
                if (ls != std::string::npos) gameName = gameName.substr(ls + 1);
                size_t dot = gameName.rfind('.');
                if (dot != std::string::npos) gameName.erase(dot);
                property_set("persist.gammaos.nano.qr_game_name", gameName.c_str());
                property_set("sys.gammaos.nano.cache_ready", "0");
                property_set("sys.gammaos.nano.cache_op", "populate_drastic");
                for (int i = 0; i < 160; i++) {   // up to ~8s for the ROM copy to finish
                    usleep(50 * 1000);
                    if (property_get_bool("sys.gammaos.nano.cache_ready", false)) break;
                }
            }
            ALOGI("drastic-nano: armed Quick Resume (qr_core=drastic, rom=%s)",
                  romPath.c_str());
        }
        const char* action = rlr.rebootAfter ? "reboot" : "shutdown";
        ALOGI("drastic-nano: %s after graceful save", action);
        property_set("service.bootanim.nano_action", action);
        ALOGI("drastic-nano: exit (power action)");
        return 0;
    }

    // External quit / SIGTERM: nano prepareShutdown or the framework ShutdownThread
    // asked us to save + exit and will issue the power action itself. Slot 9 was
    // force-saved above. Skip the SF overlay hand-back AND session_done: setting
    // session_done restarts gammaos-nano (drastic-nano.rc), which would race the
    // caller's synchronous nano_action -> sys.powerctl. The caller detects our exit
    // by scanning /proc, not by session_done.
    if (rlr.quitShutdown) {
        ALOGI("drastic-nano: exit (external shutdown quit, caller owns the power action)");
        return 0;
    }

    // Return to the launcher. In SF / overlay-home mode the resident overlay is
    // the home, so raise it in wallpaper mode: clear sys.gammaos.nano.app_launched
    // (held at 1 through the session so PhoneWindowManager owned the power
    // gestures) and set show_overlay=1, exactly as a normal app exit does. Without
    // this the overlay stays hidden behind app_launched=1 and the nano menu never
    // comes back. Skipped on a relaunch (Restart Game / a settings change), where
    // the next session re-takes the panel and re-asserts app_launched itself.
    if (sfMode && !rlr.relaunchRequested) {
        property_set("sys.gammaos.nano.app_launched", "0");
        property_set("sys.gammaos.nano.show_overlay", "1");
    }

    // SF was never stopped, so with that launcher state set the session_done
    // trigger brings nano back up on the XMB.
    property_set(kSessionDoneProp, "1");
    ALOGI("drastic-nano: exit");
    return 0;
}
