/*
 * Copyright (C) 2026 GammaOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// GammaOS Nano PS3 XMB menu. Faithful port of the PlayStation 3 XrossMediaBar
// layout (index.html drawXMB / drawItemList): a horizontal category bar with a
// vertical item list dropping out of the active category, the firmware geometry,
// the firmware animation language (a category slide rail with a fade-crossfade,
// easeOutBack item navigation), the clock, and the captured wave behind it. The
// categories are populated from nano's real content so the launcher keeps
// working. Gated by persist.gammaos.nano.ps3xmb.

// LOG_TAG must be defined before NanoMenu.h (it transitively sets a default).
#define LOG_TAG "GammaOSNano"

#include "NanoMenu.h"
#include "NanoMenuPS3.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <vector>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#include <GLES2/gl2.h>
#include <png.h>

#include <utils/Log.h>

namespace android {

// ---------------------------------------------------------------------------
// libpng loader -> mono-white (silvery) RGBA texture for the PS3 category
// icons. Tries the dev push dir then the shipped asset.
// ---------------------------------------------------------------------------
static GLuint loadPs3IconTex(const char* file) {
    char path[256];
    FILE* fp = nullptr;
    snprintf(path, sizeof(path), "/data/system/nano_xmb/icons/%s", file);
    fp = fopen(path, "rb");
    if (!fp) {
        snprintf(path, sizeof(path), "/system/etc/nano_xmb/icons/%s", file);
        fp = fopen(path, "rb");
    }
    if (!fp) return 0;
    png_structp png = png_create_read_struct(PNG_LIBPNG_VER_STRING, nullptr, nullptr, nullptr);
    if (!png) { fclose(fp); return 0; }
    png_infop info = png_create_info_struct(png);
    if (!info) { png_destroy_read_struct(&png, nullptr, nullptr); fclose(fp); return 0; }
    if (setjmp(png_jmpbuf(png))) { png_destroy_read_struct(&png, &info, nullptr); fclose(fp); return 0; }
    png_init_io(png, fp);
    png_read_info(png, info);
    int w = png_get_image_width(png, info);
    int h = png_get_image_height(png, info);
    int color = png_get_color_type(png, info);
    int depth = png_get_bit_depth(png, info);
    if (depth == 16) png_set_strip_16(png);
    if (color == PNG_COLOR_TYPE_PALETTE) png_set_palette_to_rgb(png);
    if (color == PNG_COLOR_TYPE_GRAY && depth < 8) png_set_expand_gray_1_2_4_to_8(png);
    if (png_get_valid(png, info, PNG_INFO_tRNS)) png_set_tRNS_to_alpha(png);
    if (color == PNG_COLOR_TYPE_GRAY || color == PNG_COLOR_TYPE_GRAY_ALPHA) png_set_gray_to_rgb(png);
    if (color == PNG_COLOR_TYPE_RGB || color == PNG_COLOR_TYPE_GRAY || color == PNG_COLOR_TYPE_PALETTE)
        png_set_filler(png, 0xFF, PNG_FILLER_AFTER);
    png_read_update_info(png, info);
    size_t rowbytes = png_get_rowbytes(png, info);
    std::vector<unsigned char> pixels(rowbytes * (size_t)h);
    std::vector<png_bytep> rows((size_t)h);
    for (int y = 0; y < h; y++) rows[(size_t)y] = pixels.data() + (size_t)y * rowbytes;
    png_read_image(png, rows.data());
    png_destroy_read_struct(&png, &info, nullptr);
    fclose(fp);
    for (size_t i = 0; i + 3 < pixels.size(); i += 4) {   // force white, keep alpha
        pixels[i] = 255; pixels[i + 1] = 255; pixels[i + 2] = 255;
    }
    GLuint tex = 0;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels.data());
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    return tex;
}

void NanoMenu::drawIconTex(GLuint tex, float x, float y, float w, float h,
                           float r, float g, float b, float a) {
    if (tex == 0) return;
    float x0 = (x / mWidth) * 2.0f - 1.0f;
    float y0 = 1.0f - ((y + h) / mHeight) * 2.0f;
    float x1 = ((x + w) / mWidth) * 2.0f - 1.0f;
    float y1 = 1.0f - (y / mHeight) * 2.0f;
    GLfloat verts[] = { x0,y0, x1,y0, x1,y1, x1,y1, x0,y1, x0,y0 };
    GLfloat uvs[]   = { 0,1, 1,1, 1,0, 1,0, 0,0, 0,1 };
    GLfloat colors[6 * 4];
    for (int i = 0; i < 6; i++) { colors[i*4]=r; colors[i*4+1]=g; colors[i*4+2]=b; colors[i*4+3]=a; }
    glUseProgram(mTextProgram);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, tex);
    glUniform1i(mTextLocTexture, 0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glVertexAttribPointer(mTextLocPosition, 2, GL_FLOAT, GL_FALSE, 0, verts);
    glEnableVertexAttribArray(mTextLocPosition);
    glVertexAttribPointer(mTextLocTexCoord, 2, GL_FLOAT, GL_FALSE, 0, uvs);
    glEnableVertexAttribArray(mTextLocTexCoord);
    glVertexAttribPointer(mTextLocColor, 4, GL_FLOAT, GL_FALSE, 0, colors);
    glEnableVertexAttribArray(mTextLocColor);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glDisableVertexAttribArray(mTextLocPosition);
    glDisableVertexAttribArray(mTextLocTexCoord);
    glDisableVertexAttribArray(mTextLocColor);
}

// ---------------------------------------------------------------------------
// model build
// ---------------------------------------------------------------------------
void NanoMenu::initPs3Menu() {
    if (mPs3MenuBuilt) return;
    static const char* kCatIconFiles[6] = {
        "xmb_icon_001.png", "xmb_icon_002.png", "xmb_icon_003.png",
        "xmb_icon_004.png", "xmb_icon_005.png", "xmb_icon_006.png",
    };
    for (int i = 0; i < 6; i++) mPs3CatTex[i] = loadPs3IconTex(kCatIconFiles[i]);
    buildPs3Cats();
    mPs3MenuBuilt = true;
    ALOGI("ps3menu: built %zu categories", mPs3Cats.size());
}

void NanoMenu::buildRomSubmenu(int sysIdx, Ps3Level& out) {
    out.items.clear();
    out.sel = 0;
    if (sysIdx < 0 || sysIdx >= (int)mXmbSystems.size()) return;
    const XmbSystem& sys = mXmbSystems[sysIdx];
    out.title = sys.name;
    for (size_t i = 0; i < sys.displayNames.size(); i++) {
        Ps3Item it;
        it.label = sys.displayNames[i];
        it.kind = PS3_ROM;
        it.a = sysIdx; it.b = (int)i;
        it.iconTex = mIconTextures[16];
        it.iconR = sys.iconR; it.iconG = sys.iconG; it.iconB = sys.iconB;
        out.items.push_back(it);
    }
}

void NanoMenu::buildRecentSubmenu(Ps3Level& out) {
    out.items.clear();
    out.sel = 0;
    out.title = "Recently Played";
    for (size_t i = 0; i < mXmbRecent.size(); i++) {
        Ps3Item it;
        it.label = mXmbRecent[i].displayName;
        it.value = mXmbRecent[i].systemName;
        it.kind = PS3_RECENT;
        it.a = (int)i;
        it.iconTex = mIconTextures[16];
        it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    }
}

void NanoMenu::buildAppSubmenu(Ps3Level& out) {
    out.items.clear();
    out.sel = 0;
    out.title = "Applications";
    if (!mAppsLoaded) loadInstalledApps();
    for (size_t i = 0; i < mAppEntries.size(); i++) {
        Ps3Item it;
        it.label = mAppEntries[i].label;
        it.kind = PS3_APP;
        it.a = (int)i;
        it.payloadStr = mAppEntries[i].packageName;
        it.iconTex = mIconTextures[17];
        it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    }
}

void NanoMenu::buildPs3Cats() {
    mPs3Cats.clear();

    // --- Settings (icon 001) ---
    {
        Ps3Cat c; c.name = "Settings"; c.iconTex = mPs3CatTex[0];
        if (mSettingsItems.empty()) initSettingsItems();
        for (size_t i = 0; i < mSettingsItems.size(); i++) {
            Ps3Item it;
            it.label = mSettingsItems[i].label;
            it.kind = PS3_SETTING;
            it.a = mSettingsItems[i].action;
            it.iconTex = mIconTextures[17];
            it.iconR = it.iconG = it.iconB = 1.0f;
            c.items.push_back(it);
        }
        mPs3Cats.push_back(c);
    }

    // --- Game (icon 005): Recently Played + each emulator system + Applications ---
    {
        Ps3Cat c; c.name = "Game"; c.iconTex = mPs3CatTex[4];
        if (!mXmbRecent.empty()) {
            Ps3Item it;
            it.label = "Recently Played";
            it.kind = PS3_RECENT_LIST;
            it.iconTex = mIconTextures[15];
            it.iconR = it.iconG = it.iconB = 1.0f;
            c.items.push_back(it);
        }
        for (size_t s = 0; s < mXmbSystems.size(); s++) {
            const XmbSystem& sys = mXmbSystems[s];
            if (sys.roms.empty()) continue;
            Ps3Item it;
            it.label = sys.name;
            it.kind = PS3_SYSTEM;
            it.a = (int)s;
            it.iconTex = (s < 15) ? mIconTextures[s] : mIconTextures[16];
            it.iconR = sys.iconR; it.iconG = sys.iconG; it.iconB = sys.iconB;
            char buf[32];
            snprintf(buf, sizeof(buf), "%zu", sys.roms.size());
            it.value = buf;
            c.items.push_back(it);
        }
        if (!mAppEntries.empty() || mAppsLoaded) {
            Ps3Item it;
            it.label = "Applications";
            it.kind = PS3_APP_LIST;
            it.iconTex = mIconTextures[17];
            it.iconR = it.iconG = it.iconB = 1.0f;
            c.items.push_back(it);
        }
        mPs3Cats.push_back(c);
    }

    mPs3CatItemSel.assign(mPs3Cats.size(), 0);
    if (mPs3CatIdx < 0 || mPs3CatIdx >= (int)mPs3Cats.size())
        mPs3CatIdx = (mPs3Cats.size() >= 2) ? 1 : 0;   // land on Game
    mPs3ItemIdx = 0;
}

std::vector<NanoMenu::Ps3Item>& NanoMenu::ps3CurItems() {
    if (!mPs3Stack.empty()) return mPs3Stack.back().items;
    static std::vector<Ps3Item> empty;
    if (mPs3CatIdx < 0 || mPs3CatIdx >= (int)mPs3Cats.size()) return empty;
    return mPs3Cats[mPs3CatIdx].items;
}

int& NanoMenu::ps3CurSel() {
    if (!mPs3Stack.empty()) return mPs3Stack.back().sel;
    return mPs3ItemIdx;
}

// ---------------------------------------------------------------------------
// easing + the firmware item-carousel slot model
// ---------------------------------------------------------------------------
static inline float easeSmooth(float t) { return t * t * (3.0f - 2.0f * t); }   // smoothstep
static inline float easeOutBack(float t) {                                      // 10% overshoot
    const float c1 = 1.70158f, c3 = c1 + 1.0f;
    float u = t - 1.0f;
    return 1.0f + c3 * u * u * u + c1 * u * u;
}
static float itemSlotY(int idx, int sel) {
    const float ITEM_ABOVE_BASE_Y = 120.0f;
    if (idx == sel) return ps3::ITEM_FOCUS_Y;
    if (idx < sel) {
        float k = (float)(sel - idx);
        return ITEM_ABOVE_BASE_Y - (k - 1.0f) * ps3::ITEM_SPACING;
    }
    return ps3::ITEM_FOCUS_Y + ps3::ITEM_ACTIVE_PAD + (float)(idx - sel) * ps3::ITEM_SPACING;
}

// Current live category-bar offset (virtual px), eased.
static float ps3CatOffset(bool active, float t, float fromOff) {
    return active ? fromOff * (1.0f - easeSmooth(t)) : 0.0f;
}

// ---------------------------------------------------------------------------
// navigation - the handlers START animations; render() interpolates them.
// ---------------------------------------------------------------------------
void NanoMenu::ps3XmbLeft() {
    if (!mPs3Stack.empty()) { ps3XmbBack(); return; }
    if (mPs3Cats.empty() || mPs3CatIdx <= 0) return;
    float live = ps3CatOffset(mPs3CatAnimActive, mPs3CatT, mPs3CatFromOffset);
    mPs3CatOldIdx = mPs3CatIdx;
    mPs3CatOldSel = mPs3ItemIdx;
    mPs3CatItemSel[mPs3CatIdx] = mPs3ItemIdx;
    mPs3CatIdx--;
    mPs3ItemIdx = mPs3CatItemSel[mPs3CatIdx];
    mPs3CatFromOffset = live - ps3::CAT_SPACING;       // dir = -1
    mPs3CatT = 0.0f;
    mPs3CatAnimActive = true;
    mPs3ItemAnimActive = false;
}

void NanoMenu::ps3XmbRight() {
    if (!mPs3Stack.empty()) { ps3XmbSelect(); return; }
    if (mPs3Cats.empty() || mPs3CatIdx >= (int)mPs3Cats.size() - 1) return;
    float live = ps3CatOffset(mPs3CatAnimActive, mPs3CatT, mPs3CatFromOffset);
    mPs3CatOldIdx = mPs3CatIdx;
    mPs3CatOldSel = mPs3ItemIdx;
    mPs3CatItemSel[mPs3CatIdx] = mPs3ItemIdx;
    mPs3CatIdx++;
    mPs3ItemIdx = mPs3CatItemSel[mPs3CatIdx];
    mPs3CatFromOffset = live + ps3::CAT_SPACING;       // dir = +1
    mPs3CatT = 0.0f;
    mPs3CatAnimActive = true;
    mPs3ItemAnimActive = false;
}

void NanoMenu::ps3XmbUp() {
    int& sel = ps3CurSel();
    if (sel <= 0) return;
    mPs3ItemFromIdx = sel;
    sel--;
    mPs3ItemT = 0.0f;
    mPs3ItemAnimActive = true;
}

void NanoMenu::ps3XmbDown() {
    int& sel = ps3CurSel();
    int n = (int)ps3CurItems().size();
    if (sel >= n - 1) return;
    mPs3ItemFromIdx = sel;
    sel++;
    mPs3ItemT = 0.0f;
    mPs3ItemAnimActive = true;
}

void NanoMenu::ps3XmbSelect() {
    std::vector<Ps3Item>& items = ps3CurItems();
    int sel = ps3CurSel();
    if (sel < 0 || sel >= (int)items.size()) return;
    Ps3Item it = items[sel];
    switch (it.kind) {
        case PS3_SYSTEM: { Ps3Level lvl; buildRomSubmenu(it.a, lvl); mPs3Stack.push_back(lvl);
                           mPs3SubDir = 1; mPs3SubAnim = 0.0f; mPs3ItemAnimActive = false; break; }
        case PS3_RECENT_LIST: { Ps3Level lvl; buildRecentSubmenu(lvl); mPs3Stack.push_back(lvl);
                                mPs3SubDir = 1; mPs3SubAnim = 0.0f; mPs3ItemAnimActive = false; break; }
        case PS3_APP_LIST: { Ps3Level lvl; buildAppSubmenu(lvl); mPs3Stack.push_back(lvl);
                             mPs3SubDir = 1; mPs3SubAnim = 0.0f; mPs3ItemAnimActive = false; break; }
        case PS3_ROM: { mXmbSystemIndex = it.a; mXmbGameIndex = it.b; mSearchActive = false; launchXmbGame(); break; }
        case PS3_RECENT: { mXmbSystemIndex = -1; mXmbGameIndex = it.a; mSearchActive = false; launchXmbGame(); break; }
        case PS3_SETTING: { if (it.a == 0) openWifiScreen(); else if (it.a == 1) openBtScreen(); break; }
        default: break;
    }
}

void NanoMenu::ps3XmbBack() {
    if (!mPs3Stack.empty()) {
        mPs3Stack.pop_back();
        mPs3SubDir = -1; mPs3SubAnim = 1.0f;
        mPs3ItemAnimActive = false;
    }
}

// ---------------------------------------------------------------------------
// render
// ---------------------------------------------------------------------------
void NanoMenu::renderPs3Xmb() {
    if (!mPs3MenuBuilt) initPs3Menu();
    if (mPs3Cats.empty()) return;
    if (mMenuState == MENU_WIFI) { renderWifiScreen(); return; }
    if (mMenuState == MENU_BT)   { renderBtScreen();   return; }

    ps3::layoutComputeNative(mWidth, mHeight);

    float dt = mFrameDt; if (dt < 0.0f) dt = 0.0f; if (dt > 0.1f) dt = 0.1f;

    // advance timed animations
    if (mPs3CatAnimActive) {
        mPs3CatT += dt / (ps3::CAT_ANIM_MS / 1000.0f);
        if (mPs3CatT >= 1.0f) { mPs3CatT = 1.0f; mPs3CatAnimActive = false; }
    }
    if (mPs3ItemAnimActive) {
        mPs3ItemT += dt / (ps3::ITEM_ANIM_MS / 1000.0f);
        if (mPs3ItemT >= 1.0f) { mPs3ItemT = 1.0f; mPs3ItemAnimActive = false; }
    }
    // submenu collapse factor (smoothed toward the target)
    float subDecay = 1.0f - expf(-12.0f * dt);
    if (mPs3SubDir > 0) { mPs3SubAnim += subDecay * (1.0f - mPs3SubAnim); if (mPs3SubAnim > 0.999f) mPs3SubAnim = 1.0f; }
    else if (mPs3SubDir < 0) { mPs3SubAnim += subDecay * (0.0f - mPs3SubAnim); if (mPs3SubAnim < 0.001f) { mPs3SubAnim = 0.0f; mPs3SubDir = 0; } }
    bool inSub = !mPs3Stack.empty();
    float subT = inSub ? mPs3SubAnim : (mPs3SubDir < 0 ? mPs3SubAnim : 0.0f);

    float catOffset = ps3CatOffset(mPs3CatAnimActive, mPs3CatT, mPs3CatFromOffset);

    // ---- category bar ----
    const float catSubShift = -ps3::CAT_SUBMENU_SHIFT_X * subT;
    const float catSubScale = 1.0f + (ps3::CAT_SUBMENU_SCALE - 1.0f) * subT;
    for (int i = 0; i < (int)mPs3Cats.size(); i++) {
        bool isActive = (i == mPs3CatIdx);
        float nonActiveSubFade = isActive ? 1.0f : (1.0f - subT);
        if (nonActiveSubFade <= 0.002f) continue;
        float dx = ((float)i - (float)mPs3CatIdx) * ps3::CAT_SPACING + catOffset;
        float baseSz = isActive ? ps3::CAT_ICON_ACTIVE : ps3::CAT_ICON_INACTIVE;
        float sz = baseSz * catSubScale;
        float x = ps3::CAT_X + dx + catSubShift;
        float activeYOff = isActive ? ps3::CAT_Y_ACTIVE_OFFSET * (1.0f - subT) : 0.0f;
        float y = ps3::CAT_Y + activeYOff;
        if (x < -160.0f || x > ps3::VW + 160.0f) continue;
        int catDist = abs(i - mPs3CatIdx);
        float alpha = isActive ? 1.0f : (catDist <= 2 ? ps3::CAT_INACTIVE_ALPHA : ps3::CAT_FAR_ALPHA);
        alpha *= nonActiveSubFade;
        float dsz = ps3::devS(sz);
        drawIconTex(mPs3Cats[i].iconTex, ps3::devX(ps3::XCL(x, sz * 0.5f)),
                    ps3::devY(y - sz * 0.5f), dsz, dsz, 1.0f, 1.0f, 1.0f, alpha);
        if (isActive) {
            float la = 0.9f * (1.0f - 0.55f * subT);
            float ls = ps3::fontScale(ps3::CAT_LABEL_SIZE);
            const char* nm = mPs3Cats[i].name.c_str();
            float lw = measureText(nm, ls);
            float lx = ps3::devX(ps3::XCP(x)) - lw * 0.5f;
            float ly = ps3::baselineToTopY(ps3::devY(ps3::CAT_LABEL_Y), ls);
            drawText(nm, lx, ly, ls, 0.88f, 0.82f, 0.92f, la);
        }
    }

    // ---- item list helper ----
    // Renders an item list. xShiftV: virtual-px horizontal shift (rail slide).
    // animFromIdx >= 0: easeOutBack per-item Y interpolation from that selection.
    float fTop = ps3::frameTopV();
    float fBot = fTop + ps3::frameHV();
    auto drawList = [&](std::vector<Ps3Item>& items, int sel, float xShiftV,
                        float alphaMul, int animFromIdx) {
        if (items.empty() || alphaMul <= 0.01f) return;
        bool anim = (animFromIdx >= 0 && animFromIdx != sel);
        float e = anim ? easeOutBack(mPs3ItemT) : 1.0f;
        for (int i = 0; i < (int)items.size(); i++) {
            bool isActive = (i == sel);
            float y;
            if (anim) {
                float yNew = itemSlotY(i, sel), yOld = itemSlotY(i, animFromIdx);
                y = yNew + (yOld - yNew) * (1.0f - e);
            } else {
                y = itemSlotY(i, sel);
            }
            if (y < fTop - 90.0f || y > fBot + 90.0f) continue;
            float alpha = (isActive ? ps3::ALPHA_FOCUS : ps3::ALPHA_INACTIVE) * alphaMul;
            if (y < fTop + 40.0f) alpha *= fmaxf(0.0f, (y - fTop) / 40.0f);
            if (y > fBot - 20.0f) alpha *= fmaxf(0.0f, (fBot - y) / 20.0f);
            if (alpha <= 0.01f) continue;
            const Ps3Item& it = items[i];
            // Icon: modest active enlargement (firmware 180 is for game thumbnails
            // and looks oversized on the monochrome system icons).
            float isz = isActive ? (ps3::ITEM_ICON_SIZE * 1.18f) : ps3::ITEM_ICON_SIZE;
            float dsz = ps3::devS(isz);
            if (it.iconTex) {
                drawIconTex(it.iconTex,
                            ps3::devX(ps3::XCL(ps3::ITEM_ICON_X + xShiftV, isz * 0.5f)),
                            ps3::devY(y - isz * 0.5f), dsz, dsz,
                            it.iconR, it.iconG, it.iconB, alpha);
            }
            float tSize = isActive ? ps3::ITEM_TEXT_ACTIVE_SIZE : ps3::ITEM_TEXT_SIZE;
            float ts = ps3::fontScale(tSize);
            float tx = ps3::devX(ps3::XCP(ps3::ITEM_TEXT_X + xShiftV));
            float ty = ps3::baselineToTopY(ps3::devY(y), ts);
            if (isActive) {
                // Dual-halo breathing glow: an outer + inner ring of low-alpha
                // white passes around the crisp text (approximates the firmware
                // gaussian halo while staying cheap at 60fps).
                float phase = fmodf(mEffectTime, ps3::PULSE_PERIOD_MS / 1000.0f) /
                              (ps3::PULSE_PERIOD_MS / 1000.0f);
                float s = 0.5f * (1.0f - cosf(phase * 2.0f * (float)M_PI));
                float outerA = ps3::PULSE_ALPHA_MIN + (ps3::PULSE_ALPHA_MAX - ps3::PULSE_ALPHA_MIN) * s;
                float innerA = ps3::PULSE_INNER_MIN + (ps3::PULSE_INNER_MAX - ps3::PULSE_INNER_MIN) * s;
                float oR = ps3::devS(5.0f), iR = ps3::devS(2.2f);
                const char* L = it.label.c_str();
                for (int k = 0; k < 8; k++) {
                    float a = (float)k / 8.0f * 2.0f * (float)M_PI;
                    drawText(L, tx + cosf(a) * oR, ty + sinf(a) * oR, ts, 1.0f, 1.0f, 1.0f, outerA * alphaMul * 0.16f);
                }
                for (int k = 0; k < 6; k++) {
                    float a = ((float)k + 0.5f) / 6.0f * 2.0f * (float)M_PI;
                    drawText(L, tx + cosf(a) * iR, ty + sinf(a) * iR, ts, 1.0f, 1.0f, 1.0f, innerA * alphaMul * 0.28f);
                }
                drawText(L, tx, ty, ts, 1.0f, 1.0f, 1.0f, alpha);
                if (!it.desc.empty()) {
                    float ds = ps3::fontScale(ps3::ITEM_DESC_SIZE * ps3::DESC_BOOST);
                    float dy = ps3::baselineToTopY(ps3::devY(y + ps3::ITEM_DESC_OFFSET), ds);
                    drawText(it.desc.c_str(), tx, dy, ds, 0.85f, 0.85f, 0.88f, 0.7f * alphaMul);
                }
            } else {
                drawText(it.label.c_str(), tx, ty, ts, 0.92f, 0.92f, 0.92f, alpha);
            }
            if (!it.value.empty()) {
                float vs = ps3::fontScale(ps3::ITEM_TEXT_SIZE);
                float vw = measureText(it.value.c_str(), vs);
                float vx = ps3::devX(ps3::XCF(ps3::VW - ps3::ITEM_VALUE_RIGHT_PAD)) - vw;
                drawText(it.value.c_str(), vx, ty, vs, 0.7f, 0.7f, 0.75f, alpha * 0.85f);
            }
        }
    };

    // ---- item list (with the category slide rail + fade-crossfade) ----
    float childShiftV = inSub ? ps3::SUBMENU_TEXT_X_SHIFT * (1.0f - subT) : 0.0f;
    if (mPs3CatAnimActive && !inSub) {
        // The whole rail translates; old category fades out, new fades in.
        float p = easeSmooth(mPs3CatT);
        float barTravel = mPs3CatFromOffset;
        float oldShift = -barTravel * p;
        float newShift = barTravel * (1.0f - p);
        float oldAlpha = fmaxf(0.0f, 1.0f - p / 0.4f);
        float newAlpha = fmaxf(0.0f, (p - 0.5f) / 0.5f);
        if (mPs3CatOldIdx >= 0 && mPs3CatOldIdx < (int)mPs3Cats.size())
            drawList(mPs3Cats[mPs3CatOldIdx].items, mPs3CatOldSel, oldShift, oldAlpha, -1);
        drawList(mPs3Cats[mPs3CatIdx].items, mPs3ItemIdx, newShift, newAlpha, -1);
    } else {
        std::vector<Ps3Item>& items = ps3CurItems();
        int sel = ps3CurSel();
        drawList(items, sel, childShiftV, inSub ? subT : 1.0f,
                 mPs3ItemAnimActive ? mPs3ItemFromIdx : -1);
    }

    drawPs3Clock(1.0f);
}

// ---------------------------------------------------------------------------
// clock: U-frame + analog face + DD/M H:MM (index.html drawClock)
// ---------------------------------------------------------------------------
void NanoMenu::drawPs3Clock(float fadeMul) {
    time_t tt = time(nullptr);
    struct tm lt; localtime_r(&tt, &lt);
    char timeStr[40];
    snprintf(timeStr, sizeof(timeStr), "%d/%d %d:%02d", lt.tm_mday, lt.tm_mon + 1, lt.tm_hour, lt.tm_min);

    // The clock is right-anchored: the web translates the group by
    // V.W*(LAYOUT_FIT-1) so its right edge lands on the frame's right edge.
    const float shiftV = ps3::VW * (ps3::LAYOUT_FIT - 1.0f);
    auto cx = [&](float vx) { return ps3::devX(vx + shiftV); };
    // Portrait: raise the clock toward the frame top (frameTopV < 0).
    float clockDY = (ps3::frameTopV() < 0.0f) ? (ps3::frameTopV() + 40.0f - ps3::CLOCK_FRAME_Y) : 0.0f;
    auto cy = [&](float vy) { return ps3::devY(vy + clockDY); };

    float dxL = cx(ps3::CLOCK_FRAME_X);
    float dxR = cx(ps3::VW + 4.0f);                 // open past the right edge
    float dyT = cy(ps3::CLOCK_FRAME_Y);
    float dyB = cy(ps3::CLOCK_FRAME_Y + ps3::CLOCK_FRAME_H);
    float lw = fmaxf(1.0f, ps3::devS(1.5f));

    drawQuad(dxL, dyT, dxR - dxL, dyB - dyT, 0.0f, 0.0f, 0.0f, 0.18f * fadeMul);   // dim panel
    float ba = 0.35f * fadeMul;
    drawQuad(dxL, dyT, dxR - dxL, lw, 1.0f, 1.0f, 1.0f, ba);          // top line
    drawQuad(dxL, dyB - lw, dxR - dxL, lw, 1.0f, 1.0f, 1.0f, ba);     // bottom line
    drawQuad(dxL, dyT, lw, dyB - dyT, 1.0f, 1.0f, 1.0f, ba);          // left line

    // ---- analog face: ring + two hands ----
    float iconCX = cx(ps3::CLOCK_ICON_CX);
    float iconCY = (dyT + dyB) * 0.5f;
    float R = ps3::devS(ps3::CLOCK_ICON_R);
    float ringW = fmaxf(1.5f, R * 0.16f);
    const int SEG = 28;
    for (int i = 0; i < SEG; i++) {
        float a0 = (float)i / SEG * 2.0f * (float)M_PI;
        float a1 = (float)(i + 1) / SEG * 2.0f * (float)M_PI;
        float ro = R, ri = R - ringW;
        float x0o = iconCX + cosf(a0) * ro, y0o = iconCY + sinf(a0) * ro;
        float x1o = iconCX + cosf(a1) * ro, y1o = iconCY + sinf(a1) * ro;
        float x0i = iconCX + cosf(a0) * ri, y0i = iconCY + sinf(a0) * ri;
        float x1i = iconCX + cosf(a1) * ri, y1i = iconCY + sinf(a1) * ri;
        drawTriangle(x0o, y0o, x1o, y1o, x0i, y0i, 1.0f, 1.0f, 1.0f, 0.96f * fadeMul);
        drawTriangle(x1o, y1o, x1i, y1i, x0i, y0i, 1.0f, 1.0f, 1.0f, 0.96f * fadeMul);
    }
    float handW = fmaxf(2.0f, R * 0.25f);
    float hourAng = -(float)M_PI / 2.0f + (((lt.tm_hour % 12) + lt.tm_min / 60.0f) / 12.0f) * 2.0f * (float)M_PI;
    float minAng  = -(float)M_PI / 2.0f + (lt.tm_min / 60.0f) * 2.0f * (float)M_PI;
    auto hand = [&](float ang, float len) {
        float ex = iconCX + cosf(ang) * len, ey = iconCY + sinf(ang) * len;
        float px = -sinf(ang) * handW * 0.5f, py = cosf(ang) * handW * 0.5f;
        drawTriangle(iconCX + px, iconCY + py, iconCX - px, iconCY - py, ex + px, ey + py, 1.0f, 1.0f, 1.0f, 0.96f * fadeMul);
        drawTriangle(ex + px, ey + py, ex - px, ey - py, iconCX - px, iconCY - py, 1.0f, 1.0f, 1.0f, 0.96f * fadeMul);
    };
    hand(hourAng, R * 0.47f);
    hand(minAng, R * 0.69f);

    // ---- time text (right-aligned, left of the icon) ----
    float ts = ps3::fontScale(ps3::CLOCK_SIZE);
    float tw = measureText(timeStr, ts);
    float textRight = iconCX - R - ps3::devS(6.0f);
    float ty = ps3::baselineToTopY(iconCY + ps3::devS(3.0f), ts);
    drawText(timeStr, textRight - tw, ty, ts, 1.0f, 1.0f, 1.0f, 0.92f * fadeMul);
}

} // namespace android
