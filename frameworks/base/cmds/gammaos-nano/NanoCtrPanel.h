// NanoCtrPanel - the 3DS HOME Menu Settings panel ("petit") and message dialog, as views over
// nano's own menus: the theme draws nano's settings lists, option menus and dialogs with the
// console's layouts instead of the XMB chrome.
//
// CtrPanel follows web theme/settings/panel.ts + panellist.ts: PtDlgBg_D_00 on the bottom screen
// with the rows, PtSlideBar, PtCsr_00 and PtClose_00, the PtDlgBg_U_00 title band on the top
// screen and DlgMask_D_00 dimming the home. Rows are the panel's part layouts: PtBtnS_Cmn_00
// buttons (actions, submenus), PtBtnT_Abl_00 headers with the OFF / ON pair (switches) and a
// header over a button that shows the current value (choices). The model (rows, selection) is
// nano's: keys move nano's selection and the panel follows it with the console's cursor and
// scroll rules; touch reports what was hit and nano acts on it.
//
// CtrDialog follows scenes/system/dialog.ts: Dlg_A_D_00..02 by button count with the
// DlgMask_U_00 / DlgMask_D_00 dimmers, FadeIn 21 frames, Select / Decide, FadeOut.
#pragma once

#include <cstdint>
#include <functional>
#include <memory>
#include <string>
#include <vector>

#include "NanoCtrLayout.h"

namespace android {
namespace ctr {

struct PanelRow {
    enum Kind : uint8_t { Button = 0, Toggle = 1, Value = 2 };
    Kind kind = Button;
    std::string label;   // button text, or the header of a switch / choice
    std::string value;   // the current choice (Value rows)
    bool on = false;     // the switch state (Toggle rows)
    bool enabled = true;
    bool operator==(const PanelRow& o) const {
        return kind == o.kind && label == o.label && value == o.value && on == o.on && enabled == o.enabled;
    }
    bool operator!=(const PanelRow& o) const { return !(*this == o); }
};

// What a touch on the bottom screen hit.
struct PanelHit {
    enum What { None, Row, Close, Outside } what = None;
    int row = -1;
    int column = -1;   // Toggle rows: 0 OFF, 1 ON
};

using SoundFn = std::function<void(const char*)>;

class CtrPanel {
public:
    CtrPanel(Assets& assets, SoundFn sound);
    ~CtrPanel();

    // The model, every frame while shown. `level` identifies the list: a new one starts at the
    // top with the cursor on `sel`; in the same list a moved selection moves the cursor (and
    // scrolls it into view like the console's D-pad).
    void set(const std::string& title, const std::vector<PanelRow>& rows, int sel, uint64_t level);
    void show();
    void hide();
    bool active() const { return mPhase != Hidden; }
    bool closing() const { return mPhase == Closing; }
    void setWidth(float bottomWidth) { mWidth = bottomWidth; }
    void setTopSize(float w, float h) { mTopW = w; mTopH = h; }
    void tick();   // one 60 Hz frame

    // Touch in bottom screen layout units (origin at the centre, y up). Drags scroll the list,
    // a tap reports the row (or the close button / the dimmed area around the panel).
    PanelHit touch(bool down, bool held, bool up, float x, float y);

    void drawBottom(Renderer& r);
    void drawTop(Renderer& r);
    void releaseGl();

private:
    struct Slot {
        std::unique_ptr<LayoutInstance> lay;
        std::unique_ptr<LayoutInstance> lay2;   // Value rows: the button under the header
        int row = -1;
        PanelRow shown;
        bool fresh = true;
    };
    enum Phase { Hidden, Opening, Shown, Closing };

    bool load();
    void layoutRows();
    float rowHeight(const PanelRow& r) const;
    void assignSlots();
    void bindSlot(Slot& s, const PanelRow& row);
    void placeSlot(Slot& s);
    void stopRect(int row, float* x, float* y, float* w, float* h) const;   // N_Wrp_00 space
    void placeCursor();
    void moveBy(float d);
    void applyScroll();
    float knobY() const;
    bool hit(LayoutInstance* l, const char* pane, float x, float y) const;
    void decided(Slot& s);

    Assets& A;
    SoundFn mSound;
    bool mLoaded = false, mLoadFailed = false;
    std::unique_ptr<LayoutInstance> mBg, mTop, mMask, mSlider, mCursor, mClose;
    std::vector<Slot> mButtons, mToggles, mValues;
    std::vector<PanelRow> mRows;
    std::vector<float> mRowTop, mRowH;   // distance below the list top, height
    std::string mTitle;
    uint64_t mLevel = ~0ull;
    int mSel = 0;
    float mWidth = 320, mTopW = 400, mTopH = 240;
    Phase mPhase = Hidden;
    int mPhaseFrame = 0;
    // scrolling (panellist.ts)
    float mScroll = 0, mMax = 0;
    int mScrollFrames = 0, mScrollCount = 0;
    float mScrollFrom = 0, mScrollTo = 0;
    enum Touch { TIdle, TPressed, TDrag, TInertia, TKnob } mTouch = TIdle;
    float mLastY = 0, mVelocity = 0, mGrab = 0;
    bool mPressOnRow = false;
    PanelHit mPressHit;
    bool mCursorShown = true;
    float mCurX = 0, mCurY = 0, mCurW = 0, mCurH = 0;   // cursor box (list space, scroll removed)
    float mKnobCenter = 0, mKnobTravel = 0;
    Animator* mPlates[3] = {nullptr, nullptr, nullptr};
    float mLastScrollSound = 0;
};

class CtrDialog {
public:
    CtrDialog(Assets& assets, SoundFn sound);
    ~CtrDialog();
    // The text and the buttons (at most two are drawn as buttons) every frame while shown; `sel`
    // is nano's selected button, held in the Select look.
    void set(const std::string& text, const std::vector<std::string>& buttons, int sel);
    void setWidth(float bottomWidth) { mWidth = bottomWidth; }
    void setTopSize(float w, float h) { mTopW = w; mTopH = h; }
    void show();
    void hide(bool cancel);
    bool active() const { return mPhase != Hidden; }
    void tick();
    // Index of the button under a tap (-1 none, -2 outside the window).
    int touch(bool down, bool up, float x, float y);
    void drawBottom(Renderer& r);
    void drawTop(Renderer& r);
    void releaseGl();

private:
    enum Phase { Hidden, Opening, Shown, Closing };
    bool load(int buttons);
    Assets& A;
    SoundFn mSound;
    std::unique_ptr<LayoutInstance> mLay, mMaskTop, mMaskBottom;
    int mLayButtons = -1;
    std::string mText;
    std::vector<std::string> mButtonText;
    int mSel = -1, mShownSel = -2;
    Phase mPhase = Hidden;
    int mPhaseFrame = 0;
    int mTouched = -1;
    float mWidth = 320, mTopW = 400, mTopH = 240;
};

}  // namespace ctr
}  // namespace android
