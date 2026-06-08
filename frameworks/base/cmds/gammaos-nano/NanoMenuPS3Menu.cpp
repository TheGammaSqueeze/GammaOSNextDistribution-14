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
#include "NanoMenuPS3Globe.h"
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
#include <thread>
#include <vector>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#include <GLES2/gl2.h>
#include <png.h>

#include <cutils/properties.h>
#include <utils/Log.h>

namespace android {

// Clock drop-shadow offset: a single device-y offset. The caller passes a signed
// magnitude (devS(..) * mPs3ShadowDir) where mPs3ShadowDir is the device-y sign of
// panel-down derived from the orientation (sDrmRotMat), so the shadow always falls
// toward the VISUAL bottom of the clock on any panel. out[1] = the signed offset.
static inline void ps3ShadowOffset(float s, int /*w*/, int /*h*/, float out[2]) {
    out[0] = 0.0f;
    out[1] = s;
}

// Text outline is now produced by drawText itself (mTextOutlineMode==1, an even
// 4-offset outline batched into the glyph draw - symmetric and one draw call). So
// drawTextStroke is a no-op kept only so the existing call sites stay readable
// (each "drawTextStroke(...) then drawText(...)" reads as "outline then fill").
void NanoMenu::drawTextStroke(const char*, float, float, float, float) {}
// Icons have no built-in outline, so draw an even 4-direction dark silhouette
// (left/right/up/down) behind the icon. 4 (not 8) copies keeps the per-frame draw
// count low - the menu has ~16 visible icons. Symmetric, so no clipped side.
static const float kStroke4X[4] = { -1.0f, 1.0f, 0.0f, 0.0f };
static const float kStroke4Y[4] = {  0.0f, 0.0f,-1.0f, 1.0f };
void NanoMenu::drawIconStroke(unsigned int tex, float x, float y, float w, float h, float a) {
    if (a <= 0.004f || tex == 0) return;
    float r = ps3::devS(2.0f);
    // Batch the 4 offset shadow copies into ONE draw (same texture + colour)
    // instead of 4 drawIconTex calls (each a program bind + draw + 3 attrib
    // setups). drawText already batches its outline this way. Pixel-identical:
    // the same 4 offset quads, same UVs, same alpha.
    GLfloat verts[4 * 12], uvs[4 * 12], colors[4 * 24];
    for (int s = 0; s < 4; s++) {
        float xs = x + kStroke4X[s] * r, ys = y + kStroke4Y[s] * r;
        float x0 = (xs / mWidth) * 2.0f - 1.0f;
        float y0 = 1.0f - ((ys + h) / mHeight) * 2.0f;
        float x1 = ((xs + w) / mWidth) * 2.0f - 1.0f;
        float y1 = 1.0f - (ys / mHeight) * 2.0f;
        GLfloat* v = verts + s * 12;
        v[0]=x0; v[1]=y0; v[2]=x1; v[3]=y0; v[4]=x1; v[5]=y1;
        v[6]=x1; v[7]=y1; v[8]=x0; v[9]=y1; v[10]=x0; v[11]=y0;
        GLfloat* u = uvs + s * 12;
        u[0]=0; u[1]=1; u[2]=1; u[3]=1; u[4]=1; u[5]=0;
        u[6]=1; u[7]=0; u[8]=0; u[9]=0; u[10]=0; u[11]=1;
        GLfloat* c = colors + s * 24;
        for (int i = 0; i < 6; i++) { c[i*4]=0.0f; c[i*4+1]=0.0f; c[i*4+2]=0.0f; c[i*4+3]=a; }
    }
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
    glDrawArrays(GL_TRIANGLES, 0, 24);
    glDisableVertexAttribArray(mTextLocPosition);
    glDisableVertexAttribArray(mTextLocTexCoord);
    glDisableVertexAttribArray(mTextLocColor);
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

// libpng loader -> full-COLOUR RGBA texture from an ABSOLUTE path. Used for the
// real APK icons cached at /data/system/nano_app_icons/<pkg>.png. Same as
// loadPs3IconTex but keeps the original RGB (no silvery-white force) and takes an
// absolute path. Returns 0 on any failure (missing file / decode error).
static GLuint loadColorIconTexAbs(const char* absPath) {
    FILE* fp = fopen(absPath, "rb");
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
    // Clear any stale BT receive-mode flag left by a prior session that exited
    // mid-receive, so the BondStateMachine inbound branch can't suppress Settings
    // for a UI that is no longer watching.
    property_set("sys.gammaos.bt.inbound_open", "0");
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
    buildTimezoneList();      // populate mTzEntries + pre-select the current zone so
                              // Settings -> Date and Time shows the live "Time Zone" value
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
        it.iconR = it.iconG = it.iconB = 1.0f;
        // Real APK icon from the DE cache (written by SystemServer). Loaded once
        // per package as a full-COLOUR texture (monoWhite=false) and drawn flat
        // (nmapTex=0 -> drawIconTex colour path, no glass). Only successes are
        // cached, so a cache that is not populated yet retries on the next open.
        const std::string& pkg = mAppEntries[i].packageName;
        GLuint appTex = 0;
        auto cached = mPs3AppIcons.find(pkg);
        if (cached != mPs3AppIcons.end()) {
            appTex = cached->second;
        } else {
            std::string path = "/data/system/nano_app_icons/" + pkg + ".png";
            appTex = loadColorIconTexAbs(path.c_str());
            if (appTex != 0) mPs3AppIcons[pkg] = appTex;   // cache successes only
        }
        if (appTex != 0) {
            it.iconTex = appTex; it.nmapTex = 0;          // flat, full-colour app icon
        } else {
            it.iconTex = mIconTextures[16]; it.nmapTex = bevel;  // bevelled-glass placeholder
        }
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

// Move the selection inside an open dialog (mirrors web navDialog/navItem/navCat).
// Up/Down (horizontal=false) scroll chooser lists only; Left/Right (horizontal=
// true) scroll choosers AND toggle a confirm dialog's Yes/No. Info pages ignore.
void NanoMenu::ps3DlgNav(int dir, bool horizontal) {
    int n = (int)mPs3DlgOptions.size();
    if (mPs3DlgKind == 1) {
        // Theme side-panel chooser: clamp + live hover preview.
        int ns = mPs3DlgSel + dir;
        if (ns < 0) ns = 0;
        if (ns > n - 1) ns = n - 1;
        if (ns != mPs3DlgSel) { mPs3DlgSel = ns; previewThemeSetting(mPs3DlgThemeKey, mPs3DlgSel); }
        return;
    }
    if (mPs3DlgType == 3) { if (horizontal && n >= 2) mPs3DlgSel ^= 1; return; }   // confirm
    if (mPs3DlgType == 0) return;                                                  // info
    if (n > 0) mPs3DlgSel = (mPs3DlgSel + dir + n) % n;                            // chooser (wrap)
}

void NanoMenu::ps3XmbLeft() {
    if (mPs3TzActive) return;   // tzglobe list is vertical only
    if (mPs3WizActive) { wizNav(-1, true); return; }
    if (mPs3DlgActive) { ps3DlgNav(-1, true); return; }   // chooser scroll / confirm toggle
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
    if (mPs3TzActive) return;   // tzglobe list is vertical only
    if (mPs3WizActive) { wizNav(+1, true); return; }
    if (mPs3DlgActive) { ps3DlgNav(+1, true); return; }   // chooser scroll / confirm toggle
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
    if (mPs3TzActive) { tzGlobeNav(-1); return; }
    if (mPs3WizActive) { wizNav(-1, false); return; }
    if (mPs3DlgActive) { ps3DlgNav(-1, false); return; }
    int& s = ps3CurSel();
    if (s > 0) { mPs3ItemAnimFrom = mPs3AnimItem; mPs3ItemAnimStart = mEffectTime; s--; }
}
void NanoMenu::ps3XmbDown() {
    if (mPs3TzActive) { tzGlobeNav(+1); return; }
    if (mPs3WizActive) { wizNav(+1, false); return; }
    if (mPs3DlgActive) { ps3DlgNav(+1, false); return; }
    int& s = ps3CurSel(); int n = (int)ps3CurItems().size();
    if (s < n - 1) { mPs3ItemAnimFrom = mPs3AnimItem; mPs3ItemAnimStart = mEffectTime; s++; }
}

void NanoMenu::ps3XmbSelect() {
    if (mPs3TzActive) { closeTimezoneGlobe(true); return; }   // X: apply the highlighted zone + close
    if (mPs3WizActive) { wizConfirm(); return; }   // X: advance the network setup wizard
    if (mPs3DlgActive) {
        // The "Date and Time" fullscreen chooser launches the matching wizard on
        // confirm (option 0 = Set via Internet, 1 = Set Manually).
        if (mPs3DlgKind == 0 && mPs3DlgType == 1 && mPs3DlgTitle == "Date and Time") {
            int sel = mPs3DlgSel;
            mPs3DlgActive = false; mPs3DlgBlurValid = false;
            startDateTimeWizard(sel == 0 ? 0 : 1);
            return;
        }
        closePs3Dialog(mPs3DlgThemeKey > 0); return;   // X: apply chooser / dismiss message
    }
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
        case PS3_ROM:    { mXmbSystemIndex = it.a; mXmbGameIndex = it.b; mSearchActive = false;
                           if (!isLaunchReady()) { showLaunchBusyToast(); return; }
                           if (mOverlayMode) { overlayLaunchGame(); return; } launchXmbGame(); return; }
        case PS3_RECENT: { mXmbSystemIndex = -1;  mXmbGameIndex = it.a; mSearchActive = false;
                           if (!isLaunchReady()) { showLaunchBusyToast(); return; }
                           if (mOverlayMode) { overlayLaunchGame(); return; } launchXmbGame(); return; }
        case PS3_SETTING:{ if (it.a == 0) openWifiScreen(); else if (it.a == 1) openBtScreen(); return; }
        case PS3_APP:
        case PS3_LAUNCH_PKG: {
            // Launch an installed app / package (Applications submenu). Mirrors the
            // carousel's app-launch handshake (NanoMenuInput.cpp): set launch_app to
            // the package, clear ROM/core + stale QR priming, flag return-to-apps,
            // then wait for the select-key release before the nano exits so the
            // launched app does not see a phantom press.
            if (it.payloadStr.empty()) return;
            // Overlay XMB: replace the running app with the selected one (force-stop
            // current + start new + dismiss) instead of the home exit-to-launch path.
            if (mOverlayMode) { overlayLaunchPackage(it.payloadStr); return; }
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
        case PS3_DATA_LEAF: {
            // "Internet Connection Settings" runs the full PS3 setup wizard (1:1
            // web NETCONF flow, real scan/connect). "Internet Connection" opens the
            // side-panel Enabled/Disabled toggle for the Wi-Fi radio.
            if (it.label == "Internet Connection Settings") { startNetWizard(); return; }
            if (it.label == "Internet Connection") { openPs3Dialog(it); return; }
            if (it.label == "Time Zone") { openTimezoneGlobe(); return; }   // 3D Earth selector
            if (it.label == "Set via Internet") { startDateTimeWizard(0); return; }  // NTP progress->result
            if (it.label == "Set Manually")     { startDateTimeWizard(1); return; }  // OSK date+time entry
            // Accessory Settings -> real Bluetooth device management (1:1 web bt_* flow).
            if (it.label == "Manage Bluetooth® Devices")      { startBtWizard(0); return; }
            if (it.label == "BD Remote Control Registration") { startBtWizard(1); return; }
            if (it.label == "Audio Device Settings")          { startBtWizard(2); return; }
            if (it.action == 1) openPs3Dialog(it);   // action='dialog' -> dialog/chooser
            return;
        }
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
    if (mPs3TzActive) { closeTimezoneGlobe(false); return; }   // O: cancel (keep current zone)
    if (mPs3WizActive) { wizBack(); return; }   // O: step back through the network setup wizard
    if (mPs3DlgActive) { closePs3Dialog(false); return; }   // O: cancel the dialog/chooser
    // Overlay XMB: Back at the top level RESUMES the running game (dismiss + thaw).
    if (overlayAtTopLevel()) { overlayResume(); return; }
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
    // Arm the once-per-frame glass-icon uniform upload (drawGlassIcon sends the
    // frame-invariant uniforms on the first icon, skips them on the rest).
    mGlassUniformsSet = false;
    // Route all PS3 text through drawText's EVEN 4-offset outline (mode 1) instead
    // of the default single directional drop shadow. It is symmetric on all four
    // sides (fixing the uneven/clipped look), subtle, brightness-scaled (ratio set
    // below from the wallpaper luma), and batched into the glyph draw so it adds no
    // extra draw calls. Glow/halo passes flip to mode 2 (none) around their copies.
    mTextOutlineMode = 1;
    // Cold-boot intro: advance the boot clock once per frame. While it suppresses
    // the UI, draw only the boot overlay (wave/gradient reveal from black, logo,
    // epilepsy warning) over the composited background and skip the menu. Once the
    // staged reveal window opens it falls through and the menu draws with the
    // pop-in multipliers (mPs3BootLabelReveal / mPs3BootIconReveal).
    if (mPs3BootActive) {
        if (ps3BootUpdate(mFrameDt)) { renderPs3BootOverlay(); return; }
    }
    // Fresh setup: the intro just ended on the warning (no icon reveal). Hand
    // straight to the wizard on this very frame so the live XMB menu never flashes
    // between the warning and the wizard. (Subsequent frames the render() dispatch
    // routes to renderSetupWizard once mPs3BootActive is false.)
    if (mSetupWizardActive) { renderSetupWizard(); return; }
    if (mPs3Cats.empty()) return;
    if (mMenuState == MENU_WIFI) { renderWifiScreen(); return; }
    if (mMenuState == MENU_BT)   { renderBtScreen();   return; }

    // Overlay entrance: when the in-game overlay is raised, the blurred backdrop
    // is already there; the XMB chrome (category labels/icons, item list, clock)
    // fades + pops in like the cold-boot hand-off. Reuse the boot reveal
    // multipliers, eased over ~520ms. Wrap-safe against mEffectTime's fmod-500.
    // Pending sentinel (-2): stamp the real start on this first rendered frame
    // (after the blocking capture in overlayShow) so the entrance plays.
    if (mOverlayMode && mOverlayEnterStart <= -1.5f) {
        mOverlayEnterStart = mEffectTime;
    }
    if (mOverlayMode && mOverlayEnterStart >= 0.0f) {
        float el = mEffectTime - mOverlayEnterStart;
        const float dur = 0.52f;
        if (el < 0.0f || el >= dur) {
            mPs3BootIconReveal = 1.0f;
            mPs3BootLabelReveal = 1.0f;
            mOverlayEnterStart = -1.0f;
        } else {
            float t = el / dur;
            float e = t * t * (3.0f - 2.0f * t);   // smoothstep
            mPs3BootIconReveal = e;
            mPs3BootLabelReveal = e;
        }
    }

    // Date and Time -> Time Zone: the 1:1 web 3D-globe selector. Rendered fully
    // standalone (it fills black then fades the Earth in over it), skipping the
    // expensive menu/glass-icon pass entirely.
    if (mPs3TzActive) { renderTimezoneGlobe(); return; }

    // In the in-game overlay (scrim mode: a live app is behind us) a FULLSCREEN
    // dialog or the network wizard must HIDE the XMB chrome and show ONLY the
    // dialog/wizard over the scrim - exactly like the home XMB, where the dialog's
    // own opaque backdrop covers the menu. That backdrop is intentionally gated off
    // in scrim mode (so the dimmed app shows through), so without this the category
    // rail + item list + clock would bleed through behind the dialog and clash.
    // renderPs3Dialog()/renderNetWizard() are self-contained (own layout + anim).
    // The side-panel chooser (mPs3DlgKind==1, Theme Settings) is partial and
    // deliberately keeps the XMB visible for its live colour preview, so it is NOT
    // caught here (it falls through to draw the chrome plus the side panel).
    if (mOverlayMode) {
        // Overlay (scrim AND wallpaper): a fullscreen dialog / the net wizard takes
        // over the whole screen with NO XMB chrome - shown over the dimmed live app
        // (scrim) or the user's selected wallpaper (launcher). Side-panel choosers
        // (kind 1) are NOT fullscreen, so they keep the XMB behind them.
        if (mPs3WizActive)                     { renderNetWizard(); return; }
        if (mPs3DlgActive && mPs3DlgKind != 1) { renderPs3Dialog(); return; }
    }

    // Opt-in slow-frame diagnostic: `setprop persist.gammaos.nano.ps3xmb.fpslog 1`
    // logs any frame slower than ~22ms (<45fps) with context, so transition dips
    // can be measured from logcat. Off by default (read once).
    static bool sFpsLog = property_get_bool("persist.gammaos.nano.ps3xmb.fpslog", false);
    if (sFpsLog && mFrameDt > 0.022f)
        ALOGW("ps3 slow frame %.1fms inSub=%d subAnim=%d",
              mFrameDt * 1000.0f, (int)!mPs3Stack.empty(), (int)(mPs3SubAnimStart >= 0.0f));

    { ps3::LayoutParams lp; lp.panelW = mWidth; lp.panelH = mHeight; lp.uiScale = mPs3UiScale;
      ps3::layoutCompute(lp); }

    // Dynamic text outline: alpha scales with wallpaper brightness so it is minimal
    // on a dark wallpaper (the bright text already reads) and stronger on a light
    // one (needs the contrast). It is the even 4-offset outline (drawText mode 1),
    // so there is no direction to get wrong.
    {
        float bgL = ps3bg::backgroundLuma();
        float ss = (bgL - 0.32f) / (0.85f - 0.32f);
        mPs3ShadowStrength = ss < 0.0f ? 0.0f : (ss > 1.0f ? 1.0f : ss);
    }
    mPs3ShadowAlpha = 0.12f + 0.62f * mPs3ShadowStrength;   // 0.12 dark .. 0.74 light (icon silhouette)
    mTextOutlineRatio = 0.5f;                               // text outline = 50% transparent black (alpha = 0.5 * text alpha)
    // The clock keeps a single device-y drop shadow; its sign is derived from the
    // panel orientation (sDrmRotMat[3]) and is left untouched (-1 on the 180 panel)
    // per the request to not change the clock.
    mPs3ShadowDir = (sDrmRotMat[3] <= 0.0f) ? 1.0f : -1.0f;

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
        // el < 0 means mEffectTime wrapped (it is fmod'd to 500s) below the stored
        // start, which would make the easeOutCubic extrapolate wildly and fling the
        // item list off-screen until the next input. Treat a wrap as settled.
        if (mPs3ItemAnimStart < 0.0f || el < 0.0f || el >= dur || dur <= 0.0f) {
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
        // p < 0 means mEffectTime wrapped (fmod 500s) below the stored start; snap
        // the collapse to its end state instead of extrapolating.
        if (p >= 1.0f || p < 0.0f) { p = 1.0f; mPs3SubAnimStart = -1.0f; subAnimating = false; }
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
        // 60Hz during a live theme preview (chooser open or cross-fade settling)
        // so the colour change tracks smoothly; ~15Hz otherwise.
        float blurCad = (ps3bg::themeFading() || mPs3DlgKind == 1) ? 0.0f : 0.0667f;
        bool due = !mPs3GlassValid || (mEffectTime - mPs3GlassBlurT) >= blurCad;
        // The frosted backdrop is the blurred WAVE (captureGlassFromWave reads
        // ps3bg::workTex), so it only matches when the wave is the visible background.
        // Draw it when: home XMB (!mOverlayMode, always); overlay WALLPAPER/launcher
        // ONLY if the user's wallpaper IS the wave (mCurrentEffect==22). NEVER in
        // overlay scrim mode (the dark scrim + live app must show through), and NEVER
        // over a non-wave wallpaper (the blurred wave would not match it - let
        // renderEffect's wallpaper show instead). The submenu glass ICONS refract
        // ps3bg::workTex() separately (unaffected); no OSK reuses the submenu blur.
        const bool frostBg = !mOverlayMode || (mOverlayWallpaper && mCurrentEffect == 22);
        if (due && frostBg && captureGlassFromWave()) { mPs3GlassValid = true; mPs3GlassBlurT = mEffectTime; }
        if (mPs3GlassValid && frostBg)
            // Neutral tint (1,1,1): pure blur, NO darkening or hue/shade change -
            // the backdrop is the blurred wave at its own brightness.
            drawFrostedGlass(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f,
                             1.0f, 1.0f, 1.0f, 1.0f, subT, /*waveSpace=*/true);
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
            drawIconStroke(mPs3Cats[i].iconTex, ix, iy, dsz, dsz, mPs3ShadowAlpha * 0.7f * alpha);
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
            drawTextStroke(nm, lx, ly, ls, mPs3ShadowAlpha * la);   // stroke
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
            drawTextStroke(lines[li].c_str(), txDev, ly, ds, mPs3ShadowAlpha * alpha);
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
            // Icon outline silhouette behind the flat menu icons so they read over
            // the bright wave. RetroArch/console icons (isRetroIcon) are skipped:
            // they render as bevelled glass and the dark silhouette would peek out
            // around the translucent glass as an ugly halo (the normal XMB icons do
            // not show this), so they get no stroke.
            if (it.iconTex && !isRetroIcon(it.kind))
                drawIconStroke(it.iconTex, ix, iy, dsz, dsz, mPs3ShadowAlpha * 0.7f * alpha);
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
            // Label fill. Non-active labels get the even outline (drawText mode 1,
            // set for the frame); the active label instead gets the dual-halo white
            // glow, drawn with mode 2 (no dark outline on the bright halo copies).
            if (isActive) {
                float phase = fmodf(mEffectTime, ps3::PULSE_PERIOD_MS / 1000.0f) / (ps3::PULSE_PERIOD_MS / 1000.0f);
                float s = 0.5f * (1.0f - cosf(phase * 2.0f * (float)M_PI));
                float outerA = ps3::PULSE_ALPHA_MIN + (ps3::PULSE_ALPHA_MAX - ps3::PULSE_ALPHA_MIN) * s;
                float innerA = ps3::PULSE_INNER_MIN + (ps3::PULSE_INNER_MAX - ps3::PULSE_INNER_MIN) * s;
                float oR = ps3::devS(5.0f), iR = ps3::devS(2.2f);
                // The 8 outer + 6 inner glow copies + the white centre, laid out
                // once and drawn in a single batch (no outline, == the old mode-2
                // drawText loop). The dark even-outline non-active labels below keep
                // mTextOutlineMode (==1) which drawTextGlow never touches.
                drawTextGlow(L, lx, ty, ts, oR, iR,
                             outerA * alphaMul * 0.16f, innerA * alphaMul * 0.28f, alpha);
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
                drawTextStroke(itVal.c_str(), vx, ty, vs, mPs3ShadowAlpha * alpha);
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
            if (it.iconTex && !isRetroIcon(it.kind))   // no stroke on glass/console icons
                drawIconStroke(it.iconTex, ix, iy, dsz, dsz, mPs3ShadowAlpha * 0.7f * a);
            // Glass (live wave refraction) for the PROMINENT breadcrumb cubes only
            // - the selected parent plus the near, still-legible siblings - so the
            // RetroArch / console icons stay embossed and bevelled in submenus
            // (consistent with the top level). The far, deeply-faded siblings fall
            // back to the cheap flat icon: at a <= 0.35 the glass vs flat difference
            // is imperceptible, and this keeps the per-frame live-wave glass-icon
            // count in submenus near the top-level count (no FPS regression).
            if (mIconGlassReady && it.nmapTex && ps3bg::workTex() && (sel || a > 0.35f))
                drawGlassIcon(it.nmapTex, ix, iy, dsz, dsz, it.iconR, it.iconG, it.iconB, a);
            else if (it.iconTex)
                drawIconTex(it.iconTex, ix, iy, dsz, dsz, it.iconR, it.iconG, it.iconB, a);
            float textA = (1.0f - t) * (sel ? ps3::ALPHA_FOCUS : ps3::ALPHA_INACTIVE);
            if (textA > 0.02f) {
                float ts = ps3::fontScale(sel ? ps3::ITEM_TEXT_ACTIVE_SIZE : ps3::ITEM_TEXT_SIZE);
                float tx = ps3::devX(ps3::XCP(ps3::ITEM_TEXT_X + (cx - srcX)));
                float ty = ps3::baselineToTopY(ps3::devY(y), ts);
                const char* L = it.label.c_str();
                drawTextStroke(L, tx, ty, ts, mPs3ShadowAlpha * textA);
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

    // Settings dialog / Theme chooser overlay on top of the menu. (The Time Zone
    // globe renders standalone via the early return above, fading in from black.)
    if (mPs3WizActive) renderNetWizard();
    else if (mPs3DlgActive) renderPs3Dialog();
}

// ---------------------------------------------------------------------------
// clock: open-right rounded U-frame + analog face + DD/M H:MM (drawClock 1:1)
// ---------------------------------------------------------------------------
void NanoMenu::drawPs3Clock(float fadeMul) {
    time_t tt = time(nullptr);
    struct tm lt; localtime_r(&tt, &lt);
    // Live Daylight Saving state straight from the resolved local time (the Olson
    // tz applies DST automatically, so this is correct with auto time too). The
    // "Daylight Saving" menu row + chooser read this, so it auto-captures DST.
    mPs3DstNow = (lt.tm_isdst > 0);
    // Format-aware clock (reads the cached members, no per-frame syscall). Date
    // Format reorders day vs month in the compact corner bar (no year, like the
    // PS3 clock); Time Format switches 12-hour (+AM/PM) vs 24-hour. The analog
    // face below already uses tm_hour % 12 so it needs no change.
    // Rebuild the digital string only on a minute/hour/date/format boundary (it
    // has no seconds), otherwise reuse the cached mPs3ClockStr. lt itself stays
    // live every frame (the analog hands and mPs3DstNow need it); only the 3
    // snprintf are gated. measureText below stays live so the right-anchor stays
    // correct across resize/orientation with no extra key.
    if (lt.tm_min != mPs3ClockKMin || lt.tm_hour != mPs3ClockKHour ||
        lt.tm_mday != mPs3ClockKMday || lt.tm_mon != mPs3ClockKMon ||
        mPs3DateFormatIdx != mPs3ClockKDateFmt || mPs3TimeFormatIdx != mPs3ClockKTimeFmt) {
        char dp[16], tp[20];
        if (mPs3DateFormatIdx == 2) snprintf(dp, sizeof(dp), "%d/%d", lt.tm_mday, lt.tm_mon + 1);   // DD/MM/YYYY -> D/M
        else                        snprintf(dp, sizeof(dp), "%d/%d", lt.tm_mon + 1, lt.tm_mday);   // (YYYY/)MM/DD -> M/D
        if (mPs3TimeFormatIdx == 0) {   // 12-Hour Clock
            int h12 = lt.tm_hour % 12; if (h12 == 0) h12 = 12;
            snprintf(tp, sizeof(tp), "%d:%02d %s", h12, lt.tm_min, lt.tm_hour >= 12 ? "PM" : "AM");
        } else {                        // 24-Hour Clock
            snprintf(tp, sizeof(tp), "%d:%02d", lt.tm_hour, lt.tm_min);
        }
        snprintf(mPs3ClockStr, sizeof(mPs3ClockStr), "%s %s", dp, tp);
        mPs3ClockKMin = lt.tm_min; mPs3ClockKHour = lt.tm_hour;
        mPs3ClockKMday = lt.tm_mday; mPs3ClockKMon = lt.tm_mon;
        mPs3ClockKDateFmt = mPs3DateFormatIdx; mPs3ClockKTimeFmt = mPs3TimeFormatIdx;
    }
    const char* timeStr = mPs3ClockStr;

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
    float so[2]; ps3ShadowOffset(ps3::devS(3.0f) * mPs3ShadowDir, mWidth, mHeight, so);   // panel-down drop shadow (dir from orientation)

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
    // The dim panel, inner glow, border stroke and analog face below are all
    // flat-colour drawQuad/drawTriangle with no text between them - ~230 tiny draws
    // that are identical every frame. Batch them into one glDrawArrays (same
    // vertices/order/blend); the digital time + battery/Wi-Fi/BT that follow use
    // text/icons, so the batch ends before them.
    beginSolidBatch();
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
    endSolidBatch();   // flush the batched panel/glow/border/face as one draw

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
        // Bluetooth. Not drawn at all when the radio is off (clear), white when on
        // but no device is connected, blue when a device is actively connected.
        if (bl != kBtLevel_Off && bl != kBtLevel_Unknown) {
            float btSf = iconH / 20.0f;
            bool conn = (bl == kBtLevel_Connected);
            float bta = conn ? 0.96f : 0.72f;
            float br = conn ? 0.34f : 1.0f, bg = conn ? 0.66f : 1.0f, bb = 1.0f;
            drawBtIcon(lx + so[0], cyc - iconH * 0.5f + so[1], btSf, 0.0f, 0.0f, 0.0f, bta * 0.6f * fadeMul);
            drawBtIcon(lx, cyc - iconH * 0.5f, btSf, br, bg, bb, bta * fadeMul);
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
// Date and Time chooser options (1:1 with the web SETTING_OPTIONS order).
static const char* const kDateFormatOpts[] = {"YYYY/MM/DD","MM/DD/YYYY","DD/MM/YYYY"};
static const char* const kTimeFormatOpts[] = {"12-Hour Clock","24-Hour Clock"};
static const char* const kOffOnOpts[]      = {"Off","On"};

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

    // Date and Time display formats (nano-local; the clock honours them). These
    // have non-zero defaults (DD/MM/YYYY, 24-Hour) so they can't use rd().
    property_get("persist.gammaos.nano.datetime.date_format", buf, "2");
    mPs3DateFormatIdx = atoi(buf); if (mPs3DateFormatIdx < 0 || mPs3DateFormatIdx > 2) mPs3DateFormatIdx = 2;
    property_get("persist.gammaos.nano.datetime.time_format", buf, "1");
    mPs3TimeFormatIdx = atoi(buf); if (mPs3TimeFormatIdx < 0 || mPs3TimeFormatIdx > 1) mPs3TimeFormatIdx = 1;
    // Daylight Saving reflects the real current DST state. Seed it from the local
    // time now so the menu row reads correctly before the first clock frame;
    // drawPs3Clock refreshes it every frame thereafter.
    { time_t tt = time(nullptr); struct tm lt; localtime_r(&tt, &lt); mPs3DstNow = (lt.tm_isdst > 0); }
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
    } else if (n == "Internet Connection") {
        // Cached radio state (refreshed by the HUD net poll + on toggle); never a
        // live `cmd wifi` shell-out here - this runs every frame in drawList.
        return mWifiRadioOn ? "Enabled" : "Disabled";
    } else if (n == "Time Zone") {
        // The currently selected zone's "GMT+hh:mm City" label (set on open + on
        // apply). mTzEntries is built at initPs3Menu so this reads even before the
        // globe is opened. No per-frame property_get.
        if (!mTzEntries.empty() && mTzSelected >= 0 && mTzSelected < (int)mTzEntries.size())
            return mTzEntries[mTzSelected].display;
    } else if (n == "Date Format") {
        if (mPs3DateFormatIdx >= 0 && mPs3DateFormatIdx < 3) return kDateFormatOpts[mPs3DateFormatIdx];
    } else if (n == "Time Format") {
        if (mPs3TimeFormatIdx >= 0 && mPs3TimeFormatIdx < 2) return kTimeFormatOpts[mPs3TimeFormatIdx];
    } else if (n == "Daylight Saving") {
        return mPs3DstNow ? "On" : "Off";
    }
    return it.value;
}

// ---------------------------------------------------------------------------
// Fullscreen dialog page templates. 1:1 port of the web DIALOG_TEMPLATES (the
// non-panel entries; the Theme Settings choosers stay as side panels, kind 1).
// type: 0 info, 1 chooser, 2 chooser_illust, 3 confirm.
// illust: 0 none,1 hdmi_cable,2 av_multi,3 hdd_warning,4 globe,5 controller,6 bd_remote.
// ---------------------------------------------------------------------------
struct Ps3DlgTemplate {
    const char* name;
    int         type;
    const char* title;
    const char* body;
    const char* options[4];   // nullptr-terminated; max 4
    int         illust;
    const char* notice;
    int         defaultSel;
};
static const Ps3DlgTemplate kPs3DlgTemplates[] = {
  {"Audio Output Settings",2,"Audio Output Settings",
   "Select the connector on the TV or AV amplifier (receiver).",
   {"HDMI","Optical Digital","Audio Input Connector / SCART / AV MULTI",nullptr},1,
   "Turn on the power of the connected device.",0},
  {"Video Output Settings",2,"Video Output Settings",
   "Select the connector on the TV.",
   {"HDMI","Component / D-Terminal","AV MULTI / SCART","AV MULTI / S Video"},1,
   "Turn on the power of the connected device.",0},
  {"System Update",1,"System Update","Select an update method.",
   {"Update via Internet","Update via Storage Media",nullptr,nullptr},0,nullptr,0},
  {"System Information",0,"System Information",
   "System Software\nVersion 4.91\n\nMAC Address (Wired)\n00:1F:A7:00:00:00\n\nMAC Address (Wi-Fi)\n00:1F:A7:00:00:01\n\nIP Address\n192.168.1.10\n\nSystem Storage\n466 GB free of 500 GB",
   {nullptr,nullptr,nullptr,nullptr},0,nullptr,0},
  {"Format Utility",1,"Format Utility",
   "Formats the system storage. All data on the system storage will be deleted during formatting.\nSelect an option.",
   {"Format System Storage","Cancel",nullptr,nullptr},0,nullptr,0},
  {"Format Hard Disk",3,"Format Hard Disk",
   "If you format, all data on the system storage will be deleted.\nThis data cannot be restored.\nAre you sure you want to continue?",
   {nullptr,nullptr,nullptr,nullptr},3,nullptr,1},
  {"Restore PS3™ System",3,"Restore PS3™ System",
   "Formats the system storage and restores the system software to its default settings.\n\nAll data on the hard disk will be deleted, and the system software will be reinstalled.\nDo you want to continue?",
   {nullptr,nullptr,nullptr,nullptr},3,nullptr,1},
  {"Restore Default Settings",3,"Restore Default Settings",
   "Restores the system software to its default settings.\n\nAll system settings will be restored to their default values.\nDo you want to continue?",
   {nullptr,nullptr,nullptr,nullptr},0,nullptr,1},
  {"Backup Utility",1,"Backup Utility",
   "Backs up data saved on the system storage to storage media, or restores data that has been backed up.\nSelect an option.",
   {"Back Up","Restore","Delete Backup Data",nullptr},0,nullptr,0},
  {"Data Transfer Utility",1,"Data Transfer Utility",
   "Transfers data that is saved on one PS3™ system to another PS3™ system. You can use this feature when replacing the PS3™ system that you usually use with another PS3™ system.",
   {"1. Transfer data from this system to the other PS3™ system.",
    "2. Transfer data from the other PS3™ system to this system.","Cancel",nullptr},0,nullptr,0},
  {"Date and Time",1,"Date and Time",
   "Sets the date and time for this system.\nSelect how to set the date and time.",
   {"Set via Internet","Set Manually",nullptr,nullptr},0,nullptr,0},
  {"Set via Internet",0,"Set via Internet",
   "Obtains the correct date and time automatically via the Internet when you sign in to PSN, and sets them on your system.\n\nA network connection is required for this feature.",
   {nullptr,nullptr,nullptr,nullptr},4,nullptr,0},
  {"Set Manually",0,"Set Manually",
   "Set the time and date.\n\nUse the arrow keys to adjust each field, then press the X button to apply.",
   {nullptr,nullptr,nullptr,nullptr},0,nullptr,0},
  {"Calibrate Motion Controller",0,"Calibrate Motion Controller",
   "Calibrates the magnetic sensor of a motion controller. Use this setting when the motion controller does not control on-screen movement as expected.\n\nPlace the controller on a flat surface, then press the X button.",
   {nullptr,nullptr,nullptr,nullptr},5,nullptr,0},
  {"Reassign Controllers",0,"Reassign Controllers",
   "Change the number assigned to the controller that is currently in use.\n\nPress the PS button on the controller you want to reassign.",
   {nullptr,nullptr,nullptr,nullptr},5,nullptr,0},
  {"BD Remote Control Registration",0,"BD Remote Control Registration",
   "Register a BD remote control for use with the PS3™ system.\n\nPress the START button and ENTER button of the BD remote control you want to register at the same time, and hold down until the screen changes.",
   {nullptr,nullptr,nullptr,nullptr},6,nullptr,0},
  {"Manage Bluetooth® Devices",1,"Manage Bluetooth® Devices",
   "Register or manage Bluetooth® devices such as headsets, keyboards and mouse devices.\nSelect an option.",
   {"Register New Device","Device List","Disconnect All","Unregister All"},0,nullptr,0},
  {"Camera Device Settings",0,"Camera Device Settings",
   "Tests the image from a camera that is connected to the system using a USB cable. You can adjust settings to reduce flickering for some cameras.\n\nNo camera is currently connected.",
   {nullptr,nullptr,nullptr,nullptr},0,nullptr,0},
  {"Audio Device Settings",1,"Audio Device Settings",
   "Sets the audio input and output devices for voice/video chat and other communication features.\nSelect an audio device.",
   {"System Default","USB Headset","Bluetooth® Headset",nullptr},0,nullptr,0},
  {"Wireless Stereo Headset Settings",0,"Wireless Stereo Headset Settings",
   "No wireless stereo headset is connected.",{nullptr,nullptr,nullptr,nullptr},0,nullptr,0},
  {"Change Password",0,"Change Password",
   "Change the password required to play games or videos, or to use the Internet browser.\n\nEnter your current password.",
   {nullptr,nullptr,nullptr,nullptr},0,nullptr,0},
  {"Register Device",0,"Register Device",
   "Register a device (such as a PS Vita or a PSP™ system) to be used for remote play with this system. Select this option to register a device for remote play if using it for the first time.\n\nEnter the following number on the remote device:\n\n12345678",
   {nullptr,nullptr,nullptr,nullptr},4,nullptr,0},
  {"Status of Registered Devices",0,"Status of Registered Devices",
   "Displays a list of devices registered with this system.\n\nNo devices are currently registered for remote play.",
   {nullptr,nullptr,nullptr,nullptr},0,nullptr,0},
  {"Delete Registered Device",1,"Delete Registered Device",
   "Deletes registered remote play devices from this system.\nSelect a device to delete.",
   {"(No registered devices)",nullptr,nullptr,nullptr},0,nullptr,0},
  {"Register PlayStation®Vita",0,"Register PlayStation®Vita",
   "Follow the on-screen instructions on your PlayStation®Vita system.",
   {nullptr,nullptr,nullptr,nullptr},0,nullptr,0},
  {"Settings and Connection Status List",0,"Settings and Connection Status List",
   "Displays current network settings and the Internet connection status.\n\nConnection Name    Default\nConnection Type    Wired\nIP Address         192.168.1.10\nSubnet Mask        255.255.255.0\nDefault Gateway    192.168.1.1\nPrimary DNS        8.8.8.8\nSecondary DNS      8.8.4.4",
   {nullptr,nullptr,nullptr,nullptr},0,nullptr,0},
  {"Internet Connection Settings",1,"Internet Connection Settings",
   "Sets the method for connecting the system to the Internet.\nSelect a connection method.",
   {"Easy","Custom",nullptr,nullptr},4,nullptr,0},
  {"Internet Connection Test",0,"Internet Connection Test",
   "Tests the Internet connection and displays the results.\n\nObtain IP Address    Succeeded\nInternet Connection  Succeeded\nPlayStation™Network  Succeeded\nNAT Type             Type 2\nUPnP                 Available\nConnection Speed (Download)  85.4 Mbps\nConnection Speed (Upload)    24.7 Mbps",
   {nullptr,nullptr,nullptr,nullptr},0,nullptr,0},
  {"Add/Edit Term",1,"Add/Edit Term",
   "Add words to be displayed as options for predictive text entry when using the on-screen keyboard.\nSelect an option.",
   {"Add Term","Edit Term","Delete Term",nullptr},0,nullptr,0},
  {"Delete Predictive Text Dictionary",3,"Delete Predictive Text Dictionary",
   "Deletes words that were added automatically to the dictionary when using the on-screen keyboard.\n\nThe predictive text dictionary will be deleted.\nAre you sure you want to continue?",
   {nullptr,nullptr,nullptr,nullptr},0,nullptr,1},
  {"DivX® VOD Registration Code",0,"DivX® VOD Registration Code",
   "Displays the registration code that is required to play DivX® VOD content.\n\nRegistration Code:\n\n8C9D7E1F\n\nVisit http://www.divx.com/vod/ for instructions on how to register the code.",
   {nullptr,nullptr,nullptr,nullptr},0,nullptr,0},
  {"List of Registered PS Vita Systems",0,"List of Registered PS Vita Systems",
   "Displays a list of PS Vita systems registered with this system.\n\nNo PS Vita systems are currently registered.",
   {nullptr,nullptr,nullptr,nullptr},0,nullptr,0},
  {"Delete PS Vita System's Backup Files",3,"Delete PS Vita System's Backup Files",
   "Deletes backup files for the PS Vita system saved on this system.\n\nThe backup files for the PS Vita system will be deleted.\nYou can only delete the backup files for your account.\n\nDo you want to delete the backup files?",
   {nullptr,nullptr,nullptr,nullptr},0,nullptr,1},
};

// --- low-level dialog draw primitives (device-px space) -------------------
void NanoMenu::ps3ThickLine(float x0, float y0, float x1, float y1, float w,
                            float r, float g, float b, float a) {
    float dx = x1 - x0, dy = y1 - y0;
    float len = sqrtf(dx * dx + dy * dy);
    if (len < 1e-3f) return;
    float nx = -dy / len * (w * 0.5f), ny = dx / len * (w * 0.5f);
    drawTriangle(x0 + nx, y0 + ny, x0 - nx, y0 - ny, x1 - nx, y1 - ny, r, g, b, a);
    drawTriangle(x0 + nx, y0 + ny, x1 - nx, y1 - ny, x1 + nx, y1 + ny, r, g, b, a);
}
void NanoMenu::ps3FillCircle(float cx, float cy, float rad, float r, float g, float b, float a) {
    const int N = 28;
    float px = cx + rad, py = cy;
    for (int i = 1; i <= N; i++) {
        float t = (float)i / (float)N * 2.0f * (float)M_PI;
        float x = cx + cosf(t) * rad, y = cy + sinf(t) * rad;
        drawTriangle(cx, cy, px, py, x, y, r, g, b, a);
        px = x; py = y;
    }
}
void NanoMenu::ps3StrokeRing(float cx, float cy, float radX, float radY, float lw,
                             float r, float g, float b, float a) {
    const int N = 44;
    float px = cx + radX, py = cy;
    for (int i = 1; i <= N; i++) {
        float t = (float)i / (float)N * 2.0f * (float)M_PI;
        float x = cx + cosf(t) * radX, y = cy + sinf(t) * radY;
        ps3ThickLine(px, py, x, y, lw, r, g, b, a);
        px = x; py = y;
    }
}
void NanoMenu::ps3VGradRect(float x, float y, float w, float h,
                            float r0, float g0, float b0, float r1, float g1, float b1, float a) {
    const int N = 6;
    for (int i = 0; i < N; i++) {
        float t = ((float)i + 0.5f) / (float)N;
        drawQuad(x, y + (float)i / (float)N * h, w, h / (float)N + 1.0f,
                 r0 + (r1 - r0) * t, g0 + (g1 - g0) * t, b0 + (b1 - b0) * t, a);
    }
}

// Centred/left/right dialog text with a subtle panel-down drop shadow (web:
// shadowOffsetY 1, blur 3, alpha 0.5). align: 0 left, 1 centre, 2 right.
void NanoMenu::ps3DlgText(const char* s, float cxDev, float baselineDev, float fs,
                          float r, float g, float b, float a, int align) {
    if (!s || !*s) return;
    float w = measureText(s, fs);
    float x = (align == 1) ? (cxDev - w * 0.5f) : (align == 2) ? (cxDev - w) : cxDev;
    float topY = ps3::baselineToTopY(baselineDev, fs);
    drawText(s, x, topY, fs, r, g, b, a);   // even outline via drawText mode 1
}

// One selectable dialog option. Selected = larger with a soft white halo bloom
// (web shadowBlur halo); unselected = smaller, dim, with a legibility shadow.
// midDev is the vertical CENTRE of the text (web uses textBaseline='middle').
void NanoMenu::ps3DlgOption(const char* label, float cxDev, float midDev,
                            bool sel, bool leftAlign, float ap, float baseScale) {
    if (!label || !*label) return;
    float fs = baseScale * (sel ? 28.0f : 24.0f) / 16.0f;
    float w = measureText(label, fs);
    float x = leftAlign ? cxDev : (cxDev - w * 0.5f);
    float topY = midDev - 0.45f * 16.0f * fs;
    if (sel) {
        // Soft white halo bloom (no dark outline on the bright copies -> mode 2).
        int savedMode = mTextOutlineMode; mTextOutlineMode = 2;
        float em = 16.0f * fs;
        const float gr[2] = { em * 0.30f, em * 0.16f };
        for (int pass = 0; pass < 2; pass++) {
            float gg = gr[pass];
            for (int k = 0; k < 8; k++) {
                float ang = (float)k / 8.0f * 2.0f * (float)M_PI;
                drawText(label, x + cosf(ang) * gg, topY + sinf(ang) * gg, fs, 1.0f, 1.0f, 1.0f, 0.12f * ap);
            }
        }
        drawText(label, x, topY, fs, 1.0f, 1.0f, 1.0f, ap);
        mTextOutlineMode = savedMode;
    } else {
        // Even outline comes from drawText (mode 1) - one batched draw call.
        drawText(label, x, topY, fs, 1.0f, 1.0f, 1.0f, 0.85f * ap);
    }
}

// Footer button hint: glyph (X cross or O circle) + label, centred on slotCxDev.
void NanoMenu::ps3DlgHint(float slotCxDev, bool cross, const char* label,
                          float yDev, float baseScale, float ap) {
    float fs = baseScale * 22.0f / 16.0f;
    float glyphR = baseScale * 12.0f;
    float gap = baseScale * 12.0f;
    float lw = fmaxf(baseScale * 2.0f, 1.5f);
    float tw = measureText(label, fs);
    float groupW = glyphR * 2.0f + gap + tw;
    float left = slotCxDev - groupW * 0.5f;
    float gcx = left + glyphR;
    if (cross) {
        float d = glyphR * 0.78f;
        ps3ThickLine(gcx - d, yDev - d, gcx + d, yDev + d, lw, 1.0f, 1.0f, 1.0f, 0.95f * ap);
        ps3ThickLine(gcx + d, yDev - d, gcx - d, yDev + d, lw, 1.0f, 1.0f, 1.0f, 0.95f * ap);
    } else {
        ps3StrokeRing(gcx, yDev, glyphR, glyphR, lw, 1.0f, 1.0f, 1.0f, 0.95f * ap);
    }
    float topY = yDev - 0.45f * 16.0f * fs;
    drawText(label, left + glyphR * 2.0f + gap, topY, fs, 1.0f, 1.0f, 1.0f, 0.95f * ap);
}

// PS3 "diagram-style" vector illustrations (1:1 with web drawIllustration),
// drawn centred at (cx,cy) at approximate size sz, all in device px.
void NanoMenu::ps3DlgIllustration(int kind, float cx, float cy, float sz, float ap) {
    if (kind == 1) {                                  // hdmi_cable
        float h = sz, w = h * 0.30f, top = cy - h * 0.5f;
        float rectW = w * 0.95f, rectH = h * 0.060f, rx = cx - rectW * 0.5f, ry = top;
        drawQuad(rx - 2.0f, ry, rectW + 4.0f, rectH, 0.73f, 0.73f, 0.73f, ap);
        drawQuad(rx + rectW * 0.06f, ry + rectH * 0.18f, rectW * 0.88f, rectH * 0.55f, 0.04f, 0.04f, 0.04f, ap);
        for (int i = 0; i < 5; i++)
            drawQuad(rx + rectW * 0.12f + i * (rectW * 0.76f / 5.0f), ry + rectH * 0.25f, 1.5f, rectH * 0.4f, 0.60f, 0.60f, 0.60f, ap);
        float tongueW = w * 0.78f, tongueH = h * 0.10f, tx = cx - tongueW * 0.5f, ty = top + h * 0.19f;
        drawTriangle(tx, ty, tx + tongueW, ty, tx + tongueW - 3.0f, ty + tongueH, 0.79f, 0.64f, 0.25f, ap);
        drawTriangle(tx, ty, tx + tongueW - 3.0f, ty + tongueH, tx + 3.0f, ty + tongueH, 0.79f, 0.64f, 0.25f, ap);
        float bodyW = w, bodyH = h * 0.39f, bx = cx - bodyW * 0.5f, by = ty + tongueH;
        ps3VGradRect(bx, by, bodyW, bodyH, 0.10f, 0.10f, 0.10f, 0.23f, 0.23f, 0.23f, ap);
        drawQuad(bx + bodyW * 0.45f, by + 4.0f, bodyW * 0.10f, bodyH - 8.0f, 1.0f, 1.0f, 1.0f, 0.06f * ap);
        float sw = bodyW * 0.55f, sh = h * 0.12f, sx = cx - sw * 0.5f, syy = by + bodyH;
        drawTriangle(sx, syy, sx + sw, syy, sx + sw - 6.0f, syy + sh, 0.10f, 0.10f, 0.10f, ap);
        drawTriangle(sx, syy, sx + sw - 6.0f, syy + sh, sx + 6.0f, syy + sh, 0.10f, 0.10f, 0.10f, ap);
        float cw = sw * 0.55f, cy2 = syy + sh;
        drawQuad(cx - cw * 0.5f, cy2, cw, h * 0.18f, 0.03f, 0.03f, 0.03f, ap);
    } else if (kind == 2) {                           // av_multi
        float w = sz * 0.45f, top = cy - sz * 0.5f;
        drawQuad(cx - w * 0.5f, top, w, 4.0f, 1.0f, 1.0f, 1.0f, ap);
        drawQuad(cx - w * 0.45f, top + 1.0f, w * 0.9f, 2.0f, 0.0f, 0.0f, 0.0f, ap);
        float pinY = top + 30.0f;
        drawQuad(cx - w * 0.4f, pinY, w * 0.8f, 8.0f, 0.83f, 0.66f, 0.22f, ap);
        float bodyY = pinY + 10.0f;
        drawTriangle(cx - w * 0.55f, bodyY, cx + w * 0.55f, bodyY, cx + w * 0.5f, bodyY + sz * 0.45f, 0.04f, 0.04f, 0.04f, ap);
        drawTriangle(cx - w * 0.55f, bodyY, cx + w * 0.5f, bodyY + sz * 0.45f, cx - w * 0.5f, bodyY + sz * 0.45f, 0.04f, 0.04f, 0.04f, ap);
        drawQuad(cx - w * 0.15f, bodyY + sz * 0.45f, w * 0.3f, sz * 0.25f, 0.04f, 0.04f, 0.04f, ap);
    } else if (kind == 3) {                           // hdd_warning
        float sw = sz * 0.72f, sh = sz * 0.46f, dx = cx - sw * 0.5f, dy = cy - sh * 0.55f, skew = sw * 0.08f;
        drawTriangle(dx + skew, dy, dx + sw + skew, dy, dx + sw, dy + sh * 0.16f, 0.72f, 0.72f, 0.79f, ap);
        drawTriangle(dx + skew, dy, dx + sw, dy + sh * 0.16f, dx, dy + sh * 0.16f, 0.72f, 0.72f, 0.79f, ap);
        ps3VGradRect(dx, dy + sh * 0.16f, sw, sh * 0.84f, 0.36f, 0.38f, 0.41f, 0.17f, 0.18f, 0.20f, ap);
        drawTriangle(dx + sw, dy + sh * 0.16f, dx + sw + skew, dy, dx + sw + skew, dy + sh * 0.84f, 0.23f, 0.24f, 0.26f, ap);
        drawTriangle(dx + sw, dy + sh * 0.16f, dx + sw + skew, dy + sh * 0.84f, dx + sw, dy + sh, 0.23f, 0.24f, 0.26f, ap);
        for (int s = 0; s < 4; s++)
            ps3FillCircle(dx + sw * 0.08f + s * sw * 0.27f, dy + sh * 0.85f, 3.0f, 0.10f, 0.11f, 0.12f, ap);
        drawQuad(dx + sw * 0.1f, dy + sh * 0.28f, sw * 0.65f, sh * 0.32f, 0.53f, 0.54f, 0.56f, ap);
        drawQuad(dx + sw * 0.15f, dy + sh * 0.36f, sw * 0.45f, 2.0f, 0.23f, 0.24f, 0.26f, ap);
        drawQuad(dx + sw * 0.15f, dy + sh * 0.45f, sw * 0.35f, 2.0f, 0.23f, 0.24f, 0.26f, ap);
        float tr = sz * 0.20f, tcx = cx + sz * 0.30f, tcy = cy + sz * 0.20f;
        drawTriangle(tcx, tcy - tr, tcx + tr * 0.92f, tcy + tr * 0.55f, tcx - tr * 0.92f, tcy + tr * 0.55f, 0.95f, 0.74f, 0.18f, ap);
        ps3ThickLine(tcx, tcy - tr, tcx + tr * 0.92f, tcy + tr * 0.55f, 3.0f, 0.10f, 0.10f, 0.10f, ap);
        ps3ThickLine(tcx + tr * 0.92f, tcy + tr * 0.55f, tcx - tr * 0.92f, tcy + tr * 0.55f, 3.0f, 0.10f, 0.10f, 0.10f, ap);
        ps3ThickLine(tcx - tr * 0.92f, tcy + tr * 0.55f, tcx, tcy - tr, 3.0f, 0.10f, 0.10f, 0.10f, ap);
        drawQuad(tcx - 3.0f, tcy - tr * 0.45f, 6.0f, tr * 0.65f, 0.10f, 0.10f, 0.10f, ap);
        ps3FillCircle(tcx, tcy + tr * 0.35f, 4.0f, 0.10f, 0.10f, 0.10f, ap);
    } else if (kind == 4) {                           // globe
        float r = sz * 0.32f;
        ps3StrokeRing(cx, cy, r, r, 2.0f, 0.61f, 0.77f, 1.0f, ap);
        ps3StrokeRing(cx, cy, r, r * 0.25f, 2.0f, 0.61f, 0.77f, 1.0f, ap);
        for (int i = 0; i < 3; i++)
            ps3StrokeRing(cx, cy, r * (0.35f + i * 0.32f), r, 2.0f, 0.61f, 0.77f, 1.0f, ap);
    } else if (kind == 5) {                           // controller (DualShock silhouette)
        float w = sz * 0.85f, h = w * 0.55f, x0 = cx - w * 0.5f, y0 = cy - h * 0.5f;
        // body: centre block + two rounded grip lobes
        drawRoundedRect(x0 + w * 0.18f, y0 + h * 0.05f, w * 0.64f, h * 0.70f, h * 0.18f, 0.78f, 0.79f, 0.83f, ap);
        ps3FillCircle(x0 + w * 0.16f, y0 + h * 0.62f, h * 0.30f, 0.74f, 0.75f, 0.80f, ap);
        ps3FillCircle(x0 + w * 0.84f, y0 + h * 0.62f, h * 0.30f, 0.74f, 0.75f, 0.80f, ap);
        // d-pad
        float dpx = x0 + w * 0.22f, dpy = y0 + h * 0.42f, dps = h * 0.13f;
        drawQuad(dpx - dps * 0.18f, dpy - dps * 0.55f, dps * 0.36f, dps * 1.1f, 0.23f, 0.23f, 0.25f, ap);
        drawQuad(dpx - dps * 0.55f, dpy - dps * 0.18f, dps * 1.1f, dps * 0.36f, 0.23f, 0.23f, 0.25f, ap);
        // face buttons
        float rcx = x0 + w * 0.78f, rcy = y0 + h * 0.42f, rs = h * 0.11f;
        ps3StrokeRing(rcx, rcy - rs * 0.9f, rs * 0.32f, rs * 0.32f, 2.0f, 0.23f, 0.23f, 0.25f, ap);
        ps3StrokeRing(rcx + rs * 0.9f, rcy, rs * 0.32f, rs * 0.32f, 2.0f, 0.23f, 0.23f, 0.25f, ap);
        ps3StrokeRing(rcx - rs * 0.9f, rcy, rs * 0.32f, rs * 0.32f, 2.0f, 0.23f, 0.23f, 0.25f, ap);
        ps3StrokeRing(rcx, rcy + rs * 0.9f, rs * 0.32f, rs * 0.32f, 2.0f, 0.23f, 0.23f, 0.25f, ap);
        // sticks + PS button
        ps3FillCircle(x0 + w * 0.36f, y0 + h * 0.70f, h * 0.10f, 0.11f, 0.11f, 0.13f, ap);
        ps3FillCircle(x0 + w * 0.64f, y0 + h * 0.70f, h * 0.10f, 0.11f, 0.11f, 0.13f, ap);
        ps3FillCircle(cx, y0 + h * 0.60f, h * 0.06f, 0.23f, 0.23f, 0.25f, ap);
    } else if (kind == 6) {                           // bd_remote
        float w = sz * 0.32f, h = sz * 0.95f, x0 = cx - w * 0.5f, y0 = cy - h * 0.5f;
        ps3VGradRect(x0, y0, w, h, 0.83f, 0.83f, 0.85f, 0.48f, 0.48f, 0.51f, ap);
        drawQuad(x0 + w * 0.30f, y0 + h * 0.03f, w * 0.40f, h * 0.04f, 0.10f, 0.10f, 0.12f, ap);
        ps3FillCircle(cx, y0 + h * 0.18f, w * 0.34f, 0.62f, 0.62f, 0.64f, ap);
        ps3FillCircle(cx, y0 + h * 0.18f, w * 0.18f, 0.23f, 0.23f, 0.25f, ap);
        for (int row = 0; row < 4; row++)
            for (int col = 0; col < 3; col++)
                ps3FillCircle(x0 + w * (0.22f + col * 0.28f), y0 + h * (0.36f + row * 0.10f), w * 0.07f, 0.23f, 0.23f, 0.25f, ap);
        drawQuad(x0 + w * 0.10f, y0 + h * 0.82f, w * 0.35f, h * 0.07f, 0.99f, 0.88f, 0.29f, ap);
        drawQuad(x0 + w * 0.55f, y0 + h * 0.82f, w * 0.35f, h * 0.07f, 0.99f, 0.88f, 0.29f, ap);
    }
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
    } else if (n == "Internet Connection") {
        // Side-panel Enabled/Disabled toggle for the Wi-Fi radio (route key 6).
        // Applied on commit only (no live toggle while scrolling).
        mPs3DlgKind = 1; mPs3DlgThemeKey = 6;
        mPs3DlgOptions.push_back("Enabled");  mPs3DlgSwatch.push_back(-1);
        mPs3DlgOptions.push_back("Disabled"); mPs3DlgSwatch.push_back(-1);
        mPs3DlgSel = wifiRadioEnabled() ? 0 : 1;
    } else if (n == "Date Format") {
        mPs3DlgKind = 1; mPs3DlgThemeKey = 7;
        for (const char* s : kDateFormatOpts) { mPs3DlgOptions.push_back(s); mPs3DlgSwatch.push_back(-1); }
        mPs3DlgSel = mPs3DateFormatIdx;
    } else if (n == "Time Format") {
        mPs3DlgKind = 1; mPs3DlgThemeKey = 8;
        for (const char* s : kTimeFormatOpts) { mPs3DlgOptions.push_back(s); mPs3DlgSwatch.push_back(-1); }
        mPs3DlgSel = mPs3TimeFormatIdx;
    } else if (n == "Daylight Saving") {
        // Pre-select the current real DST state (live from the clock).
        mPs3DlgKind = 1; mPs3DlgThemeKey = 9;
        for (const char* s : kOffOnOpts) { mPs3DlgOptions.push_back(s); mPs3DlgSwatch.push_back(-1); }
        mPs3DlgSel = mPs3DstNow ? 1 : 0;
    } else {
        // Fullscreen dialog page (kind 0): look up the 1:1 web template.
        mPs3DlgType = 0; mPs3DlgIllust = 0; mPs3DlgNotice.clear();
        const Ps3DlgTemplate* tpl = nullptr;
        for (const Ps3DlgTemplate& t : kPs3DlgTemplates) {
            if (n == t.name) { tpl = &t; break; }
        }
        if (tpl) {
            mPs3DlgType = tpl->type;
            mPs3DlgTitle = tpl->title;
            mPs3DlgBody = tpl->body ? tpl->body : "";
            mPs3DlgIllust = tpl->illust;
            if (tpl->notice) mPs3DlgNotice = tpl->notice;
            for (const char* o : tpl->options) { if (!o) break; mPs3DlgOptions.push_back(o); }
            mPs3DlgSel = tpl->defaultSel;
        } else {
            // Unknown action='dialog' leaf -> generic info page (matches web fallback).
            mPs3DlgType = 0;
            mPs3DlgBody = "This feature is not yet implemented.";
            mPs3DlgSel = 0;
        }
        // Header icon = the source item's icon (glass when available).
        mPs3DlgIconTex = it.iconTex; mPs3DlgIconNmap = it.nmapTex;
        mPs3DlgIconR = it.iconR; mPs3DlgIconG = it.iconG; mPs3DlgIconB = it.iconB;
        // Live-backed Network dialogs: replace the static placeholder body with the
        // real system state.
        mPs3NetTestLive = false;
        if (n == "Settings and Connection Status List") {
            mPs3DlgBody = buildNetStatusBody();          // real SSID/IP/gateway/DNS/MAC
        } else if (n == "Internet Connection Test") {
            startNetTest();                              // async; renderPs3Dialog shows live results
            mPs3NetTestLive = true;
        }
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
        // Date and Time choosers: pure live preview (the clock reads the members),
        // no syscalls until apply.
        case 7: mPs3DateFormatIdx = sel; break;
        case 8: mPs3TimeFormatIdx = sel; break;
        case 9: break;   // DST: no live preview; the row reflects the real clock, applied on commit
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
        case 6: toggleWifiRadio(sel == 0); break;   // Internet Connection: Enabled=0
        case 7:   // Date Format (nano-local display only)
            property_set("persist.gammaos.nano.datetime.date_format", v);
            previewThemeSetting(7, sel);
            break;
        case 8:   // Time Format -> also push to the framework so apps agree
            property_set("persist.gammaos.nano.datetime.time_format", v);
            { bool h12 = (sel == 0);
              std::thread([h12]{ system(h12 ? "settings put system time_12_24 12 2>/dev/null"
                                            : "settings put system time_12_24 24 2>/dev/null"); }).detach(); }
            previewThemeSetting(8, sel);
            break;
        case 9: {  // Daylight Saving: switch the timezone to apply or remove DST.
            // On  = the selected Olson zone (Europe/London etc.) -> the tz database
            //       applies DST automatically (BST in summer, +1h).
            // Off = a fixed standard-offset Etc/GMT zone -> no DST, so in DST season
            //       the displayed clock drops the extra hour. tm_isdst then reads 0
            //       and the menu row follows (next drawPs3Clock frame).
            // This is a manual tz override, so auto_time_zone is turned off; Set via
            // Internet re-enables it and the Olson tz re-captures DST automatically.
            bool on = (sel == 1);
            std::string olson, etc;
            if (!mTzEntries.empty() && mTzSelected >= 0 && mTzSelected < (int)mTzEntries.size()) {
                olson = mTzEntries[mTzSelected].id;
                int off = mTzEntries[mTzSelected].offsetMinutes;   // standard offset, minutes
                if (off % 60 == 0) {                               // Etc/GMT is whole-hours only
                    int h = off / 60;
                    char b[24];
                    if (h == 0) snprintf(b, sizeof(b), "Etc/GMT");
                    else        snprintf(b, sizeof(b), "Etc/GMT%+d", -h);   // sign is inverted in Etc/GMT
                    etc = b;
                }
            }
            if (!olson.empty()) {
                // For a half-hour zone with no clean Etc/GMT, Off falls back to the
                // Olson zone (those few zones keep automatic DST).
                std::string target = on ? olson : (etc.empty() ? olson : etc);
                std::thread([target]{
                    system("settings put global auto_time_zone 0 2>/dev/null");
                    std::string c = "cmd alarm set-timezone " + target + " 2>/dev/null";
                    system(c.c_str());
                }).detach();
            }
            break;
        }
        default: break;
    }
}

void NanoMenu::closePs3Dialog(bool apply) {
    if (mPs3DlgThemeKey > 0) {
        if (apply) applyThemeSetting(mPs3DlgThemeKey, mPs3DlgSel);
        else       previewThemeSetting(mPs3DlgThemeKey, mPs3DlgOrigSel);   // revert the live preview
    }
    if (mPs3NetTestLive) { stopNetTest(); mPs3NetTestLive = false; }
    mPs3DlgActive = false;
    mPs3DlgBlurValid = false;
}

void NanoMenu::renderPs3Dialog() {
    if (!mPs3DlgActive) return;
    // Internet Connection Test: pull the latest progressive results published by
    // the background test thread into the dialog body (main thread owns mPs3DlgBody).
    if (mPs3NetTestLive) {
        std::lock_guard<std::mutex> lk(mPs3NetTestMutex);
        mPs3DlgBody = mPs3NetTestBody;
    }
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
        float blurCad = (ps3bg::themeFading() || mPs3DlgKind == 1) ? 0.0f : 0.0667f;   // 60Hz during live preview
        bool due = !mPs3DlgBlurValid || (mEffectTime - mPs3DlgBlurT) >= blurCad;
        // The dialog frosted-WAVE backdrop only matches when the wave is the visible
        // background: home XMB always; overlay WALLPAPER/launcher only if the wallpaper
        // IS the wave (mCurrentEffect==22). Never in overlay scrim (the dimmed live app
        // must show) and never over a non-wave wallpaper (renderEffect's wallpaper, drawn
        // behind us, must show instead). (System Update has no OSK; the wizard
        // re-captures mPs3DlgBlurValid itself, so the wizard's OSK is unaffected.)
        const bool frostBg = !mOverlayMode || (mOverlayWallpaper && mCurrentEffect == 22);
        if (due && frostBg && captureGlassFromWave()) { mPs3DlgBlurValid = true; mPs3DlgBlurT = mEffectTime; }
        if (mPs3DlgBlurValid && frostBg)
            drawFrostedGlass(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, ap, /*waveSpace=*/true);  // pure blur, no darkening
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
        // ---- fullscreen dialog page (1:1 with web drawDialog) ----
        // Uniform translucent dim over the (already-drawn) blurred live wave so
        // the white chrome reads while the per-month gradient still shows through.
        // In the in-game overlay (scrim mode) the 90% scrim already darkens the
        // backdrop, so skip this extra dim to keep the dialog matching the top-level
        // scrim - the live app stays visible at the same level, not darker.
        if (!mOverlayMode || mOverlayWallpaper)
            drawQuad(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f, 0.0f, 0.0f, 0.40f * ap);
        // Dialog text outline: 50% transparent black, same as the menu. mode is 1.
        mTextOutlineRatio = 0.5f;

        // Base (ui-independent) virtual->device scale, with the 1080 design
        // CENTRED in the visible frame (web frameCenterTY). gFrame* are untouched
        // by the menu's uiScale zoom, so the dialog always fills/centres the frame
        // exactly like the web, adapting to any resolution/aspect/orientation.
        float ui = mPs3UiScale; if (ui < 0.5f) ui = 0.5f; if (ui > 2.0f) ui = 2.0f;
        const float S = ps3::gScale / ui;
        // Centre the (gFrameW/ui)-wide content band horizontally in the frame for any
    // UI zoom (mPs3UiScale defaults to 1.12). offY already recentres vertically;
    // without this, dialogs sit left-of-centre with an unbalanced right gap.
    const float offX = ps3::gFrameX + (ps3::gFrameW - S * ps3::XCF(ps3::VW)) * 0.5f;
        const float offY = ps3::gFrameY + ps3::gFrameH * 0.5f - S * (ps3::VH * 0.5f);
        auto X  = [&](float vx) { return S * vx + offX; };              // raw virtual x (left-anchored)
        auto XC = [&](float vx) { return S * ps3::XCF(vx) + offX; };    // XCF centred / frame-spanning
        auto Y  = [&](float vy) { return S * vy + offY; };
        auto DS = [&](float v)  { return S * v; };
        auto FS = [&](float px) { return S * px / 16.0f; };
        const float VW = ps3::VW;
        const float innerTop = 199.0f, innerBot = 880.0f;
        const float maxW = DS((VW - 400.0f) * ps3::LAYOUT_FIT);   // body wrap width (device px)

        // Word-wrap helper (mirrors web wrapLines: split '\n', keep blank lines,
        // greedy word-wrap each paragraph to maxW).
        auto wrap = [&](const std::string& body, float fs) {
            std::vector<std::string> out;
            std::string para;
            auto wrapPara = [&](const std::string& p) {
                if (p.empty()) { out.push_back(""); return; }
                std::string line, word;
                auto commit = [&]() {
                    if (word.empty()) return;
                    std::string trial = line.empty() ? word : line + " " + word;
                    if (!line.empty() && measureText(trial.c_str(), fs) > maxW) { out.push_back(line); line = word; }
                    else line = trial;
                    word.clear();
                };
                for (const char* q = p.c_str(); ; ++q) {
                    if (*q == ' ' || *q == '\0') { commit(); if (*q == '\0') break; }
                    else word.push_back(*q);
                }
                if (!line.empty()) out.push_back(line);
            };
            for (size_t i = 0; i <= body.size(); i++) {
                if (i == body.size() || body[i] == '\n') { wrapPara(para); para.clear(); }
                else para.push_back(body[i]);
            }
            return out;
        };

        // ---- header: item icon + title + top/bottom dividers ----
        float iconSz = DS(36.0f);
        if (mPs3DlgIconNmap && mIconGlassReady && ps3bg::workTex())
            drawGlassIcon(mPs3DlgIconNmap, X(130.0f) - iconSz * 0.5f, Y(175.0f) - iconSz * 0.5f, iconSz, iconSz,
                          mPs3DlgIconR, mPs3DlgIconG, mPs3DlgIconB, ap);
        else if (mPs3DlgIconTex)
            drawIconTex(mPs3DlgIconTex, X(130.0f) - iconSz * 0.5f, Y(175.0f) - iconSz * 0.5f, iconSz, iconSz,
                        mPs3DlgIconR, mPs3DlgIconG, mPs3DlgIconB, ap);
        ps3DlgText(mPs3DlgTitle.c_str(), X(160.0f), Y(187.0f), FS(28.0f), 1.0f, 1.0f, 1.0f, ap, 0);
        float divLw = fmaxf(1.0f, DS(1.0f));
        drawQuad(ps3::gFrameX, Y(innerTop), ps3::gFrameW, divLw, 1.0f, 1.0f, 1.0f, 0.55f * ap);
        drawQuad(ps3::gFrameX, Y(innerBot), ps3::gFrameW, divLw, 1.0f, 1.0f, 1.0f, 0.55f * ap);

        // ---- body by type ----
        int n = (int)mPs3DlgOptions.size();
        if (mPs3DlgType == 0) {                 // info
            float centerCY = (innerTop + innerBot) * 0.5f;
            if (mPs3DlgIllust) { ps3DlgIllustration(mPs3DlgIllust, XC(VW * 0.5f), Y(innerTop + 230.0f), DS(280.0f), ap); centerCY = innerTop + 460.0f; }
            float fs = FS(26.0f), lh = DS(36.0f);
            std::vector<std::string> lines = wrap(mPs3DlgBody, fs);
            float ty = Y(centerCY) - (float)((int)lines.size() - 1) * lh * 0.5f;
            for (auto& ln : lines) { if (!ln.empty()) ps3DlgText(ln.c_str(), XC(VW * 0.5f), ty, fs, 0.95f, 0.95f, 0.95f, ap, 1); ty += lh; }
        } else if (mPs3DlgType == 1) {          // chooser
            float fs = FS(24.0f);
            std::vector<std::string> bodyLines = wrap(mPs3DlgBody, fs);
            float by = Y(innerTop + 105.0f);
            for (auto& ln : bodyLines) { if (!ln.empty()) ps3DlgText(ln.c_str(), XC(VW * 0.5f), by, fs, 0.95f, 0.95f, 0.95f, ap, 1); by += DS(32.0f); }
            const float optTopV = innerTop + 305.0f, optSpacingV = 46.0f;
            float availH = innerBot - optTopV - 40.0f;
            int visibleCount = (int)(availH / optSpacingV); if (visibleCount < 3) visibleCount = 3;
            int firstVis = 0, lastVis = n - 1;
            if (n > visibleCount) {
                firstVis = mPs3DlgSel - visibleCount / 2;
                if (firstVis < 0) firstVis = 0;
                if (firstVis > n - visibleCount) firstVis = n - visibleCount;
                lastVis = firstVis + visibleCount - 1;
            }
            for (int i = firstVis; i <= lastVis; i++)
                ps3DlgOption(mPs3DlgOptions[i].c_str(), XC(VW * 0.5f), Y(optTopV + (i - firstVis) * optSpacingV),
                             i == mPs3DlgSel, false, ap, S);
            if (firstVis > 0)    ps3DlgText("▲", XC(VW * 0.5f), Y(optTopV - 18.0f), FS(20.0f), 1.0f, 1.0f, 1.0f, 0.5f * ap, 1);
            if (lastVis < n - 1) ps3DlgText("▼", XC(VW * 0.5f), Y(optTopV + visibleCount * optSpacingV + 6.0f), FS(20.0f), 1.0f, 1.0f, 1.0f, 0.5f * ap, 1);
        } else if (mPs3DlgType == 2) {          // chooser_illust
            float fs = FS(24.0f);
            std::vector<std::string> bodyLines = wrap(mPs3DlgBody, fs);
            float by = Y(innerTop + 115.0f);
            for (auto& ln : bodyLines) { if (!ln.empty()) ps3DlgText(ln.c_str(), XC(VW * 0.5f), by, fs, 0.95f, 0.95f, 0.95f, ap, 1); by += DS(32.0f); }
            float optX = XC(VW * 0.37f);
            const float optTopV = innerTop + 220.0f, optSpacingV = 42.0f;
            for (int i = 0; i < n; i++)
                ps3DlgOption(mPs3DlgOptions[i].c_str(), optX, Y(optTopV + i * optSpacingV), i == mPs3DlgSel, true, ap, S);
            float illYV = innerTop + 220.0f + (float)(n > 3 ? n : 3) * optSpacingV + 100.0f;
            if (mPs3DlgIllust) ps3DlgIllustration(mPs3DlgIllust, XC(VW * 0.5f), Y(illYV), DS(240.0f), ap);
            if (!mPs3DlgNotice.empty()) ps3DlgText(mPs3DlgNotice.c_str(), XC(VW * 0.5f), Y(innerBot - 22.0f), FS(22.0f), 1.0f, 1.0f, 1.0f, 0.85f * ap, 1);
        } else if (mPs3DlgType == 3) {          // confirm
            float fs = FS(26.0f), lh = DS(36.0f);
            float centerYV = (innerTop + innerBot) * 0.5f;
            if (mPs3DlgIllust) { ps3DlgIllustration(mPs3DlgIllust, XC(VW * 0.5f), Y(innerTop + 230.0f), DS(260.0f), ap); centerYV = innerTop + 460.0f; }
            std::vector<std::string> bodyLines = wrap(mPs3DlgBody, fs);
            float ty = Y(centerYV) - (float)((int)bodyLines.size() - 1) * lh * 0.5f - DS(50.0f);
            for (auto& ln : bodyLines) { if (!ln.empty()) ps3DlgText(ln.c_str(), XC(VW * 0.5f), ty, fs, 0.95f, 0.95f, 0.95f, ap, 1); ty += lh; }
            const char* labels[2] = { "Yes", "No" };
            float bxc = XC(VW * 0.5f) - DS(110.0f);
            float byv = ty + DS(30.0f);
            for (int i = 0; i < 2; i++)
                ps3DlgOption(labels[i], bxc + (float)i * DS(220.0f), byv, i == mPs3DlgSel, false, ap, S);
        }

        // ---- footer button hints ----
        float hintY = Y(909.0f);
        float enterCX = XC(VW * 0.401f), cancelCX = XC(VW * 0.629f);
        if (mPs3DlgType == 0) {
            ps3DlgHint(cancelCX, false, "OK", hintY, S, ap);
        } else if (mPs3DlgType == 1 || mPs3DlgType == 2) {
            if (!mPs3DlgNotice.empty() && mPs3DlgType == 2) {
                ps3DlgHint(cancelCX, false, "Cancel", hintY, S, ap);
            } else {
                ps3DlgHint(enterCX, true, "Enter", hintY, S, ap);
                ps3DlgHint(cancelCX, false, "Cancel", hintY, S, ap);
            }
        } else if (mPs3DlgType == 3) {
            ps3DlgHint(enterCX, true, "Enter", hintY, S, ap);
            ps3DlgHint(cancelCX, false, "Cancel", hintY, S, ap);
        }
    }
}

// ===========================================================================
// Internet Connection Settings wizard - a 1:1 port of the web NETCONF wireless
// flow, rendered with the same dialog chrome (blurred wave + dim, header, top/
// bottom dividers, footer hints) plus a horizontal slide, and backed by the real
// cmd-wifi scan/connect + the live connectivity test.
// ===========================================================================
namespace {
enum WizScr {
    WS_NONE = 0,
    WS_INTRO, WS_METHOD, WS_CONN,
    WS_EASY_WIRED_CHECK,                                  // Easy + Wired
    WS_WIRED_OPMODE, WS_WIRED_SPEED,                      // Custom + Wired
    WS_IP, WS_IP_ADDR, WS_IP_SUBNET, WS_IP_ROUTER, WS_IP_PDNS, WS_IP_SDNS,
    WS_PPPOE_USER, WS_PPPOE_PASS,
    WS_DHCP_HOST, WS_DHCP_HOST_ENTRY,
    WS_DNS, WS_DNS_PDNS, WS_DNS_SDNS,
    WS_MTU_Q, WS_MTU,
    WS_PROXY_Q, WS_PROXY_ADDR, WS_PROXY_PORT,
    WS_UPNP,
    WS_WLAN, WS_WLAN_AUTO, WS_AOSS_PROMPT, WS_AOSS_WAIT,
    WS_RAKU1, WS_RAKU2, WS_RAKU3, WS_AOSS_DONE,
    WS_SCANNING, WS_APLIST, WS_SSID, WS_SECURITY,
    WS_WEP_KEY, WS_WPA_KEY,
    WS_EAP_AUTH, WS_EAP_USER, WS_EAP_PASS,
    WS_EASY_ADV, WS_REVIEW, WS_SAVE,
    WS_TEST_CONFIRM, WS_TEST_RUN,
    // Date and Time flows (reuse the wizard UI): Set via Internet (NTP) and
    // Set Manually (OSK date + time entry).
    WS_DT_SVI_PROGRESS, WS_DT_SVI_DONE,
    WS_DT_SM_DATE, WS_DT_SM_TIME, WS_DT_SM_DONE,
    // Accessory Settings: Manage Bluetooth Devices (1:1 web bt_* flow, backed by
    // the real gammaos-net bt scan/pair/connect/disconnect/unpair), BD Remote
    // Control Registration, and Audio Device Settings.
    WS_BT_MANAGE, WS_BT_REGISTER_INFO, WS_BT_SCANNING, WS_BT_DEVICE_LIST,
    WS_BT_PASSKEY, WS_BT_REGISTERING, WS_BT_REGISTER_DONE,
    WS_BT_DEVICE_OPTS, WS_BT_CONNECTING, WS_BT_DISCONNECTING,
    WS_BT_DELETE_CONFIRM, WS_BT_DELETING, WS_BT_INFO,
    WS_BT_BD_REMOTE,
    WS_BT_INBOUND_WAIT, WS_BT_INBOUND_CONFIRM, WS_BT_INBOUND_PAIRING,
    WS_BT_AD_MENU, WS_BT_AD_INPUT, WS_BT_AD_OUTPUT, WS_BT_AD_MIC
};
// Bluetooth class-of-device -> human label for the "Type" column (1:1 with the
// web device list's Audio Device / Human Interface Device split, extended for the
// other major classes we may actually see).
static const char* btTypeLabel(int cod) {
    switch (cod & 0x1F00) {
        case 0x0400: return "Audio Device";
        case 0x0500: return "Human Interface Device";
        case 0x0100: return "Computer";
        case 0x0200: return "Phone";
        case 0x0600: return "Imaging Device";
        case 0x0700: return "Wearable";
        default:     return "Bluetooth Device";
    }
}
// HID devices (keyboards/mice, major class 0x0500) get the pass-key screen, like
// the web flow; audio devices pair directly.
static bool btNeedsPasskey(int cod) { return (cod & 0x1F00) == 0x0500; }
// The five WS_BT_* progress screens whose advance is gated on a background op
// completing (mBtWizBusy) rather than a fixed dwell.
static bool btIsBusyProgress(int id) {
    return id == WS_BT_SCANNING || id == WS_BT_REGISTERING ||
           id == WS_BT_CONNECTING || id == WS_BT_DISCONNECTING ||
           id == WS_BT_DELETING || id == WS_BT_INBOUND_PAIRING;
}
enum WizKind { WK_INFO, WK_CHOOSER, WK_CONFIRM, WK_SCANLIST, WK_PROGRESS,
               WK_TEXT, WK_REVIEW, WK_RESULT, WK_TEST };
enum WizField { WF_SSID = 0, WF_WEP_KEY, WF_WPA_KEY, WF_IP_ADDR, WF_IP_SUBNET,
                WF_IP_ROUTER, WF_IP_PDNS, WF_IP_SDNS, WF_DNS_PDNS, WF_DNS_SDNS,
                WF_MTU, WF_PROXY_ADDR, WF_PROXY_PORT, WF_PPPOE_USER, WF_PPPOE_PASS,
                WF_DHCP_HOST, WF_EAP_USER, WF_EAP_PASS,
                WF_DT_DATE, WF_DT_TIME, WF_BT_PIN };
struct WizDesc {
    int kind = WK_INFO;
    const char* title = "Internet Connection Settings";
    const char* body = "";
    const char* opts[8] = {nullptr};
    const char* label = "";   // text_entry field label
    int field = 0;            // WizField for text_entry
    bool mask = false;        // password mask
    int autoMs = 0;           // progress dwell
    int autoNext = WS_NONE;   // progress -> screen
};
// 1:1 with the web WIZ_SECURITY_OPTS (firmware str 228,112,113-117,121).
static const char* const WIZ_SEC_OPTS[] = {
    "None", "WEP", "WPA-PSK (TKIP)", "WPA-PSK (AES)",
    "WPA2-PSK (TKIP)", "WPA2-PSK (AES)", "WPA-PSK / WPA2-PSK",
    "EAP Authentication", nullptr };
static void wizDesc(int id, WizDesc& d) {
    d = WizDesc{};
    switch (id) {
    case WS_INTRO: d.kind = WK_INFO;
        d.body = "Adjust settings for connection to the Internet.\nIf making a wired connection, you must have an Ethernet cable connected."; break;
    case WS_METHOD: d.kind = WK_CHOOSER; d.body = "Select a setting method.";
        d.opts[0] = "Easy"; d.opts[1] = "Custom"; break;
    case WS_CONN: d.kind = WK_CHOOSER; d.body = "Select a connection method.";
        d.opts[0] = "Wired Connection"; d.opts[1] = "Wireless"; break;

    // ---- Wired connection (Easy: auto-detect; Custom: pick op-mode) ----
    case WS_EASY_WIRED_CHECK: d.kind = WK_PROGRESS;
        d.body = "Checking network configuration...\nPlease wait."; d.autoMs = 2600; d.autoNext = WS_EASY_ADV; break;
    case WS_WIRED_OPMODE: d.kind = WK_CHOOSER;
        d.body = "Select the operation mode of the network device.\nIn most cases, select Auto-Detect.";
        d.opts[0] = "Auto-Detect"; d.opts[1] = "Speed and Duplex"; break;
    case WS_WIRED_SPEED: d.kind = WK_CHOOSER;
        d.body = "Select the operation mode of the network device.";
        d.opts[0] = "10BASE-T Half-Duplex";   d.opts[1] = "10BASE-T Full-Duplex";
        d.opts[2] = "100BASE-TX Half-Duplex"; d.opts[3] = "100BASE-TX Full-Duplex";
        d.opts[4] = "1000BASE-T Half-Duplex"; d.opts[5] = "1000BASE-T Full-Duplex"; break;

    // ---- IP Address Setting ----
    case WS_IP: d.kind = WK_CHOOSER; d.body = "IP Address Setting";
        d.opts[0] = "Automatic"; d.opts[1] = "Manual"; d.opts[2] = "PPPoE"; break;
    case WS_IP_ADDR:   d.kind = WK_TEXT; d.title = "IP Address Setting"; d.label = "IP Address";     d.field = WF_IP_ADDR;   break;
    case WS_IP_SUBNET: d.kind = WK_TEXT; d.title = "IP Address Setting"; d.label = "Subnet Mask";    d.field = WF_IP_SUBNET; break;
    case WS_IP_ROUTER: d.kind = WK_TEXT; d.title = "IP Address Setting"; d.label = "Default Router"; d.field = WF_IP_ROUTER; break;
    case WS_IP_PDNS:   d.kind = WK_TEXT; d.title = "IP Address Setting"; d.label = "Primary DNS";    d.field = WF_IP_PDNS;   break;
    case WS_IP_SDNS:   d.kind = WK_TEXT; d.title = "IP Address Setting"; d.label = "Secondary DNS";  d.field = WF_IP_SDNS;   break;

    // ---- PPPoE ----
    case WS_PPPOE_USER: d.kind = WK_TEXT; d.title = "PPPoE"; d.label = "PPPoE User Name"; d.field = WF_PPPOE_USER; break;
    case WS_PPPOE_PASS: d.kind = WK_TEXT; d.title = "PPPoE"; d.label = "PPPoE Password";  d.field = WF_PPPOE_PASS; d.mask = true; break;

    // ---- DHCP host name (Automatic IP) ----
    case WS_DHCP_HOST: d.kind = WK_CHOOSER;
        d.body = "Set the DHCP host name.\nIn most cases this setting does not need to be changed.";
        d.opts[0] = "Do Not Set"; d.opts[1] = "Set"; break;
    case WS_DHCP_HOST_ENTRY: d.kind = WK_TEXT; d.title = "DHCP Host Name"; d.label = "DHCP Host Name"; d.field = WF_DHCP_HOST; break;

    // ---- DNS ----
    case WS_DNS: d.kind = WK_CHOOSER; d.body = "DNS Setting\nIn most cases, select [Automatic].";
        d.opts[0] = "Automatic"; d.opts[1] = "Manual"; break;
    case WS_DNS_PDNS: d.kind = WK_TEXT; d.title = "DNS Setting"; d.label = "Primary DNS";   d.field = WF_DNS_PDNS; break;
    case WS_DNS_SDNS: d.kind = WK_TEXT; d.title = "DNS Setting"; d.label = "Secondary DNS"; d.field = WF_DNS_SDNS; break;

    // ---- MTU / Proxy / UPnP ----
    case WS_MTU_Q: d.kind = WK_CHOOSER; d.body = "MTU"; d.opts[0] = "Automatic"; d.opts[1] = "Manual"; break;
    case WS_MTU: d.kind = WK_TEXT; d.title = "MTU"; d.label = "MTU"; d.field = WF_MTU; break;
    case WS_PROXY_Q: d.kind = WK_CHOOSER; d.body = "Proxy Server"; d.opts[0] = "Do Not Use"; d.opts[1] = "Use"; break;
    case WS_PROXY_ADDR: d.kind = WK_TEXT; d.title = "Proxy Server"; d.label = "Address";     d.field = WF_PROXY_ADDR; break;
    case WS_PROXY_PORT: d.kind = WK_TEXT; d.title = "Proxy Server"; d.label = "Port Number"; d.field = WF_PROXY_PORT; break;
    case WS_UPNP: d.kind = WK_CHOOSER; d.body = "UPnP"; d.opts[0] = "Enable"; d.opts[1] = "Disable"; break;

    // ---- WLAN ----
    case WS_WLAN: d.kind = WK_CHOOSER; d.title = "WLAN Settings"; d.body = "WLAN Settings";
        d.opts[0] = "Scan"; d.opts[1] = "Enter Manually"; d.opts[2] = "Automatic"; break;
    case WS_WLAN_AUTO: d.kind = WK_CHOOSER; d.title = "WLAN Settings";
        d.body = "Automatic setup by access point type.";
        d.opts[0] = "AOSS"; d.opts[1] = "Rakuraku WLAN Start"; break;
    case WS_AOSS_PROMPT: d.kind = WK_INFO; d.title = "AOSS";
        d.body = "Press and hold the \"AOSS Button\" of the access point until the AOSS indicator starts blinking.\nIf you don't press the button within 2 minutes, the operation will be canceled."; break;
    case WS_AOSS_WAIT: d.kind = WK_PROGRESS; d.title = "AOSS";
        d.body = "It will take about 10 seconds for the access point to be ready.\nPlease wait..."; d.autoMs = 4200; d.autoNext = WS_AOSS_DONE; break;
    case WS_RAKU1: d.kind = WK_PROGRESS; d.title = "Rakuraku WLAN Start";
        d.body = "Preparing...\nPlease wait."; d.autoMs = 1800; d.autoNext = WS_RAKU2; break;
    case WS_RAKU2: d.kind = WK_INFO; d.title = "Rakuraku WLAN Start";
        d.body = "Press and hold the \"Rakuraku Start Button\" of the access point until the \"POWER\" LED blinks in [Green].\nIf you don't press the button within 1 minute, the operation will be canceled."; break;
    case WS_RAKU3: d.kind = WK_INFO; d.title = "Rakuraku WLAN Start";
        d.body = "Press the \"Rakuraku Start Button\" again. Press and hold until the \"POWER\" LED turns [Orange].\nIf you don't press the button within 30 seconds, the operation will be canceled."; break;
    case WS_AOSS_DONE: d.kind = WK_RESULT; d.title = "WLAN Settings";
        d.body = "Save completed.\nIt may take 1 minute or longer to restart the access point."; break;

    case WS_SCANNING: d.kind = WK_PROGRESS; d.title = "WLAN Settings";
        d.body = "Scanning...\nPlease wait."; d.autoMs = 2500; d.autoNext = WS_APLIST; break;
    case WS_APLIST: d.kind = WK_SCANLIST; d.title = "WLAN Settings";
        d.body = "Select the access point to be used."; break;
    case WS_SSID: d.kind = WK_TEXT; d.title = "SSID"; d.label = "SSID"; d.field = WF_SSID; break;
    case WS_SECURITY: d.kind = WK_CHOOSER; d.title = "WLAN Security Setting"; d.body = "Security";
        for (int i = 0; WIZ_SEC_OPTS[i]; i++) d.opts[i] = WIZ_SEC_OPTS[i]; break;
    case WS_WEP_KEY: d.kind = WK_TEXT; d.title = "WLAN Security Setting"; d.label = "WEP Key"; d.field = WF_WEP_KEY; break;
    case WS_WPA_KEY: d.kind = WK_TEXT; d.title = "WLAN Security Setting"; d.label = "WPA Key"; d.field = WF_WPA_KEY; d.mask = true; break;
    case WS_EAP_AUTH: d.kind = WK_CHOOSER; d.title = "Authentication Setting"; d.body = "Authentication";
        d.opts[0] = "EAP-MD5"; break;
    case WS_EAP_USER: d.kind = WK_TEXT; d.title = "EAP Authentication"; d.label = "User Name"; d.field = WF_EAP_USER; break;
    case WS_EAP_PASS: d.kind = WK_TEXT; d.title = "EAP Authentication"; d.label = "Password";  d.field = WF_EAP_PASS; d.mask = true; break;

    // ---- review / save / test ----
    case WS_EASY_ADV: d.kind = WK_CONFIRM; d.body = "Do you want to perform advanced settings?"; break;
    case WS_REVIEW: d.kind = WK_REVIEW; d.title = "Settings List"; break;
    case WS_SAVE: d.kind = WK_RESULT;
        d.body = "Internet connection settings have been completed.\n\nSave completed."; break;
    case WS_TEST_CONFIRM: d.kind = WK_CONFIRM; d.title = "Internet Connection Test";
        d.body = "If you perform a connection test, the current connection will be terminated and the system will be disconnected.\nDo you want to continue?"; break;
    case WS_TEST_RUN: d.kind = WK_TEST; d.title = "Internet Connection Test"; break;
    // Date and Time: Set via Internet (progress -> result). 1:1 web svi_progress/svi_done.
    case WS_DT_SVI_PROGRESS: d.kind = WK_PROGRESS; d.title = "Set via Internet";
        d.body = "Obtaining the date and time via the Internet...";
        d.autoMs = 2600; d.autoNext = WS_DT_SVI_DONE; break;
    case WS_DT_SVI_DONE: d.kind = WK_RESULT; d.title = "Set via Internet";
        d.body = "The date and time have been set."; break;
    // Date and Time: Set Manually (date entry -> time entry -> result). Web sm_date/sm_time/sm_done.
    case WS_DT_SM_DATE: d.kind = WK_TEXT; d.title = "Set Manually";
        d.label = "Date (YYYY/MM/DD)"; d.field = WF_DT_DATE; break;
    case WS_DT_SM_TIME: d.kind = WK_TEXT; d.title = "Set Manually";
        d.label = "Time (HH:MM)"; d.field = WF_DT_TIME; break;
    case WS_DT_SM_DONE: d.kind = WK_RESULT; d.title = "Set Manually";
        d.body = "The date and time have been set."; break;

    // ---- Accessory: Manage Bluetooth Devices (1:1 web bt_* flow, real backend) ----
    case WS_BT_MANAGE: d.kind = WK_CHOOSER; d.title = "Manage Bluetooth® Devices";
        d.body = "Register or manage Bluetooth® devices such as headsets, keyboards and mouse devices.\nSelect an option.";
        break;   // options are dynamic (Register New Device + bonded), built in render/nav/confirm
    case WS_BT_REGISTER_INFO: d.kind = WK_INFO; d.title = "Register Bluetooth® Device";
        d.body = "To register (pair), you will need to prepare the Bluetooth® device.\nFor information on preparing the device, refer to the instructions supplied with the Bluetooth® device."; break;
    case WS_BT_SCANNING: d.kind = WK_PROGRESS; d.title = "Register Bluetooth® Device";
        d.body = "Scanning...\nPlease wait."; d.autoNext = WS_BT_DEVICE_LIST; break;   // advance on scan completion
    case WS_BT_DEVICE_LIST: d.kind = WK_SCANLIST; d.title = "Register Bluetooth® Device";
        d.body = "Select the Bluetooth® device to register."; break;
    case WS_BT_PASSKEY: d.kind = WK_TEXT; d.title = "Register Bluetooth® Device";
        d.label = "Pass Key (enter the same on the device, default 0000)"; d.field = WF_BT_PIN; break;
    case WS_BT_REGISTERING: d.kind = WK_PROGRESS; d.title = "Register Bluetooth® Device";
        d.body = "Registering...\nPlease wait."; d.autoNext = WS_BT_REGISTER_DONE; break;
    case WS_BT_REGISTER_DONE: d.kind = WK_RESULT; d.title = "Register Bluetooth® Device";
        d.body = "Register completed."; break;   // body overridden to a failure message in render
    case WS_BT_DEVICE_OPTS: d.kind = WK_CHOOSER; d.title = "Manage Bluetooth® Devices";
        d.opts[0] = "Connect"; d.opts[1] = "Disconnect"; d.opts[2] = "Delete"; d.opts[3] = "Information"; break;
    case WS_BT_CONNECTING: d.kind = WK_PROGRESS; d.title = "Manage Bluetooth® Devices";
        d.body = "Connecting...\nPlease wait."; d.autoNext = WS_BT_MANAGE; break;
    case WS_BT_DISCONNECTING: d.kind = WK_PROGRESS; d.title = "Manage Bluetooth® Devices";
        d.body = "Disconnecting...\nPlease wait."; d.autoNext = WS_BT_MANAGE; break;
    case WS_BT_DELETE_CONFIRM: d.kind = WK_CONFIRM; d.title = "Manage Bluetooth® Devices";
        d.body = "Registration with the selected device will be deleted.\nAre you sure you want to continue?"; break;
    case WS_BT_DELETING: d.kind = WK_PROGRESS; d.title = "Manage Bluetooth® Devices";
        d.body = "Removing...\nPlease wait."; d.autoNext = WS_BT_MANAGE; break;
    case WS_BT_INFO: d.kind = WK_INFO; d.title = "Manage Bluetooth® Devices"; break;   // dynamic details in render
    case WS_BT_BD_REMOTE: d.kind = WK_INFO; d.title = "Register BD Remote Control";
        d.body = "Press the START button and ENTER button of the BD remote control you want to register at the same time, and hold down until the screen changes."; break;
    // ---- Accessory: receive an inbound registration request ----
    case WS_BT_INBOUND_WAIT: d.kind = WK_PROGRESS; d.title = "Receive Registration Request";
        d.body = "Your system is now discoverable.\nRegister this system from the other Bluetooth® device.\n\nWaiting..."; break;  // advances when a request arrives
    case WS_BT_INBOUND_CONFIRM: d.kind = WK_CONFIRM; d.title = "Receive Registration Request"; break;  // dynamic body in render
    case WS_BT_INBOUND_PAIRING: d.kind = WK_PROGRESS; d.title = "Receive Registration Request";
        d.body = "Registering...\nPlease wait."; d.autoNext = WS_BT_REGISTER_DONE; break;
    // ---- Accessory: Audio Device Settings ----
    case WS_BT_AD_MENU: d.kind = WK_CHOOSER; d.title = "Audio Device Settings";
        d.body = "Sets the audio input and output devices.\nSelect an option.";
        d.opts[0] = "Input Device"; d.opts[1] = "Output Device"; d.opts[2] = "Microphone Level"; break;
    case WS_BT_AD_INPUT: d.kind = WK_CHOOSER; d.title = "Audio Device Settings"; d.body = "Input Device";
        d.opts[0] = "System Default Device"; d.opts[1] = "None"; break;
    case WS_BT_AD_OUTPUT: d.kind = WK_CHOOSER; d.title = "Audio Device Settings"; d.body = "Output Device";
        d.opts[0] = "System Default Device"; d.opts[1] = "None"; break;
    case WS_BT_AD_MIC: d.kind = WK_CHOOSER; d.title = "Audio Device Settings"; d.body = "Microphone Level";
        d.opts[0] = "1"; d.opts[1] = "2"; d.opts[2] = "3"; d.opts[3] = "4"; d.opts[4] = "5"; break;
    default: break;
    }
}
} // namespace

void NanoMenu::startNetWizard() {
    mPs3WizActive = true;
    mPs3WizExit = 0;
    mPs3WizStack.clear();
    mPs3WizSsid.clear(); mPs3WizKey.clear(); mPs3WizSecLabel.clear(); mPs3WizSecTok = 0;
    mPs3WizMethod = mPs3WizConn = mPs3WizWlanMode = "";
    mPs3WizIpMode = "Automatic"; mPs3WizDnsMode = "Automatic"; mPs3WizMtuMode = "Automatic";
    mPs3WizProxyMode = "Do Not Use"; mPs3WizUpnp = "Enable";
    mPs3WizIpAddr = mPs3WizSubnet = mPs3WizRouter = mPs3WizPdns = mPs3WizSdns = "";
    mPs3WizMtu = mPs3WizProxyAddr = mPs3WizProxyPort = "";
    mPs3WizOpmode = mPs3WizSpeedDuplex = "";
    mPs3WizPppoeUser = mPs3WizPppoePass = mPs3WizDhcpHost = "";
    mPs3WizEapUser = mPs3WizEapPass = "";
    mPs3WizAnim = 0.0f;
    mPs3WizPendingTextField = -1;
    wizEnter(WS_INTRO, 1);
}

// Launch the Date and Time wizard reusing the net-wizard UI machinery. mode 0 =
// Set via Internet, mode 1 = Set Manually (a small int rather than the WizScr
// enum because callers like ps3XmbSelect precede the enum definition). Seeds the
// manual date/time fields from the current clock so the OSK opens pre-filled.
void NanoMenu::startDateTimeWizard(int mode) {
    mPs3WizActive = true;
    mPs3WizExit = 0;
    mPs3WizStack.clear();
    mPs3WizAnim = 0.0f;
    mPs3WizPendingTextField = -1;
    {
        time_t tt = time(nullptr); struct tm lt; localtime_r(&tt, &lt);
        char db[16], tb[8];
        snprintf(db, sizeof(db), "%04d/%02d/%02d", lt.tm_year + 1900, lt.tm_mon + 1, lt.tm_mday);
        snprintf(tb, sizeof(tb), "%02d:%02d", lt.tm_hour, lt.tm_min);
        mPs3DtDate = db; mPs3DtTime = tb;
    }
    wizEnter(mode == 0 ? WS_DT_SVI_PROGRESS : WS_DT_SM_DATE, 1);
}

// Launch the Accessory Settings Bluetooth wizard, reusing the net-wizard UI.
// mode 0 = Manage Bluetooth Devices, 1 = BD Remote Control Registration,
// 2 = Audio Device Settings.
void NanoMenu::startBtWizard(int mode) {
    mPs3WizActive = true;
    mPs3WizExit = 0;
    mPs3WizStack.clear();
    mPs3WizAnim = 0.0f;
    mPs3WizPendingTextField = -1;
    mBtWizMode = mode;
    mBtWizBusy = false;
    mBtWizOpOk = false;
    mBtWizSelAddr.clear(); mBtWizSelName.clear(); mBtWizSelCod = 0; mBtWizSelConnected = false;
    { std::lock_guard<std::mutex> lk(mBtWizMutex); mBtWizScan.clear(); }
    // Audio device choices are persisted across sessions.
    { char b[PROPERTY_VALUE_MAX];
      property_get("persist.gammaos.nano.bt.ad_in",  b, "0"); mBtWizAdInput  = atoi(b);
      property_get("persist.gammaos.nano.bt.ad_out", b, "0"); mBtWizAdOutput = atoi(b);
      property_get("persist.gammaos.nano.bt.ad_mic", b, "2"); mBtWizAdMic    = atoi(b); }
    property_set("sys.gammaos.bt.inbound_open", "0");   // not receiving yet
    int first = (mode == 1) ? WS_BT_BD_REMOTE : (mode == 2) ? WS_BT_AD_MENU : WS_BT_MANAGE;
    wizEnter(first, 1);   // wizEnter(WS_BT_MANAGE) kicks btWizRefreshBondedAsync
}

// Receive an inbound registration request: make the system discoverable and open
// the property bridge so the BondStateMachine patch routes a remote-initiated
// pairing to the Nano UI (sys.gammaos.bt.inbound) instead of Settings' dialog.
void NanoMenu::btWizStartReceive() {
    property_set("sys.gammaos.bt.inbound", "");
    property_set("sys.gammaos.bt.passkey", "");   // no stale passkey from a prior request
    property_set("sys.gammaos.bt.inbound_open", "1");
    std::thread([]() { system("gammaos-net bt discoverable 120 2>/dev/null"); }).detach();
}

void NanoMenu::btWizStopReceive() {
    property_set("sys.gammaos.bt.inbound_open", "0");
    property_set("sys.gammaos.bt.inbound", "");
    // Only stop discoverable when the radio is actually on. Spawning gammaos-net
    // while BT is off (or mid radio-off toggle) makes the BluetoothManagerService
    // reconcile the radio back on - never run it during a turn-off.
    if (mBtWizRadioOn)
        std::thread([]() { system("gammaos-net bt discoverable 0 2>/dev/null"); }).detach();
}

void NanoMenu::wizEnter(int id, int dir) {
    mPs3WizId = id;
    mPs3WizSel = 0;
    mPs3WizScroll = 0;
    mPs3WizFieldError.clear();   // a fresh screen starts without a validation error
                                 // (the same-field re-open path does not call wizEnter)
    // The intro page fades in (mPs3WizAnim) but does NOT slide horizontally;
    // every later page keeps the slide-in transition.
    mPs3WizSlideDir = (id == WS_INTRO) ? 0 : dir;
    mPs3WizSlide = (float)mPs3WizSlideDir * ps3::VW;   // slide in from the side
    mPs3WizSlideStart = mEffectTime;
    mPs3WizScreenStart = mEffectTime;
    WizDesc d; wizDesc(id, d);
    // confirm defaults: WS_EASY_ADV defaults to No (safe), test_confirm to Yes.
    if (d.kind == WK_CONFIRM) mPs3WizSel = (id == WS_EASY_ADV) ? 1 : 0;
    // Side effects on entering certain screens.
    if (id == WS_SCANNING) startWifiScanAsync();               // real scan
    // Accessory: Bluetooth wizard side effects (the real gammaos-net bt backend on
    // background threads; the busy-progress screens advance when the op completes).
    if (id == WS_BT_MANAGE)      { btWizRefreshBondedAsync(); btWizStopReceive(); }  // chooser + leave receive mode
    if (id == WS_BT_REGISTER_INFO || id == WS_BT_BD_REMOTE) {           // start a fresh discovery session
        std::lock_guard<std::mutex> lk(mBtWizMutex); mBtWizScan.clear(); mBtWizPin.clear();
    }
    if (id == WS_BT_SCANNING)      btWizScanAsync();                    // real inquiry -> device list (accumulates)
    if (id == WS_BT_REGISTERING)   btWizPairAsync(mBtWizSelAddr, mBtWizPin);  // real createBond (+ PIN) + connect
    if (id == WS_BT_CONNECTING)    btWizConnectAsync(mBtWizSelAddr);    // real profile connect
    if (id == WS_BT_DISCONNECTING) btWizDisconnectAsync(mBtWizSelAddr); // real profile disconnect
    if (id == WS_BT_DELETING)      btWizUnpairAsync(mBtWizSelAddr);     // real removeBond
    if (id == WS_BT_INBOUND_WAIT)  btWizStartReceive();                 // discoverable + open inbound bridge
    if (id == WS_BT_INBOUND_PAIRING) {                                  // accepted: stop accepting others, bond
        btWizStopReceive();
        btWizInboundAcceptAsync(mBtWizSelAddr, mBtWizInVariant);
    }
    if (id == WS_SAVE) {                                       // real connect (Wi-Fi only)
        if (mPs3WizConn == "Wireless" && !mPs3WizSsid.empty()) {
            // Apply the advanced settings (static IP/DNS/MTU/proxy) via the
            // gammaos-net WifiManager helper when the user chose any manual
            // option; otherwise the quick cmd-wifi association (DHCP) is enough.
            bool custom = (mPs3WizIpMode == "Manual") || (mPs3WizDnsMode == "Manual")
                       || (mPs3WizMtuMode == "Manual") || (mPs3WizProxyMode == "Use");
            if (custom) connectWithWizardSettings();
            else addAndConnectWifi(mPs3WizSsid, mPs3WizSecTok, mPs3WizKey);
        }
    }
    if (id == WS_TEST_RUN) startNetTest();                     // real connectivity test
    // Date and Time: Set via Internet -> enable Android automatic time + time zone
    // (NTP) and nudge a refresh on a background thread (the 2.6s dwell is real work).
    if (id == WS_DT_SVI_PROGRESS) {
        std::thread([]{
            // Turn on Android automatic time/zone for ongoing syncs, then ACTIVELY
            // fetch the current UTC and set the clock: the network_time_update
            // service is not reachable on this build, so just enabling auto_time
            // does not re-sync (it stays on whatever Set Manually left). curl the
            // Date response header (UTC, second precision) and set-time from it.
            system("settings put global auto_time 1 2>/dev/null");
            system("settings put global auto_time_zone 1 2>/dev/null");
            FILE* f = popen("curl -sI --max-time 8 http://example.com 2>/dev/null | grep -i '^date:'", "r");
            if (f) {
                char line[160] = {};
                bool got = fgets(line, sizeof(line), f) != nullptr;
                pclose(f);
                const char* colon = got ? strchr(line, ':') : nullptr;   // after "Date"
                if (colon) {
                    const char* ds = colon + 1;
                    while (*ds == ' ') ds++;                               // -> "Wed, 04 Jun 2026 13:57:10 GMT"
                    struct tm tmv; memset(&tmv, 0, sizeof(tmv));
                    if (strptime(ds, "%a, %d %b %Y %H:%M:%S", &tmv)) {
                        time_t epoch = timegm(&tmv);                       // header is GMT/UTC
                        if (epoch > 0) {
                            char cmd[96];
                            snprintf(cmd, sizeof(cmd), "cmd alarm set-time %lld 2>/dev/null",
                                     (long long)epoch * 1000LL);
                            system(cmd);
                        }
                    }
                }
            }
        }).detach();
        // auto_time_zone is now on; the Olson tz captures DST automatically and the
        // "Daylight Saving" row reflects it live (drawPs3Clock -> mPs3DstNow).
    }
    // Date and Time: Set Manually -> disable network time so the manual instant
    // sticks, parse the staged YYYY/MM/DD + HH:MM into epoch millis, set the clock.
    if (id == WS_DT_SM_DONE) {
        std::string dStr = mPs3DtDate, tStr = mPs3DtTime;
        std::thread([dStr, tStr]{
            int Y = 0, Mo = 0, D = 0, h = 0, mi = 0;
            if (sscanf(dStr.c_str(), "%d/%d/%d", &Y, &Mo, &D) == 3 &&
                sscanf(tStr.c_str(), "%d:%d", &h, &mi) == 2) {
                struct tm tmv; memset(&tmv, 0, sizeof(tmv));
                tmv.tm_year = Y - 1900; tmv.tm_mon = Mo - 1; tmv.tm_mday = D;
                tmv.tm_hour = h; tmv.tm_min = mi; tmv.tm_sec = 0; tmv.tm_isdst = -1;
                time_t epoch = mktime(&tmv);   // honours the current TZ -> correct instant
                if (epoch > 0) {
                    system("settings put global auto_time 0 2>/dev/null");
                    char cmd[96];
                    snprintf(cmd, sizeof(cmd), "cmd alarm set-time %lld 2>/dev/null",
                             (long long)epoch * 1000LL);   // set-time wants MILLISECONDS
                    system(cmd);
                }
            }
        }).detach();
    }
    // Defer the OSK open to the render loop (renderNetWizard) so a chained text
    // field doesn't try to open a new OSK from inside the previous OSK's confirm
    // callback (which leaves it closed). It opens once the old OSK has finished
    // closing.
    if (d.kind == WK_TEXT) mPs3WizPendingTextField = d.field;
}

void NanoMenu::wizOpenTextField(int field) {
    mPs3WizTextField = field;
    WizDesc d; wizDesc(mPs3WizId, d);
    std::string prompt = d.label && *d.label ? d.label : "Enter";
    // Seed the current value so the field is editable in place.
    const std::string* cur = nullptr;
    switch (field) {
    case WF_SSID: cur = &mPs3WizSsid; break;
    case WF_WEP_KEY: case WF_WPA_KEY: cur = &mPs3WizKey; break;
    case WF_IP_ADDR: cur = &mPs3WizIpAddr; break;     case WF_IP_SUBNET: cur = &mPs3WizSubnet; break;
    case WF_IP_ROUTER: cur = &mPs3WizRouter; break;   case WF_IP_PDNS: cur = &mPs3WizPdns; break;
    case WF_IP_SDNS: cur = &mPs3WizSdns; break;       case WF_DNS_PDNS: cur = &mPs3WizPdns; break;
    case WF_DNS_SDNS: cur = &mPs3WizSdns; break;      case WF_MTU: cur = &mPs3WizMtu; break;
    case WF_PROXY_ADDR: cur = &mPs3WizProxyAddr; break; case WF_PROXY_PORT: cur = &mPs3WizProxyPort; break;
    case WF_PPPOE_USER: cur = &mPs3WizPppoeUser; break; case WF_PPPOE_PASS: cur = &mPs3WizPppoePass; break;
    case WF_DHCP_HOST: cur = &mPs3WizDhcpHost; break;
    case WF_EAP_USER: cur = &mPs3WizEapUser; break;   case WF_EAP_PASS: cur = &mPs3WizEapPass; break;
    case WF_DT_DATE: cur = &mPs3DtDate; break;        case WF_DT_TIME: cur = &mPs3DtTime; break;
    case WF_BT_PIN: cur = &mBtWizPin; break;
    }
    mOskQuery = cur ? *cur : "";
    openOskForPassword(prompt, [this, field](const std::string& val) {
        // Store the typed value, then advance the wizard.
        switch (field) {
        case WF_SSID: mPs3WizSsid = val; break;
        case WF_WEP_KEY: case WF_WPA_KEY: mPs3WizKey = val; break;
        case WF_IP_ADDR: mPs3WizIpAddr = val; break;     case WF_IP_SUBNET: mPs3WizSubnet = val; break;
        case WF_IP_ROUTER: mPs3WizRouter = val; break;   case WF_IP_PDNS: mPs3WizPdns = val; break;
        case WF_IP_SDNS: mPs3WizSdns = val; break;       case WF_DNS_PDNS: mPs3WizPdns = val; break;
        case WF_DNS_SDNS: mPs3WizSdns = val; break;      case WF_MTU: mPs3WizMtu = val; break;
        case WF_PROXY_ADDR: mPs3WizProxyAddr = val; break; case WF_PROXY_PORT: mPs3WizProxyPort = val; break;
        case WF_PPPOE_USER: mPs3WizPppoeUser = val; break; case WF_PPPOE_PASS: mPs3WizPppoePass = val; break;
        case WF_DHCP_HOST: mPs3WizDhcpHost = val; break;
        case WF_EAP_USER: mPs3WizEapUser = val; break;   case WF_EAP_PASS: mPs3WizEapPass = val; break;
        case WF_DT_DATE: mPs3DtDate = val; break;        case WF_DT_TIME: mPs3DtTime = val; break;
        case WF_BT_PIN: mBtWizPin = val.empty() ? "0000" : val; break;   // default PIN 0000
        }
        // Optional fields (secondary DNS, the BT PIN which defaults to 0000) may be
        // left blank; everything else treats an empty submit as a cancel.
        if (val.empty() && field != WF_IP_SDNS && field != WF_DNS_SDNS && field != WF_BT_PIN) return;
        // Validate format + range. On bad input set the inline error and re-open
        // the same field (deferred) instead of advancing.
        std::string err = validateWizField(field, val);
        if (!err.empty()) { mPs3WizFieldError = err; mPs3WizPendingTextField = field; return; }
        mPs3WizFieldError.clear();
        int nxt = wizNextScreen(mPs3WizId, 0);
        if (nxt == WS_NONE) { mPs3WizExit = 1; mPs3WizActive = false; }
        else { mPs3WizStack.push_back(mPs3WizId); wizEnter(nxt, 1); }
    });
    // NOTE: openOskForPassword clears mOskQuery, so the field opens empty (a direct
    // pre-fill does not survive the IME commit on submit; the web Set Manually also
    // opens empty on first entry). The user types the value, which commits cleanly.
    // Password fields mask; plaintext for SSID/IP/etc.
    WizDesc d2; wizDesc(mPs3WizId, d2);
    mOskPlaintext = !d2.mask;
    mOskPasswordMode = d2.mask;
    // Date/time fields are numeric: the OSK accepts digits only and inserts the
    // "/" or ":" separators automatically as the user types.
    mOskFieldFmt = (field == WF_DT_DATE) ? 1 : (field == WF_DT_TIME) ? 2 : 0;
    mOsk.caret = (int)mOskQuery.size();
}

// Per-field input validation. Returns "" when valid, otherwise a short message
// shown in red under the field. Empty values are pre-filtered by the caller
// (optional fields), so a value reaching here is non-empty.
std::string NanoMenu::validateWizField(int field, const std::string& val) {
    auto isIpv4 = [](const std::string& s) -> bool {
        int a, b, c, d; char extra;
        if (sscanf(s.c_str(), "%d.%d.%d.%d%c", &a, &b, &c, &d, &extra) != 4) return false;
        return a >= 0 && a <= 255 && b >= 0 && b <= 255 &&
               c >= 0 && c <= 255 && d >= 0 && d <= 255;
    };
    switch (field) {
    case WF_DT_DATE: {
        int Y = 0, Mo = 0, D = 0;
        if (!(val.size() == 10 && val[4] == '/' && val[7] == '/' &&
              sscanf(val.c_str(), "%d/%d/%d", &Y, &Mo, &D) == 3 &&
              Y >= 1970 && Y <= 2099 && Mo >= 1 && Mo <= 12))
            return "Enter a valid date (YYYY/MM/DD)";
        static const int dim[12] = {31,28,31,30,31,30,31,31,30,31,30,31};
        int maxd = dim[Mo - 1];
        if (Mo == 2 && ((Y % 4 == 0 && Y % 100 != 0) || Y % 400 == 0)) maxd = 29;
        if (D < 1 || D > maxd) return "Enter a valid day for that month";
        return "";
    }
    case WF_DT_TIME: {
        int h = 0, mi = 0;
        if (!(val.size() == 5 && val[2] == ':' &&
              sscanf(val.c_str(), "%d:%d", &h, &mi) == 2 &&
              h >= 0 && h <= 23 && mi >= 0 && mi <= 59))
            return "Enter a valid time (HH:MM, 24-hour)";
        return "";
    }
    case WF_IP_ADDR: case WF_IP_SUBNET: case WF_IP_ROUTER:
    case WF_IP_PDNS:  case WF_IP_SDNS:
    case WF_DNS_PDNS: case WF_DNS_SDNS:
        if (!isIpv4(val)) return "Enter a valid IPv4 address (n.n.n.n)";
        return "";
    case WF_PROXY_PORT: {
        int p = 0; char extra;
        if (sscanf(val.c_str(), "%d%c", &p, &extra) != 1 || p < 1 || p > 65535)
            return "Port must be 1-65535";
        return "";
    }
    case WF_MTU: {
        int m = 0; char extra;
        if (sscanf(val.c_str(), "%d%c", &m, &extra) != 1 || m < 576 || m > 9000)
            return "MTU must be 576-9000";
        return "";
    }
    case WF_BT_PIN: {   // empty -> 0000; otherwise 1-16 numeric digits (BT PIN spec)
        if (val.size() > 16) return "PIN must be 1-16 digits";
        for (char c : val) if (c < '0' || c > '9') return "PIN must be digits only";
        return "";
    }
    default: return "";   // SSID, keys, proxy host, EAP, PPPoE, DHCP host: free text
    }
}

void NanoMenu::wizConfirm() {
    WizDesc d; wizDesc(mPs3WizId, d);
    if (d.kind == WK_TEXT || d.kind == WK_PROGRESS) return;   // OSK owns text; progress auto-advances
    // ---- Accessory: Bluetooth wizard selects ----
    if (mPs3WizId == WS_BT_MANAGE) {          // dynamic: [Turn On] | Register / Receive + bonded + Turn Off
        if (!mBtWizRadioOn) {                 // radio off: only "Turn Bluetooth On"
            if (mPs3WizSel == 0) { mPs3WizSel = 0; btWizToggleRadioAsync(true); }
            return;
        }
        std::vector<BtDevEntry> bonded;
        { std::lock_guard<std::mutex> lk(mBtWizMutex); bonded = mBtWizBonded; }
        int toggleIdx = 2 + (int)bonded.size();   // "Turn Bluetooth Off" is the last row
        if (mPs3WizSel == 0) { mPs3WizStack.push_back(mPs3WizId); wizEnter(WS_BT_REGISTER_INFO, 1); return; }
        if (mPs3WizSel == 1) { mPs3WizStack.push_back(mPs3WizId); wizEnter(WS_BT_INBOUND_WAIT, 1); return; }
        if (mPs3WizSel == toggleIdx) { mPs3WizSel = 0; btWizToggleRadioAsync(false); btWizStopReceive(); return; }
        int di = mPs3WizSel - 2;
        if (di >= 0 && di < (int)bonded.size()) {
            mBtWizSelAddr = bonded[di].address; mBtWizSelName = bonded[di].name;
            mBtWizSelCod = bonded[di].cod;      mBtWizSelConnected = bonded[di].connected;
            mPs3WizStack.push_back(mPs3WizId); wizEnter(WS_BT_DEVICE_OPTS, 1);
        }
        return;
    }
    if (mPs3WizId == WS_BT_INBOUND_CONFIRM) {
        if (mPs3WizSel == 0) {   // Yes -> accept + wait for the bond
            mPs3WizStack.push_back(mPs3WizId); wizEnter(WS_BT_INBOUND_PAIRING, 1);
        } else {                 // No -> reject, keep listening for the next request
            std::string a = mBtWizSelAddr;
            std::thread([a]() { system(("gammaos-net bt confirm " + a + " reject 2>/dev/null").c_str()); }).detach();
            wizEnter(WS_BT_INBOUND_WAIT, -1);   // re-arm receive (btWizStartReceive clears the prop)
        }
        return;
    }
    if (mPs3WizId == WS_BT_DEVICE_LIST) {     // pick the discovered device to pair
        std::vector<BtDevEntry> scan;
        { std::lock_guard<std::mutex> lk(mBtWizMutex); scan = mBtWizScan; }
        if (mPs3WizSel < 0 || mPs3WizSel >= (int)scan.size()) return;
        mBtWizSelAddr = scan[mPs3WizSel].address; mBtWizSelName = scan[mPs3WizSel].name;
        mBtWizSelCod = scan[mPs3WizSel].cod;      mBtWizSelConnected = scan[mPs3WizSel].connected;
        mPs3WizStack.push_back(mPs3WizId);
        wizEnter(btNeedsPasskey(mBtWizSelCod) ? WS_BT_PASSKEY : WS_BT_REGISTERING, 1);
        return;
    }
    if (mPs3WizId == WS_BT_REGISTER_DONE) {   // loop back to the Manage root (BD remote closes)
        if (mBtWizMode == 1) { mPs3WizExit = 1; mPs3WizActive = false; }
        else { mPs3WizStack.clear(); wizEnter(WS_BT_MANAGE, -1); }
        return;
    }
    if (mPs3WizId == WS_BT_DELETE_CONFIRM) {
        if (mPs3WizSel == 0) { mPs3WizStack.push_back(mPs3WizId); wizEnter(WS_BT_DELETING, 1); }
        else wizBack();
        return;
    }
    if (mPs3WizId == WS_BT_INFO) { wizBack(); return; }   // "OK" dismisses
    if (mPs3WizId == WS_BT_AD_INPUT)  { mBtWizAdInput = mPs3WizSel;
        property_set("persist.gammaos.nano.bt.ad_in", std::to_string(mPs3WizSel).c_str()); wizBack(); return; }
    if (mPs3WizId == WS_BT_AD_OUTPUT) { mBtWizAdOutput = mPs3WizSel;
        property_set("persist.gammaos.nano.bt.ad_out", std::to_string(mPs3WizSel).c_str()); wizBack(); return; }
    if (mPs3WizId == WS_BT_AD_MIC)    { mBtWizAdMic = mPs3WizSel;
        property_set("persist.gammaos.nano.bt.ad_mic", std::to_string(mPs3WizSel).c_str()); wizBack(); return; }
    if (mPs3WizId == WS_APLIST) {             // pick the selected real access point
        std::vector<WifiNetEntry> aps;
        { std::lock_guard<std::mutex> lk(mWifiListMutex);
          for (auto& e : mWifiEntries) if (e.bssid != "__TOGGLE__") aps.push_back(e); }
        if (mPs3WizSel < 0 || mPs3WizSel >= (int)aps.size()) return;
        const WifiNetEntry& ap = aps[mPs3WizSel];
        mPs3WizSsid = ap.ssid; mPs3WizSecTok = ap.security;
        static const char* secNames[] = {"None","WEP","WPA2-PSK","WPA3-PSK","OWE"};
        mPs3WizSecLabel = secNames[(ap.security >= 0 && ap.security <= 4) ? ap.security : 0];
        // Mirror web wlan_ap_list.onSelect: open/OWE need no key; WEP -> WEP key;
        // everything else -> WPA key (then Easy: advanced? / Custom: IP setting).
        int nxt;
        if (ap.security == 0 || ap.security == 4) nxt = (mPs3WizMethod == "Easy") ? WS_EASY_ADV : WS_IP;
        else if (ap.security == 1)                nxt = WS_WEP_KEY;
        else                                      nxt = WS_WPA_KEY;
        mPs3WizStack.push_back(mPs3WizId);
        wizEnter(nxt, 1);
        return;
    }
    int nxt = wizNextScreen(mPs3WizId, mPs3WizSel);
    if (nxt == WS_NONE) { mPs3WizExit = 1; mPs3WizActive = false; return; }
    mPs3WizStack.push_back(mPs3WizId);
    wizEnter(nxt, 1);
}

void NanoMenu::wizBack() {
    if (mPs3WizId == WS_TEST_RUN) stopNetTest();
    // Date and Time result screens close (the web result is terminal, next=__close__);
    // the date/time is already applied, so O dismisses like the footer "OK".
    if (mPs3WizId == WS_DT_SVI_DONE || mPs3WizId == WS_DT_SM_DONE) {
        mPs3WizExit = 1; mPs3WizActive = false; return;
    }
    if (mPs3WizStack.empty()) { mPs3WizExit = -1; mPs3WizActive = false; return; }
    int prev = mPs3WizStack.back(); mPs3WizStack.pop_back();
    // Don't land back on a transient progress/test screen (mirrors web wizBack). If
    // skipping them empties the stack, close rather than re-entering a progress.
    for (;;) {
        WizDesc pd; wizDesc(prev, pd);
        if (pd.kind != WK_PROGRESS && pd.kind != WK_TEST) break;
        if (mPs3WizStack.empty()) { mPs3WizExit = -1; mPs3WizActive = false; return; }
        prev = mPs3WizStack.back(); mPs3WizStack.pop_back();
    }
    wizEnter(prev, -1);
}

void NanoMenu::wizRescan() {
    // The square button re-scans on the Wi-Fi access-point list and the Bluetooth
    // device list. Drop the transient scan screen we arrived through so repeats
    // don't bloat the stack.
    if (mPs3WizId == WS_APLIST) {
        while (!mPs3WizStack.empty() && mPs3WizStack.back() == WS_SCANNING)
            mPs3WizStack.pop_back();
        wizEnter(WS_SCANNING, 1);           // fires startWifiScanAsync; auto-advances back to the list
    } else if (mPs3WizId == WS_BT_DEVICE_LIST) {
        while (!mPs3WizStack.empty() && mPs3WizStack.back() == WS_BT_SCANNING)
            mPs3WizStack.pop_back();
        wizEnter(WS_BT_SCANNING, 1);        // fires btWizScanAsync; auto-advances back to the list
    }
}

void NanoMenu::wizNav(int dir, bool /*horizontal*/) {
    WizDesc d; wizDesc(mPs3WizId, d);
    if (d.kind == WK_CHOOSER) {
        int n;
        if (mPs3WizId == WS_BT_MANAGE) {
            if (!mBtWizRadioOn) { n = 1; }      // just "Turn Bluetooth On"
            else { std::lock_guard<std::mutex> lk(mBtWizMutex);
                   n = 3 + (int)mBtWizBonded.size(); }   // Register + Receive + bonded + Turn Off
        } else { n = 0; while (n < 8 && d.opts[n]) n++; }
        if (n > 0) mPs3WizSel = (mPs3WizSel + dir + n) % n;
    } else if (d.kind == WK_CONFIRM) {
        mPs3WizSel ^= 1;
    } else if (d.kind == WK_SCANLIST) {
        int n;
        if (mPs3WizId == WS_BT_DEVICE_LIST) {
            std::lock_guard<std::mutex> lk(mBtWizMutex); n = (int)mBtWizScan.size();
        } else {
            std::lock_guard<std::mutex> lk(mWifiListMutex);
            n = 0; for (auto& e : mWifiEntries) if (e.bssid != "__TOGGLE__") n++;
        }
        if (n > 0) { mPs3WizSel += dir; if (mPs3WizSel < 0) mPs3WizSel = 0; if (mPs3WizSel > n - 1) mPs3WizSel = n - 1; }
    }
    mDisplayDirty = true;
}

// Forward navigation table (1:1 with web wizNext). Commits the choice into state.
int NanoMenu::wizNextScreen(int id, int sel) {
    auto afterAuth = [&]() { return (mPs3WizMethod == "Easy") ? WS_EASY_ADV : WS_IP; };
    switch (id) {
    case WS_INTRO: return WS_METHOD;
    case WS_METHOD: mPs3WizMethod = sel ? "Custom" : "Easy"; return WS_CONN;
    case WS_CONN:
        mPs3WizConn = sel ? "Wireless" : "Wired Connection";
        if (sel == 0)   // Wired Connection
            return (mPs3WizMethod == "Easy") ? WS_EASY_WIRED_CHECK : WS_WIRED_OPMODE;
        return WS_WLAN; // Wireless (both Easy & Custom)

    // ---- Wired ----
    case WS_WIRED_OPMODE:
        mPs3WizOpmode = sel ? "Speed and Duplex" : "Auto-Detect";
        return sel ? WS_WIRED_SPEED : WS_IP;
    case WS_WIRED_SPEED: {
        static const char* sp[] = {"10BASE-T Half-Duplex","10BASE-T Full-Duplex",
            "100BASE-TX Half-Duplex","100BASE-TX Full-Duplex",
            "1000BASE-T Half-Duplex","1000BASE-T Full-Duplex"};
        if (sel >= 0 && sel < 6) mPs3WizSpeedDuplex = sp[sel];
        return WS_IP; }

    // ---- IP ----
    case WS_IP:
        mPs3WizIpMode = (sel == 1) ? "Manual" : (sel == 2) ? "PPPoE" : "Automatic";
        if (sel == 1) return WS_IP_ADDR;        // Manual
        if (sel == 2) return WS_PPPOE_USER;     // PPPoE
        return WS_DHCP_HOST;                    // Automatic -> DHCP host name
    case WS_IP_ADDR: return WS_IP_SUBNET;   case WS_IP_SUBNET: return WS_IP_ROUTER;
    case WS_IP_ROUTER: return WS_IP_PDNS;   case WS_IP_PDNS: return WS_IP_SDNS;
    case WS_IP_SDNS: return WS_MTU_Q;
    case WS_PPPOE_USER: return WS_PPPOE_PASS;
    case WS_PPPOE_PASS: return WS_DNS;
    case WS_DHCP_HOST: return sel ? WS_DHCP_HOST_ENTRY : WS_DNS;   // Set -> entry
    case WS_DHCP_HOST_ENTRY: return WS_DNS;

    // ---- DNS / MTU / Proxy / UPnP ----
    case WS_DNS: mPs3WizDnsMode = sel ? "Manual" : "Automatic";
        return sel ? WS_DNS_PDNS : WS_MTU_Q;
    case WS_DNS_PDNS: return WS_DNS_SDNS;   case WS_DNS_SDNS: return WS_MTU_Q;
    case WS_MTU_Q: mPs3WizMtuMode = sel ? "Manual" : "Automatic";
        return sel ? WS_MTU : WS_PROXY_Q;
    case WS_MTU: return WS_PROXY_Q;
    case WS_PROXY_Q: mPs3WizProxyMode = sel ? "Use" : "Do Not Use";
        return sel ? WS_PROXY_ADDR : WS_UPNP;
    case WS_PROXY_ADDR: return WS_PROXY_PORT;   case WS_PROXY_PORT: return WS_UPNP;
    case WS_UPNP: mPs3WizUpnp = sel ? "Disable" : "Enable"; return WS_REVIEW;

    // ---- WLAN ----
    case WS_WLAN: mPs3WizWlanMode = (sel == 1) ? "Manual" : (sel == 2) ? "Automatic" : "Scan";
        if (sel == 1) return WS_SSID;           // Enter Manually
        if (sel == 2) return WS_WLAN_AUTO;      // Automatic (AOSS / Rakuraku)
        return WS_SCANNING;                     // Scan
    case WS_WLAN_AUTO: return sel ? WS_RAKU1 : WS_AOSS_PROMPT;
    case WS_AOSS_PROMPT: return WS_AOSS_WAIT;
    case WS_RAKU2: return WS_RAKU3;
    case WS_RAKU3: return WS_AOSS_DONE;
    case WS_AOSS_DONE: return WS_TEST_CONFIRM;
    case WS_SSID: return WS_SECURITY;
    case WS_SECURITY: {
        const char* lab = (sel >= 0 && sel < 8 && WIZ_SEC_OPTS[sel]) ? WIZ_SEC_OPTS[sel] : "None";
        mPs3WizSecLabel = lab;
        if (sel == 0) { mPs3WizSecTok = 0; return afterAuth(); }   // None
        if (sel == 1) { mPs3WizSecTok = 1; return WS_WEP_KEY; }    // WEP
        if (sel == 7) { mPs3WizSecTok = 2; return WS_EAP_AUTH; }   // EAP Authentication
        mPs3WizSecTok = 2; return WS_WPA_KEY; }                    // WPA*
    case WS_WEP_KEY: return afterAuth();
    case WS_WPA_KEY: return afterAuth();
    case WS_EAP_AUTH: return WS_EAP_USER;
    case WS_EAP_USER: return WS_EAP_PASS;
    case WS_EAP_PASS: return afterAuth();

    // ---- review / save / test ----
    case WS_EASY_ADV: return (sel == 0) ? WS_IP : WS_REVIEW;   // Yes -> advanced
    case WS_REVIEW: return WS_SAVE;
    case WS_SAVE: return WS_TEST_CONFIRM;
    case WS_TEST_CONFIRM: return (sel == 0) ? WS_TEST_RUN : WS_NONE;
    case WS_TEST_RUN: return WS_NONE;
    // ---- Date and Time ----
    case WS_DT_SVI_PROGRESS: return WS_DT_SVI_DONE;
    case WS_DT_SVI_DONE: return WS_NONE;
    case WS_DT_SM_DATE: return WS_DT_SM_TIME;
    case WS_DT_SM_TIME: return WS_DT_SM_DONE;
    case WS_DT_SM_DONE: return WS_NONE;
    // ---- Accessory: Bluetooth ----
    case WS_BT_REGISTER_INFO: return WS_BT_SCANNING;
    case WS_BT_PASSKEY:       return WS_BT_REGISTERING;
    case WS_BT_BD_REMOTE:     return WS_BT_SCANNING;
    case WS_BT_DEVICE_OPTS:
        return (sel == 0) ? WS_BT_CONNECTING
             : (sel == 1) ? WS_BT_DISCONNECTING
             : (sel == 2) ? WS_BT_DELETE_CONFIRM
                          : WS_BT_INFO;
    case WS_BT_AD_MENU:
        return (sel == 0) ? WS_BT_AD_INPUT : (sel == 1) ? WS_BT_AD_OUTPUT : WS_BT_AD_MIC;
    default: return WS_NONE;
    }
}

void NanoMenu::renderNetWizard() {
    if (!mPs3WizActive) return;
    // Open a deferred text-field OSK once any previous OSK has fully closed. This
    // keeps chained text screens (e.g. Set Manually date -> time, or manual IP ->
    // subnet) from opening a new OSK inside the old OSK's confirm callback.
    if (mPs3WizPendingTextField >= 0 && !mOskActive) {
        int f = mPs3WizPendingTextField; mPs3WizPendingTextField = -1;
        wizOpenTextField(f);
    }
    float dt = mFrameDt; if (dt < 0.0f) dt = 0.0f; if (dt > 0.1f) dt = 0.1f;
    mPs3WizAnim += (1.0f - mPs3WizAnim) * (1.0f - expf(-13.0f * dt));
    if (mPs3WizAnim > 0.999f) mPs3WizAnim = 1.0f;
    float ap = mPs3WizAnim;
    { ps3::LayoutParams lp; lp.panelW = mWidth; lp.panelH = mHeight; lp.uiScale = mPs3UiScale; ps3::layoutCompute(lp); }
    // Dialog/wizard text outline ratio (matches renderPs3Dialog): set it here so the
    // wizard is self-contained when reached via the overlay scrim early-return, which
    // skips the chrome code that would otherwise set it.
    mTextOutlineRatio = 0.5f;

    WizDesc d; wizDesc(mPs3WizId, d);

    // Auto-advance for the timed progress screens (scan / wired check).
    if (d.kind == WK_PROGRESS && d.autoMs > 0 &&
        (mEffectTime - mPs3WizScreenStart) * 1000.0f >= (float)d.autoMs) {
        mPs3WizStack.push_back(mPs3WizId);
        wizEnter(d.autoNext, 1);
        wizDesc(mPs3WizId, d);
    }
    // Advance the Bluetooth busy-progress screens when their background op finishes
    // (with a short minimum dwell so the spinner is always seen). Returning to the
    // Manage screen resets the stack so it stays the root of the BT wizard.
    if (btIsBusyProgress(mPs3WizId) && !mBtWizBusy) {
        float dwell = mEffectTime - mPs3WizScreenStart; if (dwell < 0.0f) dwell += 500.0f;
        if (dwell >= 0.5f) {
            int nx = d.autoNext;
            if (nx == WS_BT_MANAGE) { mPs3WizStack.clear(); wizEnter(WS_BT_MANAGE, -1); }
            else { mPs3WizStack.push_back(mPs3WizId); wizEnter(nx, 1); }
            wizDesc(mPs3WizId, d);
        }
    }
    // The receive screen advances when the BondStateMachine patch publishes a
    // remote-initiated pairing request to sys.gammaos.bt.inbound.
    if (mPs3WizId == WS_BT_INBOUND_WAIT) {
        char ib[PROPERTY_VALUE_MAX] = {0};
        property_get("sys.gammaos.bt.inbound", ib, "");
        if (ib[0]) {
            std::string s(ib);                       // "<mac>\t<name>\t<variant>\t<passkey>"
            size_t p0 = s.find('\t');
            size_t p1 = (p0 == std::string::npos) ? std::string::npos : s.find('\t', p0 + 1);
            size_t p2 = (p1 == std::string::npos) ? std::string::npos : s.find('\t', p1 + 1);
            mBtWizSelAddr   = (p0 == std::string::npos) ? s : s.substr(0, p0);
            mBtWizSelName   = (p0 == std::string::npos) ? "" : s.substr(p0 + 1, (p1 == std::string::npos ? s.size() : p1) - (p0 + 1));
            mBtWizInVariant = (p1 == std::string::npos) ? -1 : atoi(s.substr(p1 + 1, (p2 == std::string::npos ? s.size() : p2) - (p1 + 1)).c_str());
            mBtWizInPasskey = (p2 == std::string::npos) ? "" : s.substr(p2 + 1);
            if (mBtWizSelName.empty()) mBtWizSelName = mBtWizSelAddr;
            property_set("sys.gammaos.bt.inbound", "");   // consume it so the next request can land
            mPs3WizStack.push_back(mPs3WizId);
            wizEnter(WS_BT_INBOUND_CONFIRM, 1);
            wizDesc(mPs3WizId, d);
        }
    }

    // Horizontal slide: ease the body offset back to 0 over ~260 ms (easeOutCubic).
    float sp = (mEffectTime - mPs3WizSlideStart) / 0.26f;
    if (sp > 1.0f) sp = 1.0f;
    float ease = 1.0f - (1.0f - sp) * (1.0f - sp) * (1.0f - sp);
    mPs3WizSlide = (float)mPs3WizSlideDir * ps3::VW * (1.0f - ease);

    bool oskUp = mOskActive;   // text screens render the OSK on top

    // ---- backdrop: blurred live wave + dim (same as the fullscreen dialogs) ----
    {
        // While the OSK is up we re-capture the wave EVERY frame. The OSK draws its
        // own frosted panel via captureGlass(), which overwrites the SAME shared
        // blur texture (mGlassBlurTex) in FB orientation; at the normal 15fps
        // cadence the wizard would then sample that FB-space blur on the 3-of-4
        // skipped frames (wrong orientation + a feedback of the dimmed wizard) and
        // alternate with the wave blur -> visible background flicker. Refreshing
        // from the wave each frame keeps the backdrop correct and smooth.
        float blurCad = 0.0667f;
        bool due = !mPs3DlgBlurValid || oskUp || (mEffectTime - mPs3DlgBlurT) >= blurCad;
        if (due && captureGlassFromWave()) { mPs3DlgBlurValid = true; mPs3DlgBlurT = mEffectTime; }
        // The frosted-WAVE backdrop only matches the wave: home XMB always; overlay
        // WALLPAPER/launcher only when the wallpaper IS the wave (mCurrentEffect==22).
        // Never in overlay scrim (the dimmed live app shows) or over a non-wave wallpaper
        // (renderEffect's wallpaper shows). KEEP the per-frame capture above (the OSK
        // reuses mPs3DlgBlurValid + it must stay valid) and the dim below for readability.
        const bool frostBg = !mOverlayMode || (mOverlayWallpaper && mCurrentEffect == 22);
        if (mPs3DlgBlurValid && frostBg)
            drawFrostedGlass(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, ap, true);
    }
    // In the in-game overlay (scrim mode) skip the extra wizard dim - the 90% scrim
    // already darkens the backdrop, so keep the live app at the same level as the
    // top-level scrim instead of double-dimming it.
    if (!mOverlayMode || mOverlayWallpaper)
        drawQuad(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f, 0.0f, 0.0f, (oskUp ? 0.62f : 0.5f) * ap);

    // Layout (ui-independent base scale, 1080 design centred in the frame).
    float ui = mPs3UiScale; if (ui < 0.5f) ui = 0.5f; if (ui > 2.0f) ui = 2.0f;
    const float S = ps3::gScale / ui;
    // Centre the (gFrameW/ui)-wide content band horizontally in the frame for any
    // UI zoom (mPs3UiScale defaults to 1.12). offY already recentres vertically;
    // without this, dialogs sit left-of-centre with an unbalanced right gap.
    const float offX = ps3::gFrameX + (ps3::gFrameW - S * ps3::XCF(ps3::VW)) * 0.5f;
    const float offY = ps3::gFrameY + ps3::gFrameH * 0.5f - S * (ps3::VH * 0.5f);
    const float VW = ps3::VW;
    const float innerTop = 199.0f, innerBot = 880.0f;
    auto X  = [&](float vx) { return S * vx + offX; };
    auto XC = [&](float vx) { return S * ps3::XCF(vx) + offX; };
    auto Y  = [&](float vy) { return S * vy + offY; };
    auto DS = [&](float v)  { return S * v; };
    auto FS = [&](float px) { return S * px / 16.0f; };
    const float slidePx = S * mPs3WizSlide;            // body content slide offset
    const float maxW = DS((VW - 400.0f) * ps3::LAYOUT_FIT);

    auto wrap = [&](const std::string& body, float fs) {
        std::vector<std::string> out; std::string para;
        auto wrapPara = [&](const std::string& p) {
            if (p.empty()) { out.push_back(""); return; }
            std::string line, word;
            auto commit = [&]() {
                if (word.empty()) return;
                std::string trial = line.empty() ? word : line + " " + word;
                if (!line.empty() && measureText(trial.c_str(), fs) > maxW) { out.push_back(line); line = word; }
                else line = trial;
                word.clear();
            };
            for (const char* q = p.c_str(); ; ++q) {
                if (*q == ' ' || *q == '\0') { commit(); if (*q == '\0') break; } else word.push_back(*q);
            }
            if (!line.empty()) out.push_back(line);
        };
        for (size_t i = 0; i <= body.size(); i++) {
            if (i == body.size() || body[i] == '\n') { wrapPara(para); para.clear(); } else para.push_back(body[i]);
        }
        return out;
    };

    // ---- header: settings icon + title + dividers ----
    float iconSz = DS(36.0f);
    if (mIconTextures[17])
        drawIconTex(mIconTextures[17], X(130.0f) - iconSz * 0.5f, Y(175.0f) - iconSz * 0.5f, iconSz, iconSz, 1, 1, 1, ap);
    ps3DlgText(d.title, X(160.0f), Y(187.0f), FS(28.0f), 1, 1, 1, ap, 0);
    float divLw = fmaxf(1.0f, DS(1.0f));
    drawQuad(ps3::gFrameX, Y(innerTop), ps3::gFrameW, divLw, 1, 1, 1, 0.55f * ap);
    drawQuad(ps3::gFrameX, Y(innerBot), ps3::gFrameW, divLw, 1, 1, 1, 0.55f * ap);

    const float bodyCx = XC(VW * 0.5f) + slidePx;

    if (mPs3WizId == WS_BT_INFO) {
        // Information panel for a registered device (key/value rows).
        struct KV { const char* k; std::string v; };
        std::vector<KV> rows = {
            {"Device Name",       mBtWizSelName.empty() ? mBtWizSelAddr : mBtWizSelName},
            {"Bluetooth Address", mBtWizSelAddr},
            {"Type",              btTypeLabel(mBtWizSelCod)},
            {"Connection",        mBtWizSelConnected ? "Connected" : "Not Connected"},
        };
        float fs = FS(24.0f), ty = Y(innerTop + 150.0f);
        float lx = XC(VW * 0.28f) + slidePx, rx = XC(VW * 0.72f) + slidePx;
        for (auto& kv : rows) {
            ps3DlgText(kv.k, lx, ty, fs, 0.78f, 0.78f, 0.82f, ap, 0);
            ps3DlgText(kv.v.c_str(), rx, ty, fs, 1, 1, 1, ap, 2);
            ty += DS(54.0f);
        }
    } else if (mPs3WizId == WS_BT_BD_REMOTE) {
        // Instruction text + a stylised vertical BD remote (1:1 with the web
        // bd_remote illustration: body, IR window, D-pad, button grid, and the
        // START + ENTER buttons highlighted in yellow).
        float fs = FS(24.0f), lh = DS(34.0f);
        std::vector<std::string> lines = wrap(d.body, fs);
        float ty = Y(innerTop + 70.0f);
        for (auto& ln : lines) { if (!ln.empty()) ps3DlgText(ln.c_str(), bodyCx, ty, fs, 0.95f, 0.95f, 0.95f, ap, 1); ty += lh; }
        float rw = DS(132.0f), rh = DS(360.0f);
        float rx = bodyCx - rw * 0.5f, rry = Y(innerTop + 230.0f);
        drawRoundedRect(rx, rry, rw, rh, DS(18.0f), 0.80f, 0.80f, 0.84f, ap);                 // body
        drawRoundedRect(rx, rry + rh * 0.5f, rw, rh * 0.5f, DS(18.0f), 0.50f, 0.50f, 0.55f, 0.55f * ap); // lower shade
        drawQuad(bodyCx - DS(22.0f), rry + DS(14.0f), DS(44.0f), DS(9.0f), 0.08f, 0.08f, 0.10f, ap);     // IR window
        ps3FillCircle(bodyCx, rry + DS(82.0f), DS(30.0f), 0.42f, 0.42f, 0.47f, ap);           // D-pad
        ps3FillCircle(bodyCx, rry + DS(82.0f), DS(11.0f), 0.20f, 0.20f, 0.23f, ap);
        for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++)                                // 3x3 button grid
            ps3FillCircle(bodyCx + (c - 1) * DS(34.0f), rry + DS(160.0f) + r * DS(40.0f), DS(11.0f), 0.24f, 0.24f, 0.27f, ap);
        // START + ENTER pills (yellow) with dark labels.
        float pw = DS(52.0f), ph = DS(24.0f), py = rry + rh - DS(54.0f);
        drawRoundedRect(bodyCx - DS(58.0f), py, pw, ph, DS(6.0f), 0.99f, 0.88f, 0.29f, ap);
        drawRoundedRect(bodyCx + DS(6.0f),  py, pw, ph, DS(6.0f), 0.99f, 0.88f, 0.29f, ap);
        ps3DlgText("START", bodyCx - DS(32.0f), py + DS(16.0f), FS(13.0f), 0.1f, 0.1f, 0.1f, ap, 1);
        ps3DlgText("ENTER", bodyCx + DS(32.0f), py + DS(16.0f), FS(13.0f), 0.1f, 0.1f, 0.1f, ap, 1);
    } else if (d.kind == WK_INFO || d.kind == WK_RESULT) {
        float fs = FS(26.0f), lh = DS(36.0f);
        std::string body = d.body;
        if (mPs3WizId == WS_SAVE) body = "Internet connection settings have been completed.\n\nSave completed.";
        // Bluetooth register result reflects the real pairing outcome.
        bool ok = true;
        if (mPs3WizId == WS_BT_REGISTER_DONE && !mBtWizOpOk) {
            body = "The device could not be registered.\nMake sure the device is in pairing mode and try again.";
            ok = false;
        }
        std::vector<std::string> lines = wrap(body, fs);
        float cy = (innerTop + innerBot) * 0.5f;
        float ty = Y(cy) - (float)((int)lines.size() - 1) * lh * 0.5f;
        if (d.kind == WK_RESULT) {   // status mark clear ABOVE the text block
            float r = DS(26.0f), ccx = bodyCx, ccy = ty - DS(58.0f);
            if (ok) {
                ps3StrokeRing(ccx, ccy, r, r, DS(3.0f), 0.45f, 0.9f, 0.45f, ap);
                ps3ThickLine(ccx - r * 0.45f, ccy + r * 0.05f, ccx - r * 0.1f, ccy + r * 0.45f, DS(3.0f), 0.5f, 0.95f, 0.5f, ap);
                ps3ThickLine(ccx - r * 0.1f, ccy + r * 0.45f, ccx + r * 0.5f, ccy - r * 0.4f, DS(3.0f), 0.5f, 0.95f, 0.5f, ap);
            } else {   // red X for a failed registration
                ps3StrokeRing(ccx, ccy, r, r, DS(3.0f), 0.95f, 0.45f, 0.45f, ap);
                ps3ThickLine(ccx - r * 0.4f, ccy - r * 0.4f, ccx + r * 0.4f, ccy + r * 0.4f, DS(3.0f), 0.95f, 0.5f, 0.5f, ap);
                ps3ThickLine(ccx - r * 0.4f, ccy + r * 0.4f, ccx + r * 0.4f, ccy - r * 0.4f, DS(3.0f), 0.95f, 0.5f, 0.5f, ap);
            }
        }
        for (auto& ln : lines) { if (!ln.empty()) ps3DlgText(ln.c_str(), bodyCx, ty, fs, 0.95f, 0.95f, 0.95f, ap, 1); ty += lh; }
    } else if (d.kind == WK_CHOOSER) {
        // Build the option list. Most choosers use the static d.opts; the Bluetooth
        // Manage screen is dynamic ("Register New Device" + the bonded devices), and
        // the per-device options screen prepends the selected device's name.
        std::vector<std::string> opts;
        std::string body = d.body;
        if (mPs3WizId == WS_BT_MANAGE) {
            if (!mBtWizRadioOn) {
                opts.push_back("Turn Bluetooth On");   // radio off: only the toggle
            } else {
                opts.push_back("Register New Device");
                opts.push_back("Receive Registration Request");
                { std::lock_guard<std::mutex> lk(mBtWizMutex);
                  for (auto& b : mBtWizBonded) {
                      std::string lbl = b.name.empty() ? b.address : b.name;
                      if (b.connected) lbl += "   (Connected)";
                      opts.push_back(lbl);
                  } }
                opts.push_back("Turn Bluetooth Off");
            }
            // Live-refresh the bonded list + radio state every ~2s while sitting on
            // this screen, so an external connect/disconnect (headphones reconnecting
            // outside the menu) updates the (Connected) marker without re-entering.
            float dt = mEffectTime - mBtWizManageRefreshT; if (dt < 0.0f) dt += 500.0f;
            if (dt > 2.0f) { btWizRefreshBondedAsync(); mBtWizManageRefreshT = mEffectTime; }
        } else if (mPs3WizId == WS_BT_DEVICE_OPTS) {
            body = std::string("Registered Device:  ") + mBtWizSelName;
            for (int i = 0; i < 8 && d.opts[i]; i++) opts.push_back(d.opts[i]);
        } else {
            for (int i = 0; i < 8 && d.opts[i]; i++) opts.push_back(d.opts[i]);
        }
        float fs = FS(24.0f);
        std::vector<std::string> bl = wrap(body, fs);
        float by = Y(innerTop + 95.0f);
        for (auto& ln : bl) { if (!ln.empty()) ps3DlgText(ln.c_str(), bodyCx, by, fs, 0.95f, 0.95f, 0.95f, ap, 1); by += DS(32.0f); }
        int n = (int)opts.size();
        // The Manage list is dynamic (a background refresh can shrink it); keep the
        // cursor in range so the highlight never vanishes and selection stays valid.
        if (mPs3WizSel >= n) mPs3WizSel = n > 0 ? n - 1 : 0;
        if (mPs3WizSel < 0) mPs3WizSel = 0;
        const float optTopV = innerTop + 230.0f, sp2 = 46.0f;
        float availH = innerBot - optTopV - 90.0f;
        int vis = (int)(availH / sp2); if (vis < 3) vis = 3;
        int first = 0; if (n > vis) { first = mPs3WizSel - vis / 2; if (first < 0) first = 0; if (first > n - vis) first = n - vis; }
        for (int i = first; i < n && i < first + vis; i++)
            ps3DlgOption(opts[i].c_str(), bodyCx, Y(optTopV + (i - first) * sp2), i == mPs3WizSel, false, ap, S);
        if (first > 0) ps3DlgText("▲", bodyCx, Y(optTopV - 18.0f), FS(20.0f), 1, 1, 1, 0.5f * ap, 1);
        if (first + vis < n) ps3DlgText("▼", bodyCx, Y(optTopV + vis * sp2 + 6.0f), FS(20.0f), 1, 1, 1, 0.5f * ap, 1);
    } else if (d.kind == WK_CONFIRM) {
        float fs = FS(26.0f), lh = DS(36.0f);
        std::string cbody = d.body;
        if (mPs3WizId == WS_BT_INBOUND_CONFIRM) {   // dynamic: the requesting device
            cbody = mBtWizSelName + "  (" + mBtWizSelAddr + ")\nis requesting to register with this system.";
            if (!mBtWizInPasskey.empty()) cbody += "\n\nPass Key:  " + mBtWizInPasskey;
            cbody += "\n\nAccept this device?";
        }
        std::vector<std::string> bl = wrap(cbody, fs);
        float ty = Y((innerTop + innerBot) * 0.5f) - (float)((int)bl.size() - 1) * lh * 0.5f - DS(50.0f);
        for (auto& ln : bl) { if (!ln.empty()) ps3DlgText(ln.c_str(), bodyCx, ty, fs, 0.95f, 0.95f, 0.95f, ap, 1); ty += lh; }
        float bxc = bodyCx - DS(110.0f), byv = ty + DS(30.0f);
        ps3DlgOption("Yes", bxc, byv, mPs3WizSel == 0, false, ap, S);
        ps3DlgOption("No", bxc + DS(220.0f), byv, mPs3WizSel == 1, false, ap, S);
    } else if (d.kind == WK_PROGRESS) {
        float fs = FS(26.0f), lh = DS(36.0f);
        std::vector<std::string> lines = wrap(d.body, fs);
        float ty = Y((innerTop + innerBot) * 0.5f + 40.0f) - (float)((int)lines.size() - 1) * lh * 0.5f;
        for (auto& ln : lines) { if (!ln.empty()) ps3DlgText(ln.c_str(), bodyCx, ty, fs, 0.95f, 0.95f, 0.95f, ap, 1); ty += lh; }
        // While pairing, surface the system-generated pass key so the user can
        // verify it (numeric confirmation) or type it on the remote device.
        if (mPs3WizId == WS_BT_REGISTERING || mPs3WizId == WS_BT_INBOUND_PAIRING) {
            char pk[PROPERTY_VALUE_MAX] = {0};
            property_get("sys.gammaos.bt.passkey", pk, "");
            if (pk[0]) {
                std::string pkLine = std::string("Pass Key:  ") + pk;
                ps3DlgText(pkLine.c_str(), bodyCx, ty + DS(10.0f), FS(28.0f), 1.0f, 0.92f, 0.66f, ap, 1);
            }
        }
        // spinner: 8 dots around a circle, brightness sweeping.
        float ccx = bodyCx, ccy = Y((innerTop + innerBot) * 0.5f - 50.0f), rad = DS(26.0f);
        int lead = (int)(mEffectTime * 8.0f) % 8;
        for (int i = 0; i < 8; i++) {
            float a = (float)i / 8.0f * 2.0f * (float)M_PI - (float)M_PI * 0.5f;
            int dist = (lead - i + 8) % 8;
            float br = 0.25f + 0.75f * fmaxf(0.0f, 1.0f - dist * 0.18f);
            ps3FillCircle(ccx + cosf(a) * rad, ccy + sinf(a) * rad, DS(4.0f), 1, 1, 1, br * ap);
        }
    } else if (d.kind == WK_SCANLIST && mPs3WizId == WS_BT_DEVICE_LIST) {
        // Bluetooth device list: Device Name | Type, from the real inquiry results.
        ps3DlgText(d.body, bodyCx, Y(innerTop + 70.0f), FS(24.0f), 0.95f, 0.95f, 0.95f, ap, 1);
        std::vector<BtDevEntry> devs;
        { std::lock_guard<std::mutex> lk(mBtWizMutex); devs = mBtWizScan; }
        float rowX = XC(VW * 0.18f) + slidePx, typeX = XC(VW * 0.60f) + slidePx;
        ps3DlgText("Device Name", rowX, Y(innerTop + 118.0f), FS(18.0f), 1, 1, 1, 0.6f * ap, 0);
        ps3DlgText("Type", typeX, Y(innerTop + 118.0f), FS(18.0f), 1, 1, 1, 0.6f * ap, 0);
        int n = (int)devs.size();
        if (mPs3WizSel >= n) mPs3WizSel = n > 0 ? n - 1 : 0;   // keep cursor in range (list is dynamic)
        if (mPs3WizSel < 0) mPs3WizSel = 0;
        const float top = innerTop + 150.0f, pitch = 56.0f;
        int vis = (int)((innerBot - top - 20.0f) / pitch); if (vis < 3) vis = 3;
        int first = mPs3WizSel - vis / 2; if (first < 0) first = 0; if (n <= vis) first = 0; else if (first > n - vis) first = n - vis;
        if (n == 0)
            ps3DlgText("No devices found. Press the square button to scan again.", bodyCx, Y(top + 30.0f), FS(22.0f), 0.9f, 0.9f, 0.9f, ap, 1);
        for (int i = first; i < n && i < first + vis; i++) {
            float ry = Y(top + (i - first) * pitch);
            bool sel = (i == mPs3WizSel);
            if (sel) drawRoundedRect(rowX - DS(20.0f), ry - DS(24.0f), DS(ps3::XCF(VW * 0.66f)), DS(46.0f), DS(8.0f), 1, 1, 1, 0.14f * ap);
            std::string nm = devs[i].name.empty() ? devs[i].address : devs[i].name;
            ps3DlgText(nm.c_str(), rowX, ry + DS(7.0f), FS(sel ? 24.0f : 23.0f), 1, 1, 1, (sel ? 1.0f : 0.85f) * ap, 0);
            ps3DlgText(btTypeLabel(devs[i].cod), typeX, ry + DS(7.0f), FS(20.0f), 1, 1, 1, (sel ? 0.9f : 0.7f) * ap, 0);
            if (devs[i].bonded)
                ps3DlgText("(Paired)", typeX + DS(190.0f), ry + DS(7.0f), FS(18.0f), 0.6f, 0.85f, 0.6f, ap, 0);
        }
    } else if (d.kind == WK_SCANLIST) {
        ps3DlgText(d.body, bodyCx, Y(innerTop + 70.0f), FS(24.0f), 0.95f, 0.95f, 0.95f, ap, 1);
        std::vector<WifiNetEntry> aps;
        { std::lock_guard<std::mutex> lk(mWifiListMutex);
          for (auto& e : mWifiEntries) if (e.bssid != "__TOGGLE__") aps.push_back(e); }
        float rowX = XC(VW * 0.18f) + slidePx, valX = XC(VW * 0.78f) + slidePx;
        ps3DlgText("SSID", rowX, Y(innerTop + 118.0f), FS(18.0f), 1, 1, 1, 0.6f * ap, 0);
        ps3DlgText("Signal", valX, Y(innerTop + 118.0f), FS(18.0f), 1, 1, 1, 0.6f * ap, 0);
        int n = (int)aps.size();
        const float top = innerTop + 150.0f, pitch = 56.0f;
        int vis = (int)((innerBot - top - 20.0f) / pitch); if (vis < 3) vis = 3;
        int first = mPs3WizSel - vis / 2; if (first < 0) first = 0; if (n <= vis) first = 0; else if (first > n - vis) first = n - vis;
        if (n == 0)
            ps3DlgText("No networks found. Press X to rescan.", bodyCx, Y(top + 30.0f), FS(22.0f), 0.9f, 0.9f, 0.9f, ap, 1);
        for (int i = first; i < n && i < first + vis; i++) {
            float ry = Y(top + (i - first) * pitch);
            bool sel = (i == mPs3WizSel);
            if (sel) drawRoundedRect(rowX - DS(20.0f), ry - DS(24.0f), DS(ps3::XCF(VW * 0.66f)), DS(46.0f), DS(8.0f), 1, 1, 1, 0.14f * ap);
            float nx = rowX;
            bool locked = (aps[i].security != 0 && aps[i].security != 4);
            if (locked) {   // small padlock
                float lx = rowX + DS(7.0f), ly = ry;
                drawQuad(lx - DS(6.0f), ly - DS(2.0f), DS(12.0f), DS(9.0f), 1, 1, 1, 0.85f * ap);
                ps3StrokeRing(lx, ly - DS(2.0f), DS(4.0f), DS(4.0f), DS(2.0f), 1, 1, 1, 0.85f * ap);
                nx = rowX + DS(28.0f);
            }
            ps3DlgText(aps[i].ssid.c_str(), nx, ry + DS(7.0f), FS(sel ? 24.0f : 23.0f), 1, 1, 1, (sel ? 1.0f : 0.85f) * ap, 0);
            int bars = 0; int r = aps[i].rssi;
            if (r >= -55) bars = 4; else if (r >= -66) bars = 3; else if (r >= -77) bars = 2; else if (r >= -88) bars = 1;
            for (int b = 0; b < 4; b++) {
                float bh = DS(6.0f + b * 5.0f);
                drawQuad(valX + b * DS(11.0f), ry + DS(8.0f) - bh, DS(8.0f), bh, 1, 1, 1, (b < bars ? 0.95f : 0.25f) * ap);
            }
        }
    } else if (d.kind == WK_TEXT) {
        // The OSK overlays; show the field label + the value being typed IN the
        // field (real-IME style: the keyboard suppresses its own preview line for
        // wizard fields, so the value + caret live here), masked for password fields.
        ps3DlgText(d.label, XC(VW * 0.18f) + slidePx, Y(innerTop + 64.0f), FS(24.0f), 0.95f, 0.95f, 0.95f, ap, 0);
        float boxX = XC(VW * 0.18f) + slidePx, boxY = Y(innerTop + 84.0f), boxW = DS(ps3::XCF(VW * 0.64f)), boxH = DS(44.0f);
        drawRoundedRect(boxX, boxY, boxW, boxH, DS(6.0f), 0, 0, 0, 0.45f * ap);
        std::string val = d.mask ? maskPassword(mOskQuery) : mOskQuery;
        std::string composing = mOsk.im ? mOsk.im->composingText() : std::string();
        if (!d.mask && !composing.empty()) val += composing;
        float vfs = FS(24.0f);
        float vx = boxX + DS(12.0f);
        if (!val.empty()) {
            ps3DlgText(val.c_str(), vx, boxY + DS(30.0f), vfs, 1, 1, 1, ap, 0);
            vx += measureText(val.c_str(), vfs);
        }
        float blink = 0.5f + 0.5f * sinf(mEffectTime * 6.0f);
        drawQuad(vx + DS(1.0f), boxY + DS(8.0f), fmaxf(1.0f, DS(2.0f)), DS(28.0f), 1, 1, 1, blink * ap);
        // Inline validation message (re-opened field after a bad entry).
        if (!mPs3WizFieldError.empty())
            ps3DlgText(mPs3WizFieldError.c_str(), boxX, boxY + boxH + DS(24.0f), FS(19.0f),
                       1.0f, 0.46f, 0.42f, ap, 0);
    } else if (d.kind == WK_REVIEW) {
        // Settings List - 1:1 with the web review rows.
        struct KV { std::string k; std::string v; };
        std::vector<KV> rows;
        rows.push_back({"Connection Method", mPs3WizConn.empty() ? "Wired Connection" : mPs3WizConn});
        if (mPs3WizConn == "Wireless") {
            rows.push_back({"SSID", mPs3WizSsid.empty() ? "-" : mPs3WizSsid});
            rows.push_back({"Security", mPs3WizSecLabel.empty() ? "None" : mPs3WizSecLabel});
        } else if (!mPs3WizOpmode.empty()) {
            rows.push_back({"Speed and Duplex",
                mPs3WizOpmode == "Auto-Detect" ? "Auto-Detect"
                    : (mPs3WizSpeedDuplex.empty() ? "Auto-Detect" : mPs3WizSpeedDuplex)});
        }
        rows.push_back({"IP Address Setting", mPs3WizIpMode.empty() ? "Automatic" : mPs3WizIpMode});
        if (mPs3WizIpMode == "Manual") {
            rows.push_back({"IP Address", mPs3WizIpAddr.empty() ? "-" : mPs3WizIpAddr});
            rows.push_back({"Subnet Mask", mPs3WizSubnet.empty() ? "-" : mPs3WizSubnet});
            rows.push_back({"Default Router", mPs3WizRouter.empty() ? "-" : mPs3WizRouter});
        }
        rows.push_back({"Primary DNS", mPs3WizPdns.empty() ? "Automatic" : mPs3WizPdns});
        rows.push_back({"Secondary DNS", mPs3WizSdns.empty() ? "Automatic" : mPs3WizSdns});
        rows.push_back({"MTU", mPs3WizMtuMode == "Manual" ? (mPs3WizMtu.empty() ? "-" : mPs3WizMtu) : "Automatic"});
        rows.push_back({"Proxy Server", mPs3WizProxyMode.empty() ? "Do Not Use" : mPs3WizProxyMode});
        rows.push_back({"UPnP", mPs3WizUpnp.empty() ? "Enable" : mPs3WizUpnp});
        float fs = FS(23.0f);
        float availV = innerBot - (innerTop + 90.0f) - 30.0f;
        float lhV = fminf(44.0f, availV / (float)rows.size());
        float ty = Y(innerTop + 90.0f);
        float lx = XC(VW * 0.32f) + slidePx, rx = XC(VW * 0.68f) + slidePx;
        for (auto& kv : rows) {
            ps3DlgText(kv.k.c_str(), lx, ty, fs, 0.78f, 0.78f, 0.82f, ap, 0);
            ps3DlgText(kv.v.c_str(), rx, ty, fs, 1, 1, 1, ap, 2);   // right-aligned value column (web)
            ty += DS(lhV);
        }
    } else if (d.kind == WK_TEST) {
        std::string body;
        { std::lock_guard<std::mutex> lk(mPs3NetTestMutex); body = mPs3NetTestBody; }
        float fs = FS(24.0f), lh = DS(34.0f);
        std::vector<std::string> lines = wrap(body, fs);
        float ty = Y((innerTop + innerBot) * 0.5f) - (float)((int)lines.size() - 1) * lh * 0.5f;
        for (auto& ln : lines) { if (!ln.empty()) ps3DlgText(ln.c_str(), bodyCx, ty, fs, 0.95f, 0.95f, 0.95f, ap, 1); ty += lh; }
    }

    // ---- footer hints ----
    float hintY = Y(909.0f);
    float enterCX = XC(VW * 0.401f), cancelCX = XC(VW * 0.629f);
    if (d.kind == WK_TEXT) {
        // OSK draws its own hints.
    } else if (mPs3WizId == WS_BT_INBOUND_WAIT) {
        ps3DlgHint(cancelCX, false, "Cancel", hintY, S, ap);   // back out of receive mode
    } else if (d.kind == WK_PROGRESS) {
        // no input
    } else if (d.kind == WK_RESULT || mPs3WizId == WS_BT_INFO || (d.kind == WK_TEST && !mPs3NetTestActive)) {
        ps3DlgHint(cancelCX, false, "OK", hintY, S, ap);   // info/result screens dismiss with OK
    } else if (d.kind == WK_TEST) {
        // running: no hints
    } else if (mPs3WizId == WS_APLIST || mPs3WizId == WS_BT_DEVICE_LIST) {
        // Three slots: Enter (cross) / Cancel (circle) / Search (square = X button).
        float e3 = XC(VW * 0.34f), c3 = XC(VW * 0.5f), s3 = XC(VW * 0.66f);
        ps3DlgHint(e3, true, "Enter", hintY, S, ap);
        ps3DlgHint(c3, false, "Cancel", hintY, S, ap);
        // square glyph + "Search"
        {
            float fs = S * 22.0f / 16.0f, glyphR = S * 12.0f, gap = S * 12.0f;
            float lw = fmaxf(S * 2.0f, 1.5f), tw = measureText("Search", fs);
            float groupW = glyphR * 2.0f + gap + tw, left = s3 - groupW * 0.5f, gcx = left + glyphR;
            float h = glyphR * 0.78f;
            drawQuad(gcx - h, hintY - h, 2.0f * h, lw, 1, 1, 1, 0.95f * ap);          // top
            drawQuad(gcx - h, hintY + h - lw, 2.0f * h, lw, 1, 1, 1, 0.95f * ap);     // bottom
            drawQuad(gcx - h, hintY - h, lw, 2.0f * h, 1, 1, 1, 0.95f * ap);          // left
            drawQuad(gcx + h - lw, hintY - h, lw, 2.0f * h, 1, 1, 1, 0.95f * ap);     // right
            drawText("Search", left + glyphR * 2.0f + gap, hintY - 0.45f * 16.0f * fs, fs, 1, 1, 1, 0.95f * ap);
        }
    } else {
        ps3DlgHint(enterCX, true, "Enter", hintY, S, ap);
        ps3DlgHint(cancelCX, false, "Cancel", hintY, S, ap);
    }
}

// ===========================================================================
// Time Zone 3D-globe selector (1:1 web tzglobe screen, index.html 9344-9355,
// 9728-9735, 9794-9820, 10394-10421). Shared by the XMB Date and Time -> Time
// Zone view (mPs3TzActive) and the first-run setup wizard timezone step.
// ===========================================================================

// 0..1 XMB->globe cross-fade alpha (the web fp = min(1,(now-tzFadeStart)/360),
// fa = smoothstep). <0 fade start = fully shown.
float NanoMenu::ps3TzFadeAlpha() {
    if (mTzGlobeFadeStart < 0.0f) return 1.0f;
    float dt = mEffectTime - mTzGlobeFadeStart;
    if (dt < 0.0f) return 1.0f;   // mEffectTime wrapped (fmod 500s): the 360ms fade is long done
    float fp = dt / 0.360f;
    if (fp >= 1.0f) return 1.0f;
    return fp * fp * (3.0f - 2.0f * fp);   // smoothstep
}

// (Re)start the cross-fade and snap the globe to the currently selected zone so
// it opens already showing that location (web initGlobe sets tzGlobe lon/lat =
// target = the selected zone, then renderGlobe; the fade only cross-fades the
// composite, not the rotation).
void NanoMenu::beginTzGlobeFade() {
    // Warm up first: the globe textures load on the render thread on the first
    // render (GL has no context here on the input thread), which spikes one frame.
    // Hold on black through the warm-up so that spike isn't counted against the
    // fade; renderTimezoneGlobe starts the 360ms fade once the globe is ready.
    mTzGlobeWarmFrames = 0;
    mTzGlobeFadeStart = -1.0f;
    mTzSelOnOpen = mTzSelected;   // remember the applied zone so cancel can revert
    if (mTzSelected >= 0 && mTzSelected < (int)mTzEntries.size()) {
        const TimezoneEntry& z = mTzEntries[mTzSelected];
        ps3globe::snapTo(z.lon * (float)M_PI / 180.0f, z.lat * (float)M_PI / 180.0f);
    } else {
        ps3globe::snapTo(0.0f, 0.0f);
    }
}

void NanoMenu::openTimezoneGlobe() {
    buildTimezoneList();          // (re)reads persist.sys.timezone + pre-selects it
    mPs3TzActive = true;
    beginTzGlobeFade();
}

void NanoMenu::closeTimezoneGlobe(bool apply) {
    if (apply && mTzSelected >= 0 && mTzSelected < (int)mTzEntries.size()) {
        char cmd[256];
        snprintf(cmd, sizeof(cmd), "setprop persist.sys.timezone %s",
                 mTzEntries[mTzSelected].id.c_str());
        system(cmd);
        ALOGI("ps3menu: timezone set to %s", mTzEntries[mTzSelected].id.c_str());
    } else if (!apply) {
        // Cancel: revert the highlighted zone to the one that was applied when the
        // globe opened, so the Date and Time menu's "Time Zone" value (which reads
        // mTzSelected) keeps showing the real zone, not the last-hovered one.
        if (mTzSelOnOpen >= 0 && mTzSelOnOpen < (int)mTzEntries.size())
            mTzSelected = mTzSelOnOpen;
    }
    mPs3TzActive = false;
    mTzGlobeFadeStart = -1.0f;
    // Snap the menu animation to settled so the item list draws immediately when
    // the globe was open across an mEffectTime wrap (defensive; the wrap guards in
    // renderPs3Xmb already handle the math).
    mPs3ItemAnimStart = -1.0f;
    mPs3SubAnimStart = -1.0f;
    mPs3CatAnimActive = false;
    mPs3AnimItem = (float)ps3CurSel();
}

// Move the zone selection (wrapping) and re-aim the globe at the new city. The
// globe eases there over several frames (web wizNav tzglobe branch + tzGlobeTick).
void NanoMenu::tzGlobeNav(int dir) {
    int n = (int)mTzEntries.size();
    if (n <= 0) return;
    mTzSelected = (mTzSelected + dir + n) % n;
    const TimezoneEntry& z = mTzEntries[mTzSelected];
    ps3globe::setTarget(z.lon * (float)M_PI / 180.0f, z.lat * (float)M_PI / 180.0f);
}

void NanoMenu::renderTimezoneGlobe() {
    // Even 4-offset text outline (mode 1) for legibility over the globe, in both
    // the XMB and setup-wizard entry points (the setup path does not preset it).
    mTextOutlineMode = 1;
    // Layout solve for this panel (matches renderNetWizard).
    { ps3::LayoutParams lp; lp.panelW = mWidth; lp.panelH = mHeight; lp.uiScale = mPs3UiScale;
      ps3::layoutCompute(lp); }

    float dt = mFrameDt; if (dt < 0.0f) dt = 0.0f; if (dt > 0.1f) dt = 0.1f;
    ps3globe::tick(dt);

    // Warm-up: on the first open the three earth textures load on this thread on
    // the first render, which spikes one frame. Hold the screen on black (ap=0)
    // until the globe is loaded AND one further clean frame has passed, then start
    // the 360ms fade from there so the load spike isn't charged against it (without
    // this the fade alpha leaps ~0->1 in the spike frame, i.e. it "just appears").
    float ap;
    if (mTzGlobeWarmFrames >= 0) {
        ap = 0.0f;
        ps3globe::init();                 // load textures here on the render thread
        if (ps3globe::ready() && ++mTzGlobeWarmFrames >= 2) {
            mTzGlobeFadeStart = mEffectTime;   // begin the smooth fade now (post-spike)
            mTzGlobeWarmFrames = -1;
        }
    } else {
        ap = ps3TzFadeAlpha();
    }

    // Fade the globe in from a BLACK background: cover the wave/menu behind with an
    // opaque black fill, then composite the globe over it at the fade alpha so the
    // Earth + chrome rise out of black (not a cross-fade over the XMB).
    drawQuad(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f, 0.0f, 0.0f, 1.0f);
    ps3globe::render(mWidth, mHeight, sDrmRotMat, ap);

    // ---- chrome (1:1 web tzglobe header + drawWizTzGlobe), drawn on top ----
    float ui = mPs3UiScale; if (ui < 0.5f) ui = 0.5f; if (ui > 2.0f) ui = 2.0f;
    const float S = ps3::gScale / ui;
    const float offX = ps3::gFrameX + (ps3::gFrameW - S * ps3::XCF(ps3::VW)) * 0.5f;
    const float offY = ps3::gFrameY + ps3::gFrameH * 0.5f - S * (ps3::VH * 0.5f);
    const float VW = ps3::VW;
    const float innerTop = 199.0f, innerBot = 880.0f;
    auto X  = [&](float vx) { return S * vx + offX; };
    auto XC = [&](float vx) { return S * ps3::XCF(vx) + offX; };
    auto Y  = [&](float vy) { return S * vy + offY; };
    auto DS = [&](float v)  { return S * v; };
    auto FS = [&](float px) { return S * px / 16.0f; };

    // Header: Date/Time icon (xmb_icon_022) + "Time Zone" + the two dividers.
    if (mPs3TzHeaderTex == 0) mPs3TzHeaderTex = loadPs3IconTex("xmb_icon_022.png");
    float iconSz = DS(36.0f);
    if (mPs3TzHeaderTex)
        drawIconTex(mPs3TzHeaderTex, X(130.0f) - iconSz * 0.5f, Y(175.0f) - iconSz * 0.5f,
                    iconSz, iconSz, 1, 1, 1, ap);
    ps3DlgText("Time Zone", X(160.0f), Y(187.0f), FS(28.0f), 1, 1, 1, ap, 0);
    float divLw = fmaxf(1.0f, DS(1.0f));
    drawQuad(ps3::gFrameX, Y(innerTop), ps3::gFrameW, divLw, 1, 1, 1, 0.45f * ap);
    drawQuad(ps3::gFrameX, Y(innerBot), ps3::gFrameW, divLw, 1, 1, 1, 0.45f * ap);

    // Caption "Select a time zone." (web XCF(V.W*0.04), yT+56, 24px, left).
    ps3DlgText("Select a time zone.", XC(VW * 0.04f), Y(innerTop + 56.0f), FS(24.0f),
               1, 1, 1, 0.95f * ap, 0);

    // Right-aligned scrolling zone list "GMT+hh:mm  City" (web drawWizTzGlobe).
    const float rxDev = XC(VW * 0.965f);
    const float topV = innerTop + 36.0f, pitchV = 33.0f;
    const float availV = innerBot - topV - 24.0f;
    int vis = (int)(availV / pitchV); if (vis < 6) vis = 6;
    int n = (int)mTzEntries.size();
    int first = mTzSelected - vis / 2;
    if (first < 0) first = 0;
    if (n <= vis) first = 0; else if (first > n - vis) first = n - vis;

    // One right-aligned, vertically-centred row (canvas textBaseline 'middle').
    auto drawRowR = [&](const char* s, float midDev, float fs, float r, float g, float b,
                        float a, bool glow) {
        float w = measureText(s, fs);
        float x = rxDev - w;
        float topY = midDev - 0.45f * 16.0f * fs;
        if (glow) {
            int saved = mTextOutlineMode; mTextOutlineMode = 2;   // halo, no dark outline
            float em = 16.0f * fs;
            const float gr[2] = { em * 0.28f, em * 0.14f };
            for (int p = 0; p < 2; p++)
                for (int k = 0; k < 8; k++) {
                    float ang = (float)k / 8.0f * 2.0f * (float)M_PI;
                    drawText(s, x + cosf(ang) * gr[p], topY + sinf(ang) * gr[p], fs, 1, 1, 1, 0.13f * a);
                }
            drawText(s, x, topY, fs, r, g, b, a);
            mTextOutlineMode = saved;
        } else {
            drawText(s, x, topY, fs, r, g, b, a);
        }
    };

    int last = first + vis; if (last > n) last = n;
    for (int i = first; i < last; i++) {
        float midDev = Y(topV + (float)(i - first) * pitchV);
        bool sel = (i == mTzSelected);
        if (sel)
            drawRowR(mTzEntries[i].display.c_str(), midDev, FS(24.0f), 1, 1, 1, ap, true);
        else
            drawRowR(mTzEntries[i].display.c_str(), midDev, FS(21.0f), 1, 1, 1, 0.72f * ap, false);
    }
    if (first > 0)
        drawRowR("\xE2\x96\xB2", Y(topV - 18.0f), FS(18.0f), 1, 1, 1, 0.5f * ap, false);   // up arrow
    if (first + vis < n)
        drawRowR("\xE2\x96\xBC", Y(topV + (float)vis * pitchV), FS(18.0f), 1, 1, 1, 0.5f * ap, false);  // down arrow

    // Footer hints: Enter (select) / Cancel (back), same slots as the wizard.
    float hintY = Y(909.0f);
    ps3DlgHint(XC(VW * 0.401f), true, "Enter", hintY, S, ap);
    ps3DlgHint(XC(VW * 0.629f), false, "Cancel", hintY, S, ap);
}

} // namespace android
