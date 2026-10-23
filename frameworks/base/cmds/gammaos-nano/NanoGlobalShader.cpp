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

#include "NanoGlobalShader.h"

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/system_properties.h>
#include <sys/wait.h>
#include <unistd.h>

#include <algorithm>
#include <fstream>
#include <map>
#include <sstream>
#include <string>
#include <vector>

#include <cutils/properties.h>
#include <utils/Log.h>

#include "stb_image.h"

// Exported by libc but declared only in the internal <sys/_system_properties.h>.
extern "C" uint32_t __system_property_serial(const prop_info* __pi);

namespace android {
namespace gshader {
namespace {

// ============================================================================ properties

std::string getProp(const char* key, const char* def = "") {
    char v[PROPERTY_VALUE_MAX] = {};
    property_get(key, v, def);
    return v;
}
bool getBoolProp(const char* key, bool def) {
    const std::string s = getProp(key, def ? "1" : "0");
    return s == "1" || s == "true";
}
float getFloatProp(const char* key, const char* def) {
    const std::string s = getProp(key, def);
    char* end = nullptr;
    float v = strtof(s.c_str(), &end);
    if (end == s.c_str() || isnan(v) || isinf(v)) v = strtof(def, nullptr);
    return v;
}
float clampf(float v, float lo, float hi) { return v < lo ? lo : v > hi ? hi : v; }

// The settings, re-read when the master switch or the type changes (serial watched) and
// otherwise every half second (the parameter properties of the builtin effects move with the
// menu's sliders, which the home follows live).
struct Settings {
    bool on = false;
    std::string type;         // canonical: crt-simple, lcd3x, lcd-shader, blur-fill, custom, custom-vk, custom-gl
    std::string preset;       // persist.gammaos.shader.custom.preset
    std::string resScale;     // persist.gammaos.shader.custom.res_scale
    std::string density;      // persist.gammaos.shader.custom.scanline_density
    std::string orient;       // persist.gammaos.shader.orient
    bool fast = false;
    bool debug = false;
    bool testOverlay = false;
};
Settings gS;

struct Watched {
    const char* name;
    const prop_info* pi = nullptr;
    uint32_t serial = 0;
    bool changed() {
        if (!pi) pi = __system_property_find(name);
        if (!pi) return false;
        const uint32_t s = __system_property_serial(pi);
        if (s == serial) return false;
        serial = s;
        return true;
    }
};
Watched gWEnable{"persist.gammaos.shader.enable"}, gWType{"persist.gammaos.shader.type"},
        gWPreset{"persist.gammaos.shader.custom.preset"};
int64_t gLastFullReadMs = 0;

int64_t nowMs() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

std::string canonicalType(std::string t) {
    if (t.empty()) t = "crt-simple";
    if (t == "lcd_shader" || t == "lcdshader" || t == "lcd") t = "lcd-shader";
    if (t == "blurfill" || t == "blur_fill") t = "blur-fill";
    return t;
}

void readSettings() {
    gS.on = getBoolProp("persist.gammaos.shader.enable", false);
    gS.type = canonicalType(getProp("persist.gammaos.shader.type", "crt-simple"));
    gS.preset = getProp("persist.gammaos.shader.custom.preset", "");
    gS.resScale = getProp("persist.gammaos.shader.custom.res_scale", "auto");
    gS.density = getProp("persist.gammaos.shader.custom.scanline_density", "auto");
    gS.orient = getProp("persist.gammaos.shader.orient", "auto");
    gS.fast = getBoolProp("persist.gammaos.shader.fast", false);
    gS.debug = getBoolProp("persist.gammaos.shader.debug", false);
    gS.testOverlay = getBoolProp("persist.gammaos.shader.test_overlay", false);
}

bool typeKnown(const std::string& t) {
    return t == "crt-simple" || t == "lcd3x" || t == "lcd-shader" || t == "blur-fill" ||
           t == "custom" || t == "custom-vk" || t == "custom-gl";
}
bool typeIsPreset(const std::string& t) { return t == "custom" || t == "custom-vk" || t == "custom-gl"; }

// The status the menu reads for the custom preset row: "ok" | "passes:<n>/<max>" |
// "parse_fail" | "compile_fail". Written on change only.
void setStatus(const std::string& s) {
    static std::string sLast;
    if (s == sLast) return;
    sLast = s;
    property_set("sys.gammaos.shader.status", s.c_str());
}

// ============================================================================ GL helpers

GLuint compileShader(GLenum type, const std::string& src, std::string* log = nullptr) {
    GLuint sh = glCreateShader(type);
    const char* p = src.c_str();
    glShaderSource(sh, 1, &p, nullptr);
    glCompileShader(sh);
    GLint ok = 0;
    glGetShaderiv(sh, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char buf[2048] = {};
        glGetShaderInfoLog(sh, sizeof buf, nullptr, buf);
        ALOGE("gshader: %s shader compile failed: %s", type == GL_VERTEX_SHADER ? "vertex" : "fragment", buf);
        if (log) *log = buf;
        glDeleteShader(sh);
        return 0;
    }
    return sh;
}

// Links; the caller may bind attribute locations through `bind` before the link.
GLuint linkProgram(const std::string& vs, const std::string& fs,
                   void (*bind)(GLuint) = nullptr) {
    GLuint v = compileShader(GL_VERTEX_SHADER, vs);
    if (!v) return 0;
    GLuint f = compileShader(GL_FRAGMENT_SHADER, fs);
    if (!f) { glDeleteShader(v); return 0; }
    GLuint prog = glCreateProgram();
    glAttachShader(prog, v);
    glAttachShader(prog, f);
    if (bind) bind(prog);
    glLinkProgram(prog);
    glDeleteShader(v);
    glDeleteShader(f);
    GLint ok = 0;
    glGetProgramiv(prog, GL_LINK_STATUS, &ok);
    if (!ok) {
        char buf[2048] = {};
        glGetProgramInfoLog(prog, sizeof buf, nullptr, buf);
        ALOGE("gshader: program link failed: %s", buf);
        glDeleteProgram(prog);
        return 0;
    }
    return prog;
}

// A render target: texture plus framebuffer, resized on demand.
struct Target {
    GLuint fbo = 0, tex = 0;
    int w = 0, h = 0;
    GLenum internalFormat = GL_RGBA8;
    bool ensure(int width, int height, GLenum fmt = GL_RGBA8, GLenum minFilter = GL_LINEAR,
                GLenum magFilter = GL_LINEAR, GLenum wrap = GL_CLAMP_TO_EDGE) {
        if (tex && w == width && h == height && internalFormat == fmt) {
            glBindTexture(GL_TEXTURE_2D, tex);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter);
            return true;
        }
        release();
        glGenTextures(1, &tex);
        glBindTexture(GL_TEXTURE_2D, tex);
        GLenum format = GL_RGBA, type = GL_UNSIGNED_BYTE;
        if (fmt == GL_RGBA16F) type = GL_HALF_FLOAT;
        glTexImage2D(GL_TEXTURE_2D, 0, fmt, width, height, 0, format, type, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap);
        glGenFramebuffers(1, &fbo);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
        const GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            ALOGW("gshader: framebuffer %dx%d fmt 0x%x incomplete 0x%x", width, height, fmt, status);
            release();
            return false;
        }
        w = width; h = height; internalFormat = fmt;
        return true;
    }
    void release() {
        if (fbo) glDeleteFramebuffers(1, &fbo);
        if (tex) glDeleteTextures(1, &tex);
        fbo = tex = 0; w = h = 0;
    }
};

const char* const kVsFull =
    "#version 300 es\n"
    "in vec2 aPos;\n"
    "out vec2 vUV;\n"
    "void main() { vUV = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }\n";

void bindPos(GLuint prog) { glBindAttribLocation(prog, 0, "aPos"); }

// Draws the fullscreen triangle with the bound program into the bound framebuffer.
void drawFull() {
    static const float tri[6] = { -1, -1,  3, -1,  -1, 3 };
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindVertexArray(0);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, tri);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glDisableVertexAttribArray(0);
}

void plainState() {
    glDisable(GL_BLEND);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_SCISSOR_TEST);
    glDisable(GL_STENCIL_TEST);
    glDisable(GL_CULL_FACE);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
}

// A plain copy (also the resize and the nearest half-res downscale).
struct Blit {
    GLuint prog = 0;
    GLint locTex = -1;
    bool ensure() {
        if (prog) return true;
        static const char* fs =
            "#version 300 es\nprecision highp float;\nuniform sampler2D uTex;\nin vec2 vUV;\nout vec4 o;\n"
            "void main() { o = texture(uTex, vUV); }\n";
        prog = linkProgram(kVsFull, fs, bindPos);
        if (prog) locTex = glGetUniformLocation(prog, "uTex");
        return prog != 0;
    }
    // src sampled with `filter` into `dst` (resized to w x h).
    bool run(Target& dst, int w, int h, GLuint src, GLenum filter) {
        if (!ensure() || !dst.ensure(w, h)) return false;
        glBindFramebuffer(GL_FRAMEBUFFER, dst.fbo);
        glViewport(0, 0, w, h);
        glUseProgram(prog);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, src);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glUniform1i(locTex, 0);
        drawFull();
        return true;
    }
    void release() { if (prog) glDeleteProgram(prog); prog = 0; }
};
Blit gBlit;

// ============================================================================ orientation
//
// The panel's install rotation is baked into the frame, so a scanline drawn along the chain's
// Y axis would read rotated to the viewer. The frame is turned into upright logical space before
// the effect (the panel-to-logical dihedral) and back after (its inverse), sampled nearest so
// scanlines stay pixel-crisp through the round trip. Same maps and rules as RenderEngine's chain.
struct Dihedral { float m[2][3]; bool swap; };
const Dihedral kDihedral[8] = {
    {{{1, 0, 0}, {0, 1, 0}}, false},     // identity
    {{{0, 1, 0}, {-1, 0, 1}}, true},     // rot90
    {{{-1, 0, 1}, {0, -1, 1}}, false},   // rot180
    {{{0, -1, 1}, {1, 0, 0}}, true},     // rot270
    {{{1, 0, 0}, {0, -1, 1}}, false},    // flipV
    {{{-1, 0, 1}, {0, 1, 0}}, false},    // flipH
    {{{0, 1, 0}, {1, 0, 0}}, true},      // transpose
    {{{0, -1, 1}, {-1, 0, 1}}, true},    // antitranspose
};
const int kDihedralInv[8] = {0, 3, 2, 1, 4, 5, 6, 7};

int resolveOrientIndex(const std::string& o, int installDeg) {
    if (o == "0") return 0;
    if (o == "90") return 1;
    if (o == "180") return 2;
    if (o == "270") return 3;
    if (o == "flipv") return 4;
    if (o == "fliph") return 5;
    if (o == "transpose") return 6;
    if (o == "antitranspose") return 7;
    const int d = ((installDeg % 360) + 360) % 360;
    if (d == 90) return 1;
    if (d == 180) return 2;
    if (d == 270) return 3;
    return 0;
}

struct Orient {
    GLuint prog = 0;
    GLint locTex = -1, locRow0 = -1, locRow1 = -1;
    bool ensure() {
        if (prog) return true;
        static const char* fs =
            "#version 300 es\nprecision highp float;\nuniform sampler2D uTex;\nuniform vec3 uRow0;\nuniform vec3 uRow1;\n"
            "in vec2 vUV;\nout vec4 o;\n"
            "void main() { vec2 s = vec2(dot(uRow0.xy, vUV) + uRow0.z, dot(uRow1.xy, vUV) + uRow1.z);\n"
            "  o = texture(uTex, s); o.a = 1.0; }\n";
        prog = linkProgram(kVsFull, fs, bindPos);
        if (prog) {
            locTex = glGetUniformLocation(prog, "uTex");
            locRow0 = glGetUniformLocation(prog, "uRow0");
            locRow1 = glGetUniformLocation(prog, "uRow1");
        }
        return prog != 0;
    }
    bool run(Target& dst, GLuint src, int idx, int w, int h) {
        if (!ensure() || !dst.ensure(w, h, GL_RGBA8, GL_NEAREST, GL_NEAREST)) return false;
        glBindFramebuffer(GL_FRAMEBUFFER, dst.fbo);
        glViewport(0, 0, w, h);
        glUseProgram(prog);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, src);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glUniform1i(locTex, 0);
        glUniform3fv(locRow0, 1, kDihedral[idx].m[0]);
        glUniform3fv(locRow1, 1, kDihedral[idx].m[1]);
        drawFull();
        return true;
    }
    void release() { if (prog) glDeleteProgram(prog); prog = 0; }
};
Orient gOrient;

// ============================================================================ builtin effects
//
// Ports of RenderEngine's SkSL effects. Skia hands those a top-left pixel coordinate `p` and
// samples the source image at pixel coordinates; here `p` is rebuilt from gl_FragCoord and the
// source sampled through the bottom-left texture space, so every pixel lands where Skia put it.

const char* const kCommonFs =
    "#version 300 es\n"
    "precision highp float;\n"
    "uniform sampler2D src;\n"
    "uniform vec2 fb_size;\n"
    "in vec2 vUV;\n"
    "out vec4 o;\n"
    "vec4 sampleSrc(vec2 q) { return texture(src, vec2(q.x, fb_size.y - q.y) / fb_size); }\n"
    "vec2 fragP() { return vec2(gl_FragCoord.x, fb_size.y - gl_FragCoord.y); }\n";

const char* const kCrtFs =
    "uniform float scan_strength, triad_px, mask_strength, scan_phase, scan_ca, scan_sa, inv_period, scan_dark;\n"
    "uniform float curv, vignette, edge_soft_px, blur_r;\n"
    "vec4 sample_base(vec2 q) {\n"
    "  float r = blur_r;\n"
    "  if (r <= 0.01) return sampleSrc(q);\n"
    "  vec2 offX = vec2(r, 0.0), offY = vec2(0.0, r);\n"
    "  vec4 c0 = sampleSrc(q);\n"
    "  vec4 c1 = sampleSrc(q + offX), c2 = sampleSrc(q - offX), c3 = sampleSrc(q + offY), c4 = sampleSrc(q - offY);\n"
    "  vec4 c5 = sampleSrc(q + offX + offY), c6 = sampleSrc(q + offX - offY), c7 = sampleSrc(q - offX + offY), c8 = sampleSrc(q - offX - offY);\n"
    "  return (c0 * 4.0 + (c1 + c2 + c3 + c4) * 2.0 + (c5 + c6 + c7 + c8)) * (1.0 / 20.0);\n"
    "}\n"
    "float edge_mask(vec2 q, vec2 wh) {\n"
    "  if (edge_soft_px <= 0.0) return 1.0;\n"
    "  vec2 d = min(q, wh - q);\n"
    "  float distEdge = min(d.x, d.y);\n"
    "  return clamp(distEdge / max(1e-3, edge_soft_px), 0.0, 1.0);\n"
    "}\n"
    "void main() {\n"
    "  vec2 p = fragP();\n"
    "  vec2 wh = fb_size;\n"
    "  vec2 invWh = 1.0 / wh;\n"
    "  float aspect = wh.x * invWh.y;\n"
    "  float invAspect = wh.y * invWh.x;\n"
    "  vec2 uv = p * invWh * 2.0 - 1.0;\n"
    "  vec2 uvA = uv; uvA.x *= aspect;\n"
    "  float r2 = dot(uvA, uvA);\n"
    "  vec2 q = p;\n"
    "  if (abs(curv) > 1e-4) { float k = 1.0 + curv * r2; vec2 u = uvA * k; u.x *= invAspect; q = (u * 0.5 + 0.5) * wh; }\n"
    "  vec2 pp = p - 0.5 * wh;\n"
    "  float coord = dot(pp, vec2(-scan_sa, scan_ca)) + 0.5 * wh.y;\n"
    "  float t = fract((coord + scan_phase) * inv_period);\n"
    "  float band = step(0.5, t);\n"
    "  float scanMul = mix(scan_dark, 1.0, band);\n"
    "  vec3 triRGB = vec3(1.0);\n"
    "  if (triad_px >= 1.0) {\n"
    "    float px = max(1.0, triad_px);\n"
    "    float seg = floor(3.0 * fract(q.x / px));\n"
    "    float is0 = 1.0 - step(0.5, seg);\n"
    "    float is1 = step(0.5, seg) * (1.0 - step(1.5, seg));\n"
    "    float is2 = step(1.5, seg);\n"
    "    vec3 tri = vec3(1.0, 0.80, 0.80) * is0 + vec3(0.80, 1.0, 0.80) * is1 + vec3(0.80, 0.80, 1.0) * is2;\n"
    "    triRGB = mix(vec3(1.0), tri, clamp(mask_strength, 0.0, 1.0));\n"
    "  }\n"
    "  vec4 base = sample_base(q);\n"
    "  base.rgb *= triRGB * scanMul;\n"
    "  if (vignette > 0.0) { float r2b = dot(uv, uv); float vig = 1.0 - vignette * smoothstep(0.6, 1.0, r2b); base.rgb *= vig; }\n"
    "  base.rgb *= edge_mask(q, wh);\n"
    "  o = base;\n"
    "}\n";

const char* const kLcd3xFs =
    "uniform float brighten_scanlines, brighten_lcd;\n"
    "uniform vec2 grid_px;\n"
    "void main() {\n"
    "  vec2 p = fragP();\n"
    "  vec4 base = sampleSrc(p);\n"
    "  vec2 cell = max(grid_px, vec2(1.0, 1.0));\n"
    "  vec2 omega = 6.283185307 / cell;\n"
    "  vec2 angle = p * omega;\n"
    "  float invScanDen = 1.0 / (brighten_scanlines + 1.0);\n"
    "  float y = (brighten_scanlines + sin(angle.y)) * invScanDen;\n"
    "  float invLcdDen = 1.0 / (brighten_lcd + 1.0);\n"
    "  float cx = cos(angle.x);\n"
    "  vec3 x = (brighten_lcd + vec3(cx, -cx, cx)) * invLcdDen;\n"
    "  o = vec4(base.rgb * y * x, base.a);\n"
    "}\n";

const char* const kLcdFs =
    "uniform sampler2D prev;\n"
    "uniform float use_prev, response_time, scan_strength, subpixel_strength, gap_strength, gap_px, scan_phase;\n"
    "float scan_mul(float y) { float s = clamp(scan_strength, 0.0, 1.0); float odd = mod(floor(y) + scan_phase, 2.0); return 1.0 - s * odd; }\n"
    "float gap_mul(vec2 p) {\n"
    "  float g = clamp(gap_px, 0.0, 0.45);\n"
    "  float fx = fract(p.x), fy = fract(p.y);\n"
    "  float inside = step(g, fx) * step(fx, 1.0 - g) * step(g, fy) * step(fy, 1.0 - g);\n"
    "  return mix(1.0 - clamp(gap_strength, 0.0, 1.0), 1.0, inside);\n"
    "}\n"
    "vec3 stripe_mul(float x) {\n"
    "  float t = mod(floor(x), 3.0);\n"
    "  float m0 = 1.0 - step(0.5, t);\n"
    "  float m1 = step(0.5, t) * (1.0 - step(1.5, t));\n"
    "  float m2 = step(1.5, t);\n"
    "  float off = 1.0 - clamp(subpixel_strength, 0.0, 1.0);\n"
    "  return vec3(off) + (1.0 - off) * vec3(m0, m1, m2);\n"
    "}\n"
    "void main() {\n"
    "  vec2 p = fragP();\n"
    "  vec4 c0 = sampleSrc(p);\n"
    "  vec3 c = c0.rgb;\n"
    "  if (use_prev > 0.5) { vec4 c1 = texture(prev, vec2(p.x, fb_size.y - p.y) / fb_size); c = mix(c0.rgb, c1.rgb, clamp(response_time, 0.0, 0.78)); }\n"
    "  float mul = scan_mul(p.y) * gap_mul(p);\n"
    "  o = vec4(c * mul * stripe_mul(p.x), c0.a);\n"
    "}\n";

const char* const kBlurFillFs =
    "uniform sampler2D blur;\n"
    "uniform vec2 blur_wh;\n"
    "uniform float edge_px, feather_px, strength, orientation, luma_threshold;\n"
    "void main() {\n"
    "  vec2 p = fragP();\n"
    "  vec2 wh = fb_size;\n"
    "  vec4 c = sampleSrc(p);\n"
    "  float y = dot(c.rgb, vec3(0.2126, 0.7152, 0.0722));\n"
    "  float dl = p.x, dr = wh.x - 1.0 - p.x, dt = p.y, db = wh.y - 1.0 - p.y;\n"
    "  float band = max(edge_px, 0.0);\n"
    "  float feather = clamp(feather_px, 0.0, band);\n"
    "  float edgeStart = feather, edgeEnd = band;\n"
    "  float maskLeft = 0.0, maskRight = 0.0, maskTop = 0.0, maskBottom = 0.0;\n"
    "  if (band > 0.0) {\n"
    "    maskLeft = 1.0 - smoothstep(edgeStart, edgeEnd, dl);\n"
    "    maskRight = 1.0 - smoothstep(edgeStart, edgeEnd, dr);\n"
    "    maskTop = 1.0 - smoothstep(edgeStart, edgeEnd, dt);\n"
    "    maskBottom = 1.0 - smoothstep(edgeStart, edgeEnd, db);\n"
    "  }\n"
    "  float borderX = max(maskLeft, maskRight);\n"
    "  float borderY = max(maskTop, maskBottom);\n"
    "  float borderMask; float useVertical;\n"
    "  if (orientation == 1.0) { borderMask = borderX; useVertical = 1.0; }\n"
    "  else if (orientation == 2.0) { borderMask = borderY; useVertical = 0.0; }\n"
    "  else if (borderX >= borderY) { borderMask = borderX; useVertical = 1.0; }\n"
    "  else { borderMask = borderY; useVertical = 0.0; }\n"
    "  float dark = 1.0 - smoothstep(luma_threshold, luma_threshold + 0.05, y);\n"
    "  float border = borderMask * dark * strength;\n"
    "  if (border <= 0.0) { o = c; return; }\n"
    "  vec2 q = p;\n"
    "  if (useVertical > 0.5) q.x = clamp(q.x, edge_px, wh.x - 1.0 - edge_px);\n"
    "  else q.y = clamp(q.y, edge_px, wh.y - 1.0 - edge_px);\n"
    "  vec4 cb = texture(blur, vec2(q.x, wh.y - q.y) / wh);\n"
    "  o = mix(c, cb, border);\n"
    "}\n";

// One axis of a Gaussian blur (the blur-fill buffer), sigma in buffer pixels.
const char* const kGaussFs =
    "#version 300 es\nprecision highp float;\nuniform sampler2D uTex;\nuniform vec2 uStep;\nuniform float uSigma;\nuniform int uRadius;\n"
    "in vec2 vUV;\nout vec4 o;\n"
    "void main() {\n"
    "  float twoS2 = 2.0 * uSigma * uSigma;\n"
    "  vec4 acc = texture(uTex, vUV); float wsum = 1.0;\n"
    "  for (int i = 1; i <= 96; i++) {\n"
    "    if (i > uRadius) break;\n"
    "    float w = exp(-float(i * i) / twoS2);\n"
    "    vec2 d = uStep * float(i);\n"
    "    acc += (texture(uTex, vUV + d) + texture(uTex, vUV - d)) * w;\n"
    "    wsum += 2.0 * w;\n"
    "  }\n"
    "  o = acc / wsum;\n"
    "}\n";

struct Program {
    GLuint prog = 0;
    std::map<std::string, GLint> locs;
    bool ensure(const std::string& fs, const std::string& vs = kVsFull) {
        if (prog) return true;
        prog = linkProgram(vs, fs, bindPos);
        return prog != 0;
    }
    GLint loc(const char* name) {
        auto it = locs.find(name);
        if (it != locs.end()) return it->second;
        const GLint l = glGetUniformLocation(prog, name);
        locs[name] = l;
        return l;
    }
    void set(const char* n, float v) { const GLint l = loc(n); if (l >= 0) glUniform1f(l, v); }
    void set(const char* n, float x, float y) { const GLint l = loc(n); if (l >= 0) glUniform2f(l, x, y); }
    void seti(const char* n, int v) { const GLint l = loc(n); if (l >= 0) glUniform1i(l, v); }
    void release() { if (prog) glDeleteProgram(prog); prog = 0; locs.clear(); }
};

// ============================================================================ presets
//
// One model for both preset kinds. A .glslp names GLSL ES shaders with the RetroArch GL
// uniform names; a .slangp names Vulkan GLSL stages, turned into GLSL ES by nano_shaderc and
// bound through their block and sampler names.

struct Param { std::string id, desc; float initial = 0, minimum = 0, maximum = 1, step = 0.01f, current = 0; };
struct PassDef {
    std::string shaderPath, alias;
    bool filterLinear = false;
    int scaleTypeX = 0, scaleTypeY = 0;   // 0 source, 1 absolute, 2 viewport
    float scaleX = 1, scaleY = 1;
    int absX = 0, absY = 0;
    int frameCountMod = 0;
    bool floatFbo = false, srgbFbo = false, mipmapInput = false;
    std::string wrapMode = "clamp_to_border";
};
struct LutDef { std::string id, path; bool linear = true, mipmap = false; std::string wrapMode = "clamp_to_border"; };
struct Preset {
    bool slang = false;
    std::string path, dir;
    std::vector<PassDef> passes;
    std::vector<LutDef> luts;
    std::vector<Param> params;
};

std::string readFile(const std::string& path) {
    std::ifstream f(path, std::ios::binary);
    if (!f) return std::string();
    std::stringstream ss;
    ss << f.rdbuf();
    return ss.str();
}
std::string dirOf(const std::string& p) {
    const size_t s = p.find_last_of('/');
    return s == std::string::npos ? std::string(".") : p.substr(0, s);
}
std::string trim(const std::string& s) {
    size_t a = 0, b = s.size();
    while (a < b && isspace((unsigned char)s[a])) a++;
    while (b > a && isspace((unsigned char)s[b - 1])) b--;
    return s.substr(a, b - a);
}
std::string resolvePath(const std::string& base, const std::string& rel) {
    if (!rel.empty() && rel[0] == '/') return rel;
    return base + "/" + rel;
}
// FUSE paths the menu may hand over resolve to the backend the home can read.
std::string normalizeStoragePath(const std::string& p) {
    static const char* kPrefixes[] = {"/sdcard/", "/storage/emulated/0/", "/storage/self/primary/"};
    for (const char* pre : kPrefixes)
        if (p.compare(0, strlen(pre), pre) == 0) return "/data/media/0/" + p.substr(strlen(pre));
    return p;
}
bool endsWith(const std::string& s, const char* suf) {
    const size_t n = strlen(suf);
    return s.size() >= n && s.compare(s.size() - n, n, suf) == 0;
}
int parseScaleType(const std::string& v) {
    if (v == "absolute") return 1;
    if (v == "viewport") return 2;
    return 0;
}

// The preset file: `shaders = N`, per pass shaderN / filter_linearN / scale_typeN / scaleN /
// aliasN / frame_count_modN / float_framebufferN / srgb_framebufferN / mipmap_inputN /
// wrap_modeN, `textures = a;b` with <name> / <name>_linear / <name>_wrap_mode / <name>_mipmap,
// and `parameters = a;b` with per parameter values.
bool parsePreset(const std::string& path, Preset& out) {
    const std::string content = readFile(path);
    if (content.empty()) { ALOGE("gshader: cannot read preset '%s'", path.c_str()); return false; }
    out = Preset();
    out.path = path;
    out.dir = dirOf(path);
    out.slang = endsWith(path, ".slangp");
    std::map<std::string, std::string> kv;
    std::vector<std::string> order;
    std::istringstream stream(content);
    std::string line;
    while (std::getline(stream, line)) {
        const std::string t = trim(line);
        if (t.empty() || t[0] == '#') continue;
        const size_t eq = t.find('=');
        if (eq == std::string::npos) continue;
        std::string key = trim(t.substr(0, eq));
        std::string val = trim(t.substr(eq + 1));
        if (val.size() >= 2 && val.front() == '"' && val.back() == '"') val = val.substr(1, val.size() - 2);
        kv[key] = val;
    }
    const int n = kv.count("shaders") ? atoi(kv["shaders"].c_str()) : 0;
    if (n <= 0 || n > 64) { ALOGE("gshader: bad shader count %d in '%s'", n, path.c_str()); return false; }
    out.passes.resize(n);
    auto has = [&](const std::string& k) { return kv.count(k) > 0; };
    for (int i = 0; i < n; i++) {
        PassDef& p = out.passes[i];
        const std::string idx = std::to_string(i);
        if (has("shader" + idx)) p.shaderPath = resolvePath(out.dir, kv["shader" + idx]);
        if (has("alias" + idx)) p.alias = kv["alias" + idx];
        if (has("filter_linear" + idx)) p.filterLinear = kv["filter_linear" + idx] == "true" || kv["filter_linear" + idx] == "1";
        if (has("mipmap_input" + idx)) p.mipmapInput = kv["mipmap_input" + idx] == "true" || kv["mipmap_input" + idx] == "1";
        if (has("srgb_framebuffer" + idx)) p.srgbFbo = kv["srgb_framebuffer" + idx] == "true" || kv["srgb_framebuffer" + idx] == "1";
        if (has("float_framebuffer" + idx)) p.floatFbo = kv["float_framebuffer" + idx] == "true" || kv["float_framebuffer" + idx] == "1";
        if (has("frame_count_mod" + idx)) p.frameCountMod = atoi(kv["frame_count_mod" + idx].c_str());
        if (has("wrap_mode" + idx)) p.wrapMode = kv["wrap_mode" + idx];
        if (has("scale_type" + idx)) p.scaleTypeX = p.scaleTypeY = parseScaleType(kv["scale_type" + idx]);
        if (has("scale_type_x" + idx)) p.scaleTypeX = parseScaleType(kv["scale_type_x" + idx]);
        if (has("scale_type_y" + idx)) p.scaleTypeY = parseScaleType(kv["scale_type_y" + idx]);
        if (has("scale" + idx)) p.scaleX = p.scaleY = strtof(kv["scale" + idx].c_str(), nullptr);
        if (has("scale_x" + idx)) p.scaleX = strtof(kv["scale_x" + idx].c_str(), nullptr);
        if (has("scale_y" + idx)) p.scaleY = strtof(kv["scale_y" + idx].c_str(), nullptr);
        if (p.scaleTypeX == 1) p.absX = (int)(has("scale_x" + idx) ? atoi(kv["scale_x" + idx].c_str()) : atoi(kv["scale" + idx].c_str()));
        if (p.scaleTypeY == 1) p.absY = (int)(has("scale_y" + idx) ? atoi(kv["scale_y" + idx].c_str()) : atoi(kv["scale" + idx].c_str()));
        if (p.shaderPath.empty()) { ALOGE("gshader: pass %d of '%s' names no shader", i, path.c_str()); return false; }
    }
    if (has("textures")) {
        std::istringstream ts(kv["textures"]);
        std::string tok;
        while (std::getline(ts, tok, ';')) {
            tok = trim(tok);
            if (tok.empty()) continue;
            LutDef l;
            l.id = tok;
            if (has(tok)) l.path = resolvePath(out.dir, kv[tok]);
            if (has(tok + "_linear")) l.linear = kv[tok + "_linear"] == "true" || kv[tok + "_linear"] == "1";
            if (has(tok + "_mipmap")) l.mipmap = kv[tok + "_mipmap"] == "true" || kv[tok + "_mipmap"] == "1";
            if (has(tok + "_mipmap_input")) l.mipmap = kv[tok + "_mipmap_input"] == "true" || kv[tok + "_mipmap_input"] == "1";
            if (has(tok + "_wrap_mode")) l.wrapMode = kv[tok + "_wrap_mode"];
            out.luts.push_back(l);
        }
    }
    // The preset's own parameter values (applied over the shaders' declared defaults below).
    std::map<std::string, float> presetValues;
    if (has("parameters")) {
        std::istringstream ps(kv["parameters"]);
        std::string tok;
        while (std::getline(ps, tok, ';')) {
            tok = trim(tok);
            if (!tok.empty() && has(tok)) presetValues[tok] = strtof(kv[tok].c_str(), nullptr);
        }
    }
    // #pragma parameter declarations from every shader (and its includes)
    auto scanParams = [&](const std::string& text) {
        std::istringstream ss(text);
        std::string sl;
        while (std::getline(ss, sl)) {
            const std::string tl = trim(sl);
            if (tl.find("#pragma parameter") != 0) continue;
            char id[64] = {}, desc[128] = {};
            float init = 0, mn = 0, mx = 0, st = 0;
            const int got = sscanf(tl.c_str(), "#pragma parameter %63s \"%127[^\"]\" %f %f %f %f", id, desc, &init, &mn, &mx, &st);
            if (got < 4) continue;
            bool dup = false;
            for (const Param& e : out.params) if (e.id == id) { dup = true; break; }
            if (dup) continue;
            Param p;
            p.id = id; p.desc = desc; p.initial = init; p.minimum = mn; p.maximum = mx;
            p.step = got >= 6 ? st : (out.slang ? 0.1f * (mx - mn) : 0.01f);
            p.current = init;
            out.params.push_back(p);
        }
    };
    for (const PassDef& p : out.passes) {
        // includes are scanned too (slang shaders keep their parameters in shared headers)
        std::vector<std::string> todo{p.shaderPath};
        std::vector<std::string> seen;
        while (!todo.empty() && seen.size() < 64) {
            const std::string f = todo.back();
            todo.pop_back();
            if (std::find(seen.begin(), seen.end(), f) != seen.end()) continue;
            seen.push_back(f);
            const std::string text = readFile(f);
            if (text.empty()) continue;
            scanParams(text);
            std::istringstream ss(text);
            std::string sl;
            while (std::getline(ss, sl)) {
                if (sl.find("#include") != 0) continue;
                const size_t q1 = sl.find('"'), q2 = q1 == std::string::npos ? q1 : sl.find('"', q1 + 1);
                if (q1 != std::string::npos && q2 != std::string::npos)
                    todo.push_back(resolvePath(dirOf(f), sl.substr(q1 + 1, q2 - q1 - 1)));
            }
        }
    }
    for (Param& p : out.params) {
        auto it = presetValues.find(p.id);
        if (it != presetValues.end()) { p.initial = it->second; p.current = it->second; }
    }
    ALOGI("gshader: parsed '%s': %d passes, %zu luts, %zu params", path.c_str(), n, out.luts.size(), out.params.size());
    return true;
}

// ---- stage sources --------------------------------------------------------------------------

// A .glsl file: VERTEX / FRAGMENT guards, or a fragment-only body with the stock vertex stage.
// The compatibility aliases RenderEngine's chain adds for legacy keywords apply here too.
bool splitGlslSource(const std::string& path, std::string& vertSrc, std::string& fragSrc) {
    const std::string raw = readFile(path);
    if (raw.empty()) { ALOGE("gshader: cannot read shader '%s'", path.c_str()); return false; }
    const bool hasVertexGuard = raw.find("defined(VERTEX)") != std::string::npos;
    const bool hasFragmentGuard = raw.find("defined(FRAGMENT)") != std::string::npos;
    std::string vertCompat, fragCompat;
    if (raw.find("gl_FragColor") != std::string::npos) {
        if (raw.find("vec4 FragColor") == std::string::npos) fragCompat += "out vec4 FragColor;\n";
        fragCompat += "#define gl_FragColor FragColor\n";
    }
    if (raw.find("varying") != std::string::npos) { vertCompat += "#define varying out\n"; fragCompat += "#define varying in\n"; }
    if (raw.find("attribute") != std::string::npos) vertCompat += "#define attribute in\n";
    if (raw.find("texture2D") != std::string::npos && raw.find("#define texture2D") == std::string::npos) {
        vertCompat += "#define texture2D texture\n";
        fragCompat += "#define texture2D texture\n";
    }
    if (hasVertexGuard && hasFragmentGuard) {
        vertSrc = "#version 300 es\n#define VERTEX\n#define PARAMETER_UNIFORM\n" + vertCompat + raw;
        fragSrc = "#version 300 es\nprecision highp float;\n#define FRAGMENT\n#define PARAMETER_UNIFORM\n" + fragCompat + raw;
    } else {
        vertSrc = "#version 300 es\nin vec4 VertexCoord;\nin vec4 TexCoord;\nout vec4 TEX0;\nuniform mat4 MVPMatrix;\n"
                  "void main() { gl_Position = MVPMatrix * VertexCoord; TEX0.xy = TexCoord.xy; }\n";
        fragSrc = "#version 300 es\n#define FRAGMENT\n#define PARAMETER_UNIFORM\nprecision highp float;\n" + fragCompat + raw;
    }
    auto fix = [](std::string& s) {
        std::string result;
        std::istringstream ss(s);
        std::string line;
        bool firstVersion = true;
        while (std::getline(ss, line)) {
            const std::string t = trim(line);
            if (t.find("#pragma parameter") == 0 || t.find("#define texture(") == 0 || t.find("#define texture (") == 0) {
                result += "// " + line + "\n";
            } else if (t.find("#version") == 0) {
                if (firstVersion) { firstVersion = false; result += line + "\n"; }
                else result += "// " + line + "\n";
            } else {
                result += line + "\n";
            }
        }
        s = result;
    };
    fix(vertSrc);
    fix(fragSrc);
    return true;
}

// A .slang file with its includes, split into the two stages by `#pragma stage`.
bool readSlangLines(const std::string& path, std::vector<std::string>& lines, int depth = 0) {
    if (depth > 16) { ALOGE("gshader: include depth exceeded at '%s'", path.c_str()); return false; }
    const std::string content = readFile(path);
    if (content.empty()) { ALOGE("gshader: cannot read '%s'", path.c_str()); return false; }
    const std::string dir = dirOf(path);
    std::istringstream stream(content);
    std::string line;
    while (std::getline(stream, line)) {
        if (!line.empty() && line.back() == '\r') line.pop_back();
        if (line.find("#include") == 0) {
            const size_t q1 = line.find('"'), q2 = q1 == std::string::npos ? q1 : line.find('"', q1 + 1);
            if (q1 != std::string::npos && q2 != std::string::npos) {
                if (!readSlangLines(resolvePath(dir, line.substr(q1 + 1, q2 - q1 - 1)), lines, depth + 1)) return false;
                continue;
            }
        }
        lines.push_back(line);
    }
    return true;
}
std::string buildStage(const std::vector<std::string>& lines, const char* stage) {
    std::string out;
    bool active = true, first = true;
    const std::string want = std::string("#pragma stage ") + stage;
    for (const std::string& line : lines) {
        if (first) { out += line + "\n"; first = false; continue; }   // #version
        if (line.find("#pragma stage ") == 0) { active = trim(line) == want; continue; }
        if (line.find("#pragma name ") == 0 || line.find("#pragma format ") == 0 ||
            line.find("#pragma parameter ") == 0) { out += "\n"; continue; }
        if (active) out += line;
        out += "\n";
    }
    return out;
}

// nano_shaderc turns the two Vulkan GLSL stages into GLSL ES; results are kept per shader path
// (a preset is reloaded for every size and panel, the compile only once).
struct Compiled { std::string vert, frag; bool ok = false; };
std::map<std::string, Compiled> gCompileCache;

bool writeText(const std::string& path, const std::string& text) {
    std::ofstream f(path, std::ios::binary | std::ios::trunc);
    if (!f) return false;
    f << text;
    return (bool)f;
}

const Compiled& compileSlang(const std::string& shaderPath) {
    auto it = gCompileCache.find(shaderPath);
    if (it != gCompileCache.end()) return it->second;
    Compiled& c = gCompileCache[shaderPath];
    std::vector<std::string> lines;
    if (!readSlangLines(shaderPath, lines)) return c;
    const std::string vert = buildStage(lines, "vertex"), frag = buildStage(lines, "fragment");
    const char* dir = "/data/system/nano_shaderc";
    mkdir(dir, 0700);
    const std::string base = std::string(dir) + "/" + std::to_string((unsigned)getpid());
    const std::string inV = base + ".vert.in", inF = base + ".frag.in", outV = base + ".vert.out", outF = base + ".frag.out";
    unlink(outV.c_str()); unlink(outF.c_str());
    if (!writeText(inV, vert) || !writeText(inF, frag)) { ALOGE("gshader: cannot stage '%s' for nano_shaderc", shaderPath.c_str()); return c; }
    const pid_t pid = fork();
    if (pid == 0) {
        const int devnull = open("/dev/null", O_RDWR);
        if (devnull >= 0) { dup2(devnull, 0); dup2(devnull, 1); }
        // the system copy, or a development copy pushed next to the home's test binaries
        for (const char* exe : {"/system/bin/nano_shaderc", "/data/local/tmp/nano_shaderc"})
            if (access(exe, X_OK) == 0)
                execl(exe, "nano_shaderc", inV.c_str(), inF.c_str(), outV.c_str(), outF.c_str(), (char*)nullptr);
        _exit(127);
    }
    int status = -1;
    if (pid > 0) waitpid(pid, &status, 0);
    unlink(inV.c_str()); unlink(inF.c_str());
    if (pid <= 0 || !WIFEXITED(status) || WEXITSTATUS(status) != 0) {
        ALOGE("gshader: nano_shaderc failed on '%s' (status 0x%x)", shaderPath.c_str(), status);
        unlink(outV.c_str()); unlink(outF.c_str());
        return c;
    }
    c.vert = readFile(outV);
    c.frag = readFile(outF);
    unlink(outV.c_str()); unlink(outF.c_str());
    c.ok = !c.vert.empty() && !c.frag.empty();
    if (c.ok) ALOGI("gshader: compiled '%s' to GLSL ES", shaderPath.c_str());
    return c;
}

// ---- the chain ---------------------------------------------------------------------------

constexpr int kMaxPasses = 12;
constexpr int kMaxTexRefs = 16;

// What one pass binds, resolved once at link from the program's active uniforms.
struct Block {   // a slang uniform block (the UBO, or the push constants as a block)
    GLuint index = 0, buffer = 0;
    int size = 0;
    std::vector<uint8_t> data;
    struct Member { std::string name; int offset; GLenum type; };
    std::vector<Member> members;
};
struct Pass {
    GLuint program = 0;
    Target out, feedbackOut;   // feedbackOut: last frame's output when a later pass reads PassFeedbackN
    bool feedbackUsed = false;
    int w = 0, h = 0;
    bool filterLinear = false;
    // .glslp uniforms (RetroArch GL names)
    GLint locMVP = -1, locOutputSize = -1, locTextureSize = -1, locInputSize = -1, locFrameCount = -1,
          locFrameDirection = -1, locTexture = -1, locOrigTexture = -1, locOrigTextureSize = -1, locOrigInputSize = -1;
    GLint locPassTexture[kMaxTexRefs], locPassTextureSize[kMaxTexRefs], locPassInputSize[kMaxTexRefs];
    GLint locPassPrevTexture[kMaxTexRefs], locPassPrevTextureSize[kMaxTexRefs], locPassPrevInputSize[kMaxTexRefs];
    std::vector<GLint> paramLocs, lutLocs, lutSizeLocs;
    // slang bindings (by name)
    std::vector<Block> blocks;
    GLint sSource = -1, sOriginal = -1;
    GLint sPassOutput[kMaxTexRefs], sPassFeedback[kMaxTexRefs], sHistory[kMaxTexRefs];
    std::vector<GLint> sLut;
    Pass() {
        for (int i = 0; i < kMaxTexRefs; i++) {
            locPassTexture[i] = locPassTextureSize[i] = locPassInputSize[i] = -1;
            locPassPrevTexture[i] = locPassPrevTextureSize[i] = locPassPrevInputSize[i] = -1;
            sPassOutput[i] = sPassFeedback[i] = sHistory[i] = -1;
        }
    }
    void release() {
        if (program) glDeleteProgram(program);
        program = 0;
        out.release(); feedbackOut.release();
        for (Block& b : blocks) if (b.buffer) glDeleteBuffers(1, &b.buffer);
        blocks.clear();
    }
};
struct Lut { std::string id; GLuint tex = 0; int w = 0, h = 0; bool owned = false; };

struct Chain {
    std::vector<Pass> passes;
    std::vector<Lut> luts;
    GLuint quadVbo = 0;
    std::string presetPath;
    int viewW = 0, viewH = 0;
    bool valid = false;
    bool slang = false;
    int historyDepth = 0;
    std::vector<Target> history;   // OriginalHistory1.. (the chain's source of the previous frames)
    int historyHead = 0;
    uint32_t frames = 0;
    void release() {
        for (Pass& p : passes) p.release();
        passes.clear();
        for (Lut& l : luts) if (l.owned && l.tex) glDeleteTextures(1, &l.tex);
        luts.clear();
        for (Target& t : history) t.release();
        history.clear();
        if (quadVbo) glDeleteBuffers(1, &quadVbo);
        quadVbo = 0;
        valid = false;
    }
};

GLuint gTransparentLut = 0;
GLuint transparentLut() {
    if (!gTransparentLut) {
        const uint8_t zero[4] = {0, 0, 0, 0};
        glGenTextures(1, &gTransparentLut);
        glBindTexture(GL_TEXTURE_2D, gTransparentLut);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, zero);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    }
    return gTransparentLut;
}
GLint wrapMode(const std::string& w) {
    if (w == "repeat") return GL_REPEAT;
    if (w == "mirrored_repeat") return GL_MIRRORED_REPEAT;
    return GL_CLAMP_TO_EDGE;   // GLES has no border clamp
}

// A LUT image decoded with stb_image, flipped so it sits upright in bottom-left texture space.
GLuint loadLut(const LutDef& def, int& w, int& h) {
    const std::string p = normalizeStoragePath(def.path);
    int comp = 0;
    stbi_uc* px = stbi_load(p.c_str(), &w, &h, &comp, 4);
    if (!px) { ALOGE("gshader: LUT '%s' failed to load", p.c_str()); return 0; }
    const size_t rb = (size_t)w * 4;
    std::vector<uint8_t> flipped(rb * h);
    for (int y = 0; y < h; y++) memcpy(&flipped[(size_t)(h - 1 - y) * rb], px + (size_t)y * rb, rb);
    stbi_image_free(px);
    GLuint tex = 0;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, flipped.data());
    const GLint wrap = wrapMode(def.wrapMode);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap);
    const GLenum mag = def.linear ? GL_LINEAR : GL_NEAREST;
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, mag);
    if (def.mipmap) {
        glGenerateMipmap(GL_TEXTURE_2D);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, def.linear ? GL_LINEAR_MIPMAP_LINEAR : GL_NEAREST_MIPMAP_NEAREST);
    } else {
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, mag);
    }
    ALOGI("gshader: LUT '%s' %dx%d", p.c_str(), w, h);
    return tex;
}

void computePassSize(const PassDef& d, int srcW, int srcH, int viewW, int viewH, int& outW, int& outH) {
    switch (d.scaleTypeX) {
        case 1: outW = d.absX > 0 ? d.absX : srcW; break;
        case 2: outW = (int)(viewW * d.scaleX); break;
        default: outW = (int)(srcW * d.scaleX); break;
    }
    switch (d.scaleTypeY) {
        case 1: outH = d.absY > 0 ? d.absY : srcH; break;
        case 2: outH = (int)(viewH * d.scaleY); break;
        default: outH = (int)(srcH * d.scaleY); break;
    }
    if (outW <= 0) outW = viewW;
    if (outH <= 0) outH = viewH;
}

void bindRetroArchAttribs(GLuint prog) {
    glBindAttribLocation(prog, 0, "VertexCoord");
    glBindAttribLocation(prog, 1, "TexCoord");
    glBindAttribLocation(prog, 2, "COLOR");
}
void bindSlangAttribs(GLuint prog) {
    glBindAttribLocation(prog, 0, "Position");
    glBindAttribLocation(prog, 1, "TexCoord");
}

std::string baseName(const std::string& qualified) {
    const size_t d = qualified.find_last_of('.');
    return d == std::string::npos ? qualified : qualified.substr(d + 1);
}

// Resolves a slang pass's samplers and uniform blocks from the linked program.
void reflectSlang(Chain& chain, const Preset& preset, Pass& pass) {
    const GLuint prog = pass.program;
    GLint count = 0;
    glGetProgramiv(prog, GL_ACTIVE_UNIFORMS, &count);
    pass.sLut.assign(preset.luts.size(), -1);
    for (GLint i = 0; i < count; i++) {
        char name[256] = {};
        GLint size = 0;
        GLenum type = 0;
        glGetActiveUniform(prog, (GLuint)i, sizeof name - 1, nullptr, &size, &type, name);
        if (type != GL_SAMPLER_2D) continue;
        const GLint loc = glGetUniformLocation(prog, name);
        if (loc < 0) continue;
        const std::string n = name;
        if (n == "Source" || n == "InputImage") pass.sSource = loc;
        else if (n == "Original" || n == "OriginalImage") pass.sOriginal = loc;
        else if (n.compare(0, 15, "OriginalHistory") == 0) { const int k = atoi(n.c_str() + 15); if (k >= 0 && k < kMaxTexRefs) { pass.sHistory[k] = loc; chain.historyDepth = std::max(chain.historyDepth, k); } }
        else if (n.compare(0, 10, "PassOutput") == 0) { const int k = atoi(n.c_str() + 10); if (k >= 0 && k < kMaxTexRefs) pass.sPassOutput[k] = loc; }
        else if (n.compare(0, 12, "PassFeedback") == 0) { const int k = atoi(n.c_str() + 12); if (k >= 0 && k < kMaxTexRefs) pass.sPassFeedback[k] = loc; }
        else {
            bool matched = false;
            for (int k = 0; k < (int)preset.passes.size() && k < kMaxTexRefs && !matched; k++) {
                if (preset.passes[k].alias.empty()) continue;
                if (preset.passes[k].alias == n) { pass.sPassOutput[k] = loc; matched = true; }
                else if (preset.passes[k].alias + "Feedback" == n) { pass.sPassFeedback[k] = loc; matched = true; }
            }
            for (int k = 0; k < (int)preset.luts.size() && !matched; k++)
                if (preset.luts[k].id == n) { pass.sLut[k] = loc; matched = true; }
            if (!matched) ALOGW("gshader: sampler '%s' has no semantic", name);
        }
    }
    GLint blocks = 0;
    glGetProgramiv(prog, GL_ACTIVE_UNIFORM_BLOCKS, &blocks);
    for (GLint b = 0; b < blocks; b++) {
        Block blk;
        blk.index = (GLuint)b;
        GLint size = 0, n = 0;
        glGetActiveUniformBlockiv(prog, (GLuint)b, GL_UNIFORM_BLOCK_DATA_SIZE, &size);
        glGetActiveUniformBlockiv(prog, (GLuint)b, GL_UNIFORM_BLOCK_ACTIVE_UNIFORMS, &n);
        blk.size = size;
        blk.data.assign((size_t)size, 0);
        std::vector<GLint> idx((size_t)std::max(n, 0));
        if (n > 0) glGetActiveUniformBlockiv(prog, (GLuint)b, GL_UNIFORM_BLOCK_ACTIVE_UNIFORM_INDICES, idx.data());
        for (GLint k = 0; k < n; k++) {
            const GLuint ui = (GLuint)idx[k];
            GLint offset = 0;
            glGetActiveUniformsiv(prog, 1, &ui, GL_UNIFORM_OFFSET, &offset);
            char name[256] = {};
            GLint asize = 0;
            GLenum type = 0;
            glGetActiveUniform(prog, ui, sizeof name - 1, nullptr, &asize, &type, name);
            blk.members.push_back({baseName(name), offset, type});
        }
        glGenBuffers(1, &blk.buffer);
        glBindBuffer(GL_UNIFORM_BUFFER, blk.buffer);
        glBufferData(GL_UNIFORM_BUFFER, size, nullptr, GL_DYNAMIC_DRAW);
        glUniformBlockBinding(prog, (GLuint)b, (GLuint)b);
        pass.blocks.push_back(std::move(blk));
    }
}

bool initChain(Chain& chain, const Preset& preset, int viewW, int viewH) {
    chain.release();
    const int n = (int)preset.passes.size();
    if (n > kMaxPasses) {
        ALOGE("gshader: '%s' has %d passes (limit %d)", preset.path.c_str(), n, kMaxPasses);
        setStatus("passes:" + std::to_string(n) + "/" + std::to_string(kMaxPasses));
        return false;
    }
    chain.slang = preset.slang;
    chain.presetPath = preset.path;
    chain.viewW = viewW; chain.viewH = viewH;
    chain.passes.resize(n);
    for (const LutDef& d : preset.luts) {
        Lut l;
        l.id = d.id;
        int w = 0, h = 0;
        const GLuint tex = d.path.empty() ? 0 : loadLut(d, w, h);
        if (tex) { l.tex = tex; l.w = w; l.h = h; l.owned = true; }
        else { l.tex = transparentLut(); l.w = l.h = 1; }
        chain.luts.push_back(l);
    }
    int prevW = viewW, prevH = viewH;
    for (int i = 0; i < n; i++) {
        const PassDef& d = preset.passes[i];
        Pass& p = chain.passes[i];
        std::string vert, frag;
        if (preset.slang) {
            const Compiled& c = compileSlang(d.shaderPath);
            if (!c.ok) { setStatus("compile_fail"); return false; }
            vert = c.vert; frag = c.frag;
        } else if (!splitGlslSource(d.shaderPath, vert, frag)) {
            setStatus("compile_fail");
            return false;
        }
        p.program = linkProgram(vert, frag, preset.slang ? bindSlangAttribs : bindRetroArchAttribs);
        if (!p.program) {
            if (gS.debug) ALOGE("gshader: vertex source:\n%s\nfragment source:\n%s", vert.c_str(), frag.c_str());
            setStatus("compile_fail");
            return false;
        }
        if (preset.slang) {
            reflectSlang(chain, preset, p);
        } else {
            const GLuint prog = p.program;
            p.locMVP = glGetUniformLocation(prog, "MVPMatrix");
            p.locOutputSize = glGetUniformLocation(prog, "OutputSize");
            p.locTextureSize = glGetUniformLocation(prog, "TextureSize");
            p.locInputSize = glGetUniformLocation(prog, "InputSize");
            p.locFrameCount = glGetUniformLocation(prog, "FrameCount");
            p.locFrameDirection = glGetUniformLocation(prog, "FrameDirection");
            p.locTexture = glGetUniformLocation(prog, "Texture");
            p.locOrigTexture = glGetUniformLocation(prog, "OrigTexture");
            p.locOrigTextureSize = glGetUniformLocation(prog, "OrigTextureSize");
            p.locOrigInputSize = glGetUniformLocation(prog, "OrigInputSize");
            for (int j = 0; j < kMaxTexRefs; j++) {
                const std::string idx = std::to_string(j);
                p.locPassTexture[j] = glGetUniformLocation(prog, ("Pass" + idx + "Texture").c_str());
                p.locPassTextureSize[j] = glGetUniformLocation(prog, ("Pass" + idx + "TextureSize").c_str());
                p.locPassInputSize[j] = glGetUniformLocation(prog, ("Pass" + idx + "InputSize").c_str());
                if (j >= 1) {
                    p.locPassPrevTexture[j] = glGetUniformLocation(prog, ("PassPrev" + idx + "Texture").c_str());
                    p.locPassPrevTextureSize[j] = glGetUniformLocation(prog, ("PassPrev" + idx + "TextureSize").c_str());
                    p.locPassPrevInputSize[j] = glGetUniformLocation(prog, ("PassPrev" + idx + "InputSize").c_str());
                }
            }
            p.paramLocs.resize(preset.params.size());
            for (size_t j = 0; j < preset.params.size(); j++) p.paramLocs[j] = glGetUniformLocation(prog, preset.params[j].id.c_str());
            p.lutLocs.assign(chain.luts.size(), -1);
            p.lutSizeLocs.assign(chain.luts.size(), -1);
            for (size_t j = 0; j < chain.luts.size(); j++) {
                p.lutLocs[j] = glGetUniformLocation(prog, chain.luts[j].id.c_str());
                p.lutSizeLocs[j] = glGetUniformLocation(prog, (chain.luts[j].id + "Size").c_str());
            }
        }
        p.filterLinear = d.filterLinear;
        if (preset.slang && i == n - 1) { p.w = viewW; p.h = viewH; }   // the final slang pass fills the viewport
        else computePassSize(d, prevW, prevH, viewW, viewH, p.w, p.h);
        GLenum fmt = GL_RGBA8;
        if (d.floatFbo) fmt = GL_RGBA16F;
        else if (d.srgbFbo) fmt = GL_SRGB8_ALPHA8;
        const GLenum filter = d.filterLinear ? GL_LINEAR : GL_NEAREST;
        if (!p.out.ensure(p.w, p.h, fmt, filter, filter, wrapMode(d.wrapMode)) &&
            !p.out.ensure(p.w, p.h, GL_RGBA8, filter, filter, wrapMode(d.wrapMode))) {
            ALOGE("gshader: pass %d target %dx%d failed", i, p.w, p.h);
            setStatus("compile_fail");
            return false;
        }
        ALOGI("gshader: pass %d: %s %dx%d %s", i, d.shaderPath.c_str(), p.w, p.h, d.filterLinear ? "linear" : "nearest");
        prevW = p.w; prevH = p.h;
    }
    // feedback targets for the passes a later (or the same) pass reads back
    for (int i = 0; i < n; i++)
        for (int k = 0; k < n && k < kMaxTexRefs; k++)
            if (chain.passes[i].sPassFeedback[k] >= 0) chain.passes[k].feedbackUsed = true;
    for (int k = 0; k < n; k++) {
        Pass& p = chain.passes[k];
        if (!p.feedbackUsed) continue;
        const GLenum filter = p.filterLinear ? GL_LINEAR : GL_NEAREST;
        if (!p.feedbackOut.ensure(p.w, p.h, p.out.internalFormat, filter, filter)) p.feedbackUsed = false;
        else {
            glBindFramebuffer(GL_FRAMEBUFFER, p.feedbackOut.fbo);
            glClearColor(0, 0, 0, 1);
            glClear(GL_COLOR_BUFFER_BIT);
        }
    }
    // RetroArch's quad: positions and texture coordinates in [0,1]; the MVP maps to clip space
    static const float quad[32] = {
        0, 0, 0, 1,  0, 0, 0, 1,
        1, 0, 0, 1,  1, 0, 0, 1,
        0, 1, 0, 1,  0, 1, 0, 1,
        1, 1, 0, 1,  1, 1, 0, 1,
    };
    glGenBuffers(1, &chain.quadVbo);
    glBindBuffer(GL_ARRAY_BUFFER, chain.quadVbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof quad, quad, GL_STATIC_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    chain.valid = true;
    setStatus("ok");
    ALOGI("gshader: chain '%s' ready: %d passes at %dx%d", preset.path.c_str(), n, viewW, viewH);
    return true;
}

// Writes a block member (every member of that name, any block) for the slang path.
void blockSet4(Pass& p, const char* name, float x, float y, float z, float w) {
    for (Block& b : p.blocks)
        for (const Block::Member& m : b.members)
            if (m.name == name && m.offset + 16 <= b.size) {
                float* f = (float*)(b.data.data() + m.offset);
                f[0] = x; f[1] = y; f[2] = z; f[3] = w;
            }
}
void blockSetSize(Pass& p, const char* name, int w, int h) { blockSet4(p, name, (float)w, (float)h, 1.0f / w, 1.0f / h); }
void blockSetU32(Pass& p, const char* name, uint32_t v) {
    for (Block& b : p.blocks)
        for (const Block::Member& m : b.members)
            if (m.name == name && m.offset + 4 <= b.size) memcpy(b.data.data() + m.offset, &v, 4);
}
void blockSetF(Pass& p, const char* name, float v) {
    for (Block& b : p.blocks)
        for (const Block::Member& m : b.members)
            if (m.name == name && m.offset + 4 <= b.size) memcpy(b.data.data() + m.offset, &v, 4);
}
void blockSetMvp(Pass& p, const float* mvp) {
    for (Block& b : p.blocks)
        for (const Block::Member& m : b.members)
            if (m.name == "MVP" && m.offset + 64 <= b.size) memcpy(b.data.data() + m.offset, mvp, 64);
}

// Runs the chain over `srcTex` (chainSrcW x chainSrcH, the texture actually sampled) advertising
// advW x advH as the source size; the final pass renders at the chain's view size.
bool renderChain(Chain& chain, const Preset& preset, GLuint srcTex, int chainSrcW, int chainSrcH,
                 int advW, int advH) {
    if (!chain.valid || chain.passes.empty()) return false;
    const int n = (int)chain.passes.size();
    static const float mvp[16] = { 2, 0, 0, 0,  0, 2, 0, 0,  0, 0, 1, 0,  -1, -1, 0, 1 };
    plainState();
    glBindBuffer(GL_ARRAY_BUFFER, chain.quadVbo);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 4, GL_FLOAT, GL_FALSE, 8 * sizeof(float), (void*)0);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, chain.slang ? 2 : 4, GL_FLOAT, GL_FALSE, 8 * sizeof(float), (void*)(4 * sizeof(float)));

    // OriginalHistoryN: the chain's source of N frames ago (a copy of each frame's source)
    if (chain.slang && chain.historyDepth > 0) {
        if ((int)chain.history.size() != chain.historyDepth + 1) {
            for (Target& t : chain.history) t.release();
            chain.history.assign(chain.historyDepth + 1, Target());
            chain.historyHead = 0;
        }
    }

    GLuint curTex = srcTex;
    int curW = chainSrcW, curH = chainSrcH;
    int srcAdvW = advW, srcAdvH = advH;
    for (int i = 0; i < n; i++) {
        Pass& p = chain.passes[i];
        const PassDef& d = preset.passes[i];
        glBindFramebuffer(GL_FRAMEBUFFER, p.out.fbo);
        glViewport(0, 0, p.w, p.h);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        glUseProgram(p.program);
        uint32_t fc = chain.frames;
        if (d.frameCountMod > 0) fc %= (uint32_t)d.frameCountMod;
        int unit = 0;
        auto bindTex = [&](GLint loc, GLuint tex, GLenum filter, GLint wrap, bool mip) {
            if (loc < 0) return;
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(GL_TEXTURE_2D, tex);
            if (mip) {
                glGenerateMipmap(GL_TEXTURE_2D);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter == GL_LINEAR ? GL_LINEAR_MIPMAP_LINEAR : GL_NEAREST_MIPMAP_NEAREST);
            } else {
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
            }
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap);
            glUniform1i(loc, unit);
            unit++;
        };
        const GLenum filter = p.filterLinear ? GL_LINEAR : GL_NEAREST;
        const GLint wrap = wrapMode(d.wrapMode);
        // the sizes this pass sees: its input (advertised for pass 0) and the frame source
        const int inW = i == 0 ? srcAdvW : curW, inH = i == 0 ? srcAdvH : curH;
        if (chain.slang) {
            blockSetMvp(p, mvp);
            blockSetSize(p, "OutputSize", p.w, p.h);
            blockSetSize(p, "FinalViewportSize", chain.viewW, chain.viewH);
            blockSetSize(p, "SourceSize", inW, inH);
            blockSetSize(p, "OriginalSize", srcAdvW, srcAdvH);
            blockSetU32(p, "FrameCount", fc);
            blockSetU32(p, "FrameDirection", 1);
            for (int k = 0; k < n && k < kMaxTexRefs; k++) {
                blockSetSize(p, ("PassOutputSize" + std::to_string(k)).c_str(), chain.passes[k].w, chain.passes[k].h);
                blockSetSize(p, ("PassFeedbackSize" + std::to_string(k)).c_str(), chain.passes[k].w, chain.passes[k].h);
                if (!preset.passes[k].alias.empty()) {
                    blockSetSize(p, (preset.passes[k].alias + "Size").c_str(), chain.passes[k].w, chain.passes[k].h);
                    blockSetSize(p, (preset.passes[k].alias + "FeedbackSize").c_str(), chain.passes[k].w, chain.passes[k].h);
                }
            }
            for (int k = 0; k <= chain.historyDepth && k < kMaxTexRefs; k++)
                blockSetSize(p, ("OriginalHistorySize" + std::to_string(k)).c_str(), chainSrcW, chainSrcH);
            for (size_t k = 0; k < chain.luts.size(); k++)
                blockSetSize(p, (chain.luts[k].id + "Size").c_str(), chain.luts[k].w, chain.luts[k].h);
            for (const Param& pr : preset.params) blockSetF(p, pr.id.c_str(), pr.current);
            for (Block& b : p.blocks) {
                glBindBuffer(GL_UNIFORM_BUFFER, b.buffer);
                glBufferData(GL_UNIFORM_BUFFER, b.size, b.data.data(), GL_DYNAMIC_DRAW);
                glBindBufferBase(GL_UNIFORM_BUFFER, b.index, b.buffer);
            }
            bindTex(p.sSource, curTex, filter, wrap, d.mipmapInput);
            bindTex(p.sOriginal, srcTex, GL_LINEAR, GL_CLAMP_TO_EDGE, false);
            for (int k = 0; k < n && k < kMaxTexRefs; k++) {
                if (k < i) bindTex(p.sPassOutput[k], chain.passes[k].out.tex, chain.passes[k].filterLinear ? GL_LINEAR : GL_NEAREST, GL_CLAMP_TO_EDGE, false);
                if (chain.passes[k].feedbackUsed) bindTex(p.sPassFeedback[k], chain.passes[k].feedbackOut.tex, chain.passes[k].filterLinear ? GL_LINEAR : GL_NEAREST, GL_CLAMP_TO_EDGE, false);
            }
            for (int k = 1; k <= chain.historyDepth && k < kMaxTexRefs && k < (int)chain.history.size(); k++) {
                const int slot = ((chain.historyHead - k) % (int)chain.history.size() + (int)chain.history.size()) % (int)chain.history.size();
                const GLuint tex = chain.history[slot].tex ? chain.history[slot].tex : srcTex;
                bindTex(p.sHistory[k], tex, GL_LINEAR, GL_CLAMP_TO_EDGE, false);
            }
            if (p.sHistory[0] >= 0) bindTex(p.sHistory[0], srcTex, GL_LINEAR, GL_CLAMP_TO_EDGE, false);
            for (size_t k = 0; k < chain.luts.size() && k < p.sLut.size(); k++)
                bindTex(p.sLut[k], chain.luts[k].tex, preset.luts[k].linear ? GL_LINEAR : GL_NEAREST, wrapMode(preset.luts[k].wrapMode), preset.luts[k].mipmap);
        } else {
            if (p.locMVP >= 0) glUniformMatrix4fv(p.locMVP, 1, GL_FALSE, mvp);
            if (p.locOutputSize >= 0) glUniform2f(p.locOutputSize, (float)p.w, (float)p.h);
            if (p.locTextureSize >= 0) glUniform2f(p.locTextureSize, (float)inW, (float)inH);
            if (p.locInputSize >= 0) glUniform2f(p.locInputSize, (float)inW, (float)inH);
            if (p.locOrigTextureSize >= 0) glUniform2f(p.locOrigTextureSize, (float)srcAdvW, (float)srcAdvH);
            if (p.locOrigInputSize >= 0) glUniform2f(p.locOrigInputSize, (float)srcAdvW, (float)srcAdvH);
            if (p.locFrameCount >= 0) glUniform1i(p.locFrameCount, (int)fc);
            if (p.locFrameDirection >= 0) glUniform1i(p.locFrameDirection, 1);
            for (size_t j = 0; j < preset.params.size() && j < p.paramLocs.size(); j++)
                if (p.paramLocs[j] >= 0) glUniform1f(p.paramLocs[j], preset.params[j].current);
            bindTex(p.locTexture, curTex, filter, GL_CLAMP_TO_EDGE, d.mipmapInput);
            bindTex(p.locOrigTexture, srcTex, GL_LINEAR, GL_CLAMP_TO_EDGE, false);
            for (int j = 0; j < kMaxTexRefs && j < i; j++) {
                if (p.locPassTextureSize[j] >= 0) glUniform2f(p.locPassTextureSize[j], (float)chain.passes[j].w, (float)chain.passes[j].h);
                if (p.locPassInputSize[j] >= 0) glUniform2f(p.locPassInputSize[j], (float)chain.passes[j].w, (float)chain.passes[j].h);
                bindTex(p.locPassTexture[j], chain.passes[j].out.tex, chain.passes[j].filterLinear ? GL_LINEAR : GL_NEAREST, GL_CLAMP_TO_EDGE, false);
            }
            for (int j = 1; j < kMaxTexRefs; j++) {
                const int ref = i - j;
                const float rw = ref >= 0 ? (float)chain.passes[ref].w : (float)srcAdvW;
                const float rh = ref >= 0 ? (float)chain.passes[ref].h : (float)srcAdvH;
                if (p.locPassPrevTextureSize[j] >= 0) glUniform2f(p.locPassPrevTextureSize[j], rw, rh);
                if (p.locPassPrevInputSize[j] >= 0) glUniform2f(p.locPassPrevInputSize[j], rw, rh);
                bindTex(p.locPassPrevTexture[j], ref >= 0 ? chain.passes[ref].out.tex : srcTex, GL_LINEAR, GL_CLAMP_TO_EDGE, false);
            }
            for (size_t j = 0; j < chain.luts.size(); j++) {
                if (p.lutSizeLocs[j] >= 0) glUniform2f(p.lutSizeLocs[j], (float)chain.luts[j].w, (float)chain.luts[j].h);
                bindTex(p.lutLocs[j], chain.luts[j].tex, preset.luts[j].linear ? GL_LINEAR : GL_NEAREST, wrapMode(preset.luts[j].wrapMode), false);
            }
        }
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        curTex = p.out.tex;
        curW = p.w; curH = p.h;
    }
    glDisableVertexAttribArray(0);
    glDisableVertexAttribArray(1);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    // the feedback copies for next frame, and this frame's source into the history ring
    for (Pass& p : chain.passes)
        if (p.feedbackUsed) gBlit.run(p.feedbackOut, p.w, p.h, p.out.tex, GL_NEAREST);
    if (chain.slang && !chain.history.empty()) {
        chain.historyHead = (chain.historyHead + 1) % (int)chain.history.size();
        gBlit.run(chain.history[chain.historyHead], chainSrcW, chainSrcH, srcTex, GL_NEAREST);
    }
    // alpha forced opaque on the final pass (CRT shaders often leave it)
    {
        Pass& last = chain.passes.back();
        glBindFramebuffer(GL_FRAMEBUFFER, last.out.fbo);
        glColorMask(GL_FALSE, GL_FALSE, GL_FALSE, GL_TRUE);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    }
    chain.frames++;
    return true;
}

// ---- the preset's parameter files (shared with the menu and the ShaderControl app) ----------

const char* const kMetaDir = "/data/media/0/GammaShader";
const char* const kMetaPath = "/data/media/0/GammaShader/.shader_param_meta";
const char* const kValsPath = "/data/media/0/GammaShader/.shader_params";

void writeParamMeta(const Preset& preset) {
    mkdir(kMetaDir, 0775);
    std::string text;
    for (const Param& p : preset.params) {
        float mn = p.minimum;
        if (p.id == "overscale") mn = -3.0f;   // border presets need room below zero on 4:3 panels
        char line[512];
        snprintf(line, sizeof line, "%s|%s|%g|%g|%g|%g\n", p.id.c_str(), p.desc.c_str(), p.initial, mn, p.maximum, p.step);
        text += line;
    }
    if (readFile(kMetaPath) != text) {
        writeText(kMetaPath, text);
        chmod(kMetaPath, 0664);
    }
}

struct ParamFile { time_t mtime = 0; bool known = false; };
ParamFile gParamFile;

// True when the overrides changed (then applied over the preset's defaults).
bool applyParamOverrides(Preset& preset) {
    struct stat st;
    const bool exists = stat(kValsPath, &st) == 0;
    const time_t mtime = exists ? st.st_mtime : 0;
    if (gParamFile.known && mtime == gParamFile.mtime) return false;
    gParamFile.known = true;
    gParamFile.mtime = mtime;
    for (Param& p : preset.params) p.current = p.initial;
    if (!exists) return true;
    std::istringstream s(readFile(kValsPath));
    std::string line;
    while (std::getline(s, line)) {
        const std::string t = trim(line);
        if (t.empty() || t[0] == '#') continue;
        const size_t eq = t.find('=');
        if (eq == std::string::npos) continue;
        const std::string id = trim(t.substr(0, eq));
        const float v = strtof(trim(t.substr(eq + 1)).c_str(), nullptr);
        for (Param& p : preset.params) if (p.id == id) { p.current = v; break; }
    }
    return true;
}

// ---- resolution scale and scanline density (as RenderEngine's GL chain) ---------------------

float parseResScale(const std::string& v) {
    if (v == "3/4" || v == "0.75") return 0.75f;
    if (v == "1/2" || v == "0.5" || v == "0.50") return 0.5f;
    if (v == "1/3" || v == "0.33") return 1.0f / 3.0f;
    if (v == "1/4" || v == "0.25") return 0.25f;
    if (v == "1/5" || v == "0.2") return 0.2f;
    if (v == "1/6") return 1.0f / 6.0f;
    if (v == "1/8" || v == "0.125") return 0.125f;
    return 1.0f;
}
bool isLowResPreset(const std::string& type, const std::string& presetPath) {
    std::string s = type + "|" + presetPath;
    for (char& c : s) c = (char)tolower((unsigned char)c);
    static const char* kLowRes[] = {
        "crt", "scanline", "aperture", "lottes", "geom", "easymode", "royale", "ntsc", "slotmask",
        "dotmask", "trinitron", "hyllian", "zfast", "phosphor",
        "handheld", "lcd", "dot-matrix", "gameboy", "dmg", "gba", "gbc"};
    for (const char* k : kLowRes) if (s.find(k) != std::string::npos) return true;
    return false;
}
int computeEffectHeight(const std::string& density, int outH) {
    float periodPx;
    if (density == "off") return 0;
    else if (density == "fine") periodPx = 4.5f;
    else if (density == "coarse") periodPx = 8.0f;
    else periodPx = 6.0f;
    int effH = (int)(outH / periodPx + 0.5f);
    if (effH < 64) effH = 64;
    if (effH > 384) effH = 384;
    return effH;
}

// ============================================================================ per panel state

struct Panel {
    Target orientPre, orientPost, downscale, halfRes, effect, prevSrc, blurA, blurB;
    bool havePrev = false;
    Chain chain;
    Preset preset;
    std::string presetLoaded, presetFailed;
    int initRetries = 0;
    void release() {
        orientPre.release(); orientPost.release(); downscale.release(); halfRes.release();
        effect.release(); prevSrc.release(); blurA.release(); blurB.release();
        havePrev = false;
        chain.release();
        presetLoaded.clear();
    }
};
Panel gPanel[2];
Program gCrt, gLcd3x, gLcd, gBlurFill, gGauss;

// ---- builtins ------------------------------------------------------------------------------

GLuint runCrt(Panel& pn, GLuint src, int w, int h, float scanAngleDeg) {
    if (!gCrt.ensure(std::string(kCommonFs) + kCrtFs)) return 0;
    const bool halfRes = getBoolProp("persist.gammaos.shader.crt-simple.half_res", false);
    float scan_px = std::max(1.0f, getFloatProp("persist.gammaos.shader.crt-simple.scan_px", "4"));
    float scan_strength = clampf(getFloatProp("persist.gammaos.shader.crt-simple.scan_strength", "0.4"), 0, 1);
    float curv = getFloatProp("persist.gammaos.shader.crt-simple.curv", "0.03");
    float vignette = clampf(getFloatProp("persist.gammaos.shader.crt-simple.vignette", "0.01"), 0, 1);
    float edge_soft_px = std::max(0.0f, getFloatProp("persist.gammaos.shader.crt-simple.edge_soft_px", "4"));
    float blur_intensity = clampf(getFloatProp("persist.gammaos.shader.crt-simple.blur_intensity", "0"), 0, 1);
    float triad_px = 0, mask_strength = 0, scan_phase = 0;
    if (gS.fast) { triad_px = 0; mask_strength = 0; curv = 0; vignette = 0; edge_soft_px = 0; blur_intensity = 0; }
    const float ang = scanAngleDeg * (float)(M_PI / 180.0);
    float blur_r = 0;
    if (blur_intensity > 0) {
        const float minDim = (float)std::min(w, h);
        const float baseRadius = std::max(1.0f, minDim / 480.0f);
        blur_r = blur_intensity * 3.0f * baseRadius;
    }
    GLuint sampled = src;
    if (halfRes && gBlit.run(pn.halfRes, std::max(1, w / 2), std::max(1, h / 2), src, GL_NEAREST)) sampled = pn.halfRes.tex;
    if (!pn.effect.ensure(w, h)) return 0;
    glBindFramebuffer(GL_FRAMEBUFFER, pn.effect.fbo);
    glViewport(0, 0, w, h);
    glUseProgram(gCrt.prog);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, sampled);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    gCrt.seti("src", 0);
    gCrt.set("fb_size", (float)w, (float)h);
    gCrt.set("scan_strength", scan_strength);
    gCrt.set("scan_dark", 1.0f - scan_strength);
    gCrt.set("triad_px", triad_px);
    gCrt.set("mask_strength", mask_strength);
    gCrt.set("scan_phase", scan_phase);
    gCrt.set("scan_ca", cosf(ang));
    gCrt.set("scan_sa", sinf(ang));
    gCrt.set("inv_period", 1.0f / std::max(1.0f, scan_px));
    gCrt.set("curv", curv);
    gCrt.set("vignette", vignette);
    gCrt.set("edge_soft_px", edge_soft_px);
    gCrt.set("blur_r", blur_r);
    drawFull();
    return pn.effect.tex;
}

GLuint runLcd3x(Panel& pn, GLuint src, int w, int h) {
    if (!gLcd3x.ensure(std::string(kCommonFs) + kLcd3xFs)) return 0;
    const bool halfRes = getBoolProp("persist.gammaos.shader.lcd3x.half_res", false);
    const float bs = std::max(1.0f, getFloatProp("persist.gammaos.shader.lcd3x.brighten_scanlines", "4.0"));
    const float bl = std::max(1.0f, getFloatProp("persist.gammaos.shader.lcd3x.brighten_lcd", "4.0"));
    const float gx = std::max(1.0f, getFloatProp("persist.gammaos.shader.lcd3x.grid_px_x", "4.0"));
    const float gy = std::max(1.0f, getFloatProp("persist.gammaos.shader.lcd3x.grid_px_y", "4.0"));
    GLuint sampled = src;
    if (halfRes && gBlit.run(pn.halfRes, std::max(1, w / 2), std::max(1, h / 2), src, GL_NEAREST)) sampled = pn.halfRes.tex;
    if (!pn.effect.ensure(w, h)) return 0;
    glBindFramebuffer(GL_FRAMEBUFFER, pn.effect.fbo);
    glViewport(0, 0, w, h);
    glUseProgram(gLcd3x.prog);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, sampled);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    gLcd3x.seti("src", 0);
    gLcd3x.set("fb_size", (float)w, (float)h);
    gLcd3x.set("brighten_scanlines", bs);
    gLcd3x.set("brighten_lcd", bl);
    gLcd3x.set("grid_px", gx, gy);
    drawFull();
    return pn.effect.tex;
}

GLuint runLcd(Panel& pn, GLuint src, int w, int h) {
    if (!gLcd.ensure(std::string(kCommonFs) + kLcdFs)) return 0;
    const float responseTime = clampf(getFloatProp("persist.gammaos.shader.lcd.response_time", "0"), 0, 0.78f);
    const float scanStrength = clampf(getFloatProp("persist.gammaos.shader.lcd.scan_strength", "0.20"), 0, 1);
    const float subpixelStrength = clampf(getFloatProp("persist.gammaos.shader.lcd.subpixel_strength", "0.40"), 0, 1);
    const float gapStrength = clampf(getFloatProp("persist.gammaos.shader.lcd.gap_strength", "0.10"), 0, 1);
    const float gapPx = clampf(getFloatProp("persist.gammaos.shader.lcd.gap_px", "0.05"), 0, 0.45f);
    const float scanPhase = clampf(getFloatProp("persist.gammaos.shader.lcd.scan_phase", "0"), 0, 1);
    const bool halfRes = getBoolProp("persist.gammaos.shader.lcd.half_res", false);
    if (getBoolProp("persist.gammaos.shader.lcd.reset_history", false)) pn.havePrev = false;
    GLuint sampled = src;
    if (halfRes && gBlit.run(pn.halfRes, std::max(1, w / 2), std::max(1, h / 2), src, GL_NEAREST)) sampled = pn.halfRes.tex;
    const bool usePrev = responseTime > 0.0001f && pn.havePrev && pn.prevSrc.tex && pn.prevSrc.w == w && pn.prevSrc.h == h;
    if (!pn.effect.ensure(w, h)) return 0;
    glBindFramebuffer(GL_FRAMEBUFFER, pn.effect.fbo);
    glViewport(0, 0, w, h);
    glUseProgram(gLcd.prog);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, sampled);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    gLcd.seti("src", 0);
    if (usePrev) {
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, pn.prevSrc.tex);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glActiveTexture(GL_TEXTURE0);
    }
    gLcd.seti("prev", usePrev ? 1 : 0);
    gLcd.set("use_prev", usePrev ? 1.0f : 0.0f);
    gLcd.set("fb_size", (float)w, (float)h);
    gLcd.set("response_time", responseTime);
    gLcd.set("scan_strength", scanStrength);
    gLcd.set("subpixel_strength", subpixelStrength);
    gLcd.set("gap_strength", gapStrength);
    gLcd.set("gap_px", gapPx);
    gLcd.set("scan_phase", scanPhase);
    drawFull();
    // this frame's source, for next frame's response-time blend
    if (gBlit.run(pn.prevSrc, w, h, src, GL_NEAREST)) pn.havePrev = true;
    return pn.effect.tex;
}

GLuint runBlurFill(Panel& pn, GLuint src, int w, int h) {
    if (!gBlurFill.ensure(std::string(kCommonFs) + kBlurFillFs) || !gGauss.ensure(kGaussFs)) return 0;
    const float sigma = clampf(getFloatProp("persist.gammaos.shader.blurfill.sigma", "12.0"), 0.1f, 64.0f);
    const float blurScale = clampf(getFloatProp("persist.gammaos.shader.blurfill.blur_scale", "1.0"), 0.25f, 4.0f);
    const float sigmaEff = sigma * blurScale;
    const float edgePx = clampf(getFloatProp("persist.gammaos.shader.blurfill.edge_px", "160.0"), 1.0f, 4096.0f);
    const float featherPx = clampf(getFloatProp("persist.gammaos.shader.blurfill.feather_px", "40.0"), 0.0f, 1024.0f);
    const float strength = clampf(getFloatProp("persist.gammaos.shader.blurfill.strength", "1.0"), 0, 1);
    const float resScale = clampf(getFloatProp("persist.gammaos.shader.blurfill.res_scale", "0.5"), 0.1f, 1.0f);
    const float lumaThreshold = clampf(getFloatProp("persist.gammaos.shader.blurfill.luma_threshold", "0.06"), 0, 0.3f);
    const std::string orientStr = getProp("persist.gammaos.shader.blurfill.orientation", "auto");
    float orientation = 0;
    if (orientStr == "vertical" || orientStr == "pillar" || orientStr == "v") orientation = 1;
    else if (orientStr == "horizontal" || orientStr == "letterbox" || orientStr == "h") orientation = 2;
    if (strength <= 0 || edgePx <= 0) return 0;
    int blurW = std::max(1, (int)lroundf(w * resScale)), blurH = std::max(1, (int)lroundf(h * resScale));
    blurW = std::min(blurW, w); blurH = std::min(blurH, h);
    // the downscaled copy, blurred along X then Y (a Gaussian of sigmaEff buffer pixels)
    if (!gBlit.run(pn.blurA, blurW, blurH, src, GL_LINEAR)) return 0;
    const int radius = std::min(96, std::max(1, (int)ceilf(3.0f * sigmaEff)));
    auto gauss = [&](Target& dst, GLuint from, float sx, float sy) {
        if (!dst.ensure(blurW, blurH)) return false;
        glBindFramebuffer(GL_FRAMEBUFFER, dst.fbo);
        glViewport(0, 0, blurW, blurH);
        glUseProgram(gGauss.prog);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, from);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        gGauss.seti("uTex", 0);
        gGauss.set("uStep", sx, sy);
        gGauss.set("uSigma", sigmaEff);
        gGauss.seti("uRadius", radius);
        drawFull();
        return true;
    };
    if (!gauss(pn.blurB, pn.blurA.tex, 1.0f / blurW, 0) || !gauss(pn.blurA, pn.blurB.tex, 0, 1.0f / blurH)) return 0;
    if (!pn.effect.ensure(w, h)) return 0;
    glBindFramebuffer(GL_FRAMEBUFFER, pn.effect.fbo);
    glViewport(0, 0, w, h);
    glUseProgram(gBlurFill.prog);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, src);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, pn.blurA.tex);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glActiveTexture(GL_TEXTURE0);
    gBlurFill.seti("src", 0);
    gBlurFill.seti("blur", 1);
    gBlurFill.set("fb_size", (float)w, (float)h);
    gBlurFill.set("blur_wh", (float)blurW, (float)blurH);
    gBlurFill.set("edge_px", edgePx);
    gBlurFill.set("feather_px", featherPx);
    gBlurFill.set("strength", strength);
    gBlurFill.set("orientation", orientation);
    gBlurFill.set("luma_threshold", lumaThreshold);
    drawFull();
    return pn.effect.tex;
}

// ---- presets -------------------------------------------------------------------------------

GLuint runPreset(Panel& pn, GLuint src, int w, int h, int orientIdx) {
    const std::string presetPath = normalizeStoragePath(gS.preset);
    if (presetPath.empty()) return 0;
    if (presetPath == pn.presetFailed) return 0;
    const bool wantSlang = gS.type != "custom-gl";
    if (wantSlang != endsWith(presetPath, ".slangp") && !(wantSlang == false && endsWith(presetPath, ".glslp"))) {
        // the type and the file disagree (the menu keeps them together; a stray property does not)
        if (!(endsWith(presetPath, ".slangp") || endsWith(presetPath, ".glslp"))) return 0;
    }
    if (presetPath != pn.presetLoaded) {
        pn.presetFailed.clear();
        pn.initRetries = 0;
        pn.chain.release();
        if (!parsePreset(presetPath, pn.preset)) { setStatus("parse_fail"); pn.presetFailed = presetPath; return 0; }
        pn.presetLoaded = presetPath;
        gParamFile.known = false;
        writeParamMeta(pn.preset);
    }
    if (pn.preset.passes.empty()) return 0;

    // logical (upright) space for the chain
    const bool swap = kDihedral[orientIdx].swap;
    const int viewW = swap ? h : w, viewH = swap ? w : h;
    GLuint chainSrc = src;
    int baseW = w, baseH = h;
    if (orientIdx != 0 && gOrient.run(pn.orientPre, src, orientIdx, viewW, viewH)) {
        chainSrc = pn.orientPre.tex;
        baseW = viewW; baseH = viewH;
    } else {
        orientIdx = 0;
    }
    // base resolution: the composite downscaled to a low source before the chain
    const float resScale = parseResScale(gS.resScale);
    const bool isAuto = gS.resScale.empty() || gS.resScale == "auto";
    int targetH = baseH;
    const bool lowRes = isLowResPreset(gS.type, gS.preset);
    if (isAuto) {
        if (lowRes) {
            const float frac = baseH <= 480 ? 0.5f : baseH >= 1080 ? 0.25f : 0.5f + (float)(baseH - 480) * (0.25f - 0.5f) / (1080.0f - 480.0f);
            targetH = std::min(384, std::max(1, (int)(baseH * frac + 0.5f)));
        }
    } else if (resScale < 1.0f) {
        targetH = std::max(1, (int)(baseH * resScale + 0.5f));
    }
    int chainSrcW = baseW, chainSrcH = baseH;
    if (targetH < baseH) {
        const int targetW = std::max(1, (int)((double)targetH * baseW / baseH + 0.5));
        if (gBlit.run(pn.downscale, targetW, targetH, chainSrc, GL_LINEAR)) {
            chainSrc = pn.downscale.tex;
            chainSrcW = targetW; chainSrcH = targetH;
        }
    }
    // the advertised effect resolution (scanline density) over the sampled one
    int advW = chainSrcW, advH = chainSrcH;
    if (lowRes) {
        const int effH = computeEffectHeight(gS.density, viewH);
        if (effH > 0 && effH != chainSrcH) {
            advH = effH;
            advW = std::max(1, (int)((double)effH * chainSrcW / chainSrcH + 0.5));
        }
    }
    if (!pn.chain.valid || pn.chain.presetPath != presetPath || pn.chain.viewW != viewW || pn.chain.viewH != viewH) {
        if (!initChain(pn.chain, pn.preset, viewW, viewH)) {
            if (++pn.initRetries >= 3) {
                ALOGE("gshader: chain for '%s' failed %d times, giving up", presetPath.c_str(), pn.initRetries);
                pn.presetFailed = presetPath;
            }
            return 0;
        }
        pn.initRetries = 0;
    }
    applyParamOverrides(pn.preset);
    if (!renderChain(pn.chain, pn.preset, chainSrc, chainSrcW, chainSrcH, advW, advH)) return 0;
    GLuint out = pn.chain.passes.back().out.tex;
    const int outW = pn.chain.passes.back().w, outH = pn.chain.passes.back().h;
    if (orientIdx != 0) {
        if (!gOrient.run(pn.orientPost, out, kDihedralInv[orientIdx], w, h)) return 0;
        out = pn.orientPost.tex;
    } else if (outW != w || outH != h) {
        if (!gBlit.run(pn.effect, w, h, out, GL_LINEAR)) return 0;
        out = pn.effect.tex;
    }
    return out;
}

}  // namespace

// ============================================================================ public

bool poll() {
    bool reread = gWEnable.changed();
    reread |= gWType.changed();
    reread |= gWPreset.changed();
    const int64_t t = nowMs();
    if (reread || t - gLastFullReadMs > 500) {
        gLastFullReadMs = t;
        readSettings();
    }
    const bool active = gS.on && typeKnown(gS.type) && (!typeIsPreset(gS.type) || !gS.preset.empty());
    if (!active) {
        static bool sWasActive = false;
        if (sWasActive) {
            for (Panel& p : gPanel) { p.chain.release(); p.presetLoaded.clear(); p.havePrev = false; }
            sWasActive = false;
        }
        return false;
    }
    static bool sLogged = false;
    if (!sLogged) { sLogged = true; ALOGI("gshader: global shader %s active on the DRM home", gS.type.c_str()); }
    return true;
}

GLuint apply(int panel, GLuint src, int w, int h, int installDeg) {
    if (panel < 0 || panel > 1 || !src || w < 8 || h < 8) return 0;
    Panel& pn = gPanel[panel];
    GLint prevFbo = 0, prevProg = 0, prevVp[4] = {0, 0, 0, 0}, prevActive = 0, prevTex = 0, prevArray = 0;
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
    glGetIntegerv(GL_CURRENT_PROGRAM, &prevProg);
    glGetIntegerv(GL_VIEWPORT, prevVp);
    glGetIntegerv(GL_ACTIVE_TEXTURE, &prevActive);
    glActiveTexture(GL_TEXTURE0);
    glGetIntegerv(GL_TEXTURE_BINDING_2D, &prevTex);
    glGetIntegerv(GL_ARRAY_BUFFER_BINDING, &prevArray);
    const GLboolean blend = glIsEnabled(GL_BLEND), scissor = glIsEnabled(GL_SCISSOR_TEST);
    const GLboolean depth = glIsEnabled(GL_DEPTH_TEST), cull = glIsEnabled(GL_CULL_FACE);
    plainState();

    GLuint out = 0;
    // scanline effects follow the panel's install rotation; a rotated panel swaps the axes
    int orientIdx = resolveOrientIndex(gS.orient, installDeg);
    if (kDihedral[orientIdx].swap && w >= h) orientIdx = 0;   // an upright landscape target needs no turn
    const float scanAngleDeg = kDihedral[orientIdx].swap ? 90.0f : 0.0f;
    if (gS.testOverlay) {
        out = 0;   // the debug overlay is a compositor-side check; the home shows the plain frame
    } else if (gS.type == "crt-simple") {
        out = runCrt(pn, src, w, h, scanAngleDeg);
    } else if (gS.type == "lcd3x") {
        out = runLcd3x(pn, src, w, h);
    } else if (gS.type == "lcd-shader") {
        out = runLcd(pn, src, w, h);
    } else if (gS.type == "blur-fill") {
        out = runBlurFill(pn, src, w, h);
    } else if (typeIsPreset(gS.type)) {
        out = runPreset(pn, src, w, h, orientIdx);
    }

    glBindBuffer(GL_ARRAY_BUFFER, (GLuint)prevArray);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, (GLuint)prevTex);
    glActiveTexture((GLenum)prevActive);
    glUseProgram((GLuint)prevProg);
    if (blend) glEnable(GL_BLEND);
    if (scissor) glEnable(GL_SCISSOR_TEST);
    if (depth) glEnable(GL_DEPTH_TEST);
    if (cull) glEnable(GL_CULL_FACE);
    glViewport(prevVp[0], prevVp[1], prevVp[2], prevVp[3]);
    glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prevFbo);
    return out;
}

void releaseGl() {
    for (Panel& p : gPanel) p.release();
    gCrt.release(); gLcd3x.release(); gLcd.release(); gBlurFill.release(); gGauss.release();
    gBlit.release(); gOrient.release();
    if (gTransparentLut) glDeleteTextures(1, &gTransparentLut);
    gTransparentLut = 0;
    gCompileCache.clear();
}

}  // namespace gshader
}  // namespace android
