/*
 * GammaOS Nano: keep the DRM home resident across a drastic-nano session.
 *
 * The DRM-direct home used to _exit() the moment it fired the drastic-nano
 * start trigger, and init started a fresh gammaos-nano on session_done. That
 * cold start (process spawn, drastic preload warming, EGL + shader + font +
 * icon init, app list, library scan) is the multi-second black gap the user
 * sees after every game exit. Instead the home now PARKS in-process: it hands
 * the panel to drastic-nano and frees everything the game needs (DRM master,
 * the scanout ring and its CMA, locked memory, the audio and video decoders,
 * the big GPU scratch), blocks on the session, then takes the panel back and
 * resumes the very same menu state.
 *
 * Measured on the RG DS Plus (1 GB): with the ring and scratch gone the parked
 * home is ~36 MB of anonymous memory (swappable to zram once unlocked) plus
 * the GL context, against 236 MB available under Golden Sun, the heaviest
 * DS title known. See docs for the numbers.
 */
#define LOG_TAG "GammaOSNano"

#include "NanoMenu.h"
#include "NanoMenuDrm.h"
#include "NanoMenuPS3Bg.h"
#include "NanoBacklight.h"

#include <dirent.h>
#include <linux/input.h>
#include <malloc.h>
#include <sys/mman.h>
#include <sys/system_properties.h>
#include <unistd.h>
#include <cutils/properties.h>
#include <utils/Log.h>
#include <utils/SystemClock.h>

namespace android {

namespace {

// True while a process with this comm exists (drastic-nano is init-spawned,
// so it is a plain /proc walk; nano runs as root with readproc).
bool processAlive(const char* comm) {
    DIR* d = opendir("/proc");
    if (!d) return false;
    bool found = false;
    struct dirent* e;
    char path[64], buf[64];
    while (!found && (e = readdir(d)) != nullptr) {
        if (e->d_name[0] < '0' || e->d_name[0] > '9') continue;
        snprintf(path, sizeof(path), "/proc/%s/comm", e->d_name);
        FILE* f = fopen(path, "r");
        if (!f) continue;
        if (fgets(buf, sizeof(buf), f)) {
            size_t n = strlen(buf);
            while (n && (buf[n - 1] == '\n' || buf[n - 1] == '\r')) buf[--n] = 0;
            if (strcmp(buf, comm) == 0) found = true;
        }
        fclose(f);
    }
    closedir(d);
    return found;
}

// Block until the property holds `want` (or the timeout passes). Uses the
// property serial wait so the parked render thread costs nothing while the
// game runs.
bool waitPropEquals(const char* name, const char* want, int timeoutMs) {
    const int64_t t0 = uptimeMillis();
    const prop_info* pi = nullptr;
    uint32_t serial = 0;
    for (;;) {
        char v[PROPERTY_VALUE_MAX] = {};
        property_get(name, v, "");
        if (strcmp(v, want) == 0) return true;
        const int64_t left = timeoutMs - (uptimeMillis() - t0);
        if (left <= 0) return false;
        if (!pi) pi = __system_property_find(name);
        if (!pi) { usleep(50000); continue; }
        // Wait for the property's serial to move past the one we last saw
        // (0 on the first pass returns at once and seeds it), capped so the
        // value is re-read at least every second.
        struct timespec ts = { (time_t)(left / 1000), (long)((left % 1000) * 1000000L) };
        if (ts.tv_sec >= 1) { ts.tv_sec = 1; ts.tv_nsec = 0; }
        __system_property_wait(pi, serial, &serial, &ts);
    }
}

} // namespace

bool NanoMenu::drasticParkEnabled() const {
    // The home instance only (the resident overlay never runs a session itself).
    // Covers both the DRM-direct boot home and the SurfaceFlinger-hosted home a
    // later start runs as: in DRM mode the park drops master and takes it back,
    // in SF mode drastic-nano takes master from HWC and HWC gets it back when
    // the game exits, so the home's SF layer simply shows again.
    return !mOverlayMode
            && property_get_bool("persist.gammaos.nano.drastic_park", true);
}

void NanoMenu::drasticParkPageOutAll() {
    FILE* f = fopen("/proc/self/maps", "re");
    if (!f) return;
    char line[512];
    size_t total = 0, failed = 0;
    while (fgets(line, sizeof line, f)) {
        unsigned long lo = 0, hi = 0; char perms[8] = {0}; unsigned long off = 0; char dev[16] = {0}; unsigned long ino = 0;
        int n = 0;
        if (sscanf(line, "%lx-%lx %7s %lx %15s %lu %n", &lo, &hi, perms, &off, dev, &ino, &n) < 6) continue;
        const char* path = line + n;
        if (strstr(path, "[stack") || strstr(path, "[vvar]") || strstr(path, "[vdso]") || strstr(path, "/dev/")) continue;
        if (hi - lo < 16 * 1024) continue;
        if (madvise((void*)lo, hi - lo, MADV_PAGEOUT) == 0) total += hi - lo; else failed++;
    }
    fclose(f);
    ALOGI("NanoMenu: park paged out %zu MB of mappings (%zu ranges refused)", total >> 20, failed);
}

bool NanoMenu::drasticParkSession() {
    const int64_t tPark = uptimeMillis();
    ALOGW("NanoMenu: parking the home for the drastic-nano session (in-process)");
    mDrasticParked.store(true, std::memory_order_relaxed);

    // Decoders and audio streams: the game owns the single HW video decoder
    // and the audio mix, exactly as enterDrmSleep() tears them down.
    videoHardFree();
    if (mWpVideoTop) wpVideoStop();
    if (mAmbiancePlaying) { mAmbiancePlayer.stop(); mAmbiancePlaying = false; }
    if (!mAmbianceOpening) mAmbiancePlayer.release();
    if (!mSfxOpening.load()) mSfxPlayer.release();
    if (!mMpQueue.empty() && mMusicPlayer.isPlaying()) mpAudioCmd(MpAudioCmd::Stop);

    // GPU memory the game cannot page out: the wave keyframe VBO and the glass
    // scratch (both rebuilt lazily on the next live frame, like the overlay park).
    ps3bg::freeWaveSeq();
    freeGlassScratch();
    // The whole XMB GPU working set (icons, normal maps, boxart, DSi sprites, the wave
    // scene): 120 MB of Mali memory that madvise cannot page out, and on the 1 GB Plus
    // it sat resident through the game's whole launch window while the kernel cycled the
    // game's own pages through zram. Same drop as the overlay park; overlayGpuUnpark
    // rebuilds it before the home draws again. sys.gammaos.nano.park_gpu=0 keeps it.
    // DEFAULT OFF until verified on screen: the home came back black after a session on the
    // first build with this (render loop idle at 0% CPU after "GPU working set rebuilt").
    // Suspect: overlayGpuUnpark builds the whole PS3 menu on a DSi-theme home where it was
    // never built. sys.gammaos.nano.park_gpu=1 enables it for a test.
    if (property_get_bool("sys.gammaos.nano.park_gpu", false)) overlayGpuPark();
    glFinish();

    // The panel (DRM-direct home): drop DRM master, free the AHB scanout ring
    // (its CMA) and close the fd. drastic-nano's drmEarlySplash takes master
    // right after. On the SF-hosted home there is nothing to drop: HWC loses
    // master to the game and gets it back when the game exits.
    const bool drmHome = sDrmActive;
    if (drmHome) { drmRememberInstallMatrix(); drmStop(); }
    property_set("sys.gammaos.nano.menu_active", "0");

    // Memory: the home pins itself with mlockall at startup; let every idle
    // page go to zram for the game. Not re-locked on wake (a whole-process
    // MCL_CURRENT would fault it all back synchronously on the render thread,
    // the stall that crashed the overlay); pages demand-fault from zram instead.
    munlockall();
    mallopt(M_PURGE_ALL, 0);
    // Push every page out now, file-backed ones included (the overlay park only pages out
    // its anonymous memory). Measured on the 1 GB Plus: the parked home sat at 112 to 141 MB
    // resident for the whole first minute of a Pokemon session while the kernel (swappiness
    // 180) cycled drastic-nano's and system_server's anonymous pages through zram at
    // thousands of swap-ins a second, and every 50 to 500 ms panel blit hang of the session
    // fell in that window. The parked thread waits on a property, so nothing here is needed
    // until the wake, and it demand-faults back then. sys.gammaos.nano.park_pageout=0 skips it.
    if (property_get_bool("sys.gammaos.nano.park_pageout", true)) drasticParkPageOutAll();

    // Fire the start trigger variant that does NOT `stop gammaos-nano`. init
    // consumes it at once (its rule resets the property to 0). If the running
    // init predates that rule (an older image with a newer nano), the value
    // stays at 2: fall back to the classic trigger, which stops this process
    // and starts the game exactly as before.
    // A relaunch ("Restart Game", a hardcore toggle, a restart-required setting) brings us
    // back here instead of waking the menu, so the session loop below can run more than once.
  for (;;) {
    property_set("sys.gammaos.drastic_nano.start", "2");
    if (!waitPropEquals("sys.gammaos.drastic_nano.start", "0", 3000)) {
        ALOGW("NanoMenu: init has no start=2 rule; falling back to the cold hand-off");
        property_set("sys.gammaos.drastic_nano.start", "1");
        for (;;) sleep(1);   // init stops this service
    }

    // Wait for the session: drastic-nano asserts session=1 before its ROM load
    // and drastic-nano.rc clears it on session_done (clean exit and crash alike).
    // The session ends three ways and the home must come back from all of
    // them: a clean exit (session_done from main's tail), a crash with a
    // handler (drastic-nano's crashCleanup sets session_done from the signal
    // path), and a kill nothing can handle (an OOM kill, SIGKILL). The first
    // two clear the session property; the last only removes the process. So
    // once the process has been seen, wake on either signal.
    bool started = waitPropEquals("sys.gammaos.drastic_nano.session", "1", 30000);
    if (!started) {
        ALOGE("NanoMenu: drastic-nano never asserted its session; waking the home");
    } else {
        for (;;) {
            if (waitPropEquals("sys.gammaos.drastic_nano.session", "0", 1000)) {
                ALOGI("NanoMenu: drastic-nano session ended (session_done)");
                break;
            }
            if (!processAlive("drastic-nano")) {
                ALOGE("NanoMenu: drastic-nano is gone without session_done (killed); waking the home");
                // Mirror what drastic-nano.rc does on session_done so a later
                // launch starts from a clean slate.
                property_set("sys.gammaos.drastic_nano.session", "0");
                property_set("sys.gammaos.drastic_nano.qr_resume", "0");
                break;
            }
        }
    }
    // session_done is set right before the process returns from main; its DRM
    // fd (and master) goes away when the process is gone. Wait for that so the
    // SET_MASTER below does not race the exit.
    {
        const int64_t t0 = uptimeMillis();
        while (processAlive("drastic-nano") && uptimeMillis() - t0 < 15000) usleep(20000);
    }

    // "Restart Game", a hardcore toggle and the restart-required settings rows all exit
    // drastic-nano with auto_relaunch=1 and expect the ROM to come straight back.
    // gammaos-nano's main() honours that, but a PARKED home never re-enters main(), so the
    // request was dropped and the user was returned to the menu instead (reported 2026-09-22:
    // "Restart option in the drastic menu just kicks me back to nano menu"). Handle it here:
    // stay parked and fire the launch again, with no menu frame in between.
    if (property_get_bool("persist.gammaos.nano.drastic_nano", false) &&
        property_get_bool("sys.gammaos.drastic_nano.auto_relaunch", false)) {
        property_set("sys.gammaos.drastic_nano.auto_relaunch", "0");
        ALOGI("NanoMenu: drastic-nano relaunch requested while parked; re-firing the launch");
        continue;
    }
    break;
  }
    const int64_t tWake = uptimeMillis();

    // SurfaceFlinger's composition gate: drastic-nano set drm_active=1 for its
    // DRM session and a fresh home start would clear it in main(). On the SF
    // home SF must present again (our buffer queue stalls otherwise); on the
    // DRM home drmEarlySplash sets it back to 1 below.
    property_set("sys.gammaos.nano.drm_active", "0");

    // Take the panel back on the same EGL context.
    if (drmHome && !drmReacquireForHome(mDisplay, 8000)) {
        ALOGE("NanoMenu: could not take the panel back; restarting the home process");
        property_set("sys.gammaos.nano.park_restart", "1");
        for (;;) sleep(1);   // init restarts this service
    }

    // GPU working set back before the first frame (a no-op when it was kept).
    if (mOverlayGpuParked) overlayGpuUnpark();

    // Input: nothing read the evdev fds while parked, so drop whatever the
    // game session queued (a queued press must not act on the menu).
    for (int fd : mInputFds) {
        struct input_event ev[32];
        while (read(fd, ev, sizeof(ev)) > 0) {}
    }
    mWaitForRelease = false;

    // Brightness: the game may have changed it (drastic-nano writes the same
    // persist property); adopt it and relight the panel on our value.
    {
        char saved[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.brightness", saved, "");
        if (saved[0]) {
            int b = atoi(saved);
            if (b >= 1 && b <= 255) mBrightness = b;
        }
        nanobl::nanoBacklightSet(mBrightness);
    }

    // Launch state: the effect that played into the hand-off is over.
    mDrasticNanoPending = false;
    mLaunchFadeStart = 0;
    mExitRequested = false;
    // Return like a fresh home would: drill back to the launched card from the
    // path saved at launch and replay the DSi entrance cascade.
    ndsRestoreReturnPath();
    mNdsIntroStart = 0;
    property_set("sys.gammaos.nano.menu_active", "1");
    property_set("sys.gammaos.nano.drop_input", "0");
    // The Recently Played row was updated at launch; save-state and playtime
    // rows re-read their files when they draw.
    mDrasticParked.store(false, std::memory_order_relaxed);
    ALOGW("NanoMenu: home back from the drastic-nano session (%s, parked %lld ms, wake %lld ms)",
          drmHome ? "DRM" : "SF", (long long)(tWake - tPark), (long long)(uptimeMillis() - tWake));
    return true;
}

} // namespace android
