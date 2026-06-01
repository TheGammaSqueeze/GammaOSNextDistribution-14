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
// (index.html drawXMB / drawItemList / drawClock): the horizontal category bar,
// the vertical item list dropping out of the active category at the firmware
// geometry, the firmware animation language (category slide rail + fade
// crossfade, smooth accelerating item scroll), the clock, all over the captured
// wave. Categories are populated from nano's real content. Gated by
// persist.gammaos.nano.ps3xmb.

#define LOG_TAG "GammaOSNano"

#include "NanoMenu.h"
#include "NanoMenuPS3.h"
#include "NanoMenuPS3Bg.h"
#include "NanoMenuPS3Data.h"

#include <ctype.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <time.h>
#include <vector>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#include <GLES2/gl2.h>
#include <png.h>

#include <cutils/properties.h>
#include <utils/Log.h>

namespace android {

// DRM GL rotation matrix (NanoMenuShaders.cpp). The menu draws in logical device
// space and the shader rotates to the physical panel; a drop-shadow offset must
// therefore be expressed so it lands "down" on the FINAL panel, not in logical
// space (otherwise a 180-degree panel flips the shadow). See ps3ShadowOffset().
extern float sDrmRotMat[4];

// Device-space offset (out[0],out[1]) that, after the DRM rotation, appears as a
// straight-down shadow of `s` panel pixels (matching the web's offsetX 0,
// offsetY +1). Derivation: panel-down NDC delta back-rotated into device px.
// Reduces to (0,+s) with no rotation and (0,-s) at 180 degrees.
static inline void ps3ShadowOffset(float s, int w, int h, float out[2]) {
    out[0] = -sDrmRotMat[1] * s * ((float)w / (float)h);
    out[1] =  sDrmRotMat[3] * s;
}

// ---------------------------------------------------------------------------
// libpng loader -> mono-white (silvery) RGBA texture for the PS3 icons.
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
    for (size_t i = 0; i + 3 < pixels.size(); i += 4) { pixels[i] = 255; pixels[i+1] = 255; pixels[i+2] = 255; }
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
// model build (1:1 from the web DATA tree + the nano Game consoles)
// ---------------------------------------------------------------------------
// Build one runtime Ps3Item from a static DATA node (glass via nmap_NNN).
NanoMenu::Ps3Item NanoMenu::makeDataItem(const Ps3DataItem* d) {
    Ps3Item it;
    it.label = d->name;
    if (d->desc)  it.desc  = d->desc;
    if (d->value) it.value = d->value;
    it.action = d->action;
    it.data = d;
    it.kind = (d->children && d->childCount > 0) ? PS3_DATA_SUBMENU : PS3_DATA_LEAF;
    // PS Store has no nano normal-map asset; stand in with the Game icon so it
    // still reads as glass. All other DATA icon indices have an nmap_NNN.
    int icon = (d->icon >= 0) ? d->icon : 5;
    it.nmapTex = nmapForIcon(icon);
    it.iconR = it.iconG = it.iconB = 1.0f;
    return it;
}

void NanoMenu::initPs3Menu() {
    if (mPs3MenuBuilt) return;
    // UI-size preference. Default 1.12 (a slight enlargement that reads better on
    // small physical panels like the 3.2" Brick); user-tunable via the prop.
    char usbuf[PROPERTY_VALUE_MAX];
    property_get("persist.gammaos.nano.ps3xmb.uiscale", usbuf, "1.12");
    mPs3UiScale = (float)atof(usbuf);
    if (mPs3UiScale < 0.5f || mPs3UiScale > 2.0f) mPs3UiScale = 1.12f;
    initGlassIcons();
    static const char* kCatIconFiles[6] = {
        "xmb_icon_001.png", "xmb_icon_002.png", "xmb_icon_003.png",
        "xmb_icon_004.png", "xmb_icon_005.png", "xmb_icon_006.png",
    };
    for (int i = 0; i < 6; i++) {
        mPs3CatTex[i] = loadPs3IconTex(kCatIconFiles[i]);
        mPs3CatNmap[i] = nmapForIcon(i + 1);   // category icons are xmb_icon 1..6
    }
    buildPs3Cats();
    mPs3MenuBuilt = true;
    ALOGI("ps3menu: built %zu categories", mPs3Cats.size());
}

// Build a submenu level from a static DATA node's children.
void NanoMenu::buildDataSubmenu(const Ps3DataItem* node, Ps3Level& out) {
    out.items.clear(); out.sel = 0;
    out.title = node ? node->name : "";
    if (!node || !node->children) return;
    for (int i = 0; i < node->childCount; i++)
        out.items.push_back(makeDataItem(&node->children[i]));
}

void NanoMenu::buildRomSubmenu(int sysIdx, Ps3Level& out) {
    out.items.clear(); out.sel = 0;
    if (sysIdx < 0 || sysIdx >= (int)mXmbSystems.size()) return;
    const XmbSystem& sys = mXmbSystems[sysIdx];
    out.title = sys.name;
    GLuint bevel = bevelForIconIdx(16);   // generic game cartridge bevel
    for (size_t i = 0; i < sys.displayNames.size(); i++) {
        Ps3Item it;
        it.label = sys.displayNames[i];
        it.kind = PS3_ROM; it.a = sysIdx; it.b = (int)i;
        it.iconTex = mIconTextures[16]; it.nmapTex = bevel;
        it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    }
}

void NanoMenu::buildRecentSubmenu(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.title = "Recently Played";
    GLuint bevel = bevelForIconIdx(15);
    for (size_t i = 0; i < mXmbRecent.size(); i++) {
        Ps3Item it;
        it.label = mXmbRecent[i].displayName;
        it.value = mXmbRecent[i].systemName;
        it.kind = PS3_RECENT; it.a = (int)i;
        it.iconTex = mIconTextures[15]; it.nmapTex = bevel;
        it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    }
}

void NanoMenu::buildAppSubmenu(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.title = "Applications";
    if (!mAppsLoaded) loadInstalledApps();
    GLuint bevel = bevelForIconIdx(16);
    for (size_t i = 0; i < mAppEntries.size(); i++) {
        Ps3Item it;
        it.label = mAppEntries[i].label;
        it.kind = PS3_APP; it.a = (int)i; it.payloadStr = mAppEntries[i].packageName;
        it.iconTex = mIconTextures[16]; it.nmapTex = bevel;
        it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    }
}

void NanoMenu::buildPs3Cats() {
    mPs3Cats.clear();
    int gameCatRuntimeIdx = -1;

    // Categories straight from the web DATA tree (Users/PSN/Friends excluded by
    // the table). Each item is glass-rendered from its xmb_icon normal map.
    for (int ci = 0; ci < kPs3DataCatCount; ci++) {
        const Ps3DataCat& dc = kPs3DataCats[ci];
        Ps3Cat c;
        c.name = dc.name;
        int catIdx = (dc.icon >= 1 && dc.icon <= 6) ? dc.icon - 1 : 0;
        c.iconTex = mPs3CatTex[catIdx];
        c.nmapTex = mPs3CatNmap[catIdx];
        for (int ii = 0; ii < dc.itemCount; ii++)
            c.items.push_back(makeDataItem(&dc.items[ii]));
        if (strcmp(dc.id, "game") == 0) gameCatRuntimeIdx = (int)mPs3Cats.size();
        mPs3Cats.push_back(c);
    }

    // The ONLY nano addition: the emulator consoles / Recently Played / Apps go
    // under Game so games actually launch. Prepend them (functional content
    // first) ahead of the firmware demo items.
    if (gameCatRuntimeIdx >= 0) {
        Ps3Cat& game = mPs3Cats[gameCatRuntimeIdx];
        std::vector<Ps3Item> nano;
        if (!mXmbRecent.empty()) {
            Ps3Item it; it.label = "Recently Played"; it.kind = PS3_RECENT_LIST;
            it.iconTex = mIconTextures[15]; it.nmapTex = bevelForIconIdx(15);
            it.iconR = it.iconG = it.iconB = 1.0f; nano.push_back(it);
        }
        for (size_t s = 0; s < mXmbSystems.size(); s++) {
            const XmbSystem& sys = mXmbSystems[s];
            if (sys.roms.empty()) continue;
            Ps3Item it; it.label = sys.name; it.kind = PS3_SYSTEM; it.a = (int)s;
            int iconIdx = (s < 15) ? (int)s : 16;
            it.iconTex = mIconTextures[iconIdx]; it.nmapTex = bevelForIconIdx(iconIdx);
            it.iconR = it.iconG = it.iconB = 1.0f;
            char buf[32]; snprintf(buf, sizeof(buf), "%zu", sys.roms.size()); it.value = buf;
            nano.push_back(it);
        }
        { Ps3Item it; it.label = "Applications"; it.kind = PS3_APP_LIST;
          it.iconTex = mIconTextures[16]; it.nmapTex = bevelForIconIdx(16);
          it.iconR = it.iconG = it.iconB = 1.0f; nano.push_back(it); }
        game.items.insert(game.items.begin(), nano.begin(), nano.end());
    }

    mPs3CatItemSel.assign(mPs3Cats.size(), 0);
    // Land on Game by default.
    if (mPs3CatIdx < 0 || mPs3CatIdx >= (int)mPs3Cats.size()) {
        mPs3CatIdx = (gameCatRuntimeIdx >= 0) ? gameCatRuntimeIdx : 0;
    }
    mPs3ItemIdx = 0;
    mPs3AnimItem = 0.0f;
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
static inline float easeSmooth(float t) { return t * t * (3.0f - 2.0f * t); }
static float itemSlotY(int idx, int sel) {
    const float ITEM_ABOVE_BASE_Y = 120.0f;
    if (idx == sel) return ps3::ITEM_FOCUS_Y;
    if (idx < sel) {
        float k = (float)(sel - idx);
        return ITEM_ABOVE_BASE_Y - (k - 1.0f) * ps3::ITEM_SPACING;
    }
    return ps3::ITEM_FOCUS_Y + ps3::ITEM_ACTIVE_PAD + (float)(idx - sel) * ps3::ITEM_SPACING;
}
// Item Y for a CONTINUOUS selection position (interpolates the slot model).
static float itemSlotYf(int idx, float selPos) {
    int s0 = (int)floorf(selPos);
    float frac = selPos - (float)s0;
    return itemSlotY(idx, s0) + (itemSlotY(idx, s0 + 1) - itemSlotY(idx, s0)) * frac;
}
static float ps3CatOffset(bool active, float t, float fromOff) {
    return active ? fromOff * (1.0f - easeSmooth(t)) : 0.0f;
}

// ---------------------------------------------------------------------------
// navigation - smooth continuous item position; timed category slide rail.
// ---------------------------------------------------------------------------
void NanoMenu::ps3XmbLeft() {
    if (!mPs3Stack.empty()) { ps3XmbBack(); return; }
    if (mPs3Cats.empty() || mPs3CatIdx <= 0) return;
    float live = ps3CatOffset(mPs3CatAnimActive, mPs3CatT, mPs3CatFromOffset);
    mPs3CatOldIdx = mPs3CatIdx; mPs3CatOldSel = mPs3ItemIdx;
    mPs3CatItemSel[mPs3CatIdx] = mPs3ItemIdx;
    mPs3CatIdx--;
    mPs3ItemIdx = mPs3CatItemSel[mPs3CatIdx];
    mPs3AnimItem = (float)mPs3ItemIdx;       // snap; do not animate across lists
    mPs3CatFromOffset = live - ps3::CAT_SPACING;
    mPs3CatT = 0.0f; mPs3CatAnimActive = true;
}

void NanoMenu::ps3XmbRight() {
    if (!mPs3Stack.empty()) { ps3XmbSelect(); return; }
    if (mPs3Cats.empty() || mPs3CatIdx >= (int)mPs3Cats.size() - 1) return;
    float live = ps3CatOffset(mPs3CatAnimActive, mPs3CatT, mPs3CatFromOffset);
    mPs3CatOldIdx = mPs3CatIdx; mPs3CatOldSel = mPs3ItemIdx;
    mPs3CatItemSel[mPs3CatIdx] = mPs3ItemIdx;
    mPs3CatIdx++;
    mPs3ItemIdx = mPs3CatItemSel[mPs3CatIdx];
    mPs3AnimItem = (float)mPs3ItemIdx;
    mPs3CatFromOffset = live + ps3::CAT_SPACING;
    mPs3CatT = 0.0f; mPs3CatAnimActive = true;
}

void NanoMenu::ps3XmbUp()   { int& s = ps3CurSel(); if (s > 0) s--; }
void NanoMenu::ps3XmbDown() { int& s = ps3CurSel(); int n = (int)ps3CurItems().size(); if (s < n - 1) s++; }

void NanoMenu::ps3XmbSelect() {
    std::vector<Ps3Item>& items = ps3CurItems();
    int sel = ps3CurSel();
    if (sel < 0 || sel >= (int)items.size()) return;
    Ps3Item it = items[sel];
    switch (it.kind) {
        case PS3_SYSTEM: { Ps3Level lvl; buildRomSubmenu(it.a, lvl); mPs3Stack.push_back(lvl);
                           mPs3SubDir = 1; mPs3SubAnim = 0.0f; mPs3AnimItem = 0.0f; break; }
        case PS3_RECENT_LIST: { Ps3Level lvl; buildRecentSubmenu(lvl); mPs3Stack.push_back(lvl);
                                mPs3SubDir = 1; mPs3SubAnim = 0.0f; mPs3AnimItem = 0.0f; break; }
        case PS3_APP_LIST: { Ps3Level lvl; buildAppSubmenu(lvl); mPs3Stack.push_back(lvl);
                             mPs3SubDir = 1; mPs3SubAnim = 0.0f; mPs3AnimItem = 0.0f; break; }
        case PS3_ROM: { mXmbSystemIndex = it.a; mXmbGameIndex = it.b; mSearchActive = false; launchXmbGame(); break; }
        case PS3_RECENT: { mXmbSystemIndex = -1; mXmbGameIndex = it.a; mSearchActive = false; launchXmbGame(); break; }
        case PS3_SETTING: { if (it.a == 0) openWifiScreen(); else if (it.a == 1) openBtScreen(); break; }
        case PS3_DATA_SUBMENU: { Ps3Level lvl; buildDataSubmenu(it.data, lvl); mPs3Stack.push_back(lvl);
                                 mPs3SubDir = 1; mPs3SubAnim = 0.0f; mPs3AnimItem = 0.0f; break; }
        case PS3_DATA_LEAF: break;   // dialog / value items: side-panel choosers wired in a later pass
        default: break;   // PS3_APP / PS3_LAUNCH_PKG launch wired in a later pass
    }
}

void NanoMenu::ps3XmbBack() {
    if (!mPs3Stack.empty()) {
        mPs3Stack.pop_back();
        mPs3SubDir = -1; mPs3SubAnim = 1.0f;
        mPs3AnimItem = (float)ps3CurSel();
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

    { ps3::LayoutParams lp; lp.panelW = mWidth; lp.panelH = mHeight; lp.uiScale = mPs3UiScale;
      ps3::layoutCompute(lp); }

    // Panel-down drop-shadow offset for the menu icons + text (rotation-aware, so
    // the 180-degree Brick panel does not flip it). Web: icon shadow offsetY ~1-2.
    float so[2]; ps3ShadowOffset(ps3::devS(1.5f), mWidth, mHeight, so);

    float dt = mFrameDt; if (dt < 0.0f) dt = 0.0f; if (dt > 0.1f) dt = 0.1f;

    // category slide (timed) + continuous item tracker (smooth, accelerates on hold)
    if (mPs3CatAnimActive) {
        mPs3CatT += dt / (ps3::CAT_ANIM_MS / 1000.0f);
        if (mPs3CatT >= 1.0f) { mPs3CatT = 1.0f; mPs3CatAnimActive = false; }
    }
    // Item scroll: the original pre-port nano feel (frame-rate-independent
    // exp-decay at rate 12, matching the old XMB's mXmbAnimY) plus the shared
    // accelerating d-pad auto-repeat (tickNavRepeat). The user preferred this.
    float itemDecay = 1.0f - expf(-12.0f * dt);
    float itemTarget = (float)ps3CurSel();
    mPs3AnimItem += (itemTarget - mPs3AnimItem) * itemDecay;
    if (fabsf(mPs3AnimItem - itemTarget) < 0.005f) mPs3AnimItem = itemTarget;
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
        // Inactive category icons keep a single consistent opacity regardless of
        // distance (no extra fade as they approach the screen edge, per request).
        float alpha = isActive ? 1.0f : ps3::CAT_INACTIVE_ALPHA;
        alpha *= nonActiveSubFade;
        float dsz = ps3::devS(sz);
        float ix = ps3::devX(ps3::XCL(x, sz * 0.5f)), iy = ps3::devY(y - sz * 0.5f);
        // Category icon drop shadow (panel-down), then the glass/flat icon.
        if (mPs3Cats[i].iconTex)
            drawIconTex(mPs3Cats[i].iconTex, ix + so[0], iy + so[1], dsz, dsz, 0.0f, 0.0f, 0.0f, 0.42f * alpha);
        if (mIconGlassReady && mPs3Cats[i].nmapTex && ps3bg::workTex())
            drawGlassIcon(mPs3Cats[i].nmapTex, ix, iy, dsz, dsz, 1.0f, 1.0f, 1.0f, alpha);
        else
            drawIconTex(mPs3Cats[i].iconTex, ix, iy, dsz, dsz, 1.0f, 1.0f, 1.0f, alpha);
        if (isActive) {
            float la = 0.9f * (1.0f - 0.55f * subT);
            float ls = ps3::fontScale(ps3::CAT_LABEL_SIZE);
            const char* nm = mPs3Cats[i].name.c_str();
            float lw = measureText(nm, ls);
            float lx = ps3::devX(ps3::XCP(x)) - lw * 0.5f;
            float ly = ps3::baselineToTopY(ps3::devY(ps3::CAT_LABEL_Y), ls);
            drawText(nm, lx + so[0], ly + so[1], ls, 0.0f, 0.0f, 0.0f, 0.5f * la);   // shadow
            drawText(nm, lx, ly, ls, 0.88f, 0.82f, 0.92f, la);
        }
    }

    // ---- item-list renderer (continuous selection position selPos) ----
    float fTop = ps3::frameTopV();
    float fBot = fTop + ps3::frameHV();
    auto drawList = [&](std::vector<Ps3Item>& items, float selPos, float xShiftV, float alphaMul) {
        if (items.empty() || alphaMul <= 0.01f) return;
        int activeIdx = (int)lroundf(selPos);
        for (int i = 0; i < (int)items.size(); i++) {
            bool isActive = (i == activeIdx);
            float y = itemSlotYf(i, selPos);
            if (y < fTop - 90.0f || y > fBot + 90.0f) continue;
            float alpha = (isActive ? ps3::ALPHA_FOCUS : ps3::ALPHA_INACTIVE) * alphaMul;
            if (y < fTop + 40.0f) alpha *= fmaxf(0.0f, (y - fTop) / 40.0f);
            if (y > fBot - 20.0f) alpha *= fmaxf(0.0f, (fBot - y) / 20.0f);
            if (alpha <= 0.01f) continue;
            const Ps3Item& it = items[i];
            float isz = isActive ? (ps3::ITEM_ICON_SIZE * 1.18f) : ps3::ITEM_ICON_SIZE;
            float dsz = ps3::devS(isz);
            float ix = ps3::devX(ps3::XCL(ps3::ITEM_ICON_X + xShiftV, isz * 0.5f));
            float iy = ps3::devY(y - isz * 0.5f);
            // Icon drop shadow (panel-down): a dark silhouette offset behind the
            // icon so it reads over the bright wave (web icon shadow offsetY 1).
            if (it.iconTex)
                drawIconTex(it.iconTex, ix + so[0], iy + so[1], dsz, dsz, 0.0f, 0.0f, 0.0f, 0.42f * alpha);
            if (mIconGlassReady && it.nmapTex && ps3bg::workTex())
                drawGlassIcon(it.nmapTex, ix, iy, dsz, dsz, it.iconR, it.iconG, it.iconB, alpha);
            else if (it.iconTex)
                drawIconTex(it.iconTex, ix, iy, dsz, dsz, it.iconR, it.iconG, it.iconB, alpha);
            float tSize = isActive ? ps3::ITEM_TEXT_ACTIVE_SIZE : ps3::ITEM_TEXT_SIZE;
            float ts = ps3::fontScale(tSize);
            float tx = ps3::devX(ps3::XCP(ps3::ITEM_TEXT_X + xShiftV));
            float ty = ps3::baselineToTopY(ps3::devY(y), ts);
            const char* L = it.label.c_str();
            // Text drop shadow (panel-down) under every label for legibility.
            drawText(L, tx + so[0], ty + so[1], ts, 0.0f, 0.0f, 0.0f, 0.5f * alpha);
            if (isActive) {
                float phase = fmodf(mEffectTime, ps3::PULSE_PERIOD_MS / 1000.0f) / (ps3::PULSE_PERIOD_MS / 1000.0f);
                float s = 0.5f * (1.0f - cosf(phase * 2.0f * (float)M_PI));
                float outerA = ps3::PULSE_ALPHA_MIN + (ps3::PULSE_ALPHA_MAX - ps3::PULSE_ALPHA_MIN) * s;
                float innerA = ps3::PULSE_INNER_MIN + (ps3::PULSE_INNER_MAX - ps3::PULSE_INNER_MIN) * s;
                float oR = ps3::devS(5.0f), iR = ps3::devS(2.2f);
                for (int k = 0; k < 8; k++) { float a = (float)k / 8.0f * 2.0f * (float)M_PI;
                    drawText(L, tx + cosf(a) * oR, ty + sinf(a) * oR, ts, 1.0f, 1.0f, 1.0f, outerA * alphaMul * 0.16f); }
                for (int k = 0; k < 6; k++) { float a = ((float)k + 0.5f) / 6.0f * 2.0f * (float)M_PI;
                    drawText(L, tx + cosf(a) * iR, ty + sinf(a) * iR, ts, 1.0f, 1.0f, 1.0f, innerA * alphaMul * 0.28f); }
                drawText(L, tx, ty, ts, 1.0f, 1.0f, 1.0f, alpha);
            } else {
                drawText(L, tx, ty, ts, 0.92f, 0.92f, 0.92f, alpha);
            }
            if (!it.value.empty()) {
                float vs = ps3::fontScale(ps3::ITEM_TEXT_SIZE);
                float vw = measureText(it.value.c_str(), vs);
                float vx = ps3::devX(ps3::XCF(ps3::VW - ps3::ITEM_VALUE_RIGHT_PAD)) - vw;
                drawText(it.value.c_str(), vx + so[0], ty + so[1], vs, 0.0f, 0.0f, 0.0f, 0.45f * alpha);
                drawText(it.value.c_str(), vx, ty, vs, 0.7f, 0.7f, 0.75f, alpha * 0.85f);
            }
        }
    };

    // ---- item list (category slide rail + fade-crossfade) ----
    float childShiftV = inSub ? ps3::SUBMENU_TEXT_X_SHIFT * (1.0f - subT) : 0.0f;
    if (mPs3CatAnimActive && !inSub) {
        float p = easeSmooth(mPs3CatT);
        float barTravel = mPs3CatFromOffset;
        float oldAlpha = fmaxf(0.0f, 1.0f - p / 0.4f);
        float newAlpha = fmaxf(0.0f, (p - 0.5f) / 0.5f);
        if (mPs3CatOldIdx >= 0 && mPs3CatOldIdx < (int)mPs3Cats.size())
            drawList(mPs3Cats[mPs3CatOldIdx].items, (float)mPs3CatOldSel, -barTravel * p, oldAlpha);
        drawList(mPs3Cats[mPs3CatIdx].items, mPs3AnimItem, barTravel * (1.0f - p), newAlpha);
    } else {
        drawList(ps3CurItems(), mPs3AnimItem, childShiftV, inSub ? subT : 1.0f);
    }

    drawPs3Clock(1.0f);
}

// ---------------------------------------------------------------------------
// clock: open-right rounded U-frame + analog face + DD/M H:MM (drawClock 1:1)
// ---------------------------------------------------------------------------
void NanoMenu::drawPs3Clock(float fadeMul) {
    time_t tt = time(nullptr);
    struct tm lt; localtime_r(&tt, &lt);
    char timeStr[40];
    snprintf(timeStr, sizeof(timeStr), "%d/%d %d:%02d", lt.tm_mday, lt.tm_mon + 1, lt.tm_hour, lt.tm_min);

    const float shiftV = ps3::VW * (ps3::LAYOUT_FIT - 1.0f);   // right-anchor
    auto cx = [&](float vx) { return ps3::devX(vx + shiftV); };
    float clockDY = (ps3::frameTopV() < 0.0f) ? (ps3::frameTopV() + 40.0f - ps3::CLOCK_FRAME_Y) : 0.0f;
    auto cy = [&](float vy) { return ps3::devY(vy + clockDY); };

    float dxL = cx(ps3::CLOCK_FRAME_X);
    float dxR = cx(ps3::VW + 4.0f);
    float dyT = cy(ps3::CLOCK_FRAME_Y);
    float dyB = cy(ps3::CLOCK_FRAME_Y + ps3::CLOCK_FRAME_H);
    float fr  = ps3::devS(ps3::CLOCK_FRAME_CORNER);
    float lw  = fmaxf(1.0f, ps3::devS(1.0f));
    float so[2]; ps3ShadowOffset(ps3::devS(1.0f), mWidth, mHeight, so);   // panel-down drop shadow

    // filled dim panel (open-right, rounded left corners)
    auto fillURect = [&](float x0, float y0, float x1, float y1, float rad, float r, float g, float b, float a) {
        if (rad < 1.0f) { drawQuad(x0, y0, x1 - x0, y1 - y0, r, g, b, a); return; }
        drawQuad(x0 + rad, y0, (x1 - x0) - rad, y1 - y0, r, g, b, a);     // main body
        drawQuad(x0, y0 + rad, rad, (y1 - y0) - 2.0f * rad, r, g, b, a);  // left strip
        // top-left (arc 90deg..180deg) + bottom-left (180deg..270deg) corner fans.
        const int CSEG = 10;
        for (int corner = 0; corner < 2; corner++) {
            float ccx = x0 + rad, ccy = (corner == 0) ? (y0 + rad) : (y1 - rad);
            float a0 = (corner == 0) ? (0.5f * (float)M_PI) : (float)M_PI;
            for (int s = 0; s < CSEG; s++) {
                float t0 = a0 + (float)s / (float)CSEG * 0.5f * (float)M_PI;
                float t1 = a0 + (float)(s + 1) / (float)CSEG * 0.5f * (float)M_PI;
                drawTriangle(ccx, ccy,
                             ccx + cosf(t0) * rad, ccy - sinf(t0) * rad,
                             ccx + cosf(t1) * rad, ccy - sinf(t1) * rad, r, g, b, a);
            }
        }
    };
    fillURect(dxL, dyT, dxR, dyB, fr, 0.0f, 0.0f, 0.0f, 0.18f * fadeMul);

    // border outline (open-right): top + bottom + left lines + the two rounded corners.
    auto strokeU = [&](float ox, float oy, float r, float g, float b, float a) {
        drawQuad(dxL + fr + ox, dyT + oy, (dxR - dxL) - fr, lw, r, g, b, a);             // top
        drawQuad(dxL + fr + ox, dyB - lw + oy, (dxR - dxL) - fr, lw, r, g, b, a);        // bottom
        drawQuad(dxL + ox, dyT + fr + oy, lw, (dyB - dyT) - 2.0f * fr, r, g, b, a);      // left
        const int CSEG = 10;
        for (int corner = 0; corner < 2; corner++) {
            float ccx = dxL + fr + ox, ccy = ((corner == 0) ? (dyT + fr) : (dyB - fr)) + oy;
            float a0 = (corner == 0) ? (0.5f * (float)M_PI) : (float)M_PI;
            for (int s = 0; s < CSEG; s++) {
                float t0 = a0 + (float)s / (float)CSEG * 0.5f * (float)M_PI;
                float t1 = a0 + (float)(s + 1) / (float)CSEG * 0.5f * (float)M_PI;
                float r0 = fr, r1 = fr - lw;
                float x0o = ccx + cosf(t0) * r0, y0o = ccy - sinf(t0) * r0;
                float x1o = ccx + cosf(t1) * r0, y1o = ccy - sinf(t1) * r0;
                float x0i = ccx + cosf(t0) * r1, y0i = ccy - sinf(t0) * r1;
                float x1i = ccx + cosf(t1) * r1, y1i = ccy - sinf(t1) * r1;
                drawTriangle(x0o, y0o, x1o, y1o, x0i, y0i, r, g, b, a);
                drawTriangle(x1o, y1o, x1i, y1i, x0i, y0i, r, g, b, a);
            }
        }
    };
    // Soft drop shadow of the WHOLE frame group first (panel-down direction so a
    // rotated panel does not flip it), then the crisp frame on top. Two faint
    // offset passes approximate the web's blur-3 soft shadow.
    strokeU(so[0] * 1.6f, so[1] * 1.6f, 0.0f, 0.0f, 0.0f, 0.22f * fadeMul);
    strokeU(so[0],        so[1],        0.0f, 0.0f, 0.0f, 0.40f * fadeMul);

    // Inner soft glow following the U interior (web glowSteps inset 1..5, alpha
    // 0.26/0.19/0.13/0.07/0.04). Thin 1px insets - a faint highlight, not a bevel.
    {
        static const float kGlow[5] = {0.26f, 0.19f, 0.13f, 0.07f, 0.04f};
        for (int gi = 0; gi < 5; gi++) {
            float ins = (float)(gi + 1) * lw;
            if (ins > (dyB - dyT) * 0.5f) break;
            drawQuad(dxL + fr, dyT + ins, (dxR - dxL) - fr, lw, 1.0f, 1.0f, 1.0f, kGlow[gi] * fadeMul);
            drawQuad(dxL + fr, dyB - ins - lw, (dxR - dxL) - fr, lw, 1.0f, 1.0f, 1.0f, kGlow[gi] * fadeMul);
            drawQuad(dxL + ins, dyT + fr, lw, (dyB - dyT) - 2.0f * fr, 1.0f, 1.0f, 1.0f, kGlow[gi] * fadeMul);
        }
    }

    // Single thin border line (web ~90/255). No second offset copy, so no bevel.
    strokeU(0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.35f * fadeMul);

    // ---- analog face (drawn dark-shadow then crisp) ----
    float iconCX = cx(ps3::CLOCK_ICON_CX);
    float iconCY = (dyT + dyB) * 0.5f;
    float R = ps3::devS(ps3::CLOCK_ICON_R);
    float ringW = fmaxf(1.5f, R * 0.16f);
    float handW = fmaxf(2.0f, R * 0.25f);
    float hourAng = -(float)M_PI / 2.0f + (((lt.tm_hour % 12) + lt.tm_min / 60.0f) / 12.0f) * 2.0f * (float)M_PI;
    float minAng  = -(float)M_PI / 2.0f + (lt.tm_min / 60.0f) * 2.0f * (float)M_PI;
    auto face = [&](float ox, float oy, float r, float g, float b, float a) {
        const int SEG = 28;
        for (int i = 0; i < SEG; i++) {
            float a0 = (float)i / SEG * 2.0f * (float)M_PI, a1 = (float)(i + 1) / SEG * 2.0f * (float)M_PI;
            float ro = R, ri = R - ringW;
            float x0o = iconCX + cosf(a0) * ro + ox, y0o = iconCY + sinf(a0) * ro + oy;
            float x1o = iconCX + cosf(a1) * ro + ox, y1o = iconCY + sinf(a1) * ro + oy;
            float x0i = iconCX + cosf(a0) * ri + ox, y0i = iconCY + sinf(a0) * ri + oy;
            float x1i = iconCX + cosf(a1) * ri + ox, y1i = iconCY + sinf(a1) * ri + oy;
            drawTriangle(x0o, y0o, x1o, y1o, x0i, y0i, r, g, b, a);
            drawTriangle(x1o, y1o, x1i, y1i, x0i, y0i, r, g, b, a);
        }
        auto hand = [&](float ang, float len) {
            float ex = iconCX + cosf(ang) * len + ox, ey = iconCY + sinf(ang) * len + oy;
            float bx = iconCX + ox, by = iconCY + oy;
            float px = -sinf(ang) * handW * 0.5f, py = cosf(ang) * handW * 0.5f;
            drawTriangle(bx + px, by + py, bx - px, by - py, ex + px, ey + py, r, g, b, a);
            drawTriangle(ex + px, ey + py, ex - px, ey - py, bx - px, by - py, r, g, b, a);
        };
        hand(hourAng, R * 0.47f);
        hand(minAng, R * 0.69f);
    };
    face(so[0], so[1], 0.0f, 0.0f, 0.0f, 0.55f * fadeMul);      // drop shadow (panel-down)
    face(0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.96f * fadeMul);        // crisp

    // ---- status row (battery / Wi-Fi / Bluetooth), left-anchored, each at the
    // CLOCK-ICON height (2R) and vertically centred like the face, with uniform
    // margins; drawn shadow-then-crisp (panel-down) for legibility. ----
    const float iconH = 2.0f * R;                  // == the analog-face height
    const float margin = ps3::devS(8.0f);          // uniform gap (matches the face)
    float cyc = (dyT + dyB) * 0.5f;                // bar vertical centre
    float lx = dxL + ps3::devS(14.0f);             // inside the rounded left edge
    {
        WifiLevel wl; int wb; BtLevel bl;
        { std::lock_guard<std::mutex> lk(mNetStateMutex); wl = mWifiLevel; wb = mWifiBars; bl = mBtLevel; }

        // Battery glyph + % (framework value only; hidden until the HAL reports).
        if (mBatteryPercent >= 0) {
            int pct = mBatteryPercent; if (pct < 0) pct = 0; if (pct > 100) pct = 100;
            float br, bg, bb;
            if (mBatteryCharging) { br = 0.35f; bg = 0.92f; bb = 0.45f; }
            else if (pct <= 15)   { br = 0.95f; bg = 0.30f; bb = 0.30f; }
            else if (pct <= 30)   { br = 0.97f; bg = 0.78f; bb = 0.20f; }
            else                  { br = 1.00f; bg = 1.00f; bb = 1.00f; }
            float bodyH = iconH * 0.60f, bodyW = bodyH * 1.9f;
            float capW = bodyH * 0.20f, capH = bodyH * 0.5f;
            float border = fmaxf(1.0f, ps3::devS(1.4f));
            float byy = cyc - bodyH * 0.5f;
            auto rail = [&](float qx, float qy, float qw, float qh) {
                drawQuad(qx + so[0], qy + so[1], qw, qh, 0.0f, 0.0f, 0.0f, 0.5f * fadeMul);
                drawQuad(qx, qy, qw, qh, br, bg, bb, 0.96f * fadeMul);
            };
            rail(lx, byy, bodyW, border);
            rail(lx, byy + bodyH - border, bodyW, border);
            rail(lx, byy, border, bodyH);
            rail(lx + bodyW - border, byy, border, bodyH);
            float ip = fmaxf(1.0f, ps3::devS(1.4f));
            drawQuad(lx + ip, byy + ip, (bodyW - 2.0f * ip) * ((float)pct / 100.0f),
                     bodyH - 2.0f * ip, br, bg, bb, fadeMul);
            rail(lx + bodyW, cyc - capH * 0.5f, capW, capH);
            lx += bodyW + capW + ps3::devS(4.0f);
            char pctTxt[12]; snprintf(pctTxt, sizeof(pctTxt), "%d%%", pct);
            float ps = ps3::fontScale(ps3::CLOCK_SIZE * 0.82f);
            float pty = ps3::baselineToTopY(cyc + ps3::devS(6.0f), ps);
            drawText(pctTxt, lx + so[0], pty + so[1], ps, 0.0f, 0.0f, 0.0f, 0.55f * fadeMul);
            drawText(pctTxt, lx, pty, ps, br, bg, bb, 0.96f * fadeMul);
            lx += measureText(pctTxt, ps) + margin;
        }

        // Wi-Fi at the clock-icon height.
        {
            float wifiSf = iconH / 18.0f, wifiW = 22.0f * wifiSf;
            int bars = (wl == kWifiLevel_Connected) ? wb : 0;
            float wa = (wl == kWifiLevel_Off || wl == kWifiLevel_Unknown) ? 0.32f : 0.96f;
            drawWifiIcon(lx + so[0], cyc - iconH * 0.5f + so[1], wifiSf, bars, 0.0f, 0.0f, 0.0f, wa * 0.6f * fadeMul);
            drawWifiIcon(lx, cyc - iconH * 0.5f, wifiSf, bars, 1.0f, 1.0f, 1.0f, wa * fadeMul);
            lx += wifiW + margin;
        }

        // Bluetooth at the clock-icon height (large = crisp glyph).
        {
            float btSf = iconH / 20.0f, btW = 14.0f * btSf;
            float bta = (bl == kBtLevel_Off || bl == kBtLevel_Unknown) ? 0.32f
                      : (bl == kBtLevel_Connected ? 0.96f : 0.72f);
            drawBtIcon(lx + so[0], cyc - iconH * 0.5f + so[1], btSf, 0.0f, 0.0f, 0.0f, bta * 0.6f * fadeMul);
            drawBtIcon(lx, cyc - iconH * 0.5f, btSf, 1.0f, 1.0f, 1.0f, bta * fadeMul);
            lx += btW;
        }
    }
    float statusRight = lx;

    // ---- date/time text, horizontally centred between the status row and the
    // analog face (so it sits centred in the visible clock bar). ----
    float ts = ps3::fontScale(ps3::CLOCK_SIZE);
    float tw = measureText(timeStr, ts);
    float faceLeft = iconCX - R - margin;
    float tcx = (statusRight + faceLeft) * 0.5f;
    float txx = tcx - tw * 0.5f;
    if (txx < statusRight + margin) txx = statusRight + margin;     // never overlap the status row
    float ty = ps3::baselineToTopY(iconCY + ps3::devS(7.0f), ts);
    drawText(timeStr, txx + so[0], ty + so[1], ts, 0.0f, 0.0f, 0.0f, 0.5f * fadeMul);   // shadow
    drawText(timeStr, txx, ty, ts, 1.0f, 1.0f, 1.0f, 0.92f * fadeMul);                  // crisp
}

} // namespace android
