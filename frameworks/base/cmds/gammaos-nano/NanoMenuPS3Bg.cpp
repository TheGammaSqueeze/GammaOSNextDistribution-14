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
// FS_BG (gradient) locations
static GLint  sBgPos, sBgUV, sBgTexCurDay, sBgTexNxtDay, sBgTexCurNight, sBgTexNxtNight,
              sBgMonthTime, sBgNightDayBlend, sBgNightBrightness;
// wave locations
static GLint  sWClip, sWNormal, sWUV, sWYFlip, sWScaleY, sWScaleX, sWOffset,
              sWFade, sWTint, sWAlpha, sWSilk, sWSpecW, sWSpecExp, sWYFade;
// blit locations
static GLint  sBlitPos, sBlitUV, sBlitTex;
// composite locations
static GLint  sCompPos, sCompUV, sCompTex, sCompRot, sCompExposure, sCompWhite;

// month textures
static GLuint sTexCurDay = 0, sTexNxtDay = 0, sTexCurNight = 0, sTexNxtNight = 0;
static int    sLoadedMonth = -1;            // calendar month (0-11) the textures hold

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

// Steady FS_BG: per-month hue from the texture column at x=0.5, with the
// measured dark-top/bright-bottom value+saturation ramp, corner vignette and
// day/night dimming. Active-path port of index.html main() 2879-3337 (boot /
// HSV-tint / month-base / silk branches dropped: inactive in steady state).
static const char* FS_BG =
    "precision mediump float;\n"
    "varying vec2 vUV;\n"
    "uniform sampler2D uTexCurDay;\n"
    "uniform sampler2D uTexNxtDay;\n"
    "uniform sampler2D uTexCurNight;\n"
    "uniform sampler2D uTexNxtNight;\n"
    "uniform float uMonthTime;\n"
    "uniform float uNightDayBlend;\n"
    "uniform float uNightBrightness;\n"
    "vec3 rgb2hsv(vec3 c){\n"
    "  float cmin=min(c.r,min(c.g,c.b)), cmax=max(c.r,max(c.g,c.b)), d=cmax-cmin;\n"
    "  float h=0.0;\n"
    "  if(d>0.0){\n"
    "    float dr=(cmax-c.r)/d, dg=(cmax-c.g)/d, db=(cmax-c.b)/d;\n"
    "    if(c.r>=cmax) h=db-dg; else if(c.g>=cmax) h=2.0+dr-db; else h=4.0+dg-dr;\n"
    "    h/=6.0; if(h<0.0) h+=1.0;\n"
    "  }\n"
    "  float s=(cmax>0.0)?d/cmax:0.0;\n"
    "  return vec3(h,s,cmax);\n"
    "}\n"
    "vec3 hsv2rgb(vec3 hsv){\n"
    "  float h=hsv.x,s=hsv.y,v=hsv.z, hh=h*6.0, ff=hh-floor(hh);\n"
    "  float p=v*(1.0-s), q=v*(1.0-s*ff), t=v*(1.0-s*(1.0-ff));\n"
    "  if(hh<1.0) return vec3(v,t,p); else if(hh<2.0) return vec3(q,v,p);\n"
    "  else if(hh<3.0) return vec3(p,v,t); else if(hh<4.0) return vec3(p,q,v);\n"
    "  else if(hh<5.0) return vec3(t,p,v); else return vec3(v,p,q);\n"
    "}\n"
    "void main(){\n"
    "  vec2 gradUV = vec2(0.5, 1.0 - vUV.y);\n"
    "  vec3 t1d = texture2D(uTexCurDay, gradUV).rgb.grb;\n"
    "  vec3 t2d = texture2D(uTexNxtDay, gradUV).rgb.grb;\n"
    "  vec3 t1n = texture2D(uTexCurNight, gradUV).rgb.grb;\n"
    "  vec3 t2n = texture2D(uTexNxtNight, gradUV).rgb.grb;\n"
    "  float mt = clamp(uMonthTime, 0.0, 1.0);\n"
    "  vec3 dayColor = mix(t1d, t2d, mt);\n"
    "  vec3 nightColor = mix(t1n, t2n, mt);\n"
    "  vec3 gradColor = mix(dayColor, nightColor, uNightDayBlend);\n"
    "  gradColor = mix(gradColor, gradColor * uNightBrightness, uNightDayBlend);\n"
    "  float screenY = 1.0 - vUV.y;\n"
    "  vec3 gHSV = rgb2hsv(gradColor);\n"
    "  float vRamp = mix(0.078, 0.49, pow(screenY, 0.92));\n"
    "  float sRamp = mix(0.66, 0.55, screenY);\n"
    "  float texSat = clamp(gHSV.y / 0.6, 0.0, 1.0);\n"
    "  float outS = sRamp * texSat;\n"
    "  float xn = clamp(vUV.x, 0.0, 1.0);\n"
    "  float vCorner = mix(mix(0.847, 0.925, xn), 1.0, 1.0 - screenY);\n"
    "  float outV = vRamp * mix(1.0, vCorner, 0.4);\n"
    "  gradColor = hsv2rgb(vec3(gHSV.x, outS, outV));\n"
    "  gradColor *= mix(1.0, 0.42, uNightDayBlend);\n"
    "  float nightRamp = smoothstep(-0.05, 0.80, screenY);\n"
    "  gradColor *= mix(1.0, nightRamp, uNightDayBlend);\n"
    "  vec3 finalColor = gradColor;\n"
    "  finalColor *= 1.05;\n"
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

// Load a PNG asset into a freshly-created RGBA GL texture. Returns 0 on failure.
static GLuint loadPngTexture(const char* sub, const char* file) {
    char path[256];
    if (!resolveAsset(sub, file, path, sizeof(path))) return 0;
    FILE* fp = fopen(path, "rb");
    if (!fp) return 0;
    png_structp png = png_create_read_struct(PNG_LIBPNG_VER_STRING, nullptr, nullptr, nullptr);
    if (!png) { fclose(fp); return 0; }
    png_infop info = png_create_info_struct(png);
    if (!info) { png_destroy_read_struct(&png, nullptr, nullptr); fclose(fp); return 0; }
    if (setjmp(png_jmpbuf(png))) {
        png_destroy_read_struct(&png, &info, nullptr);
        fclose(fp);
        return 0;
    }
    png_init_io(png, fp);
    png_read_info(png, info);
    int w = png_get_image_width(png, info);
    int h = png_get_image_height(png, info);
    int color = png_get_color_type(png, info);
    int depth = png_get_bit_depth(png, info);
    if (depth == 16) png_set_strip_16(png);
    if (color == PNG_COLOR_TYPE_PALETTE) png_set_palette_to_rgb(png);
    if (color == PNG_COLOR_TYPE_GRAY && depth < 8) png_set_expand_gray_1_2_4_to_8(png);
    if (png_get_valid(png, info, PNG_INFO_tRNS)) png_set_tRNS_to_alpha(png);
    if (color == PNG_COLOR_TYPE_GRAY || color == PNG_COLOR_TYPE_GRAY_ALPHA) png_set_gray_to_rgb(png);
    if (color == PNG_COLOR_TYPE_RGB || color == PNG_COLOR_TYPE_GRAY || color == PNG_COLOR_TYPE_PALETTE)
        png_set_filler(png, 0xFF, PNG_FILLER_AFTER);
    png_read_update_info(png, info);
    size_t rowbytes = png_get_rowbytes(png, info);
    uint8_t* pixels = (uint8_t*)malloc(rowbytes * (size_t)h);
    std::vector<png_bytep> rows((size_t)h);
    for (int y = 0; y < h; y++) rows[(size_t)y] = pixels + (size_t)y * rowbytes;
    png_read_image(png, rows.data());
    png_destroy_read_struct(&png, &info, nullptr);
    fclose(fp);

    GLuint tex = 0;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    free(pixels);
    return tex;
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

// ---------------------------------------------------------------------------
// month textures
// ---------------------------------------------------------------------------
static GLuint loadMonthTex(int idx /*1..24*/) {
    if (idx > 23) idx -= 12;          // Jan-night (24) has no asset; use the day texture
    if (idx < 0) idx = 0;
    char file[32];
    snprintf(file, sizeof(file), "month_bg_%02d.png", idx);
    return loadPngTexture("gradients", file);
}

static void loadMonthTextures(int month /*0..11*/) {
    if (sTexCurDay)   { glDeleteTextures(1, &sTexCurDay);   sTexCurDay = 0; }
    if (sTexNxtDay)   { glDeleteTextures(1, &sTexNxtDay);   sTexNxtDay = 0; }
    if (sTexCurNight) { glDeleteTextures(1, &sTexCurNight); sTexCurNight = 0; }
    if (sTexNxtNight) { glDeleteTextures(1, &sTexNxtNight); sTexNxtNight = 0; }
    int calMonth = month + 1;                       // 1..12
    int curDay = ((calMonth + 10) % 12) + 1;        // day texture index 1..12
    int nxtDay = ((calMonth + 11) % 12) + 1;
    sTexCurDay   = loadMonthTex(curDay);
    sTexNxtDay   = loadMonthTex(nxtDay);
    sTexCurNight = loadMonthTex(curDay + 12);
    sTexNxtNight = loadMonthTex(nxtDay + 12);
    sLoadedMonth = month;
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
            sBgTexCurDay = glGetUniformLocation(sBgProg, "uTexCurDay");
            sBgTexNxtDay = glGetUniformLocation(sBgProg, "uTexNxtDay");
            sBgTexCurNight = glGetUniformLocation(sBgProg, "uTexCurNight");
            sBgTexNxtNight = glGetUniformLocation(sBgProg, "uTexNxtNight");
            sBgMonthTime = glGetUniformLocation(sBgProg, "uMonthTime");
            sBgNightDayBlend = glGetUniformLocation(sBgProg, "uNightDayBlend");
            sBgNightBrightness = glGetUniformLocation(sBgProg, "uNightBrightness");
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
    if (sLoadedMonth < 0) {
        time_t tt = time(nullptr);
        struct tm lt;
        localtime_r(&tt, &lt);
        loadMonthTextures(lt.tm_mon);
    }
    sReady = sBgProg && sWaveProg && sBlitProg && sCompProg &&
             sWaveGeoReady && sSeqReady && sTexCurDay && sTexCurNight;
    if (sReady) ALOGI("ps3bg: ready");
    return sReady;
}

bool ready() { return sReady; }

void invalidateGradient() { sGradDirty = true; }

void shutdown() {
    if (sBgProg) glDeleteProgram(sBgProg);
    if (sWaveProg) glDeleteProgram(sWaveProg);
    if (sBlitProg) glDeleteProgram(sBlitProg);
    if (sCompProg) glDeleteProgram(sCompProg);
    GLuint texs[] = {sTexCurDay, sTexNxtDay, sTexCurNight, sTexNxtNight, sGradTex, sWorkTex};
    glDeleteTextures(6, texs);
    GLuint fbos[] = {sGradFbo, sWorkFbo};
    glDeleteFramebuffers(2, fbos);
    GLuint bufs[] = {sWaveClipVBO, sWaveAttrVBO, sWaveIBO, sQuadVBO};
    glDeleteBuffers(4, bufs);
    sBgProg = sWaveProg = sBlitProg = sCompProg = 0;
    sTexCurDay = sTexNxtDay = sTexCurNight = sTexNxtNight = 0;
    sGradFbo = sWorkFbo = sGradTex = sWorkTex = 0;
    sWaveClipVBO = sWaveAttrVBO = sWaveIBO = sQuadVBO = 0;
    sSeqFrames.clear(); sWaveScratch.clear();
    sReady = false; sTriedInit = false; sWaveGeoReady = false; sSeqReady = false;
    sLoadedMonth = -1; sGradDirty = true;
}

// ---------------------------------------------------------------------------
// per-frame render
// ---------------------------------------------------------------------------
static void renderGradientCache(int fw, int fh, float monthTime, float nightDayBlend) {
    glBindFramebuffer(GL_FRAMEBUFFER, sGradFbo);
    glViewport(0, 0, fw, fh);
    glDisable(GL_BLEND);
    glUseProgram(sBgProg);
    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, sTexCurDay);   glUniform1i(sBgTexCurDay, 0);
    glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, sTexNxtDay);   glUniform1i(sBgTexNxtDay, 1);
    glActiveTexture(GL_TEXTURE2); glBindTexture(GL_TEXTURE_2D, sTexCurNight); glUniform1i(sBgTexCurNight, 2);
    glActiveTexture(GL_TEXTURE3); glBindTexture(GL_TEXTURE_2D, sTexNxtNight); glUniform1i(sBgTexNxtNight, 3);
    glUniform1f(sBgMonthTime, monthTime);
    glUniform1f(sBgNightDayBlend, nightDayBlend);
    glUniform1f(sBgNightBrightness, 0.514f);
    drawFullQuad(sBgPos, sBgUV);
    glActiveTexture(GL_TEXTURE0);
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
    float nightDayBlend = computeNightDayBlend(hour);
    if (lt.tm_mon != sLoadedMonth) loadMonthTextures(lt.tm_mon);
    int daysInMonth = 31;
    {
        static const int dim[12] = {31,28,31,30,31,30,31,31,30,31,30,31};
        daysInMonth = dim[lt.tm_mon];
        if (lt.tm_mon == 1 && ((lt.tm_year % 4 == 0 && lt.tm_year % 100 != 0) || lt.tm_year % 400 == 0))
            daysInMonth = 29;
    }
    float monthTime = (float)(lt.tm_mday - 1) / (float)(daysInMonth > 1 ? daysInMonth - 1 : 1);

    // Re-render the cached gradient only when month / day-night / month-progress
    // meaningfully changed (otherwise it is static frame-to-frame).
    float blendQ = floorf(nightDayBlend * 50.0f) / 50.0f;
    if (sGradDirty || lt.tm_mon != sGradMonth || fabsf(blendQ - sGradBlendQ) > 1e-4f) {
        renderGradientCache(fw, fh, monthTime, nightDayBlend);
        sGradDirty = false;
        sGradMonth = lt.tm_mon;
        sGradBlendQ = blendQ;
    }

    // Build the work buffer: gradient blit, then additive wave on top.
    animateWave(dt);
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
        glUniform1f(sWFade, 1.0f);
        glUniform3f(sWTint, 0.96f, 0.97f, 1.00f);
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
    // Restore the standard alpha blend the menu/UI pass relies on (we used
    // additive for the wave and disabled blend for the composite).
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
}

} // namespace ps3bg
} // namespace android
