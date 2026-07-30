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
#include "NanoI18n.h"          // trDyn

#include <cutils/properties.h> // property_get (Background Colour)
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

// Solid background colour (Theme Settings > Background Colour). The prop holds either "none"
// (default: pure-black NextUI canvas) or a 6-digit hex RGB. Returns true + the parsed colour when
// a solid colour is set, so the Minima backdrop fills with it instead of black / the wave. Read
// live each frame so a chooser change applies immediately (property_get is a cheap shmem read).
bool NanoMenu::minimaSolidBg(float* r, float* g, float* b) {
    char v[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.minima.bg", v, "none");
    if (v[0] == '\0' || !strcmp(v, "none") || !strcmp(v, "black") || !strcmp(v, "0")) return false;
    unsigned int rr = 0, gg = 0, bb = 0;
    if (strlen(v) >= 6 && sscanf(v, "%2x%2x%2x", &rr, &gg, &bb) == 3) {
        if (r) *r = (float)rr / 255.0f;
        if (g) *g = (float)gg / 255.0f;
        if (b) *b = (float)bb / 255.0f;
        return true;
    }
    return false;
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
    // Black by default (the NextUI canvas). A custom photo / video wallpaper is drawn DIRECTLY when
    // set (so it always shows, regardless of the XMB Wave toggle - renderEffect would otherwise paint
    // the wave over it). Otherwise, the user can opt into the XMB wave / effects via Theme Settings.
    // In an in-game overlay with no overlay wallpaper, nano's framebuffer clear already lays down the
    // dark app-dimming scrim (like the XMB/DSi overlay), so leave it and let the app show through.
    const bool inGameScrim = mOverlayMode && !mOverlayWallpaper;
    float bgR, bgG, bgB;
    if (inGameScrim) {
        // leave the framebuffer's app-dimming scrim untouched; the white list draws over it
    } else if (wallpaperActive(mRenderingPanel)) {
        // drawWallpaperFill / drawTopVideoWallpaper already apply the adjustable "Wallpaper Dimming" scrim,
        // which keeps the white list legible over a bright wallpaper (the user tunes it up for Minima).
        if (!(mRenderingPanel == 0 && drawTopVideoWallpaper()))   // looping video on top, else the still
            drawWallpaperFill(mRenderingPanel);
    } else if (minimaSolidBg(&bgR, &bgG, &bgB)) {
        drawQuad(rx, ry, rw, rh, bgR, bgG, bgB, 1.0f);            // user-chosen solid backdrop colour
    } else if (mXmbWave && mXmbWaveExplicit) {
        renderEffect();                                           // opt-in XMB wave / gradient background
        drawQuad(rx, ry, rw, rh, 0.0f, 0.0f, 0.0f, 0.34f);
    } else {
        drawQuad(rx, ry, rw, rh, 0.0f, 0.0f, 0.0f, 1.0f);         // pure-black NextUI canvas (default)
    }
    mTextOutlineMode = 2;   // re-assert flat text after renderEffect (which sets its own outline mode)

    // ---- scrolling fanart hover background for a focused scraped game (same as the XMB) ----
    // A focused game with scraped fanart shows it as a slow Ken-Burns background under the list (a
    // 50% scrim keeps the white rows legible). Empty fanFile when the focus is not a scraped game, so
    // drawPs3CinfoBg fades it back out. mNdsAtRoot has no focused ROM, so focusedScrapeEntry returns null.
    const ScrapeEntry* minSe = inGameScrim ? nullptr : focusedScrapeEntry();
    if (!inGameScrim) {
        std::string fanFile = (minSe && scraperFanartEnabled() && !minSe->fan.empty()) ? minSe->fan : std::string();
        drawPs3CinfoBg("", fanFile);   // focusLabel is only used for the Photo Gallery cinfo (never a game)
        mTextOutlineMode = 2;          // drawPs3CinfoBg touches GL state; re-assert flat text
        // Minima only re-renders when dirty (no always-on wave), so keep the loop live while the fanart
        // is on screen - otherwise its Ken-Burns pan + fade-out freeze on the last drawn frame.
        if (mCinfoAlpha > 0.001f) mDisplayDirty = true;
    }

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
    // vals holds the inline right-aligned value for value-bearing rows (else empty). Only the
    // game-system rows (the GS_LIST On/Off list + the GS_EDITOR fields) expose their value so the
    // enable state and per-system config read at a glance; generic settings rows stay value-less
    // (enter to change), matching the DSi list decision.
    std::vector<std::string> rows;
    std::vector<std::string> vals;
    auto pushItem = [&](const Ps3Item& it) {
        rows.push_back(it.label);
        if (it.kind == PS3_GS_SYSTEM_ROW || it.kind == PS3_GS_FIELD || it.kind == PS3_CATORDER_ROW) vals.push_back(it.value);
        else vals.push_back(std::string());
    };
    int sel = 0;
    if (mNdsAtRoot) {
        for (auto& c : mPs3Cats) { rows.push_back(c.name); vals.push_back(std::string()); }
        sel = mPs3CatIdx;
    } else if (!mPs3Stack.empty()) {
        for (auto& it : mPs3Stack.back().items) pushItem(it);
        sel = mPs3Stack.back().sel;
    } else if (mPs3CatIdx >= 0 && mPs3CatIdx < (int)mPs3Cats.size()) {
        for (auto& it : mPs3Cats[mPs3CatIdx].items) pushItem(it);
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
    // A wrap-around jump (Up on the first row / Down on the last) snaps the window straight to the
    // new end instead of gliding through the whole list.
    if (mListWrapSnap) {
        mMinimaScroll = targetScroll; mMinimaSelAnim = (float)sel; mListWrapSnap = false;
    } else {
        // exponential approach tuned to settle in ~3 frames at 60fps (NextUI's 3-step linear glide feel)
        const float k = 1.0f - powf(1.0f - 0.55f, dt * 60.0f);
        mMinimaScroll  += (targetScroll - mMinimaScroll) * k;
        mMinimaSelAnim += ((float)sel - mMinimaSelAnim) * k;
        if (fabsf(mMinimaScroll - targetScroll)  > 0.002f) mDisplayDirty = true; else mMinimaScroll = targetScroll;
        if (fabsf(mMinimaSelAnim - (float)sel)   > 0.002f) mDisplayDirty = true; else mMinimaSelAnim = (float)sel;
    }

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

    // ---- status-pill band: a value-bearing row under the top-right status pill must inset its
    // right-aligned value so it is not hidden behind the pill. Mirror of the pill geometry drawn
    // below (keep in sync). statusPillLeft = pill's left x; statusBandBot = pill's bottom y. ----
    float statusPillLeft, statusBandBot;
    {
        int wl, bl;
        { std::lock_guard<std::mutex> lk(mNetStateMutex); wl = mWifiLevel; bl = mBtLevel; }
        char cb[24] = {}; time_t tt = time(nullptr); struct tm lt; localtime_r(&tt, &lt);
        clockRefreshMaybe(); formatClockHM(cb, sizeof(cb), lt);   // honor the 12/24-hour setting
        const float ph = rowH, gp = 6.0f * sc, iconH = MIN_FONT_S * sc * 1.15f;
        const float wifiW = 22.0f * (iconH / 18.0f), btW = 14.0f * (iconH / 20.0f);
        const float battW = iconH * 1.55f + 2.0f * sc;
        float contentW = measureText(cb, fsHint);
        if (wl != kWifiLevel_Off && wl != kWifiLevel_Unknown) contentW += wifiW + gp;
        if (bl != kBtLevel_Off && bl != kBtLevel_Unknown)     contentW += btW + gp;
        if (mBatteryPercent >= 0)                              contentW += battW + gp;
        statusPillLeft = rx + rw - pad - (contentW + btnPad * 2.0f);
        statusBandBot  = ry + pad + ph;
    }

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
        // A value-bearing row draws its value right-aligned (dim white); the label is clipped to
        // the space before it so the two never overlap. rowRight tracks slideX so the value slides
        // with the row during a level-change transition, like the label does.
        float rowRight = rx + rw - pad - btnPad + slideX;
        if (rowY < statusBandBot && rowY + rowH > ry + pad)
            rowRight = fminf(rowRight, statusPillLeft - 8.0f * sc);   // clear the status pill
        float labelMaxW = textMaxW;
        if (!vals[i].empty()) {
            float vw = measureText(vals[i].c_str(), fsRow);
            drawText(vals[i].c_str(), rowRight - vw, ty, fsRow, 1.0f, 1.0f, 1.0f, 0.70f);
            labelMaxW = (rowRight - vw - 12.0f * sc) - (lx + btnPad);
        }
        float fs = fsRow, tw = measureText(rows[i].c_str(), fs);
        if (tw > labelMaxW && labelMaxW > 0.0f) { fs *= labelMaxW / tw; }
        drawText(rows[i].c_str(), lx + btnPad, ty, fs, 1.0f, 1.0f, 1.0f, 1.0f);   // COLOR_LIST_TEXT white
    }
    // The capsule pill, hugging the selected label, glided to the eased position. A label too long to
    // fit MARQUEE-scrolls (NextUI: after a short pause, 2px/frame with a 30px gap, looping) inside a
    // full-width pill, instead of shrinking to fit.
    if (n > 0) {
        const int si = sel < n ? sel : 0;
        const std::string& lbl = rows[si];
        const std::string& val = vals[si];
        const float fs = fsRow, tw = measureText(lbl.c_str(), fs);
        const float pillY = listTop + (mMinimaSelAnim - mMinimaScroll) * rowH + rowH * 0.07f;
        const float pillH = rowH * 0.86f;
        const float maxPillW = (rx + rw - pad) - listLeft;
        const float maxTextW = maxPillW - btnPad * 2.0f;
        const float ty = pillY + (pillH - MIN_FONT * sc) * 0.5f;
        if (sel != mMinimaMarqueeSel) { mMinimaMarqueeSel = sel; mMinimaMarquee = 0.0f; mMinimaMarqueeStart = (int64_t)uptimeMillis(); }
        if (!val.empty()) {
            // value-bearing selected row: full-width capsule, label left + value right (both black)
            drawRoundedRect(lx, pillY, maxPillW, pillH, pillH * 0.5f, 1.0f, 1.0f, 1.0f, 1.0f);
            const float vw = measureText(val.c_str(), fs);
            float valRight = lx + maxPillW - btnPad;
            if (pillY < statusBandBot && pillY + pillH > ry + pad)
                valRight = fminf(valRight, statusPillLeft - 8.0f * sc);   // clear the status pill
            const float vx = valRight - vw;
            drawText(val.c_str(), vx, ty, fs, 0.0f, 0.0f, 0.0f, 1.0f);
            const float lblMax = (vx - 12.0f * sc) - (lx + btnPad);
            float lfs = fs; if (tw > lblMax && lblMax > 0.0f) lfs *= lblMax / tw;
            drawText(lbl.c_str(), lx + btnPad, ty, lfs, 0.0f, 0.0f, 0.0f, 1.0f);
        } else if (tw <= maxTextW || maxTextW <= 0.0f) {
            const float pillW = fminf(tw + btnPad * 2.0f, maxPillW);
            drawRoundedRect(lx, pillY, pillW, pillH, pillH * 0.5f, 1.0f, 1.0f, 1.0f, 1.0f);   // white capsule
            drawText(lbl.c_str(), lx + btnPad, ty, fs, 0.0f, 0.0f, 0.0f, 1.0f);   // COLOR_LIST_TEXT_SELECTED black
        } else {
            drawRoundedRect(lx, pillY, maxPillW, pillH, pillH * 0.5f, 1.0f, 1.0f, 1.0f, 1.0f);   // full-width pill
            const float gap = 30.0f * sc, loopW = tw + gap;
            if ((int64_t)uptimeMillis() - mMinimaMarqueeStart > 700) {            // ~0.7s read pause, then scroll
                mMinimaMarquee += 2.0f * sc * fmaxf(0.0f, fminf(3.0f, mFrameDt * 60.0f));   // 2px per 1/60s
                if (mMinimaMarquee >= loopW) mMinimaMarquee -= loopW;
            }
            mDisplayDirty = true;
            const float clipX = lx + btnPad, tx0 = clipX - mMinimaMarquee;
            scissorLogicalRect(clipX, pillY, maxTextW, pillH);                    // clip to the pill's text area
            drawText(lbl.c_str(), tx0,         ty, fs, 0.0f, 0.0f, 0.0f, 1.0f);
            drawText(lbl.c_str(), tx0 + loopW, ty, fs, 0.0f, 0.0f, 0.0f, 1.0f);   // wrap copy for a seamless loop
            glDisable(GL_SCISSOR_TEST);
        }
    }

    // ---- status pill (top-right): real Wi-Fi / Bluetooth / battery icons + clock, in the accent pill ----
    {
        int wl, wb, bl;
        { std::lock_guard<std::mutex> lk(mNetStateMutex); wl = mWifiLevel; wb = mWifiBars; bl = mBtLevel; }
        char clockbuf[24] = {};
        time_t tt = time(nullptr); struct tm ltm; localtime_r(&tt, &ltm);
        clockRefreshMaybe(); formatClockHM(clockbuf, sizeof(clockbuf), ltm);   // honor the 12/24-hour setting

        const float ph = rowH, gap = 6.0f * sc;
        const float iconH = MIN_FONT_S * sc * 1.15f;
        const float wifiSf = iconH / 18.0f, wifiW = 22.0f * wifiSf;
        const float btSf   = iconH / 20.0f, btW = 14.0f * btSf;
        const float battBW = iconH * 1.55f, battNub = 2.0f * sc, battW = battBW + battNub;
        const bool showWifi = (wl != kWifiLevel_Off && wl != kWifiLevel_Unknown);
        const bool showBt   = (bl != kBtLevel_Off && bl != kBtLevel_Unknown);
        const bool showBatt = (mBatteryPercent >= 0);
        const float clockW = measureText(clockbuf, fsHint);
        float contentW = clockW;
        if (showWifi) contentW += wifiW + gap;
        if (showBt)   contentW += btW + gap;
        if (showBatt) contentW += battW + gap;

        const float pw = contentW + btnPad * 2.0f;
        const float px = rx + rw - pad - pw, py = ry + pad;
        drawRoundedRect(px, py, pw, ph, ph * 0.5f, ar, ag, ab, 1.0f);   // accent pill
        float ix = px + btnPad;
        const float iconY = py + (ph - iconH) * 0.5f;
        if (showWifi) {
            int bars = (wl == kWifiLevel_Connected) ? wb : 0;
            drawWifiIcon(ix, iconY, wifiSf, bars, atc, atc, atc, 1.0f);
            ix += wifiW + gap;
        }
        if (showBt) { drawBtIcon(ix, iconY, btSf, atc, atc, atc, 1.0f); ix += btW + gap; }
        if (showBatt) {
            float bh = iconH * 0.62f, by = py + (ph - bh) * 0.5f;
            int pct = mBatteryPercent; if (pct < 0) pct = 0; if (pct > 100) pct = 100;
            drawRoundedRect(ix, by, battBW, bh, 2.0f * sc, atc, atc, atc, 1.0f);                                  // body
            drawRoundedRect(ix + 1.5f * sc, by + 1.5f * sc, battBW - 3.0f * sc, bh - 3.0f * sc, 1.5f * sc, ar, ag, ab, 1.0f);  // hollow
            float fillW = (battBW - 4.0f * sc) * ((float)pct / 100.0f);
            drawRoundedRect(ix + 2.0f * sc, by + 2.0f * sc, fillW, bh - 4.0f * sc, 1.0f * sc, atc, atc, atc, 1.0f);            // charge
            drawQuad(ix + battBW, by + bh * 0.28f, battNub, bh * 0.44f, atc, atc, atc, 1.0f);                     // nub
            ix += battW + gap;
        }
        drawText(clockbuf, ix, py + (ph - MIN_FONT_S * sc) * 0.5f, fsHint, atc, atc, atc, 1.0f);
    }

    // ---- focused game boxart, bottom-right, above the A Open pill ----
    if (minSe && scraperBoxartEnabled() && !minSe->box.empty()) {
        float bar = 0.7f;
        GLuint bt = romBoxartTex(focusedRomPath(), &bar);
        if (bt) {
            float bh = rh * 0.32f, bw = bh * (bar > 0.01f ? bar : 0.7f);
            const float maxbw = rw * 0.30f;
            if (bw > maxbw) { bw = maxbw; bh = bw / (bar > 0.01f ? bar : 0.7f); }
            float bx = rx + rw - pad - bw, by = hintTop - btnMg - bh;
            drawRoundedRect(bx - 4.0f * sc, by - 4.0f * sc, bw + 8.0f * sc, bh + 8.0f * sc, 6.0f * sc, 1.0f, 1.0f, 1.0f, 0.12f);
            drawIconTex(bt, bx, by, bw, bh, 1.0f, 1.0f, 1.0f, 1.0f);
        }
    } else if (!inGameScrim) {
        // ---- focused APP logo, bottom-right (like boxart) - the Applications list ----
        const Ps3Item* focItem = nullptr;
        if (!mNdsAtRoot) {
            if (!mPs3Stack.empty()) {
                const auto& its = mPs3Stack.back().items; int s = mPs3Stack.back().sel;
                if (s >= 0 && s < (int)its.size()) focItem = &its[s];
            } else if (mPs3CatIdx >= 0 && mPs3CatIdx < (int)mPs3Cats.size()) {
                const auto& its = mPs3Cats[mPs3CatIdx].items;
                if (mPs3ItemIdx >= 0 && mPs3ItemIdx < (int)its.size()) focItem = &its[mPs3ItemIdx];
            }
        }
        if (focItem && focItem->kind == PS3_APP && focItem->iconTex) {
            float isz = rh * 0.28f; const float maxsz = rw * 0.28f; if (isz > maxsz) isz = maxsz;
            float bx = rx + rw - pad - isz, by = hintTop - btnMg - isz;   // square app icon
            drawRoundedRect(bx - 4.0f * sc, by - 4.0f * sc, isz + 8.0f * sc, isz + 8.0f * sc, 8.0f * sc, 1.0f, 1.0f, 1.0f, 0.12f);
            drawIconTex(focItem->iconTex, bx, by, isz, isz, 1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    // ---- bottom legend: a button glyph + label, matching NextUI. B Back (left), A Open (right), and
    // a centred Y Info between them for a focused game that has scraped info. ----
    {
        const float ph = rowH, py = hintTop, gap = 5.0f * sc;
        const float glyphR = MIN_FONT_S * sc * 0.70f, lw = fmaxf(1.5f, 2.0f * sc);
        auto drawLegend = [&](int role, const char* label, int align) {   // align: 0 left, 1 right, 2 centre
            const char* lbl = trDyn(label);
            float lblW = measureText(lbl, fsHint);
            float pw = glyphR * 2.0f + gap + lblW + btnPad * 2.0f;
            float px = (align == 1) ? (rx + rw - pad - pw)
                     : (align == 2) ? (rx + rw * 0.5f - pw * 0.5f)
                                    : (rx + pad);
            drawRoundedRect(px, py, pw, ph, ph * 0.5f, ar, ag, ab, 1.0f);
            float gcx = px + btnPad + glyphR, gcy = py + ph * 0.5f;
            drawFaceGlyph(role, gcx, gcy, glyphR, lw, 1.0f);   // A=0, B=1, Y=2 (white ring + letter)
            drawText(lbl, gcx + glyphR + gap, py + (ph - MIN_FONT_S * sc) * 0.5f, fsHint, atc, atc, atc, 1.0f);
        };
        drawLegend(0, "Open", 1);    // A Open (right)
        drawLegend(1, "Back", 0);    // B Back (left)
        if (minSe) drawLegend(2, "Info", 2);   // Y Info (centre), only when the focus has scraped info
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
    float bgR, bgG, bgB;
    if (wallpaperActive(1)) { drawWallpaperFill(1); drew = true; }   // applies the adjustable Wallpaper Dimming scrim
    if (drew)                        { /* dimming handled inside drawWallpaperFill */ }
    else if (inGameScrim)            { /* leave the app-dim scrim clear; the category chrome draws over it */ }
    else if (minimaSolidBg(&bgR, &bgG, &bgB)) drawQuad(rx, ry, rw, rh, bgR, bgG, bgB, 1.0f);   // solid backdrop colour
    else                             drawQuad(rx, ry, rw, rh, 0.0f, 0.0f, 0.0f, 1.0f);    // pure-black backdrop (no app behind)

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
    // game-system rows: echo the On/Off (or field value) in the accent colour under the label,
    // so the enable state is visible on the bottom screen too.
    if (selItem && (selItem->kind == PS3_GS_SYSTEM_ROW || selItem->kind == PS3_GS_FIELD
                    || selItem->kind == PS3_CATORDER_ROW)
        && !selItem->value.empty()) {
        float fsV = (18.0f * sc) / (float)FONT_CHAR_H;
        float vw = measureText(selItem->value.c_str(), fsV);
        drawText(selItem->value.c_str(), cx - vw * 0.5f, ry + rh * 0.90f, fsV, ar, ag, ab, 1.0f);
    }
    mTextOutlineMode = minPrevOutline;
}

// Option menu / list+slider chooser in Minima's language: a dark right-anchored panel that slides
// in, with an accent left edge + title, the options as white rows (selected in a white capsule), or a
// numeric slider. Reuses the shared option/dialog animation members so opening/closing clears cleanly.
void NanoMenu::renderMinimaSidePanel(float rx, float ry, float rw, float rh) {
    setUiBlend();
    const int minPrevOutline = mTextOutlineMode; mTextOutlineMode = 2;
    float dt = mFrameDt; if (dt < 0.0f) dt = 0.0f; if (dt > 0.1f) dt = 0.1f;
    const bool optSrc = (mPs3OptActive || mPs3OptClosing);
    float ap;
    if (optSrc) {
        if (mPs3OptActive) { mPs3OptClosing = false; mPs3OptAnim += (1.0f - mPs3OptAnim) * (1.0f - expf(-14.0f * dt)); if (mPs3OptAnim > 0.999f) mPs3OptAnim = 1.0f; ap = mPs3OptAnim; }
        else { mPs3OptCloseAnim -= mPs3OptCloseAnim * (1.0f - expf(-14.0f * dt)); if (mPs3OptCloseAnim < 0.02f) { mPs3OptCloseAnim = 0.0f; mPs3OptClosing = false; mTextOutlineMode = minPrevOutline; return; } ap = mPs3OptCloseAnim; }
    } else {
        if (mPs3DlgActive) { mPs3DlgClosing = false; mPs3DlgAnim += (1.0f - mPs3DlgAnim) * (1.0f - expf(-14.0f * dt)); if (mPs3DlgAnim > 0.999f) mPs3DlgAnim = 1.0f; ap = mPs3DlgAnim; }
        else { mPs3DlgCloseAnim -= mPs3DlgCloseAnim * (1.0f - expf(-14.0f * dt)); if (mPs3DlgCloseAnim < 0.02f) { mPs3DlgCloseAnim = 0.0f; mPs3DlgClosing = false; mTextOutlineMode = minPrevOutline; return; } ap = mPs3DlgCloseAnim; }
    }
    if (ap < 0.999f) mDisplayDirty = true;

    std::vector<std::string> rows; std::string title; int sel = 0; bool slider = false;
    if (optSrc) {
        const bool subOpen = mPs3OptSubOpen && mPs3OptSel >= 0 && mPs3OptSel < (int)mPs3OptSubRows.size() && !mPs3OptSubRows[mPs3OptSel].empty();
        if (subOpen) {
            for (const auto& sr : mPs3OptSubRows[mPs3OptSel]) rows.push_back(trDyn(sr.label.c_str()));
            sel = mPs3OptSubSel;
            title = (mPs3OptSel < (int)mPs3OptLabels.size()) ? trDyn(mPs3OptLabels[mPs3OptSel].c_str()) : "Options";
        } else {
            int nn = (int)mPs3OptLabels.size();
            for (int i = 0; i < nn; i++) { if (i < (int)mPs3OptSep.size() && mPs3OptSep[i]) continue; if (i == mPs3OptSel) sel = (int)rows.size(); rows.push_back(trDyn(mPs3OptLabels[i].c_str())); }
            title = mPs3OptCtxLabel.empty() ? "Options" : trDyn(mPs3OptCtxLabel.c_str());
        }
    } else {
        title = mPs3DlgTitle.empty() ? "Options" : trDyn(mPs3DlgTitle.c_str());
        slider = mPs3DlgSlider && mPs3DlgOptions.empty();
        for (const auto& o : mPs3DlgOptions) rows.push_back(trDyn(o.c_str()));
        sel = mPs3DlgSel;
    }
    int n = (int)rows.size(); if (sel < 0) sel = 0; if (n > 0 && sel >= n) sel = n - 1;

    drawQuad(rx, ry, rw, rh, 0.0f, 0.0f, 0.0f, 0.55f * ap);          // scrim over the home
    float ar, ag, ab; minimaAccent(ar, ag, ab);
    const float sc = rh / MIN_REF_H, pad = MIN_PAD * sc, rowH = MIN_PILL * sc, btnPad = MIN_BTNPAD * sc;
    const float inset = 8.0f * sc;
    float panelW = fminf(rw * 0.60f, rw - pad * 2.0f);
    float px = rx + rw - panelW + (1.0f - ap) * panelW;             // slide in from the right
    drawQuad(px, ry, panelW, rh, 0.05f, 0.05f, 0.06f, 0.98f * ap);  // dark panel
    drawQuad(px, ry, fmaxf(2.0f, 3.0f * sc), rh, ar, ag, ab, ap);   // accent left edge
    drawText(title.c_str(), px + pad + inset, ry + pad, (18.0f * sc) / (float)FONT_CHAR_H, ar, ag, ab, ap);
    float listLeft = px + pad + inset;
    float contentTop = ry + pad + rowH * 0.9f + 6.0f * sc;

    if (slider) {
        float mn = mPs3DlgSldMin, mx = mPs3DlgSldMax, v = mPs3DlgSldVal;
        float t = (mx > mn) ? (v - mn) / (mx - mn) : 0.0f; if (t < 0.0f) t = 0.0f; if (t > 1.0f) t = 1.0f;
        char buf[32];
        if (mPs3DlgSldScale <= 0) snprintf(buf, sizeof(buf), "%d", (int)lroundf(v));
        else snprintf(buf, sizeof(buf), "%.*f", mPs3DlgSldScale, v);
        float fsV = (34.0f * sc) / (float)FONT_CHAR_H, tw = measureText(buf, fsV);
        drawText(buf, px + panelW * 0.5f - tw * 0.5f, ry + rh * 0.38f, fsV, 1.0f, 1.0f, 1.0f, ap);
        float bx = listLeft, bw = panelW - 2.0f * (pad + inset), by = ry + rh * 0.56f, bh = 8.0f * sc;
        drawRoundedRect(bx, by, bw, bh, bh * 0.5f, 0.28f, 0.28f, 0.30f, ap);
        drawRoundedRect(bx, by, bw * t, bh, bh * 0.5f, ar, ag, ab, ap);
        drawRoundedRect(bx + bw * t - 5.0f * sc, by - 5.0f * sc, 10.0f * sc, bh + 10.0f * sc, 5.0f * sc, 1.0f, 1.0f, 1.0f, ap);
    } else if (n > 0) {
        float listBot = ry + rh - pad;
        int visRows = (int)fmaxf(1.0f, floorf((listBot - contentTop) / rowH));
        int top = sel - visRows / 2; if (top > n - visRows) top = n - visRows; if (top < 0) top = 0;
        float textMaxW = px + panelW - pad - listLeft - btnPad;
        for (int i = top; i < n && i < top + visRows; i++) {
            float rowY = contentTop + (float)(i - top) * rowH;
            float ty = rowY + (rowH - MIN_FONT * sc) * 0.5f;
            float fs = (MIN_FONT * sc) / (float)FONT_CHAR_H, tw = measureText(rows[i].c_str(), fs);
            if (tw > textMaxW && textMaxW > 0.0f) { fs *= textMaxW / tw; tw = measureText(rows[i].c_str(), fs); }
            if (i == sel) {
                float pillH = rowH * 0.86f, pillW = fminf(tw + btnPad * 2.0f, px + panelW - pad - listLeft);
                drawRoundedRect(listLeft, rowY + rowH * 0.07f, pillW, pillH, pillH * 0.5f, 1.0f, 1.0f, 1.0f, ap);
                drawText(rows[i].c_str(), listLeft + btnPad, ty, fs, 0.0f, 0.0f, 0.0f, ap);
            } else {
                drawText(rows[i].c_str(), listLeft + btnPad, ty, fs, 1.0f, 1.0f, 1.0f, ap);
            }
        }
    }
    mTextOutlineMode = minPrevOutline;
}

// Confirm / message dialog in Minima's language: a centred dark rounded panel with an accent top
// rule, the title in the accent, a word-wrapped body, and the options (Yes/No/OK) as pills along the
// bottom (the selected one filled with the accent).
void NanoMenu::renderMinimaDialog(float rx, float ry, float rw, float rh) {
    setUiBlend();
    const int minPrevOutline = mTextOutlineMode; mTextOutlineMode = 2;
    float dt = mFrameDt; if (dt < 0.0f) dt = 0.0f; if (dt > 0.1f) dt = 0.1f;
    mPs3DlgClosing = false;
    mPs3DlgAnim += (1.0f - mPs3DlgAnim) * (1.0f - expf(-15.0f * dt));
    if (mPs3DlgAnim > 0.999f) mPs3DlgAnim = 1.0f; else mDisplayDirty = true;
    float ap = mPs3DlgAnim, ease = ap * ap * (3.0f - 2.0f * ap);
    float ar, ag, ab; minimaAccent(ar, ag, ab);
    const float sc = rh / MIN_REF_H, pad = MIN_PAD * sc, rowH = MIN_PILL * sc, btnPad = MIN_BTNPAD * sc;

    drawQuad(rx, ry, rw, rh, 0.0f, 0.0f, 0.0f, 0.55f * ap);   // scrim
    float pw = fminf(rw * 0.74f, rw - pad * 2.0f), ph = fminf(rh * 0.64f, rh - pad * 2.0f);
    float pxL = rx + (rw - pw) * 0.5f;
    float pyTgt = ry + (rh - ph) * 0.5f, pyOff = pyTgt + rh * 0.12f;
    float pyTop = pyOff + (pyTgt - pyOff) * ease;
    drawRoundedRect(pxL, pyTop + 4.0f * sc, pw, ph, 12.0f * sc, 0.0f, 0.0f, 0.0f, 0.5f * ap);   // shadow
    drawRoundedRect(pxL, pyTop, pw, ph, 12.0f * sc, 0.07f, 0.07f, 0.08f, ap);                   // body
    drawRoundedRect(pxL, pyTop, pw, fmaxf(2.0f, 4.0f * sc), 2.0f * sc, ar, ag, ab, ap);          // accent top rule
    float cxC = rx + rw * 0.5f;
    float yy = pyTop + pad + 6.0f * sc;
    if (!mPs3DlgTitle.empty()) {
        const char* t = trDyn(mPs3DlgTitle.c_str());
        float fs = (20.0f * sc) / (float)FONT_CHAR_H, tw = measureText(t, fs);
        drawText(t, cxC - tw * 0.5f, yy, fs, ar, ag, ab, ap);
        yy += rowH * 1.05f;
    }
    // word-wrapped body (centred), capped so it never runs into the button row
    if (!mPs3DlgBody.empty()) {
        std::string body = trDyn(mPs3DlgBody.c_str());
        float fs = (15.0f * sc) / (float)FONT_CHAR_H;
        float maxW = pw - pad * 2.0f, lineH = 18.0f * sc, bodyBot = pyTop + ph - rowH - pad * 1.5f;
        std::string line;
        size_t i = 0;
        while (i < body.size() && yy < bodyBot) {
            size_t sp = body.find(' ', i);
            std::string word = body.substr(i, (sp == std::string::npos ? body.size() : sp) - i);
            std::string cand = line.empty() ? word : line + " " + word;
            if (measureText(cand.c_str(), fs) > maxW && !line.empty()) {
                float tw = measureText(line.c_str(), fs);
                drawText(line.c_str(), cxC - tw * 0.5f, yy, fs, 0.90f, 0.90f, 0.92f, ap);
                yy += lineH; line = word;
            } else line = cand;
            if (sp == std::string::npos) break;
            i = sp + 1;
        }
        if (!line.empty() && yy < bodyBot) { float tw = measureText(line.c_str(), fs); drawText(line.c_str(), cxC - tw * 0.5f, yy, fs, 0.90f, 0.90f, 0.92f, ap); }
    }
    // option pills (Yes / No / OK), selected filled with the accent
    int n = (int)mPs3DlgOptions.size(), sel = mPs3DlgSel; if (sel < 0) sel = 0; if (n > 0 && sel >= n) sel = n - 1;
    if (n > 0) {
        float fs = (MIN_FONT_S * sc) / (float)FONT_CHAR_H, pillH = rowH, py = pyTop + ph - pad - pillH, gap = pad;
        std::vector<float> ws; float total = 0.0f;
        for (int i = 0; i < n; i++) { float w = measureText(trDyn(mPs3DlgOptions[i].c_str()), fs) + btnPad * 2.0f; ws.push_back(w); total += w; }
        total += gap * (float)(n - 1);
        float xx = cxC - total * 0.5f;
        for (int i = 0; i < n; i++) {
            const char* o = trDyn(mPs3DlgOptions[i].c_str());
            bool s = (i == sel);
            drawRoundedRect(xx, py, ws[i], pillH, pillH * 0.5f, s ? ar : 0.16f, s ? ag : 0.16f, s ? ab : 0.18f, ap);
            float tw = measureText(o, fs), ty = py + (pillH - MIN_FONT_S * sc) * 0.5f;
            drawText(o, xx + (ws[i] - tw) * 0.5f, ty, fs, 1.0f, 1.0f, 1.0f, ap);
            xx += ws[i] + gap;
        }
    }
    mTextOutlineMode = minPrevOutline;
}

// Game / app INFORMATION page in Minima's language. The shared info dialog carries the scraped
// cover (mPs3DlgBoxTex), fan art (mPs3DlgFanTex), metadata (mPs3RomInfo*) and a paged synopsis
// (mPs3RomInfoSyn, else the plain-facts mPs3DlgBody). Minima's generic dialog only draws a title +
// body, so a rich info page came out almost empty - this renders the full page flat, mirroring the
// DSi info page's content: a dimmed fan-art (screenshot) backdrop, the cover + metadata columns and
// the paged description. Paged with L/R exactly like the DSi page (mNdsInfoPage / mNdsInfoPageCount).
void NanoMenu::renderMinimaInfoPage(float rx, float ry, float rw, float rh) {
    setUiBlend();
    const int minPrevOutline = mTextOutlineMode; mTextOutlineMode = 2;
    float dt = mFrameDt; if (dt < 0.0f) dt = 0.0f; if (dt > 0.1f) dt = 0.1f;
    mPs3DlgClosing = false;
    mPs3DlgAnim += (1.0f - mPs3DlgAnim) * (1.0f - expf(-15.0f * dt));
    if (mPs3DlgAnim > 0.999f) mPs3DlgAnim = 1.0f; else mDisplayDirty = true;
    const float ap = mPs3DlgAnim;

    float ar, ag, ab; minimaAccent(ar, ag, ab);
    const float sc = rh / MIN_REF_H, pad = MIN_PAD * sc;
    const float FCH = (float)FONT_CHAR_H;

    // Backdrop: opaque black, with the fan art (a "screenshot"/background) cover-fit dim behind it.
    drawQuad(rx, ry, rw, rh, 0.0f, 0.0f, 0.0f, ap);
    if (mPs3DlgFanTex && mPs3DlgFanW > 0 && mPs3DlgFanH > 0) {
        float far = (float)mPs3DlgFanW / (float)mPs3DlgFanH, panelAr = rw / rh;
        float fw = rw, fh = rh, fx = rx, fy = ry;
        if (far > panelAr) { fw = rh * far; fx = rx - (fw - rw) * 0.5f; }
        else               { fh = rw / far; fy = ry - (fh - rh) * 0.5f; }
        drawIconTex(mPs3DlgFanTex, fx, fy, fw, fh, 1.0f, 1.0f, 1.0f, 0.30f * ap);
        drawQuad(rx, ry, rw, rh, 0.0f, 0.0f, 0.0f, 0.55f * ap);
    }

    // Header: "< Title" in the accent (matches the wallpaper picker chrome).
    const char* chev = "\xE2\x80\xB9";   // U+2039
    float chFs = (24.0f * sc) / FCH, chW = measureText(chev, chFs);
    float hx = rx + pad, hy = ry + rh * 0.05f;
    drawText(chev, hx, hy, chFs, ar, ag, ab, ap);
    std::string title = mPs3DlgTitle.empty() ? std::string("Information") : mPs3DlgTitle;
    float titleX = hx + chW + 12.0f * sc, titleFs = (20.0f * sc) / FCH;
    float titleMaxW = (rx + rw - pad) - titleX, tw = measureText(title.c_str(), titleFs);
    if (tw > titleMaxW && titleMaxW > 0.0f) titleFs *= titleMaxW / tw;
    drawText(title.c_str(), titleX, hy, titleFs, ar, ag, ab, ap);

    float descTop;
    const float rowH = 15.0f * sc;
    if (mPs3DlgRomInfo) {
        // Cover (left column).
        float colTop = ry + rh * 0.17f;
        float coverW = rw * 0.26f, coverH = coverW * 1.4f;
        if (coverH > rh * 0.40f) { coverH = rh * 0.40f; coverW = coverH / 1.4f; }
        float coverX = rx + pad, coverY = colTop;
        float metaX = coverX + coverW + rw * 0.05f;
        if (mPs3DlgBoxTex && mPs3DlgBoxW > 0 && mPs3DlgBoxH > 0) {
            float car = (float)mPs3DlgBoxW / (float)mPs3DlgBoxH;
            float cw = coverW, ch = cw / car; if (ch > coverH) { ch = coverH; cw = ch * car; }
            float cvx = coverX + (coverW - cw) * 0.5f, cvy = coverY + (coverH - ch) * 0.5f;
            drawRoundedRect(cvx - 2.0f*sc, cvy - 2.0f*sc, cw + 4.0f*sc, ch + 4.0f*sc, 4.0f*sc, 1.0f, 1.0f, 1.0f, 0.9f*ap);
            drawIconTex(mPs3DlgBoxTex, cvx, cvy, cw, ch, 1.0f, 1.0f, 1.0f, ap);
        } else {
            drawRoundedRect(coverX, coverY, coverW, coverH, 4.0f*sc, 0.12f, 0.12f, 0.14f, 0.8f*ap);   // placeholder while art loads
        }
        // Metadata rows (right of the cover).
        float mLabelFs = (11.0f * sc) / FCH, mValFs = (12.0f * sc) / FCH;
        float vy = colTop + 2.0f * sc;
        float valX = metaX + rw * 0.17f, valMaxW = (rx + rw - pad) - valX;
        auto mrow = [&](const char* label, const std::string& val) {
            if (val.empty()) return;
            drawText(label, metaX, vy, mLabelFs, ar, ag, ab, 0.9f * ap);
            float f = mValFs, vw = measureText(val.c_str(), f);
            if (vw > valMaxW && valMaxW > 0.0f) f *= valMaxW / vw;
            drawText(val.c_str(), valX, vy, f, 1.0f, 1.0f, 1.0f, ap);
            vy += rowH;
        };
        mrow("Genre",     mPs3RomInfoGenre);
        mrow("Players",   mPs3RomInfoPlayers);
        mrow("Rating",    mPs3RomInfoRating);
        mrow("Released",  mPs3RomInfoDate);
        mrow("Developer", mPs3RomInfoDev);
        mrow("Publisher", mPs3RomInfoPub);
        mrow(mPs3RomInfoCoreIsApp ? "App" : "Core", mPs3RomInfoCore);
        mrow("System",    mPs3RomInfoSystem);
        descTop = fmaxf(colTop + coverH, vy) + rh * 0.03f;
    } else {
        descTop = ry + rh * 0.18f;   // plain-facts fallback wraps from near the top
    }

    // ---- Description (paged, L/R) ----
    std::string bodyText = mPs3DlgRomInfo ? mPs3RomInfoSyn : mPs3DlgBody;
    float bodyFs = (13.0f * sc) / FCH, lineH = 17.0f * sc;
    float bodyLeft = rx + pad, wrapW = rw - pad * 2.0f;
    float capY = descTop, bodyTop = descTop + (mPs3DlgRomInfo ? rowH : 0.0f);
    float bodyBot = ry + rh - rh * 0.10f;
    std::vector<std::string> lines;
    if (!bodyText.empty()) {
        std::string line, word;
        auto commit = [&]() {
            if (word.empty()) return;
            std::string trial = line.empty() ? word : line + " " + word;
            if (!line.empty() && measureText(trial.c_str(), bodyFs) > wrapW) { lines.push_back(line); line = word; }
            else line = trial;
            word.clear();
        };
        for (const char* q = bodyText.c_str(); ; ++q) {
            if (*q == ' ' || *q == '\n' || *q == '\0') { commit(); if (*q == '\n') lines.push_back(""); if (*q == '\0') break; }
            else word.push_back(*q);
        }
        if (!line.empty()) lines.push_back(line);
    }
    int linesPerPage = (int)((bodyBot - bodyTop) / lineH); if (linesPerPage < 1) linesPerPage = 1;
    int total = (int)lines.size();
    int pages = (total + linesPerPage - 1) / linesPerPage; if (pages < 1) pages = 1;
    mNdsInfoPageCount = pages;
    if (mNdsInfoPage < 0) mNdsInfoPage = 0;
    if (mNdsInfoPage > pages - 1) mNdsInfoPage = pages - 1;
    if (mPs3DlgRomInfo && total > 0)
        drawText("Description", bodyLeft, capY, (11.0f * sc) / FCH, ar, ag, ab, 0.9f * ap);
    float ly = bodyTop;
    int firstL = mNdsInfoPage * linesPerPage, lastL = firstL + linesPerPage; if (lastL > total) lastL = total;
    for (int i = firstL; i < lastL; i++) {
        if (!lines[i].empty()) drawText(lines[i].c_str(), bodyLeft, ly, bodyFs, 0.90f, 0.90f, 0.92f, ap);
        ly += lineH;
    }
    // L/R pager (only with >1 page): centred count + accent corner pills.
    if (pages > 1) {
        float by = ry + rh - rh * 0.06f, pf = (11.0f * sc) / FCH;
        char pg[24]; snprintf(pg, sizeof(pg), "%d / %d", mNdsInfoPage + 1, pages);
        float pgw = measureText(pg, pf);
        drawText(pg, rx + rw * 0.5f - pgw * 0.5f, by, pf, ar, ag, ab, ap);
        auto pill = [&](float pcx, const char* g, bool on) {
            float gw = measureText(g, pf), pw = gw + MIN_BTNPAD * sc * 2.0f;
            drawRoundedRect(pcx - pw * 0.5f, by - 2.0f * sc, pw, rowH, rowH * 0.5f, ar, ag, ab, (on ? 1.0f : 0.35f) * ap);
            drawText(g, pcx - gw * 0.5f, by, pf, 1.0f, 1.0f, 1.0f, (on ? 1.0f : 0.5f) * ap);
        };
        pill(rx + pad + rw * 0.05f,        "L", mNdsInfoPage > 0);
        pill(rx + rw - pad - rw * 0.05f,   "R", mNdsInfoPage < pages - 1);
    }
    mTextOutlineMode = minPrevOutline;
}

} // namespace android
