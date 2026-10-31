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

// DSi theme chrome drawn as vector shapes at panel resolution.
//
// The DSi launcher's selection frame, carousel tiles, balloon tail and frame notch, scrollbar
// arrows / thumb / ticks, message box and glossy buttons came from the firmware as 1x pixel art
// (the frame and tile PNGs are 8x nearest blow-ups of 64x80 and 64x64 cells). On the RG DS Plus
// panels one DS pixel is four device pixels, so every curve showed four-pixel stair steps. Here
// each of them is rebuilt from signed-distance shapes (rounded rects with per-corner radii,
// ellipses, triangles, an optional rounded cut-out and a straight cut) with one device pixel of
// analytic anti-aliasing, filled with the same vertical colour bands sampled from the original
// art. The layer tables below are in DS pixels of each sprite cell, so the shapes keep the
// firmware proportions at any panel scale.
//
// persist.gammaos.nano.nds.vector_chrome=0 (read with the DSi assets) keeps the sprite and pixel
// paths, which also stay the fallback when the program does not link. ensureNdsAssets builds the
// program before it loads textures and skips the frame and tile sprites while it is in use.

#define LOG_TAG "GammaOSNano"

#include <math.h>
#include <string.h>

#include <cutils/properties.h>
#include <utils/Log.h>
#include <GLES2/gl2.h>

#include "NanoMenu.h"
#include "NanoMenuDrm.h"

namespace android {

// ---------------------------------------------------------------------------
// Program
// ---------------------------------------------------------------------------

static const char NDS_VEC_VERTEX_SHADER[] = R"(
    attribute vec2 aPosition;
    attribute vec2 aLocal;
    attribute vec4 aColor;
    uniform mat2 uRotation;
    varying vec2 vLocal;
    varying vec4 vColor;
    void main() {
        gl_Position = vec4(uRotation * aPosition, 0.0, 1.0);
        vLocal = aLocal;
        vColor = aColor;
    }
)";

// vLocal is the fragment's position in device pixels relative to the shape centre (y down). The
// distances reach a few hundred pixels, past what a half float resolves to a fraction of a pixel,
// so the maths runs at highp where the GPU has it (Mali-G52 does).
static const char NDS_VEC_FRAGMENT_SHADER[] = R"(
#ifdef GL_FRAGMENT_PRECISION_HIGH
    precision highp float;
#else
    precision mediump float;
#endif
    varying vec2 vLocal;
    varying vec4 vColor;
    uniform float uMode;      // 0 rounded rect, 1 ellipse, 2 triangle
    uniform vec2  uHalf;      // rect half size / ellipse semi-axes
    uniform vec4  uRadii;     // rect corner radii: bottom-right, top-right, bottom-left, top-left
    uniform vec4  uHole;      // rounded cut-out: centre xy, half size zw (z <= 0: none)
    uniform float uHoleR;
    uniform vec3  uCut;       // (y0, k, sign): keep where sign * (y - y0 - k * x * x) >= 0; sign 0 = no cut
    uniform vec3  uE0;        // triangle edges: inward unit normal xy, offset z
    uniform vec3  uE1;
    uniform vec3  uE2;
    float sdBox(vec2 p, vec2 b, vec4 r) {
        r.xy = (p.x > 0.0) ? r.xy : r.zw;
        r.x  = (p.y > 0.0) ? r.x  : r.y;
        vec2 q = abs(p) - b + r.x;
        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
    }
    void main() {
        vec2 p = vLocal;
        float d;
        if (uMode < 0.5) {
            d = sdBox(p, uHalf, uRadii);
        } else if (uMode < 1.5) {
            // First-order ellipse distance: exact on the outline, which is all the edge needs.
            float k0 = length(p / uHalf);
            float k1 = length(p / (uHalf * uHalf));
            d = (k1 > 0.00001) ? k0 * (k0 - 1.0) / k1 : -min(uHalf.x, uHalf.y);
        } else {
            d = -min(min(dot(uE0.xy, p) + uE0.z, dot(uE1.xy, p) + uE1.z), dot(uE2.xy, p) + uE2.z);
        }
        if (uHole.z > 0.0) d = max(d, -sdBox(p - uHole.xy, uHole.zw, vec4(uHoleR)));
        if (uCut.z != 0.0) {
            float g = uCut.z * (p.y - uCut.x - uCut.y * p.x * p.x);
            d = max(d, -g / length(vec2(2.0 * uCut.y * p.x, 1.0)));
        }
        float a = clamp(0.5 - d, 0.0, 1.0);
        if (a <= 0.0) discard;
        gl_FragColor = vec4(vColor.rgb, vColor.a * a);
    }
)";

// One GL context per process (NanoMenu::init), so the program lives for the process like the
// ones initShaders builds. Built on the first DSi frame, so the other themes never pay for it.
static GLuint sVecProg = 0;
static bool   sVecTried = false;
static GLint  sVecLocPos = -1, sVecLocLocal = -1, sVecLocColor = -1;
static GLint  sVecLocRot = -1, sVecLocMode = -1, sVecLocHalf = -1, sVecLocRadii = -1;
static GLint  sVecLocHole = -1, sVecLocHoleR = -1, sVecLocCut = -1;
static GLint  sVecLocE0 = -1, sVecLocE1 = -1, sVecLocE2 = -1;

static GLuint vecCompile(GLenum type, const char* src) {
    GLuint sh = glCreateShader(type);
    glShaderSource(sh, 1, &src, nullptr);
    glCompileShader(sh);
    GLint ok = 0;
    glGetShaderiv(sh, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[512] = {};
        glGetShaderInfoLog(sh, sizeof(log), nullptr, log);
        ALOGE("dsi vector chrome: shader compile failed: %s", log);
        glDeleteShader(sh);
        return 0;
    }
    return sh;
}

static void vecBuildProgram() {
    if (sVecTried) return;
    sVecTried = true;
    GLuint vs = vecCompile(GL_VERTEX_SHADER, NDS_VEC_VERTEX_SHADER);
    GLuint fs = vecCompile(GL_FRAGMENT_SHADER, NDS_VEC_FRAGMENT_SHADER);
    if (!vs || !fs) {
        if (vs) glDeleteShader(vs);
        if (fs) glDeleteShader(fs);
        return;
    }
    GLuint prog = glCreateProgram();
    glAttachShader(prog, vs);
    glAttachShader(prog, fs);
    glLinkProgram(prog);
    glDeleteShader(vs);
    glDeleteShader(fs);
    GLint ok = 0;
    glGetProgramiv(prog, GL_LINK_STATUS, &ok);
    if (!ok) {
        char log[512] = {};
        glGetProgramInfoLog(prog, sizeof(log), nullptr, log);
        ALOGE("dsi vector chrome: link failed, keeping the sprites: %s", log);
        glDeleteProgram(prog);
        return;
    }
    sVecLocPos   = glGetAttribLocation(prog, "aPosition");
    sVecLocLocal = glGetAttribLocation(prog, "aLocal");
    sVecLocColor = glGetAttribLocation(prog, "aColor");
    if (sVecLocPos < 0 || sVecLocLocal < 0 || sVecLocColor < 0) {
        ALOGE("dsi vector chrome: missing attributes, keeping the sprites");
        glDeleteProgram(prog);
        return;
    }
    sVecLocRot   = glGetUniformLocation(prog, "uRotation");
    sVecLocMode  = glGetUniformLocation(prog, "uMode");
    sVecLocHalf  = glGetUniformLocation(prog, "uHalf");
    sVecLocRadii = glGetUniformLocation(prog, "uRadii");
    sVecLocHole  = glGetUniformLocation(prog, "uHole");
    sVecLocHoleR = glGetUniformLocation(prog, "uHoleR");
    sVecLocCut   = glGetUniformLocation(prog, "uCut");
    sVecLocE0    = glGetUniformLocation(prog, "uE0");
    sVecLocE1    = glGetUniformLocation(prog, "uE1");
    sVecLocE2    = glGetUniformLocation(prog, "uE2");
    sVecProg = prog;
    ALOGI("dsi vector chrome: ready");
}

void NanoMenu::ndsVecInit() {
    mNdsVecChrome = false;
    if (!property_get_bool("persist.gammaos.nano.nds.vector_chrome", true)) return;
    vecBuildProgram();
    mNdsVecChrome = (sVecProg != 0);
}

// ---------------------------------------------------------------------------
// Batch: every instance added between ndsVecBegin and ndsVecEnd shares the shape uniforms, so a
// row of identical tiles or ticks is one draw. The colour bands are horizontal strips with the
// stop colours on their edges; the GPU's linear interpolation between two strip edges is the
// same piecewise-linear gradient the stops describe.
// ---------------------------------------------------------------------------

static const int NDS_VEC_MAX_VERTS = 3072;
static GLfloat sVecPos[NDS_VEC_MAX_VERTS * 2];
static GLfloat sVecLoc[NDS_VEC_MAX_VERTS * 2];
static GLfloat sVecCol[NDS_VEC_MAX_VERTS * 4];
static int     sVecN = 0;
static bool    sVecOpen = false;
static float   sVecBox[4] = {0.0f, 0.0f, 0.0f, 0.0f};   // shape bounds relative to its centre: x0, y0, x1, y1
// The part of the cut-out that is empty for certain (x0, y0, x1, y1 relative to the centre; x0 >= x1
// = none). The frame's window and the platform bands' holes are most of their layer's box, so the
// strips skip it instead of running the shader there only to discard.
static float   sVecSkip[4] = {0.0f, 0.0f, 0.0f, 0.0f};

static void vecFlush() {
    if (sVecN <= 0) return;
    glVertexAttribPointer(sVecLocPos, 2, GL_FLOAT, GL_FALSE, 0, sVecPos);
    glVertexAttribPointer(sVecLocLocal, 2, GL_FLOAT, GL_FALSE, 0, sVecLoc);
    glVertexAttribPointer(sVecLocColor, 4, GL_FLOAT, GL_FALSE, 0, sVecCol);
    glDrawArrays(GL_TRIANGLES, 0, sVecN);
    sVecN = 0;
}

// Inward unit normal + offset of the edge a->b of a triangle whose third corner is c.
static void vecEdge(float ax, float ay, float bx, float by, float cx, float cy, float out[3]) {
    float nx = -(by - ay), ny = bx - ax;
    float l = sqrtf(nx * nx + ny * ny);
    if (l < 1e-6f) { out[0] = 0.0f; out[1] = 0.0f; out[2] = -1.0f; return; }   // degenerate: draws nothing
    nx /= l; ny /= l;
    float c = -(nx * ax + ny * ay);
    if (nx * cx + ny * cy + c < 0.0f) { nx = -nx; ny = -ny; c = -c; }
    out[0] = nx; out[1] = ny; out[2] = c;
}

void NanoMenu::ndsVecBegin(const NdsVecShape& s) {
    if (sVecOpen) ndsVecEnd();
    if (!sVecProg) return;   // no program: Add and End stay no-ops (callers check ndsVecChrome first)
    flushSolidBatch();   // solids queued before this shape must land under it
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glUseProgram(sVecProg);
    glUniformMatrix2fv(sVecLocRot, 1, GL_FALSE, sDrmRotMat);
    glUniform1f(sVecLocMode, (float)s.mode);
    float hw = fmaxf(s.hw, 0.01f), hh = fmaxf(s.hh, 0.01f);
    glUniform2f(sVecLocHalf, hw, hh);
    const float rm = fminf(hw, hh);
    auto rc = [&](float r) { return fmaxf(0.0f, fminf(r, rm)); };
    glUniform4f(sVecLocRadii, rc(s.r[2]), rc(s.r[1]), rc(s.r[3]), rc(s.r[0]));
    sVecSkip[0] = sVecSkip[2] = 0.0f;
    if (s.holeHw > 0.0f && s.holeHh > 0.0f) {
        const float hr = fmaxf(0.0f, fminf(s.holeR, fminf(s.holeHw, s.holeHh)));
        glUniform4f(sVecLocHole, s.holeX, s.holeY, s.holeHw, s.holeHh);
        glUniform1f(sVecLocHoleR, hr);
        // The rect inside the rounded cut-out (its corners touch the arcs at 45 degrees), less the
        // anti-aliased fringe. Only for the rounded rect: the ellipse and triangle have no hole user.
        const float k = 0.293f * hr + 1.5f;
        if (s.mode == 0 && s.holeHw > k && s.holeHh > k) {
            sVecSkip[0] = s.holeX - s.holeHw + k; sVecSkip[1] = s.holeY - s.holeHh + k;
            sVecSkip[2] = s.holeX + s.holeHw - k; sVecSkip[3] = s.holeY + s.holeHh - k;
        }
    } else {
        glUniform4f(sVecLocHole, 0.0f, 0.0f, 0.0f, 0.0f);
        glUniform1f(sVecLocHoleR, 0.0f);
    }
    glUniform3f(sVecLocCut, s.cutY, s.cutK, s.cutSign);
    if (s.mode == 2) {
        float e[3][3];
        vecEdge(s.tri[0], s.tri[1], s.tri[2], s.tri[3], s.tri[4], s.tri[5], e[0]);
        vecEdge(s.tri[2], s.tri[3], s.tri[4], s.tri[5], s.tri[0], s.tri[1], e[1]);
        vecEdge(s.tri[4], s.tri[5], s.tri[0], s.tri[1], s.tri[2], s.tri[3], e[2]);
        glUniform3f(sVecLocE0, e[0][0], e[0][1], e[0][2]);
        glUniform3f(sVecLocE1, e[1][0], e[1][1], e[1][2]);
        glUniform3f(sVecLocE2, e[2][0], e[2][1], e[2][2]);
        sVecBox[0] = fminf(s.tri[0], fminf(s.tri[2], s.tri[4]));
        sVecBox[1] = fminf(s.tri[1], fminf(s.tri[3], s.tri[5]));
        sVecBox[2] = fmaxf(s.tri[0], fmaxf(s.tri[2], s.tri[4]));
        sVecBox[3] = fmaxf(s.tri[1], fmaxf(s.tri[3], s.tri[5]));
    } else {
        sVecBox[0] = -hw; sVecBox[1] = -hh; sVecBox[2] = hw; sVecBox[3] = hh;
    }
    // One pixel of margin: the anti-aliased edge reaches half a pixel past the outline.
    sVecBox[0] -= 1.5f; sVecBox[1] -= 1.5f; sVecBox[2] += 1.5f; sVecBox[3] += 1.5f;
    glEnableVertexAttribArray(sVecLocPos);
    glEnableVertexAttribArray(sVecLocLocal);
    glEnableVertexAttribArray(sVecLocColor);
    sVecN = 0;
    sVecOpen = true;
}

void NanoMenu::ndsVecAdd(float cx, float cy, const float* stopY, const float* stopRgb, int nStops,
                         float alpha, bool mirrorX) {
    if (!sVecOpen || nStops <= 0 || alpha <= 0.0f) return;
    auto colourAt = [&](float y, float* out) {
        if (y <= stopY[0]) { memcpy(out, stopRgb, 3 * sizeof(float)); return; }
        for (int i = 1; i < nStops; i++) {
            if (y <= stopY[i]) {
                float span = stopY[i] - stopY[i - 1];
                float t = (span > 1e-6f) ? (y - stopY[i - 1]) / span : 1.0f;
                for (int k = 0; k < 3; k++) out[k] = stopRgb[(i - 1) * 3 + k] + (stopRgb[i * 3 + k] - stopRgb[(i - 1) * 3 + k]) * t;
                return;
            }
        }
        memcpy(out, stopRgb + (nStops - 1) * 3, 3 * sizeof(float));
    };
    // Strip edges: the shape's top and bottom, every stop strictly between them, and the top and
    // bottom of the skipped hole interior. Kept sorted (the stops already are).
    const float yTop = sVecBox[1], yBot = sVecBox[3];
    const bool skip = sVecSkip[0] < sVecSkip[2] && sVecSkip[1] < sVecSkip[3];
    float ys[36]; int ne = 0;
    auto addEdge = [&](float y) {
        if (ne >= 36 || y < yTop || y > yBot) return;
        int at = ne;
        while (at > 0 && ys[at - 1] > y) at--;
        if ((at > 0 && y - ys[at - 1] < 1e-3f) || (at < ne && ys[at] - y < 1e-3f)) return;   // already an edge
        for (int j = ne; j > at; j--) ys[j] = ys[j - 1];
        ys[at] = y; ne++;
    };
    addEdge(yTop); addEdge(yBot);
    if (skip) { addEdge(sVecSkip[1]); addEdge(sVecSkip[3]); }
    for (int i = 0; i < nStops; i++) addEdge(stopY[i]);
    float cs[36][3];
    for (int e = 0; e < ne; e++) colourAt(ys[e], cs[e]);
    // Device x range (a mirrored instance flips the shape about its centre), and the skipped
    // interior in the same frame.
    const float lx0 = mirrorX ? -sVecBox[2] : sVecBox[0];
    const float lx1 = mirrorX ? -sVecBox[0] : sVecBox[2];
    const float kx0 = mirrorX ? -sVecSkip[2] : sVecSkip[0];
    const float kx1 = mirrorX ? -sVecSkip[0] : sVecSkip[2];
    const float sxl = mirrorX ? -1.0f : 1.0f;
    const float W = (float)mWidth, H = (float)mHeight;
    auto quad = [&](float xa, float xb, int e) {   // one strip piece between edges e and e+1
        if (sVecN + 6 > NDS_VEC_MAX_VERTS) vecFlush();
        const float ya = ys[e], yb = ys[e + 1];
        const float px[6] = {xa, xb, xb, xb, xa, xa};
        const float py[6] = {yb, yb, ya, ya, ya, yb};
        const int   top[6] = {0, 0, 1, 1, 1, 0};
        for (int v = 0; v < 6; v++) {
            const float dx = cx + px[v], dy = cy + py[v];
            sVecPos[sVecN * 2]     = (dx / W) * 2.0f - 1.0f;
            sVecPos[sVecN * 2 + 1] = 1.0f - (dy / H) * 2.0f;
            sVecLoc[sVecN * 2]     = px[v] * sxl;
            sVecLoc[sVecN * 2 + 1] = py[v];
            const float* c = top[v] ? cs[e] : cs[e + 1];
            sVecCol[sVecN * 4] = c[0]; sVecCol[sVecN * 4 + 1] = c[1]; sVecCol[sVecN * 4 + 2] = c[2];
            sVecCol[sVecN * 4 + 3] = alpha;
            sVecN++;
        }
    };
    for (int e = 0; e + 1 < ne; e++) {
        const float ym = (ys[e] + ys[e + 1]) * 0.5f;
        if (skip && ym > sVecSkip[1] && ym < sVecSkip[3]) {
            if (kx0 > lx0) quad(lx0, kx0, e);
            if (kx1 < lx1) quad(kx1, lx1, e);
        } else {
            quad(lx0, lx1, e);
        }
    }
}

void NanoMenu::ndsVecEnd() {
    if (!sVecOpen) return;
    vecFlush();
    glDisableVertexAttribArray(sVecLocPos);
    glDisableVertexAttribArray(sVecLocLocal);
    glDisableVertexAttribArray(sVecLocColor);
    sVecOpen = false;
}

// One rounded rect in device px, gradient stops at absolute device y. radii: tl, tr, br, bl.
void NanoMenu::drawNdsVecRect(float x, float y, float w, float h, const float radii[4],
                              const float* stopY, const float* stopRgb, int nStops, float alpha) {
    if (w <= 0.0f || h <= 0.0f || nStops <= 0) return;
    NdsVecShape s;
    s.hw = w * 0.5f; s.hh = h * 0.5f;
    for (int i = 0; i < 4; i++) s.r[i] = radii[i];
    const float cy = y + h * 0.5f;
    float ry[24]; const int n = nStops < 24 ? nStops : 24;
    for (int i = 0; i < n; i++) ry[i] = stopY[i] - cy;
    ndsVecBegin(s);
    ndsVecAdd(x + w * 0.5f, cy, ry, stopRgb, n, alpha);
    ndsVecEnd();
}

void NanoMenu::drawNdsVecTri(float x0, float y0, float x1, float y1, float x2, float y2,
                             float r, float g, float b, float a) {
    NdsVecShape s;
    s.mode = 2;
    const float cx = (x0 + x1 + x2) / 3.0f, cy = (y0 + y1 + y2) / 3.0f;
    const float t[6] = {x0 - cx, y0 - cy, x1 - cx, y1 - cy, x2 - cx, y2 - cy};
    memcpy(s.tri, t, sizeof(t));
    const float sy0 = 0.0f, rgb[3] = {r, g, b};
    ndsVecBegin(s);
    ndsVecAdd(cx, cy, &sy0, rgb, 1, a);
    ndsVecEnd();
}

// ---------------------------------------------------------------------------
// Layer tables (DS px of each sprite cell). Generated from the review design; the colours are
// the original art's, sampled band by band.
// ---------------------------------------------------------------------------

struct NdsVecStop  { float y; uint8_t r, g, b; };
struct NdsVecHole  { float x0, y0, x1, y1, r; };
struct NdsVecLayer {
    uint8_t mode;          // 0 rounded rect, 1 ellipse, 2 triangle
    int8_t  hole;          // index into the element's hole table, -1 = none
    int8_t  cutSign;       // straight cut: +1 keeps y >= cutY, -1 keeps y <= cutY, 0 = none
    uint8_t stop0, nStops; // range in the element's stop table
    float   g[8];          // rect x0,y0,x1,y1, radii tl,tr,br,bl | ellipse cx,cy,rx,ry | triangle x0,y0,x1,y1,x2,y2
    float   cutY, cutK;    // cut line y (DS px) and its curvature (y += k * dx^2 about the shape centre)
};

static const NdsVecHole kNdsFrameHoles[] = {
    {8.0f, 9.0f, 56.0f, 57.0f, 5.0f},
    {4.0f, 30.0f, 60.0f, 64.0f, 9.0f},
    {4.0f, 30.0f, 60.0f, 67.0f, 9.0f},
    {4.0f, 30.0f, 60.0f, 70.0f, 9.0f},
    {4.0f, 30.0f, 60.0f, 73.0f, 9.0f},
    {4.0f, 30.0f, 60.0f, 75.0f, 9.0f},
    {4.0f, 30.0f, 60.0f, 77.0f, 9.0f},
    // Hole 0 grown by 0.375 DS px on every side (1.5 device px at the panel's 4x scale; the radius
    // grows with it). Every layer under the inner window bevel cuts with this one, so their
    // anti-aliased fringes land under the bevel's opaque ring and only the bevel's own edge shows
    // at the window; with all of them on hole 0 the eight fringes stacked into an almost hard edge.
    {7.625f, 8.625f, 56.375f, 57.375f, 5.375f},
};
static const NdsVecStop kNdsFrameStops[] = {
    {0.0f, 121, 203, 251}, {1.5f, 24, 113, 251}, {3.5f, 0, 89, 243}, {4.5f, 0, 56, 211},
    {5.5f, 0, 40, 186}, {10.5f, 0, 40, 186}, {11.5f, 0, 73, 227}, {65.5f, 0, 73, 227},
    {66.5f, 0, 56, 211}, {70.5f, 0, 56, 211}, {71.5f, 0, 40, 186}, {74.5f, 0, 40, 186},
    {75.5f, 0, 56, 211}, {76.5f, 0, 89, 243}, {78.5f, 48, 130, 251}, {3.5f, 48, 130, 251},
    {4.5f, 0, 89, 243}, {5.5f, 0, 73, 227}, {6.5f, 0, 56, 211}, {7.5f, 0, 56, 211},
    {8.5f, 0, 40, 186}, {17.5f, 0, 40, 186}, {18.5f, 0, 56, 211}, {24.5f, 0, 56, 211},
    {25.5f, 0, 73, 227}, {49.5f, 0, 73, 227}, {50.5f, 0, 56, 211}, {68.5f, 0, 56, 211},
    {69.5f, 0, 40, 186}, {71.5f, 0, 40, 186}, {72.5f, 0, 56, 211}, {73.5f, 0, 73, 227},
    {74.5f, 0, 73, 227}, {75.5f, 24, 113, 251}, {76.5f, 73, 154, 251}, {4.5f, 48, 130, 251},
    {5.5f, 48, 130, 251}, {6.5f, 24, 113, 251}, {8.5f, 24, 113, 251}, {9.5f, 0, 89, 243},
    {11.5f, 0, 89, 243}, {12.5f, 0, 73, 227}, {14.5f, 0, 73, 227}, {15.5f, 0, 56, 211},
    {16.5f, 0, 56, 211}, {17.5f, 0, 40, 186}, {59.5f, 0, 40, 186}, {60.5f, 0, 56, 211},
    {65.5f, 0, 56, 211}, {66.5f, 0, 73, 227}, {67.5f, 0, 73, 227}, {68.5f, 0, 89, 243},
    {70.5f, 0, 89, 243}, {71.5f, 24, 113, 251}, {72.5f, 48, 130, 251}, {73.5f, 73, 154, 251},
    {0.0f, 24, 113, 251}, {0.0f, 48, 130, 251}, {0.0f, 48, 130, 251}, {0.0f, 0, 89, 243},
    {0.0f, 0, 73, 227}, {0.0f, 0, 56, 211}, {0.0f, 0, 40, 186}, {0.0f, 0, 56, 211},
    {0.0f, 0, 56, 211}, {0.0f, 0, 73, 227}, {0.0f, 0, 73, 227}, {0.0f, 0, 89, 243},
    {0.0f, 0, 89, 243}, {0.0f, 24, 113, 251}, {0.0f, 24, 113, 251}, {0.0f, 48, 130, 251},
    {0.0f, 48, 130, 251}, {0.0f, 73, 154, 251}, {6.5f, 24, 113, 251}, {9.5f, 24, 113, 251},
    {10.5f, 73, 154, 251}, {11.5f, 73, 154, 251}, {12.5f, 97, 178, 251}, {13.5f, 97, 178, 251},
    {14.5f, 121, 203, 251}, {15.5f, 146, 219, 251}, {19.5f, 146, 219, 251}, {20.5f, 121, 203, 251},
    {21.5f, 121, 203, 251}, {22.5f, 97, 178, 251}, {31.5f, 97, 178, 251}, {32.5f, 73, 154, 251},
    {35.5f, 73, 154, 251}, {36.5f, 48, 130, 251}, {38.5f, 48, 130, 251}, {39.5f, 24, 113, 251},
    {43.5f, 24, 113, 251}, {44.5f, 0, 89, 243}, {49.5f, 0, 89, 243}, {50.5f, 0, 73, 227},
    {51.5f, 0, 73, 227}, {52.5f, 0, 56, 211}, {57.5f, 0, 56, 211}, {59.5f, 0, 89, 243},
    {7.5f, 73, 154, 251}, {8.5f, 24, 113, 251}, {9.5f, 24, 113, 251}, {10.5f, 73, 154, 251},
    {11.5f, 121, 203, 251}, {35.5f, 121, 203, 251}, {36.5f, 97, 178, 251}, {49.5f, 97, 178, 251},
    {50.5f, 121, 203, 251}, {53.5f, 121, 203, 251}, {58.5f, 97, 178, 251}, {8.5f, 0, 73, 227},
    {9.5f, 0, 89, 243}, {10.5f, 24, 113, 251}, {33.5f, 24, 113, 251}, {34.5f, 48, 130, 251},
    {52.5f, 48, 130, 251}, {53.5f, 73, 154, 251}, {57.5f, 73, 154, 251},
};
static const NdsVecLayer kNdsFrameLayers[] = {
    {0,  7,  0,   0,  1, {0.0f, 0.0f, 64.0f, 80.0f, 5.5f, 5.5f, 5.5f, 5.5f}, 0.0f, 0.0f},   // outer rim
    {0,  7,  0,   1, 14, {1.0f, 1.0f, 63.0f, 79.0f, 4.5f, 4.5f, 4.5f, 4.5f}, 0.0f, 0.0f},   // side band 1
    {0,  7,  0,  15, 20, {2.0f, 2.0f, 62.0f, 78.0f, 3.5f, 3.5f, 3.5f, 3.5f}, 0.0f, 0.0f},   // side band 2
    {0,  7,  0,  35, 21, {3.0f, 3.0f, 61.0f, 77.0f, 2.5f, 2.5f, 2.5f, 2.5f}, 0.0f, 0.0f},   // side band 3
    {0, -1,  0,  56,  1, {3.0f, 1.0f, 61.0f, 10.0f, 2.5f, 2.5f, 0.0f, 0.0f}, 0.0f, 0.0f},   // top band ground
    {1, -1,  1,  57,  1, {3.5f, 3.1f, 1.6f, 2.5f, 0.0f, 0.0f, 0.0f, 0.0f}, 1.0f, 0.0f},   // top-left highlight
    {1, -1,  1,  58,  1, {60.5f, 3.1f, 1.6f, 2.5f, 0.0f, 0.0f, 0.0f, 0.0f}, 1.0f, 0.0f},   // top-right highlight
    {0, -1,  0,  59,  1, {5.5f, 2.0f, 58.5f, 6.0f, 3.0f, 3.0f, 0.5f, 0.5f}, 0.0f, 0.0f},   // top gloss lens F
    {1, -1,  0,  60,  1, {32.0f, 3.5f, 24.0f, 1.5f, 0.0f, 0.0f, 0.0f, 0.0f}, 0.0f, 0.0f},   // top gloss lens A
    {1, -1,  1,  61,  1, {32.0f, 2.0f, 21.5f, 2.0f, 0.0f, 0.0f, 0.0f, 0.0f}, 2.0f, 0.0f},   // top gloss lens D
    {0,  7,  0,  62,  1, {4.0f, 48.0f, 60.0f, 77.0f, 0.0f, 0.0f, 0.0f, 0.0f}, 0.0f, 0.0f},   // START platform ground
    {0,  1,  0,  63,  1, {4.0f, 48.0f, 60.0f, 77.0f, 0.0f, 0.0f, 0.0f, 0.0f}, 0.0f, 0.0f},   // platform band D
    {1, -1,  0,  64,  1, {32.0f, 64.4f, 10.0f, 1.4f, 0.0f, 0.0f, 0.0f, 0.0f}, 0.0f, 0.0f},   // platform dome D
    {0,  2,  0,  65,  1, {4.0f, 48.0f, 60.0f, 77.0f, 0.0f, 0.0f, 0.0f, 0.0f}, 0.0f, 0.0f},   // platform band A
    {1, -1,  0,  66,  1, {32.0f, 67.4f, 14.0f, 1.4f, 0.0f, 0.0f, 0.0f, 0.0f}, 0.0f, 0.0f},   // platform dome A
    {0,  3,  0,  67,  1, {4.0f, 48.0f, 60.0f, 77.0f, 0.0f, 0.0f, 0.0f, 0.0f}, 0.0f, 0.0f},   // platform band F
    {1, -1,  0,  68,  1, {32.0f, 70.5f, 14.0f, 2.2f, 0.0f, 0.0f, 0.0f, 0.0f}, 0.0f, 0.0f},   // platform dome F
    {0,  4,  0,  69,  1, {4.0f, 48.0f, 60.0f, 77.0f, 0.0f, 0.0f, 0.0f, 0.0f}, 0.0f, 0.0f},   // platform band B
    {1, -1,  0,  70,  1, {32.0f, 73.6f, 15.0f, 2.5f, 0.0f, 0.0f, 0.0f, 0.0f}, 0.0f, 0.0f},   // platform dome B
    {0,  5,  0,  71,  1, {4.0f, 48.0f, 60.0f, 77.0f, 0.0f, 0.0f, 0.0f, 0.0f}, 0.0f, 0.0f},   // platform band G
    {1, -1,  0,  72,  1, {32.0f, 75.4f, 14.0f, 1.4f, 0.0f, 0.0f, 0.0f, 0.0f}, 0.0f, 0.0f},   // platform dome G
    {0,  6,  0,  73,  1, {4.0f, 48.0f, 60.0f, 77.0f, 0.0f, 0.0f, 0.0f, 0.0f}, 0.0f, 0.0f},   // platform band H
    {0,  7,  0,  74, 26, {4.0f, 6.0f, 60.0f, 60.0f, 8.0f, 8.0f, 8.0f, 8.0f}, 0.0f, 0.0f},   // window bevel outer
    {0,  7,  0, 100, 11, {6.0f, 7.0f, 58.0f, 59.0f, 7.0f, 7.0f, 7.0f, 7.0f}, 0.0f, 0.0f},   // window bevel middle
    {0,  0,  0, 111,  8, {7.0f, 8.0f, 57.0f, 58.0f, 6.0f, 6.0f, 6.0f, 6.0f}, 0.0f, 0.0f},   // window bevel inner
};

static const NdsVecStop kNdsTileStops[] = {
    {57.5f, 240, 240, 240}, {58.5f, 252, 252, 252}, {60.5f, 252, 252, 252}, {61.5f, 240, 240, 240},
    {62.5f, 219, 219, 219}, {0.0f, 255, 255, 255}, {0.0f, 240, 240, 240}, {12.5f, 219, 219, 219},
    {13.5f, 230, 230, 230}, {0.0f, 252, 252, 252},
};
static const NdsVecLayer kNdsTileLayers[] = {
    {0, -1,  0,   0,  5, {8.0f, 50.0f, 56.0f, 63.0f, 0.0f, 0.0f, 5.0f, 5.0f}, 0.0f, 0.0f},   // lip
    {0, -1,  0,   5,  1, {7.0f, 7.0f, 57.0f, 57.0f, 5.0f, 5.0f, 0.5f, 0.5f}, 0.0f, 0.0f},   // body
    {0, -1,  0,   6,  1, {12.0f, 12.0f, 52.0f, 21.0f, 2.0f, 2.0f, 1.5f, 1.5f}, 0.0f, 0.0f},   // label recess
    {0, -1,  0,   7,  2, {13.5f, 12.0f, 50.5f, 14.0f, 1.0f, 1.0f, 1.0f, 1.0f}, 0.0f, 0.0f},   // label top shadow
    {0, -1,  0,   9,  1, {15.0f, 19.0f, 49.0f, 21.0f, 1.0f, 1.0f, 1.0f, 1.0f}, 0.0f, 0.0f},   // label bottom highlight
};

static const NdsVecStop kNdsPointerStops[] = {
    {0.0f, 93, 93, 93}, {0.0f, 140, 140, 140}, {78.0f, 251, 251, 251}, {82.0f, 245, 245, 245},
    {83.5f, 210, 210, 210}, {84.5f, 172, 172, 172}, {86.0f, 150, 150, 150}, {0.0f, 251, 251, 251},
    {0.0f, 130, 130, 130},
};
static const NdsVecLayer kNdsPointerLayers[] = {
    {2, -1, -1,   0,  1, {120.2f, 76.3f, 134.8f, 76.3f, 127.5f, 90.9f, 0.0f, 0.0f}, 88.0f, 0.0f},   // notch outline
    {2, -1, -1,   1,  1, {121.2f, 76.3f, 133.8f, 76.3f, 127.5f, 89.5f, 0.0f, 0.0f}, 87.0f, 0.0f},   // notch mid band
    {2, -1,  0,   2,  5, {122.75f, 76.3f, 132.25f, 76.3f, 127.5f, 86.4f, 0.0f, 0.0f}, 0.0f, 0.0f},   // notch bright face
    {2, -1,  0,   7,  1, {120.6f, 72.6f, 134.4f, 72.6f, 127.5f, 83.2f, 0.0f, 0.0f}, 0.0f, 0.0f},   // tail bright edges
    {2, -1,  0,   8,  1, {123.6f, 72.6f, 131.4f, 72.6f, 127.5f, 80.7f, 0.0f, 0.0f}, 0.0f, 0.0f},   // tail core
};

static const NdsVecHole kNdsDialogHoles[] = {
    {1.5f, -10.0f, 222.5f, 166.0f, 0.0f},
    {2.5f, -10.0f, 221.5f, 166.0f, 0.0f},
    {3.5f, -10.0f, 220.5f, 166.0f, 0.0f},
};
static const NdsVecStop kNdsDialogStops[] = {
    {0.0f, 162, 162, 162}, {0.0f, 195, 195, 195}, {0.0f, 219, 219, 219}, {0.0f, 251, 251, 251},
    {0.0f, 24, 89, 186}, {0.0f, 0, 89, 243}, {0.0f, 40, 121, 251}, {0.0f, 40, 121, 251},
    {0.0f, 24, 89, 186}, {0.0f, 0, 89, 243}, {0.0f, 40, 121, 251}, {0.0f, 40, 121, 251},
};
static const NdsVecLayer kNdsDialogLayers[] = {
    {0,  0,  0,   0,  1, {0.0f, 3.0f, 224.0f, 153.0f, 0.0f, 0.0f, 0.0f, 0.0f}, 0.0f, 0.0f},   // side bevel 162
    {0,  1,  0,   1,  1, {1.0f, 3.0f, 223.0f, 153.0f, 0.0f, 0.0f, 0.0f, 0.0f}, 0.0f, 0.0f},   // side bevel 195
    {0,  2,  0,   2,  1, {2.0f, 3.0f, 222.0f, 153.0f, 0.0f, 0.0f, 0.0f, 0.0f}, 0.0f, 0.0f},   // side bevel 219
    {0, -1,  0,   3,  1, {3.0f, 3.0f, 221.0f, 153.0f, 0.0f, 0.0f, 0.0f, 0.0f}, 0.0f, 0.0f},   // body
    {0, -1,  0,   4,  1, {0.0f, 0.0f, 224.0f, 6.0f, 4.5f, 4.5f, 0.0f, 0.0f}, 0.0f, 0.0f},   // top band outline
    {0, -1,  0,   5,  1, {3.0f, 1.0f, 221.0f, 6.0f, 2.5f, 2.5f, 0.0f, 0.0f}, 0.0f, 0.0f},   // top band
    {0, -1,  0,   6,  1, {5.5f, 2.0f, 218.5f, 3.0f, 0.5f, 0.5f, 0.5f, 0.5f}, 0.0f, 0.0f},   // top stripe
    {0, -1,  0,   7,  1, {4.5f, 4.0f, 219.5f, 5.0f, 0.5f, 0.5f, 0.5f, 0.5f}, 0.0f, 0.0f},   // top stripe
    {0, -1,  0,   8,  1, {0.0f, 150.0f, 224.0f, 156.0f, 0.0f, 0.0f, 4.5f, 4.5f}, 0.0f, 0.0f},   // bottom band outline
    {0, -1,  0,   9,  1, {3.0f, 150.0f, 221.0f, 155.0f, 0.0f, 0.0f, 2.5f, 2.5f}, 0.0f, 0.0f},   // bottom band
    {0, -1,  0,  10,  1, {4.5f, 151.0f, 219.5f, 152.0f, 0.5f, 0.5f, 0.5f, 0.5f}, 0.0f, 0.0f},   // bottom stripe
    {0, -1,  0,  11,  1, {5.5f, 153.0f, 218.5f, 154.0f, 0.5f, 0.5f, 0.5f, 0.5f}, 0.0f, 0.0f},   // bottom stripe
};

// ---------------------------------------------------------------------------
// Layer interpreter
// ---------------------------------------------------------------------------

static constexpr int NDS_VEC_LITERAL      = 0;   // the table colours as they are
static constexpr int NDS_VEC_FRAME_ACCENT = 1;   // the selection frame's accent rotation (ndsFrameTexAccented)
static constexpr int NDS_VEC_GREY         = 2;   // light-firmware greys through ndsChromeGrey (dark variant)
static constexpr int NDS_VEC_TINT         = 3;   // multiplied by a grey tint (the tile pillow's pal.tile)

static void vecRgb2Hsv(float r, float g, float b, float& h, float& s, float& v) {
    float mx = fmaxf(r, fmaxf(g, b)), mn = fminf(r, fminf(g, b)), d = mx - mn;
    v = mx; s = (mx <= 0.0f) ? 0.0f : d / mx;
    if (d <= 1e-6f) { h = 0.0f; return; }
    if (mx == r)      h = 60.0f * fmodf(((g - b) / d), 6.0f);
    else if (mx == g) h = 60.0f * (((b - r) / d) + 2.0f);
    else              h = 60.0f * (((r - g) / d) + 4.0f);
    if (h < 0.0f) h += 360.0f;
}
static void vecHsv2Rgb(float h, float s, float v, float& r, float& g, float& b) {
    h = fmodf(h, 360.0f); if (h < 0.0f) h += 360.0f;
    float c = v * s, x = c * (1.0f - fabsf(fmodf(h / 60.0f, 2.0f) - 1.0f)), m = v - c;
    float rr, gg, bb;
    if      (h <  60.0f) { rr = c; gg = x; bb = 0; }
    else if (h < 120.0f) { rr = x; gg = c; bb = 0; }
    else if (h < 180.0f) { rr = 0; gg = c; bb = x; }
    else if (h < 240.0f) { rr = 0; gg = x; bb = c; }
    else if (h < 300.0f) { rr = x; gg = 0; bb = c; }
    else                 { rr = c; gg = 0; bb = x; }
    r = rr + m; g = gg + m; b = bb + m;
}

// The frame art's dominant hue (the median of its saturated texels, which ndsFrameTexAccented
// measures from the same PNG at runtime), so an accent lands on the same colours as the sprite.
static const float kNdsFrameBaseHue = 218.0f;

// Draw a layer table: each layer is one shape in DS px of the element's cell, mapped to device px
// by (ox + x * sx, oy + y * sy) plus each instance's offset. Instances share the shape, so all of
// them go out in one draw per layer.
void NanoMenu::ndsVecLayers(const NdsVecLayer* L, int n, const NdsVecStop* stops, const NdsVecHole* holes,
                            int colour, float tint, float ox, float oy, float sx, float sy,
                            const float* instX, const float* instY, int nInst, float alpha) {
    if (!mNdsVecChrome || !L || n <= 0 || nInst <= 0 || alpha <= 0.004f) return;
    const float sr = fminf(sx, sy);   // radii follow the smaller axis (the settle squash is not uniform)
    const bool rotate = (colour == NDS_VEC_FRAME_ACCENT) && !ndsAccentIsDefault();
    float accH = 0.0f, sScale = 1.0f;
    if (rotate) {   // the per-texel rotation ndsFrameTexAccented applies to the sprite, per stop here
        static const float kRefBlue[3] = {0.094f, 0.573f, 0.922f};
        float ar, ag, ab; ndsAccentRGB(ar, ag, ab);
        float refH, refS, refV; vecRgb2Hsv(kRefBlue[0], kRefBlue[1], kRefBlue[2], refH, refS, refV);
        float accS, accV;       vecRgb2Hsv(ar, ag, ab, accH, accS, accV);
        sScale = (refS > 1e-3f) ? (accS / refS) : 1.0f;
        if (sScale > 1.15f) sScale = 1.15f;
    }
    const NdsPal pal = ndsPal();
    for (int i = 0; i < n; i++) {
        const NdsVecLayer& l = L[i];
        NdsVecShape s;
        float cx = 0.0f, cy = 0.0f;
        if (l.mode == 0) {
            cx = (l.g[0] + l.g[2]) * 0.5f; cy = (l.g[1] + l.g[3]) * 0.5f;
            s.hw = (l.g[2] - l.g[0]) * 0.5f * sx; s.hh = (l.g[3] - l.g[1]) * 0.5f * sy;
            for (int k = 0; k < 4; k++) s.r[k] = l.g[4 + k] * sr;
        } else if (l.mode == 1) {
            s.mode = 1;
            cx = l.g[0]; cy = l.g[1];
            s.hw = l.g[2] * sx; s.hh = l.g[3] * sy;
        } else {
            s.mode = 2;
            cx = (l.g[0] + l.g[2] + l.g[4]) / 3.0f; cy = (l.g[1] + l.g[3] + l.g[5]) / 3.0f;
            for (int k = 0; k < 3; k++) { s.tri[k * 2] = (l.g[k * 2] - cx) * sx; s.tri[k * 2 + 1] = (l.g[k * 2 + 1] - cy) * sy; }
        }
        if (l.hole >= 0 && holes) {
            const NdsVecHole& h = holes[l.hole];
            s.holeX = ((h.x0 + h.x1) * 0.5f - cx) * sx; s.holeY = ((h.y0 + h.y1) * 0.5f - cy) * sy;
            s.holeHw = (h.x1 - h.x0) * 0.5f * sx;     s.holeHh = (h.y1 - h.y0) * 0.5f * sy;
            s.holeR = h.r * sr;
        }
        if (l.cutSign != 0) {
            s.cutY = (l.cutY - cy) * sy;
            s.cutK = l.cutK * sy / (sx * sx);
            s.cutSign = (float)l.cutSign;
        }
        float ys[32], rgb[32 * 3];
        const int ns = l.nStops < 32 ? l.nStops : 32;
        for (int k = 0; k < ns; k++) {
            const NdsVecStop& st = stops[l.stop0 + k];
            ys[k] = (st.y - cy) * sy;
            float r = st.r / 255.0f, g = st.g / 255.0f, b = st.b / 255.0f;
            if (rotate) {
                float h, sat, v; vecRgb2Hsv(r, g, b, h, sat, v);
                float nsat = sat * sScale; if (nsat > 1.0f) nsat = 1.0f;
                float dh = h - kNdsFrameBaseHue; dh = fmodf(dh + 540.0f, 360.0f) - 180.0f;
                vecHsv2Rgb(accH + dh * 0.35f, nsat, v, r, g, b);
            } else if (colour == NDS_VEC_GREY) {
                r = ndsChromeGrey(r, pal); g = ndsChromeGrey(g, pal); b = ndsChromeGrey(b, pal);
            } else if (colour == NDS_VEC_TINT) {
                r *= tint; g *= tint; b *= tint;
            }
            rgb[k * 3] = fmaxf(0.0f, fminf(1.0f, r));
            rgb[k * 3 + 1] = fmaxf(0.0f, fminf(1.0f, g));
            rgb[k * 3 + 2] = fmaxf(0.0f, fminf(1.0f, b));
        }
        ndsVecBegin(s);
        for (int k = 0; k < nInst; k++)
            ndsVecAdd(ox + (instX ? instX[k] : 0.0f) + cx * sx, oy + (instY ? instY[k] : 0.0f) + cy * sy, ys, rgb, ns, alpha);
        ndsVecEnd();
    }
}

#define NDS_VEC_COUNT(a) ((int)(sizeof(a) / sizeof((a)[0])))

// The selection frame (64x80 cell) into the device rect it occupies; a squashed rect (the
// select-landing settle) scales the shapes the same way the sprite was stretched.
void NanoMenu::drawNdsVecFrame(float x, float y, float w, float h, float alpha) {
    ndsVecLayers(kNdsFrameLayers, NDS_VEC_COUNT(kNdsFrameLayers), kNdsFrameStops, kNdsFrameHoles,
                 NDS_VEC_FRAME_ACCENT, 1.0f, x, y, w / 64.0f, h / 80.0f, nullptr, nullptr, 1, alpha);
}

// Carousel tile pillows (64x64 cells): x/y are each cell's top-left in device px. The pillows
// never overlap one another, so the whole row goes out layer by layer.
void NanoMenu::drawNdsVecTiles(const float* x, const float* y, int n, float scale, float tint) {
    ndsVecLayers(kNdsTileLayers, NDS_VEC_COUNT(kNdsTileLayers), kNdsTileStops, nullptr,
                 NDS_VEC_TINT, tint, 0.0f, 0.0f, scale, scale, x, y, n, 1.0f);
}

// Balloon tail + frame notch. The table is in bottom-screen DS coordinates, so ox/oy is where
// DS (0, 0) lands in device px. The five triangles overlap, so each one drawn translucent would
// show the ones under it while the chrome fades in. The pointer is therefore all or nothing: it
// appears whole at full alpha once the fade is done (the name box above it fades up over ~8
// frames), and is skipped before that.
void NanoMenu::drawNdsVecPointer(float ox, float oy, float scale, float alpha) {
    if (alpha < 0.98f) return;
    ndsVecLayers(kNdsPointerLayers, NDS_VEC_COUNT(kNdsPointerLayers), kNdsPointerStops, nullptr,
                 NDS_VEC_GREY, 1.0f, ox, oy, scale, scale, nullptr, nullptr, 1, 1.0f);
}

// The light theme's blue message box (dialog_box_blue, a 224x156 cell) into its device rect.
void NanoMenu::drawNdsVecDialogBox(float x, float y, float w, float h) {
    ndsVecLayers(kNdsDialogLayers, NDS_VEC_COUNT(kNdsDialogLayers), kNdsDialogStops, kNdsDialogHoles,
                 NDS_VEC_LITERAL, 1.0f, x, y, w / 224.0f, h / 156.0f, nullptr, nullptr, 1, 1.0f);
}

// Carousel scrollbar ticks. bevelX are the left edges (device px) of the 4x4 bevelled squares,
// flatX / anchorX the left edges of the first two ticks (nullptr = not drawn); y180 is DS row 180
// and ux / uy one DS px across / down. The colours are the pixel version's.
void NanoMenu::drawNdsVecTicks(const float* bevelX, int nBevel, const float* flatX, const float* anchorX,
                               float y180, float ux, float uy, bool dark) {
    const float u = fminf(ux, uy);
    const float tkTop[3] = {dark ? 0.62f : 0.510f, dark ? 0.66f : 0.541f, dark ? 0.62f : 0.510f};
    const float tkEd[3]  = {dark ? 0.50f : 0.349f, dark ? 0.55f : 0.412f, dark ? 0.50f : 0.349f};
    const float zero = 0.0f;
    if (nBevel > 0 && bevelX) {
        // Dark square, then the light top row and the light middle of the second row over it.
        NdsVecShape base; base.hw = 2.0f * ux; base.hh = 2.0f * uy;
        for (int k = 0; k < 4; k++) base.r[k] = 0.8f * u;
        ndsVecBegin(base);
        for (int i = 0; i < nBevel; i++) ndsVecAdd(bevelX[i] + 2.0f * ux, y180 + 2.0f * uy, &zero, tkEd, 1, 1.0f);
        ndsVecEnd();
        NdsVecShape top; top.hw = 2.0f * ux; top.hh = 0.625f * uy;
        top.r[0] = top.r[1] = 0.8f * u;
        ndsVecBegin(top);
        for (int i = 0; i < nBevel; i++) ndsVecAdd(bevelX[i] + 2.0f * ux, y180 + 0.625f * uy, &zero, tkTop, 1, 1.0f);
        ndsVecEnd();
        NdsVecShape mid; mid.hw = 1.0f * ux; mid.hh = 1.0f * uy;
        mid.r[2] = mid.r[3] = 0.6f * u;
        ndsVecBegin(mid);
        for (int i = 0; i < nBevel; i++) ndsVecAdd(bevelX[i] + 2.0f * ux, y180 + 1.0f * uy, &zero, tkTop, 1, 1.0f);
        ndsVecEnd();
    }
    if (flatX) {   // flat grey block, DS rows 179..184
        const float v = dark ? 0.62f : 0.510f, rgb[3] = {v, v, v};
        const float rad[4] = {0.8f * u, 0.8f * u, 0.8f * u, 0.8f * u};
        drawNdsVecRect(*flatX, y180 - uy, 4.0f * ux, 6.0f * uy, rad, &zero, rgb, 1, 1.0f);
    }
    if (anchorX) {   // the first card's dark dot, lit from the top like the 4x4 firmware dot
        float lo = 0.137f, hi = 0.380f;
        if (dark) { lo = 0.40f + lo * 0.40f; hi = 0.40f + hi * 0.40f; }
        const float ys[2] = {y180 + 0.5f * uy, y180 + 3.5f * uy};
        const float rgb[6] = {hi, hi, hi, lo, lo, lo};
        const float rad[4] = {2.0f * u, 2.0f * u, 2.0f * u, 2.0f * u};
        drawNdsVecRect(*anchorX, y180, 4.0f * ux, 4.0f * uy, rad, ys, rgb, 2, 1.0f);
    }
}

}  // namespace android
