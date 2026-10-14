// NanoCtrTop - the 3DS theme's top screen 3D scene: the BannerBG wall and the banner of the
// focused launcher item, seen through BannerCamera. Port of /work/3ds/web/app/src/menu/top
// (scene3d.ts, banner.ts, banners.ts, nametex.ts).
#pragma once

#include <memory>
#include <string>
#include <vector>

#include "NanoCtr3d.h"
#include "NanoCtrLayout.h"

namespace android {
namespace ctr {

struct TopFocus {
    enum Kind { None, Empty, Folder, Title, Bar } kind = None;
    int bar = 0;              // Bar: applet bar button
    std::string key;          // identity of the focused item
    std::string plate;        // name plate text (lines split by '\n')
    std::string cbmdPath;     // Title: a 3DS ROM whose CBMD banner to show (empty = 2D banner)
};

class CtrTop {
public:
    explicit CtrTop(Assets& assets);
    ~CtrTop();
    bool load();
    void releaseGl();
    void sceneIn();
    void focus(const TopFocus& f);
    void update();            // one 60 Hz frame
    // Draws the scene into the top screen rectangle of the bound framebuffer. `proj` maps the
    // 400x240 layout space (as for the layouts); vp is the GL viewport it targets.
    void draw(const float proj[16], int vpX, int vpY, int vpW, int vpH, float width = 400);
    // Internal resolution cap of the 3D target in multiples of 400x240 (default 3).
    void setMaxScale(int s) { mMaxScale = s; }

private:
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
        bool shown = true;
        int scaleStep = 4;
        int turnFrame = 0;
        bool gone() const { return !shown && scaleStep == 0; }
        void update();
    };
    std::shared_ptr<ModelResource> resource(const std::string& name);
    std::shared_ptr<GpuTex> plateTexture(const std::string& text, float size);
    void attach();

    Assets& mAssets;
    Renderer3d mRenderer;
    Camera3d mCamera;
    std::unique_ptr<ModelInstance> mWall;
    std::unique_ptr<Banner> mBanner;
    std::vector<std::unique_ptr<Banner>> mLeaving;
    std::map<std::string, std::shared_ptr<ModelResource>> mResources;
    std::string mWantedKey;
    TopFocus mPendingFocus;
    BannerSpec mPendingSpec;
    bool mHasPending = false;
    int mPendingLeft = 0;
    bool mAttachedOnce = false;
    int mMaxScale = 3;
    std::shared_ptr<GpuTex> mPlate;
    // composite
    GLuint mBlitProgram = 0;
    GLint mBlitProj = -1;
};

}  // namespace ctr
}  // namespace android
