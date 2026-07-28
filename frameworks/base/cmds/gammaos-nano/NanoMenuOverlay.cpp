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
#include "NanoMenuUtils.h"    // setDrasticNanoRomPath for the overlay launch route
#include "NanoJson.h"        // per-app orientation override persistence

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
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

#include <ui/DisplayState.h>   // ui::DisplayState for overlayUpdateSurfaceSize
#include <gui/Surface.h>
#include <gui/SurfaceComposerClient.h>
#include <gui/SurfaceControl.h>
#include <gui/LayerState.h>

namespace android {

// Defined in NanoMenuDrm.cpp. Forward-declared here (rather than pulling in
// NanoMenuDrm.h, which drags in the DRM/NEON blit machinery) so the overlay
// surface can be recreated at the same EGLConfig it was born with.
EGLConfig getEglConfig(const EGLDisplay& display, bool wantAlpha);
void nanoSetOverlayRenderRotation(int rot);

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
    // GammaOS Nano: DRM-home XOR overlay. Latch that the overlay has run THIS session,
    // now that its SF layer is confirmed up. The crash-respawn trigger in gammaos-nano.rc
    // is gated on this (sys.gammaos.nano.overlay_ran) so it only re-raises a crashed
    // overlay-home AFTER the overlay has genuinely come up once - it can never fire at
    // the cold-boot home (where the overlay has never started) and hot-loop the DRM home.
    property_set("sys.gammaos.nano.overlay_ran", "1");

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
    // Never target the system; require a real package name with a dot. Do NOT
    // filter the whole "gammaos" namespace: the overlay is a separate --overlay
    // instance of this cc_binary with no package identity, so it can never be a
    // ResumedActivity, while com.gammaos.browser and com.gammaos.drasticsf are
    // real foreground apps that MUST be recognized here (otherwise the launch
    // handoff never sees them come to the front and the overlay only clears via
    // its 12s backstop, leaving the app hidden behind the XMB). quickKillApps
    // keeps its own independent gammaos kill-protection, so nothing gammaos gets
    // force-stopped by dropping the substring here.
    if (pkg[0] == '\0' || strchr(pkg, '.') == nullptr ||
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
    // The power-button overlay is taking over the top layer: cancel any in-flight focus-ring pulse and
    // hand ownership of mFlingerSurfaceControl to the overlay (do NOT let the ring hide it underneath).
    mCcRingDisp = -1;
    mTopRingShown = false;
    // Raising the full XMB/overlay ends any KEY_ALL_APPLICATIONS force-visible CC session: dropping back to
    // the app should not silently re-show the CC over it (the override is per-summon, not sticky).
    mCcForceVisible = false;

    // Clear any stale SELECT-held on the app->menu raise. Emulators (RetroArch/DraStic) EVIOCGRAB
    // the pad and the RetroArch back-override synthesizes a BTN_SELECT (via sendevent) while the
    // user holds BACK to exit; that write sets the kernel key bitmap, and when the app eats the
    // key-up on exit the BTN_SELECT stays stuck DOWN. selectKeyHeld() reads that bitmap, so a plain
    // volume press then adjusts brightness instead of volume. mSelectHeld is only set true by a
    // real BTN_SELECT event, so forcing it false here means SELECT+volume=brightness can only fire
    // after a genuine fresh Select press again - the stuck synthesized SELECT no longer hijacks it.
    mSelectHeld = false;

    // We deliberately do NOT mlockall() here on raise. The overlay's pages were released
    // by munlockall() while it was parked behind a running app (see threadLoop) so the game
    // could use that ~122MB. Re-locking here called mlockall(MCL_CURRENT), which faulted all
    // ~122MB back from zram SYNCHRONOUSLY on the render thread; over a live app (which must
    // be swapped out to make room) that stalled the render loop past the render watchdog ->
    // SIGABRT -> the overlay crashed and respawned, so it could never be shown or dismissed
    // (the reported "overlay won't dismiss" bug). Instead let the pages demand-fault from
    // zram (fast, lz4) as the overlay renders, spread across frames so the heartbeat keeps
    // ticking. The original mlockall (still unconditional at threadLoop, and active for the
    // DRM cold-boot home) guarded glyph faults off the SLOW lz4 EROFS system image; the
    // overlay's pages live in fast zram after parking, so demand-faulting them carries none
    // of that EROFS thrash/OOM risk.

    // The offscreen wave is frozen (rendered once, reused as the glass-icon
    // refraction source). We deliberately do NOT invalidate it on each show: the
    // overlay frees its 21MB of wave keyframes after the first bake (to give a
    // running game that RAM), so forcing a rebuild here would reload them off disk
    // and stall the power-hold raise. Reusing the baked frame keeps the raise
    // instant; the refraction is blurred and scrim-hidden so a slightly stale
    // day/night tint is imperceptible, and a real theme/day-night change still
    // bumps the wave epoch (setThemeColor/setDayNightBlend) to force one rebuild.

    // The FIXED_PERFORMANCE perf hint is owned by overlayApplyPresentMode (it is
    // mode-dependent: scrim only). See the comment there.

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
        // Raising the full launcher (no app behind) is the app-exit return. Clear any stale
        // SELECT-held here too (not just in overlayShow, which early-returns when the overlay is
        // already up over the app): the emulator ate the synthesized-SELECT up on exit, so drop
        // the held flag so volume stays volume and Power stays Power on the home.
        if (!appBehind) mSelectHeld = false;
        property_set("sys.gammaos.nano.overlay_wallpaper", "0");   // consume any hint
        ALOGI("overlay: show wallpaper=%d (app_launched=%d wp=%s paused=%s)",
              mOverlayWallpaper ? 1 : 0, appBehind ? 1 : 0, wp, mOverlayPausedPkg.c_str());
    }

    // PSP clock STANDALONE summon: the framework raised us (show_overlay=1) with
    // pspclock_summon=1 to invoke the clock over a running app via the slide, without the
    // user opening the overlay first. Force scrim-over-app (never the wave wallpaper) and
    // open the clock directly - there is no XMB behind it, so the entrance goes straight to
    // the exploding glyphs + disc drop (the blow-away producers no-op on mPspClockStandalone),
    // and nano lowers the overlay again once the clock retracts (drawPspClock teardown).
    if (property_get_bool("sys.gammaos.nano.pspclock_summon", false)) {
        mOverlayWallpaper = false;             // scrim over the live app, not the wave
        mPspClockStandalone = true;
        mPspClockRaisedOverlay = true;
        if (!mPspClockOn) mPspIconSeed += 17;  // fresh per-summon entrance avalanche
        mPspClockOn = true;
        mPspGlyphBurst = 1.0f;
    }

    // Layer opacity + swap pacing for the chosen mode (shared helper: the
    // quit-to-launcher path flips wallpaper mode WITHOUT a hide+show cycle and
    // must apply the exact same state).
    overlayApplyPresentMode();

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

    // Power-button raise over a running app: land on the Quick Menu (the GammaOS
    // legacy global actions) as the default category, mirroring the PS3 in-game XMB
    // opening on its system row. Only in scrim-over-app mode; when re-raised as the
    // post-exit launcher (wallpaper mode) keep the normal home category. Snap with no
    // rail animation and clear any leftover submenu / modal from a prior raise. The
    // Quick Menu is always the first category buildPs3Cats pushes (index 0).
    if (!mPs3MenuBuilt) initPs3Menu();   // ensure the categories exist before the snap
    int quickIdx = (mPs3QuickCatIdx >= 0) ? mPs3QuickCatIdx : 0;
    if (!mOverlayWallpaper && quickIdx < (int)mPs3Cats.size()) {
        mPs3Stack.clear();
        mPs3DlgActive = false; mPs3BrightSlider = false;
        mMenuState = MENU_MAIN;   // start each scrim raise at a clean top-level menu state;
                                  // a stale MENU_APPS/RECENT here used to block Back-to-dismiss
        mPs3CatIdx  = quickIdx;
        mPs3ItemIdx = 0;
        mPs3CatItemSel[mPs3CatIdx] = 0;
        mPs3AnimItem = 0.0f; mPs3ItemAnimStart = -1.0f; mPs3SubAnimStart = -1.0f;
        mPs3CatAnimActive = false; mPs3CatT = 1.0f; mPs3CatFromOffset = 0.0f;
    }

    // DSi theme, app-EXIT return only (wallpaper/launcher raise, app_launched=0): replay the
    // card-drop entrance cascade like a fresh home (user request), and reflect the Recently
    // Played reorder. A scrim raise OVER a running app (app_launched=1) does neither - the
    // menu just appears over the paused game. loadXmbRecent() above already reloaded the list
    // (the just-played game moved to the front); rebuild any open Recently Played level from it
    // so it shows the new order and lands on that game, and re-arm the intro so the cards drop.
    if (mOverlayWallpaper && mNdsTheme) {
        mNdsIntroStart = 0;   // renderNdsCarousel re-fires the DSi card-drop on the next frame
        bool rebuiltRecent = false;
        for (auto& lvl : mPs3Stack) {
            if (!lvl.items.empty() && lvl.items[0].kind == PS3_RECENT) {
                buildRecentSubmenu(lvl);   // rebuild from the freshly reloaded mXmbRecent
                lvl.sel = 0;               // the just-played game is now the front card
                rebuiltRecent = true;
            }
        }
        if (rebuiltRecent) {
            mNdsCamera = (float)ndsFocusSel();
            mNdsScrubbing = false; mNdsFlingVel = 0.0f; mNdsFastScroll = false;
            mPs3AnimItem = (float)ndsFocusSel(); mPs3ItemAnimStart = -1.0f;
        }
    }

    mOverlayShown = true;
    // Cold-boot-style fade + float-in of the XMB chrome. -2 = pending; the first
    // rendered frame begins it and it then advances by clamped per-frame dt, so the
    // one-time lazy work on the first-ever raise cannot skip the animation.
    mOverlayEnterStart = -2.0f;
    mOverlayEnterElapsed = 0.0f;
    mPs3BootIconReveal = 0.0f;
    mPs3BootLabelReveal = 0.0f;
    mLastFrameNs = 0;
    // Publish nano's orientation immediately so WM clamps the display to landscape
    // (or the chosen nano orientation) the instant the overlay raises, instead of
    // waiting for the next periodic tick - otherwise the overlay flashes truncated
    // over a portrait app.
    orientationTick();
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
        // Also hide the secondary (bottom) surface, atomically with the primary, so the DSi
        // in-game overlay's opaque RGBX bottom panel never covers the resumed game's bottom
        // screen. The render-loop toggle also hides it (show_overlay=0), this is the belt.
        for (const auto& sc : mSecondaryWallpaperControls) t.hide(sc);
        t.apply();
        mNdsSecondaryShown = false;   // re-show on the next summon
        mTopRingShown = false;        // this hide also covers any focus-ring layer state
    }
    mOverlayShown = false;
    mOverlayPendingShow = false;   // cancel any deferred show (hidden before 1st frame)
    mOverlayWallpaper = false;     // next in-game summon starts in scrim mode
    // Clear any PSP-clock standalone summon so a stale prop cannot re-trigger the
    // standalone path on an unrelated later raise (e.g. a power-hold quick-menu).
    property_set("sys.gammaos.nano.pspclock_summon", "0");
    mPspClockStandalone = false;
    mPspClockRaisedOverlay = false;
    // Clear the overlay-foreground flag here so the tail orientationTick() below always
    // resolves the foreground APP's token (its per-app override or its own request),
    // instead of relying on every caller to have cleared it first. All current dismiss
    // callers already clear it, so this is a no-op today but prevents a future caller
    // from leaving the app clamped to nano's orientation after dismiss.
    property_set("sys.gammaos.nano.show_overlay", "0");
    // Hand orientation back to the foreground app (its per-app override or its own
    // request) the instant the overlay is dismissed.
    orientationTick();
    ALOGI("overlay: hidden (drop_input=0)");
}

bool NanoMenu::overlayAtTopLevel() const {
    // Back dismisses the overlay (resumes the running app) when it is at a bare top-level
    // XMB category with no submenu/modal open. The sub-screen states (Settings/WiFi/BT) are
    // already diverted by handleBack() before ps3XmbBack(), and the setup wizard is guarded
    // by mPs3WizActive, so the only menu states that reach here are the top-level category
    // states MAIN / RECENT / APPS - all of which must dismiss on Back (categories are moved
    // through with Left/Right, not Back). Requiring exactly MENU_MAIN was the dismiss bug: a
    // stale/navigated MENU_APPS left Back doing nothing over a live game
    // (overlay-back-diag showed menuState=2 stack=0 atTop=0).
    return mOverlayMode && mPs3Stack.empty() && !mPs3DlgActive &&
           !mPs3WizActive && !mPs3TzActive &&
           (mMenuState == MENU_MAIN || mMenuState == MENU_RECENT || mMenuState == MENU_APPS);
}

void NanoMenu::overlayResume() {
    stopEqPreview();   // never leave the GammaEQ preview clip playing into a resumed app
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

// Layer opacity + swap pacing for the current wallpaper/scrim mode.
// In WALLPAPER mode (no app behind us) the overlay fully covers the screen with
// an opaque wave, so mark the SF layer OPAQUE (eLayerOpaque): SurfaceFlinger can
// then occlusion-cull everything beneath and scan out on a hardware plane where
// possible, instead of GPU-compositing a translucent full-screen layer every
// frame. In SCRIM mode the layer MUST stay translucent so the live app shows
// through the 90% scrim. Prop-gated (default on) so it can be A/B'd live.
//
// Swap pacing: WALLPAPER mode uses vsync-blocked swaps (interval 1), exactly
// like the cold-boot home. Measured on the Brick: the home presents 59.8fps
// (SF timestats, 0 dropped) while a free-running interval-0 + wall-clock-sleep
// overlay presented only 51.8fps at the same ~59fps render-loop rate - the
// submission phase drifts across SurfaceFlinger's latch point and ~8 frames/sec
// get replaced in the queue, which the eye reads as constant judder even though
// the loop counter says 60. SCRIM mode keeps interval 0 + the threadLoop top-up
// sleep; blocking on vsync there would contend with the game's own pipeline and
// that mode is verified smooth as-is.
// Must run on the render thread: eglSwapInterval applies to the surface current
// on the calling thread.
void NanoMenu::overlayApplyPresentMode() {
    if (mFlingerSurfaceControl != nullptr) {
        bool opaque = mOverlayWallpaper &&
            property_get_bool("persist.gammaos.nano.overlay.opaque_wallpaper", true);
        SurfaceComposerClient::Transaction()
            .setFlags(mFlingerSurfaceControl,
                      opaque ? layer_state_t::eLayerOpaque : 0u,
                      layer_state_t::eLayerOpaque)
            // Belt-and-braces: make sure no background blur is requested on
            // this layer (nano never sets one, but blur with nothing behind
            // the launcher wallpaper would be pure SF GPU waste).
            .setBackgroundBlurRadius(mFlingerSurfaceControl, 0)
            .apply();
        ALOGI("overlay: layer opaque=%d (wallpaper=%d)",
              opaque ? 1 : 0, mOverlayWallpaper ? 1 : 0);
    }
    if (mDisplay != EGL_NO_DISPLAY)
        eglSwapInterval(mDisplay, mOverlayWallpaper ? 1 : 0);

    // Universal perf hint: request IPower FIXED_PERFORMANCE while the overlay
    // is up, in BOTH modes (init turns the prop into `cmd power
    // set-fixed-performance-mode-enabled`). A wallpaper-only-unpinned variant
    // was A/B'd on the Brick and the apparent win did not reproduce across
    // runs (the governor can sag under the steady wave load); the pinned
    // sustained level matches the build that measured 60.1fps presented in the
    // post-game launcher. Released in overlayHide.
    if (property_get_bool("persist.gammaos.nano.perf.fixedperf", true))
        property_set("sys.gammaos.nano.fixedperf", "1");
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
        // The mode flips WITHOUT a hide+show cycle, so re-apply the wallpaper
        // presentation state here (opaque layer + vsync-locked swaps). Without
        // this the launcher kept the scrim's translucent layer + free-running
        // swap interval: SF could not occlusion-cull the dead app's layer and
        // the wave juddered (~34fps presented at a 59fps render loop).
        overlayApplyPresentMode();
        property_set("sys.gammaos.nano.app_launched", "0");
        if (!pkg.empty()) {
            std::string p = pkg;
            std::thread([p, isGame]() {
                if (isGame) {
                    property_set("sys.gammaos.nano.qr_send_esc", "1");
                    for (int i = 0; i < 30 && overlaySignalPackage(p.c_str(), 0) > 0; i++)
                        usleep(100000);
                }
                // Force-stop if still alive: either a non-game app, or a game
                // that ignored the injected ESC (observed: RetroArch keeps
                // rendering at ~33fps behind the opaque launcher forever,
                // burning GPU/CPU the cold-boot menu never pays). The 3s ESC
                // grace above still gives RetroArch/DraStic their clean
                // save-state exit when they do honor it.
                if (overlaySignalPackage(p.c_str(), 0) > 0) {
                    char c[320];
                    snprintf(c, sizeof(c), "am force-stop %s 2>/dev/null",
                             overlayShq(p).c_str());
                    system(c);
                    ALOGI("overlay: quit %s ignored ESC, force-stopped", p.c_str());
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

// Quick Menu: replicate the GammaOS legacy "Kill Background Apps" / "Kill All
// Apps" global actions. `pm list packages -3` enumerates only third-party
// (non-system) packages, mirroring the legacy action's FLAG_SYSTEM skip;
// force-stopping a package that is not running is a harmless no-op, so the
// observable result (every third-party app stopped) matches the legacy
// ActivityManager.forceStopPackage sweep over running processes. The Kill
// Background variant skips the current foreground package so the running game
// keeps going. Runs on a detached thread because the sweep shells out per
// package and must not stall the render loop.
void NanoMenu::quickKillApps(bool includeForeground) {
    std::string fg = includeForeground ? std::string() : overlayResolveForegroundPkg();
    std::thread([includeForeground, fg]() {
        std::string list = overlayShellCapture("pm list packages -3 2>/dev/null");
        size_t pos = 0;
        int killed = 0;
        while (pos < list.size()) {
            size_t nl = list.find('\n', pos);
            std::string line = list.substr(pos, nl == std::string::npos ? std::string::npos : nl - pos);
            pos = (nl == std::string::npos) ? list.size() : nl + 1;
            const char* pfx = "package:";
            size_t p = line.find(pfx);
            std::string pkg = (p == std::string::npos) ? line : line.substr(p + strlen(pfx));
            while (!pkg.empty() && (pkg.back() == '\r' || pkg.back() == '\n' || pkg.back() == ' '))
                pkg.pop_back();
            if (pkg.empty() || pkg.find('.') == std::string::npos) continue;
            if (pkg.find("gammaos") != std::string::npos) continue;          // never ourselves
            if (!includeForeground && !fg.empty() && pkg == fg) continue;    // keep the running game
            char c[320];
            snprintf(c, sizeof(c), "am force-stop %s 2>/dev/null", overlayShq(pkg).c_str());
            system(c);
            killed++;
        }
        ALOGI("nano: quickKillApps(includeForeground=%d) force-stopped %d third-party packages",
              includeForeground, killed);
    }).detach();
}

// Quick Menu -> Kill All Apps (in-game overlay). Hard-stop EVERY third-party app, the
// foreground game included, and drop back to the home launcher. Simply force-stopping
// the running game makes RootWindowContainer relaunch it (its launch state is still
// live -- the user "can still hear the game after Kill All"), so clear the relaunch
// triggers and become the wallpaper launcher FIRST -- exactly as overlayQuitToHome
// does for a clean quit -- then force-stop everything. NOTE: no pending_exit here; in
// overlay-home mode that can spawn a DRM-home nano that fights the SF overlay launcher.
void NanoMenu::overlayKillAll() {
    mOverlayWallpaper = true;
    overlayApplyPresentMode();   // mode flip without hide+show: opaque + vsync lock
    property_set("sys.gammaos.nano.app_launched", "0");
    property_set("sys.gammaos.nano.launch_app", "");
    property_set("sys.gammaos.nano.return_apps", "0");
    property_set("persist.gammaos.nano.qr_prepared", "0");
    property_set("persist.gammaos.nano.qr_core", "");
    mOverlayPausedPkg.clear();
    unlink(kOverlayFrozenMarker);
    quickKillApps(true);   // force-stop ALL incl the game; app_launched=0 blocks the relaunch
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

// In-game (overlay) URL launch: start the chosen browser directly ON the URL with an
// explicit ACTION_VIEW intent, instead of the plain LAUNCHER start overlayLaunchPackage
// does (which would drop the URL and open the browser on its home page). Works for any
// browser; GammaBrowser additionally reads sys.gammaos.nano.browser_url, already set by
// launchUrl before this is called.
bool NanoMenu::overlayLaunchUrl(const std::string& pkg, const std::string& comp,
                                const std::string& url) {
    if (pkg.empty() || url.empty()) return false;
    std::string cmd = "am start -a android.intent.action.VIEW -d " + overlayShq(url);
    if (!comp.empty()) cmd += " -n " + overlayShq(comp);
    else               cmd += " " + overlayShq(pkg);
    cmd += " 2>/dev/null";
    overlayLaunchCommand(pkg, cmd);
    return true;
}

// OSK-over-app bridge. An app that needs text entry (GammaBrowser web fields)
// cannot rely on the framework leanback IME here: on this 1GB low-ram device it is
// OOM-killed the instant it cold-starts under a heavy WebView. Instead the app asks
// nano to host its own lightweight OSK over the live app:
//   app  writes the current field text to <filesDir>/nano_osk_in.txt
//   app  setprop sys.gammaos.nano.osk_dir  <filesDir>
//   app  setprop sys.gammaos.nano.osk_type text|password
//   app  setprop sys.gammaos.nano.osk_req  <id>     (triggers us)
//   we   raise the overlay (drop_input=1) in OSK-only mode, prefilled
//   commit -> write <filesDir>/nano_osk_out.txt, setprop osk_done ok:<id>, dismiss
//   cancel -> setprop osk_done cancel:<id>, dismiss
// Text travels through a file (props cap at 91 bytes; URLs/fields exceed that).
// Live typing: stream the current OSK buffer to the requesting app so the field
// fills as the user types, instead of only on commit. Writes the plain buffer to
// <dir>/nano_osk_live.txt (atomic temp+rename so the reader never sees a partial
// file) and bumps sys.gammaos.nano.osk_gen=<id>:<n>, which the app polls. Only runs
// during an app-hosted OSK session.
void NanoMenu::oskPublishLive() {
    if (!mOskOverApp || mOskAppDir.empty()) return;
    std::string p = mOskAppDir + "/nano_osk_live.txt";
    std::string tmp = p + ".tmp";
    FILE* f = fopen(tmp.c_str(), "wb");
    if (!f) return;
    if (!mOskQuery.empty()) fwrite(mOskQuery.data(), 1, mOskQuery.size(), f);
    fflush(f);
    int fd = fileno(f);
    if (fd >= 0) fsync(fd);          // buffer must be on disk before the gen bump
    fclose(f);
    chmod(tmp.c_str(), 0644);
    if (rename(tmp.c_str(), p.c_str()) != 0) { unlink(tmp.c_str()); return; }
    char v[80];
    snprintf(v, sizeof v, "%s:%u", mOskAppReqId.c_str(), ++mOskAppGen);
    property_set("sys.gammaos.nano.osk_gen", v);
}

void NanoMenu::overlayOskPoll() {
    if (mOskOverApp) {
        // A commit clears mOskOverApp inside its callback below; reaching here with
        // it still set once the OSK has fully closed means the user backed out.
        if (!mOskActive && !mOsk.closing) {
            std::string id = mOskAppReqId;
            std::string dir = mOskAppDir;
            mOskOverApp = false;
            mOskAppReqId.clear();
            property_set("sys.gammaos.nano.osk_gen", "");
            if (!dir.empty()) unlink((dir + "/nano_osk_live.txt").c_str());
            property_set("sys.gammaos.nano.osk_done", ("cancel:" + id).c_str());
            property_set("sys.gammaos.nano.show_overlay", "0");
            overlayHide();
        } else if (mOskQuery != mOskAppLastBuf) {
            mOskAppLastBuf = mOskQuery;   // stream each edit to the app for live typing
            oskPublishLive();
        }
        return;   // one OSK session at a time
    }
    if (mOverlayLaunchPending) return;   // never pop the OSK mid app-launch

    char req[PROPERTY_VALUE_MAX] = {};
    if (property_get("sys.gammaos.nano.osk_req", req, "") <= 0 || !req[0]) return;
    property_set("sys.gammaos.nano.osk_req", "");   // consume the edge

    char dir[PROPERTY_VALUE_MAX] = {}, type[PROPERTY_VALUE_MAX] = {};
    property_get("sys.gammaos.nano.osk_dir", dir, "");
    property_get("sys.gammaos.nano.osk_type", type, "text");
    if (!dir[0]) return;
    mOskAppDir = dir;
    mOskAppReqId = req;

    std::string pre;
    {
        std::string p = mOskAppDir + "/nano_osk_in.txt";
        FILE* f = fopen(p.c_str(), "rb");
        if (f) {
            char buf[8192]; size_t n;
            while ((n = fread(buf, 1, sizeof buf, f)) > 0) pre.append(buf, n);
            fclose(f);
        }
    }
    bool masked = (strcmp(type, "password") == 0);

    mOskOverApp = true;
    if (!mOverlayShown) {
        property_set("sys.gammaos.nano.show_overlay", "1");
        overlayShow();
    }
    std::string id = mOskAppReqId;
    std::string outDir = mOskAppDir;
    openOskForPassword("", [this, id, outDir](const std::string& val) {
        std::string p = outDir + "/nano_osk_out.txt";
        FILE* f = fopen(p.c_str(), "wb");
        if (f) { fwrite(val.data(), 1, val.size(), f); fclose(f); chmod(p.c_str(), 0644); }
        mOskOverApp = false;
        mOskAppReqId.clear();
        property_set("sys.gammaos.nano.osk_gen", "");
        unlink((outDir + "/nano_osk_live.txt").c_str());
        property_set("sys.gammaos.nano.osk_done", ("ok:" + id).c_str());
        property_set("sys.gammaos.nano.show_overlay", "0");
        overlayHide();
    });
    mOskPlaintext = !masked;            // show typed text for normal fields, mask passwords
    mOskQuery = pre;                    // prefill AFTER openOskForPassword (it clears the query)
    mOsk.caret = (int)mOskQuery.size();
    // Live-typing baseline: the prefill equals the field's current value, so seed the
    // change-gate with it (first publish fires on the first real edit) and reset the gen.
    mOskAppGen = 0;
    mOskAppLastBuf = mOskQuery;
    property_set("sys.gammaos.nano.osk_gen", "");
}

void NanoMenu::overlayPoll() {
    overlayOskPoll();
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
        overlayShow();   // snaps to the Quick Menu by default in scrim-over-app mode
    } else if (!want && mOverlayShown) {
        // Do NOT lower the overlay while a standalone clock summon is still retracting:
        // let drawPspClock's teardown clear show_overlay once the clock reaches reveal 0,
        // so the retract (glyphs implode + disc rise) plays fully instead of the layer
        // snapping away mid-animation. (Belt-and-braces: on the normal path the framework
        // leaves show_overlay=1 through the retract, so want stays true and we never reach here.)
        if (!(mPspClockStandalone && mPspClockReveal > 0.0f)) {
            overlayHide();
        }
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
    std::string romPath, romDir, coreSo, launchPkg, launchIntent, romName;
    bool standalone;
    bool fromRecent = false;
    if (mXmbSystemIndex == -1) {                  // Recently Played
        if (mXmbGameIndex < 0 || mXmbGameIndex >= (int)mXmbRecent.size()) return;
        const XmbRecentEntry& re = mXmbRecent[mXmbGameIndex];
        romPath = re.romPath; romDir = re.romDir; coreSo = re.coreSo;
        launchPkg = re.launchPkg; launchIntent = re.launchIntent; standalone = re.standalone;
        romName = re.displayName; fromRecent = true;
    } else {                                      // per-system ROM list
        if (mXmbSystemIndex < 0 || mXmbSystemIndex >= (int)mXmbSystems.size()) return;
        const XmbSystem& sys = mXmbSystems[mXmbSystemIndex];
        if (mXmbGameIndex < 0 || mXmbGameIndex >= (int)sys.roms.size()) return;
        romPath = sys.roms[mXmbGameIndex]; romDir = sys.romDir; coreSo = sys.coreSo;
        launchPkg = sys.launchPkg; launchIntent = sys.launchIntent; standalone = sys.isStandalone();
        if (mXmbGameIndex < (int)sys.displayNames.size())
            romName = sys.displayNames[mXmbGameIndex];
    }
    if (romPath.empty()) return;
    // Same check as the home launcher: a game whose file is gone (deleted, or its card is not
    // mounted) must say so instead of replacing the running app with an emulator that then dies.
    if (!romLaunchExists(romPath)) {
        showRomMissingMsg(romName);
        // Prune the row only when its folder is reachable (genuinely deleted); an offline share/card
        // keeps the row so a briefly-down NAS never wipes valid Recently Played history.
        if (fromRecent && romParentDirReachable(romPath)) recentRemoveAt(mXmbGameIndex);
        return;
    }

    // GammaOS: drastic-nano intercept for the resident overlay launcher. The
    // home XMB reroutes a DS ROM to the drastic-nano binary when
    // persist.gammaos.nano.drastic_nano=1; do the same here so launching a DS
    // ROM while the overlay is the launcher (overlay-home mode) does not fall
    // back to the stock DraStic APK. backend=drm/auto takes the DRM handshake:
    // drastic-nano grabs the panel, and the start trigger stops both the home
    // and this overlay. The dual-display RG DS uses exactly this DRM path,
    // unchanged. (SF mode is launched through its own host-activity path.)
    char dnGate[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.drastic_nano", dnGate, "0");
    char dnBackend[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.drastic_nano.backend", dnBackend, "auto");
    if (standalone && launchPkg == "com.dsemu.drastic" && dnGate[0] == '1') {
        setDrasticNanoRomPath(romPath);
        // Keep Quick Resume's boot preview in sync with the game launched here. The
        // preview shows qr_game_name and, when storage is slow to mount, falls back
        // to the single .nds staged in the drastic cache. QR is armed later (at
        // power-off in prepareShutdown), not here, so if we launch a DS game from
        // the overlay without refreshing these the resume would preview the PREVIOUS
        // game. Refresh the display name and re-stage the cache for THIS ROM now (an
        // async copy that runs during gameplay), regardless of the QR toggle so a
        // mid-game toggle-on is covered too. Mirrors the XMB launch path.
        {
            std::string gameName = romPath;
            size_t ls = gameName.rfind('/');
            if (ls != std::string::npos) gameName = gameName.substr(ls + 1);
            size_t dot = gameName.rfind('.');
            if (dot != std::string::npos) gameName.erase(dot);
            property_set("persist.gammaos.nano.qr_game_name", gameName.c_str());
            // Point qr_core at drastic now (the resume is armed at power-off, but
            // the flag must not stay at a prior RetroArch core, or an abrupt reboot
            // during this DS session would resume the wrong game on the wrong
            // runtime). The boot dispatch reads qr_core to choose the DS preview vs
            // the RetroArch relaunch.
            property_set("persist.gammaos.nano.qr_core", "drastic");
            property_set("sys.gammaos.nano.cache_ready", "0");
            property_set("sys.gammaos.nano.cache_op", "populate_drastic");
        }
        if (strcmp(dnBackend, "sf") == 0) {
            // SurfaceFlinger mode: launch the DrasticSf host activity through the
            // SAME overlay launch path as any other app (overlayLaunchCommand).
            // That is what dismisses the XMB onto the new app (overlayPoll), sets
            // app_launched, and runs the clean handoff -- the XMB must not be
            // treated specially here. The overlay stays running as the SF panel
            // keeper (the activity composites on top of it) and the activity starts
            // the drastic-nano binary itself (start_sf from onResume); we never
            // touch DRM master and never _exit.
            ALOGW("drastic nano: overlay launch -> drastic-nano (SF host)");
            overlayLaunchCommand("com.gammaos.drasticsf",
                "am start -W -n com.gammaos.drasticsf/.DrasticSfActivity "
                "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER "
                "--activity-clear-task 2>/dev/null");
            return;
        }
        // DRM mode: do not let the overlay respawn after we exit -- drastic-nano
        // owns the panel now. Clear the QR prime so the home comes back plain.
        // The dual-display RG DS uses this DRM path, unchanged.
        //
        // Fade the overlay XMB to black first, exactly like a normal overlay game
        // launch. overlayLaunchCommand() (the normal path) sets mOverlayLaunchPending
        // so render() draws the ~300ms fade-to-black (the overlay launch transition
        // in NanoMenuRender). This branch used to _exit immediately -- a hard cut
        // with no fade while every other game/app faded. Drive that same fade inline
        // on the render thread (we are called from the input handler on it), then
        // hand the panel to drastic-nano. mOverlayLaunchPending also freezes input.
        mOverlayLaunchStartMs = uptimeMillis();
        mOverlayLaunchPending = true;
        while ((int64_t)uptimeMillis() - mOverlayLaunchStartMs < 300) {
            render();
            mRenderHeartbeat.fetch_add(1, std::memory_order_relaxed);
            usleep(16666);
        }
        property_set("sys.gammaos.nano.overlay_ran", "0");
        property_set("sys.gammaos.nano.app_launched", "0");
        property_set("sys.gammaos.nano.show_overlay", "0");
        property_set("persist.gammaos.nano.qr_prepared", "0");
        ALOGW("drastic nano: overlay launch -> drastic-nano (DRM)");
        property_set("sys.gammaos.drastic_nano.start", "1");
        _exit(0);
    }

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
    // Quick Resume prime for the overlay-launched game, mirroring the home XMB
    // launch (launchXmbGame). On the Brick the resident overlay IS the home after
    // the first game, so every launch after that comes through here -- and without
    // the same prime a RetroArch launch leaves qr_core stale (for example "drastic"
    // from a prior DS game). The boot resume dispatches on qr_core (== "drastic"
    // takes the in-process DS preview, otherwise the framework relaunches RetroArch
    // with that core), so a stale qr_core makes the next Restart resume the WRONG
    // game on the WRONG runtime. The standalone drastic ROM already returned above
    // via the drastic-nano intercept, so this only covers RetroArch cores and other
    // standalone apps (PPSSPP, etc., which have no Quick Resume).
    if (!standalone && mQuickResumeEnabled) {
        std::string corePath =
                "/data/data/com.retroarch.aarch64/cores/" + coreSo;
        setQrRomPath(romPath);
        property_set("persist.gammaos.nano.qr_core", corePath.c_str());
        property_set("persist.gammaos.nano.qr_prepared", "1");
        std::string gameName = romPath;
        size_t ls = gameName.rfind('/');
        if (ls != std::string::npos) gameName = gameName.substr(ls + 1);
        size_t dot = gameName.rfind('.');
        if (dot != std::string::npos) gameName.erase(dot);
        property_set("persist.gammaos.nano.qr_game_name", gameName.c_str());
        // Stage the DE cache for the QR preview, mirroring the home-XMB launch
        // (launchXmbGame, NanoMenuXmb.cpp:~1893). On a force-SF / overlay-home
        // device (e.g. Unisoc, where nano composites via a SurfaceFlinger layer)
        // the resident overlay IS the home after the first game, so every launch
        // after that comes through here; without this the DE cache is never warmed
        // and the next Quick Resume has no live preview (cache miss). do_populate
        // resolves the ROM from nano_qr_rom.txt (setQrRomPath above) and the core
        // from qr_core; the edge-trigger reset in nano_cache.sh makes the populate
        // re-fire even if cache_op already held "populate".
        property_set("sys.gammaos.nano.cache_ready", "0");
        property_set("sys.gammaos.nano.cache_op", "populate");
        ALOGI("overlay: primed Quick Resume (qr_core=%s, rom=%s)",
              corePath.c_str(), romPath.c_str());
    } else {
        // RetroArch with QR off, or a non-drastic standalone app: clear any stale
        // prime so the next boot does not resume an unrelated game.
        property_set("persist.gammaos.nano.qr_prepared", "0");
        property_set("persist.gammaos.nano.qr_core", "");
    }

    ALOGI("overlay: launch game pkg=%s standalone=%d rom=%s",
          pkg.c_str(), standalone ? 1 : 0, romPath.c_str());
    overlayLaunchCommand(pkg, cmd);
}

// ===================== Orientation control =====================
//
// Nano publishes a single foreground-aware orientation token to
// sys.gammaos.nano.force_orientation, which WindowManagerService reads in
// mapOrientationRequest (only while auto-rotation is off, which is nano's
// default). The token is: nano's own Screen Orientation setting when the nano
// menu or its overlay is foreground (default "landscape", so an app like
// Firefox cannot rotate the XMB to portrait), a per-app override when a normal
// app with one is foreground, or "none" (honor the app's own request) otherwise.

static int64_t nanoAppOrientMtime() {
    struct stat st;
    if (stat("/data/system/nano_app_orient.json", &st) != 0) return -1;
    return (int64_t)st.st_mtim.tv_sec * 1000000000LL + st.st_mtim.tv_nsec;
}

void NanoMenu::appOrientLoad() {
    mAppOrientLoaded = true;
    mAppOrient.clear();
    mAppOrientStamp = nanoAppOrientMtime();
    int fd = open("/data/system/nano_app_orient.json", O_RDONLY);
    if (fd < 0) return;
    std::string content;
    struct stat st;
    if (fstat(fd, &st) == 0 && st.st_size > 0 && st.st_size < 1 * 1024 * 1024) {
        char buf[4096];
        ssize_t n;
        while ((n = read(fd, buf, sizeof(buf))) > 0) content.append(buf, (size_t)n);
    }
    close(fd);
    njson::Value root;
    if (!njson::parse(content, &root) || !root.isObject()) return;
    if (const njson::Value* apps = root.find("apps"); apps && apps->isObject())
        for (const auto& kv : apps->obj)
            if (kv.second.isString() && !kv.second.str.empty())
                mAppOrient[kv.first] = kv.second.str;
}

void NanoMenu::appOrientSave() {
    njson::Value root = njson::Value::makeObject();
    root.set("version") = njson::Value::makeNumber(1);
    njson::Value apps = njson::Value::makeObject();
    for (const auto& kv : mAppOrient) apps.set(kv.first) = njson::Value::makeString(kv.second);
    root.set("apps") = std::move(apps);
    std::string text = njson::serialize(root, true);

    const char* path = "/data/system/nano_app_orient.json";
    const char* tmp = "/data/system/nano_app_orient.json.tmp";
    int fd = open(tmp, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd < 0) { ALOGW("nano: cannot write %s (errno %d)", tmp, errno); return; }
    size_t off = 0; bool ok = true;
    while (off < text.size()) {
        ssize_t w = write(fd, text.c_str() + off, text.size() - off);
        if (w <= 0) { ok = false; break; }
        off += (size_t)w;
    }
    fsync(fd); close(fd);
    if (!ok) { unlink(tmp); return; }
    if (rename(tmp, path) != 0) { unlink(tmp); return; }
    (void)chown(path, 0, 0);
    (void)chmod(path, 0644);
    mAppOrientStamp = nanoAppOrientMtime();
}

std::string NanoMenu::appOrientGet(const std::string& pkg) {
    if (pkg.empty()) return std::string();
    // Reload if another nano process (the home menu sets the override, the
    // overlay enforces it) rewrote the file since we last read it.
    if (!mAppOrientLoaded || nanoAppOrientMtime() != mAppOrientStamp) appOrientLoad();
    auto it = mAppOrient.find(pkg);
    return it == mAppOrient.end() ? std::string() : it->second;
}

void NanoMenu::appOrientSet(const std::string& pkg, const std::string& value) {
    if (pkg.empty()) return;
    if (!mAppOrientLoaded) appOrientLoad();
    if (value.empty() || value == "default") mAppOrient.erase(pkg);
    else                                     mAppOrient[pkg] = value;
    appOrientSave();
    mLastOrientToken.clear();   // re-publish on the next orientationTick
}

void NanoMenu::overlayUpdateSurfaceSize() {
    if (!mOverlayMode || mDisplayToken == nullptr || mFlingerSurface == nullptr) return;
    ui::DisplayState state;
    if (SurfaceComposerClient::getDisplayState(mDisplayToken, &state) != NO_ERROR) return;
    // Cache the rotation BEFORE the size-unchanged early-return below: touchMapRaw
    // reads mOverlayRotation to follow a forced-portrait rotation over a landscape
    // panel. (A 90<->270 flip keeps the logical size but still rotates the axes.)
    mOverlayRotation = (int)state.orientation;
    // GammaOS hardware rotation key: on a swivel device the physical panel rotation is authoritative.
    // SurfaceFlinger's reported display orientation is NOT reliable here: on a square panel it reads
    // 0 on a clean boot, but after an app has driven the WMS logical rotation to 90 and exited it can
    // report 1 - and composing the rotate prop ON TOP of that double-rotated the overlay (the "works
    // on clean boot, breaks after app launch/exit" bug). So when the rotate feature is enabled we set
    // the overlay rotation SOLELY from sys.gammaos.rotate.state (the same signal
    // PhoneWindowManager/DisplayRotation use), ignoring state.orientation entirely. Both the render
    // matrix and touchMapRaw key off mOverlayRotation, so render and touch stay consistent.
    int renderSelfRot = mOverlayRotation;   // default: match SF orientation (non-rotate devices)
    if (property_get_bool("persist.gammaos.rotate.enabled", false)) {
        // Physical panel rotation from the authoritative rotate state prop.
        int physical = 0;
        if (property_get_int32("sys.gammaos.rotate.state", 0) == 1) {
            const int deg = property_get_int32("persist.gammaos.rotate.degrees", 90);
            physical = (deg == 270) ? 3 : (deg == 180) ? 2 : 1;
        }
        // Touch un-rotation follows the FULL physical rotation: nano reads raw evdev touch in panel
        // coordinates and its content appears at the physical rotation on screen.
        mOverlayRotation = physical;
        // Render self-rotation must COMPENSATE for what SurfaceFlinger already applies. On the nano
        // home (no app) SF keeps the display at orientation 0, so nano self-rotates the full amount.
        // Over a rotated app SF applies its own display transform (state.orientation != 0) to every
        // layer including nano's, so nano must self-rotate only the DIFFERENCE, or it double-rotates
        // and the overlay goes blank/upside-down (the "overlay over the app is blank at 90" bug).
        renderSelfRot = (physical - (int)state.orientation) & 3;
    }
    // Turn nano's own rendering to match, compensating for any SurfaceFlinger display transform.
    nanoSetOverlayRenderRotation(renderSelfRot);
    // Instrumentation for the seamless-rotation work: log only on a change so the sequence during a
    // rotate is readable (physical from the rotate prop, SF orientation from getDisplayState, and the
    // resulting self-rotation nano applies). With SF pinned at 0 while the overlay is up this should
    // read sf=0 and renderSelfRot==physical, matching the seamless cold-boot path.
    {
        static int sLastLoggedRot = -100;
        const int packed = (mOverlayRotation << 8) | ((int)state.orientation << 4) | (renderSelfRot & 0xf);
        if (packed != sLastLoggedRot) {
            sLastLoggedRot = packed;
            ALOGI("nano rotate: physical=%d sf_orient=%d renderSelfRot=%d (lw=%d lh=%d)",
                  mOverlayRotation, (int)state.orientation, renderSelfRot,
                  (int)state.layerStackSpaceRect.getWidth(),
                  (int)state.layerStackSpaceRect.getHeight());
        }
    }
    const int lw = (int)state.layerStackSpaceRect.getWidth();
    const int lh = (int)state.layerStackSpaceRect.getHeight();
    if (lw <= 0 || lh <= 0) return;
    if (lw == mWidth && lh == mHeight) return;   // display logical size unchanged
    // The display's logical size changed (a rotation, e.g. nano forced portrait).
    // We must land a producer buffer of the NEW size so SurfaceFlinger composites
    // us over the whole rotated display. setBuffersDimensions alone is not enough:
    // the GLES driver caches the window geometry at eglCreateWindowSurface time and
    // re-asserts it on every dequeue, so it clobbers a bare setBuffersDimensions and
    // keeps handing out landscape (1920x1080) buffers. SF then crops that square and
    // parks it in a corner of the portrait panel (observed on RK3576: source_crop
    // 840,0,1920,1080 -> display_frame 0,840,1080,1920). Recreate the EGL window
    // surface after resizing the ANativeWindow so the driver re-reads the new
    // dimensions and allocates portrait (1080x1920) buffers that fill the display.
    // A native window can host only ONE EGLSurface at a time, so the old surface
    // must be torn down before the new one is created: creating first fails with
    // EGL_BAD_ALLOC (the window is still connected to the old producer). Unbind the
    // context, destroy the old surface, resize the ANativeWindow, then recreate.
    const int prevW = mWidth, prevH = mHeight;
    EGLConfig config = getEglConfig(mDisplay, mOverlayMode);
    eglMakeCurrent(mDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    if (mSurface != EGL_NO_SURFACE) {
        eglDestroySurface(mDisplay, mSurface);
        mSurface = EGL_NO_SURFACE;
    }
    // Resize the SurfaceControl's BLASTBufferQueue to the new size. This is the
    // consumer (SurfaceFlinger) side: the layer's queue was created at the surface
    // creation size (1920x1080), and SF only latches buffers that match the queue
    // size. Without this, a portrait (1080x1920) buffer is produced but never
    // latched, so SF keeps compositing the last landscape buffer - the menu renders
    // and swaps but the panel is frozen on a stale frame (landscape-native sizes
    // happen to match, so only portrait wedged). updateDefaultBufferSize forwards to
    // BLASTBufferQueue::update so producer and consumer agree on the new geometry.
    if (mFlingerSurfaceControl != nullptr)
        mFlingerSurfaceControl->updateDefaultBufferSize((uint32_t)lw, (uint32_t)lh);
    // Use USER dimensions, not buffer (request) dimensions: eglCreateWindowSurface
    // sizes the surface from NATIVE_WINDOW_DEFAULT_WIDTH/HEIGHT, which reads the
    // user dimensions and otherwise falls back to the SurfaceControl's creation
    // size (1920x1080). setBuffersDimensions only sets the request size, so the
    // driver kept making landscape surfaces. User dimensions also drive dequeue,
    // so the whole pipeline (EGL surface + buffers) matches the portrait display.
    native_window_set_buffers_user_dimensions(mFlingerSurface.get(), lw, lh);
    EGLSurface ns = eglCreateWindowSurface(mDisplay, config, mFlingerSurface.get(), nullptr);
    if (ns == EGL_NO_SURFACE) {
        // Fall back to a surface at the previous size so rendering keeps going.
        ALOGE("nano overlay: eglCreateWindowSurface failed at %dx%d (0x%x); "
              "restoring %dx%d", lw, lh, eglGetError(), prevW, prevH);
        if (mFlingerSurfaceControl != nullptr)
            mFlingerSurfaceControl->updateDefaultBufferSize((uint32_t)prevW, (uint32_t)prevH);
        native_window_set_buffers_user_dimensions(mFlingerSurface.get(), prevW, prevH);
        ns = eglCreateWindowSurface(mDisplay, config, mFlingerSurface.get(), nullptr);
        if (ns == EGL_NO_SURFACE) {
            ALOGE("nano overlay: fallback surface create failed (0x%x)", eglGetError());
            return;
        }
        eglMakeCurrent(mDisplay, ns, ns, mContext);
        if (mOverlayMode) eglSwapInterval(mDisplay, 0);
        mSurface = ns;
        return;
    }
    eglMakeCurrent(mDisplay, ns, ns, mContext);
    // Overlay pacing is swap-interval 0 (the threadLoop top-up sleep drives frames),
    // matching the creation path in NanoMenu.cpp.
    if (mOverlayMode) eglSwapInterval(mDisplay, 0);
    mSurface = ns;

    // Trust the actual surface geometry the driver gave us.
    EGLint qw = lw, qh = lh;
    eglQuerySurface(mDisplay, mSurface, EGL_WIDTH, &qw);
    eglQuerySurface(mDisplay, mSurface, EGL_HEIGHT, &qh);
    ALOGI("nano overlay: display logical size %dx%d -> recreated surface %dx%d (was %dx%d)",
          lw, lh, qw, qh, prevW, prevH);
    mWidth = qw; mHeight = qh;
    mDisplayDirty = true;
}

void NanoMenu::orientationTick() {
    // One-shot: seed auto-rotation from the persisted Screen Orientation - "auto" ->
    // sensor ON, any fixed orientation -> OFF (nano forces it via mapOrientationRequest,
    // which only engages when accelerometer_rotation is 0). The device may ship with a
    // different value. Home process only, guarded by a persist prop so it runs once and
    // never fights a later change. Detached thread that retries until the write takes
    // (the settings provider may not be ready on the first tick).
    if (!mOverlayMode &&
        !property_get_bool("persist.gammaos.nano.accel_seeded", false)) {
        property_set("persist.gammaos.nano.accel_seeded", "1");
        std::thread([]() {
            char ob[PROPERTY_VALUE_MAX] = {};
            property_get("persist.gammaos.nano.orientation", ob, "landscape");
            const char want = (std::string(ob) == "auto") ? '1' : '0';
            for (int i = 0; i < 20; i++) {
                char cmd[96];
                snprintf(cmd, sizeof(cmd),
                         "settings put system accelerometer_rotation %c 2>/dev/null", want);
                (void)system(cmd);
                char b[32] = {};
                FILE* f = popen("settings get system accelerometer_rotation 2>/dev/null", "r");
                if (f) { if (fgets(b, sizeof(b), f)) {} pclose(f); }
                if (b[0] == want) break;
                usleep(500000);
            }
        }).detach();
    }

    // One-shot: clear a stale user_rotation left by the previous freeze-based control.
    // Mechanism A never writes user_rotation, but a device upgrading from the old build
    // can carry mUserRotation=270, which would leak into any "none"/UNSPECIFIED path (an
    // app that itself requests no orientation would inherit portrait). Reset to 0 once so
    // natural landscape is the baseline. Own guard prop so it runs once even on devices
    // that already seeded accel, and never fights a legitimate later rotation.
    if (!mOverlayMode &&
        !property_get_bool("persist.gammaos.nano.urot_reset", false)) {
        property_set("persist.gammaos.nano.urot_reset", "1");
        std::thread([]() {
            for (int i = 0; i < 20; i++) {
                (void)system("settings put system user_rotation 0 2>/dev/null");
                char b[32] = {};
                FILE* f = popen("settings get system user_rotation 2>/dev/null", "r");
                if (f) { if (fgets(b, sizeof(b), f)) {} pclose(f); }
                if (b[0] == '0') break;
                usleep(500000);
            }
        }).detach();
    }

    char nb[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.orientation", nb, "landscape");
    std::string nanoSetting = nb[0] ? std::string(nb) : std::string("landscape");

    // Foreground context. nano is what the user sees when it holds the panel as
    // the DRM-direct home (drm_active) or when the overlay is raised over an app
    // (show_overlay). Otherwise an app owns the SurfaceFlinger display: resolve
    // the top resumed package (empty = nano SF home with no app). app_launched is
    // NOT used here - it is unreliable (stays 0/stale across the overlay handoff).
    const bool overlayShown = property_get_bool("sys.gammaos.nano.show_overlay", false);
    const bool drmActive    = property_get_bool("sys.gammaos.nano.drm_active", false);

    // Global force-orientation mode. When persist.gammaos.nano.orient_mode is a force_*
    // value, EVERY foreground app is forced to that orientation and cannot override it -
    // this sits ABOVE the per-app override and the honor-the-app "none" default. The nano
    // menu / overlay itself keeps following its own Screen Orientation (nanoSetting), an
    // independent axis (forcing the XMB home to portrait is the deferred DRM AHB item).
    // Note: an app that cannot render the forced orientation (e.g. a landscape-locked game
    // asked for portrait) will still black-screen - that is inherent to the app, not this.
    char mb[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.orient_mode", mb, "normal");
    std::string mode = mb[0] ? std::string(mb) : std::string("normal");
    std::string forced;
    if      (mode == "force_landscape")     forced = "landscape";
    else if (mode == "force_portrait")      forced = "portrait";
    else if (mode == "force_rev_landscape") forced = "rev_landscape";
    else if (mode == "force_rev_portrait")  forced = "rev_portrait";

    std::string token;
    if (overlayShown || drmActive) {
        token = nanoSetting;                     // nano menu / overlay is foreground
    } else if (!forced.empty()) {
        token = forced;                          // force mode overrides the app entirely
    } else {
        // An app owns SurfaceFlinger. Identify it by the package nano launched -
        // reliable and cheap, unlike the dumpsys foreground-resolver popen which
        // returns empty from the parked overlay's render thread. Empty = fall back
        // to nano's own setting.
        char pb[PROPERTY_VALUE_MAX] = {};
        property_get("sys.gammaos.nano.launched_pkg", pb, "");
        std::string fg = pb;
        if (fg.empty()) {
            token = nanoSetting;
        } else {
            std::string ov = appOrientGet(fg);   // per-app override, else honor the app
            token = ov.empty() ? std::string("none") : ov;
        }
    }

    if (token != mLastOrientToken) {
        mLastOrientToken = token;
        property_set("sys.gammaos.nano.force_orientation", token.c_str());
        ALOGI("nano: force_orientation=%s (overlay=%d drm_active=%d)",
              token.c_str(), overlayShown ? 1 : 0, drmActive ? 1 : 0);
        // Screen Orientation is the single owner of accelerometer_rotation. Resolve it
        // from the RESOLVED token (not just the nano setting) so a per-app "auto"
        // override - which is applied via appOrientSet and bypasses the settings write
        // path - also enables the sensor, and any fixed/none token disables it so the
        // nano force engages. Only shell out when the bit actually changes ("settings
        // put" forks the settings CLI); token changes are infrequent (fg transitions).
        static int sLastAccel = -1;
        const int wantAccel = (token == "auto") ? 1 : 0;
        if (wantAccel != sLastAccel) {
            sLastAccel = wantAccel;
            std::thread([wantAccel]() {
                char cmd[96];
                snprintf(cmd, sizeof(cmd),
                         "settings put system accelerometer_rotation %d 2>/dev/null", wantAccel);
                (void)system(cmd);
            }).detach();
        }
    }
}

} // namespace android
