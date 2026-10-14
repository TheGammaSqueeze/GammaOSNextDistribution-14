// NanoCtrTop - the 3DS theme's top screen 3D scene: the BannerBG wall and the banner of the
// focused launcher item, seen through BannerCamera. Port of /work/3ds/web/app/src/menu/top
// (scene3d.ts, banner.ts, banners.ts, nametex.ts).
#pragma once

#include <functional>
#include <memory>
#include <string>
#include <vector>

#include "NanoCtr3d.h"
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
    bool dsIcon = false;
};

class CtrTop {
public:
    explicit CtrTop(Assets& assets);
    ~CtrTop();
    bool load();
    void releaseGl();
    void sceneIn();
    void focus(const TopFocus& f);
    // The theme's wall colour and folder banner colours (null = the file colours).
    void setTheme(const ThemeBody* body);
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
    // Internal resolution cap of the 3D target in multiples of 400x240 (default 3).
    void setMaxScale(int s) { mMaxScale = s; }

private:
    void themeWall();
    void themeBanner(ModelInstance& model, int type);
    const ThemeBody* mTheme = nullptr;
    bool mWallHidden = false;
    struct BannerSpec {
        int type = 0;
        std::string model;
        std::vector<std::string> animations;
        std::vector<std::string> meshes;
        float nameScale = 1;
        std::string nameTexture = "DmyText_00";
        bool still = false, paused = false, loop = false, turn = true;
    };
    struct Banner {
        BannerSpec spec;
        std::unique_ptr<ModelInstance> model;
        std::unique_ptr<ModelInstance> extra;   // a ROM banner's language model, following `model`
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
    int mMaxScale = 3;
    float mBaseFovY = 0.5235988f;   // BannerCamera's vertical field of view at the design height
    std::shared_ptr<GpuTex> mPlate;
    // the name plate's marquee (a name too long for the plate scrolls at the one font size)
    static constexpr int kPlateW = 256, kPlateH = 64;
    static constexpr float kPlateMargin = 4, kMarqueeSpeed = 0.5f, kMarqueeHold = 90;
    std::string mPlateText;
    float mPlateSize = 1;
    int mPlateFrame = 0;
    std::vector<float> mPlateOverflow, mPlateOffsets;
    void rasterPlate(const std::string& text, float size, const std::vector<float>& offsets, std::vector<uint8_t>* px,
                     std::vector<float>* overflow);
    void updatePlate();
    std::shared_ptr<ModelResource> mRomRes;   // the current ROM banner's model (not shared)
    std::string mRomResKey;
    // composite
    GLuint mBlitProgram = 0;
    GLint mBlitProj = -1;
};

}  // namespace ctr
}  // namespace android
