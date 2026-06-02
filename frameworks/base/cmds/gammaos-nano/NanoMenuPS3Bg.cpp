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

// GammaOS Nano PS3 XMB background (see NanoMenuPS3Bg.h). 1:1 port of the source
// web app's steady drawBGWebGL pipeline. Only the active steady-state branches
// are ported; the boot / theme-HSV / month-base / in-gradient-silk branches are
// inactive in the shipped steady look (the captured mesh is enabled, so
// uWaveFade==0 and uHSVStrength==0) and are intentionally not carried over.

#include "NanoMenuPS3Bg.h"
#include "NanoMenuPS3.h"
#include "NanoMenuPS3Particles.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <vector>

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <png.h>

#define LOG_TAG "GammaOSNano"
#include <utils/Log.h>

namespace android {
namespace ps3bg {

static const int WAVE_N  = 128;
static const int WAVE_NV = WAVE_N * WAVE_N;     // 16384

// ---------------------------------------------------------------------------
// state
// ---------------------------------------------------------------------------
static bool   sTriedInit = false;
static bool   sReady     = false;

// programs + locations
static GLuint sBgProg = 0, sWaveProg = 0, sBlitProg = 0, sCompProg = 0;
// FS_BG (gradient, month-base path) locations
static GLint  sBgPos, sBgUV, sBgMonthBase, sBgMonthBaseBot, sBgNightBlend;
// wave locations
static GLint  sWClip, sWNormal, sWUV, sWYFlip, sWScaleY, sWScaleX, sWOffset,
              sWFade, sWTint, sWAlpha, sWSilk, sWSpecW, sWSpecExp, sWYFade;
// blit locations
static GLint  sBlitPos, sBlitUV, sBlitTex;
// composite locations
static GLint  sCompPos, sCompUV, sCompTex, sCompRot, sCompExposure, sCompWhite;

// ---- per-month base colour (index.html MONTH_GRADIENT + monthBaseColor) ----
// h(deg) s v nv per calendar month (0=Jan). Jul has a distinct darker-blue
// bottom anchor. The shader (FS_BG month-base path) imposes the structure.
static const float kMonthH[12]  = {58.6f,88.9f,339.8f,118.1f,270.7f,166.5f,196.0f,288.0f,47.6f,33.2f,0.9f,0.0f};
static const float kMonthS[12]  = {0.630f,0.650f,0.562f,0.713f,0.475f,0.723f,0.620f,0.600f,0.705f,0.590f,0.668f,0.000f};
static const float kMonthV[12]  = {0.785f,0.593f,0.708f,0.277f,0.440f,0.632f,0.585f,0.672f,0.816f,0.314f,0.621f,0.808f};
static const float kMonthNV[12] = {0.176f,0.154f,0.167f,0.031f,0.188f,0.031f,0.122f,0.104f,0.176f,0.074f,0.009f,0.158f};
static const float kMonthValuePrecomp = 1.30f;
static void hsvToRgb(float h, float s, float v, float* out) {
    h = fmodf(fmodf(h, 360.0f) + 360.0f, 360.0f);
    float c = v * s;
    float x = c * (1.0f - fabsf(fmodf(h / 60.0f, 2.0f) - 1.0f));
    float m = v - c;
    float r, g, b;
    if (h < 60)       { r = c; g = x; b = 0; }
    else if (h < 120) { r = x; g = c; b = 0; }
    else if (h < 180) { r = 0; g = c; b = x; }
    else if (h < 240) { r = 0; g = x; b = c; }
    else if (h < 300) { r = x; g = 0; b = c; }
    else              { r = c; g = 0; b = x; }
    out[0] = r + m; out[1] = g + m; out[2] = b + m;
}
// Theme Settings cross-fade state (mirrors the web dispTint / dispHSV / dispBlend
// + stepThemeFade, index.html line 586). A chosen Colour blends the per-month
// base toward the chosen tint by an animated strength (0 = Original per-month
// hue, 1 = full manual colour); the FS_BG vertical envelope re-imposes the
// dark-top / colour-bottom + day/night value structure on top. The day/night
// blend is likewise cross-faded. All glide toward their targets each frame with
// k = 1 - exp(-dt_ms / 220) (~0.4s) so colour + time-of-day changes ease in
// instead of snapping. Defaults leave the steady per-month / clock path intact.
static float sThemeTgtR = 0.0f, sThemeTgtG = 0.0f, sThemeTgtB = 0.0f;  // target tint
static float sThemeCurR = 0.0f, sThemeCurG = 0.0f, sThemeCurB = 0.0f;  // animated tint
static float sThemeStrTgt = 0.0f, sThemeStrCur = 0.0f;                 // manual-colour strength 0..1
static float sDayNightTgt = -1.0f;   // <0 = auto (follow the clock); 0..1 = forced
static float sDayNightCur = 0.0f;    // animated effective blend used downstream
static bool  sThemeFadeInit = false; // first frame snaps (no fade-from-zero)
static bool  sThemeFadingNow = false; // a colour/day-night cross-fade is in flight
// Background == Classic removes the glitter particle field (Theme Settings).
static bool  sParticlesEnabled = true;
static void rgbToHsv(const float* c, float* h, float* s, float* v) {
    float r = c[0], g = c[1], b = c[2];
    float mx = fmaxf(r, fmaxf(g, b)), mn = fminf(r, fminf(g, b));
    float d = mx - mn;
    *v = mx;
    *s = (mx > 1e-6f) ? (d / mx) : 0.0f;
    if (d < 1e-6f) { *h = 0.0f; return; }
    float hh;
    if (mx == r)      hh = fmodf((g - b) / d, 6.0f);
    else if (mx == g) hh = (b - r) / d + 2.0f;
    else              hh = (r - g) / d + 4.0f;
    hh *= 60.0f; if (hh < 0.0f) hh += 360.0f;
    *h = hh;
}
// HSV hue-rotation of a per-month base colour toward the chosen tint (web
// applyHSVRotation, index.html 2967-2991): hue taken from the tint, saturation
// 65% toward the tint's, value 35% toward the tint's luma (clamped 0..1.5). So
// Black darkens, White lightens, Red turns red, while the per-month value
// structure mostly survives. luma coeffs 0.299/0.587/0.114.
static void applyThemeHSV(float* c, float tr, float tg, float tb) {
    float hG, sG, vG; rgbToHsv(c, &hG, &sG, &vG); (void)hG; (void)vG;
    float gradLuma = 0.299f * c[0] + 0.587f * c[1] + 0.114f * c[2];
    float tt[3] = {tr, tg, tb};
    float hT, sT, vT; rgbToHsv(tt, &hT, &sT, &vT); (void)vT;
    float tintLuma = 0.299f * tr + 0.587f * tg + 0.114f * tb;
    float newS = sG + (sT - sG) * 0.65f;
    float newV = gradLuma + (tintLuma - gradLuma) * 0.35f;
    if (newV < 0.0f) newV = 0.0f; else if (newV > 1.5f) newV = 1.5f;
    hsvToRgb(hT, newS, newV, c);
}
static void monthBaseColor(int month, float nightBlend, float* out) {
    int m = ((month % 12) + 12) % 12;
    // Keep more colour at night: the old night value (kMonthNV) drove the base
    // near-black, so the bottom of the screen had no colour. Raise the night
    // value partway back toward the day value (the shader's vertical gradient
    // does the top-darkening), so the bottom stays colourful.
    float nightV = kMonthNV[m] + 0.45f * (kMonthV[m] - kMonthNV[m]);
    float v = kMonthV[m] * (1.0f - nightBlend) + nightV * nightBlend;
    hsvToRgb(kMonthH[m], kMonthS[m], v * kMonthValuePrecomp, out);
    // Manual Colour: blend the per-month base toward its HSV-rotated-to-tint
    // version by the animated strength (0 = Original per-month, 1 = full colour),
    // exactly the web's mix(gradColor, applyHSVRotation(gradColor,tint), strength).
    if (sThemeStrCur > 0.001f) {
        float rot[3] = { out[0], out[1], out[2] };
        applyThemeHSV(rot, sThemeCurR, sThemeCurG, sThemeCurB);
        out[0] += (rot[0] - out[0]) * sThemeStrCur;
        out[1] += (rot[1] - out[1]) * sThemeStrCur;
        out[2] += (rot[2] - out[2]) * sThemeStrCur;
    }
}
static void monthBaseColorBot(int month, float nightBlend, float* out) {
    // (July's deep-blue bottom edge removed per request - it washed the bottom of
    // the screen strong blue. The bottom now uses the same per-month colour.)
    monthBaseColor(month, nightBlend, out);
}

// wave geometry
static GLuint sWaveClipVBO = 0, sWaveAttrVBO = 0, sWaveIBO = 0;
static int    sWaveIndexCount = 0;
static bool   sWaveGeoReady = false;
static std::vector<float> sWaveScratch;      // 16384*4, reused per frame

// wave sequence (steady loop)
static std::vector<std::vector<float>> sSeqFrames;   // each 16384*4 (xyzw)
static int    sSeqCount = 0;
static bool   sSeqReady = false;
static double sSeqElapsed = 0.0;             // seconds of wave playback accumulated

// scene/gradient FBOs
static GLuint sGradFbo = 0, sGradTex = 0;
static GLuint sWorkFbo = 0, sWorkTex = 0;
static int    sFbW = 0, sFbH = 0;
static bool   sGradDirty = true;
static int    sGradMonth = -1;
static float  sGradBlendQ = -1.0f;
// Theme cross-fade values at the last gradient recache (so the cache refreshes
// every frame while a colour/day-night fade is in flight, then goes static).
static float  sGradLastStr = -1.0f;
static float  sGradLastR = -1.0f, sGradLastG = -1.0f, sGradLastB = -1.0f;
// Luminance of the current background base colour (0 dark .. ~1.2 light). The
// menu uses it to scale the text drop shadow: minimal on a dark wallpaper,
// pronounced on a light one. Updated on each gradient recache.
static float  sBgLumaEst = 0.5f;

// quad VBO (fullscreen, pos.xy + uv)
static GLuint sQuadVBO = 0;

// ---------------------------------------------------------------------------
// shaders
// ---------------------------------------------------------------------------
static const char* VS_FULL =
    "attribute vec2 aPos;\n"
    "attribute vec2 aUV;\n"
    "varying vec2 vUV;\n"
    "void main(){ vUV = aUV; gl_Position = vec4(aPos, 0.0, 1.0); }\n";

static const char* FS_BLIT =
    "precision mediump float;\n"
    "varying vec2 vUV;\n"
    "uniform sampler2D uTex;\n"
    "void main(){ gl_FragColor = vec4(texture2D(uTex, vUV).rgb, 1.0); }\n";

// Composite: exp2 tonemap of the (gradient + additive wave) scene. Mirrors
// FS_COMPOSITE (index.html 3880). uRotation maps logical NDC to the physical
// panel for DRM GL rotation.
static const char* VS_COMP =
    "attribute vec2 aPos;\n"
    "attribute vec2 aUV;\n"
    "uniform mat2 uRotation;\n"
    "varying vec2 vUV;\n"
    "void main(){ vUV = aUV; gl_Position = vec4(uRotation * aPos, 0.0, 1.0); }\n";

static const char* FS_COMP =
    "precision mediump float;\n"
    "varying vec2 vUV;\n"
    "uniform sampler2D uScene;\n"
    "uniform float uExposure;\n"
    "uniform float uWhiteLevel;\n"
    "void main(){\n"
    "  vec3 scene = texture2D(uScene, vUV).rgb;\n"
    "  const float LOG2E = 1.442695;\n"
    "  vec3 toned = vec3(1.0) - exp2(-scene * (uExposure / max(uWhiteLevel, 1e-3)) * LOG2E);\n"
    "  gl_FragColor = vec4(clamp(toned, 0.0, 1.0), 1.0);\n"
    "}\n";

// Steady FS_BG: the firmware "month-base" path (index.html main() useMonthBase
// branch, 3013-3091 + the global tonemap 3260-3265). The per-month base colour
// (uMonthBase / uMonthBaseBot, computed CPU-side from MONTH_GRADIENT) carries
// the hue/sat/value; the shader imposes the vertical envelope (bright top +
// bottom, a darker band at the menu row), a chroma boost in that band, the
// horizontal + corner vignettes and the night ramp, then the *1.05 luma
// shoulder. This is what produces the bright, near-uniform per-month gradient.
static const char* FS_BG =
    "precision mediump float;\n"
    "varying vec2 vUV;\n"
    "uniform vec3 uMonthBase;\n"
    "uniform vec3 uMonthBaseBot;\n"
    "uniform float uNightDayBlend;\n"
    "void main(){\n"
    "  float screenY = 1.0 - vUV.y;\n"
    "  vec3 gradColor = mix(uMonthBase, uMonthBaseBot, smoothstep(0.70, 1.0, screenY));\n"
    "  { float l = dot(gradColor, vec3(0.299,0.587,0.114));\n"
    "    gradColor = l + (gradColor - l) * 1.25; gradColor = max(gradColor, 0.0); }\n"
    "  float dip = exp(-pow((screenY - 0.68) / 0.14, 2.0));\n"
    "  float vEnvM = 1.05 - 0.26 * dip;\n"
    "  vEnvM *= 1.0 + 0.05 * smoothstep(0.30, 0.0, screenY);\n"
    "  vEnvM *= 1.0 + 0.06 * smoothstep(0.85, 1.0, screenY);\n"
    "  gradColor *= vEnvM;\n"
    "  { float l = dot(gradColor, vec3(0.299,0.587,0.114));\n"
    "    gradColor = l + (gradColor - l) * (1.0 + 0.22 * dip); }\n"
    "  { float dxm = vUV.x - 0.52;\n"
    "    float hV = 1.0 - 0.30 * dxm * dxm * (dxm < 0.0 ? 1.6 : 1.0); gradColor *= hV; }\n"
    "  { float dxm = vUV.x - 0.5;\n"
    "    float botEdge = clamp(screenY * (abs(dxm) * 2.0), 0.0, 1.0);\n"
    "    float sideM = mix(0.847, 0.925, smoothstep(-0.5, 0.5, dxm));\n"
    "    gradColor *= mix(1.0, sideM, botEdge); }\n"
    "  { float dxm = vUV.x - 0.5; float dyTopM = 1.0 - screenY;\n"
    "    float cornerM = clamp(pow(dyTopM, 1.5) * (abs(dxm) * 2.0), 0.0, 1.0);\n"
    "    float sideC = mix(0.88, 1.0, smoothstep(0.0, 0.5, dxm));\n"
    "    gradColor *= 1.0 - 0.40 * cornerM * sideC; }\n"
    "  // Unified vertical darkening: the TOP is darker (day ~40% darker, night\n"
    "  // much darker), easing to FULL colour at the bottom. The dark band reaches\n"
    "  // further down as the day darkens, but the bottom always keeps colour\n"
    "  // (screenY: 0 = top, 1 = bottom). Replaces the old uniform night ramp.\n"
    "  float topDark = mix(0.12, 0.0, uNightDayBlend);\n"    // day top as dark as the old night top; night top fully BLACK; dusk/dawn in between (darker than day)
    "  float fullAt  = mix(0.62, 0.92, uNightDayBlend);\n"   // day dark band reaches mid-screen; night reaches near the bottom (colour kept at the very bottom)
    "  float vGrad   = mix(topDark, 1.0, smoothstep(0.0, fullAt, screenY));\n"
    "  gradColor *= vGrad;\n"
    "  vec3 finalColor = gradColor * 1.05;\n"
    "  float lumT = dot(finalColor, vec3(0.299, 0.587, 0.114));\n"
    "  float lumO = lumT / (1.0 + lumT * 0.30);\n"
    "  finalColor = finalColor * (lumT > 1e-4 ? lumO / lumT : 1.0);\n"
    "  gl_FragColor = vec4(finalColor, 1.0);\n"
    "}\n";

// Captured cloth silk (VS/FS_WAVECAP, index.html 4846/4870). Derivative
// extension dropped (the shader uses the per-vertex normal, not dFdx/dFdy).
static const char* VS_WAVECAP =
    "precision highp float;\n"
    "attribute vec4 aClip;\n"
    "attribute vec3 aNormal;\n"
    "attribute vec2 aUV;\n"
    "uniform float uYFlip;\n"
    "uniform float uScaleY;\n"
    "uniform float uScaleX;\n"
    "uniform vec2 uOffset;\n"
    "varying vec3 vNdc;\n"
    "varying vec3 vNrm;\n"
    "varying vec2 vUV;\n"
    "void main(){\n"
    "  vec4 p = aClip;\n"
    "  p.y *= uYFlip * uScaleY;\n"
    "  p.x *= uScaleX;\n"
    "  p.xy += uOffset * p.w;\n"
    "  gl_Position = p;\n"
    "  vNdc = p.xyz / p.w;\n"
    "  vNrm = aNormal;\n"
    "  vUV = aUV;\n"
    "}\n";

static const char* FS_WAVECAP =
    "precision highp float;\n"
    "varying vec3 vNdc;\n"
    "varying vec3 vNrm;\n"
    "varying vec2 vUV;\n"
    "uniform float uFade;\n"
    "uniform vec3 uTint;\n"
    "uniform float uAlpha;\n"
    "uniform float uSilk;\n"
    "uniform float uSpecW;\n"
    "uniform float uSpecExp;\n"
    "uniform vec2 uYFade;\n"
    "void main(){\n"
    "  vec3 N = normalize(vNrm);\n"
    "  float F = 0.5 * pow(max(1.0 + dot(vec3(0.0,0.0,-1.0), N), 0.0), 3.0);\n"
    "  vec3 V  = normalize(vec3(3.12367, -0.0166247, -0.0852542));\n"
    "  vec3 L0 = normalize(vec3(0.484729, 0.382283, -0.97268));\n"
    "  vec3 H0 = normalize(L0 + V);\n"
    "  float spec0 = pow(max(dot(N, H0), 0.0), uSpecExp);\n"
    "  float silk = F * 0.667 * 1.7 + spec0 * uSpecW;\n"
    "  silk = clamp(silk, 0.0, 0.95);\n"
    "  float lit = mix(1.0, 0.107345 + silk, uSilk);\n"
    "  float edge = smoothstep(0.0, 0.06, vUV.x) * smoothstep(0.0, 0.06, 1.0 - vUV.x);\n"
    "  float yf = 1.0 - smoothstep(uYFade.x, uYFade.y, vNdc.y);\n"
    "  float a = uAlpha * edge * lit * yf;\n"
    "  gl_FragColor = vec4(uTint * uFade, clamp(a, 0.0, 1.0));\n"
    "}\n";

// ---------------------------------------------------------------------------
// helpers
// ---------------------------------------------------------------------------
static GLuint compileShader(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[1024];
        glGetShaderInfoLog(s, sizeof(log), nullptr, log);
        ALOGE("ps3bg: shader compile failed: %s", log);
        glDeleteShader(s);
        return 0;
    }
    return s;
}

static GLuint linkProgram(const char* vsSrc, const char* fsSrc) {
    GLuint vs = compileShader(GL_VERTEX_SHADER, vsSrc);
    GLuint fs = compileShader(GL_FRAGMENT_SHADER, fsSrc);
    if (!vs || !fs) { if (vs) glDeleteShader(vs); if (fs) glDeleteShader(fs); return 0; }
    GLuint p = glCreateProgram();
    glAttachShader(p, vs);
    glAttachShader(p, fs);
    glLinkProgram(p);
    glDeleteShader(vs);
    glDeleteShader(fs);
    GLint ok = 0;
    glGetProgramiv(p, GL_LINK_STATUS, &ok);
    if (!ok) {
        char log[1024];
        glGetProgramInfoLog(p, sizeof(log), nullptr, log);
        ALOGE("ps3bg: program link failed: %s", log);
        glDeleteProgram(p);
        return 0;
    }
    return p;
}

// f16 -> f32 (matches index.html f16tof32).
static inline float f16tof32(uint16_t h) {
    uint32_t s = (h & 0x8000u) >> 15;
    uint32_t e = (h & 0x7c00u) >> 10;
    uint32_t f = h & 0x03ffu;
    float sign = s ? -1.0f : 1.0f;
    if (e == 0)  return sign * powf(2.0f, -14.0f) * ((float)f / 1024.0f);
    if (e == 31) return f ? NAN : sign * INFINITY;
    return sign * powf(2.0f, (float)e - 15.0f) * (1.0f + (float)f / 1024.0f);
}

// Resolve an asset path: prefer the dev push dir, fall back to the shipped one.
static bool resolveAsset(const char* sub, const char* file, char* out, size_t n) {
    snprintf(out, n, "/data/system/nano_xmb/%s/%s", sub, file);
    FILE* f = fopen(out, "rb");
    if (f) { fclose(f); return true; }
    snprintf(out, n, "/system/etc/nano_xmb/%s/%s", sub, file);
    f = fopen(out, "rb");
    if (f) { fclose(f); return true; }
    return false;
}

// Read a whole asset file into a heap buffer. Caller frees with free().
static uint8_t* readAsset(const char* sub, const char* file, long* sizeOut) {
    char path[256];
    if (!resolveAsset(sub, file, path, sizeof(path))) return nullptr;
    FILE* f = fopen(path, "rb");
    if (!f) return nullptr;
    fseek(f, 0, SEEK_END);
    long sz = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (sz <= 0) { fclose(f); return nullptr; }
    uint8_t* buf = (uint8_t*)malloc((size_t)sz);
    if (!buf) { fclose(f); return nullptr; }
    if (fread(buf, 1, (size_t)sz, f) != (size_t)sz) { free(buf); fclose(f); return nullptr; }
    fclose(f);
    if (sizeOut) *sizeOut = sz;
    return buf;
}

// 3x3 separable box blur over a 128x128 field (clamped), matching the JS
// blur1D used to smooth the captured normals.
static void blur1D(const float* src, float* dst, bool horiz) {
    const int N = WAVE_N;
    for (int r = 0; r < N; r++) {
        for (int c = 0; c < N; c++) {
            float s = 0.0f; int w = 0;
            for (int k = -1; k <= 1; k++) {
                int rr = horiz ? r : (r + k < 0 ? 0 : (r + k > N - 1 ? N - 1 : r + k));
                int cc = horiz ? (c + k < 0 ? 0 : (c + k > N - 1 ? N - 1 : c + k)) : c;
                s += src[rr * N + cc]; w++;
            }
            dst[r * N + c] = s / (float)w;
        }
    }
}

// ---------------------------------------------------------------------------
// wave geometry / sequence load
// ---------------------------------------------------------------------------
static void loadWaveGeo() {
    long sz = 0;
    uint8_t* raw = readAsset("wave", "wave_geo.bin", &sz);
    if (!raw) { ALOGE("ps3bg: wave_geo.bin missing"); return; }
    if (sz < (long)(WAVE_NV * 9 * sizeof(float))) { free(raw); ALOGE("ps3bg: wave_geo.bin short"); return; }
    const float* data = (const float*)raw;   // 16384 * 9 (clip4, normal3, uv2), no header

    std::vector<float> clip0((size_t)WAVE_NV * 4);
    std::vector<float> attr((size_t)WAVE_NV * 5);   // normal3 + uv2
    std::vector<float> nx(WAVE_NV), ny(WAVE_NV), nz(WAVE_NV), tmp(WAVE_NV);
    for (int i = 0; i < WAVE_NV; i++) {
        const float* b = data + i * 9;
        clip0[i * 4 + 0] = b[0]; clip0[i * 4 + 1] = b[1];
        clip0[i * 4 + 2] = b[2]; clip0[i * 4 + 3] = b[3];
        nx[i] = b[4]; ny[i] = b[5]; nz[i] = b[6];
    }
    for (int p = 0; p < 3; p++) {
        blur1D(nx.data(), tmp.data(), true);  blur1D(tmp.data(), nx.data(), false);
        blur1D(ny.data(), tmp.data(), true);  blur1D(tmp.data(), ny.data(), false);
        blur1D(nz.data(), tmp.data(), true);  blur1D(tmp.data(), nz.data(), false);
    }
    for (int i = 0; i < WAVE_NV; i++) {
        const float* b = data + i * 9;
        float l = sqrtf(nx[i] * nx[i] + ny[i] * ny[i] + nz[i] * nz[i]);
        if (l <= 0.0f) l = 1.0f;
        attr[i * 5 + 0] = nx[i] / l; attr[i * 5 + 1] = ny[i] / l; attr[i * 5 + 2] = nz[i] / l;
        attr[i * 5 + 3] = b[7]; attr[i * 5 + 4] = b[8];
    }
    free(raw);

    glGenBuffers(1, &sWaveAttrVBO);
    glBindBuffer(GL_ARRAY_BUFFER, sWaveAttrVBO);
    glBufferData(GL_ARRAY_BUFFER, attr.size() * sizeof(float), attr.data(), GL_STATIC_DRAW);
    glGenBuffers(1, &sWaveClipVBO);
    glBindBuffer(GL_ARRAY_BUFFER, sWaveClipVBO);
    glBufferData(GL_ARRAY_BUFFER, clip0.size() * sizeof(float), clip0.data(), GL_DYNAMIC_DRAW);

    // Two triangles per quad; values < 16384 so 16-bit indices suffice.
    std::vector<uint16_t> idx;
    idx.reserve((size_t)(WAVE_N - 1) * (WAVE_N - 1) * 6);
    for (int r = 0; r < WAVE_N - 1; r++) {
        for (int c = 0; c < WAVE_N - 1; c++) {
            uint16_t a = (uint16_t)(r * WAVE_N + c);
            uint16_t b = (uint16_t)(r * WAVE_N + c + 1);
            uint16_t d = (uint16_t)((r + 1) * WAVE_N + c);
            uint16_t e = (uint16_t)((r + 1) * WAVE_N + c + 1);
            idx.push_back(a); idx.push_back(d); idx.push_back(b);
            idx.push_back(b); idx.push_back(d); idx.push_back(e);
        }
    }
    glGenBuffers(1, &sWaveIBO);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, sWaveIBO);
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx.size() * sizeof(uint16_t), idx.data(), GL_STATIC_DRAW);
    sWaveIndexCount = (int)idx.size();
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    sWaveScratch.assign((size_t)WAVE_NV * 4, 0.0f);
    sWaveGeoReady = true;
    ALOGI("ps3bg: wave_geo loaded (%d verts, %d indices)", WAVE_NV, sWaveIndexCount);
}

static void loadWaveSeq() {
    long sz = 0;
    uint8_t* raw = readAsset("wave", "wave_seq2.bin", &sz);
    if (!raw) { ALOGE("ps3bg: wave_seq2.bin missing"); return; }
    if (sz < 24 || memcmp(raw, "WSQ2", 4) != 0) { free(raw); ALOGE("ps3bg: wave_seq2 bad magic"); return; }
    uint32_t frameCount, gridN, vstride;
    memcpy(&frameCount, raw + 4, 4);
    memcpy(&gridN, raw + 8, 4);
    memcpy(&vstride, raw + 12, 4);
    float zwRatio;
    memcpy(&zwRatio, raw + 20, 4);
    const int NV = (int)(gridN * gridN);
    if (NV != WAVE_NV || vstride != 3) { free(raw); ALOGE("ps3bg: wave_seq2 unexpected layout"); return; }
    const uint16_t* u16 = (const uint16_t*)(raw + 24);

    sSeqFrames.clear();
    sSeqFrames.resize(frameCount);
    for (uint32_t f = 0; f < frameCount; f++) {
        std::vector<float>& fr = sSeqFrames[f];
        fr.assign((size_t)NV * 4, 0.0f);
        size_t off = (size_t)f * NV * vstride;
        for (int i = 0; i < NV; i++) {
            float X = f16tof32(u16[off + i * 3 + 0]);
            float Y = f16tof32(u16[off + i * 3 + 1]);
            float W = f16tof32(u16[off + i * 3 + 2]);
            fr[i * 4 + 0] = X; fr[i * 4 + 1] = Y; fr[i * 4 + 2] = W * zwRatio; fr[i * 4 + 3] = W;
        }
    }
    free(raw);

    // Flatten the crest amplitude (per-vertex Y deviation from temporal mean).
    const float flat = 0.72f;
    std::vector<float> meanY((size_t)NV, 0.0f);
    for (uint32_t f = 0; f < frameCount; f++)
        for (int i = 0; i < NV; i++) meanY[i] += sSeqFrames[f][i * 4 + 1];
    for (int i = 0; i < NV; i++) meanY[i] /= (float)frameCount;
    for (uint32_t f = 0; f < frameCount; f++)
        for (int i = 0; i < NV; i++) {
            float m = meanY[i];
            sSeqFrames[f][i * 4 + 1] = m + (sSeqFrames[f][i * 4 + 1] - m) * flat;
        }

    sSeqCount = (int)frameCount;
    sSeqReady = true;
    ALOGI("ps3bg: wave_seq2 loaded (%d keyframes)", sSeqCount);
}

// 0 = day, 1 = night, smooth dusk/dawn ramps. Matches computeNightDayBlend.
static float computeNightDayBlend(float hour) {
    const float D2N_BEGIN = 16.5f, D2N_END = 20.5f, N2D_BEGIN = 4.5f, N2D_END = 7.0f;
    if (hour >= D2N_BEGIN && hour <= D2N_END) return (hour - D2N_BEGIN) / (D2N_END - D2N_BEGIN);
    if (hour > D2N_END || hour < N2D_BEGIN) return 1.0f;
    if (hour >= N2D_BEGIN && hour <= N2D_END) return 1.0f - (hour - N2D_BEGIN) / (N2D_END - N2D_BEGIN);
    return 0.0f;
}

// ---------------------------------------------------------------------------
// FBO management
// ---------------------------------------------------------------------------
static bool ensureFbo(GLuint* fbo, GLuint* tex, int w, int h) {
    if (!*fbo) glGenFramebuffers(1, fbo);
    if (!*tex) glGenTextures(1, tex);
    glBindTexture(GL_TEXTURE_2D, *tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindFramebuffer(GL_FRAMEBUFFER, *fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, *tex, 0);
    return glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
}

// Fullscreen quad: pos.xy in [-1,1], uv in [0,1]. Reused for gradient/blit.
static void drawFullQuad(GLint posLoc, GLint uvLoc) {
    static const GLfloat verts[] = {
        -1.f, -1.f, 0.f, 0.f,   1.f, -1.f, 1.f, 0.f,   1.f, 1.f, 1.f, 1.f,
        -1.f, -1.f, 0.f, 0.f,   1.f,  1.f, 1.f, 1.f,  -1.f, 1.f, 0.f, 1.f,
    };
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glVertexAttribPointer(posLoc, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), verts);
    glEnableVertexAttribArray(posLoc);
    glVertexAttribPointer(uvLoc, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), verts + 2);
    glEnableVertexAttribArray(uvLoc);
    glDrawArrays(GL_TRIANGLES, 0, 6);
}

// ---------------------------------------------------------------------------
// init / shutdown
// ---------------------------------------------------------------------------
bool init() {
    if (sReady) return true;
    if (!sTriedInit) {
        sTriedInit = true;
        sBgProg   = linkProgram(VS_FULL, FS_BG);
        sWaveProg = linkProgram(VS_WAVECAP, FS_WAVECAP);
        sBlitProg = linkProgram(VS_FULL, FS_BLIT);
        sCompProg = linkProgram(VS_COMP, FS_COMP);
        if (sBgProg) {
            sBgPos = glGetAttribLocation(sBgProg, "aPos");
            sBgUV = glGetAttribLocation(sBgProg, "aUV");
            sBgMonthBase = glGetUniformLocation(sBgProg, "uMonthBase");
            sBgMonthBaseBot = glGetUniformLocation(sBgProg, "uMonthBaseBot");
            sBgNightBlend = glGetUniformLocation(sBgProg, "uNightDayBlend");
        }
        if (sWaveProg) {
            sWClip = glGetAttribLocation(sWaveProg, "aClip");
            sWNormal = glGetAttribLocation(sWaveProg, "aNormal");
            sWUV = glGetAttribLocation(sWaveProg, "aUV");
            sWYFlip = glGetUniformLocation(sWaveProg, "uYFlip");
            sWScaleY = glGetUniformLocation(sWaveProg, "uScaleY");
            sWScaleX = glGetUniformLocation(sWaveProg, "uScaleX");
            sWOffset = glGetUniformLocation(sWaveProg, "uOffset");
            sWFade = glGetUniformLocation(sWaveProg, "uFade");
            sWTint = glGetUniformLocation(sWaveProg, "uTint");
            sWAlpha = glGetUniformLocation(sWaveProg, "uAlpha");
            sWSilk = glGetUniformLocation(sWaveProg, "uSilk");
            sWSpecW = glGetUniformLocation(sWaveProg, "uSpecW");
            sWSpecExp = glGetUniformLocation(sWaveProg, "uSpecExp");
            sWYFade = glGetUniformLocation(sWaveProg, "uYFade");
        }
        if (sBlitProg) {
            sBlitPos = glGetAttribLocation(sBlitProg, "aPos");
            sBlitUV = glGetAttribLocation(sBlitProg, "aUV");
            sBlitTex = glGetUniformLocation(sBlitProg, "uTex");
        }
        if (sCompProg) {
            sCompPos = glGetAttribLocation(sCompProg, "aPos");
            sCompUV = glGetAttribLocation(sCompProg, "aUV");
            sCompTex = glGetUniformLocation(sCompProg, "uScene");
            sCompRot = glGetUniformLocation(sCompProg, "uRotation");
            sCompExposure = glGetUniformLocation(sCompProg, "uExposure");
            sCompWhite = glGetUniformLocation(sCompProg, "uWhiteLevel");
        }
    }
    if (!sWaveGeoReady) loadWaveGeo();
    if (!sSeqReady) loadWaveSeq();
    sReady = sBgProg && sWaveProg && sBlitProg && sCompProg &&
             sWaveGeoReady && sSeqReady;
    if (sReady) ALOGI("ps3bg: ready");
    return sReady;
}

bool ready() { return sReady; }

GLuint workTex() { return sWorkTex; }

// Cold-boot wave brightness on uFade (1.0 = steady; the intro ramps 0->1).
static float sBootWaveBrightness = 1.0f;
void setBootWaveBrightness(float b) { sBootWaveBrightness = b; }

// Theme Settings hooks. These set TARGETS only; render() cross-fades the live
// state toward them each frame (web stepThemeFade). setThemeColor selects a
// manual colour (strength target -> 1); clearThemeColor reverts to the per-month
// hue (strength target -> 0); setDayNightBlend forces the lighting (<0 = auto).
void setThemeColor(float r, float g, float b) {
    sThemeTgtR = r; sThemeTgtG = g; sThemeTgtB = b; sThemeStrTgt = 1.0f;
}
void clearThemeColor() { sThemeStrTgt = 0.0f; }
void setDayNightBlend(float b) { sDayNightTgt = b; }
void setParticlesEnabled(bool e) { sParticlesEnabled = e; }
float backgroundLuma() { return sBgLumaEst; }
bool themeFading() { return sThemeFadingNow; }

void invalidateGradient() { sGradDirty = true; }

void shutdown() {
    if (sBgProg) glDeleteProgram(sBgProg);
    if (sWaveProg) glDeleteProgram(sWaveProg);
    if (sBlitProg) glDeleteProgram(sBlitProg);
    if (sCompProg) glDeleteProgram(sCompProg);
    GLuint texs[] = {sGradTex, sWorkTex};
    glDeleteTextures(2, texs);
    GLuint fbos[] = {sGradFbo, sWorkFbo};
    glDeleteFramebuffers(2, fbos);
    GLuint bufs[] = {sWaveClipVBO, sWaveAttrVBO, sWaveIBO, sQuadVBO};
    glDeleteBuffers(4, bufs);
    sBgProg = sWaveProg = sBlitProg = sCompProg = 0;
    sGradFbo = sWorkFbo = sGradTex = sWorkTex = 0;
    sWaveClipVBO = sWaveAttrVBO = sWaveIBO = sQuadVBO = 0;
    sSeqFrames.clear(); sWaveScratch.clear();
    sReady = false; sTriedInit = false; sWaveGeoReady = false; sSeqReady = false;
    sGradDirty = true;
}

// ---------------------------------------------------------------------------
// per-frame render
// ---------------------------------------------------------------------------
static void renderGradientCache(int fw, int fh, int month, float nightDayBlend) {
    // CPU side of the firmware month-base path (index.html drawBGWebGL 6308-6326):
    // the per-month base colour carries the hue/sat/value; the FS_BG shader
    // imposes the vertical envelope, vignettes, night ramp + the *1.05 shoulder.
    float mb[3], mbb[3];
    monthBaseColor(month, nightDayBlend, mb);
    monthBaseColorBot(month, nightDayBlend, mbb);
    // Background brightness for the menu's dynamic drop shadow. Weight the bottom
    // (full-colour) anchor more than the dark-ramped top, since the menu text
    // sits over the mid-lower band.
    sBgLumaEst = 0.299f * (mb[0]*0.35f + mbb[0]*0.65f)
               + 0.587f * (mb[1]*0.35f + mbb[1]*0.65f)
               + 0.114f * (mb[2]*0.35f + mbb[2]*0.65f);
    glBindFramebuffer(GL_FRAMEBUFFER, sGradFbo);
    glViewport(0, 0, fw, fh);
    glDisable(GL_BLEND);
    glUseProgram(sBgProg);
    glUniform3f(sBgMonthBase, mb[0], mb[1], mb[2]);
    glUniform3f(sBgMonthBaseBot, mbb[0], mbb[1], mbb[2]);
    glUniform1f(sBgNightBlend, nightDayBlend);
    drawFullQuad(sBgPos, sBgUV);
}

// Animate the wave clip positions from the captured keyframes (Catmull-Rom,
// 0.8 kf/s, 5-keyframe loop-seam crossfade) and upload to the dynamic VBO.
static void animateWave(float dt) {
    if (!sSeqReady || sSeqCount < 2) return;
    const float kfps = 0.8f;
    sSeqElapsed += (double)dt;
    int count = sSeqCount;
    int n = WAVE_NV * 4;
    const int XF = 5;
    double pp = sSeqElapsed * kfps;
    pp = fmod(pp, (double)count);
    if (pp < 0) pp += count;
    int i0 = ((int)floor(pp)) % count;
    int i1 = (i0 + 1) % count;
    float fr = (float)(pp - floor(pp));
    int im1 = (i0 - 1 + count) % count;
    int i2 = (i1 + 1) % count;
    const std::vector<float>& P0 = sSeqFrames[im1];
    const std::vector<float>& A = sSeqFrames[i0];
    const std::vector<float>& B = sSeqFrames[i1];
    const std::vector<float>& P3 = sSeqFrames[i2];
    const std::vector<float>& C = sSeqFrames[0];
    float t = fr, t2 = t * t, t3 = t2 * t;
    float w = (pp > count - XF) ? (float)((pp - (count - XF)) / XF) : 0.0f;
    float* S = sWaveScratch.data();
    for (int i = 0; i < n; i++) {
        float p0 = P0[i], a = A[i], b = B[i], p3 = P3[i];
        float main = 0.5f * ((2.0f * a) + (-p0 + b) * t +
                     (2.0f * p0 - 5.0f * a + 4.0f * b - p3) * t2 +
                     (-p0 + 3.0f * a - 3.0f * b + p3) * t3);
        S[i] = (w > 0.0001f) ? (main * (1.0f - w) + C[i] * w) : main;
    }
    glBindBuffer(GL_ARRAY_BUFFER, sWaveClipVBO);
    glBufferSubData(GL_ARRAY_BUFFER, 0, (GLsizeiptr)(n * sizeof(float)), S);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
}

void render(int panelW, int panelH, float dt, const float rotMat2[4], bool /*rotActive*/) {
    if (!sReady && !init()) return;

    const int fw = (int)(ps3::gFrameW + 0.5f);
    const int fh = (int)(ps3::gFrameH + 0.5f);
    if (fw < 2 || fh < 2) return;

    // The caller's draw target (default surface OR the DRM AHB-backed FBO) and
    // its viewport must be restored for the composite pass; our FBO passes below
    // rebind both.
    GLint prevFbo = 0; glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
    GLint prevVp[4] = {0, 0, panelW, panelH}; glGetIntegerv(GL_VIEWPORT, prevVp);

    // (Re)create the frame-sized FBOs on a size change.
    if (fw != sFbW || fh != sFbH) {
        ensureFbo(&sGradFbo, &sGradTex, fw, fh);
        ensureFbo(&sWorkFbo, &sWorkTex, fw, fh);
        sFbW = fw; sFbH = fh;
        sGradDirty = true;
    }

    // Time-of-day + month.
    time_t tt = time(nullptr);
    struct tm lt;
    localtime_r(&tt, &lt);
    float hour = lt.tm_hour + lt.tm_min / 60.0f + lt.tm_sec / 3600.0f;

    // Theme cross-fade (web stepThemeFade, index.html 586): glide the manual
    // Colour tint + strength and the day/night blend toward their targets each
    // frame. Day/night target = the forced override (>=0) or the clock. k =
    // 1 - exp(-dt_ms/220) (~0.4s), dt capped to 50ms; first frame snaps so there
    // is no fade up from zero on boot.
    float blendTarget = (sDayNightTgt >= 0.0f) ? sDayNightTgt : computeNightDayBlend(hour);
    {
        float dtms = dt * 1000.0f;
        if (dtms <= 0.0f) dtms = 16.7f; else if (dtms > 50.0f) dtms = 50.0f;
        float k = 1.0f - expf(-dtms / 220.0f);
        if (!sThemeFadeInit) {
            sThemeCurR = sThemeTgtR; sThemeCurG = sThemeTgtG; sThemeCurB = sThemeTgtB;
            sThemeStrCur = sThemeStrTgt; sDayNightCur = blendTarget; sThemeFadeInit = true;
        } else {
            sThemeCurR   += (sThemeTgtR   - sThemeCurR)   * k;
            sThemeCurG   += (sThemeTgtG   - sThemeCurG)   * k;
            sThemeCurB   += (sThemeTgtB   - sThemeCurB)   * k;
            sThemeStrCur += (sThemeStrTgt - sThemeStrCur) * k;
            sDayNightCur += (blendTarget  - sDayNightCur) * k;
        }
        // True while a colour / strength / day-night cross-fade is still settling,
        // so the menu can sample the frosted backdrop at 60Hz during a live
        // theme preview (smooth) and drop back to 15Hz once settled.
        sThemeFadingNow =
              fabsf(sThemeStrCur - sThemeStrTgt) > 1.5e-3f
           || fabsf(sThemeCurR   - sThemeTgtR)   > 2.0e-3f
           || fabsf(sThemeCurG   - sThemeTgtG)   > 2.0e-3f
           || fabsf(sThemeCurB   - sThemeTgtB)   > 2.0e-3f
           || fabsf(sDayNightCur - blendTarget)  > 2.0e-3f;
    }
    float nightDayBlend = sDayNightCur;

    // Re-render the cached gradient when the month, the (animated) day/night blend
    // or the (animated) theme colour/strength moved meaningfully. While a colour
    // or day/night cross-fade is in flight these change every frame so the cache
    // refreshes every frame; once settled the deltas fall below epsilon and the
    // gradient is static again (one cheap quad into a small FBO, not the blur).
    float blendQ = floorf(nightDayBlend * 50.0f) / 50.0f;
    bool themeMoved = fabsf(sThemeStrCur - sGradLastStr) > 1e-3f
                   || fabsf(sThemeCurR - sGradLastR) > 1.5e-3f
                   || fabsf(sThemeCurG - sGradLastG) > 1.5e-3f
                   || fabsf(sThemeCurB - sGradLastB) > 1.5e-3f;
    if (sGradDirty || lt.tm_mon != sGradMonth || fabsf(blendQ - sGradBlendQ) > 1e-4f || themeMoved) {
        renderGradientCache(fw, fh, lt.tm_mon, nightDayBlend);
        sGradDirty = false;
        sGradMonth = lt.tm_mon;
        sGradBlendQ = blendQ;
        sGradLastStr = sThemeStrCur;
        sGradLastR = sThemeCurR; sGradLastG = sThemeCurG; sGradLastB = sThemeCurB;
    }

    // Build the work buffer: gradient blit, then additive wave on top.
    animateWave(dt);
    ps3part::update(dt);
    glBindFramebuffer(GL_FRAMEBUFFER, sWorkFbo);
    glViewport(0, 0, fw, fh);
    glDisable(GL_BLEND);
    glUseProgram(sBlitProg);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, sGradTex);
    glUniform1i(sBlitTex, 0);
    drawFullQuad(sBlitPos, sBlitUV);

    if (sWaveGeoReady) {
        glUseProgram(sWaveProg);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);      // additive
        glDisable(GL_DEPTH_TEST);
        glBindBuffer(GL_ARRAY_BUFFER, sWaveClipVBO);
        glEnableVertexAttribArray(sWClip);
        glVertexAttribPointer(sWClip, 4, GL_FLOAT, GL_FALSE, 16, (const void*)0);
        glBindBuffer(GL_ARRAY_BUFFER, sWaveAttrVBO);
        if (sWNormal >= 0) { glEnableVertexAttribArray(sWNormal); glVertexAttribPointer(sWNormal, 3, GL_FLOAT, GL_FALSE, 20, (const void*)0); }
        if (sWUV >= 0) { glEnableVertexAttribArray(sWUV); glVertexAttribPointer(sWUV, 2, GL_FLOAT, GL_FALSE, 20, (const void*)12); }
        float layoutFit = ps3::LAYOUT_FIT > 0.0f ? ps3::LAYOUT_FIT : 1.0f;
        glUniform1f(sWYFlip, 1.0f);
        glUniform1f(sWScaleY, 0.8f);
        glUniform1f(sWScaleX, 1.0f / layoutFit);
        glUniform2f(sWOffset, 0.0f, 0.0f);
        glUniform1f(sWFade, sBootWaveBrightness);   // 1.0 steady; boot ramps 0->1
        // Wave base tint: silvery by default, blended toward the user-chosen
        // Colour by the animated strength (web updateWaveTintConstants writes
        // dispTint into the ribbon base-tint constants so the wave + glints pick
        // up the theme colour). Original (strength 0) keeps the tuned silver.
        float wtR = 0.96f, wtG = 0.97f, wtB = 1.00f;
        if (sThemeStrCur > 0.001f) {
            wtR += (sThemeCurR - wtR) * sThemeStrCur;
            wtG += (sThemeCurG - wtG) * sThemeStrCur;
            wtB += (sThemeCurB - wtB) * sThemeStrCur;
        }
        glUniform3f(sWTint, wtR, wtG, wtB);
        glUniform1f(sWAlpha, 0.15f);
        glUniform1f(sWSilk, 1.0f);
        glUniform1f(sWSpecW, 0.30f);
        glUniform1f(sWSpecExp, 44.8563f);
        glUniform2f(sWYFade, 10.0f, 11.0f);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, sWaveIBO);
        glDrawElements(GL_TRIANGLES, sWaveIndexCount, GL_UNSIGNED_SHORT, (const void*)0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    // Composite the work buffer to the panel with the exp2 tonemap. The quad
    // covers ps3::gFrame* (letterbox bars stay whatever the caller cleared) and
    // uRotation maps to the physical panel under DRM GL rotation.
    glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prevFbo);
    glViewport(prevVp[0], prevVp[1], prevVp[2], prevVp[3]);
    glDisable(GL_BLEND);
    glUseProgram(sCompProg);
    float fx0 = ps3::gFrameX, fy0 = ps3::gFrameY;
    float fx1 = fx0 + ps3::gFrameW, fy1 = fy0 + ps3::gFrameH;
    // device px -> panel NDC (y-down device to y-up NDC)
    float nx0 = fx0 / panelW * 2.0f - 1.0f, nx1 = fx1 / panelW * 2.0f - 1.0f;
    float ny0 = 1.0f - fy0 / panelH * 2.0f, ny1 = 1.0f - fy1 / panelH * 2.0f;
    const GLfloat cv[] = {
        nx0, ny1, 0.f, 0.f,   nx1, ny1, 1.f, 0.f,   nx1, ny0, 1.f, 1.f,
        nx0, ny1, 0.f, 0.f,   nx1, ny0, 1.f, 1.f,   nx0, ny0, 0.f, 1.f,
    };
    static const GLfloat kIdentity[4] = {1.f, 0.f, 0.f, 1.f};
    const GLfloat* rm = rotMat2 ? rotMat2 : kIdentity;
    glUniformMatrix2fv(sCompRot, 1, GL_FALSE, rm);
    glUniform1f(sCompExposure, 1.05f);
    glUniform1f(sCompWhite, 0.899181f);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, sWorkTex);
    glUniform1i(sCompTex, 0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glVertexAttribPointer(sCompPos, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), cv);
    glEnableVertexAttribArray(sCompPos);
    glVertexAttribPointer(sCompUV, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), cv + 2);
    glEnableVertexAttribArray(sCompUV);
    glDrawArrays(GL_TRIANGLES, 0, 6);

    // Leave no enabled client/VBO attrib arrays behind for the menu pass.
    glDisableVertexAttribArray(sCompPos);
    glDisableVertexAttribArray(sCompUV);
    if (sWClip >= 0)   glDisableVertexAttribArray(sWClip);
    if (sWNormal >= 0) glDisableVertexAttribArray(sWNormal);
    if (sWUV >= 0)     glDisableVertexAttribArray(sWUV);
    glActiveTexture(GL_TEXTURE0);

    // Firmware glitter field: additive point-sprite glints drawn to the PANEL
    // AFTER the composite (so the faint HDR glints survive the tonemap, matching
    // the web). The work-space position uses the same scaleX/yFlip as the wave
    // and is mapped into the frame rect (nx0,ny1,nx1,ny0) and rotated by rm (DRM
    // rotation) - correct on every orientation. NOTE: scaleY is 1.0 here, NOT the
    // wave's 0.8: the web applies no Y-scale to particles (their cloud Y is
    // authored to land on the crest directly), so re-applying the wave's 0.8
    // double-compresses the band toward centre and lifts it off the wave.
    // ps3part restores the standard blend when done.
    if (sParticlesEnabled) {
        float layoutFit = ps3::LAYOUT_FIT > 0.0f ? ps3::LAYOUT_FIT : 1.0f;
        const float frameNdc[4] = { nx0, ny1, nx1, ny0 };
        ps3part::render(1.0f / layoutFit, 1.0f, 1.0f, (float)fh, nightDayBlend,
                        (float)(sSeqElapsed * 0.4), frameNdc, rm);
    }

    // Restore the standard alpha blend the menu/UI pass relies on (we used
    // additive for the wave and disabled blend for the composite).
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
}

} // namespace ps3bg
} // namespace android
