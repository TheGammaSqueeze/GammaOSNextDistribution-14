/*
 * NanoMenuMinima.cpp - "Minima" home theme for gammaos-nano.
 *
 * A minimal list launcher inspired by NextUI (LoveRetro/NextUI, itself descended from MinUI):
 * a full-screen vertical TEXT list on a pure-black canvas, the selected row wrapped in a white
 * rounded capsule pill with inverted (black) text, a small accent status pill top-right and an
 * accent button-hint bar along the bottom. Like the DSi theme, Minima rides the shared PS3 XMB
 * state machine (mPs3Cats / mPs3Stack / mNdsAtRoot and the nds* nav helpers) and only swaps the
 * home render, the top-level nav mapping, the nav SFX and the boot animation - every modal
 * (options, dialogs, pickers, OSK, media players) still reuses the existing chrome via the
 * ndsInModal() delegation, so the whole menu tree works for free.
 *
 * Faithful NextUI proportions (authored at its unscaled 256-tall reference and scaled to the
 * panel): PILL_SIZE 30 row height, PADDING 10, BUTTON_MARGIN 5, BUTTON_PADDING 12, list font 16.
 * Everything derives from scale = panelHeight / 256 so it adapts to any resolution / orientation /
 * aspect exactly as the web app does, with no hardcoded panel size.
 */

#include "NanoMenu.h"
#include "NanoMenuShaders.h"   // FONT_CHAR_H

#include <utils/SystemClock.h> // uptimeMillis
#include <cmath>
#include <cstdio>
#include <cstring>
#include <ctime>
#include <string>
#include <vector>

namespace android {

// NextUI unscaled reference constants (common/defines.h). Scaled by panelH/MIN_REF_H at render time.
// A larger reference height = smaller, more condensed UI (more rows on screen). 336 gives ~8-9 rows
// on a 480-tall panel (NextUI's Brick reference is 256; the user asked for a tighter, smaller list).
static constexpr float MIN_REF_H     = 336.0f;  // controls the whole-UI density (bigger = smaller/tighter)
static constexpr float MIN_PILL      = 30.0f;   // PILL_SIZE (row height, capsule height)
static constexpr float MIN_PAD       = 10.0f;   // PADDING (screen-edge inset)
static constexpr float MIN_BTNMARGIN = 5.0f;    // BUTTON_MARGIN
static constexpr float MIN_BTNPAD    = 12.0f;   // BUTTON_PADDING (text inset inside the pill)
static constexpr float MIN_FONT      = 16.0f;   // font.large (list rows)
static constexpr float MIN_FONT_S    = 12.0f;   // font.small (hints / clock)

// Resolve the Minima accent colour. Follows the shared "Colour" theme setting (mPs3ColorIdx /
// kPs3ColorOpts) exactly like the XMB, so the user's colour choice drives it. When left at
// "Original" (index 0) it defaults to NextUI's signature berry #9B2257 rather than the XMB's
// per-month hue, so Minima looks like NextUI out of the box. (Defined in NanoMenuPS3Menu.cpp
// where kPs3ColorOpts is visible.)

void NanoMenu::minimaSfxTick() {
    // Fire exactly one nav/drill/back sound per frame by diffing this frame's focus vs last.
    if (ndsInModal()) { mMinimaSfxDepth = ndsNavDepth(); mMinimaSfxSel = ndsFocusSel(); return; }
    int depth = ndsNavDepth();
    int sel   = ndsFocusSel();
    if (mMinimaSfxDepth < 0) { mMinimaSfxDepth = depth; mMinimaSfxSel = sel; return; }
    if (depth > mMinimaSfxDepth)      minimaSfx(MIN_SFX_DRILL);   // drilled a level deeper
    else if (depth < mMinimaSfxDepth) minimaSfx(MIN_SFX_BACK);    // walked back up
    else if (sel != mMinimaSfxSel)    minimaSfx(MIN_SFX_CURSOR);  // moved within the list
    mMinimaSfxDepth = depth; mMinimaSfxSel = sel;
}

void NanoMenu::renderMinima() {
    // Single interactive list, full panel. The dual-screen (RG DS) secondary panel is filled with
    // just the backdrop by the render() dispatch, so there is only ever one list to drive.
    renderMinimaList(0.0f, 0.0f, (float)mWidth, (float)mHeight);
}

void NanoMenu::renderMinimaList(float rx, float ry, float rw, float rh) {
    setUiBlend();
    const int minPrevOutline = mTextOutlineMode; mTextOutlineMode = 2;   // Minima text is flat: no drop shadow / outline
    if (!mPs3MenuBuilt) initPs3Menu();   // build the shared XMB hierarchy that feeds the rows
    minimaSfxTick();

    // ---- background ----
    // Black by default (the NextUI canvas), but the user can opt into the XMB wave / effects / a custom
    // wallpaper or video via Theme Settings - then renderEffect() draws whatever they chose. In an
    // in-game overlay with no overlay wallpaper, nano's framebuffer clear already lays down the dark
    // app-dimming scrim (like the XMB/DSi overlay), so leave it and let the app show through dimmed.
    const bool inGameScrim  = mOverlayMode && !mOverlayWallpaper;
    const bool wantEffectBg = wallpaperActive(mRenderingPanel) || (mXmbWave && mXmbWaveExplicit);
    if (inGameScrim) {
        // leave the framebuffer's app-dimming scrim untouched; the white list draws over it
    } else if (wantEffectBg) {
        renderEffect();                                       // XMB wave / gradient / wallpaper / video per settings
        drawQuad(rx, ry, rw, rh, 0.0f, 0.0f, 0.0f, 0.34f);    // readability scrim so the white list stays crisp
    } else {
        drawQuad(rx, ry, rw, rh, 0.0f, 0.0f, 0.0f, 1.0f);     // pure-black NextUI canvas (default; black stays available)
    }
    mTextOutlineMode = 2;   // re-assert flat text after renderEffect (which sets its own outline mode)

    float ar, ag, ab; minimaAccent(ar, ag, ab);   // accent = the Colour setting (berry by default)
    // Accent-pill text: black on a light accent (Yellow/White/Lime...), white on a dark one (berry),
    // so the status/hint legends stay legible for every colour preset.
    const float accLum = 0.299f * ar + 0.587f * ag + 0.114f * ab;
    const float atc = (accLum > 0.62f) ? 0.0f : 1.0f;

    // ---- adaptive scale (NextUI SCALE1 model): everything = unscaled * (panelH / 256) ----
    const float sc      = rh / MIN_REF_H;
    const float pad     = MIN_PAD * sc;
    const float rowH    = MIN_PILL * sc;
    const float btnPad  = MIN_BTNPAD * sc;
    const float btnMg   = MIN_BTNMARGIN * sc;
    const float fsRow   = (MIN_FONT * sc) / (float)FONT_CHAR_H;
    const float fsHint  = (MIN_FONT_S * sc) / (float)FONT_CHAR_H;

    // ---- resolve the current level's rows (categories at root, else the category/submenu items) ----
    std::vector<std::string> rows;
    int sel = 0;
    if (mNdsAtRoot) {
        for (auto& c : mPs3Cats) rows.push_back(c.name);
        sel = mPs3CatIdx;
    } else if (!mPs3Stack.empty()) {
        for (auto& it : mPs3Stack.back().items) rows.push_back(it.label);
        sel = mPs3Stack.back().sel;
    } else if (mPs3CatIdx >= 0 && mPs3CatIdx < (int)mPs3Cats.size()) {
        for (auto& it : mPs3Cats[mPs3CatIdx].items) rows.push_back(it.label);
        sel = mPs3ItemIdx;
    }
    const int n = (int)rows.size();
    if (sel < 0) sel = 0; if (n > 0 && sel >= n) sel = n - 1;

    // ---- list geometry: fills from the top inset down to above the hint bar ----
    const float listLeft   = rx + pad + btnMg;
    const float listTop    = ry + pad;
    const float hintTop    = ry + rh - pad - rowH;          // hint bar occupies the bottom PILL_SIZE band
    const float listBottom = hintTop - btnMg;
    const int   visRows    = (int)fmaxf(1.0f, floorf((listBottom - listTop) / rowH));
    const float textMaxW   = (rx + rw - pad) - listLeft - btnPad;   // room before the right edge

    // ---- ease the scroll window and the pill toward the selection (NextUI ~3-frame glide) ----
    const float dt = fmaxf(0.0f, fminf(0.1f, mFrameDt));
    float targetScroll = 0.0f;
    if (n > visRows) {
        // keep the selection inside the window, biased to centre like NextUI's paged list
        targetScroll = (float)sel - (float)(visRows / 2);
        const float maxScroll = (float)(n - visRows);
        if (targetScroll < 0.0f) targetScroll = 0.0f;
        if (targetScroll > maxScroll) targetScroll = maxScroll;
    }
    // exponential approach tuned to settle in ~3 frames at 60fps (NextUI's 3-step linear glide feel)
    const float k = 1.0f - powf(1.0f - 0.55f, dt * 60.0f);
    mMinimaScroll  += (targetScroll - mMinimaScroll) * k;
    mMinimaSelAnim += ((float)sel - mMinimaSelAnim) * k;
    if (fabsf(mMinimaScroll - targetScroll)  > 0.002f) mDisplayDirty = true; else mMinimaScroll = targetScroll;
    if (fabsf(mMinimaSelAnim - (float)sel)   > 0.002f) mDisplayDirty = true; else mMinimaSelAnim = (float)sel;

    // ---- detect a level change (drill/back) to arm the slide + crossfade, and snap the window so
    // the new level starts from a clean position (done before the draw so the motion is same-frame) ----
    {
        int depth = ndsNavDepth();
        if (mMinimaPrevDepth < 0) mMinimaPrevDepth = depth;
        else if (depth != mMinimaPrevDepth) {
            mMinimaTransStart = (int64_t)uptimeMillis();
            mMinimaTransDir = (depth > mMinimaPrevDepth) ? +1 : -1;
            mMinimaPrevDepth = depth;
            mMinimaScroll = targetScroll; mMinimaSelAnim = (float)sel;   // snap the window on a level change
        }
    }

    // ---- horizontal slide on a level change (NextUI folder slide: drill enters from the right,
    // back enters from the left, cubic ease-out over 150ms). The rows + pill share this x offset;
    // the crossfade is the black wash stamped in the transition block below. ----
    float slideX = 0.0f;
    if (mMinimaTransStart > 0) {
        float t = (float)((int64_t)uptimeMillis() - mMinimaTransStart) / 150.0f;
        if (t < 1.0f) {
            float ease = 1.0f - powf(1.0f - t, 3.0f);                    // NextUI TRANSITION_CURVE (1-(1-t)^3)
            slideX = (float)mMinimaTransDir * (rw * 0.22f) * (1.0f - ease);
            mDisplayDirty = true;
        }
    }
    const float lx = listLeft + slideX;

    // ---- draw the rows (white text) then the gliding white capsule + inverted selected label ----
    if (n == 0) {
        const char* empty = "Empty";
        float tw = measureText(empty, fsRow);
        drawText(empty, rx + rw * 0.5f - tw * 0.5f, listTop + rowH * 0.5f, fsRow, 1.0f, 1.0f, 1.0f, 0.55f);
    }
    for (int i = 0; i < n; i++) {
        float rowY = listTop + ((float)i - mMinimaScroll) * rowH;
        if (rowY + rowH < listTop - 1.0f || rowY > listBottom + 1.0f) continue;   // clip to the list band
        if (i == sel) continue;                                                    // selected drawn on the pill below
        float ty = rowY + (rowH - MIN_FONT * sc) * 0.5f;
        float fs = fsRow, tw = measureText(rows[i].c_str(), fs);
        if (tw > textMaxW && textMaxW > 0.0f) { fs *= textMaxW / tw; }
        drawText(rows[i].c_str(), lx + btnPad, ty, fs, 1.0f, 1.0f, 1.0f, 1.0f);   // COLOR_LIST_TEXT white
    }
    // the capsule pill, hugging the selected label, glided to the eased position
    if (n > 0) {
        const std::string& lbl = rows[sel < n ? sel : 0];
        float fs = fsRow, tw = measureText(lbl.c_str(), fs);
        if (tw > textMaxW && textMaxW > 0.0f) { fs *= textMaxW / tw; tw = measureText(lbl.c_str(), fs); }
        float pillY = listTop + (mMinimaSelAnim - mMinimaScroll) * rowH + rowH * 0.07f;
        float pillH = rowH * 0.86f;
        float pillW = tw + btnPad * 2.0f;
        float maxPillW = (rx + rw - pad) - listLeft;
        if (pillW > maxPillW) pillW = maxPillW;
        drawRoundedRect(lx, pillY, pillW, pillH, pillH * 0.5f, 1.0f, 1.0f, 1.0f, 1.0f);   // white capsule
        float ty = pillY + (pillH - MIN_FONT * sc) * 0.5f;
        drawText(lbl.c_str(), lx + btnPad, ty, fs, 0.0f, 0.0f, 0.0f, 1.0f);   // COLOR_LIST_TEXT_SELECTED black
    }

    // ---- status pill (top-right): accent capsule with the clock (+ battery when known) ----
    {
        char clockbuf[16] = {};
        time_t tt = time(nullptr); struct tm lt; localtime_r(&tt, &lt);
        strftime(clockbuf, sizeof(clockbuf), "%H:%M", &lt);
        std::string status;
        if (mBatteryPercent >= 0) { char b[8]; snprintf(b, sizeof(b), "%d%% ", mBatteryPercent); status += b; }
        status += clockbuf;
        float tw = measureText(status.c_str(), fsHint);
        float ph = rowH;
        float pw = tw + btnPad * 2.0f;
        float px = rx + rw - pad - pw;
        float py = ry + pad;
        drawRoundedRect(px, py, pw, ph, ph * 0.5f, ar, ag, ab, 1.0f);   // accent status pill
        float ty = py + (ph - MIN_FONT_S * sc) * 0.5f;
        drawText(status.c_str(), px + btnPad, ty, fsHint, atc, atc, atc, 1.0f);   // legible on the accent
    }

    // ---- bottom hint bar: accent pills with the button legend ("A Open / B Back") ----
    {
        struct Hint { const char* btn; const char* act; };
        // At root there is nothing to go back to (B exits to app/overlay); deeper, B walks up.
        Hint right = { "A", mNdsAtRoot ? "Open" : "Open" };
        Hint left  = { "B", mNdsAtRoot ? "Back" : "Back" };
        float ph = rowH, py = hintTop;
        auto drawHint = [&](const Hint& h, bool rightAlign) {
            std::string txt = std::string(h.btn) + "  " + h.act;
            float tw = measureText(txt.c_str(), fsHint);
            float pw = tw + btnPad * 2.0f;
            float px = rightAlign ? (rx + rw - pad - pw) : (rx + pad);
            drawRoundedRect(px, py, pw, ph, ph * 0.5f, ar, ag, ab, 1.0f);
            float ty = py + (ph - MIN_FONT_S * sc) * 0.5f;
            drawText(txt.c_str(), px + btnPad, ty, fsHint, atc, atc, atc, 1.0f);
        };
        drawHint(right, true);
        drawHint(left, false);
    }

    // ---- level-change black-wash crossfade (paired with the horizontal slide above) ----
    if (mMinimaTransStart > 0) {
        float a = 1.0f - (float)((int64_t)uptimeMillis() - mMinimaTransStart) / 150.0f;
        if (a > 0.0f) { drawQuad(rx, ry, rw, rh, 0.0f, 0.0f, 0.0f, a); mDisplayDirty = true; }
        else mMinimaTransStart = 0;
    }
    // launch white-wash carried through from the home (an item can launch a game/app)
    if (!mOverlayMode && mLaunchFadeStart > 0) {
        float lf = (float)((int64_t)uptimeMillis() - mLaunchFadeStart) / (1000.0f / 60.0f);
        float fa = (lf - 3.0f) / 44.0f; if (fa < 0.0f) fa = 0.0f; if (fa > 1.0f) fa = 1.0f;
        if (fa > 0.0f) { drawQuad(rx, ry, rw, rh, 1.0f, 1.0f, 1.0f, fa); mDisplayDirty = true; }
    }
    mTextOutlineMode = minPrevOutline;
}

// RG DS bottom panel. The interactive list lives on the primary (top) screen; the secondary panel
// echoes the context in Minima's language (like the NDS top screen): the current category/level as an
// accent header, the focused item's BOXART (async/cached) - or its icon - centred, and the item label
// below, over the Minima backdrop (bottom wallpaper if set, else pure black).
void NanoMenu::renderMinimaSecondary(float rx, float ry, float rw, float rh) {
    setUiBlend();
    const int minPrevOutline = mTextOutlineMode; mTextOutlineMode = 2;   // flat text: no drop shadow
    const bool inGameScrim = mOverlayMode && !mOverlayWallpaper;
    bool drew = false;
    if (wallpaperActive(1)) { drawWallpaperFill(1); drew = true; }
    if (drew)               drawQuad(rx, ry, rw, rh, 0.0f, 0.0f, 0.0f, 0.30f);   // readability scrim over a wallpaper
    else if (!inGameScrim)  drawQuad(rx, ry, rw, rh, 0.0f, 0.0f, 0.0f, 1.0f);    // pure-black backdrop (no app behind)

    float ar, ag, ab; minimaAccent(ar, ag, ab);
    const float sc = rh / MIN_REF_H;
    const float cx = rx + rw * 0.5f;

    // Resolve the current level's title and the focused item, like the NDS top screen.
    std::string head, sub; const Ps3Item* selItem = nullptr;
    if (mNdsAtRoot) {
        head = "GammaOS";
        if (mPs3CatIdx >= 0 && mPs3CatIdx < (int)mPs3Cats.size()) sub = mPs3Cats[mPs3CatIdx].name;
    } else if (!mPs3Stack.empty()) {
        head = mPs3Stack.back().title;
        const auto& its = mPs3Stack.back().items; int s = mPs3Stack.back().sel;
        if (s >= 0 && s < (int)its.size()) { sub = its[s].label; selItem = &its[s]; }
    } else if (mPs3CatIdx >= 0 && mPs3CatIdx < (int)mPs3Cats.size()) {
        head = mPs3Cats[mPs3CatIdx].name;
        const auto& its = mPs3Cats[mPs3CatIdx].items; int s = mPs3ItemIdx;
        if (s >= 0 && s < (int)its.size()) { sub = its[s].label; selItem = &its[s]; }
    }
    if (head.empty()) head = "GammaOS";

    // Boxart for the focused game (async / cached, exactly like the XMB column + NDS top screen),
    // else fall back to the item's own icon (or the category icon at root).
    std::string romPath;
    if (selItem) {
        if (selItem->kind == PS3_ROM && selItem->a >= 0 && selItem->a < (int)mXmbSystems.size()
            && selItem->b >= 0 && selItem->b < (int)mXmbSystems[selItem->a].roms.size())
            romPath = mXmbSystems[selItem->a].roms[selItem->b];
        else if (selItem->kind == PS3_RECENT && selItem->a >= 0 && selItem->a < (int)mXmbRecent.size())
            romPath = mXmbRecent[selItem->a].romPath;
    }
    GLuint boxTex = 0; float boxAR = 1.0f;
    if (!romPath.empty() && scraperBoxartEnabled()) boxTex = romBoxartTex(romPath, &boxAR);
    GLuint iconTex = selItem ? selItem->iconTex
                   : (mNdsAtRoot && mPs3CatIdx >= 0 && mPs3CatIdx < (int)mPs3Cats.size() ? mPs3Cats[mPs3CatIdx].iconTex : 0);

    // category header (accent, top)
    {
        float fsH = (22.0f * sc) / (float)FONT_CHAR_H;
        float tw = measureText(head.c_str(), fsH);
        drawText(head.c_str(), cx - tw * 0.5f, ry + rh * 0.08f, fsH, ar, ag, ab, 1.0f);
    }
    // boxart (or the item/category icon) centred
    {
        float artMaxH = rh * 0.50f, artMaxW = rw * 0.70f;
        if (boxTex) {
            float ah = artMaxH, aw = artMaxH * boxAR;
            if (aw > artMaxW) { aw = artMaxW; ah = aw / (boxAR > 0.01f ? boxAR : 1.0f); }
            float axx = cx - aw * 0.5f, ayy = ry + rh * 0.22f;
            drawRoundedRect(axx - 4.0f * sc, ayy - 4.0f * sc, aw + 8.0f * sc, ah + 8.0f * sc, 8.0f * sc, 1.0f, 1.0f, 1.0f, 0.10f);
            drawIconTex(boxTex, axx, ayy, aw, ah, 1.0f, 1.0f, 1.0f, 1.0f);
        } else if (iconTex) {
            float isz = rh * 0.34f;
            drawIconTex(iconTex, cx - isz * 0.5f, ry + rh * 0.26f, isz, isz, 1.0f, 1.0f, 1.0f, 1.0f);
        }
    }
    // focused item label (white, bottom)
    if (!sub.empty()) {
        float fsS = (20.0f * sc) / (float)FONT_CHAR_H;
        float tw = measureText(sub.c_str(), fsS);
        float maxW = rw * 0.90f;
        if (tw > maxW && maxW > 0.0f) { fsS *= maxW / tw; tw = measureText(sub.c_str(), fsS); }
        drawText(sub.c_str(), cx - tw * 0.5f, ry + rh * 0.82f, fsS, 1.0f, 1.0f, 1.0f, 1.0f);
    }
    mTextOutlineMode = minPrevOutline;
}

} // namespace android
