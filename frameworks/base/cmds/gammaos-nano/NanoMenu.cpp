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
#include <sched.h>
#include <pthread.h>
#include <sys/resource.h>
#include <sys/syscall.h>

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

// DRM direct rendering subsystem (structs, variables, functions)
#include "NanoMenuDrm.h"
// Shared utility functions (path helpers, containsInsensitive)
#include "NanoMenuUtils.h"

#include <aidl/android/hardware/light/ILights.h>
#include <aidl/android/hardware/light/HwLight.h>

#include "LibretroRunner.h"
#include "DrasticRunner.h"
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
      mDrasticNanoPending(false),
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
        setLaunchRomPath(entry.romPath);
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
            setQrRomPath(entry.romPath);
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
        setLaunchRomPath("");
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

    // GammaOS: Detect drastic QR fast-path early so subsequent init
    // stages can skip heavy work (particle/fx/XMB shader compiles,
    // ROM scanning, icon texture load) and go straight to the drastic
    // render loop. This is the analog of the libretro minimal boot
    // path. Read the props directly; we don't want a race with
    // waiting for NanoMenu's other props.
    //
    // Two trigger conditions:
    //   1) persist.gammaos.nano.drastic_smoke=1 -- debug smoke-test
    //      knob, still used by runDrasticInitIfNeeded() in main.cpp
    //      for A/B service-wait experiments.
    //   2) persist.gammaos.nano.qr_prepared=1 AND qr_core="drastic"
    //      -- real QR path primed by launchXmbGame() when the user
    //      opens a Nintendo DS game. The "drastic" sentinel in
    //      qr_core distinguishes drastic QR from libretro QR (which
    //      stores a full core .so path).
    {
        char smoke[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.drastic_smoke", smoke, "0");
        bool smokeActive = (strcmp(smoke, "1") == 0);

        char qp[PROPERTY_VALUE_MAX] = {};
        char qc[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.qr_prepared", qp, "0");
        property_get("persist.gammaos.nano.qr_core", qc, "");
        bool drasticQrPrimed = (strcmp(qp, "1") == 0) &&
                               (strcmp(qc, "drastic") == 0);

        sDrasticQrFastPath = smokeActive || drasticQrPrimed;
        if (sDrasticQrFastPath) {
            ALOGW("NanoMenu: drastic QR fast-path ACTIVE "
                  "(smoke=%d qr_primed=%d), skipping heavy init",
                  smokeActive ? 1 : 0, drasticQrPrimed ? 1 : 0);
        }
    }

    // GammaOS: Nano mode is confirmed active. Show DRM splash now — before any
    // SF/HWC setup. This replaces the U-Boot logo with a dark screen within
    // milliseconds on devices where HWC composition isn't ready yet (e.g.
    // dual-DSI RG DS RK3568). Skip DRM on restarts (returning from app) —
    // HWC is already active by then.
    {
        char bootDone[PROPERTY_VALUE_MAX] = {};
        property_get("sys.boot_completed", bootDone, "0");
        // GammaOS: force_drm=1 re-grabs DRM master post-boot for
        // drastic nano mode (XMB -> drastic nano restart path). The
        // XMB launch path sets this before restarting NanoMenu so the
        // new instance takes DRM even though boot is already complete.
        char forceDrm[PROPERTY_VALUE_MAX] = {};
        property_get("sys.gammaos.nano.force_drm", forceDrm, "0");
        if (strcmp(bootDone, "1") != 0 || strcmp(forceDrm, "1") == 0) {
            if (strcmp(forceDrm, "1") == 0) {
                ALOGW("NanoMenu: force_drm=1, grabbing DRM master "
                      "post-boot for drastic nano");
            }
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
    if (!sDrasticQrFastPath) {
        initXmbSystems();
        loadXmbRecent();
    }
    openInputDevices();
    if (!sDrasticQrFastPath) {
        initEffects();
    }
    if (sDrasticQrFastPath) {
        tlog("drastic fast-path skipped XMB+effects");
    }

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
    // GammaOS: Drastic QR fast-path skips the particle/fx/XMB shaders
    // entirely. They are expensive (~500-700ms cumulative on a Mali
    // G52) and only used by NanoMenu's normal render() path, which
    // the drastic QR loop bypasses. Set all unused locations to -1
    // so uploadRotationMatrices / renderEffect etc. can detect and
    // skip them if ever reached accidentally.
    if (!sDrasticQrFastPath) {
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
    } else {
        mParticleProgram = 0; mFxProgram = 0; mXmbProgram = 0;
        mParticleLocPosition = mParticleLocColor = mParticleLocRotation = -1;
        mFxLocPosition = mFxLocTime = mFxLocResolution = -1;
        mFxLocEffect = mFxLocRotation = mFxLocCoordSwap = -1;
        mXmbLocPosition = mXmbLocTime = mXmbLocResolution = -1;
        mXmbLocRotation = mXmbLocCoordSwap = -1;
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
    // GammaOS: Drastic QR fast-path skips icon texture init -- icons
    // are only used by the XMB/menu render() path which isn't reached
    // during drastic QR. Saves ~100-200ms + ~8 MB of GL memory.
    if (!sDrasticQrFastPath) {
        initIconTextures();
    }
}

// ---------------------------------------------------------------------------
// Icon texture rendering (monochrome 32x32 icons, tinted at draw time)
// ---------------------------------------------------------------------------

// Map system index to RetroArch XMB monochrome icon filename
// Order MUST match kXmbSystemDefs (in NanoMenuXmb.cpp): NES,SNES,GB,GBC,GBA,N64,NDS,GEN,SMS,GG,PSX,PSP,DC,NGP,P8,history
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

    // GammaOS: Drastic quick-resume dual-screen split.
    //
    // When DrasticRunner is active (smoke test path or production QR
    // for NDS ROMs), both displays are repurposed to show the two DS
    // screens full-size:
    //   primary display  (port 1 on RG DS) -> TOP DS screen
    //   secondary display (port 0)          -> BOTTOM DS screen
    // The wallpaper + XMB are suppressed on both passes. A gradient +
    // "Quick Resuming..." text overlay matches the LibretroRunner QR
    // look, with a fade from desaturated+dark to full color once
    // boot_completed fires.
    DrasticRunner* drastic = DrasticRunner::getInstance();
    const bool drasticActive = drastic && drastic->isInitialized();
    static float sDrasticSaturation = 0.15f;
    static float sDrasticGradient   = 1.0f;
    if (drasticActive) {
        // Idempotent: initSurface is a no-op after the first call.
        drastic->initSurface(mWidth, mHeight, false);
        // Push the DRM rotation matrix so our DS quads come out in
        // panel-native orientation (matching NanoMenu's XMB).
        drastic->setRotationMatrix(sDrmRotMat);
        // Pull fresh pixels ONCE per frame, then reuse the textures
        // across both display passes.
        drastic->updatePixels();

        // Advance the fade. Mirror LibretroRunner's QR transition:
        // creep gently during boot, ramp fast once home_launching or
        // boot_completed fires.
        char val[PROPERTY_VALUE_MAX] = {};
        bool ready = false;
        property_get("sys.gammaos.nano.home_launching", val, "");
        ready = (strcmp(val, "1") == 0);
        if (!ready) {
            property_get("sys.boot_completed", val, "0");
            ready = (strcmp(val, "1") == 0);
        }
        if (ready) {
            sDrasticSaturation = fminf(sDrasticSaturation + 0.01f, 1.0f);
            sDrasticGradient   = fmaxf(sDrasticGradient   - 0.01f, 0.0f);
        } else {
            sDrasticSaturation = fminf(sDrasticSaturation + 0.0004f, 0.35f);
            sDrasticGradient   = fmaxf(sDrasticGradient   - 0.0003f, 0.7f);
        }
    }

    // Small inline "Quick Resuming..." + ROM name overlay used by both
    // drastic passes. Matches LibretroRunner's libretro QR loop.
    auto drawDrasticQrOverlay = [this](int vpW, int vpH,
                                        float saturation, float gradient) {
        (void)gradient;
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        float textScale = fminf((float)vpW / 1080.0f, (float)vpH / 720.0f);
        if (textScale < 0.5f) textScale = 0.5f;
        float loadScale = 2.5f * textScale;
        const char* msg = "Quick Resuming...";
        float msgW = measureText(msg, loadScale);
        float msgX = ((float)vpW - msgW) / 2.0f;
        float msgY = (float)vpH * 0.78f;
        float pulse = 0.7f + 0.3f * sinf((float)elapsedRealtime() * 0.004f);
        float textAlpha = pulse * fmaxf(1.2f - saturation, 0.0f);
        if (textAlpha > 0.05f) {
            if (textAlpha > 1.0f) textAlpha = 1.0f;
            drawText(msg, msgX, msgY, loadScale,
                     1.0f, 1.0f, 1.0f, textAlpha);
        }
        glDisable(GL_BLEND);
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
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        if (drasticActive) {
            // Secondary display -> BOTTOM DS screen fullscreen.
            drastic->renderBottomScreen(sDrasticSaturation, sDrasticGradient);
            // drawText inside the overlay lambda uses mWidth/mHeight for
            // pixel->NDC; panel-native AHB dims would mis-project the text
            // on rotated single-display devices (RK3576 1080x1920).
            drawDrasticQrOverlay(mWidth, mHeight,
                                 sDrasticSaturation, sDrasticGradient);
        } else {
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            renderEffect();
            glDisable(GL_BLEND);
        }
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
    glClearColor(drasticActive ? 0.0f : 0.05f,
                 drasticActive ? 0.0f : 0.05f,
                 drasticActive ? 0.0f : 0.10f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    // Primary pass -> TOP DS screen fullscreen + QR overlay when
    // drastic quick-resume is active. Skip the wallpaper + XMB.
    if (drasticActive) {
        drastic->renderTopScreen(sDrasticSaturation, sDrasticGradient);
        // drawText in the overlay uses mWidth/mHeight internally; the
        // shader's uRotation uniform handles the panel rotation. Passing
        // AHB dims here drops the text off-NDC on rotated panels.
        drawDrasticQrOverlay(mWidth, mHeight,
                             sDrasticSaturation, sDrasticGradient);
    } else {

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
            // Scissor is applied in FBO pixel coords AFTER the vertex
            // shader's rotation, so the logical-landscape rect we want
            // (a horizontal band across the menu column) needs to be
            // remapped to the panel-native FBO before glScissor. Without
            // this, on a 90/270-rotated panel the scissor still clips a
            // landscape band of the FBO, which only covers a fraction of
            // the rotated content -- text outside that fraction gets
            // truncated. RK3576 (1080x1920 portrait, 270° install) is
            // the device that surfaced this.
            int sx, sy, sw, sh;
            int lx = (int)contentLeft, ly = 0, lw = (int)contentW,
                lh = (int)mHeight;
            switch (sDrmGlRotation ? sDrmRotationDeg : 0) {
            case 90:
                sx = ly; sy = mWidth - lx - lw;
                sw = lh; sh = lw;
                break;
            case 180:
                sx = mWidth - lx - lw; sy = mHeight - ly - lh;
                sw = lw; sh = lh;
                break;
            case 270:
                sx = mHeight - ly - lh; sy = lx;
                sw = lh; sh = lw;
                break;
            default:
                sx = lx; sy = ly; sw = lw; sh = lh;
                break;
            }
            glScissor(sx, sy, sw, sh);
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
    } // close drasticActive-else wrapper

    glDisable(GL_BLEND);

    // GammaOS: DRM direct rendering path.
    // - Zero-copy: GPU rendered straight into the scanout FBO; just page flip.
    // - Fallback: glReadPixels → CPU copy to dumb buffer → page flip.
    // Either way, skip eglSwapBuffers (it blocks when HWC doesn't consume buffers).
    if (sDrmActive) {
        if (sDrmZeroCopy) {
            // XMB ring path: same triple-buffer mechanism used by drastic QR.
            // Decouples glFinish (GPU wait, ~11 ms on RK3576 1080x1920) from
            // the CPU-side blit+flip (~10 ms) by presenting a slot whose GPU
            // work is ~2 iterations old -- its fence is already signaled so
            // the per-slot AHB_lock returns fast, and we don't block the
            // render thread on the current slot's in-flight GPU work. Gated
            // on persist.gammaos.nano.triple_buffer (same prop as QR). Resolved
            // once per process since the prop + slot availability don't change
            // at runtime.
            static int sXmbRingEnabled = -1;
            if (sXmbRingEnabled < 0) {
                char prop[PROPERTY_VALUE_MAX] = {};
                property_get("persist.gammaos.nano.triple_buffer", prop, "1");
                const bool flagOn = (prop[0] == '1');
                bool slotsOk = (sEglCreateSyncKHR != nullptr) &&
                               (sRingEglDpy != EGL_NO_DISPLAY);
                for (int i = 0; i < AHB_RING_DEPTH && slotsOk; i++) {
                    if (sAhbRingPrimary[i].glFbo == 0) slotsOk = false;
                }
                sXmbRingEnabled = (flagOn && slotsOk) ? 1 : 0;
                if (sXmbRingEnabled) {
                    sRingRenderIdx = 0;
                    sRingPresentIdx = 0;
                    sRingPrimedCount = 0;
                }
                ALOGW("NanoMenu XMB ring %s (flag=%d slotsOk=%d)",
                      sXmbRingEnabled ? "ENABLED" : "disabled",
                      flagOn ? 1 : 0, slotsOk ? 1 : 0);
            }

            if (sXmbRingEnabled) {
                const int renderIdxNow = sRingRenderIdx;
                // Unbind the AHB FBO and insert a native fence. This implicit
                // flush kicks the GPU without waiting -- the fence will signal
                // when all commands issued for this slot complete.
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                if (sAhbRingSyncPrimary[renderIdxNow] != EGL_NO_SYNC_KHR
                        && sEglDestroySyncKHR) {
                    sEglDestroySyncKHR(sRingEglDpy,
                            sAhbRingSyncPrimary[renderIdxNow]);
                }
                sAhbRingSyncPrimary[renderIdxNow] = sEglCreateSyncKHR(
                        sRingEglDpy, EGL_SYNC_NATIVE_FENCE_ANDROID, nullptr);
                if (sAhbRingSyncPrimary[renderIdxNow] == EGL_NO_SYNC_KHR) {
                    // Fence creation failed -- explicit flush so downstream
                    // drmFlipRingSlot's glFinish fallback observes our work.
                    glFlush();
                }
                // Advance render cursor BEFORE present so the macro
                // sAhbTarget resolves to the next slot on the next render()
                // call. Present uses presentIdx which lags by 2 (ring depth
                // minus 1), reading an older slot whose fence is signaled.
                sRingRenderIdx = (renderIdxNow + 1) % AHB_RING_DEPTH;
                // Threshold = 2 (not depth-1) keeps present-lag at 2 regardless of
// ring depth. With depth N and lag L, slot M is rendered at iter M
// and re-rendered at iter M+N, but display still owns it through
// iter M+L+1. Race-free requires L <= N-2. With depth 4 and L=2
// (this threshold), we have 1 slot of headroom = no GL/scanout
// races on the DRM PRIME path.
if (sRingPrimedCount >= 2) {
                    const int presentIdxNow = sRingPresentIdx;
                    drmFlipRingSlot(presentIdxNow);
                    sRingPresentIdx =
                            (presentIdxNow + 1) % AHB_RING_DEPTH;
                } else {
                    sRingPrimedCount++;
                }
            } else {
                drmFlipAll(); // includes glFinish + CPU blit + page flip
            }
        } else {
            drmPushFrame(mWidth, mHeight);
        }

        // GammaOS: Vsync lock for DRM-direct XMB rendering.
        //
        // Without an explicit DRM_IOCTL_WAIT_VBLANK here the loop runs as
        // fast as drmFlipAll can complete, which on Mali G52 is ~8-11 ms
        // per frame. Every second call to drmModePageFlip can return
        // -EBUSY (previous flip pending), which falls through to the
        // blocking drmModeSetCrtc in drmFlipAll's fallback path and
        // produces irregular pacing. Baseline measurement (2026-04-13)
        // showed XMB at 46-48 fps in DRM-direct mode, with frame times
        // oscillating 8-35 ms.
        //
        // Relative-vblank sequence=1 blocks until the panel has
        // completed one vblank, matching the QR loop's pacing at
        // line ~6822 of this file and giving us a stable 60 fps lock
        // as long as the per-frame work fits inside 16.67 ms.
        // Render-loop sync. Two paths:
        //
        // - Working vblank (default): DRM_IOCTL_WAIT_VBLANK with relative
        //   sequence=1 blocks until the next panel vblank. Cheap (one
        //   ioctl) and ignores cross-CRTC timing on dual-display setups.
        //
        // - Broken vblank (RK3576 DSI command-mode): the kernel's vblank
        //   queue never wakes -> WAIT_VBLANK hits the 3s timeout. The
        //   first slow wait flips sDrmVblankBroken; subsequent iterations
        //   skip the ioctl and pace via drmDrainPageFlipEvents() instead
        //   (which reads the per-flip events those panels DO generate).
        //
        // The whole gate can be disabled at runtime with
        // persist.gammaos.nano.vsync=0 (default 1) -- diagnostic, lets us
        // measure vsync overhead vs other sources of jitter. Read once.
        if (sVsyncEnabled < 0) {
            char vp[PROPERTY_VALUE_MAX] = {};
            property_get("persist.gammaos.nano.vsync", vp, "1");
            sVsyncEnabled = (vp[0] == '0') ? 0 : 1;
            ALOGW("NanoMenu vsync gate %s",
                  sVsyncEnabled ? "ENABLED" : "DISABLED (no WAIT_VBLANK / no event drain)");
        }
        if (sVsyncEnabled) {
            // WAIT_VBLANK is only safe on single-CRTC setups with working
            // vblank. Multi-CRTC setups (RG DS dual DSI) pace via
            // drmDrainPageFlipEvents instead so the sync gate waits for
            // flips on BOTH displays to complete. Broken-vblank panels
            // (RK3576 DSI command-mode) also skip WAIT_VBLANK.
            if (sDrmFd >= 0 && !sDrmDisplays.empty() &&
                !sDrmVblankBroken && sDrmDisplays.size() <= 1) {
                int64_t vblT0 = systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL;
                union drm_wait_vblank vbl = {};
                vbl.request.type = (enum drm_vblank_seq_type)(
                        _DRM_VBLANK_RELATIVE
                        | ((sDrmPrimaryIdx & 0x1f)
                           << _DRM_VBLANK_HIGH_CRTC_SHIFT));
                vbl.request.sequence = 1;
                ioctl(sDrmFd, DRM_IOCTL_WAIT_VBLANK, &vbl);
                int64_t vblElapsed =
                        systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL - vblT0;
                if (vblElapsed > 100000) {
                    sDrmVblankBroken = true;
                    ALOGW("NanoMenu: DRM_IOCTL_WAIT_VBLANK took %lld us -- "
                          "switching to page-flip-event pacing",
                          (long long)vblElapsed);
                }
            }
            // Drain pending events. No-op when no events were requested
            // (single-display + working vblank). Sync gate for broken-vblank
            // and multi-display setups.
            drmDrainPageFlipEvents();
        } else {
            // Vsync disabled: still cap the render rate at 60 fps so we
            // can compare CPU/pacing fairly. Tearing is expected.
            drmPaceWithoutVsync();
        }

        // GammaOS: drmStop() is no longer called from here.
        //
        // The previous behaviour was "after 30 frames post
        // boot_completed, unconditionally switch the XMB pipeline from
        // DRM-direct to HWC". The intent was to hand displays to
        // SurfaceFlinger so a launched app could present. But the
        // transition happened whether or not the user was actually
        // about to launch an app, which meant the XMB itself ran in
        // HWC for the rest of the session -- with the extra latency
        // of eglSwapBuffers paced by HWC's compositor tick.
        //
        // For idle XMB (no app in flight) we get better and more
        // predictable pacing by staying in DRM-direct mode:
        //   - Our render thread directly controls page flips via
        //     drmModePageFlip + DRM_IOCTL_WAIT_VBLANK above.
        //   - No SurfaceFlinger compositor tick in the critical path.
        //   - No BLASTBufferQueue buffer starvation under load.
        //
        // drmStop() + setupSecondaryEglSurfaces() now run exactly
        // once, immediately after the main XMB loop exits (see the
        // post-loop section further down, gated on
        // mExitRequested). That way SurfaceFlinger is given the
        // displays at the moment we are about to launch an Android
        // app, which matches the original intent without paying the
        // DRM->HWC transition cost for XMB browsing.
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

    // GammaOS: Render wallpaper (or bottom DS screen when drastic QR
    // is active) to secondary display(s). Switch to each secondary
    // EGL surface, render, swap.
    for (size_t i = 0; i < mSecondaryEglSurfaces.size(); i++) {
        eglMakeCurrent(mDisplay, mSecondaryEglSurfaces[i], mSecondaryEglSurfaces[i], mContext);
        glViewport(0, 0, mWidth, mHeight); // secondary has same resolution
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        if (drasticActive) {
            // Secondary display -> bottom DS screen fullscreen.
            drastic->renderBottomScreen(sDrasticSaturation, sDrasticGradient);
            drawDrasticQrOverlay(mWidth, mHeight,
                                 sDrasticSaturation, sDrasticGradient);
        } else {
            renderEffect();
        }
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
// Main loop
// ---------------------------------------------------------------------------

bool NanoMenu::threadLoop() {
    ALOGD("NanoMenu: entering main loop");
    {
        int64_t nowMs = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
        ALOGW("NanoMenu BOOT TIMING: main loop entry at T+%lldms", nowMs);
    }

    // GammaOS: Real-time boost for the render thread.
    //
    // Measured behaviour on a 4-core RK3566 (2026-04-13):
    //   post-boot steady state: 59.9 fps locked via DRM_IOCTL_WAIT_VBLANK
    //   boot window (~15 s):    occasional 33-100+ ms frame spikes
    //                           caused by vendor HAL init
    //                           (vendor.usb_gadget_default,
    //                           vendor.rockit-hal, vendor.power-aidl,
    //                           vendor.outputmanager, etc.) and
    //                           kernel interrupt activity preempting
    //                           our render thread.
    //
    // SCHED_FIFO prio 80: comfortably above every SCHED_OTHER thread,
    // above SurfaceFlinger / vndbinder / Mali helpers (FIFO 2), above
    // the Android audio/input tier (~FIFO 50), and below the kernel
    // migration / RCU tier (FIFO 99).
    //
    // FIFO over RR because drastic's internal threads also run at RR 5.
    // Under SCHED_RR peers at equal priority time-slice between each
    // other; a slice expiry mid-flip could push the render thread past
    // vblank. SCHED_FIFO never gets sliced out. The render thread
    // spends most of its budget blocked in poll() on the DRM fd
    // (drmDrainPageFlipEvents) or WAIT_VBLANK, so even at prio 80 it
    // yields enough wall-clock time for drastic's RR threads to
    // produce frames.
    //
    // History: 2026-04-13 iteration log on RK3566 RG DS:
    //   RR 5          -> 92% at >=59.5 fps, avg 59.37 (XMB);
    //                   drastic QR avg 58.5, ~40% frames with 30-50 ms spikes
    //   FIFO 5        -> same order as RR 5, ~12 catastrophic frames per
    //                   3 min on Sonic Rush drastic QR
    //   FIFO 80       -> 0 catastrophic frames per 3 min, p99=17.0 ms,
    //                   peak 17.2 ms (essentially one vblank)
    // The earlier "FIFO 90 regression to 40 fps" observation from RR
    // experiments predates the drmDrainPageFlipEvents sync gate; the
    // old pacing path had the render thread busy-waiting in some
    // cases, which starved peer threads at equal prio. The current
    // drain blocks in poll(), so the FIFO task yields cleanly and high
    // priority is safe.
    //
    // Nice=-20 is layered on top so the SCHED_OTHER fallback (below,
    // when RT is denied) still dominates normal threads.
    //
    // Applied at threadLoop entry so it covers both the DRM-direct XMB
    // path and the drastic QR fast-path (same render thread).
    {
        sched_param sp = {};
        sp.sched_priority = 80;
        int rc = pthread_setschedparam(pthread_self(), SCHED_FIFO, &sp);
        pid_t selfTid = (pid_t)syscall(SYS_gettid);
        setpriority(PRIO_PROCESS, selfTid, -20);
        if (rc == 0) {
            ALOGW("NanoMenu: render thread SCHED_FIFO prio 80 + nice -20 ok");
        } else {
            ALOGW("NanoMenu: SCHED_FIFO denied (%s), nice -20 applied",
                  strerror(rc));
        }
    }

    // GammaOS: Render thread is NOT pinned to a specific CPU.
    //
    // Tried pinning to CPU 3 (2026-04-13) as an attempt to isolate
    // from drastic's rasterizer threads floating on 0-2. Measured
    // result was WORSE: avg 58.5 vs 59.2 fps unpinned, 71 % at 60 fps
    // vs 82 %, 29 % spike rate vs 18 %. Conclusion: on this SMP
    // device the kernel's load balancer works in our favour -- when
    // CPU 3 has a transient IRQ burst the unpinned render thread
    // migrates away; pinning locks us to the stalled core. The
    // FIFO 5 priority boost alone is sufficient.

    // GammaOS: Lock all pages into RAM.
    //
    // Even with SCHED_RR prio 5, the 50-100 ms spikes during the first
    // ~25 seconds of boot persisted -- those durations rule out
    // scheduler preemption and point at kernel-side stalls. The two
    // most likely causes at that timescale are page-fault I/O (kernel
    // pulls a demand-paged page off storage while the render thread is
    // blocked) and dirty-page writeback stealing memory bandwidth.
    //
    // mlockall(MCL_CURRENT | MCL_FUTURE) pins the process's current
    // working set and every future allocation into physical memory,
    // so no subsequent access triggers a fault. Paired with
    // IPC_LOCK + SYS_RESOURCE + rlimit memlock in gammaos-nano.rc so
    // the 64 KB default cap doesn't cause EPERM/ENOMEM. The trade is
    // ~50 ms of up-front fault cost at startup for predictable frame
    // timing thereafter.
    if (mlockall(MCL_CURRENT | MCL_FUTURE) == 0) {
        ALOGW("NanoMenu: mlockall done");
    } else {
        ALOGW("NanoMenu: mlockall failed (%s) -- check caps/rlimit in "
              "gammaos-nano.rc", strerror(errno));
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

    // GammaOS: Drastic QR fast-path dedicated render loop.
    //
    // When sDrasticQrFastPath is set (debug smoke-test prop OR the
    // real QR path primed via qr_core="drastic"), NanoMenu takes over
    // the render pipeline directly with a tight loop that drives
    // drastic's getScreenBuffers output into both displays via
    // drmFrameBegin/End. This mirrors the libretro QR loop below but
    // with the dual-screen split (primary = top DS, secondary =
    // bottom DS) and without the normal XMB cycle.
    //
    // Key timing win: the loop starts here at threadLoop entry (right
    // after readyToRun finishes), NOT via NanoMenu::render() which
    // would add another pass of boot-timing overhead. Combined with
    // skipping particle/fx/XMB shader compiles in readyToRun, this
    // brings the drastic first frame ~800ms closer to boot.
    if (sDrasticQrFastPath) {
        DrasticRunner* drastic = DrasticRunner::getInstance();
        if (drastic && drastic->isInitialized()) {
            ALOGW("NanoMenu: drastic QR fast-path loop entered");
            int64_t loopStart = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
            ALOGW("NanoMenu BOOT TIMING: drastic QR loop entry at T+%lldms",
                  loopStart);

            // Max clocks for smooth DS emulation during QR preview.
            system("/vendor/bin/setclock_max.sh");

            bool hasDualDisplay = (sDrmActive && sDrmZeroCopy &&
                                    sAhbTargetSecondary.glFbo != 0);
            drastic->initSurface(mWidth, mHeight, hasDualDisplay);
            drastic->setRotationMatrix(sDrmRotMat);

            // Determine whether this is the real QR path (primed by
            // launchXmbGame) or the smoke-test debug path. The real
            // path will trigger an app handoff when the fade finishes;
            // the smoke path keeps rendering indefinitely.
            bool drasticQrHandoff = false;
            {
                char qc[PROPERTY_VALUE_MAX] = {};
                char qp[PROPERTY_VALUE_MAX] = {};
                property_get("persist.gammaos.nano.qr_core", qc, "");
                property_get("persist.gammaos.nano.qr_prepared", qp, "0");
                drasticQrHandoff = (strcmp(qc, "drastic") == 0) &&
                                   (strcmp(qp, "1") == 0);

                // GammaOS: Development-only toggle to keep the drastic
                // QR loop running indefinitely instead of handing off to
                // the real com.dsemu.drastic activity. Used to profile
                // and tune the preview-mode framerate in isolation from
                // the Android app transition. When set to "1" we pretend
                // handoff is not requested; the per-frame check at
                // `if (t >= 1.0f && drasticQrHandoff ...)` never fires.
                //
                // Uses persist.* so it survives reboot -- the QR loop
                // starts before any post-boot script has a chance to
                // set a volatile sys.* prop.
                char blockHandoff[PROPERTY_VALUE_MAX] = {};
                property_get("persist.gammaos.nano.qr_block_handoff",
                             blockHandoff, "0");
                if (blockHandoff[0] == '1') {
                    drasticQrHandoff = false;
                    ALOGW("drastic QR: handoff blocked via "
                          "persist.gammaos.nano.qr_block_handoff=1 -- "
                          "loop will stay in preview mode");
                }
            }

            // Load the game name once so the overlay has a stable
            // label across the loop. Matches the libretro QR pattern.
            std::string drasticGameName = android::base::GetProperty(
                    "persist.gammaos.nano.qr_game_name", "");
            if (drasticGameName.empty()) {
                // Fallback: scan the cached drastic rom dir for the
                // single staged .nds file. populate_drastic only
                // keeps one ROM at a time.
                DIR* d = opendir(
                        "/data/system/nano_cache/drastic/rom");
                if (d) {
                    struct dirent* e;
                    while ((e = readdir(d)) != nullptr) {
                        std::string name(e->d_name);
                        if (name == "." || name == "..") continue;
                        if (name.size() >= 4 &&
                            name.compare(name.size() - 4, 4, ".nds") == 0) {
                            size_t dotPos = name.rfind('.');
                            drasticGameName = (dotPos != std::string::npos)
                                    ? name.substr(0, dotPos) : name;
                            break;
                        }
                    }
                    closedir(d);
                }
            }
            ALOGI("drastic QR: overlay name=\"%s\" handoff=%d",
                  drasticGameName.c_str(), drasticQrHandoff ? 1 : 0);

            // GammaOS: smoke-test mode (persist.gammaos.nano.drastic_smoke=1)
            // is the dev/profiling path -- no overlay text, full color
            // immediately, and SELECT is forwarded to drastic as a real
            // DS button instead of being eaten as the "drop to XMB"
            // sentinel. Lets us measure the rendering pipeline without
            // any of the boot-time UX overlay.
            bool smokeActive = false;
            {
                char sm[PROPERTY_VALUE_MAX] = {};
                property_get("persist.gammaos.nano.drastic_smoke", sm, "0");
                smokeActive = (sm[0] == '1');
            }

            // GammaOS: Drastic Nano mode. When enabled, drastic runs
            // entirely through DrasticRunner in DRM-direct rendering
            // mode -- the real com.dsemu.drastic app is never launched.
            // Behavior: full color immediately (no desaturation fade),
            // no overlay text, no handoff to the real drastic app.
            // Long-press BACK (3s) exits to XMB. DRM master is held
            // for the entire session for lowest latency.
            bool drasticNanoActive = false;
            {
                char dn[PROPERTY_VALUE_MAX] = {};
                property_get("persist.gammaos.nano.drastic_nano",
                             dn, "0");
                drasticNanoActive = (dn[0] == '1');
                if (drasticNanoActive) {
                    drasticQrHandoff = false;
                    ALOGW("drastic nano: mode active, handoff "
                          "suppressed, DRM-direct rendering");
                }
            }

            float saturation = (smokeActive || drasticNanoActive)
                    ? 1.0f : 0.15f;
            float gradient = (smokeActive || drasticNanoActive)
                    ? 0.0f : 1.0f;
            float textScale = fminf((float)mWidth / 1080.0f,
                                     (float)mHeight / 720.0f);
            if (textScale < 0.5f) textScale = 0.5f;
            float loadScale = 2.5f * textScale;
            float nameScale = 1.5f * textScale;

            // Upload rotation matrices once -- they persist on the
            // text shader until we change programs.
            if (sDrmGlRotation || mTextLocRotation >= 0) {
                glUseProgram(mTextProgram);
                glUniformMatrix2fv(mTextLocRotation, 1, GL_FALSE, sDrmRotMat);
            }

            bool handoffPaused = false;
            bool firstFrameLogged = false;
            int64_t bootCompleteTime = 0;
            bool bootComplete = false;
            bool handoffFired = false;
            // Storage-readiness gate for the QR handoff. We hold the
            // handoff until the QR ROM's underlying volume is mounted
            // (vold defers external SD scan ~3-5s after boot start),
            // otherwise drastic resolves the content URI to "not found"
            // and falls back to its main menu.
            bool handoffStorageWaitLogged = false;
            int64_t handoffWaitStartMs = 0;
            const int64_t handoffWaitTimeoutMs = 10000; // 10s ceiling

            // GammaOS: Drastic QR preview input + handoff control.
            //
            // The preview loop is fully playable -- all buttons
            // including SELECT reach the running DS core via
            // DrasticRunner::setInput. Long-press BACK (3s) exits
            // to the NanoMenu XMB. The handoff fires automatically
            // when the color transition completes and storage is
            // ready.
            int dsBtnMask = 0;
            bool qrCancelled = false;
            bool backWasDown = false;

            // Long-press BACK (3s) to exit to XMB. Track when BACK
            // was first pressed and fire qrCancelled when the hold
            // duration exceeds the threshold.
            int64_t backPressStartMs = 0;
            const int64_t kNanoBackHoldMs = 3000;

            // Shared overlay draw — "Quick Resuming..." + ROM name.
            // Matches the libretro QR loop's pattern at 6687-6702.
            auto drawOverlay = [&](int vpW, int vpH) {
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                const char* msg = "Quick Resuming...";
                float msgW = measureText(msg, loadScale);
                float msgX = ((float)vpW - msgW) / 2.0f;
                float msgY = (float)vpH * 0.78f;
                float pulse = 0.7f + 0.3f * sinf(
                        (float)elapsedRealtime() * 0.004f);
                float textAlpha = pulse * fmaxf(1.2f - saturation, 0.0f);
                if (textAlpha > 0.05f) {
                    if (textAlpha > 1.0f) textAlpha = 1.0f;
                    drawText(msg, msgX, msgY, loadScale,
                             1.0f, 1.0f, 1.0f, textAlpha);
                    if (!drasticGameName.empty()) {
                        float nameW = measureText(
                                drasticGameName.c_str(), nameScale);
                        float nameX = ((float)vpW - nameW) / 2.0f;
                        float nameY = msgY +
                                FONT_CHAR_H * loadScale + 12.0f * textScale;
                        drawText(drasticGameName.c_str(),
                                 nameX, nameY, nameScale,
                                 0.7f, 0.7f, 0.8f, textAlpha * 0.8f);
                    }
                }
                glDisable(GL_BLEND);
            };

            // GammaOS: Track frames for periodic hotplug check. The
            // retrogame_joypad / Xbox Wireless Controller device on
            // RG DS can appear after openInputDevices() runs (~T+7s)
            // because the driver module loads late. Without a hotplug
            // check here, the QR loop never picks up that fd and all
            // face-button events are lost.
            int hotplugCounter = 0;

            // GammaOS: Triple-buffer AHB ring opt-in for this QR session.
            // Read persist.gammaos.nano.triple_buffer ONCE at loop entry so
            // toggling it mid-run doesn't desync ring cursors. When on, the
            // QR render body writes into sAhbRingPrimary[renderIdx] and the
            // glFinish() in drmFlipRingSlot presents slot (renderIdx-2) --
            // see docs/triple-buffer-plan.md for rationale. Falls back to
            // single-buffered slot 0 if any required slot wasn't allocated
            // (OOM path), so the prop is safe to leave on by default.
            bool qrUseTripleBuffer = false;
            {
                char prop[PROPERTY_VALUE_MAX] = {};
                property_get("persist.gammaos.nano.triple_buffer", prop, "0");
                qrUseTripleBuffer = (prop[0] == '1');
                if (qrUseTripleBuffer) {
                    // Every primary slot must exist. When hasDualDisplay,
                    // every secondary slot must exist too.
                    for (int i = 0; i < AHB_RING_DEPTH; i++) {
                        if (sAhbRingPrimary[i].glFbo == 0 ||
                            (hasDualDisplay && sAhbRingSecondary[i].glFbo == 0)) {
                            qrUseTripleBuffer = false;
                            ALOGW("drastic QR: triple_buffer disabled, "
                                  "slot %d not fully allocated", i);
                            break;
                        }
                    }
                }
                if (qrUseTripleBuffer) {
                    sRingRenderIdx = 0;
                    sRingPresentIdx = 0;
                    sRingPrimedCount = 0;
                }
                ALOGW("drastic QR: triple_buffer=%d",
                      qrUseTripleBuffer ? 1 : 0);
            }

            while (!exitPending() && !qrCancelled) {
                // Check for new input devices every ~0.5s (30 frames
                // at 60fps). Cheap: inotify_read is non-blocking.
                if (++hotplugCounter >= 30) {
                    hotplugCounter = 0;
                    checkInputHotplug();
                }

                // GammaOS: Poll input → DS button mask. Matches the
                // libretro QR input block at NanoMenu.cpp:~6968. We
                // drain each gamepad fd with non-blocking reads and
                // accumulate a sticky mask (dsBtnMask) so held buttons
                // stay held across frames. Axis D-pad (ABS_HAT0X/Y) is
                // handled alongside key-code D-pad so generic controllers
                // and the internal RG DS pad both work.
                //
                // Special control keys (not forwarded to drastic):
                //   KEY_BACK    → long-press (3s) exits to XMB
                // All other buttons (including SELECT) are forwarded
                // to drastic as normal DS inputs.
                for (int fd : mInputFds) {
                    struct input_event ev;
                    while (read(fd, &ev, sizeof(ev)) == sizeof(ev)) {
                        if (ev.type == EV_KEY) {
                            const bool pressed = (ev.value != 0);
                            // BACK: short-press toggles overlay
                            // + handoff pause. Long-press (3s)
                            // exits to XMB.
                            if (ev.code == KEY_BACK) {
                                if (pressed && !backWasDown) {
                                    backPressStartMs =
                                            elapsedRealtime();
                                }
                                if (!pressed && backWasDown) {
                                    int64_t held = elapsedRealtime()
                                            - backPressStartMs;
                                    if (held < kNanoBackHoldMs) {
                                        handoffPaused = !handoffPaused;
                                        if (handoffPaused) {
                                            saturation = 1.0f;
                                            gradient = 0.0f;
                                        }
                                    }
                                    backPressStartMs = 0;
                                }
                                backWasDown = pressed;
                                continue;
                            }
                            // Gamepad buttons → DS bitmask. Nintendo
                            // face-layout mapping matches what libretro
                            // QR uses (evdev BTN_A/B/X/Y are xbox-style
                            // south/east/north/west — Nintendo layout
                            // swaps A<->B and X<->Y).
                            auto bit = [&](int mask) {
                                if (pressed) dsBtnMask |=  mask;
                                else         dsBtnMask &= ~mask;
                            };
                            switch (ev.code) {
                            // Label mapping matching NanoMenu XMB:
                            // BTN_SOUTH=A(confirm), BTN_EAST=B(back),
                            // BTN_NORTH=X, BTN_WEST=Y.
                            case BTN_SOUTH:   bit(DrasticRunner::kDsBtnA);     break;
                            case BTN_EAST:    bit(DrasticRunner::kDsBtnB);     break;
                            case BTN_NORTH:   bit(DrasticRunner::kDsBtnX);     break;
                            case BTN_WEST:    bit(DrasticRunner::kDsBtnY);     break;
                            case BTN_TL:
                            case KEY_L:       bit(DrasticRunner::kDsBtnL);     break;
                            case BTN_TR:
                            case KEY_R:       bit(DrasticRunner::kDsBtnR);     break;
                            case BTN_START:   bit(DrasticRunner::kDsBtnStart); break;
                            case BTN_SELECT:  bit(DrasticRunner::kDsBtnSelect); break;
                            case KEY_UP:      bit(DrasticRunner::kDsBtnUp);    break;
                            case KEY_DOWN:    bit(DrasticRunner::kDsBtnDown);  break;
                            case KEY_LEFT:    bit(DrasticRunner::kDsBtnLeft);  break;
                            case KEY_RIGHT:   bit(DrasticRunner::kDsBtnRight); break;
                            default: break;
                            }
                        } else if (ev.type == EV_ABS) {
                            // D-pad hat axes → discrete DS D-pad bits.
                            // Touchscreen ABS_X/Y is filtered out by
                            // the non-HAT code (we don't plumb touch
                            // into drastic in this phase).
                            if (ev.code == ABS_HAT0X) {
                                dsBtnMask &= ~(DrasticRunner::kDsBtnLeft |
                                               DrasticRunner::kDsBtnRight);
                                if (ev.value < 0) dsBtnMask |= DrasticRunner::kDsBtnLeft;
                                if (ev.value > 0) dsBtnMask |= DrasticRunner::kDsBtnRight;
                            } else if (ev.code == ABS_HAT0Y) {
                                dsBtnMask &= ~(DrasticRunner::kDsBtnUp |
                                               DrasticRunner::kDsBtnDown);
                                if (ev.value < 0) dsBtnMask |= DrasticRunner::kDsBtnUp;
                                if (ev.value > 0) dsBtnMask |= DrasticRunner::kDsBtnDown;
                            }
                        }
                    }
                    if (qrCancelled) break;
                }
                if (qrCancelled) break;

                // Long-press BACK (3s) exits to XMB. Checked every
                // frame so the exit fires promptly once the hold
                // threshold is crossed.
                if (backPressStartMs > 0) {
                    int64_t held = elapsedRealtime() - backPressStartMs;
                    if (held >= kNanoBackHoldMs) {
                        qrCancelled = true;
                        ALOGW("drastic QR: BACK held %lldms, "
                              "exiting to XMB", (long long)held);
                        break;
                    }
                }

                // Push the accumulated button state to drastic.
                // All buttons including SELECT go through as normal
                // DS inputs. Exit is via long-press BACK (3s).
                drastic->setInput(dsBtnMask);

                // GammaOS: Per-phase timing for the drastic QR render
                // loop. Only emits a log line when the overall frame
                // exceeded the 16.67 ms vblank budget, so it stays
                // quiet on the 97%+ of frames that hit vsync cleanly.
                // Used to pinpoint which phase is eating time during
                // the occasional microhitch.
                auto nowUs = []() {
                    struct timespec ts;
                    clock_gettime(CLOCK_MONOTONIC, &ts);
                    return (int64_t)ts.tv_sec * 1000000LL
                            + ts.tv_nsec / 1000LL;
                };
                const int64_t phaseT0 = nowUs();

                // Render both DS screens into the offscreen FBO via
                // drastic's renderFrame (hi-res 3D, all layers).
                // Returns immediately (no-op) until the DS producer
                // has generated its first frame.
                drastic->renderDsToOffscreen();
                const int64_t phaseT1 = nowUs();

                // GammaOS: Draw the "Quick Resuming... / ROM name"
                // overlay in real QR mode. In smoke mode (debug /
                // pipeline-profiling path) we suppress it -- no overlay
                // text and no fade gradient -- so the visible output
                // is exactly what drastic produced.
                const bool showOverlay = !smokeActive && !drasticNanoActive && !handoffPaused;

                // GammaOS: secondary always renders + flips. An earlier
                // half-rate optimization saved CPU by alternating
                // secondary work, but that aliasing meant some present
                // slots had no fresh secondary content and the bottom
                // screen flashed black. Once DRM PRIME landed the
                // per-iter secondary cost dropped (~1 ms instead of
                // ~5 ms with the CPU blit), so the savings weren't
                // worth the bug. If we want to halve secondary again
                // we need to render INTO the slots that will actually
                // be presented to secondary -- not every-other render
                // iter -- which requires syncing render parity to the
                // 3-iter present lag.
                bool secondaryThisIter = true;

                // GammaOS: Triple-buffer ring slot for this frame's render
                // pass. When qrUseTripleBuffer is on, rotates through the 3
                // slots so the present-side glFinish() inside drmFlipRingSlot
                // observes work submitted ~2 frames earlier -- almost always
                // complete, so the wait is microseconds instead of 7-17 ms
                // when Mali kbase housekeeping hits. When off, always slot 0
                // (exactly the classic single-buffered path).
                const int renderIdx = qrUseTripleBuffer ? sRingRenderIdx : 0;
                AhbRenderTarget& primTgt = sAhbRingPrimary[renderIdx];
                AhbRenderTarget& secTgt  = sAhbRingSecondary[renderIdx];

                // GammaOS: Each render pass draws drastic's full-viewport
                // blit quad (DrasticRunner::drawDsQuad uses NDC -1..+1
                // vertices, which still cover the entire viewport after
                // any 90/180/270 rotation). The previous glClearColor +
                // glClear before each drawDsQuad was therefore redundant
                // -- the blit overwrites every pixel. Removing the clear
                // cuts ~0.5-1 ms of GPU work per pass on RG DS, ~2-3 ms
                // on 1080p panels.
                if (hasDualDisplay) {
                    // Pass 1: secondary display -> bottom DS screen.
                    // Gated on secondaryThisIter so we do half the work
                    // on dual-display setups; secondary then runs at
                    // 30 fps which is fine for the bottom DS screen.
                    if (secondaryThisIter) {
                        glBindFramebuffer(GL_FRAMEBUFFER, secTgt.glFbo);
                        glViewport(0, 0, secTgt.w, secTgt.h);
                        drastic->renderBottomScreen(saturation, gradient);
                        if (showOverlay) {
                            // drawText hard-codes mWidth/mHeight for
                            // pixel->NDC (the logical landscape space;
                            // rotation handled by the text shader's
                            // uRotation uniform). Using AHB dims here
                            // would mis-project text on any device
                            // where primary AHB size != mWidth/mHeight
                            // (e.g. portrait panels pushed through a
                            // landscape logical surface, like RK3576
                            // 1080x1920).
                            drawOverlay(mWidth, mHeight);
                        }
                    }

                    // Pass 2: primary display -> top DS screen.
                    glBindFramebuffer(GL_FRAMEBUFFER, primTgt.glFbo);
                    if (sDrmGlRotation) {
                        glViewport(0, 0, primTgt.w, primTgt.h);
                    } else {
                        glViewport(0, 0, mWidth, mHeight);
                    }
                    drastic->renderTopScreen(saturation, gradient);
                } else {
                    // Single display: both screens stacked.
                    glBindFramebuffer(GL_FRAMEBUFFER, primTgt.glFbo);
                    if (sDrmGlRotation) {
                        glViewport(0, 0, primTgt.w, primTgt.h);
                    } else {
                        glViewport(0, 0, mWidth, mHeight);
                    }
                    drastic->renderBothScreens(saturation, gradient);
                }
                if (showOverlay) {
                    // drawText always uses mWidth/mHeight internally for
                    // pixel->NDC. Passing AHB dims here drops the text off
                    // the visible NDC region on any device where the AHB
                    // is panel-native (portrait) but the logical surface
                    // is landscape (RK3576 case: AHB 1080x1920, mWidth/H
                    // 1920x1080 -> text msgY=0.78*1920=1497 becomes NDC
                    // y=-1.77, clipped). The rotation matrix already maps
                    // mWidth/mHeight-space NDC to the panel via the text
                    // shader's uRotation uniform.
                    drawOverlay(mWidth, mHeight);
                }
                const int64_t phaseT2 = nowUs();

                if (qrUseTripleBuffer) {
                    // Unbind so subsequent state doesn't accidentally land
                    // on the AHB FBO. eglCreateSyncKHR with NATIVE_FENCE
                    // flushes implicitly, so we do NOT call glFlush --
                    // the fence object IS our kick-and-record.
                    glBindFramebuffer(GL_FRAMEBUFFER, 0);
                    // Record a per-slot EGL native fence. Signals when
                    // every GL command issued to this point (including
                    // this slot's AHB renders) has completed on the GPU.
                    // At present time -- 2 iters later, when this slot
                    // rotates back into presentIdx -- drmFlipRingSlot
                    // will dup this fence's fd and pass it to
                    // AHardwareBuffer_lock for a per-slot dma-fence wait.
                    // That sidesteps the global glFinish drain that was
                    // the original ring's bottleneck.
                    // ITER4 diagnostic: skip fence creation and rely on
                    // kernel implicit dma-fence sync. DRM page_flip is
                    // supposed to wait on the AHB dma-buf's implicit
                    // write fence before starting scanout, so the
                    // explicit EGL fence might be redundant on this
                    // stack. Gated behind a prop so we can A/B without
                    // re-flash.
                    static int sPrimeNoFence = -1;
                    if (sPrimeNoFence < 0) {
                        char p[PROPERTY_VALUE_MAX] = {};
                        property_get("persist.gammaos.nano.prime_no_fence",
                                     p, "0");
                        sPrimeNoFence = (p[0] == '1') ? 1 : 0;
                        ALOGW("NanoMenu QR: prime_no_fence=%d",
                              sPrimeNoFence);
                    }

                    if (sPrimeNoFence) {
                        // Skip fence. Just kick GPU commands into flight
                        // and let kernel handle sync via the dma-buf
                        // implicit fence.
                        glFlush();
                    } else if (sEglCreateSyncKHR && sRingEglDpy != EGL_NO_DISPLAY) {
                        // If a previous fence is still hanging around
                        // (e.g. lock failed earlier and didn't consume
                        // it), destroy it before overwriting.
                        if (sAhbRingSyncPrimary[renderIdx] != EGL_NO_SYNC_KHR
                            && sEglDestroySyncKHR) {
                            sEglDestroySyncKHR(sRingEglDpy,
                                    sAhbRingSyncPrimary[renderIdx]);
                        }
                        sAhbRingSyncPrimary[renderIdx] = sEglCreateSyncKHR(
                                sRingEglDpy,
                                EGL_SYNC_NATIVE_FENCE_ANDROID, nullptr);
                        if (sAhbRingSyncPrimary[renderIdx] == EGL_NO_SYNC_KHR) {
                            // Fence creation failed -- glFinish fallback
                            // in drmFlipRingSlot will kick in.
                            glFlush();
                        }
                    } else {
                        glFlush();
                    }
                    sRingRenderIdx = (renderIdx + 1) % AHB_RING_DEPTH;
                    // Bootstrap: first AHB_RING_DEPTH-1 iterations just
                    // render and don't present, so the ring gets primed
                    // with real content before we start rotating.
                    // Threshold = 2 (not depth-1) keeps present-lag at 2 regardless of
// ring depth. With depth N and lag L, slot M is rendered at iter M
// and re-rendered at iter M+N, but display still owns it through
// iter M+L+1. Race-free requires L <= N-2. With depth 4 and L=2
// (this threshold), we have 1 slot of headroom = no GL/scanout
// races on the DRM PRIME path.
if (sRingPrimedCount >= 2) {
                        const int presentIdx = sRingPresentIdx;
                        // skipNonPrimary mirrors the secondary render
                        // gating above: secondary is rendered AND
                        // flipped only on alternating iters, halving
                        // the dual-display work without leaving stale
                        // content (skipped iter just keeps the last
                        // good frame on screen).
                        drmFlipRingSlot(presentIdx, !secondaryThisIter);
                        sRingPresentIdx =
                                (presentIdx + 1) % AHB_RING_DEPTH;
                    } else {
                        sRingPrimedCount++;
                    }
                } else {
                    drmFrameEnd(mDisplay, mSurface);
                }
                const int64_t phaseT3 = nowUs();

                // Vsync: block until the primary display's next
                // vertical blank. Without this, the loop runs
                // unthrottled (~2ms/frame on Mali G52) and the DRM
                // page flip with flags=0 is fire-and-forget, causing
                // severe jitter/tearing at ~45fps effective. The
                // vblank wait gates the loop to exactly 60fps (or
                // whatever the panel refresh rate is).
                // Render-loop sync. Try DRM_IOCTL_WAIT_VBLANK first -- works
                // on every panel with periodic vblank interrupts (RG DS /
                // RK3568) and avoids the cross-CRTC event timing skew that
                // page-flip-event drain hits on dual-display setups. On
                // panels where the kernel's vblank queue never wakes
                // (RK3576 DSI command-mode), the first call hits the 3s
                // timeout, sDrmVblankBroken flips, and from then on
                // drmDrainPageFlipEvents() is the sync gate.
                //
                // The whole gate can be disabled at runtime with
                // persist.gammaos.nano.vsync=0 -- diagnostic only, lets us
                // measure vsync overhead vs other sources of jitter.
                if (sVsyncEnabled < 0) {
                    char vp[PROPERTY_VALUE_MAX] = {};
                    property_get("persist.gammaos.nano.vsync", vp, "1");
                    sVsyncEnabled = (vp[0] == '0') ? 0 : 1;
                    ALOGW("NanoMenu QR vsync gate %s",
                          sVsyncEnabled ? "ENABLED" : "DISABLED");
                }
                if (sVsyncEnabled) {
                    // Multi-CRTC setups (RG DS dual DSI) and broken-vblank
                    // panels pace via drmDrainPageFlipEvents instead of
                    // WAIT_VBLANK so the sync gate waits for flips on every
                    // display to complete. See drmFlipRingSlot for why.
                    if (sDrmFd >= 0 && !sDrmDisplays.empty() &&
                        !sDrmVblankBroken && sDrmDisplays.size() <= 1) {
                        int64_t vblT0 = systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL;
                        union drm_wait_vblank vbl = {};
                        vbl.request.type = (enum drm_vblank_seq_type)(
                                _DRM_VBLANK_RELATIVE
                                | ((sDrmPrimaryIdx & 0x1f)
                                   << _DRM_VBLANK_HIGH_CRTC_SHIFT));
                        vbl.request.sequence = 1;
                        ioctl(sDrmFd, DRM_IOCTL_WAIT_VBLANK, &vbl);
                        int64_t vblElapsed =
                                systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL - vblT0;
                        if (vblElapsed > 100000) {
                            sDrmVblankBroken = true;
                            ALOGW("NanoMenu QR: DRM_IOCTL_WAIT_VBLANK took %lld us "
                                  "-- switching to page-flip-event pacing",
                                  (long long)vblElapsed);
                        }
                    }
                    drmDrainPageFlipEvents();
                } else {
                    // Vsync disabled: cap loop at 60 fps via usleep so we
                    // compare CPU fairly. Tearing expected.
                    drmPaceWithoutVsync();
                }
                const int64_t phaseT4 = nowUs();

                // Log per-phase breakdown for every frame that takes
                // longer than one vblank. No rate limit -- we need to
                // see the full cadence of stutter events during 6s
                // jitter debugging.
                {
                    int64_t total = phaseT4 - phaseT0;
                    if (total > 17000) {
                        ALOGW("drastic QR slow frame: "
                              "total=%lldus | renderDs=%lldus "
                              "gl=%lldus flip=%lldus vblank=%lldus",
                              (long long)total,
                              (long long)(phaseT1 - phaseT0),
                              (long long)(phaseT2 - phaseT1),
                              (long long)(phaseT3 - phaseT2),
                              (long long)(phaseT4 - phaseT3));
                    }
                }

                // GammaOS: Per-second FPS counter for the drastic QR loop.
                // Mirrors the XMB FPS counter further down threadLoop.
                // Zero runtime overhead when handoff is imminent and the
                // loop is about to exit, so always on. Min/max frame
                // time (us) is reported alongside so we can see how
                // tight the vsync lock is.
                {
                    static int64_t sQrFpsWindowStartNs = 0;
                    static int sQrFpsFrames = 0;
                    static int64_t sQrFpsMinFrameUs = 0;
                    static int64_t sQrFpsMaxFrameUs = 0;
                    static int64_t sQrFpsLastFrameNs = 0;
                    int64_t nowNsFps;
                    {
                        struct timespec ts;
                        clock_gettime(CLOCK_MONOTONIC, &ts);
                        nowNsFps = (int64_t)ts.tv_sec * 1000000000LL
                                   + ts.tv_nsec;
                    }
                    if (sQrFpsLastFrameNs != 0) {
                        int64_t frameUs =
                                (nowNsFps - sQrFpsLastFrameNs) / 1000LL;
                        // Catch stalls that land BETWEEN phaseT4 of one
                        // iteration and phaseT0 of the next (outside the
                        // phase-timed region). Compares full iter-to-iter
                        // time to the 17ms vblank budget.
                        if (frameUs > 17500) {
                            ALOGW("drastic QR iter gap: frameUs=%lldus "
                                  "(interiter stall not in phase log)",
                                  (long long)frameUs);
                        }
                        sQrFpsFrames++;
                        if (sQrFpsFrames == 1 ||
                                frameUs < sQrFpsMinFrameUs) {
                            sQrFpsMinFrameUs = frameUs;
                        }
                        if (frameUs > sQrFpsMaxFrameUs) {
                            sQrFpsMaxFrameUs = frameUs;
                        }
                        if (sQrFpsWindowStartNs == 0) {
                            sQrFpsWindowStartNs = nowNsFps;
                        }
                        int64_t elapsedNs =
                                nowNsFps - sQrFpsWindowStartNs;
                        if (elapsedNs >= 1000000000LL) {
                            float fps = (float)sQrFpsFrames * 1e9f
                                        / (float)elapsedNs;
                            ALOGW("drastic QR FPS: %.1f "
                                  "(%d frames / %lld.%03lld s, "
                                  "min=%lldus max=%lldus)",
                                  fps, sQrFpsFrames,
                                  elapsedNs / 1000000000LL,
                                  (elapsedNs / 1000000LL) % 1000,
                                  sQrFpsMinFrameUs, sQrFpsMaxFrameUs);
                            sQrFpsWindowStartNs = nowNsFps;
                            sQrFpsFrames = 0;
                            sQrFpsMinFrameUs = 0;
                            sQrFpsMaxFrameUs = 0;
                        }
                    }
                    sQrFpsLastFrameNs = nowNsFps;
                }

                if (!firstFrameLogged) {
                    int64_t now = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
                    ALOGW("NanoMenu BOOT TIMING: drastic first frame at T+%lldms",
                          now);
                    firstFrameLogged = true;
                }

                // Check for handoff conditions
                char val[PROPERTY_VALUE_MAX] = {};
                bool ready = false;
                property_get("sys.gammaos.nano.home_launching", val, "");
                ready = (strcmp(val, "1") == 0);
                if (!ready) {
                    property_get("sys.boot_completed", val, "0");
                    ready = (strcmp(val, "1") == 0);
                }
                if (ready && !bootComplete) {
                    bootComplete = true;
                    bootCompleteTime = elapsedRealtime();
                    ALOGI("drastic QR: transitioning to full color");
                }

                if (bootComplete && !drasticNanoActive && !handoffPaused) {
                    // Fade from desaturated to full color over 800ms.
                    // Drastic nano skips this entirely (starts at full
                    // color, no transition period).
                    int64_t elapsed = elapsedRealtime() - bootCompleteTime;
                    float t = fminf((float)elapsed / 800.0f, 1.0f);
                    t = 1.0f - (1.0f - t) * (1.0f - t);
                    saturation = 0.35f + t * 0.65f;
                    gradient = 0.7f * (1.0f - t);
                    // When fade completes, hand off to the real
                    // drastic activity — identical pattern to
                    // LibretroRunner's handoff to RetroArch at line
                    // ~6645. The intent file was already written by
                    // launchXmbGame() when the user selected the NDS
                    // game; we just need to kick
                    // NanoRelaunchMonitor via do_launch so it reads
                    // the file and starts DraSticActivity.
                    //
                    // Smoke-test mode (qr_core != "drastic") never
                    // fires handoff -- the smoke test runs the DS
                    // indefinitely for interactive debugging.
                    //
                    if (t >= 1.0f && drasticQrHandoff && !handoffFired) {
                        // Gate handoff on the QR ROM's storage being
                        // ready. If the ROM lives on external SD, vold
                        // mounts the volume ~3-5s after boot starts,
                        // and the handoff content URI cannot resolve
                        // before then. Hold the handoff (keep rendering
                        // the loading screen) until the mount is up, or
                        // until handoffWaitTimeoutMs has elapsed as a
                        // safety net.
                        bool storageReady = isQrRomStorageReady();
                        if (!storageReady) {
                            if (handoffWaitStartMs == 0) {
                                handoffWaitStartMs = elapsedRealtime();
                            }
                            int64_t waited = elapsedRealtime() - handoffWaitStartMs;
                            if (!handoffStorageWaitLogged) {
                                ALOGI("drastic QR: handoff held -- "
                                      "waiting for QR ROM storage mount");
                                handoffStorageWaitLogged = true;
                            }
                            if (waited < handoffWaitTimeoutMs) {
                                // Skip the handoff this frame; the next
                                // frame will retry. Continue rendering
                                // the loading-style overlay so the user
                                // sees a steady screen.
                                continue;
                            }
                            ALOGW("drastic QR: storage wait timed out "
                                  "after %lldms -- firing handoff anyway",
                                  (long long)waited);
                        } else if (handoffStorageWaitLogged) {
                            int64_t waited = elapsedRealtime() - handoffWaitStartMs;
                            ALOGI("drastic QR: storage ready after %lldms wait",
                                  (long long)waited);
                        }
                        handoffFired = true;
                        ALOGI("drastic QR: handoff to com.dsemu.drastic");
                        // Recreate /data/system/nano_launch_intent.txt
                        // from the persistent copy written by
                        // launchXmbGame's drastic QR prime path. The
                        // original intent file is consumed and deleted
                        // by RootWindowContainer on first launch; the
                        // persistent stash is our source of truth for
                        // every subsequent QR-triggered launch. Without
                        // this, the framework falls back to the plain
                        // LAUNCHER intent and drastic opens on its own
                        // main menu instead of the primed game.
                        {
                            const char* src =
                                    "/data/system/nano_drastic_qr_intent.txt";
                            const char* dst =
                                    "/data/system/nano_launch_intent.txt";
                            int sfd = open(src, O_RDONLY);
                            if (sfd >= 0) {
                                char buf[4096];
                                ssize_t n = read(sfd, buf, sizeof(buf));
                                close(sfd);
                                if (n > 0) {
                                    int dfd = open(
                                            dst,
                                            O_WRONLY | O_CREAT | O_TRUNC,
                                            0666);
                                    if (dfd >= 0) {
                                        write(dfd, buf, (size_t)n);
                                        close(dfd);
                                        chmod(dst, 0644);
                                        ALOGI("drastic QR: restored intent "
                                              "file from QR stash (%zd bytes)",
                                              n);
                                    } else {
                                        ALOGW("drastic QR: failed to open "
                                              "%s for write: %s",
                                              dst, strerror(errno));
                                    }
                                } else {
                                    ALOGW("drastic QR: QR intent stash "
                                          "empty or unreadable");
                                }
                            } else {
                                ALOGW("drastic QR: no QR intent stash at "
                                      "%s, framework will fall back to "
                                      "LAUNCHER", src);
                            }
                        }
                        android::base::SetProperty(
                                "sys.gammaos.nano.launch_app",
                                "com.dsemu.drastic");
                        android::base::SetProperty(
                                "sys.gammaos.nano.launch_intent", "file");
                        android::base::SetProperty(
                                "sys.gammaos.nano.launch_rom", "");
                        android::base::SetProperty(
                                "sys.gammaos.nano.launch_core", "");
                        // mXmbSystems is empty under the fast path so
                        // we can't compute a precise return position;
                        // default to recently-played (sys=-1).
                        property_set(
                                "sys.gammaos.nano.xmb_return_sys", "-1");
                        property_set(
                                "sys.gammaos.nano.xmb_return_game", "0");
                        property_set(
                                "sys.gammaos.nano.return_recent", "0");
                        property_set(
                                "sys.gammaos.nano.drop_input", "1");
                        // Fire both the direct do_launch trigger and
                        // the legacy nano_retroarch property. The
                        // direct trigger is the primary mechanism;
                        // the legacy prop keeps init-driven
                        // side-effects firing (bootanim exit etc).
                        // Set handoff_fired first so the home-launch
                        // gate in startHomeOnTaskDisplayArea opens
                        // immediately; otherwise we race the init.rc
                        // action that sets bootanim.exit=1.
                        property_set(
                                "sys.gammaos.nano.handoff_fired", "1");
                        property_set(
                                "sys.gammaos.nano.pending_exit", "0");
                        property_set(
                                "sys.gammaos.nano.do_launch", "1");
                        property_set(
                                "service.bootanim.nano_retroarch", "1");
                        // DrasticRunner keeps running in the
                        // background until the process exits -- the
                        // real drastic activity will launch from a
                        // cold DS BIOS but resume via drastic's own
                        // autosave, which is identical UX to
                        // launching drastic through the framework.
                        mExitRequested = true;
                        break;
                    }
                } else if (!smokeActive && !drasticNanoActive && !handoffPaused) {
                    // Slow creep toward color while we're still in the
                    // pre-handoff "preview" period. Smoke/drastic-nano
                    // modes skip this -- saturation/gradient stay at
                    // 1.0/0.0 so the output is exactly what drastic
                    // produced (no fade overlay).
                    saturation = fminf(saturation + 0.0003f, 0.35f);
                    gradient = fmaxf(gradient - 0.0002f, 0.7f);
                }

            }

            // GammaOS: BACK long-press cancel flow. The user held BACK
            // during QR preview; we need to drop them to the NanoMenu
            // XMB, but readyToRun() skipped XMB shader/texture/system
            // init because sDrasticQrFastPath was active, so falling
            // through to the XMB render loop would crash on a null
            // program. The simplest safe path is to exit this nano
            // process and let init respawn it with qr_prepared=0 so
            // the next instance takes the normal (non-QR) startup.
            //
            // We can't just setprop sys.gammaos.nano.restart=1 before
            // exit: init's `start gammaos-nano` fires while we're
            // still alive and sees the service already running (no-op).
            // Instead spawn a backgrounded shell helper via system()
            // that waits ~500 ms (so init sees us fully exited) and
            // then sets the restart prop. The helper is reparented to
            // init on our exit, so it outlives the current process.
            if (qrCancelled) {
                if (drasticNanoActive) {
                    ALOGW("drastic nano: exit -- clearing QR state, "
                          "releasing DRM, restarting to XMB");
                } else {
                    ALOGI("drastic QR: cancel flow -- clearing QR, "
                          "asking init to respawn nano");
                }
                property_set("persist.gammaos.nano.qr_prepared", "0");
                property_set("persist.gammaos.nano.qr_core", "");
                // Clear drastic nano ROM path so stale paths don't
                // persist across sessions.
                setDrasticNanoRomPath("");
                // Clear force_drm so the restarted NanoMenu instance
                // does NOT grab DRM master post-boot. HWC/SF resume
                // compositing normally once DRM master is released by
                // our process exit.
                property_set("sys.gammaos.nano.force_drm", "0");
                // Don't call pauseDrastic() here -- drastic's
                // pauseSystem JNI entry tries to synchronize with
                // internal worker threads and deadlocks when called
                // from the render thread. The process is about to
                // exit anyway; drastic's threads will be killed by
                // the kernel on process teardown.
                //
                // Trigger a deferred restart via init.rc. The trigger
                // sleeps 1s (so init sees us fully exited) then sets
                // sys.gammaos.nano.restart=1. We can't use system()
                // or fork() here because drastic's worker threads
                // hold mutexes that deadlock the forked child.
                property_set(
                        "sys.gammaos.nano.restart_after_cancel", "1");
                // _exit() terminates the entire process immediately.
                // return false only kills the NanoMenu thread but
                // main() is stuck in IPCThreadState::joinThreadPool
                // which never returns, so the process stays alive
                // and init can't restart it.
                _exit(0);
            }

            ALOGI("NanoMenu: drastic QR loop exiting "
                  "(handoff=%d exitRequested=%d)",
                  handoffFired ? 1 : 0,
                  mExitRequested ? 1 : 0);
            // Intentionally fall through to the rest of threadLoop()
            // instead of returning false immediately. The
            // libretro-QR block below is guarded against
            // qr_core="drastic" (just logs and no-ops), and the main
            // XMB render loop at ~7049 is gated on !mExitRequested
            // (which we already set during handoff). By falling
            // through, control reaches the common cleanup path at
            // ~7325 which clears sys.gammaos.nano.menu_active=0 AND
            // runs the "Loading..." screen until the real drastic
            // activity binds. Both are critical:
            //   1) Clearing menu_active unblocks DualStackController
            //      so it can mirror drastic's window from the primary
            //      display to the secondary, matching the retroarch
            //      dualstack behavior. Without this, the secondary
            //      display stays frozen on NanoMenu's last QR-preview
            //      frame while drastic only draws to the primary.
            //   2) The loading-screen render loop keeps gammaos-nano
            //      painting both displays via DRM while the real
            //      drastic is coming up, so there is no visible gap
            //      between NanoMenu exit and drastic's first frame.
        } else {
            ALOGW("NanoMenu: drastic QR fast-path active but DrasticRunner "
                  "not initialized -- exiting to avoid shader crash");
            // The fast path skipped compiling particle/fx/XMB shaders,
            // so the normal render() would crash on glUseProgram(0).
            // Safer to exit and let init restart us.
            mExitRequested = true;
            return false;
        }
    }

    // Quick Resume: auto-launch into saved game on boot if prepared
    if (mQuickResumeEnabled) {
        std::string qrPrepared = android::base::GetProperty(
                "persist.gammaos.nano.qr_prepared", "0");
        if (qrPrepared == "1") {
            std::string qrRom = getQrRomPath();
            std::string qrCore = android::base::GetProperty(
                    "persist.gammaos.nano.qr_core", "");

            // If a prior NanoMenu instance already fired the handoff
            // (exited after setting do_launch=1), skip QR entirely.
            // Re-running QR after handoff confuses the relaunch
            // monitor and leaves RetroArch dead. Guard with a sys
            // property: auto-cleared on reboot, but survives the
            // NanoMenu respawn that init triggers mid-boot after
            // handoff. File-based flags are unreliable because the
            // graphics-uid NanoMenu can create them but not always
            // unlink across boots.
            {
                char hf[PROPERTY_VALUE_MAX] = {};
                property_get("sys.gammaos.nano.handoff_fired", hf, "0");
                if (strcmp(hf, "1") == 0) {
                    ALOGW("Quick Resume: handoff already fired "
                          "(handoff_fired=1), skipping QR");
                    qrRom.clear();
                    qrCore.clear();
                }
            }

            // GammaOS: Skip the libretro QR path for the drastic
            // sentinel. The drastic QR render loop above handles
            // qr_core="drastic" entirely (and returns false before
            // we reach here in the normal flow). This guard is a
            // safety net for races where sDrasticQrFastPath was not
            // set at readyToRun time but the prop is now "drastic".
            if (qrCore == "drastic") {
                ALOGW("Quick Resume: qr_core=drastic reached libretro "
                      "branch unexpectedly (sDrasticQrFastPath=%d) -- "
                      "skipping libretro launch", sDrasticQrFastPath ? 1 : 0);
            } else if (!qrRom.empty() && !qrCore.empty()) {
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
                        // Extract expected ROM basename from qrRom so we
                        // don't accidentally load a stale ROM left by a
                        // previous session (e.g. drastic nano caching an
                        // NDS ROM into the sibling drastic/rom/ dir while
                        // the retroarch rom/ dir still holds an old file).
                        std::string expectedBase;
                        {
                            size_t sl = qrRom.rfind('/');
                            expectedBase = (sl != std::string::npos)
                                    ? qrRom.substr(sl + 1) : qrRom;
                        }

                        DIR* d = opendir((cacheDir + "/rom").c_str());
                        if (d) {
                            struct dirent* e;
                            while ((e = readdir(d)) != nullptr) {
                                std::string name(e->d_name);
                                if (name == "." || name == "..") continue;
                                // ROM file (not .srm, .state, .sav, .png, .brm)
                                if (name.find(".srm") == std::string::npos &&
                                    name.find(".state") == std::string::npos &&
                                    name.find(".sav") == std::string::npos &&
                                    name.find(".brm") == std::string::npos &&
                                    name.find(".png") == std::string::npos) {
                                    romFile = cacheDir + "/rom/" + name;
                                }
                            }
                            closedir(d);
                        }

                        // Verify cached ROM matches QR prop. If the cache
                        // holds a different ROM (stale from a prior session
                        // or a different emulator), skip the native launch
                        // and let populate refresh the cache first.
                        if (!romFile.empty() && !expectedBase.empty()) {
                            std::string cachedBase = romFile;
                            size_t sl = cachedBase.rfind('/');
                            if (sl != std::string::npos)
                                cachedBase = cachedBase.substr(sl + 1);
                            if (cachedBase != expectedBase) {
                                ALOGW("Quick Resume: cached ROM mismatch "
                                      "(have=%s want=%s), skipping native "
                                      "launch until cache refreshes",
                                      cachedBase.c_str(),
                                      expectedBase.c_str());
                                romFile.clear();
                            }
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
                    bool backWasDown = false;
                    int64_t backPressStartMs = 0;
                    const int64_t kBackHoldMs = 3000;
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
                            int hotplugCounter = 0;
                            bool handoffPaused = false;

                            while (!exitPending() && !qrCancelled) {
                                // Check for new/swapped input devices every
                                // ~0.5s (30 frames at 60fps). gammapad
                                // recreates device nodes during boot --
                                // without this, we lose the fd and all
                                // button events stop working.
                                if (++hotplugCounter >= 30) {
                                    hotplugCounter = 0;
                                    checkInputHotplug();
                                }

                                // Poll input -- game is live, user can play.
                                // Long-press BACK (3s) exits to XMB.
                                // All buttons including SELECT are forwarded
                                // to the libretro core.
                                for (int fd : mInputFds) {
                                    struct input_event ev;
                                    while (read(fd, &ev, sizeof(ev)) == sizeof(ev)) {
                                        if (ev.type == EV_KEY) {
                                            bool pressed = (ev.value != 0);
                                            // BACK: short-press toggles
                                            // overlay + handoff pause.
                                            // Long-press (3s) exits to XMB.
                                            if (ev.code == KEY_BACK) {
                                                if (pressed && !backWasDown) {
                                                    backPressStartMs =
                                                            elapsedRealtime();
                                                }
                                                if (!pressed && backWasDown) {
                                                    int64_t held = elapsedRealtime()
                                                            - backPressStartMs;
                                                    if (held < kBackHoldMs) {
                                                        handoffPaused = !handoffPaused;
                                                        ALOGI("Quick Resume: handoff %s",
                                                              handoffPaused ? "paused" : "unpaused");
                                                        if (handoffPaused) {
                                                            saturation = 1.0f;
                                                            gradient = 0.0f;
                                                        }
                                                    }
                                                    backPressStartMs = 0;
                                                }
                                                backWasDown = pressed;
                                                continue;
                                            }
                                            switch (ev.code) {
                                            case BTN_A:      runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_A, pressed); break;
                                            case BTN_B:      runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_B, pressed); break;
                                            case BTN_X:      runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_X, pressed); break;
                                            case BTN_Y:      runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_Y, pressed); break;
                                            case BTN_TL:     runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_L, pressed); break;
                                            case BTN_TR:     runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_R, pressed); break;
                                            case BTN_TL2:    runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_L2, pressed); break;
                                            case BTN_TR2:    runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_R2, pressed); break;
                                            case BTN_START:  runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_START, pressed); break;
                                            case BTN_SELECT: runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_SELECT, pressed); break;
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

                                // Long-press BACK (3s) exits to XMB.
                                if (backPressStartMs > 0) {
                                    int64_t held = elapsedRealtime()
                                            - backPressStartMs;
                                    if (held >= kBackHoldMs) {
                                        qrCancelled = true;
                                        ALOGW("Quick Resume: BACK held "
                                              "%lldms, exiting to XMB",
                                              (long long)held);
                                        break;
                                    }
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
                                    } else if (!handoffPaused) {
                                        // Slow creep toward color during boot
                                        saturation = fminf(saturation + 0.0003f, 0.35f);
                                        gradient = fmaxf(gradient - 0.0002f, 0.7f);
                                    }
                                }

                                if (bootComplete && !handoffPaused) {
                                    // Ramp to full color over ~0.8s (ease-out)
                                    int64_t elapsed = elapsedRealtime() - bootCompleteTime;
                                    float t = fminf((float)elapsed / 800.0f, 1.0f);
                                    t = 1.0f - (1.0f - t) * (1.0f - t);
                                    saturation = 0.35f + t * 0.65f;
                                    gradient = 0.7f * (1.0f - t);

                                    if (t >= 1.0f && isQrRomStorageReady()) {
                                        // Fully saturated AND the ROM's backing
                                        // storage is mounted. On cold boot, vold
                                        // defers external SD mounting until after
                                        // keyguard, so /storage/<UUID>/ may not
                                        // exist yet. Without this gate, RetroArch
                                        // gets a ROM path it cannot open and hangs.
                                        // Fire do_launch FIRST so NanoRelaunchMonitor can start
                                        // the home activity in parallel while we save state.
                                        // This also bypasses the slow init property trigger
                                        // chain (service.bootanim.nano_retroarch → do_launch
                                        // via init.rc action, which can queue behind boot_completed
                                        // actions for several seconds).
                                        ALOGI("Quick Resume: handoff to RetroArch");
                                        // Mark handoff as done so respawned
                                        // NanoMenu instances skip QR. Uses
                                        // a sys property so it auto-clears
                                        // on reboot while surviving respawn.
                                        property_set("sys.gammaos.nano.handoff_fired", "1");
                                        // Clear pending_exit defensively.
                                        // If the user pressed BACK during QR
                                        // to pause, some code path still sets
                                        // pending_exit=1 and the relaunch
                                        // monitor would then take the
                                        // "immediate cleanup" branch --
                                        // force-stopping the preloaded
                                        // RetroArch and leaving a black
                                        // screen. Clearing it here makes the
                                        // handoff robust regardless of prior
                                        // pause/unpause state.
                                        property_set("sys.gammaos.nano.pending_exit", "0");
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

                                // Text overlay -- fades naturally as saturation
                                // approaches 1.0 (textAlpha -> 0). Game is
                                // always playable underneath.
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
                                    float nameScale = 1.5f * textScale;
                                    float nameW = measureText(romName.c_str(), nameScale);
                                    float nameX = (mWidth - nameW) / 2.0f;
                                    float nameY = msgY + FONT_CHAR_H * loadScale + 12.0f * textScale;
                                    drawText(romName.c_str(), nameX, nameY, nameScale,
                                             0.7f, 0.7f, 0.8f, textAlpha * 0.8f);
                                }
                                glDisable(GL_BLEND);

                                drmFrameEnd(mDisplay, mSurface);
                                // Vsync gate: match drastic QR pacing.
                                // DRM path uses vblank wait or page-flip
                                // drain; EGL path falls back to 16ms sleep.
                                if (sDrmActive) {
                                    if (sVsyncEnabled < 0) {
                                        char vp[PROPERTY_VALUE_MAX] = {};
                                        property_get("persist.gammaos.nano.vsync", vp, "1");
                                        sVsyncEnabled = (vp[0] == '0') ? 0 : 1;
                                    }
                                    if (sVsyncEnabled) {
                                        if (sDrmFd >= 0 && !sDrmDisplays.empty() &&
                                            !sDrmVblankBroken && sDrmDisplays.size() <= 1) {
                                            union drm_wait_vblank vbl = {};
                                            vbl.request.type = (enum drm_vblank_seq_type)(
                                                    _DRM_VBLANK_RELATIVE
                                                    | ((sDrmPrimaryIdx & 0x1f)
                                                       << _DRM_VBLANK_HIGH_CRTC_SHIFT));
                                            vbl.request.sequence = 1;
                                            int64_t vblT0 = systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL;
                                            ioctl(sDrmFd, DRM_IOCTL_WAIT_VBLANK, &vbl);
                                            int64_t vblElapsed =
                                                    systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL - vblT0;
                                            if (vblElapsed > 100000) {
                                                sDrmVblankBroken = true;
                                            }
                                        }
                                        drmDrainPageFlipEvents();
                                    } else {
                                        drmPaceWithoutVsync();
                                    }
                                } else {
                                    usleep(16666);
                                }
                            }

                            if (qrCancelled) {
                                ALOGI("Quick Resume: cancelled by BACK hold, "
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
    int64_t bootCompletedDetectedMs = 0;
    while (!exitPending() && !mExitRequested) {
        // GammaOS: Drastic Nano cache-wait + restart. When
        // launchXmbGame() sets mDrasticNanoPending, show
        // "Preparing..." while polling cache_ready. Once the
        // drastic cache is populated, set force_drm=1 and restart
        // NanoMenu into the QR fast path with DRM-direct rendering.
        // DrasticRunner dlopen is one-shot per process lifetime, so
        // we must restart rather than re-init in the same process.
        if (mDrasticNanoPending) {
            // Restart immediately into QR fast path. populate_drastic
            // runs asynchronously via init; the restarted NanoMenu's
            // runDrasticInitIfNeeded() will wait for the cache (ROM
            // file access check) on its own. No need to block here.
            ALOGW("drastic nano: restarting into QR fast path "
                  "(cache populates in background)");
            property_set("sys.gammaos.nano.force_drm", "1");
            property_set(
                    "sys.gammaos.nano.restart_after_cancel", "1");
            _exit(0);
        }

        // Apply stock clocks once boot is fully complete, with a 1 s
        // margin for PerformanceTile to re-sync performance_mode.
        //
        // Was an inline `usleep(1000000) + system("setclock_stock.sh")`.
        // The usleep parked the render thread for a full second right at
        // boot_completed, and the subsequent system() fork+exec stalled
        // it for another ~50-200 ms. Both showed up in the XMB FPS log
        // as a massive drop to single-digit fps for a 1-2 s window.
        //
        // Replaced with a deferred, non-blocking pattern: record the
        // time when boot_completed was first observed, then when 1 s has
        // passed issue the script run in the background so the fork+exec
        // does not block the render thread.
        if (!stockClocksApplied) {
            if (bootCompletedDetectedMs == 0) {
                char bootDone[PROPERTY_VALUE_MAX] = {};
                property_get("sys.boot_completed", bootDone, "0");
                if (!strcmp(bootDone, "1")) {
                    bootCompletedDetectedMs = elapsedRealtime();
                }
            } else if (elapsedRealtime() - bootCompletedDetectedMs
                       >= 1000) {
                system("/vendor/bin/setclock_stock.sh &");
                stockClocksApplied = true;
                ALOGD("NanoMenu: spawned setclock_stock.sh "
                      "(background) after boot_completed + 1 s");
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

    // GammaOS: Hand displays to SurfaceFlinger now that XMB is done.
    //
    // We kept DRM-direct rendering active for the entire XMB loop to
    // avoid the HWC compositor tick on every frame. Now that the user
    // has committed to launching an app (mExitRequested is set via
    // handleSelect/launchXmbGame/etc.), SurfaceFlinger needs to be the
    // DRM master so it can composite the app's window. drmStop()
    // releases the DRM resources nano was holding, and
    // setupSecondaryEglSurfaces() then reclaims wallpaper ownership on
    // the secondary display(s) so the bootanim logo does not linger
    // there while the app is loading.
    //
    // We only do this when mExitRequested is set -- that way the
    // bootanim.exit "die quietly" path at the start of threadLoop also
    // exits cleanly without disturbing DRM state.
    if (mExitRequested && sDrmActive) {
        drmStop();
        setupSecondaryEglSurfaces();
    }

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
    // (backtrace: ~SurfaceControl -> ~BBQSurface -> ~BLASTBufferQueue ->
    //  ~GraphicBuffer -> freeBuffer -> RegisteredHandlePool::remove).
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
