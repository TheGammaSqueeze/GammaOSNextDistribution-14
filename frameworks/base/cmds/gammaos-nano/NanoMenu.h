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
static const int NUM_EFFECTS = 21; // total effect IDs (some disabled)

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

    // On-screen keyboard (search)
    void openOsk();
    void closeOsk();
    void oskType(char c);
    void oskBackspace();
    void oskConfirm();
    void updateSearchResults();
    void renderOsk();

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
    void renderPasswordPromptOverlay();
    std::string maskPassword(const std::string& s);

    // Quick Resume
    void prepareShutdown(const char* action);
    bool isRetroArchRunning();

    // Brightness control
    void adjustBrightness(int direction);
    bool setBrightnessViaHal(int brightness);
    void renderBrightnessBar();

    // Volume control
    void adjustVolume(int direction);
    void renderVolumeBar();
    int readSysfsInt(const char* path, int fallback);
    void writeSysfsInt(const char* path, int value);

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

    // XMB background shader (PS3-style volumetric ribbons, 60fps)
    GLuint mXmbProgram;
    GLint  mXmbLocPosition;
    GLint  mXmbLocTime;
    GLint  mXmbLocResolution;
    GLint  mXmbLocRotation;
    GLint  mXmbLocCoordSwap;

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
    std::string mOskPasswordPrompt;
    std::function<void(const std::string&)> mOskPasswordCallback;

    // Icon rendering
    void initIconTextures();
    void drawIcon(int iconIdx, float x, float y, float size,
                  float r, float g, float b, float a);
    GLuint mIconTextures[18]; // 0-14=systems, 15=history, 16=game item, 17=setting

    // On-screen keyboard (search)
    bool mOskActive;           // OSK is visible and receiving input
    bool mOskShift;            // true = uppercase letters (A-Z); false = lowercase
    std::string mOskQuery;     // Current search query
    int mOskCursorX;           // OSK grid cursor column
    int mOskCursorY;           // OSK grid cursor row
    std::vector<SearchResult> mSearchResults;
    int mSearchSelectedIndex;
    bool mSearchActive;        // Search results being displayed

    // FreeType font rendering
    static const int MAX_FT_FACES = 4;
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
};

} // namespace android

#endif // GAMMAOS_NANO_MENU_H
