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
#include <unordered_map>
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
    void connectToSavedWifi(int savedNetId);
    void addAndConnectWifi(const std::string& ssid, int security,
                           const std::string& password);
    void forgetWifiNetwork(int savedNetId);
    void toggleWifiRadio(bool on);

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
    };
    struct Ps3Item {
        std::string label;
        std::string desc;
        std::string value;
        std::string payloadStr;
        GLuint iconTex;     // 0 = none
        float iconR, iconG, iconB;  // tint (1,1,1 default)
        int kind;
        int a, b;
    };
    struct Ps3Cat {
        std::string name;
        GLuint iconTex;
        std::vector<Ps3Item> items;
    };
    struct Ps3Level {       // a submenu level on the navigation stack
        std::string title;
        std::vector<Ps3Item> items;
        int sel;
    };
    bool mPs3Xmb = false;         // persist.gammaos.nano.ps3xmb
    bool mPs3MenuBuilt = false;
    std::vector<Ps3Cat> mPs3Cats;
    std::vector<Ps3Level> mPs3Stack;   // empty = at category top level
    int mPs3CatIdx = -1;
    int mPs3ItemIdx = 0;          // selection in the top-level item list (per-category)
    std::vector<int> mPs3CatItemSel;   // remembered item selection per category
    float mPs3AnimCat = 0.0f;    // animated horizontal position (lerps to mPs3CatIdx)
    float mPs3AnimItem = 0.0f;   // animated vertical position
    float mPs3SubAnim = 0.0f;    // 0 = top level, 1 = in submenu (collapse factor)
    int   mPs3SubDir = 0;        // +1 entering, -1 exiting
    GLuint mPs3CatTex[8] = {0, 0, 0, 0, 0, 0, 0, 0};  // PS3 category icons

    void initPs3Menu();
    void buildPs3Cats();
    void buildRomSubmenu(int sysIdx, Ps3Level& out);
    void buildRecentSubmenu(Ps3Level& out);
    void buildAppSubmenu(Ps3Level& out);
    std::vector<Ps3Item>& ps3CurItems();   // current visible item list (top or submenu)
    int& ps3CurSel();
    void renderPs3Xmb();
    void ps3XmbLeft();
    void ps3XmbRight();
    void ps3XmbUp();
    void ps3XmbDown();
    void ps3XmbSelect();
    void ps3XmbBack();

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
    void   blurGlassChain();        // run the downsample passes after captureGlass
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
                          float tr, float tg, float tb, float tintA, float fade);

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
