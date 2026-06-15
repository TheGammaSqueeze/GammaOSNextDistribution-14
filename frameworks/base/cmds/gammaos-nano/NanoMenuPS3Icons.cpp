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

// GammaOS Nano PS3 XMB glass icons. 1:1 port of the web app's FS_ICON_GLASS
// (index.html 3981-4167) and ICON_MATERIAL_DAY (2503-2540): per-icon normal-map
// relight with two Blinn-Phong lights, live-wave chromatic refraction, an
// ambient ramp, a silver matcap envmap, two specular lobes, a fresnel rim and
// fresnel-driven translucency, composited with a premultiplied-alpha HDR
// tonemap. The refraction source is the ps3bg work texture (the live gradient +
// wave) sampled directly behind each icon and tonemapped to the on-screen
// colour. PS3 category/sub-item icons use the shipped nmap_NNN.png normal maps;
// console / RetroArch icons get a bevel normal generated from their alpha
// silhouette so they read in the identical glass style.

#define LOG_TAG "GammaOSNano"

#include "NanoMenu.h"
#include "NanoMenuPS3.h"
#include "NanoMenuPS3Bg.h"
#include "xmb_icons.h"            // kEmbeddedIcons (console icon PNG bytes)

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <vector>
#include <string>

// Self-contained JPEG/PNG/BMP decoder for user album art (no Skia / libjpeg
// runtime dependency, which keeps the minimal-boot nano process lean). Limited to
// the formats album art actually uses.
#define STB_IMAGE_IMPLEMENTATION
#define STBI_ONLY_JPEG
#define STBI_ONLY_PNG
#define STBI_ONLY_BMP
#define STBI_NO_FAILURE_STRINGS
#include "stb_image.h"

#include <GLES2/gl2.h>
#include <png.h>

#include <utils/Log.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

namespace android {

// The DRM GL rotation matrix (NanoMenuShaders.cpp); column-major 2x2. The glass
// quad is authored in logical device NDC and rotated to the physical panel here,
// exactly like every other nano shader program.
extern float sDrmRotMat[4];

// ---------------------------------------------------------------------------
// material (ICON_MATERIAL_DAY). Light dirs from PS3 angle convention:
//   dir = (sin(x_deg), sin(z_deg), cos(z_deg)*cos(x_deg)), normalized.
// ---------------------------------------------------------------------------
static void angleLight(float xDeg, float zDeg, float out[3]) {
    float xr = xDeg * (float)M_PI / 180.0f, zr = zDeg * (float)M_PI / 180.0f;
    float d[3] = { sinf(xr), sinf(zr), cosf(zr) * cosf(xr) };
    float l = sqrtf(d[0]*d[0] + d[1]*d[1] + d[2]*d[2]);
    out[0] = d[0]/l; out[1] = d[1]/l; out[2] = d[2]/l;
}
static const float kIconAmbient[3]  = {0.74f, 0.80f, 0.90f};
static const float kIconSpec[4]      = {100.0f, 30.0f, 2.4f, 1.6f};   // pow1,pow2,attn1,attn2
static const float kIconRefr[3]      = {-0.096f, -0.114f, -0.144f};
static const float kIconRefrScl      = 12.0f;
static const float kIconAttnDiffEnv[2] = {2.0f, 0.74f};
// Composite linear->display exposure: FS_COMPOSITE uses
// scene * (exposure/white) * log2(e), exposure 1.05, white 0.899181.
static const float kBgExposure = (1.05f / 0.899181f) * 1.442695f;

// ---------------------------------------------------------------------------
// shaders
// ---------------------------------------------------------------------------
static const char* VS_GLASS =
    "attribute vec2 aPos;\n"        // device NDC (pre-rotation logical space)
    "attribute vec2 aIconUV;\n"     // 0..1 across the icon (normal map lookup)
    "attribute vec2 aBgUV;\n"       // work-texture UV behind this vertex (refraction)
    "uniform mat2 uRotation;\n"
    "varying vec2 vUV;\n"
    "varying vec2 vScreenUV;\n"
    "void main(){\n"
    "  vUV = aIconUV;\n"
    "  vScreenUV = aBgUV;\n"
    "  gl_Position = vec4(uRotation * aPos, 0.0, 1.0);\n"
    "}\n";

static const char* FS_GLASS =
    "precision highp float;\n"
    "varying vec2 vUV;\n"
    "varying vec2 vScreenUV;\n"
    "uniform sampler2D uNormalMap;\n"
    "uniform sampler2D uAmbMap;\n"
    "uniform sampler2D uEnvMap;\n"
    "uniform sampler2D uBackgroundMap;\n"
    "uniform vec2  uBgRadiusUV;\n"
    "uniform vec3  uLight1;\n"
    "uniform vec3  uLight2;\n"
    "uniform vec3  uAmbient;\n"
    "uniform vec4  uSpecParams;\n"
    "uniform vec3  uRefr;\n"
    "uniform float uRefrScl;\n"
    "uniform vec2  uAttnDiffEnv;\n"
    "uniform vec4  uChangingColor;\n"
    "uniform float uBgExposure;\n"
    "void main(){\n"
    "  vec4 nm = texture2D(uNormalMap, vUV);\n"
    "  if (nm.a < 1.0/255.0) discard;\n"
    "  vec2 nxy = nm.rg * 2.0 - 1.0;\n"
    "  float nz = sqrt(max(0.0, 1.0 - dot(nxy, nxy)));\n"
    "  vec3 N = vec3(nxy, nz);\n"
    "  vec3 V = normalize(vec3((vUV - 0.5) * -0.3, 1.0));\n"
    "  vec3 H1 = normalize(V + uLight1);\n"
    "  vec3 H2 = normalize(V + uLight2);\n"
    "  float NdotH1 = max(dot(N, H1), 0.0);\n"
    "  float NdotH2 = max(dot(N, H2), 0.0);\n"
    "  float NdotV  = clamp(dot(N, V), 0.0, 1.0);\n"
    "  vec2 iconUVScale = uBgRadiusUV * 2.0;\n"
    "  vec2 refrDir = nxy * uRefrScl * iconUVScale;\n"
    "  vec2 uvR = vScreenUV + refrDir * uRefr.r;\n"
    "  vec2 uvG = vScreenUV + refrDir * uRefr.g;\n"
    "  vec2 uvB = vScreenUV + refrDir * uRefr.b;\n"
    "  float bgR = texture2D(uBackgroundMap, clamp(uvR, 0.0, 1.0)).r;\n"
    "  float bgG = texture2D(uBackgroundMap, clamp(uvG, 0.0, 1.0)).g;\n"
    "  float bgB = texture2D(uBackgroundMap, clamp(uvB, 0.0, 1.0)).b;\n"
    "  vec3 refracted = vec3(bgR, bgG, bgB);\n"
    "  refracted = vec3(1.0) - exp2(-refracted * uBgExposure);\n"   // linear -> on-screen
    "  float ambUVy = clamp(N.y * 0.5 + 0.5, 0.0, 1.0);\n"
    "  vec3 ambSample = texture2D(uAmbMap, vec2(0.5, ambUVy)).rgb;\n"
    "  vec3 ambient = uAmbient * (0.5 + 0.5 * ambSample);\n"
    "  vec2 envUV = N.xy * 0.5 + 0.5;\n"
    "  vec3 envCol = texture2D(uEnvMap, envUV).rgb * uAttnDiffEnv.y;\n"
    "  float spec1 = pow(NdotH1, uSpecParams.x) * uSpecParams.z;\n"
    "  float spec2 = pow(NdotH2, uSpecParams.y) * uSpecParams.w;\n"
    "  vec3 spec = vec3(spec1 + spec2);\n"
    "  float fres = pow(1.0 - NdotV, 2.0);\n"
    "  float shadowFactor = (1.0 - max(dot(N, uLight1), 0.0)) * 0.30;\n"
    "  vec3 highlights = ambient * 0.55\n"
    "                  + envCol * uAttnDiffEnv.x * 0.60\n"
    "                  + spec * 1.80\n"
    "                  + vec3(fres) * 0.75\n"
    "                  - vec3(0.0, 0.02, 0.05) * shadowFactor;\n"
    "  vec3 totalColor = refracted * 0.35 + highlights;\n"
    "  vec3 surface = vec3(1.0) - exp(-totalColor * 0.95);\n"
    "  surface = surface * 1.08;\n"
    "  float fresAlpha = 0.72 + 0.28 * fres;\n"   // slightly more opaque than web 0.55
    "  float alpha = nm.a * fresAlpha * uChangingColor.a;\n"
    "  surface = surface * uChangingColor.rgb;\n"
    "  gl_FragColor = vec4(surface * alpha, alpha);\n"   // premultiplied
    "}\n";

// ---------------------------------------------------------------------------
// local GL helpers
// ---------------------------------------------------------------------------
static GLuint glassCompile(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok = 0; glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[1024]; glGetShaderInfoLog(s, sizeof(log), nullptr, log);
        ALOGE("glassicon: shader compile failed: %s", log);
        glDeleteShader(s); return 0;
    }
    return s;
}

// libpng in-memory reader.
struct GlassMemPng { const uint8_t* d; size_t off; size_t sz; };
static void glassReadFromMem(png_structp p, png_bytep o, png_size_t c) {
    GlassMemPng* m = (GlassMemPng*)png_get_io_ptr(p);
    if (m->off + c > m->sz) { png_error(p, "read past end"); return; }
    memcpy(o, m->d + m->off, c); m->off += c;
}

// Decode a PNG (file path, or memory if data!=null) to RGBA. monoWhite forces
// RGB=255 keeping alpha (for the silvery icons). Returns false on failure.
static bool decodeRGBA(const char* path, const uint8_t* data, int dataSize,
                       int* outW, int* outH, std::vector<uint8_t>* out,
                       bool monoWhite) {
    FILE* fp = nullptr;
    GlassMemPng mem{data, 8, (size_t)dataSize};
    if (!data) {
        fp = fopen(path, "rb");
        if (!fp) return false;
        png_byte hdr[8];
        if (fread(hdr, 1, 8, fp) != 8 || png_sig_cmp(hdr, 0, 8)) { fclose(fp); return false; }
    } else if (dataSize < 8 || png_sig_cmp(data, 0, 8)) {
        return false;
    }
    png_structp png = png_create_read_struct(PNG_LIBPNG_VER_STRING, nullptr, nullptr, nullptr);
    if (!png) { if (fp) fclose(fp); return false; }
    png_infop info = png_create_info_struct(png);
    if (!info) { png_destroy_read_struct(&png, nullptr, nullptr); if (fp) fclose(fp); return false; }
    if (setjmp(png_jmpbuf(png))) { png_destroy_read_struct(&png, &info, nullptr); if (fp) fclose(fp); return false; }
    if (fp) png_init_io(png, fp);
    else    png_set_read_fn(png, &mem, glassReadFromMem);
    png_set_sig_bytes(png, 8);
    png_read_info(png, info);
    int w = png_get_image_width(png, info);
    int h = png_get_image_height(png, info);
    png_byte ct = png_get_color_type(png, info);
    png_byte bd = png_get_bit_depth(png, info);
    if (ct == PNG_COLOR_TYPE_PALETTE) png_set_palette_to_rgb(png);
    if (ct == PNG_COLOR_TYPE_GRAY && bd < 8) png_set_expand_gray_1_2_4_to_8(png);
    if (ct == PNG_COLOR_TYPE_GRAY || ct == PNG_COLOR_TYPE_GRAY_ALPHA) png_set_gray_to_rgb(png);
    if (bd == 16) png_set_strip_16(png);
    bool hasTrns = png_get_valid(png, info, PNG_INFO_tRNS) != 0;
    if (hasTrns) png_set_tRNS_to_alpha(png);
    bool hasAlpha = (ct & PNG_COLOR_MASK_ALPHA) || hasTrns;
    if (!hasAlpha) png_set_filler(png, 0xFF, PNG_FILLER_AFTER);
    png_read_update_info(png, info);
    out->resize((size_t)w * h * 4);
    std::vector<png_bytep> rows((size_t)h);
    for (int y = 0; y < h; y++) rows[(size_t)y] = out->data() + (size_t)y * w * 4;
    png_read_image(png, rows.data());
    png_destroy_read_struct(&png, &info, nullptr);
    if (fp) fclose(fp);
    if (monoWhite) {
        for (size_t p = 0; p + 3 < out->size(); p += 4) {
            (*out)[p] = 255; (*out)[p+1] = 255; (*out)[p+2] = 255;
        }
    }
    *outW = w; *outH = h;
    return true;
}

static GLuint uploadRGBA(const uint8_t* px, int w, int h, bool wantMipmap = false) {
    GLuint tex = 0;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, px);
    // Anti-alias the downscaled content icons (RetroArch system art and the
    // Recently Played thumbnails come in around 256px and show at ~108px) with
    // trilinear mipmaps. GLES2 only allows mipmaps on power-of-two textures, so
    // fall back to plain GL_LINEAR for odd-sized art (and for the non-content
    // callers - normal maps, glass support textures, boot plates - which pass
    // wantMipmap=false and must not be mip-filtered).
    bool pot = wantMipmap && w > 0 && h > 0 &&
               (w & (w - 1)) == 0 && (h & (h - 1)) == 0;
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER,
                    pot ? GL_LINEAR_MIPMAP_LINEAR : GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    if (pot) glGenerateMipmap(GL_TEXTURE_2D);
    return tex;
}

// ---------------------------------------------------------------------------
// init: program + ambient ramp + matcap
// ---------------------------------------------------------------------------
void NanoMenu::initGlassIcons() {
    if (mIconGlassReady || mIconGlassTried) return;
    mIconGlassTried = true;
    GLuint vs = glassCompile(GL_VERTEX_SHADER, VS_GLASS);
    GLuint fs = glassCompile(GL_FRAGMENT_SHADER, FS_GLASS);
    if (!vs || !fs) { if (vs) glDeleteShader(vs); if (fs) glDeleteShader(fs); return; }
    mIconGlassProgram = glCreateProgram();
    glAttachShader(mIconGlassProgram, vs);
    glAttachShader(mIconGlassProgram, fs);
    glBindAttribLocation(mIconGlassProgram, 0, "aPos");
    glBindAttribLocation(mIconGlassProgram, 1, "aIconUV");
    glBindAttribLocation(mIconGlassProgram, 2, "aBgUV");
    glLinkProgram(mIconGlassProgram);
    glDeleteShader(vs); glDeleteShader(fs);
    GLint ok = 0; glGetProgramiv(mIconGlassProgram, GL_LINK_STATUS, &ok);
    if (!ok) {
        char log[1024]; glGetProgramInfoLog(mIconGlassProgram, sizeof(log), nullptr, log);
        ALOGE("glassicon: link failed: %s", log);
        glDeleteProgram(mIconGlassProgram); mIconGlassProgram = 0; return;
    }
    mIconGlassLocPos      = glGetAttribLocation(mIconGlassProgram, "aPos");
    mIconGlassLocIconUV   = glGetAttribLocation(mIconGlassProgram, "aIconUV");
    mIconGlassLocBgUV     = glGetAttribLocation(mIconGlassProgram, "aBgUV");
    mIconGlassLocRot      = glGetUniformLocation(mIconGlassProgram, "uRotation");
    mIconGlassLocNormal   = glGetUniformLocation(mIconGlassProgram, "uNormalMap");
    mIconGlassLocAmb      = glGetUniformLocation(mIconGlassProgram, "uAmbMap");
    mIconGlassLocEnv      = glGetUniformLocation(mIconGlassProgram, "uEnvMap");
    mIconGlassLocBg       = glGetUniformLocation(mIconGlassProgram, "uBackgroundMap");
    mIconGlassLocLight1   = glGetUniformLocation(mIconGlassProgram, "uLight1");
    mIconGlassLocLight2   = glGetUniformLocation(mIconGlassProgram, "uLight2");
    mIconGlassLocAmbient  = glGetUniformLocation(mIconGlassProgram, "uAmbient");
    mIconGlassLocSpec     = glGetUniformLocation(mIconGlassProgram, "uSpecParams");
    mIconGlassLocRefr     = glGetUniformLocation(mIconGlassProgram, "uRefr");
    mIconGlassLocRefrScl  = glGetUniformLocation(mIconGlassProgram, "uRefrScl");
    mIconGlassLocAttn     = glGetUniformLocation(mIconGlassProgram, "uAttnDiffEnv");
    mIconGlassLocChanging = glGetUniformLocation(mIconGlassProgram, "uChangingColor");
    mIconGlassLocBgExp    = glGetUniformLocation(mIconGlassProgram, "uBgExposure");
    mIconGlassLocBgRad    = glGetUniformLocation(mIconGlassProgram, "uBgRadiusUV");

    // ambient ramp + matcap (preserve colours - these are not silhouettes)
    auto loadAsset = [&](const char* file) -> GLuint {
        char path[256];
        std::vector<uint8_t> px; int w = 0, h = 0;
        snprintf(path, sizeof(path), "/data/system/nano_xmb/assets/%s", file);
        if (!decodeRGBA(path, nullptr, 0, &w, &h, &px, false)) {
            snprintf(path, sizeof(path), "/system/etc/nano_xmb/assets/%s", file);
            if (!decodeRGBA(path, nullptr, 0, &w, &h, &px, false)) return 0;
        }
        return uploadRGBA(px.data(), w, h);
    };
    mIconGlassAmbTex = loadAsset("icon_amb.png");
    mIconGlassEnvTex = loadAsset("texenv.png");
    if (!mIconGlassAmbTex || !mIconGlassEnvTex) {
        ALOGE("glassicon: missing icon_amb/texenv (%u/%u)", mIconGlassAmbTex, mIconGlassEnvTex);
        // Still usable: the shader samples them but the lighting just loses the
        // ambient ramp / matcap. Treat as ready so glass still draws.
    }
    mIconGlassReady = true;
    ALOGI("glassicon: ready (prog=%u amb=%u env=%u)", mIconGlassProgram, mIconGlassAmbTex, mIconGlassEnvTex);
}

// ---------------------------------------------------------------------------
// normal maps
// ---------------------------------------------------------------------------
GLuint NanoMenu::loadPs3NmapTex(const char* file) {
    char path[256];
    std::vector<uint8_t> px; int w = 0, h = 0;
    snprintf(path, sizeof(path), "/data/system/nano_xmb/normalmaps/%s", file);
    if (!decodeRGBA(path, nullptr, 0, &w, &h, &px, false)) {
        snprintf(path, sizeof(path), "/system/etc/nano_xmb/normalmaps/%s", file);
        if (!decodeRGBA(path, nullptr, 0, &w, &h, &px, false)) return 0;
    }
    return uploadRGBA(px.data(), w, h);
}

// Music player control-panel / status / codec-badge icon (the web audioplayer set,
// images/audioplayer/icon_NNN.png). Colour PNGs (not silhouettes), cached by index;
// 0 cached on miss so it never re-reads. Lazy: only loaded on first Now-Playing use.
GLuint NanoMenu::mpIcon(int n) {
    auto it = mMpIconCache.find(n);
    if (it != mMpIconCache.end()) return it->second;
    char file[40];
    snprintf(file, sizeof(file), "icon_%03d.png", n);
    char path[256];
    std::vector<uint8_t> px; int w = 0, h = 0;
    // These are full-colour PNGs (silver/grey glyphs, dark drop-shadow plates); keep
    // their real RGB (monoWhite=false) so the panel does not turn into white blocks.
    snprintf(path, sizeof(path), "/data/system/nano_xmb/audioplayer/%s", file);
    if (!decodeRGBA(path, nullptr, 0, &w, &h, &px, false)) {
        snprintf(path, sizeof(path), "/system/etc/nano_xmb/audioplayer/%s", file);
        if (!decodeRGBA(path, nullptr, 0, &w, &h, &px, false)) { mMpIconCache[n] = 0; return 0; }
    }
    GLuint tex = uploadRGBA(px.data(), w, h, /*wantMipmap=*/true);
    mMpIconCache[n] = tex;
    mMpIconAR[n] = (h > 0) ? (float)w / (float)h : 1.0f;
    return tex;
}

// Aspect ratio (w/h) of an audioplayer icon, cached alongside its texture. The
// repeat/shuffle/repeat-one glyphs are non-square pills, so the panel must use the
// native aspect or they squash. Returns 1.0 before the icon is loaded.
float NanoMenu::mpIconAR(int n) {
    auto it = mMpIconAR.find(n);
    return (it != mMpIconAR.end()) ? it->second : 1.0f;
}

// Music jacket placeholder (assets/icon_fw_track.png, the web's default cover /
// plane_default_music_cover). Colour PNG, loaded once and cached in mMpJacketTex.
GLuint NanoMenu::mpJacket() {
    if (mMpJacketTex) return mMpJacketTex;
    char path[256];
    std::vector<uint8_t> px; int w = 0, h = 0;
    // Colour cover art (grey music-note plate); keep its real RGB (monoWhite=false).
    snprintf(path, sizeof(path), "/data/system/nano_xmb/assets/icon_fw_track.png");
    if (!decodeRGBA(path, nullptr, 0, &w, &h, &px, false)) {
        snprintf(path, sizeof(path), "/system/etc/nano_xmb/assets/icon_fw_track.png");
        if (!decodeRGBA(path, nullptr, 0, &w, &h, &px, false)) { mMpJacketTex = 0; return 0; }
    }
    mMpJacketTex = uploadRGBA(px.data(), w, h, /*wantMipmap=*/true);
    return mMpJacketTex;
}

// Box-average downscale of an RGBA image into dst (dw x dh).
static void artDownscaleRGBA(const uint8_t* src, int sw, int sh,
                             int dw, int dh, std::vector<uint8_t>& dst) {
    dst.resize((size_t)dw * dh * 4);
    for (int y = 0; y < dh; y++) {
        int sy0 = y * sh / dh, sy1 = (y + 1) * sh / dh; if (sy1 <= sy0) sy1 = sy0 + 1;
        for (int x = 0; x < dw; x++) {
            int sx0 = x * sw / dw, sx1 = (x + 1) * sw / dw; if (sx1 <= sx0) sx1 = sx0 + 1;
            uint32_t r = 0, g = 0, b = 0, a = 0, cnt = 0;
            for (int yy = sy0; yy < sy1 && yy < sh; yy++)
                for (int xx = sx0; xx < sx1 && xx < sw; xx++) {
                    const uint8_t* p = src + ((size_t)yy * sw + xx) * 4;
                    r += p[0]; g += p[1]; b += p[2]; a += p[3]; cnt++;
                }
            uint8_t* d = dst.data() + ((size_t)y * dw + x) * 4;
            if (cnt) { d[0] = r / cnt; d[1] = g / cnt; d[2] = b / cnt; d[3] = a / cnt; }
            else     { d[0] = d[1] = d[2] = 0; d[3] = 255; }
        }
    }
}

// Decode a JPEG/PNG/BMP album-art file to RGBA, downscaled so the longest side is
// <= maxDim (the jacket renders tiny, and this bounds GPU + transient memory on the
// 1GB unit). Returns false if the file is missing/undecodable or absurdly large.
static bool decodeArtRGBA(const char* path, int maxDim,
                          int* outW, int* outH, std::vector<uint8_t>* out) {
    int iw = 0, ih = 0, ic = 0;
    if (!stbi_info(path, &iw, &ih, &ic)) return false;          // missing / not an image (fast)
    if (iw <= 0 || ih <= 0 || (long)iw * ih > 4000000L) return false;   // cap ~4MP transient
    int w = 0, h = 0, n = 0;
    stbi_uc* data = stbi_load(path, &w, &h, &n, 4);             // force RGBA
    if (!data) return false;
    int longSide = w > h ? w : h;
    if (longSide > maxDim && longSide > 0) {
        int dw = w * maxDim / longSide, dh = h * maxDim / longSide;
        if (dw < 1) dw = 1; if (dh < 1) dh = 1;
        artDownscaleRGBA(data, w, h, dw, dh, *out);
        *outW = dw; *outH = dh;
    } else {
        out->assign(data, data + (size_t)w * h * 4);
        *outW = w; *outH = h;
    }
    stbi_image_free(data);
    return true;
}

void NanoMenu::mpFreeArt() {
    if (mMpArtTex) { glDeleteTextures(1, &mMpArtTex); mMpArtTex = 0; }
    mMpArtTi = -1;
}

// Album art for track ti: a per-track image (same name as the track file) overrides a
// per-folder image (same name as the folder); 0 means "no art -> use the placeholder".
// Cached in one slot, reloaded when the displayed track changes.
GLuint NanoMenu::mpTrackArt(int ti) {
    if (ti < 0 || ti >= (int)mMusicTracks.size()) return 0;
    if (ti == mMpArtTi) return mMpArtTex;        // already resolved for this track
    if (mMpArtTex) { glDeleteTextures(1, &mMpArtTex); mMpArtTex = 0; }
    mMpArtTi = ti;

    const std::string& file = mMusicTracks[ti].file;
    size_t slash = file.find_last_of('/');
    if (slash == std::string::npos) return 0;
    std::string dir = file.substr(0, slash);
    std::string fname = file.substr(slash + 1);
    size_t dot = fname.find_last_of('.');
    std::string trackBase = (dot != std::string::npos) ? fname.substr(0, dot) : fname;
    size_t pslash = dir.find_last_of('/');
    std::string folderName = (pslash != std::string::npos) ? dir.substr(pslash + 1) : dir;

    static const char* kExt[] = {".jpg", ".jpeg", ".png", ".JPG", ".JPEG", ".PNG", ".bmp"};
    // per-track art first (overrides), then per-folder album cover
    std::string bases[2] = { dir + "/" + trackBase, dir + "/" + folderName };
    for (int b = 0; b < 2; b++) {
        for (const char* e : kExt) {
            std::string p = bases[b] + e;
            int w = 0, h = 0; std::vector<uint8_t> px;
            if (decodeArtRGBA(p.c_str(), 256, &w, &h, &px)) {
                mMpArtTex = uploadRGBA(px.data(), w, h, /*wantMipmap=*/false);
                ALOGI("NanoMenu: album art %s (%dx%d)", p.c_str(), w, h);
                return mMpArtTex;
            }
        }
    }
    return 0;   // none -> caller falls back to the note placeholder
}

// Per-folder album cover for the XMB Music column: resolve <folder>/<foldername>.<img>
// from a representative track of the album and cache it by album name (small, 128px).
// 0 means no folder art (column shows the generic folder icon). Like the web XMB
// showing a thumbnail embedded in a photo folder.
GLuint NanoMenu::mpAlbumArt(const std::string& albumName) {
    auto cit = mMpAlbumArt.find(albumName);
    if (cit != mMpAlbumArt.end()) return cit->second;
    GLuint tex = 0;
    std::vector<int> tr = musicAlbumTrackIndices(albumName);
    if (!tr.empty() && tr[0] >= 0 && tr[0] < (int)mMusicTracks.size()) {
        const std::string& file = mMusicTracks[tr[0]].file;
        size_t slash = file.find_last_of('/');
        if (slash != std::string::npos) {
            std::string dir = file.substr(0, slash);
            size_t pslash = dir.find_last_of('/');
            std::string folderName = (pslash != std::string::npos) ? dir.substr(pslash + 1) : dir;
            static const char* kExt[] = {".jpg", ".jpeg", ".png", ".JPG", ".JPEG", ".PNG", ".bmp"};
            std::string base = dir + "/" + folderName;
            for (const char* e : kExt) {
                int w = 0, h = 0; std::vector<uint8_t> px;
                if (decodeArtRGBA((base + e).c_str(), 128, &w, &h, &px)) {
                    tex = uploadRGBA(px.data(), w, h, /*wantMipmap=*/false);
                    break;
                }
            }
        }
    }
    mMpAlbumArt[albumName] = tex;   // cache (0 = none/tried)
    return tex;
}

// Boot-intro plate (logo_white.png / footer_white.png). Forced mono-white from
// the alpha mask so drawIconTex tints it any colour; 2-tier resolve (dev then
// shipped) under the nano_xmb/boot sub-dir.
GLuint NanoMenu::loadPs3BootPlate(const char* name) {
    char path[256];
    std::vector<uint8_t> px; int w = 0, h = 0;
    snprintf(path, sizeof(path), "/data/system/nano_xmb/boot/%s", name);
    if (!decodeRGBA(path, nullptr, 0, &w, &h, &px, true)) {
        snprintf(path, sizeof(path), "/system/etc/nano_xmb/boot/%s", name);
        if (!decodeRGBA(path, nullptr, 0, &w, &h, &px, true)) return 0;
    }
    return uploadRGBA(px.data(), w, h);
}

GLuint NanoMenu::nmapForIcon(int iconIndex) {
    if (iconIndex < 0) return 0;
    auto it = mPs3NmapByIcon.find(iconIndex);
    if (it != mPs3NmapByIcon.end()) return it->second;
    char file[32];
    snprintf(file, sizeof(file), "nmap_%03d.png", iconIndex);
    GLuint tex = loadPs3NmapTex(file);
    mPs3NmapByIcon[iconIndex] = tex;   // cache even 0 so we do not retry every frame
    return tex;
}

// Generate a bevel normal map from a console icon's alpha silhouette. The
// silhouette is blurred to a smooth height field; the normal comes from the
// height gradient (R=nx, G=ny, A=mask) so the glass shader relights it in the
// identical silvery style as the PS3 normal-mapped icons.
// Generate a bevel normal map from an arbitrary white-on-alpha silhouette
// buffer (RGBA, alpha = mask). Refactored out of bevelForIconIdx so the
// RetroArch icon set and user PNGs get the identical glass relight. Returns a
// GL texture (R=nx, G=ny, B=255, A=original silhouette), 0 on failure.
GLuint NanoMenu::bevelFromRGBA(const uint8_t* px, int w, int h) {
    if (!px || w < 4 || h < 4) return 0;
    int N = w * h;
    std::vector<float> bufA((size_t)N), bufB((size_t)N), tmp((size_t)N);
    for (int i = 0; i < N; i++) bufA[(size_t)i] = px[(size_t)i * 4 + 3] / 255.0f;

    // Separable box blur (a few passes, ping-ponging two buffers) -> smooth
    // height field. Radius scales with icon size so the bevel band is a
    // consistent fraction of the icon.
    int rad = (w > h ? w : h) / 28; if (rad < 2) rad = 2;
    std::vector<float>* src = &bufA;
    std::vector<float>* dst = &bufB;
    for (int pass = 0; pass < 3; pass++) {
        const float* s0 = src->data();
        for (int y = 0; y < h; y++)               // horizontal: src -> tmp
            for (int x = 0; x < w; x++) {
                float s = 0.0f; int c = 0;
                for (int k = -rad; k <= rad; k++) {
                    int xx = x + k; if (xx < 0) xx = 0; if (xx > w - 1) xx = w - 1;
                    s += s0[(size_t)y * w + xx]; c++;
                }
                tmp[(size_t)y * w + x] = s / (float)c;
            }
        float* d0 = dst->data();
        for (int y = 0; y < h; y++)               // vertical: tmp -> dst
            for (int x = 0; x < w; x++) {
                float s = 0.0f; int c = 0;
                for (int k = -rad; k <= rad; k++) {
                    int yy = y + k; if (yy < 0) yy = 0; if (yy > h - 1) yy = h - 1;
                    s += tmp[(size_t)yy * w + x]; c++;
                }
                d0[(size_t)y * w + x] = s / (float)c;
            }
        std::vector<float>* t = src; src = dst; dst = t;   // swap
    }
    const std::vector<float>& H = *src;            // blurred result

    // Normal from height gradient. The bevel slope is tuned so the interior is
    // near-flat (N ~ +Z, silvery face) and edges curve (catch the lights/rim).
    const float kSlope = 6.0f;
    std::vector<uint8_t> nmap((size_t)N * 4);
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            int xm = x > 0 ? x - 1 : 0, xp = x < w - 1 ? x + 1 : w - 1;
            int ym = y > 0 ? y - 1 : 0, yp = y < h - 1 ? y + 1 : h - 1;
            float gx = (H[(size_t)y * w + xp] - H[(size_t)y * w + xm]) * 0.5f;
            float gy = (H[(size_t)yp * w + x] - H[(size_t)ym * w + x]) * 0.5f;
            // Height increases INTO the shape; the surface tilts outward at the
            // rim. Screen Y is down, so flip gy to match the PS3 nmap convention
            // (G = +Y up). nz reconstructed in the shader.
            float nx = -kSlope * gx;
            float ny =  kSlope * gy;
            float len = sqrtf(nx*nx + ny*ny + 1.0f);
            nx /= len; ny /= len;
            size_t o = (size_t)(y * w + x) * 4;
            nmap[o + 0] = (uint8_t)(int)((nx * 0.5f + 0.5f) * 255.0f + 0.5f);
            nmap[o + 1] = (uint8_t)(int)((ny * 0.5f + 0.5f) * 255.0f + 0.5f);
            nmap[o + 2] = 255;
            nmap[o + 3] = px[(size_t)(y * w + x) * 4 + 3];   // original silhouette
        }
    }
    return uploadRGBA(nmap.data(), w, h);
}

// Embedded console-icon bevel (0..17), cached per index.
GLuint NanoMenu::bevelForIconIdx(int iconIdx) {
    if (iconIdx < 0 || iconIdx >= 18) return 0;
    auto it = mPs3BevelByIconIdx.find(iconIdx);
    if (it != mPs3BevelByIconIdx.end()) return it->second;
    std::vector<uint8_t> px; int w = 0, h = 0;
    bool ok = decodeRGBA(nullptr, kEmbeddedIcons[iconIdx].data,
                         kEmbeddedIcons[iconIdx].size, &w, &h, &px, true);
    if (!ok || w < 4 || h < 4) { mPs3BevelByIconIdx[iconIdx] = 0; return 0; }
    GLuint tex = bevelFromRGBA(px.data(), w, h);
    mPs3BevelByIconIdx[iconIdx] = tex;
    return tex;
}

// Resolve a system iconRef to a (colour silhouette tex, glass bevel nmap) pair,
// caching by the full ref string. Grammar:
//   builtin:<n>            -> embedded 18-icon table + bevelForIconIdx
//   retroarch:<name>       -> /system/etc/nano_xmb/icons_retroarch/<name>.png (dev override
//                             /data/system/nano_xmb/icons_retroarch/) mono-white + bevelFromRGBA
//   core:<name>            -> /data/system/nano_icons/<name>.png
//   file:/abs/path.png     -> arbitrary user PNG
//   empty / unresolved     -> generic cartridge (icon 16)
void NanoMenu::resolveSystemIcon(const std::string& ref, GLuint* outTex, GLuint* outNmap) {
    if (outTex) *outTex = 0;
    if (outNmap) *outNmap = 0;
    auto fallback = [&]() {
        if (outTex)  *outTex  = mIconTextures[16];
        if (outNmap) *outNmap = bevelForIconIdx(16);
    };
    if (ref.empty()) { fallback(); return; }

    if (ref.compare(0, 8, "builtin:") == 0) {
        int n = atoi(ref.c_str() + 8);
        if (n < 0 || n > 17) { fallback(); return; }
        if (outTex)  *outTex  = mIconTextures[n];
        if (outNmap) *outNmap = bevelForIconIdx(n);
        return;
    }

    // cached (retroarch:/core:/file:) -> stored (tex, nmap)
    auto cached = mPs3IconRefCache.find(ref);
    if (cached != mPs3IconRefCache.end()) {
        if (outTex)  *outTex  = cached->second.first;
        if (outNmap) *outNmap = cached->second.second;
        return;
    }

    // Build the candidate file path(s) for this ref.
    std::vector<uint8_t> px; int w = 0, h = 0; bool ok = false;
    char path[512];
    if (ref.compare(0, 10, "retroarch:") == 0) {
        std::string name = ref.substr(10);
        snprintf(path, sizeof(path), "/data/system/nano_xmb/icons_retroarch/%s.png", name.c_str());
        ok = decodeRGBA(path, nullptr, 0, &w, &h, &px, true);
        if (!ok) {
            snprintf(path, sizeof(path), "/system/etc/nano_xmb/icons_retroarch/%s.png", name.c_str());
            ok = decodeRGBA(path, nullptr, 0, &w, &h, &px, true);
        }
    } else if (ref.compare(0, 5, "core:") == 0) {
        std::string name = ref.substr(5);
        snprintf(path, sizeof(path), "/data/system/nano_icons/%s.png", name.c_str());
        ok = decodeRGBA(path, nullptr, 0, &w, &h, &px, true);
    } else if (ref.compare(0, 5, "file:") == 0) {
        std::string p = ref.substr(5);
        ok = decodeRGBA(p.c_str(), nullptr, 0, &w, &h, &px, true);
    }

    if (!ok || w < 4 || h < 4) {
        // Cache the failure as the fallback so we do not retry decoding each build.
        GLuint ft = mIconTextures[16], fn = bevelForIconIdx(16);
        mPs3IconRefCache[ref] = std::make_pair(ft, fn);
        if (outTex)  *outTex  = ft;
        if (outNmap) *outNmap = fn;
        return;
    }
    GLuint tex  = uploadRGBA(px.data(), w, h, /*wantMipmap=*/true);
    GLuint nmap = bevelFromRGBA(px.data(), w, h);
    mPs3IconRefCache[ref] = std::make_pair(tex, nmap);
    if (outTex)  *outTex  = tex;
    if (outNmap) *outNmap = nmap;
}

// Decode a RetroArch icon by name (no .png) to a mono-white RGBA buffer. Dev
// override (/data/system/nano_xmb/icons_retroarch/) first, then the bundled set
// (/system/etc/nano_xmb/icons_retroarch/). Returns false if neither decodes.
bool NanoMenu::decodeRetroIconRGBA(const std::string& name, std::vector<uint8_t>* outPx,
                                   int* w, int* h) {
    if (!outPx || !w || !h) return false;
    char path[512];
    snprintf(path, sizeof(path), "/data/system/nano_xmb/icons_retroarch/%s.png", name.c_str());
    if (decodeRGBA(path, nullptr, 0, w, h, outPx, true)) return true;
    snprintf(path, sizeof(path), "/system/etc/nano_xmb/icons_retroarch/%s.png", name.c_str());
    return decodeRGBA(path, nullptr, 0, w, h, outPx, true);
}

// ---------------------------------------------------------------------------
// draw one glass icon (device px coords, like drawIconTex)
// ---------------------------------------------------------------------------
void NanoMenu::drawGlassIcon(GLuint nmapTex, float x, float y, float w, float h,
                             float cr, float cg, float cb, float alpha) {
    if (!mIconGlassReady || nmapTex == 0) return;
    GLuint bgTex = ps3bg::workTex();
    if (bgTex == 0) return;

    // device px -> NDC (pre-rotation logical), matching drawIconTex.
    float x0 = (x / mWidth) * 2.0f - 1.0f;          // left
    float y0 = 1.0f - ((y + h) / mHeight) * 2.0f;   // bottom (NDC up)
    float x1 = ((x + w) / mWidth) * 2.0f - 1.0f;    // right
    float y1 = 1.0f - (y / mHeight) * 2.0f;         // top (NDC up)

    // device px -> work-texture UV. The work texture spans ps3::gFrame* with
    // texcoord (0,0) at the frame bottom-left (GL y-up).
    float fx = ps3::gFrameX, fy = ps3::gFrameY, fw = ps3::gFrameW, fh = ps3::gFrameH;
    if (fw < 1.0f) fw = (float)mWidth;
    if (fh < 1.0f) fh = (float)mHeight;
    auto bgU = [&](float dx) { return (dx - fx) / fw; };
    auto bgV = [&](float dy) { return 1.0f - (dy - fy) / fh; };
    float bL = bgU(x), bR = bgU(x + w);
    float bTop = bgV(y), bBot = bgV(y + h);    // device-top -> larger V

    // 6 verts (two triangles), order matching drawIconTex.
    const GLfloat pos[] = { x0,y0,  x1,y0,  x1,y1,   x1,y1,  x0,y1,  x0,y0 };
    const GLfloat iuv[] = { 0,1,    1,1,    1,0,     1,0,    0,0,    0,1 };
    const GLfloat buv[] = { bL,bBot, bR,bBot, bR,bTop,  bR,bTop, bL,bTop, bL,bBot };

    // The two light vectors are pure functions of compile-time literals; compute
    // them once into file statics (identical sinf/cosf/sqrtf path -> bit-identical
    // to the per-call result) instead of redoing the trig for every icon.
    static float sGlassLight1[3], sGlassLight2[3];
    static bool  sGlassLightReady = false;
    if (!sGlassLightReady) {
        angleLight(-32.4f, 22.8f, sGlassLight1);
        angleLight(135.0f, 60.0f, sGlassLight2);
        sGlassLightReady = true;
    }

    glUseProgram(mIconGlassProgram);
    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, nmapTex);
    glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, mIconGlassAmbTex);
    glActiveTexture(GL_TEXTURE2); glBindTexture(GL_TEXTURE_2D, mIconGlassEnvTex);
    glActiveTexture(GL_TEXTURE3); glBindTexture(GL_TEXTURE_2D, bgTex);
    // Frame-invariant uniforms (rotation matrix, sampler-unit indices, the light
    // vectors and the material/refraction constants) are uploaded once per frame
    // on the first glass icon - mGlassUniformsSet is armed at the top of
    // renderPs3Xmb. mIconGlassProgram is glass-only, so its uniform state survives
    // the program switches (text/strokes) between icons. The textures are still
    // bound per icon (the normal map changes; the others are cheap to rebind).
    if (!mGlassUniformsSet) {
        glUniformMatrix2fv(mIconGlassLocRot, 1, GL_FALSE, sDrmRotMat);
        glUniform1i(mIconGlassLocNormal, 0);
        glUniform1i(mIconGlassLocAmb, 1);
        glUniform1i(mIconGlassLocEnv, 2);
        glUniform1i(mIconGlassLocBg, 3);
        glUniform3fv(mIconGlassLocLight1, 1, sGlassLight1);
        glUniform3fv(mIconGlassLocLight2, 1, sGlassLight2);
        glUniform3fv(mIconGlassLocAmbient, 1, kIconAmbient);
        glUniform4fv(mIconGlassLocSpec, 1, kIconSpec);
        glUniform3fv(mIconGlassLocRefr, 1, kIconRefr);
        glUniform1f(mIconGlassLocRefrScl, kIconRefrScl);
        glUniform2fv(mIconGlassLocAttn, 1, kIconAttnDiffEnv);
        glUniform1f(mIconGlassLocBgExp, kBgExposure);
        mGlassUniformsSet = true;
    }
    // Per-icon uniforms: the tint/alpha and the work-texture radius (icon size).
    glUniform4f(mIconGlassLocChanging, cr, cg, cb, alpha);
    glUniform2f(mIconGlassLocBgRad, (w * 0.5f) / fw, (h * 0.5f) / fh);

    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glVertexAttribPointer(mIconGlassLocPos, 2, GL_FLOAT, GL_FALSE, 0, pos);
    glEnableVertexAttribArray(mIconGlassLocPos);
    glVertexAttribPointer(mIconGlassLocIconUV, 2, GL_FLOAT, GL_FALSE, 0, iuv);
    glEnableVertexAttribArray(mIconGlassLocIconUV);
    glVertexAttribPointer(mIconGlassLocBgUV, 2, GL_FLOAT, GL_FALSE, 0, buv);
    glEnableVertexAttribArray(mIconGlassLocBgUV);

    // premultiplied-alpha blend (matches iconGLCanvas ONE / ONE_MINUS_SRC_ALPHA)
    glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    setUiBlend();   // restore for text/quads (separate-alpha in overlay)

    glDisableVertexAttribArray(mIconGlassLocPos);
    glDisableVertexAttribArray(mIconGlassLocIconUV);
    glDisableVertexAttribArray(mIconGlassLocBgUV);
    glActiveTexture(GL_TEXTURE0);
}

} // namespace android
