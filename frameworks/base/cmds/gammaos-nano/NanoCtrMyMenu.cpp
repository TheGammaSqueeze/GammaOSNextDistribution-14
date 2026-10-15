// NanoCtrMyMenu - Save/Load Layout (My Menu) for the nano 3DS theme. See NanoCtrMyMenu.h.
#include "NanoCtrMyMenu.h"
#include "NanoCtrHome.h"   // CTR_* buttons

#include <GLES2/gl2.h>
#include <sys/stat.h>
#include <unistd.h>
#include <zlib.h>

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>

#include <log/log.h>

#undef LOG_TAG
#define LOG_TAG "NanoCtr"

namespace android {
namespace ctr {

namespace {

const char* kArchive = "MyMenu";
// button layouts: the 8 slots, the flying copy (N_Thumb_08), the current layout (N_Thumb), a spare
constexpr int FLYING = 8, CURRENT = 9, SPARE = 10, BUTTONS = 11;
constexpr int LOAD_FRAMES = 84;          // request until MyMenuIn (MyMenu_LZ.bin + MngFile.dat read)
constexpr int CREATE_FRAMES = 100;       // "Creating ... management information" (state 8)
constexpr int SAVE_MOVE_FRAME = 0x32, SAVE_DOWN_FRAME = 0x94;
constexpr int SAVE_FRAMES = 200;         // the management file write outlasts the flight
constexpr int ZOOMED_SAVE_FRAMES = 0x1e;
constexpr int LOAD_WAIT_FRAMES = 46;
constexpr int WAIT_IN_FRAMES = 21;
constexpr int ROW_SLOTS = 4;
constexpr float PAGE_DISTANCE = 380, PAGE_THRESHOLD = 100;
constexpr float DRAG_START = 5, SETTLE_SPEED = 5.88f;
const char* kLook = "look";
const char* kThumbPanes[4] = {"Thumb_U_00", "Thumb_D_00", "Thumb_U_01", "Thumb_D_01"};
struct ArrowDef { const char* group; const char* bounds; uint32_t keys; };
const ArrowDef kArrows[2] = {{"G_Btn_Rev", "B_ArrowL", CTR_LEFT | CTR_L}, {"G_Btn_Next", "B_ArrowR", CTR_RIGHT | CTR_R}};

std::string pad2(int n) {
    char b[8];
    snprintf(b, sizeof(b), "%02d", n);
    return b;
}
std::string slotPane(int slot) { return "N_Thumb_" + pad2(slot); }

bool hit(LayoutInstance* l, const char* name, float x, float y) {
    Pane* p = l ? l->pane(name) : nullptr;
    if (!p) return false;
    // as hit.ts hitPane: by the pane's box alone (bounding panes are hidden but still hit)
    const float* m = p->global.m;
    float l0, t, r, b;
    p->rect(&l0, &t, &r, &b);
    const float x0 = m[12] + l0 * m[0], x1 = m[12] + r * m[0];
    const float y0 = m[13] + t * m[5], y1 = m[13] + b * m[5];
    return x >= std::min(x0, x1) && x <= std::max(x0, x1) && y >= std::min(y0, y1) && y <= std::max(y0, y1);
}

bool covers(int set, int side) { return set == 2 || set == side; }

}  // namespace

CtrMyMenu::CtrMyMenu(Assets& assets, SoundFn sound, MyMenuStore* store) : A(assets), mSound(std::move(sound)), mStore(store) {
    mBarButtons = {
        {Back, "G_Btn_00", "B_Btn_00", CTR_B, "SE_CTR_COMMON_RETURN", true},
        {LoadB, "G_Btn_01", "B_Btn_01", CTR_Y, "SE_CTR_COMMON_BUTTON", true},
        {SaveB, "G_Btn_02", "B_Btn_02", CTR_X, "SE_CTR_COMMON_BUTTON", true},
        {Zoom, "G_Btn_03", "B_Btn_03", CTR_A, "SE_CTR_COMMON_TOGGLE", true},
        {DeleteB, "G_Btn_04", "B_Btn_04", 0, "SE_CTR_COMMON_BUTTON", true},
    };
}

CtrMyMenu::~CtrMyMenu() { releaseGl(); }

void CtrMyMenu::releaseGl() {
    mActive = false;
    mDialog.reset();
    mDownParts.clear();
    mUpParts.clear();
    mButtons.clear();
    mDlgContent.reset();
    mWait.reset();
    mBar.reset();
    mCursor.reset();
    mDown.reset();
    mUp.reset();
    mOriginals.clear();
    mCurrentTop.reset();
    mCurrentBottom.reset();
    for (auto& t : mSlotTop) t.reset();
    for (auto& t : mSlotBottom) t.reset();
    mClips.clear();
    mSpareTakesOver = false;
    mLoadedLayouts = false;
}

// ------------------------------------------------------------------ setup

bool CtrMyMenu::load() {
    if (mLoadedLayouts) return true;
    mDown = A.create(kArchive, "MyMenu_D_00");
    mUp = A.create(kArchive, "MyMenu_U_00");
    mCursor = A.create(kArchive, "MyMenuCsr_00");
    mBar = A.create(kArchive, "MyMenuBtmBtn_D_00");
    mDlgContent = A.create(kArchive, "MyMenuDlg_00");
    mWait = A.create("waiticon", "WaitIcon");
    if (!mDown || !mUp || !mCursor || !mBar || !mDlgContent || !mWait) {
        ALOGW("mymenu: layouts missing");
        releaseGl();
        return false;
    }
    mWait->layout().root->visible = false;
    mButtons.resize(BUTTONS);
    for (int i = 0; i < BUTTONS; i++) {
        Pair& p = mButtons[i];
        p.down = A.create(kArchive, "MyMenuBtn_D_00");
        p.up = A.create(kArchive, "MyMenuBtn_D_00");
        p.randomDown = A.create(kArchive, "MyMenuRandom");
        p.randomUp = A.create(kArchive, "MyMenuRandom");
        if (!p.down || !p.up || !p.randomDown || !p.randomUp) {
            releaseGl();
            return false;
        }
        const std::string host = i == CURRENT ? "N_Thumb" : slotPane(i == SPARE ? 0 : i);
        attachTo(mDown.get(), host.c_str(), p.down.get(), mDownParts);
        attachTo(mUp.get(), host.c_str(), p.up.get(), mUpParts);
        attachTo(p.down.get(), "N_Icon_Random", p.randomDown.get(), mDownParts);
        attachTo(p.up.get(), "N_Icon_Random", p.randomUp.get(), mUpParts);
        if (i == SPARE) p.down->layout().root->visible = p.up->layout().root->visible = false;
        // the empty slot's plates, put back when a slot is emptied
        for (LayoutInstance* l : {p.down.get(), p.up.get()}) {
            for (const char* name : kThumbPanes) {
                Pane* pane = l->pane(name);
                if (!pane || pane->material < 0 || pane->material >= (int)l->layout().materials.size()) continue;
                const auto& maps = l->layout().materials[pane->material].texMaps;
                if (!maps.empty()) mOriginals[std::string(name)] = maps[0].texture;
            }
        }
    }
    {
        for (const char* name : kThumbPanes) {
            Pane* pane = mDlgContent->pane(name);
            if (!pane || pane->material < 0 || pane->material >= (int)mDlgContent->layout().materials.size()) continue;
            const auto& maps = mDlgContent->layout().materials[pane->material].texMaps;
            if (!maps.empty() && !mOriginals.count(name)) mOriginals[name] = maps[0].texture;
        }
    }
    mUp->setMessage("TextBox_00", "mhm_current_u");
    mUp->setMessage("TextBox_01", "mhm_title_u");
    mBar->setMessage("T_BtnB_00", "mhm_load_4b");
    mBar->setMessage("T_BtnF_00", "mhm_load_4b");
    mDialog = std::make_unique<CtrDialog>(A, mSound);
    mLoadedLayouts = true;
    return true;
}

void CtrMyMenu::prepare() {
    PlayOptions loop;
    loop.groups = 1;
    mCursor->play("Loop", loop);
    seek(mBar.get(), "BtnIn2", "G_Scene_01", false);
    for (LayoutInstance* l : screens(CURRENT)) seek(l, "MyMenuIn", "G_Btn_00", false);
    for (LayoutInstance* l : layoutScreens()) seek(l, "MyMenuIn", "G_MyMenu_00", false);
    seek(mUp.get(), "MyMenuIn", "G_Belt", false);
    seek(mBar.get(), "BtnIn", "G_Scene_00", false);
    for (int s = 0; s < kSlots; s++) showRandom(s);
    showRandom(CURRENT);
    showRandom(FLYING);
    updateBar(mStore->used(mSlot) ? BarUsed : BarEmpty, false);
    for (int s = 0; s < kSlots; s++) setSlotLook(s, mStore->used(s), false);
    calculate();
}

bool CtrMyMenu::open(TexturePtr currentTop, TexturePtr currentBottom) {
    if (mActive || !mStore) return false;
    // every visit starts from fresh layouts: the last one's clips (a load fades the backgrounds
    // out, an exit plays MyMenuOut) leave their panes where they ended
    releaseGl();
    if (!load()) return false;
    mCurrentTop = std::move(currentTop);
    mCurrentBottom = std::move(currentBottom);
    for (int s = 0; s < kSlots; s++) {
        mSlotTop[s].reset();
        mSlotBottom[s].reset();
    }
    mState = Idle;
    mFrame = 0;
    mStage = 0;
    mClips.clear();
    mSpareTakesOver = false;
    mZoomed = mSavedThisVisit = mExitRequested = mLoaded = false;
    mPressedKind = 0;
    mDragOn = mSettling = false;
    mDragOffset = 0;
    mDragNeighbour = -1;
    mPrevHeld = ~0u;   // a button held while the scene opens counts once released
    mSlot = 0;
    for (int i : {FLYING, CURRENT, SPARE}) applyTextures(i);
    for (int s = 0; s < kSlots; s++) applyTextures(s);
    prepare();
    setVisible(false);
    mActive = true;
    return true;
}

// ------------------------------------------------------------------ frame

void CtrMyMenu::tick(const MyMenuInput& in) {
    if (!mActive) return;
    mIn = in;
    // triggers and repeats (input.ts: first repeat at 20 frames, then every 5)
    const uint32_t held = in.held;
    // the frame the scene opens counts nothing as pressed (the key that opened it is held)
    mPressed = mPrevHeld == ~0u ? 0 : held & ~mPrevHeld;
    mRepeat = 0;
    for (int b = 0; b < 16; b++) {
        const uint32_t bit = 1u << b;
        if (!(held & bit)) { mRepeatFrames[b] = 0; continue; }
        const int f = ++mRepeatFrames[b];
        if (f > 20 && (f - 20) % 5 == 0) mRepeat |= bit;
    }
    mFrame++;
    if (in.home) mExitRequested = true;
    // the layouts advance (index of the clip system: every layout updates once a frame)
    for (LayoutInstance* l : {mDown.get(), mUp.get(), mCursor.get(), mBar.get(), mWait.get(), mDlgContent.get()})
        if (l) l->update(1);
    for (Pair& p : mButtons)
        for (LayoutInstance* l : {p.down.get(), p.up.get(), p.randomDown.get(), p.randomUp.get()}) l->update(1);
    if (mDialog) mDialog->tick();
    switch (mState) {
        case Idle:
            // the saved thumbnails are read while the menu "decompresses" (one slot a frame)
            if (mFrame - 1 < kSlots) setSlotImages(mFrame - 1, mStore->used(mFrame - 1));
            if (mFrame >= LOAD_FRAMES) enter(Enter);
            break;
        case Enter:
            if (clipsDone()) enter(mStore->created() ? Main : Creating);
            break;
        case Creating: updateCreating(); break;
        case Main: updateMain(); break;
        case ZoomIn:
            if (clipsDone()) enter(ArrowsIn);
            break;
        case ArrowsIn:
            if (clipsDone()) {
                setArrows(mArrowSet, true);
                enter(Zoomed);
            }
            break;
        case Zoomed: updateZoomed(); break;
        case PagePrevious:
        case PageNext:
            pageScrollSound();
            if (clipsDone()) finishPage();
            break;
        case Drag: updateDrag(); break;
        case ArrowsOut:
            if (clipsDone()) enter(ZoomOut);
            break;
        case ZoomOut:
            if (clipsDone()) {
                mZoomed = false;
                enter(Main);
            }
            break;
        case SaveArrowsOut:
            if (clipsDone()) enter(Save);
            break;
        case DeleteArrowsOut:
            if (clipsDone()) enter(Delete);
            break;
        case SaveConfirm:
        case LoadConfirm:
        case DeleteConfirm: updateConfirm(); break;
        case Save: updateSave(); break;
        case Delete:
            if (clipsDone()) finishDelete();
            break;
        case Load: updateLoad(); break;
        case Exit:
            if (clipsDone()) {
                mActive = false;
                setVisible(false);
                if (onClose) onClose(mLoaded);
            }
            break;
    }
    if (mState == Drag) placeDrag();
    calculate();
    mPrevHeld = held;
}

void CtrMyMenu::calculate() {
    mDown->layout().calculate();
    mUp->layout().calculate();
    // hosted layouts after their hosts (their roots take the hosts' matrices and alphas)
    for (auto* list : {&mDownParts, &mUpParts})
        for (const Attached& a : *list) a.child->layout().calculate();
    mCursor->layout().calculate();
    mBar->layout().calculate();
    mWait->layout().calculate();
}

// ------------------------------------------------------------------ states

void CtrMyMenu::enter(State s) {
    mState = s;
    mFrame = 0;
    mStage = 0;
    mClips.clear();
    switch (s) {
        case Enter:
            setVisible(true);
            mCursor->layout().root->visible = false;
            play(mDown.get(), "MyMenuIn", "G_MyMenu_00");
            play(mUp.get(), "MyMenuIn", "G_MyMenu_00");
            play(mUp.get(), "MyMenuIn", "G_Belt");
            play(mBar.get(), "BtnIn", "G_Scene_00");
            for (LayoutInstance* l : screens(CURRENT)) play(l, "MyMenuIn", "G_Btn_00");
            sound("SE_CTR_MYMENU_IN");
            break;
        case Creating:
            // "Creating HOME Menu layout management information..." over a turning wait icon
            mDialog->set("", {}, -1);
            mDialog->setContent(nullptr);
            mDialog->setWait(A.messageText("lau_dlg_mhm_create_data_sac"));
            mDialog->show();
            break;
        case Main:
            mExitRequested = false;
            for (int slot = 0; slot < kSlots; slot++) setSlotLook(slot, mStore->used(slot));
            updateBar(mStore->used(mSlot) ? BarUsed : BarEmpty, true, true, true);
            mCursor->layout().root->visible = true;
            placeCursor();
            break;
        case ZoomIn: enterZoomIn(); break;
        case ArrowsIn:
            play(mBar.get(), "BtnIn2", "G_Scene_01");
            mArrowSet = arrowsFor(mZoomSlot);
            if (usedSlots().size() <= 1) {
                updateBar(BarZoomed);
                enter(Zoomed);
                return;
            }
            playArrows("ArrowIn", mArrowSet);
            break;
        case Zoomed: updateBar(BarZoomed); break;
        case ArrowsOut:
        case SaveArrowsOut:
        case DeleteArrowsOut:
            play(mBar.get(), "BtnOut2", "G_Scene_01");
            setArrows(Both, false);
            if (usedSlots().size() <= 1) {
                enter(s == ArrowsOut ? ZoomOut : s == SaveArrowsOut ? Save : Delete);
                return;
            }
            playArrows("ArrowOut", mArrowSet);
            break;
        case ZoomOut: enterZoomOut(); break;
        case SaveConfirm: confirm("lau_dlg_mhm_save", "Thumb2", mZoomed ? SaveArrowsOut : Save); break;
        case LoadConfirm: confirm("lau_dlg_mhm_load", "Thumb1", Load); break;
        case DeleteConfirm: confirm("lau_dlg_mhm_delete", "Thumb1", DeleteArrowsOut); break;
        case Save: enterSave(); break;
        case Delete: enterDelete(); break;
        case Load: enterLoad(); break;
        case Exit:
            mCursor->layout().root->visible = false;
            play(mDown.get(), "MyMenuOut", "G_MyMenu_00");
            play(mUp.get(), "MyMenuOut", "G_MyMenu_00");
            play(mUp.get(), "MyMenuOut", "G_Belt");
            play(mBar.get(), "BtnOut", "G_Scene_00");
            for (LayoutInstance* l : screens(CURRENT)) play(l, "MyMenuOut", "G_Btn_00");
            sound("SE_CTR_MYMENU_OUT");
            break;
        default: break;
    }
}

void CtrMyMenu::updateMain() {
    if (mExitRequested) {
        enter(Exit);
        return;
    }
    placeCursor();
    if (handleTouch()) return;
    if (handleBarKeys()) return;
    const uint32_t keys = mPressed | mRepeat;
    for (uint32_t k : {CTR_RIGHT, CTR_LEFT, CTR_UP, CTR_DOWN})
        if ((keys & k) && moveCursor(k)) break;
}

void CtrMyMenu::updateZoomed() {
    if (mExitRequested) {
        enter(ArrowsOut);
        return;
    }
    if (trackDrag()) return;
    if (handleTouch()) return;
    if (handleBarKeys()) return;
    for (int i = 0; i < 2; i++) {
        if (mArrowValid[i] && ((mPressed | mRepeat) & kArrows[i].keys)) {
            decideArrow(i);
            return;
        }
    }
}

bool CtrMyMenu::moveCursor(uint32_t mask) {
    const int row = mSlot / ROW_SLOTS, column = mSlot % ROW_SLOTS;
    int next = -1;
    if (mask == CTR_RIGHT) next = column + 1 < ROW_SLOTS ? mSlot + 1 : -1;
    else if (mask == CTR_LEFT) next = column > 0 ? mSlot - 1 : -1;
    else if (mask == CTR_UP) next = row > 0 ? mSlot - ROW_SLOTS : -1;
    else if (mask == CTR_DOWN) next = row < 1 ? mSlot + ROW_SLOTS : -1;
    else return false;
    if (next < 0) return true;
    select(next, "SE_CTR_HOME_ICON_SELECT");
    return true;
}

void CtrMyMenu::select(int slot, const char* snd) {
    mSlot = slot;
    updateBar(mStore->used(slot) ? BarUsed : BarEmpty);
    placeCursor();
    sound(snd);
}

void CtrMyMenu::placeCursor() {
    Pane* p = mDown->pane(slotPane(mSlot));
    if (!p) return;
    Pane* root = mCursor->layout().root;
    root->translate[0] = p->translate[0];
    root->translate[1] = p->translate[1];
}

// ------------------------------------------------------------------ input

bool CtrMyMenu::handleTouch() {
    if (mIn.touchDown) {
        mPressedKind = 0;
        int i;
        if ((i = hitBar(mIn.x, mIn.y)) >= 0) {
            mPressedKind = 1;
            mPressedIndex = i;
            play(mBar.get(), selectClip(i), mBarButtons[i].group, kLook);
        } else if ((i = hitSlot(mIn.x, mIn.y)) >= 0) {
            mPressedKind = 2;
            mPressedIndex = i;
            play(mButtons[i].down.get(), "Select", "G_Btn_00", kLook);
        } else if ((i = hitArrow(mIn.x, mIn.y)) >= 0) {
            mPressedKind = 3;
            mPressedIndex = i;
            play(mDown.get(), "Select", kArrows[i].group, kLook);
        }
        return mPressedKind != 0;
    }
    if (!mIn.touchUp || mPressedKind == 0) return mIn.touchHeld && mPressedKind != 0;
    const int kind = mPressedKind, index = mPressedIndex;
    mPressedKind = 0;
    int rk = 0, ri = -1;
    if ((ri = hitBar(mIn.x, mIn.y)) >= 0) rk = 1;
    else if ((ri = hitSlot(mIn.x, mIn.y)) >= 0) rk = 2;
    else if ((ri = hitArrow(mIn.x, mIn.y)) >= 0) rk = 3;
    const bool same = rk == kind && ri == index;
    if (kind == 2) {
        play(mButtons[index].down.get(), "Decide", "G_Btn_00", kLook);
        if (same) decideSlot(index);
    } else if (kind == 1) {
        if (same) decideBar(index);
        else seek(mBar.get(), selectClip(index), mBarButtons[index].group, false, kLook);
    } else if (same) {
        decideArrow(index);
    }
    return true;
}

int CtrMyMenu::hitBar(float x, float y) {
    for (int i = 0; i < (int)mBarButtons.size(); i++) {
        const BarButton& b = mBarButtons[i];
        if (!b.valid || (b.kind == DeleteB && mState != Zoomed)) continue;
        if (hit(mBar.get(), b.bounds, x, y)) return i;
    }
    return -1;
}

int CtrMyMenu::hitSlot(float x, float y) {
    if (mState != Main) return -1;
    for (int s = 0; s < kSlots; s++)
        if (hit(mButtons[s].down.get(), "B_Thumb_00", x, y)) return s;
    return -1;
}

int CtrMyMenu::hitArrow(float x, float y) {
    if (mState != Zoomed) return -1;
    for (int i = 0; i < 2; i++)
        if (mArrowValid[i] && hit(mDown.get(), kArrows[i].bounds, x, y)) return i;
    return -1;
}

bool CtrMyMenu::handleBarKeys() {
    for (int i = 0; i < (int)mBarButtons.size(); i++) {
        const BarButton& b = mBarButtons[i];
        if (b.key == 0 || !(mPressed & b.key)) continue;
        if (b.valid) decideBar(i);
        return true;
    }
    return false;
}

const char* CtrMyMenu::selectClip(int index) const {
    return mBarButtons[index].kind == Zoom && mZoomed ? "Select2" : "Select";
}

void CtrMyMenu::decideBar(int index) {
    const BarButton& b = mBarButtons[index];
    const bool zoomOut = b.kind == Zoom && mState == Zoomed;
    play(mBar.get(), b.kind == Zoom ? (zoomOut ? "Decide" : "Decide2") : "Decide", b.group, kLook);
    sound(b.sound);
    const bool used = mStore->used(mSlot);
    switch (b.kind) {
        case Back:
            enter(mState == Main ? Exit : ArrowsOut);
            return;
        case Zoom:
            if (mState == Main && used) startZoom(mSlot);
            else if (mState == Zoomed) enter(ArrowsOut);
            return;
        case SaveB:
            enter(used ? SaveConfirm : Save);
            return;
        case LoadB:
            if (!used) return;
            enter(mSavedThisVisit ? Load : LoadConfirm);
            return;
        case DeleteB:
            if (mState == Zoomed) enter(DeleteConfirm);
            return;
    }
}

void CtrMyMenu::decideSlot(int slot) {
    if (slot != mSlot) {
        select(slot, "SE_CTR_HOME_ICON_TOUCH");
        return;
    }
    if (mStore->used(slot)) startZoom(slot);
}

void CtrMyMenu::decideArrow(int index) {
    play(mDown.get(), "Decide", kArrows[index].group, kLook);
    scrollReset(0);
    enter(index == 0 ? PagePrevious : PageNext);
    startPage(index == 0 ? -1 : 1);
}

// ------------------------------------------------------------------ zoom

void CtrMyMenu::startZoom(int slot) {
    mZoomSlot = slot;
    enter(ZoomIn);
}

void CtrMyMenu::enterZoomIn() {
    const int slot = mZoomSlot;
    mZoomed = true;
    mCursor->layout().root->visible = false;
    toFront(slot);
    // ThumbInvalid (G_MyMenu_00, children bound) also carries the slots' moves and ends 21
    // frames in, a third short of the zoom: bound first, the slot's own ThumbIn (bound after,
    // applied after) wins, and the zoomed slot ends centred as on the console
    play(mUp.get(), "BeltOut", "G_Belt");
    play(mUp.get(), "ThumbInvalid", "G_MyMenu_00");
    const auto ls = layoutScreens();
    const auto ss = screens(slot);
    for (int i = 0; i < 2; i++) {
        play(ls[i], "ThumbIn", ("G_Thumb_" + pad2(slot)).c_str());
        play(ss[i], "ThumbIn", "G_Btn_00");
    }
    fadeOthers(slot, "ThumbFadeOut", "ThumbFadeOut2");
    updateBar(BarZoomed, true, false);
    sound("SE_CTR_MYMENU_ZOOM_IN");
}

void CtrMyMenu::enterZoomOut() {
    const int slot = mZoomSlot;
    const auto ls = layoutScreens();
    const auto ss = screens(slot);
    for (int i = 0; i < 2; i++) {
        play(ls[i], "ThumbOut", ("G_Thumb_" + pad2(slot)).c_str());
        play(ss[i], "ThumbOut", "G_Btn_00");
    }
    fadeOthers(slot, "ThumbFadeIn", "ThumbFadeIn2");
    play(mUp.get(), "BeltIn", "G_Belt");
    play(mUp.get(), "ThumbValid", "G_MyMenu_00");
    sound("SE_CTR_MYMENU_ZOOM_OUT");
}

CtrMyMenu::ArrowSet CtrMyMenu::arrowsFor(int slot) const {
    const std::vector<int> used = usedSlots();
    if (!used.empty() && used.front() == slot) return Next;
    if (!used.empty() && used.back() == slot) return Previous;
    return Both;
}

void CtrMyMenu::playArrows(const char* clip, ArrowSet set) {
    for (int i = 0; i < 2; i++)
        if (set == Both || set == i) play(mDown.get(), clip, kArrows[i].group);
}

void CtrMyMenu::setArrows(ArrowSet set, bool valid) {
    for (int i = 0; i < 2; i++) mArrowValid[i] = valid && (set == Both || set == i);
}

void CtrMyMenu::startPage(int direction) {
    const std::vector<int> used = usedSlots();
    const auto at = std::find(used.begin(), used.end(), mZoomSlot);
    const int idx = (int)(at - used.begin()) + direction;
    if (at == used.end() || idx < 0 || idx >= (int)used.size()) {
        enter(Zoomed);
        return;
    }
    const int target = used[idx];
    updateBar(BarZoomed, true, false);
    setArrows(Both, false);
    attach(target, "N_Thumb_Next");
    for (LayoutInstance* l : screens(target)) seek(l, "ThumbIn", "G_Btn_00", true);
    for (LayoutInstance* l : layoutScreens()) play(l, direction < 0 ? "LtoR_Thumb" : "RtoL_Thumb", "G_MyMenu_00");
    moveArrows(target);
    mPageTarget = target;
}

void CtrMyMenu::moveArrows(int target) {
    const ArrowSet next = arrowsFor(target);
    if (next != mArrowSet) {
        for (int side : {0, 1}) {
            if (!covers(next, side) && covers(mArrowSet, side)) playArrows("ArrowOut", (ArrowSet)side);
            if (covers(next, side) && !covers(mArrowSet, side)) playArrows("ArrowIn", (ArrowSet)side);
        }
    }
    mArrowSet = next;
}

// ------------------------------------------------------------------ drag paging

bool CtrMyMenu::trackDrag() {
    if (mIn.touchDown) {
        if (hitBar(mIn.x, mIn.y) >= 0 || hitArrow(mIn.x, mIn.y) >= 0 || !hit(mDown.get(), "B_Thumb_01", mIn.x, mIn.y)) return false;
        mDragOn = true;
        mDragActive = false;
        mDragX = mDragStart = mIn.x;
        return true;
    }
    if (!mDragOn) return false;
    if (mIn.touchHeld) {
        if (mDragActive && mIn.x != mDragX) moveDrag(mIn.x - mDragX);
        else if (!mDragActive && std::fabs(mIn.x - mDragStart) > DRAG_START) mDragActive = true;
        mDragX = mIn.x;
        return true;
    }
    mDragOn = false;
    if (mState == Drag) releaseDrag();
    return true;
}

void CtrMyMenu::updateDrag() {
    if (!mSettling) {
        trackDrag();
        return;
    }
    const float remaining = mSettleTo - mDragOffset;
    const float step = std::fabs(remaining) > SETTLE_SPEED ? (remaining > 0 ? SETTLE_SPEED : -SETTLE_SPEED) : remaining;
    if (step != 0) moveDrag(step);
    if (mState == Drag && mDragOffset == mSettleTo) endDrag(mZoomSlot);
}

void CtrMyMenu::moveDrag(float dx) {
    const std::vector<int> used = usedSlots();
    const int index = (int)(std::find(used.begin(), used.end(), mZoomSlot) - used.begin());
    const float offset = std::max(-PAGE_DISTANCE, std::min(PAGE_DISTANCE, mDragOffset + dx));
    int neighbour = -1;
    if (offset < 0 && index + 1 < (int)used.size()) neighbour = used[index + 1];
    if (offset > 0 && index - 1 >= 0) neighbour = used[index - 1];
    if (offset != 0 && neighbour < 0) {
        mDragOn = false;
        if (mState == Drag) endDrag(mZoomSlot);
        return;
    }
    if (mState != Drag) {
        scrollReset(0);
        enter(Drag);
        setArrows(Both, false);
        for (LayoutInstance* l : layoutScreens()) {
            l->stop(bindingId(l, "LtoR_Thumb", "G_MyMenu_00", nullptr));
            l->stop(bindingId(l, "RtoL_Thumb", "G_MyMenu_00", nullptr));
        }
    }
    if (neighbour >= 0 && neighbour != mDragNeighbour) {
        if (mDragNeighbour >= 0) restoreSlot(mDragNeighbour);
        attach(neighbour, "N_Thumb_Next");
        for (LayoutInstance* l : screens(neighbour)) seek(l, "ThumbIn", "G_Btn_00", true);
        mDragNeighbour = neighbour;
    }
    mDragOffset = offset;
    scrollUpdate(offset, -PAGE_DISTANCE, PAGE_DISTANCE);
    placeDrag();
    if (std::fabs(offset) == PAGE_DISTANCE && neighbour >= 0) endDrag(neighbour);
}

void CtrMyMenu::pageScrollSound() {
    Pane* all = mDown->pane("N_Thumb_All");
    if (all && std::fabs(all->translate[0]) < PAGE_DISTANCE) scrollUpdate(all->translate[0], -PAGE_DISTANCE, PAGE_DISTANCE);
}

void CtrMyMenu::placeDrag() {
    if (mState != Drag) return;
    for (LayoutInstance* l : layoutScreens()) {
        if (Pane* next = l->pane("N_Thumb_Next")) {
            next->translate[0] = mDragOffset < 0 ? PAGE_DISTANCE : -PAGE_DISTANCE;
            next->visible = true;
        }
        if (Pane* all = l->pane("N_Thumb_All")) all->translate[0] = std::trunc(mDragOffset);
    }
}

void CtrMyMenu::releaseDrag() {
    const float offset = mDragOffset;
    mSettleTo = std::fabs(offset) >= PAGE_THRESHOLD ? (offset > 0 ? PAGE_DISTANCE : -PAGE_DISTANCE) : 0;
    mSettling = true;
    if (offset == mSettleTo) endDrag(mZoomSlot);
}

void CtrMyMenu::endDrag(int target) {
    const int neighbour = mDragNeighbour;
    mDragNeighbour = -1;
    mDragOffset = 0;
    mSettling = false;
    if (target != mZoomSlot) {
        moveArrows(target);
        mPageTarget = target;
        finishPage();
        return;
    }
    if (neighbour >= 0) restoreSlot(neighbour);
    for (LayoutInstance* l : layoutScreens())
        if (Pane* all = l->pane("N_Thumb_All")) all->translate[0] = 0;
    setArrows(mArrowSet, true);
    enter(Zoomed);
}

void CtrMyMenu::restoreSlot(int slot) {
    attach(slot, slotPane(slot).c_str());
    for (LayoutInstance* l : screens(slot)) {
        seek(l, "ThumbIn", "G_Btn_00", false);
        seek(l, "ThumbFadeOut", "G_BtnThumb", true);
    }
}

void CtrMyMenu::finishPage() {
    const int previous = mZoomSlot, target = mPageTarget;
    attach(target, slotPane(target).c_str());
    toFront(target);
    for (LayoutInstance* l : layoutScreens()) {
        seek(l, "LtoR_Thumb", "G_MyMenu_00", false);
        seek(l, "RtoL_Thumb", "G_MyMenu_00", false);
        seek(l, "ThumbIn", ("G_Thumb_" + pad2(target)).c_str(), true);
        seek(l, "ThumbIn", ("G_Thumb_" + pad2(previous)).c_str(), false);
    }
    restoreSlot(previous);
    mZoomSlot = mSlot = target;
    setArrows(mArrowSet, true);
    enter(Zoomed);
}

void CtrMyMenu::scrollUpdate(float p, float lo, float hi) {
    if (p != mScrollLast) {
        if (p == lo || p == hi) {
            sound("SE_CTR_COMMON_SCROLL_END");
            mScrollTickAt = p;
        } else if (++mScrollFrames > 4 && std::fabs(p - mScrollTickAt) > 20) {
            sound("SE_CTR_COMMON_SCROLL_TEXT");
            mScrollTickAt = p;
            mScrollFrames = 0;
        }
    }
    mScrollLast = p;
}

// ------------------------------------------------------------------ save, delete, load

void CtrMyMenu::confirm(const char* message, const char* thumbs, State next) {
    mDlgContent->setMessage("T_00", message);
    setThumbnails(mDlgContent.get(), "Thumb_U_00", "Thumb_D_00", mSlot);
    setThumbnails(mDlgContent.get(), "Thumb_U_01", "Thumb_D_01", FLYING);
    PlayOptions o;
    o.groups = 1;
    mDlgContent->play(thumbs, o);
    mConfirmNext = next;
    mConfirmSel = 1;
    mDialog->set("", {A.messageText("lau_dlg_2b_canc0"), A.messageText("lau_dlg_2b_ok")}, mConfirmSel);
    mDialog->setContent(mDlgContent.get());
    mDialog->show();
}

void CtrMyMenu::updateConfirm() {
    if (!mDialog->active()) return;
    mDialog->set("", {A.messageText("lau_dlg_2b_canc0"), A.messageText("lau_dlg_2b_ok")}, mConfirmSel);
    int result = -1;
    const int t = mDialog->touch(mIn.touchDown, mIn.touchUp, mIn.x, mIn.y);
    if (t >= 0) result = t;
    if (mPressed & CTR_A) result = 1;
    if (mPressed & CTR_B) result = 0;
    if (result < 0) return;
    sound(result == 1 ? "SE_CTR_COMMON_OK" : "SE_CTR_COMMON_CANCEL");
    mDialog->hide(result == 0);   // its content stays drawn while the window closes
    if (result == 1) enter(mConfirmNext);
    else enter(mZoomed ? Zoomed : Main);
}

void CtrMyMenu::enterSave() {
    mCursor->layout().root->visible = false;
    if (mZoomed) {
        const int slot = mZoomSlot;
        attach(SPARE, slotPane(slot).c_str());
        for (LayoutInstance* l : screens(SPARE)) {
            setThumbnails(l, "Thumb_U_00", "Thumb_D_00", slot);
            play(l, "ThumbOut2", "G_Btn_00");
        }
        mSpareTakesOver = true;
        mButtons[SPARE].randomDown->layout().root->visible = mButtons[SPARE].randomUp->layout().root->visible = mStore->shuffle(slot);
        for (LayoutInstance* l : layoutScreens()) play(l, "ThumbOut2", ("G_Thumb_" + pad2(slot)).c_str());
        fadeOthers(slot, "ThumbFadeIn3", "ThumbFadeIn2");
        play(mUp.get(), "ThumbValid2", "G_MyMenu_00");
        sound("SE_CTR_MYMENU_ZOOM_OUT");
        mStage = 0;
        return;
    }
    startSaveFlight(false);
}

void CtrMyMenu::startSaveFlight(bool afterZoom) {
    const int slot = mSlot;
    const bool used = mStore->used(slot);
    mStage = 2;
    mFrame = 0;
    mClips.clear();
    play(mUp.get(), "UtoD_ThumbInvalid", "G_MyMenu_00");
    toFront(slot);
    for (LayoutInstance* l : screens(FLYING)) l->layout().root->visible = true;
    const auto ls = layoutScreens();
    const auto fs = screens(FLYING);
    for (int i = 0; i < 2; i++) {
        play(ls[i], "UtoD_Thumb_" + pad2(slot), "G_MyMenu_01");
        play(fs[i], "UtoD_Thumb", "G_BtnThumb");
        play(fs[i], used ? "UtoD_ThumbSdw2" : "UtoD_ThumbSdw", "G_BaseSdw");
        play(ls[i], used ? "UtoD_Thumb_D2" : "UtoD_Thumb_D", ("G_Thumb_" + pad2(slot)).c_str());
    }
    play(mUp.get(), afterZoom ? "BeltIn2" : "BeltOutIn", "G_Belt");
    sound("SE_CTR_MYMENU_SAVE_UP");
}

void CtrMyMenu::updateSave() {
    if (mSpareTakesOver) {
        // the spare takes over once its clip is bound, so it never shows its resting pose
        mSpareTakesOver = false;
        for (LayoutInstance* l : screens(SPARE)) l->layout().root->visible = true;
        for (LayoutInstance* l : screens(mSlot)) {
            l->layout().root->visible = false;
            seek(l, "ThumbIn", "G_Btn_00", false);
        }
    }
    if (mStage == 0) {
        if (!clipsDone()) return;
        mStage = 1;
        mFrame = 0;
        return;
    }
    if (mStage == 1) {
        if (mFrame < ZOOMED_SAVE_FRAMES) return;
        mZoomed = false;
        startSaveFlight(true);
        return;
    }
    if (mFrame == SAVE_MOVE_FRAME) sound("SE_CTR_MYMENU_SAVE_MOVE");
    if (mFrame == SAVE_DOWN_FRAME) sound("SE_CTR_MYMENU_SAVE_DOWN");
    if (!clipsDone() || mFrame < SAVE_FRAMES) return;
    const int slot = mSlot;
    if (mStore->save(slot)) saveThumbs(slot);
    mSavedThisVisit = true;
    setSlotImages(slot, mStore->used(slot));
    for (LayoutInstance* l : screens(FLYING)) l->layout().root->visible = false;
    for (LayoutInstance* l : screens(SPARE)) l->layout().root->visible = false;
    for (LayoutInstance* l : screens(slot)) l->layout().root->visible = true;
    // the spare went back to its own place (N_Thumb_00, hidden)
    attach(SPARE, slotPane(0).c_str());
    seek(mDown.get(), "UtoD_Thumb_D", ("G_Thumb_" + pad2(slot)).c_str(), false);
    showRandom(slot);
    enter(Main);
}

void CtrMyMenu::enterDelete() {
    const int slot = mZoomSlot;
    play(mUp.get(), "BeltIn3", "G_Belt");
    play(mUp.get(), "ThumbValid3", "G_MyMenu_00");
    const auto ls = layoutScreens();
    const auto ss = screens(slot);
    for (int i = 0; i < 2; i++) {
        play(ls[i], "ThumbOut3", ("G_Thumb_" + pad2(slot)).c_str());
        play(ss[i], "ThumbOut3", "G_Btn_00");
    }
    fadeOthers(slot, "ThumbFadeIn4", "ThumbFadeIn5");
    sound("SE_CTR_MYMENU_DELETE");
}

void CtrMyMenu::finishDelete() {
    const int slot = mZoomSlot;
    mStore->remove(slot);
    const std::string dir = mStore->thumbDir();
    unlink((dir + "/slot" + std::to_string(slot) + "_top.bin").c_str());
    unlink((dir + "/slot" + std::to_string(slot) + "_bottom.bin").c_str());
    setSlotImages(slot, false);
    showRandom(slot);
    mZoomed = false;
    mSlot = slot;
    enter(Main);
}

void CtrMyMenu::enterLoad() {
    mCursor->layout().root->visible = false;
    if (mZoomed) {
        for (LayoutInstance* l : screens(mZoomSlot)) play(l, "ThumbFadeOut3", "G_BtnThumb");
        play(mBar.get(), "BtnOut2", "G_Scene_01");
        if (usedSlots().size() > 1) playArrows("ArrowOut", mArrowSet);
        setArrows(Both, false);
    } else {
        play(mUp.get(), "ThumbInvalid", "G_MyMenu_00");
        play(mUp.get(), "BeltFadeOut", "G_Belt");
        for (int slot = 0; slot < kSlots; slot++)
            for (LayoutInstance* l : screens(slot)) play(l, mStore->used(slot) ? "ThumbFadeOut" : "ThumbFadeOut2", "G_BtnThumb");
    }
    play(mBar.get(), "FadeOut", "G_Scene_00");
    updateBar(BarZoomed, false, false);
}

void CtrMyMenu::updateLoad() {
    if (mStage == 0) {
        if (!clipsDone()) return;
        mStage = 1;
        mFrame = 0;
        mLoaded = mStore->apply(mSlot);
        if (mLoaded && onLoaded) onLoaded();
        mWait->layout().root->visible = true;
        mWait->play("WIconIn");
        return;
    }
    if (mStage == 1) {
        if (mFrame == WAIT_IN_FRAMES) mWait->play("WIconLoop");
        if (mFrame < LOAD_WAIT_FRAMES) return;
        mStage = 2;
        mWait->stop("WIconIn");
        mWait->play("WIconOut");
        mClips.clear();
        play(mUp.get(), "BG_FadeOut", "G_BG");
        play(mDown.get(), "BG_FadeOut", "G_BG");
        return;
    }
    if (!clipsDone()) return;
    mWait->layout().root->visible = false;
    mActive = false;
    setVisible(false);
    if (onClose) onClose(true);
}

void CtrMyMenu::updateCreating() {
    if (mFrame == CREATE_FRAMES) mDialog->hide(false);
    if (mDialog->active()) return;
    mDialog->setWait("");
    mStore->create();
    enter(Main);
}

// ------------------------------------------------------------------ looks

void CtrMyMenu::updateBar(BarMode mode, bool animate, bool enabled, bool reset) {
    const bool used = mode != BarEmpty;
    for (int i = 0; i < (int)mBarButtons.size(); i++) {
        BarButton& b = mBarButtons[i];
        bool valid = true;
        switch (b.kind) {
            case Back: valid = true; break;
            case LoadB: valid = used; break;
            case SaveB: valid = true; break;
            case Zoom: valid = used; break;
            case DeleteB: valid = mode == BarZoomed; break;
        }
        const bool next = enabled && valid;
        if (b.valid == next && !reset && !(b.kind == Zoom && animate)) continue;
        b.valid = next;
        if (next) seek(mBar.get(), selectClip(i), b.group, false, kLook);
        else play(mBar.get(), "Invalid", b.group, kLook);
    }
    const char* label = used ? "mhm_overwrite_4b" : "mhm_save_4b";
    mBar->setMessage("T_BtnB_01", label);
    mBar->setMessage("T_BtnF_01", label);
}

void CtrMyMenu::setSlotLook(int slot, bool used, bool animate) {
    LayoutInstance* l = mButtons[slot].down.get();
    if (animate) play(l, used ? "Valid" : "Invalid", "G_Btn_00", kLook);
    else seek(l, used ? "Valid" : "Invalid", "G_Btn_00", true, kLook);
}

void CtrMyMenu::fadeOthers(int slot, const char* used, const char* empty) {
    for (int other = 0; other < kSlots; other++) {
        if (other == slot) continue;
        for (LayoutInstance* l : screens(other)) play(l, mStore->used(other) ? used : empty, "G_BtnThumb");
    }
}

void CtrMyMenu::showRandom(int index) {
    const bool shuffle = index < kSlots ? mStore->used(index) && mStore->shuffle(index)
                                        : (index == FLYING || index == CURRENT) && mStore->currentShuffle();
    mButtons[index].randomDown->layout().root->visible = shuffle;
    mButtons[index].randomUp->layout().root->visible = shuffle;
}

// ------------------------------------------------------------------ thumbnails

TexturePtr CtrMyMenu::thumb(int index, bool top) {
    if (index < kSlots) return top ? mSlotTop[index] : mSlotBottom[index];
    return top ? mCurrentTop : mCurrentBottom;
}

void CtrMyMenu::setSlotImages(int slot, bool used) {
    if (slot < 0 || slot >= kSlots) return;
    if (used) {
        mSlotTop[slot] = loadThumb(slot, true);
        mSlotBottom[slot] = loadThumb(slot, false);
    } else {
        mSlotTop[slot].reset();
        mSlotBottom[slot].reset();
    }
    applyTextures(slot);
}

void CtrMyMenu::applyTextures(int index) {
    for (LayoutInstance* l : screens(index)) setThumbnails(l, "Thumb_U_00", "Thumb_D_00", index);
}

void CtrMyMenu::setThumbnails(LayoutInstance* l, const char* topPane, const char* bottomPane, int index) {
    for (int s = 0; s < 2; s++) {
        const char* pane = s == 0 ? topPane : bottomPane;
        TexturePtr t = thumb(index, s == 0);
        if (!t) {
            auto o = mOriginals.find(pane);
            if (o != mOriginals.end()) t = o->second;
        }
        if (t) l->replaceTexture(pane, t);
    }
}

// A thumbnail file: "MMT1", u32 width, u32 height, then the RGBA texels (rows as GL reads them)
// deflated.
bool CtrMyMenu::saveThumbs(int slot) {
    const std::string dir = mStore->thumbDir();
    mkdir(dir.c_str(), 0755);
    bool ok = true;
    for (int s = 0; s < 2; s++) {
        TexturePtr t = s == 0 ? mCurrentTop : mCurrentBottom;
        if (!t || !t->id) { ok = false; continue; }
        const int w = t->texWidth, h = t->texHeight;
        std::vector<uint8_t> px((size_t)w * h * 4);
        GLint prev = 0;
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prev);
        GLuint fbo = 0;
        glGenFramebuffers(1, &fbo);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, t->id, 0);
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, px.data());
        glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prev);
        glDeleteFramebuffers(1, &fbo);
        uLongf packed = compressBound((uLong)px.size());
        std::vector<uint8_t> z(packed);
        if (compress2(z.data(), &packed, px.data(), (uLong)px.size(), 6) != Z_OK) { ok = false; continue; }
        const std::string path = dir + "/slot" + std::to_string(slot) + (s == 0 ? "_top.bin" : "_bottom.bin");
        const std::string tmp = path + ".tmp";
        FILE* f = fopen(tmp.c_str(), "wb");
        if (!f) { ok = false; continue; }
        const uint32_t head[3] = {0x31544d4d, (uint32_t)w, (uint32_t)h};   // "MMT1"
        bool wrote = fwrite(head, sizeof(head), 1, f) == 1 && fwrite(z.data(), 1, packed, f) == packed;
        wrote = fflush(f) == 0 && wrote;
        fclose(f);
        if (wrote) rename(tmp.c_str(), path.c_str());
        else { unlink(tmp.c_str()); ok = false; }
    }
    // the slot shows the saved images from now on (copies: the current ones belong to this visit)
    return ok;
}

TexturePtr CtrMyMenu::loadThumb(int slot, bool top) {
    const std::string path = mStore->thumbDir() + "/slot" + std::to_string(slot) + (top ? "_top.bin" : "_bottom.bin");
    FILE* f = fopen(path.c_str(), "rb");
    if (!f) return nullptr;
    uint32_t head[3] = {0, 0, 0};
    std::vector<uint8_t> z;
    if (fread(head, sizeof(head), 1, f) == 1 && head[0] == 0x31544d4d && head[1] > 0 && head[2] > 0 && head[1] <= 4096 &&
        head[2] <= 4096) {
        uint8_t buf[65536];
        size_t n;
        while ((n = fread(buf, 1, sizeof(buf), f)) > 0) z.insert(z.end(), buf, buf + n);
    }
    fclose(f);
    if (z.empty()) return nullptr;
    std::vector<uint8_t> px((size_t)head[1] * head[2] * 4);
    uLongf len = (uLongf)px.size();
    if (uncompress(px.data(), &len, z.data(), (uLong)z.size()) != Z_OK || len != px.size()) return nullptr;
    TexturePtr t = A.rgbaTexture(px.data(), (int)head[1], (int)head[2]);
    glBindTexture(GL_TEXTURE_2D, t->id);
    glGenerateMipmap(GL_TEXTURE_2D);
    t->mipmapped = true;
    return t;
}

// ------------------------------------------------------------------ helpers

std::array<LayoutInstance*, 2> CtrMyMenu::screens(int index) { return {mButtons[index].down.get(), mButtons[index].up.get()}; }
std::array<LayoutInstance*, 2> CtrMyMenu::layoutScreens() { return {mDown.get(), mUp.get()}; }

std::vector<int> CtrMyMenu::usedSlots() const {
    std::vector<int> out;
    for (int s = 0; s < kSlots; s++)
        if (mStore->used(s)) out.push_back(s);
    return out;
}

void CtrMyMenu::setVisible(bool v) {
    for (LayoutInstance* l : {mDown.get(), mUp.get(), mBar.get(), mCursor.get()})
        if (l) l->layout().root->visible = v;
}

void CtrMyMenu::toFront(int slot) {
    for (LayoutInstance* l : layoutScreens()) {
        Pane* p = l->pane(slotPane(slot));
        Pane* parent = p ? p->parent : nullptr;
        if (!p || !parent) continue;
        auto& c = parent->children;
        c.erase(std::remove(c.begin(), c.end(), p), c.end());
        c.push_back(p);
    }
}

void CtrMyMenu::attachTo(LayoutInstance* hostLayout, const char* host, LayoutInstance* child, std::vector<Attached>& list) {
    Pane* h = hostLayout ? hostLayout->pane(host) : nullptr;
    if (!h || !child) return;
    detach(child, list);
    child->layout().root->parent = h;
    list.push_back({h, child});
}

void CtrMyMenu::detach(LayoutInstance* child, std::vector<Attached>& list) {
    child->layout().root->parent = nullptr;
    list.erase(std::remove_if(list.begin(), list.end(), [&](const Attached& a) { return a.child == child; }), list.end());
}

void CtrMyMenu::attach(int index, const char* host) {
    attachTo(mDown.get(), host, mButtons[index].down.get(), mDownParts);
    attachTo(mUp.get(), host, mButtons[index].up.get(), mUpParts);
}

bool CtrMyMenu::clipsDone() const {
    for (const Clip& c : mClips) {
        Animator* a = c.layout->findId(c.id);
        if (a && !a->finished) return false;
    }
    return true;
}

std::string CtrMyMenu::bindingId(LayoutInstance* l, const std::string& name, const char* group, const char* key) const {
    const std::string base = key ? std::string(key) : l->resolveAnimation(name);
    return base + "#" + group;
}

void CtrMyMenu::play(LayoutInstance* l, const std::string& name, const char* group, const char* key) {
    if (!l || !l->hasAnimation(name)) return;
    PlayOptions o;
    o.groups = 2;
    o.groupNames = {group};
    o.loop = 0;
    o.id = bindingId(l, name, group, key);
    if (l->play(name, o)) mClips.push_back({l, o.id});
}

void CtrMyMenu::seek(LayoutInstance* l, const std::string& name, const char* group, bool end, const char* key) {
    if (!l || !l->hasAnimation(name)) return;
    PlayOptions o;
    o.groups = 2;
    o.groupNames = {group};
    o.loop = 0;
    o.paused = true;
    o.id = bindingId(l, name, group, key);
    if (Animator* a = l->play(name, o)) {
        a->seek(end ? a->lastFrame() : 0);
        a->apply();
    }
}

// ------------------------------------------------------------------ drawing

void CtrMyMenu::setScreens(float topW, float topH, float botW) {
    if (!mLoadedLayouts) return;
    // backgrounds and the belt stretch, the thumbnails stay centred (a zoomed slot fills the
    // design screen in the middle); the bottom buttons go to the edges like the home's
    mUp->layout().adaptWidth(topW, Layout::Adapt::None);
    mUp->layout().adaptHeight(topH, Layout::Adapt::None);
    mDown->layout().adaptWidth(botW, Layout::Adapt::None);
    mBar->layout().adaptWidth(botW, Layout::Adapt::Edge);
    if (mDialog) mDialog->setWidth(botW);
}

bool CtrMyMenu::coversTop() const {
    // MyMenu_U_00 has its own background (G_BG): once shown and until it fades in a load
    return mActive && mState != Idle && !(mState == Load && mStage == 2);
}

bool CtrMyMenu::coversBottom() const { return coversTop(); }

void CtrMyMenu::drawTree(Renderer& r, Layout& L, Pane* p, const std::vector<Attached>& list) {
    if (!p->visible) return;
    r.drawPaneOnly(L, p);
    for (Pane* c : p->children) drawTree(r, L, c, list);
    // layouts hung under this pane draw after its own children, in the order they were hung
    for (const Attached& a : list) {
        if (a.host != p) continue;
        Layout& CL = a.child->layout();
        if (CL.root) drawTree(r, CL, CL.root, list);
    }
}

void CtrMyMenu::drawComposed(Renderer& r, LayoutInstance* root, const std::vector<Attached>& list) {
    Layout& L = root->layout();
    if (L.root) drawTree(r, L, L.root, list);
}

void CtrMyMenu::drawTop(Renderer& r) {
    if (!mActive || mState == Idle) return;
    drawComposed(r, mUp.get(), mUpParts);
}

void CtrMyMenu::drawBottom(Renderer& r) {
    if (!mActive || mState == Idle) return;
    drawComposed(r, mDown.get(), mDownParts);
    r.drawLayout(mCursor->layout());
    r.drawLayout(mBar->layout());
    r.drawLayout(mWait->layout());
    if (mDialog) mDialog->drawBottom(r);
}

}  // namespace ctr
}  // namespace android
