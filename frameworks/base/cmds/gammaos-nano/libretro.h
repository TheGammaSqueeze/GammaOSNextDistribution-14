/* Minimal libretro API header — stable C ABI for emulator cores.
 * Extracted from libretro/libretro.h (public domain).
 * Only the types and defines needed by LibretroRunner. */

#ifndef LIBRETRO_H__
#define LIBRETRO_H__

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>
#include <limits.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Pixel formats */
#define RETRO_PIXEL_FORMAT_0RGB1555  0
#define RETRO_PIXEL_FORMAT_XRGB8888 1
#define RETRO_PIXEL_FORMAT_RGB565   2

enum retro_pixel_format {
    RETRO_PIXEL_FORMAT_0RGB1555_ENUM = 0,
    RETRO_PIXEL_FORMAT_XRGB8888_ENUM = 1,
    RETRO_PIXEL_FORMAT_RGB565_ENUM = 2
};

/* Environment commands */
#define RETRO_ENVIRONMENT_SET_PIXEL_FORMAT     10
#define RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY 9
#define RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY   31
#define RETRO_ENVIRONMENT_GET_LOG_INTERFACE    27
#define RETRO_ENVIRONMENT_SET_GEOMETRY         37
#define RETRO_ENVIRONMENT_GET_VARIABLE         15
#define RETRO_ENVIRONMENT_SET_VARIABLES        16
#define RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE  17
#define RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME  18
#define RETRO_ENVIRONMENT_GET_LANGUAGE         39
#define RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS 11

/* Input device types */
#define RETRO_DEVICE_NONE    0
#define RETRO_DEVICE_JOYPAD  1
#define RETRO_DEVICE_ANALOG  5

/* Joypad buttons */
#define RETRO_DEVICE_ID_JOYPAD_B      0
#define RETRO_DEVICE_ID_JOYPAD_Y      1
#define RETRO_DEVICE_ID_JOYPAD_SELECT 2
#define RETRO_DEVICE_ID_JOYPAD_START  3
#define RETRO_DEVICE_ID_JOYPAD_UP     4
#define RETRO_DEVICE_ID_JOYPAD_DOWN   5
#define RETRO_DEVICE_ID_JOYPAD_LEFT   6
#define RETRO_DEVICE_ID_JOYPAD_RIGHT  7
#define RETRO_DEVICE_ID_JOYPAD_A      8
#define RETRO_DEVICE_ID_JOYPAD_X      9
#define RETRO_DEVICE_ID_JOYPAD_L     10
#define RETRO_DEVICE_ID_JOYPAD_R     11
#define RETRO_DEVICE_ID_JOYPAD_L2    12
#define RETRO_DEVICE_ID_JOYPAD_R2    13
#define RETRO_DEVICE_ID_JOYPAD_L3    14
#define RETRO_DEVICE_ID_JOYPAD_R3    15

/* Analog indices */
#define RETRO_DEVICE_INDEX_ANALOG_LEFT  0
#define RETRO_DEVICE_INDEX_ANALOG_RIGHT 1
#define RETRO_DEVICE_ID_ANALOG_X 0
#define RETRO_DEVICE_ID_ANALOG_Y 1

/* Memory types */
#define RETRO_MEMORY_SAVE_RAM  0
#define RETRO_MEMORY_RTC       1
#define RETRO_MEMORY_SYSTEM_RAM 2

/* Log levels */
enum retro_log_level {
    RETRO_LOG_DEBUG = 0,
    RETRO_LOG_INFO,
    RETRO_LOG_WARN,
    RETRO_LOG_ERROR,
    RETRO_LOG_DUMMY = INT_MAX
};

/* Language */
#define RETRO_LANGUAGE_ENGLISH 0

/* Callback typedefs */
typedef bool (*retro_environment_t)(unsigned cmd, void* data);
typedef void (*retro_video_refresh_t)(const void* data, unsigned width,
                                       unsigned height, size_t pitch);
typedef void (*retro_audio_sample_t)(int16_t left, int16_t right);
typedef size_t (*retro_audio_sample_batch_t)(const int16_t* data, size_t frames);
typedef void (*retro_input_poll_t)(void);
typedef int16_t (*retro_input_state_t)(unsigned port, unsigned device,
                                        unsigned index, unsigned id);

/* Log callback struct */
struct retro_log_callback {
    void (*log)(enum retro_log_level level, const char* fmt, ...);
};

/* Variable struct */
struct retro_variable {
    const char* key;
    const char* value;
};

/* Game info */
struct retro_game_info {
    const char* path;
    const void* data;
    size_t size;
    const char* meta;
};

/* System info */
struct retro_system_info {
    const char* library_name;
    const char* library_version;
    const char* valid_extensions;
    bool need_fullpath;
    bool block_extract;
};

/* Game geometry */
struct retro_game_geometry {
    unsigned base_width;
    unsigned base_height;
    unsigned max_width;
    unsigned max_height;
    float aspect_ratio;
};

/* System timing */
struct retro_system_timing {
    double fps;
    double sample_rate;
};

/* AV info */
struct retro_system_av_info {
    struct retro_game_geometry geometry;
    struct retro_system_timing timing;
};

/* Input descriptor */
struct retro_input_descriptor {
    unsigned port;
    unsigned device;
    unsigned index;
    unsigned id;
    const char* description;
};

#ifdef __cplusplus
}
#endif

#endif /* LIBRETRO_H__ */
