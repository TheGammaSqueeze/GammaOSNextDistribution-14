// NanoCtrSetup - the 3DS theme's first-run setup scene (see NanoCtrSetup.h).
#define LOG_TAG "NanoCtr"

#include "NanoCtrSetup.h"

#include <algorithm>
#include <cmath>
#include <log/log.h>

namespace android {
namespace ctr {

namespace {
const char* kWallArchive = "common";
const char* kWallLayout = "CmnFade_U_00";
const char* kWallBg = "P_Bg_U_00";
const char* kTextArchive = "sleep";
const char* kTextLayout = "Slp_U_00";
const char* kHappyArchive = "dialog_happy";
const char* kHappyLayout = "DlgHappy_D_00";
const int kHappyFadeFrames = 21;
// the lines of the wall drift up slowly (texture v units a frame; the map repeats vertically)
constexpr float kDriftPerFrame = 0.00025f;
// the text slots: which copy of the text layout and which of its panes (T_Top_00 22.5 pt,
// T_Main_00 16.25 pt left aligned, T_Btm_00 17.5 pt)
struct SlotDef { int copy; const char* pane; };
const SlotDef kSlotDefs[CtrSetupScene::SlotCount] = {
    {0, "T_Top_00"},   // Title
    {0, "T_Main_00"},  // Desc
    {0, "T_Btm_00"},   // Hint
    {1, "T_Top_00"},   // Greeting
    {1, "T_Btm_00"},   // Lang
    {1, "T_Main_00"},  // Zone
};
const Rgba kInk{58, 58, 66, 255};
const Rgba kInkSoft{92, 93, 106, 255};   // 5:1 against the wall (AA for small text)
}  // namespace

CtrSetupScene::CtrSetupScene(std::string assetRoot) : mAssets(std::make_unique<Assets>(assetRoot)) {
    mAudio = std::make_unique<CtrAudio>(assetRoot + "/sound");
    auto sound = [this](const char* name) { play(name); };
    mPanel = std::make_unique<CtrPanel>(*mAssets, sound);
    mPanel->setCloseHidden(true);
    mPanel->setBare(true);
    mDialog = std::make_unique<CtrDialog>(*mAssets, sound);
}

CtrSetupScene::~CtrSetupScene() {
    mHappy.reset();
    mText[1].reset();
    mText[0].reset();
    mWall.reset();
    mMark.reset();
    mDialog.reset();
    mPanel.reset();
    mAudio.reset();
    mAssets.reset();
}

void CtrSetupScene::play(const char* sound) {
    if (mAudio && sound && *sound) mAudio->play(sound);
}

Pane* CtrSetupScene::slotPane(Slot s) const {
    const SlotDef& d = kSlotDefs[s];
    return mText[d.copy] ? mText[d.copy]->pane(d.pane) : nullptr;
}

bool CtrSetupScene::load() {
    if (mWall) return true;
    mWall = mAssets->create(kWallArchive, kWallLayout);
    mText[0] = mAssets->create(kTextArchive, kTextLayout);
    mText[1] = mAssets->create(kTextArchive, kTextLayout);
    if (!mWall || !mText[0] || !mText[1]) {
        ALOGE("ctr setup: %s/%s or %s/%s missing", kWallArchive, kWallLayout, kTextArchive, kTextLayout);
        mWall.reset();
        mText[0].reset();
        mText[1].reset();
        return false;
    }
    // the wall: only its light picture (the belt's camera panes are black covers)
    for (const char* n : {"P_CamMask_00", "P_Cam_00"}) mWall->setVisible(n, false);
    // the text copies: only their text panes, in ink on the light wall
    for (auto& t : mText) {
        for (const char* n : {"P_Bg_D_00", "P_LineTop_00", "P_Mask_00"}) t->setVisible(n, false);
        for (const char* n : {"T_Top_00", "T_Main_00", "T_Btm_00"}) {
            if (Pane* p = t->pane(n)) { p->alpha = 0; p->vertexColors = {kInk, kInk, kInk, kInk}; }
            t->setText(n, "");
        }
    }
    mHappy = mAssets->create(kHappyArchive, kHappyLayout);
    mMark = mAssets->layoutTexture("logo", "3dsLogo_02");
    if (!mHappy) ALOGW("ctr setup: %s/%s missing (the finish uses the plain dialog)", kHappyArchive, kHappyLayout);
    else {
        if (Pane* root = mHappy->layout().root) root->alpha = 0;
        mHappy->setVisible("P_Bg_00", false);   // its black field: the wall shows instead
        // the card's icon is the GammaOS mark of the boot splash in place of the dummy icon
        if (mMark) {
            mHappy->replaceTexture("P_Icon_00", mMark);
            // the dummy icon's picture shows 3/4 of its sheet (map 0) under a rounded square
            // mask (map 1, IconMask) that trims the icon's corners: the mark wants the whole of
            // its sheet, at its own aspect, keeping the card's icon height and left edge, and
            // the mask's centre texel everywhere so nothing of the mark is trimmed
            if (Pane* p = mHappy->pane("P_Icon_00")) {
                const QuadUv full{{{0, 0}, {1, 0}, {0, 1}, {1, 1}}};
                const QuadUv centre{{{0.5f, 0.5f}, {0.5f, 0.5f}, {0.5f, 0.5f}, {0.5f, 0.5f}}};
                for (size_t i = 0; i < p->texCoords.size(); i++) p->texCoords[i] = i == 0 ? full : centre;
                if (mMark->width > 0 && mMark->height > 0) {
                    const float h = p->size[1];
                    const float w = h * (float)mMark->width / (float)mMark->height;
                    p->translate[0] += (w - p->size[0]) / 2;
                    p->size[0] = w;
                }
            }
        }
    }
    for (const char* s : {"SE_CTR_COMMON_DIALOG", "SE_CTR_COMMON_OK", "SE_CTR_COMMON_CANCEL", "SE_CTR_COMMON_SCROLL_TEXT"})
        mAudio->preload(s);
    return true;
}

void CtrSetupScene::releaseGl() {
    mHappy.reset();
    mText[1].reset();
    mText[0].reset();
    mWall.reset();
    mMark.reset();
    mHappyPhase = Hidden;
    if (mDialog) mDialog->releaseGl();
    if (mPanel) mPanel->releaseGl();
    if (mAssets) mAssets->releaseGl();
}

bool CtrSetupScene::canDraw(const std::string& text) const {
    const Pane* p = slotPane(Title);
    if (!p || !p->font) return false;
    for (char32_t c : utf8ToU32(text))
        if (c > 0x20 && !p->font->has((uint32_t)c)) return false;
    return true;
}

unsigned CtrSetupScene::markTexture(int* w, int* h) const {
    if (!mMark || !mMark->id) return 0;
    if (w) *w = mMark->width;
    if (h) *h = mMark->height;
    return mMark->id;
}

void CtrSetupScene::tick() {
    if (!mWall) return;
    mPanel->tick();
    mDialog->tick();
    mDrift += kDriftPerFrame;
    if (mDrift > 1.0f) mDrift -= 1.0f;
    if (Pane* bg = mWall->pane(kWallBg)) {
        // the first map is the line sheet (clamp across, repeating down): slide its v
        if (!bg->texCoords.empty()) {
            QuadUv& q = bg->texCoords[0];
            if (!mHaveLineUv) { mLineUv = q; mHaveLineUv = true; }
            for (int i = 0; i < 4; i++) { q[i][0] = mLineUv[i][0]; q[i][1] = mLineUv[i][1] + mDrift; }
        }
    }
    mWall->update(1);
    for (auto& t : mText) t->update(1);
    if (mHappy && mHappyPhase != Hidden) {
        mHappyFrame++;
        if (mHappyPhase == Opening && mHappyFrame >= kHappyFadeFrames) {
            mHappyPhase = Shown;
            PlayOptions o;
            o.loop = 1;
            mHappy->play("Loop", o);
        }
        if (mHappyPhase == Closing && mHappyFrame >= kHappyFadeFrames) {
            mHappyPhase = Hidden;
            if (Pane* root = mHappy->layout().root) root->alpha = 0;
        }
        mHappy->update(1);
    }
}

void CtrSetupScene::setText(Slot slot, const std::string& text, float x, float y, float width, int align, float scale,
                            float alpha, bool soft) {
    TextPose& t = mSlots[slot];
    Pane* p = slotPane(slot);
    if (!p) return;
    const bool changed = text != t.text || width != t.width;
    t.x = x; t.y = y; t.width = width; t.align = align; t.scale = scale; t.alpha = alpha; t.soft = soft;
    if (changed) {
        t.text = text;
        if (width > 0) p->size[0] = width;
        t.wrapped = width > 0 ? wrapText(p, text) : text;
        mText[kSlotDefs[slot].copy]->setText(kSlotDefs[slot].pane, t.wrapped);
    }
}

void CtrSetupScene::showHappy(const std::string& appTitle, const std::string& text) {
    if (!mHappy) return;
    if (appTitle != mHappyTitle) { mHappyTitle = appTitle; mHappy->setText("T_AppTitle_00", appTitle); }
    if (text != mHappyText) { mHappyText = text; mHappy->setText("TextBoxDialog", text); }
    if (mHappyPhase == Opening || mHappyPhase == Shown) return;
    if (Pane* root = mHappy->layout().root) root->alpha = 255;
    PlayOptions o;
    o.loop = 0;
    mHappy->play("FadeIn", o);
    mHappyPhase = Opening;
    mHappyFrame = 0;
    play("SE_CTR_COMMON_DIALOG");
}

void CtrSetupScene::hideHappy() {
    if (!mHappy || mHappyPhase == Hidden || mHappyPhase == Closing) return;
    mHappy->stop("Loop");
    PlayOptions o;
    o.loop = 0;
    mHappy->play("FadeOut00", o);
    mHappyPhase = Closing;
    mHappyFrame = 0;
}

void CtrSetupScene::setPanelPlacement(float cx, float cy, float scale, float width, float clipBottom, float clipTop) {
    mPanelCx = cx; mPanelCy = cy; mPanelScale = scale; mPanelWidth = width;
    mPanelClipBottom = clipBottom; mPanelClipTop = clipTop;
    mPanel->setWidth(width);
    mPanel->setTopSize(400, 240);
}

void CtrSetupScene::setDialogPlacement(float cx, float cy, float scale, float maskWidth) {
    mDialogCx = cx; mDialogCy = cy; mDialogScale = scale; mDialogMask = maskWidth;
    mDialog->setWidth(maskWidth);
    mDialog->setTopSize(400, 240);
}

// The wall fills the whole widened screen: its picture grows to it and its texture keeps the
// design magnification about the centre (the lines stay the console's size, more of them).
void CtrSetupScene::widen(float lw, float lh) {
    if (lw == mLw && lh == mLh) return;
    mLw = lw;
    mLh = lh;
    if (Pane* bg = mWall ? mWall->pane(kWallBg) : nullptr) {
        bg->growW = lw - bg->size[0];
        bg->growH = lh - bg->size[1];
    }
}

// out = proj x (translate(cx, cy) x scale(s)): the 320 x 240 widget space placed on the screen
void CtrSetupScene::compose(float out[16], const float proj[16], float cx, float cy, float s) {
    float m[16] = {0};
    m[0] = s; m[5] = s; m[10] = 1; m[15] = 1;
    m[12] = cx; m[13] = cy;
    for (int c = 0; c < 4; c++)
        for (int row = 0; row < 4; row++) {
            float v = 0;
            for (int k = 0; k < 4; k++) v += proj[k * 4 + row] * m[c * 4 + k];
            out[c * 4 + row] = v;
        }
}

void CtrSetupScene::drawBackdrop(const float proj[16], int vpX, int vpY, int vpW, int vpH, float lw, float lh) {
    if (!mWall) return;
    widen(lw, lh);
    Renderer& r = mAssets->renderer();
    r.setViewport(vpX, vpY, vpW, vpH);
    r.beginWith(proj);
    r.setClip(-lw / 2, -lh / 2, lw, lh);
    if (mBackdropAlpha > 0.001f) {
        if (Pane* root = mWall->layout().root) root->alpha = std::clamp(mBackdropAlpha, 0.0f, 1.0f) * 255.0f;
        mWall->layout().calculate();
        r.drawLayout(mWall->layout());
    }
    r.clearClip();
    r.end();
}

void CtrSetupScene::drawWidgets(const float proj[16], int vpX, int vpY, int vpW, int vpH, float lw, float lh) {
    if (!mWall) return;
    Renderer& r = mAssets->renderer();
    r.setViewport(vpX, vpY, vpW, vpH);
    // 1. the list rows in their place, clipped to the list's own screen window: the list body
    //    runs past the 240 units the bottom screen's edges cut it at
    if (mPanel->active()) {
        float p[16];
        compose(p, proj, mPanelCx, mPanelCy, mPanelScale);
        r.beginWith(p);
        // the rows are cut cleanly above the hint line, as the console's screen edge cuts them
        {
            const float w = std::max(mPanelWidth, 320.0f);   // the layouts never go under 320
            r.setClip(-w / 2, mPanelClipBottom, w, mPanelClipTop - mPanelClipBottom);
        }
        mPanel->drawBottom(r);
        r.clearClip();
        r.end();
    }
    // 2. the dialog (its mask dims the whole screen under it)
    if (mDialog->active()) {
        float p[16];
        compose(p, proj, mDialogCx, mDialogCy, mDialogScale);
        r.beginWith(p);
        r.setClip(-mDialogMask / 2, -lh / 2 / mDialogScale, mDialogMask, lh / mDialogScale);
        mDialog->drawBottom(r);
        r.clearClip();
        r.end();
    }
    // 3. the celebratory dialog, centred
    if (mHappy && mHappyPhase != Hidden) {
        float p[16];
        compose(p, proj, mDialogCx, mDialogCy, mDialogScale);
        r.beginWith(p);
        r.setClip(-lw / 2 / mDialogScale, -lh / 2 / mDialogScale, lw / mDialogScale, lh / mDialogScale);
        mHappy->layout().calculate();
        r.drawLayout(mHappy->layout());
        r.clearClip();
        r.end();
    }
}

void CtrSetupScene::drawTexts(const float proj[16], int vpX, int vpY, int vpW, int vpH, float lw, float lh, int dots,
                              int dot, float dotX, float dotY, float dotAlpha) {
    if (!mWall) return;
    Renderer& r = mAssets->renderer();
    r.setViewport(vpX, vpY, vpW, vpH);
    for (int i = 0; i < SlotCount; i++) {
        Pane* p = slotPane((Slot)i);
        if (!p) continue;
        const TextPose& t = mSlots[i];
        p->alpha = (t.text.empty() ? 0.0f : std::clamp(t.alpha, 0.0f, 1.0f)) * 255.0f;
        p->scale[0] = p->scale[1] = t.scale;
        p->originX = (uint8_t)t.align;
        p->textPosX = (uint8_t)t.align;
        // the explanation paragraph hangs from its top line; the rest centre on y
        const bool para = i == Desc;
        p->originY = para ? 0 : 1;
        p->textPosY = para ? 0 : 1;
        p->translate[0] = t.x;
        p->translate[1] = t.y;
        const Rgba& c = t.soft ? kInkSoft : kInk;
        p->vertexColors = {c, c, c, c};
    }
    r.beginWith(proj);
    r.setClip(-lw / 2, -lh / 2, lw, lh);
    for (auto& t : mText) {
        t->layout().calculate();
        r.drawLayout(t->layout());
    }
    (void)dots; (void)dot; (void)dotX; (void)dotY; (void)dotAlpha;   // the host draws the step dots
    r.clearClip();
    r.end();
}

}  // namespace ctr
}  // namespace android
