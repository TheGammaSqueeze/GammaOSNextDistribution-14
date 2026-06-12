/*
 * Copyright (C) 2026 GammaOS
 */

#define LOG_TAG "DrasticNano.Gfx"

#include "OverlayGfx.h"

#include <ft2build.h>
#include FT_FREETYPE_H

#include <string.h>
#include <stdlib.h>

#include <utils/Log.h>

namespace android {
namespace drastic_gfx {

namespace {

// Column-major 2x2 rotation matrix layout so we can plug sDrmRotMat
// directly. The shader samples gl_Position via:
//   p.xy = mat2(uRot) * p.xy
// after we convert from pixel space to NDC.
//
// Vertex shader for solid-colored geometry. Accepts NDC-space vertices
// (we precompute NDC on the CPU so the shader is trivial).
const char kSolidVS[] =
    "precision highp float;\n"
    "attribute vec2 aPos;\n"
    "uniform vec2 uViewport;\n"
    "uniform mat2 uRot;\n"
    "void main() {\n"
    "  vec2 ndc = aPos / uViewport * 2.0 - 1.0;\n"
    "  ndc.y = -ndc.y;\n"
    "  gl_Position = vec4(uRot * ndc, 0.0, 1.0);\n"
    "}\n";
const char kSolidFS[] =
    "precision mediump float;\n"
    "uniform vec4 uColor;\n"
    "void main() {\n"
    "  gl_FragColor = uColor;\n"
    "}\n";

// Text vertex shader: per-vertex UV.
const char kTextVS[] =
    "precision highp float;\n"
    "attribute vec2 aPos;\n"
    "attribute vec2 aUv;\n"
    "varying vec2 vUv;\n"
    "uniform vec2 uViewport;\n"
    "uniform mat2 uRot;\n"
    "void main() {\n"
    "  vUv = aUv;\n"
    "  vec2 ndc = aPos / uViewport * 2.0 - 1.0;\n"
    "  ndc.y = -ndc.y;\n"
    "  gl_Position = vec4(uRot * ndc, 0.0, 1.0);\n"
    "}\n";
const char kTextFS[] =
    "precision mediump float;\n"
    "varying vec2 vUv;\n"
    "uniform sampler2D uTex;\n"
    "uniform vec4 uColor;\n"
    "void main() {\n"
    "  float a = texture2D(uTex, vUv).r;\n"
    "  gl_FragColor = vec4(uColor.rgb, uColor.a * a);\n"
    "}\n";

// Rounded-rect SDF, transcribed 1:1 from gammaos-nano's ROUND_FRAGMENT_SHADER
// so the OSK caps/panel match the PS3-XMB keyboard. aLocal is the centred
// pixel offset within the rect (corners at +/- half).
const char kRoundVS[] =
    "precision highp float;\n"
    "attribute vec2 aPos;\n"
    "attribute vec2 aLocal;\n"
    "varying vec2 vLocal;\n"
    "uniform vec2 uViewport;\n"
    "uniform mat2 uRot;\n"
    "void main() {\n"
    "  vLocal = aLocal;\n"
    "  vec2 ndc = aPos / uViewport * 2.0 - 1.0;\n"
    "  ndc.y = -ndc.y;\n"
    "  gl_Position = vec4(uRot * ndc, 0.0, 1.0);\n"
    "}\n";
const char kRoundFS[] =
    "precision mediump float;\n"
    "varying vec2 vLocal;\n"
    "uniform vec2 uHalf;\n"
    "uniform float uRadius;\n"
    "uniform vec4 uColor;\n"
    "void main() {\n"
    "  vec2 d = abs(vLocal) - (uHalf - vec2(uRadius));\n"
    "  float dist = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - uRadius;\n"
    "  float a = clamp(0.5 - dist, 0.0, 1.0);\n"
    "  gl_FragColor = vec4(uColor.rgb, uColor.a * a);\n"
    "}\n";

GLuint compile(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[1024];
        glGetShaderInfoLog(s, sizeof(log), nullptr, log);
        ALOGE("OverlayGfx: shader compile failed: %s", log);
        glDeleteShader(s);
        return 0;
    }
    return s;
}

GLuint link(GLuint vs, GLuint fs) {
    GLuint p = glCreateProgram();
    glAttachShader(p, vs);
    glAttachShader(p, fs);
    glBindAttribLocation(p, 0, "aPos");
    glBindAttribLocation(p, 1, "aUv");
    glBindAttribLocation(p, 1, "aLocal");   // round program reuses slot 1
    glLinkProgram(p);
    GLint ok = 0;
    glGetProgramiv(p, GL_LINK_STATUS, &ok);
    if (!ok) {
        char log[1024];
        glGetProgramInfoLog(p, sizeof(log), nullptr, log);
        ALOGE("OverlayGfx: link failed: %s", log);
        glDeleteProgram(p);
        return 0;
    }
    return p;
}

} // anonymous namespace

OverlayGfx::OverlayGfx() {}
OverlayGfx::~OverlayGfx() { shutdown(); }

bool OverlayGfx::init(int viewportW, int viewportH, const float rotMat[4]) {
    mViewportW = viewportW;
    mViewportH = viewportH;
    for (int i = 0; i < 4; i++) mRot[i] = rotMat[i];

    GLuint svs = compile(GL_VERTEX_SHADER,   kSolidVS);
    GLuint sfs = compile(GL_FRAGMENT_SHADER, kSolidFS);
    if (!svs || !sfs) return false;
    mSolidProgram = link(svs, sfs);
    glDeleteShader(svs);
    glDeleteShader(sfs);
    if (!mSolidProgram) return false;

    GLuint tvs = compile(GL_VERTEX_SHADER,   kTextVS);
    GLuint tfs = compile(GL_FRAGMENT_SHADER, kTextFS);
    if (!tvs || !tfs) return false;
    mTextProgram = link(tvs, tfs);
    glDeleteShader(tvs);
    glDeleteShader(tfs);
    if (!mTextProgram) return false;

    mSolidLocPos      = glGetAttribLocation(mSolidProgram, "aPos");
    mSolidLocColor    = glGetUniformLocation(mSolidProgram, "uColor");
    mSolidLocViewport = glGetUniformLocation(mSolidProgram, "uViewport");
    mSolidLocRot      = glGetUniformLocation(mSolidProgram, "uRot");

    mTextLocPos      = glGetAttribLocation(mTextProgram, "aPos");
    mTextLocUv       = glGetAttribLocation(mTextProgram, "aUv");
    mTextLocColor    = glGetUniformLocation(mTextProgram, "uColor");
    mTextLocViewport = glGetUniformLocation(mTextProgram, "uViewport");
    mTextLocRot      = glGetUniformLocation(mTextProgram, "uRot");
    mTextLocSampler  = glGetUniformLocation(mTextProgram, "uTex");

    GLuint rvs = compile(GL_VERTEX_SHADER,   kRoundVS);
    GLuint rfs = compile(GL_FRAGMENT_SHADER, kRoundFS);
    if (!rvs || !rfs) return false;
    mRoundProgram = link(rvs, rfs);
    glDeleteShader(rvs);
    glDeleteShader(rfs);
    if (!mRoundProgram) return false;
    mRoundLocPos      = glGetAttribLocation(mRoundProgram, "aPos");
    mRoundLocLocal    = glGetAttribLocation(mRoundProgram, "aLocal");
    mRoundLocColor    = glGetUniformLocation(mRoundProgram, "uColor");
    mRoundLocViewport = glGetUniformLocation(mRoundProgram, "uViewport");
    mRoundLocRot      = glGetUniformLocation(mRoundProgram, "uRot");
    mRoundLocHalf     = glGetUniformLocation(mRoundProgram, "uHalf");
    mRoundLocRadius   = glGetUniformLocation(mRoundProgram, "uRadius");

    glGenBuffers(1, &mQuadVbo);
    glGenBuffers(1, &mTextVbo);

    // FreeType setup.
    FT_Library lib;
    if (FT_Init_FreeType(&lib) != 0) {
        ALOGE("OverlayGfx: FT_Init_FreeType failed");
        return false;
    }
    mFtLibrary = lib;
    const char* paths[] = {
        "/system/fonts/Roboto-Regular.ttf",
        "/system/fonts/DroidSans.ttf",
        nullptr,
    };
    FT_Face face = nullptr;
    for (int i = 0; paths[i]; i++) {
        if (FT_New_Face(lib, paths[i], 0, &face) == 0) {
            ALOGI("OverlayGfx: font loaded from %s", paths[i]);
            break;
        }
        face = nullptr;
    }
    if (!face) {
        ALOGE("OverlayGfx: no system font found");
        return false;
    }
    // Scale font with viewport height so tiny 480-tall panels still
    // fit readable text. Target ~22 lines of text on a 480-tall panel.
    mFontPx = viewportH / 22;
    if (mFontPx < 12) mFontPx = 12;
    if (mFontPx > 32) mFontPx = 32;
    FT_Set_Pixel_Sizes(face, 0, mFontPx);
    mFtFace = face;
    mAscent = face->size->metrics.ascender / 64;
    mLineH  = face->size->metrics.height / 64;
    ALOGI("OverlayGfx: font %dpx ascent=%d lineH=%d viewport %dx%d",
          mFontPx, mAscent, mLineH, viewportW, viewportH);
    return true;
}

void OverlayGfx::shutdown() {
    for (auto& kv : mGlyphs) {
        if (kv.second.tex) glDeleteTextures(1, &kv.second.tex);
    }
    mGlyphs.clear();
    if (mQuadVbo) { glDeleteBuffers(1, &mQuadVbo); mQuadVbo = 0; }
    if (mTextVbo) { glDeleteBuffers(1, &mTextVbo); mTextVbo = 0; }
    if (mSolidProgram) { glDeleteProgram(mSolidProgram); mSolidProgram = 0; }
    if (mTextProgram)  { glDeleteProgram(mTextProgram);  mTextProgram = 0; }
    if (mRoundProgram) { glDeleteProgram(mRoundProgram); mRoundProgram = 0; }
    if (mFtFace) {
        FT_Done_Face((FT_Face)mFtFace);
        mFtFace = nullptr;
    }
    if (mFtLibrary) {
        FT_Done_FreeType((FT_Library)mFtLibrary);
        mFtLibrary = nullptr;
    }
}

void OverlayGfx::setRotationMatrix(const float rot[4]) {
    for (int i = 0; i < 4; i++) mRot[i] = rot[i];
}

void OverlayGfx::beginFrame() {
    // Save drastic's current program so endFrame can restore it.
    GLint cur = 0;
    glGetIntegerv(GL_CURRENT_PROGRAM, &cur);
    mDrasticProgram = (GLuint)cur;
    glDisable(GL_DEPTH_TEST);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
}

void OverlayGfx::endFrame() {
    if (mDrasticProgram) glUseProgram(mDrasticProgram);
    glDisable(GL_BLEND);
}

void OverlayGfx::drawSolidQuad(float x, float y, float w, float h, Color c) {
    glUseProgram(mSolidProgram);
    glUniform2f(mSolidLocViewport, (float)mViewportW, (float)mViewportH);
    glUniformMatrix2fv(mSolidLocRot, 1, GL_FALSE, mRot);
    glUniform4f(mSolidLocColor, c.r, c.g, c.b, c.a);

    const float verts[] = {
        x,     y,
        x + w, y,
        x,     y + h,
        x + w, y + h,
    };
    glBindBuffer(GL_ARRAY_BUFFER, mQuadVbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(verts), verts, GL_STREAM_DRAW);
    glEnableVertexAttribArray(mSolidLocPos);
    glVertexAttribPointer(mSolidLocPos, 2, GL_FLOAT, GL_FALSE, 0, nullptr);

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glDisableVertexAttribArray(mSolidLocPos);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
}

void OverlayGfx::fillRect(float x, float y, float w, float h, Color c) {
    drawSolidQuad(x, y, w, h, c);
}

void OverlayGfx::outline(float x, float y, float w, float h, float px, Color c) {
    drawSolidQuad(x, y, w, px, c);               // top
    drawSolidQuad(x, y + h - px, w, px, c);      // bottom
    drawSolidQuad(x, y, px, h, c);               // left
    drawSolidQuad(x + w - px, y, px, h, c);      // right
}

void OverlayGfx::panel(float x, float y, float w, float h, Color bg, Color edge) {
    fillRect(x, y, w, h, bg);
    outline(x, y, w, h, 2.0f, edge);
}

void OverlayGfx::roundedRect(float x, float y, float w, float h,
                             float radius, Color c) {
    if (w <= 0.0f || h <= 0.0f) return;
    const float hw = w * 0.5f;
    const float hh = h * 0.5f;
    float r = radius;
    const float rmax = (hw < hh) ? hw : hh;
    if (r > rmax) r = rmax;
    if (r < 0.0f) r = 0.0f;

    glUseProgram(mRoundProgram);
    glUniform2f(mRoundLocViewport, (float)mViewportW, (float)mViewportH);
    glUniformMatrix2fv(mRoundLocRot, 1, GL_FALSE, mRot);
    glUniform4f(mRoundLocColor, c.r, c.g, c.b, c.a);
    glUniform2f(mRoundLocHalf, hw, hh);
    glUniform1f(mRoundLocRadius, r);

    // Interleaved: pos.xy (pixel), local.xy (centred). Corners +/- half.
    const float verts[] = {
        x,     y,     -hw, -hh,
        x + w, y,      hw, -hh,
        x,     y + h, -hw,  hh,
        x + w, y + h,  hw,  hh,
    };
    glBindBuffer(GL_ARRAY_BUFFER, mQuadVbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(verts), verts, GL_STREAM_DRAW);
    glEnableVertexAttribArray(mRoundLocPos);
    glVertexAttribPointer(mRoundLocPos, 2, GL_FLOAT, GL_FALSE,
                          4 * sizeof(float), nullptr);
    glEnableVertexAttribArray(mRoundLocLocal);
    glVertexAttribPointer(mRoundLocLocal, 2, GL_FLOAT, GL_FALSE,
                          4 * sizeof(float), (void*)(2 * sizeof(float)));
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glDisableVertexAttribArray(mRoundLocPos);
    glDisableVertexAttribArray(mRoundLocLocal);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
}

void OverlayGfx::triangle(float x0, float y0, float x1, float y1,
                          float x2, float y2, Color c) {
    glUseProgram(mSolidProgram);
    glUniform2f(mSolidLocViewport, (float)mViewportW, (float)mViewportH);
    glUniformMatrix2fv(mSolidLocRot, 1, GL_FALSE, mRot);
    glUniform4f(mSolidLocColor, c.r, c.g, c.b, c.a);

    const float verts[] = { x0, y0, x1, y1, x2, y2 };
    glBindBuffer(GL_ARRAY_BUFFER, mQuadVbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(verts), verts, GL_STREAM_DRAW);
    glEnableVertexAttribArray(mSolidLocPos);
    glVertexAttribPointer(mSolidLocPos, 2, GL_FLOAT, GL_FALSE, 0, nullptr);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glDisableVertexAttribArray(mSolidLocPos);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
}

bool OverlayGfx::loadGlyph(uint32_t cp, int pxSize, Glyph* out) const {
    if (!mFtFace) return false;
    FT_Face face = (FT_Face)mFtFace;
    if (pxSize < 4) pxSize = 4;
    if (pxSize > 256) pxSize = 256;
    FT_Set_Pixel_Sizes(face, 0, pxSize);   // rasterize at the display size
    if (FT_Load_Char(face, cp, FT_LOAD_RENDER) != 0) return false;
    FT_GlyphSlot g = face->glyph;
    out->w = g->bitmap.width;
    out->h = g->bitmap.rows;
    out->bearingX = g->bitmap_left;
    out->bearingY = g->bitmap_top;
    out->advance  = g->advance.x >> 6;
    if (out->w == 0 || out->h == 0) {
        out->tex = 0;
        return true;
    }
    GLuint tex;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE,
                 out->w, out->h, 0, GL_LUMINANCE, GL_UNSIGNED_BYTE,
                 g->bitmap.buffer);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    out->tex = tex;
    return true;
}

namespace {

// Decode a single UTF-8 codepoint starting at *p. Advances *p past
// the sequence. Returns the codepoint or 0xFFFD on error.
uint32_t decodeUtf8(const char** p, const char* end) {
    const unsigned char* s = (const unsigned char*)*p;
    if (s >= (const unsigned char*)end) return 0;
    uint32_t cp;
    int extra;
    if (s[0] < 0x80) { cp = s[0]; extra = 0; }
    else if ((s[0] & 0xE0) == 0xC0) { cp = s[0] & 0x1F; extra = 1; }
    else if ((s[0] & 0xF0) == 0xE0) { cp = s[0] & 0x0F; extra = 2; }
    else if ((s[0] & 0xF8) == 0xF0) { cp = s[0] & 0x07; extra = 3; }
    else { (*p)++; return 0xFFFD; }
    if (s + 1 + extra > (const unsigned char*)end) {
        *p = end;
        return 0xFFFD;
    }
    for (int i = 1; i <= extra; i++) {
        if ((s[i] & 0xC0) != 0x80) { (*p)++; return 0xFFFD; }
        cp = (cp << 6) | (s[i] & 0x3F);
    }
    *p = (const char*)(s + 1 + extra);
    return cp;
}

} // anonymous namespace

float OverlayGfx::text(const char* s, float x, float y, float scale, Color c) {
    if (!s || !*s || !mTextProgram) return 0.0f;
    glUseProgram(mTextProgram);
    glUniform2f(mTextLocViewport, (float)mViewportW, (float)mViewportH);
    glUniformMatrix2fv(mTextLocRot, 1, GL_FALSE, mRot);
    glUniform4f(mTextLocColor, c.r, c.g, c.b, c.a);
    glActiveTexture(GL_TEXTURE0);
    glUniform1i(mTextLocSampler, 0);

    int pxSize = (int)(mFontPx * scale + 0.5f);
    if (pxSize < 4) pxSize = 4;
    if (pxSize > 256) pxSize = 256;

    float penX = x;
    const float baseline = y + mAscent * scale;
    const char* p = s;
    const char* end = s + strlen(s);
    while (p < end) {
        uint32_t cp = decodeUtf8(&p, end);
        if (!cp) break;
        uint64_t key = ((uint64_t)cp << 20) | (uint32_t)pxSize;
        auto it = mGlyphs.find(key);
        if (it == mGlyphs.end()) {
            Glyph g{};
            if (!loadGlyph(cp, pxSize, &g)) continue;
            it = mGlyphs.emplace(key, g).first;
        }
        const Glyph& g = it->second;
        if (g.tex) {
            // Metrics are already at the display pixel size -> draw 1:1.
            float gx = penX + g.bearingX;
            float gy = baseline - g.bearingY;
            float gw = g.w;
            float gh = g.h;
            const float verts[] = {
                gx,      gy,      0.0f, 0.0f,
                gx + gw, gy,      1.0f, 0.0f,
                gx,      gy + gh, 0.0f, 1.0f,
                gx + gw, gy + gh, 1.0f, 1.0f,
            };
            glBindBuffer(GL_ARRAY_BUFFER, mTextVbo);
            glBufferData(GL_ARRAY_BUFFER, sizeof(verts), verts, GL_STREAM_DRAW);
            glEnableVertexAttribArray(mTextLocPos);
            glVertexAttribPointer(mTextLocPos, 2, GL_FLOAT, GL_FALSE,
                                  4 * sizeof(float), nullptr);
            glEnableVertexAttribArray(mTextLocUv);
            glVertexAttribPointer(mTextLocUv, 2, GL_FLOAT, GL_FALSE,
                                  4 * sizeof(float),
                                  (void*)(2 * sizeof(float)));
            glBindTexture(GL_TEXTURE_2D, g.tex);
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
            glDisableVertexAttribArray(mTextLocPos);
            glDisableVertexAttribArray(mTextLocUv);
        }
        penX += g.advance;
    }
    glBindTexture(GL_TEXTURE_2D, 0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    return penX - x;
}

float OverlayGfx::measure(const char* s, float scale) const {
    if (!s || !*s) return 0.0f;
    int pxSize = (int)(mFontPx * scale + 0.5f);
    if (pxSize < 4) pxSize = 4;
    if (pxSize > 256) pxSize = 256;
    float penX = 0.0f;
    const char* p = s;
    const char* end = s + strlen(s);
    while (p < end) {
        uint32_t cp = decodeUtf8(&p, end);
        if (!cp) break;
        uint64_t key = ((uint64_t)cp << 20) | (uint32_t)pxSize;
        auto it = mGlyphs.find(key);
        if (it != mGlyphs.end()) { penX += it->second.advance; continue; }
        // Not cached: ask FT for the advance at this size without rendering.
        FT_Face face = (FT_Face)mFtFace;
        if (!face) continue;
        FT_Set_Pixel_Sizes(face, 0, pxSize);
        if (FT_Load_Char(face, cp, FT_LOAD_DEFAULT) != 0) continue;
        penX += (face->glyph->advance.x >> 6);
    }
    return penX;
}

} // namespace drastic_gfx
} // namespace android
