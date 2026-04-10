/*
 * Copyright (C) 2026 GammaOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "GammaOSNano"

#include <algorithm>
#include <thread>
#include <mutex>
#include <fcntl.h>
#include <dirent.h>
#include <unistd.h>
#include <sys/stat.h>
#include <math.h>
#include <stdlib.h>
#include <linux/input.h>
#include <sys/inotify.h>
#include <signal.h>
#include <strings.h>

#include <binder/IPCThreadState.h>
#include <cutils/properties.h>
#include <android-base/properties.h>
#include <utils/Log.h>
#include <utils/SystemClock.h>

#include <ui/DisplayMode.h>
#include <ui/DisplayState.h>
#include <ui/LayerStack.h>
#include <ui/PixelFormat.h>
#include <ui/Rect.h>

#include <gui/ISurfaceComposer.h>
#include <gui/Surface.h>
#include <gui/SurfaceComposerClient.h>

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <EGL/eglext.h>
#include <png.h>

// DRM direct framebuffer for early boot splash
#include <drm.h>
#include <drm_mode.h>
#include <drm_fourcc.h>
#include <sys/mman.h>
#include <android/hardware_buffer.h>

// ARM NEON intrinsics for fast AHB→DRM channel swap.
// Targeting ARMv8-A (Cortex-A55 on RK3568). NEON is mandatory on AArch64,
// so the __ARM_NEON define is always set when building 64-bit. Retain the
// scalar fallback for portability to hypothetical non-NEON targets.
#if defined(__ARM_NEON) || defined(__aarch64__)
#include <arm_neon.h>
#define GAMMAOS_NANO_HAVE_NEON 1
#else
#define GAMMAOS_NANO_HAVE_NEON 0
#endif

#include <aidl/android/hardware/light/ILights.h>
#include <aidl/android/hardware/light/HwLight.h>

#include "LibretroRunner.h"
#include <aidl/android/hardware/light/HwLightState.h>
#include <aidl/android/hardware/light/LightType.h>
#include <android/binder_manager.h>
#include <android/hardware/light/2.0/ILight.h>

#include "NanoMenu.h"
#include "xmb_icons.h"

namespace android {

using ui::DisplayMode;

// ---------------------------------------------------------------------------
// Shaders
// ---------------------------------------------------------------------------

static const char VERTEX_SHADER[] = R"(
    attribute vec4 aPosition;
    uniform vec4 uColor;
    uniform mat2 uRotation;
    varying vec4 vColor;
    void main() {
        gl_Position = vec4(uRotation * aPosition.xy, aPosition.zw);
        vColor = uColor;
    }
)";

static const char FRAGMENT_SHADER[] = R"(
    precision mediump float;
    varying vec4 vColor;
    void main() {
        gl_FragColor = vColor;
    }
)";

// Batched particle shader — per-vertex position + color, single draw call
static const char PARTICLE_VERTEX_SHADER[] = R"(
    attribute vec2 aPosition;
    attribute vec4 aColor;
    uniform mat2 uRotation;
    varying vec4 vColor;
    void main() {
        gl_Position = vec4(uRotation * aPosition, 0.0, 1.0);
        vColor = aColor;
    }
)";
static const char PARTICLE_FRAGMENT_SHADER[] = R"(
    precision mediump float;
    varying vec4 vColor;
    void main() { gl_FragColor = vColor; }
)";

// Fullscreen procedural effect shader (effects 11-20)
static const char FX_VERTEX_SHADER[] = R"(
    attribute vec4 aPosition;
    uniform mat2 uRotation;
    void main() { gl_Position = vec4(uRotation * aPosition.xy, aPosition.zw); }
)";

static const char FX_FRAGMENT_SHADER[] = R"(
#ifdef GL_FRAGMENT_PRECISION_HIGH
    precision highp float;
#else
    precision mediump float;
#endif
    uniform float uTime;
    uniform vec2 uResolution;
    uniform int uEffect;
    uniform float uCoordSwap;

    float hash(vec2 p) {
        return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
    }
    float hash1(float n) { return fract(sin(n) * 43758.5453); }
    float noise(vec2 p) {
        vec2 i = floor(p); vec2 f = fract(p);
        f = f * f * (3.0 - 2.0 * f);
        float a = hash(i); float b = hash(i + vec2(1,0));
        float c = hash(i + vec2(0,1)); float d = hash(i + vec2(1,1));
        return mix(mix(a,b,f.x), mix(c,d,f.x), f.y);
    }
    void main() {
        vec2 fc = mix(gl_FragCoord.xy,
                      vec2(gl_FragCoord.y, uResolution.y - gl_FragCoord.x),
                      uCoordSwap);
        vec2 uv = fc / uResolution;
        float t = uTime;
        vec3 col = vec3(0.0);
        float a = 0.0;

        if (uEffect == 11) { // Plasma
            float v = sin(uv.x*10.0+t) + sin(uv.y*8.0-t*0.7)
                    + sin((uv.x+uv.y)*6.0+t*1.3) + sin(length(uv-0.5)*12.0-t*2.0);
            v *= 0.25;
            col = vec3(sin(v*3.14)*0.5+0.5, sin(v*3.14+2.09)*0.5+0.5, sin(v*3.14+4.18)*0.5+0.5);
            a = 0.18;
        } else if (uEffect == 12) { // Static
            col = vec3(hash(uv * 500.0 + t * 100.0));
            a = 0.12;
        } else if (uEffect == 13) { // Scanlines
            float line = step(0.5, fract(gl_FragCoord.y / 3.0));
            col = vec3(0.0, line * 0.4, line * 0.3);
            a = line * 0.2;
        } else if (uEffect == 14) { // Mosaic
            vec2 cell = floor(uv * 12.0 + t * 0.5);
            float h = hash(cell + floor(t * 2.0));
            col = vec3(h * 0.3, h * 0.5, h * 0.8);
            a = 0.15;
        } else if (uEffect == 15) { // Matrix rain
            vec2 cell = vec2(floor(uv.x * 30.0), floor(uv.y * 50.0));
            float colSpeed = hash1(cell.x * 73.0) * 3.0 + 1.0;
            float drop = fract(-t * colSpeed + hash1(cell.x * 37.0));
            float bright = smoothstep(0.0, 0.15, drop) * smoothstep(0.6, 0.0, drop);
            bright *= step(0.6, hash1(cell.x * 17.0));
            col = vec3(0.0, bright, bright * 0.3);
            a = bright * 0.4;
        } else if (uEffect == 16) { // Fire
            // Distorted sine waves — no grid noise, no blockiness
            float y = 1.0 - uv.y; // 0=top, 1=bottom
            float x = uv.x;
            // Three flame layers: each is a sine wave distorted vertically
            // to create rising, turbulent tongue shapes
            float d1 = sin(y * 3.0 - t * 3.5) * 1.8;
            float d2 = sin(y * 4.5 - t * 4.5) * 1.2;
            float d3 = sin(y * 2.5 - t * 2.8) * 2.2;
            float f1 = sin(x * 8.0 + d1 + t * 0.4) * 0.5 + 0.5;
            float f2 = sin(x * 12.0 - d2 + t * 0.7 + 2.1) * 0.5 + 0.5;
            float f3 = sin(x * 5.0 + d3 - t * 0.3 + 4.2) * 0.5 + 0.5;
            float turb = f1 * 0.4 + f2 * 0.35 + f3 * 0.25;
            // Height envelope: bright base, fading tips
            float fire = turb * y * y;
            fire = pow(fire, 1.3);
            // Realistic fire colors: red → orange → yellow → white
            col = vec3(
                smoothstep(0.05, 0.5, fire),
                smoothstep(0.2, 0.8, fire) * 0.85,
                smoothstep(0.5, 1.0, fire) * 0.4
            );
            a = smoothstep(0.0, 0.06, fire) * 0.3;
        } else if (uEffect == 17) { // Aurora
            float wave = sin(uv.x * 8.0 + t) * 0.05 + sin(uv.x * 3.0 - t * 0.7) * 0.08;
            float band = smoothstep(0.15, 0.0, abs(uv.y - 0.8 - wave));
            float band2 = smoothstep(0.12, 0.0, abs(uv.y - 0.7 - wave * 1.3));
            col = vec3(0.1, 0.8, 0.4) * band + vec3(0.3, 0.2, 0.9) * band2;
            a = (band + band2) * 0.25;
        } else if (uEffect == 18) { // Ripple
            float d = length(uv - 0.5);
            float ring = sin(d * 40.0 - t * 4.0) * 0.5 + 0.5;
            ring *= smoothstep(0.5, 0.1, d);
            col = vec3(0.1, 0.3, ring);
            a = ring * 0.2;
        } else if (uEffect == 19) { // Checkerboard
            float angle = t * 0.3;
            float ca = cos(angle); float sa = sin(angle);
            vec2 ruv = vec2(ca*(uv.x-0.5) - sa*(uv.y-0.5), sa*(uv.x-0.5) + ca*(uv.y-0.5));
            float sc = 8.0 + sin(t * 0.5) * 3.0;
            float ck = mod(floor(ruv.x * sc) + floor(ruv.y * sc), 2.0);
            col = mix(vec3(0.08, 0.08, 0.15), vec3(0.15, 0.12, 0.25), ck);
            a = 0.3;
        } else if (uEffect == 20) { // Spiral
            vec2 c = uv - 0.5;
            float angle = atan(c.y, c.x);
            float dist = length(c);
            float spiral = sin(angle * 5.0 + dist * 20.0 - t * 3.0) * 0.5 + 0.5;
            spiral *= smoothstep(0.5, 0.05, dist);
            col = vec3(spiral * 0.2, spiral * 0.1, spiral * 0.4);
            a = spiral * 0.2;
        }
        gl_FragColor = vec4(col, a);
    }
)";

// XMB wave background shader — PS3-style flowing ribbons
// Analytical screen-space approach: no loops, no ray marching
// 3 ribbon bands with multi-harmonic sine waves + sparkle dust
static const char XMB_FRAGMENT_SHADER[] = R"(
#ifdef GL_FRAGMENT_PRECISION_HIGH
    precision highp float;
#else
    precision mediump float;
#endif
    uniform float uTime;
    uniform vec2 uResolution;
    uniform float uCoordSwap;

    float xmbHash(vec2 p) {
        return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
    }

    vec3 hsv2rgb(float h, float s, float v) {
        vec3 p = abs(fract(vec3(h) + vec3(0.0, 2.0/3.0, 1.0/3.0)) * 6.0 - 3.0);
        return v * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), s);
    }

    void main() {
        vec2 fc = mix(gl_FragCoord.xy,
                      vec2(gl_FragCoord.y, uResolution.y - gl_FragCoord.x),
                      uCoordSwap);
        vec2 uv = fc / uResolution;
        float t = uTime;

        float x = (uv.x - 0.5) * uResolution.x / uResolution.y;

        // Shared sine terms — reused across ribbons to cut sin() calls
        // from 7 down to 4 while keeping multi-harmonic look
        float s1 = sin(x + t * 0.25);
        float s2 = sin(x * 2.2 + t * 0.7);
        float s3 = sin(x * 1.05 + t * 0.24 + 0.5);
        float s4 = sin(x * 1.8 + t * 0.42 + 2.0);

        float ribbon = 0.0;
        float cy1 = 0.44 + 0.08 * s1 + 0.03 * s2;
        ribbon += smoothstep(0.07, 0.0, abs(uv.y - cy1)) * 0.35;

        float cy2 = 0.47 + 0.06 * s3 + 0.025 * s2;
        ribbon += smoothstep(0.05, 0.0, abs(uv.y - cy2)) * 0.25;

        float cy3 = 0.41 + 0.09 * s1 + 0.04 * s4;
        ribbon += smoothstep(0.06, 0.0, abs(uv.y - cy3)) * 0.20;

        ribbon = clamp(ribbon, 0.0, 1.0);

        float hue = fract(t * 0.002 + 0.6);
        vec3 tint = hsv2rgb(hue, 0.85, 0.65);

        // Gradient: dark tint at top, full tint at bottom
        vec3 col = mix(tint, tint * 0.15, smoothstep(0.0, 1.0, uv.y));
        col = mix(col, vec3(1.0), ribbon * 0.7);

        // Sparkle: cheap fract-based pulse instead of sin+pow
        vec2 grid = floor(gl_FragCoord.xy / 6.0);
        float seed = xmbHash(grid);
        float pulse = fract(t * 0.5 + seed * 17.0);
        pulse = 1.0 - abs(pulse * 2.0 - 1.0); // triangle wave 0→1→0
        pulse *= pulse; // sharpen
        float sparkle = step(0.97, seed) * pulse
                       * smoothstep(0.0, 0.1, ribbon) * 0.5;
        col += vec3(sparkle);

        gl_FragColor = vec4(col, 1.0);
    }
)";

// ---------------------------------------------------------------------------
// Effect names
// ---------------------------------------------------------------------------

static const char* kEffectNames[NUM_EFFECTS + 1] = {
    "None",
    "Snow", "Rain", "Confetti", "Sparks", "Fireflies",
    "Bubbles", "Starfield", "Embers", "Leaves", "Dust",
    "Plasma", "Static", "Scanlines", "Mosaic", "Matrix",
    "Fire", "Aurora", "Ripple", "Checkerboard", "Spiral",
    "XMB"
};

// Active effects — removed: Dust(10), Static(12), Scanlines(13), Mosaic(14), Matrix(15)
static const int kActiveEffects[] = {
    0,  // None
    1,  // Snow
    2,  // Rain
    3,  // Confetti
    4,  // Sparks
    5,  // Fireflies
    6,  // Bubbles
    7,  // Starfield
    8,  // Embers
    9,  // Leaves
    11, // Plasma
    16, // Fire
    17, // Aurora
    18, // Ripple
    19, // Checkerboard
    20, // Spiral
    21, // XMB
};
static const int kNumActiveEffects = sizeof(kActiveEffects) / sizeof(kActiveEffects[0]);
// Index into kActiveEffects (NOT the effect ID itself)
static int sActiveEffectIdx = 0;

// ---------------------------------------------------------------------------
// XMB system definitions (from Daijisho database)
// ---------------------------------------------------------------------------

struct SystemDef {
    const char* name;
    const char* shortname;
    const char* romDir;       // Directory name under /sdcard/ROMs/
    const char* coreSo;       // RetroArch core .so filename (empty for standalone)
    const char* launchPkg;    // Package name for standalone emulators (empty for RetroArch)
    const char* launchIntent; // Intent template for standalone ({file.uri} placeholder)
    float r, g, b;            // Icon color
    const char* acceptExts;   // Comma-separated accepted extensions (lowercase, with dots)
};

// For RetroArch cores: coreSo is set, launchPkg/launchIntent are empty
// For standalone emulators: coreSo is empty, launchPkg+launchIntent are set
// Intent uses {file.uri} as placeholder for the ROM URI
static const SystemDef kXmbSystemDefs[] = {
    {"NES",             "NES",  "nes",          "nestopia_libretro_android.so",               "", "",
     0.89f, 0.00f, 0.06f, ".nes,.fds,.unf,.unif"},
    {"SNES",            "SNES", "snes",         "snes9x_libretro_android.so",                "", "",
     0.48f, 0.49f, 0.49f, ".smc,.sfc,.fig,.swc"},
    {"Game Boy",        "GB",   "gb",           "gambatte_libretro_android.so",              "", "",
     0.61f, 0.73f, 0.06f, ".gb"},
    {"Game Boy Color",  "GBC",  "gbc",          "gambatte_libretro_android.so",              "", "",
     0.42f, 0.25f, 0.63f, ".gbc,.gb"},
    {"Game Boy Advance","GBA",  "gba",          "gpsp_libretro_android.so",                  "", "",
     0.36f, 0.25f, 0.63f, ".gba"},
    {"Nintendo 64",     "N64",  "n64",          "mupen64plus_next_gles3_libretro_android.so", "", "",
     0.00f, 0.60f, 0.00f, ".n64,.v64,.z64,.ndd"},
    {"Nintendo DS",     "NDS",  "nds",          "",
     "com.dsemu.drastic",
     "-n com.dsemu.drastic/.DraSticActivity -d {file.uri} --activity-clear-task --activity-clear-top",
     0.63f, 0.63f, 0.63f, ".nds"},
    {"Genesis",         "GEN",  "genesis",      "genesis_plus_gx_libretro_android.so",       "", "",
     0.00f, 0.38f, 0.66f, ".md,.gen,.smd,.bin"},
    {"Master System",   "SMS",  "mastersystem", "genesis_plus_gx_libretro_android.so",       "", "",
     0.78f, 0.00f, 0.00f, ".sms,.sg"},
    {"Game Gear",       "GG",   "gamegear",     "genesis_plus_gx_libretro_android.so",       "", "",
     0.09f, 0.09f, 0.85f, ".gg"},
    {"PlayStation",     "PSX",  "psx",          "pcsx_rearmed_libretro_android.so",          "", "",
     0.00f, 0.19f, 0.53f, ".cue,.pbp,.chd,.iso,.m3u,.img"},
    {"PSP",             "PSP",  "psp",          "",
     "org.ppsspp.ppsspp",
     "-n org.ppsspp.ppsspp/.PpssppActivity -a android.intent.action.VIEW -d {file.uri} -t application/octet-stream --activity-clear-task --activity-clear-top",
     0.10f, 0.10f, 0.10f, ".iso,.cso,.pbp"},
    {"Dreamcast",       "DC",   "dreamcast",    "",
     "com.flycast.emulator",
     "-n com.flycast.emulator/com.flycast.emulator.MainActivity -a android.intent.action.VIEW -d {file.uri}",
     1.00f, 0.50f, 0.00f, ".cdi,.gdi,.chd,.cue"},
    {"Neo Geo Pocket",  "NGP",  "ngpc",         "mednafen_ngp_libretro_android.so",          "", "",
     0.50f, 0.50f, 0.50f, ".ngp,.ngc,.npc"},
    {"PICO-8",          "P-8",  "pico8",        "fake08_libretro_android.so",                "", "",
     1.00f, 0.00f, 0.30f, ".p8,.png"},
};
static const int kNumXmbSystemDefs = sizeof(kXmbSystemDefs) / sizeof(kXmbSystemDefs[0]);

// OSK keyboard layout
static const char kOskLayout[3][9] = {
    {'A','B','C','D','E','F','G','H','I'},
    {'J','K','L','M','N','O','P','Q','R'},
    {'S','T','U','V','W','X','Y','Z',' '},
};
static const int kOskRows = 3;
static const int kOskCols = 9;

// Case-insensitive substring search
static bool containsInsensitive(const std::string& haystack, const std::string& needle) {
    if (needle.empty()) return true;
    if (haystack.size() < needle.size()) return false;
    for (size_t i = 0; i <= haystack.size() - needle.size(); i++) {
        bool match = true;
        for (size_t j = 0; j < needle.size(); j++) {
            char a = haystack[i + j], b = needle[j];
            if (a >= 'A' && a <= 'Z') a += 32;
            if (b >= 'A' && b <= 'Z') b += 32;
            if (a != b) { match = false; break; }
        }
        if (match) return true;
    }
    return false;
}

// ---------------------------------------------------------------------------
// Font layout constants (kept for layout compatibility)
// ---------------------------------------------------------------------------

static const int FONT_CHAR_W = 8;
static const int FONT_CHAR_H = 16;

// Text rendering shader (per-vertex color for emoji support)
static const char TEXT_VERTEX_SHADER[] = R"(
    attribute vec2 aPosition;
    attribute vec2 aTexCoord;
    attribute vec4 aColor;
    uniform mat2 uRotation;
    varying vec2 vTexCoord;
    varying vec4 vColor;
    void main() {
        gl_Position = vec4(uRotation * aPosition, 0.0, 1.0);
        vTexCoord = aTexCoord;
        vColor = aColor;
    }
)";
static const char TEXT_FRAGMENT_SHADER[] = R"(
    precision mediump float;
    varying vec2 vTexCoord;
    varying vec4 vColor;
    uniform sampler2D uTexture;
    void main() {
        vec4 texel = texture2D(uTexture, vTexCoord);
        gl_FragColor = texel * vColor;
    }
)";

static GLuint compileShader(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    GLint ok = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char buf[512];
        glGetShaderInfoLog(shader, sizeof(buf), nullptr, buf);
        ALOGE("Shader compile error: %s", buf);
    }
    return shader;
}

static GLuint linkProgram(GLuint vs, GLuint fs) {
    GLuint prog = glCreateProgram();
    glAttachShader(prog, vs);
    glAttachShader(prog, fs);
    glLinkProgram(prog);
    GLint ok = 0;
    glGetProgramiv(prog, GL_LINK_STATUS, &ok);
    if (!ok) {
        char buf[512];
        glGetProgramInfoLog(prog, sizeof(buf), nullptr, buf);
        ALOGE("Program link error: %s", buf);
    }
    return prog;
}



// ---------------------------------------------------------------------------
// Random helpers
// ---------------------------------------------------------------------------

static float randf() { return (float)rand() / (float)RAND_MAX; }
static float randf(float lo, float hi) { return lo + randf() * (hi - lo); }

// ---------------------------------------------------------------------------
// NanoMenu implementation
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Minimal JSON string value extractor for playlist parsing
// ---------------------------------------------------------------------------

static std::string extractJsonString(const std::string& json, const std::string& key,
                                     size_t searchStart, size_t searchEnd) {
    std::string needle = "\"" + key + "\"";
    size_t keyPos = json.find(needle, searchStart);
    if (keyPos == std::string::npos || keyPos > searchEnd) return "";
    size_t colonPos = json.find(':', keyPos + needle.size());
    if (colonPos == std::string::npos || colonPos > searchEnd) return "";
    size_t qStart = json.find('"', colonPos + 1);
    if (qStart == std::string::npos || qStart > searchEnd) return "";
    size_t qEnd = qStart + 1;
    while (qEnd < json.size() && qEnd <= searchEnd) {
        if (json[qEnd] == '"' && json[qEnd - 1] != '\\') break;
        qEnd++;
    }
    if (qEnd > json.size()) return "";
    return json.substr(qStart + 1, qEnd - qStart - 1);
}

NanoMenu::NanoMenu()
    : Thread(false),
      mWidth(0), mHeight(0),
      mDisplay(EGL_NO_DISPLAY),
      mContext(EGL_NO_CONTEXT),
      mSurface(EGL_NO_SURFACE),
      mAppliedLayerStack(UINT32_MAX),
      mShaderProgram(0), mLocPosition(-1), mLocColor(-1),
      mParticleProgram(0), mParticleLocPosition(-1), mParticleLocColor(-1),
      mFxProgram(0), mFxLocPosition(-1), mFxLocTime(-1),
      mFxLocResolution(-1), mFxLocEffect(-1),
      mSelectedIndex(0),
      mDisplayDirty(true),
      mInotifyFd(-1),
      mExitRequested(false),
      mWaitForRelease(false),
      mMenuState(MENU_MAIN),
      mRecentSelectedIndex(0),
      mRecentLoaded(false),
      mStorageReady(false),
      mAppSelectedIndex(0),
      mAppsLoaded(false),
      mScrollOffset(0.0f),
      mScrollDir(1),
      mScrollPause(0),
      mLastScrolledIdx(-1),
      mMenuScrollTop(0),
      mStickYTriggered(false),
      mStickXTriggered(false),
      mSelectHeld(false), mPowerPressTime(0),
      mBrightness(128), mMaxBrightness(255),
      mShowBrightnessBar(false), mBrightnessBarTimer(0),
      mVolume(10), mMaxVolume(15),
      mShowVolumeBar(false), mVolumeBarTimer(0),
      mLastFrameNs(0),
      mFrameDt(1.0f / 60.0f),
      mCurrentEffect(1),
      mEffectTime(0.0f),
      mQuickResumeEnabled(false),
      mXmbMode(false), mXmbRecentMax(50), mXmbSystemIndex(0), mXmbGameIndex(0),
      mXmbAnimX(0.0f), mXmbAnimY(0.0f),
      mXmbGameScrollTop(0), mXmbRomScanDone(false),
      mXmbBootCompleted(false),
      mBgScanResultReady(false), mBgScanThreadRunning(false),
      mOskActive(false), mOskCursorX(0), mOskCursorY(0),
      mSearchSelectedIndex(0), mSearchActive(false),
      mFtLib(nullptr),
      mFtNumFaces(0),
      mFontSize(48),
      mGlyphAtlasTex(0),
      mAtlasW(0), mAtlasH(0),
      mAtlasCurX(0), mAtlasCurY(0), mAtlasRowH(0),
      mTextProgram(0), mTextLocPosition(-1), mTextLocTexCoord(-1),
      mTextLocColor(-1), mTextLocTexture(-1) {
    mSession = new SurfaceComposerClient();
    srand(elapsedRealtime());
    memset(mParticles, 0, sizeof(mParticles));
    memset(mFtFaces, 0, sizeof(mFtFaces));
    // Restore persisted wallpaper effect, default to XMB (21)
    char wallpaper[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.wallpaper", wallpaper, "21");
    int savedEffect = atoi(wallpaper);
    bool found = false;
    for (int i = 0; i < kNumActiveEffects; i++) {
        if (kActiveEffects[i] == savedEffect) {
            sActiveEffectIdx = i;
            mCurrentEffect = savedEffect;
            found = true;
            break;
        }
    }
    if (!found) {
        sActiveEffectIdx = kNumActiveEffects - 1; // XMB
        mCurrentEffect = kActiveEffects[sActiveEffectIdx];
    }
    // Load Quick Resume toggle from persistent property
    mQuickResumeEnabled = android::base::GetBoolProperty(
            "persist.gammaos.nano.quick_resume", false);
}

NanoMenu::~NanoMenu() {
    // GammaOS: Clean up secondary display wallpaper resources.
    for (size_t i = 0; i < mSecondaryEglSurfaces.size(); i++) {
        eglDestroySurface(mDisplay, mSecondaryEglSurfaces[i]);
    }
    mSecondaryEglSurfaces.clear();
    mSecondarySurfaces.clear();
    if (!mSecondaryWallpaperControls.empty()) {
        SurfaceComposerClient::Transaction t;
        for (size_t i = 0; i < mSecondaryWallpaperControls.size(); i++) {
            t.reparent(mSecondaryWallpaperControls[i], nullptr);
        }
        t.apply();
        mSecondaryWallpaperControls.clear();
    }
    for (int fd : mInputFds) {
        ioctl(fd, EVIOCGRAB, 0); // release grab (always, in case exit-grab was applied)
        close(fd);
    }
    if (mInotifyFd >= 0) close(mInotifyFd);
}

void NanoMenu::onFirstRef() {
    status_t err = mSession->linkToComposerDeath(this);
    SLOGE_IF(err, "linkToComposerDeath failed (%s)", strerror(-err));
}

sp<SurfaceComposerClient> NanoMenu::session() const { return mSession; }

void NanoMenu::binderDied(const wp<IBinder>&) {
    ALOGD("SurfaceFlinger died, exiting...");
    kill(getpid(), SIGKILL);
    requestExit();
}

int NanoMenu::readSysfsInt(const char* path, int fallback) {
    int fd = open(path, O_RDONLY);
    if (fd < 0) return fallback;
    char buf[32] = {};
    read(fd, buf, sizeof(buf) - 1);
    close(fd);
    return atoi(buf);
}

void NanoMenu::writeSysfsInt(const char* path, int value) {
    int fd = open(path, O_WRONLY);
    if (fd < 0) { ALOGE("Cannot write %s", path); return; }
    char buf[32];
    int len = snprintf(buf, sizeof(buf), "%d", value);
    write(fd, buf, len);
    close(fd);
}

// Set backlight brightness via the ILights AIDL HAL (portable across devices).
// Returns true if HAL call succeeded, false if HAL not available yet.
bool NanoMenu::setBrightnessViaHal(int brightness) {
    using aidl::android::hardware::light::ILights;
    using aidl::android::hardware::light::HwLight;
    using aidl::android::hardware::light::HwLightState;
    using aidl::android::hardware::light::LightType;

    ndk::SpAIBinder binder(
            AServiceManager_checkService("android.hardware.light.ILights/default"));
    if (!binder.get()) {
        return false;
    }
    std::shared_ptr<ILights> hal = ILights::fromBinder(binder);
    if (!hal) {
        return false;
    }

    std::vector<HwLight> lights;
    hal->getLights(&lights);
    for (const auto& light : lights) {
        if (light.type == LightType::BACKLIGHT) {
            HwLightState state{};
            // Standard Android convention: brightness in alpha channel of ARGB
            state.color = 0xFF000000 | (brightness << 16) | (brightness << 8) | brightness;
            hal->setLightState(light.id, state);
            return true;
        }
    }
    return false;
}

void NanoMenu::adjustBrightness(int direction) {
    int step = mMaxBrightness / 10;
    if (step < 1) step = 1;
    mBrightness += step * direction;
    if (mBrightness < 1) mBrightness = 1;
    if (mBrightness > mMaxBrightness) mBrightness = mMaxBrightness;
    setBrightnessViaHal(mBrightness);
    // Sync brightness to persist property (shared with Android)
    char buf[32];
    snprintf(buf, sizeof(buf), "%d", mBrightness);
    property_set("persist.gammaos.nano.brightness", buf);
    mShowBrightnessBar = true;
    mBrightnessBarTimer = 90; // ~1.5s at 60fps
}

void NanoMenu::buildMenu() {
    // Reset launch_app to default so RetroArch launches work after returning
    // from a non-RetroArch app launched via the Applications submenu.
    property_set("sys.gammaos.nano.launch_app", "com.retroarch.aarch64");

    mMenuItems.clear();
    mMenuItems.push_back({"RetroArch (Nano)"});
    mMenuItems.push_back({"Recently Played"});
    mMenuItems.push_back({"Applications"});
    mMenuItems.push_back({"Boot Android"});
    mMenuItems.push_back({"Recovery Mode"});
    mMenuItems.push_back({"Safe Mode"});
    mMenuItems.push_back({"Reboot"});
    mMenuItems.push_back({"Power Off"});
    mSelectedIndex = 0;
    mMenuState = MENU_MAIN;
    mRecentSelectedIndex = 0;
    mAppSelectedIndex = 0;

    // If returning from a game launched via Recently Played, go straight back
    char returnRecent[PROPERTY_VALUE_MAX] = {};
    property_get("sys.gammaos.nano.return_recent", returnRecent, "0");
    if (!strcmp(returnRecent, "1")) {
        property_set("sys.gammaos.nano.return_recent", "0");
        mSelectedIndex = 1; // "Recently Played"
        // Try loading playlist; if storage isn't ready yet, the render loop
        // will keep polling and auto-load when available
        if (mStorageReady) {
            loadRecentPlaylist();
        }
        mMenuState = MENU_RECENT;
        mRecentSelectedIndex = 0;
        ALOGD("NanoMenu: returning to Recently Played after game exit");
    }
    // If returning from an app launched via Applications, go straight back
    char returnApps[PROPERTY_VALUE_MAX] = {};
    property_get("sys.gammaos.nano.return_apps", returnApps, "0");
    if (!strcmp(returnApps, "1")) {
        property_set("sys.gammaos.nano.return_apps", "0");
        mSelectedIndex = 2; // "Applications"
        if (mStorageReady) {
            loadInstalledApps();
        }
        mMenuState = MENU_APPS;
        mAppSelectedIndex = 0;
        ALOGD("NanoMenu: returning to Applications after app exit");
    }
    mDisplayDirty = true;
}

void NanoMenu::rebuildDisplayItems() {
    mDisplayItems.clear();
    if (mMenuState == MENU_RECENT) {
        mTitle = "Recently Played";
        for (const auto& entry : mRecentEntries) {
            std::string item = entry.label;
            if (item.empty()) {
                size_t slash = entry.romPath.rfind('/');
                item = (slash != std::string::npos)
                    ? entry.romPath.substr(slash + 1) : entry.romPath;
            }
            // Strip file extension
            size_t dot = item.rfind('.');
            if (dot != std::string::npos && dot > 0) item = item.substr(0, dot);
            // Append system + core
            if (!entry.coreName.empty() && entry.coreName != "DETECT") {
                std::string coreName = entry.coreName;
                size_t pStart = coreName.rfind('(');
                size_t pEnd = coreName.rfind(')');
                if (pStart != std::string::npos && pEnd != std::string::npos
                    && pEnd > pStart) {
                    coreName = coreName.substr(pStart + 1, pEnd - pStart - 1);
                }
                std::string sysName;
                if (!entry.dbName.empty()) {
                    sysName = entry.dbName;
                    size_t pipe = sysName.find('|');
                    if (pipe != std::string::npos) sysName = sysName.substr(0, pipe);
                } else if (pStart != std::string::npos && pStart > 0) {
                    sysName = entry.coreName.substr(0, pStart);
                    while (!sysName.empty() && sysName.back() == ' ')
                        sysName.pop_back();
                }
                if (!sysName.empty()) {
                    item += "  [" + sysName + " - " + coreName + "]";
                } else {
                    item += "  [" + coreName + "]";
                }
            }
            mDisplayItems.push_back(item);
        }
        mDisplayItems.push_back("< Back");
        if (!mStorageReady) {
            mSubtitle = "Please wait, unlocking storage...";
        } else if (mRecentEntries.empty()) {
            mSubtitle = "No recent games found";
        } else {
            char buf[64];
            snprintf(buf, sizeof(buf), "%zu game%s", mRecentEntries.size(),
                     mRecentEntries.size() == 1 ? "" : "s");
            mSubtitle = buf;
        }
        mFooter = "DPAD/VOL: Nav | A/PWR: Select | B: Back | R: Quick Resume";
    } else if (mMenuState == MENU_APPS) {
        mTitle = "Applications";
        for (const auto& app : mAppEntries) {
            mDisplayItems.push_back(app.label);
        }
        mDisplayItems.push_back("< Back");
        if (!mStorageReady) {
            mSubtitle = "Please wait, unlocking storage...";
        } else if (mAppEntries.empty()) {
            mSubtitle = "No installed apps found";
        } else {
            char buf[64];
            snprintf(buf, sizeof(buf), "%zu app%s", mAppEntries.size(),
                     mAppEntries.size() == 1 ? "" : "s");
            mSubtitle = buf;
        }
        mFooter = "DPAD/VOL: Nav | A/PWR: Select | B: Back | HOME: Return";
    } else {
        mTitle = "GammaOS Nano";
        for (const auto& item : mMenuItems) {
            mDisplayItems.push_back(item.label);
        }
        mSubtitle = "v0.1 - Proof of Concept";
        char buf[200];
        snprintf(buf, sizeof(buf),
                 "DPAD: Nav | A: Select | X: FX [%s] | Y: FX | L1: XMB | R1: QR",
                 kEffectNames[mCurrentEffect]);
        mFooter = buf;
    }
    mDisplayDirty = false;
}

void NanoMenu::loadRecentPlaylist() {
    mRecentEntries.clear();
    mRecentLoaded = false;
    // Read from raw filesystem path (bypasses FUSE, works before FUSE mount).
    // /data/media/0 is the backing store for emulated storage — accessible
    // after CE unlock even before FUSE is mounted.
    const char* path = "/data/media/0/RetroArch/playlists/builtin/content_history.lpl";
    int fd = open(path, O_RDONLY);
    if (fd < 0) {
        ALOGW("NanoMenu: cannot open %s: %s", path, strerror(errno));
        // mRecentLoaded stays false — UI will show "storage not ready" message
        return;
    }
    mRecentLoaded = true;
    struct stat st;
    if (fstat(fd, &st) < 0 || st.st_size <= 0 || st.st_size > 1024 * 1024) {
        close(fd);
        return;
    }
    std::string content(st.st_size, '\0');
    ssize_t bytesRead = read(fd, &content[0], st.st_size);
    close(fd);
    if (bytesRead <= 0) return;
    content.resize(bytesRead);

    // Find "items" array
    size_t itemsPos = content.find("\"items\"");
    if (itemsPos == std::string::npos) return;
    size_t arrayStart = content.find('[', itemsPos);
    if (arrayStart == std::string::npos) return;

    // Find matching ]
    size_t arrayEnd = std::string::npos;
    int depth = 0;
    bool inStr = false;
    for (size_t i = arrayStart; i < content.size(); i++) {
        char c = content[i];
        if (c == '"' && (i == 0 || content[i - 1] != '\\')) inStr = !inStr;
        if (!inStr) {
            if (c == '[') depth++;
            if (c == ']') { depth--; if (depth == 0) { arrayEnd = i; break; } }
        }
    }
    if (arrayEnd == std::string::npos) return;

    // Parse each item object
    size_t pos = arrayStart + 1;
    static const int MAX_RECENT = 8;
    while (pos < arrayEnd && (int)mRecentEntries.size() < MAX_RECENT) {
        size_t objStart = content.find('{', pos);
        if (objStart == std::string::npos || objStart > arrayEnd) break;
        // Find matching }
        int objDepth = 0;
        bool objInStr = false;
        size_t objEnd = std::string::npos;
        for (size_t i = objStart; i <= arrayEnd; i++) {
            char c = content[i];
            if (c == '"' && (i == 0 || content[i - 1] != '\\')) objInStr = !objInStr;
            if (!objInStr) {
                if (c == '{') objDepth++;
                if (c == '}') { objDepth--; if (objDepth == 0) { objEnd = i; break; } }
            }
        }
        if (objEnd == std::string::npos) break;

        RecentEntry entry;
        entry.label = extractJsonString(content, "label", objStart, objEnd);
        entry.romPath = extractJsonString(content, "path", objStart, objEnd);
        entry.corePath = extractJsonString(content, "core_path", objStart, objEnd);
        entry.coreName = extractJsonString(content, "core_name", objStart, objEnd);
        entry.dbName = extractJsonString(content, "db_name", objStart, objEnd);

        if (!entry.romPath.empty() && entry.romPath != "DETECT") {
            if (entry.label.empty()) {
                // Use filename as label
                size_t slash = entry.romPath.rfind('/');
                entry.label = (slash != std::string::npos)
                    ? entry.romPath.substr(slash + 1) : entry.romPath;
            }
            mRecentEntries.push_back(entry);
        }
        pos = objEnd + 1;
    }
    ALOGD("NanoMenu: loaded %zu recent entries from %s", mRecentEntries.size(), path);
}

void NanoMenu::loadInstalledApps() {
    mAppEntries.clear();
    mAppsLoaded = false;
    // Parse /data/system/packages.list — the authoritative package database.
    // Format: <pkg> <uid> <debug> <dataDir> <seinfo> <gids> <prof> <ver> <hasCode> <installer>
    // User-installed apps have @null as the last field (no system partition).
    const char* path = "/data/system/packages.list";
    int fd = open(path, O_RDONLY);
    if (fd < 0) {
        ALOGW("NanoMenu: cannot open %s: %s", path, strerror(errno));
        return;
    }
    mAppsLoaded = true;
    struct stat st;
    if (fstat(fd, &st) < 0 || st.st_size <= 0 || st.st_size > 2 * 1024 * 1024) {
        close(fd);
        return;
    }
    std::string content(st.st_size, '\0');
    ssize_t bytesRead = read(fd, &content[0], st.st_size);
    close(fd);
    if (bytesRead <= 0) return;
    content.resize(bytesRead);

    // Parse line by line
    size_t pos = 0;
    while (pos < content.size()) {
        size_t eol = content.find('\n', pos);
        if (eol == std::string::npos) eol = content.size();
        std::string line = content.substr(pos, eol - pos);
        pos = eol + 1;
        if (line.empty()) continue;

        // Only include user-installed apps (installer = @null)
        if (line.size() < 6 || line.substr(line.size() - 5) != "@null") continue;

        // Extract package name (first field, space-delimited)
        size_t space = line.find(' ');
        if (space == std::string::npos) continue;
        std::string pkgName = line.substr(0, space);

        // Skip RetroArch, system-like, and internal packages
        if (pkgName == "com.retroarch.aarch64") continue;
        if (pkgName.find("com.android.") == 0) continue;
        if (pkgName.find("org.lineageos.") == 0) continue;
        if (pkgName.find("com.gammaos.") == 0) continue;
        if (pkgName.find("com.topjohnwu.") == 0) continue;

        // Build a human-readable label from the package name:
        // take the last segment and capitalize first letter
        std::string label = pkgName;
        size_t lastDot = pkgName.rfind('.');
        if (lastDot != std::string::npos && lastDot + 1 < pkgName.size()) {
            label = pkgName.substr(lastDot + 1);
            if (!label.empty() && label[0] >= 'a' && label[0] <= 'z') {
                label[0] -= 32; // capitalize
            }
        }
        // Replace underscores with spaces for readability
        for (char& c : label) {
            if (c == '_') c = ' ';
        }
        AppEntry app;
        app.packageName = pkgName;
        app.label = label;
        mAppEntries.push_back(app);
    }
    // Resolve actual app labels from the cache written by SystemServer.
    // Format: "package.name|Human Label\n"
    {
        const char* labelPath = "/data/system/nano_app_labels.txt";
        int labelFd = open(labelPath, O_RDONLY);
        if (labelFd >= 0) {
            struct stat labelSt;
            if (fstat(labelFd, &labelSt) == 0 && labelSt.st_size > 0
                    && labelSt.st_size < 512 * 1024) {
                std::string labelContent(labelSt.st_size, '\0');
                ssize_t labelRead = read(labelFd, &labelContent[0], labelSt.st_size);
                if (labelRead > 0) {
                    labelContent.resize(labelRead);
                    size_t lpos = 0;
                    while (lpos < labelContent.size()) {
                        size_t leol = labelContent.find('\n', lpos);
                        if (leol == std::string::npos) leol = labelContent.size();
                        std::string lline = labelContent.substr(lpos, leol - lpos);
                        lpos = leol + 1;
                        size_t lpipe = lline.find('|');
                        if (lpipe == std::string::npos) continue;
                        std::string lpkg = lline.substr(0, lpipe);
                        std::string llabel = lline.substr(lpipe + 1);
                        if (llabel.empty()) continue;
                        for (auto& app : mAppEntries) {
                            if (app.packageName == lpkg) {
                                app.label = llabel;
                                break;
                            }
                        }
                    }
                }
            }
            close(labelFd);
        } else {
            ALOGW("NanoMenu: label cache not available yet: %s", strerror(errno));
        }
    }

    // Sort alphabetically by label
    std::sort(mAppEntries.begin(), mAppEntries.end(),
              [](const AppEntry& a, const AppEntry& b) {
                  return a.label < b.label;
              });
    ALOGD("NanoMenu: loaded %zu installed apps from packages.list", mAppEntries.size());
}

void NanoMenu::handleBack() {
    if (mOskActive) {
        closeOsk();
        return;
    }
    if (mXmbMode) {
        if (mSearchActive) {
            mSearchActive = false;
            mOskQuery.clear();
            mSearchResults.clear();
        } else {
            // Exit XMB mode back to text menu
            mXmbMode = false;
            property_set("persist.gammaos.nano.xmb_mode", "0");
            mMenuState = MENU_MAIN;
            mDisplayDirty = true;
        }
        return;
    }
    if (mMenuState == MENU_RECENT) {
        mMenuState = MENU_MAIN;
        mRecentSelectedIndex = 0;
        mMenuScrollTop = 0;
        mDisplayDirty = true;
    } else if (mMenuState == MENU_APPS) {
        mMenuState = MENU_MAIN;
        mAppSelectedIndex = 0;
        mMenuScrollTop = 0;
        mDisplayDirty = true;
    }
}

void NanoMenu::openInputDevices() {
    DIR* dir = opendir("/dev/input");
    if (!dir) { ALOGE("Cannot open /dev/input"); return; }
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (strncmp(entry->d_name, "event", 5) != 0) continue;
        char path[PATH_MAX];
        snprintf(path, sizeof(path), "/dev/input/%s", entry->d_name);
        if (mOpenedDevices.count(entry->d_name)) continue;
        int fd = open(path, O_RDONLY | O_NONBLOCK);
        if (fd >= 0) {
            // Exclusive grab: prevent Android InputReader from stealing events.
            // Gated by property — disable when preload is off to avoid input
            // ownership issues during app transitions.
            if (android::base::GetBoolProperty("persist.gammaos.nano.grab_input", false)) {
                if (ioctl(fd, EVIOCGRAB, 1) < 0) {
                    ALOGW("EVIOCGRAB failed for %s: %s", path, strerror(errno));
                }
                ALOGD("Opened + grabbed input device: %s", path);
            } else {
                ALOGD("Opened input device (no grab): %s", path);
            }
            mInputFds.push_back(fd);
            mOpenedDevices.insert(entry->d_name);
        }
    }
    closedir(dir);

    // Set up inotify to detect hotplugged and replaced input devices.
    // IN_DELETE is needed because gammapad may destroy+recreate device nodes
    // to seize them; we must detect the deletion, drop our stale fd, and
    // re-open+grab when the replacement IN_CREATE arrives.
    mInotifyFd = inotify_init1(IN_NONBLOCK | IN_CLOEXEC);
    if (mInotifyFd >= 0) {
        inotify_add_watch(mInotifyFd, "/dev/input", IN_CREATE | IN_DELETE);
        ALOGD("Watching /dev/input for hotplug");
    }
}

// ---------------------------------------------------------------------------
// Input
// ---------------------------------------------------------------------------

void NanoMenu::handleSelect() {
    if (mOskActive) {
        char ch = kOskLayout[mOskCursorY][mOskCursorX];
        oskType(ch);
        return;
    }
    if (mXmbMode) {
        launchXmbGame();
        return;
    }
    if (mMenuState == MENU_RECENT) {
        int numEntries = (int)mRecentEntries.size();
        // Last item is "< Back"
        if (mRecentSelectedIndex >= numEntries) {
            handleBack();
            return;
        }
        // Launch the selected game directly into RetroArch
        const auto& entry = mRecentEntries[mRecentSelectedIndex];
        ALOGI("NanoMenu: launching game: %s core: %s",
              entry.romPath.c_str(), entry.corePath.c_str());
        android::base::SetProperty("sys.gammaos.nano.launch_rom", entry.romPath);
        android::base::SetProperty("sys.gammaos.nano.launch_core", entry.corePath);
        // Track launched package so NanoMenu can force-stop it on next restart
        {
            char launchApp[PROPERTY_VALUE_MAX] = {};
            property_get("sys.gammaos.nano.launch_app", launchApp, "com.retroarch.aarch64");
            android::base::SetProperty("sys.gammaos.nano.launched_pkg", launchApp);
        }
        // Trigger DE cache populate (ROM first, then delta sync everything)
        property_set("sys.gammaos.nano.cache_ready", "0");
        property_set("sys.gammaos.nano.cache_op", "populate");
        // Prime Quick Resume now — the persist write has time to flush to disk
        // while the game runs. ShutdownThread may update ROM/core from the
        // playlist if the user loaded a different game, but this ensures the
        // flag survives even if the reboot races the persist write.
        if (mQuickResumeEnabled) {
            android::base::SetProperty("persist.gammaos.nano.qr_rom", entry.romPath);
            android::base::SetProperty("persist.gammaos.nano.qr_core", entry.corePath);
            property_set("persist.gammaos.nano.qr_prepared", "1");
        }
        // Flag so next nano menu restart returns to Recently Played
        property_set("sys.gammaos.nano.return_recent", "1");
        property_set("service.bootanim.nano_retroarch", "1");
        // Tell InputDispatcher to drop events immediately — prevents a fast
        // double-press A from queuing a second event before the transition.
        property_set("sys.gammaos.nano.drop_input", "1");
        // Set a timestamp fence — InputDispatcher drops any events with
        // eventTime <= this value, covering the race where the A-DOWN was
        // queued before drop_input was set.
        struct timespec ts;
        clock_gettime(CLOCK_MONOTONIC, &ts);
        int64_t fenceNs = (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
        char buf[32];
        snprintf(buf, sizeof(buf), "%" PRId64, fenceNs);
        property_set("sys.gammaos.nano.drop_fence_ns", buf);
        // Don't exit yet — wait for the select key to be released so the
        // key-up event passes through Android's InputReader before RetroArch
        // gets focus. Otherwise the A press leaks to RetroArch as a phantom input.
        mWaitForRelease = true;
        return;
    }

    if (mMenuState == MENU_APPS) {
        int numApps = (int)mAppEntries.size();
        // Last item is "< Back"
        if (mAppSelectedIndex >= numApps) {
            handleBack();
            return;
        }
        // Launch the selected app
        const auto& app = mAppEntries[mAppSelectedIndex];
        ALOGI("NanoMenu: launching app: %s", app.packageName.c_str());
        android::base::SetProperty("sys.gammaos.nano.launch_app", app.packageName);
        // Track launched package so NanoMenu can force-stop it on next restart
        android::base::SetProperty("sys.gammaos.nano.launched_pkg", app.packageName);
        // Clear any ROM/core properties so RootWindowContainer uses generic launch
        android::base::SetProperty("sys.gammaos.nano.launch_rom", "");
        android::base::SetProperty("sys.gammaos.nano.launch_core", "");
        // Flag so next nano menu restart returns to Applications
        property_set("sys.gammaos.nano.return_apps", "1");
        property_set("service.bootanim.nano_retroarch", "1");
        property_set("sys.gammaos.nano.drop_input", "1");
        mWaitForRelease = true;
        return;
    }

    // Main menu
    ALOGD("Select item %d: %s", mSelectedIndex, mMenuItems[mSelectedIndex].label.c_str());
    const auto& label = mMenuItems[mSelectedIndex].label;
    if (label == "RetroArch (Nano)") {
        property_set("service.bootanim.nano_retroarch", "1");
        property_set("sys.gammaos.nano.drop_input", "1");
        mWaitForRelease = true;
    } else if (label == "Recently Played") {
        if (!mStorageReady) return; // greyed out, ignore
        // Load playlist from RetroArch's content_history.lpl (needs CE unlock)
        loadRecentPlaylist();
        mMenuState = MENU_RECENT;
        mRecentSelectedIndex = 0;
        mMenuScrollTop = 0;
        mDisplayDirty = true;
    } else if (label == "Applications") {
        if (!mStorageReady) return; // greyed out, ignore
        loadInstalledApps();
        mMenuState = MENU_APPS;
        mAppSelectedIndex = 0;
        mMenuScrollTop = 0;
        mDisplayDirty = true;
    } else if (label == "Boot Android") {
        // Full Android needs a clean boot.  Dispatch via nano_action so
        // init (which has powerctl_prop access) handles the reboot.
        property_set("service.bootanim.nano_action", "android");
    } else if (label == "Recovery Mode") {
        property_set("service.bootanim.nano_action", "recovery");
    } else if (label == "Safe Mode") {
        property_set("service.bootanim.nano_action", "safemode");
    } else if (label == "Reboot") {
        prepareShutdown("reboot");
    } else if (label == "Power Off") {
        prepareShutdown("shutdown");
    }
}

void NanoMenu::handleUp() {
    if (mOskActive) {
        if (mOskCursorY > 0) mOskCursorY--;
        return;
    }
    if (mXmbMode) {
        if (mSearchActive) {
            if (mSearchSelectedIndex > 0) mSearchSelectedIndex--;
        } else {
            if (mXmbGameIndex > 0) mXmbGameIndex--;
        }
        return;
    }
    if (mMenuState == MENU_RECENT) {
        if (mRecentSelectedIndex > 0) mRecentSelectedIndex--;
    } else if (mMenuState == MENU_APPS) {
        if (mAppSelectedIndex > 0) mAppSelectedIndex--;
    } else {
        if (mSelectedIndex > 0) {
            mSelectedIndex--;
            // Skip greyed-out items when storage isn't ready
            if (!mStorageReady && mSelectedIndex < (int)mMenuItems.size()) {
                const auto& lbl = mMenuItems[mSelectedIndex].label;
                if ((lbl == "Recently Played" || lbl == "Applications")
                    && mSelectedIndex > 0) {
                    mSelectedIndex--;
                    // Check again for the other greyed item
                    if (!mStorageReady && mSelectedIndex < (int)mMenuItems.size()) {
                        const auto& lbl2 = mMenuItems[mSelectedIndex].label;
                        if ((lbl2 == "Recently Played" || lbl2 == "Applications")
                            && mSelectedIndex > 0) {
                            mSelectedIndex--;
                        }
                    }
                }
            }
        }
    }
}

void NanoMenu::handleDown() {
    if (mOskActive) {
        if (mOskCursorY < kOskRows - 1) mOskCursorY++;
        return;
    }
    if (mXmbMode) {
        if (mSearchActive) {
            int maxIdx = (int)mSearchResults.size() - 1;
            if (mSearchSelectedIndex < maxIdx) mSearchSelectedIndex++;
        } else if (mXmbSystemIndex == -1) {
            // Recently Played
            int maxIdx = (int)mXmbRecent.size() - 1;
            if (mXmbGameIndex < maxIdx) mXmbGameIndex++;
        } else {
            int selSys = mXmbSystemIndex;
            if (selSys >= 0 && selSys < (int)mXmbSystems.size()) {
                int maxIdx = (int)mXmbSystems[selSys].roms.size() - 1;
                if (mXmbGameIndex < maxIdx) mXmbGameIndex++;
            }
        }
        return;
    }
    if (mMenuState == MENU_RECENT) {
        int maxIdx = (int)mRecentEntries.size(); // "< Back" is at this index
        if (mRecentSelectedIndex < maxIdx) mRecentSelectedIndex++;
    } else if (mMenuState == MENU_APPS) {
        int maxIdx = (int)mAppEntries.size(); // "< Back" is at this index
        if (mAppSelectedIndex < maxIdx) mAppSelectedIndex++;
    } else {
        int last = (int)mMenuItems.size() - 1;
        if (mSelectedIndex < last) {
            mSelectedIndex++;
            // Skip greyed-out items when storage isn't ready
            if (!mStorageReady && mSelectedIndex < (int)mMenuItems.size()) {
                const auto& lbl = mMenuItems[mSelectedIndex].label;
                if ((lbl == "Recently Played" || lbl == "Applications")
                    && mSelectedIndex < last) {
                    mSelectedIndex++;
                    // Check again for the other greyed item
                    if (!mStorageReady && mSelectedIndex < (int)mMenuItems.size()) {
                        const auto& lbl2 = mMenuItems[mSelectedIndex].label;
                        if ((lbl2 == "Recently Played" || lbl2 == "Applications")
                            && mSelectedIndex < last) {
                            mSelectedIndex++;
                        }
                    }
                }
            }
        }
    }
}

void NanoMenu::pollInput() {
    struct input_event ev;
    for (int fd : mInputFds) {
        while (read(fd, &ev, sizeof(ev)) == sizeof(ev)) {
            // Wait-for-release: after a launch is triggered, keep running
            // until the select key is released. This ensures Android's
            // InputReader sees the full press-release cycle before RetroArch
            // gets focus, preventing phantom A-button presses.
            if (mWaitForRelease) {
                if (ev.type == EV_KEY && ev.value == 0
                    && (ev.code == KEY_ENTER || ev.code == BTN_SOUTH)) {
                    ALOGD("NanoMenu: select key released, exiting now");
                    mExitRequested = true;
                }
                continue; // discard all other events while waiting
            }
            // Track SELECT button state; in XMB mode, press refreshes game lists
            if (ev.type == EV_KEY && ev.code == BTN_SELECT) {
                if (ev.value == 1 && mXmbMode) {
                    forceRescanAllSystems();
                }
                mSelectHeld = (ev.value != 0);
            }
            // Power button handling
            if (ev.type == EV_KEY && ev.code == KEY_POWER) {
                if (ev.value == 1) {
                    mPowerPressTime = android::uptimeMillis();
                    // Immediately start polling for long press in a tight loop
                    // so we can shutdown before the hardware cuts power
                    bool shutdown = false;
                    for (int poll = 0; poll < 40; poll++) { // 40 * 50ms = 2s
                        usleep(50000);
                        // Check if key was released
                        struct input_event pe;
                        bool released = false;
                        for (int pfd : mInputFds) {
                            while (read(pfd, &pe, sizeof(pe)) == sizeof(pe)) {
                                if (pe.type == EV_KEY && pe.code == KEY_POWER
                                    && pe.value == 0) {
                                    released = true;
                                }
                            }
                        }
                        if (released) break;
                        if (android::uptimeMillis() - mPowerPressTime > 1500) {
                            // 1.5s hold: trigger shutdown before hardware kills us
                            ALOGI("NanoMenu: power hold 1.5s, shutting down");
                            shutdown = true;
                            break;
                        }
                    }
                    if (shutdown) {
                        mPowerPressTime = 0;
                        prepareShutdown("shutdown");
                        continue;
                    }
                    // Key was released before 1.5s — short press = sleep
                    mPowerPressTime = 0;
                    ALOGI("NanoMenu: power short press, sleeping");
                    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                    glClear(GL_COLOR_BUFFER_BIT);
                    eglSwapBuffers(mDisplay, mSurface);
                    setBrightnessViaHal(0);
                    // Sleep loop: power press to wake, auto-shutdown after 60s
                    bool asleep = true;
                    int64_t sleepStart = android::uptimeMillis();
                    while (asleep) {
                        usleep(100000);
                        if (android::uptimeMillis() - sleepStart > 60000) {
                            ALOGI("NanoMenu: sleep timeout, shutting down");
                            prepareShutdown("shutdown");
                            return;
                        }
                        struct input_event wake;
                        for (int wfd : mInputFds) {
                            while (read(wfd, &wake, sizeof(wake)) == sizeof(wake)) {
                                if (wake.type == EV_KEY && wake.code == KEY_POWER
                                    && wake.value == 1) {
                                    asleep = false;
                                }
                            }
                        }
                    }
                    usleep(200000);
                    { struct input_event d; for (int dfd : mInputFds) {
                        while (read(dfd, &d, sizeof(d)) == sizeof(d)) {} } }
                    setBrightnessViaHal(mBrightness);
                    ALOGI("NanoMenu: woke up");
                }
                continue;
            }
            if (ev.type == EV_KEY && (ev.value == 1 || ev.value == 2)) {
                // Volume keys: SELECT+VOL = brightness, VOL alone = volume
                if (ev.code == KEY_VOLUMEUP || ev.code == KEY_VOLUMEDOWN) {
                    if (mSelectHeld) {
                        adjustBrightness(ev.code == KEY_VOLUMEUP ? 1 : -1);
                    } else if (ev.value == 1) {
                        adjustVolume(ev.code == KEY_VOLUMEUP ? 1 : -1);
                    }
                    continue;
                }
                if (ev.value == 1) {
                    // Only handle menu nav on initial press, not repeat
                    switch (ev.code) {
                    case KEY_UP:
                        handleUp(); break;
                    case KEY_DOWN:
                        handleDown(); break;
                    case BTN_SOUTH:
                        handleSelect(); break;
                    case KEY_ENTER:
                        if (mOskActive) oskConfirm();
                        else handleSelect();
                        break;
                    case BTN_EAST: case KEY_BACK:
                        handleBack(); break;
                    case KEY_LEFT:
                        handleLeft(); break;
                    case KEY_RIGHT:
                        handleRight(); break;
                    case BTN_WEST: // Y button (Nintendo layout: BTN_WEST = Y)
                        if (mXmbMode) {
                            // Y: search in XMB mode
                            if (mOskActive) {
                                closeOsk();
                            } else if (mSearchActive) {
                                mOskActive = true;
                            } else {
                                openOsk();
                            }
                        } else {
                            // Y: cycle wallpaper in list mode
                            sActiveEffectIdx = (sActiveEffectIdx + 1) % kNumActiveEffects;
                            mCurrentEffect = kActiveEffects[sActiveEffectIdx];
                            if (mCurrentEffect >= 1 && mCurrentEffect <= 10) initEffects();
                            mDisplayDirty = true;
                            ALOGD("Effect: %d (%s)", mCurrentEffect, kEffectNames[mCurrentEffect]);
                            { char buf[16]; snprintf(buf, sizeof(buf), "%d", mCurrentEffect);
                              property_set("persist.gammaos.nano.wallpaper", buf); }
                        }
                        break;
                    case BTN_NORTH: // X button (Nintendo layout: BTN_NORTH = X)
                        // X: cycle wallpaper/FX
                        sActiveEffectIdx = (sActiveEffectIdx + 1) % kNumActiveEffects;
                        mCurrentEffect = kActiveEffects[sActiveEffectIdx];
                        if (mCurrentEffect >= 1 && mCurrentEffect <= 10) initEffects();
                        mDisplayDirty = true;
                        ALOGD("Effect: %d (%s)", mCurrentEffect, kEffectNames[mCurrentEffect]);
                        { char buf[16]; snprintf(buf, sizeof(buf), "%d", mCurrentEffect);
                          property_set("persist.gammaos.nano.wallpaper", buf); }
                        break;
                    case BTN_TL: case KEY_L:
                        if (mOskActive) break;
                        mXmbMode = !mXmbMode;
                        property_set("persist.gammaos.nano.xmb_mode",
                                     mXmbMode ? "1" : "0");
                        if (!mXmbMode) {
                            mMenuState = MENU_MAIN;
                            mSearchActive = false;
                            mOskActive = false;
                        }
                        mDisplayDirty = true;
                        ALOGD("XMB Mode: %s", mXmbMode ? "ON" : "OFF");
                        break;
                    case BTN_TR: case KEY_R:
                        mQuickResumeEnabled = !mQuickResumeEnabled;
                        property_set("persist.gammaos.nano.quick_resume",
                                     mQuickResumeEnabled ? "1" : "0");
                        mDisplayDirty = true;
                        ALOGD("Quick Resume: %s", mQuickResumeEnabled ? "ON" : "OFF");
                        break;
                    default: break;
                    }
                }
            }
            if (ev.type == EV_ABS) {
                if (ev.code == ABS_HAT0X) {
                    if (ev.value < 0) handleLeft();
                    else if (ev.value > 0) handleRight();
                } else if (ev.code == ABS_HAT0Y) {
                    if (ev.value < 0) handleUp();
                    else if (ev.value > 0) handleDown();
                } else if (ev.code == ABS_X) {
                    // Left stick X: horizontal navigation
                    int threshold = 29490; // 90% of 32767
                    if (ev.value < -threshold && !mStickXTriggered) {
                        handleLeft();
                        mStickXTriggered = true;
                    } else if (ev.value > threshold && !mStickXTriggered) {
                        handleRight();
                        mStickXTriggered = true;
                    } else if (ev.value > -threshold && ev.value < threshold) {
                        mStickXTriggered = false;
                    }
                } else if (ev.code == ABS_Y) {
                    // Left stick Y: signed range -32768..32767, 90% deadzone
                    // Threshold naturally filters touchscreen ABS_Y (max ~960)
                    int threshold = 29490; // 90% of 32767
                    if (ev.value < -threshold && !mStickYTriggered) {
                        handleUp();
                        mStickYTriggered = true;
                    } else if (ev.value > threshold && !mStickYTriggered) {
                        handleDown();
                        mStickYTriggered = true;
                    } else if (ev.value > -threshold && ev.value < threshold) {
                        mStickYTriggered = false;
                    }
                }
            }
        }
    }
}

void NanoMenu::checkInputHotplug() {
    if (mInotifyFd < 0) return;
    char buf[512] __attribute__((aligned(__alignof__(struct inotify_event))));
    ssize_t len = read(mInotifyFd, buf, sizeof(buf));
    if (len > 0) {
        for (char* ptr = buf; ptr < buf + len; ) {
            auto* ev = reinterpret_cast<struct inotify_event*>(ptr);
            if (ev->len > 0 && strncmp(ev->name, "event", 5) == 0) {
                if (ev->mask & IN_DELETE) {
                    // Device node was removed (gammapad hides+recreates devices).
                    // Close our stale fd and forget it so we re-grab on IN_CREATE.
                    //
                    // BUG fix: only match the fd whose path corresponds to the
                    // deleted device. The previous code OR'd against any
                    // "(deleted)" fd, which closed the wrong fd when multiple
                    // devices were being torn down nearly simultaneously
                    // (gammapad's swap of event12 races with EventHub removing
                    // event10, etc). The result was that nano permanently lost
                    // the Xbox controller because event12's fd got closed by
                    // event10's IN_DELETE event, leaving stale state in
                    // mOpenedDevices that suppressed IN_CREATE re-opening.
                    if (mOpenedDevices.count(ev->name)) {
                        char path[PATH_MAX];
                        snprintf(path, sizeof(path), "/dev/input/%s", ev->name);
                        for (auto it = mInputFds.begin(); it != mInputFds.end(); ++it) {
                            char fdPath[PATH_MAX];
                            char procLink[64];
                            snprintf(procLink, sizeof(procLink), "/proc/self/fd/%d", *it);
                            ssize_t rl = readlink(procLink, fdPath, sizeof(fdPath) - 1);
                            if (rl > 0) {
                                fdPath[rl] = '\0';
                                // Match only by exact device name. The path may
                                // also have " (deleted)" appended once the kernel
                                // marks it removed; tolerate that suffix.
                                char wantPath[PATH_MAX];
                                snprintf(wantPath, sizeof(wantPath), "/dev/input/%s", ev->name);
                                size_t wantLen = strlen(wantPath);
                                if (strncmp(fdPath, wantPath, wantLen) == 0 &&
                                        (fdPath[wantLen] == '\0' ||
                                         fdPath[wantLen] == ' ')) {
                                    ioctl(*it, EVIOCGRAB, 0);
                                    close(*it);
                                    mInputFds.erase(it);
                                    break;
                                }
                            }
                        }
                        mOpenedDevices.erase(ev->name);
                        ALOGI("Device removed, dropped stale fd: %s", path);
                    }
                } else if ((ev->mask & IN_CREATE) && !mOpenedDevices.count(ev->name)) {
                    // Small delay for the device node to be fully ready
                    usleep(100000); // 100ms
                    char path[PATH_MAX];
                    snprintf(path, sizeof(path), "/dev/input/%s", ev->name);
                    int fd = open(path, O_RDONLY | O_NONBLOCK);
                    if (fd >= 0) {
                        if (android::base::GetBoolProperty("persist.gammaos.nano.grab_input", false)) {
                            if (ioctl(fd, EVIOCGRAB, 1) < 0) {
                                ALOGW("EVIOCGRAB failed for hotplugged %s: %s", path, strerror(errno));
                            }
                        }
                        mInputFds.push_back(fd);
                        mOpenedDevices.insert(ev->name);
                        ALOGI("Hotplugged + grabbed input device: %s", path);
                    }
                }
            }
            ptr += sizeof(struct inotify_event) + ev->len;
        }
    }

    // GammaOS: defensive sweep for missed devices. The inotify handler above
    // can lose track of devices when multiple are torn down/recreated nearly
    // simultaneously (typical with gammapad swaps). Periodically rescan
    // /dev/input and reconcile against mOpenedDevices: open anything missing,
    // and drop any fd that has gone "(deleted)" without a matching IN_DELETE.
    // Ratelimited to once per second to keep cost negligible.
    static int64_t sLastSweepNs = 0;
    int64_t nowNs = systemTime(SYSTEM_TIME_MONOTONIC);
    if (nowNs - sLastSweepNs < 1000000000LL) return;
    sLastSweepNs = nowNs;

    // (1) Drop any of our fds that point to a deleted inode. This handles
    // the case where gammapad recreated a device under the same name without
    // us seeing the IN_DELETE.
    for (auto it = mInputFds.begin(); it != mInputFds.end(); ) {
        char fdPath[PATH_MAX];
        char procLink[64];
        snprintf(procLink, sizeof(procLink), "/proc/self/fd/%d", *it);
        ssize_t rl = readlink(procLink, fdPath, sizeof(fdPath) - 1);
        if (rl > 0) {
            fdPath[rl] = '\0';
            if (strstr(fdPath, "(deleted)")) {
                // Recover the device name from the path so we can also
                // erase it from mOpenedDevices and let the rescan re-open it.
                const char* base = strrchr(fdPath, '/');
                if (base) {
                    base++;
                    char nameOnly[64];
                    size_t i = 0;
                    while (base[i] && base[i] != ' ' && i < sizeof(nameOnly) - 1) {
                        nameOnly[i] = base[i];
                        i++;
                    }
                    nameOnly[i] = '\0';
                    mOpenedDevices.erase(nameOnly);
                    ALOGI("Sweep: dropped stale fd %s", fdPath);
                }
                ioctl(*it, EVIOCGRAB, 0);
                close(*it);
                it = mInputFds.erase(it);
                continue;
            }
        }
        ++it;
    }

    // (2) Open any /dev/input/event* that we don't currently have.
    DIR* dir = opendir("/dev/input");
    if (!dir) return;
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (strncmp(entry->d_name, "event", 5) != 0) continue;
        if (mOpenedDevices.count(entry->d_name)) continue;
        char path[PATH_MAX];
        snprintf(path, sizeof(path), "/dev/input/%s", entry->d_name);
        int fd = open(path, O_RDONLY | O_NONBLOCK);
        if (fd >= 0) {
            if (android::base::GetBoolProperty("persist.gammaos.nano.grab_input", false)) {
                if (ioctl(fd, EVIOCGRAB, 1) < 0) {
                    ALOGW("EVIOCGRAB failed for swept %s: %s", path, strerror(errno));
                }
            }
            mInputFds.push_back(fd);
            mOpenedDevices.insert(entry->d_name);
            ALOGI("Sweep: opened previously-missed input device %s", path);
        }
    }
    closedir(dir);
}

// ---------------------------------------------------------------------------
// Effects: particle system (effects 1-10)
// ---------------------------------------------------------------------------

void NanoMenu::resetParticle(int i) {
    Particle& p = mParticles[i];
    float w = (float)mWidth;
    float h = (float)mHeight;
    p.phase = randf(0.0f, 6.28f);

    switch (mCurrentEffect) {
    case 1: // Snow
        p.x = randf(0, w); p.y = randf(-h, 0);
        p.vx = randf(-0.3f, 0.3f); p.vy = randf(0.5f, 2.0f);
        p.size = randf(4, 10); p.r = 1; p.g = 1; p.b = 1; p.a = randf(0.15f, 0.4f);
        break;
    case 2: // Rain
        p.x = randf(0, w); p.y = randf(-h, 0);
        p.vx = randf(0.5f, 1.5f); p.vy = randf(8.0f, 16.0f);
        p.size = randf(2, 4); p.r = 0.5f; p.g = 0.7f; p.b = 1; p.a = randf(0.1f, 0.3f);
        break;
    case 3: // Confetti
        p.x = randf(0, w); p.y = randf(-h, 0);
        p.vx = randf(-1, 1); p.vy = randf(0.5f, 3.0f);
        p.size = randf(6, 14); p.r = randf(0.3f,1); p.g = randf(0.3f,1); p.b = randf(0.3f,1);
        p.a = randf(0.15f, 0.35f);
        break;
    case 4: // Sparks
        p.x = randf(w*0.2f, w*0.8f); p.y = h;
        p.vx = randf(-2, 2); p.vy = randf(-8.0f, -3.0f);
        p.size = randf(4, 8); p.r = 1; p.g = randf(0.4f, 0.8f); p.b = 0.1f;
        p.a = randf(0.2f, 0.5f); p.life = 1.0f;
        break;
    case 5: // Fireflies
        p.x = randf(0, w); p.y = randf(0, h);
        p.vx = randf(-0.5f, 0.5f); p.vy = randf(-0.5f, 0.5f);
        p.size = randf(4, 10); p.r = 0.8f; p.g = 1.0f; p.b = 0.3f;
        p.a = randf(0.05f, 0.3f);
        break;
    case 6: // Bubbles
        p.x = randf(0, w); p.y = h + randf(0, h);
        p.vx = randf(-0.3f, 0.3f); p.vy = randf(-3.0f, -1.0f);
        p.size = randf(8, 20); p.r = 0.6f; p.g = 0.8f; p.b = 1.0f; p.a = randf(0.08f, 0.2f);
        break;
    case 7: // Starfield
        p.x = w * 0.5f; p.y = h * 0.5f;
        { float angle = randf(0, 6.28f); float speed = randf(1, 6);
          p.vx = cosf(angle) * speed; p.vy = sinf(angle) * speed; }
        p.size = randf(2, 6); p.r = 1; p.g = 1; p.b = 1; p.a = 0.05f; p.life = 0.0f;
        break;
    case 8: // Embers
        p.x = randf(0, w); p.y = h + randf(0, 100);
        p.vx = randf(-0.5f, 0.5f); p.vy = randf(-2.0f, -0.5f);
        p.size = randf(4, 8); p.r = 1; p.g = randf(0.2f, 0.5f); p.b = 0;
        p.a = randf(0.1f, 0.35f); p.life = 1.0f;
        break;
    case 9: // Leaves
        p.x = randf(0, w); p.y = randf(-h, 0);
        p.vx = randf(-1, 1); p.vy = randf(0.5f, 2.0f);
        p.size = randf(6, 14); p.r = randf(0.3f, 0.6f); p.g = randf(0.5f, 0.8f); p.b = 0.1f;
        p.a = randf(0.1f, 0.25f);
        break;
    case 10: // Dust (disabled but keep code)
        p.x = randf(0, w); p.y = randf(0, h);
        p.vx = randf(-0.2f, 0.2f); p.vy = randf(-0.2f, 0.2f);
        p.size = randf(1, 3); p.r = 0.7f; p.g = 0.7f; p.b = 0.7f; p.a = randf(0.05f, 0.15f);
        break;
    default:
        p.x = -100; p.y = -100; p.vx = 0; p.vy = 0; p.a = 0; break;
    }
}

void NanoMenu::initEffects() {
    for (int i = 0; i < MAX_PARTICLES; i++) resetParticle(i);
}

void NanoMenu::updateEffect() {
    if (mCurrentEffect < 1 || mCurrentEffect > 10) return;
    float w = (float)mWidth;
    float h = (float)mHeight;

    for (int i = 0; i < MAX_PARTICLES; i++) {
        Particle& p = mParticles[i];
        p.x += p.vx;
        p.y += p.vy;
        p.phase += 0.05f;

        // Per-effect behaviour
        switch (mCurrentEffect) {
        case 1: // Snow: gentle sway
            p.vx = sinf(p.phase) * 0.5f;
            if (p.y > h) resetParticle(i);
            break;
        case 2: // Rain
            if (p.y > h) resetParticle(i);
            break;
        case 3: // Confetti: sway
            p.vx = sinf(p.phase * 2.0f) * 1.0f;
            if (p.y > h) resetParticle(i);
            break;
        case 4: // Sparks: gravity
            p.vy += 0.15f;
            p.life -= 0.008f;
            p.a = p.life * 0.4f;
            if (p.life <= 0 || p.y > h) resetParticle(i);
            break;
        case 5: // Fireflies: wander
            p.vx += randf(-0.15f, 0.15f); p.vy += randf(-0.15f, 0.15f);
            p.vx *= 0.98f; p.vy *= 0.98f;
            p.a = (sinf(p.phase * 3.0f) * 0.5f + 0.5f) * 0.25f;
            if (p.x < 0) p.x = w; if (p.x > w) p.x = 0;
            if (p.y < 0) p.y = h; if (p.y > h) p.y = 0;
            break;
        case 6: // Bubbles: rise with wobble
            p.vx = sinf(p.phase) * 0.4f;
            if (p.y < -p.size) resetParticle(i);
            break;
        case 7: // Starfield: accelerate outward
            p.vx *= 1.02f; p.vy *= 1.02f;
            p.life += 0.01f;
            p.a = fminf(p.life, 0.4f);
            p.size = 1.0f + p.life * 2.0f;
            if (p.x < 0 || p.x > w || p.y < 0 || p.y > h) resetParticle(i);
            break;
        case 8: // Embers: drift and fade
            p.vx += randf(-0.05f, 0.05f);
            p.life -= 0.005f;
            p.a = p.life * 0.3f;
            if (p.life <= 0 || p.y < 0) resetParticle(i);
            break;
        case 9: // Leaves: sway and spin
            p.vx = sinf(p.phase * 1.5f) * 1.5f;
            if (p.y > h) resetParticle(i);
            break;
        case 10: // Dust: brownian wander
            p.vx += randf(-0.05f, 0.05f); p.vy += randf(-0.05f, 0.05f);
            p.vx *= 0.99f; p.vy *= 0.99f;
            if (p.x < 0) p.x = w; if (p.x > w) p.x = 0;
            if (p.y < 0) p.y = h; if (p.y > h) p.y = 0;
            break;
        }
    }
}

// GammaOS: DRM state variables needed by renderEffect (before the main DRM section).
static bool sDrmActive = false;
static int sDrmRotationDeg = 0;

void NanoMenu::renderEffect() {
    if (mCurrentEffect == 0) return;

    if (mCurrentEffect >= 1 && mCurrentEffect <= 10) {
        // Batched particle rendering: build one vertex+color buffer, single draw call
        static GLfloat pVerts[MAX_PARTICLES * 6 * 2];
        static GLfloat pColors[MAX_PARTICLES * 6 * 4];
        float invW = 2.0f / mWidth, invH = 2.0f / mHeight;
        int n = 0;
        for (int i = 0; i < MAX_PARTICLES; i++) {
            const Particle& p = mParticles[i];
            if (p.a <= 0.0f) continue;
            float hs = p.size * 0.5f;
            float x0 = (p.x - hs) * invW - 1.0f;
            float y0 = 1.0f - (p.y + hs) * invH;
            float x1 = (p.x + hs) * invW - 1.0f;
            float y1 = 1.0f - (p.y - hs) * invH;
            int vi = n * 12;
            pVerts[vi]= x0; pVerts[vi+1]= y0; pVerts[vi+2]= x1; pVerts[vi+3]= y0;
            pVerts[vi+4]= x1; pVerts[vi+5]= y1; pVerts[vi+6]= x1; pVerts[vi+7]= y1;
            pVerts[vi+8]= x0; pVerts[vi+9]= y1; pVerts[vi+10]= x0; pVerts[vi+11]= y0;
            int ci = n * 24;
            for (int v = 0; v < 6; v++) {
                pColors[ci + v*4] = p.r; pColors[ci + v*4+1] = p.g;
                pColors[ci + v*4+2] = p.b; pColors[ci + v*4+3] = p.a;
            }
            n++;
        }
        if (n > 0) {
            glUseProgram(mParticleProgram);
            glVertexAttribPointer(mParticleLocPosition, 2, GL_FLOAT, GL_FALSE, 0, pVerts);
            glEnableVertexAttribArray(mParticleLocPosition);
            glVertexAttribPointer(mParticleLocColor, 4, GL_FLOAT, GL_FALSE, 0, pColors);
            glEnableVertexAttribArray(mParticleLocColor);
            glDrawArrays(GL_TRIANGLES, 0, n * 6);
            glDisableVertexAttribArray(mParticleLocPosition);
            glDisableVertexAttribArray(mParticleLocColor);
        }
    } else if (mCurrentEffect >= 11 && mCurrentEffect <= 20) {
        // Fullscreen procedural shader
        GLfloat verts[] = { -1,-1, 1,-1, 1,1, 1,1, -1,1, -1,-1 };
        // GammaOS: When GL rotation is active, gl_FragCoord is in panel-native
        // pixel space but the shader effect should render in logical orientation.
        // uCoordSwap=1.0 tells the fragment shader to swap gl_FragCoord.xy → .yx
        // so the UV mapping matches the logical dimensions passed in uResolution.
        float coordSwap = (sDrmActive && (sDrmRotationDeg == 90
                           || sDrmRotationDeg == 270)) ? 1.0f : 0.0f;
        glUseProgram(mFxProgram);
        glUniform1f(mFxLocTime, mEffectTime);
        glUniform2f(mFxLocResolution, (float)mWidth, (float)mHeight);
        glUniform1i(mFxLocEffect, mCurrentEffect);
        glUniform1f(mFxLocCoordSwap, coordSwap);
        glVertexAttribPointer(mFxLocPosition, 2, GL_FLOAT, GL_FALSE, 0, verts);
        glEnableVertexAttribArray(mFxLocPosition);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glDisableVertexAttribArray(mFxLocPosition);
    } else if (mCurrentEffect == 21) {
        // XMB: PS3-style volumetric ribbon background (dedicated shader, 60fps)
        GLfloat verts[] = { -1,-1, 1,-1, 1,1, 1,1, -1,1, -1,-1 };
        float coordSwap = (sDrmActive && (sDrmRotationDeg == 90
                           || sDrmRotationDeg == 270)) ? 1.0f : 0.0f;
        glUseProgram(mXmbProgram);
        glUniform1f(mXmbLocTime, mEffectTime);
        glUniform2f(mXmbLocResolution, (float)mWidth, (float)mHeight);
        glUniform1f(mXmbLocCoordSwap, coordSwap);
        glVertexAttribPointer(mXmbLocPosition, 2, GL_FLOAT, GL_FALSE, 0, verts);
        glEnableVertexAttribArray(mXmbLocPosition);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glDisableVertexAttribArray(mXmbLocPosition);
    }
}

// ---------------------------------------------------------------------------
// EGL setup
// ---------------------------------------------------------------------------

static EGLConfig getEglConfig(const EGLDisplay& display) {
    EGLint attribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 0, EGL_DEPTH_SIZE, 0, EGL_NONE
    };
    EGLint numConfigs = 0;
    EGLConfig config = nullptr;
    eglChooseConfig(display, attribs, &config, 1, &numConfigs);
    return config;
}

// GammaOS: Direct DRM framebuffer for instant boot display.
// On dual-DSI devices HWC doesn't present SF buffers until DMS fully configures
// displays (~25s into boot). This writes directly to DRM to show content immediately.
// After the initial splash, render() pushes GL frames to the DRM buffer via glReadPixels
// so the NanoMenu is visible within seconds of kernel start.
struct DrmBuffer {
    uint32_t handle;
    uint32_t fbId;
    uint32_t pitch;
    size_t size;
    void* mapped;
    // Zero-copy GPU→DRM: DMA-BUF fd + EGLImage + GL texture + FBO for direct rendering
    int dmaFd;
    EGLImageKHR eglImage;
    GLuint glTexture;
    GLuint glFbo;
};
struct DrmDisplay {
    uint32_t crtcId;
    uint32_t connId;
    uint32_t w, h;
    DrmBuffer buffers[2];  // double buffer
    int activeBuffer;       // index currently being displayed
    struct drm_mode_modeinfo mode;
};
static int sDrmFd = -1;
static std::vector<DrmDisplay> sDrmDisplays;
// sDrmActive and sDrmRotationDeg are defined earlier (before renderEffect)
static bool sDrmZeroCopy = false; // true if AHB/FBO setup succeeded
static bool sDrmGlRotation = false; // true when GL applies rotation (blit uses 0° path)
// GL rotation matrix (column-major for GLES2 uniformMatrix2fv)
static float sDrmRotMat[4] = {1.0f, 0.0f, 0.0f, 1.0f}; // identity

// EGL extensions for zero-copy path
static PFNEGLCREATEIMAGEKHRPROC sEglCreateImageKHR = nullptr;
static PFNEGLDESTROYIMAGEKHRPROC sEglDestroyImageKHR = nullptr;
static PFNGLEGLIMAGETARGETTEXTURE2DOESPROC sGlEGLImageTargetTexture2DOES = nullptr;
typedef EGLClientBuffer (EGLAPIENTRYP PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC) (const struct AHardwareBuffer *buffer);
static PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC sEglGetNativeClientBufferANDROID = nullptr;

// AHardwareBuffer-backed GPU render targets for zero-copy DRM output.
// sAhbTarget is the PRIMARY render target (wallpaper + XMB/menu), blitted to
// the display selected by persist.gammaos.nano.primary_display.
// sAhbTargetSecondary is the SECONDARY render target (wallpaper only, no menu),
// blitted to every non-primary display. This lets the non-XMB screen(s) show
// just the wallpaper without pulling menu geometry through.
struct AhbRenderTarget {
    AHardwareBuffer* ahb;
    EGLImageKHR eglImage;
    GLuint glTexture;
    GLuint glFbo;
    uint32_t w, h;
};
static AhbRenderTarget sAhbTarget = {};
static AhbRenderTarget sAhbTargetSecondary = {};
// Index into sDrmDisplays that receives the primary AHB. Selected from
// persist.gammaos.nano.primary_display (defaults to 0 = first enumerated CRTC).
static int sDrmPrimaryIdx = 0;
// Deadline for the late-display re-probe (monotonic ns). drmRescanDisplays()
// is a no-op once the boot window has expired, to avoid wasted ioctls
// forever on single-display hardware.
static int64_t sDrmRescanDeadlineNs = 0;

// Create and map a double-buffered dumb buffer pair for a DRM CRTC. Shared
// by drmEarlySplash (first-pass enumeration) and drmRescanDisplays (late
// re-probe for displays that weren't ready at splash time).
static bool drmCreateDumbBuffer(int fd, uint32_t w, uint32_t h, DrmBuffer* out) {
    struct drm_mode_create_dumb create = {};
    create.width = w; create.height = h; create.bpp = 32;
    if (ioctl(fd, DRM_IOCTL_MODE_CREATE_DUMB, &create) != 0) return false;

    struct drm_mode_map_dumb mapReq = {};
    mapReq.handle = create.handle;
    if (ioctl(fd, DRM_IOCTL_MODE_MAP_DUMB, &mapReq) != 0) return false;

    void* mapped = mmap(nullptr, create.size, PROT_READ | PROT_WRITE,
                       MAP_SHARED, fd, mapReq.offset);
    if (mapped == MAP_FAILED) return false;

    // Fill with dark background so the display isn't garbage on first scanout
    uint32_t* px = (uint32_t*)mapped;
    for (uint32_t i = 0; i < w * h; i++) px[i] = 0xFF1A0D0D;

    struct drm_mode_fb_cmd fbCmd = {};
    fbCmd.width = w; fbCmd.height = h;
    fbCmd.pitch = create.pitch; fbCmd.bpp = 32; fbCmd.depth = 24;
    fbCmd.handle = create.handle;
    if (ioctl(fd, DRM_IOCTL_MODE_ADDFB, &fbCmd) != 0) {
        munmap(mapped, create.size); return false;
    }
    out->handle = create.handle;
    out->fbId = fbCmd.fb_id;
    out->pitch = create.pitch;
    out->size = create.size;
    out->mapped = mapped;
    out->dmaFd = -1;
    out->eglImage = EGL_NO_IMAGE_KHR;
    out->glTexture = 0;
    out->glFbo = 0;
    return true;
}

// Try to bring up a single DRM CRTC with the given connector. Returns true
// if the CRTC was added to sDrmDisplays. Non-blocking: if the mode is not
// valid (display not ready), returns false immediately — caller can retry
// later via drmRescanDisplays().
static bool drmTryAddDisplay(int fd, uint32_t crtcId, uint32_t connId, const char* stage) {
    struct drm_mode_crtc crtc = {};
    crtc.crtc_id = crtcId;
    if (ioctl(fd, DRM_IOCTL_MODE_GETCRTC, &crtc) != 0 || !crtc.mode_valid) {
        return false;
    }

    uint32_t w = crtc.mode.hdisplay, h = crtc.mode.vdisplay;
    DrmBuffer buf0, buf1;
    if (!drmCreateDumbBuffer(fd, w, h, &buf0)) return false;
    if (!drmCreateDumbBuffer(fd, w, h, &buf1)) {
        munmap(buf0.mapped, buf0.size);
        return false;
    }

    crtc.fb_id = buf0.fbId;
    crtc.set_connectors_ptr = (uint64_t)(uintptr_t)&connId;
    crtc.count_connectors = 1;
    int ret = ioctl(fd, DRM_IOCTL_MODE_SETCRTC, &crtc);

    int64_t now = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
    ALOGW("NanoMenu DRM %s: crtc %u (%ux%u) conn %u → %s at T+%lldms",
          stage, crtcId, w, h, connId, ret == 0 ? "OK" : strerror(errno), now);

    if (ret != 0) {
        munmap(buf0.mapped, buf0.size);
        munmap(buf1.mapped, buf1.size);
        return false;
    }

    DrmDisplay d = {};
    d.crtcId = crtcId; d.connId = connId;
    d.w = w; d.h = h;
    d.buffers[0] = buf0;
    d.buffers[1] = buf1;
    d.activeBuffer = 0;
    d.mode = crtc.mode;
    sDrmDisplays.push_back(d);
    return true;
}

// Re-probe DRM CRTCs that weren't ready at drmEarlySplash() time. Called
// periodically from the main loop so a slow-to-come-up display can be
// brought in without blocking the fast path. Bounded by sDrmRescanDeadlineNs
// so we stop burning ioctls after the boot window.
static void drmRescanDisplays() {
    if (sDrmFd < 0) return;
    if (sDrmRescanDeadlineNs == 0) return;
    if (systemTime(SYSTEM_TIME_MONOTONIC) > sDrmRescanDeadlineNs) {
        sDrmRescanDeadlineNs = 0; // disable further scans
        return;
    }

    struct drm_mode_card_res res = {};
    if (ioctl(sDrmFd, DRM_IOCTL_MODE_GETRESOURCES, &res) != 0 || res.count_crtcs == 0) {
        return;
    }
    uint32_t numCrtcs = res.count_crtcs, numConns = res.count_connectors;
    std::vector<uint32_t> crtcs(numCrtcs), connectors(numConns);
    struct drm_mode_card_res res2 = {};
    res2.count_crtcs = numCrtcs;
    res2.count_connectors = numConns;
    res2.crtc_id_ptr = (uint64_t)(uintptr_t)crtcs.data();
    res2.connector_id_ptr = (uint64_t)(uintptr_t)connectors.data();
    if (ioctl(sDrmFd, DRM_IOCTL_MODE_GETRESOURCES, &res2) != 0) return;

    for (uint32_t c = 0; c < numCrtcs && c < 2; c++) {
        // Skip CRTCs already in our display list.
        bool already = false;
        for (const auto& d : sDrmDisplays) {
            if (d.crtcId == crtcs[c]) { already = true; break; }
        }
        if (already) continue;

        uint32_t connId = (c < numConns) ? connectors[c] : 0;
        if (drmTryAddDisplay(sDrmFd, crtcs[c], connId, "rescan")) {
            ALOGW("NanoMenu DRM rescan: brought up late CRTC %u (now %zu displays)",
                  crtcs[c], sDrmDisplays.size());
            // A new display came up. We don't re-allocate the secondary AHB
            // here because the GL context for AHB allocation lives on the
            // render thread and drmRescanDisplays is called from the main
            // loop, which IS the render thread — but drmSetupZeroCopy uses
            // the EGL display. The simplest behavior: leave AHB setup alone.
            // The new display will mirror the primary (sAhbTarget) via the
            // fallback path in drmFlipAll. Good enough for the edge case.
        }
    }
}

static void drmEarlySplash() {
    int64_t t0 = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
    int fd = open("/dev/dri/card0", O_RDWR | O_CLOEXEC);
    if (fd < 0) return;

    ioctl(fd, DRM_IOCTL_SET_MASTER, 0); // try, OK if fails

    // Get DRM resources
    struct drm_mode_card_res res = {};
    if (ioctl(fd, DRM_IOCTL_MODE_GETRESOURCES, &res) != 0 || res.count_crtcs == 0) {
        close(fd); return;
    }
    uint32_t numCrtcs = res.count_crtcs, numConns = res.count_connectors;
    std::vector<uint32_t> crtcs(numCrtcs), connectors(numConns);
    struct drm_mode_card_res res2 = {};
    res2.count_crtcs = numCrtcs;
    res2.count_connectors = numConns;
    res2.crtc_id_ptr = (uint64_t)(uintptr_t)crtcs.data();
    res2.connector_id_ptr = (uint64_t)(uintptr_t)connectors.data();
    if (ioctl(fd, DRM_IOCTL_MODE_GETRESOURCES, &res2) != 0) { close(fd); return; }

    sDrmFd = fd;

    // Enumerate CRTCs independently — a slow display does NOT hold back a
    // fast one. Any CRTC that isn't ready here is retried by drmRescanDisplays().
    int attempted = 0, addedCount = 0;
    for (uint32_t c = 0; c < numCrtcs && c < 2; c++) {
        attempted++;
        uint32_t connId = (c < numConns) ? connectors[c] : 0;
        if (drmTryAddDisplay(fd, crtcs[c], connId, "splash")) addedCount++;
    }
    sDrmActive = !sDrmDisplays.empty();
    if (sDrmActive) {
        ALOGW("NanoMenu DRM splash: %d/%d CRTCs active for direct rendering",
              addedCount, attempted);
    }

    // Enable late-display re-probe for the first 5 seconds of the process.
    // This is a bounded window: if a panel hasn't come up by then, it is
    // either broken or never going to, so we stop spending ioctls on it.
    sDrmRescanDeadlineNs = systemTime(SYSTEM_TIME_MONOTONIC) + 5000000000LL;

    // GammaOS: Read persist.gammaos.nano.primary_display to choose which
    // enumerated CRTC receives the XMB/menu AHB. All other displays receive
    // wallpaper-only output via sAhbTargetSecondary. The property value is a
    // CRTC enumeration index (0 = first, 1 = second, ...). Invalid values
    // fall back to 0. This mirrors the same property used post-boot to pick
    // the physical display port for the EGL/SurfaceFlinger path.
    {
        char primaryProp[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.primary_display", primaryProp, "0");
        int wantIdx = atoi(primaryProp);
        if (wantIdx < 0 || wantIdx >= (int)sDrmDisplays.size()) {
            wantIdx = 0;
        }
        sDrmPrimaryIdx = wantIdx;
        if (sDrmDisplays.size() > 1) {
            ALOGW("NanoMenu DRM: primary_display prop='%s' → CRTC index %d (of %zu)",
                  primaryProp, sDrmPrimaryIdx, sDrmDisplays.size());
        }
    }

    // GammaOS: Read the install orientation so DRM direct rendering can rotate
    // content to match the physical panel orientation. Without this, DRM direct
    // rendering outputs unrotated content and the menu appears sideways on devices
    // with a rotated install orientation (e.g. portrait panel used landscape).
    {
        char orient[PROPERTY_VALUE_MAX] = {};
        property_get("ro.surface_flinger.primary_display_orientation", orient, "");
        if (!strcmp(orient, "ORIENTATION_90")) sDrmRotationDeg = 90;
        else if (!strcmp(orient, "ORIENTATION_180")) sDrmRotationDeg = 180;
        else if (!strcmp(orient, "ORIENTATION_270")) sDrmRotationDeg = 270;
        else sDrmRotationDeg = 0;
        if (sDrmRotationDeg != 0) {
            ALOGI("NanoMenu DRM: installOrientation=%s → rotate %d°",
                  orient, sDrmRotationDeg);
        }
    }
}

// Build an AHB-backed FBO of the given dimensions. Fills out `target` on
// success; leaves it zeroed and returns false on failure. Factored out so we
// can allocate both primary and secondary render targets from the same code.
static bool drmAllocAhbTarget(EGLDisplay eglDpy, uint32_t w, uint32_t h,
                               AhbRenderTarget* target, const char* label) {
    AHardwareBuffer_Desc desc = {};
    desc.width = w;
    desc.height = h;
    desc.layers = 1;
    desc.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_FRAMEBUFFER |
                 AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE |
                 AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN;
    if (AHardwareBuffer_allocate(&desc, &target->ahb) != 0 || !target->ahb) {
        ALOGW("NanoMenu DRM zero-copy: AHardwareBuffer_allocate(%s) failed", label);
        return false;
    }

    EGLClientBuffer clientBuf = sEglGetNativeClientBufferANDROID(target->ahb);
    if (!clientBuf) {
        ALOGW("NanoMenu DRM zero-copy: eglGetNativeClientBufferANDROID(%s) failed", label);
        AHardwareBuffer_release(target->ahb); target->ahb = nullptr;
        return false;
    }

    EGLint imgAttrs[] = { EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE };
    target->eglImage = sEglCreateImageKHR(eglDpy, EGL_NO_CONTEXT,
                                           EGL_NATIVE_BUFFER_ANDROID, clientBuf, imgAttrs);
    if (target->eglImage == EGL_NO_IMAGE_KHR) {
        ALOGW("NanoMenu DRM zero-copy: eglCreateImageKHR(%s) failed", label);
        AHardwareBuffer_release(target->ahb); target->ahb = nullptr;
        return false;
    }

    glGenTextures(1, &target->glTexture);
    glBindTexture(GL_TEXTURE_2D, target->glTexture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    sGlEGLImageTargetTexture2DOES(GL_TEXTURE_2D, (GLeglImageOES)target->eglImage);
    if (glGetError() != GL_NO_ERROR) {
        ALOGW("NanoMenu DRM zero-copy: glEGLImageTargetTexture2DOES(%s) failed", label);
        glDeleteTextures(1, &target->glTexture); target->glTexture = 0;
        sEglDestroyImageKHR(eglDpy, target->eglImage); target->eglImage = EGL_NO_IMAGE_KHR;
        AHardwareBuffer_release(target->ahb); target->ahb = nullptr;
        return false;
    }

    glGenFramebuffers(1, &target->glFbo);
    glBindFramebuffer(GL_FRAMEBUFFER, target->glFbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                           GL_TEXTURE_2D, target->glTexture, 0);
    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        ALOGW("NanoMenu DRM zero-copy: AHB FBO(%s) incomplete 0x%x", label, status);
        glDeleteFramebuffers(1, &target->glFbo); target->glFbo = 0;
        glDeleteTextures(1, &target->glTexture); target->glTexture = 0;
        sEglDestroyImageKHR(eglDpy, target->eglImage); target->eglImage = EGL_NO_IMAGE_KHR;
        AHardwareBuffer_release(target->ahb); target->ahb = nullptr;
        return false;
    }

    target->w = w;
    target->h = h;
    ALOGW("NanoMenu DRM zero-copy: AHB(%s) ENABLED — fbo=%u tex=%u (%ux%u)",
          label, target->glFbo, target->glTexture, w, h);
    return true;
}

// Set up fast GPU→DRM rendering via AHardwareBuffer.
// GPU renders into an AHB-backed FBO, then we lock the AHB for CPU read
// (fast, no driver format conversion) and memcpy to DRM dumb buffer.
// This avoids glReadPixels (~170ms on Mali G52) entirely.
//
// Allocates two targets: PRIMARY (wallpaper + menu) and SECONDARY (wallpaper
// only). The secondary is only created if sDrmDisplays has more than one
// entry. Secondary allocation is best-effort: if it fails, the secondary
// display simply mirrors the primary (same behavior as before this patch).
static void drmSetupZeroCopy(EGLDisplay eglDpy) {
    if (!sDrmActive) return;

    // Resolve extension functions
    sEglCreateImageKHR = (PFNEGLCREATEIMAGEKHRPROC)eglGetProcAddress("eglCreateImageKHR");
    sEglDestroyImageKHR = (PFNEGLDESTROYIMAGEKHRPROC)eglGetProcAddress("eglDestroyImageKHR");
    sGlEGLImageTargetTexture2DOES = (PFNGLEGLIMAGETARGETTEXTURE2DOESPROC)
            eglGetProcAddress("glEGLImageTargetTexture2DOES");
    sEglGetNativeClientBufferANDROID = (PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC)
            eglGetProcAddress("eglGetNativeClientBufferANDROID");

    if (!sEglCreateImageKHR || !sGlEGLImageTargetTexture2DOES || !sEglGetNativeClientBufferANDROID) {
        ALOGW("NanoMenu DRM zero-copy: EGL/GL ext functions not available");
        return;
    }

    const char* exts = eglQueryString(eglDpy, EGL_EXTENSIONS);
    if (!exts || !strstr(exts, "EGL_ANDROID_image_native_buffer") ||
        !strstr(exts, "EGL_ANDROID_get_native_client_buffer")) {
        ALOGW("NanoMenu DRM zero-copy: AHB EGL extensions not supported");
        return;
    }

    // AHB is always at PANEL NATIVE dimensions of the selected primary display.
    // When the install orientation is non-zero, GL rotation (via uRotation mat2
    // in vertex shaders) maps logical coords to the panel-native AHB — so the
    // blit is always a fast straight copy with no per-pixel rotation.
    const uint32_t primaryW = sDrmDisplays[sDrmPrimaryIdx].w;
    const uint32_t primaryH = sDrmDisplays[sDrmPrimaryIdx].h;

    if (!drmAllocAhbTarget(eglDpy, primaryW, primaryH, &sAhbTarget, "primary")) {
        return;
    }

    sDrmZeroCopy = true;

    // Allocate a secondary AHB for wallpaper-only rendering when more than
    // one display is active. Use the first non-primary display's dimensions;
    // if other non-primary displays have different resolutions the blit
    // tolerates mismatch (clips/pads in blitAhbToDrmBuffer).
    if (sDrmDisplays.size() > 1) {
        uint32_t secW = 0, secH = 0;
        for (size_t i = 0; i < sDrmDisplays.size(); i++) {
            if ((int)i == sDrmPrimaryIdx) continue;
            secW = sDrmDisplays[i].w;
            secH = sDrmDisplays[i].h;
            break;
        }
        if (secW > 0 && secH > 0) {
            if (!drmAllocAhbTarget(eglDpy, secW, secH, &sAhbTargetSecondary,
                                    "secondary-wallpaper")) {
                // Secondary failed — secondary display(s) will mirror primary.
                ALOGW("NanoMenu DRM: secondary AHB alloc failed, fallback to mirror");
            }
        }
    }
}

// Bind the AHB-backed FBO for rendering. Call before render().
static void drmBindNextFbo() {
    if (!sDrmZeroCopy) return;
    glBindFramebuffer(GL_FRAMEBUFFER, sAhbTarget.glFbo);
    glViewport(0, 0, sAhbTarget.w, sAhbTarget.h);
}

// NEON-accelerated row blit: AHB (R8G8B8A8, memory order [R,G,B,A]) →
// DRM dumb buffer (XRGB8888, memory order [B,G,R,X=0xFF]). Processes 16
// pixels per vector iteration using vld4q/vst4q for a zero-shuffle channel
// swap. Scalar tail handles any sub-16-pixel remainder.
//
// Cortex-A55 characteristics: 128-bit NEON, 64-byte cache line, in-order.
// vld4q_u8 de-interleaves 16 RGBA pixels into 4x16 byte planes in ~4 cycles.
// Channel swap is free — we just re-interleave in a different order.
// Prefetch hint at +128 bytes (32 pixels ahead) keeps the L2 fed on A55.
static inline void blitRowRgbaToXrgbNeon(const uint32_t* __restrict src,
                                          uint32_t* __restrict dst,
                                          uint32_t count) {
#if GAMMAOS_NANO_HAVE_NEON
    const uint32_t vecPixels = count & ~15u; // round down to multiple of 16
    const uint8x16_t alphaMask = vdupq_n_u8(0xFF);
    const uint8_t* s8 = (const uint8_t*)src;
    uint8_t* d8 = (uint8_t*)dst;
    for (uint32_t i = 0; i < vecPixels; i += 16) {
        __builtin_prefetch(s8 + 128, 0, 0); // next-next cache line read
        uint8x16x4_t p = vld4q_u8(s8);
        // p.val[0]=R, [1]=G, [2]=B, [3]=A (from memory order R,G,B,A)
        // DRM XRGB8888 little-endian stores [B,G,R,X] in memory.
        uint8x16x4_t q;
        q.val[0] = p.val[2]; // B
        q.val[1] = p.val[1]; // G
        q.val[2] = p.val[0]; // R
        q.val[3] = alphaMask; // X=0xFF
        vst4q_u8(d8, q);
        s8 += 64; d8 += 64;
    }
    for (uint32_t i = vecPixels; i < count; i++) {
        uint32_t rgba = src[i];
        dst[i] = 0xFF000000u |
                 ((rgba >> 16) & 0xFFu) |
                 (rgba & 0xFF00u) |
                 ((rgba & 0xFFu) << 16);
    }
#else
    for (uint32_t i = 0; i < count; i++) {
        uint32_t rgba = src[i];
        dst[i] = 0xFF000000u |
                 ((rgba >> 16) & 0xFFu) |
                 (rgba & 0xFF00u) |
                 ((rgba & 0xFFu) << 16);
    }
#endif
}

// Blit a single AHB into a single DRM dumb buffer, handling Y-flip and
// optional 90/180/270 rotation. The 0° case uses the NEON row helper above.
static void blitAhbToDrmBuffer(const void* ahbPtr, uint32_t ahbStride,
                                uint32_t srcW, uint32_t srcH,
                                void* dstMapped, uint32_t dstPitch,
                                uint32_t dstW, uint32_t dstH,
                                int blitRotation) {
    uint8_t* dst = (uint8_t*)dstMapped;
    if (blitRotation == 0) {
        const uint32_t copyW = std::min(srcW, dstW);
        const uint32_t copyH = std::min(srcH, dstH);
        for (uint32_t dy = 0; dy < copyH; dy++) {
            uint32_t* dstRow = (uint32_t*)(dst + dy * dstPitch);
            const uint32_t* srcRow = (const uint32_t*)((const uint8_t*)ahbPtr
                                     + (srcH - 1 - dy) * ahbStride);
            blitRowRgbaToXrgbNeon(srcRow, dstRow, copyW);
            // If dst is wider than src, clear the right margin.
            if (dstW > copyW) {
                memset(dstRow + copyW, 0, (dstW - copyW) * 4);
            }
        }
        // If dst is taller than src, clear the bottom margin.
        for (uint32_t dy = copyH; dy < dstH; dy++) {
            memset(dst + dy * dstPitch, 0, dstW * 4);
        }
        return;
    }
    // Rotated paths: scalar per-pixel (rare — only used if GL rotation fallback).
    for (uint32_t dy = 0; dy < dstH; dy++) {
        uint32_t* dstRow = (uint32_t*)(dst + dy * dstPitch);
        for (uint32_t dx = 0; dx < dstW; dx++) {
            uint32_t sx, sy;
            switch (blitRotation) {
            case 90:  sx = dy; sy = dx; break;
            case 180: sx = srcW-1-dx; sy = dy; break;
            case 270: sx = srcW-1-dy; sy = srcH-1-dx; break;
            default:  sx = dx; sy = srcH-1-dy; break;
            }
            if (sx >= srcW || sy >= srcH) continue;
            const uint32_t* srcRow = (const uint32_t*)((const uint8_t*)ahbPtr
                                     + sy * ahbStride);
            uint32_t rgba = srcRow[sx];
            dstRow[dx] = 0xFF000000u |
                         ((rgba >> 16) & 0xFFu) |
                         (rgba & 0xFF00u) |
                         ((rgba & 0xFFu) << 16);
        }
    }
}

// Present all DRM displays. Each display reads from its assigned AHB:
// - sDrmPrimaryIdx → sAhbTarget (wallpaper + menu)
// - every other display → sAhbTargetSecondary (wallpaper only)
// If the secondary AHB isn't allocated (single-display hardware, or allocation
// failed), every display falls back to the primary AHB (mirrored).
// Non-blocking page flip per display: displays are independent — if one fails
// to flip, the others still present.
static void drmFlipAll() {
    if (!sDrmZeroCopy || !sAhbTarget.ahb) return;

    static int sFlipCount = 0;
    // Log timing for the first 5 flips (boot window) then once per second
    // thereafter so we can observe steady-state latency in XMB mode without
    // flooding logcat.
    bool verbose = (sFlipCount < 5) || (sFlipCount % 60 == 0);
    int64_t t0 = verbose ? (systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL) : 0;

    // Unbind FBO so subsequent GL calls don't mess with AHB
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glFinish(); // ensure GPU done writing to both AHBs before CPU locks them

    int64_t tFinish = verbose ? (systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL) : 0;

    const bool haveSecondary = (sAhbTargetSecondary.ahb != nullptr);
    const int blitRotation = sDrmGlRotation ? 0 : sDrmRotationDeg;

    // Pre-query strides once per AHB (may be padded beyond width).
    AHardwareBuffer_Desc descPrimary = {};
    AHardwareBuffer_describe(sAhbTarget.ahb, &descPrimary);
    const uint32_t primaryStride = descPrimary.stride * 4;

    AHardwareBuffer_Desc descSecondary = {};
    uint32_t secondaryStride = 0;
    if (haveSecondary) {
        AHardwareBuffer_describe(sAhbTargetSecondary.ahb, &descSecondary);
        secondaryStride = descSecondary.stride * 4;
    }

    // Lock primary AHB once for the whole flip pass.
    void* primaryPtr = nullptr;
    int lockErr = AHardwareBuffer_lock(sAhbTarget.ahb,
                                        AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN,
                                        -1, nullptr, &primaryPtr);
    if (lockErr != 0 || !primaryPtr) {
        if (verbose) ALOGW("NanoMenu DRM: primary AHB lock failed %d", lockErr);
        return;
    }

    // Lock secondary AHB (if present). If this fails, fall through and feed
    // every display from the primary AHB — the secondary display is still
    // visible, just mirrored.
    void* secondaryPtr = nullptr;
    if (haveSecondary) {
        int serr = AHardwareBuffer_lock(sAhbTargetSecondary.ahb,
                                         AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN,
                                         -1, nullptr, &secondaryPtr);
        if (serr != 0 || !secondaryPtr) {
            if (verbose) ALOGW("NanoMenu DRM: secondary AHB lock failed %d", serr);
            secondaryPtr = nullptr;
        }
    }

    int64_t tLock = verbose ? (systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL) : 0;

    // Blit + flip each display independently. Order is not important — each
    // page flip is a non-blocking ioctl and the displays scan out on their own
    // vblank. A failure on one display does not prevent the others from
    // presenting.
    for (size_t i = 0; i < sDrmDisplays.size(); i++) {
        auto& d = sDrmDisplays[i];
        const bool isPrimary = ((int)i == sDrmPrimaryIdx);

        // Select source AHB. Secondary displays fall back to primary if the
        // secondary AHB wasn't set up or locked successfully.
        const void* srcPtr;
        uint32_t srcStride;
        uint32_t srcW, srcH;
        if (isPrimary || !secondaryPtr) {
            srcPtr = primaryPtr;
            srcStride = primaryStride;
            srcW = sAhbTarget.w;
            srcH = sAhbTarget.h;
        } else {
            srcPtr = secondaryPtr;
            srcStride = secondaryStride;
            srcW = sAhbTargetSecondary.w;
            srcH = sAhbTargetSecondary.h;
        }

        int idx = 1 - d.activeBuffer;
        DrmBuffer& buf = d.buffers[idx];
        blitAhbToDrmBuffer(srcPtr, srcStride, srcW, srcH,
                            buf.mapped, buf.pitch, d.w, d.h, blitRotation);

        // Page flip — non-blocking. flags=0 means no vblank event is
        // requested, just swap the buffer on next vsync. If the kernel
        // rejects the flip (e.g. CRTC not enabled yet for a late-ready
        // display), fall back to drmModeSetCrtc which force-sets the mode.
        struct drm_mode_crtc_page_flip flip = {};
        flip.crtc_id = d.crtcId;
        flip.fb_id = buf.fbId;
        flip.flags = 0;
        if (ioctl(sDrmFd, DRM_IOCTL_MODE_PAGE_FLIP, &flip) != 0) {
            struct drm_mode_crtc crtc = {};
            crtc.crtc_id = d.crtcId;
            crtc.fb_id = buf.fbId;
            crtc.set_connectors_ptr = (uint64_t)(uintptr_t)&d.connId;
            crtc.count_connectors = 1;
            crtc.mode = d.mode;
            crtc.mode_valid = 1;
            ioctl(sDrmFd, DRM_IOCTL_MODE_SETCRTC, &crtc);
        }
        d.activeBuffer = idx;
    }

    int64_t tCopy = verbose ? (systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL) : 0;

    AHardwareBuffer_unlock(sAhbTarget.ahb, nullptr);
    if (secondaryPtr) AHardwareBuffer_unlock(sAhbTargetSecondary.ahb, nullptr);

    if (verbose) {
        int64_t tEnd = systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL;
        ALOGW("NanoMenu AHB flip #%d: glFinish=%lldus lock=%lldus blit+flip=%lldus unlock=%lldus total=%lldus displays=%zu primary=%d sec=%d",
              sFlipCount, tFinish - t0, tLock - tFinish, tCopy - tLock,
              tEnd - tCopy, tEnd - t0, sDrmDisplays.size(), sDrmPrimaryIdx,
              haveSecondary ? 1 : 0);
    }
    sFlipCount++;
}

// Helper: bind AHB FBO before rendering a frame (no-op if DRM inactive).
// Use this INSTEAD of glViewport at the start of ad-hoc render blocks
// (quick resume screens, loading screens, libretro overlays).
static inline void drmFrameBegin() {
    if (sDrmActive && sDrmZeroCopy) {
        drmBindNextFbo();
    }
}

// Helper: present the current frame. Either DRM page flip or EGL swap.
// NanoMenu's main render() uses drmFlipAll directly; this is for other sites.
static inline void drmFrameEnd(EGLDisplay dpy, EGLSurface surf) {
    if (sDrmActive && sDrmZeroCopy) {
        glFlush();
        drmFlipAll();
    } else if (sDrmActive) {
        // Non-zero-copy fallback: would need width/height. Skip — in practice
        // sDrmZeroCopy is true on supported devices.
    } else {
        eglSwapBuffers(dpy, surf);
    }
}

// Push GL framebuffer to DRM dumb buffers.
// Optimized for 60fps: GL_BGRA readback + NEON copy + double-buffered page flip.
static void drmPushFrame(uint32_t glWidth, uint32_t glHeight) {
    if (!sDrmActive || sDrmDisplays.empty()) return;

    static int sPushCount = 0;
    static bool sBgraSupported = true; // try BGRA first, fall back to RGBA
    bool verbose = (sPushCount < 3);

    int64_t tStart = verbose ? (systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL) : 0;
    if (verbose) glFinish();
    int64_t tFinish = verbose ? (systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL) : 0;

    // Determine target buffer for first display; we'll reuse the readback for both
    DrmDisplay& d0 = sDrmDisplays[0];
    int targetIdx = 1 - d0.activeBuffer;
    uint8_t* dst0 = (uint8_t*)d0.buffers[targetIdx].mapped;
    uint32_t dstStride0 = d0.buffers[targetIdx].pitch;

    // glReadPixels directly into the DRM mmap'd buffer (avoids intermediate copy).
    // Use GL_BGRA_EXT if supported — matches DRM's XRGB8888 natively (no swap needed).
    // GL reads bottom-up; we'll flip with a vertical y-inversion via pitch trick below.
    // Actually glReadPixels doesn't support negative stride, so read into linear buf,
    // then flip+copy to DRM. But we can read directly into the DRM buffer if we're OK
    // with upside-down output, OR read upside-down and flip during blit to display 2.
    //
    // Simplest fast path: read into a reusable static scratch buffer, then memcpy rows
    // in reverse order into each DRM buffer. This is ~1.2MB read + 2×1.2MB memcpy.
    static std::vector<uint8_t> scratch;
    size_t pixelBytes = glWidth * glHeight * 4;
    if (scratch.size() != pixelBytes) scratch.resize(pixelBytes);

    GLenum fmt = sBgraSupported ? GL_BGRA_EXT : GL_RGBA;
    glReadPixels(0, 0, glWidth, glHeight, fmt, GL_UNSIGNED_BYTE, scratch.data());
    if (glGetError() != GL_NO_ERROR && sBgraSupported) {
        // Fall back to RGBA if BGRA not supported
        sBgraSupported = false;
        glReadPixels(0, 0, glWidth, glHeight, GL_RGBA, GL_UNSIGNED_BYTE, scratch.data());
    }

    int64_t tReadback = verbose ? (systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL) : 0;

    // Row-by-row flipped copy into each display's inactive buffer.
    uint32_t rowBytes = glWidth * 4;
    for (auto& d : sDrmDisplays) {
        int idx = 1 - d.activeBuffer;
        DrmBuffer& buf = d.buffers[idx];
        uint8_t* dst = (uint8_t*)buf.mapped;
        uint32_t copyH = std::min(glHeight, d.h);
        uint32_t copyRowBytes = std::min(rowBytes, buf.pitch);

        if (sBgraSupported) {
            // Direct row copy (BGRA matches XRGB8888)
            for (uint32_t y = 0; y < copyH; y++) {
                uint8_t* srcRow = scratch.data() + (glHeight - 1 - y) * rowBytes;
                memcpy(dst + y * buf.pitch, srcRow, copyRowBytes);
            }
        } else {
            // RGBA → BGRA swap via manual loop
            for (uint32_t y = 0; y < copyH; y++) {
                uint32_t* srcRow = (uint32_t*)(scratch.data() + (glHeight - 1 - y) * rowBytes);
                uint32_t* dstRow = (uint32_t*)(dst + y * buf.pitch);
                uint32_t cw = std::min(glWidth, d.w);
                for (uint32_t x = 0; x < cw; x++) {
                    uint32_t rgba = srcRow[x];
                    dstRow[x] = 0xFF000000 |
                                ((rgba >> 16) & 0xFF) |          // R → B
                                ((rgba & 0xFF00)) |              // G
                                ((rgba & 0xFF) << 16);           // B → R
                }
            }
        }
    }

    int64_t tCopy = verbose ? (systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL) : 0;

    // Page flip each display
    for (auto& d : sDrmDisplays) {
        int idx = 1 - d.activeBuffer;
        struct drm_mode_crtc_page_flip flip = {};
        flip.crtc_id = d.crtcId;
        flip.fb_id = d.buffers[idx].fbId;
        flip.flags = 0;
        if (ioctl(sDrmFd, DRM_IOCTL_MODE_PAGE_FLIP, &flip) != 0) {
            // Fallback to setCrtc
            struct drm_mode_crtc crtc = {};
            crtc.crtc_id = d.crtcId;
            crtc.fb_id = d.buffers[idx].fbId;
            crtc.set_connectors_ptr = (uint64_t)(uintptr_t)&d.connId;
            crtc.count_connectors = 1;
            crtc.mode = d.mode;
            crtc.mode_valid = 1;
            ioctl(sDrmFd, DRM_IOCTL_MODE_SETCRTC, &crtc);
        }
        d.activeBuffer = idx;
    }

    if (verbose) {
        int64_t tEnd = systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL;
        ALOGW("NanoMenu DRM push #%d: bgra=%d glFinish=%lldus readback=%lldus copy=%lldus flip=%lldus total=%lldus",
              sPushCount, sBgraSupported ? 1 : 0,
              tFinish - tStart, tReadback - tFinish, tCopy - tReadback, tEnd - tCopy, tEnd - tStart);
    }
    (void)dst0; (void)dstStride0;
    sPushCount++;
}

// Stop DRM direct rendering (HWC has taken over)
static void drmStop() {
    if (!sDrmActive) return;
    sDrmActive = false;
    sDrmZeroCopy = false;
    // Reset GL rotation to identity for the SF EGL path
    sDrmGlRotation = false;
    sDrmRotMat[0] = 1.0f; sDrmRotMat[1] = 0.0f;
    sDrmRotMat[2] = 0.0f; sDrmRotMat[3] = 1.0f;
    ALOGW("NanoMenu DRM splash: stopping direct rendering, HWC has taken over");

    // Rebind default framebuffer before destroying FBOs
    glBindFramebuffer(GL_FRAMEBUFFER, 0);

    // Release primary AHB render target
    if (sAhbTarget.glFbo) { glDeleteFramebuffers(1, &sAhbTarget.glFbo); sAhbTarget.glFbo = 0; }
    if (sAhbTarget.glTexture) { glDeleteTextures(1, &sAhbTarget.glTexture); sAhbTarget.glTexture = 0; }
    if (sAhbTarget.ahb) { AHardwareBuffer_release(sAhbTarget.ahb); sAhbTarget.ahb = nullptr; }
    sAhbTarget.eglImage = EGL_NO_IMAGE_KHR;

    // Release secondary (wallpaper-only) AHB render target
    if (sAhbTargetSecondary.glFbo) {
        glDeleteFramebuffers(1, &sAhbTargetSecondary.glFbo);
        sAhbTargetSecondary.glFbo = 0;
    }
    if (sAhbTargetSecondary.glTexture) {
        glDeleteTextures(1, &sAhbTargetSecondary.glTexture);
        sAhbTargetSecondary.glTexture = 0;
    }
    if (sAhbTargetSecondary.ahb) {
        AHardwareBuffer_release(sAhbTargetSecondary.ahb);
        sAhbTargetSecondary.ahb = nullptr;
    }
    sAhbTargetSecondary.eglImage = EGL_NO_IMAGE_KHR;

    for (auto& d : sDrmDisplays) {
        for (int i = 0; i < 2; i++) {
            DrmBuffer& buf = d.buffers[i];
            if (buf.mapped) { munmap(buf.mapped, buf.size); buf.mapped = nullptr; }
        }
    }
    sDrmDisplays.clear();
    if (sDrmFd >= 0) { close(sDrmFd); sDrmFd = -1; }
}

status_t NanoMenu::readyToRun() {
    int64_t t0 = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
    auto tlog = [&](const char* label) {
        int64_t now = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
        ALOGW("NanoMenu BOOT TIMING: %s at T+%lldms (delta %lldms)", label, now, now - t0);
        t0 = now;
    };
    tlog("readyToRun enter");

    // GammaOS: Before doing ANYTHING that touches the display (DRM master grab,
    // SF transactions), confirm we are actually in nano boot mode. StartPropertySetThread
    // already gates this, but gammaos-nano can also be (re)started by other init triggers
    // (ctl restarts, userspace reboot, app exit relaunch), so a defensive check here
    // prevents DRM takeover in normal boot if any of those paths fire unexpectedly.
    //
    // persist.* properties may not be available yet (loaded after /data mount), so
    // ALWAYS wait for init to signal they are ready before reading persist.bootanim.skip_nano.
    // Reading it too early would return an empty string and we would proceed into the
    // nano path in normal boot — blanking the display via drmEarlySplash() and causing
    // a multi-second black gap between bootloader and bootanim.
    {
        char ready[PROPERTY_VALUE_MAX] = {};
        property_get("ro.persistent_properties.ready", ready, "");
        if (strcmp(ready, "true") != 0) {
            ALOGI("GammaOS Nano: persist props not ready, waiting...");
            for (int i = 0; i < 500; i++) { // max 5s
                usleep(10000); // 10ms
                property_get("ro.persistent_properties.ready", ready, "");
                if (!strcmp(ready, "true")) break;
            }
        }
    }

    char skip[PROPERTY_VALUE_MAX] = {};
    property_get("persist.bootanim.skip_nano", skip, "");
    if (strcmp(skip, "0") != 0) {
        ALOGI("GammaOS Nano: skip_nano='%s' (not '0'), starting bootanim (no DRM touch)", skip);
        property_set("ctl.start", "bootanim");
        _exit(0); // terminate — bootanim takes over, stock boot flow continues
    }

    tlog("persist props resolved");

    // GammaOS: Nano mode is confirmed active. Show DRM splash now — before any
    // SF/HWC setup. This replaces the U-Boot logo with a dark screen within
    // milliseconds on devices where HWC composition isn't ready yet (e.g.
    // dual-DSI RG DS RK3568). Skip DRM on restarts (returning from app) —
    // HWC is already active by then.
    {
        char bootDone[PROPERTY_VALUE_MAX] = {};
        property_get("sys.boot_completed", bootDone, "0");
        if (strcmp(bootDone, "1") != 0) {
            drmEarlySplash();
        } else {
            ALOGI("NanoMenu: skipping DRM splash (already booted)");
        }
    }
    tlog("drmEarlySplash done");
    // Nano mode is active — tell any boot animation instance to exit.
    // Vendor init may start bootanim independently (e.g. in on late-fs),
    // so it can be running alongside us with the same z-layer.
    property_set("service.bootanim.exit", "1");

    const std::vector<PhysicalDisplayId> ids = SurfaceComposerClient::getPhysicalDisplayIds();
    tlog("getPhysicalDisplayIds returned");
    if (ids.empty()) { ALOGE("No displays found"); return NAME_NOT_FOUND; }

    // GammaOS: persist.gammaos.nano.primary_display selects which physical display
    // port NanoMenu renders on (and which display nano-launched apps target).
    // Value is a port number (0 = first physical port, 1 = second, ...). Falls back
    // to ids.front() if the configured port is not found. Dualstack-whitelisted apps
    // still launch on the real primary — that routing is enforced in RootWindowContainer.
    PhysicalDisplayId chosenId = ids.front();
    {
        char primaryProp[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.primary_display", primaryProp, "0");
        const int wantPort = atoi(primaryProp);
        for (const PhysicalDisplayId& pid : ids) {
            if (static_cast<int>(pid.getPort()) == wantPort) {
                chosenId = pid;
                break;
            }
        }
        ALOGI("NanoMenu: primary_display prop='%s' chosen port=%d",
              primaryProp, static_cast<int>(chosenId.getPort()));
    }

    mDisplayToken = SurfaceComposerClient::getPhysicalDisplayToken(chosenId);
    if (mDisplayToken == nullptr) return NAME_NOT_FOUND;
    tlog("getPhysicalDisplayToken");

    // GammaOS: Look up the chosen display's layer stack so the NanoMenu surface
    // can be attached to it. Layers only show up on a display whose layerStack
    // matches the layer's layerStack, so without this the surface stays on the
    // default (port 0) display regardless of which token we targeted above.
    // NOTE: SurfaceFlinger's layerStack for a given display can change after
    // DisplayManagerService finishes assigning logical display IDs (seen on
    // dual-DSI RK3568: port 1 starts at layerStack=1, becomes 2 after DMS).
    // The render loop re-queries and re-applies setLayerStack to handle that.
    ui::DisplayState chosenDisplayState;
    ui::LayerStack chosenLayerStack = ui::DEFAULT_LAYER_STACK;
    if (SurfaceComposerClient::getDisplayState(mDisplayToken, &chosenDisplayState) == NO_ERROR) {
        chosenLayerStack = chosenDisplayState.layerStack;
        ALOGI("NanoMenu: chosen display layerStack=%u", chosenLayerStack.id);
    } else {
        ALOGW("NanoMenu: getDisplayState failed, using default layerStack");
    }
    mAppliedLayerStack = chosenLayerStack.id;

    DisplayMode displayMode;
    const status_t error = SurfaceComposerClient::getActiveDisplayMode(mDisplayToken, &displayMode);
    if (error != NO_ERROR) return error;
    tlog("getActiveDisplayMode");

    ui::Size resolution = displayMode.resolution;
    // GammaOS: Use RGBX_8888 (opaque 32bpp) to match the EGL config (8:8:8
    // with alpha=0) and the AHB/DRM pixel format (R8G8B8A8 / XRGB8888).
    // The previous RGB_565 surface forced the driver to do a 16→32 bpp
    // conversion on every present, visible as extra GPU/CPU cost on Mali-G52.
    sp<SurfaceControl> control = session()->createSurface(
        String8("GammaOSNano"), resolution.getWidth(), resolution.getHeight(),
        PIXEL_FORMAT_RGBX_8888, ISurfaceComposerClient::eOpaque);
    tlog("createSurface (primary)");

    SurfaceComposerClient::Transaction t;
    Rect forcedRes(0, 0, resolution.width, resolution.height);
    Rect physRes(0, 0, displayMode.resolution.width, displayMode.resolution.height);
    // GammaOS: Always set the primary display projection to physical resolution.
    // NanoMenu renders at 640x480 and needs the display projection to match.
    // If DualStack left it at 640x960, NanoMenu would appear compressed.
    // DualStack invalidates the Java-side cache when it later re-applies 640x960.
    t.setDisplayProjection(mDisplayToken, ui::ROTATION_0, forcedRes, physRes);
    t.setLayer(control, 0x40000001);
    // GammaOS: Route the NanoMenu surface to the chosen display's layer stack.
    t.setLayerStack(control, chosenLayerStack);

    // GammaOS: Signal that NanoMenu is active. DualStackController checks this
    // to suppress DualStack while NanoMenu is rendering.
    property_set("sys.gammaos.nano.menu_active", "1");
    // Only clear forced display size if DualStack was actually active. Sending the
    // clear signal unconditionally triggers DualStack teardown + display reconfig
    // events that interfere with subsequent app launches.
    {
        char dsActive[PROPERTY_VALUE_MAX] = {};
        property_get("sys.gammaos.dualstack.active", dsActive, "0");
        if (!strcmp(dsActive, "1")) {
            property_set("sys.gammaos.dualstack.active", "0");
            property_set("sys.gammaos.nano.clear_forced_size", "1");
            ALOGD("NanoMenu: signaled DualStack clear (was active)");
        }
    }

    // GammaOS: Signal the framework to kill the previous foreground app.
    // NanoMenu runs as graphics user and can't call am force-stop directly.
    // The framework (RootWindowContainer) picks up this property and kills the app.
    {
        char lastApp[PROPERTY_VALUE_MAX] = {};
        property_get("sys.gammaos.nano.launched_pkg", lastApp, "");
        if (lastApp[0] != '\0') {
            property_set("sys.gammaos.nano.kill_pkg", lastApp);
            ALOGD("NanoMenu: signaled framework to kill: %s", lastApp);
            property_set("sys.gammaos.nano.launched_pkg", "");
        }
    }
    tlog("primary transaction applied");
    ALOGD("NanoMenu: signaled framework to clear forced display size");

    // GammaOS: Defer secondary display surface creation until after the first frame
    // is rendered on the primary. On dual-DSI devices the secondary surface/transaction
    // can trigger SF display reconfiguration that delays the primary scanout.
    t.apply();
    tlog("primary transaction applied (secondary deferred)");

    sp<Surface> s = control->getSurface();
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(display, nullptr, nullptr);
    EGLConfig config = getEglConfig(display);
    EGLSurface surface = eglCreateWindowSurface(display, config, s.get(), nullptr);
    EGLint contextAttributes[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    EGLContext context = eglCreateContext(display, config, nullptr, contextAttributes);
    EGLint w, h;
    eglQuerySurface(display, surface, EGL_WIDTH, &w);
    eglQuerySurface(display, surface, EGL_HEIGHT, &h);
    if (eglMakeCurrent(display, surface, surface, context) == EGL_FALSE) return NO_INIT;

    mDisplay = display; mContext = context; mSurface = surface;
    mWidth = w; mHeight = h;
    // NOTE: eglQuerySurface returns logical (rotation-applied) dimensions
    // because SurfaceFlinger reports the active display mode with install
    // orientation already factored in. Do NOT swap mWidth/mHeight here —
    // the AHB swap in drmSetupZeroCopy ensures the render target matches.
    mFlingerSurfaceControl = control; mFlingerSurface = s;

    ALOGD("NanoMenu: display %dx%d", mWidth, mHeight);
    tlog("EGL init + primary surface ready");

    // GammaOS: Set up zero-copy DRM rendering via DMA-BUF/EGLImage/FBO.
    // GPU writes straight to the DRM scanout buffer — no glReadPixels, no CPU copy.
    drmSetupZeroCopy(display);
    tlog("DRM zero-copy setup");

    // Secondary EGL surfaces deferred — created later in threadLoop after first frame.
    tlog("EGL ready (secondary deferred)");
    initShaders();
    tlog("shaders compiled");
    buildMenu();
    initXmbSystems();
    loadXmbRecent();
    openInputDevices();
    initEffects();

    // Initialize brightness — restore from persist property (synced with Android),
    // falling back to current sysfs value
    mMaxBrightness = readSysfsInt("/sys/class/leds/lcd-backlight/max_brightness", 255);
    char savedBrightness[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.brightness", savedBrightness, "");
    if (savedBrightness[0] != '\0') {
        mBrightness = atoi(savedBrightness);
        if (mBrightness < 1) mBrightness = 1;
        if (mBrightness > mMaxBrightness) mBrightness = mMaxBrightness;
    } else {
        mBrightness = readSysfsInt("/sys/class/leds/lcd-backlight/brightness", mMaxBrightness / 2);
    }
    // GammaOS: Immediately write brightness to sysfs for instant backlight during early boot.
    // The Lights HAL (vendor.light-rockchip) may not be up for seconds, so write directly
    // to ensure the screen is visible as soon as NanoMenu starts rendering.
    {
        char brightnessStr[16];
        snprintf(brightnessStr, sizeof(brightnessStr), "%d", mBrightness);
        const char* backlightPaths[] = {
            "/sys/class/backlight/backlight/brightness",
            "/sys/class/backlight/backlight1/brightness",
            "/sys/class/leds/lcd-backlight/brightness",
        };
        for (const char* path : backlightPaths) {
            int fd = open(path, O_WRONLY);
            if (fd >= 0) {
                write(fd, brightnessStr, strlen(brightnessStr));
                close(fd);
                ALOGI("NanoMenu: early sysfs brightness %d → %s", mBrightness, path);
            }
        }
    }

    // Apply brightness async via HAL — also sets it through the proper Android path
    // so DMS/PowerManager stay in sync.
    {
        int brightness = mBrightness;
        std::thread([brightness]() {
            // Try AIDL first
            {
                using aidl::android::hardware::light::ILights;
                using aidl::android::hardware::light::HwLight;
                using aidl::android::hardware::light::HwLightState;
                using aidl::android::hardware::light::LightType;

                ndk::SpAIBinder binder(AServiceManager_checkService(
                        "android.hardware.light.ILights/default"));
                if (binder.get()) {
                    std::shared_ptr<ILights> hal = ILights::fromBinder(binder);
                    if (hal) {
                        std::vector<HwLight> lights;
                        hal->getLights(&lights);
                        for (const auto& light : lights) {
                            if (light.type == LightType::BACKLIGHT) {
                                HwLightState state{};
                                state.color = 0xFF000000 | (brightness << 16) | (brightness << 8) | brightness;
                                hal->setLightState(light.id, state);
                                ALOGI("NanoMenu: brightness %d via AIDL ILights", brightness);
                                return;
                            }
                        }
                    }
                }
            }

            // AIDL not available yet — wait for HIDL or AIDL, whichever comes first
            using HidlLight = ::android::hardware::light::V2_0::ILight;
            using HidlType = ::android::hardware::light::V2_0::Type;
            using HidlLightState = ::android::hardware::light::V2_0::LightState;
            using HidlBrightness = ::android::hardware::light::V2_0::Brightness;
            using HidlFlash = ::android::hardware::light::V2_0::Flash;

            // Poll for either HAL (100ms intervals, up to 5s)
            for (int i = 0; i < 50; i++) {
                // Try AIDL
                {
                    using aidl::android::hardware::light::ILights;
                    using aidl::android::hardware::light::HwLight;
                    using aidl::android::hardware::light::HwLightState;
                    using aidl::android::hardware::light::LightType;

                    ndk::SpAIBinder binder(AServiceManager_checkService(
                            "android.hardware.light.ILights/default"));
                    if (binder.get()) {
                        std::shared_ptr<ILights> hal = ILights::fromBinder(binder);
                        if (hal) {
                            std::vector<HwLight> lights;
                            hal->getLights(&lights);
                            for (const auto& light : lights) {
                                if (light.type == LightType::BACKLIGHT) {
                                    HwLightState state{};
                                    state.color = 0xFF000000 | (brightness << 16) | (brightness << 8) | brightness;
                                    hal->setLightState(light.id, state);
                                    ALOGI("NanoMenu: brightness %d via AIDL ILights", brightness);
                                    return;
                                }
                            }
                        }
                    }
                }
                // Try HIDL
                {
                    android::sp<HidlLight> hal = HidlLight::getService();
                    if (hal != nullptr) {
                        HidlLightState state{};
                        state.color = 0xFF000000 | (brightness << 16) | (brightness << 8) | brightness;
                        state.flashMode = HidlFlash::NONE;
                        state.brightnessMode = HidlBrightness::USER;
                        hal->setLight(HidlType::BACKLIGHT, state);
                        ALOGI("NanoMenu: brightness %d via HIDL ILight@2.0", brightness);
                        return;
                    }
                }
                usleep(100000); // 100ms
            }
            ALOGW("NanoMenu: lights HAL not available after 5s, brightness not set");
        }).detach();
    }

    // Restore volume from persist property
    {
        char savedVolume[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.volume", savedVolume, "10");
        mVolume = atoi(savedVolume);
        if (mVolume < 0) mVolume = 0;
        if (mVolume > mMaxVolume) mVolume = mMaxVolume;
    }

    // Zygote + SystemServer preload is triggered by init.rc on nonencrypted,
    // before gammaos-nano even starts.  By the time the user sees the menu,
    // Android is already booting in the background.

    return NO_ERROR;
}

void NanoMenu::initShaders() {
    {   GLuint vs = compileShader(GL_VERTEX_SHADER, VERTEX_SHADER);
        GLuint fs = compileShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        mShaderProgram = linkProgram(vs, fs);
        mLocPosition = glGetAttribLocation(mShaderProgram, "aPosition");
        mLocColor = glGetUniformLocation(mShaderProgram, "uColor");
        mLocRotation = glGetUniformLocation(mShaderProgram, "uRotation");
        glDeleteShader(vs); glDeleteShader(fs);
    }
    {   GLuint vs = compileShader(GL_VERTEX_SHADER, TEXT_VERTEX_SHADER);
        GLuint fs = compileShader(GL_FRAGMENT_SHADER, TEXT_FRAGMENT_SHADER);
        mTextProgram = linkProgram(vs, fs);
        mTextLocPosition = glGetAttribLocation(mTextProgram, "aPosition");
        mTextLocTexCoord = glGetAttribLocation(mTextProgram, "aTexCoord");
        mTextLocColor    = glGetAttribLocation(mTextProgram, "aColor");
        mTextLocTexture  = glGetUniformLocation(mTextProgram, "uTexture");
        mTextLocRotation = glGetUniformLocation(mTextProgram, "uRotation");
        glDeleteShader(vs); glDeleteShader(fs);
    }
    {   GLuint vs = compileShader(GL_VERTEX_SHADER, PARTICLE_VERTEX_SHADER);
        GLuint fs = compileShader(GL_FRAGMENT_SHADER, PARTICLE_FRAGMENT_SHADER);
        mParticleProgram = linkProgram(vs, fs);
        mParticleLocPosition = glGetAttribLocation(mParticleProgram, "aPosition");
        mParticleLocColor    = glGetAttribLocation(mParticleProgram, "aColor");
        mParticleLocRotation = glGetUniformLocation(mParticleProgram, "uRotation");
        glDeleteShader(vs); glDeleteShader(fs);
    }
    {   GLuint vs = compileShader(GL_VERTEX_SHADER, FX_VERTEX_SHADER);
        GLuint fs = compileShader(GL_FRAGMENT_SHADER, FX_FRAGMENT_SHADER);
        mFxProgram = linkProgram(vs, fs);
        mFxLocPosition   = glGetAttribLocation(mFxProgram, "aPosition");
        mFxLocTime       = glGetUniformLocation(mFxProgram, "uTime");
        mFxLocResolution = glGetUniformLocation(mFxProgram, "uResolution");
        mFxLocEffect     = glGetUniformLocation(mFxProgram, "uEffect");
        mFxLocRotation   = glGetUniformLocation(mFxProgram, "uRotation");
        mFxLocCoordSwap  = glGetUniformLocation(mFxProgram, "uCoordSwap");
        glDeleteShader(vs); glDeleteShader(fs);
    }
    {   GLuint vs = compileShader(GL_VERTEX_SHADER, FX_VERTEX_SHADER);
        GLuint fs = compileShader(GL_FRAGMENT_SHADER, XMB_FRAGMENT_SHADER);
        mXmbProgram = linkProgram(vs, fs);
        mXmbLocPosition   = glGetAttribLocation(mXmbProgram, "aPosition");
        mXmbLocTime       = glGetUniformLocation(mXmbProgram, "uTime");
        mXmbLocResolution = glGetUniformLocation(mXmbProgram, "uResolution");
        mXmbLocRotation   = glGetUniformLocation(mXmbProgram, "uRotation");
        mXmbLocCoordSwap  = glGetUniformLocation(mXmbProgram, "uCoordSwap");
        glDeleteShader(vs); glDeleteShader(fs);
    }

    // GammaOS: Compute the GL rotation matrix for DRM direct rendering.
    // When sDrmActive + non-zero orientation, the vertex shaders rotate NDC
    // coordinates so the AHB content is already panel-native — eliminating the
    // per-pixel rotation from the blit loop (25ms → <5ms copy).
    if (sDrmActive && sDrmRotationDeg != 0) {
        sDrmGlRotation = true;
        // Column-major mat2 for glUniformMatrix2fv
        // Column-major mat2 for glUniformMatrix2fv.
        // Derived from: logical_ndc → 270°/90° CW rotation → panel_ndc
        // where panel_ndc accounts for both the rotation AND the GL y-up
        // convention in the panel-native viewport.
        switch (sDrmRotationDeg) {
        case 90:  // 90° CW: panel_ndc = (y_ndc, -x_ndc)
            sDrmRotMat[0] =  0.0f; sDrmRotMat[1] = -1.0f;
            sDrmRotMat[2] =  1.0f; sDrmRotMat[3] =  0.0f;
            break;
        case 180: // 180°: panel_ndc = (-x_ndc, -y_ndc)
            sDrmRotMat[0] = -1.0f; sDrmRotMat[1] =  0.0f;
            sDrmRotMat[2] =  0.0f; sDrmRotMat[3] = -1.0f;
            break;
        case 270: // 270° CW: panel_ndc = (-y_ndc, x_ndc)
            sDrmRotMat[0] =  0.0f; sDrmRotMat[1] =  1.0f;
            sDrmRotMat[2] = -1.0f; sDrmRotMat[3] =  0.0f;
            break;
        }
        ALOGI("NanoMenu: GL rotation %d° active for DRM", sDrmRotationDeg);
    }
    initFonts();
    initIconTextures();
}

// ---------------------------------------------------------------------------
// Icon texture rendering (monochrome 32x32 icons, tinted at draw time)
// ---------------------------------------------------------------------------

// Map system index to RetroArch XMB monochrome icon filename
// Order MUST match kXmbSystemDefs: NES,SNES,GB,GBC,GBA,N64,NDS,GEN,SMS,GG,PSX,PSP,DC,NGP,P8,history
static const char* kIconPngNames[16] = {
    "Nintendo - Nintendo Entertainment System.png",       // 0: NES
    "Nintendo - Super Nintendo Entertainment System.png", // 1: SNES
    "Nintendo - Game Boy.png",                            // 2: GB
    "Nintendo - Game Boy Color.png",                      // 3: GBC
    "Nintendo - Game Boy Advance.png",                    // 4: GBA
    "Nintendo - Nintendo 64.png",                         // 5: N64
    "Nintendo - Nintendo DS.png",                         // 6: NDS
    "Sega - Mega Drive - Genesis.png",                    // 7: Genesis
    "Sega - Master System - Mark III.png",                // 8: Master System
    "Sega - Game Gear.png",                               // 9: Game Gear
    "Sony - PlayStation.png",                             // 10: PSX
    "Sony - PlayStation Portable.png",                    // 11: PSP
    "Sega - Dreamcast.png",                               // 12: Dreamcast
    "SNK - Neo Geo Pocket Color.png",                     // 13: NGP
    "PICO-8.png",                                         // 14: PICO-8
    "history.png",                                        // 15: Recently Played
};

static const char* kIconPngDir = "/data/system/nano_icons";

// Decode PNG pixel data from any source.
// If monoWhite is true, forces RGB to white and uses alpha for shape (monochrome icons).
// If monoWhite is false, preserves original RGBA colors (colored icons like PICO-8).
static bool decodePngToRGBA(png_structp png, png_infop info,
                            int* outW, int* outH, std::vector<uint8_t>* outPixels,
                            bool monoWhite = true) {
    int width = png_get_image_width(png, info);
    int height = png_get_image_height(png, info);
    png_byte colorType = png_get_color_type(png, info);
    png_byte bitDepth = png_get_bit_depth(png, info);

    if (colorType == PNG_COLOR_TYPE_PALETTE) png_set_palette_to_rgb(png);
    if (colorType == PNG_COLOR_TYPE_GRAY && bitDepth < 8) png_set_expand_gray_1_2_4_to_8(png);
    if (colorType == PNG_COLOR_TYPE_GRAY || colorType == PNG_COLOR_TYPE_GRAY_ALPHA)
        png_set_gray_to_rgb(png);
    if (bitDepth == 16) png_set_strip_16(png);
    bool hasTrns = png_get_valid(png, info, PNG_INFO_tRNS) != 0;
    if (hasTrns) png_set_tRNS_to_alpha(png);
    bool hasAlpha = (colorType & PNG_COLOR_MASK_ALPHA) || hasTrns;
    if (!hasAlpha) png_set_filler(png, 0xFF, PNG_FILLER_AFTER);
    png_read_update_info(png, info);

    outPixels->resize(width * height * 4);
    std::vector<png_bytep> rows(height);
    for (int y = 0; y < height; y++)
        rows[y] = outPixels->data() + y * width * 4;
    png_read_image(png, rows.data());

    if (monoWhite) {
        // White + alpha: preserve alpha, set RGB=255
        for (int p = 0; p < width * height; p++) {
            (*outPixels)[p * 4 + 0] = 255;
            (*outPixels)[p * 4 + 1] = 255;
            (*outPixels)[p * 4 + 2] = 255;
        }
    }
    *outW = width;
    *outH = height;
    return true;
}

// Upload decoded RGBA pixels as a GL texture with mipmaps.
static GLuint createIconTexture(const uint8_t* pixels, int width, int height) {
    GLuint tex;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    glGenerateMipmap(GL_TEXTURE_2D);
    return tex;
}

// Load a PNG as RGBA texture from file. Returns true on success.
static bool loadPngAsAlphaTexture(const char* path, GLuint* outTex, bool monoWhite = true) {
    FILE* fp = fopen(path, "rb");
    if (!fp) return false;

    png_byte header[8];
    if (fread(header, 1, 8, fp) != 8 || png_sig_cmp(header, 0, 8)) {
        fclose(fp);
        return false;
    }

    png_structp png = png_create_read_struct(PNG_LIBPNG_VER_STRING, nullptr, nullptr, nullptr);
    if (!png) { fclose(fp); return false; }
    png_infop info = png_create_info_struct(png);
    if (!info) { png_destroy_read_struct(&png, nullptr, nullptr); fclose(fp); return false; }

    if (setjmp(png_jmpbuf(png))) {
        png_destroy_read_struct(&png, &info, nullptr);
        fclose(fp);
        return false;
    }

    png_init_io(png, fp);
    png_set_sig_bytes(png, 8);
    png_read_info(png, info);

    int width, height;
    std::vector<uint8_t> pixels;
    if (!decodePngToRGBA(png, info, &width, &height, &pixels, monoWhite)) {
        png_destroy_read_struct(&png, &info, nullptr);
        fclose(fp);
        return false;
    }
    png_destroy_read_struct(&png, &info, nullptr);
    fclose(fp);

    *outTex = createIconTexture(pixels.data(), width, height);
    ALOGD("NanoMenu: loaded PNG icon %s (%dx%d, %s)", path, width, height,
          monoWhite ? "mono" : "color");
    return true;
}

// Memory read callback for libpng
struct MemPngState { const uint8_t* data; size_t offset; size_t size; };
static void pngReadFromMemory(png_structp png, png_bytep out, png_size_t count) {
    MemPngState* state = (MemPngState*)png_get_io_ptr(png);
    if (state->offset + count > state->size) {
        png_error(png, "read past end");
        return;
    }
    memcpy(out, state->data + state->offset, count);
    state->offset += count;
}

// Load a PNG from in-memory data as texture. Returns true on success.
// If monoWhite is false, preserves original colors (for colored icons like PICO-8).
static bool loadPngFromMemory(const uint8_t* pngData, int pngSize, GLuint* outTex,
                              bool monoWhite = true) {
    if (pngSize < 8 || png_sig_cmp(pngData, 0, 8)) return false;

    png_structp png = png_create_read_struct(PNG_LIBPNG_VER_STRING, nullptr, nullptr, nullptr);
    if (!png) return false;
    png_infop info = png_create_info_struct(png);
    if (!info) { png_destroy_read_struct(&png, nullptr, nullptr); return false; }

    if (setjmp(png_jmpbuf(png))) {
        png_destroy_read_struct(&png, &info, nullptr);
        return false;
    }

    MemPngState memState = { pngData, 8, (size_t)pngSize };
    png_set_read_fn(png, &memState, pngReadFromMemory);
    png_set_sig_bytes(png, 8);
    png_read_info(png, info);

    int width, height;
    std::vector<uint8_t> pixels;
    if (!decodePngToRGBA(png, info, &width, &height, &pixels, monoWhite)) {
        png_destroy_read_struct(&png, &info, nullptr);
        return false;
    }
    png_destroy_read_struct(&png, &info, nullptr);

    *outTex = createIconTexture(pixels.data(), width, height);
    ALOGD("NanoMenu: loaded embedded PNG icon (%dx%d, %s)", width, height,
          monoWhite ? "mono" : "color");
    return true;
}

void NanoMenu::initIconTextures() {
    memset(mIconTextures, 0, sizeof(mIconTextures));
    int fileLoaded = 0, embeddedLoaded = 0;
    for (int i = 0; i < 17; i++) {
        // Try loading high-res PNG from on-device RetroArch assets
        bool mono = (i != 14); // PICO-8 (index 14) keeps its original colors
        std::string pngPath;
        if (i < 16) pngPath = std::string(kIconPngDir) + "/" + kIconPngNames[i];
        if (loadPngAsAlphaTexture(pngPath.c_str(), &mIconTextures[i], mono)) {
            fileLoaded++;
            continue;
        }
        // Fallback: embedded 256x256 PNG data
        const EmbeddedIcon& icon = kEmbeddedIcons[i];
        if (loadPngFromMemory(icon.data, icon.size, &mIconTextures[i], mono)) {
            embeddedLoaded++;
            continue;
        }
        ALOGE("NanoMenu: failed to load icon %d from file or embedded data", i);
    }
    ALOGD("NanoMenu: loaded %d file + %d embedded icon textures", fileLoaded, embeddedLoaded);
}

void NanoMenu::drawIcon(int iconIdx, float x, float y, float size,
                        float r, float g, float b, float a) {
    if (iconIdx < 0 || iconIdx >= 17 || mIconTextures[iconIdx] == 0) return;

    float x0 = (x / mWidth) * 2.0f - 1.0f;
    float y0 = 1.0f - ((y + size) / mHeight) * 2.0f;
    float x1 = ((x + size) / mWidth) * 2.0f - 1.0f;
    float y1 = 1.0f - (y / mHeight) * 2.0f;

    GLfloat verts[] = { x0,y0, x1,y0, x1,y1, x1,y1, x0,y1, x0,y0 };
    GLfloat uvs[]   = { 0,1, 1,1, 1,0, 1,0, 0,0, 0,1 };
    GLfloat colors[6 * 4];
    for (int i = 0; i < 6; i++) {
        colors[i*4+0] = r; colors[i*4+1] = g;
        colors[i*4+2] = b; colors[i*4+3] = a;
    }

    glUseProgram(mTextProgram); // reuse text shader (texture * vertex color)
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, mIconTextures[iconIdx]);
    glUniform1i(mTextLocTexture, 0);
    glVertexAttribPointer(mTextLocPosition, 2, GL_FLOAT, GL_FALSE, 0, verts);
    glEnableVertexAttribArray(mTextLocPosition);
    glVertexAttribPointer(mTextLocTexCoord, 2, GL_FLOAT, GL_FALSE, 0, uvs);
    glEnableVertexAttribArray(mTextLocTexCoord);
    glVertexAttribPointer(mTextLocColor, 4, GL_FLOAT, GL_FALSE, 0, colors);
    glEnableVertexAttribArray(mTextLocColor);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glDisableVertexAttribArray(mTextLocPosition);
    glDisableVertexAttribArray(mTextLocTexCoord);
    glDisableVertexAttribArray(mTextLocColor);
}

// ---------------------------------------------------------------------------
// Drawing helpers
// ---------------------------------------------------------------------------

void NanoMenu::drawQuad(float x, float y, float w, float h,
                         float r, float g, float b, float a) {
    float x0 = (x / mWidth) * 2.0f - 1.0f;
    float y0 = 1.0f - ((y + h) / mHeight) * 2.0f;
    float x1 = ((x + w) / mWidth) * 2.0f - 1.0f;
    float y1 = 1.0f - (y / mHeight) * 2.0f;
    GLfloat verts[] = { x0,y0, x1,y0, x1,y1, x1,y1, x0,y1, x0,y0 };
    glUseProgram(mShaderProgram);
    glUniform4f(mLocColor, r, g, b, a);
    glVertexAttribPointer(mLocPosition, 2, GL_FLOAT, GL_FALSE, 0, verts);
    glEnableVertexAttribArray(mLocPosition);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glDisableVertexAttribArray(mLocPosition);
}

// ---------------------------------------------------------------------------
// FreeType font initialization
// ---------------------------------------------------------------------------

void NanoMenu::initFonts() {
    if (FT_Init_FreeType(&mFtLib) != 0) {
        ALOGE("NanoMenu: FreeType init failed");
        return;
    }
    mFtNumFaces = 0;
    const char* fontPaths[] = {
        "/system/fonts/Roboto-Regular.ttf",
        "/system/fonts/DroidSans.ttf",
        "/system/fonts/NotoColorEmoji.ttf",
    };
    for (const char* path : fontPaths) {
        if (mFtNumFaces >= MAX_FT_FACES) break;
        if (FT_New_Face(mFtLib, path, 0, &mFtFaces[mFtNumFaces]) == 0) {
            ALOGD("NanoMenu: loaded font: %s", path);
            mFtNumFaces++;
        } else {
            ALOGW("NanoMenu: failed to load font: %s", path);
        }
    }
    mFontSize = 48; // base render size
    for (int i = 0; i < mFtNumFaces; i++) {
        if (!FT_HAS_COLOR(mFtFaces[i])) {
            FT_Set_Pixel_Sizes(mFtFaces[i], 0, mFontSize);
        }
    }
    // Create RGBA glyph atlas
    mAtlasW = 2048;
    mAtlasH = 2048;
    mAtlasCurX = 1; // start at 1 to avoid bleeding from edge
    mAtlasCurY = 1;
    mAtlasRowH = 0;
    glGenTextures(1, &mGlyphAtlasTex);
    glBindTexture(GL_TEXTURE_2D, mGlyphAtlasTex);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    std::vector<uint8_t> blank(mAtlasW * mAtlasH * 4, 0);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, mAtlasW, mAtlasH, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, blank.data());
    ALOGD("NanoMenu: font atlas %dx%d, %d faces loaded", mAtlasW, mAtlasH, mFtNumFaces);
}

// ---------------------------------------------------------------------------
// Glyph caching
// ---------------------------------------------------------------------------

void NanoMenu::ensureGlyph(uint32_t cp) {
    if (mGlyphCache.count(cp)) return;
    if (mFtNumFaces == 0) return;

    FT_Face face = nullptr;
    FT_UInt gi = 0;
    bool isColorFace = false;
    for (int i = 0; i < mFtNumFaces; i++) {
        gi = FT_Get_Char_Index(mFtFaces[i], cp);
        if (gi != 0) {
            face = mFtFaces[i];
            isColorFace = FT_HAS_COLOR(face);
            break;
        }
    }
    if (!face) {
        // Fallback to '?' in primary font
        face = mFtFaces[0];
        gi = FT_Get_Char_Index(face, '?');
        isColorFace = false;
    }
    if (!face || gi == 0) return;

    // For emoji, select a strike size close to our font size
    if (isColorFace && FT_HAS_FIXED_SIZES(face)) {
        int bestIdx = 0;
        int bestDiff = 99999;
        for (int i = 0; i < face->num_fixed_sizes; i++) {
            int diff = abs(face->available_sizes[i].height - mFontSize);
            if (diff < bestDiff) { bestDiff = diff; bestIdx = i; }
        }
        FT_Select_Size(face, bestIdx);
    } else if (!isColorFace) {
        FT_Set_Pixel_Sizes(face, 0, mFontSize);
    }

    FT_Int32 loadFlags = FT_LOAD_RENDER;
    if (isColorFace) loadFlags |= FT_LOAD_COLOR;
    if (FT_Load_Glyph(face, gi, loadFlags) != 0) return;

    FT_Bitmap* bmp = &face->glyph->bitmap;
    int bw = (int)bmp->width;
    int bh = (int)bmp->rows;
    bool isColor = (bmp->pixel_mode == FT_PIXEL_MODE_BGRA);

    // Pack into atlas (row-based, simple packer)
    if (bw > 0 && bh > 0) {
        if (mAtlasCurX + bw + 1 > mAtlasW) {
            mAtlasCurX = 1;
            mAtlasCurY += mAtlasRowH + 1;
            mAtlasRowH = 0;
        }
        if (mAtlasCurY + bh + 1 > mAtlasH) {
            ALOGW("NanoMenu: glyph atlas full at cp=%u", cp);
            return;
        }

        // Convert to RGBA
        std::vector<uint8_t> rgba(bw * bh * 4, 0);
        for (int y = 0; y < bh; y++) {
            for (int x = 0; x < bw; x++) {
                int di = (y * bw + x) * 4;
                if (isColor) {
                    int si = y * bmp->pitch + x * 4;
                    rgba[di + 0] = bmp->buffer[si + 2]; // B->R
                    rgba[di + 1] = bmp->buffer[si + 1]; // G->G
                    rgba[di + 2] = bmp->buffer[si + 0]; // R->B
                    rgba[di + 3] = bmp->buffer[si + 3]; // A
                } else {
                    uint8_t a = bmp->buffer[y * bmp->pitch + x];
                    rgba[di + 0] = 255;
                    rgba[di + 1] = 255;
                    rgba[di + 2] = 255;
                    rgba[di + 3] = a;
                }
            }
        }
        glBindTexture(GL_TEXTURE_2D, mGlyphAtlasTex);
        glTexSubImage2D(GL_TEXTURE_2D, 0, mAtlasCurX, mAtlasCurY,
                        bw, bh, GL_RGBA, GL_UNSIGNED_BYTE, rgba.data());
    }

    GlyphInfo info = {};
    if (bw > 0 && bh > 0) {
        info.u0 = (float)mAtlasCurX / mAtlasW;
        info.v0 = (float)mAtlasCurY / mAtlasH;
        info.u1 = (float)(mAtlasCurX + bw) / mAtlasW;
        info.v1 = (float)(mAtlasCurY + bh) / mAtlasH;
    }
    info.bmpW = bw;
    info.bmpH = bh;
    info.bearingX = face->glyph->bitmap_left;
    info.bearingY = face->glyph->bitmap_top;
    info.advance = (int)(face->glyph->advance.x >> 6);
    info.color = isColor;

    // For emoji, compute scale factor to normalize to mFontSize
    if (isColor && bh > 0) {
        float s = (float)mFontSize / (float)bh;
        info.scaleW = s;
        info.scaleH = s;
        info.advance = mFontSize; // square emoji advance
    } else {
        info.scaleW = 1.0f;
        info.scaleH = 1.0f;
    }

    mGlyphCache[cp] = info;

    if (bw > 0 && bh > 0) {
        mAtlasCurX += bw + 1;
        if (bh + 1 > mAtlasRowH) mAtlasRowH = bh + 1;
    }
}

// ---------------------------------------------------------------------------
// Text measurement and rendering
// ---------------------------------------------------------------------------

float NanoMenu::measureText(const char* str, float scale) {
    if (!str || !*str) return 0.0f;
    float pixelScale = (FONT_CHAR_H * scale) / (float)mFontSize;
    float width = 0.0f;
    for (const char* p = str; *p; ) {
        uint32_t cp;
        uint8_t b0 = (uint8_t)*p;
        if (b0 < 0x80) { cp = b0; p++; }
        else if ((b0 & 0xE0) == 0xC0) { cp = ((b0 & 0x1F) << 6) | (p[1] & 0x3F); p += 2; }
        else if ((b0 & 0xF0) == 0xE0) { cp = ((b0 & 0x0F) << 12) | ((p[1] & 0x3F) << 6) | (p[2] & 0x3F); p += 3; }
        else if ((b0 & 0xF8) == 0xF0) { cp = ((b0 & 0x07) << 18) | ((p[1] & 0x3F) << 12) | ((p[2] & 0x3F) << 6) | (p[3] & 0x3F); p += 4; }
        else { p++; continue; }
        ensureGlyph(cp);
        auto it = mGlyphCache.find(cp);
        if (it != mGlyphCache.end()) {
            width += it->second.advance * it->second.scaleW * pixelScale;
        }
    }
    return width;
}

// 5x capacity: 4 shadow passes + 1 main pass batched into one draw
static const int TEXT_MAX_CHARS = 256;
static const int TEXT_BUF_QUADS = TEXT_MAX_CHARS * 5;
static GLfloat sTextVerts[TEXT_BUF_QUADS * 6 * 2];
static GLfloat sTextUVs[TEXT_BUF_QUADS * 6 * 2];
static GLfloat sTextColors[TEXT_BUF_QUADS * 6 * 4];

// Helper: emit one glyph quad into the batch buffers at position n.
static inline void emitGlyph(int n, float x0, float y0, float x1, float y1,
                              float u0, float v0, float u1, float v1,
                              float cr, float cg, float cb, float ca) {
    int vi = n * 12;
    sTextVerts[vi]= x0; sTextVerts[vi+1]= y0;
    sTextVerts[vi+2]= x1; sTextVerts[vi+3]= y0;
    sTextVerts[vi+4]= x1; sTextVerts[vi+5]= y1;
    sTextVerts[vi+6]= x1; sTextVerts[vi+7]= y1;
    sTextVerts[vi+8]= x0; sTextVerts[vi+9]= y1;
    sTextVerts[vi+10]= x0; sTextVerts[vi+11]= y0;
    int ui = n * 12;
    sTextUVs[ui]= u0; sTextUVs[ui+1]= v1;
    sTextUVs[ui+2]= u1; sTextUVs[ui+3]= v1;
    sTextUVs[ui+4]= u1; sTextUVs[ui+5]= v0;
    sTextUVs[ui+6]= u1; sTextUVs[ui+7]= v0;
    sTextUVs[ui+8]= u0; sTextUVs[ui+9]= v0;
    sTextUVs[ui+10]= u0; sTextUVs[ui+11]= v1;
    int ci = n * 24;
    for (int v = 0; v < 6; v++) {
        sTextColors[ci + v*4] = cr;
        sTextColors[ci + v*4 + 1] = cg;
        sTextColors[ci + v*4 + 2] = cb;
        sTextColors[ci + v*4 + 3] = ca;
    }
}

void NanoMenu::drawText(const char* str, float px, float py, float scale,
                        float r, float g, float b, float a) {
    if (!str || !*str || mFtNumFaces == 0) return;
    float pixelScale = (FONT_CHAR_H * scale) / (float)mFontSize;
    float invW = 2.0f / mWidth, invH = 2.0f / mHeight;
    float baseline = py + mFontSize * pixelScale * 0.8f;
    float off = fmaxf(1.0f, scale * 0.4f);
    // Pixel offset in NDC
    float offX = off * invW;
    float offY = off * invH;

    // First pass: parse glyphs and compute base positions
    struct GlyphPos { float x0, y0, x1, y1, u0, v0, u1, v1; bool color; };
    GlyphPos glyphs[TEXT_MAX_CHARS];
    int nGlyphs = 0;
    float curX = px;
    for (const char* p = str; *p && nGlyphs < TEXT_MAX_CHARS; ) {
        uint32_t cp;
        uint8_t b0 = (uint8_t)*p;
        if (b0 < 0x80) { cp = b0; p++; }
        else if ((b0 & 0xE0) == 0xC0) { cp = ((b0 & 0x1F) << 6) | (p[1] & 0x3F); p += 2; }
        else if ((b0 & 0xF0) == 0xE0) { cp = ((b0 & 0x0F) << 12) | ((p[1] & 0x3F) << 6) | (p[2] & 0x3F); p += 3; }
        else if ((b0 & 0xF8) == 0xF0) { cp = ((b0 & 0x07) << 18) | ((p[1] & 0x3F) << 12) | ((p[2] & 0x3F) << 6) | (p[3] & 0x3F); p += 4; }
        else { p++; continue; }

        ensureGlyph(cp);
        auto it = mGlyphCache.find(cp);
        if (it == mGlyphCache.end()) continue;
        const GlyphInfo& gi = it->second;
        if (gi.bmpW == 0 || gi.bmpH == 0) {
            curX += gi.advance * gi.scaleW * pixelScale;
            continue;
        }

        float gw = gi.bmpW * gi.scaleW * pixelScale;
        float gh = gi.bmpH * gi.scaleH * pixelScale;
        float gx = curX + gi.bearingX * gi.scaleW * pixelScale;
        float gy = baseline - gi.bearingY * gi.scaleH * pixelScale;

        GlyphPos& gp = glyphs[nGlyphs];
        gp.x0 = gx * invW - 1.0f;
        gp.y0 = 1.0f - (gy + gh) * invH;
        gp.x1 = (gx + gw) * invW - 1.0f;
        gp.y1 = 1.0f - gy * invH;
        gp.u0 = gi.u0; gp.v0 = gi.v0;
        gp.u1 = gi.u1; gp.v1 = gi.v1;
        gp.color = gi.color;
        curX += gi.advance * gi.scaleW * pixelScale;
        nGlyphs++;
    }
    if (nGlyphs == 0) return;

    // Shadow pass selection:
    // - XMB mode uses a single drop shadow (+1,+1). The XMB ribbon background
    //   is dark and moving, so one offset is enough for readability and it
    //   cuts text geometry by 60% (2 passes vs 5). This is a hot path on
    //   Mali-G52: every visible game item emits one drawText call per frame,
    //   and the footer string alone is 76 glyphs.
    // - Normal menu mode keeps the 4-offset outline shadow since the flat
    //   menu background benefits from an omnidirectional outline for
    //   legibility against the blue selection bar.
    int n = 0;
    const float shadowA = a * 0.8f;
    const bool xmbShadow = mXmbMode;
    if (xmbShadow) {
        // Single drop shadow (down-right) — emits nGlyphs quads, vs 4*nGlyphs
        // in the default branch.
        const float sdx = offX;
        const float sdy = offY;
        for (int i = 0; i < nGlyphs && n < TEXT_BUF_QUADS; i++, n++) {
            const GlyphPos& gp = glyphs[i];
            emitGlyph(n, gp.x0 + sdx, gp.y0 + sdy, gp.x1 + sdx, gp.y1 + sdy,
                      gp.u0, gp.v0, gp.u1, gp.v1,
                      0.0f, 0.0f, 0.0f, shadowA);
        }
    } else {
        // 4-offset outline (left/right/up/down)
        static const float dirs[4][2] = {{-1,0},{1,0},{0,-1},{0,1}};
        for (int d = 0; d < 4; d++) {
            float dx = dirs[d][0] * offX;
            float dy = dirs[d][1] * offY;
            for (int i = 0; i < nGlyphs && n < TEXT_BUF_QUADS; i++, n++) {
                const GlyphPos& gp = glyphs[i];
                emitGlyph(n, gp.x0 + dx, gp.y0 + dy, gp.x1 + dx, gp.y1 + dy,
                          gp.u0, gp.v0, gp.u1, gp.v1,
                          0.0f, 0.0f, 0.0f, shadowA);
            }
        }
    }
    // Main pass (on top)
    for (int i = 0; i < nGlyphs && n < TEXT_BUF_QUADS; i++, n++) {
        const GlyphPos& gp = glyphs[i];
        float cr = gp.color ? 1.0f : r;
        float cg = gp.color ? 1.0f : g;
        float cb = gp.color ? 1.0f : b;
        emitGlyph(n, gp.x0, gp.y0, gp.x1, gp.y1,
                  gp.u0, gp.v0, gp.u1, gp.v1, cr, cg, cb, a);
    }

    glUseProgram(mTextProgram);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, mGlyphAtlasTex);
    glUniform1i(mTextLocTexture, 0);
    glVertexAttribPointer(mTextLocPosition, 2, GL_FLOAT, GL_FALSE, 0, sTextVerts);
    glEnableVertexAttribArray(mTextLocPosition);
    glVertexAttribPointer(mTextLocTexCoord, 2, GL_FLOAT, GL_FALSE, 0, sTextUVs);
    glEnableVertexAttribArray(mTextLocTexCoord);
    glVertexAttribPointer(mTextLocColor, 4, GL_FLOAT, GL_FALSE, 0, sTextColors);
    glEnableVertexAttribArray(mTextLocColor);
    glDrawArrays(GL_TRIANGLES, 0, n * 6);
    glDisableVertexAttribArray(mTextLocPosition);
    glDisableVertexAttribArray(mTextLocTexCoord);
    glDisableVertexAttribArray(mTextLocColor);
}

// ---------------------------------------------------------------------------
// Secondary display setup (post-boot)
// ---------------------------------------------------------------------------

// Create EGL window surfaces for every non-primary physical display so the
// existing post-HWC render loop can drive wallpaper rendering on those panels.
// Called once after drmStop() — before that point the DRM-direct path's
// secondary AHB is feeding those displays directly.
//
// Idempotent: returns immediately if surfaces are already set up.
//
// Concretely on the RG DS (RK3568, dual DSI 640x480):
//   - persist.gammaos.nano.primary_display=1 → port 1 holds the XMB.
//   - This function creates a wallpaper SurfaceControl on port 0's
//     layerStack and wraps it in an EGLSurface. The render loop renders
//     the wallpaper effect into it every frame.
//   - Bootanim is killed once the surface is up so it stops fighting for
//     the secondary display's layer stack.
void NanoMenu::setupSecondaryEglSurfaces() {
    if (!mSecondaryEglSurfaces.empty()) return; // already set up
    if (mDisplay == EGL_NO_DISPLAY) return;

    int64_t t0 = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;

    const std::vector<PhysicalDisplayId> ids =
            SurfaceComposerClient::getPhysicalDisplayIds();
    if (ids.size() <= 1) {
        ALOGI("NanoMenu: only %zu physical display(s); no secondary wallpaper",
              ids.size());
        return;
    }

    // Match the same primary_display port the readyToRun() path used so we
    // skip exactly the display the XMB renders on.
    int primaryPort = 0;
    {
        char p[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.primary_display", p, "0");
        primaryPort = atoi(p);
    }

    EGLConfig config = getEglConfig(mDisplay);
    if (config == nullptr) {
        ALOGW("NanoMenu: secondary setup failed — no EGL config");
        return;
    }

    for (const PhysicalDisplayId& pid : ids) {
        const int port = static_cast<int>(pid.getPort());
        if (port == primaryPort) continue;

        sp<IBinder> token = SurfaceComposerClient::getPhysicalDisplayToken(pid);
        if (token == nullptr) {
            ALOGW("NanoMenu: secondary port %d has no display token", port);
            continue;
        }

        ui::DisplayState state;
        ui::LayerStack stack = ui::DEFAULT_LAYER_STACK;
        if (SurfaceComposerClient::getDisplayState(token, &state) == NO_ERROR) {
            stack = state.layerStack;
        } else {
            ALOGW("NanoMenu: secondary port %d getDisplayState failed", port);
            continue;
        }

        DisplayMode mode;
        if (SurfaceComposerClient::getActiveDisplayMode(token, &mode) != NO_ERROR) {
            ALOGW("NanoMenu: secondary port %d getActiveDisplayMode failed", port);
            continue;
        }

        ui::Size res = mode.resolution;
        sp<SurfaceControl> sc = session()->createSurface(
                String8("GammaOSNanoWallpaper"),
                res.getWidth(), res.getHeight(),
                PIXEL_FORMAT_RGBX_8888, ISurfaceComposerClient::eOpaque);
        if (sc == nullptr || !sc->isValid()) {
            ALOGW("NanoMenu: secondary port %d createSurface failed", port);
            continue;
        }

        // Route to the secondary display's layer stack at top z so it
        // overrides bootanim (which uses STRATUM_BOOT_PROGRESS layers).
        SurfaceComposerClient::Transaction t;
        Rect bounds(0, 0, res.width, res.height);
        t.setDisplayProjection(token, ui::ROTATION_0, bounds, bounds);
        t.setLayer(sc, 0x40000001);
        t.setLayerStack(sc, stack);
        t.show(sc);
        t.apply();

        sp<Surface> s = sc->getSurface();
        EGLSurface eglSurf = eglCreateWindowSurface(mDisplay, config, s.get(), nullptr);
        if (eglSurf == EGL_NO_SURFACE) {
            ALOGW("NanoMenu: secondary port %d eglCreateWindowSurface failed: 0x%x",
                  port, eglGetError());
            continue;
        }

        // Set the secondary surface to swap interval 0 (no vsync wait) so its
        // swap doesn't block the primary 60fps loop. The wallpaper still
        // animates smoothly because the render loop runs every frame; we just
        // don't artificially serialize on the secondary's vsync.
        // eglSwapInterval applies to the surface that is currently bound, so
        // we need to make the secondary current temporarily.
        EGLSurface prevDraw = eglGetCurrentSurface(EGL_DRAW);
        EGLSurface prevRead = eglGetCurrentSurface(EGL_READ);
        EGLContext prevCtx  = eglGetCurrentContext();
        if (eglMakeCurrent(mDisplay, eglSurf, eglSurf, mContext) == EGL_TRUE) {
            eglSwapInterval(mDisplay, 0);
            // Restore primary as current.
            eglMakeCurrent(mDisplay, prevDraw, prevRead, prevCtx);
        }

        mSecondaryDisplayTokens.push_back(token);
        mSecondaryWallpaperControls.push_back(sc);
        mSecondarySurfaces.push_back(s);
        mSecondaryEglSurfaces.push_back(eglSurf);

        int64_t now = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
        ALOGI("NanoMenu: secondary EGL surface ready: port=%d layerStack=%u %dx%d at T+%lldms",
              port, stack.id, res.width, res.height, now);
    }

    if (!mSecondaryEglSurfaces.empty()) {
        // Tell bootanim to exit so it stops painting on the secondary display.
        // CRITICAL: do NOT set service.bootanim.exit — that property is also
        // nano's OWN exit signal, polled in threadLoop. Setting it here would
        // make nano shut itself down two frames after secondary setup. Use the
        // init ctl.stop command instead, which kills the bootanimation
        // service via init without touching the shared exit-signal property.
        property_set("ctl.stop", "bootanim");
        int64_t now = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
        ALOGI("NanoMenu: secondary wallpaper setup complete (%zu surfaces, %lldms)",
              mSecondaryEglSurfaces.size(), now - t0);
    }
}

// ---------------------------------------------------------------------------
// Render
// ---------------------------------------------------------------------------

void NanoMenu::render() {
    static bool sFirstFrame = true;
    if (sFirstFrame) {
        int64_t nowMs = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
        ALOGW("NanoMenu BOOT TIMING: first render() call at T+%lldms", nowMs);
    }

    // Once-per-frame state update for background effects (particle motion,
    // XMB ribbon time advance). Must run before either pass below so both
    // AHBs render the same effect state.
    updateEffect();

    // GammaOS: Helper lambda that uploads the DRM rotation matrix to all
    // shader programs. Called at the start of each render pass since the
    // rotation is global state that every program reads.
    auto uploadRotationMatrices = [this]() {
        const GLuint progs[] = {mShaderProgram, mTextProgram, mParticleProgram,
                                mFxProgram, mXmbProgram};
        const GLint  locs[]  = {mLocRotation, mTextLocRotation, mParticleLocRotation,
                                mFxLocRotation, mXmbLocRotation};
        for (int i = 0; i < 5; i++) {
            glUseProgram(progs[i]);
            glUniformMatrix2fv(locs[i], 1, GL_FALSE, sDrmRotMat);
        }
    };

    // GammaOS: Secondary display pass — wallpaper only, no menu/icons/text.
    // Runs only in DRM direct mode when a secondary AHB was allocated.
    // Renders into sAhbTargetSecondary which drmFlipAll() will blit to every
    // non-primary DRM display. This gives the secondary screen a clean
    // wallpaper view without paying for the menu geometry.
    //
    // The XMB ribbon / procedural effects are cheap fullscreen shaders on
    // Mali-G52, so rendering them twice (once here, once on the primary AHB
    // below) costs well under a millisecond total on 640x480.
    if (sDrmActive && sDrmZeroCopy && sAhbTargetSecondary.glFbo != 0) {
        glBindFramebuffer(GL_FRAMEBUFFER, sAhbTargetSecondary.glFbo);
        glViewport(0, 0, sAhbTargetSecondary.w, sAhbTargetSecondary.h);
        uploadRotationMatrices();
        glClearColor(0.05f, 0.05f, 0.10f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        renderEffect();
        glDisable(GL_BLEND);
    }

    // GammaOS: Primary pass — wallpaper + full menu (XMB or normal). When
    // DRM zero-copy is active, binds sAhbTarget (primary AHB). When post-
    // boot, the default SurfaceFlinger-backed FBO is used via the EGL path.
    if (sDrmActive && sDrmZeroCopy) {
        drmBindNextFbo();
    }
    // GammaOS: When GL rotation is active, use the AHB (panel-native) dimensions
    // for the viewport, not the logical mWidth/mHeight. The rotation matrix in the
    // vertex shaders maps logical NDC to the panel-native viewport.
    if (sDrmGlRotation) {
        glViewport(0, 0, sAhbTarget.w, sAhbTarget.h);
    } else {
        glViewport(0, 0, mWidth, mHeight);
    }
    uploadRotationMatrices();
    glClearColor(0.05f, 0.05f, 0.10f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    // Background effect on primary AHB.
    renderEffect();

    if (mXmbMode) {
        renderXmb();
    } else {

    // Responsive scaling: fit to both width and height so the menu
    // looks correct on any aspect ratio (4:3, 16:9, 16:10, 3:2, etc.)
    float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
    if (sf < 0.5f) sf = 0.5f;

    // Font scales
    float titleScale = 4.0f * sf;
    float subScale   = 2.0f * sf;
    float footScale  = 1.5f * sf;

    // Rebuild display items only when state changes (avoids per-frame heap allocs)
    if (mDisplayDirty) rebuildDisplayItems();

    // Storage readiness is polled by the outer loop (threadLoop) every ~0.5s.
    // No per-frame access() here — that syscall was costing ~2us at 60fps which
    // adds up on Cortex-A55 and is visible in perf traces during boot.

    float menuScale = 3.0f * sf;
    int currentSelected = (mMenuState == MENU_APPS) ? mAppSelectedIndex
                        : (mMenuState == MENU_RECENT) ? mRecentSelectedIndex : mSelectedIndex;
    int numItems = (int)mDisplayItems.size();

    // Element heights
    float titleH = FONT_CHAR_H * titleScale;
    float subH   = FONT_CHAR_H * subScale;
    float itemH  = FONT_CHAR_H * menuScale;
    float footH  = FONT_CHAR_H * footScale;

    // Gaps
    float gap1 = 10.0f * sf;   // title -> subtitle
    float gap2 = 20.0f * sf;   // subtitle -> separator
    float sepH = 2.0f * sf;
    float gap3 = 30.0f * sf;   // separator -> menu
    float itemSpacing = 12.0f * sf;

    // Place heading using the main menu's item count (7) so the title,
    // subtitle, separator, and footer stay at identical positions regardless
    // of which menu state is active.
    int layoutItems = 8; // main menu item count — used as the reference layout
    float menuContentH = titleH + gap1 + subH + gap2 + sepH + gap3
                        + layoutItems * itemH + (layoutItems - 1) * itemSpacing;
    float startY = (mHeight - menuContentH) / 6.0f;
    if (startY < 10.0f) startY = 10.0f;

    // Title
    float titleW = measureText(mTitle.c_str(), titleScale);
    float titleX = (mWidth - titleW) / 2.0f;
    float titleY = startY;
    drawText(mTitle.c_str(), titleX, titleY, titleScale,
             0.0f, 0.85f, 1.0f, 1.0f);

    // Subtitle
    float subW = measureText(mSubtitle.c_str(), subScale);
    float subX = (mWidth - subW) / 2.0f;
    float subY = titleY + titleH + gap1;
    drawText(mSubtitle.c_str(), subX, subY, subScale,
             0.5f, 0.5f, 0.6f, 1.0f);

    // Separator
    float sepY = subY + subH + gap2;
    drawQuad(mWidth * 0.1f, sepY, mWidth * 0.8f, sepH, 0.3f, 0.3f, 0.4f, 1.0f);

    // Menu items
    float menuStartY = sepY + sepH + gap3;
    float menuX = mWidth * 0.15f;

    // Available text width for menu items (from menuX to 85% of screen)
    float maxTextW = mWidth * 0.85f - menuX;
    float charW = FONT_CHAR_W * menuScale;

    // Calculate how many items fit on screen (between menu start and footer)
    float footY = mHeight - footH - startY;
    float availableH = footY - menuStartY - 10.0f * sf;
    int maxVisibleItems = (int)(availableH / (itemH + itemSpacing));
    if (maxVisibleItems < 1) maxVisibleItems = 1;

    // Vertical scrolling for submenus with more items than fit
    if (mMenuState != MENU_MAIN && numItems > maxVisibleItems) {
        // Ensure selected item is visible
        if (currentSelected < mMenuScrollTop) {
            mMenuScrollTop = currentSelected;
        } else if (currentSelected >= mMenuScrollTop + maxVisibleItems) {
            mMenuScrollTop = currentSelected - maxVisibleItems + 1;
        }
        // Clamp
        if (mMenuScrollTop > numItems - maxVisibleItems) {
            mMenuScrollTop = numItems - maxVisibleItems;
        }
        if (mMenuScrollTop < 0) mMenuScrollTop = 0;
    } else {
        mMenuScrollTop = 0;
    }

    int renderEnd = (mMenuState != MENU_MAIN && numItems > maxVisibleItems)
                  ? mMenuScrollTop + maxVisibleItems : numItems;
    if (renderEnd > numItems) renderEnd = numItems;

    for (int i = mMenuScrollTop; i < renderEnd; i++) {
        float itemY = menuStartY + (i - mMenuScrollTop) * (itemH + itemSpacing);
        bool selected = (i == currentSelected);

        // Grey out "Recently Played" and "Applications" in main menu when storage isn't ready
        bool greyed = (mMenuState == MENU_MAIN && !mStorageReady
                       && (mDisplayItems[i] == "Recently Played"
                           || mDisplayItems[i] == "Applications"));

        if (selected && !greyed) {
            drawQuad(mWidth * 0.10f, itemY - 4.0f * sf,
                     mWidth * 0.80f, itemH + 8.0f * sf,
                     0.0f, 0.35f, 0.6f, 0.8f);
        }
        const char* prefix = (selected && !greyed) ? "> " : "  ";
        float r, g, b;
        if (greyed) {
            r = 0.35f; g = 0.35f; b = 0.4f; // dimmed
        } else if (selected) {
            r = 1.0f; g = 1.0f; b = 1.0f;
        } else {
            r = 0.7f; g = 0.7f; b = 0.75f;
        }

        // Draw prefix at fixed position
        float prefixW = 2 * charW; // "> " or "  " is always 2 chars
        drawText(prefix, menuX, itemY, menuScale, r, g, b, 1.0f);

        // Content area: from after prefix to end of blue selection bar
        float contentLeft = menuX + prefixW;
        float contentRight = mWidth * 0.90f; // right edge of selection bar
        float contentW = contentRight - contentLeft;
        float textW = measureText(mDisplayItems[i].c_str(), menuScale);

        // Horizontal scroll for selected items that overflow (Recently Played)
        float drawX = contentLeft;
        bool scrolling = false;
        if (selected && !greyed
            && (mMenuState == MENU_RECENT || mMenuState == MENU_APPS)
            && textW > contentW
            && ((mMenuState == MENU_RECENT && i < (int)mRecentEntries.size())
                || (mMenuState == MENU_APPS && i < (int)mAppEntries.size()))) {
            scrolling = true;
            // Reset scroll when selection changes
            if (mLastScrolledIdx != i) {
                mLastScrolledIdx = i;
                mScrollOffset = 0.0f;
                mScrollDir = 1;
                mScrollPause = 60; // pause ~1s at start before scrolling
            }
            float overflow = textW - contentW;
            if (mScrollPause > 0) {
                mScrollPause--;
            } else {
                mScrollOffset += mScrollDir * 1.5f * sf; // scroll speed
                if (mScrollOffset >= overflow) {
                    mScrollOffset = overflow;
                    mScrollDir = -1;
                    mScrollPause = 60;
                } else if (mScrollOffset <= 0.0f) {
                    mScrollOffset = 0.0f;
                    mScrollDir = 1;
                    mScrollPause = 60;
                }
            }
            drawX = contentLeft - mScrollOffset;
        }

        // Scissor clip: all game entries in MENU_RECENT clip at the bar's right edge.
        // Selected items clip at both left and right (for scroll), unselected only right.
        bool needsClip = scrolling
            || ((mMenuState == MENU_RECENT && i < (int)mRecentEntries.size()
                 && textW > contentW)
                || (mMenuState == MENU_APPS && i < (int)mAppEntries.size()
                    && textW > contentW));
        if (needsClip) {
            glEnable(GL_SCISSOR_TEST);
            glScissor((int)contentLeft, 0, (int)contentW, mHeight);
        }
        drawText(mDisplayItems[i].c_str(), drawX, itemY, menuScale,
                 r, g, b, 1.0f);
        if (needsClip) {
            glDisable(GL_SCISSOR_TEST);
        }
    }

    // Footer (footY already computed above for scroll calculations)
    float footW = measureText(mFooter.c_str(), footScale);
    float footX = (mWidth - footW) / 2.0f;
    drawText(mFooter.c_str(), footX, footY, footScale, 0.4f, 0.4f, 0.5f, 1.0f);

    } // end !mXmbMode text menu

    // Brightness bar overlay
    renderBrightnessBar();
    renderVolumeBar();

    // Quick Resume indicator (top-right corner)
    {
        float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
        if (sf < 0.5f) sf = 0.5f;
        float qrScale = 1.5f * sf;
        float dotSize = 10.0f * sf;
        float pad = 15.0f * sf;
        const char* qrLabel = "Quick Resume";
        float qrLabelW = measureText(qrLabel, qrScale);
        float qrX = mWidth - qrLabelW - pad;
        float dotX = qrX + qrLabelW / 2.0f - dotSize / 2.0f;
        float dotY = pad;
        float labelY = dotY + dotSize + 5.0f * sf;
        if (mQuickResumeEnabled) {
            drawQuad(dotX, dotY, dotSize, dotSize, 0.0f, 0.85f, 0.0f, 1.0f);
            drawText(qrLabel, qrX, labelY, qrScale,
                     0.4f, 0.7f, 0.4f, 0.8f);
        } else {
            drawQuad(dotX, dotY, dotSize, dotSize, 0.85f, 0.0f, 0.0f, 1.0f);
            drawText(qrLabel, qrX, labelY, qrScale,
                     0.5f, 0.35f, 0.35f, 0.6f);
        }
    }

    glDisable(GL_BLEND);

    // GammaOS: DRM direct rendering path.
    // - Zero-copy: GPU rendered straight into the scanout FBO; just page flip.
    // - Fallback: glReadPixels → CPU copy to dumb buffer → page flip.
    // Either way, skip eglSwapBuffers (it blocks when HWC doesn't consume buffers).
    if (sDrmActive) {
        if (sDrmZeroCopy) {
            drmFlipAll(); // includes glFinish + CPU blit + page flip
        } else {
            drmPushFrame(mWidth, mHeight);
        }
        static int sFrameCount = 0;
        if (++sFrameCount % 30 == 0) {
            char bootDone[PROPERTY_VALUE_MAX] = {};
            property_get("sys.boot_completed", bootDone, "0");
            if (!strcmp(bootDone, "1")) {
                drmStop();
                // After HWC takes over, set up secondary EGL surfaces so the
                // wallpaper continues to render on non-XMB displays. Without
                // this, bootanim retains layer ownership on the secondary and
                // the user sees the GammaOS logo there indefinitely.
                setupSecondaryEglSurfaces();
            }
        }
    } else {
        eglSwapBuffers(mDisplay, mSurface);
        if (sFirstFrame) {
            int64_t nowMs = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
            ALOGW("NanoMenu BOOT TIMING: first eglSwapBuffers complete at T+%lldms", nowMs);
            sFirstFrame = false;
            // Restart path (returning from game): readyToRun() saw boot
            // already complete and skipped DRM splash, so the drmStop()
            // branch above never runs. Set up secondary EGL surfaces here
            // on the first EGL swap so the wallpaper renders on the
            // secondary display in restart sessions too. Idempotent — the
            // setup function returns immediately if already populated.
            if (mSecondaryEglSurfaces.empty()) {
                setupSecondaryEglSurfaces();
            }
        }
    }

    // GammaOS: Render wallpaper to secondary display(s).
    // Switch to each secondary EGL surface, render just the wallpaper effect, swap back.
    for (size_t i = 0; i < mSecondaryEglSurfaces.size(); i++) {
        eglMakeCurrent(mDisplay, mSecondaryEglSurfaces[i], mSecondaryEglSurfaces[i], mContext);
        glViewport(0, 0, mWidth, mHeight); // secondary has same resolution
        renderEffect(); // render just the wallpaper shader
        eglSwapBuffers(mDisplay, mSecondaryEglSurfaces[i]);
    }
    // Switch back to primary
    if (!mSecondaryEglSurfaces.empty()) {
        eglMakeCurrent(mDisplay, mSurface, mSurface, mContext);
    }
}

void NanoMenu::renderBrightnessBar() {
    if (!mShowBrightnessBar) return;
    if (--mBrightnessBarTimer <= 0) {
        mShowBrightnessBar = false;
        return;
    }

    float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
    if (sf < 0.5f) sf = 0.5f;

    float barW = 250.0f * sf;
    float barH = 20.0f * sf;
    float pad = 12.0f * sf;
    float iconScale = 1.5f * sf;
    float textScale = 1.5f * sf;
    float iconW = measureText("*", iconScale);
    float bgW = iconW + pad + barW + pad + 50.0f * sf;
    float bgH = barH + pad * 2;
    float bgX = (mWidth - bgW) / 2.0f;
    float bgY = pad;

    // Background
    drawQuad(bgX, bgY, bgW, bgH, 0.0f, 0.0f, 0.0f, 0.8f);

    // Sun icon "*"
    float iconX = bgX + pad;
    float iconY = bgY + (bgH - FONT_CHAR_H * iconScale) / 2.0f;
    drawText("*", iconX, iconY, iconScale, 1.0f, 0.9f, 0.3f, 1.0f);

    // Progress bar background
    float barX = iconX + iconW;
    float barY = bgY + (bgH - barH) / 2.0f;
    drawQuad(barX, barY, barW, barH, 0.3f, 0.3f, 0.3f, 1.0f);

    // Progress bar fill
    int pct = (mMaxBrightness > 0) ? (mBrightness * 100 / mMaxBrightness) : 0;
    float fillW = barW * pct / 100.0f;
    drawQuad(barX, barY, fillW, barH, 1.0f, 0.9f, 0.3f, 1.0f);

    // Percentage text
    char pctStr[8];
    snprintf(pctStr, sizeof(pctStr), "%d%%", pct);
    float textX = barX + barW + pad;
    float textY = bgY + (bgH - FONT_CHAR_H * textScale) / 2.0f;
    drawText(pctStr, textX, textY, textScale, 1.0f, 1.0f, 1.0f, 1.0f);
}

void NanoMenu::adjustVolume(int direction) {
    mVolume += direction;
    if (mVolume < 0) mVolume = 0;
    if (mVolume > mMaxVolume) mVolume = mMaxVolume;
    // Persist NanoMenu's own 0-mMaxVolume UI value for the bar display.
    char buf[32];
    snprintf(buf, sizeof(buf), "%d", mVolume);
    property_set("persist.gammaos.nano.volume", buf);

    // Actually tell Android's AudioService to change STREAM_MUSIC.
    // NanoMenu has the input devices grabbed via EVIOCGRAB, so physical volume
    // keys never reach PhoneWindowManager the usual way. We inject a synthetic
    // KeyEvent via `input keyevent`, which goes through InputManager →
    // InputDispatcher → PhoneWindowManager.handleVolumeKey → AudioService.
    // That path updates volume_music_speaker AND the gammaos per-display
    // volume map, so it persists across reboots.
    const char* keyCode = (direction > 0) ? "KEYCODE_VOLUME_UP" : "KEYCODE_VOLUME_DOWN";
    std::string cmd = std::string("/system/bin/input keyevent ") + keyCode + " 2>/dev/null";
    std::thread([cmd]() {
        int rc = system(cmd.c_str());
        if (rc != 0) {
            ALOGW("NanoMenu: input keyevent volume failed rc=%d", rc);
        }
    }).detach();

    mShowVolumeBar = true;
    mVolumeBarTimer = 90; // ~1.5s at 60fps
}

void NanoMenu::renderVolumeBar() {
    if (!mShowVolumeBar) return;
    if (--mVolumeBarTimer <= 0) {
        mShowVolumeBar = false;
        return;
    }

    float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
    if (sf < 0.5f) sf = 0.5f;

    // Same layout as brightness bar
    float barW = 250.0f * sf;
    float barH = 20.0f * sf;
    float pad = 12.0f * sf;
    float iconScale = 1.5f * sf;
    float textScale = 1.5f * sf;
    float iconW = measureText("*", iconScale); // same width reference
    float bgW = iconW + pad + barW + pad + 50.0f * sf;
    float bgH = barH + pad * 2;
    float bgX = (mWidth - bgW) / 2.0f;
    // Stack below brightness bar if both showing
    float bgY = mShowBrightnessBar ? (pad + bgH + pad) : pad;

    // Background
    drawQuad(bgX, bgY, bgW, bgH, 0.0f, 0.0f, 0.0f, 0.8f);

    // Volume icon
    float iconX = bgX + pad;
    float iconY = bgY + (bgH - FONT_CHAR_H * iconScale) / 2.0f;
    drawText(mVolume == 0 ? "x" : "+", iconX, iconY, iconScale,
             0.4f, 0.7f, 1.0f, 1.0f);

    // Progress bar background
    float barX = iconX + iconW;
    float barY = bgY + (bgH - barH) / 2.0f;
    drawQuad(barX, barY, barW, barH, 0.3f, 0.3f, 0.3f, 1.0f);

    // Progress bar fill
    int pct = (mMaxVolume > 0) ? (mVolume * 100 / mMaxVolume) : 0;
    float fillW = barW * pct / 100.0f;
    drawQuad(barX, barY, fillW, barH, 0.4f, 0.7f, 1.0f, 1.0f);

    // Percentage text
    char pctStr[8];
    snprintf(pctStr, sizeof(pctStr), "%d%%", pct);
    float textX = barX + barW + pad;
    float textY = bgY + (bgH - FONT_CHAR_H * textScale) / 2.0f;
    drawText(pctStr, textX, textY, textScale, 1.0f, 1.0f, 1.0f, 1.0f);
}

// ---------------------------------------------------------------------------
// XMB System Initialization
// ---------------------------------------------------------------------------

void NanoMenu::initXmbSystems() {
    mXmbSystems.clear();
    mXmbSystems.reserve(kNumXmbSystemDefs);
    for (int i = 0; i < kNumXmbSystemDefs; i++) {
        XmbSystem sys;
        sys.name = kXmbSystemDefs[i].name;
        sys.shortname = kXmbSystemDefs[i].shortname;
        sys.romDir = kXmbSystemDefs[i].romDir;
        sys.coreSo = kXmbSystemDefs[i].coreSo;
        sys.launchPkg = kXmbSystemDefs[i].launchPkg;
        sys.launchIntent = kXmbSystemDefs[i].launchIntent;
        sys.iconR = kXmbSystemDefs[i].r;
        sys.iconG = kXmbSystemDefs[i].g;
        sys.iconB = kXmbSystemDefs[i].b;
        sys.acceptExts = kXmbSystemDefs[i].acceptExts;
        sys.scanned = false;
        sys.pathExists = false;
        sys.lastScanTime = 0;

        // Allow prop overrides per system
        char propBuf[PROPERTY_VALUE_MAX] = {};
        char propKey[128];
        snprintf(propKey, sizeof(propKey), "persist.gammaos.nano.xmb.%s.dir",
                 kXmbSystemDefs[i].romDir);
        property_get(propKey, propBuf, "");
        if (propBuf[0]) sys.romDir = propBuf;

        snprintf(propKey, sizeof(propKey), "persist.gammaos.nano.xmb.%s.core",
                 kXmbSystemDefs[i].romDir);
        property_get(propKey, propBuf, "");
        if (propBuf[0]) sys.coreSo = propBuf;

        // Try loading cached file list from DE storage
        // Cache format: each line is a full ROM path.
        // Legacy compat: if line 1 is a directory and remaining lines
        // are bare filenames, prepend the directory to each filename.
        {
            std::string cachePath = "/data/system/nano_xmb_cache/" + sys.romDir + ".list";
            int cfd = open(cachePath.c_str(), O_RDONLY);
            if (cfd >= 0) {
                struct stat cst;
                if (fstat(cfd, &cst) == 0 && cst.st_size > 0 && cst.st_size < 512 * 1024) {
                    std::string content(cst.st_size, '\0');
                    ssize_t rd = read(cfd, &content[0], cst.st_size);
                    if (rd > 0) {
                        content.resize(rd);
                        std::vector<std::string> lines;
                        size_t pos = 0;
                        while (pos < content.size()) {
                            size_t eol = content.find('\n', pos);
                            if (eol == std::string::npos) eol = content.size();
                            std::string line = content.substr(pos, eol - pos);
                            pos = eol + 1;
                            if (!line.empty()) lines.push_back(std::move(line));
                        }
                        // Detect legacy format: first line is a directory,
                        // rest are bare filenames (no '/' in them)
                        std::string dirPrefix;
                        if (lines.size() >= 2 && lines[0].find('/') != std::string::npos) {
                            bool allBare = true;
                            for (size_t i = 1; i < lines.size(); i++) {
                                if (lines[i].find('/') != std::string::npos) {
                                    allBare = false;
                                    break;
                                }
                            }
                            if (allBare) {
                                dirPrefix = lines[0];
                                // Remove trailing slash if present
                                if (!dirPrefix.empty() && dirPrefix.back() == '/')
                                    dirPrefix.pop_back();
                                lines.erase(lines.begin());
                                ALOGD("NanoMenu: %s: legacy cache format, dir=%s",
                                      sys.name.c_str(), dirPrefix.c_str());
                            }
                        }
                        for (auto& l : lines) {
                            if (!dirPrefix.empty())
                                sys.roms.push_back(dirPrefix + "/" + l);
                            else
                                sys.roms.push_back(std::move(l));
                        }
                        // Derive activePath from the directory of the first ROM
                        if (!sys.roms.empty()) {
                            size_t ls = sys.roms[0].rfind('/');
                            if (ls != std::string::npos) {
                                sys.activePath = sys.roms[0].substr(0, ls);
                            }
                            sys.pathExists = true;
                        }
                        // Pre-compute display names from cache (strip path + extension)
                        for (const auto& rom : sys.roms) {
                            std::string dn = rom;
                            size_t sl = dn.rfind('/');
                            if (sl != std::string::npos) dn = dn.substr(sl + 1);
                            size_t d = dn.rfind('.');
                            if (d != std::string::npos) dn = dn.substr(0, d);
                            sys.displayNames.push_back(std::move(dn));
                        }
                        if (!sys.roms.empty()) {
                            ALOGD("NanoMenu: %s: loaded %zu ROMs from cache",
                                  sys.name.c_str(), sys.roms.size());
                        }
                    }
                }
                close(cfd);
            }
        }

        // If cache had ROMs, mark as scanned so we don't clear them
        // during early boot. Background rescan will update after boot_completed.
        if (!sys.roms.empty()) {
            sys.scanned = true;
        }

        mXmbSystems.push_back(std::move(sys));
    }
    ALOGD("NanoMenu: initialized %d XMB systems", (int)mXmbSystems.size());

    // If all systems loaded from cache, mark scan as done
    bool allCached = true;
    for (const auto& s : mXmbSystems) {
        if (!s.scanned) { allCached = false; break; }
    }
    if (allCached) mXmbRomScanDone = true;
}

// ---------------------------------------------------------------------------
// ROM Path Scanning
// ---------------------------------------------------------------------------

// Helper: find a case-insensitive match for 'target' in directory 'parent'
static std::string findCaseInsensitive(const std::string& parent, const std::string& target) {
    DIR* d = opendir(parent.c_str());
    if (!d) return "";
    struct dirent* e;
    while ((e = readdir(d)) != nullptr) {
        if (strcasecmp(e->d_name, target.c_str()) == 0) {
            std::string result = parent + "/" + e->d_name;
            closedir(d);
            return result;
        }
    }
    closedir(d);
    return "";
}

void NanoMenu::scanRomPaths() {
    ALOGD("NanoMenu: scanning ROM paths");
    for (auto& sys : mXmbSystems) {
        if (sys.scanned) continue;

        const std::string romDir = sys.romDir;

        // Build candidate paths — these are directories to scan for ROMs.
        // We scan ALL accessible paths (not just the best one) and merge results.
        std::vector<std::string> scanPaths;

        // 1. Internal storage (raw + FUSE)
        scanPaths.push_back("/data/media/0/ROMs/" + romDir);
        scanPaths.push_back("/sdcard/ROMs/" + romDir);
        scanPaths.push_back("/storage/emulated/0/ROMs/" + romDir);

        // 2. External volumes — case-insensitive matching for ROMs dir and system dir
        auto addExternalVolume = [&](const std::string& base) {
            // Try exact paths first (fast)
            scanPaths.push_back(base + "/ROMs/" + romDir);
            scanPaths.push_back(base + "/roms/" + romDir);
            scanPaths.push_back(base + "/" + romDir);
            // Case-insensitive: find ROMs-like folder, then system folder within
            std::string romsDir = findCaseInsensitive(base, "ROMs");
            if (!romsDir.empty()) {
                std::string sysDir = findCaseInsensitive(romsDir, romDir);
                if (!sysDir.empty()) scanPaths.push_back(sysDir);
            }
            // Also try system dir directly at volume root (case-insensitive)
            std::string directDir = findCaseInsensitive(base, romDir);
            if (!directDir.empty()) scanPaths.push_back(directDir);
        };

        {
            DIR* storageDir = opendir("/storage");
            if (storageDir) {
                struct dirent* sEntry;
                while ((sEntry = readdir(storageDir)) != nullptr) {
                    if (sEntry->d_name[0] == '.') continue;
                    if (!strcmp(sEntry->d_name, "emulated")) continue;
                    if (!strcmp(sEntry->d_name, "self")) continue;
                    std::string base = std::string("/storage/") + sEntry->d_name;
                    addExternalVolume(base);
                }
                closedir(storageDir);
            }
            DIR* mntDir = opendir("/mnt/media_rw");
            if (mntDir) {
                struct dirent* mEntry;
                while ((mEntry = readdir(mntDir)) != nullptr) {
                    if (mEntry->d_name[0] == '.') continue;
                    std::string base = std::string("/mnt/media_rw/") + mEntry->d_name;
                    addExternalVolume(base);
                }
                closedir(mntDir);
            }
        }

        // 3. Prop-overridden custom path
        char customPath[PROPERTY_VALUE_MAX] = {};
        char propKey[128];
        snprintf(propKey, sizeof(propKey), "persist.gammaos.nano.xmb.%s.path", romDir.c_str());
        property_get(propKey, customPath, "");
        if (customPath[0]) {
            // Custom path gets highest priority — insert at front
            scanPaths.insert(scanPaths.begin(), std::string(customPath));
        }

        // Deduplicate candidate paths (realpath-based would be ideal but too slow;
        // just skip exact string duplicates)
        {
            std::set<std::string> seen;
            std::vector<std::string> unique;
            for (auto& p : scanPaths) {
                if (seen.insert(p).second) unique.push_back(std::move(p));
            }
            scanPaths = std::move(unique);
        }

        // Build extension set from comma-separated list
        std::set<std::string> exts;
        {
            const std::string& extStr = sys.acceptExts;
            size_t pos = 0;
            while (pos < extStr.size()) {
                size_t comma = extStr.find(',', pos);
                if (comma == std::string::npos) comma = extStr.size();
                std::string ext = extStr.substr(pos, comma - pos);
                while (!ext.empty() && ext[0] == ' ') ext.erase(0, 1);
                if (!ext.empty()) exts.insert(ext);
                pos = comma + 1;
            }
        }
        exts.insert(".zip");
        exts.insert(".7z");

        // Clear any cached data — fresh scan replaces it
        sys.roms.clear();
        sys.displayNames.clear();
        sys.activePaths.clear();

        // Track filenames already seen (case-insensitive) for deduplication
        std::set<std::string> seenFilenames;
        // Per-path ROM count for determining activePath (largest collection)
        std::string bestPath;
        int bestCount = 0;
        bool anyPath = false;

        // Scan ALL candidate paths and merge results
        for (const auto& candidatePath : scanPaths) {
            DIR* dir = opendir(candidatePath.c_str());
            if (!dir) continue;

            int pathRomCount = 0;
            struct dirent* entry;
            while ((entry = readdir(dir)) != nullptr) {
                if (entry->d_name[0] == '.') continue;
                if (entry->d_type == DT_DIR) continue;

                std::string name(entry->d_name);
                size_t dot = name.rfind('.');
                if (dot == std::string::npos) continue;

                // Skip known non-ROM files
                std::string ext = name.substr(dot);
                for (size_t i = 0; i < ext.size(); i++) {
                    if (ext[i] >= 'A' && ext[i] <= 'Z') ext[i] += 32;
                }
                if (ext == ".txt" || ext == ".jpg" || ext == ".png" || ext == ".xml"
                    || ext == ".srm" || ext == ".sav" || ext == ".state" || ext == ".rtc"
                    || ext == ".dat" || ext == ".bak" || ext == ".cfg" || ext == ".log") {
                    continue;
                }

                if (!exts.count(ext)) continue;

                // Deduplicate by filename (case-insensitive) — first found wins
                std::string nameLower = name;
                for (size_t i = 0; i < nameLower.size(); i++) {
                    if (nameLower[i] >= 'A' && nameLower[i] <= 'Z') nameLower[i] += 32;
                }
                if (!seenFilenames.insert(nameLower).second) continue;

                // Skip 0-byte files (dummy/placeholder files)
                std::string fullPath = candidatePath + "/" + name;
                {
                    struct stat st;
                    if (stat(fullPath.c_str(), &st) == 0 && st.st_size == 0) continue;
                }

                sys.roms.push_back(fullPath);
                pathRomCount++;
            }
            closedir(dir);

            if (pathRomCount > 0) {
                sys.activePaths.push_back(candidatePath);
                anyPath = true;
                if (pathRomCount > bestCount) {
                    bestCount = pathRomCount;
                    bestPath = candidatePath;
                }
            }
        }

        if (!anyPath) {
            sys.pathExists = false;
            ALOGD("NanoMenu: %s: no accessible path found (will retry)", sys.name.c_str());
            continue;
        }

        sys.scanned = true;
        sys.pathExists = true;
        sys.activePath = bestPath;
        sys.lastScanTime = elapsedRealtime();

        // Sort by display name (case-insensitive) — extract filename, strip extension
        std::sort(sys.roms.begin(), sys.roms.end(),
                  [](const std::string& a, const std::string& b) {
                      // Extract filename from full path
                      size_t sa = a.rfind('/');
                      size_t sb = b.rfind('/');
                      const char* na = (sa != std::string::npos) ? a.c_str() + sa + 1 : a.c_str();
                      const char* nb = (sb != std::string::npos) ? b.c_str() + sb + 1 : b.c_str();
                      // Case-insensitive compare
                      for (size_t i = 0; na[i] && nb[i]; i++) {
                          char ca = na[i], cb = nb[i];
                          if (ca >= 'A' && ca <= 'Z') ca += 32;
                          if (cb >= 'A' && cb <= 'Z') cb += 32;
                          if (ca != cb) return ca < cb;
                      }
                      // Shorter name first if prefixes match
                      size_t la = strlen(na), lb = strlen(nb);
                      return la < lb;
                  });

        // Pre-compute display names (strip path + extension)
        sys.displayNames.reserve(sys.roms.size());
        for (const auto& rom : sys.roms) {
            std::string dn = rom;
            size_t sl = dn.rfind('/');
            if (sl != std::string::npos) dn = dn.substr(sl + 1);
            size_t d = dn.rfind('.');
            if (d != std::string::npos) dn = dn.substr(0, d);
            sys.displayNames.push_back(std::move(dn));
        }

        ALOGD("NanoMenu: %s: %zu ROMs across %zu paths (primary: %s)",
              sys.name.c_str(), sys.roms.size(), sys.activePaths.size(),
              bestPath.c_str());

        // Save cache to DE storage — each line is a full ROM path
        {
            std::string cacheDir = "/data/system/nano_xmb_cache";
            mkdir(cacheDir.c_str(), 0755);
            std::string cachePath = cacheDir + "/" + sys.romDir + ".list";
            int cfd = open(cachePath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
            if (cfd >= 0) {
                for (const auto& rom : sys.roms) {
                    std::string line = rom + "\n";
                    write(cfd, line.c_str(), line.size());
                }
                close(cfd);
            }
        }
    }
    // Only mark scan complete if all systems were scanned
    bool allScanned = true;
    for (const auto& s : mXmbSystems) {
        if (!s.scanned) { allScanned = false; break; }
    }
    mXmbRomScanDone = allScanned;
}

// Scan a single system's ROM paths into temp buffers. Only updates
// the system's rom/displayNames/activePath if the result differs from
// the current data. Returns true if the system was updated.
bool NanoMenu::scanOneSystemAsync(int sysIdx) {
    if (sysIdx < 0 || sysIdx >= (int)mXmbSystems.size()) return false;
    auto& sys = mXmbSystems[sysIdx];
    const std::string romDir = sys.romDir;

    // Build candidate paths (same logic as scanRomPaths)
    std::vector<std::string> scanPaths;
    scanPaths.push_back("/data/media/0/ROMs/" + romDir);
    scanPaths.push_back("/sdcard/ROMs/" + romDir);
    scanPaths.push_back("/storage/emulated/0/ROMs/" + romDir);

    auto addExternalVolume = [&](const std::string& base) {
        scanPaths.push_back(base + "/ROMs/" + romDir);
        scanPaths.push_back(base + "/roms/" + romDir);
        scanPaths.push_back(base + "/" + romDir);
        std::string romsDir = findCaseInsensitive(base, "ROMs");
        if (!romsDir.empty()) {
            std::string sysDir = findCaseInsensitive(romsDir, romDir);
            if (!sysDir.empty()) scanPaths.push_back(sysDir);
        }
        std::string directDir = findCaseInsensitive(base, romDir);
        if (!directDir.empty()) scanPaths.push_back(directDir);
    };

    {
        DIR* storageDir = opendir("/storage");
        if (storageDir) {
            struct dirent* sEntry;
            while ((sEntry = readdir(storageDir)) != nullptr) {
                if (sEntry->d_name[0] == '.') continue;
                if (!strcmp(sEntry->d_name, "emulated")) continue;
                if (!strcmp(sEntry->d_name, "self")) continue;
                addExternalVolume(std::string("/storage/") + sEntry->d_name);
            }
            closedir(storageDir);
        }
        DIR* mntDir = opendir("/mnt/media_rw");
        if (mntDir) {
            struct dirent* mEntry;
            while ((mEntry = readdir(mntDir)) != nullptr) {
                if (mEntry->d_name[0] == '.') continue;
                addExternalVolume(std::string("/mnt/media_rw/") + mEntry->d_name);
            }
            closedir(mntDir);
        }
    }

    char customPath[PROPERTY_VALUE_MAX] = {};
    char propKey[128];
    snprintf(propKey, sizeof(propKey), "persist.gammaos.nano.xmb.%s.path", romDir.c_str());
    property_get(propKey, customPath, "");
    if (customPath[0]) scanPaths.insert(scanPaths.begin(), std::string(customPath));

    // Deduplicate paths
    {
        std::set<std::string> seen;
        std::vector<std::string> unique;
        for (auto& p : scanPaths) {
            if (seen.insert(p).second) unique.push_back(std::move(p));
        }
        scanPaths = std::move(unique);
    }

    // Build extension set
    std::set<std::string> exts;
    {
        const std::string& extStr = sys.acceptExts;
        size_t pos = 0;
        while (pos < extStr.size()) {
            size_t comma = extStr.find(',', pos);
            if (comma == std::string::npos) comma = extStr.size();
            std::string ext = extStr.substr(pos, comma - pos);
            while (!ext.empty() && ext[0] == ' ') ext.erase(0, 1);
            if (!ext.empty()) exts.insert(ext);
            pos = comma + 1;
        }
    }
    exts.insert(".zip");
    exts.insert(".7z");

    // Scan into TEMP buffers (don't touch sys.roms yet)
    std::vector<std::string> newRoms;
    std::vector<std::string> newActivePaths;
    std::set<std::string> seenFilenames;
    std::string newBestPath;
    int bestCount = 0;

    for (const auto& candidatePath : scanPaths) {
        DIR* dir = opendir(candidatePath.c_str());
        if (!dir) continue;

        int pathRomCount = 0;
        struct dirent* entry;
        while ((entry = readdir(dir)) != nullptr) {
            if (entry->d_name[0] == '.') continue;
            if (entry->d_type == DT_DIR) continue;

            std::string name(entry->d_name);
            size_t dot = name.rfind('.');
            if (dot == std::string::npos) continue;

            std::string ext = name.substr(dot);
            for (size_t i = 0; i < ext.size(); i++) {
                if (ext[i] >= 'A' && ext[i] <= 'Z') ext[i] += 32;
            }
            if (ext == ".txt" || ext == ".jpg" || ext == ".png" || ext == ".xml"
                || ext == ".srm" || ext == ".sav" || ext == ".state" || ext == ".rtc"
                || ext == ".dat" || ext == ".bak" || ext == ".cfg" || ext == ".log") {
                continue;
            }
            if (!exts.count(ext)) continue;

            std::string nameLower = name;
            for (size_t i = 0; i < nameLower.size(); i++) {
                if (nameLower[i] >= 'A' && nameLower[i] <= 'Z') nameLower[i] += 32;
            }
            if (!seenFilenames.insert(nameLower).second) continue;

            std::string fullPath = candidatePath + "/" + name;
            {
                struct stat st;
                if (stat(fullPath.c_str(), &st) == 0 && st.st_size == 0) continue;
            }

            newRoms.push_back(fullPath);
            pathRomCount++;
        }
        closedir(dir);

        if (pathRomCount > 0) {
            newActivePaths.push_back(candidatePath);
            if (pathRomCount > bestCount) {
                bestCount = pathRomCount;
                newBestPath = candidatePath;
            }
        }
    }

    // Sort by display name
    std::sort(newRoms.begin(), newRoms.end(),
              [](const std::string& a, const std::string& b) {
                  size_t sa = a.rfind('/');
                  size_t sb = b.rfind('/');
                  const char* na = (sa != std::string::npos) ? a.c_str() + sa + 1 : a.c_str();
                  const char* nb = (sb != std::string::npos) ? b.c_str() + sb + 1 : b.c_str();
                  for (size_t i = 0; na[i] && nb[i]; i++) {
                      char ca = na[i], cb = nb[i];
                      if (ca >= 'A' && ca <= 'Z') ca += 32;
                      if (cb >= 'A' && cb <= 'Z') cb += 32;
                      if (ca != cb) return ca < cb;
                  }
                  return strlen(na) < strlen(nb);
              });

    ALOGD("NanoMenu: %s: scan found %zu ROMs across %zu paths (current: %zu ROMs)",
          sys.name.c_str(), newRoms.size(), newActivePaths.size(), sys.roms.size());

    // Check if result differs from current data
    bool changed = (newRoms != sys.roms);

    // Guard against downgrading cached data when storage is partially
    // mounted (e.g., SD card not yet available after reboot). Only
    // replace with fewer ROMs if the new scan found at least as many
    // source directories. Otherwise storage likely isn't fully mounted.
    if (changed && !newRoms.empty() && newRoms.size() < sys.roms.size()
        && sys.scanned) {
        // Check if new scan covered as many directories as before.
        // activePaths may be empty (cache-loaded), so fall back to
        // comparing new path count against unique dirs in current ROMs.
        size_t curPathCount = sys.activePaths.size();
        if (curPathCount == 0 && !sys.roms.empty()) {
            // Estimate from current ROM paths
            std::set<std::string> dirs;
            for (const auto& r : sys.roms) {
                size_t sl = r.rfind('/');
                if (sl != std::string::npos) dirs.insert(r.substr(0, sl));
            }
            curPathCount = dirs.size();
        }
        if (newActivePaths.size() < curPathCount) {
            // Fewer paths scanned — storage not fully mounted yet.
            sys.lastScanTime = elapsedRealtime();
            return false;
        }
    }

    if (changed || !sys.scanned) {
        // Swap in new data atomically (fast — just pointer swaps)
        sys.roms = std::move(newRoms);
        sys.activePaths = std::move(newActivePaths);
        sys.activePath = newBestPath;
        sys.pathExists = !sys.roms.empty();

        // Rebuild display names
        sys.displayNames.clear();
        sys.displayNames.reserve(sys.roms.size());
        for (const auto& rom : sys.roms) {
            std::string dn = rom;
            size_t sl = dn.rfind('/');
            if (sl != std::string::npos) dn = dn.substr(sl + 1);
            size_t d = dn.rfind('.');
            if (d != std::string::npos) dn = dn.substr(0, d);
            sys.displayNames.push_back(std::move(dn));
        }

        // Update cache file
        std::string cacheDir = "/data/system/nano_xmb_cache";
        mkdir(cacheDir.c_str(), 0755);
        std::string cachePath = cacheDir + "/" + sys.romDir + ".list";
        int cfd = open(cachePath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
        if (cfd >= 0) {
            for (const auto& rom : sys.roms) {
                std::string line = rom + "\n";
                write(cfd, line.c_str(), line.size());
            }
            close(cfd);
        }

        if (changed) {
            ALOGD("NanoMenu: %s: %zu ROMs across %zu paths (primary: %s)",
                  sys.name.c_str(), sys.roms.size(), sys.activePaths.size(),
                  sys.activePath.c_str());
            mDisplayDirty = true;
        }
    }

    sys.scanned = true;
    sys.lastScanTime = elapsedRealtime();
    return changed;
}

void NanoMenu::forceRescanAllSystems() {
    // Launch a background thread to scan all systems. The thread builds
    // results in mBgScanResults; the render loop swaps them in when ready.
    if (mBgScanThreadRunning) return; // already scanning
    mBgScanThreadRunning = true;
    ALOGI("NanoMenu: launching background scan thread");
    std::thread(&NanoMenu::bgScanThreadFunc, this).detach();
}

// Background thread: scans all systems and stores results for the render
// thread to pick up. Never touches sys.roms/displayNames directly — only
// writes to mBgScanResults behind a mutex.
void NanoMenu::bgScanThreadFunc() {
    int numSys = (int)mXmbSystems.size();
    std::vector<BgScanResult> results(numSys);

    for (int si = 0; si < numSys; si++) {
        const auto& sys = mXmbSystems[si];
        const std::string romDir = sys.romDir;
        auto& res = results[si];
        res.valid = false;

        // Build candidate paths (same logic as scanOneSystemAsync)
        std::vector<std::string> scanPaths;
        scanPaths.push_back("/data/media/0/ROMs/" + romDir);
        scanPaths.push_back("/sdcard/ROMs/" + romDir);
        scanPaths.push_back("/storage/emulated/0/ROMs/" + romDir);

        auto addVol = [&](const std::string& base) {
            scanPaths.push_back(base + "/ROMs/" + romDir);
            scanPaths.push_back(base + "/roms/" + romDir);
            scanPaths.push_back(base + "/" + romDir);
            std::string romsDir = findCaseInsensitive(base, "ROMs");
            if (!romsDir.empty()) {
                std::string sd = findCaseInsensitive(romsDir, romDir);
                if (!sd.empty()) scanPaths.push_back(sd);
            }
            std::string dd = findCaseInsensitive(base, romDir);
            if (!dd.empty()) scanPaths.push_back(dd);
        };

        {
            DIR* d = opendir("/storage");
            if (d) {
                struct dirent* e;
                while ((e = readdir(d)) != nullptr) {
                    if (e->d_name[0] == '.') continue;
                    if (!strcmp(e->d_name, "emulated")) continue;
                    if (!strcmp(e->d_name, "self")) continue;
                    addVol(std::string("/storage/") + e->d_name);
                }
                closedir(d);
            }
            d = opendir("/mnt/media_rw");
            if (d) {
                struct dirent* e;
                while ((e = readdir(d)) != nullptr) {
                    if (e->d_name[0] == '.') continue;
                    addVol(std::string("/mnt/media_rw/") + e->d_name);
                }
                closedir(d);
            }
        }

        char customPath[PROPERTY_VALUE_MAX] = {};
        char propKey[128];
        snprintf(propKey, sizeof(propKey), "persist.gammaos.nano.xmb.%s.path",
                 romDir.c_str());
        property_get(propKey, customPath, "");
        if (customPath[0])
            scanPaths.insert(scanPaths.begin(), std::string(customPath));

        // Deduplicate
        { std::set<std::string> seen;
          std::vector<std::string> uniq;
          for (auto& p : scanPaths)
              if (seen.insert(p).second) uniq.push_back(std::move(p));
          scanPaths = std::move(uniq);
        }

        // Build extension set
        std::set<std::string> exts;
        { const std::string& es = sys.acceptExts;
          size_t pos = 0;
          while (pos < es.size()) {
              size_t c = es.find(',', pos);
              if (c == std::string::npos) c = es.size();
              std::string ext = es.substr(pos, c - pos);
              while (!ext.empty() && ext[0] == ' ') ext.erase(0, 1);
              if (!ext.empty()) exts.insert(ext);
              pos = c + 1;
          }
        }
        exts.insert(".zip");
        exts.insert(".7z");

        // Scan all paths, merge
        std::set<std::string> seenNames;
        std::string bestPath;
        int bestCount = 0;

        for (const auto& cp : scanPaths) {
            DIR* dir = opendir(cp.c_str());
            if (!dir) continue;
            int cnt = 0;
            struct dirent* entry;
            while ((entry = readdir(dir)) != nullptr) {
                if (entry->d_name[0] == '.') continue;
                if (entry->d_type == DT_DIR) continue;
                std::string name(entry->d_name);
                size_t dot = name.rfind('.');
                if (dot == std::string::npos) continue;
                std::string ext = name.substr(dot);
                for (size_t i = 0; i < ext.size(); i++)
                    if (ext[i] >= 'A' && ext[i] <= 'Z') ext[i] += 32;
                if (ext == ".txt" || ext == ".jpg" || ext == ".png" || ext == ".xml"
                    || ext == ".srm" || ext == ".sav" || ext == ".state" || ext == ".rtc"
                    || ext == ".dat" || ext == ".bak" || ext == ".cfg" || ext == ".log")
                    continue;
                if (!exts.count(ext)) continue;
                std::string nl = name;
                for (size_t i = 0; i < nl.size(); i++)
                    if (nl[i] >= 'A' && nl[i] <= 'Z') nl[i] += 32;
                if (!seenNames.insert(nl).second) continue;
                std::string fp = cp + "/" + name;
                struct stat st;
                if (stat(fp.c_str(), &st) == 0 && st.st_size == 0) continue;
                res.roms.push_back(fp);
                cnt++;
            }
            closedir(dir);
            if (cnt > 0) {
                res.activePaths.push_back(cp);
                res.valid = true;
                if (cnt > bestCount) { bestCount = cnt; bestPath = cp; }
            }
        }
        res.activePath = bestPath;

        // Sort by display name
        std::sort(res.roms.begin(), res.roms.end(),
                  [](const std::string& a, const std::string& b) {
                      size_t sa = a.rfind('/'), sb = b.rfind('/');
                      const char* na = sa != std::string::npos ? a.c_str()+sa+1 : a.c_str();
                      const char* nb = sb != std::string::npos ? b.c_str()+sb+1 : b.c_str();
                      for (size_t i = 0; na[i] && nb[i]; i++) {
                          char ca = na[i], cb = nb[i];
                          if (ca >= 'A' && ca <= 'Z') ca += 32;
                          if (cb >= 'A' && cb <= 'Z') cb += 32;
                          if (ca != cb) return ca < cb;
                      }
                      return strlen(na) < strlen(nb);
                  });

        // Build display names
        res.displayNames.reserve(res.roms.size());
        for (const auto& rom : res.roms) {
            std::string dn = rom;
            size_t sl = dn.rfind('/');
            if (sl != std::string::npos) dn = dn.substr(sl + 1);
            size_t d = dn.rfind('.');
            if (d != std::string::npos) dn = dn.substr(0, d);
            res.displayNames.push_back(std::move(dn));
        }
    }

    // Publish results for the render thread
    {
        std::lock_guard<std::mutex> lock(mBgScanMutex);
        mBgScanResults = std::move(results);
        mBgScanResultReady = true;
    }
    mBgScanThreadRunning = false;
    ALOGI("NanoMenu: background scan thread complete");
}

// ---------------------------------------------------------------------------
// XMB Recently Played
// ---------------------------------------------------------------------------

static const char* kXmbRecentFile = "/data/system/nano_xmb_recent.list";

void NanoMenu::loadXmbRecent() {
    mXmbRecent.clear();
    int fd = open(kXmbRecentFile, O_RDONLY);
    if (fd < 0) return;
    struct stat st;
    if (fstat(fd, &st) != 0 || st.st_size == 0 || st.st_size > 256 * 1024) {
        close(fd); return;
    }
    std::string content(st.st_size, '\0');
    ssize_t rd = read(fd, &content[0], st.st_size);
    close(fd);
    if (rd <= 0) return;
    content.resize(rd);

    // Format: one entry per 7 lines (romPath, coreSo, launchPkg, launchIntent,
    //         displayName, systemName, romDir) separated by \n, entries by \n\n
    size_t pos = 0;
    while (pos < content.size() && (int)mXmbRecent.size() < mXmbRecentMax) {
        XmbRecentEntry e;
        auto readLine = [&]() -> std::string {
            if (pos >= content.size()) return {};
            size_t eol = content.find('\n', pos);
            if (eol == std::string::npos) eol = content.size();
            std::string line = content.substr(pos, eol - pos);
            pos = eol + 1;
            return line;
        };
        e.romPath = readLine();
        if (e.romPath.empty()) { pos++; continue; }
        e.coreSo = readLine();
        e.launchPkg = readLine();
        e.launchIntent = readLine();
        e.displayName = readLine();
        e.systemName = readLine();
        e.romDir = readLine();
        e.standalone = !e.launchPkg.empty();
        // Skip blank separator line
        if (pos < content.size() && content[pos] == '\n') pos++;
        mXmbRecent.push_back(std::move(e));
    }
    ALOGD("NanoMenu: loaded %zu recent XMB entries", mXmbRecent.size());
}

void NanoMenu::saveXmbRecent() {
    int fd = open(kXmbRecentFile, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd < 0) return;
    chmod(kXmbRecentFile, 0644);
    for (const auto& e : mXmbRecent) {
        std::string block = e.romPath + "\n" + e.coreSo + "\n" + e.launchPkg + "\n"
            + e.launchIntent + "\n" + e.displayName + "\n" + e.systemName + "\n"
            + e.romDir + "\n\n";
        write(fd, block.c_str(), block.size());
    }
    close(fd);
}

void NanoMenu::addXmbRecent(int sysIdx, int gameIdx) {
    if (sysIdx < 0 || sysIdx >= (int)mXmbSystems.size()) return;
    const auto& sys = mXmbSystems[sysIdx];
    if (gameIdx < 0 || gameIdx >= (int)sys.roms.size()) return;

    XmbRecentEntry e;
    // roms[] contains full paths — use directly, convert for app access
    e.romPath = sys.roms[gameIdx];
    if (e.romPath.find("/data/media/0/") == 0) {
        e.romPath = "/sdcard/" + e.romPath.substr(14);
    }
    if (e.romPath.find("/mnt/media_rw/") == 0) {
        // Convert raw SD path to FUSE path for app access
        e.romPath = "/storage/" + e.romPath.substr(14);
    }
    e.coreSo = sys.coreSo;
    e.launchPkg = sys.launchPkg;
    e.launchIntent = sys.launchIntent;
    e.displayName = sys.displayNames[gameIdx];
    e.systemName = sys.shortname;
    e.romDir = sys.romDir;
    e.standalone = sys.isStandalone();

    // Remove duplicate if already in list
    for (auto it = mXmbRecent.begin(); it != mXmbRecent.end(); ++it) {
        if (it->romPath == e.romPath) {
            mXmbRecent.erase(it);
            break;
        }
    }
    // Insert at front (most recent first)
    mXmbRecent.insert(mXmbRecent.begin(), std::move(e));
    // Cap size
    if ((int)mXmbRecent.size() > mXmbRecentMax) {
        mXmbRecent.resize(mXmbRecentMax);
    }
    saveXmbRecent();
}

// ---------------------------------------------------------------------------
// XMB Navigation
// ---------------------------------------------------------------------------

void NanoMenu::handleLeft() {
    if (mOskActive) {
        if (mOskCursorX > 0) mOskCursorX--;
        return;
    }
    if (!mXmbMode) return;
    if (mSearchActive) return;
    // Index -1 = Recently Played, 0..N-1 = systems
    if (mXmbSystemIndex > -1) {
        // Don't go to Recently Played if it's empty
        if (mXmbSystemIndex == 0 && mXmbRecent.empty()) return;
        mXmbSystemIndex--;
        mXmbGameIndex = 0;
        mXmbGameScrollTop = 0;
        mDisplayDirty = true;
    }
}

void NanoMenu::handleRight() {
    if (mOskActive) {
        int maxCol = kOskCols - 1;
        if (mOskCursorX < maxCol) mOskCursorX++;
        return;
    }
    if (!mXmbMode) return;
    if (mSearchActive) return;
    int numSys = (int)mXmbSystems.size();
    if (numSys == 0) return;
    if (mXmbSystemIndex < numSys - 1) {
        mXmbSystemIndex++;
        mXmbGameIndex = 0;
        mXmbGameScrollTop = 0;
        mDisplayDirty = true;
    }
}

void NanoMenu::launchXmbGame() {
    int sysIdx, gameIdx;

    // Recently Played mode (index -1): launch directly from recent entry
    if (mXmbSystemIndex == -1 && !mSearchActive) {
        if (mXmbRecent.empty()) return;
        if (mXmbGameIndex < 0 || mXmbGameIndex >= (int)mXmbRecent.size()) return;
        // Take a copy — the vector reorder below invalidates references.
        XmbRecentEntry re = mXmbRecent[mXmbGameIndex];

        // Move to front of recent list — only on disk, not in-memory.
        // Modifying the vector causes a visible shuffle during the
        // transition frames before NanoMenu exits.
        if (mXmbGameIndex > 0) {
            std::vector<XmbRecentEntry> saved = mXmbRecent;
            saved.erase(saved.begin() + mXmbGameIndex);
            saved.insert(saved.begin(), re);
            std::swap(mXmbRecent, saved);
            saveXmbRecent();
            std::swap(mXmbRecent, saved); // restore in-memory order
        }

        if (re.standalone) {
            // Build content URI and intent file
            std::string filename = re.romPath;
            size_t lastSlash = filename.rfind('/');
            if (lastSlash != std::string::npos) filename = filename.substr(lastSlash + 1);
            std::string encodedFilename;
            for (char c : filename) {
                if (c == ' ') encodedFilename += "%20";
                else if (c == '(') encodedFilename += "%28";
                else if (c == ')') encodedFilename += "%29";
                else if (c == '&') encodedFilename += "%26";
                else if (c == '+') encodedFilename += "%2B";
                else if (c == '!') encodedFilename += "%21";
                else if (c == '\'') encodedFilename += "%27";
                else encodedFilename += c;
            }
            // Determine volume ID from romPath (same logic as launchXmbGame)
            std::string volumeId = "primary";
            std::string relDir = "ROMs%2F" + re.romDir;
            if (re.romPath.find("/storage/") == 0) {
                std::string work = re.romPath.substr(9);
                size_t sl1 = work.find('/');
                if (sl1 != std::string::npos) {
                    std::string uuid = work.substr(0, sl1);
                    if (uuid != "emulated") {
                        size_t lastSl = work.rfind('/');
                        std::string subdir = work.substr(sl1 + 1, lastSl - sl1 - 1);
                        std::string encodedDir;
                        for (char c : subdir) {
                            if (c == '/') encodedDir += "%2F";
                            else if (c == ' ') encodedDir += "%20";
                            else encodedDir += c;
                        }
                        volumeId = uuid;
                        relDir = encodedDir;
                    }
                }
            }
            std::string treeRoot = volumeId + "%3A" + relDir;
            std::string contentUri = "content://com.android.externalstorage.documents/tree/"
                + treeRoot + "/document/" + treeRoot + "%2F" + encodedFilename;
            std::string intent = re.launchIntent;
            size_t pos = intent.find("{file.uri}");
            if (pos != std::string::npos) intent.replace(pos, 10, contentUri);
            std::string tabIntent;
            { const char* p = intent.c_str(); while (*p) { while (*p == ' ') p++;
              if (!*p) break; if (!tabIntent.empty()) tabIntent += '\t';
              const char* s = p; while (*p && *p != ' ') p++; tabIntent.append(s, p - s); } }
            android::base::SetProperty("sys.gammaos.nano.launch_app", re.launchPkg);
            { const char* f = "/data/system/nano_launch_intent.txt";
              int ifd = open(f, O_WRONLY|O_CREAT|O_TRUNC, 0666);
              if (ifd >= 0) { write(ifd, tabIntent.c_str(), tabIntent.size()); close(ifd); chmod(f, 0644); }
              android::base::SetProperty("sys.gammaos.nano.launch_intent", "file"); }
            android::base::SetProperty("sys.gammaos.nano.launch_rom", "");
            android::base::SetProperty("sys.gammaos.nano.launch_core", "");
        } else {
            std::string corePath = "/data/data/com.retroarch.aarch64/cores/" + re.coreSo;
            android::base::SetProperty("sys.gammaos.nano.launch_rom", re.romPath);
            android::base::SetProperty("sys.gammaos.nano.launch_core", corePath);
            android::base::SetProperty("sys.gammaos.nano.launch_app", "com.retroarch.aarch64");
            android::base::SetProperty("sys.gammaos.nano.launch_intent", "");
            // Trigger DE cache populate
            property_set("sys.gammaos.nano.cache_ready", "0");
            property_set("sys.gammaos.nano.cache_op", "populate");
            if (mQuickResumeEnabled) {
                android::base::SetProperty("persist.gammaos.nano.qr_rom", re.romPath);
                android::base::SetProperty("persist.gammaos.nano.qr_core", corePath);
                property_set("persist.gammaos.nano.qr_prepared", "1");
            }
        }
        ALOGI("NanoMenu XMB: recent launch %s [%s]", re.displayName.c_str(), re.systemName.c_str());
        mSearchActive = false; mOskActive = false;
        // Save return state: go back to recently played
        property_set("sys.gammaos.nano.xmb_return_sys", "-1");
        property_set("sys.gammaos.nano.xmb_return_game", "0");
        { char cb[32]; snprintf(cb, sizeof(cb), "%.2f", mEffectTime);
          property_set("persist.gammaos.nano.xmb_color_phase", cb); }
        property_set("sys.gammaos.nano.return_recent", "0");
        property_set("sys.gammaos.nano.return_apps", "0");
        property_set("service.bootanim.nano_retroarch", "1");
        property_set("sys.gammaos.nano.drop_input", "1");
        struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
        int64_t fenceNs = (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
        char buf[32]; snprintf(buf, sizeof(buf), "%" PRId64, fenceNs);
        property_set("sys.gammaos.nano.drop_fence_ns", buf);
        mWaitForRelease = true;
        return;
    }

    if (mSearchActive) {
        // Launch from search results
        if (mSearchResults.empty()) return;
        if (mSearchSelectedIndex < 0 || mSearchSelectedIndex >= (int)mSearchResults.size())
            return;
        sysIdx = mSearchResults[mSearchSelectedIndex].sysIdx;
        gameIdx = mSearchResults[mSearchSelectedIndex].gameIdx;
    } else {
        // Launch from current system
        sysIdx = mXmbSystemIndex;
        gameIdx = mXmbGameIndex;
    }

    if (sysIdx < 0 || sysIdx >= (int)mXmbSystems.size()) return;
    const auto& sys = mXmbSystems[sysIdx];
    if (gameIdx < 0 || gameIdx >= (int)sys.roms.size()) return;

    // roms[] contains full paths — use directly, convert for app access
    std::string romPath = sys.roms[gameIdx];
    if (romPath.find("/data/media/0/") == 0) {
        romPath = "/sdcard/" + romPath.substr(14);
    }
    if (romPath.find("/mnt/media_rw/") == 0) {
        // Convert raw SD path to FUSE path for app access
        // /mnt/media_rw/UUID/dir/file -> /storage/UUID/dir/file
        romPath = "/storage/" + romPath.substr(14);
    }

    if (sys.isStandalone()) {
        // Standalone emulator: build content:// URI matching SAF format.
        // Internal: content://.../tree/primary%3AROMs%2F{dir}/document/primary%3AROMs%2F{dir}%2F{file}
        // External: content://.../tree/{UUID}%3A{subdir}/document/{UUID}%3A{subdir}%2F{file}
        std::string fullRomPath = sys.roms[gameIdx];
        std::string filename;
        { size_t ls = fullRomPath.rfind('/');
          filename = (ls != std::string::npos) ? fullRomPath.substr(ls + 1) : fullRomPath; }
        // URL-encode the filename (spaces, parens, ampersands, etc.)
        std::string encodedFilename;
        for (char c : filename) {
            if (c == ' ') encodedFilename += "%20";
            else if (c == '(') encodedFilename += "%28";
            else if (c == ')') encodedFilename += "%29";
            else if (c == '&') encodedFilename += "%26";
            else if (c == '+') encodedFilename += "%2B";
            else if (c == '!') encodedFilename += "%21";
            else if (c == '\'') encodedFilename += "%27";
            else encodedFilename += c;
        }
        // Determine volume ID and relative subdir from the full path
        // /data/media/0/ROMs/<dir>/file → primary, ROMs/<dir>
        // /sdcard/ROMs/<dir>/file → primary, ROMs/<dir>
        // /storage/emulated/0/ROMs/<dir>/file → primary, ROMs/<dir>
        // /mnt/media_rw/<UUID>/<subdir>/file → <UUID>, <subdir>
        // /storage/<UUID>/<subdir>/file → <UUID>, <subdir>
        std::string volumeId = "primary";
        std::string relDir = "ROMs%2F" + sys.romDir;
        if (fullRomPath.find("/mnt/media_rw/") == 0 || fullRomPath.find("/storage/") == 0) {
            // Extract UUID and relative dir from path
            std::string work = fullRomPath;
            if (work.find("/mnt/media_rw/") == 0) work = work.substr(14);
            else if (work.find("/storage/") == 0) work = work.substr(9);
            // work = "UUID/subdir/file" or "UUID/ROMs/subdir/file" etc.
            size_t sl1 = work.find('/');
            if (sl1 != std::string::npos) {
                std::string uuid = work.substr(0, sl1);
                if (uuid != "emulated") {
                    // Extract the dir portion between UUID/ and /filename
                    size_t lastSl = work.rfind('/');
                    std::string subdir = work.substr(sl1 + 1, lastSl - sl1 - 1);
                    // URL-encode the subdir (/ → %2F)
                    std::string encodedDir;
                    for (char c : subdir) {
                        if (c == '/') encodedDir += "%2F";
                        else if (c == ' ') encodedDir += "%20";
                        else encodedDir += c;
                    }
                    volumeId = uuid;
                    relDir = encodedDir;
                }
            }
        }
        std::string treeRoot = volumeId + "%3A" + relDir;
        std::string contentUri = "content://com.android.externalstorage.documents/tree/"
            + treeRoot + "/document/" + treeRoot + "%2F" + encodedFilename;

        // Build tab-separated intent for RootWindowContainer's parseAmIntent()
        std::string intent = sys.launchIntent;
        size_t pos = intent.find("{file.uri}");
        if (pos != std::string::npos) {
            intent.replace(pos, 10, contentUri);
        }
        // Convert to tab-separated tokens
        std::string tabIntent;
        {
            const char* p = intent.c_str();
            while (*p) {
                while (*p == ' ') p++;
                if (!*p) break;
                if (!tabIntent.empty()) tabIntent += '\t';
                const char* start = p;
                while (*p && *p != ' ') p++;
                tabIntent.append(start, p - start);
            }
        }
        ALOGI("NanoMenu XMB: standalone launch %s uri=%s",
              sys.launchPkg.c_str(), contentUri.c_str());
        android::base::SetProperty("sys.gammaos.nano.launch_app", sys.launchPkg);
        // Intent string exceeds PROP_VALUE_MAX (92 bytes), write to file instead
        {
            const char* intentFile = "/data/system/nano_launch_intent.txt";
            int ifd = open(intentFile, O_WRONLY | O_CREAT | O_TRUNC, 0666);
            if (ifd >= 0) {
                write(ifd, tabIntent.c_str(), tabIntent.size());
                close(ifd);
                // Ensure system_server (uid 1000) can read it
                chmod(intentFile, 0644);
                // Signal that an intent file is available
                android::base::SetProperty("sys.gammaos.nano.launch_intent", "file");
            } else {
                ALOGE("NanoMenu: failed to write intent file: %s", strerror(errno));
                android::base::SetProperty("sys.gammaos.nano.launch_intent", "");
            }
        }
        // Clear RetroArch-specific props
        android::base::SetProperty("sys.gammaos.nano.launch_rom", "");
        android::base::SetProperty("sys.gammaos.nano.launch_core", "");
    } else {
        // RetroArch core
        std::string corePath = "/data/data/com.retroarch.aarch64/cores/" + sys.coreSo;
        ALOGI("NanoMenu XMB: launching %s core=%s", romPath.c_str(), corePath.c_str());
        android::base::SetProperty("sys.gammaos.nano.launch_rom", romPath);
        android::base::SetProperty("sys.gammaos.nano.launch_core", corePath);
        android::base::SetProperty("sys.gammaos.nano.launch_app", "com.retroarch.aarch64");
        android::base::SetProperty("sys.gammaos.nano.launch_intent", "");
        // Trigger DE cache populate
        property_set("sys.gammaos.nano.cache_ready", "0");
        property_set("sys.gammaos.nano.cache_op", "populate");

        // Prime Quick Resume (only for RetroArch games)
        if (mQuickResumeEnabled) {
            android::base::SetProperty("persist.gammaos.nano.qr_rom", romPath);
            android::base::SetProperty("persist.gammaos.nano.qr_core", corePath);
            property_set("persist.gammaos.nano.qr_prepared", "1");
        }
    }

    // Record in recently played (only for system launches, not recent replays)
    if (!mSearchActive) {
        addXmbRecent(sysIdx, gameIdx);
    } else {
        // For search results, also record
        addXmbRecent(sysIdx, gameIdx);
    }

    // Clear search state
    mSearchActive = false;
    mOskActive = false;

    // Save return state: system + game index so we go back to the right place
    {
        char buf[32];
        // If launched from a system (not recently played or search), return to that system
        if (!mSearchActive && mXmbSystemIndex >= 0) {
            snprintf(buf, sizeof(buf), "%d", mXmbSystemIndex);
            property_set("sys.gammaos.nano.xmb_return_sys", buf);
            snprintf(buf, sizeof(buf), "%d", gameIdx);
            property_set("sys.gammaos.nano.xmb_return_game", buf);
        } else {
            // Recently played or search: return to recently played
            property_set("sys.gammaos.nano.xmb_return_sys", "-1");
            property_set("sys.gammaos.nano.xmb_return_game", "0");
        }
        // Save XMB background color phase (effectTime drives hue cycle)
        snprintf(buf, sizeof(buf), "%.2f", mEffectTime);
        property_set("persist.gammaos.nano.xmb_color_phase", buf);
    }

    property_set("sys.gammaos.nano.return_recent", "0");
    property_set("sys.gammaos.nano.return_apps", "0");
    property_set("service.bootanim.nano_retroarch", "1");
    property_set("sys.gammaos.nano.drop_input", "1");

    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    int64_t fenceNs = (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
    char buf[32];
    snprintf(buf, sizeof(buf), "%" PRId64, fenceNs);
    property_set("sys.gammaos.nano.drop_fence_ns", buf);

    mWaitForRelease = true;
}

// ---------------------------------------------------------------------------
// On-Screen Keyboard (Search)
// ---------------------------------------------------------------------------

void NanoMenu::openOsk() {
    mOskActive = true;
    mOskQuery.clear();
    mOskCursorX = 0;
    mOskCursorY = 0;
    mSearchResults.clear();
    mSearchSelectedIndex = 0;
    mSearchActive = false;
}

void NanoMenu::closeOsk() {
    mOskActive = false;
    if (mOskQuery.empty()) {
        mSearchActive = false;
    }
}

void NanoMenu::oskType(char c) {
    if (mOskQuery.size() < 32) {
        mOskQuery += c;
        updateSearchResults();
    }
}

void NanoMenu::oskBackspace() {
    if (!mOskQuery.empty()) {
        mOskQuery.pop_back();
        updateSearchResults();
    }
}

void NanoMenu::oskConfirm() {
    mOskActive = false;
    if (!mOskQuery.empty()) {
        mSearchActive = true;
        mSearchSelectedIndex = 0;
        updateSearchResults();
    } else {
        mSearchActive = false;
    }
}

void NanoMenu::updateSearchResults() {
    mSearchResults.clear();
    if (mOskQuery.empty()) return;
    for (int s = 0; s < (int)mXmbSystems.size(); s++) {
        const auto& sys = mXmbSystems[s];
        for (int g = 0; g < (int)sys.displayNames.size(); g++) {
            if (containsInsensitive(sys.displayNames[g], mOskQuery)) {
                mSearchResults.push_back({s, g});
                if (mSearchResults.size() >= 100) return; // cap results
            }
        }
    }
    mSearchActive = !mSearchResults.empty() || !mOskQuery.empty();
}

void NanoMenu::renderOsk() {
    if (!mOskActive) return;

    float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
    if (sf < 0.5f) sf = 0.5f;

    float oskScale = 2.5f * sf;
    float charW = FONT_CHAR_W * oskScale * 3.0f; // wider spacing for grid
    float charH = FONT_CHAR_H * oskScale;
    float pad = 15.0f * sf;

    // OSK background
    float gridW = kOskCols * charW + pad * 2;
    float gridH = (kOskRows + 1) * (charH + 8.0f * sf) + pad * 2; // +1 for query line
    float bgX = (mWidth - gridW) / 2.0f;
    float bgY = mHeight - gridH - pad;
    drawQuad(bgX, bgY, gridW, gridH, 0.0f, 0.0f, 0.0f, 0.85f);

    // Query line
    float queryY = bgY + pad;
    std::string queryDisplay = "Search: " + mOskQuery + "_";
    float queryScale = 2.0f * sf;
    drawText(queryDisplay.c_str(), bgX + pad, queryY, queryScale,
             0.0f, 0.85f, 1.0f, 1.0f);

    // Keyboard grid
    float gridStartY = queryY + charH + 12.0f * sf;
    float gridStartX = bgX + pad;

    for (int row = 0; row < kOskRows; row++) {
        for (int col = 0; col < kOskCols; col++) {
            float cx = gridStartX + col * charW;
            float cy = gridStartY + row * (charH + 8.0f * sf);
            bool selected = (row == mOskCursorY && col == mOskCursorX);

            if (selected) {
                drawQuad(cx - 2.0f * sf, cy - 2.0f * sf,
                         charW - 4.0f * sf, charH + 4.0f * sf,
                         0.0f, 0.35f, 0.6f, 0.9f);
            }

            char ch = kOskLayout[row][col];
            char str[2] = {ch, '\0'};
            if (ch == ' ') str[0] = '_'; // display space as underscore
            float cr = selected ? 1.0f : 0.7f;
            float cg = selected ? 1.0f : 0.7f;
            float cb = selected ? 1.0f : 0.7f;
            drawText(str, cx + charW * 0.25f, cy, oskScale, cr, cg, cb, 1.0f);
        }
    }

    // Help text
    float helpY = gridStartY + kOskRows * (charH + 8.0f * sf) + 4.0f * sf;
    float helpScale = 1.5f * sf;
    drawText("A:Type  B:Delete  Start:Search  Y:Cancel",
             bgX + pad, helpY, helpScale, 0.4f, 0.4f, 0.5f, 1.0f);
}

// ---------------------------------------------------------------------------
// XMB Rendering
// ---------------------------------------------------------------------------

void NanoMenu::renderXmb() {
    float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
    if (sf < 0.5f) sf = 0.5f;

    // Smooth animation — frame-rate-independent exponential decay.
    // decay = 1 - e^(-speed * dt); at 60fps with speed=12: ~0.18 per frame (matches old feel).
    // At 30fps: ~0.33 per frame — animation converges at the same wall-clock rate.
    float dt = mFrameDt;
    float decay = 1.0f - expf(-12.0f * dt);
    mXmbAnimX += ((float)mXmbSystemIndex - mXmbAnimX) * decay;
    if (fabsf(mXmbAnimX - mXmbSystemIndex) < 0.005f) mXmbAnimX = mXmbSystemIndex;
    float targetY = mSearchActive ? (float)mSearchSelectedIndex : (float)mXmbGameIndex;
    mXmbAnimY += (targetY - mXmbAnimY) * decay;
    if (fabsf(mXmbAnimY - targetY) < 0.005f) mXmbAnimY = targetY;

    int numSys = (int)mXmbSystems.size();
    if (numSys == 0) return;

    // PSP-style: white/gray icons, no gold tint
    float iconR = 0.85f, iconG = 0.85f, iconB = 0.85f;
    float dimIconR = 0.45f, dimIconG = 0.45f, dimIconB = 0.45f;

    // Layout — based on RetroArch XMB driver constants (xmb.c lines 6160-6243)
    // Reference: 1280x720, scale_factor ~1.0
    float scaleFactor = sf;
    float iconSize = 100.0f * scaleFactor;              // Base icon size (scaled by zoom)
    float catActiveZoom = 1.0f;                         // Selected category scale
    float catPassiveZoom = 0.55f;                       // Unselected category scale
    float itemActiveZoom = 0.8f;                        // Selected item scale
    float itemPassiveZoom = 0.4f;                       // Unselected item scale
    float iconSpacingH = 200.0f * scaleFactor;          // Horizontal category spacing (wider)
    float iconSpacingV = 110.0f * scaleFactor;          // Vertical item spacing
    float marginTop = 180.0f * scaleFactor;             // Top of icon bar from screen top
    float marginLeft = 120.0f * scaleFactor;            // Left edge of selected category
    float labelLeft = 20.0f * scaleFactor;              // Text gap from icon right edge
    float aboveItemOff = -1.5f;                         // Items above selected
    float underItemOff = 2.5f;                          // Items below: room for cat name
    float iconBarY = marginTop;
    float selIconX = marginLeft;
    float textScale = 2.2f * sf;
    float selTextScale = 2.8f * sf;
    float footScale = 1.4f * sf;
    float catNameScale = 1.8f * sf;

    bool isRecent = (mXmbSystemIndex == -1);

    // --- Horizontal category bar (icons along the horizontal axis) ---
    // Each category icon is centered at iconBarY, positioned horizontally
    // relative to the selected one. Selected = full size, others = half size.

    auto drawCatIcon = [&](int idx, float hOffset, bool isSel, int iconId) {
        float zoom = isSel ? catActiveZoom : catPassiveZoom;
        float sz = iconSize * zoom;
        float alpha = isSel ? 1.0f : fmaxf(0.15f, 1.0f - fabsf(hOffset) * 0.15f);
        float ix = selIconX + hOffset * iconSpacingH;
        float iy = iconBarY - sz / 2.0f;
        if (ix < -sz || ix > mWidth + sz) return; // cull
        float cr = isSel ? iconR : dimIconR;
        float cg = isSel ? iconG : dimIconG;
        float cb = isSel ? iconB : dimIconB;
        drawIcon(iconId, ix, iy, sz, cr, cg, cb, alpha);
        if (isSel) {
            const char* name = (idx == -1) ? "Recently Played"
                             : (idx >= 0 && idx < numSys) ? mXmbSystems[idx].name.c_str()
                             : "";
            // Category name centered below the icon (PS3 style)
            float nameY = iy + sz + 6.0f * sf;
            float nameW = measureText(name, catNameScale);
            float nameCX = ix + sz / 2.0f - nameW / 2.0f;
            drawText(name, nameCX, nameY, catNameScale, 0.8f, 0.8f, 0.8f, 0.9f);
        }
    };

    // Recently Played (index -1)
    if (!mXmbRecent.empty()) {
        float hOff = -1.0f - mXmbAnimX;
        drawCatIcon(-1, hOff, isRecent, 15);
    }
    // System icons
    for (int i = 0; i < numSys; i++) {
        float hOff = (float)i - mXmbAnimX;
        if (fabsf(hOff) > 8.0f) continue;
        drawCatIcon(i, hOff, !isRecent && i == mXmbSystemIndex, i < 16 ? i : 0);
    }

    // --- Vertical item list (XMB: selected at icon bar, above close, below with gap) ---
    float selSz = iconSize * catActiveZoom;
    float textStartX = selIconX + selSz + labelLeft;
    float contentRight = mWidth * 0.93f;

    int numItems = 0;
    bool hasItems = true;
    if (isRecent) {
        numItems = (int)mXmbRecent.size();
    } else if (mSearchActive) {
        numItems = (int)mSearchResults.size();
    } else {
        int si = mXmbSystemIndex;
        if (si >= 0 && si < numSys) numItems = (int)mXmbSystems[si].roms.size();
    }
    if (numItems == 0) hasItems = false;

    int curIdx = isRecent ? mXmbGameIndex
               : mSearchActive ? mSearchSelectedIndex : mXmbGameIndex;
    if (curIdx >= numItems) curIdx = numItems - 1;
    if (curIdx < 0) curIdx = 0;

    if (hasItems) {
        int maxAbove = (int)(iconBarY / iconSpacingV) + 1;
        int maxBelow = (int)((mHeight - iconBarY) / iconSpacingV) + 1;
        int startItem = curIdx - maxAbove;
        int endItem = curIdx + maxBelow;
        if (startItem < 0) startItem = 0;
        if (endItem >= numItems) endItem = numItems - 1;

        // Items start below the category icon + name
        float selCatSz = iconSize * catActiveZoom;
        float itemListTop = iconBarY + selCatSz / 2.0f + FONT_CHAR_H * catNameScale + 140.0f * sf;

        // Enable scissor once for the entire item list
        float clipTop = iconBarY + selCatSz / 2.0f + 10.0f * sf;
        float animCur = mXmbAnimY;
        // Pre-compute icon X (same for all items)
        float itemIconBaseX = selIconX + (iconSize * catActiveZoom) / 2.0f;

        // Single scissor rect covering the whole item list region. The old
        // code set a narrower rect per-item (and per-draw within each item),
        // but every item draws into the same horizontal band: leftmost
        // possible icon edge → contentRight. Setting it once per list saves
        // 2 * N glScissor state changes per frame (~24 calls for a full list).
        const float selIconSz = iconSize * catActiveZoom;
        const float listScissorX = itemIconBaseX - selIconSz / 2.0f;
        const float listScissorW = contentRight - listScissorX;
        glEnable(GL_SCISSOR_TEST);
        glScissor((int)listScissorX, 0, (int)listScissorW, mHeight);

        for (int i = startItem; i <= endItem; i++) {
            float relPos = (float)i - animCur;
            float fy = itemListTop + relPos * iconSpacingV;
            if (fy < clipTop - iconSpacingV * 0.3f || fy > mHeight + iconSpacingV) continue;

            bool isSel = (fabsf((float)i - animCur) < 0.5f);
            float iAlpha = isSel ? 1.0f : 0.55f;
            float tSc = isSel ? selTextScale : textScale;

            // Get display text — use const refs to avoid std::string copies
            const char* displayText = "";
            const char* sysLabel = nullptr;
            if (isRecent && i < (int)mXmbRecent.size()) {
                displayText = mXmbRecent[i].displayName.c_str();
                sysLabel = mXmbRecent[i].systemName.c_str();
            } else if (mSearchActive && i < (int)mSearchResults.size()) {
                const auto& res = mSearchResults[i];
                if (res.sysIdx < numSys) {
                    displayText = mXmbSystems[res.sysIdx].displayNames[res.gameIdx].c_str();
                    sysLabel = mXmbSystems[res.sysIdx].shortname.c_str();
                }
            } else {
                int si = mXmbSystemIndex;
                if (si >= 0 && si < numSys && i < (int)mXmbSystems[si].displayNames.size()) {
                    displayText = mXmbSystems[si].displayNames[i].c_str();
                }
            }

            // Game disc icon next to each item
            float itemIconSz = isSel ? 50.0f * sf : 30.0f * sf;
            float iconX = itemIconBaseX - itemIconSz / 2.0f;
            float iconY = fy - itemIconSz / 2.0f;
            drawIcon(16, iconX, iconY, itemIconSz,
                     isSel ? iconR : dimIconR, isSel ? iconG : dimIconG,
                     isSel ? iconB : dimIconB, iAlpha);

            float tx = iconX + itemIconSz + 10.0f * sf;
            float ty = fy - FONT_CHAR_H * tSc * 0.4f;
            float tr = isSel ? 1.0f : 0.6f;
            float tg = isSel ? 1.0f : 0.6f;
            float tb = isSel ? 1.0f : 0.6f;

            drawText(displayText, tx, ty, tSc, tr, tg, tb, iAlpha);
            if (isSel && sysLabel && *sysLabel) {
                float tagY = ty + FONT_CHAR_H * tSc + 2.0f * sf;
                drawText(sysLabel, tx, tagY, catNameScale * 0.9f,
                         0.5f, 0.5f, 0.55f, 0.7f);
            }
        }
        glDisable(GL_SCISSOR_TEST);
    } else if (!mSearchActive) {
        const char* msg = isRecent ? "No recently played games"
                        : (mXmbSystemIndex >= 0 && mXmbSystemIndex < numSys
                           && mXmbSystems[mXmbSystemIndex].pathExists)
                          ? "No games found" : "ROM folder not found";
        float msgW = measureText(msg, textScale);
        drawText(msg, (mWidth - msgW) / 2.0f, iconBarY + 40.0f * sf,
                 textScale, 0.5f, 0.5f, 0.5f, 0.7f);
    }

    // Footer
    float footH = FONT_CHAR_H * footScale;
    float footY = mHeight - footH - 8.0f * sf;
    const char* footer = mSearchActive
        ? "Up/Dn: Browse | A: Launch | B: Clear | Y: Refine"
        : "L/R: System | Up/Dn: Game | A: Play | Y: Search | X: FX | L1: List | R1: QR";
    float fW = measureText(footer, footScale);
    drawText(footer, (mWidth - fW) / 2.0f, footY, footScale, 0.35f, 0.35f, 0.4f, 0.8f);

    // Search indicator
    if (mSearchActive && !mOskActive) {
        char searchHdr[64];
        snprintf(searchHdr, sizeof(searchHdr), "Search: \"%s\"", mOskQuery.c_str());
        drawText(searchHdr, 10.0f * sf, footY - FONT_CHAR_H * footScale - 4.0f * sf,
                 footScale, 0.7f, 0.7f, 0.2f, 0.9f);
    }

    // OSK overlay
    renderOsk();
}

// ---------------------------------------------------------------------------
// Main loop
// ---------------------------------------------------------------------------

bool NanoMenu::threadLoop() {
    ALOGD("NanoMenu: entering main loop");
    {
        int64_t nowMs = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
        ALOGW("NanoMenu BOOT TIMING: main loop entry at T+%lldms", nowMs);
    }

    // Clear any stale drop_input/fence from a previous instance.
    property_set("sys.gammaos.nano.drop_input", "0");
    property_set("sys.gammaos.nano.drop_fence_ns", "0");

    // readyToRun() sets service.bootanim.exit=1 to kill the vendor bootanim.
    // Reset it here so our own exit check (further below) doesn't immediately
    // terminate the menu on restarts.
    property_set("service.bootanim.exit", "0");

    // Re-read quick resume flag — the constructor runs before persist props
    // are loaded, so the value read there may be stale (always false).
    mQuickResumeEnabled = android::base::GetBoolProperty(
            "persist.gammaos.nano.quick_resume", false);
    mXmbMode = android::base::GetBoolProperty(
            "persist.gammaos.nano.xmb_mode", false);
    // Re-read wallpaper effect (constructor ran before persist props loaded)
    {
        char wallpaper[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.wallpaper", wallpaper, "21");
        int savedEffect = atoi(wallpaper);
        for (int i = 0; i < kNumActiveEffects; i++) {
            if (kActiveEffects[i] == savedEffect) {
                sActiveEffectIdx = i;
                mCurrentEffect = savedEffect;
                if (mCurrentEffect >= 1 && mCurrentEffect <= 10) initEffects();
                break;
            }
        }
    }
    // If returning from a game, restore the XMB position + color
    {
        std::string retSys = android::base::GetProperty(
                "sys.gammaos.nano.xmb_return_sys", "");
        if (mXmbMode && !retSys.empty()) {
            int returnSysIdx = atoi(retSys.c_str());
            int returnGameIdx = atoi(android::base::GetProperty(
                    "sys.gammaos.nano.xmb_return_game", "0").c_str());
            // Clear so we don't re-apply on next restart
            property_set("sys.gammaos.nano.xmb_return_sys", "");
            property_set("sys.gammaos.nano.xmb_return_game", "");

            if (returnSysIdx == -1) {
                // Return to Recently Played
                loadXmbRecent();
                if (!mXmbRecent.empty()) {
                    mXmbSystemIndex = -1;
                    mXmbGameIndex = 0;
                    mXmbAnimX = -1.0f;
                    mXmbAnimY = 0.0f;
                }
            } else if (returnSysIdx >= 0 && returnSysIdx < (int)mXmbSystems.size()) {
                // Return to specific system + game
                mXmbSystemIndex = returnSysIdx;
                mXmbGameIndex = returnGameIdx;
                mXmbAnimX = (float)returnSysIdx;
                mXmbAnimY = (float)returnGameIdx;
                ALOGD("NanoMenu: returning to system %d game %d",
                      returnSysIdx, returnGameIdx);
            }
            mXmbGameScrollTop = 0;
        }
        // Restore XMB background color phase (check both persist and sys)
        std::string colorPhase = android::base::GetProperty(
                "persist.gammaos.nano.xmb_color_phase", "");
        if (colorPhase.empty()) {
            colorPhase = android::base::GetProperty(
                    "sys.gammaos.nano.xmb_color_phase", "");
        }
        if (!colorPhase.empty()) {
            mEffectTime = atof(colorPhase.c_str());
            property_set("sys.gammaos.nano.xmb_color_phase", "");
            ALOGD("NanoMenu: restored color phase %.2f", mEffectTime);
        }
    }

    // Quick Resume: auto-launch into saved game on boot if prepared
    if (mQuickResumeEnabled) {
        std::string qrPrepared = android::base::GetProperty(
                "persist.gammaos.nano.qr_prepared", "0");
        if (qrPrepared == "1") {
            std::string qrRom = android::base::GetProperty(
                    "persist.gammaos.nano.qr_rom", "");
            std::string qrCore = android::base::GetProperty(
                    "persist.gammaos.nano.qr_core", "");
            if (!qrRom.empty() && !qrCore.empty()) {
                // GammaOS: Ensure rotation uniforms are set for the QR screens.
                // The QR path runs before render(), which uploads the matrix
                // every frame. Without this, the text shader's uRotation is
                // the GL default zero matrix in HWC mode, collapsing all text
                // vertices to origin. Always upload — identity in HWC mode,
                // rotation matrix in DRM mode.
                {
                    const GLuint progs[] = {mShaderProgram, mTextProgram};
                    const GLint  locs[]  = {mLocRotation, mTextLocRotation};
                    for (int i = 0; i < 2; i++) {
                        glUseProgram(progs[i]);
                        glUniformMatrix2fv(locs[i], 1, GL_FALSE, sDrmRotMat);
                    }
                }

                    ALOGI("Quick Resume: launching ROM=%s CORE=%s",
                          qrRom.c_str(), qrCore.c_str());

                    // Find matching system + game for return navigation.
                    // Extract ROM filename and parent dir from qrRom path.
                    int returnSysIdx = -1, returnGameIdx = 0;
                    {
                        std::string romFile = qrRom;
                        size_t lastSlash = romFile.rfind('/');
                        std::string romFilename = (lastSlash != std::string::npos)
                                ? romFile.substr(lastSlash + 1) : romFile;
                        // Parent dir is the system dir (e.g. "snes")
                        std::string parentDir;
                        if (lastSlash != std::string::npos && lastSlash > 0) {
                            size_t prevSlash = romFile.rfind('/', lastSlash - 1);
                            if (prevSlash != std::string::npos)
                                parentDir = romFile.substr(prevSlash + 1,
                                        lastSlash - prevSlash - 1);
                        }
                        for (int si = 0; si < (int)mXmbSystems.size(); si++) {
                            if (mXmbSystems[si].romDir == parentDir) {
                                returnSysIdx = si;
                                // Find game in this system's rom list
                                for (int gi = 0; gi < (int)mXmbSystems[si].roms.size(); gi++) {
                                    std::string romBase = mXmbSystems[si].roms[gi];
                                    { size_t ls = romBase.rfind('/');
                                      if (ls != std::string::npos) romBase = romBase.substr(ls + 1); }
                                    if (romBase == romFilename) {
                                        returnGameIdx = gi;
                                        break;
                                    }
                                }
                                break;
                            }
                        }
                        ALOGI("Quick Resume: return sys=%d game=%d (dir=%s file=%s)",
                              returnSysIdx, returnGameIdx,
                              parentDir.c_str(), romFilename.c_str());
                    }

                    // Try native libretro launch from DE cache first.
                    // This runs the game directly in NanoMenu's EGL context,
                    // bypassing the Android framework entirely (~T+3.5s).
                    std::string cacheDir = "/data/system/nano_cache";
                    std::string cachedCore = cacheDir + "/cores/";
                    std::string cachedRom = cacheDir + "/rom/";
                    std::string romFile, coreFile;

                    // Find cached ROM and core (match core from QR property)
                    {
                        DIR* d = opendir((cacheDir + "/rom").c_str());
                        if (d) {
                            struct dirent* e;
                            while ((e = readdir(d)) != nullptr) {
                                std::string name(e->d_name);
                                if (name == "." || name == "..") continue;
                                // ROM file (not .srm, .state, .sav, .png)
                                if (name.find(".srm") == std::string::npos &&
                                    name.find(".state") == std::string::npos &&
                                    name.find(".sav") == std::string::npos &&
                                    name.find(".png") == std::string::npos) {
                                    romFile = cacheDir + "/rom/" + name;
                                }
                            }
                            closedir(d);
                        }
                        // Match core from QR property (basename), not blindly first .so
                        std::string qrCoreBase = qrCore;
                        size_t lastSlash = qrCoreBase.rfind('/');
                        if (lastSlash != std::string::npos)
                            qrCoreBase = qrCoreBase.substr(lastSlash + 1);
                        std::string candidateCore = cacheDir + "/cores/" + qrCoreBase;
                        struct stat cst;
                        if (!qrCoreBase.empty() && stat(candidateCore.c_str(), &cst) == 0) {
                            coreFile = candidateCore;
                        } else {
                            // Fallback: first .so in cache
                            d = opendir((cacheDir + "/cores").c_str());
                            if (d) {
                                struct dirent* e;
                                while ((e = readdir(d)) != nullptr) {
                                    std::string name(e->d_name);
                                    if (name.find(".so") != std::string::npos) {
                                        coreFile = cacheDir + "/cores/" + name;
                                        break;
                                    }
                                }
                                closedir(d);
                            }
                        }
                    }

                    bool nativeLaunch = false;
                    bool qrCancelled = false;
                    if (!romFile.empty() && !coreFile.empty()) {
                        // Find save state and SRAM — check states/saves subdirs
                        // first, then fall back to rom/ dir (depends on RA config)
                        std::string romBase = romFile;
                        size_t slashPos = romBase.rfind('/');
                        if (slashPos != std::string::npos)
                            romBase = romBase.substr(slashPos + 1);
                        size_t dotPos = romBase.rfind('.');
                        if (dotPos != std::string::npos)
                            romBase = romBase.substr(0, dotPos);
                        std::string statePath, sramPath;
                        struct stat st;
                        // State: try states/ then rom/
                        std::string s1 = cacheDir + "/states/" + romBase + ".state.auto";
                        std::string s2 = cacheDir + "/rom/" + romBase + ".state.auto";
                        if (stat(s1.c_str(), &st) == 0) statePath = s1;
                        else if (stat(s2.c_str(), &st) == 0) statePath = s2;
                        // SRAM: try saves/ then rom/
                        std::string r1 = cacheDir + "/saves/" + romBase + ".srm";
                        std::string r2 = cacheDir + "/rom/" + romBase + ".srm";
                        if (stat(r1.c_str(), &st) == 0) sramPath = r1;
                        else if (stat(r2.c_str(), &st) == 0) sramPath = r2;

                        ALOGI("Quick Resume: trying native libretro launch");
                        ALOGI("  core=%s", coreFile.c_str());
                        ALOGI("  rom=%s", romFile.c_str());
                        ALOGI("  state=%s", statePath.c_str());
                        ALOGI("  sram=%s", sramPath.c_str());

                        LibretroRunner runner;
                        if (sDrmGlRotation) {
                            runner.setRotationMatrix(sDrmRotMat);
                        }
                        if (runner.init(coreFile, romFile, statePath, sramPath)) {
                            ALOGI("Quick Resume: native libretro loading screen active!");
                            nativeLaunch = true;

                            // Set properties for RetroArch handoff
                            android::base::SetProperty(
                                    "sys.gammaos.nano.launch_rom", qrRom);
                            android::base::SetProperty(
                                    "sys.gammaos.nano.launch_core", qrCore);
                            property_set("sys.gammaos.nano.cache_ready", "0");
                            property_set("sys.gammaos.nano.cache_op", "populate");

                            // Live loading screen: core runs in real-time with
                            // grayscale→color transition. The game is actually
                            // playing behind the desaturation + gradient overlay.
                            // User can play while "loading." When RetroArch takes
                            // over, the game is already running — seamless handoff.
                            float saturation = 0.15f;  // start slightly colorized
                            float gradient = 1.0f;     // strong gradient (full black at bottom)
                            // Extract ROM display name (strip path + extension)
                            std::string romName = romFile;
                            size_t sl = romName.rfind('/');
                            if (sl != std::string::npos) romName = romName.substr(sl + 1);
                            size_t dot = romName.rfind('.');
                            if (dot != std::string::npos) romName = romName.substr(0, dot);
                            bool bootComplete = false;
                            int64_t bootCompleteTime = 0;
                            float textScale = fminf((float)mWidth / 1080.0f,
                                                    (float)mHeight / 720.0f);
                            if (textScale < 0.5f) textScale = 0.5f;
                            float loadScale = 2.5f * textScale;

                            while (!exitPending() && !qrCancelled) {
                                // Poll input — game is live, user can play.
                                // SELECT cancels QR and returns to NanoMenu.
                                for (int fd : mInputFds) {
                                    struct input_event ev;
                                    while (read(fd, &ev, sizeof(ev)) == sizeof(ev)) {
                                        if (ev.type == EV_KEY) {
                                            bool pressed = (ev.value != 0);
                                            if (ev.code == BTN_SELECT && pressed) {
                                                qrCancelled = true;
                                                break;
                                            }
                                            switch (ev.code) {
                                            case BTN_A:      runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_B, pressed); break;
                                            case BTN_B:      runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_A, pressed); break;
                                            case BTN_X:      runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_Y, pressed); break;
                                            case BTN_Y:      runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_X, pressed); break;
                                            case BTN_TL:     runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_L, pressed); break;
                                            case BTN_TR:     runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_R, pressed); break;
                                            case BTN_TL2:    runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_L2, pressed); break;
                                            case BTN_TR2:    runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_R2, pressed); break;
                                            case BTN_START:  runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_START, pressed); break;
                                            case BTN_THUMBL: runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_L3, pressed); break;
                                            case BTN_THUMBR: runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_R3, pressed); break;
                                            case KEY_UP:     runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_UP, pressed); break;
                                            case KEY_DOWN:   runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_DOWN, pressed); break;
                                            case KEY_LEFT:   runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_LEFT, pressed); break;
                                            case KEY_RIGHT:  runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_RIGHT, pressed); break;
                                            }
                                        } else if (ev.type == EV_ABS) {
                                            if (ev.code == ABS_HAT0X) {
                                                runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_LEFT, ev.value < 0);
                                                runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_RIGHT, ev.value > 0);
                                            } else if (ev.code == ABS_HAT0Y) {
                                                runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_UP, ev.value < 0);
                                                runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_DOWN, ev.value > 0);
                                            } else if (ev.code == ABS_X) {
                                                runner.setAnalog(0, RETRO_DEVICE_INDEX_ANALOG_LEFT,
                                                    RETRO_DEVICE_ID_ANALOG_X, (int16_t)((ev.value - 128) * 256));
                                            } else if (ev.code == ABS_Y) {
                                                runner.setAnalog(0, RETRO_DEVICE_INDEX_ANALOG_LEFT,
                                                    RETRO_DEVICE_ID_ANALOG_Y, (int16_t)((ev.value - 128) * 256));
                                            } else if (ev.code == ABS_RX) {
                                                runner.setAnalog(0, RETRO_DEVICE_INDEX_ANALOG_RIGHT,
                                                    RETRO_DEVICE_ID_ANALOG_X, (int16_t)((ev.value - 128) * 256));
                                            } else if (ev.code == ABS_RY) {
                                                runner.setAnalog(0, RETRO_DEVICE_INDEX_ANALOG_RIGHT,
                                                    RETRO_DEVICE_ID_ANALOG_Y, (int16_t)((ev.value - 128) * 256));
                                            }
                                        }
                                    }
                                    if (qrCancelled) break;
                                }

                                // Check boot progress. Trigger the color transition as soon as
                                // possible using the earliest valid signal:
                                //   1. sys.gammaos.nano.home_launching=1 — UserController has
                                //      called startHomeActivity(nanoUnlocked). Earliest signal.
                                //   2. sys.user.0.ce_available=true — user CE storage mounted.
                                //   3. sys.boot_completed=1 — full boot (fallback).
                                if (!bootComplete) {
                                    char val[PROPERTY_VALUE_MAX] = {};
                                    property_get("sys.gammaos.nano.home_launching", val, "");
                                    bool ready = (strcmp(val, "1") == 0);
                                    if (!ready) {
                                        property_get("sys.user.0.ce_available", val, "");
                                        ready = (strcmp(val, "true") == 0);
                                    }
                                    if (!ready) {
                                        property_get("sys.boot_completed", val, "0");
                                        ready = (strcmp(val, "1") == 0);
                                    }
                                    if (ready) {
                                        bootComplete = true;
                                        bootCompleteTime = elapsedRealtime();
                                        ALOGI("Quick Resume: user ready, "
                                              "transitioning to full color");
                                    } else {
                                        // Slow creep toward color during boot
                                        saturation = fminf(saturation + 0.0003f, 0.35f);
                                        gradient = fmaxf(gradient - 0.0002f, 0.7f);
                                    }
                                }

                                if (bootComplete) {
                                    // Ramp to full color over ~0.8s (ease-out)
                                    int64_t elapsed = elapsedRealtime() - bootCompleteTime;
                                    float t = fminf((float)elapsed / 800.0f, 1.0f);
                                    t = 1.0f - (1.0f - t) * (1.0f - t);
                                    saturation = 0.35f + t * 0.65f;
                                    gradient = 0.7f * (1.0f - t);

                                    if (t >= 1.0f) {
                                        // Fully saturated — hand off to RetroArch.
                                        // Fire do_launch FIRST so NanoRelaunchMonitor can start
                                        // the home activity in parallel while we save state.
                                        // This also bypasses the slow init property trigger
                                        // chain (service.bootanim.nano_retroarch → do_launch
                                        // via init.rc action, which can queue behind boot_completed
                                        // actions for several seconds).
                                        ALOGI("Quick Resume: handoff to RetroArch");
                                        { char buf[32];
                                          snprintf(buf, sizeof(buf), "%d", returnSysIdx);
                                          property_set("sys.gammaos.nano.xmb_return_sys", buf);
                                          snprintf(buf, sizeof(buf), "%d", returnGameIdx);
                                          property_set("sys.gammaos.nano.xmb_return_game", buf);
                                        }
                                        property_set("sys.gammaos.nano.return_recent", "0");
                                        property_set("sys.gammaos.nano.drop_input", "1");
                                        // Direct: trigger the relaunch monitor immediately.
                                        property_set("sys.gammaos.nano.do_launch", "1");
                                        // Also set legacy nano_retroarch for init side-effects
                                        // (service.bootanim.exit, etc) but don't depend on it.
                                        property_set("service.bootanim.nano_retroarch", "1");

                                        // Now save state + SRAM in parallel with RetroArch launch
                                        runner.saveState(cacheDir + "/states/" + romBase + ".state.auto");
                                        runner.saveSRAM(cacheDir + "/saves/" + romBase + ".srm");
                                        runner.shutdown();
                                        mExitRequested = true;
                                        break;
                                    }
                                }

                                // Bind AHB FBO so libretro + overlay render through DRM path
                                drmFrameBegin();

                                // Run core + render with desaturation + gradient.
                                // Pass LOGICAL dims for aspect ratio correction;
                                // the rotation matrix maps logical NDC → panel NDC.
                                runner.runFrame(mWidth, mHeight, saturation, gradient);

                                // Text overlay — restore viewport for rotated rendering
                                if (sDrmGlRotation) {
                                    glViewport(0, 0, sAhbTarget.w, sAhbTarget.h);
                                } else {
                                    glViewport(0, 0, mWidth, mHeight);
                                }
                                glEnable(GL_BLEND);
                                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                                const char* msg = "Quick Resuming...";
                                float msgW = measureText(msg, loadScale);
                                float msgX = (mWidth - msgW) / 2.0f;
                                float msgY = mHeight * 0.75f;
                                float pulse = 0.7f + 0.3f * sinf(
                                        (float)elapsedRealtime() * 0.004f);
                                float textAlpha = pulse * fmaxf(1.2f - saturation, 0.0f);
                                if (textAlpha > 0.05f) {
                                    if (textAlpha > 1.0f) textAlpha = 1.0f;
                                    drawText(msg, msgX, msgY, loadScale,
                                             1.0f, 1.0f, 1.0f, textAlpha);
                                    // ROM name below
                                    float nameScale = 1.5f * textScale;
                                    float nameW = measureText(romName.c_str(), nameScale);
                                    float nameX = (mWidth - nameW) / 2.0f;
                                    float nameY = msgY + FONT_CHAR_H * loadScale + 12.0f * textScale;
                                    drawText(romName.c_str(), nameX, nameY, nameScale,
                                             0.7f, 0.7f, 0.8f, textAlpha * 0.8f);
                                }
                                glDisable(GL_BLEND);

                                drmFrameEnd(mDisplay, mSurface);
                                // DRM path: GPU finish + AHB copy already takes
                                // ~13-20ms, providing natural frame pacing.
                                // Only sleep on the eglSwapBuffers path.
                                if (!sDrmActive) {
                                    usleep(16666);
                                }
                            }

                            if (qrCancelled) {
                                ALOGI("Quick Resume: cancelled by SELECT, "
                                      "returning to menu");
                                runner.shutdown();
                                property_set("persist.gammaos.nano.qr_prepared", "0");
                                // Don't set mExitRequested — fall through to NanoMenu
                            }
                        } else {
                            ALOGW("Quick Resume: native libretro init failed, "
                                  "falling back to RetroArch APK");
                        }
                    }

                    // Fallback: normal RetroArch APK launch (only if native
                    // didn't launch and user didn't cancel)
                    if (!nativeLaunch && !qrCancelled) {
                        android::base::SetProperty(
                                "sys.gammaos.nano.launch_rom", qrRom);
                        android::base::SetProperty(
                                "sys.gammaos.nano.launch_core", qrCore);
                        property_set("sys.gammaos.nano.cache_ready", "0");
                        property_set("sys.gammaos.nano.cache_op", "populate");
                        // Set return navigation to matching system/game
                        { char buf[32];
                          snprintf(buf, sizeof(buf), "%d", returnSysIdx);
                          property_set("sys.gammaos.nano.xmb_return_sys", buf);
                          snprintf(buf, sizeof(buf), "%d", returnGameIdx);
                          property_set("sys.gammaos.nano.xmb_return_game", buf);
                        }
                        property_set("sys.gammaos.nano.return_recent", "0");
                        property_set("service.bootanim.nano_retroarch", "1");
                        property_set("sys.gammaos.nano.drop_input", "1");
                        mExitRequested = true;
                    }
            } else {
                // ROM or core path empty/invalid — stale data
                property_set("persist.gammaos.nano.qr_prepared", "0");
            }
        }
    }

    int exitCheckCounter = 0;
    bool stockClocksApplied = false;
    while (!exitPending() && !mExitRequested) {
        // Apply stock clocks once boot is fully complete (PerformanceTile
        // re-syncs performance_mode on boot_completed, so we must wait)
        if (!stockClocksApplied) {
            char bootDone[PROPERTY_VALUE_MAX] = {};
            property_get("sys.boot_completed", bootDone, "0");
            if (!strcmp(bootDone, "1")) {
                usleep(1000000); // 1s margin for PerformanceTile sync
                system("/vendor/bin/setclock_stock.sh");
                stockClocksApplied = true;
                ALOGD("NanoMenu: applied stock clocks after boot_completed");
            }
        }
        pollInput();
        checkInputHotplug();

        // Adaptive framerate:
        //   60fps for DRM, XMB, or procedural effects (vsync-locked, no usleep)
        //   20fps for particle effects
        //   ~10fps idle
        bool xmbActive = (mCurrentEffect == 21);
        bool proceduralFx = (mCurrentEffect >= 11 && mCurrentEffect <= 20);
        bool xmbAnimating = mXmbMode && (fabsf(mXmbAnimX - mXmbSystemIndex) > 0.01f
                                         || fabsf(mXmbAnimY - (mSearchActive
                                             ? (float)mSearchSelectedIndex
                                             : (float)mXmbGameIndex)) > 0.01f);
        bool animating = (mCurrentEffect != 0) || mShowBrightnessBar
                         || mWaitForRelease
                         || ((mMenuState == MENU_RECENT || mMenuState == MENU_APPS)
                             && mScrollOffset > 0.0f);
        int frameTimeUs;
        if (sDrmActive || xmbActive || mXmbMode || proceduralFx) {
            frameTimeUs = 16666; // 60fps — vsync-locked, no usleep
        } else if (animating) {
            frameTimeUs = 50000; // 20fps for particles
        } else {
            frameTimeUs = 100000; // 10fps idle
        }
        // Measure real frame delta for animations
        {
            struct timespec ts;
            clock_gettime(CLOCK_MONOTONIC, &ts);
            int64_t nowNs = (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
            if (mLastFrameNs > 0) {
                float dt = (float)(nowNs - mLastFrameNs) / 1e9f;
                // Clamp to avoid huge jumps on stalls (e.g. first frame, suspend)
                if (dt < 0.001f) dt = 0.001f;
                if (dt > 0.1f) dt = 0.1f;
                mFrameDt = dt;
            }
            mLastFrameNs = nowNs;
        }
        mEffectTime += mFrameDt;
        // Wrap time to prevent mediump float precision degradation and to
        // align with XMB hue cycle (rate 0.002 → period 500s). Wrapping at
        // exactly 500s ensures fract(t*0.002+offset) is seamless.
        if (mEffectTime > 500.0f) mEffectTime -= 500.0f;
        render();

        // GammaOS: XMB FPS counter. Logs once per second when in XMB mode so
        // we can verify the menu is actually hitting the 60fps target post-
        // optimization. Zero overhead when disabled. Uses monotonic clock
        // already measured for mFrameDt.
        {
            static int64_t sFpsWindowStartNs = 0;
            static int sFpsFrames = 0;
            static int64_t sFpsMinFrameUs = 0;
            static int64_t sFpsMaxFrameUs = 0;
            if (mXmbMode) {
                sFpsFrames++;
                int64_t frameUs = (int64_t)(mFrameDt * 1e6f);
                if (sFpsFrames == 1 || frameUs < sFpsMinFrameUs) sFpsMinFrameUs = frameUs;
                if (frameUs > sFpsMaxFrameUs) sFpsMaxFrameUs = frameUs;
                if (sFpsWindowStartNs == 0) sFpsWindowStartNs = mLastFrameNs;
                int64_t elapsedNs = mLastFrameNs - sFpsWindowStartNs;
                if (elapsedNs >= 1000000000LL) {
                    float fps = (float)sFpsFrames * 1e9f / (float)elapsedNs;
                    ALOGW("NanoMenu XMB FPS: %.1f (%d frames / %lld.%03lld s, min=%lldus max=%lldus)",
                          fps, sFpsFrames, elapsedNs / 1000000000LL,
                          (elapsedNs / 1000000LL) % 1000,
                          sFpsMinFrameUs, sFpsMaxFrameUs);
                    sFpsWindowStartNs = mLastFrameNs;
                    sFpsFrames = 0;
                    sFpsMinFrameUs = 0;
                    sFpsMaxFrameUs = 0;
                }
            } else {
                sFpsWindowStartNs = 0;
                sFpsFrames = 0;
            }
        }

        // Frame pacing: at 60fps, eglSwapBuffers vsync-blocks — no sleep needed.
        // For lower rates, sleep the remaining time to hit the target frame period.
        // Clock-based: measure actual elapsed time so variable swap durations
        // don't cause frame-to-frame jitter.
        if (frameTimeUs > 16666) {
            struct timespec tsNow;
            clock_gettime(CLOCK_MONOTONIC, &tsNow);
            int64_t nowUs = (int64_t)tsNow.tv_sec * 1000000LL + tsNow.tv_nsec / 1000LL;
            int64_t frameStartUs = mLastFrameNs / 1000LL;
            int64_t elapsedUs = nowUs - frameStartUs;
            int64_t remainUs = (int64_t)frameTimeUs - elapsedUs;
            if (remainUs > 1000) usleep((useconds_t)remainUs);
        }

        // Check every ~0.5s if an external trigger requested exit
        int exitCheckInterval = animating ? 30 : 5; // 30*16ms or 5*100ms
        if (++exitCheckCounter >= exitCheckInterval) {
            exitCheckCounter = 0;
            char val[PROPERTY_VALUE_MAX] = {};
            property_get("service.bootanim.exit", val, "0");
            if (!strcmp(val, "1") && !mWaitForRelease) {
                ALOGI("GammaOS Nano: service.bootanim.exit=1, exiting");
                break;
            }
            // Probe storage readiness (CE unlock) until it becomes available.
            // This is the only place that polls — render() used to probe every
            // frame but that was wasteful once the 0.5s cadence here exists.
            // When storage comes up, auto-load the active submenu's content
            // so the user sees the entries as soon as they are available.
            if (!mStorageReady) {
                if (access("/data/media/0", R_OK) == 0) {
                    mStorageReady = true;
                    mDisplayDirty = true;
                    if (mMenuState == MENU_RECENT) loadRecentPlaylist();
                    else if (mMenuState == MENU_APPS) loadInstalledApps();
                    ALOGI("GammaOS Nano: storage is now accessible");
                }
            }

            // GammaOS: Late-display re-probe. Any DRM CRTC that wasn't ready at
            // splash time gets a second chance here. Bounded to a 5-second boot
            // window by drmRescanDisplays itself. No-op post-boot (sDrmFd = -1).
            drmRescanDisplays();
            // GammaOS Nano: Keep the surface's layer stack in sync with the
            // chosen display. SurfaceFlinger's initial layerStack for the display
            // can change once DisplayManagerService finishes assigning logical
            // display IDs (e.g. port 1 starts at layerStack=1 but becomes 2). We
            // re-query and re-apply here so the NanoMenu surface follows the
            // chosen physical display even after DMS reassigns.
            if (mDisplayToken != nullptr && mFlingerSurfaceControl != nullptr) {
                ui::DisplayState cur;
                if (SurfaceComposerClient::getDisplayState(mDisplayToken, &cur)
                        == NO_ERROR
                        && cur.layerStack.id != mAppliedLayerStack) {
                    SurfaceComposerClient::Transaction lt;
                    lt.setLayerStack(mFlingerSurfaceControl, cur.layerStack);
                    lt.apply();
                    ALOGI("NanoMenu: layerStack changed %u → %u, reapplied",
                          mAppliedLayerStack, cur.layerStack.id);
                    mAppliedLayerStack = cur.layerStack.id;
                }
            }
            // GammaOS: Defensive recovery for the secondary (wallpaper-only)
            // displays. After exiting a DualStack app, DualStackController
            // tears down its forced tall size via clearForcedDisplaySize,
            // which triggers a display reconfiguration on DEFAULT_DISPLAY.
            // The reconfiguration applies the orientation policy of whatever
            // is still considered the "top resumed activity" — usually the
            // dying portrait emulator — and rotates the secondary display
            // to ROTATION_270 a few seconds after we returned to nano. The
            // wallpaper then renders sideways (480x640 instead of 640x480).
            // We can't suppress that race from this side, so just poll the
            // secondary display states each ~0.5s and re-apply ROTATION_0
            // whenever the rotation drifts. Cheap: bounded by the number of
            // secondary displays (typically one).
            for (size_t i = 0; i < mSecondaryDisplayTokens.size(); i++) {
                const sp<IBinder>& token = mSecondaryDisplayTokens[i];
                if (token == nullptr) continue;
                ui::DisplayState state;
                if (SurfaceComposerClient::getDisplayState(token, &state) != NO_ERROR) {
                    continue;
                }
                if (state.orientation == ui::ROTATION_0) continue;
                DisplayMode mode;
                if (SurfaceComposerClient::getActiveDisplayMode(token, &mode) != NO_ERROR) {
                    continue;
                }
                ui::Size res = mode.resolution;
                Rect bounds(0, 0, res.width, res.height);
                SurfaceComposerClient::Transaction t;
                t.setDisplayProjection(token, ui::ROTATION_0, bounds, bounds);
                t.apply();
                ALOGI("NanoMenu: secondary display %zu rotated to %d, "
                      "reset to ROTATION_0 (%dx%d)",
                      i, static_cast<int>(state.orientation),
                      res.width, res.height);
            }
            // Background ROM scanning — all I/O runs on a separate thread.
            // The render thread only does a quick lock-free check + swap.
            if (mStorageReady) {
                // Check boot_completed once → trigger initial background scan
                if (!mXmbBootCompleted) {
                    char val[PROPERTY_VALUE_MAX] = {};
                    property_get("sys.boot_completed", val, "0");
                    if (!strcmp(val, "1")) {
                        mXmbBootCompleted = true;
                        forceRescanAllSystems();
                    }
                }

                // Pick up results from background scan thread (lock-free check)
                if (mBgScanResultReady) {
                    std::lock_guard<std::mutex> lock(mBgScanMutex);
                    if (mBgScanResultReady) {
                        for (int i = 0; i < (int)mXmbSystems.size()
                                 && i < (int)mBgScanResults.size(); i++) {
                            auto& sys = mXmbSystems[i];
                            auto& res = mBgScanResults[i];
                            // Guard: don't replace with fewer ROMs when storage
                            // is partially mounted. Two checks:
                            // 1. Path count: if fewer source dirs, storage not ready
                            // 2. Time: never remove entries within 60s of boot_completed
                            if (res.roms.size() < sys.roms.size() && sys.scanned) {
                                // Time guard: always block within 60s of boot
                                static int64_t sBootCompletedTime = 0;
                                if (sBootCompletedTime == 0)
                                    sBootCompletedTime = elapsedRealtime();
                                if ((elapsedRealtime() - sBootCompletedTime) < 60000)
                                    continue;
                                // Path guard: block if fewer paths accessible
                                size_t curPaths = sys.activePaths.size();
                                if (curPaths == 0 && !sys.roms.empty()) {
                                    std::set<std::string> dirs;
                                    for (const auto& r : sys.roms) {
                                        size_t sl = r.rfind('/');
                                        if (sl != std::string::npos)
                                            dirs.insert(r.substr(0, sl));
                                    }
                                    curPaths = dirs.size();
                                }
                                if (res.activePaths.size() < curPaths) continue;
                            }
                            if (res.roms != sys.roms || !sys.scanned) {
                                sys.roms = std::move(res.roms);
                                sys.displayNames = std::move(res.displayNames);
                                sys.activePaths = std::move(res.activePaths);
                                sys.activePath = std::move(res.activePath);
                                sys.pathExists = !sys.roms.empty();
                                mDisplayDirty = true;
                                // Update cache file
                                std::string cacheDir = "/data/system/nano_xmb_cache";
                                mkdir(cacheDir.c_str(), 0755);
                                std::string cp = cacheDir + "/" + sys.romDir + ".list";
                                int cfd = open(cp.c_str(), O_WRONLY|O_CREAT|O_TRUNC, 0644);
                                if (cfd >= 0) {
                                    for (const auto& r : sys.roms) {
                                        std::string l = r + "\n";
                                        write(cfd, l.c_str(), l.size());
                                    }
                                    close(cfd);
                                }
                            }
                            sys.scanned = true;
                            sys.lastScanTime = elapsedRealtime();
                        }
                        mBgScanResultReady = false;
                        mXmbRomScanDone = true;
                    }
                }

                // Periodic rescan every 30s (runs on background thread,
                // zero impact on render)
                if (mXmbBootCompleted && !mBgScanThreadRunning
                    && !mXmbSystems.empty()) {
                    int64_t now = elapsedRealtime();
                    if (mXmbSystems[0].lastScanTime > 0 &&
                        (now - mXmbSystems[0].lastScanTime) > 30000) {
                        forceRescanAllSystems();
                    }
                }
            }
        }
    }

    // GammaOS: Clear menu_active flag so DualStack can re-enable when app launches.
    property_set("sys.gammaos.nano.menu_active", "0");

    // Only re-apply performance clocks when launching an app (not on bootanim.exit)
    // Run in background — setclock_max.sh has a 20s retry loop that must not block exit.
    if (mExitRequested) {
        char mode[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.performance_mode", mode, "stock");
        char cmd[128];
        snprintf(cmd, sizeof(cmd), "/vendor/bin/setclock_%s.sh &", mode);
        ALOGI("NanoMenu: re-applying performance mode '%s' (background)", mode);
        system(cmd);
    }

    // Transition: grab input devices. drop_input was already set in handleSelect()
    // to block InputDispatcher from the moment the user pressed A.
    for (int fd : mInputFds) {
        ioctl(fd, EVIOCGRAB, 1);
    }
    // Do NOT clear drop_input here. The A-DOWN may still be sitting in
    // InputDispatcher's queue waiting for a focused window. InputDispatcher
    // will clear drop_input itself when it processes a FOCUS entry (which
    // arrives after all stale events have been dropped).
    ALOGD("NanoMenu: showing loading screen, waiting for RetroArch");
    // GammaOS: Ensure rotation uniforms are set for the loading screen path.
    if (sDrmGlRotation) {
        const GLuint progs[] = {mShaderProgram, mTextProgram};
        const GLint  locs[]  = {mLocRotation, mTextLocRotation};
        for (int i = 0; i < 2; i++) {
            glUseProgram(progs[i]);
            glUniformMatrix2fv(locs[i], 1, GL_FALSE, sDrmRotMat);
        }
    }
    {
        float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
        if (sf < 0.5f) sf = 0.5f;
        float loadScale = 3.0f * sf;
        struct input_event drain_ev;
        char launched[PROPERTY_VALUE_MAX] = {};
        for (int wait = 0; wait < 600; wait++) { // max ~10s
            // Drain all queued input events
            for (int fd : mInputFds) {
                while (read(fd, &drain_ev, sizeof(drain_ev)) == sizeof(drain_ev)) {}
            }
            // Render loading screen — route through DRM if active
            drmFrameBegin();
            if (sDrmGlRotation) {
                glViewport(0, 0, sAhbTarget.w, sAhbTarget.h);
            } else {
                glViewport(0, 0, mWidth, mHeight);
            }
            glClearColor(0.05f, 0.05f, 0.10f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            const char* loadMsg = "Loading...";
            float loadW = measureText(loadMsg, loadScale);
            float loadX = (mWidth - loadW) / 2.0f;
            float loadY = (mHeight - FONT_CHAR_H * loadScale) / 2.0f;
            drawText(loadMsg, loadX, loadY, loadScale,
                     0.6f, 0.6f, 0.7f, 1.0f);
            glDisable(GL_BLEND);
            drmFrameEnd(mDisplay, mSurface);

            property_get("sys.gammaos.nano.app_launched", launched, "0");
            if (!strcmp(launched, "1")) {
                ALOGD("NanoMenu: RetroArch launched, exiting");
                break;
            }
            usleep(16666);
        }
    }
    // GammaOS: tear down secondary wallpaper EGL surfaces / SurfaceControls
    // BEFORE eglTerminate. The destructor (~NanoMenu) was previously doing
    // this cleanup, but by then eglTerminate had already invalidated the
    // EGL display, leaving the underlying BLASTBufferQueue's GraphicBuffers
    // in an inconsistent state. The buffer release path then tried to call
    // freeBuffer through the gralloc mapper after its RegisteredHandlePool
    // mutex was effectively destroyed, aborting with FORTIFY:
    //   pthread_mutex_lock called on a destroyed mutex
    // (backtrace: ~SurfaceControl → ~BBQSurface → ~BLASTBufferQueue →
    //  ~GraphicBuffer → freeBuffer → RegisteredHandlePool::remove).
    // Cleaning up here, while the EGL display is still alive, avoids the
    // crash. The destructor's identical cleanup becomes a no-op because the
    // vectors are already empty.
    for (size_t i = 0; i < mSecondaryEglSurfaces.size(); i++) {
        eglDestroySurface(mDisplay, mSecondaryEglSurfaces[i]);
    }
    mSecondaryEglSurfaces.clear();
    mSecondarySurfaces.clear();
    if (!mSecondaryWallpaperControls.empty()) {
        SurfaceComposerClient::Transaction t;
        for (size_t i = 0; i < mSecondaryWallpaperControls.size(); i++) {
            t.reparent(mSecondaryWallpaperControls[i], nullptr);
        }
        t.apply();
        mSecondaryWallpaperControls.clear();
    }

    eglMakeCurrent(mDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(mDisplay, mContext);
    eglDestroySurface(mDisplay, mSurface);
    mFlingerSurface.clear();
    mFlingerSurfaceControl.clear();
    eglTerminate(mDisplay);
    eglReleaseThread();
    IPCThreadState::self()->stopProcess();
    return false;
}

// ---------------------------------------------------------------------------
// Quick Resume helpers
// ---------------------------------------------------------------------------

bool NanoMenu::isRetroArchRunning() {
    DIR* dir = opendir("/proc");
    if (!dir) return false;
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (entry->d_type != DT_DIR) continue;
        char* end;
        long pid = strtol(entry->d_name, &end, 10);
        if (*end != '\0' || pid <= 0) continue;
        char cmdPath[64];
        snprintf(cmdPath, sizeof(cmdPath), "/proc/%ld/cmdline", pid);
        int fd = open(cmdPath, O_RDONLY);
        if (fd < 0) continue;
        char cmdline[256] = {};
        read(fd, cmdline, sizeof(cmdline) - 1);
        close(fd);
        if (strstr(cmdline, "retroarch")) {
            closedir(dir);
            return true;
        }
    }
    closedir(dir);
    return false;
}

void NanoMenu::prepareShutdown(const char* action) {
    // Always close RetroArch gracefully if running (saves state via ESC)
    if (isRetroArchRunning()) {
        ALOGI("NanoMenu: RetroArch still running, sending ESC to close gracefully");
        property_set("sys.gammaos.nano.qr_send_esc", "1");
        for (int i = 0; i < 50 && isRetroArchRunning(); i++) {
            usleep(100000); // 100ms
        }
        if (isRetroArchRunning()) {
            ALOGW("NanoMenu: RetroArch did not exit after ESC, proceeding anyway");
        }
    }

    // Quick Resume is primed at game launch time (handleSelect) and by
    // ShutdownThread when the user reboots from within RetroArch via legacy
    // global actions.  Do NOT re-prime here — the nano menu only runs after
    // the user has exited RetroArch, so priming here would cause a stale
    // game to auto-launch on the next boot.

    // Proceed with the requested action
    property_set("service.bootanim.nano_action", action);
}

} // namespace android
