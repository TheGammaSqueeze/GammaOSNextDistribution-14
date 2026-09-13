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
std::atomic<bool>     gPaceOn{false};
std::atomic<uint32_t> gVblSeq{0};
std::mutex            gPaceMu;
std::condition_variable gPaceCv;
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
    gProducerDoneUs.store(now);
    const int64_t t = gLastTickUs.load();
    if (gPaceOn.load() && t > 0 && now - t > 0 && now - t < 30000) {
        const int64_t d = gEmuDurUs.load();
        gEmuDurUs.store((d * 7 + (now - t)) / 8);
    }
    if (gOrigSlotFlip) gOrigSlotFlip();
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
extern "C" void drasticVWait(unsigned usec) {
    gVWaitCount.fetch_add(1, std::memory_order_relaxed);
    if (!gPaceOn.load(std::memory_order_relaxed)) { usleep(usec); return; }
    std::unique_lock<std::mutex> lk(gPaceMu);
    const uint32_t seen = gVblSeq.load(std::memory_order_acquire);
    gPaceCv.wait_for(lk, std::chrono::milliseconds(50),
                     [&] { return gVblSeq.load(std::memory_order_acquire) != seen ||
                                  !gPaceOn.load(std::memory_order_relaxed); });
}
} // namespace

void DrasticRunner::installVblankPacing(uint8_t* base) {
    if (!base || mPanelHz <= 1.0) return;
    if (!property_get_bool("persist.gammaos.drastic_nano.vblank_pace", true)) return;
    const long ps = sysconf(_SC_PAGESIZE) > 0 ? sysconf(_SC_PAGESIZE) : 4096;
    // 1. OpenSL PCM sample rate (rodata, milliHz): both format tables.
    const uint32_t rate = (uint32_t)llround(44100000.0 * mPanelHz / 60.0);
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
    const int mode = property_get_int32("persist.gammaos.drastic_nano.t3d_sync", 5);
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

void DrasticRunner::reportFrameMiss(int source) {
    if (!gPaceOn.load()) return;   // bypass: the lock is not driving the emulator
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
        if (!gPaceBypass.load()) {
            if (gPaceOn.load() && emu > sBypassUs) {
                gPaceBypass.store(true); sBypassSinceUs = vblankUs; sProbeSinceUs = 0;
                ALOGW("PACE bypass: emulator %lld us per frame", (long long)emu);
                setVblankPacing(mPaceWanted);
            }
        } else if (sProbeSinceUs == 0 && vblankUs - sBypassSinceUs > 3000000 &&
                   (gEmuCpuLightSecs.load() >= 3 ||
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
            ALOGW("PACE lead=%lld misses=%u floor=%lld hookflips=%u emu=%lld", (long long)gLeadUs.load(),
                  gMissCount.load(), (long long)gLeadCreepFloor.load(), gFlipHookCount.load(),
                  (long long)gEmuDurUs.load());
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
void DrasticRunner::pacerThread() {
    pthread_setname_np(pthread_self(), "dn-pacer");
    int64_t nextTick = 0;
    while (mPacerRun.load()) {
        if (!gPaceOn.load()) { usleep(2000); nextTick = 0; continue; }
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
        const int64_t sleepUs = target - now;
        if (sleepUs > 0) usleep((useconds_t)sleepUs);
        nextTick = target;
        gLastTickUs.store(target);
        {
            const uint32_t k = gTickLogN.fetch_add(1) & 2047;
            gTickLog[k][0] = (int64_t)std::chrono::duration_cast<std::chrono::microseconds>(
                    std::chrono::steady_clock::now().time_since_epoch()).count();
            gTickLog[k][1] = last; gTickLog[k][2] = lead; gTickLog[k][3] = target;
        }
        { std::lock_guard<std::mutex> lk(gPaceMu); gVblSeq.fetch_add(1, std::memory_order_acq_rel); }
        gPaceCv.notify_all();
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
            gLastFrameUs.store((int64_t)std::chrono::duration_cast<std::chrono::microseconds>(
                    std::chrono::steady_clock::now().time_since_epoch()).count());
        }
        return true;   // never a pacing miss while the lock is off
    }
    bool dropped = false;
    mLastWaitImmediate = (*mask != 0);
    if (mLastWaitImmediate && gSteadyFrames.load() >= 120 &&
        nowUs - gProducerDoneUs.load() > 8000) {
        // The producer sets the mask a few microseconds before the flip
        // call that stamps the time: give a fresh frame that instant to
        // land before judging the mask stale.
        usleep(300);
    }
    if (mLastWaitImmediate && gSteadyFrames.load() >= 120 &&
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
        while (*mask == 0) {
            const int64_t now2 = (int64_t)std::chrono::duration_cast<std::chrono::microseconds>(
                    std::chrono::steady_clock::now().time_since_epoch()).count();
            if (now2 >= deadline) return dropped;
            gFlipCv.wait_for(lk, std::chrono::microseconds(deadline - now2));
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
    const int front = ((~cur) & 1) ? 1 : 0;
    const int32_t hr = *reinterpret_cast<int32_t*>(bss + 0x968);
    const int w = (hr + 1) << 8, h = (hr + 1) * 192;
    if (w != mZcImgW || h != mZcImgH) {
        for (int s2 = 0; s2 < 2; s2++) for (int k = 0; k < 2; k++) {
            if (mZcImg[s2][k]) { sEglDestroyImageKHR(sRingEglDpy, (EGLImageKHR)mZcImg[s2][k]); mZcImg[s2][k] = nullptr; }
            const EGLint attrs[] = {
                EGL_WIDTH, w, EGL_HEIGHT, h,
                EGL_LINUX_DRM_FOURCC_EXT, (EGLint)kDrmFormatAbgr8888,
                EGL_DMA_BUF_PLANE0_FD_EXT, mZcFd,
                EGL_DMA_BUF_PLANE0_OFFSET_EXT, (EGLint)(s2 * 0x180000 + k * 0xC0000),
                EGL_DMA_BUF_PLANE0_PITCH_EXT, w * 4,
                EGL_NONE };
            EGLImageKHR img = sEglCreateImageKHR(sRingEglDpy, EGL_NO_CONTEXT, EGL_LINUX_DMA_BUF_EXT, nullptr, attrs);
            if (img == EGL_NO_IMAGE_KHR) {
                ALOGW("DrasticRunner: zero-copy: dma-buf EGLImage %dx%d failed (0x%x)", w, h, eglGetError());
                mZcImgW = mZcImgH = 0;
                return false;
            }
            mZcImg[s2][k] = (void*)img;
        }
        mZcImgW = w; mZcImgH = h;
        ALOGW("DrasticRunner: zero-copy: dma-buf views %dx%d ready", w, h);
    }
    struct dma_buf_sync sync = {};
    sync.flags = DMA_BUF_SYNC_END | DMA_BUF_SYNC_WRITE;   // clean CPU writes to memory
    ioctl(mZcFd, DMA_BUF_IOCTL_SYNC, &sync);
    const unsigned texs[2] = { mDsTopTex, mDsBotTex };
    for (int k = 0; k < 2; k++) {
        glBindTexture(GL_TEXTURE_2D, texs[k]);
        sGlEGLImageTargetTexture2DOES(GL_TEXTURE_2D, (GLeglImageOES)mZcImg[front][k]);
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
    const uint8_t* front = ((~cur) & 1) ? slot1 : slot0;
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
    if (mWaitScreen &&
        property_get_int32("persist.gammaos.drastic_nano.frame_coherent", 0)) {
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
    const bool direct = mDirectFbo != 0 && mFxRender && !mFastForwardOn && !mFfBlendThisFrame;
    if (mOffscreenFbo != 0 && !direct) {
        glBindFramebuffer(GL_FRAMEBUFFER, mOffscreenFbo);
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
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
        if (mFastUploadOn) fastUploadFrame();
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
    // Completion: the emulator thread clears the request byte at
    // master+0x4b6 once the state is restored.
    if (mArm64Base) {
        std::thread([this, t0, slot] {
            volatile uint8_t* req = mArm64Base + 0x14c000 + 0x4b6;
            int spins = 0;
            while (*req != 0 && spins++ < 1000000) usleep(10);
            const int64_t t1 = std::chrono::duration_cast<std::chrono::microseconds>(
                    std::chrono::steady_clock::now().time_since_epoch()).count();
            ALOGW("STATELOAD slot %d done in %lld us", slot, (long long)(t1 - t0));
        }).detach();
    }
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
    return gEmuFrames.load();
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
