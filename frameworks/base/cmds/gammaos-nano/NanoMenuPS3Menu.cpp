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
#include "NanoVideo.h"
#include "NanoMenuPS3Globe.h"
#include "NanoMenuPS3Data.h"
#include "NanoMenuShaders.h" // kEffectNames/kActiveEffects/sActiveEffectIdx for the Wallpaper picker
#include "NanoMenuDrm.h"   // sDrmGlRotation / sDrmRotationDeg for ticker scissor
#include "NanoMenuUtils.h" // setLaunchRomPath for the Applications launch
#include "NanoI18n.h"      // trDyn() runtime translation of hardcoded UI strings
#include "NanoMenuStrings.h" // NanoLocale/LocaleInfo + nanoGetLocale/SetLocale/ApplyLocaleToSystem (System Language picker)
#include "stb_image.h"     // stbi_load for the cinfo hover background JPEG (impl lives in NanoMenuPS3Icons.cpp)

#include <ctype.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <unistd.h>
#include <time.h>
#include <thread>
#include <vector>
#include <functional>
#include <sys/stat.h>   // stat() for ROM file size in the Information page
#include <aaudio/AAudio.h>   // GammaEQ audio preview (looping playback)

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

// Small-panel readability boost for the fullscreen dialog/wizard body text. The
// 1:1 web font sizes are fine on a 720p+ panel but too small to read on a
// handheld like the 480-tall RG DS, so ramp the dialog text up on small screens
// only (big panels stay exactly 1:1 with the web app).
float NanoMenu::ps3DlgFontBoost() const {
    float d = fminf((float)mWidth, (float)mHeight);
    if (d >= 700.0f) return 1.0f;
    if (d <= 480.0f) return 1.30f;
    return 1.0f + (700.0f - d) / (700.0f - 480.0f) * 0.30f;
}
// Icons have no built-in outline, so draw an even 4-direction dark silhouette
// (left/right/up/down) behind the icon. 4 (not 8) copies keeps the per-frame draw
// count low - the menu has ~16 visible icons. Symmetric, so no clipped side.
static const float kStroke4X[4] = { -1.0f, 1.0f, 0.0f, 0.0f };
static const float kStroke4Y[4] = {  0.0f, 0.0f,-1.0f, 1.0f };
void NanoMenu::drawIconStroke(unsigned int tex, float x, float y, float w, float h, float a) {
    // A 5% dark silhouette under a fading icon is invisible against the wave;
    // skipping it culls four textured quads per icon during reveals/edge fades.
    if (a <= 0.05f || tex == 0) return;
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
    if (mTextLocSharp >= 0) glUniform1f(mTextLocSharp, 0.0f);   // icon shadow: no glyph edge-sharpen
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
    if (mTextLocSharp >= 0) glUniform1f(mTextLocSharp, 0.0f);   // icons: no glyph edge-sharpen
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
    // Resolve the settings binding once here (the only producer of items whose label
    // can match kPs3Bindings) so resolvePs3ItemValue does not re-scan the table by
    // string-compare for every visible item every frame. nullptr for non-bound rows.
    it.binding = ps3BindingFor(it.label);
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
    // Quick Menu category icon (free slot 6): the xmb_icon_054 power glyph + its
    // glass normal map, so the new category renders the live-wave glass effect.
    mPs3CatTex[6]  = loadPs3IconTex("xmb_icon_054.png");
    mPs3CatNmap[6] = nmapForIcon(54);
    buildPs3Cats();
    // Pre-warm everything the first draw of any submenu would otherwise build
    // lazily inside a single frame: the glass normal maps for every icon in
    // the static DATA tree, and the glyph-atlas entries (+ width cache) for
    // every label/description/value string. The lazy path costs a one-shot
    // ~100ms frame on the FIRST entry into a submenu (a visible hitch, and in
    // the in-game overlay it competes with the still-running app); here the
    // cost lands in startup, hidden behind the boot intro.
    //
    // CRITICAL: warm at the EXACT scales the menu renders, not a single 2.0f.
    // Glyphs are rasterized per display-pixel-size (the per-size cache key), so
    // warming at the wrong scale caches the wrong bitmaps and the real render
    // still rasterizes on demand on first visit. The XMB draws item labels at
    // ITEM_TEXT_SIZE (inactive) and ITEM_TEXT_ACTIVE_SIZE (focused), values at
    // ITEM_TEXT_SIZE, descriptions at gDescSize, category labels at
    // CAT_LABEL_SIZE - warm each string at the scale(s) it actually uses.
    {
        // Compute the layout the SAME way the menu render does - it folds the UI
        // zoom (mPs3UiScale) into gScale via layoutCompute. layoutComputeNative
        // (uiScale 1.0) would warm the wrong pixel size, so every label would
        // still rasterize on demand on first scroll (the microstutter).
        { ps3::LayoutParams lp; lp.panelW = mWidth; lp.panelH = mHeight;
          lp.uiScale = mPs3UiScale; ps3::layoutCompute(lp); }
        const float sLabel  = ps3::fontScale(ps3::ITEM_TEXT_SIZE);         // inactive item
        const float sActive = ps3::fontScale(ps3::ITEM_TEXT_ACTIVE_SIZE);  // focused item
        const float sDesc   = ps3::fontScale(ps3::gDescSize);              // subtitle
        const float sCat    = ps3::fontScale(ps3::CAT_LABEL_SIZE);         // category label
        auto warmStr = [&](const char* s, float a, float b) {
            if (!s || !*s) return;
            (void)measureText(s, a);
            if (b > 0.0f) (void)measureText(s, b);
        };
        // (a) Static DATA tree: glass normal maps + every label/desc/value glyph.
        std::function<void(const Ps3DataItem*, int)> warm =
            [&](const Ps3DataItem* items, int n) {
                for (int i = 0; i < n; i++) {
                    if (items[i].icon >= 0) (void)nmapForIcon(items[i].icon);
                    warmStr(items[i].name,  sLabel, sActive);
                    warmStr(items[i].desc,  sDesc, 0.0f);
                    warmStr(items[i].value, sLabel, 0.0f);
                    if (items[i].children && items[i].childCount > 0)
                        warm(items[i].children, items[i].childCount);
                }
            };
        for (int ci = 0; ci < kPs3DataCatCount; ci++) {
            warmStr(kPs3DataCats[ci].name, sCat, 0.0f);
            warm(kPs3DataCats[ci].items, kPs3DataCats[ci].itemCount);
        }
        // (b) Dynamically-built rows (console/system tiles, Recently Played,
        // Applications, Game Systems): their labels/values are NOT in the static
        // DATA tree, so without this they rasterize on demand on first scroll.
        for (const auto& cat : mPs3Cats) {
            warmStr(cat.name.c_str(), sCat, 0.0f);
            for (const auto& it : cat.items) {
                warmStr(it.label.c_str(), sLabel, sActive);
                warmStr(it.desc.c_str(),  sDesc, 0.0f);
                warmStr(it.value.c_str(), sLabel, 0.0f);
            }
        }
        // (c) On-screen keyboard glyphs. The OSK was the one screen NOT warmed
        // here, so opening it cold-rasterized ~95 printable ASCII at its own
        // (un-prewarmed) pixel sizes in a single frame - a burst of on-demand
        // reads off the lz4 system image. On this RAM-tight EROFS/loop panel that
        // burst spiked memory hard enough to trip the low-memory killer and
        // thrash the device. Warm every printable ASCII at the exact scales
        // renderOsk()/oskLayoutBox() draw at (b.sf = min(W/1080,H/720)), so the
        // keyboard opens entirely from cache and touches the font image not at
        // all. ASCII covers all keycaps, the footer hint and the action label;
        // non-Latin keycaps are handled by their own locale path.
        {
            const float osf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
            // The distinct multipliers renderOsk applies to b.sf (preview 2.0,
            // keys 1.85/1.35, candidate 1.7, action 1.6, lang 1.5, superscript
            // 1.0/0.85).
            static const float oskMul[] = {2.0f, 1.85f, 1.7f, 1.6f, 1.5f, 1.35f, 1.0f, 0.85f};
            char ch[2] = {0, 0};
            for (float m : oskMul) {
                const float sc = m * osf;
                for (int c = 0x20; c < 0x7f; c++) { ch[0] = (char)c; (void)measureText(ch, sc); }
            }
        }
    }
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
    if (out.items.empty()) {
        // PS3 parity: an empty list shows the dim "There are no titles" row
        // (inert DATA leaf, no icon) instead of a blank column.
        Ps3Item it;
        it.label = "There are no titles";
        it.kind = PS3_DATA_LEAF; it.action = 0;
        it.iconTex = 0; it.nmapTex = 0;
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
        int matchSys = -1;
        for (size_t s = 0; s < mXmbSystems.size(); s++) {
            if (mXmbSystems[s].shortname == mXmbRecent[i].systemName
                || (!mXmbRecent[i].romDir.empty() && mXmbSystems[s].romDir == mXmbRecent[i].romDir)) {
                matchSys = (int)s; break;
            }
        }
        if (matchSys >= 0) {
            resolveSystemIcon(mXmbSystems[matchSys].iconRef, &it.iconTex, &it.nmapTex);
            it.iconR = mXmbSystems[matchSys].iconR;
            it.iconG = mXmbSystems[matchSys].iconG;
            it.iconB = mXmbSystems[matchSys].iconB;
        } else {
            it.iconTex = mIconTextures[15]; it.nmapTex = bevelForIconIdx(15);   // generic recent icon
            it.iconR = it.iconG = it.iconB = 1.0f;
        }
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

// Quick Menu action codes (Ps3Item.a when kind == PS3_QUICK). Dispatched in
// ps3XmbSelect(). These mirror the GammaOS Nano legacy global actions.
enum {
    QA_RESUME_AUDIO = -1, // reopen the Now-Playing screen when audio is playing in the background
    QA_BRIGHTNESS = 0,   // brightness HUD (Left/Right adjusts the row in place)
    QA_PERFORMANCE,      // performance-mode side-panel chooser (theme key 10)
    QA_CLOSE_APP,        // close the current foreground app (overlay only)
    QA_KILL_BG,          // force-stop background apps (spare the foreground)
    QA_KILL_ALL,         // force-stop all third-party apps
    QA_POWER_SUBMENU,    // open the Power submenu
    QA_RESTART,          // reboot
    QA_POWEROFF,         // shutdown
    QA_RECOVERY,         // reboot to recovery
    QA_SAFEMODE,         // reboot to safe mode
    QA_BOOT_ANDROID,     // exit nano -> full Android
    QA_QUICK_SETTINGS,   // open the Quick Settings submenu (ported GammaOS QS tiles)
    QA_NOTIFICATIONS,    // open the Notifications submenu (active notifications)
    QA_SECONDARY_DISPLAY,// toggle the external display (cmd display enable/disable-display 2)
    QA_LAUNCH_CALIBRATION,// am start the LineageParts gamepad calibration activity
    QA_LAUNCH_REMAP,     // am start the LineageParts gamepad button-remap activity
    QA_NOTIF_DISMISS,    // dismiss (snooze ~1yr) the focused notification, then refresh
};

void NanoMenu::buildPs3Cats() {
    mPs3Cats.clear();
    int gameCatRuntimeIdx = -1;
    int settingsCatRuntimeIdx = -1;
    int musicCatRuntimeIdx = -1;
    int photoCatRuntimeIdx = -1;
    int videoCatRuntimeIdx = -1;
    mPs3QuickCatIdx = -1;

    // ---- Quick Menu (GammaOS Nano legacy global actions) ----
    // Inserted BEFORE the web DATA categories so it is the FIRST (leftmost)
    // category, ahead of Settings. Items are glass-rendered from their xmb_icon
    // normal maps, identical to every other XMB icon. The Power group is a
    // runtime submenu built on select (buildQuickPowerSubmenu).
    {
        Ps3Cat q;
        q.name    = "Quick Menu";
        q.iconTex = mPs3CatTex[6];     // xmb_icon_054 power glyph (loaded in initPs3Menu)
        q.nmapTex = mPs3CatNmap[6];
        auto qItem = [&](const char* label, int action, int icon) {
            Ps3Item it; it.label = label; it.kind = PS3_QUICK; it.a = action;
            it.nmapTex = nmapForIcon(icon); it.iconR = it.iconG = it.iconB = 1.0f;
            q.items.push_back(it);
        };
        // Resume Audio Player: only present while music is playing in the background
        // (minimized). musicTick rebuilds the cats when audio starts/stops so it
        // appears/disappears. Always the first item so it is the default landing.
        if (mMusicResumeShown) qItem("Resume Audio Player", QA_RESUME_AUDIO, 3);
        qItem("Screen Brightness",   QA_BRIGHTNESS,    16);
        qItem("Performance Mode",    QA_PERFORMANCE,   21);
        qItem("Quick Settings",      QA_QUICK_SETTINGS, 21);
        qItem("Notifications",       QA_NOTIFICATIONS,  16);
        qItem("Close Current App",   QA_CLOSE_APP,     24);
        qItem("Kill Background Apps", QA_KILL_BG,       49);
        qItem("Kill All Apps",       QA_KILL_ALL,      25);
        qItem("Power",               QA_POWER_SUBMENU, 54);
        // Seed the cached Performance Mode row value from the persisted governor prop.
        { char pm[PROPERTY_VALUE_MAX]; property_get("persist.gammaos.performance_mode", pm, "stock");
          mPs3PerfModeLabel = !strcmp(pm, "max") ? "Max Performance"
                            : (!strcmp(pm, "powersave") ? "Power Saver" : "Normal"); }
        mPs3QuickCatIdx = (int)mPs3Cats.size();   // 0
        mPs3Cats.push_back(q);
    }

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
        if (strcmp(dc.id, "game") == 0)     gameCatRuntimeIdx     = (int)mPs3Cats.size();
        if (strcmp(dc.id, "settings") == 0) settingsCatRuntimeIdx = (int)mPs3Cats.size();
        if (strcmp(dc.id, "music") == 0)    musicCatRuntimeIdx    = (int)mPs3Cats.size();
        if (strcmp(dc.id, "photo") == 0)    photoCatRuntimeIdx    = (int)mPs3Cats.size();
        if (strcmp(dc.id, "video") == 0)    videoCatRuntimeIdx    = (int)mPs3Cats.size();
        mPs3Cats.push_back(c);
    }

    // Music library: append the scanned album folders BELOW the firmware
    // "Search for Media Servers" / "Playlists" items (user request: folders at the
    // bottom, not the top). Only when the library is loaded (lazy); empty until the
    // user imports a folder.
    if (musicCatRuntimeIdx >= 0 && mMusicLoaded && !mMusicTracks.empty()) {
        std::vector<Ps3Item> albums;
        buildMusicColumnItems(albums);
        Ps3Cat& music = mPs3Cats[musicCatRuntimeIdx];
        music.items.insert(music.items.end(), albums.begin(), albums.end());
    }

    // Photo library: append the scanned photo group-folders (By Month / Year /
    // Album / All) below the firmware "Search for Media Servers" / "Photo Gallery"
    // / "Playlists" items, mirroring the Music column. Lazy: empty until import.
    if (photoCatRuntimeIdx >= 0 && mPhotoLoaded && !mPhotos.empty()) {
        std::vector<Ps3Item> groups;
        buildPhotoColumnItems(groups);
        Ps3Cat& photo = mPs3Cats[photoCatRuntimeIdx];
        photo.items.insert(photo.items.end(), groups.begin(), groups.end());
    }

    // Video library: append the scanned video files below the firmware stub items
    // (BD Data Utility / Search for Media Servers / Video Editor & Uploader), mirroring
    // the Music column. Lazy: empty until the user imports a folder.
    if (videoCatRuntimeIdx >= 0 && mVideoLoaded && !mVideos.empty()) {
        std::vector<Ps3Item> vids;
        buildVideoColumnItems(vids);
        Ps3Cat& video = mPs3Cats[videoCatRuntimeIdx];
        video.items.insert(video.items.end(), vids.begin(), vids.end());
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
        { Ps3Item it; it.label = "Applications"; it.kind = PS3_APP_LIST;
          it.iconTex = mIconTextures[16]; it.nmapTex = bevelForIconIdx(16);
          it.iconR = it.iconG = it.iconB = 1.0f; nano.push_back(it); }
        for (size_t s = 0; s < mXmbSystems.size(); s++) {
            const XmbSystem& sys = mXmbSystems[s];
            if (!sys.enabled) continue;
            // Builtin systems stay hidden until ROMs are found (keeps the
            // column to what the user actually has). User-added systems always
            // show: the user explicitly created them and needs to see the tile
            // (and its 0 count) before any ROMs land in the scan folders.
            if (sys.roms.empty() && sys.builtin) continue;
            Ps3Item it; it.label = sys.name; it.kind = PS3_SYSTEM; it.a = (int)s;
            resolveSystemIcon(sys.iconRef, &it.iconTex, &it.nmapTex);
            it.iconR = sys.iconR; it.iconG = sys.iconG; it.iconB = sys.iconB;   // per-system tint
            char buf[32]; snprintf(buf, sizeof(buf), "%zu", sys.roms.size()); it.value = buf;
            nano.push_back(it);
        }
        game.items.insert(game.items.begin(), nano.begin(), nano.end());
    }

    // The "Game Systems" editor lives under Settings (it configures systems, so
    // it belongs with the other settings). Appended as the last Settings item,
    // glass wrench glyph (xmb_icon_022).
    if (settingsCatRuntimeIdx >= 0) {
        Ps3Item it; it.label = "Game Systems"; it.kind = PS3_GS_ROOT;
        it.iconTex = 0; it.nmapTex = nmapForIcon(22);
        it.iconR = it.iconG = it.iconB = 1.0f;
        mPs3Cats[settingsCatRuntimeIdx].items.push_back(it);
    }

    // Preserve the user's position across rebuilds: Game Systems edits rebuild
    // the cats constantly (toggle/icon/emulator/name changes), and resetting to
    // the top made backing out of Game Systems land on the FIRST Settings item
    // instead of the Game Systems row the user came from. Clamp the remembered
    // per-category selections into the new lists; only the very first build
    // (no category yet) takes the Game-default landing.
    std::vector<int> oldSel = std::move(mPs3CatItemSel);
    mPs3CatItemSel.assign(mPs3Cats.size(), 0);
    for (size_t c = 0; c < mPs3Cats.size() && c < oldSel.size(); c++) {
        int n = (int)mPs3Cats[c].items.size();
        int s = oldSel[c];
        if (s >= n) s = n - 1;
        mPs3CatItemSel[c] = s < 0 ? 0 : s;
    }
    if (mPs3CatIdx < 0 || mPs3CatIdx >= (int)mPs3Cats.size()) {
        // First build: land on Game by default.
        mPs3CatIdx = (gameCatRuntimeIdx >= 0) ? gameCatRuntimeIdx : 0;
        mPs3ItemIdx = 0;
    } else {
        int n = (int)mPs3Cats[mPs3CatIdx].items.size();
        if (mPs3ItemIdx >= n) mPs3ItemIdx = n > 0 ? n - 1 : 0;
        if (mPs3ItemIdx < 0) mPs3ItemIdx = 0;
        mPs3CatItemSel[mPs3CatIdx] = mPs3ItemIdx;
    }
    mPs3AnimItem = (float)mPs3ItemIdx; mPs3ItemAnimStart = -1.0f;
}

// Rebuild the category tree in place (a background rescan changed some
// system's ROM list, so Game tiles may appear/disappear or change count)
// WITHOUT yanking the user's position: re-find each category's selected item
// by label after the rebuild. Only called at the settled XMB root.
void NanoMenu::rebuildPs3CatsPreserveSel() {
    int catIdx = mPs3CatIdx;
    std::vector<std::string> selLabels(mPs3Cats.size());
    for (size_t c = 0; c < mPs3Cats.size(); c++) {
        int s = ((int)c == catIdx) ? mPs3ItemIdx
              : (c < mPs3CatItemSel.size() ? mPs3CatItemSel[c] : 0);
        if (s >= 0 && s < (int)mPs3Cats[c].items.size())
            selLabels[c] = mPs3Cats[c].items[s].label;
    }
    buildPs3Cats();
    if (catIdx >= 0 && catIdx < (int)mPs3Cats.size()) mPs3CatIdx = catIdx;
    for (size_t c = 0; c < mPs3Cats.size() && c < selLabels.size(); c++) {
        if (selLabels[c].empty()) continue;
        for (size_t i = 0; i < mPs3Cats[c].items.size(); i++) {
            if (mPs3Cats[c].items[i].label == selLabels[c]) {
                mPs3CatItemSel[c] = (int)i;
                if ((int)c == mPs3CatIdx) mPs3ItemIdx = (int)i;
                break;
            }
        }
    }
}

// Quick Menu -> Power submenu: the reboot/shutdown/recovery/safe-mode/android
// actions, built on select like buildRomSubmenu. Glass icons via nmap_NNN.
void NanoMenu::buildQuickPowerSubmenu(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.title = "Power";
    auto q = [&](const char* label, int action, int icon) {
        Ps3Item it; it.label = label; it.kind = PS3_QUICK; it.a = action;
        it.nmapTex = nmapForIcon(icon); it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    };
    q("Restart",      QA_RESTART,       8);   // refresh arrows
    q("Power Off",    QA_POWEROFF,     54);   // power glyph
    q("Recovery",     QA_RECOVERY,     22);   // wrench
    q("Safe Mode",    QA_SAFEMODE,     18);   // wrench + lock
    q("Boot Android", QA_BOOT_ANDROID, 44);   // android robot
}

// Quick Menu -> Quick Settings submenu: the custom GammaOS Quick Settings tiles
// surfaced as XMB options. Toggle/cycle/slider rows are PS3_DATA_LEAF bound to a
// kPs3Bindings entry (A opens the side-panel chooser, the row shows the live value);
// action rows (Secondary Display, Calibration, Remap) are PS3_QUICK with a QA_ code.
// Built lazily on open and discarded on pop, so it costs nothing while closed.
void NanoMenu::buildQuickSettingsSubmenu(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.title = "Quick Settings"; out.screenKind = 0;
    // A bound leaf: display label may differ from the binding label (dispatch and
    // value both prefer it.binding when set), letting us use the tile's name while
    // reusing an existing setting binding.
    auto leaf = [&](const char* label, const char* bindLabel, int icon) {
        Ps3Item it; it.label = label; it.kind = PS3_DATA_LEAF; it.action = 1;
        it.binding = ps3BindingFor(bindLabel ? bindLabel : label);
        it.nmapTex = nmapForIcon(icon); it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    };
    auto act = [&](const char* label, int qa, int icon, const char* val) {
        Ps3Item it; it.label = label; it.kind = PS3_QUICK; it.a = qa;
        if (val) it.value = val;
        it.nmapTex = nmapForIcon(icon); it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    };
    act ("Performance Mode", QA_PERFORMANCE, 21, nullptr);     // existing side-panel chooser
    leaf("Fan Speed", nullptr, 16);
    leaf("GammaShader", "CRT Shader", 16);
    act ("Secondary Display", QA_SECONDARY_DISPLAY, 16, mSecondaryDisplayOn ? "On" : "Off");
    leaf("GammaRGB", "Effect", 16);                            // GammaRGB effect chooser
    leaf("ABXY Swap", nullptr, 16);
    leaf("Deep Sleep Mode", "Ultra Low Power Saving", 16);
    leaf("Immersive Mode", nullptr, 16);
    leaf("DPAD/Analog Swap", nullptr, 16);
    leaf("Analog Sensitivity", "Global Sensitivity", 16);
    act ("Analog Calibration", QA_LAUNCH_CALIBRATION, 16, nullptr);
    leaf("Invert Left Stick", nullptr, 16);
    leaf("Invert Right Stick", nullptr, 16);
    leaf("DC Dimming Emulation", nullptr, 16);
    leaf("RetroArch Back Button Override", nullptr, 16);
    act ("Edit Button Mappings", QA_LAUNCH_REMAP, 16, nullptr);
    leaf("Screen Map", nullptr, 16);
}

// --- Notifications submenu ---------------------------------------------------
// A native list of the device's currently-active notifications, parsed on demand
// from `dumpsys notification --noredact` (single popen) when the submenu opens,
// and freed on close - nothing runs while it is shut. App = opPkg, title/body =
// android.title / android.text (or android.bigText). Dismiss = snooze ~1 year
// (the only safe mechanism reachable from this process; a true cancel is not).

// "String (value)" -> "value", taking the LAST ')' so values with parens survive.
static std::string nanoStripStringExtra(const std::string& v) {
    size_t p = v.find("String (");
    if (p == std::string::npos) return std::string();
    size_t a = p + 8;
    size_t b = v.rfind(')');
    if (b == std::string::npos || b < a) return std::string();
    return v.substr(a, b - a);
}

// A notification key is "userId|pkg|id|tag|uid". Only pass a validated key into a
// shell command: digits, '|', and the limited package/tag character set, no spaces
// or shell metacharacters.
static bool nanoValidNotifKey(const std::string& k) {
    if (k.empty() || k.find('|') == std::string::npos) return false;
    for (char c : k) {
        if (!(isalnum((unsigned char)c) || c=='|' || c=='.' || c=='_' ||
              c=='-' || c==':' || c=='/' || c=='#' || c=='+')) return false;
    }
    return true;
}

void NanoMenu::readNotifications(std::vector<NanoNotif>& out) {
    out.clear();
    FILE* f = popen("dumpsys notification --noredact 2>/dev/null", "r");
    if (!f) return;
    char buf[8192];
    bool inList = false, inExtras = false, have = false;
    NanoNotif cur;
    auto flush = [&]() { if (have) { out.push_back(cur); cur = NanoNotif(); have = false; inExtras = false; } };
    while (fgets(buf, sizeof(buf), f)) {
        std::string line(buf);
        while (!line.empty() && (line.back()=='\n' || line.back()=='\r')) line.pop_back();
        if (line.rfind("  Notification List:", 0) == 0) { inList = true; continue; }
        if (!inList) continue;
        if (line.rfind("    NotificationRecord(0x", 0) == 0) {
            flush(); have = true;
            size_t k = line.find(" key=");
            if (k != std::string::npos) {
                size_t s = k + 5, e = line.find(": Notification(", s);
                if (e != std::string::npos) cur.key = line.substr(s, e - s);
            }
            continue;
        }
        // Section terminator: a 3-space general-state line, or the next "  X:" header.
        if (have && line.rfind("   ", 0) == 0 && line.size() > 3 && line[3] != ' ') { flush(); inList = false; continue; }
        if (line.rfind("  Notification attention state:", 0) == 0) { flush(); inList = false; continue; }
        if (!have) continue;
        if (line.rfind("      opPkg=", 0) == 0)               cur.pkg = line.substr(12);
        else if (line.rfind("      key=", 0) == 0 && cur.key.empty()) cur.key = line.substr(10);
        else if (line.find("extras={") != std::string::npos) inExtras = true;
        else if (inExtras) {
            size_t t;
            if ((t = line.find("android.title=")) != std::string::npos)
                cur.title = nanoStripStringExtra(line.substr(t + 14));
            else if ((t = line.find("android.bigText=")) != std::string::npos) {
                std::string bt = nanoStripStringExtra(line.substr(t + 16));
                if (!bt.empty()) cur.text = bt;          // bigText preferred when present
            } else if ((t = line.find("android.text=")) != std::string::npos && cur.text.empty())
                cur.text = nanoStripStringExtra(line.substr(t + 13));
            else if (line.find('}') != std::string::npos && line.find('=') == std::string::npos)
                inExtras = false;
        }
    }
    flush();
    pclose(f);
}

void NanoMenu::buildNotificationsSubmenu(Ps3Level& out) {
    mNotifs.clear();
    readNotifications(mNotifs);                       // lazy: only runs on open
    buildNotificationsLevel(out);
}

// Build the submenu rows from the CURRENT mNotifs (no dumpsys read). Used after a
// dismiss so the row disappears immediately while the snooze applies asynchronously.
void NanoMenu::buildNotificationsLevel(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.title = "Notifications"; out.screenKind = 0;
    for (size_t i = 0; i < mNotifs.size(); i++) {
        const NanoNotif& n = mNotifs[i];
        Ps3Item it;
        if (!n.title.empty())      it.label = n.title;
        else if (!n.pkg.empty())   it.label = n.pkg;
        else                       it.label = "Notification";
        it.desc = n.text;                            // body shown as the active-row description
        it.kind = PS3_QUICK; it.a = QA_NOTIF_DISMISS; it.b = (int)i;
        it.nmapTex = nmapForIcon(16); it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    }
    if (out.items.empty()) {                          // PS3 empty-list parity
        Ps3Item it; it.label = "There are no notifications";
        it.kind = PS3_DATA_LEAF; it.action = 0; it.iconTex = 0; it.nmapTex = 0;
        it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    }
}

// ---------------------------------------------------------------------------
// Game Systems editor (dynamic systems config)
// ---------------------------------------------------------------------------

// The systems-list screen: every configured system (enabled AND disabled) with
// an On/Off value and the system's glass icon (dimmed when disabled). A = open
// the per-system editor; X = toggle enabled; L1/R1 = reorder.
void NanoMenu::buildGameSystemsList(Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.title = "Game Systems";
    out.screenKind = GS_LIST;
    for (size_t s = 0; s < mXmbSystems.size(); s++) {
        const XmbSystem& sys = mXmbSystems[s];
        Ps3Item it;
        it.label = sys.name;
        it.kind = PS3_GS_SYSTEM_ROW; it.a = (int)s;
        it.value = sys.enabled ? "On" : "Off";
        resolveSystemIcon(sys.iconRef, &it.iconTex, &it.nmapTex);
        // Per-system tint, dimmed when disabled so the On/Off state reads at a glance.
        float m = sys.enabled ? 1.0f : 0.45f;
        it.iconR = sys.iconR * m; it.iconG = sys.iconG * m; it.iconB = sys.iconB * m;
        out.items.push_back(it);
    }
    // Add New System (from the Daijishou catalog or blank). Y removes a custom row.
    { Ps3Item it; it.label = "Add New System..."; it.kind = PS3_GS_ADD;
      it.iconTex = 0; it.nmapTex = nmapForIcon(51);   // add glyph
      it.iconR = it.iconG = it.iconB = 1.0f; out.items.push_back(it); }
}

// Flip a system's enabled flag, persist the config, and refresh both the visible
// list and the Game category. Disabling clears the in-memory ROM list (so it
// drops out of Game) but keeps the on-disk cache for an instant re-enable.
void NanoMenu::gsToggleSystem(int sysIdx) {
    if (sysIdx < 0 || sysIdx >= (int)mXmbSystems.size()) return;
    XmbSystem& sys = mXmbSystems[sysIdx];
    sys.enabled = !sys.enabled;
    if (!sys.enabled) {
        sys.roms.clear(); sys.displayNames.clear(); sys.activePaths.clear();
        sys.pathExists = false; sys.scanned = true;   // hidden; not scanned
        ALOGI("ps3menu: disabled system %s", sys.id.c_str());
    } else {
        sys.scanned = false;
        loadRomCacheForSystem(sys);   // instant repopulate from the DE cache
        ALOGI("ps3menu: enabled system %s (%zu cached roms)", sys.id.c_str(), sys.roms.size());
    }
    saveSystemsConfig();
    gsRefreshStackLevels();
    buildPs3Cats();
}

// Move a system up (dir -1) or down (dir +1) within the config order. Renumbers
// every system's `order`, persists, and refreshes the list + Game category.
void NanoMenu::gsReorderSystem(int sysIdx, int dir) {
    if (sysIdx < 0 || sysIdx >= (int)mXmbSystems.size()) return;
    int j = sysIdx + dir;
    if (j < 0 || j >= (int)mXmbSystems.size()) return;
    std::swap(mXmbSystems[sysIdx], mXmbSystems[j]);
    for (size_t k = 0; k < mXmbSystems.size(); k++) mXmbSystems[k].order = (int)k;
    saveSystemsConfig();
    gsRefreshStackLevels();
    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == GS_LIST)
        mPs3Stack.back().sel = j;   // follow the moved row
    buildPs3Cats();
}

// Rebuild any Game Systems list / editor levels currently on the nav stack in
// place (preserving each level's selection) after a config change, so the
// visible screen and any parent GS screen stay in sync.
void NanoMenu::gsRefreshStackLevels() {
    for (auto& lvl : mPs3Stack) {
        int keep = lvl.sel;
        if (lvl.screenKind == GS_LIST) {
            buildGameSystemsList(lvl);
        } else if (lvl.screenKind == GS_EDITOR && mGsEditIdx >= 0) {
            buildGameSystemEditor(mGsEditIdx, lvl);
        } else {
            continue;
        }
        int n = (int)lvl.items.size();
        if (keep >= n) keep = n - 1;
        lvl.sel = keep < 0 ? 0 : keep;
    }
}

// ---- Per-system editor ----

// Editor field ids (Ps3Item.a for PS3_GS_FIELD rows).
enum {
    GSF_ENABLED = 0, GSF_NAME, GSF_SHORT, GSF_LTYPE, GSF_EMULATOR, GSF_CORE, GSF_PACKAGE,
    GSF_ARGS, GSF_INTENT, GSF_EXTS, GSF_SCAN, GSF_ICON, GSF_TINT,
    GSF_SCRAPER, GSF_SCRAPE_USER, GSF_SCRAPE_PASS, GSF_SCRAPE_NOW, GSF_RESET, GSF_DELETE
};

// Per-system scraper-override row value: "Default" (inherit global) or the chosen
// engine / Off. Empty override string == inherit.
static const char* scraperOverrideLabel(const std::string& ov) {
    if (ov == "screenscraper") return "ScreenScraper";
    if (ov == "thegamesdb")    return "TheGamesDB";
    if (ov == "off")           return "Off";
    return "Default";
}

static const char* launchTypeLabel(int lt) {
    switch (lt) {
        case NanoMenu::XLT_CUSTOM_PACKAGE:   return "Custom Package";
        case NanoMenu::XLT_RETROARCH_INTENT: return "RetroArch Intent";
        default:                             return "Libretro Core";
    }
}

void NanoMenu::buildGameSystemEditor(int sysIdx, Ps3Level& out) {
    out.items.clear(); out.sel = 0; out.screenKind = GS_EDITOR;
    if (sysIdx < 0 || sysIdx >= (int)mXmbSystems.size()) { out.title = "System"; return; }
    const XmbSystem& sys = mXmbSystems[sysIdx];
    out.title = sys.name.empty() ? "System" : sys.name;
    auto add = [&](const char* label, int field, const std::string& value) {
        Ps3Item it; it.label = label; it.kind = PS3_GS_FIELD; it.a = field;
        it.value = value; it.iconTex = 0; it.nmapTex = 0;   // clean icon-free form rows
        it.iconR = it.iconG = it.iconB = 1.0f;
        out.items.push_back(it);
    };
    add("Enabled", GSF_ENABLED, sys.enabled ? "On" : "Off");
    add("Display Name", GSF_NAME, sys.name);
    add("Short Name", GSF_SHORT, sys.shortname);
    add("Launch Type", GSF_LTYPE, launchTypeLabel(sys.launchType));
    // Emulator chooser (bundled Daijishou catalog: cores + standalone emulators).
    // Shows the current target; opens the searchable picker.
    add("Emulator", GSF_EMULATOR,
        sys.launchType == XLT_CUSTOM_PACKAGE
            ? (sys.packageName.empty() ? "Select..." : sys.packageName)
            : (sys.coreSo.empty() ? "Select..." : sys.coreSo));
    if (sys.launchType != XLT_CUSTOM_PACKAGE)
        add("Core (.so)", GSF_CORE, sys.coreSo.empty() ? "(none)" : sys.coreSo);
    if (sys.launchType == XLT_CUSTOM_PACKAGE)
        add("Package", GSF_PACKAGE, sys.packageName.empty() ? "(none)" : sys.packageName);
    if (sys.launchType == XLT_CUSTOM_PACKAGE || sys.launchType == XLT_RETROARCH_INTENT)
        add("Intent Template", GSF_INTENT, sys.launchIntent.empty() ? "(default)" : "(custom)");
    add("Launch Args", GSF_ARGS, sys.launchArgs.empty() ? "(none)" : sys.launchArgs);
    add("Extensions", GSF_EXTS, sys.acceptExts.empty() ? "(none)" : sys.acceptExts);
    // Scan Folders: opens the native folder picker (raw-path browser).
    { char v[40]; int nsrc = (int)sys.scanSources.size();
      if (nsrc == 0) snprintf(v, sizeof(v), "Default");
      else           snprintf(v, sizeof(v), "%d folder%s", nsrc, nsrc == 1 ? "" : "s");
      add("Scan Folders", GSF_SCAN, v); }
    // Icon picker row: opens the 849-icon grid. Shows the system's current icon.
    { Ps3Item it; it.label = "Icon"; it.kind = PS3_GS_FIELD; it.a = GSF_ICON;
      if (sys.iconRef.compare(0, 10, "retroarch:") == 0) {
          it.value = sys.iconRef.substr(10);
          for (char& c : it.value) if (c == '_') c = ' ';   // prettify sanitized name
      } else if (sys.iconRef.compare(0, 5, "file:") == 0)  it.value = "Custom";
      else                                                 it.value = "Default";
      resolveSystemIcon(sys.iconRef, &it.iconTex, &it.nmapTex);
      it.iconR = sys.iconR; it.iconG = sys.iconG; it.iconB = sys.iconB;
      out.items.push_back(it); }
    // Icon Tint: preview the tinted console glyph on the row so the colour reads.
    { Ps3Item it; it.label = "Icon Tint"; it.kind = PS3_GS_FIELD; it.a = GSF_TINT;
      it.value = "Colour";
      resolveSystemIcon(sys.iconRef, &it.iconTex, &it.nmapTex);
      it.iconR = sys.iconR; it.iconG = sys.iconG; it.iconB = sys.iconB;
      out.items.push_back(it); }
    // Boxart scraper per-system overrides (inherit the global Settings by default).
    add("Scraper", GSF_SCRAPER, scraperOverrideLabel(sys.scraperOverride));
    add("Scraper Username", GSF_SCRAPE_USER, sys.scrapeUser.empty() ? "Default" : sys.scrapeUser);
    add("Scraper Password", GSF_SCRAPE_PASS,
        sys.scrapePass.empty() ? std::string("Default")
                               : std::string((sys.scrapePass.size() > 8 ? 8 : sys.scrapePass.size()), '*'));
    add("Scrape This System", GSF_SCRAPE_NOW, "");
    if (sys.builtin) add("Reset to Default", GSF_RESET, "");
    else             add("Delete System", GSF_DELETE, "");
}

// Normalize a comma-separated extension list to lowercase, leading-dot tokens.
static std::string normalizeExts(const std::string& in) {
    std::string out, tok;
    auto flush = [&]() {
        while (!tok.empty() && tok.front() == ' ') tok.erase(tok.begin());
        while (!tok.empty() && tok.back() == ' ') tok.pop_back();
        if (!tok.empty()) {
            std::string t; if (tok[0] != '.') t += '.';
            for (char c : tok) t += (char)tolower((unsigned char)c);
            if (!out.empty()) out += ",";
            out += t;
        }
        tok.clear();
    };
    for (char c : in) { if (c == ',') flush(); else tok += c; }
    flush();
    return out;
}

// A on an editor field: open the OSK (text fields, prefilled) or a side-panel
// chooser (launch type / icon tint / reset), or toggle enabled in place.
void NanoMenu::gsEditField(int field) {
    int idx = mGsEditIdx;
    if (idx < 0 || idx >= (int)mXmbSystems.size()) return;
    switch (field) {
        case GSF_ENABLED:  gsToggleSystem(idx); return;   // refreshes editor + list + cats
        case GSF_LTYPE:    gsOpenLaunchTypeChooser(); return;
        case GSF_EMULATOR: gsOpenEmulatorPicker(); return;
        case GSF_SCAN:     gsOpenScanFolders(); return;
        case GSF_ICON:     openIconGridPicker(); return;
        case GSF_TINT:    gsOpenTintChooser(); return;
        case GSF_SCRAPER:      gsOpenScraperChooser(); return;
        case GSF_SCRAPE_USER:  gsEditScraperCred(false); return;
        case GSF_SCRAPE_PASS:  gsEditScraperCred(true); return;
        case GSF_SCRAPE_NOW:   scrapeOneSystem(idx); return;
        case GSF_RESET:   gsOpenResetConfirm(); return;
        case GSF_DELETE:  gsOpenRemoveConfirm(idx); return;
        default: break;
    }
    // Text fields (prefilled OSK).
    const XmbSystem& sys = mXmbSystems[idx];
    std::string cur, prompt;
    switch (field) {
        case GSF_NAME:    cur = sys.name;         prompt = "Display Name"; break;
        case GSF_SHORT:   cur = sys.shortname;    prompt = "Short Name"; break;
        case GSF_CORE:    cur = sys.coreSo;       prompt = "Core .so filename"; break;
        case GSF_PACKAGE: cur = sys.packageName;  prompt = "Target package (e.g. com.dsemu.drastic)"; break;
        case GSF_ARGS:    cur = sys.launchArgs;   prompt = "Launch args (am tokens)"; break;
        case GSF_INTENT:  cur = sys.launchIntent; prompt = "Intent template ({file.uri})"; break;
        case GSF_EXTS:    cur = sys.acceptExts;   prompt = "Extensions (comma-separated)"; break;
        default: return;
    }
    openOskForPassword(prompt, [this, idx, field](const std::string& val) {
        if (idx < 0 || idx >= (int)mXmbSystems.size()) return;
        XmbSystem& s = mXmbSystems[idx];
        switch (field) {
            case GSF_NAME:    if (!val.empty()) s.name = val; break;
            case GSF_SHORT:   if (!val.empty()) s.shortname = val; break;
            case GSF_CORE:    s.coreSo = val; break;
            case GSF_PACKAGE: s.packageName = val; break;
            case GSF_ARGS:    s.launchArgs = val; break;
            case GSF_INTENT:  s.launchIntent = val; break;
            case GSF_EXTS: {
                s.acceptExts = normalizeExts(val);
                // Extensions changed: invalidate + rescan this system. Skip the
                // synchronous scan if a background scan is in flight (it would
                // race the ROM vectors); the next periodic scan picks up the
                // new extensions safely.
                unlink(xmbCachePath(s).c_str());
                s.scanned = false;
                if (!mBgScanThreadRunning) scanOneSystemAsync(idx);
                break;
            }
            default: break;
        }
        if (s.launchType == XLT_CUSTOM_PACKAGE) s.launchPkg = s.packageName;
        saveSystemsConfig();
        gsRefreshStackLevels();
        buildPs3Cats();
    });
    mOskPasswordMode = false;
    mOskPlaintext = true;
    mOskQuery = cur;                       // prefill AFTER openOskForPassword (which clears it)
    mOsk.caret = (int)mOskQuery.size();
}

void NanoMenu::gsOpenLaunchTypeChooser() {
    if (mGsEditIdx < 0 || mGsEditIdx >= (int)mXmbSystems.size()) return;
    mPs3DlgOptions.clear(); mPs3DlgSwatch.clear();
    mPs3DlgKind = 1; mPs3DlgThemeKey = 20; mPs3DlgTitle = "Launch Type"; mPs3DlgBody.clear();
    // Two options: a RetroArch libretro core (fast native route), or an arbitrary
    // package launched via an am intent (Daijisho-style). sel 0 -> libretro-core,
    // sel 1 -> custom-package.
    mPs3DlgOptions.push_back("Libretro Core");  mPs3DlgSwatch.push_back(-1);
    mPs3DlgOptions.push_back("Custom Package");  mPs3DlgSwatch.push_back(-1);
    mPs3DlgSel = (mXmbSystems[mGsEditIdx].launchType == XLT_CUSTOM_PACKAGE) ? 1 : 0;
    mPs3DlgIconTex = 0; mPs3DlgIconNmap = nmapForIcon(22);
    mPs3DlgIconR = mPs3DlgIconG = mPs3DlgIconB = 1.0f;
    mPs3DlgOrigSel = mPs3DlgSel;
    mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgBlurValid = false;
}

// gsOpenTintChooser() is defined further down, next to the kPs3ColorOpts table.

// Per-system scraper override chooser (Default = inherit the global Settings).
// Commit handled in applyThemeSetting case 24.
void NanoMenu::gsOpenScraperChooser() {
    if (mGsEditIdx < 0 || mGsEditIdx >= (int)mXmbSystems.size()) return;
    mPs3DlgOptions.clear(); mPs3DlgSwatch.clear();
    mPs3DlgKind = 1; mPs3DlgThemeKey = 24; mPs3DlgTitle = "Scraper"; mPs3DlgBody.clear();
    static const char* kOpts[] = {"Default", "ScreenScraper", "TheGamesDB", "Off"};
    for (const char* s : kOpts) { mPs3DlgOptions.push_back(s); mPs3DlgSwatch.push_back(-1); }
    const std::string& ov = mXmbSystems[mGsEditIdx].scraperOverride;
    int sel = 0;
    if (ov == "screenscraper") sel = 1; else if (ov == "thegamesdb") sel = 2; else if (ov == "off") sel = 3;
    mPs3DlgSel = sel; mPs3DlgOrigSel = sel;
    mPs3DlgIconTex = 0; mPs3DlgIconNmap = nmapForIcon(22);
    mPs3DlgIconR = mPs3DlgIconG = mPs3DlgIconB = 1.0f;
    mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgBlurValid = false;
}

// Per-system scraper credential (ScreenScraper account) override via the OSK.
// masked = password field. Empty commit clears the override (inherit global).
void NanoMenu::gsEditScraperCred(bool masked) {
    int idx = mGsEditIdx;
    if (idx < 0 || idx >= (int)mXmbSystems.size()) return;
    const XmbSystem& sys = mXmbSystems[idx];
    std::string cur = masked ? std::string() : sys.scrapeUser;
    const char* prompt = masked ? "Scraper Password (blank = use global)"
                                : "Scraper Username (blank = use global)";
    openOskForPassword(prompt, [this, idx, masked](const std::string& val) {
        if (idx < 0 || idx >= (int)mXmbSystems.size()) return;
        XmbSystem& s = mXmbSystems[idx];
        if (masked) s.scrapePass = val; else s.scrapeUser = val;   // blank clears (inherit)
        saveSystemsConfig();
        gsRefreshStackLevels();
    });
    mOskPasswordMode = masked; mOskPlaintext = !masked;
    mOskQuery = cur; mOsk.caret = (int)mOskQuery.size();
}

void NanoMenu::gsOpenResetConfirm() {
    if (mGsEditIdx < 0 || mGsEditIdx >= (int)mXmbSystems.size()) return;
    mPs3DlgOptions.clear(); mPs3DlgSwatch.clear();
    mPs3DlgKind = 1; mPs3DlgThemeKey = 22; mPs3DlgTitle = "Reset to Default"; mPs3DlgBody.clear();
    mPs3DlgOptions.push_back("Cancel");           mPs3DlgSwatch.push_back(-1);
    mPs3DlgOptions.push_back("Reset to Default");  mPs3DlgSwatch.push_back(-1);
    mPs3DlgSel = 0;
    mPs3DlgIconTex = 0; mPs3DlgIconNmap = nmapForIcon(22);
    mPs3DlgIconR = mPs3DlgIconG = mPs3DlgIconB = 1.0f;
    mPs3DlgOrigSel = 0;
    mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgBlurValid = false;
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
    return ps3::ITEM_FOCUS_Y + ps3::gActivePad + (float)(idx - sel) * ps3::ITEM_SPACING;
}
// Item Y for a CONTINUOUS selection position (interpolates the slot model).
static float itemSlotYf(int idx, float selPos) {
    int s0 = (int)floorf(selPos);
    float frac = selPos - (float)s0;
    return itemSlotY(idx, s0) + (itemSlotY(idx, s0 + 1) - itemSlotY(idx, s0)) * frac;
}
static float ps3CatOffset(bool active, float t, float fromOff) {
    // Category slide rail uses easeOutCubic (web catAnim.ease), NOT smoothstep:
    // "fast start, smooth settle" - smoothstep is too floaty in the first half.
    return active ? fromOff * (1.0f - easeOutCubic(t)) : 0.0f;
}

// ---------------------------------------------------------------------------
// navigation - smooth continuous item position; timed category slide rail.
// ---------------------------------------------------------------------------

// Move the selection inside an open dialog (mirrors web navDialog/navItem/navCat).
// Up/Down (horizontal=false) scroll chooser lists only; Left/Right (horizontal=
// true) scroll choosers AND toggle a confirm dialog's Yes/No. Info pages ignore.
void NanoMenu::ps3DlgNav(int dir, bool horizontal) {
    if (mPs3DlgRomInfo) {   // rich Information page: Up/Down scroll the description; L/R inert
        if (!horizontal) { mPs3RomInfoScroll += dir; if (mPs3RomInfoScroll < 0) mPs3RomInfoScroll = 0; }
        return;             // the renderer clamps the upper bound (it knows the line count)
    }
    int n = (int)mPs3DlgOptions.size();
    if (mPs3DlgKind == 1) {
        if (mPs3DlgSlider) {
            // Numeric slider: Left/Right (horizontal) step the value; Up/Down ignored.
            if (!horizontal) return;
            float v = mPs3DlgSldVal + (float)dir * mPs3DlgSldStep;
            // snap to the step grid relative to min so partial offsets don't accumulate
            float steps = roundf((v - mPs3DlgSldMin) / mPs3DlgSldStep);
            v = mPs3DlgSldMin + steps * mPs3DlgSldStep;
            if (v < mPs3DlgSldMin) v = mPs3DlgSldMin;
            if (v > mPs3DlgSldMax) v = mPs3DlgSldMax;
            mPs3DlgSldVal = v;
            return;
        }
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
    if (mGSearchActive) return;   // results overlay ignores left/right
    if (mVidActive) {   // video player: Go To field / Scene grid / panel grid / rewind 10s
        if (mVidDlgActive) { if (mVidDlgKind == 1) mVidDlgSel = 0; return; }   // modal: left = Yes
        if (mVidResumeAsk) { mVidResumeSel = 0; return; }   // Resume prompt: left = Resume
        if (mVidGoToOpen) vidGoToMove(-1);
        else if (mVidSceneOpen) vidSceneMove(-1, 0);
        else if (mVidCpOpen) vidPanelMove(-1, 0);
        else vidSeek(-10.0);
        return;
    }
    if (mPs3OptActive) { if (mPs3OptSubOpen) xmbOptCloseSub(); else closeXmbOpt(); return; }   // Left: back out of a submenu, else dismiss (web optBack)
    if (mMpActive) {   // chooser ignores L/R; panel grid nav, or scrub back 5s with no panel
        if (mMpPlChooserActive) return;
        if (mMpCpOpen) mpOptMove(-1, 0);
        else { double base = mMpSeekPending ? mMpSeekTarget : mMusicPlayer.position();
               double p = base - 5.0; if (p < 0.0) p = 0.0;
               mMpSeekTarget = p; mMpSeekPending = true; mMpSeekInputT = mEffectTime; }
        return;
    }
    if (mPvPlChooserActive || mVidPlChooserActive) return;   // add-to-playlist chooser ignores left/right
    if (mPhotoMultiActive) { photoMultiLR(-1); return; }
    if (mPvActive) { if (mPvPlChooserActive || mPvWpMode || mPvTrimMode) return;
                     if (mPvPanel || mPvCpSub) pvPanelMove(-1, 0); else pvStep(-1); return; }
    if (ps3TopScreenKind() == GS_ICONGRID) { iconGridNav(-1, 0); return; }
    if (!mPs3DlgActive && ps3TopScreenKind() == PHOTO_GRID) { photoGridNav(-1, 0); return; }
    if (mPs3BrightSlider) { adjustBrightness(-1); return; }   // Quick Menu brightness slider modal
    if (mPs3TzActive) return;   // tzglobe list is vertical only
    if (mPs3LangActive) return; // language list is vertical only
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
    musicOnCatFocus();   // lazy-load the music library when the Music column is focused
    photoOnCatFocus();   // lazy-load the photo library when the Photo column is focused
    videoOnCatFocus();   // lazy-load the video library when the Video column is focused
}

void NanoMenu::ps3XmbRight() {
    if (mGSearchActive) return;   // results overlay ignores left/right
    if (mVidActive) {   // video player: Go To field / Scene grid / panel grid / forward 10s
        if (mVidDlgActive) { if (mVidDlgKind == 1) mVidDlgSel = 1; return; }   // modal: right = No
        if (mVidResumeAsk) { mVidResumeSel = 1; return; }   // Resume prompt: right = Play from beginning
        if (mVidGoToOpen) vidGoToMove(+1);
        else if (mVidSceneOpen) vidSceneMove(+1, 0);
        else if (mVidCpOpen) vidPanelMove(+1, 0);
        else vidSeek(10.0);
        return;
    }
    if (mPs3OptActive) { if (!mPs3OptSubOpen) xmbOptOpenSub(); return; }   // Right: open the focused row's submenu (web optOpenSub)
    if (mMpActive) {   // panel grid nav, or scrub fwd 5s with no panel (hold = continuous; debounced commit)
        if (mMpPlChooserActive) return;
        if (mMpCpOpen) mpOptMove(+1, 0);
        else { double base = mMpSeekPending ? mMpSeekTarget : mMusicPlayer.position();
               double d = mMusicPlayer.duration(); double np = base + 5.0;
               if (d > 0.0 && np > d) np = d;
               mMpSeekTarget = np; mMpSeekPending = true; mMpSeekInputT = mEffectTime; }
        return;
    }
    if (mPvPlChooserActive || mVidPlChooserActive) return;   // add-to-playlist chooser ignores left/right
    if (mPhotoMultiActive) { photoMultiLR(+1); return; }
    if (mPvActive) { if (mPvPlChooserActive || mPvWpMode || mPvTrimMode) return;
                     if (mPvPanel || mPvCpSub) pvPanelMove(+1, 0); else pvStep(+1); return; }
    if (ps3TopScreenKind() == GS_ICONGRID) { iconGridNav(+1, 0); return; }
    if (!mPs3DlgActive && ps3TopScreenKind() == PHOTO_GRID) { photoGridNav(+1, 0); return; }
    if (mPs3BrightSlider) { adjustBrightness(+1); return; }   // Quick Menu brightness slider modal
    if (mPs3TzActive) return;   // tzglobe list is vertical only
    if (mPs3LangActive) return; // language list is vertical only
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
    musicOnCatFocus();   // lazy-load the music library when the Music column is focused
    photoOnCatFocus();   // lazy-load the photo library when the Photo column is focused
    videoOnCatFocus();   // lazy-load the video library when the Video column is focused
}

void NanoMenu::ps3XmbUp() {
    if (mGSearchActive) { gsearchMove(-1); return; }   // global search results
    if (mVidActive) {   // video player: Go To digit up / Scene grid up / panel grid up
        if (mVidDlgActive) return;   // modal: up/down inert (Yes/No is horizontal)
        if (mVidResumeAsk) { mVidResumeSel = 0; return; }   // Resume prompt: up = Resume
        if (mVidGoToOpen) vidGoToAdjust(+1);
        else if (mVidSceneOpen) vidSceneMove(0, -1);
        else if (mVidCpOpen) vidPanelMove(0, -1);
        else adjustBrightness(+1);   // plain playback: Up raises screen brightness (HUD bar)
        return;
    }
    if (mPs3OptActive) { xmbOptMove(-1); return; }
    if (mMpActive) { if (mMpPlChooserActive) { mpPlChooserMove(-1); return; }
                     if (mMpCpOpen) mpOptMove(0, +1); return; }   // panel grid nav (screen-up = grid-up)
    if (mPvPlChooserActive) { pvPlChooserMove(-1); return; }
    if (mVidPlChooserActive) { vidPlChooserMove(-1); return; }
    if (mPhotoMultiActive) { photoMultiMove(-1); return; }
    if (mPvActive) { if (mPvPlChooserActive) { pvPlChooserMove(-1); return; }
                     if (mPvWpMode || mPvTrimMode) return;
                     if (mPvPanel || mPvCpSub) pvPanelMove(0, -1); return; }
    if (ps3TopScreenKind() == GS_ICONGRID) { iconGridNav(0, -1); return; }
    if (!mPs3DlgActive && ps3TopScreenKind() == PHOTO_GRID) { photoGridNav(0, -1); return; }
    if (mPs3BrightSlider) { mPs3BrightSlider = false; mShowBrightnessBar = false; mBrightnessBarTimer = 0; return; }
    if (mPs3TzActive) { tzGlobeNav(-1); return; }
    if (mPs3LangActive) { langPickerNav(-1); return; }
    if (mPs3WizActive) { wizNav(-1, false); return; }
    if (mPs3DlgActive) { ps3DlgNav(-1, false); return; }
    int& s = ps3CurSel();
    if (s > 0) { mPs3ItemAnimFrom = mPs3AnimItem; mPs3ItemAnimStart = mEffectTime; s--; }
}
void NanoMenu::ps3XmbDown() {
    if (mGSearchActive) { gsearchMove(+1); return; }   // global search results
    if (mVidActive) {   // video player: Go To digit down / Scene grid down / panel grid down
        if (mVidDlgActive) return;   // modal: up/down inert (Yes/No is horizontal)
        if (mVidResumeAsk) { mVidResumeSel = 1; return; }   // Resume prompt: down = Play from beginning
        if (mVidGoToOpen) vidGoToAdjust(-1);
        else if (mVidSceneOpen) vidSceneMove(0, +1);
        else if (mVidCpOpen) vidPanelMove(0, +1);
        else adjustBrightness(-1);   // plain playback: Down lowers screen brightness (HUD bar)
        return;
    }
    if (mPs3OptActive) { xmbOptMove(+1); return; }
    if (mMpActive) { if (mMpPlChooserActive) { mpPlChooserMove(+1); return; }
                     if (mMpCpOpen) mpOptMove(0, -1); return; }   // panel grid nav (screen-down = grid-down)
    if (mPvPlChooserActive) { pvPlChooserMove(+1); return; }
    if (mVidPlChooserActive) { vidPlChooserMove(+1); return; }
    if (mPhotoMultiActive) { photoMultiMove(+1); return; }
    if (mPvActive) { if (mPvPlChooserActive) { pvPlChooserMove(+1); return; }
                     if (mPvWpMode || mPvTrimMode) return;
                     if (mPvPanel || mPvCpSub) pvPanelMove(0, +1); return; }
    if (ps3TopScreenKind() == GS_ICONGRID) { iconGridNav(0, +1); return; }
    if (!mPs3DlgActive && ps3TopScreenKind() == PHOTO_GRID) { photoGridNav(0, +1); return; }
    if (mPs3BrightSlider) { mPs3BrightSlider = false; mShowBrightnessBar = false; mBrightnessBarTimer = 0; return; }
    if (mPs3TzActive) { tzGlobeNav(+1); return; }
    if (mPs3LangActive) { langPickerNav(+1); return; }
    if (mPs3WizActive) { wizNav(+1, false); return; }
    if (mPs3DlgActive) { ps3DlgNav(+1, false); return; }
    int& s = ps3CurSel(); int n = (int)ps3CurItems().size();
    if (s < n - 1) { mPs3ItemAnimFrom = mPs3AnimItem; mPs3ItemAnimStart = mEffectTime; s++; }
}

void NanoMenu::ps3XmbSelect() {
    if (mGSearchActive) { gsearchActivate(); return; }   // launch / open the selected result
    if (mVidActive) {   // video player: Go To enter / Scene seek / panel activate / play-pause
        if (mVidDlgActive) { vidDlgActivate(); return; }     // modal: OK / Yes-No confirm
        if (mVidResumeAsk) { vidResumeConfirm(); return; }   // Resume prompt: confirm the choice
        if (mVidGoToOpen) vidGoToActivate();
        else if (mVidSceneOpen) vidSceneActivate();
        else if (mVidCpOpen) vidPanelActivate();
        else vidTogglePlay();
        return;
    }
    if (mPs3OptActive) { xmbOptEnter(); return; }   // option menu: activate the highlighted action
    if (mMpActive) {   // X: chooser select, panel control, or toggle play/pause with no panel up
        if (mMpPlChooserActive) { mpPlChooserSelect(); return; }
        if (mMpCpOpen) mpOptActivate();
        else mpAudioCmd(mMusicPlayer.isPlaying() ? MpAudioCmd::Pause : MpAudioCmd::Play);
        return;
    }
    if (mPvPlChooserActive) { pvPlChooserSelect(); return; }   // X: commit the chooser
    if (mVidPlChooserActive) { vidPlChooserSelect(); return; }
    if (mPhotoMultiActive) { photoMultiActivate(); return; }   // X: toggle row / activate button
    if (mPvActive) {   // X: chooser select / range-selector confirm / panel activate
        if (mPvPlChooserActive) { pvPlChooserSelect(); return; }
        if (mPvWpMode) { pvWallpaperConfirm(); return; }
        if (mPvTrimMode) { mPvTrimMode = false; pvShowMsg("The image has been trimmed.", 1100.0f); return; }
        if (mPvPanel) pvPanelActivate();
        return;
    }
    if (ps3TopScreenKind() == GS_ICONGRID) { iconGridSelect(); return; }
    if (!mPs3DlgActive && ps3TopScreenKind() == PHOTO_GRID) { photoGridSelect(); return; }
    if (mPs3BrightSlider) { mPs3BrightSlider = false; mShowBrightnessBar = false; mBrightnessBarTimer = 0; return; }  // X confirms the brightness slider
    if (mPs3TzActive) { closeTimezoneGlobe(true); return; }   // X: apply the highlighted zone + close
    if (mPs3LangActive) { closeLanguagePicker(true); return; }  // X: apply the highlighted language + close
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
        // X commits a chooser (theme leaf or settings-bound leaf); on a plain
        // message dialog it just dismisses.
        closePs3Dialog(mPs3DlgThemeKey > 0 || mPs3DlgBinding != nullptr); return;
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
        case PS3_DATA_SUBMENU: { if (it.label == "GammaEQ") warmEqPreview();   // preload the clip before the user reaches Audio Preview
                                 Ps3Level lvl; buildDataSubmenu(it.data, lvl); mPs3Stack.push_back(lvl); break; }
        case PS3_GS_ROOT:      { Ps3Level lvl; buildGameSystemsList(lvl);      mPs3Stack.push_back(lvl); break; }
        case PS3_GS_SYSTEM_ROW: { mGsEditIdx = it.a; Ps3Level lvl; buildGameSystemEditor(it.a, lvl); mPs3Stack.push_back(lvl); break; }
        case PS3_GS_FIELD:     { gsEditField(it.a); return; }   // open OSK / chooser / toggle
        case PS3_GS_EMUROW: {   // pick a catalog emulator/core -> apply to the system
            int ci = it.a;
            if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == GS_EMUPICK) mPs3Stack.pop_back();
            applyEmulatorChoice(ci);
            return;
        }
        case PS3_GS_EMU_CUSTOM: {   // "Custom..." -> blank add (add mode) or manual OSK (edit)
            if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == GS_EMUPICK) mPs3Stack.pop_back();
            if (mGsAddMode) { gsAddBlankSystem(); return; }
            if (mGsEditIdx >= 0 && mGsEditIdx < (int)mXmbSystems.size())
                gsEditField(mXmbSystems[mGsEditIdx].launchType == XLT_CUSTOM_PACKAGE
                                ? GSF_PACKAGE : GSF_CORE);
            return;
        }
        case PS3_GS_ADD:       { gsAddSystem(); return; }   // emulator picker in add mode (self-animates)
        case PS3_MUSIC_REFRESH: {   // rescan the imported music folders on demand
            musicRefresh();
            return;
        }
        case PS3_GS_ADDFOLDER: {   // open the raw-path folder browser (storage roots)
            std::vector<Ps3Item> ps = ps3CurItems(); int pSel = ps3CurSel();
            Ps3Level lvl; buildFolderBrowser("", lvl); mPs3Stack.push_back(lvl);
            mPs3SubParentItems = ps; mPs3SubParentIdx = pSel; mPs3SubChildItems = mPs3Stack.back().items;
            mPs3SubDir = 1; mPs3SubAnimStart = mEffectTime; mPs3SubAnim = 0.0f;
            mPs3AnimItem = 0.0f; mPs3ItemAnimStart = -1.0f;
            return;
        }
        case PS3_GS_DIR: {   // descend/ascend the folder browser in place
            if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == GS_FOLDERBROWSE)
                buildFolderBrowser(it.payloadStr, mPs3Stack.back());
            return;
        }
        case PS3_GS_SELFOLDER: {
            if (mFolderPickTarget == 1) musicFolderSelect(it.payloadStr);
            else if (mFolderPickTarget == 2) photoFolderSelect(it.payloadStr);
            else if (mFolderPickTarget == 3) videoFolderSelect(it.payloadStr);
            else gsFolderSelect(it.payloadStr);
            return;
        }
        case PS3_PHOTO_REFRESH: { photoRefresh(); return; }   // rescan the imported photo folders
        case PS3_PHOTO_FOLDER_ROW: { return; }   // a display row; removal is via the option (Y)
        case PS3_PHOTO_ALBUM: {   // a group-folder -> open its thumbnail grid
            std::vector<PhotoGroup> groups = photoGroups();
            if (it.a >= 0 && it.a < (int)groups.size())
                openPhotoGrid(groups[it.a].idx, groups[it.a].name, -1);
            return;
        }
        case PS3_PHOTO: {   // an individual photo row -> open the viewer on this list
            std::vector<int> list;
            for (const auto& x : items) if (x.kind == PS3_PHOTO) list.push_back(x.a);
            int vi = 0; for (size_t i = 0; i < list.size(); i++) if (list[i] == it.a) { vi = (int)i; break; }
            openPhotoViewer(list, vi);
            return;
        }
        case PS3_PHOTO_PL_NEW: {
            openOskForPassword("Enter a name for the playlist",
                [this](const std::string& nm){ photoCreatePlaylist(nm);
                    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == 0
                        && mPs3Stack.back().title == "Playlists")
                        buildPhotoPlaylistsScreen(mPs3Stack.back()); });
            mOskPasswordMode = false; mOskPlaintext = true;   // a playlist name is plain text, not masked
            return;
        }
        case PS3_PHOTO_PLAYLIST: {   // open the playlist's thumbnail grid
            std::vector<int> list; std::string title;
            buildPhotoPlaylistGridList(it.a, list, title);
            openPhotoGrid(list, title, it.a);
            return;
        }
        case PS3_MUSIC_ALBUM:    { Ps3Level lvl; buildMusicAlbumSubmenu(it.a, lvl); mPs3Stack.push_back(lvl); break; }
        case PS3_MUSIC_PLAYLIST: { Ps3Level lvl; buildMusicPlaylistSubmenu(it.a, lvl); mPs3Stack.push_back(lvl); break; }
        case PS3_MUSIC_TRACK: {
            // If the selected track is the one already playing in the live session,
            // resume the Now-Playing screen on it instead of restarting it from 0.
            bool live = !mMpQueue.empty() &&
                        (mMusicPlayer.isPlaying() || mMusicPlayer.isPaused());
            int curTi = (live && mMpIdx >= 0 && mMpIdx < (int)mMpQueue.size())
                        ? mMpQueue[mMpIdx] : -1;
            if (live && it.a == curTi) { resumeMusicPlayer(); return; }
            // Otherwise open the player on the surrounding track list, starting at the
            // selected row (sel = its position in the current list).
            openMusicPlayer(items, sel);
            return;
        }
        case PS3_VIDEO_FILE: {
            // Open the full-screen video player on the surrounding list of videos.
            openVideoPlayer(items, sel);
            return;
        }
        case PS3_VIDEO_REFRESH: { videoRefresh(); return; }
        case PS3_VIDEO_PLAYLIST: {   // open the playlist's file submenu
            Ps3Level lvl; buildVideoPlaylistSubmenu(it.a, lvl); mPs3Stack.push_back(lvl); return;
        }
        case PS3_VIDEO_PL_NEW: {
            openOskForPassword("Enter a name for the playlist",
                [this](const std::string& nm){ videoCreatePlaylist(nm);
                    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == GS_NONE
                        && mPs3Stack.back().title == "Playlists")
                        buildVideoPlaylistsScreen(mPs3Stack.back()); });
            mOskPasswordMode = false; mOskPlaintext = true;   // a playlist name is plain text, not masked
            return;
        }
        case PS3_MUSIC_PL_NEW: {
            openOskForPassword("Enter a name for the playlist",
                [this](const std::string& nm){ musicCreatePlaylist(nm);
                    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == 0
                        && mPs3Stack.back().title == "Playlists")
                        buildMusicPlaylistsScreen(mPs3Stack.back()); });
            mOskPasswordMode = false; mOskPlaintext = true;   // a playlist name is plain text, not masked
            return;
        }
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
            // Music category: "Search for Media Servers" manages the imported music
            // folders (reusing the Game Systems folder picker); "Playlists" opens the
            // playlists screen. Gated to the Music column so Photo/Video keep their
            // inert "Search for Media Servers" items.
            bool inMusicCat = (mPs3CatIdx >= 0 && mPs3CatIdx < (int)mPs3Cats.size()
                               && mPs3Cats[mPs3CatIdx].name == "Music");
            bool inPhotoCat = (mPs3CatIdx >= 0 && mPs3CatIdx < (int)mPs3Cats.size()
                               && mPs3Cats[mPs3CatIdx].name == "Photo");
            bool inVideoCat = (mPs3CatIdx >= 0 && mPs3CatIdx < (int)mPs3Cats.size()
                               && mPs3Cats[mPs3CatIdx].name == "Video");
            if (inVideoCat && it.label == "Search for Media Servers") { videoOpenFolders(); return; }
            if (inVideoCat && it.label == "Playlists") {
                videoEnsureLoaded();
                std::vector<Ps3Item> ps = ps3CurItems(); int pSel = ps3CurSel();
                Ps3Level lvl; buildVideoPlaylistsScreen(lvl); mPs3Stack.push_back(lvl);
                mPs3SubParentItems = ps; mPs3SubParentIdx = pSel; mPs3SubChildItems = mPs3Stack.back().items;
                mPs3SubDir = 1; mPs3SubAnimStart = mEffectTime; mPs3SubAnim = 0.0f;
                mPs3AnimItem = 0.0f; mPs3ItemAnimStart = -1.0f;
                return;
            }
            if (inPhotoCat && it.label == "Search for Media Servers") { photoOpenFolders(); return; }
            if (inPhotoCat && it.label == "Photo Gallery") { return; }   // info item (no-op)
            if (inPhotoCat && it.label == "Playlists") {
                photoEnsureLoaded();
                std::vector<Ps3Item> ps = ps3CurItems(); int pSel = ps3CurSel();
                Ps3Level lvl; buildPhotoPlaylistsScreen(lvl); mPs3Stack.push_back(lvl);
                mPs3SubParentItems = ps; mPs3SubParentIdx = pSel; mPs3SubChildItems = mPs3Stack.back().items;
                mPs3SubDir = 1; mPs3SubAnimStart = mEffectTime; mPs3SubAnim = 0.0f;
                mPs3AnimItem = 0.0f; mPs3ItemAnimStart = -1.0f;
                return;
            }
            if (inMusicCat && it.label == "Search for Media Servers") { musicOpenFolders(); return; }
            if (inMusicCat && it.label == "Playlists") {
                musicEnsureLoaded();
                std::vector<Ps3Item> ps = ps3CurItems(); int pSel = ps3CurSel();
                Ps3Level lvl; buildMusicPlaylistsScreen(lvl); mPs3Stack.push_back(lvl);
                mPs3SubParentItems = ps; mPs3SubParentIdx = pSel; mPs3SubChildItems = mPs3Stack.back().items;
                mPs3SubDir = 1; mPs3SubAnimStart = mEffectTime; mPs3SubAnim = 0.0f;
                mPs3AnimItem = 0.0f; mPs3ItemAnimStart = -1.0f;
                return;
            }
            // "Internet Connection Settings" runs the full PS3 setup wizard (1:1
            // web NETCONF flow, real scan/connect). "Internet Connection" opens the
            // side-panel Enabled/Disabled toggle for the Wi-Fi radio.
            if (it.label == "Internet Connection Settings") { startNetWizard(); return; }
            if (it.label == "Internet Connection") { openPs3Dialog(it); return; }
            if (it.label == "Time Zone") { openTimezoneGlobe(); return; }   // 3D Earth selector
            // Block ONLY when a live app is behind the in-game overlay (scrim):
            // changing the locale + rendering the heavy CJK/Arabic native-name
            // glyphs there competed with the still-running app and took it down.
            // The home XMB (rendered by the overlay process in overlay_home mode)
            // and the launcher/wallpaper overlay have no app behind, so allow it
            // there - that is where you would normally change the language.
            if (it.label == "System Language") {
                bool liveAppBehind = mOverlayMode && !mOverlayWallpaper;
                if (!liveAppBehind) openLanguagePicker();
                return;
            }
            if (it.label == "Audio Preview") {   // GammaEQ: toggle the looping preview clip
                if (mEqPreviewWanted.load()) stopEqPreview(); else startEqPreview();
                mDisplayDirty = true; return;
            }
            if (it.label == "Set via Internet") { startDateTimeWizard(0); return; }  // NTP progress->result
            if (it.label == "Set Manually")     { startDateTimeWizard(1); return; }  // OSK date+time entry
            // Accessory Settings -> real Bluetooth device management (1:1 web bt_* flow).
            if (it.label == "Manage Bluetooth® Devices")      { startBtWizard(0); return; }
            if (it.label == "BD Remote Control Registration") { startBtWizard(1); return; }
            if (it.label == "Audio Device Settings")          { startBtWizard(2); return; }
            // Data-driven settings leaf -> bound side chooser (real backing setting).
            if (it.label == "Scrape All Systems") { scrapeAllSystems(); return; }
            // Prefer the item's pre-resolved binding (Quick Settings leaves bind a
            // tile-named row to an existing setting), else match by label.
            if (const Ps3SettingBinding* b = it.binding ? it.binding : ps3BindingFor(it.label)) { openBoundChooser(b); return; }
            if (it.action == 1) openPs3Dialog(it);   // action='dialog' -> dialog/chooser
            return;
        }
        case PS3_QUICK: {
            // GammaOS Nano legacy global actions. Leaf actions return; only the
            // Power submenu pushes a level and falls through to the collapse anim.
            switch (it.a) {
                case QA_RESUME_AUDIO: resumeMusicPlayer(); return;   // reopen Now-Playing on the live queue
                case QA_POWER_SUBMENU: { Ps3Level lvl; buildQuickPowerSubmenu(lvl); mPs3Stack.push_back(lvl); break; }
                case QA_QUICK_SETTINGS: { Ps3Level lvl; buildQuickSettingsSubmenu(lvl); mPs3Stack.push_back(lvl); break; }
                case QA_NOTIFICATIONS:  { Ps3Level lvl; buildNotificationsSubmenu(lvl);  mPs3Stack.push_back(lvl); break; }
                case QA_BRIGHTNESS:   mPs3BrightSlider = true; mShowBrightnessBar = true; mBrightnessBarTimer = 90; return;
                case QA_PERFORMANCE:  openPerformanceChooser(); return;
                case QA_CLOSE_APP:    if (mOverlayMode) overlayQuitToHome(); return;  // home: no fg app
                case QA_KILL_BG:      quickKillApps(false); return;   // keep the foreground game running
                case QA_KILL_ALL:
                    // Overlay (in-game): hard-stop EVERY app including the foreground
                    // game (clearing its relaunch state so it does not respawn) and
                    // drop to the home launcher. DRM home: no foreground game, so just
                    // sweep all third-party apps.
                    if (mOverlayMode) overlayKillAll();
                    else              quickKillApps(true);
                    return;
                case QA_RESTART:      prepareShutdown("reboot");   return;
                case QA_POWEROFF:     prepareShutdown("shutdown"); return;
                case QA_RECOVERY:     property_set("persist.gammaos.nano.qr_prepared", "0");
                                      property_set("persist.gammaos.nano.qr_core", "");
                                      prepareShutdown("recovery"); return;
                case QA_SAFEMODE:     property_set("persist.gammaos.nano.qr_prepared", "0");
                                      property_set("persist.gammaos.nano.qr_core", "");
                                      prepareShutdown("safemode"); return;
                case QA_BOOT_ANDROID: property_set("persist.gammaos.nano.qr_prepared", "0");
                                      property_set("persist.gammaos.nano.qr_core", "");
                                      prepareShutdown("android"); return;
                case QA_SECONDARY_DISPLAY: {
                    // In-memory toggle like the QS tile (the real state is in
                    // DisplayManagerService). enable/disable display id 2; a no-op when
                    // no external panel is attached. Run off the render thread.
                    mSecondaryDisplayOn = !mSecondaryDisplayOn;
                    bool on = mSecondaryDisplayOn;
                    std::thread([on]{ system(on ? "cmd display enable-display 2 2>/dev/null"
                                                : "cmd display disable-display 2 2>/dev/null"); }).detach();
                    // Reflect the new state in the open Quick Settings row.
                    if (!mPs3Stack.empty())
                        for (auto& r : mPs3Stack.back().items)
                            if (r.kind == PS3_QUICK && r.a == QA_SECONDARY_DISPLAY) r.value = on ? "On" : "Off";
                    mDisplayDirty = true; return;
                }
                case QA_LAUNCH_CALIBRATION:
                    std::thread([]{ system("am start -a org.lineageos.lineageparts.GAMEPAD_CALIBRATION 2>/dev/null"); }).detach();
                    return;
                case QA_LAUNCH_REMAP:
                    std::thread([]{ system("am start -n org.lineageos.lineageparts/.input.GamepadSettings "
                                           "--es :settings:fragment_args_key gamepad_remap_buttons 2>/dev/null"); }).detach();
                    return;
                case QA_NOTIF_DISMISS: {
                    if (it.b >= 0 && it.b < (int)mNotifs.size()) {
                        const std::string key = mNotifs[it.b].key;
                        if (nanoValidNotifKey(key)) {   // snooze ~1yr = effective dismiss (no true cancel from here)
                            std::string c = "cmd notification snooze --for 31536000000 \"" + key + "\" >/dev/null 2>&1";
                            std::thread([c]{ system(c.c_str()); }).detach();
                        }
                        // Optimistically drop the row now and rebuild from the local list
                        // (the snooze applies asynchronously, so re-reading dumpsys here
                        // would still show it). The cursor is kept, clamped to the list.
                        mNotifs.erase(mNotifs.begin() + it.b);
                        if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == 0 &&
                            mPs3Stack.back().title == "Notifications") {
                            int keep = mPs3Stack.back().sel;
                            buildNotificationsLevel(mPs3Stack.back());
                            int n = (int)mPs3Stack.back().items.size();
                            mPs3Stack.back().sel = keep < n ? keep : (n > 0 ? n - 1 : 0);
                        }
                        mDisplayDirty = true;
                    }
                    return;
                }
                default: return;
            }
            break;   // only QA_POWER_SUBMENU reaches here -> collapse animation
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
    if (mGSearchActive) { gsearchClose(); return; }   // close the global search overlay
    if (mVidActive) {   // video player: cascade Go To -> Scene Search -> submenu -> panel -> close
        if (mVidDlgActive) { vidDlgBack(); return; }   // modal: dismiss / cancel (= No)
        if (mVidResumeAsk) { mVidResumeSel = 0; vidResumeConfirm(); return; }   // Circle defaults to Resume
        if (mVidGoToOpen) { vidGoToClose(); return; }
        if (mVidSceneOpen) { vidSceneClose(); return; }
        if (mVidSubOpen)  { mVidSubOpen = false; return; }
        if (mVidCpOpen)   { vidPanelClose(); return; }
        closeVideoPlayer(); return;
    }
    if (mPs3OptActive) { if (mPs3OptSubOpen) xmbOptCloseSub(); else closeXmbOpt(); return; }   // O: back out of a submenu, else dismiss
    if (mMpActive) { if (mMpPlChooserActive) { mpPlChooserCancel(); return; }
                     if (mMpCpOpen) mpOptBack(); else minimizeMusicPlayer(); return; }   // O: chooser cancel / panel back / minimize (audio keeps playing)
    if (mPvPlChooserActive) { pvPlChooserCancel(); return; }   // O: cancel the chooser
    if (mVidPlChooserActive) { vidPlChooserCancel(); return; }
    if (mPhotoMultiActive) { photoMultiClose(); return; }   // O: leave multi-select
    if (mPvActive) {   // O: chooser/range cancel -> submenu -> panel -> stop slideshow -> close
        if (mPvPlChooserActive) { pvPlChooserCancel(); return; }
        if (mPvWpMode || mPvTrimMode) { mPvWpMode = false; mPvTrimMode = false; return; }
        if (mPvCpSub) { mPvCpSub = false; return; }
        if (mPvPanel || mPvCpClosing) { pvPanelBack(); return; }
        if (mPvSlideshow) { mPvSlideshow = false; mPvPaused = false; return; }
        closePhotoViewer(); return;
    }
    if (ps3TopScreenKind() == GS_ICONGRID) { closeIconGridPicker(); mPs3Stack.pop_back(); return; }
    if (!mPs3DlgActive && ps3TopScreenKind() == PHOTO_GRID) { closePhotoGrid(); mPs3Stack.pop_back(); return; }
    if (mPs3BrightSlider) { mPs3BrightSlider = false; mShowBrightnessBar = false; mBrightnessBarTimer = 0; return; }  // O dismisses the brightness slider
    if (mPs3TzActive) { closeTimezoneGlobe(false); return; }   // O: cancel (keep current zone)
    if (mPs3LangActive) { closeLanguagePicker(false); return; }  // O: cancel (revert the live preview)
    if (mPs3WizActive) { wizBack(); return; }   // O: step back through the network setup wizard
    if (mPs3DlgActive) { closePs3Dialog(false); return; }   // O: cancel the dialog/chooser
    // Overlay XMB: Back at the top level RESUMES the running game (dismiss + thaw).
    if (overlayAtTopLevel()) { overlayResume(); return; }
    if (!mPs3Stack.empty()) {
        // Leaving the Notifications submenu: drop the parsed list so nothing is held
        // while it is closed (zero idle cost).
        if (mPs3Stack.back().title == "Notifications" && mPs3Stack.back().screenKind == 0) {
            mNotifs.clear(); mNotifs.shrink_to_fit();
        }
        // Snapshot the child list (being left) for the slide-out, then pop and
        // expand the parent back out of the breadcrumb column (timed, dir -1).
        mPs3SubChildItems  = mPs3Stack.back().items;
        mPs3Stack.pop_back();
        // Left the GammaEQ submenu: stop the preview and release its 25MB clip.
        if (mEqGammaEqDepth >= 0 && (int)mPs3Stack.size() <= mEqGammaEqDepth) {
            stopEqPreview();
            freeEqPcm();
            mEqGammaEqDepth = -1;
        }
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
    eqPreviewTick();   // retry the GammaEQ preview open if the audio HAL was not ready
    musicTick();       // music player: auto-advance to the next track at end-of-stream
    photoTick();       // photo viewer: enter-fade easing + slideshow timers
    vidReapDying();    // free any async-released video decoders every frame (also after the player closes)
    if (renderVideoPlayer()) return;   // full-screen video player owns the screen while up/fading
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
    // Global search overlay (Select): a categorized results list over the dimmed XMB.
    if (mGSearchActive) { renderGlobalSearch(); return; }
    if (mMenuState == MENU_WIFI) { renderWifiScreen(); return; }
    if (mMenuState == MENU_BT)   { renderBtScreen();   return; }
    if (ps3TopScreenKind() == GS_ICONGRID) { renderIconGridPicker(); return; }
    // The viewer opens ON TOP of the grid level (the grid stays on the stack), so the
    // viewer (mPvActive) must take precedence over the grid screen render.
    if (mPhotoMultiActive) { renderPhotoMulti(); return; }   // multi-select overlays the grid
    if (!mPvActive && mPvEnterRaw <= 0.004f && ps3TopScreenKind() == PHOTO_GRID) {
        renderPhotoGrid();
        if (mPs3OptActive || mPs3OptClosing) renderXmbOpt();   // option menu over the grid
        if (mPs3DlgActive || mPs3DlgClosing) renderPs3Dialog(); // Information dialog over the grid
        if (mPvPlChooserActive || mPvPlChooserAnim > 0.004f) drawPvPlChooser();  // add-to-playlist chooser
        drawPhotoMsg();    // post-confirm Delete/Copy message
        drawPhotoBanner(); // Sort By / Group Content change banner
        return;
    }

    // Overlay entrance: when the in-game overlay is raised, the blurred backdrop
    // is already there; the XMB chrome (category labels/icons, item list, clock)
    // fades + pops in like the cold-boot hand-off. Reuse the boot reveal
    // multipliers, eased over ~520ms. Wrap-safe against mEffectTime's fmod-500.
    // Pending sentinel (-2): stamp the real start on this first rendered frame
    // (after the blocking capture in overlayShow) so the entrance plays.
    // Advance the entrance by accumulated CLAMPED per-frame dt rather than wall-clock
    // elapsed. On the first-ever raise the first rendered frame does one-time lazy
    // work (glyph rasterization, FBOs, glass refraction), so a wall-clock timer would
    // see a huge frame-1-to-frame-2 gap and jump the eased reveal straight to 1.0,
    // skipping the animation. Clamping dt makes every rendered frame show a smooth
    // step regardless of a slow frame, so the transition plays even on first load.
    if (mOverlayMode && mOverlayEnterStart <= -1.5f) {
        // First rendered frame after the raise: begin the entrance, fully faded out.
        mOverlayEnterStart = 0.0f;       // active (>=0); see ps3Settled / 60fps gate
        mOverlayEnterElapsed = 0.0f;
        mPs3BootIconReveal = 0.0f;
        mPs3BootLabelReveal = 0.0f;
    } else if (mOverlayMode && mOverlayEnterStart >= 0.0f) {
        float dt = mFrameDt; if (dt < 0.0f) dt = 0.0f; if (dt > 0.033f) dt = 0.033f;
        mOverlayEnterElapsed += dt;
        const float dur = 0.52f;
        if (mOverlayEnterElapsed >= dur) {
            mPs3BootIconReveal = 1.0f;
            mPs3BootLabelReveal = 1.0f;
            mOverlayEnterStart = -1.0f;
        } else {
            float t = mOverlayEnterElapsed / dur;
            float e = t * t * (3.0f - 2.0f * t);   // smoothstep
            mPs3BootIconReveal = e;
            mPs3BootLabelReveal = e;
        }
    }

    // Date and Time -> Time Zone: the 1:1 web 3D-globe selector. Rendered fully
    // standalone (it fills black then fades the Earth in over it), skipping the
    // expensive menu/glass-icon pass entirely.
    if (mPs3TzActive) { renderTimezoneGlobe(); return; }

    // Now-Playing music screen: cross-fade with the XMB chrome both ways, 1:1 with the
    // web (drawBG). The chrome fades via mMpChromeT scaled into the cold-boot reveal
    // multipliers (the SAME validated path that fades the chrome in at boot) - OUT over
    // ~0.4s on enter, back IN over ~0.4s on leave - while the now-playing bar fades via
    // mMpEnterT - IN over ~1.0s on enter, OUT over ~1.0s on leave (in renderMusicPlayer).
    // Leave fades only when minimizing (Circle keeps the audio + queue, so the bar can
    // fade out live); a full close clears the queue and is instant. Once the chrome is
    // fully gone in the steady player we skip it entirely (perf). The Waves morph
    // animates the background underneath; the panel + bar live in NanoMenuMusic.cpp.
    // The reveal multipliers are NOT recomputed each frame post-boot (only the boot
    // intro / overlay entrance set them), so the chrome cross-fade below scales them in
    // place and MUST restore them at the end of this function - otherwise the chrome
    // would stay hidden after the player closes.
    bool mpVisible = mMpActive || (mMpEnterT > 0.004f && !mMpQueue.empty());
    float mpSavedIconReveal = mPs3BootIconReveal, mpSavedLabelReveal = mPs3BootLabelReveal;
    bool  mpChromeScaled = false;
    bool  mpExitFade = false;
    if (mpVisible) {
        if (mMpActive && mMpChromeT <= 0.004f) { renderMusicPlayer(); return; }
        // entering (chrome fading out) or leaving (chrome fading in): scale the cold-boot
        // reveal so the whole chrome fades, render it below, then composite the player on
        // top at the end.
        mPs3BootIconReveal  *= mMpChromeT;
        mPs3BootLabelReveal *= mMpChromeT;
        mpChromeScaled = true;
        // On LEAVE (minimize) the web composites the now-playing bar fading OUT UNDER the
        // menu chrome fading IN, so the returning column (with its enlarged focused track)
        // sits ON TOP of the bar. nano was drawing the bar last (on top), so the small
        // bar overlapped the big focused item and read as the HUD "getting bigger". Render
        // the player here, under the chrome drawn below; the chrome text re-asserts outline
        // mode 1 after (renderMusicPlayer leaves it 0). Enter keeps the player on top.
        if (!mMpActive) { renderMusicPlayer(); mTextOutlineMode = 1; mpExitFade = true; }
    }

    // Full-screen photo viewer: drawn over the wave background, replacing the XMB
    // chrome (the viewer fades in from black). The control panel + EXIF overlay
    // live in NanoMenuPhotos.cpp.
    if (mPvActive || mPvEnterRaw > 0.004f) { renderPhotoViewer(); return; }   // keep drawing through the exit fade

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
        if (mPs3LangActive)                    { renderLanguagePicker(); return; }
    }

    // From here down is the actual XMB menu chrome (category bar, item list,
    // clock): anti-alias its minified labels/subtitles. Turned back off before
    // the dialog overlay below so dialogs and the OSK keep crisp GL_LINEAR text.
    setGlyphAtlasAA(true);

    // Opt-in slow-frame diagnostic: `setprop persist.gammaos.nano.ps3xmb.fpslog 1`
    // logs any frame slower than ~22ms (<45fps) with context, so transition dips
    // can be measured from logcat. Off by default (read once).
    static bool sFpsLog = property_get_bool("persist.gammaos.nano.ps3xmb.fpslog", false);
    if (sFpsLog && mFrameDt > 0.022f)
        ALOGW("ps3 slow frame %.1fms inSub=%d subAnim=%d",
              mFrameDt * 1000.0f, (int)!mPs3Stack.empty(), (int)(mPs3SubAnimStart >= 0.0f));

    { ps3::LayoutParams lp; lp.panelW = mWidth; lp.panelH = mHeight; lp.uiScale = mPs3UiScale;
      ps3::layoutCompute(lp); }

    // Boxart: cache the toggle once per frame (drawList reads it per visible ROM),
    // and free the cover textures whenever the Game category is not the active one,
    // so the feature holds no GL memory when you are not browsing games. They reload
    // lazily from the disk cache on return.
    mScrapeBoxartOn = scraperBoxartEnabled();
    {
        bool inGame = (mPs3CatIdx >= 0 && mPs3CatIdx < (int)mPs3Cats.size()
                       && mPs3Cats[mPs3CatIdx].name == "Game");
        // Leaving Game frees the boxart/fanart GL AND joins the async decode worker
        // (scraperFreeBoxart). Fire it whenever any scraper-art state is live (cache,
        // hover fanart, or a running worker) so nothing lingers when not browsing games.
        if (!inGame && (!mRomBoxartCache.empty() || mFanartTex || mSaDecStarted.load()))
            scraperFreeBoxart();
    }
    // Upload any finished async art decodes (boxart / hover fanart / Information art)
    // to GL on the render thread; the worker decoded the pixels off-thread so opening
    // a Game system or Information never blocks the render loop.
    saDrainArt();

    // Content-info hover background is drawn further down, AFTER the submenu frost
    // backdrop, so a scraped ROM's fanart in a submenu is not hidden by the frost.

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

    // Dynamic subtitle reserve: grow the active-item pad to fit the active
    // description's actual wrapped line count (1..4), eased so the items below
    // reflow smoothly instead of snapping when scrolling between a short and a
    // long blurb. mPs3DescLinesTarget is set by drawDesc (one frame of lag, hidden
    // by the ease). Floored at the tuned baseline so the enlarged active icon is
    // never crowded. ps3::gActivePad feeds itemSlotY below.
    {
        int dl = mPs3DescLinesTarget; if (dl < 1) dl = 1; if (dl > 4) dl = 4;
        float targetPad = ps3::ITEM_DESC_OFFSET
                        + (float)dl * (ps3::gDescSize * ps3::ITEM_DESC_LINEH)
                        + ps3::ITEM_DESC_MARGIN;
        if (targetPad < ps3::ITEM_ACTIVE_PAD) targetPad = ps3::ITEM_ACTIVE_PAD;
        mPs3ActivePad += (targetPad - mPs3ActivePad) * (1.0f - expf(-14.0f * dt));
        ps3::gActivePad = mPs3ActivePad;
    }

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
            // Mark the step finished. Without this the start stamp lingered
            // forever after the last up/down, so ps3Settled (and everything
            // gated on it: the idle frame-rate drop, the settled-root Game
            // column refresh) never saw the list as settled again until some
            // other navigation happened to snap the flag.
            mPs3ItemAnimStart = -1.0f;
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
    // Deep transitions (sub -> sub-sub and back) never cross the root, so the
    // CATEGORY bar and the frost backdrop must stay fully collapsed instead of
    // flashing back in for 250ms on every hop (the web has the same quirk; the
    // collapsed state is pinned here on request). The stack is already pushed /
    // popped when the animation starts: entering crosses the root iff the stack
    // is exactly 1 deep, exiting iff it is now empty. catT drives the category
    // bar / frost / chevron; subT keeps driving the per-level rail + slide.
    bool subDeep = subAnimating
        && ((mPs3SubDir == 1) ? mPs3Stack.size() >= 2 : !mPs3Stack.empty());
    // When the animation has just ENDED this frame, subT holds the animation
    // endpoint (0 for a pop), which for a DEEP pop is not the settled value (1
    // while a submenu remains) - deriving catT from it flashed the category bar
    // for exactly one frame at the end of editor -> list pops. Settled frames
    // take the state-based value instead.
    float catT;
    if (subAnimating) catT = subDeep ? 1.0f : subT;
    else              catT = inSub ? 1.0f : 0.0f;

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
    // Below 2% backdrop alpha the frost is invisible; the higher floor also
    // skips the blur-chain refresh for those frames (the costly part), at
    // worst delaying the first capture on submenu entry by a single frame.
    if (catT > 0.02f) {
        // Keep the wave/gradient ANIMATING behind the frosted backdrop, but
        // sample it at only ~30Hz so the menu itself stays locked at 60fps. The
        // blur reads ps3bg::workTex (the already-rendered scene) instead of a
        // full framebuffer capture, so each sample costs no mid-frame tile flush
        // and fits the 60fps budget; between samples the cached blur is reused
        // and drawn every frame. The blur is in LOGICAL orientation, so draw it
        // waveSpace=true. tintA MUST be > 0 or the panel composites to nothing.
        // 60Hz during a live theme preview (chooser open or cross-fade settling)
        // so the colour change tracks smoothly; ~15Hz otherwise. During the
        // 250ms submenu collapse/expand the cached blur is HELD (no refresh):
        // those frames already pay two extra list layers plus the ramping
        // frost draw, and a re-blur of a crossfading backdrop is invisible -
        // the transition was the last spot still missing 60fps on the overlay.
        float blurCad = (ps3bg::themeFading() || mPs3DlgKind == 1) ? 0.0f : 0.0667f;
        bool due = !mPs3GlassValid
                || (!subAnimating && (mEffectTime - mPs3GlassBlurT) >= blurCad);
        // The frosted backdrop is the blurred WAVE (captureGlassFromWave reads
        // ps3bg::workTex), so it only matches when the wave is the visible background.
        // Draw it when: home XMB (!mOverlayMode, always); overlay WALLPAPER/launcher
        // ONLY if the user's wallpaper IS the wave (mCurrentEffect==22). NEVER in
        // overlay scrim mode (the dark scrim + live app must show through), and NEVER
        // over a non-wave wallpaper (the blurred wave would not match it - let
        // renderEffect's wallpaper show instead). The submenu glass ICONS refract
        // ps3bg::workTex() separately (unaffected); no OSK reuses the submenu blur.
        const bool frostBg = mCurrentEffect == 22 && (!mOverlayMode || mOverlayWallpaper);
        if (due && frostBg && captureGlassFromWave()) { mPs3GlassValid = true; mPs3GlassBlurT = mEffectTime; }
        if (mPs3GlassValid && frostBg)
            // Neutral tint (1,1,1): pure blur, NO darkening or hue/shade change -
            // the backdrop is the blurred wave at its own brightness.
            drawFrostedGlass(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f,
                             1.0f, 1.0f, 1.0f, 1.0f, catT, /*waveSpace=*/true);
    } else {
        mPs3GlassValid = false;
    }

    // Content-info hover background: drawn here (after the submenu frost backdrop,
    // over the wave) so it sits under the category bar / item list / clock but ON
    // TOP of the frost - otherwise a scraped ROM's fanart in a submenu would be
    // hidden by the full-screen frost. Home contexts only (not over a live app).
    {
        const char* cinfoFocus = "";
        std::string fanFile;
        if (!mOverlayMode || mOverlayWallpaper) {
            std::vector<Ps3Item>& ci = ps3CurItems();
            int cs = ps3CurSel();
            if (cs >= 0 && cs < (int)ci.size()) {
                const Ps3Item& f = ci[cs];
                cinfoFocus = f.label.c_str();
                // A focused, scraped ROM shows its fanart as the hover background -
                // in a system submenu (PS3_ROM) AND in Recently Played (PS3_RECENT).
                std::string focusRom;
                if (f.kind == PS3_ROM
                    && f.a >= 0 && f.a < (int)mXmbSystems.size()
                    && f.b >= 0 && f.b < (int)mXmbSystems[f.a].roms.size())
                    focusRom = mXmbSystems[f.a].roms[f.b];
                else if (f.kind == PS3_RECENT
                         && f.a >= 0 && f.a < (int)mXmbRecent.size())
                    focusRom = mXmbRecent[f.a].romPath;
                if (!focusRom.empty() && scraperFanartEnabled()) {
                    const ScrapeEntry* e = scrapeEntryFor(focusRom);
                    if (e && !e->fan.empty()) fanFile = e->fan;
                }
            }
        }
        drawPs3CinfoBg(cinfoFocus, fanFile);
    }

    float catOffset = ps3CatOffset(mPs3CatAnimActive, mPs3CatT, mPs3CatFromOffset);

    // ---- category bar ----
    const float catSubShift = -ps3::CAT_SUBMENU_SHIFT_X * catT;
    const float catSubScale = 1.0f + (ps3::CAT_SUBMENU_SCALE - 1.0f) * catT;
    for (int i = 0; i < (int)mPs3Cats.size(); i++) {
        bool isActive = (i == mPs3CatIdx);
        float nonActiveSubFade = isActive ? 1.0f : (1.0f - catT);
        if (nonActiveSubFade <= 0.002f) continue;
        float dx = ((float)i - (float)mPs3CatIdx) * ps3::CAT_SPACING + catOffset;
        float baseSz = isActive ? ps3::CAT_ICON_ACTIVE : ps3::CAT_ICON_INACTIVE;
        float sz = baseSz * catSubScale;
        float x = ps3::CAT_X + dx + catSubShift;
        float activeYOff = isActive ? ps3::CAT_Y_ACTIVE_OFFSET * (1.0f - catT) : 0.0f;
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
            float la = 0.9f * (1.0f - 0.55f * catT) * mPs3BootLabelReveal;
            float ls = ps3::fontScale(ps3::CAT_LABEL_SIZE);
            const char* nm = trDyn(mPs3Cats[i].name.c_str());
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
    auto drawDesc = [&](const std::string& textIn, float txDev, float labelBaselineYDev, float alpha) {
        std::string text = trDyn(textIn.c_str());
        if (text.empty()) { mPs3DescLinesTarget = 0; return; }
        // Resolution-gated subtitle size: one step bigger on small panels
        // (gDescSize = ITEM_DESC_SIZE * DESC_BOOST), unchanged at >=720p. The
        // active pad (itemSlotY) grows to fit the wrapped line count (up to 4).
        float descV = ps3::gDescSize;
        float ds = ps3::fontScale(descV);
        // Wrap width = visible frame edge minus the label x, clamped to the panel
        // (the uiScale zoom can push XCF(VW) off-screen on narrow panels).
        float rightEdge = ps3::devX(ps3::XCF(ps3::VW - ps3::ITEM_VALUE_RIGHT_PAD));
        float panelEdge = (float)mWidth - ps3::devS(ps3::ITEM_VALUE_RIGHT_PAD);
        if (rightEdge > panelEdge) rightEdge = panelEdge;
        float maxW = rightEdge - txDev;
        if (maxW < 40.0f) maxW = 40.0f;
        // Greedy word-wrap into as many lines as the text needs, then clamp to 4
        // (dynamic: short blurbs stay short, long ones use up to 4 lines). Rather
        // than silently dropping the tail past the limit, clip the last line with
        // an ellipsis (trimming whole words so it still fits the width).
        const int kMaxLines = 4;
        std::vector<std::string> wrapped;
        std::string cur, word;
        auto commit = [&]() {
            if (word.empty()) return;
            std::string trial = cur.empty() ? word : cur + " " + word;
            if (!cur.empty() && measureText(trial.c_str(), ds) > maxW) {
                wrapped.push_back(cur);
                cur = word;
            } else cur = trial;
            word.clear();
        };
        for (const char* p = text.c_str(); ; ++p) {
            if (*p == ' ' || *p == '\0') { commit(); if (*p == '\0') break; }
            else word.push_back(*p);
        }
        if (!cur.empty()) wrapped.push_back(cur);
        int nLines = (int)wrapped.size();
        if (nLines > kMaxLines) {
            nLines = kMaxLines;
            std::string last = wrapped[kMaxLines - 1];
            std::string withE = last + "...";
            while (!last.empty() && measureText(withE.c_str(), ds) > maxW) {
                size_t sp = last.find_last_of(' ');
                if (sp == std::string::npos) { last.clear(); withE = "..."; break; }
                last.erase(sp);
                withE = last + "...";
            }
            wrapped[kMaxLines - 1] = withE;
        }
        // Publish the line count so renderPs3Xmb can ease the active pad to fit it
        // (computed even when faded mid-scroll so the reflow leads the selection).
        mPs3DescLinesTarget = nLines;
        if (alpha <= 0.02f) return;   // count captured; don't draw the faded blurb
        // Tighter line height than the label so the enlarged description still
        // fits its wrapped lines in the (now adaptive) active pad.
        float lineH = ps3::devS(descV * ps3::ITEM_DESC_LINEH);
        float y0 = labelBaselineYDev + ps3::devS(ps3::ITEM_DESC_OFFSET);
        // Heavier, darker dark stroke just for the subtitle so the small grey
        // blurb stays legible over the wave wallpaper.
        float savedRatio = mTextOutlineRatio, savedMul = mTextOutlineWidthMul;
        mTextOutlineRatio = 0.20f;          // 80% transparent black stroke
        mTextOutlineWidthMul = 1.0f;        // tight 1px stroke (crisp edge, not a spread halo)
        for (int li = 0; li < nLines; li++) {
            float ly = ps3::baselineToTopY(y0 + (float)li * lineH, ds);
            drawText(wrapped[li].c_str(), txDev, ly, ds, 0.82f, 0.82f, 0.86f, alpha);
        }
        mTextOutlineRatio = savedRatio;
        mTextOutlineWidthMul = savedMul;
    };
    // Now-playing highlight for the Music column: a soft pulsing glow on the album AND
    // the track currently playing, so you can see what is playing without opening it.
    bool npLive = !mMpQueue.empty() &&
                  (mMusicPlayer.isPlaying() || mMusicPlayer.isPaused());
    int  npTi = (npLive && mMpIdx >= 0 && mMpIdx < (int)mMpQueue.size()) ? mMpQueue[mMpIdx] : -1;
    std::string npAlbum = (npTi >= 0 && npTi < (int)mMusicTracks.size())
                          ? mMusicTracks[npTi].album : std::string();
    float npPulse = 0.5f + 0.5f * sinf(mEffectTime * 2.0f * 3.14159265f / 1.4f);  // ~1.4s breathe
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
            // Now-playing glow: a soft pulsing halo behind the album AND the track
            // currently playing, drawn as concentric cyan discs (no glow shader on ES2).
            bool nowPlaying = npLive &&
                ((it.kind == PS3_MUSIC_TRACK && it.a == npTi) ||
                 (it.kind == PS3_MUSIC_ALBUM && !npAlbum.empty() && it.label == npAlbum));
            if (nowPlaying) {
                float cxg = ix + dsz * 0.5f, cyg = iy + dsz * 0.5f;
                float gb = (0.18f + 0.16f * npPulse) * alpha;
                float gr = dsz * (0.62f + 0.10f * npPulse);
                ps3FillCircle(cxg, cyg, gr,          0.45f, 0.85f, 1.0f, gb * 0.5f);
                ps3FillCircle(cxg, cyg, gr * 0.72f,  0.55f, 0.90f, 1.0f, gb * 0.8f);
                ps3FillCircle(cxg, cyg, gr * 0.50f,  0.70f, 0.95f, 1.0f, gb);
            }
            // Album folder art embedded in the column icon (like the web XMB photo
            // folders): a per-folder cover replaces the generic glass folder icon.
            bool isFolderKind = (it.kind == PS3_MUSIC_ALBUM || it.kind == PS3_PHOTO_ALBUM);
            GLuint folderCover = (it.kind == PS3_MUSIC_ALBUM) ? mpAlbumArt(it.label)
                            : (it.kind == PS3_PHOTO_ALBUM && it.b >= 0) ? photoGroupCover(it.b) : 0;
            // Scraped boxart cover replaces a ROM's generic cartridge icon when
            // available (lazy GL texture, freed on leaving Game). Aspect-fit inside
            // the icon box (covers are usually portrait) with the XMB drop shadow.
            GLuint boxTex = 0; float boxAR = 1.0f;
            if (mScrapeBoxartOn && it.kind == PS3_ROM
                && it.a >= 0 && it.a < (int)mXmbSystems.size()
                && it.b >= 0 && it.b < (int)mXmbSystems[it.a].roms.size())
                boxTex = romBoxartTex(mXmbSystems[it.a].roms[it.b], &boxAR);
            else if (mScrapeBoxartOn && it.kind == PS3_RECENT
                     && it.a >= 0 && it.a < (int)mXmbRecent.size()
                     && !mXmbRecent[it.a].romPath.empty())
                boxTex = romBoxartTex(mXmbRecent[it.a].romPath, &boxAR);   // boxart in Recently Played too
            if (isFolderKind) {
                drawFolderIcon(ix, iy, dsz, alpha, folderCover);
            } else if (boxTex) {
                float bw = dsz, bh = dsz;
                if (boxAR >= 1.0f) bh = dsz / boxAR; else bw = dsz * boxAR;
                float bx = ix + (dsz - bw) * 0.5f, by = iy + (dsz - bh) * 0.5f;
                drawIconStroke(boxTex, bx, by, bw, bh, mPs3ShadowAlpha * 0.7f * alpha);
                drawIconTex(boxTex, bx, by, bw, bh, 1.0f, 1.0f, 1.0f, alpha);
            } else {
            // Icon outline silhouette behind the flat menu icons so they read over
            // the bright wave. RetroArch/console icons (isRetroIcon) are skipped:
            // they render as bevelled glass and the dark silhouette would peek out
            // around the translucent glass as an ugly halo (the normal XMB icons do
            // not show this), so they get no stroke.
            if (it.iconTex && !isRetroIcon(it.kind))
                drawIconStroke(it.iconTex, ix, iy, dsz, dsz, mPs3ShadowAlpha * 0.7f * alpha);
            // Below ~3% alpha the live-wave refraction is indistinguishable
            // from the flat icon, so spend the glass shader only above it.
            // nmap-ONLY items (no flat texture) keep glass at any alpha or
            // they would vanish entirely (the Settings-rail lesson). The
            // threshold stays this low (NOT drawParentLayer's 0.35) because
            // drawList's alpha carries full-column category-crossfade fades:
            // a higher floor would pop the whole column flat mid-crossfade.
            if (mIconGlassReady && it.nmapTex && ps3bg::workTex()
                && (alpha > 0.03f || !it.iconTex))
                drawGlassIcon(it.nmapTex, ix, iy, dsz, dsz, it.iconR, it.iconG, it.iconB, alpha);
            else if (it.iconTex)
                drawIconTex(it.iconTex, ix, iy, dsz, dsz, it.iconR, it.iconG, it.iconB, alpha);
            }
            float tSize = isActive ? ps3::ITEM_TEXT_ACTIVE_SIZE : ps3::ITEM_TEXT_SIZE;
            float ts = ps3::fontScale(tSize);
            float tx = ps3::devX(ps3::XCP(ps3::ITEM_TEXT_X + xShiftV));
            float ty = ps3::baselineToTopY(ps3::devY(y), ts);
            const char* L = trDyn(it.label.c_str());
            // Scissor a full-height X band [bx, bx+bw], mapped to the panel's
            // DRM rotation. Used by both the label and the value tickers.
            auto scissorBand = [&](float bx, float bw) {
                int rlx = (int)bx, rly = 0, rlw = (int)bw, rlh = (int)mHeight;
                int sx, sy, sw, sh;
                switch (sDrmGlRotation ? sDrmRotationDeg : 0) {
                    case 90:  sx = rly; sy = mWidth - rlx - rlw; sw = rlh; sh = rlw; break;
                    case 180: sx = mWidth - rlx - rlw; sy = mHeight - rly - rlh; sw = rlw; sh = rlh; break;
                    case 270: sx = mHeight - rly - rlh; sy = rlx; sw = rlh; sh = rlw; break;
                    default:  sx = rlx; sy = rly; sw = rlw; sh = rlh; break;
                }
                glEnable(GL_SCISSOR_TEST); glScissor(sx, sy, sw, sh);
            };
            // Ping-pong ticker offset (hold, scroll, hold, scroll back) for text
            // wider than its window - shared by the label and the value.
            auto tickerOff = [&](float overflow) {
                float scrollT = overflow / fmaxf(1.0f, ps3::devS(70.0f));   // ~70 vpx/s
                float hold = 1.4f, period = 2.0f * (scrollT + hold);
                float ph = fmodf(mEffectTime, period);
                if (ph < hold)                       return 0.0f;
                if (ph < hold + scrollT)             return (ph - hold) / scrollT * overflow;
                if (ph < 2.0f * hold + scrollT)      return overflow;
                return overflow - (ph - 2.0f * hold - scrollT) / scrollT * overflow;
            };
            // The System Language value is a native language name (e.g. "English",
            // "Espanol") and must render verbatim - never through trDyn, where
            // "English" is itself a translation key (-> "Ingles" under es). Every
            // other value (On/Off, Enabled/Disabled, ...) localises normally.
            std::string itVal = (it.label == "System Language")
                                    ? resolvePs3ItemValue(it)
                                    : trDyn(resolvePs3ItemValue(it).c_str());
            bool hasVal = !itVal.empty();
            // Suppress the row value while a side-panel chooser is open OR fading
            // out (web sidePanelActive): the scrim panel owns that right gutter, and
            // the value being edited is shown inside the panel, so the row reverts to
            // a plain label (which then also gets the full row width).
            if ((mPs3DlgActive || mPs3DlgClosing) && mPs3DlgKind == 1) hasVal = false;
            float vRight = ps3::devX(ps3::XCF(ps3::VW - ps3::ITEM_VALUE_RIGHT_PAD));
            { float vPanelMax = (float)mWidth - ps3::devS(ps3::ITEM_VALUE_RIGHT_PAD);
              if (vRight > vPanelMax) vRight = vPanelMax; }
            // Sub-menu indicator: rows that open a deeper list get a right-edge
            // chevron. If the row also shows a value (e.g. a module's On/Off) the
            // value sits to the chevron's left. Suppressed under the side panel.
            bool opensSub = (it.kind == PS3_DATA_SUBMENU || it.kind == PS3_SYSTEM ||
                             it.kind == PS3_RECENT_LIST || it.kind == PS3_APP_LIST ||
                             it.kind == PS3_GS_ROOT || it.kind == PS3_GS_SYSTEM_ROW);
            if ((mPs3DlgActive || mPs3DlgClosing) && mPs3DlgKind == 1) opensSub = false;
            const char* kChevron = "\xE2\x80\xBA";   // > single right angle quotation mark
            float chFs = ps3::fontScale(ps3::ITEM_TEXT_SIZE);
            float gutterRight = vRight;               // far-right edge before the chevron carve-out
            if (opensSub) vRight -= measureText(kChevron, chFs) + ps3::devS(12.0f);
            float gapW = ps3::devS(14.0f);
            float totalAvail = vRight - tx;
            float labelW = measureText(L, ts);
            float vs = 0.0f, vw = 0.0f, vx = 0.0f;
            bool valScroll = false; float valAvailW = 0.0f;
            if (hasVal) {
                vs = ps3::fontScale(ps3::ITEM_TEXT_SIZE);
                vw = measureText(itVal.c_str(), vs);
                vx = vRight - vw;
                if (labelW + gapW + vw > totalAvail && labelW <= totalAvail * 0.5f) {
                    // Short field label + long VALUE (editor rows like Emulator /
                    // Core (.so)): the label keeps its natural width and the
                    // value ticker-scrolls in the space to its right. Without
                    // this split the full-width value squeezed the label window
                    // and the wrong text (the short label) bounced.
                    valAvailW = totalAvail - labelW - gapW;
                    if (valAvailW < ps3::devS(20.0f)) valAvailW = ps3::devS(20.0f);
                    valScroll = true;
                    vx = vRight - valAvailW;   // the value clip window's left edge
                }
            }
            float labelRight = hasVal ? (vx - gapW)
                                      : ((float)mWidth - ps3::devS(ps3::ITEM_VALUE_RIGHT_PAD));
            float availW = labelRight - tx;
            if (availW < ps3::devS(20.0f)) availW = ps3::devS(20.0f);
            // Long ACTIVE labels ticker-scroll (ping-pong with end pauses) within
            // [tx, tx+availW] rather than overflowing into the value / off-screen.
            // A scissor (mapped to the panel's DRM rotation, full-height band so
            // only X clips) keeps the text inside its bounds.
            float lx = tx;
            bool scissorOn = false;
            if (isActive && labelW > availW + 1.0f) {
                lx = tx - tickerOff(labelW - availW);
                scissorBand(tx, availW);
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
                float dvx = vx;
                bool vScissor = false;
                if (valScroll) {
                    // The window's left edge is vx; the ACTIVE row's overflowing
                    // value ticker-scrolls inside it, inactive rows clip to the
                    // head of the value (no motion off-focus).
                    dvx = vx - (isActive ? tickerOff(vw - valAvailW) : 0.0f);
                    scissorBand(vx, valAvailW);
                    vScissor = true;
                }
                drawTextStroke(itVal.c_str(), dvx, ty, vs, mPs3ShadowAlpha * alpha);
                drawText(itVal.c_str(), dvx, ty, vs, 0.7f, 0.7f, 0.75f, alpha * 0.85f);
                if (vScissor) glDisable(GL_SCISSOR_TEST);
            }
            if (opensSub) {
                float chW = measureText(kChevron, chFs);
                float cx = gutterRight - chW;
                float chA = isActive ? alpha : alpha * 0.7f;   // brighter on the focused row
                drawTextStroke(kChevron, cx, ty, chFs, mPs3ShadowAlpha * alpha);
                drawText(kChevron, cx, ty, chFs, 0.85f, 0.85f, 0.9f, chA);
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
    // aMul scales the whole layer (the deep-transition rail cross-fade);
    // fromChildCol starts the morph at the CHILD column (where a sub level
    // actually sits) instead of the root item column, so deep hops collapse /
    // expand the moving level from its real on-screen position.
    auto drawParentLayer = [&](std::vector<Ps3Item>& pItems, int pIdx, float t,
                               float aMul = 1.0f, bool fromChildCol = false) {
        if (pItems.empty() || aMul <= 0.05f) return;
        const float srcX = fromChildCol
            ? ps3::ITEM_ICON_X + ps3::SUBMENU_CHILD_X_SHIFT : ps3::ITEM_ICON_X;
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
            float a = (fullA + (setA - fullA) * t) * aMul;
            if (y < 40.0f)            a *= fmaxf(0.0f, y / 40.0f);
            if (y > ps3::VH - 20.0f)  a *= fmaxf(0.0f, (ps3::VH - y) / 20.0f);
            if (a <= 0.01f) continue;
            const Ps3Item& it = pItems[i];
            float psz = isRetroIcon(it.kind) ? ps3::ITEM_ICON_SIZE * ps3::RETRO_ICON_SCALE
                                             : ps3::ITEM_ICON_SIZE;   // breadcrumb cube = normal size (no active boost)
            float dsz = ps3::devS(psz);
            float ix = ps3::devX(ps3::XCL(cx, psz * 0.5f));
            float iy = ps3::devY(y - psz * 0.5f);
            // Now-playing glow + embedded album folder art (same as the main column).
            bool nowPlaying = npLive &&
                ((it.kind == PS3_MUSIC_TRACK && it.a == npTi) ||
                 (it.kind == PS3_MUSIC_ALBUM && !npAlbum.empty() && it.label == npAlbum));
            if (nowPlaying) {
                float cxg = ix + dsz * 0.5f, cyg = iy + dsz * 0.5f;
                float gb = (0.18f + 0.16f * npPulse) * a;
                float gr = dsz * (0.62f + 0.10f * npPulse);
                ps3FillCircle(cxg, cyg, gr,          0.45f, 0.85f, 1.0f, gb * 0.5f);
                ps3FillCircle(cxg, cyg, gr * 0.72f,  0.55f, 0.90f, 1.0f, gb * 0.8f);
                ps3FillCircle(cxg, cyg, gr * 0.50f,  0.70f, 0.95f, 1.0f, gb);
            }
            GLuint albumArt = (it.kind == PS3_MUSIC_ALBUM) ? mpAlbumArt(it.label)
                            : (it.kind == PS3_PHOTO_ALBUM && it.b >= 0) ? photoGroupCover(it.b) : 0;
            if (albumArt) {
                drawIconStroke(albumArt, ix, iy, dsz, dsz, mPs3ShadowAlpha * 0.7f * a);
                drawIconTex(albumArt, ix, iy, dsz, dsz, 1.0f, 1.0f, 1.0f, a);
            } else {
            if (it.iconTex && !isRetroIcon(it.kind))   // no stroke on glass/console icons
                drawIconStroke(it.iconTex, ix, iy, dsz, dsz, mPs3ShadowAlpha * 0.7f * a);
            // Glass (live wave refraction) for the PROMINENT breadcrumb cubes only
            // - the selected parent plus the near, still-legible siblings - so the
            // RetroArch / console icons stay embossed and bevelled in submenus
            // (consistent with the top level). The far, deeply-faded siblings fall
            // back to the cheap flat icon: at a <= 0.35 the glass vs flat difference
            // is imperceptible, and this keeps the per-frame live-wave glass-icon
            // count in submenus near the top-level count (no FPS regression).
            // nmap-ONLY items (the glass DATA/settings glyphs have no flat
            // texture) must keep the glass path at ANY alpha or far siblings
            // vanish from the rail entirely - that is why a Settings rail used
            // to show only the 2 nearest neighbours while Game (icons have a
            // flat fallback) showed the full faded column.
            if (mIconGlassReady && it.nmapTex && ps3bg::workTex()
                && (sel || a > 0.35f || !it.iconTex))
                drawGlassIcon(it.nmapTex, ix, iy, dsz, dsz, it.iconR, it.iconG, it.iconB, a);
            else if (it.iconTex)
                drawIconTex(it.iconTex, ix, iy, dsz, dsz, it.iconR, it.iconG, it.iconB, a);
            }
            float textA = (1.0f - t) * (sel ? ps3::ALPHA_FOCUS : ps3::ALPHA_INACTIVE) * aMul;
            if (textA > 0.02f) {
                float ts = ps3::fontScale(sel ? ps3::ITEM_TEXT_ACTIVE_SIZE : ps3::ITEM_TEXT_SIZE);
                float textBase = fromChildCol
                    ? ps3::ITEM_TEXT_X + ps3::SUBMENU_CHILD_X_SHIFT : ps3::ITEM_TEXT_X;
                float tx = ps3::devX(ps3::XCP(textBase + (cx - srcX)));
                float ty = ps3::baselineToTopY(ps3::devY(y), ts);
                const char* L = trDyn(it.label.c_str());
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
        if (!subDeep) {
            // Crossing the root: the parent morphs between the ITEM column and
            // the rail while the categories collapse/expand (catT == subT here).
            drawParentLayer(mPs3SubParentItems, mPs3SubParentIdx, subT);
        } else {
            // Deep hop (sub <-> sub-sub): the moving level morphs between the
            // CHILD column (where it actually sat on screen) and the rail, and
            // the OUTER rail (the level above it) cross-fades: out on push, in
            // on pop. The categories/frost/chevron stay pinned (catT == 1).
            int depth = (int)mPs3Stack.size();
            int railFrame = (mPs3SubDir == 1) ? depth - 3 : depth - 2;
            std::vector<Ps3Item>* railItems = nullptr; int railIdx = 0;
            if (railFrame >= 0) {
                railItems = &mPs3Stack[railFrame].items;
                railIdx = mPs3Stack[railFrame].sel;
            } else if (mPs3CatIdx >= 0 && mPs3CatIdx < (int)mPs3Cats.size()) {
                railItems = &mPs3Cats[mPs3CatIdx].items;
                railIdx = mPs3ItemIdx;
            }
            if (railItems)
                drawParentLayer(*railItems, railIdx, 1.0f, 1.0f - subT);
            drawParentLayer(mPs3SubParentItems, mPs3SubParentIdx, subT, 1.0f,
                            /*fromChildCol=*/true);
        }
        drawList(*childItems, mPs3AnimItem, ps3::SUBMENU_CHILD_X_SHIFT + ps3::SLIDE_DIST * (1.0f - subT), subT);
        drawBackChevron(catT);
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
        // Top-level category slide rail + fade-crossfade. easeOutCubic (web
        // catAnim.ease) so the rail moves fast then settles and the crossfade
        // (old out by p=0.4, new in from p=0.5) lands like the reference, not the
        // floaty symmetric smoothstep.
        float p = easeOutCubic(mPs3CatT);
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

    // Dialogs/choosers (and any OSK over them) keep crisp GL_LINEAR text.
    setGlyphAtlasAA(false);

    // Settings dialog / Theme chooser overlay on top of the menu. (The Time Zone
    // globe renders standalone via the early return above, fading in from black.)
    // Home XMB option menu (Triangle / X) renders first so a dialog opened from it
    // (Information) fades in on top of the option menu's fade-out.
    if (mPs3OptActive || mPs3OptClosing) renderXmbOpt();
    if (mPs3WizActive) renderNetWizard();
    else if (mPs3DlgActive || mPs3DlgClosing) renderPs3Dialog();   // mPs3DlgClosing: side-panel fade-out
    else if (mPs3LangActive) renderLanguagePicker();   // System Language: frosted backdrop + fade, over the menu

    // Player enter/leave cross-fade: the now-playing screen composites OVER the fading
    // XMB chrome (the mpVisible branch near the top scaled the reveal). On enter this runs
    // until the chrome is gone (then the early return above takes the perf path); on leave
    // it runs while the bar fades out with the queue still loaded (minimize).
    if (mpVisible && !mpExitFade) renderMusicPlayer();   // enter/steady: player on top; exit drew it under the chrome above
    // Restore the reveal multipliers scaled for the chrome cross-fade (they persist
    // across frames otherwise, leaving the chrome hidden after the player closes).
    if (mpChromeScaled) { mPs3BootIconReveal = mpSavedIconReveal; mPs3BootLabelReveal = mpSavedLabelReveal; }
    if (!mMpActive && !mPvActive) drawPhotoBanner();   // Sort By / Group Content change banner (column level)

    // Video "Add to Playlist" chooser over the live Video column (modal; eased fade).
    {
        float ct = mVidPlChooserActive ? 1.0f : 0.0f;
        float d = mFrameDt; if (d < 0.0f || d > 0.2f) d = 0.016f;
        mVidPlChooserAnim += (ct - mVidPlChooserAnim) * fminf(1.0f, d * 10.0f);
        if (mVidPlChooserActive || mVidPlChooserAnim > 0.004f) drawVidPlChooser();
    }
}

// ---------------------------------------------------------------------------
// Content-info hover background + description (web HOVER_BG / CINFO_DESC, drawCinfoBg).
// Only "Photo Gallery" is reachable in nano (the other web HOVER_BG items are in the
// excluded PSN category). Dwell >= 1.5s -> a full-frame cover-cropped bg image fades in
// (max alpha 0.85) over the wave, under the chrome, with the firmware title + wrapped
// description over it. Fade-in 500ms, fade-out 300ms.
// ---------------------------------------------------------------------------
void NanoMenu::drawPs3CinfoBg(const char* focusLabel, const std::string& fanFile) {
    // fanFile set => a scraped ROM is focused: show its fanart (no description).
    // Otherwise the Photo Gallery cinfo bg. The two are mutually exclusive.
    bool romFan = !fanFile.empty();
    bool isCinfo = !romFan && focusLabel && !strcmp(focusLabel, "Photo Gallery");
    std::string key = romFan ? ("fan:" + fanFile) : (isCinfo ? "Photo Gallery" : "");
    if (key != mCinfoFocusKey) { mCinfoFocusKey = key; mCinfoDwellStart = mEffectTime; }
    float dwell = romFan ? 0.5f : 1.5f;   // fanart appears a touch sooner than the cinfo
    // Wrap-safe dwell: mEffectTime is fmod(BOOTTIME,500), so the delta goes negative
    // once every ~8.3 min and would briefly force target=0 (a stray flash).
    float since = mEffectTime - mCinfoDwellStart; if (since < 0.0f) since += 500.0f;
    float target = (!key.empty() && since >= dwell) ? 0.85f : 0.0f;
    float dt = mFrameDt; if (dt < 0.0f || dt > 0.2f) dt = 0.016f;
    float dur = (target > mCinfoAlpha) ? 0.5f : 0.3f;        // fade-in 500ms / out 300ms
    float stp = (dt / dur) * 0.85f;
    // Settle deadband: snap to target once within one frame step, so the alpha does
    // NOT fence-post oscillate (0.85>0.85 is false -> would step down then up) and
    // strobe the full-screen fanart while it is supposed to be held steady.
    if (fabsf(target - mCinfoAlpha) <= stp) mCinfoAlpha = target;
    else if (target > mCinfoAlpha)          mCinfoAlpha += stp;   // fade in
    else                                    mCinfoAlpha -= stp;   // fade out
    if (mCinfoAlpha <= 0.001f) {
        // Fully faded: drop the fanart texture if it is no longer the focus, and clear
        // the shown-image alias so the next focus starts a clean fade-in.
        if (mFanartTex && (!romFan || fanFile != mFanartPath)) {
            glDeleteTextures(1, &mFanartTex); mFanartTex = 0; mFanartPath.clear(); mFanartTexW = mFanartTexH = 0;
        }
        mCinfoShownTex = 0; mCinfoShownW = mCinfoShownH = 0;
        return;
    }

    GLuint bgTex = 0; int bgW = 0, bgH = 0;
    if (romFan) {
        // Load the focused ROM's fanart, but do NOT hard-swap the visible image
        // mid-scroll: while the OLD image is still up (path differs) keep drawing it
        // so it fades out first; only bind the NEW one once nothing is shown (faded
        // to ~0 by the dwell reset, or none loaded yet). This makes scrolling a
        // scraped library a clean fade-out/fade-in instead of a strobe of pictures.
        if (!mFanartTex || (mFanartPath != fanFile && mCinfoAlpha <= 0.02f)) {
            if (mFanartTex) { glDeleteTextures(1, &mFanartTex); mFanartTex = 0; mFanartTexW = mFanartTexH = 0; }
            // Decode ASYNC off the render thread (no hitch when scrolling a scraped
            // library). mFanartPath is set now so saDrainArt routes the result here;
            // mFanartTex stays 0 until it lands, and the early-out below keeps the
            // old/empty frame until then = the existing clean fade-in.
            mFanartPath = fanFile;
            saRequestArt(fanFile, 1024, SA_CINFO_FAN, "");
        }
        bgTex = mFanartTex; bgW = mFanartTexW; bgH = mFanartTexH;
    } else if (isCinfo) {
        // Lazy-load the Photo Gallery background JPEG (stb_image; /data then /system).
        if (!mCinfoTex && !mCinfoTexTried) {
            mCinfoTexTried = true;
            const char* paths[2] = {
                "/data/system/nano_xmb/backgrounds/cinfo-bg-photogallery.jpg",
                "/system/etc/nano_xmb/backgrounds/cinfo-bg-photogallery.jpg" };
            for (const char* p : paths) {
                int w = 0, h = 0, n = 0;
                stbi_uc* d = stbi_load(p, &w, &h, &n, 4);
                if (!d) continue;
                glGenTextures(1, &mCinfoTex);
                glBindTexture(GL_TEXTURE_2D, mCinfoTex);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, d);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                stbi_image_free(d);
                mCinfoTexW = w; mCinfoTexH = h;
                break;
            }
        }
        bgTex = mCinfoTex; bgW = mCinfoTexW; bgH = mCinfoTexH;
    }
    // Track the live source as the "shown" image. When the focus moves to an item
    // with NO cinfo (a game without fanart, or a non-Photo-Gallery item), bgTex is 0
    // here, so we keep drawing the LAST shown image as it fades out - never cross-show
    // the Photo Gallery bg over a game (the reported flash) and still fade the cinfo
    // out cleanly when leaving Photo Gallery.
    if (bgTex && bgW > 0 && bgH > 0) {
        mCinfoShownTex = bgTex; mCinfoShownW = bgW; mCinfoShownH = bgH;
    } else {
        bgTex = mCinfoShownTex; bgW = mCinfoShownW; bgH = mCinfoShownH;
    }
    if (!bgTex || bgW <= 0 || bgH <= 0) return;

    const float W = (float)mWidth, H = (float)mHeight, a = mCinfoAlpha;

    // Cover-crop full-frame blit: a centred quad scaled to FILL the frame at the image's
    // aspect (overflow clipped by the viewport). Same textured-quad path as the photo
    // viewer (mTextProgram samples RGBA * vertex colour).
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    float sc = fmaxf(W / (float)bgW, H / (float)bgH);
    float dw = bgW * sc, dh = bgH * sc;
    float cx = W * 0.5f, cy = H * 0.5f, hw = dw * 0.5f, hh = dh * 0.5f;
    float qx[4] = { cx - hw, cx + hw, cx + hw, cx - hw };
    float qy[4] = { cy - hh, cy - hh, cy + hh, cy + hh };
    float uu[4] = { 0.0f, 1.0f, 1.0f, 0.0f };
    float vv[4] = { 0.0f, 0.0f, 1.0f, 1.0f };
    auto ndcX = [&](float x){ return (x / W) * 2.0f - 1.0f; };
    auto ndcY = [&](float y){ return 1.0f - (y / H) * 2.0f; };
    // ROM fanart shows at its true colours; a 50% black scrim drawn AFTER it (below)
    // provides the darkening so the ROM list reads on top. The Photo Gallery cinfo
    // sits beside its menu column, so it also keeps full brightness (no scrim).
    float tint = 1.0f;
    GLfloat verts[12], uvs[12], cols[24];
    const int order[6] = { 0, 1, 2, 0, 2, 3 };
    for (int k = 0; k < 6; k++) {
        int c = order[k];
        verts[k*2] = ndcX(qx[c]); verts[k*2+1] = ndcY(qy[c]);
        uvs[k*2] = uu[c]; uvs[k*2+1] = vv[c];
        cols[k*4] = tint; cols[k*4+1] = tint; cols[k*4+2] = tint; cols[k*4+3] = a;
    }
    glUseProgram(mTextProgram);
    if (mTextLocSharp >= 0) glUniform1f(mTextLocSharp, 0.0f);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, bgTex);
    glUniform1i(mTextLocTexture, 0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glVertexAttribPointer(mTextLocPosition, 2, GL_FLOAT, GL_FALSE, 0, verts);
    glEnableVertexAttribArray(mTextLocPosition);
    glVertexAttribPointer(mTextLocTexCoord, 2, GL_FLOAT, GL_FALSE, 0, uvs);
    glEnableVertexAttribArray(mTextLocTexCoord);
    glVertexAttribPointer(mTextLocColor, 4, GL_FLOAT, GL_FALSE, 0, cols);
    glEnableVertexAttribArray(mTextLocColor);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glDisableVertexAttribArray(mTextLocPosition);
    glDisableVertexAttribArray(mTextLocTexCoord);
    glDisableVertexAttribArray(mTextLocColor);

    // 50% black scrim over the ROM fanart (under the chrome) so the list reads on
    // top of bright art. Tracks the fanart's own fade (0..0.5) so it never pops in.
    if (romFan)
        drawQuad(0.0f, 0.0f, W, H, 0.0f, 0.0f, 0.0f, 0.5f * (a / 0.85f));

    // ROM fanart is a plain background (no firmware description); only the Photo
    // Gallery cinfo carries the descriptive paragraph.
    if (romFan) return;

    // Word-wrapped description over the bg. Web coords as VW/VH fractions mapped via the
    // ps3 layout helpers. The TITLE is the focused item's own label ("Photo Gallery"),
    // which the chrome already draws over this bg at the active slot - in the web the
    // cinfo title merely coincides with that label, so drawing a second copy here would
    // double it. We render only the description.
    float descScale  = ps3::fontScale(ps3::XCF(ps3::VH * 0.0241f));
    float lx = ps3::devX(ps3::XCP(ps3::VW * 0.3427f));

    static const char* kDesc =
        "Create a space to enjoy and enhance your photos.\n"
        "Turn the photos on your PS3\xe2\x84\xa2 system into great albums in minutes.\n"
        "You can sort your photos by themes, add music to enhance a slideshow or add "
        "custom frames to your photos. The more photos you add, the more fun you can "
        "have - the possibilities are endless!";
    std::string desc = kDesc;
    float wrapW = ps3::devS(ps3::XCF(ps3::VW * 0.527f));
    float lineH = ps3::devS(ps3::VH * 0.0345f);
    float y = ps3::devY(ps3::VH * 0.490f) - 0.5f * ps3::emPx(descScale);
    size_t start = 0;
    while (start <= desc.size()) {
        size_t nl = desc.find('\n', start);
        std::string para = desc.substr(start, (nl == std::string::npos) ? std::string::npos : nl - start);
        std::string line;
        size_t ws = 0;
        while (ws < para.size()) {
            size_t sp = para.find(' ', ws);
            std::string word = para.substr(ws, (sp == std::string::npos) ? std::string::npos : sp - ws);
            std::string trial = line.empty() ? word : line + " " + word;
            if (!line.empty() && measureText(trial.c_str(), descScale) > wrapW) {
                drawText(line.c_str(), lx, y, descScale, 0.94f, 0.94f, 0.94f, a);
                y += lineH; line = word;
            } else {
                line = trial;
            }
            if (sp == std::string::npos) break;
            ws = sp + 1;
        }
        if (!line.empty()) { drawText(line.c_str(), lx, y, descScale, 0.94f, 0.94f, 0.94f, a); y += lineH; }
        if (nl == std::string::npos) break;
        start = nl + 1;
    }
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
    // One-shot hand spin (web drawClock): the hands cycle a whole revolution and
    // settle back on the time whenever the menu context changes (submenu depth or
    // a dialog opening/closing - NOT a category switch or side-panel chooser), and
    // during the cold-boot/XMB-load reveal (fadeMul 0->1). MIN_TURNS/HOUR_TURNS are
    // integers (2 and 1) so the hands always land exactly on the current time and
    // cross into one line once mid-spin. 620ms, smoothstep-eased.
    int spinSig = (int)mPs3Stack.size() * 2 + (mPs3DlgActive ? 1 : 0);
    if (mPs3ClockSpinSig < 0) mPs3ClockSpinSig = spinSig;                       // first frame: no spin
    else if (spinSig != mPs3ClockSpinSig) { mPs3ClockSpinSig = spinSig; mPs3ClockSpinStart = mEffectTime; }
    float spinProg = fmaxf(0.0f, fminf(1.0f, (mEffectTime - mPs3ClockSpinStart) / 0.62f));
    float spinT = easeSmooth(fmaxf(spinProg, fmaxf(0.0f, 1.0f - fadeMul)));
    float spin = spinT * 2.0f * (float)M_PI;
    float hourAng = -(float)M_PI / 2.0f + (((lt.tm_hour % 12) + lt.tm_min / 60.0f) / 12.0f) * 2.0f * (float)M_PI
                    + spin * (float)ps3::CLOCK_HOUR_TURNS;
    float minAng  = -(float)M_PI / 2.0f + (lt.tm_min / 60.0f) * 2.0f * (float)M_PI
                    + spin * (float)ps3::CLOCK_MIN_TURNS;
    // grow widens the ring/hands outward by `grow` px so the same geometry, drawn
    // wider at low alpha, approximates the web's soft white glow halo (canvas
    // shadowBlur 3.5) - no blur shader on ES2.
    auto face = [&](float ox, float oy, float grow, float r, float g, float b, float a) {
        const int SEG = 28;
        float ro = R + grow, ri = fmaxf(0.0f, R - ringW - grow);
        for (int i = 0; i < SEG; i++) {
            float a0 = (float)i / SEG * 2.0f * (float)M_PI, a1 = (float)(i + 1) / SEG * 2.0f * (float)M_PI;
            float x0o = iconCX + cosf(a0) * ro + ox, y0o = iconCY + sinf(a0) * ro + oy;
            float x1o = iconCX + cosf(a1) * ro + ox, y1o = iconCY + sinf(a1) * ro + oy;
            float x0i = iconCX + cosf(a0) * ri + ox, y0i = iconCY + sinf(a0) * ri + oy;
            float x1i = iconCX + cosf(a1) * ri + ox, y1i = iconCY + sinf(a1) * ri + oy;
            drawTriangle(x0o, y0o, x1o, y1o, x0i, y0i, r, g, b, a);
            drawTriangle(x1o, y1o, x1i, y1i, x0i, y0i, r, g, b, a);
        }
        float hw = handW + grow * 2.0f;
        auto hand = [&](float ang, float len) {
            float ll = len + grow;
            float ex = iconCX + cosf(ang) * ll + ox, ey = iconCY + sinf(ang) * ll + oy;
            float bx = iconCX + ox, by = iconCY + oy;
            float px = -sinf(ang) * hw * 0.5f, py = cosf(ang) * hw * 0.5f;
            drawTriangle(bx + px, by + py, bx - px, by - py, ex + px, ey + py, r, g, b, a);
            drawTriangle(ex + px, ey + py, ex - px, ey - py, bx - px, by - py, r, g, b, a);
        };
        hand(hourAng, R * 0.47f);
        hand(minAng, R * 0.69f);
    };
    face(so[0], so[1], 0.0f, 0.0f, 0.0f, 0.0f, 0.55f * fadeMul);                // 1. dark drop shadow (panel-down)
    face(0.0f, 0.0f, ps3::devS(3.0f), 1.0f, 1.0f, 1.0f, 0.12f * fadeMul);       // 2a. soft white glow halo (outer)
    face(0.0f, 0.0f, ps3::devS(1.5f), 1.0f, 1.0f, 1.0f, 0.22f * fadeMul);       // 2b. soft white glow halo (inner)
    face(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.96f * fadeMul);                  // 3. crisp face on top
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

        // Music-playing indicator (nano): a small eighth-note at the far left only
        // while audio is ACTIVELY playing (hidden when paused/stopped/idle), so the
        // user sees music is going even after leaving Now-Playing / resuming their app.
        if (mMusicPlayer.isPlaying()) {
            float na = 0.95f * fadeMul;
            float nh = iconH * 0.80f;
            float headW = nh * 0.46f, headH = nh * 0.34f;
            float stemW = fmaxf(1.0f, ps3::devS(1.7f));
            float topY = cyc - nh * 0.5f;
            float headX = lx, headY = cyc + nh * 0.5f - headH;
            auto nrail = [&](float qx, float qy, float qw, float qh) {
                drawQuad(qx + so[0], qy + so[1], qw, qh, 0.0f, 0.0f, 0.0f, na * 0.6f);
                drawQuad(qx, qy, qw, qh, 1.0f, 1.0f, 1.0f, na);
            };
            nrail(headX, headY, headW, headH);                          // note head
            float stemX = headX + headW - stemW;
            nrail(stemX, topY, stemW, (cyc + nh * 0.5f) - topY - headH * 0.5f);   // stem
            // flag off the stem top
            drawTriangle(stemX + so[0], topY + so[1], stemX + headW * 0.75f + so[0], topY + so[1],
                         stemX + so[0], topY + nh * 0.30f + so[1], 0.0f, 0.0f, 0.0f, na * 0.6f);
            drawTriangle(stemX, topY, stemX + headW * 0.75f, topY,
                         stemX, topY + nh * 0.30f, 1.0f, 1.0f, 1.0f, na);
            lx += headW + ps3::devS(8.0f);
        }

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
// r,g,b = the wave/icon TINT applied on commit (ps3bg::setThemeColor / icon tint;
// the icon-tint nearest-match in gsOpenTintChooser depends on these, do not change).
// sr,sg,sb = the CHOOSER SWATCH square shown in the side-panel Colour picker - the
// firmware-measured palette from the web xmb (COLOR_OPTIONS[].swatch), distinct from
// the tint (e.g. Original's swatch is silver while its tint is plum).
struct Ps3ColorOpt { const char* name; float r, g, b; float sr, sg, sb; };
// {name, r,g,b (XMB icon/theme tint), sr,sg,sb (swatch preview + LED-drive hex)}.
// The sr,sg,sb set is written verbatim to persist.gammaos.primary.rgb_hex_custom and
// thus drives the physical RGB LED, so it must be saturated/near-primary (matching the
// original GammaRGB JoystickLedPicker palette) or the LED reads as pale/washed-out. The
// r,g,b tint stays soft for the on-screen icon look and is intentionally separate.
static const Ps3ColorOpt kPs3ColorOpts[] = {
    {"Original",    0.82f,0.62f,0.90f,  0.722f,0.749f,0.792f},
    {"Yellow",      1.00f,0.88f,0.20f,  1.000f,0.957f,0.000f},
    {"Green",       0.65f,0.87f,0.30f,  0.443f,1.000f,0.208f},
    {"Pink",        1.00f,0.64f,0.72f,  1.000f,0.235f,0.553f},
    {"Dark Green",  0.25f,0.70f,0.25f,  0.000f,0.600f,0.000f},
    {"Light Purple",0.82f,0.62f,0.90f,  0.667f,0.290f,1.000f},
    {"Teal",        0.30f,0.88f,0.85f,  0.000f,0.760f,0.690f},
    {"Dark Blue",   0.10f,0.30f,0.80f,  0.000f,0.150f,0.850f},
    {"Magenta",     0.70f,0.30f,0.80f,  1.000f,0.000f,1.000f},
    {"Orange",      1.00f,0.70f,0.15f,  1.000f,0.541f,0.165f},
    {"Brown",       0.62f,0.43f,0.18f,  0.545f,0.235f,0.000f},
    {"Red",         0.90f,0.22f,0.22f,  1.000f,0.180f,0.180f},
    {"Black",       0.06f,0.06f,0.075f, 0.039f,0.039f,0.047f},
    {"White",       0.95f,0.95f,0.98f,  0.949f,0.949f,0.961f},
    {"Gray",        0.55f,0.57f,0.62f,  0.549f,0.561f,0.620f},
    {"Blue",        0.20f,0.45f,0.95f,  0.000f,0.231f,1.000f},
    {"Cyan",        0.20f,0.85f,0.95f,  0.000f,0.737f,0.831f},
    {"Lime",        0.55f,0.95f,0.20f,  0.549f,1.000f,0.000f},
    {"Gold",        1.00f,0.78f,0.25f,  1.000f,0.757f,0.027f},
    {"Violet",      0.55f,0.35f,0.95f,  0.541f,0.169f,0.886f},
    {"Crimson",     0.80f,0.10f,0.30f,  0.863f,0.078f,0.235f},
};
static const int kPs3ColorCount = 21;

// Game Systems editor: icon-tint chooser (theme key 21). Defined here so it can
// see the kPs3ColorOpts swatch table above. Applied on commit (applyThemeSetting
// case 21); no live preview to keep cancel lossless for non-swatch built-in tints.
void NanoMenu::gsOpenTintChooser() {
    if (mGsEditIdx < 0 || mGsEditIdx >= (int)mXmbSystems.size()) return;
    mPs3DlgOptions.clear(); mPs3DlgSwatch.clear();
    mPs3DlgKind = 1; mPs3DlgThemeKey = 21; mPs3DlgTitle = "Icon Tint"; mPs3DlgBody.clear();
    for (int i = 0; i < kPs3ColorCount; i++) {
        mPs3DlgOptions.push_back(kPs3ColorOpts[i].name); mPs3DlgSwatch.push_back(i);
    }
    // Remember the exact current tint so cancel restores it losslessly (the
    // nearest swatch is only an approximation of arbitrary RGB).
    mGsTintOrigR = mXmbSystems[mGsEditIdx].iconR;
    mGsTintOrigG = mXmbSystems[mGsEditIdx].iconG;
    mGsTintOrigB = mXmbSystems[mGsEditIdx].iconB;
    // Pre-select the swatch nearest the current tint.
    const XmbSystem& s = mXmbSystems[mGsEditIdx];
    int best = 0; float bestd = 1e9f;
    for (int i = 0; i < kPs3ColorCount; i++) {
        float dr = kPs3ColorOpts[i].r - s.iconR, dg = kPs3ColorOpts[i].g - s.iconG,
              db = kPs3ColorOpts[i].b - s.iconB;
        float d = dr*dr + dg*dg + db*db;
        if (d < bestd) { bestd = d; best = i; }
    }
    mPs3DlgSel = best;
    mPs3DlgIconTex = 0; mPs3DlgIconNmap = nmapForIcon(22);
    mPs3DlgIconR = mPs3DlgIconG = mPs3DlgIconB = 1.0f;
    mPs3DlgOrigSel = mPs3DlgSel;
    mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgBlurValid = false;
}

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

// ---- Settings binding: data-driven leaf -> real backing setting -------------
// Each entry maps a PS3-XMB settings leaf (by label) to a real Android setting.
// options uses the parseListOptions "value:Label,..." format; a toggle is just a
// two-entry list. Keys/defaults mirror the legacy data-driven tree so both
// front-ends drive the same settings.
static const Ps3SettingBinding kPs3Bindings[] = {
    {"Screen Timeout", SettingSource::kSystem, "screen_off_timeout", "60000",
     "15000:15 seconds,30000:30 seconds,60000:1 minute,120000:2 minutes,"
     "300000:5 minutes,600000:10 minutes,1800000:30 minutes,-1:Never"},
    {"Font Size", SettingSource::kSystem, "font_scale", "1.0",
     "0.85:Small,1.0:Default,1.15:Large,1.30:Largest"},
    // Free-text setting edited via the on-screen keyboard ("@text" special).
    {"System Name", SettingSource::kProp, "persist.gammaos.nano.system_name", "", "@text"},
    {"Touch Sounds", SettingSource::kSystem, "sound_effects_enabled", "1", "0:Off,1:On"},
    {"Charging Sounds", SettingSource::kGlobal, "charging_sounds_enabled", "1", "0:Off,1:On"},
    {"Screen Lock Sounds", SettingSource::kSystem, "lockscreen_sounds_enabled", "1", "0:Off,1:On"},
    {"Battery Percentage", SettingSource::kSystem, "status_bar_show_battery_percent", "0", "0:Off,1:On"},
    {"Battery Saver", SettingSource::kGlobal, "low_power", "0", "0:Off,1:On"},
    {"Dark Theme", SettingSource::kSecure, "ui_night_mode", "1", "1:Off,2:On"},
    {"Auto-Rotate", SettingSource::kSystem, "accelerometer_rotation", "1", "0:Off,1:On"},
    // Developer Options
    {"USB Debugging", SettingSource::kGlobal, "adb_enabled", "0", "0:Off,1:On"},
    {"Stay Awake While Charging", SettingSource::kGlobal, "stay_on_while_plugged_in", "0", "0:Off,7:On"},
    {"Show Touches", SettingSource::kSystem, "show_touches", "0", "0:Off,1:On"},
    {"Pointer Location", SettingSource::kSystem, "pointer_location", "0", "0:Off,1:On"},
    {"Transition Animation Scale", SettingSource::kGlobal, "transition_animation_scale", "1.0",
     "0:Off,0.5:0.5x,1.0:1x,1.5:1.5x,2.0:2x"},
    {"Window Animation Scale", SettingSource::kGlobal, "window_animation_scale", "1.0",
     "0:Off,0.5:0.5x,1.0:1x,1.5:1.5x,2.0:2x"},
    {"Animator Duration Scale", SettingSource::kGlobal, "animator_duration_scale", "1.0",
     "0:Off,0.5:0.5x,1.0:1x,1.5:1.5x,2.0:2x"},
    // Gamepad Settings (persist.gammaos.gamepad.* props)
    {"Controller Enable", SettingSource::kProp, "persist.gammaos.gamepad.enable", "false", "false:Off,true:On"},
    {"Merge Controllers", SettingSource::kProp, "persist.gammaos.gamepad.merge", "true", "false:Off,true:On"},
    {"Hide Source Device", SettingSource::kProp, "persist.gammaos.gamepad.hide_source", "true", "false:Off,true:On"},
    // Gamepad transform props are read by the gammapad daemon via GetIntProperty,
    // so they MUST be stored as 0/1 (a "true"/"false" string parses to 0 = Off and
    // the swap/invert never engages). writeSettingValue bumps gamepad config_version.
    {"ABXY Swap", SettingSource::kProp, "persist.gammaos.gamepad.abxy_swap", "0", "0:Off,1:On"},
    {"Invert Left Stick", SettingSource::kProp, "persist.gammaos.gamepad.invert_left", "0", "0:Off,1:On"},
    {"Invert Right Stick", SettingSource::kProp, "persist.gammaos.gamepad.invert_right", "0", "0:Off,1:On"},
    {"Analog to D-Pad", SettingSource::kProp, "persist.gammaos.gamepad.analog_to_dpad", "0", "0:Off,1:On"},
    {"D-Pad to Analog", SettingSource::kProp, "persist.gammaos.gamepad.dpad_to_analog", "0", "0:Off,1:On"},
    // Combined swap (the GammaOS QS "DPAD/Analog Swap" tile): writes BOTH transform
    // props to the same value. The mirror write to dpad_to_analog is in closePs3Dialog
    // (keyed on this label so the standalone rows above stay independent).
    {"DPAD/Analog Swap", SettingSource::kProp, "persist.gammaos.gamepad.analog_to_dpad", "0", "0:Off,1:On"},
    {"Global Sensitivity", SettingSource::kProp, "persist.gammaos.gamepad.global_sensitivity", "0",
     "-3:-50%,-2:-25%,-1:-10%,0:Off,1:+10%,2:+25%,3:+50%"},
    {"PWM Enable", SettingSource::kProp, "persist.gammaos.gamepad.pwm_enable", "true", "false:Off,true:On"},
    {"PWM Intensity", SettingSource::kProp, "persist.gammaos.gamepad.pwm_intensity", "255",
     "64:64,96:96,128:128,160:160,192:192,224:224,255:255 (Max)"},
    {"D-Pad Threshold", SettingSource::kProp, "persist.gammaos.gamepad.dpad_threshold", "50",
     "10:10,20:20,30:30,40:40,50:50,60:60,70:70,80:80,90:90"},
    {"Screen Map", SettingSource::kProp, "persist.gammaos.screenmap.enabled", "0", "0:Off,1:On"},
    // Mouse Mode (persist.gammaos.gamepad.mouse_* props)
    {"Stick Speed", SettingSource::kProp, "persist.gammaos.gamepad.mouse_stick_speed", "12",
     "4:4,8:8,12:12,16:16,20:20,24:24,30:30"},
    {"D-Pad Speed", SettingSource::kProp, "persist.gammaos.gamepad.mouse_dpad_speed", "6",
     "2:2,4:4,6:6,8:8,10:10,12:12,16:16,20:20"},
    {"Boost", SettingSource::kProp, "persist.gammaos.gamepad.mouse_boost", "20",
     "15:1.5x,20:2x,30:3x,40:4x"},
    {"Scroll Speed", SettingSource::kProp, "persist.gammaos.gamepad.mouse_scroll_speed", "4",
     "1:1,2:2,4:4,8:8,16:16,30:30"},
    // GammaOS Toolbox (persist.gammaos.* props)
    {"Fan Speed", SettingSource::kProp, "persist.gammaos.fan_mode", "auto",
     "auto:Auto,cool:Cool,max:Max,off:Off"},
    {"Immersive Mode", SettingSource::kProp, "persist.gammaos.immersive", "0", "0:Off,1:On"},
    {"Refresh Rate Lock", SettingSource::kProp, "persist.gammaos.refresh.lock", "false", "false:Off,true:On"},
    {"Display Tweaks", SettingSource::kProp, "persist.gammaos.display.tweaks", "false", "false:Off,true:On"},
    {"Force Client Composition", SettingSource::kProp, "persist.gammaos.force_client_comp", "false", "false:Off,true:On"},
    {"Desktop Fullscreen", SettingSource::kProp, "persist.gammaos.desktop.fullscreen", "false", "false:Off,true:On"},
    {"Multi-Volume", SettingSource::kProp, "persist.gammaos.audio.multivolume", "false", "false:Off,true:On"},
    {"Ultra Low Power Saving", SettingSource::kProp, "persist.gammaos.ultra_low_power_saving_mode", "false", "false:Off,true:On"},
    {"RetroArch Back Button Override", SettingSource::kProp, "persist.gammaos.retroarchoverride.backbutton", "0", "0:Off,1:On"},
    {"Start+Select LED", SettingSource::kProp, "persist.gammaos.startselectled", "false", "false:Off,true:On"},
    {"USB Controller Switch", SettingSource::kProp, "persist.gammaos.usbcontrollerswitch", "false", "false:Off,true:On"},
    {"DC Dimming Emulation", SettingSource::kProp, "persist.gammaos.dcdimmingemulation", "0", "0:Off,1:On"},
    {"Phone Taskbar", SettingSource::kProp, "persist.gammaos.taskbar.phone", "true", "false:Off,true:On"},
    {"Dual Taskbar", SettingSource::kProp, "persist.gammaos.taskbar.dual", "false", "false:Off,true:On"},
    {"Black Frame Insertion", SettingSource::kProp, "persist.gammaos.bfi.enable", "false", "false:Off,true:On"},
    {"CRT Shader", SettingSource::kProp, "persist.gammaos.shader.enable", "0", "0:Off,1:On"},
    {"Dual-Stack Display", SettingSource::kProp, "persist.gammaos.dualstack.enabled", "false", "false:Off,true:On"},
    {"RGB LED", SettingSource::kProp, "persist.gammaos.rgb.enable", "false", "false:Off,true:On"},
    {"Launch Guard", SettingSource::kProp, "persist.gammaos.launch.guard.enabled", "false", "false:Off,true:On"},
    // GammaRGB (persist.gammaos.rgb.* + persist.gammargb.control - sampler polls live, no seq).
    // "@rgbeffect"/"@rgbcolor" are special choosers handled in openBoundChooser/closePs3Dialog.
    {"Effect", SettingSource::kProp, "persist.gammargb.control", "on", "@rgbeffect"},
    {"LED Colour", SettingSource::kProp, "persist.gammaos.primary.rgb_hex_custom", "", "@rgbcolor"},
    {"LED Brightness", SettingSource::kProp, "persist.gammaos.rgb.led_brightness", "255", "slider:0:255:5:0"},
    {"Scale with Brightness", SettingSource::kProp, "persist.gammaos.rgb.scale_with_brightness", "1", "0:Off,1:On"},
    {"Effect Speed", SettingSource::kProp, "persist.gammaos.rgb.effect_speed", "10", "slider:0:255:5:0"},
    {"Saturation Boost", SettingSource::kProp, "persist.gammaos.rgb.saturation_boost", "1.4", "slider:0.5:2.0:0.1:1"},
    {"Fade Enable", SettingSource::kProp, "persist.gammaos.rgb.fade.enable", "1", "0:Off,1:On"},
    {"Fade FPS", SettingSource::kProp, "persist.gammaos.rgb.fade.fps", "60", "slider:10:240:10:0"},
    {"Sampling FPS", SettingSource::kProp, "persist.gammaos.rgb.fps", "6", "slider:1:60:1:0"},
    {"Pre-FX Sampling", SettingSource::kProp, "persist.gammaos.rgb.sample.pre_fx", "1", "0:Off,1:On"},
    {"Split LEDs", SettingSource::kProp, "persist.gammaos.rgb.split", "0", "0:Off,1:On"},
    {"Split Colours", SettingSource::kProp, "persist.gammaos.rgb.color_split", "0", "0:Off,1:On"},
    {"Left Colour", SettingSource::kProp, "persist.gammaos.rgb.left_hex_custom", "", "@rgbcolor"},
    {"Right Colour", SettingSource::kProp, "persist.gammaos.rgb.right_hex_custom", "", "@rgbcolor"},
    // GammaEQ master (persist.sys.gammaeq.*; every write bumps the .seq props - see ps3BumpEqSeqs)
    {"Enable EQ", SettingSource::kProp, "persist.sys.gammaeq.enable", "0", "0:Off,1:On"},
    {"Speaker Only", SettingSource::kProp, "persist.sys.gammaeq.spk_only", "1", "0:Off,1:On"},
    {"Preamp (dB)", SettingSource::kProp, "persist.sys.gammaeq.preamp_db", "0", "slider:-24:6:1:0"},
    {"Postgain (dB)", SettingSource::kProp, "persist.sys.gammaeq.postgain_db", "0", "slider:-12:12:1:0"},
    // GammaEQ Crystalizer (persist.sys.spk.cryst*)
    {"Crystalizer", SettingSource::kProp, "persist.sys.spk.cryst", "0", "0:Off,1:On"},
    {"Cryst Amount", SettingSource::kProp, "persist.sys.spk.cryst.amount", "0.5", "slider:0:4:0.1:1"},
    {"Cryst Mix", SettingSource::kProp, "persist.sys.spk.cryst.mix", "1.0", "slider:0:1:0.05:2"},
    {"Cryst Frequency", SettingSource::kProp, "persist.sys.spk.cryst.hz", "11500", "slider:2000:20000:500:0"},
    {"Cryst Limit", SettingSource::kProp, "persist.sys.spk.cryst.limit", "0.30", "slider:0:1:0.05:2"},
    {"Cryst Pre-gain", SettingSource::kProp, "persist.sys.spk.cryst.pregain_db", "0", "slider:-24:6:1:0"},
    {"Cryst Post-gain", SettingSource::kProp, "persist.sys.spk.cryst.postgain_db", "0", "slider:-12:12:1:0"},
    // GammaEQ Bass Limiter (persist.sys.spk.lbp*)
    {"Bass Limiter", SettingSource::kProp, "persist.sys.spk.lbp", "0", "0:Off,1:On"},
    {"Bass Threshold", SettingSource::kProp, "persist.sys.spk.lbp.thr", "0.69", "slider:0:2.5:0.05:2"},
    {"Bass Attack", SettingSource::kProp, "persist.sys.spk.lbp.atk", "4", "slider:1:50:1:0"},
    {"Bass Release", SettingSource::kProp, "persist.sys.spk.lbp.rel", "110", "slider:10:500:10:0"},
    {"Bass Frequency", SettingSource::kProp, "persist.sys.spk.lbp.fc", "160", "slider:20:400:5:0"},
    // GammaEQ Mid Protector (persist.sys.spk.mp*)
    {"Mid Protector", SettingSource::kProp, "persist.sys.spk.mp", "0", "0:Off,1:On"},
    {"Mid High-Pass", SettingSource::kProp, "persist.sys.spk.mp.hpf", "220", "slider:20:1000:10:0"},
    {"Mid Low-Pass", SettingSource::kProp, "persist.sys.spk.mp.lpf", "5800", "slider:2000:16000:100:0"},
    {"Mid Threshold", SettingSource::kProp, "persist.sys.spk.mp.thr", "0.92", "slider:0:1:0.01:2"},
    {"Mid Attack", SettingSource::kProp, "persist.sys.spk.mp.atk", "2", "slider:1:50:1:0"},
    {"Mid Release", SettingSource::kProp, "persist.sys.spk.mp.rel", "120", "slider:10:500:10:0"},
    // GammaEQ Stereo Widener (persist.sys.spk.wide*)
    {"Stereo Widener", SettingSource::kProp, "persist.sys.spk.wide", "0", "0:Off,1:On"},
    {"Widener Amount", SettingSource::kProp, "persist.sys.spk.wide.amount", "1.0", "slider:0:2:0.05:2"},
    {"Widener Mix", SettingSource::kProp, "persist.sys.spk.wide.mix", "0.35", "slider:0:1:0.05:2"},
    {"Widener Pre-gain", SettingSource::kProp, "persist.sys.spk.wide.pre", "0", "slider:-12:12:1:0"},
    {"Widener Limit", SettingSource::kProp, "persist.sys.spk.wide.limit", "0.5", "slider:0:1:0.05:2"},
    {"Widener High-Pass", SettingSource::kProp, "persist.sys.spk.wide.hpf", "500", "slider:500:16000:250:0"},
    {"Widener Centre", SettingSource::kProp, "persist.sys.spk.wide.fc", "2000", "slider:500:16000:250:0"},
    // GammaEQ Parametric EQ bands (persist.sys.spk.peq*/peq2* biquad b0/b1/b2)
    {"Parametric EQ 1", SettingSource::kProp, "persist.sys.spk.peq", "0", "0:Off,1:On"},
    {"PEQ1 Band 0", SettingSource::kProp, "persist.sys.spk.peq.b0", "1.0", "slider:0:3:0.05:2"},
    {"PEQ1 Band 1", SettingSource::kProp, "persist.sys.spk.peq.b1", "0", "slider:-4:4:0.05:2"},
    {"PEQ1 Band 2", SettingSource::kProp, "persist.sys.spk.peq.b2", "0", "slider:0:2:0.05:2"},
    {"Parametric EQ 2", SettingSource::kProp, "persist.sys.spk.peq2", "0", "0:Off,1:On"},
    {"PEQ2 Band 0", SettingSource::kProp, "persist.sys.spk.peq2.b0", "1.0", "slider:0:3:0.05:2"},
    {"PEQ2 Band 1", SettingSource::kProp, "persist.sys.spk.peq2.b1", "0", "slider:-4:4:0.05:2"},
    {"PEQ2 Band 2", SettingSource::kProp, "persist.sys.spk.peq2.b2", "0", "slider:0:2:0.05:2"},
    // Boxart / cover scraper (persist.gammaos.scraper.* props). Credentials are
    // user-supplied (both services require an account/key); "@password" masks them.
    {"Scraper", SettingSource::kProp, "persist.gammaos.scraper.engine", "screenscraper",
     "screenscraper:ScreenScraper,thegamesdb:TheGamesDB"},
    {"Replace Icons with Boxart", SettingSource::kProp, "persist.gammaos.scraper.boxart", "true", "false:Off,true:On"},
    {"Hover Background Art", SettingSource::kProp, "persist.gammaos.scraper.fanart", "true", "false:Off,true:On"},
    {"Scrape Region", SettingSource::kProp, "persist.gammaos.scraper.region", "us",
     "us:USA,eu:Europe,jp:Japan,wor:World"},
    {"Overwrite Existing", SettingSource::kProp, "persist.gammaos.scraper.overwrite", "false", "false:Off,true:On"},
    {"ScreenScraper Username", SettingSource::kProp, "persist.gammaos.scraper.ssuser", "", "@text"},
    {"ScreenScraper Password", SettingSource::kProp, "persist.gammaos.scraper.sspass", "", "@password"},
    {"ScreenScraper Dev ID", SettingSource::kProp, "persist.gammaos.scraper.ssdevid", "", "@text"},
    {"ScreenScraper Dev Password", SettingSource::kProp, "persist.gammaos.scraper.ssdevpw", "", "@password"},
    {"TheGamesDB API Key", SettingSource::kProp, "persist.gammaos.scraper.tgdbkey", "", "@password"},
};

const Ps3SettingBinding* ps3BindingFor(const std::string& label) {
    for (const auto& b : kPs3Bindings) if (label == b.label) return &b;
    return nullptr;
}

// Match a stored setting value to an option value, treating boolean synonyms as
// equal. GammaOS boolean props are inconsistent on-device (some stored "true"/
// "false", some "1"/"0"), so a prop saved as "1" must still match a "true:On"
// option and vice versa. Writes still use the option's own value (canonical
// "true"/"false", matching the legacy tree, which property_get_bool accepts).
static bool ps3OptMatch(const std::string& cur, const std::string& opt) {
    if (cur == opt) return true;
    auto truthy = [](const std::string& s) { return s == "1" || s == "true" || s == "on"  || s == "yes"; };
    auto falsy  = [](const std::string& s) { return s == "0" || s == "false" || s == "off" || s == "no"; };
    if (truthy(cur) && truthy(opt)) return true;
    if (falsy(cur)  && falsy(opt))  return true;
    return false;
}

// A binding whose options string is "slider:min:max:step[:scale]" is a numeric
// slider rather than a discrete list (ranges with too many values for a list:
// LED brightness 0..255, dB gains, float macro params). scale = decimal places
// to store/display (0 = integer); if omitted it is inferred from the step.
static bool ps3SliderSpec(const char* options, float& mn, float& mx, float& step, int& scale) {
    if (!options || strncmp(options, "slider:", 7) != 0) return false;
    const char* p = options + 7;
    float vals[3] = {0.0f, 1.0f, 1.0f};
    int sc = -1;
    int idx = 0;
    char buf[32]; int bi = 0;
    auto flush = [&]() {
        buf[bi] = '\0';
        if (bi > 0) {
            if (idx < 3) vals[idx] = strtof(buf, nullptr);
            else if (idx == 3) sc = atoi(buf);
        }
        idx++; bi = 0;
    };
    for (const char* q = p; ; ++q) {
        if (*q == ':' || *q == '\0') { flush(); if (*q == '\0') break; }
        else if (bi < 31) buf[bi++] = *q;
    }
    mn = vals[0]; mx = vals[1]; step = vals[2];
    if (step <= 0.0f) step = 1.0f;
    if (sc < 0) {
        // infer decimals from the step (e.g. 0.05 -> 2, 0.1 -> 1, 1 -> 0)
        sc = 0; float s = step;
        while (sc < 4 && fabsf(s - roundf(s)) > 1e-4f) { s *= 10.0f; sc++; }
    }
    scale = sc;
    return true;
}

// Format a slider value to its scale (decimal places), trimming the float noise.
static std::string ps3FormatNum(float v, int scale) {
    if (scale < 0) scale = 0; if (scale > 4) scale = 4;
    char buf[32];
    snprintf(buf, sizeof(buf), "%.*f", scale, v);
    return std::string(buf);
}

// GammaRGB Effect chooser options, mirroring the JoystickLedPicker: Off + Follow
// Screen + Solid Colour are always offered; numbered effects 1-5 only when the
// device advertises rgb.effectN.supported. Each entry is (display label, code)
// where the code is "off" (gammargb.control=off) or an rgb.effect value.
static std::vector<std::pair<std::string,std::string>> ps3RgbEffectList() {
    std::vector<std::pair<std::string,std::string>> v;
    v.push_back({"Off", "off"});
    v.push_back({"Follow Screen", "follow"});
    v.push_back({"Solid Colour", "none"});
    for (int i = 1; i <= 5; i++) {
        char key[64]; snprintf(key, sizeof(key), "persist.gammaos.rgb.effect%d.supported", i);
        if (property_get_bool(key, false)) {
            char lbl[16]; snprintf(lbl, sizeof(lbl), "Effect %d", i);
            char code[4]; snprintf(code, sizeof(code), "%d", i);
            v.push_back({lbl, code});
        }
    }
    return v;
}

// The GammaEQ FastMixer re-reads a module's params when its .seq prop changes.
// The GammaEQ app bumps every .seq on any change, so do the same after a write.
static void ps3BumpEqSeqs() {
    static const char* kSeqKeys[] = {
        "persist.sys.spk.peq.seq", "persist.sys.spk.cryst.seq", "persist.sys.spk.lbp.seq",
        "persist.sys.spk.mp.seq",  "persist.sys.spk.wide.seq",  "persist.sys.spk.peq2.seq",
    };
    for (const char* k : kSeqKeys) {
        char buf[PROPERTY_VALUE_MAX] = {};
        property_get(k, buf, "0");
        long cur = strtol(buf, nullptr, 10);
        char out[24]; snprintf(out, sizeof(out), "%ld", cur + 1);
        property_set(k, out);
    }
}

// "#RRGGBB" for a kPs3ColorOpts swatch (uses the swatch display RGB).
static std::string ps3SwatchHex(int idx) {
    if (idx < 0 || idx >= kPs3ColorCount) return std::string("#FFFFFF");
    const Ps3ColorOpt& c = kPs3ColorOpts[idx];
    auto q = [](float v){ int n = (int)(v * 255.0f + 0.5f); return n < 0 ? 0 : (n > 255 ? 255 : n); };
    char buf[8]; snprintf(buf, sizeof(buf), "#%02X%02X%02X", q(c.sr), q(c.sg), q(c.sb));
    return std::string(buf);
}

// Nearest kPs3ColorOpts swatch to a "#RRGGBB" string (squared RGB distance).
static int ps3NearestSwatch(const std::string& hex) {
    std::string h = hex; if (!h.empty() && h[0] == '#') h = h.substr(1);
    if (h.size() < 6) return -1;
    long v = strtol(h.c_str(), nullptr, 16);
    float r = ((v >> 16) & 0xFF) / 255.0f, g = ((v >> 8) & 0xFF) / 255.0f, b = (v & 0xFF) / 255.0f;
    int best = 0; float bestD = 1e9f;
    for (int i = 0; i < kPs3ColorCount; i++) {
        float dr = kPs3ColorOpts[i].sr - r, dg = kPs3ColorOpts[i].sg - g, db = kPs3ColorOpts[i].sb - b;
        float d = dr*dr + dg*dg + db*db;
        if (d < bestD) { bestD = d; best = i; }
    }
    return best;
}

// ---- GammaEQ audio preview (AAudio looping playback) -----------------------
// Minimal RIFF/WAVE PCM16 loader: scan chunks for "fmt " (rate/channels, 16-bit
// PCM only) and "data" (samples) into an interleaved int16 vector.
static bool loadWavPcm16(const char* path, std::vector<int16_t>& out, int& rate, int& chans) {
    FILE* f = fopen(path, "rb");
    if (!f) return false;
    char hdr[12];
    if (fread(hdr, 1, 12, f) != 12 || memcmp(hdr, "RIFF", 4) || memcmp(hdr + 8, "WAVE", 4)) { fclose(f); return false; }
    int sr = 0, ch = 0, bits = 0; bool haveFmt = false;
    long dataPos = -1; uint32_t dataLen = 0;
    for (;;) {
        unsigned char ch8[8];
        if (fread(ch8, 1, 8, f) != 8) break;
        uint32_t sz = ch8[4] | (ch8[5] << 8) | (ch8[6] << 16) | ((uint32_t)ch8[7] << 24);
        if (!memcmp(ch8, "fmt ", 4)) {
            unsigned char fmt[16] = {};
            uint32_t n = sz < 16 ? sz : 16;
            if (fread(fmt, 1, n, f) != n) break;
            ch   = fmt[2] | (fmt[3] << 8);
            sr   = fmt[4] | (fmt[5] << 8) | (fmt[6] << 16) | ((uint32_t)fmt[7] << 24);
            bits = fmt[14] | (fmt[15] << 8);
            haveFmt = true;
            if (sz > n) fseek(f, (long)(sz - n), SEEK_CUR);
        } else if (!memcmp(ch8, "data", 4)) {
            dataPos = ftell(f); dataLen = sz;
            fseek(f, (long)sz, SEEK_CUR);
        } else {
            fseek(f, (long)sz, SEEK_CUR);
        }
        if (sz & 1u) fseek(f, 1, SEEK_CUR);   // chunks are word-aligned
    }
    if (!haveFmt || dataPos < 0 || bits != 16 || ch < 1) { fclose(f); return false; }
    size_t nSamples = dataLen / 2;
    out.resize(nSamples);
    fseek(f, dataPos, SEEK_SET);
    size_t got = fread(out.data(), 2, nSamples, f);
    fclose(f);
    out.resize(got);
    rate = sr; chans = ch;
    return !out.empty();
}

// AAudio data callback (runs on the audio thread): fill numFrames, looping the
// preview PCM. Free function so it uses AAudio's real opaque types; defers to the
// NanoMenu member, which owns mEqPrevPos while the stream is running.
int32_t NanoMenu::eqFillAudio(void* audioData, int32_t numFrames) {
    int16_t* dst = static_cast<int16_t*>(audioData);
    int ch = mEqPrevChans;
    int32_t want = numFrames * ch;
    size_t total = mEqPrevPcm.size();
    if (total == 0) { memset(dst, 0, (size_t)want * sizeof(int16_t)); return AAUDIO_CALLBACK_RESULT_CONTINUE; }
    size_t pos = mEqPrevPos;
    const int16_t* src = mEqPrevPcm.data();
    for (int32_t i = 0; i < want; i++) { dst[i] = src[pos]; if (++pos >= total) pos = 0; }
    mEqPrevPos = pos;
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

static aaudio_data_callback_result_t nanoEqPrevCb(AAudioStream* /*s*/, void* userData,
                                                  void* audioData, int32_t numFrames) {
    return (aaudio_data_callback_result_t)
        static_cast<NanoMenu*>(userData)->eqFillAudio(audioData, numFrames);
}

// Background-load the preview clip once (the 25MB read is the few-second cost, so
// warmEqPreview() kicks this on GammaEQ entry, well before the user can toggle).
void NanoMenu::ensureEqPcmAsync() {
    bool expected = false;
    if (!mEqPrevLoadStarted.compare_exchange_strong(expected, true)) return;   // already loading/loaded
    std::thread([this]{
        std::vector<int16_t> pcm; int rate = 48000, ch = 2;
        const char* paths[] = {
            "/data/system/nano_xmb/audio/preview.wav",   // dev override
            "/system/etc/nano_xmb/audio/preview.wav",    // shipped asset
        };
        for (const char* p : paths)
            if (loadWavPcm16(p, pcm, rate, ch)) {
                ALOGI("nano: EQ preview preloaded %zu samples %dHz x%d from %s", pcm.size(), rate, ch, p);
                break;
            }
        if (!pcm.empty()) { mEqPrevPcm.swap(pcm); mEqPrevRate = rate; mEqPrevChans = ch; mEqPrevPcmReady = true; }
        else { ALOGW("nano: EQ preview clip not found"); mEqPrevLoadStarted = false; }   // allow a later retry
    }).detach();
}

void NanoMenu::warmEqPreview() { mEqGammaEqDepth = (int)mPs3Stack.size(); ensureEqPcmAsync(); }

// Release the preview clip (25MB) once we leave the GammaEQ submenu. Safe only
// when nothing is using or loading it; otherwise it is freed on the next leave.
void NanoMenu::freeEqPcm() {
    if (mEqPreviewOn.load() || mEqPrevOpening.load()) return;            // playing / opening
    if (mEqPrevLoadStarted.load() && !mEqPrevPcmReady.load()) return;    // a load is in flight
    std::vector<int16_t>().swap(mEqPrevPcm);
    mEqPrevPcmReady = false;
    mEqPrevLoadStarted = false;
}

// Open the AAudio stream + start playback. Runs on a detached worker so a slow or
// not-yet-ready audio HAL never blocks the UI / boot. The clip must be preloaded
// (warmEqPreview); if not ready yet it kicks the load and returns, and
// eqPreviewTick() retries. If the open fails (HAL not up) it also just retries.
void NanoMenu::eqPreviewOpenWorker() {
    if (!mEqPrevPcmReady.load()) { ensureEqPcmAsync(); mEqPrevOpening = false; return; }
    if (!mEqPreviewWanted.load()) { mEqPrevOpening = false; return; }   // toggled off while loading
    AAudioStreamBuilder* b = nullptr;
    if (AAudio_createStreamBuilder(&b) != AAUDIO_OK || !b) { mEqPrevOpening = false; return; }
    AAudioStreamBuilder_setDirection(b, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setFormat(b, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setChannelCount(b, mEqPrevChans);
    AAudioStreamBuilder_setSampleRate(b, mEqPrevRate);
    AAudioStreamBuilder_setUsage(b, AAUDIO_USAGE_MEDIA);
    AAudioStreamBuilder_setPerformanceMode(b, AAUDIO_PERFORMANCE_MODE_NONE);
    AAudioStreamBuilder_setDataCallback(b, nanoEqPrevCb, this);
    AAudioStream* s = nullptr;
    aaudio_result_t r = AAudioStreamBuilder_openStream(b, &s);   // may fail while the HAL is not up
    AAudioStreamBuilder_delete(b);
    if (r != AAUDIO_OK || !s) { ALOGW("nano: EQ preview open failed (%d), will retry", (int)r); mEqPrevOpening = false; return; }
    {
        std::lock_guard<std::mutex> lk(mEqPrevMutex);
        if (!mEqPreviewWanted.load()) {   // toggled off during the (possibly slow) open: discard
            AAudioStream_requestStop(s); AAudioStream_close(s);
            mEqPrevOpening = false; return;
        }
        mEqPrevPos = 0;
        AAudioStream_requestStart(s);
        mEqPrevStream = s;
        mEqPreviewOn = true;
    }
    mEqPrevOpening = false;
    ALOGI("nano: EQ preview started");
}

void NanoMenu::tryStartEqPreviewAsync() {
    if (mEqPreviewOn.load() || mEqPrevOpening.load()) return;
    mEqPrevOpening = true;   // only the UI/render thread spawns, so this guard is race-free
    std::thread([this]{ eqPreviewOpenWorker(); }).detach();
}

// User toggled the preview on. Never blocks: the open runs on a worker and, if the
// audio HAL is not ready yet, eqPreviewTick() retries until it succeeds.
void NanoMenu::startEqPreview() {
    if (mEqPreviewWanted.load()) return;
    mEqPreviewWanted = true;
    mEqPrevRetryT = mEffectTime;
    tryStartEqPreviewAsync();
}

void NanoMenu::stopEqPreview() {
    mEqPreviewWanted = false;
    std::lock_guard<std::mutex> lk(mEqPrevMutex);
    if (mEqPrevStream) {
        AAudioStream* s = static_cast<AAudioStream*>(mEqPrevStream);
        AAudioStream_requestStop(s);
        AAudioStream_close(s);
        mEqPrevStream = nullptr;
        ALOGI("nano: EQ preview stopped");
    }
    mEqPreviewOn = false;
}

// Per-frame: if the preview is wanted but not yet playing (e.g. the audio HAL was
// not ready when first toggled), retry the async open about once a second.
void NanoMenu::eqPreviewTick() {
    if (!mEqPreviewWanted.load() || mEqPreviewOn.load() || mEqPrevOpening.load()) return;
    if (mEffectTime - mEqPrevRetryT < 1.0f) return;
    mEqPrevRetryT = mEffectTime;
    tryStartEqPreviewAsync();
}

// Cached current value for a binding. Read once per leaf (a settings get / prop
// read) then served from mPs3BindCache so the per-frame drawList stays cheap;
// updated on commit.
std::string NanoMenu::ps3BoundValue(const Ps3SettingBinding* b) {
    auto it = mPs3BindCache.find(b->label);
    if (it != mPs3BindCache.end()) return it->second;
    std::string v = readSettingValue(b->source, b->key, b->def);
    mPs3BindCache[b->label] = v;
    return v;
}

// Open the side-panel chooser for a settings-bound leaf, preselecting the option
// that matches the current value. Commit/cancel run through closePs3Dialog.
void NanoMenu::openBoundChooser(const Ps3SettingBinding* b) {
    mPs3DlgOptions.clear(); mPs3DlgSwatch.clear();
    mPs3DlgKind = 1; mPs3DlgThemeKey = 0; mPs3DlgBinding = b;
    mPs3DlgTitle = b->label; mPs3DlgBody.clear();
    std::string cur = ps3BoundValue(b);
    // Free-text setting: open the on-screen keyboard prefilled with the current
    // value; commit writes the typed string back. No side-panel dialog is shown.
    // "@password" is the masked variant (scraper credentials) - the typed text is
    // hidden on entry and shown as bullets in the menu (resolvePs3ItemValue).
    bool isText = !strcmp(b->options, "@text");
    bool isPassword = !strcmp(b->options, "@password");
    if (isText || isPassword) {
        mPs3DlgActive = false; mPs3DlgBinding = nullptr;
        std::string prefill = isPassword ? std::string() : cur;
        if (prefill.empty() && !strcmp(b->key, "persist.gammaos.nano.system_name")) {
            char mb[PROPERTY_VALUE_MAX]; property_get("ro.product.model", mb, "GammaOS");
            prefill = mb;
        }
        SettingSource src = b->source; std::string key = b->key, label = b->label;
        bool allowBlank = isPassword;   // a password field must be clearable
        openOskForPassword(label, [this, src, key, label, allowBlank](const std::string& val) {
            if (val.empty() && !allowBlank) return;   // keep the previous value rather than blanking it
            writeSettingValue(src, key, val);
            mPs3BindCache[label] = val;
            if (label == "System Name") mPs3SystemName = val;   // keep the legacy cache in sync
            mDisplayDirty = true;
        });
        mOskPasswordMode = isPassword; mOskPlaintext = !isPassword;
        mOskQuery = prefill; mOsk.caret = (int)mOskQuery.size();
        return;
    }
    // GammaRGB Effect: build the dynamic list, preselect from control + rgb.effect.
    if (!strcmp(b->options, "@rgbeffect")) {
        mPs3DlgSlider = false;
        auto list = ps3RgbEffectList();
        std::string control = readSettingValue(SettingSource::kProp, "persist.gammargb.control", "on");
        std::string effect  = readSettingValue(SettingSource::kProp, "persist.gammaos.rgb.effect", "follow");
        int sel = 0;
        for (int i = 0; i < (int)list.size(); i++) {
            mPs3DlgOptions.push_back(list[i].first); mPs3DlgSwatch.push_back(-1);
            bool isOff = (list[i].second == "off");
            if (control == "off") { if (isOff) sel = i; }
            else if (!isOff && list[i].second == effect) sel = i;
        }
        mPs3DlgSel = sel; mPs3DlgOrigSel = sel;
        mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgClosing = false; mPs3DlgBlurValid = false;
        return;
    }
    // GammaRGB LED Colour: a swatch chooser (kPs3ColorOpts), preselect nearest hex.
    if (!strcmp(b->options, "@rgbcolor")) {
        mPs3DlgSlider = false;
        for (int i = 0; i < kPs3ColorCount; i++) { mPs3DlgOptions.push_back(kPs3ColorOpts[i].name); mPs3DlgSwatch.push_back(i); }
        int sel = ps3NearestSwatch(cur); if (sel < 0) sel = 0;
        mPs3DlgSel = sel; mPs3DlgOrigSel = sel;
        mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgClosing = false; mPs3DlgBlurValid = false;
        return;
    }
    float mn, mx, step; int scale;
    if (ps3SliderSpec(b->options, mn, mx, step, scale)) {
        // Numeric slider: snap the live value into [min,max], no option list.
        mPs3DlgSlider = true;
        mPs3DlgSldMin = mn; mPs3DlgSldMax = mx; mPs3DlgSldStep = step; mPs3DlgSldScale = scale;
        float v = cur.empty() ? mn : strtof(cur.c_str(), nullptr);
        if (v < mn) v = mn; if (v > mx) v = mx;
        mPs3DlgSldVal = v;
        mPs3DlgSel = 0; mPs3DlgOrigSel = 0;
    } else {
        mPs3DlgSlider = false;
        std::vector<SettingListOption> opts = parseListOptions(b->options);
        int sel = 0;
        for (int i = 0; i < (int)opts.size(); i++) {
            mPs3DlgOptions.push_back(opts[i].label); mPs3DlgSwatch.push_back(-1);
            if (ps3OptMatch(cur, opts[i].value)) sel = i;
        }
        mPs3DlgSel = sel; mPs3DlgOrigSel = sel;
    }
    mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgClosing = false; mPs3DlgBlurValid = false;
}

// Live right-side value for a Theme Settings row (mirrors the web
// resolveItemValue): the value reflects the CURRENT selection so the menu shows
// it without opening the chooser. Non-theme rows fall back to the static value.
std::string NanoMenu::resolvePs3ItemValue(const Ps3Item& it) {
    const std::string& n = it.label;
    // Use the binding resolved once at item build (makeDataItem) instead of
    // re-scanning kPs3Bindings by string-compare on every call (this runs per
    // visible item every frame in drawList).
    if (const Ps3SettingBinding* b = it.binding) {
        std::string cur = ps3BoundValue(b);
        if (!strcmp(b->options, "@text")) {
            if (!cur.empty()) return cur;
            if (!strcmp(b->key, "persist.gammaos.nano.system_name")) {
                char mb[PROPERTY_VALUE_MAX]; property_get("ro.product.model", mb, "GammaOS");
                return std::string(mb);
            }
            return std::string("-");
        }
        if (!strcmp(b->options, "@password")) {
            if (cur.empty()) return std::string("-");
            int n = (int)cur.size(); if (n > 8) n = 8;   // never reveal the secret
            return std::string(n, '*');
        }
        if (!strcmp(b->options, "@rgbeffect")) {
            // cur is gammargb.control; "off" wins, else map the rgb.effect code.
            if (cur == "off") return std::string("Off");
            std::string effect = readSettingValue(SettingSource::kProp, "persist.gammaos.rgb.effect", "follow");
            for (const auto& e : ps3RgbEffectList()) if (e.second == effect) return e.first;
            return std::string("Follow Screen");
        }
        if (!strcmp(b->options, "@rgbcolor")) {
            if (cur.empty()) return std::string("-");
            int s = ps3NearestSwatch(cur);
            return (s >= 0) ? std::string(kPs3ColorOpts[s].name) : cur;
        }
        float mn, mx, step; int scale;
        if (ps3SliderSpec(b->options, mn, mx, step, scale)) {
            if (cur.empty()) return std::string("-");
            float v = strtof(cur.c_str(), nullptr);
            if (v < mn) v = mn; if (v > mx) v = mx;
            return ps3FormatNum(v, scale);   // normalised numeric (trims stored float noise)
        }
        for (const auto& o : parseListOptions(b->options)) if (ps3OptMatch(cur, o.value)) return o.label;
        return cur.empty() ? std::string("-") : cur;
    }
    if (n == "Theme") {
        int c = (int)(sizeof(kPs3ThemeOpts) / sizeof(kPs3ThemeOpts[0]));
        if (mPs3ThemeIdx >= 0 && mPs3ThemeIdx < c) return kPs3ThemeOpts[mPs3ThemeIdx];
    } else if (n == "Colour" || n == "Color") {
        if (mPs3ColorIdx >= 0 && mPs3ColorIdx < kPs3ColorCount) return kPs3ColorOpts[mPs3ColorIdx].name;
    } else if (n == "Background") {
        int c = (int)(sizeof(kPs3BgOpts) / sizeof(kPs3BgOpts[0]));
        if (mPs3BgIdx >= 0 && mPs3BgIdx < c) return kPs3BgOpts[mPs3BgIdx];
    } else if (n == "Wallpaper") {
        if (mCurrentEffect >= 0 && mCurrentEffect <= NUM_EFFECTS) return kEffectNames[mCurrentEffect];
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
    } else if (n == "Performance Mode") {
        return mPs3PerfModeLabel;   // cached; refreshed at build + on apply (no per-frame property_get)
    } else if (n == "Audio Preview") {
        return mEqPreviewWanted.load() ? "On" : "Off";   // transient session intent, not a setting
    } else if (n == "System Name") {
        // PS3 "System Name" = the device network name. Lazily cached so this stays
        // cheap in the per-frame drawList (read once: the user-set prop, else model).
        if (mPs3SystemName.empty()) {
            char b[PROPERTY_VALUE_MAX];
            property_get("persist.gammaos.nano.system_name", b, "");
            if (!b[0]) property_get("ro.product.model", b, "GammaOS");
            mPs3SystemName = b;
        }
        return mPs3SystemName;
    } else if (n == "System Language") {
        // Show the active UI language in its own native name (e.g. "Espanol",
        // "日本語"), updating live as the picker preview changes the locale.
        return nanoGetLocaleInfo(nanoGetLocale()).nativeName;
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
    s = trDyn(s);
    float w = measureText(s, fs);
    float x = (align == 1) ? (cxDev - w * 0.5f) : (align == 2) ? (cxDev - w) : cxDev;
    float topY = ps3::baselineToTopY(baselineDev, fs);
    drawText(s, x, topY, fs, r, g, b, a);   // even outline via drawText mode 1
}

// One selectable dialog option. Selected = larger with a soft white halo bloom
// (web shadowBlur halo); unselected = smaller, dim, with a legibility shadow.
// midDev is the vertical CENTRE of the text (web uses textBaseline='middle').
void NanoMenu::ps3DlgOption(const char* label, float cxDev, float midDev,
                            bool sel, bool leftAlign, float ap, float baseScale,
                            bool translate) {
    if (!label || !*label) return;
    if (translate) label = trDyn(label);
    // Same small-panel readability boost the dialog body uses, so chooser
    // options (e.g. System Update) are not left tiny next to the boosted body.
    baseScale *= ps3DlgFontBoost();
    float fs = baseScale * (sel ? 28.0f : 24.0f) / 16.0f;
    float w = measureText(label, fs);
    float x = leftAlign ? cxDev : (cxDev - w * 0.5f);
    float topY = midDev - 0.45f * 16.0f * fs;
    if (sel) {
        // Match the XMB active-label glow exactly: the optimised dual-halo
        // drawTextGlow (8 outer + 6 inner copies + the white centre, laid out
        // once and drawn in a single batch) with the same radii and the same
        // breathing pulse. The old hand-rolled 2-ring loop read coarser/blobbier.
        // drawTextGlow adds no dark outline, so no mTextOutlineMode juggling.
        float phase = fmodf(mEffectTime, ps3::PULSE_PERIOD_MS / 1000.0f)
                      / (ps3::PULSE_PERIOD_MS / 1000.0f);
        float s = 0.5f * (1.0f - cosf(phase * 2.0f * (float)M_PI));
        float outerA = ps3::PULSE_ALPHA_MIN + (ps3::PULSE_ALPHA_MAX - ps3::PULSE_ALPHA_MIN) * s;
        float innerA = ps3::PULSE_INNER_MIN + (ps3::PULSE_INNER_MAX - ps3::PULSE_INNER_MIN) * s;
        drawTextGlow(label, x, topY, fs, ps3::devS(5.0f), ps3::devS(2.2f),
                     outerA * ap * 0.16f, innerA * ap * 0.28f, ap);
    } else {
        // Even outline comes from drawText (mode 1) - one batched draw call.
        drawText(label, x, topY, fs, 1.0f, 1.0f, 1.0f, 0.85f * ap);
    }
}

// Footer button hint: glyph (X cross or O circle) + label, centred on slotCxDev.
void NanoMenu::ps3DlgHint(float slotCxDev, bool cross, const char* label,
                          float yDev, float baseScale, float ap) {
    ps3DlgHintG(slotCxDev, cross ? 0 : 1, label, yDev, baseScale, ap);
}

void NanoMenu::ps3DlgHintG(float slotCxDev, int glyph, const char* label,
                           float yDev, float baseScale, float ap) {
    label = trDyn(label);
    // Boost the whole hint (glyph + label) on small panels so the interactive
    // footer (Enter / Cancel / OK / Search / Skip) is not tiny on the wizard pages.
    baseScale *= ps3DlgFontBoost();
    float fs = baseScale * 22.0f / 16.0f;
    float glyphR = baseScale * 12.0f;
    float gap = baseScale * 12.0f;
    float lw = fmaxf(baseScale * 2.0f, 1.5f);
    float tw = measureText(label, fs);
    float groupW = glyphR * 2.0f + gap + tw;
    float left = slotCxDev - groupW * 0.5f;
    float gcx = left + glyphR;
    if (glyph == 0) {            // cross (X)
        float d = glyphR * 0.78f;
        ps3ThickLine(gcx - d, yDev - d, gcx + d, yDev + d, lw, 1.0f, 1.0f, 1.0f, 0.95f * ap);
        ps3ThickLine(gcx + d, yDev - d, gcx - d, yDev + d, lw, 1.0f, 1.0f, 1.0f, 0.95f * ap);
    } else if (glyph == 1) {     // ring (O)
        ps3StrokeRing(gcx, yDev, glyphR, glyphR, lw, 1.0f, 1.0f, 1.0f, 0.95f * ap);
    } else {                     // Start: the real PlayStation Start glyph - a
                                 // right-pointing filled "play" triangle.
        drawTriangle(gcx - glyphR * 0.62f, yDev - glyphR * 0.82f,
                     gcx - glyphR * 0.62f, yDev + glyphR * 0.82f,
                     gcx + glyphR * 0.98f, yDev,
                     1.0f, 1.0f, 1.0f, 0.95f * ap);
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
    mPs3DlgBinding = nullptr;   // not a settings-bound chooser
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
    } else if (n == "Wallpaper") {
        // The moving background effect picker (the old X/Y cycle, now its own side
        // menu). Options are the enabled effects; live-previewed while scrolling.
        mPs3DlgKind = 1; mPs3DlgThemeKey = 11;
        for (int i = 0; i < kNumActiveEffects; i++) {
            mPs3DlgOptions.push_back(kEffectNames[kActiveEffects[i]]);
            mPs3DlgSwatch.push_back(-1);
        }
        mPs3DlgSel = (sActiveEffectIdx >= 0 && sActiveEffectIdx < kNumActiveEffects) ? sActiveEffectIdx : 0;
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
        } else if (n == "System Information") {
            mPs3DlgBody = buildSysInfoBody();            // real build/model/serial/MAC/IP/storage
        }
    }
    mPs3DlgOrigSel = mPs3DlgSel;
    mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgBlurValid = false;
}

// Quick Menu -> Performance Mode: a side-panel chooser (kind 1, theme key 10)
// reusing the Theme Settings chooser infra. X commits via closePs3Dialog(true) ->
// applyThemeSetting(10), which writes persist.gammaos.performance_mode.
void NanoMenu::openPerformanceChooser() {
    mPs3DlgOptions.clear(); mPs3DlgSwatch.clear();
    mPs3DlgKind = 1; mPs3DlgThemeKey = 10; mPs3DlgTitle = "Performance Mode"; mPs3DlgBody.clear();
    static const char* kPerfOpts[] = {"Normal", "Max Performance", "Power Saver"};
    for (const char* s : kPerfOpts) { mPs3DlgOptions.push_back(s); mPs3DlgSwatch.push_back(-1); }
    char cur[PROPERTY_VALUE_MAX]; property_get("persist.gammaos.performance_mode", cur, "stock");
    mPs3DlgSel = !strcmp(cur, "max") ? 1 : (!strcmp(cur, "powersave") ? 2 : 0);
    mPs3DlgIconTex = 0; mPs3DlgIconNmap = nmapForIcon(21);   // performance glyph header (glass)
    mPs3DlgIconR = mPs3DlgIconG = mPs3DlgIconB = 1.0f;
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
        case 10: break;  // Performance Mode: governor change applied on commit only
        case 11:         // Wallpaper effect: live-preview the highlighted effect
            if (sel >= 0 && sel < kNumActiveEffects) {
                sActiveEffectIdx = sel;
                mCurrentEffect = kActiveEffects[sel];
                if (mCurrentEffect >= 1 && mCurrentEffect <= 10) initEffects();
                mDisplayDirty = true;
            }
            break;
        case 20: break;  // GS launch type: applied on commit
        case 23: break;  // GS remove-system confirm: applied on commit
        case 21:         // GS icon tint: live-preview the chosen swatch (Game tile + editor row recolour)
            if (mGsEditIdx >= 0 && mGsEditIdx < (int)mXmbSystems.size()
                && sel >= 0 && sel < kPs3ColorCount) {
                XmbSystem& s = mXmbSystems[mGsEditIdx];
                s.iconR = kPs3ColorOpts[sel].r;
                s.iconG = kPs3ColorOpts[sel].g;
                s.iconB = kPs3ColorOpts[sel].b;
                gsRefreshStackLevels();
                buildPs3Cats();
            }
            break;
        case 22: break;  // GS reset confirm: applied on commit
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
        case 11:    // Wallpaper effect: commit the live selection + persist it
            previewThemeSetting(11, sel);
            { char b[16]; snprintf(b, sizeof(b), "%d", mCurrentEffect);
              property_set("persist.gammaos.nano.wallpaper", b); }
            break;
        case 10: {  // Quick Menu -> Performance Mode. Replicates the legacy
            // global action / PerformanceTile: set persist.gammaos.performance_mode
            // to stock/max/powersave; the vendor governor trigger applies it.
            static const char* kPerfModes[]  = {"stock", "max", "powersave"};
            static const char* kPerfLabels[] = {"Normal", "Max Performance", "Power Saver"};
            if (sel >= 0 && sel < 3) {
                property_set("persist.gammaos.performance_mode", kPerfModes[sel]);
                mPs3PerfModeLabel = kPerfLabels[sel];
            }
            break;
        }
        case 20: {  // Game Systems editor: launch type (sel 0 = libretro, 1 = custom-package)
            if (mGsEditIdx >= 0 && mGsEditIdx < (int)mXmbSystems.size() && sel >= 0 && sel <= 1) {
                XmbSystem& s = mXmbSystems[mGsEditIdx];
                s.launchType = (sel == 1) ? XLT_CUSTOM_PACKAGE : XLT_LIBRETRO_CORE;
                s.launchPkg = (s.launchType == XLT_CUSTOM_PACKAGE) ? s.packageName : std::string();
                saveSystemsConfig();
                gsRefreshStackLevels();
                buildPs3Cats();
            }
            break;
        }
        case 21: {  // Game Systems editor: icon tint (from the colour swatches)
            if (mGsEditIdx >= 0 && mGsEditIdx < (int)mXmbSystems.size()
                && sel >= 0 && sel < kPs3ColorCount) {
                XmbSystem& s = mXmbSystems[mGsEditIdx];
                s.iconR = kPs3ColorOpts[sel].r;
                s.iconG = kPs3ColorOpts[sel].g;
                s.iconB = kPs3ColorOpts[sel].b;
                saveSystemsConfig();
                gsRefreshStackLevels();
                buildPs3Cats();
            }
            break;
        }
        case 22: {  // Game Systems editor: reset-to-default confirm (sel 1 = reset)
            if (sel == 1 && mGsEditIdx >= 0 && resetSystemToBuiltinDefaults(mGsEditIdx)) {
                saveSystemsConfig();
                gsRefreshStackLevels();
                buildPs3Cats();
            }
            break;
        }
        case 23: {  // Game Systems: remove custom system confirm (sel 1 = remove)
            if (sel == 1 && mGsEditIdx >= 0) gsRemoveSystem(mGsEditIdx);
            break;
        }
        case 24: {  // Game Systems editor: per-system scraper override
            if (mGsEditIdx >= 0 && mGsEditIdx < (int)mXmbSystems.size() && sel >= 0 && sel <= 3) {
                static const char* kVals[] = {"", "screenscraper", "thegamesdb", "off"};
                mXmbSystems[mGsEditIdx].scraperOverride = kVals[sel];
                saveSystemsConfig();
                gsRefreshStackLevels();
            }
            break;
        }
        default: break;
    }
}

void NanoMenu::closePs3Dialog(bool apply) {
    if (mPs3DlgBinding) {
        // Settings-bound chooser: write the selected option's value back to the
        // real setting on confirm (cancel just discards). Then refresh the cache
        // so the row value updates immediately.
        const Ps3SettingBinding* b = mPs3DlgBinding;
        mPs3DlgBinding = nullptr;
        if (apply) {
            if (!strcmp(b->options, "@rgbeffect")) {
                // Off -> gammargb.control=off; any effect -> control=on + rgb.effect.
                auto list = ps3RgbEffectList();
                if (mPs3DlgSel >= 0 && mPs3DlgSel < (int)list.size()) {
                    const std::string& code = list[mPs3DlgSel].second;
                    if (code == "off") {
                        writeSettingValue(SettingSource::kProp, "persist.gammargb.control", "off");
                    } else {
                        writeSettingValue(SettingSource::kProp, "persist.gammargb.control", "on");
                        writeSettingValue(SettingSource::kProp, "persist.gammaos.rgb.effect", code);
                    }
                    mPs3BindCache.erase("Effect");   // cached control value -> re-read next draw
                    mDisplayDirty = true;
                }
            } else if (!strcmp(b->options, "@rgbcolor")) {
                // Write the chosen swatch hex to this colour channel's *_hex_custom
                // (primary / left / right), like the JoystickLedPicker. The primary
                // channel also has a live rgb_hex mirror the sampler overwrites.
                if (mPs3DlgSel >= 0 && mPs3DlgSel < kPs3ColorCount) {
                    std::string hex = ps3SwatchHex(mPs3DlgSel);
                    writeSettingValue(b->source, b->key, hex);
                    if (!strcmp(b->key, "persist.gammaos.primary.rgb_hex_custom"))
                        writeSettingValue(SettingSource::kProp, "persist.gammaos.primary.rgb_hex", hex);
                    mPs3BindCache[b->label] = hex;
                    mDisplayDirty = true;
                }
            } else if (mPs3DlgSlider) {
                std::string v = ps3FormatNum(mPs3DlgSldVal, mPs3DlgSldScale);
                writeSettingValue(b->source, b->key, v);
                mPs3BindCache[b->label] = v;
                mDisplayDirty = true;
            } else {
                std::vector<SettingListOption> opts = parseListOptions(b->options);
                if (mPs3DlgSel >= 0 && mPs3DlgSel < (int)opts.size()) {
                    const std::string& v = opts[mPs3DlgSel].value;
                    writeSettingValue(b->source, b->key, v);
                    mPs3BindCache[b->label] = v;
                    // Quick Settings "DPAD/Analog Swap" tile writes BOTH transform
                    // props to the same value (the standalone Settings rows above
                    // stay independent, so key on the label not the prop).
                    if (!strcmp(b->label, "DPAD/Analog Swap"))
                        writeSettingValue(SettingSource::kProp, "persist.gammaos.gamepad.dpad_to_analog", v);
                    // Screen Map: mirror the volatile active flag and show/hide the
                    // cosmetic button-hint overlay service (functional effect is the
                    // two props, consumed by the gammapad daemon).
                    if (!strcmp(b->label, "Screen Map")) {
                        bool on = (v == "1" || v == "true");
                        writeSettingValue(SettingSource::kProp, "sys.gammaos.screenmap.active", on ? "1" : "0");
                        std::thread([on]{ system(on
                            ? "am start-foreground-service -n com.gammaos.screenmapper/.ScreenMapOverlayService --ei mode 1 2>/dev/null"
                            : "am stopservice -n com.gammaos.screenmapper/.ScreenMapOverlayService 2>/dev/null"); }).detach();
                    }
                    mDisplayDirty = true;
                }
            }
            // GammaEQ writes only take effect once the matching .seq prop changes.
            if (strstr(b->key, "persist.sys.gammaeq") || strstr(b->key, "persist.sys.spk"))
                ps3BumpEqSeqs();
        }
    } else if (mPs3DlgThemeKey == 21 && !apply) {
        // Icon-tint cancel: restore the exact original tint (not the nearest
        // swatch) and refresh the live preview.
        if (mGsEditIdx >= 0 && mGsEditIdx < (int)mXmbSystems.size()) {
            XmbSystem& s = mXmbSystems[mGsEditIdx];
            s.iconR = mGsTintOrigR; s.iconG = mGsTintOrigG; s.iconB = mGsTintOrigB;
            gsRefreshStackLevels();
            buildPs3Cats();
        }
    } else if (mPs3DlgThemeKey > 0) {
        if (apply) applyThemeSetting(mPs3DlgThemeKey, mPs3DlgSel);
        else       previewThemeSetting(mPs3DlgThemeKey, mPs3DlgOrigSel);   // revert the live preview
    }
    if (mPs3NetTestLive) { stopNetTest(); mPs3NetTestLive = false; }
    // Side-panel choosers (kind 1) play a fade + reverse-settle close animation on
    // dismiss (whether cancel or confirm). The option/swatch/sel state is left intact
    // so the fading panel still renders; input returns to the menu immediately.
    // Fullscreen dialogs (kind 0) close instantly as before.
    if (mPs3DlgKind == 1) { mPs3DlgClosing = true; mPs3DlgCloseAnim = (mPs3DlgAnim > 0.02f ? mPs3DlgAnim : 1.0f); }
    // Release the ROM Information page art (page-owned textures) on close. The
    // pending paths are cleared so any late async result is freed by saDrainArt, not
    // routed to a closed page.
    if (mPs3DlgRomInfo) {
        if (mPs3DlgFanTex) { glDeleteTextures(1, &mPs3DlgFanTex); mPs3DlgFanTex = 0; }
        if (mPs3DlgBoxTex) { glDeleteTextures(1, &mPs3DlgBoxTex); mPs3DlgBoxTex = 0; }
        mPs3DlgFanW = mPs3DlgFanH = mPs3DlgBoxW = mPs3DlgBoxH = 0;
        mPs3DlgPendingFan.clear(); mPs3DlgPendingBox.clear();
        mPs3RomInfoScroll = 0;
        mPs3DlgRomInfo = false;
    }
    mPs3DlgActive = false;
    mPs3DlgBlurValid = false;
}

// ---------------------------------------------------------------------------
// Home XMB option menu (Triangle / X). The web optMenu context "sidebar": a
// short list of real per-item actions over the focused column item. A separate
// modal from the theme chooser so Information can open a dialog cleanly.
// ---------------------------------------------------------------------------
void NanoMenu::openXmbOpt() {
    if (mPs3OptActive) return;
    // Only over the live home column - never while another modal owns input, and
    // not over a live in-game app in the overlay (where a dialog could fight it).
    if (mPs3DlgActive || mMpActive || mPvActive || mVidActive || mPs3WizActive || mPs3TzActive || mPs3LangActive
        || mPs3BrightSlider || mOskActive || mVidPlChooserActive) return;
    if (mOverlayMode && !mOverlayWallpaper) return;

    mPs3OptLabels.clear(); mPs3OptActs.clear(); mPs3OptStart.clear();
    mPs3OptSep.clear(); mPs3OptHasSub.clear(); mPs3OptSubDef.clear(); mPs3OptSubRows.clear();
    mPs3OptSubOpen = false; mPs3OptSubSel = 0;
    auto add = [&](const char* label, const char* act, bool start) {
        mPs3OptLabels.push_back(label); mPs3OptActs.push_back(act);
        mPs3OptStart.push_back(start ? 1 : 0);
        mPs3OptSep.push_back(0); mPs3OptHasSub.push_back(0);
        mPs3OptSubDef.push_back(0); mPs3OptSubRows.push_back({});
    };
    auto addSep = [&]() {   // a visual gap between the list-level group and the per-item actions
        mPs3OptLabels.push_back(""); mPs3OptActs.push_back("");
        mPs3OptStart.push_back(0); mPs3OptSep.push_back(1); mPs3OptHasSub.push_back(0);
        mPs3OptSubDef.push_back(0); mPs3OptSubRows.push_back({});
    };
    auto addSub = [&](const char* label, bool start, const std::vector<Ps3OptSub>& sub, int subDef) {
        mPs3OptLabels.push_back(label); mPs3OptActs.push_back("");
        mPs3OptStart.push_back(start ? 1 : 0); mPs3OptSep.push_back(0); mPs3OptHasSub.push_back(1);
        mPs3OptSubDef.push_back(subDef); mPs3OptSubRows.push_back(sub);
    };
    // Photo Sort By submenu (web photoSortBy, 5 firmware options). Default focus tracks
    // the live sort. Film/Import Date desc/asc + Image Name.
    auto photoSortSub = [&]() {
        std::vector<Ps3OptSub> v;
        auto S = [](const char* l, int f, int d) { Ps3OptSub s; s.label = l; s.kind = 0; s.field = f; s.dir = d; return s; };
        v.push_back(S("Film Date (newest)",   0, 0));
        v.push_back(S("Film Date (oldest)",   0, 1));
        v.push_back(S("Import Date (newest)", 1, 0));
        v.push_back(S("Import Date (oldest)", 1, 1));
        v.push_back(S("Image Name",           2, 1));
        return v;
    };
    auto photoSortDef = [&]() {
        if (mPhotoSortField == 2) return 4;
        if (mPhotoSortField == 1) return mPhotoSortDir == 0 ? 2 : 3;
        return mPhotoSortDir == 0 ? 0 : 1;
    };
    // Photo Slideshow style submenu (web photoStyles, 5 styles).
    auto photoStyleSub = [&]() {
        std::vector<Ps3OptSub> v;
        static const char* kS[5] = {"Normal", "Slide", "Portrait", "Photo Album", "Photo Album 2"};
        for (int i = 0; i < 5; i++) { Ps3OptSub s; s.label = kS[i]; s.kind = 2; s.sstyle = i; v.push_back(s); }
        return v;
    };
    // Photo Group Content submenu (By Month / By Year / By Album / All).
    auto photoGroupSub = [&]() {
        std::vector<Ps3OptSub> v;
        static const char* kG[4] = {"By Month", "By Year", "By Album", "All"};
        for (int i = 0; i < 4; i++) { Ps3OptSub s; s.label = kG[i]; s.kind = 1; s.groupIdx = i; v.push_back(s); }
        return v;
    };

    // Photo thumbnail grid: per-photo options for the focused thumbnail (the grid
    // is a full-takeover screen with an empty stack level, so it is handled before
    // the normal item-list path). Layout/order 1:1 with the web in-album option menu.
    if (ps3TopScreenKind() == PHOTO_GRID) {
        if (mPhotoGridCursor < 0 || mPhotoGridCursor >= (int)mPhotoGridList.size()) return;
        int pIdx = mPhotoGridList[mPhotoGridCursor];
        add("Delete Multiple", "delmulti", false);
        add("Copy Multiple", "copymulti", false);
        addSub("Sort By", false, photoSortSub(), photoSortDef());
        addSub("Slideshow", true, photoStyleSub(), (mPvSlideStyle >= 0 && mPvSlideStyle < 5) ? mPvSlideStyle : 0);
        addSep();
        add("View", "pgview", false);
        add("Copy", "pgcopy", false);
        add("Add to Playlist", "pgaddgrid", false);
        add("Print", "pgprint", false);
        add("Delete", "pgdelete", false);
        add("Information", "photoinfo", false);
        mPs3OptCtxKind = PS3_PHOTO; mPs3OptCtxA = pIdx; mPs3OptCtxB = 0;
        mPs3OptCtxLabel = (pIdx >= 0 && pIdx < (int)mPhotos.size()) ? mPhotos[pIdx].name : std::string();
        mPs3OptCtxPayload.clear(); mPs3OptCtxDesc.clear();
        mPs3OptCtxList.clear(); mPs3OptCtxSel = 0;
        mPs3OptSel = xmbOptDefaultSel(); mPs3OptActive = true; mPs3OptClosing = false; mPs3OptAnim = 0.0f; mPs3OptBlurValid = false;
        return;
    }

    std::vector<Ps3Item>& items = ps3CurItems();
    int sel = ps3CurSel();
    if (items.empty() || sel < 0 || sel >= (int)items.size()) return;
    const Ps3Item& it = items[sel];
    switch (it.kind) {
        case PS3_ROM: case PS3_RECENT:
            // ROMs get the rich scraped Information page (cover + fanart + metadata,
            // falling back to file path/size/core when nothing has been scraped).
            add("Start", "start", true); add("Information", "rominfo", false); break;
        case PS3_APP: case PS3_LAUNCH_PKG:
            add("Start", "start", true); add("Information", "info", false); break;
        case PS3_MUSIC_ALBUM:
            add("Play", "playalbum", true); add("Information", "info", false); break;
        case PS3_MUSIC_TRACK:
            add("Play", "playtrack", true); add("Information", "info", false); break;
        case PS3_MUSIC_PLAYLIST:
            add("Play", "playpl", true); add("Information", "info", false); break;
        case PS3_VIDEO_FILE:
            // Web video option menu (index.html 13446-13464): a watched title (resumeSec>0)
            // shows Resume + Play from Beginning; an unwatched one shows a single Play. Then
            // Copy / Delete / Information. "Add to Playlist" is a nano addition (web video has
            // no playlists), inserted after the play row(s).
            if (it.a >= 0 && it.a < (int)mVideos.size() && mVideos[it.a].resumeSec > 0.0) {
                add("Resume", "vplay", true);
                add("Play from Beginning", "vplaybegin", false);
            } else {
                add("Play", "vplay", true);
            }
            add("Add to Playlist", "vaddpl", false);
            add("Copy", "vcopy", false); add("Delete", "vdelete", false);
            add("Information", "vinfo", false); break;
        case PS3_PHOTO_ALBUM: {
            // Photo column-root folder: 1:1 with the web (Sort By + Group Content,
            // a gap, then Slideshow / Copy / Delete / Information).
            addSub("Sort By", false, photoSortSub(), photoSortDef());
            addSub("Group Content", false, photoGroupSub(), mPhotoGroupIdx);
            addSep();
            addSub("Slideshow", true, photoStyleSub(), (mPvSlideStyle >= 0 && mPvSlideStyle < 5) ? mPvSlideStyle : 0);
            add("Copy", "pcopyfolder", false);
            add("Delete", "pdelfolder", false);
            add("Information", "photofolderinfo", false); break;
        }
        default:
            add("Information", "info", false); break;
    }
    if (mPs3OptLabels.empty()) return;

    mPs3OptCtxKind = it.kind; mPs3OptCtxA = it.a; mPs3OptCtxB = it.b;
    mPs3OptCtxLabel = it.label; mPs3OptCtxPayload = it.payloadStr; mPs3OptCtxDesc = it.desc;
    mPs3OptCtxList = items; mPs3OptCtxSel = sel;
    mPs3OptSel = xmbOptDefaultSel();
    mPs3OptActive = true; mPs3OptClosing = false; mPs3OptAnim = 0.0f; mPs3OptBlurValid = false;
}

void NanoMenu::closeXmbOpt() {
    if (!mPs3OptActive) return;
    mPs3OptClosing = true;
    mPs3OptCloseAnim = (mPs3OptAnim > 0.02f ? mPs3OptAnim : 1.0f);
    mPs3OptActive = false;
    mPs3OptSubOpen = false;
}

// Default cursor: the first per-item action (the first non-separator row after the
// list-level group's separator), or row 0 if there is no separator (web openOptMenu).
int NanoMenu::xmbOptDefaultSel() {
    int n = (int)mPs3OptSep.size();
    int sep = -1;
    for (int i = 0; i < n; i++) if (mPs3OptSep[i]) { sep = i; break; }
    if (sep < 0) return 0;
    for (int i = sep + 1; i < n; i++) if (!mPs3OptSep[i]) return i;
    return 0;
}

void NanoMenu::xmbOptMove(int dir) {
    if (mPs3OptSubOpen) {   // navigate within the open submenu (wrap)
        int sn = (mPs3OptSel >= 0 && mPs3OptSel < (int)mPs3OptSubRows.size())
                 ? (int)mPs3OptSubRows[mPs3OptSel].size() : 0;
        if (sn <= 0) return;
        mPs3OptSubSel = (mPs3OptSubSel + (dir % sn) + sn) % sn;
        return;
    }
    int n = (int)mPs3OptLabels.size();
    if (n <= 0) return;
    int s = mPs3OptSel;
    do { s = (s + dir + n) % n; } while (s >= 0 && s < (int)mPs3OptSep.size() && mPs3OptSep[s]);
    mPs3OptSel = s;
}

void NanoMenu::xmbOptOpenSub() {
    if (mPs3OptSubOpen) return;
    if (mPs3OptSel < 0 || mPs3OptSel >= (int)mPs3OptHasSub.size()) return;
    if (!mPs3OptHasSub[mPs3OptSel] || mPs3OptSubRows[mPs3OptSel].empty()) return;
    mPs3OptSubOpen = true;
    int def = (mPs3OptSel < (int)mPs3OptSubDef.size()) ? mPs3OptSubDef[mPs3OptSel] : 0;
    int sn = (int)mPs3OptSubRows[mPs3OptSel].size();
    mPs3OptSubSel = (def >= 0 && def < sn) ? def : 0;
}

void NanoMenu::xmbOptCloseSub() {
    mPs3OptSubOpen = false;
}

void NanoMenu::xmbOptEnter() {
    if (mPs3OptSubOpen) {
        if (mPs3OptSel >= 0 && mPs3OptSel < (int)mPs3OptSubRows.size()
            && mPs3OptSubSel >= 0 && mPs3OptSubSel < (int)mPs3OptSubRows[mPs3OptSel].size())
            xmbOptApplySub(mPs3OptSubRows[mPs3OptSel][mPs3OptSubSel]);
        return;
    }
    if (mPs3OptSel < 0 || mPs3OptSel >= (int)mPs3OptActs.size()) return;
    if (mPs3OptSel < (int)mPs3OptHasSub.size() && mPs3OptHasSub[mPs3OptSel]) { xmbOptOpenSub(); return; }
    std::string act = mPs3OptActs[mPs3OptSel];
    closeXmbOpt();
    xmbOptAction(act);
}

// Apply a chosen submenu row, then close the whole option menu (web applyOptSub).
void NanoMenu::xmbOptApplySub(const Ps3OptSub& sr) {
    if (sr.kind == 0) {            // Sort By
        photoSetSort(sr.field, sr.dir);
        closeXmbOpt();
    } else if (sr.kind == 1) {     // Group Content
        photoSetGroup(sr.groupIdx);
        closeXmbOpt();
    } else if (sr.kind == 2) {     // Slideshow style -> start the show
        int style = sr.sstyle;
        closeXmbOpt();
        if (mPs3OptCtxKind == PS3_PHOTO_ALBUM) {
            std::vector<PhotoGroup> groups = photoGroups();
            if (mPs3OptCtxA >= 0 && mPs3OptCtxA < (int)groups.size() && !groups[mPs3OptCtxA].idx.empty())
                pvSlideshowStart(groups[mPs3OptCtxA].idx, 0, style);
        } else {   // grid (PS3_PHOTO): slideshow the open grid from the focused photo
            int vi = 0; for (size_t i = 0; i < mPhotoGridList.size(); i++)
                if (mPhotoGridList[i] == mPs3OptCtxA) { vi = (int)i; break; }
            if (!mPhotoGridList.empty()) pvSlideshowStart(mPhotoGridList, vi, style);
        }
    }
}

void NanoMenu::xmbOptAction(const std::string& act) {
    if (act == "info") {
        // Fullscreen info page. For a music track, show the FULL tag set (probed
        // fresh so genre/year/track are included even if not stored in the library);
        // otherwise fall back to the item's name + description (web openContentInfo).
        std::string title = mPs3OptCtxLabel.empty() ? std::string("Information") : mPs3OptCtxLabel;
        std::string body;
        if (mPs3OptCtxKind == PS3_MUSIC_TRACK
            && mPs3OptCtxA >= 0 && mPs3OptCtxA < (int)mMusicTracks.size()) {
            const MusicTrack& t = mMusicTracks[mPs3OptCtxA];
            NanoAudioPlayer::Meta m;
            NanoAudioPlayer::probe(t.file, m);   // best-effort; uses stored values as fallback
            auto pick = [](const std::string& a, const std::string& b) {
                return !a.empty() ? a : b;
            };
            std::string fname = t.file;
            size_t sl = fname.find_last_of('/');
            if (sl != std::string::npos) fname = fname.substr(sl + 1);
            title = pick(pick(m.title, t.title), fname);
            auto row = [&](const char* label, const std::string& v) {
                if (v.empty()) return;
                char pad[20]; snprintf(pad, sizeof(pad), "%-14s", label);
                body += pad; body += v; body += "\n";
            };
            row("Title",  pick(m.title, t.title));
            row("Artist", pick(m.artist, t.artist));
            row("Album",  pick(m.album, t.album));
            row("Genre",  m.genre);
            row("Release Year", m.year);
            row("Track No.", m.track);
            double dur = m.durationSec > 0.0 ? m.durationSec : t.durationSec;
            if (dur > 0.0) { int s = (int)(dur + 0.5); char b[24];
                snprintf(b, sizeof(b), "%d:%02d", s / 60, s % 60); row("Playing Time", b); }
            row("Format", pick(m.codec, t.codec));
            if (m.sampleRate > 0) { char b[24]; snprintf(b, sizeof(b), "%d Hz", m.sampleRate); row("Sample Rate", b); }
            if (m.channels > 0)   row("Channels", m.channels >= 2 ? "Stereo" : "Mono");
            if (m.bitRate > 0)    { char b[24]; snprintf(b, sizeof(b), "%d kbps", m.bitRate / 1000); row("Bitrate", b); }
            row("File", fname);
            if (body.empty()) body = "No information is available.";
        } else {
            body = mPs3OptCtxDesc.empty() ? std::string("No information is available.") : mPs3OptCtxDesc;
        }
        mPs3DlgOptions.clear(); mPs3DlgSwatch.clear();
        mPs3DlgKind = 0; mPs3DlgType = 0; mPs3DlgThemeKey = 0; mPs3DlgBinding = nullptr;
        mPs3DlgIllust = 0; mPs3DlgNotice.clear(); mPs3DlgRomInfo = false;
        mPs3DlgTitle = title;
        mPs3DlgBody  = body;
        mPs3DlgSel = 0; mPs3DlgOrigSel = 0;
        mPs3DlgIconTex = 0; mPs3DlgIconNmap = 0; mPs3DlgIconR = mPs3DlgIconG = mPs3DlgIconB = 1.0f;
        mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgBlurValid = false;
        return;
    }
    if (act == "rominfo") {
        // Rich game Information page. When the ROM has scraped data, show the cover,
        // a faint fanart backdrop and the scraped metadata (synopsis/genre/players/
        // rating/release/developer/publisher). Otherwise fall back to the firmware
        // file facts: name, file name, path, size and core/app.
        std::string romPath, name, core, sysName;
        bool isApp = false;
        if (mPs3OptCtxKind == PS3_ROM
            && mPs3OptCtxA >= 0 && mPs3OptCtxA < (int)mXmbSystems.size()) {
            const XmbSystem& s = mXmbSystems[mPs3OptCtxA];
            if (mPs3OptCtxB >= 0 && mPs3OptCtxB < (int)s.roms.size()) romPath = s.roms[mPs3OptCtxB];
            if (mPs3OptCtxB >= 0 && mPs3OptCtxB < (int)s.displayNames.size()) name = s.displayNames[mPs3OptCtxB];
            isApp = s.isStandalone();
            core = isApp ? s.launchPkg : s.coreSo;
            sysName = s.name;
        } else if (mPs3OptCtxKind == PS3_RECENT
                   && mPs3OptCtxA >= 0 && mPs3OptCtxA < (int)mXmbRecent.size()) {
            // buildRecentSubmenu iterates mXmbRecent, so it.a (= mPs3OptCtxA) indexes it.
            const XmbRecentEntry& r = mXmbRecent[mPs3OptCtxA];
            romPath = r.romPath; name = r.displayName;
            isApp = r.standalone;
            core = isApp ? r.launchPkg : r.coreSo;
            sysName = r.systemName;
        }
        if (name.empty()) name = mPs3OptCtxLabel;
        mPs3RomInfoCoreIsApp = isApp;

        // File name / directory split + on-disk size.
        std::string fname = romPath, dir = "/";
        size_t slash = romPath.find_last_of('/');
        if (slash != std::string::npos) {
            fname = romPath.substr(slash + 1);
            dir = (slash == 0) ? std::string("/") : romPath.substr(0, slash);
        }
        std::string sizeStr;
        struct stat stt;
        if (!romPath.empty() && stat(romPath.c_str(), &stt) == 0)
            sizeStr = fmtFileSize((int64_t)stt.st_size);

        // Free any prior info-page art before (maybe) decoding new ones.
        if (mPs3DlgFanTex) { glDeleteTextures(1, &mPs3DlgFanTex); mPs3DlgFanTex = 0; }
        if (mPs3DlgBoxTex) { glDeleteTextures(1, &mPs3DlgBoxTex); mPs3DlgBoxTex = 0; }
        mPs3DlgFanW = mPs3DlgFanH = 0; mPs3DlgBoxW = mPs3DlgBoxH = 0;

        const ScrapeEntry* se = romPath.empty() ? nullptr : scrapeEntryFor(romPath);
        bool rich = se && (!se->box.empty() || !se->fan.empty() ||
                           !se->synopsis.empty() || !se->genre.empty() ||
                           !se->developer.empty() || !se->publisher.empty() ||
                           !se->players.empty() || !se->rating.empty() ||
                           !se->releaseDate.empty());

        mPs3DlgOptions.clear(); mPs3DlgSwatch.clear();
        mPs3DlgKind = 0; mPs3DlgType = 0; mPs3DlgThemeKey = 0; mPs3DlgBinding = nullptr;
        mPs3DlgIllust = 0; mPs3DlgNotice.clear();
        mPs3DlgTitle = name.empty() ? std::string("Information") : name;
        mPs3DlgSel = 0; mPs3DlgOrigSel = 0;
        mPs3DlgIconTex = 0; mPs3DlgIconNmap = 0; mPs3DlgIconR = mPs3DlgIconG = mPs3DlgIconB = 1.0f;

        if (rich) {
            mPs3DlgRomInfo = true;
            mPs3RomInfoScroll = 0;
            mPs3RomInfoSyn     = se->synopsis;
            mPs3RomInfoGenre   = se->genre;
            mPs3RomInfoPlayers = se->players;
            mPs3RomInfoRating  = se->rating;
            mPs3RomInfoDate    = se->releaseDate;
            mPs3RomInfoDev     = se->developer;
            mPs3RomInfoPub     = se->publisher;
            mPs3RomInfoFileName = fname;
            mPs3RomInfoDir     = dir;
            mPs3RomInfoSize    = sizeStr;
            mPs3RomInfoCore    = core;
            mPs3RomInfoSystem  = sysName;
            // Decode the art ASYNC into page-owned textures (no hitch on open). The
            // pending paths route the drained results; the renderers guard on tex!=0,
            // so the page draws metadata immediately and the art fades in (with
            // mPs3DlgAnim) once the worker lands. Freed on dialog close.
            mPs3DlgPendingFan = se->fan;
            mPs3DlgPendingBox = se->box;
            if (!se->fan.empty()) saRequestArt(se->fan, 1024, SA_DLG_FAN, "");
            if (!se->box.empty()) saRequestArt(se->box, 512,  SA_DLG_BOX, "");
            mPs3DlgBody.clear();
        } else {
            // Fallback: firmware file facts, centred like the other Information pages.
            mPs3DlgRomInfo = false;
            std::string body;
            auto row = [&](const char* label, const std::string& v) {
                if (v.empty()) return;
                char pad[24]; snprintf(pad, sizeof(pad), "%-15s", label);
                body += pad; body += v; body += "\n";
            };
            row("Title", name);
            row("File Name", fname);
            row("Path", dir);
            row("Size", sizeStr);
            row(isApp ? "App" : "Core", core);
            row("System", sysName);
            if (body.empty()) body = "No information is available.";
            mPs3DlgBody = body;
        }
        mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgBlurValid = false;
        return;
    }
    if (act == "start") {
        // Same as pressing Cross on the focused game/app.
        if (mPs3OptCtxKind == PS3_ROM) {
            mXmbSystemIndex = mPs3OptCtxA; mXmbGameIndex = mPs3OptCtxB; mSearchActive = false;
            if (!isLaunchReady()) { showLaunchBusyToast(); return; }
            if (mOverlayMode) { overlayLaunchGame(); return; }
            launchXmbGame(); return;
        }
        if (mPs3OptCtxKind == PS3_RECENT) {
            mXmbSystemIndex = -1; mXmbGameIndex = mPs3OptCtxA; mSearchActive = false;
            if (!isLaunchReady()) { showLaunchBusyToast(); return; }
            if (mOverlayMode) { overlayLaunchGame(); return; }
            launchXmbGame(); return;
        }
        if (mPs3OptCtxKind == PS3_APP || mPs3OptCtxKind == PS3_LAUNCH_PKG) {
            if (mPs3OptCtxPayload.empty()) return;
            if (mOverlayMode) { overlayLaunchPackage(mPs3OptCtxPayload); return; }
            if (!isLaunchReady()) { showLaunchBusyToast(); return; }
            property_set("sys.gammaos.nano.launch_app", mPs3OptCtxPayload.c_str());
            property_set("sys.gammaos.nano.launched_pkg", mPs3OptCtxPayload.c_str());
            setLaunchRomPath("");
            property_set("sys.gammaos.nano.launch_core", "");
            property_set("persist.gammaos.nano.qr_prepared", "0");
            property_set("persist.gammaos.nano.qr_core", "");
            property_set("sys.gammaos.nano.return_apps", "1");
            property_set("service.bootanim.nano_retroarch", "1");
            property_set("sys.gammaos.nano.drop_input", "1");
            mWaitForRelease = true;
        }
        return;
    }
    if (act == "playtrack") { openMusicPlayer(mPs3OptCtxList, mPs3OptCtxSel); return; }
    if (act == "playalbum") {
        std::vector<std::string> albums = musicAlbumNames();
        if (mPs3OptCtxA >= 0 && mPs3OptCtxA < (int)albums.size()) {
            Ps3Level lvl; buildMusicAlbumSubmenu(mPs3OptCtxA, lvl);
            if (!lvl.items.empty()) openMusicPlayer(lvl.items, 0);
        }
        return;
    }
    if (act == "playpl") {
        if (mPs3OptCtxA >= 0 && mPs3OptCtxA < (int)mMusicPlaylists.size()) {
            Ps3Level lvl; buildMusicPlaylistSubmenu(mPs3OptCtxA, lvl);
            if (!lvl.items.empty()) openMusicPlayer(lvl.items, 0);
        }
        return;
    }
    if (act == "vplay") { openVideoPlayer(mPs3OptCtxList, mPs3OptCtxSel, 1); return; }   // Resume (or Play if unwatched); no prompt
    if (act == "vplaybegin") {   // "Play from Beginning" (web playbegin): clear the bookmark, start at 0
        if (mPs3OptCtxA >= 0 && mPs3OptCtxA < (int)mVideos.size() && mVideos[mPs3OptCtxA].resumeSec != 0.0) {
            mVideos[mPs3OptCtxA].resumeSec = 0.0; mVidResumeDirty = true;
        }
        openVideoPlayer(mPs3OptCtxList, mPs3OptCtxSel, 0);
        return;
    }
    if (act == "vaddpl") {   // add the focused video to a playlist (chooser over the column)
        if (mPs3OptCtxA >= 0 && mPs3OptCtxA < (int)mVideos.size())
            vidOpenAddChooser(mVideos[mPs3OptCtxA].file);
        return;
    }
    if (act == "vcopy" || act == "vdelete") {   // simulated (web doOptAction no-op) -> result dialog
        mPs3DlgOptions.clear(); mPs3DlgSwatch.clear();
        mPs3DlgKind = 0; mPs3DlgType = 0; mPs3DlgThemeKey = 0; mPs3DlgBinding = nullptr;
        mPs3DlgIllust = 0; mPs3DlgNotice.clear();
        mPs3DlgTitle = ""; mPs3DlgBody = (act == "vdelete") ? "Delete completed." : "Copy completed.";
        mPs3DlgSel = 0; mPs3DlgOrigSel = 0;
        mPs3DlgIconTex = 0; mPs3DlgIconNmap = 0; mPs3DlgIconR = mPs3DlgIconG = mPs3DlgIconB = 1.0f;
        mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgBlurValid = false;
        return;
    }
    if (act == "vinfo") {   // video Information (7 firmware lines, web openContentInfo 13456-13464)
        std::string title = mPs3OptCtxLabel.empty() ? std::string("Information") : mPs3OptCtxLabel;
        std::string body = "No information is available.";
        if (mPs3OptCtxA >= 0 && mPs3OptCtxA < (int)mVideos.size()) {
            const VideoItem& v = mVideos[mPs3OptCtxA];
            auto fmtLen = [](double s) -> std::string {
                if (s <= 0.0) return "-";
                int t = (int)s, h = t / 3600, m = (t % 3600) / 60, sec = t % 60; char b[24];
                if (h > 0) snprintf(b, sizeof(b), "%d:%02d:%02d", h, m, sec);
                else snprintf(b, sizeof(b), "%d:%02d", m, sec);
                return b;
            };
            std::string res = (v.w && v.h) ? (std::to_string(v.w) + " x " + std::to_string(v.h)) : std::string("-");
            std::string vc = v.vcodec.empty() ? "-" : v.vcodec;
            std::string ac = v.acodec.empty() ? "-" : v.acodec;
            body.clear();
            auto row = [&](const char* label, const std::string& val) {
                char pad[20]; snprintf(pad, sizeof(pad), "%-15s", label);
                body += pad; body += val; body += "\n";
            };
            // File name + directory split from the absolute path.
            std::string fname = v.file, dir = "/";
            size_t slash = v.file.find_last_of('/');
            if (slash != std::string::npos) {
                fname = v.file.substr(slash + 1);
                dir = (slash == 0) ? std::string("/") : v.file.substr(0, slash);
            }
            row("Title", v.name);
            row("File Name", fname);
            row("Length", fmtLen(v.durationSec));
            row("Resolution", res);
            row("Video Codec", vc);
            row("Audio Codec", ac);
            row("File Type", vc);   // web filetype == vcodec for these
            row("Size", fmtFileSize(v.sz));
            row("Path", dir);
        }
        mPs3DlgOptions.clear(); mPs3DlgSwatch.clear();
        mPs3DlgKind = 0; mPs3DlgType = 0; mPs3DlgThemeKey = 0; mPs3DlgBinding = nullptr;
        mPs3DlgIllust = 0; mPs3DlgNotice.clear(); mPs3DlgRomInfo = false;
        mPs3DlgTitle = title; mPs3DlgBody = body;
        mPs3DlgSel = 0; mPs3DlgOrigSel = 0;
        mPs3DlgIconTex = 0; mPs3DlgIconNmap = 0; mPs3DlgIconR = mPs3DlgIconG = mPs3DlgIconB = 1.0f;
        mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgBlurValid = false;
        return;
    }
    if (act == "pgslidefolder") {   // slideshow the focused group folder
        std::vector<PhotoGroup> groups = photoGroups();
        if (mPs3OptCtxA >= 0 && mPs3OptCtxA < (int)groups.size() && !groups[mPs3OptCtxA].idx.empty())
            pvSlideshowStart(groups[mPs3OptCtxA].idx, 0, mPvSlideStyle);
        return;
    }
    if (act == "pgview" || act == "pgslidegrid") {   // open / slideshow the focused grid photo
        int vi = 0; for (size_t i = 0; i < mPhotoGridList.size(); i++)
            if (mPhotoGridList[i] == mPs3OptCtxA) { vi = (int)i; break; }
        if (act == "pgview") openPhotoViewer(mPhotoGridList, vi);
        else pvSlideshowStart(mPhotoGridList, vi, mPvSlideStyle);
        return;
    }
    if (act == "pgaddgrid") {   // add the focused grid photo to a playlist (chooser over the grid)
        if (mPs3OptCtxA >= 0 && mPs3OptCtxA < (int)mPhotos.size())
            pvOpenAddChooser(mPhotos[mPs3OptCtxA].file);
        return;
    }
    if (act == "delmulti")  { photoMultiOpen(0); return; }   // Delete Multiple checkbox screen
    if (act == "copymulti") { photoMultiOpen(1); return; }   // Copy Multiple checkbox screen
    if (act == "pgcopy")   { pvShowMsg("Copy completed.", 1100.0f); return; }    // grid photo (simulated)
    if (act == "pgdelete") { pvShowMsg("Delete completed.", 1100.0f); return; }  // grid photo (simulated)
    if (act == "pgprint")  { return; }   // no printer in this environment (web closes too)
    if (act == "pcopyfolder" || act == "pdelfolder") {   // folder Copy/Delete -> result dialog over the column
        mPs3DlgOptions.clear(); mPs3DlgSwatch.clear();
        mPs3DlgKind = 0; mPs3DlgType = 0; mPs3DlgThemeKey = 0; mPs3DlgBinding = nullptr;
        mPs3DlgIllust = 0; mPs3DlgNotice.clear();
        mPs3DlgTitle = ""; mPs3DlgBody = (act == "pdelfolder") ? "Delete completed." : "Copy completed.";
        mPs3DlgSel = 0; mPs3DlgOrigSel = 0;
        mPs3DlgIconTex = 0; mPs3DlgIconNmap = 0; mPs3DlgIconR = mPs3DlgIconG = mPs3DlgIconB = 1.0f;
        mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgBlurValid = false;
        return;
    }
    if (act == "photoinfo") {   // photo Information (File / Date taken / Image size / Size)
        std::string title = mPs3OptCtxLabel.empty() ? std::string("Information") : mPs3OptCtxLabel;
        std::string body = "No information is available.";
        if (mPs3OptCtxA >= 0 && mPs3OptCtxA < (int)mPhotos.size()) {
            const PhotoItem& p = mPhotos[mPs3OptCtxA];
            std::string dim = (p.w && p.h) ? (std::to_string(p.w) + " x " + std::to_string(p.h)) : std::string("-");
            char b[320];
            snprintf(b, sizeof(b), "File          %s\nDate taken    %s\nImage size    %s\nSize          %s",
                     p.name.c_str(), fmtPhotoDate(p.date).c_str(), dim.c_str(), fmtFileSize(p.sz).c_str());
            body = b;
        }
        mPs3DlgOptions.clear(); mPs3DlgSwatch.clear();
        mPs3DlgKind = 0; mPs3DlgType = 0; mPs3DlgThemeKey = 0; mPs3DlgBinding = nullptr;
        mPs3DlgIllust = 0; mPs3DlgNotice.clear();
        mPs3DlgTitle = title; mPs3DlgBody = body;
        mPs3DlgSel = 0; mPs3DlgOrigSel = 0;
        mPs3DlgIconTex = 0; mPs3DlgIconNmap = 0; mPs3DlgIconR = mPs3DlgIconG = mPs3DlgIconB = 1.0f;
        mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgBlurValid = false;
        return;
    }
    if (act == "photofolderinfo") {   // folder Information (Folder / Images / Size)
        std::vector<PhotoGroup> groups = photoGroups();
        std::string title = mPs3OptCtxLabel.empty() ? std::string("Information") : mPs3OptCtxLabel;
        std::string body = "No information is available.";
        if (mPs3OptCtxA >= 0 && mPs3OptCtxA < (int)groups.size()) {
            const PhotoGroup& g = groups[mPs3OptCtxA];
            int64_t total = 0; for (int i : g.idx) total += mPhotos[i].sz;
            char b[256];
            snprintf(b, sizeof(b), "Folder        %s\nImages        %d\nSize          %s",
                     g.name.c_str(), (int)g.idx.size(), fmtFileSize(total).c_str());
            body = b;
        }
        mPs3DlgOptions.clear(); mPs3DlgSwatch.clear();
        mPs3DlgKind = 0; mPs3DlgType = 0; mPs3DlgThemeKey = 0; mPs3DlgBinding = nullptr;
        mPs3DlgIllust = 0; mPs3DlgNotice.clear();
        mPs3DlgTitle = title; mPs3DlgBody = body;
        mPs3DlgSel = 0; mPs3DlgOrigSel = 0;
        mPs3DlgIconTex = 0; mPs3DlgIconNmap = 0; mPs3DlgIconR = mPs3DlgIconG = mPs3DlgIconB = 1.0f;
        mPs3DlgActive = true; mPs3DlgAnim = 0.0f; mPs3DlgBlurValid = false;
        return;
    }
}

void NanoMenu::renderXmbOpt() {
    if (!mPs3OptActive && !mPs3OptClosing) return;
    setGlyphAtlasAA(true);
    float dt = mFrameDt; if (dt < 0.0f) dt = 0.0f; if (dt > 0.1f) dt = 0.1f;
    float ap;
    if (mPs3OptActive) {
        mPs3OptClosing = false;
        mPs3OptAnim += (1.0f - mPs3OptAnim) * (1.0f - expf(-13.0f * dt));
        if (mPs3OptAnim > 0.999f) mPs3OptAnim = 1.0f;
        ap = mPs3OptAnim;
    } else {
        mPs3OptCloseAnim -= mPs3OptCloseAnim * (1.0f - expf(-13.0f * dt));
        if (mPs3OptCloseAnim < 0.02f) { mPs3OptCloseAnim = 0.0f; mPs3OptClosing = false; mPs3OptBlurValid = false; return; }
        ap = mPs3OptCloseAnim;
    }
    { ps3::LayoutParams lp; lp.panelW = mWidth; lp.panelH = mHeight; lp.uiScale = mPs3UiScale; ps3::layoutCompute(lp); }

    const float fb = ps3DlgFontBoost();
    const bool sp43 = ps3::LAYOUT_XC < 0.999f;
    // Submenu (side-panel) layout: when a submenu opens, the main column slides LEFT
    // by SUB_SHIFT and the submenu takes the original (rightmost) slot, the selected
    // sub-row aligned to the parent row (web optMenu side-swap).
    const float SUB_SHIFT = 330.0f;     // virtual-px the main column slides left (web)
    const float SP_ARROW_DX = 232.0f;   // ">" arrow column, virtual-px offset from the text x
    const bool subOpen = mPs3OptSubOpen && mPs3OptSel >= 0 && mPs3OptSel < (int)mPs3OptSubRows.size()
                         && !mPs3OptSubRows[mPs3OptSel].empty();
    // Bases in virtual (1920) space; the submenu shift is applied here (before XCP) so it
    // tracks the layout's x-scale, not the frame-fit scale.
    const float SP_PANEL_LEFT_BASE = sp43 ? 1056.0f : 1324.0f;
    const float SP_TEXT_BASE       = sp43 ? 1100.0f : 1340.0f;
    const float mainShift = subOpen ? SUB_SHIFT : 0.0f;
    const float SP_PANEL_LEFT = ps3::XCP(SP_PANEL_LEFT_BASE - mainShift);
    const float SP_TEXT_X     = ps3::XCP(SP_TEXT_BASE);   // submenu (right, unshifted) slot
    const float SP_ITEM_PITCH = 44.0f;
    const float SP_LIST_TOP_Y = 470.0f;

    float ease = ap * ap * (3.0f - 2.0f * ap);
    float xShiftV = (1.0f - ease) * ps3::XCP(37.0f);

    const float pLeftDev = ps3::devX(SP_PANEL_LEFT + xShiftV);
    const float pWDev    = (float)mWidth - pLeftDev;
    const float pTopDev  = ps3::gFrameY;
    const float pHDev    = ps3::gFrameH;

    // Frosted-glass backdrop behind the panel (only where the wave is the visible
    // background, like the theme chooser).
    const bool frost = mCurrentEffect == 22 && (!mOverlayMode || mOverlayWallpaper);
    if (frost) {
        bool due = !mPs3OptBlurValid || (mEffectTime - mPs3OptBlurT) >= 0.0667f;
        if (due && captureGlassFromWave()) { mPs3OptBlurValid = true; mPs3OptBlurT = mEffectTime; }
        if (mPs3OptBlurValid)
            drawFrostedGlass(pLeftDev, pTopDev, pWDev, pHDev, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, ap, /*waveSpace=*/true);
    }
    // Black scrim side panel: a smooth fade gradient, 50% black at its left edge to
    // fully transparent at the screen's right edge - the SAME style as the theme /
    // performance choosers (renderPs3Dialog kind 1), so the option menu matches the
    // rest of nano's side panels (user request 2026-06-15).
    const int kStrips = 64;
    for (int s = 0; s < kStrips; s++) {
        float u0 = (float)s / (float)kStrips, u1 = (float)(s + 1) / (float)kStrips;
        float uc = 0.5f * (u0 + u1);
        float al = (0.50f * (1.0f - uc)) * ap;
        drawQuad(pLeftDev + pWDev * u0, pTopDev, pWDev * (u1 - u0), pHDev,
                 0.0f, 0.0f, 0.0f, al);
    }

    float ss = ps3::devS(1.5f);
    float so[2] = { sDrmRotMat[2] * ss, sDrmRotMat[3] * ss };
    const float txDev = ps3::devX(ps3::XCP(SP_TEXT_BASE - mainShift) + xShiftV);
    int n = (int)mPs3OptLabels.size();
    float yV = SP_LIST_TOP_Y;          // running y (separators add a gap, not a full row)
    float parentYV = SP_LIST_TOP_Y;    // y of the open-submenu parent, for sub alignment
    for (int i = 0; i < n; i++) {
        if (i < (int)mPs3OptSep.size() && mPs3OptSep[i]) {
            float dyDev = ps3::devY(yV + SP_ITEM_PITCH * 0.10f);
            drawQuad(txDev, dyDev, ps3::devS(300.0f), ps3::devS(1.0f), 1.0f, 1.0f, 1.0f, 0.12f * ap);
            yV += SP_ITEM_PITCH * 0.55f;
            continue;
        }
        bool sel = (i == mPs3OptSel);
        if (sel) parentYV = yV;
        float cyDev = ps3::devY(yV);
        float fs = ps3::fontScale((sel ? 26.0f : 22.0f) * fb);
        float ty = cyDev - 0.45f * ps3::emPx(fs);
        const char* txt = trDyn(mPs3OptLabels[i].c_str());
        // While a submenu is open the parent column dims (web: parent ~0.5), the open
        // parent kept a touch brighter for context.
        float baseA = subOpen ? (sel ? 0.9f : 0.40f) : (sel ? 1.0f : 0.82f);
        float a2 = baseA * ap;
        drawText(txt, txDev + so[0], ty + so[1], fs, 0.0f, 0.0f, 0.0f, 0.35f * ap * (subOpen ? 0.5f : 1.0f));
        drawText(txt, txDev, ty, fs, 1.0f, 1.0f, 1.0f, a2);
        // START pill on the primary action row.
        if (i < (int)mPs3OptStart.size() && mPs3OptStart[i]) {
            float lblW = measureText(txt, fs);
            float pfs = ps3::fontScale(13.0f * fb);
            const char* pill = "START";
            float pw = measureText(pill, pfs);
            float padx = ps3::devS(8.0f), pady = ps3::devS(4.0f);
            float px = txDev + lblW + ps3::devS(14.0f);
            float ph = ps3::emPx(pfs) + pady * 2.0f;
            float py = cyDev - ph * 0.5f;
            drawQuad(px, py, pw + padx * 2.0f, ph, 1.0f, 1.0f, 1.0f, 0.22f * ap);
            float pty = cyDev - 0.42f * ps3::emPx(pfs);
            drawText(pill, px + padx, pty, pfs, 1.0f, 1.0f, 1.0f, 0.95f * ap);
        }
        // ">" arrow on rows that open a submenu (right-aligned at a fixed column).
        if (i < (int)mPs3OptHasSub.size() && mPs3OptHasSub[i]) {
            float afs = ps3::fontScale(20.0f * fb);
            float ax = ps3::devX(ps3::XCP(SP_TEXT_BASE - mainShift + SP_ARROW_DX) + xShiftV);
            float aty = cyDev - 0.45f * ps3::emPx(afs);
            drawText(">", ax + so[0], aty + so[1], afs, 0.0f, 0.0f, 0.0f, 0.30f * ap);
            drawText(">", ax, aty, afs, 1.0f, 1.0f, 1.0f, a2);
        }
        yV += SP_ITEM_PITCH;
    }

    // Open submenu: the right (original) slot, the selected sub-row aligned to its parent.
    if (subOpen) {
        const std::vector<Ps3OptSub>& sub = mPs3OptSubRows[mPs3OptSel];
        const float subTxDev = ps3::devX(SP_TEXT_X + xShiftV);
        float syV = parentYV - (float)mPs3OptSubSel * SP_ITEM_PITCH;
        for (int j = 0; j < (int)sub.size(); j++) {
            bool ssel = (j == mPs3OptSubSel);
            float cyDev = ps3::devY(syV);
            float fs = ps3::fontScale((ssel ? 26.0f : 22.0f) * fb);
            float ty = cyDev - 0.45f * ps3::emPx(fs);
            const char* txt = trDyn(sub[j].label.c_str());
            float a2 = (ssel ? 1.0f : 0.82f) * ap;
            drawText(txt, subTxDev + so[0], ty + so[1], fs, 0.0f, 0.0f, 0.0f, 0.35f * ap);
            drawText(txt, subTxDev, ty, fs, 1.0f, 1.0f, 1.0f, a2);
            syV += SP_ITEM_PITCH;
        }
    }
}

void NanoMenu::renderPs3Dialog() {
    if (!mPs3DlgActive && !mPs3DlgClosing) return;
    // Dialog pages (System Update, System Information, the network test, etc.) are
    // dense readable text, so anti-alias them too. renderPs3Xmb turned AA off
    // before dispatching here; turn it back on for the dialog body. The OSK, if it
    // opens over a dialog, forces it back off in renderOsk so the keys stay crisp.
    setGlyphAtlasAA(true);
    // Internet Connection Test: pull the latest progressive results published by
    // the background test thread into the dialog body (main thread owns mPs3DlgBody).
    if (mPs3NetTestLive) {
        std::lock_guard<std::mutex> lk(mPs3NetTestMutex);
        mPs3DlgBody = mPs3NetTestBody;
    }
    float dt = mFrameDt; if (dt < 0.0f) dt = 0.0f; if (dt > 0.1f) dt = 0.1f;
    float ap;
    if (mPs3DlgActive) {
        // Opening / open: a freshly-opened dialog supersedes any in-flight close.
        mPs3DlgClosing = false;
        mPs3DlgAnim += (1.0f - mPs3DlgAnim) * (1.0f - expf(-13.0f * dt));
        if (mPs3DlgAnim > 0.999f) mPs3DlgAnim = 1.0f;
        ap = mPs3DlgAnim;
    } else {
        // Closing (side-panel only): fade + reverse-settle back out, then stop.
        mPs3DlgCloseAnim -= mPs3DlgCloseAnim * (1.0f - expf(-13.0f * dt));
        if (mPs3DlgCloseAnim < 0.02f) { mPs3DlgCloseAnim = 0.0f; mPs3DlgClosing = false; mPs3DlgBlurValid = false; return; }
        ap = mPs3DlgCloseAnim;
    }
    { ps3::LayoutParams lp; lp.panelW = mWidth; lp.panelH = mHeight; lp.uiScale = mPs3UiScale; ps3::layoutCompute(lp); }

    // Fullscreen message dialogs (System Update, ...) sit on a blurred backdrop
    // that keeps ANIMATING (re-captured every frame, like the web). Side-panel
    // choosers do NOT blur - the XMB stays visible and the LIVE background colour
    // shows through so the Colour/Day-Night preview is seen behind the panel.
    if (mPs3DlgKind != 1) {
        // A FULLSCREEN dialog replaces the screen, so it needs a focus blur of
        // whatever is behind it for ANY wallpaper, or the live menu clashes through.
        // - Wave wallpaper: capture its cheap work-texture (~30Hz, animated, logical
        //   orientation -> waveSpace=true).
        // - Any other wallpaper: capture the framebuffer ONCE (the live effect + the
        //   menu, already drawn this frame) and hold it (screen orientation ->
        //   waveSpace=false). captureGlass forces a tile resolve, so do it once per
        //   open, not per frame.
        // Never blur in the overlay scrim (the dimmed live app must show through).
        const bool frostHome = !mOverlayMode || mOverlayWallpaper;
        const bool waveSpace = (mCurrentEffect == 22);
        float blurCad = ps3bg::themeFading() ? 0.0f : 0.0667f;
        bool due = !mPs3DlgBlurValid || (waveSpace && (mEffectTime - mPs3DlgBlurT) >= blurCad);
        if (due && frostHome) {
            bool got = waveSpace ? captureGlassFromWave()
                                 : captureGlass(0.0f, 0.0f, (float)mWidth, (float)mHeight);
            if (got) { mPs3DlgBlurValid = true; mPs3DlgBlurT = mEffectTime; }
        }
        if (mPs3DlgBlurValid && frostHome)
            drawFrostedGlass(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, ap, waveSpace);  // pure blur, no darkening
    }

    float ss = ps3::devS(1.5f);
    float so[2] = { sDrmRotMat[2] * ss, sDrmRotMat[3] * ss };

    if (mPs3DlgKind == 1) {
        // ---- side-panel chooser: 1:1 port of the web xmb drawSidePanel() ----
        // Black fade-gradient scrim panel at a fixed position, fading IN on open and
        // OUT on dismiss, with a small ~37px settle (NOT a full-width slide). No blur
        // backdrop (gated above by mPs3DlgKind != 1) so the live XMB / per-month
        // gradient shows through for the Colour / Day-Night live preview. The XMB
        // chrome behind it provides the context (the highlighted submenu row), so
        // there is no panel title - exactly like the firmware chooser.
        const float fb = ps3DlgFontBoost();
        const bool sp43 = ps3::LAYOUT_XC < 0.999f;
        // Web design-space constants (1920x1080), measured from RPCS3 firmware
        // captures (side_panel_color_chooser_REAL.png). 4:3 uses its own wider bases.
        const float SP_PANEL_LEFT  = ps3::XCP(sp43 ? 1056.0f : 1324.0f);
        const float SP_TEXT_X      = ps3::XCP(sp43 ? 1100.0f : 1340.0f);
        const float SP_ITEM_PITCH  = 40.0f;   // virtual px between items
        const float SP_SWATCH_SIZE = 27.0f;   // colour swatch square (virtual px)
        const float SP_LIST_TOP_Y  = 510.0f;  // middle-y of the first item
        const float SP_VISIBLE_BOT = ps3::VH - 60.0f;

        float ease = ap * ap * (3.0f - 2.0f * ap);
        float xShiftV = (1.0f - ease) * ps3::XCP(37.0f);

        // (1) Panel background: a smooth black fade gradient, 50% at its darkest.
        //     drawQuad has no gradient mode, so tile it with adjacent vertical strips
        //     (each stop's alpha scaled by the panel fade ap). One linear ramp:
        //     darkest at the LEFT (behind the labels) getting gradually lighter to
        //     fully transparent at the screen's right edge, so the fade is even.
        struct GStop { float p, r, g, b, a; };
        static const GStop kStops[] = {
            {0.000f, 0.0f,0.0f,0.0f, 0.50f},   // darkest: 50% at the panel's left edge
            {1.000f, 0.0f,0.0f,0.0f, 0.00f},   // lightest: fully transparent at the screen's right edge
        };
        const int kStopN = (int)(sizeof(kStops) / sizeof(kStops[0]));
        const float pLeftDev = ps3::devX(SP_PANEL_LEFT + xShiftV);
        const float pWDev    = (float)mWidth - pLeftDev;   // fade extends to the screen's right edge
        const float pTopDev  = ps3::gFrameY;
        const float pHDev    = ps3::gFrameH;
        // (0) Cheap background blur behind the panel: reuse the wave frosted-glass
        //     (the 1/8-res Gaussian already used by fullscreen dialogs) but draw it
        //     ONLY over the panel region, so content under the scrim reads as frosted.
        //     Low cost: no extra render pass, ~30Hz re-capture of the existing wave
        //     work-tex. Only when the wave is the visible background (home XMB, or the
        //     wave wallpaper); in the in-game overlay the live app is already blurred
        //     by SurfaceFlinger behind the scrim, so we skip it there.
        {
            const bool spFrost = mCurrentEffect == 22 && (!mOverlayMode || mOverlayWallpaper);
            if (spFrost) {
                bool due = !mPs3DlgBlurValid || (mEffectTime - mPs3DlgBlurT) >= 0.0667f;
                if (due && captureGlassFromWave()) { mPs3DlgBlurValid = true; mPs3DlgBlurT = mEffectTime; }
                if (mPs3DlgBlurValid)
                    drawFrostedGlass(pLeftDev, pTopDev, pWDev, pHDev, 0.0f,
                                     1.0f, 1.0f, 1.0f, 1.0f, ap, /*waveSpace=*/true);
            }
        }
        const int   kStrips  = 64;
        for (int s = 0; s < kStrips; s++) {
            float u0 = (float)s / (float)kStrips;
            float u1 = (float)(s + 1) / (float)kStrips;
            float uc = 0.5f * (u0 + u1);
            int gi = 0; while (gi < kStopN - 2 && kStops[gi + 1].p < uc) gi++;
            const GStop& s0 = kStops[gi]; const GStop& s1 = kStops[gi + 1];
            float t = (s1.p > s0.p) ? (uc - s0.p) / (s1.p - s0.p) : 0.0f;
            if (t < 0.0f) t = 0.0f; else if (t > 1.0f) t = 1.0f;
            float r  = s0.r + (s1.r - s0.r) * t;
            float g  = s0.g + (s1.g - s0.g) * t;
            float b  = s0.b + (s1.b - s0.b) * t;
            float al = (s0.a + (s1.a - s0.a) * t) * ap;
            drawQuad(pLeftDev + pWDev * u0, pTopDev, pWDev * (u1 - u0), pHDev, r, g, b, al);
        }

      if (mPs3DlgSlider) {
        // (2a) Numeric slider: a horizontal track + fill + knob + the live value,
        //      stepped by Left/Right. Shown instead of the option list for sliders.
        const float txDev = ps3::devX(SP_TEXT_X + xShiftV);
        std::string vs = ps3FormatNum(mPs3DlgSldVal, mPs3DlgSldScale);
        float vfs = ps3::fontScale(30.0f * fb);
        float vy  = ps3::devY(SP_LIST_TOP_Y) - 0.45f * ps3::emPx(vfs);
        drawText(vs.c_str(), txDev + so[0], vy + so[1], vfs, 0.0f, 0.0f, 0.0f, 0.35f * ap);
        drawText(vs.c_str(), txDev, vy, vfs, 1.0f, 1.0f, 1.0f, ap);
        // Track in device space: from the text x to a margin off the screen's right
        // edge, centred on the row below the value.
        float trkY = ps3::devY(SP_LIST_TOP_Y + 56.0f);
        float trkH = ps3::devS(6.0f);
        float trkL = txDev;
        float trkR = (float)mWidth - ps3::devS(40.0f);
        if (trkR < trkL + ps3::devS(40.0f)) trkR = trkL + ps3::devS(40.0f);
        float trkW = trkR - trkL;
        float frac = (mPs3DlgSldMax > mPs3DlgSldMin)
                     ? (mPs3DlgSldVal - mPs3DlgSldMin) / (mPs3DlgSldMax - mPs3DlgSldMin) : 0.0f;
        if (frac < 0.0f) frac = 0.0f; if (frac > 1.0f) frac = 1.0f;
        drawQuad(trkL, trkY - trkH * 0.5f, trkW, trkH, 1.0f, 1.0f, 1.0f, 0.25f * ap);          // track
        drawQuad(trkL, trkY - trkH * 0.5f, trkW * frac, trkH, 1.0f, 1.0f, 1.0f, 0.95f * ap);    // fill
        float kb = ps3::devS(16.0f);
        drawQuad(trkL + trkW * frac - kb * 0.5f, trkY - kb * 0.5f, kb, kb, 1.0f, 1.0f, 1.0f, ap); // knob
        // min / max end labels under the track
        float efs = ps3::fontScale(15.0f * fb);
        float ey  = ps3::devY(SP_LIST_TOP_Y + 84.0f) - 0.45f * ps3::emPx(efs);
        std::string mns = ps3FormatNum(mPs3DlgSldMin, mPs3DlgSldScale);
        std::string mxs = ps3FormatNum(mPs3DlgSldMax, mPs3DlgSldScale);
        drawText(mns.c_str(), trkL, ey, efs, 1.0f, 1.0f, 1.0f, 0.55f * ap);
        float mxw = measureText(mxs.c_str(), efs);
        drawText(mxs.c_str(), trkR - mxw, ey, efs, 1.0f, 1.0f, 1.0f, 0.55f * ap);
      } else {
        // (2) Visible window. The list is ANCHORED at SP_LIST_TOP_Y; the selection
        //     only changes the per-item font size/weight, it does NOT recentre the
        //     list. Scroll the window only when the list is longer than what fits.
        int n = (int)mPs3DlgOptions.size();
        int maxFit = (int)((SP_VISIBLE_BOT - SP_LIST_TOP_Y) / SP_ITEM_PITCH);
        if (maxFit < 1) maxFit = 1;
        int firstVis, lastVis;
        if (n <= maxFit)                  { firstVis = 0; lastVis = n - 1; }
        else if (mPs3DlgSel < maxFit - 1) { firstVis = 0; lastVis = maxFit - 1; }
        else if (mPs3DlgSel >= n - 1)     { lastVis = n - 1; firstVis = lastVis - (maxFit - 1); }
        else {
            firstVis = mPs3DlgSel - (int)((float)maxFit * 0.66f);
            if (firstVis < 0) firstVis = 0;
            lastVis = firstVis + maxFit - 1;
            if (lastVis >= n) { lastVis = n - 1; firstVis = lastVis - (maxFit - 1); }
        }

        // (3) Items. Colour chooser (any swatch present): non-selected items render
        //     as the swatch square only, the selected item as its text label. Other
        //     choosers: all text labels. Selected = larger font + pure white; others
        //     = 90% white. Both get a soft 1px black drop shadow (so[] is the
        //     rotation-aware 1.5px offset computed above).
        bool isColorChooser = false;
        for (size_t i = 0; i < mPs3DlgSwatch.size(); i++) if (mPs3DlgSwatch[i] >= 0) { isColorChooser = true; break; }
        const float txDev = ps3::devX(SP_TEXT_X + xShiftV);
        for (int i = firstVis; i <= lastVis; i++) {
            bool sel = (i == mPs3DlgSel);
            float cyDev = ps3::devY(SP_LIST_TOP_Y + (float)(i - firstVis) * SP_ITEM_PITCH);
            if (isColorChooser && !sel && i < (int)mPs3DlgSwatch.size() && mPs3DlgSwatch[i] >= 0) {
                int ci = mPs3DlgSwatch[i];
                float swDev = ps3::devS(SP_SWATCH_SIZE);
                drawQuad(txDev, cyDev - swDev * 0.5f, swDev, swDev,
                         kPs3ColorOpts[ci].sr, kPs3ColorOpts[ci].sg, kPs3ColorOpts[ci].sb, ap);
                continue;
            }
            float fs = ps3::fontScale((sel ? 26.0f : 22.0f) * fb);
            float ty = cyDev - 0.45f * ps3::emPx(fs);   // web textBaseline='middle'
            const char* optTxt = trDyn(mPs3DlgOptions[i].c_str());
            drawText(optTxt, txDev + so[0], ty + so[1], fs, 0.0f, 0.0f, 0.0f, 0.35f * ap);
            float ta = (sel ? 1.0f : 0.90f) * ap;
            drawText(optTxt, txDev, ty, fs, 1.0f, 1.0f, 1.0f, ta);
        }

        // (4) Up/down scroll arrows at the text x when items scroll off-window.
        float arrFs = ps3::fontScale(20.0f * fb);
        if (firstVis > 0) {
            float ay = ps3::devY(SP_LIST_TOP_Y - SP_ITEM_PITCH) - 0.45f * ps3::emPx(arrFs);
            drawText("\xE2\x96\xB2", txDev, ay, arrFs, 1.0f, 1.0f, 1.0f, 0.85f * ap);
        }
        if (lastVis < n - 1) {
            float ay = ps3::devY(SP_LIST_TOP_Y + (float)(lastVis - firstVis + 1) * SP_ITEM_PITCH) - 0.45f * ps3::emPx(arrFs);
            drawText("\xE2\x96\xBC", txDev, ay, arrFs, 1.0f, 1.0f, 1.0f, 0.85f * ap);
        }
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
        const float fb = ps3DlgFontBoost();
        auto FS = [&](float px) { return S * px * fb / 16.0f; };
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

        // ---- ROM Information faint fanart backdrop (behind the title/dividers) ----
        // Contain-fit the scraped fanart inside the content band (never overflowing
        // past the dividers) and draw it dimmed so it reads as a backdrop, not chrome.
        // The 50% page dim above already darkens whatever is behind it.
        if (mPs3DlgRomInfo && mPs3DlgFanTex && mPs3DlgFanW > 0 && mPs3DlgFanH > 0) {
            float rx = ps3::gFrameX, rw = ps3::gFrameW;
            float ry = Y(innerTop), rh = Y(innerBot) - Y(innerTop);
            float far_ = (float)mPs3DlgFanW / (float)mPs3DlgFanH;
            float dw = rw, dh = dw / far_;
            if (dh > rh) { dh = rh; dw = dh * far_; }   // contain: fit inside the band
            float dx = rx + (rw - dw) * 0.5f, dy = ry + (rh - dh) * 0.5f;
            drawIconTex(mPs3DlgFanTex, dx, dy, dw, dh, 1.0f, 1.0f, 1.0f, 0.20f * ap);
        }

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
        // Translate the whole body BEFORE wrapping (the static dialog-template
        // bodies are translation keys; dynamic bodies - net status SSID/IP, NTP
        // results - have no key and pass through unchanged).
        std::string dlgBody = trDyn(mPs3DlgBody.c_str());
        int n = (int)mPs3DlgOptions.size();
        if (mPs3DlgRomInfo) {                   // rich ROM Information (cover + metadata + synopsis)
            // FIT-AWARE: every horizontal position uses XC() (= S*XCF(vx)+offX) and
            // every horizontal span compresses by LAYOUT_FIT, so the cover, metadata
            // column and wrapped description stay inside the visible frame on ANY
            // aspect (4:3/1:1/16:9/portrait). On 16:9 LAYOUT_FIT==1 so XC()==X() and
            // this is pixel-identical to before. Raw X() here pushed content offscreen
            // on the 4:3 Brick panel (LAYOUT_FIT=0.75). Vertical Y()/heights are uniform.
            const float fit = ps3::LAYOUT_FIT;
            // ---- left: cover, contain-fit in a (compressed) box with a thin frame ----
            // Reserve the cover slot when a cover was REQUESTED (pending path set), not
            // only when it has finished decoding, so the metadata column does not jump
            // right when the async cover lands a frame or two after the page opens.
            bool hasCover = !mPs3DlgPendingBox.empty();
            if (hasCover && mPs3DlgBoxTex != 0 && mPs3DlgBoxW > 0 && mPs3DlgBoxH > 0) {
                float bxL = 130.0f, bxT = innerTop + 50.0f, bxW = 470.0f, bxH = (innerBot - 50.0f) - bxT;
                float boxWdev = DS(bxW * fit), boxHdev = DS(bxH);
                float car = (float)mPs3DlgBoxW / (float)mPs3DlgBoxH;
                float cwd = boxWdev, chd = cwd / car;
                if (chd > boxHdev) { chd = boxHdev; cwd = chd * car; }
                float cx = XC(bxL) + (boxWdev - cwd) * 0.5f, cy = Y(bxT) + (boxHdev - chd) * 0.5f;
                drawQuad(cx - DS(2.0f), cy - DS(2.0f), cwd + DS(4.0f), chd + DS(4.0f), 1.0f, 1.0f, 1.0f, 0.25f * ap); // frame
                drawIconTex(mPs3DlgBoxTex, cx, cy, cwd, chd, 1.0f, 1.0f, 1.0f, ap);
            }
            // ---- right: metadata rows, synopsis, bottom-anchored file facts ----
            float metaX  = hasCover ? 660.0f : 140.0f;
            float valX   = metaX + 230.0f;
            float metaR  = VW - 120.0f;
            float vy = innerTop + 58.0f;
            auto mrow = [&](const char* label, const std::string& val, float lum) {
                if (val.empty()) return;
                ps3DlgText(label, XC(metaX), Y(vy), FS(20.0f), 0.60f, 0.65f, 0.72f, ap, 0);
                ps3DlgText(val.c_str(), XC(valX), Y(vy), FS(21.0f), lum, lum, lum, ap, 0);
                vy += 38.0f;
            };
            mrow("Genre",     mPs3RomInfoGenre,   0.95f);
            mrow("Players",   mPs3RomInfoPlayers, 0.95f);
            mrow("Rating",    mPs3RomInfoRating,  0.95f);
            mrow("Released",  mPs3RomInfoDate,    0.95f);
            mrow("Developer", mPs3RomInfoDev,     0.95f);
            mrow("Publisher", mPs3RomInfoPub,     0.95f);
            const char* coreLabel = mPs3RomInfoCoreIsApp ? "App" : "Core";

            // File facts pinned to the bottom of the band (dimmer), with a divider.
            struct FF { const char* l; const std::string* v; };
            FF ffAll[] = { {"File", &mPs3RomInfoFileName}, {"Size", &mPs3RomInfoSize},
                           {coreLabel, &mPs3RomInfoCore},  {"System", &mPs3RomInfoSystem},
                           {"Path", &mPs3RomInfoDir} };
            int ffN = 0; for (auto& f : ffAll) if (!f.v->empty()) ffN++;
            float ffRowH = 32.0f;
            float ffTop  = innerBot - 26.0f - (float)ffN * ffRowH;

            // Synopsis fills the gap between the rows and the file-facts block. Long
            // descriptions scroll with Up/Down (mPs3RomInfoScroll = first visible line).
            if (!mPs3RomInfoSyn.empty()) {
                float synTop = vy + 6.0f;
                ps3DlgText("Description", XC(metaX), Y(synTop), FS(20.0f), 0.60f, 0.65f, 0.72f, ap, 0);
                float synFs = FS(19.0f), lineH = 27.0f;     // lineH in virtual px
                float wdev = XC(metaR) - XC(metaX);          // fit-compressed wrap width
                auto wrapW = [&](const std::string& text, float fs) {
                    std::vector<std::string> out; std::string line, word;
                    auto commit = [&]() {
                        if (word.empty()) return;
                        std::string trial = line.empty() ? word : line + " " + word;
                        if (!line.empty() && measureText(trial.c_str(), fs) > wdev) { out.push_back(line); line = word; }
                        else line = trial;
                        word.clear();
                    };
                    for (const char* q = text.c_str(); ; ++q) {
                        if (*q == ' ' || *q == '\n' || *q == '\0') { commit(); if (*q == '\n') out.push_back(""); if (*q == '\0') break; }
                        else word.push_back(*q);
                    }
                    if (!line.empty()) out.push_back(line);
                    return out;
                };
                std::vector<std::string> lines = wrapW(mPs3RomInfoSyn, synFs);   // full wrap, once
                int total = (int)lines.size();
                float firstY = synTop + 34.0f;               // virtual y of the first line
                float synBot = ffTop - 18.0f;                // virtual y limit (above the facts)
                int maxVis = (int)((synBot - firstY) / lineH) + 1;   // lines that fit
                if (maxVis < 1) maxVis = 1;
                int maxScroll = total - maxVis; if (maxScroll < 0) maxScroll = 0;
                if (mPs3RomInfoScroll < 0) mPs3RomInfoScroll = 0;
                if (mPs3RomInfoScroll > maxScroll) mPs3RomInfoScroll = maxScroll;   // clamp (input only +'d)
                int first = mPs3RomInfoScroll;
                int last  = first + maxVis; if (last > total) last = total;
                float ly = firstY;
                for (int i = first; i < last; i++) {
                    if (!lines[i].empty()) ps3DlgText(lines[i].c_str(), XC(metaX), Y(ly), synFs, 0.92f, 0.92f, 0.92f, ap, 0);
                    ly += lineH;
                }
                // Scroll-more chevrons at the right edge of the description column.
                float chX = XC(metaR) - DS(14.0f);
                if (first > 0)     ps3DlgText("\xE2\x96\xB2", chX, Y(firstY), FS(15.0f), 0.85f, 0.88f, 0.92f, ap, 2);
                if (last < total)  ps3DlgText("\xE2\x96\xBC", chX, Y(synBot), FS(15.0f), 0.85f, 0.88f, 0.92f, ap, 2);
            }

            if (ffN > 0) {
                float dy = ffTop - 16.0f;
                drawQuad(XC(metaX), Y(dy), XC(metaR) - XC(metaX), fmaxf(1.0f, DS(1.0f)), 1.0f, 1.0f, 1.0f, 0.18f * ap);
                float fy = ffTop;
                for (auto& f : ffAll) {
                    if (f.v->empty()) continue;
                    ps3DlgText(f.l, XC(metaX), Y(fy), FS(17.0f), 0.55f, 0.60f, 0.66f, ap, 0);
                    ps3DlgText(f.v->c_str(), XC(valX), Y(fy), FS(17.0f), 0.80f, 0.80f, 0.80f, ap, 0);
                    fy += ffRowH;
                }
            }
        } else if (mPs3DlgType == 0) {          // info
            float centerCY = (innerTop + innerBot) * 0.5f;
            if (mPs3DlgIllust) { ps3DlgIllustration(mPs3DlgIllust, XC(VW * 0.5f), Y(innerTop + 230.0f), DS(280.0f), ap); centerCY = innerTop + 460.0f; }
            float fs = FS(26.0f), lh = DS(36.0f);
            std::vector<std::string> lines = wrap(dlgBody, fs);
            float ty = Y(centerCY) - (float)((int)lines.size() - 1) * lh * 0.5f;
            for (auto& ln : lines) { if (!ln.empty()) ps3DlgText(ln.c_str(), XC(VW * 0.5f), ty, fs, 0.95f, 0.95f, 0.95f, ap, 1); ty += lh; }
        } else if (mPs3DlgType == 1) {          // chooser
            float fs = FS(24.0f);
            std::vector<std::string> bodyLines = wrap(dlgBody, fs);
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
            std::vector<std::string> bodyLines = wrap(dlgBody, fs);
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
            std::vector<std::string> bodyLines = wrap(dlgBody, fs);
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
    // The Internet Connection / Manage Bluetooth wizard pages are readable text
    // chrome like the dialogs, so anti-alias them too. The OSK (password / text
    // fields) forces AA back off in renderOsk so the keys stay crisp.
    setGlyphAtlasAA(true);
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
        const bool frostBg = mCurrentEffect == 22 && (!mOverlayMode || mOverlayWallpaper);
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
    const float fb = ps3DlgFontBoost();
    auto FS = [&](float px) { return S * px * fb / 16.0f; };
    const float slidePx = S * mPs3WizSlide;            // body content slide offset
    const float maxW = DS((VW - 400.0f) * ps3::LAYOUT_FIT);

    auto wrap = [&](const std::string& bodyIn, float fs) {
        // Translate the whole body (a static WizScreen body is a translation key;
        // dynamic bodies - device names, status text - pass straight through).
        std::string body = trDyn(bodyIn.c_str());
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
            const char* searchTxt = trDyn("Search");
            float sb = S * fb;   // small-panel boost, matching ps3DlgHint
            float fs = sb * 22.0f / 16.0f, glyphR = sb * 12.0f, gap = sb * 12.0f;
            float lw = fmaxf(S * 2.0f, 1.5f), tw = measureText(searchTxt, fs);
            float groupW = glyphR * 2.0f + gap + tw, left = s3 - groupW * 0.5f, gcx = left + glyphR;
            float h = glyphR * 0.78f;
            drawQuad(gcx - h, hintY - h, 2.0f * h, lw, 1, 1, 1, 0.95f * ap);          // top
            drawQuad(gcx - h, hintY + h - lw, 2.0f * h, lw, 1, 1, 1, 0.95f * ap);     // bottom
            drawQuad(gcx - h, hintY - h, lw, 2.0f * h, 1, 1, 1, 0.95f * ap);          // left
            drawQuad(gcx + h - lw, hintY - h, lw, 2.0f * h, 1, 1, 1, 0.95f * ap);     // right
            drawText(searchTxt, left + glyphR * 2.0f + gap, hintY - 0.45f * 16.0f * fs, fs, 1, 1, 1, 0.95f * ap);
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

// ---------------------------------------------------------------------------
// System Language picker. The XMB Settings -> System Settings -> System Language
// entry reuses the first-run setup wizard's fullscreen language list (same
// renderLanguageList chrome) with live locale preview as the cursor moves.
// ---------------------------------------------------------------------------
void NanoMenu::openLanguagePicker() {
    mLangSelected   = (int)nanoGetLocale();  // start on the active UI language
    mLangSelOnOpen  = mLangSelected;         // remember it so cancel can revert
    mPs3LangActive  = true;
    mPs3LangAnim    = 0.0f;                   // play the open transition (fade + frost)
    mPs3DlgBlurValid = false;                 // force a fresh frosted-wave capture
}

void NanoMenu::closeLanguagePicker(bool apply) {
    if (apply) {
        nanoSetLocale((NanoLocale)mLangSelected);   // commit the previewed locale
        nanoApplyLocaleToSystem();                  // persist.sys.locale = <code>-<region>
        ALOGI("ps3menu: system language set to %s",
              nanoGetLocaleInfo(nanoGetLocale()).englishName);
    } else {
        // Cancel: undo the live preview (restore the locale that was active on open)
        // so the XMB returns to its previous language and nothing is persisted.
        nanoSetLocale((NanoLocale)mLangSelOnOpen);
        mLangSelected = mLangSelOnOpen;
    }
    mPs3LangActive = false;
    mPs3DlgBlurValid = false;   // drop the frosted capture (shared blur scratch)
    // Snap the menu animation to settled so the item list draws immediately when
    // the picker closes (mirrors closeTimezoneGlobe; defensive against mEffectTime
    // wraps while the picker was open).
    mPs3ItemAnimStart = -1.0f;
    mPs3SubAnimStart  = -1.0f;
    mPs3CatAnimActive = false;
    mPs3AnimItem = (float)ps3CurSel();
}

void NanoMenu::langPickerNav(int dir) {
    int next = mLangSelected + dir;
    if (next < 0) next = 0;
    if (next > LOCALE_COUNT - 1) next = LOCALE_COUNT - 1;
    mLangSelected = next;   // renderLanguagePicker applies the live preview next frame
}

void NanoMenu::renderLanguagePicker() {
    nanoSetLocale((NanoLocale)mLangSelected);   // live locale preview as you scroll
    setGlyphAtlasAA(true);                       // crisp list text (renderPs3Xmb turned it off)
    // Open transition: ease the panel + frosted backdrop in exactly like the
    // fullscreen System Update dialog (renderPs3Dialog: exp ease + per-frame
    // captureGlassFromWave + drawFrostedGlass, all fading with the anim).
    float dt = mFrameDt; if (dt < 0.0f) dt = 0.0f; if (dt > 0.1f) dt = 0.1f;
    mPs3LangAnim += (1.0f - mPs3LangAnim) * (1.0f - expf(-13.0f * dt));
    if (mPs3LangAnim > 0.999f) mPs3LangAnim = 1.0f;
    float ap = mPs3LangAnim;
    { ps3::LayoutParams lp; lp.panelW = mWidth; lp.panelH = mHeight;
      lp.uiScale = mPs3UiScale; ps3::layoutCompute(lp); }
    // Frosted-wave backdrop, re-captured each frame, fading in with the panel.
    float blurCad = ps3bg::themeFading() ? 0.0f : 0.0667f;
    bool due = !mPs3DlgBlurValid || (mEffectTime - mPs3DlgBlurT) >= blurCad;
    const bool frostBg = mCurrentEffect == 22 && (!mOverlayMode || mOverlayWallpaper);
    if (due && frostBg && captureGlassFromWave()) { mPs3DlgBlurValid = true; mPs3DlgBlurT = mEffectTime; }
    if (mPs3DlgBlurValid && frostBg)
        drawFrostedGlass(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f,
                         1.0f, 1.0f, 1.0f, 1.0f, ap, /*waveSpace=*/true);
    renderLanguageList("System Language", ap);
}

// Shared fullscreen language-list chrome used by BOTH the setup wizard step and
// the XMB System Language picker: PS3 dialog layout (left title + dividers, a
// centred scrollable list of native language names, up/down chevrons, X-Enter /
// O-Cancel footer). The native names render verbatim (translate=false) so the
// English row stays "English", not the locale's word for it.
void NanoMenu::renderLanguageList(const char* title, float alpha) {
    { ps3::LayoutParams lp; lp.panelW = mWidth; lp.panelH = mHeight;
      lp.uiScale = mPs3UiScale; ps3::layoutCompute(lp); }
    float ui = mPs3UiScale; if (ui < 0.5f) ui = 0.5f; if (ui > 2.0f) ui = 2.0f;
    const float S = ps3::gScale / ui;
    const float offX = ps3::gFrameX + (ps3::gFrameW - S * ps3::XCF(ps3::VW)) * 0.5f;
    const float offY = ps3::gFrameY + ps3::gFrameH * 0.5f - S * (ps3::VH * 0.5f);
    auto X  = [&](float vx) { return S * vx + offX; };
    auto XC = [&](float vx) { return S * ps3::XCF(vx) + offX; };
    auto Y  = [&](float vy) { return S * vy + offY; };
    auto DS = [&](float v)  { return S * v; };
    const float fb = ps3DlgFontBoost();
    auto FS = [&](float px) { return S * px * fb / 16.0f; };
    const float VW = ps3::VW;
    const float innerTop = 199.0f, innerBot = 880.0f;

    int   savedMode  = mTextOutlineMode;
    float savedRatio = mTextOutlineRatio;
    mTextOutlineMode = 1; mTextOutlineRatio = 0.5f;

    // Header: title + dividers.
    ps3DlgText(title, X(160.0f), Y(187.0f), FS(28.0f), 1.0f, 1.0f, 1.0f, alpha, 0);
    float divLw = fmaxf(1.0f, DS(1.0f));
    drawQuad(ps3::gFrameX, Y(innerTop), ps3::gFrameW, divLw, 1, 1, 1, 0.55f * alpha);
    drawQuad(ps3::gFrameX, Y(innerBot), ps3::gFrameW, divLw, 1, 1, 1, 0.55f * alpha);

    // Centred scrollable language list (selected row glows like a dialog option).
    const int n = LOCALE_COUNT;
    const float optTopV = innerTop + 80.0f, optSpacingV = 50.0f;
    float availH = innerBot - optTopV - 40.0f;
    int visibleCount = (int)(availH / optSpacingV); if (visibleCount < 3) visibleCount = 3;
    int firstVis = 0, lastVis = n - 1;
    if (n > visibleCount) {
        firstVis = mLangSelected - visibleCount / 2;
        if (firstVis < 0) firstVis = 0;
        if (firstVis > n - visibleCount) firstVis = n - visibleCount;
        lastVis = firstVis + visibleCount - 1;
    }
    for (int i = firstVis; i <= lastVis; i++) {
        const LocaleInfo& info = nanoGetLocaleInfo((NanoLocale)i);
        ps3DlgOption(info.nativeName, XC(VW * 0.5f),
                     Y(optTopV + (i - firstVis) * optSpacingV),
                     i == mLangSelected, false, alpha, S, /*translate=*/false);
    }
    if (firstVis > 0)
        ps3DlgText("\xE2\x96\xB2", XC(VW * 0.5f), Y(optTopV - 18.0f),
                   FS(20.0f), 1, 1, 1, 0.5f * alpha, 1);
    if (lastVis < n - 1)
        ps3DlgText("\xE2\x96\xBC", XC(VW * 0.5f),
                   Y(optTopV + visibleCount * optSpacingV + 6.0f),
                   FS(20.0f), 1, 1, 1, 0.5f * alpha, 1);

    // Footer hints (X Enter / O Cancel).
    float hintY = Y(909.0f);
    ps3DlgHint(XC(VW * 0.401f), true,  "Enter",  hintY, S, alpha);
    ps3DlgHint(XC(VW * 0.629f), false, "Cancel", hintY, S, alpha);

    mTextOutlineMode = savedMode;
    mTextOutlineRatio = savedRatio;
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
    const float fb = ps3DlgFontBoost();
    auto FS = [&](float px) { return S * px * fb / 16.0f; };

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
