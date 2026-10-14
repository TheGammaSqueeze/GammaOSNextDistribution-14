// NanoCtrPanel - see NanoCtrPanel.h.
#include "NanoCtrPanel.h"
#include "NanoCtrHome.h"

#include <algorithm>
#include <cmath>
#include <cstring>

#include <log/log.h>

#undef LOG_TAG
#define LOG_TAG "NanoCtr"

namespace android {
namespace ctr {

namespace {
const char* kArchive = "petit";
const int kFadeInFrames = 21;
const int kFadeOutFrames = 16;
// panel.ts / panellist.ts
const float kViewHalf = 120;
const float kViewHeight = 240;
const float kVisibleMargin = 30;   // DAT_002553b0
const float kDragStart = 10;       // FUN_00254570
const float kFriction = 0.6f;      // FUN_00254ab4
const int kScrollFrames = 10;      // FUN_001f7278(-1)
const float kStripePeriod = 8;     // FUN_00253fe4
const float kPlateTop = 20, kPlateBottom = 20;   // N_TopBg_00 / N_BtmBg_00 heights
// row geometry of PtDlgCnt_KTR: the first entry's top 28 below the list top, small buttons
// (PtBtnS_*, 40 tall) every 48, header entries (PtBtnT_*, 64 tall) every 72
const float kListTop = 28;
const float kListBottom = 20;
const float kButtonPitch = 48;
const float kHeaderPitch = 72;
const char* kKnobPanes[] = {"SBBtn", "SBBtnShdw", "SBBtnFrame", "B_Slide_00"};

// Squeezes a text box's line to its width like the console's labels (the font is narrowed, the
// height kept).
void fitText(Pane* p) {
    if (!p || !p->font) return;
    const Font& f = *p->font;
    const double sx = f.width ? (double)p->fontSize[0] / f.width : 1.0;
    const double sy = f.height ? (double)p->fontSize[1] / f.height : 1.0;
    double widest = 0, w = 0;
    int n = 0;
    for (const TextRun& run : p->runs)
        for (char32_t ch : run.text) {
            if (ch == U'\n') {
                widest = std::max(widest, w + (n > 1 ? p->charSpace * (n - 1) : 0));
                w = 0;
                n = 0;
                continue;
            }
            const Glyph* g = f.glyph(ch);
            w += (g ? g->charWidth : 0) * sx * run.scale;
            n++;
        }
    widest = std::max(widest, w + (n > 1 ? p->charSpace * (n - 1) : 0));
    const double room = p->size[0] + p->growW;
    const bool squeeze = widest > room && widest > 0;
    const float fx = squeeze ? (float)(sx * room / widest) : (float)sx;
    if (p->hasFontScale != squeeze || (squeeze && p->fontScale[0] != fx)) {
        p->hasFontScale = squeeze;
        p->fontScale[0] = fx;
        p->fontScale[1] = (float)sy;
        p->textRevision++;
    }
}

// Width of one line of `text` in a text box's units (its font size and spacing).
double lineWidth(const Pane* p, const std::u32string& text) {
    const Font& f = *p->font;
    const double sx = f.width ? (double)p->fontSize[0] / f.width : 1.0;
    double w = 0;
    for (char32_t ch : text) {
        const Glyph* g = f.glyph(ch);
        w += (g ? g->charWidth : 0) * sx;
    }
    if (text.size() > 1) w += p->charSpace * (double)(text.size() - 1);
    return w;
}

// Word wraps `text` to the text box's width (message bodies; the console's dialogs wrap).
std::string wrapText(const Pane* p, const std::string& text) {
    if (!p || !p->font) return text;
    const double room = p->size[0] + p->growW;
    const std::u32string in = utf8ToU32(text);
    std::u32string out, line, word;
    auto flushWord = [&]() {
        if (word.empty()) return;
        std::u32string trial = line.empty() ? word : line + U' ' + word;
        if (!line.empty() && lineWidth(p, trial) > room) {
            out += line;
            out += U'\n';
            line = word;
        } else {
            line = trial;
        }
        word.clear();
    };
    for (char32_t ch : in) {
        if (ch == U'\n') { flushWord(); out += line; out += U'\n'; line.clear(); continue; }
        if (ch == U' ') { flushWord(); continue; }
        word += ch;
    }
    flushWord();
    out += line;
    std::string utf8;
    for (char32_t c : out) {
        if (c < 0x80) utf8 += (char)c;
        else if (c < 0x800) { utf8 += (char)(0xC0 | (c >> 6)); utf8 += (char)(0x80 | (c & 0x3F)); }
        else if (c < 0x10000) { utf8 += (char)(0xE0 | (c >> 12)); utf8 += (char)(0x80 | ((c >> 6) & 0x3F)); utf8 += (char)(0x80 | (c & 0x3F)); }
        else { utf8 += (char)(0xF0 | (c >> 18)); utf8 += (char)(0x80 | ((c >> 12) & 0x3F)); utf8 += (char)(0x80 | ((c >> 6) & 0x3F)); utf8 += (char)(0x80 | (c & 0x3F)); }
    }
    return utf8;
}

void setLabel(LayoutInstance* l, const char* pane, const std::string& text) {
    if (!l || !l->pane(pane)) return;
    l->setText(pane, text);
    fitText(l->pane(pane));
}

void holdGroups(LayoutInstance* l, const char* clip, std::vector<std::string> groups) {
    if (!l || groups.empty()) return;
    PlayOptions o;
    o.groups = 2;
    o.groupNames = std::move(groups);
    o.loop = 0;
    o.paused = true;
    if (Animator* a = l->play(clip, o)) a->seek(1);
}

void paneOrigin(const Pane* p, float* x, float* y) {
    *x = p->global.m[12];
    *y = p->global.m[13];
}
}  // namespace

// ================================================================== CtrPanel

CtrPanel::CtrPanel(Assets& assets, SoundFn sound) : A(assets), mSound(std::move(sound)) {}
CtrPanel::~CtrPanel() = default;

bool CtrPanel::load() {
    if (mLoaded) return true;
    if (mLoadFailed) return false;
    mBg = A.create(kArchive, "PtDlgBg_D_00");
    mTop = A.create(kArchive, "PtDlgBg_U_00");
    mMask = A.create("dialogmask", "DlgMask_D_00");
    mSlider = A.create(kArchive, "PtSlideBar");
    mCursor = A.create(kArchive, "PtCsr_00");
    mClose = A.create(kArchive, "PtClose_00");
    if (!mBg || !mTop || !mMask || !mSlider || !mCursor || !mClose) {
        ALOGW("ctr: settings panel layouts missing");
        mLoadFailed = true;
        return false;
    }
    PlayOptions loop;
    loop.groups = 1;
    mCursor->play("Loop", loop);
    PlayOptions plate;
    plate.groups = 1;
    plate.paused = true;
    plate.loop = 0;
    mPlates[0] = mBg->play("SlideTop", plate);
    mPlates[1] = mBg->play("SlideMiddle", plate);
    mPlates[2] = mBg->play("SlideBottom", plate);
    if (Pane* g = mSlider->pane("N_Slide_00")) mKnobCenter = g->translate[1];
    mLoaded = true;
    return true;
}

float CtrPanel::rowHeight(const PanelRow& r) const { return r.kind == PanelRow::Button ? kButtonPitch : kHeaderPitch; }

void CtrPanel::layoutRows() {
    mRowTop.resize(mRows.size());
    mRowH.resize(mRows.size());
    float y = kListTop;
    for (size_t i = 0; i < mRows.size(); i++) {
        mRowTop[i] = y;
        mRowH[i] = rowHeight(mRows[i]);
        y += mRowH[i];
    }
    const float content = y + kListBottom;
    mMax = std::max(0.0f, content - kViewHeight);
    // slide bar knob: the visible share of the list, never below the knob art (FUN_002549cc)
    Pane* groove = mSlider->pane("SBBaseWndw");
    Pane* art = mSlider->pane("SBBtn");
    if (groove && art) {
        static float artH = -1;
        if (artH < 0) artH = art->size[1];
        const float knob = std::max(artH, groove->size[1] * kViewHeight / std::max(content, kViewHeight));
        for (const char* n : kKnobPanes)
            if (Pane* p = mSlider->pane(n)) p->size[1] = knob;
        mKnobTravel = groove->size[1] - knob;
    }
    mScroll = std::min(std::max(mScroll, 0.0f), mMax);
}

// The cursor box of a row in list space: x from the list centre, y = distance below the list top
// (of the box centre). Boxes from the part layouts' N_CPos_* panes.
void CtrPanel::stopRect(int row, float* x, float* y, float* w, float* h) const {
    *x = 0;
    *y = 0;
    *w = 249;
    *h = 66;
    if (row < 0 || row >= (int)mRows.size()) return;
    const PanelRow& r = mRows[row];
    const float centre = mRowTop[row] + (r.kind == PanelRow::Button ? 40.0f : 64.0f) / 2;
    switch (r.kind) {
        case PanelRow::Button:   // N_CPos_Btn_00 (0, 1.5) 249 x 66
            *y = centre - 1.5f;
            break;
        case PanelRow::Toggle:   // N_CPos_Off_00 (-51, -7.5) / N_CPos_On_00 (50, -7.5) 152 x 74
            *x = r.on ? 50.0f : -51.0f;
            *y = centre + 7.5f;
            *w = 152;
            *h = 74;
            break;
        case PanelRow::Value:    // the value button 8 below the header's centre
            *y = centre + 8.0f - 1.5f;
            break;
    }
}

void CtrPanel::set(const std::string& title, const std::vector<PanelRow>& rows, int sel, uint64_t level) {
    if (!load()) return;
    if (title != mTitle) {
        mTitle = title;
        mTop->setText("T_Text_00", title);
        fitText(mTop->pane("T_Text_00"));
    }
    const bool newLevel = level != mLevel;
    const bool reshaped = rows.size() != mRows.size() ||
                          !std::equal(rows.begin(), rows.end(), mRows.begin(), [](const PanelRow& a, const PanelRow& b) { return a.kind == b.kind; });
    if (newLevel || reshaped || rows != mRows) {
        mRows = rows;
        layoutRows();
    }
    if (rows.empty()) sel = -1;
    else sel = std::min(std::max(sel, 0), (int)rows.size() - 1);
    if (newLevel) {
        mLevel = level;
        mSel = sel;
        mScroll = 0;
        mScrollFrames = 0;
        mTouch = TIdle;
        // a selection further down starts scrolled to it
        if (sel >= 0) {
            float x, y, w, h;
            stopRect(sel, &x, &y, &w, &h);
            if (y + h / 2 > kViewHeight - kVisibleMargin) mScroll = std::min(mMax, y - kViewHalf);
        }
        mCursorShown = true;
        placeCursor();
        return;
    }
    if (sel != mSel) {
        const int prev = mSel;
        mSel = sel;
        mCursorShown = true;
        if (sel < 0) return;
        float x, y, w, h;
        stopRect(sel, &x, &y, &w, &h);
        // on screen when the box keeps the margin from both edges (panellist.ts moveRow)
        const float top = y - h / 2 - mScroll, bottom = y + h / 2 - mScroll;
        const bool visible = top > kVisibleMargin - 30 && bottom < kViewHeight - kVisibleMargin + 30 &&
                             y - mScroll > kVisibleMargin && y - mScroll < kViewHeight - kVisibleMargin;
        if (visible) {
            placeCursor();
        } else {
            // FUN_001f7278(-1): scroll over 10 frames so the stop lands where the cursor was; the
            // first and last rows scroll to the bounds; a jump of more than a row snaps
            float target;
            if (sel == 0) target = 0;
            else if (sel == (int)mRows.size() - 1) target = mMax;
            else target = y - (mCurY - mScroll);
            target = std::min(std::max(target, 0.0f), mMax);
            if (std::abs(sel - prev) > 1) {
                mScroll = target;
                mScrollFrames = 0;
                placeCursor();
            } else {
                mScrollFrom = mScroll;
                mScrollTo = target;
                mScrollFrames = kScrollFrames;
                mScrollCount = 0;
            }
        }
    } else if (mRows.size() && sel >= 0 && mScrollFrames == 0 && mTouch == TIdle) {
        placeCursor();   // a toggled switch moves the cursor to its new side
    }
}

void CtrPanel::show() {
    if (!load()) return;
    if (mPhase == Opening || mPhase == Shown) return;
    PlayOptions o;
    o.groups = 1;
    o.loop = 0;
    mBg->play("FadeIn", o);
    mTop->play("FadeIn", o);
    PlayOptions m;
    m.loop = 0;
    mMask->play("FadeIn", m);
    mPhase = Opening;
    mPhaseFrame = 0;
    mLevel = ~0ull;   // the next set() starts the list fresh
}

void CtrPanel::hide() {
    if (mPhase == Hidden || mPhase == Closing) return;
    PlayOptions o;
    o.groups = 1;
    o.loop = 0;
    mBg->play("FadeOut00", o);
    if (mTop->hasAnimation("FadeOut00")) mTop->play("FadeOut00", o);
    PlayOptions m;
    m.loop = 0;
    mMask->play("FadeOut00", m);
    mPhase = Closing;
    mPhaseFrame = 0;
}

void CtrPanel::moveBy(float d) {
    mScroll = std::min(std::max(mScroll + d, 0.0f), mMax);
    // scroll ticks (scroll/sound.ts): one every 20 px of travel
    if (std::abs(mScroll - mLastScrollSound) > 20) {
        mLastScrollSound = mScroll;
        if (mSound) mSound("SE_CTR_COMMON_SCROLL_TEXT");
    }
}

float CtrPanel::knobY() const {
    const float share = mMax > 0 ? mScroll / mMax : 0;
    return mKnobCenter - mKnobTravel / 2 + mKnobTravel * (1 - share);
}

void CtrPanel::placeCursor() {
    if (mSel < 0) return;
    float x, y, w, h;
    stopRect(mSel, &x, &y, &w, &h);
    mCurX = x;
    mCurY = y;
    mCurW = w;
    mCurH = h;
}

void CtrPanel::tick() {
    if (mPhase == Hidden || !mLoaded) return;
    mPhaseFrame++;
    if (mPhase == Opening && mPhaseFrame >= kFadeInFrames) mPhase = Shown;
    if (mPhase == Closing && mPhaseFrame >= kFadeOutFrames) {
        mPhase = Hidden;
        return;
    }
    // D-pad scroll (panellist.ts scrollFrame): linear, the cursor glides to the stop meanwhile
    if (mScrollFrames > 0) {
        mScrollCount++;
        const float t = (float)mScrollCount / mScrollFrames;
        moveBy((1 - t) * mScrollFrom + t * mScrollTo - mScroll);
        float x, y, w, h;
        stopRect(mSel, &x, &y, &w, &h);
        const int left = mScrollFrames - mScrollCount;
        if (left <= 0) {
            mScrollFrames = 0;
            placeCursor();
        } else {
            mCurX += (x - mCurX) / left;
            mCurY += (y - mCurY) / left;
            mCurW += (w - mCurW) / left;
            mCurH += (h - mCurH) / left;
        }
    }
    if (mTouch == TInertia) {
        float v = mVelocity;
        bool stopped = false;
        if (v <= 0) { v += kFriction; stopped = v >= 0; }
        else { v -= kFriction; stopped = v <= 0; }
        if (stopped) { v = 0; mTouch = TIdle; }
        mVelocity = v;
        const float before = mScroll;
        moveBy(v);
        if (mScroll == before && v != 0) { mVelocity = 0; mTouch = TIdle; }
    }
    for (LayoutInstance* l : {mBg.get(), mTop.get(), mMask.get(), mSlider.get(), mCursor.get(), mClose.get()}) l->update(1);
    for (std::vector<Slot>* s : {&mButtons, &mToggles, &mValues})
        for (Slot& sl : *s) {
            if (sl.lay) sl.lay->update(1);
            if (sl.lay2) sl.lay2->update(1);
        }
}

PanelHit CtrPanel::touch(bool down, bool held, bool up, float x, float y) {
    PanelHit none;
    if (mPhase != Shown || !mLoaded) return none;
    auto rowAt = [&](float tx, float ty) {
        PanelHit h;
        // list space distance below the list top
        Pane* wrp = mBg->pane("N_Wrp_00");
        float wx = 0, wy = 0;
        if (wrp) paneOrigin(wrp, &wx, &wy);
        const float d = (wy + kViewHalf) - ty;
        for (size_t i = 0; i < mRows.size(); i++) {
            const float top = mRowTop[i];
            const float bodyTop = mRows[i].kind == PanelRow::Button ? top + 3.5f : top + 24;   // B_* boxes
            const float bodyH = mRows[i].kind == PanelRow::Button ? 33.0f : 38.0f;
            if (d < bodyTop || d > bodyTop + bodyH) continue;
            const float lx = tx - wx;
            if (mRows[i].kind == PanelRow::Toggle) {
                if (lx < -101 || lx > 101) return h;
                h.what = PanelHit::Row;
                h.row = (int)i;
                h.column = lx >= 0 ? 1 : 0;
                return h;
            }
            if (lx < -90 || lx > 90) return h;
            h.what = PanelHit::Row;
            h.row = (int)i;
            return h;
        }
        return h;
    };
    if (down) {
        mCursorShown = false;
        mScrollFrames = 0;
        if (hit(mClose.get(), "B_Btn_00", x, y)) {
            mPressHit.what = PanelHit::Close;
            mTouch = TPressed;
            mPressOnRow = true;
            mLastY = y;
            return none;
        }
        if (hit(mSlider.get(), "B_Slide_00", x, y)) {
            mTouch = TKnob;
            mGrab = y - knobY();
            PlayOptions o;
            o.groups = 1;
            o.loop = 0;
            mSlider->play("Select", o);
            return none;
        }
        if (hit(mBg.get(), "B_Slide_00", x, y)) {
            mPressHit = rowAt(x, y);
            mPressOnRow = mPressHit.what == PanelHit::Row;
            mTouch = TPressed;
            mLastY = y;
            return none;
        }
        mPressHit = PanelHit();
        mPressHit.what = PanelHit::Outside;
        mTouch = TPressed;
        mPressOnRow = false;
        mLastY = y;
        return none;
    }
    if (held) {
        switch (mTouch) {
            case TPressed: {
                float delta = y - mLastY;
                if (mPressHit.what == PanelHit::Close || mPressHit.what == PanelHit::Outside) break;
                if (mPressOnRow) {
                    if (std::abs(delta) <= kDragStart) break;
                    delta -= (delta > 0 ? 1 : -1) * kDragStart;
                }
                mLastY = y;
                mVelocity = delta;
                mTouch = TDrag;
                mPressHit = PanelHit();
                moveBy(delta);
                break;
            }
            case TDrag:
                mVelocity = y - mLastY;
                mLastY = y;
                moveBy(mVelocity);
                break;
            case TKnob:
                if (mKnobTravel > 0 && mMax > 0) {
                    // the list chases the knob: knob position -> scroll share
                    const float k = std::min(std::max(y - mGrab, mKnobCenter - mKnobTravel / 2), mKnobCenter + mKnobTravel / 2);
                    const float share = 1 - (k - (mKnobCenter - mKnobTravel / 2)) / mKnobTravel;
                    moveBy(share * mMax - mScroll);
                }
                break;
            default:
                break;
        }
        return none;
    }
    if (up) {
        const Touch was = mTouch;
        const PanelHit pressed = mPressHit;
        mPressHit = PanelHit();
        if (was == TDrag) { mTouch = TInertia; return none; }
        if (was == TKnob) {
            mTouch = TIdle;
            PlayOptions o;
            o.groups = 1;
            o.loop = 0;
            o.speed = -1;
            mSlider->play("Select", o);
            return none;
        }
        mTouch = TIdle;
        if (was != TPressed) return none;
        if (pressed.what == PanelHit::Close) {
            if (!hit(mClose.get(), "B_Btn_00", x, y)) return none;
            PlayOptions o;
            o.groups = 1;
            o.loop = 0;
            mClose->play("Decide", o);
            return pressed;
        }
        if (pressed.what == PanelHit::Outside) {
            return hit(mBg.get(), "B_Slide_00", x, y) ? none : pressed;
        }
        if (pressed.what == PanelHit::Row) {
            const PanelHit now = rowAt(x, y);
            if (now.row != pressed.row) return none;
            for (std::vector<Slot>* pool : {&mButtons, &mToggles, &mValues})
                for (Slot& s : *pool)
                    if (s.row == pressed.row) decided(s);
            return now;
        }
    }
    return none;
}

void CtrPanel::decided(Slot& s) {
    PlayOptions o;
    o.groups = 1;
    o.loop = 0;
    if (mRows[s.row].kind == PanelRow::Button && s.lay) s.lay->play("PtBtnM_Shr_00_Decide", o);
    if (mRows[s.row].kind == PanelRow::Value && s.lay2) s.lay2->play("PtBtnM_Shr_00_Decide", o);
    mCursor->play("Decide", o);
}

bool CtrPanel::hit(LayoutInstance* l, const char* pane, float x, float y) const {
    Pane* p = l ? l->pane(pane) : nullptr;
    if (!p) return false;
    for (const Pane* q = p; q; q = q->parent)
        if (!q->visible) return false;
    float px, py;
    paneOrigin(p, &px, &py);
    float lft, top, rgt, bot;
    p->rect(&lft, &top, &rgt, &bot);
    return x >= px + lft && x <= px + rgt && y <= py + top && y >= py + bot;
}

void CtrPanel::bindSlot(Slot& s, const PanelRow& row) {
    if (!s.fresh && s.shown == row) return;
    const bool kindChanged = s.fresh || s.shown.kind != row.kind;
    s.fresh = false;
    switch (row.kind) {
        case PanelRow::Button:
            if (kindChanged || s.shown.label != row.label) {
                setLabel(s.lay.get(), "T_Btn_00", row.label);
                setLabel(s.lay.get(), "T_Btn_01", row.label);
            }
            break;
        case PanelRow::Toggle:
            if (kindChanged || s.shown.label != row.label) setLabel(s.lay.get(), "T_Base_00", row.label);
            if (kindChanged) {
                s.lay->setVisible("P_IconR_00", false);   // the brightness entry's own icon
                for (const char* p : {"T_Off_00", "T_Off_01"}) setLabel(s.lay.get(), p, "OFF");
                for (const char* p : {"T_On_00", "T_On_01"}) setLabel(s.lay.get(), p, "ON");
            }
            // the decided look on the current side (panel.ts showToggles)
            holdGroups(s.lay.get(), "PtBtnT_Lgt_01_Decide", {row.on ? "G_On_00" : "G_Off_00"});
            holdGroups(s.lay.get(), "PtBtnT_Lgt_01_UnDecide", {row.on ? "G_Off_00" : "G_On_00"});
            break;
        case PanelRow::Value:
            if (kindChanged || s.shown.label != row.label) setLabel(s.lay.get(), "T_Base_00", row.label);
            if (kindChanged) {
                // the header only: its OFF / ON pair is replaced by the value button
                for (const char* p : {"P_IconR_00", "N_CntntsPos_00", "P_Base_00", "P_Base_01", "P_OffShdw_00", "P_OnShdw_00", "P_Off_00", "P_On_00"})
                    s.lay->setVisible(p, false);
            }
            if (kindChanged || s.shown.value != row.value) {
                setLabel(s.lay2.get(), "T_Btn_00", row.value);
                setLabel(s.lay2.get(), "T_Btn_01", row.value);
            }
            break;
    }
    s.shown = row;
    if (s.lay) s.lay->refresh();
    if (s.lay2) s.lay2->refresh();
}

// Gives every row on screen a part layout from its kind's pool (rows off screen hold none, so
// a long list costs no more than a screenful).
void CtrPanel::assignSlots() {
    const float viewTop = mScroll - 80, viewBottom = mScroll + kViewHeight + 80;
    for (std::vector<Slot>* pool : {&mButtons, &mToggles, &mValues})
        for (Slot& s : *pool) {
            const bool keep = s.row >= 0 && s.row < (int)mRows.size() && mRows[s.row].kind == s.shown.kind &&
                              mRowTop[s.row] + mRowH[s.row] >= viewTop && mRowTop[s.row] <= viewBottom;
            if (!keep) s.row = -1;
        }
    for (size_t i = 0; i < mRows.size(); i++) {
        if (mRowTop[i] + mRowH[i] < viewTop || mRowTop[i] > viewBottom) continue;
        std::vector<Slot>& pool = mRows[i].kind == PanelRow::Button ? mButtons : mRows[i].kind == PanelRow::Toggle ? mToggles : mValues;
        Slot* have = nullptr;
        for (Slot& s : pool) if (s.row == (int)i) have = &s;
        if (!have) {
            for (Slot& s : pool) if (s.row < 0) { have = &s; break; }
            if (!have) {
                pool.emplace_back();
                have = &pool.back();
                switch (mRows[i].kind) {
                    case PanelRow::Button: have->lay = A.create(kArchive, "PtBtnS_Cmn_00"); break;
                    case PanelRow::Toggle: have->lay = A.create(kArchive, "PtBtnT_Abl_00"); break;
                    case PanelRow::Value:
                        have->lay = A.create(kArchive, "PtBtnT_Abl_00");
                        have->lay2 = A.create(kArchive, "PtBtnS_Cmn_00");
                        break;
                }
                if (!have->lay || (mRows[i].kind == PanelRow::Value && !have->lay2)) {
                    pool.pop_back();
                    continue;
                }
            }
            have->row = (int)i;
            have->fresh = true;
        }
        bindSlot(*have, mRows[i]);
    }
}

// The row's part root under the list wrapper (N_Wrp_00, which carries the scroll and the fades).
void CtrPanel::placeSlot(Slot& s) {
    Pane* wrp = mBg->pane("N_Wrp_00");
    if (!wrp) return;
    float wx, wy;
    paneOrigin(wrp, &wx, &wy);
    const PanelRow& row = mRows[s.row];
    const float h = row.kind == PanelRow::Button ? 40.0f : 64.0f;
    const float cy = wy + kViewHalf - (mRowTop[s.row] + h / 2);
    const float alpha = wrp->childAlpha * (row.enabled ? 1.0f : 0.5f);
    if (Pane* r = s.lay->layout().root) {
        r->translate[0] = wx;
        r->translate[1] = cy;
        r->alpha = alpha;
        s.lay->layout().calculate();
    }
    if (s.lay2) {
        if (Pane* r = s.lay2->layout().root) {
            r->translate[0] = wx;
            r->translate[1] = cy - 8;
            r->alpha = alpha;
            s.lay2->layout().calculate();
        }
    }
}

void CtrPanel::applyScroll() {
    if (Pane* wrp = mBg->pane("N_Wrp_00")) wrp->translate[1] = mScroll;
    for (const char* n : {"N_Slide_00", "B_Slide_00"})
        if (Pane* p = mSlider->pane(n)) p->translate[1] = knobY();
    // background plates follow the position (FUN_00253fe4)
    float f[3];
    if (mScroll <= kPlateTop) f[0] = mScroll, f[1] = 0, f[2] = 0;
    else if (mMax - kPlateBottom <= mScroll) f[0] = kPlateTop, f[1] = kStripePeriod, f[2] = kPlateBottom - (mMax - mScroll);
    else f[0] = kPlateTop, f[1] = std::fmod(mScroll, kStripePeriod), f[2] = 0;
    for (int i = 0; i < 3; i++) if (mPlates[i]) mPlates[i]->seek(f[i]);
    mBg->refresh();
}

void CtrPanel::drawBottom(Renderer& r) {
    if (mPhase == Hidden || !mLoaded) return;
    mMask->layout().adaptWidth(mWidth, Layout::Adapt::Edge);
    applyScroll();
    mBg->layout().calculate();
    assignSlots();
    auto probe = [&](const char* n) { if (CtrHome::perfMark) { r.flushNow(); CtrHome::perfMark(n); } };
    probe("p.mask");
    r.drawLayout(mMask->layout());
    probe("p.bg");
    Pane* scene0 = mBg->pane("N_Scene_00");
    Pane* scene1 = mBg->pane("N_Scene_01");
    Pane* wrp = mBg->pane("N_Wrp_00");
    Pane* slideHost = mBg->pane("N_SlideBar_00");
    Pane* closeHost = mBg->pane("N_PetitBtn_00");
    if (scene0) r.drawPane(mBg->layout(), scene0);
    probe("p.rows");
    // rows, then the cursor, under the list wrapper
    for (std::vector<Slot>* pool : {&mButtons, &mToggles, &mValues})
        for (Slot& s : *pool) {
            if (s.row < 0) continue;
            placeSlot(s);
            r.drawLayout(s.lay->layout());
            if (s.lay2) r.drawLayout(s.lay2->layout());
        }
    if (wrp && mCursorShown && mSel >= 0) {
        float wx, wy;
        paneOrigin(wrp, &wx, &wy);
        Pane* root = mCursor->layout().root;
        root->translate[0] = wx + mCurX;
        root->translate[1] = wy + kViewHalf - mCurY;
        root->alpha = wrp->childAlpha;
        for (const char* n : {"W_CsrF_00", "W_CsrLgt_00"})
            if (Pane* w = mCursor->pane(n)) { w->size[0] = mCurW; w->size[1] = mCurH; }
        mCursor->layout().calculate();
        r.drawLayout(mCursor->layout());
    }
    probe("p.rest");
    if (slideHost && mMax > 0) {
        float sx, sy;
        paneOrigin(slideHost, &sx, &sy);
        Pane* root = mSlider->layout().root;
        root->translate[0] = sx;
        root->translate[1] = sy;
        root->alpha = slideHost->childAlpha;
        mSlider->layout().calculate();
        r.drawLayout(mSlider->layout());
    }
    if (scene1) r.drawPane(mBg->layout(), scene1);
    if (closeHost) {
        float cx, cy;
        paneOrigin(closeHost, &cx, &cy);
        Pane* root = mClose->layout().root;
        root->translate[0] = cx;
        root->translate[1] = cy;
        root->alpha = closeHost->childAlpha;
        mClose->layout().calculate();
        r.drawLayout(mClose->layout());
    }
}

void CtrPanel::drawTop(Renderer& r) {
    if (mPhase == Hidden || !mLoaded) return;
    mTop->layout().adaptWidth(mTopW, Layout::Adapt::Edge);
    mTop->layout().adaptHeight(mTopH, Layout::Adapt::Edge);   // the title band stays at the bottom edge
    r.drawLayout(mTop->layout());
}

void CtrPanel::releaseGl() {
    mButtons.clear();
    mToggles.clear();
    mValues.clear();
    mBg.reset();
    mTop.reset();
    mMask.reset();
    mSlider.reset();
    mCursor.reset();
    mClose.reset();
    mPlates[0] = mPlates[1] = mPlates[2] = nullptr;
    mLoaded = false;
    mLoadFailed = false;
    mPhase = Hidden;
    mLevel = ~0ull;
    mTitle.clear();
}

// ================================================================== CtrDialog

namespace {
const char* kDlgLayouts[] = {"Dlg_A_D_00", "Dlg_A_D_01", "Dlg_A_D_02"};
const char* kDlgClip = "Dlg_A_D_02_";
struct DlgButtonPanes { const char* group; const char* bounds; const char* texts[2]; };
const DlgButtonPanes kDlgButtons[2] = {
    {"Group_00", "Bounding_00", {"TextBox_00", "TextBox_01"}},
    {"Group_01", "Bounding_01", {"TextBox_02", "TextBox_03"}},
};
}  // namespace

CtrDialog::CtrDialog(Assets& assets, SoundFn sound) : A(assets), mSound(std::move(sound)) {}
CtrDialog::~CtrDialog() = default;

bool CtrDialog::load(int buttons) {
    buttons = std::min(std::max(buttons, 0), 2);
    if (mLay && mLayButtons == buttons) return true;
    mLay = A.create("dialog", kDlgLayouts[buttons]);
    mLayButtons = buttons;
    if (!mMaskTop) mMaskTop = A.create("dialogmask", "DlgMask_U_00");
    if (!mMaskBottom) mMaskBottom = A.create("dialogmask", "DlgMask_D_00");
    mText.clear();
    mButtonText.clear();
    mShownSel = -2;
    return mLay != nullptr;
}

void CtrDialog::set(const std::string& text, const std::vector<std::string>& buttons, int sel) {
    if (!load((int)buttons.size())) return;
    if (text != mText) {
        mText = text;
        Pane* body = mLay->pane("TextBoxDialog");
        mLay->setText("TextBoxDialog", wrapText(body, text));
        fitText(body);   // a word longer than the box is narrowed
    }
    if (buttons != mButtonText) {
        mButtonText = buttons;
        for (size_t i = 0; i < buttons.size() && i < 2; i++)
            for (const char* p : kDlgButtons[i].texts) setLabel(mLay.get(), p, buttons[i]);
        mShownSel = -2;
    }
    mSel = sel;
    if (mPhase == Shown && mSel != mShownSel && mLayButtons > 1) {
        // the selected button holds the Select look (the console's touch press)
        for (int i = 0; i < 2; i++) {
            PlayOptions o;
            o.groups = 2;
            o.groupNames = {kDlgButtons[i].group};
            o.loop = 0;
            o.paused = true;
            if (Animator* a = mLay->play(std::string(kDlgClip) + "Select", o)) a->seek(i == mSel ? 1e6f : 0);
        }
        mShownSel = mSel;
        mLay->refresh();
    }
}

void CtrDialog::setProgress(const std::string& text, float fraction) {
    if (fraction < 0) {
        mProgFrac = -1;
        mProgDrag = false;
        return;
    }
    if (!mProg) {
        mProg = A.create("sequence", "DlgProgBar_00");
        mBar = A.create("progbar", "ProgBar");
        mBarClip = nullptr;
        mProgText.clear();
        if (mBar) {
            PlayOptions o;
            o.loop = 0;
            o.paused = true;
            mBarClip = mBar->play("ProgStart", o);
        }
    }
    if (!mProg || !mBar) return;
    fraction = std::min(fraction, 1.0f);
    if (text != mProgText) {
        mProgText = text;
        Pane* body = mProg->pane("T_00");
        mProg->setText("T_00", wrapText(body, text));
        fitText(body);
    }
    if (fraction != mProgFrac) {
        mProgFrac = fraction;
        // ProgStart is two frames long: frame 0 empty, frame 1 full (boot.ts seeks it to the fraction)
        if (mBarClip) mBarClip->seek(fraction);
        mBar->refresh();
    }
}

void CtrDialog::placeProgress() {
    // hung under the window's N_Obj_00 (dialog.ts content) and the bar under N_ProgBar, so both
    // follow the window's fades
    Pane* obj = mLay ? mLay->pane("N_Obj_00") : nullptr;
    Pane* root = mProg ? mProg->layout().root : nullptr;
    if (!obj || !root) return;
    root->parent = obj;
    mProg->layout().calculate();
    Pane* slot = mProg->pane("N_ProgBar");
    Pane* barRoot = mBar ? mBar->layout().root : nullptr;
    if (!slot || !barRoot) return;
    barRoot->parent = slot;
    mBar->layout().calculate();
}

bool CtrDialog::barSpan(float* left, float* right, float* bottom, float* top) const {
    Pane* p = mBar ? mBar->pane("P_ProgBar_00") : nullptr;
    if (!p) return false;
    float px, py, l, t, r, b;
    paneOrigin(p, &px, &py);
    p->rect(&l, &t, &r, &b);
    *left = px + l;
    *right = px + r;
    *top = py + t;
    *bottom = py + b;
    return *right > *left;
}

float CtrDialog::progressTouch(bool down, bool held, bool up, float x, float y) {
    if (mPhase != Shown || mProgFrac < 0) { mProgDrag = false; return -1; }
    float l, r, b, t;
    if (!barSpan(&l, &r, &b, &t)) return -1;
    // the bar is 16 units tall: a finger gets a band twice that around it
    const float slack = 16;
    if (down) mProgDrag = x >= l - slack && x <= r + slack && y >= b - slack && y <= t + slack;
    if (up) { const bool was = mProgDrag; mProgDrag = false; if (!was) return -1; }
    if (!mProgDrag || !(down || held || up)) return -1;
    return std::clamp((x - l) / (r - l), 0.0f, 1.0f);
}

void CtrDialog::show() {
    if (!mLay || mPhase == Opening || mPhase == Shown) return;
    PlayOptions o;
    o.loop = 0;
    mLay->play(std::string(kDlgClip) + "FadeIn", o);
    for (LayoutInstance* m : {mMaskTop.get(), mMaskBottom.get()}) if (m) m->play("FadeIn", o);
    if (mSound) mSound("SE_CTR_COMMON_DIALOG");
    mPhase = Opening;
    mPhaseFrame = 0;
    mShownSel = -2;
}

void CtrDialog::hide(bool cancel) {
    if (!mLay || mPhase == Hidden || mPhase == Closing) return;
    const char* suffix = cancel ? "FadeOut01" : "FadeOut00";
    PlayOptions o;
    o.loop = 0;
    mLay->play(std::string(kDlgClip) + suffix, o);
    for (LayoutInstance* m : {mMaskTop.get(), mMaskBottom.get()}) if (m) m->play(suffix, o);
    mPhase = Closing;
    mPhaseFrame = 0;
}

void CtrDialog::tick() {
    if (mPhase == Hidden || !mLay) return;
    mPhaseFrame++;
    if (mPhase == Opening && mPhaseFrame >= kFadeInFrames) mPhase = Shown;
    if (mPhase == Closing && mPhaseFrame >= kFadeInFrames) { mPhase = Hidden; return; }
    for (LayoutInstance* l : {mLay.get(), mMaskTop.get(), mMaskBottom.get()}) if (l) l->update(1);
    if (mProgFrac >= 0 && mProg) mProg->update(1);
}

int CtrDialog::touch(bool down, bool up, float x, float y) {
    if (mPhase != Shown || !mLay) return -1;
    auto at = [&](float tx, float ty) {
        for (int i = 0; i < mLayButtons && i < 2; i++) {
            Pane* p = mLay->pane(kDlgButtons[i].bounds);
            if (!p) continue;
            float px, py, l, t, rr, b;
            paneOrigin(p, &px, &py);
            p->rect(&l, &t, &rr, &b);
            if (tx >= px + l && tx <= px + rr && ty <= py + t && ty >= py + b) return i;
        }
        return -1;
    };
    if (down) { mTouched = at(x, y); return -1; }
    if (up) {
        const int i = mTouched;
        mTouched = -1;
        if (i >= 0 && at(x, y) == i) {
            PlayOptions o;
            o.groups = 2;
            o.groupNames = {kDlgButtons[i].group};
            o.loop = 0;
            mLay->play(std::string(kDlgClip) + "Decide", o);
            return i;
        }
    }
    return -1;
}

void CtrDialog::drawBottom(Renderer& r) {
    if (mPhase == Hidden || !mLay) return;
    if (mMaskBottom) {
        mMaskBottom->layout().adaptWidth(mWidth, Layout::Adapt::Edge);
        r.drawLayout(mMaskBottom->layout());
    }
    mLay->layout().calculate();
    r.drawLayout(mLay->layout());
    if (mProgFrac >= 0 && mProg && mBar) {
        placeProgress();
        r.drawLayout(mProg->layout());
        r.drawLayout(mBar->layout());
    }
}

void CtrDialog::drawTop(Renderer& r) {
    if (mPhase == Hidden || !mMaskTop) return;
    mMaskTop->layout().adaptWidth(mTopW, Layout::Adapt::Edge);
    mMaskTop->layout().adaptHeight(mTopH, Layout::Adapt::Edge);
    r.drawLayout(mMaskTop->layout());
}

void CtrDialog::releaseGl() {
    // the progress layouts point into mLay (their roots' hosts): they go first
    mBar.reset();
    mProg.reset();
    mBarClip = nullptr;
    mProgText.clear();
    mProgFrac = -1;
    mProgDrag = false;
    mLay.reset();
    mMaskTop.reset();
    mMaskBottom.reset();
    mLayButtons = -1;
    mPhase = Hidden;
}

}  // namespace ctr
}  // namespace android
