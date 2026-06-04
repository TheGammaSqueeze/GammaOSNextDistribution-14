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

#ifndef GAMMAOS_NANO_MENU_H
#define GAMMAOS_NANO_MENU_H

#include <stdint.h>
#include <functional>
#include <string>
#include <vector>
#include <set>
#include <map>
#include <unordered_map>
#include <atomic>
#include <mutex>
#include <thread>

#include "NanoMenuSettingsTree.h"
#include "NanoOsk.h"

#include <ft2build.h>
#include FT_FREETYPE_H

#include <utils/Thread.h>
#include <binder/IBinder.h>

#include <EGL/egl.h>
#include <GLES2/gl2.h>

namespace android {

class Surface;
class SurfaceComposerClient;
class SurfaceControl;

static const int MAX_PARTICLES = 150;
static const int NUM_EFFECTS = 22; // total effect IDs (some disabled)

struct Particle {
    float x, y, vx, vy, size;
    float r, g, b, a;
    float life, phase;
};

struct GlyphInfo {
    float u0, v0, u1, v1;  // UV coords in atlas
    int bmpW, bmpH;        // raw bitmap size in atlas
    int bearingX, bearingY; // offset from baseline
    int advance;            // horizontal advance in pixels
    bool color;             // true for color emoji (BGRA)
    float scaleW, scaleH;  // display scale (for emoji normalization)
};

// Static, table-driven 1:1 copy of the web index.html DATA[] tree. One entry
// per menu item; children point into the same static array space. The tree is
// generated straight from the web source in NanoMenuPS3Data.h. Namespace scope
// so the static const tables in NanoMenuPS3Menu.cpp can reference them.
struct Ps3DataItem {
    const char* name;
    int icon;               // xmb_icon index (NNN); -1 = special (PS Store)
    const char* desc;       // may be null
    const char* value;      // may be null
    int action;             // 0 none, 1 dialog, 2 landing
    const Ps3DataItem* children;
    int childCount;
};
struct Ps3DataCat {
    const char* id;
    const char* name;
    int icon;               // category xmb_icon index
    const Ps3DataItem* items;
    int itemCount;
};

class NanoMenu : public Thread, public IBinder::DeathRecipient {
public:
    NanoMenu();
    virtual ~NanoMenu();

    sp<SurfaceComposerClient> session() const;

    struct MenuItem {
        std::string label;
    };

    struct RecentEntry {
        std::string label;
        std::string romPath;
        std::string corePath;
        std::string coreName;
        std::string dbName;  // system/platform name from playlist
    };

    struct AppEntry {
        std::string packageName;
        std::string label;
    };

    struct XmbSystem {
        std::string name;
        std::string shortname;
        std::string romDir;        // Directory name under ROMs/
        std::string coreSo;        // RetroArch core .so filename (empty for standalone)
        std::string launchPkg;     // Package name for standalone emulators
        std::string launchIntent;  // Intent template ({file.uri} placeholder)
        float iconR, iconG, iconB; // Icon color
        std::string acceptExts;    // Comma-separated accepted extensions
        std::string activePath;    // Primary path (largest collection) — for backward compat
        std::vector<std::string> activePaths; // ALL directories with ROMs for this system
        std::vector<std::string> roms;         // Sorted FULL PATHS (e.g., /storage/UUID/nes/game.nes)
        std::vector<std::string> displayNames; // Pre-stripped display names (parallel to roms)
        bool scanned;
        bool pathExists;
        int64_t lastScanTime;     // elapsedRealtime() of last scan — for periodic rescan
        bool isStandalone() const { return !launchPkg.empty(); }
    };

    struct SearchResult {
        int sysIdx;
        int gameIdx;
    };

    struct XmbRecentEntry {
        std::string romPath;       // Full ROM path for launch
        std::string coreSo;        // Core .so or empty for standalone
        std::string launchPkg;     // Package for standalone
        std::string launchIntent;  // Intent template for standalone
        std::string displayName;   // Game display name
        std::string systemName;    // System shortname (e.g. "NES")
        std::string romDir;        // ROM directory name
        bool standalone;
    };

    enum MenuState {
        MENU_MAIN = 0,
        MENU_RECENT = 1,
        MENU_APPS = 2,
        MENU_WIFI = 3,
        MENU_BT = 4,
        MENU_SETTINGS = 5,
        MENU_SETUP_WIZARD = 6,
    };

    enum SetupWizardStep {
        SETUP_WELCOME = 0,
        SETUP_LANGUAGE,
        SETUP_WIFI,
        SETUP_BLUETOOTH,
        SETUP_TIMEZONE,
        SETUP_INSTALLING,
        SETUP_FINISH,
        SETUP_STEP_COUNT,
    };

    struct TimezoneEntry {
        std::string id;
        std::string display;
        int offsetMinutes;
        float lon = 0.0f;   // city longitude (deg) for the 3D globe selector
        float lat = 0.0f;   // city latitude (deg)
    };

    struct SettingsItem {
        std::string label;
        int action; // 0 = Wi-Fi screen, 1 = Bluetooth screen
    };

    struct WifiNetEntry {
        std::string ssid;
        std::string bssid;
        int rssi;            // dBm (e.g. -55)
        int security;        // 0=none, 1=wep, 2=wpa/wpa2, 3=wpa3, 4=owe
        int savedNetId;      // -1 if not saved
        bool connected;
    };

    struct BtDevEntry {
        std::string name;
        std::string address;
        bool bonded;
        bool connected;
    };

private:
    // Hold-to-repeat navigation direction. Declared here (before use in
    // member function signatures) so navPress/navRelease can reference it.
    enum class NavDir : int { None = 0, Up, Down, Left, Right };

    virtual bool        threadLoop();
    virtual status_t    readyToRun();
    virtual void        onFirstRef();
    virtual void        binderDied(const wp<IBinder>& who);

    // Input handling
    void openInputDevices();
    void checkInputHotplug();
    void pollInput();
    void handleUp();
    void handleDown();
    void handleLeft();
    void handleRight();
    // Hold-to-repeat helpers. navPress() fires the matching handle* once
    // and arms the repeat tick; navRelease() clears it (pass NavDir::None
    // to clear unconditionally, e.g. on HAT/stick return to center when
    // we don't know which specific axis direction was held).
    // tickNavRepeat() is invoked once per frame from pollInput() and
    // fires handle* at an accelerating cadence while a direction is held.
    void navPress(NavDir dir);
    void navRelease(NavDir dir);
    void tickNavRepeat();
    void handleSelect();
    void handleBack();
    void loadRecentPlaylist();
    void loadInstalledApps();

    // XMB mode
    void initXmbSystems();
    void scanRomPaths();
    void forceRescanAllSystems();
    bool scanOneSystemAsync(int sysIdx);
    void renderXmb();
    void launchXmbGame();
    void loadXmbRecent();
    void saveXmbRecent();
    void addXmbRecent(int sysIdx, int gameIdx);

    // On-screen keyboard. Native reimplementation of the Leanback IME keyboard
    // extended into a multi-script input method. Runtime state is mOsk
    // (NanoOskState); layout data is the generated kOskKb[] / kOskPopups[].
    // Implementation lives in NanoOsk.cpp.
    void openOsk();                 // search-mode OSK
    void closeOsk();                // dismiss (popup-aware)
    void oskType(char c);           // legacy thin ASCII insert, kept for callers
    void oskBackspace();            // delete one codepoint before the caret
    void oskConfirm();              // submit (search -> results; password -> callback)
    void updateSearchResults();
    void renderOsk();
    // New OSK runtime (NanoOsk.cpp):
    void oskApplyLocale();          // choose layout from the active UI language
    void oskSetLanguage(const char* code, const char* region); // layout + IME
    void oskMoveCursor(NavDir dir); // geometric nearest-in-direction focus move
    void oskActivateKey(const OskKey& key); // dispatch a grid key (A on key)
    void oskAPress();               // A pressed while OSK active
    void oskARelease();             // A released while OSK active
    void oskTick();                 // per-frame: long-press popup + animation clock
    void oskToggleShift();          // off <-> on (from locked -> off)
    void oskToggleCaps();           // caps lock toggle
    void oskToggleSym();            // ABC <-> SYM page
    void oskOpenPopup(const OskKey& key);  // open accent/shift mini popup
    void oskClosePopup();
    void oskCommitPopupCell();      // commit the focused popup cell
    void oskCycleLanguage(int dir); // in-keyboard language switch
    void oskInsertCp(uint32_t cp);  // insert a codepoint at the caret (UTF-8)
    void oskCaretLeft();
    void oskCaretRight();
    OskBox oskLayoutBox();          // compute the aspect-aware keyboard box
    const OskKeyboard* oskCurrentKb() const; // current page's keyboard table

    // Settings column (WiFi + Bluetooth). The Settings entry lives as an
    // extra pseudo-system at index == mXmbSystems.size() in the XMB column
    // bar. Selecting it shows a vertical list of {"Wi-Fi", "Bluetooth"}.
    // Selecting an item from that list opens a full-screen sub-menu.
    void initSettingsItems();
    void renderSettingsList(float selIconX, float iconBarY, float iconSpacingV,
                            float iconSize, float sf,
                            float textScale, float selTextScale);
    bool isOnSettingsColumn() const;

    // Wi-Fi screen state + helpers
    void openWifiScreen();
    void closeWifiScreen();
    void refreshWifiList();              // blocking: list-networks + scan + list-scan-results
    void startWifiScanAsync();           // kick a background scan thread
    void wifiScanThreadFunc();
    void renderWifiScreen();
    void handleWifiScreenSelect();
    void handleWifiScreenUp();
    void handleWifiScreenDown();
    void handleWifiScreenX();            // manual rescan
    void handleWifiScreenY();            // forget / remove the selected saved network
    void connectToSavedWifi(int savedNetId);
    void addAndConnectWifi(const std::string& ssid, int security,
                           const std::string& password);
    void connectWithWizardSettings();   // applies the wizard's static IP/DNS/MTU/proxy
    void forgetWifiNetwork(int savedNetId);
    void toggleWifiRadio(bool on);
    bool wifiRadioEnabled();   // live Wi-Fi radio state (for the Internet Connection toggle)
    // Network Settings dialogs backed by the live system state:
    std::string buildNetStatusBody();    // real SSID/IP/gateway/DNS/MAC for the status list
    void startNetTest();                 // async connectivity test (IP / internet / DNS)
    void stopNetTest();                  // stop + join the test thread

    // Bluetooth screen state + helpers
    void openBtScreen();
    void closeBtScreen();
    void refreshBtList();                // blocking: list bonded + list scanned
    void discoverBtDevices();            // blocking: ~8 s discovery run
    void startBtScanAsync();
    void startBtDiscoveryAsync();
    void btScanThreadFunc();
    void btDiscoveryThreadFunc();
    void renderBtScreen();
    void handleBtScreenSelect();
    void handleBtScreenUp();
    void handleBtScreenDown();
    void handleBtScreenX();              // rescan
    void handleBtScreenY();              // unpair selected bonded device
    void pairBtDevice(const std::string& mac);
    void unpairBtDevice(const std::string& mac);
    void connectBtDevice(const std::string& mac);
    void toggleBtRadio(bool on);

    // OSK password extension: same visual OSK, but types into a password
    // field rendered with masked chars, and on Enter invokes a callback.
    void openOskForPassword(const std::string& prompt,
                            std::function<void(const std::string&)> onSubmit);
    std::string maskPassword(const std::string& s);

    // Hierarchical settings tree browser (MENU_SETTINGS)
    void buildSettingsTree();
    void openSettingsTree();
    void settingsTreePushCategory(int nodeIdx);
    bool settingsTreePop();
    void settingsTreeGetChildren(int parentIdx, std::vector<int>& out) const;
    std::string settingsTreeBreadcrumb() const;
    void renderSettingsTree();
    void handleSettingsTreeSelect();
    void handleSettingsTreeBack();
    void handleSettingsTreeUp();
    void handleSettingsTreeDown();
    void handleSettingsTreeLeft();
    void handleSettingsTreeRight();
    void startSettingsValueRefresh();
    std::string getSettingsCachedValue(int nodeIdx) const;
    void settingsToggleValue(int nodeIdx);
    void settingsCycleListValue(int nodeIdx, int direction);
    void settingsSetTextValue(int nodeIdx, const std::string& val);

    // Setup wizard (NanoMenuSetupWizard.cpp)
    bool checkDeviceProvisioned();
    void startSetupWizard();
    void finishSetupWizard();
    void renderSetupWizard();
    void renderSetupWelcome();
    void renderSetupWifiStep();
    void renderSetupBluetoothStep();
    void renderSetupTimezone();
    void renderSetupInstalling();
    void renderSetupFinish();
    void renderSetupProgressDots();
    void handleSetupSelect();
    void handleSetupBack();
    void handleSetupUp();
    void handleSetupDown();
    void handleSetupStart();
    void advanceSetupStep();
    void goBackSetupStep();
    void updateSetupTransition();
    void buildTimezoneList();
    void startSetupScript();
    void stopSetupLogThread();
    void setupLogTailThreadFunc();
    void renderSetupLanguage();
    void handleSetupLanguageSelect();

    // Quick Resume
    void prepareShutdown(const char* action);
    bool isRetroArchRunning();

    // Brightness control
    void adjustBrightness(int direction);
    bool setBrightnessViaHal(int brightness);
    void applyBrightness();
    void syncBrightnessToAndroid();
    int readAndroidBrightness();
    void renderBrightnessBar();

    // Volume control
    void adjustVolume(int direction);
    void renderVolumeBar();
    int readSysfsInt(const char* path, int fallback);
    void writeSysfsInt(const char* path, int value);

    // GammaOS Nano: launch-readiness gate. Returns true once the system
    // is far enough through boot for the home-launch path in
    // RootWindowContainer.startHomeOnTaskDisplayArea() to actually
    // accept a launch. Without this gate, pressing A on a game in the
    // few seconds between NanoMenu paint and user 0 unlock leaves the
    // screen blank: NanoMenu exits, bootanim exits, but RWC's nano
    // launch branch returns false (user not unlocking yet) so no app
    // ever takes over the display.
    bool isLaunchReady() const;
    // Arm the centred "Booting up" overlay AND mark the press as a
    // queued launch -- the main loop re-fires handleSelect() as soon
    // as isLaunchReady() flips to true, so the user does not have to
    // press A a second time after boot finishes.
    void showLaunchBusyToast();
    // Drop the queued launch (called from navigation handlers and
    // handleBack so navigating away cancels the pending launch).
    void cancelPendingLaunch();
    void renderLaunchBusyToast();

    // Battery HUD
    void pollBattery();
    // Returns the right-edge X (in surface pixels) of the whole battery
    // indicator (icon + text). Network HUD chains its own icons from this
    // x so the layout scales cleanly with resolution / orientation.
    float renderBatteryIndicator();

    // Network HUD (WiFi + BT). Real polling happens on a background thread
    // because the underlying 'cmd wifi status' / dumpsys calls are slow
    // enough (100-400ms on a cold first call, 10-50ms steady state) that
    // running them on the render thread would spike the frame budget.
    // Render thread only reads the cached state behind mNetStateMutex.
    enum WifiLevel {
        kWifiLevel_Unknown = 0,
        kWifiLevel_Off,
        kWifiLevel_Disconnected,  // radio on, no network
        kWifiLevel_Connected,     // add mWifiBars for signal strength 0..4
    };
    enum BtLevel {
        kBtLevel_Unknown = 0,
        kBtLevel_Off,
        kBtLevel_On,
        kBtLevel_Connected,       // at least one device connected
    };
    void startNetPollThread();
    void stopNetPollThread();
    void netPollThreadFunc();
    // Draws WiFi then BT icons + short label starting at startX. Returns
    // the x position right after the last-drawn element so callers can
    // chain more HUDs to the right.
    float renderNetworkIndicators(float startX, float rowY, float rowH,
                                  float sf, float textScale);
    void drawWifiIcon(float x, float y, float sf, int bars,
                      float r, float g, float b, float a);
    void drawBtIcon(float x, float y, float sf,
                    float r, float g, float b, float a);

    // Rendering
    void initShaders();
    void initFonts();
    void ensureGlyph(uint32_t codepoint);
    void drawText(const char* str, float px, float py, float scale,
                  float r, float g, float b, float a);
    float measureText(const char* str, float scale);
    void render();
    void drawQuad(float x, float y, float w, float h,
                  float r, float g, float b, float a);
    // GammaOS: Create EGL surfaces on every non-primary physical display so the
    // post-HWC render loop can drive wallpaper-only rendering on those panels.
    // Idempotent — first call wires the surfaces, subsequent calls are no-ops.
    void setupSecondaryEglSurfaces();
    // Deferred SF init: create SurfaceComposerClient + SF surface when
    // transitioning from DRM boot path to app launch.
    void initSurfaceFlingerPath();

    // Menu
    void buildMenu();
    void rebuildDisplayItems();

    // Effects
    void initEffects();
    void resetParticle(int i);
    void updateEffect();
    void renderEffect();

    sp<SurfaceComposerClient> mSession;
    int         mWidth;
    int         mHeight;
    EGLDisplay  mDisplay;
    EGLContext  mContext;
    EGLSurface  mSurface;
    sp<IBinder> mDisplayToken;
    uint32_t mAppliedLayerStack; // GammaOS: last layer stack applied to the nano surface
    std::vector<sp<IBinder>> mSecondaryDisplayTokens; // GammaOS: secondary display tokens
    std::vector<sp<SurfaceControl>> mSecondaryWallpaperControls; // GammaOS: wallpaper on secondaries
    std::vector<EGLSurface> mSecondaryEglSurfaces; // GammaOS: EGL surfaces for secondary wallpaper
    std::vector<sp<Surface>> mSecondarySurfaces; // GammaOS: keep refs alive
    std::vector<uint32_t> mSecondaryAppliedLayerStacks; // GammaOS: last layer stack applied to each secondary wallpaper SC
    sp<SurfaceControl> mFlingerSurfaceControl;
    sp<Surface> mFlingerSurface;

    // GL shader program
    GLuint mShaderProgram;
    GLint  mLocPosition;
    GLint  mLocColor;
    GLint  mLocRotation; // DRM orientation rotation

    // Batched particle shader (per-vertex color)
    GLuint mParticleProgram;
    GLint  mParticleLocPosition;
    GLint  mParticleLocColor;
    GLint  mParticleLocRotation;

    // Fullscreen effect shader
    GLuint mFxProgram;
    GLint  mFxLocPosition;
    GLint  mFxLocTime;
    GLint  mFxLocResolution;
    GLint  mFxLocEffect;
    GLint  mFxLocRotation;
    GLint  mFxLocCoordSwap;
    GLint  mFxLocYFlip;
    GLint  mFxLocXFlip;

    // XMB background shader (PS3-style volumetric ribbons, 60fps)
    GLuint mXmbProgram;
    GLint  mXmbLocPosition;
    GLint  mXmbLocTime;
    GLint  mXmbLocResolution;
    GLint  mXmbLocRotation;
    GLint  mXmbLocCoordSwap;
    GLint  mXmbLocYFlip;
    GLint  mXmbLocXFlip;

    // Menu state
    std::vector<MenuItem> mMenuItems;
    int mSelectedIndex;

    // Pre-computed display strings (rebuilt on state change, not every frame)
    std::vector<std::string> mDisplayItems;
    std::string mTitle;
    std::string mSubtitle;
    std::string mFooter;
    bool mDisplayDirty; // true when display items need rebuild

    // Input device fds
    std::vector<int> mInputFds;
    std::set<std::string> mOpenedDevices;
    int mInotifyFd;

    // Exit flag
    bool mExitRequested;
    bool mWaitForRelease; // wait for select key release before exiting
    bool mDrasticNanoPending; // drastic nano: waiting for cache, then restart
    bool mDrmBootPath; // headless EGL + DRM direct (SF deferred until app launch)

    // Submenu state
    MenuState mMenuState;
    std::vector<RecentEntry> mRecentEntries;
    int mRecentSelectedIndex;
    bool mRecentLoaded;  // true if playlist file was readable
    bool mStorageReady;  // true once /data/media/0 is accessible (CE unlocked)

    // Applications submenu
    std::vector<AppEntry> mAppEntries;
    int mAppSelectedIndex;
    bool mAppsLoaded;

    // Scrolling text state for long game names in Recently Played / Applications
    float mScrollOffset;
    int   mScrollDir;       // 1 = scrolling left, -1 = scrolling right
    int   mScrollPause;     // frames to pause at each end before reversing
    int   mLastScrolledIdx; // which item index was scrolling (reset on change)

    // Vertical menu scroll for submenus with more items than fit on screen
    int mMenuScrollTop;     // first visible item index

    // Analog stick state
    bool mStickYTriggered; // prevents repeat until stick returns to center
    bool mStickXTriggered; // prevents repeat for horizontal axis

    // Hold-to-repeat navigation. Set when a dpad key / HAT axis / stick
    // axis enters its held state; cleared on release. pollInput() ticks
    // this each frame and calls handleUp/Down/Left/Right at an
    // accelerating cadence while a direction is held, so users don't
    // have to tap repeatedly to scroll long lists.
    NavDir  mNavHeldDir      = NavDir::None;
    int64_t mNavHeldStartMs  = 0; // when the current direction was pressed
    int64_t mNavLastRepeatMs = 0; // when we last fired a repeat (or initial fire)
    int     mNavRepeatCount  = 0; // repeat fires so far, used for acceleration

    // Brightness / power
    bool mSelectHeld;
    bool mStartHeld = false;   // for the START+SELECT PS3 boot-intro re-trigger
    int64_t mPowerPressTime;
    int mBrightness;
    int mMaxBrightness;
    bool mShowBrightnessBar;
    int mBrightnessBarTimer;
    int mVolume;
    int mMaxVolume;
    bool mShowVolumeBar;
    int mVolumeBarTimer;

    // GammaOS Nano: launch-busy state. mShowLaunchBusy drives the
    // centred toast for a few seconds after a too-early press, while
    // mLaunchPending persists until either isLaunchReady() flips true
    // (auto re-fire of handleSelect) or the user navigates / backs out.
    bool mShowLaunchBusy;
    int mLaunchBusyTimer;
    bool mLaunchPending;

    // Battery state (cached, refreshed ~1/s from pollBattery())
    int mBatteryPercent;      // -1 if unknown / no battery
    bool mBatteryCharging;    // true when charging or full
    int mBatteryPollTicks;    // frames until next sysfs read

    // Network state (cached, refreshed from netPollThreadFunc at ~0.5 Hz).
    // All fields are guarded by mNetStateMutex; copy into locals before use.
    std::mutex mNetStateMutex;
    WifiLevel mWifiLevel;
    bool mWifiRadioOn = true;      // cached radio on/off for the Internet Connection row (no per-frame query)
    int mWifiBars;                 // 0..4 signal strength, meaningful only if Connected
    std::string mWifiSsid;         // connected SSID, empty otherwise
    BtLevel mBtLevel;
    int mBtConnectedCount;         // bonded AND currently connected devices
    bool mNetPollInitialised;      // first poll landed, safe to draw
    // Worker thread handles for the net poller. Started from the ctor
    // after mSession is wired up; joined from the dtor.
    std::thread mNetPollThread;
    bool mNetPollThreadRunning;
    bool mNetPollExitRequested;

    // Frame timing
    int64_t mLastFrameNs;  // monotonic clock from previous frame
    float mFrameDt;        // seconds elapsed since last frame (clamped)

    // Effects
    int mCurrentEffect; // 0 = none, 1..21 = effect
    float mEffectTime;
    Particle mParticles[MAX_PARTICLES];

    // Quick Resume
    bool mQuickResumeEnabled;

    // XMB mode
    bool mXmbMode;
    // drawText() outline/shadow control:
    //   0 = default (single drop shadow in XMB mode, else 4-offset outline) at 0.8a
    //   1 = even 4-offset outline at mTextOutlineRatio*a (PS3 XMB: symmetric, subtle,
    //       brightness-scaled, and ONE draw call so it stays cheap on every label)
    //   2 = none (used around glow/halo white copies so they get no dark outline)
    int   mTextOutlineMode = 0;
    float mTextOutlineRatio = 0.8f;
    std::vector<XmbRecentEntry> mXmbRecent; // Recently played from XMB
    int mXmbRecentMax;                       // Max entries to keep
    std::vector<XmbSystem> mXmbSystems;
    int mXmbSystemIndex;       // Currently selected system
    int mXmbGameIndex;         // Currently selected game in current system
    float mXmbAnimX;           // Animated horizontal position (lerps to mXmbSystemIndex)
    float mXmbAnimY;           // Animated vertical position (lerps to mXmbGameIndex)
    int mXmbGameScrollTop;     // First visible game in list
    bool mXmbRomScanDone;      // ROM paths have been scanned
    bool mXmbBootCompleted;    // true after sys.boot_completed=1

    // ===================================================================
    // PS3 XMB layout (NanoMenuPS3Menu.cpp). A faithful PS3 XrossMediaBar:
    // a horizontal category bar (Settings/Game/...) with a vertical item
    // list dropping out of the active category, submenu push/pop, the
    // authentic firmware geometry and the captured wave behind it. Gated
    // by persist.gammaos.nano.ps3xmb during build-up; the categories are
    // populated from the SAME nano content the carousel uses (emulator
    // systems + ROMs, recently played, applications, settings).
    // ===================================================================
    enum Ps3ItemKind {
        PS3_SYSTEM = 0,   // an emulator system -> ROM submenu (a = sysIdx)
        PS3_ROM,          // a ROM -> launch (a = sysIdx, b = romIdx)
        PS3_RECENT_LIST,  // "Recently Played" -> recent submenu
        PS3_RECENT,       // a recent entry -> launch (a = recent idx)
        PS3_APP_LIST,     // "Applications" -> app submenu
        PS3_APP,          // an installed app -> launch (a = app idx)
        PS3_SETTING,      // a settings entry (a = action: 0 Wi-Fi, 1 Bluetooth, 2 settings tree)
        PS3_LAUNCH_PKG,   // launch a package (payloadStr = package name)
        PS3_DATA_SUBMENU, // a static DATA item with children -> submenu (data*)
        PS3_DATA_LEAF,    // a static DATA leaf (dialog / value / info, no action)
    };
    struct Ps3Item {
        std::string label;
        std::string desc;
        std::string value;
        std::string payloadStr;
        GLuint iconTex = 0;     // flat fallback texture (mono-white), 0 = none
        GLuint nmapTex = 0;     // normal map for the glass shader, 0 = flat fallback
        float iconR = 1.0f, iconG = 1.0f, iconB = 1.0f;  // _ChangingColor tint
        int kind = 0;
        int a = 0, b = 0;
        const Ps3DataItem* data = nullptr;  // static DATA node (children + meta)
        int action = 0;                     // 0 none, 1 dialog, 2 landing
    };
    struct Ps3Cat {
        std::string name;
        GLuint iconTex;
        GLuint nmapTex;     // category icon normal map (glass)
        std::vector<Ps3Item> items;
    };
    struct Ps3Level {       // a submenu level on the navigation stack
        std::string title;
        std::vector<Ps3Item> items;
        int sel;
    };
    bool mPs3Xmb = false;         // persist.gammaos.nano.ps3xmb
    bool mPs3MenuBuilt = false;
    float mPs3UiScale = 1.0f;     // persist.gammaos.nano.ps3xmb.uiscale (menu zoom)
    // ---- PS3 cold-boot intro (NanoMenuPS3Boot.cpp) ----
    // The intro plays the wave/gradient revealing from black, the white logo +
    // footer plate, the photosensitivity warning, then hands off to the XMB with
    // the category/icon/clock fading + popping in. Driven each frame from the
    // boot clock (accumulated clamped mFrameDt, NOT mEffectTime which wraps).
    bool   mPs3BootActive = false;
    bool   mPs3BootWizardAfter = false;  // fresh setup -> show the wizard after the intro
    double mPs3BootElapsedMs = 0.0;      // monotonic boot clock
    float  mPs3BootLabelReveal = 1.0f;   // category-label fade-in (1 = fully shown)
    float  mPs3BootIconReveal = 1.0f;    // category-icon / item / clock pop-in (1 = shown)
    GLuint mPs3BootLogoTex = 0;
    GLuint mPs3BootFooterTex = 0;
    bool   mPs3BootPlatesLoaded = false;
    // ---- PS3 Settings dialogs + Theme Settings (NanoMenuPS3Menu.cpp) ----
    // action='dialog' DATA leaves open either a side-panel chooser (Theme
    // Settings: Theme/Colour/Background/Font/Day-Night) or a fullscreen message
    // dialog (System Update, System Information, Format Utility, ...).
    bool   mPs3DlgActive = false;
    int    mPs3DlgKind = 0;        // 0 = fullscreen message/chooser, 1 = side-panel theme chooser
    int    mPs3DlgThemeKey = 0;    // 0 none, 1 theme, 2 colour, 3 background, 4 font, 5 day/night
    std::string mPs3DlgTitle;
    std::string mPs3DlgBody;
    std::vector<std::string> mPs3DlgOptions;
    std::vector<int> mPs3DlgSwatch;   // COLOR_OPTIONS index per option for the Colour chooser, else -1
    int    mPs3DlgSel = 0;
    int    mPs3DlgOrigSel = 0;     // value at open, for revert on cancel
    float  mPs3DlgAnim = 0.0f;     // open slide/fade 0->1
    bool   mPs3DlgBlurValid = false;
    // Fullscreen dialog page (mPs3DlgKind==0). Mirrors web DIALOG_TEMPLATES +
    // drawDialog: a body type, an optional vector illustration, a notice line and
    // the source item's header icon.
    int    mPs3DlgType = 0;        // 0 info, 1 chooser, 2 chooser_illust, 3 confirm
    int    mPs3DlgIllust = 0;      // 0 none,1 hdmi_cable,2 av_multi,3 hdd_warning,4 globe,5 controller,6 bd_remote
    std::string mPs3DlgNotice;     // chooser_illust bottom notice line
    unsigned int mPs3DlgIconTex = 0;   // header item icon (flat fallback)
    unsigned int mPs3DlgIconNmap = 0;  // header item icon (glass normal map)
    float  mPs3DlgIconR = 1.0f, mPs3DlgIconG = 1.0f, mPs3DlgIconB = 1.0f;
    // Live Theme Settings selection indices (mirror the web themeIdx/colorIdx/
    // bgIdx/fontIdx/daynightIdx). They reflect the CURRENT applied-or-previewing
    // value so the menu rows show the selection inline (resolvePs3ItemValue),
    // exactly like the web resolveItemValue. Loaded from props at init.
    int    mPs3ThemeIdx = 0;
    int    mPs3ColorIdx = 0;
    int    mPs3BgIdx = 0;
    int    mPs3FontIdx = 0;
    int    mPs3DayNightIdx = 0;
    // Date and Time settings (functional). Date Format / Time Format are nano-
    // local display choices the clock honours; Daylight Saving reflects the real
    // current DST state (tm_isdst, refreshed each frame in drawPs3Clock) and the
    // toggle switches the timezone between the Olson zone (auto DST) and a fixed
    // standard-offset Etc/GMT zone (no DST). Set Manually's two OSK fields are
    // staged in mPs3DtDate / mPs3DtTime.
    int    mPs3DateFormatIdx = 2;   // 0=YYYY/MM/DD 1=MM/DD/YYYY 2=DD/MM/YYYY (web order)
    int    mPs3TimeFormatIdx = 1;   // 0=12-Hour 1=24-Hour (web order)
    bool   mPs3DstNow = false;      // Daylight Saving currently active (live from tm_isdst)
    std::string mPs3DtDate;         // "YYYY/MM/DD" staged in the Set Manually wizard
    std::string mPs3DtTime;         // "HH:MM"
    // Dynamic text drop shadow, scaled by the wallpaper brightness each frame:
    // strength 0 (dark wallpaper, minimal shadow) .. 1 (light wallpaper, strong).
    float  mPs3ShadowStrength = 0.5f;
    float  mPs3ShadowAlpha = 0.4f;
    // Clock drop-shadow device-y sign, derived from the panel orientation
    // (sDrmRotMat[3]); -1 on the 180 panel. The menu text/icons instead use an
    // even 8-direction outline (drawTextStroke), which needs no direction.
    float  mPs3ShadowDir = -1.0f;
    void   openPs3Dialog(const Ps3Item& it);
    void   closePs3Dialog(bool apply);
    void   renderPs3Dialog();
    // Fullscreen dialog page helpers (1:1 with web drawDialog/drawDialogOption/
    // drawIllustration). Coordinates are device px; ap = open-anim alpha.
    void   ps3DlgNav(int dir, bool horizontal);   // chooser scroll / confirm Yes-No toggle
    void   ps3DlgText(const char* s, float cxDev, float baselineDev, float fs,
                      float r, float g, float b, float a, int align);  // align 0 left,1 centre,2 right
    void   ps3DlgOption(const char* label, float cxDev, float baselineDev,
                        bool sel, bool leftAlign, float ap, float baseScale);
    void   ps3DlgHint(float slotCxDev, bool cross, const char* label,
                      float yDev, float baseScale, float ap);
    void   ps3DlgIllustration(int kind, float cx, float cy, float sz, float ap);
    void   ps3FillCircle(float cx, float cy, float rad, float r, float g, float b, float a);
    void   ps3StrokeRing(float cx, float cy, float radX, float radY, float lw,
                         float r, float g, float b, float a);
    void   ps3ThickLine(float x0, float y0, float x1, float y1, float w,
                        float r, float g, float b, float a);
    void   ps3VGradRect(float x, float y, float w, float h,
                        float r0, float g0, float b0, float r1, float g1, float b1, float a);
    void   previewThemeSetting(int themeKey, int sel);   // apply live (no persist)
    void   applyThemeSetting(int themeKey, int sel);     // persist + apply
    void   loadPs3ThemeSettings();
    std::string resolvePs3ItemValue(const Ps3Item& it);  // live theme value for a row, else it.value
    // Dark STROKE behind text/icons instead of a single drop shadow: a left/right
    // pair plus one panel-DOWN copy, all in panel space (offsets rotated through
    // the orientation), so it reads the same on any panel rotation and is subtle.
    // Early-returns when a is tiny, so dark wallpapers (low mPs3ShadowAlpha) skip it.
    void   drawTextStroke(const char* s, float x, float y, float scale, float a);
    void   drawIconStroke(unsigned int tex, float x, float y, float w, float h, float a);
    void  ps3BootReset(bool freshSetup);
    bool  ps3BootActive() const { return mPs3BootActive; }
    void  ps3BootSkip();
    bool  ps3BootUpdate(float dtSeconds);   // advances clock; returns true while the XMB UI must stay suppressed
    void  renderPs3BootOverlay();           // logo/footer plate, warning, scene-reveal black wash
    GLuint loadPs3BootPlate(const char* name);
    std::vector<Ps3Cat> mPs3Cats;
    std::vector<Ps3Level> mPs3Stack;   // empty = at category top level
    int mPs3CatIdx = -1;
    int mPs3ItemIdx = 0;          // selection in the top-level item list (per-category)
    std::vector<int> mPs3CatItemSel;   // remembered item selection per category
    // Timed animation state mirroring the web's catAnim / itemAnim model so the
    // motion language matches: a category slide rail with a fade-crossfade, and
    // easeOutBack item navigation. Progress (0 = just started, 1 = settled) is
    // driven by mFrameDt; the input handlers START an animation, render()
    // interpolates it.
    bool  mPs3CatAnimActive = false;
    float mPs3CatT = 1.0f;           // category slide progress
    float mPs3CatFromOffset = 0.0f;  // virtual-px bar offset at t=0 (eases to 0)
    int   mPs3CatOldIdx = 0;         // category slid away from (for the fade-out rail)
    int   mPs3CatOldSel = 0;
    // Item scroll matches the web: each d-pad step restarts a 200ms easeOutCubic
    // from the CURRENT animated position to the new index (mPs3ItemAnimFrom +
    // start time), so holding (with the accelerating auto-repeat) reads as a
    // smooth, snappy, continuously-accelerating scroll. Snapped on category /
    // submenu changes (mPs3ItemAnimStart < 0) so it does not animate across lists.
    float mPs3AnimItem = 0.0f;
    float mPs3ItemAnimFrom = 0.0f;     // animated position when the step started
    float mPs3ItemAnimStart = -1.0f;   // mEffectTime at the step start (<0 = snap)
    float mPs3SubAnim = 0.0f;        // 0 = top level, 1 = in submenu (collapse factor)
    int   mPs3SubDir = 0;            // +1 entering, -1 exiting
    // Timed submenu collapse animation (mirrors the web submenuAnim: 250ms
    // easeOutCubic). On enter/exit we snapshot the parent list + the entered
    // index + the child list so drawParentLayer / the child slide can run
    // continuously through the animation even while the live stack is changing.
    float mPs3SubAnimStart = -1.0f;            // mEffectTime at anim start (<0 = settled)
    std::vector<Ps3Item> mPs3SubParentItems;   // parent (breadcrumb) list snapshot
    int   mPs3SubParentIdx = 0;                // entered index in the parent list
    std::vector<Ps3Item> mPs3SubChildItems;    // child list snapshot (for the exit slide-out)
    GLuint mPs3CatTex[8] = {0, 0, 0, 0, 0, 0, 0, 0};  // PS3 category icons (flat)
    GLuint mPs3CatNmap[8] = {0, 0, 0, 0, 0, 0, 0, 0}; // PS3 category icons (glass nmap)
    // Glass-icon resources (FS_ICON_GLASS). Normal maps are cached by xmb_icon
    // index (PS3 icons -> nmap_NNN.png) and by flat-icon texture id (console /
    // RetroArch icons -> a bevel normal generated from the alpha silhouette).
    std::map<int, GLuint>    mPs3NmapByIcon;     // xmb_icon index -> nmap tex
    std::map<int, GLuint>    mPs3BevelByIconIdx; // console icon idx (0..17) -> bevel nmap
    // Real per-app icons: package name -> full-colour GL texture, decoded from the
    // DE cache /data/system/nano_app_icons/<pkg>.png written by SystemServer. Loaded
    // lazily when the Applications submenu is built; only successes are cached so a
    // not-yet-populated cache is retried on the next open.
    std::map<std::string, GLuint> mPs3AppIcons;
    // Icon-glass shader (distinct from the frosted-glass blur chain's mGlass*).
    GLuint mIconGlassProgram = 0;
    GLint  mIconGlassLocPos = -1, mIconGlassLocIconUV = -1, mIconGlassLocBgUV = -1, mIconGlassLocRot = -1;
    GLint  mIconGlassLocNormal = -1, mIconGlassLocAmb = -1, mIconGlassLocEnv = -1, mIconGlassLocBg = -1;
    GLint  mIconGlassLocLight1 = -1, mIconGlassLocLight2 = -1, mIconGlassLocAmbient = -1;
    GLint  mIconGlassLocSpec = -1, mIconGlassLocRefr = -1, mIconGlassLocRefrScl = -1;
    GLint  mIconGlassLocAttn = -1, mIconGlassLocChanging = -1, mIconGlassLocBgExp = -1;
    GLint  mIconGlassLocBgRad = -1;
    GLuint mIconGlassAmbTex = 0;     // icon_amb.png 16x12 ambient ramp
    GLuint mIconGlassEnvTex = 0;     // texenv.png 64x64 silver matcap
    bool   mIconGlassReady = false;
    bool   mIconGlassTried = false;

    void initPs3Menu();
    void buildPs3Cats();
    Ps3Item makeDataItem(const Ps3DataItem* d);   // runtime item from a DATA node
    void buildDataSubmenu(const Ps3DataItem* node, Ps3Level& out);
    void buildRomSubmenu(int sysIdx, Ps3Level& out);
    void buildRecentSubmenu(Ps3Level& out);
    void buildAppSubmenu(Ps3Level& out);
    std::vector<Ps3Item>& ps3CurItems();   // current visible item list (top or submenu)
    int& ps3CurSel();
    void renderPs3Xmb();
    void drawPs3Clock(float fadeMul);   // U-frame + analog face + DD/M H:MM
    void ps3XmbLeft();
    void ps3XmbRight();
    void ps3XmbUp();
    void ps3XmbDown();
    void ps3XmbSelect();
    void ps3XmbBack();
    // Time Zone 3D-globe selector (NanoMenuPS3Globe.cpp). Shared 1:1 web tzglobe
    // screen used by BOTH the XMB Date and Time -> Time Zone view (mPs3TzActive)
    // and the first-run setup wizard timezone step.
    void openTimezoneGlobe();              // open the XMB Time Zone view
    void closeTimezoneGlobe(bool apply);   // close it (apply = set persist.sys.timezone)
    void tzGlobeNav(int dir);              // move the zone selection + re-aim the globe
    void renderTimezoneGlobe();            // full-screen globe + zone-list chrome (both flows)
    float ps3TzFadeAlpha();                // 0..1 XMB->globe cross-fade (360ms smoothstep)
    void beginTzGlobeFade();               // (re)start the cross-fade + aim the globe at mTzSelected
    // Glass icon pipeline (NanoMenuPS3Icons.cpp).
    void initGlassIcons();                 // compile program, load amb/env textures
    GLuint nmapForIcon(int iconIndex);     // load+cache nmap_NNN.png
    GLuint bevelForIconIdx(int iconIdx);   // bevel normal from a console icon's alpha
    GLuint loadPs3NmapTex(const char* file);             // RGBA normal-map loader
    // Draw an icon with the glass shader (device px coords, like drawIconTex).
    // Refraction samples the live wave (ps3bg work texture) behind the icon.
    void drawGlassIcon(GLuint nmapTex, float x, float y, float w, float h,
                       float cr, float cg, float cb, float alpha);

    // Background scan thread — scans ROM paths off the render thread
    struct BgScanResult {
        std::vector<std::string> roms;
        std::vector<std::string> displayNames;
        std::vector<std::string> activePaths;
        std::string activePath;
        bool valid;              // true if scan found at least one path
    };
    std::mutex mBgScanMutex;
    std::vector<BgScanResult> mBgScanResults; // one per system, guarded by mutex
    bool mBgScanResultReady;                  // set by thread, cleared by render loop
    bool mBgScanThreadRunning;                // true while thread is active
    void bgScanThreadFunc();                  // the thread entry point

    // Settings column. Pinned as the leftmost XMB column at sentinel index -2.
    // Order: -2 Settings | -1 Recently Played | 0..N-1 Systems.
    // The vertical list shows mSettingsItems instead of ROMs when
    // mXmbSystemIndex == -2 (see isOnSettingsColumn()).
    std::vector<SettingsItem> mSettingsItems;
    int mSettingsSelectedIndex;  // cursor inside the settings vertical list

    // Wi-Fi sub-screen state
    std::vector<WifiNetEntry> mWifiEntries;
    int mWifiEntrySelected;
    int mWifiScrollTop;
    int64_t mWifiLastScanMs;
    bool mWifiScanInProgress;       // scan thread is running
    bool mWifiListDirty;            // bg thread produced a fresh list; render should re-read
    std::mutex mWifiListMutex;
    std::thread mWifiScanThread;
    std::string mWifiStatusMsg;
    int64_t mWifiStatusMsgUntilMs;
    // Pending new-network add: when the user picks an unsaved SSID we pop
    // the OSK password prompt, and stash the SSID+security here so the
    // callback can finish the `cmd wifi connect-network` invocation.
    std::string mWifiPendingSsid;
    int mWifiPendingSecurity;
    // Internet Connection Test (Network Settings dialog): an async thread runs the
    // connectivity checks and publishes progressive result text here; renderPs3Dialog
    // copies it into the dialog body each frame while mPs3NetTestLive is set.
    bool mPs3NetTestLive = false;        // the open dialog is the live connection test
    std::atomic<bool> mPs3NetTestActive{false};
    std::string mPs3NetTestBody;
    std::mutex mPs3NetTestMutex;
    std::thread mPs3NetTestThread;

    // ---- Internet Connection Settings wizard (NanoMenuPS3Menu.cpp) -------------
    // 1:1 port of the web NETCONF wireless flow with a real cmd-wifi backend:
    // intro -> method -> connection -> WLAN scan/AP list -> security/key (OSK) ->
    // IP/DNS/MTU/proxy/UPnP (Custom) -> review -> save (connect) -> test. Rendered
    // with the same chrome as the fullscreen dialogs + a horizontal slide.
    bool   mPs3WizActive = false;
    int    mPs3WizExit = 0;               // on close: +1 completed/forward, -1 cancelled/back (for the setup step)
    bool   mSetupNetWizSeen = false;      // setup-wizard tracking of the network step's wizard
    int    mPs3WizId = 0;                 // current screen (WizScreen enum, file-local)
    std::vector<int> mPs3WizStack;        // back stack of screen ids
    int    mPs3WizSel = 0;                // chooser/list selection
    int    mPs3WizScroll = 0;             // scan-list scroll top
    float  mPs3WizAnim = 0.0f;            // open fade 0..1
    float  mPs3WizSlide = 0.0f;           // body slide offset (virtual px), eases to 0
    int    mPs3WizSlideDir = 1;           // +1 forward (enter from right), -1 back
    float  mPs3WizSlideStart = 0.0f;      // mEffectTime at the start of the slide
    float  mPs3WizScreenStart = 0.0f;     // mEffectTime when the current screen opened
    int    mPs3WizTextField = 0;          // which value the open OSK is editing (WizField)
    std::string mPs3WizFieldError;        // inline validation message under the current text field (red)
    // Collected values (mirror web wizState.values):
    std::string mPs3WizSsid;              // chosen SSID
    int    mPs3WizSecTok = 0;             // cmd-wifi security token: 0 open,1 wep,2 wpa2,3 wpa3,4 owe
    std::string mPs3WizSecLabel;          // display security label
    std::string mPs3WizKey;               // WEP/WPA passphrase
    std::string mPs3WizMethod, mPs3WizConn, mPs3WizWlanMode;
    std::string mPs3WizIpMode, mPs3WizDnsMode, mPs3WizMtuMode, mPs3WizProxyMode, mPs3WizUpnp;
    std::string mPs3WizIpAddr, mPs3WizSubnet, mPs3WizRouter, mPs3WizPdns, mPs3WizSdns;
    std::string mPs3WizMtu, mPs3WizProxyAddr, mPs3WizProxyPort;
    std::string mPs3WizOpmode, mPs3WizSpeedDuplex;          // wired op-mode + speed/duplex
    std::string mPs3WizPppoeUser, mPs3WizPppoePass;         // PPPoE
    std::string mPs3WizDhcpHost;                            // DHCP host name
    std::string mPs3WizEapUser, mPs3WizEapPass;             // EAP authentication
    int mPs3WizPendingTextField = -1;   // text-field OSK to open from the render loop
                                        // (deferred so chained text fields don't open
                                        // a new OSK from inside the old OSK's callback)
    // Wizard lifecycle + render (NanoMenuPS3Menu.cpp).
    void startNetWizard();
    void startDateTimeWizard(int mode);   // 0 = Set via Internet, 1 = Set Manually (reuses net-wizard UI)
    void wizEnter(int id, int dir);       // push/go to a screen
    void wizConfirm();                    // X / Enter
    void wizBack();                       // O / Back
    void wizNav(int dir, bool horizontal);// Up/Down/Left/Right
    void wizOpenTextField(int field);     // open the OSK for a value field
    std::string validateWizField(int field, const std::string& val);  // "" = ok, else error message
    void wizRescan();                     // X: re-scan APs on the scan-list screen
    int  wizNextScreen(int id, int sel);  // forward-nav table (commits the choice)
    void renderNetWizard();

    // Bluetooth sub-screen state
    std::vector<BtDevEntry> mBtEntries;
    int mBtEntrySelected;
    int mBtScrollTop;
    int64_t mBtLastScanMs;
    bool mBtScanInProgress;
    bool mBtDiscoveryInProgress;
    bool mBtListDirty;
    std::mutex mBtListMutex;
    std::thread mBtScanThread;
    std::thread mBtDiscoveryThread;
    std::string mBtStatusMsg;
    int64_t mBtStatusMsgUntilMs;

    // OSK password mode: when active, keystrokes append to mOskQuery, but
    // the HUD renders masked chars. On Enter, mOskPasswordCallback fires
    // with the raw string and the overlay closes. Reusing mOskActive so the
    // existing render path still handles dismissal + keyboard grid.
    bool mOskPasswordMode;
    bool mOskPlaintext;
    std::string mOskPasswordPrompt;
    std::function<void(const std::string&)> mOskPasswordCallback;
    // Numeric field auto-formatting: 0 = free text, 1 = date (YYYY/MM/DD),
    // 2 = time (HH:MM). For 1/2 the OSK accepts digits only and inserts the
    // separators automatically as the user types (real-IME field behaviour).
    int mOskFieldFmt = 0;

    // Hierarchical settings tree
    std::vector<SettingNode> mSettingsNodes;
    std::vector<int> mSettingsNavStack;
    std::vector<int> mSettingsTreeVisible;
    int mSettingsTreeSelected;
    int mSettingsTreeScrollTop;
    int mSettingsEditNodeIdx;
    bool mSettingsValuesDirty;
    mutable std::mutex mSettingsValueMutex;
    std::unordered_map<int, std::string> mSettingsValueCache;

    // Icon rendering
    void initIconTextures();
    void drawIcon(int iconIdx, float x, float y, float size,
                  float r, float g, float b, float a);
    // Draw an arbitrary GL texture handle (PS3 category icons live outside
    // mIconTextures[]). Supports a non-square w/h. (NanoMenuPS3Menu.cpp)
    void drawIconTex(GLuint tex, float x, float y, float w, float h,
                     float r, float g, float b, float a);
    GLuint mIconTextures[18]; // 0-14=systems, 15=history, 16=game item, 17=setting

    // On-screen keyboard. mOskActive + mOskQuery are the keep-stable members
    // external code reads/writes directly; all new runtime state is in mOsk.
    bool mOskActive;           // OSK is visible and receiving input
    bool  mOskGlassValid = false;  // OSK frosted-panel blur cached (avoid per-frame full-FB resolve)
    float mOskGlassT = 0.0f;       // mEffectTime of the last OSK panel capture
    std::string mOskQuery;     // current typed buffer (committed text)
    NanoOskState mOsk;         // page/shift/focus/caret/popup/candidates/IME state
    std::vector<SearchResult> mSearchResults;
    int mSearchSelectedIndex;
    bool mSearchActive;        // Search results being displayed

    // FreeType font rendering
    static const int MAX_FT_FACES = 8;
    FT_Library mFtLib;
    FT_Face mFtFaces[MAX_FT_FACES];
    int mFtNumFaces;
    int mFontSize;  // render pixel size
    GLuint mGlyphAtlasTex;
    int mAtlasW, mAtlasH;
    int mAtlasCurX, mAtlasCurY, mAtlasRowH;
    std::unordered_map<uint32_t, GlyphInfo> mGlyphCache;

    // Text shader (per-vertex color for emoji support)
    GLuint mTextProgram;
    GLint  mTextLocPosition;
    GLint  mTextLocTexCoord;
    GLint  mTextLocColor;
    GLint  mTextLocTexture;
    GLint  mTextLocRotation;

    // Rounded-rect shader (OSK keys)
    GLuint mRoundProgram;
    GLint  mRoundLocPosition;
    GLint  mRoundLocLocal;
    GLint  mRoundLocRotation;
    GLint  mRoundLocHalf;
    GLint  mRoundLocRadius;
    GLint  mRoundLocColor;
    // Frosted-glass shader (OSK panel) + framebuffer snapshot texture
    GLuint mGlassProgram;
    GLint  mGlassLocPosition;
    GLint  mGlassLocLocal;
    GLint  mGlassLocTexCoord;
    GLint  mGlassLocRotation;
    GLint  mGlassLocHalf;
    GLint  mGlassLocRadius;
    GLint  mGlassLocTexture;
    GLint  mGlassLocTexel;
    GLint  mGlassLocTint;
    GLint  mGlassLocAlpha;
    GLint  mGlassLocTonemap;
    GLuint mGlassTex;
    int    mGlassTexW, mGlassTexH;
    // Dual-Kawase downsample blur for the frosted panel: render the full-res
    // capture down through box-filter passes into progressively smaller FBO
    // textures, then upsample with a tent filter in the panel pass. Every source
    // pixel contributes (unlike sparse single-pass taps), so the result is a
    // smooth aero-glass blur instead of a pixelated/ghosted one.
    GLuint mGlassDownProgram      = 0;
    GLint  mGlassDownLocPosition  = -1;
    GLint  mGlassDownLocTexCoord  = -1;
    GLint  mGlassDownLocTexture   = -1;
    GLint  mGlassDownLocHalfpixel = -1;
    GLint  mGlassDownLocOffset    = -1;
    GLuint mGlassFbo[4]      = {0, 0, 0, 0};   // 4 down levels: 1/2..1/16
    GLuint mGlassDownTex[4]  = {0, 0, 0, 0};
    int    mGlassDownW[4]    = {0, 0, 0, 0};
    int    mGlassDownH[4]    = {0, 0, 0, 0};
    // Separable Gaussian (H then V) run on the 1/8 downsample, ping-ponging
    // between two scratch buffers so we never sample and render the same texture
    // in one pass. mGlassGaussTex[1] is the final blur source.
    GLuint mGlassGaussProgram     = 0;
    GLint  mGlassGaussLocPosition = -1;
    GLint  mGlassGaussLocTexCoord = -1;
    GLint  mGlassGaussLocTexture  = -1;
    GLint  mGlassGaussLocDir      = -1;
    GLuint mGlassGaussFbo[2]      = {0, 0};
    GLuint mGlassGaussTex[2]      = {0, 0};
    int    mGlassGaussW[2]        = {0, 0};
    int    mGlassGaussH[2]        = {0, 0};
    GLuint mGlassBlurTex     = 0;   // final blurred texture sampled by the panel
    int    mGlassBlurW       = 0;
    int    mGlassBlurH       = 0;
    // PS3 XMB submenu depth-of-field cache: the capture+blur chain is expensive
    // (~26ms), so capture exactly once per submenu visit and reuse the cached
    // blur for every frame (the panel draw itself is cheap). Invalidated on
    // returning to the top level.
    bool   mPs3GlassValid      = false;
    float  mPs3GlassBlurT      = 0.0f;   // mEffectTime of last submenu wave-blur (30Hz cadence)
    float  mPs3DlgBlurT        = 0.0f;   // mEffectTime of last dialog backdrop wave-blur
    // Run the downsample + Gaussian passes on srcTex (srcW x srcH); leaves the
    // result in mGlassBlurTex. Called by captureGlass (FB snapshot) and
    // captureGlassFromWave (ps3bg::workTex, no FB capture).
    void   blurGlassChain(GLuint srcTex, int srcW, int srcH,
                          int downLevels = 3, int gaussIters = 2);
    // Blur the live PS3 wave/gradient (ps3bg::workTex) with no framebuffer
    // capture - cheap enough to sustain 60fps. Result is in LOGICAL orientation;
    // draw with drawFrostedGlass(..., waveSpace=true). False if wave not ready.
    bool   captureGlassFromWave();
    // OSK rounded-rect + frosted-glass primitives (NanoMenuRender.cpp)
    void drawRoundedRect(float x, float y, float w, float h, float radius,
                         float r, float g, float b, float a);
    void drawTriangle(float x0, float y0, float x1, float y1, float x2, float y2,
                      float r, float g, float b, float a);
    // Snapshot the framebuffer region [x,y,w,h] (logical px) into mGlassTex.
    // Returns false if capture is unavailable (e.g. active DRM GL rotation).
    bool captureGlass(float x, float y, float w, float h);
    // Draw a frosted-glass panel over the captured region (call captureGlass
    // first with the same rect). tint rgb darkens; tintA = panel opacity.
    void drawFrostedGlass(float x, float y, float w, float h, float radius,
                          float tr, float tg, float tb, float tintA, float fade,
                          bool waveSpace = false);

    // Setup wizard state
    bool mSetupWizardActive;
    SetupWizardStep mSetupStep;
    float mSetupTransitionAlpha;
    float mSetupSlideOffset;
    bool mSetupTransitioning;
    SetupWizardStep mSetupTransitionTarget;
    bool mSetupBootWaited;
    // Language selection
    int mLangSelected;
    int mLangScrollTop;
    // Welcome greeting animation (iOS-style cycling)
    int mGreetingIndex;
    float mGreetingTimer;
    float mGreetingFade;     // 0..1 current greeting opacity
    bool mGreetingFadingOut; // true = fading out, false = fading in / holding
    int mGreetingTransType;  // randomized transition style (0-5)
    // Timezone
    std::vector<TimezoneEntry> mTzEntries;
    int mTzSelected;
    int mTzScrollTop;
    // Time Zone 3D-globe selector state (shared by the setup wizard step + the
    // XMB Date and Time -> Time Zone view). mPs3TzActive gates the XMB view;
    // mTzGlobeFadeStart drives the 360ms XMB->globe cross-fade (mEffectTime when
    // the screen opened, <0 = no fade / fully shown).
    bool mPs3TzActive = false;
    float mTzGlobeFadeStart = -1.0f;
    int mTzGlobeWarmFrames = -1;  // >=0 while warming up (loading textures): hold the
                                  // globe on black until ready so the one-time texture
                                  // load spike isn't charged against the 360ms fade
    int mTzSelOnOpen = 0;         // mTzSelected when the globe opened (restore on cancel)
    GLuint mPs3TzHeaderTex = 0;   // cached xmb_icon_022 colour texture (header glyph)
    // Setup script log tailing
    std::vector<std::string> mSetupLogLines;
    int mSetupLogScrollTop;
    bool mSetupScriptRunning;
    bool mSetupScriptDone;
    std::thread mSetupLogThread;
    std::mutex mSetupLogMutex;
    bool mSetupLogExitRequested;
};

} // namespace android

#endif // GAMMAOS_NANO_MENU_H
