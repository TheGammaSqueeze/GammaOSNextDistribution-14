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
#include <dirent.h>
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
    "uniform float uWallpaperLift;\n"   // 0 wave / 1 custom wallpaper: make the glass icons more opaque + lighter
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
    // Custom wallpaper (esp. a busy video): the semi-transparent glass reads poorly over it, so raise the
    // opacity floor (mostly solid, keeping a touch of fresnel life) and lift the shade toward white.
    "  fresAlpha = mix(fresAlpha, 0.92 + 0.08 * fres, uWallpaperLift);\n"
    "  float alpha = nm.a * fresAlpha * uChangingColor.a;\n"
    "  surface = surface * uChangingColor.rgb;\n"
    "  surface = mix(surface, min(surface * 1.35 + 0.10, vec3(1.0)), uWallpaperLift);\n"   // lighter shade
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
    mIconGlassLocWpLift   = glGetUniformLocation(mIconGlassProgram, "uWallpaperLift");
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

// Load a DSi-theme colour sprite (an SVG rasterised to PNG, the real 4x asset) to a
// full-RGBA GL texture. Dev-override under /data first, then the bundled /system path.
// monoWhite=false so the sprite keeps its colours + alpha. 0 on failure (caller falls
// back to the procedural draw).
GLuint NanoMenu::ndsLoadTex(const char* name) {
    char path[256];
    std::vector<uint8_t> px; int w = 0, h = 0;
    snprintf(path, sizeof(path), "/data/system/nano_xmb/nds/%s.png", name);
    if (!decodeRGBA(path, nullptr, 0, &w, &h, &px, false)) {
        snprintf(path, sizeof(path), "/system/etc/nano_xmb/nds/%s.png", name);
        if (!decodeRGBA(path, nullptr, 0, &w, &h, &px, false)) return 0;
    }
    if (w < 2 || h < 2) return 0;
    return uploadRGBA(px.data(), w, h);
}

// Value-preserving HSV helpers (file-local; the same maths renderer's ndsRecolor uses, applied
// per texel here so a baked sprite can be hue-rotated without crushing its shadows).
static void ndsPxRgb2Hsv(float r, float g, float b, float& h, float& s, float& v) {
    float mx = fmaxf(r, fmaxf(g, b)), mn = fminf(r, fminf(g, b)), d = mx - mn;
    v = mx; s = (mx <= 0.0f) ? 0.0f : d / mx;
    if (d <= 1e-6f) { h = 0.0f; return; }
    if (mx == r)      h = 60.0f * fmodf(((g - b) / d), 6.0f);
    else if (mx == g) h = 60.0f * (((b - r) / d) + 2.0f);
    else              h = 60.0f * (((r - g) / d) + 4.0f);
    if (h < 0.0f) h += 360.0f;
}
static void ndsPxHsv2Rgb(float h, float s, float v, float& r, float& g, float& b) {
    h = fmodf(h, 360.0f); if (h < 0.0f) h += 360.0f;
    float c = v * s, x = c * (1.0f - fabsf(fmodf(h / 60.0f, 2.0f) - 1.0f)), m = v - c;
    float rr, gg, bb;
    if      (h <  60.0f) { rr = c; gg = x; bb = 0; }
    else if (h < 120.0f) { rr = x; gg = c; bb = 0; }
    else if (h < 180.0f) { rr = 0; gg = c; bb = x; }
    else if (h < 240.0f) { rr = 0; gg = x; bb = c; }
    else if (h < 300.0f) { rr = x; gg = 0; bb = c; }
    else                 { rr = c; gg = 0; bb = x; }
    r = rr + m; g = gg + m; b = bb + m;
}

// The iconic DSi selection-frame sprite (glossy blue border + START platform) recoloured to
// follow the Colour accent while KEEPING its tonal structure - a per-texel value-preserving HSV
// hue rotation (accent hue - reference azure hue, saturation scaled once), exactly like the
// procedural chrome's ndsRecolor. The old path multiplied the sprite by accent/refBlue, which
// crushed the deep-blue shadow texels to near-black on a warm accent (the "black shadows" the
// user hit). Result is cached and only rebuilt when the Colour index changes; at "Original" the
// untouched baked-blue sprite is returned. Falls back to the blue sprite if the base cannot load.
GLuint NanoMenu::ndsFrameTexAccented() {
    if (ndsAccentIsDefault()) return mNdsFrameTex;                  // Original: baked blue verbatim
    if (mNdsFrameTexAccent && mNdsFrameAccentIdx == mPs3ColorIdx)   // cached for this accent
        return mNdsFrameTexAccent;
    // Decode the base sprite once and keep its pixels (cheap to re-tint on later accent changes).
    if (mNdsFrameBasePx.empty()) {
        char path[256]; int w = 0, h = 0; std::vector<uint8_t> px;
        snprintf(path, sizeof(path), "/data/system/nano_xmb/nds/nds_frame.png");
        if (!decodeRGBA(path, nullptr, 0, &w, &h, &px, false)) {
            snprintf(path, sizeof(path), "/system/etc/nano_xmb/nds/nds_frame.png");
            if (!decodeRGBA(path, nullptr, 0, &w, &h, &px, false)) return mNdsFrameTex;
        }
        if (w < 2 || h < 2) return mNdsFrameTex;
        mNdsFrameBasePx.swap(px); mNdsFrameBaseW = w; mNdsFrameBaseH = h;
    }
    // One hue delta + saturation scale for the whole sprite (accent vs reference DSi azure).
    static const float kRefBlue[3] = {0.094f, 0.573f, 0.922f};
    float ar, ag, ab; ndsAccentRGB(ar, ag, ab);
    float refH, refS, refV;  ndsPxRgb2Hsv(kRefBlue[0], kRefBlue[1], kRefBlue[2], refH, refS, refV);
    float accH, accS, accV;  ndsPxRgb2Hsv(ar, ag, ab, accH, accS, accV);
    float dH = accH - refH;
    float sScale = (refS > 1e-3f) ? (accS / refS) : 1.0f;
    if (sScale > 1.15f) sScale = 1.15f;                            // matches ndsRecolor's clamp
    std::vector<uint8_t> out = mNdsFrameBasePx;
    for (size_t p = 0; p + 3 < out.size(); p += 4) {
        if (out[p + 3] == 0) continue;                            // skip fully-transparent texels
        float r = out[p] / 255.0f, g = out[p + 1] / 255.0f, b = out[p + 2] / 255.0f;
        float h, s, v; ndsPxRgb2Hsv(r, g, b, h, s, v);
        float ns = s * sScale; if (ns > 1.0f) ns = 1.0f;
        ndsPxHsv2Rgb(h + dH, ns, v, r, g, b);
        r = fmaxf(0.0f, fminf(1.0f, r)); g = fmaxf(0.0f, fminf(1.0f, g)); b = fmaxf(0.0f, fminf(1.0f, b));
        out[p] = (uint8_t)(r * 255.0f + 0.5f); out[p + 1] = (uint8_t)(g * 255.0f + 0.5f); out[p + 2] = (uint8_t)(b * 255.0f + 0.5f);
    }
    if (mNdsFrameTexAccent) { glDeleteTextures(1, &mNdsFrameTexAccent); mNdsFrameTexAccent = 0; }
    mNdsFrameTexAccent = uploadRGBA(out.data(), mNdsFrameBaseW, mNdsFrameBaseH);
    mNdsFrameAccentIdx = mPs3ColorIdx;
    return mNdsFrameTexAccent ? mNdsFrameTexAccent : mNdsFrameTex;
}

// Decode a PNG embedded in the binary (no file dependency) -> RGBA texture. Used for the
// status-bar glyphs so they never depend on /data or /system being readable at render time.
GLuint NanoMenu::ndsLoadTexMem(const unsigned char* data, int len) {
    if (!data || len < 8) return 0;
    std::vector<uint8_t> px; int w = 0, h = 0;
    if (!decodeRGBA(nullptr, data, len, &w, &h, &px, false)) return 0;
    if (w < 2 || h < 2) return 0;
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

// Video player control-panel icons (the web app's images/videoplayer set,
// icon_NNN.png). Full-colour glyphs, cached by index; 0 cached on miss so it never
// re-reads. Lazy: only loaded on first video-player control-panel use. Mirrors mpIcon.
GLuint NanoMenu::vidIcon(int n) {
    auto it = mVidIconCache.find(n);
    if (it != mVidIconCache.end()) return it->second;
    char file[40];
    snprintf(file, sizeof(file), "icon_%03d.png", n);
    char path[256];
    std::vector<uint8_t> px; int w = 0, h = 0;
    snprintf(path, sizeof(path), "/data/system/nano_xmb/videoplayer/%s", file);
    if (!decodeRGBA(path, nullptr, 0, &w, &h, &px, false)) {
        snprintf(path, sizeof(path), "/system/etc/nano_xmb/videoplayer/%s", file);
        if (!decodeRGBA(path, nullptr, 0, &w, &h, &px, false)) { mVidIconCache[n] = 0; return 0; }
    }
    GLuint tex = uploadRGBA(px.data(), w, h, /*wantMipmap=*/true);
    mVidIconCache[n] = tex;
    mVidIconAR[n] = (h > 0) ? (float)w / (float)h : 1.0f;
    return tex;
}

float NanoMenu::vidIconAR(int n) {
    auto it = mVidIconAR.find(n);
    return (it != mVidIconAR.end()) ? it->second : 1.0f;
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

    // Resolved on the art worker, not here.
    //
    // This runs from the Now-Playing draw, and the probe it used to do inline (2 basenames x 7
    // extensions, each a decode attempt) is one round trip per miss on a network share - enough on
    // its own to hold the render thread past the watchdog and get the process killed. Returning 0
    // draws the note placeholder for a few frames instead; mpDrainAlbumArt() fills mMpArtTex in
    // when the worker is done, and discards the result if the track has changed since.
    (void)trackBase; (void)folderName; (void)dir;
    mpStartArtWorker();
    {
        std::lock_guard<std::mutex> lk(mMpArtLock);
        // Keyed on the path, so re-entering the same track while the worker is still on it does
        // not queue it twice.
        if (mMpArtPending.insert(file).second) {
            MpArtJob job;
            job.album = file;     // not used for a track job; keeps the key and the job aligned
            job.track = file;
            job.isTrack = true;
            job.ti = ti;
            mMpArtQueue.push_back(std::move(job));
        }
    }
    mMpArtCv.notify_one();
    return 0;   // nothing yet -> caller falls back to the note placeholder
}

// Per-folder album cover for the XMB Music column: resolve <folder>/<foldername>.<img>
// from a representative track of the album and cache it by album name (small, 128px).
// 0 means no folder art (column shows the generic folder icon). Like the web XMB
// showing a thumbnail embedded in a photo folder.
// Find an album's cover and decode it to RGBA. Runs on the art worker, never on the render thread.
//
// The old version probed up to 80 paths (10 candidate basenames x 8 extensions) with a decode
// attempt each, then fell back to reading the first track's ID3v2 tag. On local storage those
// misses cost microseconds. On a network share every miss is a round trip to the server, so a
// single album could take tens of seconds - which is what made the draw path hang and got nano
// killed by its own render watchdog.
//
// Two changes: this is off the render thread, and the probe is now ONE directory listing matched
// in memory instead of dozens of speculative opens. That is both faster everywhere and, on a
// share, the difference between one request and eighty.
bool NanoMenu::mpResolveArtPixels(const std::string& firstTrackPath, int maxDim,
                                  int* outW, int* outH, std::vector<uint8_t>* outPx,
                                  bool preferTrackStem) {
    const size_t slash = firstTrackPath.find_last_of('/');
    if (slash == std::string::npos) return false;
    const std::string dir = firstTrackPath.substr(0, slash);
    const size_t pslash = dir.find_last_of('/');
    const std::string folderName = (pslash != std::string::npos) ? dir.substr(pslash + 1) : dir;

    auto lower = [](std::string s) {
        for (auto& c : s) if (c >= 'A' && c <= 'Z') c += 32;
        return s;
    };
    // Accepted stems, lowercased once. The folder's own name is the strongest hint, so it is
    // tried first; the rest are the conventional cover filenames.
    std::vector<std::string> stems;
    if (preferTrackStem) {
        // A cover named after the track itself beats the album's, which is how a per-track
        // override works in Now-Playing.
        const std::string fname = firstTrackPath.substr(slash + 1);
        const size_t dot = fname.find_last_of('.');
        stems.push_back(lower(dot == std::string::npos ? fname : fname.substr(0, dot)));
    }
    stems.push_back(lower(folderName));
    for (const char* nm : {"cover", "folder", "front", "album", "albumart", "thumb"})
        stems.push_back(nm);
    auto isArtExt = [&](const std::string& lname) {
        for (const char* e : {".jpg", ".jpeg", ".png", ".bmp", ".webp"}) {
            const size_t n = strlen(e);
            if (lname.size() > n && lname.compare(lname.size() - n, n, e) == 0) return true;
        }
        return false;
    };

    // One listing, then match in memory. Ranked so the folder-name cover wins over "cover.jpg".
    std::string best;
    int bestRank = 1 << 30;
    if (DIR* d = opendir(dir.c_str())) {
        while (struct dirent* e = readdir(d)) {
            if (e->d_name[0] == '.') continue;
            const std::string lname = lower(e->d_name);
            if (!isArtExt(lname)) continue;
            const size_t dot = lname.find_last_of('.');
            const std::string stem = (dot == std::string::npos) ? lname : lname.substr(0, dot);
            for (size_t i = 0; i < stems.size(); i++) {
                if (stem == stems[i] && (int)i < bestRank) { bestRank = (int)i; best = e->d_name; }
            }
        }
        closedir(d);
    }
    if (!best.empty() && decodeArtRGBA((dir + "/" + best).c_str(), maxDim, outW, outH, outPx))
        return true;

    // Fallback: the cover embedded in the first track's ID3v2 tag. Reading a tag means pulling the
    // head of the file, which is why this too has to stay off the render thread.
    return musicEmbeddedArtPixels(firstTrackPath, maxDim, outW, outH, outPx);
}

void NanoMenu::mpStartArtWorker() {
    if (mMpArtStarted) return;
    mMpArtStarted = true;
    mMpArtThread = std::thread([this]() {
        for (;;) {
            MpArtJob job;
            {
                std::unique_lock<std::mutex> lk(mMpArtLock);
                mMpArtCv.wait(lk, [this]{
                    return mMpArtQuit.load(std::memory_order_relaxed) || !mMpArtQueue.empty();
                });
                if (mMpArtQuit.load(std::memory_order_relaxed)) return;
                job = mMpArtQueue.front();
                mMpArtQueue.pop_front();
            }
            MpArtResult r;
            r.album = job.album;
            r.isTrack = job.isTrack;
            r.ti = job.ti;
            r.isPhoto = job.isPhoto;
            r.photoIdx = job.photoIdx;
            r.cacheFile = job.cacheFile;
            r.wpSlot = job.wpSlot;
            if (job.wpSlot >= 0) {
                // Wallpaper still on a share: decode to pixels here, upload on the render thread.
                photoDecodeCoverPixels(job.track, job.decodePx, &r.px);
                if (!r.px.empty()) { r.w = job.decodePx; r.h = job.decodePx; }
                {
                    std::lock_guard<std::mutex> lk(mMpArtLock);
                    mMpArtDone.push_back(std::move(r));
                }
                continue;
            }
            if (job.isPhoto) {
                // job.track holds the photo's path; decode to the cover size off-thread.
                if (photoDecodeCoverPixels(job.track, kPhotoCoverPx, &r.px)) {
                    r.w = kPhotoCoverPx; r.h = kPhotoCoverPx;
                }
                {
                    std::lock_guard<std::mutex> lk(mMpArtLock);
                    mMpArtDone.push_back(std::move(r));
                }
                continue;
            }
            // A failure is still a result: it is what stops the album being asked for again every
            // frame. The render thread turns an empty pixel buffer into a cached 0.
            mpResolveArtPixels(job.track, 256, &r.w, &r.h, &r.px, job.isTrack);
            {
                std::lock_guard<std::mutex> lk(mMpArtLock);
                mMpArtDone.push_back(std::move(r));
            }
        }
    });
}

void NanoMenu::mpStopArtWorker() {
    if (!mMpArtStarted) return;
    mMpArtQuit.store(true, std::memory_order_relaxed);
    mMpArtCv.notify_all();
    if (mMpArtThread.joinable()) mMpArtThread.join();
    mMpArtStarted = false;
}

void NanoMenu::mpRequestAlbumArt(const std::string& albumName) {
    // Resolve the first track on the render thread: it is a pure in-memory lookup, and it keeps
    // the worker from having to touch mMusicTracks (which the scanner rewrites under its own lock).
    std::vector<int> tr = musicAlbumTrackIndices(albumName);
    if (tr.empty() || tr[0] < 0 || tr[0] >= (int)mMusicTracks.size()) {
        mMpAlbumArt[albumName] = 0;   // nothing to look at; do not ask again
        return;
    }
    const std::string track = mMusicTracks[tr[0]].file;
    mpStartArtWorker();
    {
        std::lock_guard<std::mutex> lk(mMpArtLock);
        if (!mMpArtPending.insert(albumName).second) return;   // already queued or in flight
        mMpArtQueue.push_back(MpArtJob{albumName, track});
    }
    mMpArtCv.notify_one();
}

// Render thread: take whatever the worker finished and turn it into textures. Called once a frame,
// so a slow share costs a placeholder for a few frames rather than a dead menu.
void NanoMenu::mpDrainAlbumArt() {
    std::vector<MpArtResult> done;
    {
        std::lock_guard<std::mutex> lk(mMpArtLock);
        if (mMpArtDone.empty()) return;
        done.swap(mMpArtDone);
        for (const auto& r : done) mMpArtPending.erase(r.album);
    }
    for (auto& r : done) {
        if (r.wpSlot >= 0) {
            GLuint wt = 0;
            if (r.w > 0 && r.h > 0 && !r.px.empty()) wt = photoUploadCover(r.px.data(), r.w, r.h);
            if (r.wpSlot == 0) {
                if (mWpTexTop) glDeleteTextures(1, &mWpTexTop);
                mWpTexTop = wt; mWpTopW = r.w; mWpTopH = r.h;
                if (!wt) mWpPathTop.clear();       // failed: fall back to the wave
            } else {
                if (mWpTexBottom) glDeleteTextures(1, &mWpTexBottom);
                mWpTexBottom = wt; mWpBottomW = r.w; mWpBottomH = r.h;
                if (!wt) mWpPathBottom.clear();
            }
            mDisplayDirty = true;
            continue;
        }
        if (r.isPhoto) {
            GLuint ptex = 0;
            if (r.w > 0 && r.h > 0 && !r.px.empty()) {
                ptex = photoUploadCover(r.px.data(), r.w, r.h);
                // Write the disk cache here rather than on the worker: it is cheap, local, and
                // keeps all the cache bookkeeping on one thread.
                if (!r.cacheFile.empty()) photoWriteCoverCache(r.cacheFile, r.px.data(), r.w, r.h);
            }
            mPhotoCoverCache[r.photoIdx] = ptex;   // 0 = tried, none
            continue;
        }
        GLuint tex = 0;
        if (r.w > 0 && r.h > 0 && !r.px.empty())
            tex = uploadRGBA(r.px.data(), r.w, r.h, /*wantMipmap=*/false);
        if (r.isTrack) {
            // Drop it if the user has already moved on to another track: mMpArtTi is what the
            // player is showing now, and uploading a stale cover over it would be worse than none.
            if (r.ti == mMpArtTi) {
                if (mMpArtTex) glDeleteTextures(1, &mMpArtTex);
                mMpArtTex = tex;
            } else if (tex) {
                glDeleteTextures(1, &tex);
            }
        } else {
            mMpAlbumArt[r.album] = tex;   // 0 = tried, none found
        }
    }
    mDisplayDirty = true;
}

GLuint NanoMenu::mpAlbumArt(const std::string& albumName) {
    auto cit = mMpAlbumArt.find(albumName);
    if (cit != mMpAlbumArt.end()) return cit->second;   // resolved (0 = tried, none)
    mpRequestAlbumArt(albumName);   // queue it and draw the placeholder until it lands
    return 0;
}

// Free every resolved cover. Render thread only: these are GL textures, and keeping the GL calls in
// this file means the music code does not have to pull in GLES headers to invalidate its own cache.
void NanoMenu::mpClearAlbumArt() {
    for (auto& kv : mMpAlbumArt) if (kv.second) glDeleteTextures(1, &kv.second);
    mMpAlbumArt.clear();
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

// Framework/SystemUI vector icons, rasterised to mono-white silhouettes and
// shipped in /system/etc/nano_xmb/icons_ui/. They occupy the icon-index range at
// or above kUiIconBase and are glass-relit at runtime by bevelFromRGBA - the same
// path the console icons use - so they share the exact glass look. Assess the
// menu item's meaning before assigning one (they are generic UI concepts).
static const int kUiIconBase = 70;
static const char* uiIconName(int idx) {
    switch (idx) {
        case 70: return "rotate";      // Screen Orientation (auto-rotate arrows)
        case 71: return "bell";        // Notifications
        case 72: return "lock";        // Security
        case 73: return "brightness";  // Screen Brightness
        case 74: return "gear";        // System Settings / Quick Settings
        case 75: return "sdcard";      // Memory Card Utility
        case 76: return "usb";         // Accessory Settings
        case 77: return "power";       // spare power glyph
        case 78: return "bug";         // Developer Options
        case 79: return "palette";     // Theme Settings
        case 80: return "bulb";        // GammaRGB (LED lighting)
        case 81: return "bolt";        // Performance Mode
        case 82: return "clear";       // Kill All Apps (close/clear tile)
        case 83: return "logout";      // Close Current App (exit door)
        case 84: return "layers";      // Kill Background Apps (stacked cards)
        case 85: return "wifi";        // Quick Settings: Wi-Fi
        case 86: return "bluetooth";   // Quick Settings: Bluetooth
        case 87: return "present";     // Quick Settings: External as Primary
        case 88: return "fullscreen";  // Quick Settings: Immersive Mode
        case 89: return "moon";        // Quick Settings: Deep Sleep Mode
        case 90: return "landscape";   // Screen Orientation: Landscape
        case 91: return "portrait";    // Screen Orientation: Portrait
        default: return nullptr;
    }
}

GLuint NanoMenu::nmapForIcon(int iconIndex) {
    if (iconIndex < 0) return 0;
    auto it = mPs3NmapByIcon.find(iconIndex);
    if (it != mPs3NmapByIcon.end()) return it->second;
    GLuint tex = 0;
    if (iconIndex >= kUiIconBase) {
        // Framework UI icon: decode the mono silhouette and bevel it at runtime so
        // it is relit identically to the console icons (dev override first).
        const char* name = uiIconName(iconIndex);
        if (name) {
            char path[256];
            std::vector<uint8_t> px; int w = 0, h = 0;
            snprintf(path, sizeof(path), "/data/system/nano_xmb/icons_ui/%s.png", name);
            bool ok = decodeRGBA(path, nullptr, 0, &w, &h, &px, true);
            if (!ok) {
                snprintf(path, sizeof(path), "/system/etc/nano_xmb/icons_ui/%s.png", name);
                ok = decodeRGBA(path, nullptr, 0, &w, &h, &px, true);
            }
            if (ok && w >= 4 && h >= 4) tex = bevelFromRGBA(px.data(), w, h);
        }
    } else {
        char file[32];
        snprintf(file, sizeof(file), "nmap_%03d.png", iconIndex);
        tex = loadPs3NmapTex(file);
    }
    mPs3NmapByIcon[iconIndex] = tex;   // cache even 0 so we do not retry every frame
    return tex;
}

// Framework UI icon (index >= kUiIconBase) as a PLAIN mono silhouette texture. There is no
// colour xmb_icon_NNN.png for these framework icons - the DSi carousel needs a mono-white
// texture so it draws them as a flat dark glyph (drawIconTex dark), matching the console
// icons, instead of falling through to the glass relight (which the DSi cards do not use).
// The silhouette (icons_ui/<name>.png, white on alpha) is the same source nmapForIcon bevels;
// here it is uploaded straight, un-beveled. Returns 0 for non-framework indices.
GLuint NanoMenu::uiIconTexForIcon(int iconIndex) {
    if (iconIndex < kUiIconBase) return 0;
    const char* name = uiIconName(iconIndex);
    if (!name) return 0;
    char path[256];
    std::vector<uint8_t> px; int w = 0, h = 0;
    snprintf(path, sizeof(path), "/data/system/nano_xmb/icons_ui/%s.png", name);
    bool ok = decodeRGBA(path, nullptr, 0, &w, &h, &px, false);
    if (!ok) {
        snprintf(path, sizeof(path), "/system/etc/nano_xmb/icons_ui/%s.png", name);
        ok = decodeRGBA(path, nullptr, 0, &w, &h, &px, false);
    }
    if (ok && w >= 4 && h >= 4) return uploadRGBA(px.data(), w, h);
    return 0;
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
    // Right-size to the panel before generating the bevel. The bevel is a smooth,
    // low-frequency relight field drawn at the console-icon size, so a 256 source
    // is oversized on a small panel; this both shrinks the GPU texture and speeds
    // up the (scalar) blur. High-DPI panels keep the full source resolution.
    std::vector<uint8_t> capped;
    {
        int cap = ps3::iconTexCap(mWidth, mHeight, ps3::ITEM_ICON_SIZE, w > h ? w : h);
        if (cap > 0 && (w > cap || h > cap)) {
            int dw = w, dh = h;
            if (w >= h) { dw = cap; dh = (int)((long)h * cap / w); }
            else        { dh = cap; dw = (int)((long)w * cap / h); }
            if (dw < 4) dw = 4; if (dh < 4) dh = 4;
            artDownscaleRGBA(px, w, h, dw, dh, capped);
            px = capped.data(); w = dw; h = dh;
        }
    }
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

// Favourites loveheart, rasterised from the implicit heart curve so no PNG asset is needed.
// f(x,y) = (x^2 + y^2 - 1)^3 - x^2*y^3 < 0 is inside a heart (point at the bottom, lobes on
// top). We sample it into a white-on-alpha silhouette with 3x3 supersampled coverage for smooth
// edges, upload that as the flat colour tex (DSi/Minima tint it red via iconR/G/B), and bevel it
// into a normal map for the XMB glass relight (identical treatment to every console icon).
void NanoMenu::ensureHeartIcon() {
    if (mHeartIconTex && mHeartNmapTex) return;
    const int N = 128;                                  // source silhouette resolution
    std::vector<uint8_t> px((size_t)N * N * 4, 0);
    const int SS = 3;                                   // supersample per axis
    // Coordinate window centred on the heart's bounds (~x[-1.3,1.3], y[-1.4,1.3]); a small
    // vertical bias keeps the shape optically centred in the square, with a little margin.
    const float span = 2.9f, biasY = 0.18f;
    for (int iy = 0; iy < N; iy++) {
        for (int ix = 0; ix < N; ix++) {
            int cov = 0;
            for (int sy = 0; sy < SS; sy++) {
                for (int sx = 0; sx < SS; sx++) {
                    float fx = ((ix + (sx + 0.5f) / SS) / N - 0.5f) * span;
                    // image y grows downward; flip so the lobes sit at the TOP of the image.
                    float fy = (0.5f - (iy + (sy + 0.5f) / SS) / N) * span + biasY;
                    float t = fx * fx + fy * fy - 1.0f;
                    float f = t * t * t - fx * fx * fy * fy * fy;
                    if (f < 0.0f) cov++;
                }
            }
            if (cov > 0) {
                uint8_t a = (uint8_t)(cov * 255 / (SS * SS));
                size_t o = (size_t)(iy * N + ix) * 4;
                px[o + 0] = 255; px[o + 1] = 255; px[o + 2] = 255; px[o + 3] = a;   // white on alpha
            }
        }
    }
    mHeartIconTex = uploadRGBA(px.data(), N, N);
    mHeartNmapTex = bevelFromRGBA(px.data(), N, N);
}

// Procedural bevel normal map for a gamepad-tester button shape, cached by shape +
// aspect bucket so the Test Controller / Calibration buttons get the identical glass
// relight as the console icons (bevelFromRGBA), refracting the live XMB wave. round =
// a filled disc/ellipse; else a rounded rectangle. The silhouette is generated at the
// requested aspect (uniform bevel band all round, no stretch) with a transparent
// margin so the bevel has room to curve at the rim.
GLuint NanoMenu::gpGlassNmap(bool round, float wpx, float hpx) {
    float ar = (hpx > 0.5f) ? wpx / hpx : 1.0f;
    if (ar < 0.2f) ar = 0.2f; if (ar > 6.0f) ar = 6.0f;
    int bucket = (int)lroundf(ar * 8.0f); if (bucket < 1) bucket = 1;
    int key = (round ? 1 : 0) * 1048576 + bucket;
    auto it = mGpGlassNmaps.find(key);
    if (it != mGpGlassNmaps.end()) return it->second;
    // Texture dimensions at this aspect, ~112 px on the long side.
    int W, H;
    if (ar >= 1.0f) { W = (int)lroundf(112.0f * ar); H = 112; }
    else            { W = 112; H = (int)lroundf(112.0f / ar); }
    if (W < 8) W = 8; if (H < 8) H = 8; if (W > 340) W = 340; if (H > 340) H = 340;
    std::vector<uint8_t> px((size_t)W * H * 4, 0);
    const float cx = (W - 1) * 0.5f, cy = (H - 1) * 0.5f;
    const float mgn = 0.15f;                                  // transparent margin fraction
    const float hx = W * 0.5f * (1.0f - mgn), hy = H * 0.5f * (1.0f - mgn);
    const float rr = round ? 0.0f : fminf(hx, hy) * 0.42f;    // rect corner radius
    for (int y = 0; y < H; y++) {
        for (int x = 0; x < W; x++) {
            bool inside;
            if (round) {
                float ex = (x - cx) / hx, ey = (y - cy) / hy;
                inside = (ex * ex + ey * ey) <= 1.0f;
            } else {
                float qx = fabsf(x - cx) - (hx - rr), qy = fabsf(y - cy) - (hy - rr);
                float ax = fmaxf(qx, 0.0f), ay = fmaxf(qy, 0.0f);
                float d = sqrtf(ax * ax + ay * ay) + fminf(fmaxf(qx, qy), 0.0f) - rr;
                inside = d <= 0.0f;
            }
            if (inside) {
                size_t o = ((size_t)y * W + x) * 4;
                px[o + 0] = 255; px[o + 1] = 255; px[o + 2] = 255; px[o + 3] = 255;
            }
        }
    }
    GLuint tex = bevelFromRGBA(px.data(), W, H);   // hard edge; the bevel blur smooths it
    mGpGlassNmaps[key] = tex;
    return tex;
}

// Embedded console-icon bevel (0..17), cached per index.
GLuint NanoMenu::bevelForIconIdx(int iconIdx) {
    if (iconIdx < 0 || iconIdx >= 19) return 0;
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
                             float cr, float cg, float cb, float alpha, float rot) {
    if (!mIconGlassReady || nmapTex == 0) return;
    GLuint bgTex = ps3bg::workTex();
    if (bgTex == 0) return;

    // device px -> NDC (pre-rotation logical), matching drawIconTex.
    float x0 = (x / mWidth) * 2.0f - 1.0f;          // left
    float y0 = 1.0f - ((y + h) / mHeight) * 2.0f;   // bottom (NDC up)
    float x1 = ((x + w) / mWidth) * 2.0f - 1.0f;    // right
    float y1 = 1.0f - (y / mHeight) * 2.0f;         // top (NDC up)
    // Tumble corners about the icon centre (device space, isotropic) for the
    // PSP-clock blow-away. rot==0 keeps the axis-aligned quad below untouched.
    // BL=(x0,y0) BR=(x1,y0) TR=(x1,y1) TL=(x0,y1) in NDC.
    float BLx=x0,BLy=y0, BRx=x1,BRy=y0, TRx=x1,TRy=y1, TLx=x0,TLy=y1;
    if (rot != 0.0f) {
        float cx = x + w * 0.5f, cy = y + h * 0.5f;
        float co = cosf(rot), si = sinf(rot);
        auto rc = [&](float lx, float ly, float& nx, float& ny) {
            float rx = lx * co - ly * si, ry = lx * si + ly * co;
            nx = ((cx + rx) / mWidth) * 2.0f - 1.0f;
            ny = 1.0f - ((cy + ry) / mHeight) * 2.0f;
        };
        const float hw = w * 0.5f, hh = h * 0.5f;   // device y-down: +hh = bottom
        rc(-hw,  hh, BLx, BLy); rc( hw,  hh, BRx, BRy);
        rc( hw, -hh, TRx, TRy); rc(-hw, -hh, TLx, TLy);
    }

    // device px -> work-texture UV. The work texture spans ps3::gFrame* with
    // texcoord (0,0) at the frame bottom-left (GL y-up).
    float fx = ps3::gFrameX, fy = ps3::gFrameY, fw = ps3::gFrameW, fh = ps3::gFrameH;
    if (fw < 1.0f) fw = (float)mWidth;
    if (fh < 1.0f) fh = (float)mHeight;
    auto bgU = [&](float dx) { return (dx - fx) / fw; };
    auto bgV = [&](float dy) { return 1.0f - (dy - fy) / fh; };
    float bL = bgU(x), bR = bgU(x + w);
    float bTop = bgV(y), bBot = bgV(y + h);    // device-top -> larger V

    // 6 verts (two triangles), order matching drawIconTex: BL BR TR TR TL BL.
    const GLfloat pos[] = { BLx,BLy, BRx,BRy, TRx,TRy, TRx,TRy, TLx,TLy, BLx,BLy };
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
    // Wallpaper mode (still or video, wave off): make the glass icons more opaque + lighter so they read
    // over a busy custom background instead of half-dissolving into it.
    glUniform1f(mIconGlassLocWpLift,
                (wallpaperActive(mRenderingPanel) && !mXmbWave) ? 1.0f : 0.0f);

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
