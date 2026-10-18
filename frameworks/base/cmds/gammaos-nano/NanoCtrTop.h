// NanoCtrTop - the 3DS theme's top screen 3D scene: the BannerBG wall and the banner of the
// focused launcher item, seen through BannerCamera. Port of /work/3ds/web/app/src/menu/top
// (scene3d.ts, banner.ts, banners.ts, nametex.ts).
#pragma once

#include <atomic>
#include <functional>
#include <set>
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>

#include "NanoCtr3d.h"
#include "NanoCtrGlyph3d.h"
#include "NanoCtrLayout.h"
#include "NanoCtrTheme.h"

namespace android {
namespace ctr {

struct RomBanner;

struct TopFocus {
    enum Kind { None, Empty, Folder, Title, Bar } kind = None;
    int bar = 0;              // Bar: applet bar button
    std::string key;          // identity of the focused item
    std::string plate;        // name plate text (lines split by '\n')
    // Title art: a 3DS ROM's own CBMD banner, else an icon (a DS cartridge's icon on the DS banner,
    // a cover on the DSiWare card), else the 2D name banner.
    std::shared_ptr<const RomBanner> cbmd;
    unsigned icon = 0;
    int iconW = 0, iconH = 0;
    bool dsIcon = false;      // the DS cartridge model carries the icon
    bool pixelArt = false;    // the icon is pixel art (a DS banner icon): sampled nearest
    // Folder: a monochrome glyph (a game system's icon) shown as a solid 3D object in place of the
    // folder model (its alpha is the shape; rows stored bottom first, like the layout textures).
    unsigned glyph = 0;
    int glyphW = 0, glyphH = 0;
    bool glyphColor = false;    // a full colour icon: the solid's faces and walls keep its colours
    bool glyphTinted = false;   // the glyph's own colour (a system's tint), else the folder colours
    float glyphTint[3] = {1, 1, 1};
};

class CtrTop {
public:
    explicit CtrTop(Assets& assets);
    ~CtrTop();
    bool load();
    void releaseGl();
    void parkGl();   // the 3D pass's targets and programs (rebuilt by the next draw)
    void sceneIn();
    void focus(const TopFocus& f);
    // The next focus attaches its banner again even when it names the same item and texture (the
    // host's textures were freed or replaced and a GL name can come back for other pixels).
    void refocus() { mWantedKey.clear(); }
    // The theme's wall colour and folder banner colours (null = the file colours).
    void setTheme(const ThemeBody* body);
    // nano's category colours for the folder banner, in place of the theme's (null = the theme's).
    void setFolderBlock(const ThemeBlock* block);
    // The empty slot banner's floating plates (BannerDef: one per applet) show nano's category
    // icons: applet button 1..5's pictogram texture (GL name, owned by the caller, laid out as the
    // console's DefBnr textures: luminance white plate / black ink, alpha the silhouette, rows bottom
    // first) and its ink colour.
    void setDefaultIcon(int button, unsigned texture, int w, int h, const float ink[3]);
    void update();            // one 60 Hz frame
    // Draws the scene into the top screen rectangle of the bound framebuffer. `proj` maps the
    // 400x240 layout space (as for the layouts); vp is the GL viewport it targets.
    void draw(const float proj[16], int vpX, int vpY, int vpW, int vpH, float width = 400, float height = 240);
    // Drawn into the scene's target under the 3D models (the top background image): gets the
    // target size and a projection of the width x height layout space onto it.
    std::function<void(int w, int h, const float proj[16])> underlay;
    // The focused title's banner is left out (a settings panel is open over the home).
    void setBannerHidden(bool hidden) { mBannerHidden = hidden; }
    // A background image covers the wall (scene3d.ts wallHidden): the wall is not drawn; the
    // image comes from `underlay`.
    void setWallHidden(bool hidden) { mWallHidden = hidden; }
    // A title banner with a sound appeared / the focus left it (the banner plays its BCWAV once).
    std::function<void(const std::shared_ptr<const RomBanner>&)> onBannerSound;
    std::function<void()> onBannerLeave;
    // Internal resolution cap of the 3D target in multiples of 400x240 (default 2.5).
    void setMaxScale(float s) { mMaxScale = s; }
    // The suspended title (scene3d.ts setAppImage / followApp). `image` is its upper screen laid
    // out like the LCD framebuffer (BG_DmyApp_00, 256x512 portrait, any multiple of that size);
    // the caller keeps the GL texture alive until clearAppImage. The wall goes out while the
    // title runs (BannerBG_SceneOut, the capture filling the screen) and comes back with
    // AppPause; AppRestart and AppQuit play over a resume or a close.
    enum class AppState { Running, Suspended, Resumed, Closing, Closed };
    enum class AppBanner { None, Pause, Restart, Quit };
    void setAppImage(GLuint texture, int width, int height);
    void clearAppImage();
    void followApp(AppState state, AppBanner banner);
    // The wall already out (a title was running before this instance saw it start).
    void setWallOut();
    // No title any more (it ended while the home was away): the app animations stop and the wall
    // is in place at once.
    void resetApp();
    bool appAnimating() const { return !mAppEnding.empty(); }
    // The grid identities of the suspended title: focused, it shows no banner (the wall holds
    // its screen).
    void setSuspendedKeys(const std::vector<std::string>& keys) { mSuspendedKeys = keys; }
    // A game's Information page: its box art centred on the top screen over its fanart (dimmed),
    // in the banner's place. The caller keeps the textures alive until clearInfoArt.
    void setInfoArt(unsigned box, int boxW, int boxH, unsigned fan, int fanW, int fanH);
    void clearInfoArt() { mInfoBox = mInfoFan = 0; }

private:
    void themeWall();
    void themeBanner(ModelInstance& model, int type);
    const ThemeBody* mTheme = nullptr;
    bool mFolderOn = false;
    struct DefIcon { unsigned tex = 0; int w = 0, h = 0; float ink[3] = {0, 0, 0}; };
    DefIcon mDefIcons[6];
    ThemeBlock mFolder;
    bool mWallHidden = false;
    std::vector<std::string> mSuspendedKeys;
    std::shared_ptr<GpuTex> mAppTex, mAppTexOriginal;
    bool mAppTexSaved = false;
    std::string mAppEnding;   // AppRestart / AppQuit while it plays
    bool mWallOut = false;
    struct BannerSpec {
        int type = 0;
        std::string model;
        std::vector<std::string> animations;
        std::vector<std::string> meshes;
        std::vector<std::string> hideMaterials;   // meshes of these materials are left out
        float nameScale = 1;
        // width / height of the quad the name texture covers: the 4:1 of DmyText_00, 2:1 for
        // Banner2D's Dmy_00 (24 x 12 units): the glyphs are widened to keep their proportions
        float plateAspect = 4;
        std::string nameTexture = "DmyText_00";
        bool still = false, paused = false, loop = false, turn = true;
    };
    struct Banner {
        BannerSpec spec;
        std::unique_ptr<ModelInstance> model;
        std::unique_ptr<ModelInstance> extra;   // a ROM banner's language model, following `model`
        std::shared_ptr<GlyphMesh> glyph;        // a folder's system icon, on the folder's bone
        std::string glyphKey;                    // the glyph still being built for it (mGlyphJobs)
        std::set<int> glyphHide;                 // the meshes the glyph stands in for, hidden once it lands
        int glyphBone = -1;
        bool tinted = false;
        float tint[3] = {1, 1, 1};
        bool shown = true;
        int scaleStep = 4;
        int turnFrame = 0;
        bool gone() const { return !shown && scaleStep == 0; }
        void update();
    };
    std::shared_ptr<ModelResource> resource(const std::string& name);
    std::shared_ptr<ModelResource> romResource(const std::shared_ptr<const RomBanner>& b);
    std::shared_ptr<ModelResource> packResource(const std::vector<uint8_t>& bytes, const std::string& what);
    std::shared_ptr<GpuTex> plateTexture(const std::string& text, float size);
    void attach();

    Assets& mAssets;
    Renderer3d mRenderer;
    Camera3d mCamera;
    std::unique_ptr<ModelInstance> mWall;
    std::unique_ptr<Banner> mBanner;
    bool mBannerHidden = false;
    std::vector<std::unique_ptr<Banner>> mLeaving;
    std::map<std::string, std::shared_ptr<ModelResource>> mResources;
    std::string mWantedKey;
    TopFocus mPendingFocus;
    BannerSpec mPendingSpec;
    bool mHasPending = false;
    int mPendingLeft = 0;
    bool mAttachedOnce = false;
    // The 3D target's cap in multiples of 400x240. The wall fills the whole target and its fill
    // was most of the frame on the RP Duo Lite (about 10 ms at 3x and 600 MHz): 2.5x keeps 60 fps
    // there with no visible loss on a 1080p panel (the banners stay above their texture detail).
    float mMaxScale = 2.5f;
    float mBaseFovY = 0.5235988f;   // BannerCamera's vertical field of view at the design height
    std::shared_ptr<GpuTex> mPlate;
    // the name plate's marquee (a name too long for the plate scrolls at the one font size)
    // the plate is drawn at kPlateRes times DmyText_00's 256 x 64 (the model samples it 0..1)
    static constexpr int kPlateRes = 3, kPlateW = 256 * kPlateRes, kPlateH = 64 * kPlateRes, kPlateSdf = 4;
    static constexpr float kPlateMargin = 4 * kPlateRes, kMarqueeSpeed = 0.5f * kPlateRes, kMarqueeHold = 90;
    std::unordered_map<const Glyph*, std::vector<uint8_t>> mPlateSdf;   // glyph distance fields
    struct PlateLine {
        std::vector<uint8_t> ink;   // w x h coverage
        int w = 0, y0 = 0, h = 0;
        bool fits = true;
        float overflow = 0;
    };
    std::vector<PlateLine> mPlateLines;
    float mPlateStretch = 1;
    int mPlateFrame = 0;
    std::vector<float> mPlateOffsets, mPlateNext;
    std::vector<uint8_t> mPlateRows;   // the marquee's rows, reused frame to frame
    void layoutPlate(const std::string& text, float size);
    void composePlate(int y0, int h, std::vector<uint8_t>* out) const;
    void updatePlate();
    std::shared_ptr<ModelResource> mRomRes;   // the current ROM banner's model (not shared)
    // solid glyphs by folder (built once each; a few game systems)
    std::map<std::string, std::shared_ptr<GlyphMesh>> mGlyphs;
    // a glyph being built off the GL thread: its pixels are read back when the focus lands, the
    // field and mesh are computed on a worker and uploaded in update() (pollGlyphJobs). The
    // synchronous build took about 80 ms per newly focused folder and stalled the frame.
    struct GlyphJob {
        std::string key;
        std::shared_ptr<GlyphMesh::Built> built;
        std::shared_ptr<std::atomic<bool>> done;
    };
    std::vector<GlyphJob> mGlyphJobs;
    bool startGlyphJob(const std::string& key, unsigned texture, int w, int h, float boxW, float boxH, float cx, float cy,
                       float depth, bool colour);
    void pollGlyphJobs();
    void drawGlyphs(const Camera3d& cam);
    void drawInfoArt(float width, float height);
    std::string mRomResKey;
    // composite
    GLuint mBlitProgram = 0;
    GLint mBlitProj = -1, mBlitTint = -1;
    unsigned mInfoBox = 0, mInfoFan = 0;
    int mInfoBoxW = 0, mInfoBoxH = 0, mInfoFanW = 0, mInfoFanH = 0;
};

}  // namespace ctr
}  // namespace android
