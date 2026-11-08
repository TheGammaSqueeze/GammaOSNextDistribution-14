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
#include "NanoCtrHome.h"
#include "NanoMenuDrm.h"
#include "NanoMenuPS3Bg.h"
#include "NanoBacklight.h"
#include "NanoBrightness.h"

#include <algorithm>
#include <dirent.h>
#include <linux/input.h>
#include <malloc.h>
#include <sched.h>
#include <sys/mman.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <sys/system_properties.h>
#include <thread>
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

// The pid of a running process with this comm, or -1 (one /proc walk).
int findProcess(const char* comm) {
    DIR* d = opendir("/proc");
    if (!d) return -1;
    int pid = -1;
    struct dirent* e;
    char path[64], buf[64];
    while (pid < 0 && (e = readdir(d)) != nullptr) {
        if (e->d_name[0] < '0' || e->d_name[0] > '9') continue;
        snprintf(path, sizeof(path), "/proc/%s/comm", e->d_name);
        FILE* f = fopen(path, "r");
        if (!f) continue;
        if (fgets(buf, sizeof(buf), f)) {
            size_t n = strlen(buf);
            while (n && (buf[n - 1] == '\n' || buf[n - 1] == '\r')) buf[--n] = 0;
            if (strcmp(buf, comm) == 0) pid = atoi(e->d_name);
        }
        fclose(f);
    }
    closedir(d);
    return pid;
}

// True while that pid still runs a process with this comm (a single /proc read).
bool pidAlive(int pid, const char* comm) {
    if (pid <= 0) return false;
    char path[64], buf[64];
    snprintf(path, sizeof(path), "/proc/%d/comm", pid);
    FILE* f = fopen(path, "r");
    if (!f) return false;
    bool same = false;
    if (fgets(buf, sizeof(buf), f)) {
        size_t n = strlen(buf);
        while (n && (buf[n - 1] == '\n' || buf[n - 1] == '\r')) buf[--n] = 0;
        same = strcmp(buf, comm) == 0;
    }
    fclose(f);
    return same;
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

// Block until the session is over for the home: drastic-nano raised panel_free (it dropped DRM master,
// freed its ring and left the panels on a plain frame, with its save still running) or cleared the
// session (the classic end, a crash). 1 = panel_free, 2 = session ended, 0 = timeout. Both properties
// are read on every wake. The wait itself is on the session property alone: every end of the session
// changes it (init's session_done rule, and handPanelToHome sets it to 0 right after panel_free), and
// panel_free is already up by then. Waiting on the global serial instead woke this SCHED_FIFO 80 thread,
// whose pages are paged out for the game, on every property change in the system (drastic-nano alone
// publishes metrics once a second). The global serial is the fallback only while the property has no
// prop_info yet.
int waitPanelFreeOrSessionEnd(int timeoutMs) {
    const int64_t t0 = uptimeMillis();
    const prop_info* pi = nullptr;
    uint32_t serial = 0;
    for (;;) {
        char v[PROPERTY_VALUE_MAX] = {};
        property_get("sys.gammaos.drastic_nano.panel_free", v, "");
        if (strcmp(v, "1") == 0) return 1;
        property_get("sys.gammaos.drastic_nano.session", v, "");
        if (strcmp(v, "0") == 0) return 2;
        const int64_t left = timeoutMs - (uptimeMillis() - t0);
        if (left <= 0) return 0;
        if (!pi) pi = __system_property_find("sys.gammaos.drastic_nano.session");
        struct timespec ts = { (time_t)(left / 1000), (long)((left % 1000) * 1000000L) };
        __system_property_wait(pi, serial, &serial, &ts);   // pi null: the global serial
    }
}

// Wait (bounded) for the drastic-nano process to be gone. A new `start drastic-nano` is a no-op for init
// while the old instance still runs, and the next game must read the slot-9 save the old one is finishing.
bool waitDrasticGone(int timeoutMs) {
    const int64_t t0 = uptimeMillis();
    while (processAlive("drastic-nano") && uptimeMillis() - t0 < timeoutMs) usleep(20000);
    return !processAlive("drastic-nano");
}

// The RG DS Plus return hand-off of a game exit (drastic-nano main.cpp reads the same property): the
// game fades out, the save starts at the press, and the home takes the panels back while the save is
// still being written. persist.gammaos.drastic_nano.exit_handoff=0 switches it off, =1 turns it on
// for other devices.
bool returnHandoffOn() {
    char dev[PROPERTY_VALUE_MAX] = {};
    property_get("ro.gammaos.device", dev, "");
    return property_get_bool("persist.gammaos.drastic_nano.exit_handoff", strcmp(dev, "anbernicrgdsplus") == 0);
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

// After a return hand-off drastic-nano is still alive for the write, the stability wait, the fsync and
// the exit (several seconds) while this home is already back on screen. A DS launch waits for it in
// fireStart; this is the same wait for every other launch the home can make (an Android app, RetroArch
// or a libretro core, azahar), which end with this process leaving. Leaving early would run the app
// beside the old drastic-nano on 1 GB, and drastic-nano's session_done (set once it is done when the
// home is gone) would reset the launch flags and start a home over or under the app. The render
// watchdog is kept fed: the wait can outlast its 8 s. A no-op when no hand-off happened.
void NanoMenu::drasticTailWaitBeforeLaunch() {
    if (mOverlayMode || !property_get_bool("sys.gammaos.drastic_nano.panel_free", false)) return;
    const int pid = findProcess("drastic-nano");
    if (pid <= 0) return;
    const int64_t t0 = uptimeMillis();
    ALOGI("NanoMenu: waiting for drastic-nano to finish its save before the launch");
    while (pidAlive(pid, "drastic-nano") && uptimeMillis() - t0 < 15000) {
        mRenderHeartbeat.fetch_add(1, std::memory_order_relaxed);
        usleep(20000);
    }
    if (pidAlive(pid, "drastic-nano")) ALOGW("NanoMenu: drastic-nano still running after 15 s; launching anyway");
    else ALOGI("NanoMenu: drastic-nano gone after %lld ms, launching", (long long)(uptimeMillis() - t0));
}

// Pass 0 pages out the anonymous mappings (inode 0: the heap, [anon:*], bss), the part that matters
// under the game's memory pressure. Pass 1 pages out the rest: the mappings a file backs (libraries,
// fonts, a private mapping's copy-on-write pages, memfd). Pass 1 runs a few seconds into the session
// instead of before the launch: drastic-nano maps the same libraries (libGLES_mali, libgui, the media
// chain) and the kernel skips pages more than one process has mapped, so by then the shared ones stay
// resident and only the pages the home alone had go. Paged out before drastic-nano exists, they were
// dropped from the page cache and drastic-nano read them back from the microSD in its exec, EGL init
// and ROM load.
void NanoMenu::drasticParkPageOutAll(int pass) {
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
        if ((ino != 0) != (pass != 0)) continue;
        if (madvise((void*)lo, hi - lo, MADV_PAGEOUT) == 0) total += hi - lo; else failed++;
    }
    fclose(f);
    ALOGI("NanoMenu: park paged out %zu MB of %s mappings (%zu ranges refused)", total >> 20,
          pass ? "file-backed" : "anonymous", failed);
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
    mNavSfx.release();   // the DSi navigation and launch sounds: their stream kept the mixer awake into the game's exclusive open
    if (!mMpQueue.empty() && mMusicPlayer.isPlaying()) mpAudioCmd(MpAudioCmd::Stop);

    // The panel (DRM-direct home): drop DRM master, free the AHB scanout ring (its CMA) and,
    // plainly, close the fd. drastic-nano's drmEarlySplash takes master right after. On the
    // SF-hosted home there is nothing to drop: HWC loses master to the game and gets it back when
    // the game exits.
    const bool drmHome = sDrmActive;
    // DSi theme on the RG DS Plus: the launch effect ends in a white wash on both panels, and freeing
    // the ring turns the panels black until drastic-nano's splash. So the panels are kept lit on a
    // plain white frame across the hand-off (drmStopKeepBaton) and drastic-nano starts its own splash
    // and loading frame white too (it reads the colour below once and clears it), then fades the
    // white into the game. Other themes end their launch effect black and keep the plain stop.
    // persist.gammaos.nano.handoff_baton=0 switches it off, =1 turns it on for other devices.
    bool batonWanted = false;
    if (drmHome && mNdsTheme) {
        char dev[PROPERTY_VALUE_MAX] = {}, backend[PROPERTY_VALUE_MAX] = {};
        property_get("ro.gammaos.device", dev, "");
        // A SurfaceFlinger-hosted drastic-nano (backend=sf) needs the SurfaceFlinger gate open at once.
        property_get("persist.gammaos.drastic_nano.backend", backend, "auto");
        batonWanted = strcmp(backend, "sf") != 0 &&
                      property_get_bool("persist.gammaos.nano.handoff_baton", strcmp(dev, "anbernicrgdsplus") == 0);
    }

    // Fire the start trigger variant that does NOT `stop gammaos-nano`. init
    // consumes it at once (its rule resets the property to 0). If the running
    // init predates that rule (an older image with a newer nano), the value
    // stays at 2: fall back to the classic trigger, which stops this process
    // and starts the game exactly as before.
    // It fires before the rest of the park rather than after it: drastic-nano needs about 600 ms
    // from the trigger to ask for the panel (exec, link, mlockall, ROM path), and its SET_MASTER
    // retries for seconds, so the GPU park, the drop of master and the page-out below overlap
    // with its start-up instead of delaying it. (The rest of the park only needs this process.)
    // A relaunch ("Restart Game", a hardcore toggle, a restart-required setting) brings us
    // back here instead of waking the menu, so the session loop below can run more than once and
    // fires it again.
    // After a return hand-off (panel_free is still up from it) drastic-nano may still be saving when
    // the home is back on screen, so a launch fired in that window (the next game) first waits for the
    // old process: init's `start` would be a no-op for it, and the next game's auto-load must read the
    // finished slot 9. Every other session ended with the process gone before the home woke.
    auto fireStart = [&]() {
        if (property_get_bool("sys.gammaos.drastic_nano.panel_free", false) && !waitDrasticGone(15000))
            ALOGW("NanoMenu: the previous drastic-nano is still running; firing the launch anyway");
        property_set("sys.gammaos.drastic_nano.panel_free", "0");
        property_set("sys.gammaos.drastic_nano.panel_taken", "0");
        nanobr::brightnessRelease(300);   // persist now; the session pushes Settings
        property_set("sys.gammaos.drastic_nano.start", "2");
        if (!waitPropEquals("sys.gammaos.drastic_nano.start", "0", 3000)) {
            ALOGW("NanoMenu: init has no start=2 rule; falling back to the cold hand-off");
            property_set("sys.gammaos.nano.handoff_argb", "");
            property_set("sys.gammaos.nano.parked", "0");
            property_set("sys.gammaos.drastic_nano.home_sf", "0");   // this home is stopped, nobody is parked
            property_set("sys.gammaos.drastic_nano.start", "1");
            for (;;) sleep(1);   // init stops this service
        }
    };
    if (batonWanted) property_set("sys.gammaos.nano.handoff_argb", "ffffffff");
    // Tells drastic-nano a home is waiting to take the panels back (it hands them over early only then).
    if (drmHome) property_set("sys.gammaos.nano.parked", "1");
    // Tells drastic-nano a SurfaceFlinger-hosted home is parked, so on a plain exit it opens the
    // SurfaceFlinger gate at once: the composer's power cycle of both panels (about 2 s) then runs
    // while the game is torn down and the home wakes, not after the home's first frame. Never set
    // for the DRM home, whose panels are taken back through DRM master and must not be cycled.
    // Cleared at the wake below, in the fallback above and by the home's start and init's cold rules.
    if (!drmHome) property_set("sys.gammaos.drastic_nano.home_sf", "1");
    fireStart();

    // GPU memory the game cannot page out: the wave keyframe VBO and the glass
    // scratch (both rebuilt lazily on the next live frame, like the overlay park).
    ps3bg::freeWaveSeq();
    freeGlassScratch();
    // The whole XMB GPU working set (icons, normal maps, boxart, DSi sprites, the wave
    // scene): 100 MB of Mali memory that madvise cannot page out, and on the 1 GB Plus
    // it sat resident through the whole session while the kernel cycled the game's own
    // pages through zram (measured 2026-09-25, GPU 3D path, Mario Kart attract: about a
    // thousand pages swapped in and out per second and a third of a core in iowait with the
    // set resident; a quarter of that with it dropped, home Mali 100 MB to 45 MB). Same drop
    // as the overlay park; overlayGpuUnpark rebuilds it before the home draws again, verified
    // on screen across repeated sessions on the DSi theme. sys.gammaos.nano.park_gpu=0 keeps
    // the set resident if a theme ever comes back wrong.
    if (property_get_bool("sys.gammaos.nano.park_gpu", true)) overlayGpuPark();
    glFinish();

    // Drop DRM master and free the scanout ring (decided above). With the baton the panels stay on
    // their white frame, still held through this process's fd, until drastic-nano's splash has
    // replaced it (below).
    bool batonHeld = false;
    if (drmHome) {
        drmRememberInstallMatrix();
        if (batonWanted) {
            batonHeld = drmStopKeepBaton(0xFFFFFFFFu);
            // drastic-nano reads the colour at its start-up, 600 ms away: without a baton it starts dark.
            if (!batonHeld) property_set("sys.gammaos.nano.handoff_argb", "");
        } else {
            drmStop();
        }
    }
    property_set("sys.gammaos.nano.menu_active", "0");

    // Memory: the home pins itself with mlockall at startup; let every idle
    // page go to zram for the game. Not re-locked on wake (a whole-process
    // MCL_CURRENT would fault it all back synchronously on the render thread,
    // the stall that crashed the overlay); pages demand-fault from zram instead.
    munlockall();
    mallopt(M_PURGE_ALL, 0);
    // Push the idle anonymous pages out now (the overlay park does the same with its own memory).
    // Measured on the 1 GB Plus: the parked home sat at 112 to 141 MB
    // resident for the whole first minute of a Pokemon session while the kernel (swappiness
    // 180) cycled drastic-nano's and system_server's anonymous pages through zram at
    // thousands of swap-ins a second, and every 50 to 500 ms panel blit hang of the session
    // fell in that window. The parked thread waits on a property, so nothing here is needed
    // until the wake, and it demand-faults back then. sys.gammaos.nano.park_pageout=0 skips it.
    // The file-backed mappings follow a few seconds into the session (see drasticParkPageOutAll):
    // sys.gammaos.nano.park_pageout_file_delay_ms sets the delay, 0 pages them out here as well.
    const bool pageOut = property_get_bool("sys.gammaos.nano.park_pageout", true);
    const int fileDelayMs = property_get_int32("sys.gammaos.nano.park_pageout_file_delay_ms", 5000);
    if (pageOut) {
        drasticParkPageOutAll(0);
        if (fileDelayMs <= 0) drasticParkPageOutAll(1);
    }

    // The panels hold the white frame until drastic-nano's splash has replaced it, which happens
    // about 600 ms after the trigger, well before the ring it allocates later. Then the frame (3 MB
    // per panel) and the fd go, so they never compete with that ring for CMA. If it does not show
    // within 5 s of the trigger and its process is nowhere to be seen (it never started, or fell back
    // to SurfaceFlinger) the frame goes anyway and SurfaceFlinger gets its gate back, which is where
    // a plain stop leaves it. Once the process is seen the wait follows it instead, up to 20 s: its
    // splash can be late when its start-up is slow (the first launch after an image update used to
    // spend seconds merging the user's cheat database before it asked for the panel), and giving
    // up on a live process only turned the panels black until it got there. A drastic-nano that
    // ends before its splash (session_done, or its process seen and gone) does not keep the poll.
    // sys.gammaos.nano.baton_max_ms sets the cap for a live process (5000 is the old behaviour).
    if (batonHeld) {
        const int64_t b0 = uptimeMillis();
        bool handedOver = false, ended = false;
        int batonPid = -1, polls = 0;
        const int64_t liveMaxMs = std::min<int64_t>(60000, std::max<int64_t>(5000,
                property_get_int32("sys.gammaos.nano.baton_max_ms", 20000)));
        int64_t limitMs = 5000;
        while (uptimeMillis() - b0 < limitMs) {
            char sess[PROPERTY_VALUE_MAX] = {};
            property_get("sys.gammaos.drastic_nano.session", sess, "");
            if (drmBatonReplaced() || strcmp(sess, "1") == 0) { handedOver = true; break; }
            if (property_get_bool("sys.gammaos.drastic_nano.session_done", false)) { ended = true; break; }
            // The /proc walk that finds the process runs every 100 ms until it is seen (it is not up yet
            // for the first few hundred ms); afterwards only that pid's comm is read.
            if (batonPid <= 0) {
                if (polls++ % 5 == 0) {
                    batonPid = findProcess("drastic-nano");
                    if (batonPid > 0) limitMs = liveMaxMs;
                }
            } else if (!pidAlive(batonPid, "drastic-nano")) {
                ended = true;
                break;
            }
            usleep(20000);
        }
        drmBatonRelease();
        if (!handedOver) {
            if (ended) ALOGW("NanoMenu: drastic-nano ended before it replaced the hand-off frame");
            else ALOGW("NanoMenu: drastic-nano did not replace the hand-off frame within %lld ms",
                       (long long)(uptimeMillis() - b0));
            property_set("sys.gammaos.nano.drm_active", "0");
            property_set("sys.gammaos.nano.handoff_argb", "");
        } else {
            ALOGI("NanoMenu: hand-off frame replaced after %lld ms", (long long)(uptimeMillis() - b0));
        }
    }

  bool earlyHandOff = false;   // drastic-nano freed the panels with its save still running
  int gamePid = -1;
  for (;;) {
    earlyHandOff = false;
    // Wait for the session: drastic-nano asserts session=1 before its ROM load
    // and drastic-nano.rc clears it on session_done (clean exit and crash alike).
    // The session ends three ways and the home must come back from all of
    // them: a clean exit (session_done from main's tail), a crash with a
    // handler (drastic-nano's crashCleanup sets session_done from the signal
    // path), and a kill nothing can handle (an OOM kill, SIGKILL). The first
    // two clear the session property; the last only removes the process. So
    // once the process has been seen, wake on either signal.
    bool started = waitPropEquals("sys.gammaos.drastic_nano.session", "1", 30000);
    const int64_t tSession = uptimeMillis();
    bool filePassDone = !pageOut || fileDelayMs <= 0;   // switched off, or already done up front
    if (!started) {
        ALOGE("NanoMenu: drastic-nano never asserted its session; waking the home");
        drmBatonRelease();
    } else {
        // The liveness check runs once a second for the whole session: walking all of /proc for it
        // took 3-4 ms on a big core at this thread's SCHED_FIFO 80, preempting the emulator (seen in
        // a scheduler trace of a Pokemon White 2 session on the RP Duo Lite). Find the pid once and
        // then read only that pid's comm.
        gamePid = findProcess("drastic-nano");
        for (;;) {
            // The session ends for the home either when drastic-nano hands the panels over (a clean
            // exit to the home on the RG DS Plus: the game has faded out and the save is captured, the
            // write and the exit are still to come) or when the session is over (session_done).
            if (waitPanelFreeOrSessionEnd(1000) != 0) {
                earlyHandOff = property_get_bool("sys.gammaos.drastic_nano.panel_free", false);
                if (earlyHandOff) ALOGI("NanoMenu: drastic-nano handed the panels over (panel_free), it is still saving");
                else ALOGI("NanoMenu: drastic-nano session ended (session_done)");
                break;
            }
            if (gamePid <= 0) gamePid = findProcess("drastic-nano");
            if (!pidAlive(gamePid, "drastic-nano")) {
                ALOGE("NanoMenu: drastic-nano is gone without session_done (killed); waking the home");
                // Mirror what drastic-nano.rc does on session_done so a later
                // launch starts from a clean slate.
                property_set("sys.gammaos.drastic_nano.session", "0");
                property_set("sys.gammaos.drastic_nano.qr_resume", "0");
                break;
            }
            if (!filePassDone && uptimeMillis() - tSession >= fileDelayMs) {
                filePassDone = true;
                // The game has been running for a while: page out the home's file-backed pages
                // (drasticParkPageOutAll). A background job, not the render thread's real-time
                // priority, so it never takes CPU from the emulator, and the priority is given
                // back exactly as threadLoop set it (SCHED_FIFO 80, nice -20) for the wake.
                const pid_t tid = (pid_t)syscall(SYS_gettid);
                const int origPolicy = sched_getscheduler(0);
                sched_param origParam = {};
                sched_getparam(0, &origParam);
                sched_param other = {};
                sched_setscheduler(0, SCHED_OTHER, &other);
                setpriority(PRIO_PROCESS, tid, 10);
                drasticParkPageOutAll(1);
                if (origPolicy >= 0) sched_setscheduler(0, origPolicy, &origParam);
                setpriority(PRIO_PROCESS, tid, -20);
            }
        }
    }
    if (!earlyHandOff) {
        // A per-game performance mode parks the global one in perf_restore and drastic-nano puts
        // it back on exit; if the session ended without that (killed, power cut), do it here.
        drasticPerfRestoreCheck();
        // session_done is set right before the process returns from main; its DRM
        // fd (and master) goes away when the process is gone. Wait for that so the
        // SET_MASTER below does not race the exit.
        waitDrasticGone(15000);
    }
    // After a hand-off neither applies yet: drastic-nano has let go of the panels and its ring (the
    // reacquire below does not need the process gone) but it is still finishing the save, and its
    // exit restores the performance mode itself. The tail watcher below covers a kill in that time.

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
        fireStart();
        continue;
    }
    break;
  }
    // Drop any scripted nav queued while the home was parked (e.g. a framework power-hold
    // during the session): it was meant for a menu that was not on screen.
    property_set("sys.gammaos.nano.nav", "");
    const int64_t tWake = uptimeMillis();
    // The render thread was parked in this function for the whole session, so the
    // idle-sleep tick never saw the game as an unfocused state: restart the Screen
    // Timeout from the moment the home is back (it slept right after a long game).
    mLastInputMs = mLastPointerMs = tWake;

    // SurfaceFlinger's composition gate: drastic-nano set drm_active=1 for its
    // DRM session and a fresh home start would clear it in main(). On the SF
    // home SF must present again (our buffer queue stalls otherwise); on the
    // DRM home drmEarlySplash sets it back to 1 below. Not after a hand-off: drastic-nano's baton
    // still lights the panels, and opening the gate for the instant before the splash would let
    // SurfaceFlinger cycle the composer's power and take them.
    if (!(drmHome && earlyHandOff)) property_set("sys.gammaos.nano.drm_active", "0");
    property_set("sys.gammaos.nano.parked", "0");
    // The session is over (clean exit, crash or kill alike): no later exit may read a stale flag.
    property_set("sys.gammaos.drastic_nano.home_sf", "0");
    // No hand-off frame or colour may outlive the session (a drastic-nano that never started would
    // have left them); both are no-ops when drastic-nano took them.
    drmBatonRelease();
    property_set("sys.gammaos.nano.handoff_argb", "");

    const bool returnFx = returnHandoffOn();
    if (earlyHandOff) {
        // The home's wake work now runs beside drastic-nano's tail. Warm what the first frame
        // reads (the wave scene's two files, cold after the game's memory pressure) from a
        // low-priority helper while the render thread takes the panels back, and watch for the
        // process end for the one job that waited on it: the performance mode restore of a
        // process that was killed rather than exiting (its own exit restores it otherwise).
        if (property_get_bool("sys.gammaos.nano.park_prewarm", returnFx)) {
            std::thread([]() {
                pthread_setname_np(pthread_self(), "park-prewarm");
                sched_param sp = {};
                sched_setscheduler(0, SCHED_OTHER, &sp);
                setpriority(PRIO_PROCESS, (pid_t)syscall(SYS_gettid), 10);
                ps3bg::prewarmAssets();
            }).detach();
        }
        const int pid = gamePid;
        const int64_t tHand = tWake;
        std::thread([this, pid, tHand]() {
            pthread_setname_np(pthread_self(), "park-tail");
            sched_param sp = {};
            sched_setscheduler(0, SCHED_OTHER, &sp);
            setpriority(PRIO_PROCESS, (pid_t)syscall(SYS_gettid), 10);
            bool gone = false;
            if (pid > 0) {
                while (pidAlive(pid, "drastic-nano") && uptimeMillis() - tHand < 20000) usleep(50000);
                gone = !pidAlive(pid, "drastic-nano");
                ALOGI("NanoMenu: drastic-nano %s %lld ms after the hand-off",
                      gone ? "finished its save and exited" : "is still running", (long long)(uptimeMillis() - tHand));
            }
            // A process that was killed after the hand-off never reached the line of its tail that
            // clears the one-shot Quick Resume marker; left at 1 it would force a slot-9 load (and drop
            // RA hardcore) on the next game of this boot. Not once a new session has started. The
            // performance mode restore follows the same rule: a next game may already have parked its
            // per-game mode in perf_restore, which this must not undo.
            if (gone && !property_get_bool("sys.gammaos.drastic_nano.session", false)) {
                property_set("sys.gammaos.drastic_nano.qr_resume", "0");
                drasticPerfRestoreCheck();
            }
        }).detach();
    }

    // Take the panel back on the same EGL context. The DSi theme's return fades in from white and
    // drastic-nano leaves white behind, so the splash frame is white too (no dark flash between).
    if (drmHome && !drmReacquireForHome(mDisplay, 8000, (mNdsTheme && returnFx) ? 0xFFFFFFFFu : 0)) {
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
    resetHeldInputState("park wake");

    // Brightness: the game may have changed it (drastic-nano persists it before the hand-off); adopt it, relight the
    // panel on it and push Settings unconditionally (drastic leaves the Settings push to the home after a hand-off).
    {
        const int b = property_get_int32("persist.gammaos.nano.brightness", -1);
        if (b >= 1 && b <= 255) mBrightness = b;
        nanobr::brightnessAdopt(mBrightness, true);
    }

    // Launch state: the effect that played into the hand-off is over.
    mDrasticNanoPending = false;
    mLaunchFadeStart = 0;
    mExitRequested = false;
    // Return like a fresh home would: drill back to the launched card from the
    // path saved at launch and replay the DSi entrance cascade.
    ndsRestoreReturnPath();
    mNdsIntroStart = 0;
    // 3DS theme: the launch left the home in its app-running state behind the console's black
    // launch fade (nothing else ends it in a parked home); play the return to the menu.
    if (mCtrTheme && mCtrHome) mCtrHome->resumeFromApp();
    property_set("sys.gammaos.nano.menu_active", "1");
    property_set("sys.gammaos.nano.drop_input", "0");
    // The Recently Played row was updated at launch; save-state and playtime
    // rows re-read their files when they draw.
    mDrasticParked.store(false, std::memory_order_relaxed);
    ALOGW("NanoMenu: home back from the drastic-nano session (%s%s, parked %lld ms, wake %lld ms)",
          drmHome ? "DRM" : "SF", earlyHandOff ? ", panels handed over while it saves" : "",
          (long long)(tWake - tPark), (long long)(uptimeMillis() - tWake));
    return true;
}

} // namespace android
