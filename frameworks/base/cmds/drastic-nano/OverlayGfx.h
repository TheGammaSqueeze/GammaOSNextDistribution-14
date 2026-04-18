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
    int viewportW() const   { return mViewportW; }
    int viewportH() const   { return mViewportH; }

private:
    int mViewportW = 0;
    int mViewportH = 0;
    float mRot[4] = {1.f, 0.f, 0.f, 1.f};

    GLuint mSolidProgram = 0;
    GLuint mTextProgram  = 0;
    GLint  mSolidLocPos = -1, mSolidLocColor = -1, mSolidLocViewport = -1, mSolidLocRot = -1;
    GLint  mTextLocPos = -1, mTextLocUv = -1, mTextLocColor = -1, mTextLocViewport = -1, mTextLocRot = -1, mTextLocSampler = -1;

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
        int  advance = 0;
    };
    mutable std::unordered_map<uint32_t, Glyph> mGlyphs;

    void drawSolidQuad(float x, float y, float w, float h, Color c);
    bool loadGlyph(uint32_t codepoint, Glyph* out) const;
};

} // namespace drastic_gfx
} // namespace android
