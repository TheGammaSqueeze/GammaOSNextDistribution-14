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

// Music Globe visualizer - a compact port of the real XMB web globe (globe_mp.js).
// See NanoMenuMusicGlobe.h for the design summary. The earth shading constants, the
// analytic atmosphere, the sun disc and the bloom recipe are transcribed from the
// firmware (mapped from globe_mp.js); the camera replays the real scene-0..4 paths
// (NanoMenuMusicGlobeScenes.h, decoded from the captured MVP registers).

#include "NanoMenuMusicGlobe.h"
#include "NanoMenuMusicGlobeScenes.h"
#include "NanoMenuPS3.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <vector>

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <png.h>
#include <cutils/properties.h>

#include <utils/Log.h>

namespace android {
namespace ps3mpglobe {

// ---------------------------------------------------------------------------
// tunables (prop-overridable for on-device tuning without a rebuild)
// ---------------------------------------------------------------------------
static float TUNE_SUNBRI   = 60.0f;   // sun HDR core brightness (blooms into the corona)
static float TUNE_SUNHALO  = 2.2f;    // broad warm sun halo strength
static float TUNE_BLOOMTHR = 0.42f;   // bright-pass threshold (display luminance)
static float TUNE_BLOOMGAIN= 1.30f;   // additive bloom gain
static float TUNE_NIGHTBRI = 0.9f;    // night-lights (earth_night) brightness (web shows them subtly)
static float TUNE_RIM      = 0.50f;   // atmosphere fresnel rim strength (the always-on blue limb)
static float TUNE_HALO     = 0.70f;   // outer atmosphere halo strength
static float TUNE_HALOFALL  = 100.0f; // outer halo falloff rate (higher = tighter glow hugging the limb); tuned vs web
static float TUNE_DAYK      = 2.40f;  // lit-side (day) albedo gain; between firmware 1.5 and a no-bloom-haze lift, so the day side reads bright without washing out the night/twilight crescents

static float propF(const char* key, float def) {
    char v[PROPERTY_VALUE_MAX] = {};
    if (property_get(key, v, "") > 0 && v[0]) { float f = (float)atof(v); if (f != 0.0f || v[0] == '0') return f; }
    return def;
}

// ---------------------------------------------------------------------------
// shaders (GLES2)
// ---------------------------------------------------------------------------
static const char* FULL_VS =
    "attribute vec2 aPos;\n"
    "varying vec2 vUV;\n"   // [-1,1]
    "void main(){ vUV = aPos; gl_Position = vec4(aPos, 0.0, 1.0); }\n";

// Ray-march the earth + atmosphere + sun + stars, output the display-tonemapped scene.
static const char* SCENE_FS =
    "precision highp float;\n"
    "varying vec2 vUV;\n"
    "uniform vec3 uEye, uFwd, uRight, uUp;\n"
    "uniform float uTanHF, uAspect;\n"
    "uniform vec3 uSun;\n"
    "uniform sampler2D uDay, uNight, uClouds;\n"
    "uniform float uStars, uNightBri, uRim, uHalo, uSunBri, uSunHalo, uNightLift, uHaloFall, uDayK;\n"
    "const float PI = 3.14159265;\n"
    "vec2 erUV(vec3 n){\n"
    "  float u = atan(n.z, n.x) * (0.1591549) + 0.5;\n"
    "  float v = acos(clamp(n.y, -1.0, 1.0)) * (0.3183099);\n"
    "  return vec2(u, v);\n"
    "}\n"
    "float hash(vec3 p){ return fract(sin(dot(p, vec3(12.9898,78.233,37.719))) * 43758.5453); }\n"
    "void main(){\n"
    "  vec3 rd = normalize(uFwd + (vUV.x * uAspect * uRight + vUV.y * uUp) * uTanHF);\n"
    "  vec3 ro = uEye;\n"
    "  float b = dot(ro, rd);\n"
    "  float c = dot(ro, ro) - 1.0;\n"
    "  float disc = b*b - c;\n"
    "  vec3 col = vec3(0.0);\n"           // space
    "  bool hit = false;\n"
    "  if (disc > 0.0) {\n"
    "    float t = -b - sqrt(disc);\n"
    "    if (t > 0.0) {\n"
    "      hit = true;\n"
    "      vec3 p = ro + rd * t;\n"
    "      vec3 N = normalize(p);\n"
    "      vec2 uv = erUV(N);\n"
    "      vec3 alb = texture2D(uDay, uv).rgb;\n"
    "      float cloud = clamp(texture2D(uClouds, uv).r, 0.0, 1.0);\n"
    "      vec3 ntex = texture2D(uNight, uv).rgb;\n"
    "      float ndl = dot(N, uSun);\n"
    "      float day = 1.0 - smoothstep(0.62, -0.30, ndl);\n"
    "      vec3 dayC = mix(alb, vec3(1.0), cloud * 0.5) * uDayK * vec3(1.0,0.98,0.93);\n"  // brighter lit side (web earth reads bright); subtle warm cast
    "      vec3 nightC = ntex * uNightBri + uNightLift * (vec3(0.020,0.042,0.072)\n"  // city lights + per-scene cool ambient (darker/less blue so the lit crescents pop, matches web night)
    "                  + cloud * vec3(0.010,0.014,0.020));\n"
    "      vec3 termGlow = vec3(0.11,0.06,0.035) *\n"
    "         (smoothstep(0.32,-0.02,ndl) * (1.0 - smoothstep(-0.02,-0.42,ndl)));\n"  // wider twilight band
    "      col = mix(nightC, dayC, day) + termGlow * 0.7 * uNightLift;\n"
    "      vec3 V = normalize(uEye - p);\n"
    "      vec3 H = normalize(V + uSun);\n"
    "      float lum = dot(alb, vec3(0.299,0.587,0.114));\n"
    "      float wmask = clamp((alb.b - lum) * 4.0 + (0.22 - lum) * 2.5, 0.0, 1.0);\n"  // rough ocean
    "      float glint = pow(max(dot(N,H),0.0), 200.0) * wmask\n"
    "                   * clamp(dot(N,V)*3.0, 0.0, 1.0) * clamp(ndl+0.1, 0.0, 1.0);\n"
    "      col += clamp(0.6 * glint, 0.0, 0.5) * vec3(1.0,0.95,0.82);\n"
    "      float graze = 1.0 - clamp(dot(N,V), 0.0, 1.0);\n"
    "      float rim = pow(graze, 12.0);\n"
    "      col += uRim * rim * (1.0 - day*0.30) * vec3(0.45,0.65,1.0) * 6.0;\n"  // atmosphere limb on the surface; light-blue whitens at the core via tonemap, stays blue in falloff
    "      col *= 8.0;\n"                  // HDR scale
    "    }\n"
    "  }\n"
    "  if (!hit) {\n"
    "    float distC = sqrt(max(dot(ro,ro) - b*b, 0.0));\n"
    "    if (b < 0.0) {\n"                 // closest approach is IN FRONT of the camera (ray skims the globe)
    "      float limbD = distC - 1.0;\n"
    "      if (limbD > 0.0) {\n"
    "        float halo = exp(-limbD * uHaloFall);\n"  // tight glow hugging the limb so space above reads black (web look)
    "        col += uHalo * halo * vec3(0.45,0.65,1.0) * 34.0;\n"  // outer limb glow; bright light-blue whitens at the limb core, blue band outward (the web glowing ring)
    "      }\n"
    "    }\n"
    "    float sd = max(dot(rd, uSun), 0.0);\n"
    "    vec3 perp = rd - uSun * sd;\n"                               // screen-space offset from the sun centre
    "    float px = dot(perp, uRight), py = dot(perp, uUp);\n"
    "    float streak = exp(-(abs(px)*16.0 + abs(py)*120.0)) * sd;\n" // thin horizontal anisotropic flare spike
    "    col += vec3(1.0,0.93,0.82) * (pow(sd, 2200.0) * uSunBri + pow(sd, 120.0) * uSunHalo\n"
    "                                + streak * uSunHalo * 0.55);\n"
    "    if (uStars > 0.5) {\n"
    "      vec3 g = rd * 300.0; vec3 cell = floor(g);\n"   // finer grid: a dense field of small stars (web STARTEX look)
    "      float h = hash(cell);\n"
    "      if (h > 0.94) {\n"                              // more cells -> many faint stars (was a sparse few large blobs)
    "        vec3 fc = fract(g) - 0.5;\n"
    "        float d2 = dot(fc, fc);\n"
    "        float star = smoothstep(0.045, 0.0, d2) * (0.25 + 0.75 * hash(cell + 3.1));\n"  // small crisp points
    "        col += star * 0.65 * vec3(0.85,0.92,1.0);\n"  // faint, slightly cool
    "      }\n"
    "    }\n"
    "  }\n"
    "  vec3 L = col * 0.0986;\n"           // GLOW_SLUM*0.125 exposure
    "  vec3 toned = (L * (1.0 + L / 11.6225)) / (1.0 + L);\n"   // extended Reinhard, W=3.40918
    "  gl_FragColor = vec4(clamp(toned, 0.0, 1.0), 1.0);\n"
    "}\n";

// Bright-pass: extract the over-threshold (sun + lit limb) energy -> bloom source.
static const char* BRIGHT_FS =
    "precision mediump float;\n"
    "varying vec2 vUV;\n"
    "uniform sampler2D uTex;\n"
    "uniform float uThresh;\n"
    "void main(){\n"
    "  vec3 c = texture2D(uTex, vUV * 0.5 + 0.5).rgb;\n"
    "  float m = max(c.r, max(c.g, c.b));\n"
    "  float k = max(m - uThresh, 0.0) / max(1.0 - uThresh, 0.001);\n"
    "  gl_FragColor = vec4(c * k, 1.0);\n"
    "}\n";

// Separable Gaussian blur (9-tap, linear-sampled pairs -> 5 fetches).
static const char* BLUR_FS =
    "precision mediump float;\n"
    "varying vec2 vUV;\n"
    "uniform sampler2D uTex;\n"
    "uniform vec2 uStep;\n"   // texel step along the blur axis
    "void main(){\n"
    "  vec2 uv = vUV * 0.5 + 0.5;\n"
    "  vec3 acc = texture2D(uTex, uv).rgb * 0.227027;\n"
    "  acc += (texture2D(uTex, uv + uStep*1.3846).rgb + texture2D(uTex, uv - uStep*1.3846).rgb) * 0.316216;\n"
    "  acc += (texture2D(uTex, uv + uStep*3.2308).rgb + texture2D(uTex, uv - uStep*3.2308).rgb) * 0.070270;\n"
    "  gl_FragColor = vec4(acc, 1.0);\n"
    "}\n";

// Composite: scene + bloom*gain -> panel, with DRM rotation + crossfade alpha.
static const char* COMP_VS =
    "attribute vec2 aPos;\n"
    "attribute vec2 aUV;\n"
    "uniform mat2 uRotation;\n"
    "varying vec2 vUV;\n"
    "void main(){ vUV = aUV; gl_Position = vec4(uRotation * aPos, 0.0, 1.0); }\n";

static const char* COMP_FS =
    "precision mediump float;\n"
    "varying vec2 vUV;\n"
    "uniform sampler2D uScene, uBloomA, uBloomB;\n"
    "uniform float uGain, uAlpha, uFade;\n"
    "void main(){\n"
    "  vec3 s = texture2D(uScene, vUV).rgb;\n"
    "  vec3 bl = texture2D(uBloomA, vUV).rgb + texture2D(uBloomB, vUV).rgb;\n"
    "  vec3 c = clamp(s + bl * uGain, 0.0, 1.0) * uFade;\n"
    "  gl_FragColor = vec4(c, uAlpha);\n"
    "}\n";

// ---------------------------------------------------------------------------
// state
// ---------------------------------------------------------------------------
static bool sReady = false, sTried = false;
static GLuint sSceneProg = 0, sBrightProg = 0, sBlurProg = 0, sCompProg = 0;
static GLint scAPos, scEye, scFwd, scRight, scUp, scTanHF, scAspect, scSun,
             scDay, scNight, scClouds, scStars, scNightBri, scRim, scHalo, scSunBri, scSunHalo, scNightLift, scHaloFall, scDayK;
static GLint brAPos, brTex, brThresh;
static GLint blAPos, blTex, blStep;
static GLint cmAPos, cmAUV, cmScene, cmBloomA, cmBloomB, cmGain, cmAlpha, cmFade, cmRot;
static GLuint sTexDay = 0, sTexNight = 0, sTexClouds = 0;
static GLuint sQuadVBO = 0;

struct RT { GLuint fb, tex; int w, h; };
static RT sScene = {0,0,0,0}, sHalfA = {0,0,0,0}, sHalfB = {0,0,0,0}, sQuadA = {0,0,0,0}, sQuadB = {0,0,0,0};

// scene clock
static int sScn = 0;
static float sScnT = 0.0f;   // elapsed seconds in the current scene

// ---------------------------------------------------------------------------
// helpers
// ---------------------------------------------------------------------------
static GLuint compileShader(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok = 0; glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) { char log[512]; glGetShaderInfoLog(s, sizeof(log), nullptr, log);
               ALOGE("ps3mpglobe: shader: %s", log); }
    return s;
}
static GLuint linkProgram(const char* vs, const char* fs) {
    GLuint v = compileShader(GL_VERTEX_SHADER, vs), f = compileShader(GL_FRAGMENT_SHADER, fs);
    GLuint p = glCreateProgram();
    glAttachShader(p, v); glAttachShader(p, f);
    glLinkProgram(p);
    glDeleteShader(v); glDeleteShader(f);
    GLint ok = 0; glGetProgramiv(p, GL_LINK_STATUS, &ok);
    if (!ok) { char log[512]; glGetProgramInfoLog(p, sizeof(log), nullptr, log);
               ALOGE("ps3mpglobe: link: %s", log); glDeleteProgram(p); return 0; }
    return p;
}
static bool resolveAsset(const char* file, char* out, size_t n) {
    snprintf(out, n, "/data/system/nano_xmb/globe/%s", file);
    FILE* f = fopen(out, "rb"); if (f) { fclose(f); return true; }
    snprintf(out, n, "/system/etc/nano_xmb/globe/%s", file);
    f = fopen(out, "rb"); if (f) { fclose(f); return true; }
    return false;
}
static GLuint loadEquirect(const char* file, GLenum wrapS) {
    char path[256];
    if (!resolveAsset(file, path, sizeof(path))) { ALOGE("ps3mpglobe: %s not found", file); return 0; }
    FILE* fp = fopen(path, "rb"); if (!fp) return 0;
    png_structp png = png_create_read_struct(PNG_LIBPNG_VER_STRING, nullptr, nullptr, nullptr);
    if (!png) { fclose(fp); return 0; }
    png_infop info = png_create_info_struct(png);
    if (!info) { png_destroy_read_struct(&png, nullptr, nullptr); fclose(fp); return 0; }
    if (setjmp(png_jmpbuf(png))) { png_destroy_read_struct(&png, &info, nullptr); fclose(fp); return 0; }
    png_init_io(png, fp);
    png_read_info(png, info);
    int w = png_get_image_width(png, info), h = png_get_image_height(png, info);
    int color = png_get_color_type(png, info), depth = png_get_bit_depth(png, info);
    if (depth == 16) png_set_strip_16(png);
    if (color == PNG_COLOR_TYPE_PALETTE) png_set_palette_to_rgb(png);
    if (color == PNG_COLOR_TYPE_GRAY && depth < 8) png_set_expand_gray_1_2_4_to_8(png);
    if (color == PNG_COLOR_TYPE_GRAY || color == PNG_COLOR_TYPE_GRAY_ALPHA) png_set_gray_to_rgb(png);
    if (png_get_valid(png, info, PNG_INFO_tRNS)) png_set_tRNS_to_alpha(png);
    if (color == PNG_COLOR_TYPE_RGB || color == PNG_COLOR_TYPE_GRAY || color == PNG_COLOR_TYPE_PALETTE)
        png_set_filler(png, 0xFF, PNG_FILLER_AFTER);
    png_read_update_info(png, info);
    std::vector<unsigned char> px((size_t)w * h * 4);
    std::vector<png_bytep> rows((size_t)h);
    for (int y = 0; y < h; y++) rows[(size_t)y] = px.data() + (size_t)y * w * 4;
    png_read_image(png, rows.data());
    png_destroy_read_struct(&png, &info, nullptr);
    fclose(fp);
    GLuint tex = 0;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, px.data());
    bool pot = (w & (w-1)) == 0 && (h & (h-1)) == 0;
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrapS);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    if (pot) {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glGenerateMipmap(GL_TEXTURE_2D);
    } else {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    }
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    return tex;
}
static void makeRT(RT& rt, int w, int h) {
    if (!rt.fb) glGenFramebuffers(1, &rt.fb);
    if (!rt.tex) glGenTextures(1, &rt.tex);
    glBindTexture(GL_TEXTURE_2D, rt.tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindFramebuffer(GL_FRAMEBUFFER, rt.fb);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, rt.tex, 0);
    rt.w = w; rt.h = h;
}
static bool ensureRTs(int fw, int fh) {
    // The earth ray-march is the cost, so render the SCENE at half-res (the globe is a
    // smooth object and the bloom softens it; A53/PowerVR holds 60fps this way). Bloom
    // levels are then quarter + eighth of the full frame.
    int sw = fw >> 1, sh = fh >> 1;
    if (sScene.w == sw && sScene.h == sh && sScene.fb) return true;
    int hw = fw >> 2, hh = fh >> 2, qw = fw >> 3, qh = fh >> 3;
    if (sw < 2) sw = 2; if (sh < 2) sh = 2;
    if (hw < 2) hw = 2; if (hh < 2) hh = 2; if (qw < 2) qw = 2; if (qh < 2) qh = 2;
    makeRT(sScene, sw, sh);
    makeRT(sHalfA, hw, hh); makeRT(sHalfB, hw, hh);
    makeRT(sQuadA, qw, qh); makeRT(sQuadB, qw, qh);
    return true;
}

static inline float smoothstep01(float x, float a, float b) {
    float t = (x - a) / (b - a); if (t < 0.0f) t = 0.0f; if (t > 1.0f) t = 1.0f;
    return t * t * (3.0f - 2.0f * t);
}

static void fullQuad(GLint posLoc) {
    glBindBuffer(GL_ARRAY_BUFFER, sQuadVBO);
    glEnableVertexAttribArray(posLoc);
    glVertexAttribPointer(posLoc, 2, GL_FLOAT, GL_FALSE, 0, (const void*)0);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glDisableVertexAttribArray(posLoc);
}

// ---------------------------------------------------------------------------
// public API
// ---------------------------------------------------------------------------
bool init() {
    if (sReady) return true;
    if (sTried) return false;
    sTried = true;
    TUNE_SUNBRI    = propF("persist.gammaos.nano.globe.sunbri", TUNE_SUNBRI);
    TUNE_SUNHALO   = propF("persist.gammaos.nano.globe.sunhalo", TUNE_SUNHALO);
    TUNE_BLOOMTHR  = propF("persist.gammaos.nano.globe.bloomthr", TUNE_BLOOMTHR);
    TUNE_BLOOMGAIN = propF("persist.gammaos.nano.globe.bloomgain", TUNE_BLOOMGAIN);
    TUNE_NIGHTBRI  = propF("persist.gammaos.nano.globe.nightbri", TUNE_NIGHTBRI);
    TUNE_RIM       = propF("persist.gammaos.nano.globe.rim", TUNE_RIM);
    TUNE_HALO      = propF("persist.gammaos.nano.globe.halo", TUNE_HALO);
    TUNE_HALOFALL  = propF("persist.gammaos.nano.globe.halofall", TUNE_HALOFALL);
    TUNE_DAYK      = propF("persist.gammaos.nano.globe.dayk", TUNE_DAYK);
    sSceneProg  = linkProgram(FULL_VS, SCENE_FS);
    sBrightProg = linkProgram(FULL_VS, BRIGHT_FS);
    sBlurProg   = linkProgram(FULL_VS, BLUR_FS);
    sCompProg   = linkProgram(COMP_VS, COMP_FS);
    if (!sSceneProg || !sBrightProg || !sBlurProg || !sCompProg) return false;
    scAPos    = glGetAttribLocation(sSceneProg, "aPos");
    scEye     = glGetUniformLocation(sSceneProg, "uEye");
    scFwd     = glGetUniformLocation(sSceneProg, "uFwd");
    scRight   = glGetUniformLocation(sSceneProg, "uRight");
    scUp      = glGetUniformLocation(sSceneProg, "uUp");
    scTanHF   = glGetUniformLocation(sSceneProg, "uTanHF");
    scAspect  = glGetUniformLocation(sSceneProg, "uAspect");
    scSun     = glGetUniformLocation(sSceneProg, "uSun");
    scDay     = glGetUniformLocation(sSceneProg, "uDay");
    scNight   = glGetUniformLocation(sSceneProg, "uNight");
    scClouds  = glGetUniformLocation(sSceneProg, "uClouds");
    scStars   = glGetUniformLocation(sSceneProg, "uStars");
    scNightBri= glGetUniformLocation(sSceneProg, "uNightBri");
    scNightLift= glGetUniformLocation(sSceneProg, "uNightLift");
    scHaloFall= glGetUniformLocation(sSceneProg, "uHaloFall");
    scDayK    = glGetUniformLocation(sSceneProg, "uDayK");
    scRim     = glGetUniformLocation(sSceneProg, "uRim");
    scHalo    = glGetUniformLocation(sSceneProg, "uHalo");
    scSunBri  = glGetUniformLocation(sSceneProg, "uSunBri");
    scSunHalo = glGetUniformLocation(sSceneProg, "uSunHalo");
    brAPos   = glGetAttribLocation(sBrightProg, "aPos");
    brTex    = glGetUniformLocation(sBrightProg, "uTex");
    brThresh = glGetUniformLocation(sBrightProg, "uThresh");
    blAPos   = glGetAttribLocation(sBlurProg, "aPos");
    blTex    = glGetUniformLocation(sBlurProg, "uTex");
    blStep   = glGetUniformLocation(sBlurProg, "uStep");
    cmAPos   = glGetAttribLocation(sCompProg, "aPos");
    cmAUV    = glGetAttribLocation(sCompProg, "aUV");
    cmScene  = glGetUniformLocation(sCompProg, "uScene");
    cmBloomA = glGetUniformLocation(sCompProg, "uBloomA");
    cmBloomB = glGetUniformLocation(sCompProg, "uBloomB");
    cmGain   = glGetUniformLocation(sCompProg, "uGain");
    cmAlpha  = glGetUniformLocation(sCompProg, "uAlpha");
    cmFade   = glGetUniformLocation(sCompProg, "uFade");
    cmRot    = glGetUniformLocation(sCompProg, "uRotation");
    sTexDay    = loadEquirect("earth_day.png", GL_REPEAT);
    sTexNight  = loadEquirect("earth_night.png", GL_REPEAT);
    sTexClouds = loadEquirect("earth_clouds.png", GL_REPEAT);
    if (!sTexDay) return false;
    if (!sTexNight) sTexNight = sTexDay;
    if (!sTexClouds) sTexClouds = sTexDay;
    static const GLfloat quad[] = { -1,-1, 1,-1, -1,1, -1,1, 1,-1, 1,1 };
    glGenBuffers(1, &sQuadVBO);
    glBindBuffer(GL_ARRAY_BUFFER, sQuadVBO);
    glBufferData(GL_ARRAY_BUFFER, sizeof(quad), quad, GL_STATIC_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    sReady = true;
    ALOGI("ps3mpglobe: ready (%d scenes, sun=%.0f thr=%.2f gain=%.2f)",
          kGlobeNumScenes, TUNE_SUNBRI, TUNE_BLOOMTHR, TUNE_BLOOMGAIN);
    return true;
}

void shutdown() {
    if (sSceneProg) glDeleteProgram(sSceneProg);
    if (sBrightProg) glDeleteProgram(sBrightProg);
    if (sBlurProg) glDeleteProgram(sBlurProg);
    if (sCompProg) glDeleteProgram(sCompProg);
    GLuint texs[3] = { sTexDay, sTexNight, sTexClouds };
    for (int i = 0; i < 3; i++) if (texs[i]) { glDeleteTextures(1, &texs[i]); }
    if (sQuadVBO) glDeleteBuffers(1, &sQuadVBO);
    RT* rts[5] = { &sScene, &sHalfA, &sHalfB, &sQuadA, &sQuadB };
    for (int i = 0; i < 5; i++) {
        if (rts[i]->fb) glDeleteFramebuffers(1, &rts[i]->fb);
        if (rts[i]->tex) glDeleteTextures(1, &rts[i]->tex);
        *rts[i] = {0,0,0,0};
    }
    sSceneProg = sBrightProg = sBlurProg = sCompProg = 0;
    sTexDay = sTexNight = sTexClouds = 0; sQuadVBO = 0;
    sReady = false; sTried = false; sScn = 0; sScnT = 0.0f;
}

bool ready() { return sReady; }

void reset() { sScn = 0; sScnT = 0.0f; }

// Catmull-Rom over a scene's keyframe column (one of the 10 floats per keyframe).
static float crom(int scn, int col, float u) {
    int n = kGlobeKF;
    float fp = u * (float)(n - 1);
    int i1 = (int)floorf(fp); if (i1 < 0) i1 = 0; if (i1 > n - 1) i1 = n - 1;
    int i0 = i1 > 0 ? i1 - 1 : 0;
    int i2 = i1 < n - 1 ? i1 + 1 : n - 1;
    int i3 = i2 < n - 1 ? i2 + 1 : n - 1;
    float t = fp - (float)i1;
    float p0 = kGlobeScenes[scn][i0][col], p1 = kGlobeScenes[scn][i1][col];
    float p2 = kGlobeScenes[scn][i2][col], p3 = kGlobeScenes[scn][i3][col];
    return 0.5f * ((2.0f*p1) + (-p0 + p2)*t + (2.0f*p0 - 5.0f*p1 + 4.0f*p2 - p3)*t*t
                   + (-p0 + 3.0f*p1 - 3.0f*p2 + p3)*t*t*t);
}

void render(int panelW, int panelH, const float rotMat2[4], float alpha,
            float dt, const nanoaudio::Bands& bands) {
    if (!sReady && !init()) return;
    if (alpha <= 0.0f) return;
    const int fw = (int)(ps3::gFrameW + 0.5f);
    const int fh = (int)(ps3::gFrameH + 0.5f);
    if (fw < 8 || fh < 8) return;
    if (dt <= 0.0f || dt > 0.1f) dt = 0.016f;

    GLint prevFbo = 0; glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
    GLint prevVp[4] = {0,0,panelW,panelH}; glGetIntegerv(GL_VIEWPORT, prevVp);
    if (!ensureRTs(fw, fh)) return;

    // ---- advance the scene cycle ----
    float dur = kGlobeSceneDur[sScn];
    sScnT += dt;
    if (sScnT >= dur) { sScnT -= dur; sScn = (sScn + 1) % kGlobeNumScenes; dur = kGlobeSceneDur[sScn]; }
    float u = (dur > 0.0f) ? (sScnT / dur) : 0.0f;
    if (u < 0.0f) u = 0.0f; if (u > 1.0f) u = 1.0f;
    // dip-to-black fade at the scene boundaries (FADE ~0.7s in/out)
    float fade = smoothstep01(sScnT, 0.0f, 0.7f) * smoothstep01(dur - sScnT, 0.0f, 0.7f);
    // debug: pin an exact (scene,t) for 1:1 web comparison. prop "S.TTT" e.g. 2.50 = scene 2, t 0.50.
    // matches the web MPGlobe._frame(sid,t). negative/absent = normal cycling.
    { char fv[PROPERTY_VALUE_MAX] = {}; if (property_get("sys.gammaos.nano.globe.force", fv, "") > 0 && fv[0]) {
        float f = (float)atof(fv); if (f >= 0.0f) {
          int fs = (int)f; float ft = f - (float)fs;
          sScn = ((fs % kGlobeNumScenes) + kGlobeNumScenes) % kGlobeNumScenes;
          dur = kGlobeSceneDur[sScn]; sScnT = ft * dur; u = ft;
          if (u < 0.0f) u = 0.0f; if (u > 1.0f) u = 1.0f; fade = 1.0f; } } }
    // live tuning: when sys.gammaos.nano.globe.live=1, re-read the TUNE props each frame so the
    // look can be dialed in without a rebuild (then bake the winners). negligible when off.
    { char lv[PROPERTY_VALUE_MAX] = {}; if (property_get("sys.gammaos.nano.globe.live", lv, "") > 0 && lv[0] == '1') {
        TUNE_HALO     = propF("persist.gammaos.nano.globe.halo", TUNE_HALO);
        TUNE_HALOFALL = propF("persist.gammaos.nano.globe.halofall", TUNE_HALOFALL);
        TUNE_DAYK     = propF("persist.gammaos.nano.globe.dayk", TUNE_DAYK);
        TUNE_RIM      = propF("persist.gammaos.nano.globe.rim", TUNE_RIM);
        TUNE_NIGHTBRI = propF("persist.gammaos.nano.globe.nightbri", TUNE_NIGHTBRI);
        TUNE_BLOOMTHR = propF("persist.gammaos.nano.globe.bloomthr", TUNE_BLOOMTHR);
        TUNE_BLOOMGAIN= propF("persist.gammaos.nano.globe.bloomgain", TUNE_BLOOMGAIN);
        TUNE_SUNBRI   = propF("persist.gammaos.nano.globe.sunbri", TUNE_SUNBRI);
        TUNE_SUNHALO  = propF("persist.gammaos.nano.globe.sunhalo", TUNE_SUNHALO); } }

    // ---- camera from the replayed keyframes ----
    float eye[3] = { crom(sScn,0,u), crom(sScn,1,u), crom(sScn,2,u) };
    float fwd[3] = { crom(sScn,3,u), crom(sScn,4,u), crom(sScn,5,u) };
    float up[3]  = { crom(sScn,6,u), crom(sScn,7,u), crom(sScn,8,u) };
    float fovy   = crom(sScn,9,u);
    // re-orthonormalize the basis
    auto nrm = [](float* v){ float l = sqrtf(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]); if (l < 1e-6f) l = 1.0f; v[0]/=l; v[1]/=l; v[2]/=l; };
    nrm(fwd);
    float right[3] = { fwd[1]*up[2]-fwd[2]*up[1], fwd[2]*up[0]-fwd[0]*up[2], fwd[0]*up[1]-fwd[1]*up[0] };
    nrm(right);
    up[0] = right[1]*fwd[2]-right[2]*fwd[1]; up[1] = right[2]*fwd[0]-right[0]*fwd[2]; up[2] = right[0]*fwd[1]-right[1]*fwd[0];
    nrm(up);
    float tanHF = tanf(fovy * (float)M_PI / 180.0f * 0.5f);
    float aspect = (float)fw / (float)fh;
    float starsOn = (sScn == 0) ? 0.0f : 1.0f;   // STARTEX_SKIP_SCENES = [0]

    // ---- pass 1: ray-march the scene -> sScene (full-res, display-toned) ----
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_BLEND);
    glBindFramebuffer(GL_FRAMEBUFFER, sScene.fb);
    glViewport(0, 0, sScene.w, sScene.h);
    glUseProgram(sSceneProg);
    glUniform3f(scEye, eye[0], eye[1], eye[2]);
    glUniform3f(scFwd, fwd[0], fwd[1], fwd[2]);
    glUniform3f(scRight, right[0], right[1], right[2]);
    glUniform3f(scUp, up[0], up[1], up[2]);
    glUniform1f(scTanHF, tanHF);
    glUniform1f(scAspect, aspect);
    const float* sun = kGlobeSceneSun[sScn];   // the real per-scene sun/light direction
    glUniform3f(scSun, sun[0], sun[1], sun[2]);
    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, sTexDay);    glUniform1i(scDay, 0);
    glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, sTexNight);  glUniform1i(scNight, 1);
    glActiveTexture(GL_TEXTURE2); glBindTexture(GL_TEXTURE_2D, sTexClouds); glUniform1i(scClouds, 2);
    glActiveTexture(GL_TEXTURE0);
    glUniform1f(scStars, starsOn);
    glUniform1f(scNightBri, TUNE_NIGHTBRI);
    // per-scene night fill: OFF on the fully-lit low-orbit scenes (played 0,1), ON for the dark/distant scenes.
    glUniform1f(scNightLift, (sScn <= 1) ? 0.0f : 1.0f);
    glUniform1f(scHaloFall, TUNE_HALOFALL);
    glUniform1f(scDayK, TUNE_DAYK);
    glUniform1f(scRim, TUNE_RIM);
    glUniform1f(scHalo, TUNE_HALO);
    // subtle audio reactivity: the bass pulses the sun brightness / corona.
    float bass = bands.bass; if (bass < 0.0f) bass = 0.0f; if (bass > 1.0f) bass = 1.0f;
    glUniform1f(scSunBri, TUNE_SUNBRI * (1.0f + bass * 0.45f));
    glUniform1f(scSunHalo, TUNE_SUNHALO * (1.0f + bass * 0.30f));
    fullQuad(scAPos);

    // ---- pass 2: bright-pass -> sHalfA (half-res) ----
    glBindFramebuffer(GL_FRAMEBUFFER, sHalfA.fb);
    glViewport(0, 0, sHalfA.w, sHalfA.h);
    glUseProgram(sBrightProg);
    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, sScene.tex); glUniform1i(brTex, 0);
    glUniform1f(brThresh, TUNE_BLOOMTHR);
    fullQuad(brAPos);

    // ---- pass 3: blur half (H -> sHalfB, V -> sHalfA) ----
    glUseProgram(sBlurProg);
    glBindFramebuffer(GL_FRAMEBUFFER, sHalfB.fb); glViewport(0, 0, sHalfB.w, sHalfB.h);
    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, sHalfA.tex); glUniform1i(blTex, 0);
    glUniform2f(blStep, 1.0f / sHalfA.w, 0.0f);
    fullQuad(blAPos);
    glBindFramebuffer(GL_FRAMEBUFFER, sHalfA.fb); glViewport(0, 0, sHalfA.w, sHalfA.h);
    glBindTexture(GL_TEXTURE_2D, sHalfB.tex);
    glUniform2f(blStep, 0.0f, 1.0f / sHalfB.h);
    fullQuad(blAPos);

    // ---- pass 4: downsample half -> quarter (sHalfA -> sQuadA) then blur (H,V) for a wider corona ----
    glBindFramebuffer(GL_FRAMEBUFFER, sQuadA.fb); glViewport(0, 0, sQuadA.w, sQuadA.h);
    glBindTexture(GL_TEXTURE_2D, sHalfA.tex);
    glUniform2f(blStep, 1.0f / sHalfA.w, 0.0f);   // a blur-downsample
    fullQuad(blAPos);
    glBindFramebuffer(GL_FRAMEBUFFER, sQuadB.fb); glViewport(0, 0, sQuadB.w, sQuadB.h);
    glBindTexture(GL_TEXTURE_2D, sQuadA.tex);
    glUniform2f(blStep, 0.0f, 1.0f / sQuadA.h);
    fullQuad(blAPos);
    glBindFramebuffer(GL_FRAMEBUFFER, sQuadA.fb); glViewport(0, 0, sQuadA.w, sQuadA.h);
    glBindTexture(GL_TEXTURE_2D, sQuadB.tex);
    glUniform2f(blStep, 1.0f / sQuadB.w, 0.0f);
    fullQuad(blAPos);

    // ---- pass 5: composite scene + bloom -> panel (DRM rotation + alpha) ----
    glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prevFbo);
    glViewport(prevVp[0], prevVp[1], prevVp[2], prevVp[3]);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glUseProgram(sCompProg);
    float fx0 = ps3::gFrameX, fy0 = ps3::gFrameY;
    float fx1 = fx0 + ps3::gFrameW, fy1 = fy0 + ps3::gFrameH;
    float pw = (float)panelW, ph = (float)panelH;
    float nx0 = fx0 / pw * 2.0f - 1.0f, nx1 = fx1 / pw * 2.0f - 1.0f;
    float ny0 = 1.0f - fy0 / ph * 2.0f, ny1 = 1.0f - fy1 / ph * 2.0f;
    const GLfloat cv[] = {
        nx0, ny1, 0.f, 0.f,   nx1, ny1, 1.f, 0.f,   nx1, ny0, 1.f, 1.f,
        nx0, ny1, 0.f, 0.f,   nx1, ny0, 1.f, 1.f,   nx0, ny0, 0.f, 1.f,
    };
    static const GLfloat kIdentity[4] = {1.f, 0.f, 0.f, 1.f};
    const GLfloat* rm = rotMat2 ? rotMat2 : kIdentity;
    glUniformMatrix2fv(cmRot, 1, GL_FALSE, rm);
    glUniform1f(cmGain, TUNE_BLOOMGAIN);
    glUniform1f(cmAlpha, alpha > 1.0f ? 1.0f : alpha);
    glUniform1f(cmFade, fade);
    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, sScene.tex); glUniform1i(cmScene, 0);
    glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, sHalfA.tex); glUniform1i(cmBloomA, 1);
    glActiveTexture(GL_TEXTURE2); glBindTexture(GL_TEXTURE_2D, sQuadA.tex); glUniform1i(cmBloomB, 2);
    glActiveTexture(GL_TEXTURE0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glVertexAttribPointer(cmAPos, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), cv);
    glEnableVertexAttribArray(cmAPos);
    glVertexAttribPointer(cmAUV, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), cv + 2);
    glEnableVertexAttribArray(cmAUV);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glDisableVertexAttribArray(cmAPos);
    glDisableVertexAttribArray(cmAUV);
}

} // namespace ps3mpglobe
} // namespace android
