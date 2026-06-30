/*
 * Copyright (C) 2026 GammaOS
 *
 * OverlayGfx: minimal GLES2 renderer used by drastic-nano's in-game
 * overlay menu. Provides:
 *
 *   - translucent colored rectangles and outlines (for panels, bars,
 *     highlights)
 *   - Roboto-rendered text at arbitrary scale, via a FreeType glyph
 *     cache lazily populated as new codepoints are encountered
 *   - a logical coordinate system in "overlay pixels" (top-left
 *     origin; Y grows downward) sized to the presented panel; drastic
 *     itself is rotated via sDrmRotMat so we apply the same rotation
 *     in the shader to end up in physical-panel orientation
 *
 * The class is a thin wrapper around plain GL state; it does NOT own
 * an EGL context. Caller must have a current GL context that is
 * compatible with the drastic render path (same context, different
 * framebuffers). beginFrame saves the drastic GL program binding,
 * endFrame restores it so renderFrame's next invocation is not
 * disturbed.
 */

#pragma once

#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include <GLES2/gl2.h>

namespace android {
namespace drastic_gfx {

struct Color { float r, g, b, a; };

inline Color rgba(float r, float g, float b, float a = 1.0f) {
    return Color{r, g, b, a};
}

class OverlayGfx {
public:
    OverlayGfx();
    ~OverlayGfx();

    // Initialize shaders, font cache, VBOs. Must be called with a
    // current GL context. viewportW/H are the logical overlay size
    // (drastic-nano passes the panel dimensions of its primary AHB
    // target). The 2x2 rotation matrix is the same one the runner
    // uses (sDrmRotMat), column-major.
    bool init(int viewportW, int viewportH, const float rotMat[4]);

    // Release all GL resources. Safe to call more than once.
    void shutdown();

    // Call before any of the draw helpers in a given frame. Snapshots
    // current GL state (the active program) so endFrame can restore
    // it.
    void beginFrame();

    // Restore the previously bound program. Call after all overlay
    // drawing for this frame is done. Keeps drastic's renderFrame
    // unaffected on the next iteration.
    void endFrame();

    // Tell beginFrame what program binding to restore. Captured by
    // DrasticRunner and handed in each frame.
    void setRestoreProgram(GLuint prog) { mDrasticProgram = prog; }

    // Update rotation matrix (call whenever drastic's rotation changes).
    void setRotationMatrix(const float rot[4]);

    // Primitives. All coordinates are in overlay pixel space,
    // top-left origin. Widths/heights are pixels.
    void fillRect(float x, float y, float w, float h, Color c);
    void outline(float x, float y, float w, float h, float px, Color c);
    // Convenience: translucent panel with 1px outline.
    void panel(float x, float y, float w, float h, Color bg, Color edge);

    // Rounded rectangle via the same signed-distance-field shader the
    // gammaos-nano XMB uses (1px anti-aliased edge). radius is clamped to
    // min(w,h)/2. Used by the PS3-XMB-styled on-screen keyboard.
    void roundedRect(float x, float y, float w, float h, float radius, Color c);
    // Flat-colour triangle (3 points, pixel space). For glyph shapes such
    // as the backspace arrowhead.
    void triangle(float x0, float y0, float x1, float y1,
                  float x2, float y2, Color c);

    // Filled 5-point star centred at (cx, cy) with outer radius r. Drawn as
    // geometry (not a font glyph) so the "unlocked" marker renders regardless
    // of font coverage (Roboto has no U+2605).
    void star(float cx, float cy, float r, Color c);

    // RGBA image drawing (achievement badges). createImageTexture uploads raw
    // 8-bit RGBA pixels (w*h*4 bytes) and returns a GL texture name (0 on
    // failure); drawImage draws it as a quad in overlay pixel space with an
    // overall alpha multiplier; destroyTexture frees it. Must be called with a
    // current GL context (render thread).
    GLuint createImageTexture(const uint8_t* rgba, int w, int h);
    void   drawImage(GLuint tex, float x, float y, float w, float h,
                     float alpha = 1.0f);
    void   destroyTexture(GLuint tex);

    // Override the logical viewport (pixel->NDC denominator). Lets the same
    // OverlayGfx draw into a differently-sized FBO (e.g. the bottom DS panel
    // for the OSK) for one pass; restore the primary size afterwards.
    void setViewport(int w, int h) { mViewportW = w; mViewportH = h; }

    // Bitmap text rendering. scale=1.0 renders at the FreeType base
    // pixel size (set at init). Larger scale oversamples the atlas.
    // (x, y) is the top-left of the glyph row. Returns the advance
    // width in pixels.
    float text(const char* s, float x, float y, float scale, Color c);

    // Measure-only variant (no draw). Same math as text().
    float measure(const char* s, float scale) const;

    // Font metrics.
    int fontAscent() const  { return mAscent; }
    int fontLineH() const   { return mLineH; }
    // Base FreeType pixel size: text(scale=1) renders glyphs this tall, so a
    // desired pixel height maps to scale = pxH / fontBasePx().
    int fontBasePx() const  { return mFontPx; }
    int viewportW() const   { return mViewportW; }
    int viewportH() const   { return mViewportH; }

private:
    int mViewportW = 0;
    int mViewportH = 0;
    float mRot[4] = {1.f, 0.f, 0.f, 1.f};

    GLuint mSolidProgram = 0;
    GLuint mTextProgram  = 0;
    GLuint mRoundProgram = 0;
    GLuint mImageProgram = 0;
    GLint  mSolidLocPos = -1, mSolidLocColor = -1, mSolidLocViewport = -1, mSolidLocRot = -1;
    GLint  mTextLocPos = -1, mTextLocUv = -1, mTextLocColor = -1, mTextLocViewport = -1, mTextLocRot = -1, mTextLocSampler = -1;
    GLint  mRoundLocPos = -1, mRoundLocLocal = -1, mRoundLocColor = -1,
           mRoundLocViewport = -1, mRoundLocRot = -1, mRoundLocHalf = -1, mRoundLocRadius = -1;
    GLint  mImgLocPos = -1, mImgLocUv = -1, mImgLocViewport = -1, mImgLocRot = -1,
           mImgLocSampler = -1, mImgLocAlpha = -1, mImgLocTexSize = -1,
           mImgLocScale = -1;
    // Native pixel size of each image texture, remembered at upload so drawImage
    // can drive the sharp-bilinear sampler (it needs the texel grid and the
    // on-screen upscale factor). Cleared in destroyTexture.
    std::unordered_map<GLuint, std::pair<int, int>> mImgSizes;

    GLuint mQuadVbo = 0;   // scratch VBO, refilled per-draw
    GLuint mTextVbo = 0;
    GLuint mDrasticProgram = 0;

    // FreeType glyph cache.
    void* mFtLibrary = nullptr;
    void* mFtFace = nullptr;
    int   mFontPx = 18;        // base pixel height
    int   mAscent = 0;
    int   mLineH  = 0;

    struct Glyph {
        GLuint tex = 0;
        int  w = 0, h = 0;
        int  bearingX = 0, bearingY = 0;
        int  advance = 0;     // metrics are in pixels at the rasterized size
    };
    // Size-aware cache keyed by (codepoint, pixelSize): each glyph is
    // rasterized at its actual display size and drawn 1:1, so scaled-up text
    // (e.g. big OSK keys) stays crisp instead of magnifying a small atlas.
    mutable std::unordered_map<uint64_t, Glyph> mGlyphs;

    void drawSolidQuad(float x, float y, float w, float h, Color c);
    bool loadGlyph(uint32_t codepoint, int pxSize, Glyph* out) const;
};

} // namespace drastic_gfx
} // namespace android
