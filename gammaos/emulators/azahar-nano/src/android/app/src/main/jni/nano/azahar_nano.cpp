// azahar-nano core: the Azahar Android native layer without the JVM.
// See azahar_nano.h. This is native.cpp's run path with the JNI entry points and
// Java callbacks replaced by the C ABI, and the Android-only applets, camera
// and multiplayer pieces replaced by the core defaults.

#include <atomic>
#include <condition_variable>
#include <memory>
#include <mutex>
#include <fstream>
#include <chrono>
#include <string>

#include <android/log.h>
#include <android/native_window.h>
#include <dlfcn.h>

#include "common/android_utils.h"
#include "common/arch.h"
#include "common/common_paths.h"
#include "common/dynamic_library/dynamic_library.h"
#include "common/file_util.h"
#include "common/logging/backend.h"
#include "common/logging/log.h"
#include "common/microprofile.h"
#include "common/scope_exit.h"
#include "common/settings.h"
#include "core/core.h"
#include "core/frontend/applets/default_applets.h"
#include "core/hle/service/am/am.h"
#include "core/loader/loader.h"
#include "core/savestate.h"
#include "common/thread_worker.h"
#include "jni/config.h"
#include "jni/emu_window/emu_window.h"
#include "jni/input_manager.h"
#include "video_core/gpu.h"
#include "video_core/renderer_base.h"
#include "video_core/rasterizer_interface.h"

#if defined(ENABLE_VULKAN)
#include "jni/emu_window/emu_window_vk.h"
#if CITRA_ARCH(arm64)
#include <adrenotools/driver.h>
#endif
#endif
#if defined(ENABLE_OPENGL)
#include "jni/emu_window/emu_window_gl.h"
#endif

#include "jni/nano/azahar_nano.h"

#define AZN_TAG "azahar-nano-core"

namespace {

azn_callbacks g_cb{};
std::shared_ptr<Common::DynamicLibrary> vulkan_library{};
std::unique_ptr<EmuWindow_Android> window;
std::unique_ptr<EmuWindow_Android> secondary_window;

ANativeWindow* s_surface{};
ANativeWindow* s_secondary_surface{};

std::atomic<bool> stop_run{true};
std::atomic<bool> pause_emulation{false};
std::mutex paused_mutex;
std::mutex running_mutex;
std::condition_variable running_cv;
std::recursive_mutex surface_mutex;
std::condition_variable_any surface_cv;

int ToAznResult(Core::System::ResultStatus r) {
    switch (r) {
    case Core::System::ResultStatus::Success:
        return AZN_OK;
    case Core::System::ResultStatus::ErrorLoader:
        return AZN_ERROR_LOADER;
    case Core::System::ResultStatus::ErrorSystemFiles:
        return AZN_ERROR_SYSTEM_FILES;
    case Core::System::ResultStatus::ErrorSavestate:
        return AZN_ERROR_SAVESTATE;
    case Core::System::ResultStatus::ShutdownRequested:
        return AZN_SHUTDOWN_REQUESTED;
    default:
        return AZN_ERROR_OTHER;
    }
}

bool CheckMicPermission() {
    return g_cb.on_mic_permission ? g_cb.on_mic_permission(g_cb.user) : true;
}

bool HandleCoreError(Core::System::ResultStatus result, const std::string& details) {
    __android_log_print(ANDROID_LOG_ERROR, AZN_TAG, "core error %d: %s",
                        static_cast<int>(result), details.c_str());
    if (g_cb.on_core_error) {
        return g_cb.on_core_error(g_cb.user, ToAznResult(result), details.c_str());
    }
    return false;
}

void LoadDiskCacheProgress(VideoCore::LoadCallbackStage stage, int progress, int max,
                           const std::string& object) {
    if (g_cb.on_load_progress) {
        g_cb.on_load_progress(g_cb.user, static_cast<int>(stage), progress, max);
    }
}

void TryShutdown() {
    std::scoped_lock surface_lock(surface_mutex);
    if (!window) {
        return;
    }
    window->DoneCurrent();
    if (secondary_window) {
        secondary_window->DoneCurrent();
    }
    Core::System& system{Core::System::GetInstance()};
    system.Shutdown();
    system.EjectCartridge();
    window.reset();
    if (secondary_window) {
        secondary_window.reset();
    }
    InputManager::Shutdown();
    MicroProfileShutdown();
}

static int s_upright_override = -1;
static int s_vsync_override = -1;
static int s_swapchain_extra = 1;
static bool s_deadline_pacing = false;
static double s_pace_scale = 1.0;

static void ApplyFrontendOverrides() {
    if (s_upright_override >= 0) {
        Settings::values.upright_screen.SetGlobal(true);
        Settings::values.upright_screen.SetValue(s_upright_override != 0);
        // The override describes the primary panel only; the secondary panel is
        // handed to the core in its own natural orientation.
        Settings::values.upright_primary_only = true;
    }
    if (s_vsync_override >= 0) {
        Settings::values.use_vsync.SetGlobal(true);
        Settings::values.use_vsync.SetValue(s_vsync_override != 0);
    }
    Settings::values.swapchain_extra_images = s_swapchain_extra;
    Settings::values.deadline_frame_pacing = s_deadline_pacing;
    Settings::values.deadline_pace_scale = s_pace_scale;
}

static void LoadConfig() {
    Config{};
    ApplyFrontendOverrides();
}


Core::System::ResultStatus RunCore(const std::string& filepath) {
    std::scoped_lock lock(running_mutex);
    LOG_INFO(Frontend, "azahar-nano core starting...");
    MicroProfileOnThreadCreate("EmuThread");

    if (filepath.empty()) {
        LOG_CRITICAL(Frontend, "No ROM specified");
        return Core::System::ResultStatus::ErrorLoader;
    }

    Core::System& system{Core::System::GetInstance()};

    std::unique_lock<std::recursive_mutex> surface_lock(surface_mutex);
    system.RegisterOnInitCallback([](bool init_start) {
        if (init_start) {
            surface_mutex.lock();
            if (!s_surface) {
                std::unique_lock<std::recursive_mutex> wait_lock(surface_mutex, std::adopt_lock);
                surface_cv.wait(wait_lock, [] { return s_surface != nullptr; });
                wait_lock.release();
            }
        } else {
            surface_mutex.unlock();
        }
    });

    const auto graphics_api = Settings::GetWorkingGraphicsAPI();
    switch (graphics_api) {
#ifdef ENABLE_OPENGL
    case Settings::GraphicsAPI::OpenGL: {
        window = std::make_unique<EmuWindow_Android_OpenGL>(system, s_surface, false);
        EGLContext* shared_context = window->GetEGLContext();
        secondary_window = std::make_unique<EmuWindow_Android_OpenGL>(system, s_secondary_surface,
                                                                      true, shared_context);
        break;
    }
#endif
#ifdef ENABLE_VULKAN
    case Settings::GraphicsAPI::Vulkan:
        window = std::make_unique<EmuWindow_Android_Vulkan>(s_surface, vulkan_library, false);
        secondary_window =
            std::make_unique<EmuWindow_Android_Vulkan>(s_secondary_surface, vulkan_library, true);
        break;
#endif
    default:
        LOG_CRITICAL(Frontend, "Unsupported graphics API {}, using the default", graphics_api);
#ifdef ENABLE_VULKAN
        window = std::make_unique<EmuWindow_Android_Vulkan>(s_surface, vulkan_library, false);
        secondary_window =
            std::make_unique<EmuWindow_Android_Vulkan>(s_secondary_surface, vulkan_library, true);
#else
        window = std::make_unique<EmuWindow_Android_OpenGL>(system, s_surface, false);
        secondary_window = std::make_unique<EmuWindow_Android_OpenGL>(
            system, s_secondary_surface, true, window->GetEGLContext());
#endif
        break;
    }

    // Reload config.ini on every boot, then the per-game settings.
    LoadConfig();
    u64 program_id{};
    FileUtil::SetCurrentRomPath(filepath);
    auto app_loader = Loader::GetLoader(filepath);
    if (app_loader) {
        app_loader->ReadProgramId(program_id);
        system.RegisterAppLoaderEarly(app_loader);
    }
    system.ApplySettings();
    Settings::LogSettings();

    Frontend::RegisterDefaultApplets(system);
    system.RegisterMicPermissionCheck(&CheckMicPermission);


    InputManager::Init();
    window->MakeCurrent();

    const Core::System::ResultStatus load_result{
        system.Load(*window, filepath, secondary_window.get())};
    surface_lock.unlock();
    if (load_result != Core::System::ResultStatus::Success) {
        return load_result;
    }

    stop_run = false;
    pause_emulation = false;

    LoadDiskCacheProgress(VideoCore::LoadCallbackStage::Prepare, 0, 0, "");
    system.GPU().ApplyPerProgramSettings(program_id);
    system.GPU().Renderer().Rasterizer()->LoadDefaultDiskResources(stop_run,
                                                                   &LoadDiskCacheProgress);
    LoadDiskCacheProgress(VideoCore::LoadCallbackStage::Complete, 0, 0, "");
    system.GammaJitWarm();

    SCOPE_EXIT({ TryShutdown(); });
    SCOPE_EXIT({ system.GammaJitSave(); });
    auto last_jit_save = std::chrono::steady_clock::now();
    system.RegisterCoreLoopThreadId();

    while (!stop_run) {
        if (!pause_emulation) {
            const auto result = system.RunLoop();
            if (result == Core::System::ResultStatus::Success) {
                // Keep the JIT warm-up lists current (a killed session keeps them).
                if (std::chrono::steady_clock::now() - last_jit_save > std::chrono::seconds(30)) {
                    last_jit_save = std::chrono::steady_clock::now();
                    system.GammaJitSave();
                }
                continue;
            }
            if (result == Core::System::ResultStatus::ShutdownRequested) {
                return result;
            }
            if (!HandleCoreError(result, system.GetStatusDetails())) {
                return Core::System::ResultStatus::ShutdownRequested;
            }
        } else {
            const float volume = Settings::values.volume.GetValue();
            SCOPE_EXIT({ Settings::values.volume = volume; });
            Settings::values.volume = 0;
            std::unique_lock pause_lock{paused_mutex};
            running_cv.wait(pause_lock, [] { return !pause_emulation || stop_run; });
            window->PollEvents();
        }
    }
    return Core::System::ResultStatus::Success;
}

} // namespace

extern "C" {

int azn_abi_version(void) {
    return AZN_ABI_VERSION;
}

int azn_init(const char* user_dir, const struct azn_callbacks* callbacks) {
    if (callbacks) {
        g_cb = *callbacks;
    }
    if (!user_dir || !*user_dir) {
        return AZN_ERROR_OTHER;
    }
    std::string dir = user_dir;
    if (dir.back() != '/') {
        dir += '/';
    }
    AndroidUtils::SetNativeUserDirectory(dir);
    FileUtil::SetCurrentDir(dir);
    // The user directory is also where config.ini lives (ConfigDir).
    Common::Log::Initialize();
    Common::Log::Start();
    // The app loads config.ini at startup through JNI; here it happens before the
    // windows are created so their layouts come from the user's settings and not
    // from the defaults (the run path reloads it again on boot).
    Config{};
    __android_log_print(ANDROID_LOG_INFO, AZN_TAG, "user dir %s", dir.c_str());
    return AZN_OK;
}

void azn_shutdown_library(void) {
    azn_stop();
}

void azn_set_gpu_driver(const char* hook_lib_dir, const char* custom_driver_dir,
                        const char* custom_driver_name, const char* file_redirect_dir) {
#if defined(ENABLE_VULKAN) && CITRA_ARCH(arm64)
    void* handle{};
    const char* redirect{};
    int flags{};
    if (file_redirect_dir && *file_redirect_dir && Settings::values.renderer_debug) {
        flags |= ADRENOTOOLS_DRIVER_FILE_REDIRECT;
        redirect = file_redirect_dir;
    }
    const char* hook = hook_lib_dir ? hook_lib_dir : "";
    if (custom_driver_name && *custom_driver_name && custom_driver_dir && *custom_driver_dir) {
        handle = adrenotools_open_libvulkan(RTLD_NOW, flags | ADRENOTOOLS_DRIVER_CUSTOM, nullptr,
                                            hook, custom_driver_dir, custom_driver_name, redirect,
                                            nullptr);
        __android_log_print(ANDROID_LOG_INFO, AZN_TAG, "custom driver %s/%s -> %p",
                            custom_driver_dir, custom_driver_name, handle);
    }
    if (!handle) {
        handle = adrenotools_open_libvulkan(RTLD_NOW, flags, nullptr, hook, nullptr, nullptr,
                                            redirect, nullptr);
        __android_log_print(ANDROID_LOG_INFO, AZN_TAG, "system driver -> %p", handle);
    }
    vulkan_library = std::make_shared<Common::DynamicLibrary>(handle);
#else
    (void)hook_lib_dir;
    (void)custom_driver_dir;
    (void)custom_driver_name;
    (void)file_redirect_dir;
#endif
}

// Frontend overrides applied on top of config.ini after every load: the DRM
// backend hands the core a portrait window for a panel that is physically
// landscape (install rotation), so the primary layout is rendered upright.
void azn_set_upright(int upright) {
    s_upright_override = upright;
    ApplyFrontendOverrides();
}

// Relocatable user data: saves (sdmc), save states, cheats, dumps and mods (load)
// live in the user folder; config, nand, system files, shader cache and logs stay
// in the private root given to azn_init.
void azn_set_user_folder(const char* dir) {
    if (!dir || !*dir) return;
    // The file layer resolves a bare path under the private user directory; a '!'
    // prefix marks an already-native absolute path (same rule as the ROM path).
    // Without it /sdcard/... became <private>/sdcard/... and saves went missing.
    std::string base = std::string(dir);
    while (base.size() > 1 && base.back() == '/') base.pop_back();
    base += "/";
    if (base[0] == '/') base = "!" + base;
    const std::pair<FileUtil::UserPath, const char*> sub[] = {
        {FileUtil::UserPath::SDMCDir, SDMC_DIR},    {FileUtil::UserPath::StatesDir, STATES_DIR},
        {FileUtil::UserPath::CheatsDir, CHEATS_DIR}, {FileUtil::UserPath::DumpDir, DUMP_DIR},
        {FileUtil::UserPath::LoadDir, LOAD_DIR},
    };
    FileUtil::CreateFullPath(base);
    for (const auto& [path, name] : sub) {
        const std::string full = base + name + "/";
        FileUtil::CreateFullPath(full);
        FileUtil::UpdateUserPath(path, full);
    }
    __android_log_print(ANDROID_LOG_INFO, AZN_TAG, "user folder %s", dir);
}

void azn_set_present_mode(int vsync, int swapchain_extra_images) {
    s_vsync_override = vsync;
    s_swapchain_extra = swapchain_extra_images;
    ApplyFrontendOverrides();
}

void azn_set_deadline_pacing(int enabled) {
    s_deadline_pacing = enabled != 0;
    ApplyFrontendOverrides();
}

void azn_set_tweaks(int enabled, int vertex_depth) {
    Settings::values.gammaos_vertex_depth = enabled != 0 && vertex_depth != 0;
    __android_log_print(ANDROID_LOG_INFO, AZN_TAG, "GammaOS tweaks %s (vertex depth %s)",
                        enabled ? "on" : "off",
                        Settings::values.gammaos_vertex_depth ? "on" : "off");
}

void azn_skip_preload() {
    Settings::values.gammaos_skip_preload = true;
}

int azn_compiles_pending() {
    return std::max(0, Common::g_compile_jobs.load(std::memory_order_relaxed));
}

void azn_set_tweak(const char* name, int value) {
    if (!name) return;
    if (std::string_view{name} == "renderpass_clears") {
        Settings::values.gammaos_renderpass_clears = value != 0;
    } else if (std::string_view{name} == "vd_keep_fs_depth") {
        Settings::values.gammaos_vd_keep_fs_depth = value != 0;
    } else if (std::string_view{name} == "jit_warmup") {
        Settings::values.gammaos_jit_warmup = value != 0;
    } else if (std::string_view{name} == "hw_scissor") {
        Settings::values.gammaos_hw_scissor = value != 0;
    } else if (std::string_view{name} == "fold_clears") {
        Settings::values.gammaos_fold_clears = value != 0;
    } else if (std::string_view{name} == "hybrid_shaders") {
        Settings::values.gammaos_hybrid_shaders = value != 0;
    } else if (std::string_view{name} == "preload_wait") {
        Settings::values.gammaos_preload_wait = value != 0;
    } else if (std::string_view{name} == "fs_mediump") {
        Settings::values.gammaos_fs_mediump = value != 0;
    } else if (std::string_view{name} == "vertex_depth") {
        Settings::values.gammaos_vertex_depth = value != 0;
    } else {
        __android_log_print(ANDROID_LOG_WARN, AZN_TAG, "unknown tweak %s", name);
        return;
    }
    __android_log_print(ANDROID_LOG_INFO, AZN_TAG, "tweak %s = %d", name, value);
}

void azn_set_pace_scale(double scale) {
    s_pace_scale = (scale > 0.9 && scale < 1.1) ? scale : 1.0;
    ApplyFrontendOverrides();
}

void azn_set_windows(struct ANativeWindow* primary, struct ANativeWindow* secondary) {
    std::scoped_lock lock(surface_mutex);
    auto& system = Core::System::GetInstance();

    if (s_surface != primary) {
        if (s_surface) {
            ANativeWindow_release(s_surface);
        }
        s_surface = primary;
        if (s_surface) {
            ANativeWindow_acquire(s_surface);
            surface_cv.notify_all();
            if (window && window->OnSurfaceChanged(s_surface) && system.IsPoweredOn()) {
                system.GPU().Renderer().NotifySurfaceChanged(false);
            }
        }
    }
    if (s_secondary_surface != secondary) {
        if (s_secondary_surface) {
            ANativeWindow_release(s_secondary_surface);
        }
        s_secondary_surface = secondary;
        if (s_secondary_surface) {
            ANativeWindow_acquire(s_secondary_surface);
            if (secondary_window && secondary_window->OnSurfaceChanged(s_secondary_surface) &&
                system.IsPoweredOn()) {
                system.GPU().Renderer().NotifySurfaceChanged(true);
            }
        }
    }
}

int azn_run(const char* rom_path) {
    if (!stop_run) {
        stop_run = true;
        running_cv.notify_all();
    }
    // The Android file layer treats a bare path as relative to the user directory
    // and a '!' prefix as an already-native absolute path.
    std::string path = rom_path ? rom_path : "";
    if (!path.empty() && path[0] == '/') {
        path = "!" + path;
    }
    const auto result = RunCore(path);
    if (result != Core::System::ResultStatus::Success && g_cb.on_shutdown) {
        g_cb.on_shutdown(g_cb.user, ToAznResult(result));
    }
    return ToAznResult(result);
}

void azn_stop(void) {
    if (stop_run.exchange(true)) {
        return;
    }
    pause_emulation = false;
    if (window) {
        window->StopPresenting();
    }
    if (secondary_window) {
        secondary_window->StopPresenting();
    }
    running_cv.notify_all();
}

void azn_pause(bool paused) {
    pause_emulation = paused;
    if (!paused) {
        running_cv.notify_all();
    }
}

bool azn_is_running(void) {
    return !stop_run;
}

bool azn_is_paused(void) {
    return pause_emulation;
}

// Minimal config.ini writer for the frontend's overlay menu: replaces or appends
// "key = value" inside [section] (the Android app writes the file from Java, so
// the native layer has no writer of its own). Keeps every other line as is.
int azn_set_config_value(const char* section, const char* key, const char* value) {
    if (!section || !key || !value) return AZN_ERROR_OTHER;
    // Paths from GetUserPath are the core's file-layer paths (relative to the user
    // directory on Android), so the file goes through FileUtil like Config does.
    const std::string path =
        FileUtil::GetUserPath(FileUtil::UserPath::ConfigDir) + "config.ini";
    std::vector<std::string> lines;
    {
        std::string buffer;
        FileUtil::ReadFileToString(true, path, buffer);
        std::string line;
        for (char c : buffer) {
            if (c == '\n') { lines.push_back(line); line.clear(); }
            else if (c != '\r') line.push_back(c);
        }
        if (!line.empty()) lines.push_back(line);
    }
    const std::string header = std::string("[") + section + "]";
    const std::string prefix = std::string(key) + " ";
    const std::string entry = std::string(key) + " = " + value;
    size_t sec_begin = lines.size(), sec_end = lines.size();
    for (size_t i = 0; i < lines.size(); i++) {
        if (lines[i] == header) {
            sec_begin = i + 1;
            sec_end = lines.size();
            for (size_t j = sec_begin; j < lines.size(); j++) {
                if (!lines[j].empty() && lines[j][0] == '[') { sec_end = j; break; }
            }
            break;
        }
    }
    bool written = false;
    if (sec_begin < lines.size()) {
        for (size_t i = sec_begin; i < sec_end; i++) {
            const std::string& l = lines[i];
            if (l.rfind(prefix, 0) == 0 || l.rfind(std::string(key) + "=", 0) == 0) {
                lines[i] = entry;
                written = true;
                break;
            }
        }
        if (!written) {
            lines.insert(lines.begin() + sec_end, entry);
            written = true;
        }
    } else {
        lines.push_back(header);
        lines.push_back(entry);
    }
    std::string out;
    for (const auto& l : lines) { out += l; out += '\n'; }
    FileUtil::IOFile file(path, "w");
    if (!file.IsOpen()) {
        __android_log_print(ANDROID_LOG_ERROR, AZN_TAG, "config writer: cannot open %s", path.c_str());
        return AZN_ERROR_OTHER;
    }
    const bool ok = file.WriteBytes(out.data(), out.size()) == out.size();
    file.Close();
    __android_log_print(ok ? ANDROID_LOG_INFO : ANDROID_LOG_ERROR, AZN_TAG,
                        "config writer: [%s] %s = %s (%zu lines, %s) -> %s%s", section, key, value,
                        lines.size(), written ? "replaced" : "appended", path.c_str(),
                        ok ? "" : " WRITE FAILED");
    return ok ? AZN_OK : AZN_ERROR_OTHER;
}

void azn_reload_settings(void) {
    LoadConfig();
    Core::System& system{Core::System::GetInstance()};
    system.ApplySettings();
}

bool azn_button(int button, bool pressed) {
    auto* h = InputManager::ButtonHandler();
    if (!h) {
        return false;
    }
    return pressed ? h->PressKey(button) : h->ReleaseKey(button);
}

bool azn_joystick(int axis_button, float x, float y) {
    auto* h = InputManager::AnalogHandler();
    return h ? h->MoveJoystick(axis_button, x, y) : false;
}

bool azn_analog_button(int axis_button, float value) {
    auto* h = InputManager::ButtonHandler();
    return h ? h->AnalogButtonEvent(axis_button, value) : false;
}

bool azn_touch(float x, float y, bool pressed) {
    return window ? window->OnTouchEvent(static_cast<int>(x + 0.5f),
                                         static_cast<int>(y + 0.5f), pressed)
                  : false;
}

void azn_touch_moved(float x, float y) {
    if (window) {
        window->OnTouchMoved(static_cast<int>(x), static_cast<int>(y));
    }
}

bool azn_secondary_touch(float x, float y, bool pressed) {
    return secondary_window ? secondary_window->OnTouchEvent(static_cast<int>(x + 0.5f),
                                                             static_cast<int>(y + 0.5f), pressed)
                            : false;
}

void azn_secondary_touch_moved(float x, float y) {
    if (secondary_window) {
        secondary_window->OnTouchMoved(static_cast<int>(x), static_cast<int>(y));
    }
}

void azn_save_state(int slot) {
    Core::System::GetInstance().SendSignal(Core::System::Signal::Save, slot);
}

void azn_load_state(int slot) {
    const bool ok = Core::System::GetInstance().SendSignal(Core::System::Signal::Load, slot);
    __android_log_print(ANDROID_LOG_INFO, AZN_TAG, "load state %d: signal %s", slot,
                        ok ? "queued" : "refused (another operation pending)");
}

void azn_get_perf_stats(struct azn_perf_stats* out) {
    if (!out) {
        return;
    }
    *out = {};
    auto& core = Core::System::GetInstance();
    if (!core.IsPoweredOn()) {
        return;
    }
    auto r = core.GetAndResetPerfStats();
    out->system_fps = r.system_fps;
    out->game_fps = r.game_fps;
    out->emulation_speed = r.emulation_speed;
    out->time_vblank_interval = r.time_vblank_interval;
    out->time_hle_svc = r.time_hle_svc;
    out->time_hle_ipc = r.time_hle_ipc;
    out->time_gpu = r.time_gpu;
    out->time_swap = r.time_swap;
    out->time_remaining = r.time_remaining;
}

uint64_t azn_running_title_id(void) {
    u64 id{};
    auto& core = Core::System::GetInstance();
    if (core.IsPoweredOn()) {
        core.GetAppLoader().ReadProgramId(id);
    }
    return id;
}

void azn_set_temporary_frame_limit(int percent) {
    Settings::temporary_frame_limit = percent;
    Settings::is_temporary_frame_limit = true;
}

void azn_disable_temporary_frame_limit(void) {
    Settings::is_temporary_frame_limit = false;
}

// GammaOS fast forward: unlimited speed, 1x internal resolution with linear screen filtering
// (the user's resolution and filter come back when it is turned off), and presents limited to
// about one per display period so vsync does not cap the speed.
void azn_set_fast_forward(int on) {
    static bool active = false;
    static u32 saved_resolution = 1;
    static bool saved_filter = true;
    if (on && !active) {
        saved_resolution = Settings::values.resolution_factor.GetValue();
        saved_filter = Settings::values.filter_mode.GetValue();
        Settings::values.resolution_factor.SetValue(1);
        Settings::values.filter_mode.SetValue(true);
        Settings::temporary_frame_limit = 0;
        Settings::is_temporary_frame_limit = true;
        Settings::values.gammaos_fast_forward = true;
        active = true;
    } else if (!on && active) {
        Settings::values.gammaos_fast_forward = false;
        Settings::is_temporary_frame_limit = false;
        Settings::values.resolution_factor.SetValue(saved_resolution);
        Settings::values.filter_mode.SetValue(saved_filter);
        active = false;
    }
    __android_log_print(ANDROID_LOG_INFO, AZN_TAG, "fast forward %s", active ? "on" : "off");
}

} // extern "C"
