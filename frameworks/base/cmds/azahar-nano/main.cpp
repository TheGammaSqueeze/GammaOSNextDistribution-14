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

#include <dirent.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <cmath>
#include <time.h>
#include <unistd.h>

#include <atomic>
#include <functional>
#include <thread>
#include <fstream>
#include <string>
#include <algorithm>
#include <vector>

#include <cutils/properties.h>
#include <log/log.h>

#include "AzaharNanoApi.h"
#include "AzaharSettings.h"
#include "NanoInput.h"
#include "SfWindows.h"
#include "DrmWindows.h"
#include "Overlay.h"
#include "Hud.h"
#include "Brightness.h"
#include "../gammaos-nano/NanoBacklight.h"
#include <sys/system_properties.h>
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
    void (*set_pace_scale)(double) = nullptr;
    void (*set_tweaks)(int, int) = nullptr;
    void (*set_tweak)(const char*, int) = nullptr;
    void (*skip_preload)() = nullptr;   // optional (newer cores)
    int (*compiles_pending)() = nullptr;   // optional (newer cores)
    void (*set_fast_forward)(int) = nullptr;   // optional (newer cores)
    void (*set_lcd_shader)(int, int) = nullptr;  // optional (newer cores)
    void (*windows_resized)() = nullptr;          // optional (newer cores)
    void (*set_resolution)(int) = nullptr;        // optional (newer cores)
    int (*shader_cache_rebuilding)() = nullptr;   // optional (newer cores)
    void (*set_screen_aa)(int) = nullptr;         // optional (newer cores)
    void (*set_user_folder)(const char*) = nullptr;
    void (*set_config_overrides)(const char*) = nullptr;   // optional (older cores)
    int (*set_config_value)(const char*, const char*, const char*) = nullptr;
};

CoreApi gCore;

// Latest frame rate counter values, written by the perf thread for the HUD thread.
std::atomic<float> sHudBlitFps{0.0f}, sHudGameFps{0.0f};
// Emulated frames per second (the game may present none while it loads; a shader compile
// stall blocks the emulation itself, so this one drops to 0 as well).
std::atomic<float> sSystemFps{0.0f};
std::atomic<bool> sHudSlow{false};
std::atomic<bool> sFastForward{false};   // R3 fast forward state
std::atomic<float> sHudSpeed{100.0f};    // emulation speed in percent of real time
std::string gUserDir;                     // config.ini folder, for readers outside main()

// Dynamic resolution (persist.gammaos.azahar_nano.dynres=1): run at the configured
// resolution and drop to 1x with Sharp Bilinear + AA on both screens while the game cannot
// hold full speed there; probe the configured resolution again after a cooldown that doubles
// each time the probe fails (30 s up to 5 min) and resets once it holds for 30 s. Called once
// a second from the perf thread with the measured emulation speed.
struct DynRes {
    bool low = false;        // running at 1x
    bool probing = false;    // back at the configured resolution on trial
    int slowSecs = 0;        // consecutive slow seconds at the configured resolution
    int settle = 0;          // seconds to ignore after a switch (scale change hitch)
    int highSecs = 0;        // seconds held at the configured resolution
    int backoff = 30;        // seconds at 1x before the next probe
    int64_t nextProbeS = 0;
};
std::atomic<uint64_t> sHudStatsSeq{0};
// Seconds Dynamic Resolution ignores the speed after something that stalls the emulation on
// purpose (boot, a save state load, a resume from the menu or from sleep): those seconds
// read slow without the resolution being at fault.
std::atomic<int> gDynResSettle{8};
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
    ok &= sym(gCore.set_pace_scale, "azn_set_pace_scale");
    ok &= sym(gCore.set_tweaks, "azn_set_tweaks");
    ok &= sym(gCore.set_tweak, "azn_set_tweak");
    // Optional: older cores do not have it (no skip on the loading screen then).
    gCore.skip_preload =
        reinterpret_cast<void (*)()>(dlsym(gCore.handle, "azn_skip_preload"));
    gCore.compiles_pending =
        reinterpret_cast<int (*)()>(dlsym(gCore.handle, "azn_compiles_pending"));
    gCore.set_fast_forward =
        reinterpret_cast<void (*)(int)>(dlsym(gCore.handle, "azn_set_fast_forward"));
    gCore.set_lcd_shader =
        reinterpret_cast<void (*)(int, int)>(dlsym(gCore.handle, "azn_set_lcd_shader"));
    gCore.windows_resized =
        reinterpret_cast<void (*)()>(dlsym(gCore.handle, "azn_windows_resized"));
    gCore.set_resolution =
        reinterpret_cast<void (*)(int)>(dlsym(gCore.handle, "azn_set_resolution"));
    gCore.shader_cache_rebuilding =
        reinterpret_cast<int (*)()>(dlsym(gCore.handle, "azn_shader_cache_rebuilding"));
    gCore.set_screen_aa =
        reinterpret_cast<void (*)(int)>(dlsym(gCore.handle, "azn_set_screen_aa"));
    ok &= sym(gCore.set_user_folder, "azn_set_user_folder");
    ok &= sym(gCore.set_config_value, "azn_set_config_value");
    gCore.set_config_overrides =
        reinterpret_cast<void (*)(const char*)>(dlsym(gCore.handle, "azn_set_config_overrides"));
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

// The handlers that were in place before ours (debuggerd's, installed by the
// linker): restored before re-raising so a crash still leaves a tombstone.
struct sigaction gPrevCrashAction[NSIG];

void crashCleanup(int sig, siginfo_t* info, void* ctx) {
    ALOGE("crash signal %d (code %d, addr %p) on thread %d, returning to the home", sig,
          info ? info->si_code : 0, info ? info->si_addr : nullptr, gettid());
    sessionDone();
    // Hand the original signal to debuggerd's handler so the tombstone shows the
    // real fault (re-raising would record our own tgkill instead).
    if (sig > 0 && sig < NSIG) {
        const struct sigaction& prev = gPrevCrashAction[sig];
        sigaction(sig, &prev, nullptr);
        if ((prev.sa_flags & SA_SIGINFO) && prev.sa_sigaction) {
            prev.sa_sigaction(sig, info, ctx);
            return;
        }
        if (prev.sa_handler != SIG_DFL && prev.sa_handler != SIG_IGN && prev.sa_handler) {
            prev.sa_handler(sig);
            return;
        }
    } else {
        signal(sig, SIG_DFL);
    }
    raise(sig);
}

void installCrashHandlers() {
    // A dynarmic fastmem handler installed later chains back to this one for
    // faults outside the JIT, so it must be in place before the core runs.
    for (int sig : {SIGSEGV, SIGBUS, SIGABRT, SIGILL, SIGFPE}) {
        struct sigaction sa{};
        sa.sa_sigaction = crashCleanup;
        sigemptyset(&sa.sa_mask);
        sa.sa_flags = SA_SIGINFO | SA_ONSTACK;
        sigaction(sig, &sa, &gPrevCrashAction[sig]);
    }
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

// Every frontend setting read goes through the per-game override layer (AzaharSettings.h):
// an azahar_nano key the game's override holds comes from the file.
std::string prop(const char* name, const char* def) {
    char v[PROPERTY_VALUE_MAX] = {};
    azahar_nano::settings::get(name, v, def);
    return v;
}

// A config.ini value as the running game sees it: the per-game override first, then the
// file. seedConfig and the override snapshot read the file itself (configValue).
std::string configGet(const char* section, const char* key) {
    std::string v;
    if (azahar_nano::settings::configLookup(section, key, &v)) return v;
    return configValue(gUserDir, section, key);
}

// The core lays the override's config.ini keys over config.ini whenever it loads its
// settings; hand it the current set (empty when no override is active).
void pushConfigOverrides() {
    if (gCore.set_config_overrides) {
        gCore.set_config_overrides(azahar_nano::settings::overridesText().c_str());
    }
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
static bool copyFileTo(const std::string& from, const std::string& to) {
    const int in = open(from.c_str(), O_RDONLY | O_CLOEXEC);
    if (in < 0) return false;
    const std::string tmp = to + ".part";
    const int out = open(tmp.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0664);
    if (out < 0) { close(in); return false; }
    char buf[1 << 16];
    bool ok = true;
    for (;;) {
        const ssize_t n = read(in, buf, sizeof(buf));
        if (n < 0) { ok = false; break; }
        if (n == 0) break;
        for (ssize_t off = 0; off < n;) {
            const ssize_t w = write(out, buf + off, (size_t)(n - off));
            if (w <= 0) { ok = false; break; }
            off += w;
        }
        if (!ok) break;
    }
    if (fsync(out) != 0) ok = false;
    close(in);
    close(out);
    if (!ok || rename(tmp.c_str(), to.c_str()) != 0) { unlink(tmp.c_str()); return false; }
    // Keep the original time so newer/older comparisons stay meaningful.
    struct stat st{};
    if (stat(from.c_str(), &st) == 0) {
        struct timespec ts[2] = {st.st_atim, st.st_mtim};
        utimensat(AT_FDCWD, to.c_str(), ts, 0);
    }
    return true;
}

// Earlier builds handed the core the user folder as a bare absolute path, which
// its file layer resolves under the private root: saves, save states and cheats
// landed in <private><dataDir>/ instead of <dataDir>/. Move anything stranded
// there into the real folder. A file that already exists in the user folder is
// kept unless the stranded copy is newer; then the old one is kept beside it as
// <name>.bak so nothing is ever lost. Returns the number of files moved.
static int moveTree(const std::string& from, const std::string& to) {
    int moved = 0;
    DIR* d = opendir(from.c_str());
    if (!d) return 0;
    mkdir(to.c_str(), 0775);
    while (dirent* e = readdir(d)) {
        if (!strcmp(e->d_name, ".") || !strcmp(e->d_name, "..")) continue;
        const std::string src = from + "/" + e->d_name;
        const std::string dst = to + "/" + e->d_name;
        struct stat ss{};
        if (lstat(src.c_str(), &ss) != 0) continue;
        if (S_ISDIR(ss.st_mode)) {
            moved += moveTree(src, dst);
            rmdir(src.c_str());   // only succeeds once emptied
            continue;
        }
        if (!S_ISREG(ss.st_mode)) continue;
        struct stat ds{};
        if (stat(dst.c_str(), &ds) == 0) {
            if (ds.st_mtime >= ss.st_mtime) {
                // The user folder copy is as new or newer: keep it, park ours.
                const std::string park = src + ".stale";
                rename(src.c_str(), park.c_str());
                continue;
            }
            const std::string bak = dst + ".bak";
            if (!copyFileTo(dst, bak)) {
                ALOGE("migrate: cannot back up %s, leaving %s in place", dst.c_str(), src.c_str());
                continue;
            }
        }
        // rename() fails across filesystems (/data -> sdcard FUSE): copy then unlink.
        if (rename(src.c_str(), dst.c_str()) != 0) {
            if (!copyFileTo(src, dst)) {
                ALOGE("migrate: copy %s -> %s failed", src.c_str(), dst.c_str());
                continue;
            }
            unlink(src.c_str());
        }
        ALOGI("migrate: %s -> %s", src.c_str(), dst.c_str());
        moved++;
    }
    closedir(d);
    return moved;
}

static void migrateStrandedUserFolder(const std::string& userDir, const std::string& dataDir) {
    if (dataDir.empty() || dataDir[0] != '/') return;
    std::string root = userDir;
    while (root.size() > 1 && root.back() == '/') root.pop_back();
    const std::string stranded = root + dataDir;   // e.g. /data/system/azahar-nano/sdcard/ROMs/3ds
    struct stat st{};
    if (stat(stranded.c_str(), &st) != 0 || !S_ISDIR(st.st_mode)) return;
    const int n = moveTree(stranded, dataDir);
    // Remove the now empty chain up to the private root.
    for (std::string p = stranded; p.size() > root.size(); p = p.substr(0, p.find_last_of('/'))) {
        if (rmdir(p.c_str()) != 0) break;
    }
    ALOGI("migrate: %d file(s) from %s into %s", n, stranded.c_str(), dataDir.c_str());
}

// Before a slot is overwritten, keep the previous file under states/backup/ as
// <name>.<YYYYmmdd-HHMMSS>, the last kStateBackups per slot. A bad save (wrong
// scene, a crash mid-write) then never costs the player their only copy.
constexpr int kStateBackups = 5;
static std::string sStatesDir;

static void backupStateSlot(int slot) {
    if (sStatesDir.empty() || slot <= 0) return;
    char suffix[16];
    snprintf(suffix, sizeof(suffix), ".%02d.cst", slot);
    const std::string backupDir = sStatesDir + "/backup";
    DIR* d = opendir(sStatesDir.c_str());
    if (!d) return;
    std::vector<std::string> slotFiles;
    while (dirent* e = readdir(d)) {
        const std::string n = e->d_name;
        if (n.size() > strlen(suffix) && n.compare(n.size() - strlen(suffix), strlen(suffix), suffix) == 0)
            slotFiles.push_back(n);
    }
    closedir(d);
    if (slotFiles.empty()) return;
    mkdir(backupDir.c_str(), 0775);
    char stamp[32];
    const time_t now = time(nullptr);
    struct tm tmv{};
    localtime_r(&now, &tmv);
    strftime(stamp, sizeof(stamp), "%Y%m%d-%H%M%S", &tmv);
    for (const auto& n : slotFiles) {
        const std::string dst = backupDir + "/" + n + "." + stamp;
        if (copyFileTo(sStatesDir + "/" + n, dst)) ALOGI("state backup: %s", dst.c_str());
        else ALOGE("state backup of %s failed", n.c_str());
        // Prune: keep the newest kStateBackups for this file name.
        std::vector<std::string> olds;
        if (DIR* b = opendir(backupDir.c_str())) {
            while (dirent* e = readdir(b)) {
                const std::string bn = e->d_name;
                if (bn.rfind(n + ".", 0) == 0) olds.push_back(bn);
            }
            closedir(b);
        }
        std::sort(olds.begin(), olds.end());   // the stamp sorts by time
        for (size_t i = 0; i + kStateBackups < olds.size(); i++) unlink((backupDir + "/" + olds[i]).c_str());
    }
}

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
// Loading screen: set once the overlay is ready; the core reports shader
// cache progress from its load (before the first game frame).
azahar_nano::Overlay* gLoadOverlay = nullptr;
// Shows or hides the overlay's output on a panel: the DRM presenter switches the
// panel's source, the SurfaceFlinger backend shows or hides the overlay layer.
std::function<void(int panel, bool active)> gSetOverlayActive;
std::atomic<bool> gLoadScreenOn{false};
// The game waits on shader compiles (no game frames): the stall screen is up. Shares the
// overlay plane with the loading screen and the menu.
std::atomic<bool> gStallScreenOn{false};
// The core is paused for a device sleep (the menu pause is overlay.isOpen()).
std::atomic<bool> gSleepPaused{false};

bool shaderCacheRebuilding() {
    return gCore.shader_cache_rebuilding && gCore.shader_cache_rebuilding() != 0;
}

// What the user reads while shaders compile. A rebuilt cache (the emulator was updated or the
// cache did not match) is a one-off per game; otherwise these are shaders this game has not
// needed before.
const char* kRebuildNote =
    "This game's saved shaders no longer match the emulator (it was updated),\n"
    "so they are being rebuilt. This happens once per game.";
const char* kFirstUseNote =
    "This scene needs shaders this game has not used before.\n"
    "They are saved, so this only happens the first time.";
int64_t gLoadLastDrawNs = 0;

int64_t monoNs() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

void onLoadProgress(void*, int stage, int progress, int max) {
    if (stage == AZN_LOAD_COMPLETE || (progress % 50) == 0) {
        ALOGI("shader cache stage %d %d/%d", stage, progress, max);
    }
    if (!gLoadOverlay || !gSetOverlayActive) return;
    if (stage == AZN_LOAD_COMPLETE) {
        if (gLoadScreenOn.exchange(false)) {
            gSetOverlayActive(0, false);
            ALOGI("loading screen closed");
        }
        return;
    }
    // Only worth showing when there is real work (a long pipeline build list).
    if (max < 20) return;
    const int64_t now = monoNs();
    const bool last = progress + 1 >= max;
    if (gLoadScreenOn && !last && now - gLoadLastDrawNs < 50000000LL) return;
    gLoadLastDrawNs = now;
    const bool rebuild = shaderCacheRebuilding();
    const char* title = rebuild ? "Rebuilding shader cache"
                        : stage == AZN_LOAD_BUILD ? "Preparing shaders" : "Loading shader cache";
    char detail[96];
    snprintf(detail, sizeof(detail), "%d of %d%s", progress + 1, max,
             gCore.skip_preload ? "    Press START to skip" : "");
    if (!gLoadScreenOn.exchange(true)) {
        gStallScreenOn = false;   // the loading screen takes the plane over
        gSetOverlayActive(0, true);
        ALOGI("loading screen open (stage %d, %d items, rebuild %d)", stage, max, rebuild);
    }
    gLoadOverlay->renderLoading(title, detail, max > 0 ? (float)(progress + 1) / (float)max : -1.0f,
                                rebuild ? kRebuildNote : "Shaders this game used before are prepared up front.");
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

// One second of dynamic resolution (see DynRes).
static void dynResTick(double speed, bool menuOpen,
                       const std::function<void(int, int)>& setLcdShader) {
    static DynRes d;
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    const int64_t nowS = ts.tv_sec;
    const int base = atoi(configGet("Renderer", "resolution_factor").c_str());
    const bool on = prop("persist.gammaos.azahar_nano.dynres", "0") == "1" && base > 1 &&
                    gCore.set_resolution != nullptr;
    const auto userShaders = [&] {
        if (!setLcdShader) return;
        setLcdShader(0, atoi(prop("persist.gammaos.azahar_nano.lcd_top", "0").c_str()));
        setLcdShader(1, atoi(prop("persist.gammaos.azahar_nano.lcd_bottom", "0").c_str()));
    };
    const auto raise = [&] {
        gCore.set_resolution(base);
        userShaders();
        d.low = false;
        d.slowSecs = 0;
        d.highSecs = 0;
        d.settle = 2;
    };
    if (!on) {
        if (d.low) {
            ALOGI("dynres: off, back to %dx", base);
            raise();
        }
        d = DynRes{};
        return;
    }
    // Not a game speed reading: menu (paused), fast forward, the loading screen, or shader
    // compiles (their stalls are not the resolution's fault).
    const int pending = gCore.compiles_pending ? gCore.compiles_pending() : 0;
    if (gDynResSettle > 0) {
        gDynResSettle--;
        d.slowSecs = 0;
        return;
    }
    // A reading above 150% is not a speed either (catching up after a stall).
    if (menuOpen || sFastForward || gLoadScreenOn || pending > 0 || speed < 0.05 || speed > 1.5) {
        d.slowSecs = 0;
        return;
    }
    if (d.settle > 0) {
        d.settle--;
        return;
    }
    if (!d.low) {
        // Four seconds in a row under 95%: a game that runs at 96-100% with the odd dip (Metroid:
        // Samus Returns at 2x) keeps its resolution.
        d.slowSecs = speed < 0.95 ? d.slowSecs + 1 : 0;
        if (d.slowSecs >= 4) {
            // A failed probe waits twice as long next time; a first drop starts at 30 s.
            d.backoff = d.probing ? std::min(d.backoff * 2, 300) : 30;
            d.probing = false;
            d.low = true;
            d.settle = 2;
            d.nextProbeS = nowS + d.backoff;
            gCore.set_resolution(1);
            // The user's screen shaders stay (they are keyed to the 3DS pixels, so they look
            // the same at 1x); a screen without one gets Sharp Bilinear, which keeps the
            // 1x image crisp on the panel. Screen AA follows its own setting.
            if (setLcdShader) {
                for (int screen = 0; screen < 2; screen++) {
                    const int user = atoi(prop(screen == 0 ? "persist.gammaos.azahar_nano.lcd_top"
                                                           : "persist.gammaos.azahar_nano.lcd_bottom",
                                               "0").c_str());
                    setLcdShader(screen, user > 0 ? user : 6);
                }
            }
            ALOGI("dynres: %.0f%% speed at %dx, 1x (screen shaders kept, sharp bilinear where none), "
                  "next try in %d s", speed * 100.0, base, d.backoff);
        } else if (d.probing && ++d.highSecs >= 30) {
            d.probing = false;
            d.backoff = 30;
            ALOGI("dynres: %dx holds", base);
        }
    } else if (nowS >= d.nextProbeS) {
        ALOGI("dynres: trying %dx again", base);
        raise();
        d.probing = true;
    }
}

// persist.gammaos.azahar_nano.env = "NAME=value[,NAME=value]" is exported before any
// driver loads (for example TU_DEBUG for Turnip). Read through the settings layer, so a
// per-game override can carry it too (env=TU_DEBUG=sysmem: Luigi's Mansion renders 10 to
// 25 percent faster with Turnip forced to system memory rendering).
static void exportEnvProp() {
    const std::string all = prop("persist.gammaos.azahar_nano.env", "");
    size_t pos = 0;
    while (pos < all.size()) {
        const size_t comma = all.find(',', pos);
        const std::string kv = all.substr(pos, comma == std::string::npos ? std::string::npos : comma - pos);
        const size_t eq = kv.find('=');
        if (eq != std::string::npos && eq > 0) {
            setenv(kv.substr(0, eq).c_str(), kv.substr(eq + 1).c_str(), 1);
            ALOGI("env %s", kv.c_str());
        }
        if (comma == std::string::npos) break;
        pos = comma + 1;
    }
}

int main(int argc, char** argv) {
    exportEnvProp();
    installCrashHandlers();
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
    gUserDir = userDir;
    // Saves, save states, cheats, dumps and mods live in the user folder (the Game
    // Settings "Azahar Data Folder" row, persist.gammaos.azahar.data_dir); everything
    // else stays in the private root. When both point at the same place (a user who
    // keeps a whole Azahar user directory, e.g. the stock app's) nothing moves.
    const std::string dataDir = prop("persist.gammaos.azahar.data_dir", kDataDirDefault);
    // Per-game settings override (AzaharSettings.h): <user folder>/overrides/<rom base>.cfg.
    // When it exists it is the source of truth for every setting it holds from here on;
    // the global properties and config.ini are neither read for those keys nor written.
    {
        const size_t slash = rom.find_last_of('/');
        std::string base = slash == std::string::npos ? rom : rom.substr(slash + 1);
        const size_t dot = base.find_last_of('.');
        if (dot != std::string::npos && dot > 0) base = base.substr(0, dot);
        azahar_nano::settings::setOverridePath(dataDir + "/overrides/" + base + ".cfg");
        if (!azahar_nano::settings::load()) {
            if (azahar_nano::settings::overrideUnreadable()) {
                ALOGW("per-game override for %s is unreadable (%s), global settings apply this session",
                      base.c_str(), azahar_nano::settings::overrideError().c_str());
            } else {
                ALOGI("no per-game override for %s, global settings apply", base.c_str());
            }
        }
        // The override may carry a driver environment of its own.
        exportEnvProp();
        // Core switches the override may carry (the lighting approximations): the core reads
        // sys.gammaos.azahar_nano.<key> first and the persist prop second, so the override
        // value (or the global one when the override has no line) is published per launch
        // without touching the global persist props.
        for (const char* key : {"light_max", "no_shadow_map"}) {
            const std::string v = prop((std::string("persist.gammaos.azahar_nano.") + key).c_str(), "");
            property_set((std::string("sys.gammaos.azahar_nano.") + key).c_str(), v.c_str());
        }
    }
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
    if (dataDir != userDir) {
        mkdir(dataDir.c_str(), 0775);
        migrateStrandedUserFolder(userDir, dataDir);
        gCore.set_user_folder(dataDir.c_str());
    }
    ALOGI("private root %s, user folder %s", userDir.c_str(), dataDir.c_str());
    // GammaOS tweaks (persist.gammaos.azahar_nano.tweaks, default on): renderer side.
    gCore.set_tweaks(prop("persist.gammaos.azahar_nano.tweaks", "1") == "1" ? 1 : 0,
                     prop("persist.gammaos.azahar_nano.vertex_depth", "1") == "1" ? 1 : 0);
    {
        const bool tweaks = prop("persist.gammaos.azahar_nano.tweaks", "1") == "1";
        gCore.set_tweak("jit_warmup",
                        tweaks && prop("persist.gammaos.azahar_nano.jit_warmup", "1") == "1" ? 1 : 0);
        gCore.set_tweak("hw_scissor",
                        tweaks && prop("persist.gammaos.azahar_nano.hw_scissor", "1") == "1" ? 1 : 0);
        gCore.set_tweak("fold_clears",
                        tweaks && prop("persist.gammaos.azahar_nano.fold_clears", "1") == "1" ? 1 : 0);
        gCore.set_tweak("fs_mediump",
                        prop("persist.gammaos.azahar_nano.fs_mediump", "0") == "1" ? 1 : 0);
        gCore.set_tweak("renderpass_clears",
                        tweaks && prop("persist.gammaos.azahar_nano.rp_clears", "1") == "1" ? 1 : 0);
    }
    sStatesDir = dataDir + "/states";
    seedConfig(userDir);

    // GPU driver: the adrenotools hook libraries ship next to the core; a custom
    // driver is an unpacked adrenotools package under <user>/gpu_drivers/<name>/.
    // The package lives in the user's data folder (zips are imported there by the
    // menu) but the sdcard is mounted noexec, so the chosen package is staged
    // under the private root and loaded from there.
    const std::string hookDir = coreLib.substr(0, coreLib.find_last_of('/'));
    // persist.gammaos.azahar_nano.gpu_driver: empty = the default for this chip (the
    // bundled driver made for it, else the system one), "system" = the vendor driver,
    // anything else = a package by name (the user's, else a bundled one).
    const std::string driverChoice = prop("persist.gammaos.azahar_nano.gpu_driver", "");
    const std::string driverName = driverChoice.empty()      ? azahar_nano::gpudrv::defaultPackage()
                                   : driverChoice == "system" ? std::string()
                                                              : driverChoice;
    ALOGI("gpu driver: %s (setting \"%s\")", driverName.empty() ? "system" : driverName.c_str(),
          driverChoice.c_str());
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
    // "Sharp Bilinear + AA" (7) became Sharp Bilinear (6) plus the separate Screen AA option.
    for (const char* key : {"persist.gammaos.azahar_nano.lcd_top",
                            "persist.gammaos.azahar_nano.lcd_bottom"}) {
        if (prop(key, "0") == "7") {
            property_set(key, "6");
            property_set("persist.gammaos.azahar_nano.screen_aa", "1");
            ALOGI("%s: Sharp Bilinear + AA is now Sharp Bilinear with Screen AA on", key);
        }
    }
    if (gCore.set_lcd_shader) {
        gCore.set_lcd_shader(0, atoi(prop("persist.gammaos.azahar_nano.lcd_top", "0").c_str()));
        gCore.set_lcd_shader(1, atoi(prop("persist.gammaos.azahar_nano.lcd_bottom", "0").c_str()));
    }
    if (gCore.set_screen_aa) {
        gCore.set_screen_aa(prop("persist.gammaos.azahar_nano.screen_aa", "0") == "1");
    }

    // Panels. DRM backend: our own BufferQueues flipped straight onto the panels.
    // SurfaceFlinger backend: one opaque layer per physical display.
    azahar_nano::SfWindows sf;
    azahar_nano::DrmWindows drm;
    const bool useDrm = backend == "drm";
    int panels = 0;
    if (useDrm) {
        // GammaOS tweaks (persist.gammaos.azahar_nano.tweaks, default on): native output.
        // The game buffers get the emulated screen height at the internal resolution
        // (240 px times the resolution factor) so the image is drawn 1:1 and the
        // display plane does the single upscale to the panel. Only for the default
        // one-screen-per-panel layout, where each panel shows exactly one 3DS screen.
        if (prop("persist.gammaos.azahar_nano.tweaks", "1") == "1" &&
            configGet("Layout", "layout_option") == "1") {
            const int factor = atoi(configGet("Renderer", "resolution_factor").c_str());
            // A panel whose screen has an LCD shader draws at least 3 output pixels per 3DS
            // pixel (720 short side) so the grid lands on whole pixels before the plane's
            // upscale. Full panel resolution cost ~8 fps on the Duo Lite top panel (memory
            // bandwidth), 720 costs nothing measurable, 960 cost ~4 fps.
            const bool swap = configGet("Layout", "swap_screen") == "true";
            const bool lcd[2] = {
                atoi(prop("persist.gammaos.azahar_nano.lcd_top", "0").c_str()) > 0,
                atoi(prop("persist.gammaos.azahar_nano.lcd_bottom", "0").c_str()) > 0};
            if (factor >= 1 && factor <= 10) {
                for (int panel = 0; panel < 2; panel++) {
                    const int screen = swap ? 1 - panel : panel;
                    drm.setOutputShortSide(panel, 240u * factor);
                    // Both sizes are verified at create; the menu switches live.
                    drm.setLcdShortSide(panel, std::max(240u * factor, 720u), lcd[screen]);
                }
                ALOGI("tweaks: native output for resolution factor %d", factor);
            }
        }
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
        // Pace to the panel's exact refresh (the DSI runs 59.8 Hz, not 60): one
        // emulated frame per real vblank, so no frame is dropped every few seconds.
        // The mode timings are nominal (the DSI reports 60.000 while it flips at
        // 59.9), so the scale follows the measured flip interval (perf thread).
        ALOGI("top panel mode %.3f Hz (nominal); pacing follows the measured vblank", drm.refreshHz(0));
        ALOGI("top panel %ux%u, upright=%d", drm.width(0), drm.height(0), upright);
    }
    gCore.set_windows(top, bottom);

    // Frame rate counter: a strip on an alpha blended plane over the top panel.
    static azahar_nano::Hud hud;
    static bool sHudReady = false;
    // In-game menu: shown on the top panel in place of the game (the DRM presenter
    // switches the panel's source, SurfaceFlinger shows the menu layer over the game).
    static azahar_nano::Overlay overlay;
    static azahar_nano::NanoInput* sInput = nullptr;
    static azahar_nano::DrmWindows* sDrm = nullptr;
    static azahar_nano::SfWindows* sSf = nullptr;
    static bool sOverlayReady = false;
    static azahar_nano::OverlayActions sOa;   // state hooks use the same actions
    {
        if (drmActive) {
            sDrm = &drm;
            gSetOverlayActive = [](int panel, bool active) { sDrm->setOverlayActive(panel, active); };
        } else {
            sSf = &sf;
            gSetOverlayActive = [](int panel, bool active) { sSf->setOverlayActive(panel, active); };
        }
        azahar_nano::OverlayActions oa;
        oa.resume = [] {
            if (!overlay.isOpen()) return;
            overlay.close();
            gSetOverlayActive(0, false);
            gCore.pause(false);
            gDynResSettle = 5;
        };
        oa.saveState = [](int slot) {
            backupStateSlot(slot);
            // The request is processed by the emulation loop, so unpause first.
            overlay.close();
            gSetOverlayActive(0, false);
            gCore.pause(false);
            gDynResSettle = 5;
            gCore.save_state(slot);
        };
        oa.loadState = [](int slot) {
            overlay.close();
            gSetOverlayActive(0, false);
            gCore.pause(false);
            gDynResSettle = 5;
            gCore.load_state(slot);
            gDynResSettle = 5;
        };
        oa.exitGame = [] {
            ALOGI("exit requested from the menu");
            overlay.close();
            gExitRequested = true;
            gCore.pause(false);
            gDynResSettle = 5;
            gCore.stop();
        };
        oa.setConfig = [](const char* sec, const char* key, const char* val) {
            // With an override active the value goes to the override file, never to
            // config.ini; the core sees it at the reload that follows.
            if (azahar_nano::settings::setConfig(sec, key, val)) pushConfigOverrides();
            else gCore.set_config_value(sec, key, val);
        };
        oa.reloadSettings = [] { gCore.reload_settings(); };
        oa.setProp = [](const char* p, const char* v) { azahar_nano::settings::set(p, v); };
        oa.getProp = [](const char* p, const char* d) { return prop(p, d); };
        static std::string sUserDir;
        sUserDir = userDir;
        oa.getConfig = [](const char* sec, const char* key) { return configGet(sec, key); };
        oa.getGlobalConfig = [](const char* sec, const char* key) {
            return configValue(sUserDir, sec, key);
        };
        oa.overrideChanged = [] {
            // An override was created or deleted: the core reloads its settings with (or
            // without) the override's config keys, and the frontend side re-applies what
            // it keeps live.
            pushConfigOverrides();
            gCore.reload_settings();
            if (sInput) {
                sInput->setNintendoLayout(
                    azahar_nano::settings::getBool("persist.gammaos.azahar_nano.nintendo_layout", false));
                sInput->setStickDpadSwap(
                    azahar_nano::settings::getBool("persist.gammaos.azahar_nano.stick_dpad_swap", false));
            }
            if (sOa.setLcdShader) {
                sOa.setLcdShader(0, atoi(prop("persist.gammaos.azahar_nano.lcd_top", "0").c_str()));
                sOa.setLcdShader(1, atoi(prop("persist.gammaos.azahar_nano.lcd_bottom", "0").c_str()));
            }
            if (sOa.setScreenAa) sOa.setScreenAa(prop("persist.gammaos.azahar_nano.screen_aa", "0") == "1");
            gDynResSettle = 5;
        };
        oa.setNintendoLayout = [](bool on) { if (sInput) sInput->setNintendoLayout(on); };
        oa.setStickDpadSwap = [](bool on) { if (sInput) sInput->setStickDpadSwap(on); };
        oa.setScreenAa = [](bool on) {
            if (gCore.set_screen_aa) gCore.set_screen_aa(on ? 1 : 0);
        };
        oa.setLcdShader = [](int screen, int shader) {
            if (gCore.set_lcd_shader) gCore.set_lcd_shader(screen, shader);
            // The panel showing this screen switches to its LCD (or plain) buffer size now.
            if (!sDrm) return;
            const bool swap = configGet("Layout", "swap_screen") == "true";
            const int panel = swap ? 1 - screen : screen;
            if (panel < sDrm->count() && sDrm->setLcdOutput(panel, shader > 0) &&
                gCore.windows_resized) {
                gCore.windows_resized();
            }
        };
        // The SurfaceFlinger windows are the framework's logical (already turned) space,
        // so only a DRM portrait panel draws the menu and the HUD upright by itself.
        const bool upright = drmActive && drm.isPortrait(0);
        ANativeWindow* ovWin = drmActive ? drm.overlayWindow(0) : sf.overlayWindow(0);
        sOverlayReady = ovWin && overlay.init(ovWin, (int)widthAt(0), (int)heightAt(0), upright, oa, dataDir);
        if (!sOverlayReady) ALOGE("overlay unavailable");
        if (sOverlayReady && prop("persist.gammaos.azahar_nano.loading_screen", "1") == "1") {
            gLoadOverlay = &overlay;
        }
        sOa = oa;
        uint32_t hw = 0, hh = 0;
        ANativeWindow* hudWin = drmActive ? drm.hudWindow(0, &hw, &hh) : sf.hudWindow(0, &hw, &hh);
        if (hudWin) sHudReady = hud.init(hudWin, (int)hw, (int)hh, upright);
        if (!sHudReady) ALOGW("fps counter unavailable (no HUD plane)");
    }

    // Input: pad to the core, touch to the bottom screen window.
    azahar_nano::NanoInput input;
    sInput = &input;
    azahar_nano::InputCallbacks icb;
    // Fast forward on R3: each press toggles it while the menu is closed.
    static auto toggleFastForward = [] {
        if (!gCore.set_fast_forward || gLoadScreenOn || overlay.isOpen()) return;
        // A bouncing switch: one toggle per 250 ms at most.
        static int64_t lastToggleNs = 0;
        struct timespec ts;
        clock_gettime(CLOCK_MONOTONIC, &ts);
        const int64_t now = (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
        if (now - lastToggleNs < 250000000LL) return;
        lastToggleNs = now;
        const bool on = !sFastForward.load();
        sFastForward = on;
        gCore.set_fast_forward(on ? 1 : 0);
        sHudStatsSeq++;
        ALOGI("fast forward %s (R3)", on ? "on" : "off");
    };
    icb.fastForwardToggle = [] { toggleFastForward(); };
    icb.menuOpen = [] { return overlay.isOpen(); };
    icb.button = [](int b, bool p) {
        // Start on the shader loading screen skips the rest of the precompile.
        if (gLoadScreenOn && p && b == AZN_BUTTON_START) {
            if (gCore.skip_preload) {
                ALOGI("loading screen: Start pressed, skipping the shader precompile");
                gCore.skip_preload();
            }
            return true;
        }
        if (overlay.isOpen()) {
            overlay.onButton(b, p);
            if (overlay.isOpen()) overlay.render();
            return true;
        }
        return gCore.button(b, p);
    };
    icb.joystick = [](int a, float x, float y) { return gCore.joystick(a, x, y); };
    icb.analogButton = [](int a, float v) { return gCore.analog_button(a, v); };
    // Touch arrives as a fraction of the panel it was made on. Each panel's touches go to the
    // window on that panel (the top one included: on the RP Duo Lite every touch used to land
    // on the bottom screen, the top panel's turned by 90 degrees), in window (buffer) pixels:
    // the panel size times its current render scale (an LCD shader switch changes it live).
    static uint32_t sPanelW[2] = {widthAt(0), bottom ? widthAt(1) : 0};
    static uint32_t sPanelH[2] = {heightAt(0), bottom ? heightAt(1) : 0};
    static auto toWindow = [](int panel, float& x, float& y) {
        const float k = sDrm ? sDrm->renderScale(panel) : 1.0f;
        x *= sPanelW[panel] * k;
        y *= sPanelH[panel] * k;
    };
    icb.touch = [](int panel, float x, float y, bool p) {
        toWindow(panel, x, y);
        if (panel == 1) gCore.secondary_touch(x, y, p);
        else gCore.touch(x, y, p);
    };
    icb.touchMoved = [](int panel, float x, float y) {
        toWindow(panel, x, y);
        if (panel == 1) gCore.secondary_touch_moved(x, y);
        else gCore.touch_moved(x, y);
    };
    icb.menuToggle = [] {
        if (!sOverlayReady) {
            ALOGW("menu toggle ignored: overlay not ready");
            return;
        }
        if (gLoadScreenOn) {
            ALOGW("menu toggle ignored: loading screen up");
            return;
        }
        if (overlay.isOpen()) {
            ALOGI("menu close");
            overlay.close();
            gSetOverlayActive(0, false);
            gCore.pause(false);
            gDynResSettle = 5;
            return;
        }
        ALOGI("menu open");
        if (gStallScreenOn.exchange(false)) ALOGI("stall screen closed for the menu");
        gCore.pause(true);
        overlay.open();
        // Switch the panel to the overlay source first: a frame rendered before
        // the switch is discarded by the presenter as the inactive source.
        gSetOverlayActive(0, true);
        overlay.render();
    };
    icb.exitRequest = [] {
        ALOGI("exit requested");
        gExitRequested = true;
        gCore.stop();
    };
    input.start(icb, bottom != nullptr, bottom ? 1 : 0);

    // Core performance stats every 2 s while running (persist.gammaos.azahar_nano.perf_log=1
    // or always during bring-up).
    static azahar_nano::InputCallbacks* sIcb = nullptr;
    sIcb = &icb;
    static bool sPaceToPanel = false;
    static double sPaceScale = 1.0;
    sPaceToPanel = prop("persist.gammaos.azahar_nano.pace_to_panel", "1") == "1";
    // Sleep: in the DRM session the framework owns the power key and the lid and puts the device
    // to sleep, but the panels are ours: without this the 3DS kept emulating, playing audio and
    // lighting both panels behind a sleeping device. Follow sys.screen.state (PowerManager keeps
    // it in step with wakefulness in nano mode): pause the core and turn the backlights off, then
    // relight at the saved level and resume what we paused.
    std::thread sleepWatch([] {
        const prop_info* pi = nullptr;
        while (!pi && !gExitRequested) {
            pi = __system_property_find("sys.screen.state");
            if (!pi) usleep(500 * 1000);
        }
        uint32_t serial = 0;
        bool asleep = false, pausedForSleep = false;
        while (!gExitRequested) {
            char v[PROP_VALUE_MAX] = {};
            __system_property_read(pi, nullptr, v);
            const bool off = strcmp(v, "off") == 0;
            if (off && !asleep) {
                asleep = true;
                pausedForSleep = !overlay.isOpen();
                if (pausedForSleep) {
                    gSleepPaused = true;
                    gCore.pause(true);
                }
                android::nanobl::nanoBacklightSet(0);
                ALOGI("sleep: screen off, core %s, backlights off",
                      pausedForSleep ? "paused" : "left as the menu has it");
            } else if (!off && asleep) {
                asleep = false;
                android::nanobl::nanoBacklightSet(azahar_nano::brightnessLevel());
                if (pausedForSleep) {
                    gCore.pause(false);
                    gDynResSettle = 5;
                    gSleepPaused = false;
                }
                pausedForSleep = false;
                ALOGI("sleep: screen on, backlights restored");
            }
            // Wakes on the next change of the property (1 s cap so exit is noticed).
            timespec ts = {1, 0};
            __system_property_wait(pi, serial, &serial, &ts);
        }
    });
    sleepWatch.detach();

    std::thread perfThread([] {
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
                // sys.gammaos.azahar_nano.load_state=N / save_state=N: direct slot
                // access for scripted tests (the request runs on the emulation loop).
                const std::string ld = prop("sys.gammaos.azahar_nano.load_state", "");
                if (!ld.empty()) {
                    property_set("sys.gammaos.azahar_nano.load_state", "");
                    const int slot = atoi(ld.c_str());
                    if (slot > 0 && sOa.loadState) { ALOGI("hook: load state %d", slot); sOa.loadState(slot); }
                }
                const std::string sv = prop("sys.gammaos.azahar_nano.save_state", "");
                if (!sv.empty()) {
                    property_set("sys.gammaos.azahar_nano.save_state", "");
                    const int slot = atoi(sv.c_str());
                    if (slot > 0 && sOa.saveState) { ALOGI("hook: save state %d", slot); sOa.saveState(slot); }
                }
                // sys.gammaos.azahar_nano.pause=1/0: freeze the game for screenshot
                // comparisons (no menu drawn).
                const std::string pz = prop("sys.gammaos.azahar_nano.pause", "");
                if (!pz.empty()) {
                    property_set("sys.gammaos.azahar_nano.pause", "");
                    ALOGI("hook: pause %s", pz.c_str());
                    gCore.pause(pz == "1");
                }
                // sys.gammaos.azahar_nano.lcd="<top> <bottom>": switch the LCD shaders the way
                // the menu rows do (scripted tests).
                const std::string lcdSet = prop("sys.gammaos.azahar_nano.lcd", "");
                if (!lcdSet.empty()) {
                    property_set("sys.gammaos.azahar_nano.lcd", "");
                    int t = 0, b = 0;
                    if (sscanf(lcdSet.c_str(), "%d %d", &t, &b) == 2 && sOa.setLcdShader) {
                        ALOGI("hook: lcd %d %d", t, b);
                        sOa.setLcdShader(0, t);
                        sOa.setLcdShader(1, b);
                    }
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
            // Pace the emulator to the top panel's real refresh: one emulated frame
            // per vblank, so no frame is dropped or repeated every few seconds.
            if (sDrm && sPaceToPanel) {
                const double periodNs = sDrm->measuredPeriodNs(0);
                if (periodNs > 0.0) {
                    const double scale = periodNs / (1e9 / 60.0);
                    if (std::fabs(scale - sPaceScale) > 2e-5) {
                        sPaceScale = scale;
                        gCore.set_pace_scale(scale);
                        ALOGI("pace: top panel %.3f Hz measured, scale %.5f", 1e9 / periodNs, scale);
                    }
                }
            }
            azn_perf_stats st{};
            gCore.get_perf_stats(&st);
            ALOGI("perf: game %.1f fps, system %.1f fps, speed %.0f%%, vblank %.2f ms, gpu %.2f ms",
                  st.game_fps, st.system_fps, st.emulation_speed * 100.0,
                  st.time_vblank_interval * 1000.0, st.time_gpu * 1000.0);
            // Frame rate counter values for the HUD thread.
            {
                struct timespec ts;
                clock_gettime(CLOCK_MONOTONIC, &ts);
                const int64_t now = (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
                const uint64_t flips = sDrm ? sDrm->flipCount(0) : 0;
                if (lastFlipNs > 0 && now > lastFlipNs) {
                    const float r = (float)(flips - lastFlips) * 1e9f / (float)(now - lastFlipNs);
                    blitFps = blitFps > 0.0f ? blitFps * 0.5f + r * 0.5f : r;
                }
                lastFlips = flips;
                lastFlipNs = now;
                sHudBlitFps = blitFps;
                sHudGameFps = (float)st.game_fps;
                sSystemFps = (float)st.system_fps;
                sHudSlow = st.emulation_speed < 0.92;
                sHudSpeed = (float)(st.emulation_speed * 100.0);
                sHudStatsSeq++;
            }
            dynResTick(st.emulation_speed, overlay.isOpen(), sOa.setLcdShader);
        }
    });

    // HUD drawer: the frame rate counter (menu toggle persist.gammaos.azahar_nano.fps_counter)
    // and the "Compiling shaders" notice (persist.gammaos.azahar_nano.compile_notice), which
    // fades in while the core has compile jobs and out once they are done. Redraws at about
    // 30 Hz while something animates, otherwise only when the counter changes. Nothing is
    // drawn while the menu is open: the core is paused, no game frame flips and the HUD
    // queue would fill up.
    std::thread hudThread([] {
        if (!sHudReady) return;
        bool hudOn = false;
        uint32_t lastRegions = 0;
        uint64_t lastSeq = ~0ull;
        float toast = 0.0f, spin = 0.0f;
        int64_t stallSinceNs = 0;
        int64_t idleSinceNs = 0;
        int64_t lastNs = 0;
        int propTick = 0;
        bool wantFps = false, wantNotice = true, stallPreview = false;
        while (!gExitRequested) {
            struct timespec ts;
            clock_gettime(CLOCK_MONOTONIC, &ts);
            const int64_t now = (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
            const float dt = lastNs ? std::min((float)(now - lastNs) * 1e-9f, 0.1f) : 0.0f;
            lastNs = now;
            if (propTick-- <= 0) {
                propTick = 15;
                wantFps = prop("persist.gammaos.azahar_nano.fps_counter", "0") == "1";
                wantNotice = prop("persist.gammaos.azahar_nano.compile_notice", "1") == "1";
                // Debug: show the stall screen without a real stall (screenshots, layout checks).
                stallPreview = prop("sys.gammaos.azahar_nano.stall_preview", "0") == "1";
            }
            const bool live = gCore.is_running() && !overlay.isOpen() && !gLoadScreenOn;
            // Stall screen: emulation has stopped (no emulated frame in the last second) while
            // shader jobs are pending, so the game is waiting on them. The HUD plane only
            // reaches the panel with game frames, so the toast and the frame counter would just
            // freeze; the overlay plane presents on its own. Opens after 1.5 s of that and only
            // while jobs are queued; closes as soon as emulation runs again.
            {
                const int jobs = gCore.compiles_pending ? gCore.compiles_pending() : 0;
                const bool emuStopped = sSystemFps.load() < 0.5f;
                if (live && !gSleepPaused && emuStopped && (jobs > 0 || gStallScreenOn)) {
                    if (stallSinceNs == 0) stallSinceNs = now;
                } else {
                    stallSinceNs = 0;
                }
                if (stallPreview && live && stallSinceNs == 0) stallSinceNs = now - 1500000000LL;
                const bool wantStall = stallSinceNs && now - stallSinceNs >= 1500000000LL &&
                                       (jobs > 0 || gStallScreenOn || stallPreview) &&
                                       sOverlayReady && gLoadOverlay && gSetOverlayActive;
                if (wantStall && !overlay.isOpen() && !gLoadScreenOn) {
                    if (!gStallScreenOn.exchange(true)) {
                        gSetOverlayActive(0, true);
                        ALOGI("stall screen open (%d compile jobs, rebuild %d)", jobs,
                              shaderCacheRebuilding());
                    }
                    const bool rebuild = shaderCacheRebuilding();
                    const int secs = (int)((now - stallSinceNs) / 1000000000LL);
                    char detail[96];
                    snprintf(detail, sizeof(detail), "%d compiling    %d:%02d    the game continues by itself",
                             jobs, secs / 60, secs % 60);
                    gLoadOverlay->renderLoading(rebuild ? "Rebuilding shader cache" : "Compiling shaders",
                                                detail, -1.0f, rebuild ? kRebuildNote : kFirstUseNote,
                                                (float)((now / 1000000LL) % 1600) / 1600.0f);
                } else if (!wantStall && gStallScreenOn.exchange(false)) {
                    if (!overlay.isOpen() && !gLoadScreenOn) gSetOverlayActive(0, false);
                    ALOGI("stall screen closed");
                }
            }
            // The notice stays up through short gaps between jobs (no flicker) and
            // fades out 0.4 s after the queue drained.
            const int pending = gCore.compiles_pending ? gCore.compiles_pending() : 0;
            if (pending > 0) idleSinceNs = 0;
            else if (idleSinceNs == 0) idleSinceNs = now;
            const bool compiling = pending > 0 || (idleSinceNs && now - idleSinceNs < 400000000LL);
            const float target = live && wantNotice && compiling ? 1.0f : 0.0f;
            const float step = dt / 0.25f;   // 250 ms fades
            toast = target > toast ? std::min(target, toast + step) : std::max(target, toast - step);
            if (toast > 0.0f) spin += dt * 1.1f;
            else spin = 0.0f;
            const bool fps = live && wantFps;
            const bool ff = live && sFastForward.load();
            const bool want = fps || ff || toast > 0.0f;
            const uint32_t regions = (fps || ff ? azahar_nano::kHudTop : 0u) |
                                     (toast > 0.0f ? azahar_nano::kHudBottom : 0u);
            const uint64_t seq = sHudStatsSeq.load();
            const bool animating = toast > 0.0f;
            if (want && (animating || seq != lastSeq || regions != lastRegions || !hudOn)) {
                azahar_nano::Hud::Frame f;
                f.fps = fps;
                f.blitFps = sHudBlitFps.load();
                f.gameFps = sHudGameFps.load();
                f.slow = sHudSlow.load();
                f.ff = ff;
                f.speedPct = sHudSpeed.load();
                f.toastAlpha = toast;
                f.spin = spin;
                hud.render(f);
                if (sDrm) {
                    sDrm->setHudRegions(0, regions);
                    if (!hudOn) sDrm->setHudActive(0, true);
                } else if (sSf && !hudOn) {
                    sSf->setHudActive(0, true);
                }
                hudOn = true;
                lastSeq = seq;
                lastRegions = regions;
            } else if (!want && hudOn) {
                if (sDrm) sDrm->setHudActive(0, false);
                else if (sSf) sSf->setHudActive(0, false);
                hudOn = false;
                lastRegions = 0;
            }
            usleep(animating || gStallScreenOn ? 33 * 1000 : 100 * 1000);
        }
    });

    // The emulation runs on this thread; its frame deadlines are what the panels
    // show, so keep it ahead of the core's own worker threads in the scheduler.
    if (setpriority(PRIO_PROCESS, 0, -10) != 0) ALOGW("setpriority failed: %s", strerror(errno));
    ALOGI("running %s on %d panel(s) via %s", rom.c_str(), panels, drmActive ? "drm" : "sf");
    pushConfigOverrides();
    const int result = gCore.run(rom.c_str());
    gExitRequested = true;
    perfThread.join();
    hudThread.join();
    ALOGI("core returned %d", result);

    input.stop();
    if (sOverlayReady) overlay.shutdown();
    if (sHudReady) hud.shutdown();
    gCore.set_windows(nullptr, nullptr);
    if (drmActive) drm.teardown();
    else sf.teardown();
    azahar_nano::settings::flush();
    sessionDone();
    // Leave without running static destructors: a library thread can still be running at
    // this point (the core's audio backend, log and worker threads), and destroying a
    // still-joinable std::thread during exit() aborts the process (tombstones after
    // "save state then quit"). Everything that matters is already torn down above.
    fflush(nullptr);
    _exit(0);
}
