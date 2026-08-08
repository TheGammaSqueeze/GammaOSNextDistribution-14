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
    // Fragment-space X flip: user flip_h correction mirrors vertex geometry
    // via uRotation, but gl_FragCoord is unaffected by vertex transforms, so
    // orientation-dependent effects would otherwise read the unflipped screen
    // X. uXFlip=1.0 mirrors fragment-space X so procedural effects stay
    // consistent with the vertex-mirrored image.
    uniform float uXFlip;

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
        fc.x = mix(fc.x, uResolution.x - fc.x, uXFlip);
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
    uniform float uXFlip;

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
        fc.x = mix(fc.x, uResolution.x - fc.x, uXFlip);
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
    #extension GL_OES_standard_derivatives : enable
    precision mediump float;
    varying vec2 vTexCoord;
    varying vec4 vColor;
    uniform sampler2D uTexture;
    uniform float uSharp;    // 0 = plain bilinear; >0 = crisp analytic edge AA (glyphs)
    uniform vec2  uSharpUp;  // >0: sharp-bilinear upscale; xy = source size in TEXELS
    void main() {
        vec2 uv = vTexCoord;
        if (uSharpUp.x > 0.0) {
            // Sharp-bilinear: keep GL_LINEAR (no aliasing) but pre-warp UV toward
            // texel centres by the on-screen magnification, so a half-res icon
            // upscales crisp instead of soft. 1 fetch + a few ALU ops.
            vec2 ts = 1.0 / uSharpUp;          // texel size in UV
            vec2 tc = uv * uSharpUp;           // texel-space coord
            vec2 tf = fract(tc);
            vec2 w  = fwidth(tc);              // texels per dest pixel (<1 when magnifying)
            vec2 f  = clamp((tf - 0.5) / max(w, vec2(1e-4)) + 0.5, 0.0, 1.0);
            uv = (floor(tc) + f) * ts;         // keep the whole texel, sharpen the fraction
        }
        vec4 texel = texture2D(uTexture, uv);
        float a = texel.a;
        if (uSharp > 0.001) {
            // The atlas stores antialiased coverage in alpha; trilinear sampling
            // keeps it clean but soft. Re-sharpen the 0.5 coverage contour to a
            // ~1px-wide edge using the screen-space gradient, so glyphs read crisp
            // at any scale instead of blurry. uSharp scales the edge width
            // (smaller = sharper). Non-AA text skips this and is unchanged.
            float sw = max(fwidth(a) * uSharp, 1.0 / 256.0);
            a = smoothstep(0.5 - sw, 0.5 + sw, a);
        }
        gl_FragColor = vec4(texel.rgb, a) * vColor;
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
    "XMB", "XMB Wave"
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
    21, // XMB (procedural ribbon)
    22, // XMB Wave (real ported PS3 captured-geometry wave + per-month gradient)
};
const int kNumActiveEffects = sizeof(kActiveEffects) / sizeof(kActiveEffects[0]);

// Index into kActiveEffects (NOT the effect ID itself). Resolved from the
// persist.gammaos.nano.wallpaper prop in the NanoMenu ctor.
int sActiveEffectIdx = 0;

// ---------------------------------------------------------------------------
// Rounded-rect solid shader (OSK keys). aLocal carries the centered pixel
// coordinate so the fragment evaluates a rounded-box SDF for crisp,
// resolution-independent corners with 1px anti-aliasing.
const char ROUND_VERTEX_SHADER[] = R"(
    attribute vec2 aPosition;
    attribute vec2 aLocal;
    uniform mat2 uRotation;
    varying vec2 vLocal;
    void main() {
        gl_Position = vec4(uRotation * aPosition, 0.0, 1.0);
        vLocal = aLocal;
    }
)";
const char ROUND_FRAGMENT_SHADER[] = R"(
    precision mediump float;
    varying vec2 vLocal;
    uniform vec2 uHalf;
    uniform float uRadius;
    uniform vec4 uColor;
    void main() {
        vec2 d = abs(vLocal) - (uHalf - vec2(uRadius));
        float dist = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - uRadius;
        float a = clamp(0.5 - dist, 0.0, 1.0);
        if (a <= 0.0) discard;
        gl_FragColor = vec4(uColor.rgb, uColor.a * a);
    }
)";

// Frosted-glass panel shader (final pass of the dual-Kawase blur). Samples the
// already-downsampled blur texture (uTex, produced by blurGlassChain) with an
// 8-tap Kawase tent upsample, darkens it (uTint), and clips to a rounded rect.
// The heavy smoothing happens in the downsample passes; this pass just spreads
// and bilinearly upscales the low-res result, which is what gives the smooth
// aero-glass look instead of the sparse-tap pixelation of a single full-res pass.
const char GLASS_VERTEX_SHADER[] = R"(
    attribute vec2 aPosition;
    attribute vec2 aLocal;
    attribute vec2 aTexCoord;
    uniform mat2 uRotation;
    varying vec2 vLocal;
    varying vec2 vTex;
    void main() {
        gl_Position = vec4(uRotation * aPosition, 0.0, 1.0);
        vLocal = aLocal;
        vTex = aTexCoord;
    }
)";
const char GLASS_FRAGMENT_SHADER[] = R"(
    precision mediump float;
    varying vec2 vLocal;
    varying vec2 vTex;
    uniform vec2 uHalf;
    uniform float uRadius;
    uniform sampler2D uTex;
    uniform vec2 uTexel;   // blur step (texcoords)
    uniform vec4 uTint;    // rgb = darken multiply, a = panel opacity
    uniform float uAlpha;  // fade in/out
    uniform float uTonemap; // >0: exp2 tonemap the source (linear scene) to display space
    void main() {
        // Rounded-rect SDF only when actually rounded; the full-screen submenu /
        // dialog backdrop passes radius 0, so skip the length()/SDF math there.
        // No discard: ra is folded into the output alpha (ra=0 -> transparent),
        // so the tile GPU keeps hidden-surface removal / early-Z for the pass.
        float ra = 1.0;
        if (uRadius > 0.0) {
            vec2 d = abs(vLocal) - (uHalf - vec2(uRadius));
            float dist = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - uRadius;
            ra = clamp(0.5 - dist, 0.0, 1.0);
        }
        // 4-tap bilinear box upsample of the pre-blurred low-res source. The
        // source is already heavily band-limited (box downsample), so 4 corner
        // taps match the old 8-tap tent at half the texture fetches - the main
        // per-frame full-screen cost of the frosted backdrop.
        vec2 o = uTexel;
        vec3 c  = texture2D(uTex, vTex + vec2(-o.x,  o.y)).rgb;
        c += texture2D(uTex, vTex + vec2( o.x,  o.y)).rgb;
        c += texture2D(uTex, vTex + vec2( o.x, -o.y)).rgb;
        c += texture2D(uTex, vTex + vec2(-o.x, -o.y)).rgb;
        c *= 0.25;
        // The wave blur samples ps3bg::workTex, which is the scene in LINEAR
        // (pre-tonemap) space. Apply the same exp2 tonemap the composite uses so
        // the blurred backdrop matches the displayed background (no brighter /
        // more-saturated shift). uTonemap = exposure/white * LOG2E (~1.6846).
        if (uTonemap > 0.0) c = vec3(1.0) - exp2(-c * uTonemap);
        c *= uTint.rgb;
        gl_FragColor = vec4(c, uTint.a * uAlpha * ra);
    }
)";

// Dual-Kawase DOWNSAMPLE pass. Renders a full-viewport quad into a half-size
// FBO texture, box-averaging a center tap (x4) plus four diagonal taps. Chaining
// these halves the resolution each time while every source pixel contributes,
// which is what produces a genuinely smooth (non-pixelated) blur.
const char DOWNSAMPLE_VERTEX_SHADER[] = R"(
    attribute vec2 aPosition;
    attribute vec2 aTexCoord;
    varying vec2 vTex;
    void main() {
        gl_Position = vec4(aPosition, 0.0, 1.0);
        vTex = aTexCoord;
    }
)";
const char DOWNSAMPLE_FRAGMENT_SHADER[] = R"(
    precision mediump float;
    varying vec2 vTex;
    uniform sampler2D uTex;
    uniform vec2 uHalfpixel;   // 1/srcW, 1/srcH
    uniform float uOffset;
    void main() {
        vec2 o = uHalfpixel * uOffset;
        vec3 sum = texture2D(uTex, vTex).rgb * 4.0;
        sum += texture2D(uTex, vTex - o).rgb;
        sum += texture2D(uTex, vTex + o).rgb;
        sum += texture2D(uTex, vTex + vec2(o.x, -o.y)).rgb;
        sum += texture2D(uTex, vTex - vec2(o.x, -o.y)).rgb;
        gl_FragColor = vec4(sum / 8.0, 1.0);
    }
)";

// Separable Gaussian program. Run TWICE per frame on the 1/8-res downsample:
// once horizontal (uDir = 1/w,0), once vertical (uDir = 0,1/h). A 9-tap Gaussian
// (sigma = 3 texels, taps -4..+4) folded into 5 bilinear fetches via the
// linear-sampling weight/offset trick, so it costs 5 texture2D calls per pass
// but covers a 9-texel kernel. A true H+V Gaussian has no triangular side lobes
// (the failure mode that ghosts sparse Kawase taps), so on the already
// band-limited 1/8 image it gives a heavy, perfectly smooth frost. mediump-safe,
// ES2-legal (no textureLod). Weights are renormalized to sum to EXACTLY 1.0
// (energy-preserving, no darkening): 0.153170 + 2*0.267542 + 2*0.155873 = 1.0.
const char GAUSS_VERTEX_SHADER[] = R"(
    attribute vec2 aPosition;
    attribute vec2 aTexCoord;
    varying vec2 vTex;
    void main() {
        gl_Position = vec4(aPosition, 0.0, 1.0);
        vTex = aTexCoord;
    }
)";
const char GAUSS_FRAGMENT_SHADER[] = R"(
    precision mediump float;
    varying vec2 vTex;
    uniform sampler2D uTex;
    uniform vec2 uDir;     // (1/srcW, 0) for H pass, (0, 1/srcH) for V pass
    void main() {
        vec2 o1 = uDir * 1.458430;
        vec2 o2 = uDir * 3.403985;
        vec3 c = texture2D(uTex, vTex).rgb * 0.153170;
        c += texture2D(uTex, vTex + o1).rgb * 0.267542;
        c += texture2D(uTex, vTex - o1).rgb * 0.267542;
        c += texture2D(uTex, vTex + o2).rgb * 0.155873;
        c += texture2D(uTex, vTex - o2).rgb * 0.155873;
        gl_FragColor = vec4(c, 1.0);
    }
)";

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
        mTextLocSharp    = glGetUniformLocation(mTextProgram, "uSharp");
        mTextLocSharpUp  = glGetUniformLocation(mTextProgram, "uSharpUp");
        glDeleteShader(vs); glDeleteShader(fs);
    }
    // Rounded-rect shader (OSK keys).
    {   GLuint vs = compileShader(GL_VERTEX_SHADER, ROUND_VERTEX_SHADER);
        GLuint fs = compileShader(GL_FRAGMENT_SHADER, ROUND_FRAGMENT_SHADER);
        mRoundProgram = linkProgram(vs, fs);
        mRoundLocPosition = glGetAttribLocation(mRoundProgram, "aPosition");
        mRoundLocLocal    = glGetAttribLocation(mRoundProgram, "aLocal");
        mRoundLocRotation = glGetUniformLocation(mRoundProgram, "uRotation");
        mRoundLocHalf     = glGetUniformLocation(mRoundProgram, "uHalf");
        mRoundLocRadius   = glGetUniformLocation(mRoundProgram, "uRadius");
        mRoundLocColor    = glGetUniformLocation(mRoundProgram, "uColor");
        glDeleteShader(vs); glDeleteShader(fs);
    }
    // Frosted-glass shader (OSK panel) + its framebuffer-snapshot texture.
    {   GLuint vs = compileShader(GL_VERTEX_SHADER, GLASS_VERTEX_SHADER);
        GLuint fs = compileShader(GL_FRAGMENT_SHADER, GLASS_FRAGMENT_SHADER);
        mGlassProgram = linkProgram(vs, fs);
        mGlassLocPosition   = glGetAttribLocation(mGlassProgram, "aPosition");
        mGlassLocLocal      = glGetAttribLocation(mGlassProgram, "aLocal");
        mGlassLocTexCoord   = glGetAttribLocation(mGlassProgram, "aTexCoord");
        mGlassLocRotation   = glGetUniformLocation(mGlassProgram, "uRotation");
        mGlassLocHalf       = glGetUniformLocation(mGlassProgram, "uHalf");
        mGlassLocRadius     = glGetUniformLocation(mGlassProgram, "uRadius");
        mGlassLocTexture    = glGetUniformLocation(mGlassProgram, "uTex");
        mGlassLocTexel      = glGetUniformLocation(mGlassProgram, "uTexel");
        mGlassLocTint       = glGetUniformLocation(mGlassProgram, "uTint");
        mGlassLocAlpha      = glGetUniformLocation(mGlassProgram, "uAlpha");
        mGlassLocTonemap    = glGetUniformLocation(mGlassProgram, "uTonemap");
        glDeleteShader(vs); glDeleteShader(fs);
        glGenTextures(1, &mGlassTex);
        glBindTexture(GL_TEXTURE_2D, mGlassTex);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        mGlassTexW = mGlassTexH = 0;
    }
    // Dual-Kawase downsample program (feeds the frosted-glass panel pass). The
    // FBO textures themselves are allocated lazily in blurGlassChain() once the
    // capture size is known.
    {   GLuint vs = compileShader(GL_VERTEX_SHADER, DOWNSAMPLE_VERTEX_SHADER);
        GLuint fs = compileShader(GL_FRAGMENT_SHADER, DOWNSAMPLE_FRAGMENT_SHADER);
        mGlassDownProgram = linkProgram(vs, fs);
        mGlassDownLocPosition  = glGetAttribLocation(mGlassDownProgram, "aPosition");
        mGlassDownLocTexCoord  = glGetAttribLocation(mGlassDownProgram, "aTexCoord");
        mGlassDownLocTexture   = glGetUniformLocation(mGlassDownProgram, "uTex");
        mGlassDownLocHalfpixel = glGetUniformLocation(mGlassDownProgram, "uHalfpixel");
        mGlassDownLocOffset    = glGetUniformLocation(mGlassDownProgram, "uOffset");
        glDeleteShader(vs); glDeleteShader(fs);
    }
    // Separable Gaussian program (H + V passes on the 1/8 downsample). Sits
    // ABOVE the sDrasticQrFastPath guard, like mGlassDownProgram, because the
    // OSK panel can appear over the drastic QR path too. If this ever fails to
    // compile (program == 0), blurGlassChain falls back to the box pyramid.
    {   GLuint vs = compileShader(GL_VERTEX_SHADER, GAUSS_VERTEX_SHADER);
        GLuint fs = compileShader(GL_FRAGMENT_SHADER, GAUSS_FRAGMENT_SHADER);
        mGlassGaussProgram = linkProgram(vs, fs);
        mGlassGaussLocPosition = glGetAttribLocation(mGlassGaussProgram, "aPosition");
        mGlassGaussLocTexCoord = glGetAttribLocation(mGlassGaussProgram, "aTexCoord");
        mGlassGaussLocTexture  = glGetUniformLocation(mGlassGaussProgram, "uTex");
        mGlassGaussLocDir      = glGetUniformLocation(mGlassGaussProgram, "uDir");
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
            mFxLocXFlip      = glGetUniformLocation(mFxProgram, "uXFlip");
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
            mXmbLocXFlip      = glGetUniformLocation(mXmbProgram, "uXFlip");
            glDeleteShader(vs); glDeleteShader(fs);
        }
    } else {
        mParticleProgram = 0; mFxProgram = 0; mXmbProgram = 0;
        mParticleLocPosition = mParticleLocColor = mParticleLocRotation = -1;
        mFxLocPosition = mFxLocTime = mFxLocResolution = -1;
        mFxLocEffect = mFxLocRotation = mFxLocCoordSwap = mFxLocYFlip = -1;
        mFxLocXFlip = -1;
        mXmbLocPosition = mXmbLocTime = mXmbLocResolution = -1;
        mXmbLocRotation = mXmbLocCoordSwap = mXmbLocYFlip = -1;
        mXmbLocXFlip = -1;
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

    // GammaOS: Apply user-requested flip corrections on top of whatever
    // matrix state we have (identity, rotation, or rotation + PRIME Y-flip
    // from drmSetupZeroCopy). H flip negates row 0 of the 2x2 column-major
    // matrix; V flip negates row 1. Composed left-multiply:
    //   H:  diag(-1,  1) * M  =>  m0=-m0, m2=-m2
    //   V:  diag( 1, -1) * M  =>  m1=-m1, m3=-m3
    // Force sDrmGlRotation=true so the shader matrix path is used even when
    // the installed orientation is 0°. Fragment-space wallpaper FX receive
    // the flip state via uXFlip / uYFlip (see renderEffect).
    if (sDrmActive && (sDrmFlipH || sDrmFlipV)) {
        if (sDrmFlipH) {
            sDrmRotMat[0] = -sDrmRotMat[0];
            sDrmRotMat[2] = -sDrmRotMat[2];
        }
        if (sDrmFlipV) {
            sDrmRotMat[1] = -sDrmRotMat[1];
            sDrmRotMat[3] = -sDrmRotMat[3];
        }
        sDrmGlRotation = true;
        ALOGI("NanoMenu: DRM user flip applied (h=%d v=%d) rotMat=[%g %g %g %g]",
              sDrmFlipH ? 1 : 0, sDrmFlipV ? 1 : 0,
              sDrmRotMat[0], sDrmRotMat[1], sDrmRotMat[2], sDrmRotMat[3]);
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
