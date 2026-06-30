/*
 * Copyright (C) 2026 GammaOS
 *
 * DrasticRunner implementation -- see header.
 */

#define LOG_TAG "GammaOSNano.Drastic"

#include "DrasticRunner.h"
#include "FakeJNI.h"

#include <dirent.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <pthread.h>
#include <sched.h>
#include <stdio.h>
#include <sys/mman.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <cstring>
#include <chrono>
#include <thread>
#include <atomic>
#include <vector>
#include <GLES2/gl2.h>
#include <cutils/properties.h>
#include <utils/Log.h>

namespace android {

// Boost a thread (identified by its POSIX handle) to SCHED_RR with a
// low real-time priority so it preempts the system_server / zygote /
// surfaceflinger storm at boot. Falls back to nice=-20 if RT isn't
// granted by the current capability set.
//
// Called for drastic's own startGame thread AND the pixel-pull thread
// because both are critical path during the drastic QR init phase.
//
// Rationale (2026-04-11 measurement): at boot time, drastic's startGame
// thread takes 15.68s to produce its first frame vs. 767ms post-boot.
// The difference is CPU availability — at boot the system is 80% busy
// with init + vendor HALs + zygote so drastic only gets ~5% of a core
// on average. SCHED_RR at prio 5 preempts SCHED_OTHER but still yields
// to audio/kernel RT threads (which run at 50-99), so it's safe:
// drastic can't starve genuinely critical services, but it dominates
// anything init is doing.
//
// The priority value is deliberately low (5) so we stay well below
// any audio/input/kernel RT thread. Higher values would risk starving
// inputs or audio during drastic's brief 5.5s of init work.
static void drasticBoostThread(pthread_t handle, const char* label) {
    sched_param sp;
    sp.sched_priority = 5;
    int rc = pthread_setschedparam(handle, SCHED_RR, &sp);
    if (rc == 0) {
        ALOGI("DrasticRunner: boosted %s thread to SCHED_RR prio 5",
              label);
        return;
    }
    ALOGW("DrasticRunner: SCHED_RR failed for %s: %s (falling back "
          "to nice=-20)", label, strerror(rc));
    // Fall back: try SCHED_OTHER nice=-20. pthread handle → tid isn't
    // portable, but on bionic the pthread_t IS the native tid as a
    // kernel-level concept via clone(CLONE_THREAD); setpriority with
    // the right "who" can still work via gettid from inside the thread.
    // This fallback is best-effort — if it fails too, drastic will
    // still run, just more slowly at boot.
    //
    // We do NOT attempt to look up tid from the opaque pthread_t here
    // because the thread may or may not be running yet. The correct
    // place to setpriority is inside the thread function itself via
    // gettid(). Our thread wrappers below call this at entry.
}

// Singleton -- set by init() when it successfully brings up drastic,
// never cleared. NanoMenu's render loop accesses it via getInstance().
// We use a raw pointer (not std::unique_ptr) because the lifetime
// extends until process exit and we deliberately leak it in main.cpp
// so drastic's background threads keep their state alive.
static std::atomic<DrasticRunner*> sInstance{nullptr};

DrasticRunner* DrasticRunner::getInstance() {
    return sInstance.load(std::memory_order_acquire);
}

// -------- Config bit layout --------
//
// drastic's `applyConfig(long)` packs a config bitfield. Bit layout
// decoded from smali `Lf0/h;->n()J` (f0/h.smali:3546):
//   bit 23 (0x00000000800000): R0        (unknown)
//   bit 24 (0x00000001000000): o0        (unknown)
//   bit 25 (0x00000002000000): n0        (unknown)
//   bit 26 (0x00000004000000): T         (unknown)
//   bit 27 (0x00000008000000): j0        (unknown)
//   bit 28 (0x00000010000000): Q0        (_Threaded3D) -- CONFIRMED
//   bit 29 (0x00000020000000): V         (unknown)
//   bit 30 (0x00000040000000): U         (unknown)
//   bit 31 (0x00000080000000): S         (unknown)
//   bit 35 (0x00000800000000): T0        (unknown)
//   bit 36 (0x00001000000000): p0        (unknown)
//   bit 39 (0x00008000000000): A0        (_DisableEdgeMarking)
//   bit 40 (0x00010000000000): S0        (unknown)
//   bit 41 (0x00020000000000): V0        (_Hires3D) -- likely
//   bit 42 (0x00040000000000): G0        (unknown)
//   bit 47 (0x00800000000000): U0        (unknown)
//
// Phase 5 v8: hi-res mode REVERTED. Enabling _Hires3D (bit 41) does
// not actually make getScreenBuffers emit 512x384 frames -- it only
// affects drastic's internal 3D rasterizer resolution, and the
// framebuffer output is still 256x192. Sizing our dest arrays +
// GL textures at 512x384 caused the 256x192 data to land in the
// top-left quadrant, which the sampler then stretched across the
// full quad (and because 512-wide rows pack 2 source rows per
// destination row, the content also appeared horizontally doubled).
// So: 256x192 per-screen buffers, 256x192 textures, _Hires3D OFF.
static constexpr long kDefaultConfigBits =
    0x10000000L            // _Threaded3D (bit 28)
  | 0x10000000000L         // _DisableEdgeMarking (bit 40) -- skip 3D edge pass
  | 0x20000000000L         // _Hires3D (bit 41) -- 2x internal 3D resolution
  | 0x4000000000000L;      // _m0 (bit 50) -- sets master+0x4b8=1 via the
                           //   canonical applyConfig path. Per
                           //   drastic-android-mod disasm at
                           //   libdrastic+0x17c8c the config bit 50 extracts
                           //   into master+0x4b8. The real Drastic app has
                           //   this bit set; without it nano leaves +0x4b8=0
                           //   which participates in the BG-layer priority
                           //   bug. Flowing through applyConfig (rather than
                           //   raw-patching +0x4b8) survives because drastic
                           //   itself never re-clears bits it just set via
                           //   this entry.

// Native DS resolution: 256x192 per screen, RGBA8888, one jint per
// pixel. getScreenBuffers writes exactly 49152 ints per screen.
// These need to be visible to init() (used for pixel-pull buffer
// allocation), so they live above the constructor alongside
// kDefaultConfigBits rather than next to the GL surface code.
static constexpr int kDsScreenW = 256;
static constexpr int kDsScreenH = 192;
static constexpr int kDsScreenPixels = kDsScreenW * kDsScreenH;  // 49152

DrasticRunner::DrasticRunner() {}

DrasticRunner::~DrasticRunner() {
    shutdown();
}

// Periodic thread-state sampler for boot-time stall diagnosis. Dumps
// /proc/self/task/TID/stat for each thread in the process every 500 ms
// into logcat for up to 20 s. Lets us see exactly what drastic's
// worker threads are doing (R/S/D, utime accumulation, wchan) during
// the critical init window without needing to attach strace.
//
// Gated on persist.gammaos.nano.drastic_thread_trace=1 so it's off
// unless explicitly enabled. When on, starts a detached thread from
// init() that logs for 20 s then exits.
static void maybeStartThreadTracer() {
    char val[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.drastic_thread_trace", val, "0");
    if (strcmp(val, "1") != 0) return;

    std::thread([]() {
        pthread_setname_np(pthread_self(), "drastic-tid-trace");
        ALOGW("DrasticRunner: thread trace starting (20s)");
        for (int iter = 0; iter < 40; iter++) {
            char wchan[64] = {};
            // Enumerate /proc/self/task
            DIR* d = opendir("/proc/self/task");
            if (!d) break;
            struct dirent* e;
            int count = 0;
            while ((e = readdir(d)) != nullptr && count < 30) {
                if (e->d_name[0] == '.') continue;
                char statPath[128];
                snprintf(statPath, sizeof(statPath),
                         "/proc/self/task/%s/stat", e->d_name);
                int fd = open(statPath, O_RDONLY | O_CLOEXEC);
                if (fd < 0) continue;
                char buf[512];
                ssize_t n = read(fd, buf, sizeof(buf) - 1);
                close(fd);
                if (n <= 0) continue;
                buf[n] = 0;
                // Parse: tid (comm) state ppid ... utime stime ...
                // state is field 3, utime is field 14, policy is 41
                // comm may contain spaces so find the ')'
                char* rparen = strrchr(buf, ')');
                if (!rparen) continue;
                char state = 0;
                unsigned long utime = 0, stime = 0;
                int policy = 0, prio = 0;
                sscanf(rparen + 2, "%c %*d %*d %*d %*d %*d %*u %*u %*u "
                       "%*u %*u %lu %lu %*d %*d %d %*d %*d %*d %*u %*u "
                       "%*d %*u %*u %*u %*u %*u %*u %*u %*u %*u %*u %*u "
                       "%*u %*u %*u %*u %d",
                       &state, &utime, &stime, &prio, &policy);
                snprintf(wchan, sizeof(wchan),
                         "/proc/self/task/%s/wchan", e->d_name);
                int wfd = open(wchan, O_RDONLY | O_CLOEXEC);
                char wc[32] = "?";
                if (wfd >= 0) {
                    ssize_t w = read(wfd, wc, sizeof(wc) - 1);
                    if (w > 0) wc[w] = 0;
                    else wc[0] = 0;
                    close(wfd);
                }
                // Also dump /proc/$pid/task/$tid/syscall for more
                // context on what the thread is blocked in. Format:
                // "nr arg1 arg2 arg3 arg4 arg5 arg6 sp pc" for live
                // syscalls, "running" / "-1 0xSP 0xPC" otherwise.
                // For futex (nr=98 on arm64) arg1 is the futex address
                // we're waiting on — lets us match to a mapped lib.
                char syscPath[128];
                snprintf(syscPath, sizeof(syscPath),
                         "/proc/self/task/%s/syscall", e->d_name);
                int sfd = open(syscPath, O_RDONLY | O_CLOEXEC);
                char sc[128] = "?";
                if (sfd >= 0) {
                    ssize_t s = read(sfd, sc, sizeof(sc) - 1);
                    if (s > 0) sc[s] = 0;
                    else sc[0] = 0;
                    close(sfd);
                    // Trim trailing whitespace
                    for (int i = (int)strlen(sc) - 1; i >= 0; i--) {
                        if (sc[i] == '\n' || sc[i] == ' ') sc[i] = 0;
                        else break;
                    }
                }
                // comm is between '(' and ')'
                char* lparen = strchr(buf, '(');
                char comm[32] = "?";
                if (lparen && rparen > lparen) {
                    size_t len = rparen - lparen - 1;
                    if (len >= sizeof(comm)) len = sizeof(comm) - 1;
                    memcpy(comm, lparen + 1, len);
                    comm[len] = 0;
                }
                ALOGW("ttrace[%02d] tid=%s %s %c ut=%lu st=%lu "
                      "pol=%d prio=%d wchan=%s sc=%s",
                      iter, e->d_name, comm, state, utime, stime,
                      policy, prio, wc, sc);
                count++;
            }
            closedir(d);
            std::this_thread::sleep_for(
                    std::chrono::milliseconds(500));
        }
        ALOGW("DrasticRunner: thread trace done");
    }).detach();
}

bool DrasticRunner::init(const std::string& cacheDir,
                         const std::string& romPath,
                         const std::string& libsDir,
                         bool soundEnabled,
                         long configBitsOverride,
                         int autosaveIntervalSeconds,
                         const std::string& initialShader,
                         int autoLoadSlot) {
    maybeStartThreadTracer();
    mCacheDir = cacheDir;
    mAutoLoadSlot = autoLoadSlot;
    mInitialShader = initialShader.empty() ? std::string("Linear")
                                           : initialShader;
    const std::string& effectiveLibs = libsDir.empty() ? cacheDir : libsDir;
    ALOGI("DrasticRunner: init cacheDir=%s libsDir=%s rom=%s sound=%d "
          "cfgOverride=0x%lx autosave=%ds shader=%s",
          cacheDir.c_str(), effectiveLibs.c_str(), romPath.c_str(),
          soundEnabled ? 1 : 0, configBitsOverride,
          autosaveIntervalSeconds, mInitialShader.c_str());

    // ---- Phase 1: dlopen the libraries ----
    std::string cpuPath = effectiveLibs + "/libdrastic_cpu.so";
    std::string arm64Path = effectiveLibs + "/libdrastic_arm64.so";

    mCpuHandle = dlopen(cpuPath.c_str(), RTLD_NOW);
    if (!mCpuHandle) {
        ALOGW("DrasticRunner: dlopen(%s) failed: %s",
              cpuPath.c_str(), dlerror());
        // Non-fatal: arm64 lib might load fine without it
    }

    mArm64Handle = dlopen(arm64Path.c_str(), RTLD_NOW);
    if (!mArm64Handle) {
        ALOGE("DrasticRunner: dlopen(%s) failed: %s",
              arm64Path.c_str(), dlerror());
        shutdown();
        return false;
    }

    // ---- Phase 1b: in-memory longjmp patches (non-zygote safety) ----
    //
    // drastic-android-mod disasm (2026-04-17): two `bl longjmp@plt`
    // sites at libdrastic_arm64.so+0x17304 and +0x1b4d0 race the
    // `bl setjmp@plt` at +0x1bf44 in non-zygote processes. If the
    // longjmp fires before setjmp populates the jmp_buf at
    // master+0x3b2f800, siglongjmp dereferences a zero saved
    // context and SEGVs with si_addr=0xfffffffffffffff0.
    //
    // Zygote-hosted drastic doesn't hit this because the ART
    // lifecycle happens to give the main thread a head start. Our
    // DrasticRunner host is init-spawned in every nano / drastic-nano
    // invocation, so the race resolves the wrong way. Overwriting
    // both `bl longjmp` (32-bit) with `ret` is the confirmed fix
    // (verified 2026-04-15 on the nano-shim ART path).
    //
    // Both sites live on the same 4K page, so one mprotect pair
    // covers them. We also patch the audio init at 0x1d760 ONLY if
    // sound is disabled (matches the nano_cache's on-disk patch).
    // When sound is enabled (drastic-nano path), we leave 0x1d760
    // alone so the real OpenSL ES engine comes up.
    {
        uint8_t* base = nullptr;
        // dlopen returns a handle, not necessarily the load base.
        // Recover the base via dladdr on a known symbol we can
        // resolve right now. JNI_OnLoad always lives inside the .so
        // so dladdr gives us DLI_FBASE for the same mapping.
        void* onLoadPtr = dlsym(mArm64Handle, "JNI_OnLoad");
        Dl_info info{};
        if (onLoadPtr && dladdr(onLoadPtr, &info) && info.dli_fbase) {
            base = reinterpret_cast<uint8_t*>(info.dli_fbase);
            mArm64Base = base;
        } else {
            ALOGW("DrasticRunner: dladdr(JNI_OnLoad) failed, skip "
                  "longjmp patches");
        }
        if (base) {
            static constexpr uintptr_t kLongjmp1Off = 0x17304;
            static constexpr uintptr_t kLongjmp2Off = 0x1b4d0;
            static constexpr uintptr_t kAudioOff    = 0x1d760;
            static constexpr uint32_t  kRetInsn     = 0xd65f03c0;

            // Round down to page boundary; the two sites + the audio
            // site all live within ~0x6500 bytes, spanning a couple
            // of 4K pages. Unprotect a generous range.
            long pageSize = sysconf(_SC_PAGESIZE);
            if (pageSize <= 0) pageSize = 4096;
            uint8_t* start = base + kLongjmp1Off;
            uint8_t* end   = base + kAudioOff + 4;
            uint8_t* pageStart = reinterpret_cast<uint8_t*>(
                    reinterpret_cast<uintptr_t>(start) & ~(pageSize - 1));
            size_t pageLen =
                    (size_t)(reinterpret_cast<uintptr_t>(end)
                             - reinterpret_cast<uintptr_t>(pageStart));
            pageLen = (pageLen + pageSize - 1) & ~(size_t)(pageSize - 1);

            if (mprotect(pageStart, pageLen,
                         PROT_READ | PROT_WRITE | PROT_EXEC) == 0) {
                uint32_t* p1 = reinterpret_cast<uint32_t*>(
                        base + kLongjmp1Off);
                uint32_t* p2 = reinterpret_cast<uint32_t*>(
                        base + kLongjmp2Off);
                *p1 = kRetInsn;
                *p2 = kRetInsn;
                ALOGI("DrasticRunner: longjmp patches applied at "
                      "+0x17304 +0x1b4d0");

                if (!soundEnabled) {
                    // Also short-circuit initialize_audio so drastic
                    // does not stall 15s on slCreateEngine's binder
                    // wait. Only relevant when the caller does NOT
                    // want sound -- the on-disk nano_cache patch
                    // handles this already for gammaos-nano, but
                    // applying in memory makes the effect idempotent
                    // across both paths.
                    uint32_t* pa = reinterpret_cast<uint32_t*>(
                            base + kAudioOff);
                    *pa = kRetInsn;
                    ALOGI("DrasticRunner: audio-init short-circuit "
                          "applied at +0x1d760 (sound disabled)");
                }

                if (mprotect(pageStart, pageLen,
                             PROT_READ | PROT_EXEC) != 0) {
                    ALOGW("DrasticRunner: mprotect restore "
                          "failed: %s", strerror(errno));
                }
                __builtin___clear_cache(
                        reinterpret_cast<char*>(pageStart),
                        reinterpret_cast<char*>(pageStart + pageLen));
            } else {
                ALOGW("DrasticRunner: mprotect for longjmp patches "
                      "failed: %s -- drastic may SEGV on per-frame "
                      "path", strerror(errno));
            }
        }
    }

    // ---- Phase 2: resolve JNI entry points ----
    typedef jint (*JNI_OnLoad_t)(JavaVM*, void*);
    JNI_OnLoad_t onLoad = (JNI_OnLoad_t)dlsym(mArm64Handle, "JNI_OnLoad");
    if (!onLoad) {
        ALOGE("DrasticRunner: JNI_OnLoad not found: %s", dlerror());
        shutdown();
        return false;
    }

    if (!loadSym(mOnInit,       "Java_com_dsemu_drastic_DraSticJNI_onInit")) return false;
    if (!loadSym(mApplyConfig,  "Java_com_dsemu_drastic_DraSticJNI_applyConfig")) return false;
    if (!loadSym(mStartGame,    "Java_com_dsemu_drastic_DraSticJNI_startGame")) return false;
    if (!loadSym(mPauseSystem,  "Java_com_dsemu_drastic_DraSticJNI_pauseSystem")) return false;
    if (!loadSym(mQuitSystem,   "Java_com_dsemu_drastic_DraSticJNI_quitSystem")) return false;
    // Best-effort: these are in the normal activity's pre-startGame
    // sequence (DraSticEmuActivity.run → setFirmwareUserdata →
    // setAutosaveInterval → startGame). Failing to call them shouldn't
    // break drastic but might hit a slow "default firmware" path at
    // boot. We resolve them but don't treat missing symbols as fatal.
    loadSym(mSetFirmwareUserdata, "Java_com_dsemu_drastic_DraSticJNI_setFirmwareUserdata");
    loadSym(mSetAutosaveInterval, "Java_com_dsemu_drastic_DraSticJNI_setAutosaveInterval");
    loadSym(mSetAudioVolume,      "Java_com_dsemu_drastic_DraSticJNI_setAudioVolume");
    // Runtime control hooks used by the overlay menu. All optional --
    // missing symbols just disable the corresponding UI action.
    loadSym(mSaveState, "Java_com_dsemu_drastic_DraSticJNI_saveState");
    loadSym(mLoadState, "Java_com_dsemu_drastic_DraSticJNI_loadState");
    loadSym(mResetDS,   "Java_com_dsemu_drastic_DraSticJNI_resetDS");
    // Cheat API (all optional -- the overlay hides the Cheats tab if the
    // core lacks them).
    loadSym(mGetCheatCount,       "Java_com_dsemu_drastic_DraSticJNI_getCheatCount");
    loadSym(mGetCheatFolderCount, "Java_com_dsemu_drastic_DraSticJNI_getCheatFolderCount");
    loadSym(mGetCheatName,        "Java_com_dsemu_drastic_DraSticJNI_getCheatName");
    loadSym(mGetCheatNote,        "Java_com_dsemu_drastic_DraSticJNI_getCheatNote");
    loadSym(mGetCheatFolderName,  "Java_com_dsemu_drastic_DraSticJNI_getCheatFolderName");
    loadSym(mGetCheatEnabled,     "Java_com_dsemu_drastic_DraSticJNI_getCheatEnabled");
    loadSym(mGetCheatFolderMultiSelect,
            "Java_com_dsemu_drastic_DraSticJNI_getCheatFolderMultiSelect");
    loadSym(mGetCheatFolderId,    "Java_com_dsemu_drastic_DraSticJNI_getCheatFolderId");
    loadSym(mSetCheatEnabled,     "Java_com_dsemu_drastic_DraSticJNI_setCheatEnabled");
    loadSym(mUpdateCheats,        "Java_com_dsemu_drastic_DraSticJNI_updateCheats");
    loadSym(mGetCustomCheatCount, "Java_com_dsemu_drastic_DraSticJNI_getCustomCheatCount");
    loadSym(mGetCustomCheatName,  "Java_com_dsemu_drastic_DraSticJNI_getCustomCheatName");
    loadSym(mGetCustomCheatEnabled,
            "Java_com_dsemu_drastic_DraSticJNI_getCustomCheatEnabled");
    loadSym(mSetCustomCheatEnabled,
            "Java_com_dsemu_drastic_DraSticJNI_setCustomCheatEnabled");
    loadSym(mGetCustomCheatData,  "Java_com_dsemu_drastic_DraSticJNI_getCustomCheatData");
    loadSym(mRemoveCustomCheat,   "Java_com_dsemu_drastic_DraSticJNI_removeCustomCheat");
    loadSym(mAddCustomCheat,      "Java_com_dsemu_drastic_DraSticJNI_addCustomCheat");
    loadSym(mFindCustomCheat,     "Java_com_dsemu_drastic_DraSticJNI_findCustomCheat");
    loadSym(mFxLoad,                "Java_com_dsemu_drastic_DraSticJNI_fxLoad"); // optional
    if (!loadSym(mFxSetup,          "Java_com_dsemu_drastic_DraSticJNI_fxSetup")) return false;
    if (!loadSym(mRenderFrame,      "Java_com_dsemu_drastic_DraSticJNI_renderFrame")) return false;
    // Optional -- when present, renderDsToOffscreen prefers fxRender
    // over renderFrame for the per-frame render pass, which is what
    // actually invokes the loaded .dfx shader (renderFrame never
    // calls glUseProgram so shaders are invisible on that path).
    loadSym(mFxRender,              "Java_com_dsemu_drastic_DraSticJNI_fxRender");
    if (!loadSym(mSignalScreen,     "Java_com_dsemu_drastic_DraSticJNI_signalScreen")) return false;
    if (!loadSym(mWaitScreen,       "Java_com_dsemu_drastic_DraSticJNI_waitScreen")) return false;
    if (!loadSym(mUpdateFrame,      "Java_com_dsemu_drastic_DraSticJNI_updateFrame")) return false;
    if (!loadSym(mUpdateInput,      "Java_com_dsemu_drastic_DraSticJNI_updateInput")) return false;
    if (!loadSym(mGetScreenBuffers, "Java_com_dsemu_drastic_DraSticJNI_getScreenBuffers")) return false;

    // The fxRender shader pipeline (fxLoad -> fxSetup -> patchFinalPassFbo
    // -> fxRender) is only meaningful for full drastic-nano standalone,
    // where we ship .dfx files under the real drastic files dir. In QR
    // preview mode there is no .dfx shader in /data/system/nano_cache/
    // drastic, so fxLoad never builds a pass list; calling fxRender then
    // either no-ops or writes to whatever FBO was last active. With the
    // SurfaceFlinger composition gate suppressing the EGL surface, that
    // dead path produced the red canary left in mOffscreenFbo.
    //
    // Force the renderFrame path for QR preview by nulling the shader
    // entry points. renderDsToOffscreen()'s `if (mFxRender)` branch then
    // falls through to renderFrame, which uploads DS frames directly
    // into mDsTopTex / mDsBotTex, and renderTopScreen / renderBottomScreen
    // sample those textures (see the `if (mFxRender && mOffscreenTex)`
    // checks there).
    {
        // The fxRender shader path is for a real play session. Two signals
        // mark one: the persist drastic-nano feature flag (set on devices that
        // route DS launches through drastic-nano, also the home's launch gate)
        // and sys.gammaos.drastic_nano.session, which the drastic-nano binary
        // sets for its own lifetime. The home's QR preview sets neither, so it
        // stays on the renderFrame path and never walks an empty pass list
        // (the SurfaceFlinger-gated dead path that left a red canary). Gating on
        // the session prop too means the player binary always shades, even when
        // the persist feature flag has not been set on the device.
        char dnProp[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.drastic_nano", dnProp, "0");
        const bool realSession =
                (dnProp[0] == '1') ||
                property_get_bool("sys.gammaos.drastic_nano.session", false);
        if (!realSession) {
            ALOGI("DrasticRunner: not a real session -- disabling fxRender "
                  "shader path, using renderFrame (QR preview)");
            mFxRender = nullptr;
            mFxLoad   = nullptr;
        }
    }

    // ---- Phase 3: fake JNI setup ----
    fakejni::setCacheRoot(cacheDir);
    JavaVM* fakeVm = fakejni::init();
    jint onLoadRc = onLoad(fakeVm, nullptr);
    if (onLoadRc != JNI_VERSION_1_6) {
        ALOGW("DrasticRunner: JNI_OnLoad returned unexpected 0x%x "
              "(continuing)", onLoadRc);
    }
    ALOGI("DrasticRunner: JNI_OnLoad ok (0x%x)", onLoadRc);

    // Grab a JNIEnv* for the lifetime calls below. We use JavaVM::GetEnv
    // via the fake vtable; this returns our singleton FakeJNIEnv.
    JNIEnv* env = nullptr;
    if (fakeVm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK || !env) {
        ALOGE("DrasticRunner: fake GetEnv failed");
        shutdown();
        return false;
    }

    // jclass doesn't matter -- our lifetime dispatchers don't look at
    // it, but the JNI ABI wants something non-null in x1.
    void* fakeCls = (void*)(uintptr_t)0x1000;

    // Cache env + cls for render-thread calls (initSurface, renderOneFrame).
    mFakeEnv = env;
    mFakeCls = fakeCls;

    // ---- Phase 4: onInit ----
    // From DraSticActivity.smali:3036:
    //   onInit(activityContext, versionCode, Build.VERSION.SDK_INT)
    // The disasm at 0x17df4 shows only w3 (versionCode) and w4 (sdkInt)
    // are actually read; the context is ignored. Pass nullptr for it.
    ALOGI("DrasticRunner: calling onInit");
    mOnInit(env, fakeCls, nullptr, 109, 33);
    ALOGI("DrasticRunner: onInit returned");

    // The early (post-onInit) master-state patch attempted in an
    // earlier iteration was ineffective: drastic resets all of
    // 0x10 / 0x14 / 0x4b8 / 0x9140 between onInit and the first
    // rendered frame. The +0x4b8 case is now handled cleanly by
    // setting config bit 50 (_m0) in kDefaultConfigBits above, which
    // flows through applyConfig and survives. The remaining writes
    // are retried post-startGame by a monitor thread spawned just
    // after mStartGameThread.detach(), further down this function.

    // ---- Phase 5: applyConfig ----
    // bit 31 = _SoundEnabled. Clear when using the patched libdrastic
    // whose initialize_audio was short-circuited to ret (no
    // slCreateEngine, so the per-frame mixer must also be disabled or
    // it dereferences NULL engine pointers). Set when running the
    // unpatched library so the real audio path comes up.
    //
    // When the caller supplies configBitsOverride (non-zero), that
    // replaces the compiled-in kDefaultConfigBits -- drastic-nano
    // passes user-XML-derived bits this way. soundEnabled still forces
    // bit 31 regardless of the override.
    long configBits = (configBitsOverride != 0) ? configBitsOverride
                                                : kDefaultConfigBits;
    if (soundEnabled) {
        configBits |= 0x80000000L;
    }
    ALOGI("DrasticRunner: calling applyConfig(0x%lx) sound=%d (override=%s)",
          configBits, soundEnabled ? 1 : 0,
          configBitsOverride != 0 ? "yes" : "no");
    mApplyConfig(env, fakeCls, configBits);
    mBaseConfigBits = configBits;
    ALOGI("DrasticRunner: applyConfig returned");

    // ---- Phase 6: startGame on a dedicated thread ----
    //
    // Empirical finding (Phase 3 on-device test): drastic's startGame
    // is the emulator main loop and does NOT return. It enters a
    // producer/consumer frame pacer spinning at ~60Hz for the DS CPU
    // emulation, with a small pool of rasterizer workers parked on
    // futexes. Normal Android drastic hands this off to the Java UI
    // thread, but there the activity keeps running until finish().
    //
    // We can't afford to block NanoMenu's main thread, so we spawn
    // startGame on a detached worker. The thread captures the fake
    // env + rom jstring + method pointer and calls in. It never
    // returns under normal operation; if it ever does, we log a
    // warning so we notice.
    jstring romJStr = env->NewStringUTF(romPath.c_str());
    if (!romJStr) {
        ALOGE("DrasticRunner: NewStringUTF for rom path failed");
        shutdown();
        return false;
    }

    // Phase 5 v11: match the real activity's pre-startGame JNI
    // sequence (DraSticEmuActivity.smali:1172, 2243, 2290):
    //   setAudioVolume → setFirmwareUserdata → setAutosaveInterval →
    //   startGame
    // setFirmwareUserdata uses GetStringChars (vtable slot 165) +
    // GetStringLength (slot 164); both are now implemented in
    // FakeJNI. Nick is ASCII ("Player"), color is a non-zero ARGB.
    if (mSetAudioVolume) {
        ALOGI("DrasticRunner: setAudioVolume(40)");
        mSetAudioVolume(env, fakeCls, 40);
    }
    if (mSetFirmwareUserdata) {
        jstring nick = env->NewStringUTF("GammaOS");
        if (nick) {
            // Packed firmware userdata: (bday_day << 24) | (bday_month << 16) | (color << 8) | language
            // Language: 1=English, Color: 0=grey, Birthday: Jan 1
            const int fwPacked = (1 << 24) | (1 << 16) | (0 << 8) | 1; // 0x01010001
            ALOGI("DrasticRunner: setFirmwareUserdata(\"GammaOS\", "
                  "0x%08x)", fwPacked);
            mSetFirmwareUserdata(env, fakeCls, nick, fwPacked);
            ALOGI("DrasticRunner: setFirmwareUserdata returned");
        }
    }
    if (mSetAutosaveInterval) {
        ALOGI("DrasticRunner: setAutosaveInterval(%d)",
              autosaveIntervalSeconds);
        mSetAutosaveInterval(env, fakeCls, autosaveIntervalSeconds);
    }

    // Phase 5 v10 fix: call fxSetup BEFORE spawning the startGame
    // thread.
    //
    // Boot-time thread trace finding (2026-04-11): when drastic's
    // startGame is called before fxSetup has touched the internal
    // video state, the startGame thread enters a futex_wait and
    // stays there for ~14 seconds (ut=0 the whole time), eventually
    // waking up only when... something. The natural flow in the real
    // app is:
    //   onCreate → setContentView(GLSurfaceView) →
    //     GL thread: onSurfaceChanged → fxSetup →
    //   UI thread: startGame
    // i.e. fxSetup completes BEFORE startGame is invoked. Our earlier
    // ordering (startGame → then fxSetup from NanoMenu render thread)
    // breaks that assumption and drastic's internal initialize_video
    // → worker pool → pthread_cond_wait state machine ends up
    // waiting for state that fxSetup would have set.
    //
    // fxSetup() itself is pure state: it doesn't touch GL, it writes
    // dimensions and flags into a master struct for later consumption
    // by the render loop. Calling it from our init() thread (not the
    // GL thread) is safe because no GL context is required at call
    // time. The actual GL setup happens later in initSurface() on the
    // render thread, and we keep that path doing its texture +
    // shader setup.
    //
    // The dimensions passed here are our final target viewport. Even
    // if the actual viewport we later initSurface with is different,
    // drastic re-uses the fxSetup dimensions only as a state marker
    // for which slot in the framebuffer pool to write into.
    // Phase 7: fxSetup sets a one-shot sentinel in BSS+2888. If we
    // call it here (no GL context), it writes dimensions/flags but
    // skips the GL work (shader compile, VAO). Then initSurface's
    // fxSetup on the GL thread sees the sentinel already set and
    // no-ops, so GL is never initialized --> black screen.
    //
    // When renderFrame is available, we SKIP the early fxSetup so
    // the initSurface call is the first (and only) fxSetup, running
    // on the GL thread with the correct portrait viewport. startGame
    // may stall ~1-2s longer without the early fxSetup but SCHED_RR
    // boost compensates.
    //
    // When renderFrame is NOT available (fallback to getScreenBuffers),
    // the early fxSetup is still needed because the sentinel must be
    // set before startGame to avoid the 14s futex stall, and the GL
    // parts aren't needed for getScreenBuffers.
    if (mFxSetup && !mRenderFrame) {
        ALOGI("DrasticRunner: early fxSetup(%d, %d, 0, 0, 640, 480) "
              "from init thread (pre-startGame, legacy path)",
              kDsScreenW, kDsScreenH);
        mFxSetup(env, fakeCls,
                 kDsScreenW, kDsScreenH, 0, 0,
                 /*viewW*/ 640, /*viewH*/ 480);
        ALOGI("DrasticRunner: early fxSetup returned");
    } else if (mFxSetup && mRenderFrame) {
        ALOGI("DrasticRunner: skipping early fxSetup (renderFrame path "
              "-- initSurface will call fxSetup on the GL thread)");
    }

    ALOGI("DrasticRunner: spawning startGame thread with rom=%s",
          romPath.c_str());

    auto startGameFn  = mStartGame;
    void* fakeClsCopy = fakeCls;
    JNIEnv* envCopy   = env;
    jstring romCopy   = romJStr;
    long startGameConfig = configBits;

    // Phase 5 v9: startGame arg3 IS the config bits long, not zero.
    // DraSticEmuActivity.smali:2319-2327 shows the real call:
    //   v2=rom v3=slot v4/v5=f0.h.n() v6=0 v7=insertMode v8/v9=customClock
    //   invoke-static/range {v2 .. v9}, startGame(LS;IJIZJ)Z
    // Passing 0 for the config long means drastic sees _Threaded3D OFF
    // and all other perf-related config bits clear, which forces the
    // rasterizer into a single-threaded slow path. The applyConfig()
    // call we do earlier sets the static Java-side state, but startGame
    // re-reads its arg3 internally.
    //
    // arg2 (slot): user says save state is a red herring. Keep 0 (no
    //   auto-load). The smali normally passes a slot from `->o:I`
    //   field which is usually -1 unless _ShortcutAutoResume fired.
    // arg3 (configBits): kDefaultConfigBits -- THE FIX.
    // arg5 (insertMode): 0 = fresh boot (first startGame call path).
    // arg6 (customClock): 0 = default DS clock (no overclock).
    mStartGameThread = std::thread([this, envCopy, fakeClsCopy, romCopy,
                                    startGameFn, startGameConfig]() {
        // Self-boost BEFORE calling startGameFn, so drastic's own
        // pthread_create calls for rasterizer workers inherit our
        // elevated scheduling class. pthread_create without explicit
        // sched attrs uses PTHREAD_INHERIT_SCHED on bionic (confirmed:
        // bionic/libc/bionic/pthread_create.cpp), so a child thread
        // starts with the same policy+priority as the creating thread.
        // This means boosting the startGame thread BEFORE it calls
        // drastic's startGame() cascades the boost to every thread
        // drastic spawns internally.
        //
        // Try SCHED_RR at a low real-time priority first (preempts
        // SCHED_OTHER from init/zygote/system_server, yields to any
        // higher RT thread like audio or kernel workers). Fall back
        // to nice=-20 (strongest SCHED_OTHER) if RT isn't granted.
        {
            sched_param sp = {};
            sp.sched_priority = 5;
            int rc = pthread_setschedparam(pthread_self(),
                                           SCHED_RR, &sp);
            if (rc == 0) {
                ALOGI("DrasticRunner: startGame self-boost SCHED_RR "
                      "prio 5 ok");
            } else {
                ALOGW("DrasticRunner: startGame SCHED_RR failed (%s), "
                      "falling back to nice=-20", strerror(rc));
                pid_t selfTid = (pid_t)syscall(SYS_gettid);
                if (setpriority(PRIO_PROCESS, selfTid, -20) != 0) {
                    ALOGW("DrasticRunner: startGame nice=-20 also "
                          "failed: %s", strerror(errno));
                }
            }
        }
        mStartGameLaunched.store(true);
        // arg2 (slot): drastic's startGame loads this save slot on boot
        // when it is >= 0 (with arg4 == 0). mAutoLoadSlot is 9 to
        // auto-resume the mod's autosave, or -1 for a fresh boot; the
        // legacy QR-preview callers leave it at 0.
        unsigned char rc = startGameFn(envCopy, fakeClsCopy, romCopy,
                                        /*arg2 slot*/  mAutoLoadSlot,
                                        /*arg3 cfg*/   startGameConfig,
                                        /*arg4*/       0,
                                        /*arg5 insrt*/ 0,
                                        /*arg6 clock*/ -1L);
        // Reaching here is unexpected -- startGame is drastic's main
        // emulator loop and normally runs until the process exits.
        ALOGW("DrasticRunner: startGame RETURNED (unexpected) rc=%d",
              (int)rc);
    });
    // Boost the startGame thread to SCHED_RR before it begins its
    // emulator init work, so the boot-time CPU contention window
    // (init / zygote / system_server / vendor HALs all fighting for
    // cores) doesn't starve drastic. See drasticBoostThread comment.
    drasticBoostThread(mStartGameThread.native_handle(), "startGame");
    mStartGameThread.detach();

    // ---- Post-startGame master-state patch ----
    //
    // A/B dump comparison against the real Drastic app identified 13
    // u32 scalars in master that differ between nano and the real app
    // regardless of which game is loaded. Patching them to the real-
    // app values fixes the BG-layer priority rendering bug.
    //
    // Engine A (near master+0x0):
    //   +0x00010  android=6 nano=1    (renderer capability, paired)
    //   +0x00014  android=6 nano=1    (renderer capability, paired)
    //   +0x09140  android=0 nano=1    (flag, inverted direction)
    // Second symmetric capability cluster near master+0x8b680:
    //   +0x8b68c  android=6 nano=1    (mirrors +0x10)
    //   +0x8b690  android=6 nano=1    (mirrors +0x14)
    //   +0x8ba98  android=0 nano=2    (inverted)
    //   +0x8bab8  android=1 nano=0
    //   +0x8bad0  android=1 nano=0
    //   +0x8badc  android=1 nano=0
    //   +0x8bae8  android=3 nano=0
    //   +0x8bb00  android=1 nano=0
    //   +0x8bb10  android=1 nano=0
    //   +0x8bb28  android=1 nano=0
    //
    // Two other diffs from the same methodology are not patched here:
    //   +0x004b8 -- handled cleanly via applyConfig bit 50 (_m0) above
    //   +0x017a4 / +0x017a8 -- backed by a NULL pointer cluster at
    //       +0x17b0..+0x17cc on nano; flipping the flags without
    //       populating the pointers would deref NULL.
    //
    // Timing:
    //   Drastic's startGame init overwrites these values once during
    //   its own setup, then never touches them again. Empirical monitor
    //   (30 passes x 100ms) showed 11/13 reset at the 100ms mark and
    //   zero drift from 200ms onward. A one-shot 250ms-delayed patch
    //   is enough; we intentionally run it on a detached thread so
    //   init() does not block.
    //
    // Guarded by persist.gammaos.nano.drastic_master_patch
    // (default "1", set "0" to disable for A/B comparison). The actual
    // rewrite lives in applyMasterStatePatch() so the same patch can be
    // re-applied after a runtime applyConfig() (see setFastForward).
    {
        char patchEnable[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.drastic_master_patch",
                     patchEnable, "1");
        bool applyPatch = (patchEnable[0] != '0');

        if (applyPatch) {
            std::thread([this]() {
                pthread_setname_np(pthread_self(), "drastic-patch");
                // Wait past drastic's one-shot reset window: the monitor
                // audit showed all drift happens by the 100ms mark and
                // none from 200ms onward, so 250ms is a safe margin.
                usleep(250 * 1000);
                applyMasterStatePatch("startGame init");
            }).detach();
            ALOGW("DrasticRunner: master-state patch scheduled "
                  "(runs 250ms after startGame)");
        } else {
            ALOGW("DrasticRunner: master-state patch disabled via prop");
        }
    }

    // Pre-allocate the int arrays for getScreenBuffers RIGHT HERE
    // (rather than waiting for initSurface on the render thread).
    // This lets the background pixel-pull thread start producing
    // frames immediately without waiting for EGL + render-thread
    // setup. The arrays live in our fakejni pool so they're safe
    // to share across threads.
    mTopArr = (void*)fakejni::allocIntArray(kDsScreenPixels);
    mBotArr = (void*)fakejni::allocIntArray(kDsScreenPixels);

    // Background pixel-pull thread. Keeps calling getScreenBuffers
    // on drastic's timing (may block initially for 10+ seconds until
    // the ROM boots) and writes the latest frame into a mutex-
    // protected shadow buffer pair. The render thread reads the
    // shadow buffers via updatePixels() without blocking.
    mTopShadow.resize(kDsScreenPixels, 0);
    mBotShadow.resize(kDsScreenPixels, 0);
    mPixelPullRunning.store(true);
    mPixelPullThread = std::thread([this]() {
        // Self-boost just like the startGame thread — see the note in
        // the startGame lambda above for rationale. The pull thread is
        // also critical path: if drastic produces a frame but we don't
        // consume it fast enough (getScreenBuffers blocks internally
        // on the producer mutex), the DS CPU thread back-pressures
        // and falls further behind.
        {
            sched_param sp = {};
            sp.sched_priority = 5;
            int rc = pthread_setschedparam(pthread_self(),
                                           SCHED_RR, &sp);
            if (rc != 0) {
                ALOGW("DrasticRunner: pull SCHED_RR failed (%s), "
                      "falling back to nice=-20", strerror(rc));
                pid_t selfTid = (pid_t)syscall(SYS_gettid);
                if (setpriority(PRIO_PROCESS, selfTid, -20) != 0) {
                    ALOGW("DrasticRunner: pull nice=-20 also failed: "
                          "%s", strerror(errno));
                }
            }
        }
        ALOGI("DrasticRunner: pixel-pull thread starting");
        if (!mPixelPullRunning.load()) return;

        ALOGI("DrasticRunner: pixel-pull thread: arrays ready, pulling frames");
        int64_t firstFrameLogged = 0;
        while (mPixelPullRunning.load()) {
            // These calls may block internally until drastic's DS
            // CPU thread has produced a frame. That's FINE on this
            // background thread -- the render thread keeps running.
            if (mWaitScreen) mWaitScreen(mFakeEnv, mFakeCls);
            if (mGetScreenBuffers) {
                mGetScreenBuffers(mFakeEnv, mFakeCls, mTopArr, mBotArr);
            }
            if (mSignalScreen) mSignalScreen(mFakeEnv, mFakeCls);

            const jint* top = fakejni::getIntArrayData((jintArray)mTopArr);
            const jint* bot = fakejni::getIntArrayData((jintArray)mBotArr);
            if (top && bot) {
                {
                    std::lock_guard<std::mutex> lock(mShadowMutex);
                    memcpy(mTopShadow.data(), top,
                           kDsScreenPixels * sizeof(jint));
                    memcpy(mBotShadow.data(), bot,
                           kDsScreenPixels * sizeof(jint));
                }
                mShadowReady.store(true);
                int fc = mFrameCounter.fetch_add(1) + 1;
                // Wake any frame-ready waiter (waitForFrameAfter). Note this
                // notify only fires while the pixel-pull thread runs; once
                // renderDsToOffscreen takes over the renderFrame/fxRender path
                // it stops the pull thread, so mFrameCounter freezes here.
                // RetroAchievements do_frame is NOT driven off this wake on
                // that path; it runs off the render-loop vblank tick (main.cpp
                // onRenderFrame), since this counter is dead once pull stops.
                mFrameCv.notify_all();
                if (firstFrameLogged == 0) {
                    firstFrameLogged = 1;
                    ALOGW("DrasticRunner: first drastic frame produced");
                }
                (void)fc;
            }
        }
        ALOGI("DrasticRunner: pixel-pull thread exiting");
    });
    drasticBoostThread(mPixelPullThread.native_handle(), "pixel-pull");
    mPixelPullThread.detach();

    // Wait briefly so that the worker has actually entered startGame
    // before we return. Not strictly required for correctness -- it
    // just makes log ordering cleaner on the first few frames.
    for (int i = 0; i < 50 && !mStartGameLaunched.load(); i++) {
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }

    mInitialized = true;

    // Set the DS RTC to Jan 1 2020 00:00. With setAutosaveInterval(0)
    // and _RtcSystemTime clear, drastic's RTC handler computes time
    // from an internal offset at master+0x14c4a0 plus the emulated
    // cycle counter. The offset defaults to 0 (DS epoch = 2000-01-01).
    // Writing 631152000 (seconds from 2000-01-01 to 2020-01-01) sets
    // the base date to 2020.
    //
    // master base is at offset 0x14c000 from the .so load address.
    // We derive the load address from a known symbol (updateInput at
    // file offset 0x1a5d8).
    {
        Dl_info info;
        if (mUpdateInput && dladdr((void*)mUpdateInput, &info)
                && info.dli_fbase) {
            uintptr_t soBase = (uintptr_t)info.dli_fbase;
            volatile uint64_t* rtcOffset =
                    (volatile uint64_t*)(soBase + 0x14c4a0);
            // 20 years: 2000..2004..2008..2012..2016..2020 = 5 leap years
            // = 20*365 + 5 = 7305 days = 631,152,000 seconds
            *rtcOffset = 631152000ULL;
            ALOGI("DrasticRunner: RTC offset set to 631152000 "
                  "(2020-01-01) at %p (soBase=%p)",
                  (void*)rtcOffset, (void*)soBase);
        } else {
            ALOGW("DrasticRunner: could not resolve .so base for "
                  "RTC offset write");
        }
    }

    // Publish ourselves as the singleton so NanoMenu's render thread
    // can pick us up and call initSurface() / renderOneFrame().
    sInstance.store(this, std::memory_order_release);

    ALOGI("DrasticRunner: init complete (startGame running on bg thread)");
    return true;
}

// -------- Phase 4: GL surface setup + render (Option B) --------
//
// We use the getScreenBuffers path: drastic copies the DS framebuffer
// from its internal pool into caller-provided int arrays, we upload
// those pixels into our own GL textures and draw two quads (top and
// bottom DS screens) stacked vertically. This is self-contained and
// avoids having to reproduce drastic's full Java-side GL pipeline
// (shaders, 9 textures, VBO, attrib pointers).
//
// Native mode DS resolution: 256 x 192 per screen, RGBA8888 packed
// into one jint per pixel. Two screens = 2 * 256 * 192 = 98304 jints.
// kDsScreenW/H/Pixels live at the top of this file so init() can
// see them.

static const char* kDrasticVs =
    "attribute vec2 aPos;\n"
    "attribute vec2 aUv;\n"
    "uniform mat2 uRotation;\n"
    "uniform vec4 uUvRect;\n" // xy = uv origin, zw = uv size
    "varying vec2 vUv;\n"
    "void main() {\n"
    "  gl_Position = vec4(uRotation * aPos, 0.0, 1.0);\n"
    "  vUv = uUvRect.xy + aUv * uUvRect.zw;\n"
    "}\n";

static const char* kDrasticFs =
    "precision mediump float;\n"
    "varying vec2 vUv;\n"
    "uniform sampler2D uTex;\n"
    "uniform float uSaturation;\n"  // 0=grayscale, 1=full color
    "uniform float uGradient;\n"    // 0=none, 1=strong dark gradient
    "void main() {\n"
    "  vec4 c = texture2D(uTex, vUv);\n"
    "  vec3 rgb = c.rgb;\n"
    "  float gray = dot(rgb, vec3(0.299, 0.587, 0.114));\n"
    "  rgb = mix(vec3(gray), rgb, uSaturation);\n"
    "  float fade = smoothstep(0.35, 0.85, vUv.y) * uGradient;\n"
    "  rgb *= (1.0 - fade);\n"
    "  gl_FragColor = vec4(rgb, 1.0);\n"
    "}\n";

// Fast-forward blit shader: identical to kDrasticFs but cross-fades the
// current frame (uTex) with the previous one (uPrevTex) before the
// saturation/gradient math. uBlend is the weight of the CURRENT frame
// (0.5 = equal average = maximum motion-blur smoothing, 1.0 = no trail).
// Used ONLY while fast-forward is held; the 1x path keeps mQuadProgram.
static const char* kDrasticFsBlend =
    "precision mediump float;\n"
    "varying vec2 vUv;\n"
    "uniform sampler2D uTex;\n"
    "uniform sampler2D uPrevTex;\n"
    "uniform float uBlend;\n"
    "uniform float uSaturation;\n"
    "uniform float uGradient;\n"
    "void main() {\n"
    "  vec3 cur  = texture2D(uTex, vUv).rgb;\n"
    "  vec3 prev = texture2D(uPrevTex, vUv).rgb;\n"
    "  vec3 rgb = mix(prev, cur, uBlend);\n"
    "  float gray = dot(rgb, vec3(0.299, 0.587, 0.114));\n"
    "  rgb = mix(vec3(gray), rgb, uSaturation);\n"
    "  float fade = smoothstep(0.35, 0.85, vUv.y) * uGradient;\n"
    "  rgb *= (1.0 - fade);\n"
    "  gl_FragColor = vec4(rgb, 1.0);\n"
    "}\n";

static GLuint drCompileShader(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint status = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &status);
    if (!status) {
        char log[512] = {};
        glGetShaderInfoLog(s, sizeof(log), nullptr, log);
        ALOGE("DrasticRunner: shader compile failed: %s", log);
    }
    return s;
}

static GLuint drLinkProgram(const char* vs, const char* fs) {
    GLuint v = drCompileShader(GL_VERTEX_SHADER, vs);
    GLuint f = drCompileShader(GL_FRAGMENT_SHADER, fs);
    GLuint p = glCreateProgram();
    glAttachShader(p, v);
    glAttachShader(p, f);
    glLinkProgram(p);
    GLint status = 0;
    glGetProgramiv(p, GL_LINK_STATUS, &status);
    if (!status) {
        char log[512] = {};
        glGetProgramInfoLog(p, sizeof(log), nullptr, log);
        ALOGE("DrasticRunner: program link failed: %s", log);
    }
    glDeleteShader(v);
    glDeleteShader(f);
    return p;
}

void DrasticRunner::initSurface(int viewportW, int viewportH,
                                bool dualDisplay) {
    if (mSurfaceReady) return;
    if (!mInitialized) {
        ALOGE("DrasticRunner::initSurface: not initialized");
        return;
    }

    mDualDisplay = dualDisplay;
    mUseRenderFrame = (mRenderFrame != nullptr);

    // Portrait offscreen dimensions: both DS screens stacked.
    // Each screen gets the full viewport width; height is doubled
    // for dual-display (top screen + bottom screen stacked).
    mOffscreenW = viewportW;
    mOffscreenH = dualDisplay ? viewportH * 2 : viewportH;

    // Call fxSetup with the offscreen dimensions. This initializes
    // drastic's internal shader pipeline, texture pool, and viewport
    // for the renderFrame path. The vertex data drastic creates will
    // lay out both screens in a portrait stack within this viewport.
    // IMPORTANT: compile our blit shader and create GL resources
    // BEFORE calling fxSetup. renderFrame relies on fxSetup's GL
    // state (program, vertex attribs, texture bindings) being intact.
    // If we compile shaders after fxSetup, the active GL program
    // changes and renderFrame draws black. By doing our setup first,
    // fxSetup has the last word on GL state.
    //
    // Compile the blit shader (samples offscreen texture -> display).
    mQuadProgram = drLinkProgram(kDrasticVs, kDrasticFs);
    mQuadPosLoc      = glGetAttribLocation(mQuadProgram, "aPos");
    mQuadTexLoc      = glGetAttribLocation(mQuadProgram, "aUv");
    mQuadSamplerLoc  = glGetUniformLocation(mQuadProgram, "uTex");
    mQuadSatLoc      = glGetUniformLocation(mQuadProgram, "uSaturation");
    mQuadGradLoc     = glGetUniformLocation(mQuadProgram, "uGradient");
    mQuadRotLoc      = glGetUniformLocation(mQuadProgram, "uRotation");
    mQuadUvRectLoc   = glGetUniformLocation(mQuadProgram, "uUvRect");

    // Fast-forward blend program (cross-fade current + previous frame).
    // Separate program so the 1x quad shader stays byte-identical.
    mFfBlendProgram        = drLinkProgram(kDrasticVs, kDrasticFsBlend);
    mFfBlendPosLoc         = glGetAttribLocation(mFfBlendProgram, "aPos");
    mFfBlendTexLoc         = glGetAttribLocation(mFfBlendProgram, "aUv");
    mFfBlendSamplerLoc     = glGetUniformLocation(mFfBlendProgram, "uTex");
    mFfBlendPrevSamplerLoc = glGetUniformLocation(mFfBlendProgram, "uPrevTex");
    mFfBlendAmountLoc      = glGetUniformLocation(mFfBlendProgram, "uBlend");
    mFfBlendSatLoc         = glGetUniformLocation(mFfBlendProgram, "uSaturation");
    mFfBlendGradLoc        = glGetUniformLocation(mFfBlendProgram, "uGradient");
    mFfBlendRotLoc         = glGetUniformLocation(mFfBlendProgram, "uRotation");
    mFfBlendUvRectLoc      = glGetUniformLocation(mFfBlendProgram, "uUvRect");

    auto setupTex = [](unsigned int tex, int w, int h) {
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0,
                     GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    };

    if (mUseRenderFrame) {
        // DON'T stop the pixel-pull thread yet -- we need it to
        // detect when the DS producer has produced its first frame.
        // Without the early fxSetup (skipped for renderFrame path),
        // startGame stalls until initSurface's fxSetup runs, then
        // needs ~500ms to produce the first frame. renderFrame
        // returns empty data until then. The pixel-pull thread's
        // mShadowReady flag tells us when frame data is available.
        // We stop the pixel-pull once renderFrame takes over.
        ALOGI("DrasticRunner: keeping pixel-pull alive as frame detector");

        // Create textures for drastic's renderFrame to upload into.
        // renderFrame calls glTexSubImage2D with dimensions from
        // fxSetup's BSS state. drastic uploads each DS screen at (0,0)
        // sized 256x192 (native) or 512x384 (_Hires3D). Size the textures
        // to EXACTLY match the upload from the live _Hires3D bit, else a
        // native frame fills only the top-left quarter of a 512x384
        // texture (the "corner" bug). redimDsTextures() re-sizes these
        // when the user toggles Hi-res 3D in-game.
        int dsTexW, dsTexH;
        dsTexDims(dsHiresEnabled(), &dsTexW, &dsTexH);
        mDsTexW = dsTexW;
        mDsTexH = dsTexH;
        glGenTextures(1, &mDsTopTex);
        setupTex(mDsTopTex, dsTexW, dsTexH);
        glGenTextures(1, &mDsBotTex);
        setupTex(mDsBotTex, dsTexW, dsTexH);

        // Create the offscreen FBO + color attachment. drastic's
        // renderFrame draws into this FBO, then we blit halves to
        // the display FBOs.
        glGenTextures(1, &mOffscreenTex);
        setupTex(mOffscreenTex, mOffscreenW, mOffscreenH);
        glGenFramebuffers(1, &mOffscreenFbo);
        glBindFramebuffer(GL_FRAMEBUFFER, mOffscreenFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                               GL_TEXTURE_2D, mOffscreenTex, 0);
        GLenum fboStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (fboStatus != GL_FRAMEBUFFER_COMPLETE) {
            ALOGE("DrasticRunner: offscreen FBO incomplete: 0x%x",
                  fboStatus);
            mUseRenderFrame = false;
        } else {
            ALOGI("DrasticRunner: offscreen FBO %dx%d ready",
                  mOffscreenW, mOffscreenH);
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    if (!mUseRenderFrame) {
        // Fallback: legacy getScreenBuffers textures.
        glGenTextures(1, &mTopTex);
        setupTex(mTopTex, kDsScreenW, kDsScreenH);
        glGenTextures(1, &mBotTex);
        setupTex(mBotTex, kDsScreenW, kDsScreenH);
    }

    glGenBuffers(1, &mQuadVbo);
    // Upload the constant fullscreen quad geometry ONCE at init. The
    // verts are -1..+1 NDC with 0..1 UVs (drawDsQuad's vMin/vMax args
    // are always 0/1 across every call site -- renderTopScreen,
    // renderBottomScreen, renderBothScreens all pass 0,1). Previously
    // this was re-uploaded via glBufferData on every drawDsQuad call,
    // which was ~64 bytes of DMA + driver bookkeeping per frame per
    // display. Negligible per-call but adds up at 60 fps x 2 displays.
    {
        const float verts[16] = {
            -1.0f,  1.0f, 0.0f, 0.0f,
             1.0f,  1.0f, 1.0f, 0.0f,
            -1.0f, -1.0f, 0.0f, 1.0f,
             1.0f, -1.0f, 1.0f, 1.0f,
        };
        glBindBuffer(GL_ARRAY_BUFFER, mQuadVbo);
        glBufferData(GL_ARRAY_BUFFER, sizeof(verts), verts, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    // Build the VBO that fxRender's pass runner uses for vertex
    // attribs. drastic's pass runner at libdrastic+0x20690 issues:
    //   glVertexAttribPointer(pos_attr, 2, GL_FLOAT, 0, 0, pos_ptr)
    //   glVertexAttribPointer(uv_attr,  2, GL_FLOAT, 0, 0, uv_ptr)
    //   glDrawArrays(GL_TRIANGLES, first, 6)
    // Mode is GL_TRIANGLES (w0=0x4 at libdrastic+0x20874), NOT
    // GL_TRIANGLE_STRIP. 6 verts = 2 independent triangles per quad.
    //
    // pos_ptr and uv_ptr are the 2nd/3rd args we pass to fxLoad; they
    // land at fx_ctx+1128 and fx_ctx+1136. With a VBO bound, GL treats
    // them as byte offsets into the VBO rather than host addresses.
    //
    // fxRender issues TWO pass runner calls per frame; for multi-pass
    // shaders (LCD3x, scanline) each call runs the whole pass list.
    // The runner picks the 'first' arg of glDrawArrays based on
    // whether the current pass is the final pass or an intermediate:
    //   final pass    -> first=arg2 of pass runner ("frame arg": 0 or 6)
    //   intermediate  -> first=arg3 of pass runner (18 in both calls)
    // So our VBO must populate verts 0..5 (final top), 6..11 (final
    // bot) and 18..23 (intermediate passes). See libdrastic+0x2087c
    // (csel w1, w19, w20, eq) for the selection logic.
    //
    // Final-pass viewport is glViewport(fx_ctx+1156..1168) which
    // fxSetup populated as (0, 0, mOffscreenW, mOffscreenH). We render
    // the top DS screen at NDC y in [-1, 0] so it lands at framebuffer
    // pixel y in [0, 480] of the 640x960 mOffscreenFbo; bot DS goes at
    // NDC y in [0, +1] landing at pixel y in [480, 960]. drawDsQuad's
    // UV-flipped sampling in renderTopScreen (v=[0, 0.5]) then reads
    // pixel y=[0, 480] and puts it on the physical top display.
    // Intermediate passes draw a full-NDC quad (their viewport is
    // pass.viewW/H from the .dfx file).
    //
    // For GL_TRIANGLES a quad is 2 separate triangles:
    //   Triangle 1: BL, BR, TL (verts 0, 1, 2)
    //   Triangle 2: TL, BR, TR (verts 3, 4, 5)
    glGenBuffers(1, &mFxVbo);
    {
        // 24 vec2 positions (192 bytes) then 24 vec2 UVs (192 bytes).
        const float fxVerts[96] = {
            // TOP DS quad (verts 0..5), NDC y in [-1, 0].
            // T1: BL, BR, TL.  T2: TL, BR, TR.
            -1.0f, -1.0f,   +1.0f, -1.0f,   -1.0f,  0.0f,
            -1.0f,  0.0f,   +1.0f, -1.0f,   +1.0f,  0.0f,
            // BOT DS quad (verts 6..11), NDC y in [0, +1].
            -1.0f,  0.0f,   +1.0f,  0.0f,   -1.0f, +1.0f,
            -1.0f, +1.0f,   +1.0f,  0.0f,   +1.0f, +1.0f,
            // Verts 12..17: unused by the pass runner's draw calls
            // (no 'first=12' ever selected). Set to degenerate TR so
            // a stray fetch doesn't cause pathological coords.
            +1.0f, +1.0f,   +1.0f, +1.0f,   +1.0f, +1.0f,
            +1.0f, +1.0f,   +1.0f, +1.0f,   +1.0f, +1.0f,
            // Verts 18..23: intermediate passes (multi-pass shaders).
            // Full-NDC quad; the pass sets its own viewport.
            -1.0f, -1.0f,   +1.0f, -1.0f,   -1.0f, +1.0f,
            -1.0f, +1.0f,   +1.0f, -1.0f,   +1.0f, +1.0f,
            // UVs for TOP quad (verts 0..5). BL->(0,0) samples pixel
            // y=0 which has the DS top row (textures are uploaded
            // top-down so pixel y=0 = DS top row).
            0.0f, 0.0f,   1.0f, 0.0f,   0.0f, 1.0f,
            0.0f, 1.0f,   1.0f, 0.0f,   1.0f, 1.0f,
            // UVs for BOT quad (verts 6..11).
            0.0f, 0.0f,   1.0f, 0.0f,   0.0f, 1.0f,
            0.0f, 1.0f,   1.0f, 0.0f,   1.0f, 1.0f,
            // UVs for unused verts 12..17.
            1.0f, 1.0f,   1.0f, 1.0f,   1.0f, 1.0f,
            1.0f, 1.0f,   1.0f, 1.0f,   1.0f, 1.0f,
            // UVs for intermediate verts 18..23 (full texture).
            0.0f, 0.0f,   1.0f, 0.0f,   0.0f, 1.0f,
            0.0f, 1.0f,   1.0f, 0.0f,   1.0f, 1.0f,
        };
        glBindBuffer(GL_ARRAY_BUFFER, mFxVbo);
        glBufferData(GL_ARRAY_BUFFER, sizeof(fxVerts), fxVerts,
                     GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    // Dedicated VBO for renderSlotShaded (single-panel layout path). Same
    // pos@0 / uv@192 24-vertex layout fxLoad expects, so binding it before an
    // fxRender call is transparent to the pass runner -- but the shared mFxVbo
    // (used by renderDsToOffscreen on every other device, incl. the RG DS) is
    // never touched. verts 0..5 are a full-NDC quad with V-FLIPPED UVs so one
    // screen drawn full lands DS-top at the slot top, matching the drawDsQuad
    // orientation the layout composite expects; verts 6..11 are a degenerate
    // zero-area quad for the suppressed screen; verts 18..23 are the full-NDC
    // standard-UV quad the multi-pass prescale intermediates draw.
    glGenBuffers(1, &mSlotVbo);
    {
        const float slotVerts[96] = {
            // verts 0..5: active-screen quad (BL, BR, TL / TL, BR, TR).
            -1.0f, -1.0f,   +1.0f, -1.0f,   -1.0f, +1.0f,
            -1.0f, +1.0f,   +1.0f, -1.0f,   +1.0f, +1.0f,
            // verts 6..11: degenerate (suppressed screen).
            +1.0f, +1.0f,   +1.0f, +1.0f,   +1.0f, +1.0f,
            +1.0f, +1.0f,   +1.0f, +1.0f,   +1.0f, +1.0f,
            // verts 12..17: unused, degenerate.
            +1.0f, +1.0f,   +1.0f, +1.0f,   +1.0f, +1.0f,
            +1.0f, +1.0f,   +1.0f, +1.0f,   +1.0f, +1.0f,
            // verts 18..23: full-NDC intermediate quad.
            -1.0f, -1.0f,   +1.0f, -1.0f,   -1.0f, +1.0f,
            -1.0f, +1.0f,   +1.0f, -1.0f,   +1.0f, +1.0f,
            // UVs 0..5: V-flipped so NDC top (+y) samples the DS top row (v=0).
            0.0f, 1.0f,   1.0f, 1.0f,   0.0f, 0.0f,
            0.0f, 0.0f,   1.0f, 1.0f,   1.0f, 0.0f,
            // UVs 6..11 (degenerate).
            1.0f, 1.0f,   1.0f, 1.0f,   1.0f, 1.0f,
            1.0f, 1.0f,   1.0f, 1.0f,   1.0f, 1.0f,
            // UVs 12..17 (degenerate).
            1.0f, 1.0f,   1.0f, 1.0f,   1.0f, 1.0f,
            1.0f, 1.0f,   1.0f, 1.0f,   1.0f, 1.0f,
            // UVs 18..23: full texture, standard orientation.
            0.0f, 0.0f,   1.0f, 0.0f,   0.0f, 1.0f,
            0.0f, 1.0f,   1.0f, 0.0f,   1.0f, 1.0f,
        };
        glBindBuffer(GL_ARRAY_BUFFER, mSlotVbo);
        glBufferData(GL_ARRAY_BUFFER, sizeof(slotVerts), slotVerts,
                     GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    // Call fxSetup LAST so drastic's GL state (program, vertex
    // attribs, texture bindings) is the active state when
    // renderFrame runs. renderFrame does NOT call glUseProgram or
    // set vertex attribs -- it relies entirely on fxSetup's state.
    if (mFxSetup && mUseRenderFrame) {
        // fxLoad loads drastic's shader files (.dfx) from the system
        // shaders dir. Must be called BEFORE fxSetup which compiles
        // them. The real app calls fxLoad(shaderPath, 0, 0x8A0).
        // Our FakeJNI translates "DraStic/shaders/Linear.dfx" to
        // {cacheRoot}/system/shaders/Linear.dfx.
        if (mFxLoad) {
            // fxLoad takes an absolute filesystem path to the .dfx
            // shader file. Name is taken from mInitialShader which
            // drastic-nano populates from the user's _CurrentFx pref
            // (gammaos-nano passes empty -> defaults to "Linear").
            // Try the drastic-app layout first; if the file is
            // missing, fall back to the nano_cache layout, and
            // finally fall back to Linear.dfx if the requested
            // shader isn't installed at all.
            auto tryPath = [&](const std::string& name) -> std::string {
                std::string a = mCacheDir + "/shaders/" + name + ".dfx";
                if (access(a.c_str(), R_OK) == 0) return a;
                std::string b = mCacheDir + "/system/shaders/" + name + ".dfx";
                if (access(b.c_str(), R_OK) == 0) return b;
                return std::string();
            };
            std::string sp = tryPath(mInitialShader);
            if (sp.empty()) {
                ALOGW("DrasticRunner::initSurface: shader '%s' not "
                      "found, falling back to Linear",
                      mInitialShader.c_str());
                sp = tryPath("Linear");
            }
            if (sp.empty()) {
                ALOGE("DrasticRunner::initSurface: no .dfx shader "
                      "found under %s/shaders or %s/system/shaders",
                      mCacheDir.c_str(), mCacheDir.c_str());
            } else {
                jstring shaderJStr =
                        ((JNIEnv*)mFakeEnv)->NewStringUTF(sp.c_str());
                // Pass pos_ptr=0, uv_ptr=192 as byte offsets into
                // mFxVbo (bound before each fxRender call). Stock
                // drastic passes (0, 0x8A0) which are offsets into
                // its own pre-built VBO; since we build our own VBO
                // we pick our own layout. Positions are 24 vec2 at
                // offset 0 (= 192 bytes), UVs are 24 vec2 at offset
                // 192 (= 192 bytes). Total VBO = 384 bytes.
                int rc = mFxLoad(mFakeEnv, mFakeCls,
                                 (void*)shaderJStr, 0, 192);
                ALOGI("DrasticRunner::initSurface: fxLoad(\"%s\") = %d",
                      sp.c_str(), rc);
            }
        }
        // fxSetup args from smali (DraSticGlView$j onSurfaceChanged):
        //   arg1/2 = DS texture resolution (256x192 or 512x384 with hires)
        //   arg3/4 = 0, 0
        //   arg5/6 = surface/viewport width, height
        // texW/texH must match the DS textures (mDsTexW/mDsTexH, sized
        // from the live _Hires3D bit) so drastic's pass-resolution uniforms
        // agree with the upload. redimDsTextures() re-runs fxSetup with the
        // new dims when Hi-res 3D is toggled in-game.
        int texW = mDsTexW, texH = mDsTexH;
        mFxTexW = texW;
        mFxTexH = texH;
        ALOGI("DrasticRunner::initSurface: fxSetup(%d, %d, 0, 0, %d, %d) "
              "[called LAST, after all our GL setup]",
              texW, texH, mOffscreenW, mOffscreenH);
        mFxSetup(mFakeEnv, mFakeCls,
                 texW, texH, 0, 0,
                 mOffscreenW, mOffscreenH);
        // fxRender does NOT respect an externally-bound FBO — it
        // binds each pass's own pass.fbo on entry. For stock .dfx
        // files the final pass.fbo is 0 (= EGL surface), so shader
        // output bypasses mOffscreenFbo. Redirect the final pass to
        // our FBO so renderTop/Bottom/Both can sample halves from
        // mOffscreenTex. See DrasticRunner::patchFinalPassFbo.
        //
        // Only meaningful when the fxRender shader path is active
        // (fxLoad was called). In QR preview mode we null mFxLoad /
        // mFxRender in init(), so there's no pass list to walk --
        // skip the call to avoid a misleading "empty pass list"
        // warning on every QR launch.
        if (mFxLoad) {
            patchFinalPassFbo();
        }
        // mDrasticGlProgram is no longer used on the fxRender path -
        // fxRender itself binds the right pass program internally
        // (confirmed by drastic-android-mod disasm 2026-04-17). For
        // the legacy renderFrame fallback we also leave it at 0; that
        // path relies on whatever default state fxSetup leaves behind.
        mDrasticGlProgram = 0;
    } else if (mFxSetup) {
        // Legacy path: fxSetup for getScreenBuffers. Order doesn't
        // matter since getScreenBuffers doesn't use GL state.
        mFxSetup(mFakeEnv, mFakeCls,
                 kDsScreenW, kDsScreenH, 0, 0,
                 viewportW, viewportH);
    }

    mViewportW = viewportW;
    mViewportH = viewportH;
    mSurfaceReady = true;
    ALOGI("DrasticRunner::initSurface: ready (viewport %dx%d, "
          "offscreen %dx%d, renderFrame=%d, dual=%d)",
          viewportW, viewportH, mOffscreenW, mOffscreenH,
          mUseRenderFrame ? 1 : 0, dualDisplay ? 1 : 0);
}

void DrasticRunner::patchFinalPassFbo() {
    // Forward to the parameterized walk with our shared offscreen FBO, then
    // invalidate the slot-shade cache: this no-arg form is called by init,
    // hi-res redim and the runtime shader swap, all of which rebuild the pass
    // list at mOffscreenW/H and re-point the final pass at mOffscreenFbo, so a
    // prior slot-sized setup must not be reused by renderSlotShaded.
    patchFinalPassFbo(mOffscreenFbo);
    mSlotShadeW = -1;
    mSlotShadeH = -1;
    mSlotShadeFbo = 0;
}

void DrasticRunner::patchFinalPassFbo(unsigned int targetFbo) {
    if (!mArm64Base) {
        ALOGW("DrasticRunner::patchFinalPassFbo: no base address, skip");
        return;
    }
    if (targetFbo == 0) {
        ALOGW("DrasticRunner::patchFinalPassFbo: no target FBO, skip");
        return;
    }

    // fx_ctx struct layout (verified via local objdump of
    // libdrastic_arm64.so at 0x1d1f8..0x1d2f4):
    //   0x1d208:  adrp x24, 3f2d000        ; page
    //   0x1d210:  add  x24, x24, #0x1f8    ; x24 = 0x3f2d1f8
    //   0x1d2e8:  add  x0,  x24, #0x10     ; x0  = 0x3f2d208  <-- fx_ctx
    //   0x1d2f4:  bl   20690               ; pass processor(x0=fx_ctx)
    // and at the pass-loop (0x20690):
    //   0x206e0:  ldp  x26, x28, [x21]     ; x26=head, x28=tail
    //   0x20888:  ldr  x26, [x26, #368]    ; next pointer at +368
    //   0x20898:  ldr  w1,  [x26, #344]    ; fbo at +344
    //
    // fx_ctx sits inside BSS (VirtAddr 0x14c000, MemSize 65 MB). The
    // offset 0x3f2d208 is the ELF virtual address; at runtime it maps
    // to mArm64Base + 0x3f2d208 because the library's first LOAD segment
    // is at VirtAddr 0 (confirmed via readelf -l).
    //
    // pass struct:
    //   offset +0:   uint32_t program   (GL shader program id)
    //   offset +344: uint32_t fbo       (target FBO for this pass)
    //   offset +368: fx_pass* next      (NULL at tail)
    static constexpr uintptr_t kFxCtxOff     = 0x3f2d208;
    static constexpr uintptr_t kPassFboOff   = 344;
    static constexpr uintptr_t kPassNextOff  = 368;

    struct FxCtxHead {
        void* head;
        void* tail;
    };
    FxCtxHead* ctx = reinterpret_cast<FxCtxHead*>(mArm64Base + kFxCtxOff);

    uint8_t* p = reinterpret_cast<uint8_t*>(ctx->head);
    uint8_t* last = nullptr;
    int count = 0;
    // Cap the walk so a corrupted next pointer cannot loop forever.
    // Stock .dfx files have 1..2 passes; 64 is many orders of magnitude
    // above anything real.
    //
    // Walk 1: normalize sampler unit_enums on every pass. fxRender
    // iterates ALL passes and calls glActiveTexture(sampler[i].unit_enum)
    // on each, so a bad unit_enum in ANY pass generates one GL_INVALID_ENUM
    // per frame. Prior versions only normalized the final pass, which left
    // multi-pass shaders (e.g. _CurrentFx=2xPrescaleFast_LCD) logging the
    // mali error every frame and hitting the GLES error slow path.
    //
    // Normalize ALL 8 potential sampler slots (not just i < samplerCnt):
    // on stock shaders we observed samplerCnt=1 with sampler[0] valid but
    // mali still spams GL_INVALID_ENUM once per frame, implying fxRender
    // iterates the sampler array with a hardcoded upper bound (likely 2
    // or 8) and ignores samplerCnt for the unit_enum pre-setup step. By
    // writing a valid unit_enum to every slot up to 7 we cover that
    // over-iteration without touching other pass fields.
    uint32_t totalBadNormalized = 0;
    while (p && count < 64) {
        last = p;
        for (uint32_t i = 0; i < 8; ++i) {
            uint32_t* unitP =
                    reinterpret_cast<uint32_t*>(p + 56 + 40 * i);
            if (*unitP < 0x84C0 || *unitP > 0x84C7) {
                if (totalBadNormalized < 16) {
                    ALOGW("DrasticRunner::patchFinalPassFbo: pass[%d] "
                          "sampler[%u].unit_enum = 0x%x (invalid) -> "
                          "forcing to GL_TEXTURE%u (0x%x)",
                          count, i, *unitP, i, 0x84C0 + i);
                }
                *unitP = 0x84C0 + i;
                totalBadNormalized++;
            }
        }
        p = *reinterpret_cast<uint8_t**>(p + kPassNextOff);
        count++;
    }

    if (!last) {
        ALOGW("DrasticRunner::patchFinalPassFbo: empty pass list "
              "(ctx=%p head=%p)", ctx, ctx->head);
        return;
    }

    uint32_t oldFbo = *reinterpret_cast<uint32_t*>(last + kPassFboOff);
    *reinterpret_cast<uint32_t*>(last + kPassFboOff) = targetFbo;

    uint32_t program    = *reinterpret_cast<uint32_t*>(last + 0);
    uint32_t posAttrib  = *reinterpret_cast<uint32_t*>(last + 4);
    uint32_t uvAttrib   = *reinterpret_cast<uint32_t*>(last + 8);
    uint32_t resUnif    = *reinterpret_cast<uint32_t*>(last + 16);
    uint32_t sclUnif    = *reinterpret_cast<uint32_t*>(last + 20);
    uint32_t samp0Unit  = *reinterpret_cast<uint32_t*>(last + 56);
    uint32_t samp0Idx   = *reinterpret_cast<uint32_t*>(last + 60);
    uint32_t samp1Unit  = *reinterpret_cast<uint32_t*>(last + 96);
    uint32_t samp1Idx   = *reinterpret_cast<uint32_t*>(last + 100);
    uint32_t outW       = *reinterpret_cast<uint32_t*>(last + 348);
    uint32_t outH       = *reinterpret_cast<uint32_t*>(last + 352);
    uint32_t samplerCnt = *reinterpret_cast<uint32_t*>(last + 364);

    ALOGI("DrasticRunner::patchFinalPassFbo: walked %d pass(es), "
          "final pass.fbo %u -> %u (targetFbo)",
          count, oldFbo, targetFbo);
    ALOGI("DrasticRunner::patchFinalPassFbo: pass fields "
          "program=%u posAttrib=%u uvAttrib=%u resUnif=%u sclUnif=%u "
          "outW=%u outH=%u samplerCount=%u",
          program, posAttrib, uvAttrib, resUnif, sclUnif,
          outW, outH, samplerCnt);
    ALOGI("DrasticRunner::patchFinalPassFbo: final sampler[0] unit=0x%x "
          "idx=%u  sampler[1] unit=0x%x idx=%u "
          "(total bad unit_enums normalized across all passes: %u)",
          samp0Unit, samp0Idx, samp1Unit, samp1Idx, totalBadNormalized);
}

void DrasticRunner::dumpFxCtxState(const char* when) {
    if (!mArm64Base) {
        ALOGI("DrasticRunner::dumpFxCtxState[%s]: no base, skip", when);
        return;
    }
    static constexpr uintptr_t kFxCtxOff    = 0x3f2d208;
    static constexpr uintptr_t kPassFboOff  = 344;
    static constexpr uintptr_t kPassNextOff = 368;
    static constexpr uintptr_t kPassSampCnt = 364;

    uint8_t* base = reinterpret_cast<uint8_t*>(mArm64Base + kFxCtxOff);
    void* head = *reinterpret_cast<void**>(base + 0);
    void* tail = *reinterpret_cast<void**>(base + 8);
    uint32_t count480 = *reinterpret_cast<uint32_t*>(base + 0x480);
    void* f438 = *reinterpret_cast<void**>(base + 0x438);
    void* f448 = *reinterpret_cast<void**>(base + 0x448);
    void* f458 = *reinterpret_cast<void**>(base + 0x458);
    ALOGI("DrasticRunner::dumpFxCtxState[%s]: ctx=%p head=%p tail=%p "
          "count480=%u f438=%p f448=%p f458=%p",
          when, (void*)base, head, tail, count480, f438, f448, f458);

    uint8_t* p = reinterpret_cast<uint8_t*>(head);
    int i = 0;
    while (p && i < 32) {
        uint32_t program    = *reinterpret_cast<uint32_t*>(p + 0);
        uint32_t fbo        = *reinterpret_cast<uint32_t*>(p + kPassFboOff);
        uint32_t samplerCnt = *reinterpret_cast<uint32_t*>(p + kPassSampCnt);
        void*    next       = *reinterpret_cast<void**>(p + kPassNextOff);
        ALOGI("  pass[%d] @%p program=%u fbo=%u samplerCnt=%u next=%p",
              i, (void*)p, program, fbo, samplerCnt, next);
        p = reinterpret_cast<uint8_t*>(next);
        ++i;
    }
    if (p) {
        ALOGW("  pass list walk capped at 32 -- possible cycle");
    }
}

void DrasticRunner::unpatchFinalPassFbo() {
    if (!mArm64Base) return;

    static constexpr uintptr_t kFxCtxOff     = 0x3f2d208;
    static constexpr uintptr_t kPassFboOff   = 344;
    static constexpr uintptr_t kPassNextOff  = 368;

    struct FxCtxHead {
        void* head;
        void* tail;
    };
    FxCtxHead* ctx = reinterpret_cast<FxCtxHead*>(mArm64Base + kFxCtxOff);

    uint8_t* p = reinterpret_cast<uint8_t*>(ctx->head);
    uint8_t* last = nullptr;
    int count = 0;
    while (p && count < 64) {
        last = p;
        p = *reinterpret_cast<uint8_t**>(p + kPassNextOff);
        count++;
    }
    if (!last) return;

    uint32_t* fboP = reinterpret_cast<uint32_t*>(last + kPassFboOff);
    uint32_t old = *fboP;
    *fboP = 0;
    ALOGI("DrasticRunner::unpatchFinalPassFbo: final pass.fbo %u -> 0 "
          "(walked %d pass(es))",
          old, count);
}

void DrasticRunner::renderDsToOffscreen() {
    if (!mSurfaceReady || !mUseRenderFrame) return;

    // Consume a pending Hi-res 3D re-dim on the render thread, before
    // drastic's next upload, so the just-resized textures and the upload
    // size agree. No-op when the size already matches.
    if (mPendingDsReDim.exchange(false)) {
        redimDsTextures();
    }

    // Need at least one of the two per-frame render entry points.
    // fxRender is preferred (actually invokes the loaded shader);
    // renderFrame is a fallback for builds that don't export fxRender.
    if (!mFxRender && !mRenderFrame) return;

    // Stop the pixel-pull thread on the first renderFrame call.
    // We take over frame consumption via waitScreen + renderFrame.
    // The pixel-pull must be stopped first because both it and us
    // call waitScreen, and the condvar only wakes one waiter per
    // producer signal.
    static bool sPixelPullStopped = false;
    if (!sPixelPullStopped && mPixelPullRunning.load()) {
        ALOGI("DrasticRunner: stopping pixel-pull for renderFrame "
              "takeover");
        mPixelPullRunning.store(false);
        usleep(50000); // 50ms grace for the pull thread to exit
        // Kick the producer with signalScreen in case it's blocked
        // waiting for the getScreenBuffers consumer ack. The pixel-
        // pull may have exited mid-loop without calling signalScreen,
        // leaving the producer stuck. This one-time kick resumes
        // the producer so subsequent waitScreen calls get signaled.
        if (mSignalScreen) {
            mSignalScreen(mFakeEnv, mFakeCls);
            ALOGI("DrasticRunner: signalScreen kick after pixel-pull stop");
        }
        sPixelPullStopped = true;
    }

    // GammaOS (2026-04-13): Removed the mWaitScreen() call here.
    //
    // Previously we called waitScreen before renderFrame on the
    // assumption (backed by an earlier comment) that without it
    // renderFrame would read a mid-composited slot and produce
    // missing 3D layers or corrupted sprites. drastic-android-mod's
    // later trace of renderFrame (0x1ceac) clarified that renderFrame
    // locks its own framebuffer-slot mutex (BSS+0x98c) which the
    // producer also takes when flipping the double-buffer slot --
    // i.e. renderFrame cannot observe a partial slot on its own.
    // waitScreen only gates "there is a NEWER frame than last time",
    // not correctness.
    //
    // Keeping waitScreen here was coupling the render thread's
    // framerate to drastic's producer rate. When drastic's rasterizer
    // occasionally overran 16.67 ms per frame (common with _Hires3D
    // + complex scenes), the render thread blocked in waitScreen,
    // missed its own vblank, and fell into a 30 fps lock for the
    // duration of drastic's stall. Profiling showed the QR preview
    // oscillating 30-60 fps with avg 45 fps.
    //
    // By skipping waitScreen we let renderFrame simply upload
    // whatever the current complete slot is each vblank. When
    // drastic is keeping up: every upload is a new frame (60 fps).
    // When drastic is momentarily slow: we upload the same slot
    // twice (visually a duplicate frame, invisible to the user at
    // 60 Hz) but the render thread still hits the next vblank, so
    // the display stays at 60 fps.

    // Fast-forward frame blending: BEFORE fxRender overwrites
    // mOffscreenTex with this frame, snapshot the frame it still holds
    // (the previous displayed frame) into mFfPrevTex. drawDsQuad then
    // cross-fades the two, turning the FF frameskip strobe into fluid
    // motion-blur. FF-only and lazily allocated, so 1x is untouched.
    mFfBlendThisFrame = false;
    if (mFastForwardOn && mOffscreenTex != 0 && mOffscreenFbo != 0 &&
            mFfBlendProgram != 0 &&
            property_get_int32("persist.gammaos.drastic_nano.ff_blend", 1)) {
        if (mFfPrevTex == 0) {
            glGenTextures(1, &mFfPrevTex);
            glBindTexture(GL_TEXTURE_2D, mFfPrevTex);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, mOffscreenW,
                         mOffscreenH, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        }
        // Copy the current (= previous displayed) offscreen contents
        // into mFfPrevTex via glCopyTexSubImage2D (read source is the
        // mOffscreenFbo color attachment, which is mOffscreenTex).
        glBindFramebuffer(GL_FRAMEBUFFER, mOffscreenFbo);
        glBindTexture(GL_TEXTURE_2D, mFfPrevTex);
        glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0,
                            mOffscreenW, mOffscreenH);
        // Only blend once a genuine prior frame has been captured (skips
        // the very first FF frame so no stale content flashes).
        mFfBlendThisFrame = mFfPrevValid;
        mFfPrevValid = true;
        int a = property_get_int32(
                "persist.gammaos.drastic_nano.ff_blend_alpha", 50);
        if (a < 0)   a = 0;
        if (a > 100) a = 100;
        mFfBlendAlpha = (float)a / 100.0f;
    }

    // renderFrame uploads the complete framebuffer into our textures.
    // Bind the offscreen FBO first so drastic's internal glDrawArrays
    // (which it issues alongside the texSubImage uploads -- see
    // renderFrame disasm at libdrastic+0x1ceac) lands in a scratch
    // buffer we do not sample. Without this bind the drastic draw
    // writes into whichever FBO was last active (often the primary
    // AHB from the previous vblank), so the GPU does a full-screen
    // fragment shader pass that our own renderer then clears over
    // before drawing -- adds 5-7 ms to glFinish during sustained
    // frames.
    if (mOffscreenFbo != 0) {
        glBindFramebuffer(GL_FRAMEBUFFER, mOffscreenFbo);
        // DEBUG canary: clear to red on entry. With patchFinalPassFbo
        // in place, fxRender's final pass should overwrite this with
        // the shaded DS frame, so the displays show the game. If any
        // red is visible, the pass.fbo patch did not take (walk found
        // no pass list, or the struct offsets shifted) and fxRender
        // wrote to FBO 0 (the EGL surface) instead.
        glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
    }
    if (mFxRender) {
        // fxRender is the shader-enabled render path — renderFrame
        // never issues glUseProgram, so the loaded .dfx shader is
        // invisible on the renderFrame path. Stock drastic
        // (DraSticGlView$j cond_11) calls fxRender ONCE per frame
        // passing BOTH screen textures and a single output rect; the
        // shader pipeline internally lays them out stacked. Args
        // 3/4/5 are fixed constants drastic uses for source-rect
        // metadata (NOT runtime geometry). We reproduce that exact
        // call shape here so the .dfx pass list composites top + bot
        // into the offscreen FBO in one pass.
        glViewport(0, 0, mOffscreenW, mOffscreenH);
        // Bind our VBO so the pass runner's glVertexAttribPointer
        // reads from buffer-object memory (byte offsets 0 / 96) rather
        // than treating our pos_ptr/uv_ptr as host addresses. Without
        // this, glDrawArrays fetches vertex data from host memory at
        // virtual address 0 (= segfault protection returns garbage
        // zeros) and host addr 96 (= wherever that maps today),
        // producing random geometry that still happens to run through
        // drastic's shader pipeline. The shader then samples the DS
        // textures at garbage UVs, producing the "game colors with LCD
        // grid overlay at random triangle positions" symptom.
        glBindBuffer(GL_ARRAY_BUFFER, mFxVbo);
        // Drain any prior errors first so the post-call check is clean.
        while (glGetError() != GL_NO_ERROR) {}
        mFxRender(mFakeEnv, mFakeCls,
                  (int)mDsTopTex, (int)mDsBotTex,
                  0, 6, 18,
                  0, 0, mOffscreenW, mOffscreenH,
                  0);
        GLenum err = glGetError();
        static bool sLoggedOnce = false;
        if (!sLoggedOnce) {
            sLoggedOnce = true;
            ALOGW("DrasticRunner: post-fxRender glError=0x%x "
                  "(offscreenFbo=%u, tex=%u/%u, rect=%dx%d)",
                  err, mOffscreenFbo, mDsTopTex, mDsBotTex,
                  mOffscreenW, mOffscreenH);
        }
    } else {
        // Fallback: renderFrame (no shader). Left here in case the
        // fxRender symbol goes missing in a future libdrastic build.
        mRenderFrame(mFakeEnv, mFakeCls, (int)mDsTopTex,
                     (int)mDsBotTex, 0);
    }

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
}

bool DrasticRunner::renderSlotShaded(int which, unsigned int targetFbo,
                                     int vx, int vy, int vw, int vh) {
    // Shader-enabled per-slot render. Falls back (returns false) when the
    // .dfx path is inactive (QR preview / missing fxRender) so the caller can
    // use the re-sampled renderTop/BottomScreen blit instead. Only the single-
    // panel DRM layout calls this; the dual-panel (RG DS) and offscreen paths
    // never do, so renderDsToOffscreen and its callers are untouched.
    if (!mSurfaceReady || !mFxRender || targetFbo == 0) return false;
    if (vw <= 0 || vh <= 0) return false;

    // Consume a pending Hi-res 3D re-dim before the upload, like
    // renderDsToOffscreen, so the textures and the upload size agree.
    if (mPendingDsReDim.exchange(false)) {
        redimDsTextures();
    }

    // Take over frame consumption from the background pixel-pull thread. This
    // mirrors renderDsToOffscreen's one-time stop but with its own latch so we
    // never have to touch that function (which the RG DS path relies on).
    static bool sSlotPixelPullStopped = false;
    if (!sSlotPixelPullStopped && mPixelPullRunning.load()) {
        ALOGI("DrasticRunner: stopping pixel-pull for renderSlotShaded takeover");
        mPixelPullRunning.store(false);
        usleep(50000);
        if (mSignalScreen) {
            mSignalScreen(mFakeEnv, mFakeCls);
            ALOGI("DrasticRunner: signalScreen kick after pixel-pull stop");
        }
        sSlotPixelPullStopped = true;
    }

    // (Re)size AND place the shader pass list for this slot. fxRender ignores
    // its own viewport args -- the final pass uses the viewport fxSetup stored
    // -- so the slot ORIGIN (vx,vy) as well as the size goes through fxSetup.
    // That also makes the prescale/LCD grid uniforms match the real on-screen
    // pixel size. fxSetup clears the final-pass FBO redirect, so re-patch
    // after. Cached on the full rect: a stable layout re-sizes once per
    // distinct slot rect; an asymmetric big+small layout re-runs fxSetup per
    // screen each frame (correct -- each prescale must match its own slot).
    if (mSlotShadeX != vx || mSlotShadeY != vy ||
        mSlotShadeW != vw || mSlotShadeH != vh) {
        mFxSetup(mFakeEnv, mFakeCls, mFxTexW, mFxTexH, vx, vy, vw, vh);
        mSlotShadeX = vx;
        mSlotShadeY = vy;
        mSlotShadeW = vw;
        mSlotShadeH = vh;
        mSlotShadeFbo = 0;  // fxSetup reset the patch; force a re-patch below
    }
    if (mSlotShadeFbo != targetFbo) {
        patchFinalPassFbo(targetFbo);
        mSlotShadeFbo = targetFbo;
    }

    glBindFramebuffer(GL_FRAMEBUFFER, targetFbo);
    glBindBuffer(GL_ARRAY_BUFFER, mSlotVbo);
    while (glGetError() != GL_NO_ERROR) {}
    // Draw exactly one screen filling the slot. The chosen screen uses the
    // full-NDC V-flipped quad (mSlotVbo verts 0..5); the other uses the
    // degenerate zero-area quad (verts 6..11) so it contributes nothing.
    // fxRender uploads the top frame into mDsTopTex and the bottom frame into
    // mDsBotTex, then its final pass draws the top quad sampling mDsTopTex and
    // the bottom quad sampling mDsBotTex -- so firstTop=0/firstBot=6 yields the
    // top screen alone, and firstTop=6/firstBot=0 yields the bottom alone.
    if (which == 0) {
        mFxRender(mFakeEnv, mFakeCls, (int)mDsTopTex, (int)mDsBotTex,
                  0, 6, 18, vx, vy, vw, vh, 0);
    } else {
        mFxRender(mFakeEnv, mFakeCls, (int)mDsTopTex, (int)mDsBotTex,
                  6, 0, 18, vx, vy, vw, vh, 0);
    }
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    return true;
}

void DrasticRunner::updatePixels() {
    if (!mSurfaceReady) return;
    if (mUseRenderFrame) return; // renderDsToOffscreen replaces this
    if (!mShadowReady.load(std::memory_order_acquire)) return;
    {
        std::lock_guard<std::mutex> lock(mShadowMutex);
        glBindTexture(GL_TEXTURE_2D, mTopTex);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, kDsScreenW, kDsScreenH,
                        GL_RGBA, GL_UNSIGNED_BYTE, mTopShadow.data());
        glBindTexture(GL_TEXTURE_2D, mBotTex);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, kDsScreenW, kDsScreenH,
                        GL_RGBA, GL_UNSIGNED_BYTE, mBotShadow.data());
    }
}

void DrasticRunner::drawDsQuad(unsigned int tex, float vMin, float vMax,
                                float saturation, float gradient) {
    if (!mSurfaceReady) return;
    // vMin/vMax are the UV Y range to sample from the source texture.
    // Full texture = (0, 1). For the fxRender path we pass the
    // shaded offscreen FBO (top + bot stacked) so renderTopScreen
    // samples (0, 0.5) and renderBottomScreen samples (0.5, 1).

    // Fast-forward path: cross-fade the current frame with the snapshot
    // of the previous one (mFfPrevTex, a full copy of mOffscreenTex so
    // the same vMin/vMax sub-rect applies to both). Only when FF is held
    // and the source is the composited offscreen texture; everything
    // else falls through to the byte-identical 1x path below.
    if (mFfBlendThisFrame && tex == mOffscreenTex && mFfBlendProgram != 0) {
        glUseProgram(mFfBlendProgram);
        if (mFfBlendRotLoc >= 0)
            glUniformMatrix2fv(mFfBlendRotLoc, 1, GL_FALSE, mRotationMatrix);
        if (mFfBlendSatLoc >= 0)  glUniform1f(mFfBlendSatLoc, saturation);
        if (mFfBlendGradLoc >= 0) glUniform1f(mFfBlendGradLoc, gradient);
        if (mFfBlendAmountLoc >= 0) glUniform1f(mFfBlendAmountLoc, mFfBlendAlpha);
        if (mFfBlendSamplerLoc >= 0)     glUniform1i(mFfBlendSamplerLoc, 0);
        if (mFfBlendPrevSamplerLoc >= 0) glUniform1i(mFfBlendPrevSamplerLoc, 1);
        if (mFfBlendUvRectLoc >= 0)
            glUniform4f(mFfBlendUvRectLoc, 0.0f, vMin, 1.0f, vMax - vMin);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, mFfPrevTex);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, tex);

        glDisable(GL_BLEND);
        glDisable(GL_DEPTH_TEST);

        glBindBuffer(GL_ARRAY_BUFFER, mQuadVbo);
        glVertexAttribPointer(mFfBlendPosLoc, 2, GL_FLOAT, GL_FALSE,
                              4 * sizeof(float), (void*)0);
        glEnableVertexAttribArray(mFfBlendPosLoc);
        glVertexAttribPointer(mFfBlendTexLoc, 2, GL_FLOAT, GL_FALSE,
                              4 * sizeof(float), (void*)(2 * sizeof(float)));
        glEnableVertexAttribArray(mFfBlendTexLoc);

        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        return;
    }

    glUseProgram(mQuadProgram);
    if (mQuadRotLoc >= 0)
        glUniformMatrix2fv(mQuadRotLoc, 1, GL_FALSE, mRotationMatrix);
    if (mQuadSatLoc >= 0) glUniform1f(mQuadSatLoc, saturation);
    if (mQuadGradLoc >= 0) glUniform1f(mQuadGradLoc, gradient);
    if (mQuadSamplerLoc >= 0) glUniform1i(mQuadSamplerLoc, 0);
    if (mQuadUvRectLoc >= 0) {
        glUniform4f(mQuadUvRectLoc,
                    0.0f, vMin,         // uv origin
                    1.0f, vMax - vMin); // uv scale
    }

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, tex);

    glDisable(GL_BLEND);
    glDisable(GL_DEPTH_TEST);

    glBindBuffer(GL_ARRAY_BUFFER, mQuadVbo);
    glVertexAttribPointer(mQuadPosLoc, 2, GL_FLOAT, GL_FALSE,
                          4 * sizeof(float), (void*)0);
    glEnableVertexAttribArray(mQuadPosLoc);
    glVertexAttribPointer(mQuadTexLoc, 2, GL_FLOAT, GL_FALSE,
                          4 * sizeof(float), (void*)(2 * sizeof(float)));
    glEnableVertexAttribArray(mQuadTexLoc);

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    // End-of-call cleanup removed -- the next drawDsQuad call (or any
    // GL state update in the render loop) will re-configure vertex
    // attribs and buffer bindings. Skipping these saves ~3 GL state
    // changes per call and avoids driver shadow-state updates.
}

void DrasticRunner::renderTopScreen(float saturation, float gradient) {
    if (mUseRenderFrame) {
        if (mFxRender && mOffscreenTex != 0) {
            // fxRender wrote the shader-composited top-on-top,
            // bottom-on-bottom layout into mOffscreenTex. Top half
            // is the DS top screen.
            drawDsQuad(mOffscreenTex, 0.0f, 0.5f, saturation, gradient);
        } else {
            // renderFrame fallback (no shader): drastic's own draw
            // into the offscreen FBO doesn't produce visible output,
            // but its glTexSubImage2D uploads do land in mDsTopTex,
            // so we can blit that directly.
            drawDsQuad(mDsTopTex, 0.0f, 1.0f, saturation, gradient);
        }
    } else {
        drawDsQuad(mTopTex, 0.0f, 1.0f, saturation, gradient);
    }
}

void DrasticRunner::renderBottomScreen(float saturation, float gradient) {
    if (mUseRenderFrame) {
        if (mFxRender && mOffscreenTex != 0) {
            drawDsQuad(mOffscreenTex, 0.5f, 1.0f, saturation, gradient);
        } else {
            drawDsQuad(mDsBotTex, 0.0f, 1.0f, saturation, gradient);
        }
    } else {
        drawDsQuad(mBotTex, 0.0f, 1.0f, saturation, gradient);
    }
}

void DrasticRunner::renderBothScreens(float saturation, float gradient) {
    if (mUseRenderFrame) {
        if (mFxRender && mOffscreenTex != 0) {
            // Single display: draw the whole composited FBO (top
            // on the upper half + bottom on the lower half).
            drawDsQuad(mOffscreenTex, 0.0f, 1.0f, saturation, gradient);
        } else {
            // renderFrame fallback: no composite -- only top is visible.
            drawDsQuad(mDsTopTex, 0.0f, 1.0f, saturation, gradient);
        }
    } else {
        drawDsQuad(mTopTex, 0.0f, 1.0f, saturation, gradient);
    }
}

void DrasticRunner::setInput(int bitmask) {
    setInputWithTouch(bitmask, 0, 0, false);
}

void DrasticRunner::setInputWithTouch(int bitmask, int touchX, int touchY,
                                      bool touchHeld) {
    if (!mInitialized || !mUpdateInput) return;
    // updateInput(JNIEnv*, jclass, int bitmask, int touchPacked, int held)
    //
    // Native side at 0x1a5d8 stores:
    //   master+0x48c = bitmask & 0x7fffffff     (button bits 0..11 used)
    //   master+0x490 = held
    //   master+0x494 = asr(touchPacked, 16)     (touch Y, signed top half)
    //   master+0x498 = touchPacked & 0xffff     (touch X, low half)
    //   master+0x4bf = (bitmask >> 31) & 1      (pointer-down flag)
    //
    // The bit layout confirmed via drastic-android-mod disasm analysis:
    // bits 0..11 are the 12 DS buttons (see kDsBtn* constants), bits
    // 12..30 are reserved / trap-door indices we must not touch, and
    // bit 31 is the touchscreen pointer-down indicator. Clamp the
    // caller's mask to 0..11 so we can never accidentally trip a
    // trap-door index even if the caller passed a stray bit.
    int fullBitmask = bitmask & 0x00000fff;
    if (touchHeld) fullBitmask |= 0x80000000;
    // Drastic packs touch as (x << 16) | y, empirically verified by
    // corner-tap test (2026-04-17). The disasm at 0x1a600 labels the
    // ASR'd high half "y" and the low half "x", but drastic's TSC2046
    // emulation reads master+0x494 as the X channel and master+0x498
    // as the Y channel -- the opposite of the label. Sending the DS
    // horizontal coord in the HIGH half and the DS vertical coord in
    // the LOW half makes the in-game cursor land where the finger
    // actually is.
    //
    // Clamp to DS bottom-screen space so the touchscreen MMIO handler
    // sees coordinates in [0..255, 0..191] as the real hardware would.
    // The low half (Y) is hardware-clamped at 191 inside drastic too,
    // so sending a value > 191 there just produces Y=191 rather than
    // bleeding into unrelated fields.
    if (touchX < 0) touchX = 0;
    if (touchX > 255) touchX = 255;
    if (touchY < 0) touchY = 0;
    if (touchY > 191) touchY = 191;
    const int touchPacked =
            ((touchX & 0xffff) << 16) | (touchY & 0xffff);
    const int held = touchHeld ? 1 : 0;
    mUpdateInput(mFakeEnv, mFakeCls, fullBitmask, touchPacked, held);
}

void DrasticRunner::pauseDrastic() {
    if (!mInitialized || !mPauseSystem) return;
    // Reuse the cached fake env/cls — the render thread calls this
    // live, so unlike shutdown() we have a valid env on hand. The
    // disasm of pauseSystem (see findings.md) does not dereference
    // env, so either would work; prefer the cached pair for symmetry
    // with setInput.
    mPauseSystem(mFakeEnv, mFakeCls, 1);
    mPaused = true;
}

void DrasticRunner::pauseToggle(bool pause) {
    if (!mInitialized || !mPauseSystem) return;
    if (pause == mPaused) return;
    mPauseSystem(mFakeEnv, mFakeCls, pause ? 1 : 0);
    mPaused = pause;
    ALOGI("DrasticRunner::pauseToggle: paused=%d", pause ? 1 : 0);
}

bool DrasticRunner::saveStateSlot(int slot) {
    if (!mInitialized || !mSaveState) {
        ALOGW("DrasticRunner::saveStateSlot: not available "
              "(initialized=%d, mSaveState=%p)",
              mInitialized ? 1 : 0, (void*)mSaveState);
        return false;
    }
    if (slot < 0 || slot > 8) {
        ALOGW("DrasticRunner::saveStateSlot: refusing slot %d (valid 0..8)",
              slot);
        return false;
    }
    int rc = mSaveState(mFakeEnv, mFakeCls, slot);
    ALOGI("DrasticRunner::saveStateSlot(%d) = %d", slot, rc);
    return true;
}
bool DrasticRunner::saveAutosave() {
    if (!mInitialized || !mSaveState) {
        ALOGW("DrasticRunner::saveAutosave: not available");
        return false;
    }
    // Slot 9 is drastic's reserved autosave slot (what the
    // drastic-android-mod auto-resumes from). saveStateSlot refuses 9
    // on purpose; this path is the sanctioned exception used on a
    // graceful exit.
    int rc = mSaveState(mFakeEnv, mFakeCls, 9);
    ALOGI("DrasticRunner::saveAutosave (slot 9) = %d", rc);
    return true;
}

bool DrasticRunner::loadStateSlot(int slot) {
    if (!mInitialized || !mLoadState) {
        ALOGW("DrasticRunner::loadStateSlot: not available "
              "(initialized=%d, mLoadState=%p)",
              mInitialized ? 1 : 0, (void*)mLoadState);
        return false;
    }
    if (slot < 0 || slot > 8) {
        ALOGW("DrasticRunner::loadStateSlot: refusing slot %d (valid 0..8)",
              slot);
        return false;
    }
    int rc = mLoadState(mFakeEnv, mFakeCls, slot);
    ALOGI("DrasticRunner::loadStateSlot(%d) = %d", slot, rc);
    return true;
}

// ---- Cheat API wrappers ----
// All gated on the game being booted (isFrameReady): the drastic cheat
// getters check the same emu-init/game-loaded guards and return 0/null
// before that. The overlay pauses the core while open, so these run in a
// quiet window.

// Decode a drastic byte[] return (NUL-terminated UTF-8 name/note) to a
// std::string, stopping at the first NUL within the allocated length.
static std::string readByteArrayString(void* ba) {
    if (!ba) return std::string();
    const jbyte* d = fakejni::getByteArrayData((jbyteArray)ba);
    jsize n = fakejni::getByteArrayLength((jbyteArray)ba);
    if (!d || n <= 0) return std::string();
    size_t len = strnlen((const char*)d, (size_t)n);
    return std::string((const char*)d, len);
}

int DrasticRunner::cheatCount() {
    if (!mGetCheatCount || !mInitialized || !isFrameReady()) return 0;
    return mGetCheatCount(mFakeEnv, mFakeCls);
}
int DrasticRunner::cheatFolderCount() {
    if (!mGetCheatFolderCount || !mInitialized || !isFrameReady()) return 0;
    return mGetCheatFolderCount(mFakeEnv, mFakeCls);
}
std::string DrasticRunner::cheatName(int idx) {
    if (!mGetCheatName || !mInitialized || !isFrameReady()) return std::string();
    return readByteArrayString(mGetCheatName(mFakeEnv, mFakeCls, idx));
}
std::string DrasticRunner::cheatNote(int idx) {
    if (!mGetCheatNote || !mInitialized || !isFrameReady()) return std::string();
    return readByteArrayString(mGetCheatNote(mFakeEnv, mFakeCls, idx));
}
std::string DrasticRunner::cheatFolderName(int folder) {
    if (!mGetCheatFolderName || !mInitialized || !isFrameReady()) return std::string();
    return readByteArrayString(mGetCheatFolderName(mFakeEnv, mFakeCls, folder));
}
bool DrasticRunner::cheatEnabled(int idx) {
    if (!mGetCheatEnabled || !mInitialized || !isFrameReady()) return false;
    return mGetCheatEnabled(mFakeEnv, mFakeCls, idx) != 0;
}
bool DrasticRunner::cheatFolderMultiSelect(int folder) {
    // Default true (no radio enforcement) when unavailable.
    if (!mGetCheatFolderMultiSelect || !mInitialized || !isFrameReady()) return true;
    return mGetCheatFolderMultiSelect(mFakeEnv, mFakeCls, folder) != 0;
}
int DrasticRunner::cheatFolderId(int idx) {
    if (!mGetCheatFolderId || !mInitialized || !isFrameReady()) return -1;
    return mGetCheatFolderId(mFakeEnv, mFakeCls, idx);
}
void DrasticRunner::setCheatEnabled(int idx, bool on) {
    if (!mSetCheatEnabled || !mInitialized || !isFrameReady()) return;
    mSetCheatEnabled(mFakeEnv, mFakeCls, idx, on ? 1 : 0);
}
void DrasticRunner::applyCheats() {
    if (!mUpdateCheats || !mInitialized || !isFrameReady()) return;
    // Ensure the cheats/ dir exists or drastic's .cht writer fails silently
    // (mCacheDir = the DraStic data dir). Harmless if it already exists.
    if (!mCacheDir.empty()) {
        std::string dir = mCacheDir + "/cheats";
        mkdir(dir.c_str(), 0770);
    }
    // 1 = write cheats/<gamecode>.cht + set the dirty byte the run loop
    // consumes next tick to re-apply enabled cheats to live RAM.
    mUpdateCheats(mFakeEnv, mFakeCls, 1);
    ALOGI("DrasticRunner::applyCheats: updateCheats(1)");
}

int DrasticRunner::customCheatCount() {
    if (!mGetCustomCheatCount || !mInitialized || !isFrameReady()) return 0;
    return mGetCustomCheatCount(mFakeEnv, mFakeCls);
}
std::string DrasticRunner::customCheatName(int idx) {
    if (!mGetCustomCheatName || !mInitialized || !isFrameReady()) return std::string();
    return readByteArrayString(mGetCustomCheatName(mFakeEnv, mFakeCls, idx));
}
bool DrasticRunner::customCheatEnabled(int idx) {
    if (!mGetCustomCheatEnabled || !mInitialized || !isFrameReady()) return false;
    return mGetCustomCheatEnabled(mFakeEnv, mFakeCls, idx) != 0;
}
void DrasticRunner::setCustomCheatEnabled(int idx, bool on) {
    if (!mSetCustomCheatEnabled || !mInitialized || !isFrameReady()) return;
    mSetCustomCheatEnabled(mFakeEnv, mFakeCls, idx, on ? 1 : 0);
}
std::vector<int> DrasticRunner::customCheatData(int idx) {
    std::vector<int> out;
    if (!mGetCustomCheatData || !mInitialized || !isFrameReady()) return out;
    void* arr = mGetCustomCheatData(mFakeEnv, mFakeCls, idx);
    if (!arr) return out;
    const jint* d = fakejni::getIntArrayData((jintArray)arr);
    jsize n = fakejni::getIntArrayLength((jintArray)arr);
    if (d && n > 0) out.assign(d, d + n);
    return out;
}
void DrasticRunner::removeCustomCheat(int idx) {
    if (!mRemoveCustomCheat || !mInitialized || !isFrameReady()) return;
    mRemoveCustomCheat(mFakeEnv, mFakeCls, idx);
}
int DrasticRunner::addCustomCheat(const std::string& name,
                                  const std::vector<int>& words, bool enabled) {
    if (!mAddCustomCheat || !mInitialized || !isFrameReady()) return -1;
    jintArray arr = fakejni::allocIntArray((jsize)words.size());
    if (!words.empty()) {
        ((JNIEnv*)mFakeEnv)->SetIntArrayRegion(arr, 0, (jsize)words.size(),
                                               (const jint*)words.data());
    }
    void* nameStr = ((JNIEnv*)mFakeEnv)->NewStringUTF(name.c_str());
    int rc = mAddCustomCheat(mFakeEnv, mFakeCls, nameStr, arr,
                             (int)words.size(), enabled ? 1 : 0);
    ALOGI("DrasticRunner::addCustomCheat(\"%s\", %zu words) = %d",
          name.c_str(), words.size(), rc);
    return rc;
}
int DrasticRunner::findCustomCheat(const std::vector<int>& words) {
    if (!mFindCustomCheat || !mInitialized || !isFrameReady()) return -1;
    jintArray arr = fakejni::allocIntArray((jsize)words.size());
    if (!words.empty()) {
        ((JNIEnv*)mFakeEnv)->SetIntArrayRegion(arr, 0, (jsize)words.size(),
                                               (const jint*)words.data());
    }
    return mFindCustomCheat(mFakeEnv, mFakeCls, arr, (int)words.size());
}

void DrasticRunner::resetSystem() {
    if (!mInitialized || !mResetDS) {
        ALOGW("DrasticRunner::resetSystem: not available");
        return;
    }
    ALOGI("DrasticRunner::resetSystem");
    mResetDS(mFakeEnv, mFakeCls);
}

void DrasticRunner::setVolumeRuntime(int vol0to100) {
    if (!mInitialized || !mSetAudioVolume) return;
    if (vol0to100 < 0)   vol0to100 = 0;
    if (vol0to100 > 100) vol0to100 = 100;
    mCurVolume = vol0to100;
    mSetAudioVolume(mFakeEnv, mFakeCls, vol0to100);
}

int DrasticRunner::applyMasterStatePatch(const char* reason) {
    char patchEnable[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.drastic_master_patch",
                 patchEnable, "1");
    if (patchEnable[0] == '0') return 0;

    Dl_info patchInfo;
    if (!mUpdateInput || !dladdr((void*)mUpdateInput, &patchInfo) ||
            !patchInfo.dli_fbase) {
        ALOGW("DrasticRunner::applyMasterStatePatch(%s): no base "
              "address, skip", reason ? reason : "?");
        return 0;
    }
    uint8_t* master =
            (uint8_t*)((uintptr_t)patchInfo.dli_fbase + 0x14c000);

    // The 13 GPU fast-path feature-flag scalars that fix the BG-layer
    // priority rendering bug. See the post-startGame block in init() for
    // the full A/B-derived offset table and rationale.
    struct Target { size_t off; uint32_t value; };
    static const Target targets[] = {
        { 0x00010, 6 }, { 0x00014, 6 }, { 0x09140, 0 },
        { 0x8b68c, 6 }, { 0x8b690, 6 }, { 0x8ba98, 0 },
        { 0x8bab8, 1 }, { 0x8bad0, 1 }, { 0x8badc, 1 },
        { 0x8bae8, 3 }, { 0x8bb00, 1 }, { 0x8bb10, 1 },
        { 0x8bb28, 1 },
    };
    const int kNumTargets = sizeof(targets)/sizeof(targets[0]);
    int rewrote = 0;
    for (int i = 0; i < kNumTargets; i++) {
        uint32_t cur;
        memcpy(&cur, master + targets[i].off, 4);
        if (cur != targets[i].value) {
            memcpy(master + targets[i].off, &targets[i].value, 4);
            rewrote++;
        }
    }
    ALOGW("DrasticRunner: master-state patch (%s): rewrote %d / %d "
          "targets", reason ? reason : "?", rewrote, kNumTargets);
    return rewrote;
}

DrasticRunner::DsMainRam DrasticRunner::dsMainRam() {
    DsMainRam r;
    if (!mArm64Base) return r;
    // master = soBase + 0x14c000; the live context pointer is stored at
    // *(master) (it equals soBase + 0x14d000 once DraStic's onInit has run).
    // The DS memory-region descriptor is context + 0x35d9930; its first field
    // is the pointer to the 4 MB ARM9 Main RAM. Read it through the live
    // context pointer rather than a fixed offset so the access survives any
    // re-anchoring of the descriptor block.
    uintptr_t master = (uintptr_t)mArm64Base + 0x14c000;
    uintptr_t ctx = *reinterpret_cast<uintptr_t*>(master);
    if (!ctx) return r;
    uintptr_t desc = ctx + 0x35d9930;
    uint8_t* ram = *reinterpret_cast<uint8_t**>(desc);
    if (!ram) return r;
    r.base = ram;
    r.mask = 0x3FFFFF;
    return r;
}

DrasticRunner::DsDataTcm DrasticRunner::dsDataTcm() {
    DsDataTcm r;
    if (!mArm64Base) return r;
    // Same chase as dsMainRam(): master = soBase + 0x14c000; the live context
    // pointer is *(master). The DS memory-region table is at context + 0x35d9930;
    // Main RAM is its first entry (offset 0) and Data TCM is the fourth (offset
    // 0x18). Read through the live context pointer so it survives re-anchoring.
    uintptr_t master = (uintptr_t)mArm64Base + 0x14c000;
    uintptr_t ctx = *reinterpret_cast<uintptr_t*>(master);
    if (!ctx) return r;
    uintptr_t desc = ctx + 0x35d9930 + 0x18;
    uint8_t* tcm = *reinterpret_cast<uint8_t**>(desc);
    if (!tcm) return r;
    r.base = tcm;
    r.mask = 0x3FFF;
    return r;
}

int DrasticRunner::waitForFrameAfter(int lastCount, int timeoutMs) {
    std::unique_lock<std::mutex> lk(mFrameCvMutex);
    mFrameCv.wait_for(lk, std::chrono::milliseconds(timeoutMs),
                      [&] { return mFrameCounter.load() != lastCount; });
    return mFrameCounter.load();
}

uint16_t DrasticRunner::dsEmulatedFrameCounter() {
    if (!mArm64Base) return 0;
    uintptr_t master = (uintptr_t)mArm64Base + 0x14c000;
    return *reinterpret_cast<volatile uint16_t*>(master + 0x4b0);
}

// Overlay drastic's fast-forward bits onto an already-built config word.
// Shared by setFastForward and applyVideoConfigLive so a live video/audio
// change never stomps an active fast-forward and a fast-forward toggle
// never reverts a live change. _V (bit 29) is the runtime FF lever; when
// it is set, drastic's converter (libdrastic 0x17db0) reads _FfwdSpeed
// (bits 12-15) as an INDEX into a fixed interval table at rodata 0x1070c0
// = { 100000, 33333, 25000, 16666, 12500, 5000 } microseconds (smaller
// interval = faster; index 6..15 store 0 = "no FF interval" = back to the
// normal 60fps pace, i.e. FF disabled, so index 5 is the practical max).
// ff_no_threaded3d optionally drops to single-threaded 3D while FF is held
// so the 3D worker is never starved by the faster producer.
static void applyFfBits(long& bits, bool ffOn) {
    if (!ffOn) return;
    int cap = property_get_int32("persist.gammaos.drastic_nano.ffspeed", 5);
    if (cap < 0)  cap = 0;
    if (cap > 15) cap = 15;
    bits |= 0x20000000L;                          // _V fast-forward lever
    bits = (bits & ~0xF000L) | ((long)cap << 12); // _FfwdSpeed index
    if (property_get_int32(
            "persist.gammaos.drastic_nano.ff_no_threaded3d", 0)) {
        bits &= ~0x10000000L;                     // clear _Threaded3D
    }
}

void DrasticRunner::setFastForward(bool on) {
    if (!mInitialized || !mApplyConfig) return;
    if (on == mFastForwardOn) return;
    mFastForwardOn = on;
    // mBaseConfigBits holds the user's current (non-FF) settings, kept up
    // to date by applyVideoConfigLive, so FF composes with live changes.
    long bits = mBaseConfigBits;
    applyFfBits(bits, on);
    mApplyConfig(mFakeEnv, mFakeCls, bits);
    ALOGI("DrasticRunner::setFastForward: %s (applyConfig=0x%lx)",
          on ? "ON" : "OFF", bits);
    // applyConfig re-runs drastic's config converter, which resets the
    // GPU fast-path feature flags back to fallback-mode defaults and
    // brings back the BG-layer priority rendering glitch (on every game,
    // 2D and 3D). The one-shot init patch already exited, so re-assert
    // those 13 scalars right now. The logged rewrite count tells us how
    // many applyConfig actually clobbered.
    applyMasterStatePatch(on ? "fast-forward on" : "fast-forward off");

    // Reset the frame-blend prev state on both edges: on FF-on the first
    // blended frame must wait for a genuine capture (no stale flash); on
    // FF-off the next 1x frame must use the plain path immediately.
    mFfPrevValid = false;
    mFfBlendThisFrame = false;

    // Audio during fast-forward. With the pacer removed the engine emits
    // samples faster than realtime into the fixed-rate output queue, so
    // FF audio plays pitched-up and crackly, which aliases with the video
    // skip and amplifies the perceived choppiness. Mute it on FF and
    // restore the user's exact volume on release (what mGBA/Dolphin/RA
    // all do). Keep the engine running -- only zero the volume. Gated by
    // persist.gammaos.drastic_nano.ff_mute (default 1).
    if (mSetAudioVolume) {
        if (on) {
            if (property_get_int32(
                    "persist.gammaos.drastic_nano.ff_mute", 1)) {
                mPreFfVolume = mCurVolume;
                setVolumeRuntime(0);
            }
        } else if (mPreFfVolume >= 0) {
            setVolumeRuntime(mPreFfVolume);
            mPreFfVolume = -1;
        }
    }
}

void DrasticRunner::applyVideoConfigLive(long callerBits) {
    if (!mInitialized || !mApplyConfig) return;
    // callerBits is a full config word from DrasticPrefs::applyConfigBitsFrom
    // (all invariant bits present: _m0, sound, etc) and carries no FF-only
    // bits, so keep it as the live base: a later setFastForward composes
    // its lever onto the user's current settings, and a change made while
    // FF is held keeps FF active.
    mBaseConfigBits = callerBits;
    long bits = callerBits;
    applyFfBits(bits, mFastForwardOn);
    mApplyConfig(mFakeEnv, mFakeCls, bits);
    ALOGI("DrasticRunner::applyVideoConfigLive: applyConfig=0x%lx (ff=%d)",
          bits, mFastForwardOn ? 1 : 0);
    // Same converter-clobber repair as setFastForward: applyConfig resets
    // the 13 GPU fast-path scalars, so re-assert the master-state patch or
    // the BG-layer priority glitch returns on every game.
    applyMasterStatePatch("video/audio config change");
}

void DrasticRunner::redimDsTextures() {
    if (!mSurfaceReady || !mUseRenderFrame || !mFxSetup) return;
    int newW, newH;
    dsTexDims(dsHiresEnabled(), &newW, &newH);
    if (newW == mDsTexW && newH == mDsTexH) return;  // already correct

    // Share the shader-swap guard: redim and setShaderRuntime both mutate
    // the fx pass list (fxSetup + patchFinalPassFbo) and must not
    // interleave. If a swap is in flight, retry next frame.
    bool expected = false;
    if (!mShaderSwapInFlight.compare_exchange_strong(expected, true)) {
        mPendingDsReDim.store(true);
        return;
    }
    ALOGI("DrasticRunner::redimDsTextures: %dx%d -> %dx%d (hires=%d)",
          mDsTexW, mDsTexH, newW, newH, dsHiresEnabled() ? 1 : 0);

    // Drain in-flight GL reads of the DS textures before respecifying.
    glFinish();

    // Respecify the backing store on the same texture names. The
    // NEAREST/CLAMP params set in initSurface survive a glTexImage2D
    // respecify, so they do not need resetting.
    glBindTexture(GL_TEXTURE_2D, mDsTopTex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, newW, newH, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glBindTexture(GL_TEXTURE_2D, mDsBotTex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, newW, newH, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glBindTexture(GL_TEXTURE_2D, 0);

    mDsTexW = newW;
    mDsTexH = newH;
    mFxTexW = newW;
    mFxTexH = newH;

    // Re-run fxSetup so drastic's pass-resolution uniforms match the new
    // texture size, then re-assert the final-pass FBO redirect (fxSetup's
    // teardown can reset the final pass.fbo, which would otherwise leave
    // it pointing at FBO 0 and stop the displays from updating). Same
    // unpatch/fxSetup/patch resequence as setShaderRuntime.
    unpatchFinalPassFbo();
    mFxSetup(mFakeEnv, mFakeCls, mFxTexW, mFxTexH, 0, 0,
             mOffscreenW, mOffscreenH);
    patchFinalPassFbo();

    mShaderSwapInFlight.store(false);
}

bool DrasticRunner::setShaderRuntime(const std::string& absDfxPath) {
    if (!mSurfaceReady || !mFxLoad || !mFxSetup) {
        ALOGW("DrasticRunner::setShaderRuntime: surface not ready or "
              "fxLoad/fxSetup unavailable");
        return false;
    }
    if (access(absDfxPath.c_str(), R_OK) != 0) {
        ALOGW("DrasticRunner::setShaderRuntime: %s not readable",
              absDfxPath.c_str());
        return false;
    }

    // Re-entrance guard. The overlay menu runs on the same render
    // thread so in principle two calls can't interleave, but the atomic
    // is cheap insurance against future callers from any other thread
    // (e.g. a Settings-driven swap). No time-based throttle anymore --
    // the old scudo corruption that required it is fixed by the
    // leak-don't-delete path below, so back-to-back swaps are safe.
    bool expected = false;
    if (!mShaderSwapInFlight.compare_exchange_strong(expected, true)) {
        ALOGW("DrasticRunner::setShaderRuntime: already in flight, skip");
        return false;
    }
    mLastShaderSwap = std::chrono::steady_clock::now();
    mSwapCounter++;
    ALOGI("DrasticRunner::setShaderRuntime[#%d]: \"%s\" begin",
          mSwapCounter, absDfxPath.c_str());

    // Pause drastic so the DS producer thread is not racing our GL
    // state mutation. Restore the pause state afterwards; the overlay
    // typically calls us while already paused, so this is normally a
    // no-op.
    bool wasPaused = mPaused;
    if (!wasPaused && mPauseSystem) {
        mPauseSystem(mFakeEnv, mFakeCls, 1);
    }

    // Drain the GPU pipeline before fxLoad tears down the current
    // pass list. Without this, the driver may still be reading from
    // intermediate pass FBOs / textures that fxLoad is about to
    // glDeleteFramebuffers / glDeleteTextures and then free(), which
    // leaves the driver's userspace bookkeeping inconsistent with
    // scudo's view of those heap chunks. Empirically correlated with
    // the "invalid chunk state" aborts seen without this finish.
    glFinish();

    jstring jp = ((JNIEnv*)mFakeEnv)->NewStringUTF(absDfxPath.c_str());
    // Use the same pos_ptr/uv_ptr layout as init (bytes 0 and 192
    // inside mFxVbo). Stock drastic passes (0, 0x8A0) referencing its
    // internal pre-built VBO; we build our own VBO in initSurface so
    // runtime shader swaps must keep the same layout or the pass
    // runner's glVertexAttribPointer(uv_attr, ..., 0x8A0) reads past
    // our 384-byte VBO and fetches garbage UVs each frame -- visible
    // as a frozen/garbage output after changing shader.

    // CRITICAL: fxLoad's internal teardown (bl 202b8 at +0x1ffc0) walks
    // the old pass list and calls glDeleteFramebuffers on each pass's
    // `fbo` field. Our patchFinalPassFbo() overwrote the final pass.fbo
    // with mOffscreenFbo, so a naive second fxLoad would delete our
    // offscreen FBO, leaving mOffscreenFbo as a stale GL name.
    // Subsequent glBindFramebuffer(mOffscreenFbo) fails silently and
    // the displays stop updating ("screen doesn't update when I change
    // the shader"). Reset the final pass.fbo back to 0 first so fxLoad's
    // teardown deletes a harmless zero.
    unpatchFinalPassFbo();
    dumpFxCtxState("pre-fxLoad");
    GLboolean fbOk = glIsFramebuffer(mOffscreenFbo);
    ALOGI("DrasticRunner::setShaderRuntime[#%d]: pre-fxLoad "
          "mOffscreenFbo=%u glIsFramebuffer=%d",
          mSwapCounter, mOffscreenFbo, (int)fbOk);

    // WORKAROUND for scudo "invalid chunk state when deallocating"
    // crash in fxLoad's internal teardown (bl 202b8 at +0x1ffc0).
    // The teardown walks fx_ctx->head and frees per-pass sub-
    // allocations -- empirically one of those pointers is stale or
    // MTE-mismatched after 2..4 consecutive swaps, even with glFinish
    // and a 500 ms throttle. The pass struct addresses are all valid
    // scudo primary-pool chunks (0xb4...), but the scudo-reported
    // bad-free address is in a different region (0x76...), i.e. a
    // LARGE secondary allocation held by a pointer field inside a
    // pass. We cannot identify that field from the public disassembly.
    //
    // Null out head/tail/count so fxLoad's internal teardown finds an
    // empty list and returns immediately. The OLD GL programs and
    // FBOs get glDeleted by us first (walking the saved head), and
    // the pass structs themselves are intentionally leaked (~400 B
    // each) -- we cannot safely free them because we do not know
    // which internal fields to free first. At 400 B per swap this
    // is a couple MB of leak even for obsessive users; well under
    // the budget for a session.
    static constexpr uintptr_t kFxCtxOff    = 0x3f2d208;
    static constexpr uintptr_t kPassFboOff  = 344;
    static constexpr uintptr_t kPassNextOff = 368;
    uint8_t* fxCtxBase = mArm64Base
        ? reinterpret_cast<uint8_t*>(mArm64Base + kFxCtxOff)
        : nullptr;
    uint8_t* oldHead = nullptr;
    if (fxCtxBase) {
        oldHead = *reinterpret_cast<uint8_t**>(fxCtxBase + 0);
        // Zero head (+0), tail (+8), count (+0x480). Leave everything
        // else so we don't accidentally break state fxLoad's parser
        // relies on.
        *reinterpret_cast<uint8_t**>(fxCtxBase + 0) = nullptr;
        *reinterpret_cast<uint8_t**>(fxCtxBase + 8) = nullptr;
        *reinterpret_cast<uint32_t*>(fxCtxBase + 0x480) = 0;
        ALOGI("DrasticRunner::setShaderRuntime[#%d]: nulled fx_ctx head/"
              "tail/count before fxLoad (oldHead=%p)",
              mSwapCounter, oldHead);
    }

    // Do NOT call glDeleteProgram / glDeleteFramebuffers on the old
    // pass list. The Mali driver internally tracks shader-source and
    // framebuffer-attached-texture allocations in the scudo secondary
    // pool (0x76... range). When we glDeleteProgram a program whose
    // shader source was set via fxLoad, the driver's free path hits
    // an "invalid chunk state" in scudo on the second swap. Root
    // cause is almost certainly a refcount mismatch inside the
    // driver or libdrastic where two programs (or a program and its
    // fxLoad-owned cache entry) share a single source-string chunk
    // and the first delete corrupts the second one.
    //
    // Counting and logging what we would have leaked so a long
    // session can be audited. Each pass leaks: 1 GL program, at
    // most 1 FBO (not counting our offscreenFbo), and ~400 B of
    // pass struct. Programs dominate the leak; on Mali those are
    // typically under a few KB including metadata, so hundreds of
    // swaps are still well under a MB.
    {
        uint8_t* p = oldHead;
        int i = 0;
        while (p && i < 32) {
            uint8_t* next = *reinterpret_cast<uint8_t**>(p + kPassNextOff);
            p = next;
            ++i;
        }
        ALOGI("DrasticRunner::setShaderRuntime[#%d]: leaked %d old "
              "pass(es) (GL programs + FBOs + structs)",
              mSwapCounter, i);
    }

    // Make sure no program or framebuffer from the old list is
    // currently bound, so the driver cannot dereference a stale
    // internal pointer while fxLoad creates new programs.
    glUseProgram(0);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glFinish();

    int rc = mFxLoad(mFakeEnv, mFakeCls, (void*)jp, 0, 192);
    ALOGI("DrasticRunner::setShaderRuntime[#%d]: fxLoad(\"%s\") = %d",
          mSwapCounter, absDfxPath.c_str(), rc);

    dumpFxCtxState("post-fxLoad");
    fbOk = glIsFramebuffer(mOffscreenFbo);
    ALOGI("DrasticRunner::setShaderRuntime[#%d]: post-fxLoad "
          "mOffscreenFbo=%u glIsFramebuffer=%d",
          mSwapCounter, mOffscreenFbo, (int)fbOk);
    if (!fbOk) {
        ALOGE("DrasticRunner::setShaderRuntime: offscreen FBO was "
              "invalidated by fxLoad -- displays will stop updating "
              "until a surface rebuild");
    }

    // Re-invoke fxSetup with the same tex dims so drastic rebuilds
    // its render-state for the new shader. fxLoad's own cleanup
    // (called internally at its entry: bl 202b8 at +0x1ffc0) already
    // tore down the previous shader's program and FBOs. We do not
    // manually clear any sentinel.
    mFxSetup(mFakeEnv, mFakeCls,
             mFxTexW, mFxTexH, 0, 0,
             mOffscreenW, mOffscreenH);

    // fxLoad rebuilt the pass list; the new final pass has pass.fbo
    // defaulted back to 0. Re-redirect it into our offscreen FBO.
    patchFinalPassFbo();

    if (!wasPaused && mPauseSystem) {
        mPauseSystem(mFakeEnv, mFakeCls, 0);
    }
    mShaderSwapInFlight.store(false);
    return true;
}

void DrasticRunner::shutdown() {
    // Stop the background pixel-pull thread FIRST so it unblocks
    // from getScreenBuffers and stops calling into drastic state
    // that we're about to pause. The thread is detached, so we
    // can't join -- instead it polls mPixelPullRunning each iteration
    // and exits on its own. Give it a short grace period.
    if (mPixelPullRunning.load()) {
        ALOGI("DrasticRunner: shutdown stopping pixel-pull thread");
        mPixelPullRunning.store(false);
        // 25 ms is enough for a single getScreenBuffers round-trip
        // under normal conditions. If the thread is blocked inside
        // drastic's frame pacer it'll unblock on the next signal.
        usleep(25 * 1000);
    }

    if (mInitialized) {
        if (mPauseSystem) {
            // Pause first so drastic's worker threads park cleanly.
            // We don't have a jenv -- pass nullptr; drastic's
            // pauseSystem body doesn't dereference the env pointer
            // based on the 412-byte disasm (findings.md).
            mPauseSystem(nullptr, nullptr, 1);
        }
        if (mQuitSystem) {
            mQuitSystem(nullptr, nullptr);
        }
        mInitialized = false;
    }

    // NOTE: deliberately not dlclose()'ing -- drastic's internal
    // background threads may still be live and dlclose would yank
    // the text segment out from under them. For the preview path we
    // just leak the handles; the process tears down at handoff.
    //
    // For the on-demand test watcher (main.cpp), this means a
    // given gammaos-nano process can run exactly one start/stop
    // cycle before libdrastic's residual globals + detached
    // threads make a second init unsafe. That's fine for
    // strace-based debugging -- kill and respawn gammaos-nano
    // via `stop gammaos-nano; start gammaos-nano` between runs.
    mArm64Handle = nullptr;
    mCpuHandle = nullptr;
}

} // namespace android
