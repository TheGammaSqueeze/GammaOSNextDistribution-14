/*
 * Copyright (C) 2026 GammaOS
 *
 * OverlayMenu: the in-game transparent menu that opens on a short-
 * press of KEY_BACK. Shows four tabbed sections (Save States, Video,
 * Audio, Controls) and lets the user adjust settings without leaving
 * the game.
 *
 * The menu is driven by an InputActions struct produced by InputMap;
 * rendering goes through OverlayGfx.
 */

#pragma once

#include <functional>
#include <string>
#include <sys/types.h>
#include <vector>

#include "DrasticPrefs.h"
#include "DrasticOsk.h"
#include "InputMap.h"
#include "OverlayGfx.h"

namespace android {
class DrasticRunner;
}

namespace android {
namespace drastic_overlay {

class OverlayMenu {
public:
    OverlayMenu();
    ~OverlayMenu();

    // Initialize with a pointer to the long-running DrasticRunner so
    // the menu can fire save/load/reset/volume/shader calls, and the
    // user's current prefs snapshot. appUid/appGid are used when
    // writing XML so the real drastic app UID can read the result.
    // xmlPath is the absolute path to _Dra$t1c_Pref$_.xml.
    // savestatesDir is where .dss files live (for slot-status probes).
    // romPath is the ROM's absolute path (for basename-building).
    // shadersDir is the directory where .dfx files live (for shader
    // picker listing).
    void init(DrasticRunner* runner,
              const drastic_prefs::Prefs& prefs,
              uid_t appUid, gid_t appGid,
              std::string xmlPath,
              std::string savestatesDir,
              std::string romPath,
              std::string shadersDir);

    bool isOpen() const { return mOpen; }
    // True when the menu wants the input layer to route the next key
    // press to capturedAndroidKc (Controls > rebind).
    bool isCapturingKey() const;

    // Per-frame tick. Apply actions, update state, perform side-
    // effects (save/load/reset/volume/shader/restart).
    void update(const drastic_input::InputActions& a,
                drastic_input::InputState* input);

    // Render the overlay on top of whatever is in the current FBO.
    // Caller should have already rendered drastic's DS screens.
    void draw(drastic_gfx::OverlayGfx& gfx);

    // Render ONLY the on-screen keyboard (with a scrim) onto the current FBO.
    // main.cpp binds the secondary (bottom DS) FBO and calls this so the
    // keyboard appears on the bottom screen, not over the cheats menu. No-op
    // when the keyboard is inactive.
    void drawOsk(drastic_gfx::OverlayGfx& gfx);

    // True while the on-screen keyboard is up (drives the bottom-screen pass).
    bool oskActive() const { return mOsk.active(); }

    // Called from the main loop on volume-key presses: VOL = volume,
    // SELECT+VOL = brightness. Adjusts the level and shows the slider HUD.
    void onVolumeAdjust(int dir)     { adjustVolume(dir); }
    void onBrightnessAdjust(int dir) { adjustBrightness(dir); }

    // True if the user picked an option that requires drastic to
    // quit and relaunch (e.g. Hi-res toggle). main.cpp polls this
    // and, when set, writes prefs + triggers the auto-relaunch
    // handshake.
    bool relaunchRequested() const { return mRelaunch; }

    // Clear relaunch flag.
    void clearRelaunchRequest() { mRelaunch = false; }

    // True when the user picked the "Exit Game" row. main.cpp polls
    // this and breaks the run loop, running the same graceful teardown
    // (autosave -> session_done -> XMB) as a back-button hold.
    bool exitAppRequested() const { return mExitApp; }

    // True when the user picked the "Restart Game" row. drastic's
    // in-process soft reset (resetDS) cannot run because the boot-race
    // longjmp patch neuters the reset's loop-restart, so a real restart
    // is a fresh relaunch: main.cpp skips the slot-9 autosave, sets the
    // boot_fresh prop, and triggers the relaunch handshake so the ROM
    // reboots from the title.
    bool restartFreshRequested() const { return mRestartFresh; }

    // Access staged prefs (for final write-on-close).
    const drastic_prefs::Prefs& prefs() const { return mPrefs; }

    // Force-close the menu (used when drastic-nano is shutting down).
    void close();

private:
    enum Section { kSec_Save = 0, kSec_Video, kSec_Audio, kSec_Controls,
                   kSec_Cheats, kSec_COUNT };
    enum class NavDir { None, Up, Down, Left, Right };

    struct RowAction {
        std::string label;
        std::string value;  // right-aligned value string (may be empty)
        // Handler invoked on Accept (A). Optional.
        std::function<void()> onAccept;
        // Left/Right handler for adjustable fields. Optional.
        std::function<void(int dir)> onAdjust;
    };

    DrasticRunner* mRunner = nullptr;
    drastic_prefs::Prefs mPrefs;      // staged / live prefs
    drastic_prefs::Prefs mSavedPrefs; // snapshot at open, for restart check
    uid_t mAppUid = 0;
    gid_t mAppGid = 0;
    std::string mXmlPath;
    std::string mSavestatesDir;
    std::string mRomBase;             // basename of the ROM, no ext
    std::string mShadersDir;

    bool mOpen = false;
    bool mRelaunch = false;
    bool mExitApp = false;            // "Exit Game" row selected
    bool mRestartFresh = false;       // "Restart Game" row selected
    Section mSection = kSec_Save;
    int mCursor[kSec_COUNT] = {0, 0, 0, 0, 0};
    int mScroll[kSec_COUNT] = {0, 0, 0, 0, 0};
    bool mCaptureKey = false;
    int  mCaptureActionIdx = -1;      // when mCaptureKey: which action
    bool mDirty = false;              // staged edits pending write
    int64_t mToastUntilMs = 0;
    std::string mToast;

    // Shader files scanned from shadersDir.
    std::vector<std::string> mShaders;

    // Battery indicator state (refreshed lazily while the menu is open).
    int     mBatteryPercent = -1;   // -1 until first successful read
    bool    mBatteryCharging = false;
    int64_t mBatteryNextPollMs = 0; // elapsedRealtime() of next refresh

    // Cached per-section row lists. Rebuilt when state changes.
    std::vector<RowAction> mRows;

    // Cheats model. Built once per overlay-open from the running ROM's
    // cheats (invalidated on open and after custom add/remove) so per-input
    // rebuilds only re-read the cheap enabled bytes, not the byte[] names.
    struct CheatFolder {
        std::string name;
        bool multiSelect = true;
        std::vector<int> children;             // global cheat indices
        std::vector<std::string> childNames;   // parallel to children
    };
    std::vector<CheatFolder> mCheatFolders;
    std::vector<std::string> mCustomCheatNames;  // cached, parallel to index
    bool mCheatModelValid = false;
    bool mCheatsDirty = false;   // a cheat enable changed; flush on close

    // Hold-to-repeat scroll state (see navPress/tickNavRepeat).
    NavDir  mNavHeldDir = NavDir::None;
    int64_t mNavLastRepeatMs = 0;
    int     mNavRepeatCount = 0;

    // On-screen keyboard for cheat search / custom-cheat entry. When
    // active, update() routes nav to it and drawOsk() paints it on the
    // bottom DS screen.
    DrasticOsk mOsk;
    // Bottom-screen touch -> OSK key. mPrevOskTouch tracks the previous-frame
    // real-finger state for tap-edge detection; the flip flags are device
    // tuning knobs (read once) in case an axis comes in inverted.
    bool mPrevOskTouch = false;
    bool mOskTouchInit = false;
    bool mOskTouchFlipX = false;
    bool mOskTouchFlipY = false;

    // In-app volume / brightness slider HUDs (ported from the Nano home),
    // shown on the volume keys since the SF system HUDs never appear on the
    // DRM-direct path. Render every frame (even with the menu closed) and
    // auto-hide after ~1.5s.
    int mBrightLevel = 128;     // 0-255 (Android range)
    int mVolHudTimer = 0;       // frames remaining (60fps)
    int mBrightHudTimer = 0;
    bool mBrightInit = false;
    // Cheat search filter (lowercased substring; empty = no filter).
    std::string mCheatFilter;
    // Show filter: 0 = all, 1 = enabled only, 2 = disabled only. Lets the
    // user quickly see which cheats are already on.
    int mCheatShow = 0;

    void openMenu();
    void closeMenu();
    void rebuildRows();
    void rebuildSave();
    void rebuildVideo();
    void rebuildAudio();
    void rebuildControls();
    void rebuildCheats();

    // Hold-to-repeat navigation, ported from the PS3 XMB (NanoMenu
    // navPress/navRelease/tickNavRepeat): holding a dpad direction scrolls
    // continuously on an accelerating cadence so long lists (cheats) are
    // easy to traverse. navPress fires one step immediately (a tap still
    // moves one slot); tickNavRepeat (every frame) fires the rest.
    void navPress(NavDir dir);
    void navRelease();
    void tickNavRepeat();
    void fireNav(NavDir dir);
    void handleNavUp();
    void handleNavDown();
    void adjustCurrent(int dir);   // Left/Right onAdjust on the cursor row
    // Enumerate the running ROM's cheats from drastic (folders + flat list
    // grouped by folderId, plus a synthetic "Assorted" group), caching
    // names so per-input rebuilds only re-read the cheap enabled bytes.
    void buildCheatModel();
    void toggleCheat(int globalIdx, int folderModelIdx);
    bool cheatMatchesFilter(const std::string& name) const;
    void openCheatSearch();
    void addCustomCheatFlow();   // chained name -> hex OSK -> addCustomCheat

    // Volume/brightness HUD (in-app slider notifications).
    void adjustVolume(int dir);
    void adjustBrightness(int dir);
    void drawHud(drastic_gfx::OverlayGfx& gfx);  // volume + brightness bars
    // Push the current prefs to the running emulator with no relaunch
    // (rebuilds the config word and calls DrasticRunner::applyVideoConfigLive).
    void applyConfigLive();
    void scanShaders();
    bool slotFileExists(int slot) const;
    void toast(const std::string& msg, int64_t ms = 1500);
    void writePrefsSafe();
    void commitAndMaybeRelaunch();

    // UI helpers. The overlay paints over the full screen with a light
    // scrim, styled after gammaos-nano's XMB: no bounded panel, a
    // horizontal category row at the top with the active section scaled
    // up and the rest dimmed, a vertical list below, and a footer hint
    // strip along the bottom.
    void drawCategoryBar(drastic_gfx::OverlayGfx& gfx, float vw,
                         float barY, float sf);
    void drawList(drastic_gfx::OverlayGfx& gfx, float vw, float listY,
                  float listH, float sf);
    void drawFooter(drastic_gfx::OverlayGfx& gfx, float vw, float vh,
                    float sf);

    // Battery HUD. refreshBattery() polls the IHealth HAL (or sysfs) at
    // most once per second; drawBatteryIndicator() renders into the
    // top-right of the screen.
    void refreshBattery();
    void drawBatteryIndicator(drastic_gfx::OverlayGfx& gfx, float vw,
                              float sf);

    // Wall-clock HH:MM HUD in the top-right, mirroring the battery HUD.
    void drawTimeIndicator(drastic_gfx::OverlayGfx& gfx, float vw,
                           float sf);
};

} // namespace drastic_overlay
} // namespace android
