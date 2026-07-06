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
#include <memory>
#include <mutex>
#include <thread>
#include <deque>
#include <condition_variable>

#include "NanoMenuSettingsTree.h"
#include "NanoOsk.h"
#include "NanoAudio.h"
#include "NanoTsDemux.h"
#include "NanoAviDemux.h"
#include "NanoScraper.h"

#include <ft2build.h>
#include FT_FREETYPE_H

#include <utils/Thread.h>
#include <binder/IBinder.h>

#include <EGL/egl.h>
#include <GLES2/gl2.h>

class NanoVideo;   // global; HW video decoder (NanoVideo.h). Forward-declared to keep gui/ headers out of NanoMenu.h.
class NanoDvbSub;  // global; DVB bitmap subtitle decoder (NanoDvbSub.h).
typedef struct AMediaFormat AMediaFormat;   // NDK media format (kept opaque here; pointer member only).

namespace android {

class Surface;
class SurfaceComposerClient;
class SurfaceControl;

// Release/restore the Bluetooth bluesleep LPM wakelock around the framework-owned
// (overlay / SurfaceFlinger) screen-off path so a held bluesleep does not block
// suspend-to-RAM while an app is foreground. Defined in NanoMenuInput.cpp; mirrors
// the enterDrmSleep fix for nano's own DRM-home sleep. `disabled` is the caller's latch.
void nanoBtLpmSuspendGate(bool screenOff, bool& disabled);

// Adapter that lets the shared NanoSliderHud spec draw through NanoMenu's
// private GL primitives (drawQuad/drawText/measureText). Defined in
// NanoMenuSystem.cpp; friended so the volume/brightness HUD matches the
// drastic-nano overlay 1:1.
struct NanoMenuSliderBackend;

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

    // Shared volume/brightness HUD adapter (see NanoSliderHud.h).
    friend struct NanoMenuSliderBackend;

    sp<SurfaceComposerClient> session() const;

    // Overlay XMB: run as the power-hold in-game overlay (a translucent,
    // background-blurred SurfaceFlinger layer over the running app) instead of
    // the normal full-screen home. Set from main() before run() when invoked
    // with --overlay. See NanoMenuOverlay.cpp.
    void setOverlayMode(bool on) { mOverlayMode = on; }

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

    // A user-chosen ROM scan location for a system. type 0 = raw filesystem
    // path, type 1 = SAF document-tree URI (resolved to rawHint for native
    // readdir, see the folder picker). Built-in systems with an empty
    // scanSources list fall back to the legacy default candidate paths.
    struct ScanSource {
        int type = 0;            // 0 = rawpath, 1 = safuri
        std::string value;       // raw absolute path, or content:// tree URI
        std::string rawHint;     // resolved raw mount for a safuri (optional)
    };

    // Launch routing discriminator (persisted as launchType in the config).
    enum XmbLaunchType {
        XLT_LIBRETRO_CORE = 0,   // RetroArch native-core / QR cache route (today's RA path)
        XLT_RETROARCH_INTENT = 1,// RetroArch via an am intent (content URI / extras)
        XLT_CUSTOM_PACKAGE = 2,  // arbitrary package via am intent (Daijisho-style)
    };

    struct XmbSystem {
        std::string id;            // stable unique key; for built-ins == original romDir
        bool builtin = true;       // seeded from kXmbSystemDefs (enables "reset to default")
        bool enabled = true;       // disabled systems are hidden from Game and never scanned
        int order = 0;             // explicit sort key within the Game category
        std::string name;
        std::string shortname;
        std::string romDir;        // Directory name under ROMs/ (also default scan-path key)
        std::string coreSo;        // RetroArch core .so filename (empty for standalone)
        std::string launchPkg;     // Package name for standalone emulators
        std::string launchIntent;  // Intent template ({file.uri} placeholder)
        int launchType = XLT_LIBRETRO_CORE;
        std::string packageName;   // custom-package target (mirrors launchPkg)
        std::string launchArgs;    // extra am tokens appended to the intent (custom-package)
        std::string iconRef;       // builtin:N | retroarch:name | core:name | file:/abs | ""
        std::vector<ScanSource> scanSources; // user-chosen scan locations (empty = legacy default)
        float iconR = 1.0f, iconG = 1.0f, iconB = 1.0f; // Icon color (tint)
        std::string acceptExts;    // Comma-separated accepted extensions
        std::string activePath;    // Primary path (largest collection) — for backward compat
        std::vector<std::string> activePaths; // ALL directories with ROMs for this system
        std::vector<std::string> roms;         // Sorted FULL PATHS (e.g., /storage/UUID/nes/game.nes)
        std::vector<std::string> displayNames; // Pre-stripped display names (parallel to roms)
        bool scanned = false;
        bool pathExists = false;
        int64_t lastScanTime = 0; // elapsedRealtime() of last scan - for periodic rescan
        // Boxart/cover scraper per-system overrides (empty = inherit the global
        // Settings). scraperOverride: ""=default, "screenscraper", "thegamesdb",
        // "off". scrapeUser/scrapePass override the ScreenScraper account; scrapeKey
        // overrides the TheGamesDB key; scrapePlatform forces a ScreenScraper
        // systemeid when the auto romDir->platform map is wrong.
        std::string scraperOverride;
        std::string scrapeUser;
        std::string scrapePass;
        std::string scrapeKey;
        std::string scrapePlatform;
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
        SETUP_TIMEZONE,
        SETUP_WIFI,
        SETUP_BLUETOOTH,
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
        int  cod = 0;   // Bluetooth class-of-device (for the "Type" column)
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
    bool selectKeyHeld() const;   // live SELECT state via EVIOCGKEY (not the sticky flag)
    // DRM-direct sleep: blank panels + backlights, drive PowerManager
    // suspend, block until a wake (power press or lid open), then
    // re-commit the modeset and restore. Shared by the power short-press
    // and the lid-close handlers. Returns false if the legacy
    // pre-boot_completed timeout initiated a shutdown (caller returns).
    bool enterDrmSleep();
    // CPU clock/governor while the screen is off: drop to powersave on screen-off and
    // re-apply the user's persisted performance mode on wake (NanoMenu.cpp).
    void nanoApplyPerfClock(const char* mode);   // run /vendor/bin/setclock_<mode>.sh (validated)
    void nanoRestorePerfClock();                 // re-apply persist.gammaos.performance_mode
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
    // Dynamic systems config (/data/system/nano_systems.json, DE storage).
    // loadSystemsConfig parses the JSON into mXmbSystems; seedSystemsConfig
    // builds the default config from kXmbSystemDefs (+ legacy props) and
    // writes it; saveSystemsConfig serializes mXmbSystems back atomically.
    bool loadSystemsConfig();
    void seedSystemsConfig();
    void saveSystemsConfig();
    // Cross-process config coherence: nanosecond mtime stamp of
    // nano_systems.json (-1 when absent) and the stamp of this process's last
    // load/save. The threadLoop polls the file and reloads when an external
    // nano (overlay vs DRM home) rewrote it.
    int64_t systemsConfigStamp() const;
    int64_t mSystemsCfgStamp = -1;
    // Consolidated ROM scan candidate-path builder (replaces the duplicated
    // logic in scanRomPaths / scanOneSystemAsync / bgScanThreadFunc). Honors
    // scanSources when present, else reproduces the legacy default candidates.
    std::vector<std::string> buildScanCandidates(const XmbSystem& sys);
    // DE cache path for a system's ROM list, keyed on the stable id.
    std::string xmbCachePath(const XmbSystem& sys) const {
        return "/data/system/nano_xmb_cache/" + sys.id + ".list";
    }
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
    void oskTouchFrame();                   // SYN_REPORT: normalize + dispatch the live touch
    void oskTouchAt(float px, float py, bool tap); // hit-test a logical point against the keys
    // XMB touch navigation (NanoMenuPS3Menu.cpp). Swipe left/right to change
    // category, swipe up/down to scroll the item list with inertial momentum, tap
    // to open an item/submenu, long-press to open the option side-menu, tap side-
    // menu rows. Reuses the OSK raw-touch mapping (swap/flipX/flipY), so it is
    // correct on both the DRM and SF back-ends.
    void  xmbTouchFrame();                    // SYN_REPORT: gesture recognition + dispatch
    bool  xmbTouchLive() const;               // true when the XMB home/submenu/opt owns touch
    void  xmbTouchTap(float px, float py);    // single tap -> category jump / open item
    void  xmbTouchLongPress(float px, float py); // hold -> open the option side-menu for the item
    int   xmbTouchItemAt(float vy);           // item row nearest a virtual y (-1 = none in range)
    void  xmbTouchSettleItem();               // snap the momentum scroll onto the nearest item
    void  xmbCancelTouchScroll();             // stop an in-flight inertial fling (dpad takeover)
    void  xmbTouchOptHover(float px, float py); // slide over the option panel: highlight a row
    void  xmbTouchOptTap(float px, float py);   // tap the option panel: activate a row / dismiss
    // Dialog touch (system-update / confirm / chooser / slider / info pages). Taps
    // set mPs3DlgSel then reuse ps3XmbSelect (apply) / ps3XmbBack (cancel); drags
    // scroll info pages, adjust the side-panel slider, or hover chooser rows.
    void  xmbDialogTouchTap(float px, float py);  // tap a dialog button/option/footer -> apply/cancel
    void  xmbDialogTouchDrag(float px, float py); // drag in a dialog -> scroll / slider / chooser hover
    void  dlgFullscreenXform(float& S, float& offX, float& offY) const; // reconstruct renderPs3Dialog's local kind-0 transform
    // Media-player touch (Gallery / YouTube / YouTube Music style). Each reuses the
    // shared raw-digitizer -> logical mapping, then hit-tests its own controls and
    // drives the same vid*/mp*/pv* handlers the D-pad uses. Each includes an on-screen
    // way to exit the player (swipe-down).
    bool  touchLogicalPx(float& px, float& py);   // shared raw digitizer -> logical pixel (swap/flip; DRM+SF)
    void  pvTouchFrame();                          // photo viewer: swipe prev/next, tap controls, swipe-down exit
    void  vidTouchFrame();                         // video player: tap controls, drag-scrub the seek bar, exit
    void  mpTouchFrame();                          // music player: play/pause, drag-scrub, next/prev, exit
    int   photoGridCellAt(float px, float py);     // thumbnail under a touch (-1 = none)
    void  photoGridScrollTo(int topRow);           // clamp + set the grid's top visible row
    void  photoGridScrollDrag(float downPy, float py, int anchorTop); // drag-scroll the grid
    void  photoGridOpenAt(int idx);                // select + open a thumbnail into the viewer
    void  photoGridBack();                         // close the thumbnail grid (touch back button)
    bool  photoGridBackHit(float px, float py);    // top-left back chevron hit-test
    int   xmbOptRowAt(float px, float py) const; // option-panel row under a touch (-1 = outside)
    float xmbOptRowY(int row) const;          // virtual y of an option-panel main row
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
    std::string buildSysInfoBody();      // real build/model/serial/MAC/IP/storage for System Information
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
    void startSetupWizard();
    void finishSetupWizard();
    void renderSetupWizard();
    // Overlay a "Start: Skip" hint (top-right) on the Wireless / Bluetooth steps
    // so the user knows Start skips them. Drawn on top of the net/BT wizard.
    void drawSetupSkipHint();
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
    // Rasterize (if needed) and return the glyph cached at rasterPx device px.
    // Mono glyphs are rendered at that exact size (crisp, evenly hinted, no
    // fractional bitmap scaling); color emoji use their fixed strike normalized
    // to mFontSize regardless of rasterPx. Returns nullptr if unavailable.
    const GlyphInfo* ensureGlyph(uint32_t codepoint, int rasterPx);
    // Toggle anti-aliased (mipmapped) minification on the glyph atlas. Scoped to
    // the home-XMB menu content only (see renderPs3Xmb); off for dialogs, OSK,
    // setup wizard and legacy menus. Filter is texture-object state, so one call
    // per region covers every drawText within it.
    void setGlyphAtlasAA(bool on);
    void drawText(const char* str, float px, float py, float scale,
                  float r, float g, float b, float a);
    float measureText(const char* str, float scale);
    // Enable a scissor covering the LOGICAL rect (x,y,w,h), correct for any
    // panel rotation AND flip (transforms the rect through sDrmRotMat, the
    // same composed matrix the vertex shaders apply). See the definition for
    // why the old per-site rotation switches were wrong on flipped panels.
    void scissorLogicalRect(float x, float y, float w, float h);
    void render();
    void drawQuad(float x, float y, float w, float h,
                  float r, float g, float b, float a);
    // Selected-label glow: lay the glyphs out ONCE and emit all 15 offset copies
    // (8 outer ring + 6 inner ring + 1 centre) in a single draw, instead of 15
    // separate drawText calls that each re-decode and re-lay-out the same string.
    // Bit-identical: each copy re-runs drawText's exact advance accumulation from
    // its own (px+dx, py+dy), only the glyph cache lookups are shared.
    void drawTextGlow(const char* str, float px, float py, float scale,
                      float oR, float iR, float outerA, float innerA, float mainA);
    // Flat-colour batch: while active, drawQuad/drawTriangle accumulate their NDC
    // vertices + per-vertex colour into one buffer instead of issuing a draw each;
    // flushSolidBatch() submits them in one glDrawArrays through mParticleProgram
    // (same uRotation as mShaderProgram, set per frame). Same vertices, submission
    // order and blend as the immediate path, so the composite is identical. Used
    // to collapse the clock chrome's ~230 tiny draws into one.
    void beginSolidBatch();
    void flushSolidBatch();
    void endSolidBatch();
    // GammaOS: Create EGL surfaces on every non-primary physical display so the
    // post-HWC render loop can drive wallpaper-only rendering on those panels.
    // Idempotent — first call wires the surfaces, subsequent calls are no-ops.
    void setupSecondaryEglSurfaces();
    // Deferred SF init: create SurfaceComposerClient + SF surface when
    // transitioning from DRM boot path to app launch.
    void initSurfaceFlingerPath();

    // ---- Overlay XMB (NanoMenuOverlay.cpp) ----------------------------------
    // The power-hold in-game overlay: the same PS3 XMB renderer presented on a
    // translucent, SF-background-blurred layer over the running app (the app
    // keeps running, SurfaceFlinger composites the blur). Gated by
    // persist.gammaos.nano.overlay; the layer is created hidden and toggled by
    // sys.gammaos.nano.show_overlay (set by PhoneWindowManager on power-hold).
    bool mOverlayMode = false;        // this process is the overlay instance
    bool mOverlayShown = false;       // overlay layer is currently visible + grabbing input
    bool mOverlayPagesLocked = true;  // overlay RSS pinned by the startup mlockall; released by
                                      // munlockall once parked behind an app, then left reclaimable
                                      // (demand-faults from zram on raise; NOT re-locked - see overlayShow)
    // OSK-over-app: an app (GammaBrowser web fields) requests nano's lightweight OSK
    // because the framework leanback IME (~130MB) gets OOM-killed on this 1GB device
    // under a heavy WebView. We raise the overlay in an OSK-only mode and hand the
    // typed text back via a file + the sys.gammaos.nano.osk_done prop.
    bool mOskOverApp = false;         // overlay is up purely to host an app's OSK request
    std::string mOskAppReqId;         // request id echoed back in osk_done
    std::string mOskAppDir;           // requesting app's files dir (in/out text files)
    uint32_t mOskAppGen = 0;          // live-typing generation counter for the app OSK
    std::string mOskAppLastBuf;       // last buffer streamed to the app (per-frame change gate)
    void oskPublishLive();            // stream the current OSK buffer to the app for live typing
    bool mOverlayInited = false;      // one-time blur/hide transaction applied
    int  mOverlayInitTries = 0;       // init retries while SF surface control is still null
    int  mOverlayBlurPx = 0;          // background blur radius (px), 0 if unsupported
    bool mOverlayOpaque = false;      // persist.gammaos.nano.overlay.opaque: opaque layer
                                      // (HWC direct scanout -> 60fps) vs translucent+SF blur
    // Universal performance hints (so the vendor power/DVFS stack can run nano's
    // UI in a higher mode without device-specific frequency writes). mHintSession
    // is an APerformanceHintSession* (void to keep the NDK header out of this
    // header). See perfHintInit/perfHintReport in NanoMenu.cpp. Each hint is
    // independently prop-gated for A/B testing:
    //   persist.gammaos.nano.perf.framerate  - setFrameRate(60) on the overlay layer
    //   persist.gammaos.nano.perf.adpf        - ADPF render-thread hint session
    //   persist.gammaos.nano.perf.fixedperf   - IPower FIXED_PERFORMANCE while shown
    void*  mHintSession = nullptr;
    bool   mHintTried = false;
    void   perfHintInit(int tid);     // create the ADPF session for the render thread
    void   perfHintReport();          // report this frame's work duration to ADPF
    bool mOverlayWallpaper = false;   // overlay is showing the FULL PS3 wallpaper (no app behind,
                                      // or a submenu is open) vs the scrim-over-live-app top level
    bool mOverlayPendingShow = false; // defer the SF t.show() to after the first faded-out frame is
                                      // composited, so the entrance animates in (no stale-buffer flash)
    // Enable blend for UI chrome. In overlay (translucent SF) mode use a SEPARATE
    // alpha term (GL_ONE, GL_ONE_MINUS_SRC_ALPHA) so the framebuffer alpha
    // accumulates toward 1 for opaque chrome - otherwise GL_SRC_ALPHA
    // UNDER-accumulates alpha and SurfaceFlinger bleeds the live app through
    // "white" text/icons. The home DRM path keeps the plain straight blend.
    void setUiBlend();
    void overlayInitLayer();          // create the translucent layer + initial hide (once)
    void overlayPoll();               // watch show_overlay; drive show/hide each frame
    void overlayOskPoll();            // watch osk_req; host an app's OSK over the live app
    void overlayShow();               // raise layer + drop_input=1 + reset to XMB top
    void overlayHide();               // hide layer + drop_input=0
    // Apply the mode-dependent presentation state (layer opaque flag + EGL swap
    // interval) for the CURRENT mOverlayWallpaper. Must run on the render thread
    // (eglSwapInterval needs the context current). Called from overlayShow and
    // from every path that flips wallpaper/scrim without a hide+show cycle
    // (overlayQuitToHome's quit-to-launcher switch).
    void overlayApplyPresentMode();
    // Resolve the current foreground (resumed) package via ActivityManager, with
    // the same "real 3rd-party app" validation overlayPauseApp applies. Empty if
    // none. Populated on every overlayShow() so quit/launch always have a target
    // regardless of whether the pause feature is enabled.
    std::string overlayResolveForegroundPkg();
    // GammaOS Nano orientation control. orientationTick() runs on the ~0.5s
    // cadence in both the home and overlay processes; it computes a single
    // foreground-aware orientation token (nano's own setting when the menu or
    // overlay is foreground, a per-app override or "none" when an app is) and
    // publishes it to sys.gammaos.nano.force_orientation, which WindowManager
    // reads in mapOrientationRequest. appOrientGet returns the stored override
    // for a package (empty = none). See NanoMenuOrientation.cpp.
    void orientationTick();
    // SF overlay only: keep the overlay's render surface sized to the display's
    // current logical size, so when nano forces a portrait orientation the XMB
    // reflows to a real portrait layout instead of a rotated/truncated landscape
    // one. Queries the display's layerStackSpaceRect each shown frame and resizes
    // the SurfaceControl buffers + mWidth/mHeight when it changes.
    void overlayUpdateSurfaceSize();
    std::string appOrientGet(const std::string& pkg);
    void appOrientSet(const std::string& pkg, const std::string& value);
    void appOrientLoad();
    void appOrientSave();
    std::string mLastOrientToken;                 // last force_orientation we wrote
    std::map<std::string,std::string> mAppOrient; // package -> orientation override
    bool mAppOrientLoaded = false;
    int64_t mAppOrientStamp = -1;                 // nano_app_orient.json mtime (cross-process reload)
    // Freeze/thaw the foreground app while the overlay is up (SIGSTOP/SIGCONT of
    // its process group), so gameplay + audio pause and its GPU/CPU is freed -
    // the real PS3 in-game XMB pauses the title. Gated by
    // persist.gammaos.nano.overlay.pause (default on); the frozen window keeps
    // its last frame for SurfaceFlinger to blur.
    void overlayPauseApp(bool pause);
    std::string mOverlayPausedPkg;    // package frozen on show, thawed on hide
    // Overlay XMB actions (NanoMenuOverlay.cpp), invoked from the PS3 input
    // handlers when mOverlayMode. Resume = dismiss + thaw the running app; quit =
    // force-stop it and fall back to the home XMB; launch = force-stop the
    // current app and start a new package (the running one is replaced).
    void overlayResume();
    void overlayQuitToHome();
    bool overlayLaunchPackage(const std::string& pkg);
    // Shared overlay launch primitive: cleanly exit whatever is running (ESC +
    // save-state wait for RetroArch/DraStic, force-stop for other apps), then run
    // the prebuilt `am start ...` command for the new target. Tracks launch_app +
    // app_launched=1 so the framework (RootWindowContainer / PhoneWindowManager)
    // detects the new app's exit and raises the overlay launcher again. Runs the
    // exit+launch on a detached thread so the overlay keeps animating; overlayPoll
    // dismisses onto the new app once it resumes.
    void overlayLaunchCommand(const std::string& pkg, const std::string& amCmd);
    // Launch the currently selected XMB game/ROM from the overlay (resolves the
    // emulator package for the selected system/recent entry, then reuses
    // overlayLaunchPackage). Routed from ps3XmbSelect's PS3_ROM/PS3_RECENT cases
    // when mOverlayMode (the home-mode launchXmbGame() must not run in the overlay).
    void overlayLaunchGame();
    // True while mOverlayMode and at the XMB top level with no dialog/submenu, so
    // Back resumes the game rather than doing nothing.
    bool overlayAtTopLevel() const;
    // Capture the current screen (the just-frozen app) with screencap and load
    // it into mOverlayBgTex, used as the static, blurred+tinted opaque overlay
    // background (the task-switcher snapshot model). Implemented in
    // NanoMenuRender.cpp (next to the PNG loader).
    void overlayCaptureBackground();
    // Fast in-process display capture via SurfaceComposerClient (no screencap
    // spawn). Returns a new GL texture of the current screen (or 0 on failure,
    // caller falls back to the screencap binary). Outputs the captured size.
    GLuint overlayCaptureInProcess(int* outW, int* outH);
    GLuint mOverlayBgTex = 0;         // captured app snapshot (colour), 0 = none
    int64_t mOverlayShowMs = 0;       // uptimeMillis() when the overlay was raised;
                                      // power events within a grace window after
                                      // this are ignored so the summoning hold's
                                      // own release does not instantly dismiss it
    // Deferred dismiss after launching another app from the overlay: keep the
    // overlay layer up (occluding the dying old app / blank) until the new app is
    // resumed, so the user never sees the old frame or black between the two.
    bool mOverlayLaunchPending = false;
    std::string mOverlayLaunchTarget;
    int64_t mOverlayLaunchStartMs = 0;
    int64_t mOverlayLaunchLastCheckMs = 0;
    float mOverlayEnterStart = -1.0f; // entrance state: -2 = pending (raised, awaiting
                                      // first render), >=0 = actively animating,
                                      // -1 = done/settled. Drives the cold-boot-style
                                      // fade/float-in of the XMB chrome (<0 = settled)
    float mOverlayEnterElapsed = 0.0f;// accumulated CLAMPED seconds into the entrance,
                                      // so a slow first frame (lazy load) cannot skip
                                      // the animation (it advances per rendered frame)

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
    // fds in mInputFds that are the POWER key node (axp2202-pek / KEY_POWER). The
    // overlay must NEVER EVIOCGRAB these: grabbing the power node blocks Android
    // EventHub from seeing the summon's power UP, which strands PhoneWindowManager's
    // single-key power gesture (mDownKeyCode stuck) so power only toggles every
    // OTHER press. Power is left ungrabbed so PWM owns the show/hide toggle.
    std::set<int> mPowerFds;
    int mInotifyFd;

    // Exit flag
    bool mExitRequested;
    bool mWaitForRelease; // wait for select key release before exiting
    // Home (non-overlay) launch transition: stamped (uptimeMillis) when the select
    // that triggered a launch is RELEASED; render() fades the XMB to black over
    // kLaunchFadeMs and the exit (hand-off to the app) is held until the fade
    // completes, so launching a game/app fades out instead of hard-cutting. 0 = idle.
    int64_t mLaunchFadeStart = 0;
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

    // uptimeMillis() of the last user input (any button event, a dpad/stick
    // direction press, or a scripted nav injection). The PS3 XMB idle frame-
    // rate drop in threadLoop only engages a full minute after this stamp.
    int64_t mLastInputMs = 0;

    // Brightness / power
    bool mSelectHeld;
    bool mStartHeld = false;   // for the START+SELECT PS3 boot-intro re-trigger
    int64_t mPowerPressTime;
    int mBrightness;
    int mMaxBrightness;
    bool mShowBrightnessBar;
    int mBrightnessBarTimer;
    bool mPs3BrightSlider = false;   // Quick Menu brightness slider modal: Left/Right adjust, any other key exits
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
    // Multiplies drawText's outline offset (stroke thickness). 1.0 normally;
    // raised briefly around the item subtitle so its dark stroke is heavier and
    // the small grey description reads clearly on small panels.
    float mTextOutlineWidthMul = 1.0f;
    // Small-panel readability boost for the fullscreen dialog/wizard body text
    // (System Update, Network Connection Settings). 1.0 on >=720p panels,
    // ramping up on small handheld screens where the 1:1 web sizes are too small.
    float ps3DlgFontBoost() const;
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
        PS3_QUICK,        // Quick Menu action; a = action code (QA_* in NanoMenuPS3Menu.cpp)
        PS3_GS_ROOT,      // "Game Systems" entry -> open the systems-list editor screen
        PS3_GS_SYSTEM_ROW,// a system row in the Game Systems list (a = mXmbSystems index)
        PS3_GS_FIELD,     // a field row in the per-system editor (a = field id)
        PS3_GS_ADD,       // "Add New System" row in the Game Systems list
        PS3_GS_EMUROW,    // an emulator/core row in the emulator picker (a = catalog index)
        PS3_GS_EMU_CUSTOM,// "Custom..." row in the emulator picker (type a core/package by hand)
        PS3_GS_SCANSRC,   // a configured scan-folder row (a = scanSources index; Y removes it)
        PS3_GS_ADDFOLDER, // "Add Folder..." row in the scan-folders screen
        PS3_GS_DIR,       // a directory row in the folder browser (payloadStr = path)
        PS3_GS_SELFOLDER, // "Select This Folder" row in the folder browser (payloadStr = path)
        // ---- Music player (PS3 XMB music port) ----
        PS3_MUSIC_ALBUM,    // an album folder in the Music column -> track submenu (a = album idx)
        PS3_MUSIC_TRACK,    // a track -> open the Now-Playing player (a = track idx in the view list)
        PS3_MUSIC_PLAYLIST, // a playlist -> its track submenu (a = playlist idx)
        PS3_MUSIC_PL_NEW,   // "Create New Playlist" row (OSK name)
        PS3_MUSIC_FOLDER_ROW,// a configured music scan-folder row (a = mMusicFolders idx; Y removes)
        PS3_MUSIC_REFRESH,  // "Refresh" row in the music folders screen -> rescan the library
        // ---- Photo viewer (PS3 XMB photo port) ----
        PS3_PHOTO_ALBUM,    // a photo group-folder in the Photo column -> thumbnail grid (a = group idx)
        PS3_PHOTO,          // an individual photo row (a = photo idx) -> open the viewer
        PS3_PHOTO_PLAYLIST, // a photo playlist -> its thumbnail grid (a = playlist idx)
        PS3_PHOTO_PL_NEW,   // "Create New Playlist" row in the photo playlists screen (OSK name)
        PS3_PHOTO_FOLDER_ROW,// a configured photo scan-folder row (a = mPhotoFolders idx; Y removes)
        PS3_PHOTO_REFRESH,  // "Refresh" row in the photo folders screen -> rescan the library
        // ---- Video player (PS3 XMB video port) ----
        PS3_VIDEO_FILE,     // a video file row (a = video idx in mVideos) -> open the player
        PS3_VIDEO_FOLDER_ROW,// a configured video scan-folder row (a = mVideoFolders idx; Y removes)
        PS3_VIDEO_REFRESH,  // "Refresh" row in the video folders screen -> rescan the library
        PS3_VIDEO_PLAYLIST, // a video playlist -> its file submenu (a = playlist idx)
        PS3_VIDEO_PL_NEW,   // "Create New Playlist" row in the video playlists screen (OSK name)
        // ---- IPTV (live channels from iptv-org index.m3u; nano addition) ----
        PS3_IPTV_GROUP,     // a category -> country submenu or channels (a = mIptvCats idx)
        PS3_IPTV_COUNTRY,   // a country within a category -> channel submenu (a = cat idx, b = country idx)
        PS3_IPTV_CHANNEL,   // a channel -> open the live stream player (a = mIptvChannels idx; payloadStr = stream URL)
        // ---- Internet Radio (live audio stations m3u; nano addition, Music category) ----
        PS3_RADIO_GROUP,    // a station group -> bucket submenu or stations (a = mRadioCats idx)
        PS3_RADIO_BUCKET,   // an alpha bucket within a group -> station submenu (a = cat idx, b = bucket idx)
        PS3_RADIO_STATION,  // a station -> open the audio stream in the music player (a = mRadioStations idx; payloadStr = URL)
        // ---- File Explorer (Settings > File Explorer; nano addition) ----
        PS3_FE_DIR,         // a directory row in the file explorer (payloadStr = full path) -> navigate into it
        PS3_FE_FILE,        // a file row in the file explorer (payloadStr = full path) -> X/Triangle for options
    };
    // Game Systems editor screen kinds (Ps3Level.screenKind). Used to route the
    // X / L1 / R1 / Y buttons contextually while a GS screen is on the nav stack.
    // MUSIC_FOLDER = the music library's folder-list screen (Search for Media Servers).
    enum GsScreenKind { GS_NONE = 0, GS_LIST = 1, GS_EDITOR = 2, GS_FOLDER = 3,
                        GS_ICONGRID = 4, GS_EMUPICK = 5, GS_FOLDERBROWSE = 6,
                        MUSIC_FOLDER = 7, PHOTO_FOLDER = 8, PHOTO_GRID = 9,
                        VIDEO_FOLDER = 10, IPTV_GROUPS = 11, RADIO_STATIONS = 12,
                        FE_BROWSE = 13, APP_INFO = 14, APP_STORAGE = 15, APP_PERMS = 16 };
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
        // Settings binding resolved ONCE at item build (makeDataItem) from the
        // item's label against the static kPs3Bindings table, instead of re-scanning
        // that table by string-compare for every visible item every frame in
        // resolvePs3ItemValue. The label is fixed for the item's lifetime (items are
        // never relabeled in place) and kPs3Bindings is static const, so the pointer
        // stays valid. nullptr for any item whose label is not a bound setting
        // (ROM / app / recent / Game-Systems-editor rows never match).
        const Ps3SettingBinding* binding = nullptr;
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
        int screenKind = 0;   // 0 = normal submenu; GS_* for the Game Systems editor screens
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
    bool   mPs3DlgKeepOpen = false; // set by an accept handler that reconfigured the dialog in place (uninstall progress); closePs3Dialog then leaves it up
    int    mPs3DlgKind = 0;        // 0 = fullscreen message/chooser, 1 = side-panel theme chooser
    int    mPs3DlgThemeKey = 0;    // 0 none, 1 theme, 2 colour, 3 background, 4 font, 5 day/night
    std::string mPs3DlgTitle;
    std::string mPs3DlgBody;
    std::vector<std::string> mPs3DlgOptions;
    std::vector<int> mPs3DlgSwatch;   // COLOR_OPTIONS index per option for the Colour chooser, else -1
    int    mPs3DlgSel = 0;
    int    mPs3DlgOrigSel = 0;     // value at open, for revert on cancel
    float  mPs3DlgAnim = 0.0f;     // open slide/fade 0->1
    bool   mPs3DlgClosing = false; // side-panel dismiss fade-out in flight (kind 1)
    float  mPs3DlgCloseAnim = 0.0f;// close fade alpha 1->0 (reverse of mPs3DlgAnim)
    bool   mPs3DlgBlurValid = false;
    const Ps3SettingBinding* mPs3DlgBinding = nullptr;  // active settings-bound chooser (else null)
    std::unordered_map<std::string, std::string> mPs3BindCache;  // cached bound values by leaf label
    // Side-panel numeric slider (mPs3DlgKind==1, mPs3DlgThemeKey==0). For bound
    // settings whose options string is "slider:min:max:step[:scale]" (too many
    // values for a list - LED brightness, dB gains, float macro params). Left/Right
    // adjust by step; X commits the formatted value via writeSettingValue, O cancels.
    bool   mPs3DlgSlider = false;
    float  mPs3DlgSldMin  = 0.0f;
    float  mPs3DlgSldMax  = 1.0f;
    float  mPs3DlgSldStep = 1.0f;
    float  mPs3DlgSldVal  = 0.0f;
    int    mPs3DlgSldScale = 0;    // decimal places (0 = integer)
    // ---- Home XMB option menu (Triangle / X) ----------------------------------
    // The web optMenu context "sidebar": pressing Triangle on a focused item opens
    // a small list of real per-item actions (Start, Play, Information). A separate
    // modal from the theme chooser so an action like Information can open a dialog
    // without colliding with its own state.
    bool   mPs3OptActive = false;
    bool   mPs3OptClosing = false;
    float  mPs3OptAnim = 0.0f;
    float  mPs3OptCloseAnim = 0.0f;
    bool   mPs3OptBlurValid = false;
    float  mPs3OptBlurT = 0.0f;
    std::vector<std::string> mPs3OptLabels;   // row labels
    std::vector<std::string> mPs3OptActs;     // parallel action ids
    std::vector<char>        mPs3OptStart;    // parallel: 1 = draw a START pill
    int    mPs3OptSel = 0;
    // Snapshot of the item the menu was opened on (the column may not change while
    // the modal is up, but snapshotting keeps the action self-contained).
    int    mPs3OptCtxKind = 0;
    int    mPs3OptCtxA = 0, mPs3OptCtxB = 0;
    std::string mPs3OptCtxLabel, mPs3OptCtxPayload, mPs3OptCtxDesc;
    std::vector<Ps3Item> mPs3OptCtxList;
    int    mPs3OptCtxSel = 0;
    // Nested side-panel submenu (web optMenu.subOpen/subRows): a parent row can
    // carry a list of sub-rows. Opening it slides the main column left and shows the
    // submenu in the right slot, the selected sub-row aligned to the parent. Single
    // level only. The vectors are populated only while the menu is open (no idle cost).
    struct Ps3OptSub {
        std::string label;
        int kind = 0;      // 0 = sort, 1 = group content, 2 = slideshow style, 3 = per-app orientation
        int field = 0;     // sort: 0 = film date, 1 = import date, 2 = name
        int dir = 1;       // sort: 0 = desc, 1 = asc
        int groupIdx = 0;  // group-content mode index
        int sstyle = 0;    // slideshow style 0..4
        std::string orient; // per-app orientation value ("" = default/none, else landscape/portrait/rev_*)
    };
    std::vector<char> mPs3OptSep;                       // parallel: 1 = separator row (skipped in nav)
    std::vector<char> mPs3OptHasSub;                    // parallel: 1 = row opens a submenu
    std::vector<int>  mPs3OptSubDef;                    // parallel: default sub-selection
    std::vector<std::vector<Ps3OptSub>> mPs3OptSubRows; // parallel: each row's submenu rows
    bool   mPs3OptSubOpen = false;
    int    mPs3OptSubSel = 0;
    void   openXmbOpt();          // build context rows + open (no-op if nothing useful)
    void   closeXmbOpt();
    int    xmbOptDefaultSel();    // first per-item action after the list-group separator
    void   xmbOptMove(int dir);
    void   xmbOptEnter();         // activate the highlighted row
    void   xmbOptOpenSub();       // open the focused row's submenu (Right / Cross)
    void   xmbOptCloseSub();      // close the open submenu back to the parent list (Left / Circle)
    void   xmbOptApplySub(const Ps3OptSub& sr);   // apply a chosen submenu row
    void   xmbOptAction(const std::string& act);
    void   renderXmbOpt();
    // GammaEQ audio preview: a looping PCM clip played via AAudio so the equalizer
    // is audible while adjusting it (the FastMixer EQs the speaker mix). mEqPrevPcm
    // is interleaved int16 at mEqPrevRate/mEqPrevChans; the callback owns mEqPrevPos.
    // mEqPreviewWanted is the user's intent; mEqPreviewOn is the actual play state.
    // The stream is opened on a detached worker so a not-yet-ready audio HAL never
    // blocks the UI/boot; eqPreviewTick() retries until the HAL is available.
    std::atomic<bool> mEqPreviewOn{false};
    std::atomic<bool> mEqPreviewWanted{false};
    std::atomic<bool> mEqPrevOpening{false};
    std::atomic<bool> mEqPrevPcmReady{false};    // the clip is loaded into mEqPrevPcm
    std::atomic<bool> mEqPrevLoadStarted{false}; // a background PCM load is in flight/done
    std::mutex mEqPrevMutex;           // guards mEqPrevStream commit / teardown
    float  mEqPrevRetryT = 0.0f;       // mEffectTime of the last open attempt
    void*  mEqPrevStream = nullptr;    // AAudioStream* (opaque here; AAudio.h is .cpp-only)
    std::vector<int16_t> mEqPrevPcm;
    volatile size_t mEqPrevPos = 0;
    int    mEqPrevRate  = 48000;
    int    mEqPrevChans = 2;
    int    mEqGammaEqDepth = -1;   // mPs3Stack depth of the GammaEQ submenu (-1 = not in it)
    void   startEqPreview();       // user toggled on (non-blocking)
    void   stopEqPreview();        // user toggled off / left the page / teardown
    void   tryStartEqPreviewAsync();
    void   eqPreviewOpenWorker();
    void   eqPreviewTick();        // per-frame retry while wanted but not playing
    void   ensureEqPcmAsync();     // background-load the clip (once)
    void   warmEqPreview();        // preload the clip when entering GammaEQ (no playback)
    void   freeEqPcm();            // release the 25MB clip when leaving GammaEQ
public:
    int32_t eqFillAudio(void* audioData, int32_t numFrames);   // called by the AAudio data callback (free fn)
private:
    // Render-thread watchdog: render() bumps mRenderHeartbeat every frame; a
    // background thread aborts (-> debuggerd tombstone with every thread's stack,
    // then init restarts us) if it stops advancing for ~8s. Turns a silent hang
    // into a diagnosable stack + an auto-recovery instead of a frozen device.
    std::atomic<uint64_t> mRenderHeartbeat{0};
    bool   mWatchdogStarted = false;
    // True while enterDrmSleep() intentionally parks the render thread (screen off /
    // system suspend). The render loop stops bumping the heartbeat then, so the
    // watchdog must skip its stall check or it would abort the whole process (which
    // also kills background music) on every power-button sleep.
    std::atomic<bool> mInDrmSleep{false};
    // True while opening a video on the render thread. NanoVideo::open() (and track
    // Async video open (no UI freeze on warmup). The blocking open work (extractor build,
    // codec create/configure/start, NanoHls network) runs on mVidOpenThread OFF the render
    // thread; the render thread shows a cancelable spinner and adopts the decoder once the
    // worker signals done (acquire/release handoff). The render thread is watchdog-protected
    // throughout an open (it spins the spinner); only a blocking TEARDOWN join of a wedged
    // worker (sleep/occlusion) is watchdog-exempt, via mVidTeardownExempt below.
    std::atomic<bool> mVidOpenInProgress{false};  // an open worker is live, decoder not yet adopted
    std::atomic<bool> mVidOpenDone{false};        // worker sets LAST (release); render reads (acquire)
    std::atomic<bool> mVidOpenOk{false};          // worker result (stored before mVidOpenDone)
    std::atomic<bool> mVidTeardownExempt{false};  // scoped watchdog exemption around a blocking teardown join
    std::thread mVidOpenThread;                    // per-open worker (never move-assigned while joinable)
    float  mVidOpenStartT = 0.0f;                  // mEffectTime at open begin (spinner delay + 30s deadline)
    bool   mVidOpenCancelReq = false;             // Back/sleep/occlusion/deadline asked to cancel
    bool   mVidStepActive = false;                // a next/prev/auto-advance step-retry chain is running
    int    mVidStepDir = 0;                        // its direction
    int    mVidStepTries = 0;                      // candidates left to try
    struct VidPending { bool isStream = false; std::string file; std::string url; int w = 0, h = 0;
                        int resumeChoice = -1; double resumeSec = 0.0; int vidIdx = 0; };
    VidPending mVidPending;                         // params captured at vidBeginOpen, applied on adopt
    void   vidBeginOpen(const VidPending& p);       // render: GL alloc + spawn the open worker
    bool   vidOpenTitleRun();                       // worker: blocking title open (reads mVidPending)
    bool   vidOpenStreamRun();                      // worker: blocking IPTV stream open
    void   vidAdoptOpen();                          // render: adopt a finished+joined successful open
    void   vidAbortOpen(const char* banner);        // render: tear down a failed/canceled open (then fade to XMB)
    void   vidStepRetryNext();                      // render: advance to the next step candidate (after prior joined)
    void   startRenderWatchdog();
    // Fullscreen dialog page (mPs3DlgKind==0). Mirrors web DIALOG_TEMPLATES +
    // drawDialog: a body type, an optional vector illustration, a notice line and
    // the source item's header icon.
    int    mPs3DlgType = 0;        // 0 info, 1 chooser, 2 chooser_illust, 3 confirm
    // Rich ROM Information page (a type-0 dialog variant): scraped cover + faint
    // fanart + metadata rows, with file path/size/core fallback when not scraped.
    bool   mPs3DlgRomInfo = false;
    std::string mPs3RomInfoSyn, mPs3RomInfoGenre, mPs3RomInfoPlayers, mPs3RomInfoRating;
    std::string mPs3RomInfoDate, mPs3RomInfoDev, mPs3RomInfoPub;     // scraped metadata
    std::string mPs3RomInfoFileName, mPs3RomInfoDir, mPs3RomInfoSize, mPs3RomInfoCore, mPs3RomInfoSystem;
    bool   mPs3RomInfoCoreIsApp = false;   // true = standalone app (label "App"), false = libretro core ("Core")
    int    mPs3RomInfoScroll = 0;  // first visible wrapped description line (Up/Down scroll)
    // App Information page (kind-0 dialog, filled asynchronously): "info" on an app sets
    // sys.gammaos.nano.appinfo_req=<pkg>#<n>, the framework writes the details file and
    // bumps sys.gammaos.nano.appinfo_gen, and we swap the "Loading..." body for it.
    bool        mPs3DlgAppInfo        = false;
    bool        mPs3DlgAppInfoPending = false;
    std::string mPs3AppInfoNonce;         // "<pkg>#<n>" we asked for; must match the file's req| line
    int         mPs3AppInfoScroll     = 0;
    int         mPs3AppInfoWaitFrames = 0;
    std::string mPs3AppInfoPkg;            // package the open App Information level acts on
    // Parsed App Information (filled by parseAppInfo from the framework's tagged file); the
    // Information page and the Storage / Permissions submenus all build from these.
    struct AppPerm { std::string perm, label; bool granted; };
    bool                     mAppInfoLoaded = false;
    std::vector<std::string> mAppInfoFacts;              // "Label    value" display rows
    std::string              mAppInfoCacheSz, mAppInfoDataSz;
    std::vector<AppPerm>     mAppInfoPerms;
    bool readNanoAppInfo(const std::string& nonce, std::string& bodyOut);
    void parseAppInfo(const std::string& body);          // tagged file -> the members above
    void buildAppInfoLevel(Ps3Level& out);               // facts + Storage + Permissions rows
    void buildAppStorageLevel(Ps3Level& out);            // Clear Cache / Clear Data
    void buildAppPermsLevel(Ps3Level& out);              // per-permission grant/deny toggles
    void appInfoTick();                    // per-frame: async-refresh the App Information levels
    // Auto-scroll: glide the Applications cursor to a freshly installed app "as if the nav
    // button were held", reusing the accelerating nav cadence + the item ease.
    int     mPs3AutoScrollTarget = -1;     // >=0: step the cursor toward this row
    int64_t mPs3AutoScrollLastMs = 0;
    int     mPs3AutoScrollCount  = 0;
    void tickAutoScroll();
    GLuint mPs3DlgFanTex = 0;       // fanart texture for the info page (freed on dialog close)
    int    mPs3DlgFanW = 0, mPs3DlgFanH = 0;
    GLuint mPs3DlgBoxTex = 0;       // cover texture for the info page (freed on dialog close)
    int    mPs3DlgBoxW = 0, mPs3DlgBoxH = 0;
    std::string mPs3DlgPendingFan, mPs3DlgPendingBox;  // async-decode target paths (drain routes results here)
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
    // Clock digital-string cache: the formatted "D/M H:MM" string only changes on
    // a minute (or format) boundary, so rebuild the snprintf set only when the key
    // changes. measureText stays live (cheap, keeps the right-anchor correct under
    // resize/orientation with no extra invalidation).
    char   mPs3ClockStr[48] = {0};
    int    mPs3ClockKMin = -1, mPs3ClockKHour = -1, mPs3ClockKMday = -1,
           mPs3ClockKMon = -1, mPs3ClockKDateFmt = -1, mPs3ClockKTimeFmt = -1;
    // One-shot analog-hand spin on a menu context change (submenu enter/leave or a
    // dialog open/close), mirroring the web drawClock. mPs3ClockSpinSig is the last
    // seen context signature; a change restarts the spin at mPs3ClockSpinStart.
    int    mPs3ClockSpinSig = -1;
    float  mPs3ClockSpinStart = -1.0e9f;
    // Flat-colour batch state (see beginSolidBatch). When true, drawQuad/
    // drawTriangle accumulate instead of drawing.
    bool   mSolidBatchActive = false;
    // Glass-icon frame-invariant uniforms (lights, ambient/spec/refraction, the
    // rotation matrix, sampler unit indices) are constants; upload them once per
    // frame on the first glass icon instead of ~14 glUniform calls per icon.
    // Reset to false at the top of renderPs3Xmb each frame.
    bool   mGlassUniformsSet = false;
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
                        bool sel, bool leftAlign, float ap, float baseScale,
                        bool translate = true);   // translate=false keeps native
                                                  // names verbatim (language list)
    void   ps3DlgHint(float slotCxDev, bool cross, const char* label,
                      float yDev, float baseScale, float ap);
    // Like ps3DlgHint but selects the button glyph: 0 = cross (X)/Confirm,
    // 1 = ring (O)/Cancel, 2 = Start (pill + play arrow). ps3DlgHint forwards to
    // this. The Confirm/Cancel glyphs follow the user's Button Prompts theme.
    void   ps3DlgHintG(float slotCxDev, int glyph, const char* label,
                       float yDev, float baseScale, float ap);
    // Draw one themed face-button badge centred on (gcx,yDev): a letter (A/B/X/Y)
    // in the default theme, or a PlayStation glyph (cross/ring/square/triangle)
    // in the PlayStation theme. role: 0 = Confirm, 1 = Cancel, 2 = Square,
    // 3 = Triangle. The OK/Cancel relabel swap flips the Confirm/Cancel display.
    void   drawFaceGlyph(int role, float gcx, float yDev, float glyphR, float lw, float ap);
    // Re-read persist.gammaos.nano.face_glyphs / face_swap (cheap shared-mem read).
    void   refreshFaceButtonPrefs();
    // Theme an on-screen legend that uses the canonical PlayStation face-button
    // words (Cross/Circle/Square/Triangle) into the user's Button Prompts theme:
    // letters map Cross->A, Circle->B, Square->Y, Triangle->X (the OK/Cancel swap
    // flips Cross/Circle); the PlayStation theme keeps the names. Applied after
    // trDyn so the translated action words are preserved.
    std::string themeButtonText(const char* in);
    bool   mFaceLetters = true;   // Button Prompts: letters (default) vs PlayStation
    bool   mFaceSwapOk  = false;  // OK Button: false = A/Cross, true = B/Circle
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
    void openBoundChooser(const Ps3SettingBinding* b);   // side chooser for a settings-bound leaf
    std::string ps3BoundValue(const Ps3SettingBinding* b);  // cached current value for a binding
    // Dark STROKE behind text/icons instead of a single drop shadow: a left/right
    // pair plus one panel-DOWN copy, all in panel space (offsets rotated through
    // the orientation), so it reads the same on any panel rotation and is subtle.
    // Early-returns when a is tiny, so dark wallpapers (low mPs3ShadowAlpha) skip it.
    void   drawTextStroke(const char* s, float x, float y, float scale, float a);
    void   drawIconStroke(unsigned int tex, float x, float y, float w, float h, float a);
    void  ps3BootReset(bool freshSetup);
    bool  ps3BootActive() const { return mPs3BootActive; }
    void  ps3BootSkip();
    void  ps3BootReplay();                  // test hook: re-run the cold-boot intro from t=0
    bool  ps3BootUpdate(float dtSeconds);   // advances clock; returns true while the XMB UI must stay suppressed
    void  renderPs3BootOverlay();           // logo/footer plate, warning, scene-reveal black wash
    GLuint loadPs3BootPlate(const char* name);
    std::vector<Ps3Cat> mPs3Cats;
    std::vector<Ps3Level> mPs3Stack;   // empty = at category top level
    int mPs3CatIdx = -1;
    int mPs3QuickCatIdx = -1;     // runtime index of the Quick Menu category (-1 if absent)
    std::string mPs3PerfModeLabel = "Normal";  // cached persist.gammaos.performance_mode label (Quick Menu row value)
    std::string mPs3SystemName;                // cached System Name (persist.gammaos.nano.system_name, else ro.product.model)
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
    int   mPs3DescLinesTarget = 3;     // active item's wrapped subtitle line count (1..4), set by drawDesc; drives the dynamic active pad
    float mPs3ActivePad = 118.0f;      // eased active-item pad (virtual px) -> ps3::gActivePad, grows to fit the subtitle's actual line count
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
    // iconRef string -> (colour silhouette tex, glass bevel nmap) for retroarch:/core:/file: refs.
    std::map<std::string, std::pair<GLuint, GLuint>> mPs3IconRefCache;
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
    // Rebuild the cats after a background rescan changed ROM lists, re-finding
    // each category's selected item by label (no selection yank). Driven by
    // mPs3CatsStale from the threadLoop scan pickup, applied at the XMB root.
    void rebuildPs3CatsPreserveSel();
    bool mPs3CatsStale = false;
    Ps3Item makeDataItem(const Ps3DataItem* d);   // runtime item from a DATA node
    void buildDataSubmenu(const Ps3DataItem* node, Ps3Level& out);
    void buildRomSubmenu(int sysIdx, Ps3Level& out);
    // Quick Menu (nano legacy global actions): the Power submenu builder, the
    // performance-mode side-panel chooser, and the kill-apps backend.
    void buildQuickPowerSubmenu(Ps3Level& out);
    // Overlay-only per-app Orientation submenu for the foreground app.
    void buildAppOrientSubmenu(Ps3Level& out);
    // Quick Settings submenu (ported GammaOS QS tiles) + Notifications submenu.
    void buildQuickSettingsSubmenu(Ps3Level& out);
    void buildGamepadSubmenu(Ps3Level& out);         // full GammaPad settings (Settings-app parity)
    void buildMouseSubmenu(Ps3Level& out);           // Mouse Mode cursor-speed settings
    void buildRemapSrcSubmenu(Ps3Level& out, bool axis);  // button/axis remap source list
    void buildRemapTargetSubmenu(Ps3Level& out);          // target chooser for mRemapSrc
    void buildDevicesSubmenu(Ps3Level& out);              // capture-device multi-select
    void buildFfDeviceSubmenu(Ps3Level& out);             // vibration-device single-select
    void buildBlacklistSubmenu(Ps3Level& out);            // passthrough-blacklist button multi-select
    void buildComboSubmenu(Ps3Level& out);                // combo_map list editor (add-flow state machine)
    void buildAxisBtnSubmenu(Ps3Level& out);              // axis_btn list editor
    int mComboStage = 0; int mComboB1 = 0; int mComboB2 = 0;   // combo add-flow state
    int mAxbStage = 0;   int mAxbAxis = 0; int mAxbBtn = 0;    // axis_btn add-flow state
    std::string mRemapKey;        // remap prop being edited (remap_btn / remap_axis)
    bool mRemapAxis = false;      // axis (vs button) name table for the active remap picker
    int  mRemapSrc = 0;           // source code chosen, awaiting a target pick
    void buildNotificationsSubmenu(Ps3Level& out);   // re-reads the live list, then builds
    void buildNotificationsLevel(Ps3Level& out);     // builds rows from the current mNotifs (no read)
    // One active notification, parsed from `dumpsys notification --noredact` only
    // while the Notifications submenu is open (mNotifs is cleared on close).
    struct NanoNotif { std::string key, pkg, title, text; };
    std::vector<NanoNotif> mNotifs;
    void readNotifications(std::vector<NanoNotif>& out);
    // Secondary (external) display on/off, in-memory like the QS tile (default on,
    // not persisted; the real state lives in DisplayManagerService).
    bool mSecondaryDisplayOn = true;
    void openPerformanceChooser();
    void quickKillApps(bool includeForeground);
    void overlayKillAll();   // Quick Menu Kill All Apps (overlay): hard-stop every app incl the game, no relaunch
    void buildRecentSubmenu(Ps3Level& out);
    void buildAppSubmenu(Ps3Level& out);
    // Game Systems editor (dynamic systems config). The list screen shows every
    // configured system (enabled + disabled) with enable/disable + reorder; later
    // phases add the per-system editor, folder picker, and icon grid.
    void buildGameSystemsList(Ps3Level& out);
    void gsToggleSystem(int sysIdx);            // flip enabled, persist, rebuild
    void gsReorderSystem(int sysIdx, int dir);  // move a system up (-1) / down (+1)
    void loadRomCacheForSystem(XmbSystem& sys); // reload a system's cached ROM list (DE)
    // Per-system editor: name/exts/tint/launch type+core+package+args.
    void buildGameSystemEditor(int sysIdx, Ps3Level& out);
    void gsEditField(int field);                // A on an editor field row -> open OSK / chooser
    void gsRefreshStackLevels();                // rebuild any GS list/editor levels on the stack after an edit
    void gsOpenLaunchTypeChooser();             // side-panel chooser (theme key 20)
    void gsOpenTintChooser();                   // colour-swatch chooser (theme key 21)
    void gsOpenResetConfirm();                  // Cancel / Reset-to-default chooser (theme key 22)
    void gsOpenScraperChooser();                // per-system scraper override (theme key 24)
    void gsEditScraperCred(bool masked);        // per-system scraper credential override via OSK
    bool resetSystemToBuiltinDefaults(int sysIdx);  // restore a built-in's config from kXmbSystemDefs
    int mGsEditIdx = -1;   // mXmbSystems index currently open in the editor (for chooser/OSK writeback)
    float mGsTintOrigR = 1.0f, mGsTintOrigG = 1.0f, mGsTintOrigB = 1.0f;  // exact tint at chooser open (cancel restore)
    int  ps3TopScreenKind() const { return mPs3Stack.empty() ? 0 : mPs3Stack.back().screenKind; }

    // ---- Icon grid picker: the RetroArch 849-icon chooser ----
    std::vector<std::string> mIconGridNames;   // all icon names (no .png), loaded once
    std::vector<int>         mIconGridFiltered;// indices into mIconGridNames matching the filter
    std::string              mIconGridFilter;  // current OSK substring filter (lowercased)
    int    mIconGridCursor = 0;                // index into mIconGridFiltered
    int    mIconGridTop = 0;                   // first visible row (scroll)
    float  mIconGridAnim = 0.0f;               // open fade-in 0->1
    std::map<int, GLuint> mIconGridThumb;      // nameIdx -> glass bevel nmap (downscaled 64x64)
    std::vector<int>      mIconGridLru;        // LRU order of cached nameIdx (evict past the cap)
    void   loadIconGridNames();                // enumerate the bundled 849-icon set once
    void   applyIconGridFilter();              // rebuild mIconGridFiltered from mIconGridFilter
    void   openIconGridPicker();               // push the grid screen for mGsEditIdx
    void   closeIconGridPicker();              // free thumbnails + grid nav state
    GLuint iconGridThumb(int nameIdx);         // get/generate the cached glass bevel for a name
    void   renderIconGridPicker();             // draw the grid (called from renderPs3Xmb)
    void   iconGridNav(int dx, int dy);        // 2D cursor movement
    void   iconGridSelect();                   // assign the highlighted icon to the system
    void   iconGridResetCache();               // drop all thumbnail textures

    // ---- Emulator catalog (Daijishou platform configs, NanoMenuPS3EmuCatalog.cpp) ----
    struct EmuCatEntry {
        std::string platform;       // platform display name ("Sony - PlayStation 2")
        std::string platformId;     // platform uniqueId ("ps2") -> conventional rom folder
        std::string player;         // player display name ("Aethersx2")
        std::string amArgs;         // amStartArguments (whitespace-normalized)
        std::string playerRegex;    // player acceptedFilenameRegex
        std::string platformRegex;  // platform acceptedFilenameRegex
    };
    std::vector<EmuCatEntry> mEmuCatalog;      // all players across all bundled platforms (loaded once)
    std::string mEmuPickFilter;                // current emulator-picker substring filter
    void loadEmuCatalog();                     // parse the bundled Daijishou JSONs once
    void buildEmulatorPicker(Ps3Level& out);   // build the filtered emulator list screen
    void gsOpenEmulatorPicker();               // open the emulator chooser for mGsEditIdx
    void applyEmulatorChoice(int catIdx);      // apply a catalog entry (edit, or add a new system)
    void applyEmuEntryToSystem(XmbSystem& s, const EmuCatEntry& e);  // set launch fields from a catalog entry
    bool mGsAddMode = false;                    // emulator picker opened to create a NEW system

    // ---- Add / remove custom systems ----
    void gsAddSystem();                        // "Add New System" -> emulator picker in add mode
    void gsAddBlankSystem();                   // create a blank custom system + open its editor
    void gsRemoveSystem(int sysIdx);           // remove a custom system + its caches
    void gsOpenRemoveConfirm(int sysIdx);      // Cancel / Remove confirm chooser

    // ---- Native raw-path folder picker for scan sources ----
    std::string mGsFolderPath;                 // current folder-browser path ("" = storage roots)
    void buildScanFoldersScreen(Ps3Level& out);// the system's scan-source list (+ Add Folder)
    void gsOpenScanFolders();                  // open the scan-folders screen for mGsEditIdx
    void buildFolderBrowser(const std::string& path, Ps3Level& out);  // raw-path browser
    void gsFolderSelect(const std::string& path); // add a folder as a rawpath scan source
    void gsRemoveScanSource(int srcIdx);       // drop a scan source from the edited system

    // ======================= File Explorer (Settings > File Explorer) =======================
    // A controller-first file manager that reuses the folder-picker navigation (opendir/readdir,
    // storage roots, ".." up) but lists files AND folders, and exposes Copy/Move/Delete/Rename/
    // Information through the shared XMB X/Triangle side menu (openXmbOpt). Implementation in
    // NanoMenuFileExplorer.cpp. Long-running copy/move/delete run on a detached worker (feOpWorker)
    // so the render thread never blocks (see nano_render_thread_blocking).
    std::string mFeBrowsePath;                  // current explorer directory ("" = storage roots)
    std::string mFeClipPath;                    // pending Copy/Move source (empty = clipboard clear)
    bool        mFeClipMove = false;            // true = Move (cut), false = Copy
    std::string mFeDeleteTarget;                // path awaiting the delete confirm (dialog themeKey 30)
    std::string mNanoUninstallPkg;              // pkg awaiting the uninstall confirm (dialog themeKey 31)
    std::string mNanoUninstallPending;          // pkg being uninstalled: the "Uninstalling..." dialog stays up until it is gone
    // Async copy/move/delete: the worker holds its OWN shared_ptr to this result block and touches
    // ONLY the block + value-captured paths (never `this`), so a teardown mid-op cannot use-after-free.
    // feTick polls done and reaps. One op at a time (mFeOp non-null = busy).
    struct FeOp { std::atomic<bool> done{false}; std::atomic<bool> ok{false}; int kind = 0; std::string name; };
    std::shared_ptr<FeOp> mFeOp;
    void feOpen();                              // open the explorer at storage roots (from the Settings leaf)
    void buildFileBrowser(const std::string& path, Ps3Level& out);  // list dirs + files at path
    void feRefresh();                           // rebuild the current top level in place after an op
    void feNavigate(const std::string& path);   // enter a directory (rebuild top level in place)
    bool feBack();                              // Circle: up one dir if not at root; true = handled, false = pop
    void feAction(const std::string& act);      // dispatch a side-menu action (fecopy/femove/...)
    void feStartOp(int kind, const std::string& src, const std::string& dst);  // spawn the async worker
    void feTick();                              // per-frame: reap a finished worker, refresh, result dialog
    void feShowInfo(const std::string& path);   // open the Information page for a file/folder
    void feInfoDialog(const std::string& title, const std::string& body);  // generic XMB info dialog (kind 0)

    // ======================= Music player (PS3 XMB port) =======================
    // Library model (nano_music.json), folder import (reuses the folder picker via
    // mFolderPickTarget), the scanner, and the Music-column content. The Now-Playing
    // screen + control panel + playlists are in NanoMenuMusic.cpp.
    struct MusicTrack {
        std::string file;        // absolute path
        std::string title;       // tag title (fallback: filename)
        std::string artist;
        std::string album;       // album group key (fallback: parent folder name)
        std::string codec;       // badge text (MP3/AAC/FLAC/...)
        double durationSec = 0.0;
        int trackNo = 0;
        int64_t mtime = 0;       // for incremental rescan
        bool albumHidden = false; // track lives in a folder that has an .m3u: shown via the playlist,
                                  // not as a duplicate folder-fallback album
    };
    struct MusicPlaylist {
        std::string name;
        std::vector<std::string> files;   // references MusicTrack.file
        std::string m3uPath;              // non-empty: derived from this .m3u (regenerated on scan);
                                          // empty: user-created (preserved across scans)
    };
    int mFolderPickTarget = 0;             // 0 = Game Systems scan source, 1 = Music library, 2 = Photo library, 3 = Video library
    std::vector<std::string> mMusicFolders;
    std::vector<MusicTrack>  mMusicTracks;
    std::vector<MusicPlaylist> mMusicPlaylists;
    int64_t mMusicCfgStamp = -1;           // mtime of nano_music.json (cross-process reload)
    // Metadata-parser schema version. Bumped whenever the way we read tags changes
    // so an existing library (mtime-cached) re-probes instead of keeping stale data.
    // v2: read real container tags (title/artist/album) via the fixed getFileFormat.
    // v3: parse .m3u/.m3u8 into playlists + hide the folder-fallback album for m3u dirs.
    static const int kMusicMetaVersion = 3;
    int mMusicCfgVersion = 0;              // version found in nano_music.json (0 = none)
    bool mMusicLoaded = false;             // library parsed once (lazy, first Music entry)
    bool mMusicCatsStale = false;          // a scan finished -> rebuild the Music column at root
    // scan worker
    std::mutex mMusicScanMutex;
    std::vector<MusicTrack> mMusicScanResults;
    std::vector<MusicPlaylist> mMusicScanPlaylists;   // m3u-derived playlists from the last scan
    bool mMusicScanReady = false;
    bool mMusicScanRunning = false;
    bool mMusicScanPending = false;        // scan deferred until external storage is mounted
    bool musicStorageReady() const;        // true when the imported folders are reachable
    void musicRefresh();                   // user-triggered rescan of the imported folders
    // Default media directories: nano always scans the standard media folders that
    // exist across every storage medium (internal + each mounted external volume),
    // in addition to any folders the user imported. kind: 0 = photo, 1 = video,
    // 2 = music. nanoDefaultMediaDirs returns the existing standard dirs; the *ScanDirs
    // helper merges them with the user folders (deduped). Both skip paths that do not
    // exist, so an unmounted card or missing folder is handled gracefully.
    std::vector<std::string> nanoDefaultMediaDirs(int kind) const;
    std::vector<std::string> nanoMediaScanDirs(int kind, const std::vector<std::string>& userFolders) const;

    // ---- Boxart / cover scraper (NanoMenuScraper.cpp) ---------------------
    // Manifest of scraped art keyed by ROM path -> on-disk cover/fanart files.
    // Tiny metadata; loaded lazily, kept resident. The GL textures it points to
    // are loaded/freed on demand (Phase 3/4), not here.
    struct ScrapeEntry {
        std::string box;     // cover file path ("" = none)
        std::string fan;     // fanart file path ("" = none)
        std::string title;   // matched game title
        std::string scraper; // "screenscraper" | "thegamesdb"
        long long when = 0;  // epoch seconds when scraped
        // Metadata for the Information screen (empty = no data).
        std::string synopsis, genre, players, rating, releaseDate, developer, publisher;
    };
    std::unordered_map<std::string, ScrapeEntry> mScrapeIndex;
    bool mScrapeIndexLoaded = false;
    void scraperEnsureLoaded();            // lazy-load index.json
    void loadScrapeIndex();
    void saveScrapeIndex();
    const ScrapeEntry* scrapeEntryFor(const std::string& romPath);
    bool scraperBoxartEnabled();           // persist.gammaos.scraper.boxart
    bool scraperFanartEnabled();           // persist.gammaos.scraper.fanart
    nanoscraper::Credentials scraperCredsFor(int sysIdx);   // global + per-system override
    nanoscraper::Engine scraperEngineFor(int sysIdx, const nanoscraper::Credentials& cred);
    void scrapeAllSystems();               // Settings action: scrape every enabled system
    void scrapeOneSystem(int sysIdx);      // Game Systems editor action
    // One ROM of work, fully snapshotted so the worker never touches mXmbSystems.
    struct ScrapeJob {
        std::string romPath;
        std::string displayName;
        std::string sysName;
        int engine = 0;                    // nanoscraper::Engine
        nanoscraper::Credentials cred;
        nanoscraper::PlatformIds plat;
    };
    void scrapeSystemsAsync(const std::vector<int>& sysIdxs);
    void scrapeThreadFunc(std::vector<ScrapeJob> jobs);
    void scraperDrainResults();            // render thread: merge finished art + progress
    void scraperCancel();
    void renderScrapeProgress();           // the progress / result modal
    // scrape worker state
    std::mutex mScrapeMutex;
    bool mScrapeRunning = false;           // worker alive
    bool mScrapeCancel = false;            // cancel requested
    bool mScrapeProgActive = false;        // modal shown
    bool mScrapeDoneFlag = false;          // worker finished -> show summary
    int  mScrapeDone = 0, mScrapeTotal = 0, mScrapeHits = 0, mScrapeFail = 0;
    std::string mScrapeStatus;             // "System / Game" current line (under mutex)
    std::string mScrapeError;              // terminal message (creds missing / network)
    std::vector<std::pair<std::string, ScrapeEntry>> mScrapePending;  // finished -> merge on drain
    bool mScrapeBox = true, mScrapeFan = true;   // snapshot of the enabled-media toggles for the worker
    std::string mScrapeCacheDir = "/data/system/nano_scrape";
    // Boxart icon textures: lazy per-ROM cover GL textures that replace the generic
    // cartridge icon, freed when leaving the Game category and on sleep/occlusion.
    struct BoxTex { GLuint tex = 0; float ar = 1.0f; };   // ar = width/height
    std::unordered_map<std::string, BoxTex> mRomBoxartCache;
    bool mScrapeBoxartOn = false;        // per-frame cache of scraperBoxartEnabled()
    GLuint romBoxartTex(const std::string& romPath, float* outAR);
    void scraperFreeBoxart();            // delete all cached cover textures
    // Decode scraped art to a GL texture via stb_image (AImageDecoder silently
    // fails on the scrape PNGs on this device; stb_image works, same as the cinfo
    // bg). maxDim>0 downscales (nearest) to bound VRAM. Render thread only.
    GLuint scraperDecodeTex(const std::string& path, int maxDim, float* outAR);
    // ---- async scraper-art decode (mirrors the photo-viewer worker) ----------
    // ONE worker for ALL scraper art (boxart icons + hover fanart + Information
    // page art): decode RGBA off the render thread, upload GL in saDrainArt() on
    // the render thread, so opening a Game system or Information never hitches.
    // Lazy-started on the first request; fully stopped+joined (zero threads/CPU at
    // idle) by scraperFreeBoxart on leaving Game / occlusion / the 96-cache backstop.
    enum ScrapeArtTarget { SA_BOX = 0, SA_CINFO_FAN, SA_DLG_FAN, SA_DLG_BOX };
    struct SaDecReq { std::string path; int maxDim = 0; int target = 0; std::string key; uint64_t gen = 0; };
    struct SaDecRes { std::string path; int target = 0; std::string key; int w = 0, h = 0; float ar = 1.0f;
                      uint64_t gen = 0; std::vector<uint8_t> px; };
    std::thread mSaDecThread;
    std::mutex mSaDecMutex;
    std::condition_variable mSaDecCv;
    std::deque<SaDecReq> mSaDecQueue;
    std::vector<SaDecRes> mSaDecDone;
    std::set<std::string> mSaDecInFlight;        // dedup key = target-tagged path
    std::atomic<bool> mSaDecStop{false};
    std::atomic<bool> mSaDecStarted{false};
    std::atomic<uint64_t> mSaDecGen{0};          // bumped on teardown to drop stale results
    bool scraperDecodeRGBACpu(const std::string& path, int maxDim, int* outW, int* outH,
                              float* outAR, std::vector<uint8_t>& out);   // GL-free
    void saStartArtWorker();
    void saStopArtWorker();
    void saArtThreadFunc();
    void saRequestArt(const std::string& path, int maxDim, int target, const std::string& key);
    void saDrainArt();                           // render thread: upload + route results
    // persistence + lazy load
    int64_t musicConfigStamp() const;
    bool loadMusicConfig();
    void saveMusicConfig();
    void musicEnsureLoaded();              // parse JSON + NanoAudio init + kick a stale scan (guarded)
    void musicOnCatFocus();                // lazy-load the library when the Music category is focused
    // scan
    void musicScanAsync();                 // detached worker over mMusicFolders
    void musicScanThreadFunc();            // the worker body
    static bool parseM3u(const std::string& m3uPath, MusicPlaylist& out);  // .m3u/.m3u8 -> playlist
    void musicDrainScanResults();          // render-thread: swap in finished results + rebuild
    // folder import (Search for Media Servers)
    void musicOpenFolders();               // push the music folders screen
    void buildMusicFoldersScreen(Ps3Level& out);
    void musicFolderSelect(const std::string& path);
    void musicRemoveFolder(int idx);
    // content
    void buildMusicColumnItems(std::vector<Ps3Item>& out);   // albums + playlists entry for the Music cat
    void buildMusicAlbumSubmenu(int albumIdx, Ps3Level& out);
    void buildMusicPlaylistsScreen(Ps3Level& out);
    void buildMusicPlaylistSubmenu(int plIdx, Ps3Level& out);
    // helpers
    std::vector<int> musicAlbumTrackIndices(const std::string& album) const;  // sorted track idxs
    std::vector<std::string> musicAlbumNames() const;                          // unique, ordered
    // Sort By (Y) on the Music column: orders the albums (within-album track order
    // stays on-disk by file path, deliberately - user 2026-06-15).
    int  mMusicSortField = 0;               // 0 = name, 1 = date (newest album track mtime), 2 = track count
    int  mMusicSortDir   = 1;               // 0 = desc, 1 = asc (name forced asc)
    int64_t musicAlbumNewestMtime(const std::string& album) const;
    std::string musicSortLabelCur() const;
    void musicSortCycleY();                 // Y on the Music column: cycle sort + banner
    // playlists
    void musicCreatePlaylist(const std::string& name);
    void musicAddTrackToPlaylist(int plIdx, const std::string& file);
    // The audio engine instance (decode + AAudio + FFT). Lazy: init() on first Music
    // entry; open()/play() on first track play.
    NanoAudioPlayer mMusicPlayer;

    // ==== Video library (R4) ==============================================
    // Mirrors the Music library; HW playback via NanoVideo. Folder import reuses the
    // picker (mFolderPickTarget = 3). The web video section has no library/import/sort
    // (videos are flat demo items); these are a nano addition (user request) on top of
    // a 1:1 player UI. Persisted to nano_video.json. Lazy: parsed on first Video focus.
    struct VideoItem {
        std::string file;        // absolute path
        std::string name;        // display name (filename without extension)
        std::string vcodec;      // "AVC"/"HEVC"/... (column subtitle + Info)
        std::string acodec;
        double durationSec = 0.0;
        int w = 0, h = 0;
        int64_t sz = 0;          // file size in bytes
        int64_t mtime = 0;       // for incremental rescan
        double resumeSec = 0.0;  // last-played position for Resume (0 = none / start fresh)
    };
    // A live stream reference (IPTV channel) carried by the player queue and stored in a
    // playlist alongside local files. Keeps the channel name so it survives an index refresh.
    struct VidStreamRef { std::string name; std::string url; std::string group; };
    struct VideoPlaylist {
        std::string name;
        std::vector<std::string> files;       // references VideoItem.file (user-only; preserved across scans)
        std::vector<VidStreamRef> streams;    // IPTV/live-stream channels added to this playlist
    };
    std::vector<std::string> mVideoFolders;
    std::vector<VideoItem>   mVideos;
    std::vector<VideoPlaylist> mVideoPlaylists;
    int64_t mVideoCfgStamp = -1;
    static const int kVideoMetaVersion = 5;   // 2 = per-file resume position ("pos"); 3-5 = re-probe TS so the descramble path replaces stale "SCRAMBLED" codec labels
    int  mVideoCfgVersion = 0;
    bool mVideoLoaded = false;              // library parsed once (lazy, first Video entry)
    bool mVideoCatsStale = false;           // a scan finished -> rebuild the Video column
    int  mVideoSortField = 0;               // 0 = name, 1 = date (mtime), 2 = duration
    int  mVideoSortDir = 1;                 // 0 = desc, 1 = asc (name forced asc)
    std::mutex mVideoScanMutex;
    std::vector<VideoItem> mVideoScanResults;
    bool mVideoScanReady = false;
    bool mVideoScanRunning = false;
    bool mVideoScanPending = false;
    bool videoStorageReady() const;
    int64_t videoConfigStamp() const;
    bool loadVideoConfig();
    void saveVideoConfig();
    void videoEnsureLoaded();               // parse JSON + kick a stale scan (guarded)
    void videoOnCatFocus();                 // lazy-load when the Video category is focused
    void videoScanAsync();
    void videoScanThreadFunc();
    void videoDrainScanResults();           // render-thread: swap in finished results + rebuild
    void videoRefresh();                    // user-triggered rescan
    void videoOpenFolders();                // push the video folders screen (import)
    void buildVideoFoldersScreen(Ps3Level& out);
    void videoFolderSelect(const std::string& path);
    void videoRemoveFolder(int idx);
    void buildVideoColumnItems(std::vector<Ps3Item>& out);   // scanned video files for the Video cat
    // Video playlists (a nano addition; the web video section has none) - mirror music/photo.
    void buildVideoPlaylistsScreen(Ps3Level& out);
    void buildVideoPlaylistSubmenu(int plIdx, Ps3Level& out);
    void videoCreatePlaylist(const std::string& name);
    void videoAddToPlaylist(int plIdx, const std::string& file);
    bool  mVidPlChooserActive = false;
    std::vector<std::string> mVidPlChooserOpts;   // "New Playlist..." + existing names
    int   mVidPlChooserSel = 0;
    std::string mVidPlChooserFile;                // video file being added
    bool  mVidPlChooserIsStream = false;          // true = adding an IPTV channel (mVidPlChooserStream)
    VidStreamRef mVidPlChooserStream;             // the channel being added (when mVidPlChooserIsStream)
    float mVidPlChooserAnim = 0.0f;
    void vidOpenAddChooser(const std::string& file);
    void vidPlChooserMove(int dir);
    void vidPlChooserSelect();
    void vidPlChooserCancel();
    void drawVidPlChooser();
    void videoSortApply();                  // re-sort mVideos by the current field+dir
    void videoSortCycleY();                 // Y on the Video column: cycle sort + banner
    std::string videoSortLabelCur() const;
    bool videoSortLess(int a, int b) const;
    void videoAddStreamToPlaylist(int plIdx, const VidStreamRef& s);   // add an IPTV channel to a playlist
    void vidOpenAddStreamChooser(const VidStreamRef& s);               // "Add to Playlist" for a channel

    // ==== IPTV (live channels, nano addition) =============================
    // Channels parsed from the community iptv-org index.m3u (downloaded via device curl,
    // cached under /data, refreshed every 24h, cached copy kept if a refresh fails). Gated
    // by a first-use disclaimer (persist.gammaos.nano.iptv.agreed) and a Video Settings
    // toggle (persist.gammaos.nano.iptv). Grouped by the m3u group-title; activating a
    // channel streams its HLS URL through the video player (openIptvStream).
    // Two-level hierarchy: Group (the m3u group-title - a country or content group) ->
    // Sub (an alphabetical bucket, only when a group is large) -> Channels. A group small
    // enough to list directly has a single unnamed sub and skips straight to its channels.
    struct IptvChannel { std::string name; std::string url; std::string group; };
    struct IptvCountry { std::string name; std::vector<int> channels; };  // a sub-bucket (indices into mIptvChannels)
    struct IptvCat     { std::string title; std::vector<IptvCountry> countries; int total = 0; };
    std::vector<IptvChannel> mIptvChannels;          // published flat list (UI reads under mIptvMutex)
    std::vector<IptvCat>     mIptvCats;              // category -> country -> channels (sorted)
    std::mutex mIptvMutex;                            // guards the published channels/cats + status
    std::atomic<bool> mIptvScanRunning{false};       // a fetch/parse worker is in flight
    std::atomic<bool> mIptvReady{false};             // channels are loaded + usable
    std::atomic<bool> mIptvDirty{false};             // worker finished -> rebuild the open IPTV screen
    std::string mIptvStatus;                          // "Loading..."/error text (guarded by mIptvMutex)
    int mIptvEnabledCache = -1;                       // last-seen persist.gammaos.nano.iptv (live toggle-hide)
    void iptvOpen();                                  // disclaimer-passed entry: load + push the categories screen
    void iptvEnsureLoaded();                          // lazy: kick the (24h) refresh worker (async publish)
    void iptvEnsureLoadedSync();                      // parse the on-disk cache inline if not loaded (for search), then kick refresh
    void iptvFetchAsync();                            // spawn the download/parse worker (guarded)
    void iptvFetchThreadFunc();                       // worker: 24h-gated download -> parse -> publish (keep cache on fail)
    bool iptvDownloadIndex(const std::string& dst);   // curl the index.m3u to a temp file (true on non-empty success)
    bool iptvParseM3u(const std::string& path,
                      std::vector<IptvChannel>& outCh,
                      std::vector<IptvCat>& outCat);
    void iptvPublish(std::vector<IptvChannel>& ch, std::vector<IptvCat>& cat);
    void iptvDrain();                                 // render thread: rebuild the open IPTV screen; live toggle-hide
    void buildIptvCategoriesScreen(Ps3Level& out);    // IPTV -> category list (screenKind IPTV_GROUPS)
    void buildIptvCountrySubmenu(int catIdx, Ps3Level& out);          // category -> country list
    void buildIptvChannelSubmenu(int catIdx, int countryIdx, Ps3Level& out);  // (category,country) -> channels
    void openIptvStream(const std::vector<VidStreamRef>& queue, int startIdx);  // play a channel (queue = the group)
    // (vidOpenStream/vidOpenTitle are now the worker-thread vidOpenStreamRun/vidOpenTitleRun above)

    // ==== Internet Radio (live audio stations, nano addition - Music category) ============
    // Mirrors IPTV but audio-only: stations parsed from a community m3u (default the Pulham
    // Internet-Radio HQ list), downloaded via device curl, cached under /data, refreshed every
    // 24h (cached copy kept on a failed refresh). Gated by a first-use disclaimer
    // (persist.gammaos.nano.radio.agreed) and a Music Settings toggle (persist.gammaos.nano.radio).
    // The user can override the playlist URL (persist.gammaos.nano.radio.url). Grouped by the m3u
    // group-title; lists with no groups become one "All Stations" group split into alpha buckets.
    // Activating a station streams its URL through the MUSIC player (openRadioStation) so it gets
    // the Now-Playing screen, visualizers, clock-bar indicator and background playback.
    struct RadioStation { std::string name; std::string url; std::string group; };
    struct RadioBucket  { std::string name; std::vector<int> stations; };  // a sub-bucket (indices into mRadioStations)
    struct RadioCat     { std::string title; std::vector<RadioBucket> buckets; int total = 0; };
    std::vector<RadioStation> mRadioStations;         // published flat list (UI reads under mRadioMutex)
    std::vector<RadioCat>     mRadioCats;             // group -> bucket -> stations (sorted)
    std::mutex mRadioMutex;                            // guards the published stations/cats + status
    std::atomic<bool> mRadioScanRunning{false};       // a fetch/parse worker is in flight
    std::atomic<bool> mRadioReady{false};             // stations are loaded + usable
    std::atomic<bool> mRadioDirty{false};             // worker finished -> rebuild the open radio screen
    std::string mRadioStatus;                          // "Loading..."/error text (guarded by mRadioMutex)
    int mRadioEnabledCache = -1;                       // last-seen persist.gammaos.nano.radio (live toggle-hide)
    void radioOpen();                                  // disclaimer-passed entry: load + push the stations screen
    void radioEnsureLoaded();                          // lazy: kick the (24h) refresh worker (async publish)
    void radioEnsureLoadedSync();                      // parse the on-disk cache inline if not loaded (for search), then kick refresh
    void radioFetchAsync();                            // spawn the download/parse worker (guarded)
    void radioFetchThreadFunc();                       // worker: 24h-gated download -> parse -> publish (keep cache on fail)
    bool radioDownloadIndex(const std::string& dst);   // curl the m3u to a temp file (true on non-empty success)
    bool radioParseM3u(const std::string& path,
                       std::vector<RadioStation>& outSt,
                       std::vector<RadioCat>& outCat);
    void radioPublish(std::vector<RadioStation>& st, std::vector<RadioCat>& cat);
    void radioDrain();                                 // render thread: rebuild the open radio screen; live toggle-hide
    void buildRadioRootScreen(Ps3Level& out);          // Internet Radio -> group/bucket/station list (screenKind RADIO_STATIONS)
    void buildRadioBucketSubmenu(int catIdx, Ps3Level& out);                 // group -> bucket list
    void buildRadioStationSubmenu(int catIdx, int bucketIdx, Ps3Level& out); // (group,bucket) -> stations
    void openRadioStation(const std::vector<Ps3Item>& list, int listSel);    // play a station in the music player (queue = surrounding stations)

    // ---- Video player screen (R4 V2) - the full-screen 1:1 player (web drawVideoPlayer)
    bool mVidActive = false;                // the player is up
    std::vector<int> mVidList;              // mVideos indices in the player queue (all column videos)
    int  mVidIdx = 0;                       // current index into mVidList (or mVidStreamList for IPTV)
    // IPTV live-stream session: when true the player queue is mVidStreamList (channel URLs)
    // instead of mVidList/mVideos. Streams are live (no duration / seek / Resume).
    bool mVidIsStream = false;
    std::vector<VidStreamRef> mVidStreamList;
    float mVidEnterRaw = 0.0f;              // linear 0..1 (~400ms)
    float mVidEnterT = 0.0f;                // smoothstep of mVidEnterRaw (multiplies every layer's alpha)
    bool mVidPlaying = true;
    int  mVidScreenMode = 0;                // 0 Normal,1 Full Screen,2 Original,3 Zoom,4 Double Scale
    float mVidHintUntil = 0.0f;             // OSD bar auto-hide deadline (mEffectTime seconds)
    bool mVidOsd = false;                   // persistent Display toggle (keeps the bar visible)
    std::string mVidTransient; float mVidTransientUntil = 0.0f;   // top-center flash (FF/Rew/etc.)
    std::string mVidDispMode; float mVidDispModeUntil = 0.0f;     // screen-mode pill
    // resumeChoice: -1 = ask (direct Enter shows the Resume prompt for a watched title);
    // 1 = resume now (option-menu "Resume", no prompt); 0 = play from the start (option-menu
    // "Play from Beginning", caller clears the bookmark first).
    void openVideoPlayer(const std::vector<Ps3Item>& list, int listSel, int resumeChoice = -1);
    void closeVideoPlayer();                // release the decoder + fade out
    void videoTick();                       // enter/leave ease + end-of-stream auto-advance
    bool renderVideoPlayer();               // draws the player; true = it owns the screen
    void videoHardFree(bool sync = false);  // full decoder teardown; sync=true blocks (dtor), else async (sleep/occlusion)
    void vidSeek(double deltaSec);          // relative seek (D-pad L/R)
    void vidStepTitle(int dir);             // previous / next video in the queue
    void vidTogglePlay();
    void vidShowTransient(const std::string& text, float ms);

    // ---- Video transport extras (web vidScan/vidSlow/vidStepFrame/vidStop) -----
    // NanoVideo plays only at 1x, so every non-1x rate is timer-driven in videoTick:
    // native playback is paused and the position is seek()ed by rate*dt each frame.
    double mVidRate = 1.0;                   // scan/slow rate (1 = normal; <0 = reverse)
    bool   mVidStopped = false;             // Stop pressed (paused at t=0)
    int    mVidRepeat = 0;                  // 0 off,1 on,2 title,3 A-B,4 folder
    double mVidAbA = -1.0, mVidAbB = -1.0;  // A-B repeat points (seconds; -1 = unset)
    float  mVidVolume = 1.0f;               // 0..1 (applied to the video's audio track)
    int    mVidVolLevel = 0;                // Volume Control bar level -4..+4 (web v.volLevel); vol=(lvl+4)/8 on change
    // The video file's audio track: a second HW audio engine (AMediaExtractor/AMediaCodec ->
    // AAudio) opened on the same file, the picture is the master clock and the audio resnaps
    // when it drifts > 0.3s (web vidSyncAux). Lazy: opened on play, released on leave.
    NanoAudioPlayer mVidAudio;
    bool   mVidHasAudio = false;
    bool   mVidAudioStarted = false;        // audio held until the first video frame (avoids warmup desync)
    float  mVidAudioResyncT = 0.0f;         // last A/V resync time (cooldown so resync never tight-loops)
    // .ts runs through the in-process single-pass demuxer: ONE read pointer feeds both the
    // HW video codec (NanoVideo fed mode) and the audio (liba52 -> mVidAudio fed ring), so
    // A/V stay in lockstep with no system extractor. Everything else uses the normal path.
    NanoTsDemux mVidTsDemux;
    bool   mVidTsMode = false;              // current title's PICTURE is fed by mVidTsDemux
    bool   mVidTsAudio = false;             // current title's audio is fed by mVidTsDemux
    NanoAviDemux mVidAviDemux;              // RIFF/AVI demuxer (NDK extractor has no AVI support)
    bool   mVidAviMode = false;            // current title's PICTURE is fed by mVidAviDemux (audio TODO)
    // The .ts video track's real AMediaFormat (captured by vidBuildTracks from the system
    // extractor): handed to NanoVideo::openFed so the HW decoder gets the full format (csd,
    // colour aspects) and cold-starts reliably. Owned here; freed right after openFed/on close.
    AMediaFormat* mVidTsVideoFmt = nullptr;
    // ---- multiple audio tracks + subtitles (built per opened title; web audioTracks/subList) ----
    struct VidCue { double t = 0.0, d = 0.0; std::string text; };   // start, duration, text
    struct VidAudTrk { int idx = 0; std::string name; };           // idx = extractor track index
    struct VidSubTrk { std::string name; bool external = false; std::string file; int embIdx = -1;
                       std::vector<VidCue> cues; bool dvb = false; int dvbPid = -1;
                       bool cea608 = false; int ccChannel = 0; };  // live line-21 caption (via NanoTsDemux)
    std::vector<VidAudTrk> mVidAudTracks;   // all audio tracks in the current file
    std::vector<VidSubTrk> mVidSubTracks;   // embedded text subs + external SRT/VTT sidecars
    mutable std::vector<VidCue> mVidCcCues; // live CEA-608 cues snapshot (refreshed on read)
    int mVidAudCur = 0;                      // current index into mVidAudTracks
    int mVidSubCur = -1;                     // -1 = Off, else index into mVidSubTracks
    void vidBuildTracks(const std::string& file);   // enumerate audio + embedded text subs + sidecars
    void vidReadEmbeddedCues(const std::string& file, int trackIdx, std::vector<VidCue>& out);
    void vidSetAudioTrack(int ordinal);             // switch the active audio track (re-opens mVidAudio)
    void vidOpenTitleAudio(const std::string& file);   // open audio for a NON-.ts title (mVidAudio)
    // (vidOpenTitle is now vidOpenTitleRun above - runs on the open worker thread)
    void vidCloseTitleAudio();                      // stop the demux (if any) + release mVidAudio
    void vidAudioSeek(double sec);                  // seek the audio, routed to the demux for .ts
    double vidDuration() const;                     // duration (s): demux for .ts, else NanoVideo
    std::vector<VidCue> vidParseSrt(const std::string& text);   // SRT/VTT cue parser (web vidParseCues)
    const std::vector<VidCue>* vidActiveSubCues() const;        // cues for the selected sub track, or null
    double mVidScanLastTick = -1.0;         // wall-clock anchor for timer-driven scan
    double mVidScanPos = 0.0;               // commanded scan clock (decoder position lags + snaps to keyframes)
    double mVidLastPos = -1.0;              // buffering detection: last seen playback position
    float  mVidLastPosT = 0.0f;            // time the position last advanced
    bool   mVidBuffering = false;           // no new frame while playing -> show the buffering spinner
    // ---- Resume at last timestamp (per-file, persisted in nano_video.json "pos") ----
    bool   mVidResumeDirty = false;         // resume position changed since the last save
    double mVidResumeSaveT = -1.0;          // last debounced save time (mEffectTime)
    bool   mVidResumeAsk = false;           // the Resume / Play-from-beginning prompt is up
    int    mVidResumeSel = 0;               // 0 = Resume (default), 1 = Play from beginning
    double mVidResumeAskSec = 0.0;          // the saved position offered by the prompt
    void vidCaptureResume();                // store the current position onto the playing VideoItem
    void vidResumeConfirm();                // apply the highlighted Resume-prompt choice
    void drawVideoResume(float et);         // render the Resume prompt
    // Video-player modal dialog (web drawDialog confirm/info/busy), mirroring the home XMB
    // message dialog: Delete + Change Icon confirms, the Creating-icon/result chain, and the
    // no-audio / no-subtitle / Go-To-over-limit errors (web shows centred modals, not the
    // transient flashes nano used). Exists only while open; no allocations.
    bool   mVidDlgActive = false;
    int    mVidDlgKind = 0;                 // 0 = info (OK), 1 = confirm (Yes/No), 2 = busy (no button)
    std::string mVidDlgBody;
    int    mVidDlgSel = 1;                  // confirm: 0 = Yes, 1 = No (web defaultSel = No)
    int    mVidDlgYesAct = 0;               // confirm Yes action: 1 = Delete, 2 = Change Icon
    float  mVidDlgBusyUntil = 0.0f;         // busy auto-advance time (Creating icon... -> result)
    void   vidDlgInfo(const std::string& body);
    void   vidDlgConfirm(const std::string& body, int yesAct);
    void   vidDlgActivate();                // Cross on the dialog
    void   vidDlgBack();                    // Circle/Back on the dialog
    void   drawVideoDialog(float et);       // render the modal
    void vidStop();                         // pause + rewind to 0
    void vidScan(int dir);                  // Fast Forward / Fast Reverse (steps 1.5/10/30/120)
    void vidSlow(int dir);                  // Slow Forward / Slow Reverse (+-0.5)
    void vidStepFrame(int dir);             // single-frame step (pause + seek 1/30s)
    void vidFlash(int dir);                 // Instant Replay / Advance (+-15s)
    void vidBeginning();                    // Return to Beginning (or Previous if near start)

    // ---- Video control panel (VID_CP, web drawVideoPanel) ----------------------
    bool  mVidCpOpen = false;
    int   mVidCpSel = 0;                    // index into kVidCp
    int   mVidCpSelPrev = -1;
    float mVidCpAnimStart = -1.0f;          // open slide/fade start
    float mVidCpFocusStart = -1.0f;         // focus-change ease start
    bool  mVidCpClosing = false;
    float mVidCpCloseStart = -1.0f;
    float mVidCpPressStart = -1.0f;         // button invoke flash
    int   mVidCpPressSel = -1;
    // Panel submenu (screen mode / repeat / volume / AV settings / audio / subtitle)
    bool  mVidSubOpen = false;
    int   mVidSubKind = 0;                  // 0 screenmode,1 repeat,2 volume,4 audio,5 subtitle
    std::string mVidSubLabel;
    std::vector<std::string> mVidSubOpts;
    int   mVidSubSel = 0;
    bool  mVidPanelTouch = false;           // panel opened by a screen tap -> enlarge icons
    bool  mVidScrubbing = false;            // dragging the seek bar
    // Debounced touch scrub: NanoAudioPlayer::seek() joins+restarts the decode thread
    // (heavy, blocking on the render thread), so a live drag that seeks every move
    // freezes the render heartbeat and trips the 8s watchdog. Instead the drag only
    // previews a pending target; the real vidSeek fires once the finger settles/lifts
    // (mirrors the music player's mMpSeekPending debounce).
    bool   mVidScrubPending = false;
    double mVidScrubTarget = 0.0;           // pending seek target (seconds)
    float  mVidScrubInputT = 0.0f;          // mEffectTime of the last scrub input
    void vidPanelToggle();                  // Triangle: open/close the control panel
    void vidPanelOpen(bool byTouch = false);
    float vidPanelUi();                     // vidUiScale, enlarged when opened by touch
    void vidPanelClose();
    void vidPanelMove(int dx, int dy);      // spatial grid nav (+ submenu wrap)
    void vidPanelActivate();                // Cross on the focused control / submenu row
    void vidPanelBack();                    // Circle: close submenu, else close panel
    void vidSubBuild(int kind);             // populate mVidSubOpts/Sel for a control
    void vidSubConfirm();                   // apply the highlighted submenu row
    void drawVideoPanel(float closeT);      // render the panel (closeT>=0 drives close anim)
    GLuint vidIcon(int n);                  // load+cache a videoplayer icon (NanoMenuPS3Icons.cpp)
    float  vidIconAR(int n);                // cached aspect ratio (w/h)
    std::map<int, GLuint> mVidIconCache;
    std::map<int, float>  mVidIconAR;

    // ---- Go To (in-player H:MM:SS seek picker, web drawVideoGoTo) ---------------
    bool mVidGoToOpen = false;
    int  mVidGoToH = 0, mVidGoToM = 0, mVidGoToS = 0;
    int  mVidGoToField = 0;                 // 0 h, 1 m, 2 s
    void vidGoToOpen();
    void vidGoToMove(int dx);               // L/R: change field
    void vidGoToAdjust(int dy);             // U/D: change the focused digit
    void vidGoToActivate();                 // Cross: seek to the chosen time
    void vidGoToClose();
    void drawVideoGoTo();

    // ---- Scene Search (chapter grid, web vidScene* / drawVideoScene) ------------
    // Chapters parsed directly from the container (mp4/mov QT chapter track + Nero
    // chpl; mkv EBML Chapters) since the NDK extractor does not expose chapters.
    struct VidChapter { double t = 0.0; std::string title; };
    std::vector<VidChapter> mVidChapters;   // sorted by time; empty = "No chapters"
    void vidParseChapters(const std::string& file);   // fill mVidChapters from the file
    bool  mVidSceneOpen = false;
    bool  mVidSceneClosing = false;
    int   mVidSceneSel = 0;
    int   mVidSceneSelPrev = 0;
    float mVidSceneAnimStart = -1.0f;       // open fade start
    float mVidSceneCloseStart = -1.0f;
    float mVidSceneFocusStart = -1.0f;      // selection-change ease start
    void vidSceneOpen();                    // Scene Search: open the chapter grid (or "No chapters")
    void vidSceneClose();
    void vidSceneMove(int dx, int dy);      // grid nav (L/R = +-1 wrap, U/D = +-cols)
    void vidSceneActivate();                // Cross: seek to the focused chapter
    void drawVideoScene(float closeT);      // render the grid (closeT>=0 drives close anim)

    // ---- DVB / bitmap subtitles (NanoDvbSub) -----------------------------------
    // Decoded off the render thread (a large .ts can take seconds to stream + the
    // render watchdog would abort on a synchronous decode). Lazy: created only when
    // the user selects the DVB track, the whole pipeline freed on deselect/close.
    NanoDvbSub*       mVidDvb = nullptr;        // decoded subtitle timeline (or null)
    std::thread       mVidDvbThread;            // background decode worker
    std::atomic<bool> mVidDvbReady{false};      // timeline decoded + safe to read
    std::atomic<bool> mVidDvbLoading{false};    // decode in progress (shows a hint)
    std::atomic<bool> mVidDvbAbort{false};      // cancel a long decode on deselect/teardown
    std::string       mVidDvbPath;              // .ts being decoded
    int               mVidDvbPid = -1;          // subtitle PID (from probe)
    GLuint            mVidDvbTex = 0;            // currently uploaded region bitmap
    int               mVidDvbTexRegion = -1;    // which region index mVidDvbTex holds
    void vidDvbSelect(const std::string& file, int pid);   // start the background decode
    void vidDvbFree();                                     // join the worker + free all

    // ---- Now-Playing screen state (control panel + visualizers in Phase 3-5) ----
    bool mMpActive = false;            // the Now-Playing fullscreen is up
    std::vector<int> mMpQueue;         // track indices (into mMusicTracks) being played
    std::vector<std::string> mMpQueueFiles;  // parallel file paths so the queue survives a
                                       // library reload (mMusicTracks is replaced by a finished
                                       // rescan / external edit; the indices would otherwise dangle)
    int  mMpIdx = 0;                   // position in mMpQueue
    int  mMpVis = 0;                   // 0 = XMB Waves, 1 = Canyon, 2 = Globe, 3.. = wallpaper effects
    float mMpCanyonAlpha = 0.0f;       // Waves<->Canyon crossfade (eased 0..1 over ~0.5s)
    float mMpGlobeAlpha = 0.0f;        // Globe crossfade (eased 0..1 over ~0.5s)
    float mMpGlobeLon = 0.0f;          // auto-rotating longitude for the Globe visualizer
    bool mMusicResumeShown = false;    // is the Quick Menu "Resume Audio Player" item present
    int  mMpRepeat = 0;                // 0 off / 1 all / 2 one
    bool mMpShuffle = false;
    std::vector<int> mMpOrder;         // playback order (indices into mMpQueue, or mMpRadioQueue when radio)
    // Internet Radio session: when true the player queue is mMpRadioQueue (live station URLs),
    // not mMusicTracks. The Now-Playing screen shows station name / group / LIVE (no seek bar,
    // no FF/REW, no auto-advance on end); L/R step stations. mMpIdx indexes mMpRadioQueue.
    bool mMpIsRadio = false;
    std::vector<RadioStation> mMpRadioQueue;   // the playing station queue (the surrounding group)
    bool mMpRadioErrShown = false;             // one-shot: "Could not open this station." already shown
    void openMusicPlayer(const std::vector<Ps3Item>& list, int listSel);
    void musicRemapQueueAfterReload(); // re-resolve mMpQueue indices by file path after mMusicTracks is replaced
    void closeMusicPlayer();           // full stop + release the audio engine
    void minimizeMusicPlayer();        // hide the Now-Playing UI but keep audio playing
    void freeMusicVisGl();             // free Canyon/Globe GL immediately (park point; idempotent)
    void resumeMusicPlayer();          // reopen the Now-Playing screen on the live queue
    void mpPlayCurrent();
    void mpRebuildOrder();
    void mpStep(int dir, bool isAuto);
    void mpNext();
    void mpPrev();
    void musicTick();                  // per-frame: auto-advance on EOS + fades
    bool mpIsOpening() const;          // a track/station is loading (for the open spinner)
    float mMpOpenStartT = 0.0f;        // when the current open began (spinner delay + spin)
    bool mMpOpening = false;           // an OpenPlay was issued and audio has not started/failed yet.
                                       // Latch: set in mpPlayCurrent, cleared in musicTick once the
                                       // player starts (isPlaying) or fails (openFailed). NOT mMpAdvancing:
                                       // that is cleared the moment the open begins (ended() goes false),
                                       // so it cannot track a slow network/HLS open.
    // Shared rotating loading/buffering spinner (icon 114); used by music + video overlays.
    void drawLoadingSpinner(float ccx, float ccy, float sz, float alpha);

    // Bluetooth AVRCP media control (NanoMediaBridge in system_server owns an
    // AVRCP-eligible MediaSession and bridges to these via system properties).
    void nanoMediaDispatch(const char* cmd);   // one-shot transport command -> active player
    void nanoPublishMediaState();              // publish now-playing state + metadata for the bridge
    void writeMediaMetaJson(const std::string& title, const std::string& artist,
                            const std::string& album, const char* kind, double dur);
    std::string mMediaLastState;               // last-published playback state (change-gated)
    std::string mMediaLastKind;                // last-published source kind
    std::string mMediaLastPos;                 // last-published whole-second position
    std::string mMediaLastDur;                 // last-published whole-second duration
    std::string mMediaMetaSig;                 // last-published title/artist/album/kind signature
    unsigned    mMediaMetaGen = 0;             // metadata generation counter (bumped on track change)
    // The metadata JSON write (open/write/fsync/rename on /data) must NOT run on the
    // render thread: under heavy I/O (a video/stream playing) + memory pressure the
    // rename can stall for seconds and trip the render watchdog (SIGABRT). The render
    // thread snapshots the metadata and hands it to this detached writer, which does
    // the file write THEN the gen-prop bump (that order, so the bridge watching the
    // prop always reads a current file). Single coalesced slot: only the latest matters.
    struct MediaMetaReq { std::string title, artist, album, kind; double dur; unsigned gen; };
    std::mutex mMediaWriteMutex;
    std::condition_variable mMediaWriteCv;
    MediaMetaReq mMediaWritePending;
    bool mMediaWriteHasPending = false;
    bool mMediaWriteStarted = false;
    void mediaMetaWriter();                    // detached worker: file write + gen-prop bump
    void queueMediaMetaWrite(const std::string& title, const std::string& artist,
                             const std::string& album, const char* kind, double dur, unsigned gen);

    // Now-Playing fullscreen render + control panel (1:1 web drawMusicPlayer / MP_CP).
    bool mMpFullInfo = true;           // Display toggle (counter/time/codec/seek cluster); shown by default
    // Coalesced (debounced) seek for Left/Right scrub: pressing only updates a target
    // + previews it; the heavy NanoAudio::seek() (it joins+restarts the decoder) is
    // committed once after input settles, so rapid/held scrubbing never blocks the
    // render thread (which would trip the watchdog).
    bool   mMpSeekPending = false;
    double mMpSeekTarget = 0.0;        // desired position (seconds) while scrubbing
    float  mMpSeekInputT = 0.0f;       // mEffectTime of the last scrub press
    // Async audio-control worker: NanoAudio control ops (play/pause/stop/seek/open)
    // can block on the audio server / codec; running them on the render thread risks
    // a watchdog abort or a hang. The render thread enqueues a command (cheap) and a
    // dedicated worker executes the blocking op, so the render loop never stalls.
    enum class MpAudioCmd { Play, Pause, Stop, Seek, OpenPlay, Release };
    struct MpAudioReq { MpAudioCmd cmd; double arg; std::string path; bool radio = false; };
    std::mutex mMpAudioMutex;
    std::condition_variable mMpAudioCv;
    std::deque<MpAudioReq> mMpAudioQueue;
    bool mMpAudioStarted = false;
    void mpAudioCmd(MpAudioCmd cmd, double arg = 0.0, const std::string& path = std::string(), bool radio = false);
    void mpAudioWorker();
    // True while an auto-advance OpenPlay is in flight. Since open() is async now,
    // ended() stays true until the worker loads the next track; without this the
    // per-frame auto-advance would fire repeatedly and skip tracks.
    bool mMpAdvancing = false;
    int  mMpVolLevel = 0;              // -4..+4 (web volLevel); maps to (lvl+4)/8 gain
    float mMpEnterT = 0.0f;            // player-presence fade 0..1 (bar fades in), smoothstep of mMpEnterRaw
    float mMpEnterRaw = 0.0f;          // linear 0..1 enter progress (~1.0s), eased into mMpEnterT (web mpEnterRaw)
    float mMpChromeT = 1.0f;           // XMB chrome presence during the player enter cross-fade (1 full, 0 hidden)
    float mMpFullInfoT = 0.0f;         // full-info cluster fade 0..1
    // control panel (TRIANGLE)
    bool  mMpCpOpen = false;
    bool  mMpCpTouch = false;          // panel opened by a screen tap -> enlarge icons
    int   mMpCpSel = 0;                // index into MP_CP
    int   mMpCpSelPrev = -1;
    float mMpCpAnimStart = -1.0f;      // open slide/fade start (mEffectTime)
    float mMpCpFocusStart = -1.0f;     // focus-change ease start
    bool  mMpCpClosing = false;
    float mMpCpCloseStart = -1.0f;
    float mMpCpPressStart = -1.0f;     // button invoke flash
    int   mMpCpPressSel = -1;
    bool  mMpVolSub = false;           // Volume Control submenu inside the panel
    // transient transport glyph (skip/back/ff/rew flash ~900ms)
    int   mMpTransientIcon = -1;
    float mMpTransientUntil = 0.0f;
    // visualizer-name banner + full-screen message chain
    std::string mMpBanner; float mMpBannerStart = -1.0f;
    std::string mMpMsg; float mMpMsgStart = -1.0f; float mMpMsgDur = 0.0f; int mMpMsgThen = 0;
    std::map<int, GLuint> mMpIconCache;
    std::map<int, float> mMpIconAR;   // audioplayer icon aspect ratios (w/h; pills are non-square)
    GLuint mMpJacketTex = 0;
    // Per-track album art (one slot, reloaded when the displayed track changes): a
    // <folder>/<trackname>.<img> overrides a <folder>/<foldername>.<img> album cover;
    // mMpArtTex==0 means "no art for this track -> use the note placeholder".
    int    mMpArtTi = -1;
    GLuint mMpArtTex = 0;
    GLuint mpIcon(int n);              // load+cache an audioplayer icon texture (NanoMenuPS3Icons.cpp)
    float  mpIconAR(int n);           // cached aspect ratio (w/h) of an audioplayer icon, 1.0 if unknown
    GLuint mpJacket();                 // load+cache the default jacket cover texture
    GLuint mpTrackArt(int ti);        // folder/per-track album art for track ti (0 if none)
    void   mpFreeArt();               // free the cached art texture (on close / track change)
    // Per-album folder art for the XMB Music column (embedded in the album icon, like
    // the web photo folders). Cached by album name; 0 means "no art / tried".
    std::map<std::string, GLuint> mMpAlbumArt;
    GLuint mpAlbumArt(const std::string& albumName);
    void renderMusicPlayer();         // the Now-Playing fullscreen draw
    void openMpOpt(bool byTouch = false);  // open the control panel
    float mpPanelUi();                // mpUiScale, enlarged when opened by touch
    void closeMpOpt();                // close it (or the volume submenu first)
    void mpOptMove(int dx, int dy);   // grid nav (dx +right, dy +grid-up)
    void mpOptActivate();             // X on the focused control
    void mpOptBack();                 // Circle inside the panel (vol-sub -> panel -> close)
    void drawMpOpt(float closeT);     // render the panel (closeT>=0 drives the close anim)
    void drawMpVolMeter(float t);     // the Volume Control submenu meter
    void drawMpStatusRow(float ax, float fade);   // play-state/transport/repeat/shuffle row
    void mpShowMsg(const std::string& text, float durMs, int then);
    void mpCycleVis();                // SQUARE: cycle Waves/Canyon/Globe + wallpaper effects + banner
    int  mpVisCount() const;          // total music visualizers (3 + wallpaper-effect extras)
    int  mpVisEffectId() const;       // wallpaper-effect id for mMpVis>=3, else 0
    // Add-to-Playlist chooser (player): an XMB-style modal list to add the current
    // track to an existing playlist or create a new one (web mpOpenAddChooser).
    bool mMpPlChooserActive = false;
    std::vector<std::string> mMpPlChooserOpts;   // "New Playlist..." + existing names
    int   mMpPlChooserSel = 0;
    int   mMpPlChooserTrack = -1;                // track index being added
    float mMpPlChooserAnim = 0.0f;               // open fade
    void mpOpenAddChooser();          // build + open the chooser for the current track
    void mpPlChooserMove(int dir);    // up/down through the options
    void mpPlChooserSelect();         // commit the highlighted option
    void mpPlChooserCancel();         // dismiss without adding
    void drawMpPlChooser();           // render the modal list

    // ======================= Photo viewer (PS3 XMB port) =======================
    // Library model (nano_photo.json), folder import (reuses the folder picker via
    // mFolderPickTarget = 2), the scanner, the Photo-column groups + thumbnail grid,
    // the full-screen viewer and the in-viewer control panel (the same icon style /
    // spacing / focus treatment as the Music MP_CP control panel). 1:1 source of
    // truth: /work/ps3/xmb-app/index.html (the Photo DATA category, photoViewer,
    // drawPhotoGrid, PV_CP / drawPvPanel, drawPvInfo). Implemented in NanoMenuPhotos.cpp.
    struct PhotoItem {
        std::string file;     // absolute path
        std::string name;     // filename without extension (display)
        std::string date;     // "YYYY-MM-DD HH:MM" capture date (file mtime proxy)
        int w = 0, h = 0;     // native pixel dimensions
        int64_t sz = 0;       // file size in bytes
        int64_t mtime = 0;    // for incremental rescan
    };
    struct PhotoPlaylist {
        std::string name;
        std::vector<std::string> files;   // references PhotoItem.file
    };
    std::vector<std::string>   mPhotoFolders;
    std::vector<PhotoItem>     mPhotos;
    std::vector<PhotoPlaylist> mPhotoPlaylists;
    int64_t mPhotoCfgStamp = -1;          // mtime of nano_photo.json (cross-process reload)
    static const int kPhotoMetaVersion = 1;
    int  mPhotoCfgVersion = 0;
    bool mPhotoLoaded = false;            // library parsed once (lazy, first Photo entry)
    bool mPhotoCatsStale = false;         // a scan finished -> rebuild the Photo column at root
    int  mPhotoGroupIdx = 0;              // 0 By Month, 1 By Year, 2 By Album, 3 All
    // Sort By (web photoSortBy, 5 options): field 0 = film(EXIF) date, 1 = import
    // (file mtime) date, 2 = image name. dir 0 = desc, 1 = asc (name forced asc).
    // Default = Film Date ascending (web subDef 1, the live firmware default).
    int  mPhotoSortField = 0;
    int  mPhotoSortDir   = 1;
    bool photoSortLess(int a, int b) const;   // compare two photo indices by the current field+dir
    void photoApplySort();                    // re-group the column + re-sort the open grid in place
    void photoSetSort(int field, int dir);    // set + apply (option-menu submenu)
    void photoSortCycleY();                   // Y on the grid/folder: cycle the 5 sort options + banner
    std::string photoSortLabelCur() const;    // current sort label for the option-menu "Sort By" row
    // Transient centered banner over the photo column/grid (Sort By / Group Content
    // change feedback, web showGroupBanner). Empty start = inactive (no idle cost).
    std::string mPhotoBanner; float mPhotoBannerStart = -1.0f;
    void photoShowBanner(const std::string& text);
    void drawPhotoBanner();

    // ==== Global search (Select on the home XMB) ==========================
    // A categorized results overlay across every XMB section (Games, Music, Photos,
    // Videos). Lazy: nothing is built until the user runs a search; the result list
    // is freed on close so an idle launcher holds no search state. Reuses the OSK for
    // the query and each section's existing open/launch path to activate a result.
    struct GSearchResult {
        int section = 0;          // 0 Games, 1 Music, 2 Photo, 3 Video
        std::string label;        // primary display text
        std::string sub;          // secondary text (system / artist+album / date / codec)
        int kind = 0;             // dispatch kind (PS3_ROM / PS3_RECENT / PS3_APP / PS3_MUSIC_TRACK / PS3_VIDEO_FILE / PS3_PHOTO)
        int a = 0, b = 0;         // dispatch indices (kind specific)
        std::string payload;      // dispatch payload (app package)
    };
    bool mGSearchActive = false;
    std::string mGSearchQuery;
    std::vector<GSearchResult> mGSearchResults;
    int mGSearchSel = 0;          // selected result index (into mGSearchResults)
    int mGSearchScrollRow = 0;    // first visible visual row (results + section headers)
    float mGSearchAnim = 0.0f;    // open ease
    void gsearchOpen();           // Select pressed on the home XMB: open the query OSK
    void gsearchBuild(const std::string& q);  // run the search, populate results
    void gsearchClose();
    void gsearchMove(int dir);    // up/down through results
    void gsearchActivate();       // launch / open the selected result
    int  gsearchVisRow(int resultIdx) const;  // visual row of a result (counts preceding headers)
    void renderGlobalSearch();    // draw the overlay (returns nothing; caller gates on mGSearchActive)

    // VIDEO (R4) - the HW decoder instance for the player. Lazily created in
    // openVideoPlayer, fully torn down in closeVideoPlayer/videoTick so an idle launcher
    // holds no video resources.
    NanoVideo* mVideoTest = nullptr;
    // Decoders being torn down asynchronously (releaseAsync): the worker join + the
    // blocking OMX stop run entirely off the render thread, and vidReapDying frees each
    // wrapper once its background teardown completes. A LIST (not a single pointer): the
    // render thread must never block waiting on an in-flight teardown, so it can never
    // synchronously release a previous one to make room - it just queues another and
    // reaps them as they finish. Drains to empty when idle (no leak).
    std::vector<NanoVideo*> mVidDying;
    void vidAsyncFree(NanoVideo* v);   // hand v to async teardown + queue it for reaping
    void vidReapDying();               // free any queued decoder whose async teardown is done
    // Single HW video decoder: a new title's codec must not be created until the previous
    // title's codec has fully released it, or the create wedges (second-video-hangs-on-switch).
    // mVidPrevCodecFreed is true when no video-codec teardown is pending (mVidDying drained);
    // false on vidAsyncFree, back to mVidDying.empty() in vidReapDying (render thread). The open
    // WORKER is not spawned until it is true: vidBeginOpen DEFERS the spawn (mVidOpenDeferred)
    // and videoTick spawns it once the decoder is free. Nothing blocks the render thread.
    std::atomic<bool> mVidPrevCodecFreed{true};
    bool mVidOpenDeferred = false;     // open worker held until the prev codec frees the HW decoder
    void vidSpawnOpenWorker();         // spawn the blocking open worker (vidOpenTitle/StreamRun)
    // scan worker
    std::mutex mPhotoScanMutex;
    std::vector<PhotoItem> mPhotoScanResults;
    bool mPhotoScanReady = false;
    bool mPhotoScanRunning = false;
    bool mPhotoScanPending = false;       // scan deferred until external storage is mounted
    bool photoStorageReady() const;
    void photoRefresh();
    // persistence + lazy load
    int64_t photoConfigStamp() const;
    bool loadPhotoConfig();
    void savePhotoConfig();
    void photoEnsureLoaded();
    void photoOnCatFocus();
    // scan
    void photoScanAsync();
    void photoScanThreadFunc();
    void photoDrainScanResults();
    // image decode (AImageDecoder scaled-on-decode; NanoMenuPhotos.cpp)
    static bool photoProbeDims(const std::string& path, int* w, int* h, int64_t* sz);
    GLuint photoDecodeTex(const std::string& path, int maxDim, int* outW, int* outH);
    GLuint musicEmbeddedArt(const std::string& mp3path, int maxDim);   // ID3v2 APIC cover
    // folder import (Search for Media Servers; mFolderPickTarget = 2)
    void photoOpenFolders();
    void buildPhotoFoldersScreen(Ps3Level& out);
    void photoFolderSelect(const std::string& path);
    void photoRemoveFolder(int idx);
    // column content + grouping
    void buildPhotoColumnItems(std::vector<Ps3Item>& out);
    void photoCycleGroup();               // SQUARE in the Photo column -> next Group Content mode
    void photoSetGroup(int mode);         // option-menu Group Content submenu -> a specific mode
    struct PhotoGroup { std::string name; std::vector<int> idx; };
    std::vector<PhotoGroup> photoGroups() const;   // groups per the current mode
    static std::string fmtPhotoDate(const std::string& iso);   // "YYYY-MM-DD HH:MM" -> "D/M/YYYY H:MM"
    static std::string fmtFileSize(int64_t b);
    // thumbnail grid (screenKind PHOTO_GRID)
    std::vector<int> mPhotoGridList;      // photo indices in the open album/grid
    int  mPhotoGridCursor = 0;
    int  mPhotoGridTop = 0;               // top visible row (scroll, kept in sync with the pixel scroll)
    float mPhotoGridScrollY = 0.0f;       // smooth pixel scroll (finger-driven; source of truth for the render)
    float mPhotoGridScrollAnchor = 0.0f;  // mPhotoGridScrollY at the start of a drag
    std::string mPhotoGridTitle;
    float mPhotoGridAnim = 0.0f;          // open fade-in
    float mPhotoGridFocusStart = -1.0f;   // focus grow tween start
    int  mPhotoGridCursorPrev = -1;
    int  mPhotoGridFromPl = -1;           // playlist index if opened from a playlist, else -1
    std::map<int, GLuint> mPhotoThumbCache;   // photo idx -> thumbnail texture
    std::map<int, float>  mPhotoThumbAR;      // photo idx -> thumbnail aspect (w/h)
    void openPhotoGrid(const std::vector<int>& list, const std::string& title, int fromPl);
    void closePhotoGrid();
    void photoGridNav(int dx, int dy);
    void photoGridSelect();
    void renderPhotoGrid();
    GLuint photoThumb(int photoIdx);      // lazy thumbnail (maxDim ~256), cached
    void photoThumbEvict();               // bound the thumb cache around the cursor
    void photoFreeThumbs();               // drop the whole thumb cache (on grid close)
    // Group-folder cover: a centre-square thumbnail of the group's first photo,
    // drawn as the column icon (like the Music album art / the web photo folders).
    std::map<int, GLuint> mPhotoCoverCache;   // photo idx -> 160px square cover
    GLuint photoGroupCover(int photoIdx);
    void photoFreeCovers();
    // Draw a folder-shaped column icon (silver folder + tab) with an optional cover
    // photo / album art inset in the body, matching the web XMB photo folders. Shared
    // by the Photo group folders and the Music album folders.
    void drawFolderIcon(float ix, float iy, float dsz, float alpha, GLuint coverTex);

    // full-screen viewer
    bool  mPvActive = false;
    std::vector<int> mPvList;             // photo indices being viewed
    int   mPvIdx = 0;
    int   mPvRot = 0;                     // 0 / 90 / 180 / 270
    float mPvZoom = 1.0f;
    float mPvPanX = 0.0f, mPvPanY = 0.0f;
    float mPvEnterT = 0.0f, mPvEnterRaw = 0.0f;   // enter/exit fade from black (~0.4s)
    float mPvHintUntil = 0.0f;
    std::string mPvEffect;               // "Normal" / "Slide" / "Fade" (Change Effect)
    // photo-to-photo transition (Slide/Fade)
    bool  mPvTrans = false; int mPvTransFrom = -1; float mPvTransStart = 0.0f;
    int   mPvTransDir = 1; std::string mPvTransEffect;
    // interactive touch swipe: the photo tracks the finger horizontally, then either
    // completes to the neighbour or springs back on release (mobile-gallery feel).
    bool  mPvDragActive = false;          // finger currently dragging the photo
    float mPvDragDx = 0.0f;               // live horizontal offset (logical px)
    bool  mPvDragSettle = false;          // releasing: easing dx toward the target
    float mPvDragFrom = 0.0f, mPvDragTo = 0.0f, mPvDragSettleStart = 0.0f;
    int   mPvDragCommitDir = 0;           // step to apply when a commit settle finishes
    bool  mPvPanning = false;             // single-finger pan in progress (while zoomed)
    void  pvGoTo(int newIdx);             // jump to a photo with no Slide/Fade transition
    // decoded viewer textures (current +/- neighbours), keyed by photo idx
    std::map<int, GLuint> mPvTexCache;
    std::map<int, int> mPvTexW, mPvTexH;  // decoded texture dims
    GLuint pvTex(int photoIdx, int* w, int* h);
    void pvPrefetch();
    void pvFreeTextures();
    // Async display-size decode worker: the multi-MP decode runs OFF the render
    // thread (worker produces CPU RGBA); pvDrainDecodes uploads to GL on the render
    // thread each frame. Keeps slideshow/navigation at a locked 60fps and pre-decodes
    // direction-ahead neighbours so each advance is instant. (issue: viewer 60fps)
    struct PvDecReq { int idx; std::string path; int maxDim; uint64_t gen; };
    struct PvDecRes { int idx; int w; int h; uint64_t gen; std::vector<uint8_t> px; };
    std::thread mPvDecThread;
    std::mutex mPvDecMutex;
    std::condition_variable mPvDecCv;
    std::deque<PvDecReq> mPvDecQueue;
    std::vector<PvDecRes> mPvDecDone;
    std::set<int> mPvDecInFlight;
    std::atomic<bool> mPvDecStop{false};
    std::atomic<bool> mPvDecStarted{false};
    std::atomic<int>  mPvFocusIdx{0};        // photo idx the worker decodes first
    std::atomic<uint64_t> mPvDecGen{0};      // bumped on open/free to drop stale results
    int mPvMaxDim = 1920;
    static const int kPvTexCap = 5;          // current +/- 2 uploaded display textures
    void pvDecodeThreadFunc();
    void pvRequestDecode(int photoIdx);
    void pvDrainDecodes();
    void pvStartDecodeWorker();
    void pvStopDecodeWorker();               // stop + join (called from ~NanoMenu)
    void openPhotoViewer(const std::vector<int>& list, int idx);
    void closePhotoViewer();
    void pvStep(int d);
    void pvShow3D();
    // slideshow
    bool  mPvSlideshow = false, mPvPaused = false, mPvRepeat = false;
    float mPvSlideNext = 0.0f, mPvSlideMs = 4000.0f;
    int   mPvSlideStyle = 0;
    std::string mPvSlideSpeed;            // "Slow" / "Normal" / "Fast"
    // info / display mode
    bool  mPvInfo = false;
    std::string mPvDispModeName;          // "Zoom" / "Normal"
    float mPvDispModeUntil = 0.0f;
    // Set as Wallpaper / Trimming range selector
    bool  mPvWpMode = false, mPvTrimMode = false;
    float mPvWpZoom = 1.0f;
    void  pvWallpaperConfirm();
    void  pvShowDeleteConfirm();
    // transient full-screen message (Delete / 2D-3D / wallpaper-set), music-style
    std::string mPvMsg; float mPvMsgStart = -1.0f; float mPvMsgDur = 0.0f;
    void  pvShowMsg(const std::string& text, float durMs);
    // control panel (TRIANGLE) - mirrors the Music MP_CP look
    bool  mPvPanel = false;
    bool  mPvPanelTouch = false;   // panel opened by a screen tap -> enlarge icons for touch
    int   mPvCpSel = 0, mPvCpSelPrev = -1;
    float mPvCpAnimStart = -1.0f, mPvCpFocusStart = -1.0f;
    bool  mPvCpClosing = false; float mPvCpCloseStart = -1.0f;
    float mPvCpPressStart = -1.0f; int mPvCpPressSel = -1;
    // control submenu (Change Effect / Slideshow Speed / Slideshow Style)
    bool  mPvCpSub = false; std::string mPvCpSubKind; std::vector<std::string> mPvCpSubOpts; int mPvCpSubSel = 0;
    void renderPhotoViewer();
    void openPvPanel(bool byTouch = false);
    float pvPanelUi();   // pvUiScale, enlarged when the panel was opened by touch
    void closePvPanel();
    void pvPanelMove(int dx, int dy);
    void pvPanelActivate();
    void pvPanelBack();
    void drawPvPanel(float closeT);
    void drawPvInfo();
    void drawPvDispModePill();
    void drawPvWallpaperSel();
    // photoviewer icons (lazy, /data override + /system/etc fallback)
    std::map<int, GLuint> mPvIconCache;
    std::map<int, float>  mPvIconAR;
    GLuint pvIcon(int n);
    float  pvIconAR(int n);
    // photo playlists
    void buildPhotoPlaylistsScreen(Ps3Level& out);
    void buildPhotoPlaylistGridList(int plIdx, std::vector<int>& out, std::string& title);
    void photoCreatePlaylist(const std::string& name);
    void photoAddToPlaylist(int plIdx, const std::string& file);
    // add-to-playlist chooser (viewer + grid)
    bool  mPvPlChooserActive = false;
    std::vector<std::string> mPvPlChooserOpts;   // "New Playlist..." + existing names
    int   mPvPlChooserSel = 0;
    std::string mPvPlChooserFile;                // photo file being added
    float mPvPlChooserAnim = 0.0f;
    void pvOpenAddChooser(const std::string& file);
    void pvPlChooserMove(int dir);
    void pvPlChooserSelect();
    void pvPlChooserCancel();
    void drawPvPlChooser();
    void pvSlideshowStart(const std::vector<int>& list, int idx, int style);
    void photoTick();                     // per-frame: viewer enter fade + slideshow + timers
    // Multi-select (Delete Multiple / Copy Multiple) checkbox screen (web photoMulti):
    // checkbox + thumbnail + name + date rows, Select All / Clear All / OK buttons.
    bool mPhotoMultiActive = false;
    int  mPhotoMultiMode = 0;             // 0 = delete, 1 = copy
    std::vector<int> mPhotoMultiItems;    // photo indices in the open album/grid
    int  mPhotoMultiSel = 0;
    int  mPhotoMultiBtn = -1;             // -1 list, 0 Select All, 1 Clear All, 2 OK
    std::set<int> mPhotoMultiChecked;     // checked row indices
    void photoMultiOpen(int mode);
    void photoMultiMove(int d);           // up/down (list or button column)
    void photoMultiLR(int d);             // left = list, right = the side buttons
    void photoMultiActivate();            // X
    void photoMultiClose();
    void renderPhotoMulti();
    void drawPhotoMsg();                  // shared transient-message render (viewer/grid/multi)

    std::vector<Ps3Item>& ps3CurItems();   // current visible item list (top or submenu)
    int& ps3CurSel();
    void renderPs3Xmb();
    void drawPs3Clock(float fadeMul);   // U-frame + analog face + DD/M H:MM
    // Content-info hover background + description (web HOVER_BG/CINFO_DESC): dwell on a
    // mapped item ("Photo Gallery" is the only one reachable in nano) fades a full-frame
    // bg image + firmware title/description in over the wave, under the chrome.
    // fanFile non-empty => draw a scraped ROM fanart background (no description)
    // instead of the Photo Gallery cinfo bg; the two are mutually exclusive.
    void drawPs3CinfoBg(const char* focusLabel, const std::string& fanFile = std::string());
    GLuint mCinfoTex = 0;            // lazily-loaded cinfo background texture
    bool   mCinfoTexTried = false;   // load attempted (don't retry on failure)
    int    mCinfoTexW = 0, mCinfoTexH = 0;
    float  mCinfoAlpha = 0.0f;       // current visible alpha (0 .. 0.85)
    std::string mCinfoFocusKey;      // currently focused mapped item name (or empty)
    float  mCinfoDwellStart = -1.0f; // mEffectTime when focus moved to the mapped item
    // The texture actually on screen last frame, so a focus move to an item with NO
    // cinfo (a game without fanart) fades OUT whatever was shown instead of cross-
    // showing the Photo Gallery bg. Not owned (aliases mFanartTex / mCinfoTex).
    GLuint mCinfoShownTex = 0;
    int    mCinfoShownW = 0, mCinfoShownH = 0;
    // Scraped ROM fanart hover background (Phase 4): one texture at a time, reloaded
    // when the focused ROM changes, freed on fade-out / leaving Game / occlusion.
    GLuint mFanartTex = 0;
    int    mFanartTexW = 0, mFanartTexH = 0;
    std::string mFanartPath;         // path currently loaded into mFanartTex
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
    // System Language picker (NanoMenuPS3Menu.cpp). Settings -> System Settings ->
    // System Language opens the SAME fullscreen language list as the first-run
    // setup wizard, with live locale preview as the cursor moves and
    // persist.sys.locale applied on confirm. mPs3LangActive gates the XMB view.
    void openLanguagePicker();             // open from the System Language item
    void closeLanguagePicker(bool apply);  // close (apply = persist + keep the locale)
    void langPickerNav(int dir);           // move the highlighted language (clamped)
    void renderLanguagePicker();           // XMB entry: live preview + the shared list
    void renderLanguageList(const char* title, float alpha);  // shared chrome (wizard + XMB)
    // Glass icon pipeline (NanoMenuPS3Icons.cpp).
    void initGlassIcons();                 // compile program, load amb/env textures
    GLuint nmapForIcon(int iconIndex);     // load+cache nmap_NNN.png
    GLuint bevelForIconIdx(int iconIdx);   // bevel normal from a console icon's alpha
    GLuint bevelFromRGBA(const uint8_t* px, int w, int h);   // bevel normal from any silhouette buffer
    // Resolve a system iconRef (builtin:/retroarch:/core:/file:) to a (colour
    // tex, glass bevel nmap) pair, cached by ref string. See NanoMenuPS3Icons.cpp.
    void resolveSystemIcon(const std::string& ref, GLuint* outTex, GLuint* outNmap);
    // Decode a RetroArch icon (by name, no .png) to a mono-white RGBA buffer.
    // Resolves the dev override then the bundled set. Used by the grid picker.
    bool decodeRetroIconRGBA(const std::string& name, std::vector<uint8_t>* outPx, int* w, int* h);
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
    bool   mSetupBtWizSeen = false;       // setup-wizard tracking of the Bluetooth step's wizard
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
    // Network: Internet Browser / Internet Search (launch the com.gammaos.browser app).
    void openInternetBrowser();
    void openInternetSearch();
    void openGoToUrl();                  // OSK prefilled https:// -> launch the typed URL
    bool tryOpenSearchEngineChooser();   // X/Triangle: Internet Search -> engine chooser, Internet Browser -> Go to URL
    void launchUrl(const std::string& url);
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

    // Accessory Settings Bluetooth wizard (PS3 UI). Reuses the net-wizard
    // screen machinery (WS_BT_* screens, renderNetWizard, wizConfirm/Nav/Back)
    // but drives the real gammaos-net bt backend on background threads.
    int  mBtWizMode = 0;                      // 0 Manage, 1 BD Remote, 2 Audio Device
    std::vector<BtDevEntry> mBtWizBonded;     // bonded devices for the Manage chooser
    std::vector<BtDevEntry> mBtWizScan;       // discovered devices for the Register list
    std::mutex mBtWizMutex;                   // guards mBtWizBonded / mBtWizScan
    std::atomic<bool> mBtWizBusy{false};      // a bg scan/pair/connect/disconnect/unpair is running
    std::atomic<bool> mBtWizOpOk{false};      // result of the last bg op (for the done screen)
    std::string mBtWizSelAddr, mBtWizSelName; // selected bonded device (opts / info)
    int  mBtWizSelCod = 0;                    // its class-of-device
    bool mBtWizSelConnected = false;          // its live connection state
    int  mBtWizAdInput = 0, mBtWizAdOutput = 0, mBtWizAdMic = 2;  // audio device choices
    std::string mBtWizPin;                    // user PIN entered for outbound classic pairing
    int  mBtWizInVariant = -1;                // inbound pairing variant (0 = classic PIN)
    std::string mBtWizInPasskey;              // inbound passkey to display for confirmation
    std::atomic<bool> mBtWizRadioOn{true};    // cached real radio state (dumpsys enabled:)
    std::atomic<bool> mBtWizToggling{false};  // a radio enable/disable is settling
    float mBtWizManageRefreshT = -999.0f;     // last live-refresh time while sitting on Manage
    void btWizToggleRadioAsync(bool on);      // enable/disable the radio + settle + refresh

    void startBtWizard(int mode);             // mode 0 Manage, 1 BD Remote, 2 Audio Device
    void btWizRefreshBondedAsync();           // gammaos-net bt list-bonded -> mBtWizBonded
    void btWizScanAsync();                    // gammaos-net bt scan -> mBtWizScan
    void btWizPairAsync(const std::string& addr, const std::string& pin);
    void btWizConnectAsync(const std::string& addr);
    void btWizDisconnectAsync(const std::string& addr);
    void btWizUnpairAsync(const std::string& addr);
    void btWizStartReceive();                 // discoverable + open inbound bridge
    void btWizStopReceive();                  // stop discoverable + close inbound bridge
    void btWizInboundAcceptAsync(const std::string& addr, int variant);  // confirm + await bond
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
    GLuint mIconTextures[19]; // 0-14=systems, 15=history, 16=generic game cartridge, 17=setting, 18=Applications app-grid

    // On-screen keyboard. mOskActive + mOskQuery are the keep-stable members
    // external code reads/writes directly; all new runtime state is in mOsk.
    bool mOskActive;           // OSK is visible and receiving input
    bool  mOskGlassValid = false;  // OSK frosted-panel blur cached (avoid per-frame full-FB resolve)
    float mOskGlassT = 0.0f;       // mEffectTime of the last OSK panel capture
    std::string mOskQuery;     // current typed buffer (committed text)
    NanoOskState mOsk;         // page/shift/focus/caret/popup/candidates/IME state
    // Touchscreen OSK input. The digitizer is read straight from the same evdev
    // stream (ABS_MT_POSITION_X/Y + BTN_TOUCH); ranges are read lazily via
    // EVIOCGABS. Raw -> panel-normalized -> logical is corrected per device by
    // the osk_touch_swap/flipx/flipy props (DRM-rotated panels need them; SF and
    // upright panels use the defaults). Works on both DRM and SF back-ends.
    int   mTouchMinX = 0, mTouchMaxX = 0;   // digitizer X range (max<=min = unread)
    int   mTouchMinY = 0, mTouchMaxY = 0;   // digitizer Y range
    int   mTouchRawX = -1, mTouchRawY = -1; // last raw ABS_MT position (slot 0)
    bool  mTouchDown = false, mTouchWasDown = false;
    // Multi-touch slot tracking (Type-B) for pinch-zoom: two contacts is enough.
    int   mTouchSlot = 0;                   // current ABS_MT_SLOT selector
    int   mTouchId[2]  = { -1, -1 };        // per-slot tracking id (-1 = no contact)
    int   mTouchSX[2]  = { 0, 0 };          // per-slot raw X
    int   mTouchSY[2]  = { 0, 0 };          // per-slot raw Y
    bool  mPvPinchActive = false;           // two-finger pinch in progress (photo)
    float mPvPinchStartDist = 0.0f, mPvPinchStartZoom = 1.0f;
    bool  touchMapRaw(int rawX, int rawY, float& px, float& py);  // raw -> logical px
    bool  mOskTouchTuneRead = false;
    bool  mOskTouchSwap = false, mOskTouchFlipX = false, mOskTouchFlipY = false;
    // XMB touch-navigation gesture state. Positions are in logical pixels (post
    // swap/flip), matching oskTouchFrame's px/py output. A released vertical drag
    // hands mPs3AnimItem to an inertial fling that settles on the nearest item.
    bool    mXmbTouchTracking = false;   // a finger-down gesture is being followed
    int     mXmbTouchMode = 0;           // 0 undecided, 1 item drag, 2 category swipe, 3 option panel
    bool    mXmbTouchMoved = false;      // exceeded the slop -> a drag, not a tap
    bool    mXmbTouchLongFired = false;  // long-press already opened the side menu this gesture
    int64_t mXmbTouchDownMs = 0;         // press start (uptimeMillis)
    float   mXmbTouchDownPX = 0.0f, mXmbTouchDownPY = 0.0f;   // press position
    float   mXmbTouchLastPX = 0.0f, mXmbTouchLastPY = 0.0f;   // previous-frame position
    int64_t mXmbTouchLastMs = 0;
    float   mXmbTouchAnchorItem = 0.0f;  // mPs3AnimItem at the start of a vertical drag
    float   mXmbTouchCatAccum = 0.0f;    // accumulated horizontal virtual-px toward a category step
    float   mXmbItemVel = 0.0f;          // item scroll velocity (rows/sec), tracked then flung
    bool    mXmbItemFling = false;       // inertial glide active
    int     mXmbDlgScrollBase = 0;       // rich-info dialog scroll value at the start of a drag
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
    // Guards against re-resetting the glyph atlas more than once per frame: the
    // atlas is recycled (cache cleared, cursor rewound) the first time it fills
    // mid-frame so glyphs keep rasterizing instead of re-reading the compressed
    // font off EROFS every frame (a sustained decompress storm -> kernel OOM).
    // render() clears this at the top of each frame.
    bool mGlyphAtlasReset = false;
    void resetGlyphAtlas();
    // Currently-set atlas MIN_FILTER (texture-object state). setGlyphAtlasAA()
    // flips this to GL_LINEAR_MIPMAP_NEAREST only around the home-XMB menu
    // content and back to GL_LINEAR everywhere else, skipping redundant GL sets.
    GLint mGlyphAtlasMinFilter = GL_LINEAR;
    // Keyed by (rasterPx << 32 | codepoint): one entry per glyph per display
    // size, so each is rendered crisp at its native pixel size.
    std::unordered_map<uint64_t, GlyphInfo> mGlyphCache;
    // Scale-independent text widths keyed by string (see measureText). Never
    // invalidated: the font size is fixed at init and glyphs only get added.
    std::unordered_map<std::string, float> mTextWidthCache;

    // Text shader (per-vertex color for emoji support)
    GLuint mTextProgram;
    GLint  mTextLocPosition;
    GLint  mTextLocTexCoord;
    GLint  mTextLocColor;
    GLint  mTextLocTexture;
    GLint  mTextLocRotation;
    GLint  mTextLocSharp = -1;   // uSharp uniform: crisp analytic edge AA amount
    float  mTextSharp = 0.0f;    // current uSharp value (set by setGlyphAtlasAA), uploaded by drawText

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
    // Per-vertex-alpha triangle in logical device px, single RGB. Used to build
    // anti-aliased procedural shapes (feathered edges ramp alpha to 0). Emits into
    // the solid batch (per-vertex colour) when active; falls back to a flat solid
    // triangle at the mean alpha otherwise.
    void triAA(float x0, float y0, float a0,
               float x1, float y1, float a1,
               float x2, float y2, float a2,
               float r, float g, float b);
    // Snapshot the framebuffer region [x,y,w,h] (logical px) into mGlassTex.
    // Returns false if capture is unavailable (e.g. active DRM GL rotation).
    bool captureGlass(float x, float y, float w, float h);
    void freeGlassScratch();   // release glass-blur scratch buffers (parked overlay)
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
    // System Language picker (Settings -> System Settings -> System Language).
    bool mPs3LangActive = false;  // gates the XMB fullscreen language picker
    int mLangSelOnOpen = 0;       // mLangSelected when the picker opened (revert on cancel)
    float mPs3LangAnim = 0.0f;    // 0->1 open transition (frosted backdrop + fade-in)
    // Setup script log tailing
    std::vector<std::string> mSetupLogLines;
    // Cached word-wrapped tail of the install log (text + colour kind). Re-wrapped
    // only when the log grows or the frame width changes so the running setup
    // script never makes the Configuring screen re-wrap every frame.
    std::vector<std::pair<std::string, int>> mSetupLogRows;
    int   mSetupLogRowsForLines = -1;
    float mSetupLogRowsForW = -1.0f;
    int mSetupLogScrollTop;
    bool mSetupScriptRunning;
    bool mSetupScriptDone;
    std::thread mSetupLogThread;
    std::mutex mSetupLogMutex;
    bool mSetupLogExitRequested;
};

} // namespace android

#endif // GAMMAOS_NANO_MENU_H
