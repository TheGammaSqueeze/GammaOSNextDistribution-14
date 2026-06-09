/*
 * Copyright (C) 2026 GammaOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

// Game Systems editor: the icon grid picker. A filterable, glass-rendered grid
// over the bundled 849-icon RetroArch monochrome set. Thumbnails are decoded,
// downscaled to 64x64, beveled and glass-relit on demand, with a rolling LRU so
// the full set is never resident (Section 13 of the dynamic-systems plan).

#define LOG_TAG "GammaOSNano"

#include "NanoMenu.h"
#include "NanoMenuPS3.h"
#include "NanoMenuPS3Bg.h"

#include <dirent.h>
#include <string.h>
#include <strings.h>
#include <math.h>
#include <algorithm>
#include <GLES2/gl2.h>
#include <utils/Log.h>

namespace android {

// Grid geometry (responsive: columns scale with panel width, capped 3..6).
static const int kGridThumbMax = 28;   // LRU cap on decoded thumbnails

static int gridCols(int w) { int c = w / 190; if (c < 3) c = 3; if (c > 6) c = 6; return c; }
static int gridRows(int h) { int r = (h - 170) / 150; if (r < 2) r = 2; if (r > 5) r = 5; return r; }

// Enumerate the bundled icon set once (dev override dir first, then the shipped
// dir). Names are stored without the .png suffix and sorted case-insensitively.
void NanoMenu::loadIconGridNames() {
    if (!mIconGridNames.empty()) return;
    const char* dirs[2] = {
        "/data/system/nano_xmb/icons_retroarch",
        "/system/etc/nano_xmb/icons_retroarch",
    };
    std::vector<std::string> names;
    for (const char* d : dirs) {
        DIR* dp = opendir(d);
        if (!dp) continue;
        struct dirent* e;
        while ((e = readdir(dp)) != nullptr) {
            const char* n = e->d_name;
            if (n[0] == '.') continue;
            size_t len = strlen(n);
            if (len < 5) continue;
            if (strcasecmp(n + len - 4, ".png") != 0) continue;
            names.emplace_back(std::string(n, len - 4));
        }
        closedir(dp);
        if (!names.empty()) break;   // prefer the dev override if it has content
    }
    std::sort(names.begin(), names.end(), [](const std::string& a, const std::string& b) {
        return strcasecmp(a.c_str(), b.c_str()) < 0;
    });
    mIconGridNames = std::move(names);
    ALOGI("icongrid: loaded %zu icon names", mIconGridNames.size());
}

void NanoMenu::applyIconGridFilter() {
    mIconGridFiltered.clear();
    if (mIconGridFilter.empty()) {
        mIconGridFiltered.reserve(mIconGridNames.size());
        for (int i = 0; i < (int)mIconGridNames.size(); i++) mIconGridFiltered.push_back(i);
    } else {
        for (int i = 0; i < (int)mIconGridNames.size(); i++) {
            // Case-insensitive substring match.
            if (strcasestr(mIconGridNames[i].c_str(), mIconGridFilter.c_str()))
                mIconGridFiltered.push_back(i);
        }
    }
    if (mIconGridCursor >= (int)mIconGridFiltered.size())
        mIconGridCursor = (int)mIconGridFiltered.size() - 1;
    if (mIconGridCursor < 0) mIconGridCursor = 0;
    mIconGridTop = 0;
}

// Downscale an RGBA buffer to dst x dst with a simple area average.
static void downscaleRGBA(const std::vector<uint8_t>& src, int sw, int sh,
                          std::vector<uint8_t>* dst, int dstSz) {
    dst->assign((size_t)dstSz * dstSz * 4, 0);
    for (int y = 0; y < dstSz; y++) {
        int sy0 = y * sh / dstSz, sy1 = (y + 1) * sh / dstSz; if (sy1 <= sy0) sy1 = sy0 + 1;
        for (int x = 0; x < dstSz; x++) {
            int sx0 = x * sw / dstSz, sx1 = (x + 1) * sw / dstSz; if (sx1 <= sx0) sx1 = sx0 + 1;
            uint32_t r = 0, g = 0, b = 0, a = 0, c = 0;
            for (int sy = sy0; sy < sy1 && sy < sh; sy++)
                for (int sx = sx0; sx < sx1 && sx < sw; sx++) {
                    const uint8_t* p = &src[((size_t)sy * sw + sx) * 4];
                    r += p[0]; g += p[1]; b += p[2]; a += p[3]; c++;
                }
            if (!c) c = 1;
            uint8_t* o = &(*dst)[((size_t)y * dstSz + x) * 4];
            o[0] = (uint8_t)(r / c); o[1] = (uint8_t)(g / c);
            o[2] = (uint8_t)(b / c); o[3] = (uint8_t)(a / c);
        }
    }
}

// Get (or generate) the cached glass bevel for an icon-name index. Rolling LRU:
// touched entries move to the front; the oldest are evicted past the cap.
GLuint NanoMenu::iconGridThumb(int nameIdx) {
    if (nameIdx < 0 || nameIdx >= (int)mIconGridNames.size()) return 0;
    auto it = mIconGridThumb.find(nameIdx);
    if (it != mIconGridThumb.end()) {
        // Move to LRU front.
        auto lit = std::find(mIconGridLru.begin(), mIconGridLru.end(), nameIdx);
        if (lit != mIconGridLru.end()) mIconGridLru.erase(lit);
        mIconGridLru.insert(mIconGridLru.begin(), nameIdx);
        return it->second;
    }
    std::vector<uint8_t> px; int w = 0, h = 0;
    if (!decodeRetroIconRGBA(mIconGridNames[nameIdx], &px, &w, &h) || w < 4 || h < 4) {
        mIconGridThumb[nameIdx] = 0;
        mIconGridLru.insert(mIconGridLru.begin(), nameIdx);
        return 0;
    }
    std::vector<uint8_t> small;
    downscaleRGBA(px, w, h, &small, 64);
    GLuint nmap = bevelFromRGBA(small.data(), 64, 64);
    mIconGridThumb[nameIdx] = nmap;
    mIconGridLru.insert(mIconGridLru.begin(), nameIdx);
    // Evict beyond the cap.
    while ((int)mIconGridLru.size() > kGridThumbMax) {
        int victim = mIconGridLru.back();
        mIconGridLru.pop_back();
        auto vit = mIconGridThumb.find(victim);
        if (vit != mIconGridThumb.end()) {
            if (vit->second) { GLuint t = vit->second; glDeleteTextures(1, &t); }
            mIconGridThumb.erase(vit);
        }
    }
    return nmap;
}

void NanoMenu::iconGridResetCache() {
    for (auto& kv : mIconGridThumb)
        if (kv.second) { GLuint t = kv.second; glDeleteTextures(1, &t); }
    mIconGridThumb.clear();
    mIconGridLru.clear();
}

void NanoMenu::openIconGridPicker() {
    if (mGsEditIdx < 0 || mGsEditIdx >= (int)mXmbSystems.size()) return;
    loadIconGridNames();
    mIconGridFilter.clear();
    mIconGridCursor = 0;
    mIconGridTop = 0;
    mIconGridAnim = 0.0f;   // fade in on open
    applyIconGridFilter();
    // Pre-select the system's current retroarch: icon if it has one.
    const std::string& ref = mXmbSystems[mGsEditIdx].iconRef;
    if (ref.compare(0, 10, "retroarch:") == 0) {
        std::string cur = ref.substr(10);
        for (int i = 0; i < (int)mIconGridFiltered.size(); i++) {
            if (mIconGridNames[mIconGridFiltered[i]] == cur) { mIconGridCursor = i; break; }
        }
    }
    Ps3Level lvl;
    lvl.title = "Choose Icon";
    lvl.sel = 0;
    lvl.screenKind = GS_ICONGRID;
    mPs3Stack.push_back(lvl);
}

void NanoMenu::closeIconGridPicker() {
    iconGridResetCache();
}

void NanoMenu::iconGridNav(int dx, int dy) {
    if (mIconGridFiltered.empty()) return;
    int cols = gridCols(mWidth);
    int n = (int)mIconGridFiltered.size();
    int cur = mIconGridCursor;
    if (dx != 0) { cur += dx; }
    if (dy != 0) { cur += dy * cols; }
    if (cur < 0) cur = 0;
    if (cur >= n) cur = n - 1;
    mIconGridCursor = cur;
    // Keep the cursor row visible.
    int rows = gridRows(mHeight);
    int curRow = mIconGridCursor / cols;
    if (curRow < mIconGridTop) mIconGridTop = curRow;
    if (curRow >= mIconGridTop + rows) mIconGridTop = curRow - rows + 1;
    if (mIconGridTop < 0) mIconGridTop = 0;
}

void NanoMenu::iconGridSelect() {
    if (mGsEditIdx < 0 || mGsEditIdx >= (int)mXmbSystems.size()) return;
    if (mIconGridCursor < 0 || mIconGridCursor >= (int)mIconGridFiltered.size()) return;
    const std::string& name = mIconGridNames[mIconGridFiltered[mIconGridCursor]];
    mXmbSystems[mGsEditIdx].iconRef = "retroarch:" + name;
    ALOGI("icongrid: assigned %s to system %s", name.c_str(),
          mXmbSystems[mGsEditIdx].id.c_str());
    saveSystemsConfig();
    // Pop the grid, free thumbnails, then refresh the editor + Game category so
    // the new icon shows immediately.
    closeIconGridPicker();
    if (!mPs3Stack.empty() && mPs3Stack.back().screenKind == GS_ICONGRID)
        mPs3Stack.pop_back();
    gsRefreshStackLevels();
    buildPs3Cats();
}

void NanoMenu::renderIconGridPicker() {
    int W = mWidth, H = mHeight;
    // Open fade-in + gentle upward settle.
    float dt = mFrameDt; if (dt < 0.0f) dt = 0.0f; if (dt > 0.1f) dt = 0.1f;
    mIconGridAnim += (1.0f - mIconGridAnim) * (1.0f - expf(-13.0f * dt));
    if (mIconGridAnim > 0.999f) mIconGridAnim = 1.0f;
    float a = mIconGridAnim;
    float slide = (1.0f - a) * 24.0f;
    // Dark scrim over the menu (fades in).
    drawQuad(0, 0, (float)W, (float)H, 0.04f, 0.05f, 0.06f, 0.92f * a);

    int cols = gridCols(W), rows = gridRows(H);
    float margin = W * 0.06f;
    float top = 96.0f + slide;
    float footerH = 56.0f;
    float gridW = W - margin * 2.0f;
    float cell = gridW / cols;
    float iconSz = cell * 0.66f;

    // Title + filter line.
    drawText("Choose Icon", margin, 40.0f + slide, 0.85f, 1.0f, 1.0f, 1.0f, a);
    char info[160];
    if (mIconGridFilter.empty())
        snprintf(info, sizeof(info), "%zu icons", mIconGridFiltered.size());
    else
        snprintf(info, sizeof(info), "filter: \"%s\"  (%zu)",
                 mIconGridFilter.c_str(), mIconGridFiltered.size());
    drawText(info, margin, 74.0f + slide, 0.5f, 0.75f, 0.85f, 0.95f, a);

    // Visible cells.
    int first = mIconGridTop * cols;
    int last = first + cols * rows;
    int n = (int)mIconGridFiltered.size();
    mGlassUniformsSet = false;   // re-arm glass uniforms for this pass
    for (int idx = first; idx < last && idx < n; idx++) {
        int gi = idx - first;
        int cx = gi % cols, cy = gi / cols;
        float x = margin + cx * cell;
        float y = top + cy * cell;
        bool sel = (idx == mIconGridCursor);
        // Cell background + selection ring.
        if (sel) drawRoundedRect(x + 4, y + 4, cell - 8, cell - 8, 14.0f, 0.20f, 0.45f, 0.85f, 0.55f * a);
        else     drawRoundedRect(x + 6, y + 6, cell - 12, cell - 12, 12.0f, 1.0f, 1.0f, 1.0f, 0.06f * a);
        float ix = x + (cell - iconSz) * 0.5f;
        float iy = y + (cell - iconSz) * 0.5f;
        GLuint nmap = iconGridThumb(mIconGridFiltered[idx]);
        if (mIconGlassReady && nmap && ps3bg::workTex())
            drawGlassIcon(nmap, ix, iy, iconSz, iconSz, 1.0f, 1.0f, 1.0f, a);
    }

    // Selected name + footer hints. Filenames are sanitized (underscores for
    // spaces); prettify back to spaces for display.
    if (mIconGridCursor >= 0 && mIconGridCursor < n) {
        std::string nm = mIconGridNames[mIconGridFiltered[mIconGridCursor]];
        for (char& c : nm) if (c == '_') c = ' ';
        float tw = measureText(nm.c_str(), 0.5f);
        drawText(nm.c_str(), (W - tw) * 0.5f, (float)H - footerH - 30.0f, 0.5f,
                 1.0f, 1.0f, 1.0f, a);
    }
    const char* hints = "Enter: Select    Y: Filter    Back: Cancel";
    float hw = measureText(hints, 0.45f);
    drawText(hints, (W - hw) * 0.5f, (float)H - 28.0f, 0.45f, 0.7f, 0.78f, 0.88f, a);
}

} // namespace android
