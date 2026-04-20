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

// Shader source strings, program compile/link helpers, effect tables, and
// the NanoMenu::initShaders() implementation. Extracted from NanoMenu.cpp to
// keep rendering setup decoupled from the main thread loop.

#define LOG_TAG "GammaOSNano"

#include <utils/Log.h>

#include <GLES2/gl2.h>

#include "NanoMenu.h"
#include "NanoMenuDrm.h"
#include "NanoMenuShaders.h"

namespace android {

// ---------------------------------------------------------------------------
// Shaders
// ---------------------------------------------------------------------------

const char VERTEX_SHADER[] = R"(
    attribute vec4 aPosition;
    uniform vec4 uColor;
    uniform mat2 uRotation;
    varying vec4 vColor;
    void main() {
        gl_Position = vec4(uRotation * aPosition.xy, aPosition.zw);
        vColor = uColor;
    }
)";

const char FRAGMENT_SHADER[] = R"(
    precision mediump float;
    varying vec4 vColor;
    void main() {
        gl_FragColor = vColor;
    }
)";

// Batched particle shader — per-vertex position + color, single draw call
const char PARTICLE_VERTEX_SHADER[] = R"(
    attribute vec2 aPosition;
    attribute vec4 aColor;
    uniform mat2 uRotation;
    varying vec4 vColor;
    void main() {
        gl_Position = vec4(uRotation * aPosition, 0.0, 1.0);
        vColor = aColor;
    }
)";
const char PARTICLE_FRAGMENT_SHADER[] = R"(
    precision mediump float;
    varying vec4 vColor;
    void main() { gl_FragColor = vColor; }
)";

// Fullscreen procedural effect shader (effects 11-20)
const char FX_VERTEX_SHADER[] = R"(
    attribute vec4 aPosition;
    uniform mat2 uRotation;
    void main() { gl_Position = vec4(uRotation * aPosition.xy, aPosition.zw); }
)";

const char FX_FRAGMENT_SHADER[] = R"(
#ifdef GL_FRAGMENT_PRECISION_HIGH
    precision highp float;
#else
    precision mediump float;
#endif
    uniform float uTime;
    uniform vec2 uResolution;
    uniform int uEffect;
    uniform float uCoordSwap;
    // GammaOS DRM PRIME path flips vertex Y via uRotation so AHB scanout
    // memory ordering matches the panel. gl_FragCoord is NOT affected by
    // vertex transforms, so orientation-dependent effects (Fire, Aurora,
    // etc.) render upside-down. uYFlip=1.0 in PRIME mode flips the
    // fragment-space Y so the designer's "0=bottom" convention still holds.
    uniform float uYFlip;

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
        fc.y = mix(fc.y, uResolution.y - fc.y, uYFlip);
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
const char XMB_FRAGMENT_SHADER[] = R"(
#ifdef GL_FRAGMENT_PRECISION_HIGH
    precision highp float;
#else
    precision mediump float;
#endif
    uniform float uTime;
    uniform vec2 uResolution;
    uniform float uCoordSwap;
    uniform float uYFlip;

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
        fc.y = mix(fc.y, uResolution.y - fc.y, uYFlip);
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

// Text rendering shader (per-vertex color for emoji support)
const char TEXT_VERTEX_SHADER[] = R"(
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
const char TEXT_FRAGMENT_SHADER[] = R"(
    precision mediump float;
    varying vec2 vTexCoord;
    varying vec4 vColor;
    uniform sampler2D uTexture;
    void main() {
        vec4 texel = texture2D(uTexture, vTexCoord);
        gl_FragColor = texel * vColor;
    }
)";

// ---------------------------------------------------------------------------
// Shader compile + link helpers
// ---------------------------------------------------------------------------

GLuint compileShader(GLenum type, const char* source) {
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

GLuint linkProgram(GLuint vs, GLuint fs) {
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
// Effect name + active-list tables
// ---------------------------------------------------------------------------

const char* kEffectNames[NUM_EFFECTS + 1] = {
    "None",
    "Snow", "Rain", "Confetti", "Sparks", "Fireflies",
    "Bubbles", "Starfield", "Embers", "Leaves", "Dust",
    "Plasma", "Static", "Scanlines", "Mosaic", "Matrix",
    "Fire", "Aurora", "Ripple", "Checkerboard", "Spiral",
    "XMB"
};

// Active effects — removed: Dust(10), Static(12), Scanlines(13), Mosaic(14), Matrix(15)
const int kActiveEffects[] = {
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
const int kNumActiveEffects = sizeof(kActiveEffects) / sizeof(kActiveEffects[0]);

// Index into kActiveEffects (NOT the effect ID itself). Resolved from the
// persist.gammaos.nano.wallpaper prop in the NanoMenu ctor.
int sActiveEffectIdx = 0;

// ---------------------------------------------------------------------------
// Shader program initialization
// ---------------------------------------------------------------------------

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
            mFxLocYFlip      = glGetUniformLocation(mFxProgram, "uYFlip");
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
            mXmbLocYFlip      = glGetUniformLocation(mXmbProgram, "uYFlip");
            glDeleteShader(vs); glDeleteShader(fs);
        }
    } else {
        mParticleProgram = 0; mFxProgram = 0; mXmbProgram = 0;
        mParticleLocPosition = mParticleLocColor = mParticleLocRotation = -1;
        mFxLocPosition = mFxLocTime = mFxLocResolution = -1;
        mFxLocEffect = mFxLocRotation = mFxLocCoordSwap = mFxLocYFlip = -1;
        mXmbLocPosition = mXmbLocTime = mXmbLocResolution = -1;
        mXmbLocRotation = mXmbLocCoordSwap = mXmbLocYFlip = -1;
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

} // namespace android
