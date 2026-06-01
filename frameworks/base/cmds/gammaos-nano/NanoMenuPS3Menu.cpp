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

// GammaOS Nano PS3 XMB menu. A faithful port of the PlayStation 3 XrossMediaBar
// layout (index.html drawXMB / drawItemList): a horizontal category bar with a
// vertical item list dropping out of the active category, rendered with the
// firmware geometry over the captured wave. The categories are populated from
// nano's real content (emulator systems + ROMs, recently played, settings), so
// the launcher keeps working. Gated by persist.gammaos.nano.ps3xmb.

// LOG_TAG must be defined before any header that conditionally defines it
// (NanoMenu.h pulls in utils/binder headers that set a default LOG_TAG).
#define LOG_TAG "GammaOSNano"

#include "NanoMenu.h"
#include "NanoMenuPS3.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <vector>

#include <GLES2/gl2.h>
#include <png.h>

#include <utils/Log.h>
#include <utils/SystemClock.h>

namespace android {

// ---------------------------------------------------------------------------
// small libpng loader -> mono-white (silvery) RGBA texture, for the PS3
// category icons. Tries the dev push dir then the shipped asset.
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
    // Force white, keep alpha as the shape (silvery monochrome look).
    for (size_t i = 0; i + 3 < pixels.size(); i += 4) {
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

// Draw an arbitrary GL texture (the PS3 category icons live outside the
// mIconTextures[] array drawIcon() indexes). Mirrors drawIcon().
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
    // Category icons: settings/photo/music/video/game/network = xmb_icon_001..006.
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
        it.iconTex = mIconTextures[16];     // generic game-item icon
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
        it.iconTex = mIconTextures[16];
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
            it.a = mSettingsItems[i].action;     // 0 Wi-Fi, 1 Bluetooth
            it.iconTex = mIconTextures[17];       // settings icon
            it.iconR = it.iconG = it.iconB = 1.0f;
            c.items.push_back(it);
        }
        mPs3Cats.push_back(c);
    }

    // --- Game (icon 005): Recently Played + each emulator system ---
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
            if (sys.roms.empty()) continue;       // only systems with ROMs
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
    if (mPs3CatIdx < 0 || mPs3CatIdx >= (int)mPs3Cats.size()) {
        // Land on Game by default.
        mPs3CatIdx = (mPs3Cats.size() >= 2) ? 1 : 0;
    }
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
// navigation
// ---------------------------------------------------------------------------
void NanoMenu::ps3XmbLeft() {
    if (!mPs3Stack.empty()) { ps3XmbBack(); return; }
    if (mPs3Cats.empty()) return;
    if (mPs3CatIdx > 0) {
        mPs3CatItemSel[mPs3CatIdx] = mPs3ItemIdx;
        mPs3CatIdx--;
        mPs3ItemIdx = mPs3CatItemSel[mPs3CatIdx];
    }
}

void NanoMenu::ps3XmbRight() {
    if (!mPs3Stack.empty()) {
        // Right enters the focused item's submenu, like X.
        ps3XmbSelect();
        return;
    }
    if (mPs3Cats.empty()) return;
    if (mPs3CatIdx < (int)mPs3Cats.size() - 1) {
        mPs3CatItemSel[mPs3CatIdx] = mPs3ItemIdx;
        mPs3CatIdx++;
        mPs3ItemIdx = mPs3CatItemSel[mPs3CatIdx];
    }
}

void NanoMenu::ps3XmbUp() {
    int& sel = ps3CurSel();
    if (sel > 0) sel--;
}

void NanoMenu::ps3XmbDown() {
    int& sel = ps3CurSel();
    int n = (int)ps3CurItems().size();
    if (sel < n - 1) sel++;
}

void NanoMenu::ps3XmbSelect() {
    std::vector<Ps3Item>& items = ps3CurItems();
    int sel = ps3CurSel();
    if (sel < 0 || sel >= (int)items.size()) return;
    Ps3Item it = items[sel];     // copy: pushing levels may reallocate
    switch (it.kind) {
        case PS3_SYSTEM: {
            Ps3Level lvl; buildRomSubmenu(it.a, lvl);
            mPs3Stack.push_back(lvl);
            mPs3SubDir = 1; mPs3SubAnim = 0.0f;
            break;
        }
        case PS3_RECENT_LIST: {
            Ps3Level lvl; buildRecentSubmenu(lvl);
            mPs3Stack.push_back(lvl);
            mPs3SubDir = 1; mPs3SubAnim = 0.0f;
            break;
        }
        case PS3_APP_LIST: {
            Ps3Level lvl; buildAppSubmenu(lvl);
            mPs3Stack.push_back(lvl);
            mPs3SubDir = 1; mPs3SubAnim = 0.0f;
            break;
        }
        case PS3_ROM: {
            mXmbSystemIndex = it.a;
            mXmbGameIndex = it.b;
            mSearchActive = false;
            launchXmbGame();
            break;
        }
        case PS3_RECENT: {
            mXmbSystemIndex = -1;
            mXmbGameIndex = it.a;
            mSearchActive = false;
            launchXmbGame();
            break;
        }
        case PS3_SETTING: {
            if (it.a == 0) openWifiScreen();
            else if (it.a == 1) openBtScreen();
            break;
        }
        case PS3_APP:
        default:
            // App launch is wired in a later pass.
            break;
    }
}

void NanoMenu::ps3XmbBack() {
    if (!mPs3Stack.empty()) {
        mPs3Stack.pop_back();
        mPs3SubDir = -1; mPs3SubAnim = 1.0f;
    }
}

// ---------------------------------------------------------------------------
// render
// ---------------------------------------------------------------------------
static float itemSlotY(int idx, int sel) {
    const float ITEM_ABOVE_BASE_Y = 120.0f;
    if (idx == sel) return ps3::ITEM_FOCUS_Y;
    if (idx < sel) {
        float k = (float)(sel - idx);
        return ITEM_ABOVE_BASE_Y - (k - 1.0f) * ps3::ITEM_SPACING;
    }
    return ps3::ITEM_FOCUS_Y + ps3::ITEM_ACTIVE_PAD + (float)(idx - sel) * ps3::ITEM_SPACING;
}

void NanoMenu::renderPs3Xmb() {
    if (!mPs3MenuBuilt) initPs3Menu();
    if (mPs3Cats.empty()) return;

    // Sub-screens (Wi-Fi / Bluetooth) opened from Settings render over the XMB.
    if (mMenuState == MENU_WIFI) { renderWifiScreen(); return; }
    if (mMenuState == MENU_BT)   { renderBtScreen();   return; }

    ps3::layoutComputeNative(mWidth, mHeight);

    // Frame-rate-independent smoothing toward the current selection.
    float dt = mFrameDt;
    if (dt < 0.0f) dt = 0.0f;
    if (dt > 0.1f) dt = 0.1f;
    float decay = 1.0f - expf(-12.0f * dt);
    mPs3AnimCat += ((float)mPs3CatIdx - mPs3AnimCat) * decay;
    if (fabsf(mPs3AnimCat - (float)mPs3CatIdx) < 0.004f) mPs3AnimCat = (float)mPs3CatIdx;
    float selTarget = (float)ps3CurSel();
    mPs3AnimItem += (selTarget - mPs3AnimItem) * decay;
    if (fabsf(mPs3AnimItem - selTarget) < 0.004f) mPs3AnimItem = selTarget;
    // Submenu collapse factor.
    if (mPs3SubDir > 0) { mPs3SubAnim += decay * (1.0f - mPs3SubAnim); if (mPs3SubAnim > 0.999f) mPs3SubAnim = 1.0f; }
    else                { mPs3SubAnim += decay * (0.0f - mPs3SubAnim); if (mPs3SubAnim < 0.001f) mPs3SubAnim = 0.0f; }
    bool inSub = !mPs3Stack.empty();
    float subT = inSub ? mPs3SubAnim : (mPs3SubDir < 0 ? mPs3SubAnim : 0.0f);

    // ---- category bar ----
    const float catSubShift = -ps3::CAT_SUBMENU_SHIFT_X * subT;
    const float catSubScale = 1.0f + (ps3::CAT_SUBMENU_SCALE - 1.0f) * subT;
    for (int i = 0; i < (int)mPs3Cats.size(); i++) {
        bool isActive = (i == mPs3CatIdx);
        float nonActiveSubFade = isActive ? 1.0f : (1.0f - subT);
        if (nonActiveSubFade <= 0.002f) continue;
        float dx = ((float)i - mPs3AnimCat) * ps3::CAT_SPACING;
        float baseSz = isActive ? ps3::CAT_ICON_ACTIVE : ps3::CAT_ICON_INACTIVE;
        float sz = baseSz * catSubScale;
        float x = ps3::CAT_X + dx + catSubShift;
        float activeYOff = isActive ? ps3::CAT_Y_ACTIVE_OFFSET * (1.0f - subT) : 0.0f;
        float y = ps3::CAT_Y + activeYOff;
        if (x < -160.0f || x > ps3::VW + 160.0f) continue;
        int catDist = abs(i - mPs3CatIdx);
        float alpha = isActive ? 1.0f : (catDist <= 2 ? ps3::CAT_INACTIVE_ALPHA : ps3::CAT_FAR_ALPHA);
        alpha *= nonActiveSubFade;
        // icon (centered at virtual x,y; position compressed, size uniform)
        float dsz = ps3::devS(sz);
        drawIconTex(mPs3Cats[i].iconTex,
                    ps3::devX(ps3::XCL(x, sz * 0.5f)),
                    ps3::devY(y - sz * 0.5f), dsz, dsz,
                    1.0f, 1.0f, 1.0f, alpha);
        // label under the active category
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

    // ---- item list (active category, or the submenu on the stack) ----
    std::vector<Ps3Item>& items = ps3CurItems();
    int sel = ps3CurSel();
    int s0 = (int)floorf(mPs3AnimItem);
    int s1 = s0 + 1;
    float frac = mPs3AnimItem - (float)s0;
    float xShift = inSub ? ps3::SUBMENU_TEXT_X_SHIFT * (1.0f - subT) : 0.0f; // children slide in

    float fTop = ps3::frameTopV();
    float fBot = fTop + ps3::frameHV();

    for (int i = 0; i < (int)items.size(); i++) {
        bool isActive = (i == sel);
        // interpolate the firmware slot Y between the two integer selections
        float yA = itemSlotY(i, s0);
        float yB = itemSlotY(i, s1);
        float y = yA + (yB - yA) * frac;
        if (y < fTop - 80.0f || y > fBot + 80.0f) continue;

        float alpha = isActive ? ps3::ALPHA_FOCUS : ps3::ALPHA_INACTIVE;
        if (y < fTop + 40.0f) alpha *= fmaxf(0.0f, (y - fTop) / 40.0f);
        if (y > fBot - 20.0f) alpha *= fmaxf(0.0f, (fBot - y) / 20.0f);
        if (alpha <= 0.01f) continue;

        const Ps3Item& it = items[i];
        float isz = isActive ? (inSub ? ps3::ITEM_ICON_SIZE : ps3::ITEM_ICON_ACTIVE) : ps3::ITEM_ICON_SIZE;
        float dsz = ps3::devS(isz);
        if (it.iconTex) {
            drawIconTex(it.iconTex,
                        ps3::devX(ps3::XCL(ps3::ITEM_ICON_X + xShift, isz * 0.5f)),
                        ps3::devY(y - isz * 0.5f), dsz, dsz,
                        it.iconR, it.iconG, it.iconB, alpha);
        }
        // text
        float tSize = isActive ? ps3::ITEM_TEXT_ACTIVE_SIZE : ps3::ITEM_TEXT_SIZE;
        float ts = ps3::fontScale(tSize);
        float tx = ps3::devX(ps3::XCP(ps3::ITEM_TEXT_X + xShift));
        float ty = ps3::baselineToTopY(ps3::devY(y), ts);
        if (isActive) {
            // Bright white with a cheap white glow approximation (a few offset
            // passes; the full dual-halo gaussian is a later pass).
            float g = 0.5f + 0.18f * sinf(mEffectTime * 4.18879f);
            float go = ps3::devS(2.0f);
            drawText(it.label.c_str(), tx + go, ty, ts, 1.0f, 1.0f, 1.0f, 0.35f * g);
            drawText(it.label.c_str(), tx - go, ty, ts, 1.0f, 1.0f, 1.0f, 0.35f * g);
            drawText(it.label.c_str(), tx, ty + go, ts, 1.0f, 1.0f, 1.0f, 0.35f * g);
            drawText(it.label.c_str(), tx, ty - go, ts, 1.0f, 1.0f, 1.0f, 0.35f * g);
            drawText(it.label.c_str(), tx, ty, ts, 1.0f, 1.0f, 1.0f, alpha);
        } else {
            drawText(it.label.c_str(), tx, ty, ts, 0.92f, 0.92f, 0.92f, alpha);
        }
        // value at the right edge (e.g. ROM count, system name)
        if (!it.value.empty()) {
            float vs = ps3::fontScale(ps3::ITEM_TEXT_SIZE);
            float vw = measureText(it.value.c_str(), vs);
            float vx = ps3::devX(ps3::XCF(ps3::VW - ps3::ITEM_VALUE_RIGHT_PAD)) - vw;
            drawText(it.value.c_str(), vx, ty, vs, 0.7f, 0.7f, 0.75f, alpha * 0.85f);
        }
    }
}

} // namespace android
