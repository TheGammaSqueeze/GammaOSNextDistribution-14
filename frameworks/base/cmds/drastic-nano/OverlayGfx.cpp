/*
 * Copyright (C) 2026 GammaOS
 */

#define LOG_TAG "DrasticNano.Gfx"

#include "OverlayGfx.h"

#include <ft2build.h>
#include FT_FREETYPE_H

#include <string.h>
#include <stdlib.h>
#include <math.h>
#include <sys/system_properties.h>
#include <cutils/properties.h>

#include <utils/Log.h>
#include <utils/SystemClock.h>

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
// Solid geometry carries its colour per vertex so a whole frame of rects,
// outlines and triangles is one client-side array and one draw (see
// flushSolids); a uniform colour would force a draw per rect.
const char kSolidVS[] =
    "precision highp float;\n"
    "attribute vec2 aPos;\n"
    "attribute vec4 aColor;\n"
    "varying vec4 vColor;\n"
    "uniform vec2 uViewport;\n"
    "uniform mat2 uRot;\n"
    "void main() {\n"
    "  vColor = aColor;\n"
    "  vec2 ndc = aPos / uViewport * 2.0 - 1.0;\n"
    "  ndc.y = -ndc.y;\n"
    "  gl_Position = vec4(uRot * ndc, 0.0, 1.0);\n"
    "}\n";
const char kSolidFS[] =
    "precision mediump float;\n"
    "varying vec4 vColor;\n"
    "void main() {\n"
    "  gl_FragColor = vColor;\n"
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

// RGBA image (achievement badge): same per-vertex UV vertex shader as text,
// but samples a full-colour texture and modulates by an overall alpha. Badges
// are small bitmaps drawn much larger, so a plain bilinear upscale looks soft.
// This samples with "sharp bilinear": it keeps each texel's interior crisp and
// confines the linear blend to the one-output-pixel seam between texels, which
// is as sharp as linear filtering gets without the blockiness of nearest. It
// needs the texel grid (uTexSize) and the on-screen upscale (uScale = output
// pixels per texel). At uScale 1 (no upscale) the math reduces to plain
// bilinear, so a 1:1 or downscaled draw is unchanged.
const char kImageFS[] =
    "precision mediump float;\n"
    "varying vec2 vUv;\n"
    "uniform sampler2D uTex;\n"
    "uniform float uAlpha;\n"
    "uniform vec2 uTexSize;\n"
    "uniform vec2 uScale;\n"
    "void main() {\n"
    "  vec2 texel = vUv * uTexSize;\n"
    "  vec2 tFloor = floor(texel);\n"
    "  vec2 s = fract(texel);\n"
    "  vec2 region = 0.5 - 0.5 / uScale;\n"
    "  vec2 cd = s - 0.5;\n"
    "  vec2 f = (cd - clamp(cd, -region, region)) * uScale + 0.5;\n"
    "  vec2 uv = (tFloor + f) / uTexSize;\n"
    "  vec4 t = texture2D(uTex, uv);\n"
    "  gl_FragColor = vec4(t.rgb, t.a * uAlpha);\n"
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
    glBindAttribLocation(p, 1, "aColor");   // solid program: per-vertex colour in slot 1
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
    mSolidLocColor    = glGetAttribLocation(mSolidProgram, "aColor");
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

    GLuint ivs = compile(GL_VERTEX_SHADER,   kTextVS);   // shares aPos+aUv layout
    GLuint ifs = compile(GL_FRAGMENT_SHADER, kImageFS);
    if (!ivs || !ifs) return false;
    mImageProgram = link(ivs, ifs);
    glDeleteShader(ivs);
    glDeleteShader(ifs);
    if (!mImageProgram) return false;
    mImgLocPos      = glGetAttribLocation(mImageProgram, "aPos");
    mImgLocUv       = glGetAttribLocation(mImageProgram, "aUv");
    mImgLocViewport = glGetUniformLocation(mImageProgram, "uViewport");
    mImgLocRot      = glGetUniformLocation(mImageProgram, "uRot");
    mImgLocSampler  = glGetUniformLocation(mImageProgram, "uTex");
    mImgLocAlpha    = glGetUniformLocation(mImageProgram, "uAlpha");
    mImgLocTexSize  = glGetUniformLocation(mImageProgram, "uTexSize");
    mImgLocScale    = glGetUniformLocation(mImageProgram, "uScale");

    glGenBuffers(1, &mQuadVbo);
    glGenBuffers(1, &mTextVbo);

    // FreeType setup.
    FT_Library lib;
    if (FT_Init_FreeType(&lib) != 0) {
        ALOGE("OverlayGfx: FT_Init_FreeType failed");
        return false;
    }
    mFtLibrary = lib;
    // Load the same font chain gammaos-nano uses (NanoMenuRender.cpp initFonts):
    // a Rodin/theme primary for the authentic XMB Latin look, then the Noto
    // script-fallback faces so CJK/Arabic/Thai/Hebrew glyphs render instead of
    // tofu. Per-glyph, faceForCp() picks the first face that has the codepoint.
    mFtNumFaces = 0;
    auto tryLoad = [&](const char* path) {
        if (!path || !*path || mFtNumFaces >= kMaxFtFaces) return;
        FT_Face f = nullptr;
        if (FT_New_Face(lib, path, 0, &f) == 0) {
            mFtFaces[mFtNumFaces++] = f;
            ALOGI("OverlayGfx: font loaded from %s", path);
        }
    };
    // Theme override (same prop as the XMB), else the bundled PS3 Rodin.
    char fontProp[PROP_VALUE_MAX] = {0};
    __system_property_get("persist.gammaos.nano.font", fontProp);
    if (fontProp[0]) tryLoad(fontProp);
    if (mFtNumFaces == 0) {
        tryLoad("/data/system/nano_xmb/fonts/ps3-rodin-regular.ttf");
        if (mFtNumFaces == 0)
            tryLoad("/system/etc/nano_xmb/fonts/ps3-rodin-regular.ttf");
    }
    // Latin fallbacks + the Noto script faces (CJK/Arabic/Thai/Hebrew).
    tryLoad("/system/fonts/Roboto-Regular.ttf");
    tryLoad("/system/fonts/DroidSans.ttf");
    tryLoad("/system/fonts/NotoSansCJK-Regular.ttc");
    tryLoad("/system/fonts/NotoNaskhArabic-Regular.ttf");
    tryLoad("/system/fonts/NotoSansThai-Regular.ttf");
    tryLoad("/system/fonts/NotoSansHebrew-Regular.ttf");
    if (mFtNumFaces == 0) {
        ALOGE("OverlayGfx: no system font found");
        return false;
    }
    FT_Face face = (FT_Face)mFtFaces[0];   // primary drives metrics/base size
    // Scale font with viewport height so tiny 480-tall panels still
    // fit readable text. Target ~22 lines of text on a 480-tall panel.
    mFontPx = viewportH / 22;
    if (mFontPx < 12) mFontPx = 12;
    if (mFontPx > 32) mFontPx = 32;
    for (int i = 0; i < mFtNumFaces; i++)
        FT_Set_Pixel_Sizes((FT_Face)mFtFaces[i], 0, mFontPx);
    mAscent = face->size->metrics.ascender / 64;
    mLineH  = face->size->metrics.height / 64;
    ALOGI("OverlayGfx: font %dpx ascent=%d lineH=%d viewport %dx%d",
          mFontPx, mAscent, mLineH, viewportW, viewportH);
    return true;
}

void OverlayGfx::shutdown() {
    mGlyphs.clear();
    if (mAtlasTex) { glDeleteTextures(1, &mAtlasTex); mAtlasTex = 0; }
    mAtlasX = mAtlasY = mAtlasShelfH = 0;
    if (mQuadVbo) { glDeleteBuffers(1, &mQuadVbo); mQuadVbo = 0; }
    if (mTextVbo) { glDeleteBuffers(1, &mTextVbo); mTextVbo = 0; }
    if (mSolidProgram) { glDeleteProgram(mSolidProgram); mSolidProgram = 0; }
    if (mTextProgram)  { glDeleteProgram(mTextProgram);  mTextProgram = 0; }
    if (mRoundProgram) { glDeleteProgram(mRoundProgram); mRoundProgram = 0; }
    if (mImageProgram) { glDeleteProgram(mImageProgram); mImageProgram = 0; }
    for (int i = 0; i < mFtNumFaces; i++) {
        if (mFtFaces[i]) FT_Done_Face((FT_Face)mFtFaces[i]);
        mFtFaces[i] = nullptr;
    }
    mFtNumFaces = 0;
    if (mFtLibrary) {
        FT_Done_FreeType((FT_Library)mFtLibrary);
        mFtLibrary = nullptr;
    }
}

void OverlayGfx::setRotationMatrix(const float rot[4]) {
    flushSolids();   // queued geometry belongs to the previous matrix
    for (int i = 0; i < 4; i++) mRot[i] = rot[i];
}

void OverlayGfx::beginFrame() {
    // Save drastic's current program so endFrame can restore it.
    GLint cur = 0;
    glGetIntegerv(GL_CURRENT_PROGRAM, &cur);
    mDrasticProgram = (GLuint)cur;
    mCurProgram = 0;   // drastic owned the binding since our last frame
    glDisable(GL_DEPTH_TEST);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
}

void OverlayGfx::endFrame() {
    flushSolids();
    // Frame probe (diagnostic, off by default): sys.gammaos.drastic_nano.overlay_probe=1.
    static int sProbe = -1;
    if (sProbe < 0 || (mDbgFlushes & 63) == 0) sProbe = property_get_bool("sys.gammaos.drastic_nano.overlay_probe", false) ? 1 : 0;
    if (sProbe && (mDbgUploads || mDbgFlushNs > 2000000 || mDbgTextNs > 2000000)) {
        ALOGI("OverlayGfx frame: glyph uploads %d (%.2f ms), solid flushes %d (%.2f ms), text draws %d (%.2f ms)",
              mDbgUploads, mDbgUploadNs / 1e6, mDbgFlushes, mDbgFlushNs / 1e6, mDbgTexts, mDbgTextNs / 1e6);
    }
    mDbgUploads = mDbgFlushes = mDbgTexts = 0; mDbgUploadNs = mDbgFlushNs = mDbgTextNs = 0;
    if (mDrasticProgram) glUseProgram(mDrasticProgram);
    mCurProgram = 0;
    glDisable(GL_BLEND);
}

// Bind a program only when it is not already the one we bound last. The
// per-primitive viewport/rotation uniforms are re-uploaded on every real bind
// by the callers, so a skipped bind never leaves a stale matrix behind.
void OverlayGfx::useProgram(GLuint prog) {
    if (prog == mCurProgram) return;
    glUseProgram(prog);
    mCurProgram = prog;
}

// Solid geometry is accumulated per frame and drawn in one client-side array
// (no VBO upload per rect). The batch is flushed before anything that must
// paint over it (text, rounded rects, images), around scissor changes, and at
// endFrame, so painter order is preserved. Measured on the RG DS Plus: the
// per-rect glBufferData + draw scheme spent most of a 20 ms menu frame in the
// kernel's DMA cache maintenance for those tiny uploads.
void OverlayGfx::pushSolidVertex(float x, float y, Color c) {
    mSolidVerts.push_back(x); mSolidVerts.push_back(y);
    mSolidVerts.push_back(c.r); mSolidVerts.push_back(c.g);
    mSolidVerts.push_back(c.b); mSolidVerts.push_back(c.a);
}

void OverlayGfx::drawSolidQuad(float x, float y, float w, float h, Color c) {
    if (mRasterOnly) return;
    pushSolidVertex(x,     y,     c); pushSolidVertex(x + w, y,     c); pushSolidVertex(x,     y + h, c);
    pushSolidVertex(x + w, y,     c); pushSolidVertex(x + w, y + h, c); pushSolidVertex(x,     y + h, c);
}

void OverlayGfx::flushSolids() {
    if (mSolidVerts.empty()) return;
    const int64_t t0 = android::elapsedRealtimeNano();
    mDbgFlushes++;
    useProgram(mSolidProgram);
    glUniform2f(mSolidLocViewport, (float)mViewportW, (float)mViewportH);
    glUniformMatrix2fv(mSolidLocRot, 1, GL_FALSE, mRot);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glEnableVertexAttribArray(mSolidLocPos);
    glVertexAttribPointer(mSolidLocPos, 2, GL_FLOAT, GL_FALSE, 6 * sizeof(float), mSolidVerts.data());
    glEnableVertexAttribArray(mSolidLocColor);
    glVertexAttribPointer(mSolidLocColor, 4, GL_FLOAT, GL_FALSE, 6 * sizeof(float), mSolidVerts.data() + 2);
    glDrawArrays(GL_TRIANGLES, 0, (GLsizei)(mSolidVerts.size() / 6));
    glDisableVertexAttribArray(mSolidLocPos);
    glDisableVertexAttribArray(mSolidLocColor);
    mSolidVerts.clear();
    mDbgFlushNs += android::elapsedRealtimeNano() - t0;
}

void OverlayGfx::fillRect(float x, float y, float w, float h, Color c) {
    drawSolidQuad(x, y, w, h, c);
}

void OverlayGfx::clipBegin(float x, float y, float w, float h) {
    // The vertex shaders map overlay space (y down) to NDC and then apply
    // uRot, so the scissor box must go through the same transform: rotate
    // the rectangle's corners in NDC and take their bounding box in window
    // (bottom-left based) pixels.
    flushSolids();   // what was queued before the clip must not be clipped
    if (w <= 0.0f || h <= 0.0f) { glScissor(0, 0, 0, 0); glEnable(GL_SCISSOR_TEST); return; }
    const float vw = (float)mViewportW, vh = (float)mViewportH;
    float minX = 1e9f, minY = 1e9f, maxX = -1e9f, maxY = -1e9f;
    const float cx[4] = {x, x + w, x, x + w};
    const float cy[4] = {y, y, y + h, y + h};
    for (int i = 0; i < 4; i++) {
        float nx = cx[i] / vw * 2.0f - 1.0f;
        float ny = -(cy[i] / vh * 2.0f - 1.0f);
        // column-major mat2: out = M * v
        const float rx = mRot[0] * nx + mRot[2] * ny;
        const float ry = mRot[1] * nx + mRot[3] * ny;
        const float px = (rx + 1.0f) * 0.5f * vw;
        const float py = (ry + 1.0f) * 0.5f * vh;
        if (px < minX) minX = px; if (px > maxX) maxX = px;
        if (py < minY) minY = py; if (py > maxY) maxY = py;
    }
    int sx = (int)floorf(minX), sy = (int)floorf(minY);
    int ex = (int)ceilf(maxX), ey = (int)ceilf(maxY);
    if (sx < 0) sx = 0; if (sy < 0) sy = 0;
    if (ex > mViewportW) ex = mViewportW; if (ey > mViewportH) ey = mViewportH;
    glScissor(sx, sy, ex > sx ? ex - sx : 0, ey > sy ? ey - sy : 0);
    glEnable(GL_SCISSOR_TEST);
}

void OverlayGfx::clipEnd() {
    flushSolids();   // clipped geometry is drawn while the scissor is still on
    glDisable(GL_SCISSOR_TEST);
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
    if (w <= 0.0f || h <= 0.0f || mRasterOnly) return;
    flushSolids();
    const float hw = w * 0.5f;
    const float hh = h * 0.5f;
    float r = radius;
    const float rmax = (hw < hh) ? hw : hh;
    if (r > rmax) r = rmax;
    if (r < 0.0f) r = 0.0f;

    glUseProgram(mRoundProgram);
    mCurProgram = mRoundProgram;
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
    if (mRasterOnly) return;
    pushSolidVertex(x0, y0, c); pushSolidVertex(x1, y1, c); pushSolidVertex(x2, y2, c);
}

void OverlayGfx::star(float cx, float cy, float r, Color c) {
    if (r <= 0.0f) return;
    const float inner = r * 0.42f;
    float px[10], py[10];
    for (int i = 0; i < 10; i++) {
        // Start at the top point and alternate outer/inner radius.
        float ang = -1.5707963f + (float)i * 0.62831853f;   // -pi/2 + i*pi/5
        float rad = (i & 1) ? inner : r;
        px[i] = cx + cosf(ang) * rad;
        py[i] = cy + sinf(ang) * rad;
    }
    for (int i = 0; i < 10; i++) {
        int j = (i + 1) % 10;
        triangle(cx, cy, px[i], py[i], px[j], py[j], c);
    }
}

GLuint OverlayGfx::createImageTexture(const uint8_t* rgba, int w, int h) {
    if (!rgba || w <= 0 || h <= 0) return 0;
    GLuint tex = 0;
    glGenTextures(1, &tex);
    if (!tex) return 0;
    glBindTexture(GL_TEXTURE_2D, tex);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA,
                 GL_UNSIGNED_BYTE, rgba);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindTexture(GL_TEXTURE_2D, 0);
    mImgSizes[tex] = { w, h };   // remembered for the sharp-bilinear sampler
    return tex;
}

void OverlayGfx::drawImage(GLuint tex, float x, float y, float w, float h,
                           float alpha) {
    if (!tex || !mImageProgram || mRasterOnly) return;
    flushSolids();
    glUseProgram(mImageProgram);
    mCurProgram = mImageProgram;
    glUniform2f(mImgLocViewport, (float)mViewportW, (float)mViewportH);
    glUniformMatrix2fv(mImgLocRot, 1, GL_FALSE, mRot);
    glUniform1f(mImgLocAlpha, alpha);
    glActiveTexture(GL_TEXTURE0);
    glUniform1i(mImgLocSampler, 0);
    // Drive the sharp-bilinear sampler from the texture's native size and the
    // on-screen upscale. uScale stays >= 1 so a 1:1 or downscaled draw, or a
    // texture whose size we never recorded, falls back to plain bilinear (the
    // shader reduces to an identity sample at uScale 1).
    float texW = 1.0f, texH = 1.0f, scaleX = 1.0f, scaleY = 1.0f;
    auto szIt = mImgSizes.find(tex);
    if (szIt != mImgSizes.end()) {
        texW = (float)szIt->second.first;
        texH = (float)szIt->second.second;
        if (texW > 0.0f && w > texW) scaleX = w / texW;
        if (texH > 0.0f && h > texH) scaleY = h / texH;
    }
    glUniform2f(mImgLocTexSize, texW, texH);
    glUniform2f(mImgLocScale, scaleX, scaleY);

    const float verts[] = {
        x,     y,     0.0f, 0.0f,
        x + w, y,     1.0f, 0.0f,
        x,     y + h, 0.0f, 1.0f,
        x + w, y + h, 1.0f, 1.0f,
    };
    glBindBuffer(GL_ARRAY_BUFFER, mTextVbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(verts), verts, GL_STREAM_DRAW);
    glEnableVertexAttribArray(mImgLocPos);
    glVertexAttribPointer(mImgLocPos, 2, GL_FLOAT, GL_FALSE,
                          4 * sizeof(float), nullptr);
    glEnableVertexAttribArray(mImgLocUv);
    glVertexAttribPointer(mImgLocUv, 2, GL_FLOAT, GL_FALSE,
                          4 * sizeof(float), (void*)(2 * sizeof(float)));
    glBindTexture(GL_TEXTURE_2D, tex);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glDisableVertexAttribArray(mImgLocPos);
    glDisableVertexAttribArray(mImgLocUv);
    glBindTexture(GL_TEXTURE_2D, 0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
}

void OverlayGfx::destroyTexture(GLuint tex) {
    if (tex) glDeleteTextures(1, &tex);
    mImgSizes.erase(tex);
}

void* OverlayGfx::faceForCp(uint32_t cp) const {
    for (int i = 0; i < mFtNumFaces; i++) {
        if (FT_Get_Char_Index((FT_Face)mFtFaces[i], cp) != 0) return mFtFaces[i];
    }
    return mFtNumFaces > 0 ? mFtFaces[0] : nullptr;   // primary (renders .notdef)
}

bool OverlayGfx::ensureAtlas() const {
    if (mAtlasTex) return true;
    glGenTextures(1, &mAtlasTex);
    if (!mAtlasTex) return false;
    glBindTexture(GL_TEXTURE_2D, mAtlasTex);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    // Single-channel, 1 MB. Replaces one GL texture per glyph (each of which the
    // driver page-aligned), so the working set is smaller, not larger.
    glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE, kAtlasW, kAtlasH, 0,
                 GL_LUMINANCE, GL_UNSIGNED_BYTE, nullptr);
    // Glyphs are rasterized at the display pixel size and drawn 1:1 on the pixel
    // grid, so nearest-neighbour sampling keeps them pixel-perfect and crisp.
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindTexture(GL_TEXTURE_2D, 0);
    mAtlasX = mAtlasY = mAtlasShelfH = 0;
    return true;
}

// Atlas full: drop every cached glyph and start packing from the top again.
// Stale pixels are never referenced once their cache entries are gone, and new
// packs overwrite them. The visible menu is a few hundred glyphs against a
// 1024x1024 atlas, so this is rare (a long session that visits every page at
// every size).
void OverlayGfx::resetAtlas() const {
    mGlyphs.clear();
    mAtlasX = mAtlasY = mAtlasShelfH = 0;
    ALOGI("OverlayGfx: glyph atlas recycled");
}

bool OverlayGfx::loadGlyph(uint32_t cp, int pxSize, Glyph* out) const {
    FT_Face face = (FT_Face)faceForCp(cp);
    if (!face) return false;
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
    out->u0 = out->v0 = out->u1 = out->v1 = 0.0f;
    if (out->w == 0 || out->h == 0) return true;   // blank (space): metrics only
    if (!ensureAtlas()) return false;
    const int pad = 1;   // one clear texel between glyphs so nearest sampling never bleeds
    const int gw = out->w + pad, gh = out->h + pad;
    if (gw > kAtlasW || gh > kAtlasH) return false;
    // Shelf packer: glyphs go left to right on the current shelf; a glyph that
    // does not fit opens a new shelf below. A miss at the bottom recycles.
    if (mAtlasX + gw > kAtlasW) { mAtlasX = 0; mAtlasY += mAtlasShelfH; mAtlasShelfH = 0; }
    if (mAtlasY + gh > kAtlasH) {
        resetAtlas();
        // The caller's earlier glyphs in this run are gone too; they are
        // re-rasterized on the next text() pass (mGlyphs is empty now).
    }
    {
        const int64_t t0 = android::elapsedRealtimeNano();
        glBindTexture(GL_TEXTURE_2D, mAtlasTex);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexSubImage2D(GL_TEXTURE_2D, 0, mAtlasX, mAtlasY, out->w, out->h,
                        GL_LUMINANCE, GL_UNSIGNED_BYTE, g->bitmap.buffer);
        glBindTexture(GL_TEXTURE_2D, 0);
        mDbgUploads++; mDbgUploadNs += android::elapsedRealtimeNano() - t0;
    }
    out->u0 = (float)mAtlasX / (float)kAtlasW;
    out->v0 = (float)mAtlasY / (float)kAtlasH;
    out->u1 = (float)(mAtlasX + out->w) / (float)kAtlasW;
    out->v1 = (float)(mAtlasY + out->h) / (float)kAtlasH;
    mAtlasX += gw;
    if (gh > mAtlasShelfH) mAtlasShelfH = gh;
    return true;
}

const OverlayGfx::Glyph* OverlayGfx::ensureGlyph(uint32_t cp, int pxSize) const {
    const uint64_t key = ((uint64_t)cp << 20) | (uint32_t)pxSize;
    auto it = mGlyphs.find(key);
    if (it != mGlyphs.end()) return &it->second;
    Glyph g{};
    if (!loadGlyph(cp, pxSize, &g)) return nullptr;
    return &mGlyphs.emplace(key, g).first->second;
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
    int pxSize = (int)(mFontPx * scale + 0.5f);
    if (pxSize < 4) pxSize = 4;
    if (pxSize > 256) pxSize = 256;

    // Pass 1: make sure every glyph of the run is in the atlas. Uploads happen
    // here, before any draw, and a recycle mid-run simply re-rasterizes the
    // run's earlier glyphs on the next pass (they are looked up again below).
    const char* end = s + strlen(s);
    for (int attempt = 0; attempt < 2; attempt++) {
        const size_t before = mGlyphs.size();
        const char* cp0 = s;
        while (cp0 < end) {
            uint32_t cp = decodeUtf8(&cp0, end);
            if (!cp) break;
            ensureGlyph(cp, pxSize);
        }
        // A recycle during the pass empties the cache; run it once more so the
        // whole run is resident before drawing.
        if (mGlyphs.size() >= before) break;
    }
    if (mRasterOnly) return measure(s, scale);

    // Pass 2: one vertex array for the whole run, one upload, one draw.
    // Snap the pen and baseline to whole pixels. Each glyph is rasterized at an
    // integer pixel size and drawn 1:1, so if it were placed at a fractional
    // coordinate the bitmap would straddle the pixel grid and blur, which is the
    // "badly scaled" look on low-resolution panels. Advances and bearings are
    // already whole pixels, so once the pen starts on an integer every glyph in
    // the run lands on the grid and stays crisp.
    float penX = floorf(x + 0.5f);
    const float baseline = floorf(y + mAscent * scale + 0.5f);
    mTextVerts.clear();
    const char* p = s;
    while (p < end) {
        uint32_t cp = decodeUtf8(&p, end);
        if (!cp) break;
        const uint64_t key = ((uint64_t)cp << 20) | (uint32_t)pxSize;
        auto it = mGlyphs.find(key);
        if (it == mGlyphs.end()) continue;   // rasterized above; skip if that failed
        const Glyph& g = it->second;
        if (g.w > 0 && g.h > 0) {
            const float gx = penX + g.bearingX, gy = baseline - g.bearingY;
            const float gw = g.w, gh = g.h;
            const float q[24] = {
                gx,      gy,      g.u0, g.v0,
                gx + gw, gy,      g.u1, g.v0,
                gx,      gy + gh, g.u0, g.v1,
                gx + gw, gy,      g.u1, g.v0,
                gx + gw, gy + gh, g.u1, g.v1,
                gx,      gy + gh, g.u0, g.v1,
            };
            mTextVerts.insert(mTextVerts.end(), q, q + 24);
        }
        penX += g.advance;
    }
    if (!mTextVerts.empty() && mAtlasTex) {
        flushSolids();   // text paints over the rects queued before it
        const int64_t t0 = android::elapsedRealtimeNano();
        mDbgTexts++;
        useProgram(mTextProgram);
        glUniform2f(mTextLocViewport, (float)mViewportW, (float)mViewportH);
        glUniformMatrix2fv(mTextLocRot, 1, GL_FALSE, mRot);
        glUniform4f(mTextLocColor, c.r, c.g, c.b, c.a);
        glActiveTexture(GL_TEXTURE0);
        glUniform1i(mTextLocSampler, 0);
        glBindTexture(GL_TEXTURE_2D, mAtlasTex);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glEnableVertexAttribArray(mTextLocPos);
        glVertexAttribPointer(mTextLocPos, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), mTextVerts.data());
        glEnableVertexAttribArray(mTextLocUv);
        glVertexAttribPointer(mTextLocUv, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float),
                              mTextVerts.data() + 2);
        glDrawArrays(GL_TRIANGLES, 0, (GLsizei)(mTextVerts.size() / 4));
        glDisableVertexAttribArray(mTextLocPos);
        glDisableVertexAttribArray(mTextLocUv);
        glBindTexture(GL_TEXTURE_2D, 0);
        mDbgTextNs += android::elapsedRealtimeNano() - t0;
    }
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
        // Measure through the same cache the draw uses: a miss rasterizes the
        // glyph into the atlas once (the run is about to be drawn anyway), so
        // a right-aligned value is never re-shaped by FreeType every frame.
        const Glyph* g = ensureGlyph(cp, pxSize);
        if (g) penX += g->advance;
    }
    return penX;
}

} // namespace drastic_gfx
} // namespace android
