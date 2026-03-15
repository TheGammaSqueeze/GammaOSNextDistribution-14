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
#include <ui/PixelFormat.h>
#include <ui/Rect.h>

#include <gui/ISurfaceComposer.h>
#include <gui/Surface.h>
#include <gui/SurfaceComposerClient.h>

#include <GLES2/gl2.h>
#include <EGL/eglext.h>

#include "NanoMenu.h"

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

// ---------------------------------------------------------------------------
// Effect names
// ---------------------------------------------------------------------------

static const char* kEffectNames[NUM_EFFECTS + 1] = {
    "None",
    "Snow", "Rain", "Confetti", "Sparks", "Fireflies",
    "Bubbles", "Starfield", "Embers", "Leaves", "Dust",
    "Plasma", "Static", "Scanlines", "Mosaic", "Matrix",
    "Fire", "Aurora", "Ripple", "Checkerboard", "Spiral"
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
};
static const int kNumActiveEffects = sizeof(kActiveEffects) / sizeof(kActiveEffects[0]);
// Index into kActiveEffects (NOT the effect ID itself)
static int sActiveEffectIdx = 0;

// ---------------------------------------------------------------------------
// Font data (8x16 CP437 bitmap, ASCII 32..126)
// ---------------------------------------------------------------------------

// clang-format off
static const uint8_t kFont8x16[][16] = {
    {0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x18,0x3C,0x3C,0x3C,0x18,0x18,0x18,0x00,0x18,0x18,0x00,0x00,0x00,0x00},
    {0x00,0x66,0x66,0x66,0x24,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x6C,0x6C,0xFE,0x6C,0x6C,0x6C,0xFE,0x6C,0x6C,0x00,0x00,0x00,0x00},
    {0x18,0x18,0x7C,0xC6,0xC2,0xC0,0x7C,0x06,0x06,0x86,0xC6,0x7C,0x18,0x18,0x00,0x00},
    {0x00,0x00,0x00,0x00,0xC2,0xC6,0x0C,0x18,0x30,0x60,0xC6,0x86,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x38,0x6C,0x6C,0x38,0x76,0xDC,0xCC,0xCC,0xCC,0x76,0x00,0x00,0x00,0x00},
    {0x00,0x30,0x30,0x30,0x60,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x0C,0x18,0x30,0x30,0x30,0x30,0x30,0x30,0x18,0x0C,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x30,0x18,0x0C,0x0C,0x0C,0x0C,0x0C,0x0C,0x18,0x30,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x00,0x00,0x66,0x3C,0xFF,0x3C,0x66,0x00,0x00,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x00,0x00,0x18,0x18,0x7E,0x18,0x18,0x00,0x00,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x18,0x18,0x18,0x30,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xFE,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x18,0x18,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x00,0x02,0x06,0x0C,0x18,0x30,0x60,0xC0,0x80,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x7C,0xC6,0xC6,0xCE,0xDE,0xF6,0xE6,0xC6,0xC6,0x7C,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x18,0x38,0x78,0x18,0x18,0x18,0x18,0x18,0x18,0x7E,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x7C,0xC6,0x06,0x0C,0x18,0x30,0x60,0xC0,0xC6,0xFE,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x7C,0xC6,0x06,0x06,0x3C,0x06,0x06,0x06,0xC6,0x7C,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x0C,0x1C,0x3C,0x6C,0xCC,0xFE,0x0C,0x0C,0x0C,0x1E,0x00,0x00,0x00,0x00},
    {0x00,0x00,0xFE,0xC0,0xC0,0xC0,0xFC,0x06,0x06,0x06,0xC6,0x7C,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x38,0x60,0xC0,0xC0,0xFC,0xC6,0xC6,0xC6,0xC6,0x7C,0x00,0x00,0x00,0x00},
    {0x00,0x00,0xFE,0xC6,0x06,0x06,0x0C,0x18,0x30,0x30,0x30,0x30,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x7C,0xC6,0xC6,0xC6,0x7C,0xC6,0xC6,0xC6,0xC6,0x7C,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x7C,0xC6,0xC6,0xC6,0x7E,0x06,0x06,0x06,0x0C,0x78,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x00,0x18,0x18,0x00,0x00,0x00,0x18,0x18,0x00,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x00,0x18,0x18,0x00,0x00,0x00,0x18,0x18,0x30,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x06,0x0C,0x18,0x30,0x60,0x30,0x18,0x0C,0x06,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x00,0x00,0x7E,0x00,0x00,0x7E,0x00,0x00,0x00,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x60,0x30,0x18,0x0C,0x06,0x0C,0x18,0x30,0x60,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x7C,0xC6,0xC6,0x0C,0x18,0x18,0x18,0x00,0x18,0x18,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x7C,0xC6,0xC6,0xC6,0xDE,0xDE,0xDE,0xDC,0xC0,0x7C,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x10,0x38,0x6C,0xC6,0xC6,0xFE,0xC6,0xC6,0xC6,0xC6,0x00,0x00,0x00,0x00},
    {0x00,0x00,0xFC,0x66,0x66,0x66,0x7C,0x66,0x66,0x66,0x66,0xFC,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x3C,0x66,0xC2,0xC0,0xC0,0xC0,0xC0,0xC2,0x66,0x3C,0x00,0x00,0x00,0x00},
    {0x00,0x00,0xF8,0x6C,0x66,0x66,0x66,0x66,0x66,0x66,0x6C,0xF8,0x00,0x00,0x00,0x00},
    {0x00,0x00,0xFE,0x66,0x62,0x68,0x78,0x68,0x60,0x62,0x66,0xFE,0x00,0x00,0x00,0x00},
    {0x00,0x00,0xFE,0x66,0x62,0x68,0x78,0x68,0x60,0x60,0x60,0xF0,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x3C,0x66,0xC2,0xC0,0xC0,0xDE,0xC6,0xC6,0x66,0x3A,0x00,0x00,0x00,0x00},
    {0x00,0x00,0xC6,0xC6,0xC6,0xC6,0xFE,0xC6,0xC6,0xC6,0xC6,0xC6,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x3C,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x3C,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x1E,0x0C,0x0C,0x0C,0x0C,0x0C,0xCC,0xCC,0xCC,0x78,0x00,0x00,0x00,0x00},
    {0x00,0x00,0xE6,0x66,0x6C,0x6C,0x78,0x78,0x6C,0x66,0x66,0xE6,0x00,0x00,0x00,0x00},
    {0x00,0x00,0xF0,0x60,0x60,0x60,0x60,0x60,0x60,0x62,0x66,0xFE,0x00,0x00,0x00,0x00},
    {0x00,0x00,0xC6,0xEE,0xFE,0xFE,0xD6,0xC6,0xC6,0xC6,0xC6,0xC6,0x00,0x00,0x00,0x00},
    {0x00,0x00,0xC6,0xE6,0xF6,0xFE,0xDE,0xCE,0xC6,0xC6,0xC6,0xC6,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x7C,0xC6,0xC6,0xC6,0xC6,0xC6,0xC6,0xC6,0xC6,0x7C,0x00,0x00,0x00,0x00},
    {0x00,0x00,0xFC,0x66,0x66,0x66,0x7C,0x60,0x60,0x60,0x60,0xF0,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x7C,0xC6,0xC6,0xC6,0xC6,0xC6,0xC6,0xD6,0xDE,0x7C,0x0C,0x0E,0x00,0x00},
    {0x00,0x00,0xFC,0x66,0x66,0x66,0x7C,0x6C,0x66,0x66,0x66,0xE6,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x7C,0xC6,0xC6,0x60,0x38,0x0C,0x06,0xC6,0xC6,0x7C,0x00,0x00,0x00,0x00},
    {0x00,0x00,0xFF,0xDB,0x99,0x18,0x18,0x18,0x18,0x18,0x18,0x3C,0x00,0x00,0x00,0x00},
    {0x00,0x00,0xC6,0xC6,0xC6,0xC6,0xC6,0xC6,0xC6,0xC6,0xC6,0x7C,0x00,0x00,0x00,0x00},
    {0x00,0x00,0xC6,0xC6,0xC6,0xC6,0xC6,0xC6,0xC6,0x6C,0x38,0x10,0x00,0x00,0x00,0x00},
    {0x00,0x00,0xC6,0xC6,0xC6,0xC6,0xC6,0xD6,0xD6,0xFE,0x6C,0x6C,0x00,0x00,0x00,0x00},
    {0x00,0x00,0xC6,0xC6,0x6C,0x7C,0x38,0x38,0x7C,0x6C,0xC6,0xC6,0x00,0x00,0x00,0x00},
    {0x00,0x00,0xCC,0xCC,0xCC,0xCC,0x78,0x30,0x30,0x30,0x30,0x78,0x00,0x00,0x00,0x00},
    {0x00,0x00,0xFE,0xC6,0x86,0x0C,0x18,0x30,0x60,0xC2,0xC6,0xFE,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x3C,0x30,0x30,0x30,0x30,0x30,0x30,0x30,0x30,0x3C,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x80,0xC0,0xE0,0x70,0x38,0x1C,0x0E,0x06,0x02,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x3C,0x0C,0x0C,0x0C,0x0C,0x0C,0x0C,0x0C,0x0C,0x3C,0x00,0x00,0x00,0x00},
    {0x10,0x38,0x6C,0xC6,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xFF,0x00,0x00,0x00},
    {0x00,0x30,0x18,0x0C,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x00,0x00,0x78,0x0C,0x7C,0xCC,0xCC,0xCC,0x76,0x00,0x00,0x00,0x00},
    {0x00,0x00,0xE0,0x60,0x60,0x78,0x6C,0x66,0x66,0x66,0x66,0x7C,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x00,0x00,0x7C,0xC6,0xC0,0xC0,0xC0,0xC6,0x7C,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x1C,0x0C,0x0C,0x3C,0x6C,0xCC,0xCC,0xCC,0xCC,0x76,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x00,0x00,0x7C,0xC6,0xFE,0xC0,0xC0,0xC6,0x7C,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x1C,0x36,0x32,0x30,0x78,0x30,0x30,0x30,0x30,0x78,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x00,0x00,0x76,0xCC,0xCC,0xCC,0xCC,0xCC,0x7C,0x0C,0xCC,0x78,0x00},
    {0x00,0x00,0xE0,0x60,0x60,0x6C,0x76,0x66,0x66,0x66,0x66,0xE6,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x18,0x18,0x00,0x38,0x18,0x18,0x18,0x18,0x18,0x3C,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x06,0x06,0x00,0x0E,0x06,0x06,0x06,0x06,0x06,0x06,0x66,0x66,0x3C,0x00},
    {0x00,0x00,0xE0,0x60,0x60,0x66,0x6C,0x78,0x78,0x6C,0x66,0xE6,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x38,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x3C,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x00,0x00,0xEC,0xFE,0xD6,0xD6,0xD6,0xD6,0xC6,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x00,0x00,0xDC,0x66,0x66,0x66,0x66,0x66,0x66,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x00,0x00,0x7C,0xC6,0xC6,0xC6,0xC6,0xC6,0x7C,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x00,0x00,0xDC,0x66,0x66,0x66,0x66,0x66,0x7C,0x60,0x60,0xF0,0x00},
    {0x00,0x00,0x00,0x00,0x00,0x76,0xCC,0xCC,0xCC,0xCC,0xCC,0x7C,0x0C,0x0C,0x1E,0x00},
    {0x00,0x00,0x00,0x00,0x00,0xDC,0x76,0x66,0x60,0x60,0x60,0xF0,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x00,0x00,0x7C,0xC6,0x60,0x38,0x0C,0xC6,0x7C,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x10,0x30,0x30,0xFC,0x30,0x30,0x30,0x30,0x36,0x1C,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x00,0x00,0xCC,0xCC,0xCC,0xCC,0xCC,0xCC,0x76,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x00,0x00,0xC6,0xC6,0xC6,0xC6,0x6C,0x38,0x10,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x00,0x00,0xC6,0xC6,0xD6,0xD6,0xD6,0xFE,0x6C,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x00,0x00,0xC6,0x6C,0x38,0x38,0x38,0x6C,0xC6,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x00,0x00,0x00,0xC6,0xC6,0xC6,0xC6,0xC6,0xC6,0x7E,0x06,0x0C,0xF8,0x00},
    {0x00,0x00,0x00,0x00,0x00,0xFE,0xCC,0x18,0x30,0x60,0xC6,0xFE,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x0E,0x18,0x18,0x18,0x70,0x18,0x18,0x18,0x18,0x0E,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x18,0x18,0x18,0x18,0x00,0x18,0x18,0x18,0x18,0x18,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x70,0x18,0x18,0x18,0x0E,0x18,0x18,0x18,0x18,0x70,0x00,0x00,0x00,0x00},
    {0x00,0x00,0x76,0xDC,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00},
};
// clang-format on

static const int FONT_CHAR_W = 8;
static const int FONT_CHAR_H = 16;
static const int FONT_FIRST_CHAR = 32;
static const int FONT_LAST_CHAR = 126;

static GLuint createFontTexture() {
    const int numChars = FONT_LAST_CHAR - FONT_FIRST_CHAR + 1;
    const int atlasW = FONT_CHAR_W * numChars;
    const int atlasH = FONT_CHAR_H;
    std::vector<uint8_t> pixels(atlasW * atlasH, 0);
    for (int ch = 0; ch < numChars; ch++) {
        for (int row = 0; row < FONT_CHAR_H; row++) {
            uint8_t bits = kFont8x16[ch][row];
            for (int col = 0; col < FONT_CHAR_W; col++) {
                if ((bits >> (7 - col)) & 1)
                    pixels[row * atlasW + ch * FONT_CHAR_W + col] = 255;
            }
        }
    }
    GLuint tex;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE, atlasW, atlasH, 0,
                 GL_LUMINANCE, GL_UNSIGNED_BYTE, pixels.data());
    return tex;
}

// Text rendering shader
static const char TEXT_VERTEX_SHADER[] = R"(
    attribute vec4 aPosition;
    attribute vec2 aTexCoord;
    varying vec2 vTexCoord;
    void main() { gl_Position = aPosition; vTexCoord = aTexCoord; }
)";
static const char TEXT_FRAGMENT_SHADER[] = R"(
    precision mediump float;
    varying vec2 vTexCoord;
    uniform sampler2D uTexture;
    uniform vec4 uColor;
    void main() {
        float alpha = texture2D(uTexture, vTexCoord).r;
        gl_FragColor = vec4(uColor.rgb, uColor.a * alpha);
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

static GLuint sTextProgram = 0;
static GLint  sTextLocPosition = -1;
static GLint  sTextLocTexCoord = -1;
static GLint  sTextLocTexture = -1;
static GLint  sTextLocColor = -1;
static GLuint sFontTexture = 0;

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
      mScrollOffset(0.0f),
      mScrollDir(1),
      mScrollPause(0),
      mLastScrolledIdx(-1),
      mSelectHeld(false),
      mBrightness(128), mMaxBrightness(255),
      mShowBrightnessBar(false), mBrightnessBarTimer(0),
      mCurrentEffect(1),
      mEffectTime(0.0f) {
    mSession = new SurfaceComposerClient();
    srand(elapsedRealtime());
    memset(mParticles, 0, sizeof(mParticles));
    // Randomize starting effect (skip index 0 which is "None")
    sActiveEffectIdx = 1 + (rand() % (kNumActiveEffects - 1));
    mCurrentEffect = kActiveEffects[sActiveEffectIdx];
}

NanoMenu::~NanoMenu() {
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

void NanoMenu::adjustBrightness(int direction) {
    int step = mMaxBrightness / 10;
    if (step < 1) step = 1;
    mBrightness += step * direction;
    if (mBrightness < 1) mBrightness = 1;
    if (mBrightness > mMaxBrightness) mBrightness = mMaxBrightness;
    writeSysfsInt("/sys/class/leds/lcd-backlight/brightness", mBrightness);
    // Sync brightness to persist property (shared with Android)
    char buf[32];
    snprintf(buf, sizeof(buf), "%d", mBrightness);
    property_set("persist.gammaos.nano.brightness", buf);
    mShowBrightnessBar = true;
    mBrightnessBarTimer = 90; // ~1.5s at 60fps
}

void NanoMenu::buildMenu() {
    mMenuItems.clear();
    mMenuItems.push_back({"RetroArch (Nano)"});
    mMenuItems.push_back({"Recently Played"});
    mMenuItems.push_back({"Boot Android"});
    mMenuItems.push_back({"Recovery Mode"});
    mMenuItems.push_back({"Safe Mode"});
    mMenuItems.push_back({"Reboot"});
    mMenuItems.push_back({"Power Off"});
    mSelectedIndex = 0;
    mMenuState = MENU_MAIN;
    mRecentSelectedIndex = 0;

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
        mFooter = "DPAD/VOL: Nav | A/PWR: Select | B: Back | SEL+VOL: Brightness";
    } else {
        mTitle = "GammaOS Nano";
        for (const auto& item : mMenuItems) {
            mDisplayItems.push_back(item.label);
        }
        mSubtitle = "v0.1 - Proof of Concept";
        char buf[160];
        snprintf(buf, sizeof(buf),
                 "DPAD/VOL: Nav | A/PWR: Select | SEL+VOL: Brightness | X: FX [%s]",
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

void NanoMenu::handleBack() {
    if (mMenuState == MENU_RECENT) {
        mMenuState = MENU_MAIN;
        mRecentSelectedIndex = 0;
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
        // Flag so next nano menu restart returns to Recently Played
        property_set("sys.gammaos.nano.return_recent", "1");
        property_set("service.bootanim.nano_retroarch", "1");
        // Don't exit yet — wait for the select key to be released so the
        // key-up event passes through Android's InputReader before RetroArch
        // gets focus. Otherwise the A press leaks to RetroArch as a phantom input.
        mWaitForRelease = true;
        return;
    }

    // Main menu
    ALOGD("Select item %d: %s", mSelectedIndex, mMenuItems[mSelectedIndex].label.c_str());
    const auto& label = mMenuItems[mSelectedIndex].label;
    if (label == "RetroArch (Nano)") {
        property_set("service.bootanim.nano_retroarch", "1");
        mWaitForRelease = true;
    } else if (label == "Recently Played") {
        if (!mStorageReady) return; // greyed out, ignore
        // Load playlist from RetroArch's content_history.lpl (needs CE unlock)
        loadRecentPlaylist();
        mMenuState = MENU_RECENT;
        mRecentSelectedIndex = 0;
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
        property_set("service.bootanim.nano_action", "reboot");
    } else if (label == "Power Off") {
        property_set("service.bootanim.nano_action", "shutdown");
    }
}

void NanoMenu::handleUp() {
    if (mMenuState == MENU_RECENT) {
        if (mRecentSelectedIndex > 0) mRecentSelectedIndex--;
    } else {
        if (mSelectedIndex > 0) {
            mSelectedIndex--;
            // Skip greyed-out "Recently Played" when storage isn't ready
            if (!mStorageReady && mSelectedIndex < (int)mMenuItems.size()
                && mMenuItems[mSelectedIndex].label == "Recently Played"
                && mSelectedIndex > 0) {
                mSelectedIndex--;
            }
        }
    }
}

void NanoMenu::handleDown() {
    if (mMenuState == MENU_RECENT) {
        int maxIdx = (int)mRecentEntries.size(); // "< Back" is at this index
        if (mRecentSelectedIndex < maxIdx) mRecentSelectedIndex++;
    } else {
        int last = (int)mMenuItems.size() - 1;
        if (mSelectedIndex < last) {
            mSelectedIndex++;
            // Skip greyed-out "Recently Played" when storage isn't ready
            if (!mStorageReady && mSelectedIndex < (int)mMenuItems.size()
                && mMenuItems[mSelectedIndex].label == "Recently Played"
                && mSelectedIndex < last) {
                mSelectedIndex++;
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
                    && (ev.code == KEY_POWER || ev.code == KEY_ENTER
                        || ev.code == BTN_SOUTH)) {
                    ALOGD("NanoMenu: select key released, exiting now");
                    mExitRequested = true;
                }
                continue; // discard all other events while waiting
            }
            // Track SELECT button state
            if (ev.type == EV_KEY && ev.code == BTN_SELECT) {
                mSelectHeld = (ev.value != 0);
            }
            if (ev.type == EV_KEY && (ev.value == 1 || ev.value == 2)) {
                // value 1 = press, value 2 = repeat (for hold-to-adjust)
                if (mSelectHeld && (ev.code == KEY_VOLUMEUP || ev.code == KEY_VOLUMEDOWN)) {
                    adjustBrightness(ev.code == KEY_VOLUMEUP ? 1 : -1);
                } else if (ev.value == 1) {
                    // Only handle menu nav on initial press, not repeat
                    switch (ev.code) {
                    case KEY_VOLUMEUP: case KEY_UP:
                        handleUp(); break;
                    case KEY_VOLUMEDOWN: case KEY_DOWN:
                        handleDown(); break;
                    case KEY_POWER: case KEY_ENTER: case BTN_SOUTH:
                        handleSelect(); break;
                    case BTN_EAST: case KEY_BACK:
                        handleBack(); break;
                    case BTN_NORTH:
                        sActiveEffectIdx = (sActiveEffectIdx + 1) % kNumActiveEffects;
                        mCurrentEffect = kActiveEffects[sActiveEffectIdx];
                        if (mCurrentEffect >= 1 && mCurrentEffect <= 10) initEffects();
                        mDisplayDirty = true; // footer shows effect name
                        ALOGD("Effect: %d (%s)", mCurrentEffect, kEffectNames[mCurrentEffect]);
                        break;
                    default: break;
                    }
                }
            }
            if (ev.type == EV_ABS) {
                if (ev.code == ABS_HAT0Y) {
                    if (ev.value < 0) handleUp();
                    else if (ev.value > 0) handleDown();
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
    // If "Boot Android" was selected, skip the nano menu entirely.
    char skip[PROPERTY_VALUE_MAX] = {};
    property_get("persist.bootanim.skip_nano", skip, "");
    if (!strcmp(skip, "1")) {
        ALOGI("GammaOS Nano: skip_nano=1, exiting for full Android boot");
        return INVALID_OPERATION;
    }

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
    t.setDisplayProjection(mDisplayToken, ui::ROTATION_0, forcedRes, physRes);
    t.setLayer(control, 0x40000000).apply();

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
    initShaders();
    buildMenu();
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
        writeSysfsInt("/sys/class/leds/lcd-backlight/brightness", mBrightness);
    } else {
        mBrightness = readSysfsInt("/sys/class/leds/lcd-backlight/brightness", mMaxBrightness / 2);
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
        sTextProgram = linkProgram(vs, fs);
        sTextLocPosition = glGetAttribLocation(sTextProgram, "aPosition");
        sTextLocTexCoord = glGetAttribLocation(sTextProgram, "aTexCoord");
        sTextLocTexture  = glGetUniformLocation(sTextProgram, "uTexture");
        sTextLocColor    = glGetUniformLocation(sTextProgram, "uColor");
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
    sFontTexture = createFontTexture();
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

// Batched text: builds vertex+UV arrays for the entire string, draws once.
// Max 256 chars per call (1536 vertices). Reduces ~100 draw calls to ~10.
static const int TEXT_MAX_CHARS = 256;
static GLfloat sTextVerts[TEXT_MAX_CHARS * 6 * 2];
static GLfloat sTextUVs[TEXT_MAX_CHARS * 6 * 2];

static void drawText(const char* str, float px, float py,
                     float scale, float screenW, float screenH,
                     float r, float g, float b, float a) {
    if (!str || !*str) return;
    const int numCharsInAtlas = FONT_LAST_CHAR - FONT_FIRST_CHAR + 1;
    const float charTexW = 1.0f / numCharsInAtlas;
    float charW = FONT_CHAR_W * scale;
    float charH = FONT_CHAR_H * scale;
    float invW = 2.0f / screenW, invH = 2.0f / screenH;
    int n = 0;
    for (const char* p = str; *p && n < TEXT_MAX_CHARS; p++, n++) {
        char ch = *p;
        if (ch < FONT_FIRST_CHAR || ch > FONT_LAST_CHAR) ch = '?';
        int idx = ch - FONT_FIRST_CHAR;
        float u0 = idx * charTexW, u1 = u0 + charTexW;
        float x0 = px * invW - 1.0f;
        float y1 = 1.0f - py * invH;
        float x1 = (px + charW) * invW - 1.0f;
        float y0 = 1.0f - (py + charH) * invH;
        int vi = n * 12;
        sTextVerts[vi]= x0; sTextVerts[vi+1]= y0;
        sTextVerts[vi+2]= x1; sTextVerts[vi+3]= y0;
        sTextVerts[vi+4]= x1; sTextVerts[vi+5]= y1;
        sTextVerts[vi+6]= x1; sTextVerts[vi+7]= y1;
        sTextVerts[vi+8]= x0; sTextVerts[vi+9]= y1;
        sTextVerts[vi+10]= x0; sTextVerts[vi+11]= y0;
        int ui = n * 12;
        sTextUVs[ui]= u0; sTextUVs[ui+1]= 1;
        sTextUVs[ui+2]= u1; sTextUVs[ui+3]= 1;
        sTextUVs[ui+4]= u1; sTextUVs[ui+5]= 0;
        sTextUVs[ui+6]= u1; sTextUVs[ui+7]= 0;
        sTextUVs[ui+8]= u0; sTextUVs[ui+9]= 0;
        sTextUVs[ui+10]= u0; sTextUVs[ui+11]= 1;
        px += charW;
    }
    glUseProgram(sTextProgram);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, sFontTexture);
    glUniform1i(sTextLocTexture, 0);
    glUniform4f(sTextLocColor, r, g, b, a);
    glVertexAttribPointer(sTextLocPosition, 2, GL_FLOAT, GL_FALSE, 0, sTextVerts);
    glEnableVertexAttribArray(sTextLocPosition);
    glVertexAttribPointer(sTextLocTexCoord, 2, GL_FLOAT, GL_FALSE, 0, sTextUVs);
    glEnableVertexAttribArray(sTextLocTexCoord);
    glDrawArrays(GL_TRIANGLES, 0, n * 6);
    glDisableVertexAttribArray(sTextLocPosition);
    glDisableVertexAttribArray(sTextLocTexCoord);
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

    // Poll storage while in Recent submenu — auto-load once available
    if (mMenuState == MENU_RECENT && !mStorageReady) {
        if (access("/data/media/0", R_OK) == 0) {
            mStorageReady = true;
            loadRecentPlaylist();
            mDisplayDirty = true;
            rebuildDisplayItems();
        }
    }

    float menuScale = 3.0f * sf;
    int currentSelected = (mMenuState == MENU_RECENT) ? mRecentSelectedIndex : mSelectedIndex;
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
    int layoutItems = 7; // main menu item count — used as the reference layout
    float menuContentH = titleH + gap1 + subH + gap2 + sepH + gap3
                        + layoutItems * itemH + (layoutItems - 1) * itemSpacing;
    float startY = (mHeight - menuContentH) / 6.0f;
    if (startY < 10.0f) startY = 10.0f;

    // Title
    float titleW = mTitle.size() * FONT_CHAR_W * titleScale;
    float titleX = (mWidth - titleW) / 2.0f;
    float titleY = startY;
    drawText(mTitle.c_str(), titleX, titleY, titleScale,
             mWidth, mHeight, 0.0f, 0.85f, 1.0f, 1.0f);

    // Subtitle
    float subW = mSubtitle.size() * FONT_CHAR_W * subScale;
    float subX = (mWidth - subW) / 2.0f;
    float subY = titleY + titleH + gap1;
    drawText(mSubtitle.c_str(), subX, subY, subScale,
             mWidth, mHeight, 0.5f, 0.5f, 0.6f, 1.0f);

    // Separator
    float sepY = subY + subH + gap2;
    drawQuad(mWidth * 0.1f, sepY, mWidth * 0.8f, sepH, 0.3f, 0.3f, 0.4f, 1.0f);

    // Menu items
    float menuStartY = sepY + sepH + gap3;
    float menuX = mWidth * 0.15f;

    // Available text width for menu items (from menuX to 85% of screen)
    float maxTextW = mWidth * 0.85f - menuX;
    float charW = FONT_CHAR_W * menuScale;

    for (int i = 0; i < numItems; i++) {
        float itemY = menuStartY + i * (itemH + itemSpacing);
        bool selected = (i == currentSelected);

        // Grey out "Recently Played" in main menu when storage isn't ready
        bool greyed = (mMenuState == MENU_MAIN && !mStorageReady
                       && mDisplayItems[i] == "Recently Played");

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
        drawText(prefix, menuX, itemY, menuScale, mWidth, mHeight, r, g, b, 1.0f);

        // Content area: from after prefix to end of blue selection bar
        float contentLeft = menuX + prefixW;
        float contentRight = mWidth * 0.90f; // right edge of selection bar
        float contentW = contentRight - contentLeft;
        float textW = mDisplayItems[i].size() * charW;

        // Horizontal scroll for selected items that overflow (Recently Played)
        float drawX = contentLeft;
        bool scrolling = false;
        if (selected && !greyed && mMenuState == MENU_RECENT
            && textW > contentW && i < (int)mRecentEntries.size()) {
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
            || (mMenuState == MENU_RECENT && i < (int)mRecentEntries.size()
                && textW > contentW);
        if (needsClip) {
            glEnable(GL_SCISSOR_TEST);
            glScissor((int)contentLeft, 0, (int)contentW, mHeight);
        }
        drawText(mDisplayItems[i].c_str(), drawX, itemY, menuScale,
                 mWidth, mHeight, r, g, b, 1.0f);
        if (needsClip) {
            glDisable(GL_SCISSOR_TEST);
        }
    }

    // Footer
    float footW = mFooter.size() * FONT_CHAR_W * footScale;
    float footX = (mWidth - footW) / 2.0f;
    float footY = mHeight - footH - startY;
    drawText(mFooter.c_str(), footX, footY, footScale, mWidth, mHeight, 0.4f, 0.4f, 0.5f, 1.0f);

    // Brightness bar overlay
    renderBrightnessBar();

    glDisable(GL_BLEND);
    eglSwapBuffers(mDisplay, mSurface);
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
    float iconW = 8 * FONT_CHAR_W * iconScale; // "*" sun symbol
    float bgW = iconW + pad + barW + pad + 50.0f * sf;
    float bgH = barH + pad * 2;
    float bgX = (mWidth - bgW) / 2.0f;
    float bgY = pad;

    // Background
    drawQuad(bgX, bgY, bgW, bgH, 0.0f, 0.0f, 0.0f, 0.8f);

    // Sun icon "*"
    float iconX = bgX + pad;
    float iconY = bgY + (bgH - FONT_CHAR_H * iconScale) / 2.0f;
    drawText("*", iconX, iconY, iconScale, mWidth, mHeight, 1.0f, 0.9f, 0.3f, 1.0f);

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
    drawText(pctStr, textX, textY, textScale, mWidth, mHeight, 1.0f, 1.0f, 1.0f, 1.0f);
}

// ---------------------------------------------------------------------------
// Main loop
// ---------------------------------------------------------------------------

bool NanoMenu::threadLoop() {
    ALOGD("NanoMenu: entering main loop");

    int exitCheckCounter = 0;
    while (!exitPending() && !mExitRequested) {
        pollInput();
        checkInputHotplug();

        // Adaptive framerate: 20fps for effects, ~10fps when idle.
        bool animating = (mCurrentEffect != 0) || mShowBrightnessBar
                         || mWaitForRelease
                         || (mMenuState == MENU_RECENT && mScrollOffset > 0.0f);
        int frameTimeUs = animating ? 50000 : 100000; // 20fps vs 10fps
        float dt = animating ? (1.0f / 20.0f) : (1.0f / 10.0f);
        mEffectTime += dt;
        render();
        usleep(frameTimeUs);

        // Check every ~0.5s if an external trigger requested exit
        int exitCheckInterval = animating ? 30 : 5; // 30*16ms or 5*100ms
        if (++exitCheckCounter >= exitCheckInterval) {
            exitCheckCounter = 0;
            char val[PROPERTY_VALUE_MAX] = {};
            property_get("service.bootanim.exit", val, "0");
            if (!strcmp(val, "1")) {
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
        }
    }

    // Transition: grab input, show "Loading...", wait for RetroArch to start.
    // Keeps the nano surface alive so there's no blank screen, and prevents
    // InputReader from queuing phantom events during the transition.
    for (int fd : mInputFds) {
        ioctl(fd, EVIOCGRAB, 1);
    }
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
            float loadW = strlen(loadMsg) * FONT_CHAR_W * loadScale;
            float loadX = (mWidth - loadW) / 2.0f;
            float loadY = (mHeight - FONT_CHAR_H * loadScale) / 2.0f;
            drawText(loadMsg, loadX, loadY, loadScale,
                     mWidth, mHeight, 0.6f, 0.6f, 0.7f, 1.0f);
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

} // namespace android
