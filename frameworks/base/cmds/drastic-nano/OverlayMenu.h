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

    // True if the user picked an option that requires drastic to
    // quit and relaunch (e.g. Hi-res toggle). main.cpp polls this
    // and, when set, writes prefs + triggers the auto-relaunch
    // handshake.
    bool relaunchRequested() const { return mRelaunch; }

    // Clear relaunch flag.
    void clearRelaunchRequest() { mRelaunch = false; }

    // Access staged prefs (for final write-on-close).
    const drastic_prefs::Prefs& prefs() const { return mPrefs; }

    // Force-close the menu (used when drastic-nano is shutting down).
    void close();

private:
    enum Section { kSec_Save = 0, kSec_Video, kSec_Audio, kSec_Controls,
                   kSec_COUNT };

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
    Section mSection = kSec_Save;
    int mCursor[kSec_COUNT] = {0, 0, 0, 0};
    int mScroll[kSec_COUNT] = {0, 0, 0, 0};
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

    void openMenu();
    void closeMenu();
    void rebuildRows();
    void rebuildSave();
    void rebuildVideo();
    void rebuildAudio();
    void rebuildControls();
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
