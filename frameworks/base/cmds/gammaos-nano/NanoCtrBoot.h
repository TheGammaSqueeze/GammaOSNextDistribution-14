// NanoCtrBoot - the 3DS theme's cold boot splash.
//
// The console's launch splash (romfs lyt/logo: NintendoLogo_U_00 with SceneOutA, SceneOutB and
// SceneOutC, 3ds-web docs/porting.md section 6) carrying the GammaOS logo (tools/ctr_boot_logo.py
// packs ctr3ds/data/lyt_logo.cpk). nano's boot clock poses it (NanoMenuPS3Boot.cpp ps3BootUpdate
// runs the phases, NanoMenuCtr.cpp draws it): black, SceneOutA with SE_CTR_HOME_START, SceneOutB
// looping while the notice waits to be dismissed, SceneOutC fading it out, then the HOME Menu.
#pragma once

#include <memory>
#include <string>
#include <vector>

#include "NanoCtrLayout.h"

namespace android {
namespace ctr {

// Boot timeline, frames at 60 fps from power on.
constexpr double kBootLogoStart = 54;    // black until the splash (logo.ts LOGO_START_FRAMES)
constexpr double kBootSceneA = 60;       // SceneOutA: the wordmark slides in, the mark scales in
constexpr double kBootSceneB = 30;       // SceneOutB: the hold, looped (the sweeps keep cycling)
constexpr double kBootSceneC = 15;       // SceneOutC: the logo fades out
constexpr double kBootNoticeFade = 23;   // the notice fades in once SceneOutA is done
constexpr double kBootPrompt = kBootLogoStart + kBootSceneA + kBootNoticeFade;   // dismissal accepted
constexpr double kBootEnterEnd = kBootSceneC + 5;   // from the dismissal to the HOME Menu

class CtrBootSplash {
public:
    explicit CtrBootSplash(std::string assetRoot);
    ~CtrBootSplash();
    // Loads the top screen layout (needs the GL context current). False when the archive is missing.
    bool load();
    bool loaded() const { return mTop != nullptr; }
    // Poses the splash for boot frame `f`; `leaveAt` is the frame the notice was dismissed
    // (SceneOutC plays from there) or negative while it waits. False while nothing shows.
    bool pose(double f, double leaveAt);
    // Draws the top screen, laid out lw x lh units (at least 400 x 240: the sweeps widen to it),
    // which covers screenW x screenH pixels of the panel.
    void draw(const float proj[16], int vpX, int vpY, int vpW, int vpH, float lw, float lh, float screenW,
              float screenH);
    void releaseGl();

private:
    enum Scene { None = -1, SceneA, SceneB, SceneC };
    void enter(Scene s);
    void widen(float lw, float lh);
    std::unique_ptr<Assets> mAssets;
    std::unique_ptr<LayoutInstance> mTop;
    Scene mScene = None;
    Animator* mAnim = nullptr;
    // the full screen sweep panes and their texture coordinates as designed (400 x 240)
    struct Sweep { Pane* pane; std::vector<QuadUv> uv; };
    std::vector<Sweep> mSweeps;
    float mLw = 400, mLh = 240;
    // the sweeps drawn at a reduced resolution (draw): their subtrees, the buffer, its blit
    bool sweepBuffer(int w, int h);
    std::vector<Pane*> mSweepRoots, mLogoPanes;
    GLuint mFbo = 0, mFboTex = 0, mBlitProgram = 0;
    GLint mBlitProj = -1, mBlitHalf = -1;
    int mFboW = 0, mFboH = 0;
};

}  // namespace ctr
}  // namespace android
