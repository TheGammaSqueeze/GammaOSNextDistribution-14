// NanoCtrSetup - the 3DS theme's first-run setup scene: the console's own layouts on the top
// screen only, at their native size. The HOME Menu's light wall (common/CmnFade_U_00: BgLgt
// under BgLine, the lines drifting) is the ground; each step has its title and explanation on
// the left in the console font, and its interactive part on the right: the settings panel's
// list rows (petit/PtBtnS, bare, no window) or the message dialog (dialog/Dlg_A_D_0x with its
// wait body) at 100 percent. The finish is the celebratory dialog (dialog_happy). Everything
// comes from its own Assets so the HOME Menu (3D wall, banners, grids) is never loaded while
// setup.sh provisions the device: on a 1 GB dual screen device that memory and CPU belong to
// the install.
#pragma once

#include <memory>
#include <string>
#include <vector>

#include "NanoCtrAudio.h"
#include "NanoCtrLayout.h"
#include "NanoCtrPanel.h"

namespace android {
namespace ctr {

class CtrSetupScene {
public:
    explicit CtrSetupScene(std::string assetRoot);
    ~CtrSetupScene();
    bool load();
    bool loaded() const { return mWall != nullptr; }
    void releaseGl();

    CtrPanel& panel() { return *mPanel; }
    CtrDialog& dialog() { return *mDialog; }
    void play(const char* sound);

    // one 60 Hz frame: the widgets' animations and the wall's drift
    void tick();

    // the wall's opacity (the Earth globe shows through as it fades out on the time zone step)
    void setBackdropAlpha(float a) { mBackdropAlpha = a; }

    // Text slots in the console font, positioned in layout units (origin the screen centre,
    // y up). `align` 0 left (x is the left edge), 1 centre, 2 right (x is the right edge);
    // y is the line's centre. `width` is the box the text wraps in (0 = no wrap). Empty text
    // hides the slot.
    enum Slot { Title = 0, Desc, Hint, Greeting, Lang, Zone, SlotCount };
    void setText(Slot slot, const std::string& text, float x, float y, float width, int align, float scale,
                 float alpha, bool soft);

    // the celebratory dialog (the console's "new software" notice) for the finish
    void showHappy(const std::string& appTitle, const std::string& text);
    void hideHappy();
    bool happyActive() const { return mHappyPhase != Hidden; }

    // The GammaOS mark (the boot splash's) as a GL texture for the host to draw.
    unsigned markTexture(int* w, int* h) const;

    // Whether the console font (with its regional fallbacks) has every character of `text`
    // (it has no Arabic, for one: the host draws such a text with nano's own font).
    bool canDraw(const std::string& text) const;

    // Where the 320 x 240 panel and dialog spaces sit on the top screen: centre in layout units
    // and a magnification. The panel is also given a width (its layouts adapt to it) and the window it is cut to,
    // bottom and top in its own units.
    void setPanelPlacement(float cx, float cy, float scale, float width, float clipBottom, float clipTop);
    void setDialogPlacement(float cx, float cy, float scale, float maskWidth);

    // The frame in three parts so the host can draw the globe under the widgets and the list
    // fade over them: `proj` maps the lw x lh layout space (the top screen widened to the panel).
    void drawBackdrop(const float proj[16], int vpX, int vpY, int vpW, int vpH, float lw, float lh);
    void drawWidgets(const float proj[16], int vpX, int vpY, int vpW, int vpH, float lw, float lh);
    // the texts and the step dots (`dots` of them, `dot` the filled one; x, y the row's centre)
    void drawTexts(const float proj[16], int vpX, int vpY, int vpW, int vpH, float lw, float lh, int dots, int dot,
                   float dotX, float dotY, float dotAlpha);

private:
    enum Phase { Hidden, Opening, Shown, Closing };
    void widen(float lw, float lh);
    static void compose(float out[16], const float proj[16], float cx, float cy, float s);
    Pane* slotPane(Slot s) const;

    std::unique_ptr<Assets> mAssets;
    std::unique_ptr<CtrAudio> mAudio;
    std::unique_ptr<CtrPanel> mPanel;
    std::unique_ptr<CtrDialog> mDialog;
    std::unique_ptr<LayoutInstance> mWall;      // the light wall
    std::unique_ptr<LayoutInstance> mText[2];   // two copies of a text layout: six text panes
    std::unique_ptr<LayoutInstance> mHappy;
    TexturePtr mMark;
    float mBackdropAlpha = 1;
    float mLw = 400, mLh = 240;
    float mDrift = 0;
    QuadUv mLineUv{};          // the line sheet's file coordinates (the drift is added to them)
    bool mHaveLineUv = false;
    float mPanelCx = 0, mPanelCy = 0, mPanelScale = 1, mPanelWidth = 320;
    float mPanelClipBottom = -80, mPanelClipTop = 120;
    float mDialogCx = 0, mDialogCy = 0, mDialogScale = 1, mDialogMask = 320;
    Phase mHappyPhase = Hidden;
    int mHappyFrame = 0;
    std::string mHappyTitle, mHappyText;
    struct TextPose { std::string text, wrapped; float x = 0, y = 0, width = 0, scale = 1, alpha = 0; int align = 0; bool soft = false; };
    TextPose mSlots[SlotCount];
};

}  // namespace ctr
}  // namespace android
