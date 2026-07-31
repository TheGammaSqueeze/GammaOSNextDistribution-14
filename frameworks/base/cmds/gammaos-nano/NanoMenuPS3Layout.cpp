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

// GammaOS Nano PS3 XMB responsive layout model. 1:1 port of the source web
// app's resize() (index.html 1865-2047) plus its ?aspect / ?orientation / ?res
// model (index.html 1375-1449): the XMB is authored in a fixed 1920x1080
// virtual space and mapped to the panel by a uniform scale plus position-
// compression factors, so 4:3 / 1:1 / 3:2 / 16:9 / 21:9 / portrait / low-res
// all lay out correctly without anamorphic stretching.
//
// By default the panel's native logical (post-DRM-rotation) size IS the frame,
// so the UI fills the panel at its real aspect. A forced aspect/resolution/
// orientation fits a smaller frame inside the panel (centred, integer-magnified
// for a simulated low resolution) with black bars around it, matching the web
// app's letterboxed simulation. Real device resolutions (480x800, 640x480,
// 1024x768, 480p/576p/720p/1080p, ...) are handled identically whether they are
// the actual panel or a forced simulation.

#include "NanoMenuPS3.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>

namespace android {
namespace ps3 {

float gScale       = 1.0f;
float gDescSize    = ITEM_DESC_SIZE;
float gActivePad   = ITEM_ACTIVE_PAD;
float gOffX        = 0.0f;
float gOffY        = 0.0f;
float LAYOUT_FIT   = 1.0f;
float LAYOUT_XC    = 1.0f;
float LAYOUT_XSHIFT = 0.0f;
float LAYOUT_VFIT  = 1.0f;
float LAYOUT_VOFF  = 0.0f;
float DESC_BOOST   = 1.0f;
bool  gPortrait    = false;
float gFrameX = 0.0f, gFrameY = 0.0f, gFrameW = 0.0f, gFrameH = 0.0f;

// ---- parsers (mirror ASPECTS / RES_PRESETS / orientation parsing) ----

float parseAspect(const char* s) {
    if (!s || !*s) return 0.0f;
    // Named ratios.
    if (!strcmp(s, "1:1")) return 1.0f;
    if (!strcmp(s, "4:3")) return 4.0f / 3.0f;
    if (!strcmp(s, "3:2")) return 3.0f / 2.0f;
    if (!strcmp(s, "16:9")) return 16.0f / 9.0f;
    if (!strcmp(s, "16:10")) return 16.0f / 10.0f;
    if (!strcmp(s, "21:9")) return 21.0f / 9.0f;
    // Generic "W:H".
    const char* colon = strchr(s, ':');
    if (colon) {
        float a = (float)atof(s);
        float b = (float)atof(colon + 1);
        if (a > 0.0f && b > 0.0f) return a / b;
        return 0.0f;
    }
    // Bare number.
    float n = (float)atof(s);
    return (n > 0.1f) ? n : 0.0f;
}

int parseOrientation(const char* s) {
    if (!s || !*s) return ORIENT_AUTO;
    if (!strcasecmp(s, "portrait") || !strcasecmp(s, "p")) return ORIENT_PORTRAIT;
    if (!strcasecmp(s, "landscape") || !strcasecmp(s, "l")) return ORIENT_LANDSCAPE;
    return ORIENT_AUTO;
}

bool parseRes(const char* s, int* w, int* h) {
    if (!s || !*s || !w || !h) return false;
    struct Preset { const char* name; int w, h; };
    static const Preset kPresets[] = {
        {"240p", 426, 240},  {"360p", 640, 360},  {"480p", 640, 480},
        {"vga", 640, 480},   {"576p", 720, 576},  {"720p", 1280, 720},
        {"hd", 1280, 720},   {"768p", 1366, 768}, {"900p", 1600, 900},
        {"1080p", 1920, 1080}, {"fhd", 1920, 1080}, {"1440p", 2560, 1440},
        {"qhd", 2560, 1440}, {"2160p", 3840, 2160}, {"4k", 3840, 2160},
        {"uhd", 3840, 2160},
    };
    for (const Preset& p : kPresets) {
        if (!strcasecmp(s, p.name)) { *w = p.w; *h = p.h; return true; }
    }
    // "WxH" / "W*H" / "W X H".
    int ww = 0, hh = 0;
    char sep = 0;
    if (sscanf(s, "%d%c%d", &ww, &sep, &hh) == 3 &&
        (sep == 'x' || sep == 'X' || sep == '*') && ww > 0 && hh > 0) {
        *w = ww; *h = hh; return true;
    }
    return false;
}

// ---- the layout solve ----

void layoutCompute(const LayoutParams& P) {
    if (P.panelW <= 0 || P.panelH <= 0) return;
    const float panelW = (float)P.panelW;
    const float panelH = (float)P.panelH;

    // Simulated viewport (web's vw/vh): the forced resolution if any, else the
    // real panel.
    const float vw = (P.simW > 0) ? (float)P.simW : panelW;
    const float vh = (P.simH > 0) ? (float)P.simH : panelH;

    // Target aspect + orientation.
    bool wantPortrait;
    if (P.orientation == ORIENT_PORTRAIT)      wantPortrait = true;
    else if (P.orientation == ORIENT_LANDSCAPE) wantPortrait = false;
    else                                        wantPortrait = (vh > vw); // AUTO

    float r;
    if (P.aspectOverride > 0.0f) {
        const float a = P.aspectOverride;          // canonical (e.g. 1.333 for 4:3)
        if (fabsf(a - 1.0f) < 0.001f) wantPortrait = false;  // square never rotates
        r = wantPortrait ? (1.0f / a) : a;
    } else {
        r = vw / vh;                                // native ratio encodes orientation
        if (fabsf(r - 1.0f) < 0.001f) wantPortrait = false;
    }

    // Fit the content frame (fw x fh) inside the viewport for ratio r.
    float fw, fh;
    if (vw / vh > r) { fh = vh; fw = fh * r; }
    else             { fw = vw; fh = fw / r; }
    gPortrait = wantPortrait;

    const float READ_CUT = 540.0f;
    float baseScale, vOffWithin;
    if (wantPortrait) {
        // Zoom in past a plain width-fit; park the focus row at 44% height so a
        // long item list runs below it. (index.html 1949-1973)
        const float PORTRAIT_ZOOM = 1.3f;
        // Small portrait panels (e.g. a 480-wide handheld) render the XMB far too small at the base
        // 1.3 zoom - the icons/text need to be roughly 2x bigger to read. Scale the zoom up as the
        // frame narrows (fw ~480 -> ~2.2, i.e. ~1.7x the base), clamped so a large portrait panel
        // (tablet) is not over-zoomed. Applies to the native panel too, not just a simulated res.
        const float pZoom = fmaxf(PORTRAIT_ZOOM, fminf(2.2f, 1050.0f / fw));
        baseScale = (fw / VW) * pZoom;
        LAYOUT_FIT = (fw / baseScale) / VW;
        LAYOUT_XC = 1.0f - (1.0f - LAYOUT_FIT) * 0.56f;
        LAYOUT_VFIT = (fh / baseScale) / VH;
        vOffWithin = fh * 0.44f - ITEM_FOCUS_Y * baseScale;
        LAYOUT_VOFF = vOffWithin / baseScale;
    } else {
        // Fill the frame height uniformly; narrow frames soften the position
        // compression toward 1.0 so content crops on the right like the real PS3
        // 4:3 (0.86 at 4:3 vs a pure-fit 0.75). (index.html 1974-1990)
        baseScale = fh / VH;
        LAYOUT_FIT = (fw / baseScale) / VW;
        LAYOUT_XC = (LAYOUT_FIT >= 1.0f) ? LAYOUT_FIT
                                         : (1.0f - (1.0f - LAYOUT_FIT) * 0.56f);
        LAYOUT_VFIT = (fh / baseScale) / VH;
        vOffWithin = (fh - VH * baseScale) * 0.5f;
        LAYOUT_VOFF = vOffWithin / baseScale;
    }

    // Subtitle font boost on genuinely tiny frames. (index.html 1999-2001)
    const float md = fminf(fw, fh);
    DESC_BOOST = fminf(1.5f, 1.0f + fmaxf(0.0f, READ_CUT - md) / 300.0f);
    // Effective subtitle size: resolution-gated (base at >=720p, one size bigger
    // on small panels via DESC_BOOST). Read per frame by drawDesc. The active pad
    // that fits its wrapped lines is computed and eased in renderPs3Xmb (it needs
    // the per-item line count), so gActivePad is not set here.
    gDescSize  = ITEM_DESC_SIZE * DESC_BOOST;

    // Extra leftward shift on frames narrower than 4:3. (index.html 2006-2011)
    const float FIT_43 = 0.75f;
    LAYOUT_XSHIFT = (LAYOUT_FIT < FIT_43 - 0.01f)
        ? (FIT_43 - LAYOUT_FIT) * VW * 0.34f
        : 0.0f;

    // Place the (fw x fh) frame inside the panel: largest integer factor when
    // upscaling (crisp NxN like the web's integer scaling), fractional when the
    // frame is larger than the panel. = 1 for native fill and for a pillarboxed
    // aspect override that already fits. (index.html 1909-1927)
    float kf = fminf(panelW / fw, panelH / fh);
    float k = (kf >= 1.0f) ? floorf(kf) : kf;
    if (k <= 0.0f) k = kf;

    gScale  = baseScale * k;
    gFrameW = fw * k;
    gFrameH = fh * k;
    gFrameX = (panelW - gFrameW) * 0.5f;
    gFrameY = (panelH - gFrameH) * 0.5f;
    gOffX = gFrameX;
    gOffY = gFrameY + k * vOffWithin;

    // User UI-size zoom: enlarge the menu about the visible-frame centre, keeping
    // that centre fixed in device space, so the category bar / item list / clock
    // grow together. The background frame (gFrame*) is untouched, so the wave
    // still fills the whole panel under the larger menu. Clamp to a sane range.
    float ui = P.uiScale;
    if (ui < 0.5f) ui = 0.5f;
    if (ui > 2.0f) ui = 2.0f;
    if (fabsf(ui - 1.0f) > 0.001f) {
        const float cxv = (VW * LAYOUT_FIT) * 0.5f;            // visible centre x (virtual)
        const float cyv = LAYOUT_VOFF + (VH * LAYOUT_VFIT) * 0.5f; // visible centre y (virtual)
        gOffX += gScale * cxv * (1.0f - ui);
        gOffY += gScale * cyv * (1.0f - ui);
        gScale *= ui;
    }
}

} // namespace ps3
} // namespace android
