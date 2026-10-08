/*
 * azahar-nano: the GammaOS Nano frontend for the Azahar 3DS core.
 *
 * Session shape (mirrors drastic-nano):
 *   1. gammaos-nano writes the ROM path to /data/system/nano_azahar_nano_rom.txt
 *      and sets sys.gammaos.azahar_nano.start=1; init stops the home and starts
 *      this service as root.
 *   2. We dlopen the core (libazahar-nano-core.so, Azahar's Android native layer
 *      behind a C ABI), bring up the panels, hand the core one ANativeWindow per
 *      screen, start the evdev input thread and run the core on this thread.
 *   3. On exit (long press of Back, HOME menu close, crash) we tear the panels
 *      down and set sys.gammaos.azahar_nano.session_done=1; init restarts the
 *      home. SurfaceFlinger is never stopped.
 *
 * Backends: persist.gammaos.azahar_nano.backend = sf | drm (default drm once
 * the DRM path lands; sf today). User directory: persist.gammaos.azahar_nano.user_dir
 * (default /sdcard/azahar-nano) holds config/, nand/, sdmc/, sysdata/, shaders/,
 * gpu_drivers/ exactly like the Azahar app's user directory.
 */

#define LOG_TAG "azahar-nano"

#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

#include <atomic>
#include <thread>
#include <fstream>
#include <string>

#include <cutils/properties.h>
#include <log/log.h>

#include "AzaharNanoApi.h"
#include "NanoInput.h"
#include "SfWindows.h"
#include "DrmWindows.h"
#include "Overlay.h"
#include "Hud.h"
#include "GpuDriverPkg.h"

namespace {

constexpr const char* kRomPathFile = "/data/system/nano_azahar_nano_rom.txt";
constexpr const char* kSessionDoneProp = "sys.gammaos.azahar_nano.session_done";
constexpr const char* kCoreLibDefault = "/system/lib64/libazahar-nano-core.so";
constexpr const char* kUserDirDefault = "/data/system/azahar-nano";   // private root
constexpr const char* kDataDirDefault = "/sdcard/azahar-nano";        // relocatable user folder

struct CoreApi {
    void* handle = nullptr;
    int (*abi_version)() = nullptr;
    int (*init)(const char*, const azn_callbacks*) = nullptr;
    void (*set_gpu_driver)(const char*, const char*, const char*, const char*) = nullptr;
    void (*set_windows)(ANativeWindow*, ANativeWindow*) = nullptr;
    int (*run)(const char*) = nullptr;
    void (*stop)() = nullptr;
    void (*pause)(bool) = nullptr;
    bool (*is_running)() = nullptr;
    bool (*button)(int, bool) = nullptr;
    bool (*joystick)(int, float, float) = nullptr;
    bool (*analog_button)(int, float) = nullptr;
    bool (*touch)(float, float, bool) = nullptr;
    void (*touch_moved)(float, float) = nullptr;
    bool (*secondary_touch)(float, float, bool) = nullptr;
    void (*secondary_touch_moved)(float, float) = nullptr;
    void (*save_state)(int) = nullptr;
    void (*load_state)(int) = nullptr;
    void (*get_perf_stats)(azn_perf_stats*) = nullptr;
    void (*reload_settings)() = nullptr;
    void (*set_upright)(int) = nullptr;
    void (*set_present_mode)(int, int) = nullptr;
    void (*set_deadline_pacing)(int) = nullptr;
    void (*set_user_folder)(const char*) = nullptr;
    int (*set_config_value)(const char*, const char*, const char*) = nullptr;
};

CoreApi gCore;
std::atomic<bool> gExitRequested{false};
std::atomic<bool> gSessionDoneSent{false};

template <typename T>
bool sym(T& out, const char* name) {
    out = reinterpret_cast<T>(dlsym(gCore.handle, name));
    if (!out) ALOGE("missing core symbol %s", name);
    return out != nullptr;
}

bool loadCore(const std::string& path) {
    gCore.handle = dlopen(path.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!gCore.handle) {
        ALOGE("dlopen %s failed: %s", path.c_str(), dlerror());
        return false;
    }
    bool ok = true;
    ok &= sym(gCore.abi_version, "azn_abi_version");
    ok &= sym(gCore.init, "azn_init");
    ok &= sym(gCore.set_gpu_driver, "azn_set_gpu_driver");
    ok &= sym(gCore.set_windows, "azn_set_windows");
    ok &= sym(gCore.run, "azn_run");
    ok &= sym(gCore.stop, "azn_stop");
    ok &= sym(gCore.pause, "azn_pause");
    ok &= sym(gCore.is_running, "azn_is_running");
    ok &= sym(gCore.button, "azn_button");
    ok &= sym(gCore.joystick, "azn_joystick");
    ok &= sym(gCore.analog_button, "azn_analog_button");
    ok &= sym(gCore.touch, "azn_touch");
    ok &= sym(gCore.touch_moved, "azn_touch_moved");
    ok &= sym(gCore.secondary_touch, "azn_secondary_touch");
    ok &= sym(gCore.secondary_touch_moved, "azn_secondary_touch_moved");
    ok &= sym(gCore.save_state, "azn_save_state");
    ok &= sym(gCore.load_state, "azn_load_state");
    ok &= sym(gCore.get_perf_stats, "azn_get_perf_stats");
    ok &= sym(gCore.reload_settings, "azn_reload_settings");
    ok &= sym(gCore.set_upright, "azn_set_upright");
    ok &= sym(gCore.set_present_mode, "azn_set_present_mode");
    ok &= sym(gCore.set_deadline_pacing, "azn_set_deadline_pacing");
    ok &= sym(gCore.set_user_folder, "azn_set_user_folder");
    ok &= sym(gCore.set_config_value, "azn_set_config_value");
    if (ok && gCore.abi_version() != AZN_ABI_VERSION) {
        ALOGE("core ABI %d, expected %d", gCore.abi_version(), AZN_ABI_VERSION);
        ok = false;
    }
    return ok;
}

void sessionDone() {
    if (gSessionDoneSent.exchange(true)) return;
    property_set(kSessionDoneProp, "1");
}

// SIGTERM (init stopping the service, pkill): leave through the normal exit path so
// the panels are released and session_done fires; a second SIGTERM kills outright.
void onTerm(int) {
    static std::atomic<int> count{0};
    if (count.fetch_add(1) == 0) {
        gExitRequested = true;
        if (gCore.stop) gCore.stop();
        return;
    }
    sessionDone();
    _exit(0);
}

void crashCleanup(int sig) {
    ALOGE("crash signal %d, returning to the home", sig);
    sessionDone();
    signal(sig, SIG_DFL);
    raise(sig);
}

std::string readRomPath(int argc, char** argv) {
    if (argc > 1 && argv[1][0]) return argv[1];
    std::ifstream f(kRomPathFile);
    std::string p;
    std::getline(f, p);
    while (!p.empty() && (p.back() == '\n' || p.back() == '\r' || p.back() == ' ')) p.pop_back();
    return p;
}

// One value out of config.ini ([section] key = value), "" when absent.
std::string configValue(const std::string& userDir, const char* section, const char* key) {
    std::ifstream f(userDir + "/config/config.ini");
    std::string line, want = std::string("[") + section + "]";
    bool in = false;
    while (std::getline(f, line)) {
        if (!line.empty() && line[0] == '[') { in = (line == want); continue; }
        if (!in) continue;
        const size_t eq = line.find('=');
        if (eq == std::string::npos) continue;
        std::string k = line.substr(0, eq);
        while (!k.empty() && (k.back() == ' ' || k.back() == '\t')) k.pop_back();
        if (k != key) continue;
        std::string v = line.substr(eq + 1);
        while (!v.empty() && (v.front() == ' ' || v.front() == '\t')) v.erase(0, 1);
        while (!v.empty() && (v.back() == ' ' || v.back() == '\r')) v.pop_back();
        return v;
    }
    return "";
}

std::string prop(const char* name, const char* def) {
    char v[PROPERTY_VALUE_MAX] = {};
    property_get(name, v, def);
    return v;
}

// Re-assert the user's clock policy (persist.gammaos.performance_mode) through the
// vendor setclock_* services, exactly like drastic-nano: the boot trigger can race
// or never fire when the home was stopped, and this session then ran on the
// powersave governor at 650 MHz (measured: 2-9 fps instead of 60).
void retriggerPowerProfile() {
    const std::string mode = prop("persist.gammaos.performance_mode", "max");
    const char* svc = mode == "stock" ? "setclock_stock"
                    : mode == "powersave" ? "setclock_powersave" : "setclock_max";
    property_set("ctl.start", svc);
    ALOGI("power profile %s (mode=%s)", svc, mode.c_str());
}

// First run: the private root has no config.ini yet. Seed the two values a
// dual-panel handheld needs (the top window shows the top screen only; the bottom
// panel gets the secondary layout by default) and a 2x internal resolution.
void seedConfig(const std::string& userDir) {
    // The core writes a config.ini with empty (default) values on its first load,
    // so seed on empty values rather than on a missing file; a value the user set
    // is never touched.
    bool changed = false;
    if (configValue(userDir, "Layout", "layout_option").empty()) {
        gCore.set_config_value("Layout", "layout_option", "1");
        changed = true;
    }
    if (configValue(userDir, "Renderer", "resolution_factor").empty()) {
        gCore.set_config_value("Renderer", "resolution_factor", "2");
        changed = true;
    }
    // Performance defaults for a handheld: no right-eye render (no 3D screen here)
    // and background shader compilation; both stay switchable in the Video page.
    if (configValue(userDir, "Renderer", "disable_right_eye_render").empty()) {
        gCore.set_config_value("Renderer", "disable_right_eye_render", "true");
        changed = true;
    }
    if (configValue(userDir, "Renderer", "async_shader_compilation").empty()) {
        gCore.set_config_value("Renderer", "async_shader_compilation", "true");
        changed = true;
    }
    if (changed) {
        ALOGI("seeded config.ini defaults in %s", userDir.c_str());
        gCore.reload_settings();
    }
}

// Core callbacks.
bool onCoreError(void*, int result, const char* details) {
    ALOGE("core error %d: %s", result, details ? details : "");
    // Keep running for recoverable errors; the user can exit from the menu.
    return result != AZN_ERROR_SYSTEM_FILES;
}
void onLoadProgress(void*, int stage, int progress, int max) {
    if (stage == AZN_LOAD_COMPLETE || (progress % 50) == 0) {
        ALOGI("shader cache stage %d %d/%d", stage, progress, max);
    }
}
void onShutdown(void*, int result) {
    ALOGI("core shutdown request %d", result);
}
bool onMicPermission(void*) {
    return true;
}
void onLog(void*, int level, const char* cls, const char* msg) {
    __android_log_print(level >= 4 ? ANDROID_LOG_ERROR : ANDROID_LOG_INFO, "azahar-core",
                        "[%s] %s", cls ? cls : "", msg ? msg : "");
}

} // namespace

int main(int argc, char** argv) {
    signal(SIGSEGV, crashCleanup);
    signal(SIGBUS, crashCleanup);
    signal(SIGABRT, crashCleanup);
    signal(SIGILL, crashCleanup);
    signal(SIGFPE, crashCleanup);
    signal(SIGTERM, onTerm);
    signal(SIGINT, onTerm);
    umask(0002);

    const std::string rom = readRomPath(argc, argv);
    if (rom.empty()) {
        ALOGE("no ROM path (argv[1] or %s)", kRomPathFile);
        sessionDone();
        return 1;
    }
    const std::string coreLib = prop("persist.gammaos.azahar_nano.core", kCoreLibDefault);
    const std::string userDir = prop("persist.gammaos.azahar_nano.user_dir", kUserDirDefault);
    const std::string backend = prop("persist.gammaos.azahar_nano.backend", "sf");
    ALOGI("rom=%s core=%s user=%s backend=%s", rom.c_str(), coreLib.c_str(), userDir.c_str(),
          backend.c_str());

    retriggerPowerProfile();
    if (!loadCore(coreLib)) {
        sessionDone();
        return 1;
    }

    azn_callbacks cb{};
    cb.on_core_error = onCoreError;
    cb.on_load_progress = onLoadProgress;
    cb.on_shutdown = onShutdown;
    cb.on_mic_permission = onMicPermission;
    cb.on_log = onLog;
    mkdir(userDir.c_str(), 0775);
    if (gCore.init(userDir.c_str(), &cb) != AZN_OK) {
        ALOGE("core init failed");
        sessionDone();
        return 1;
    }
    // Saves, save states, cheats, dumps and mods live in the user folder (the Game
    // Settings "Azahar Data Folder" row, persist.gammaos.azahar.data_dir); everything
    // else stays in the private root. When both point at the same place (a user who
    // keeps a whole Azahar user directory, e.g. the stock app's) nothing moves.
    const std::string dataDir = prop("persist.gammaos.azahar.data_dir", kDataDirDefault);
    if (dataDir != userDir) {
        mkdir(dataDir.c_str(), 0775);
        gCore.set_user_folder(dataDir.c_str());
    }
    ALOGI("private root %s, user folder %s", userDir.c_str(), dataDir.c_str());
    seedConfig(userDir);

    // GPU driver: the adrenotools hook libraries ship next to the core; a custom
    // driver is an unpacked adrenotools package under <user>/gpu_drivers/<name>/.
    // The package lives in the user's data folder (zips are imported there by the
    // menu) but the sdcard is mounted noexec, so the chosen package is staged
    // under the private root and loaded from there.
    const std::string hookDir = coreLib.substr(0, coreLib.find_last_of('/'));
    const std::string driverName = prop("persist.gammaos.azahar_nano.gpu_driver", "");
    std::string driverDir, driverLib;
    if (!driverName.empty()) {
        driverDir = azahar_nano::gpudrv::stage(dataDir, userDir, driverName);
        if (!driverDir.empty()) {
            driverLib = azahar_nano::gpudrv::libraryName(driverDir);
            // adrenotools joins dir + name with no separator (the app passes a
            // trailing slash too).
            driverDir += "/";
        }
        if (driverDir.empty() || driverLib.empty()) {
            ALOGW("gpu driver %s unavailable, using the system driver", driverName.c_str());
            driverDir.clear();
            driverLib.clear();
        }
    }
    gCore.set_gpu_driver(hookDir.c_str(), driverDir.c_str(), driverLib.c_str(), "");

    // Panels. DRM backend: our own BufferQueues flipped straight onto the panels.
    // SurfaceFlinger backend: one opaque layer per physical display.
    azahar_nano::SfWindows sf;
    azahar_nano::DrmWindows drm;
    const bool useDrm = backend == "drm";
    int panels = 0;
    if (useDrm) {
        panels = drm.create();
        if (panels == 0) ALOGE("DRM backend failed, falling back to SurfaceFlinger");
    }
    if (panels == 0) panels = sf.create();
    const bool drmActive = useDrm && drm.count() > 0;
    if (panels == 0) {
        ALOGE("no panels");
        sessionDone();
        return 1;
    }
    auto winAt = [&](int i) -> ANativeWindow* { return drmActive ? drm.window(i) : sf.window(i); };
    auto widthAt = [&](int i) { return drmActive ? drm.width(i) : sf.width(i); };
    auto heightAt = [&](int i) { return drmActive ? drm.height(i) : sf.height(i); };
    ANativeWindow* top = winAt(0);
    ANativeWindow* bottom = panels >= 2 ? winAt(1) : nullptr;
    // A DRM top panel that is physically landscape but scans out portrait (the
    // Duo Lite DSI, install rotation 90) gets the layout rendered upright. The
    // SurfaceFlinger backend hands the core the already-rotated logical window.
    if (drmActive) {
        const bool upright = drm.isPortrait(0) &&
                             prop("persist.gammaos.azahar_nano.drm_upright", "1") == "1";
        gCore.set_upright(upright ? 1 : 0);
        // FIFO mode: the presenter takes frames in order and the queue paces the
        // core (vsync swapchain, shortest queue). Mailbox mode: the core runs on
        // its own frame limiter and the presenter scans out the newest frame at
        // each panel's vblank, so the two panels never pace the emulator in series.
        const bool fifo = prop("persist.gammaos.azahar_nano.drm_core_fifo", "1") == "1";
        const int extra = atoi(prop("persist.gammaos.azahar_nano.drm_core_extra", "1").c_str());
        gCore.set_present_mode(fifo ? 1 : 0, extra);
        // Even swap cadence: the scanout presenter shows one frame per vblank, so
        // the emulator's swaps must land one frame period apart.
        gCore.set_deadline_pacing(prop("persist.gammaos.azahar_nano.drm_deadline", "1") == "1" ? 1 : 0);
        ALOGI("top panel %ux%u, upright=%d", drm.width(0), drm.height(0), upright);
    }
    gCore.set_windows(top, bottom);

    // Frame rate counter: a strip on an alpha blended plane over the top panel.
    static azahar_nano::Hud hud;
    static bool sHudReady = false;
    // In-game menu (DRM backend: scanned out on the top panel in place of the game).
    static azahar_nano::Overlay overlay;
    static azahar_nano::NanoInput* sInput = nullptr;
    static azahar_nano::DrmWindows* sDrm = nullptr;
    static bool sOverlayReady = false;
    if (drmActive) {
        sDrm = &drm;
        azahar_nano::OverlayActions oa;
        oa.resume = [] {
            if (!overlay.isOpen()) return;
            overlay.close();
            sDrm->setOverlayActive(0, false);
            gCore.pause(false);
        };
        oa.saveState = [](int slot) {
            // The request is processed by the emulation loop, so unpause first.
            overlay.close();
            sDrm->setOverlayActive(0, false);
            gCore.pause(false);
            gCore.save_state(slot);
        };
        oa.loadState = [](int slot) {
            overlay.close();
            sDrm->setOverlayActive(0, false);
            gCore.pause(false);
            gCore.load_state(slot);
        };
        oa.exitGame = [] {
            ALOGI("exit requested from the menu");
            overlay.close();
            gExitRequested = true;
            gCore.pause(false);
            gCore.stop();
        };
        oa.setConfig = [](const char* sec, const char* key, const char* val) {
            gCore.set_config_value(sec, key, val);
        };
        oa.reloadSettings = [] { gCore.reload_settings(); };
        oa.setProp = [](const char* p, const char* v) { property_set(p, v); };
        oa.getProp = [](const char* p, const char* d) { return prop(p, d); };
        static std::string sUserDir;
        sUserDir = userDir;
        oa.getConfig = [](const char* sec, const char* key) { return configValue(sUserDir, sec, key); };
        oa.setNintendoLayout = [](bool on) { if (sInput) sInput->setNintendoLayout(on); };
        sOverlayReady = overlay.init(drm.overlayWindow(0), drm.width(0), drm.height(0),
                                     drm.isPortrait(0), oa, dataDir);
        if (!sOverlayReady) ALOGE("overlay unavailable");
        uint32_t hw = 0, hh = 0;
        ANativeWindow* hudWin = drm.hudWindow(0, &hw, &hh);
        if (hudWin) sHudReady = hud.init(hudWin, (int)hw, (int)hh, drm.isPortrait(0));
        if (!sHudReady) ALOGW("fps counter unavailable (no HUD plane)");
    }

    // Input: pad to the core, touch to the bottom screen window.
    azahar_nano::NanoInput input;
    sInput = &input;
    azahar_nano::InputCallbacks icb;
    icb.button = [](int b, bool p) {
        if (overlay.isOpen()) {
            overlay.onButton(b, p);
            if (overlay.isOpen()) overlay.render();
            return true;
        }
        return gCore.button(b, p);
    };
    icb.joystick = [](int a, float x, float y) { return gCore.joystick(a, x, y); };
    icb.analogButton = [](int a, float v) { return gCore.analog_button(a, v); };
    const bool touchOnSecondary = bottom != nullptr;
    icb.touch = [touchOnSecondary](float x, float y, bool p) {
        if (touchOnSecondary) gCore.secondary_touch(x, y, p);
        else gCore.touch(x, y, p);
    };
    icb.touchMoved = [touchOnSecondary](float x, float y) {
        if (touchOnSecondary) gCore.secondary_touch_moved(x, y);
        else gCore.touch_moved(x, y);
    };
    icb.menuToggle = [] {
        if (!sOverlayReady) {
            ALOGW("menu toggle ignored: overlay not ready");
            return;
        }
        if (overlay.isOpen()) {
            ALOGI("menu close");
            overlay.close();
            sDrm->setOverlayActive(0, false);
            gCore.pause(false);
            return;
        }
        ALOGI("menu open");
        gCore.pause(true);
        overlay.open();
        // Switch the panel to the overlay source first: a frame rendered before
        // the switch is discarded by the presenter as the inactive source.
        sDrm->setOverlayActive(0, true);
        overlay.render();
    };
    icb.exitRequest = [] {
        ALOGI("exit requested");
        gExitRequested = true;
        gCore.stop();
    };
    const uint32_t tw = bottom ? widthAt(1) : widthAt(0);
    const uint32_t th = bottom ? heightAt(1) : heightAt(0);
    input.start(icb, tw, th);

    // Core performance stats every 2 s while running (persist.gammaos.azahar_nano.perf_log=1
    // or always during bring-up).
    static azahar_nano::InputCallbacks* sIcb = nullptr;
    sIcb = &icb;
    std::thread perfThread([] {
        bool hudOn = false;
        uint64_t lastFlips = 0;
        int64_t lastFlipNs = 0;
        float blitFps = 0.0f;
        while (!gExitRequested) {
            for (int i = 0; i < 10 && !gExitRequested; i++) {
                usleep(100 * 1000);
                // Test hooks (adb): sys.gammaos.azahar_nano.menu=1 toggles the menu,
                // sys.gammaos.azahar_nano.menu_nav=up|down|left|right|a|b presses a key.
                if (prop("sys.gammaos.azahar_nano.menu", "") == "1") {
                    property_set("sys.gammaos.azahar_nano.menu", "");
                    if (sIcb && sIcb->menuToggle) sIcb->menuToggle();
                }
                const std::string nav = prop("sys.gammaos.azahar_nano.menu_nav", "");
                if (!nav.empty()) {
                    property_set("sys.gammaos.azahar_nano.menu_nav", "");
                    const int b = nav == "up" ? AZN_DPAD_UP : nav == "down" ? AZN_DPAD_DOWN
                                : nav == "left" ? AZN_DPAD_LEFT : nav == "right" ? AZN_DPAD_RIGHT
                                : nav == "a" ? AZN_BUTTON_A : nav == "b" ? AZN_BUTTON_B : -1;
                    if (b >= 0 && sIcb && sIcb->button) { sIcb->button(b, true); sIcb->button(b, false); }
                }
            }
            if (gExitRequested || !gCore.is_running()) continue;
            azn_perf_stats st{};
            gCore.get_perf_stats(&st);
            ALOGI("perf: game %.1f fps, system %.1f fps, speed %.0f%%, vblank %.2f ms, gpu %.2f ms",
                  st.game_fps, st.system_fps, st.emulation_speed * 100.0,
                  st.time_vblank_interval * 1000.0, st.time_gpu * 1000.0);
            // Frame rate counter (menu toggle persist.gammaos.azahar_nano.fps_counter).
            // Not drawn while the menu is open: the core is paused then, no game
            // frame flips, and the HUD queue would fill up.
            if (sHudReady && sDrm && !overlay.isOpen()) {
                const bool want = prop("persist.gammaos.azahar_nano.fps_counter", "0") == "1";
                if (want) {
                    struct timespec ts;
                    clock_gettime(CLOCK_MONOTONIC, &ts);
                    const int64_t now = (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
                    const uint64_t flips = sDrm->flipCount(0);
                    if (lastFlipNs > 0 && now > lastFlipNs) {
                        const float r = (float)(flips - lastFlips) * 1e9f / (float)(now - lastFlipNs);
                        blitFps = blitFps > 0.0f ? blitFps * 0.5f + r * 0.5f : r;
                    }
                    lastFlips = flips;
                    lastFlipNs = now;
                    hud.render(blitFps, (float)st.game_fps, st.emulation_speed < 0.92);
                    if (!hudOn) sDrm->setHudActive(0, true);
                } else if (hudOn) {
                    sDrm->setHudActive(0, false);
                }
                hudOn = want;
            }
        }
    });

    // The emulation runs on this thread; its frame deadlines are what the panels
    // show, so keep it ahead of the core's own worker threads in the scheduler.
    if (setpriority(PRIO_PROCESS, 0, -10) != 0) ALOGW("setpriority failed: %s", strerror(errno));
    ALOGI("running %s on %d panel(s) via %s", rom.c_str(), panels, drmActive ? "drm" : "sf");
    const int result = gCore.run(rom.c_str());
    gExitRequested = true;
    perfThread.join();
    ALOGI("core returned %d", result);

    input.stop();
    if (sOverlayReady) overlay.shutdown();
    if (sHudReady) hud.shutdown();
    gCore.set_windows(nullptr, nullptr);
    if (drmActive) drm.teardown();
    else sf.teardown();
    sessionDone();
    return 0;
}
