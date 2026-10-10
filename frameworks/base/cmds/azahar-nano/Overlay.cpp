#define LOG_TAG "azahar-nano"

#include "Overlay.h"
#include <time.h>
#include <cmath>
#include "AzaharNanoApi.h"
#include "GpuDriverPkg.h"
#include "Brightness.h"

#include <dirent.h>
#include <stdlib.h>
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
              kPageDrivers = 5;
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

void Overlay::tickerLoop() {
    std::unique_lock<std::mutex> lk(mTickMu);
    while (!mTickStop) {
        mTickCv.wait_for(lk, std::chrono::milliseconds(33));
        if (mTickStop) break;
        if (!mOpen || !mMarquee) continue;
        lk.unlock();
        render();
        lk.lock();
    }
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
    for (const auto& n : gpudrv::listPackages(mDataDir)) mDrivers.push_back(n);
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
    r.change = [this, p, onChange, chs = r.choices](int i) {
        if (i < 0 || i >= (int)chs.size()) return;
        if (mA.setProp) mA.setProp(p.c_str(), chs[i].value.c_str());
        if (onChange) onChange(chs[i].value);
    };
    return r;
}

void Overlay::buildPages() {
    mPages.clear();
    mPages.resize(6);
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
            "Drops to 1x with Sharp Bilinear + AA while the game runs slow, then tries the resolution above again"));
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
            {{"Game Controlled", "0"}, {"Nearest Neighbor", "1"}, {"Linear", "2"}},
            "How textures are sampled"));
        pg.rows.push_back(configToggle("Linear Filtering", "Renderer", "filter_mode",
            "Smooth the screens when scaled"));
        // LCD shaders per 3DS screen: one texture read each, drawn in the normal present pass.
        const std::vector<Choice> lcd = {{"Off", "0"},   {"LCD1x", "1"}, {"LCD3x", "2"},
                                         {"zfast LCD", "3"}, {"Retro", "4"}, {"Bevel", "5"},
                                         {"Sharp Bilinear", "6"}, {"Sharp Bilinear + AA", "7"}};
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
            for (const auto& d : mDrivers) r.choices.push_back({d.empty() ? "System" : d, d});
            const std::string cur = mA.getProp ? mA.getProp("persist.gammaos.azahar_nano.gpu_driver", "") : "";
            for (size_t i = 0; i < mDrivers.size(); i++) if (mDrivers[i] == cur) r.sel = (int)i;
            r.change = [this](int i) {
                if (i < 0 || i >= (int)mDrivers.size()) return;
                if (mA.setProp) mA.setProp("persist.gammaos.azahar_nano.gpu_driver", mDrivers[i].c_str());
                mBanner = "GPU driver applies at the next launch";
            };
            r.hint = "Custom Adreno drivers unpacked under " + mDataDir + "/gpu_drivers";
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

void Overlay::open() {
    std::lock_guard<std::recursive_mutex> lock(mMu);
    if (!mTicker.joinable()) mTicker = std::thread([this] { tickerLoop(); });
    mOpen = true;
    mBanner.clear();
    scanGpuDrivers();
    buildPages();
    mStack.assign(1, kPageTop);
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
    mGfx.text(pg.title.c_str(), px + 32 * scale, py + rowH * 0.35f, titleScale, rgba(1, 1, 1, 1));
    if (mStack.size() > 1) {
        const char* back = "B: back";
        mGfx.text(back, px + panelW - 32 * scale - mGfx.measure(back, hintScale), py + rowH * 0.5f,
                  hintScale, rgba(0.6f, 0.65f, 0.7f, 1));
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

void Overlay::renderLoading(const char* title, const char* detail, float frac) {
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
    }
    if (detail && *detail) {
        mGfx.text(detail, cx - mGfx.measure(detail, detailScale) * 0.5f, barY + barH + 30 * scale,
                  detailScale, rgba(0.7f, 0.75f, 0.8f, 1));
    }
    mGfx.endFrame();
    if (!eglSwapBuffers(mDpy, mSurf)) ALOGE("overlay: eglSwapBuffers failed 0x%x", eglGetError());
    eglMakeCurrent(mDpy, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
}

} // namespace azahar_nano
