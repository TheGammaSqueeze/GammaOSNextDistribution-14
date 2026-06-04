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

// GammaOS Nano PS3 XMB "Time Zone" 3D Earth globe (see NanoMenuPS3Globe.h).
// 1:1 port of the web app's tzglobe screen (index.html 10285-10391): a
// fullscreen-quad ray-marched sphere, an offscreen FBO render in logical
// orientation, and a DRM-rotation-aware composite with a fade alpha.

#include "NanoMenuPS3Globe.h"
#include "NanoMenuPS3.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <vector>

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <png.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#define LOG_TAG "GammaOSNano"
#include <utils/Log.h>

namespace android {
namespace ps3globe {

// ---------------------------------------------------------------------------
// shaders
// ---------------------------------------------------------------------------

// Fullscreen quad position passthrough (the globe shader uses gl_FragCoord, not
// a varying UV). Mirrors the web vertex shader (index.html 10302).
static const char* VS_GLOBE =
    "attribute vec2 p;\n"
    "void main(){ gl_Position = vec4(p, 0.0, 1.0); }\n";

// Ray-marched Earth. VERBATIM translation of the web fragment shader
// (index.html 10303-10337): per-pixel unit-sphere intersection from uCenter/uR/
// uRes, normal rotated by rotY(uLon)*rotX(-uLat), day/night/clouds equirect
// sampling, day-night terminator, fresnel atmosphere rim, and the space +
// atmosphere halo outside the disc.
static const char* FS_GLOBE =
    "precision highp float;\n"
    "uniform vec2 uRes; uniform vec2 uCenter; uniform float uR; uniform float uLon; uniform float uLat;\n"
    "uniform sampler2D uDay, uNight, uClouds;\n"
    "const float PI=3.14159265;\n"
    "mat3 rotY(float a){ float s=sin(a),c=cos(a); return mat3(c,0.,-s, 0.,1.,0., s,0.,c); }\n"
    "mat3 rotX(float a){ float s=sin(a),c=cos(a); return mat3(1.,0.,0., 0.,c,s, 0.,-s,c); }\n"
    "void main(){\n"
    "  vec2 fc=vec2(gl_FragCoord.x, uRes.y-gl_FragCoord.y);\n"
    "  vec2 d=(fc-uCenter)/uR;\n"
    "  float r2=dot(d,d);\n"
    "  vec3 L=normalize(vec3(-0.55,-0.32,0.77));\n"
    "  if(r2>1.0){\n"
    "     float g=smoothstep(1.30,1.0,sqrt(r2));\n"
    "     float lit=clamp(dot(normalize(vec3(d.x,-d.y,0.001)),vec3(-0.7,0.4,0.))*0.5+0.5,0.,1.);\n"
    "     vec3 space=vec3(0.012,0.022,0.05);\n"
    "     gl_FragColor=vec4(space + vec3(0.27,0.5,0.92)*g*0.6*lit, 1.0); return;\n"
    "  }\n"
    "  float z=sqrt(max(0.,1.0-r2));\n"
    "  vec3 N=vec3(d.x,-d.y,z);\n"
    "  vec3 G=rotY(uLon)*rotX(-uLat)*N;\n"
    "  float lat=asin(clamp(G.y,-1.,1.));\n"
    "  float lon=atan(G.x,G.z);\n"
    "  vec2 tuv=vec2(lon/(2.0*PI)+0.5, 0.5 - lat/PI);\n"
    "  vec3 day=texture2D(uDay,tuv).rgb;\n"
    "  vec3 night=texture2D(uNight,tuv).rgb;\n"
    "  float cloud=texture2D(uClouds,tuv).r;\n"
    "  float diff=dot(N,L);\n"
    "  float dayAmt=smoothstep(-0.18,0.22,diff);\n"
    "  day=mix(day, vec3(1.0), cloud*0.55*clamp(dayAmt+0.15,0.,1.));\n"
    "  vec3 col=mix(night*1.3, day*(0.32+0.9*clamp(diff,0.,1.)), dayAmt);\n"
    "  float fres=pow(1.0-z,3.0);\n"
    "  col += vec3(0.27,0.5,0.92)*fres*clamp(dayAmt+0.12,0.,1.)*0.9;\n"
    "  gl_FragColor=vec4(col,1.0);\n"
    "}\n";

// Composite the globe FBO to the panel. uRotation maps logical NDC to the
// physical panel (DRM GL rotation); uAlpha fades the whole globe in over the
// menu/wizard underneath (the web XMB->globe cross-fade, ctx2.globalAlpha=fa).
static const char* VS_COMP =
    "attribute vec2 aPos;\n"
    "attribute vec2 aUV;\n"
    "uniform mat2 uRotation;\n"
    "varying vec2 vUV;\n"
    "void main(){ vUV = aUV; gl_Position = vec4(uRotation * aPos, 0.0, 1.0); }\n";

static const char* FS_COMP =
    "precision mediump float;\n"
    "varying vec2 vUV;\n"
    "uniform sampler2D uTex;\n"
    "uniform float uAlpha;\n"
    "void main(){ gl_FragColor = vec4(texture2D(uTex, vUV).rgb, uAlpha); }\n";

// ---------------------------------------------------------------------------
// state
// ---------------------------------------------------------------------------

static bool   sTriedInit = false;
static bool   sReady = false;

static GLuint sGlobeProg = 0;
static GLint  sGPos = -1, sGRes = -1, sGCenter = -1, sGR = -1, sGLon = -1, sGLat = -1;
static GLint  sGDay = -1, sGNight = -1, sGClouds = -1;

static GLuint sCompProg = 0;
static GLint  sCPos = -1, sCUV = -1, sCTex = -1, sCRot = -1, sCAlpha = -1;

static GLuint sTexDay = 0, sTexNight = 0, sTexClouds = 0;

static GLuint sFbo = 0, sFboTex = 0;
static int    sFbW = 0, sFbH = 0;

// Eased rotation (radians), mirroring the web tzGlobe {lon,lat,targetLon,targetLat}.
static float  sLon = 0.0f, sLat = 0.0f;
static float  sTgtLon = 0.0f, sTgtLat = 0.0f;

// ---------------------------------------------------------------------------
// GL helpers (file-local, mirrors ps3bg)
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
        ALOGE("ps3globe: shader compile failed: %s", log);
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
        ALOGE("ps3globe: program link failed: %s", log);
        glDeleteProgram(p);
        return 0;
    }
    return p;
}

static bool resolveAsset(const char* sub, const char* file, char* out, size_t n) {
    snprintf(out, n, "/data/system/nano_xmb/%s/%s", sub, file);
    FILE* f = fopen(out, "rb");
    if (f) { fclose(f); return true; }
    snprintf(out, n, "/system/etc/nano_xmb/%s/%s", sub, file);
    f = fopen(out, "rb");
    if (f) { fclose(f); return true; }
    return false;
}

// Load an equirectangular earth map: power-of-two RGBA PNG with GL_REPEAT on S
// (longitude wraps at the antimeridian), CLAMP_TO_EDGE on T (poles), trilinear
// mipmaps. Matches the web globe texture params (index.html 10346-10349) plus
// mipmaps for the minified back hemisphere. Returns 0 on any failure; the caller
// keeps the 1x1 deep-blue fallback so the globe still renders.
static GLuint loadEarthMap(const char* file) {
    char path[256];
    if (!resolveAsset("globe", file, path, sizeof(path))) {
        ALOGE("ps3globe: earth map %s not found", file);
        return 0;
    }
    FILE* fp = fopen(path, "rb");
    if (!fp) return 0;
    png_structp png = png_create_read_struct(PNG_LIBPNG_VER_STRING, nullptr, nullptr, nullptr);
    if (!png) { fclose(fp); return 0; }
    png_infop info = png_create_info_struct(png);
    if (!info) { png_destroy_read_struct(&png, nullptr, nullptr); fclose(fp); return 0; }
    if (setjmp(png_jmpbuf(png))) { png_destroy_read_struct(&png, &info, nullptr); fclose(fp); return 0; }
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
    std::vector<unsigned char> pixels(rowbytes * (size_t)h);
    std::vector<png_bytep> rows((size_t)h);
    for (int y = 0; y < h; y++) rows[(size_t)y] = pixels.data() + (size_t)y * rowbytes;
    png_read_image(png, rows.data());
    png_destroy_read_struct(&png, &info, nullptr);
    fclose(fp);

    GLuint tex = 0;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels.data());
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glGenerateMipmap(GL_TEXTURE_2D);
    return tex;
}

// 1x1 deep-blue fallback matching the web default texel [8,16,36,255]
// (index.html 10345). REPEAT/CLAMP so the globe still binds a complete texture
// if a map asset is missing.
static GLuint makeFallbackTex() {
    static const unsigned char px[4] = {8, 16, 36, 255};
    GLuint tex = 0;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, px);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    return tex;
}

static bool ensureFbo(int w, int h) {
    if (w == sFbW && h == sFbH && sFbo && sFboTex) return true;
    if (!sFbo) glGenFramebuffers(1, &sFbo);
    if (!sFboTex) glGenTextures(1, &sFboTex);
    glBindTexture(GL_TEXTURE_2D, sFboTex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindFramebuffer(GL_FRAMEBUFFER, sFbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, sFboTex, 0);
    bool ok = glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
    if (ok) { sFbW = w; sFbH = h; }
    return ok;
}

// ---------------------------------------------------------------------------
// public API
// ---------------------------------------------------------------------------

bool init() {
    if (sReady) return true;
    if (!sTriedInit) {
        sTriedInit = true;
        sGlobeProg = linkProgram(VS_GLOBE, FS_GLOBE);
        sCompProg  = linkProgram(VS_COMP, FS_COMP);
        if (sGlobeProg) {
            sGPos    = glGetAttribLocation(sGlobeProg, "p");
            sGRes    = glGetUniformLocation(sGlobeProg, "uRes");
            sGCenter = glGetUniformLocation(sGlobeProg, "uCenter");
            sGR      = glGetUniformLocation(sGlobeProg, "uR");
            sGLon    = glGetUniformLocation(sGlobeProg, "uLon");
            sGLat    = glGetUniformLocation(sGlobeProg, "uLat");
            sGDay    = glGetUniformLocation(sGlobeProg, "uDay");
            sGNight  = glGetUniformLocation(sGlobeProg, "uNight");
            sGClouds = glGetUniformLocation(sGlobeProg, "uClouds");
        }
        if (sCompProg) {
            sCPos   = glGetAttribLocation(sCompProg, "aPos");
            sCUV    = glGetAttribLocation(sCompProg, "aUV");
            sCTex   = glGetUniformLocation(sCompProg, "uTex");
            sCRot   = glGetUniformLocation(sCompProg, "uRotation");
            sCAlpha = glGetUniformLocation(sCompProg, "uAlpha");
        }
    }
    // (Re)try the map loads until they succeed (assets may mount slightly after
    // first render on a cold boot). Keep the fallback for any that fail.
    if (!sTexDay)    { GLuint t = loadEarthMap("earth_day.png");    sTexDay    = t ? t : makeFallbackTex(); }
    if (!sTexNight)  { GLuint t = loadEarthMap("earth_night.png");  sTexNight  = t ? t : makeFallbackTex(); }
    if (!sTexClouds) { GLuint t = loadEarthMap("earth_clouds.png"); sTexClouds = t ? t : makeFallbackTex(); }

    sReady = (sGlobeProg != 0 && sCompProg != 0);
    return sReady;
}

void shutdown() {
    if (sGlobeProg) { glDeleteProgram(sGlobeProg); sGlobeProg = 0; }
    if (sCompProg)  { glDeleteProgram(sCompProg);  sCompProg = 0; }
    if (sTexDay)    { glDeleteTextures(1, &sTexDay);    sTexDay = 0; }
    if (sTexNight)  { glDeleteTextures(1, &sTexNight);  sTexNight = 0; }
    if (sTexClouds) { glDeleteTextures(1, &sTexClouds); sTexClouds = 0; }
    if (sFboTex)    { glDeleteTextures(1, &sFboTex);    sFboTex = 0; }
    if (sFbo)       { glDeleteFramebuffers(1, &sFbo);   sFbo = 0; }
    sFbW = sFbH = 0;
    sReady = false; sTriedInit = false;
}

bool ready() { return sReady; }

void snapTo(float lonRad, float latRad) {
    sLon = sTgtLon = lonRad;
    sLat = sTgtLat = latRad;
}

void setTarget(float lonRad, float latRad) {
    sTgtLon = lonRad;
    sTgtLat = latRad;
}

void tick(float dt) {
    // Shortest-path longitude (index.html 10385-10388).
    float dLon = sTgtLon - sLon;
    while (dLon > (float)M_PI)  dLon -= 2.0f * (float)M_PI;
    while (dLon < -(float)M_PI) dLon += 2.0f * (float)M_PI;
    // Frame-rate-independent equivalent of the web's per-frame *0.12 at 60fps.
    if (dt < 0.0f) dt = 0.0f; if (dt > 0.1f) dt = 0.1f;
    float k = 1.0f - powf(0.88f, dt * 60.0f);   // 0.12 at dt=1/60
    sLon += dLon * k;
    sLat += (sTgtLat - sLat) * k;
}

void render(int panelW, int panelH, const float rotMat2[4], float alpha) {
    if (!sReady && !init()) return;
    if (alpha <= 0.0f) return;

    const int fw = (int)(ps3::gFrameW + 0.5f);
    const int fh = (int)(ps3::gFrameH + 0.5f);
    if (fw < 2 || fh < 2) return;

    // Save the caller's framebuffer + viewport (the FBO pass rebinds both).
    GLint prevFbo = 0; glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
    GLint prevVp[4] = {0, 0, panelW, panelH}; glGetIntegerv(GL_VIEWPORT, prevVp);

    if (!ensureFbo(fw, fh)) return;

    // ---- pass 1: ray-march the globe into the frame-sized FBO (logical) ----
    glBindFramebuffer(GL_FRAMEBUFFER, sFbo);
    glViewport(0, 0, fw, fh);
    glDisable(GL_BLEND);
    glClearColor(0.012f, 0.022f, 0.05f, 1.0f);   // web renderGlobe clear (index.html 10367)
    glClear(GL_COLOR_BUFFER_BIT);
    glUseProgram(sGlobeProg);
    glUniform2f(sGRes, (float)fw, (float)fh);
    glUniform2f(sGCenter, 0.43f * (float)fw, 0.49f * (float)fh);   // index.html 10372
    glUniform1f(sGR, 0.50f * (float)fh);                            // index.html 10373
    glUniform1f(sGLon, sLon);
    glUniform1f(sGLat, sLat);
    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, sTexDay);    glUniform1i(sGDay, 0);
    glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, sTexNight);  glUniform1i(sGNight, 1);
    glActiveTexture(GL_TEXTURE2); glBindTexture(GL_TEXTURE_2D, sTexClouds); glUniform1i(sGClouds, 2);
    {
        static const GLfloat quad[] = { -1.f,-1.f,  1.f,-1.f,  -1.f,1.f,
                                        -1.f,1.f,   1.f,-1.f,   1.f,1.f };
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glVertexAttribPointer(sGPos, 2, GL_FLOAT, GL_FALSE, 2 * sizeof(GLfloat), quad);
        glEnableVertexAttribArray(sGPos);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glDisableVertexAttribArray(sGPos);
    }
    glActiveTexture(GL_TEXTURE0);

    // ---- pass 2: composite the FBO over the bound framebuffer ----
    // Covers ps3::gFrame* exactly (letterbox bars stay whatever is behind), with
    // uRotation mapping logical NDC to the panel and uAlpha cross-fading in. The
    // globe FBO is opaque, so at alpha=1 it fully covers the menu underneath.
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
    glUniformMatrix2fv(sCRot, 1, GL_FALSE, rm);
    glUniform1f(sCAlpha, alpha > 1.0f ? 1.0f : alpha);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, sFboTex);
    glUniform1i(sCTex, 0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glVertexAttribPointer(sCPos, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), cv);
    glEnableVertexAttribArray(sCPos);
    glVertexAttribPointer(sCUV, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), cv + 2);
    glEnableVertexAttribArray(sCUV);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glDisableVertexAttribArray(sCPos);
    glDisableVertexAttribArray(sCUV);
}

} // namespace ps3globe
} // namespace android
