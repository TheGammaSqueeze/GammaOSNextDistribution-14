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
    std::function<void(int screen, int shader)> setLcdShader;  // 0 top, 1 bottom
    std::function<void(bool on)> setScreenAa;                   // FXAA on both screens
};

class Overlay {
public:
    bool init(ANativeWindow* win, int bufW, int bufH, bool upright, const OverlayActions& a,
              const std::string& dataDir);
    void shutdown();

    void open();
    void close();
    bool isOpen() const { return mOpen; }

    // AZN_* button codes; returns true when consumed (always while open).
    bool onButton(int button, bool pressed);

    // Draws the current state and presents it (any thread; the EGL context is
    // bound for the call and released after).
    void render();
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
    bool makeCurrent();

    OverlayActions mA;
    std::string mDataDir;
    std::vector<Page> mPages;
    std::vector<int> mStack;             // page navigation stack (indices into mPages)
    int mSlot = 1;
    std::vector<std::string> mDrivers;   // "" = system driver, else folder name
    std::vector<std::string> mDriverZips; // importable packages (full paths)
    std::atomic<bool> mOpen{false};
    bool mRebuildPages = false;
    bool mUpright = false;
    int mBufW = 0, mBufH = 0;
    std::string mBanner;

    EGLDisplay mDpy = EGL_NO_DISPLAY;
    EGLSurface mSurf = EGL_NO_SURFACE;
    EGLContext mCtx = EGL_NO_CONTEXT;
    android::drastic_gfx::OverlayGfx mGfx;
    bool mGfxReady = false;

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
};

} // namespace azahar_nano
