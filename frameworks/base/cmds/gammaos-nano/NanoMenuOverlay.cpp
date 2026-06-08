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

// Overlay XMB: the power-hold in-game overlay.
//
// This file implements the show/hide lifecycle for the overlay instance of
// gammaos-nano (launched as `gammaos-nano --overlay`, see main.cpp). The
// overlay reuses the exact same PS3 XMB renderer as the home menu, but is
// presented on a TRANSLUCENT, SurfaceFlinger-background-blurred layer that
// sits above the running app. The app keeps running; SurfaceFlinger composites
// the blur of whatever is beneath the overlay layer, so we never capture the
// app's framebuffer (which is why this only works while SF + the app are up,
// i.e. Nano mode).
//
// Surface creation (translucent RGBA, hidden, very high Z) happens in
// NanoMenu::readyToRun()'s SF path, branched on mOverlayMode. Here we:
//   - overlayInitLayer(): apply the background blur radius once and confirm the
//     layer starts hidden;
//   - overlayPoll(): every tick, read sys.gammaos.nano.show_overlay (set by
//     PhoneWindowManager on a power-hold) and raise/dismiss accordingly;
//   - overlayShow()/overlayHide(): flip layer visibility + grab/release the
//     evdev input devices so navigation drives the XMB (not the app) while up.

#define LOG_TAG "GammaOSNano"

#include "NanoMenu.h"
#include "NanoMenuPS3.h"      // ps3::layoutComputeNative for the boot warm-up
#include "NanoMenuPS3Bg.h"    // ps3bg::init for the boot warm-up

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <signal.h>
#include <dirent.h>
#include <unistd.h>
#include <fcntl.h>
#include <thread>
#include <sched.h>
#include <sys/ioctl.h>
#include <linux/input.h>

#include <cutils/properties.h>
#include <utils/Log.h>
#include <utils/SystemClock.h>

#include <gui/Surface.h>
#include <gui/SurfaceComposerClient.h>
#include <gui/SurfaceControl.h>
#include <gui/LayerState.h>

namespace android {

// Records the PIDs frozen by the overlay so they can always be thawed if the
// overlay dies (graceful stop, crash, or kill) - prevents a wedged device.
static const char* kOverlayFrozenMarker = "/data/local/tmp/.nano_overlay_frozen";

static void overlayThawFromMarker();
static void overlayTermHandler(int);
// Defined further down (next to the launch builders) but used earlier by
// overlayQuitToHome.
static std::string overlayShq(const std::string& s);
static int overlaySignalPackage(const char* pkg, int sig);

void NanoMenu::overlayInitLayer() {
    // Crash/kill recovery: if a previous overlay instance died while an app was
    // frozen, thaw it now. Then install a SIGTERM/SIGINT handler so a graceful
    // `stop gammaos-nano-overlay` also thaws (init does not restart a stopped
    // service, so the handler is the only safety net there).
    overlayThawFromMarker();
    {
        struct sigaction sa = {};
        sa.sa_handler = overlayTermHandler;
        sigaction(SIGTERM, &sa, nullptr);
        sigaction(SIGINT, &sa, nullptr);
    }

    // The overlay is always the PS3 XMB layout. Force it on regardless of the
    // persist.gammaos.nano.ps3xmb home-mode flag so the overlay is always the
    // real XMB and never the legacy carousel / text menu.
    mPs3Xmb = true;
    mXmbMode = false;

    if (mFlingerSurfaceControl == nullptr || mSession == nullptr) {
        // SurfaceFlinger may not have produced a usable surface control yet (the
        // service can start at boot_completed before SF is fully ready). Do NOT
        // mark the layer initialised so the threadLoop gate retries on the next
        // tick; cap the retries so a permanently-null control cannot hot-loop.
        if (++mOverlayInitTries <= 150) {     // ~5s at the 33ms idle tick
            return;
        }
        ALOGW("overlay: giving up SF surface control init after %d tries "
              "(mFlingerSurfaceControl=%p)", mOverlayInitTries,
              mFlingerSurfaceControl.get());
        mOverlayInited = true;   // stop retrying; overlay stays inert this run
        return;
    }

    // TRANSLUCENT layer (no opaque flag, no background blur): the running app
    // shows through live and we paint an 80% dark scrim + the XMB chrome over it
    // (render()). No capture/blur/freeze, so the overlay appears instantly. Just
    // make sure we start hidden.
    SurfaceComposerClient::Transaction t;
    t.hide(mFlingerSurfaceControl);
    t.apply();
    mOverlayShown = false;
    mOverlayInited = true;   // only NOW, after the surface control is confirmed

    ALOGI("overlay: layer initialised (translucent live-app + scrim), waiting on "
          "sys.gammaos.nano.show_overlay");

    // Warm up the PS3 XMB NOW, at boot, so the FIRST show is instant. The overlay
    // renders nothing while hidden, so otherwise the expensive one-time init runs on
    // the first show and costs seconds: initPs3Menu() compiles the glass-icon shader,
    // bakes the console-icon normal maps, builds the categories and scans ROMs/apps,
    // and loads the theme; ps3bg::init() compiles the wave shader and loads its
    // geometry + sequence assets. Call them directly here (idempotent: initPs3Menu
    // gates on mPs3MenuBuilt, ps3bg::init on its own ready flag) while the layer is
    // still hidden - no render, no eglSwapBuffers, no input grab - so it is invisible
    // and safe. The GL context is current on this render thread. The first real show
    // then finds mPs3MenuBuilt=true and skips the rebuild, so it pops up immediately.
    if (mOverlayMode && !mPs3MenuBuilt) {
        ALOGI("overlay: warming PS3 XMB (menu + wave) at boot for instant first show");
        mPs3Xmb = true;
        initPs3Menu();
        ps3::layoutComputeNative(mWidth, mHeight);
        ps3bg::init();
        // In-game overlay perf: FREEZE the offscreen-only wave (the glass-icon
        // refraction source; the dark scrim hides the wave itself). It is rendered
        // ONCE and reused every frame - the animation is imperceptible in the small
        // glass icons on the 90% scrim, so it is visually identical while removing
        // the entire per-frame wave cost. Overlay process only; the home XMB and any
        // visible (composited) wave always render live.
        ps3bg::setScrimWaveFreeze(true);
        ALOGI("overlay: warm-up complete (menu built, glass + wave shaders ready)");
    }
}

// Send sig to every process of this package. The PIDs come from ActivityManager
// via dumpsys (binder) rather than a /proc walk: gammaos-nano runs in init's
// bootstrap mount namespace where readdir("/proc") does not enumerate other
// processes even with the readproc group, so /proc-based discovery found
// nothing. kill() on a known PID still works (we run as root). ProcessRecord
// lines read "<pid>:<pkg>[:tag]/uXXX", so grep that exact shape to get every
// process (main + helpers) of the package. Returns the count signalled.
// Return the PIDs of every process of a package (main + helpers), parsed from the
// ActivityManager binder dump - a /proc walk does not enumerate other processes
// from nano's bootstrap mount namespace, dumpsys does. Anchored on "ProcessRecord{"
// so the LRU "<uid>:<pkg>" lines cannot be mistaken for pids.
static std::set<int> overlayGetPids(const char* pkg) {
    std::set<int> pids;
    char cmd[256];
    snprintf(cmd, sizeof(cmd), "dumpsys activity processes '%s' 2>/dev/null", pkg);
    FILE* f = popen(cmd, "r");
    if (!f) { ALOGW("overlay: popen dumpsys failed"); return pids; }
    std::string out;
    char buf[4096];
    size_t r;
    while ((r = fread(buf, 1, sizeof(buf), f)) > 0) out.append(buf, r);
    pclose(f);
    size_t rp = 0;
    const std::string rec = "ProcessRecord{";
    while ((rp = out.find(rec, rp)) != std::string::npos) {
        size_t i = rp + rec.size();
        while (i < out.size() && out[i] != ' ' && out[i] != '}') i++;
        if (i < out.size() && out[i] == ' ') i++;
        size_t ds = i;
        while (i < out.size() && out[i] >= '0' && out[i] <= '9') i++;
        if (i > ds && i < out.size() && out[i] == ':' &&
            out.compare(i + 1, strlen(pkg), pkg) == 0) {
            pids.insert(atoi(out.substr(ds, i - ds).c_str()));
        }
        rp += rec.size();
    }
    return pids;
}

// True while any process of the package is still alive. Uses kill(pid,0) on a
// previously captured pid set - reliable even after dumpsys drops the
// ProcessRecord (which happens seconds before the process actually dies).
static bool overlayAnyAlive(const std::set<int>& pids) {
    for (int pid : pids) if (pid > 1 && kill(pid, 0) == 0) return true;
    return false;
}

static int overlaySignalPackage(const char* pkg, int sig) {
    std::set<int> pids = overlayGetPids(pkg);
    // Persist the frozen PIDs to a marker file BEFORE sending SIGSTOP so a SIGTERM
    // handler (graceful `stop`) or a startup-recovery pass after a crash/kill can
    // always thaw the app - otherwise a crash in the tiny window between freezing
    // and recording would leave the foreground app SIGSTOP'd with no record and
    // the device looks frozen. Writing the marker for a pid we then fail to stop
    // is harmless (a redundant SIGCONT later).
    if (sig == SIGSTOP) {
        int fd = open(kOverlayFrozenMarker, O_WRONLY | O_CREAT | O_TRUNC, 0600);
        if (fd >= 0) {
            for (int pid : pids) {
                char line[16];
                int len = snprintf(line, sizeof(line), "%d\n", pid);
                if (len > 0) (void)!write(fd, line, len);
            }
            close(fd);
        }
    }
    int n = 0;
    for (int pid : pids) {
        if (pid > 1 && kill(pid, sig) == 0) n++;
    }
    if (sig == SIGCONT) {
        unlink(kOverlayFrozenMarker);
    }
    ALOGI("overlay: %zu pid(s) for %s, signalled %d", pids.size(), pkg, n);
    return n;
}

// SIGCONT every PID listed in the frozen marker file, then remove it. Used both
// as crash recovery on overlay startup and from the SIGTERM handler. kill() and
// the file syscalls here are async-signal-safe.
static void overlayThawFromMarker() {
    int fd = open(kOverlayFrozenMarker, O_RDONLY);
    if (fd < 0) return;
    char buf[256];
    ssize_t got = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    if (got > 0) {
        buf[got] = '\0';
        const char* p = buf;
        while (*p) {
            int pid = 0;
            while (*p >= '0' && *p <= '9') { pid = pid * 10 + (*p - '0'); p++; }
            if (pid > 1) kill(pid, SIGCONT);
            while (*p && (*p < '0' || *p > '9')) p++;
        }
    }
    unlink(kOverlayFrozenMarker);
}

static void overlayTermHandler(int) {
    overlayThawFromMarker();
    _exit(0);
}

std::string NanoMenu::overlayResolveForegroundPkg() {
    // Resolve the top resumed package via ActivityManager (binder, works from
    // nano's restricted namespace). Returns a validated real 3rd-party-looking
    // package, or empty if there is nothing we should act on.
    char pkg[256] = {};
    FILE* f = popen("dumpsys activity activities 2>/dev/null | "
                    "grep -m1 ResumedActivity | "
                    "sed -nE 's#.* ([a-zA-Z0-9_.]+)/[^ }]+.*#\\1#p'", "r");
    if (f) {
        if (fgets(pkg, sizeof(pkg), f)) {
            size_t n = strlen(pkg);
            while (n > 0 && (pkg[n-1] == '\n' || pkg[n-1] == '\r' || pkg[n-1] == ' '))
                pkg[--n] = '\0';
        }
        pclose(f);
    }
    // Never target ourselves or the system; require a real package name with a dot.
    if (pkg[0] == '\0' || strchr(pkg, '.') == nullptr ||
        strstr(pkg, "gammaos") != nullptr ||
        strcmp(pkg, "android") == 0 ||
        strncmp(pkg, "com.android.systemui", 20) == 0) {
        return std::string();
    }
    return std::string(pkg);
}

void NanoMenu::overlayPauseApp(bool pause) {
    // DEFAULT OFF: do NOT freeze the background app. The opaque overlay layer
    // occludes the app so the compositor scans out only the overlay (60fps) while
    // the app keeps running underneath - freezing (SIGSTOP) was causing apps to
    // fail to resume cleanly. Re-enable with persist.gammaos.nano.overlay.pause=1.
    //
    // NOTE: mOverlayPausedPkg is populated by overlayShow() regardless of this
    // flag (so quit/launch always know the target). This routine only sends the
    // freeze/thaw signals, gated by the flag.
    if (!property_get_bool("persist.gammaos.nano.overlay.pause", false)) {
        return;
    }

    if (pause) {
        if (mOverlayPausedPkg.empty()) {
            ALOGI("overlay: no pausable foreground app");
            return;
        }
        std::string p = mOverlayPausedPkg;
        std::thread([p]() {
            int n = overlaySignalPackage(p.c_str(), SIGSTOP);
            ALOGI("overlay: paused (SIGSTOP) %s -> %d process(es)", p.c_str(), n);
        }).detach();
    } else {
        if (mOverlayPausedPkg.empty()) return;
        std::string p = mOverlayPausedPkg;
        std::thread([p]() {
            int n = overlaySignalPackage(p.c_str(), SIGCONT);
            ALOGI("overlay: resumed (SIGCONT) %s -> %d process(es)", p.c_str(), n);
        }).detach();
    }
}

void NanoMenu::overlayShow() {
    if (mOverlayShown) return;
    if (mFlingerSurfaceControl == nullptr) {
        ALOGW("overlay: show requested but no SF surface control; clearing request");
        property_set("sys.gammaos.nano.show_overlay", "0");
        return;
    }

    // The offscreen wave is frozen (rendered once, reused for glass-icon
    // refraction). Force one fresh re-render on each show so the glass picks up the
    // current day/night + theme even though the wave never runs per-frame.
    ps3bg::invalidateScrimWave();

    // Universal perf hint: request IPower FIXED_PERFORMANCE while the overlay is
    // up so the device holds a sustained performance level for the UI (init turns
    // the prop into `cmd power set-fixed-performance-mode-enabled`). Released in
    // overlayHide so a launched app gets normal DVFS back.
    if (property_get_bool("persist.gammaos.nano.perf.fixedperf", true))
        property_set("sys.gammaos.nano.fixedperf", "1");

    // Resolve the foreground package so quit/launch know what to act on. The
    // dumpsys resolve intermittently returns empty for a live game from the
    // overlay's process context; fall back to the tracked launch_app so
    // quit/launch/ESC still target the right package.
    mOverlayPausedPkg = overlayResolveForegroundPkg();
    if (mOverlayPausedPkg.empty() &&
        property_get_bool("sys.gammaos.nano.app_launched", false)) {
        char la[PROPERTY_VALUE_MAX] = {};
        property_get("sys.gammaos.nano.launch_app", la, "");
        if (la[0] && strchr(la, '.') != nullptr) mOverlayPausedPkg = la;
    }

    // Decide scrim-over-app vs full-wallpaper. A RWC hint (set when the overlay is
    // raised as the launcher after an app exits) forces wallpaper even if dumpsys
    // transiently still reports the dying app; otherwise infer from "no fg app".
    {
        char wp[PROPERTY_VALUE_MAX] = {};
        property_get("sys.gammaos.nano.overlay_wallpaper", wp, "0");
        // Wallpaper (launcher) vs scrim-over-app: decide from app_launched (a
        // reliable prop set when a nano app is running), NOT the dumpsys pkg resolve
        // above - that intermittently returns empty for a live game (RetroArch) and
        // wrongly flipped the in-game overlay to the full wave wallpaper. An app is
        // behind us iff app_launched==1 -> scrim; otherwise (launcher) -> wallpaper.
        // The RWC hint still forces wallpaper for the post-exit launcher raise even
        // in the moment before app_launched clears.
        // app_launched is the SOLE signal: scrim over the live app whenever a nano
        // app is launched, full wallpaper only in the launcher (no app). A stale
        // overlay_wallpaper hint must NOT force the wave over a running game (that
        // was the "submenus show the wave" bug). The launcher raise sets
        // app_launched=0 before show, so it correctly gets the wallpaper.
        bool appBehind = property_get_bool("sys.gammaos.nano.app_launched", false);
        mOverlayWallpaper = !appBehind;
        property_set("sys.gammaos.nano.overlay_wallpaper", "0");   // consume any hint
        ALOGI("overlay: show wallpaper=%d (app_launched=%d wp=%s paused=%s)",
              mOverlayWallpaper ? 1 : 0, appBehind ? 1 : 0, wp, mOverlayPausedPkg.c_str());
    }

    // Layer opacity. In WALLPAPER mode (no app behind us) the overlay fully covers
    // the screen with an opaque wave, so mark the SF layer OPAQUE (eLayerOpaque):
    // SurfaceFlinger can then scan it out on a hardware plane like the DRM home
    // (~60fps) instead of GPU-compositing a translucent full-screen layer every
    // frame (the ~30-44fps floor). In SCRIM mode the layer MUST stay translucent so
    // the live app shows through the 90% scrim. Re-evaluated on every show; prop-
    // gated (default on) so it can be A/B'd live via hide+show. The home XMB is a
    // separate DRM-direct process and is unaffected.
    {
        bool opaque = mOverlayWallpaper &&
            property_get_bool("persist.gammaos.nano.overlay.opaque_wallpaper", true);
        SurfaceComposerClient::Transaction()
            .setFlags(mFlingerSurfaceControl,
                      opaque ? layer_state_t::eLayerOpaque : 0u,
                      layer_state_t::eLayerOpaque)
            .apply();
        ALOGI("overlay: layer opaque=%d (wallpaper=%d)",
              opaque ? 1 : 0, mOverlayWallpaper ? 1 : 0);
    }

    // Re-apply the user's saved Theme Settings (wave colour, day/night, particles)
    // every time the overlay is raised, so the overlay wallpaper matches whatever
    // the user picked in the home XMB's Theme Settings - including changes made
    // AFTER this resident process started (persist props are only read once at
    // startup otherwise, leaving the overlay on the default wave).
    loadPs3ThemeSettings();

    // Reload Recently Played from disk on every raise. This resident overlay loaded
    // mXmbRecent once at startup; each game launch (this process AND the home DRM
    // nano's first launch) rewrites /data/system/nano_xmb_recent.list, so the
    // in-memory list drifts and the submenu showed a stale top entry / wrong index.
    // Re-reading here makes the list always reflect the most recently launched game.
    loadXmbRecent();

    // Isolate the running app's input via the FRAMEWORK drop_input path: while it
    // is set, InputDispatcher drops keys + motion to the app (POWER and BACK are
    // exempt) so the app cannot act on XMB navigation - the same principle as
    // Global Actions taking input for its own menu. nano reads evdev directly for
    // the XMB. No EVIOCGRAB (which fought PhoneWindowManager's power gesture).
    property_set("sys.gammaos.nano.drop_input", "1");

    // Prioritise this render thread (SCHED_FIFO) so the XMB stays smooth while it
    // GPU-composites over the LIVE app. The service has CAP_SYS_NICE + rtprio 99.
    // Match SurfaceFlinger's RT priority (its main + RenderEngine run at FIFO 2)
    // rather than sit ABOVE it: at FIFO 4 we preempted SF mid-CTM-composite on the
    // A53, pushing its present past the vsync deadline and causing the wave
    // wallpaper's ~50fps vsync-beat. Equal priority round-robins on contention so
    // SF keeps its composite slice, while we stay above all SCHED_OTHER threads.
    {
        struct sched_param sp = {};
        sp.sched_priority = 2;
        if (sched_setscheduler(0, SCHED_FIFO, &sp) != 0)
            ALOGW("overlay: SCHED_FIFO boost failed: %s", strerror(errno));
    }

    // DEFER the SF show: do NOT t.show() here. render() shows the layer only AFTER
    // it has composited the first faded-out (reveal~0) frame, so the entrance
    // animates IN instead of flashing the full XMB (the stale layer buffer). See
    // mOverlayPendingShow handling after the overlay eglSwapBuffers in render().
    mOverlayPendingShow = true;

    // Drain stale evdev events buffered while the overlay was HIDDEN (nano does
    // not read its input fds while idle, so they accumulate - including the power
    // gesture's own queued events, or a Back the user pressed in the app just
    // before summoning). Without this the very first pollInput flushes an old
    // Back/button press into the XMB and instantly dismisses the overlay ("shows
    // then disappears"). The fds are O_NONBLOCK so this returns at once.
    {
        struct input_event ev;
        for (int fd : mInputFds) {
            if (fd < 0) continue;
            while (read(fd, &ev, sizeof(ev)) == (ssize_t)sizeof(ev)) { /* discard */ }
        }
    }

    mOverlayShown = true;
    // Cold-boot-style fade + float-in of the XMB chrome (renderPs3Xmb stamps the
    // real start on the first rendered frame).
    mOverlayEnterStart = -2.0f;
    mPs3BootIconReveal = 0.0f;
    mPs3BootLabelReveal = 0.0f;
    mLastFrameNs = 0;
    ALOGI("overlay: shown (translucent live-app + scrim, drop_input=1)");
}

void NanoMenu::overlayHide() {
    if (!mOverlayShown) return;

    // Drop any input up to NOW before restoring the app's input - notably the BACK
    // press that dismissed the overlay. The overlay reads BACK via evdev and clears
    // drop_input here, so without this the just-pressed BACK could still be sitting
    // in InputDispatcher's queue and get delivered to the app once drop_input=0
    // (the "BACK bleeds into the app after dismiss" bug). The timestamp fence drops
    // any event with eventTime <= now (checked independently of drop_input); newer
    // events still reach the app. Same mechanism the home launch handoff uses.
    {
        int64_t fenceNs = uptimeMillis() * 1000000LL;
        char fb[32];
        snprintf(fb, sizeof(fb), "%lld", (long long)fenceNs);
        property_set("sys.gammaos.nano.drop_fence_ns", fb);
    }
    // Restore the app's input (the framework re-dispatches keys+motion to it).
    property_set("sys.gammaos.nano.drop_input", "0");
    // Release FIXED_PERFORMANCE so a launched app gets normal vendor DVFS.
    property_set("sys.gammaos.nano.fixedperf", "0");
    mOverlayPausedPkg.clear();

    // Drop back to normal scheduling so the resident-hidden overlay does not hold
    // a real-time priority while the app runs unobstructed.
    {
        struct sched_param sp = {};
        sp.sched_priority = 0;
        sched_setscheduler(0, SCHED_OTHER, &sp);
    }

    if (mFlingerSurfaceControl != nullptr) {
        SurfaceComposerClient::Transaction t;
        t.hide(mFlingerSurfaceControl);
        t.apply();
    }
    mOverlayShown = false;
    mOverlayPendingShow = false;   // cancel any deferred show (hidden before 1st frame)
    mOverlayWallpaper = false;     // next in-game summon starts in scrim mode
    ALOGI("overlay: hidden (drop_input=0)");
}

bool NanoMenu::overlayAtTopLevel() const {
    return mOverlayMode && mPs3Stack.empty() && !mPs3DlgActive &&
           !mPs3WizActive && !mPs3TzActive && mMenuState == MENU_MAIN;
}

void NanoMenu::overlayResume() {
    // In overlay-home LAUNCHER mode (full wallpaper, no app behind us) there is
    // nothing to return to - the overlay IS the home surface - so Back/Resume at
    // the top level must be a no-op. Dismissing would orphan the screen and the
    // framework would immediately re-raise it, a hide/show flicker loop. Only the
    // in-game overlay (scrim over a running app) dismisses on Back.
    if (mOverlayWallpaper &&
        property_get_bool("persist.gammaos.nano.overlay_home", false)) {
        ALOGI("overlay: resume ignored (launcher mode, nothing to return to)");
        return;
    }
    // Back / power at the XMB top level: dismiss the overlay and let the running
    // app take input again.
    property_set("sys.gammaos.nano.show_overlay", "0");
    overlayHide();
    ALOGI("overlay: resume -> dismissed, app resumed");
}

void NanoMenu::overlayQuitToHome() {
    // Quit the running app. The app is being killed so we must NOT thaw-then-resume
    // it: clear the paused state + marker first so overlayHide's thaw is a no-op.
    std::string pkg = mOverlayPausedPkg;
    mOverlayPausedPkg.clear();
    unlink(kOverlayFrozenMarker);
    bool isGame = !pkg.empty() &&
        (pkg.find("retroarch") != std::string::npos ||
         pkg.find("drastic") != std::string::npos);

    if (property_get_bool("persist.gammaos.nano.overlay_home", false)) {
        // Overlay-home: quit == return to the overlay launcher. Keep the overlay
        // shown and switch it to the opaque full-wallpaper XMB; clean-exit the app
        // behind it (ESC save-state for RetroArch/DraStic, force-stop otherwise).
        // app_launched=0 + show_overlay=1 keeps the RWC overlay-launcher
        // short-circuit active so the real launcher never appears.
        mOverlayWallpaper = true;
        property_set("sys.gammaos.nano.app_launched", "0");
        if (!pkg.empty()) {
            std::string p = pkg;
            std::thread([p, isGame]() {
                if (isGame) {
                    property_set("sys.gammaos.nano.qr_send_esc", "1");
                    for (int i = 0; i < 30 && overlaySignalPackage(p.c_str(), 0) > 0; i++)
                        usleep(100000);
                } else {
                    char c[320];
                    snprintf(c, sizeof(c), "am force-stop %s 2>/dev/null",
                             overlayShq(p).c_str());
                    system(c);
                }
                ALOGI("overlay: quit %s -> overlay launcher", p.c_str());
            }).detach();
        }
        return;   // stay shown as the launcher (drop_input stays 1)
    }

    // Non-overlay-home: force-stop and let the DRM home XMB take the display back.
    if (!pkg.empty()) {
        char cmd[320];
        snprintf(cmd, sizeof(cmd), "am force-stop %s 2>/dev/null",
                 overlayShq(pkg).c_str());
        system(cmd);
        ALOGI("overlay: quit -> force-stopped %s, returning to home XMB", pkg.c_str());
    }
    property_set("sys.gammaos.nano.show_overlay", "0");
    overlayHide();
}

// Single-quote a string for safe interpolation into a /bin/sh command line.
// Handles embedded apostrophes (ROM names like "Marvel's ...") via '\'' splicing.
static std::string overlayShq(const std::string& s) {
    std::string r = "'";
    for (char c : s) {
        if (c == '\'') r += "'\\''";
        else r += c;
    }
    r += "'";
    return r;
}

// Run a shell command and return its trimmed stdout (first use: pm path / settings).
static std::string overlayShellCapture(const char* cmd) {
    std::string out;
    FILE* f = popen(cmd, "r");
    if (!f) return out;
    char b[512];
    size_t n;
    while ((n = fread(b, 1, sizeof(b), f)) > 0) out.append(b, n);
    pclose(f);
    while (!out.empty() &&
           (out.back() == '\n' || out.back() == '\r' || out.back() == ' '))
        out.pop_back();
    return out;
}

// Build the Storage Access Framework content:// URI for a ROM, mirroring the
// home-mode launchXmbGame() encoding exactly so standalone emulators (DraStic,
// PPSSPP, Flycast) resolve the same document. romDir is the ROMs/ subdir used as
// the primary-volume fallback tree.
static std::string overlayBuildContentUri(const std::string& romPath,
                                          const std::string& romDir) {
    std::string filename = romPath;
    size_t ls = filename.rfind('/');
    if (ls != std::string::npos) filename = filename.substr(ls + 1);
    std::string encFile;
    for (char c : filename) {
        switch (c) {
            case ' ':  encFile += "%20"; break;
            case '(':  encFile += "%28"; break;
            case ')':  encFile += "%29"; break;
            case '&':  encFile += "%26"; break;
            case '+':  encFile += "%2B"; break;
            case '!':  encFile += "%21"; break;
            case '\'': encFile += "%27"; break;
            default:   encFile += c;
        }
    }
    std::string volumeId = "primary";
    std::string relDir = "ROMs%2F" + romDir;
    std::string work;
    if (romPath.find("/mnt/media_rw/") == 0) work = romPath.substr(14);
    else if (romPath.find("/storage/") == 0) work = romPath.substr(9);
    if (!work.empty()) {
        size_t sl1 = work.find('/');
        if (sl1 != std::string::npos) {
            std::string uuid = work.substr(0, sl1);
            if (uuid != "emulated") {
                size_t lastSl = work.rfind('/');
                std::string subdir = work.substr(sl1 + 1, lastSl - sl1 - 1);
                std::string encDir;
                for (char c : subdir) {
                    if (c == '/') encDir += "%2F";
                    else if (c == ' ') encDir += "%20";
                    else encDir += c;
                }
                volumeId = uuid;
                relDir = encDir;
            }
        }
    }
    std::string treeRoot = volumeId + "%3A" + relDir;
    return "content://com.android.externalstorage.documents/tree/" + treeRoot
         + "/document/" + treeRoot + "%2F" + encFile;
}

void NanoMenu::overlayLaunchCommand(const std::string& pkg, const std::string& amCmd) {
    // NOTE: no "pkg == mOverlayPausedPkg -> resume" shortcut here. A single
    // emulator package (com.retroarch.aarch64) hosts MANY games, so selecting a
    // different ROM of the running emulator MUST relaunch with the new ROM, not
    // resume the old game. The same-app resume shortcut lives in
    // overlayLaunchPackage (plain apps only); selecting a game always (re)launches.

    std::string old = mOverlayPausedPkg;
    mOverlayPausedPkg.clear();
    unlink(kOverlayFrozenMarker);

    // Hold the overlay up through the transition; overlayPoll() dismisses onto the
    // new app once it resumes (so the user never sees the dying app or a black
    // frame). Armed synchronously before the worker thread starts.
    mOverlayLaunchPending = true;
    mOverlayLaunchTarget = pkg;
    mOverlayLaunchStartMs = uptimeMillis();
    mOverlayLaunchLastCheckMs = 0;

    // Do the (possibly slow) clean exit + launch off the render thread so the XMB
    // keeps animating during the handoff.
    std::thread([this, old, pkg, amCmd]() {
        // Guard the whole exit+launch transition: RootWindowContainer skips ALL of
        // its startHome handling while killing=1, so force-stopping the old app
        // cannot make it falsely detect the (not-yet-registered) new app as
        // "exited" and tear it down. Same guard the home-mode nanoKillAppAndRestart
        // uses. Cleared only once the new app is actually the resumed activity.
        property_set("sys.gammaos.nano.killing", "1");

        bool oldIsGame = !old.empty() &&
            (old.find("retroarch") != std::string::npos ||
             old.find("drastic") != std::string::npos);
        if (oldIsGame) {
            // CLEAN SELF-CLOSE (no force-stop): capture the game's pids, send ESC
            // (RetroArch / DraStic save state then quit themselves), and WAIT for
            // those pids to ACTUALLY die via kill(pid,0). dumpsys drops the
            // ProcessRecord seconds before the process exits, so polling dumpsys was
            // premature and needed a force-stop to stop the relaunch racing the husk
            // - but a force-stop is a hard kill the user does not want. kill(pid,0)
            // on the captured pids is reliable, so the old game closes itself
            // cleanly (saving state) and the new ROM launches only once it is truly
            // gone. The death lands under killing=1 so the AMS overlay hook ignores it.
            std::set<int> oldPids = overlayGetPids(old.c_str());
            // Restore the game's input, then ask PhoneWindowManager to send ESCAPE
            // (overlay_esc -> triggerVirtualKeypress, the SAME path the back-long-
            // press uses; a shell-injected ESC is ignored by RetroArch). The game
            // must be the focused foreground window for this to land, which it is
            // (the overlay is an SF layer, not a focusable window).
            property_set("sys.gammaos.nano.drop_input", "0");
            property_set("sys.gammaos.nano.overlay_esc", "1");
            if (!oldPids.empty()) {
                bool alive = true;
                for (int i = 0; i < 100 && alive; i++) {   // up to ~10s for save+quit
                    usleep(100000);
                    // Re-send ESC a couple more times early, in case the first was
                    // dropped while focus settled after drop_input cleared.
                    if (i == 15 || i == 35)
                        property_set("sys.gammaos.nano.overlay_esc", "1");
                    alive = overlayAnyAlive(oldPids);
                }
                if (alive) {
                    // Last resort ONLY (it never exited - a hung save): force-stop so
                    // the relaunch does not race a stuck instance.
                    char c[320];
                    snprintf(c, sizeof(c), "am force-stop %s 2>/dev/null",
                             overlayShq(old).c_str());
                    system(c);
                    usleep(400000);
                    ALOGW("overlay: %s did not self-exit ~10s after ESC, force-stopped",
                          old.c_str());
                } else {
                    ALOGI("overlay: %s self-exited cleanly (saved state) before launch",
                          old.c_str());
                }
            } else {
                usleep(2500000);   // pids unknown: give the ESC time to save and quit
                ALOGI("overlay: ESC-exited %s (pids unknown, fixed wait)", old.c_str());
            }
        } else if (!old.empty()) {
            char c[320];
            snprintf(c, sizeof(c), "am force-stop %s 2>/dev/null",
                     overlayShq(old).c_str());
            system(c);
        }
        // Track for the framework: RootWindowContainer raises the overlay launcher
        // when this app exits, and PhoneWindowManager's back-long-press exit fires
        // (both gated on app_launched=1).
        if (!pkg.empty())
            property_set("sys.gammaos.nano.launch_app", pkg.c_str());
        property_set("sys.gammaos.nano.app_launched", "1");
        system(amCmd.c_str());
        ALOGI("overlay: launched %s", pkg.c_str());

        // Release the guard only once the new app is the resumed activity (or a
        // timeout), so RootWindowContainer never sees the gap between the old app
        // dying and the new one registering.
        for (int i = 0; i < 40; i++) {
            usleep(100000);
            if (pkg.empty() || overlayResolveForegroundPkg() == pkg) break;
        }
        property_set("sys.gammaos.nano.killing", "0");
    }).detach();
}

bool NanoMenu::overlayLaunchPackage(const std::string& pkg) {
    if (pkg.empty()) return false;
    // Selecting the app that is ALREADY running = just resume it (one app per
    // package, unlike emulators). Games never take this path (see overlayLaunchGame).
    if (pkg == mOverlayPausedPkg) { overlayResume(); return true; }
    // Plain app (Applications submenu): start its LAUNCHER activity.
    std::string cmd = "monkey -p " + overlayShq(pkg)
                    + " -c android.intent.category.LAUNCHER 1 2>/dev/null";
    overlayLaunchCommand(pkg, cmd);
    return true;
}

void NanoMenu::overlayPoll() {
    // Deferred dismiss after launching another app from the overlay: hold the
    // overlay layer up (it occludes the dying old app / black) until the new app
    // is the resumed activity, or a safety timeout. Throttle the ActivityManager
    // query so it does not run every frame.
    if (mOverlayLaunchPending) {
        int64_t now = uptimeMillis();
        int64_t el = now - mOverlayLaunchStartMs;
        // The launch worker holds sys.gammaos.nano.killing=1 for the WHOLE
        // exit+launch transition and clears it ONLY after the new app is the resumed
        // activity. Do NOT test foreground while killing=1: when switching to a
        // different ROM of the SAME emulator package (RetroArch A -> RetroArch B),
        // the OLD game is still that package and foreground, so foreground==target
        // would fire instantly and dismiss onto the dying old game before the new
        // one loads (the "switching games does nothing" bug). Wait for killing=0.
        bool switchInProgress = property_get_bool("sys.gammaos.nano.killing", false);
        // Launch abandoned backstop: the worker has finished (killing cleared)
        // but the launched app is not running (app_launched=0). The worker sets
        // app_launched=1 unconditionally before the am-start and only the AMS
        // death hook clears it, so app_launched=0 here means the app crashed/
        // exited during or right after the handoff and the foreground will never
        // become the target. Without this, mOverlayLaunchPending - and the
        // input-drain it triggers in pollInput() - would ride all the way to the
        // 12s ceiling, freezing the overlay home. Release the hold and fall
        // through to normal show/hide reconciliation (show_overlay is still 1, so
        // the overlay stays up as the home). A running app keeps app_launched=1,
        // so this can never dismiss a slow-but-valid launch; the el>1500 guard
        // skips the brief startup window before the worker sets killing/app_launched.
        if (!switchInProgress && el > 1500 &&
            !property_get_bool("sys.gammaos.nano.app_launched", false)) {
            mOverlayLaunchPending = false;
            mOverlayLaunchTarget.clear();
            ALOGI("overlay: launch abandoned (app not running, %lldms) - "
                  "releasing input hold", (long long)el);
            // fall through to show/hide reconciliation below (do NOT return)
        } else {
            bool ready = false;
            if (!switchInProgress && el > 250 && (now - mOverlayLaunchLastCheckMs) > 300) {
                mOverlayLaunchLastCheckMs = now;
                ready = (overlayResolveForegroundPkg() == mOverlayLaunchTarget);
            }
            // Ceiling is generous (12s): a RetroArch/DraStic clean-exit can take ~3s to
            // save state and die, plus a settle, plus the new app's own resume, plus the
            // worker's post-launch confirm poll. The `ready` check (after killing clears)
            // dismisses the instant the new app is up; the ceiling is only a backstop.
            if (ready || el > 12000) {
                mOverlayLaunchPending = false;
                mOverlayLaunchTarget.clear();
                property_set("sys.gammaos.nano.show_overlay", "0");
                overlayHide();
                ALOGI("overlay: launch dismiss (resumed=%d, %lldms)",
                      ready ? 1 : 0, (long long)el);
            }
            return;   // keep the overlay shown during the launch transition
        }
    }

    char v[PROPERTY_VALUE_MAX] = {};
    property_get("sys.gammaos.nano.show_overlay", v, "0");
    bool want = (v[0] == '1' && v[1] == '\0');
    if (want && !mOverlayShown) {
        overlayShow();
    } else if (!want && mOverlayShown) {
        overlayHide();
    }
    // No grab to reconcile: input isolation is the framework drop_input prop,
    // which overlayShow/overlayHide set/clear, and InputDispatcher self-clears a
    // stuck drop_input (plus the 10s BACK emergency) if this process ever dies
    // mid-show. The overlay never owns an evdev grab now.
}

void NanoMenu::overlayLaunchGame() {
    // Launch the currently selected XMB game/ROM from the overlay by building the
    // SAME activity intent the home-mode launchXmbGame() hands the framework, but
    // starting it directly with `am start` (no DRM-exit handshake, which must never
    // run in the resident overlay). Standalone emulators get the SAF content:// URI;
    // RetroArch gets the libretro extras (direct FUSE ROM path, no DE-cache shuffle
    // since the system is fully up). overlayLaunchCommand() handles the clean exit
    // of any running game first.
    std::string romPath, romDir, coreSo, launchPkg, launchIntent;
    bool standalone;
    if (mXmbSystemIndex == -1) {                  // Recently Played
        if (mXmbGameIndex < 0 || mXmbGameIndex >= (int)mXmbRecent.size()) return;
        const XmbRecentEntry& re = mXmbRecent[mXmbGameIndex];
        romPath = re.romPath; romDir = re.romDir; coreSo = re.coreSo;
        launchPkg = re.launchPkg; launchIntent = re.launchIntent; standalone = re.standalone;
    } else {                                      // per-system ROM list
        if (mXmbSystemIndex < 0 || mXmbSystemIndex >= (int)mXmbSystems.size()) return;
        const XmbSystem& sys = mXmbSystems[mXmbSystemIndex];
        if (mXmbGameIndex < 0 || mXmbGameIndex >= (int)sys.roms.size()) return;
        romPath = sys.roms[mXmbGameIndex]; romDir = sys.romDir; coreSo = sys.coreSo;
        launchPkg = sys.launchPkg; launchIntent = sys.launchIntent; standalone = sys.isStandalone();
    }
    if (romPath.empty()) return;

    std::string pkg, cmd;
    if (standalone) {
        pkg = launchPkg;
        std::string uri = overlayBuildContentUri(romPath, romDir);
        std::string intent = launchIntent;          // am-start arg template
        size_t pos = intent.find("{file.uri}");
        if (pos != std::string::npos) intent.replace(pos, 10, overlayShq(uri));
        cmd = "am start " + intent + " --grant-read-uri-permission 2>/dev/null";
    } else {
        pkg = "com.retroarch.aarch64";
        std::string rom = romPath;                   // direct FUSE path for RetroArch
        if (rom.find("/data/media/0/") == 0) rom = "/sdcard/" + rom.substr(14);
        else if (rom.find("/mnt/media_rw/") == 0) rom = "/storage/" + rom.substr(14);
        std::string apk = overlayShellCapture("pm path com.retroarch.aarch64 2>/dev/null");
        {   size_t pp = apk.find("package:");
            if (pp != std::string::npos) {
                apk = apk.substr(pp + 8);
                size_t nl = apk.find_first_of("\r\n");
                if (nl != std::string::npos) apk = apk.substr(0, nl);
            } else {
                apk.clear();
            }
        }
        std::string ime = overlayShellCapture(
                "settings get secure default_input_method 2>/dev/null");
        if (ime == "null") ime.clear();
        const std::string dataDir = "/data/user/0/com.retroarch.aarch64";
        const std::string ext = "/storage/emulated/0/Android/data/com.retroarch.aarch64/files";
        cmd = "am start -n com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture"
              " -a android.intent.action.MAIN -c android.intent.category.LAUNCHER"
              " --activity-clear-task --activity-clear-top"
              " --es ROM " + overlayShq(rom) +
              " --es CONFIGFILE " + overlayShq(ext + "/retroarch.cfg") +
              " --es DATADIR " + overlayShq(dataDir) +
              " --es SDCARD " + overlayShq(std::string("/storage/emulated/0")) +
              " --es EXTERNAL " + overlayShq(ext);
        if (!coreSo.empty())
            cmd += " --es LIBRETRO " + overlayShq("/data/data/com.retroarch.aarch64/cores/" + coreSo);
        if (!apk.empty()) cmd += " --es APK " + overlayShq(apk);
        if (!ime.empty()) cmd += " --es IME " + overlayShq(ime);
        cmd += " 2>/dev/null";
    }
    // Record the launch in Recently Played (mirror the home-mode launchXmbGame),
    // so games launched from the overlay show up in the overlay's Recently Played
    // list the next time it is opened. mXmbSystemIndex/mXmbGameIndex still hold the
    // selected entry. For a re-launch from Recently Played itself, move it to front.
    if (mXmbSystemIndex == -1) {
        if (mXmbGameIndex > 0 && mXmbGameIndex < (int)mXmbRecent.size()) {
            XmbRecentEntry moved = mXmbRecent[mXmbGameIndex];
            mXmbRecent.erase(mXmbRecent.begin() + mXmbGameIndex);
            mXmbRecent.insert(mXmbRecent.begin(), moved);
            saveXmbRecent();
        }
    } else {
        addXmbRecent(mXmbSystemIndex, mXmbGameIndex);
    }
    ALOGI("overlay: launch game pkg=%s standalone=%d rom=%s",
          pkg.c_str(), standalone ? 1 : 0, romPath.c_str());
    overlayLaunchCommand(pkg, cmd);
}

} // namespace android
