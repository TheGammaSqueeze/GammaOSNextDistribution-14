// azahar-nano core C ABI.
//
// A JNI-free entry point into the Azahar 3DS core for the GammaOS Nano frontend
// (frameworks/base/cmds/azahar-nano). The frontend dlopens libazahar_nano_core.so,
// hands it two ANativeWindows (top screen, bottom screen) that it owns, drives
// input, and runs the emulation loop on its own thread. Everything else (user
// directory, config.ini, GPU driver, disk shader cache) works exactly as it does
// in the Azahar Android app, because this is the app's native layer with the
// JVM callbacks replaced by plain function pointers.

#pragma once

#include <stdbool.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

struct ANativeWindow;

#define AZN_ABI_VERSION 1

// Mirrors Core::System::ResultStatus (only the values the frontend acts on).
enum azn_result {
    AZN_OK = 0,
    AZN_ERROR_LOADER = 1,
    AZN_ERROR_SYSTEM_FILES = 2,
    AZN_ERROR_SAVESTATE = 3,
    AZN_SHUTDOWN_REQUESTED = 4,
    AZN_ERROR_OTHER = 100,
};

// Mirrors VideoCore::LoadCallbackStage.
enum azn_load_stage {
    AZN_LOAD_PREPARE = 0,
    AZN_LOAD_DECOMPILE = 1,
    AZN_LOAD_BUILD = 2,
    AZN_LOAD_COMPLETE = 3,
};

// Button ids are the Azahar Android InputManager::ButtonType values.
enum azn_button {
    AZN_BUTTON_A = 700,
    AZN_BUTTON_B = 701,
    AZN_BUTTON_X = 702,
    AZN_BUTTON_Y = 703,
    AZN_BUTTON_START = 704,
    AZN_BUTTON_SELECT = 705,
    AZN_BUTTON_HOME = 706,
    AZN_BUTTON_ZL = 707,
    AZN_BUTTON_ZR = 708,
    AZN_DPAD_UP = 709,
    AZN_DPAD_DOWN = 710,
    AZN_DPAD_LEFT = 711,
    AZN_DPAD_RIGHT = 712,
    AZN_CIRCLEPAD = 713,
    AZN_STICK_C = 718,
    AZN_TRIGGER_L = 773,
    AZN_TRIGGER_R = 774,
};

struct azn_perf_stats {
    double system_fps;
    double game_fps;
    double emulation_speed;
    double time_vblank_interval;
    double time_hle_svc;
    double time_hle_ipc;
    double time_gpu;
    double time_swap;
    double time_remaining;
};

struct azn_callbacks {
    void* user;
    // Return true to keep running after a recoverable core error, false to stop.
    bool (*on_core_error)(void* user, int result, const char* details);
    // Disk shader cache progress (prepare/decompile/build/complete).
    void (*on_load_progress)(void* user, int stage, int progress, int max);
    // The core asked for the session to end (HOME menu exit, fatal error).
    void (*on_shutdown)(void* user, int result);
    // Microphone permission query; return true to allow.
    bool (*on_mic_permission)(void* user);
    // Log line from the core (level 0..5 trace..critical).
    void (*on_log)(void* user, int level, const char* cls, const char* msg);
};

// Must be the first call. user_dir is the Azahar user directory (config/, nand/,
// sdmc/, sysdata/, shaders/, gpu_drivers/ live under it).
int azn_abi_version(void);
int azn_init(const char* user_dir, const struct azn_callbacks* callbacks);
void azn_shutdown_library(void);

// Vulkan driver selection, call before azn_run. hook_lib_dir holds the
// libadrenotools hook libraries; custom_driver_dir/name select an unpacked
// adrenotools driver (meta.json libraryName), empty name = system driver.
void azn_set_gpu_driver(const char* hook_lib_dir, const char* custom_driver_dir,
                        const char* custom_driver_name, const char* file_redirect_dir);

// Windows for the two screens. The frontend keeps ownership; the core takes a
// reference while it renders. secondary may be NULL (single window layout).
// May be called again while running to swap surfaces.
void azn_set_windows(struct ANativeWindow* primary, struct ANativeWindow* secondary);

// Layout override: -1 = follow config.ini, 0/1 = force upright_screen off/on.
// Set before azn_run; the DRM backend uses it for a portrait primary panel.
void azn_set_upright(int upright);

// Presentation override: vsync -1 = follow config.ini, 0/1 = force the FIFO
// (vsync) swapchain off/on; swapchain_extra_images = images above the surface
// minimum (1 = stock; the DRM backend tunes the present queue depth with it).
void azn_set_present_mode(int vsync, int swapchain_extra_images);

// Relocatable user data folder (saves, save states, cheats, dumps, mods); the
// private root from azn_init keeps config, nand, system files and caches. Call
// after azn_init and before azn_run; the subfolders are created.
void azn_set_user_folder(const char* dir);

// Frame limiter mode: 1 = pace every emulated frame onto a fixed wall-clock
// schedule (even swap cadence for a scanout presenter), 0 = stock limiter.
void azn_set_deadline_pacing(int enabled);
// Deadline pacing period scale = panel period / emulated period (1.0 nominal), so
// the swap cadence matches a panel that is not exactly 60 Hz.
void azn_set_pace_scale(double scale);
// GammaOS renderer tweaks; vertex_depth: Z-buffered depth from the vertex stage
// (no measured gain on Super Mario 3D Land, off by default). Call before azn_run.
void azn_set_tweaks(int enabled, int vertex_depth);
// Individual GammaOS renderer tweak by name ("renderpass_clears"), 0/1.
void azn_set_tweak(const char* name, int value);

// Blocking: boots the ROM and runs the emulation loop on the calling thread
// until azn_stop or a shutdown request. Returns an azn_result.
int azn_run(const char* rom_path);
void azn_stop(void);
void azn_pause(bool paused);
bool azn_is_running(void);
bool azn_is_paused(void);

// Re-read config.ini and apply (video/audio settings changed by the overlay).
void azn_reload_settings(void);

// Write one "key = value" into [section] of the user's config.ini (replace or
// append), for the overlay menu. Returns an azn_result.
int azn_set_config_value(const char* section, const char* key, const char* value);

// Input. Returns true when the core consumed the event.
bool azn_button(int button, bool pressed);
bool azn_joystick(int axis_button, float x, float y);
bool azn_analog_button(int axis_button, float value);
bool azn_touch(float x, float y, bool pressed);
void azn_touch_moved(float x, float y);
bool azn_secondary_touch(float x, float y, bool pressed);
void azn_secondary_touch_moved(float x, float y);

// Save states (asynchronous, handled on the core thread).
void azn_save_state(int slot);
void azn_load_state(int slot);

void azn_get_perf_stats(struct azn_perf_stats* out);
uint64_t azn_running_title_id(void);

// Temporary frame limit override for fast forward (percent, 0 = unlimited).
void azn_set_temporary_frame_limit(int percent);
void azn_disable_temporary_frame_limit(void);

#ifdef __cplusplus
}
#endif
