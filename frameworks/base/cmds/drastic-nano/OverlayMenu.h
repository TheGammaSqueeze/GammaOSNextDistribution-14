/*
 * Copyright (C) 2026 GammaOS
 *
 * OverlayMenu: the in-game transparent menu that opens on a short-
 * press of KEY_BACK. Shows six tabbed sections (Save States, Video,
 * Audio, Controls, Cheats, Achievements) and lets the user adjust
 * settings without leaving the game. The Achievements section hosts
 * RetroAchievements login and the achievement list.
 *
 * The menu is driven by an InputActions struct produced by InputMap;
 * rendering goes through OverlayGfx.
 */

#pragma once

#include <deque>
#include <functional>
#include <map>
#include <string>
#include <sys/types.h>
#include <vector>

#include "DrasticPrefs.h"
#include "DrasticOsk.h"
#include "InputMap.h"
#include "OverlayGfx.h"

namespace android {
class DrasticRunner;
class NanoRetroAchievements;
struct RaUiEvent;
}

namespace android {
namespace drastic_overlay {

// Row text scales, shared so every page sizes its rows the same way. The list
// renderer uses kRowBaseScale for a normal row and kRowSelScale for the
// selected one (both multiplied by the viewport scale sf). The Achievements
// page reads kRowBaseScale too so its rows match the rest of the menu instead
// of being sized off a panel-height fraction.
constexpr float kRowSelScale  = 1.77f;
constexpr float kRowBaseScale = 1.50f;

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

    // Debug: open the keyboard for headless OSK-placement verification (this
    // platform cannot inject controller input). Gated by a prop in the render loop.
    void debugOpenOsk() {
        if (!mOsk.active())
            mOsk.open("Keyboard test", "", DrasticOsk::Mode::Text,
                      [](const std::string&) {});
    }

    // True when the bottom screen should show the RetroAchievements detail panel
    // (overlay open on the Achievements section, logged in, no keyboard up).
    bool wantsRaBottomPanel() const;
    // Draw the RetroAchievements detail + leaderboards panel on the bottom DS
    // screen: the selected achievement's badge, title, full description and
    // status, plus the game's leaderboards. Touch scrolls. Rendered against the
    // secondary FBO by main.cpp, same as drawOsk.
    void drawRaBottomPanel(drastic_gfx::OverlayGfx& gfx);
    // Feed a bottom-screen touch (normalized 0..1) to the RA panel for scrolling.
    void raBottomTouch(bool down, bool held, float nx, float ny);
    // Dim the bottom DS screen while the overlay is open on any section, so the
    // paused game reads as "menu is up". Drawn against the secondary FBO.
    void drawBottomScrim(drastic_gfx::OverlayGfx& gfx);
    // Free the bottom-panel / banner GL textures. Call before tearing the GL
    // context down (game exit or relaunch) so no GPU resources are outstanding
    // when the driver releases the context.
    void freeRaTextures(drastic_gfx::OverlayGfx& gfx);

    // Called from the main loop on volume-key presses: VOL = volume,
    // SELECT+VOL = brightness. Adjusts the level and shows the slider HUD.
    void onVolumeAdjust(int dir)     { adjustVolume(dir); }
    void onBrightnessAdjust(int dir) { adjustBrightness(dir); }

    // RetroAchievements: surface a UI event (unlock, game placard, login, etc.)
    // as an on-screen message, and learn whether hardcore restrictions are
    // currently active so the menu can gate cheats and save-state loading.
    void onRaUiEvent(const RaUiEvent& ev);
    void setHardcoreActive(bool on) {
        if (on != mRaHardcore) {
            mRaHardcore = on;
            // Reflect the cheats / load-state gating immediately if the menu is
            // already open when RetroAchievements finishes loading.
            if (mOpen) rebuildRows();
        }
    }
    bool hardcoreActive() const { return mRaHardcore; }
    // Give the menu the RetroAchievements client so the Achievements section can
    // show login + the achievement list.
    void setRaClient(NanoRetroAchievements* ra) { mRa = ra; }
    // Single-screen mode: no second DS screen to host the RetroAchievements
    // detail panel, so the Achievements section gets a controller-driven
    // drill-in (achievement detail with badge + description + progress, and a
    // leaderboards view) rendered on the one screen. Set false for dual-panel
    // devices (RG DS), which keep the bottom-screen panel instead.
    void setSingleScreen(bool s) { mSingleScreen = s; }

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

    // True when the user picked "Power Off" / "Reboot" from the overlay. main.cpp
    // saves DraStic slot 9 (and arms Quick Resume when enabled), then powers the
    // device off / reboots instead of returning to the XMB. The only in-DRM power
    // control besides the ~5s power-button hold (the SF gammaos-nano overlay
    // cannot composite over a DRM-master game).
    bool powerOffRequested() const { return mPowerOff; }
    bool rebootRequested() const { return mReboot; }

    // Access staged prefs (for final write-on-close).
    const drastic_prefs::Prefs& prefs() const { return mPrefs; }

    // Force-close the menu (used when drastic-nano is shutting down).
    void close();

private:
    enum Section { kSec_Save = 0, kSec_Video, kSec_Audio, kSec_Controls,
                   kSec_Cheats, kSec_Achievements, kSec_COUNT };
    enum class NavDir { None, Up, Down, Left, Right };

    struct RowAction {
        std::string label;
        std::string value;  // right-aligned value string (may be empty)
        // Handler invoked on Accept (A). Optional.
        std::function<void()> onAccept;
        // Left/Right handler for adjustable fields. Optional.
        std::function<void(int dir)> onAdjust;
        // Colour hint for the Achievements list: 0 normal, 1 unlocked (gold),
        // 2 locked (dim), 3 section header (accent).
        int tag = 0;
        // Optional detail (achievement description) shown for the selected row.
        std::string detail;
        // Achievement id for an Achievements-list row (0 otherwise), used to
        // pull the badge for the bottom-screen detail panel.
        uint32_t raAchId = 0;
    };
    enum { kRowNormal = 0, kRowUnlocked = 1, kRowLocked = 2, kRowHeader = 3 };

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
    bool mPowerOff = false;           // "Power Off" row selected
    bool mReboot = false;             // "Reboot" row selected
    bool mRaHardcore = false;         // RetroAchievements hardcore restrictions active
    NanoRetroAchievements* mRa = nullptr;   // RetroAchievements client (for the Achievements section)
    uint32_t mRaUiGen = 0;            // last seen RA UI generation (refresh Achievements on change)
    bool mRaShownLoggedIn = false;    // login state the Achievements rows were built for
    bool mRaShownActive = false;      // gameActive() the Achievements rows were built for

    // RetroAchievements rich banner (top-right, drawn over gameplay and over the
    // menu). Used for unlocks AND all other RA messages (login, placard, mastery,
    // errors). The badge texture is created lazily when the decoded image arrives
    // from the RA client (NanoRetroAchievements::popBadge).
    bool        mBannerActive = false;
    int64_t     mBannerStartMs = 0;
    int64_t     mBannerDurMs = 6000;
    std::string mBannerHeader;        // small top line, e.g. "ACHIEVEMENT UNLOCKED"
    std::string mBannerTitle;
    std::string mBannerDesc;
    int         mBannerPoints = -1;   // <0 hides the points chip
    uint32_t    mBannerAchId = 0;     // 0 = no badge (a plain message)
    unsigned    mBannerBadgeTex = 0;       // GLuint; 0 until the badge is uploaded
    uint32_t    mBannerBadgeTexAchId = 0;  // achievement the current texture belongs to
    // Accent colour of the banner frame (varies by message kind).
    float       mBannerAccent[3] = {0.96f, 0.80f, 0.28f};
    // Queue of pending banners so several achievements unlocking at once are
    // shown one after another instead of clobbering each other.
    struct BannerSpec {
        std::string header, title, desc, badgeUrl;
        int points = -1;
        uint32_t achId = 0;
        float accent[3] = {0.96f, 0.80f, 0.28f};
    };
    std::deque<BannerSpec> mBannerQueue;
    void startNextBanner();   // pop the next queued banner and show it
    Section mSection = kSec_Save;
    int mCursor[kSec_COUNT] = {};   // zero-init all sections (count-proof)
    int mScroll[kSec_COUNT] = {};
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

    // Bottom-screen RetroAchievements detail panel: badge textures lazily built
    // from the on-disk cache keyed by achievement id, plus its touch scroll.
    std::map<uint32_t, unsigned> mRaBadgeTex;
    // Throttle: last elapsedRealtime (ms) a not-yet-cached badge was attempted,
    // so a missing badge is retried ~1Hz instead of a file read+decode/frame.
    std::map<uint32_t, int64_t> mRaBadgeMissAt;
    // In-gameplay RA indicators (RA compliance: Measured/progress and
    // Trigger/challenge flags must be shown during play, not only in the list).
    // Challenge: the set of currently primed achievements (id -> badge url),
    // toggled by the runtime's CHALLENGE_INDICATOR_SHOW/HIDE events; their badges
    // are drawn at the screen edge while playing.
    std::map<uint32_t, std::string> mRaChallenge;
    // Progress: the most recent measured-progress popup (badge + "title  23/50"),
    // shown on PROGRESS_INDICATOR_SHOW/UPDATE and auto-hidden after a short tail.
    uint32_t    mRaProgressId = 0;
    std::string mRaProgressText;
    std::string mRaProgressBadgeUrl;
    int64_t     mRaProgressUntilMs = 0;
    float mRaBottomScroll = 0.0f;       // leaderboard list scroll (pixels)
    float mRaBottomMaxScroll = 0.0f;    // clamp, set each draw from content size
    float mRaBottomViewH = 1.0f;        // panel height (pixels), set each draw
    float mRaScrollVel = 0.0f;          // inertial scroll velocity (px/frame)
    bool  mRaBottomTouchActive = false; // a drag is in progress
    float mRaBottomTouchY = 0.0f;       // last touch Y (normalized) for the drag
    bool  mPrevRaTouch = false;         // previous-frame finger state (tap edge)
    // Tap vs drag detection + leaderboard drill-in (online rankings) state.
    float mRaTouchDownX = 0.0f, mRaTouchDownY = 0.0f;
    bool  mRaTouchMoved = false;
    uint32_t mRaOpenLbId = 0;           // leaderboard whose rankings are open
    // Hit-test layout cached from the last draw (pixels), for tap handling.
    // mLbHitBot / mLbHitTextH mirror the draw-side whole-row clip so a tap in a
    // scrolled-off partial-row gap does not resolve to a hidden leaderboard.
    float mLbHitTop = 0.0f, mLbHitRowH = 1.0f;
    float mLbHitBot = 0.0f, mLbHitTextH = 0.0f;
    std::vector<uint32_t> mLbHitIds;    // leaderboard ids in drawn order
    float mBackBtnX = 0, mBackBtnY = 0, mBackBtnW = 0, mBackBtnH = 0;
    void raHandleTap(float nx, float ny);

    // Single-screen RetroAchievements drill-in (no bottom DS panel). View:
    // 0 = achievement list (normal), 1 = achievement detail card,
    // 2 = leaderboards list, 3 = one leaderboard's online rankings.
    bool mSingleScreen = false;
    int  mRaView = 0;
    int  mAchTopRow = 0;           // first visible row of the rich achievements
                                   // list (whole-row scroll, so the cursor moves
                                   // within the window and only scrolls at edges,
                                   // and no partial row overlaps the footer)
    uint32_t mRaDetailAchId = 0;   // achievement shown in the detail card (view 1)
    int   mLbCursor = 0;           // selected row in the leaderboards list (view 2)
    float mRaViewScroll = 0.0f;    // scroll offset for the rankings list (view 3)
    float mRaViewMaxScroll = 0.0f; // clamp, recomputed each draw from content size
    // Handle Accept / Cancel / Up-Down in the single-screen RA drill-in. Each
    // returns true when it consumed the input (so normal list nav is skipped).
    bool raSingleAccept();
    bool raSingleCancel();
    bool raSingleNav(NavDir dir);
    // Draw the active single-screen RA view (detail / leaderboards / rankings)
    // into the list region. Returns true when it drew (caller skips the list).
    bool drawRaSingle(drastic_gfx::OverlayGfx& gfx, float x, float top,
                      float w, float h, float sf);
    // Rich RetroAchievements list (badge + title + description + unlock date /
    // rarity + points per row, grouped by bucket), replacing the plain text
    // list for the Achievements section. Uses mRows for order/cursor and the
    // achievement snapshot for the badge/rarity/unlock-time detail.
    void drawAchievementsList(drastic_gfx::OverlayGfx& gfx, float vw,
                              float listY, float listH, float sf);
    // Lazily decode + upload an achievement badge from the on-disk cache.
    // Returns a GL texture (0 if not cached yet). Cached for the session.
    unsigned raBadgeTex(uint32_t achId, drastic_gfx::OverlayGfx& gfx);

    // In-app volume / brightness slider HUDs (ported from the Nano home),
    // shown on the volume keys since the SF system HUDs never appear on the
    // DRM-direct path. Render every frame (even with the menu closed) and
    // auto-hide after ~1.5s.
    int mBrightLevel = 128;     // 0-255 (Android range)
    int mVolHudTimer = 0;       // frames remaining (60fps)
    int mBrightHudTimer = 0;
    bool mBrightInit = false;
    // System volume mirror for the DRM-path HUD: the real level PhoneWindowManager
    // publishes (persist.gammaos.nano.volume/volmax). adjustVolume tracks it so the
    // slider shows the actual output level, not the DS core's own mixer.
    int mSysVol = 0;
    int mSysVolMax = 15;
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
    void rebuildAchievements();
    void startRaLogin();   // chained username + password OSK -> mRa->requestLogin
    // Show the rich banner. header is the small top line; points<0 hides the
    // points chip; achId>0 requests the badge image; accent is the frame colour.
    void showBanner(const std::string& header, const std::string& title,
                    const std::string& desc, int points, uint32_t achId,
                    float ar, float ag, float ab, const std::string& badgeUrl = "");
    void drawAchievementBanner(drastic_gfx::OverlayGfx& gfx, float sf);
    // Draw the in-gameplay challenge (primed) and progress (measured) indicators
    // over the running game (skipped while the overlay menu itself is open).
    void drawRaIndicators(drastic_gfx::OverlayGfx& gfx, float sf);

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
