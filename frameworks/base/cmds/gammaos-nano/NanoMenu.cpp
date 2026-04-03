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
#include <fcntl.h>
#include <dirent.h>
#include <unistd.h>
#include <sys/stat.h>
#include <math.h>
#include <stdlib.h>
#include <linux/input.h>
#include <sys/inotify.h>
#include <signal.h>

#include <binder/IPCThreadState.h>
#include <cutils/properties.h>
#include <android-base/properties.h>
#include <utils/Log.h>
#include <utils/SystemClock.h>

#include <ui/DisplayMode.h>
#include <ui/LayerStack.h>
#include <ui/PixelFormat.h>
#include <ui/Rect.h>

#include <gui/ISurfaceComposer.h>
#include <gui/Surface.h>
#include <gui/SurfaceComposerClient.h>

#include <GLES2/gl2.h>
#include <EGL/eglext.h>
#include <png.h>

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
    varying vec4 vColor;
    void main() {
        gl_Position = aPosition;
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
    varying vec4 vColor;
    void main() {
        gl_Position = vec4(aPosition, 0.0, 1.0);
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
    void main() { gl_Position = aPosition; }
)";

static const char FX_FRAGMENT_SHADER[] = R"(
    precision mediump float;
    uniform float uTime;
    uniform vec2 uResolution;
    uniform int uEffect;

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
        vec2 uv = gl_FragCoord.xy / uResolution;
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
            float n = noise(vec2(uv.x * 6.0, uv.y * 4.0 - t * 3.0));
            float n2 = noise(vec2(uv.x * 12.0, uv.y * 8.0 - t * 5.0));
            float fire = (1.0 - uv.y) * (n * 0.6 + n2 * 0.4);
            fire = pow(fire, 1.5);
            col = vec3(fire * 1.5, fire * 0.6, fire * 0.1);
            a = fire * 0.25;
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

    float xmbHash(vec2 p) {
        return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
    }

    // HSV to RGB — strong saturated colors for cycling background
    vec3 hsv2rgb(float h, float s, float v) {
        vec3 p = abs(fract(vec3(h) + vec3(0.0, 2.0/3.0, 1.0/3.0)) * 6.0 - 3.0);
        return v * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), s);
    }

    void main() {
        vec2 uv = gl_FragCoord.xy / uResolution;
        float t = mod(uTime, 628.318);

        // Aspect-corrected x for spatial frequency matching
        float x = (uv.x - 0.5) * uResolution.x / uResolution.y;

        // --- Three flowing ribbon bands (analytical distance field) ---
        float ribbon = 0.0;

        // Ribbon 1: wide primary wave
        float cy1 = 0.44 + 0.08 * sin(x * 1.0 + t * 0.25)
                         + 0.03 * sin(x * 2.2 + t * 0.7)
                         + 0.015 * sin(x * 4.0 - t * 0.3);
        ribbon += smoothstep(0.07, 0.0, abs(uv.y - cy1)) * 0.35;

        // Ribbon 2: narrower, slightly offset
        float cy2 = 0.47 + 0.06 * sin(x * 1.05 + t * 0.24 + 0.5)
                         + 0.025 * sin(x * 2.5 - t * 0.35 + 1.5);
        ribbon += smoothstep(0.05, 0.0, abs(uv.y - cy2)) * 0.25;

        // Ribbon 3: thin accent band
        float cy3 = 0.41 + 0.09 * sin(x * 0.95 + t * 0.26 + 1.2)
                         + 0.04 * sin(x * 1.8 + t * 0.42 + 2.0);
        ribbon += smoothstep(0.06, 0.0, abs(uv.y - cy3)) * 0.20;

        ribbon = clamp(ribbon, 0.0, 1.0);

        // Slowly cycle hue — full rainbow over ~500s (~8 min).
        float hue = fract(t * 0.002 + 0.6);
        vec3 tint = hsv2rgb(hue, 0.85, 0.65);

        // Gradient: dark tint at bottom → full tint at top
        vec3 col = mix(tint * 0.15, tint, smoothstep(0.0, 1.0, uv.y));

        // Blend ribbons toward white
        col = mix(col, vec3(1.0), ribbon * 0.7);

        // Sparkle dust near ribbons
        vec2 grid = floor(gl_FragCoord.xy / 6.0);
        float seed = xmbHash(grid);
        float sparkle = step(0.97, seed)
                       * pow(sin(t * 4.0 + seed * 628.0) * 0.5 + 0.5, 2.0)
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
    varying vec2 vTexCoord;
    varying vec4 vColor;
    void main() {
        gl_Position = vec4(aPosition, 0.0, 1.0);
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
      mCurrentEffect(1),
      mInShadowPass(false),
      mEffectTime(0.0f),
      mQuickResumeEnabled(false),
      mXmbMode(false), mXmbRecentMax(50), mXmbSystemIndex(0), mXmbGameIndex(0),
      mXmbAnimX(0.0f), mXmbAnimY(0.0f),
      mXmbGameScrollTop(0), mXmbRomScanDone(false),
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
            // Track SELECT button state
            if (ev.type == EV_KEY && ev.code == BTN_SELECT) {
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
    if (len <= 0) return;

    for (char* ptr = buf; ptr < buf + len; ) {
        auto* ev = reinterpret_cast<struct inotify_event*>(ptr);
        if (ev->len > 0 && strncmp(ev->name, "event", 5) == 0) {
            if (ev->mask & IN_DELETE) {
                // Device node was removed (gammapad hides+recreates devices).
                // Close our stale fd and forget it so we re-grab on IN_CREATE.
                if (mOpenedDevices.count(ev->name)) {
                    char path[PATH_MAX];
                    snprintf(path, sizeof(path), "/dev/input/%s", ev->name);
                    // Find and close the fd for this device
                    for (auto it = mInputFds.begin(); it != mInputFds.end(); ++it) {
                        char fdPath[PATH_MAX];
                        char procLink[64];
                        snprintf(procLink, sizeof(procLink), "/proc/self/fd/%d", *it);
                        ssize_t rl = readlink(procLink, fdPath, sizeof(fdPath) - 1);
                        if (rl > 0) {
                            fdPath[rl] = '\0';
                            if (strstr(fdPath, ev->name) || strstr(fdPath, "(deleted)")) {
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
        glUseProgram(mFxProgram);
        glUniform1f(mFxLocTime, mEffectTime);
        glUniform2f(mFxLocResolution, (float)mWidth, (float)mHeight);
        glUniform1i(mFxLocEffect, mCurrentEffect);
        glVertexAttribPointer(mFxLocPosition, 2, GL_FLOAT, GL_FALSE, 0, verts);
        glEnableVertexAttribArray(mFxLocPosition);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glDisableVertexAttribArray(mFxLocPosition);
    } else if (mCurrentEffect == 21) {
        // XMB: PS3-style volumetric ribbon background (dedicated shader, 60fps)
        GLfloat verts[] = { -1,-1, 1,-1, 1,1, 1,1, -1,1, -1,-1 };
        glUseProgram(mXmbProgram);
        glUniform1f(mXmbLocTime, mEffectTime);
        glUniform2f(mXmbLocResolution, (float)mWidth, (float)mHeight);
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

status_t NanoMenu::readyToRun() {
    // Started unconditionally by StartPropertySetThread for the fastest nano
    // mode path.  persist.* properties may not be available yet (loaded after
    // /data mount), so wait for init to signal that they are ready.
    char skip[PROPERTY_VALUE_MAX] = {};
    property_get("persist.bootanim.skip_nano", skip, "");
    if (strcmp(skip, "0") != 0) {
        // Property is "1", empty, or not yet loaded.  If persist props are not
        // ready, wait — the value could change once /data mounts.
        char ready[PROPERTY_VALUE_MAX] = {};
        property_get("ro.persistent_properties.ready", ready, "");
        if (strcmp(ready, "true") != 0) {
            ALOGI("GammaOS Nano: persist props not ready, waiting...");
            for (int i = 0; i < 500; i++) { // max 5s
                usleep(10000); // 10ms
                property_get("ro.persistent_properties.ready", ready, "");
                if (!strcmp(ready, "true")) break;
            }
            // Re-read after persist props loaded
            property_get("persist.bootanim.skip_nano", skip, "");
        }
        if (strcmp(skip, "0") != 0) {
            ALOGI("GammaOS Nano: skip_nano='%s' (not '0'), starting bootanim", skip);
            property_set("ctl.start", "bootanim");
            _exit(0); // terminate — bootanim takes over
        }
    }

    // Nano mode is active — tell any boot animation instance to exit.
    // Vendor init may start bootanim independently (e.g. in on late-fs),
    // so it can be running alongside us with the same z-layer.
    property_set("service.bootanim.exit", "1");

    const std::vector<PhysicalDisplayId> ids = SurfaceComposerClient::getPhysicalDisplayIds();
    if (ids.empty()) { ALOGE("No displays found"); return NAME_NOT_FOUND; }

    mDisplayToken = SurfaceComposerClient::getPhysicalDisplayToken(ids.front());
    if (mDisplayToken == nullptr) return NAME_NOT_FOUND;

    DisplayMode displayMode;
    const status_t error = SurfaceComposerClient::getActiveDisplayMode(mDisplayToken, &displayMode);
    if (error != NO_ERROR) return error;

    ui::Size resolution = displayMode.resolution;
    sp<SurfaceControl> control = session()->createSurface(
        String8("GammaOSNano"), resolution.getWidth(), resolution.getHeight(),
        PIXEL_FORMAT_RGB_565, ISurfaceComposerClient::eOpaque);

    SurfaceComposerClient::Transaction t;
    Rect forcedRes(0, 0, resolution.width, resolution.height);
    Rect physRes(0, 0, displayMode.resolution.width, displayMode.resolution.height);
    // GammaOS: Always set the primary display projection to physical resolution.
    // NanoMenu renders at 640x480 and needs the display projection to match.
    // If DualStack left it at 640x960, NanoMenu would appear compressed.
    // DualStack invalidates the Java-side cache when it later re-applies 640x960.
    t.setDisplayProjection(mDisplayToken, ui::ROTATION_0, forcedRes, physRes);
    t.setLayer(control, 0x40000001);

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
    ALOGD("NanoMenu: signaled framework to clear forced display size");

    // GammaOS: Create a wallpaper surface on each secondary display.
    // Instead of changing layer stacks (which desyncs SF from the Java framework
    // and breaks DualStack), create a separate surface on each secondary display's
    // own layer stack. The wallpaper shader renders to this surface independently.
    for (size_t i = 1; i < ids.size(); i++) {
        sp<IBinder> secToken = SurfaceComposerClient::getPhysicalDisplayToken(ids[i]);
        if (secToken == nullptr) continue;

        DisplayMode secMode;
        if (SurfaceComposerClient::getActiveDisplayMode(secToken, &secMode) != NO_ERROR)
            continue;

        ui::Size secRes = secMode.resolution;
        sp<SurfaceControl> secControl = session()->createSurface(
            String8("GammaOSNano-Secondary"), secRes.getWidth(), secRes.getHeight(),
            PIXEL_FORMAT_RGB_565, ISurfaceComposerClient::eOpaque);

        // Place on the secondary display's layer stack (i*2 for internal displays)
        SurfaceComposerClient::Transaction secT;
        secT.setLayer(secControl, 0x40000001);
        secT.setLayerStack(secControl, ui::LayerStack::fromValue(i * 2));
        secT.show(secControl);
        secT.apply();

        mSecondaryDisplayTokens.push_back(secToken);
        mSecondaryWallpaperControls.push_back(secControl);
        ALOGD("NanoMenu: created wallpaper surface on secondary display %zu (%dx%d, layerStack=%zu)",
                i, secRes.getWidth(), secRes.getHeight(), i * 2);
    }
    t.apply();

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
    mFlingerSurfaceControl = control; mFlingerSurface = s;

    ALOGD("NanoMenu: display %dx%d", mWidth, mHeight);

    // GammaOS: Create EGL surfaces for secondary wallpaper rendering.
    // Immediately render a black frame so the surface appears at the same time
    // as the primary. The real wallpaper starts after initShaders()/initEffects().
    for (size_t i = 0; i < mSecondaryWallpaperControls.size(); i++) {
        sp<Surface> secSurf = mSecondaryWallpaperControls[i]->getSurface();
        EGLSurface secEgl = eglCreateWindowSurface(display, config, secSurf.get(), nullptr);
        if (secEgl != EGL_NO_SURFACE) {
            mSecondaryEglSurfaces.push_back(secEgl);
            mSecondarySurfaces.push_back(secSurf);
            // Prime the surface with a black frame so it's visible immediately
            eglMakeCurrent(display, secEgl, secEgl, context);
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);
            eglSwapBuffers(display, secEgl);
            ALOGD("NanoMenu: created and primed EGL surface for secondary %zu", i);
        }
    }
    // Switch back to primary
    eglMakeCurrent(display, surface, surface, context);

    initShaders();
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
    // Apply brightness async — the lights HAL may not be up yet during early boot.
    // Spawn a thread that waits for the HAL then sets the saved brightness.
    // Tries AIDL ILights first (Android 13+), falls back to HIDL ILight@2.0.
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
        glDeleteShader(vs); glDeleteShader(fs);
    }
    {   GLuint vs = compileShader(GL_VERTEX_SHADER, TEXT_VERTEX_SHADER);
        GLuint fs = compileShader(GL_FRAGMENT_SHADER, TEXT_FRAGMENT_SHADER);
        mTextProgram = linkProgram(vs, fs);
        mTextLocPosition = glGetAttribLocation(mTextProgram, "aPosition");
        mTextLocTexCoord = glGetAttribLocation(mTextProgram, "aTexCoord");
        mTextLocColor    = glGetAttribLocation(mTextProgram, "aColor");
        mTextLocTexture  = glGetUniformLocation(mTextProgram, "uTexture");
        glDeleteShader(vs); glDeleteShader(fs);
    }
    {   GLuint vs = compileShader(GL_VERTEX_SHADER, PARTICLE_VERTEX_SHADER);
        GLuint fs = compileShader(GL_FRAGMENT_SHADER, PARTICLE_FRAGMENT_SHADER);
        mParticleProgram = linkProgram(vs, fs);
        mParticleLocPosition = glGetAttribLocation(mParticleProgram, "aPosition");
        mParticleLocColor    = glGetAttribLocation(mParticleProgram, "aColor");
        glDeleteShader(vs); glDeleteShader(fs);
    }
    {   GLuint vs = compileShader(GL_VERTEX_SHADER, FX_VERTEX_SHADER);
        GLuint fs = compileShader(GL_FRAGMENT_SHADER, FX_FRAGMENT_SHADER);
        mFxProgram = linkProgram(vs, fs);
        mFxLocPosition   = glGetAttribLocation(mFxProgram, "aPosition");
        mFxLocTime       = glGetUniformLocation(mFxProgram, "uTime");
        mFxLocResolution = glGetUniformLocation(mFxProgram, "uResolution");
        mFxLocEffect     = glGetUniformLocation(mFxProgram, "uEffect");
        glDeleteShader(vs); glDeleteShader(fs);
    }
    {   GLuint vs = compileShader(GL_VERTEX_SHADER, FX_VERTEX_SHADER);
        GLuint fs = compileShader(GL_FRAGMENT_SHADER, XMB_FRAGMENT_SHADER);
        mXmbProgram = linkProgram(vs, fs);
        mXmbLocPosition   = glGetAttribLocation(mXmbProgram, "aPosition");
        mXmbLocTime       = glGetUniformLocation(mXmbProgram, "uTime");
        mXmbLocResolution = glGetUniformLocation(mXmbProgram, "uResolution");
        glDeleteShader(vs); glDeleteShader(fs);
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

static const int TEXT_MAX_CHARS = 256;
static GLfloat sTextVerts[TEXT_MAX_CHARS * 6 * 2];
static GLfloat sTextUVs[TEXT_MAX_CHARS * 6 * 2];
static GLfloat sTextColors[TEXT_MAX_CHARS * 6 * 4];

void NanoMenu::drawText(const char* str, float px, float py, float scale,
                        float r, float g, float b, float a) {
    if (!str || !*str || mFtNumFaces == 0) return;
    // Black outline: draw text at 4 cardinal offsets in black, then normal on top
    if (!mInShadowPass) {
        float off = fmaxf(1.0f, scale * 0.4f);
        mInShadowPass = true;
        drawText(str, px - off, py, scale, 0.0f, 0.0f, 0.0f, a * 0.8f);
        drawText(str, px + off, py, scale, 0.0f, 0.0f, 0.0f, a * 0.8f);
        drawText(str, px, py - off, scale, 0.0f, 0.0f, 0.0f, a * 0.8f);
        drawText(str, px, py + off, scale, 0.0f, 0.0f, 0.0f, a * 0.8f);
        mInShadowPass = false;
    }
    float pixelScale = (FONT_CHAR_H * scale) / (float)mFontSize;
    float invW = 2.0f / mWidth, invH = 2.0f / mHeight;
    // baseline: py is the top of the text area, add ascent to get baseline
    float baseline = py + mFontSize * pixelScale * 0.8f; // approximate ascent at 80%
    int n = 0;
    for (const char* p = str; *p && n < TEXT_MAX_CHARS; ) {
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
            px += gi.advance * gi.scaleW * pixelScale;
            continue;
        }

        float gw = gi.bmpW * gi.scaleW * pixelScale;
        float gh = gi.bmpH * gi.scaleH * pixelScale;
        float gx = px + gi.bearingX * gi.scaleW * pixelScale;
        float gy = baseline - gi.bearingY * gi.scaleH * pixelScale;

        float x0 = gx * invW - 1.0f;
        float y0 = 1.0f - (gy + gh) * invH;
        float x1 = (gx + gw) * invW - 1.0f;
        float y1 = 1.0f - gy * invH;

        int vi = n * 12;
        sTextVerts[vi]= x0; sTextVerts[vi+1]= y0;
        sTextVerts[vi+2]= x1; sTextVerts[vi+3]= y0;
        sTextVerts[vi+4]= x1; sTextVerts[vi+5]= y1;
        sTextVerts[vi+6]= x1; sTextVerts[vi+7]= y1;
        sTextVerts[vi+8]= x0; sTextVerts[vi+9]= y1;
        sTextVerts[vi+10]= x0; sTextVerts[vi+11]= y0;
        int ui = n * 12;
        sTextUVs[ui]= gi.u0; sTextUVs[ui+1]= gi.v1;
        sTextUVs[ui+2]= gi.u1; sTextUVs[ui+3]= gi.v1;
        sTextUVs[ui+4]= gi.u1; sTextUVs[ui+5]= gi.v0;
        sTextUVs[ui+6]= gi.u1; sTextUVs[ui+7]= gi.v0;
        sTextUVs[ui+8]= gi.u0; sTextUVs[ui+9]= gi.v0;
        sTextUVs[ui+10]= gi.u0; sTextUVs[ui+11]= gi.v1;
        // Per-vertex color: for color emoji use white (pass-through), else use text color
        float cr = gi.color ? 1.0f : r;
        float cg = gi.color ? 1.0f : g;
        float cb = gi.color ? 1.0f : b;
        float ca = a;
        int ci = n * 24;
        for (int v = 0; v < 6; v++) {
            sTextColors[ci + v*4] = cr;
            sTextColors[ci + v*4 + 1] = cg;
            sTextColors[ci + v*4 + 2] = cb;
            sTextColors[ci + v*4 + 3] = ca;
        }
        px += gi.advance * gi.scaleW * pixelScale;
        n++;
    }
    if (n == 0) return;
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
// Render
// ---------------------------------------------------------------------------

void NanoMenu::render() {
    glViewport(0, 0, mWidth, mHeight);
    glClearColor(0.05f, 0.05f, 0.10f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    // Background effect
    updateEffect();
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

    // Poll storage while in submenu — auto-load once available
    if ((mMenuState == MENU_RECENT || mMenuState == MENU_APPS) && !mStorageReady) {
        if (access("/data/media/0", R_OK) == 0) {
            mStorageReady = true;
            if (mMenuState == MENU_RECENT) loadRecentPlaylist();
            else loadInstalledApps();
            mDisplayDirty = true;
            rebuildDisplayItems();
        }
    }

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
    eglSwapBuffers(mDisplay, mSurface);

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
    // Set Android media volume via audio sysfs/property
    // Scale 0-15 to 0-100 for the mixer, or use AudioService property
    char buf[32];
    snprintf(buf, sizeof(buf), "%d", mVolume);
    property_set("persist.gammaos.nano.volume", buf);
    // Try writing to ALSA mixer for immediate effect
    snprintf(buf, sizeof(buf), "%d", mVolume * 100 / mMaxVolume);
    // Use tinymix or write to a known volume path
    char cmd[128];
    snprintf(cmd, sizeof(cmd), "service call audio 3 i32 3 i32 %d i32 0", mVolume);
    // AudioService may not be running in nano mode, so also try direct mixer
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
                        size_t pos = 0;
                        bool firstLine = true;
                        while (pos < content.size()) {
                            size_t eol = content.find('\n', pos);
                            if (eol == std::string::npos) eol = content.size();
                            std::string line = content.substr(pos, eol - pos);
                            pos = eol + 1;
                            if (line.empty()) continue;
                            if (firstLine) {
                                sys.activePath = line;
                                sys.pathExists = true;
                                firstLine = false;
                            } else {
                                sys.roms.push_back(line);
                            }
                        }
                        // Pre-compute display names from cache
                        for (const auto& rom : sys.roms) {
                            std::string dn = rom;
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

        mXmbSystems.push_back(std::move(sys));
    }
    ALOGD("NanoMenu: initialized %d XMB systems", (int)mXmbSystems.size());
}

// ---------------------------------------------------------------------------
// ROM Path Scanning
// ---------------------------------------------------------------------------

void NanoMenu::scanRomPaths() {
    ALOGD("NanoMenu: scanning ROM paths");
    for (auto& sys : mXmbSystems) {
        if (sys.scanned) continue;

        // Build candidate paths in priority order:
        // 1. Raw filesystem (bypasses FUSE, works earliest after CE unlock)
        // 2. FUSE-mounted internal storage (available after vold)
        // 3. External SD card volumes (enumerated from /storage/)
        // 4. Prop-overridden custom path
        const std::string romDir = sys.romDir;
        std::vector<std::string> scanPaths;
        scanPaths.push_back("/data/media/0/ROMs/" + romDir);
        scanPaths.push_back("/sdcard/ROMs/" + romDir);
        scanPaths.push_back("/storage/emulated/0/ROMs/" + romDir);

        // Enumerate external storage volumes (/storage/XXXX-XXXX/ROMs/)
        {
            DIR* storageDir = opendir("/storage");
            if (storageDir) {
                struct dirent* sEntry;
                while ((sEntry = readdir(storageDir)) != nullptr) {
                    if (sEntry->d_name[0] == '.') continue;
                    // Skip "emulated" and "self" — those are internal
                    if (!strcmp(sEntry->d_name, "emulated")) continue;
                    if (!strcmp(sEntry->d_name, "self")) continue;
                    std::string extPath = "/storage/";
                    extPath += sEntry->d_name;
                    extPath += "/ROMs/" + romDir;
                    scanPaths.push_back(extPath);
                }
                closedir(storageDir);
            }
            // Also try /mnt/media_rw/ for raw external SD access
            DIR* mntDir = opendir("/mnt/media_rw");
            if (mntDir) {
                struct dirent* mEntry;
                while ((mEntry = readdir(mntDir)) != nullptr) {
                    if (mEntry->d_name[0] == '.') continue;
                    std::string extPath = "/mnt/media_rw/";
                    extPath += mEntry->d_name;
                    extPath += "/ROMs/" + romDir;
                    scanPaths.push_back(extPath);
                }
                closedir(mntDir);
            }
        }

        // Check for prop-overridden custom path
        char customPath[PROPERTY_VALUE_MAX] = {};
        char propKey[128];
        snprintf(propKey, sizeof(propKey), "persist.gammaos.nano.xmb.%s.path", romDir.c_str());
        property_get(propKey, customPath, "");

        DIR* dir = nullptr;
        std::string activePath;

        // If custom path is set, try it first
        if (customPath[0]) {
            dir = opendir(customPath);
            if (dir) activePath = customPath;
        }

        // Try all candidate paths
        if (!dir) {
            for (const auto& path : scanPaths) {
                dir = opendir(path.c_str());
                if (dir) {
                    activePath = path;
                    break;
                }
            }
        }

        if (!dir) {
            // Not ready yet — leave scanned=false so we retry next check
            sys.pathExists = false;
            ALOGD("NanoMenu: %s: no accessible path found (will retry)", sys.name.c_str());
            continue;
        }
        sys.scanned = true;
        sys.pathExists = true;
        sys.activePath = activePath;

        // Clear any cached data — fresh scan replaces it
        sys.roms.clear();
        sys.displayNames.clear();

        // Build extension set from comma-separated list
        std::set<std::string> exts;
        {
            const std::string& extStr = sys.acceptExts;
            size_t pos = 0;
            while (pos < extStr.size()) {
                size_t comma = extStr.find(',', pos);
                if (comma == std::string::npos) comma = extStr.size();
                std::string ext = extStr.substr(pos, comma - pos);
                // Trim whitespace
                while (!ext.empty() && ext[0] == ' ') ext.erase(0, 1);
                if (!ext.empty()) exts.insert(ext);
                pos = comma + 1;
            }
        }

        // Always accept .zip and .7z (RetroArch can extract these)
        exts.insert(".zip");
        exts.insert(".7z");

        struct dirent* entry;
        while ((entry = readdir(dir)) != nullptr) {
            if (entry->d_name[0] == '.') continue; // skip hidden
            if (entry->d_type == DT_DIR) continue;  // skip directories

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

            // Skip 0-byte files (dummy/placeholder files)
            {
                std::string fullPath = activePath + "/" + name;
                struct stat st;
                if (stat(fullPath.c_str(), &st) == 0 && st.st_size == 0) continue;
            }

            sys.roms.push_back(name);
        }
        closedir(dir);

        // Sort alphabetically (case-insensitive)
        std::sort(sys.roms.begin(), sys.roms.end(),
                  [](const std::string& a, const std::string& b) {
                      for (size_t i = 0; i < a.size() && i < b.size(); i++) {
                          char ca = a[i], cb = b[i];
                          if (ca >= 'A' && ca <= 'Z') ca += 32;
                          if (cb >= 'A' && cb <= 'Z') cb += 32;
                          if (ca != cb) return ca < cb;
                      }
                      return a.size() < b.size();
                  });

        // Pre-compute display names (strip extension)
        sys.displayNames.reserve(sys.roms.size());
        for (const auto& rom : sys.roms) {
            std::string dn = rom;
            size_t d = dn.rfind('.');
            if (d != std::string::npos) dn = dn.substr(0, d);
            sys.displayNames.push_back(std::move(dn));
        }

        ALOGD("NanoMenu: %s: %zu ROMs in %s", sys.name.c_str(),
              sys.roms.size(), activePath.c_str());

        // Save cache to DE storage (accessible before CE unlock)
        {
            std::string cacheDir = "/data/system/nano_xmb_cache";
            mkdir(cacheDir.c_str(), 0755);
            std::string cachePath = cacheDir + "/" + sys.romDir + ".list";
            int cfd = open(cachePath.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
            if (cfd >= 0) {
                // First line: active path
                std::string header = activePath + "\n";
                write(cfd, header.c_str(), header.size());
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
    // Build the launch path the same way launchXmbGame does
    if (!sys.activePath.empty()) {
        e.romPath = sys.activePath + "/" + sys.roms[gameIdx];
        if (e.romPath.find("/data/media/0/") == 0) {
            e.romPath = "/sdcard/" + e.romPath.substr(14);
        }
    } else {
        e.romPath = "/sdcard/ROMs/" + sys.romDir + "/" + sys.roms[gameIdx];
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
        const auto& re = mXmbRecent[mXmbGameIndex];

        // Move to front of recent list
        if (mXmbGameIndex > 0) {
            XmbRecentEntry moved = mXmbRecent[mXmbGameIndex];
            mXmbRecent.erase(mXmbRecent.begin() + mXmbGameIndex);
            mXmbRecent.insert(mXmbRecent.begin(), std::move(moved));
            saveXmbRecent();
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
                else encodedFilename += c;
            }
            std::string contentUri = "content://com.android.externalstorage.documents/tree/primary%3AROMs%2F"
                + re.romDir + "/document/primary%3AROMs%2F" + re.romDir + "%2F" + encodedFilename;
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

    // Build launch path — use /sdcard/ROMs/ prefix for FUSE access (RetroArch runs
    // after FUSE mount). If the ROM was found on an external path, use that directly.
    std::string romPath;
    if (!sys.activePath.empty()) {
        romPath = sys.activePath + "/" + sys.roms[gameIdx];
        // Convert raw /data/media/0/ paths to /sdcard/ for app access
        if (romPath.find("/data/media/0/") == 0) {
            romPath = "/sdcard/" + romPath.substr(14);
        }
    } else {
        romPath = "/sdcard/ROMs/" + sys.romDir + "/" + sys.roms[gameIdx];
    }

    if (sys.isStandalone()) {
        // Standalone emulator: build content:// URI matching SAF format
        // that Daijisho/Android uses for document providers.
        // Format: content://com.android.externalstorage.documents/tree/
        //         primary%3AROMs%2F{dir}/document/primary%3AROMs%2F{dir}%2F{filename}
        std::string filename = sys.roms[gameIdx];
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
        std::string contentUri = "content://com.android.externalstorage.documents/tree/primary%3AROMs%2F"
            + sys.romDir + "/document/primary%3AROMs%2F" + sys.romDir + "%2F" + encodedFilename;

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

    // Smooth animation (tuned for 60fps, snappy feel)
    float animSpeed = 0.18f;
    mXmbAnimX += ((float)mXmbSystemIndex - mXmbAnimX) * animSpeed;
    if (fabsf(mXmbAnimX - mXmbSystemIndex) < 0.01f) mXmbAnimX = mXmbSystemIndex;
    float targetY = mSearchActive ? (float)mSearchSelectedIndex : (float)mXmbGameIndex;
    mXmbAnimY += (targetY - mXmbAnimY) * animSpeed;
    if (fabsf(mXmbAnimY - targetY) < 0.01f) mXmbAnimY = targetY;

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

        for (int i = startItem; i <= endItem; i++) {
            // All items flow downward from itemListTop.
            // Selected item is at itemListTop, items above go up, items below go down.
            float animCur = mXmbAnimY;
            float relPos = (float)i - animCur;
            float fy = itemListTop + relPos * iconSpacingV;
            // Clip: don't draw items that overlap the category icon/name area
            float clipTop = iconBarY + selCatSz / 2.0f + 10.0f * sf;
            if (fy < clipTop - iconSpacingV * 0.3f || fy > mHeight + iconSpacingV) continue;

            bool isSel = (fabsf((float)i - animCur) < 0.5f);
            float iAlpha = isSel ? 1.0f : 0.55f;
            float tSc = isSel ? selTextScale : textScale;

            // Get display text
            std::string displayText;
            std::string sysLabel;
            if (isRecent && i < (int)mXmbRecent.size()) {
                displayText = mXmbRecent[i].displayName;
                sysLabel = mXmbRecent[i].systemName;
            } else if (mSearchActive && i < (int)mSearchResults.size()) {
                const auto& res = mSearchResults[i];
                if (res.sysIdx < numSys) {
                    displayText = mXmbSystems[res.sysIdx].displayNames[res.gameIdx];
                    sysLabel = mXmbSystems[res.sysIdx].shortname;
                }
            } else {
                int si = mXmbSystemIndex;
                if (si >= 0 && si < numSys && i < (int)mXmbSystems[si].displayNames.size()) {
                    displayText = mXmbSystems[si].displayNames[i];
                }
            }

            // Game disc icon next to each item
            float itemIconSz = isSel ? 50.0f * sf : 30.0f * sf;
            float iconX = selIconX + (iconSize * catActiveZoom) / 2.0f - itemIconSz / 2.0f;
            float iconY = fy - itemIconSz / 2.0f;
            drawIcon(16, iconX, iconY, itemIconSz,
                     isSel ? iconR : dimIconR, isSel ? iconG : dimIconG,
                     isSel ? iconB : dimIconB, iAlpha);

            float tx = iconX + itemIconSz + 10.0f * sf;
            float ty = fy - FONT_CHAR_H * tSc * 0.4f;
            float tr = isSel ? 1.0f : 0.6f;
            float tg = isSel ? 1.0f : 0.6f;
            float tb = isSel ? 1.0f : 0.6f;

            glEnable(GL_SCISSOR_TEST);
            glScissor((int)tx, 0, (int)(contentRight - tx), mHeight);
            drawText(displayText.c_str(), tx, ty, tSc, tr, tg, tb, iAlpha);
            if (isSel && !sysLabel.empty()) {
                float tagY = ty + FONT_CHAR_H * tSc + 2.0f * sf;
                drawText(sysLabel.c_str(), tx, tagY, catNameScale * 0.9f,
                         0.5f, 0.5f, 0.55f, 0.7f);
            }
            glDisable(GL_SCISSOR_TEST);
        }
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
                // Show "Quick Resuming..." screen for ~1s while checking
                // for SELECT button hold (bypass)
                bool bypass = false;
                float sf = fminf((float)mWidth / 1080.0f,
                                 (float)mHeight / 720.0f);
                if (sf < 0.5f) sf = 0.5f;
                float loadScale = 3.0f * sf;
                float hintScale = 1.5f * sf;

                for (int frame = 0; frame < 20 && !bypass; frame++) {
                    // Check current key state via ioctl
                    for (int fd : mInputFds) {
                        unsigned char keyState[(KEY_MAX + 7) / 8] = {};
                        if (ioctl(fd, EVIOCGKEY(sizeof(keyState)),
                                  keyState) >= 0) {
                            if (keyState[BTN_SELECT / 8]
                                    & (1 << (BTN_SELECT % 8))) {
                                bypass = true;
                            }
                        }
                        // Also drain events for SELECT press
                        struct input_event ev;
                        while (read(fd, &ev, sizeof(ev)) == sizeof(ev)) {
                            if (ev.type == EV_KEY && ev.code == BTN_SELECT
                                    && ev.value != 0) {
                                bypass = true;
                            }
                        }
                    }
                    // Render quick resume screen
                    glViewport(0, 0, mWidth, mHeight);
                    glClearColor(0.05f, 0.05f, 0.10f, 1.0f);
                    glClear(GL_COLOR_BUFFER_BIT);
                    glEnable(GL_BLEND);
                    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                    const char* msg = "Quick Resuming...";
                    float msgW = measureText(msg, loadScale);
                    float msgX = (mWidth - msgW) / 2.0f;
                    float msgY = (mHeight - FONT_CHAR_H * loadScale) / 2.0f;
                    drawText(msg, msgX, msgY, loadScale,
                             0.6f, 0.6f, 0.7f, 1.0f);
                    const char* hint = "Hold SELECT to cancel";
                    float hintW = measureText(hint, hintScale);
                    float hintX = (mWidth - hintW) / 2.0f;
                    float hintY = msgY + FONT_CHAR_H * loadScale + 20.0f * sf;
                    drawText(hint, hintX, hintY, hintScale,
                             0.4f, 0.4f, 0.5f, 1.0f);
                    glDisable(GL_BLEND);
                    eglSwapBuffers(mDisplay, mSurface);
                    usleep(50000); // 50ms per frame, ~1s total
                }

                if (!bypass) {
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
                                    if (mXmbSystems[si].roms[gi] == romFilename) {
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
                    if (!romFile.empty() && !coreFile.empty()) {
                        // Find save state and SRAM in the ROM cache dir
                        std::string baseName = romFile;
                        size_t dotPos = baseName.rfind('.');
                        if (dotPos != std::string::npos)
                            baseName = baseName.substr(0, dotPos);
                        std::string statePath = baseName + ".state.auto";
                        std::string sramPath = baseName + ".srm";

                        // Check if state/sram exist
                        struct stat st;
                        if (stat(statePath.c_str(), &st) != 0) statePath.clear();
                        if (stat(sramPath.c_str(), &st) != 0) sramPath.clear();

                        ALOGI("Quick Resume: trying native libretro launch");
                        ALOGI("  core=%s", coreFile.c_str());
                        ALOGI("  rom=%s", romFile.c_str());
                        ALOGI("  state=%s", statePath.c_str());
                        ALOGI("  sram=%s", sramPath.c_str());

                        LibretroRunner runner;
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

                            while (!exitPending()) {
                                // Poll input — game is live, user can play
                                for (int fd : mInputFds) {
                                    struct input_event ev;
                                    while (read(fd, &ev, sizeof(ev)) == sizeof(ev)) {
                                        if (ev.type == EV_KEY) {
                                            bool pressed = (ev.value != 0);
                                            switch (ev.code) {
                                            case BTN_A:      runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_B, pressed); break;
                                            case BTN_B:      runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_A, pressed); break;
                                            case BTN_X:      runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_Y, pressed); break;
                                            case BTN_Y:      runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_X, pressed); break;
                                            case BTN_TL:     runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_L, pressed); break;
                                            case BTN_TR:     runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_R, pressed); break;
                                            case BTN_TL2:    runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_L2, pressed); break;
                                            case BTN_TR2:    runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_R2, pressed); break;
                                            case BTN_SELECT: runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_SELECT, pressed); break;
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
                                }

                                // Check boot progress
                                if (!bootComplete) {
                                    char val[PROPERTY_VALUE_MAX] = {};
                                    property_get("sys.boot_completed", val, "0");
                                    if (strcmp(val, "1") == 0) {
                                        bootComplete = true;
                                        bootCompleteTime = elapsedRealtime();
                                        ALOGI("Quick Resume: boot complete, "
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
                                        // Fully saturated — hand off to RetroArch
                                        ALOGI("Quick Resume: handoff to RetroArch");
                                        runner.saveState(baseName + ".state.auto");
                                        runner.saveSRAM(baseName + ".srm");
                                        runner.shutdown();
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
                                        break;
                                    }
                                }

                                // Run core + render with desaturation + gradient
                                runner.runFrame(mWidth, mHeight, saturation, gradient);

                                // Text overlay
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

                                eglSwapBuffers(mDisplay, mSurface);
                                usleep(16666); // ~60fps
                            }
                        } else {
                            ALOGW("Quick Resume: native libretro init failed, "
                                  "falling back to RetroArch APK");
                        }
                    }

                    // Fallback: normal RetroArch APK launch
                    if (!nativeLaunch) {
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
                    ALOGI("Quick Resume: bypassed by SELECT hold");
                    property_set("persist.gammaos.nano.qr_prepared", "0");
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
        //   60fps during XMB animation (smooth transitions)
        //   30fps for XMB background effect
        //   20fps for other effects
        //   ~10fps idle
        bool xmbActive = (mCurrentEffect == 21);
        bool xmbAnimating = mXmbMode && (fabsf(mXmbAnimX - mXmbSystemIndex) > 0.01f
                                         || fabsf(mXmbAnimY - (mSearchActive
                                             ? (float)mSearchSelectedIndex
                                             : (float)mXmbGameIndex)) > 0.01f);
        bool animating = (mCurrentEffect != 0) || mShowBrightnessBar
                         || mWaitForRelease
                         || ((mMenuState == MENU_RECENT || mMenuState == MENU_APPS)
                             && mScrollOffset > 0.0f);
        int frameTimeUs;
        float dt;
        if (xmbActive || mXmbMode) {
            frameTimeUs = 16666; // 60fps for XMB
            dt = 1.0f / 60.0f;
        } else if (animating) {
            frameTimeUs = 50000; // 20fps
            dt = 1.0f / 20.0f;
        } else {
            frameTimeUs = 100000; // 10fps
            dt = 1.0f / 10.0f;
        }
        mEffectTime += dt;
        // Wrap time early to prevent mediump float precision degradation.
        // sin()/cos() with large args stutter on mediump (10-bit mantissa).
        // 62.83 = 10*2*PI — max shader multiplier is ~5x, so peak arg ~314,
        // well within mediump precision.
        if (mEffectTime > 628.318f) mEffectTime -= 628.318f;
        render();
        usleep(frameTimeUs);

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
            // Probe storage readiness (CE unlock) until it becomes available
            if (!mStorageReady) {
                if (access("/data/media/0", R_OK) == 0) {
                    mStorageReady = true;
                    mDisplayDirty = true;
                    ALOGI("GammaOS Nano: storage is now accessible");
                }
            }
            // Keep retrying ROM scan until all systems found
            if (mStorageReady && !mXmbRomScanDone) {
                scanRomPaths();
            }
        }
    }

    // GammaOS: Clear menu_active flag so DualStack can re-enable when app launches.
    property_set("sys.gammaos.nano.menu_active", "0");

    // Only re-apply performance clocks when launching an app (not on bootanim.exit)
    if (mExitRequested) {
        char mode[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.performance_mode", mode, "stock");
        char cmd[128];
        snprintf(cmd, sizeof(cmd), "/vendor/bin/setclock_%s.sh", mode);
        ALOGI("NanoMenu: re-applying performance mode '%s': %s", mode, cmd);
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
            // Render loading screen
            glViewport(0, 0, mWidth, mHeight);
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
            eglSwapBuffers(mDisplay, mSurface);

            property_get("sys.gammaos.nano.app_launched", launched, "0");
            if (!strcmp(launched, "1")) {
                ALOGD("NanoMenu: RetroArch launched, exiting");
                break;
            }
            usleep(16666);
        }
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
