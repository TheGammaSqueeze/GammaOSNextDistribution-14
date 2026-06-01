// Host test for oskComputeBox geometry across aspect ratios (incl. portrait).
// Verifies the panel + keyboard + footer fit on-screen and inside the panel.
//   g++ -std=c++17 -I ../../frameworks/base/cmds/gammaos-nano test_geometry.cpp \
//       -o /tmp/osk_geo && /tmp/osk_geo

#include <cstdio>
#include <cmath>
#include "NanoOsk.h"
using namespace android;

static const int   FONT_CHAR_H = 16;
static const float kBoxAspect  = 2.488372f;
static const float kKeyFrac    = 0.065421f;

// ---- verbatim copy of NanoOsk.cpp oskComputeBox ----
static OskBox oskComputeBox(int W, int H, float actLabelPx) {
    OskBox b{};
    float sf = fminf((float)W / 1080.0f, (float)H / 720.0f);
    if (sf < 0.5f) sf = 0.5f;
    b.sf = sf;
    float aspect = (H > 0) ? (float)W / (float)H : 1.6f;
    b.portrait = aspect < 0.85f;
    b.actBelow = b.portrait;
    float wFrac = (aspect >= 1.6f) ? 0.78f : (aspect >= 1.2f) ? 0.86f
                : (aspect >= 0.85f) ? 0.90f : 0.96f;
    float gap = 12.0f * sf, actPadH = 16.0f * sf;
    float previewH = FONT_CHAR_H * 2.0f * sf + 10.0f * sf;
    float footerH  = FONT_CHAR_H * 1.4f * sf + 6.0f * sf;
    float bottomPad = 16.0f * sf, panelPad = 10.0f * sf;
    b.actW = actLabelPx + 2.0f * actPadH;
    b.actH = 36.0f * sf;
    float availW = (float)W * wFrac;
    float kbW = b.actBelow ? availW : (availW - b.actW - gap);
    float keyPx = kbW * kKeyFrac;
    float minKeyPx = 22.0f * sf, maxKeyPx = 64.0f * sf;
    if (keyPx > maxKeyPx) kbW = maxKeyPx / kKeyFrac;
    else if (keyPx < minKeyPx) {
        kbW = minKeyPx / kKeyFrac;
        float capW = (float)W * 0.98f - (b.actBelow ? 0.0f : (b.actW + gap));
        if (kbW > capW) kbW = capW;
    }
    float kbH = kbW / kBoxAspect;
    float rowGap = 8.0f * sf;
    float extra = b.actBelow ? (gap + b.actH) : 0.0f;
    float innerH = previewH + rowGap + kbH + extra + rowGap + footerH;
    float budget = (b.portrait ? 0.85f : 0.66f) * (float)H - 2.0f * panelPad;
    if (innerH > budget && innerH > 0.0f) {
        float scale = budget / innerH; kbW *= scale; kbH *= scale;
        innerH = previewH + rowGap + kbH + extra + rowGap + footerH;
    }
    b.keyPx = kbW * kKeyFrac; b.kbW = kbW; b.kbH = kbH;
    float blockW = b.actBelow ? kbW : (kbW + gap + b.actW);
    b.panelW = blockW + 2.0f * panelPad;
    b.panelH = innerH + 2.0f * panelPad;
    b.panelX = ((float)W - b.panelW) / 2.0f;
    b.panelY = (float)H - bottomPad - b.panelH;
    if (b.panelY < 4.0f * sf) b.panelY = 4.0f * sf;
    b.kbX = ((float)W - blockW) / 2.0f;
    b.previewX = b.kbX;
    b.previewY = b.panelY + panelPad;
    b.kbY = b.previewY + previewH + rowGap;
    if (!b.actBelow) {
        b.actX = b.kbX + kbW + gap; b.actY = b.kbY + kbH / 2.0f - b.actH / 2.0f;
        b.footerY = b.kbY + kbH + rowGap;
    } else {
        b.actX = ((float)W - b.actW) / 2.0f; b.actY = b.kbY + kbH + gap;
        b.footerY = b.actY + b.actH + rowGap;
    }
    return b;
}
// ----------------------------------------------------

static int fails = 0;
static void chk(const char* name, int W, int H) {
    OskBox b = oskComputeBox(W, H, 90.0f);
    bool ok = true;
    const char* why = "";
    auto fail = [&](const char* m){ ok = false; why = m; };
    if (b.panelX < -0.5f) fail("panelX<0");
    if (b.panelY < -0.5f) fail("panelY<0");
    if (b.panelX + b.panelW > W + 0.5f) fail("panel right overflow");
    if (b.panelY + b.panelH > H + 0.5f) fail("panel bottom overflow");
    if (b.kbX < b.panelX - 0.5f) fail("kb left of panel");
    if (b.kbY < b.panelY - 0.5f) fail("kb above panel");
    if (b.kbY + b.kbH > b.panelY + b.panelH + 0.5f) fail("kb below panel");
    if (b.footerY + 16.0f * b.sf > b.panelY + b.panelH + 2.0f) fail("footer below panel");
    if (b.keyPx < 18.0f * b.sf) fail("keys too small");
    bool wantPortrait = ((float)W / H) < 0.85f;
    if (b.portrait != wantPortrait) fail("portrait flag wrong");
    if (b.actBelow != wantPortrait) fail("actBelow wrong");
    // action button on-screen
    if (b.actX < -0.5f || b.actX + b.actW > W + 0.5f) fail("action off-screen x");
    if (b.actY + b.actH > b.panelY + b.panelH + 0.5f) fail("action below panel");
    printf("%s %-14s %4dx%-4d  panel=%.0fx%.0f@(%.0f,%.0f) key=%.0fpx %s%s%s\n",
           ok ? "ok  " : "FAIL", name, W, H, b.panelW, b.panelH, b.panelX, b.panelY,
           b.keyPx, b.portrait ? "[portrait]" : "[landscape]",
           ok ? "" : "  <-- ", why);
    if (!ok) fails++;
}

int main() {
    chk("16:9 wide",   1280, 720);
    chk("16:10",       1280, 800);
    chk("3:2",         1080, 720);
    chk("4:3 Brick",   1024, 768);
    chk("5:4",         1280, 1024);
    chk("1:1 square",   720, 720);
    chk("portrait 3:4", 768, 1024);
    chk("portrait 9:16",720, 1280);
    chk("portrait tall", 600, 1024);
    chk("tiny 480p",    640, 480);
    printf("\n%s (%d failures)\n", fails == 0 ? "ALL PASS" : "FAILURES", fails);
    return fails ? 1 : 0;
}
