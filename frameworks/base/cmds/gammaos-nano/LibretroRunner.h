/*
 * Copyright (C) 2026 GammaOS
 *
 * Minimal libretro frontend for NanoMenu quick resume.
 * Runs a libretro core directly in NanoMenu's EGL context,
 * bypassing the Android framework entirely. Hands off to
 * the real RetroArch APK when the framework is ready.
 */

#pragma once

#include <string>
#include <EGL/egl.h>
#include <GLES2/gl2.h>

// libretro API types (stable C ABI)
#include "libretro.h"

class LibretroRunner {
public:
    LibretroRunner();
    ~LibretroRunner();

    // Initialize with paths to core, ROM, and optional save state
    bool init(const std::string& corePath,
              const std::string& romPath,
              const std::string& saveStatePath,
              const std::string& saveRamPath);

    // Set the GL rotation matrix for DRM orientation-aware rendering.
    // Called by NanoMenu before runFrame when DRM direct mode is active.
    void setRotationMatrix(const float mat[4]) {
        for (int i = 0; i < 4; i++) mRotationMatrix[i] = mat[i];
    }

    // Run one frame: processes input, runs core, renders to current GL context
    // saturation: 0.0=grayscale, 1.0=full color
    // gradient: 0.0=no gradient, 1.0=full black gradient at bottom
    bool runFrame(int screenWidth, int screenHeight,
                  float saturation = 1.0f, float gradient = 0.0f);

    // Render the last frame again without running the core (for static display)
    bool renderOnly(int screenWidth, int screenHeight,
                    float saturation = 1.0f, float gradient = 0.0f);

    // Save state to file (for handoff to RetroArch)
    bool saveState(const std::string& path);

    // Save SRAM to file
    bool saveSRAM(const std::string& path);

    // Clean up
    void shutdown();

    // Neuter a core that crashed under the first-frame guard so neither
    // shutdown() nor the destructor re-enters it (deliberate leak).
    void abandon();

    // Set input state (called before runFrame)
    void setButton(unsigned port, unsigned button, bool pressed);
    void setAnalog(unsigned port, unsigned index, unsigned id, int16_t value);

    bool isInitialized() const { return mInitialized; }

    // Try running the first frame with crash guard (catches SIGSEGV from
    // cores that need BIOS/dynarec permissions not available in bootanim domain)
    bool trySafeFirstFrame();

private:
    // libretro callbacks (static, route to singleton)
    static void onVideoRefresh(const void* data, unsigned width,
                               unsigned height, size_t pitch);
    static void onAudioSample(int16_t left, int16_t right);
    static size_t onAudioSampleBatch(const int16_t* data, size_t frames);
    static void onInputPoll();
    static int16_t onInputState(unsigned port, unsigned device,
                                unsigned index, unsigned id);
    static bool onEnvironment(unsigned cmd, void* data);
    static void onLog(enum retro_log_level level, const char* fmt, ...);

    // Video
    void uploadFrame(const void* data, unsigned width,
                     unsigned height, size_t pitch);
    void renderFrame(int screenWidth, int screenHeight,
                     float saturation, float gradient);
    void initVideoGL();

    // Audio
    void initAudio(double sampleRate);
    void shutdownAudio();
    void writeAudio(const int16_t* data, size_t frames);

    // Core handle
    void* mCoreHandle = nullptr;
    bool mInitialized = false;

    // libretro function pointers
    void (*retro_init)() = nullptr;
    void (*retro_deinit)() = nullptr;
    void (*retro_set_environment)(retro_environment_t) = nullptr;
    void (*retro_set_video_refresh)(retro_video_refresh_t) = nullptr;
    void (*retro_set_audio_sample)(retro_audio_sample_t) = nullptr;
    void (*retro_set_audio_sample_batch)(retro_audio_sample_batch_t) = nullptr;
    void (*retro_set_input_poll)(retro_input_poll_t) = nullptr;
    void (*retro_set_input_state)(retro_input_state_t) = nullptr;
    bool (*retro_load_game)(const struct retro_game_info*) = nullptr;
    void (*retro_unload_game)() = nullptr;
    void (*retro_run)() = nullptr;
    void (*retro_get_system_info)(struct retro_system_info*) = nullptr;
    void (*retro_get_system_av_info)(struct retro_system_av_info*) = nullptr;
    size_t (*retro_serialize_size)() = nullptr;
    bool (*retro_serialize)(void*, size_t) = nullptr;
    bool (*retro_unserialize)(const void*, size_t) = nullptr;
    size_t (*retro_get_memory_size)(unsigned) = nullptr;
    void* (*retro_get_memory_data)(unsigned) = nullptr;

    // Video state
    GLuint mFrameTex = 0;
    GLuint mVideoProgram = 0;
    GLint mVideoLocPosition = -1;
    GLint mVideoLocTexCoord = -1;
    GLint mVideoLocTexture = -1;
    GLint mVideoLocSwizzle = -1;
    GLint mVideoLocSaturation = -1;
    GLint mVideoLocGradient = -1;
    GLint mVideoLocRotation = -1;
    float mRotationMatrix[4] = {1.0f, 0.0f, 0.0f, 1.0f}; // identity
    float mAspectRatio = 0.0f;  // from core's retro_system_av_info
    unsigned mFrameWidth = 0;
    unsigned mFrameHeight = 0;
    bool mFrameDirty = false;
    int mPixelFormat = RETRO_PIXEL_FORMAT_0RGB1555;

    // Audio state (null driver — no output)
    double mSampleRate = 44100.0;

    // Input state
    int16_t mButtons[2] = {};  // 2 ports, bitfield per port
    int16_t mAnalog[2][2][2] = {};  // [port][index][id]

    // Singleton for static callbacks
    static LibretroRunner* sInstance;
};
