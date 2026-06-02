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
#include "NanoMenuDrm.h"   // sDrmGlRotation / sDrmRotationDeg for ticker scissor
#include "NanoMenuUtils.h" // setLaunchRomPath for the Applications launch

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

// Drop-shadow offset: the shadow must fall toward the VISUAL bottom of the text.
// Verified on device by zoomed capture: an offset of -s puts the shadow clearly
// ABOVE the glyphs, +s puts it BELOW. So device +y maps straight to visual-down
// here (the panel rotation does not invert the menu text), and visual-down =
// out[1] = +s. The magnitude (devS at the call sites) sets how far it drops.
static inline void ps3ShadowOffset(float s, int /*w*/, int /*h*/, float out[2]) {
    out[0] = 0.0f;
    out[1] = s;
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
    loadPs3ThemeSettings();   // apply any saved Theme Settings (colour / day-night)
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
    for (size_t i = 0; i < mXmbRecent.size(); i++) {
        Ps3Item it;
        it.label = mXmbRecent[i].displayName;
        it.kind = PS3_RECENT; it.a = (int)i;
        // Show the game's CONSOLE icon (matched by system shortname / ROM dir to
        // the same icon the Game category uses) instead of a generic clock, and
        // drop the right-aligned system-name label entirely.
        int iconIdx = 15;   // fallback: generic recent icon
        for (size_t s = 0; s < mXmbSystems.size(); s++) {
            if (mXmbSystems[s].shortname == mXmbRecent[i].systemName
                || (!mXmbRecent[i].romDir.empty() && mXmbSystems[s].romDir == mXmbRecent[i].romDir)) {
                iconIdx = (s < 15) ? (int)s : 16;
                break;
            }
        }
        it.iconTex = mIconTextures[iconIdx];
        it.nmapTex = bevelForIconIdx(iconIdx);
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
    mPs3AnimItem = 0.0f; mPs3ItemAnimStart = -1.0f;
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
// Snappy, front-loaded easeOutCubic - the web uses this for category scrolling
// AND the submenu collapse (submenuAnim.ease = easeOutCubic).
static inline float easeOutCubic(float t) {
    if (t < 0.0f) t = 0.0f; if (t > 1.0f) t = 1.0f;
    float u = 1.0f - t;
    return 1.0f - u * u * u;
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
    if (mPs3DlgActive) { closePs3Dialog(false); return; }   // cancel/back
    if (!mPs3Stack.empty()) { ps3XmbBack(); return; }
    if (mPs3Cats.empty() || mPs3CatIdx <= 0) return;
    float live = ps3CatOffset(mPs3CatAnimActive, mPs3CatT, mPs3CatFromOffset);
    mPs3CatOldIdx = mPs3CatIdx; mPs3CatOldSel = mPs3ItemIdx;
    mPs3CatItemSel[mPs3CatIdx] = mPs3ItemIdx;
    mPs3CatIdx--;
    mPs3ItemIdx = mPs3CatItemSel[mPs3CatIdx];
    mPs3AnimItem = (float)mPs3ItemIdx; mPs3ItemAnimStart = -1.0f;  // snap; no cross-list anim
    mPs3CatFromOffset = live - ps3::CAT_SPACING;
    mPs3CatT = 0.0f; mPs3CatAnimActive = true;
}

void NanoMenu::ps3XmbRight() {
    if (mPs3DlgActive) return;   // consume; the chooser uses up/down + X/O
    if (!mPs3Stack.empty()) { ps3XmbSelect(); return; }
    if (mPs3Cats.empty() || mPs3CatIdx >= (int)mPs3Cats.size() - 1) return;
    float live = ps3CatOffset(mPs3CatAnimActive, mPs3CatT, mPs3CatFromOffset);
    mPs3CatOldIdx = mPs3CatIdx; mPs3CatOldSel = mPs3ItemIdx;
    mPs3CatItemSel[mPs3CatIdx] = mPs3ItemIdx;
    mPs3CatIdx++;
    mPs3ItemIdx = mPs3CatItemSel[mPs3CatIdx];
    mPs3AnimItem = (float)mPs3ItemIdx; mPs3ItemAnimStart = -1.0f;
    mPs3CatFromOffset = live + ps3::CAT_SPACING;
    mPs3CatT = 0.0f; mPs3CatAnimActive = true;
}

void NanoMenu::ps3XmbUp() {
    if (mPs3DlgActive) {
        if (mPs3DlgSel > 0) { mPs3DlgSel--; previewThemeSetting(mPs3DlgThemeKey, mPs3DlgSel); }
        return;
    }
    int& s = ps3CurSel();
    if (s > 0) { mPs3ItemAnimFrom = mPs3AnimItem; mPs3ItemAnimStart = mEffectTime; s--; }
}
void NanoMenu::ps3XmbDown() {
    if (mPs3DlgActive) {
        if (mPs3DlgSel < (int)mPs3DlgOptions.size() - 1) { mPs3DlgSel++; previewThemeSetting(mPs3DlgThemeKey, mPs3DlgSel); }
        return;
    }
    int& s = ps3CurSel(); int n = (int)ps3CurItems().size();
    if (s < n - 1) { mPs3ItemAnimFrom = mPs3AnimItem; mPs3ItemAnimStart = mEffectTime; s++; }
}

void NanoMenu::ps3XmbSelect() {
    if (mPs3DlgActive) { closePs3Dialog(mPs3DlgThemeKey > 0); return; }   // X: apply chooser / dismiss message
    std::vector<Ps3Item>& items = ps3CurItems();
    int sel = ps3CurSel();
    if (sel < 0 || sel >= (int)items.size()) return;
    Ps3Item it = items[sel];
    // Snapshot the parent (breadcrumb) list + entered index BEFORE any push, so
    // the collapse animation renders the parent layer continuously (web parity:
    // submenuAnim.parentItems / parentIdx).
    std::vector<Ps3Item> parentSnap = items;
    int parentSelSnap = sel;
    size_t depthBefore = mPs3Stack.size();
    switch (it.kind) {
        case PS3_SYSTEM:       { Ps3Level lvl; buildRomSubmenu(it.a, lvl);     mPs3Stack.push_back(lvl); break; }
        case PS3_RECENT_LIST:  { Ps3Level lvl; buildRecentSubmenu(lvl);        mPs3Stack.push_back(lvl); break; }
        case PS3_APP_LIST:     { Ps3Level lvl; buildAppSubmenu(lvl);           mPs3Stack.push_back(lvl); break; }
        case PS3_DATA_SUBMENU: { Ps3Level lvl; buildDataSubmenu(it.data, lvl); mPs3Stack.push_back(lvl); break; }
        case PS3_ROM:    { mXmbSystemIndex = it.a; mXmbGameIndex = it.b; mSearchActive = false; launchXmbGame(); return; }
        case PS3_RECENT: { mXmbSystemIndex = -1;  mXmbGameIndex = it.a; mSearchActive = false; launchXmbGame(); return; }
        case PS3_SETTING:{ if (it.a == 0) openWifiScreen(); else if (it.a == 1) openBtScreen(); return; }
        case PS3_APP:
        case PS3_LAUNCH_PKG: {
            // Launch an installed app / package (Applications submenu). Mirrors the
            // carousel's app-launch handshake (NanoMenuInput.cpp): set launch_app to
            // the package, clear ROM/core + stale QR priming, flag return-to-apps,
            // then wait for the select-key release before the nano exits so the
            // launched app does not see a phantom press.
            if (it.payloadStr.empty()) return;
            if (!isLaunchReady()) { showLaunchBusyToast(); return; }
            ALOGI("ps3menu: launching app %s", it.payloadStr.c_str());
            property_set("sys.gammaos.nano.launch_app", it.payloadStr.c_str());
            property_set("sys.gammaos.nano.launched_pkg", it.payloadStr.c_str());
            setLaunchRomPath("");
            property_set("sys.gammaos.nano.launch_core", "");
            property_set("persist.gammaos.nano.qr_prepared", "0");
            property_set("persist.gammaos.nano.qr_core", "");
            property_set("sys.gammaos.nano.return_apps", "1");
            property_set("service.bootanim.nano_retroarch", "1");
            property_set("sys.gammaos.nano.drop_input", "1");
            mWaitForRelease = true;
            return;
        }
        case PS3_DATA_LEAF: { if (it.action == 1) openPs3Dialog(it); return; }   // action='dialog' -> dialog/chooser
        default: return;
    }
    if (mPs3Stack.size() > depthBefore) {
        // A submenu was pushed: collapse the parent into the breadcrumb column
        // and slide the children in (timed 250ms easeOutCubic, dir +1).
        mPs3SubParentItems = std::move(parentSnap);
        mPs3SubParentIdx   = parentSelSnap;
        mPs3SubChildItems  = mPs3Stack.back().items;
        mPs3SubDir         = 1;
        mPs3SubAnimStart   = mEffectTime;
        mPs3SubAnim        = 0.0f;
        mPs3AnimItem       = 0.0f;
        mPs3ItemAnimStart  = -1.0f;   // snap child selection to 0
    }
}

void NanoMenu::ps3XmbBack() {
    if (mPs3DlgActive) { closePs3Dialog(false); return; }   // O: cancel the dialog/chooser
    if (!mPs3Stack.empty()) {
        // Snapshot the child list (being left) for the slide-out, then pop and
        // expand the parent back out of the breadcrumb column (timed, dir -1).
        mPs3SubChildItems  = mPs3Stack.back().items;
        mPs3Stack.pop_back();
        mPs3SubParentItems = ps3CurItems();     // restored parent list
        mPs3SubParentIdx   = ps3CurSel();
        mPs3SubDir         = -1;
        mPs3SubAnimStart   = mEffectTime;
        mPs3SubAnim        = 1.0f;
        mPs3AnimItem       = (float)ps3CurSel();
        mPs3ItemAnimStart  = -1.0f;
    }
}

// ---------------------------------------------------------------------------
// render
// ---------------------------------------------------------------------------
void NanoMenu::renderPs3Xmb() {
    if (!mPs3MenuBuilt) initPs3Menu();
    // Cold-boot intro: advance the boot clock once per frame. While it suppresses
    // the UI, draw only the boot overlay (wave/gradient reveal from black, logo,
    // epilepsy warning) over the composited background and skip the menu. Once the
    // staged reveal window opens it falls through and the menu draws with the
    // pop-in multipliers (mPs3BootLabelReveal / mPs3BootIconReveal).
    if (mPs3BootActive) {
        if (ps3BootUpdate(mFrameDt)) { renderPs3BootOverlay(); return; }
    }
    if (mPs3Cats.empty()) return;
    if (mMenuState == MENU_WIFI) { renderWifiScreen(); return; }
    if (mMenuState == MENU_BT)   { renderBtScreen();   return; }

    // Opt-in slow-frame diagnostic: `setprop persist.gammaos.nano.ps3xmb.fpslog 1`
    // logs any frame slower than ~22ms (<45fps) with context, so transition dips
    // can be measured from logcat. Off by default (read once).
    static bool sFpsLog = property_get_bool("persist.gammaos.nano.ps3xmb.fpslog", false);
    if (sFpsLog && mFrameDt > 0.022f)
        ALOGW("ps3 slow frame %.1fms inSub=%d subAnim=%d",
              mFrameDt * 1000.0f, (int)!mPs3Stack.empty(), (int)(mPs3SubAnimStart >= 0.0f));

    { ps3::LayoutParams lp; lp.panelW = mWidth; lp.panelH = mHeight; lp.uiScale = mPs3UiScale;
      ps3::layoutCompute(lp); }

    // Panel-down drop-shadow offset for the menu icons + text (rotation-aware, so
    // the 180-degree Brick panel does not flip it). Web: icon shadow offsetY ~1-2.
    float so[2]; ps3ShadowOffset(ps3::devS(4.5f), mWidth, mHeight, so);   // stronger panel-down drop shadow (light-bg readability)

    float dt = mFrameDt; if (dt < 0.0f) dt = 0.0f; if (dt > 0.1f) dt = 0.1f;

    // category slide (timed) + continuous item tracker (smooth, accelerates on hold)
    if (mPs3CatAnimActive) {
        mPs3CatT += dt / (ps3::CAT_ANIM_MS / 1000.0f);
        if (mPs3CatT >= 1.0f) { mPs3CatT = 1.0f; mPs3CatAnimActive = false; }
    }
    // Item scroll: per-step easeOutCubic over ITEM_ANIM_MS from the position the
    // step started at to the current selection (the web's itemAnim). Combined
    // with the accelerating auto-repeat this gives a smooth, snappy, continuously
    // accelerating hold-scroll. mPs3ItemAnimStart < 0 means "snap" (list change).
    {
        float target = (float)ps3CurSel();
        float dur = ps3::ITEM_ANIM_MS / 1000.0f;   // 200ms easeOutCubic, exactly the web's itemAnim
        float el = mEffectTime - mPs3ItemAnimStart;
        if (mPs3ItemAnimStart < 0.0f || el >= dur || dur <= 0.0f) {
            mPs3AnimItem = target;
        } else {
            float t = el / dur;
            float e = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);   // easeOutCubic
            mPs3AnimItem = mPs3ItemAnimFrom + (target - mPs3ItemAnimFrom) * e;
        }
    }
    // Submenu collapse factor: timed 250ms easeOutCubic (the web submenuAnim).
    // subT 0 = full parent list at the item column; 1 = settled breadcrumb +
    // faded sibling column with the children slid in. dir +1 enter, -1 exit.
    bool inSub = !mPs3Stack.empty();
    bool subAnimating = (mPs3SubAnimStart >= 0.0f);
    float subT;
    if (subAnimating) {
        float p = (mEffectTime - mPs3SubAnimStart) / (ps3::SUBMENU_ANIM_MS / 1000.0f);
        if (p >= 1.0f) { p = 1.0f; mPs3SubAnimStart = -1.0f; subAnimating = false; }
        float sp = easeOutCubic(p);
        subT = (mPs3SubDir == 1) ? sp : (1.0f - sp);
        if (!subAnimating) mPs3SubDir = 0;
    } else {
        subT = inSub ? 1.0f : 0.0f;
    }
    mPs3SubAnim = subT;   // keep the legacy field synced (category bar reads subT)

    // Submenu depth-of-field: the real XMB blurs the background + wave as you
    // descend into a submenu and sharpens it on exit (web submenuBlur, ramped
    // with the collapse, held while settled). The wave/gradient/particles are
    // already composited into the framebuffer at this point, so capture + blur
    // it and crossfade the blurred copy in at the collapse factor; the menu then
    // draws sharp on top. The capture+blur chain (full-FB copy + downsample
    // pyramid + separable gaussian) is expensive, so only recapture while the
    // backdrop is actually moving (transition / category slide / item scroll),
    // on the first settled frame, or on a ~10Hz cadence; otherwise reuse the
    // cached blur. tintA MUST be > 0 or the panel composites to nothing.
    if (subT > 0.004f) {
        // Keep the wave/gradient ANIMATING behind the frosted backdrop, but
        // sample it at only ~30Hz so the menu itself stays locked at 60fps. The
        // blur reads ps3bg::workTex (the already-rendered scene) instead of a
        // full framebuffer capture, so each sample costs no mid-frame tile flush
        // and fits the 60fps budget; between samples the cached blur is reused
        // and drawn every frame. The blur is in LOGICAL orientation, so draw it
        // waveSpace=true. tintA MUST be > 0 or the panel composites to nothing.
        bool due = !mPs3GlassValid || (mEffectTime - mPs3GlassBlurT) >= 0.0667f;   // ~15Hz backdrop sample
        if (due && captureGlassFromWave()) { mPs3GlassValid = true; mPs3GlassBlurT = mEffectTime; }
        if (mPs3GlassValid)
            drawFrostedGlass(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f,
                             0.62f, 0.62f, 0.70f, 1.0f, subT, /*waveSpace=*/true);
    } else {
        mPs3GlassValid = false;
    }

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
        // Cold-boot icon pop-in: staggered alpha fade + a small rise from below
        // (active category leads, each step out trails by 0.18). No effect once
        // the intro is over (mPs3BootIconReveal == 1).
        float catRise = 0.0f;
        if (mPs3BootIconReveal < 0.999f) {
            float stagger = fminf(1.0f, fabsf((float)(i - mPs3CatIdx)) * 0.18f);
            float ir = (mPs3BootIconReveal - stagger) / fmaxf(0.001f, 1.0f - stagger);
            if (ir < 0.0f) ir = 0.0f; if (ir > 1.0f) ir = 1.0f;
            ir = ir * ir * (3.0f - 2.0f * ir);
            alpha *= ir;
            catRise = (1.0f - ir) * ps3::devS(14.0f);
        }
        float dsz = ps3::devS(sz);
        float ix = ps3::devX(ps3::XCL(x, sz * 0.5f)), iy = ps3::devY(y - sz * 0.5f) + catRise;
        // Category icon drop shadow (panel-down), then the glass/flat icon.
        if (mPs3Cats[i].iconTex)
            drawIconTex(mPs3Cats[i].iconTex, ix + so[0], iy + so[1], dsz, dsz, 0.0f, 0.0f, 0.0f, 0.42f * alpha);
        if (mIconGlassReady && mPs3Cats[i].nmapTex && ps3bg::workTex())
            drawGlassIcon(mPs3Cats[i].nmapTex, ix, iy, dsz, dsz, 1.0f, 1.0f, 1.0f, alpha);
        else
            drawIconTex(mPs3Cats[i].iconTex, ix, iy, dsz, dsz, 1.0f, 1.0f, 1.0f, alpha);
        if (isActive) {
            float la = 0.9f * (1.0f - 0.55f * subT) * mPs3BootLabelReveal;
            float ls = ps3::fontScale(ps3::CAT_LABEL_SIZE);
            const char* nm = mPs3Cats[i].name.c_str();
            float lw = measureText(nm, ls);
            float lx = ps3::devX(ps3::XCP(x)) - lw * 0.5f;
            float ly = ps3::baselineToTopY(ps3::devY(ps3::CAT_LABEL_Y), ls);
            drawText(nm, lx + so[0], ly + so[1], ls, 0.0f, 0.0f, 0.0f, 0.78f * la);   // shadow
            drawText(nm, lx, ly, ls, 0.88f, 0.82f, 0.92f, la);
        }
    }

    // ---- item-list renderer (continuous selection position selPos) ----
    float fTop = ps3::frameTopV();
    float fBot = fTop + ps3::frameHV();
    // Content icons sourced from RetroArch / the launcher (console systems, ROM
    // / game art, app icons) render larger than the PS3-style data icons in the
    // same box, so scale just those down (RETRO_ICON_SCALE).
    auto isRetroIcon = [](int k) {
        return k == PS3_SYSTEM || k == PS3_ROM || k == PS3_RECENT
            || k == PS3_APP || k == PS3_LAUNCH_PKG;
    };
    // Active-item description: greedy word-wrap to <=3 lines below the label,
    // left-aligned at the label column, using the full width to the panel edge.
    // Mirrors the web (drawWrappedText maxLines 3); 3 lines because the enlarged
    // subtitle needs them to show the full blurb.
    auto drawDesc = [&](const std::string& text, float txDev, float labelBaselineYDev, float alpha) {
        if (text.empty() || alpha <= 0.02f) return;
        float ds = ps3::fontScale(ps3::ITEM_DESC_SIZE);
        // Wrap width = visible frame edge minus the label x, clamped to the panel
        // (the uiScale zoom can push XCF(VW) off-screen on narrow panels).
        float rightEdge = ps3::devX(ps3::XCF(ps3::VW - ps3::ITEM_VALUE_RIGHT_PAD));
        float panelEdge = (float)mWidth - ps3::devS(ps3::ITEM_VALUE_RIGHT_PAD);
        if (rightEdge > panelEdge) rightEdge = panelEdge;
        float maxW = rightEdge - txDev;
        if (maxW < 40.0f) maxW = 40.0f;
        const int kMaxLines = 3;
        std::string lines[3]; int nLines = 0; std::string cur, word;
        auto commit = [&]() {
            if (word.empty()) return;
            std::string trial = cur.empty() ? word : cur + " " + word;
            if (!cur.empty() && measureText(trial.c_str(), ds) > maxW) {
                if (nLines < kMaxLines) lines[nLines++] = cur;
                cur = word;
            } else cur = trial;
            word.clear();
        };
        for (const char* p = text.c_str(); ; ++p) {
            if (*p == ' ' || *p == '\0') { commit(); if (*p == '\0') break; }
            else word.push_back(*p);
        }
        if (!cur.empty() && nLines < kMaxLines) lines[nLines++] = cur;
        float lineH = ps3::devS(ps3::ITEM_DESC_SIZE * 1.18f);
        float y0 = labelBaselineYDev + ps3::devS(ps3::ITEM_DESC_OFFSET);
        for (int li = 0; li < nLines; li++) {
            float ly = ps3::baselineToTopY(y0 + (float)li * lineH, ds);
            drawText(lines[li].c_str(), txDev + so[0], ly + so[1], ds, 0.0f, 0.0f, 0.0f, 0.7f * alpha);
            drawText(lines[li].c_str(), txDev, ly, ds, 0.78f, 0.78f, 0.82f, alpha);
        }
    };
    auto drawList = [&](std::vector<Ps3Item>& items, float selPos, float xShiftV, float alphaMul) {
        if (items.empty() || alphaMul <= 0.01f) return;
        int activeIdx = (int)lroundf(selPos);
        for (int i = 0; i < (int)items.size(); i++) {
            bool isActive = (i == activeIdx);
            float y = itemSlotYf(i, selPos);
            if (y < fTop - 90.0f || y > fBot + 90.0f) continue;
            // mPs3BootIconReveal fades the item list in during the cold-boot
            // hand-off (1.0 = no effect once the intro is over).
            float alpha = (isActive ? ps3::ALPHA_FOCUS : ps3::ALPHA_INACTIVE) * alphaMul * mPs3BootIconReveal;
            if (y < fTop + 40.0f) alpha *= fmaxf(0.0f, (y - fTop) / 40.0f);
            if (y > fBot - 20.0f) alpha *= fmaxf(0.0f, (fBot - y) / 20.0f);
            if (alpha <= 0.01f) continue;
            const Ps3Item& it = items[i];
            float baseIsz = isRetroIcon(it.kind) ? ps3::ITEM_ICON_SIZE * ps3::RETRO_ICON_SCALE
                                                 : ps3::ITEM_ICON_SIZE;
            float isz = isActive ? (baseIsz * 1.18f) : baseIsz;
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
            // Value position first (right-anchored, panel-clamped) so the label
            // knows how far right it may extend before it must ticker-scroll.
            std::string itVal = resolvePs3ItemValue(it);
            bool hasVal = !itVal.empty();
            float vs = 0.0f, vw = 0.0f, vx = 0.0f;
            if (hasVal) {
                vs = ps3::fontScale(ps3::ITEM_TEXT_SIZE);
                vw = measureText(itVal.c_str(), vs);
                float vRight = ps3::devX(ps3::XCF(ps3::VW - ps3::ITEM_VALUE_RIGHT_PAD));
                float vPanelMax = (float)mWidth - ps3::devS(ps3::ITEM_VALUE_RIGHT_PAD);
                if (vRight > vPanelMax) vRight = vPanelMax;
                vx = vRight - vw;
            }
            float labelRight = hasVal ? (vx - ps3::devS(14.0f))
                                      : ((float)mWidth - ps3::devS(ps3::ITEM_VALUE_RIGHT_PAD));
            float availW = labelRight - tx;
            if (availW < ps3::devS(20.0f)) availW = ps3::devS(20.0f);
            float labelW = measureText(L, ts);
            // Long ACTIVE labels ticker-scroll (ping-pong with end pauses) within
            // [tx, tx+availW] rather than overflowing into the value / off-screen.
            // A scissor (mapped to the panel's DRM rotation, full-height band so
            // only X clips) keeps the text inside its bounds.
            float lx = tx;
            bool scissorOn = false;
            if (isActive && labelW > availW + 1.0f) {
                float overflow = labelW - availW;
                float scrollT = overflow / fmaxf(1.0f, ps3::devS(70.0f));   // ~70 vpx/s
                float hold = 1.4f, period = 2.0f * (scrollT + hold);
                float ph = fmodf(mEffectTime, period);
                float off;
                if (ph < hold)                       off = 0.0f;
                else if (ph < hold + scrollT)        off = (ph - hold) / scrollT * overflow;
                else if (ph < 2.0f * hold + scrollT) off = overflow;
                else                                 off = overflow - (ph - 2.0f * hold - scrollT) / scrollT * overflow;
                lx = tx - off;
                int rlx = (int)tx, rly = 0, rlw = (int)availW, rlh = (int)mHeight;
                int sx, sy, sw, sh;
                switch (sDrmGlRotation ? sDrmRotationDeg : 0) {
                    case 90:  sx = rly; sy = mWidth - rlx - rlw; sw = rlh; sh = rlw; break;
                    case 180: sx = mWidth - rlx - rlw; sy = mHeight - rly - rlh; sw = rlw; sh = rlh; break;
                    case 270: sx = mHeight - rly - rlh; sy = rlx; sw = rlh; sh = rlw; break;
                    default:  sx = rlx; sy = rly; sw = rlw; sh = rlh; break;
                }
                glEnable(GL_SCISSOR_TEST); glScissor(sx, sy, sw, sh);
                scissorOn = true;
            }
            // Text drop shadow (panel-down) under every label for legibility.
            drawText(L, lx + so[0], ty + so[1], ts, 0.0f, 0.0f, 0.0f, 0.78f * alpha);
            if (isActive) {
                float phase = fmodf(mEffectTime, ps3::PULSE_PERIOD_MS / 1000.0f) / (ps3::PULSE_PERIOD_MS / 1000.0f);
                float s = 0.5f * (1.0f - cosf(phase * 2.0f * (float)M_PI));
                float outerA = ps3::PULSE_ALPHA_MIN + (ps3::PULSE_ALPHA_MAX - ps3::PULSE_ALPHA_MIN) * s;
                float innerA = ps3::PULSE_INNER_MIN + (ps3::PULSE_INNER_MAX - ps3::PULSE_INNER_MIN) * s;
                float oR = ps3::devS(5.0f), iR = ps3::devS(2.2f);
                for (int k = 0; k < 8; k++) { float a = (float)k / 8.0f * 2.0f * (float)M_PI;
                    drawText(L, lx + cosf(a) * oR, ty + sinf(a) * oR, ts, 1.0f, 1.0f, 1.0f, outerA * alphaMul * 0.16f); }
                for (int k = 0; k < 6; k++) { float a = ((float)k + 0.5f) / 6.0f * 2.0f * (float)M_PI;
                    drawText(L, lx + cosf(a) * iR, ty + sinf(a) * iR, ts, 1.0f, 1.0f, 1.0f, innerA * alphaMul * 0.28f); }
                drawText(L, lx, ty, ts, 1.0f, 1.0f, 1.0f, alpha);
            } else {
                drawText(L, lx, ty, ts, 0.92f, 0.92f, 0.92f, alpha);
            }
            if (scissorOn) glDisable(GL_SCISSOR_TEST);
            if (isActive) {
                // Description fades in only when the active item is centred, so
                // it does not flash two descriptions while the list is scrolling.
                float descA = alpha * fmaxf(0.0f, 1.0f - 2.0f * fabsf(selPos - (float)activeIdx));
                drawDesc(it.desc, tx, ps3::devY(y), descA);
            }
            if (hasVal) {
                drawText(itVal.c_str(), vx + so[0], ty + so[1], vs, 0.0f, 0.0f, 0.0f, 0.7f * alpha);
                drawText(itVal.c_str(), vx, ty, vs, 0.7f, 0.7f, 0.75f, alpha * 0.85f);
            }
        }
    };

    // ---- breadcrumb parent layer + back-chevron ----
    // drawParentLayer: on entering a submenu the parent item list COLLAPSES -
    // the selected parent slides to the breadcrumb column (PARENT_COL_X) as a
    // normal-size cube; the siblings gather into a faded uniform column at SIB_X
    // (more faded with distance); their text fades out and travels left with the
    // icon. t: 0 = full bright list at the item column, 1 = settled breadcrumb.
    // Mirrors index.html drawParentLayer (7531-7571) exactly.
    auto drawParentLayer = [&](std::vector<Ps3Item>& pItems, int pIdx, float t) {
        if (pItems.empty()) return;
        const float srcX = ps3::ITEM_ICON_X;
        for (int i = 0; i < (int)pItems.size(); i++) {
            bool sel = (i == pIdx);
            float ySrc = itemSlotYf(i, (float)pIdx);
            float yDst = ps3::ITEM_FOCUS_Y + (float)(i - pIdx) * ps3::PARENT_SPACING;
            float y = ySrc + (yDst - ySrc) * t;
            if (y < fTop - 40.0f || y > fBot + 40.0f) continue;
            float dstX = sel ? ps3::PARENT_COL_X : ps3::SIB_X;
            float cx = srcX + (dstX - srcX) * t;
            int dist = abs(i - pIdx);
            float fullA = sel ? ps3::ALPHA_FOCUS : ps3::ALPHA_INACTIVE;
            float setA = sel ? 0.92f : fmaxf(0.12f, 0.42f - (float)(dist - 1) * 0.05f);
            float a = fullA + (setA - fullA) * t;
            if (y < 40.0f)            a *= fmaxf(0.0f, y / 40.0f);
            if (y > ps3::VH - 20.0f)  a *= fmaxf(0.0f, (ps3::VH - y) / 20.0f);
            if (a <= 0.01f) continue;
            const Ps3Item& it = pItems[i];
            float psz = isRetroIcon(it.kind) ? ps3::ITEM_ICON_SIZE * ps3::RETRO_ICON_SCALE
                                             : ps3::ITEM_ICON_SIZE;   // breadcrumb cube = normal size (no active boost)
            float dsz = ps3::devS(psz);
            float ix = ps3::devX(ps3::XCL(cx, psz * 0.5f));
            float iy = ps3::devY(y - psz * 0.5f);
            if (it.iconTex)
                drawIconTex(it.iconTex, ix + so[0], iy + so[1], dsz, dsz, 0.0f, 0.0f, 0.0f, 0.42f * a);
            // Glass (live wave refraction) only for the selected breadcrumb cube;
            // the faded sibling column uses the cheap flat icon (the per-icon
            // glass shader is costly and invisible on dim, small siblings).
            if (sel && mIconGlassReady && it.nmapTex && ps3bg::workTex())
                drawGlassIcon(it.nmapTex, ix, iy, dsz, dsz, it.iconR, it.iconG, it.iconB, a);
            else if (it.iconTex)
                drawIconTex(it.iconTex, ix, iy, dsz, dsz, it.iconR, it.iconG, it.iconB, a);
            float textA = (1.0f - t) * (sel ? ps3::ALPHA_FOCUS : ps3::ALPHA_INACTIVE);
            if (textA > 0.02f) {
                float ts = ps3::fontScale(sel ? ps3::ITEM_TEXT_ACTIVE_SIZE : ps3::ITEM_TEXT_SIZE);
                float tx = ps3::devX(ps3::XCP(ps3::ITEM_TEXT_X + (cx - srcX)));
                float ty = ps3::baselineToTopY(ps3::devY(y), ts);
                const char* L = it.label.c_str();
                drawText(L, tx + so[0], ty + so[1], ts, 0.0f, 0.0f, 0.0f, 0.78f * textA);
                float c = sel ? 1.0f : 0.92f;
                drawText(L, tx, ty, ts, c, c, c, textA);
            }
        }
    };
    // Back-chevron breadcrumb: a solid left-pointing gradient triangle drawn as
    // horizontal slices (no per-vertex-colour primitive available). tip XCP(430),
    // base XCP(462), h 30, vertical gradient rgb(206,206,208)->rgb(120,120,124).
    // Mirrors index.html drawBackChevron (7574-7591).
    auto drawBackChevron = [&](float mul) {
        if (mul <= 0.02f) return;
        float cy   = ps3::devY(ps3::ITEM_FOCUS_Y);
        float tipX = ps3::devX(ps3::XCP(430.0f));
        float baseX = ps3::devX(ps3::XCP(462.0f));
        float h = ps3::devS(30.0f);
        float a = 0.95f * mul;
        const int N = 10;
        for (int s = 0; s < N; s++) {
            float v0 = (float)s / (float)N, v1 = (float)(s + 1) / (float)N;
            float y0 = cy - h * 0.5f + v0 * h;
            float y1 = cy - h * 0.5f + v1 * h;
            float lx0 = tipX + (baseX - tipX) * fabsf(v0 - 0.5f) * 2.0f;
            float lx1 = tipX + (baseX - tipX) * fabsf(v1 - 0.5f) * 2.0f;
            float vm = (v0 + v1) * 0.5f;
            float rg = (206.0f + (120.0f - 206.0f) * vm) / 255.0f;
            float bb = (208.0f + (124.0f - 208.0f) * vm) / 255.0f;
            drawTriangle(lx0, y0, baseX, y0, baseX, y1, rg, rg, bb, a);
            drawTriangle(lx0, y0, baseX, y1, lx1, y1, rg, rg, bb, a);
        }
    };

    // ---- item list dispatch ----
    if (subAnimating) {
        // Entering / exiting a submenu: collapse the parent into the breadcrumb,
        // slide the children in from the right (SLIDE_DIST), draw the chevron.
        // On exit the stack is already popped, so children come from the snapshot.
        std::vector<Ps3Item>* childItems =
            (mPs3SubDir == -1) ? &mPs3SubChildItems
          : (!mPs3Stack.empty() ? &mPs3Stack.back().items : &mPs3SubChildItems);
        drawParentLayer(mPs3SubParentItems, mPs3SubParentIdx, subT);
        drawList(*childItems, mPs3AnimItem, ps3::SUBMENU_CHILD_X_SHIFT + ps3::SLIDE_DIST * (1.0f - subT), subT);
        drawBackChevron(subT);
    } else if (inSub) {
        // Settled in a submenu: derive the parent (one level up) from the LIVE
        // stack so a multi-level breadcrumb stays correct, draw it collapsed,
        // the children at the normal column, and the chevron.
        int depth = (int)mPs3Stack.size();
        std::vector<Ps3Item>* parentItems = nullptr; int parentIdx = 0;
        if (depth >= 2) { parentItems = &mPs3Stack[depth - 2].items; parentIdx = mPs3Stack[depth - 2].sel; }
        else if (mPs3CatIdx >= 0 && mPs3CatIdx < (int)mPs3Cats.size()) {
            parentItems = &mPs3Cats[mPs3CatIdx].items; parentIdx = mPs3ItemIdx;
        }
        if (parentItems) drawParentLayer(*parentItems, parentIdx, 1.0f);
        drawList(mPs3Stack.back().items, mPs3AnimItem, ps3::SUBMENU_CHILD_X_SHIFT, 1.0f);
        drawBackChevron(1.0f);
    } else if (mPs3CatAnimActive) {
        // Top-level category slide rail + fade-crossfade.
        float p = easeSmooth(mPs3CatT);
        float barTravel = mPs3CatFromOffset;
        float oldAlpha = fmaxf(0.0f, 1.0f - p / 0.4f);
        float newAlpha = fmaxf(0.0f, (p - 0.5f) / 0.5f);
        if (mPs3CatOldIdx >= 0 && mPs3CatOldIdx < (int)mPs3Cats.size())
            drawList(mPs3Cats[mPs3CatOldIdx].items, (float)mPs3CatOldSel, -barTravel * p, oldAlpha);
        drawList(mPs3Cats[mPs3CatIdx].items, mPs3AnimItem, barTravel * (1.0f - p), newAlpha);
    } else {
        drawList(ps3CurItems(), mPs3AnimItem, 0.0f, 1.0f);
    }

    drawPs3Clock(mPs3BootIconReveal);   // fades in with the cold-boot hand-off (1.0 otherwise)

    // Settings dialog / Theme chooser overlay on top of the menu.
    if (mPs3DlgActive) renderPs3Dialog();
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
    float so[2]; ps3ShadowOffset(ps3::devS(3.0f), mWidth, mHeight, so);   // panel-down drop shadow (stronger)

    // filled dim panel (open-right, rounded left corners)
    auto fillURect = [&](float x0, float y0, float x1, float y1, float rad, float r, float g, float b, float a) {
        if (rad < 1.0f) { drawQuad(x0, y0, x1 - x0, y1 - y0, r, g, b, a); return; }
        drawQuad(x0 + rad, y0, (x1 - x0) - rad, y1 - y0, r, g, b, a);     // main body
        drawQuad(x0, y0 + rad, rad, (y1 - y0) - 2.0f * rad, r, g, b, a);  // left strip
        // top-left (arc 90deg..180deg) + bottom-left (180deg..270deg) corner fans.
        const int CSEG = 16;
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
    // The dim panel wash (web: rgb(0,0,0) at 0.18, NO shadow under it). We do
    // NOT draw a filled drop-shadow silhouette here: it covered the box interior
    // and stacked with this fill (making the bar ~2x too dark) and poked dark
    // pixels at the rounded corners. The drop-shadow feel comes from the per-
    // element (text / face / status) panel-down shadows below.
    fillURect(dxL, dyT, dxR, dyB, fr, 0.0f, 0.0f, 0.0f, 0.18f * fadeMul);

    // border outline (open-right): top + bottom + left lines + the two rounded corners.
    auto strokeU = [&](float ox, float oy, float r, float g, float b, float a) {
        drawQuad(dxL + fr + ox, dyT + oy, (dxR - dxL) - fr, lw, r, g, b, a);             // top
        drawQuad(dxL + fr + ox, dyB - lw + oy, (dxR - dxL) - fr, lw, r, g, b, a);        // bottom
        drawQuad(dxL + ox, dyT + fr + oy, lw, (dyB - dyT) - 2.0f * fr, r, g, b, a);      // left
        const int CSEG = 16;
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
    // (The panel drop shadow is the filled silhouette drawn above; the border
    // itself needs no separate offset stroke - that produced the dark corner
    // pixels. Just the inner glow + the crisp border line follow.)

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

    // ---- bar layout: status icons [battery][Wi-Fi][BT] anchored to the LEFT of
    // the bar; the date/time + analog face on the RIGHT. All at the clock-icon
    // height (2R), vertically centred, drawn shadow-then-crisp for legibility. ----
    const float iconH = 2.0f * R;                  // == the analog-face height
    const float margin = ps3::devS(9.0f);          // uniform gap (matches the face)
    float cyc = (dyT + dyB) * 0.5f;                // bar vertical centre
    float baseY = iconCY + ps3::devS(9.0f);        // text baseline -> visually centred

    // date/time: right edge just left of the face, vertically centred.
    float ts = ps3::fontScale(ps3::CLOCK_SIZE);
    float tw = measureText(timeStr, ts);
    float txx = (iconCX - R - margin) - tw;
    float ty  = ps3::baselineToTopY(baseY, ts);
    drawText(timeStr, txx + so[0], ty + so[1], ts, 0.0f, 0.0f, 0.0f, 0.5f * fadeMul);   // shadow
    drawText(timeStr, txx, ty, ts, 1.0f, 1.0f, 1.0f, 0.92f * fadeMul);                  // crisp

    {
        WifiLevel wl; int wb; BtLevel bl;
        { std::lock_guard<std::mutex> lk(mNetStateMutex); wl = mWifiLevel; wb = mWifiBars; bl = mBtLevel; }
        float lx = dxL + ps3::devS(16.0f);          // start from the LEFT of the bar

        // Battery glyph + % (leftmost), framework value only; hidden until reported.
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
            lx += bodyW + capW + ps3::devS(5.0f);
            char pctTxt[12]; snprintf(pctTxt, sizeof(pctTxt), "%d%%", pct);
            float ps = ps3::fontScale(ps3::CLOCK_SIZE * 0.82f);
            float pty = ps3::baselineToTopY(baseY, ps);
            drawText(pctTxt, lx + so[0], pty + so[1], ps, 0.0f, 0.0f, 0.0f, 0.55f * fadeMul);
            drawText(pctTxt, lx, pty, ps, br, bg, bb, 0.96f * fadeMul);
            lx += measureText(pctTxt, ps) + margin;
        }
        // Reposition the Wi-Fi + Bluetooth pair so it sits BETWEEN the battery
        // (left) and the date/time text (right), centred in the gap, instead of
        // clustered next to the battery.
        {
            float wifiSfG = iconH / 18.0f, wifiWG = 22.0f * wifiSfG;
            float btSfG = iconH / 20.0f, btWG = 14.0f * btSfG;
            float groupW = wifiWG + margin + btWG;
            float gapL = lx;        // right edge of the battery group
            float gapR = txx;       // left edge of the date/time text
            float gs = gapL + ((gapR - gapL) - groupW) * 0.5f;
            if (gs < gapL) gs = gapL;
            lx = gs;
        }
        // Wi-Fi.
        {
            float wifiSf = iconH / 18.0f, wifiW = 22.0f * wifiSf;
            int bars = (wl == kWifiLevel_Connected) ? wb : 0;
            float wa = (wl == kWifiLevel_Off || wl == kWifiLevel_Unknown) ? 0.32f : 0.96f;
            drawWifiIcon(lx + so[0], cyc - iconH * 0.5f + so[1], wifiSf, bars, 0.0f, 0.0f, 0.0f, wa * 0.6f * fadeMul);
            drawWifiIcon(lx, cyc - iconH * 0.5f, wifiSf, bars, 1.0f, 1.0f, 1.0f, wa * fadeMul);
            lx += wifiW + margin;
        }
        // Bluetooth.
        {
            float btSf = iconH / 20.0f;
            float bta = (bl == kBtLevel_Off || bl == kBtLevel_Unknown) ? 0.32f
                      : (bl == kBtLevel_Connected ? 0.96f : 0.72f);
            drawBtIcon(lx + so[0], cyc - iconH * 0.5f + so[1], btSf, 0.0f, 0.0f, 0.0f, bta * 0.6f * fadeMul);
            drawBtIcon(lx, cyc - iconH * 0.5f, btSf, 1.0f, 1.0f, 1.0f, bta * fadeMul);
        }
    }
}

// ===========================================================================
// Settings dialogs + Theme Settings choosers (web DIALOG_TEMPLATES + theme tables)
// ===========================================================================
struct Ps3ColorOpt { const char* name; float r, g, b; };
static const Ps3ColorOpt kPs3ColorOpts[] = {
    {"Original",0.82f,0.62f,0.90f},{"Yellow",1.00f,0.88f,0.20f},{"Green",0.65f,0.87f,0.30f},
    {"Pink",1.00f,0.64f,0.72f},{"Dark Green",0.25f,0.70f,0.25f},{"Light Purple",0.82f,0.62f,0.90f},
    {"Teal",0.30f,0.88f,0.85f},{"Dark Blue",0.10f,0.30f,0.80f},{"Magenta",0.70f,0.30f,0.80f},
    {"Orange",1.00f,0.70f,0.15f},{"Brown",0.62f,0.43f,0.18f},{"Red",0.90f,0.22f,0.22f},
    {"Black",0.06f,0.06f,0.075f},{"White",0.95f,0.95f,0.98f},{"Gray",0.55f,0.57f,0.62f},
    {"Blue",0.20f,0.45f,0.95f},{"Cyan",0.20f,0.85f,0.95f},{"Lime",0.55f,0.95f,0.20f},
    {"Gold",1.00f,0.78f,0.25f},{"Violet",0.55f,0.35f,0.95f},{"Crimson",0.80f,0.10f,0.30f},
};
static const int kPs3ColorCount = 21;
struct Ps3DayNightOpt { const char* name; float blend; };
static const Ps3DayNightOpt kPs3DayNightOpts[] = {
    {"Auto (Time of Day)",-1.0f},{"Day",0.0f},{"Morning",0.25f},{"Dusk",0.5f},{"Evening",0.75f},{"Night",1.0f},
};
static const int kPs3DayNightCount = 6;
static const char* const kPs3ThemeOpts[] = {"Original","Classic"};
static const char* const kPs3BgOpts[]    = {"Original","Classic","Wallpaper"};
static const char* const kPs3FontOpts[]  = {"Original","Rounded","Pop"};

void NanoMenu::loadPs3ThemeSettings() {
    char buf[PROPERTY_VALUE_MAX];
    auto rd = [&](const char* prop, int cap) -> int {
        property_get(prop, buf, "0"); int v = atoi(buf);
        if (v < 0 || v >= cap) v = 0; return v;
    };
    mPs3ThemeIdx    = rd("persist.gammaos.nano.ps3xmb.theme", 2);
    mPs3ColorIdx    = rd("persist.gammaos.nano.ps3xmb.color", kPs3ColorCount);
    mPs3BgIdx       = rd("persist.gammaos.nano.ps3xmb.bg", 3);
    mPs3FontIdx     = rd("persist.gammaos.nano.ps3xmb.font", 3);
    mPs3DayNightIdx = rd("persist.gammaos.nano.ps3xmb.daynight", kPs3DayNightCount);
    // Apply the visual state (colour + day/night). Cross-fades from the boot
    // colour are handled in ps3bg; setThemeColor/setDayNightBlend just set the
    // target. mPs3ColorIdx 0 = Original (per-month hue).
    if (mPs3ColorIdx == 0) ps3bg::clearThemeColor();
    else ps3bg::setThemeColor(kPs3ColorOpts[mPs3ColorIdx].r,
                              kPs3ColorOpts[mPs3ColorIdx].g,
                              kPs3ColorOpts[mPs3ColorIdx].b);
    ps3bg::setDayNightBlend(kPs3DayNightOpts[mPs3DayNightIdx].blend);
    // Background == Classic (index 1) removes the glitter particle field.
    ps3bg::setParticlesEnabled(mPs3BgIdx != 1);
}

// Live right-side value for a Theme Settings row (mirrors the web
// resolveItemValue): the value reflects the CURRENT selection so the menu shows
// it without opening the chooser. Non-theme rows fall back to the static value.
std::string NanoMenu::resolvePs3ItemValue(const Ps3Item& it) {
    const std::string& n = it.label;
    if (n == "Theme") {
        int c = (int)(sizeof(kPs3ThemeOpts) / sizeof(kPs3ThemeOpts[0]));
        if (mPs3ThemeIdx >= 0 && mPs3ThemeIdx < c) return kPs3ThemeOpts[mPs3ThemeIdx];
    } else if (n == "Colour" || n == "Color") {
        if (mPs3ColorIdx >= 0 && mPs3ColorIdx < kPs3ColorCount) return kPs3ColorOpts[mPs3ColorIdx].name;
    } else if (n == "Background") {
        int c = (int)(sizeof(kPs3BgOpts) / sizeof(kPs3BgOpts[0]));
        if (mPs3BgIdx >= 0 && mPs3BgIdx < c) return kPs3BgOpts[mPs3BgIdx];
    } else if (n == "Font") {
        int c = (int)(sizeof(kPs3FontOpts) / sizeof(kPs3FontOpts[0]));
        if (mPs3FontIdx >= 0 && mPs3FontIdx < c) return kPs3FontOpts[mPs3FontIdx];
    } else if (n == "Day/Night") {
        if (mPs3DayNightIdx >= 0 && mPs3DayNightIdx < kPs3DayNightCount) return kPs3DayNightOpts[mPs3DayNightIdx].name;
    }
    return it.value;
}

void NanoMenu::openPs3Dialog(const Ps3Item& it) {
    const std::string& n = it.label;
    mPs3DlgOptions.clear(); mPs3DlgSwatch.clear();
    mPs3DlgThemeKey = 0; mPs3DlgKind = 0; mPs3DlgTitle = n; mPs3DlgBody.clear();
    if (n == "Theme") {
        mPs3DlgKind = 1; mPs3DlgThemeKey = 1;
        for (const char* s : kPs3ThemeOpts) { mPs3DlgOptions.push_back(s); mPs3DlgSwatch.push_back(-1); }
        mPs3DlgSel = mPs3ThemeIdx;
    } else if (n == "Colour" || n == "Color") {
        mPs3DlgKind = 1; mPs3DlgThemeKey = 2;
        for (int i = 0; i < kPs3ColorCount; i++) { mPs3DlgOptions.push_back(kPs3ColorOpts[i].name); mPs3DlgSwatch.push_back(i); }
        mPs3DlgSel = mPs3ColorIdx;
    } else if (n == "Background") {
        mPs3DlgKind = 1; mPs3DlgThemeKey = 3;
        for (const char* s : kPs3BgOpts) { mPs3DlgOptions.push_back(s); mPs3DlgSwatch.push_back(-1); }
        mPs3DlgSel = mPs3BgIdx;
    } else if (n == "Font") {
        mPs3DlgKind = 1; mPs3DlgThemeKey = 4;
        for (const char* s : kPs3FontOpts) { mPs3DlgOptions.push_back(s); mPs3DlgSwatch.push_back(-1); }
        mPs3DlgSel = mPs3FontIdx;
    } else if (n == "Day/Night") {
        mPs3DlgKind = 1; mPs3DlgThemeKey = 5;
        for (int i = 0; i < kPs3DayNightCount; i++) { mPs3DlgOptions.push_back(kPs3DayNightOpts[i].name); mPs3DlgSwatch.push_back(-1); }
        mPs3DlgSel = mPs3DayNightIdx;
    } else if (n == "System Update") {
        mPs3DlgBody = "Select an update method.";
        mPs3DlgOptions.push_back("Update via Internet"); mPs3DlgOptions.push_back("Update via Storage Media");
        mPs3DlgSwatch.assign(2, -1); mPs3DlgSel = 0;
    } else if (n == "System Information") {
        mPs3DlgBody = "System Software\nVersion 4.91\n\nIP Address\n192.168.1.10\n\nSystem Storage\n466 GB free of 500 GB";
        mPs3DlgOptions.push_back("OK"); mPs3DlgSwatch.assign(1, -1); mPs3DlgSel = 0;
    } else {
        // generic info dialog for any other action='dialog' leaf
        mPs3DlgBody = "This feature is not available yet.";
        mPs3DlgOptions.push_back("OK"); mPs3DlgSwatch.assign(1, -1); mPs3DlgSel = 0;
    }
    mPs3DlgOrigSel = mPs3DlgSel;
    mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgBlurValid = false;
}

void NanoMenu::previewThemeSetting(int themeKey, int sel) {
    // Apply a chooser value to the live state WITHOUT persisting (live preview
    // while scrolling + revert on cancel). The index members track the live
    // selection so the menu row's inline value updates as the cursor moves, and
    // ps3bg cross-fades the colour/day-night toward the new target.
    switch (themeKey) {
        case 1: mPs3ThemeIdx = sel; break;
        case 2:
            mPs3ColorIdx = sel;
            if (sel <= 0) ps3bg::clearThemeColor();
            else if (sel < kPs3ColorCount) ps3bg::setThemeColor(kPs3ColorOpts[sel].r, kPs3ColorOpts[sel].g, kPs3ColorOpts[sel].b);
            break;
        case 3: mPs3BgIdx = sel; ps3bg::setParticlesEnabled(sel != 1); break;  // Classic hides particles
        case 4: mPs3FontIdx = sel; break;
        case 5:
            mPs3DayNightIdx = sel;
            if (sel >= 0 && sel < kPs3DayNightCount) ps3bg::setDayNightBlend(kPs3DayNightOpts[sel].blend);
            break;
        default: break;
    }
}

void NanoMenu::applyThemeSetting(int themeKey, int sel) {
    char v[16]; snprintf(v, sizeof(v), "%d", sel);
    switch (themeKey) {
        case 1: property_set("persist.gammaos.nano.ps3xmb.theme", v); break;
        case 2: property_set("persist.gammaos.nano.ps3xmb.color", v); previewThemeSetting(2, sel); break;
        case 3: property_set("persist.gammaos.nano.ps3xmb.bg", v); break;
        case 4: property_set("persist.gammaos.nano.ps3xmb.font", v); break;
        case 5: property_set("persist.gammaos.nano.ps3xmb.daynight", v); previewThemeSetting(5, sel); break;
        default: break;
    }
}

void NanoMenu::closePs3Dialog(bool apply) {
    if (mPs3DlgThemeKey > 0) {
        if (apply) applyThemeSetting(mPs3DlgThemeKey, mPs3DlgSel);
        else       previewThemeSetting(mPs3DlgThemeKey, mPs3DlgOrigSel);   // revert the live preview
    }
    mPs3DlgActive = false;
    mPs3DlgBlurValid = false;
}

void NanoMenu::renderPs3Dialog() {
    if (!mPs3DlgActive) return;
    float dt = mFrameDt; if (dt < 0.0f) dt = 0.0f; if (dt > 0.1f) dt = 0.1f;
    mPs3DlgAnim += (1.0f - mPs3DlgAnim) * (1.0f - expf(-13.0f * dt));
    if (mPs3DlgAnim > 0.999f) mPs3DlgAnim = 1.0f;
    float ap = mPs3DlgAnim;
    { ps3::LayoutParams lp; lp.panelW = mWidth; lp.panelH = mHeight; lp.uiScale = mPs3UiScale; ps3::layoutCompute(lp); }

    // Fullscreen message dialogs (System Update, ...) sit on a blurred backdrop
    // that keeps ANIMATING (re-captured every frame, like the web). Side-panel
    // choosers do NOT blur - the XMB stays visible and the LIVE background colour
    // shows through so the Colour/Day-Night preview is seen behind the panel.
    if (mPs3DlgKind != 1) {
        // ~30Hz live wave/gradient backdrop (workTex, no FB capture) so the
        // dialog open animation stays smooth at 60fps. waveSpace = logical blur.
        bool due = !mPs3DlgBlurValid || (mEffectTime - mPs3DlgBlurT) >= 0.0667f;   // ~15Hz backdrop sample
        if (due && captureGlassFromWave()) { mPs3DlgBlurValid = true; mPs3DlgBlurT = mEffectTime; }
        if (mPs3DlgBlurValid)
            drawFrostedGlass(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f, 0.40f, 0.40f, 0.48f, 1.0f, ap, /*waveSpace=*/true);
    }

    float ss = ps3::devS(1.5f);
    float so[2] = { sDrmRotMat[2] * ss, sDrmRotMat[3] * ss };

    if (mPs3DlgKind == 1) {
        // ---- side-panel chooser (Theme Settings) ----
        float panelW = (float)mWidth * 0.42f;
        float ease = ap * ap * (3.0f - 2.0f * ap);
        float px = (float)mWidth - panelW * ease;
        drawQuad(px, 0.0f, panelW + ps3::devS(40.0f), (float)mHeight, 0.13f, 0.12f, 0.18f, 0.84f * ap);
        float titleX = px + ps3::devS(30.0f);
        float tts = ps3::fontScale(26.0f);
        drawText(mPs3DlgTitle.c_str(), titleX + so[0], ps3::devS(38.0f) + so[1], tts, 0.0f, 0.0f, 0.0f, 0.5f * ap);
        drawText(mPs3DlgTitle.c_str(), titleX, ps3::devS(38.0f), tts, 0.90f, 0.86f, 0.96f, ap);
        int n = (int)mPs3DlgOptions.size();
        float rowH = ps3::devS(46.0f);
        float listCy = (float)mHeight * 0.52f;
        for (int i = 0; i < n; i++) {
            float y = listCy + (float)(i - mPs3DlgSel) * rowH;
            if (y < -rowH || y > (float)mHeight + rowH) continue;
            bool sel = (i == mPs3DlgSel);
            float a = (sel ? 1.0f : 0.55f) * ap;
            float fs = ps3::fontScale(sel ? 30.0f : 24.0f);
            float tx = titleX;
            if (i < (int)mPs3DlgSwatch.size() && mPs3DlgSwatch[i] >= 0) {
                int ci = mPs3DlgSwatch[i]; float sw = ps3::devS(26.0f);
                drawQuad(titleX, y - sw * 0.5f, sw, sw, kPs3ColorOpts[ci].r, kPs3ColorOpts[ci].g, kPs3ColorOpts[ci].b, a);
                tx = titleX + sw + ps3::devS(14.0f);
            }
            float ty = ps3::baselineToTopY(y, fs);
            drawText(mPs3DlgOptions[i].c_str(), tx + so[0], ty + so[1], fs, 0.0f, 0.0f, 0.0f, 0.5f * a);
            float c = sel ? 1.0f : 0.85f;
            drawText(mPs3DlgOptions[i].c_str(), tx, ty, fs, c, c, c, a);
        }
    } else {
        // ---- fullscreen message / chooser dialog (System Update, ...) ----
        float bw = (float)mWidth * 0.72f, bh = (float)mHeight * 0.56f;
        float bx = ((float)mWidth - bw) * 0.5f, by = ((float)mHeight - bh) * 0.5f;
        drawQuad(bx, by, bw, bh, 0.08f, 0.08f, 0.11f, 0.86f * ap);
        float padX = bx + ps3::devS(34.0f);
        float tts = ps3::fontScale(32.0f);
        float titleY = by + ps3::devS(28.0f);
        drawText(mPs3DlgTitle.c_str(), padX + so[0], titleY + so[1], tts, 0.0f, 0.0f, 0.0f, 0.5f * ap);
        drawText(mPs3DlgTitle.c_str(), padX, titleY, tts, 1.0f, 1.0f, 1.0f, ap);
        drawQuad(padX, by + ps3::devS(74.0f), bw - ps3::devS(68.0f), fmaxf(1.0f, ps3::devS(1.0f)), 0.5f, 0.5f, 0.55f, 0.6f * ap);
        // body: split on '\n', word-wrap each segment to the box width
        float bs = ps3::fontScale(22.0f);
        float lineH = ps3::devS(30.0f);
        float maxW = bw - ps3::devS(68.0f);
        float ty = by + ps3::devS(96.0f);
        std::string seg;
        auto emitWrapped = [&](const std::string& s) {
            std::string cur, word;
            auto flush = [&](bool last) {
                std::string trial = cur.empty() ? word : cur + " " + word;
                if (!cur.empty() && measureText(trial.c_str(), bs) > maxW) {
                    drawText(cur.c_str(), padX, ty, bs, 0.82f, 0.82f, 0.86f, ap); ty += lineH; cur = word;
                } else cur = trial;
                word.clear();
                if (last) { if (!cur.empty()) { drawText(cur.c_str(), padX, ty, bs, 0.82f, 0.82f, 0.86f, ap); ty += lineH; } }
            };
            if (s.empty()) { ty += lineH * 0.5f; return; }
            for (const char* p = s.c_str(); ; ++p) {
                if (*p == ' ' || *p == '\0') { flush(*p == '\0'); if (*p == '\0') break; }
                else word.push_back(*p);
            }
        };
        for (size_t i = 0; i <= mPs3DlgBody.size(); i++) {
            if (i == mPs3DlgBody.size() || mPs3DlgBody[i] == '\n') { emitWrapped(seg); seg.clear(); }
            else seg.push_back(mPs3DlgBody[i]);
        }
        // options as a vertical list near the bottom of the box, selected highlit
        int n = (int)mPs3DlgOptions.size();
        float os = ps3::fontScale(26.0f);
        float optRow = ps3::devS(40.0f);
        float optY = by + bh - ps3::devS(30.0f) - (float)n * optRow;
        for (int i = 0; i < n; i++) {
            float y = optY + (float)i * optRow;
            bool sel = (i == mPs3DlgSel);
            if (sel) drawQuad(padX - ps3::devS(8.0f), y - ps3::devS(4.0f), maxW + ps3::devS(16.0f), optRow - ps3::devS(6.0f), 0.20f, 0.42f, 0.62f, 0.55f * ap);
            float c = sel ? 1.0f : 0.7f;
            float ty2 = ps3::baselineToTopY(y + optRow * 0.5f, os);
            drawText(mPs3DlgOptions[i].c_str(), padX + so[0], ty2 + so[1], os, 0.0f, 0.0f, 0.0f, 0.5f * ap);
            drawText(mPs3DlgOptions[i].c_str(), padX, ty2, os, c, c, c, ap);
        }
    }
}

} // namespace android
