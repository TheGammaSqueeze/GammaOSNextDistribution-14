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
#include <sys/resource.h>
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
    0x10000000L;           // _Threaded3D (bit 28)

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
                         const std::string& romPath) {
    maybeStartThreadTracer();
    ALOGI("DrasticRunner: init cacheDir=%s rom=%s",
          cacheDir.c_str(), romPath.c_str());

    // ---- Phase 1: dlopen the libraries ----
    std::string cpuPath = cacheDir + "/libdrastic_cpu.so";
    std::string arm64Path = cacheDir + "/libdrastic_arm64.so";

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
    if (!loadSym(mFxSetup,          "Java_com_dsemu_drastic_DraSticJNI_fxSetup")) return false;
    if (!loadSym(mRenderFrame,      "Java_com_dsemu_drastic_DraSticJNI_renderFrame")) return false;
    if (!loadSym(mSignalScreen,     "Java_com_dsemu_drastic_DraSticJNI_signalScreen")) return false;
    if (!loadSym(mWaitScreen,       "Java_com_dsemu_drastic_DraSticJNI_waitScreen")) return false;
    if (!loadSym(mUpdateFrame,      "Java_com_dsemu_drastic_DraSticJNI_updateFrame")) return false;
    if (!loadSym(mUpdateInput,      "Java_com_dsemu_drastic_DraSticJNI_updateInput")) return false;
    if (!loadSym(mGetScreenBuffers, "Java_com_dsemu_drastic_DraSticJNI_getScreenBuffers")) return false;

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
    mOnInit(env, fakeCls, nullptr, 1, 34);
    ALOGI("DrasticRunner: onInit returned");

    // ---- Phase 5: applyConfig ----
    ALOGI("DrasticRunner: calling applyConfig(0x%lx)", kDefaultConfigBits);
    mApplyConfig(env, fakeCls, kDefaultConfigBits);
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
        jstring nick = env->NewStringUTF("Player");
        if (nick) {
            ALOGI("DrasticRunner: setFirmwareUserdata(\"Player\", "
                  "0xFF806B80)");
            mSetFirmwareUserdata(env, fakeCls, nick, 0xFF806B80);
            ALOGI("DrasticRunner: setFirmwareUserdata returned");
        }
    }
    if (mSetAutosaveInterval) {
        ALOGI("DrasticRunner: setAutosaveInterval(0)");
        mSetAutosaveInterval(env, fakeCls, 0);
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
    if (mFxSetup) {
        ALOGI("DrasticRunner: early fxSetup(%d, %d, 0, 0, 640, 480) "
              "from init thread (pre-startGame)",
              kDsScreenW, kDsScreenH);
        mFxSetup(env, fakeCls,
                 kDsScreenW, kDsScreenH, 0, 0,
                 /*viewW*/ 640, /*viewH*/ 480);
        ALOGI("DrasticRunner: early fxSetup returned");
    }

    ALOGI("DrasticRunner: spawning startGame thread with rom=%s",
          romPath.c_str());

    auto startGameFn  = mStartGame;
    void* fakeClsCopy = fakeCls;
    JNIEnv* envCopy   = env;
    jstring romCopy   = romJStr;

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
                                    startGameFn]() {
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
        unsigned char rc = startGameFn(envCopy, fakeClsCopy, romCopy,
                                        /*arg2 slot*/  0,
                                        /*arg3 cfg*/   kDefaultConfigBits,
                                        /*arg4*/       0,
                                        /*arg5 insrt*/ 0,
                                        /*arg6 clock*/ 0L);
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
    "varying vec2 vUv;\n"
    "void main() {\n"
    "  gl_Position = vec4(uRotation * aPos, 0.0, 1.0);\n"
    "  vUv = aUv;\n"
    "}\n";

static const char* kDrasticFs =
    "precision mediump float;\n"
    "varying vec2 vUv;\n"
    "uniform sampler2D uTex;\n"
    "uniform float uSaturation;\n"  // 0=grayscale, 1=full color
    "uniform float uGradient;\n"    // 0=none, 1=strong dark gradient
    "void main() {\n"
    // DS framebuffer is BGRA-packed in drastic (little-endian ABGR
    // int). Our jint upload as RGBA gives us BGR + alpha -- swizzle
    // so colors look right.
    "  vec4 c = texture2D(uTex, vUv);\n"
    "  vec3 rgb = vec3(c.b, c.g, c.r);\n"
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

void DrasticRunner::initSurface(int viewportW, int viewportH) {
    if (mSurfaceReady) return;
    if (!mInitialized || !mGetScreenBuffers) {
        ALOGE("DrasticRunner::initSurface: not initialized");
        return;
    }

    // Call drastic's fxSetup even though we don't use its GL path.
    // Phase 5 v3 finding: without fxSetup, drastic's internal frame
    // compositing is incomplete -- sprites and 3D layers are missing
    // from the getScreenBuffers output, leaving only the static
    // background. fxSetup initialises drastic's internal framebuffer
    // pool indices and the swap chain that getScreenBuffers reads
    // from. See DraSticGlView$j.smali:3193 (onSurfaceChanged).
    if (mFxSetup) {
        ALOGI("DrasticRunner::initSurface: fxSetup(%d, %d, 0, 0, %d, %d)",
              kDsScreenW, kDsScreenH, viewportW, viewportH);
        mFxSetup(mFakeEnv, mFakeCls,
                 kDsScreenW, kDsScreenH, 0, 0,
                 viewportW, viewportH);
    }

    // Destination arrays are already allocated in init() so the
    // background pixel-pull thread can use them immediately.

    // GL: compile shader, gen textures, build quad VBO.
    mQuadProgram = drLinkProgram(kDrasticVs, kDrasticFs);
    mQuadPosLoc      = glGetAttribLocation(mQuadProgram, "aPos");
    mQuadTexLoc      = glGetAttribLocation(mQuadProgram, "aUv");
    mQuadSamplerLoc  = glGetUniformLocation(mQuadProgram, "uTex");
    mQuadSatLoc      = glGetUniformLocation(mQuadProgram, "uSaturation");
    mQuadGradLoc     = glGetUniformLocation(mQuadProgram, "uGradient");
    mQuadRotLoc      = glGetUniformLocation(mQuadProgram, "uRotation");

    auto setupTex = [](unsigned int tex) {
        glBindTexture(GL_TEXTURE_2D, tex);
        // Linear filtering -- the DS screens are tiny (256x192) and
        // look blocky on a 640x480+ panel without interpolation.
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, kDsScreenW, kDsScreenH, 0,
                     GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    };
    glGenTextures(1, &mTopTex);
    setupTex(mTopTex);
    glGenTextures(1, &mBotTex);
    setupTex(mBotTex);

    glGenBuffers(1, &mQuadVbo);

    mViewportW = viewportW;
    mViewportH = viewportH;
    mSurfaceReady = true;
    ALOGI("DrasticRunner::initSurface: Option B ready (viewport %dx%d)",
          viewportW, viewportH);
}

void DrasticRunner::updatePixels() {
    if (!mSurfaceReady) return;
    // Non-blocking: only upload if the background pull thread has
    // produced at least one frame. Before that, the GL textures
    // retain whatever the previous render wrote (initially black).
    if (!mShadowReady.load(std::memory_order_acquire)) return;

    // Upload shadow to GL textures. Hold the mutex only during the
    // memcpy-equivalent glTexSubImage2D call -- GPU driver will
    // consume the source pointer synchronously so we're safe to
    // release the lock immediately after.
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

void DrasticRunner::drawFullscreenDsQuad(unsigned int tex,
                                          float saturation,
                                          float gradient) {
    if (!mSurfaceReady) return;

    // Read the current glViewport so we can compute an aspect-
    // preserving letterbox for the DS 256:192 (4:3) screen.
    GLint viewport[4] = {};
    glGetIntegerv(GL_VIEWPORT, viewport);
    const float vw = (float)viewport[2];
    const float vh = (float)viewport[3];
    if (vw <= 0.0f || vh <= 0.0f) return;

    const float dsAspect = 256.0f / 192.0f;
    const float vpAspect = vw / vh;

    // NDC coordinates in logical (non-rotated) space. We fill the
    // viewport edge-to-edge on the tighter axis and letterbox the
    // other. For a 4:3 panel matching the DS aspect this collapses
    // to (-1,-1)..(1,1) with no bars.
    float x0 = -1.0f, y0 = -1.0f, x1 = 1.0f, y1 = 1.0f;
    if (vpAspect > dsAspect) {
        // Panel wider than DS -- pillarbox left/right.
        float w = dsAspect / vpAspect;
        x0 = -w; x1 = w;
    } else if (vpAspect < dsAspect) {
        // Panel taller than DS -- letterbox top/bottom.
        float h = vpAspect / dsAspect;
        y0 = -h; y1 = h;
    }

    // Vertex data: (x, y, u, v). V is flipped because drastic writes
    // the DS framebuffer top-to-bottom but OpenGL texel rows are
    // bottom-to-top.
    const float verts[16] = {
        x0, y1, 0.0f, 0.0f,
        x1, y1, 1.0f, 0.0f,
        x0, y0, 0.0f, 1.0f,
        x1, y0, 1.0f, 1.0f,
    };

    glUseProgram(mQuadProgram);
    // Upload our cached DRM rotation matrix (identity unless
    // NanoMenu explicitly called setRotationMatrix before this
    // pass). Without this, the DS quad would be rendered in
    // logical NDC while the rest of NanoMenu's UI is in panel-
    // native NDC, causing orientation mismatch on rotated panels.
    if (mQuadRotLoc >= 0) {
        glUniformMatrix2fv(mQuadRotLoc, 1, GL_FALSE, mRotationMatrix);
    }
    if (mQuadSatLoc >= 0) glUniform1f(mQuadSatLoc, saturation);
    if (mQuadGradLoc >= 0) glUniform1f(mQuadGradLoc, gradient);
    if (mQuadSamplerLoc >= 0) glUniform1i(mQuadSamplerLoc, 0);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, tex);

    glDisable(GL_BLEND);
    glDisable(GL_DEPTH_TEST);

    glBindBuffer(GL_ARRAY_BUFFER, mQuadVbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(verts), verts, GL_STREAM_DRAW);
    glVertexAttribPointer(mQuadPosLoc, 2, GL_FLOAT, GL_FALSE,
                          4 * sizeof(float), (void*)0);
    glEnableVertexAttribArray(mQuadPosLoc);
    glVertexAttribPointer(mQuadTexLoc, 2, GL_FLOAT, GL_FALSE,
                          4 * sizeof(float), (void*)(2 * sizeof(float)));
    glEnableVertexAttribArray(mQuadTexLoc);

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glDisableVertexAttribArray(mQuadPosLoc);
    glDisableVertexAttribArray(mQuadTexLoc);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
}

void DrasticRunner::renderTopScreen(float saturation, float gradient) {
    drawFullscreenDsQuad(mTopTex, saturation, gradient);
}

void DrasticRunner::renderBottomScreen(float saturation, float gradient) {
    drawFullscreenDsQuad(mBotTex, saturation, gradient);
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
