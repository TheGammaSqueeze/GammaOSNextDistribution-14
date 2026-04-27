/*
 * Copyright (C) 2026 GammaOS
 *
 * Minimal libretro frontend for NanoMenu quick resume.
 * dlopen()s a libretro core, loads a ROM, resumes from save state,
 * and renders frames to the current EGL context.
 */

#define LOG_TAG "GammaOSNano"

#include "LibretroRunner.h"

#include <dlfcn.h>
#include <dirent.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <cstring>
#include <cstdarg>
#include <vector>
#include <setjmp.h>
#include <signal.h>
#include <zlib.h>
#include <utils/Log.h>
#include <GLES2/gl2.h>

LibretroRunner* LibretroRunner::sInstance = nullptr;

// ---- GL shaders for rendering the core's framebuffer ----

static const char* kVideoVS =
    "attribute vec2 aPosition;\n"
    "attribute vec2 aTexCoord;\n"
    "uniform mat2 uRotation;\n"
    "varying vec2 vTexCoord;\n"
    "void main() {\n"
    "    gl_Position = vec4(uRotation * aPosition, 0.0, 1.0);\n"
    "    vTexCoord = aTexCoord;\n"
    "}\n";

static const char* kVideoFS =
    "precision mediump float;\n"
    "varying vec2 vTexCoord;\n"
    "uniform sampler2D uTexture;\n"
    "uniform int uSwizzle;\n"
    "uniform float uSaturation;\n"   // 0.0 = grayscale, 1.0 = full color
    "uniform float uGradient;\n"     // 0.0 = no gradient, 1.0 = full gradient
    "void main() {\n"
    "    vec4 c = texture2D(uTexture, vTexCoord);\n"
    "    if (uSwizzle == 1) c = vec4(c.b, c.g, c.r, 1.0);\n"
    "    float gray = dot(c.rgb, vec3(0.299, 0.587, 0.114));\n"
    "    c.rgb = mix(vec3(gray), c.rgb, uSaturation);\n"
    "    float fade = smoothstep(0.35, 0.75, vTexCoord.y) * uGradient;\n"
    "    c.rgb *= (1.0 - fade);\n"  // aggressive gradient: starts 35% down, full black by 75%
    "    gl_FragColor = c;\n"
    "}\n";

static GLuint compileShader(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    return s;
}

static GLuint linkProgram(const char* vs, const char* fs) {
    GLuint v = compileShader(GL_VERTEX_SHADER, vs);
    GLuint f = compileShader(GL_FRAGMENT_SHADER, fs);
    GLuint p = glCreateProgram();
    glAttachShader(p, v);
    glAttachShader(p, f);
    glLinkProgram(p);
    glDeleteShader(v);
    glDeleteShader(f);
    return p;
}

// ---- Helper: read entire file ----

static std::vector<uint8_t> readFile(const std::string& path) {
    std::vector<uint8_t> data;
    int fd = open(path.c_str(), O_RDONLY);
    if (fd < 0) return data;
    struct stat st;
    if (fstat(fd, &st) == 0 && st.st_size > 0) {
        data.resize(st.st_size);
        read(fd, data.data(), st.st_size);
    }
    close(fd);
    return data;
}

// RetroArch RZIP format: zlib-compressed save states.
// Header: "#RZIPv" (6 bytes) + version (1 byte) + full_size (8 bytes LE)
// Then compressed frames: uint32_le compressed_size + uint32_le uncompressed_size + zlib data
static std::vector<uint8_t> tryDecompressRzip(const std::vector<uint8_t>& data) {
    if (data.size() < 15 || memcmp(data.data(), "#RZIPv", 6) != 0)
        return {};
    uint64_t fullSize = 0;
    for (int i = 0; i < 8; i++)
        fullSize |= (uint64_t)data[7 + i] << (i * 8);
    if (fullSize == 0 || fullSize > 64 * 1024 * 1024)
        return {};
    std::vector<uint8_t> out(fullSize);
    size_t pos = 15;
    size_t outPos = 0;
    while (pos + 8 <= data.size() && outPos < fullSize) {
        uint32_t compSize = 0, uncompSize = 0;
        for (int i = 0; i < 4; i++) {
            compSize |= (uint32_t)data[pos + i] << (i * 8);
            uncompSize |= (uint32_t)data[pos + 4 + i] << (i * 8);
        }
        pos += 8;
        if (compSize == 0 || pos + compSize > data.size())
            break;
        if (uncompSize == 0 || outPos + uncompSize > fullSize)
            break;
        uLongf destLen = uncompSize;
        if (uncompress(out.data() + outPos, &destLen,
                       data.data() + pos, compSize) != Z_OK)
            return {};
        pos += compSize;
        outPos += destLen;
    }
    if (outPos != fullSize) {
        ALOGW("LibretroRunner: RZIP decompress incomplete "
              "(%zu/%llu)", outPos, (unsigned long long)fullSize);
        return {};
    }
    ALOGI("LibretroRunner: RZIP decompressed %zu -> %llu bytes",
          data.size(), (unsigned long long)fullSize);
    return out;
}

static bool writeFile(const std::string& path, const void* data, size_t size) {
    int fd = open(path.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd < 0) return false;
    const uint8_t* p = (const uint8_t*)data;
    size_t remaining = size;
    while (remaining > 0) {
        ssize_t w = write(fd, p, remaining);
        if (w <= 0) { close(fd); return false; }
        p += w;
        remaining -= w;
    }
    close(fd);
    return true;
}


// ---- Constructor / Destructor ----

LibretroRunner::LibretroRunner() {
    sInstance = this;
}

LibretroRunner::~LibretroRunner() {
    shutdown();
    if (sInstance == this) sInstance = nullptr;
}

// ---- Core loading ----

#define LOAD_SYM(handle, name) \
    *(void**)(&name) = dlsym(handle, #name); \
    if (!name) { ALOGE("LibretroRunner: missing symbol: %s", #name); }

bool LibretroRunner::init(const std::string& corePath,
                          const std::string& romPath,
                          const std::string& saveStatePath,
                          const std::string& saveRamPath) {
    ALOGI("LibretroRunner: init core=%s rom=%s", corePath.c_str(), romPath.c_str());

    // dlopen the core
    mCoreHandle = dlopen(corePath.c_str(), RTLD_LAZY);
    if (!mCoreHandle) {
        ALOGE("LibretroRunner: dlopen failed: %s", dlerror());
        return false;
    }

    // Load all function pointers
    LOAD_SYM(mCoreHandle, retro_init);
    LOAD_SYM(mCoreHandle, retro_deinit);
    LOAD_SYM(mCoreHandle, retro_set_environment);
    LOAD_SYM(mCoreHandle, retro_set_video_refresh);
    LOAD_SYM(mCoreHandle, retro_set_audio_sample);
    LOAD_SYM(mCoreHandle, retro_set_audio_sample_batch);
    LOAD_SYM(mCoreHandle, retro_set_input_poll);
    LOAD_SYM(mCoreHandle, retro_set_input_state);
    LOAD_SYM(mCoreHandle, retro_load_game);
    LOAD_SYM(mCoreHandle, retro_unload_game);
    LOAD_SYM(mCoreHandle, retro_run);
    LOAD_SYM(mCoreHandle, retro_get_system_info);
    LOAD_SYM(mCoreHandle, retro_get_system_av_info);
    LOAD_SYM(mCoreHandle, retro_serialize_size);
    LOAD_SYM(mCoreHandle, retro_serialize);
    LOAD_SYM(mCoreHandle, retro_unserialize);
    LOAD_SYM(mCoreHandle, retro_get_memory_size);
    LOAD_SYM(mCoreHandle, retro_get_memory_data);

    if (!retro_init || !retro_load_game || !retro_run) {
        ALOGE("LibretroRunner: critical symbols missing");
        shutdown();
        return false;
    }

    // Set callbacks before retro_init
    retro_set_environment(onEnvironment);
    retro_set_video_refresh(onVideoRefresh);
    retro_set_audio_sample(onAudioSample);
    retro_set_audio_sample_batch(onAudioSampleBatch);
    retro_set_input_poll(onInputPoll);
    retro_set_input_state(onInputState);

    // Initialize core
    retro_init();

    // Get system info
    struct retro_system_info sysInfo = {};
    if (retro_get_system_info) {
        retro_get_system_info(&sysInfo);
        ALOGI("LibretroRunner: core=%s version=%s fullpath=%d",
              sysInfo.library_name, sysInfo.library_version,
              sysInfo.need_fullpath);
    }

    // Load ROM — for fullpath cores with zips, look for pre-extracted file
    struct retro_game_info gameInfo = {};
    std::vector<uint8_t> romData;
    std::string effectivePath = romPath;
    if (sysInfo.need_fullpath && romPath.size() > 4 &&
        romPath.substr(romPath.size() - 4) == ".zip") {
        // Core wants a file path but got a zip — look for extracted ROM
        // (nano_cache.sh extracts zips during populate)
        std::string cacheDir = romPath.substr(0, romPath.rfind('/'));
        DIR* d = opendir(cacheDir.c_str());
        if (d) {
            struct dirent* e;
            while ((e = readdir(d)) != nullptr) {
                std::string name(e->d_name);
                if (name == "." || name == ".." || name.find(".zip") != std::string::npos ||
                    name.find(".srm") != std::string::npos ||
                    name.find(".state") != std::string::npos ||
                    name.find(".sav") != std::string::npos ||
                    name.find(".png") != std::string::npos)
                    continue;
                // Found a non-zip ROM file
                effectivePath = cacheDir + "/" + name;
                ALOGI("LibretroRunner: using extracted ROM %s", effectivePath.c_str());
                break;
            }
            closedir(d);
        }
    }
    gameInfo.path = effectivePath.c_str();
    if (!sysInfo.need_fullpath) {
        romData = readFile(romPath);
        if (romData.empty()) {
            ALOGE("LibretroRunner: failed to read ROM: %s", romPath.c_str());
            shutdown();
            return false;
        }
        gameInfo.data = romData.data();
        gameInfo.size = romData.size();
    }

    if (!retro_load_game(&gameInfo)) {
        ALOGE("LibretroRunner: retro_load_game failed");
        shutdown();
        return false;
    }

    // Get AV info for audio setup
    struct retro_system_av_info avInfo = {};
    if (retro_get_system_av_info) {
        retro_get_system_av_info(&avInfo);
        mSampleRate = avInfo.timing.sample_rate;
        mAspectRatio = avInfo.geometry.aspect_ratio;
        if (mAspectRatio <= 0.01f && avInfo.geometry.base_width > 0
                && avInfo.geometry.base_height > 0) {
            mAspectRatio = (float)avInfo.geometry.base_width
                         / (float)avInfo.geometry.base_height;
        }
        ALOGI("LibretroRunner: %ux%u @ %.1ffps, audio %.0fHz, aspect %.3f",
              avInfo.geometry.base_width, avInfo.geometry.base_height,
              avInfo.timing.fps, avInfo.timing.sample_rate, mAspectRatio);
    }

    // Load SRAM if available
    if (!saveRamPath.empty() && retro_get_memory_data && retro_get_memory_size) {
        void* sramPtr = retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
        size_t sramSize = retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
        if (sramPtr && sramSize > 0) {
            auto sramData = readFile(saveRamPath);
            if (sramData.size() == sramSize) {
                memcpy(sramPtr, sramData.data(), sramSize);
                ALOGI("LibretroRunner: loaded SRAM (%zu bytes)", sramSize);
            }
        }
    }

    // Load save state if available
    if (!saveStatePath.empty() && retro_unserialize && retro_serialize_size) {
        auto stateData = readFile(saveStatePath);
        if (!stateData.empty()) {
            // Decompress RZIP if RetroArch compressed the state
            auto rzip = tryDecompressRzip(stateData);
            if (!rzip.empty())
                stateData = std::move(rzip);

            size_t expectedSize = retro_serialize_size();
            ALOGI("LibretroRunner: state file=%zu expected=%zu",
                  stateData.size(), expectedSize);

            bool restored = retro_unserialize(
                    stateData.data(), stateData.size());

            // Size mismatch: pad with zeros or truncate to match
            // what the core expects (RetroArch does this too)
            if (!restored && expectedSize > 0 &&
                    stateData.size() != expectedSize) {
                std::vector<uint8_t> adj(expectedSize, 0);
                memcpy(adj.data(), stateData.data(),
                       std::min(stateData.size(), expectedSize));
                restored = retro_unserialize(adj.data(), adj.size());
                if (restored)
                    ALOGI("LibretroRunner: restored with size "
                          "adjustment (%zu -> %zu)",
                          stateData.size(), expectedSize);
            }

            if (restored) {
                ALOGI("LibretroRunner: restored save state (%zu bytes)",
                      stateData.size());
            } else {
                ALOGW("LibretroRunner: save state restore failed "
                      "(file=%zu expected=%zu)",
                      stateData.size(), expectedSize);
            }
        }
    }

    // Init GL for video rendering
    initVideoGL();

    // Null audio — no sound during loading screen preview
    initAudio(mSampleRate);

    mInitialized = true;
    return true;
}

void LibretroRunner::shutdown() {
    if (mCoreHandle) {
        if (retro_unload_game) retro_unload_game();
        if (retro_deinit) retro_deinit();
        dlclose(mCoreHandle);
        mCoreHandle = nullptr;
    }
    if (mFrameTex) {
        glDeleteTextures(1, &mFrameTex);
        mFrameTex = 0;
    }
    if (mVideoProgram) {
        glDeleteProgram(mVideoProgram);
        mVideoProgram = 0;
    }
    shutdownAudio();
    mInitialized = false;
}

// ---- Video ----

void LibretroRunner::initVideoGL() {
    mVideoProgram = linkProgram(kVideoVS, kVideoFS);
    mVideoLocPosition = glGetAttribLocation(mVideoProgram, "aPosition");
    mVideoLocTexCoord = glGetAttribLocation(mVideoProgram, "aTexCoord");
    mVideoLocTexture = glGetUniformLocation(mVideoProgram, "uTexture");
    mVideoLocSwizzle = glGetUniformLocation(mVideoProgram, "uSwizzle");
    mVideoLocSaturation = glGetUniformLocation(mVideoProgram, "uSaturation");
    mVideoLocGradient = glGetUniformLocation(mVideoProgram, "uGradient");
    mVideoLocRotation = glGetUniformLocation(mVideoProgram, "uRotation");

    glGenTextures(1, &mFrameTex);
    glBindTexture(GL_TEXTURE_2D, mFrameTex);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
}

void LibretroRunner::uploadFrame(const void* data, unsigned width,
                                  unsigned height, size_t pitch) {
    if (!data || !width || !height) return;

    glBindTexture(GL_TEXTURE_2D, mFrameTex);

    GLenum format = GL_RGB;
    GLenum type = GL_UNSIGNED_SHORT_5_6_5;

    if (mPixelFormat == RETRO_PIXEL_FORMAT_XRGB8888) {
        format = GL_RGBA;
        type = GL_UNSIGNED_BYTE;
    } else if (mPixelFormat == RETRO_PIXEL_FORMAT_0RGB1555) {
        format = GL_RGBA;
        type = GL_UNSIGNED_SHORT_5_5_5_1;
    }

    // Handle pitch != width * bpp by uploading row-by-row
    unsigned bpp = (mPixelFormat == RETRO_PIXEL_FORMAT_XRGB8888) ? 4 : 2;
    if (pitch == width * bpp) {
        glTexImage2D(GL_TEXTURE_2D, 0, format, width, height,
                     0, format, type, data);
    } else {
        // Allocate and copy with correct pitch
        std::vector<uint8_t> buf(width * height * bpp);
        const uint8_t* src = (const uint8_t*)data;
        for (unsigned y = 0; y < height; y++) {
            memcpy(buf.data() + y * width * bpp, src + y * pitch, width * bpp);
        }
        glTexImage2D(GL_TEXTURE_2D, 0, format, width, height,
                     0, format, type, buf.data());
    }

    mFrameWidth = width;
    mFrameHeight = height;
    mFrameDirty = true;
}

void LibretroRunner::renderFrame(int screenWidth, int screenHeight,
                                  float saturation, float gradient) {
    if (!mFrameTex || !mFrameWidth || !mFrameHeight) return;

    // GammaOS: When rotation is active, the caller has already set the viewport
    // to panel-native AHB dims. Don't override it — the rotation matrix maps
    // logical NDC (from the aspect-corrected quad) to panel-native NDC.
    bool hasRotation = (mRotationMatrix[0] != 1.0f || mRotationMatrix[3] != 1.0f);
    if (!hasRotation) {
        glViewport(0, 0, screenWidth, screenHeight);
    }
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    // Calculate aspect-correct quad (use core's reported aspect ratio if available)
    float srcAspect = (mAspectRatio > 0.01f) ? mAspectRatio
                      : (float)mFrameWidth / (float)mFrameHeight;
    float dstAspect = (float)screenWidth / (float)screenHeight;
    float qw = 1.0f, qh = 1.0f;
    if (srcAspect > dstAspect) {
        qh = dstAspect / srcAspect;
    } else {
        qw = srcAspect / dstAspect;
    }

    // Fullscreen quad with aspect ratio correction
    float vertices[] = {
        -qw, -qh,  0.0f, 1.0f,  // bottom-left  (pos, texcoord)
         qw, -qh,  1.0f, 1.0f,  // bottom-right
        -qw,  qh,  0.0f, 0.0f,  // top-left
         qw,  qh,  1.0f, 0.0f,  // top-right
    };

    glUseProgram(mVideoProgram);
    // GammaOS: Apply the DRM rotation matrix if set. NanoMenu uploads it
    // via setRotationMatrix() before calling runFrame during DRM direct mode.
    if (mVideoLocRotation >= 0) {
        glUniformMatrix2fv(mVideoLocRotation, 1, GL_FALSE, mRotationMatrix);
    }
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, mFrameTex);
    glUniform1i(mVideoLocTexture, 0);
    glUniform1i(mVideoLocSwizzle,
                (mPixelFormat == RETRO_PIXEL_FORMAT_XRGB8888) ? 1 : 0);
    glUniform1f(mVideoLocSaturation, saturation);
    glUniform1f(mVideoLocGradient, gradient);

    glVertexAttribPointer(mVideoLocPosition, 2, GL_FLOAT, GL_FALSE,
                          4 * sizeof(float), vertices);
    glEnableVertexAttribArray(mVideoLocPosition);
    glVertexAttribPointer(mVideoLocTexCoord, 2, GL_FLOAT, GL_FALSE,
                          4 * sizeof(float), vertices + 2);
    glEnableVertexAttribArray(mVideoLocTexCoord);

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glDisableVertexAttribArray(mVideoLocPosition);
    glDisableVertexAttribArray(mVideoLocTexCoord);
}

// ---- Audio (null driver — drop all samples) ----

void LibretroRunner::initAudio(double sampleRate) {
    mSampleRate = sampleRate;
}

void LibretroRunner::shutdownAudio() {
}

void LibretroRunner::writeAudio(const int16_t* /*data*/, size_t /*frames*/) {
    // Drop all audio samples — null driver
}

// ---- Input ----

void LibretroRunner::setButton(unsigned port, unsigned button, bool pressed) {
    if (port > 1 || button > 15) return;
    if (pressed)
        mButtons[port] |= (1 << button);
    else
        mButtons[port] &= ~(1 << button);
}

void LibretroRunner::setAnalog(unsigned port, unsigned index,
                                unsigned id, int16_t value) {
    if (port > 1 || index > 1 || id > 1) return;
    mAnalog[port][index][id] = value;
}

// ---- Run one frame ----

// Guard for first-frame crash (some cores crash without BIOS files)
static sigjmp_buf sFirstFrameJmp;
static volatile bool sFirstFrameCrash = false;
static void firstFrameSigHandler(int sig) {
    (void)sig;
    sFirstFrameCrash = true;
    siglongjmp(sFirstFrameJmp, 1);
}

bool LibretroRunner::trySafeFirstFrame() {
    // Install temporary signal handler to catch SIGSEGV/SIGABRT
    struct sigaction sa = {}, oldSa = {}, oldAbrt = {};
    sa.sa_handler = firstFrameSigHandler;
    sa.sa_flags = 0;
    sigemptyset(&sa.sa_mask);
    sigaction(SIGSEGV, &sa, &oldSa);
    sigaction(SIGABRT, &sa, &oldAbrt);

    bool ok = false;
    if (sigsetjmp(sFirstFrameJmp, 1) == 0) {
        retro_run();
        ok = true;
    } else {
        ALOGE("LibretroRunner: core crashed on first frame (missing BIOS?)");
    }

    // Restore original handlers
    sigaction(SIGSEGV, &oldSa, nullptr);
    sigaction(SIGABRT, &oldAbrt, nullptr);
    return ok;
}

bool LibretroRunner::runFrame(int screenWidth, int screenHeight,
                              float saturation, float gradient) {
    if (!mInitialized || !retro_run) return false;
    retro_run();
    renderFrame(screenWidth, screenHeight, saturation, gradient);
    return true;
}

bool LibretroRunner::renderOnly(int screenWidth, int screenHeight,
                                float saturation, float gradient) {
    if (!mInitialized || !mFrameTex) return false;
    renderFrame(screenWidth, screenHeight, saturation, gradient);
    return true;
}

// ---- Save state / SRAM ----

bool LibretroRunner::saveState(const std::string& path) {
    if (!retro_serialize_size || !retro_serialize) return false;
    size_t size = retro_serialize_size();
    if (size == 0) return false;
    std::vector<uint8_t> buf(size);
    if (!retro_serialize(buf.data(), size)) return false;
    return writeFile(path, buf.data(), size);
}

bool LibretroRunner::saveSRAM(const std::string& path) {
    if (!retro_get_memory_data || !retro_get_memory_size) return false;
    void* data = retro_get_memory_data(RETRO_MEMORY_SAVE_RAM);
    size_t size = retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
    if (!data || size == 0) return false;
    return writeFile(path, data, size);
}

// ---- Static callbacks (route to singleton) ----

void LibretroRunner::onVideoRefresh(const void* data, unsigned width,
                                     unsigned height, size_t pitch) {
    if (sInstance) sInstance->uploadFrame(data, width, height, pitch);
}

void LibretroRunner::onAudioSample(int16_t left, int16_t right) {
    if (sInstance) {
        int16_t buf[2] = {left, right};
        sInstance->writeAudio(buf, 1);
    }
}

size_t LibretroRunner::onAudioSampleBatch(const int16_t* data, size_t frames) {
    if (sInstance) sInstance->writeAudio(data, frames);
    return frames;
}

void LibretroRunner::onInputPoll() {
    // Input is set externally via setButton/setAnalog before runFrame
}

int16_t LibretroRunner::onInputState(unsigned port, unsigned device,
                                      unsigned index, unsigned id) {
    if (!sInstance || port > 1) return 0;
    if (device == RETRO_DEVICE_JOYPAD) {
        return (sInstance->mButtons[port] >> id) & 1;
    }
    if (device == RETRO_DEVICE_ANALOG) {
        if (index <= 1 && id <= 1)
            return sInstance->mAnalog[port][index][id];
    }
    return 0;
}

bool LibretroRunner::onEnvironment(unsigned cmd, void* data) {
    unsigned rawCmd = cmd & 0xFFFF;
    switch (cmd) {
    case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
        if (!data) return false;
        unsigned fmt = *(const unsigned*)data;
        if (sInstance) sInstance->mPixelFormat = (enum retro_pixel_format)fmt;
        ALOGI("LibretroRunner: pixel format = %u", fmt);
        return true;
    }
    case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY: {
        if (!data) return false;
        *(const char**)data = "/data/system/nano_cache";
        return true;
    }
    case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY: {
        if (!data) return false;
        *(const char**)data = "/data/system/nano_cache/rom";
        return true;
    }
    case RETRO_ENVIRONMENT_GET_LOG_INTERFACE: {
        if (!data) return false;
        auto* cb = (struct retro_log_callback*)data;
        cb->log = onLog;
        return true;
    }
    case RETRO_ENVIRONMENT_GET_VARIABLE: {
        auto* var = (struct retro_variable*)data;
        if (!var || !var->key) return false;
        // gpSP: disable dynarec (PROT_EXEC blocked in bootanim)
        if (strcmp(var->key, "gpsp_drc") == 0) {
            var->value = "disabled";
            return true;
        }
        if (strcmp(var->key, "mupen64plus-rdp-plugin") == 0) {
            var->value = "angrylion";
            return true;
        }
        // Gambatte: enable GBC colorization
        if (strcmp(var->key, "gambatte_gb_colorization") == 0) {
            var->value = "GBC";
            return true;
        }
        // Nestopia: 4:3 aspect ratio
        if (strcmp(var->key, "nestopia_aspect") == 0) {
            var->value = "4:3";
            return true;
        }
        // snes9x: return false (unhandled) so core uses defaults.
        // Returning "disabled" breaks rendering (disables transparency).
        if (strncmp(var->key, "snes9x_", 7) == 0) {
            return false;
        }
        return false;
    }
    case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
        if (!data) return false;
        *(bool*)data = false;
        return true;
    case RETRO_ENVIRONMENT_GET_LANGUAGE:
        if (!data) return false;
        *(unsigned*)data = RETRO_LANGUAGE_ENGLISH;
        return true;
    case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
        return true;
    case 3: // RETRO_ENVIRONMENT_GET_CAN_DUPE
        if (data) *(bool*)data = true;
        return true;
    case 62: // RETRO_ENVIRONMENT_SET_AUDIO_BUFFER_STATUS_CALLBACK
        return false;
    case 65: // RETRO_ENVIRONMENT_SET_CONTENT_INFO_OVERRIDE
        return true;
    case (47 | 0x10000): // RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE (experimental)
        if (data) *(int*)data = 3; // enable both audio + video
        return true;
    default:
        return false;
    }
}

void LibretroRunner::onLog(enum retro_log_level level, const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    char buf[512];
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);
    switch (level) {
    case RETRO_LOG_DEBUG: ALOGD("LibretroCore: %s", buf); break;
    case RETRO_LOG_INFO:  ALOGI("LibretroCore: %s", buf); break;
    case RETRO_LOG_WARN:  ALOGW("LibretroCore: %s", buf); break;
    case RETRO_LOG_ERROR: ALOGE("LibretroCore: %s", buf); break;
    default: break;
    }
}
