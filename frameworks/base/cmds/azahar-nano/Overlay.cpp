#define LOG_TAG "azahar-nano"

#include "Overlay.h"
#include <time.h>
#include <algorithm>
#include <cmath>
#include "AzaharNanoApi.h"
#include "GpuDriverPkg.h"
#include "Brightness.h"
#include "AzaharSettings.h"

#include <dirent.h>
#include <stdio.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>

#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <android/native_window.h>
#include <log/log.h>

using android::drastic_gfx::Color;
using android::drastic_gfx::rgba;

namespace azahar_nano {

namespace {
constexpr int kPageTop = 0, kPageVideo = 1, kPageAudio = 2, kPageSystem = 3, kPageControls = 4,
              kPageDrivers = 5, kPageConfirm = 6;
}

bool Overlay::init(ANativeWindow* win, int bufW, int bufH, bool upright, const OverlayActions& a,
                   const std::string& dataDir) {
    mA = a;
    mDataDir = dataDir;
    mBufW = bufW;
    mBufH = bufH;
    mUpright = upright;

    mDpy = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (mDpy == EGL_NO_DISPLAY || !eglInitialize(mDpy, nullptr, nullptr)) {
        ALOGE("overlay: eglInitialize failed 0x%x", eglGetError());
        return false;
    }
    const EGLint cfgAttrs[] = {EGL_SURFACE_TYPE, EGL_WINDOW_BIT, EGL_RENDERABLE_TYPE,
                               EGL_OPENGL_ES2_BIT, EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8,
                               EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_NONE};
    EGLConfig cfg;
    EGLint n = 0;
    if (!eglChooseConfig(mDpy, cfgAttrs, &cfg, 1, &n) || n < 1) {
        ALOGE("overlay: no EGL config");
        return false;
    }
    const EGLint ctxAttrs[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    mCtx = eglCreateContext(mDpy, cfg, EGL_NO_CONTEXT, ctxAttrs);
    mSurf = eglCreateWindowSurface(mDpy, cfg, (EGLNativeWindowType)win, nullptr);
    if (mCtx == EGL_NO_CONTEXT || mSurf == EGL_NO_SURFACE) {
        ALOGE("overlay: EGL context/surface failed 0x%x", eglGetError());
        return false;
    }
    if (!makeCurrent()) return false;
    // Logical layout space: the panel as the user sees it. OverlayGfx takes a 2x2
    // clip-space matrix; 90 degrees for the upright (portrait buffer) panel.
    // Verified by capture against the game's upright frames: the panel's top edge
    // is the buffer's left edge, which is this matrix.
    const int logicalW = upright ? bufH : bufW;
    const int logicalH = upright ? bufW : bufH;
    const float rotUpright[4] = {0.0f, 1.0f, -1.0f, 0.0f};
    const float rotNone[4] = {1.0f, 0.0f, 0.0f, 1.0f};
    if (!mGfx.init(logicalW, logicalH, upright ? rotUpright : rotNone)) {
        ALOGE("overlay: OverlayGfx init failed");
        return false;
    }
    mGfxReady = true;
    eglMakeCurrent(mDpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    scanGpuDrivers();
    buildPages();
    ALOGI("overlay: ready %dx%d upright=%d logical %dx%d", bufW, bufH, upright, logicalW, logicalH);
    return true;
}

bool Overlay::makeCurrent() {
    if (!eglMakeCurrent(mDpy, mSurf, mSurf, mCtx)) {
        ALOGE("overlay: eglMakeCurrent failed 0x%x", eglGetError());
        return false;
    }
    return true;
}

static int64_t monoNs();

void Overlay::tickerLoop() {
    std::unique_lock<std::mutex> lk(mTickMu);
    int tick = 0;
    while (!mTickStop) {
        mTickCv.wait_for(lk, std::chrono::milliseconds(33));
        if (mTickStop) break;
        if (!mOpen) continue;
        bool redraw = mMarquee;
        if (++tick % 30 == 0) {
            // Once a second: the clock minute or the battery reading may have changed.
            time_t now = time(nullptr);
            struct tm local;
            const int minute = localtime_r(&now, &local) ? local.tm_hour * 60 + local.tm_min : -1;
            readBattery();
            const int battery = mBatteryPercent * 2 + (mBatteryCharging ? 1 : 0);
            if (minute != mShownMinute || battery != mShownBattery) redraw = true;
        }
        if (!redraw) continue;
        lk.unlock();
        render();
        lk.lock();
    }
}

void Overlay::readBattery() {
    const int64_t now = monoNs();
    if (mBatteryReadNs != 0 && now - mBatteryReadNs < 5000000000LL) return;
    mBatteryReadNs = now;
    int fd = ::open("/sys/class/power_supply/battery/capacity", O_RDONLY | O_CLOEXEC);
    if (fd >= 0) {
        char buf[16] = {};
        const ssize_t n = ::read(fd, buf, sizeof(buf) - 1);
        ::close(fd);
        if (n > 0) mBatteryPercent = std::min(100, std::max(0, atoi(buf)));
    }
    fd = ::open("/sys/class/power_supply/battery/status", O_RDONLY | O_CLOEXEC);
    if (fd >= 0) {
        char buf[32] = {};
        const ssize_t n = ::read(fd, buf, sizeof(buf) - 1);
        ::close(fd);
        mBatteryCharging = n > 0 && (strncmp(buf, "Charging", 8) == 0 || strncmp(buf, "Full", 4) == 0);
    }
}

// Clock and battery at the right end of the panel header: "12:34" then a battery icon with
// its charge drawn as the fill and the percentage after it. Returns the x where it starts.
float Overlay::drawStatus(float right, float y, float scale) {
    readBattery();
    const float textScale = 1.0f * scale;
    const float lineH = mGfx.fontLineH() * textScale;
    time_t now = time(nullptr);
    struct tm local;
    char clock[16] = {};
    if (localtime_r(&now, &local)) {
        strftime(clock, sizeof(clock), "%H:%M", &local);
        mShownMinute = local.tm_hour * 60 + local.tm_min;
    }
    mShownBattery = mBatteryPercent * 2 + (mBatteryCharging ? 1 : 0);
    float x = right;
    if (mBatteryPercent >= 0) {
        char pct[16];
        snprintf(pct, sizeof(pct), mBatteryCharging ? "+%d%%" : "%d%%", mBatteryPercent);
        const auto col = mBatteryCharging ? rgba(0.25f, 0.9f, 0.35f, 1.0f)
                                      : mBatteryPercent <= 15 ? rgba(0.95f, 0.25f, 0.25f, 1.0f)
                                                              : rgba(0.85f, 0.88f, 0.92f, 1.0f);
        const float pw = mGfx.measure(pct, textScale);
        x -= pw;
        mGfx.text(pct, x, y, textScale, col);
        // Icon: body outline, a tip on the right, the charge as the fill.
        const float bh = lineH * 0.55f, bw = bh * 1.9f, bt = std::max(1.0f, 2.0f * scale);
        const float tipW = bh * 0.18f, tipH = bh * 0.45f;
        x -= 10 * scale + tipW + bw;
        const float by = y + (lineH - bh) * 0.5f;
        mGfx.fillRect(x, by, bw, bh, col);
        mGfx.fillRect(x + bt, by + bt, bw - 2 * bt, bh - 2 * bt, rgba(0.08f, 0.09f, 0.12f, 1.0f));
        mGfx.fillRect(x + bw, by + (bh - tipH) * 0.5f, tipW, tipH, col);
        const float fillW = (bw - 4 * bt) * mBatteryPercent / 100.0f;
        if (fillW > 0) mGfx.fillRect(x + 2 * bt, by + 2 * bt, fillW, bh - 4 * bt, col);
    }
    if (clock[0]) {
        const float cw = mGfx.measure(clock, textScale);
        x -= (mBatteryPercent >= 0 ? 24 * scale : 0) + cw;
        mGfx.text(clock, x, y, textScale, rgba(0.92f, 0.92f, 0.96f, 1.0f));
    }
    return x;
}

static int64_t monoNs() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

// Marquee position for a hint `overflow` pixels wider than its box: hold at the start, slide
// to the end, hold, slide back, repeat.
float Overlay::marqueeOffset(float overflow, float scale) {
    const double hold = 1.2;
    const double speed = 90.0 * scale;   // pixels per second
    const double slide = overflow / speed;
    const double cycle = 2.0 * (hold + slide);
    double t = std::fmod((monoNs() - mHintStartNs) / 1e9, cycle);
    if (t < hold) return 0.0f;
    t -= hold;
    if (t < slide) return (float)(t * speed);
    t -= slide;
    if (t < hold) return overflow;
    t -= hold;
    return (float)(overflow - t * speed);
}

void Overlay::shutdown() {
    {
        std::lock_guard<std::mutex> lk(mTickMu);
        mTickStop = true;
    }
    mTickCv.notify_all();
    if (mTicker.joinable()) mTicker.join();
    std::lock_guard<std::recursive_mutex> lock(mMu);
    if (mDpy == EGL_NO_DISPLAY) return;
    if (mGfxReady && makeCurrent()) {
        mGfx.shutdown();
        mGfxReady = false;
    }
    eglMakeCurrent(mDpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    if (mSurf != EGL_NO_SURFACE) eglDestroySurface(mDpy, mSurf);
    if (mCtx != EGL_NO_CONTEXT) eglDestroyContext(mDpy, mCtx);
    mSurf = EGL_NO_SURFACE;
    mCtx = EGL_NO_CONTEXT;
    mDpy = EGL_NO_DISPLAY;
}

void Overlay::scanGpuDrivers() {
    mDrivers.clear();
    mDrivers.push_back("");
    mDrivers.push_back("system");
    for (const auto& n : gpudrv::allPackages(mDataDir)) mDrivers.push_back(n);
    mDriverZips = gpudrv::listZips(mDataDir);
}

// A config.ini backed choice row: the current value selects the choice (an
// unknown or empty value shows the first choice, which is the Azahar default).
Overlay::Row Overlay::configChoice(const char* label, const char* section, const char* key,
                                   std::vector<Choice> choices, const char* hint, bool restart) {
    Row r;
    r.label = label;
    r.kind = ROW_CHOICE;
    r.choices = std::move(choices);
    const std::string cur = mA.getConfig ? mA.getConfig(section, key) : "";
    for (size_t i = 0; i < r.choices.size(); i++) if (r.choices[i].value == cur) r.sel = (int)i;
    r.hint = hint;
    const std::string sec = section, k = key;
    mConfigKeys.emplace_back(sec, k);
    r.change = [this, sec, k, restart, chs = r.choices](int i) {
        if (i < 0 || i >= (int)chs.size()) return;
        if (mA.setConfig) mA.setConfig(sec.c_str(), k.c_str(), chs[i].value.c_str());
        if (mA.reloadSettings) mA.reloadSettings();
        if (restart) mBanner = "Applies at the next launch";
    };
    return r;
}

Overlay::Row Overlay::configToggle(const char* label, const char* section, const char* key,
                                   const char* hint, bool restart) {
    return configChoice(label, section, key, {{"Off", "false"}, {"On", "true"}}, hint, restart);
}

Overlay::Row Overlay::propChoice(const char* label, const char* prop, const char* def,
                                 std::vector<Choice> choices, const char* hint,
                                 std::function<void(const std::string&)> onChange) {
    Row r;
    r.label = label;
    r.kind = ROW_CHOICE;
    r.choices = std::move(choices);
    const std::string cur = mA.getProp ? mA.getProp(prop, def) : def;
    for (size_t i = 0; i < r.choices.size(); i++) if (r.choices[i].value == cur) r.sel = (int)i;
    r.hint = hint;
    const std::string p = prop;
    if (p.rfind(settings::kPrefix, 0) == 0) mPropKeys.push_back(p.substr(strlen(settings::kPrefix)));
    r.change = [this, p, onChange, chs = r.choices](int i) {
        if (i < 0 || i >= (int)chs.size()) return;
        if (mA.setProp) mA.setProp(p.c_str(), chs[i].value.c_str());
        if (onChange) onChange(chs[i].value);
    };
    return r;
}

void Overlay::buildPages() {
    mPages.clear();
    mPages.resize(7);
    mPropKeys.clear();
    mConfigKeys.clear();
    mPropKeys.push_back("gpu_driver");   // its row is built by hand below
    auto pageRow = [](const char* label, int page, const char* hint) {
        Row r;
        r.label = label;
        r.kind = ROW_PAGE;
        r.page = page;
        r.hint = hint;
        return r;
    };

    // ---- top page ----
    {
        Page& pg = mPages[kPageTop];
        pg.title = "Azahar Nano";
        Row r;
        r.label = "Resume";
        r.activate = [this] { if (mA.resume) mA.resume(); };
        r.hint = "Back to the game";
        pg.rows.push_back(r);

        r = Row();
        r.label = "Save State";
        r.activate = [this] { if (mA.saveState) mA.saveState(mSlot); };
        r.hint = "Save the game to the selected slot";
        pg.rows.push_back(r);

        r = Row();
        r.label = "Load State";
        r.activate = [this] { if (mA.loadState) mA.loadState(mSlot); };
        r.hint = "Load the selected slot";
        pg.rows.push_back(r);

        r = Row();
        r.label = "Slot";
        r.kind = ROW_CHOICE;
        for (int i = 1; i <= 10; i++) r.choices.push_back({std::to_string(i), std::to_string(i)});
        r.sel = mSlot - 1;
        r.change = [this](int i) { mSlot = i + 1; };
        r.hint = "Save state slot";
        pg.rows.push_back(r);

        // Screen brightness, as drastic-nano's row: steps of 16 within 8..255, applied at once.
        {
            Row b;
            b.label = "Brightness";
            b.kind = ROW_CHOICE;
            b.stopAtEnds = true;
            for (int level = 8; level < 255; level += 16) {
                b.choices.push_back({std::to_string(level * 100 / 255) + "%", std::to_string(level)});
            }
            b.choices.push_back({"100%", "255"});
            const int cur = brightnessLevel();
            int best = 0;
            for (size_t i = 0; i < b.choices.size(); i++) {
                if (std::abs(atoi(b.choices[i].value.c_str()) - cur) <
                    std::abs(atoi(b.choices[best].value.c_str()) - cur)) {
                    best = (int)i;
                }
            }
            b.sel = best;
            b.hint = "Screen brightness";
            b.change = [chs = b.choices](int i) {
                if (i >= 0 && i < (int)chs.size()) setBrightness(atoi(chs[i].value.c_str()));
            };
            pg.rows.push_back(b);
        }

        // Device performance and fan, the same system settings as drastic-nano and the home
        // menu: init applies each property as it changes (init.gammaos_power.rc,
        // init.gammaos_fan.rc). Performance also starts its clock service directly so picking
        // the current mode re-applies it.
        pg.rows.push_back(propChoice("Performance", "persist.gammaos.performance_mode", "max",
            {{"Max", "max"}, {"Stock", "stock"}, {"Powersave", "powersave"}},
            "CPU and GPU clocks: Max for full speed, Powersave for battery",
            [this](const std::string& v) {
                if (mA.setProp) mA.setProp("ctl.start", ("setclock_" + v).c_str());
            }));
        pg.rows.push_back(propChoice("Fan", "persist.gammaos.fan_mode", "auto",
            {{"Auto", "auto"}, {"Cool", "cool"}, {"Max", "max"}, {"Off", "off"}},
            "Auto follows the temperature; Cool and Max run it all the time"));

        pg.rows.push_back(pageRow("Video", kPageVideo, "Resolution, screen layout, filters, 3D, frame rate"));
        pg.rows.push_back(pageRow("Audio", kPageAudio, "Volume, stretching, audio emulation"));
        pg.rows.push_back(pageRow("System", kPageSystem, "New 3DS mode, region, CPU clock"));
        pg.rows.push_back(pageRow("Controls", kPageControls, "Button layout"));

        // Per-game settings override (AzaharSettings.h). Create snapshots the current
        // settings into <user folder>/overrides/<rom>.cfg and makes that file the source of
        // truth for this game; every later change goes to the file and the global settings
        // are neither read nor written. Delete removes the file and re-applies the global
        // settings at once.
        if (!settings::overridePath().empty()) {
            r = Row();
            if (settings::overrideUnreadable()) {
                r.label = "Per-Game Override: Unreadable";
                r.hint = "Could not read " + settings::overridePath() + " (" +
                         settings::overrideError() + "); the global settings are in use";
                r.activate = [this] {
                    openConfirm("Delete Override?",
                                "Delete the unreadable override; the global settings keep applying to this game",
                                [this] { deletePerGameOverride(); });
                };
            } else if (settings::overrideActive()) {
                r.label = "Delete Per-Game Override";
                r.hint = "This game uses its own settings from " + settings::overridePath();
                r.activate = [this] {
                    openConfirm("Delete Per-Game Override?",
                                "Delete this game's settings override; the global settings apply again at once",
                                [this] { deletePerGameOverride(); });
                };
            } else {
                r.label = "Create Per-Game Override";
                r.hint = "Keep separate settings for this game, stored in " + settings::overridePath();
                r.activate = [this] {
                    openConfirm("Create Per-Game Override?",
                                "Save this game's settings as its own; changes from now on apply to this game only",
                                [this] { createPerGameOverride(); });
                };
            }
            pg.rows.push_back(r);
        }

        r = Row();
        r.label = "Exit Game";
        r.activate = [this] { if (mA.exitGame) mA.exitGame(); };
        r.hint = "Back to the home menu";
        pg.rows.push_back(r);
    }

    // ---- Video ----
    {
        Page& pg = mPages[kPageVideo];
        pg.title = "Video";
        pg.rows.push_back(configChoice("Resolution", "Renderer", "resolution_factor",
            {{"Native (1x)", "1"}, {"2x", "2"}, {"3x", "3"}, {"4x", "4"}, {"5x", "5"}, {"6x", "6"}},
            "Internal 3DS render resolution"));
        pg.rows.push_back(propChoice("Dynamic Resolution", "persist.gammaos.azahar_nano.dynres", "0",
            {{"Off", "0"}, {"On", "1"}},
            "Drops to 1x while the game runs slow (screen shaders stay; Sharp Bilinear where none is set), then tries the resolution above again"));
        pg.rows.push_back(configChoice("Top Panel", "Layout", "layout_option",
            {{"Top Screen", "1"}, {"Both Screens", "0"}, {"Large Screen", "2"}, {"Side by Side", "3"},
             {"Hybrid", "5"}},
            "What the top panel shows; the bottom panel keeps the other screen"));
        pg.rows.push_back(configChoice("Bottom Panel", "Layout", "secondary_display_layout",
            {{"Opposite Screen", "4"}, {"Bottom Screen", "2"}, {"Top Screen", "1"}, {"Both Screens", "5"},
             {"Side by Side", "3"}, {"Hybrid", "6"}},
            "What the bottom panel shows"));
        pg.rows.push_back(configToggle("Swap Screens", "Layout", "swap_screen",
            "Swap the top and bottom screens"));
        pg.rows.push_back(configChoice("Aspect Ratio", "Layout", "aspect_ratio",
            {{"Default", "0"}, {"Stretch", "5"}}, "Default keeps the 3DS aspect ratio"));
        pg.rows.push_back(configChoice("Texture Filter", "Renderer", "texture_filter",
            {{"None", "0"}, {"Anime4K", "1"}, {"Bicubic", "2"}, {"ScaleForce", "3"}, {"MMPX", "5"}},
            "Texture upscaling filter"));
        pg.rows.push_back(configChoice("Texture Sampling", "Renderer", "texture_sampling",
            {{"Game Controlled", "0"}, {"Nearest Neighbor", "1"}, {"Linear", "2"},
             {"Nearest 2D, Linear 3D", "3"}, {"Sharp Bilinear 2D, Linear 3D", "4"},
             {"Sharp Bilinear 2D and 3D", "5"}, {"Sharp Bilinear 2D, Nearest 3D", "6"}},
            "How textures are sampled; the 2D modes keep sprites and menus sharp and 3D smooth"));
        pg.rows.push_back(configToggle("Linear Filtering", "Renderer", "filter_mode",
            "Smooth the screens when scaled"));
        // LCD shaders per 3DS screen: one texture read each, drawn in the normal present pass.
        const std::vector<Choice> lcd = {{"Off", "0"},   {"LCD1x", "1"}, {"LCD3x", "2"},
                                         {"zfast LCD", "3"}, {"Retro", "4"}, {"Bevel", "5"},
                                         {"Sharp Bilinear", "6"}};
        for (int screen = 0; screen < 2; screen++) {
            pg.rows.push_back(propChoice(
                screen == 0 ? "Top Screen Shader" : "Bottom Screen Shader",
                screen == 0 ? "persist.gammaos.azahar_nano.lcd_top"
                            : "persist.gammaos.azahar_nano.lcd_bottom",
                "0", lcd, "LCD effect drawn over the 3DS pixels at the panel's resolution",
                [this, screen](const std::string& v) {
                    if (mA.setLcdShader) mA.setLcdShader(screen, atoi(v.c_str()));
                }));
        }
        pg.rows.push_back(propChoice("Half Precision Shaders",
            "persist.gammaos.azahar_nano.fs_mediump", "0", {{"Off", "0"}, {"On", "1"}},
            "Faster 3D: lighting and color math in 16-bit (lower precision, can band). Applies "
            "the next time a game starts and rebuilds its shaders once"));
        // GammaOS approximations for games that overload the GPU (Luigi's Mansion): both change
        // the picture, both are read by the core when a game starts.
        pg.rows.push_back(propChoice("Shadow Maps",
            "persist.gammaos.azahar_nano.no_shadow_map", "0", {{"On", "0"}, {"Off", "1"}},
            "Off skips the hardware shadow map passes: faster in games that use them (Luigi's "
            "Mansion), no dynamic shadows. Applies the next time a game starts"));
        pg.rows.push_back(propChoice("Light Limit",
            "persist.gammaos.azahar_nano.light_max", "0",
            {{"All", "0"}, {"1 light", "1"}, {"2 lights", "2"}, {"4 lights", "4"}},
            "Emulate only the first lights of a scene: much faster in heavily lit games, rooms "
            "lit by the dropped lights go dark. Applies the next time a game starts"));
        pg.rows.push_back(propChoice("Screen AA", "persist.gammaos.azahar_nano.screen_aa", "0",
            {{"Off", "0"}, {"On", "1"}},
            "Smooths jagged edges at native 1x (also when Dynamic Resolution drops to 1x), with any screen shader",
            [this](const std::string& v) {
                if (mA.setScreenAa) mA.setScreenAa(v == "1");
            }));
        pg.rows.push_back(configChoice("Stereoscopic 3D", "Renderer", "render_3d",
            {{"Off", "0"}, {"Side by Side", "1"}, {"Anaglyph", "3"}, {"Interlaced", "4"},
             {"Reverse Interlaced", "5"}},
            "3D rendering mode"));
        {
            std::vector<Choice> d;
            for (int v = 0; v <= 100; v += 10) d.push_back({std::to_string(v) + "%", std::to_string(v)});
            pg.rows.push_back(configChoice("3D Depth", "Renderer", "factor_3d", d, "Depth of the 3D effect"));
        }
        pg.rows.push_back(configChoice("Speed Limit", "Renderer", "frame_limit",
            {{"100%", "100"}, {"50%", "50"}, {"75%", "75"}, {"125%", "125"}, {"150%", "150"},
             {"200%", "200"}, {"Unlimited", "0"}},
            "Emulation speed limit"));
        pg.rows.push_back(configToggle("Skip Duplicate Frames", "Renderer", "use_skip_duplicate_frames",
            "Do not re-present a frame the game did not change"));
        pg.rows.push_back(configToggle("Async Shader Compilation", "Renderer", "async_shader_compilation",
            "Compile shaders in the background (may show artefacts briefly)"));
        pg.rows.push_back(configChoice("Right Eye Rendering", "Renderer", "disable_right_eye_render",
            {{"Off (faster)", "true"}, {"On", "false"}},
            "Render the right eye image; off saves GPU time on a 2D screen"));
        pg.rows.push_back(configToggle("Disk Shader Cache", "Renderer", "use_disk_shader_cache",
            "Keep compiled shaders between sessions", true));
        pg.rows.push_back(configToggle("Hardware Shaders", "Renderer", "use_hw_shader",
            "Run vertex shaders on the GPU", true));
        pg.rows.push_back(configChoice("Graphics API", "Renderer", "graphics_api",
            {{"Vulkan", "2"}, {"OpenGL ES", "1"}}, "Renderer backend", true));
        {
            Row r;
            r.label = "GPU Driver";
            r.kind = ROW_CHOICE;
            const std::string def = gpudrv::defaultPackage();
            for (const auto& d : mDrivers) {
                r.choices.push_back({d.empty()        ? "Default (" + (def.empty() ? std::string("System") : def) + ")"
                                     : d == "system" ? std::string("System")
                                                     : d,
                                     d});
            }
            const std::string cur = mA.getProp ? mA.getProp("persist.gammaos.azahar_nano.gpu_driver", "") : "";
            for (size_t i = 0; i < mDrivers.size(); i++) if (mDrivers[i] == cur) r.sel = (int)i;
            r.change = [this](int i) {
                if (i < 0 || i >= (int)mDrivers.size()) return;
                if (mA.setProp) mA.setProp("persist.gammaos.azahar_nano.gpu_driver", mDrivers[i].c_str());
                mBanner = "GPU driver applies at the next launch";
            };
            r.hint = "Default picks the driver bundled for this GPU; custom drivers are unpacked under " +
                     mDataDir + "/gpu_drivers";
            pg.rows.push_back(r);
        }
        pg.rows.push_back(pageRow("Import GPU Driver", kPageDrivers,
            "Unpack a driver zip from the data folder, /sdcard or Download"));
        pg.rows.push_back(propChoice("FPS Counter", "persist.gammaos.azahar_nano.fps_counter", "0",
            {{"Off", "0"}, {"On", "1"}}, "Show the frame rate and speed on the top panel"));
        pg.rows.push_back(propChoice("Shader Notice", "persist.gammaos.azahar_nano.compile_notice", "1",
            {{"Off", "0"}, {"On", "1"}}, "Show \"Compiling shaders\" while new shaders are built"));
    }

    // ---- Audio ----
    {
        Page& pg = mPages[kPageAudio];
        pg.title = "Audio";
        {
            std::vector<Choice> v;
            for (int p = 0; p <= 100; p += 10) {
                char buf[16];
                snprintf(buf, sizeof(buf), "%.2f", p / 100.0);
                v.push_back({std::to_string(p) + "%", buf});
            }
            // The stored value is a float; match on the rounded percentage.
            Row r = configChoice("Volume", "Audio", "volume", v, "Game audio volume");
            const std::string cur = mA.getConfig ? mA.getConfig("Audio", "volume") : "";
            if (!cur.empty()) {
                const int pct = (int)(atof(cur.c_str()) * 100.0 + 0.5);
                r.sel = pct / 10 > 10 ? 10 : pct / 10;
            } else {
                r.sel = 10;
            }
            pg.rows.push_back(r);
        }
        pg.rows.push_back(configToggle("Audio Stretching", "Audio", "enable_audio_stretching",
            "Stretch audio to match the emulation speed (fewer dropouts)"));
        pg.rows.push_back(configToggle("Realtime Audio", "Audio", "enable_realtime_audio",
            "Scale audio by the real elapsed time"));
        pg.rows.push_back(configChoice("Audio Emulation", "Audio", "audio_emulation",
            {{"HLE (fast)", "0"}, {"LLE (accurate)", "1"}, {"LLE multithreaded", "2"}},
            "DSP emulation method", true));
    }

    // ---- System ----
    {
        Page& pg = mPages[kPageSystem];
        pg.title = "System";
        pg.rows.push_back(configToggle("New 3DS Mode", "System", "is_new_3ds",
            "Emulate a New 3DS (more CPU power for games that use it)", true));
        pg.rows.push_back(configChoice("Region", "System", "region_value",
            {{"Auto", "-1"}, {"Japan", "0"}, {"USA", "1"}, {"Europe", "2"}, {"Australia", "3"},
             {"China", "4"}, {"Korea", "5"}, {"Taiwan", "6"}},
            "Console region", true));
        pg.rows.push_back(configChoice("CPU Clock", "Core", "cpu_clock_percentage",
            {{"100%", "100"}, {"50%", "50"}, {"75%", "75"}, {"150%", "150"}, {"200%", "200"},
             {"300%", "300"}, {"400%", "400"}},
            "Emulated CPU speed (underclock for speed, overclock for stutter)"));
        pg.rows.push_back(configToggle("CPU JIT", "Core", "use_cpu_jit",
            "Dynamic recompiler (turn off only for debugging)", true));
        pg.rows.push_back(configToggle("Plugin Loader", "System", "plugin_loader_enabled",
            "Load 3GX plugins", true));
    }

    // ---- Controls ----
    {
        Page& pg = mPages[kPageControls];
        pg.title = "Controls";
        pg.rows.push_back(propChoice("Button Layout", "persist.gammaos.azahar_nano.nintendo_layout", "0",
            {{"Xbox (A south, B east)", "0"}, {"Nintendo (A east, B south)", "1"}},
            "Which physical button is A and B",
            [this](const std::string& v) { if (mA.setNintendoLayout) mA.setNintendoLayout(v == "1"); }));
        pg.rows.push_back(propChoice("Swap Stick and D-Pad", "persist.gammaos.azahar_nano.stick_dpad_swap", "0",
            {{"Off", "0"}, {"On", "1"}},
            "On: the left stick presses the d-pad and the d-pad moves the circle pad (the menu keeps the d-pad)",
            [this](const std::string& v) { if (mA.setStickDpadSwap) mA.setStickDpadSwap(v == "1"); }));
    }
    // ---- Import GPU Driver ----
    {
        Page& pg = mPages[kPageDrivers];
        pg.title = "Import GPU Driver";
        if (mDriverZips.empty()) {
            Row r;
            r.label = "No driver zips found";
            r.hint = "Copy a .adpkg.zip to " + mDataDir + "/gpu_drivers, /sdcard or Download";
            r.activate = [] {};
            pg.rows.push_back(r);
        }
        for (const auto& zip : mDriverZips) {
            Row r;
            const size_t s = zip.find_last_of('/');
            r.label = s == std::string::npos ? zip : zip.substr(s + 1);
            r.hint = zip;
            r.activate = [this, zip] {
                std::string err;
                const std::string name = gpudrv::importZip(zip, mDataDir, &err);
                if (name.empty()) {
                    mBanner = "Import failed: " + err;
                    return;
                }
                if (mA.setProp) mA.setProp("persist.gammaos.azahar_nano.gpu_driver", name.c_str());
                mBanner = name + " imported; applies at the next launch";
                mRebuildPages = true;   // done after this callback returns (it owns us)
            };
            pg.rows.push_back(r);
        }
    }
}

void Overlay::openConfirm(const std::string& question, const std::string& detail,
                          std::function<void()> onYes) {
    Page& pg = mPages[kPageConfirm];
    pg = Page();
    pg.title = question;
    Row yes;
    yes.label = "Yes";
    yes.hint = detail;
    yes.activate = [this, onYes] {
        if (onYes) onYes();
        mRebuildTop = true;
    };
    pg.rows.push_back(yes);
    Row no;
    no.label = "No";
    no.hint = "Back";
    no.activate = [this] {
        if (mStack.size() > 1) mStack.pop_back();
    };
    pg.rows.push_back(no);
    pg.cursor = 1;
    mStack.push_back(kPageConfirm);
}

void Overlay::createPerGameOverride() {
    // The snapshot reads the global properties and config.ini itself (never the
    // override); from here on every change this menu makes lands in the file.
    const bool ok = settings::create(mPropKeys, mConfigKeys, [this](const char* sec, const char* key) {
        return mA.getGlobalConfig ? mA.getGlobalConfig(sec, key) : std::string();
    });
    if (!ok) {
        mBanner = "Could not create the override file";
        return;
    }
    if (mA.overrideChanged) mA.overrideChanged();
    mBanner = "Per-game override created; changes now apply to this game only";
}

void Overlay::deletePerGameOverride() {
    if (!settings::remove()) {
        mBanner = "Could not delete the override file";
        return;
    }
    if (mA.overrideChanged) mA.overrideChanged();
    mBanner = "Override deleted; the global settings apply";
}

void Overlay::open() {
    std::lock_guard<std::recursive_mutex> lock(mMu);
    if (!mTicker.joinable()) mTicker = std::thread([this] { tickerLoop(); });
    mOpen = true;
    mBanner.clear();
    // The menu reopens where it was closed: the same page and row (the pages are rebuilt
    // so every row shows its current value). A confirm page is not restored.
    std::vector<int> stack = mStack;
    std::vector<int> cursors;
    for (const Page& pg : mPages) cursors.push_back(pg.cursor);
    scanGpuDrivers();
    buildPages();
    for (size_t i = 0; i < mPages.size() && i < cursors.size(); i++) {
        if (!mPages[i].rows.empty()) {
            mPages[i].cursor = std::min(std::max(cursors[i], 0), (int)mPages[i].rows.size() - 1);
        }
    }
    mStack.assign(1, kPageTop);
    for (size_t i = 1; i < stack.size(); i++) {
        if (stack[i] <= kPageTop || stack[i] >= (int)mPages.size() || stack[i] == kPageConfirm) break;
        mStack.push_back(stack[i]);
    }
}

void Overlay::close() {
    std::lock_guard<std::recursive_mutex> lock(mMu);
    mOpen = false;
    mMarquee = false;
}

bool Overlay::onButton(int button, bool pressed) {
    std::lock_guard<std::recursive_mutex> lock(mMu);
    if (!mOpen) return false;
    if (!pressed) return true;
    if (mStack.empty()) mStack.assign(1, kPageTop);
    Page& pg = mPages[mStack.back()];
    if (pg.rows.empty()) return true;
    Row& row = pg.rows[pg.cursor];
    mBanner.clear();
    switch (button) {
    case AZN_DPAD_UP:
        pg.cursor = (pg.cursor + (int)pg.rows.size() - 1) % (int)pg.rows.size();
        break;
    case AZN_DPAD_DOWN:
        pg.cursor = (pg.cursor + 1) % (int)pg.rows.size();
        break;
    case AZN_DPAD_LEFT:
    case AZN_DPAD_RIGHT:
        if (row.kind == ROW_CHOICE && !row.choices.empty()) {
            const int n = (int)row.choices.size();
            if (row.stopAtEnds) {
                const int next = row.sel + (button == AZN_DPAD_LEFT ? -1 : 1);
                if (next < 0 || next >= n) break;
                row.sel = next;
            } else {
                row.sel = (row.sel + (button == AZN_DPAD_LEFT ? n - 1 : 1)) % n;
            }
            if (row.change) row.change(row.sel);
        }
        break;
    case AZN_BUTTON_A:
        if (row.kind == ROW_PAGE && row.page >= 0) {
            mStack.push_back(row.page);
        } else if (row.kind == ROW_ACTION && row.activate) {
            row.activate();
            if (mRebuildTop) {
                // An override was created or deleted: every row re-reads its value, and the
                // top page comes back on the override row with the banner showing.
                mRebuildTop = false;
                const std::string banner = mBanner;
                scanGpuDrivers();
                buildPages();
                mBanner = banner;
                mStack.assign(1, kPageTop);
                Page& top = mPages[kPageTop];
                for (size_t i = 0; i < top.rows.size(); i++) {
                    if (top.rows[i].label.find("Per-Game Override") != std::string::npos) top.cursor = (int)i;
                }
                render();
                return true;
            }
            if (mRebuildPages) {
                // A driver import changed the package list: rebuild every page
                // and come back to the Video page with the banner showing.
                mRebuildPages = false;
                const int cursor = mPages[kPageVideo].cursor;
                scanGpuDrivers();
                buildPages();
                mPages[kPageVideo].cursor = cursor;
                mStack.assign(1, kPageTop);
                mStack.push_back(kPageVideo);
                render();
                return true;
            }
        } else if (row.kind == ROW_CHOICE && !row.choices.empty()) {
            row.sel = (row.sel + 1) % (int)row.choices.size();
            if (row.change) row.change(row.sel);
        }
        break;
    case AZN_BUTTON_B:
        if (mStack.size() > 1) mStack.pop_back();
        else if (mA.resume) mA.resume();
        break;
    default:
        break;
    }
    return true;
}

void Overlay::render() {
    std::lock_guard<std::recursive_mutex> lock(mMu);
    if (!mGfxReady || !makeCurrent()) {
        ALOGE("overlay: render skipped (ready=%d)", mGfxReady ? 1 : 0);
        return;
    }
    if (mStack.empty()) mStack.assign(1, kPageTop);
    const Page& pg = mPages[mStack.back()];
    const int W = mGfx.viewportW();
    const int H = mGfx.viewportH();
    glViewport(0, 0, mBufW, mBufH);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    mGfx.beginFrame();

    mGfx.fillRect(0, 0, (float)W, (float)H, rgba(0, 0, 0, 0.55f));
    const float scale = H / 1080.0f;
    const float panelW = W * 0.62f;
    const float titleScale = 1.6f * scale, rowScale = 1.25f * scale, hintScale = 1.0f * scale;
    const float rowH = mGfx.fontLineH() * rowScale + 14 * scale;
    // Rows visible at once; long pages scroll around the cursor.
    int maxRows = (int)((H * 0.8f - rowH * 3.0f) / rowH);
    if (maxRows < 3) maxRows = 3;
    const int nRows = (int)pg.rows.size();
    int first = 0;
    if (nRows > maxRows) {
        first = pg.cursor - maxRows / 2;
        if (first < 0) first = 0;
        if (first > nRows - maxRows) first = nRows - maxRows;
    }
    const int shown = nRows < maxRows ? nRows : maxRows;
    const float panelH = rowH * (shown + 3.0f);
    const float px = (W - panelW) / 2, py = (H - panelH) / 2;
    mGfx.panel(px, py, panelW, panelH, rgba(0.08f, 0.09f, 0.12f, 0.96f), rgba(0.3f, 0.5f, 0.9f, 1.0f));
    float headerRight = drawStatus(px + panelW - 32 * scale, py + rowH * 0.5f, scale);
    if (mStack.size() > 1) {
        const char* back = "B: back";
        headerRight -= 28 * scale + mGfx.measure(back, hintScale);
        mGfx.text(back, headerRight, py + rowH * 0.5f, hintScale, rgba(0.6f, 0.65f, 0.7f, 1));
    }
    // The title takes the space left of the status and "B: back": a long one is drawn
    // smaller (centred on the same line) so it never runs into them or past the panel.
    {
        const float titleX = px + 32 * scale;
        const float avail = headerRight - 28 * scale - titleX;
        float ts = titleScale;
        const float tw = mGfx.measure(pg.title.c_str(), ts);
        if (tw > avail && avail > 0) ts *= avail / tw;
        const float ty = py + rowH * 0.35f + mGfx.fontLineH() * (titleScale - ts) * 0.5f;
        mGfx.text(pg.title.c_str(), titleX, ty, ts, rgba(1, 1, 1, 1));
    }
    float y = py + rowH * 1.6f;
    for (int i = first; i < first + shown; i++) {
        const Row& r = pg.rows[i];
        const bool sel = i == pg.cursor;
        // text() takes the top of the glyph box (verified by capture).
        if (sel) mGfx.fillRect(px + 16 * scale, y - 6 * scale, panelW - 32 * scale,
                               mGfx.fontLineH() * rowScale + 8 * scale, rgba(0.2f, 0.35f, 0.7f, 0.9f));
        mGfx.text(r.label.c_str(), px + 40 * scale, y, rowScale, rgba(1, 1, 1, 1));
        std::string v;
        if (r.kind == ROW_CHOICE && !r.choices.empty()) v = "<  " + r.choices[r.sel].label + "  >";
        else if (r.kind == ROW_PAGE) v = ">";
        if (!v.empty()) {
            const float tw = mGfx.measure(v.c_str(), rowScale);
            mGfx.text(v.c_str(), px + panelW - 40 * scale - tw, y, rowScale, rgba(0.85f, 0.9f, 1.0f, 1));
        }
        y += rowH;
    }
    // The help line's box runs from the left margin to the position counter (or the right
    // margin); a longer hint scrolls inside it as a bouncing marquee.
    float hintRight = px + panelW - 32 * scale;
    const float hintY = py + panelH - rowH * 0.9f;
    if (nRows > maxRows) {
        char pos[32];
        snprintf(pos, sizeof(pos), "%d/%d", pg.cursor + 1, nRows);
        const float pw = mGfx.measure(pos, hintScale);
        mGfx.text(pos, px + panelW - 32 * scale - pw, hintY, hintScale, rgba(0.6f, 0.65f, 0.7f, 1));
        hintRight -= pw + 24 * scale;
    }
    const std::string hint = mBanner.empty() ? pg.rows[pg.cursor].hint : mBanner;
    if (hint != mHintShown) {
        mHintShown = hint;
        mHintStartNs = monoNs();
    }
    const float hintX = px + 32 * scale;
    const float boxW = hintRight - hintX;
    const float overflow = mGfx.measure(hint.c_str(), hintScale) - boxW;
    if (overflow > 0.0f) {
        mMarquee = true;
        mGfx.clipBegin(hintX, hintY - 4 * scale, boxW, mGfx.fontLineH() * hintScale + 8 * scale);
        mGfx.text(hint.c_str(), hintX - marqueeOffset(overflow, scale), hintY, hintScale,
                  rgba(0.7f, 0.75f, 0.8f, 1));
        mGfx.clipEnd();
    } else {
        mMarquee = false;
        mGfx.text(hint.c_str(), hintX, hintY, hintScale, rgba(0.7f, 0.75f, 0.8f, 1));
    }
    mGfx.endFrame();
    if (!eglSwapBuffers(mDpy, mSurf)) ALOGE("overlay: eglSwapBuffers failed 0x%x", eglGetError());
    // Any thread may render next (input thread or main), so leave the context free.
    eglMakeCurrent(mDpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
}

void Overlay::renderLoading(const char* title, const char* detail, float frac, const char* note,
                            float phase) {
    std::lock_guard<std::recursive_mutex> lock(mMu);
    if (!mGfxReady || !makeCurrent()) return;
    const float W = (float)mGfx.viewportW();
    const float H = (float)mGfx.viewportH();
    glViewport(0, 0, mBufW, mBufH);
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    mGfx.beginFrame();
    const float scale = H / 1080.0f;
    const float titleScale = 1.6f * scale, detailScale = 1.1f * scale;
    const float barW = W * 0.5f, barH = 18.0f * scale;
    const float cx = W * 0.5f;
    const float barY = H * 0.55f;
    mGfx.text(title, cx - mGfx.measure(title, titleScale) * 0.5f,
              barY - mGfx.fontLineH() * titleScale - 40 * scale, titleScale, rgba(1, 1, 1, 1));
    if (frac >= 0.0f) {
        if (frac > 1.0f) frac = 1.0f;
        mGfx.fillRect(cx - barW * 0.5f, barY, barW, barH, rgba(1, 1, 1, 0.18f));
        mGfx.fillRect(cx - barW * 0.5f, barY, barW * frac, barH, rgba(0.3f, 0.75f, 1.0f, 1));
    } else if (phase >= 0.0f) {
        // Indeterminate: a quarter-width segment sweeping across the track and wrapping.
        const float seg = barW * 0.25f;
        const float p = phase - std::floor(phase);
        const float x0 = cx - barW * 0.5f + p * (barW + seg) - seg;
        const float a = std::max(x0, cx - barW * 0.5f);
        const float b = std::min(x0 + seg, cx + barW * 0.5f);
        mGfx.fillRect(cx - barW * 0.5f, barY, barW, barH, rgba(1, 1, 1, 0.18f));
        if (b > a) mGfx.fillRect(a, barY, b - a, barH, rgba(0.3f, 0.75f, 1.0f, 1));
    }
    float lineY = barY + barH + 30 * scale;
    if (detail && *detail) {
        mGfx.text(detail, cx - mGfx.measure(detail, detailScale) * 0.5f, lineY, detailScale,
                  rgba(0.7f, 0.75f, 0.8f, 1));
        lineY += mGfx.fontLineH() * detailScale + 24 * scale;
    }
    // The note may hold several lines separated by '\n', each centred.
    if (note && *note) {
        const float noteScale = 0.95f * scale;
        const char* p = note;
        char line[160];
        while (*p) {
            const char* e = strchr(p, '\n');
            const size_t n = std::min<size_t>(e ? (size_t)(e - p) : strlen(p), sizeof(line) - 1);
            memcpy(line, p, n);
            line[n] = 0;
            mGfx.text(line, cx - mGfx.measure(line, noteScale) * 0.5f, lineY, noteScale,
                      rgba(0.55f, 0.6f, 0.66f, 1));
            lineY += mGfx.fontLineH() * noteScale + 8 * scale;
            if (!e) break;
            p = e + 1;
        }
    }
    mGfx.endFrame();
    if (!eglSwapBuffers(mDpy, mSurf)) ALOGE("overlay: eglSwapBuffers failed 0x%x", eglGetError());
    eglMakeCurrent(mDpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
}

} // namespace azahar_nano
