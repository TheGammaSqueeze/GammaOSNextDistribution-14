// NanoCtrLayout - NW4C layout engine for the nano 3DS theme.
//
// A C++ port of the 3ds-web layout engine (/work/3ds/web/app/src/layout and src/gfx, described in
// /work/3ds/web/docs/engine.md): bclyt pane trees with materials, PICA200 texture combiners
// generated as GLSL ES 1.00, window frames, text boxes drawn with the console's fonts, and bclan
// animation playback. Layout space has its origin at the screen centre, y up; the top screen is
// 400x240 and the bottom screen 320x240 layout units.
//
// Assets come from the packs written by tools/ctr_pack.py (NanoCtrPack.h). Layout templates,
// animation curves, textures and shader programs are shared and cached; each LayoutInstance
// holds its own mutable copy of the pane tree and materials, which animations write into.
#pragma once

#include <GLES2/gl2.h>

#include <array>
#include <cstdint>
#include <functional>
#include <map>
#include <memory>
#include <string>
#include <string_view>
#include <unordered_map>
#include <vector>

#include "NanoCtrPack.h"

namespace android {
namespace ctr {

using Rgba = std::array<float, 4>;   // 0..255 per channel (animations write fractional values)
using Vec2 = std::array<float, 2>;
using QuadUv = std::array<Vec2, 4>;  // TL, TR, BL, BR

// ------------------------------------------------------------------ GPU textures

struct Texture {
    GLuint id = 0;
    int width = 0, height = 0;               // image size
    int texWidth = 0, texHeight = 0;         // allocated size (padded layout textures)
    // last sampler state applied to the GL object (GLES2 has no sampler objects)
    GLint wrapS = -1, wrapT = -1, minF = -1, magF = -1;
};
using TexturePtr = std::shared_ptr<Texture>;

// Bumped whenever a texture's pixels may change under the same GL name (uploads, atlas restarts,
// deletions, host texture reloads): a cached render (Renderer::CacheSlot) is only reused while
// it is unchanged.
extern uint64_t gTextureEpoch;

// ------------------------------------------------------------------ fonts

struct Glyph {
    uint32_t code = 0;
    int16_t left = 0;
    uint8_t glyphWidth = 0, charWidth = 0;
};

// A CFN1 font (tools/ctr_pack.py). Glyph bitmaps stay in the mapped file; the ones text
// actually uses are copied into a GL atlas on demand.
class Font {
public:
    ~Font();
    bool open(const std::string& path, const std::string& name);
    const std::string& name() const { return mName; }
    // Glyph for a code point, falling back to the alternate character. Never null once open.
    const Glyph* glyph(uint32_t code) const;
    // Atlas placement of a glyph (uploads it when needed). Returns false when it cannot be placed.
    bool place(const Glyph* g, float* u0, float* v0, float* u1, float* v1);
    GLuint atlas() const { return mAtlas; }
    // CPU copy of a glyph cell's coverage (cellWidth + 2) x (cellHeight + 2), border included.
    bool cellAlpha(const Glyph* g, std::vector<uint8_t>& out) const;
    // Bumped when the atlas was cleared to make room; cached text geometry must be rebuilt.
    uint32_t generation() const { return mGeneration; }
    void releaseGl();

    int lineFeed = 0, height = 0, width = 0, ascent = 0, baseline = 0, cellWidth = 0, cellHeight = 0;

private:
    std::string mName;
    const uint8_t* mMap = nullptr;
    size_t mMapSize = 0;
    int mFormat = 0;              // 0 A4 packed, 1 LA8, 2 A8
    uint32_t mCount = 0, mAlt = 0;
    const uint8_t* mTable = nullptr;
    const uint8_t* mBitmaps = nullptr;
    size_t mBitmapStride = 0;
    std::vector<Glyph> mGlyphs;   // decoded table (sorted by code)
    // atlas
    GLuint mAtlas = 0;
    int mAtlasSize = 0, mSlotW = 0, mSlotH = 0, mCols = 0, mRows = 0, mNext = 0;
    std::unordered_map<uint32_t, int> mSlots;   // glyph table index -> atlas slot
    uint32_t mGeneration = 1;
    std::vector<uint8_t> mScratch;
};

// ------------------------------------------------------------------ messages

struct TextRun {
    std::u32string text;
    bool hasColor = false;
    Rgba color{};
    float scale = 1.0f;
};
std::vector<TextRun> parseMarkup(std::string_view markup);
std::vector<TextRun> plainRuns(std::string_view text);
std::u32string utf8ToU32(std::string_view s);

struct MessageStyle {
    float fontScaleX = 1, fontScaleY = 1;
    bool valid = false;
};

// ------------------------------------------------------------------ materials

enum class TevMode : uint8_t { Replace, Modulate, Add, AddSigned, Interpolate, Subtract, AddMultiply, MultiplyAdd, Dot3Rgb, Dot3Rgba };
enum class TevSrc : uint8_t { Tex0, Tex1, Tex2, Tex3, Constant, Primary, Previous, Register };

struct TevCombiner {
    TevSrc src[3] = {TevSrc::Primary, TevSrc::Primary, TevSrc::Primary};
    uint8_t op[3] = {0, 0, 0};     // rgb: 0 rgb 1 invRgb 2 alpha 3 invAlpha 4 rrr 5 invRrr 6 ggg 7 invGgg 8 bbb 9 invBbb
                                   // alpha: 0 alpha 1 invAlpha 2 r 3 invR 4 g 5 invG 6 b 7 invB
    TevMode mode = TevMode::Replace;
    uint8_t scale = 1;
    bool saveToBuffer = false;
};

struct TevStage {
    TevCombiner rgb, alpha;
    uint8_t konstColor = 0, konstAlpha = 0;
};

struct TexMap {
    std::string name;          // texture resource name without extension
    TexturePtr texture;
    GLint wrapS = GL_CLAMP_TO_EDGE, wrapT = GL_CLAMP_TO_EDGE;
    GLint minF = GL_LINEAR, magF = GL_LINEAR;
};

struct TexSrt {
    float tx = 0, ty = 0, rotate = 0, sx = 1, sy = 1;
};

enum class AlphaFn : uint8_t { Never, Less, LEqual, Equal, NEqual, GEqual, Greater, Always };
enum class BlendOp : uint8_t { Disable, Add, Subtract, ReverseSubtract, Min, Max };
enum class BlendFactor : uint8_t { Zero, One, DstColor, InvDstColor, SrcAlpha, InvSrcAlpha, DstAlpha, InvDstAlpha, SrcColor, InvSrcColor };

struct Material {
    std::string name;
    Rgba bufferColor{0, 0, 0, 0};
    std::array<Rgba, 6> konst{};
    std::vector<TexMap> texMaps;
    std::vector<TexSrt> texSrts;
    std::vector<int> texCoordSources;
    std::vector<TevStage> stages;
    AlphaFn alphaFn = AlphaFn::Always;
    float alphaRef = 0;
    BlendOp blendOp = BlendOp::Add;
    BlendFactor blendSrc = BlendFactor::SrcAlpha, blendDst = BlendFactor::InvSrcAlpha;
    bool textureOnly = false;
    uint64_t shaderKey = 0;    // hash of the combiner configuration
    std::string shaderKeyText;
};

// ------------------------------------------------------------------ panes

enum class PaneKind : uint8_t { Null, Picture, Text, Window, Bounding, Parts };

struct Mat4 {
    float m[16];
};

struct WindowFrame {
    int material = -1;
    uint8_t flip = 0;  // 0 none 1 flipH 2 flipV 3 rotate90 4 rotate180 5 rotate270
};

struct Pane {
    PaneKind kind = PaneKind::Null;
    std::string name;
    std::string userData;
    Pane* parent = nullptr;
    std::vector<Pane*> children;

    float translate[3] = {0, 0, 0};
    float rotate[3] = {0, 0, 0};
    float scale[2] = {1, 1};
    float size[2] = {0, 0};
    float alpha = 255;
    bool visible = true;
    bool influencedAlpha = false;
    uint8_t originX = 1, originY = 1;   // 0 left/top, 1 centre, 2 right/bottom

    // widescreen adaptation (Layout::adaptWidth): added to translate x / size x when the layout
    // is drawn wider than designed; kept apart from the animated values
    float shiftX = 0, growW = 0;

    Mat4 global;
    float globalAlpha = 255;
    float childAlpha = 255;

    // picture / window content
    int material = -1;
    std::array<Rgba, 4> vertexColors{};       // lt rt lb rb (text: top, top, bottom, bottom)
    std::vector<QuadUv> texCoords;
    // window
    float overflowL = 0, overflowR = 0, overflowT = 0, overflowB = 0;
    std::vector<WindowFrame> frames;
    // text box
    std::string fontResource;
    std::shared_ptr<Font> font;
    float fontSize[2] = {0, 0};
    float charSpace = 0, lineSpace = 0;
    uint8_t textPosX = 1, textPosY = 1;
    uint8_t alignment = 0;                     // 0 synchronous 1 left 2 center 3 right
    std::vector<TextRun> runs;
    bool hasFontScale = false;
    float fontScale[2] = {1, 1};
    uint32_t textRevision = 0;
    // draw cache for text
    struct GlyphQuad { float l, t, r, b, u0, v0, u1, v1; bool hasColor; Rgba color; };
    std::vector<GlyphQuad> textQuads;
    uint64_t textCacheKey = 0;

    void rect(float* l, float* t, float* r, float* b) const {
        const float w = size[0] + growW, h = size[1];
        *l = -w * originX * 0.5f;
        *t = h * originY * 0.5f;
        *r = *l + w;
        *b = *t - h;
    }
};

struct Group {
    std::string name;
    std::vector<Pane*> panes;
    std::vector<int> children;  // indices into the layout's group list
};

// Immutable parsed layout (shared by every instance of it).
struct LayoutTemplate;
// Parsed animation file (shared).
struct AnimTemplate;

class Assets;

// One loaded layout: its own pane tree and materials, textures bound.
class Layout {
public:
    Layout(const LayoutTemplate& t, Assets& assets, const std::string& archive);
    ~Layout();
    Layout(const Layout&) = delete;
    Layout& operator=(const Layout&) = delete;

    float width = 0, height = 0;
    std::vector<Material> materials;
    Pane* root = nullptr;
    std::vector<Group> groups;
    float opacity = 255;   // whole layout fade (web extension)

    Pane* findPane(std::string_view name) const;
    const Group* findGroup(std::string_view name) const;
    Material* findMaterial(std::string_view name);
    void calculate();      // global matrices and alphas
    // Widescreen: lays the layout out `width` units wide (>= its design width). Panes spanning
    // the design width grow to the new width; with policy Edge, panes in the outer 15% move to
    // the new edges; with Split, panes left / right of the centre move to the new edges.
    enum class Adapt : uint8_t { None, Edge, Split };
    void adaptWidth(float width, Adapt policy);
    float adaptedWidth = 0;
    // Several materials can share a name; animations bind every one.
    std::vector<Material*> materialsNamed(std::string_view name);

private:
    void calcPane(Pane* p, const Mat4& parent, float inherited);
    std::vector<std::unique_ptr<Pane>> mPanes;
    std::unordered_map<std::string, Pane*> mByName;
};

// ------------------------------------------------------------------ animation

struct PlayOptions {
    int loop = -1;              // -1 from the file, 0 one shot, 1 loop
    float speed = 1.0f;
    float weight = 1.0f;
    float frame = -1.0f;        // -1: 0, or the last frame when speed < 0
    bool paused = false;
    int groups = 0;             // 0 whole layout, 1 the file's pat1 groups, 2 the names below
    std::vector<std::string> groupNames;
    std::vector<std::pair<std::string, std::string>> bindAs;   // pane name -> content name
    std::string id;             // binding key, default the file name
};

class Animator {
public:
    std::string name, id;
    int frameSize = 1;
    bool loop = false;
    float speed = 1, weight = 1, frame = 0;
    bool playing = true;
    bool finished = false;
    std::function<void()> onFinish;   // called once when a one shot ends (or stop())

    float lastFrame() const { return loop ? (float)frameSize : (float)(frameSize - 1); }
    void advance(float frames);
    void seek(float f);
    void apply();
    void stop();

    struct Track {
        const struct AnimCurve* curve;
        std::function<void(float, float)> set;
    };
    std::vector<Track> tracks;
    std::shared_ptr<const AnimTemplate> source;
private:
    void finish();
};

float evaluateCurve(const struct AnimCurve& c, float frame);

// ------------------------------------------------------------------ instance (menu API)

enum class Screen : uint8_t { Top, Bottom };

class LayoutInstance {
public:
    LayoutInstance(Assets& assets, std::string archive, std::string name, std::unique_ptr<Layout> layout);
    ~LayoutInstance();

    const std::string& archive() const { return mArchive; }
    const std::string& name() const { return mName; }
    Layout& layout() { return *mLayout; }
    Screen defaultScreen() const { return mLayout->width > 320 ? Screen::Top : Screen::Bottom; }

    Pane* pane(std::string_view name) const { return mLayout->findPane(name); }
    bool hasAnimation(std::string_view name) const;
    std::string resolveAnimation(std::string_view name) const;   // "" when absent
    // Binds and starts an animation; returns nullptr when the file does not exist.
    Animator* play(std::string_view name, const PlayOptions& opt = {});
    void stop(std::string_view idOrName);
    Animator* find(std::string_view name);
    void update(float frames = 1.0f);
    void refresh() { update(0.0f); }

    void setVisible(std::string_view pane, bool v);
    void setAlpha(std::string_view pane, float a);
    void setText(std::string_view pane, std::string_view text, bool markup = false);
    void setTextU32(std::string_view pane, const std::u32string& text);
    bool setMessage(std::string_view pane, std::string_view label);
    // Replaces texture map `mapIndex` of a picture / window content.
    void replaceTexture(std::string_view pane, TexturePtr tex, int mapIndex = 0);

    std::vector<std::unique_ptr<Animator>> animators;
    bool anyPlaying() const;
private:
    // Stopped animators are kept until the end of the running update (a finish callback may stop
    // the animator that is being advanced).
    std::vector<std::unique_ptr<Animator>> mDead;
    int mUpdating = 0;
    Assets& mAssets;
    std::string mArchive, mName;
    std::unique_ptr<Layout> mLayout;
};

// ------------------------------------------------------------------ renderer

struct ProgramInfo;

class Renderer {
public:
    explicit Renderer(Assets& assets);
    ~Renderer();
    bool init();
    void releaseGl();
    // Starts drawing a screen of the given layout size into the bound framebuffer + viewport.
    // `widen` grows the projected width in layout units (aspect adaptation), centred.
    void begin(float layoutW, float layoutH);
    void beginWith(const float proj[16]);
    // Restores the GL state nano's own drawing expects (client arrays, full colour mask).
    void end();
    void drawLayout(Layout& layout);
    // Draws one pane subtree (icons share one layout drawn at several places).
    void drawPane(Layout& layout, Pane* pane);
    const float* projection() const { return mProj; }
    int drawCalls() const { return mDrawCalls; }
    void resetStats() { mDrawCalls = 0; }

    // Scissor in layout units (x, y of the bottom-left corner, y up) or off. The rectangle goes
    // through the projection, so it also works on rotated panels. The viewport must be the GL
    // viewport the projection's clip space maps to.
    void setScissor(bool on, float x = 0, float y = 0, float w = 0, float h = 0);
    void setViewport(int x, int y, int w, int h) { mVpX = x; mVpY = y; mVpW = w; mVpH = h; }
    // Clip every following draw to a layout rectangle (the screen); nested setScissor calls
    // intersect with it. clearClip() drops it.
    void setClip(float x, float y, float w, float h);
    void clearClip();

    // Exact cache for a group of draws that rarely changes (the bottom screen's background,
    // sheets and icons). The group must be the first thing drawn on a viewport that was filled
    // with `clear` (rgb). Its whole draw stream (vertices, uniforms, textures, state) is hashed:
    // when it equals the previous frame's, the pixels rendered then are copied 1:1 instead of
    // drawing it again; a group that changes every frame is drawn directly (no copy cost).
    struct CacheSlot {
        uint64_t hash = 0;
        bool valid = false;        // tex holds the pixels of `hash`
        GLuint fbo = 0, tex = 0;
        int w = 0, h = 0;
        int hits = 0, misses = 0;  // statistics (probe)
    };
    void beginGroup();
    void endGroup(CacheSlot& slot, const float clear[3]);
    void releaseCache(CacheSlot& slot);

private:
    void drawPaneTree(Layout& L, Pane* p);
    void drawPicture(Layout& L, Pane* p);
    void drawWindow(Layout& L, Pane* p);
    void drawText(Layout& L, Pane* p);
    int writeQuad(int index, float l, float t, float r, float b, const Rgba* colors, const QuadUv* const* uvSets);
    void submit(const Pane* p, const Material& m, int quads, const TexturePtr* tex, int texCount,
                const TexMap* samplers, const TexSrt* srts, int srtCount, GLuint glyphAtlas = 0);
    ProgramInfo* program(const Material& m, int textureCount);
    void applyBlend(int key, GLenum eq, GLenum src, GLenum dst);
    void flush();

    // Draws are recorded and issued in one go at end(): every vertex of the frame goes to the
    // GPU in a single upload into a buffer of a ring the GPU is done with. Uploading per draw
    // into one buffer still in flight made the driver allocate and zero fresh GPU memory for
    // each draw (the whole frame's cost).
    struct DrawCmd {
        ProgramInfo* prog;
        float mvp[16];
        float texMatrix[18];
        float texScale[6];
        float konst[24];
        float buffer[4];
        float alphaRef;
        GLuint tex[3];
        Texture* texObj[3];
        GLint params[3][4];   // wrap S, wrap T, min, mag
        int blendKey;
        GLenum blendEq, blendSrc, blendDst;
        bool scissorOn;
        int scissor[4];
        int first, count;     // vertices in the frame stream
        // special commands: 1 = draw commands [cacheFrom, i) into cache->fbo first (cleared to
        // cacheClear), 2 = copy cache->tex over the viewport
        int special;
        int cacheFrom;
        CacheSlot* cache;
        float cacheClear[3];
    };
    int mGroupStart = -1;
    GLuint mBlitProgram = 0;
    void issue(size_t from, size_t to, GLuint* bound, int* scissorOn, int* scissor, int* unit, int scissorDx, int scissorDy);
    std::vector<DrawCmd> mCmds;
    std::vector<float> mFrameVerts;
    std::vector<TexturePtr> mKeep;     // textures a recorded draw uses stay alive until it is issued
    static const int kRing = 6;
    GLuint mRing[kRing] = {};
    size_t mRingSize[kRing] = {};
    int mRingNext = 0;
    bool mScissorOn = false;
    int mScissor[4] = {0, 0, 0, 0};
    std::vector<float> mVerts;
    GLuint mWhiteTex = 0;
    std::unordered_map<std::string, std::unique_ptr<ProgramInfo>> mPrograms;
    float mProj[16];
    int mDrawCalls = 0;
    int mVpX = 0, mVpY = 0, mVpW = 0, mVpH = 0;
    bool mHasClip = false;
    int mClip[4] = {0, 0, 0, 0};   // window pixels x, y, w, h
    void layoutRectToWindow(float x, float y, float w, float h, int out[4]) const;
    float mLayoutW = 400, mLayoutH = 240;
    // GL state while issuing
    int mBlendState = -1;
    GLuint mBoundProgram = 0;
};

// ------------------------------------------------------------------ assets

class Assets {
public:
    explicit Assets(std::string root);
    ~Assets();
    const std::string& root() const { return mRoot; }
    bool ok();

    Pack* pack(const std::string& file);                 // e.g. "lyt_launcher.cpk", cached
    const LayoutTemplate* layoutTemplate(const std::string& archive, const std::string& name);
    std::shared_ptr<const AnimTemplate> animation(const std::string& archive, const std::string& name);
    bool archiveHasAnim(const std::string& archive, std::string_view name);
    TexturePtr layoutTexture(const std::string& archive, const std::string& name);
    TexturePtr pngTexture(const uint8_t* data, size_t size, bool flipY);
    TexturePtr rgbaTexture(const uint8_t* rgba, int w, int h);
    std::shared_ptr<Font> font(const std::string& resourceName);

    // Messages of the active language (menu first, then hud).
    void setLanguage(const std::string& language);
    const std::string& language() const { return mLanguage; }
    bool message(std::string_view label, std::string* markup, MessageStyle* style);
    std::string messageText(std::string_view label);   // plain text (markup stripped)

    std::unique_ptr<LayoutInstance> create(const std::string& archive, const std::string& name);
    Renderer& renderer() { return *mRenderer; }

    // Frees GL objects not referenced by any live layout (textures) and drops parsed docs.
    void trim();
    void releaseGl();     // context loss / theme unload: every GL handle dies

private:
    std::string mRoot;
    std::map<std::string, std::unique_ptr<Pack>> mPacks;
    std::map<std::string, std::unique_ptr<LayoutTemplate>> mLayouts;
    std::map<std::string, std::shared_ptr<const AnimTemplate>> mAnims;
    std::map<std::string, std::weak_ptr<Texture>> mTextures;
    std::map<std::string, TexturePtr> mTexturePins;     // recently used, kept briefly
    std::map<std::string, std::shared_ptr<Font>> mFonts;
    std::unique_ptr<Renderer> mRenderer;
    std::string mLanguage = "English";
    const BjDoc* mMsg[4] = {nullptr, nullptr, nullptr, nullptr};   // menu, hud, menuStyles, hudStyles
};

}  // namespace ctr
}  // namespace android
