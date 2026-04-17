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

#include <dirent.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
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

#include <cutils/properties.h>
#include <system/thread_defs.h>
#include <utils/Log.h>
#include <utils/SystemClock.h>

#include "DrasticRunner.h"
#include "FakeJNI.h"
#include "NanoMenuDrm.h"

using android::DrasticRunner;

namespace {

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

constexpr int64_t     kBackHoldMs        = 3000;

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
    // Restore default disposition and re-raise so tombstone catches
    // the crash with a proper stack trace.
    signal(sig, SIG_DFL);
    raise(sig);
}

void installCrashHandler() {
    struct sigaction sa{};
    sa.sa_handler = crashCleanup;
    sigemptyset(&sa.sa_mask);
    // SA_NODEFER so re-raising the same signal inside the handler
    // re-enters with default disposition (SIG_DFL) and aborts the
    // process cleanly.
    sa.sa_flags = SA_NODEFER | SA_RESETHAND;
    sigaction(SIGBUS,  &sa, nullptr);
    sigaction(SIGSEGV, &sa, nullptr);
    sigaction(SIGABRT, &sa, nullptr);
    sigaction(SIGILL,  &sa, nullptr);
    sigaction(SIGFPE,  &sa, nullptr);
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
    android::drmEarlySplash();
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

// ------------------------------------------------------------------
// Input
// ------------------------------------------------------------------

struct InputState {
    std::vector<int> fds;
    int dsBtnMask;
    bool backWasDown;
    int64_t backPressStartMs;
};

void scanInputDevices(InputState* st) {
    DIR* d = opendir("/dev/input");
    if (!d) return;
    struct dirent* e;
    while ((e = readdir(d)) != nullptr) {
        if (strncmp(e->d_name, "event", 5) != 0) continue;
        std::string p = std::string("/dev/input/") + e->d_name;
        int fd = open(p.c_str(), O_RDONLY | O_NONBLOCK | O_CLOEXEC);
        if (fd < 0) continue;
        unsigned long keys[(KEY_MAX + 8 * sizeof(long)) /
                            (8 * sizeof(long))] = {};
        if (ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(keys)), keys) >= 0) {
            auto has = [&](int code) {
                return (keys[code / (8 * sizeof(long))] >>
                        (code % (8 * sizeof(long)))) & 1;
            };
            if (has(BTN_SOUTH) || has(BTN_A) || has(KEY_BACK) ||
                has(KEY_UP) || has(KEY_VOLUMEUP)) {
                st->fds.push_back(fd);
                continue;
            }
        }
        close(fd);
    }
    closedir(d);
}

void pollInput(InputState* st, bool* exitRequested) {
    for (int fd : st->fds) {
        struct input_event ev;
        while (read(fd, &ev, sizeof(ev)) == sizeof(ev)) {
            if (ev.type == EV_KEY) {
                const bool pressed = (ev.value != 0);
                if (ev.code == KEY_BACK) {
                    if (pressed && !st->backWasDown) {
                        st->backPressStartMs = android::elapsedRealtime();
                    }
                    if (!pressed) {
                        st->backPressStartMs = 0;
                    }
                    st->backWasDown = pressed;
                    continue;
                }
                auto bit = [&](int mask) {
                    if (pressed) st->dsBtnMask |=  mask;
                    else         st->dsBtnMask &= ~mask;
                };
                switch (ev.code) {
                case BTN_SOUTH:   bit(DrasticRunner::kDsBtnA);     break;
                case BTN_EAST:    bit(DrasticRunner::kDsBtnB);     break;
                case BTN_NORTH:   bit(DrasticRunner::kDsBtnX);     break;
                case BTN_WEST:    bit(DrasticRunner::kDsBtnY);     break;
                case BTN_TL:
                case KEY_L:       bit(DrasticRunner::kDsBtnL);     break;
                case BTN_TR:
                case KEY_R:       bit(DrasticRunner::kDsBtnR);     break;
                case BTN_START:   bit(DrasticRunner::kDsBtnStart); break;
                case BTN_SELECT:  bit(DrasticRunner::kDsBtnSelect); break;
                case KEY_UP:      bit(DrasticRunner::kDsBtnUp);    break;
                case KEY_DOWN:    bit(DrasticRunner::kDsBtnDown);  break;
                case KEY_LEFT:    bit(DrasticRunner::kDsBtnLeft);  break;
                case KEY_RIGHT:   bit(DrasticRunner::kDsBtnRight); break;
                default: break;
                }
            } else if (ev.type == EV_ABS) {
                if (ev.code == ABS_HAT0X) {
                    st->dsBtnMask &= ~(DrasticRunner::kDsBtnLeft |
                                        DrasticRunner::kDsBtnRight);
                    if (ev.value < 0) st->dsBtnMask |= DrasticRunner::kDsBtnLeft;
                    if (ev.value > 0) st->dsBtnMask |= DrasticRunner::kDsBtnRight;
                } else if (ev.code == ABS_HAT0Y) {
                    st->dsBtnMask &= ~(DrasticRunner::kDsBtnUp |
                                        DrasticRunner::kDsBtnDown);
                    if (ev.value < 0) st->dsBtnMask |= DrasticRunner::kDsBtnUp;
                    if (ev.value > 0) st->dsBtnMask |= DrasticRunner::kDsBtnDown;
                }
            }
        }
    }
    if (st->backPressStartMs > 0) {
        int64_t held = android::elapsedRealtime() - st->backPressStartMs;
        if (held >= kBackHoldMs) {
            ALOGW("drastic-nano: BACK held %lldms, exiting",
                  (long long)held);
            *exitRequested = true;
        }
    }
}

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
// Render loop
// ------------------------------------------------------------------

void runLoop(Display* dpy, DrasticRunner* dr) {
    bool hasDualDisplay = (android::sDrmActive && android::sDrmZeroCopy &&
                            android::sAhbRingSecondary[0].glFbo != 0);
    dr->initSurface(dpy->width, dpy->height, hasDualDisplay);
    dr->setRotationMatrix(android::sDrmRotMat);

    InputState input{};
    scanInputDevices(&input);
    ALOGI("drastic-nano: found %zu input devices", input.fds.size());

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

    // audioserver spawns its output thread after our first buffer
    // enqueue. Sweep after 1 s, 3 s, 5 s so we catch it regardless
    // of when drastic's audio engine actually comes up.
    int64_t audioBoostDeadlineMs = android::elapsedRealtime() + 1000;
    bool audioBoosted = false;
    int audioBoostSweeps = 0;

    while (!exitRequested) {
        if (!audioBoosted &&
            android::elapsedRealtime() >= audioBoostDeadlineMs) {
            boostAudioServer();
            audioBoostSweeps++;
            if (audioBoostSweeps >= 3) {
                audioBoosted = true;
            } else {
                audioBoostDeadlineMs =
                        android::elapsedRealtime() + 2000;
            }
        }
        pollInput(&input, &exitRequested);
        if (exitRequested) break;
        dr->setInput(input.dsBtnMask);

        dr->renderDsToOffscreen();

        const int renderIdx =
                tripleBuffer ? android::sRingRenderIdx : 0;
        android::AhbRenderTarget& primTgt =
                android::sAhbRingPrimary[renderIdx];
        android::AhbRenderTarget& secTgt =
                android::sAhbRingSecondary[renderIdx];

        if (hasDualDisplay) {
            glBindFramebuffer(GL_FRAMEBUFFER, secTgt.glFbo);
            glViewport(0, 0, (GLsizei)secTgt.w, (GLsizei)secTgt.h);
            dr->renderBottomScreen(saturation, gradient);

            glBindFramebuffer(GL_FRAMEBUFFER, primTgt.glFbo);
            if (android::sDrmGlRotation) {
                glViewport(0, 0, (GLsizei)primTgt.w,
                           (GLsizei)primTgt.h);
            } else {
                glViewport(0, 0, dpy->width, dpy->height);
            }
            dr->renderTopScreen(saturation, gradient);
        } else {
            glBindFramebuffer(GL_FRAMEBUFFER, primTgt.glFbo);
            if (android::sDrmGlRotation) {
                glViewport(0, 0, (GLsizei)primTgt.w,
                           (GLsizei)primTgt.h);
            } else {
                glViewport(0, 0, dpy->width, dpy->height);
            }
            dr->renderBothScreens(saturation, gradient);
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
            // slot we rendered ~2 frames ago. Present-lag = 2 with
            // ring depth = 4 leaves 1 slot of headroom, so GL and
            // scanout never race.
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
            union drm_wait_vblank vbl = {};
            vbl.request.type = (enum drm_vblank_seq_type)(
                    _DRM_VBLANK_RELATIVE
                    | ((android::sDrmPrimaryIdx & 0x1f)
                       << _DRM_VBLANK_HIGH_CRTC_SHIFT));
            vbl.request.sequence = 1;
            ioctl(android::sDrmFd, DRM_IOCTL_WAIT_VBLANK, &vbl);
        }
        android::drmDrainPageFlipEvents();
    }

    for (int fd : input.fds) close(fd);
}

}  // namespace

int main(int argc, char** argv) {
    setpriority(PRIO_PROCESS, 0, ANDROID_PRIORITY_DISPLAY);
    // Install early so any crash inside Drastic's native code or our
    // own init path still unblocks SurfaceFlinger / gammaos-nano.
    installCrashHandler();
    ALOGI("drastic-nano: starting (argc=%d)", argc);

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
    if (!setupDisplay(&dpy)) {
        ALOGE("drastic-nano: display setup failed");
        property_set(kSessionDoneProp, "1");
        return 6;
    }

    DrasticRunner dr;
    // cacheDir = drastic's installed files dir so every open / write
    // lands on the real files. libsDir points at the APK's
    // nativeLibraryDir so we get the unpatched libdrastic (real
    // audio). soundEnabled sets the _SoundEnabled config bit.
    if (!dr.init(kDrasticDataDir, romPath, libsDir,
                 /*soundEnabled=*/true)) {
        ALOGE("drastic-nano: DrasticRunner::init failed");
        property_set(kSessionDoneProp, "1");
        return 7;
    }

    runLoop(&dpy, &dr);

    // DrasticRunner destructor -> shutdown() -> pauseSystem +
    // quitSystem. That writes drastic's autosave + any dirty config
    // straight back to /data/user/0/com.dsemu.drastic/files/DraStic/
    // -- no sync-out step required.

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

    // SF was never stopped, so only the session_done trigger is
    // needed to bring nano back up on the XMB.
    property_set(kSessionDoneProp, "1");
    ALOGI("drastic-nano: exit");
    return 0;
}
