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
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/capability.h>
#include <linux/capability.h>
#include <grp.h>
#include <math.h>
#include <selinux/selinux.h>
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

// DRM direct framebuffer for early boot splash
#include <drm.h>
#include <drm_mode.h>

#include "NanoBridge.h"
#include <drm_fourcc.h>
#include <sys/mman.h>
#include <poll.h>
#include <android/hardware_buffer.h>
#include <vndk/hardware_buffer.h>  // AHardwareBuffer_getNativeHandle
#include <cutils/native_handle.h>  // native_handle_t layout
#include <drm_fourcc.h>            // DRM_FORMAT_ABGR8888

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
     // -a android.intent.action.VIEW is required: drastic's DraSticActivity
     // has two intent filters, a MAIN/LAUNCHER one (its main menu) and a
     // VIEW one (scheme=file|content, path=*.nds, which loads the ROM).
     // Without -a, Android defaults to ACTION_MAIN and drops into the main
     // menu instead of auto-loading the content URI. PPSSPP and Flycast
     // both use the same -a android.intent.action.VIEW pattern.
     "-n com.dsemu.drastic/.DraSticActivity -a android.intent.action.VIEW -d {file.uri} --activity-clear-task --activity-clear-top",
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

// GammaOS: path props routinely exceed PROP_VALUE_MAX (92 bytes) when
// ROMs live on external SD at /storage/<UUID>/..., so the prop set
// silently fails. Mirror the path into a plain text file alongside so
// readers can fall back when the prop is empty. Empty values unlink
// the file so a stale path can never resurrect in the fallback read.
static void writePathFile(const char* path, const std::string& value) {
    if (value.empty()) {
        unlink(path);
        return;
    }
    int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC, 0666);
    if (fd >= 0) {
        write(fd, value.c_str(), value.size());
        close(fd);
        chmod(path, 0644);
    }
}
static std::string readPathFile(const char* path) {
    int fd = open(path, O_RDONLY);
    if (fd < 0) return std::string();
    char buf[4096];
    ssize_t n = read(fd, buf, sizeof(buf));
    close(fd);
    if (n <= 0) return std::string();
    std::string s(buf, (size_t)n);
    // Strip trailing whitespace/newlines to tolerate shell-written files.
    while (!s.empty() && (s.back() == '\n' || s.back() == '\r' ||
                          s.back() == ' '  || s.back() == '\t')) {
        s.pop_back();
    }
    return s;
}
static void setLaunchRomPath(const std::string& romPath) {
    android::base::SetProperty("sys.gammaos.nano.launch_rom", romPath);
    writePathFile("/data/system/nano_launch_rom.txt", romPath);
}
static void setQrRomPath(const std::string& romPath) {
    android::base::SetProperty("persist.gammaos.nano.qr_rom", romPath);
    writePathFile("/data/system/nano_qr_rom.txt", romPath);
}
static std::string getQrRomPath() {
    // Prefer the file over the persist prop. External SD paths
    // (e.g. /storage/<UUID>/nds/<long name>.nds) routinely exceed
    // PROP_VALUE_MAX (92 bytes) and the prop write silently fails,
    // leaving a stale short path from a previous session. The file
    // is always written regardless of length, so it's the source of
    // truth. Fall back to the prop only when the file is missing
    // (e.g. first boot, or the file was manually deleted).
    std::string p = readPathFile("/data/system/nano_qr_rom.txt");
    if (!p.empty()) return p;
    return android::base::GetProperty(
            "persist.gammaos.nano.qr_rom", "");
}

// GammaOS: returns true when the QR ROM's backing storage is mounted.
//
// On cold boot vold defers external SD scanning until after the secure
// keyguard step, so /mnt/media_rw/<UUID>/ doesn't exist for ~3-5
// seconds after gammaos-nano comes up. The drastic QR handoff fires a
// content:// URI pointing at that volume, and if the ContentProvider
// can't resolve the file (because vold hasn't mounted it yet), drastic
// silently falls back to its main menu instead of routing to
// DraSticEmuActivity. By blocking the handoff until the raw vold mount
// point exists we guarantee the URI is resolvable when drastic looks
// it up. ROMs on /sdcard or /data/media/0 don't need this gate -- they
// live on /data which is up before NanoMenu starts.
static bool isQrRomStorageReady() {
    std::string qrRom = getQrRomPath();
    if (qrRom.empty()) return true;
    if (qrRom.find("/storage/") != 0) return true;
    std::string rest = qrRom.substr(9); // skip "/storage/"
    size_t slash = rest.find('/');
    if (slash == std::string::npos) return true;
    std::string uuid = rest.substr(0, slash);
    if (uuid == "emulated" || uuid == "self") return true;
    std::string rawDir = "/mnt/media_rw/" + uuid;
    struct stat st;
    if (stat(rawDir.c_str(), &st) != 0) return false;
    return S_ISDIR(st.st_mode);
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

// Set by the nano-shim handoff branch right before it issues
// `ctl.stop surfaceflinger`. When true, binderDied below treats
// the SF death as intentional and does NOT kill nano -- nano needs
// to keep running to service the NanoBridge socket for the shim
// process.
static std::atomic<bool> sShimHandoffActive{false};

void NanoMenu::binderDied(const wp<IBinder>&) {
    if (sShimHandoffActive.load()) {
        ALOGI("SurfaceFlinger died but nano-shim handoff is "
              "active; staying alive to service NanoBridge");
        return;
    }
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
// Set true when DRM PRIME succeeded for at least slot 0 primary; tells
// drmStop and any re-init code that sDrmRotMat has the Y-flip baked in
// (so we don't accidentally flip twice on a re-setup).
static bool sDrmYFlipForPrime = false;
// On some panels (observed: Anbernic RK3576 / RGVITA, DSI command-mode) the
// kernel's DRM_IOCTL_WAIT_VBLANK never fires because the driver doesn't
// generate vblank interrupts unless a DRM_MODE_PAGE_FLIP_EVENT was requested
// on the prior flip. The ioctl then hits the kernel's 3-second wait timeout
// and returns EIO on every call, pinning the render loop to ~0.3 fps. The
// first slow wait flips this flag so subsequent iterations skip the ioctl
// entirely. To keep the render loop vsync-locked (no tearing) without the
// vblank gate, every page flip now requests DRM_MODE_PAGE_FLIP_EVENT and
// the render thread drains those events from the DRM fd between frames.
// That gives per-flip completion signals that work on video-mode AND
// command-mode panels -- no tearing, proper 60 fps cap at the panel's
// native refresh rate.
static bool sDrmVblankBroken = false;
// Count of page flip events that have been requested but not yet drained.
// Incremented on successful DRM_IOCTL_MODE_PAGE_FLIP submission, decremented
// as events arrive on the DRM fd. Always kept in balance between iterations
// so the render loop stays sync'd to the panel.
static int sPendingFlipEvents = 0;
// Per-CRTC pending flip count. Needed on multi-CRTC setups to prevent stale
// DRM_EVENT_FLIP_COMPLETE events from draining pending counts for flips that
// haven't actually landed yet. A CRTC that EBUSYs stays "pending" until the
// event with a matching crtc_id arrives. Without per-CRTC tracking we saw
// EBUSY storms (~200/min) after panel retiming on RG DS -- the events
// decrement but the flips themselves were still in flight, so the next
// iteration submitted too early.
static constexpr int kMaxCrtcTrack = 8;
static uint32_t sCrtcIds[kMaxCrtcTrack] = {0};
static int sCrtcPending[kMaxCrtcTrack] = {0};
static int sCrtcTrackCount = 0;

static int drmCrtcSlot(uint32_t crtcId) {
    for (int i = 0; i < sCrtcTrackCount; i++) {
        if (sCrtcIds[i] == crtcId) return i;
    }
    if (sCrtcTrackCount < kMaxCrtcTrack) {
        sCrtcIds[sCrtcTrackCount] = crtcId;
        return sCrtcTrackCount++;
    }
    return -1;
}
// Runtime kill-switch for the entire vsync gate. Read once per process from
// persist.gammaos.nano.vsync (default 1). When 0, both render loops skip
// DRM_IOCTL_WAIT_VBLANK and drmDrainPageFlipEvents and instead pace via
// usleep so we still cap at 60 fps -- just without the DRM-side gating.
// Lets us isolate vsync overhead (DRM ioctls, kbase event queue interaction)
// from the rendering work itself. Will tear because page flips no longer
// align with panel scanout.
static int sVsyncEnabled = -1;
// Helper: when vsync is disabled, cap iter rate at 60 fps via usleep on a
// monotonic deadline. Same call site as the vsync gate so the loop runs at
// the same effective rate -- only the pacing source changes.
static void drmPaceWithoutVsync() {
    static int64_t sNextDeadlineUs = 0;
    constexpr int64_t kFrameUs = 16667;  // ~60 Hz
    int64_t nowUs = systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL;
    if (sNextDeadlineUs == 0) {
        sNextDeadlineUs = nowUs + kFrameUs;
        return;
    }
    if (nowUs < sNextDeadlineUs) {
        usleep((useconds_t)(sNextDeadlineUs - nowUs));
        nowUs = systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL;
    }
    // If we were already past the deadline (slow frame), reset to "now" so
    // we don't try to catch up by submitting back-to-back -- just keep
    // pacing at 60 fps from the current moment.
    if (nowUs > sNextDeadlineUs + kFrameUs) {
        sNextDeadlineUs = nowUs + kFrameUs;
    } else {
        sNextDeadlineUs += kFrameUs;
    }
}

// GammaOS: Drastic QR fast-path flag. When persist.gammaos.nano.drastic_smoke=1,
// NanoMenu skips its normal init (particle/fx/XMB shader compiles, ROM
// scanning, icon textures) and enters a dedicated drastic render loop at
// the top of threadLoop. This matches the libretro QR architecture and
// cuts ~700ms+ off the boot-to-first-frame time.
static bool sDrasticQrFastPath = false;
// GL rotation matrix (column-major for GLES2 uniformMatrix2fv)
static float sDrmRotMat[4] = {1.0f, 0.0f, 0.0f, 1.0f}; // identity

// EGL extensions for zero-copy path
static PFNEGLCREATEIMAGEKHRPROC sEglCreateImageKHR = nullptr;
static PFNEGLDESTROYIMAGEKHRPROC sEglDestroyImageKHR = nullptr;
static PFNGLEGLIMAGETARGETTEXTURE2DOESPROC sGlEGLImageTargetTexture2DOES = nullptr;
typedef EGLClientBuffer (EGLAPIENTRYP PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC) (const struct AHardwareBuffer *buffer);
static PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC sEglGetNativeClientBufferANDROID = nullptr;

// EGL_ANDROID_native_fence_sync extension for per-slot GPU→CPU sync. Used
// by the triple-buffer ring: instead of a global glFinish() inside
// drmFlipRingSlot (which drains the ENTIRE shared Mali kbase queue),
// we insert a native fence after each slot's render and pass that fence
// fd to AHardwareBuffer_lock at present time. The lock waits on ONLY
// that slot's dma-fence -- no cross-slot coupling -- so Mali kbase
// housekeeping stalls that affect IN-FLIGHT slots (N-1, N) cannot
// delay the wait for an already-complete older slot (N-2).
#ifndef EGL_SYNC_NATIVE_FENCE_ANDROID
#define EGL_SYNC_NATIVE_FENCE_ANDROID 0x3144
#endif
#ifndef EGL_NO_NATIVE_FENCE_FD_ANDROID
#define EGL_NO_NATIVE_FENCE_FD_ANDROID -1
#endif
typedef EGLSyncKHR (EGLAPIENTRYP PFNEGLCREATESYNCKHRPROC_LOCAL)(EGLDisplay, EGLenum, const EGLint*);
typedef EGLBoolean (EGLAPIENTRYP PFNEGLDESTROYSYNCKHRPROC_LOCAL)(EGLDisplay, EGLSyncKHR);
typedef EGLint (EGLAPIENTRYP PFNEGLCLIENTWAITSYNCKHRPROC_LOCAL)(EGLDisplay, EGLSyncKHR, EGLint, EGLTimeKHR);
typedef EGLint (EGLAPIENTRYP PFNEGLDUPNATIVEFENCEFDANDROIDPROC_LOCAL)(EGLDisplay, EGLSyncKHR);
static PFNEGLCREATESYNCKHRPROC_LOCAL sEglCreateSyncKHR = nullptr;
static PFNEGLDESTROYSYNCKHRPROC_LOCAL sEglDestroySyncKHR = nullptr;
static PFNEGLCLIENTWAITSYNCKHRPROC_LOCAL sEglClientWaitSyncKHR = nullptr;
static PFNEGLDUPNATIVEFENCEFDANDROIDPROC_LOCAL sEglDupNativeFenceFDANDROID = nullptr;
// Stashed EGLDisplay for the ring's fence lifecycle. Set in drmSetupZeroCopy.
static EGLDisplay sRingEglDpy = EGL_NO_DISPLAY;

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
    // DRM PRIME zero-copy: when non-zero, the AHB's underlying dma-buf was
    // imported into DRM as a scanout framebuffer (DRM_FORMAT_ABGR8888 since
    // the AHB is R8G8B8A8_UNORM in memory). drmFlipRingSlot can then page
    // flip directly to this fb_id, skipping the AHB-CPU-lock + memcpy +
    // DRM-dumb-buffer path entirely (~2-3 ms CPU saved per iter per
    // display on RG DS, ~5-8 ms on 1080p panels).
    uint32_t drmFbId;
    uint32_t drmGemHandle;
};
// Triple-buffered AHB ring for drastic QR preview. The 5-7 s Mali kbase
// housekeeping cadence stalls glFinish() in drmFlipAll() for 7-17 ms,
// manifesting as visible frame drops on the same cadence. Presenting a
// slot whose GPU work was submitted ~2 frames ago moves glFinish off the
// critical path: by the time we want to present, the work is already
// complete, so glFinish returns in microseconds. See
// docs/triple-buffer-plan.md for the full rationale and implementation
// staging. Slot 0 is also used by the XMB single-buffered path (unchanged),
// so sAhbTarget/sAhbTargetSecondary below are just compat references to
// ring slot 0 -- every caller that names them keeps working unchanged.
// Ring depth = 4 so we always have one slot the display is NOT actively
// scanning out. With present-lag-of-2 (sRingPresentIdx = renderIdx - 2),
// renderIdx and presentIdx differ by 2. With depth 3, renderIdx wraps
// every 3 iters and "renderIdx - presentIdx = 2 mod 3" still leaves the
// slot we render into being the slot display owns -- causes tearing on
// the DRM PRIME path where GL writes directly to the scanout buffer.
// Depth 4 makes "renderIdx - presentIdx + 1 = 3 mod 4 = 3", so the slot
// we render is always 3 ahead of what display owns, no race.
static constexpr int AHB_RING_DEPTH = 4;
static AhbRenderTarget sAhbRingPrimary[AHB_RING_DEPTH] = {};
static AhbRenderTarget sAhbRingSecondary[AHB_RING_DEPTH] = {};
// Per-slot EGL native fences. Inserted into the GL stream AFTER all draws
// for a slot complete (glFlush is implied by eglCreateSyncKHR). Consumed
// at present time: we dup the fence fd and pass it to AHardwareBuffer_lock
// which waits on just that slot's dma-fence -- per-slot scoped wait, not
// the global glFinish drain the ring first used (which was an own-goal on
// this stack because glFinish blocks on ALL in-flight GPU work).
static EGLSyncKHR sAhbRingSyncPrimary[AHB_RING_DEPTH] = {
    EGL_NO_SYNC_KHR, EGL_NO_SYNC_KHR, EGL_NO_SYNC_KHR, EGL_NO_SYNC_KHR
};
// Render-thread-only ring state (no cross-thread sharing).
// renderIdx = slot we will render into on the next iteration.
// presentIdx = slot we will present on the next iteration (valid after priming).
// primedCount = bootstrap counter; presents begin once it reaches AHB_RING_DEPTH-1.
static int sRingRenderIdx = 0;
static int sRingPresentIdx = 0;
static int sRingPrimedCount = 0;

// Compat macros: existing code refers to sAhbTarget / sAhbTargetSecondary
// and used to be the SINGLE target before the ring landed. These now resolve
// to the currently-active ring slot (sRingRenderIdx) so every reader
// automatically follows the ring cursor without touching the ~40 call sites.
// When ring mode is disabled sRingRenderIdx stays 0, giving identical
// behavior to the pre-ring single-buffer world. Macros (not references)
// because references are bound once at init and can't follow a changing
// index.
#define sAhbTarget           (sAhbRingPrimary[sRingRenderIdx])
#define sAhbTargetSecondary  (sAhbRingSecondary[sRingRenderIdx])

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
    // CPU_READ_OFTEN is required on RK3568 Mali gralloc for the AHB
    // to be allocated LINEAR (ABGR8888). Removing it picks an AFBC
    // compressed / tiled layout and DRM_IOCTL_MODE_ADDFB2 rejects
    // with EINVAL, breaking PRIME. COMPOSER_OVERLAY is a hint that
    // the buffer is for direct composition -- some gralloc impls
    // use it to pick a DRM-scanout-friendly (coherent, linear)
    // layout. Kept alongside CPU_READ so the blit fallback path
    // still has a CPU mapping if PRIME ever fails at runtime.
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_FRAMEBUFFER |
                 AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE |
                 AHARDWAREBUFFER_USAGE_COMPOSER_OVERLAY |
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

    // Best-effort DRM PRIME import: try to make this AHB directly
    // scanout-able by the DRM panel, so drmFlipRingSlot can page flip
    // straight to it instead of CPU-blit'ing AHB->dumb buffer.
    //
    // Sequence: native_handle's first fd is the dma-buf for the AHB's
    // backing store -> PRIME_FD_TO_HANDLE in our DRM context returns
    // a GEM handle -> ADDFB2 with DRM_FORMAT_ABGR8888 (matches AHB's
    // R8G8B8A8 memory order) gives us a fb_id.
    //
    // If any step fails (driver doesn't accept foreign dma-buf imports,
    // or the AHB has a non-trivial gralloc layout), we leave drmFbId=0
    // and the flip path falls back to the blit path. So this is a
    // no-risk experiment.
    target->drmFbId = 0;
    target->drmGemHandle = 0;
    if (sDrmFd >= 0) {
        const native_handle_t* nh = AHardwareBuffer_getNativeHandle(target->ahb);
        if (nh && nh->numFds > 0) {
            int dmabufFd = nh->data[0];
            // Pull the AHB's stride (in pixels) for fb pitch.
            AHardwareBuffer_Desc d = {};
            AHardwareBuffer_describe(target->ahb, &d);
            uint32_t pitch = d.stride * 4;  // R8G8B8A8 = 4 bytes/px

            struct drm_prime_handle ph = {};
            ph.fd = dmabufFd;
            ph.flags = 0;
            ph.handle = 0;
            if (ioctl(sDrmFd, DRM_IOCTL_PRIME_FD_TO_HANDLE, &ph) == 0
                && ph.handle != 0) {
                struct drm_mode_fb_cmd2 cmd = {};
                cmd.width = w;
                cmd.height = h;
                cmd.pixel_format = DRM_FORMAT_ABGR8888;
                cmd.flags = 0;
                cmd.handles[0] = ph.handle;
                cmd.pitches[0] = pitch;
                cmd.offsets[0] = 0;
                if (ioctl(sDrmFd, DRM_IOCTL_MODE_ADDFB2, &cmd) == 0
                    && cmd.fb_id != 0) {
                    target->drmFbId = cmd.fb_id;
                    target->drmGemHandle = ph.handle;
                    ALOGW("NanoMenu DRM PRIME: AHB(%s) imported as fb_id=%u "
                          "(gem=%u dmabuf_fd=%d pitch=%u)",
                          label, target->drmFbId, target->drmGemHandle,
                          dmabufFd, pitch);
                } else {
                    ALOGW("NanoMenu DRM PRIME: ADDFB2 failed for AHB(%s) "
                          "(errno=%d) -- will fall back to blit path",
                          label, errno);
                    // GEM handle leaks slightly; close via GEM_CLOSE
                    struct drm_gem_close gc = {};
                    gc.handle = ph.handle;
                    ioctl(sDrmFd, DRM_IOCTL_GEM_CLOSE, &gc);
                }
            } else {
                ALOGW("NanoMenu DRM PRIME: PRIME_FD_TO_HANDLE failed for "
                      "AHB(%s) (errno=%d) -- will fall back to blit path",
                      label, errno);
            }
        }
    }

    ALOGW("NanoMenu DRM zero-copy: AHB(%s) ENABLED — fbo=%u tex=%u (%ux%u) drmFb=%u",
          label, target->glFbo, target->glTexture, w, h, target->drmFbId);
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
    // EGL_ANDROID_native_fence_sync: used by the triple-buffer ring to
    // pass a per-slot dma-fence fd to AHardwareBuffer_lock so the wait
    // is scoped to ONE slot's GPU work, not the whole kbase queue.
    sEglCreateSyncKHR = (PFNEGLCREATESYNCKHRPROC_LOCAL)
            eglGetProcAddress("eglCreateSyncKHR");
    sEglDestroySyncKHR = (PFNEGLDESTROYSYNCKHRPROC_LOCAL)
            eglGetProcAddress("eglDestroySyncKHR");
    sEglClientWaitSyncKHR = (PFNEGLCLIENTWAITSYNCKHRPROC_LOCAL)
            eglGetProcAddress("eglClientWaitSyncKHR");
    sEglDupNativeFenceFDANDROID = (PFNEGLDUPNATIVEFENCEFDANDROIDPROC_LOCAL)
            eglGetProcAddress("eglDupNativeFenceFDANDROID");
    sRingEglDpy = eglDpy;

    if (!sEglCreateImageKHR || !sGlEGLImageTargetTexture2DOES || !sEglGetNativeClientBufferANDROID) {
        ALOGW("NanoMenu DRM zero-copy: EGL/GL ext functions not available");
        return;
    }
    const bool haveFenceSync = sEglCreateSyncKHR && sEglDestroySyncKHR &&
                               sEglDupNativeFenceFDANDROID;
    ALOGW("NanoMenu DRM zero-copy: EGL native fence sync %s",
          haveFenceSync ? "ENABLED" : "NOT AVAILABLE (ring will fall back to glFinish)");

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

    // Allocate all AHB_RING_DEPTH primary slots. Slot 0 is the "classic"
    // single-buffered target used by XMB and the default QR path;
    // slots 1..2 only get used when the QR loop opts into the triple-
    // buffer ring path (Step 4). We pay the +30 MB up-front so the ring
    // can be enabled/disabled at runtime via a prop without reallocating.
    for (int i = 0; i < AHB_RING_DEPTH; i++) {
        char label[32];
        snprintf(label, sizeof(label), "primary[%d]", i);
        if (!drmAllocAhbTarget(eglDpy, primaryW, primaryH,
                               &sAhbRingPrimary[i], label)) {
            // Release any previously-allocated slots and bail. We stay
            // in the pre-AHB path (readback via glReadPixels) because
            // the ring has to be "all or nothing" -- a partially-allocated
            // ring would corrupt any attempt to advance renderIdx.
            for (int j = 0; j < i; j++) {
                if (sAhbRingPrimary[j].glFbo) {
                    glDeleteFramebuffers(1, &sAhbRingPrimary[j].glFbo);
                }
                if (sAhbRingPrimary[j].glTexture) {
                    glDeleteTextures(1, &sAhbRingPrimary[j].glTexture);
                }
                if (sAhbRingPrimary[j].eglImage != EGL_NO_IMAGE_KHR) {
                    sEglDestroyImageKHR(eglDpy, sAhbRingPrimary[j].eglImage);
                }
                if (sAhbRingPrimary[j].ahb) {
                    AHardwareBuffer_release(sAhbRingPrimary[j].ahb);
                }
                sAhbRingPrimary[j] = {};
            }
            return;
        }
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
            for (int i = 0; i < AHB_RING_DEPTH; i++) {
                char label[48];
                snprintf(label, sizeof(label), "secondary-wallpaper[%d]", i);
                if (!drmAllocAhbTarget(eglDpy, secW, secH,
                                       &sAhbRingSecondary[i], label)) {
                    // Partial secondary allocation. Roll back so either
                    // all secondary slots exist or none do -- matches the
                    // primary-ring policy. Secondary displays then mirror
                    // the primary AHB (same as single-display fallback).
                    ALOGW("NanoMenu DRM: secondary AHB slot %d alloc failed, "
                          "rolling back to mirror", i);
                    for (int j = 0; j < i; j++) {
                        if (sAhbRingSecondary[j].glFbo) {
                            glDeleteFramebuffers(1, &sAhbRingSecondary[j].glFbo);
                        }
                        if (sAhbRingSecondary[j].glTexture) {
                            glDeleteTextures(1, &sAhbRingSecondary[j].glTexture);
                        }
                        if (sAhbRingSecondary[j].eglImage != EGL_NO_IMAGE_KHR) {
                            sEglDestroyImageKHR(eglDpy, sAhbRingSecondary[j].eglImage);
                        }
                        if (sAhbRingSecondary[j].ahb) {
                            AHardwareBuffer_release(sAhbRingSecondary[j].ahb);
                        }
                        sAhbRingSecondary[j] = {};
                    }
                    break;
                }
            }
        }
    }

    // GammaOS: DRM PRIME path requires Y-flip in the vertex shader.
    // The legacy blit path was implicitly Y-flipping while copying
    // (srcRow = ahbPtr + (srcH - 1 - dy) * ahbStride). With PRIME we
    // page-flip the AHB directly to scanout, so the GL-y-up output
    // displays upside-down unless we pre-flip Y in clip space. Apply
    // the flip to sDrmRotMat (multiply by [1,0,0,-1] on the left =
    // negate row-1 of the post-rotation matrix) and force the vertex
    // shader's matrix path on (sDrmGlRotation = true) even at 0
    // install rotation. drastic gets the updated matrix via the
    // setRotationMatrix call inside the QR loop, which runs after
    // this setup completes.
    if (sAhbRingPrimary[0].drmFbId != 0 && !sDrmYFlipForPrime) {
        sDrmRotMat[1] = -sDrmRotMat[1];
        sDrmRotMat[3] = -sDrmRotMat[3];
        sDrmGlRotation = true;
        sDrmYFlipForPrime = true;
        ALOGW("NanoMenu DRM PRIME: applied Y-flip to rotation matrix "
              "(rotMat=[%g %g %g %g], glRotation forced ON)",
              sDrmRotMat[0], sDrmRotMat[1], sDrmRotMat[2], sDrmRotMat[3]);
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

// Present one ring slot to all DRM displays. Each display reads from its
// assigned AHB in the given ring slot:
// - sDrmPrimaryIdx → sAhbRingPrimary[idx] (wallpaper + menu)
// - every other display → sAhbRingSecondary[idx] (wallpaper only)
// If the secondary AHB isn't allocated (single-display hardware, or allocation
// failed), every display falls back to the primary AHB (mirrored).
// Non-blocking page flip per display: displays are independent — if one fails
// to flip, the others still present.
//
// When called with idx=0, this is exactly the classic single-buffered path
// (drmFlipAll). The triple-buffer QR path calls it with idx=0,1,2 on a
// rolling 2-frame-old schedule so glFinish() observes mostly-complete GPU
// work.
//
// skipNonPrimary: when true, page flip is only submitted to the primary
// CRTC. Used by the QR loop to flip the secondary at half the rate
// (~30 fps) since on the dual-display RG DS roughly half of the
// secondary flips were getting EBUSY'd anyway due to cross-CRTC vblank
// drift, and the secondary's bottom-DS-screen content rarely changes
// fast enough that 30 fps is visible. AHB locks are skipped too so
// the CPU blit cost goes away on those iters.
static void drmFlipRingSlot(int idx, bool skipNonPrimary = false) {
    if (idx < 0 || idx >= AHB_RING_DEPTH) return;
    AhbRenderTarget& prim = sAhbRingPrimary[idx];
    AhbRenderTarget& sec  = sAhbRingSecondary[idx];
    if (!sDrmZeroCopy || !prim.ahb) return;

    static int sFlipCount = 0;
    // Log timing for the first 5 flips (boot window) then once per
    // second thereafter so we can observe steady-state latency
    // without flooding logcat. A "slow" flip (total > 10 ms) also
    // emits unconditionally so microhitches are captured, but
    // rate-limited to once per second to keep logs clean.
    bool periodic = (sFlipCount < 5) || (sFlipCount % 60 == 0);
    bool verbose = true; // always capture timing; filter at print time
    int64_t t0 = (systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL);

    // Unbind FBO so subsequent GL calls don't mess with AHB
    glBindFramebuffer(GL_FRAMEBUFFER, 0);

    const bool primeActive = (prim.drmFbId != 0) &&
                             (!sec.ahb || sec.drmFbId != 0);
    const bool haveSecondary = (sec.ahb != nullptr);
    const int blitRotation = sDrmGlRotation ? 0 : sDrmRotationDeg;

    // GPU fence wait. Two paths:
    //
    // 1. PRIME: DRM page-flips directly to the AHB -- no CPU read, so
    //    AHardwareBuffer_lock is unnecessary. Use eglClientWaitSyncKHR
    //    on the slot's fence directly. Saves ~500 us of lock + 20 us
    //    of unlock per iter on RG DS -- these add up to ~3 % CPU.
    //
    // 2. Legacy blit: need a CPU mapping to memcpy AHB -> dumb buffer,
    //    so AHB_lock is unavoidable. Pass the fence fd so the lock
    //    waits on it (rather than letting AHB's broken implicit
    //    dma-fence sync on RK3568 lead to tearing).
    //
    // 3. No fence + non-PRIME: fallback to glFinish() (global drain).
    int primaryFenceFd = -1;
    bool fenceUsed = false;
    if (sAhbRingSyncPrimary[idx] != EGL_NO_SYNC_KHR &&
        sEglDupNativeFenceFDANDROID && sEglDestroySyncKHR &&
        sRingEglDpy != EGL_NO_DISPLAY) {
        if (primeActive && sEglClientWaitSyncKHR) {
            // PRIME path: sync via eglClientWaitSyncKHR, no fd needed.
            sEglClientWaitSyncKHR(sRingEglDpy,
                                  sAhbRingSyncPrimary[idx],
                                  EGL_SYNC_FLUSH_COMMANDS_BIT_KHR,
                                  100000000);  // 100 ms timeout
            fenceUsed = true;
        } else {
            primaryFenceFd = sEglDupNativeFenceFDANDROID(
                    sRingEglDpy, sAhbRingSyncPrimary[idx]);
            if (primaryFenceFd >= 0) fenceUsed = true;
        }
        sEglDestroySyncKHR(sRingEglDpy, sAhbRingSyncPrimary[idx]);
        sAhbRingSyncPrimary[idx] = EGL_NO_SYNC_KHR;
    }
    if (!fenceUsed && !primeActive) {
        // Legacy blit path needs a pre-flip barrier since the AHB lock
        // below is passing fence_fd=-1. PRIME path doesn't need this --
        // if there was no fence, the GPU must already be idle (nothing
        // to sync for a slot that was never rendered).
        glFinish();
    }

    int64_t tFinish = verbose ? (systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL) : 0;

    // Pre-query strides once per AHB (may be padded beyond width).
    // Only needed for the legacy blit path; PRIME flips use fb_id alone.
    AHardwareBuffer_Desc descPrimary = {};
    uint32_t primaryStride = 0;
    AHardwareBuffer_Desc descSecondary = {};
    uint32_t secondaryStride = 0;
    void* primaryPtr = nullptr;
    void* secondaryPtr = nullptr;

    if (!primeActive) {
        AHardwareBuffer_describe(prim.ahb, &descPrimary);
        primaryStride = descPrimary.stride * 4;
        if (haveSecondary) {
            AHardwareBuffer_describe(sec.ahb, &descSecondary);
            secondaryStride = descSecondary.stride * 4;
        }

        // Lock primary AHB for CPU read. fenceUsed means we have a
        // dup'd fence fd; AHB_lock will wait on it and take ownership.
        int lockErr = AHardwareBuffer_lock(prim.ahb,
                                            AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN,
                                            fenceUsed ? primaryFenceFd : -1,
                                            nullptr, &primaryPtr);
        if (lockErr != 0 || !primaryPtr) {
            if (fenceUsed && primaryFenceFd >= 0) {
                close(primaryFenceFd); // ownership not transferred on failure
            }
            if (verbose) ALOGW("NanoMenu DRM: primary AHB lock failed %d (slot=%d)",
                               lockErr, idx);
            return;
        }
    } else if (primaryFenceFd >= 0) {
        // PRIME path already waited via eglClientWaitSyncKHR, but we
        // never actually used the fence_fd -- close it to avoid a fd leak.
        close(primaryFenceFd);
    }

    // Lock secondary AHB (legacy blit path only). Same slot's fence
    // covers both primary and secondary renders (they're done in
    // sequence, fence inserted after both). Since the fence fd was
    // consumed by the primary lock, pass -1 here -- the primary's wait
    // has already guaranteed both surfaces' GPU work is complete. PRIME
    // path skips this: no CPU read of secondary AHB is needed, the
    // page flip reads from the dma-buf directly.
    if (!primeActive && haveSecondary && !skipNonPrimary) {
        int serr = AHardwareBuffer_lock(sec.ahb,
                                         AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN,
                                         -1, nullptr, &secondaryPtr);
        if (serr != 0 || !secondaryPtr) {
            if (verbose) ALOGW("NanoMenu DRM: secondary AHB lock failed %d (slot=%d)",
                               serr, idx);
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

        // Skip non-primary CRTCs when caller asked us to (QR loop's
        // secondary-rate-halving optimization). Secondary keeps the
        // pixels it had on its last successful flip; visually that's
        // a 30 fps update on the bottom screen which is below DS
        // perception threshold for typical content.
        if (!isPrimary && skipNonPrimary) continue;

        // Select source AHB. Secondary displays use sec AHB if available
        // (PRIME: always, legacy: only if lock succeeded); else fall
        // back to primary (mirror mode).
        const bool useSecondaryAhb = !isPrimary && haveSecondary &&
                (primeActive || secondaryPtr != nullptr);
        const AhbRenderTarget& srcAhb = useSecondaryAhb ? sec : prim;

        int idx = 1 - d.activeBuffer;
        DrmBuffer& buf = d.buffers[idx];

        // DRM PRIME zero-copy path: when the source AHB was successfully
        // imported as a DRM scanout fb at allocation time, we skip the
        // CPU memcpy entirely and page flip straight to the AHB's
        // fb_id. Saves ~2-3 ms CPU per iter per display on RG DS.
        const bool primePath = (srcAhb.drmFbId != 0);
        uint32_t targetFbId = primePath ? srcAhb.drmFbId : buf.fbId;

        if (!primePath) {
            const void* srcPtr = useSecondaryAhb ? secondaryPtr : primaryPtr;
            uint32_t srcStride = useSecondaryAhb ? secondaryStride : primaryStride;
            uint32_t srcW = useSecondaryAhb ? sec.w : prim.w;
            uint32_t srcH = useSecondaryAhb ? sec.h : prim.h;
            blitAhbToDrmBuffer(srcPtr, srcStride, srcW, srcH,
                                buf.mapped, buf.pitch, d.w, d.h, blitRotation);
        }

        // Page flip — non-blocking. flags=0 means no vblank event is
        // requested, just swap the buffer on next vsync.
        //
        // Error handling (2026-04-13):
        //   -EBUSY: previous page flip for this CRTC has not landed
        //     yet. Previously we fell through to drmModeSetCrtc here,
        //     which is SYNCHRONOUS and blocks until the modeset takes
        //     effect on the next vblank. Per-frame timing showed the
        //     fallback hitting 13-32 ms on microhitch frames, driving
        //     the occasional 30-50 ms stall. On -EBUSY the correct
        //     action is to drop this flip -- the still-pending flip
        //     already has a newer buffer queued than what is on
        //     screen, so we simply keep the current active buffer
        //     index and let the next iteration try again.
        //   Other errors: fall back to drmModeSetCrtc, which handles
        //     the one-time "CRTC not enabled yet" case at the
        //     startup splash where the CRTC needs to be primed.
        // Page flip submission. Two pacing modes:
        //
        // - Working vblank (default, RG DS / RK3568): flags=0. The render
        //   loop sync's via DRM_IOCTL_WAIT_VBLANK. No page-flip events
        //   requested -> nothing to drain, and on dual-display setups we
        //   avoid waiting for cross-CRTC event timing skew (which on RG DS
        //   was adding ~8ms to the drain on top of WAIT_VBLANK -> dropping
        //   us into 30-fps pacing). This preserves the 60-fps behavior
        //   that landed in commit 10885f101d7.
        //
        // - Broken vblank (RK3576 / RGVITA, DSI command-mode): flags=
        //   DRM_MODE_PAGE_FLIP_EVENT. The render loop sync's via
        //   drmDrainPageFlipEvents which reads the events the kernel posts
        //   when the flip latches. WAIT_VBLANK on these panels never fires
        //   so it's skipped after detection (sDrmVblankBroken=true).
        //
        // sDrmVblankBroken flips true the first time WAIT_VBLANK exceeds
        // 100ms, after which all flips request the EVENT flag.
        struct drm_mode_crtc_page_flip flip = {};
        flip.crtc_id = d.crtcId;
        flip.fb_id = targetFbId;
        // Only request EVENT when we're going to drain it (vsync gate ON
        // and broken-vblank path active). When vsync is disabled at runtime
        // we'd never call the drainer, and unread events would pile up in
        // the DRM fd's queue until the kernel drops them.
        // Request page-flip completion events on:
        //  - broken-vblank panels (RK3576 DSI command-mode): kernel's vblank
        //    queue never wakes so WAIT_VBLANK is useless.
        //  - multi-CRTC setups (RG DS dual DSI): the two panels have separate
        //    vblank clocks that drift relative to each other. WAIT_VBLANK on
        //    the primary CRTC alone mis-paces the secondary CRTC's flips,
        //    producing EBUSY storms on the non-primary display whenever the
        //    phase drifts into alignment. drmDrainPageFlipEvents waits for
        //    every submitted flip to latch, giving correct cross-CRTC sync.
        const bool wantEvent =
                (sDrmVblankBroken || sDrmDisplays.size() > 1) &&
                (sVsyncEnabled != 0);
        flip.flags = wantEvent ? DRM_MODE_PAGE_FLIP_EVENT : 0;
        // Pre-check: if this CRTC still has a pending flip from a prior
        // iteration, don't submit another one. The kernel would EBUSY us
        // anyway, and submitting too early is what caused the tearing
        // storm after the 60Hz panel retiming (EBUSY count climbed ~200/min).
        // The previous iteration's drain should have consumed the event, but
        // on multi-CRTC setups with phase skew, stale events can decrement
        // sPendingFlipEvents for a flip that hasn't actually landed yet.
        // Per-CRTC tracking fixes this: wait until this specific CRTC's
        // event has arrived before submitting a new flip to it.
        int crtcSlot = wantEvent ? drmCrtcSlot(d.crtcId) : -1;
        if (crtcSlot >= 0 && sCrtcPending[crtcSlot] > 0) {
            if (!primePath) d.activeBuffer = idx;
            continue;
        }
        int flipRc = ioctl(sDrmFd, DRM_IOCTL_MODE_PAGE_FLIP, &flip);
        if (flipRc == 0 && wantEvent) {
            sPendingFlipEvents++;
            if (crtcSlot >= 0) sCrtcPending[crtcSlot]++;
        }
        if (flipRc != 0) {
            if (errno == EBUSY) {
                // Drop this flip. Keep the previous buffer as active
                // so the next iteration's page flip targets the
                // correct slot for the double-buffer rotation.
                //
                // Rate-limited diagnostic: EBUSY means the last flip
                // for this CRTC has not landed yet. Visible as a
                // stutter on the affected display. Counts accumulate
                // per CRTC so cross-display correlation is possible.
                static int64_t sLastEbusyLogMs = 0;
                static uint32_t sEbusyCount[8] = {0};
                if (i < 8) sEbusyCount[i]++;
                int64_t nowMs = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
                if (nowMs - sLastEbusyLogMs >= 500) {
                    sLastEbusyLogMs = nowMs;
                    ALOGW("NanoMenu DRM flip EBUSY: display=%zu primary=%d "
                          "flipCount=%d ebusy=[%u/%u/%u/%u]",
                          i, (int)(isPrimary ? 1 : 0), sFlipCount,
                          sEbusyCount[0], sEbusyCount[1],
                          sEbusyCount[2], sEbusyCount[3]);
                }
                continue;
            }
            struct drm_mode_crtc crtc = {};
            crtc.crtc_id = d.crtcId;
            crtc.fb_id = targetFbId;
            crtc.set_connectors_ptr = (uint64_t)(uintptr_t)&d.connId;
            crtc.count_connectors = 1;
            crtc.mode = d.mode;
            crtc.mode_valid = 1;
            ioctl(sDrmFd, DRM_IOCTL_MODE_SETCRTC, &crtc);
        }
        // Track activeBuffer only when we used a dumb buffer; PRIME path
        // doesn't have alternating buffers (the AHB IS the framebuffer).
        if (!primePath) d.activeBuffer = idx;
    }

    int64_t tCopy = verbose ? (systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL) : 0;

    // Unlock AHBs only if we locked them (legacy blit path).
    if (!primeActive && primaryPtr) {
        AHardwareBuffer_unlock(prim.ahb, nullptr);
    }
    if (secondaryPtr) {
        AHardwareBuffer_unlock(sec.ahb, nullptr);
    }

    {
        int64_t tEnd = systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL;
        int64_t tot = tEnd - t0;
        // Rate-limit slow-flip logging to once per second so a burst
        // does not flood logcat. Periodic samples always emit.
        static int64_t sLastSlowLogMs = 0;
        int64_t nowMs = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
        bool logSlow = (tot > 10000) &&
                       (nowMs - sLastSlowLogMs >= 1000);
        if (periodic || logSlow) {
            if (logSlow) sLastSlowLogMs = nowMs;
            ALOGW("NanoMenu AHB flip #%d slot=%d sync=%s: wait=%lldus "
                  "lock=%lldus blit+flip=%lldus unlock=%lldus "
                  "total=%lldus displays=%zu primary=%d sec=%d",
                  sFlipCount, idx, fenceUsed ? "fence" : "glFinish",
                  tFinish - t0, tLock - tFinish,
                  tCopy - tLock, tEnd - tCopy, tot,
                  sDrmDisplays.size(), sDrmPrimaryIdx,
                  haveSecondary ? 1 : 0);
        }
    }
    sFlipCount++;
}

// Legacy single-buffered path. Preserves XMB behavior and default QR flow
// (triple_buffer=0). Flips slot 0, which is exactly what every non-ring
// caller was doing before the ring landed.
static void drmFlipAll() {
    drmFlipRingSlot(0);
}

// Drain all pending page-flip completion events from the DRM fd. Called
// once per render iteration AFTER the page flips have been submitted --
// blocks until every submitted flip has latched, giving us vsync-accurate
// pacing on both video-mode and command-mode panels.
//
// Replaces DRM_IOCTL_WAIT_VBLANK on panels where the kernel doesn't
// generate periodic vblank interrupts (RK3576 DSI command-mode). Works
// uniformly on all DRM drivers because every DRM driver that implements
// page flipping also implements the flip-complete event.
//
// Uses poll() with a 100 ms timeout per event so a dropped/missing event
// (driver bug) doesn't wedge the render thread forever. If an event is
// lost we log once and bail; sPendingFlipEvents will drift and the next
// iteration's EBUSY drops will naturally re-sync.
static bool drmAnyCrtcPending() {
    for (int i = 0; i < sCrtcTrackCount; i++) {
        if (sCrtcPending[i] > 0) return true;
    }
    return false;
}

static void drmDrainPageFlipEvents() {
    if (sDrmFd < 0) return;
    if (sPendingFlipEvents <= 0 && !drmAnyCrtcPending()) return;
    char buf[4096];
    static int64_t sLastTimeoutLogMs = 0;
    // Per-CRTC arrival tracking for slow-drain diagnostics. On multi-CRTC
    // setups we want to know WHICH display's flip event is arriving late.
    const int64_t drainT0 = systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL;
    int64_t crtcArrivalUs[4] = {-1, -1, -1, -1};
    uint32_t crtcIds[4] = {0, 0, 0, 0};
    int numCrtcs = 0;
    // Loop until every CRTC we submitted to has received its event. The
    // per-CRTC gate is authoritative -- the global counter exists only for
    // legacy single-CRTC pacing. On multi-CRTC setups stale events can
    // decrement the global without matching any tracked CRTC, so waiting
    // on per-CRTC ensures we don't return until the flips we actually
    // submitted have all landed.
    while (sPendingFlipEvents > 0 || drmAnyCrtcPending()) {
        struct pollfd pfd = {};
        pfd.fd = sDrmFd;
        pfd.events = POLLIN;
        int pr = poll(&pfd, 1, 100);
        if (pr < 0) {
            if (errno == EINTR) continue;
            // fd broken -- abandon the drain so we don't spin
            sPendingFlipEvents = 0;
            for (int i = 0; i < sCrtcTrackCount; i++) sCrtcPending[i] = 0;
            return;
        }
        if (pr == 0) {
            // Timeout. Rate-limit the log to once per second so a panel
            // that never posts events doesn't flood logcat.
            int64_t nowMs = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
            if (nowMs - sLastTimeoutLogMs >= 1000) {
                sLastTimeoutLogMs = nowMs;
                ALOGW("NanoMenu: page-flip event drain timeout, "
                      "pending=%d -- resyncing",
                      sPendingFlipEvents);
            }
            // Reset so we don't accumulate stale debt.
            sPendingFlipEvents = 0;
            for (int i = 0; i < sCrtcTrackCount; i++) sCrtcPending[i] = 0;
            return;
        }
        ssize_t n = read(sDrmFd, buf, sizeof(buf));
        if (n <= 0) {
            if (n < 0 && errno == EINTR) continue;
            sPendingFlipEvents = 0;
            for (int i = 0; i < sCrtcTrackCount; i++) sCrtcPending[i] = 0;
            return;
        }
        // Walk the event records. Each one starts with a drm_event header
        // (type + length). Events of type DRM_EVENT_FLIP_COMPLETE match
        // the flips we just issued; other event types (rare) are ignored.
        char* p = buf;
        char* end = buf + n;
        while (p + sizeof(struct drm_event) <= end) {
            struct drm_event* ev = (struct drm_event*)p;
            if (ev->length == 0 || p + ev->length > end) break;
            if (ev->type == DRM_EVENT_FLIP_COMPLETE &&
                sPendingFlipEvents > 0) {
                sPendingFlipEvents--;
                // Decrement per-CRTC pending count for this specific
                // CRTC so the next iteration's submit for the same CRTC
                // will only proceed when its previous flip has landed.
                if (ev->length >= sizeof(struct drm_event_vblank)) {
                    struct drm_event_vblank* vb =
                            (struct drm_event_vblank*)p;
                    int slot = drmCrtcSlot(vb->crtc_id);
                    if (slot >= 0 && sCrtcPending[slot] > 0) {
                        sCrtcPending[slot]--;
                    }
                    // Capture per-CRTC arrival time for the slow-drain log.
                    if (numCrtcs < 4) {
                        int64_t arrivalUs =
                                systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL -
                                drainT0;
                        crtcIds[numCrtcs] = vb->crtc_id;
                        crtcArrivalUs[numCrtcs] = arrivalUs;
                        numCrtcs++;
                    }
                }
            }
            p += ev->length;
        }
    }
    // If the drain was slow, log per-CRTC arrivals so we can tell which
    // display is dragging. Rate-limited to once per second.
    const int64_t drainTotalUs =
            systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL - drainT0;
    if (drainTotalUs > 15000 && numCrtcs > 1) {
        static int64_t sLastSlowDrainMs = 0;
        int64_t nowMs = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
        if (nowMs - sLastSlowDrainMs >= 1000) {
            sLastSlowDrainMs = nowMs;
            ALOGW("NanoMenu slow drain: total=%lldus "
                  "crtc[0]=%u@%lldus crtc[1]=%u@%lldus",
                  (long long)drainTotalUs,
                  crtcIds[0], (long long)crtcArrivalUs[0],
                  crtcIds[1], (long long)crtcArrivalUs[1]);
        }
    }
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

    // Release all ring slots. sAhbTarget / sAhbTargetSecondary are refs to
    // slot 0 so they tear down with the ring. The EGLDisplay is retrieved
    // once and reused for every slot's eglDestroyImageKHR.
    EGLDisplay eglDpy = eglGetCurrentDisplay();
    auto releaseSlot = [&](AhbRenderTarget& t) {
        if (t.glFbo) { glDeleteFramebuffers(1, &t.glFbo); t.glFbo = 0; }
        if (t.glTexture) { glDeleteTextures(1, &t.glTexture); t.glTexture = 0; }
        if (t.eglImage != EGL_NO_IMAGE_KHR && eglDpy != EGL_NO_DISPLAY &&
            sEglDestroyImageKHR) {
            sEglDestroyImageKHR(eglDpy, t.eglImage);
        }
        t.eglImage = EGL_NO_IMAGE_KHR;
        // Release DRM PRIME fb + GEM handle if we imported one.
        if (t.drmFbId != 0 && sDrmFd >= 0) {
            ioctl(sDrmFd, DRM_IOCTL_MODE_RMFB, &t.drmFbId);
            t.drmFbId = 0;
        }
        if (t.drmGemHandle != 0 && sDrmFd >= 0) {
            struct drm_gem_close gc = {};
            gc.handle = t.drmGemHandle;
            ioctl(sDrmFd, DRM_IOCTL_GEM_CLOSE, &gc);
            t.drmGemHandle = 0;
        }
        if (t.ahb) { AHardwareBuffer_release(t.ahb); t.ahb = nullptr; }
        t.w = 0; t.h = 0;
    };
    for (int i = 0; i < AHB_RING_DEPTH; i++) {
        releaseSlot(sAhbRingPrimary[i]);
        releaseSlot(sAhbRingSecondary[i]);
        // Release any lingering EGL fence for this slot. These normally
        // get destroyed when drmFlipRingSlot consumes them, but a
        // pending fence can outlive the loop if QR was interrupted
        // mid-render.
        if (sAhbRingSyncPrimary[i] != EGL_NO_SYNC_KHR &&
            sEglDestroySyncKHR && sRingEglDpy != EGL_NO_DISPLAY) {
            sEglDestroySyncKHR(sRingEglDpy, sAhbRingSyncPrimary[i]);
        }
        sAhbRingSyncPrimary[i] = EGL_NO_SYNC_KHR;
    }
    // Reset ring cursors so a subsequent drmSetupZeroCopy starts fresh.
    sRingRenderIdx = 0;
    sRingPresentIdx = 0;
    sRingPrimedCount = 0;
    sRingEglDpy = EGL_NO_DISPLAY;
    // Drain any outstanding page flip events before handing the DRM fd
    // back (or closing it). Otherwise the kernel holds buffers hostage
    // and the next CRTC user (SurfaceFlinger on restart, for instance)
    // gets stuck because its first commit is blocked on our unconsumed
    // flip. 50 ms is plenty -- events arrive at panel refresh rate.
    drmDrainPageFlipEvents();
    sPendingFlipEvents = 0;
    for (int i = 0; i < sCrtcTrackCount; i++) sCrtcPending[i] = 0;

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
            setLaunchRomPath("");
            android::base::SetProperty("sys.gammaos.nano.launch_core", "");

            // GammaOS: Drastic quick-resume prime (recent-played path).
            // When launching a Nintendo DS game via drastic with QR
            // enabled, also populate the DE cache (libdrastic + BIOS +
            // firmware + ROM + audio patch) and prime QR props with the
            // "drastic" sentinel. Next boot will hit DrasticRunner
            // directly and show the live DS framebuffer in NanoMenu at
            // ~T+1.5s, mirroring the libretro QR path for RetroArch
            // cores. Guarded on mQuickResumeEnabled because
            // populate_drastic reads qr_rom to find the ROM file; with
            // QR disabled there would be no path to stage.
            if (re.launchPkg == "com.dsemu.drastic" && mQuickResumeEnabled) {
                setQrRomPath(re.romPath);
                // Non-filename sentinel distinguishes drastic QR from
                // libretro QR (which stores a full core .so path).
                android::base::SetProperty(
                        "persist.gammaos.nano.qr_core", "drastic");
                property_set("persist.gammaos.nano.qr_prepared", "1");
                // Derive overlay name (basename without extension)
                std::string gameName = filename;
                size_t dotPos = gameName.rfind('.');
                if (dotPos != std::string::npos) gameName.erase(dotPos);
                android::base::SetProperty(
                        "persist.gammaos.nano.qr_game_name", gameName);
                // Persistent copy of the tab-separated intent for the
                // drastic QR handoff path. The primary intent file at
                // /data/system/nano_launch_intent.txt is consumed and
                // deleted by RootWindowContainer on first launch. Next
                // boot's QR handoff needs to re-issue the same specific
                // drastic-activity-with-content-URI intent to drop the
                // user straight into the game (instead of drastic's
                // own main menu). We stash a copy under a dedicated
                // path that nothing else consumes, and the QR handoff
                // threadLoop() block re-creates the primary intent
                // file from this copy before firing do_launch.
                { const char* qf = "/data/system/nano_drastic_qr_intent.txt";
                  int qfd = open(qf, O_WRONLY|O_CREAT|O_TRUNC, 0666);
                  if (qfd >= 0) {
                      write(qfd, tabIntent.c_str(), tabIntent.size());
                      close(qfd);
                      chmod(qf, 0644);
                  } }
                // Kick populate_drastic AFTER qr_rom so nano_cache.sh
                // can read it for the ROM path. Order matters here --
                // the init property trigger fires synchronously on
                // cache_op change.
                property_set("sys.gammaos.nano.cache_ready", "0");
                property_set("sys.gammaos.nano.cache_op", "populate_drastic");
            }
        } else {
            std::string corePath = "/data/data/com.retroarch.aarch64/cores/" + re.coreSo;
            setLaunchRomPath(re.romPath);
            android::base::SetProperty("sys.gammaos.nano.launch_core", corePath);
            android::base::SetProperty("sys.gammaos.nano.launch_app", "com.retroarch.aarch64");
            android::base::SetProperty("sys.gammaos.nano.launch_intent", "");
            // Trigger DE cache populate
            property_set("sys.gammaos.nano.cache_ready", "0");
            property_set("sys.gammaos.nano.cache_op", "populate");
            if (mQuickResumeEnabled) {
                setQrRomPath(re.romPath);
                android::base::SetProperty("persist.gammaos.nano.qr_core", corePath);
                property_set("persist.gammaos.nano.qr_prepared", "1");
                // Overlay name for QR preview: basename without extension.
                // Read by both libretro QR and drastic QR from the same prop.
                std::string gameName;
                { size_t ls = re.romPath.rfind('/');
                  gameName = (ls != std::string::npos)
                          ? re.romPath.substr(ls + 1) : re.romPath; }
                size_t dotPos = gameName.rfind('.');
                if (dotPos != std::string::npos) gameName.erase(dotPos);
                android::base::SetProperty(
                        "persist.gammaos.nano.qr_game_name", gameName);
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
        setLaunchRomPath("");
        android::base::SetProperty("sys.gammaos.nano.launch_core", "");

        // GammaOS: Drastic quick-resume prime (XMB system path).
        // Same contract as the recent-played standalone branch: prime
        // the QR props with the "drastic" sentinel and kick
        // populate_drastic so nano_cache.sh caches libdrastic + BIOS +
        // ROM (with the idempotent 4-byte audio patch) against the
        // next boot. Guarded on mQuickResumeEnabled because without
        // QR there is no target boot to prepare, and populate_drastic
        // reads qr_rom to find the ROM path.
        if (sys.launchPkg == "com.dsemu.drastic" && mQuickResumeEnabled) {
            setQrRomPath(romPath);
            android::base::SetProperty(
                    "persist.gammaos.nano.qr_core", "drastic");
            property_set("persist.gammaos.nano.qr_prepared", "1");
            // Derive overlay name (basename without extension)
            std::string gameName = filename;
            size_t dotPos = gameName.rfind('.');
            if (dotPos != std::string::npos) gameName.erase(dotPos);
            android::base::SetProperty(
                    "persist.gammaos.nano.qr_game_name", gameName);
            // Persistent copy of the tab-separated intent for drastic
            // QR handoff. See the matching recent-played branch for
            // the full rationale: the primary nano_launch_intent.txt
            // is consumed once by RootWindowContainer and deleted, so
            // next boot's QR handoff needs to recreate it from this
            // stash to drop the user straight into the game instead
            // of drastic's own main menu.
            { const char* qf = "/data/system/nano_drastic_qr_intent.txt";
              int qfd = open(qf, O_WRONLY|O_CREAT|O_TRUNC, 0666);
              if (qfd >= 0) {
                  write(qfd, tabIntent.c_str(), tabIntent.size());
                  close(qfd);
                  chmod(qf, 0644);
              } }
            // Kick populate_drastic AFTER qr_rom so nano_cache.sh can
            // read the path. The init property trigger fires
            // synchronously on cache_op change.
            property_set("sys.gammaos.nano.cache_ready", "0");
            property_set("sys.gammaos.nano.cache_op", "populate_drastic");
        }
    } else {
        // RetroArch core
        std::string corePath = "/data/data/com.retroarch.aarch64/cores/" + sys.coreSo;
        ALOGI("NanoMenu XMB: launching %s core=%s", romPath.c_str(), corePath.c_str());
        setLaunchRomPath(romPath);
        android::base::SetProperty("sys.gammaos.nano.launch_core", corePath);
        android::base::SetProperty("sys.gammaos.nano.launch_app", "com.retroarch.aarch64");
        android::base::SetProperty("sys.gammaos.nano.launch_intent", "");
        // Trigger DE cache populate
        property_set("sys.gammaos.nano.cache_ready", "0");
        property_set("sys.gammaos.nano.cache_op", "populate");

        // Prime Quick Resume (only for RetroArch games)
        if (mQuickResumeEnabled) {
            setQrRomPath(romPath);
            android::base::SetProperty("persist.gammaos.nano.qr_core", corePath);
            property_set("persist.gammaos.nano.qr_prepared", "1");
            // Overlay name for QR preview: basename without extension.
            // Read by both libretro QR and drastic QR from the same prop.
            std::string gameName;
            { size_t ls = romPath.rfind('/');
              gameName = (ls != std::string::npos)
                      ? romPath.substr(ls + 1) : romPath; }
            size_t dotPos = gameName.rfind('.');
            if (dotPos != std::string::npos) gameName.erase(dotPos);
            android::base::SetProperty(
                    "persist.gammaos.nano.qr_game_name", gameName);
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
        // Remap logical-landscape scissor box to FBO pixels for the
        // current rotation. Without this, on a 90/270-rotated panel
        // the scissor only covers a fraction of the rotated content
        // and game name text gets truncated. See identical block at
        // ~line 4763 for full rationale.
        {
            int sx, sy, sw, sh;
            int lx = (int)listScissorX, ly = 0,
                lw = (int)listScissorW, lh = (int)mHeight;
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

            float saturation = smokeActive ? 1.0f : 0.15f;
            float gradient   = smokeActive ? 0.0f : 1.0f;
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
            // User requirement: the preview loop is playable — ABXY /
            // DPAD / START / L / R all reach the running DS core via
            // DrasticRunner::setInput. SELECT cancels QR and drops to
            // the NanoMenu XMB. The Android BACK key toggles a
            // "handoff suspended" state: even when the fade completes
            // and the core is at full color, the handoff does not
            // fire while suspended, letting the user keep playing in
            // nano's own render loop. Pressing BACK a second time
            // clears the suspension and resumes the handoff on the
            // next frame.
            int dsBtnMask = 0;
            bool qrCancelled = false;
            bool handoffSuspended = false;
            bool backWasDown = false;   // edge detect for BACK toggle

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
                //   BTN_SELECT  → cancel QR, drop to XMB
                //   KEY_BACK    → toggle handoff suspension (edge only)
                for (int fd : mInputFds) {
                    struct input_event ev;
                    while (read(fd, &ev, sizeof(ev)) == sizeof(ev)) {
                        if (ev.type == EV_KEY) {
                            const bool pressed = (ev.value != 0);
                            // SELECT cancels QR regardless of which
                            // phase we're in. On release we ignore —
                            // the initial press is the cancel signal.
                            if (ev.code == BTN_SELECT && pressed
                                && !smokeActive) {
                                // In smoke mode SELECT is just another
                                // DS button (case BTN_SELECT below maps
                                // it to kDsBtnSelect). In real QR mode
                                // it cancels to XMB.
                                qrCancelled = true;
                                ALOGI("drastic QR: SELECT pressed, "
                                      "cancelling to XMB");
                                break;
                            }
                            // BACK toggles handoff suspension on the
                            // press edge only (so holding it doesn't
                            // thrash). First BACK during the fade =
                            // suspend handoff. Second BACK while
                            // suspended = resume handoff.
                            if (ev.code == KEY_BACK) {
                                if (pressed && !backWasDown) {
                                    handoffSuspended = !handoffSuspended;
                                    ALOGI("drastic QR: BACK press, "
                                          "handoffSuspended=%d",
                                          handoffSuspended ? 1 : 0);
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
                            // SELECT mapped to DS Select. In real QR
                            // mode the press also triggers qrCancelled
                            // above (and we strip kDsBtnSelect from the
                            // mask before forwarding to drastic). In
                            // smoke mode the cancel is suppressed and
                            // we leave Select in the mask -- so it
                            // reaches the emulator like every other
                            // button.
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

                // Push the accumulated button state to drastic. In real
                // QR mode kDsBtnSelect is stripped because Select is
                // reserved for "drop to XMB" -- if we let it through,
                // every cancel-to-XMB press would also fire SELECT to
                // the emulator. In smoke mode the cancel is disabled,
                // so Select goes through as a normal DS button.
                int sendMask = dsBtnMask;
                if (!smokeActive) sendMask &= ~DrasticRunner::kDsBtnSelect;
                drastic->setInput(sendMask);

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
                const bool showOverlay = !smokeActive;

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

                if (bootComplete) {
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
                    // GammaOS: handoffSuspended is toggled by the user
                    // pressing BACK during the loop. When set, we keep
                    // rendering the DS at full color and do not fire
                    // the handoff. Pressing BACK again clears the flag
                    // and the next frame through this branch will fire
                    // the handoff normally.
                    if (t >= 1.0f && drasticQrHandoff && !handoffFired
                            && !handoffSuspended) {
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

                        // Opt-in branch: if persist.gammaos.nano.drastic_app=1
                        // we skip the framework-based am-start handoff and
                        // instead fork app_process with the
                        // GammaDrasticNanoShim classpath so drastic runs
                        // inside the minimal-boot environment (SF never
                        // starts). The DrasticRunner QR instance is torn
                        // down first so the shim process can claim DRM's
                        // AHB ring via NanoBridge. When unset (default),
                        // we fall through to the legacy am-start path
                        // below -- the real drastic Activity boots after
                        // system_server comes up, exactly as today.
                        //
                        // See docs dialogue with drastic-android-mod for
                        // the lifecycle and the NanoBridge surface. The
                        // fork-exec itself is not yet implemented; this
                        // branch is the handoff skeleton.
                        {
                            char drasticAppProp[PROPERTY_VALUE_MAX] = {};
                            property_get(
                                    "persist.gammaos.nano.drastic_app",
                                    drasticAppProp, "0");
                            if (drasticAppProp[0] == '1') {
                                ALOGI("drastic QR: nano-shim handoff "
                                      "path selected (persist.gammaos."
                                      "nano.drastic_app=1)");

                                // Step 1: release DrasticRunner's
                                // fake-JNI DS CPU threads. The shim's
                                // ART will re-dlopen libdrastic_arm64
                                // fresh; without pauseDrastic, the
                                // original worker pool continues running
                                // in nano.
                                DrasticRunner* drastic =
                                        DrasticRunner::getInstance();
                                if (drastic) {
                                    drastic->pauseDrastic();
                                    ALOGI("drastic QR nano-shim: paused "
                                          "DrasticRunner fake-JNI path");
                                }

                                // Step 2: bring the NanoBridge server
                                // up if it's not already. main.cpp
                                // starts it only when the opt-in prop
                                // was set at boot; defensively call it
                                // again here -- the server is
                                // idempotent.
                                if (!android::nano_bridge::startServer()) {
                                    ALOGW("drastic QR nano-shim: Bridge "
                                          "server failed to start -- "
                                          "shim will fail to connect");
                                }

                                // Step 3: look up stock drastic's UID
                                // by stat'ing its app-private dir. We
                                // need this UID for setuid + setgid in
                                // the fork child so the shim sees stock
                                // drastic's files under
                                // /data/data/com.dsemu.drastic/ as its
                                // own.
                                struct stat stockStat = {};
                                uid_t stockUid = 0;
                                gid_t stockGid = 0;
                                if (stat("/data/data/com.dsemu.drastic",
                                         &stockStat) == 0) {
                                    stockUid = stockStat.st_uid;
                                    stockGid = stockStat.st_gid;
                                    ALOGI("drastic QR nano-shim: stock "
                                          "drastic uid=%u gid=%u",
                                          stockUid, stockGid);
                                } else {
                                    ALOGW("drastic QR nano-shim: cannot "
                                          "stat stock drastic datadir "
                                          "(%s); shim launch aborted",
                                          strerror(errno));
                                    goto legacy_handoff;
                                }

                                // Step 4: resolve the shim APK path and
                                // the stock drastic APK path. Both are
                                // fixed: shim under /system/priv-app,
                                // stock under /data/app.
                                std::string shimApk =
                                        "/system/priv-app/"
                                        "GammaDrasticNanoShim/"
                                        "GammaDrasticNanoShim.apk";
                                std::string drasticApk;
                                {
                                    DIR* d = opendir("/data/app");
                                    if (d) {
                                        struct dirent* e;
                                        while ((e = readdir(d)) != nullptr) {
                                            std::string name(e->d_name);
                                            // Android 11+ puts apps in
                                            // ~/data/app/~~hash1==/pkg-hash2==/
                                            if (name.substr(0, 2) != "~~") continue;
                                            std::string inner = std::string("/data/app/") + name;
                                            DIR* d2 = opendir(inner.c_str());
                                            if (!d2) continue;
                                            struct dirent* e2;
                                            while ((e2 = readdir(d2)) != nullptr) {
                                                std::string n2(e2->d_name);
                                                if (n2.find("com.dsemu.drastic-") == 0 &&
                                                    n2.find("nano") == std::string::npos) {
                                                    drasticApk = inner + "/" + n2 + "/base.apk";
                                                    break;
                                                }
                                            }
                                            closedir(d2);
                                            if (!drasticApk.empty()) break;
                                        }
                                        closedir(d);
                                    }
                                }
                                if (drasticApk.empty()) {
                                    ALOGW("drastic QR nano-shim: stock "
                                          "drastic APK not found under "
                                          "/data/app; shim launch "
                                          "aborted");
                                    goto legacy_handoff;
                                }
                                ALOGI("drastic QR nano-shim: drastic apk=%s",
                                      drasticApk.c_str());
                                ALOGI("drastic QR nano-shim: shim apk=%s",
                                      shimApk.c_str());

                                // Step 5: resolve ROM path. Use the
                                // REAL /storage path the user picked,
                                // not the nano_cache staging copy.
                                // Stock drastic expects ROMs under
                                // /storage/<UUID>/nds/ via its
                                // normal ACTION_VIEW content:// flow;
                                // the shim mirrors that. Source of
                                // truth: /data/system/nano_qr_rom.txt
                                // (getQrRomPath). Skip nano_cache --
                                // that file is mode 0770:root:media_rw
                                // with a longer path and isn't what
                                // drastic's DraSticPathCache expects.
                                std::string romPath = getQrRomPath();
                                if (romPath.empty()) {
                                    ALOGW("drastic QR nano-shim: "
                                          "getQrRomPath returned empty; "
                                          "shim launch aborted");
                                    goto legacy_handoff;
                                }
                                ALOGI("drastic QR nano-shim: rom=%s",
                                      romPath.c_str());

                                // Step 6: fork + setuid + setexeccon +
                                // exec. The classpath lists shim first
                                // so its DraSticGlView wins (see expert
                                // dialog 2026-04-15 on PathClassLoader
                                // scan order).
                                std::string classpathArg =
                                        std::string("-Djava.class.path=")
                                        + shimApk + ":" + drasticApk;

                                // Init-service based launch. Fork+exec
                                // of app_process directly from
                                // gammaos-nano's multi-threaded
                                // context hits a bionic linker
                                // failure ("libnativeloader.so not
                                // found") no matter how we sanitise
                                // the child (setuid, capset, vfork,
                                // shell intermediary, fd close -- all
                                // tried, all still fail). Init's own
                                // fork+exec is single-threaded and
                                // clean, so we hand the job to init
                                // via ctl.start and a tiny launcher
                                // binary reading args from props.
                                property_set(
                                        "persist.gammaos.nano.drastic_rom",
                                        romPath.c_str());
                                property_set(
                                        "persist.gammaos.nano.drastic_apk",
                                        drasticApk.c_str());
                                property_set(
                                        "persist.gammaos.nano.drastic_shim",
                                        shimApk.c_str());
                                // Point the shim at the prebuilt_etc-
                                // staged libs which carry the
                                // expert's v10 longjmp + audio
                                // patches. /system/etc/drastic_nano
                                // is outside the app classloader's
                                // namespace permitted.paths -- so
                                // separately stage the libs to a
                                // namespace-accessible location as
                                // root from ShimLauncher before exec.
                                // That's still TODO; for this
                                // iteration we hand the shim the
                                // /system/etc path and let the shim's
                                // updated findShimLibDir (v10) resolve
                                // via its own fallback chain. If
                                // dlopen still fails on namespace
                                // accessibility, we copy libs from
                                // /system/etc to drastic's nano_cache
                                // from ShimLauncher (root can write).
                                property_set(
                                        "persist.gammaos.nano.drastic_libdir",
                                        "/system/etc/drastic_nano");
                                // Stop the framework's compositor
                                // chain so DRM master can return to
                                // nano. On RK3568 the vendor HWC
                                // (hwcomposer-3) is the actual DRM
                                // master holder; SF is its client.
                                // Both must go for nano's drmSet
                                // Master to succeed.
                                // Option C implementation: stop SF +
                                // HWC (free DRM master) and then start
                                // gammaos_sf_stub before the shim so the
                                // shim's SurfaceView.<init> -> SCC.
                                // onFirstRef() -> waitForService
                                // ("SurfaceFlingerAIDL") lookup resolves
                                // to our empty binder instead of
                                // blocking forever. See SfAidlStub.cpp.
                                sShimHandoffActive.store(true);
                                ALOGI("drastic QR nano-shim: stopping "
                                      "hwcomposer-3 then surfaceflinger "
                                      "(Option C: SF AIDL stub)");
                                property_set("ctl.stop", "hwcomposer-3");
                                usleep(200000);
                                property_set("ctl.stop", "surfaceflinger");
                                usleep(300000);
                                // TODO: nano reinit DRM master here
                                // (drmOpen + drmSetMaster, or call
                                // existing drmEarlySplash equivalent).
                                ALOGI("drastic QR nano-shim: SF + "
                                      "HWC stopped, starting SF stub");

                                // Start the SF AIDL stub service.
                                // Init spawns /system/bin/gammaos_sf_stub
                                // in the gammaos_sf_stub domain, which
                                // registers an empty binder under names
                                // SurfaceFlingerAIDL and SurfaceFlinger
                                // via servicemanager. Give it time to
                                // finish registration before the shim
                                // issues its waitForService lookup.
                                property_set(
                                        "ctl.start",
                                        "gammaos_sf_stub");
                                usleep(300000);
                                ALOGI("drastic QR nano-shim: SF stub "
                                      "started, forking shim");

                                property_set(
                                        "ctl.start",
                                        "gammaos_drastic_shim");
                                ALOGI("drastic QR nano-shim: "
                                      "triggered init service "
                                      "gammaos_drastic_shim via "
                                      "ctl.start; nano stays alive "
                                      "to service the NanoBridge "
                                      "socket for the shim");
                                // Do NOT set mExitRequested -- nano
                                // needs to keep running so
                                // NanoBridgeServer can accept the
                                // shim's handshake and route
                                // queueBuffer calls into the DRM
                                // ring. Also swallow future QR
                                // handoff triggers so we don't fire
                                // another shim.
                                handoffFired = true;
                                break;
#if 0
                                // Legacy fork path kept for reference;
                                // not reached. Hit a bionic linker
                                // failure in every variant (fork,
                                // vfork, setuid yes/no, capset, fd
                                // close, sh-c, etc). Init-service
                                // launcher above is the working path.
                                pid_t pid = vfork();
                                if (pid == 0) {
                                    // Child.
                                    setsid();

                                    // Build a minimal environment so
                                    // app_process / ART can find the
                                    // boot classpath, dex2oat paths,
                                    // and data dirs. Nano's own env
                                    // is empty (init-launched service
                                    // without env inheritance).
                                    setenv("PATH",
                                           "/product/bin:"
                                           "/apex/com.android.runtime/bin:"
                                           "/apex/com.android.art/bin:"
                                           "/system_ext/bin:"
                                           "/system/bin:/system/xbin:"
                                           "/odm/bin:/vendor/bin:"
                                           "/vendor/xbin", 1);
                                    setenv("ANDROID_ROOT", "/system", 1);
                                    setenv("ANDROID_DATA", "/data", 1);
                                    setenv("ANDROID_ART_ROOT",
                                           "/apex/com.android.art", 1);
                                    setenv("ANDROID_I18N_ROOT",
                                           "/apex/com.android.i18n", 1);
                                    setenv("ANDROID_TZDATA_ROOT",
                                           "/apex/com.android.tzdata", 1);

                                    // BOOTCLASSPATH / DEX2OATBOOTCLASSPATH
                                    // / SYSTEMSERVERCLASSPATH are
                                    // populated by derive_classpath
                                    // during boot and cached at
                                    // /data/system/environ. Source
                                    // them from there.
                                    // derive_classpath format:
                                    //   export NAME VALUE\n
                                    auto loadEnvFromFile = [](const char* path) {
                                        FILE* f = fopen(path, "r");
                                        if (!f) return;
                                        char line[16384];
                                        while (fgets(line, sizeof(line), f)) {
                                            size_t n = strlen(line);
                                            while (n > 0 && (line[n-1] == '\n' ||
                                                             line[n-1] == '\r')) {
                                                line[--n] = 0;
                                            }
                                            if (strncmp(line, "export ", 7) != 0) continue;
                                            char* name = line + 7;
                                            char* space = strchr(name, ' ');
                                            if (!space) continue;
                                            *space = 0;
                                            char* val = space + 1;
                                            setenv(name, val, 1);
                                        }
                                        fclose(f);
                                    };
                                    loadEnvFromFile("/data/system/environ/classpath");

                                    // Setgid/setuid FIRST while we
                                    // still have CAP_SETGID/SETUID,
                                    // then capset to drop all caps so
                                    // the linker's AT_SECURE logic
                                    // doesn't fire on exec and
                                    // libnativeloader.so resolves via
                                    // the ART-apex namespace link.
                                    // Close inherited file descriptors
                                    // beyond 0/1/2. Nano has open DRM
                                    // fds, EGL, the bridge listen
                                    // socket, etc. which bleed into
                                    // the child; worth ruling out as
                                    // the cause of the linker's
                                    // libnativeloader.so failure.
                                    {
                                        DIR* d = opendir("/proc/self/fd");
                                        if (d) {
                                            struct dirent* e;
                                            while ((e = readdir(d)) != nullptr) {
                                                int fd = atoi(e->d_name);
                                                if (fd > 2) close(fd);
                                            }
                                            closedir(d);
                                        }
                                    }

                                    (void)stockUid;
                                    (void)stockGid;
                                    // Shell intermediary: `su X -c cmd`
                                    // style setup. Without this,
                                    // direct exec of app_process from
                                    // a fork of gammaos-nano hits a
                                    // "CANNOT LINK ... libnativeloader
                                    // not found" linker failure that
                                    // DOES NOT reproduce when the
                                    // same UID runs app_process via a
                                    // shell. The shell in between
                                    // normalises whatever state is
                                    // off (likely related to
                                    // linkerconfig selection based on
                                    // caller context). Cost: one
                                    // extra exec, <10ms.
                                    {
                                        std::string cmd =
                                                "exec /system/bin/app_process ";
                                        cmd += classpathArg;
                                        cmd += " /system/bin ";
                                        cmd += "gammaos.drastic.NanoDraSticEntry ";
                                        cmd += "\""; cmd += romPath; cmd += "\" ";
                                        cmd += "\""; cmd += drasticApk; cmd += "\"";
                                        execl("/system/bin/sh",
                                              "sh", "-c", cmd.c_str(),
                                              nullptr);
                                    }
                                    _exit(93);  // exec failed
                                } else if (pid > 0) {
                                    ALOGI("drastic QR nano-shim: forked "
                                          "pid=%d; nano continues "
                                          "holding DRM master",
                                          pid);
                                    // Park the nano main loop: let the
                                    // existing render loop continue but
                                    // stop driving DrasticRunner's XMB;
                                    // the shim now owns rendering via
                                    // NanoBridge. On shim exit we'd
                                    // normally loop back to XMB; v1
                                    // treats the shim as a one-shot
                                    // session -- exit nano when shim
                                    // exits. Wait in a background
                                    // thread so the main render loop
                                    // keeps going.
                                    std::thread([pid]() {
                                        int status = 0;
                                        waitpid(pid, &status, 0);
                                        ALOGI("drastic QR nano-shim: "
                                              "child exited (status=0x%x)",
                                              status);
                                    }).detach();
                                    mExitRequested = true;
                                    break;
                                } else {
                                    ALOGE("drastic QR nano-shim: fork "
                                          "failed (%s); falling back to "
                                          "am-start", strerror(errno));
                                    // fall through to legacy path
                                }
#endif
                            }
                        }
                        legacy_handoff:

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
                } else if (!smokeActive) {
                    // Slow creep toward color while we're still in the
                    // pre-handoff "preview" period. Smoke mode skips
                    // this -- saturation/gradient stay at the 1.0/0.0
                    // we set at loop entry so the output is exactly
                    // what drastic produced (no fade overlay).
                    saturation = fminf(saturation + 0.0003f, 0.35f);
                    gradient = fmaxf(gradient - 0.0002f, 0.7f);
                }

                // GammaOS: BACK-pause override. When the user suspends
                // the handoff, pin the visible state at full color and
                // no gradient regardless of where the natural fade is.
                // This lets them keep playing the preview indefinitely.
                // When they press BACK again to resume, handoffSuspended
                // clears and the next iteration's fade math takes over.
                if (handoffSuspended) {
                    saturation = 1.0f;
                    gradient = 0.0f;
                }
            }

            // GammaOS: SELECT cancel flow. The user pressed SELECT
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
                ALOGI("drastic QR: cancel flow -- clearing QR, "
                      "asking init to respawn nano");
                property_set("persist.gammaos.nano.qr_prepared", "0");
                property_set("persist.gammaos.nano.qr_core", "");
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
                  "(handoff=%d suspended=%d exitRequested=%d)",
                  handoffFired ? 1 : 0,
                  handoffSuspended ? 1 : 0,
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
    int64_t bootCompletedDetectedMs = 0;
    while (!exitPending() && !mExitRequested) {
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
