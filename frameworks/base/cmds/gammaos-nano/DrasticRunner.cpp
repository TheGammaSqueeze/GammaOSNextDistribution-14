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
#include <signal.h>
#include <ucontext.h>
#include <stdio.h>
#include <sys/mman.h>
#include <sys/uio.h>
#include <sys/ioctl.h>
#include <linux/dma-buf.h>
#include <linux/dma-heap.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <cstring>
#include <cstdlib>
#include <chrono>
#include <thread>
#include <atomic>
#include <mutex>
#include <condition_variable>
#include <sys/time.h>
#include <cmath>
#include <vector>
#include <algorithm>
#include <unordered_map>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include "NanoMenuDrm.h"
#include <cutils/properties.h>
#include <utils/Log.h>

namespace android {
extern int sRingRenderIdx;

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

// Un-pin the large DS ROM mmap (and DraStic's big drastic_mapped_memory.dat
// ashmem) from a detached thread once libdrastic has mapped them.
//
// The gammaos-nano HOME process locks mlockall(MCL_CURRENT | MCL_FUTURE) and
// MUST keep it -- MCL_FUTURE is what keeps its fonts / glyph atlases / textures
// resident so on-screen-keyboard glyph pages never demand-fault back off the
// lz4-compressed EROFS image (which thrashes/OOMs). But that same MCL_FUTURE
// pins EVERY page a Quick Resume drastic PREVIEW faults from the ROM: a large
// DSi ROM (up to 512 MB, e.g. Pokemon White/Black 2) would balloon the home's
// unevictable RSS and OOM the launcher, exactly like the standalone drastic-nano
// freeze (which we fixed by dropping MCL_FUTURE there -- the home cannot).
//
// So instead of unlocking everything, we munlock ONLY the two huge drastic
// regions: the ROM (matched by ".nds" in /proc/self/maps -- after the FakeJNI
// FUSE->/data/media redirect it maps under /data/media, still ".nds") and the
// mapped-memory ashmem. munlock clears VM_LOCKED on those VMAs so their clean
// file / shmem pages become reclaimable again; MCL_FUTURE does not re-lock an
// existing VMA on later faults, so one pass per region suffices (we loop a short
// while because the ROM VMA appears a beat after startGame begins, and re-munlock
// is idempotent). The home's UI assets stay locked. Harmless no-op in the
// standalone drastic-nano process (MCL_CURRENT: the ROM was never locked).
static void unlockLargeDrasticMappingsAsync() {
    std::thread([]() {
        pthread_setname_np(pthread_self(), "dn-rom-munlock");
        bool romDone = false, ashmemDone = false;
        for (int iter = 0; iter < 120 && !(romDone && ashmemDone); iter++) {
            FILE* f = fopen("/proc/self/maps", "r");
            if (f) {
                char line[512];
                while (fgets(line, sizeof(line), f)) {
                    bool isRom    = strstr(line, ".nds") != nullptr;
                    bool isAshmem = strstr(line, "drastic_mapped_memory") != nullptr;
                    if (!isRom && !isAshmem) continue;
                    unsigned long s = 0, e = 0;
                    if (sscanf(line, "%lx-%lx", &s, &e) == 2 && e > s) {
                        if (munlock((void*)s, (size_t)(e - s)) == 0) {
                            if (isRom)    romDone = true;
                            if (isAshmem) ashmemDone = true;
                        }
                    }
                }
                fclose(f);
            }
            usleep(100000);  // 100 ms
        }
        ALOGI("DrasticRunner: ROM/ashmem munlock pass done (rom=%d ashmem=%d)",
              romDone ? 1 : 0, ashmemDone ? 1 : 0);
    }).detach();
}

bool DrasticRunner::init(const std::string& cacheDir,
                         const std::string& romPath,
                         const std::string& libsDir,
                         bool soundEnabled,
                         long configBitsOverride,
                         int autosaveIntervalSeconds,
                         const std::string& initialShader,
                         int autoLoadSlot,
                         int firmwareLanguage,
                         int firmwareColor,
                         int firmwareBdayMonth,
                         int firmwareBdayDay,
                         const std::string& firmwareNick) {
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
            installVblankPacing(base);
            installThreaded3dSync(base);
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

    // In-memory perf patch (opt-in, default OFF): the hi-res scanline compositing
    // inner loop at libdrastic_arm64.so +0x8edec is the top CPU hot path on heavy
    // PW2 scenes (simpleperf ~15.7%) and is DDR-bandwidth-bound - two write-only
    // 32bpp layer buffers copied per span with st1 (which allocate in cache). We
    // redirect the loop to a code cave that uses non-temporal STNP for those two
    // stores so the write-only data does not evict the read working set -> frees
    // effective DDR bandwidth. Gated by sys.gammaos.drastic_nano.libpatch_ntstore.
    // Reversible by relaunch: we patch only the in-memory mapping, never the .so on
    // disk, so a fresh dlopen (next launch with the prop off) is unpatched.
    int ntMode = property_get_int32("sys.gammaos.drastic_nano.libpatch_ntstore", 0);
    if (mArm64Base && ntMode > 0) {
        uint8_t* base = mArm64Base;
        // Two caves for the +0x8edec hi-res composite inner loop, selected by the
        // prop value: mode 1 = STNP (non-temporal) stores to free DDR bandwidth;
        // mode 2 = a BYTE-IDENTICAL st1 copy (control-flow test - proves the
        // trampoline branches/relocation are correct, isolating any STNP-specific
        // fault). Both end with subs/b.gt back to cave start; the return `b` is
        // appended at runtime. Encodings checked against llvm-objdump of the
        // original loop (the counter decrement must write w11, 0x7100216b; an
        // earlier 0x71002169 wrote w9 and crashed the rasterizer workers).
        static const uint32_t kCaveStnp[10] = {
            0x4cdfa9a2u, // ld1  {v2.4s,v3.4s},[x13],#32
            0x4cdfa9c0u, // ld1  {v0.4s,v1.4s},[x14],#32
            0x0cdf7184u, // ld1  {v4.8b},[x12],#8
            0xac000c22u, // stnp q2,q3,[x1]
            0x91008021u, // add  x1,x1,#32
            0xac000400u, // stnp q0,q1,[x0]
            0x91008000u, // add  x0,x0,#32
            0x0c9f7044u, // st1  {v4.8b},[x2],#8
            0x7100216bu, // subs w11,w11,#8
            0x54fffeecu, // b.gt cave_start (-9 words)
        };
        static const uint32_t kCaveCopy[8] = {
            0x4cdfa9a2u, // ld1  {v2.4s,v3.4s},[x13],#32
            0x4cdfa9c0u, // ld1  {v0.4s,v1.4s},[x14],#32
            0x0cdf7184u, // ld1  {v4.8b},[x12],#8
            0x4c9fa822u, // st1  {v2.4s,v3.4s},[x1],#32
            0x4c9fa800u, // st1  {v0.4s,v1.4s},[x0],#32
            0x0c9f7044u, // st1  {v4.8b},[x2],#8
            0x7100216bu, // subs w11,w11,#8
            0x54ffff2cu, // b.gt cave_start (-7 words)
        };
        const uint32_t* kCave = (ntMode == 2) ? kCaveCopy : kCaveStnp;
        const int nFixed      = (ntMode == 2) ? 8 : 10;
        const uintptr_t kLoopOff   = 0x8edec;
        const uintptr_t kReturnOff = 0x8ee0c;
        // DIAGNOSTIC: confirm base+offset actually points at the expected loop head
        // (0x4cdfa9a2 = ld1) and return site (0x8b2bc800 = add x0,x0,w11). If these
        // do not match, the crash is a vaddr/file-offset skew, not the cave logic.
        ALOGI("DrasticRunner: NT precheck mode=%d loop@+0x%lx=0x%08x (exp 4cdfa9a2) "
              "ret@+0x%lx=0x%08x (exp 8b2bc800)",
              ntMode,
              (unsigned long)kLoopOff, *reinterpret_cast<uint32_t*>(base + kLoopOff),
              (unsigned long)kReturnOff, *reinterpret_cast<uint32_t*>(base + kReturnOff));
        long ps = sysconf(_SC_PAGESIZE); if (ps <= 0) ps = 4096;
        // Cave placed INSIDE libdrastic's own executable RX padding. The R E
        // PT_LOAD has filesz 0x13228c which page-rounds to 0x133000, so
        // 0x13228c..0x133000 is zero-filled, executable, never-code/never-data
        // padding. Using it (instead of an anonymous mmap that DraStic later
        // overwrote as data -> inconsistent SIGSEGV/exit) guarantees the veneer
        // survives and is trivially within `b` range of the loop.
        const uintptr_t kCaveOff = 0x132c00;   // in the RX padding, +44B < 0x133000
        uint8_t* cave = base + kCaveOff;
        intptr_t retOff  = (intptr_t)(base + kReturnOff) - (intptr_t)((uint32_t*)cave + nFixed);
        intptr_t caveOff = (intptr_t)cave - (intptr_t)(base + kLoopOff);
        uint8_t* cpg = (uint8_t*)((uintptr_t)cave & ~(uintptr_t)(ps - 1));
        if (mprotect(cpg, (size_t)ps, PROT_READ|PROT_WRITE|PROT_EXEC) == 0) {
            uint32_t* c = reinterpret_cast<uint32_t*>(cave);
            for (int i = 0; i < nFixed; i++) c[i] = kCave[i];
            c[nFixed] = 0x14000000u | (uint32_t)((retOff >> 2) & 0x03ffffff);
            mprotect(cpg, (size_t)ps, PROT_READ|PROT_EXEC);
            __builtin___clear_cache((char*)cave, (char*)cave + (nFixed + 1) * 4);
            uint8_t* site = base + kLoopOff;
            uint8_t* pgs = (uint8_t*)((uintptr_t)site & ~(uintptr_t)(ps - 1));
            if (mprotect(pgs, (size_t)ps * 2,
                         PROT_READ|PROT_WRITE|PROT_EXEC) == 0) {
                *reinterpret_cast<uint32_t*>(site) =
                        0x14000000u | (uint32_t)((caveOff >> 2) & 0x03ffffff);
                mprotect(pgs, (size_t)ps * 2, PROT_READ|PROT_EXEC);
                __builtin___clear_cache((char*)site, (char*)site + 4);
                ALOGI("DrasticRunner: NT patch mode=%d applied in-lib cave "
                      "(caveOff=%ld retOff=%ld)", ntMode, (long)caveOff, (long)retOff);
            } else {
                ALOGW("DrasticRunner: NT patch mprotect(site) failed: %s",
                      strerror(errno));
            }
        } else {
            ALOGW("DrasticRunner: NT patch mprotect(cave) failed: %s",
                  strerror(errno));
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
        // The ONLY reliable "this is a real play session" signal is
        // sys.gammaos.drastic_nano.session, which the drastic-nano binary sets
        // for its own lifetime (main.cpp, before dr.init) and drastic-nano.rc
        // clears on session_done. Do NOT also treat persist.gammaos.nano.drastic_nano
        // as a real-session signal: that is a DEVICE-level flag (set on every unit
        // whose DS launches route through drastic-nano), so it is 1 during the
        // home's in-process QR preview too -- which then wrongly took the fxRender
        // path, walked an empty pass list (no .dfx in the QR cache), and left the
        // red canary in mOffscreenFbo (the "no live preview, solid red" symptom).
        const bool realSession =
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
    fakejni::addRamStateSlot(kRamStateSlot);
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
        // Packed firmware userdata: (bday_day << 24) | (bday_month << 16) |
        // (color << 8) | language -- the same int the real drastic app
        // hands to setFirmwareUserdata, built from its SharedPreferences.
        // When the caller supplied prefs-derived values (firmwareLanguage
        // >= 0) use them so the DS boots in the user's chosen language;
        // otherwise fall back to the legacy hardcoded default so the
        // gammaos-nano QR-preview / smoke-test callers are unchanged.
        const bool fromPrefs = (firmwareLanguage >= 0);
        const char* nickStr = fromPrefs ? firmwareNick.c_str() : "GammaOS";
        int fwPacked;
        if (fromPrefs) {
            fwPacked = ((firmwareBdayDay   & 0xff) << 24)
                     | ((firmwareBdayMonth & 0xff) << 16)
                     | ((firmwareColor     & 0xff) << 8)
                     | (firmwareLanguage   & 0xff);
        } else {
            // Language: 1=English, Color: 0=grey, Birthday: Jan 1
            fwPacked = (1 << 24) | (1 << 16) | (0 << 8) | 1; // 0x01010001
        }
        jstring nick = env->NewStringUTF(nickStr);
        if (nick) {
            ALOGI("drastic: firmware userdata nick=%s packed=0x%08x lang=%d "
                  "(%s)", nickStr, fwPacked,
                  fromPrefs ? firmwareLanguage : 1,
                  fromPrefs ? "prefs" : "default");
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
        // Runtime knob for experiments: sys.gammaos.drastic_nano.emu_rt
        // 1 (default) = SCHED_RR 5 as below; 0 = stay SCHED_OTHER at
        // nice -10 (inherited by the workers the same way). Read once at
        // startGame, so a relaunch is needed to change it.
        // emu_rt: 1 = SCHED_RR 5 (the historical boost), 0 = SCHED_OTHER at
        // nice -10, 2 = keep what this thread inherited from its creator (the
        // presenter's SCHED_FIFO 80, so emulator, workers and presenter share
        // one FIFO level and never preempt each other; measured the smoothest
        // producer on the RG DS). The workers drastic spawns inherit whatever
        // is set here.
        const int emuRt = property_get_int32("sys.gammaos.drastic_nano.emu_rt", 1);
        {
            sched_param sp = {};
            sp.sched_priority = 5;
            int rc = (emuRt == 1) ? pthread_setschedparam(pthread_self(), SCHED_RR, &sp) : EPERM;
            mEmuTid = (pid_t)syscall(__NR_gettid);
            if (emuRt == 0) {
                sched_param so = {}; so.sched_priority = 0;
                if (pthread_setschedparam(pthread_self(), SCHED_OTHER, &so) != 0)
                    ALOGW("DrasticRunner: startGame SCHED_OTHER failed: %s", strerror(errno));
                pid_t selfTid = (pid_t)syscall(SYS_gettid);
                if (setpriority(PRIO_PROCESS, selfTid, -10) != 0)
                    ALOGW("DrasticRunner: startGame nice=-10 failed: %s", strerror(errno));
                ALOGW("DrasticRunner: startGame emu_rt=0, SCHED_OTHER nice -10");
            } else if (emuRt != 1) {
                int pol = 0; sched_param cur = {};
                pthread_getschedparam(pthread_self(), &pol, &cur);
                ALOGW("DrasticRunner: startGame emu_rt=%d, keeping inherited policy %d prio %d",
                      emuRt, pol, cur.sched_priority);
            } else if (rc == 0) {
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
    // Same emu_rt gate as the self-boost inside the thread: with emu_rt=0
    // the emulator and the workers it spawns stay SCHED_OTHER.
    if (property_get_int32("sys.gammaos.drastic_nano.emu_rt", 1) == 1)
        drasticBoostThread(mStartGameThread.native_handle(), "startGame");
    mStartGameThread.detach();

    // Un-pin the huge ROM mmap + mapped-memory ashmem so the home's
    // mlockall(MCL_FUTURE) does not OOM the launcher on a large Quick Resume
    // preview. No-op in the standalone drastic-nano process. See
    // unlockLargeDrasticMappingsAsync.
    unlockLargeDrasticMappingsAsync();

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
    // DIAGNOSTIC (afbc_coherent_test): force the legacy getScreenBuffers path,
    // which grabs BOTH DS screens as one atomic coherent pair (pixel-pull ->
    // updatePixels -> mTopTex/mBotTex), instead of fxRender. Used to determine
    // whether drastic can hand us both screens from one frame (no shader).
    if (property_get_bool("persist.gammaos.drastic_nano.afbc_coherent_test", false)) {
        mUseRenderFrame = false;
        ALOGW("DrasticRunner: afbc_coherent_test ON -- forcing legacy "
              "getScreenBuffers (coherent pair, no shader)");
    }

    // Portrait offscreen dimensions: both DS screens stacked.
    // Each screen gets the full viewport width; height is doubled
    // for dual-display (top screen + bottom screen stacked).
    mOffscreenW = viewportW;
    mOffscreenH = dualDisplay ? viewportH * 2 : viewportH;

    // PERF: cap the fx-pipeline render resolution independently of the panel.
    // drastic's whole per-fragment fx pass (prescale/LCD/blit) renders INTO this
    // offscreen FBO, so sizing it to the full panel (e.g. 1024x768 on the TrimUI
    // Brick) makes every filter pass pay panel-sized fill even though the DS
    // source is only 256x192/screen -- up to ~16x wasted work on a weak Mali,
    // which is the frame-pacing / frameskip gap vs standalone DraStic (standalone
    // renders low and lets the HW compositor upscale). We do the same: render the
    // fx at panel/scale and let the final drawDsQuad upscale the offscreen texture
    // to the real panel (see mOffscreenTex GL_LINEAR below). Confirmed by hand:
    // `wm size 512x384` (scale 2 on a 1024x768 panel) restored smooth pacing.
    //
    // Scale = persist.gammaos.nano.drastic_render_scale. DEFAULT 1 (full-panel
    // fx) so shader detail is unchanged from today: drastic's prescale/LCD grid
    // uniforms are tied to the on-screen pixel size, so a value > 1 renders those
    // shaders at panel/scale and nearest-upscales -> a coarser LCD/scanline grid.
    // It is therefore an OPT-IN perf knob (2 = quarter the fx fill, sharp
    // integer nearest upscale, softer grid shaders) rather than the default.
    // Clamped so the offscreen never drops below the DS content resolution.
    {
        int rscale = 1;
        char rs[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.drastic_render_scale", rs, "");
        if (rs[0]) { int v = atoi(rs); if (v >= 1 && v <= 8) rscale = v; }
        // The caller can pin the scale: the DRM dual-panel path renders the
        // shader's final pass straight into the combined panel buffer whose
        // pass geometry is fixed here by fxSetup, so with Half Resolution off it
        // needs the full-size offscreen (full-resolution shaders, both panels
        // filled); the render-scale prop then only applies with Half Resolution on.
        if (mFxScaleOverride >= 1) rscale = mFxScaleOverride;
        if (rscale > 1) {
            int scaledW = mOffscreenW / rscale;
            int scaledH = mOffscreenH / rscale;
            // Clamp to >= DS content (per-screen width; both screens stacked in H).
            int dsW = 0, dsH = 0;
            dsTexDims(dsHiresEnabled(), &dsW, &dsH);
            int minW = dsW;
            int minH = dsH * 2;   // top + bottom stacked in the offscreen
            if (scaledW < minW) scaledW = minW;
            if (scaledH < minH) scaledH = minH;
            if (scaledW < mOffscreenW || scaledH < mOffscreenH) {
                ALOGI("DrasticRunner: fx render scale %d -> offscreen %dx%d "
                      "(panel %dx%d), upscaled at blit",
                      rscale, scaledW, scaledH, mOffscreenW,
                      dualDisplay ? viewportH * 2 : viewportH);
                mOffscreenW = scaledW;
                mOffscreenH = scaledH;
            }
        }
    }

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
        // Keep GL_NEAREST (setupTex default): with an integer render-scale the
        // offscreen upscales to the panel by an exact NxN pixel block, so
        // nearest-neighbour gives a sharp, pixel-perfect image (no bilinear
        // softening) -- the intended retro look.
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

// Direct render: aim drastic's final shader pass at the AFBC ring target so
// the presenter's copy pass (offscreen -> ring, 1.1 ms GPU and ~5 MB of
// traffic per frame) disappears. The pass runner draws verts 0..5 for the
// top DS screen and 6..11 for the bottom one; a per-layout VBO variant
// places each screen in the lower or upper half of the 640x960 target and
// flips both texture axes for the half whose panel scans from the hinge.
void DrasticRunner::setDirectTarget(unsigned int fbo, int w, int h, bool topToLower,
                                    bool rotLower, bool rotUpper) {
    mDirectFbo = fbo;
    mDirectW = w;
    mDirectH = h;
    mDirectVariant = (topToLower ? 1 : 0) | (rotLower ? 2 : 0) | (rotUpper ? 4 : 0);
    mDirectDone = false;
}

unsigned int DrasticRunner::directVbo(int variant) {
    if (variant < 0 || variant >= 8) return mFxVbo;
    const bool vflipAll = property_get_bool("sys.gammaos.drastic_nano.direct_vflip", false);
    const bool uflipAll = property_get_bool("sys.gammaos.drastic_nano.direct_uflip", false);
    const int key = variant | (vflipAll ? 8 : 0) | (uflipAll ? 16 : 0);
    if (mDirectVboKey[variant] == key && mDirectVbo[variant]) return mDirectVbo[variant];
    const bool topToLower = variant & 1, rotLower = variant & 2, rotUpper = variant & 4;
    float v[96];
    auto quadPos = [&](int base, float y0, float y1) {
        const float q[12] = { -1.f, y0,  +1.f, y0,  -1.f, y1,   -1.f, y1,  +1.f, y0,  +1.f, y1 };
        for (int k = 0; k < 12; k++) v[base * 2 + k] = q[k];
    };
    auto quadUv = [&](int base, bool flip) {
        float u0 = 0.f, u1 = 1.f, t0 = 0.f, t1 = 1.f;
        if (flip != uflipAll) { u0 = 1.f; u1 = 0.f; }
        if (flip != vflipAll) { t0 = 1.f; t1 = 0.f; }
        const float q[12] = { u0, t0,  u1, t0,  u0, t1,   u0, t1,  u1, t0,  u1, t1 };
        for (int k = 0; k < 12; k++) v[48 + base * 2 + k] = q[k];
    };
    quadPos(0, topToLower ? -1.f : 0.f, topToLower ? 0.f : +1.f);
    quadPos(6, topToLower ? 0.f : -1.f, topToLower ? +1.f : 0.f);
    quadUv(0, topToLower ? rotLower : rotUpper);
    quadUv(6, topToLower ? rotUpper : rotLower);
    quadPos(12, +1.f, +1.f); quadUv(12, false);
    quadPos(18, -1.f, +1.f); quadUv(18, false);
    if (!mDirectVbo[variant]) glGenBuffers(1, &mDirectVbo[variant]);
    glBindBuffer(GL_ARRAY_BUFFER, mDirectVbo[variant]);
    glBufferData(GL_ARRAY_BUFFER, sizeof(v), v, GL_STATIC_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    mDirectVboKey[variant] = key;
    ALOGW("DrasticRunner: direct render VBO variant %d built (topToLower=%d rotLower=%d rotUpper=%d vflip=%d uflip=%d)",
          variant, (int)topToLower, (int)rotLower, (int)rotUpper, (int)vflipAll, (int)uflipAll);
    return mDirectVbo[variant];
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
                // Global one-shot cap: patchFinalPassFbo runs in the
                // per-frame render path (fxSetup rebuilds the pass list per
                // slot), so a per-call cap would still spam once per frame on
                // a shader that genuinely ships bad enums. Cap across the
                // whole session so a real problem surfaces a few times then
                // goes quiet.
                static int sBadEnumLogs = 0;
                if (sBadEnumLogs < 16) {
                    ALOGW("DrasticRunner::patchFinalPassFbo: pass[%d] "
                          "sampler[%u].unit_enum = 0x%x (invalid) -> "
                          "forcing to GL_TEXTURE%u (0x%x)",
                          count, i, *unitP, i, 0x84C0 + i);
                    sBadEnumLogs++;
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

    // Verbose per-call diagnostics. patchFinalPassFbo runs in the per-frame
    // render path: renderSlotShaded re-patches whenever the slot geometry
    // changes, and the two DS screen slots alternate geometry every frame, so
    // this fires ~2x per frame (~125 lines/sec into logd on the Brick) -- pure
    // per-frame string-format + logd IPC cost that steals CPU from the
    // emulator. Gate behind an opt-in debug prop (default off); the pass-list
    // walk, the sampler normalize and the FBO patch above always run.
    static const bool kFxDebug =
            property_get_int32("persist.gammaos.drastic_nano.fxdebug", 0) != 0;
    if (kFxDebug) {
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

        static int sWalkLogs = 0;
        if (sWalkLogs++ < 4) ALOGI("DrasticRunner::patchFinalPassFbo: walked %d pass(es), "
              "final pass.fbo %u -> %u (targetFbo)",
              count, oldFbo, targetFbo);
        if (sWalkLogs <= 4) ALOGI("DrasticRunner::patchFinalPassFbo: pass fields "
              "program=%u posAttrib=%u uvAttrib=%u resUnif=%u sclUnif=%u "
              "outW=%u outH=%u samplerCount=%u",
              program, posAttrib, uvAttrib, resUnif, sclUnif,
              outW, outH, samplerCnt);
        if (sWalkLogs <= 4) ALOGI("DrasticRunner::patchFinalPassFbo: final sampler[0] unit=0x%x "
              "idx=%u  sampler[1] unit=0x%x idx=%u "
              "(total bad unit_enums normalized across all passes: %u)",
              samp0Unit, samp0Idx, samp1Unit, samp1Idx, totalBadNormalized);
    }
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


// ---- Slot content probe (diagnostic, sys.gammaos.drastic_nano.slot_probe) ----
// Reads libdrastic's screen double-buffer state directly: slotArray at BSS
// +0x1f8/+0x200, curSlot at +0x958, pixel type at +0x95c (0x10 = 16bpp), the
// per-screen hires flags at +0x968, the per-screen ready mask byte at
// 0x3f2db80, and the emulator's frame counters at 0x3c9b120 (rendered) and
// 0x3c9b124 (total). Hashes a sparse sample of each screen in the FRONT slot
// (the one fxRender uploads) and the BACK slot before the upload, and the
// same front pointer again after it, so a producer write into the slot being
// uploaded, a slot flip mid-upload, or one screen changing an iteration later
// than the other all show up directly in the log. Prop value = iterations to
// log; cleared to 0 when done. Read-only except for clearing the ready mask.
namespace {
struct SlotProbeState {
    int      remaining = 0;
    int      iter = 0;
    uint8_t* base = nullptr;
};
SlotProbeState gSlotProbe;

uint32_t slotProbeHash(const uint8_t* p, size_t bytes) {
    if (!p || bytes < 64) return 0;
    uint32_t h = 2166136261u;
    for (size_t off = 0; off + 4 <= bytes; off += 64) {
        uint32_t v;
        memcpy(&v, p + off, 4);
        h ^= v;
        h *= 16777619u;
    }
    return h;
}
} // namespace

// ---- Vblank-locked pacing ----
// drastic paces itself with a timer: its frame limiter (libdrastic +0x1b7a8)
// reads a microsecond clock through +0x1b26c (called at +0x1b76c in the
// limiter reset and +0x1b814 in the limiter), keeps a deadline that advances
// by the period at master+0x8aae4 (units of 1/3 us; 0 means 50000 = 60.000
// Hz) and sleeps the remainder through the usleep thunk +0x1b30c (called at
// +0x1b934 and +0x1b98c). Nothing else feeds it: the audio-level check it
// calls first (+0x1e1e4) is a stub returning 0. So redirecting those four
// call sites makes the emulator run exactly one frame per panel vblank, in
// phase with it, with no frame ever duplicated or dropped for rate mismatch.
namespace {
// SPU mix cave control block (see the spu_mix_lines cave in init): [0] lines since the last
// mix, [1] lines per mix (0xffffffff = disarmed until the first frame-end submit has run, so the
// mixer is never entered before drastic has finished bringing the sound unit up), [2] mixes done.
volatile uint32_t* gSpuMixCtl = nullptr;
uint32_t gSpuMixLines = 0;
std::atomic<bool>     gPaceOn{false};
std::atomic<uint32_t> gVblSeq{0};
std::mutex            gPaceMu;
std::condition_variable gPaceCv;
// Step mode (run-ahead): the pacer thread stops ticking and the emulator
// thread waits in drasticVWait without the 50 ms free-run timeout, so it
// advances exactly one frame per stepOneFrame() tick. gEmuParked is true
// while the emulator thread sits in that wait (a frame boundary).
std::atomic<bool>     gStepMode{false};
std::atomic<bool>     gEmuParked{false};
std::mutex            gParkMu;
std::condition_variable gParkCv;
std::atomic<int64_t>  gLastStepTickUs{0};
std::atomic<int64_t>  gPacerNextTickUs{0};   // the pacer's next scheduled tick (steady clock us)
std::atomic<int64_t>  gRaSaveCostUs{5000};   // measured park-save cost (EMA)
std::atomic<uint32_t> gRaStatSaveSkipped{0}; // park saves skipped for lack of slack
void raFlushDeferredUnmaps();   // DS memory-map remap dedup, defined with the run-ahead primitives
void raJoin3dWorker(uint8_t* base);   // threaded-3D worker join (run-ahead)
extern uint8_t* gRaLibBase;
// Preemptive-frames engine state (see runAheadPacerTick).
std::atomic<int>      gRaMode{0};            // 0 off, 2 preemptive frames
std::atomic<bool>     gRaBurst{false};       // hidden replay frames in flight: presenter must not consume
std::atomic<uint32_t> gRaBurstUntilSeq{0};   // pacing-miss / lead bookkeeping suspended until this seq
volatile uint8_t*     gRaReadyMask = nullptr;
// Set when the presenter consumed a fresh VISIBLE frame; the DS slot upload
// only happens then, so a repeated present during a replay burst keeps the
// last shown frame instead of picking a replayed one out of the slots.
std::atomic<bool>     gRaFreshVisible{true};
// Front slot index of the last VISIBLE flip. drastic's curSlot toggles on
// every flip, hidden ones included, so with run-ahead on the presenter
// binds this slot instead of deriving it from curSlot.
std::atomic<int>      gRaShownFront{-1};
// Input poke: setInputWithTouch wakes the pacer so a replay burst can start
// the moment the input changes instead of at the next tick.
std::atomic<bool>     gRaInputPoke{false};
std::mutex            gRaPokeMu;
std::condition_variable gRaPokeCv;
// Parked operations: drastic consumes save/load requests at the END of a
// frame (after emulating it), so a request armed before a tick costs a
// whole frame. Instead the pacer asks the emulator thread, while it sits
// parked in drasticVWait (the same frame boundary), to call drastic's
// internal save (+0x17308) or load (+0x7acc4) routine directly. No frame
// runs between the request and its effect.
std::atomic<int>      gRaParkOp{0};          // 1 load, 2 save
uint8_t*              gRaParkBuf = nullptr;
size_t                gRaParkLen = 0;
std::atomic<bool>     gRaParkDone{false};
uint32_t              gRaParkGen = 0;          // code generation of the entry being loaded
std::atomic<bool>     gRaParkOk{false};
constexpr int kRaMaxRing = 6;   // N+1 entries, N <= 4
// drastic keeps referencing parts of a loaded image for a while after its
// load returns (its own load path frees that buffer late and never notices;
// a ring save into it later crashed the 3D geometry restore). So the buffer
// a burst just loaded from is retired: swapped out of the ring for a fresh
// one and left untouched until two more loads have happened.
uint8_t* gRaRetired[2] = {nullptr, nullptr};
uint64_t gRaRingHash[kRaMaxRing] = {0, 0, 0, 0, 0, 0};   // diagnostic: FNV of each saved image
bool gRaHashCheck = false;
inline uint64_t raFnv(const uint8_t* p, size_t n) {
    uint64_t h = 1469598103934665603ULL;
    for (size_t k = 0; k < n; k += 64) { h ^= p[k]; h *= 1099511628211ULL; }   // sampled: 1 byte per 64
    return h;
}
int gRaRetiredIdx = 0;
int gRaParkRingIdx = -1;   // ring index of the entry a parked load reads
uint8_t* gRaRing[kRaMaxRing] = {nullptr, nullptr, nullptr, nullptr, nullptr, nullptr};
size_t   gRaRingLen[kRaMaxRing] = {0, 0, 0, 0, 0, 0};
// JIT invalidation skip. drastic's +0x37cd0(cpu, addr) is called from the
// CPU write handlers whenever a write lands on a page holding translated
// code (self-modifying code, overlays) and with -1 for a full reset; the
// state load calls it with -1 unconditionally (+0x1c694). A cave on every
// non-load site bumps gRaCodeGen (the cave writes it, single emulator
// thread). Each ring entry records the generation at its save; a burst
// load whose entry has the current generation skips the load's full
// invalidation (translations are still valid: same code), which removes
// the ~16 ms retranslation from the first replayed frame.
volatile uint32_t gRaCodeGen = 0;
uint32_t gRaRingGen[kRaMaxRing] = {0, 0, 0, 0, 0, 0};
bool gRaJitSkipOn = false;
bool gRaWarmLoad = false;   // the parked load in flight is the warm-up (state unchanged: keep the GX FIFOs)
std::atomic<uint32_t> gRaStatJitSkipped{0}, gRaStatJitFull{0};
std::atomic<int> gRaFrames{0}, gRaRingNext{0}, gRaRingCount{0};
void raRunParkedOp();
uint32_t raPatchInsn(uint8_t* base, uintptr_t off, uint32_t insn);
extern "C" void raAudioSubmitHook(uint8_t* ctx);
// SPU mix trace (sys.gammaos.drastic_nano.spu_trace=1): one record per mixer call from the
// scanline cave, with the two capture units' state and the ring buffers they write, so the
// emulated timeline of a capture-fed delay line can be reconstructed offline.
struct SpuTraceRec { uint32_t cycles, widx; uint64_t cap0, cap1; uint32_t line, cnt; int16_t ring0[680]; int16_t ring1[680]; uint8_t spu[3328]; };
static SpuTraceRec* gSpuTrace = nullptr;
static std::atomic<uint32_t> gSpuTraceN{0};
static const uint32_t kSpuTraceCap = 4000;
// In-ring interpolation repair (persist.gammaos.drastic_nano.ring_repair): Golden Sun DD streams speech
// through an SPU capture-feedback echo ring (680 samples, capture len 680) that channels 1/3 loop-read.
// DraStic reads+clears it in blocks, so the reader crosses cleared-but-not-yet-refilled zero-gaps and
// steps to a floor (the scratch). Hardware/melonDS never expose such gaps. Right before the channel
// mix reads the ring, linearly interpolate across every internal zero-run (circular, between the two
// flanking non-zero samples). Offline replay of the ring trace: reader-crossed discontinuities 133 -> 8;
// hold-last 83, forward-fill 358, so linear interpolation is the one that works. The required clear
// (capture side) is left intact so the echo still decays. Gated on the exact echo config so no other
// game or address is touched.
static std::atomic<uint32_t> gRingRepairCalls{0}, gRingRepairFilled{0};
// Universal form: the ring length, sample width and mode come from the capture unit itself, not from
// one game's numbers. Length is in samples for both formats (the clear loop compares the sample index
// against it and scales the byte offset by the width). Gated to loop-mode, non-add-mode captures.
static const uint32_t kRingMaxSamples = 32768;
static uint8_t  gRingSave[2][kRingMaxSamples * 2];
static uint32_t gRingSaveBytes[2] = {0, 0};
static uint32_t gRingCfgSeen[2] = {0xffffffffu, 0xffffffffu};   // (len<<8 | cnt) last logged per unit
static inline bool ringRepairTarget(uint8_t* spu, int u, uint8_t** out, uint32_t* len, int* width) {
    const uint8_t cnt = spu[0x40cc4 + u * 32];
    if (!(cnt & 0x80)) return false;                                            // not running
    const uint32_t L = *reinterpret_cast<uint32_t*>(spu + 0x40cc0 + u * 32);
    uint8_t* p = *reinterpret_cast<uint8_t**>(spu + 0x40cb8 + u * 32);
    const uint32_t key = (L << 8) | cnt;
    const bool ok = !(cnt & 0x04) && !(cnt & 0x01) && L >= 16 && L <= kRingMaxSamples && p;   // loop, not add-mode, sane, mapped
    if (gRingCfgSeen[u] != key) {                                               // one line per new config, for the sweep
        gRingCfgSeen[u] = key;
        ALOGI("DrasticRunner: RINGREPAIR cfg unit=%d len=%u cnt=0x%02x fmt=%s mode=%s add=%d -> %s", u, L, cnt,
              (cnt & 0x08) ? "pcm8" : "pcm16", (cnt & 0x04) ? "oneshot" : "loop", (cnt & 0x01) ? 1 : 0, ok ? "repair" : "skip");
    }
    if (!ok) return false;
    *out = p; *len = L; *width = (cnt & 0x08) ? 1 : 2;
    return true;
}
template <typename T> static uint32_t ringInterpFill(T* r, uint32_t L) {
    int firstnz = -1;
    for (uint32_t i = 0; i < L; i++) if (r[i]) { firstnz = (int)i; break; }
    if (firstnz < 0) return 0;                                                  // silent ring, nothing to bridge
    uint32_t prev = (uint32_t)firstnz, filled = 0;
    for (uint32_t step = 1; step <= L; step++) {
        uint32_t i = ((uint32_t)firstnz + step) % L;
        if (!r[i]) continue;
        uint32_t gap = (i - prev + L) % L;
        if (gap > 1) {
            long a = r[prev], b = r[i];
            for (uint32_t k = 1; k < gap; k++) r[(prev + k) % L] = (T)(a + ((b - a) * (long)k) / (long)gap);
            filled += gap - 1;
        }
        prev = i;
    }
    return filled;
}
// Hardware SPU output routing + real sound capture (persist.gammaos.drastic_nano.hw_route).
// DS SOUNDCNT bits 8-9 / 10-11 select what the LEFT / RIGHT speaker plays: 0 = the mixer, 1 = channel 1,
// 2 = channel 3, 3 = ch1+ch3; bits 12/13 drop ch1/ch3 from the mixer; the two capture units record the
// mixer (left / right) into RAM rings. Golden Sun DD sets 0xB97F: capture the mixer sans ch1/3 into the
// rings, then play ONLY ch1/3 (which loop those rings plus the game's software-mixed voice). DraStic
// ignores all of it: the capture stores zeros and the whole mixer goes to the speaker, so the voice
// (via the rings) and the music (direct) reach the output by different paths and the ring path is
// mostly silence -> "overlapping, unclear" against melonDS/hardware where one summed 32.7 kHz path
// carries everything. Emulation, using DraStic's own channel decoder for every sample so per-channel
// rendering is bit-identical to DraStic: run the channel mix twice into two accumulators, A = all
// channels except 1/3 (the capture source) and B = only the channels the routing selects for output,
// write A into the rings at the capture units' own positions/rates, and leave B for the mixer's tail.
// Active only while SOUNDCNT selects ch1/ch3 output AND a capture unit is running; otherwise the
// original path runs unchanged. The interp ring repair is skipped while this is active (the rings then
// hold real samples).
static std::atomic<uint32_t> gHwRouteMixes{0}, gHwRouteCapt{0}, gHwFallback{0};
// Set to 1 only while spuHwRoute is actively routing (a capture-routed game like Golden Sun), 0 otherwise. The cubic
// PCM16 interpolation cave reads it and falls back to a plain nearest fetch when 0, so games that do NOT use the
// capture routing (the vast majority, e.g. Pokemon) pay nothing for the cubic and keep stock audio/performance; only
// the routed voice that needs the melonDS match gets the cubic.
static volatile int gCubicActive = 0;
static int32_t* gHwAccA = nullptr;
static uint16_t gHwLastCnt = 0;
extern "C" void spuMixTrace(uint8_t* master);
extern "C" int spuHwRoute(uint8_t* spu, int32_t* acc, uint32_t n, uint8_t* master, void (*chanmix)(uint8_t*, int32_t*, uint32_t)) {
    static int sOn = -1;
    if (sOn < 0) sOn = property_get_bool("persist.gammaos.drastic_nano.hw_route", true) ? 1 : 0;
    if (!sOn || n == 0 || n > 8192) return 0;
    // Register mirror: the channel mix loads it as *(spu+0x40cd8) and reads master volume at +0x100,
    // i.e. the mirror starts at IO 0x04000400 and SOUNDCNT (0x04000500) is at +0x100.
    uint8_t* regs = *reinterpret_cast<uint8_t**>(spu + 0x40cd8);
    if (!regs) return 0;
    const uint16_t cntRaw = *reinterpret_cast<uint16_t*>(regs + 0x100);   // NOT the register (garbage); logged only
    static int sFixed = -1; if (sFixed < 0) sFixed = property_get_int32("persist.gammaos.drastic_nano.hw_route_cnt", 0xB97F);
    const uint16_t cnt = (uint16_t)sFixed;                                  // hardware-verified value for this routing class
    gHwLastCnt = cntRaw;
    {   // find SOUNDCNT empirically: scan the first 0x1000 bytes of the mirror for the 0x?97F pattern the game
        // uses (bit15 set, vol 0x7f, drop bits 12/13 set) and log where it lives; also trace the ring here.
        static int shots = 0;
        if (shots < 4 && (gHwRouteMixes.load() % 900) == 0) { shots++;
            char lg[400]; int n = 0;
            for (int o = 0; o < 0x1000 && n < 300; o += 2) { uint16_t v = *reinterpret_cast<uint16_t*>(regs + o); if ((v & 0xb07f) == 0xb07f) n += snprintf(lg + n, sizeof(lg) - n, " +%x:%04x", o, v); }
            ALOGI("DrasticRunner: HWROUTE regs=%p candidates(bit15,12,13,vol7f):%s", regs, lg[0] ? lg : " none"); }
        if (gSpuTrace) spuMixTrace(master);
    }
    const int selL = (cnt >> 8) & 3, selR = (cnt >> 10) & 3;
    const bool cap0 = (spu[0x40cc4] & 0x80) != 0, cap1 = (spu[0x40cc4 + 32] & 0x80) != 0;
    if ((selL == 0 && selR == 0) || !(cap0 || cap1)) return 0;         // plain mixer output: nothing to emulate
    if (!gHwAccA) gHwAccA = static_cast<int32_t*>(calloc(8192 * 2, sizeof(int32_t)));
    if (!gHwAccA) return 0;
    gCubicActive = 1;                                                     // routing this game: let the cubic PCM16 interp run (reset below)
    uint8_t* ch = spu + 0x40028;                                          // 16 channel records, stride 0xc8, +190 = active flag
    uint8_t save[16];
    for (int i = 0; i < 16; i++) save[i] = ch[i * 0xc8 + 190];
    if (gSpuTrace) {   // (diagnostic, persist.gammaos.drastic_nano.hw_route_diffdump) ch4 contribution by DIFFERENCE of two full mixes
        static int sDD = -1; if (sDD < 0) sDD = property_get_bool("persist.gammaos.drastic_nano.hw_route_diffdump", false) ? 1 : 0;
        static FILE* fd_ = nullptr; static FILE* fs_ = nullptr; static int di = 0;
        if (sDD) {
            if (!di) { di = 1; fd_ = fopen("/data/local/tmp/ch4_diff.bin", "wb"); fs_ = fopen("/data/local/tmp/ch4_src2.bin", "wb"); }
            if (fd_ && fs_ && save[4] && n >= 8 && n <= 512) {
                static int32_t* s1 = nullptr; static int32_t* s2 = nullptr;
                if (!s1) { s1 = static_cast<int32_t*>(calloc(8192 * 2, sizeof(int32_t))); s2 = static_cast<int32_t*>(calloc(8192 * 2, sizeof(int32_t))); }
                static uint8_t recs[16 * 0xc8]; memcpy(recs, ch, sizeof(recs));
                memset(s1, 0, n * 2 * sizeof(int32_t)); chanmix(spu, s1, n);              // full mix, all channels as-is
                uint32_t hdr[2] = {n, *reinterpret_cast<uint32_t*>(ch + 4 * 0xc8 + 144)};
                fwrite(hdr, 4, 2, fs_); fwrite(ch + 4 * 0xc8, 2, 64, fs_);                  // ch4's decode buffer after the real advance
                memcpy(ch, recs, sizeof(recs));
                ch[4 * 0xc8 + 190] = 0;
                memset(s2, 0, n * 2 * sizeof(int32_t)); chanmix(spu, s2, n);              // full mix without ch4
                memcpy(ch, recs, sizeof(recs));
                for (uint32_t i = 0; i < n * 2; i++) s1[i] -= s2[i];
                fwrite(&n, 4, 1, fd_); fwrite(s1, sizeof(int32_t), n * 2, fd_);
            }
        }
    }
    if (gSpuTrace) {   // (diagnostic, persist.gammaos.drastic_nano.hw_route_bufdump) ch4 decode buffer vs its mixed contribution
        static int sBD = -1; if (sBD < 0) sBD = property_get_bool("persist.gammaos.drastic_nano.hw_route_bufdump", false) ? 1 : 0;
        static FILE* fb = nullptr; static FILE* fm = nullptr; static int bi = 0;
        if (sBD) {
            if (!bi) { bi = 1; fb = fopen("/data/local/tmp/ch4_buf.bin", "wb"); fm = fopen("/data/local/tmp/ch4_mix.bin", "wb"); }
            if (fb && fm && save[4] && n >= 8 && n <= 512) {
                static int32_t* sc = nullptr; if (!sc) sc = static_cast<int32_t*>(calloc(8192 * 2, sizeof(int32_t)));
                static uint8_t recs[16 * 0xc8]; memcpy(recs, ch, sizeof(recs));
                for (int i = 0; i < 16; i++) ch[i * 0xc8 + 190] = (i == 4) ? save[i] : 0;
                memset(sc, 0, n * 2 * sizeof(int32_t)); chanmix(spu, sc, n);
                // the decode buffer AFTER the solo mix holds the samples just consumed (64 x s16 at record+0)
                uint32_t hdr[2] = {n, *reinterpret_cast<uint32_t*>(ch + 4 * 0xc8 + 144)};
                fwrite(hdr, 4, 2, fb); fwrite(ch + 4 * 0xc8, 2, 64, fb);
                fwrite(&n, 4, 1, fm); fwrite(sc, sizeof(int32_t), n * 2, fm);
                memcpy(ch, recs, sizeof(recs));
            }
        }
    }
    {   // (diagnostic, persist.gammaos.drastic_nano.hw_route_perch) per-channel solo mix: rms and HF share of each channel
        static int sPer = -1; if (sPer < 0) sPer = property_get_bool("persist.gammaos.drastic_nano.hw_route_perch", false) ? 1 : 0;
        static uint32_t cnt_ = 0;
        if (sPer && (++cnt_ % 200) == 0 && n >= 32) {
            static int32_t* scratch = nullptr; if (!scratch) scratch = static_cast<int32_t*>(calloc(8192 * 2, sizeof(int32_t)));
            // snapshot the full channel records so solo mixes do not advance state permanently
            static uint8_t recs[16 * 0xc8]; memcpy(recs, ch, sizeof(recs));
            char lg[600]; int m = 0;
            for (int c = 0; c < 16 && m < (int)sizeof(lg) - 40; c++) {
                if (!save[c]) continue;
                for (int i = 0; i < 16; i++) ch[i * 0xc8 + 190] = (i == c) ? save[i] : 0;
                memset(scratch, 0, n * 2 * sizeof(int32_t)); chanmix(spu, scratch, n);
                memcpy(ch, recs, sizeof(recs));   // restore all records (positions, flags, decode buffers)
                double e = 0, hf = 0; double prev = 0;
                for (uint32_t i = 0; i < n; i++) { double v = scratch[i * 2] / 4096.0; e += v * v; double d = v - prev; hf += d * d; prev = v; }
                m += snprintf(lg + m, sizeof(lg) - m, " ch%d:%.0f/%.2f", c, sqrt(e / n), hf / (e + 1e-9));
            }
            const uint8_t fmt1 = recs[1 * 0xc8 + 188];
            ALOGI("DrasticRunner: HWROUTE perch n=%u (rms/diff-ratio; higher ratio = more HF)%s fmt1=%d", n, lg, fmt1);
        }
    }
    // A: capture source = mixer without ch1/ch3 (SOUNDCNT bits 12/13 drop them from the mixer; the
    // routing case only matters when the game also excludes them, which Golden Sun does: 0xB97F).
    memset(gHwAccA, 0, n * 2 * sizeof(int32_t));
    const bool drop1 = (cnt >> 12) & 1, drop3 = (cnt >> 13) & 1;
    static uint8_t started[2] = {0, 0};                        // our persistent "started" state for ch1/ch3
    if (drop1) ch[1 * 0xc8 + 190] = 0;
    if (drop3) ch[3 * 0xc8 + 190] = 0;
    chanmix(spu, gHwAccA, n);
    // restore the flags the mix may have cleared (channels that ended); ch1/ch3 come back as STARTED if we
    // started them before (a channel that toggles 0->1 every mix re-enters the loop as freshly keyed and
    // restarts, which produced a burst of discontinuities and a level jump).
    for (int i = 0; i < 16; i++) if (i != 1 && i != 3) save[i] = ch[i * 0xc8 + 190];
    save[1] = started[0] ? 1 : save[1]; save[3] = started[1] ? 1 : save[3];
    // Optional (persist.gammaos.drastic_nano.hw_route_repair): bridge the game's half-ring zero-slots by linear
    // interpolation right before ch1/ch3 read the ring (same repair as ring_repair, applied in place; the
    // capture pass below then overwrites the bridged slots with real samples on its next revolution).
    {
        static int sRep = -1; if (sRep < 0) sRep = property_get_bool("persist.gammaos.drastic_nano.hw_route_repair", false) ? 1 : 0;
        if (sRep) for (int u = 0; u < 2; u++) {
            uint8_t* rec = spu + 0x40ca8 + u * 32; if (!(rec[0x1c] & 0x80)) continue;
            uint8_t* dst = *reinterpret_cast<uint8_t**>(rec + 0x10); const uint32_t len = *reinterpret_cast<uint32_t*>(rec + 0x18);
            if (dst && len >= 16 && len <= 65536 && !(rec[0x1c] & 0x08)) ringInterpFill(reinterpret_cast<int16_t*>(dst), len);
        }
    }
    // B: output = only the selected channels, into the mixer's own accumulator (already zeroed by the mixer)
    for (int i = 0; i < 16; i++) ch[i * 0xc8 + 190] = 0;
    // ch1/ch3 are keyed on (SOUNDxCNT bit 31) but DraStic never started them (+190 stays 0, so the mixer
    // skips them: it knows its own capture rings are silent). Start them for the output pass when the
    // routing selects them; the mixer then decodes the ring itself via its refill path.
    auto playing = [&](int c) { uint32_t* rp = *reinterpret_cast<uint32_t**>(ch + c * 0xc8 + 152); return rp && (*rp & 0x80000000u); };
    static int sForce = -1; if (sForce < 0) sForce = property_get_int32("persist.gammaos.drastic_nano.hw_route_force", 1);
    // Start ch1/ch3 the way DraStic's own key-on does: +190 = has-samples (loop entry), +192 = playing
    // (the decoder +0x71740 gates on it; without it the position stalls at the key-on value and the
    // channel never advances, which is what left the read head frozen at one slot).
    for (int c : {1, 3}) {
        const bool sel = (c == 1) ? ((selL & 1) || (selR & 1)) : ((selL & 2) || (selR & 2));
        uint8_t* rc = ch + c * 0xc8;
        if (sel && sForce && playing(c)) { rc[190] = 1; started[c == 3] = 1; }
        else { rc[190] = save[c]; if (!playing(c)) started[c == 3] = 0; }
    }
    chanmix(spu, acc, n);
    // Self-correction: the SOUNDCNT is hardcoded to Golden Sun's 0xB97F (ch1/ch3 routing). A different game that
    // trips the capture activation but does NOT use this routing gets ch1/ch3 forced as its only output, and if
    // those channels carry nothing the whole frame is silenced (measured on NFS Underground 2). When the routed
    // output is essentially silent, fall back to gHwAccA (the mix without ch1/ch3, already computed for capture,
    // no channel re-advance) so hw_route never mutes a title it does not actually apply to. Early-exit the energy
    // sum as soon as it clears the silence threshold (the common, audible case) instead of summing the whole frame.
    {
        const double thr = (double)n * 64.0;
        double eb = 0; for (uint32_t i = 0; i < n * 2; i++) { eb += (double)acc[i] * acc[i]; if (eb >= thr) break; }
        if (eb < thr) {   // avg |acc| < ~5.7, i.e. below ~0.001 FS: the routing produced silence
            memcpy(acc, gHwAccA, n * 2 * sizeof(int32_t));
            const uint32_t k = gHwFallback.fetch_add(1, std::memory_order_relaxed);
            if (k < 4) ALOGI("DrasticRunner: HWROUTE routed output silent, using normal mix (this title is not Golden-Sun-routed)");
        }
    }
    // put every channel back to DraStic's own flags; the forced-on ch1/ch3 state is per-pass only, so the
    // next mix's capture pass never sees the ring channels as sources (that leak fed the ring back into
    // itself and low-passed the music).
    for (int i = 0; i < 16; i++) ch[i * 0xc8 + 190] = save[i];
    if (started[0]) ch[1 * 0xc8 + 190] = 1;
    if (started[1]) ch[3 * 0xc8 + 190] = 1;
    {   // diagnostic: which channels are active, and how much energy each accumulator carries
        static int shots = 0;
        if (shots < 8 && (gHwRouteMixes.load() % 300) == 0) { shots++;
            char fl[40]; for (int i = 0; i < 16; i++) fl[i] = save[i] ? '1' : '0'; fl[16] = 0;
            double ea = 0, eb = 0; for (uint32_t i = 0; i < n * 2; i++) { ea += (double)gHwAccA[i] * gHwAccA[i]; eb += (double)acc[i] * acc[i]; }
            char st[400]; int m = 0;
            for (int i = 0; i < 6; i++) { uint8_t* rc = ch + i * 0xc8; uint32_t* rp = *reinterpret_cast<uint32_t**>(rc + 152);
                m += snprintf(st + m, sizeof(st) - m, " ch%d[bc..c1]=%02x%02x%02x%02x%02x%02x cnt=%08x", i, rc[188], rc[189], rc[190], rc[191], rc[192], rc[193], rp ? *rp : 0u); }
            ALOGI("DrasticRunner: HWROUTE act=%s selL=%d selR=%d n=%u rmsA=%.0f rmsB=%.0f%s", fl, selL, selR, n, sqrt(ea / (n * 2)) / 4096.0, sqrt(eb / (n * 2)) / 4096.0, st); }
    }
    // Anti-alias the capture source (persist.gammaos.drastic_nano.hw_route_aa, default on): A is a 44.1 kHz
    // mix (with the source channels' own nearest-neighbour imaging up to 22 kHz); the ring is 32.7 kHz.
    // Hardware captures a mix that never had anything above 16.4 kHz. Decimating A into the ring without a
    // filter folds 16.4-22 kHz back into 10-16 kHz (measured +6 dB at 8-12 kHz vs melonDS). Same elliptic
    // 16 kHz design as the output lowpass; state kept per accumulator channel across mixes.
    {
        static int sAA = -1; if (sAA < 0) sAA = property_get_bool("persist.gammaos.drastic_nano.hw_route_aa", false) ? 1 : 0;   // refuted offline: unfiltered decimation matches the reference
        if (sAA) {
            static double z[2][4][2] = {};
            static const double kSos[4][6] = {
                {0.117591635888, 0.231334590813, 0.117591635888, 1, -0.044855136197, 0.118892690289},
                {1, 1.78076605039, 1, 1, 0.683890483871, 0.561995737842},
                {1, 1.61010735757, 1, 1, 1.11201121842, 0.828723891871},
                {1, 1.52945492835, 1, 1, 1.28681505201, 0.955182739885},
            };
            for (uint32_t i = 0; i < n; i++) for (int c = 0; c < 2; c++) {
                double x = gHwAccA[i * 2 + c];
                for (int k = 0; k < 4; k++) { const double* co = kSos[k]; double* zz = z[c][k]; const double y = co[0] * x + zz[0]; zz[0] = co[1] * x - co[4] * y + zz[1]; zz[1] = co[2] * x - co[5] * y; x = y; }
                gHwAccA[i * 2 + c] = (int32_t)lrint(x);
            }
        }
    }
    // Capture: write A into the rings using each unit's own 32.32 position and step (DraStic's rate
    // conversion), clamped >>12 like the mixer's own output conversion. PCM16 and PCM8, loop mode.
    // Ordering: on hardware the capture writes a slot first and the game's ARM9 then ADDS its software
    // voice into that slot before ch1/3 read it. Here the game has already run for the whole frame
    // before this mix, so its add is already in the ring; overwriting the slot would erase it. Keep a
    // shadow of what capture wrote last time: the game's contribution is ring - shadow, and the new
    // slot value is fresh_capture + (ring - shadow). The shadow is per unit, sized to the ring.
    static int16_t* shadow[2] = {nullptr, nullptr}; static uint32_t shadowLen[2] = {0, 0};
    for (int u = 0; u < 2; u++) {
        uint8_t* rec = spu + 0x40ca8 + u * 32;
        const uint8_t c = rec[0x1c];
        if (!(c & 0x80)) continue;
        uint64_t pos = *reinterpret_cast<uint64_t*>(rec + 0x00);
        const uint64_t step = *reinterpret_cast<uint64_t*>(rec + 0x08);
        uint8_t* dst = *reinterpret_cast<uint8_t**>(rec + 0x10);
        const uint32_t len = *reinterpret_cast<uint32_t*>(rec + 0x18);
        if (!dst || len == 0 || len > 65536) continue;
        const bool pcm8 = (c & 0x08) != 0, oneshot = (c & 0x04) != 0;
        if (shadowLen[u] != len) { free(shadow[u]); shadow[u] = static_cast<int16_t*>(calloc(len, sizeof(int16_t))); shadowLen[u] = shadow[u] ? len : 0; }
        int16_t* sh = shadow[u];
        static int sShift = -1; if (sShift < 0) sShift = property_get_int32("persist.gammaos.drastic_nano.hw_route_capshift", 12);   // capture scale: acc >> shift
        static int sCapMode = -1; if (sCapMode < 0) sCapMode = property_get_int32("persist.gammaos.drastic_nano.hw_route_cap", 0);   // 0 = overwrite (default, best), 1 = shadow-add, 3 = slot-walk lerp
        if (sCapMode == 0 || sCapMode == 3) sh = nullptr;
        if (sCapMode == 3 && !pcm8) {
            // Walk the ring slots this mix covers (pos .. pos + step*n) and sample the 44.1 kHz source A at the
            // fractional output index each slot corresponds to (linear). The old loop dropped ~26% of A's
            // samples and placed the rest by sample-and-hold on the ring grid, which smeared 8-16 kHz.
            int16_t* d16 = reinterpret_cast<int16_t*>(dst);
            const uint64_t endpos = pos + step * (uint64_t)n;
            const double inv = 4294967296.0 / (double)step;           // output samples per ring slot
            uint32_t k = (uint32_t)(pos >> 32) + 1;                   // first slot whose boundary lies inside this mix
            for (uint64_t sp_ = ((uint64_t)k << 32); sp_ <= endpos; sp_ += (1ull << 32), k++) {
                const double t = (double)(sp_ - pos) / 4294967296.0 * inv;   // fractional output index in [0, n)
                uint32_t i0 = (uint32_t)t; double fr = t - i0; if (i0 >= n - 1) { i0 = n - 1; fr = 0; }
                const double v0 = gHwAccA[i0 * 2 + u], v1 = gHwAccA[(i0 + (fr > 0 ? 1 : 0)) * 2 + u];
                int32_t v = (int32_t)lrint((v0 + (v1 - v0) * fr) / (double)(1 << sShift));
                if (v > 32767) v = 32767; else if (v < -32768) v = -32768;
                uint32_t idx = k % len; d16[idx] = (int16_t)v;
            }
            pos = endpos; while ((uint32_t)(pos >> 32) >= len) pos -= (uint64_t)len << 32;
            *reinterpret_cast<uint64_t*>(rec + 0x00) = pos;
            gHwRouteCapt.fetch_add(n, std::memory_order_relaxed);
            continue;
        }
        // Phase-lock the capture to its channel (unit 0 <-> ch1, unit 1 <-> ch3). Hardware clocks both from the same
        // timer reload and the game starts the channel an integer number of ticks after the capture, so the channel's
        // fractional phase at any slot equals the capture's phase when it wrote that slot: the channel's double-advance
        // (skip) lands exactly on the capture's double-write (dup) and the ring path is a pure delay. DraStic starts
        // both at block boundaries with independent phases (lead exactly 506 slots, not a multiple of the 1.002891
        // step), so the skip never meets the dup: a +/-1 sample delay sawtooth at (step-1)*mixrate = 94.6 Hz that put
        // sidebands 5 dB below every HF line (measured: every native spur sat exactly 94.6 Hz under a real line).
        // Set cap = ch + D*step for the integer D nearest the current lead; the lead moves by under half a slot, which
        // the game's half-ring logic cannot see.
        {
            const int cch = u ? 3 : 1; uint8_t* crec = ch + cch * 0xc8;
            const uint64_t cpos = *reinterpret_cast<uint64_t*>(crec + 128), cstep = *reinterpret_cast<uint64_t*>(crec + 136);
            // Lock mode 1: keep the current lead, only align the fractional phase. Mode 2 (default): also set the lead
            // itself to len-k slots, the hardware relation. Measured in melonDS (ringctx log, every frame): the game's
            // ARM9 half-ring burst starts when the capture is at slot 365 and cap-ch1 is always 3 in FIFO position
            // terms; the channel's 16-byte prefetch runs ahead of the capture FIFO's flush, so playback reads a slot
            // just BEFORE the capture overwrites it (the previous revolution, already processed by the ARM9). DraStic
            // (direct memory, no FIFOs) started the capture 176 slots late relative to ch1 (lead 506 instead of ~670),
            // so the game processed 150 of every 340 slots before they were captured and the capture then erased its
            // work: the voice defect. Reader k slots ahead of the writer reproduces the hardware order.
            static int sLock = -1; if (sLock < 0) sLock = property_get_int32("persist.gammaos.drastic_nano.hw_route_lock", 2);
            static int sLeadK = -1; if (sLeadK < 0) sLeadK = property_get_int32("persist.gammaos.drastic_nano.hw_route_lead_k", 10);
            const uint64_t sdiff = cstep > step ? cstep - step : step - cstep;
            const bool lockOk = sLock && sdiff < (1ull << 20) && step && (crec[190] || started[u]) && (uint32_t)(cpos >> 32) < len;
            static uint32_t seen[2] = {0, 0}; const bool periodic = (seen[u]++ % 2000) == 0 && seen[u] < 40000;
            if (lockOk) {
                // The B pass has already advanced the channel by n samples for this block while the capture has not
                // moved yet: measure and set the lead at the block START (channel position minus n*step).
                const uint64_t ring = (uint64_t)len << 32, cp = (cpos % ring + ring - ((uint64_t)n * step) % ring) % ring;
                const uint64_t lead = (pos + ring - cp) % ring;
                const uint64_t target = (sLock == 2 && (uint32_t)sLeadK < len) ? ((uint64_t)(len - (uint32_t)sLeadK) << 32) : lead;
                const uint64_t D = (target + step / 2) / step;
                const uint64_t want = (cp + D * step) % ring;
                const int64_t resid = (int64_t)(pos - want);
                if (periodic) ALOGI("DrasticRunner: HWROUTE cap%d lock-check: lead %.5f D=%llu resid=%lld (%.5f slot) ch190=%d started=%d", u, lead / 4294967296.0, (unsigned long long)D, (long long)resid, resid / 4294967296.0, crec[190], started[u]);
                if (want != pos) {
                    static uint32_t logged = 0;
                    if (logged++ < 40) ALOGI("DrasticRunner: HWROUTE cap%d phase-lock: lead %.5f -> %.5f slots (D=%llu, step %.6f, resid %lld)", u, lead / 4294967296.0, (D * step % ring) / 4294967296.0, (unsigned long long)D, step / 4294967296.0, (long long)resid);
                    pos = want;
                }
            } else if (periodic) ALOGI("DrasticRunner: HWROUTE cap%d lock-check SKIPPED: cstep=%#llx step=%#llx ch190=%d started=%d cpos=%#llx pos=%#llx len=%u", u, (unsigned long long)cstep, (unsigned long long)step, crec[190], started[u], (unsigned long long)cpos, (unsigned long long)pos, len);
        }
        // The capture timer never leaves a slot unwritten: when the unit's rate exceeds the mix rate (step > 1, e.g. a
        // 32823.6 Hz SOUND1TMR against a 32729 Hz native mix) hardware overflows the timer twice on one tick and writes
        // the same sample into both slots. Mirror that: every slot from the next expected one up to this one gets v.
        // Without it a slot every 1/(step-1) samples kept the previous revolution's 20 ms-old value (the one-sample
        // dips measured in the ring and heard as ticks under native_mix). The expected slot carries across blocks.
        static int32_t sNext[2] = {-1, -1};
        int32_t nextSlot = sNext[u];
        if (nextSlot >= (int32_t)len) nextSlot = -1;
        int16_t* d16 = reinterpret_cast<int16_t*>(dst);
        for (uint32_t i = 0; i < n; i++) {
            int32_t v = gHwAccA[i * 2 + u] >> sShift;
            if (v > 32767) v = 32767; else if (v < -32768) v = -32768;
            const uint32_t idx = (uint32_t)(pos >> 32);
            if (idx < len) {
                if (!pcm8 && !sh && nextSlot >= 0 && (int32_t)idx > nextSlot) for (uint32_t j = (uint32_t)nextSlot; j < idx; j++) d16[j] = (int16_t)v;   // skipped slots
                if (pcm8) {
                    const int32_t cur = (int8_t)dst[idx], old = sh ? (int8_t)(sh[idx] >> 8) : 0;
                    int32_t nv = (v >> 8) + (cur - old); if (nv > 127) nv = 127; else if (nv < -128) nv = -128;
                    dst[idx] = (uint8_t)(int8_t)nv; if (sh) sh[idx] = (int16_t)((v >> 8) << 8);
                } else {
                    const int32_t cur = d16[idx], old = sh ? sh[idx] : 0;
                    int32_t nv = v;
                    if (sh && sCapMode == 2) { const int32_t voice = cur - old; if (voice > -24000 && voice < 24000) nv = v + voice; }
                    else if (sh) nv = v + (cur - old);
                    if (nv > 32767) nv = 32767; else if (nv < -32768) nv = -32768;
                    d16[idx] = (int16_t)nv; if (sh) sh[idx] = (int16_t)v;
                }
                nextSlot = (int32_t)idx + 1;
            }
            pos += step;
            if ((uint32_t)(pos >> 32) >= len) {
                if (oneshot) { rec[0x1c] = c & 0x7f; break; }
                if (!pcm8 && !sh && nextSlot >= 0) for (uint32_t j = (uint32_t)nextSlot; j < len; j++) d16[j] = (int16_t)v;   // skip across the wrap
                pos -= (uint64_t)len << 32; nextSlot = 0;
            }
        }
        sNext[u] = nextSlot;
        *reinterpret_cast<uint64_t*>(rec + 0x00) = pos;
        gHwRouteCapt.fetch_add(n, std::memory_order_relaxed);
    }
    if (gSpuTrace) {   // (diagnostic) dump both accumulators: A = capture source, B = ch1/ch3 output (pre >>12)
        static FILE* bf = nullptr; static FILE* af = nullptr; static int bi = 0;
        if (!bi) { bi = 1; bf = fopen("/data/local/tmp/spu_accB.bin", "wb"); af = fopen("/data/local/tmp/spu_accA.bin", "wb"); }
        uint32_t m = n < 512 ? n : 512;
        if (bf) { fwrite(&m, 4, 1, bf); fwrite(acc, sizeof(int32_t), m * 2, bf); }
        if (af) { fwrite(&m, 4, 1, af); fwrite(gHwAccA, sizeof(int32_t), m * 2, af); }
    }
    gCubicActive = 0;                                                     // routing done for this mix; inert games never set it, so cubic stays off for them
    gHwRouteMixes.fetch_add(1, std::memory_order_relaxed);
    return 1;
}
// Pre: snapshot the real ring, then bridge every internal zero-run by linear interpolation so the
// channel-mix that follows never reads a cleared-but-unrefilled zero. Visible only for that read.
extern "C" void spuRingRepairPre(uint8_t* master) {
    uint8_t* spu = master + 0x158c000;
    gRingRepairCalls.fetch_add(1, std::memory_order_relaxed);
    static int sHw = -1;
    if (sHw < 0) sHw = property_get_bool("persist.gammaos.drastic_nano.hw_route", true) && !property_get_bool("persist.gammaos.drastic_nano.hw_route_repair", false) ? 1 : 0;
    if (sHw) { gRingSaveBytes[0] = gRingSaveBytes[1] = 0; return; }   // the rings hold real captured audio now
    for (int u = 0; u < 2; u++) {
        gRingSaveBytes[u] = 0;
        uint8_t* p; uint32_t L; int w;
        if (!ringRepairTarget(spu, u, &p, &L, &w)) continue;
        const uint32_t bytes = L * (uint32_t)w;
        memcpy(gRingSave[u], p, bytes); gRingSaveBytes[u] = bytes;
        // ring_mode: 0 = interpolate the gaps (the fix), 1 = silence the ring for this read only
        // (diagnostic: whatever still reaches the speaker did NOT come through channels 1/3).
        static int sRingMode = -1;
        if (sRingMode < 0) sRingMode = property_get_int32("persist.gammaos.drastic_nano.ring_mode", 0);
        if (sRingMode == 1) { memset(p, 0, bytes); continue; }
        const uint32_t filled = (w == 2) ? ringInterpFill(reinterpret_cast<int16_t*>(p), L) : ringInterpFill(reinterpret_cast<int8_t*>(p), L);
        if (filled) gRingRepairFilled.fetch_add(filled, std::memory_order_relaxed);
    }
}
// Post: put the real (gapped) ring back so the capture units still clear/write it and the game's ARM9
// feedback never accumulates our bridged values; an in-place fill alone runs the echo away.
extern "C" void spuRingRepairPost(uint8_t* master) {
    uint8_t* spu = master + 0x158c000;
    for (int u = 0; u < 2; u++) {
        if (!gRingSaveBytes[u]) continue;
        uint8_t* p = *reinterpret_cast<uint8_t**>(spu + 0x40cb8 + u * 32);
        if (p) memcpy(p, gRingSave[u], gRingSaveBytes[u]);
        gRingSaveBytes[u] = 0;
    }
}
static uint8_t* gSpuTraceBase = nullptr;
static uint8_t* gFetchLog = nullptr;
static uint8_t* gWrapLog = nullptr;
static uint8_t* gRefillLog = nullptr;
static int gNativeMix = 0;
static std::atomic<uint32_t> gNativeMinIn{0xffffffffu}, gNativeMaxIn{0}, gNativeOdd{0}, gNativeShort{0};   // chunk-size stats for the NATIVEMIX line
static std::atomic<uint32_t> gNativeIn{0}, gNativeOut{0};
static volatile uint32_t gAdpcmWraps = 0;
// Compact long-run trace: capture state and a few ring samples per mix, for the whole session.
struct SpuMini { uint32_t cycles, cap, cnt; int16_t sig[8]; };
static SpuMini* gSpuMini = nullptr;
static uint32_t gSpuMiniN = 0;
static const uint32_t kSpuMiniCap = 120000;
extern "C" void spuMixTrace(uint8_t* master) {
    if (!gSpuTrace) return;
    if (gSpuMini && gSpuMiniN < kSpuMiniCap) {
        SpuMini& m = gSpuMini[gSpuMiniN++];
        uint8_t* sp = master + 0x158c000;
        m.cycles = *reinterpret_cast<uint32_t*>(master + 8);
        m.cap = (uint32_t)(*reinterpret_cast<uint64_t*>(sp + 0x40ca8) >> 32);
        m.cnt = sp[0x40cc4] | (sp[0x40cc4 + 32] << 8);
        uint8_t* d = *reinterpret_cast<uint8_t**>(sp + 0x40cb8);
        uint32_t len = *reinterpret_cast<uint32_t*>(sp + 0x40cc0);
        if ((sp[0x40cc4] & 0x80) && d && len == 680) {
            const int16_t* rg = reinterpret_cast<const int16_t*>(d);
            m.sig[0] = rg[0]; m.sig[1] = rg[1]; m.sig[2] = rg[338]; m.sig[3] = rg[339]; m.sig[4] = rg[340]; m.sig[5] = rg[341]; m.sig[6] = rg[678]; m.sig[7] = rg[679];
        } else memset(m.sig, 0, sizeof(m.sig));
    }
    static int listed = 0;
    if (listed < 4 && gSpuTraceBase) {   // the scheduler's event list: {countdown, handler, arg, next}
        listed++;
        char lg[512]; int n = 0;
        uint8_t* ev = *reinterpret_cast<uint8_t**>(master + 792);
        for (int k = 0; ev && k < 12 && n < (int)sizeof(lg) - 60; k++) {
            uint64_t h = *reinterpret_cast<uint64_t*>(ev + 8);
            n += snprintf(lg + n, sizeof(lg) - n, " [cnt=%u h=+0x%llx arg=%llx]", *reinterpret_cast<uint32_t*>(ev),
                          (unsigned long long)(h - (uint64_t)(uintptr_t)gSpuTraceBase), (unsigned long long)*reinterpret_cast<uint64_t*>(ev + 16));
            ev = *reinterpret_cast<uint8_t**>(ev + 24);
        }
        ALOGI("DrasticRunner: SPU trace events line=%u cyc=%u m0=%u m16=%u:%s", *reinterpret_cast<uint16_t*>(master + 20),
              *reinterpret_cast<uint32_t*>(master + 8), *reinterpret_cast<uint32_t*>(master), *reinterpret_cast<uint32_t*>(master + 16), lg);
    }
    SpuTraceRec& r = gSpuTrace[gSpuTraceN.fetch_add(1) % kSpuTraceCap];
    r.cycles = *reinterpret_cast<uint32_t*>(master + 8);
    r.widx = *reinterpret_cast<uint32_t*>(master + 0x15cc00c);
    r.line = *reinterpret_cast<uint16_t*>(master + 20);
    uint8_t* spu = master + 0x158c000;   // the SPU state the mixer hands to its channel and capture routines
    r.cnt = spu[0x40cc4] | (spu[0x40cc4 + 32] << 8);
    r.cap0 = *reinterpret_cast<uint64_t*>(spu + 0x40ca8);
    r.cap1 = *reinterpret_cast<uint64_t*>(spu + 0x40ca8 + 32);
    memcpy(r.spu, spu + 0x40000, sizeof(r.spu));
    for (int u = 0; u < 2; u++) {
        uint8_t* d = *reinterpret_cast<uint8_t**>(spu + 0x40cb8 + u * 32);
        uint32_t len = *reinterpret_cast<uint32_t*>(spu + 0x40cc0 + u * 32);
        int16_t* dst = u ? r.ring1 : r.ring0;
        if ((spu[0x40cc4 + u * 32] & 0x80) && d && len == 680) memcpy(dst, d, 1360); else memset(dst, 0, 1360);
    }
}
extern "C" void raAudioSubmitPost(uint8_t* ctx);
extern "C" void raAudioCallbackHook();
std::atomic<uint32_t> gEmuLostTicks{0}, gEmuCatchUps{0}, gEmuDebtDrops{0};   // tick accounting (see drasticVWait)
std::atomic<uint32_t> gEmuRenderCatchUps{0};   // catch-up frames run render-skipped (see drasticVWait)
// Stall diagnostic (see drasticVWait): a "stall" is a single emulated frame that ran past
// pace_catchup_max vblank periods. We bracket the emulation (return of drasticVWait to the
// next entry) and compare wall time against this thread's CPU time: wall >> cpu means the
// emulator thread was OFF-CPU (preempted/blocked by the scheduler, fixable with priority),
// wall ~= cpu means a genuinely heavy frame (fixable with catch-up / offload).
std::atomic<uint32_t> gStallOffCpu{0}, gStallOnCpu{0};   // classified stall counts
std::atomic<uint32_t> gStallMaxWallMs{0}, gStallMaxCpuMs{0};   // worst stall seen
uint8_t* gAudLibBase = nullptr;   // libdrastic base for the audio submit probe
uint8_t* gPaceBase = nullptr;     // libdrastic base captured by installVblankPacing (drasticVWait render-skip)
std::atomic<bool> gRaCatchUpSkip{false};   // a render-skip catch-up is in flight (guards the gEmuDurUs EMA)
void raHiddenRenderSkip(uint8_t* base, int mask);
void raHiddenRenderRestore(uint8_t* base);
void raDirtyPostLoad();
void raDirtyApplyWant();
void raDirtyOnRemap(void* addr, size_t len, int fd, off_t off);
void raRingAutoSave();   // emulator thread, on park: save the end-of-frame state into the ring
void raJoin3dWorker(uint8_t* base);
static void raLogGx(const char* when);
uint8_t* gRaGx = nullptr;      // 3D engine object: *(heapMaster + 0xfba78), back-pointer at gx+0x9a30
uint8_t* gRaVideoP = nullptr;  // video object: *(master' + 0xfba68); byte +0x8aaa0 = "this frame skipped" (frameskip)
// Hidden replay frames must not reach the panel. With zero-copy slots the
// shader samples the DS slots directly, so for the duration of a burst the
// emulator's slotArray[0..1] is pointed at a scratch pair and the dma-buf
// slots keep the last shown frame; restored before the shown frame runs.
// Applied on the emulator thread only (park / wake / parked op).
uint8_t* gRaScratchSlots = nullptr;
bool     gRaSlotsRedirected = false;
void raApplySlotRedirect();
// The limiter multiplies the clock by 3 and compares against a period of
// 50000 units (16666.67 us). A tick of 16667 us advances 50001 units, one more
// than the period, so exactly one frame runs per tick; the one-unit surplus
// per frame is absorbed by the limiter's own realignment every ~14 minutes.
constexpr uint64_t kPaceTickUs      = 16667;
std::atomic<int64_t> gLastVblankUs{0};
std::atomic<int64_t> gVblankPeriodUs{16667};
// Diagnostic ring of pacer tick times (us), dumped by the slot sampler.
int64_t gTickLog[2048][4]; std::atomic<uint32_t> gTickLogN{0};  // tick, last vblank, period, target
// Adaptive lead: how long before the expected vblank the emulator is
// ticked. Shrinks slowly while frames land on time, grows on a miss, so the
// emulated frame completes as late as the render and GPU allow.
std::atomic<int64_t>  gLeadUs{6500};
std::atomic<uint32_t> gMissCount{0};
std::atomic<int64_t>  gLeadHoldUntil{0};
std::atomic<int64_t>  gLeadCreepFloor{-6000};  // never creep below: last miss + 500 us
std::atomic<uint32_t> gSteadyFrames{0};        // fresh emulated frames consumed since the emulator last went quiet
std::atomic<int64_t>  gLastFrameUs{0};         // time of the last fresh emulated frame
std::atomic<bool>     gMarginOk{true};         // GPU finished >= comfy margin before the vblank (last 20 frames)
std::atomic<int64_t>  gProducerDoneUs{0};      // emulator frame completion (its limiter entering the vblank wait)
std::atomic<int64_t>  gLastTickUs{0};          // last pacer tick
std::atomic<int64_t>  gLeadFloorRelaxAt{0};    // vblank seq at which the floor relaxes

// Virtual clock continuity: when the lock is (re)enabled the virtual clock
// starts from the real clock's current value, so drastic's limiter never
// sees a jump backwards (which would leave it waiting for a deadline the
// virtual clock could not reach for minutes).
std::atomic<int64_t>  gVirtBaseUs{0};
std::atomic<uint32_t> gVirtBaseSeq{0};
uint32_t gT3dStats[8] = {0, 0, 0, 0, 0, 0, 0, 0};   // threaded 3D sync caves, see installThreaded3dSync
// Mode 4 state (t3dComposeHook): adaptive join for games that compose engine A in
// mid-frame chunks (Golden Sun, Dragon Ball Origins compose every scanline; Pokemon
// Black 2 ~130 chunks per frame with a 3D render that never fits in the vblank slack).
uint8_t* gT3dBase = nullptr;
struct T3dAdapt {
    bool lagMode = false;      // partial-path frames stay on drastic's one-frame pipeline
    bool frameFresh = false;   // this frame's first chunk published the fresh buffer
    int64_t emaWaitUs = 0;     // smoothed wait at the first chunk (join mode)
    uint32_t freeStreak = 0;   // consecutive frames whose worker was idle at the first chunk
    uint32_t wholeJoins = 0, firstFree = 0, firstWait = 0, firstSkip = 0, switches = 0;
    int64_t maxWaitUs = 0, sumWaitUs = 0;
    // Alternating-screen detection: games that draw the 3D scene on both screens toggle
    // the POWCNT1 display-swap bit (master+0x1b374 bit 15) every frame. For them a stale
    // 3D frame lands on the wrong screen, so they always wait for the fresh buffer; games
    // that keep the 3D on one screen never show the one-frame lag and may use the budget.
    uint16_t lastPow = 0; uint32_t altHist = 0; uint32_t toggles = 0; bool alternating = false;
    uint32_t capFrames = 0;    // frames whose display capture was armed at the first chunk (render+0x458836)
    uint32_t frames = 0;       // frames seen at the first chunk
    // Capture-based alternation: the 3D is captured every frame (render+0x458836) and the
    // OTHER engine displays VRAM (DISPCNT B display mode 2, master+0x1c070 bits 16..17),
    // i.e. the captured 3D is shown on the other screen a frame later (Dragon Ball
    // Origins). Pokemon Black 2 captures every frame too but shows the 3D on its own
    // engine, so the lag is not visible there.
    uint32_t capHist = 0, vramBHist = 0; uint32_t modeA = 0, modeB = 0;
} gT3d;
// Mode 5 (per-band pipeline) state. gT3dBands is written by the rasterizer band cave
// (+0x5ee64: bands completed in the in-flight target buffer, 32 hi-res lines each) and
// reset by the kick cave (+0x2c9c4) at scanline 214 when the next frame is queued.
alignas(8) volatile uint32_t gT3dBandMask = 0;   // bit b set when global band b is rendered+edge-fixed
int gT3dMode = 0;
struct T3dPipe {
    uint32_t chunks = 0, waited = 0, timeouts = 0, startWaits = 0, idleSkips = 0, fullWaits = 0;
    int64_t sumUs = 0, maxUs = 0, sumStartUs = 0;
} gT3dPipe;
static inline int64_t t3dNowUs() {
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000 + ts.tv_nsec / 1000;
}
extern "C" void t3dComposeHook(uint8_t* engA, unsigned first, unsigned last) {
    if (last > 191 || !gT3dBase) return;                       // vblank-issued compose
    uint8_t* render = engA - 0x2e78;
    uint8_t* master = *reinterpret_cast<uint8_t**>(render);
    uint8_t* video  = *reinterpret_cast<uint8_t**>(master + 0xfba68);
    if (*reinterpret_cast<uint32_t*>(video + 0x8aac0) == 0) return;   // threaded 3D not active
    if (video[0x8f42c] & 8) return;                                     // original join gate
    if (gT3dMode >= 5) {
        // Per-band pipeline for the multi-threaded rasterizer. Frame N's render was kicked
        // at scanline 214 of N-1; nth rasterizer threads render interleaved 32-line bands
        // out of order, each setting its bit in gT3dBandMask (band cave +0x5ee64) after it
        // has rendered and edge-fixed that band; the kick cave (+0x2c9c4) clears the mask.
        // The 3D line fetch reads the in-flight target buffer (csel patches), so a compose
        // chunk [first,last] only waits until every band it covers is set, or the worker is
        // idle. Normally the wait is zero and the 3D overlaps the CPU emulation with no lag.
        uint8_t* cfg = *reinterpret_cast<uint8_t**>(render + 8);
        const bool hires = *reinterpret_cast<uint32_t*>(cfg + 1184) != 0;
        volatile uint8_t* work = render + 0x34ec78;
        volatile uint8_t* busy = render + 0x34ec79;
        const int64_t t0 = t3dNowUs();
        gT3dPipe.chunks++;
        // Wait for the worker to take this frame's kick before trusting the target pointer.
        { bool w = false;
          while (*work) { w = true; if (t3dNowUs() - t0 > 40000) { gT3dPipe.timeouts++; break; } sched_yield(); }
          if (w) { gT3dPipe.startWaits++; gT3dPipe.sumStartUs += t3dNowUs() - t0; } }
        uint32_t need;
        const uint32_t ctl = *reinterpret_cast<uint32_t*>(render + 0x34eb40);
        if (!hires) { need = 0xffffffffu; gT3dPipe.fullWaits++; }   // lo-res rasterizer: no band mask, wait for idle
        else {
            const uint32_t bnd = last / 16;              // band holding DS line `last`
            uint32_t top = bnd;
            if ((ctl & 0x20) && (last % 16) == 15 && bnd < 11) top = bnd + 1;   // edge boundary needs the next band
            need = (top >= 31) ? 0xffffffffu : ((1u << (top + 1)) - 1u);        // bands 0..top
        }
        bool waited = false;
        while ((__atomic_load_n(&gT3dBandMask, __ATOMIC_ACQUIRE) & need) != need) {
            if (!*work && !*busy) { if (!waited) gT3dPipe.idleSkips++; break; }
            waited = true;
            if (t3dNowUs() - t0 > 40000) { gT3dPipe.timeouts++; break; }
            sched_yield();
        }
        if (waited) {
            const int64_t w = t3dNowUs() - t0;
            gT3dPipe.waited++; gT3dPipe.sumUs += w; if (w > gT3dPipe.maxUs) gT3dPipe.maxUs = w;
        }
        return;
    }
    if (first != 0) return;   // later chunks read whatever the first chunk left published
    auto join = reinterpret_cast<void (*)(void*)>(gT3dBase + 0x5f4b4);
    const bool busy = render[0x34ec79] != 0;
    if (last == 191) {        // whole-frame compose: fresh (mode 3 behaviour) unless the
                              // game is in lag mode, where every frame must stay one behind
                              // so a mix of whole-frame and chunked frames never lands the
                              // two 3D scenes of an alternating game on the same screen
        if (gT3d.lagMode) { gT3d.firstSkip++; gT3d.frameFresh = false; return; }
        gT3d.wholeJoins++;
        join(render + 0x1056c0);
        return;
    }
    // first chunk of an incrementally composed frame: refresh the alternation history
    {
        const uint16_t pow = *reinterpret_cast<uint16_t*>(master + 0x1b374);
        const bool tog = ((pow ^ gT3d.lastPow) & 0x8000) != 0;
        gT3d.lastPow = pow;
        gT3d.altHist = (gT3d.altHist << 1) | (tog ? 1u : 0u);
        if (tog) gT3d.toggles++;
        gT3d.frames++;
        const bool cap = render[0x458836] != 0;
        if (cap) gT3d.capFrames++;
        gT3d.modeA = (*reinterpret_cast<uint32_t*>(master + 0x1b070) >> 16) & 3;
        gT3d.modeB = (*reinterpret_cast<uint32_t*>(master + 0x1c070) >> 16) & 3;
        gT3d.capHist   = (gT3d.capHist   << 1) | (cap ? 1u : 0u);
        gT3d.vramBHist = (gT3d.vramBHist << 1) | (gT3d.modeB == 2 ? 1u : 0u);
        const bool swapAlt = __builtin_popcount(gT3d.altHist) >= 16;      // half of the last 32 frames
        const bool capAlt  = __builtin_popcount(gT3d.capHist) >= 16 && __builtin_popcount(gT3d.vramBHist) >= 16;
        gT3d.alternating = swapAlt || capAlt;
        if (gT3d.alternating && gT3d.lagMode) { gT3d.lagMode = false; gT3d.switches++; gT3d.emaWaitUs = 0; }
    }
    if (!busy) {
        gT3d.firstFree++;
        if (gT3d.lagMode) {
            if (++gT3d.freeStreak >= 30) { gT3d.lagMode = false; gT3d.switches++; gT3d.emaWaitUs = 0; }
            gT3d.frameFresh = false;   // stay consistent with the previous frames until we switch
            return;
        }
        join(render + 0x1056c0);
        gT3d.frameFresh = true;
        return;
    }
    gT3d.freeStreak = 0;
    if (gT3d.lagMode) { gT3d.firstSkip++; gT3d.frameFresh = false; return; }
    static int64_t sBudgetUs = 3000, sBudgetReadUs = 0;
    const int64_t t0 = t3dNowUs();
    if (t0 - sBudgetReadUs > 1000000) {
        sBudgetReadUs = t0;
        sBudgetUs = property_get_int32("sys.gammaos.drastic_nano.t3d_wait_budget_us", 3000);
    }
    join(render + 0x1056c0);
    const int64_t w = t3dNowUs() - t0;
    gT3d.firstWait++; gT3d.sumWaitUs += w; if (w > gT3d.maxWaitUs) gT3d.maxWaitUs = w;
    gT3d.emaWaitUs = (gT3d.emaWaitUs * 7 + w) / 8;
    gT3d.frameFresh = true;
    if (!gT3d.alternating && gT3d.emaWaitUs > sBudgetUs) { gT3d.lagMode = true; gT3d.switches++; }
}
std::atomic<bool>     gPaceBypass{false};   // emulator too slow for the lock: drastic's own timer
std::atomic<bool>     gAudioChunks8{false};  // OpenSL sink re-shaped to 8 x 33 ms chunks (audio_chunks_8; set at install)
static inline uint64_t realClockUs() {
    struct timeval tv; gettimeofday(&tv, nullptr);
    return (uint64_t)tv.tv_sec * 1000000ULL + (uint64_t)tv.tv_usec;
}
// Counts the emulator frame-limiter's clock reads. The limiter reads the clock a
// FIXED number of times per emulated frame (limiter reset + deadline check) in
// EVERY mode -- paced, unpaced/heavy, and fast-forward -- because it always runs
// once per frame even when it does not sleep. So this advances strictly with the
// emulation rate (unlike drasticVWait, which stops under fast-forward, and unlike
// the slot-flip counter, which frame-skips). Divide the per-second delta by the
// reads-per-frame to get emulated FPS.
std::atomic<uint32_t> gVTimeCount{0};
extern "C" void drasticVTime(uint64_t* out) {
    gVTimeCount.fetch_add(1, std::memory_order_relaxed);
    if (gPaceOn.load(std::memory_order_relaxed)) {
        const uint32_t seq = gVblSeq.load(std::memory_order_acquire);
        *out = (uint64_t)gVirtBaseUs.load() +
               (uint64_t)(seq - gVirtBaseSeq.load()) * kPaceTickUs;
        return;
    }
    *out = realClockUs();
}

// Emulated-frame counter. Bumped once per emulated frame by the cave installed on
// the frame-limiter call site (+0x2c99c) in installVblankPacing: that site is
// entered exactly once per emulated frame in every mode -- paced, unpaced/heavy,
// and fast-forward -- so gEmuFrames' per-second delta is the true emulation rate
// (60 at full speed, higher under fast-forward, and it DROPS when the emulator
// cannot keep up). Written only by the emulator thread (single writer).
std::atomic<uint32_t> gEmuFrames{0};

// Called in place of the per-frame slot flip (+0x1cb14 from +0x3d2bc):
// records the exact instant the emulated frame becomes visible to the
// consumer, then performs the original flip.
void (*gOrigSlotFlip)() = nullptr;
std::atomic<int64_t> gEmuDurUs{6000};   // running estimate of tick -> flip
std::atomic<int> gEmuCpuPct{0};          // emulator thread CPU share over the last second (percent of one core)
std::atomic<int> gEmuCpuLightSecs{0};    // consecutive seconds with a light emulator thread
std::atomic<uint32_t> gFlipHookCount{0};
std::mutex gFlipMu; std::condition_variable gFlipCv;
// Zero-copy slot swap, executed by the emulator thread inside the slot-flip
// hook (a frame boundary): copy the current slot contents into the dma-buf
// and repoint drastic's slotArray[0..1] at it.
std::atomic<uint8_t*> gZcNewBase{nullptr};
std::atomic<int> gZcSwapState{0};   // 0 idle, 1 requested, 2 done
static uint8_t* gZcBss = nullptr;

// Fast-forward frameskip repair. Under FF drastic's frame limiter marks
// most frames "skip" (about 6 of 7 at full speed); at the compose entry
// (+0x3c938) the skip flag (+0x3c9a0, ldrb w24,[x26]) drops both engine
// composes, but the display-capture block (+0x3cb40) still runs and
// captures the unrendered engine A output. Games that display their own
// capture then show garbage: Pokemon White 2's transition (a one-shot
// capture) turns into a gray field with bands, Golden Sun Dark Dawn
// (which renders 3D on one frame and shows the capture on the next,
// swapping the engines every frame via POWCNT1 bit 15) flickers black.
// The compose entry is routed through ffCapHook with DISPCAPCNT (live in
// w8 there, drastic saves it at +0x3c9b8 for the capture block) and the
// skip flag; the hook returns both:
//   no capture: the limiter's decision, untouched;
//   capture newly enabled (one-shot): render this frame;
//   continuous capture: our own skip pattern, rendered frames in pairs
//     aligned to the engine swap (one pair every ff_pair_period frames),
//     and on skipped frames the capture is bypassed (enable bit cleared
//     in the saved copy) so the capture VRAM keeps the last real image.
// Integer only: the cave saves x0-x15/x29/x30 but no vector registers.
uint8_t* gProbePage = nullptr;      // RWX page near the library (audio probe), caves at fixed offsets
static int gFfPairPeriod = 8;
static int gFfPairPhase = 0;    // POWCNT1 bit 15 value that starts a rendered pair
static int gFfSkipPeriod = 0;   // non-swapping games under FF: 0 = the limiter's cadence, -1 adaptive, N fixed
std::atomic<int> gFfOnForHook{0};
static int gLastPhase = 0;          // POWCNT1 bit 15 of the frame last seen by the hook
std::atomic<int> gLastRender{1};    // the hook rendered the frame last seen
std::atomic<int> gCapToggling{0};   // the game swaps the engines every frame (POWCNT1 bit 15)
// FF presentation staging: games that compose engine A per scanline (Golden Sun)
// keep writing the slot the GPU is sampling between flips, and under FF the
// skipped frames' chunks land there too (the displayed slot alternated between
// the two screens' images). While FF is on the flip hook copies the completed
// front slot into a double-buffered staging pair (slots 2/3 of the dma-buf)
// and the presenter samples the last published one. ~24 copies/s under FF.
// The staging pair lives in its OWN dma-buf: the per-vblank cache clean
// (DMA_BUF_IOCTL_SYNC) covers a whole buffer, and growing the live one
// from 3 to 6 MB cost ~10 fps at 1x. The staging buffer is only synced
// while FF is on.
std::atomic<int> gFfStageWant{0};
std::atomic<int> gFfStagePub{-1};
static int gFfStageNext = 0;
static int gFfStageFd = -1;
std::atomic<uint8_t*> gFfStageMap{nullptr};
extern "C" uint64_t ffCapHook(uint32_t cap, uint32_t flag, uint8_t* hm) {
    static bool prevOn = false, pending = false;
    static int prevPow = -1;
    static uint32_t pairs = 0;
    const bool capOn = (cap & 0x80000000u) != 0;
    const bool wasOn = prevOn; prevOn = capOn;
    if (!capOn) {
        // No capture: the limiter's decision. The publish state must follow
        // it too, or a 2D scene after a skipped capture frame would never be
        // published (Golden Sun's menus froze for seconds under FF).
        gLastRender.store(flag == 0 ? 1 : 0, std::memory_order_relaxed);
        gCapToggling.store(0, std::memory_order_relaxed);
        return ((uint64_t)cap << 32) | flag;
    }
    const uint8_t* io = *reinterpret_cast<uint8_t* const*>(hm);   // memory image, IO at +0x1b070
    const int pow15 = (*reinterpret_cast<const uint16_t*>(io + 0x1b374) >> 15) & 1;   // POWCNT1 swap bit
    const bool toggling = prevPow >= 0 && prevPow != pow15; prevPow = pow15;
    gCapToggling.store(toggling ? 1 : 0, std::memory_order_relaxed);
    // A frame the limiter wants rendered always renders (at 1x that is
    // every frame, so the hook is a passthrough); the pair pattern only
    // adds frames among the ones the limiter wanted to skip. When the
    // engines swap every frame a frame is only correct together with its
    // partner: a pair starts on the start phase (a limiter-rendered frame
    // included) and the other phase renders only as the partner.
    // Games that do not swap the engines keep the limiter's own cadence
    // (adding frames of our own made the presented motion step unevenly,
    // seen as judder in Pokemon White 2); only the capture bypass applies.
    bool render;
    if (!wasOn) { render = true; pending = false; }
    else if (!toggling) {
        // Under FF the limiter's own cadence (1 in 7, the fastest) is the
        // default; ff_skip_period selects a denser cadence for smoother
        // motion at a speed cost: -1 = adaptive (content rate near 30 a
        // second, a uniform 2 refresh hold), N = every Nth emulated frame.
        // At 1x the limiter renders every frame and its decision is kept.
        static uint32_t idx = 0, cnt = 0, per = 3; static int64_t secStart = 0;
        if (gFfOnForHook.load(std::memory_order_relaxed) && gFfSkipPeriod != 0) {
            struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
            const int64_t t = (int64_t)ts.tv_sec * 1000000 + ts.tv_nsec / 1000;
            cnt++;
            if (!secStart) secStart = t;
            else if (t - secStart >= 500000) {
                const uint32_t rate = (uint32_t)((int64_t)cnt * 1000000 / (t - secStart));
                if (gFfSkipPeriod > 0) per = (uint32_t)gFfSkipPeriod;
                else { per = (rate + 15) / 30; if (per < 2) per = 2; if (per > 7) per = 7; }   // adaptive
                secStart = t; cnt = 0;
            }
            render = (idx++ % per) == 0;
        } else { render = flag == 0; idx = 0; cnt = 0; secStart = 0; }
        pending = false;
    }
    else if (pow15 == gFfPairPhase) {
        // Engine-swapping games: the pair pattern alone sets the cadence
        // (the limiter's own frames would start extra pairs and cost a
        // third of the fast-forward speed).
        const uint32_t per = (uint32_t)gFfPairPeriod / 2;
        render = (pairs++ % (per ? per : 1)) == 0;
        pending = render;
    } else { render = pending; pending = false; }
    gLastPhase = pow15;
    // Keep the limiter's skip byte (heapMaster+0x8f42c: bit 3 no 3D kick,
    // bit 5 skip the frame end incl. the screen assignment and the slot
    // flip, bit 6 skip the compose) consistent with the decision, so the
    // rest of the frame-end path treats the frame the same way.
    uint8_t* skipByte = hm + 0x8f42c;
    gLastRender.store(render ? 1 : 0, std::memory_order_relaxed);
    if (render) *skipByte &= (uint8_t)~0x68u; else *skipByte |= 0x68u;
    if (render) return ((uint64_t)cap << 32);
    return ((uint64_t)(cap & 0x7fffffffu) << 32) | 1u;
}
extern "C" void drasticSlotFlipHook() {
    gFlipHookCount.fetch_add(1);
    if (gZcSwapState.load() == 1 && gZcBss) {
        uint8_t* nb = gZcNewBase.load();
        uint8_t** slots = reinterpret_cast<uint8_t**>(gZcBss);
        if (nb && slots[0] && slots[1]) {
            memcpy(nb, slots[0], 0x180000);
            memcpy(nb + 0x180000, slots[1], 0x180000);
            slots[0] = nb; slots[1] = nb + 0x180000;
            __sync_synchronize();
            gZcSwapState.store(2);
        } else {
            gZcSwapState.store(3);   // cannot swap
        }
    }
    const int64_t now = (int64_t)std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
    const bool hidden = gRaBurst.load(std::memory_order_acquire);
    const bool afterBurst = (int32_t)(gVblSeq.load() - gRaBurstUntilSeq.load()) < 0;
    gProducerDoneUs.store(now);
    const int64_t t = gLastTickUs.load();
    if (gPaceOn.load() && t > 0 && now - t > 0 && now - t < 30000 && !hidden && !afterBurst &&
        !gRaCatchUpSkip.load(std::memory_order_relaxed)) {
        const int64_t d = gEmuDurUs.load();
        gEmuDurUs.store((d * 7 + (now - t)) / 8);
    }
    if (gOrigSlotFlip) gOrigSlotFlip();
    // Hidden replay frames run back to back with no vblank slack, so the
    // next frame's SWAP could restart the GX command list while the 3D
    // worker (per-band pipeline) is still consuming it (crash in the FIFO
    // compaction at +0x63bc4: write reset to start, read mid-list). Join
    // the worker here, after the kick and compose, before the emulation of
    // the next hidden frame begins.
    if (hidden && gRaLibBase) raJoin3dWorker(gRaLibBase);
    if (!hidden && gZcBss) {
        const int32_t cur = *reinterpret_cast<int32_t*>(gZcBss + 0x958);
        const int front = ((~cur) & 1) ? 1 : 0;
        gRaShownFront.store(front);
        if (gFfStageWant.load(std::memory_order_relaxed) && gZcSwapState.load() == 2) {
            uint8_t* st = gFfStageMap.load(std::memory_order_acquire);
            uint8_t** sl = reinterpret_cast<uint8_t**>(gZcBss);
            // Publish only frames the hook rendered; for engine-swapping
            // games only the pair's second frame (the first composes with a
            // stale capture and drastic still flips on some skipped frames).
            const bool publishable = gLastRender.load(std::memory_order_relaxed) &&
                    (!gCapToggling.load(std::memory_order_relaxed) || gLastPhase != gFfPairPhase);
            if (st && sl[front] && publishable) {
                const int k = gFfStageNext & 1;
                memcpy(st + (size_t)k * 0x180000, sl[front], 0x180000);
                gFfStageNext ^= 1;
                gFfStagePub.store(2 + k, std::memory_order_release);
            }
        } else if (gFfStagePub.load(std::memory_order_relaxed) >= 0) {
            gFfStagePub.store(-1, std::memory_order_release);
        }
    }
    if (hidden) {
        // A replayed frame: not for the panel. Clear the ready mask the
        // producer just set and do not wake the presenter.
        if (gRaReadyMask) *gRaReadyMask = 0;
        return;
    }
    // Wake the consumer: it waits on this instead of polling the ready
    // mask (the polling cost ~100 context switches per frame).
    { std::lock_guard<std::mutex> lk(gFlipMu); }
    gFlipCv.notify_all();
}

// Counts emulated frames: drastic's frame limiter calls this once per emulated
// frame in EVERY mode (paced, unpaced/heavy, and fast-forward), before any
// render frame-skip, so a per-second delta is the true emulation rate (60 at
// full speed, ~120 at 2x fast-forward), unlike the slot-flip counter which
// tracks the frame-skipped render rate.
std::atomic<uint32_t> gVWaitCount{0};
std::atomic<int64_t> gLastParkUs{0};   // emulator entered the limiter wait (park)
static inline int64_t threadCpuUs() {
    struct timespec t;
    clock_gettime(CLOCK_THREAD_CPUTIME_ID, &t);
    return (int64_t)t.tv_sec * 1000000LL + t.tv_nsec / 1000;
}
// Emulation bracket for the stall diagnostic: markers set at every return of drasticVWait
// (when drastic resumes emulating) and read at the next entry, so the delta is the frame's
// emulation only, excluding the park wait. gEmuRunStart* / the diagnostic are emulator-thread only.
static int64_t sEmuRunStartUs = 0, sEmuRunStartCpu = 0;
static long sEmuRunNvcsw = 0, sEmuRunNivcsw = 0, sEmuRunMajflt = 0;
static int sStallDiag = 0;   // pace_stall_diag: gate the per-frame stall diagnostic (off = zero cost)
// Read this thread's voluntary/involuntary context switches and major faults. On an off-CPU
// stall these separate the cause: nivcsw = preempted (scheduler), nvcsw = blocked on a lock/IO,
// majflt = a major page fault (memory reclaim under pressure).
static inline void threadRu(long& nvcsw, long& nivcsw, long& majflt) {
    struct rusage ru; getrusage(RUSAGE_THREAD, &ru);
    nvcsw = ru.ru_nvcsw; nivcsw = ru.ru_nivcsw; majflt = ru.ru_majflt;
}
extern "C" void drasticVWait(unsigned usec) {
    gVWaitCount.fetch_add(1, std::memory_order_relaxed);
    const int64_t nowUs = (int64_t)std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
    gLastParkUs.store(nowUs);
    // Emulation just finished (since the last return): wall vs this thread's CPU time.
    // Gated behind pace_stall_diag (default off) so production pays nothing per frame.
    int64_t emuWallUs = 0, emuCpuUs = 0, cpuNowUs = 0;
    long nvNow = 0, nivNow = 0, mfNow = 0, dNv = 0, dNiv = 0, dMf = 0;
    if (sStallDiag) {
        cpuNowUs = threadCpuUs(); threadRu(nvNow, nivNow, mfNow);
        if (sEmuRunStartUs > 0) {
            emuWallUs = nowUs - sEmuRunStartUs; emuCpuUs = cpuNowUs - sEmuRunStartCpu;
            dNv = nvNow - sEmuRunNvcsw; dNiv = nivNow - sEmuRunNivcsw; dMf = mfNow - sEmuRunMajflt;
        }
    }
    raDirtyApplyWant();   // emulator thread at a frame boundary: dirty tracking follows run-ahead and the pacer lock
    if (!gPaceOn.load(std::memory_order_relaxed)) { sEmuRunStartUs = 0; usleep(usec); return; }
    std::unique_lock<std::mutex> lk(gPaceMu);
    const uint32_t seen = gVblSeq.load(std::memory_order_acquire);
    // Tick accounting. A frame that ran past its period saw the next tick
    // fire while it was still emulating; waiting for the tick after that
    // silently drops one emulated frame, and with it 16.7 ms of audio that
    // the output side still consumes (the buffer queue then starves: that is
    // the crackle on heavy scenes). With catch-up on, that tick is consumed
    // now and the frame runs back to back; a backlog of more than
    // pace_catchup_max ticks means the scene cannot keep 60 and is forgiven
    // (the bypass handles sustained overload).
    static uint32_t sConsumedSeq = 0; static bool sConsumedValid = false;
    static int sCatchUp = -1, sCatchUpMax = 2, sCatchCeil = 5; static int64_t sCatchUpReadUs = 0;
    static int sRenderSkip = 0, sCatchMask = 3;
    static bool sSkipActive = false;
    if (gLastParkUs.load() - sCatchUpReadUs > 1000000) {
        sCatchUpReadUs = gLastParkUs.load();
        sCatchUp = property_get_int32("persist.gammaos.drastic_nano.pace_catchup", 1);
        sCatchUpMax = property_get_int32("persist.gammaos.drastic_nano.pace_catchup_max", 2);
        // Full-render catch-up ceiling: an isolated stall this many ticks or fewer is
        // caught up (owed frames run back to back), so its audio is produced and the
        // output queue refills instead of starving (the heavy-scene pop). The picture
        // stays fully composed on every catch-up frame, both DS panels included.
        // Catch small isolated gameplay hiccups (behind 3..ceiling) but leave a giant
        // stall (a state load is behind 8..10 and ~140 ms of CPU) to the drop path: its
        // audio is silence during the load anyway, and a slow full-render catch-up of a
        // backlog that big drains the output queue far worse than an instant resync.
        sCatchCeil = property_get_int32("persist.gammaos.drastic_nano.pace_catchup_ceiling", 5);
        // Render-skip the extension frames (compose + 3D kick): cheaper catch-up, but it
        // leaves a STATIC panel (the DS bottom screen) un-redrawn and blank, so it is OFF
        // by default. Only for experiments.
        sRenderSkip = property_get_int32("persist.gammaos.drastic_nano.pace_render_catchup", 0);
        sCatchMask = property_get_int32("persist.gammaos.drastic_nano.pace_catchup_mask", 3);
        sStallDiag = property_get_int32("persist.gammaos.drastic_nano.pace_stall_diag", 0);
    }
    if (sConsumedValid) {
        const int32_t behind = (int32_t)(seen - sConsumedSeq);   // ticks that fired during the frame
        if (behind > 0) {
            gEmuLostTicks.fetch_add((uint32_t)behind, std::memory_order_relaxed);
            const bool eligible = sCatchUp > 0 && !gStepMode.load(std::memory_order_relaxed) &&
                gRaMode.load(std::memory_order_relaxed) != 2 && gRaParkOp.load(std::memory_order_acquire) == 0;
            // Stall diagnostic: this frame ran past the small window. Classify it by
            // comparing its emulation wall time against this thread's CPU time.
            if (sStallDiag && behind > sCatchUpMax && emuWallUs > 0) {
                const bool offCpu = emuCpuUs * 3 < emuWallUs * 2;   // used < 2/3 of the wall on CPU
                (offCpu ? gStallOffCpu : gStallOnCpu).fetch_add(1, std::memory_order_relaxed);
                const uint32_t wms = (uint32_t)(emuWallUs / 1000), cms = (uint32_t)(emuCpuUs / 1000);
                uint32_t pw = gStallMaxWallMs.load(std::memory_order_relaxed);
                if (wms > pw) gStallMaxWallMs.store(wms, std::memory_order_relaxed);
                uint32_t pc = gStallMaxCpuMs.load(std::memory_order_relaxed);
                if (cms > pc) gStallMaxCpuMs.store(cms, std::memory_order_relaxed);
                static int64_t sLastStallLogUs = 0;
                const int stallLogGap = property_get_int32("persist.gammaos.drastic_nano.pace_stall_log_us", 1000000);
                if (nowUs - sLastStallLogUs > stallLogGap) {
                    sLastStallLogUs = nowUs;
                    ALOGW("PACE stall behind=%d wall=%u ms cpu=%u ms nvcsw=%ld nivcsw=%ld majflt=%ld %s",
                          behind, wms, cms, dNv, dNiv, dMf,
                          offCpu ? "OFF-CPU(sched)" : "ON-CPU(heavy)");
                }
            }
            if (eligible) {
                // Catch up (owed frame runs now, no park) while the backlog fits the
                // ceiling. Full render by default; render-skip only as an experiment.
                const bool small = behind <= sCatchUpMax;
                if (small || behind <= sCatchCeil) {
                    if (!small && sRenderSkip > 0 && !sSkipActive && gPaceBase) {
                        raHiddenRenderSkip(gPaceBase, sCatchMask);
                        sSkipActive = true;
                        gRaCatchUpSkip.store(true, std::memory_order_release);
                    }
                    sConsumedSeq++;
                    gEmuCatchUps.fetch_add(1, std::memory_order_relaxed);
                    if (sSkipActive) gEmuRenderCatchUps.fetch_add(1, std::memory_order_relaxed);
                    if (sStallDiag) {   // next frame's emulation starts now: reset the bracket
                        sEmuRunStartUs = nowUs; sEmuRunStartCpu = cpuNowUs;
                        sEmuRunNvcsw = nvNow; sEmuRunNivcsw = nivNow; sEmuRunMajflt = mfNow;
                    }
                    raFlushDeferredUnmaps();
                    return;   // run the next frame now: no park, no wait
                }
                gEmuDebtDrops.fetch_add((uint32_t)behind, std::memory_order_relaxed);
            }
        }
    }
    // Caught up (or catch-up not eligible): if the picture was render-skipped for
    // a catch-up run, restore it now, before this frame parks, so the next shown
    // frame composes normally.
    if (sSkipActive) {
        if (gPaceBase) raHiddenRenderRestore(gPaceBase);
        sSkipActive = false;
        gRaCatchUpSkip.store(false, std::memory_order_release);
    }
    raFlushDeferredUnmaps();
    // Run-ahead: the ring save happens here, in the idle slack before the
    // next tick, at the same frame boundary the burst loads use.
    if (gRaMode.load(std::memory_order_relaxed) == 2) { raRingAutoSave(); raApplySlotRedirect(); }
    { std::lock_guard<std::mutex> pk(gParkMu); gEmuParked.store(true); }
    gParkCv.notify_all();
    if (gRaMode.load(std::memory_order_relaxed) == 2 && !gRaBurst.load(std::memory_order_relaxed)) {
        // Wake the pacer: an input change that arrived during the frame can
        // start its replay burst now, at the frame boundary, instead of at
        // the tick (where it would always push the shown frame past its vblank).
        { std::lock_guard<std::mutex> lk(gRaPokeMu); gRaInputPoke.store(true); }
        gRaPokeCv.notify_all();
    }
    auto ticked = [&] { return gVblSeq.load(std::memory_order_acquire) != seen ||
                               !gPaceOn.load(std::memory_order_relaxed); };
    auto pending = [&] { return ticked() || gRaParkOp.load(std::memory_order_acquire) != 0; };
    if (gRaParkOp.load(std::memory_order_acquire)) raRunParkedOp();
    // Step mode: never free-run; wait for a tick however long it takes.
    while (gStepMode.load(std::memory_order_relaxed) && !ticked()) {
        gPaceCv.wait_for(lk, std::chrono::milliseconds(50));
        if (gRaParkOp.load(std::memory_order_acquire)) raRunParkedOp();
    }
    while (!ticked()) {
        if (gPaceCv.wait_for(lk, std::chrono::milliseconds(50), pending) == false) break;   // free-run timeout
        if (gRaParkOp.load(std::memory_order_acquire)) raRunParkedOp();
    }
    if (gRaMode.load(std::memory_order_relaxed) == 2 || gRaSlotsRedirected) raApplySlotRedirect();
    // The tick this frame consumes is the first one after the park; any
    // further ticks that arrived during the wait stay owed (see above).
    if (gVblSeq.load(std::memory_order_acquire) != seen) { sConsumedSeq = seen + 1; sConsumedValid = true; }
    else sConsumedValid = false;   // free-run timeout or pacing switched off: resync on the next tick
    gEmuParked.store(false);
    // The park is over; the next frame's emulation begins now. Bracket it for the
    // stall diagnostic (wall vs thread CPU), excluding the wait we just did.
    if (sStallDiag) {
        sEmuRunStartUs = (int64_t)std::chrono::duration_cast<std::chrono::microseconds>(
                std::chrono::steady_clock::now().time_since_epoch()).count();
        sEmuRunStartCpu = threadCpuUs();
        threadRu(sEmuRunNvcsw, sEmuRunNivcsw, sEmuRunMajflt);
    }
}
} // namespace

void DrasticRunner::installVblankPacing(uint8_t* base) {
    if (!base || mPanelHz <= 1.0) return;
    if (!property_get_bool("persist.gammaos.drastic_nano.vblank_pace", true)) return;
    const long ps = sysconf(_SC_PAGESIZE) > 0 ? sysconf(_SC_PAGESIZE) : 4096;
    // 1. OpenSL PCM sample rate (rodata, milliHz): both format tables. With native_mix the SPU mixes at 32824 Hz and
    // hands 547 stereo frames per video frame; open the player at 32824 too so the frames go out untouched and
    // AudioFlinger does the 32824->48000 conversion with its own (off-thread, optimised) resampler. This is what lets
    // native_mix avoid a per-sample software resampler on the emulation thread, which starved the OpenSL queue.
    const bool nativeMixRate = property_get_bool("persist.gammaos.drastic_nano.native_mix", false);
    const uint32_t rate = (uint32_t)llround((nativeMixRate ? 32824000.0 : 44100000.0) * mPanelHz / 60.0);
    static const uintptr_t kRateOffs[2] = { 0x10a08c, 0x10a0c0 };
    for (uintptr_t off : kRateOffs) {
        uint32_t* p = reinterpret_cast<uint32_t*>(base + off);
        if (*p != 44100000u) {
            ALOGW("DrasticRunner: vblank pacing: rate constant at +0x%lx is %u, "
                  "not 44100000; leaving pacing off", (unsigned long)off, *p);
            return;
        }
    }
    for (uintptr_t off : kRateOffs) {
        uint8_t* pg = (uint8_t*)((uintptr_t)(base + off) & ~(uintptr_t)(ps - 1));
        if (mprotect(pg, ps, PROT_READ | PROT_WRITE) != 0) {
            ALOGW("DrasticRunner: vblank pacing: mprotect(rate) failed: %s", strerror(errno));
            return;
        }
        *reinterpret_cast<uint32_t*>(base + off) = rate;
        mprotect(pg, ps, PROT_READ);
    }
    // 2. Trampolines in the library's RX padding (the NT patch uses +0x132c00).
    struct Site { uintptr_t off; uint32_t expect; };
    static const Site kTimeSites[2] = { {0x1b76c, 0x97fffec0u}, {0x1b814, 0x97fffe96u} };
    static const Site kWaitSites[2] = { {0x1b934, 0x97fffe76u}, {0x1b98c, 0x97fffe60u} };
    static const Site kFlipSite = { 0x3d2bc, 0x97ff7e16u };   // bl +0x1cb14 (per-frame slot flip)
    for (const Site* tab : { kTimeSites, kWaitSites })
        for (int i = 0; i < 2; i++)
            if (*reinterpret_cast<uint32_t*>(base + tab[i].off) != tab[i].expect) {
                ALOGW("DrasticRunner: vblank pacing: unexpected code at +0x%lx, "
                      "leaving pacing off", (unsigned long)tab[i].off);
                return;
            }
    if (*reinterpret_cast<uint32_t*>(base + kFlipSite.off) != kFlipSite.expect) {
        ALOGW("DrasticRunner: vblank pacing: unexpected code at +0x%lx, leaving pacing off",
              (unsigned long)kFlipSite.off);
        return;
    }
    const uintptr_t kCaveTime = 0x132c40, kCaveWait = 0x132c60, kCaveFlip = 0x132c80,
                    kCaveLim = 0x132e60;   // free RX padding after the threaded3d caves (end ~0x132e58)
    uint8_t* cavePg = (uint8_t*)((uintptr_t)(base + kCaveTime) & ~(uintptr_t)(ps - 1));
    uint8_t* sitePg = (uint8_t*)((uintptr_t)(base + 0x1b76c) & ~(uintptr_t)(ps - 1));
    uint8_t* flipPg = (uint8_t*)((uintptr_t)(base + kFlipSite.off) & ~(uintptr_t)(ps - 1));
    if (mprotect(cavePg, ps, PROT_READ | PROT_WRITE | PROT_EXEC) != 0 ||
        mprotect(sitePg, ps, PROT_READ | PROT_WRITE | PROT_EXEC) != 0 ||
        mprotect(flipPg, ps, PROT_READ | PROT_WRITE | PROT_EXEC) != 0) {
        ALOGW("DrasticRunner: vblank pacing: mprotect(code) failed: %s", strerror(errno));
        return;
    }
    auto writeCave = [&](uintptr_t off, void* target) {
        uint32_t* c = reinterpret_cast<uint32_t*>(base + off);
        c[0] = 0x58000050u;              // ldr x16, [pc, #8]
        c[1] = 0xd61f0200u;              // br  x16
        uint64_t addr = (uint64_t)(uintptr_t)target;
        memcpy(&c[2], &addr, 8);
    };
    writeCave(kCaveTime, (void*)&drasticVTime);
    writeCave(kCaveWait, (void*)&drasticVWait);
    gPaceBase = base;   // drasticVWait uses this for the render-skip catch-up
    gOrigSlotFlip = reinterpret_cast<void (*)()>(base + 0x1cb14);
    writeCave(kCaveFlip, (void*)&drasticSlotFlipHook);
    // Emulated-frame counter: the frame limiter (+0x1b7a8) is entered from exactly
    // ONE site (+0x2c99c: `bl +0x1b7a8`), once per emulated frame in every mode.
    // Replace that bl with a bl into a cave that bumps gEmuFrames and tail-branches
    // (b, LR preserved) into the real limiter, so gEmuFrames is the true emulation
    // rate. Cave at kCaveLim (free RX padding after the threaded3d caves).
    {
        const uintptr_t kLimSite = 0x2c99c;
        const uint32_t  kLimExpect = 0x97ffbb83u;   // bl +0x1b7a8 from +0x2c99c (device-verified)
        uint32_t* site = reinterpret_cast<uint32_t*>(base + kLimSite);
        if (*site != kLimExpect) {
            ALOGW("DrasticRunner: emu-frame counter: unexpected code at +0x%lx (0x%08x), "
                  "skipping", (unsigned long)kLimSite, *site);
        } else {
            uint32_t* c = reinterpret_cast<uint32_t*>(base + kCaveLim);
            c[0] = 0x580000b0u;   // ldr x16, [pc, #20]  -> &gEmuFrames at c[5]
            c[1] = 0xb9400211u;   // ldr w17, [x16]
            c[2] = 0x11000631u;   // add w17, w17, #1
            c[3] = 0xb9000211u;   // str w17, [x16]     (single writer: emulator thread)
            intptr_t bd = (intptr_t)(base + 0x1b7a8) - (intptr_t)(base + kCaveLim + 16);
            c[4] = 0x14000000u | (uint32_t)((bd >> 2) & 0x03ffffffu);   // b +0x1b7a8
            uint64_t addr = (uint64_t)(uintptr_t)&gEmuFrames;
            memcpy(&c[5], &addr, 8);
            // page holding +0x2c99c must be writable for the patch.
            uint8_t* limPg = (uint8_t*)((uintptr_t)site & ~(uintptr_t)(ps - 1));
            if (mprotect(limPg, ps, PROT_READ | PROT_WRITE | PROT_EXEC) == 0) {
                intptr_t d = (intptr_t)(base + kCaveLim) - (intptr_t)(base + kLimSite);
                *site = 0x94000000u | (uint32_t)((d >> 2) & 0x03ffffffu);   // bl kCaveLim
                __builtin___clear_cache((char*)limPg, (char*)limPg + ps);
                __builtin___clear_cache((char*)(base + kCaveLim), (char*)(base + kCaveLim) + 32);
                mprotect(limPg, ps, PROT_READ | PROT_EXEC);
                ALOGW("DrasticRunner: emu-frame counter installed at +0x%lx", (unsigned long)kLimSite);
            } else {
                ALOGW("DrasticRunner: emu-frame counter: mprotect(site) failed: %s", strerror(errno));
            }
        }
    }
    auto patchBl = [&](uintptr_t site, uintptr_t target) {
        intptr_t d = (intptr_t)target - (intptr_t)site;
        *reinterpret_cast<uint32_t*>(base + site) =
                0x94000000u | (uint32_t)((d >> 2) & 0x03ffffff);
    };
    for (int i = 0; i < 2; i++) { patchBl(kTimeSites[i].off, kCaveTime); patchBl(kWaitSites[i].off, kCaveWait); }
    patchBl(kFlipSite.off, kCaveFlip);
    __builtin___clear_cache((char*)cavePg, (char*)cavePg + ps);
    __builtin___clear_cache((char*)sitePg, (char*)sitePg + ps);
    // Audio output rate: drastic generates 735 samples per emulated frame
    // (60.000 fps worth at 44100) but opens its player at 44100 x 59.8261/60
    // = 43971 Hz (+0x73050..+0x73080: x12 = rate * 0.997101), the DS's native
    // frame rate. The paced emulator runs 60.000 frames per second, so that
    // rate leaves a 0.29% surplus that drifts the buffer queue to full, where
    // the submit (+0x1de98) drops whole frames. Play at 44100: production and
    // consumption match exactly (pitch +0.29%, inaudible).
    // Audio submit hook (audio_probe, default on): wraps every per-frame
    // submit for the frame normalisation below and counts refill underruns.
    if (property_get_bool("sys.gammaos.drastic_nano.audio_probe", true)) {
        // The library's padding page (+0x132c00..+0x133000) is fully used by
        // the pacing, threaded-3D and run-ahead caves, so this probe gets its
        // own executable page, mapped just below the library so the site's
        // 26-bit branch reaches it.
        const uintptr_t site = 0x2cc20, target = 0x1dd6c;
        static uint8_t* sProbePage = nullptr;
        if (!sProbePage) {
            // A plain hint is ignored when the address is taken and the
            // layout below the library varies per launch (one run found no
            // free megabyte in 64), so walk /proc/self/maps for any unmapped
            // page within the 26-bit branch range of the site (128 MB
            // either side, kept to 120 MB for the cave's own branches back).
            const uintptr_t siteAbs = (uintptr_t)base + site;
            const uintptr_t lo = siteAbs > 120u * 0x100000u ? (siteAbs - 120u * 0x100000u) & ~(uintptr_t)(ps - 1) : (uintptr_t)ps;
            const uintptr_t hi = siteAbs + 120u * 0x100000u;
            std::vector<std::pair<uintptr_t, uintptr_t>> used;
            if (FILE* mf = fopen("/proc/self/maps", "r")) {
                char line[512];
                while (fgets(line, sizeof line, mf)) {
                    unsigned long a = 0, b = 0;
                    if (sscanf(line, "%lx-%lx", &a, &b) == 2 && b > lo && a < hi) used.emplace_back((uintptr_t)a, (uintptr_t)b);
                }
                fclose(mf);
            }
            std::sort(used.begin(), used.end());
            // candidates: the page just below each mapping (closest to the
            // library first is not needed; any gap in range does)
            std::vector<uintptr_t> cands;
            uintptr_t cursor = lo;
            for (const auto& r : used) {
                if (r.first > cursor && r.first - cursor >= (uintptr_t)ps) cands.push_back(r.first - ps);   // top of the gap
                if (r.second > cursor) cursor = r.second;
            }
            if (hi > cursor + ps) cands.push_back(cursor);
            for (uintptr_t want : cands) {
                if (want < lo || want + ps > hi) continue;
                void* pg = mmap((void*)want, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS | MAP_FIXED_NOREPLACE, -1, 0);
                if (pg == MAP_FAILED) continue;
                if (pg != (void*)want) { munmap(pg, (size_t)ps); continue; }
                sProbePage = static_cast<uint8_t*>(pg);
                gProbePage = sProbePage;
                break;
            }
            if (!sProbePage) ALOGW("DrasticRunner: audio probe: no executable page within branch range (%zu candidates), skipped", cands.size());
            else ALOGI("DrasticRunner: audio probe page at %p (library %p)", sProbePage, base);
        }
        if (sProbePage && *reinterpret_cast<uint32_t*>(base + site) == 0x97ffc453u) {   // bl +0x1dd6c
            const uintptr_t cave = (uintptr_t)sProbePage - (uintptr_t)base;   // base-relative like the others
            uint8_t* cavePg2 = sProbePage;
            mprotect(cavePg2, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
            // The cave wraps the submit: pre-hook (may rewrite the frame's
            // sample count and tail), the submit itself, then the post-hook
            // (re-seeds the emptied frame buffer with the carried surplus).
            // The caller ignores the submit's return value (+0x2cc24 reloads
            // w8 from memory); x1 is preserved for the submit.
            uint32_t* w = reinterpret_cast<uint32_t*>(base + cave);
            const int64_t bOff = ((int64_t)target - (int64_t)(cave + 20)) / 4;
            w[0] = 0xa9bf07e0u;                      // stp x0, x1, [sp, #-16]!
            w[1] = 0xf81f0ffeu;                      // str x30, [sp, #-16]!
            w[2] = 0x58000150u;                      // ldr x16, [pc, #40]  (pre literal at cave+48)
            w[3] = 0xd63f0200u;                      // blr x16
            w[4] = 0xa94107e0u;                      // ldp x0, x1, [sp, #16]
            w[5] = 0x94000000u | ((uint32_t)bOff & 0x03ffffffu);   // bl +0x1dd6c
            w[6] = 0xf9400be0u;                      // ldr x0, [sp, #16]
            w[7] = 0x580000f0u;                      // ldr x16, [pc, #28]  (post literal at cave+56)
            w[8] = 0xd63f0200u;                      // blr x16
            w[9] = 0xf84107feu;                      // ldr x30, [sp], #16
            w[10] = 0xa8c107e0u;                     // ldp x0, x1, [sp], #16
            w[11] = 0xd65f03c0u;                     // ret
            *reinterpret_cast<uint64_t*>(base + cave + 48) = (uint64_t)(uintptr_t)&raAudioSubmitHook;
            *reinterpret_cast<uint64_t*>(base + cave + 56) = (uint64_t)(uintptr_t)&raAudioSubmitPost;
            __builtin___clear_cache((char*)(base + cave), (char*)(base + cave + 64));
            mprotect(cavePg2, (size_t)ps, PROT_READ | PROT_EXEC);
            gAudLibBase = base;
            uint8_t* sitePg2 = (uint8_t*)((uintptr_t)(base + site) & ~(uintptr_t)(ps - 1));
            mprotect(sitePg2, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
            const int64_t d = ((int64_t)cave - (int64_t)site) / 4;
            *reinterpret_cast<uint32_t*>(base + site) = 0x94000000u | ((uint32_t)d & 0x03ffffffu);
            __builtin___clear_cache((char*)(base + site), (char*)(base + site + 4));
            mprotect(sitePg2, (size_t)ps, PROT_READ | PROT_EXEC);
            ALOGI("DrasticRunner: audio submit probe installed");
        } else ALOGW("DrasticRunner: audio submit site +0x2cc20 unexpected (0x%08x)", *reinterpret_cast<uint32_t*>(base + site));
        // Buffer queue refill callback (+0x1d650, called by OpenSL each time
        // a chunk finishes): when its queued count is zero it enqueues a
        // silence buffer, which is an audible gap. The entry instruction
        // (adrp x8, +0x3c7d000) is replaced by a branch to a second cave at
        // +64 in the probe page that counts empty entries, re-executes the
        // adrp with the displacement recomputed for the cave, and continues
        // at +0x1d654.
        const uintptr_t cbSite = 0x1d650;
        if (sProbePage && *reinterpret_cast<uint32_t*>(base + cbSite) == 0x9001e308u) {
            uint8_t* cavePg3 = sProbePage;
            const uintptr_t cave2 = (uintptr_t)sProbePage - (uintptr_t)base + 64;
            mprotect(cavePg3, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
            uint32_t* w = reinterpret_cast<uint32_t*>(base + cave2);
            const int64_t adrpPage = (((int64_t)0x3c7d000) >> 12) - (((int64_t)(cave2 + 24)) >> 12);
            const uint32_t immlo = (uint32_t)adrpPage & 3u, immhi = ((uint32_t)adrpPage >> 2) & 0x7ffffu;
            const int64_t bOff2 = ((int64_t)(cbSite + 4) - (int64_t)(cave2 + 28)) / 4;
            w[0] = 0xa9bf07e0u;                      // stp x0, x1, [sp, #-16]!
            w[1] = 0xa9bf7be2u;                      // stp x2, x30, [sp, #-16]!
            w[2] = 0x58000110u;                      // ldr x16, [pc, #32]  (literal at cave2+40)
            w[3] = 0xd63f0200u;                      // blr x16
            w[4] = 0xa8c17be2u;                      // ldp x2, x30, [sp], #16
            w[5] = 0xa8c107e0u;                      // ldp x0, x1, [sp], #16
            w[6] = 0x90000008u | (immlo << 29) | (immhi << 5);   // adrp x8, +0x3c7d000 (from the cave)
            w[7] = 0x14000000u | ((uint32_t)bOff2 & 0x03ffffffu);   // b +0x1d654
            w[8] = 0xd503201fu; w[9] = 0xd503201fu;  // pad to the literal
            *reinterpret_cast<uint64_t*>(base + cave2 + 40) = (uint64_t)(uintptr_t)&raAudioCallbackHook;
            __builtin___clear_cache((char*)(base + cave2), (char*)(base + cave2 + 48));
            mprotect(cavePg3, (size_t)ps, PROT_READ | PROT_EXEC);
            uint8_t* sitePg3 = (uint8_t*)((uintptr_t)(base + cbSite) & ~(uintptr_t)(ps - 1));
            mprotect(sitePg3, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
            const int64_t d2 = ((int64_t)cave2 - (int64_t)cbSite) / 4;
            *reinterpret_cast<uint32_t*>(base + cbSite) = 0x14000000u | ((uint32_t)d2 & 0x03ffffffu);
            __builtin___clear_cache((char*)(base + cbSite), (char*)(base + cbSite + 4));
            mprotect(sitePg3, (size_t)ps, PROT_READ | PROT_EXEC);
            ALOGI("DrasticRunner: audio callback probe installed");
        } else if (sProbePage) ALOGW("DrasticRunner: audio callback entry +0x1d650 unexpected (0x%08x)", *reinterpret_cast<uint32_t*>(base + cbSite));

        // SPU mix granularity (persist.gammaos.drastic_nano.spu_mix_lines, default 16, 0 = off).
        // drastic renders the DS sound unit once per frame: the mixer (+0x72764) is called from
        // the frame-end path of the scanline handler (+0x2c8f8, entered once per scanline by the
        // scheduler) and converts the ARM9 cycles elapsed since its previous call into samples
        // in one go, so the SPU channels read their sample memory in 16.7 ms bursts. A game that
        // streams audio through a small looping buffer refilled by the CPU during the frame
        // (Golden Sun Dark Dawn: a 680-sample PCM16 ring for its voice, refilled half at a time
        // every 10.4 ms) is then mixed from halves the CPU has not written yet or has already
        // replaced: a step in the output at every half-ring boundary, audible as scratchy speech.
        // The hardware and melonDS read the ring sample by sample. A third cave takes over the
        // handler's entry: it re-executes the entry instruction, counts scanlines and every N
        // lines calls the mixer for the cycles elapsed so far (x0 = master, x0/x1 preserved for
        // the handler), then continues at +0x2c8fc. The mixer only ever produces the samples for
        // that interval, so the per-frame total and the submit are unchanged; the SPU just tracks
        // the CPU more closely. Gated like the frame-end call on bit 6 of the sound flags byte at
        // master+0x8f42c. The frame limiter site (+0x2c99c) is left to installVblankPacing.
        // Ring-clear removal (persist.gammaos.drastic_nano.no_ring_clear, default on): the channel
        // mix routine at libdrastic +0x724f4 zeroes each source sample as it reads it (the four
        // "strh/strb wzr, [x13, x..]" stores at +0x7258c/+0x725b8/+0x72658/+0x72680). For a normal
        // sample that source is ROM/RAM the game owns, so clearing it is invisible; but Golden Sun
        // DD streams voice through an SPU capture ring that channels 1 and 3 loop over, and the
        // hardware (and melonDS) never clear it. drastic clearing it leaves zero gaps between the
        // consumed region and the capture/DSP write heads, and those gaps are the scratch. NOP the
        // four stores so the ring keeps its samples, exactly as on hardware.
        if (property_get_bool("persist.gammaos.drastic_nano.no_ring_clear", false)) {
            const uintptr_t clr[4] = {0x7258c, 0x725b8, 0x72658, 0x72680};
            const uint32_t exp[4] = {0x782a69bfu, 0x783069bfu, 0x382a69bfu, 0x383069bfu};
            int ok = 0;
            uint8_t* pg0 = (uint8_t*)((uintptr_t)(base + clr[0]) & ~(uintptr_t)(ps - 1));
            mprotect(pg0, (size_t)ps * 2, PROT_READ | PROT_WRITE | PROT_EXEC);
            for (int i = 0; i < 4; i++) {
                uint32_t* site = reinterpret_cast<uint32_t*>(base + clr[i]);
                if (*site == exp[i]) { *site = 0xd503201fu; __builtin___clear_cache((char*)site, (char*)site + 4); ok++; }
            }
            mprotect(pg0, (size_t)ps * 2, PROT_READ | PROT_EXEC);
            ALOGI("DrasticRunner: SPU ring-clear removal patched %d/4 stores", ok);
        }
        const int mixLines = property_get_int32("persist.gammaos.drastic_nano.spu_mix_lines", 0);
        const uintptr_t lineEntry = 0x2c8f8;
        if (sProbePage && mixLines > 0 && *reinterpret_cast<uint32_t*>(base + lineEntry) == 0xf81a0ffbu) {   // str x27, [sp, #-96]!
            uint8_t* cavePg4 = sProbePage;
            const uintptr_t cave3 = (uintptr_t)sProbePage - (uintptr_t)base + 128;
            mprotect(cavePg4, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
            uint32_t* w = reinterpret_cast<uint32_t*>(base + cave3);
            static const uint32_t kCave3[] = {   // scratchpad/gs/cave7.s
                0xf81a0ffbu, 0xa9bf07e0u, 0xa9bf7bfdu, 0x58000330u,
                0xb9400211u, 0xb940060fu, 0x11000631u, 0x6b0f023fu,
                0x540001e3u, 0xb900021fu, 0x529e858fu, 0x72a0010fu,
                0x386f680fu, 0x3730016fu, 0x94000000u, 0xf9400be0u,
                0x580001d0u, 0xd63f0200u, 0x58000150u, 0xb9400a0fu,
                0x110005efu, 0xb9000a0fu, 0x14000002u, 0xb9000211u,
                0xa8c17bfdu, 0xa8c107e0u, 0x14000000u, 0xd503201fu,
                0u, 0u,        // +0x70: control block address (u64)
                0u, 0u,        // +0x78: spuMixTrace address (u64)
                0u,            // +0x80: lines since the last mix
                0xffffffffu,   // +0x84: lines per mix (disarmed)
                0u,            // +0x88: mixes done
                0u,            // +0x8c: pad
            };
            memcpy(w, kCave3, sizeof(kCave3));
            const int64_t bMix  = ((int64_t)0x72764 - (int64_t)(cave3 + 0x38)) / 4;
            const int64_t bBack = ((int64_t)(lineEntry + 4) - (int64_t)(cave3 + 0x68)) / 4;
            w[14] = 0x94000000u | ((uint32_t)bMix & 0x03ffffffu);
            w[26] = 0x14000000u | ((uint32_t)bBack & 0x03ffffffu);
            *reinterpret_cast<uint64_t*>(base + cave3 + 0x70) = (uint64_t)(uintptr_t)(base + cave3 + 0x80);
            *reinterpret_cast<uint64_t*>(base + cave3 + 0x78) = (uint64_t)(uintptr_t)&spuMixTrace;
            gSpuMixCtl = reinterpret_cast<volatile uint32_t*>(base + cave3 + 0x80);
            if (property_get_bool("sys.gammaos.drastic_nano.spu_trace", false)) {
                gSpuTraceBase = base;
                gSpuTrace = static_cast<SpuTraceRec*>(calloc(kSpuTraceCap, sizeof(SpuTraceRec)));
                gSpuMini = static_cast<SpuMini*>(calloc(kSpuMiniCap, sizeof(SpuMini)));
                ALOGI("DrasticRunner: SPU trace buffer %s", gSpuTrace ? "ready" : "FAILED");
            }
            gSpuMixLines = (uint32_t)mixLines;
            __builtin___clear_cache((char*)(base + cave3), (char*)(base + cave3 + sizeof(kCave3)));
            // The probe page stays RWX: the cave's control block (counters) is written from the
            // cave and from the submit hook, like the counters the other two caves keep there.
            uint8_t* sitePg4 = (uint8_t*)((uintptr_t)(base + lineEntry) & ~(uintptr_t)(ps - 1));
            mprotect(sitePg4, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
            const int64_t d3 = ((int64_t)cave3 - (int64_t)lineEntry) / 4;
            *reinterpret_cast<uint32_t*>(base + lineEntry) = 0x14000000u | ((uint32_t)d3 & 0x03ffffffu);
            __builtin___clear_cache((char*)(base + lineEntry), (char*)(base + lineEntry + 4));
            mprotect(sitePg4, (size_t)ps, PROT_READ | PROT_EXEC);
            ALOGI("DrasticRunner: SPU mix every %d scanlines installed (cave +0x%zx)", mixLines, (size_t)cave3);
        } else if (sProbePage && mixLines > 0) ALOGW("DrasticRunner: scanline handler entry +0x2c8f8 unexpected (0x%08x)", *reinterpret_cast<uint32_t*>(base + lineEntry));
        // In-ring interpolation repair cave: wraps the mixer's channel-mix call so the ring is bridged
        // right before channels 1/3 read it. Site +0x72858 is "bl +0x71bf0" inside the mixer +0x72764,
        // where x19 = master and x0/x1/w2 are the channel-mix arguments; the cave saves those, calls
        // spuRingRepair(master), restores them and tail-branches into +0x71bf0 with x30 still holding the
        // mixer's return address. Lives at probe page +512, clear of the other caves.
        if (sProbePage && property_get_bool("sys.gammaos.drastic_nano.spu_trace", false) && !gSpuTrace) {
            gSpuTraceBase = base;
            gSpuTrace = static_cast<SpuTraceRec*>(calloc(kSpuTraceCap, sizeof(SpuTraceRec)));
            gSpuMini = static_cast<SpuMini*>(calloc(kSpuMiniCap, sizeof(SpuMini)));
            ALOGI("DrasticRunner: SPU trace buffer %s (early)", gSpuTrace ? "ready" : "FAILED");
        }
        const uintptr_t rrSite = 0x72858;
        if (sProbePage && property_get_bool("persist.gammaos.drastic_nano.hw_route", true) &&
            *reinterpret_cast<uint32_t*>(base + rrSite) == 0x97fffce6u) {   // bl +0x71bf0
            // Hardware routing cave (probe page +768, scratchpad/gs/caveRoute.s): hands the whole mix to
            // spuHwRoute; on 1 it skips the original channel-mix + two capture calls (continues at
            // +0x72884), on 0 it tail-calls the original +0x71bf0 with the mixer's return address intact.
            if (property_get_bool("sys.gammaos.drastic_nano.spu_trace", false) && !gSpuTrace) {
                gSpuTraceBase = base;
                gSpuTrace = static_cast<SpuTraceRec*>(calloc(kSpuTraceCap, sizeof(SpuTraceRec)));
                gSpuMini = static_cast<SpuMini*>(calloc(kSpuMiniCap, sizeof(SpuMini)));
                ALOGI("DrasticRunner: SPU trace buffer %s (hw_route)", gSpuTrace ? "ready" : "FAILED");
            }
            uint8_t* pg = sProbePage;
            const uintptr_t cave = (uintptr_t)sProbePage - (uintptr_t)base + 768;
            mprotect(pg, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
            uint32_t* w = reinterpret_cast<uint32_t*>(base + cave);
            static const uint32_t kCaveRoute[] = {
                0xa9bf07e0u, 0xa9bf0fe2u, 0xa9bf7be4u, 0xaa1603e0u, 0xaa1403e1u, 0x2a1503e2u, 0xaa1303e3u, 0x580001e4u,
                0x58000190u, 0xd63f0200u, 0x340000a0u, 0xa8c17be4u, 0xa8c10fe2u, 0xa8c107e0u, 0x14000000u, 0xa8c17be4u,
                0xa8c10fe2u, 0xa8c107e0u, 0x14000000u, 0xd503201fu, 0u, 0u, 0u, 0u,
            };
            memcpy(w, kCaveRoute, sizeof(kCaveRoute));
            w[14] = 0x14000000u | ((uint32_t)(((int64_t)0x72884 - (int64_t)(cave + 0x38)) / 4) & 0x03ffffffu);
            w[18] = 0x14000000u | ((uint32_t)(((int64_t)0x71bf0 - (int64_t)(cave + 0x48)) / 4) & 0x03ffffffu);
            *reinterpret_cast<uint64_t*>(base + cave + 0x50) = (uint64_t)(uintptr_t)&spuHwRoute;
            *reinterpret_cast<uint64_t*>(base + cave + 0x58) = (uint64_t)(uintptr_t)(base + 0x71bf0);
            __builtin___clear_cache((char*)(base + cave), (char*)(base + cave + sizeof(kCaveRoute)));
            uint8_t* sp2 = (uint8_t*)((uintptr_t)(base + rrSite) & ~(uintptr_t)(ps - 1));
            mprotect(sp2, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
            *reinterpret_cast<uint32_t*>(base + rrSite) = 0x94000000u | ((uint32_t)(((int64_t)cave - (int64_t)rrSite) / 4) & 0x03ffffffu);
            __builtin___clear_cache((char*)(base + rrSite), (char*)(base + rrSite + 4));
            mprotect(sp2, (size_t)ps, PROT_READ | PROT_EXEC);
            ALOGI("DrasticRunner: SPU hardware output routing + real capture installed (cave +0x%zx)", (size_t)cave);
        } else if (sProbePage && property_get_bool("persist.gammaos.drastic_nano.ring_repair", true) &&
            *reinterpret_cast<uint32_t*>(base + rrSite) == 0x97fffce6u) {   // bl +0x71bf0
            uint8_t* cavePg5 = sProbePage;
            const uintptr_t cave4 = (uintptr_t)sProbePage - (uintptr_t)base + 512;
            mprotect(cavePg5, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
            uint32_t* w = reinterpret_cast<uint32_t*>(base + cave4);
            static const uint32_t kCaveRR[] = {   // scratchpad/gs/caveRR2.s
                0xa9bf07e0u, 0xa9bf7be2u, 0xaa1303e0u, 0x580001b0u,
                0xd63f0200u, 0xa8c17be2u, 0xa8c107e0u, 0xf81f0ffeu,
                0x94000000u, 0xf84107feu, 0xf81f0ffeu, 0xaa1303e0u,
                0x580000d0u, 0xd63f0200u, 0xf84107feu, 0xd65f03c0u,
                0u, 0u,        // +0x40: spuRingRepairPre address (u64)
                0u, 0u,        // +0x48: spuRingRepairPost address (u64)
            };
            memcpy(w, kCaveRR, sizeof(kCaveRR));
            const int64_t bMix = ((int64_t)0x71bf0 - (int64_t)(cave4 + 0x20)) / 4;
            w[8] = 0x94000000u | ((uint32_t)bMix & 0x03ffffffu);   // bl +0x71bf0, returns into the cave
            *reinterpret_cast<uint64_t*>(base + cave4 + 0x40) = (uint64_t)(uintptr_t)&spuRingRepairPre;
            *reinterpret_cast<uint64_t*>(base + cave4 + 0x48) = (uint64_t)(uintptr_t)&spuRingRepairPost;
            __builtin___clear_cache((char*)(base + cave4), (char*)(base + cave4 + sizeof(kCaveRR)));
            uint8_t* sitePg5 = (uint8_t*)((uintptr_t)(base + rrSite) & ~(uintptr_t)(ps - 1));
            mprotect(sitePg5, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
            const int64_t d = ((int64_t)cave4 - (int64_t)rrSite) / 4;
            *reinterpret_cast<uint32_t*>(base + rrSite) = 0x94000000u | ((uint32_t)d & 0x03ffffffu);   // bl cave4
            __builtin___clear_cache((char*)(base + rrSite), (char*)(base + rrSite + 4));
            mprotect(sitePg5, (size_t)ps, PROT_READ | PROT_EXEC);
            ALOGI("DrasticRunner: SPU ring interpolation repair installed (cave +0x%zx)", (size_t)cave4);
        } else if (sProbePage && property_get_bool("persist.gammaos.drastic_nano.ring_repair", true))
            ALOGW("DrasticRunner: mixer channel-mix site +0x72858 unexpected (0x%08x)", *reinterpret_cast<uint32_t*>(base + rrSite));
        // PCM channel linear interpolation (persist.gammaos.drastic_nano.pcm_interp): DraStic's channel mixer
        // resamples every channel from its own rate to 44.1 kHz by nearest neighbour: the PCM16 fetch at
        // +0x71e04 / +0x72154 is "ldrsh w8, [x22, x8, lsl #1]" with x8 = the integer part of the 32.32
        // position only, the fraction never used. Sample-and-hold images the source spectrum around its
        // Nyquist and sounds gritty ("harsh, raw"); melonDS reads a 32.7 kHz channel 1:1 and sounds rounded.
        // Two caves replace those fetches with a lerp between buf[i-1] and buf[i] by the 16-bit fraction
        // (both already decoded; the 64-entry decode buffer is refilled just in time, so buf[i+1] may be
        // stale), i.e. linear interpolation with a one-source-sample delay. The first sample of a channel
        // (position 0) is fetched plain so a stale buf[i-1] cannot click. x16/x17 are scratch; x30 is not
        // live across the loop (it already calls the decoder). Caves at probe page +640 and +704.
        if (sProbePage && property_get_bool("persist.gammaos.drastic_nano.pcm_interp", false)) {
            static const uint32_t kLerp21[] = {0xd360feb1u,0x34000191u,0x78e87ad0u,0x51000511u,0x12001631u,0x78f17ad1u,0xd350fea8u,0x12003d08u,
                                               0x4b110210u,0x9b287e10u,0x9350fe10u,0x0b100228u,0xd65f03c0u,0x78e87ac8u,0xd65f03c0u};
            static const uint32_t kLerp23[] = {0xd360fef1u,0x34000191u,0x78e87ad0u,0x51000511u,0x12001631u,0x78f17ad1u,0xd350fee8u,0x12003d08u,
                                               0x4b110210u,0x9b287e10u,0x9350fe10u,0x0b100228u,0xd65f03c0u,0x78e87ac8u,0xd65f03c0u};
            const uintptr_t sites[2] = {0x71e04, 0x72154};
            const uint32_t* caves[2] = {kLerp21, kLerp23};
            const uintptr_t caveOff[2] = {(uintptr_t)sProbePage - (uintptr_t)base + 640, (uintptr_t)sProbePage - (uintptr_t)base + 704};
            int ok = 0;
            mprotect(sProbePage, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
            for (int k = 0; k < 2; k++) {
                if (*reinterpret_cast<uint32_t*>(base + sites[k]) != 0x78e87ac8u) {   // ldrsh w8, [x22, x8, lsl #1]
                    ALOGW("DrasticRunner: pcm_interp site +0x%zx unexpected (0x%08x)", (size_t)sites[k], *reinterpret_cast<uint32_t*>(base + sites[k]));
                    continue;
                }
                memcpy(base + caveOff[k], caves[k], 15 * 4);
                __builtin___clear_cache((char*)(base + caveOff[k]), (char*)(base + caveOff[k] + 64));
                uint8_t* sp = (uint8_t*)((uintptr_t)(base + sites[k]) & ~(uintptr_t)(ps - 1));
                mprotect(sp, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
                const int64_t d = ((int64_t)caveOff[k] - (int64_t)sites[k]) / 4;
                *reinterpret_cast<uint32_t*>(base + sites[k]) = 0x94000000u | ((uint32_t)d & 0x03ffffffu);   // bl cave
                __builtin___clear_cache((char*)(base + sites[k]), (char*)(base + sites[k] + 4));
                mprotect(sp, (size_t)ps, PROT_READ | PROT_EXEC);
                ok++;
            }
            ALOGI("DrasticRunner: PCM channel linear interpolation installed at %d/2 fetch sites", ok);
        }
        // PCM16 direct-read linear interpolation (persist.gammaos.drastic_nano.pcm16_interp): the PCM16 channel
        // path does NOT use the 64-entry decode buffer; it reads the source directly at +0x71e94 (and the
        // two loop copies at +0x72230 / +0x72288): "ldrsh w11, [x8, x11]" with x8 = source, x11 = byte offset of
        // the integer position, the fraction unused = nearest neighbour. For the routed ch1/ch3 (ring at
        // 32.7 kHz read at 44.1 kHz) that adds +3.5 dB of 6-12 kHz over the ring content (measured on the
        // pass-B accumulator). One cave lerps s_i and s_{i+1} by the 16-bit fraction, wrapping s_{i+1} to the
        // loop start at the loop length (x27), so a circular ring interpolates across its seam. x16/x17 are
        // free in that loop. Probe page +896.
        const int sInterpMode = property_get_int32("persist.gammaos.drastic_nano.pcm16_interp", 2);
        if (sProbePage && sInterpMode >= 1) {
            // The PCM16 channels are read at 44.1 kHz with a fractional step, so they image (nearest-neighbour = the
            // harsh grinding). melonDS mixes at 32.7 kHz where these channels have step 1.0 and never resample. mode 1 =
            // linear interp (sinc^2: kills the imaging but also dulls the genuine highs). mode 2 = Catmull-Rom cubic
            // (offline-verified: HF preserved like nearest, imaging rejected like linear - the best of both, closest to
            // melonDS while staying at 44.1 kHz so no rate change / no underruns). Twin register variants: site +0x71e94
            // has pos=x21 len=x27; the loop copies at +0x72230/+0x72288 have pos=x23 len=x21, so each gets its own cave.
            static const uint32_t kLerpA[] = {0x78eb6910u,0xd341fd71u,0x91000631u,0xeb1b023fu,0x9a9123f1u,0x78f17911u,0x4b100231u,
                                              0xd350feabu,0x12003d6bu,0x9b2b7e31u,0x9350fe31u,0x0b11020bu,0xd65f03c0u};
            static const uint32_t kLerpB[] = {0x78eb6910u,0xd341fd71u,0x91000631u,0xeb15023fu,0x9a9123f1u,0x78f17911u,0x4b100231u,
                                              0xd350feebu,0x12003d6bu,0x9b2b7e31u,0x9350fe31u,0x0b11020bu,0xd65f03c0u};
            // Catmull-Rom cubic (pos=x21 len=x27), saves x2,x3,x12-x15; reads s[i-1..i+2] circular, 16.16 frac. Gated:
            // the cave loads gCubicActive (literal at cave+192, filled at install); when 0 it does a plain nearest fetch, so
            // only capture-routed games (hw_route active) pay for the cubic. Non-capture games get stock audio and speed.
            static const uint32_t kCubicA[] = {0x58000610u,0xb9400210u,0x34000570u,0xa9bf0fe2u,0xa9bf37ecu,0xa9bf3feeu,0xd341fd62u,0x78ab690du,0xd1000763u,0xd1000451u,0xf100005fu,0x9a910071u,0x78b1790cu,0x91000451u,0xeb1b023fu,0x9a9103f1u,0x78b1790eu,0x91000851u,0xeb1b0223u,0x9a912071u,0x78b1790fu,0xd3507eb0u,0xcb0e01a2u,0x8b020442u,0xcb0c01e3u,0x8b030051u,0x9b117e11u,0x9350fe31u,0xd37ef5c2u,0x8b0c0442u,0xcb0f0042u,0xcb0d0842u,0xcb0d0042u,0x8b110051u,0x9b117e11u,0x9350fe31u,0xcb0c01c2u,0x8b110051u,0x9b117e11u,0x9351fe31u,0x8b1101abu,0xa8c13feeu,0xa8c137ecu,0xa8c10fe2u,0xd65f03c0u,0x78eb690bu,0xd65f03c0u,0xd503201fu,0x0u,0x0u};
            static const uint32_t kCubicB[] = {0x58000610u,0xb9400210u,0x34000570u,0xa9bf0fe2u,0xa9bf37ecu,0xa9bf3feeu,0xd341fd62u,0x78ab690du,0xd10006a3u,0xd1000451u,0xf100005fu,0x9a910071u,0x78b1790cu,0x91000451u,0xeb15023fu,0x9a9103f1u,0x78b1790eu,0x91000851u,0xeb150223u,0x9a912071u,0x78b1790fu,0xd3507ef0u,0xcb0e01a2u,0x8b020442u,0xcb0c01e3u,0x8b030051u,0x9b117e11u,0x9350fe31u,0xd37ef5c2u,0x8b0c0442u,0xcb0f0042u,0xcb0d0842u,0xcb0d0042u,0x8b110051u,0x9b117e11u,0x9350fe31u,0xcb0c01c2u,0x8b110051u,0x9b117e11u,0x9351fe31u,0x8b1101abu,0xa8c13feeu,0xa8c137ecu,0xa8c10fe2u,0xd65f03c0u,0x78eb690bu,0xd65f03c0u,0xd503201fu,0x0u,0x0u};
            const bool cubic = sInterpMode >= 2;
            const uint32_t* cvA = cubic ? kCubicA : kLerpA; const size_t szA = cubic ? sizeof(kCubicA) : sizeof(kLerpA);
            const uint32_t* cvB = cubic ? kCubicB : kLerpB; const size_t szB = cubic ? sizeof(kCubicB) : sizeof(kLerpB);
            const uintptr_t sites[3] = {0x71e94, 0x72230, 0x72288};
            const uintptr_t caves[2] = {(uintptr_t)sProbePage - (uintptr_t)base + (cubic ? 3072 : 896),
                                        (uintptr_t)sProbePage - (uintptr_t)base + (cubic ? 3328 : 960)};
            mprotect(sProbePage, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
            memcpy(base + caves[0], cvA, szA); memcpy(base + caves[1], cvB, szB);
            __builtin___clear_cache((char*)(base + caves[0]), (char*)(base + caves[1] + szB));
            if (cubic) {   // point both cubic caves at gCubicActive (literal at +192) so they gate on the routing
                *reinterpret_cast<volatile int**>(base + caves[0] + 192) = &gCubicActive;
                *reinterpret_cast<volatile int**>(base + caves[1] + 192) = &gCubicActive;
                __builtin___clear_cache((char*)(base + caves[0]), (char*)(base + caves[1] + szB));
            }
            int ok = 0;
            for (int k = 0; k < 3; k++) {
                const uintptr_t cave = caves[k == 0 ? 0 : 1];
                if (*reinterpret_cast<uint32_t*>(base + sites[k]) != 0x78eb690bu) {   // ldrsh w11, [x8, x11]
                    ALOGW("DrasticRunner: pcm16_interp site +0x%zx unexpected (0x%08x)", (size_t)sites[k], *reinterpret_cast<uint32_t*>(base + sites[k])); continue;
                }
                uint8_t* sp = (uint8_t*)((uintptr_t)(base + sites[k]) & ~(uintptr_t)(ps - 1));
                mprotect(sp, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
                *reinterpret_cast<uint32_t*>(base + sites[k]) = 0x94000000u | ((uint32_t)(((int64_t)cave - (int64_t)sites[k]) / 4) & 0x03ffffffu);   // bl cave
                __builtin___clear_cache((char*)(base + sites[k]), (char*)(base + sites[k] + 4));
                mprotect(sp, (size_t)ps, PROT_READ | PROT_EXEC); ok++;
            }
            ALOGI("DrasticRunner: PCM16 direct-read %s interpolation installed at %d/3 sites", cubic ? "cubic" : "linear", ok);
        }
        // NEON RGB555 two-source blend (persist.gammaos.drastic_nano.lerp_neon, default ON: proven bit-exact on the host over
        // 264M pixels / all 65536 factor pairs, hot bucket emptied on device, main emu thread -7.6% CPU, panels intact).
        // Profile: the scalar loop at +0x48358 (per pixel: ch = min(srcCh*2B + tabCh*A, 1023) >> 5, pack RGB555|0x8000,
        // 33 instructions, do-while over [ctx+0x4c] pixels) is ~7% of all libdrastic CPU in the 3D band workers. The cave
        // (scratchpad/gs/caveLerp555.s) does floor(count/4)*4 pixels in u32 lanes (overflow-proof for any byte factors)
        // and leaves x9 as the index, then branches to the original scalar loop for the tail (drastic's own code, so the
        // tail is bit-exact by construction) or to the ret when nothing remains (the do-while would run once more). It is
        // reached by a plain b from +0x48354 (the last constant-setup mov, which the cave repeats): this leaf returns
        // through LR, so no bl. The two trailing placeholders are patched here to the real targets.
        if (sProbePage && property_get_int32("persist.gammaos.drastic_nano.lerp_neon", 1) > 0) {
            static const uint32_t kLerp555[] = {0x528f800du,0x7940980eu,0x4e040d50u,0x4e040d11u,0x52807fefu,0x4e040df2u,0x4f0007f3u,0x5290000fu,0x4e040df4u,0x4b0901d0u,0x7100121fu,0x54000543u,0xd37ff92fu,0xfc6f6840u,0x2f10a400u,0x4e331c01u,0x6f3b0402u,0x4e331c42u,0x6f360403u,0x4e331c63u,0x8b090070u,0xbd400204u,0xbd410205u,0xbd420206u,0x2f08a484u,0x2f10a484u,0x2f08a4a5u,0x2f10a4a5u,0x2f08a4c6u,0x2f10a4c6u,0x4eb09c21u,0x4eb19481u,0x4eb09c42u,0x4eb194a2u,0x4eb09c63u,0x4eb194c3u,0x6eb26c21u,0x6eb26c42u,0x6eb26c63u,0x6f3b0421u,0x6f3b0442u,0x6f3b0463u,0x4f255442u,0x4f2a5463u,0x4ea21c21u,0x4ea31c21u,0x4eb41c21u,0x0e612821u,0xfc2f6821u,0x91001129u,0x4b0901d0u,0x7100121fu,0x54fffb02u,0x6b0e013fu,0x54000042u,0x14000000u,0x14000000u};
            const uintptr_t kSite = 0x48354, kTail = 0x48358, kRet = 0x48430;
            const size_t nw = sizeof(kLerp555) / sizeof(kLerp555[0]);
            if (*reinterpret_cast<uint32_t*>(base + kSite) != 0x528f800du || kLerp555[nw - 2] != 0x14000000u || kLerp555[nw - 1] != 0x14000000u) {
                ALOGW("DrasticRunner: lerp_neon: site +0x%zx unexpected (0x%08x), skipped", (size_t)kSite, *reinterpret_cast<uint32_t*>(base + kSite));
            } else {
                const uintptr_t cave = (uintptr_t)sProbePage - (uintptr_t)base + 3584;
                auto bRel = [](int64_t from, int64_t to) { return 0x14000000u | ((uint32_t)((to - from) / 4) & 0x03ffffffu); };
                mprotect(sProbePage, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
                memcpy(base + cave, kLerp555, sizeof(kLerp555));
                *reinterpret_cast<uint32_t*>(base + cave + (nw - 2) * 4) = bRel((int64_t)(cave + (nw - 2) * 4), (int64_t)kTail);
                *reinterpret_cast<uint32_t*>(base + cave + (nw - 1) * 4) = bRel((int64_t)(cave + (nw - 1) * 4), (int64_t)kRet);
                __builtin___clear_cache((char*)(base + cave), (char*)(base + cave + sizeof(kLerp555)));
                uint8_t* sp = (uint8_t*)((uintptr_t)(base + kSite) & ~(uintptr_t)(ps - 1));
                mprotect(sp, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
                *reinterpret_cast<uint32_t*>(base + kSite) = bRel((int64_t)kSite, (int64_t)cave);
                __builtin___clear_cache((char*)(base + kSite), (char*)(base + kSite + 4));
                mprotect(sp, (size_t)ps, PROT_READ | PROT_EXEC);
                ALOGI("DrasticRunner: NEON RGB555 blend cave installed at probe+3584 (site +0x48354, %zu words)", nw);
            }
        }
        // ADPCM loop-wrap fix (persist.gammaos.drastic_nano.adpcm_loop_fix, default OFF, kept as a no-op experiment): the
        // "frozen decode buffer" theory this targeted was refuted by the refill log (buffered - pos stays 7..8 on every
        // channel in stock, refills run continuously across loop wraps); the +144 field the SPU trace showed frozen is
        // not the buffered count. Measured audio is bit-identical with it on or off. scratchpad/gs/caveWrapFix.s.
        // The per-loop position rebase for every channel format is the PCM-tail "sub x21, x21, x11, lsl #32" (+0x71ecc, and
        // +0x71f28 on the PCM8 tail), reached by the ADPCM loop too. It subtracts the loop length from pos and nothing else,
        // so the decode bookkeeping (+144) is left past the loop end and the refill guard (buffered > pos) blocks every refill
        // for the whole next loop: the channel replays a frozen 64-sample decode window (confirmed in stock: 0 skips before
        // the first wrap, 27% after, forever = the grinding on looping ADPCM channels). The cave re-executes the rebase,
        // rebases +144 by the same length, and restores the loop-start predictor DraStic saved at the first boundary hit.
        if (sProbePage && property_get_bool("persist.gammaos.drastic_nano.adpcm_loop_fix", false)) {   // REFUTED: refills run fine in stock (gap 7-8); default off
            static const uint32_t kWF[] = {0xcb0b82b5u,0xb94092d0u,0x4b0b0210u,0xb90092d0u,0x794172d0u,0x790176d0u,0x3942fed0u,0x390302d0u,0x580000d0u,0xb9400211u,0x11000631u,0xb9000211u,0xd65f03c0u,0xd503201fu,0u,0u};
            const uintptr_t sites[2] = {0x71ecc, 0x71f28};
            mprotect(sProbePage, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
            int ok = 0;
            for (int k = 0; k < 2; k++) {
                if (*reinterpret_cast<uint32_t*>(base + sites[k]) != 0xcb0b82b5u) { ALOGW("DrasticRunner: loop-fix site +0x%zx unexpected (0x%08x)", (size_t)sites[k], *reinterpret_cast<uint32_t*>(base + sites[k])); continue; }
                const uintptr_t cave = (uintptr_t)sProbePage - (uintptr_t)base + 2048 + k * 128;
                memcpy(base + cave, kWF, sizeof(kWF));
                *reinterpret_cast<uint64_t*>(base + cave + 0x38) = (uint64_t)(uintptr_t)&gAdpcmWraps;
                __builtin___clear_cache((char*)(base + cave), (char*)(base + cave + sizeof(kWF)));
                uint8_t* sp = (uint8_t*)((uintptr_t)(base + sites[k]) & ~(uintptr_t)(ps - 1));
                mprotect(sp, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
                *reinterpret_cast<uint32_t*>(base + sites[k]) = 0x94000000u | ((uint32_t)(((int64_t)cave - (int64_t)sites[k]) / 4) & 0x03ffffffu);   // bl cave
                __builtin___clear_cache((char*)(base + sites[k]), (char*)(base + sites[k] + 4));
                mprotect(sp, (size_t)ps, PROT_READ | PROT_EXEC); ok++;
            }
            ALOGI("DrasticRunner: ADPCM loop-wrap fix installed at %d/2 wrap sites", ok);
        }
        // Native-rate mix (persist.gammaos.drastic_nano.native_mix=1): make DraStic's SPU mix at the rate its channels
        // actually run at, so a DS-native channel (SOUNDxTMR -512 = 32728.5 Hz, what this game uses for its music, its
        // capture rings and ch1/ch3) gets a step of exactly 1.0, as on hardware and in melonDS: no per-channel nearest
        // resampling, no skipped sample every 346 (measured: that skip put a 94.6 Hz phase sawtooth on every line, i.e.
        // sidebands 5 dB below the carrier, the "grinding"). DraStic runs the DS at 60 fps and scales every channel
        // step by 60/59.8261, so the matching mix rate is 32729 * 60/59.8261 = 32824 Hz (channel step for -512 then
        // 32728.5/32824 * 1.002907 = 1.000002); the submit hook maps each chunk (547 frames) onto 735 output frames.
        // Two in-place immediates: the SPU init "mov w10, #0xac44" (+0x72ff8) -> 32824, and the per-frame stereo
        // sample constant "add w9, w8, #0x5be" (+0x1dedc, 1470) -> 1094. Patched before the core initialises.
        if (property_get_bool("persist.gammaos.drastic_nano.native_mix", false)) {
            const struct { uintptr_t site; uint32_t expect, patch; const char* what; } imm[2] = {
                {0x72ff8, 0x5295888au, 0x52800000u | (32824u << 5) | 10u, "SPU mix rate 44100->32824"},   // mov w10, #32824
                {0x1dedc, 0x1116f909u, 0x11000000u | (1094u << 10) | (8u << 5) | 9u, "frame stereo samples 1470->1094"},   // add w9, w8, #1094
            };
            int ok = 0;
            for (int k = 0; k < 2; k++) {
                uint32_t* site = reinterpret_cast<uint32_t*>(base + imm[k].site);
                if (*site != imm[k].expect) { ALOGW("DrasticRunner: native_mix site +0x%zx unexpected (0x%08x)", (size_t)imm[k].site, *site); continue; }
                uint8_t* sp = (uint8_t*)((uintptr_t)site & ~(uintptr_t)(ps - 1));
                mprotect(sp, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
                *site = imm[k].patch; __builtin___clear_cache((char*)site, (char*)site + 4);
                mprotect(sp, (size_t)ps, PROT_READ | PROT_EXEC); ok++;
            }
            gNativeMix = (ok == 2) ? 1 : 0;
            ALOGI("DrasticRunner: native-rate mix patched %d/2 sites (%s)", ok, ok == 2 ? "active" : "INACTIVE");
            // Re-point the OpenSL player rate to 32824 here, at LAUNCH: installVblankPacing set it (to the 44100 base) at
            // preload before this session's native_mix prop was known, so without this the mixer runs at 32824 while the
            // player still expects 44100 and the queue drains (the choppiness). Unless native_resample is on (the old
            // in-hook resampler still produces 44100), match the player to the 32824 mix. Both format tables, milliHz.
            if (gNativeMix && !property_get_bool("persist.gammaos.drastic_nano.native_resample", false)) {
                const uint32_t nrate = (uint32_t)llround(32824000.0 * mPanelHz / 60.0);
                static const uintptr_t kRateOffs[2] = { 0x10a08c, 0x10a0c0 };
                for (uintptr_t off : kRateOffs) {
                    uint32_t* p = reinterpret_cast<uint32_t*>(base + off);
                    uint8_t* pg = (uint8_t*)((uintptr_t)p & ~(uintptr_t)(ps - 1));
                    mprotect(pg, (size_t)ps, PROT_READ | PROT_WRITE);
                    *p = nrate;
                    mprotect(pg, (size_t)ps, PROT_READ);
                }
                ALOGI("DrasticRunner: native-rate mix: OpenSL player rate set to %u milliHz (32824 Hz base)", nrate);
            }
        }
        // OpenSL sink re-shape: 8 x 33 ms chunks instead of 4 x 67 ms in the SAME 47,040-byte static chunk region
        // (persist.gammaos.drastic_nano.audio_chunks_8, default ON: 200 s steady play went from 6-7 dropped submits
        // with the 4-chunk sink to 0, no crash, panels intact). The residual steady-state clicks
        // and gaps are the 4-chunk queue's quantisation margin: with 67 ms chunks and 16.7 ms submits the depth can
        // only be held about one chunk from both the empty and the full edge. Halving the chunk keeps the total
        // buffering and latency and halves that step. drastic keeps the chunk count in [ctx+0x6c], the chunk size
        // (shorts) in [ctx+0x70], the drop gate as queued >= maxq ([row+0x70]+0xc), the stride as a literal 11760,
        // and the SL locator numBuffers is that same count field (read at +0x1d888, stored by the stp at +0x1d8c4;
        // the "mov w9,#4" at +0x1d89c is an unrelated CreateAudioPlayer argument and must NOT be touched: changing it
        // fails player creation and stalls the audio init). Every read/literal in the audio code (+0x1d600..+0x1e700)
        // becomes the matching immediate; the [ctx+0x70] field itself is left alone since all its reads are patched. The per-chunk fill counters at ctx+0x1dff8 extend into
        // ctx+0x1e000..0x1e01f, which no code touches. All-or-nothing: every original word is verified first.
        if (property_get_int32("persist.gammaos.drastic_nano.audio_chunks_8", 1) > 0) {
            struct P { uintptr_t off; uint32_t expect, patched; const char* what; };
            static const P kChunks8[] = {
                {0x1d888, 0xb9406e6au, 0x5280010au, "count read"}, {0x1d9b8, 0xb9406e68u, 0x52800108u, "count read"},
                {0x1d9fc, 0xb9406e68u, 0x52800108u, "count read"}, {0x1df24, 0xb9406ee9u, 0x52800109u, "count read"},
                {0x1e54c, 0xb9406ea8u, 0x52800108u, "count read"},
                {0x1d9e4, 0xb9407269u, 0x52816f89u, "size read"}, {0x1dee4, 0xb94072e8u, 0x52816f88u, "size read"},
                {0x1e534, 0xb94072a9u, 0x52816f89u, "size read"},
                {0x1de94, 0xb9400d08u, 0x52800108u, "maxq read -> 8"},
                {0x1d9d4, 0x5285be19u, 0x5282df19u, "stride"}, {0x1deac, 0x5285be0au, 0x5282df0au, "stride"},
                {0x1def4, 0x5285be0au, 0x5282df0au, "stride"}, {0x1e524, 0x5285be17u, 0x5282df17u, "stride"},
            };
            bool ok = true;
            for (const P& q : kChunks8) if (*reinterpret_cast<uint32_t*>(base + q.off) != q.expect) {
                ALOGW("DrasticRunner: audio_chunks_8: +0x%zx (%s) is 0x%08x, expected 0x%08x; not applied", (size_t)q.off, q.what,
                      *reinterpret_cast<uint32_t*>(base + q.off), q.expect);
                ok = false;
            }
            if (ok) {
                for (const P& q : kChunks8) raPatchInsn(base, q.off, q.patched);
                gAudioChunks8.store(true);
                ALOGI("DrasticRunner: audio_chunks_8: OpenSL sink re-shaped to 8 x 33 ms chunks (%zu sites)", sizeof(kChunks8) / sizeof(kChunks8[0]));
            }
        }
        // Skip the audio stop/flush the state-load path calls (bl 0x1e320 at +0x7a48c, which touches the OpenSL queue
        // globals at 0x3c7d078): loading a slot stops and restarts the player, which drains the queue and puts a hard
        // discontinuity in the output = the click on every state load. Replacing the call with "mov w0, #1" keeps the
        // player running across the load (the audio content of a same-game slot is continuous, so no flush is needed).
        // This is the same site the run-ahead path patches; only apply it here when run-ahead is NOT managing it.
        if (property_get_bool("persist.gammaos.drastic_nano.skip_load_audio_flush", true) &&
            property_get_int32("persist.gammaos.drastic_nano.runahead_mode", 0) != 2 &&
            *reinterpret_cast<uint32_t*>(base + 0x7a48c) == 0x97fe8fa5u) {   // bl 0x1e320 (kRaLoadJitFlushSite)
            const uint32_t was = raPatchInsn(base, 0x7a48c, 0x52800020u);    // mov w0, #1 (kRaMovW0One)
            ALOGI("DrasticRunner: state-load audio stop/flush skipped (site was 0x%08x)", was);
        }
        // Refill-log diagnostic (persist.gammaos.drastic_nano.refill_log=1): after every ADPCM refill call (+0x71df4) log
        // (rec, pos>>32, +144, +172) so a position rebase inside the refill shows up as pos dropping across the call.
        if (sProbePage && property_get_bool("persist.gammaos.drastic_nano.refill_log", false) && *reinterpret_cast<uint32_t*>(base + 0x71df4) == 0xb94092c8u) {
            static const uint32_t kRL[] = {0xb94092c8u,0xa9bf47f0u,0x580002d0u,0xb9400a11u,0x52861a90u,0x72a00070u,0x6b10023fu,0x540001c2u,0x58000210u,0x8b111211u,0x91004231u,0xb9000236u,0xd360feb0u,0xb9000630u,0xb9000a28u,0xb940aed0u,0xb9000e30u,0x580000f0u,0xb9400a11u,0x11000631u,0xb9000a11u,0xa8c147f0u,0xd65f03c0u,0xd503201fu,0u,0u};
            gRefillLog = static_cast<uint8_t*>(calloc(16 + 16 * 200000, 1));
            if (gRefillLog) {
                const uintptr_t cave = (uintptr_t)sProbePage - (uintptr_t)base + 2304;
                mprotect(sProbePage, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
                memcpy(base + cave, kRL, sizeof(kRL));
                *reinterpret_cast<uint64_t*>(base + cave + 0x60) = (uint64_t)(uintptr_t)gRefillLog;
                __builtin___clear_cache((char*)(base + cave), (char*)(base + cave + sizeof(kRL)));
                uint8_t* sp = (uint8_t*)((uintptr_t)(base + 0x71df4) & ~(uintptr_t)(ps - 1));
                mprotect(sp, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
                *reinterpret_cast<uint32_t*>(base + 0x71df4) = 0x94000000u | ((uint32_t)(((int64_t)cave - (int64_t)0x71df4) / 4) & 0x03ffffffu);
                __builtin___clear_cache((char*)(base + 0x71df4), (char*)(base + 0x71df8));
                mprotect(sp, (size_t)ps, PROT_READ | PROT_EXEC);
                ALOGI("DrasticRunner: refill log installed");
            }
        }
        // Wrap-log diagnostic (persist.gammaos.drastic_nano.wrap_log=1): logs the channel state at BOTH ADPCM loop-wrap
        // paths (+0x71da8 catch-up, +0x71e4c first-wrap) into one 32-byte-entry block; dumped at spu_trace_dump.
        if (sProbePage && property_get_bool("persist.gammaos.drastic_nano.wrap_log", false) &&
            *reinterpret_cast<uint32_t*>(base + 0x71da8) == 0xb94092c9u && *reinterpret_cast<uint32_t*>(base + 0x71e4c) == 0x0b1b011bu) {
            static const uint32_t kWA[] = {0xb94092c9u,0xa9bf47f0u,0xa9bf2feau,0x58000370u,0xb9400a11u,0x713e823fu,0x54000282u,0xd37bea2au,0x8b0a020au,0x9100414au,0xb9000156u,0xd360feabu,0xb900054bu,0xb9000949u,0xb9000d48u,0xb900115bu,0x794172cbu,0x7900294bu,0x3942fecbu,0x3900594bu,0x394306cbu,0x39005d4bu,0xb940b2cbu,0xb900194bu,0x11000631u,0xb9000a11u,0xa8c12feau,0xa8c147f0u,0xd65f03c0u,0xd503201fu,0u,0u};
            static const uint32_t kWB[] = {0xa9bf47f0u,0xa9bf2feau,0x580003d0u,0xb9400a11u,0x713e823fu,0x540002c2u,0xd37bea2au,0x8b0a020au,0x9100414au,0x320102cbu,0xb900014bu,0xd360feabu,0xb900054bu,0xb94092cbu,0xb900094bu,0xb9000d48u,0xb900115bu,0x794176cbu,0x7900294bu,0x394302cbu,0x3900594bu,0x394306cbu,0x39005d4bu,0xb940aecbu,0xb900194bu,0x11000631u,0xb9000a11u,0xa8c12feau,0xa8c147f0u,0x0b1b011bu,0xd65f03c0u,0xd503201fu,0u,0u};
            gWrapLog = static_cast<uint8_t*>(calloc(16 + 32 * 4000, 1));
            if (gWrapLog) {
                *reinterpret_cast<uint32_t*>(gWrapLog + 8) = 0;
                const uintptr_t ca = (uintptr_t)sProbePage - (uintptr_t)base + 1280, cb = ca + 256;
                mprotect(sProbePage, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
                memcpy(base + ca, kWA, sizeof(kWA)); memcpy(base + cb, kWB, sizeof(kWB));
                *reinterpret_cast<uint64_t*>(base + ca + 0x78) = (uint64_t)(uintptr_t)gWrapLog;
                *reinterpret_cast<uint64_t*>(base + cb + 0x80) = (uint64_t)(uintptr_t)gWrapLog;
                __builtin___clear_cache((char*)(base + ca), (char*)(base + cb + sizeof(kWB)));
                for (int k = 0; k < 2; k++) {
                    const uintptr_t site = k ? 0x71e4c : 0x71da8, cave = k ? cb : ca;
                    uint8_t* sp = (uint8_t*)((uintptr_t)(base + site) & ~(uintptr_t)(ps - 1));
                    mprotect(sp, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
                    *reinterpret_cast<uint32_t*>(base + site) = 0x94000000u | ((uint32_t)(((int64_t)cave - (int64_t)site) / 4) & 0x03ffffffu);
                    __builtin___clear_cache((char*)(base + site), (char*)(base + site + 4));
                    mprotect(sp, (size_t)ps, PROT_READ | PROT_EXEC);
                }
                ALOGI("DrasticRunner: wrap log installed at both loop-wrap paths");
            }
        }
        // Fetch-log diagnostic (persist.gammaos.drastic_nano.fetch_log=<channel>): logs every ADPCM/buffer fetch at
        // +0x71e04 for one channel record (pos>>32, fetched s16) into a control block, so the fetch sequence can be
        // compared against the channel's decode buffer offline. Probe page +1024 (cave) with the log block after it.
        {
            const int fl = property_get_int32("persist.gammaos.drastic_nano.fetch_log", -1);
            if (sProbePage && fl >= 0 && fl < 16 && *reinterpret_cast<uint32_t*>(base + 0x71e04) == 0x78e87ac8u) {
                static const uint32_t kFL[] = {0x78e87ac8u,0x580001f0u,0xb9400a11u,0xb9400e09u,0x0a090231u,0xd503201fu,0x91004209u,0x8b111529u,0xb9000136u,0xf9000535u,0xf9000937u,0x79003128u,0x58000090u,0x11000631u,0xb9000a11u,0xd65f03c0u,0u,0u};
                gFetchLog = static_cast<uint8_t*>(calloc(16 + 32 * 262144, 1));   // control (16) + 256k entries, circular (index masked in the cave)
                if (gFetchLog) {
                    *reinterpret_cast<uint64_t*>(gFetchLog) = (uint64_t)(uintptr_t)(base + 0x158c000 + 0x40028 + fl * 0xc8);   // watch = that channel's record
                    *reinterpret_cast<uint32_t*>(gFetchLog + 8) = 0; *reinterpret_cast<uint32_t*>(gFetchLog + 12) = 262143;   // mask
                    const uintptr_t cave = (uintptr_t)sProbePage - (uintptr_t)base + 1024;
                    mprotect(sProbePage, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
                    uint32_t* w = reinterpret_cast<uint32_t*>(base + cave); memcpy(w, kFL, sizeof(kFL));
                    *reinterpret_cast<uint64_t*>(base + cave + 0x40) = (uint64_t)(uintptr_t)gFetchLog;
                    __builtin___clear_cache((char*)(base + cave), (char*)(base + cave + sizeof(kFL)));
                    uint8_t* sp = (uint8_t*)((uintptr_t)(base + 0x71e04) & ~(uintptr_t)(ps - 1));
                    mprotect(sp, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC);
                    *reinterpret_cast<uint32_t*>(base + 0x71e04) = 0x94000000u | ((uint32_t)(((int64_t)cave - (int64_t)0x71e04) / 4) & 0x03ffffffu);
                    __builtin___clear_cache((char*)(base + 0x71e04), (char*)(base + 0x71e08));
                    mprotect(sp, (size_t)ps, PROT_READ | PROT_EXEC);
                    {   // self-check: literal readback + a sentinel entry written from C++ (entry 0)
                        const uint64_t lit = *reinterpret_cast<uint64_t*>(base + cave + 0x40);
                        uint8_t* e0 = gFetchLog + 16; *reinterpret_cast<uint32_t*>(e0) = 0xdeadbeefu; *reinterpret_cast<uint32_t*>(e0 + 4) = 0xffffffffu;
                        *reinterpret_cast<uint32_t*>(gFetchLog + 8) = 1;
                        ALOGI("DrasticRunner: fetch log installed for channel %d (log=%p literal=%p match=%d, site now 0x%08x)", fl, gFetchLog, (void*)(uintptr_t)lit, lit == (uint64_t)(uintptr_t)gFetchLog, *reinterpret_cast<uint32_t*>(base + 0x71e04));
                    }
                }
            }
        }
    }
    // The ratio is the 64-bit fixed-point constant 0xff90ecc69f727e51
    // (0.997101 x 2^64) loaded by a mov/movk quartet at three sites
    // (+0x723ec x12, +0x72450 x11, +0x73058 x14) and applied with umulh to
    // rate << 22. Replacing the constant with 0xffffffffffffffff makes every
    // derived quantity nominal (ratio 1 - 2^-64). Pinning a single derived
    // value instead crashed: those are buffer sizes and time-to-sample
    // scales, not the rate itself.
    {
        static bool sRateDone = false;
        // Off by default: measured on Golden Sun slot 1 it did not change the
        // player rate (43971, set elsewhere) and raised empty-queue top-ups
        // and pacer misses (29 vs 10 per 30 s). Kept as an experiment knob.
        if (!sRateDone && property_get_bool("persist.gammaos.drastic_nano.audio_rate_fix", false)) {
            sRateDone = true;
            struct Q { uintptr_t off; uint32_t expect; uint32_t patched; };
            // movz/movk keep every field but imm16, so the encodings differ only in bits 20:5
            static const Q sites[12] = {
                {0x723ec, 0xd28fca2cu, 0xd29fffecu}, {0x723f0, 0xf2b3ee4cu, 0xf2bfffecu},
                {0x723f4, 0xf2dd98ccu, 0xf2dfffecu}, {0x723fc, 0xf2fff20cu, 0xf2ffffecu},
                {0x72450, 0xd28fca2bu, 0xd29fffebu}, {0x72454, 0xf2b3ee4bu, 0xf2bfffebu},
                {0x72458, 0xf2dd98cbu, 0xf2dfffebu}, {0x72460, 0xf2fff20bu, 0xf2ffffebu},
                {0x73058, 0xd28fca2eu, 0xd29fffeeu}, {0x73060, 0xf2b3ee4eu, 0xf2bfffeeu},
                {0x73068, 0xf2dd98ceu, 0xf2dfffeeu}, {0x7306c, 0xf2fff20eu, 0xf2ffffeeu},
            };
            bool ok = true;
            for (const Q& q : sites) if (*reinterpret_cast<uint32_t*>(base + q.off) != q.expect) { ok = false; ALOGW("DrasticRunner: audio ratio site +0x%lx unexpected (0x%08x)", (unsigned long)q.off, *reinterpret_cast<uint32_t*>(base + q.off)); }
            if (ok) {
                for (const Q& q : sites) raPatchInsn(base, q.off, q.patched);
                ALOGI("DrasticRunner: audio 59.8261/60 rate ratio neutralised at 3 sites (player rate 44100, matches 735 samples x 60 fps)");
            }
        }
    }
    __builtin___clear_cache((char*)flipPg, (char*)flipPg + ps);
    mprotect(cavePg, ps, PROT_READ | PROT_EXEC);
    mprotect(sitePg, ps, PROT_READ | PROT_EXEC);
    mprotect(flipPg, ps, PROT_READ | PROT_EXEC);
    mPaceInstalled = true;
    gVblankPeriodUs.store((int64_t)llround(1000000.0 / mPanelHz));
    mPacerRun.store(true);
    mPacerThread = std::thread([this] { pacerThread(); });
    mPacerThread.detach();
    ALOGW("DrasticRunner: vblank pacing installed (panel %.4f Hz, audio %u mHz)",
          mPanelHz, rate);
}

// Threaded 3D presents the 3D layer one frame late. libdrastic's frame-end
// routine (+0x3cf88, called at scanline 191) first composes the 2D layers
// (+0x3cd78), reading the 3D scanlines through the "published" buffer
// pointer (+0x34eb60 in the render struct), and only afterwards waits for
// the 3D worker and publishes the buffer it just finished (+0x5f4b4:
// wait busy==0, then published = target). The worker is kicked at
// scanline 214, so the frame composed at line 191 shows the geometry
// swapped two frames earlier, while the non-threaded path renders at
// line 214 and shows it one frame later, like the hardware. Games that
// alternate the 3D engine between the two screens every frame (display
// capture + screen swap, e.g. Diddy Kong Racing DS) therefore get each
// screen's 3D image on the other screen with threading on.
//
// The cave below swaps the order: wait for the worker and publish first,
// then compose. The worker still overlaps the whole CPU frame (kicked at
// line 214, joined at line 191 of the next frame); only the join moves in
// front of the 2D compose instead of behind it. The original join after
// the compose stays and becomes a no-op.
void DrasticRunner::installThreaded3dSync(uint8_t* base) {
    if (!base) return;
    // 0 = off; 1 = join before the frame-end compose (line 191); 2 = join before every
    // engine A compose (the whole-frame compose and the partial composes issued by
    // mid-frame VRAM/capture changes); 3 = join only before the whole-frame engine A
    // compose. Mode 3 is the default: games that compose the frame in one go at line
    // 191 (Diddy Kong Racing, Sonic Rush) read the freshest 3D buffer while engine B's
    // compose on the 2D worker thread still overlaps the wait; games that compose
    // incrementally (Pokemon Black 2 does ~130 chunks per frame from line 0) keep
    // drastic's original one-frame pipeline for the whole frame, so a frame is never
    // mixed from two 3D buffers and the first chunk never stalls on the worker (mode 2
    // measured Black 2 below 60 fps for that reason).
    // 4 = mode 3 for whole-frame composes plus an adaptive join at the FIRST chunk of an
    // incrementally composed frame: free when the worker is already done, otherwise wait
    // while the smoothed wait stays under sys.gammaos.drastic_nano.t3d_wait_budget_us
    // (3 ms); a game whose 3D render never fits the vblank slack (Black 2) falls back to
    // the original one-frame pipeline until its worker is idle at the first chunk for 30
    // consecutive frames. Later chunks never join, so a frame is never mixed.
    // 5 = per-band pipeline (see t3dComposeHook): universal, no lag, 3D overlaps the
    // CPU emulation; the default.
    int mode = property_get_int32("persist.gammaos.drastic_nano.t3d_sync", 5);
    { const int rt = property_get_int32("sys.gammaos.drastic_nano.t3d_sync_rt", -1); if (rt >= 0) mode = rt; }   // A/B override
    gT3dMode = mode;
    if (mode <= 0) {
        ALOGW("DrasticRunner: threaded 3D sync patch disabled by property");
        return;
    }
    const long ps = sysconf(_SC_PAGESIZE) > 0 ? sysconf(_SC_PAGESIZE) : 4096;
    struct Word { uintptr_t off; uint32_t expect; };
    // Frame-end compose site (+0x3cf88 at line 191): bl +0x3cd78.
    static const Word kFeSite = { 0x3cfe4, 0x97ffff65u };
    // Engine A (the engine with the 3D layer, render+0x2e78) compose sites: bl +0x5004c
    // with x0 = engine A, w1 = first line, w2 = last line. +0x3ced8 is the whole-frame
    // path of +0x3cd78 on the main thread (the 2D worker +0x3cca0 composes engine B,
    // render+0x84298, meanwhile), +0x3cf40 is the partial path.
    static const Word kASites[2] = { {0x3ced8, 0x94004c5du}, {0x3cf40, 0x94004c43u} };
    // The original join we mirror: ldr w8,[x22,#32]; ldrb w8,[x21]; tbnz #3;
    // mov w8,#0x56c0; movk w8,#0x10,lsl#16; add x0,x19,x8; bl +0x5f4b4.
    static const Word kJoinSite[] = {
        {0x3d2c0, 0xb94022c8u}, {0x3d2c8, 0x394002a8u}, {0x3d2d0, 0x528ad808u},
        {0x3d2d4, 0x72a00208u}, {0x3d2d8, 0x8b080260u}, {0x3d2dc, 0x94008876u},
    };
    auto check = [&](const Word& w) {
        if (*reinterpret_cast<uint32_t*>(base + w.off) == w.expect) return true;
        ALOGW("DrasticRunner: threaded 3D sync: unexpected code at +0x%lx, leaving off",
              (unsigned long)w.off);
        return false;
    };
    if (!check(kFeSite)) return;
    for (const Word& w : kJoinSite) if (!check(w)) return;
    for (const Word& w : kASites) if (!check(w)) return;
    // Both caves, assembled and linked at +0x132ca0 (RX padding after the pacing caves):
    //   fe_cave +0x132ca0 (mode 1): join (+0x5f4b4) then compose (+0x3cd78) with the
    //     original gates ([x22+32] threaded active, !([x21]&8)); literal +0x132d20.
    //   a_cave +0x132d30 (modes 2/3): for composes ending before line 192 derive render
    //     (x0 - 0x2e78), master ([render]) and video ([master+0xfba68]), same gates,
    //     join, then tail-call the line compose (+0x5004c); literal +0x132dd0.
    //     A vblank-issued compose skips the join: nothing to compose, and the kick at
    //     line 214 must never see a join before the worker has taken the job.
    // Counters gT3dStats: [0] frame-end joins, [1] of which preceded by a partial compose,
    // [2] of which found the worker busy, [3] engine A joins, [4] of which busy,
    // [5] last split line seen at a frame-end join.
    static const uint32_t kBlob[110] = {
        0xa9bf7bfdu, 0x910003fdu, 0xb94022c8u, 0x34000308u, 0x394002a8u, 0x371802c8u,
        0x58000350u, 0xb9400211u, 0x11000631u, 0xb9000211u, 0x79405a88u, 0x340000a8u,
        0xb9001608u, 0xb9400611u, 0x11000631u, 0xb9000611u, 0x529d8f31u, 0x72a00691u,
        0x38716a68u, 0x34000088u, 0xb9400a11u, 0x11000631u, 0xb9000a11u, 0x528ad808u,
        0x72a00208u, 0x8b080260u, 0x97fcb1ebu, 0xaa1303e0u, 0x528017e1u, 0x97fc2819u,
        0xa8c17bfdu, 0xd65f03c0u, 0x00000000u, 0x00000000u, 0xd503201fu, 0xd503201fu,
        0xa9bd7bfdu, 0xa90107e0u, 0xa9020fe2u, 0x910003fdu, 0x7102fc5fu, 0x540003c8u,
        0x5285cf11u, 0xcb110010u, 0xf9400211u, 0x52974d08u, 0x72a001e8u, 0xf8686a31u,
        0x52955808u, 0x72a00108u, 0xb8686a28u, 0x34000288u, 0x529e8588u, 0x72a00108u,
        0x38686a28u, 0x37180208u, 0x58000291u, 0xb9400e28u, 0x11000508u, 0xb9000e28u,
        0x529d8f28u, 0x72a00688u, 0x38686a08u, 0x34000088u, 0xb9401228u, 0x11000508u,
        0xb9001228u, 0x528ad808u, 0x72a00208u, 0x8b080200u, 0x97fcb1bfu, 0xa94107e0u,
        0xa9420fe2u, 0xa8c37bfdu, 0x17fc74a1u, 0xd503201fu, 0x00000000u, 0x00000000u,
        0xd503201fu, 0xd503201fu, 0xa9bd7bfdu, 0xa90107e0u, 0xa9020fe2u, 0x910003fdu,
        0x580000d0u, 0xd63f0200u, 0xa94107e0u, 0xa9420fe2u, 0xa8c37bfdu, 0x17fc7492u,
        0x00000000u, 0x00000000u, 0x58000090u, 0x889ffe1fu, 0x17fcb179u, 0xd503201fu,
        0x00000000u, 0x00000000u, 0xd503201fu, 0xd503201fu, 0xb94173e1u, 0x12001c21u,
        0x52800031u, 0x1ac12231u, 0x58000090u, 0xb871321fu, 0xb9416feau, 0xd65f03c0u,
        0x00000000u, 0x00000000u,
    };
    //   c_cave +0x132de0 (mode 4): both engine A sites -> t3dComposeHook(engineA, first,
    //     last) (C, decides and joins), then the line compose; literal +0x132e08.
    //   k_cave +0x132e10 (mode 5): kick site +0x2c9c4 (bl +0x5f3fc) -> reset gT3dBands,
    //     tail-call the kick; literal +0x132e20.
    //   b_cave +0x132e30 (mode 5): rasterizer band-complete site +0x5ee64 (ldr w10,[sp,#364]
    //     in the hi-res frame rasterizer +0x5e648, one band = 32 hi-res lines). With edge
    //     marking on (3D control bit 5) the band-boundary pass (+0x5c9d4 / +0x5dd80 /
    //     just marks the band done (atomic OR); no per-band edge pass (calling the edge
    //     boundary pass from the concurrent rasterizer threads segfaults - see 2026-09-14;
    //     the original frame-end edge pass still runs). Literal +0x132e50.
    // Mode 5 also patches the 3D line fetches so threaded mode reads the in-flight target
    // buffer (+0x34eb58) like non-threaded: +0x5f2a0 / +0x59f98 csel -> mov x8, x9,
    // +0x5fa18 csel -> nop, +0x5fa9c ldr x8,[x9,#8] -> ldr x8,[x9].
    const uintptr_t kCave = 0x132ca0, kFeCave = 0x132ca0, kACave = 0x132d30, kCCave = 0x132de0;
    const uintptr_t kKCave = 0x132e10, kBCave = 0x132e30;
    const int kFeLit = 32, kALit = 76, kCLit = 90, kKLit = 96, kBLit = 108;   // word index of each literal
    static const Word kKickSite = { 0x2c9c4, 0x9400ca8eu };
    static const Word kBandSite = { 0x5ee64, 0xb9416feau };
    struct Patch { uintptr_t off; uint32_t expect; uint32_t with; };
    static const Patch kFetch[4] = {
        { 0x5f2a0, 0x9a8a0128u, 0xaa0903e8u },   // hi-res fetch: csel x8,x9,x10,eq -> mov x8, x9
        { 0x59f98, 0x9a8a0128u, 0xaa0903e8u },   // lo-res fetch: same
        { 0x5fa18, 0x9a8b0129u, 0xd503201fu },   // unified fetch (hi-res): csel x9,x9,x11,eq -> nop
        { 0x5fa9c, 0xf9400528u, 0xf9400128u },   // unified fetch (lo-res): ldr x8,[x9,#8] -> ldr x8,[x9]
    };
    if (mode >= 5) {
        if (!check(kKickSite) || !check(kBandSite)) return;
        for (const Patch& f : kFetch)
            if (*reinterpret_cast<uint32_t*>(base + f.off) != f.expect) {
                ALOGW("DrasticRunner: threaded 3D sync: unexpected fetch code at +0x%lx, leaving off",
                      (unsigned long)f.off);
                return;
            }
    }
    uint8_t* cavePg = (uint8_t*)((uintptr_t)(base + kCave) & ~(uintptr_t)(ps - 1));
    uint8_t* sitePg = (uint8_t*)((uintptr_t)(base + kFeSite.off) & ~(uintptr_t)(ps - 1));
    // Pages touched by mode 5 (kick site, band site, the four fetch sites).
    static const uintptr_t kExtraOffs[5] = { 0x2c9c4, 0x5ee64, 0x5f2a0, 0x59f98, 0x5fa18 };
    uint8_t* extraPg[5];
    for (int i = 0; i < 5; i++) extraPg[i] = (uint8_t*)((uintptr_t)(base + kExtraOffs[i]) & ~(uintptr_t)(ps - 1));
    if (mprotect(cavePg, ps, PROT_READ | PROT_WRITE | PROT_EXEC) != 0 ||
        mprotect(sitePg, ps, PROT_READ | PROT_WRITE | PROT_EXEC) != 0) {
        ALOGW("DrasticRunner: threaded 3D sync: mprotect failed: %s", strerror(errno));
        return;
    }
    if (mode >= 5)
        for (int i = 0; i < 5; i++)
            if (mprotect(extraPg[i], ps, PROT_READ | PROT_WRITE | PROT_EXEC) != 0) {
                ALOGW("DrasticRunner: threaded 3D sync: mprotect(+0x%lx) failed: %s",
                      (unsigned long)kExtraOffs[i], strerror(errno));
                return;
            }
    auto bl = [&](uintptr_t from, uintptr_t to) -> uint32_t {
        intptr_t d = (intptr_t)to - (intptr_t)from;
        return 0x94000000u | (uint32_t)((d >> 2) & 0x03ffffff);
    };
    uint32_t* c = reinterpret_cast<uint32_t*>(base + kCave);
    for (int i = 0; i < 110; i++) c[i] = kBlob[i];
    const uint64_t statsAddr = (uint64_t)(uintptr_t)gT3dStats;
    const uint64_t hookAddr  = (uint64_t)(uintptr_t)&t3dComposeHook;
    const uint64_t bandsAddr = (uint64_t)(uintptr_t)&gT3dBandMask;
    memcpy(&c[kFeLit], &statsAddr, 8);
    memcpy(&c[kALit], &statsAddr, 8);
    memcpy(&c[kCLit], &hookAddr, 8);
    memcpy(&c[kKLit], &bandsAddr, 8);
    memcpy(&c[kBLit], &bandsAddr, 8);
    gT3dBase = base;
    if (mode >= 5) {
        *reinterpret_cast<uint32_t*>(base + kKickSite.off) = bl(kKickSite.off, kKCave);
        *reinterpret_cast<uint32_t*>(base + kBandSite.off) = bl(kBandSite.off, kBCave);
        for (const Patch& f : kFetch) *reinterpret_cast<uint32_t*>(base + f.off) = f.with;
        for (const Word& w : kASites)
            *reinterpret_cast<uint32_t*>(base + w.off) = bl(w.off, kCCave);
    } else if (mode >= 4) {
        for (const Word& w : kASites)
            *reinterpret_cast<uint32_t*>(base + w.off) = bl(w.off, kCCave);
    } else if (mode == 2) {
        for (const Word& w : kASites)
            *reinterpret_cast<uint32_t*>(base + w.off) = bl(w.off, kACave);
    } else if (mode == 3) {
        *reinterpret_cast<uint32_t*>(base + kASites[0].off) = bl(kASites[0].off, kACave);
    } else {
        *reinterpret_cast<uint32_t*>(base + kFeSite.off) = bl(kFeSite.off, kFeCave);
    }
    __builtin___clear_cache((char*)cavePg, (char*)cavePg + ps);
    __builtin___clear_cache((char*)sitePg, (char*)sitePg + ps);
    mprotect(cavePg, ps, PROT_READ | PROT_EXEC);
    mprotect(sitePg, ps, PROT_READ | PROT_EXEC);
    if (mode >= 5)
        for (int i = 0; i < 5; i++) {
            __builtin___clear_cache((char*)extraPg[i], (char*)extraPg[i] + ps);
            mprotect(extraPg[i], ps, PROT_READ | PROT_EXEC);
        }
    mT3dSyncInstalled = true;
    ALOGW("DrasticRunner: threaded 3D sync patch installed (mode %d: join before %s)",
          mode, mode >= 5 ? "nothing: per-band pipeline, fetch reads the in-flight target"
                          : mode >= 4 ? "the whole-frame engine A compose, adaptive at first chunks"
                          : mode == 3 ? "the whole-frame engine A compose"
                          : mode == 2 ? "every engine A compose" : "the frame-end compose");
}

bool DrasticRunner::vblankPacingActive() const { return gPaceOn.load(); }

void DrasticRunner::setVblankPacing(bool on) {
    mPaceWanted = on;
    if (gStepMode.load()) return;   // the step controller owns the lock
    const bool eff = on && mPaceInstalled && !mFastForwardOn && !gPaceBypass.load();
    if (eff != gPaceOn.load()) {
        if (eff) {
            gVirtBaseUs.store((int64_t)realClockUs());
            gVirtBaseSeq.store(gVblSeq.load());
        }
        if (eff && mArm64Base) {
            const uint32_t period = *reinterpret_cast<uint32_t*>(mArm64Base + 0x14c000 + 0x8aae4);
            ALOGI("DrasticRunner: limiter period config = %u (0 = 50000 units)", period);
        }
        gPaceOn.store(eff);
        { std::lock_guard<std::mutex> lk(gPaceMu); }
        gPaceCv.notify_all();
        ALOGI("DrasticRunner: vblank pacing %s", eff ? "on" : "off");
    }
}

std::atomic<uint32_t> gAudioHoldSeq{0};   // vblank seq of the last deliberate audio-lead hold (see audioLeadHoldTick)
void DrasticRunner::reportFrameMiss(int source) {
    if (!gPaceOn.load()) return;   // bypass: the lock is not driving the emulator
    // A replay burst legitimately delays the shown frame: not a pacing miss.
    if (gRaBurst.load() || (int32_t)(gVblSeq.load() - gRaBurstUntilSeq.load()) < 0) return;
    // A deliberate audio-lead hold produced no frame this period: not a miss either.
    if ((int32_t)(gVblSeq.load() - gAudioHoldSeq.load()) <= 2) return;
    // Only adapt in steady state. While the ROM loads, a menu is open or the
    // game is paused the emulator produces nothing and every wait times out;
    // those are not pacing misses.
    const int64_t now = (int64_t)std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
    if (now - gLastFrameUs.load() > 100000) { gSteadyFrames.store(0); return; }  // emulator quiet
    if (gSteadyFrames.load() < 120) return;
    ALOGW("PACE miss source=%d lead=%lld", source, (long long)gLeadUs.load());
    // A frame landed a vblank late or stale: back the tick off by a
    // millisecond, and remember this lead as the edge so the creep stops
    // 500 us short of it rather than re-probing it every few seconds
    // (each probe is a visible stutter). The edge relaxes after ~5 minutes.
    gMissCount.fetch_add(1);
    const int64_t at = gLeadUs.load();
    const uint32_t seq = gVblSeq.load();
    int64_t l = at + 1000;
    if (l > 12000) l = 12000;
    gLeadUs.store(l);
    gLeadHoldUntil.store(seq + 120);
    // A single miss is a hiccup (autosave, decompression burst): back off,
    // hold, creep again. Two misses within ten seconds mark this lead as the
    // edge and pin the creep floor just above it.
    static uint32_t sPrevMissSeq = 0;
    if (sPrevMissSeq && seq - sPrevMissSeq < 600) {
        if (at + 500 > gLeadCreepFloor.load()) gLeadCreepFloor.store(at + 500);
        gLeadFloorRelaxAt.store(seq + 3600);
    }
    sPrevMissSeq = seq;
}

// CPU placement experiment (prop-gated, off by default). Masks are hex CPU
// bitmasks: emu_cpus for the emulator (startGame) thread, worker_cpus for
// drastic's other CPU-heavy threads (its rasterizer workers), render_cpus for
// this render thread. Applied every second so late-spawned workers get it.
void DrasticRunner::applyCpuPlacement() {
    static int64_t sLastUs = 0;
    const int64_t now = (int64_t)std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
    if (now - sLastUs < 1000000) return;
    const int64_t elapsedUs = sLastUs > 0 ? now - sLastUs : 0;
    sLastUs = now;
    // Emulator thread CPU share (utime+stime ticks from /proc), sampled
    // once a second: the pacer uses it to decide when a bypassed heavy scene
    // has become light enough to try the vblank lock again, instead of a
    // blind timed probe that stalls the emulator for a second every 10 s.
    if (mEmuTid > 0 && elapsedUs > 0) {
        static long sPrevTicks = -1;
        char path[64]; snprintf(path, sizeof(path), "/proc/self/task/%d/stat", (int)mEmuTid);
        FILE* f = fopen(path, "r");
        if (f) {
            char line[512] = {};
            if (fgets(line, sizeof(line), f)) {
                const char* rp = strrchr(line, ')');
                long ut = 0, st = 0;
                if (rp && sscanf(rp + 2, "%*c %*d %*d %*d %*d %*d %*u %*u %*u %*u %*u %ld %ld", &ut, &st) == 2) {
                    const long ticks = ut + st;
                    if (sPrevTicks >= 0) {
                        const long hz = sysconf(_SC_CLK_TCK) > 0 ? sysconf(_SC_CLK_TCK) : 100;
                        const int pct = (int)((ticks - sPrevTicks) * 100LL * 1000000LL / (hz * elapsedUs));
                        gEmuCpuPct.store(pct);
                        const int light = property_get_int32("sys.gammaos.drastic_nano.pace_probe_cpu_pct", 55);
                        if (pct < light) gEmuCpuLightSecs.fetch_add(1); else gEmuCpuLightSecs.store(0);
                    }
                    sPrevTicks = ticks;
                }
            }
            fclose(f);
        }
    }
    char v[PROPERTY_VALUE_MAX] = {};
    property_get("sys.gammaos.drastic_nano.emu_cpus", v, "");
    const unsigned emuMask = v[0] ? (unsigned)strtoul(v, nullptr, 16) : 0;
    property_get("sys.gammaos.drastic_nano.worker_cpus", v, "");
    const unsigned workerMask = v[0] ? (unsigned)strtoul(v, nullptr, 16) : 0;
    property_get("sys.gammaos.drastic_nano.render_cpus", v, "");
    const unsigned renderMask = v[0] ? (unsigned)strtoul(v, nullptr, 16) : 0;
    if (!emuMask && !workerMask && !renderMask) return;
    auto setMask = [](pid_t tid, unsigned mask) {
        if (!mask) return;
        cpu_set_t cs; CPU_ZERO(&cs);
        for (int c = 0; c < 8; c++) if (mask & (1u << c)) CPU_SET(c, &cs);
        sched_setaffinity(tid, sizeof(cs), &cs);
    };
    const pid_t self = getpid();
    setMask(self, renderMask);
    if (mEmuTid > 0) setMask(mEmuTid, emuMask);
    DIR* d = opendir("/proc/self/task");
    if (!d) return;
    struct dirent* e;
    while ((e = readdir(d))) {
        if (e->d_name[0] == '.') continue;
        const pid_t tid = (pid_t)atoi(e->d_name);
        if (tid == self || tid == mEmuTid) continue;
        char path[64], comm[32] = {};
        snprintf(path, sizeof(path), "/proc/self/task/%d/comm", tid);
        FILE* f = fopen(path, "r");
        if (f) { if (!fgets(comm, sizeof(comm), f)) comm[0] = 0; fclose(f); }
        // drastic's own threads carry the process name; ours are named dn-*
        if (strncmp(comm, "drastic-nano", 12) == 0) setMask(tid, workerMask);
    }
    closedir(d);
}

// Per-frame audio submit probe (cave on the frame loop's bl +0x1dd6c at
// +0x2cc20): histogram of the samples each emulated frame hands over
// ([ctx+0x4000c], nominally 1470 = 735 stereo) and the queue-full drops
// (drastic skips the frame when its queued count reaches the maximum).
std::atomic<uint32_t> gAudSubmitCalls{0}, gAudSubmitNominal{0}, gAudSubmitShort{0}, gAudSubmitLong{0}, gAudSubmitDropped{0};
std::atomic<uint32_t> gAudSubmitMin{0xffffffff}, gAudSubmitMax{0};
// Frame normalisation (audio_frame_fix, default on). drastic's mixer
// (+0x72764) turns the ARM9 cycles elapsed since its last call into samples
// with an exact fractional accumulator, so a frame whose boundary landed a
// few hundred cycles later than usual mixes 736 stereo samples and the next
// one 734; the total is exact. The submit (+0x1dd6c) copies count*2 bytes but
// always advances the chunk by 735 samples, so a 734 frame leaves one stale
// sample (from the chunk's previous lap, 267 ms old) and a 736 frame writes
// one that is overwritten. Measured on Golden Sun slot 1 with a microphone
// on the speaker: the crackle bursts coincide exactly with runs of those
// frames. Here every frame is made exactly 1470 shorts: a surplus is held
// back and re-seeded into the emptied frame buffer after the submit; a small
// deficit is padded by repeating the last stereo sample (the held surplus
// then normally covers the next deficit); a large deficit (state load) is
// padded with silence instead of stale audio.
std::atomic<uint32_t> gAudFixCarried{0}, gAudFixPadded{0}, gAudFixSilenced{0}, gAudFixDropped{0};
std::atomic<bool> gClockMatchOn{false};      // the submit-hook clock match is active (gates the frame-skip hold)
std::atomic<int>  gClockMatchRatioPpm{0};   // current trim, ppm below 1.0 (telemetry)
std::atomic<uint32_t> gClockMatchSkips{0};   // submits handed to drastic's discard path by the clock match
std::atomic<int>  gClockMatchAvgX100{150};   // last 4 s average queue depth, chunks x100 (gates the emergency top-up)
static int16_t gAudCarry[64]; static uint32_t gAudCarryN = 0;   // emulator thread only
static int sAudFrameFix = -1;
extern "C" void raAudioSubmitPost(uint8_t* ctx) {
    // The submit zeroed the count (both its copy and its drop path).
    if (gAudCarryN == 0) return;
    if (*reinterpret_cast<uint32_t*>(ctx + 0x4000c) != 0) { gAudCarryN = 0; return; }   // unexpected: do not corrupt
    memcpy(ctx, gAudCarry, gAudCarryN * sizeof(int16_t));
    *reinterpret_cast<uint32_t*>(ctx + 0x4000c) = gAudCarryN;
    gAudCarryN = 0;
}
std::atomic<uint32_t> gAudSkipped{0};   // frames drastic discards itself (skip byte at ctx+0x40027)
extern "C" void raAudioSubmitHook(uint8_t* ctx) {
    // Native-rate resampler: with native_mix the core mixes at 32729 Hz and hands over ~546 stereo frames per video frame;
    // convert each chunk to 44100 Hz here (before the frame-size normaliser and the dump). 16-tap Hann-windowed sinc,
    // history of the last 16 input frames carried across chunks, phase accumulated in 32.32 so long runs do not drift.
    if (gNativeMix) {
        // Each submitted chunk is one video frame of audio in DraStic's time model (the pacer consumes 735 frames at 44.1 kHz
        // per video frame), so map every chunk of nin native frames onto exactly 735 output frames: step = nin/735 per chunk
        // (546/735 is 0.095% off the true 32729/44100, 1.6 cents, and it removes the variable-count carry/pad entirely).
        // Outputs whose kernel needs frames beyond the chunk are deferred to the next call; a 16-frame silent pre-roll makes
        // the FIFO always hold a full 735, so the count is a constant 1470 and the normaliser below never engages.
        const uint32_t m = *reinterpret_cast<uint32_t*>(ctx + 0x4000c) & 0x7fffffffu;   // stereo samples (2 per frame)
        // Default: play the 32824 Hz mix as-is and let AudioFlinger resample it (native_resample=0). The old submit-hook
        // sinc resampler (native_resample=1) is kept only as a fallback; on this SoC it could not finish before the
        // OpenSL refill callback and starved the queue (choppy audio) at any useful tap count.
        static int sResample = -1; if (sResample < 0) sResample = property_get_int32("persist.gammaos.drastic_nano.native_resample", 0);
        if (sResample && m >= 2 && m < 0x8000 && ctx[0x40027] == 0) {
            enum { H = 32, OUTF = 735, PRE = 32, MAXPEND = 64, FIFOSZ = 2048, TAPMAX = 32 };
            static int TAPS = -1, LB = 0, LA = 0;
            if (TAPS < 0) { int t = property_get_int32("persist.gammaos.drastic_nano.native_taps", 16); if (t < 2) t = 2; if (t > TAPMAX) t = TAPMAX; t &= ~1; TAPS = t; LB = t / 2 - 1; LA = t / 2 + 1; }
            static int16_t hist[H][2] = {};
            static int64_t pos = 0;                     // 32.32 centre of this chunk's first output, relative to buf[0]
            static int64_t pend[MAXPEND]; static int npend = 0;   // (unused with the constant-ratio loop; outputs simply wait for lookahead)
            static int16_t fifo[FIFOSZ][2]; static int nfifo = 0;
            static float win[TAPMAX][256];
            static bool init = false;
            if (!init) { init = true;
                for (int ph = 0; ph < 256; ph++) { double sum = 0; for (int k = 0; k < TAPS; k++) { double x = (k - LB) - ph / 256.0; double w = 0.5 + 0.5 * cos(3.14159265358979 * x / (TAPS / 2.0)); double v = (fabs(x) < 1e-9) ? 1.0 : sin(3.14159265358979 * x) / (3.14159265358979 * x); win[k][ph] = (float)(v * w); sum += v * w; } for (int k = 0; k < TAPS; k++) win[k][ph] /= (float)sum; }
                pos = (int64_t)H << 32;                 // first centre on the first real frame (after the zero history)
                memset(fifo, 0, sizeof(int16_t) * 2 * PRE); nfifo = PRE;
            }
            const int16_t* in = reinterpret_cast<const int16_t*>(ctx); const uint32_t nin = m / 2 < 8192 ? m / 2 : 8192;
            static int16_t buf[H + 8192][2]; memcpy(buf, hist, sizeof(hist)); for (uint32_t i = 0; i < nin; i++) { buf[H + i][0] = in[i * 2]; buf[H + i][1] = in[i * 2 + 1]; }
            const int64_t total = (int64_t)H + nin;
            // Deinterleave the whole chunk to contiguous float once (not TAPS times per output): the tap loop then reduces
            // two contiguous float arrays, which the compiler auto-vectorises. Cuts the per-frame resample cost enough that
            // 16 taps has the headroom 8 taps did not (the audio thread must finish before the OpenSL refill callback).
            static float bufL[H + 8192], bufR[H + 8192];
            for (int64_t i = 0; i < total; i++) { bufL[i] = buf[i][0]; bufR[i] = buf[i][1]; }
            auto emit = [&](int64_t c) {
                const int64_t ci = c >> 32; const int ph = (int)(((uint64_t)c & 0xffffffffu) >> 24);
                const float* bl = &bufL[ci - LB]; const float* br = &bufR[ci - LB];
                float l = 0, r = 0; for (int k = 0; k < TAPS; k++) { const float w = win[k][ph]; l += bl[k] * w; r += br[k] * w; }
                if (nfifo < FIFOSZ) { long vl = lrintf(l), vr = lrintf(r); fifo[nfifo][0] = (int16_t)(vl > 32767 ? 32767 : vl < -32768 ? -32768 : vl); fifo[nfifo][1] = (int16_t)(vr > 32767 ? 32767 : vr < -32768 ? -32768 : vr); nfifo++; }
            };
            // Constant ratio (32824/44100) with a slow FIFO-level trim instead of a per-chunk warp: the mixer hands over
            // 547 or 548 frames per chunk, and warping each chunk onto 735 would flutter the pitch by 0.09% at 60 Hz
            // (sidebands -19 dB on a 15 kHz line). The level loop corrects the residual 0.02 frame/chunk drift with a
            // pitch trim of at most 0.02% (time constant ~2 s), inaudible and phase-continuous.
            static double stepD = 32824.0 / 44100.0;
            const int level = nfifo - PRE;               // frames beyond the pre-roll after the last emit (0 = on target)
            double trim = level * 1.0e-5; if (trim > 2.0e-4) trim = 2.0e-4; else if (trim < -2.0e-4) trim = -2.0e-4;   // too many buffered -> larger step (fewer outputs per input)
            stepD = (32824.0 / 44100.0) * (1.0 + trim);
            const int64_t step = (int64_t)(stepD * 4294967296.0);
            for (int i = 0; i < npend; i++) emit(pend[i]);   // last chunk's tail, its lookahead has arrived
            npend = 0;
            while (true) {
                const int64_t ci = pos >> 32; if (ci + LA >= total) break;
                emit(pos); pos += step;
            }
            const int64_t keep = total - H; memcpy(hist, &buf[keep], sizeof(hist)); pos -= keep << 32;
            const int nout = nfifo < OUTF ? nfifo : OUTF;
            memcpy(ctx, fifo, (size_t)nout * 2 * sizeof(int16_t)); nfifo -= nout; memmove(fifo, &fifo[nout], (size_t)nfifo * 2 * sizeof(int16_t));
            *reinterpret_cast<uint32_t*>(ctx + 0x4000c) = ((uint32_t)nout * 2u) | (*reinterpret_cast<uint32_t*>(ctx + 0x4000c) & 0x80000000u);
            gNativeOut.fetch_add((uint32_t)nout, std::memory_order_relaxed); gNativeIn.fetch_add(nin, std::memory_order_relaxed);
            {   // 1 Hz rate diagnostic: mixer input, resampler output, and the final submitted count vs the OpenSL queue
                static int64_t t0 = 0; static uint32_t in0 = 0, out0 = 0, sub0 = 0, calls = 0; static uint64_t subAcc = 0;
                subAcc += (uint32_t)nout; calls++;
                const int64_t now = (int64_t)(clock() * 1000000LL / CLOCKS_PER_SEC);
                struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts); const int64_t mono = ts.tv_sec * 1000000LL + ts.tv_nsec / 1000;
                if (t0 == 0) t0 = mono;
                if (mono - t0 >= 1000000) {
                    const double dt = (mono - t0) / 1000000.0;
                    const uint32_t inR = (uint32_t)((gNativeIn.load() - in0) / dt), outR = (uint32_t)((gNativeOut.load() - out0) / dt);
                    const uint32_t q = gAudLibBase ? *reinterpret_cast<volatile uint32_t*>(gAudLibBase + 0x3c7d070) : 0;
                    ALOGW("NATIVERATE in=%u/s out=%u/s calls=%u/s avgnin=%u avgnout=%u queue=%u", inR, outR, (uint32_t)(calls / dt), inR / (calls ? (uint32_t)(calls / dt) : 1), outR / (calls ? (uint32_t)(calls / dt) : 1), q);
                    t0 = mono; in0 = gNativeIn.load(); out0 = gNativeOut.load(); calls = 0; subAcc = 0;
                }
            }
            { uint32_t v = gNativeMinIn.load(); while (nin < v && !gNativeMinIn.compare_exchange_weak(v, nin)) {} v = gNativeMaxIn.load(); while (nin > v && !gNativeMaxIn.compare_exchange_weak(v, nin)) {} }
            if (nin < 545 || nin > 549) gNativeOdd.fetch_add(1, std::memory_order_relaxed);
            if (nout < OUTF) gNativeShort.fetch_add(1, std::memory_order_relaxed);
        }
    }
    // Clock match (persist.gammaos.drastic_nano.clock_match, default 1). Production is 735 frames per
    // emulated frame at the panel's vblank rate; the OpenSL sink drains a hair slower (measured ~0.28% on the
    // 59.83 Hz panel: once the queue reached its ceiling a ~735-frame submit was dropped every ~6 s, a click,
    // and the frame-skip "hold" meant to cancel it is a 16.7 ms discontinuity itself). Match the two clocks
    // continuously: every chunk is resampled by a ratio (trimmed by a slow loop on the 2 s average queue
    // depth) into a FIFO, and each call hands drastic EXACTLY 735 frames from that FIFO, so the frame-size
    // normaliser below stays inert (it would otherwise pad any shorter chunk back to 1470 and undo the trim,
    // which is what happened with a plain in-place resample). About once per 1/(1-ratio) frames the FIFO is
    // short of 735: that call sets drastic's own skip byte (ctx+0x40027, read first thing by the submit at
    // +0x1dd84 and branched to its discard path, the same flag its state-load path uses), so that one submit
    // is dropped and the byte is cleared on the next call. The FIFO content is continuous, so the dropped
    // submit is not a gap, just fewer bytes delivered: that is the rate match. Trim only (ratio <= 1, one
    // submit per frame at most); a deficit stays with the queued==0 emergency top-up. At ~0.3% linear
    // interpolation is sub-LSB and needs no filter; ~1470 lerps per frame. Skipped under native_mix.
    static int sClockMatch = -1; static double sRatioBase = 1.0, sRatioKp = 0.0, sRatioKi = 0.0, sTargetQ = 1.5;
    if (sClockMatch < 0) {
        sClockMatch = property_get_int32("persist.gammaos.drastic_nano.clock_match", 1);
        sRatioBase = 1.0 - property_get_int32("persist.gammaos.drastic_nano.clock_match_ppm", 2700) * 1e-6;
        sRatioKp = property_get_int32("persist.gammaos.drastic_nano.clock_match_kp_ppm", 120) * 1e-6;   // per chunk of error
        sRatioKi = property_get_int32("persist.gammaos.drastic_nano.clock_match_ki_ppm", 8) * 1e-6;     // per chunk, per window
        // Target queue depth in 67 ms chunks. maxq is 4 INCLUDING the chunk playing and the counter toggles
        // +-1 per chunk, so an average above ~2 lets the peak touch the ceiling (a dropped submit); 1.5
        // reads 1..2: never empty, never full.
        sTargetQ = property_get_int32("persist.gammaos.drastic_nano.clock_match_target_x10", 13) / 10.0;
        // 33 ms chunks: the ceiling (8) is far, so the target can sit a little deeper (~107 ms) to keep the
        // trough off zero on a hiccup; still nowhere near a drop.
        if (gAudioChunks8.load(std::memory_order_relaxed))
            sTargetQ = property_get_int32("persist.gammaos.drastic_nano.clock_match_target8_x10", 32) / 10.0;
    }
    static bool sSkipSet = false;
    if (sSkipSet) { ctx[0x40027] = 0; sSkipSet = false; }   // clear the discard we asked for last call
    if (sClockMatch > 0 && !gNativeMix && gAudLibBase) {
        const uint32_t rawIn = *reinterpret_cast<uint32_t*>(ctx + 0x4000c);
        const uint32_t nin = rawIn & 0x7fffffffu;
        if (nin >= 4 && nin < 0x10000 && (nin & 1u) == 0 && ctx[0x40027] == 0) {
            gClockMatchOn.store(true, std::memory_order_relaxed);
            static double sSum = 0; static int sN = 0; static int64_t sWin = 0; static double sInteg = 0, sErr = 0;
            const uint32_t queued = *reinterpret_cast<volatile uint32_t*>(gAudLibBase + 0x3c7d070);
            sSum += queued; sN++;
            const int64_t nowUs = (int64_t)std::chrono::duration_cast<std::chrono::microseconds>(
                    std::chrono::steady_clock::now().time_since_epoch()).count();
            if (sWin == 0) sWin = nowUs;
            if (nowUs - sWin >= 4000000 && sN >= 60) {   // 4 s window: halves the +-1 quantisation noise
                const double avg = sSum / sN; sSum = 0; sN = 0; sWin = nowUs;
                gClockMatchAvgX100.store((int)(avg * 100.0), std::memory_order_relaxed);
                sErr = avg - sTargetQ;                  // positive = too full: produce fewer frames
                sInteg += sErr;
                if (sInteg > 60) sInteg = 60; if (sInteg < -60) sInteg = -60;   // +-480 ppm at ki 8: covers the real surplus
            }
            // The plant is slow (~1 chunk per 60 s per 1000 ppm) and queued is quantised +-1, so the loop is
            // gentle; the integral finds the real effective surplus (the mixer's 734/736 input jitter makes the
            // delivered trim differ from the ratio). Wide safety clamp only: trim 0..5000 ppm.
            double ratio = sRatioBase - sRatioKp * sErr - sRatioKi * sInteg;
            if (ratio < 0.995) ratio = 0.995; if (ratio > 1.0) ratio = 1.0;
            // Starve gate: while the queue is empty (after a state load or at launch) never trim; the
            // emergency top-up is refilling it and removing frames now only prolongs the gaps.
            if (queued < 1) ratio = 1.0;
            gClockMatchRatioPpm.store((int)((1.0 - ratio) * 1e6), std::memory_order_relaxed);
            enum { kFifoFr = 8192 };
            static int16_t sFifo[kFifoFr * 2]; static uint32_t sFifoN = 0;   // frames of continuous resampled audio
            static int16_t sIn[0x10000]; static int16_t sLast[2] = {0, 0}; static double sPhase = 0.0;
            const uint32_t fin = nin / 2;
            memcpy(sIn, ctx, (size_t)nin * sizeof(int16_t));
            const double step = 1.0 / ratio;            // input frames per output frame
            double pos = sPhase;
            while (pos < (double)fin - 1.0 && sFifoN < (uint32_t)kFifoFr) {
                const int i = (int)floor(pos); const double fr = pos - i;
                const int16_t* a = (i < 0) ? sLast : &sIn[(size_t)i * 2];
                const int16_t* b = &sIn[(size_t)(i + 1) * 2];
                for (int ch = 0; ch < 2; ch++) {
                    const long v = lrint(a[ch] + (b[ch] - a[ch]) * fr);
                    sFifo[sFifoN * 2 + ch] = (int16_t)(v > 32767 ? 32767 : (v < -32768 ? -32768 : v));
                }
                sFifoN++; pos += step;
            }
            sLast[0] = sIn[(size_t)(fin - 1) * 2]; sLast[1] = sIn[(size_t)(fin - 1) * 2 + 1];
            sPhase = pos - (double)fin;
            // Burst smoothing: a catch-up runs owed frames back to back, so submits land a few ms apart and
            // the OpenSL queue (4 chunks) overflows and drops one. Emit at most one submit per ~12.5 ms; the
            // extra audio stays in the FIFO and drains over the following frames (nothing is lost). Past a
            // 4-chunk backlog (fast-forward) emit regardless so the FIFO stays bounded.
            static int64_t sLastEmitUs = 0;
            const bool burst = (nowUs - sLastEmitUs) < 12500 && sFifoN < 4u * 735u;
            if (sFifoN >= 735u && !burst) {
                sLastEmitUs = nowUs;
                memcpy(ctx, sFifo, 735u * 2u * sizeof(int16_t));
                sFifoN -= 735u;
                memmove(sFifo, sFifo + 735u * 2u, (size_t)sFifoN * 2u * sizeof(int16_t));
            } else {
                ctx[0x40027] = 1; sSkipSet = true;   // drastic discards this submit; the FIFO keeps the audio
                gClockMatchSkips.fetch_add(1, std::memory_order_relaxed);
            }
            *reinterpret_cast<uint32_t*>(ctx + 0x4000c) = 1470u | (rawIn & 0x80000000u);   // always nominal
        }
    }
    const uint32_t raw = *reinterpret_cast<uint32_t*>(ctx + 0x4000c);
    const uint32_t flag = raw & 0x80000000u;   // bit 31 is a flag the submit masks off; keep it
    uint32_t n = raw & 0x7fffffffu;
    gAudSubmitCalls.fetch_add(1, std::memory_order_relaxed);
    if (gSpuMixCtl && gSpuMixCtl[1] == 0xffffffffu && gAudSubmitCalls.load() >= 2) {
        gSpuMixCtl[1] = gSpuMixLines;   // arm the per-scanline mixing once frame-end mixing has run
        ALOGI("DrasticRunner: SPU mix every %u scanlines armed", gSpuMixLines);
    }
    if (sAudFrameFix < 0) sAudFrameFix = property_get_int32("persist.gammaos.drastic_nano.audio_frame_fix", 1);
    static int sAudDebug = -1;
    if (sAudDebug < 0) sAudDebug = property_get_int32("sys.gammaos.drastic_nano.audio_debug", 0);
    if (ctx[0x40027] != 0) {
        const uint32_t k = gAudSkipped.fetch_add(1, std::memory_order_relaxed);
        if (sAudDebug > 0 && k < 100) ALOGW("AUDIO frame skipped by drastic (call %u) samples=%u", gAudSubmitCalls.load(), n);
    }
    if (sAudDebug > 0 && gAudLibBase) {
        const uint32_t queued = *reinterpret_cast<volatile uint32_t*>(gAudLibBase + 0x3c7d070);
        const uint32_t maxq = *reinterpret_cast<volatile uint32_t*>(gAudLibBase + 0x3c7d07c);
        static uint32_t sDropLogged = 0;
        if (queued >= maxq && sDropLogged++ < 100) ALOGW("AUDIO frame dropped, queue full (%u/%u) call %u", queued, maxq, gAudSubmitCalls.load());
    }
    // Target one video frame's worth of samples: 735 stereo at 44100, or 547 (1094) at the 32824 native-mix rate when
    // native_mix plays through AudioFlinger without the hook resampler.
    const uint32_t nTarget = (gNativeMix && !property_get_bool("persist.gammaos.drastic_nano.native_resample", false)) ? 1094u : 1470u;
    if (sAudFrameFix > 0 && n != nTarget && n < 0x10000) {
        int16_t* pcm = reinterpret_cast<int16_t*>(ctx);
        if (n > nTarget) {
            uint32_t extra = n - nTarget;
            if (extra > 64) { gAudFixDropped.fetch_add(extra - 64, std::memory_order_relaxed); extra = 64; }
            memcpy(gAudCarry, pcm + nTarget, extra * sizeof(int16_t));
            gAudCarryN = extra;
            gAudFixCarried.fetch_add(1, std::memory_order_relaxed);
        } else if (n >= nTarget - 16 && n >= 2) {
            for (uint32_t i = n; i < nTarget; i += 2) { pcm[i] = pcm[n - 2]; pcm[i + 1] = pcm[n - 1]; }
            gAudFixPadded.fetch_add(1, std::memory_order_relaxed);
        } else {
            memset(pcm + n, 0, (nTarget - n) * sizeof(int16_t));
            gAudFixSilenced.fetch_add(1, std::memory_order_relaxed);
        }
        *reinterpret_cast<uint32_t*>(ctx + 0x4000c) = nTarget | flag;
    }
    // Diagnostic: replace the frame with a continuous synthetic tone
    // (audio_tone=1) so the rest of the output path can be judged on its own.
    {
        static int sTone = -1; static double sPhase = 0;
        if (sTone < 0) sTone = property_get_int32("sys.gammaos.drastic_nano.audio_tone", 0);
        if (sTone > 0) {
            int16_t* pcm = reinterpret_cast<int16_t*>(ctx);
            const uint32_t m = *reinterpret_cast<uint32_t*>(ctx + 0x4000c) & 0x7fffffffu;
            for (uint32_t i = 0; i + 1 < m && i < 0x10000; i += 2) {
                const double v = 0.15 * (sin(sPhase) + 0.5 * sin(sPhase * 1.5) + 0.3 * sin(sPhase * 2.0));
                pcm[i] = pcm[i + 1] = (int16_t)(v * 32767.0);
                sPhase += 2.0 * M_PI * 220.0 / 44100.0;
                if (sPhase > 2.0 * M_PI * 1000.0) sPhase -= 2.0 * M_PI * 1000.0;
            }
        }
    }
    // 16 kHz output band limit (persist.gammaos.drastic_nano.lowpass). DraStic mixes at 44.1 kHz with
    // nearest-neighbour per-channel resampling, so imaging lands anywhere up to 22 kHz and reaches the
    // speaker; real DS hardware mixes at 32.7 kHz and physically cannot output above its 16.4 kHz Nyquist,
    // and melonDS band-limits its native mix on the way out. That out-of-band imaging is the "grinding"
    // heard on Golden Sun DD's speech. Measured on the speech: harshness ratio HF(6-20k)/MF(1-6k)
    // -14.8 dB unfiltered vs melonDS -16.4; with this filter -16.0 (melonDS's own mix brought to 44.1 kHz
    // by nearest neighbour measures -13.3, proving the difference is band-limiting, not the mixer).
    // Elliptic order 8, 0.2 dB ripple, 70 dB stop, fc 16 kHz: flat to 16 kHz (-0.2 dB), -58 dB at
    // 17 kHz, -70 dB at 18 kHz. Four transposed direct-form-II biquads per channel, state kept across
    // chunks. Runs before AudioFlinger, so the HAL/EQ chain is untouched.
    {
        static int sLowpass = -1;
        static double sLpz[2][4][2] = {};
        static const double kLpSos[4][6] = {
            {0.117591635888, 0.231334590813, 0.117591635888, 1, -0.044855136197, 0.118892690289},
            {1, 1.78076605039, 1, 1, 0.683890483871, 0.561995737842},
            {1, 1.61010735757, 1, 1, 1.11201121842, 0.828723891871},
            {1, 1.52945492835, 1, 1, 1.28681505201, 0.955182739885},
        };
        if (sLowpass < 0) sLowpass = property_get_bool("persist.gammaos.drastic_nano.lowpass", false) ? 1 : 0;
        if (sLowpass) {
            const uint32_t m = *reinterpret_cast<uint32_t*>(ctx + 0x4000c) & 0x7fffffffu;
            if (m < 0x10000 && ctx[0x40027] == 0) {
                int16_t* pcm = reinterpret_cast<int16_t*>(ctx);
                for (uint32_t i = 0; i + 1 < m; i += 2) {
                    for (int ch = 0; ch < 2; ch++) {
                        double x = pcm[i + ch];
                        for (int sct = 0; sct < 4; sct++) {
                            const double* c = kLpSos[sct]; double* z = sLpz[ch][sct];
                            const double y = c[0] * x + z[0];
                            z[0] = c[1] * x - c[4] * y + z[1];
                            z[1] = c[2] * x - c[5] * y;
                            x = y;
                        }
                        const long v = lrint(x);
                        pcm[i + ch] = (int16_t)(v > 32767 ? 32767 : (v < -32768 ? -32768 : v));
                    }
                }
            }
        }
    }
    // Diagnostic: dump the exact stream handed to the chunk (audio_dump=path).
    {
        static FILE* sDump = nullptr; static int sDumpTried = 0;
        if (!sDumpTried) {
            sDumpTried = 1;
            char path[PROP_VALUE_MAX] = {0};
            if (property_get("sys.gammaos.drastic_nano.audio_dump", path, "") > 0) sDump = fopen(path, "wb");
        }
        if (sDump) {
            const uint32_t m = *reinterpret_cast<uint32_t*>(ctx + 0x4000c) & 0x7fffffffu;
            if (m < 0x10000 && ctx[0x40027] == 0) fwrite(ctx, 2, m, sDump);
        }
    }
    if (n != 1470 && sAudDebug > 0) {
        static std::atomic<uint32_t> sLogged{0};
        if (sLogged.fetch_add(1) < 60)
            ALOGW("AUDIO frame samples=%u (call %u) queued=%u vbl=%u parked=%d burst=%d holdseq=%u", n, gAudSubmitCalls.load(),
                  gAudLibBase ? *reinterpret_cast<volatile uint32_t*>(gAudLibBase + 0x3c7d070) : 0u, gVblSeq.load(),
                  gEmuParked.load() ? 1 : 0, gRaBurst.load() ? 1 : 0, gAudioHoldSeq.load());
    }
    if (n == 1470) gAudSubmitNominal.fetch_add(1, std::memory_order_relaxed);
    else if (n < 1470) gAudSubmitShort.fetch_add(1, std::memory_order_relaxed);
    else gAudSubmitLong.fetch_add(1, std::memory_order_relaxed);
    uint32_t mn = gAudSubmitMin.load(); while (n < mn && !gAudSubmitMin.compare_exchange_weak(mn, n)) {}
    uint32_t mx = gAudSubmitMax.load(); while (n > mx && !gAudSubmitMax.compare_exchange_weak(mx, n)) {}
    if (gAudLibBase) {
        const uint32_t queued = *reinterpret_cast<volatile uint32_t*>(gAudLibBase + 0x3c7d070);
        const uint32_t maxq = gAudioChunks8.load(std::memory_order_relaxed) ? 8u : *reinterpret_cast<volatile uint32_t*>(gAudLibBase + 0x3c7d07c);
        if (queued >= maxq) gAudSubmitDropped.fetch_add(1, std::memory_order_relaxed);
    }
}
// Refill callback probe (cave at +0x1d650): an entry with nothing queued
// means drastic hands OpenSL a silence chunk (67 ms gap) = one underrun.
std::atomic<uint32_t> gAudCallbacks{0}, gAudUnderruns{0};
extern "C" void raAudioCallbackHook() {
    gAudCallbacks.fetch_add(1, std::memory_order_relaxed);
    if (!gAudLibBase) return;
    if (*reinterpret_cast<volatile uint32_t*>(gAudLibBase + 0x3c7d074) != 0) return;   // output stopped
    const uint32_t queued = *reinterpret_cast<volatile uint32_t*>(gAudLibBase + 0x3c7d070);
    if (queued == 0) {
        const uint32_t k = gAudUnderruns.fetch_add(1, std::memory_order_relaxed);
        if (k < 200 && property_get_int32("sys.gammaos.drastic_nano.audio_debug", 0) > 0)
            ALOGW("AUDIO underrun %u at vbl=%u parked=%d lost=%u catchups=%u misses=%u", k + 1, gVblSeq.load(),
                  gEmuParked.load() ? 1 : 0, gEmuLostTicks.load(), gEmuCatchUps.load(), gMissCount.load());
    }
}
// Audio lead state (see audioLeadExtraTick).
constexpr uintptr_t kAudioQueuedOff = 0x3c7d070;
std::atomic<int> gAudioLeadDebt{0};
std::atomic<uint32_t> gAudioLeadExtra{0}, gAudioLeadTopUps{0}, gAudioLeadHolds{0};

void DrasticRunner::vblankTick(int64_t vblankUs, int64_t gpuDoneUs) {
    if (vblankUs <= 0) return;
    applyCpuPlacement();
    // Heavy games: if the emulated frame takes longer than the lock can
    // absorb, hand the emulator back to its own timer (it then runs as fast
    // as it can, which is faster than one tick per period) and probe the
    // lock again every ten seconds.
    {
        static int64_t sBypassSinceUs = 0, sProbeSinceUs = 0;
        // Threshold is a runtime prop for experiments: 0 forces the bypass,
        // a very large value disables it. Re-read once a second.
        static int64_t sBypassUs = 12500, sBypassReadUs = 0;
        if (vblankUs - sBypassReadUs > 1000000) {
            sBypassReadUs = vblankUs;
            sBypassUs = property_get_int32("sys.gammaos.drastic_nano.pace_bypass_us", 12500);
        }
        const int64_t emu = gEmuDurUs.load();
        // The bypass stays immediate (a lock that cannot hold costs presented
        // frames: White 2 measured 54 fps locked vs 59.8 bypassed); scenes
        // that merely hiccuped recover through the run-ahead retry below.
        static int sOverCount = 0;
        static int64_t sRaRetryUs = 4000000, sLockedSinceUs = 0;
        if (gPaceOn.load() && emu > sBypassUs) sOverCount++; else sOverCount = 0;
        if (gPaceOn.load() && !gPaceBypass.load()) {
            if (sLockedSinceUs == 0) sLockedSinceUs = vblankUs;
            if (vblankUs - sLockedSinceUs > 10000000) sRaRetryUs = 4000000;   // held 10 s: reset the backoff
        } else {
            sLockedSinceUs = 0;
        }
        // With run-ahead on the lock is retried on heavy scenes; if it then
        // costs presented frames (pacing misses), drop it again and back off.
        static uint32_t sMissBase = 0; static int64_t sMissWindowUs = 0; static bool sMissTrip = false;
        if (vblankUs - sMissWindowUs > 1000000) {
            const uint32_t m = gMissCount.load();
            sMissTrip = gPaceOn.load() && !gPaceBypass.load() && gRaMode.load() == 2 && (m - sMissBase) >= 4;
            sMissBase = m; sMissWindowUs = vblankUs;
        }
        if (!gPaceBypass.load()) {
            if (gPaceOn.load() && (emu > sBypassUs || sMissTrip) && !gStepMode.load()) {
                sMissTrip = false;
                gPaceBypass.store(true); sBypassSinceUs = vblankUs; sProbeSinceUs = 0;
                if (sRaRetryUs < 64000000) sRaRetryUs *= 2;
                ALOGW("PACE bypass: emulator %lld us per frame", (long long)emu);
                setVblankPacing(mPaceWanted);
            }
        } else if (sProbeSinceUs == 0 && vblankUs - sBypassSinceUs > 3000000 &&
                   (gEmuCpuLightSecs.load() >= 3 ||
                    // run-ahead needs the lock: retry regardless of load, with
                    // exponential backoff (4, 8, 16 .. 64 s) so a scene that
                    // cannot hold the lock is not stuttered every few seconds
                    (gRaMode.load() == 2 && vblankUs - sBypassSinceUs > sRaRetryUs) ||
                    (property_get_int32("sys.gammaos.drastic_nano.pace_probe_ms", 0) > 0 &&
                     vblankUs - sBypassSinceUs > 1000LL * property_get_int32("sys.gammaos.drastic_nano.pace_probe_ms", 0)))) {
            // probe: the emulator thread has been light for three seconds (or
            // the optional timed probe fired): re-enable the lock and see if
            // the emulator keeps up. A timed probe on a scene that cannot hold
            // the lock costs a second at 30 fps every time, so it is off by
            // default.
            gPaceBypass.store(false); sProbeSinceUs = vblankUs;
            gEmuDurUs.store(6000);
            gEmuCpuLightSecs.store(0);
            ALOGW("PACE probe: emulator thread at %d%% of a core, trying the lock", gEmuCpuPct.load());
            setVblankPacing(mPaceWanted);
        } else if (sProbeSinceUs != 0) {
            if (emu > sBypassUs && vblankUs - sProbeSinceUs > 500000) {
                gPaceBypass.store(true); sBypassSinceUs = vblankUs; sProbeSinceUs = 0;
                setVblankPacing(mPaceWanted);
            } else if (vblankUs - sProbeSinceUs > 2000000) {
                sProbeSinceUs = 0;   // probe passed: stay locked
                ALOGW("PACE lock restored: emulator %lld us per frame", (long long)emu);
            }
        }
    }
    // GPU margin controller: creep the lead later while the render finishes
    // comfortably before the vblank, back it off as soon as it gets tight.
    // Runs only while the emulator is producing frames.
    if (gpuDoneUs > 0 && gSteadyFrames.load() >= 120) {
        const int64_t margin = vblankUs - gpuDoneUs;
        static int64_t sMinMargin = 1 << 30; static int nMargin = 0;
        if (margin < sMinMargin) sMinMargin = margin;
        if (++nMargin >= 20) {
            const int64_t tight = property_get_int32("sys.gammaos.drastic_nano.pace_margin_tight_us", 1500);
            const int64_t comfy = property_get_int32("sys.gammaos.drastic_nano.pace_margin_ok_us", 3000);
            if (sMinMargin < tight) {
                const int64_t at = gLeadUs.load();
                gLeadUs.store(at + 500);
                if (at + 250 > gLeadCreepFloor.load()) gLeadCreepFloor.store(at + 250);
                gLeadFloorRelaxAt.store(gVblSeq.load() + 3600);
                ALOGW("PACE tight margin %lld us at lead %lld", (long long)sMinMargin, (long long)at);
            }
            gMarginOk.store(sMinMargin >= comfy);
            sMinMargin = 1 << 30; nMargin = 0;
        }
    }
    {
        static int64_t sLastStatUs = 0;
        if (vblankUs - sLastStatUs >= 1000000) {
            sLastStatUs = vblankUs;
            audioRateApply();
            static int sAudStatN = 0;
            if (++sAudStatN % 10 == 0)
                ALOGW("AUDIO frames=%u nominal=%u short=%u long=%u min=%u max=%u dropped=%u callbacks=%u underruns=%u "
                      "lostticks=%u catchups=%u debtdrops=%u rcatchups=%u stalls=%u/%u(off/on) stallmax=%u/%ums clockppm=%d cmskips=%u skipped=%u fix: carried=%u padded=%u silenced=%u dropped=%u spumix=%u",
                      gAudSubmitCalls.load(), gAudSubmitNominal.load(), gAudSubmitShort.load(), gAudSubmitLong.load(),
                      gAudSubmitMin.load(), gAudSubmitMax.load(), gAudSubmitDropped.load(),
                      gAudCallbacks.load(), gAudUnderruns.load(), gEmuLostTicks.load(), gEmuCatchUps.load(), gEmuDebtDrops.load(),
                      gEmuRenderCatchUps.load(), gStallOffCpu.load(), gStallOnCpu.load(), gStallMaxWallMs.load(), gStallMaxCpuMs.load(), gClockMatchRatioPpm.load(), gClockMatchSkips.load(),
                      gAudSkipped.load(), gAudFixCarried.load(), gAudFixPadded.load(), gAudFixSilenced.load(), gAudFixDropped.load(),
                      gSpuMixCtl ? gSpuMixCtl[2] : 0u);
                if (gRingRepairCalls.load()) ALOGW("DrasticRunner: RINGREPAIR calls=%u filled=%u", gRingRepairCalls.load(), gRingRepairFilled.load());
                if (gAdpcmWraps) ALOGW("DrasticRunner: ADPCMLOOP wraps=%u", gAdpcmWraps);
                if (gNativeMix) ALOGW("DrasticRunner: NATIVEMIX in=%u out=%u ratio=%.4f nin=%u..%u odd=%u short=%u", gNativeIn.load(), gNativeOut.load(), gNativeIn.load() ? (double)gNativeOut.load() / gNativeIn.load() : 0.0, gNativeMinIn.load(), gNativeMaxIn.load(), gNativeOdd.load(), gNativeShort.load());
                if (gHwRouteMixes.load()) ALOGW("DrasticRunner: HWROUTE mixes=%u captured=%u soundcnt=0x%04x", gHwRouteMixes.load(), gHwRouteCapt.load(), gHwLastCnt);
                if (gSpuTrace && property_get_int32("sys.gammaos.drastic_nano.spu_trace_dump", 0) == 1) {
                    FILE* tf = fopen("/data/local/tmp/spu_trace.bin", "wb");
                    uint32_t n = gSpuTraceN.load(); uint32_t first = n > kSpuTraceCap ? n - kSpuTraceCap : 0;
                    for (uint32_t i = first; tf && i < n; i++) fwrite(&gSpuTrace[i % kSpuTraceCap], sizeof(SpuTraceRec), 1, tf);
                    if (tf) fclose(tf);
                    if (gSpuMini && (tf = fopen("/data/local/tmp/spu_mini.bin", "wb")) != nullptr) { fwrite(gSpuMini, sizeof(SpuMini), gSpuMiniN, tf); fclose(tf); }
                    if (gRefillLog && (tf = fopen("/data/local/tmp/refill_log.bin", "wb")) != nullptr) { uint32_t cnt = *reinterpret_cast<uint32_t*>(gRefillLog + 8); fwrite(gRefillLog + 16, 16, cnt, tf); fclose(tf); ALOGW("DrasticRunner: refill log dumped %u entries", cnt); }
                    if (gWrapLog && (tf = fopen("/data/local/tmp/wrap_log.bin", "wb")) != nullptr) { uint32_t cnt = *reinterpret_cast<uint32_t*>(gWrapLog + 8); fwrite(gWrapLog + 16, 32, cnt, tf); fclose(tf); ALOGW("DrasticRunner: wrap log dumped %u entries", cnt); }
                    if (gFetchLog && (tf = fopen("/data/local/tmp/fetch_log.bin", "wb")) != nullptr) { uint32_t cnt = *reinterpret_cast<uint32_t*>(gFetchLog + 8); fwrite(gFetchLog + 16, 32, 262144, tf); fclose(tf); ALOGW("DrasticRunner: fetch log dumped 262144 entries (circular, next write index %u)", cnt & 262143u); }
                    property_set("sys.gammaos.drastic_nano.spu_trace_dump", "2");
                    ALOGW("DrasticRunner: SPU trace dumped %u records (first %u)", n - first, first);
                }
            ALOGW("PACE lead=%lld misses=%u floor=%lld hookflips=%u emu=%lld audioq=%u lead+%u topups=%u holds=%u", (long long)gLeadUs.load(),
                  gMissCount.load(), (long long)gLeadCreepFloor.load(), gFlipHookCount.load(),
                  (long long)gEmuDurUs.load(),
                  mArm64Base ? *reinterpret_cast<volatile uint32_t*>(mArm64Base + kAudioQueuedOff) : 0u,
                  gAudioLeadExtra.load(), gAudioLeadTopUps.load(), gAudioLeadHolds.load());
            if (mT3dSyncInstalled && gT3dMode >= 5)
                ALOGW("PACE t3d5 chunks=%u waited=%u sum=%lld max=%lld start=%u startus=%lld idle=%u full=%u to=%u bands=%u",
                      gT3dPipe.chunks, gT3dPipe.waited, (long long)gT3dPipe.sumUs, (long long)gT3dPipe.maxUs,
                      gT3dPipe.startWaits, (long long)gT3dPipe.sumStartUs, gT3dPipe.idleSkips,
                      gT3dPipe.fullWaits, gT3dPipe.timeouts, __atomic_load_n(&gT3dBandMask, __ATOMIC_RELAXED));
            else if (mT3dSyncInstalled)
                ALOGW("PACE t3d joins=%u partial=%u blocked=%u ajoins=%u ablocked=%u split=%u "
                      "whole=%u ffree=%u fwait=%u fskip=%u ema=%lld max=%lld sum=%lld sw=%u lag=%d tog=%u alt=%d cap=%u/%u modeA=%u modeB=%u",
                      gT3dStats[0], gT3dStats[1], gT3dStats[2], gT3dStats[3], gT3dStats[4], gT3dStats[5],
                      gT3d.wholeJoins, gT3d.firstFree, gT3d.firstWait, gT3d.firstSkip,
                      (long long)gT3d.emaWaitUs, (long long)gT3d.maxWaitUs, (long long)gT3d.sumWaitUs,
                      gT3d.switches, gT3d.lagMode ? 1 : 0, gT3d.toggles, gT3d.alternating ? 1 : 0,
                      gT3d.capFrames, gT3d.frames, gT3d.modeA, gT3d.modeB);
        }
    }
    // Creep the lead in while frames land on time: 50 us every 20 frames,
    // floor pace_lead_min_us (default 300).
    {
        static uint32_t clean = 0;
        const uint32_t seq = gVblSeq.load();
        if (gLeadFloorRelaxAt.load() > 0 && seq >= (uint32_t)gLeadFloorRelaxAt.load()) {
            gLeadFloorRelaxAt.store(seq + 3600);
            gLeadCreepFloor.store(gLeadCreepFloor.load() - 250);
        }
        if (gSteadyFrames.load() >= 120 && gMarginOk.load() && seq >= (uint32_t)gLeadHoldUntil.load() && ++clean >= 20) {
            clean = 0;
            // The lead may go negative: the emulator is then ticked after
            // the vblank, using the slack between GPU completion and the
            // next vblank. The miss detector backs it off on any stale or
            // late frame and pins a creep floor just above the edge.
            int64_t floorUs = property_get_int32("sys.gammaos.drastic_nano.pace_lead_min_us", -6000);
            if (gLeadCreepFloor.load() > floorUs) floorUs = gLeadCreepFloor.load();
            int64_t l = gLeadUs.load() - 50;
            if (l < floorUs) l = floorUs;
            gLeadUs.store(l);
        }
    }
    const int64_t prev = gLastVblankUs.load();
    if (prev > 0) {
        const int64_t d = vblankUs - prev;
        // Period estimate from consecutive vblanks (skip if a vblank was
        // missed or the clock jumped).
        if (d > 15000 && d < 18500) {
            const int64_t p = gVblankPeriodUs.load();
            gVblankPeriodUs.store((p * 15 + d + 8) / 16);
        }
    }
    gLastVblankUs.store(vblankUs);
}

// Ticks the emulator pace_lead_us before each expected vblank. When the loop
// stops reporting vblanks (menu, stall) it keeps ticking at the panel period
// from the last one, so the game keeps full speed rather than slowing down.
// Audio lead. drastic hands its output queue 4-frame (67 ms) chunks and the
// queue holds at most 4 of them; under vblank pacing the emulator runs exactly
// real time, so each chunk lands as the previous one ends and the queue sits
// at 0 or 1 (measured: 0 in 63% of samples on Golden Sun). Any late frame then
// leaves the output thread with nothing: a silence gap that AudioFlinger does
// not count as an underrun (the crackle). Stock drastic only avoids it because
// its unpaced loop runs ahead until the queue is full. So the pacer runs the
// emulator audio_lead_frames (default 2) frames ahead once when the lock
// engages, and tops the lead up with one extra frame whenever the queue is
// found empty (rate limited): the picture is then a frame or two ahead of the
// sound, 33 ms, below what people notice, and a late frame no longer opens a
// gap. The queue depth is drastic's own counter at .bss +0x3c7d070.
// Ceiling: drastic drops a whole frame of audio at its submit when the
// queue already holds the maximum (4 chunks). The 0.29% rate surplus and
// the top-ups walk the queue up over time, so when it reaches
// audio_lead_max_queued (default 3) the pacer holds the emulator for one
// vblank (the presenter repeats a frame) instead of letting drastic pop.
// Audio output rate. drastic opens its OpenSL player at 44100 x 59.8261/60 =
// 43971 Hz (the DS's native frame rate) while producing 735 samples per
// emulated frame; on this build the emulator runs 60.000 frames per second
// (drastic's own limiter period is 16666.67 us, and the vblank lock ticks at
// the panel's 60.000 Hz), so 44100 samples arrive per second and the queue
// drifts full at 0.29% per second, where drastic's submit drops frames: the
// popping about a minute into a scene. The rate computation is buried in
// drastic, but the AudioTrack libwilhelm created for the player is in this
// process: find it from drastic's player object (static +0x3c7d050) by its
// vtable and set its sample rate to 44100 (AudioFlinger resamples; the pitch
// change is 0.29%). audio_rate_fix (default on).
std::atomic<bool> gAudioRateApplied{false};
void DrasticRunner::audioRateApply() {
    static int sTries = 0;
    // drastic keeps its OpenSL objects (engine, output mix, player and their
    // interfaces) as a row of pointers in its static block at +0x3c7d008 ..
    // +0x3c7d060; the exact slot of the player varies, so every pointer in
    // the row is a search root. A new set of pointers means a new player.
    static uint64_t sLastSig = 0;
    uint64_t sig = 0;
    if (mArm64Base) for (uintptr_t o = 0x3c7d008; o <= 0x3c7d060; o += 8) sig ^= *reinterpret_cast<uint64_t*>(mArm64Base + o) * (o & 0xff);
    if (sig != sLastSig) { sLastSig = sig; gAudioRateApplied.store(false); sTries = 0; }
    if (gAudioRateApplied.load() || !mArm64Base || sTries > 60) return;
    static int sWant = -1;
    // Off by default: the search never found the track (libwilhelm keeps it
    // behind more indirection) and its thousands of process_vm_readv calls
    // per attempt stalled the pacer once a second for the first minute.
    if (sWant < 0) sWant = property_get_int32("persist.gammaos.drastic_nano.audio_rate_fix", 0) ? property_get_int32("persist.gammaos.drastic_nano.audio_rate_hz", 44100) : 0;
    if (sWant <= 0) { gAudioRateApplied.store(true); return; }
    sTries++;
    typedef int (*setRate_t)(void*, uint32_t);
    typedef uint32_t (*getRate_t)(void*);
    static void* vt = dlsym(RTLD_DEFAULT, "_ZTVN7android10AudioTrackE");
    static setRate_t setRate = reinterpret_cast<setRate_t>(dlsym(RTLD_DEFAULT, "_ZN7android10AudioTrack13setSampleRateEj"));
    static getRate_t getRate = reinterpret_cast<getRate_t>(dlsym(RTLD_DEFAULT, "_ZNK7android10AudioTrack13getSampleRateEv"));
    if (!vt || !setRate) { ALOGW("DrasticRunner: audio rate: AudioTrack symbols not found (vt %p set %p)", vt, (void*)setRate); gAudioRateApplied.store(true); return; }
    const uintptr_t vptr = (uintptr_t)vt + 16;   // Itanium ABI: object vptr points past the offset/typeinfo slots
    if (sTries == 1) ALOGI("DrasticRunner: audio rate: scanning OpenSL object row, AudioTrack vtable %p", vt);
    // Every read of foreign memory goes through process_vm_readv: it fails
    // with EFAULT on unmapped or unreadable pages instead of faulting (mincore
    // alone said "mapped" for PROT_NONE guard pages and crashed the scan).
    auto peek = [](uintptr_t a, uintptr_t* out) {
        if (a < 0x10000 || (a & 7)) return false;
        struct iovec l = { out, sizeof(*out) }, r = { (void*)a, sizeof(*out) };
        return process_vm_readv(getpid(), &l, 1, &r, 1, 0) == (ssize_t)sizeof(*out);
    };
    auto readable = [&](uintptr_t a) { uintptr_t v; return peek(a, &v); };
    auto isTrack = [&](uintptr_t a) { uintptr_t v; return peek(a, &v) && v == vptr; };
    // The player object is a libwilhelm CAudioPlayer. Depending on the
    // wilhelm version the AudioTrack is a direct member or sits one level
    // down (CAudioPlayer -> TrackPlayerBase -> sp<AudioTrack>): scan two levels.
    for (uintptr_t root = 0x3c7d008; root <= 0x3c7d060; root += 8)
    for (size_t off = 0; off < 4096; off += 8) {
        uint8_t* player = *reinterpret_cast<uint8_t**>(mArm64Base + root);
        uintptr_t cand = 0;
        if (!peek((uintptr_t)player + off, &cand)) continue;
        uintptr_t track = 0; size_t off2 = 0;
        if (isTrack(cand)) track = cand;
        else if (readable(cand)) {
            for (size_t o2 = 0; o2 < 1024; o2 += 8) {
                uintptr_t c2 = 0;
                if (!peek(cand + o2, &c2)) break;
                if (isTrack(c2)) { track = c2; off2 = o2; break; }
            }
        }
        if (!track) continue;
        const uint32_t before = getRate ? getRate((void*)track) : 0;
        const int rc = setRate((void*)track, (uint32_t)sWant);
        const uint32_t after = getRate ? getRate((void*)track) : 0;
        ALOGI("DrasticRunner: audio rate: AudioTrack via static+0x%lx -> +0x%zx%s, setSampleRate(%d) rc=%d (rate %u -> %u)",
              (unsigned long)root, off, off2 ? (std::string(" +0x") + std::to_string(off2)).c_str() : "", sWant, rc, before, after);
        gAudioRateApplied.store(true);
        return;
    }
    if (sTries == 60) ALOGW("DrasticRunner: audio rate: AudioTrack not found in the player object");
}

// Queue depth is a coarse counter that toggles between two values every
// 67 ms chunk, so decisions use its 2 s average: above audio_lead_hi (2.6
// chunks) one frame is held, below audio_lead_lo (1.4) one frame is added,
// at most one correction per 2 s. One frame is a quarter chunk, so a
// correction moves the average by 0.25; the 0.29% rate surplus (when the
// AudioTrack rate could not be set) needs a hold about every 6 s.
bool DrasticRunner::audioLeadHoldTick(int64_t nowUs) {
    static double sSum = 0; static int sN = 0; static int64_t sWinStartUs = 0, sLastActUs = 0;
    static int sHi = -1, sLo = -1;
    if (sHi < 0) { sHi = property_get_int32("persist.gammaos.drastic_nano.audio_lead_hi_x10", 26); sLo = property_get_int32("persist.gammaos.drastic_nano.audio_lead_lo_x10", 14); }
    if (!mArm64Base || !gPaceOn.load() || gRaBurst.load()) return false;
    const uint32_t queued = *reinterpret_cast<volatile uint32_t*>(mArm64Base + kAudioQueuedOff);
    sSum += queued; sN++;
    if (sWinStartUs == 0) sWinStartUs = nowUs;
    if (nowUs - sWinStartUs < 2000000 || sN < 30) return false;
    const double avg = sSum / sN;
    sSum = 0; sN = 0; sWinStartUs = nowUs;
    if (gAudioLeadDebt.load() > 0) return false;
    if (avg * 10 > sHi && nowUs - sLastActUs > 2000000) {
        sLastActUs = nowUs; gAudioLeadHolds.fetch_add(1); gAudioHoldSeq.store(gVblSeq.load());
        ALOGW("AUDIO lead hold at vbl=%u (avg queued %.2f)", gVblSeq.load(), avg);
        return true;
    }
    if (avg * 10 < sLo && nowUs - sLastActUs > 2000000) {
        sLastActUs = nowUs; gAudioLeadDebt.store(1); gAudioLeadTopUps.fetch_add(1);
        ALOGW("AUDIO lead top-up at vbl=%u (avg queued %.2f)", gVblSeq.load(), avg);
    }
    return false;
}

void DrasticRunner::audioLeadExtraTick(int64_t nowUs) {
    static int sLeadFrames = -1; static int64_t sLastTopUpUs = 0;
    if (sLeadFrames < 0) sLeadFrames = property_get_int32("persist.gammaos.drastic_nano.audio_lead_frames", 2);
    if (sLeadFrames <= 0 || !mArm64Base || !gPaceOn.load() || gRaBurst.load()) return;
    const uint32_t queued = *reinterpret_cast<volatile uint32_t*>(mArm64Base + kAudioQueuedOff);
    // Top up while one chunk (67 ms) is still queued: at 0 the output thread
    // may already be starving. Measured on Golden Sun slot 1: late frames
    // erode the lead at about one chunk per 10 s.
    // An empty queue is an emergency regardless of the averaging controller.
    // With the clock match running, a single 0 reading is usually the +-1 quantisation of a healthy queue;
    // topping up then pushes it to the ceiling and drops a submit. Require the 4 s average to be low too.
    const bool reallyLow = !gClockMatchOn.load(std::memory_order_relaxed) ||
                           gClockMatchAvgX100.load(std::memory_order_relaxed) < 75;
    if (gAudioLeadDebt.load() <= 0 && queued == 0 && reallyLow && nowUs - sLastTopUpUs > 1000000) {
        gAudioLeadDebt.store(1); sLastTopUpUs = nowUs; gAudioLeadTopUps.fetch_add(1);
    }
    if (gAudioLeadDebt.load() <= 0) return;
    // the frame just ticked must finish first; give it most of a period
    if (!waitEmuParked(12000)) return;
    gAudioLeadDebt.fetch_sub(1);
    gAudioLeadExtra.fetch_add(1);
    ALOGW("AUDIO lead extra tick at vbl=%u", gVblSeq.load());
    { std::lock_guard<std::mutex> lk(gPaceMu); gVblSeq.fetch_add(1, std::memory_order_acq_rel); }
    gPaceCv.notify_all();
}

void DrasticRunner::pacerThread() {
    pthread_setname_np(pthread_self(), "dn-pacer");
    bool prevPaceOn = false;
    int64_t nextTick = 0;
    while (mPacerRun.load()) {
        if (!gPaceOn.load() || gStepMode.load()) { usleep(2000); nextTick = 0; continue; }
        {
            static int64_t sHbUs = 0;
            const int64_t hb = (int64_t)std::chrono::duration_cast<std::chrono::microseconds>(
                    std::chrono::steady_clock::now().time_since_epoch()).count();
            static int sHbOn = -1;
            if (sHbOn < 0) sHbOn = property_get_bool("sys.gammaos.drastic_nano.ra_debug", false) ? 1 : 0;
            if (sHbOn && hb - sHbUs > 2000000) {
                sHbUs = hb;
                ALOGI("pacer heartbeat: raMode=%d frames=%d ringCount=%d parked=%d",
                      gRaMode.load(), gRaFrames.load(), gRaRingCount.load(), gEmuParked.load() ? 1 : 0);
            }
        }
        const int64_t fixedLead = property_get_int32("sys.gammaos.drastic_nano.pace_lead_us", 0);
        const int64_t lead = fixedLead > 0 ? fixedLead : gLeadUs.load();
        const int64_t period = gVblankPeriodUs.load();
        const int64_t now = (int64_t)std::chrono::duration_cast<std::chrono::microseconds>(
                std::chrono::steady_clock::now().time_since_epoch()).count();
        const int64_t last = gLastVblankUs.load();
        int64_t target;
        if (last > 0 && now - last < 3 * period) {
            // Next vblank-aligned target that is both in the future and at
            // least three quarters of a period after the previous tick.
            // Right after a tick, now is only microseconds past that tick's
            // target, and jitter can put last + period a few tens of
            // microseconds beyond now + lead, which would select the slot
            // that just fired.
            const int64_t minTarget = nextTick > 0 ? nextTick + period - period / 4 : now;
            int64_t nv = last + period;
            while (nv - lead <= now || nv - lead < minTarget) nv += period;
            target = nv - lead;
        } else {
            target = (nextTick > 0 ? nextTick : now) + period;
            while (target <= now) target += period;
        }
        // Never tick sooner than one period after the previous tick, even if
        // a late-reported vblank pulls the alignment earlier.
        if (nextTick > 0 && target < nextTick + period - 500) target = nextTick + period - 500;
        int64_t sleepUs = target - now;
        if (gRaMode.load() == 2) {
            // Sleep in a wakeable way: an input change starts the replay
            // burst right away (the burst then overlaps the vblank slack),
            // the shown-frame tick still fires at the target.
            while (sleepUs > 0) {
                {
                    std::unique_lock<std::mutex> lk(gRaPokeMu);
                    gRaPokeCv.wait_for(lk, std::chrono::microseconds(sleepUs),
                                       [] { return gRaInputPoke.load(); });
                }
                if (gRaInputPoke.exchange(false)) runAheadTryBurst(false);
                sleepUs = target - (int64_t)std::chrono::duration_cast<std::chrono::microseconds>(
                        std::chrono::steady_clock::now().time_since_epoch()).count();
            }
        } else if (sleepUs > 0) {
            usleep((useconds_t)sleepUs);
        }
        nextTick = target;
        gPacerNextTickUs.store(target + period);   // where the tick after this one will land
        gLastTickUs.store(target);
        {
            const uint32_t k = gTickLogN.fetch_add(1) & 2047;
            gTickLog[k][0] = (int64_t)std::chrono::duration_cast<std::chrono::microseconds>(
                    std::chrono::steady_clock::now().time_since_epoch()).count();
            gTickLog[k][1] = last; gTickLog[k][2] = lead; gTickLog[k][3] = target;
        }
        {
            const bool on = gPaceOn.load();
            if (on && !prevPaceOn) gAudioLeadDebt.store(property_get_int32("persist.gammaos.drastic_nano.audio_lead_frames", 2));
            prevPaceOn = on;
        }
        if (!gClockMatchOn.load(std::memory_order_relaxed) && audioLeadHoldTick(target)) continue;   // queue at its ceiling: no emulated frame this vblank (the clock match trims the rate instead)
        if (gRaMode.load() == 2) {
            runAheadPacerTick();   // replay burst if the input changed, then the shown frame
            audioLeadExtraTick(target);
            continue;
        }
        { std::lock_guard<std::mutex> lk(gPaceMu); gVblSeq.fetch_add(1, std::memory_order_acq_rel); }
        gPaceCv.notify_all();
        audioLeadExtraTick(target);
    }
}

// Waits for the emulated frame of THIS period. drastic's per-screen ready
// mask (byte at BSS 0x3f2db80) is set by the producer just before its slot
// flip and cleared here once consumed; the slot-flip hook gives the exact
// time the frame became visible. A frame already waiting at wake that is
// older than half a period belongs to the previous period (the loop slipped a
// phase): it is dropped and the next one taken, which resyncs at the cost
// of one frame. If nothing arrives within the window the old content is
// presented as is; that is an emulator stall, not a pacing miss, so it
// returns true.
bool DrasticRunner::waitProducerFrame(int timeoutUs) {
    if (!mArm64Base) return false;
    volatile uint8_t* mask = mArm64Base + 0x3f2db80;
    const auto t0 = std::chrono::steady_clock::now();
    const int64_t nowUs = (int64_t)std::chrono::duration_cast<std::chrono::microseconds>(t0.time_since_epoch()).count();
    // With the lock off (bypass for a heavy scene, fast-forward, or not
    // wanted) the emulator runs on its own timer and is not phase-aligned
    // with our vblank: waiting here would stall the presenter by up to the
    // timeout every frame and the stale-drop below would discard frames
    // that are merely unaligned. Present the latest frame immediately.
    if (!gPaceOn.load()) {
        // Fast-forward with the staging pair: the presenter never waits for
        // the producer, it shows the newest staged frame every vblank (waiting
        // here chained the presenter to the emulator's flips and made it miss
        // every other vblank, an uneven 1 or 2 refresh hold per frame).
        if (mFastForwardOn && gFfStageWant.load(std::memory_order_relaxed) && gFfStagePub.load(std::memory_order_acquire) >= 2) {
            if (*mask != 0) { *mask = 0; gRaFreshVisible.store(true); }
            return true;
        }
        if (*mask == 0 && mPaceInstalled) {
            std::unique_lock<std::mutex> lk(gFlipMu);
            const int64_t deadline = nowUs + std::max(timeoutUs, 17000);
            while (*mask == 0) {
                const int64_t now2 = (int64_t)std::chrono::duration_cast<std::chrono::microseconds>(
                        std::chrono::steady_clock::now().time_since_epoch()).count();
                if (now2 >= deadline) break;
                gFlipCv.wait_for(lk, std::chrono::microseconds(deadline - now2));
            }
        }
        if (*mask != 0) {
            for (int i = 0; i < 40 && gProducerDoneUs.load() < nowUs - 1500; i++) usleep(50);
            *mask = 0;
            gRaFreshVisible.store(true);
            gLastFrameUs.store((int64_t)std::chrono::duration_cast<std::chrono::microseconds>(
                    std::chrono::steady_clock::now().time_since_epoch()).count());
        }
        return true;   // never a pacing miss while the lock is off
    }
    bool dropped = false;
    if (gRaBurst.load()) *mask = 0;   // never present a replay frame
    // Right after a replay burst the shown frame is legitimately late; do
    // not judge it stale (that would drop it and creep the lead).
    const bool afterBurst = (int32_t)(gVblSeq.load() - gRaBurstUntilSeq.load()) < 0;
    mLastWaitImmediate = (*mask != 0);
    if (mLastWaitImmediate && !afterBurst && gSteadyFrames.load() >= 120 &&
        nowUs - gProducerDoneUs.load() > 8000) {
        // The producer sets the mask a few microseconds before the flip
        // call that stamps the time: give a fresh frame that instant to
        // land before judging the mask stale.
        usleep(300);
    }
    if (mLastWaitImmediate && !afterBurst && gSteadyFrames.load() >= 120 &&
        nowUs - gProducerDoneUs.load() > 8000) {
        *mask = 0;
        dropped = true;
        mLastWaitImmediate = false;
        static int sDropLog = 0;
        if (sDropLog < 20) { sDropLog++; ALOGW("PACE drop stale frame age=%lld lead=%lld",
                (long long)(nowUs - gProducerDoneUs.load()), (long long)gLeadUs.load()); }
    }
    // Wait for the slot-flip hook (which runs on the emulator thread after
    // the ready mask is set and the slot toggled) rather than polling the
    // mask: the hook signals gFlipCv. With the hooks not installed fall
    // back to polling.
    if (mPaceInstalled) {
        std::unique_lock<std::mutex> lk(gFlipMu);
        const int64_t deadline = nowUs + timeoutUs;
        while (*mask == 0 || gRaBurst.load()) {
            if (gRaBurst.load()) *mask = 0;
            const int64_t now2 = (int64_t)std::chrono::duration_cast<std::chrono::microseconds>(
                    std::chrono::steady_clock::now().time_since_epoch()).count();
            if (now2 >= deadline) return dropped;
            gFlipCv.wait_for(lk, std::chrono::microseconds(std::min<int64_t>(deadline - now2, 2000)));
        }
        lk.unlock();
        // The hook fires after the producer's slot toggle; if the mask was
        // already set at entry we may be ahead of the toggle by microseconds.
        for (int i = 0; i < 40 && gProducerDoneUs.load() < nowUs - 1500; i++) usleep(50);
    } else {
        while (*mask == 0) {
            if (std::chrono::duration_cast<std::chrono::microseconds>(
                        std::chrono::steady_clock::now() - t0).count() >= timeoutUs) {
                return dropped;
            }
            usleep(100);
        }
    }
    *mask = 0;
    gRaFreshVisible.store(true);
    if (gSteadyFrames.fetch_add(1) == 0) ALOGW("PACE first emulated frame");
    gLastFrameUs.store((int64_t)std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count());
    return true;
}

void DrasticRunner::slotProbePre(SlotProbeSample& sm) {
    sm.valid = false;
    if (!mArm64Base) return;
    uint8_t* base = mArm64Base;
    uint8_t* bss = base + 0x3f2d1f8;
    uint8_t* slot0 = *reinterpret_cast<uint8_t**>(bss);
    uint8_t* slot1 = *reinterpret_cast<uint8_t**>(bss + 8);
    if (!slot0 || !slot1) return;
    int32_t cur = *reinterpret_cast<int32_t*>(bss + 0x958);
    int32_t ptype = *reinterpret_cast<int32_t*>(bss + 0x95c);
    int bpp = (ptype == 0x10) ? 2 : 4;
    for (int i = 0; i < 2; i++) {
        int32_t hr = *reinterpret_cast<int32_t*>(bss + 0x968 + 4 * i);
        size_t w = (size_t)(hr + 1) << 8;
        size_t h = (size_t)(hr + 1) * 192;
        sm.bytes[i] = w * h * (size_t)bpp;
        if (sm.bytes[i] > 0xC0000) sm.bytes[i] = 0xC0000;
    }
    int front = (~cur) & 1;
    sm.front = front ? slot1 : slot0;
    sm.back  = front ? slot0 : slot1;
    sm.curSlotPre = cur;
    sm.mask = *(base + 0x3f2db80);
    *(base + 0x3f2db80) = 0;
    sm.framesPre   = *reinterpret_cast<uint32_t*>(base + 0x3c9b124);
    sm.renderedPre = *reinterpret_cast<uint32_t*>(base + 0x3c9b120);
    sm.tPre = std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
    sm.f0 = slotProbeHash(sm.front, sm.bytes[0]);
    sm.f1 = slotProbeHash(sm.front + 0xC0000, sm.bytes[1]);
    sm.b0 = slotProbeHash(sm.back, sm.bytes[0]);
    sm.b1 = slotProbeHash(sm.back + 0xC0000, sm.bytes[1]);
    sm.valid = true;
}

void DrasticRunner::slotProbePost(SlotProbeSample& sm) {
    if (!sm.valid || !mArm64Base) return;
    uint8_t* base = mArm64Base;
    uint8_t* bss = base + 0x3f2d1f8;
    sm.curSlotPost = *reinterpret_cast<int32_t*>(bss + 0x958);
    sm.framesPost  = *reinterpret_cast<uint32_t*>(base + 0x3c9b124);
    sm.tPost = std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
    sm.f0b = slotProbeHash(sm.front, sm.bytes[0]);
    sm.f1b = slotProbeHash(sm.front + 0xC0000, sm.bytes[1]);
    ALOGW("SLOTP ridx=%d i=%d t=%lld cs=%d/%d fr=%u/%u rd=%u m=%02x "
          "f0=%08x f1=%08x b0=%08x b1=%08x f0p=%08x f1p=%08x up=%lld sz=%zu/%zu",
          sRingRenderIdx, gSlotProbe.iter, (long long)sm.tPre, sm.curSlotPre, sm.curSlotPost,
          sm.framesPre, sm.framesPost, sm.renderedPre, sm.mask,
          sm.f0, sm.f1, sm.b0, sm.b1, sm.f0b, sm.f1b,
          (long long)(sm.tPost - sm.tPre), sm.bytes[0], sm.bytes[1]);
    gSlotProbe.iter++;
    if (--gSlotProbe.remaining <= 0) {
        property_set("sys.gammaos.drastic_nano.slot_probe", "0");
        ALOGW("SLOTP done");
    }
}

bool DrasticRunner::slotProbeArm() {
    if (gSlotProbe.remaining > 0) return true;
    int n = property_get_int32("sys.gammaos.drastic_nano.slot_probe", 0);
    if (n <= 0) return false;
    gSlotProbe.remaining = n;
    gSlotProbe.iter = 0;
    ALOGW("SLOTP armed for %d iterations (base=%p)", n, mArm64Base);
    return true;
}


// ---- Slot sampler thread (diagnostic, sys.gammaos.drastic_nano.slot_sampler) ----
// Polls libdrastic's slot state at ~4 kHz for N seconds, independent of our
// render loop, and records (a) every curSlot flip and (b) every content
// change of screen 0 / screen 1 in either slot, each with a monotonic
// timestamp. That resolves the producer's write order relative to its flip
// (is a screen still being written after the flip, into the slot the
// consumer is about to read?) and gives drastic's real emulation rate from
// the flip timestamps. Output: /data/local/tmp/drastic_slot_sampler.txt.
namespace {
struct SampEv { int64_t t; char kind; int slot; int scr; uint32_t h; };
std::atomic<bool> gSamplerRunning{false};

void slotSamplerThread(uint8_t* base, int seconds) {
    uint8_t* bss = base + 0x3f2d1f8;
    uint8_t* slots[2] = { *reinterpret_cast<uint8_t**>(bss),
                          *reinterpret_cast<uint8_t**>(bss + 8) };
    if (!slots[0] || !slots[1]) { gSamplerRunning = false; return; }
    int32_t ptype = *reinterpret_cast<int32_t*>(bss + 0x95c);
    int bpp = (ptype == 0x10) ? 2 : 4;
    size_t bytes[2];
    for (int i = 0; i < 2; i++) {
        int32_t hr = *reinterpret_cast<int32_t*>(bss + 0x968 + 4 * i);
        bytes[i] = ((size_t)(hr + 1) << 8) * ((size_t)(hr + 1) * 192) * (size_t)bpp;
        if (bytes[i] > 0xC0000) bytes[i] = 0xC0000;
    }
    std::vector<SampEv> ev; ev.reserve(200000);
    uint32_t last[2][2] = {{0,0},{0,0}};
    int32_t lastCur = *reinterpret_cast<int32_t*>(bss + 0x958);
    auto now = []() { return (int64_t)std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count(); };
    const int64_t tEnd = now() + (int64_t)seconds * 1000000LL;
    ev.push_back({now(), 'S', lastCur, 0, 0});
    // lite mode: flips only, no content hashing (the hashing costs enough CPU
    // on this SoC to halve the frame rate, which invalidates timing captures).
    const bool lite = property_get_bool("sys.gammaos.drastic_nano.slot_sampler_lite", true);
    while (now() < tEnd) {
        int32_t cur = *reinterpret_cast<int32_t*>(bss + 0x958);
        if (cur != lastCur) { ev.push_back({now(), 'F', cur, 0, 0}); lastCur = cur; }
        if (lite) { usleep(200); continue; }
        for (int sl = 0; sl < 2; sl++) for (int sc = 0; sc < 2; sc++) {
            uint32_t h = slotProbeHash(slots[sl] + sc * 0xC0000, bytes[sc]);
            if (h != last[sl][sc]) { ev.push_back({now(), 'C', sl, sc, h}); last[sl][sc] = h; }
        }
        usleep(200);
    }
    // Profile mode: after each flip, wait 1.5 ms for the straggler writes, then
    // record a per-column luminance profile of BOTH screens in the just-
    // completed (front) slot. Offline, the horizontal scroll velocity of each
    // screen per emulator frame comes from cross-correlating consecutive
    // profiles; if the two screens' velocity series are offset by a frame, the
    // emulator itself is handing us screens from different DS frames.
    const bool prof = property_get_bool("sys.gammaos.drastic_nano.slot_prof", false);
    std::vector<std::vector<float>> profs; std::vector<int64_t> profT; std::vector<int> profSlot;
    if (prof) {
        int32_t lc = *reinterpret_cast<int32_t*>(bss + 0x958);
        const int64_t tEnd2 = now() + 8 * 1000000LL;
        while (now() < tEnd2) {
            int32_t cur = *reinterpret_cast<int32_t*>(bss + 0x958);
            if (cur == lc) { usleep(100); continue; }
            lc = cur;
            usleep(1500);
            int front = (~cur) & 1;
            // 2 screens x 4 row bands x 512 columns x RGB (band = 96 rows,
            // every 2nd row sampled).
            std::vector<float> pr(2 * 4 * 512 * 3, 0.f);
            for (int sc = 0; sc < 2; sc++) {
                const uint8_t* img = slots[front] + sc * 0xC0000;
                for (int y = 0; y < 384; y += 2) {
                    const int band = y / 96;
                    const uint8_t* row = img + (size_t)y * 2048;
                    float* dst = &pr[((sc * 4 + band) * 512) * 3];
                    for (int x = 0; x < 512; x++) {
                        const uint8_t* px = row + x * 4;
                        dst[x * 3 + 0] += px[0]; dst[x * 3 + 1] += px[1]; dst[x * 3 + 2] += px[2];
                    }
                }
            }
            profs.push_back(std::move(pr)); profT.push_back(now()); profSlot.push_back(front);
        }
        FILE* pf = fopen("/data/local/tmp/drastic_slot_prof.txt", "w");
        if (pf) {
            for (size_t i = 0; i < profs.size(); i++) {
                fprintf(pf, "%lld %d", (long long)profT[i], profSlot[i]);
                for (float v : profs[i]) fprintf(pf, " %.0f", v);
                fprintf(pf, "\n");
            }
            fclose(pf);
            ALOGW("SLOTS profile wrote %zu frames", profs.size());
        }
    }
    FILE* f = fopen("/data/local/tmp/drastic_slot_sampler.txt", "w");
    if (f) {
        fprintf(f, "# base=%p slots=%p,%p bytes=%zu,%zu bpp=%d\n", base, slots[0], slots[1], bytes[0], bytes[1], bpp);
        for (auto& e : ev) fprintf(f, "%lld %c %d %d %08x\n", (long long)e.t, e.kind, e.slot, e.scr, e.h);
        {
            const uint32_t n = gTickLogN.load(); const uint32_t from = n > 2048 ? n - 2048 : 0;
            for (uint32_t i = from; i < n; i++)
                fprintf(f, "%lld T %lld %lld %lld\n", (long long)gTickLog[i & 2047][0],
                        (long long)gTickLog[i & 2047][1], (long long)gTickLog[i & 2047][2],
                        (long long)gTickLog[i & 2047][3]);
        }
        fclose(f);
        ALOGW("SLOTS sampler wrote %zu events", ev.size());
    } else {
        ALOGW("SLOTS sampler: cannot open output (%s), dumping %zu events to log", strerror(errno), ev.size());
        for (size_t i = 0; i < ev.size() && i < 4000; i++)
            ALOGW("SLOTS %lld %c %d %d %08x", (long long)ev[i].t, ev[i].kind, ev[i].slot, ev[i].scr, ev[i].h);
    }
    gSamplerRunning = false;
}
} // namespace

void DrasticRunner::slotSamplerArm() {
    if (gSamplerRunning || !mArm64Base) return;
    int secs = property_get_int32("sys.gammaos.drastic_nano.slot_sampler", 0);
    if (secs <= 0) return;
    property_set("sys.gammaos.drastic_nano.slot_sampler", "0");
    gSamplerRunning = true;
    ALOGW("SLOTS sampler armed for %d s", secs);
    std::thread(slotSamplerThread, mArm64Base, secs).detach();
}


// ---- Fast DS texture upload ----
typedef EGLClientBuffer (*PFN_GetNativeClientBuffer)(const AHardwareBuffer*);

bool DrasticRunner::setupDsAhbTextures(int w, int h) {
    if (!sEglCreateImageKHR || !sGlEGLImageTargetTexture2DOES || sRingEglDpy == EGL_NO_DISPLAY) return false;
    static PFN_GetNativeClientBuffer getBuf = (PFN_GetNativeClientBuffer)
            eglGetProcAddress("eglGetNativeClientBufferANDROID");
    if (!getBuf) return false;
    const unsigned texs[2] = { mDsTopTex, mDsBotTex };
    for (int i = 0; i < 2; i++) {
        if (mDsImg[i]) { sEglDestroyImageKHR(sRingEglDpy, (EGLImageKHR)mDsImg[i]); mDsImg[i] = nullptr; }
        if (mDsAhb[i]) { AHardwareBuffer_release(mDsAhb[i]); mDsAhb[i] = nullptr; }
        AHardwareBuffer_Desc d = {};
        d.width = (uint32_t)w; d.height = (uint32_t)h; d.layers = 1;
        d.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
        d.usage = AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN | AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE;
        if (AHardwareBuffer_allocate(&d, &mDsAhb[i]) != 0 || !mDsAhb[i]) {
            ALOGW("DrasticRunner: fast upload: AHardwareBuffer_allocate(%dx%d) failed", w, h);
            return false;
        }
        EGLClientBuffer cb = getBuf(mDsAhb[i]);
        const EGLint attrs[] = { EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE };
        EGLImageKHR img = sEglCreateImageKHR(sRingEglDpy, EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID, cb, attrs);
        if (img == EGL_NO_IMAGE_KHR) {
            ALOGW("DrasticRunner: fast upload: eglCreateImageKHR failed (0x%x)", eglGetError());
            return false;
        }
        mDsImg[i] = (void*)img;
        glBindTexture(GL_TEXTURE_2D, texs[i]);
        while (glGetError() != GL_NO_ERROR) {}
        sGlEGLImageTargetTexture2DOES(GL_TEXTURE_2D, (GLeglImageOES)img);
        const GLenum err = glGetError();
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);
        if (err != GL_NO_ERROR) {
            ALOGW("DrasticRunner: fast upload: glEGLImageTargetTexture2DOES failed (0x%x)", err);
            return false;
        }
    }
    mDsAhbW = w; mDsAhbH = h;
    ALOGW("DrasticRunner: fast upload: DS textures backed by AHardwareBuffers %dx%d", w, h);
    return true;
}

// NOP fxRender's two glTexSubImage2D calls (+0x1d2dc, +0x1d35c) so the DS
// textures keep the memory we copy into; restore them when disabling.
void DrasticRunner::patchFxUpload(bool disableUpload) {
    if (!mArm64Base || mFxUploadPatched == disableUpload) return;
    static const uintptr_t kSites[2] = { 0x1d2dc, 0x1d35c };
    static const uint32_t kOrig[2] = { 0x97ffe501u, 0x97ffe4e1u };
    const long ps = sysconf(_SC_PAGESIZE) > 0 ? sysconf(_SC_PAGESIZE) : 4096;
    uint8_t* pg = (uint8_t*)((uintptr_t)(mArm64Base + kSites[0]) & ~(uintptr_t)(ps - 1));
    for (int i = 0; i < 2; i++) {
        const uint32_t cur = *reinterpret_cast<uint32_t*>(mArm64Base + kSites[i]);
        if (cur != (disableUpload ? kOrig[i] : 0xd503201fu)) {
            ALOGW("DrasticRunner: fast upload: unexpected code at +0x%lx (%08x)", (unsigned long)kSites[i], cur);
            return;
        }
    }
    if (mprotect(pg, ps, PROT_READ | PROT_WRITE | PROT_EXEC) != 0) return;
    for (int i = 0; i < 2; i++)
        *reinterpret_cast<uint32_t*>(mArm64Base + kSites[i]) = disableUpload ? 0xd503201fu : kOrig[i];
    __builtin___clear_cache((char*)pg, (char*)pg + ps);
    mprotect(pg, ps, PROT_READ | PROT_EXEC);
    mFxUploadPatched = disableUpload;
}

// Copy the front slot's two screens into the AHardwareBuffer-backed DS
// textures. Called right before fxRender; replaces its glTexSubImage2D.
#ifndef EGL_LINUX_DMA_BUF_EXT
#define EGL_LINUX_DMA_BUF_EXT 0x3270
#define EGL_LINUX_DRM_FOURCC_EXT 0x3271
#define EGL_DMA_BUF_PLANE0_FD_EXT 0x3272
#define EGL_DMA_BUF_PLANE0_OFFSET_EXT 0x3273
#define EGL_DMA_BUF_PLANE0_PITCH_EXT 0x3274
#endif
static const uint32_t kDrmFormatAbgr8888 = 0x34324241u;   // 'AB24': R,G,B,A byte order

// Allocate the 3 MB dma-buf, map it, and ask the emulator thread to move
// drastic's slots into it at the next frame boundary. Called on the
// presenter thread; returns true once the swap has completed.
bool DrasticRunner::setupZeroCopySlots() {
    if (mZcOn) return true;
    if (!mArm64Base || !sEglCreateImageKHR || !sGlEGLImageTargetTexture2DOES) return false;
    if (!property_get_bool("persist.gammaos.drastic_nano.zero_copy", true)) return false;
    if (!mZcTried) {
        mZcTried = true;
        int heap = open("/dev/dma_heap/system", O_RDONLY | O_CLOEXEC);
        if (heap < 0) { ALOGW("DrasticRunner: zero-copy: no /dev/dma_heap/system (%s)", strerror(errno)); return false; }
        struct dma_heap_allocation_data ad = {};
        ad.len = 0x300000; ad.fd_flags = O_RDWR | O_CLOEXEC;
        int rc = ioctl(heap, DMA_HEAP_IOCTL_ALLOC, &ad);
        close(heap);
        if (rc != 0 || (int)ad.fd < 0) { ALOGW("DrasticRunner: zero-copy: dma-heap alloc failed (%s)", strerror(errno)); return false; }
        mZcFd = (int)ad.fd;
        void* m = mmap(nullptr, 0x300000, PROT_READ | PROT_WRITE, MAP_SHARED, mZcFd, 0);
        if (m == MAP_FAILED) { ALOGW("DrasticRunner: zero-copy: mmap failed (%s)", strerror(errno)); close(mZcFd); mZcFd = -1; return false; }
        mZcMap = (uint8_t*)m;
        memset(mZcMap, 0, 0x300000);
        gZcBss = mArm64Base + 0x3f2d1f8;
        gZcNewBase.store(mZcMap);
        gZcSwapState.store(1);
        ALOGW("DrasticRunner: zero-copy: dma-buf fd %d mapped, swap requested", mZcFd);
        return false;   // the emulator thread swaps at the next flip
    }
    const int st = gZcSwapState.load();
    if (st == 1) return false;
    if (st != 2) { ALOGW("DrasticRunner: zero-copy: swap not possible, staying on the copy path"); return false; }
    mZcOn = true;
    ALOGW("DrasticRunner: zero-copy: slots now live in the dma-buf");
    return true;
}

// Bind the front slot's two screens (EGLImage views of the dma-buf) to the
// DS textures after cleaning the CPU cache so the GPU sees drastic's writes.
bool DrasticRunner::zeroCopyBindFront() {
    uint8_t* bss = mArm64Base + 0x3f2d1f8;
    const int32_t cur = *reinterpret_cast<int32_t*>(bss + 0x958);
    int front = ((~cur) & 1) ? 1 : 0;
    if (gRaMode.load() == 2 && gRaShownFront.load() >= 0 && property_get_bool("sys.gammaos.drastic_nano.ra_front_pin", true)) front = gRaShownFront.load();
    {
        static int sFfStage = -1;
        if (sFfStage < 0) sFfStage = property_get_int32("persist.gammaos.drastic_nano.ff_stage", 1);
        const int rt = property_get_int32("sys.gammaos.drastic_nano.ff_stage_rt", -1);
        bool want = mFastForwardOn && (rt >= 0 ? rt > 0 : sFfStage > 0);
        if (want && gFfStageFd < 0) {
            // lazily allocate the staging dma-buf on the first FF use
            int heap = open("/dev/dma_heap/system", O_RDONLY | O_CLOEXEC);
            struct dma_heap_allocation_data ad = {};
            ad.len = 0x300000; ad.fd_flags = O_RDWR | O_CLOEXEC;
            if (heap >= 0 && ioctl(heap, DMA_HEAP_IOCTL_ALLOC, &ad) == 0 && (int)ad.fd >= 0) {
                void* m = mmap(nullptr, 0x300000, PROT_READ | PROT_WRITE, MAP_SHARED, (int)ad.fd, 0);
                if (m == MAP_FAILED) { close((int)ad.fd); gFfStageFd = -2; }
                else { memset(m, 0, 0x300000); gFfStageFd = (int)ad.fd; gFfStageMap.store((uint8_t*)m, std::memory_order_release); }
            } else gFfStageFd = -2;
            if (heap >= 0) close(heap);
            ALOGW("DrasticRunner: FF staging dma-buf %s", gFfStageFd >= 0 ? "allocated" : "unavailable, staging off");
        }
        if (gFfStageFd < 0) want = false;
        gFfStageWant.store(want ? 1 : 0, std::memory_order_relaxed);
        int pub = gFfStagePub.load(std::memory_order_acquire);
        // Change the shown frame only every second vblank: with the content
        // rate near 30 a second every frame is held exactly 2 refreshes.
        static int sHeld = -1, sHeldCount = 0;
        if (want && pub >= 2) {
            if (sHeld >= 2 && sHeldCount < 2 && property_get_int32("sys.gammaos.drastic_nano.ff_hold2_rt", 1) > 0) { pub = sHeld; sHeldCount++; }
            else { if (pub != sHeld) sHeldCount = 1; else sHeldCount++; sHeld = pub; }
            front = pub;
        } else { sHeld = -1; sHeldCount = 0; }
    }
    // slots 2/3 = FF staging views, in the staging dma-buf
    static void* sZcStageImg[2][2] = {{nullptr, nullptr}, {nullptr, nullptr}};
    void** imgs[4] = { mZcImg[0], mZcImg[1], sZcStageImg[0], sZcStageImg[1] };
    static int sStageImgW = 0, sStageImgH = 0;
    const int32_t hr = *reinterpret_cast<int32_t*>(bss + 0x968);
    const int w = (hr + 1) << 8, h = (hr + 1) * 192;
    const bool liveStale = w != mZcImgW || h != mZcImgH;
    const bool stageStale = gFfStageFd >= 0 && (w != sStageImgW || h != sStageImgH);
    if (liveStale || stageStale) {
        const int s2lo = liveStale ? 0 : 2, s2hi = stageStale ? 4 : 2;
        for (int s2 = s2lo; s2 < s2hi; s2++) for (int k = 0; k < 2; k++) {
            if (imgs[s2][k]) { sEglDestroyImageKHR(sRingEglDpy, (EGLImageKHR)imgs[s2][k]); imgs[s2][k] = nullptr; }
            const EGLint attrs[] = {
                EGL_WIDTH, w, EGL_HEIGHT, h,
                EGL_LINUX_DRM_FOURCC_EXT, (EGLint)kDrmFormatAbgr8888,
                EGL_DMA_BUF_PLANE0_FD_EXT, s2 < 2 ? mZcFd : gFfStageFd,
                EGL_DMA_BUF_PLANE0_OFFSET_EXT, (EGLint)((s2 & 1) * 0x180000 + k * 0xC0000),
                EGL_DMA_BUF_PLANE0_PITCH_EXT, w * 4,
                EGL_NONE };
            EGLImageKHR img = sEglCreateImageKHR(sRingEglDpy, EGL_NO_CONTEXT, EGL_LINUX_DMA_BUF_EXT, nullptr, attrs);
            if (img == EGL_NO_IMAGE_KHR) {
                ALOGW("DrasticRunner: zero-copy: dma-buf EGLImage %dx%d failed (0x%x)%s", w, h, eglGetError(), s2 >= 2 ? " (staging off)" : "");
                if (s2 >= 2) { gFfStageFd = -2; gFfStageWant.store(0, std::memory_order_relaxed); if (front >= 2) front = ((~cur) & 1) ? 1 : 0; break; }
                mZcImgW = mZcImgH = 0;
                return false;
            }
            imgs[s2][k] = (void*)img;
        }
        if (liveStale) { mZcImgW = w; mZcImgH = h; }
        if (stageStale) { sStageImgW = w; sStageImgH = h; }
        ALOGW("DrasticRunner: zero-copy: dma-buf views %dx%d ready", w, h);
    }
    struct dma_buf_sync sync = {};
    sync.flags = DMA_BUF_SYNC_END | DMA_BUF_SYNC_WRITE;   // clean CPU writes to memory
    ioctl(front >= 2 ? gFfStageFd : mZcFd, DMA_BUF_IOCTL_SYNC, &sync);
    const unsigned texs[2] = { mDsTopTex, mDsBotTex };
    for (int k = 0; k < 2; k++) {
        glBindTexture(GL_TEXTURE_2D, texs[k]);
        sGlEGLImageTargetTexture2DOES(GL_TEXTURE_2D, (GLeglImageOES)imgs[front][k]);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }
    glBindTexture(GL_TEXTURE_2D, 0);
    return true;
}

void DrasticRunner::fastUploadFrame() {
    if (!mArm64Base) return;
    inputHeldCheck();
    uint8_t* bss = mArm64Base + 0x3f2d1f8;
    uint8_t* slot0 = *reinterpret_cast<uint8_t**>(bss);
    uint8_t* slot1 = *reinterpret_cast<uint8_t**>(bss + 8);
    if (!slot0 || !slot1) return;
    const int32_t cur = *reinterpret_cast<int32_t*>(bss + 0x958);
    int frontIdx = ((~cur) & 1) ? 1 : 0;
    if (gRaMode.load() == 2 && gRaShownFront.load() >= 0) frontIdx = gRaShownFront.load();
    const uint8_t* front = frontIdx ? slot1 : slot0;
    // Both screens share the hires flag in practice; size from screen 0.
    const int32_t hr = *reinterpret_cast<int32_t*>(bss + 0x968);
    const int w = (hr + 1) << 8, h = (hr + 1) * 192;
    if (w != mDsAhbW || h != mDsAhbH) {
        if (!setupDsAhbTextures(w, h)) { patchFxUpload(false); mFastUploadOn = false; return; }
    }
    if (mZcOn || setupZeroCopySlots()) {
        if (zeroCopyBindFront()) return;
        // view creation failed: fall back to the copy path for good
        mZcOn = false; property_set("persist.gammaos.drastic_nano.zero_copy", "0");
    }
    for (int i = 0; i < 2; i++) {
        void* dst = nullptr;
        if (AHardwareBuffer_lock(mDsAhb[i], AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, -1, nullptr, &dst) != 0 || !dst)
            continue;
        AHardwareBuffer_Desc d = {}; AHardwareBuffer_describe(mDsAhb[i], &d);
        const size_t rowBytes = (size_t)w * 4, dstStride = (size_t)d.stride * 4;
        const uint8_t* src = front + (size_t)i * 0xC0000;
        if (dstStride == rowBytes) memcpy(dst, src, rowBytes * (size_t)h);
        else for (int y = 0; y < h; y++) memcpy((uint8_t*)dst + y * dstStride, src + y * rowBytes, rowBytes);
        AHardwareBuffer_unlock(mDsAhb[i], nullptr);
    }
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
            (property_get_int32("sys.gammaos.drastic_nano.ff_blend_rt", -1) < 0
                 ? property_get_int32("persist.gammaos.drastic_nano.ff_blend", 0)
                 : property_get_int32("sys.gammaos.drastic_nano.ff_blend_rt", 0))) {
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

    // Frame-coherence sync (dual-DSI screen desync fix, prop-gated).
    // fxRender/renderFrame grab drastic's CURRENT top and bottom framebuffers.
    // The DS CPU thread renders a frame top-to-bottom, so at an arbitrary
    // instant the top screen can already be frame N while the bottom is still
    // frame N-1 -- a one-frame skew BETWEEN the two screens (this is the
    // dual-panel "desync", not a display/scanout offset: proven on RG DS by the
    // afbc_dup_top test, where both panels showing the SAME screen are perfectly
    // synced). waitScreen blocks until drastic signals a COMPLETE frame, so the
    // subsequent grab sees both screens from the same frame N. It was removed
    // earlier to avoid coupling the render rate to drastic on overrun; gate it
    // so the dual-panel path can opt back in without affecting other devices.
    // Under fast-forward drastic runs frames back to back, unaligned to our vblank grab, so the grab can
    // read a screen buffer mid-compose or freshly cleared (an all-black panel for a frame: the FF flicker
    // that ff_noclear does not cover). Waiting for a complete frame is cheap under FF (drastic produces
    // them faster than 60 Hz). sys.gammaos.drastic_nano.ff_coherent (runtime): 1 = wait only while FF is on.
    static int sFfCoherent = -1;
    if (sFfCoherent < 0) sFfCoherent = property_get_int32("sys.gammaos.drastic_nano.ff_coherent", 0);
    if (mWaitScreen &&
        (property_get_int32("persist.gammaos.drastic_nano.frame_coherent", 0) ||
         (mFastForwardOn && sFfCoherent > 0))) {
        mWaitScreen(mFakeEnv, mFakeCls);
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
    static int sFfDirect = -1;
    if (sFfDirect < 0) sFfDirect = property_get_int32("persist.gammaos.drastic_nano.ff_direct", 1);
    const int ffDirectRt = property_get_int32("sys.gammaos.drastic_nano.ff_direct_rt", -1);
    const bool ffDirectOk = ffDirectRt >= 0 ? ffDirectRt > 0 : sFfDirect > 0;
    // Under FF the direct panel path is allowed when the frame is not being
    // blended (the blend needs the offscreen), so the presenter keeps 60 Hz.
    const bool direct = mDirectFbo != 0 && mFxRender && (!mFastForwardOn || ffDirectOk) && !mFfBlendThisFrame;
    // Fast-forward (persist.gammaos.drastic_nano.ff_noclear, default 1): under FF the presenter goes through
    // the offscreen FBO and used to clear it to black every frame; when a screen is not drawn that frame an
    // all-black panel was presented (part of the FF flicker, read as "screen swapping"). Keep the previous
    // frame in the offscreen instead: fxRender overwrites it whenever it does draw. 1x is untouched.
    static int sFfNoClear = -1;
    if (sFfNoClear < 0) sFfNoClear = property_get_int32("persist.gammaos.drastic_nano.ff_noclear", 1);
    const int ncRt = property_get_int32("sys.gammaos.drastic_nano.ff_noclear_rt", -1);
    const bool skipClear = mFastForwardOn && (ncRt >= 0 ? ncRt > 0 : sFfNoClear > 0);
    if (mOffscreenFbo != 0 && !direct) {
        glBindFramebuffer(GL_FRAMEBUFFER, mOffscreenFbo);
        if (!skipClear) {
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);
        }
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
        if (direct) {
            patchFinalPassFbo(mDirectFbo);
            glBindFramebuffer(GL_FRAMEBUFFER, mDirectFbo);
            glDisable(GL_SCISSOR_TEST);
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);
            glBindBuffer(GL_ARRAY_BUFFER, directVbo(mDirectVariant));
            // The final pass lands in the combined buffer, whose size is the
            // panel pair, not the (possibly render-scaled) fx offscreen: size
            // the viewport and the output rect to the target, or a render scale
            // above 1 leaves the pair drawn small in one corner of it.
            if (mDirectW > 0 && mDirectH > 0) glViewport(0, 0, mDirectW, mDirectH);
        }
        // Drain any prior errors first so the post-call check is clean.
        while (glGetError() != GL_NO_ERROR) {}
        slotSamplerArm();
        SlotProbeSample probe;
        const bool probing = slotProbeArm();
        if (probing) slotProbePre(probe);
        if (!mFastUploadOn && !mFastUploadTried && mArm64Base && mDsTexW > 0 &&
            property_get_bool("persist.gammaos.drastic_nano.fast_upload", true)) {
            mFastUploadTried = true;
            if (setupDsAhbTextures(mDsTexW, mDsTexH)) {
                patchFxUpload(true);
                mFastUploadOn = mFxUploadPatched;
            }
            ALOGW("DrasticRunner: fast upload %s", mFastUploadOn ? "on" : "off");
        }
        // Run-ahead Phase 0 harness: sys.gammaos.drastic_nano.runahead_probe=N
        // (see runaheadProbe). Runs on its own thread so the presenter keeps
        // going while the emulator is single-stepped.
        {
            int n = property_get_int32("sys.gammaos.drastic_nano.runahead_probe", 0);
            if (n > 0 && mSaveState && mLoadState && mArm64Base) {
                property_set("sys.gammaos.drastic_nano.runahead_probe", "0");
                std::thread([this, n] { runaheadProbe(n); }).detach();
            }
        }
        // State benchmark (diagnostic): sys.gammaos.drastic_nano.state_bench=N
        // runs N blocking saveState + loadState cycles on slot 8 from here and
        // logs each duration; loadState has no blocking form, so it is timed
        // by polling its request byte at master+0x4b6 until the emulator
        // thread clears it.
        {
            int n = property_get_int32("sys.gammaos.drastic_nano.state_bench", 0);
            if (n > 0 && mSaveState && mLoadState && mArm64Base) {
                property_set("sys.gammaos.drastic_nano.state_bench", "0");
                typedef int (*saveState4_t)(void*, void*, int, int);
                saveState4_t save4 = reinterpret_cast<saveState4_t>(mSaveState);
                volatile uint8_t* loadReq = mArm64Base + 0x14c000 + 0x4b6;
                for (int i = 0; i < n; i++) {
                    const int64_t t0 = std::chrono::duration_cast<std::chrono::microseconds>(
                            std::chrono::steady_clock::now().time_since_epoch()).count();
                    save4(mFakeEnv, mFakeCls, 8, 1);
                    const int64_t t1 = std::chrono::duration_cast<std::chrono::microseconds>(
                            std::chrono::steady_clock::now().time_since_epoch()).count();
                    mLoadState(mFakeEnv, mFakeCls, 8);
                    int spins = 0;
                    while (*loadReq != 0 && spins++ < 500000) usleep(10);
                    const int64_t t2 = std::chrono::duration_cast<std::chrono::microseconds>(
                            std::chrono::steady_clock::now().time_since_epoch()).count();
                    ALOGW("STATEBENCH %d: save %lld us, load %lld us (spins %d)", i,
                          (long long)(t1 - t0), (long long)(t2 - t1), spins);
                }
            }
        }
        // Run-ahead without zero-copy slots: upload only a fresh visible frame.
        if (mFastUploadOn && (gRaMode.load() != 2 || gZcSwapState.load() == 2 || gRaFreshVisible.exchange(false)))
            fastUploadFrame();
        const int fxOutW = (direct && mDirectW > 0) ? mDirectW : mOffscreenW;
        const int fxOutH = (direct && mDirectH > 0) ? mDirectH : mOffscreenH;
        mFxRender(mFakeEnv, mFakeCls,
                  (int)mDsTopTex, (int)mDsBotTex,
                  0, 6, 18,
                  0, 0, fxOutW, fxOutH,
                  0);
        if (probing) slotProbePost(probe);
        if (direct) { patchFinalPassFbo(mOffscreenFbo); mDirectDone = true; }
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

    // Frame-coherence handshake close (see waitScreen above). drastic holds the
    // just-produced frame stable between waitScreen and signalScreen; the grab/
    // upload above ran inside that window so both screens came from one frame.
    // signalScreen releases drastic to produce the next. Without this ack the
    // handshake is half-open (frame never released) -> stutter, which is what a
    // lone waitScreen produced.
    if (mSignalScreen &&
        property_get_int32("persist.gammaos.drastic_nano.frame_coherent", 0)) {
        mSignalScreen(mFakeEnv, mFakeCls);
    }

    mDirectFbo = 0;   // one-shot: the presenter re-arms it every frame
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

int* gInputLastWritten = nullptr;
// Per-frame diagnostic (input_log): report when drastic's input word no
// longer matches what we last wrote, i.e. drastic changed it by itself.
void DrasticRunner::inputHeldCheck() {
    if (!mArm64Base || !gInputLastWritten || *gInputLastWritten < 0) return;
    static int64_t sCheckUs = 0; static int sOn = 0;
    const int64_t nowUs = (int64_t)std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
    if (nowUs - sCheckUs > 2000000) { sOn = property_get_int32("sys.gammaos.drastic_nano.input_log", 0); sCheckUs = nowUs; }
    if (!sOn) return;
    const int held = *reinterpret_cast<int*>(mArm64Base + 0x14c000 + 0x48c) & 0xfff;
    static int sLastLogged = -1;
    if (held != (*gInputLastWritten & 0xfff) && held != sLastLogged) {
        ALOGW("INPUTH t=%lld held=%03x written=%03x (drastic changed it)",
              (long long)(nowUs % 100000000LL), held, *gInputLastWritten & 0xfff);
        sLastLogged = held;
    } else if (held == (*gInputLastWritten & 0xfff)) sLastLogged = -1;
}

void DrasticRunner::setInputWithTouch(int bitmask, int touchX, int touchY,
                                      bool touchHeld) {
    if (!mInitialized || !mUpdateInput) return;
    // updateInput(JNIEnv*, jclass, int bitmask, int touchPacked,
    //             int inputFilterMask)
    //
    // Native side at 0x1a5d8 stores:
    //   master+0x48c = bitmask & 0x7fffffff     (button bits 0..11 used)
    //   master+0x490 = input-filter mask (the third JNI argument)
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
    // The third JNI argument is not the touchscreen held flag. DraStic uses it
    // as a mask while processing special input bindings; passing touchHeld
    // here would set bit 0 of that mask whenever a finger is down and suppress
    // the D-pad. Touch state is already carried by bit 31 above.
    // Diagnostic (sys.gammaos.drastic_nano.input_log=1): log each change of
    // the written mask with the writing thread, and what drastic's master
    // input word held right before the write (a mismatch with our previous
    // write means drastic itself changed it).
    static int sInputLog = -1; static int64_t sInputLogCheckUs = 0;
    static int sLastWritten = -1;
    gInputLastWritten = &sLastWritten;
    const int64_t nowUs = (int64_t)std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
    if (sInputLog < 0 || nowUs - sInputLogCheckUs > 2000000) {
        sInputLog = property_get_int32("sys.gammaos.drastic_nano.input_log", 0);
        sInputLogCheckUs = nowUs;
    }
    if (sInputLog && mArm64Base) {
        const int held = *reinterpret_cast<int*>(mArm64Base + 0x14c000 + 0x48c);
        if (fullBitmask != sLastWritten || (held & 0xfff) != (sLastWritten & 0xfff)) {
            ALOGW("INPUTW t=%lld tid=%d mask=%03x prev=%03x held=%03x",
                  (long long)(nowUs % 100000000LL), (int)syscall(__NR_gettid),
                  fullBitmask & 0xfff, sLastWritten & 0xfff, held & 0xfff);
        }
    }
    sLastWritten = fullBitmask;
    mUpdateInput(mFakeEnv, mFakeCls, fullBitmask, touchPacked, 0);
    // Live input latch (live_input, default on): drastic copies the JNI input
    // words into the block the emulated KEYINPUT/touch reads come from
    // (heap+0x80010) once per frame, at frame end (+0x16e74 from +0x8087c).
    // A game polling in vblank then sees input that is most of a frame old.
    // Writing the same block here, on every input change, makes the next
    // emulated read see it: up to a frame less latency, no replay needed.
    {
        static int sLive = -1;
        if (sLive < 0) sLive = property_get_int32("sys.gammaos.drastic_nano.live_input", 1);
        if (sLive == 1 && mArm64Base) {
            uint8_t* heapMaster = *reinterpret_cast<uint8_t**>(mArm64Base + 0x14c000);
            if (heapMaster) {
                uint8_t* st = mArm64Base + 0x14c000;
                uint8_t* latch = heapMaster + 0x80010;
                uint32_t mask = *reinterpret_cast<volatile uint32_t*>(st + 0x48c);
                if (st[0x4c0]) mask |= 0x1000;
                *reinterpret_cast<volatile uint64_t*>(latch + 4) = *reinterpret_cast<volatile uint64_t*>(st + 0x494);
                latch[12] = st[0x4bf];
                *reinterpret_cast<volatile uint32_t*>(latch) = mask;
            }
        }
    }
    if (gRaMode.load(std::memory_order_relaxed) == 2) {
        { std::lock_guard<std::mutex> lk(gRaPokeMu); gRaInputPoke.store(true); }
        gRaPokeCv.notify_all();
    }
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
    // Blocking form (4th argument): returns once the emulator thread has
    // written the state.
    typedef int (*saveState4_t)(void*, void*, int, int);
    int rc = reinterpret_cast<saveState4_t>(mSaveState)(mFakeEnv, mFakeCls, slot, 1);
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
    if (slot < 0 || slot > 9) {
        ALOGW("DrasticRunner::loadStateSlot: refusing slot %d (valid 0..9, 9 = autosave)",
              slot);
        return false;
    }
    const int64_t t0 = std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
    int rc = mLoadState(mFakeEnv, mFakeCls, slot);
    ALOGI("DrasticRunner::loadStateSlot(%d) = %d", slot, rc);
    runAheadReset();   // the ring belongs to the old timeline
    // Completion: the emulator thread clears the request byte at
    // master+0x4b6 once the state is restored.
    if (mArm64Base) {
        std::thread([this, t0, slot] {
            volatile uint8_t* req = mArm64Base + 0x14c000 + 0x4b6;
            int spins = 0;
            while (*req != 0 && spins++ < 1000000) usleep(10);
            // A state load stalls production ~140 ms and empties the OpenSL queue; the clock match only trims,
            // and the queued==0 top-up adds 1/4 chunk per second, so without help the first ~30 s after a load
            // ride the empty edge (audible gaps). Prime the queue now: a lead debt of clock_match_load_prime
            // emulated frames (default 0: measured not to execute reliably after a load and, while pending, it gated
            // off the once-per-second emergency top-up; kept as a knob) that the pacer runs over the next vblanks, at the
            // load cut where a hitch already exists.
            if (property_get_int32("persist.gammaos.drastic_nano.clock_match", 1) > 0) {
                const int prime = property_get_int32("persist.gammaos.drastic_nano.clock_match_load_prime", 0);
                if (prime > 0) gAudioLeadDebt.store(prime);
            }
            const int64_t t1 = std::chrono::duration_cast<std::chrono::microseconds>(
                    std::chrono::steady_clock::now().time_since_epoch()).count();
            ALOGW("STATELOAD slot %d done in %lld us", slot, (long long)(t1 - t0));
        }).detach();
    }
    return true;
}

// ---- Run-ahead primitives ----
//
// libdrastic_arm64.so (md5 7c5f33a3) savestate machinery, offsets = vaddr:
//   master = base + 0x14c000
//   master+0x4b4 save slot, master+0x4b5 save request: set by the saveState
//     JNI, consumed by the per-frame request hook (+0x16fb4, right after the
//     input latch at the start of a frame) which renders the two thumbnail
//     screens, serializes the state (+0x1c88c) into a malloc'd buffer and
//     hands it to a writer pthread (+0x7a1bc: optional zlib compress, fwrite
//     through the DraSticPathCache file, rename temp -> slot); the request
//     byte clears once the writer thread is started.
//   base+0x3f1e09c: writer-thread busy flag, cleared when the file is done.
//     The blocking JNI form (and the next load) spin on it.
//   master+0x4b6 load request: consumed by the same hook (+0x16fc4), which
//     opens the slot file, reads it back into a 6.8 MB buffer (+0x7a408),
//     flushes the JIT translation cache (+0x1e320), deserializes (+0x1c614)
//     and clears the byte.
// With the RAM slot registered in FakeJNI the file side is a memfd, so the
// remaining cost is the serializer itself plus drastic's thread handoffs.
namespace {
constexpr uint32_t kRaMasterOff     = 0x14c000;
constexpr uint32_t kRaSaveReqOff    = 0x4b5;
constexpr uint32_t kRaLoadReqOff    = 0x4b6;
constexpr uint32_t kRaWriterBusyOff = 0x3f1e09c;

int64_t raNowUs() {
    return (int64_t)std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
}

bool raPollZero(volatile uint8_t* p, int timeoutUs) {
    const int64_t deadline = raNowUs() + timeoutUs;
    while (*p != 0) {
        if (raNowUs() > deadline) return false;
        usleep(20);
    }
    return true;
}

// Issue one pacer tick from the step controller. Returns the limiter-wait
// count before the tick, for raStepWait.
uint32_t raStepTick() {
    const uint32_t v0 = gVWaitCount.load();
    const int64_t t0 = raNowUs();
    gLastStepTickUs.store(t0);
    gLastTickUs.store(t0);
    { std::lock_guard<std::mutex> lk(gPaceMu); gVblSeq.fetch_add(1, std::memory_order_acq_rel); }
    gPaceCv.notify_all();
    return v0;
}

// The stepped frame is done once the emulator thread has entered the
// limiter wait again (count advanced) and parked there.
bool raStepWait(uint32_t v0, int timeoutUs) {
    std::unique_lock<std::mutex> lk(gParkMu);
    return gParkCv.wait_for(lk, std::chrono::microseconds(timeoutUs),
                            [&] { return gVWaitCount.load() != v0 && gEmuParked.load(); });
}

// Overwrite one instruction in libdrastic's text. Returns the old word.
uint32_t raPatchInsn(uint8_t* base, uintptr_t off, uint32_t insn) {
    const long ps = sysconf(_SC_PAGESIZE) > 0 ? sysconf(_SC_PAGESIZE) : 4096;
    uint8_t* p = base + off;
    uint8_t* pg = (uint8_t*)((uintptr_t)p & ~(uintptr_t)(ps - 1));
    const uint32_t old = *reinterpret_cast<uint32_t*>(p);
    if (mprotect(pg, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC) != 0) {
        ALOGW("DrasticRunner: patch at +0x%lx: mprotect failed: %s", (unsigned long)off, strerror(errno));
        return old;
    }
    *reinterpret_cast<uint32_t*>(p) = insn;
    __builtin___clear_cache((char*)p, (char*)p + 4);
    mprotect(pg, (size_t)ps, PROT_READ | PROT_EXEC);
    return old;
}

// Load path +0x7a488: "bl 0x1e320" flushes the JIT translation cache and
// makes the caller re-initialize the recompiler (+0x80828 -> +0x1e490)
// after the state is restored. "mov w0, #1" skips both.
constexpr uintptr_t kRaLoadJitFlushSite = 0x7a48c;
constexpr uint32_t  kRaMovW0One = 0x52800020;
constexpr uint32_t  kRaMovW0Zero = 0x52800000;
// Deserializer +0x1c694: "bl 0x37cd0" with w1 = -1 is the JIT invalidation
// (wipes both CPU block-lookup tables and resets the code cache) that
// costs the whole next frame in retranslation. Same-session replays keep
// their translations valid, so the run-ahead load can skip it.
constexpr uintptr_t kRaLoadJitClearSite = 0x1c694;
constexpr uint32_t  kRaNop = 0xd503201f;
constexpr uint32_t  kRaRet = 0xd65f03c0;
constexpr uintptr_t kRaComposeFn = 0x3cd78;   // 2D compose (both engines, up to a line); frame end calls it with line 191
constexpr uintptr_t kRaKick3dSite = 0x2c9c4;  // scanline 214: bl +0x5f3fc, hands the frame's geometry to the 3D worker
uint32_t gRaHiddenSaved[2] = {0, 0};
int      gRaHiddenMask = 0;                   // bit 1: compose skipped, bit 2: 3D kick skipped (while set)
// Experiment (ra_hidden_nop bitmask): the three calls after the frame end in
// the scanline routine, dropped during hidden frames to measure their cost.
constexpr uintptr_t kRaPostFrameSites[3] = {0x2caac, 0x2cabc, 0x2cac8};
uint32_t gRaPostSaved[3] = {0, 0, 0};
int      gRaPostMask = 0;
// Hidden replay frames need no picture: skip the 2D compose and the 3D worker
// kick (mask bit 1 / bit 2). Everything the state depends on still runs (the
// GX command parser runs as the game writes the FIFO, not in the kick).
constexpr uintptr_t kRaNo3dFlagOff = 0x8f42c;   // heapMaster byte; bit 3: the kick tells the worker not to render
void raHiddenRenderSkip(uint8_t* base, int mask) {
    if (!base || gRaHiddenMask) return;
    gRaHiddenMask = mask & 7;
    if (gRaHiddenMask & 1) gRaHiddenSaved[0] = raPatchInsn(base, kRaComposeFn, kRaRet);
    if (gRaHiddenMask & 2) gRaHiddenSaved[1] = raPatchInsn(base, kRaKick3dSite, kRaNop);
    if (gRaHiddenMask & 4) {
        uint8_t* hm = *reinterpret_cast<uint8_t**>(base + kRaMasterOff);
        if (hm) *(hm + kRaNo3dFlagOff) |= 8;
    }
    static int sPost = -1;
    if (sPost < 0) sPost = property_get_int32("sys.gammaos.drastic_nano.ra_hidden_nop", 0);
    gRaPostMask = sPost & 7;
    for (int i = 0; i < 3; i++) if (gRaPostMask & (1 << i)) gRaPostSaved[i] = raPatchInsn(base, kRaPostFrameSites[i], kRaNop);
}
void raHiddenRenderRestore(uint8_t* base) {
    if (!base || !gRaHiddenMask) return;
    if (gRaHiddenMask & 1) raPatchInsn(base, kRaComposeFn, gRaHiddenSaved[0]);
    if (gRaHiddenMask & 2) raPatchInsn(base, kRaKick3dSite, gRaHiddenSaved[1]);
    if (gRaHiddenMask & 4) {
        uint8_t* hm = *reinterpret_cast<uint8_t**>(base + kRaMasterOff);
        if (hm) *(hm + kRaNo3dFlagOff) &= (uint8_t)~8;
    }
    for (int i = 0; i < 3; i++) if (gRaPostMask & (1 << i)) raPatchInsn(base, kRaPostFrameSites[i], gRaPostSaved[i]);
    gRaPostMask = 0;
    gRaHiddenMask = 0;
}
// Savestate compression switch read by the save path (+0x7a934):
// master+0x8aac8 nonzero = zlib compress on the writer thread.
constexpr uint32_t kRaCompressOff = 0x8aac8;

// ---- DS memory-map remap dedup (GOT hook on libdrastic's mmap/munmap) ----
//
// drastic emulates the DS address space with page-granular MAP_FIXED
// mappings of two ashmem files (drastic_mapped_memory.dat, 4096 mappings,
// and drastic_mapped_memory_vram.dat, 513) and rebuilds them with a
// munmap + mmap pair per 16 KB page whenever a bank/mirror control changes
// (helpers at +0x20a08 / +0x20aac and siblings). A state load restores every
// control register and so remaps ~570 pages: ~1100 syscalls plus a refault
// of every page on the next frame, which measured as the whole 53 ms load
// cost and the 22 ms first frame after it. Almost all of those remaps
// re-create the mapping that is already there.
//
// The hook keeps a per-4K-page shadow of (fd, offset, prot, flags). munmap
// of known pages is deferred (marked pending, no syscall); a MAP_FIXED mmap
// that matches the shadow exactly clears the pending mark and returns without
// a syscall; anything else goes to the kernel and updates the shadow.
// Pending unmaps that were not re-mapped are flushed at the next frame
// boundary (drasticVWait) and after a RAM-state load, so drastic's view of
// what is mapped is restored before it can matter. libdrastic installs no
// SIGSEGV handler (only SIGINT), so nothing depends on faults from unmapped
// DS memory.
int64_t raTsNow();
extern std::atomic<int64_t> gRaLoadTs[6];
// Shadow of the DS memory mappings: fixed open-addressing table keyed by
// 4K page (a hash map cost ~4 ms per load in the 2120 hook calls).
struct RaMapEntry { uintptr_t page; int fd; off_t off; int prot; int flags; bool pending; bool used; };
constexpr size_t kRaMapSlots = 32768;   // power of two; ~6600 pages in use at most
RaMapEntry gRaMapTab[kRaMapSlots];
size_t gRaMapUsed = 0;
std::mutex gRaMapMu;
std::atomic<bool> gRaMapHookOn{false};
std::atomic<uint32_t> gRaMapSkipped{0}, gRaMapDeferred{0}, gRaMapReal{0}, gRaUnmapReal{0}, gRaFlushed{0};
size_t gRaPending = 0;   // pages marked pending (under gRaMapMu)
constexpr size_t kRaPage = 4096;
constexpr uintptr_t kRaGotMmap = 0x138970;
constexpr uintptr_t kRaGotMunmap = 0x1387f0;

inline size_t raMapHash(uintptr_t page) { return (size_t)((page >> 12) * 0x9E3779B97F4A7C15ull >> 40) & (kRaMapSlots - 1); }
// Find the entry for a page; nullptr if absent (insert=false) or the free slot to use.
inline RaMapEntry* raMapFind(uintptr_t page, bool insert) {
    size_t i = raMapHash(page);
    for (size_t n = 0; n < kRaMapSlots; n++, i = (i + 1) & (kRaMapSlots - 1)) {
        RaMapEntry& e = gRaMapTab[i];
        if (!e.used) { if (!insert) return nullptr; e.page = page; e.used = true; e.pending = false; gRaMapUsed++; return &e; }
        if (e.page == page) return &e;
    }
    return nullptr;
}
inline void raMapErase(RaMapEntry* e) {
    // Open addressing with linear probing: mark deleted by re-inserting the
    // cluster tail. Simpler: keep the slot used with fd = -1 (a tombstone
    // that never matches a mapping) so probes stay valid.
    e->fd = -1; e->off = -1; e->prot = -1; e->flags = -1; e->pending = false;
}
inline bool raMapLive(const RaMapEntry* e) { return e && e->used && e->fd >= 0; }

// Fast cache in front of the map table: the load remaps the same pages to the
// same mappings every time (munmap then mmap of an identical mapping). One
// entry per 16 KB address slot remembers the last identical mapping accepted
// by the slow path; a munmap of that exact range just marks it pending and
// an mmap of the identical tuple clears it, both without the table walk.
struct RaMapFast { uintptr_t addr; size_t len; int fd; off_t off; int prot; int flags; bool valid; bool pending; };
constexpr size_t kRaFastSlots = 8192;
RaMapFast gRaMapFast[kRaFastSlots];
inline RaMapFast& raFastSlot(uintptr_t a) { return gRaMapFast[(a >> 14) & (kRaFastSlots - 1)]; }
std::atomic<uint32_t> gRaMapFastHits{0};
// Hand a fast entry back to the table (under gRaMapMu): its deferred unmap
// becomes table-pending so the slow path treats the range as unmapped.
void raFastRetire(RaMapFast& f) {
    if (f.valid && f.pending) {
        for (size_t k = 0; k < f.len; k += kRaPage) {
            RaMapEntry* e = raMapFind(f.addr + k, false);
            if (raMapLive(e) && !e->pending) { e->pending = true; gRaPending++; }
        }
    }
    f.valid = false;
}
std::atomic<uint64_t> gRaMapHookNs{0};   // time spent in the mmap/munmap hooks
struct RaHookTimer { struct timespec t0; RaHookTimer() { clock_gettime(CLOCK_MONOTONIC, &t0); }
    ~RaHookTimer() { struct timespec t1; clock_gettime(CLOCK_MONOTONIC, &t1); gRaMapHookNs.fetch_add((uint64_t)((t1.tv_sec - t0.tv_sec) * 1000000000LL + (t1.tv_nsec - t0.tv_nsec)), std::memory_order_relaxed); } };
extern "C" void* raHookMmap(void* addr, size_t len, int prot, int flags, int fd, off_t off) {
    if (!gRaMapHookOn.load(std::memory_order_relaxed) || !addr || (flags & MAP_ANONYMOUS) ||
        ((uintptr_t)addr & (kRaPage - 1)) || (len & (kRaPage - 1)) || len == 0) {
        return mmap(addr, len, prot, flags, fd, off);
    }
    const uintptr_t a = (uintptr_t)addr;
    const int cmpFlags = flags & ~MAP_FIXED;
    {
        RaMapFast& f = raFastSlot(a);
        if (f.valid && f.pending && f.addr == a && f.len == len && f.fd == fd && f.off == off && f.prot == prot && f.flags == cmpFlags) {
            f.pending = false;
            gRaMapFastHits.fetch_add(1, std::memory_order_relaxed);
            gRaMapSkipped.fetch_add(1, std::memory_order_relaxed);
            return addr;
        }
    }
    RaHookTimer tm;
    std::lock_guard<std::mutex> lk(gRaMapMu);
    { RaMapFast& f = raFastSlot(a); if (f.valid && f.addr == a) raFastRetire(f); }   // slow path owns the slot again
    const size_t pages = len / kRaPage;
    size_t known = 0, pending = 0, same = 0;
    for (size_t k = 0; k < len; k += kRaPage) {
        const RaMapEntry* e = raMapFind(a + k, false);
        if (!raMapLive(e)) continue;
        known++;
        if (e->pending) pending++;
        if (e->fd == fd && e->off == off + (off_t)k && e->prot == prot && e->flags == cmpFlags) same++;
    }
    if (known == pages && same == pages) {
        for (size_t k = 0; k < len; k += kRaPage) {
            RaMapEntry* e = raMapFind(a + k, false);
            if (e && e->pending) { e->pending = false; gRaPending--; }
        }
        gRaMapSkipped.fetch_add(1, std::memory_order_relaxed);
        RaMapFast& f = raFastSlot(a);
        f = {a, len, fd, off, prot, cmpFlags, true, false};
        return addr;
    }
    int useFlags = flags;
    if (known == pages && pending == pages) {
        useFlags |= MAP_FIXED;
    } else if (pending > 0) {
        for (size_t k = 0; k < len; k += kRaPage) {
            RaMapEntry* e = raMapFind(a + k, false);
            if (raMapLive(e) && e->pending) {
                munmap((void*)(a + k), kRaPage);
                gRaFlushed.fetch_add(1, std::memory_order_relaxed);
                gRaPending--;
                raMapErase(e);
            }
        }
    }
    void* r = mmap(addr, len, prot, useFlags, fd, off);
    gRaMapReal.fetch_add(1, std::memory_order_relaxed);
    if (r == addr) raDirtyOnRemap(r, len, fd, off);
    for (size_t k = 0; k < len; k += kRaPage) {
        RaMapEntry* e = raMapFind(a + k, r == addr);
        if (!e) continue;
        if (raMapLive(e) && e->pending) gRaPending--;
        if (r == addr) { e->fd = fd; e->off = off + (off_t)k; e->prot = prot; e->flags = cmpFlags; e->pending = false; }
        else raMapErase(e);
    }
    return r;
}

extern "C" int raHookMunmap(void* addr, size_t len) {
    if (!gRaMapHookOn.load(std::memory_order_relaxed) || !addr ||
        ((uintptr_t)addr & (kRaPage - 1)) || (len & (kRaPage - 1)) || len == 0) {
        return munmap(addr, len);
    }
    const uintptr_t a = (uintptr_t)addr;
    {
        RaMapFast& f = raFastSlot(a);
        if (f.valid && !f.pending && f.addr == a && f.len == len) {
            f.pending = true;   // deferred: the identical mmap that follows clears it
            gRaMapDeferred.fetch_add(1, std::memory_order_relaxed);
            return 0;
        }
    }
    RaHookTimer tm;
    {
        const int64_t t = raTsNow();
        if (gRaLoadTs[3].load() < gRaLoadTs[2].load()) gRaLoadTs[3].store(t);
        gRaLoadTs[4].store(t);
    }
    std::lock_guard<std::mutex> lk(gRaMapMu);
    { RaMapFast& f = raFastSlot(a); if (f.valid && f.addr == a) raFastRetire(f); }
    bool known = true;
    for (size_t k = 0; k < len; k += kRaPage) {
        if (!raMapLive(raMapFind(a + k, false))) { known = false; break; }
    }
    if (known) {
        for (size_t k = 0; k < len; k += kRaPage) {
            RaMapEntry* e = raMapFind(a + k, false);
            if (!e->pending) { e->pending = true; gRaPending++; }
        }
        gRaMapDeferred.fetch_add(1, std::memory_order_relaxed);
        return 0;
    }
    const int rc = munmap(addr, len);
    gRaUnmapReal.fetch_add(1, std::memory_order_relaxed);
    for (size_t k = 0; k < len; k += kRaPage) {
        RaMapEntry* e = raMapFind(a + k, false);
        if (raMapLive(e)) { if (e->pending) gRaPending--; raMapErase(e); }
    }
    return rc;
}

// Perform the unmaps drastic asked for that were never re-mapped.
void raFlushDeferredUnmaps() {
    if (!gRaMapHookOn.load(std::memory_order_relaxed)) return;
    std::lock_guard<std::mutex> lk(gRaMapMu);
    if (gRaPending == 0) return;
    for (size_t i = 0; i < kRaMapSlots && gRaPending > 0; i++) {
        RaMapEntry& e = gRaMapTab[i];
        if (raMapLive(&e) && e.pending) {
            munmap((void*)e.page, kRaPage);
            gRaFlushed.fetch_add(1, std::memory_order_relaxed);
            gRaPending--;
            raMapErase(&e);
        }
    }
    gRaPending = 0;
}

bool raInstallMapHook(uint8_t* base) {
    if (gRaMapHookOn.load()) return true;
    const long ps = sysconf(_SC_PAGESIZE) > 0 ? sysconf(_SC_PAGESIZE) : 4096;
    uint8_t* lo = base + (kRaGotMunmap & ~(uintptr_t)(ps - 1));
    uint8_t* hi = base + (kRaGotMmap & ~(uintptr_t)(ps - 1));
    for (uint8_t* pg : {lo, hi}) {
        if (mprotect(pg, (size_t)ps, PROT_READ | PROT_WRITE) != 0) {
            ALOGW("DrasticRunner: map hook: mprotect(GOT) failed: %s", strerror(errno));
            return false;
        }
    }
    void** gotMmap = reinterpret_cast<void**>(base + kRaGotMmap);
    void** gotMunmap = reinterpret_cast<void**>(base + kRaGotMunmap);
    if (*gotMmap != (void*)&mmap || *gotMunmap != (void*)&munmap) {
        ALOGW("DrasticRunner: map hook: GOT slots do not hold mmap/munmap (%p %p vs %p %p); not installed",
              *gotMmap, *gotMunmap, (void*)&mmap, (void*)&munmap);
        return false;
    }
    *gotMmap = (void*)&raHookMmap;
    *gotMunmap = (void*)&raHookMunmap;
    __sync_synchronize();
    gRaMapHookOn.store(true);
    ALOGI("DrasticRunner: DS memory-map remap dedup hook installed");
    return true;
}

// ---- Direct state buffers (GOT hooks on malloc/free/fread/pthread_create) ----
//
// drastic's save path mallocs a 6.8 MB buffer, serializes into it (64-byte
// header + body), then starts a writer pthread (+0x7a1bc) that fwrites the
// image to the temp file and renames it; the load path mallocs the same
// size, freads the header and body from the slot file into it, and
// deserializes. With these hooks armed for the run-ahead slot:
//   malloc(0x680000)  -> the armed run-ahead buffer (pre-faulted, reused)
//   free(that buffer) -> no-op
//   pthread_create(writer) -> record the image length, close the FILE,
//                             clear the busy flag, no thread, no write
//   fread(into the armed load buffer) -> no copy, the image is already there
// so a save costs the serializer alone and a load the deserializer alone.
// The slot memfd only has to report the right size (ftruncate) for the
// load's ftell-based length computation; its contents are never read.
constexpr uintptr_t kRaGotMalloc = 0x138650;
constexpr uintptr_t kRaGotFree = 0x138608;
constexpr uintptr_t kRaGotFread = 0x138610;
constexpr uintptr_t kRaGotPthreadCreate = 0x138940;
constexpr uintptr_t kRaGotPthreadCreateData = 0x138ea0;
constexpr uintptr_t kRaGotMemcpy = 0x138498;
constexpr uintptr_t kRaGotMemmove = 0x138708;
constexpr uintptr_t kRaWriterThreadFn = 0x7a1bc;
constexpr size_t kRaStateBufSize = 0x680000;
constexpr size_t kRaStateHeader = 0x40;
constexpr int kRaJobFile = 2056, kRaJobStart = 2080, kRaJobEnd = 2088, kRaJobBusy = 2108;
constexpr int kRaJobDir = 0;            // virtual savestates directory ("User/savestates")
constexpr int kRaJobSlotName = 0x400;   // slot file name ("<rom>_<slot>.dss"); the writer joins them with '/'

std::atomic<bool> gRaStateHookOn{false};
uint8_t* gRaLibBase = nullptr;
std::atomic<uint8_t*> gRaSaveBuf{nullptr};
std::atomic<uint8_t*> gRaLoadBuf{nullptr};
std::atomic<bool>     gRaInSave{false};    // a ring save is running on the emulator thread
std::atomic<size_t> gRaSaveLen{0};
std::atomic<bool> gRaSaveDone{false};
std::atomic<uint32_t> gRaStatMallocHit{0}, gRaStatFreeSkip{0}, gRaStatFreadSkip{0}, gRaStatWriterBypass{0};
// Load timeline (us, steady clock) stamped by the hooks while a load buffer is armed:
// 0 malloc, 1 header fread, 2 body fread, 3 first remap, 4 last remap, 5 free.
std::atomic<int64_t> gRaLoadTs[6];
int64_t raTsNow() {
    return (int64_t)std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
}
// Registered run-ahead buffers: a fixed lock-free table, since the free
// hook consults it on every free() drastic makes.
constexpr int kRaMaxBufs = 16;
std::atomic<uint8_t*> gRaBufs[kRaMaxBufs];
std::atomic<int> gRaBufCount{0};

inline bool raInOurBuf(const void* p) {   // anywhere inside one of our state buffers
    const int n = gRaBufCount.load(std::memory_order_acquire);
    for (int i = 0; i < n; i++) {
        const uint8_t* b = gRaBufs[i].load(std::memory_order_relaxed);
        if (b && p >= b && p < b + kRaStateBufSize) return true;
    }
    return false;
}
inline bool raIsOurBuf(const void* p) {
    const int n = gRaBufCount.load(std::memory_order_acquire);
    for (int i = 0; i < n; i++) if (gRaBufs[i].load(std::memory_order_relaxed) == p) return true;
    return false;
}

extern "C" void* raHookMalloc(size_t n) {
    if (n == kRaStateBufSize && gRaStateHookOn.load(std::memory_order_relaxed)) {
        uint8_t* b = gRaSaveBuf.exchange(nullptr);
        if (!b) { b = gRaLoadBuf.load(); if (b) gRaLoadTs[0].store(raTsNow()); }
        if (b) { gRaStatMallocHit.fetch_add(1, std::memory_order_relaxed); return b; }
    }
    return malloc(n);
}

extern "C" void raHookFree(void* p) {
    if (p && gRaStateHookOn.load(std::memory_order_relaxed) && raIsOurBuf(p)) {
        gRaStatFreeSkip.fetch_add(1, std::memory_order_relaxed);
        gRaLoadTs[5].store(raTsNow());
        return;
    }
    free(p);
}

extern "C" size_t raHookFread(void* buf, size_t size, size_t n, FILE* f) {
    if (gRaStateHookOn.load(std::memory_order_relaxed)) {
        uint8_t* b = gRaLoadBuf.load();
        if (b && (buf == b || buf == b + kRaStateHeader)) {
            // header read (buf == b) then body read (buf == b + 0x40)
            gRaLoadTs[buf == b ? 1 : 2].store(raTsNow());
            if (buf == b + kRaStateHeader) gRaLoadBuf.store(nullptr);
            gRaStatFreadSkip.fetch_add(1, std::memory_order_relaxed);
            return n;
        }
    }
    return fread(buf, size, n, f);
}

extern "C" int raHookPthreadCreate(pthread_t* t, const pthread_attr_t* attr,
                                   void* (*fn)(void*), void* arg) {
    if (gRaStateHookOn.load(std::memory_order_relaxed) && gRaLibBase &&
        fn == reinterpret_cast<void* (*)(void*)>(gRaLibBase + kRaWriterThreadFn) && arg) {
        uint8_t* job = static_cast<uint8_t*>(arg);
        uint8_t* start = *reinterpret_cast<uint8_t**>(job + kRaJobStart);
        uint8_t* end = *reinterpret_cast<uint8_t**>(job + kRaJobEnd);
        if (raIsOurBuf(start)) {
            FILE* f = *reinterpret_cast<FILE**>(job + kRaJobFile);
            if (f) fclose(f);
            // The slot file the writer would have renamed the temp file to
            // must exist with this size for the load's length computation.
            {
                char vpath[0x820];
                snprintf(vpath, sizeof(vpath), "%s/%s", reinterpret_cast<const char*>(job + kRaJobDir),
                         reinterpret_cast<const char*>(job + kRaJobSlotName));
                fakejni::ramStateEnsureVirtual(vpath, (size_t)(end - start));
            }
            gRaSaveLen.store((size_t)(end - start));
            *reinterpret_cast<volatile uint32_t*>(job + kRaJobBusy) = 0;
            __sync_synchronize();
            gRaSaveDone.store(true);
            gRaStatWriterBypass.fetch_add(1, std::memory_order_relaxed);
            if (t) *t = 0;
            return 0;
        }
    }
    return pthread_create(t, attr, fn, arg);
}

// Flat mappings of the DS memory files (64 MB main, 8 MB VRAM), from /proc/self/maps.
uint8_t* gRaFlatMain = nullptr; size_t gRaFlatMainSz = 0;
uint8_t* gRaFlatVram = nullptr; size_t gRaFlatVramSz = 0;
void raFindFlatMaps() {
    if (gRaFlatMain) return;
    FILE* f = fopen("/proc/self/maps", "re");
    if (!f) return;
    char line[512];
    while (fgets(line, sizeof(line), f)) {
        unsigned long a = 0, b = 0, off = 0;
        char perm[8] = {0}; char path[256] = {0};
        if (sscanf(line, "%lx-%lx %7s %lx %*s %*s %255s", &a, &b, perm, &off, path) < 5) continue;
        if (off != 0) continue;
        if (strstr(path, "drastic_mapped_memory_vram") && b - a >= 0x800000) { gRaFlatVram = (uint8_t*)a; gRaFlatVramSz = b - a; }
        else if (strstr(path, "drastic_mapped_memory.dat") && b - a >= 0x4000000) { gRaFlatMain = (uint8_t*)a; gRaFlatMainSz = b - a; }
    }
    fclose(f);
    ALOGI("run-ahead flat maps: main %p (%zu MB) vram %p (%zu MB)", gRaFlatMain, gRaFlatMainSz >> 20, gRaFlatVram, gRaFlatVramSz >> 20);
}
const char* raRegionName(const void* p, char* buf, size_t n) {
    const uint8_t* q = (const uint8_t*)p;
    if (gRaFlatMain && q >= gRaFlatMain && q < gRaFlatMain + gRaFlatMainSz) { snprintf(buf, n, "main+0x%zx", (size_t)(q - gRaFlatMain)); return buf; }
    if (gRaFlatVram && q >= gRaFlatVram && q < gRaFlatVram + gRaFlatVramSz) { snprintf(buf, n, "vram+0x%zx", (size_t)(q - gRaFlatVram)); return buf; }
    for (int i = 0; i < gRaBufCount.load(); i++) {
        uint8_t* b = gRaBufs[i].load(std::memory_order_relaxed);
        if (b && q >= b && q < b + kRaStateBufSize) { snprintf(buf, n, "img+0x%zx", (size_t)(q - b)); return buf; }
    }
    if (gRaLibBase) {
        uint8_t* hm = *reinterpret_cast<uint8_t**>(gRaLibBase + kRaMasterOff);
        if (hm && q >= hm && q < hm + 0x4000000) { snprintf(buf, n, "heap+0x%zx", (size_t)(q - hm)); return buf; }
    }
    snprintf(buf, n, "%p", p); return buf;
}

// ---- DS memory dirty tracking (undo logs) -----------------------------------
// The 4 MB main RAM block is 78% of every state image and VRAM another 12%.
// Instead of copying them on each ring save and burst load, the views of each
// ashmem file (drastic's flat mapping and the 16 KB DS address-space window
// mappings) are write-protected; the first write to a 16 KB page since the
// last ring save faults here, the page's old content goes into the open undo
// log and that view is made writable. At the next ring save the open log is
// attached to the previous ring entry (it takes the memory from that save's
// time back to the previous one) and a fresh log opens. A burst load of an
// older entry replays the open log, then the attached logs newest to oldest.
constexpr size_t kRaDPage = 16384;
constexpr int    kRaDMaxPages = 256;       // main RAM: 4 MB; VRAM: 0xa4000 = 41 pages
constexpr int    kRaDMaxViews = 8;
constexpr int    kRaDLogs = kRaMaxRing + 2;
constexpr int    kRaDFiles = 2;
struct RaDLog { int n; uint16_t page[kRaDMaxPages]; uint8_t* data; };
struct RaDViewIdx { uintptr_t addr; uint16_t page; uint8_t view; uint8_t file; };
struct RaDTrack {
    const char* match;                    // /proc/self/maps path substring
    int pages;                            // tracked file pages (offset 0 .. pages * 16 KB)
    uint8_t* flat;                        // the flat view (offset 0), read side of every log copy and replay
    uint8_t* views[kRaDMaxPages][kRaDMaxViews];
    int viewN[kRaDMaxPages];
    int8_t flatView[kRaDMaxPages];        // index of the flat view in views[p] (-1: none)
    std::atomic<uint32_t> unprot[kRaDMaxPages];   // views currently writable (bit per view)
    volatile uint8_t inLog[kRaDMaxPages]; // page already in the open log
    RaDLog logs[kRaDLogs];
    int open;                             // open log slot
    int ringLog[kRaMaxRing];              // log attached to ring entry i (-1: none)
    bool restoredThisLoad;                // VRAM: replayed on the first of its chunk copies
    unsigned long ino;                    // the file's inode (remap registration by fd)
    uint8_t* rw;                          // alias of the tracked range (mremap of the shared pages), never protected
    uint8_t streak[kRaDMaxPages];         // consecutive frames the page was dirty
    uint8_t hot[kRaDMaxPages];            // hot: stays writable, pre-copied into every new log
    uint32_t hotCount;
    uint32_t framesSinceDemote;
};
constexpr int kRaDHotStreak = 3;          // dirty this many frames running -> hot
constexpr uint32_t kRaDHotDemoteFrames = 600;   // re-qualify hot pages every 10 s
std::atomic<uint32_t> gRaDStatHotPages{0}, gRaDStatHotCopies{0};
RaDTrack gRaDT[kRaDFiles] = {{"drastic_mapped_memory.dat", 256}, {"drastic_mapped_memory_vram.dat", 41}};
RaDViewIdx gRaDIdx[kRaDFiles * kRaDMaxPages * kRaDMaxViews];
int        gRaDIdxN = 0;
bool       gRaDInited = false;
std::atomic<bool> gRaDirtyOn{false};
int  gRaDVerify = -1;                         // ra_dirty_verify: keep the full copies and check the replay against them
std::atomic<uint32_t> gRaDStatVerifyBad{0}, gRaDStatVerifyRuns{0};
std::atomic<uint32_t> gRaDStatFaults{0}, gRaDStatFramePages{0}, gRaDStatMaxPages{0}, gRaDStatReplayPages{0}, gRaDStatReplays{0}, gRaDStatSkippedCopies{0}, gRaDStatRemaps{0};
std::atomic<uint64_t> gRaDStatSumPages{0};
std::atomic<uint64_t> gRaDStatFaultNs{0};     // time spent in the fault handler
std::atomic<uint64_t> gRaDStatRestoreUs{0};   // last replay duration (all files)
std::atomic<uint32_t> gRaDStatSaves{0};
std::atomic<uint32_t> gRaDStatFaultFlat{0}, gRaDStatFaultWin{0}, gRaDStatFaultVram{0};   // where the writes land
std::atomic<uint64_t> gRaDStatMprotectNs{0};

void raDAddView(int file, int page, uint8_t* addr) {
    RaDTrack& T = gRaDT[file];
    if (page < 0 || page >= T.pages) return;
    for (int i = 0; i < T.viewN[page]; i++) if (T.views[page][i] == addr) return;
    if (T.viewN[page] >= kRaDMaxViews || gRaDIdxN >= (int)(sizeof(gRaDIdx) / sizeof(gRaDIdx[0]))) return;
    const int v = T.viewN[page]++;
    T.views[page][v] = addr;
    int k = gRaDIdxN++;
    while (k > 0 && gRaDIdx[k - 1].addr > (uintptr_t)addr) { gRaDIdx[k] = gRaDIdx[k - 1]; k--; }
    gRaDIdx[k] = {(uintptr_t)addr, (uint16_t)page, (uint8_t)v, (uint8_t)file};
}
const RaDViewIdx* raDFind(uintptr_t a) {
    int lo = 0, hi = gRaDIdxN - 1;
    while (lo <= hi) {
        const int mid = (lo + hi) / 2;
        if (gRaDIdx[mid].addr <= a) lo = mid + 1; else hi = mid - 1;
    }
    if (hi < 0) return nullptr;
    const RaDViewIdx* e = &gRaDIdx[hi];
    return (a < e->addr + kRaDPage) ? e : nullptr;
}
int raDFileOf(const void* p) {   // file index whose tracked flat range holds p, else -1
    for (int f = 0; f < kRaDFiles; f++) {
        const RaDTrack& T = gRaDT[f];
        if (T.flat && (const uint8_t*)p >= T.flat && (const uint8_t*)p < T.flat + (size_t)T.pages * kRaDPage) return f;
    }
    return -1;
}
bool raDirtyInit() {
    if (gRaDInited) return true;
    FILE* f = fopen("/proc/self/maps", "re");
    if (!f) return false;
    char line[512];
    struct M { unsigned long a, b, off; int file; };
    static M maps[8192]; int nm = 0;
    while (fgets(line, sizeof(line), f)) {
        unsigned long a = 0, b = 0, off = 0, in = 0; char perm[8] = {0}; char path[256] = {0};
        if (sscanf(line, "%lx-%lx %7s %lx %*s %lu %255s", &a, &b, perm, &off, &in, path) < 6) continue;
        for (int fi = 0; fi < kRaDFiles; fi++) {
            RaDTrack& T = gRaDT[fi];
            if (!strstr(path, T.match)) continue;
            if (fi == 0 && strstr(path, "_vram")) continue;
            T.ino = in;
            if (off == 0 && b - a >= (unsigned long)T.pages * kRaDPage) T.flat = (uint8_t*)a;
            if (off < (unsigned long)T.pages * kRaDPage && nm < 8192) maps[nm++] = {a, b, off, fi};
        }
    }
    fclose(f);
    for (int fi = 0; fi < kRaDFiles; fi++) {
        RaDTrack& T = gRaDT[fi];
        if (!T.flat) { ALOGW("run-ahead dirty: no flat view for %s", T.match); return false; }
        for (int i = 0; i < T.pages; i++) T.flatView[i] = -1;
        for (int i = 0; i < kRaMaxRing; i++) T.ringLog[i] = -1;
        T.open = -1;
        for (int i = 0; i < kRaDLogs; i++) {
            if (T.logs[i].data) continue;
            void* p = mmap(nullptr, (size_t)T.pages * kRaDPage, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS | MAP_POPULATE, -1, 0);
            if (p == MAP_FAILED) { ALOGW("run-ahead dirty: log alloc failed"); return false; }
            T.logs[i].data = static_cast<uint8_t*>(p); T.logs[i].n = 0;
        }
    }
    int views = 0;
    for (int i = 0; i < nm; i++) {
        RaDTrack& T = gRaDT[maps[i].file];
        for (unsigned long o = maps[i].off, a = maps[i].a; a < maps[i].b && o < (unsigned long)T.pages * kRaDPage; o += kRaDPage, a += kRaDPage) {
            raDAddView(maps[i].file, (int)(o / kRaDPage), (uint8_t*)a); views++;
        }
    }
    int split = 0;
    for (int fi = 0; fi < kRaDFiles; fi++) {
        RaDTrack& T = gRaDT[fi];
        // Writable alias of the tracked pages: mremap with old_size 0 maps the
        // same shared pages again (no fd needed). Replay writes go through it.
        if (!T.rw) {
            void* al = mremap(T.flat, 0, (size_t)T.pages * kRaDPage, MREMAP_MAYMOVE);
            if (al != MAP_FAILED) T.rw = static_cast<uint8_t*>(al);
            else ALOGW("run-ahead dirty: alias mremap failed for %s: %s", T.match, strerror(errno));
        }
        for (int p = 0; p < T.pages; p++)
            for (int v = 0; v < T.viewN[p]; v++)
                if (T.views[p][v] == T.flat + (size_t)p * kRaDPage) T.flatView[p] = (int8_t)v;
        // Pre-split the flat mapping into fixed 16 KB VMAs over the tracked
        // range: alternating VM_DONTDUMP keeps neighbours from merging, so a
        // protection change on one page never splits or merges VMAs.
        for (int p = 1; p < T.pages; p += 2)
            if (madvise(T.flat + (size_t)p * kRaDPage, kRaDPage, MADV_DONTDUMP) == 0) split++;
    }
    gRaDInited = true;
    ALOGI("run-ahead dirty: %d mappings, %d page views (index %d), flats %p %p, aliases %p %p, %d logs per file, %d pages pre-split", nm, views, gRaDIdxN, gRaDT[0].flat, gRaDT[1].flat, gRaDT[0].rw, gRaDT[1].rw, kRaDLogs, split);
    return true;
}
void raDirtyProtectAll() {
    for (int fi = 0; fi < kRaDFiles; fi++) {
        RaDTrack& T = gRaDT[fi];
        for (int p = 0; p < T.pages; p++) {
            for (int v = 0; v < T.viewN[p]; v++) mprotect(T.views[p][v], kRaDPage, PROT_READ);
            T.unprot[p].store(0); T.inLog[p] = 0;
        }
    }
}
int raDFreeLog(RaDTrack& T) {
    for (int i = 0; i < kRaDLogs; i++) {
        if (i == T.open) continue;
        bool used = false;
        for (int r = 0; r < kRaMaxRing; r++) if (T.ringLog[r] == i) used = true;
        if (!used) return i;
    }
    return -1;
}
void raDReprotectLog(RaDTrack& T, int slot) {
    if (slot < 0) return;
    RaDLog& L = T.logs[slot];
    for (int i = 0; i < L.n; i++) {
        const int p = L.page[i];
        T.inLog[p] = 0;
        if (T.hot[p]) continue;          // stays writable; the next log gets its copy up front
        const uint32_t m = T.unprot[p].exchange(0);
        for (int v = 0; v < T.viewN[p] && m; v++) if (m & (1u << v)) mprotect(T.views[p][v], kRaDPage, PROT_READ);
    }
}
// Hot page bookkeeping at a save boundary: pages dirty kRaDHotStreak frames
// running stay writable and are copied into every new log up front (a 16 KB
// copy instead of a fault plus two mprotects, ~50 us of TLB shootdowns).
// Every kRaDHotDemoteFrames frames all hot pages are re-protected so pages
// that went cold stop costing a copy per frame.
void raDHotUpdate(RaDTrack& T, int closedSlot) {
    static uint8_t mark[kRaDMaxPages];
    memset(mark, 0, (size_t)T.pages);
    if (closedSlot >= 0) { RaDLog& L = T.logs[closedSlot]; for (int i = 0; i < L.n; i++) mark[L.page[i]] = 1; }
    const bool demote = ++T.framesSinceDemote >= kRaDHotDemoteFrames;
    if (demote) T.framesSinceDemote = 0;
    for (int p = 0; p < T.pages; p++) {
        if (demote && T.hot[p]) {
            T.hot[p] = 0; T.streak[p] = 0; T.hotCount--;
            const uint32_t m = T.unprot[p].exchange(0);
            for (int v = 0; v < T.viewN[p] && m; v++) if (m & (1u << v)) mprotect(T.views[p][v], kRaDPage, PROT_READ);
            continue;
        }
        if (T.hot[p]) continue;
        if (mark[p]) { if (T.streak[p] < 255) T.streak[p]++; if (T.streak[p] >= kRaDHotStreak) { T.hot[p] = 1; T.hotCount++; } }
        else T.streak[p] = 0;
    }
}
// A new log opened: hot pages are already writable, so record them now with
// their current content (the content at this frame boundary).
void raDHotPrime(RaDTrack& T) {
    if (T.open < 0 || !T.hotCount) return;
    RaDLog& L = T.logs[T.open];
    for (int p = 0; p < T.pages && L.n < T.pages; p++) {
        if (!T.hot[p]) continue;
        memcpy(L.data + (size_t)L.n * kRaDPage, T.flat + (size_t)p * kRaDPage, kRaDPage);
        L.page[L.n++] = (uint16_t)p;
        T.inLog[p] = 1;
        gRaDStatHotCopies.fetch_add(1, std::memory_order_relaxed);
    }
}
// Re-protect the pages of the open log and start a fresh one.
void raDirtyReopen(RaDTrack& T) {
    raDReprotectLog(T, T.open);
    const int slot = raDFreeLog(T);
    T.open = slot;
    if (slot >= 0) T.logs[slot].n = 0;
}
// Fault handler part: returns true when the address was a protected view.
bool raDirtyFault(void* addr) {
    if (!gRaDirtyOn.load(std::memory_order_relaxed)) return false;
    const RaDViewIdx* e = raDFind((uintptr_t)addr);
    if (!e) return false;
    RaDTrack& T = gRaDT[e->file];
    const int p = e->page;
    struct timespec t0; clock_gettime(CLOCK_MONOTONIC, &t0);
    gRaDStatFaults.fetch_add(1, std::memory_order_relaxed);
    if (e->file == 1) gRaDStatFaultVram.fetch_add(1, std::memory_order_relaxed);
    else if (T.flatView[p] == (int8_t)e->view) gRaDStatFaultFlat.fetch_add(1, std::memory_order_relaxed);
    else gRaDStatFaultWin.fetch_add(1, std::memory_order_relaxed);
    if (!T.inLog[p] && T.open >= 0) {
        RaDLog& L = T.logs[T.open];
        if (L.n < T.pages) {
            memcpy(L.data + (size_t)L.n * kRaDPage, T.flat + (size_t)p * kRaDPage, kRaDPage);
            L.page[L.n++] = (uint16_t)p;
        }
        T.inLog[p] = 1;
    }
    struct timespec tm0; clock_gettime(CLOCK_MONOTONIC, &tm0);
    mprotect(T.views[p][e->view], kRaDPage, PROT_READ | PROT_WRITE);
    T.unprot[p].fetch_or(1u << e->view);
    struct timespec t1; clock_gettime(CLOCK_MONOTONIC, &t1);
    gRaDStatMprotectNs.fetch_add((uint64_t)((t1.tv_sec - tm0.tv_sec) * 1000000000LL + (t1.tv_nsec - tm0.tv_nsec)), std::memory_order_relaxed);
    gRaDStatFaultNs.fetch_add((uint64_t)((t1.tv_sec - t0.tv_sec) * 1000000000LL + (t1.tv_nsec - t0.tv_nsec)), std::memory_order_relaxed);
    return true;
}
// Ring save of entry `next` completed (count before the save = countBefore).
void raDirtySaveBoundary(int next, int countBefore, int R) {
    if (!gRaDirtyOn.load()) return;
    const int prev = (next + R - 1) % R;
    uint32_t total = 0;
    for (int fi = 0; fi < kRaDFiles; fi++) {
        RaDTrack& T = gRaDT[fi];
        if (T.ringLog[next] >= 0) T.ringLog[next] = -1;   // the entry being overwritten frees its log
        if (T.open >= 0) total += (uint32_t)T.logs[T.open].n;
        const int closed = T.open;
        raDHotUpdate(T, closed);
        if (countBefore > 0 && T.open >= 0) {
            T.ringLog[prev] = T.open;   // attach to the previous entry, re-protect, open a new slot
            raDReprotectLog(T, T.open);
            T.open = -1;
            const int slot = raDFreeLog(T);
            T.open = slot;
            if (slot >= 0) T.logs[slot].n = 0;
        } else {
            raDirtyReopen(T);           // ring restart: discard, re-protect, fresh log
        }
        raDHotPrime(T);
    }
    { uint32_t h = 0; for (int fi = 0; fi < kRaDFiles; fi++) h += gRaDT[fi].hotCount; gRaDStatHotPages.store(h); }
    gRaDStatFramePages.store(total); gRaDStatSumPages.fetch_add(total); gRaDStatSaves.fetch_add(1);
    if (total > gRaDStatMaxPages.load()) gRaDStatMaxPages.store(total);
}
void raDApply(RaDTrack& T, int slot) {
    if (slot < 0) return;
    RaDLog& L = T.logs[slot];
    for (int i = 0; i < L.n; i++) {
        const int p = L.page[i];
        if (T.rw) { memcpy(T.rw + (size_t)p * kRaDPage, L.data + (size_t)i * kRaDPage, kRaDPage); continue; }
        uint8_t* dst = T.flat + (size_t)p * kRaDPage;
        const int fv = T.flatView[p];
        const bool locked = fv < 0 || !(T.unprot[p].load() & (1u << fv));
        if (locked) mprotect(dst, kRaDPage, PROT_READ | PROT_WRITE);
        memcpy(dst, L.data + (size_t)i * kRaDPage, kRaDPage);
        if (locked) mprotect(dst, kRaDPage, PROT_READ);
    }
    gRaDStatReplayPages.fetch_add((uint32_t)L.n);
}
// Burst load of ring entry `target`: bring one file back to its time.
void raDirtyRestore(int file, int target, int newest, int R) {
    RaDTrack& T = gRaDT[file];
    const int64_t t0 = raTsNow();
    if (file == 0) gRaDStatReplays.fetch_add(1);
    raDApply(T, T.open);
    if (target != newest) {
        for (int e = (newest + R - 1) % R; ; e = (e + R - 1) % R) {
            raDApply(T, T.ringLog[e]);
            if (e == target) break;
        }
    }
    gRaDStatRestoreUs.store((file == 0 ? 0 : gRaDStatRestoreUs.load()) + (uint64_t)(raTsNow() - t0));
}
// After a load the memory is at the loaded entry's time: the open log (pages
// dirtied since the newest save, now stale) is dropped and a fresh one starts
// here, so the hidden frames that follow log against this state and their
// saves attach consistent logs (consecutive bursts stay bit-exact).
void raDirtyPostLoad() {
    if (!gRaDirtyOn.load()) return;
    for (int fi = 0; fi < kRaDFiles; fi++) {
        RaDTrack& T = gRaDT[fi];
        raDirtyReopen(T);
        raDHotPrime(T);
        T.restoredThisLoad = false;
    }
}
void raDirtyResetLogs() {
    for (int fi = 0; fi < kRaDFiles; fi++)
        for (int i = 0; i < kRaMaxRing; i++) gRaDT[fi].ringLog[i] = -1;
}
// A real mmap of a tracked page landed: register the view, protect it if the
// page is not open for writing in this frame.
void raDirtyOnRemap(void* addr, size_t len, int fd, off_t off) {
    if (!gRaDInited || fd < 0) return;
    // which file: match the fd's inode against the flats via /proc is costly;
    // use the address instead (windows of a file sit in one virtual run).
    int file = -1;
    const RaDViewIdx* e = raDFind((uintptr_t)addr);
    if (e) file = e->file;
    else {
        struct stat st;
        if (fstat(fd, &st) == 0)
            for (int fi = 0; fi < kRaDFiles; fi++) if (gRaDT[fi].ino == (unsigned long)st.st_ino) file = fi;
    }
    if (file < 0) return;
    RaDTrack& T = gRaDT[file];
    for (size_t k = 0; k < len; k += kRaDPage) {
        const off_t o = off + (off_t)k;
        if (o < 0 || o >= (off_t)T.pages * kRaDPage) continue;
        const int p = (int)(o / kRaDPage);
        raDAddView(file, p, (uint8_t*)addr + k);
        gRaDStatRemaps.fetch_add(1);
        if (gRaDirtyOn.load() && !T.inLog[p]) mprotect((uint8_t*)addr + k, kRaDPage, PROT_READ);
    }
}
std::atomic<int> gRaDirtyWant{0};   // 1: run-ahead wants dirty tracking (applied on the emulator thread at a frame boundary)
void raDirtyEnable(bool on);
// Tracking is on only while run-ahead wants it AND the pacer lock is held:
// in bypass there are no ring saves, so the write faults would be pure cost
// on a scene that has no headroom (Black 2 lost 2 to 3 fps to them).
void raDirtyApplyWant() {
    const bool want = gRaDirtyWant.load(std::memory_order_relaxed) == 1 && gPaceOn.load(std::memory_order_relaxed);
    if (want != gRaDirtyOn.load()) raDirtyEnable(want);
}
// Runs on the emulator thread while parked (no writes in flight), so the
// protection and the handler state cannot race the emulation.
void raDirtyEnable(bool on) {
    if (on) {
        if (gRaDirtyOn.load()) return;
        if (!raDirtyInit()) return;
        for (int fi = 0; fi < kRaDFiles; fi++) {
            RaDTrack& T = gRaDT[fi];
            T.open = -1;
            for (int i = 0; i < kRaMaxRing; i++) T.ringLog[i] = -1;
            memset(T.hot, 0, sizeof(T.hot)); memset(T.streak, 0, sizeof(T.streak)); T.hotCount = 0; T.framesSinceDemote = 0;
            raDirtyReopen(T);
        }
        gRaDirtyOn.store(true);
        raDirtyProtectAll();
        ALOGI("run-ahead dirty tracking on");
    } else if (gRaDirtyOn.load()) {
        gRaDirtyOn.store(false);
        for (int fi = 0; fi < kRaDFiles; fi++) {
            RaDTrack& T = gRaDT[fi];
            for (int p = 0; p < T.pages; p++) {
                for (int v = 0; v < T.viewN[p]; v++) mprotect(T.views[p][v], kRaDPage, PROT_READ | PROT_WRITE);
                T.unprot[p].store(0); T.inLog[p] = 0;
            }
        }
        ALOGI("run-ahead dirty tracking off");
    }
}
std::atomic<int> gRaCopyLog{0};   // ra_copy_log: log large copies during the next saves/loads (count)
void raLogCopy(const char* what, void* d, const void* s, size_t n) {
    if (gRaCopyLog.load() <= 0) return;
    if (!(gRaInSave.load() || gRaBurst.load())) return;
    if (n < 4096) return;
    gRaCopyLog.fetch_sub(1);
    char a[48], b[48];
    ALOGI("run-ahead copy %s %s: t=%lld dst %s src %s %zu bytes", gRaInSave.load() ? "save" : "load", what,
          (long long)(raTsNow() % 10000000LL), raRegionName(d, a, sizeof(a)), raRegionName(s, b, sizeof(b)), n);
}
void* raHookMemcpy(void* d, const void* s, size_t n) {
    raLogCopy("memcpy", d, s, n);
    if (n >= kRaDPage && (n % kRaDPage) == 0 && gRaDirtyOn.load(std::memory_order_relaxed)) {
        if (gRaDVerify < 0) gRaDVerify = property_get_int32("sys.gammaos.drastic_nano.ra_dirty_verify", 0);
        const bool save = gRaInSave.load(std::memory_order_relaxed);
        const int sf = save ? raDFileOf(s) : -1;
        if (sf >= 0 && !gRaDVerify) {
            gRaDStatSkippedCopies.fetch_add(1, std::memory_order_relaxed);
            return d;   // ring save: this block is covered by the undo logs
        }
        const int df = save ? -1 : raDFileOf(d);
        if (df >= 0 && raInOurBuf(s)) {
            // ring images carry no main RAM / VRAM: the warm-up load keeps the
            // live memory (it is the newest state); a burst load replays the
            // undo logs, once per file, on the file's first chunk copy.
            if (gRaWarmLoad) return d;
            if (gRaBurst.load(std::memory_order_relaxed) && gRaParkRingIdx >= 0) {
                RaDTrack& T = gRaDT[df];
                if (df == 0) { for (int fi = 0; fi < kRaDFiles; fi++) gRaDT[fi].restoredThisLoad = false; }
                if (!T.restoredThisLoad) {
                    T.restoredThisLoad = true;
                    const int R = gRaFrames.load() + 1;
                    raDirtyRestore(df, gRaParkRingIdx, (gRaRingNext.load() + R - 1) % R, R);
                }
                if (!gRaDVerify) return d;
                // verify: the replayed memory must equal the image's full copy
                uint32_t bad = 0; int first = -1;
                for (size_t k = 0; k < n; k += kRaDPage)
                    if (memcmp((const uint8_t*)d + k, (const uint8_t*)s + k, kRaDPage) != 0) { bad++; if (first < 0) first = (int)(((const uint8_t*)d + k - T.flat) / kRaDPage); }
                gRaDStatVerifyRuns.fetch_add(1);
                if (bad) {
                    gRaDStatVerifyBad.fetch_add(1);
                    ALOGW("run-ahead dirty VERIFY: file %d: %u pages differ after replay (first page %d), ring target %d", df, bad, first, gRaParkRingIdx);
                }
                // fall through to the real copy (through the protected flat view: faults are handled)
            }
        }
    }
    if (n == 98304 && gRaInSave.load(std::memory_order_relaxed) && raInOurBuf(d)) return d;   // stale thumbnails
    return memcpy(d, s, n);
}
void* raHookMemmove(void* d, const void* s, size_t n) { raLogCopy("memmove", d, s, n); return memmove(d, s, n); }

bool raInstallStateHooks(uint8_t* base) {
    if (gRaStateHookOn.load()) return true;
    const long ps = sysconf(_SC_PAGESIZE) > 0 ? sysconf(_SC_PAGESIZE) : 4096;
    const uintptr_t slots[] = {kRaGotMalloc, kRaGotFree, kRaGotFread, kRaGotPthreadCreate, kRaGotPthreadCreateData, kRaGotMemcpy, kRaGotMemmove};
    for (uintptr_t off : slots) {
        uint8_t* pg = base + (off & ~(uintptr_t)(ps - 1));
        if (mprotect(pg, (size_t)ps, PROT_READ | PROT_WRITE) != 0) {
            ALOGW("DrasticRunner: state hooks: mprotect(GOT) failed: %s", strerror(errno));
            return false;
        }
    }
    void** gMalloc = reinterpret_cast<void**>(base + kRaGotMalloc);
    void** gFree = reinterpret_cast<void**>(base + kRaGotFree);
    void** gFread = reinterpret_cast<void**>(base + kRaGotFread);
    void** gPc = reinterpret_cast<void**>(base + kRaGotPthreadCreate);
    void** gPcData = reinterpret_cast<void**>(base + kRaGotPthreadCreateData);
    if (*gMalloc != (void*)&malloc || *gFree != (void*)&free || *gFread != (void*)&fread ||
        *gPc != (void*)&pthread_create) {
        ALOGW("DrasticRunner: state hooks: GOT slots unexpected (malloc %p/%p free %p/%p fread %p/%p pthread_create %p/%p); not installed",
              *gMalloc, (void*)&malloc, *gFree, (void*)&free, *gFread, (void*)&fread, *gPc, (void*)&pthread_create);
        return false;
    }
    gRaLibBase = base;
    *gMalloc = (void*)&raHookMalloc;
    *gFree = (void*)&raHookFree;
    *gFread = (void*)&raHookFread;
    *gPc = (void*)&raHookPthreadCreate;
    if (*gPcData == (void*)&pthread_create) *gPcData = (void*)&raHookPthreadCreate;
    {
        void** gMc = reinterpret_cast<void**>(base + kRaGotMemcpy);
        void** gMm = reinterpret_cast<void**>(base + kRaGotMemmove);
        if (*gMc == (void*)&memcpy) *gMc = (void*)&raHookMemcpy; else ALOGW("DrasticRunner: memcpy GOT slot unexpected %p", *gMc);
        if (*gMm == (void*)&memmove) *gMm = (void*)&raHookMemmove; else ALOGW("DrasticRunner: memmove GOT slot unexpected %p", *gMm);
        raFindFlatMaps();
    }
    __sync_synchronize();
    gRaStateHookOn.store(true);
    ALOGI("DrasticRunner: direct state buffer hooks installed");
    return true;
}

bool raDumpFile(const char* path, const std::vector<uint8_t>& v) {
    int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
    if (fd < 0) return false;
    size_t done = 0;
    while (done < v.size()) {
        ssize_t w = write(fd, v.data() + done, v.size() - done);
        if (w <= 0) break;
        done += (size_t)w;
    }
    close(fd);
    return done == v.size();
}
} // namespace

bool DrasticRunner::stepModeActive() const { return gStepMode.load(); }

bool DrasticRunner::setStepMode(bool on) {
    if (on) {
        if (!mPaceInstalled || !mArm64Base) {
            ALOGW("DrasticRunner::setStepMode: vblank pacing hooks not installed");
            return false;
        }
        if (gStepMode.load()) return true;
        gStepMode.store(true);
        if (!gPaceOn.load()) {
            // Take the lock regardless of the presenter's bypass decision.
            gVirtBaseUs.store((int64_t)realClockUs());
            gVirtBaseSeq.store(gVblSeq.load());
            gPaceOn.store(true);
            { std::lock_guard<std::mutex> lk(gPaceMu); }
            gPaceCv.notify_all();
        }
        // A pacer tick already scheduled (sleeping) still lands; let it.
        usleep(40000);
        const bool parked = waitEmuParked(500000);
        ALOGI("DrasticRunner: step mode on (parked=%d)", parked ? 1 : 0);
        return parked;
    }
    if (!gStepMode.load()) return true;
    gStepMode.store(false);
    { std::lock_guard<std::mutex> lk(gPaceMu); }
    gPaceCv.notify_all();
    gEmuDurUs.store(6000);
    setVblankPacing(mPaceWanted);
    ALOGI("DrasticRunner: step mode off");
    return true;
}

bool DrasticRunner::waitEmuParked(int timeoutUs) {
    std::unique_lock<std::mutex> lk(gParkMu);
    return gParkCv.wait_for(lk, std::chrono::microseconds(timeoutUs),
                            [] { return gEmuParked.load(); });
}

bool DrasticRunner::stepOneFrame(int timeoutUs) {
    if (!gStepMode.load() || !gPaceOn.load()) return false;
    if (!waitEmuParked(timeoutUs)) return false;
    const uint32_t v0 = raStepTick();
    return raStepWait(v0, timeoutUs);
}

bool DrasticRunner::ramStateSave(RamStateTiming* t, int timeoutUs) {
    if (!mInitialized || !mSaveState || !mArm64Base) return false;
    volatile uint8_t* req  = mArm64Base + kRaMasterOff + kRaSaveReqOff;
    volatile uint8_t* busy = mArm64Base + kRaWriterBusyOff;
    // A writer still running from the previous save would stall drastic's
    // save inside the frame; wait it out first.
    if (!raPollZero(busy, timeoutUs)) return false;
    typedef int (*saveState4_t)(void*, void*, int, int);
    const bool step = gStepMode.load();
    if (step && !waitEmuParked(timeoutUs)) return false;
    int64_t t0 = raNowUs();
    reinterpret_cast<saveState4_t>(mSaveState)(mFakeEnv, mFakeCls, kRamStateSlot, 0);
    uint32_t v0 = 0;
    if (step) { v0 = raStepTick(); t0 = gLastStepTickUs.load(); }
    bool ok = raPollZero(req, timeoutUs);
    const int64_t t1 = raNowUs();
    if (step) ok = raStepWait(v0, timeoutUs) && ok;
    const int64_t tf = raNowUs();
    ok = raPollZero(busy, timeoutUs) && ok;
    const int64_t t2 = raNowUs();
    if (t) {
        t->requestUs = t1 - t0;
        t->writerUs  = t2 - t0;
        t->frameUs   = step ? tf - t0 : 0;
        t->bytes = 0;
        struct stat st = {};
        int fd = fakejni::ramStateFd(kRamStateSlot);
        if (fd >= 0 && fstat(fd, &st) == 0) t->bytes = (size_t)st.st_size;
    }
    return ok;
}

bool DrasticRunner::ramStateLoad(int64_t* loadUs, int timeoutUs) {
    if (!mInitialized || !mLoadState || !mArm64Base) return false;
    if (fakejni::ramStateFd(kRamStateSlot) < 0) return false;
    volatile uint8_t* req  = mArm64Base + kRaMasterOff + kRaLoadReqOff;
    volatile uint8_t* busy = mArm64Base + kRaWriterBusyOff;
    if (!raPollZero(busy, timeoutUs)) return false;
    const bool step = gStepMode.load();
    if (step && !waitEmuParked(timeoutUs)) return false;
    int64_t t0 = raNowUs();
    mLoadState(mFakeEnv, mFakeCls, kRamStateSlot);
    uint32_t v0 = 0;
    if (step) { v0 = raStepTick(); t0 = gLastStepTickUs.load(); }
    bool ok = raPollZero(req, timeoutUs);
    const int64_t t1 = raNowUs();
    if (step) ok = raStepWait(v0, timeoutUs) && ok;
    raFlushDeferredUnmaps();
    if (loadUs) *loadUs = t1 - t0;
    return ok;
}

bool DrasticRunner::ramStateCopyOut(std::vector<uint8_t>& out) const {
    return fakejni::ramStateCopyOut(kRamStateSlot, out);
}

bool DrasticRunner::ramStateCopyIn(const void* data, size_t len) {
    return fakejni::ramStateCopyIn(kRamStateSlot, data, len);
}

namespace {
void raApplySlotRedirect() {
    if (!gZcBss || gZcSwapState.load() != 2) return;
    uint8_t** slots = reinterpret_cast<uint8_t**>(gZcBss);
    uint8_t* nb = gZcNewBase.load();
    if (!nb) return;
    static int sRedirectOn = -1;
    if (sRedirectOn < 0) sRedirectOn = property_get_bool("sys.gammaos.drastic_nano.ra_slot_redirect", true) ? 1 : 0;
    const bool want = sRedirectOn && gRaBurst.load(std::memory_order_acquire) && gRaMode.load() == 2;
    if (want && !gRaSlotsRedirected) {
        if (!gRaScratchSlots) {
            void* p = mmap(nullptr, 2 * 0x180000, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS | MAP_POPULATE, -1, 0);
            if (p == MAP_FAILED) return;
            gRaScratchSlots = static_cast<uint8_t*>(p);
        }
        // No seeding copy: hidden frames render every line and are never
        // shown, and the 3 MB copy cost ~1.5 ms per burst.
        slots[0] = gRaScratchSlots; slots[1] = gRaScratchSlots + 0x180000;
        __sync_synchronize();
        gRaSlotsRedirected = true;
    } else if (!want && gRaSlotsRedirected) {
        slots[0] = nb; slots[1] = nb + 0x180000;
        __sync_synchronize();
        gRaSlotsRedirected = false;
    }
}

// Runs on the emulator thread while parked in drasticVWait.
void raRunParkedOp() {
    const int op = gRaParkOp.load(std::memory_order_acquire);
    if (!op) return;
    raApplySlotRedirect();
    DrasticRunner* r = DrasticRunner::getInstance();
    uint8_t* base = r ? r->libBase() : nullptr;
    bool ok = false;
    if (base) {
        uint8_t* heapMaster = *reinterpret_cast<uint8_t**>(base + kRaMasterOff);
        raJoin3dWorker(base);
        if (op == 1) {
            typedef int (*loadFn_t)(void*, int, void*, void*, int);
            if (gRaHashCheck && gRaParkRingIdx >= 0) {
                const uint64_t h = raFnv(gRaParkBuf, gRaParkLen);
                const uint32_t* hdr = reinterpret_cast<const uint32_t*>(gRaParkBuf + 0x20);
                ALOGI("run-ahead load check: ring[%d] buf %p len %zu hash %016llx %s saved %016llx, hdr ver %u flags 0x%x",
                      gRaParkRingIdx, gRaParkBuf, gRaParkLen, (unsigned long long)h,
                      h == gRaRingHash[gRaParkRingIdx] ? "==" : "!=", (unsigned long long)gRaRingHash[gRaParkRingIdx],
                      hdr[0], hdr[1]);
            }
            if (fakejni::ramStateSetSize(DrasticRunner::kRamStateSlot, gRaParkLen)) {
                gRaLoadBuf.store(gRaParkBuf);
                const bool skipJit = gRaJitSkipOn && gRaParkGen == gRaCodeGen;
                uint32_t was = 0;
                if (skipJit) { was = raPatchInsn(base, kRaLoadJitClearSite, kRaNop); gRaStatJitSkipped.fetch_add(1); }
                else gRaStatJitFull.fetch_add(1);
                reinterpret_cast<loadFn_t>(base + 0x7acc4)(heapMaster, DrasticRunner::kRamStateSlot, nullptr, nullptr, 0);
                if (skipJit) raPatchInsn(base, kRaLoadJitClearSite, was);
                gRaLoadBuf.store(nullptr);
                raDirtyPostLoad();
                { static int n = 0; if (gRaHashCheck && n++ < 60) raLogGx("after load"); }
                // GX FIFO parser reset (ra_gx_reset, default on): the geometry pending in the GX
                // FIFOs belongs to the timeline just discarded; the first
                // replayed frame resubmits its own and is hidden anyway. Reset
                // both FIFOs to their buffer starts (what the compaction at
                // +0x63af8 leaves behind when nothing is pending).
                static int sGxReset = -1;
                if (sGxReset < 0) sGxReset = property_get_bool("sys.gammaos.drastic_nano.ra_gx_reset", true) ? 1 : 0;
                if (sGxReset == 1 && gRaGx && !gRaWarmLoad) {
                    uint8_t** f = reinterpret_cast<uint8_t**>(gRaGx + 0x9a68);
                    f[0] = gRaGx + 0x79b00; f[2] = gRaGx + 0x79b00;   // cmd read / write
                    f[1] = gRaGx + 0x81b00; f[3] = gRaGx + 0x81b00;   // vtx read / write
                    *(gRaGx + 0x9a30 + 154) = 0;
                    *(gRaGx + 0x9ac1) = 0;   // parameter words still expected by the command in flight
                }
                // Retire the buffer just loaded from (see gRaRetired).
                if (gRaParkRingIdx >= 0 && gRaRetired[gRaRetiredIdx]) {
                    uint8_t* fresh = gRaRetired[gRaRetiredIdx];
                    gRaRetired[gRaRetiredIdx] = gRaRing[gRaParkRingIdx];
                    gRaRing[gRaParkRingIdx] = fresh;
                    gRaRetiredIdx ^= 1;
                    gRaParkRingIdx = -1;
                }
                raFlushDeferredUnmaps();
                // Re-latch the LIVE input. The per-frame hook (+0x16e74) copies
                // the JNI input words into heap+0x80010 at the end of every
                // frame and the next frame reads that copy; the restored state
                // carries the latch of the old frame, so without this the first
                // replayed frame would run with the old input and the replay
                // would gain nothing.
                if (property_get_bool("sys.gammaos.drastic_nano.ra_relatch", true)) {
                    uint8_t* st = base + kRaMasterOff;
                    uint8_t* latch = heapMaster + 0x80010;
                    uint32_t mask = *reinterpret_cast<volatile uint32_t*>(st + 0x48c);
                    if (st[0x4c0]) mask |= 0x1000;
                    *reinterpret_cast<uint32_t*>(latch) = mask;
                    memcpy(latch + 4, st + 0x494, 8);
                    latch[12] = st[0x4bf];
                }
                ok = true;
            }
        } else if (op == 2) {
            typedef int (*saveFn_t)(int);
            gRaSaveDone.store(false);
            gRaSaveBuf.store(gRaParkBuf);
            reinterpret_cast<saveFn_t>(base + 0x17308)(DrasticRunner::kRamStateSlot);
            gRaSaveBuf.store(nullptr);
            ok = gRaSaveDone.load();
            if (ok) gRaParkLen = gRaSaveLen.load();
        }
    }
    gRaParkOk.store(ok);
    gRaParkOp.store(0, std::memory_order_release);
    { std::lock_guard<std::mutex> pk(gParkMu); gRaParkDone.store(true); }
    gParkCv.notify_all();
}

// Threaded 3D: in the per-band pipeline the 3D worker rasterizes frame t
// while the CPU emulates t+1, so at the park point it may still be
// consuming the geometry FIFO. A state serialized then carried inconsistent
// FIFO pointers and the restore crashed in the FIFO compaction (+0x63bc4).
// Join the worker first (drastic's own join, +0x5f4b4 on video+0x1056c0,
// idempotent when idle) before any park-point save or load.
uint8_t* gRaVideo = nullptr;   // validated video struct pointer (see raFindVideo)
// GX FIFO pointers (video+0x9a68 cmd read, +0x9a70 vtx read, +0x9a78 cmd
// write, +0x9a80 vtx write; buffers at video+0x79b00 / +0x81b00).
static void raLogGx(const char* when) {
    if (!gRaGx) return;
    const uint8_t* p[4];
    for (int i = 0; i < 4; i++) p[i] = *reinterpret_cast<uint8_t**>(gRaGx + 0x9a68 + 8 * i);
    ALOGW("run-ahead GX %s: cmd r %+lld w %+lld (pend %lld), vtx r %+lld w %+lld (pend %lld) [rel gx, cmd buf +0x79b00, vtx buf +0x81b00]", when,
          (long long)(p[0] - gRaGx), (long long)(p[2] - gRaGx), (long long)(p[2] - p[0]),
          (long long)(p[1] - gRaGx), (long long)(p[3] - gRaGx), (long long)(p[3] - p[1]));
}
bool gRa3dJoinOn = false;
// The frame-end routine (+0x3cf88) takes the video struct; the sync caves
// derive it as [[engineA - 0x2e78] + 0xfba68]. Log the candidates once and
// keep the one whose threaded-3D flag word and worker mutex look sane.
void raFindVideo(uint8_t* base) {
    uint8_t* heapMaster = *reinterpret_cast<uint8_t**>(base + kRaMasterOff);
    uint8_t* st = base + kRaMasterOff;
    uint8_t* candA = heapMaster ? *reinterpret_cast<uint8_t**>(heapMaster + 0xfba68) : nullptr;
    uint8_t* candB = *reinterpret_cast<uint8_t**>(st + 0xfba68);
    ALOGW("run-ahead video candidates: heapMaster %p, [heap+0xfba68] %p, [static+0xfba68] %p, heap 3D flag %u",
          heapMaster, candA, candB, heapMaster ? *reinterpret_cast<uint32_t*>(heapMaster + 0x8aac0) : 0u);
    // The frame loop passes heapMaster + 0x36d6ec0 to both the 3D kick
    // (+0x2c9c4) and the frame-end routine (+0x2caa0): that is the video
    // struct (the GPU serializer component starts there too).
    gRaVideo = heapMaster ? heapMaster + 0x36d6ec0 : nullptr;
    // render = heapMaster + 0x36d6ec0; its first field points to the struct
    // ("master" in the sync-cave notes) that holds video at +0xfba68 and the
    // 3D engine object at +0xfba78 (+0x314ec: x22 = [x0], gx = [x22+0xfba78],
    // [gx+0x9a30] = heapMaster).
    auto sane = [](const void* q) { const uintptr_t v = (uintptr_t)q; return v > 0x10000 && v < 0x8000000000ull && (v & 7) == 0; };
    uint8_t* masterP = gRaVideo ? *reinterpret_cast<uint8_t**>(gRaVideo) : nullptr;
    uint8_t* gx = sane(masterP) ? *reinterpret_cast<uint8_t**>(masterP + 0xfba78) : nullptr;
    const bool gxOk = sane(gx) && *reinterpret_cast<uint8_t**>(gx + 0x9a30) == heapMaster;
    gRaGx = gxOk ? gx : nullptr;
    uint8_t* vp = sane(masterP) ? *reinterpret_cast<uint8_t**>(masterP + 0xfba68) : nullptr;
    gRaVideoP = sane(vp) ? vp : nullptr;
    ALOGW("run-ahead master' %p gx object %p (%s)", masterP, gx, gxOk ? "back-pointer ok" : "NOT validated");
    if (gRaGx) raLogGx("at enable");
}
void raJoin3dWorker(uint8_t* base) {
    if (!gRa3dJoinOn || !gRaVideo) return;
    uint8_t* heapMaster = *reinterpret_cast<uint8_t**>(base + kRaMasterOff);
    if (!heapMaster || *reinterpret_cast<uint32_t*>(heapMaster + 0x8aac0) == 0) return;   // threaded 3D off
    typedef void (*join_t)(void*);
    reinterpret_cast<join_t>(base + 0x5f4b4)(gRaVideo + 0x1056c0);
}

// Emulator thread, at park: serialize the state at the end of the frame
// just emulated into ring[next] (drastic's internal save routine, writer
// bypassed) and advance the ring. Bursts refresh entries the same way as
// their hidden frames park.
void raRingAutoSave() {
    DrasticRunner* r = DrasticRunner::getInstance();
    uint8_t* base = r ? r->libBase() : nullptr;
    const int R = gRaFrames.load() + 1;
    const int next = gRaRingNext.load();
    if (!base || R < 2 || !gRaRing[next]) {
        static int n = 0; if (n++ < 3) ALOGW("run-ahead auto-save skipped: base %p R %d ring[%d] %p", base, R, next, gRaRing[next]);
        return;
    }
    typedef int (*saveFn_t)(int);
    // No 3D worker join here: the serializer only reads state the worker
    // never writes, and joining at every park would serialize the 3D render
    // into the frame period (pushed this scene over the pacer's bypass
    // threshold, which then idles run-ahead). Loads do join.
    { static int n = 0; if (gRaHashCheck && gRaBurst.load() && n++ < 60) raLogGx("hidden park"); }
    // Only save when it fits before the next tick: a save still running when
    // the tick lands delays the frame start, inflates the pacer's frame-time
    // estimate and trips its bypass (which idles run-ahead). During a burst
    // the tick timing is ours, so hidden frames always save.
    const int64_t t0 = raTsNow();
    // Hidden replay frames save (ra_hidden_save=1, default, RetroArch
    // preempt_run order): the ring stays full through a burst, so the very
    // next frame can replay again when the input changes again. With the
    // dirty tracking a save is about 1 ms.
    static int sHiddenSave = -1;
    if (sHiddenSave < 0) sHiddenSave = property_get_int32("sys.gammaos.drastic_nano.ra_hidden_save", 1);
    if (gRaBurst.load() && !sHiddenSave) return;
    if (gRaBurst.load()) {
        // Hidden frame: its compose was skipped, so nothing joined the 3D
        // worker; join here so the saved geometry state is the finished one.
        static int sHiddenJoin = -1;
        if (sHiddenJoin < 0) sHiddenJoin = property_get_int32("sys.gammaos.drastic_nano.ra_hidden_join", 0);
        if (sHiddenJoin) raJoin3dWorker(base);
    }
    if (!gRaBurst.load()) {
        const int64_t due = gPacerNextTickUs.load();
        if (due > 0 && due - t0 < gRaSaveCostUs.load() + 500) {
            gRaRingCount.store(0);
            gRaStatSaveSkipped.fetch_add(1);
            return;
        }
    }
    gRaSaveDone.store(false);
    gRaSaveBuf.store(gRaRing[next]);
    gRaInSave.store(true);
    reinterpret_cast<saveFn_t>(base + 0x17308)(DrasticRunner::kRamStateSlot);
    gRaInSave.store(false);
    gRaSaveBuf.store(nullptr);
    if (!gRaSaveDone.load()) { gRaRingCount.store(0); return; }
    {
        const int64_t dt = raTsNow() - t0;
        const int64_t c = gRaSaveCostUs.load();
        gRaSaveCostUs.store((c * 7 + dt) / 8);
    }
    gRaRingLen[next] = gRaSaveLen.load();
    gRaRingGen[next] = gRaCodeGen;
    raDirtySaveBoundary(next, gRaRingCount.load(), R);
    if (gRaHashCheck) gRaRingHash[next] = raFnv(gRaRing[next], gRaRingLen[next]);
    gRaRingNext.store((next + 1) % R);
    const int c = gRaRingCount.load();
    if (c < R) gRaRingCount.store(c + 1);
}

// Ask the parked emulator thread to run a save (op 2) or load (op 1) now.
bool raParkedOp(int op, uint8_t* buf, size_t* len, int timeoutUs) {
    if (!gEmuParked.load()) return false;
    gRaParkBuf = buf; gRaParkLen = len ? *len : 0;
    gRaParkDone.store(false);
    gRaParkOp.store(op, std::memory_order_release);
    { std::lock_guard<std::mutex> lk(gPaceMu); }
    gPaceCv.notify_all();
    std::unique_lock<std::mutex> lk(gParkMu);
    const bool done = gParkCv.wait_for(lk, std::chrono::microseconds(timeoutUs), [] { return gRaParkDone.load(); });
    if (!done) { gRaParkOp.store(0); return false; }
    if (len && op == 2) *len = gRaParkLen;
    return gRaParkOk.load();
}
} // namespace

bool DrasticRunner::ramStateInstallHooks() {
    if (!mArm64Base) return false;
    uint8_t* heapMaster = *reinterpret_cast<uint8_t**>(mArm64Base + kRaMasterOff);
    if (heapMaster) *reinterpret_cast<volatile uint32_t*>(heapMaster + kRaCompressOff) = 0;
    const bool a = property_get_bool("sys.gammaos.drastic_nano.ra_mmap_dedup", true) ? raInstallMapHook(mArm64Base) : true;
    const bool b = raInstallStateHooks(mArm64Base);
    return a && b;
}

uint8_t* DrasticRunner::ramStateAllocBuffer() {
    void* p = mmap(nullptr, kRaStateBufSize, PROT_READ | PROT_WRITE,
                   MAP_PRIVATE | MAP_ANONYMOUS | MAP_POPULATE, -1, 0);
    if (p == MAP_FAILED) return nullptr;
    memset(p, 0, kRaStateBufSize);   // fault every page in now
    const int n = gRaBufCount.load();
    if (n >= kRaMaxBufs) { munmap(p, kRaStateBufSize); return nullptr; }
    gRaBufs[n].store(static_cast<uint8_t*>(p));
    gRaBufCount.store(n + 1, std::memory_order_release);
    return static_cast<uint8_t*>(p);
}

size_t DrasticRunner::ramStateBufferSize() const { return kRaStateBufSize; }

bool DrasticRunner::ramStateSaveTo(uint8_t* buf, size_t* lenOut, RamStateTiming* t, int timeoutUs) {
    if (!gRaStateHookOn.load() || !buf || !raIsOurBuf(buf)) return false;
    if (!mInitialized || !mSaveState || !mArm64Base) return false;
    volatile uint8_t* req  = mArm64Base + kRaMasterOff + kRaSaveReqOff;
    volatile uint8_t* busy = mArm64Base + kRaWriterBusyOff;
    if (!raPollZero(busy, timeoutUs)) return false;
    typedef int (*saveState4_t)(void*, void*, int, int);
    const bool step = gStepMode.load();
    if (step && !waitEmuParked(timeoutUs)) return false;
    gRaSaveDone.store(false);
    gRaSaveBuf.store(buf);
    int64_t t0 = raNowUs();
    reinterpret_cast<saveState4_t>(mSaveState)(mFakeEnv, mFakeCls, kRamStateSlot, 0);
    uint32_t v0 = 0;
    if (step) { v0 = raStepTick(); t0 = gLastStepTickUs.load(); }
    bool ok = raPollZero(req, timeoutUs);
    const int64_t t1 = raNowUs();
    if (step) ok = raStepWait(v0, timeoutUs) && ok;
    const int64_t tf = raNowUs();
    ok = raPollZero(busy, timeoutUs) && ok;
    const int64_t t2 = raNowUs();
    gRaSaveBuf.store(nullptr);
    if (!gRaSaveDone.load()) ok = false;
    const size_t len = gRaSaveLen.load();
    if (lenOut) *lenOut = len;
    if (t) { t->requestUs = t1 - t0; t->writerUs = t2 - t0; t->frameUs = step ? tf - t0 : 0; t->bytes = len; }
    return ok;
}

bool DrasticRunner::ramStateLoadFrom(uint8_t* buf, size_t len, int64_t* loadUs, int timeoutUs) {
    if (!gRaStateHookOn.load() || !buf || !raIsOurBuf(buf) || len <= kRaStateHeader) {
        ALOGW("DrasticRunner::ramStateLoadFrom: bad args (hooks %d buf %p ours %d len %zu)",
              gRaStateHookOn.load() ? 1 : 0, buf, raIsOurBuf(buf) ? 1 : 0, len);
        return false;
    }
    if (!mInitialized || !mLoadState || !mArm64Base) return false;
    // The slot file only has to exist with the image's size.
    if (!fakejni::ramStateSetSize(kRamStateSlot, len)) {
        ALOGW("DrasticRunner::ramStateLoadFrom: ramStateSetSize(%d, %zu) failed", kRamStateSlot, len);
        return false;
    }
    volatile uint8_t* req  = mArm64Base + kRaMasterOff + kRaLoadReqOff;
    volatile uint8_t* busy = mArm64Base + kRaWriterBusyOff;
    if (!raPollZero(busy, timeoutUs)) return false;
    const bool step = gStepMode.load();
    if (step && !waitEmuParked(timeoutUs)) return false;
    gRaLoadBuf.store(buf);
    int64_t t0 = raNowUs();
    mLoadState(mFakeEnv, mFakeCls, kRamStateSlot);
    uint32_t v0 = 0;
    if (step) { v0 = raStepTick(); t0 = gLastStepTickUs.load(); }
    bool ok = raPollZero(req, timeoutUs);
    const int64_t t1 = raNowUs();
    if (step) ok = raStepWait(v0, timeoutUs) && ok;
    gRaLoadBuf.store(nullptr);
    raFlushDeferredUnmaps();
    if (loadUs) *loadUs = t1 - t0;
    return ok;
}

void DrasticRunner::ramStateHookStats(std::string& out) const {
    char b[256];
    snprintf(b, sizeof(b), "malloc hits %u, free skips %u, fread skips %u, writer bypass %u; map skipped %u real %u deferred %u",
             gRaStatMallocHit.load(), gRaStatFreeSkip.load(), gRaStatFreadSkip.load(), gRaStatWriterBypass.load(),
             gRaMapSkipped.load(), gRaMapReal.load(), gRaMapDeferred.load());
    out = b;
}

// ---- Preemptive frames (RetroArch preempt_run, adapted to the async core) ----
//
// Ring of N direct state buffers. Each pacer tick (one per vblank):
//   if the DS input words changed since the last tick and the ring is full:
//     load ring[start] (the state N frames ago) and re-run that frame with
//     the NEW input, then for the remaining N-1 ring entries re-save and
//     re-run, all hidden (the flip hook clears the ready mask and the
//     presenter waits);
//   save the current state into ring[start] (consumed at the start of the
//   shown frame), advance the ring, tick the shown frame as usual.
// Save/load requests are consumed by drastic's per-frame hook at the start
// of the ticked frame, so "arm + tick" is RetroArch's "serialize; retro_run"
// / "unserialize; retro_run". Bursts run on the pacer thread and need the
// emulator parked in the limiter wait; on a frame where it is still busy
// the burst is skipped and retried next tick (the input stays dirty).
namespace {
// Ring of N+2 end-of-frame states: entry 0 is a spare so the buffer a burst
// just loaded from is never the next save target (drastic keeps referencing
// parts of a loaded image after its load returns; overwriting it with the
// next save crashed in the 3D geometry restore). Entry j+1 (oldest first) is the state at
// the end of frame t-N+j = the start of frame t-N+j+1, for j = 0..N; the
// newest (j = N) is the state the next shown frame starts from. Saves ride
// drastic's own end-of-frame request (armed before the tick, no parking
// needed); the burst's load is a parked op (no frame runs before it).

uint32_t gRaPrevIn[3] = {0, 0, 0};
bool gRaPrevValid = false;
std::atomic<uint32_t> gRaStatBursts{0}, gRaStatHidden{0}, gRaStatSkipped{0}, gRaStatShown{0};
// Burst rate limiting. A burst costs 35-47 ms (more than two frame periods),
// so back-to-back input changes (a stylus drag changes the coordinates every
// frame, button mashing) would stall the game: allow a burst only after
// gRaMinGap shown frames since the previous one, and double the gap (up to
// 30 frames) while changes keep arriving closer than twice the gap. Isolated
// presses, where the latency cut matters most, always get the full replay;
// changes inside the gap apply with normal latency.
uint32_t gRaLastBurstShown = 0;
int gRaMinGap = 1;
std::atomic<uint32_t> gRaStatRateLimited{0};
std::atomic<uint32_t> gRaStatNoFit{0};        // input changes applied normally: the burst would not fit before the vblank
std::atomic<int64_t>  gRaBurstEstUs{0};       // EMA of measured burst cost (0 = no burst yet)
std::atomic<int64_t>  gRaLoadEstUs{0};        // EMA of the burst load (0 = none yet)
std::atomic<int64_t>  gRaHiddenEstUs{0};      // EMA of one hidden replay frame
std::atomic<uint32_t> gRaStatBurstN[kRaMaxRing + 1];   // bursts by replay depth
bool                  gRaWarm = false;         // one warm-up load done since enable (first load is ~40 ms cold)
std::atomic<int64_t>  gRaStatLastBurstUs{0}, gRaStatMaxBurstUs{0}, gRaStatSumBurstUs{0};
} // namespace

// Allocate and fault in the run-ahead buffers (ring of frames+1, two
// retired) ahead of time: doing it at enable time, on the render thread
// while the game runs, stalls the emulator for a few frames and trips the
// pacer's bypass on scenes near its threshold.
bool DrasticRunner::runAheadPrepare(int frames) {
    if (frames < 1) frames = 1;
    if (frames > kRaMaxRing - 1) frames = kRaMaxRing - 1;
    for (int i = 0; i < 2; i++) if (!gRaRetired[i]) gRaRetired[i] = ramStateAllocBuffer();
    for (int i = 0; i < frames + 1; i++) if (!gRaRing[i]) gRaRing[i] = ramStateAllocBuffer();
    if (!gRaScratchSlots) {
        void* p = mmap(nullptr, 2 * 0x180000, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS | MAP_POPULATE, -1, 0);
        if (p != MAP_FAILED) gRaScratchSlots = static_cast<uint8_t*>(p);
    }
    return gRaRetired[0] && gRaRetired[1] && gRaRing[frames];
}

bool DrasticRunner::setRunAhead(int mode, int frames) {
    // sys.gammaos.drastic_nano.ra_skip_audio_restart (default 1): the load
    // path stops the OpenSL player (+0x1e320, synchronous audioserver calls)
    // and restarts it afterwards (+0x80828 -> +0x1e490). A run-ahead load
    // happens between two frames of continuous audio, so both are skipped
    // ("mov w0, #1" at +0x7a48c); hidden frames are muted via the audio
    // ctx flag instead (ra_audio_skip).
    static bool sAudioPatched = false; static uint32_t sAudioWas = 0;
    const bool wantAudioSkip = mode == 2 && frames >= 1 &&
                               property_get_bool("sys.gammaos.drastic_nano.ra_skip_audio_restart", true);
    if (wantAudioSkip && !sAudioPatched && mArm64Base) {
        sAudioWas = raPatchInsn(mArm64Base, kRaLoadJitFlushSite, kRaMovW0One); sAudioPatched = true;
        ALOGI("DrasticRunner: run-ahead: audio stop/restart on load skipped");
    } else if (!wantAudioSkip && sAudioPatched && mArm64Base) {
        raPatchInsn(mArm64Base, kRaLoadJitFlushSite, sAudioWas); sAudioPatched = false;
    }
    if (mode == 2 && frames >= 1) raInstallCrashLogger();
    {
        static int sDirtyProp = -1;
        if (sDirtyProp < 0) sDirtyProp = property_get_int32("sys.gammaos.drastic_nano.ra_dirty", 1);
        gRaDirtyWant.store((sDirtyProp && mArm64Base && mode == 2 && frames >= 1) ? 1 : 0);
    }
    // SMC generation cave (see gRaCodeGen): every "bl 0x37cd0" except the
    // load's goes through a cave that bumps the counter, then tail-jumps.
    static bool sSmcCaveOn = false;
    if (mode == 2 && frames >= 1 && !sSmcCaveOn && mArm64Base &&
        property_get_bool("sys.gammaos.drastic_nano.ra_smc_cave", true)) {
        static const uintptr_t sites[] = {
            0x7c870, 0x81de8, 0x81ed4, 0x81fc0, 0x820ac, 0x82198, 0x82284, 0x82c08, 0x82d8c, 0x82f98,
            0x83118, 0x8329c, 0x83420, 0x83524, 0x836c0, 0x8370c, 0x83bac, 0x83ca0, 0x83da4, 0x83e98,
            0x83f9c, 0x840a0, 0x841b4, 0x842b8, 0x843cc, 0x844e0, 0x84604, 0x84718, 0x8483c, 0x84960,
            0x84a94, 0x84bb8, 0x84ebc};
        const uintptr_t cave = 0x132f60, target = 0x37cd0;
        auto bImm = [](uintptr_t from, uintptr_t to, uint32_t op) {
            const int64_t off = ((int64_t)to - (int64_t)from) / 4;
            return op | ((uint32_t)off & 0x03ffffffu);
        };
        const uint64_t flagAddr = (uint64_t)(uintptr_t)&gRaCodeGen;
        // x16/x17 are saved around the bump: compiled callers may not
        // expect them clobbered by a direct call to a known routine.
        const uint32_t words[8] = {
            0xa9bf47f0u,                          // stp x16, x17, [sp, #-16]!
            0x580000f0u,                          // ldr x16, [pc+28]   (literal at cave+32)
            0xb9400211u,                          // ldr w17, [x16]
            0x11000631u,                          // add w17, w17, #1
            0xb9000211u,                          // str w17, [x16]
            0xa8c147f0u,                          // ldp x16, x17, [sp], #16
            bImm(cave + 24, target, 0x14000000u), // b 0x37cd0
            0xd503201fu};                         // nop (pad to the 8-byte literal)
        for (int i = 0; i < 8; i++) raPatchInsn(mArm64Base, cave + 4 * i, words[i]);
        raPatchInsn(mArm64Base, cave + 32, (uint32_t)(flagAddr & 0xffffffffu));
        raPatchInsn(mArm64Base, cave + 36, (uint32_t)(flagAddr >> 32));
        int patched = 0;
        for (uintptr_t site : sites) {
            const uint32_t expect = bImm(site, target, 0x94000000u);
            if (*reinterpret_cast<uint32_t*>(mArm64Base + site) == expect) {
                raPatchInsn(mArm64Base, site, bImm(site, cave, 0x94000000u));
                patched++;
            }
        }
        sSmcCaveOn = true;
        ALOGI("DrasticRunner: run-ahead: SMC generation cave installed on %d/%d sites", patched, (int)(sizeof(sites) / sizeof(sites[0])));
    }
    gRaJitSkipOn = property_get_bool("sys.gammaos.drastic_nano.ra_jit_skip", true);
    gRaHashCheck = property_get_bool("sys.gammaos.drastic_nano.ra_hash_check", false);
    static bool sVideoLogged = false;
    if (mode == 2 && frames >= 1 && !sVideoLogged && mArm64Base) { sVideoLogged = true; raFindVideo(mArm64Base); }
    gRa3dJoinOn = property_get_bool("sys.gammaos.drastic_nano.ra_3d_join", true);
    // The save path renders two thumbnails (+0x7fce4, scaled from the hi-res
    // screens) before serializing. Skip them for the run-ahead slot: a cave in
    // the RX padding compares the slot (w19) and returns, else tail-jumps to
    // the original. Both "bl 0x7fce4" sites in +0x17308 are redirected.
    static bool sThumbPatched = false; static uint32_t sThumbWas[2] = {0, 0};
    const bool wantThumbSkip = mode == 2 && frames >= 1 &&
                               property_get_bool("sys.gammaos.drastic_nano.ra_thumb_skip", true);
    if (wantThumbSkip && !sThumbPatched && mArm64Base) {
        const uintptr_t cave = 0x132f40, target = 0x7fce4, sites[2] = {0x17330, 0x17340};
        auto bImm = [](uintptr_t from, uintptr_t to, uint32_t op) {
            const int64_t off = ((int64_t)to - (int64_t)from) / 4;
            return op | ((uint32_t)off & 0x03ffffffu);
        };
        const uint32_t words[4] = {
            0x71002a7fu,                          // cmp w19, #10
            0x54000040u,                          // b.eq +8
            bImm(cave + 8, target, 0x14000000u),  // b 0x7fce4
            0xd65f03c0u};                         // ret
        for (int i = 0; i < 4; i++) raPatchInsn(mArm64Base, cave + 4 * i, words[i]);
        for (int i = 0; i < 2; i++) sThumbWas[i] = raPatchInsn(mArm64Base, sites[i], bImm(sites[i], cave, 0x94000000u));
        sThumbPatched = true;
        ALOGI("DrasticRunner: run-ahead: thumbnail renders skipped for slot %d (sites were 0x%08x 0x%08x)",
              kRamStateSlot, sThumbWas[0], sThumbWas[1]);
    } else if (!wantThumbSkip && sThumbPatched && mArm64Base) {
        raPatchInsn(mArm64Base, 0x17330, sThumbWas[0]);
        raPatchInsn(mArm64Base, 0x17340, sThumbWas[1]);
        sThumbPatched = false;
    }
    if (mode != 2 || frames < 1) {
        if (gRaMode.exchange(0) != 0) ALOGI("DrasticRunner: run-ahead off");
        gRaRingCount.store(0); gRaPrevValid = false;
        return true;
    }
    if (frames > kRaMaxRing - 1) frames = kRaMaxRing - 1;
    if (!mPaceInstalled || !mArm64Base) return false;
    if (!gRaStateHookOn.load() && !ramStateInstallHooks()) return false;
    for (int i = 0; i < 2; i++) {
        if (!gRaRetired[i]) gRaRetired[i] = ramStateAllocBuffer();
        if (!gRaRetired[i]) { ALOGW("DrasticRunner: run-ahead: retired buffer %d alloc failed", i); return false; }
    }
    for (int i = 0; i < frames + 1; i++) {
        if (!gRaRing[i]) gRaRing[i] = ramStateAllocBuffer();
        if (!gRaRing[i]) { ALOGW("DrasticRunner: run-ahead: buffer %d alloc failed", i); return false; }
    }
    gRaReadyMask = mArm64Base + 0x3f2db80;
    if (gRaMode.load() != 2 || gRaFrames != frames) {
        gRaRingNext.store(0); gRaRingCount.store(0); gRaPrevValid = false;
        gRaFrames.store(frames);
        gRaMode.store(2);
        ALOGI("DrasticRunner: run-ahead on: preemptive frames, N=%d", frames);
    }
    return true;
}

int DrasticRunner::runAheadMode() const { return gRaMode.load(); }

bool DrasticRunner::takeFreshVisible() { return gRaFreshVisible.exchange(false); }

void DrasticRunner::runAheadReset() {
    gRaRingCount.store(0); gRaPrevValid = false; gRaWarm = false;
    raDirtyResetLogs();
}

void DrasticRunner::runAheadStats(std::string& out) const {
    char b[400];
    const uint32_t n = gRaStatBursts.load();
    snprintf(b, sizeof(b), "bursts %u (n1 %u n2 %u n3 %u, hidden frames %u, skipped busy %u, rate limited %u, no fit %u, est %lld us = load %lld + hidden %lld, gap %d, jit skipped %u full %u, codegen %u, save %lld us skipped %u), shown %u, burst last %lld us max %lld us avg %lld us, ring %d/%d; map skipped %u real %u; malloc hits %u fread skips %u writer bypass %u",
             n, gRaStatBurstN[1].load(), gRaStatBurstN[2].load(), gRaStatBurstN[3].load(), gRaStatHidden.load(), gRaStatSkipped.load(), gRaStatRateLimited.load(), gRaStatNoFit.load(), (long long)gRaBurstEstUs.load(), (long long)gRaLoadEstUs.load(), (long long)gRaHiddenEstUs.load(), gRaMinGap,
             gRaStatJitSkipped.load(), gRaStatJitFull.load(), gRaCodeGen, (long long)gRaSaveCostUs.load(), gRaStatSaveSkipped.load(), gRaStatShown.load(),
             (long long)gRaStatLastBurstUs.load(), (long long)gRaStatMaxBurstUs.load(),
             n ? (long long)(gRaStatSumBurstUs.load() / n) : 0LL, gRaRingCount.load(), gRaFrames.load() + 1,
             gRaMapSkipped.load(), gRaMapReal.load(), gRaStatMallocHit.load(), gRaStatFreadSkip.load(), gRaStatWriterBypass.load());
    out = b;
    if (gRaDirtyOn.load()) {
        const uint32_t sv = gRaDStatSaves.load();
        snprintf(b, sizeof(b), "; dirty: faults %u (%llu us), pages/frame last %u avg %llu max %u, replays %u pages %u, copies skipped %u, remaps %u, verify %u/%u bad",
                 gRaDStatFaults.load(), (unsigned long long)(gRaDStatFaultNs.load() / 1000), gRaDStatFramePages.load(), sv ? (unsigned long long)(gRaDStatSumPages.load() / sv) : 0ULL,
                 gRaDStatMaxPages.load(), gRaDStatReplays.load(), gRaDStatReplayPages.load(), gRaDStatSkippedCopies.load(), gRaDStatRemaps.load(),
                 gRaDStatVerifyBad.load(), gRaDStatVerifyRuns.load());
        out += b;
        snprintf(b, sizeof(b), "; faults by view: flat %u window %u vram %u, mprotect %llu us, hot pages %u (copies %u)",
                 gRaDStatFaultFlat.load(), gRaDStatFaultWin.load(), gRaDStatFaultVram.load(), (unsigned long long)(gRaDStatMprotectNs.load() / 1000),
                 gRaDStatHotPages.load(), gRaDStatHotCopies.load());
        out += b;
    }
}

// Arm a save/load request for the next ticked frame (emulator parked).
void DrasticRunner::raArmSave(uint8_t* buf) {
    typedef int (*saveState4_t)(void*, void*, int, int);
    gRaSaveDone.store(false);
    gRaSaveBuf.store(buf);
    reinterpret_cast<saveState4_t>(mSaveState)(mFakeEnv, mFakeCls, kRamStateSlot, 0);
}

bool DrasticRunner::raArmLoad(uint8_t* buf, size_t len) {
    if (!fakejni::ramStateSetSize(kRamStateSlot, len)) return false;
    gRaLoadBuf.store(buf);
    mLoadState(mFakeEnv, mFakeCls, kRamStateSlot);
    return true;
}

bool DrasticRunner::runAheadTryBurst(bool atTick) {
    volatile uint8_t* st = mArm64Base + kRaMasterOff;
    const uint32_t in[3] = {
        *reinterpret_cast<volatile uint32_t*>(st + 0x48c),
        *reinterpret_cast<volatile uint32_t*>(st + 0x494),
        (*reinterpret_cast<volatile uint32_t*>(st + 0x498) & 0xffffu) | ((uint32_t)st[0x4bf] << 16)};
    const bool inputDirty = gRaPrevValid && (in[0] != gRaPrevIn[0] || in[1] != gRaPrevIn[1] || in[2] != gRaPrevIn[2]);
    // Null-burst check (ra_null_burst=K frames): every K shown frames run a
    // burst with UNCHANGED input and compare the state the replay produced
    // for the current frame against the state the original timeline saved
    // for it. Proves the live rollback (they must match except thumbnails
    // and the SPU tail).
    static int sNullK = -1;
    if (sNullK < 0) sNullK = property_get_int32("sys.gammaos.drastic_nano.ra_null_burst", 0);
    const uint32_t shownNow = gRaStatShown.load();
    const bool nullDue = sNullK > 0 && !inputDirty && gRaPrevValid && (shownNow % (uint32_t)sNullK) == 0 &&
                         shownNow != gRaLastBurstShown && gEmuParked.load();
    const bool dirty = inputDirty || nullDue;
    const int N = gRaFrames.load(), R = N + 1;
    const bool parked = gEmuParked.load();
    const bool full = gRaRingCount.load() >= R;
    bool burstDone = false;
    const uint32_t shown = gRaStatShown.load();
    const uint32_t sinceBurst = shown - gRaLastBurstShown;
    if (!gRaWarm && full && parked && *(st + kRaSaveReqOff) == 0 && *(st + kRaLoadReqOff) == 0) {
        // Warm-up: reload the newest ring entry (the state the emulator is
        // already in) once, so the first real burst does not pay the cold
        // load (page-table and remap dedup misses, ~40 ms measured).
        gRaWarm = true;
        const int newest = (gRaRingNext.load() + R - 1) % R;
        const int64_t w0 = raNowUs();
        gRaParkGen = gRaRingGen[newest];
        gRaParkRingIdx = newest;
        gRaWarmLoad = true;
        const bool ok = raParkedOp(1, gRaRing[newest], &gRaRingLen[newest], 200000);
        gRaWarmLoad = false;
        ALOGI("run-ahead warm-up load: %lld us ok=%d", (long long)(raNowUs() - w0), ok ? 1 : 0);
        if (!ok) gRaRingCount.store(0);
        gRaPrevIn[0] = in[0]; gRaPrevIn[1] = in[1]; gRaPrevIn[2] = in[2]; gRaPrevValid = true;
        return false;
    }
    if (dirty && full && parked && gRaStatBursts.load() > 0 && sinceBurst < (uint32_t)gRaMinGap) {
        // Too soon after the previous burst: apply the change normally.
        gRaStatRateLimited.fetch_add(1);
        static uint32_t sRlLog = 0;
        if (property_get_bool("sys.gammaos.drastic_nano.ra_debug", false) && sRlLog < 12) { sRlLog++; ALOGI("run-ahead rate limited: in %08x/%08x/%08x -> %08x/%08x/%08x since %u gap %d", gRaPrevIn[0], gRaPrevIn[1], gRaPrevIn[2], in[0], in[1], in[2], sinceBurst, gRaMinGap); }
        gRaPrevIn[0] = in[0]; gRaPrevIn[1] = in[1]; gRaPrevIn[2] = in[2];
        return false;
    }
    if (dirty && full && parked && *(st + kRaSaveReqOff) == 0 && *(st + kRaLoadReqOff) == 0) {
        // Minimum gap between bursts (ra_min_gap, default 1 = every frame,
        // like RetroArch): the fit policy alone decides. The old escalating
        // gap (4 to 30 frames) made mashing and drags preempt only some
        // presses, a visible timing jitter.
        static int sMinGap = -1;
        if (sMinGap < 0) sMinGap = std::max(1, property_get_int32("sys.gammaos.drastic_nano.ra_min_gap", 1));
        // Fit policy: the shown frame after the burst must still be ready by
        // its vblank (tick + lead). If the burst cannot end at least one
        // emulated frame before that, the input is applied normally instead:
        // no latency gain for this change, but no repeated frame either.
        // ra_fit: sys override, else the persisted Run-Ahead Mode row
        // (runahead_strict 1 = Always: replay on every change regardless of
        // fit, like RetroArch; 0 = Adaptive, default). Re-read every 2 s so
        // the menu applies live.
        static int sFit = -1; static int64_t sFitReadUs = 0;
        {
            const int64_t nowF = raNowUs();
            if (sFit < 0 || nowF - sFitReadUs > 2000000) {
                sFitReadUs = nowF;
                const int sys = property_get_int32("sys.gammaos.drastic_nano.ra_fit", -1);
                sFit = sys >= 0 ? sys : (property_get_bool("persist.gammaos.drastic_nano.runahead_strict", false) ? 0 : 2);
            }
        }
        const int64_t est = gRaBurstEstUs.load();
        int useN = N;
        if (sFit && est > 0 && gPaceOn.load() && !nullDue) {
            const int64_t now = raNowUs();
            const int64_t tick = atTick ? now : gPacerNextTickUs.load();
            // The presenter waits at most pace_wait_us after a vblank for the
            // producer's frame, then renders and flips on the next vblank. A
            // frame produced later than that is a producer-wait timeout: the
            // old frame repeats and the next vblank shows two frames of
            // progress (the "jump"). So the shown frame after a burst must be
            // ready by (next vblank - (period - pace_wait)) minus a margin,
            // not merely before the vblank.
            static int64_t sPresReserveUs = -1; static int64_t sPresReadUs = 0;
            if (sPresReserveUs < 0 || now - sPresReadUs > 2000000) {
                sPresReadUs = now;
                const int64_t waitUs = property_get_int32("sys.gammaos.drastic_nano.pace_wait_us", 10000);
                const int64_t period = gVblankPeriodUs.load() > 0 ? gVblankPeriodUs.load() : 16667;
                const int64_t margin = property_get_int32("sys.gammaos.drastic_nano.ra_fit_margin_us", 1000);
                sPresReserveUs = std::max<int64_t>(margin, period - waitUs + margin);
            }
            const int64_t deadline = tick + gLeadUs.load() - gEmuDurUs.load() - sPresReserveUs;
            // Adaptive depth (ra_fit=2, default): when N replays do not fit,
            // fall back to the largest n that does (load + n hidden frames).
            // A hidden frame is the CPU emulation of a visible one (about 70%
            // measured, the rest is the 3D wait): predict it from the live
            // frame time so a scene that got heavier since the last burst
            // does not push the shown frame past its vblank.
            // Hidden frame = 75% of the live visible frame (its CPU emulation,
            // no 3D wait) plus its ring save. The learned per-burst average is
            // not used as a floor: it is seeded by the first, cold burst and
            // would hold bursts back for minutes.
            const int64_t le = gRaLoadEstUs.load();
            const int64_t he = gEmuDurUs.load() * 3 / 4 + gRaSaveCostUs.load();
            if (sFit >= 2 && le > 0 && he > 0) {
                useN = 0;
                for (int n = N; n >= 1; n--) if (now + le + n * he + (le + n * he) / 8 <= deadline) { useN = n; break; }
            } else if (now + est > deadline) useN = 0;
            if (useN == 0) {
                gRaStatNoFit.fetch_add(1);
                // Decay so the estimate re-probes after a run of no-fits.
                gRaBurstEstUs.store(est - est / 32);
                gRaLoadEstUs.store(std::max<int64_t>(1500, gRaLoadEstUs.load() - gRaLoadEstUs.load() / 64));   // floor: a warm load is ~1.8 ms
                static uint32_t sNoFitLog = 0;
                if (property_get_bool("sys.gammaos.drastic_nano.ra_debug", false) && sNoFitLog < 20) {
                    sNoFitLog++;
                    ALOGI("run-ahead no fit: in %08x/%08x/%08x -> %08x/%08x/%08x est %lld > slack %lld (tick %+lld lead %lld emu %lld)%s",
                          gRaPrevIn[0], gRaPrevIn[1], gRaPrevIn[2], in[0], in[1], in[2],
                          (long long)est, (long long)(deadline - now), (long long)(tick - now),
                          (long long)gLeadUs.load(), (long long)gEmuDurUs.load(), atTick ? " at tick" : "");
                }
                gRaPrevIn[0] = in[0]; gRaPrevIn[1] = in[1]; gRaPrevIn[2] = in[2];
                return false;
            }
        }
        gRaMinGap = sMinGap;
        gRaLastBurstShown = shown;
        const int64_t b0 = raNowUs();
        // Audio of replayed frames is dropped: drastic's per-frame audio
        // submit (+0x1dd6c, from the frame loop at +0x2cc20) returns early
        // and zeroes its pending-sample count while the byte at audio
        // ctx+0x40027 (the flag its own load path sets around a state
        // load) is nonzero. The shown frame after the burst submits as usual.
        uint8_t* heapMaster = *reinterpret_cast<uint8_t**>(mArm64Base + kRaMasterOff);
        static int sAudioSkipMode = -1; static int64_t sAudioSkipReadUs = 0;
        if (sAudioSkipMode < 0 || b0 - sAudioSkipReadUs > 2000000) {
            sAudioSkipReadUs = b0;
            sAudioSkipMode = property_get_int32("sys.gammaos.drastic_nano.ra_audio_skip", 1);
        }
        volatile uint8_t* audioSkip = (heapMaster && sAudioSkipMode == 1) ? heapMaster + 0x158c000 + 0x40027 : nullptr;
        if (audioSkip) *audioSkip = 1;
        {
            static bool sCopyLogArmed = false;
            if (!sCopyLogArmed) {
                sCopyLogArmed = true;
                const int n = property_get_int32("sys.gammaos.drastic_nano.ra_copy_log", 0);
                if (n > 0) gRaCopyLog.store(n);
            }
        }
        gRaBurst.store(true, std::memory_order_release);
        // RetroArch preempt_run: unserialize(oldest), then run the last N
        // frames again; each re-run frame's park auto-saves its end state
        // into the ring slot the oldest entry occupied, rotating the ring.
        // full ring: next == oldest = the state useN..N frames back; a shallower
        // replay loads a newer entry
        const int oldest = (gRaRingNext.load() + (N - useN)) % R;
        int64_t tl[kRaMaxRing + 1] = {0, 0, 0, 0, 0, 0, 0};
        gRaParkGen = gRaRingGen[oldest];
        gRaParkRingIdx = oldest;
        gRaStatBurstN[useN].fetch_add(1);
        bool ok = raParkedOp(1, gRaRing[oldest], &gRaRingLen[oldest], 200000);
        tl[0] = raNowUs() - b0;
        // Hidden frames are never shown: skip their 2D compose and 3D kick
        // (ra_hidden_skip bit 1 / bit 2, default both).
        static int sHiddenSkip = -1;
        // Default: skip the 2D compose and the 3D worker kick (bits 1|2). The
        // game's geometry lists are parsed on the main thread as it writes the
        // GX FIFO, so a hidden frame builds its list without the kick and the
        // shown frame after a burst rasterizes its own. Keeping the kick on
        // hidden frames (with a join before the hidden save) gained nothing
        // in the null-burst state comparison and crashed once under mashing.
        if (sHiddenSkip < 0) sHiddenSkip = property_get_int32("sys.gammaos.drastic_nano.ra_hidden_skip", 3);
        static int sHiddenSaveOn = -1;
        if (sHiddenSaveOn < 0) sHiddenSaveOn = property_get_int32("sys.gammaos.drastic_nano.ra_hidden_save", 1);
        if (ok && sHiddenSkip) raHiddenRenderSkip(mArm64Base, sHiddenSkip);
        uint32_t hf[kRaMaxRing + 1] = {0, 0, 0, 0, 0, 0, 0};
        int64_t hflip[kRaMaxRing + 1] = {0, 0, 0, 0, 0, 0, 0}, hpark[kRaMaxRing + 1] = {0, 0, 0, 0, 0, 0, 0}, hwait[kRaMaxRing + 1] = {0, 0, 0, 0, 0, 0, 0};
        for (int j = 1; ok && j <= useN; j++) {
            const uint32_t f0 = gRaDStatFaults.load();
            const int64_t w0 = gT3dPipe.sumUs;
            const int64_t tk = raNowUs();
            const uint32_t v0 = raStepTick();
            ok = raStepWait(v0, 200000);
            gRaStatHidden.fetch_add(1);
            tl[j] = raNowUs() - b0;
            hf[j] = gRaDStatFaults.load() - f0;
            hflip[j] = gProducerDoneUs.load() - tk;
            hpark[j] = gLastParkUs.load() - tk;
            hwait[j] = gT3dPipe.sumUs - w0;
        }
        raHiddenRenderRestore(mArm64Base);
        if (nullDue && ok && sHiddenSaveOn && useN >= 1) {
            // slot (oldest + useN - 1) holds the replayed state for the current
            // frame, slot (oldest + useN) the original timeline's save of it
            const int a = (oldest + useN - 1) % R, b = (oldest + useN) % R;
            const size_t la = gRaRingLen[a], lb = gRaRingLen[b];
            size_t diffs = 0, thumb = 0, spu = 0, other = 0, first = (size_t)-1, n = std::min(la, lb);
            char where[160] = {0}; int shownOff = 0;
            for (size_t k = 0; k < n; k++) {
                if (gRaRing[a][k] != gRaRing[b][k]) {
                    diffs++;
                    if (k >= 0x40 && k < 0x30040) thumb++;
                    else if (k + 738 >= n) spu++;
                    else { other++; if (first == (size_t)-1) first = k; if (shownOff < 8) { char t[20]; snprintf(t, sizeof(t), "0x%zx ", k); strncat(where, t, sizeof(where) - strlen(where) - 1); shownOff++; } }
                }
            }
            ALOGW("run-ahead NULL BURST depth %d: replayed vs original state %s (%zu vs %zu bytes; diffs %zu = thumb %zu + spu %zu + other %zu, first other 0x%zx %s)",
                  useN, (la == lb && other == 0) ? "IDENTICAL" : "DIFFERENT", la, lb, diffs, thumb, spu, other, first == (size_t)-1 ? (size_t)0 : first, where);
            // ra_null_dump=1: keep the first differing pair for offline analysis
            static bool sDumped = false;
            if (other && !sDumped && property_get_bool("sys.gammaos.drastic_nano.ra_null_dump", false)) {
                sDumped = true;
                std::vector<uint8_t> va(gRaRing[a], gRaRing[a] + la), vb(gRaRing[b], gRaRing[b] + lb);
                ALOGW("run-ahead NULL BURST dump: replayed %d original %d", raDumpFile("/data/local/tmp/nb_replayed.bin", va) ? 1 : 0, raDumpFile("/data/local/tmp/nb_original.bin", vb) ? 1 : 0);
            }
        }
        static uint32_t sBurstLog = 0;
        static int sBurstDebug = -1;
        if (sBurstDebug < 0) sBurstDebug = property_get_bool("sys.gammaos.drastic_nano.ra_debug", false) ? 1 : 0;
        if (sBurstDebug && sBurstLog < 40) {
            sBurstLog++;
            ALOGI("run-ahead burst: in %08x/%08x/%08x -> %08x/%08x/%08x: load %lld, run+save %lld %lld %lld %lld us, ok=%d",
                  gRaPrevIn[0], gRaPrevIn[1], gRaPrevIn[2], in[0], in[1], in[2],
                  (long long)tl[0], (long long)tl[1], (long long)tl[2], (long long)tl[3], (long long)tl[4], ok ? 1 : 0);
            ALOGI("run-ahead hidden frames: tick->flip %lld %lld, tick->park %lld %lld, 3D waits %lld %lld us, emu EMA %lld, t3d mode %d",
                  (long long)hflip[1], (long long)hflip[2], (long long)hpark[1], (long long)hpark[2],
                  (long long)hwait[1], (long long)hwait[2], (long long)gEmuDurUs.load(), gT3dMode);
            ALOGI("run-ahead map hooks: %llu us total in slow mmap/munmap hooks so far (%u skipped %u real, fast hits %u)",
                  (unsigned long long)(gRaMapHookNs.load() / 1000), gRaMapSkipped.load(), gRaMapReal.load(), gRaMapFastHits.load());
            ALOGI("run-ahead burst dirty: replay %llu us, hidden faults %u %u %u, fault time total %llu us / %u faults",
                  (unsigned long long)gRaDStatRestoreUs.load(), hf[1], hf[2], hf[3],
                  (unsigned long long)(gRaDStatFaultNs.load() / 1000), gRaDStatFaults.load());
            ALOGI("run-ahead load timeline (us from burst start): malloc %lld, header %lld, body %lld, remaps %lld..%lld, free %lld",
                  (long long)(gRaLoadTs[0].load() - b0), (long long)(gRaLoadTs[1].load() - b0),
                  (long long)(gRaLoadTs[2].load() - b0), (long long)(gRaLoadTs[3].load() - b0),
                  (long long)(gRaLoadTs[4].load() - b0), (long long)(gRaLoadTs[5].load() - b0));
        }
        if (audioSkip) *audioSkip = 0;
        gRaBurst.store(false, std::memory_order_release);
        gRaBurstUntilSeq.store(gVblSeq.load() + 8);
        const int64_t dt = raNowUs() - b0;
        {
            // Cost estimates for the fit policy: EMA with a 12% guard band on the
            // whole burst; load and per-hidden-frame estimates for adaptive depth.
            const int64_t e = gRaBurstEstUs.load();
            gRaBurstEstUs.store(e > 0 ? (e * 3 + dt + dt / 8) / 4 : dt + dt / 8);
            if (ok && tl[0] > 0) {
                const int64_t l = gRaLoadEstUs.load();
                // outlier clamp: a cold or interrupted load must not poison the average
                const int64_t sample = (l > 0 && tl[0] > 2 * l) ? 2 * l : tl[0];
                gRaLoadEstUs.store(l > 0 ? (l * 3 + sample) / 4 : std::min<int64_t>(tl[0], 4000));
                if (useN >= 1) {
                    const int64_t h = (tl[useN] - tl[0]) / useN;
                    const int64_t hh = gRaHiddenEstUs.load();
                    gRaHiddenEstUs.store(hh > 0 ? (hh * 3 + h) / 4 : h);
                }
            }
        }
        gRaStatBursts.fetch_add(1);
        gRaStatLastBurstUs.store(dt);
        gRaStatSumBurstUs.fetch_add(dt);
        if (dt > gRaStatMaxBurstUs.load()) gRaStatMaxBurstUs.store(dt);
        if (!ok) { ALOGW("DrasticRunner: run-ahead burst failed, ring reset"); gRaRingCount.store(0); }
        else if (!sHiddenSaveOn) gRaRingCount.store(0);   // without hidden saves the ring holds the discarded timeline
        burstDone = ok;
    } else if (dirty && full) {
        gRaStatSkipped.fetch_add(1);
    }
    if (burstDone || !dirty || !full) {
        gRaPrevIn[0] = in[0]; gRaPrevIn[1] = in[1]; gRaPrevIn[2] = in[2]; gRaPrevValid = true;
    }
    return burstDone;
}

void DrasticRunner::runAheadPacerTick() {
    runAheadTryBurst(true);
    gRaStatShown.fetch_add(1);
    { std::lock_guard<std::mutex> lk(gPaceMu); gVblSeq.fetch_add(1, std::memory_order_acq_rel); }
    gPaceCv.notify_all();
}

uint64_t DrasticRunner::hashFrontSlot() const {
    if (!mArm64Base) return 0;
    uint8_t* bss = mArm64Base + 0x3f2d1f8;
    uint8_t* slot0 = *reinterpret_cast<uint8_t**>(bss);
    uint8_t* slot1 = *reinterpret_cast<uint8_t**>(bss + 8);
    if (!slot0 || !slot1) return 0;
    const int32_t cur = *reinterpret_cast<int32_t*>(bss + 0x958);
    const int32_t ptype = *reinterpret_cast<int32_t*>(bss + 0x95c);
    const size_t bpp = (ptype == 0x10) ? 2 : 4;
    const uint8_t* front = ((~cur) & 1) ? slot1 : slot0;
    uint64_t h = 1469598103934665603ULL;
    for (int i = 0; i < 2; i++) {
        const int32_t hr = *reinterpret_cast<int32_t*>(bss + 0x968 + 4 * i);
        size_t bytes = ((size_t)(hr + 1) << 8) * ((size_t)(hr + 1) * 192) * bpp;
        if (bytes > 0xC0000) bytes = 0xC0000;
        const uint8_t* p = front + (size_t)i * 0xC0000;
        for (size_t k = 0; k < bytes; k++) { h ^= p[k]; h *= 1099511628211ULL; }
    }
    return h;
}

// Crash pc logger (drastic-nano is an init oneshot with no tombstone).
void DrasticRunner::raInstallCrashLogger() {
    static bool sInstalled = false;
    if (sInstalled) return;
    sInstalled = true;
    struct sigaction sa = {};
    sa.sa_flags = SA_SIGINFO | SA_NODEFER;
    sa.sa_sigaction = [](int sig, siginfo_t* si, void* uc) {
        if (sig == SIGSEGV && si && raDirtyFault(si->si_addr)) return;
        signal(sig, SIG_DFL);
        ucontext_t* u = static_cast<ucontext_t*>(uc);
        const uintptr_t pc = u->uc_mcontext.pc;
        const uintptr_t lr = u->uc_mcontext.regs[30];
        DrasticRunner* r = DrasticRunner::getInstance();
        const uintptr_t base = r ? (uintptr_t)r->libBase() : 0;
        ALOGE("RAPROBE CRASH sig=%d addr=%p pc=0x%lx (+0x%lx) lr=0x%lx (+0x%lx) x0=0x%lx x1=0x%lx",
              sig, si ? si->si_addr : nullptr, (unsigned long)pc, (unsigned long)(pc - base),
              (unsigned long)lr, (unsigned long)(lr - base),
              (unsigned long)u->uc_mcontext.regs[0], (unsigned long)u->uc_mcontext.regs[1]);
        usleep(200000);
        for (int i = 0; i < kRaMaxRing; i++) if (gRaRing[i]) ALOGE("RAPROBE CRASH ring[%d]=%p len %zu%s", i, gRaRing[i], gRaRingLen[i], (si && (uint8_t*)si->si_addr >= gRaRing[i] && (uint8_t*)si->si_addr <= gRaRing[i] + kRaStateBufSize) ? "  <-- fault" : "");
        ALOGE("RAPROBE CRASH retired %p %p scratch=%p burst=%d parkOp=%d ringNext=%d count=%d loadBuf=%p saveBuf=%p", gRaRetired[0], gRaRetired[1], gRaScratchSlots, gRaBurst.load() ? 1 : 0, gRaParkOp.load(), gRaRingNext.load(), gRaRingCount.load(), gRaLoadBuf.load(), gRaSaveBuf.load());
        raLogGx("CRASH");
        raise(sig);
    };
    sigaction(SIGSEGV, &sa, nullptr);
    sigaction(SIGBUS, &sa, nullptr);
}
void DrasticRunner::runaheadProbe(int iters) {
    pthread_setname_np(pthread_self(), "dn-raprobe");
    ALOGW("RAPROBE start iters=%d t3d_sync=%d threaded3d=%d", iters,
          property_get_int32("persist.gammaos.drastic_nano.t3d_sync", 3),
          property_get_int32("persist.gammaos.drastic_nano.threaded3d", -1));
    if (!setStepMode(true)) { ALOGW("RAPROBE: cannot enter step mode"); return; }
    // Raw (uncompressed) states for the probe, restored at the end.
    // The save path reads the switch from the heap master struct, whose
    // pointer is the first word of the static block at base+0x14c000.
    uint8_t* heapMaster = *reinterpret_cast<uint8_t**>(mArm64Base + kRaMasterOff);
    volatile uint32_t* compress = reinterpret_cast<volatile uint32_t*>(heapMaster + kRaCompressOff);
    const uint32_t compressWas = *compress;
    if (property_get_bool("sys.gammaos.drastic_nano.ra_compress", false)) {
        ALOGW("RAPROBE: leaving state compression as configured (%u)", compressWas);
    } else {
        *compress = 0;
    }
    // Optional: skip the JIT flush on load (see kRaLoadJitFlushSite).
    // 1 = "mov w0, #1": skip the flush and the recompiler re-init;
    // 2 = "mov w0, #0": skip the flush, keep the re-init.
    const int skipJit = property_get_int32("sys.gammaos.drastic_nano.ra_skip_jit_flush", 0);
    uint32_t jitSiteWas = 0;
    if (skipJit == 1 || skipJit == 2) {
        jitSiteWas = raPatchInsn(mArm64Base, kRaLoadJitFlushSite,
                                 skipJit == 1 ? kRaMovW0One : kRaMovW0Zero);
        ALOGW("RAPROBE: JIT flush on load skipped, variant %d (site word was 0x%08x)", skipJit, jitSiteWas);
    }
    // Mode bits: 1 step burst, 2 save/load timing, 4 replay determinism,
    // 8 saves only in the timing loop (profiling).
    const int mode = property_get_int32("sys.gammaos.drastic_nano.runahead_probe_mode", 7);
    const bool skipJitClear = property_get_bool("sys.gammaos.drastic_nano.ra_skip_jit_clear", false);
    uint32_t jitClearWas = 0;
    if (skipJitClear) {
        jitClearWas = raPatchInsn(mArm64Base, kRaLoadJitClearSite, kRaNop);
        ALOGW("RAPROBE: JIT invalidation on load skipped (site word was 0x%08x)", jitClearWas);
    }
    if (property_get_bool("sys.gammaos.drastic_nano.ra_mmap_dedup", false)) {
        ALOGW("RAPROBE: memory-map remap dedup hook %s", raInstallMapHook(mArm64Base) ? "on" : "FAILED");
    }
    // Direct buffers: saves/loads go straight to/from registered buffers.
    const bool direct = property_get_bool("sys.gammaos.drastic_nano.ra_direct", false);
    uint8_t* dbuf[3] = {nullptr, nullptr, nullptr};
    size_t dlen[3] = {0, 0, 0};
    if (direct) {
        const bool ok = ramStateInstallHooks();
        for (int i = 0; i < 3; i++) dbuf[i] = ramStateAllocBuffer();
        ALOGW("RAPROBE: direct state buffers %s (hooks %s, bufs %p %p %p)",
              (ok && dbuf[0] && dbuf[1] && dbuf[2]) ? "on" : "FAILED", ok ? "ok" : "failed",
              dbuf[0], dbuf[1], dbuf[2]);
    }
    auto probeSave = [&](int which, size_t* len, RamStateTiming* t) {
        return direct ? ramStateSaveTo(dbuf[which], len, t) : ramStateSave(t);
    };
    auto probeLoad = [&](int which, int64_t* lu) {
        return direct ? ramStateLoadFrom(dbuf[which], dlen[which], lu) : ramStateLoad(lu);
    };
    auto logMapStats = [](const char* when) {
        ALOGW("RAPROBE map hook %s: mmap skipped %u real %u, munmap deferred %u real %u, flushed %u, shadow %zu pages",
              when, gRaMapSkipped.load(), gRaMapReal.load(), gRaMapDeferred.load(), gRaUnmapReal.load(),
              gRaFlushed.load(), gRaMapUsed);
    };

    // JIT object introspection: the flush (+0x1e320) and re-init (+0x1e490)
    // call virtual methods on objects whose pointers live in BSS at
    // +0x3c7d030.. ; log their vtable targets as .so offsets so the
    // routines can be read in the disassembly.
    raInstallCrashLogger();
    // JIT object introspection: the flush (+0x1e320) and re-init (+0x1e490)
    // call virtual methods on objects whose pointers live in BSS at
    // +0x3c7d030..; resolve their vtables and entries with dladdr so the
    // routines can be read in the right library's disassembly.
    {
        uint8_t* jb = mArm64Base + 0x3c7d000;
        ALOGW("RAPROBE jit: enabled=%u tables=%u tableEntries=%u",
              *reinterpret_cast<uint32_t*>(jb + 0x78),
              *reinterpret_cast<uint32_t*>(jb + 0x7c), *reinterpret_cast<uint32_t*>(jb + 0x80));
        const int offs[] = {0x30, 0x38, 0x48, 0x50, 0x58};
        for (int off : offs) {
            uint8_t* obj = *reinterpret_cast<uint8_t**>(jb + off);
            if (!obj) { ALOGW("RAPROBE jit obj@+0x%x: null", off); continue; }
            uint8_t** vt = *reinterpret_cast<uint8_t***>(obj);
            Dl_info di = {};
            std::string line;
            if (vt && dladdr(vt, &di) && di.dli_fbase) {
                char b[160];
                snprintf(b, sizeof(b), "vtable %s+0x%lx:", di.dli_fname ? strrchr(di.dli_fname, '/') + 1 : "?",
                         (unsigned long)((uint8_t*)vt - (uint8_t*)di.dli_fbase));
                line += b;
                for (int k = 0; k < 6; k++) {
                    Dl_info fi = {};
                    if (dladdr(vt[k], &fi) && fi.dli_fbase) {
                        snprintf(b, sizeof(b), " [%d]=%s+0x%lx", k,
                                 fi.dli_fname ? strrchr(fi.dli_fname, '/') + 1 : "?",
                                 (unsigned long)(vt[k] - (uint8_t*)fi.dli_fbase));
                    } else {
                        snprintf(b, sizeof(b), " [%d]=%p", k, vt[k]);
                    }
                    line += b;
                }
            } else {
                line = "vtable unresolved";
            }
            ALOGW("RAPROBE jit obj@+0x%x: obj=%p %s", off, obj, line.c_str());
        }
    }

    // 1. A plain step burst: per-step wall time with nothing else going on.
    if (mode & 1) {
        std::string line;
        const uint32_t ef0 = emuFrameCount();
        const uint32_t pf0 = producerFrameCount();
        for (int i = 0; i < 30; i++) {
            const int64_t a = raNowUs();
            const bool ok = stepOneFrame();
            char b[24]; snprintf(b, sizeof(b), "%s%lld", ok ? "" : "!", (long long)(raNowUs() - a));
            line += b; line += ' ';
            if (!ok) break;
        }
        ALOGW("RAPROBE burst step us: %s| emu frames +%u flips +%u", line.c_str(),
              emuFrameCount() - ef0, producerFrameCount() - pf0);
    }

    // 2. RAM save / load cost.
    for (int i = 0; (mode & 2) && i < iters; i++) {
        RamStateTiming t;
        const bool ok = probeSave(0, &dlen[0], &t);
        ALOGW("RAPROBE save %d: ok=%d serialize %lld us, writer done %lld us, frame %lld us, %zu bytes",
              i, ok ? 1 : 0, (long long)t.requestUs, (long long)t.writerUs,
              (long long)t.frameUs, t.bytes);
    }
    for (int i = 0; (mode & 2) && !(mode & 8) && i < iters; i++) {
        int64_t lu = 0;
        const int64_t a = raNowUs();
        const bool ok = probeLoad(0, &lu);
        const int64_t frame = raNowUs() - a;
        std::string line;
        for (int k = 0; k < ((mode & 16) ? 0 : 6); k++) {   // mode 16: loads back to back (profiling)
            const int64_t s = raNowUs();
            if (!stepOneFrame()) { line += "! "; break; }
            char b[24]; snprintf(b, sizeof(b), "%lld ", (long long)(raNowUs() - s));
            line += b;
        }
        ALOGW("RAPROBE load %d: ok=%d load %lld us, frame %lld us, next steps us: %s",
              i, ok ? 1 : 0, (long long)lu, (long long)frame, line.c_str());
        if (gRaMapHookOn.load()) logMapStats("after load");
    }

    // 3. Replay determinism: state after D frames from S0 must be bit-exact
    //    whether reached the first time or by reloading S0 and re-stepping.
    for (int round = 0; (mode & 4) && round < iters; round++) {
        for (int depth = 1; depth <= 3; depth++) {
            std::vector<uint8_t> s0, a, b;
            const uint8_t *pa = nullptr, *pb = nullptr;
            size_t na = 0, nb = 0;
            int64_t lu = 0;
            // Pass A: S0 at the start of frame k, then frames k..k+depth-1, state A at the start of k+depth.
            const uint32_t r0 = producerFrameCount(), f0 = emuFrameCount();
            if (direct) {
                if (!ramStateSaveTo(dbuf[0], &dlen[0])) { ALOGW("RAPROBE depth %d: save S0 failed", depth); continue; }
                for (int i = 1; i < depth; i++) stepOneFrame();
                if (!ramStateSaveTo(dbuf[1], &dlen[1])) { ALOGW("RAPROBE depth %d: save A failed", depth); continue; }
                pa = dbuf[1]; na = dlen[1];
            } else {
                if (!ramStateSave() || !ramStateCopyOut(s0)) { ALOGW("RAPROBE depth %d: save S0 failed", depth); continue; }
                for (int i = 1; i < depth; i++) stepOneFrame();
                if (!ramStateSave() || !ramStateCopyOut(a)) { ALOGW("RAPROBE depth %d: save A failed", depth); continue; }
                pa = a.data(); na = a.size();
            }
            const uint64_t ha = hashFrontSlot();
            const uint32_t r1 = producerFrameCount(), f1 = emuFrameCount();
            // Pass B: reload S0, same frames, state B.
            if (direct) {
                if (!ramStateLoadFrom(dbuf[0], dlen[0], &lu)) { ALOGW("RAPROBE depth %d: load S0 failed", depth); continue; }
                if (mode & 32) raHiddenRenderSkip(mArm64Base, property_get_int32("sys.gammaos.drastic_nano.ra_hidden_skip", 3));   // replay like a hidden burst frame
                for (int i = 1; i < depth; i++) stepOneFrame();
                raHiddenRenderRestore(mArm64Base);
                if (!ramStateSaveTo(dbuf[2], &dlen[2])) { ALOGW("RAPROBE depth %d: save B failed", depth); continue; }
                pb = dbuf[2]; nb = dlen[2];
            } else {
                if (!ramStateCopyIn(s0.data(), s0.size())) { ALOGW("RAPROBE: copy in failed"); continue; }
                if (!ramStateLoad(&lu)) { ALOGW("RAPROBE depth %d: load S0 failed", depth); continue; }
                for (int i = 1; i < depth; i++) stepOneFrame();
                if (!ramStateSave() || !ramStateCopyOut(b)) { ALOGW("RAPROBE depth %d: save B failed", depth); continue; }
                pb = b.data(); nb = b.size();
            }
            const uint64_t hb = hashFrontSlot();
            const uint32_t r2 = producerFrameCount(), f2 = emuFrameCount();
            // Classify the differing bytes: thumbnails (0x40..0x30040), the
            // SPU channel records in the last 738 bytes, anything else.
            size_t diffs = 0, first = (size_t)-1, thumb = 0, spu = 0, other = 0;
            std::string regs;
            int shown = 0;
            const size_t n = std::min(na, nb);
            for (size_t k = 0; k < n; k++) {
                if (pa[k] != pb[k]) {
                    if (first == (size_t)-1) first = k;
                    diffs++;
                    if (k >= 0x40 && k < 0x30040) thumb++;
                    else if (k + 738 >= n) spu++;
                    else {
                        other++;
                        if (shown < 16) { char t[40]; snprintf(t, sizeof(t), "0x%zx ", k); regs += t; shown++; }
                    }
                }
            }
            const bool coreSame = na == nb && other == 0;
            ALOGW("RAPROBE round %d depth %d: core state %s (%zu vs %zu bytes; diffs %zu = thumb %zu + spu %zu + other %zu, first 0x%zx) frame hash %s (%016llx vs %016llx) flips/emu A +%u/+%u B +%u/+%u load %lld us",
                  round, depth, coreSame ? "IDENTICAL" : "DIFFERENT", na, nb, diffs, thumb, spu, other,
                  first == (size_t)-1 ? (size_t)0 : first,
                  ha == hb ? "same" : "DIFFERENT", (unsigned long long)ha, (unsigned long long)hb,
                  r1 - r0, f1 - f0, r2 - r1, f2 - f1, (long long)lu);
            if (other) ALOGW("RAPROBE   other diff offsets: %s", regs.c_str());
            if (!coreSame && round == 0 && !direct) {
                char fa[96], fb[96];
                snprintf(fa, sizeof(fa), "/data/local/tmp/raprobe_d%d_a.bin", depth);
                snprintf(fb, sizeof(fb), "/data/local/tmp/raprobe_d%d_b.bin", depth);
                ALOGW("RAPROBE   dumped %s (%d) %s (%d)", fa, raDumpFile(fa, a) ? 1 : 0, fb, raDumpFile(fb, b) ? 1 : 0);
            }
        }
    }
    if (direct) { std::string st; ramStateHookStats(st); ALOGW("RAPROBE direct hook stats: %s", st.c_str()); }
    if (skipJit) raPatchInsn(mArm64Base, kRaLoadJitFlushSite, jitSiteWas);
    if (skipJitClear) raPatchInsn(mArm64Base, kRaLoadJitClearSite, jitClearWas);
    if (gRaMapHookOn.load()) logMapStats("at end");
    *compress = compressWas;
    setStepMode(false);
    ALOGW("RAPROBE done");
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
    runAheadReset();
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

// Producer/emulated frame count from the slot-flip hook. gFlipHookCount is the
// file-scope atomic bumped once per DS core frame in drasticSlotFlipHook(),
// so it tracks the emulation rate (and rises during fast-forward) even on the
// renderDsToOffscreen() path where the other counters freeze.
uint32_t DrasticRunner::producerFrameCount() const {
    return gFlipHookCount.load();
}

// Emulated-frame count from the frame-limiter hook (drasticVWait). Advances once
// per emulated frame regardless of render frame-skip, so it is the true emulation
// rate under fast-forward. 0 until the pacing hooks are installed.
uint32_t DrasticRunner::limiterFrameCount() const {
    return gVWaitCount.load();
}

// Raw frame-limiter clock-read count (see drasticVTime). Advances a fixed number
// of times per emulated frame in every mode, so its per-second delta divided by
// that per-frame count is the true emulation rate, fast-forward included.
uint32_t DrasticRunner::limiterClockCount() const {
    return gVTimeCount.load();
}

// True emulated-frame count (see drasticVTimeReset): once per emulated frame in
// every mode, so its per-second delta is the emulation FPS -- 60 at full speed,
// higher under fast-forward, lower when the emulator cannot keep up.
uint32_t DrasticRunner::emuFrameCount() const {
    // Hidden replay frames are not game progress: keep them out of the rate.
    return gEmuFrames.load() - gRaStatHidden.load();
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
    { const int rt = property_get_int32("sys.gammaos.drastic_nano.ffspeed_rt", -1); if (rt >= 0) cap = rt; }   // A/B override
    if (cap < 0)  cap = 0;
    if (cap > 15) cap = 15;
    bits |= 0x20000000L;                          // _V fast-forward lever
    bits = (bits & ~0xF000L) | ((long)cap << 12); // _FfwdSpeed index
    // Runtime override for A/B (sys.gammaos.drastic_nano.ff_no_threaded3d_rt: -1 follow the persist knob).
    const int rtNoT3d = property_get_int32("sys.gammaos.drastic_nano.ff_no_threaded3d_rt", -1);
    if (rtNoT3d > 0 || (rtNoT3d < 0 && property_get_int32(
            "persist.gammaos.drastic_nano.ff_no_threaded3d", 0))) {
        bits &= ~0x10000000L;                     // clear _Threaded3D
    }
}

void DrasticRunner::setFastForward(bool on) {
    if (!mInitialized || !mApplyConfig) return;
    if (on == mFastForwardOn) return;
    mFastForwardOn = on;
    gFfOnForHook.store(on ? 1 : 0, std::memory_order_relaxed);
    setVblankPacing(mPaceWanted);
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

    // Capture-aware frameskip (see ffCapHook). Installed on the first FF
    // entry and left in place: at 1x the limiter never sets the skip flag
    // so the hook is a passthrough. Knob persist ff_capfix (default 1),
    // runtime override sys.gammaos.drastic_nano.ff_capfix_rt, pair period
    // persist ff_pair_period (default 8: two rendered frames in eight,
    // Golden Sun 2.0x at full speed).
    {
        static int sCapFix = -1;
        if (sCapFix < 0) sCapFix = property_get_int32("persist.gammaos.drastic_nano.ff_capfix", 1);
        const int rt = property_get_int32("sys.gammaos.drastic_nano.ff_capfix_rt", -1);
        const bool wantC = on && (rt >= 0 ? rt > 0 : sCapFix > 0);
        static bool sCapApplied = false;
        const uintptr_t kSite = 0x3c9a0;
        const uint32_t kSiteExpect = 0x39400358u;   // ldrb w24, [x26]
        if (wantC && !sCapApplied && mArm64Base && gProbePage) {
            uint32_t* site = reinterpret_cast<uint32_t*>(mArm64Base + kSite);
            gFfPairPeriod = property_get_int32("persist.gammaos.drastic_nano.ff_pair_period", 8);
            { const int rt = property_get_int32("sys.gammaos.drastic_nano.ff_skip_period_rt", -2);
              gFfSkipPeriod = rt >= -1 ? rt : property_get_int32("persist.gammaos.drastic_nano.ff_skip_period", 0); }
            if (gFfPairPeriod < 2) gFfPairPeriod = 2;
            gFfPairPhase = property_get_int32("sys.gammaos.drastic_nano.ff_pair_phase_rt", -1);
            if (gFfPairPhase < 0) gFfPairPhase = property_get_int32("persist.gammaos.drastic_nano.ff_pair_phase", 0) ? 1 : 0;
            if (*site == kSiteExpect) {
                // cave at probe page +2048: w0 = DISPCAPCNT (w8), w1 = skip flag, x2 = heapMaster (x19);
                // x16 is unused in the compose entry so it carries the result across the restores.
                uint32_t* c = reinterpret_cast<uint32_t*>(gProbePage + 2048);
                const uint32_t words[28] = {
                    0x39400358u,   // ldrb w24, [x26]
                    0xa9bf7bfdu,   // stp x29, x30, [sp, #-16]!
                    0xa9bf07e0u, 0xa9bf0fe2u, 0xa9bf17e4u, 0xa9bf1fe6u,   // stp x0..x7
                    0xa9bf27e8u, 0xa9bf2feau, 0xa9bf37ecu, 0xa9bf3feeu,   // stp x8..x15
                    0x2a0803e0u,   // mov w0, w8
                    0x2a1803e1u,   // mov w1, w24
                    0xaa1303e2u,   // mov x2, x19
                    0x580001f0u,   // ldr x16, [pc, #60]  -> literal at c[28]
                    0xd63f0200u,   // blr x16
                    0xaa0003f0u,   // mov x16, x0
                    0xa8c13feeu, 0xa8c137ecu, 0xa8c12feau, 0xa8c127e8u,   // ldp x14..x8
                    0xa8c11fe6u, 0xa8c117e4u, 0xa8c10fe2u, 0xa8c107e0u,   // ldp x6..x0
                    0xa8c17bfdu,   // ldp x29, x30, [sp], #16
                    0x2a1003f8u,   // mov w24, w16         skip flag
                    0xd360fe08u,   // lsr x8, x16, #32     DISPCAPCNT as saved for the capture block
                    0xd65f03c0u }; // ret
                memcpy(c, words, sizeof(words));
                const uint64_t fn = (uint64_t)(uintptr_t)&ffCapHook;
                memcpy(&c[28], &fn, 8);
                __builtin___clear_cache((char*)c, (char*)c + 128);
                const intptr_t d = (intptr_t)(gProbePage + 2048) - (intptr_t)(mArm64Base + kSite);
                raPatchInsn(mArm64Base, kSite, 0x94000000u | (uint32_t)((d >> 2) & 0x03ffffffu));
                sCapApplied = true;
                ALOGW("DrasticRunner: ff_capfix installed (+0x3c9a0 -> probe page +2048, pair period %d)", gFfPairPeriod);
            } else {
                ALOGW("DrasticRunner: ff_capfix: unexpected code at +0x3c9a0 (0x%08x), skipped", *site);
                sCapFix = 0;
            }
        } else if (!wantC && sCapApplied && mArm64Base && rt == 0) {
            raPatchInsn(mArm64Base, kSite, kSiteExpect);
            sCapApplied = false;
            ALOGW("DrasticRunner: ff_capfix removed");
        }
    }

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
    if (mFastUploadOn && !setupDsAhbTextures(newW, newH)) { patchFxUpload(false); mFastUploadOn = false; }

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
