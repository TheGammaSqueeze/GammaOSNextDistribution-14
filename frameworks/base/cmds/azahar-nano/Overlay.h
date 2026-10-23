// Overlay: the in-game menu of azahar-nano (short press of the Back/menu key).
//
// The core is paused while the menu is open. The menu renders with GLES2 (the
// drastic-nano OverlayGfx helper: FreeType text, rects, panels) into its own
// ANativeWindow, which the DRM presenter scans out on the top panel in place of
// the core's frames (DrmWindows::setOverlayActive). Drawing happens on demand
// (every state change), so the presenter simply holds the last frame.
//
// Pages: the top page (resume, save states, exit) and option pages (Video,
// Audio, System, Controls) whose rows map to config.ini keys of the Azahar app,
// written through the core's config writer and applied with a settings reload.
#pragma once

#include <EGL/egl.h>
#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <functional>
#include <mutex>
#include <thread>
#include <string>
#include <vector>

#include "OverlayGfx.h"
#include "DrasticOsk.h"

struct ANativeWindow;

namespace azahar_nano {

struct OverlayActions {
    std::function<void()> resume;                       // close the menu, unpause
    std::function<void(int slot)> saveState;
    std::function<void(int slot)> loadState;
    std::function<void()> exitGame;
    // config.ini access + live reload (azn_set_config_value / azn_reload_settings)
    std::function<std::string(const char* section, const char* key)> getConfig;
    std::function<void(const char* section, const char* key, const char* value)> setConfig;
    std::function<void()> reloadSettings;
    // persistent frontend properties (GPU driver, pad layout, counters)
    std::function<void(const char* prop, const char* value)> setProp;
    std::function<std::string(const char* prop, const char* def)> getProp;
    std::function<void(bool nintendo)> setNintendoLayout;
    std::function<void(bool on)> setStickDpadSwap;
    std::function<void(int screen, int shader)> setLcdShader;  // 0 top, 1 bottom
    std::function<void(bool on)> setScreenAa;                   // FXAA on both screens
    // Per-game override (AzaharSettings.h): config.ini as the file holds it (never the
    // override), and the hook run after an override was created or deleted.
    std::function<std::string(const char* section, const char* key)> getGlobalConfig;
    std::function<void()> overrideChanged;
    // Cheats (CheatDb.h + the core's list): count and entries by the core's index, toggling,
    // saving the enabled marks, the user's own cheats (the first customCount entries) added
    // and removed through the custom file, and the paths shown on the Cheat files row.
    std::function<int()> cheatCount;
    std::function<bool(int index, std::string* name, std::string* comments, bool* enabled)> cheatGet;
    std::function<void(int index, bool enabled)> cheatSetEnabled;
    std::function<void()> cheatsSave;
    std::function<int()> cheatCustomCount;
    std::function<bool(const std::string& name, const std::string& code, std::string* err)> cheatAddCustom;
    std::function<bool(int customIndex)> cheatRemoveCustom;
    std::function<std::string()> cheatFilesHint;
    // The bottom panel shows the keyboard while one is open (its overlay source is switched
    // on and off around it); without a bottom window the keyboard draws over the menu.
    std::function<void(bool on)> setBottomOverlay;
};

class Overlay {
public:
    // upright: a portrait buffer for a landscape picture (90 degrees); turn180: the panel
    // is mounted upside down and the plane cannot turn it, so the menu is drawn turned.
    bool init(ANativeWindow* win, int bufW, int bufH, bool upright, const OverlayActions& a,
              const std::string& dataDir, bool turn180 = false);
    // The bottom panel's overlay window: the keyboard is drawn there (touchable), as the
    // drastic-nano menu does, with the page staying on the top panel.
    bool initBottom(ANativeWindow* win, int bufW, int bufH, bool upright, bool turn180 = false);
    void shutdown();

    void open();
    void close();
    bool isOpen() const { return mOpen; }

    // AZN_* button codes; returns true when consumed (always while open).
    bool onButton(int button, bool pressed);
    // A touch on a panel as a fraction of it: the keyboard on the bottom panel takes it (a press
    // presses the key under the finger, a move slides the focus). Returns true when consumed.
    bool onTouch(int panel, float nx, float ny, bool pressed);

    // Draws the current state and presents it (any thread; the EGL context is
    // bound for the call and released after).
    void render();
    // The display colour transform for both overlay windows (DRM path).
    void setColorMatrix(const float top[12], const float bottom[12]) { mGfx.setColorMatrix(top); mGfxBottom.setColorMatrix(bottom); }
    // Loading screen while the core prepares its shader cache: a title, a detail
    // line and a progress bar (frac 0..1, negative = no bar).
    // Full-panel progress screen: title, a bar (frac 0..1; frac < 0 draws an indeterminate bar
    // whose sweep follows phase, or no bar when phase < 0 too), the detail line under it and an
    // optional dimmer note explaining what is going on.
    void renderLoading(const char* title, const char* detail, float frac,
                       const char* note = nullptr, float phase = -1.0f);

private:
    enum RowKind { ROW_ACTION, ROW_CHOICE, ROW_PAGE };
    struct Choice {
        std::string label;
        std::string value;   // config / property value
    };
    struct Row {
        std::string label;
        RowKind kind = ROW_ACTION;
        std::vector<Choice> choices;        // ROW_CHOICE
        int sel = 0;
        bool stopAtEnds = false;            // ROW_CHOICE: no wrap (a level, not a list)
        std::function<void()> activate;     // ROW_ACTION / ROW_PAGE
        std::function<void(int)> change;    // ROW_CHOICE: new index
        std::string hint;
        int page = -1;                      // ROW_PAGE target
    };
    struct Page {
        std::string title;
        std::vector<Row> rows;
        int cursor = 0;
    };
    // Row builders
    Row configChoice(const char* label, const char* section, const char* key,
                     std::vector<Choice> choices, const char* hint, bool restart = false);
    Row configToggle(const char* label, const char* section, const char* key, const char* hint,
                     bool restart = false);
    Row propChoice(const char* label, const char* prop, const char* def, std::vector<Choice> choices,
                   const char* hint, std::function<void(const std::string&)> onChange = nullptr);
    void buildPages();
    void scanGpuDrivers();
    // A yes/no page: the question is its title, the detail its hint; yes runs onYes.
    void openConfirm(const std::string& question, const std::string& detail,
                     std::function<void()> onYes);
    void createPerGameOverride();
    void deletePerGameOverride();
    // The Cheats page (search, show filter, add / remove custom, one toggle row per cheat)
    // and the Remove Custom Cheat page, rebuilt from the core's list.
    void buildCheatPages();
    void startAddCheat();
    bool makeCurrent();

    OverlayActions mA;
    std::string mDataDir;
    std::vector<Page> mPages;
    std::vector<int> mStack;             // page navigation stack (indices into mPages)
    int mSlot = 1;
    // "" = the default for this chip, "system" = the vendor driver, else a package name.
    std::vector<std::string> mDrivers;
    std::vector<std::string> mDriverZips; // importable packages (full paths)
    std::atomic<bool> mOpen{false};
    bool mRebuildPages = false;
    bool mRebuildTop = false;            // rebuild and return to the top page (override rows)
    bool mRebuildCheats = false;         // rebuild the cheat pages in place (filter, add, remove)
    int mReturnPage = -1;                // after a full rebuild, come back to this page
    // Cheats page state and the on-screen keyboard (search text, new cheat name and code)
    android::drastic_overlay::DrasticOsk mOsk;
    std::string mCheatFilter;            // lower case substring of the name
    int mCheatShow = 0;                  // 0 all, 1 enabled, 2 disabled, 3 built-in, 4 custom
    bool mCheatsDirty = false;           // enabled marks changed: saved when the menu closes
    // Every user-facing setting the pages hold, for the override snapshot: property short
    // keys and config.ini (section, key) pairs, collected while the pages are built.
    std::vector<std::string> mPropKeys;
    std::vector<std::pair<std::string, std::string>> mConfigKeys;
    bool mUpright = false;
    int mBufW = 0, mBufH = 0;
    std::string mBanner;

    EGLDisplay mDpy = EGL_NO_DISPLAY;
    EGLSurface mSurf = EGL_NO_SURFACE;
    EGLContext mCtx = EGL_NO_CONTEXT;
    android::drastic_gfx::OverlayGfx mGfx;
    bool mGfxReady = false;
    // the bottom panel (keyboard) surface, drawn with the same context
    EGLSurface mSurfBottom = EGL_NO_SURFACE;
    android::drastic_gfx::OverlayGfx mGfxBottom;
    bool mGfxBottomReady = false;
    int mBufWBottom = 0, mBufHBottom = 0;
    bool mOskShown = false;              // the bottom overlay is on for the keyboard
    void renderBottom();
    void syncOskPanel();

    // Rendering, input and open/close run on several threads (input, prop hooks, the marquee
    // ticker): one lock for the page state and the EGL context. Recursive because a menu
    // action can close the overlay from inside onButton.
    std::recursive_mutex mMu;
    // Help line marquee: a hint wider than its box slides to its end and back, clipped to the
    // box. The ticker redraws while the menu is open and the hint overflows.
    std::string mHintShown;
    int64_t mHintStartNs = 0;
    std::atomic<bool> mMarquee{false};
    std::thread mTicker;
    std::mutex mTickMu;
    std::condition_variable mTickCv;
    bool mTickStop = false;
    void tickerLoop();
    float marqueeOffset(float overflow, float scale);

    // Clock and battery in the panel header (read from the power_supply sysfs nodes, a few
    // times a minute; the ticker redraws when the minute or the charge changes).
    void readBattery();
    float drawStatus(float right, float y, float scale);   // returns the left edge drawn to
    int mBatteryPercent = -1;            // -1 = unknown
    bool mBatteryCharging = false;
    int64_t mBatteryReadNs = 0;
    int mShownMinute = -1;
    int mShownBattery = -1;
};

} // namespace azahar_nano
