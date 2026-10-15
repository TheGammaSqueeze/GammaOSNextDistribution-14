// NanoCtrMyMenu - Save/Load Layout (the HOME Menu's My Menu, scene 0x24) for the nano 3DS theme.
//
// Port of /work/3ds/web/app/src/menu/scenes/mymenu (scene.ts, store.ts, thumbnail.ts): the
// current layout on the top screen and eight saved layouts on the bottom screen, each shown as
// a thumbnail of both screens. X saves into the selected slot (asking before an overwrite), Y
// loads a slot (asking unless the current layout was saved this visit), A or a second tap zooms
// in on a used slot, where L / R or a drag page through the used slots and the corner button
// deletes one. Timings are frames at 60 fps, as in the web app.
#pragma once

#include <array>
#include <functional>
#include <map>
#include <memory>
#include <string>
#include <vector>

#include "NanoCtrLayout.h"
#include "NanoCtrPanel.h"

namespace android {
namespace ctr {

// The layouts a slot keeps, kept by the host (the grid file, the theme, the zoom).
class MyMenuStore {
public:
    virtual ~MyMenuStore() = default;
    virtual bool created() = 0;            // the management file exists (else the first visit makes it)
    virtual void create() = 0;
    virtual bool used(int slot) = 0;
    virtual bool shuffle(int slot) = 0;    // the slot's theme is Shuffle
    virtual bool currentShuffle() = 0;
    virtual bool save(int slot) = 0;       // the current layout into the slot
    virtual void remove(int slot) = 0;
    virtual bool apply(int slot) = 0;      // the slot becomes the current layout
    virtual std::string thumbDir() = 0;    // where the slots' thumbnails are kept
};

struct MyMenuInput {
    uint32_t held = 0;                    // CTR_* buttons
    bool home = false;
    bool touchDown = false, touchHeld = false, touchUp = false;
    float x = 0, y = 0;                   // bottom screen layout space (y up, origin centre)
};

class CtrMyMenu {
public:
    static constexpr int kSlots = 8;
    CtrMyMenu(Assets& assets, SoundFn sound, MyMenuStore* store);
    ~CtrMyMenu();
    // Starts the scene with the current layout's thumbnails (textures the scene keeps).
    bool open(TexturePtr currentTop, TexturePtr currentBottom);
    bool active() const { return mActive; }
    bool entered() const { return mActive && mState != Idle; }   // shown (the status bar is back)
    // The scene draws over both screens (the home underneath need not be drawn).
    bool coversTop() const;
    bool coversBottom() const;
    void tick(const MyMenuInput& in);
    // The screens' layout sizes (wide and tall panels): the backgrounds and belt widen, the slots
    // stay centred, as the home's own layouts adapt.
    void setScreens(float topW, float topH, float botW);
    void drawTop(Renderer& r);
    void drawBottom(Renderer& r);
    void releaseGl();
    // Called when the scene is gone: `loaded` when it left through a load (the host rebuilt the
    // grid under its screen while it faded).
    std::function<void(bool loaded)> onClose;
    // A load applied the slot: the home rebuilds its grid now (the scene still covers it).
    std::function<void()> onLoaded;

private:
    enum State {
        Idle = 0, Creating = 0x08, Enter = 0x0a, Main = 0x0b, ZoomIn = 0x0c, ArrowsIn = 0x0d, Zoomed = 0x0e,
        PagePrevious = 0x0f, PageNext = 0x10, Drag = 0x11, ZoomOut = 0x12, ArrowsOut = 0x13, SaveConfirm = 0x14,
        Save = 0x15, SaveArrowsOut = 0x16, LoadConfirm = 0x18, Load = 0x19, DeleteConfirm = 0x1d, Delete = 0x1e,
        DeleteArrowsOut = 0x1f, Exit = 0x21,
    };
    enum BarMode { BarUsed, BarEmpty, BarZoomed };
    enum ArrowSet { Previous = 0, Next = 1, Both = 2 };
    enum BarKind { Back, LoadB, SaveB, Zoom, DeleteB };
    struct BarButton { BarKind kind; const char* group; const char* bounds; uint32_t key; const char* sound; bool valid; };
    struct Pair {   // a MyMenuBtn_D_00 on both screens and their MyMenuRandom marks
        std::unique_ptr<LayoutInstance> down, up, randomDown, randomUp;
    };
    struct Clip { LayoutInstance* layout; std::string id; };   // a played binding
    struct Attached { Pane* host; LayoutInstance* child; };

    bool load();
    void prepare();
    void enter(State s);
    void updateMain();
    void updateZoomed();
    bool moveCursor(uint32_t mask);
    void select(int slot, const char* sound);
    void placeCursor();
    bool handleTouch();
    int hitBar(float x, float y);
    int hitSlot(float x, float y);
    int hitArrow(float x, float y);
    bool handleBarKeys();
    const char* selectClip(int index) const;
    void decideBar(int index);
    void decideSlot(int slot);
    void decideArrow(int index);
    void startZoom(int slot);
    void enterZoomIn();
    void enterZoomOut();
    ArrowSet arrowsFor(int slot) const;
    void playArrows(const char* clip, ArrowSet set);
    void setArrows(ArrowSet set, bool valid);
    void startPage(int direction);
    void moveArrows(int target);
    bool trackDrag();
    void updateDrag();
    void moveDrag(float dx);
    void pageScrollSound();
    void placeDrag();
    void releaseDrag();
    void endDrag(int target);
    void restoreSlot(int slot);
    void finishPage();
    void confirm(const char* message, const char* thumbs, State next);
    void updateConfirm();
    void enterSave();
    void startSaveFlight(bool afterZoom);
    void updateSave();
    void enterDelete();
    void finishDelete();
    void enterLoad();
    void updateLoad();
    void updateCreating();
    void updateBar(BarMode mode, bool animate = true, bool enabled = true, bool reset = false);
    void setSlotLook(int slot, bool used, bool animate = true);
    void fadeOthers(int slot, const char* used, const char* empty);
    void showRandom(int index);
    void setSlotImages(int slot, bool used);
    void applyTextures(int index);
    void setThumbnails(LayoutInstance* l, const char* topPane, const char* bottomPane, int index);
    TexturePtr thumb(int index, bool top);
    bool saveThumbs(int slot);
    TexturePtr loadThumb(int slot, bool top);
    std::array<LayoutInstance*, 2> screens(int index);
    std::array<LayoutInstance*, 2> layoutScreens();
    std::vector<int> usedSlots() const;
    void setVisible(bool v);
    void toFront(int slot);
    void attach(int index, const char* host);
    void attachTo(LayoutInstance* hostLayout, const char* host, LayoutInstance* child, std::vector<Attached>& list);
    void detach(LayoutInstance* child, std::vector<Attached>& list);
    void calculate();
    bool clipsDone() const;
    void play(LayoutInstance* l, const std::string& name, const char* group, const char* key = nullptr);
    void seek(LayoutInstance* l, const std::string& name, const char* group, bool end, const char* key = nullptr);
    std::string bindingId(LayoutInstance* l, const std::string& name, const char* group, const char* key) const;
    void drawComposed(Renderer& r, LayoutInstance* root, const std::vector<Attached>& list);
    void drawTree(Renderer& r, Layout& L, Pane* p, const std::vector<Attached>& list);
    void sound(const char* name) { if (mSound) mSound(name); }

    Assets& A;
    SoundFn mSound;
    MyMenuStore* mStore;
    bool mLoadedLayouts = false, mActive = false;
    std::unique_ptr<LayoutInstance> mDown, mUp, mCursor, mBar, mWait, mDlgContent;
    std::vector<Pair> mButtons;   // kSlots slots, FLYING, CURRENT, SPARE
    std::vector<Attached> mDownParts, mUpParts;
    std::unique_ptr<CtrDialog> mDialog;
    std::vector<BarButton> mBarButtons;
    bool mArrowValid[2] = {false, false};
    std::map<std::string, TexturePtr> mOriginals;
    TexturePtr mCurrentTop, mCurrentBottom;
    std::array<TexturePtr, kSlots> mSlotTop, mSlotBottom;
    State mState = Idle;
    int mFrame = 0, mStage = 0;
    std::vector<Clip> mClips;
    bool mSpareTakesOver = false;
    int mSlot = 0, mZoomSlot = 0;
    bool mZoomed = false, mSavedThisVisit = false, mExitRequested = false, mLoaded = false;
    ArrowSet mArrowSet = Both;
    int mPressedKind = 0, mPressedIndex = -1;   // 0 none, 1 bar, 2 slot, 3 arrow
    State mConfirmNext = Main;
    int mConfirmSel = 1;
    int mPageTarget = -1;
    bool mDragOn = false, mDragActive = false;
    float mDragX = 0, mDragStart = 0, mDragOffset = 0;
    int mDragNeighbour = -1;
    bool mSettling = false;
    float mSettleTo = 0;
    // scroll ticks (scroll/sound.ts)
    float mScrollLast = 0, mScrollTickAt = 0;
    int mScrollFrames = 0;
    void scrollReset(float p) { mScrollLast = mScrollTickAt = p; mScrollFrames = 0; }
    void scrollUpdate(float p, float lo, float hi);
    // input edges
    uint32_t mPrevHeld = 0, mPressed = 0, mRepeat = 0;
    int mRepeatFrames[16] = {0};
    MyMenuInput mIn;
};

}  // namespace ctr
}  // namespace android
