// NanoMenuCtrSetup - the first-run setup wizard in the 3DS theme (NanoCtrSetup): nano's wizard
// state machine (NanoMenuSetupWizard.cpp: steps, keys, setup.sh) drawn with the console's own
// layouts on the top screen only, at native size. Each step: the title and its explanation on
// the left, the interactive part on the right (the console's list rows, or its dialog). The
// bottom panel stays black for the whole of setup (the render passes already skip it while the
// wizard runs), and the HOME Menu is not loaded until the wizard finishes.
#define LOG_TAG "GammaOSNano"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <string>
#include <vector>

#include <cutils/properties.h>
#include <log/log.h>
#include <utils/SystemClock.h>

#include <GLES2/gl2.h>

#include "NanoCtrHome.h"
#include "NanoCtrSetup.h"
#include "NanoI18n.h"
#include "NanoMenu.h"
#include "NanoMenuDrm.h"
#include "NanoMenuPS3.h"
#include "NanoMenuPS3Globe.h"
#include "NanoMenuShaders.h"
#include "NanoMenuStrings.h"

namespace android {

namespace {
// the console font's button glyphs (private use area of cbf_std)
const char* kGlyphA = "\xEE\x80\x80";
const char* kGlyphB = "\xEE\x80\x81";
// list identities for the panel (a new level starts at the top, the cursor on the selection)
constexpr uint64_t kLevelLanguage = 0x53e7051000ull;
constexpr uint64_t kLevelTimezone = 0x53e7051002ull;
constexpr uint64_t kLevelWizard = 0x53e7051010ull;
// the list rows (the panel's 320 x 240 space) on the right of the top screen: at this
// magnification the row buttons keep the console's size and the slider lands inside the screen
// (a taller top screen, 3:2 and 4:3, shows them up to full size)
constexpr float kListScaleMin = 0.85f, kListCy = -14.0f;
// the text column's share of the width, bounded so the rows keep their room on every aspect
constexpr float kTextColFrac = 0.37f, kTextColMin = 130.0f, kTextColMax = 200.0f;
// the rows' right margin: the slider is the outermost element, a thin track
constexpr float kListMargin = 8.0f;
// the panel's 320 unit space: the slider's x and the row buttons' half width
constexpr float kPanelSliderX = 117.0f, kPanelRowsHalf = 125.0f;
// the content rises 14 units as it fades in (and sinks out)
constexpr float kRise = 14.0f;
// the greeting's mark: the screen height less this margin, faint in the wall
constexpr float kMarkMargin = 18.0f, kMarkAlpha = 0.085f;
// the wall's colour (CmnFade_U_00's light), for the fade under the list's last rows
constexpr float kWallR = 229.0f / 255.0f, kWallG = 229.0f / 255.0f, kWallB = 236.0f / 255.0f;

std::string hintLine(std::initializer_list<std::pair<const char*, const char*>> items) {
    std::string s;
    for (const auto& it : items) {
        if (!s.empty()) s += "      ";
        s += it.first;
        s += " ";
        s += it.second;
    }
    return s;
}

}  // namespace

bool NanoMenu::ctrSetupThemed() const {
    return mCtrTheme && mSetupWizardActive && !mPs3BootActive && mCtrSetup && mCtrSetup->loaded();
}

bool NanoMenu::ctrSetupEnsure() {
    if (!mCtrTheme || mCtrSetupFailed) return false;
    if (mCtrSetup && mCtrSetup->loaded()) return true;
    const std::string root = ctrAssetRoot("lyt_launcher.cpk");
    if (root.empty()) {
        ALOGE("ctr setup: 3DS theme assets not found, the wizard keeps the shared chrome");
        mCtrSetupFailed = true;
        return false;
    }
    {
        // text at the magnification the panel gives the 400 x 240 top screen
        const float W = (float)mWidth, H = (float)mHeight;
        if (W > 0 && H > 0) ctr::Font::setVectorScale((int)ceilf(std::min(W / 400.0f, H / 240.0f) - 0.1f));
        ctr::Font::setGlyphCacheDir("/data/system/nano_cache/ctr_glyphs");
        ctr::setProgramCacheDir("/data/system/nano_cache/ctr_shaders");
    }
    if (!mCtrSetup) mCtrSetup = new ctr::CtrSetupScene(root);
    if (!mCtrSetup->load()) {
        ALOGE("ctr setup: layouts failed to load, the wizard keeps the shared chrome");
        delete mCtrSetup;
        mCtrSetup = nullptr;
        mCtrSetupFailed = true;
        return false;
    }
    mCtrSetupLastStep = -1;
    ALOGI("ctr setup: 3DS first-run setup at %s", root.c_str());
    return true;
}

void NanoMenu::ctrSetupRelease() {
    if (!mCtrSetup) return;
    mCtrSetup->releaseGl();
    delete mCtrSetup;
    mCtrSetup = nullptr;
    ps3globe::resetPlacement();
}

// The frame: the wizard's own tick (transitions, the boot wait, setup.sh), then the scene.
void NanoMenu::renderCtrSetup() {
    using Slot = ctr::CtrSetupScene;
    ctr::CtrSetupScene& S = *mCtrSetup;
    updateSetupTransition();
    setupStepJumpHook();
    // A "next" into the Wi-Fi step waits for the system to finish booting (the real scan needs
    // WifiService); the wait dialog below shows meanwhile and the step enters by itself.
    if (mSetupWifiWaitPending) {
        char bd[PROPERTY_VALUE_MAX] = {0};
        property_get("sys.boot_completed", bd, "0");
        if (bd[0] == '1') {
            mSetupWifiWaitPending = false;
            advanceSetupStep();
        }
    }
    if (!mSetupTransitioning && mSetupTransitionAlpha < 1.0f) {
        mSetupTransitionAlpha += 0.06f;
        if (mSetupTransitionAlpha >= 1.0f) { mSetupTransitionAlpha = 1.0f; mSetupSlideOffset = 0.0f; }
        else mSetupSlideOffset *= 0.85f;
    }
    if (mSetupStep == SETUP_WIFI) {
        if (mPs3WizActive) mSetupNetWizSeen = true;
        else if (mSetupNetWizSeen) {
            mSetupNetWizSeen = false;
            if (mPs3WizExit >= 0) advanceSetupStep(); else goBackSetupStep();
        }
    }
    setupInstallTick();
    setGlyphAtlasAA(true);

    // ---- geometry: the whole primary panel is the top screen, widened to its aspect ----
    const float W = (float)mWidth, H = (float)mHeight;
    // the test hook sys.gammaos.nano.ctr.screensize=WxH draws into a centred rectangle of that
    // size (a 4:3 or 3:2 panel checked on the Duo), black around it
    float rx = 0, ry = 0, rw = W, rh = H;
    const bool testRect = ctrTestScreenRect(W, H, &rx, &ry, &rw, &rh);
    const float hw = ctrScreenWidth(true, rw, rh), hh = ctrTopHeight(rw, rh);
    float ox, oy, ow, oh;
    ctrFit(hw, hh, rx, ry, rw, rh, &ox, &oy, &ow, &oh);
    const float scale = hw > 0 ? ow / hw : 1.0f;
    if (scale <= 0.0f) return;
    const float lw = std::max(400.0f, rw / scale), lh = std::max(240.0f, rh / scale);
    ctrFit(lw, lh, rx, ry, rw, rh, &ox, &oy, &ow, &oh);
    ctr::Font::setVectorScale((int)ceilf(scale - 0.1f));
    float proj[16];
    ctr::CtrHome::projection(proj, lw, lh, ox, oy, ow, oh, W, H, sDrmRotMat);
    GLint vp[4];
    glGetIntegerv(GL_VIEWPORT, vp);
    // layout units (origin the centre, y up) to panel pixels (origin top left)
    auto PX = [&](float x) { return ox + (x + lw / 2) * scale; };
    auto PY = [&](float y) { return oy + (lh / 2 - y) * scale; };
    const float L = -lw / 2, R = lw / 2, T = lh / 2, B = -lh / 2;
    const float margin = 16.0f;
    // the columns: the title and explanation on the left, the rows (or the side dialog) filling
    // the rest to the right margin; the rows grow with a taller screen
    const float textColW = std::clamp(lw * kTextColFrac, kTextColMin, kTextColMax);
    const float listLeft = L + textColW;
    const float listSpan = R - kListMargin - listLeft;
    // the panel's layouts never go under their 320 units: the magnification fits them to the span
    const float listScale = std::min(std::clamp(kListScaleMin + (lh - 240.0f) * (1.0f - kListScaleMin) / 60.0f, kListScaleMin, 1.0f),
                                     listSpan / 320.0f);
    const float listWidth = std::max(320.0f, listSpan / listScale);
    // the panel's slider is its outermost element, inside its right edge: the panel sits so the
    // slider lands at the margin (no dead band to its right)
    const float listCx = R - kListMargin - (kPanelSliderX + 6.0f) * listScale;
    const float rowsLeft = listCx - kPanelRowsHalf * listScale;   // the row buttons' left edge
    const float hintY = B + 16.0f, dotsY = B + 5.0f, titleY = T - 20.0f, descY = T - 40.0f;

    // ---- a step change: the console's decide / cancel sounds ----
    if (mCtrSetupLastStep != (int)mSetupStep) {
        if (mCtrSetupLastStep >= 0) S.play((int)mSetupStep > mCtrSetupLastStep ? "SE_CTR_COMMON_OK" : "SE_CTR_COMMON_CANCEL");
        mCtrSetupLastStep = (int)mSetupStep;
    }

    // ---- the content of the step; while a transition runs out, everything leaves ----
    const bool leaving = mSetupTransitioning;
    const float fade = std::clamp(mSetupTransitionAlpha, 0.0f, 1.0f);
    // the content fades up: it sits kRise lower while faded and rises as it comes in (nano's
    // horizontal slide offset is not used in this look)
    const float rise = -(1.0f - fade) * kRise;
    std::string title, desc, hint;
    std::string greet, lang;
    float greetScale = 1, greetDx = 0, greetDy = 0, greetAlpha = 0;
    bool wantPanel = false, wantDialog = false, wantHappy = false, showMark = false;
    // a greeting the console font cannot draw (Arabic) is drawn with nano's font instead
    bool greetNano = false;
    std::string greetPrompt;
    S.setDialogPlacement(0, rise, 1.0f, lw);
    S.dialog().setMaskHidden(false);
    // the rows are cut above the hint line and at the screen top, as the console's edges cut them
    S.setPanelPlacement(listCx, kListCy + rise, listScale, listWidth,
                        (hintY + 14.0f - kListCy - rise) / listScale, (titleY - 10.0f - kListCy - rise) / listScale);
    if (leaving) {
        S.dialog().setWait(std::string());
        S.dialog().hide(false);
        S.panel().hide();
        S.hideHappy();
    } else {
        switch (mSetupStep) {
        case SETUP_WELCOME: {
            float easeT, offX, offY, scaleMul;
            const char* word = nullptr;
            const char* langName = nullptr;
            const char* prompt = nullptr;
            setupGreetingTick(&easeT, &offX, &offY, &scaleMul, &word, &langName, &prompt);
            greet = word ? word : "";
            greetScale = 2.3f * scaleMul;
            greetDx = offX * 0.2f;
            greetDy = offY * 0.2f;
            greetAlpha = easeT * fade;
            lang = langName ? langName : "";
            // the prompt in the greeting's own language, fading with it
            greetPrompt = prompt ? prompt : tr(STR_SETUP_PRESS_A);
            greetNano = !S.canDraw(greet) || !S.canDraw(greetPrompt);
            if (greetNano) {
                // the console draws only the A glyph (left of where nano's text goes); the
                // words are placed below once their widths are measured
                hint = kGlyphA;
                greet.clear();
                lang.clear();
            } else {
                hint = hintLine({{kGlyphA, greetPrompt.c_str()}});
            }
            showMark = true;
            break;
        }
        case SETUP_LANGUAGE: {
            nanoSetLocale((NanoLocale)mLangSelected);   // live preview of the highlighted language
            title = tr(STR_SETUP_LANG_TITLE);
            desc = trDyn("Choose the language for menus and messages. You can change it later in System Settings.");
            std::vector<ctr::PanelRow> rows;
            for (int i = 0; i < LOCALE_COUNT; i++) {
                ctr::PanelRow r;
                r.label = nanoGetLocaleInfo((NanoLocale)i).nativeName;
                rows.push_back(r);
            }
            S.panel().set(title, rows, mLangSelected, kLevelLanguage);
            wantPanel = true;
            hint = hintLine({{kGlyphA, trDyn("Select")}, {kGlyphB, trDyn("Back")}});
            break;
        }
        case SETUP_TIMEZONE: {
            title = trDyn("Time Zone");
            std::vector<ctr::PanelRow> rows;
            for (const TimezoneEntry& z : mTzEntries) {
                ctr::PanelRow r;
                r.label = z.display;
                rows.push_back(r);
            }
            S.panel().set(title, rows, mTzSelected, kLevelTimezone);
            wantPanel = true;
            hint = hintLine({{kGlyphA, trDyn("Select")}, {kGlyphB, trDyn("Back")}, {"START", trDyn("Skip")}});
            break;
        }
        case SETUP_WIFI: {
            if (mSetupWifiWaitPending || !mPs3WizActive) {
                title = tr(STR_SETUP_WIFI_TITLE);
                S.dialog().setProgress(std::string(), -1);
                S.dialog().set(std::string(), {}, 0);
                S.dialog().setWait(tr(STR_SETUP_INSTALL_BOOT_WAIT));
                wantDialog = true;
            } else {
                // the net wizard: its lists as the rows on the right with their text rows as
                // the explanation on the left; its questions and waits as the dialog
                netWizardTick();
                CtrWizView v;
                ctrWizView(&v);
                title = v.title.empty() ? std::string(tr(STR_SETUP_WIFI_TITLE)) : v.title;
                if (v.kind == CtrWizView::List) {
                    std::vector<ctr::PanelRow> rows;
                    int sel = -1;
                    for (size_t i = 0; i < v.rows.size(); i++) {
                        const auto& w = v.rows[i];
                        if (w.text) {
                            if (w.label == title) continue;   // a page that repeats its own title
                            if (!desc.empty()) desc += "\n";
                            desc += w.label;
                            continue;
                        }
                        ctr::PanelRow r;
                        r.label = w.label;
                        if (!w.value.empty()) { r.kind = ctr::PanelRow::Value; r.value = w.value; }
                        r.enabled = w.act != -1;
                        if ((int)i == v.sel) sel = (int)rows.size();
                        rows.push_back(r);
                    }
                    S.panel().set(title, rows, sel, kLevelWizard + (uint64_t)mPs3WizId * 7 + rows.size());
                    wantPanel = true;
                    mCtrSetupPanelShown = true;
                } else if (v.kind == CtrWizView::Dialog || v.kind == CtrWizView::Wait) {
                    S.dialog().setProgress(std::string(), -1);
                    if (v.kind == CtrWizView::Wait) {
                        S.dialog().set(std::string(), {}, 0);
                        S.dialog().setWait(v.body);
                    } else {
                        S.dialog().setWait(std::string());
                        S.dialog().set(v.body, v.buttons, v.buttonSel);
                    }
                    wantDialog = true;
                    // a question over the list it came from keeps the list up under it
                    wantPanel = mCtrSetupPanelShown && S.panel().active();
                } else {
                    mCtrSetupPanelShown = false;
                    // a text page: the keyboard holds the field, the explanation names it (and
                    // the network a key is for)
                    if (!v.body.empty() && v.body != title) desc = v.body;
                }
                hint = hintLine({{kGlyphA, trDyn("OK")}, {kGlyphB, trDyn("Back")}, {"START", trDyn("Skip")}});
            }
            break;
        }
        case SETUP_INSTALLING: {
            title = tr(mSetupScriptDone ? STR_SETUP_INSTALL_DONE : STR_SETUP_INSTALL_TITLE);
            desc = trDyn("Installing the emulators, RetroArch and the bundled content. This takes a few minutes and continues by itself.");
            std::string last;
            {
                std::lock_guard<std::mutex> lk(mSetupLogMutex);
                for (int i = (int)mSetupLogLines.size() - 1; i >= 0 && last.empty(); i--) last = mSetupLogLines[i];
            }
            // setup.sh stamps its lines "[+12s] ..."; the stamp is noise here
            if (last.size() > 3 && last[0] == '[' && last[1] == '+') {
                size_t e = last.find("] ");
                if (e != std::string::npos && e < 10) last.erase(0, e + 2);
            }
            // the progress line under the explanation on the left, soft, wrapped by the column
            if (!mSetupScriptDone && !last.empty()) desc += "\n\n" + last;
            // the wait dialog on the right, at the width of the list column
            // (the dialog's card is 320 units: at this size it is kept inside the right margin)
            S.setDialogPlacement(std::min(listCx, R - margin - 160.0f * 0.74f), -10.0f + rise, 0.74f, listSpan / 0.74f);
            S.dialog().setMaskHidden(true);
            S.dialog().setProgress(std::string(), -1);
            if (!mSetupScriptDone) {
                S.dialog().set(std::string(), {}, 0);
                S.dialog().setWait(tr(STR_SETUP_INSTALL_WAIT));
            } else {
                S.dialog().setWait(std::string());
                std::string body = tr(STR_SETUP_INSTALL_DONE);
                if (mSetupScriptDoneMs > 0) {
                    int left = (int)((10000 - (elapsedRealtime() - mSetupScriptDoneMs) + 999) / 1000);
                    if (left < 0) left = 0; if (left > 10) left = 10;
                    char buf[96];
                    snprintf(buf, sizeof(buf), tr(STR_SETUP_INSTALL_AUTO), left);
                    body += "\n";
                    body += buf;
                }
                S.dialog().set(body, {trDyn("Continue")}, 0);
                hint = hintLine({{kGlyphA, trDyn("Continue")}});
            }
            wantDialog = true;
            break;
        }
        case SETUP_FINISH: {
            S.showHappy("GammaOS", std::string(tr(STR_SETUP_FINISH_TITLE)) + "\n" + tr(STR_SETUP_FINISH_SUB));
            wantHappy = true;
            hint = hintLine({{kGlyphA, tr(STR_SETUP_FINISH_PRESS_A)}});
            break;
        }
        default: break;
        }
        // the widgets a step does not want leave; the ones it wants come up once the stage is clear
        if (!wantPanel) S.panel().hide();
        if (!wantDialog) { S.dialog().setWait(std::string()); S.dialog().hide(false); }
        if (!wantHappy) S.hideHappy();
        const bool stageClear = (wantPanel || !S.panel().active()) && (wantDialog || !S.dialog().active()) &&
                                (wantHappy || !S.happyActive());
        if (stageClear) {
            if (wantPanel) S.panel().show();
            if (wantDialog) S.dialog().show();
        }
    }
    // ---- the texts: title and explanation at the top left, the hints bottom right ----
    // (the keyboard covers the lower screen: its own footer replaces the hints and the dots)
    const bool oskUp = mOskActive;
    // the full dialog (the boot wait, the wizard's questions) stands alone too: the title,
    // explanation, hints and dots leave while it is up (the installing step's side dialog,
    // placed beside the explanation, keeps them)
    const bool dialogAlone = wantDialog && mSetupStep != SETUP_INSTALLING;
    if (oskUp || dialogAlone) { hint.clear(); }
    if (dialogAlone) { title.clear(); desc.clear(); }
    const bool centredHint = mSetupStep == SETUP_WELCOME || mSetupStep == SETUP_FINISH;
    S.setText(Slot::Title, title, L + margin, titleY + rise, 0, 0, 1.0f, fade, false);
    S.setText(Slot::Desc, desc, L + margin, descY + rise, textColW - margin - 10.0f, 0, 1.0f, fade, true);
    // nano's font for a greeting the console font lacks: sizes from the panes' point sizes
    // (T_Top_00 22.5, T_Btm_00 17.5) at this magnification, centred like the console's texts
    const float nanoBig = 22.5f * greetScale * scale * 0.8f / FONT_CHAR_H;
    const float nanoSmall = 17.5f * scale * 0.8f / FONT_CHAR_H;
    const float glyphW = 20.0f;   // the A glyph's advance in layout units, plus its gap
    float promptW = 0;
    if (greetNano) promptW = measureText(greetPrompt.c_str(), nanoSmall) / scale;
    const float hintX = greetNano ? -(glyphW + promptW) / 2 : (centredHint ? 0.0f : R - margin);
    const int hintAlign = greetNano ? 0 : (centredHint ? 1 : 2);
    S.setText(Slot::Hint, hint, hintX, hintY + rise, 0, hintAlign, 1.0f, fade * 0.95f, true);
    S.setText(Slot::Greeting, greet, greetDx, 20.0f + greetDy + rise, 0, 1, greetScale, greetAlpha, false);
    S.setText(Slot::Lang, lang, greetDx * 0.6f, -14.0f + greetDy * 0.5f + rise, 0, 1, 1.0f, greetAlpha * 0.85f, true);
    S.tick();

    // ---- draw: the wall, the Earth on the time zone step, the widgets, the texts ----
    setUiBlend();
    drawQuad(0.0f, 0.0f, W, H, testRect ? 0.0f : kWallR, testRect ? 0.0f : kWallG, testRect ? 0.0f : kWallB, 1.0f);
    S.setBackdropAlpha(1.0f);
    S.drawBackdrop(proj, vp[0], vp[1], vp[2], vp[3], lw, lh);
    if (showMark) {
        // the GammaOS mark in the wall behind the greeting: the screen height less a margin,
        // faint, breathing with the greeting's drift
        int mw = 0, mh = 0;
        if (GLuint tex = S.markTexture(&mw, &mh)) {
            const float size = (lh - 2 * kMarkMargin) * scale;
            drawIconTex(tex, PX(-(lh - 2 * kMarkMargin) / 2 + greetDx * 0.15f), PY((lh - 2 * kMarkMargin) / 2 + greetDy * 0.15f),
                        size, size, 1.0f, 1.0f, 1.0f, kMarkAlpha * fade, 0.0f, /*flipV*/ false);
        }
    }
    if (mSetupStep == SETUP_TIMEZONE) {
        float dt = mFrameDt; if (dt < 0.0f) dt = 0.0f; if (dt > 0.1f) dt = 0.1f;
        ps3globe::tick(dt);
        float globeAlpha = 0.0f;
        if (mTzGlobeWarmFrames >= 0) {
            // the Earth's textures load on this thread on the first render: hold until a clean
            // frame passed, then start the fade (renderTimezoneGlobe does the same)
            ps3globe::init();
            if (ps3globe::ready() && ++mTzGlobeWarmFrames >= 2) {
                mTzGlobeFadeStart = mEffectTime;
                mTzGlobeWarmFrames = -1;
            }
        } else {
            globeAlpha = ps3TzFadeAlpha();
        }
        if (leaving) globeAlpha *= fade;
        if (globeAlpha > 0.001f) {
            // the disc in the left column over the wall (no space, only its halo), its centre
            // and radius as fractions of the panel the globe renders into
            { ps3::LayoutParams lp; lp.panelW = mWidth; lp.panelH = mHeight; lp.uiScale = mPs3UiScale; ps3::layoutCompute(lp); }
            // the Earth fills the column left of the rows, under the title, above the hints
            const float colRight = rowsLeft - 6.0f;
            const float colCx = (L + colRight) / 2;
            const float colW = colRight - L - 2 * margin;
            const float colTop = titleY - 16.0f, colBot = hintY + 14.0f;
            const float r = std::min(colW * 0.5f, (colTop - colBot) * 0.5f);
            const float cy = (colTop + colBot) / 2 + rise;
            ps3globe::setPlacement((PX(colCx)) / W, PY(cy) / H, r * scale / H, true);
            ps3globe::render(mWidth, mHeight, sDrmRotMat, globeAlpha);
        }
    }
    if (wantHappy || S.happyActive()) drawQuad(0.0f, 0.0f, W, H, 0.0f, 0.0f, 0.0f, 0.32f * fade);
    S.drawWidgets(proj, vp[0], vp[1], vp[2], vp[3], lw, lh);
    S.drawTexts(proj, vp[0], vp[1], vp[2], vp[3], lw, lh, 0, 0, 0.0f, 0.0f, 0.0f);
    if (greetNano) {
        const int prevOutline = mTextOutlineMode;
        mTextOutlineMode = 2;   // flat, like the console's text
        setUiBlend();
        const char* word = nullptr; const char* langName = nullptr;
        { float a, b, c, d; setupGreetingTick(&a, &b, &c, &d, &word, &langName); }
        const float inkR = 58.0f / 255.0f, inkG = 58.0f / 255.0f, inkB = 66.0f / 255.0f;
        const float softR = 92.0f / 255.0f, softG = 93.0f / 255.0f, softB = 106.0f / 255.0f;
        if (word) {
            const float w = measureText(word, nanoBig);
            drawText(word, PX(greetDx) - w / 2, PY(20.0f + greetDy + rise) - FONT_CHAR_H * nanoBig / 2, nanoBig,
                     inkR, inkG, inkB, greetAlpha);
        }
        if (langName) {
            const float w = measureText(langName, nanoSmall);
            drawText(langName, PX(greetDx * 0.6f) - w / 2, PY(-14.0f + greetDy * 0.5f + rise) - FONT_CHAR_H * nanoSmall / 2,
                     nanoSmall, softR, softG, softB, greetAlpha * 0.85f);
        }
        drawText(greetPrompt.c_str(), PX(hintX + glyphW), PY(hintY + rise) - FONT_CHAR_H * nanoSmall / 2, nanoSmall,
                 softR, softG, softB, fade * 0.95f);
        mTextOutlineMode = prevOutline;
    }
    if (mSetupStep != SETUP_FINISH && !oskUp && !dialogAlone) {
        // the step dots along the bottom edge: the console's blue, the current one solid
        const int n = (int)SETUP_STEP_COUNT;
        const float gap = 9.0f, d = 2.6f, y = dotsY;
        const float x0 = -(n - 1) * gap / 2;
        for (int i = 0; i < n; i++) {
            const bool on = i == (int)mSetupStep;
            const float sz = on ? d * 1.35f : d;
            const float x = x0 + i * gap;
            drawQuad(PX(x - sz / 2), PY(y + sz / 2), sz * scale, sz * scale, 47.0f / 255.0f, 138.0f / 255.0f, 214.0f / 255.0f,
                     (on ? 1.0f : 0.35f) * fade);
        }
    }
    setUiBlend();
}

}  // namespace android
