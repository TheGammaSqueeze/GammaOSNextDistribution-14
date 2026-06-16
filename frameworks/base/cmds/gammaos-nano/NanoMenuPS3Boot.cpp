/*
 * GammaOS Nano - PS3 XMB cold-boot intro.
 *
 * A 1:1 port of the web app's cold-boot sequence (index.html boot timeline):
 * the wave/gradient revealing from black, the white PS3 logo + footer plate
 * appearing with an L->R wipe and fading out, the photosensitivity-epilepsy
 * warning, and finally the hand-off to the live XMB where the category icons /
 * item list / clock fade and pop in.
 *
 * Design notes:
 *  - The whole sequence is a pure function of a monotonic boot clock
 *    (mPs3BootElapsedMs), accumulated from the CLAMPED per-frame dt - never
 *    mEffectTime (which wraps at 500s) nor wall-clock.
 *  - The steady wave/gradient pipeline (ps3bg) is left untouched except for one
 *    safe hook: ps3bg::setBootWaveBrightness() scales uFade so the wave emerges
 *    from black (1.0 = steady, restored when the intro ends). The gradient/scene
 *    reveal is done here with a fading black overlay so no shader path changes.
 *  - The warning backdrop blur reuses the proven captureGlass/drawFrostedGlass
 *    chain, captured once and held (frozen) for the whole warning.
 */

#include "NanoMenu.h"
#include "NanoMenuPS3.h"
#include "NanoMenuPS3Bg.h"
#include "NanoMenuDrm.h"   // sDrmGlRotation / sDrmRotationDeg for the logo wipe scissor
#include "NanoI18n.h"      // trDyn() resource-file translations

#include <math.h>
#include <string.h>
#include <string>
#include <vector>

#include <GLES2/gl2.h>
#include <cutils/properties.h>
#include <utils/Log.h>

namespace android {

// ---- timeline constants (ms), from the web boot timeline ----
static const double BOOT_WAVE_IN_A   = 800.0;    // wave brightness ramp start
static const double BOOT_WAVE_IN_B   = 2600.0;   // wave brightness full
static const double BOOT_SCENE_A     = 800.0;    // scene reveal (black wash recedes)
static const double BOOT_SCENE_B     = 4500.0;
static const double BOOT_LOGO_IN_A   = 2200.0;   // logo wipe in
static const double BOOT_LOGO_IN_B   = 4000.0;
static const double BOOT_LOGO_HOLD_B = 5800.0;   // logo full hold end
static const double BOOT_LOGO_OUT_B  = 6800.0;   // logo gone
static const double BOOT_WARN_BLUR_A = 7115.0;   // warning backdrop blur in
static const double BOOT_WARN_BLUR_B = 7415.0;
static const double BOOT_WARN_IN     = 7665.0;   // warning text hard cut in
static const double BOOT_WARN_OUT    = 12530.0;  // warning text hard cut out
static const double BOOT_WARN_BLUROUT_A = 12650.0;
static const double BOOT_WARN_BLUROUT_B = 12870.0;
static const double BOOT_UI_IN_MS    = 15900.0;  // un-suppress the XMB UI
static const double BOOT_LABEL_A     = 15900.0;  // category label reveal
static const double BOOT_LABEL_B     = 16350.0;
static const double BOOT_ICON_A      = 16000.0;  // category icon / item / clock reveal
static const double BOOT_ICON_B      = 16900.0;
static const double BOOT_SEQ_END_MS  = 16900.0;  // hand-off complete

static const float EDGE_W = 1.6f;                // logo wipe soft-edge lead

static const char* kWarnTitle = "PHOTOSENSITIVE EPILEPSY";
static const char* kWarnBody =
    "IF YOU HAVE A HISTORY OF EPILEPSY OR SEIZURES, CONSULT A DOCTOR BEFORE USE. "
    "CERTAIN PATTERNS MAY TRIGGER SEIZURES WITH NO PRIOR HISTORY. BEFORE USING "
    "THIS PRODUCT, CAREFULLY READ THE INSTRUCTION MANUAL.";

static inline float bootRamp(double e, double a, double b) {
    if (b <= a) return e >= b ? 1.0f : 0.0f;
    float t = (float)((e - a) / (b - a));
    if (t < 0.0f) t = 0.0f; if (t > 1.0f) t = 1.0f;
    return t;
}
static inline float smooth01(float u) { return u * u * (3.0f - 2.0f * u); }

// ---------------------------------------------------------------------------
// lifecycle
// ---------------------------------------------------------------------------
void NanoMenu::ps3BootReset(bool freshSetup) {
    mPs3BootElapsedMs = 0.0;
    mPs3BootActive = true;
    mPs3BootWizardAfter = freshSetup;
    mPs3BootLabelReveal = 0.0f;
    mPs3BootIconReveal = 0.0f;
    ps3bg::setBootWaveBrightness(0.0f);
}

void NanoMenu::ps3BootSkip() {
    // Jump to the end of the sequence; the next update() snaps everything steady.
    mPs3BootElapsedMs = BOOT_SEQ_END_MS;
}

// Test hook (nav-hook token "bootreplay"): re-run the whole cold-boot intro from
// t=0 so the sequence can be verified 1:1 against the web without a real reboot.
// The logo/footer plates are freed once the first boot completes, so force a
// reload; the warning backdrop blur is captured live each frame and needs no reset.
void NanoMenu::ps3BootReplay() {
    mPs3BootPlatesLoaded = false;
    mPs3BootLogoTex = 0;
    mPs3BootFooterTex = 0;
    ps3BootReset(false);
}

// Advance the boot clock and recompute the cross-module reveal values + the
// wave-brightness hook. Returns true while the XMB UI must stay suppressed.
bool NanoMenu::ps3BootUpdate(float dtSeconds) {
    if (!mPs3BootActive) return false;
    if (dtSeconds < 0.0f) dtSeconds = 0.0f;
    if (dtSeconds > 0.1f) dtSeconds = 0.1f;
    mPs3BootElapsedMs += (double)dtSeconds * 1000.0;
    double e = mPs3BootElapsedMs;

    // Fresh-setup boots end the intro right after the epilepsy warning fades out
    // and hand straight to the setup wizard - the XMB category/item icon reveal is
    // skipped so the wizard is never preceded by a flash of the live menu.
    double endMs = mPs3BootWizardAfter ? BOOT_WARN_BLUROUT_B : BOOT_SEQ_END_MS;
    if (e >= endMs) {
        // Hand-off complete: snap everything to the steady state.
        mPs3BootLabelReveal = 1.0f;
        mPs3BootIconReveal = 1.0f;
        ps3bg::setBootWaveBrightness(1.0f);
        mPs3BootActive = false;
        // The cold-boot logo/footer plates are only drawn by renderPs3BootOverlay
        // during this intro and never again this process lifetime. Free them now
        // (700x350 RGBA x2 ~= 1.9 MB of otherwise-mlocked GPU memory). We are on
        // the render thread (renderPs3Xmb -> ps3BootUpdate) so the GL context is
        // current. Leave mPs3BootPlatesLoaded true so they are never reloaded.
        if (mPs3BootLogoTex)   { glDeleteTextures(1, &mPs3BootLogoTex);   mPs3BootLogoTex = 0; }
        if (mPs3BootFooterTex) { glDeleteTextures(1, &mPs3BootFooterTex); mPs3BootFooterTex = 0; }
        return false;
    }

    ps3bg::setBootWaveBrightness(smooth01(bootRamp(e, BOOT_WAVE_IN_A, BOOT_WAVE_IN_B)));
    mPs3BootLabelReveal = smooth01(bootRamp(e, BOOT_LABEL_A, BOOT_LABEL_B));
    mPs3BootIconReveal  = smooth01(bootRamp(e, BOOT_ICON_A, BOOT_ICON_B));

    return e < BOOT_UI_IN_MS;   // suppress the XMB UI until the staged reveal
}

// (loadPs3BootPlate is implemented in NanoMenuPS3Icons.cpp where decodeRGBA lives)

// ---------------------------------------------------------------------------
// overlay render (drawn on top of the composited wave/gradient, primary pass)
// ---------------------------------------------------------------------------
void NanoMenu::renderPs3BootOverlay() {
    // Make sure the responsive layout globals are current (the menu, which
    // normally computes them, is suppressed during boot).
    { ps3::LayoutParams lp; lp.panelW = mWidth; lp.panelH = mHeight; lp.uiScale = mPs3UiScale;
      ps3::layoutCompute(lp); }

    if (!mPs3BootPlatesLoaded) {
        mPs3BootLogoTex   = loadPs3BootPlate("logo_white.png");
        mPs3BootFooterTex = loadPs3BootPlate("footer_white.png");
        mPs3BootPlatesLoaded = true;
    }

    double e = mPs3BootElapsedMs;
    const float fx = ps3::gFrameX, fy = ps3::gFrameY, fw = ps3::gFrameW, fh = ps3::gFrameH;
    // Visual-down drop shadow: rotate the device-down vector by sDrmRotMat so the
    // shadow falls toward the bottom of the panel on any orientation (matches the
    // menu's ps3ShadowOffset; on the 180-degree Brick this is {0,-s}).
    float ss = ps3::devS(1.5f);
    float so[2] = { sDrmRotMat[2] * ss, sDrmRotMat[3] * ss };

    // ---- 1. scene reveal: black wash receding (gradient emerges from black) ----
    float sceneReveal = smooth01(bootRamp(e, BOOT_SCENE_A, BOOT_SCENE_B));
    float blackA = 1.0f - sceneReveal;
    if (blackA > 0.001f)
        drawQuad(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f, 0.0f, 0.0f, blackA);

    // ---- 2. logo + footer white plates (2200..6800) ----
    if (e >= BOOT_LOGO_IN_A && e < BOOT_LOGO_OUT_B) {
        float mainA;
        if (e < BOOT_LOGO_IN_B)        { float u = bootRamp(e, BOOT_LOGO_IN_A, BOOT_LOGO_IN_B); mainA = u * u; }
        else if (e < BOOT_LOGO_HOLD_B) mainA = 1.0f;
        else                           mainA = 1.0f - smooth01(bootRamp(e, BOOT_LOGO_HOLD_B, BOOT_LOGO_OUT_B));

        // logo: native 700x350, dW = 0.365*fw, centred at (0.734fw, 0.532fh).
        float dW = 0.365f * fw, dH = dW * 0.5f;
        float lcx = fx + 0.734f * fw, lcy = fy + 0.532f * fh;
        float lx = lcx - dW * 0.5f, ly = lcy - dH * 0.5f;

        // L->R wipe during the in-phase only (settled full after). A scissor on a
        // full-height band clips the revealed left fraction (rotation-aware).
        bool wipe = (e < BOOT_LOGO_IN_B);
        bool scissorOn = false;
        if (wipe) {
            float revealU = bootRamp(e, BOOT_LOGO_IN_A, BOOT_LOGO_IN_B);
            float front = revealU * (1.0f + EDGE_W);
            if (front > 1.0f) front = 1.0f;
            int rlx = (int)lx, rly = 0, rlw = (int)(dW * front), rlh = (int)mHeight;
            if (rlw < 1) rlw = 1;
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
        if (mPs3BootLogoTex) {
            drawIconTex(mPs3BootLogoTex, lx + so[0], ly + so[1], dW, dH, 0.0f, 0.0f, 0.0f, 0.45f * mainA);
            drawIconTex(mPs3BootLogoTex, lx, ly, dW, dH, 1.0f, 1.0f, 1.0f, mainA);
        }
        // The footer plate (the "PLAYSTATION 3" wordmark) is a SAME-SIZE 700x350
        // overlay drawn at the SAME rect as the logo - its text is positioned
        // within its own canvas to sit under the PS3 mark - and rides the same
        // wipe. (Web: drawImage(LOGO, dx,dy,dW,dH); drawImage(FOOTER, dx,dy,dW,dH).)
        if (mPs3BootFooterTex) {
            drawIconTex(mPs3BootFooterTex, lx + so[0], ly + so[1], dW, dH, 0.0f, 0.0f, 0.0f, 0.45f * mainA);
            drawIconTex(mPs3BootFooterTex, lx, ly, dW, dH, 1.0f, 1.0f, 1.0f, mainA);
        }
        if (scissorOn) glDisable(GL_SCISSOR_TEST);
    }

    // ---- 3. epilepsy warning backdrop blur + text ----
    if (e >= BOOT_WARN_BLUR_A && e < BOOT_WARN_BLUROUT_B) {
        // Backdrop behind the warning text. When the WAVE is the wallpaper, blur its
        // work-texture (captureGlassFromWave reads ps3bg::workTex; the full-framebuffer
        // captureGlass path renders BLACK here because the freshly drawn scene is not
        // yet resolved to the FBO when glCopyTexSubImage2D runs during the DRM-direct
        // boot, while workTex is its own resolved FBO). For ANY OTHER wallpaper the
        // selected effect is already drawn behind us but has no resolved offscreen
        // texture to blur mid-boot, so dim it with a scrim instead - that shows the
        // user's chosen wallpaper (not the wave) and keeps the white text readable.
        float warnBlur = smooth01(bootRamp(e, BOOT_WARN_BLUR_A, BOOT_WARN_BLUR_B))
                       * (1.0f - smooth01(bootRamp(e, BOOT_WARN_BLUROUT_A, BOOT_WARN_BLUROUT_B)));
        if (warnBlur > 0.001f) {
            if (mCurrentEffect == 22 && captureGlassFromWave())
                drawFrostedGlass(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f,
                                 0.90f, 0.90f, 0.92f, 1.0f, warnBlur, /*waveSpace=*/true);
            else
                drawQuad(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f, 0.0f, 0.0f, 0.55f * warnBlur);
        }
    }

    if (e >= BOOT_WARN_IN && e < BOOT_WARN_OUT) {
        float sy = fh / 1080.0f;
        float titleS = ps3::fontScale(30.0f);
        float bodyS  = ps3::fontScale(24.0f);
        float pitch  = 34.0f * sy;
        float wrapW  = fw * (1280.0f / 1920.0f);

        // Localised title/body (static English literals are translation keys).
        const char* warnTitle = trDyn(kWarnTitle);
        const char* warnBody  = trDyn(kWarnBody);

        // word-wrap the body to wrapW
        std::vector<std::string> lines;
        std::string cur, word;
        auto commit = [&](bool last) {
            if (word.empty()) { if (last && !cur.empty()) lines.push_back(cur); return; }
            std::string trial = cur.empty() ? word : cur + " " + word;
            if (!cur.empty() && measureText(trial.c_str(), bodyS) > wrapW) { lines.push_back(cur); cur = word; }
            else cur = trial;
            word.clear();
            if (last && !cur.empty()) lines.push_back(cur);
        };
        for (const char* p = warnBody; ; ++p) {
            if (*p == ' ' || *p == '\0') { commit(*p == '\0'); if (*p == '\0') break; }
            else word.push_back(*p);
        }

        float titleW = measureText(warnTitle, titleS);
        float maxW = titleW;
        for (auto& l : lines) { float w = measureText(l.c_str(), bodyS); if (w > maxW) maxW = w; }
        float blockX = fx + (fw - maxW) * 0.5f;
        int totalLines = 1 + 1 + (int)lines.size();   // title + gap + body
        float totalH = totalLines * pitch;
        float baseY = fy + (fh - totalH) * 0.5f;

        auto line = [&](const char* t, float scale, float yTop) {
            drawText(t, blockX + so[0], yTop + so[1], scale, 0.0f, 0.0f, 0.0f, 0.55f);
            drawText(t, blockX, yTop, scale, 1.0f, 1.0f, 1.0f, 1.0f);
        };
        float y = baseY;
        line(warnTitle, titleS, y); y += pitch * 2.0f;   // title + blank line
        for (auto& l : lines) { line(l.c_str(), bodyS, y); y += pitch; }
    }
}

} // namespace android
