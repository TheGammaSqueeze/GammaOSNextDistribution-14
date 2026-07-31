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

// Rendering primitives and the main frame composer. Contains:
//   - PNG decode + icon texture loading (file + embedded fallbacks)
//   - drawIcon / drawQuad
//   - FreeType font init + glyph atlas caching
//   - measureText / drawText (batched, rotation-aware)
//   - setupSecondaryEglSurfaces (post-boot wallpaper surfaces)
//   - render() — per-frame orchestrator (drastic QR passes, XMB, text menu,
//     brightness/volume HUDs, DRM/HWC present, secondary EGL swap)
//
// Extracted from NanoMenu.cpp — behavior unchanged.

#define LOG_TAG "GammaOSNano"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <setjmp.h>
#include <unistd.h>
#include <vector>
#include <thread>   // render-thread watchdog
#include <string>

#include <android-base/properties.h>
#include <cutils/properties.h>
#include <utils/Log.h>
#include <utils/SystemClock.h>

#include <gui/DisplayCaptureArgs.h>
#include <gui/SyncScreenCaptureListener.h>
#include <ui/GraphicBuffer.h>
#include <ui/DisplayMode.h>
#include <ui/DisplayState.h>
#include <ui/LayerStack.h>
#include <ui/PixelFormat.h>
#include <ui/Rect.h>

#include <gui/Surface.h>
#include <gui/SurfaceComposerClient.h>

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <png.h>

#include "DrasticRunner.h"
#include "NanoMenu.h"
#include "NanoI18n.h"      // trDyn() runtime translation of hardcoded UI strings
#include "NanoMenuStrings.h"  // NanoLocale / LocaleInfo / nanoGetLocaleInfo (DSi language picker)
#include "NanoMenuDrm.h"
#include "NanoMenuShaders.h"
#include "NanoMenuPS3.h"
#include "NanoMenuPS3Bg.h"
#include "NanoMenuSbIcons.h"   // embedded status-bar glyph PNGs (framework SystemUI vectors)
#include "xmb_icons.h"

namespace android {

using ui::DisplayMode;

// Alpha for the SECONDARY (bottom) panel clear. In an in-game overlay (an app is running,
// no overlay wallpaper) the primary panel clears to a BLACK scrim baked into the alpha
// (persist.gammaos.nano.overlay.dim, default 0.90) so SurfaceFlinger shows the live app at
// (1-dim) through the translucent layer. The secondary panel must use the SAME scrim alpha,
// otherwise it clears to opaque black and hides the app instead of dimming it (the DSi and
// Minima bottom panels rely on this clear for their in-game scrim). Opaque (1.0) at the home
// or over an overlay wallpaper.
static float nanoSecondaryClearAlpha(bool overlayMode, bool overlayWallpaper) {
    if (!overlayMode || overlayWallpaper) return 1.0f;
    static float sSecDim = -1.0f;
    if (sSecDim < 0.0f) {
        char d[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.overlay.dim", d, "0.90");
        sSecDim = (float)atof(d);
        if (sSecDim < 0.0f) sSecDim = 0.0f;
        if (sSecDim > 1.0f) sSecDim = 1.0f;
    }
    return sSecDim;
}

// ---------------------------------------------------------------------------
// Icon texture rendering (monochrome 32x32 icons, tinted at draw time)
// ---------------------------------------------------------------------------

// Map system index to RetroArch XMB monochrome icon filename
// Order MUST match kXmbSystemDefs (in NanoMenuXmb.cpp): NES,SNES,GB,GBC,GBA,N64,NDS,GEN,SMS,GG,PSX,PSP,DC,NGP,P8,history,<game slot has no file>,setting
static const char* kIconPngNames[19] = {
    "Nintendo - Nintendo Entertainment System.png",       // 0: NES
    "Nintendo - Super Nintendo Entertainment System.png", // 1: SNES
    "Nintendo - Game Boy.png",                            // 2: GB
    "Nintendo - Game Boy Color.png",                      // 3: GBC
    "Nintendo - Game Boy Advance.png",                    // 4: GBA
    "Nintendo - Nintendo 64.png",                         // 5: N64
    "Nintendo - Nintendo DS.png",                         // 6: NDS
    "Sega - Mega Drive - Genesis.png",                    // 7: Genesis
    "Sega - Master System - Mark III.png",                // 8: Master System
    "Sega - Game Gear.png",                               // 9: Game Gear
    "Sony - PlayStation.png",                             // 10: PSX
    "Sony - PlayStation Portable.png",                    // 11: PSP
    "Sega - Dreamcast.png",                               // 12: Dreamcast
    "SNK - Neo Geo Pocket Color.png",                     // 13: NGP
    "PICO-8.png",                                         // 14: PICO-8
    "history.png",                                        // 15: Recently Played
    nullptr,                                              // 16: generic game cartridge (embedded only)
    "setting.png",                                        // 17: Settings column
    nullptr,                                              // 18: Applications app-grid (embedded only)
};

static const char* kIconPngDir = "/data/system/nano_icons";

// Decode PNG pixel data from any source.
// If monoWhite is true, forces RGB to white and uses alpha for shape (monochrome icons).
// If monoWhite is false, preserves original RGBA colors (colored icons like PICO-8).
static bool decodePngToRGBA(png_structp png, png_infop info,
                            int* outW, int* outH, std::vector<uint8_t>* outPixels,
                            bool monoWhite = true) {
    int width = png_get_image_width(png, info);
    int height = png_get_image_height(png, info);
    png_byte colorType = png_get_color_type(png, info);
    png_byte bitDepth = png_get_bit_depth(png, info);

    if (colorType == PNG_COLOR_TYPE_PALETTE) png_set_palette_to_rgb(png);
    if (colorType == PNG_COLOR_TYPE_GRAY && bitDepth < 8) png_set_expand_gray_1_2_4_to_8(png);
    if (colorType == PNG_COLOR_TYPE_GRAY || colorType == PNG_COLOR_TYPE_GRAY_ALPHA)
        png_set_gray_to_rgb(png);
    if (bitDepth == 16) png_set_strip_16(png);
    bool hasTrns = png_get_valid(png, info, PNG_INFO_tRNS) != 0;
    if (hasTrns) png_set_tRNS_to_alpha(png);
    bool hasAlpha = (colorType & PNG_COLOR_MASK_ALPHA) || hasTrns;
    if (!hasAlpha) png_set_filler(png, 0xFF, PNG_FILLER_AFTER);
    png_read_update_info(png, info);

    outPixels->resize(width * height * 4);
    std::vector<png_bytep> rows(height);
    for (int y = 0; y < height; y++)
        rows[y] = outPixels->data() + y * width * 4;
    png_read_image(png, rows.data());

    if (monoWhite) {
        // White + alpha: preserve alpha, set RGB=255
        for (int p = 0; p < width * height; p++) {
            (*outPixels)[p * 4 + 0] = 255;
            (*outPixels)[p * 4 + 1] = 255;
            (*outPixels)[p * 4 + 2] = 255;
        }
    }
    *outW = width;
    *outH = height;
    return true;
}

// Box-average downscale of an RGBA image to (dw x dh). Used to right-size icon
// textures to the panel before upload (the source art is 256/512 but is drawn
// far smaller on a 1024x768 panel).
static void boxDownscaleRGBA(const uint8_t* src, int sw, int sh,
                             int dw, int dh, std::vector<uint8_t>& dst) {
    dst.resize((size_t)dw * dh * 4);
    for (int y = 0; y < dh; y++) {
        int sy0 = y * sh / dh, sy1 = (y + 1) * sh / dh; if (sy1 <= sy0) sy1 = sy0 + 1;
        for (int x = 0; x < dw; x++) {
            int sx0 = x * sw / dw, sx1 = (x + 1) * sw / dw; if (sx1 <= sx0) sx1 = sx0 + 1;
            uint32_t r = 0, g = 0, b = 0, a = 0, cnt = 0;
            for (int yy = sy0; yy < sy1 && yy < sh; yy++)
                for (int xx = sx0; xx < sx1 && xx < sw; xx++) {
                    const uint8_t* p = src + ((size_t)yy * sw + xx) * 4;
                    r += p[0]; g += p[1]; b += p[2]; a += p[3]; cnt++;
                }
            uint8_t* d = dst.data() + ((size_t)y * dw + x) * 4;
            if (cnt) { d[0] = r / cnt; d[1] = g / cnt; d[2] = b / cnt; d[3] = a / cnt; }
            else     { d[0] = d[1] = d[2] = 0; d[3] = 255; }
        }
    }
}

// Upload decoded RGBA pixels as a GL texture with mipmaps. When maxSize > 0 and
// the source exceeds it, the image is box-downscaled to fit maxSize first (the
// caller sizes maxSize from the panel resolution via ps3::iconTexCap, so the
// texture is right-sized per screen with no perceptible quality loss).
static GLuint createIconTexture(const uint8_t* pixels, int width, int height,
                                int maxSize = 0) {
    std::vector<uint8_t> scaled;
    if (maxSize > 0 && (width > maxSize || height > maxSize)) {
        int dw = width, dh = height;
        if (width >= height) { dw = maxSize; dh = (int)((long)height * maxSize / width); }
        else                 { dh = maxSize; dw = (int)((long)width * maxSize / height); }
        if (dw < 1) dw = 1; if (dh < 1) dh = 1;
        boxDownscaleRGBA(pixels, width, height, dw, dh, scaled);
        pixels = scaled.data(); width = dw; height = dh;
    }
    GLuint tex;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    glGenerateMipmap(GL_TEXTURE_2D);
    return tex;
}

// Load a PNG as RGBA texture from file. Returns true on success.
static bool loadPngAsAlphaTexture(const char* path, GLuint* outTex, bool monoWhite = true,
                                  int maxSize = 0) {
    FILE* fp = fopen(path, "rb");
    if (!fp) return false;

    png_byte header[8];
    if (fread(header, 1, 8, fp) != 8 || png_sig_cmp(header, 0, 8)) {
        fclose(fp);
        return false;
    }

    png_structp png = png_create_read_struct(PNG_LIBPNG_VER_STRING, nullptr, nullptr, nullptr);
    if (!png) { fclose(fp); return false; }
    png_infop info = png_create_info_struct(png);
    if (!info) { png_destroy_read_struct(&png, nullptr, nullptr); fclose(fp); return false; }

    if (setjmp(png_jmpbuf(png))) {
        png_destroy_read_struct(&png, &info, nullptr);
        fclose(fp);
        return false;
    }

    png_init_io(png, fp);
    png_set_sig_bytes(png, 8);
    png_read_info(png, info);

    int width, height;
    std::vector<uint8_t> pixels;
    if (!decodePngToRGBA(png, info, &width, &height, &pixels, monoWhite)) {
        png_destroy_read_struct(&png, &info, nullptr);
        fclose(fp);
        return false;
    }
    png_destroy_read_struct(&png, &info, nullptr);
    fclose(fp);

    *outTex = createIconTexture(pixels.data(), width, height, maxSize);
    ALOGD("NanoMenu: loaded PNG icon %s (%dx%d, %s)", path, width, height,
          monoWhite ? "mono" : "color");
    return true;
}

// Memory read callback for libpng
struct MemPngState { const uint8_t* data; size_t offset; size_t size; };
static void pngReadFromMemory(png_structp png, png_bytep out, png_size_t count) {
    MemPngState* state = (MemPngState*)png_get_io_ptr(png);
    if (state->offset + count > state->size) {
        png_error(png, "read past end");
        return;
    }
    memcpy(out, state->data + state->offset, count);
    state->offset += count;
}

// Load a PNG from in-memory data as texture. Returns true on success.
// If monoWhite is false, preserves original colors (for colored icons like PICO-8).
static bool loadPngFromMemory(const uint8_t* pngData, int pngSize, GLuint* outTex,
                              bool monoWhite = true, int maxSize = 0) {
    if (pngSize < 8 || png_sig_cmp(pngData, 0, 8)) return false;

    png_structp png = png_create_read_struct(PNG_LIBPNG_VER_STRING, nullptr, nullptr, nullptr);
    if (!png) return false;
    png_infop info = png_create_info_struct(png);
    if (!info) { png_destroy_read_struct(&png, nullptr, nullptr); return false; }

    if (setjmp(png_jmpbuf(png))) {
        png_destroy_read_struct(&png, &info, nullptr);
        return false;
    }

    MemPngState memState = { pngData, 8, (size_t)pngSize };
    png_set_read_fn(png, &memState, pngReadFromMemory);
    png_set_sig_bytes(png, 8);
    png_read_info(png, info);

    int width, height;
    std::vector<uint8_t> pixels;
    if (!decodePngToRGBA(png, info, &width, &height, &pixels, monoWhite)) {
        png_destroy_read_struct(&png, &info, nullptr);
        return false;
    }
    png_destroy_read_struct(&png, &info, nullptr);

    *outTex = createIconTexture(pixels.data(), width, height, maxSize);
    ALOGD("NanoMenu: loaded embedded PNG icon (%dx%d, %s)", width, height,
          monoWhite ? "mono" : "color");
    return true;
}

GLuint NanoMenu::overlayCaptureInProcess(int* outW, int* outH) {
    const std::vector<PhysicalDisplayId> ids =
            SurfaceComposerClient::getPhysicalDisplayIds();
    if (ids.empty()) return 0;
    DisplayId did = ids.front();
    // Prefer the configured primary display port (matches the overlay layer).
    {
        char prim[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.primary_display", prim, "0");
        int wantPort = atoi(prim);
        for (const PhysicalDisplayId& pid : ids)
            if ((int)pid.getPort() == wantPort) { did = pid; break; }
    }

    gui::CaptureArgs args;
    sp<SyncScreenCaptureListener> listener = new SyncScreenCaptureListener();
    if (ScreenshotClient::captureDisplay(did, args, listener) != NO_ERROR)
        return 0;
    ScreenCaptureResults res = listener->waitForResults();
    if (!res.fenceResult.ok() || res.buffer == nullptr) return 0;

    sp<GraphicBuffer> buf = res.buffer;
    void* base = nullptr;
    if (buf->lock(GraphicBuffer::USAGE_SW_READ_OFTEN, &base) != NO_ERROR || !base)
        return 0;
    const int w = (int)buf->getWidth();
    const int h = (int)buf->getHeight();
    const int stride = (int)buf->getStride();

    GLuint tex = 0;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    if (stride == w) {
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0,
                     GL_RGBA, GL_UNSIGNED_BYTE, base);
    } else {
        // GLES2 has no GL_UNPACK_ROW_LENGTH; repack rows tightly.
        std::vector<uint8_t> tight((size_t)w * h * 4);
        const uint8_t* src = (const uint8_t*)base;
        for (int y = 0; y < h; y++)
            memcpy(&tight[(size_t)y * w * 4], src + (size_t)y * stride * 4, (size_t)w * 4);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0,
                     GL_RGBA, GL_UNSIGNED_BYTE, tight.data());
    }
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    buf->unlock();
    if (outW) *outW = w;
    if (outH) *outH = h;
    return tex;
}

void NanoMenu::overlayCaptureBackground() {
    // Snapshot the current screen (the just-frozen foreground app) so the opaque
    // overlay can show a static, blurred+tinted backdrop of it (task-switcher
    // model) at 60fps - rather than per-frame compositing the live app. We use
    // the screencap binary via SurfaceFlinger (binder works from nano); the
    // overlay layer is still hidden at this point, so the capture is the app.
    // Fast path: in-process SurfaceFlinger capture (~50ms, no process spawn).
    int capW = mWidth, capH = mHeight;
    GLuint rawTex = overlayCaptureInProcess(&capW, &capH);
    if (rawTex == 0) {
        // Fallback: spawn the screencap binary (~1s) + PNG load.
        const char* path = "/data/local/tmp/nano_overlay_bg.png";
        unlink(path);
        int rc = system("screencap -p /data/local/tmp/nano_overlay_bg.png 2>/dev/null");
        if (!loadPngAsAlphaTexture(path, &rawTex, /*monoWhite=*/false)) {
            ALOGW("overlay: capture failed (in-process + screencap rc=%d) - bg black", rc);
            if (mOverlayBgTex) { glDeleteTextures(1, &mOverlayBgTex); mOverlayBgTex = 0; }
            return;
        }
        capW = mWidth; capH = mHeight;
        ALOGI("overlay: used screencap fallback");
    } else {
        ALOGI("overlay: in-process capture %dx%d", capW, capH);
    }

    // Blur the sharp snapshot ONCE through the dual-Kawase chain, then bake the
    // upscaled result into an owned full-res texture (mOverlayBgTex). render()
    // then just draws that texture every frame (no per-frame blur -> 60fps).
    glBindTexture(GL_TEXTURE_2D, rawTex);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    // Heavy blur - baked once so cost is irrelevant: 4 downsample levels (1/16
    // res) + 4 separable Gaussian iterations for a strong frosted backdrop.
    blurGlassChain(rawTex, capW, capH, 4, 4);

    GLint prevFbo = 0;
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
    GLint prevVp[4] = {0, 0, 0, 0};
    glGetIntegerv(GL_VIEWPORT, prevVp);
    if (mOverlayBgTex == 0) glGenTextures(1, &mOverlayBgTex);
    glBindTexture(GL_TEXTURE_2D, mOverlayBgTex);
    // RGB565 (16-bit) rather than RGBA8888: the baked backdrop is opaque and
    // heavily blurred, so 16-bit colour is visually identical while halving this
    // full-screen texture (e.g. 1024x768: 3MB -> 1.5MB). Fall back to RGBA8888 if
    // the driver cannot render 565 to an FBO colour attachment.
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, mWidth, mHeight, 0,
                 GL_RGB, GL_UNSIGNED_SHORT_5_6_5, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    GLuint fbo = 0;
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                           GL_TEXTURE_2D, mOverlayBgTex, 0);
    GLenum fbStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if (fbStatus != GL_FRAMEBUFFER_COMPLETE) {
        // 565 not colour-renderable on this GPU: retry the proven RGBA8888 path
        // before giving up, so the backdrop still bakes (just at 32-bit).
        glBindTexture(GL_TEXTURE_2D, mOverlayBgTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, mWidth, mHeight, 0,
                     GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                               GL_TEXTURE_2D, mOverlayBgTex, 0);
        fbStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    }
    if (fbStatus != GL_FRAMEBUFFER_COMPLETE) {
        // GL_RGBA is not guaranteed colour-renderable on every GLES2 GPU. If the
        // attachment is incomplete, baking would silently no-op (leaving a black
        // backdrop and a fake-success diagnostic), so bail cleanly: drop the bg
        // texture (render() then falls back to a flat dark scrim) and restore
        // state. Better a clean dark backdrop than a black one with a false log.
        ALOGW("overlay: bake FBO incomplete (0x%x) - dropping bg texture", fbStatus);
        glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prevFbo);
        glViewport(prevVp[0], prevVp[1], prevVp[2], prevVp[3]);
        glDeleteFramebuffers(1, &fbo);
        glDeleteTextures(1, &rawTex);
        if (mOverlayBgTex) { glDeleteTextures(1, &mOverlayBgTex); mOverlayBgTex = 0; }
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        return;
    }
    glViewport(0, 0, mWidth, mHeight);
    glDisable(GL_BLEND);
    // The bake runs from overlayShow, which fires BEFORE any render() frame of
    // the (previously hidden) overlay - so uploadRotationMatrices() has not run
    // yet and mTextProgram's uRotation is still the zero matrix, which collapses
    // drawIconTex to nothing (the baked texture came out black). Set identity
    // rotation explicitly here (overlay SF mode has no panel rotation anyway).
    {
        static const GLfloat kIdentity2[4] = {1.0f, 0.0f, 0.0f, 1.0f};
        glUseProgram(mTextProgram);
        if (mTextLocRotation >= 0)
            glUniformMatrix2fv(mTextLocRotation, 1, GL_FALSE, kIdentity2);
    }
    if (mGlassBlurTex)
        drawIconTex(mGlassBlurTex, 0.0f, 0.0f, (float)mWidth, (float)mHeight,
                    1.0f, 1.0f, 1.0f, 1.0f);
    // Diagnostic: read back the centre of the baked texture so we can tell from
    // logcat whether the bake produced real (blurred) content or black, since
    // the opaque overlay layer cannot be seen via screencap.
    {
        uint8_t c[4] = {0, 0, 0, 0};
        glReadPixels(mWidth / 2, mHeight / 2, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, c);
        ALOGI("overlay: baked centre pixel RGBA=%d,%d,%d,%d (mGlassBlurTex=%u)",
              c[0], c[1], c[2], c[3], mGlassBlurTex);
    }
    glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prevFbo);
    glViewport(prevVp[0], prevVp[1], prevVp[2], prevVp[3]);
    glDeleteFramebuffers(1, &fbo);
    glDeleteTextures(1, &rawTex);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    ALOGI("overlay: captured + baked blurred background -> tex %u (%dx%d)",
          mOverlayBgTex, mWidth, mHeight);
}

void NanoMenu::initIconTextures() {
    memset(mIconTextures, 0, sizeof(mIconTextures));
    int fileLoaded = 0, embeddedLoaded = 0;
    // Right-size the console icons to this panel: they are drawn at ITEM_ICON_SIZE
    // virtual px, so a 256 source is oversized on a small panel. iconTexCap keeps
    // them crisp on high-DPI screens (returns up to the full source there).
    const int iconCap = ps3::iconTexCap(mWidth, mHeight, ps3::ITEM_ICON_SIZE, 256);
    for (int i = 0; i < 19; i++) {
        // Try loading high-res PNG from on-device RetroArch assets
        bool mono = (i != 14); // PICO-8 (index 14) keeps its original colors
        std::string pngPath;
        const char* fname = kIconPngNames[i];
        if (fname) pngPath = std::string(kIconPngDir) + "/" + fname;
        if (!pngPath.empty() && loadPngAsAlphaTexture(pngPath.c_str(), &mIconTextures[i], mono, iconCap)) {
            fileLoaded++;
            continue;
        }
        // Fallback: embedded 256x256 PNG data
        const EmbeddedIcon& icon = kEmbeddedIcons[i];
        if (loadPngFromMemory(icon.data, icon.size, &mIconTextures[i], mono, iconCap)) {
            embeddedLoaded++;
            continue;
        }
        ALOGE("NanoMenu: failed to load icon %d from file or embedded data", i);
    }
    ALOGD("NanoMenu: loaded %d file + %d embedded icon textures", fileLoaded, embeddedLoaded);
}

void NanoMenu::drawIcon(int iconIdx, float x, float y, float size,
                        float r, float g, float b, float a) {
    if (iconIdx < 0 || iconIdx >= 19 || mIconTextures[iconIdx] == 0) return;

    float x0 = (x / mWidth) * 2.0f - 1.0f;
    float y0 = 1.0f - ((y + size) / mHeight) * 2.0f;
    float x1 = ((x + size) / mWidth) * 2.0f - 1.0f;
    float y1 = 1.0f - (y / mHeight) * 2.0f;

    GLfloat verts[] = { x0,y0, x1,y0, x1,y1, x1,y1, x0,y1, x0,y0 };
    GLfloat uvs[]   = { 0,1, 1,1, 1,0, 1,0, 0,0, 0,1 };
    GLfloat colors[6 * 4];
    for (int i = 0; i < 6; i++) {
        colors[i*4+0] = r; colors[i*4+1] = g;
        colors[i*4+2] = b; colors[i*4+3] = a;
    }

    glUseProgram(mTextProgram); // reuse text shader (texture * vertex color)
    if (mTextLocSharp >= 0) glUniform1f(mTextLocSharp, 0.0f);   // icons: no glyph edge-sharpen
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, mIconTextures[iconIdx]);
    glUniform1i(mTextLocTexture, 0);
    // Unbind any VBO (see drawText) so the client pointers below are
    // read correctly.
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
// Drawing helpers
// ---------------------------------------------------------------------------

// Enable alpha blending for UI chrome. In the translucent overlay (SF) mode use
// a SEPARATE alpha term so the framebuffer alpha accumulates straight toward 1
// for opaque chrome - plain GL_SRC_ALPHA under-accumulates the alpha channel
// (dst_a = a*a + ...), so SurfaceFlinger composites the live app through "white"
// text/icons. The home DRM path is unaffected (no SF compositing of alpha).
void NanoMenu::setUiBlend() {
    glEnable(GL_BLEND);
    if (mOverlayMode) {
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA,
                            GL_ONE,       GL_ONE_MINUS_SRC_ALPHA);
    } else {
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }
}

// ---------------------------------------------------------------------------
// Flat-colour batch (see beginSolidBatch in the header). While mSolidBatchActive
// is set, drawQuad/drawTriangle append their NDC vertices + a per-vertex colour
// here instead of issuing one glDrawArrays each. flushSolidBatch submits the lot
// through mParticleProgram (per-vertex colour, same uRotation as mShaderProgram,
// uploaded once per frame). Same vertices, same submission order, same blend, so
// the composited result is byte-identical to the immediate path.
static const int SOLID_BATCH_MAX_VERTS = 8192;
static GLfloat sSolidPos[SOLID_BATCH_MAX_VERTS * 2];
static GLfloat sSolidCol[SOLID_BATCH_MAX_VERTS * 4];
static int     sSolidN = 0;

static inline void solidPush(float nx, float ny, float r, float g, float b, float a) {
    int p = sSolidN * 2, c = sSolidN * 4;
    sSolidPos[p] = nx; sSolidPos[p + 1] = ny;
    sSolidCol[c] = r; sSolidCol[c + 1] = g; sSolidCol[c + 2] = b; sSolidCol[c + 3] = a;
    sSolidN++;
}

void NanoMenu::flushSolidBatch() {
    if (sSolidN <= 0) return;
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glUseProgram(mParticleProgram);   // gl_FragColor = vColor, uRotation set per frame
    glVertexAttribPointer(mParticleLocPosition, 2, GL_FLOAT, GL_FALSE, 0, sSolidPos);
    glEnableVertexAttribArray(mParticleLocPosition);
    glVertexAttribPointer(mParticleLocColor, 4, GL_FLOAT, GL_FALSE, 0, sSolidCol);
    glEnableVertexAttribArray(mParticleLocColor);
    glDrawArrays(GL_TRIANGLES, 0, sSolidN);
    glDisableVertexAttribArray(mParticleLocPosition);
    glDisableVertexAttribArray(mParticleLocColor);
    sSolidN = 0;
}

void NanoMenu::beginSolidBatch() {
    flushSolidBatch();
    sSolidN = 0;
    // Only batch if the per-vertex-colour program exists. It is stripped on the
    // drastic QR fast-path (mParticleProgram == 0), which never reaches the clock,
    // but this guarantees drawQuad/drawTriangle fall back to their immediate path
    // rather than the clock silently vanishing if that ever changes.
    mSolidBatchActive = (mParticleProgram != 0 && mParticleLocPosition >= 0 && mParticleLocColor >= 0);
}
void NanoMenu::endSolidBatch()   { flushSolidBatch(); mSolidBatchActive = false; }

void NanoMenu::drawQuad(float x, float y, float w, float h,
                         float r, float g, float b, float a) {
    float x0 = (x / mWidth) * 2.0f - 1.0f;
    float y0 = 1.0f - ((y + h) / mHeight) * 2.0f;
    float x1 = ((x + w) / mWidth) * 2.0f - 1.0f;
    float y1 = 1.0f - (y / mHeight) * 2.0f;
    if (mSolidBatchActive) {
        // Same 6-vertex order as the immediate verts[] below (BL,BR,TR,TR,TL,BL).
        if (sSolidN + 6 > SOLID_BATCH_MAX_VERTS) flushSolidBatch();
        solidPush(x0, y0, r, g, b, a); solidPush(x1, y0, r, g, b, a); solidPush(x1, y1, r, g, b, a);
        solidPush(x1, y1, r, g, b, a); solidPush(x0, y1, r, g, b, a); solidPush(x0, y0, r, g, b, a);
        return;
    }
    GLfloat verts[] = { x0,y0, x1,y0, x1,y1, x1,y1, x0,y1, x0,y0 };
    // Unbind any VBO so the glVertexAttribPointer below is treated as a
    // client memory pointer. DrasticRunner::drawDsQuad leaves mQuadVbo
    // bound; without this, the client pointer `verts` gets interpreted
    // as a byte offset into mQuadVbo and the quad renders from garbage.
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glUseProgram(mShaderProgram);
    glUniform4f(mLocColor, r, g, b, a);
    glVertexAttribPointer(mLocPosition, 2, GL_FLOAT, GL_FALSE, 0, verts);
    glEnableVertexAttribArray(mLocPosition);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glDisableVertexAttribArray(mLocPosition);
}

// Rounded-rect with a fragment-shader SDF (crisp corners at any scale). Vertex
// order matches drawQuad: BL, BR, TR, TR, TL, BL; aLocal is the centered pixel
// coordinate so the SDF abs() handles all four corners symmetrically.
void NanoMenu::drawRoundedRect(float x, float y, float w, float h, float radius,
                               float r, float g, float b, float a) {
    flushSolidBatch();   // submit any pending batched solids first so this SDF rect keeps painter order
    float x0 = (x / mWidth) * 2.0f - 1.0f;
    float y0 = 1.0f - ((y + h) / mHeight) * 2.0f;
    float x1 = ((x + w) / mWidth) * 2.0f - 1.0f;
    float y1 = 1.0f - (y / mHeight) * 2.0f;
    GLfloat verts[] = { x0,y0, x1,y0, x1,y1, x1,y1, x0,y1, x0,y0 };
    float hw = w * 0.5f, hh = h * 0.5f;
    GLfloat local[] = { -hw,hh, hw,hh, hw,-hh, hw,-hh, -hw,-hh, -hw,hh };
    float mh = (hw < hh ? hw : hh);
    if (radius > mh) radius = mh;
    if (radius < 0.0f) radius = 0.0f;
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glUseProgram(mRoundProgram);
    glUniformMatrix2fv(mRoundLocRotation, 1, GL_FALSE, sDrmRotMat);
    glUniform2f(mRoundLocHalf, hw, hh);
    glUniform1f(mRoundLocRadius, radius);
    glUniform4f(mRoundLocColor, r, g, b, a);
    glVertexAttribPointer(mRoundLocPosition, 2, GL_FLOAT, GL_FALSE, 0, verts);
    glEnableVertexAttribArray(mRoundLocPosition);
    glVertexAttribPointer(mRoundLocLocal, 2, GL_FLOAT, GL_FALSE, 0, local);
    glEnableVertexAttribArray(mRoundLocLocal);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glDisableVertexAttribArray(mRoundLocPosition);
    glDisableVertexAttribArray(mRoundLocLocal);
}

// Solid-color triangle (3 verts) using the flat-color program. Relies on the
// frame's uploadRotationMatrices having set uRotation on mShaderProgram, same
// as drawQuad. Used to compose the backspace icon.
void NanoMenu::drawTriangle(float x0, float y0, float x1, float y1,
                            float x2, float y2,
                            float r, float g, float b, float a) {
    float n0x = (x0 / mWidth) * 2.0f - 1.0f, n0y = 1.0f - (y0 / mHeight) * 2.0f;
    float n1x = (x1 / mWidth) * 2.0f - 1.0f, n1y = 1.0f - (y1 / mHeight) * 2.0f;
    float n2x = (x2 / mWidth) * 2.0f - 1.0f, n2y = 1.0f - (y2 / mHeight) * 2.0f;
    if (mSolidBatchActive) {
        if (sSolidN + 3 > SOLID_BATCH_MAX_VERTS) flushSolidBatch();
        solidPush(n0x, n0y, r, g, b, a); solidPush(n1x, n1y, r, g, b, a); solidPush(n2x, n2y, r, g, b, a);
        return;
    }
    GLfloat verts[] = { n0x, n0y, n1x, n1y, n2x, n2y };
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glUseProgram(mShaderProgram);
    glUniform4f(mLocColor, r, g, b, a);
    glVertexAttribPointer(mLocPosition, 2, GL_FLOAT, GL_FALSE, 0, verts);
    glEnableVertexAttribArray(mLocPosition);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glDisableVertexAttribArray(mLocPosition);
}

// Mouse cursor overlay. A small white arrow with a dark halo drawn in logical-pixel space at
// (mCursorX,mCursorY); like every other element it is authored pre-rotation so the shader's
// rotation uniform keeps it aligned on rotated/flipped panels. The size adapts to the panel so
// it works on every resolution. Self-hides a few seconds after the last pointer activity.
void NanoMenu::drawPointerCursor() {
    if (!mCursorVisible) return;
    // Don't paint the cursor over a live app: the overlay instance scrims a running app unless
    // it is showing the full PS3 wallpaper (the home). Only draw on actual nano-menu content.
    if (mOverlayMode && !mOverlayWallpaper) return;
    if ((int64_t)uptimeMillis() - mLastPointerMs > 4000) { mCursorVisible = false; return; }
    const float x = mCursorX, y = mCursorY;
    float s = (float)(mWidth < mHeight ? mWidth : mHeight) / 22.0f;
    if (s < 14.0f) s = 14.0f;
    // Dark outline arrow (slightly larger/offset), then the white arrow on top, so the cursor
    // stays visible over any wallpaper, dialog or scrim.
    drawTriangle(x - 1.5f,             y - 1.5f,
                 x - 1.5f,             y + s + 2.0f,
                 x + s * 0.72f + 1.5f, y + s * 0.72f + 1.0f,
                 0.0f, 0.0f, 0.0f, 0.75f);
    drawTriangle(x,             y,
                 x,             y + s,
                 x + s * 0.70f, y + s * 0.70f,
                 1.0f, 1.0f, 1.0f, 1.0f);
}

// ===========================================================================
// DSi System Menu theme (persist.gammaos.nano.ndstheme) - a 1:1 port of the
// /work/nds launcher (nds-web). FIRST SCAFFOLD: the bottom-screen launcher
// carousel with the real firmware geometry/colours (config.js CAROUSEL +
// launcher.js draw()), aspect-adaptive: the native 256x192 DSi design contain-
// fits and centres into any panel (letterbox), so it adapts to every resolution/
// aspect. Tile CONTENT is a placeholder pending the XMB-hierarchy feed; the
// layout, the selected blue frame, START, the name box and the scrollbar are the
// real DSi values. The DSi font/sprites and live carousel scroll come next.
// ===========================================================================
// DSi launcher home. Orchestrates the panel layout: the single-screen default fills the
// panel with the carousel; the stacked adaptation (persist.gammaos.nano.ndstheme.stack)
// puts the DSi top screen over the carousel (each contain-fit in its half, the light bg
// fills the side margins so there are never black bars). The RG DS dual-screen path renders
// the top screen on the top panel and the carousel on the bottom panel separately (later).
void NanoMenu::ensureNdsAssets() {
    if (mNdsTexLoaded) return;    // one-shot load of the real 4x SVG sprites + the layout mode
    mNdsFrameTex = ndsLoadTex("nds_frame");   // cell_00 selection frame (transparent centre)
    mNdsTileTex  = ndsLoadTex("nds_tile");    // tile_white pillow
    mNdsPhotoTex = ndsLoadTex("nds_photo");   // photo_U top-screen panel (bevel frame + mint field)
    mNdsBattTex  = ndsLoadTex("nds_batt");        // spr_batt_full sprite (only the unknown-level fallback)
    // status-bar glyphs (framework SystemUI vectors -> mono PNGs), EMBEDDED in the binary
    // (NanoMenuSbIcons.h) so they load with no /data or /system file dependency. Tinted at draw.
    if (!mNdsSbIconsLoaded) {
        mNdsSbSpeaker     = ndsLoadTexMem(kNdsSbSpeakerPng,     kNdsSbSpeakerPngLen);
        mNdsSbSpeakerMute = ndsLoadTexMem(kNdsSbSpeakerMutePng, kNdsSbSpeakerMutePngLen);
        mNdsSbWifi        = ndsLoadTexMem(kNdsSbWifiPng,        kNdsSbWifiPngLen);
        mNdsSbBt          = ndsLoadTexMem(kNdsSbBtPng,          kNdsSbBtPngLen);
        mNdsSbNote        = ndsLoadTexMem(kNdsSbNotePng,        kNdsSbNotePngLen);
        mNdsSbIconsLoaded = (mNdsSbSpeaker && mNdsSbWifi && mNdsSbBt && mNdsSbNote);
    }
    char sk[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.ndstheme.stack", sk, "auto");
    // persist.gammaos.nano.ndstheme.stack: 1/true/on -> stack the DSi top screen ABOVE the carousel
    // on a single-screen device; 0/false/off/auto/unset -> carousel-only (default). A real dual-panel
    // device ignores this and always uses its two panels. Effective mNdsStack is set per frame.
    if (sk[0] == '1' || sk[0] == 't' || (sk[0] == 'o' && sk[1] == 'n')) mNdsStackMode = 1;
    else if (sk[0] == '0' || sk[0] == 'f' || (sk[0] == 'o' && sk[1] == 'f')) mNdsStackMode = 2;
    else mNdsStackMode = 0;
    mNdsTexLoaded = true;
}

// The 36 sparkle-ring frames (launcher_d cell_53..88) played during a game launch.
// Loaded lazily on the first launch (masked by the white wash) so the ~2.6MB of ring
// texture is never resident during a normal home session.
void NanoMenu::ensureNdsRing() {
    if (mNdsRingLoaded) return;
    for (int i = 0; i < 36; i++) {
        char nm[16]; snprintf(nm, sizeof(nm), "nds_ring_%02d", i);
        mNdsRingTex[i] = ndsLoadTex(nm);
    }
    mNdsRingLoaded = true;
}

// launcher._introFall: the boot->carousel entrance cascade. Each tile at screen offset
// `off` from the centre spring-falls ~90px from off-top with one ~24px damped overshoot,
// staggered 4f/slot right-to-left. Returns the vertical offset in DS px from the settled
// position (0 = settled), or -1000 as a "not yet visible" sentinel (web returns null).
float NanoMenu::ndsIntroFall(int off, float f) {
    const float START = 3.0f, STAGGER = 4.0f, TF = 12.0f, TT = 34.0f, H = 90.0f, OV = 24.0f;
    float t = f - START - (2.0f - (float)off) * STAGGER;
    if (t < 0.0f)   return -1000.0f;   // not yet visible
    if (t >= TT)    return 0.0f;       // settled
    if (t < TF)  { float p = t / TF; return -H + (H + OV) * (p * p * (1.15f - 0.15f * p)); }
    float bt = t - TF;                 // damped bounce back to 0
    return OV * cosf(bt * 0.42f) * expf(-bt * 0.11f);
}

// Commit a scrub/fling landing (launcher.snap): write the settled slot into the live
// selection so the top screen, name box and subsequent nav all agree. The DSi carousel
// drag only runs at the top level (mPs3Stack empty), so this drives mPs3ItemIdx.
void NanoMenu::ndsCommitSelect(int slot) {
    if (!mPs3Stack.empty()) { mPs3Stack.back().sel = slot; return; }
    if (mNdsAtRoot) { mPs3CatIdx = slot; return; }
    mPs3ItemIdx = slot;
}

// ---- Stacked-carousel navigation (user redesign) ----------------------------------------
// The DSi home is a stack of carousels: the root is the XMB categories, drilling in pushes a
// child carousel into focus while the parent slides up and dims. These helpers drive that.

// A modal overlay (option chooser / dialog / OSK / a media player / wizard) owns navigation;
// while one is up the DSi nav delegates to the existing XMB handlers (restyled separately).
bool NanoMenu::ndsInModal() const {
    return mPs3OptActive || mPs3DlgActive || mOskActive || mVidActive || mMpActive || mPvActive
        || mGSearchActive || mPs3WizActive || mPs3TzActive || mPs3LangActive || mPs3BrightSlider
        || mPhotoMultiActive || mScrapeProgActive || mPvPlChooserActive || mVidPlChooserActive
        || mMpPlChooserActive || ps3TopScreenKind() == PHOTO_GRID;
}

// A media player is on screen (user: "show the XMB ones when we're actually playing"). While
// one is up the DSi home hands the whole render to renderPs3Xmb() so the existing full-screen
// video / music / photo player UI shows instead of the DSi carousel.
bool NanoMenu::ndsPlayerActive() const {
    return mVidActive || mMpActive || mPvActive || mPhotoMultiActive
        || mPvPlChooserActive || mVidPlChooserActive || mMpPlChooserActive
        || ps3TopScreenKind() == PHOTO_GRID;
}

// A dialog that is really a chooser (a scrolling list of options) or a numeric slider renders
// as the DSi settings-options SIDE PANEL; a short confirm (Yes/No/OK message) renders as the
// DSi message-box DIALOG. kind 1 side-panel choosers and sliders are always the list style.
bool NanoMenu::ndsDlgIsSidePanel() const {
    if (mPs3DlgSlider) return true;
    if (mPs3DlgKind == 1) return true;
    if (otaInBrowse()) return true;   // OTA package browser: always the DSi vertical list (a single
                                      // long "systest.zip (1.67 GB)" label overflows a modal button)
    if ((int)mPs3DlgOptions.size() > 3) return true;   // long option list -> list style, not buttons
    // 2-3 options whose labels are too long for the horizontal DSi buttons (they would collide,
    // e.g. System Update: "Update via Internet" / "Update via Storage Media") read as a vertical
    // list instead. The two canonical buttons hold ~14 chars each before overflow.
    if ((int)mPs3DlgOptions.size() >= 2)
        for (const auto& o : mPs3DlgOptions)
            if (o.size() > 14) return true;
    return false;
}

void NanoMenu::ndsBuildCatCards() {
    mNdsCatCards.clear();
    for (auto& c : mPs3Cats) {
        Ps3Item it; it.label = c.name; it.iconTex = c.iconTex; it.nmapTex = c.nmapTex;
        it.iconR = it.iconG = it.iconB = 1.0f;
        mNdsCatCards.push_back(it);
    }
    mNdsCatCardsBuilt = true;
}

int NanoMenu::ndsNavDepth() const { return mNdsAtRoot ? 0 : 1 + (int)mPs3Stack.size(); }

int NanoMenu::ndsFocusSel() const {
    if (mNdsAtRoot) return mPs3CatIdx;
    if (!mPs3Stack.empty()) return mPs3Stack.back().sel;
    return mPs3ItemIdx;
}
int NanoMenu::ndsFocusCount() const {
    if (mNdsAtRoot) return (int)mPs3Cats.size();
    if (!mPs3Stack.empty()) return (int)mPs3Stack.back().items.size();
    return (mPs3CatIdx >= 0 && mPs3CatIdx < (int)mPs3Cats.size()) ? (int)mPs3Cats[mPs3CatIdx].items.size() : 0;
}

// cycle the focused carousel's selection (root = categories, else the category/submenu items).
void NanoMenu::ndsNavHoriz(int dir) {
    if (ndsGameInfoActive()) { ndsInfoPage(dir); return; }   // L/R turn the info page
    if (ndsInModal()) { if (dir < 0) ps3XmbLeft(); else ps3XmbRight(); return; }
    mNdsFastScroll = false;   // a D-pad step uses the slow nav slide, not the blank-track fast glide
    int* sel; int n;
    if (mNdsAtRoot)              { sel = &mPs3CatIdx;  n = (int)mPs3Cats.size(); }
    else if (mPs3Stack.empty())  { sel = &mPs3ItemIdx; n = (mPs3CatIdx >= 0 && mPs3CatIdx < (int)mPs3Cats.size()) ? (int)mPs3Cats[mPs3CatIdx].items.size() : 0; }
    else                         { sel = &mPs3Stack.back().sel; n = (int)mPs3Stack.back().items.size(); }
    if (n <= 0) return;
    // Wrap around the ends (user request): Up on the first row jumps to the last and Down on
    // the last jumps to the first, in the DSi/Minima lists + the Minima root list (the pure XMB
    // carousel uses its own handlers and is unaffected). On a wrap, snap the scroll/camera to the
    // new end so it does not glide-scroll through the whole list.
    int cur = *sel;
    int ni = cur + dir;
    bool wrapped = false;
    if (dir == -1 || dir == 1) {                 // single-step Up/Down wraps around the ends
        if (ni < 0)          { ni = n - 1; wrapped = (n > 1); }
        else if (ni > n - 1) { ni = 0;     wrapped = (n > 1); }
    } else {                                      // page jumps (Minima LEFT/RIGHT = +-6) clamp, never wrap
        if (ni < 0) ni = 0;
        if (ni > n - 1) ni = n - 1;
    }
    if (ni != cur) {
        *sel = ni;
        mDisplayDirty = true;
        if (wrapped) {
            mListWrapSnap = true;            // Minima list + DSi submenu snap their scroll to sel this frame
            mNdsCamera = (float)ni;          // DSi root carousel camera jumps to the wrapped card
        }
    }
    // A carousel blocked at the first/last card plays no reject blip: the real firmware's
    // TWL_LAN_SE_SCROLL_INVALID could not be captured cleanly, so the source of truth dropped it.
}

// enter/drill/launch the focused card. At the root this enters the selected category; deeper,
// it reuses ps3XmbSelect (which drills a submenu, launches a game/app, or opens a chooser).
void NanoMenu::ndsNavSelect(bool allowLaunch) {
    if (ndsInModal()) { ps3XmbSelect(); return; }
    if (mNdsAtRoot) {
        if (mPs3CatIdx < 0 || mPs3CatIdx >= (int)mPs3Cats.size()) return;
        mNdsAtRoot = false; mPs3ItemIdx = 0;
        mNdsCamera = 0.0f; mNdsScrubbing = false; mNdsFlingVel = 0.0f;
        mDisplayDirty = true;
        return;
    }
    size_t before = mPs3Stack.size();
    // D-pad / buttons (allowLaunch=false) navigate the hierarchy (drill submenus, open choosers)
    // but must NEVER launch a game/app - that is touch-only (user request). Snapshot the launch
    // state, run the select, and if it armed a launch on a leaf, undo it (the drill/chooser side
    // effects still stand). Touch (allowLaunch=true) launches normally.
    const int64_t savedFade = mLaunchFadeStart;
    char savedPkg[PROPERTY_VALUE_MAX] = {};
    property_get("sys.gammaos.nano.launched_pkg", savedPkg, "");
    ps3XmbSelect();                       // drill / launch / open a chooser or dialog
    if (!allowLaunch && mLaunchFadeStart != savedFade) {
        mLaunchFadeStart = savedFade; mWaitForRelease = false; mExitRequested = false;
        property_set("sys.gammaos.nano.launched_pkg", savedPkg);
    }
    if (mPs3Stack.size() > before) {      // drilled into a submenu -> focus its selection
        mNdsCamera = (float)mPs3Stack.back().sel; mNdsScrubbing = false; mNdsFlingVel = 0.0f;
        mDisplayDirty = true;
    }
}

// walk up one level: pop a submenu, else leave the category back to the categories root.
void NanoMenu::ndsNavBack() {
    if (ndsInModal()) { ps3XmbBack(); return; }
    if (!mPs3Stack.empty()) {
        ps3XmbBack();
        mNdsCamera = mPs3Stack.empty() ? (float)mPs3ItemIdx : (float)mPs3Stack.back().sel;
        mNdsScrubbing = false; mNdsFlingVel = 0.0f; mDisplayDirty = true;
    } else if (!mNdsAtRoot) {
        mNdsAtRoot = true;
        mNdsCamera = (float)mPs3CatIdx; mNdsScrubbing = false; mNdsFlingVel = 0.0f;
        mDisplayDirty = true;
    } else if (mOverlayMode) {
        // At the DSi root inside the resume overlay there is nothing more to walk up, so B/Back
        // dismisses the overlay and resumes the running app (ps3XmbBack -> overlayResume). User
        // request: the overlay must be dismissable via B/Back.
        ps3XmbBack();
    }
}

// A drill level whose items are ALL settings kinds (data toggles / data groups / quick-settings)
// renders as the DSi vertical glossy LIST (renderNdsSubmenu) with each row's value inline, instead of
// the horizontal icon-tile carousel, so On/Off state reads at a glance (user request). The root
// categories, and any level that holds an app / game / system card, stay a carousel. Whitelisting the
// settings kinds means a media / app category is never misdetected as a list.
bool NanoMenu::ndsCurLevelIsList() const {
    if (mNdsAtRoot) return false;
    // Special-screen levels that are inherently vertical lists (the file browser, network-shares
    // list + editor, shader-file browser) always render as the DSi glossy list, regardless of their
    // row kinds. Without this they hold PS3_FE_*/PS3_NS_* rows the whitelist below rejects, so they
    // fall through to the horizontal carousel - which cannot draw or navigate them, and where UP
    // walks straight back out. (File Explorer looked broken on the DSi theme for exactly this.)
    if (!mPs3Stack.empty()) {
        switch (mPs3Stack.back().screenKind) {
            case FE_BROWSE: case NS_LIST: case NS_EDITOR:
            case SHADER_BROWSE: case GS_FOLDERBROWSE:
            // Game system list and per-system editor hold GS-kind rows (PS3_GS_SYSTEM_ROW,
            // PS3_GS_FIELD) which the item-kind whitelist below does not cover. Force list
            // mode here so Enabled toggles and scraper rows are navigable in Minima/NDS.
            // Home Categories (CAT_ORDER) holds PS3_CATORDER_ROW rows for the same reason.
            case GS_LIST: case GS_EDITOR: case CAT_ORDER:
                return true;
            default: break;
        }
    }
    const std::vector<Ps3Item>* items = nullptr;
    if (!mPs3Stack.empty()) items = &mPs3Stack.back().items;
    else if (mPs3CatIdx >= 0 && mPs3CatIdx < (int)mPs3Cats.size()) items = &mPs3Cats[mPs3CatIdx].items;
    if (!items || items->empty()) return false;
    for (const Ps3Item& it : *items)
        if (it.kind != PS3_DATA_LEAF && it.kind != PS3_DATA_SUBMENU && it.kind != PS3_QUICK)
            return false;
    return true;
}

// ---- DSi accent recolour ----
// Small local HSV helpers (the ones in NanoMenuPS3Bg.cpp are file-static).
static void ndsRgb2Hsv(float r, float g, float b, float& h, float& s, float& v) {
    float mx = fmaxf(r, fmaxf(g, b)), mn = fminf(r, fminf(g, b)), d = mx - mn;
    v = mx; s = (mx <= 0.0f) ? 0.0f : d / mx;
    if (d <= 1e-6f) { h = 0.0f; return; }
    if (mx == r)      h = 60.0f * fmodf(((g - b) / d), 6.0f);
    else if (mx == g) h = 60.0f * (((b - r) / d) + 2.0f);
    else              h = 60.0f * (((r - g) / d) + 4.0f);
    if (h < 0.0f) h += 360.0f;
}
static void ndsHsv2Rgb(float h, float s, float v, float& r, float& g, float& b) {
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

// Recolour a DSi reference-blue shade (a stop of the favColour gloss gradient or a flat blue
// accent) toward the user's Colour setting. The whole DSi palette is rotated by ONE hue delta
// (accent hue - reference azure hue) and scaled by ONE saturation ratio, so every shade keeps
// its relative gloss/brightness relationship - light stays light, deep stays deep - just in the
// new hue. At "Original" the accent is the reference azure, so this is the identity and the DSi
// stays its classic blue. Value (brightness) is preserved to keep the glossy look intact.
void NanoMenu::ndsRecolor(float& r, float& g, float& b) const {
    static const float kRefBlue[3] = {0.094f, 0.573f, 0.922f};   // reference DSi favColour azure
    float ar, ag, ab; ndsAccentRGB(ar, ag, ab);
    float refH, refS, refV;  ndsRgb2Hsv(kRefBlue[0], kRefBlue[1], kRefBlue[2], refH, refS, refV);
    float accH, accS, accV;  ndsRgb2Hsv(ar, ag, ab, accH, accS, accV);
    float dH = accH - refH;
    float sScale = (refS > 1e-3f) ? (accS / refS) : 1.0f;
    if (sScale > 1.15f) sScale = 1.15f;   // clamp so a very saturated accent does not over-boost the light stops
    float h, s, v; ndsRgb2Hsv(r, g, b, h, s, v);
    float ns = s * sScale; if (ns > 1.0f) ns = 1.0f;
    ndsHsv2Rgb(h + dH, ns, v, r, g, b);
}

// A DSi System Settings glossy list button (settings.js _glossyButtonVec + button_grads.json):
// a dark drop-shadow rounded rect under a rounded rect filled with the exact 12-stop vertical
// gradient - glossy grey when idle, glossy accent-coloured when selected (follows the Colour
// setting via ndsRecolor). r/x/y/w/h in device px.
void NanoMenu::drawNdsGlossyBtn(float x, float y, float w, float h, float r, bool sel) {
    // button_grads.json menu.idle (grey) and menu.blue, 12 vertical stops (0..1).
    static const float grey[12][3] = {
        {0.922f,0.922f,0.922f},{0.875f,0.875f,0.875f},{0.827f,0.827f,0.827f},{0.796f,0.796f,0.796f},
        {0.765f,0.765f,0.765f},{0.733f,0.733f,0.733f},{0.698f,0.698f,0.698f},{0.682f,0.682f,0.682f},
        {0.667f,0.667f,0.667f},{0.635f,0.635f,0.635f},{0.604f,0.604f,0.604f},{0.667f,0.667f,0.667f} };
    static const float blue[12][3] = {
        {0.255f,0.667f,0.859f},{0.176f,0.651f,0.875f},{0.094f,0.635f,0.890f},{0.094f,0.604f,0.906f},
        {0.094f,0.573f,0.922f},{0.063f,0.541f,0.890f},{0.031f,0.510f,0.859f},{0.047f,0.463f,0.906f},
        {0.063f,0.412f,0.953f},{0.031f,0.380f,0.937f},{0.000f,0.349f,0.922f},{0.063f,0.412f,0.953f} };
    // Recolour the selected (blue) gradient toward the Colour accent once (identity at "Original").
    float blueAcc[12][3];
    for (int i = 0; i < 12; i++) {
        blueAcc[i][0] = blue[i][0]; blueAcc[i][1] = blue[i][1]; blueAcc[i][2] = blue[i][2];
        ndsRecolor(blueAcc[i][0], blueAcc[i][1], blueAcc[i][2]);
    }
    const float (*g)[3] = sel ? blueAcc : grey;
    float sh = fmaxf(1.0f, h * (2.0f / 24.0f));                       // shadow offset ~2 DS px
    drawRoundedRect(x, y + sh, w, h, r, 0.125f, 0.125f, 0.125f, 1.0f); // #202020 drop shadow
    // face gradient as horizontal bands with circular corner insets (radius r).
    const int rows = 24;
    float rowH = h / (float)rows;
    for (int i = 0; i < rows; i++) {
        float t = (float)i / (float)(rows - 1) * 11.0f;              // across the 12 stops
        int k = (int)t; if (k > 10) k = 10; float f = t - (float)k;
        float R = g[k][0] * (1.0f - f) + g[k + 1][0] * f;
        float G = g[k][1] * (1.0f - f) + g[k + 1][1] * f;
        float B = g[k][2] * (1.0f - f) + g[k + 1][2] * f;
        float yc = ((float)i + 0.5f) * rowH, ins = 0.0f;
        float dTop = yc, dBot = h - yc;
        if (dTop < r) { float e = r - dTop; ins = fmaxf(ins, r - sqrtf(fmaxf(0.0f, r * r - e * e))); }
        if (dBot < r) { float e = r - dBot; ins = fmaxf(ins, r - sqrtf(fmaxf(0.0f, r * r - e * e))); }
        drawQuad(x + ins, y + (float)i * rowH, w - 2.0f * ins, rowH + 0.6f, R, G, B, 1.0f);
    }
}

// DSi scrolling-list scrollbar, 1:1 with settings.js _drawCountryBottom + _scrollArrowVec: a
// recessed groove (x233..251, #414141 outer / #595959 inner / #9a9a9a 1px edges) with a glossy
// favColor-blue up/down arrow button (19x17, white triangle) at each end and a glossy blue thumb
// (white grip pad + #1069f3 grip lines) sized to the visible fraction. cx/offY/scale rebuild the
// caller's X/Y/S mapping so the same routine serves the settings list and the picker list.
void NanoMenu::drawNdsListScrollbar(float cx, float offY, float scale,
                                    float trackTopDS, float trackBotDS, float thumbFrac, float scrollFrac) {
    auto X = [&](float d){ return cx + (d - 128.0f) * scale; };
    auto Y = [&](float d){ return offY + d * scale; };
    auto S = [&](float v){ return v * scale; };
    const float sbx = 233.0f, sbw = 19.0f, aH = 17.0f;
    const float grooveTop = trackTopDS + aH, grooveBot = trackBotDS - aH;
    const float el = fmaxf(1.0f, S(1.0f));
    // recessed groove
    drawQuad(X(sbx),        Y(grooveTop), S(sbw),        Y(grooveBot) - Y(grooveTop), 0.255f, 0.255f, 0.255f, 1.0f); // #414141
    drawQuad(X(sbx + 2.0f), Y(grooveTop), S(sbw - 4.0f), Y(grooveBot) - Y(grooveTop), 0.349f, 0.349f, 0.349f, 1.0f); // #595959 inner
    drawQuad(X(sbx + 2.0f), Y(grooveTop), el,            Y(grooveBot) - Y(grooveTop), 0.604f, 0.604f, 0.604f, 1.0f); // #9a9a9a L edge
    drawQuad(X(sbx + 16.0f),Y(grooveTop), el,            Y(grooveBot) - Y(grooveTop), 0.604f, 0.604f, 0.604f, 1.0f); // R edge
    // glossy blue up/down arrow buttons with a white triangle (web _scrollArrowVec: cx=x+10, cy=y+9)
    auto arrow = [&](float ay, int dir){
        drawNdsGlossyBtn(X(sbx), Y(ay), S(sbw), S(aH), S(3.0f), true);
        float acx = X(sbx + 10.0f), acy = Y(ay + 9.0f);
        bool lb = !mSolidBatchActive; if (lb) beginSolidBatch();
        if (dir > 0) drawTriangle(acx, acy - S(4.0f), acx + S(5.0f), acy + S(3.0f), acx - S(5.0f), acy + S(3.0f), 0.984f, 0.984f, 0.984f, 1.0f);
        else         drawTriangle(acx, acy + S(4.0f), acx + S(5.0f), acy - S(3.0f), acx - S(5.0f), acy - S(3.0f), 0.984f, 0.984f, 0.984f, 1.0f);
        if (lb) endSolidBatch();
    };
    arrow(trackTopDS, +1);
    arrow(trackBotDS - aH, -1);
    // glossy blue thumb (sized to the visible fraction) with a white grip pad + blue grip lines
    const float grooveH = grooveBot - grooveTop;
    float tf = thumbFrac; if (tf < 0.0f) tf = 0.0f; if (tf > 1.0f) tf = 1.0f;
    float sf = scrollFrac; if (sf < 0.0f) sf = 0.0f; if (sf > 1.0f) sf = 1.0f;
    float thumbH = fmaxf(12.0f, grooveH * tf);
    if (thumbH > grooveH) thumbH = grooveH;
    float thumbY = grooveTop + (grooveH - thumbH) * sf;
    drawNdsGlossyBtn(X(sbx), Y(thumbY), S(sbw), S(thumbH), S(3.0f), true);
    float gpY = thumbY + thumbH * 0.5f - 3.5f;                                   // 11x7 grip pad centred in the thumb
    drawRoundedRect(X(sbx + 4.0f), Y(gpY), S(11.0f), S(7.0f), S(2.0f), 0.984f, 0.984f, 0.984f, 1.0f);
    { float gr = 0.063f, gg = 0.412f, gb = 0.953f; ndsRecolor(gr, gg, gb);       // #1069f3 grip lines -> accent
      for (float gy = gpY + 1.0f; gy <= gpY + 5.0f; gy += 2.0f)
          drawQuad(X(sbx + 5.0f), Y(gy), S(9.0f), el, gr, gg, gb, 1.0f); }
}

// DSi System Settings submenu screen (settings.js _renderBottom): a dark scanline background
// with a header title, a vertical scrolling stack of glossy list buttons (the current XMB
// stack level's items, the selected one favColour-blue), a right-edge scrollbar for long
// lists, and the bottom hint bar. Used for every DSi-theme submenu level (mPs3Stack non-empty).
void NanoMenu::renderNdsSubmenu(float rx, float ry, float rw, float rh) {
    setUiBlend();
    const bool ndsPrevFont = mNdsFontPref; mNdsFontPref = true;
    const int ndsPrevOutline = mTextOutlineMode; mTextOutlineMode = 2;   // DSi menu text is flat (no drop shadow / outline)
    float scale = rh / 192.0f;
    if (256.0f * scale > rw + 0.5f) scale = rw / 256.0f;
    const float offY = ry + (rh - 192.0f * scale) * 0.5f;
    const float cx = rx + rw * 0.5f;
    auto Y = [&](float d){ return offY + d * scale; };
    auto S = [&](float v){ return v * scale; };
    auto X = [&](float d){ return cx + (d - 128.0f) * scale; };

    // Resolve the current drill level generically: a submenu (mPs3Stack) OR a drilled category
    // (mPs3Cats[mPs3CatIdx], e.g. the Quick Menu). The selection index differs (stack.sel vs
    // mPs3ItemIdx) exactly as ndsNavHoriz tracks it. Root never reaches here (always the carousel).
    const std::vector<Ps3Item>* itemsP = nullptr; int selSrc = 0; std::string title;
    if (!mPs3Stack.empty()) { itemsP = &mPs3Stack.back().items; selSrc = mPs3Stack.back().sel; title = mPs3Stack.back().title; }
    else if (mPs3CatIdx >= 0 && mPs3CatIdx < (int)mPs3Cats.size()) { itemsP = &mPs3Cats[mPs3CatIdx].items; selSrc = mPs3ItemIdx; title = mPs3Cats[mPs3CatIdx].name; }
    if (!itemsP) { mTextOutlineMode = ndsPrevOutline; mNdsFontPref = ndsPrevFont; return; }
    const std::vector<Ps3Item>& items = *itemsP;
    int n = (int)items.size();
    int sel = selSrc; if (sel < 0) sel = 0; if (n > 0 && sel >= n) sel = n - 1;
    if (title.empty()) title = "Settings";

    // dark scanline background (#383838 base, #414141 every other DS row) + darker header band.
    drawQuad(rx, ry, rw, rh, 0.220f, 0.220f, 0.220f, 1.0f);
    float lh = fmaxf(1.0f, S(1.0f));
    for (float yy = ry; yy < ry + rh; yy += S(2.0f)) drawQuad(rx, yy, rw, lh, 0.255f, 0.255f, 0.255f, 1.0f);
    drawQuad(rx, ry, rw, Y(23.0f) - ry, 0.188f, 0.188f, 0.188f, 1.0f);
    for (float yy = ry; yy < Y(23.0f); yy += S(2.0f)) drawQuad(rx, yy, rw, lh, 0.220f, 0.220f, 0.220f, 1.0f);
    // header title (banner-style, white, DS x6 baseline like settings.js) + dashed rule y21.
    { float fs = S(13.0f) / (float)FONT_CHAR_H; drawText(title.c_str(), X(6.0f), Y(4.0f), fs, 0.984f, 0.984f, 0.984f, 1.0f); }
    for (float xx = X(2.0f); xx < X(254.0f); xx += S(4.0f)) {   // web dashed rule (settings.js): 1px #828282 + 1px #717171, then 2px gap
        drawQuad(xx,             Y(21.0f), fmaxf(1.0f, S(1.0f)), lh, 0.510f, 0.510f, 0.510f, 1.0f);
        drawQuad(xx + S(1.0f),   Y(21.0f), fmaxf(1.0f, S(1.0f)), lh, 0.443f, 0.443f, 0.443f, 1.0f);
    }

    // list: glossy buttons x34 w186 h24. A list that FITS is vertically centred like the real
    // DSi (_btnY: pitch 40 for <4 items, 32 for 4, y0 = round(94 - (n-1)*pitch/2 - 12)); a list
    // too long to fit scrolls at pitch 32 from listTop so the selection stays visible.
    const float bh = 24.0f, bw = 186.0f, bx = 34.0f;
    const float listTop = 30.0f, listBot = 168.0f;
    const int fitRows = (int)((listBot - listTop) / 32.0f);   // rows that fit at the scroll pitch
    const bool scrolling = n > fitRows;
    const float pitch = scrolling ? 32.0f : (n >= 4 ? 32.0f : 40.0f);
    float top0;
    if (scrolling) {
        const float dt = fmaxf(0.0f, fminf(0.1f, mFrameDt));
        const float maxScroll = (float)(n - fitRows);
        // Writable selection (stack level OR drilled category) so touch scrub / fling can move the
        // highlight; `sel` (the local used to draw the highlighted row) is updated to match this frame.
        int* selPtr = !mPs3Stack.empty() ? &mPs3Stack.back().sel
                    : (mPs3CatIdx >= 0 && mPs3CatIdx < (int)mPs3Cats.size()) ? &mPs3ItemIdx : nullptr;
        auto centreSel = [&]() {
            int c = (int)lroundf(mNdsSubScroll + (float)(fitRows / 2));
            if (c < 0) c = 0; if (c > n - 1) c = n - 1;
            sel = c; if (selPtr) *selPtr = c;
        };
        if (mNdsListScrub) {                               // (a) finger owns the scroll (set in ndsSubmenuTouch)
            if (mNdsSubScroll < 0.0f) mNdsSubScroll = 0.0f;
            if (mNdsSubScroll > maxScroll) mNdsSubScroll = maxScroll;
            centreSel(); mDisplayDirty = true;
        } else if (fabsf(mNdsListFlingVel) > 1e-4f) {      // (b) momentum fling (web 0.85/frame decay)
            mNdsSubScroll += mNdsListFlingVel * (dt * 60.0f);
            mNdsListFlingVel *= powf(0.85f, dt * 60.0f);
            if (mNdsSubScroll < 0.0f)        { mNdsSubScroll = 0.0f;      mNdsListFlingVel = 0.0f; }
            if (mNdsSubScroll > maxScroll)   { mNdsSubScroll = maxScroll; mNdsListFlingVel = 0.0f; }
            if (fabsf(mNdsListFlingVel) < 0.02f) { mNdsListFlingVel = 0.0f; mNdsSubScroll = roundf(mNdsSubScroll); }
            centreSel(); mDisplayDirty = true;
        } else {                                           // (c) D-pad: ease the scroll toward the selection
            float targetScroll = (float)sel - (float)(fitRows / 2);
            if (targetScroll < 0.0f) targetScroll = 0.0f;
            if (targetScroll > maxScroll) targetScroll = maxScroll;
            if (mListWrapSnap) {                            // a wrap jump: snap, do not scroll through the list
                mNdsSubScroll = targetScroll; mListWrapSnap = false;
            } else {
                float k = 1.0f - powf(1.0f - 0.4f, dt * 60.0f);
                mNdsSubScroll += (targetScroll - mNdsSubScroll) * k;
                if (fabsf(mNdsSubScroll - targetScroll) < 0.01f) mNdsSubScroll = targetScroll;
                else mDisplayDirty = true;
            }
        }
        top0 = listTop;
    } else {
        mNdsSubScroll = 0.0f; mNdsListFlingVel = 0.0f; mNdsListScrub = false; mNdsListThumb = false;
        top0 = roundf(94.0f - (float)(n - 1) * pitch * 0.5f - 12.0f);   // _btnY centring
    }

    for (int i = 0; i < n; i++) {
        float rowY = top0 + ((float)i - mNdsSubScroll) * pitch;
        if (rowY + bh < listTop - 1.0f || rowY > listBot + 1.0f) continue;   // clip to the list band
        drawNdsGlossyBtn(X(bx), Y(rowY), S(bw), S(bh), S(5.0f), i == sel);
        const std::string& lbl = items[i].label;
        // label: DSi banner font, cap ~12 DS px with baseline at rowTop+19 (measured off the
        // settings ref); nano drawText baseline is top+0.8*em, so S(16) at rowY+6 matches
        // (the old S(14) at rowY+4 rendered ~2px small and ~4px high in the 24px button).
        float fs = S(16.0f) / (float)FONT_CHAR_H;
        float ic = (i == sel) ? 1.0f : 0.157f;                               // white sel / #282828 idle
        // Per-row icon: draw the item's icon ONLY where it is MEANINGFUL - the settings GROUPS, the
        // Quick Menu items, and action rows that carry a real glyph (e.g. System Update = xmb_icon_008,
        // which the user confirmed the XMB shows). The ~219 leaf rows that share the generic settings
        // glyph (DATA icon index 22) stay text-only, matching the real DSi text list and avoiding a wall
        // of identical icons. Generic settings rows still HIDE their value (enter the row to change it,
        // user 2026-07-11), but game-system rows (GS_LIST On/Off list + GS_EDITOR fields) DO show their
        // value right-aligned so the enable state reads at a glance (user 2026-07-30).
        const int  iconIdx     = items[i].data ? items[i].data->icon : -1;   // -1 = Quick/dynamic (no DATA node)
        const bool genericIcon = (iconIdx == 22);                            // shared placeholder settings glyph
        const bool hasIcon     = items[i].iconTex && !genericIcon;
        const bool showVal     = (items[i].kind == PS3_GS_SYSTEM_ROW || items[i].kind == PS3_GS_FIELD
                                  || items[i].kind == PS3_CATORDER_ROW)
                                 && !items[i].value.empty();
        float rightEdge = X(bx + bw - 9.0f);
        if (showVal) {
            float vw = measureText(items[i].value.c_str(), fs);
            drawText(items[i].value.c_str(), rightEdge - vw, Y(rowY + 6.0f), fs, ic, ic, ic, 1.0f);
            rightEdge = rightEdge - vw - S(10.0f);                           // label clipped before the value
        }
        if (hasIcon) {
            float isz = S(18.0f);
            drawIconTex(items[i].iconTex, X(bx + 8.0f), Y(rowY + (bh - 18.0f) * 0.5f), isz, isz, ic, ic, ic, 1.0f);
            float labelLeft = X(bx + 32.0f);                                 // label left-aligned after the icon
            float lmax = rightEdge - labelLeft;
            float lw = measureText(lbl.c_str(), fs);
            if (lw > lmax && lmax > 0.0f) fs *= lmax / lw;
            drawText(lbl.c_str(), labelLeft, Y(rowY + 6.0f), fs, ic, ic, ic, 1.0f);
        } else if (showVal) {
            float labelLeft = X(bx + 12.0f);                                 // value row: label left, value right
            float lmax = rightEdge - labelLeft;
            float lw = measureText(lbl.c_str(), fs);
            if (lw > lmax && lmax > 0.0f) fs *= lmax / lw;
            drawText(lbl.c_str(), labelLeft, Y(rowY + 6.0f), fs, ic, ic, ic, 1.0f);
        } else {
            float tw = measureText(lbl.c_str(), fs);                         // centred DSi text row
            float maxW = S(bw - 16.0f); if (tw > maxW) { fs *= maxW / tw; tw = measureText(lbl.c_str(), fs); }
            drawText(lbl.c_str(), cx - tw * 0.5f, Y(rowY + 6.0f), fs, ic, ic, ic, 1.0f);
        }
    }
    // right-edge scrollbar: the real DSi scrolling-list bar (blue arrows + glossy thumb) when scrolling.
    if (scrolling) {
        float thumbFrac  = (float)fitRows / (float)n;
        float scrollFrac = mNdsSubScroll / (float)(n - fitRows);
        drawNdsListScrollbar(cx, offY, scale, 26.0f, 168.0f, thumbFrac, scrollFrac);
    }
    // bottom hint bar (settings.js _settingsBottomBar): #717171 top line + a #595959->#303030
    // gradient, with the Back/OK legend.
    drawQuad(rx, Y(171.0f), rw, lh, 0.443f, 0.443f, 0.443f, 1.0f);
    { const int NB = 14; float bandH = (Y(186.0f) - Y(172.0f)) / (float)NB;
      for (int b = 0; b < NB; b++) { float t = (float)b / (float)(NB - 1); float c = 0.349f * (1.0f - t) + 0.188f * t;
          drawQuad(rx, Y(172.0f) + (float)b * bandH, rw, bandH + 0.6f, c, c, c, 1.0f); }
      drawQuad(rx, Y(186.0f), rw, Y(192.0f) - Y(186.0f), 0.188f, 0.188f, 0.188f, 1.0f); }
    { float fs = S(11.0f) / (float)FONT_CHAR_H; drawText("Back", X(8.0f), Y(176.0f), fs, 0.898f, 0.898f, 0.898f, 1.0f);
      float tw = measureText("OK", fs); drawText("OK", X(248.0f) - tw, Y(176.0f), fs, 0.898f, 0.898f, 0.898f, 1.0f); }

    // enter/back fade-in: this submenu brightens from black over ~180ms after a stack change.
    if (mNdsSubTransStart > 0) {
        float a = 1.0f - (float)((int64_t)uptimeMillis() - mNdsSubTransStart) / 180.0f;
        if (a > 0.0f) { drawQuad(rx, ry, rw, rh, 0.0f, 0.0f, 0.0f, a); mDisplayDirty = true; }
        else mNdsSubTransStart = 0;
    }
    // launch white-wash carried through from the home (a submenu item can launch a game/app).
    if (!mOverlayMode && mLaunchFadeStart > 0) {
        float lf = (float)((int64_t)uptimeMillis() - mLaunchFadeStart) / (1000.0f / 60.0f);
        float fa = (lf - 3.0f) / 44.0f; if (fa < 0.0f) fa = 0.0f; if (fa > 1.0f) fa = 1.0f;
        if (fa > 0.0f) { drawQuad(rx, ry, rw, rh, 1.0f, 1.0f, 1.0f, fa); mDisplayDirty = true; }
    }
    mTextOutlineMode = ndsPrevOutline;
    mNdsFontPref = ndsPrevFont;
}

// DSi full-screen picker list (System Language / Time Zone). The XMB theme draws these as the
// fullscreen native-language list / 3D globe; in the DSi theme they must match the rest of the
// menu, so this renders the web Country/Language screen: dark scanline field, glossy button rows
// (selected = blue), centred labels, a right-edge scrollbar and the Back/OK bar. Nav + apply are
// still driven by the XMB langPickerNav/tzGlobeNav/close* handlers (ndsInModal falls through to
// them); this only restyles the render. rx/ry/rw/rh is the target panel rect (the DSi bottom
// screen). D-pad selection is `sel`; the list scrolls to keep it visible.
void NanoMenu::renderNdsPickerList(float rx, float ry, float rw, float rh, const char* title,
                                   const std::vector<std::string>& labels, int sel) {
    setUiBlend();
    const bool ndsPrevFont = mNdsFontPref; mNdsFontPref = true;
    const int ndsPrevOutline = mTextOutlineMode; mTextOutlineMode = 2;   // DSi text is flat
    float scale = rh / 192.0f;
    if (256.0f * scale > rw + 0.5f) scale = rw / 256.0f;
    const float offY = ry + (rh - 192.0f * scale) * 0.5f;
    const float cx = rx + rw * 0.5f;
    auto Y = [&](float d){ return offY + d * scale; };
    auto S = [&](float v){ return v * scale; };
    auto X = [&](float d){ return cx + (d - 128.0f) * scale; };

    int n = (int)labels.size();
    if (sel < 0) sel = 0; if (n > 0 && sel >= n) sel = n - 1;

    // dark scanline background + darker header band (identical to renderNdsSubmenu).
    drawQuad(rx, ry, rw, rh, 0.220f, 0.220f, 0.220f, 1.0f);
    float lh = fmaxf(1.0f, S(1.0f));
    for (float yy = ry; yy < ry + rh; yy += S(2.0f)) drawQuad(rx, yy, rw, lh, 0.255f, 0.255f, 0.255f, 1.0f);
    drawQuad(rx, ry, rw, Y(23.0f) - ry, 0.188f, 0.188f, 0.188f, 1.0f);
    for (float yy = ry; yy < Y(23.0f); yy += S(2.0f)) drawQuad(rx, yy, rw, lh, 0.220f, 0.220f, 0.220f, 1.0f);
    { float fs = S(13.0f) / (float)FONT_CHAR_H; drawText(title, X(6.0f), Y(4.0f), fs, 0.984f, 0.984f, 0.984f, 1.0f); }
    for (float xx = X(2.0f); xx < X(254.0f); xx += S(4.0f)) {   // web dashed rule (settings.js): 1px #828282 + 1px #717171, then 2px gap
        drawQuad(xx,             Y(21.0f), fmaxf(1.0f, S(1.0f)), lh, 0.510f, 0.510f, 0.510f, 1.0f);
        drawQuad(xx + S(1.0f),   Y(21.0f), fmaxf(1.0f, S(1.0f)), lh, 0.443f, 0.443f, 0.443f, 1.0f);
    }

    // Dense scrollable list: wide near-edge buttons (web _drawCountryBottom style) so the long zone
    // strings ("GMT+05:30  Kolkata") fit, leaving the right margin (x233+) for the DSi scrollbar. Rows
    // h24, pitch 26. A list that fits is vertically centred (_btnY); a longer one scrolls to the sel.
    const float bh = 24.0f, bw = 210.0f, bx = 17.0f;
    const float listTop = 30.0f, listBot = 168.0f, pitch = 26.0f;
    const int fitRows = (int)((listBot - listTop) / pitch);
    const bool scrolling = n > fitRows;
    float top0;
    if (scrolling) {
        float targetScroll = (float)sel - (float)(fitRows / 2);
        if (targetScroll < 0.0f) targetScroll = 0.0f;
        if (targetScroll > (float)(n - fitRows)) targetScroll = (float)(n - fitRows);
        float dt = fmaxf(0.0f, fminf(0.1f, mFrameDt)); float k = 1.0f - powf(1.0f - 0.4f, dt * 60.0f);
        mNdsSubScroll += (targetScroll - mNdsSubScroll) * k;
        if (fabsf(mNdsSubScroll - targetScroll) < 0.01f) mNdsSubScroll = targetScroll;
        else mDisplayDirty = true;
        top0 = listTop;
    } else {
        mNdsSubScroll = 0.0f;
        top0 = roundf(94.0f - (float)(n - 1) * pitch * 0.5f - 12.0f);
    }

    for (int i = 0; i < n; i++) {
        float rowY = top0 + ((float)i - mNdsSubScroll) * pitch;
        if (rowY + bh < listTop - 1.0f || rowY > listBot + 1.0f) continue;
        drawNdsGlossyBtn(X(bx), Y(rowY), S(bw), S(bh), S(5.0f), i == sel);
        float fs = S(16.0f) / (float)FONT_CHAR_H;
        float ic = (i == sel) ? 1.0f : 0.157f;                               // white sel / #282828 idle
        float tw = measureText(labels[i].c_str(), fs);
        float maxW = S(bw - 16.0f); if (tw > maxW) { fs *= maxW / tw; tw = measureText(labels[i].c_str(), fs); }
        drawText(labels[i].c_str(), cx - tw * 0.5f, Y(rowY + 6.0f), fs, ic, ic, ic, 1.0f);
    }
    // right-edge scrollbar: the real DSi scrolling-list bar (blue arrows + glossy thumb) when scrolling.
    if (scrolling && n > fitRows) {
        float thumbFrac  = (float)fitRows / (float)n;
        float scrollFrac = mNdsSubScroll / (float)(n - fitRows);
        drawNdsListScrollbar(cx, offY, scale, 26.0f, 168.0f, thumbFrac, scrollFrac);
    }
    // bottom hint bar (Back / OK), identical to renderNdsSubmenu.
    drawQuad(rx, Y(171.0f), rw, lh, 0.443f, 0.443f, 0.443f, 1.0f);
    { const int NB = 14; float bandH = (Y(186.0f) - Y(172.0f)) / (float)NB;
      for (int b = 0; b < NB; b++) { float t = (float)b / (float)(NB - 1); float c = 0.349f * (1.0f - t) + 0.188f * t;
          drawQuad(rx, Y(172.0f) + (float)b * bandH, rw, bandH + 0.6f, c, c, c, 1.0f); }
      drawQuad(rx, Y(186.0f), rw, Y(192.0f) - Y(186.0f), 0.188f, 0.188f, 0.188f, 1.0f); }
    { float fs = S(11.0f) / (float)FONT_CHAR_H; drawText("Back", X(8.0f), Y(176.0f), fs, 0.898f, 0.898f, 0.898f, 1.0f);
      float tw = measureText("OK", fs); drawText("OK", X(248.0f) - tw, Y(176.0f), fs, 0.898f, 0.898f, 0.898f, 1.0f); }

    mTextOutlineMode = ndsPrevOutline;
    mNdsFontPref = ndsPrevFont;
}

// DSi settings-options SIDE PANEL (user redesign): the Triangle option menu and the
// list/slider choosers (network settings, theme/colour pickers, GammaShader sliders) render
// as the DSi System Settings glossy list instead of the XMB side panel. The row data is
// pulled from whichever modal is active - the option menu (mPs3OptLabels, or its open
// submenu) or a dialog chooser (mPs3DlgOptions); D-pad nav is delegated to the XMB handlers
// (ndsInModal), this only restyles + drives touch (ndsSidePanelTouch). Replaces the carousel.
void NanoMenu::renderNdsSidePanel(float rx, float ry, float rw, float rh) {
    setUiBlend();
    ensureNdsAssets();
    const bool ndsPrevFont = mNdsFontPref; mNdsFontPref = true;
    const int ndsPrevOutline = mTextOutlineMode; mTextOutlineMode = 2;   // DSi menu text is flat (no drop shadow / outline)

    // Open/close fade (ap 0..1). renderXmbOpt/renderPs3Dialog are NOT called in the DSi path,
    // so tick + resolve the animation here (else a close would never clear). A close fades to
    // black then pops back to the carousel next frame.
    float dt = mFrameDt; if (dt < 0.0f) dt = 0.0f; if (dt > 0.1f) dt = 0.1f;
    const bool optSrc = (mPs3OptActive || mPs3OptClosing);
    float ap;
    if (optSrc) {
        if (mPs3OptActive) { mPs3OptClosing = false;
            mPs3OptAnim += (1.0f - mPs3OptAnim) * (1.0f - expf(-13.0f * dt));
            if (mPs3OptAnim > 0.999f) mPs3OptAnim = 1.0f; ap = mPs3OptAnim;
        } else {
            mPs3OptCloseAnim -= mPs3OptCloseAnim * (1.0f - expf(-13.0f * dt));
            if (mPs3OptCloseAnim < 0.02f) { mPs3OptCloseAnim = 0.0f; mPs3OptClosing = false; mTextOutlineMode = ndsPrevOutline; mNdsFontPref = ndsPrevFont; return; }
            ap = mPs3OptCloseAnim;
        }
    } else {
        if (mPs3DlgActive) { mPs3DlgClosing = false;
            mPs3DlgAnim += (1.0f - mPs3DlgAnim) * (1.0f - expf(-13.0f * dt));
            if (mPs3DlgAnim > 0.999f) mPs3DlgAnim = 1.0f; ap = mPs3DlgAnim;
        } else {
            mPs3DlgCloseAnim -= mPs3DlgCloseAnim * (1.0f - expf(-13.0f * dt));
            if (mPs3DlgCloseAnim < 0.02f) { mPs3DlgCloseAnim = 0.0f; mPs3DlgClosing = false; mTextOutlineMode = ndsPrevOutline; mNdsFontPref = ndsPrevFont; return; }
            ap = mPs3DlgCloseAnim;
        }
    }
    if (ap < 0.999f) mDisplayDirty = true;

    // ---- gather the visible rows + title + selection from the active modal ----
    struct Row { std::string label; bool hasSub; bool start; int swatch = -1; };
    std::vector<Row> rows; std::string title; int sel = 0;
    bool slider = false;
    if (optSrc) {
        const bool subOpen = mPs3OptSubOpen && mPs3OptSel >= 0 && mPs3OptSel < (int)mPs3OptSubRows.size()
                             && !mPs3OptSubRows[mPs3OptSel].empty();
        if (subOpen) {
            for (const auto& sr : mPs3OptSubRows[mPs3OptSel]) rows.push_back({ trDyn(sr.label.c_str()), false, false });
            sel = mPs3OptSubSel;
            title = (mPs3OptSel < (int)mPs3OptLabels.size()) ? trDyn(mPs3OptLabels[mPs3OptSel].c_str()) : "Options";
        } else {
            int n = (int)mPs3OptLabels.size();
            for (int i = 0; i < n; i++) {
                if (i < (int)mPs3OptSep.size() && mPs3OptSep[i]) continue;   // skip separators (settings list has none)
                if (i == mPs3OptSel) sel = (int)rows.size();
                rows.push_back({ trDyn(mPs3OptLabels[i].c_str()),
                                 (i < (int)mPs3OptHasSub.size() && mPs3OptHasSub[i]) != 0,
                                 (i < (int)mPs3OptStart.size() && mPs3OptStart[i]) != 0 });
            }
            title = mPs3OptCtxLabel.empty() ? "Options" : trDyn(mPs3OptCtxLabel.c_str());
        }
    } else {
        title = mPs3DlgTitle.empty() ? "Options" : trDyn(mPs3DlgTitle.c_str());
        slider = mPs3DlgSlider && mPs3DlgOptions.empty();
        for (size_t oi = 0; oi < mPs3DlgOptions.size(); oi++) {
            int sw = (oi < mPs3DlgSwatch.size()) ? mPs3DlgSwatch[oi] : -1;
            rows.push_back({ trDyn(mPs3DlgOptions[oi].c_str()), false, false, sw });
        }
        sel = mPs3DlgSel;
    }
    int n = (int)rows.size();
    if (sel < 0) sel = 0; if (n > 0 && sel >= n) sel = n - 1;

    float scale = rh / 192.0f;
    if (256.0f * scale > rw + 0.5f) scale = rw / 256.0f;
    const float offY = ry + (rh - 192.0f * scale) * 0.5f;
    const float cx = rx + rw * 0.5f;
    auto Y = [&](float d){ return offY + d * scale; };
    auto S = [&](float v){ return v * scale; };
    auto X = [&](float d){ return cx + (d - 128.0f) * scale; };
    const float lh = fmaxf(1.0f, S(1.0f));

    // dark scanline background (#383838 base, #414141 alternate rows) + darker header band.
    drawQuad(rx, ry, rw, rh, 0.220f, 0.220f, 0.220f, 1.0f);
    for (float yy = ry; yy < ry + rh; yy += S(2.0f)) drawQuad(rx, yy, rw, lh, 0.255f, 0.255f, 0.255f, 1.0f);
    drawQuad(rx, ry, rw, Y(23.0f) - ry, 0.188f, 0.188f, 0.188f, 1.0f);
    for (float yy = ry; yy < Y(23.0f); yy += S(2.0f)) drawQuad(rx, yy, rw, lh, 0.220f, 0.220f, 0.220f, 1.0f);
    { float fs = S(13.0f) / (float)FONT_CHAR_H; drawText(title.c_str(), X(6.0f), Y(4.0f), fs, 0.984f, 0.984f, 0.984f, 1.0f); }
    for (float xx = X(2.0f); xx < X(254.0f); xx += S(4.0f)) {   // web dashed rule (settings.js): 1px #828282 + 1px #717171, then 2px gap
        drawQuad(xx,             Y(21.0f), fmaxf(1.0f, S(1.0f)), lh, 0.510f, 0.510f, 0.510f, 1.0f);
        drawQuad(xx + S(1.0f),   Y(21.0f), fmaxf(1.0f, S(1.0f)), lh, 0.443f, 0.443f, 0.443f, 1.0f);
    }

    if (slider) {
        // Numeric slider (GammaShader / bound value): a centred value read-out + a favColour
        // track with a thumb; L/R (delegated to ps3DlgNav) moves the value. Range from the dialog.
        float mn = mPs3DlgSldMin, mx = mPs3DlgSldMax, v = mPs3DlgSldVal;
        float t = (mx > mn) ? (v - mn) / (mx - mn) : 0.0f; if (t < 0.0f) t = 0.0f; if (t > 1.0f) t = 1.0f;
        char buf[32];
        if (mPs3DlgSldScale <= 0) snprintf(buf, sizeof(buf), "%d", (int)lroundf(v));
        else snprintf(buf, sizeof(buf), "%.*f", mPs3DlgSldScale, v);
        { float fs = S(30.0f) / (float)FONT_CHAR_H, tw = measureText(buf, fs);
          drawText(buf, cx - tw * 0.5f, Y(74.0f), fs, 0.984f, 0.984f, 0.984f, 1.0f); }
        const float tX = X(40.0f), tW = X(216.0f) - X(40.0f), tY = Y(120.0f), tH = S(8.0f);
        drawRoundedRect(tX, tY, tW, tH, S(3.0f), 0.125f, 0.125f, 0.125f, 1.0f);
        { float sr = 0.094f, sg = 0.573f, sb = 0.922f; ndsRecolor(sr, sg, sb);   // slider fill -> accent
          drawRoundedRect(tX, tY, tW * t, tH, S(3.0f), sr, sg, sb, 1.0f); }
        float thx = tX + tW * t; drawNdsGlossyBtn(thx - S(7.0f), tY - S(7.0f), S(14.0f), S(22.0f), S(4.0f), true);
    } else if (n > 0) {
        // glossy list buttons x34 w186 h24; centred when they fit, else scroll at pitch 32.
        const float bh = 24.0f, bw = 186.0f, bx = 34.0f, listTop = 30.0f, listBot = 168.0f;
        const int fitRows = (int)((listBot - listTop) / 32.0f);
        const bool scrolling = n > fitRows;
        const float pitch = scrolling ? 32.0f : (n >= 4 ? 32.0f : 40.0f);
        float top0;
        if (scrolling) {
            float targetScroll = (float)sel - (float)(fitRows / 2);
            if (targetScroll < 0.0f) targetScroll = 0.0f;
            if (targetScroll > (float)(n - fitRows)) targetScroll = (float)(n - fitRows);
            float k = 1.0f - powf(1.0f - 0.4f, fmaxf(0.0f, fminf(0.1f, mFrameDt)) * 60.0f);
            mNdsSubScroll += (targetScroll - mNdsSubScroll) * k;
            if (fabsf(mNdsSubScroll - targetScroll) < 0.01f) mNdsSubScroll = targetScroll; else mDisplayDirty = true;
            top0 = listTop;
        } else { mNdsSubScroll = 0.0f; top0 = roundf(94.0f - (float)(n - 1) * pitch * 0.5f - 12.0f); }
        for (int i = 0; i < n; i++) {
            float rowY = top0 + ((float)i - mNdsSubScroll) * pitch;
            if (rowY + bh < listTop - 1.0f || rowY > listBot + 1.0f) continue;
            float _sr, _sg, _sb;
            if (rows[i].swatch >= 0 && ps3SwatchColor(rows[i].swatch, _sr, _sg, _sb)) {
                // Colour-chooser swatch: fill the row with the actual colour (no grey glossy drop
                // shadow), the name in luminance-contrasting ink, selected row gets a white ring.
                float bx2 = X(bx), by2 = Y(rowY), bw2 = S(bw), bh2 = S(bh), rr = S(5.0f);
                if (i == sel) drawRoundedRect(bx2 - S(2.5f), by2 - S(2.5f), bw2 + S(5.0f), bh2 + S(5.0f), rr + S(2.0f), 0.984f, 0.984f, 0.984f, 1.0f);
                drawRoundedRect(bx2, by2, bw2, bh2, rr, _sr, _sg, _sb, 1.0f);
                const std::string& lbl = rows[i].label;
                float fs = S(13.0f) / (float)FONT_CHAR_H, tw = measureText(lbl.c_str(), fs);
                float mw = S(bw - 16.0f); if (tw > mw) { fs *= mw / tw; tw = measureText(lbl.c_str(), fs); }
                float lum = 0.299f * _sr + 0.587f * _sg + 0.114f * _sb;
                float tc = (lum > 0.55f) ? 0.0f : 1.0f;
                drawText(lbl.c_str(), cx - tw * 0.5f, Y(rowY + 6.0f), fs, tc, tc, tc, 1.0f);
                continue;
            }
            drawNdsGlossyBtn(X(bx), Y(rowY), S(bw), S(bh), S(5.0f), i == sel);
            const std::string& lbl = rows[i].label;
            float fs = S(13.0f) / (float)FONT_CHAR_H, tw = measureText(lbl.c_str(), fs);
            float maxW = S(bw - (rows[i].hasSub ? 34.0f : 16.0f)); if (tw > maxW) { fs *= maxW / tw; tw = measureText(lbl.c_str(), fs); }
            float ic = (i == sel) ? 1.0f : 0.157f;
            drawText(lbl.c_str(), cx - tw * 0.5f, Y(rowY + 6.0f), fs, ic, ic, ic, 1.0f);
            if (rows[i].hasSub) { float afs = S(13.0f) / (float)FONT_CHAR_H, aw = measureText(">", afs);
                drawText(">", X(bx + bw - 14.0f) - aw * 0.5f, Y(rowY + 6.5f), afs, ic, ic, ic, 1.0f); }
            if (rows[i].start) { float pfs = S(9.0f) / (float)FONT_CHAR_H; const char* pill = "START";
                float pw = measureText(pill, pfs); float px = X(bx + bw - 8.0f) - pw - S(6.0f);
                drawQuad(px - S(4.0f), Y(rowY + 4.5f), pw + S(8.0f), S(15.0f), 0.0f, 0.0f, 0.0f, 0.28f);
                drawText(pill, px, Y(rowY + 6.5f), pfs, 0.90f, 0.90f, 0.90f, 1.0f); }
        }
        if (scrolling) {
            drawQuad(X(250.0f), Y(32.0f), S(6.0f), Y(154.0f) - Y(32.0f), 0.125f, 0.125f, 0.125f, 1.0f);
            float trackH = Y(154.0f) - Y(34.0f);
            float thumbH = trackH * (float)fitRows / (float)n;
            float thumbY = Y(34.0f) + (trackH - thumbH) * (mNdsSubScroll / (float)(n - fitRows));
            drawQuad(X(250.0f), thumbY, S(5.0f), thumbH, 0.827f, 0.827f, 0.827f, 1.0f);
        }
    }
    // bottom hint bar (Back / OK).
    drawQuad(rx, Y(171.0f), rw, lh, 0.443f, 0.443f, 0.443f, 1.0f);
    { const int NB = 14; float bandH = (Y(186.0f) - Y(172.0f)) / (float)NB;
      for (int b = 0; b < NB; b++) { float t = (float)b / (float)(NB - 1); float c = 0.349f * (1.0f - t) + 0.188f * t;
          drawQuad(rx, Y(172.0f) + (float)b * bandH, rw, bandH + 0.6f, c, c, c, 1.0f); }
      drawQuad(rx, Y(186.0f), rw, Y(192.0f) - Y(186.0f), 0.188f, 0.188f, 0.188f, 1.0f); }
    { float fs = S(11.0f) / (float)FONT_CHAR_H; drawText("Back", X(8.0f), Y(176.0f), fs, 0.898f, 0.898f, 0.898f, 1.0f);
      float tw = measureText("OK", fs); drawText("OK", X(248.0f) - tw, Y(176.0f), fs, 0.898f, 0.898f, 0.898f, 1.0f); }

    if (ap < 0.999f) drawQuad(rx, ry, rw, rh, 0.0f, 0.0f, 0.0f, 1.0f - ap);   // fade in/out from black
    mTextOutlineMode = ndsPrevOutline;
    mNdsFontPref = ndsPrevFont;
}

// DSi message-box DIALOG (user redesign): confirmations like System Update or "exit settings"
// render as the DSi light message box - a rounded panel that slides up, a title, the body
// text, and glossy Yes/No (or OK) buttons - over the dimmed carousel. Reads mPs3DlgTitle /
// mPs3DlgBody / mPs3DlgOptions / mPs3DlgSel; D-pad nav is delegated (ps3DlgNav), touch via
// ndsDialogTouch. kind-0 dialogs close instantly, so only the open slide is animated.
void NanoMenu::renderNdsDialog(float rx, float ry, float rw, float rh) {
    setUiBlend();
    ensureNdsAssets();
    const bool ndsPrevFont = mNdsFontPref; mNdsFontPref = true;
    const int ndsPrevOutline = mTextOutlineMode; mTextOutlineMode = 2;   // DSi menu text is flat (no drop shadow / outline)
    float dt = mFrameDt; if (dt < 0.0f) dt = 0.0f; if (dt > 0.1f) dt = 0.1f;
    mPs3DlgClosing = false;
    mPs3DlgAnim += (1.0f - mPs3DlgAnim) * (1.0f - expf(-15.0f * dt));
    if (mPs3DlgAnim > 0.999f) mPs3DlgAnim = 1.0f; else mDisplayDirty = true;
    const float ap = mPs3DlgAnim, ease = ap * ap * (3.0f - 2.0f * ap);

    float scale = rh / 192.0f;
    if (256.0f * scale > rw + 0.5f) scale = rw / 256.0f;
    const float offY = ry + (rh - 192.0f * scale) * 0.5f;
    const float cx = rx + rw * 0.5f;
    auto S = [&](float v){ return v * scale; };
    auto X = [&](float d){ return cx + (d - 128.0f) * scale; };
    auto Y = [&](float d){ return offY + d * scale; };

    // scrim over the carousel behind.
    drawQuad(rx, ry, rw, rh, 0.0f, 0.0f, 0.0f, 0.42f * ap);

    // panel x16 y18 w224 h156, sliding up from y118 into place.
    const float byTgt = 18.0f, byOff = 118.0f;
    const float by = byOff + (byTgt - byOff) * ease;
    const float px = X(16.0f), pw = S(224.0f), pyTop = Y(by), ph = S(156.0f);
    // DSi dialog panel: the real msk_dialog_BG sprite (white body + favColour-blue border with
    // corner AA), 224x156 at x16 - matches the web `dialog_box_<favColor>` exactly (favColour 11 =
    // blue = the device default). Falls back to the flat rounded rects if the sprite is missing.
    static GLuint gDsiDialogTex = 0;
    if (!gDsiDialogTex) gDsiDialogTex = ndsLoadTex("dialog_box_blue");
    if (gDsiDialogTex && ndsAccentIsDefault()) {
        drawIconTex(gDsiDialogTex, px, pyTop, pw, ph, 1.0f, 1.0f, 1.0f, ap);   // colour-preserving blue sprite
    } else {
        // Non-Original accent (or the sprite is missing): a procedural panel with an ACCENT-coloured
        // border around the white body, so the dialog frame follows the Colour setting too. The
        // sprite's own white body cannot be tinted without staining the body, hence the rebuild.
        float br, bg, bb; ndsAccentRGB(br, bg, bb);
        drawRoundedRect(px, pyTop + S(3.0f), pw, ph, S(6.0f), 0.05f, 0.05f, 0.05f, 0.55f * ap);   // drop shadow
        drawRoundedRect(px, pyTop, pw, ph, S(6.0f), br, bg, bb, ap);                              // accent border
        drawRoundedRect(px + S(2.0f), pyTop + S(2.0f), pw - S(4.0f), ph - S(4.0f), S(5.0f), 0.97f, 0.97f, 0.98f, ap);  // white body
    }

    // title: flat DSi ink, no drop shadow (the real DSi message-box title is flat dark grey).
    if (!mPs3DlgTitle.empty()) {
        const char* t = trDyn(mPs3DlgTitle.c_str());
        // DSi dialog title = Fonts.m (cap-height 10 DS); the DSVec face hits cap 10 at S(13)
        // (the same size the carousel name-box title was validated at). S(15) read oversized.
        float fs = S(13.0f) / (float)FONT_CHAR_H, tw = measureText(t, fs);
        float tx = cx - tw * 0.5f, ty = Y(by + 54.0f - 18.0f);
        drawText(t, tx, ty, fs, 0.28f, 0.28f, 0.30f, ap);
    }
    // body text, word-wrapped. A confirm dialog (Yes/No) keeps its short paragraph centred; an
    // info dialog (single OK - System Information, music tags, ...) can be long, so it top-anchors
    // and PAGINATES with L/R (bottom corners) instead of truncating.
    const bool infoStyle = ((int)mPs3DlgOptions.size() <= 1);
    mNdsInfoPageCount = 1;   // stays 1 unless the info body overflows (below)
    if (!mPs3DlgBody.empty()) {
        const char* body = trDyn(mPs3DlgBody.c_str());
        float fs = S(11.0f) / (float)FONT_CHAR_H;
        const float maxW = pw - S(28.0f);
        std::vector<std::string> lines; std::string cur;
        std::string src(body);
        size_t i = 0;
        auto flush = [&](){ if (!cur.empty()) { lines.push_back(cur); cur.clear(); } };
        while (i < src.size()) {
            size_t sp = src.find_first_of(" \n", i);
            std::string word = src.substr(i, sp == std::string::npos ? std::string::npos : sp - i);
            bool nl = (sp != std::string::npos && src[sp] == '\n');
            std::string trial = cur.empty() ? word : cur + " " + word;
            if (measureText(trial.c_str(), fs) > maxW && !cur.empty()) { flush(); cur = word; }
            else cur = trial;
            if (nl) flush();
            i = (sp == std::string::npos) ? src.size() : sp + 1;
        }
        flush();
        float lineH = S(13.0f);
        const int total = (int)lines.size();
        // Rows that fit between the title and the buttons (leave room for the page number).
        const float bodyTopY = Y(by + 50.0f), bodyBotY = Y(by + 100.0f);
        int maxVis = (int)((bodyBotY - bodyTopY) / lineH); if (maxVis < 1) maxVis = 1;
        if (infoStyle && total > maxVis) {
            // paginate: top-anchored body, page number ABOVE the OK button, L/R pills in the
            // bottom corners (flanking the button).
            int pages = (total + maxVis - 1) / maxVis; mNdsInfoPageCount = pages;
            if (mNdsInfoPage < 0) mNdsInfoPage = 0; if (mNdsInfoPage > pages - 1) mNdsInfoPage = pages - 1;
            int first = mNdsInfoPage * maxVis, last = first + maxVis; if (last > total) last = total;
            for (int k = first; k < last; k++) {
                float tw = measureText(lines[k].c_str(), fs);
                drawText(lines[k].c_str(), cx - tw * 0.5f, bodyTopY + (float)(k - first) * lineH, fs, 0.20f, 0.20f, 0.22f, ap);
            }
            char pg[24]; snprintf(pg, sizeof(pg), "%d / %d", mNdsInfoPage + 1, pages);
            float pf = S(9.0f) / (float)FONT_CHAR_H, pgw = measureText(pg, pf);
            drawText(pg, cx - pgw * 0.5f, Y(by + 106.0f), pf, 0.40f, 0.40f, 0.44f, ap);   // above the button
            float pillY = Y(by + 132.0f);
            float lA = (mNdsInfoPage > 0) ? 1.0f : 0.3f, rA = (mNdsInfoPage < pages - 1) ? 1.0f : 0.3f;
            auto pill = [&](float dxc, const char* g, float act){
                float gw = measureText(g, pf);
                drawRoundedRect(X(dxc) - S(9.0f), pillY - S(1.5f), S(18.0f), S(13.0f), S(3.0f), 0.28f, 0.28f, 0.30f, act * ap);
                drawText(g, X(dxc) - gw * 0.5f, pillY, pf, 1.0f, 1.0f, 1.0f, act * ap); };
            pill(30.0f, "L", lA); pill(226.0f, "R", rA);
        } else if (!infoStyle && total > maxVis) {
            // Long confirm-dialog body (Yes/No, e.g. the IPTV disclaimer): AUTO-SCROLL it as a
            // ping-pong marquee between the title and the buttons, scissor-clipped to the band, so
            // the whole message is readable instead of being truncated (user request 2026-07-11).
            const float bandTop = Y(by + 46.0f), bandBot = Y(by + 112.0f);
            const float bandH = bandBot - bandTop;
            const float contentH = lineH * (float)total;
            float off = 0.0f;
            if (contentH > bandH + 0.5f) {                          // overflows the band -> scroll
                const float scrollRange = contentH - bandH;
                const float scrollT = scrollRange / (20.0f * scale);   // 20 DS px/s
                const float pause = 1.8f, cycle = 2.0f * (pause + scrollT);
                float tt = fmodf((float)mEffectTime, cycle);
                off = (tt < pause) ? 0.0f
                    : (tt < pause + scrollT) ? (tt - pause) / scrollT * scrollRange
                    : (tt < 2.0f * pause + scrollT) ? scrollRange
                    : scrollRange - (tt - 2.0f * pause - scrollT) / scrollT * scrollRange;
                mDisplayDirty = true;                              // keep the marquee animating
            }
            glEnable(GL_SCISSOR_TEST);
            scissorLogicalRect(X(16.0f), bandTop, S(224.0f), bandH);
            for (int k = 0; k < total; k++) {
                float ty = bandTop + (float)k * lineH - off;
                if (ty > bandBot || ty + lineH < bandTop) continue;   // fully outside the band
                float tw = measureText(lines[k].c_str(), fs);
                drawText(lines[k].c_str(), cx - tw * 0.5f, ty, fs, 0.20f, 0.20f, 0.22f, ap);
            }
            glDisable(GL_SCISSOR_TEST);
        } else {
            int shown = total; if (shown > maxVis + 1) shown = maxVis + 1;
            float blockH = lineH * (float)shown;
            float y0 = Y(by + 82.0f) - blockH * 0.5f;
            for (int k = 0; k < shown; k++) {
                float tw = measureText(lines[k].c_str(), fs);
                drawText(lines[k].c_str(), cx - tw * 0.5f, y0 + (float)k * lineH, fs, 0.20f, 0.20f, 0.22f, ap);
            }
        }
    }
    // buttons (Yes x37 / No x134, or a single OK). Selected = blue glossy, else grey.
    int nOpt = (int)mPs3DlgOptions.size();
    int selOpt = mPs3DlgSel; if (selOpt < 0) selOpt = 0; if (nOpt > 0 && selOpt >= nOpt) selOpt = nOpt - 1;
    const float btnY = Y(by + 118.0f), btnH = S(32.0f), btnR = S(4.0f);
    if (otaInProgress()) {
        // Non-interactive OTA progress screen (Checking / Downloading / Reading / Preparing):
        // draw NO confirm button. It auto-advances to the reboot and a stray press is a no-op,
        // so a lone "OK" here was dead + un-DSi. Result/info dialogs (up-to-date, error) and
        // other 0-option info pages (System Information) still get their OK below.
    } else if (nOpt <= 1) {
        const char* lbl = (nOpt == 1) ? trDyn(mPs3DlgOptions[0].c_str()) : "OK";
        float bxp = X(84.0f), bwp = S(88.0f);
        drawNdsGlossyBtn(bxp, btnY, bwp, btnH, btnR, true);
        float fs = S(13.0f) / (float)FONT_CHAR_H, tw = measureText(lbl, fs);
        drawText(lbl, bxp + bwp * 0.5f - tw * 0.5f, btnY + btnH * 0.5f - S(6.5f), fs, 1.0f, 1.0f, 1.0f, ap);
    } else {
        // Two (or more) buttons laid across the panel; the first two use the canonical DSi slots,
        // any extras stack to the right. Highlight the selected one.
        for (int i = 0; i < nOpt && i < 2; i++) {
            float bxp = (i == 0) ? X(37.0f) : X(134.0f), bwp = S(89.0f);
            bool s = (i == selOpt);
            drawNdsGlossyBtn(bxp, btnY, bwp, btnH, btnR, s);
            const char* lbl = trDyn(mPs3DlgOptions[i].c_str());
            float fs = S(13.0f) / (float)FONT_CHAR_H, tw = measureText(lbl, fs);
            float maxLblW = bwp - S(10.0f);
            if (tw > maxLblW && tw > 0) { fs *= maxLblW / tw; tw = measureText(lbl, fs); }   // shrink to fit the button
            float ic = s ? 1.0f : 0.16f;
            drawText(lbl, bxp + bwp * 0.5f - tw * 0.5f, btnY + btnH * 0.5f - S(6.5f), fs, ic, ic, ic, ap);
        }
    }
    mTextOutlineMode = ndsPrevOutline;
    mNdsFontPref = ndsPrevFont;
}

// Single-panel (or stacked) DSi home. On a true dual-panel device (RG DS) the
// render() dispatch drives renderNdsTop (primary panel) and renderNdsCarousel
// (secondary panel) directly, bypassing this. Both of those are self-contained
// (blend + assets + DSVec font pref), so this only picks the single-panel layout.
// The device rect the DSi carousel/list/side-panel renders into: in the single-panel STACKED
// layout it is the BOTTOM band (the top band holds the DS top screen), otherwise the whole panel.
// Single source of truth so touch (ndsMapTouchDs) maps into exactly what renderNds draws.
void NanoMenu::ndsCarouselRect(float& rx, float& ry, float& rw, float& rh) {
    rx = 0.0f; ry = 0.0f; rw = (float)mWidth; rh = (float)mHeight;
    if (!mNdsStack) return;
    // Gap between the two stacked screens (the DS "hinge"): persist.gammaos.nano.ndstheme.gap is a
    // percentage of the panel height (0..40, default 6). 0 = the screens meet in the middle; larger
    // values push each screen toward its edge (top/bottom snap).
    char gb[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.ndstheme.gap", gb, "6");
    float gapPct = (float)atof(gb);
    if (gapPct < 0.0f) gapPct = 0.0f; if (gapPct > 40.0f) gapPct = 40.0f;
    const float gap  = (float)mHeight * (gapPct / 100.0f);
    const float half = ((float)mHeight - gap) * 0.5f;
    ry = half + gap; rh = half;
}

void NanoMenu::renderNds() {
    ensureNdsAssets();
    const float W = (float)mWidth, H = (float)mHeight;
    if (mNdsStack) {
        float crx, cry, crw, crh; ndsCarouselRect(crx, cry, crw, crh);   // bottom carousel band
        const float half = crh;          // the top screen and carousel band are equal height
        const float gap  = cry - crh;    // the DS "hinge" gap between them
        // Fill the hinge gap with the SAME background the bottom screen uses (the light DSi field,
        // or the custom wallpaper) so the two screens read as one continuous surface rather than the
        // wave showing through. Skipped in an in-game scrim so the dimmed app shows through the gap.
        if (gap > 0.5f && !(mOverlayMode && !mOverlayWallpaper)) {
            if (wallpaperActive(mRenderingPanel)) {
                drawWallpaperFill(mRenderingPanel);
            } else {
                drawQuad(0.0f, half, W, gap, 0.953f, 0.953f, 0.953f, 1.0f);       // #f3 light field
                const float ec = 1.0f;
                drawQuad(0.0f, half, ec, gap, 0.859f, 0.859f, 0.859f, 1.0f);      // #db edge columns
                drawQuad(W - ec, half, ec, gap, 0.859f, 0.859f, 0.859f, 1.0f);
            }
        }
        renderNdsTop(0.0f, 0.0f, W, half);
        renderNdsCarousel(crx, cry, crw, crh, /*singleFull=*/false);
    } else {
        renderNdsCarousel(0.0f, 0.0f, W, H, /*singleFull=*/true);
    }
}

// The DSi bottom screen (launcher carousel) drawn into a device-px rect. Contain-fit the
// 256x192 design vertically and EXPAND horizontally to fill rw (no letterbox: the bg field,
// name box and scrollbar span rw). rx/ry = top-left of the target rect.
void NanoMenu::renderNdsCarousel(float rx, float ry, float rw, float rh, bool singleFull) {
    setUiBlend();
    ensureNdsAssets();
    if (!mPs3MenuBuilt) initPs3Menu();   // the XMB hierarchy feeds the carousel tiles
    ndsSfxTick();                        // DSi interactive SFX: fire nav/drill/back/launch by state diff
    // The Time Zone and System Language selectors are full-screen. The XMB theme draws them as a
    // native-language list / 3D globe, which looks out of place inside the DSi menu; render them as
    // the DSi scrollable picker list instead (nav + apply stay on the XMB langPickerNav/tzGlobeNav/
    // close* handlers via ndsInModal). Time Zone: "GMT+hh:mm City" rows. Language: native names with
    // a live locale preview as the cursor moves (matching renderLanguagePicker).
    if (mPs3TzActive) {
        std::vector<std::string> labels; labels.reserve(mTzEntries.size());
        for (const auto& z : mTzEntries) labels.push_back(z.display);
        renderNdsPickerList(rx, ry, rw, rh, trDyn("Time Zone"), labels, mTzSelected);
        return;
    }
    if (mPs3LangActive) {
        nanoSetLocale((NanoLocale)mLangSelected);   // live locale preview as you scroll
        std::vector<std::string> labels; labels.reserve(LOCALE_COUNT);
        for (int i = 0; i < LOCALE_COUNT; i++) labels.push_back(nanoGetLocaleInfo((NanoLocale)i).nativeName);
        renderNdsPickerList(rx, ry, rw, rh, trDyn("System Language"), labels, mLangSelected);
        return;
    }
    // Modal overlays (user redesign): the Triangle option menu and the list/slider choosers
    // replace the carousel with the DSi settings-options side panel; confirm dialogs are drawn
    // OVER the carousel at the end of this function (renderNdsDialog). Side panels return early.
    if (mPs3OptActive || mPs3OptClosing ||
        ((mPs3DlgActive || mPs3DlgClosing) && ndsDlgIsSidePanel())) {
        renderNdsSidePanel(rx, ry, rw, rh);
        return;
    }
    // DSi enter/back transition: stamp a fade whenever the nav depth changes (root -> category
    // -> submenu), so the new level fades in from black (settings.js fadeIn). The full stacked
    // parent-carousel visual is layered on top of this.
    { int depth = ndsNavDepth();
      if (mNdsPrevStackDepth < 0) mNdsPrevStackDepth = depth;
      else if (depth != mNdsPrevStackDepth) {
          if (!(!mOverlayMode && mLaunchFadeStart > 0)) {
              mNdsSubTransStart = (int64_t)uptimeMillis();
              mNdsTransDir = (depth > mNdsPrevStackDepth) ? +1 : -1;   // drill down vs back up
          }
          mNdsPrevStackDepth = depth;
      } }
    // Settings screens (a drill level whose rows are all On/Off / choice settings, e.g. the Quick
    // Menu or a System Settings submenu) render as the DSi vertical LIST with each row's value inline,
    // not the horizontal carousel (user request). The depth-change fade was stamped just above, and
    // renderNdsSubmenu draws it; app/game/media levels fall through to the carousel below.
    if (ndsCurLevelIsList()) {
        renderNdsSubmenu(rx, ry, rw, rh);
        // A confirm/info dialog (kind-0) opened FROM a settings list must still draw: this list path
        // returns before the carousel's dialog dispatch below, so a dialog opened here (System
        // Information, IPTV/Radio disclaimer, Restore/Format confirms) would set mPs3DlgActive but
        // never render. Draw it OVER the list (scrim + DSi panel), matching the carousel path.
        if ((mPs3DlgActive || mPs3DlgClosing) && !ndsDlgIsSidePanel())
            renderNdsDialog(rx, ry, rw, rh);
        return;
    }
    // Stacked-carousel nav: every other level (categories root, a category's items, or a submenu)
    // is a carousel. Build the category cards once.
    if (!mNdsCatCardsBuilt) ndsBuildCatCards();
    const bool ndsPrevFont = mNdsFontPref; mNdsFontPref = true;   // DSi text uses the DSVec faces
    const int ndsPrevOutline = mTextOutlineMode; mTextOutlineMode = 2;   // DSi menu text is flat (no drop shadow / outline)
    float scale = rh / 192.0f;
    if (256.0f * scale > rw + 0.5f) scale = rw / 256.0f;   // width-limited: don't overflow
    float offY = ry + (rh - 192.0f * scale) * 0.5f;
    const float cx = rx + rw * 0.5f;                        // carousel / chrome centre
    const float W = rw;                                     // "panel width" is the rect width here
    auto Y = [&](float dy){ return offY + dy * scale; };   // DS y -> device px (offY may shift per band below)
    auto S = [&](float v){ return v * scale; };            // DS length -> device px
    // DS-x -> device px, centred: the 256-wide DSi chrome (name box, scrollbar track,
    // L/R buttons, per-slot ticks) sits centred. On the RG DS (4:3) this is edge to edge
    // (rw == 256*scale); on wider panels the bg field fills the side margins (like the web).
    auto X = [&](float dx){ return cx + (dx - 128.0f) * scale; };

    // Single-screen (lone panel) redistribution: on a taller-than-4:3 panel the 4:3 carousel
    // width-fits and leaves top+bottom letterbox. Put it to use - draw the DSi status bar
    // (radios + date/time + battery) pinned to the TOP strip and the scrollbar/navbar pinned to
    // the BOTTOM strip, and centre the name box + tiles in the band between. On a 4:3/wide panel
    // there is no letterbox room, so this is inert and the classic centred layout stands. offY is
    // shifted to the content band here; the scrollbar block temporarily swaps in ndsScrollOffY.
    const bool ndsSingleBands = singleFull && (192.0f * scale < rh - 1.0f);
    float ndsScrollOffY = offY;
    if (ndsSingleBands) {
        const float sbTopH = 17.0f * scale;                 // status-bar strip (DS y2..17)
        const float sbBotH = 22.0f * scale;                 // scrollbar strip (DS y170..192)
        const float midTop = ry + sbTopH, midBot = ry + rh - sbBotH;
        const float contentH = (161.0f - 3.0f) * scale;     // name box top (y3) .. frame bottom (y161)
        offY = midTop + ((midBot - midTop) - contentH) * 0.5f - 3.0f * scale;   // content centred in the mid band
        ndsScrollOffY = (ry + rh) - 192.0f * scale;         // pin DS y192 (scrollbar foot) to the panel bottom
    }

    // background field fills the rect (no black bars): #f3f3f3 + #ebebeb dither lines
    // + #dbdbdb edge columns at the rect edges. SKIP the opaque field while this is a translucent
    // in-game overlay (an app is running behind: mOverlayMode && !mOverlayWallpaper) so the
    // darkened live app shows through (the overlay's own sOvDim clear provides the scrim, exactly
    // like the PS3 XMB in-game overlay) instead of the DSi stripe bg covering it (user request).
    const bool ndsInGameScrim = mOverlayMode && !mOverlayWallpaper;
    if (!ndsInGameScrim) {
        if (wallpaperActive(mRenderingPanel)) {
            // Custom wallpaper fills the whole DSi bottom screen behind the carousel chrome.
            drawWallpaperFill(mRenderingPanel);
        } else {
            drawQuad(rx, ry, rw, rh, 0.953f, 0.953f, 0.953f, 1.0f);
            { float dl = S(2.0f); if (dl < 2.0f) dl = 2.0f;
              for (float y = ry; y < ry + rh; y += dl) drawQuad(rx, y, rw, fmaxf(1.0f, S(1.0f)), 0.922f, 0.922f, 0.922f, 1.0f); }
            float ec = fmaxf(1.0f, S(1.0f));
            drawQuad(rx, ry, ec, rh, 0.859f, 0.859f, 0.859f, 1.0f);
            drawQuad(rx + rw - ec, ry, ec, rh, 0.859f, 0.859f, 0.859f, 1.0f);
        }
    }

    // Single-screen: draw the DSi status bar pinned to the top strip (DS y2 mapped to the panel
    // top). The dual/stacked layouts draw it on their own top panel, so this is single-only.
    // Skipped in the in-game scrim so the darkened live app shows through the top strip too.
    if (ndsSingleBands && !ndsInGameScrim) drawNdsStatusBar(cx, ry - 2.0f * scale, scale);

    // ---- carousel content from the XMB hierarchy. At the top level it is the current
    // category's items; inside a submenu it is the current stack level's items (so
    // selecting an item that opens a sub-list shows that list, driven by the same nav). ----
    // Focused level (stacked-carousel nav): the CATEGORIES at the root, else the selected
    // category's items, else the current submenu-stack level's items.
    const std::vector<Ps3Item>* items = nullptr;
    int sel = 0;
    std::string ndsCtxTitle;   // category name / submenu title -> name-box 2nd line
    if (mNdsAtRoot) {
        items = &mNdsCatCards;
        sel = mPs3CatIdx;
        ndsCtxTitle.clear();   // root: show only the category name, no "GammaOS" second line
    } else if (!mPs3Stack.empty()) {
        items = &mPs3Stack.back().items;
        sel = mPs3Stack.back().sel;
        ndsCtxTitle = mPs3Stack.back().title;
    } else if (mPs3CatIdx >= 0 && mPs3CatIdx < (int)mPs3Cats.size()) {
        items = &mPs3Cats[mPs3CatIdx].items;
        sel = mPs3ItemIdx;
        ndsCtxTitle = mPs3Cats[mPs3CatIdx].name;
    }
    const int nItems = items ? (int)items->size() : 0;
    if (sel < 0) sel = 0;
    if (nItems > 0 && sel >= nItems) sel = nItems - 1;

    // ---- game-launch animation state (launcher._drawLaunch): once the launch fade is
    // armed, the whole carousel freezes, the centred tile rises 5px/frame and the screen
    // washes to white. lFrames = frames (60fps) since the effect began; START=3f delay. ----
    const bool  ndsLaunching = (!mOverlayMode && mLaunchFadeStart > 0);
    const float lFrames = ndsLaunching
        ? (float)((int64_t)uptimeMillis() - mLaunchFadeStart) / (1000.0f / 60.0f) : -1.0f;
    const bool  launchFx = ndsLaunching && lFrames >= 3.0f;    // effect visible after ~3f
    const float launchRise = launchFx ? (lFrames - 3.0f) * 5.0f : 0.0f;
    if (ndsLaunching) ensureNdsRing();

    // ---- boot/return entrance cascade (launcher._introFall): armed once per home
    // appearance (fresh process = replays on every return from an app), after the boot
    // intro clears and while not launching. introFrame counts 60fps frames; done at 78. ----
    if (mNdsIntroStart == 0 && !mPs3BootActive && !ndsLaunching) {
        mNdsIntroStart = (int64_t)uptimeMillis();
        mNdsCamera = (float)sel;                          // start the cascade settled on the selection
    }
    float introFrame = (mNdsIntroStart > 0)
        ? (float)((int64_t)uptimeMillis() - mNdsIntroStart) / (1000.0f / 60.0f) : 1e9f;
    const bool introActive = !ndsLaunching && introFrame < 78.0f;
    // chrome (name box + frame + START) hard-pops/fades in only after the icons settle
    // (~frame 59), cross-fading up from the "Nintendo DSi Menu" watermark over ~8f.
    float chromeA = introActive ? fmaxf(0.0f, fminf(1.0f, (introFrame - 59.0f) / 8.0f)) : 1.0f;

    // ---- animated carousel camera. Three owners, in priority: a finger scrub (camera
    // set 1:1 in ndsTouchFrame), a release momentum fling (0.85/frame ease-out then snap,
    // launcher.update), or the D-pad nav slide (fixed 7px/frame in a 58px/slot space). ----
    float target = (nItems == 0) ? 0.0f : (float)sel;
    { float dt = mFrameDt; if (dt < 0.0f) dt = 0.0f; if (dt > 0.1f) dt = 0.1f;
      const float fdt = dt * 60.0f;                                   // frames elapsed (web is per-frame @60fps)
      const float camMax = (nItems > 0) ? (float)(nItems - 1) : 0.0f;
      if (ndsLaunching) {
          /* frozen: the launch owns the scene */
      } else if (mNdsScrubbing) {
          if (mNdsCamera < 0.0f) mNdsCamera = 0.0f;
          if (mNdsCamera > camMax) mNdsCamera = camMax;               // finger owns it; just clamp
      } else if (mNdsFlingVel != 0.0f) {
          mNdsCamera += mNdsFlingVel * fdt;                           // coast
          mNdsFlingVel *= powf(0.85f, fdt);                          // ease-out decay
          if (mNdsCamera <= 0.0f)   { mNdsCamera = 0.0f;   mNdsFlingVel = 0.0f; }
          if (mNdsCamera >= camMax) { mNdsCamera = camMax; mNdsFlingVel = 0.0f; }
          if (fabsf(mNdsFlingVel) < 0.02f) mNdsFlingVel = 0.0f;
          if (mNdsFlingVel == 0.0f) {                                 // glide finished -> commit the slot, then
              // let the linear nav-slide ease the last fraction in. The web snap()
              // (launcher.js 510-516) does NOT hard-set the camera: it sets selected +
              // targetCamera = round(camera) and update()'s 7px/58px-slot slide walks the
              // camera the remaining <0.5 slot smoothly. Hard-setting mNdsCamera here popped
              // that fraction (up to ~29px) in one frame. Leave mNdsCamera fractional so the
              // else-branch below eases it to the committed slot next frames, exactly as the web.
              int snap = (int)lroundf(mNdsCamera);
              if (snap < 0) snap = 0; if (snap > (int)camMax) snap = (int)camMax;
              ndsCommitSelect(snap);
          }
      } else if (mNdsFastScroll) {
          // FAST momentum scroll (scrollbar blank-track press, launcher.scrollTo + update
          // fastScroll): ease-out ~40% of the remaining distance per frame so a far jump
          // crosses in ~0.15s then decelerates in - NOT the slow 7px/frame nav slide and
          // NOT an instant snap. Framerate-scaled to the web's per-60fps-frame k.
          float k = 1.0f - powf(1.0f - 0.40f, fdt);
          mNdsCamera += (target - mNdsCamera) * k;
          if (fabsf(mNdsCamera - target) < 0.03f) { mNdsCamera = target; mNdsFastScroll = false; }
      } else {
          if (fabsf(mNdsCamera - target) > 8.0f) mNdsCamera = target; // snap big jumps (startup / category switch)
          float step = (7.0f / 58.0f) * fdt;                          // slots this frame
          if (mNdsCamera < target)      { mNdsCamera += step; if (mNdsCamera > target) mNdsCamera = target; }
          else if (mNdsCamera > target) { mNdsCamera -= step; if (mNdsCamera < target) mNdsCamera = target; }
      } }
    const float camera = mNdsCamera;
    const bool scrubOwns = mNdsScrubbing || (mNdsFlingVel != 0.0f);
    bool camMoving = scrubOwns || introActive || ndsLaunching || fabsf(camera - target) > 0.001f;
    if (camMoving) mDisplayDirty = true;       // keep animating until settled
    // during a scrub/fling the name box + frame reflect the item under the centre cursor
    // (launcher._displaySelected), not the stale committed selection.
    int centerSlot = scrubOwns ? (int)lroundf(camera) : sel;
    if (centerSlot < 0) centerSlot = 0;
    if (nItems > 0 && centerSlot >= nItems) centerSlot = nItems - 1;
    // web _displaySelected(): the name box + top container reflect the slot under the centre
    // cursor and HARD-SWAP (never cross-fade) at 42/58 of a D-pad slide - the outgoing title
    // stays crisp until the incoming card is ~72% centred, then flips. A finger scrub tracks
    // round(camera). Published to mNdsDispSel for renderNdsTop.
    { int ns;
      if (scrubOwns) {
          ns = (int)lroundf(camera);
      } else if (fabsf(camera - (float)sel) < 0.001f) {
          ns = sel; mNdsSlideFrom = -1;                 // settled: slide finished
      } else {
          if (mNdsSlideFrom < 0) mNdsSlideFrom = mNdsDispSel;   // capture the slide origin
          float denom = (float)sel - (float)mNdsSlideFrom;
          float prog = (denom != 0.0f) ? (camera - (float)mNdsSlideFrom) / denom : 1.0f;
          ns = (prog >= 42.0f / 58.0f) ? sel : mNdsSlideFrom;
      }
      if (ns < 0) ns = 0;
      if (nItems > 0 && ns >= nItems) ns = nItems - 1;
      mNdsDispSel = ns; }
    // over a between-slots scrub the frame + START + name hide for the watermark
    const bool scrubHide = scrubOwns && fabsf(camera - (float)centerSlot) > 0.2f;
    // select-landing settle squash: on the frame the camera lands on a new item, the
    // blue selection frame insets [1,2,1]px over 3 frames then returns (config ANIM
    // settlePulseFrames 3 / settlePulseInset [1,2,1]).
    if (mNdsCamMoving && !camMoving) mNdsSettleT = 0.0f;   // fresh landing -> start the pulse
    mNdsCamMoving = camMoving;
    if (mNdsSettleT >= 0.0f) {
        mNdsSettleT += 60.0f * fmaxf(0.0f, fminf(0.1f, mFrameDt));
        if (mNdsSettleT >= 3.0f) mNdsSettleT = -1.0f;
        mDisplayDirty = true;
    }
    static const int kSettleInset[3] = {1, 2, 1};
    float frameInset = (mNdsSettleT >= 0.0f) ? (float)kSettleInset[(int)mNdsSettleT] : 0.0f;

    // JNCL slot spacing (config): 65px around the centre, 58px per gap after; a fractional
    // camera interpolates linearly between the integer anchors (matches launcher.js).
    auto slotAnchor = [](int n)->float {
        if (n == 0) return 0.0f;
        int s = n > 0 ? 1 : -1, a = n > 0 ? n : -n;
        return (float)s * (65.0f + (float)(a - 1) * 58.0f);
    };
    auto slotOffX = [&](float d)->float {
        float fl = floorf(d), fr = d - fl;
        return slotAnchor((int)fl) * (1.0f - fr) + slotAnchor((int)fl + 1) * fr;
    };

    // ---- scrollbar (DSi model, launcher.js _drawScrollTrack + _drawScrollbar) ----
    // grey rail y170..191; one tick PER game card (slot 0 anchor dot, slot 1 flat grey,
    // the rest beveled green dot-squares); a favColor thumb (29px pill + glossy window with
    // the per-card ticks redrawn on top so they read through the pill); and L/R arrow
    // buttons at the track ends. Exact DS coords via X()/Y()/S().
    {
        // Single-screen: pin the scrollbar/navbar to the bottom strip (Y() reads offY by ref, so
        // swap it to the bottom-pinned origin for this block only, then restore for the tiles).
        float ndsSbSave = offY;
        if (ndsSingleBands) offY = ndsScrollOffY;
        // FULL-WIDTH grey rail (launcher._drawScrollTrack fills x0..256), drawn BEHIND the L/R
        // arrow buttons so there is no white gap between the bar and the arrows (user report).
        float railX = X(0.0f), railW = X(256.0f) - X(0.0f);
        // exact 22-row rail gradient (launcher._drawScrollTrack): dark #82 top -> #eb light
        // band -> #aa/#a2 bottom, one grey per DS row y170..191 (was a flat #d3 approximation).
        static const int rail22[22] = {130,162,211,235,235,219,219,195,211,195,211,195,
                                        170,162,170,162,170,162,162,162,170,170};
        float rowH = S(1.0f);
        for (int r = 0; r < 22; r++) { float v = (float)rail22[r] / 255.0f;
            drawQuad(railX, Y(170.0f + (float)r), railW, rowH + 0.6f, v, v, v, 1.0f); }
        // one tick per slot at track-x = 33 + 5*i (launcher._drawScrollbar). Occupied slots
        // are beveled green SQUARES (#828a82 light top / #596959 dark edges+bottom); slot 0
        // is a 3D dark anchor dot; slot 1 is a flat grey block. All XMB items are occupied.
        auto drawTick = [&](int i){
            float cx = 33.0f + 5.0f * (float)i;
            if (X(cx) > X(236.0f)) return;
            if (i == 0) {                                       // anchor: per-pixel 3D dot, x32..35 y180..183
                static const float an[4][4] = {   // rows y180..183, cols x32..35 (grey level /255)
                    {0.616f,0.255f,0.255f,0.616f}, {0.188f,0.380f,0.380f,0.188f},
                    {0.000f,0.137f,0.137f,0.000f}, {0.510f,0.000f,0.000f,0.510f} };
                for (int ry = 0; ry < 4; ry++) for (int cxi = 0; cxi < 4; cxi++) {
                    float v = an[ry][cxi]; if (v <= 0.0f) continue;
                    drawQuad(X(32.0f + cxi), Y(180.0f + ry), S(1.0f), S(1.0f), v, v, v, 1.0f);
                }
            } else if (i == 1) {                                // flat grey first tick, x(cx-1..cx+2) y179..184
                drawQuad(X(cx - 1.0f), Y(179.0f), S(4.0f), S(6.0f), 0.510f, 0.510f, 0.510f, 1.0f);
            } else {                                            // beveled green square
                drawQuad(X(cx - 1.0f), Y(180.0f), S(4.0f), S(2.0f), 0.510f, 0.541f, 0.510f, 1.0f); // #828a82 light top
                drawQuad(X(cx - 1.0f), Y(181.0f), S(1.0f), S(3.0f), 0.349f, 0.412f, 0.349f, 1.0f); // #596959 left edge
                drawQuad(X(cx + 2.0f), Y(181.0f), S(1.0f), S(3.0f), 0.349f, 0.412f, 0.349f, 1.0f); // right edge
                drawQuad(X(cx),        Y(182.0f), S(2.0f), S(2.0f), 0.349f, 0.412f, 0.349f, 1.0f); // dark bottom centre
            }
        };
        for (int i = 0; i < nItems; i++) drawTick(i);
        // L/R arrow buttons at the track ends (favColor pills + white embossed chevrons)
        // arrows at the thumb height (y171..192, 21 DS) over the full-width rail, with a SQUARE
        // track-facing edge (innerDS=0) so they read as flat ends of the bar; only the outer
        // corner (at the screen edge) keeps a small round (outerDS=3). User: no rounded corners
        // except on the screen edges; not taller than the bar.
        drawNdsArrowBtn(X(0.0f),   Y(171.0f), S(19.0f), S(21.0f), -1, 3.0f, 0.0f);
        drawNdsArrowBtn(X(237.0f), Y(171.0f), S(19.0f), S(21.0f), +1, 3.0f, 0.0f);
        // favColor thumb over the ticks: DS left = clamp(19, 208, 19 + 5*camera); 29px wide.
        // Exactly like the web (launcher._drawScrollbar): a blue frame + a glossy interior
        // window, and the per-card ticks are REDRAWN on top of the window (clipped to it) so
        // they show through the pill. Idle = opaque glossy white (e3->fb->d3); held = the
        // pressed light-blue translucent window. Either way the ticks read through it.
        if (nItems > 0) {
            float tlDS = 19.0f + 5.0f * camera;
            if (tlDS < 19.0f)  tlDS = 19.0f;
            if (tlDS > 208.0f) tlDS = 208.0f;
            float tx = X(tlDS), tw = S(29.0f);
            drawNdsPillGrad(tx, Y(171.0f), tw, S(21.0f));                        // exact 21-stop favColor frame (r=3)
            const float ixDS = tlDS + 4.0f, iwDS = 21.0f;                        // interior window (DS x)
            float ix = X(ixDS), iw = S(iwDS), iy = Y(172.0f), ih = S(19.0f);
            if (mNdsThumbHeld) {                                                 // pressed: light-blue translucent
                drawRoundedRect(ix, iy, iw, ih, S(2.0f), 0.827f, 0.882f, 0.984f, 0.58f);
            } else {
                // idle: the EXACT web glossy window (launcher._drawScrollbar), a per-DS-row
                // gloss ramp e3 / f3 / fb x9 / e3 / db / d3 / db from y172..190, with the blue
                // pill frame curving into the interior top/bottom corners (p11 top, p9 bottom)
                // so the window reads as a rounded glass inset, not a flat white block.
                auto band = [&](float yds, float h, float v){ drawQuad(ix, Y(yds), iw + 0.6f, S(h) + 0.6f, v, v, v, 1.0f); };
                band(172.0f, 1.0f, 0.890f);   // e3 top edge
                band(173.0f, 2.0f, 0.953f);   // f3
                band(175.0f, 9.0f, 0.984f);   // fb solid white core (y175..183)
                band(184.0f, 1.0f, 0.890f);   // e3
                band(185.0f, 2.0f, 0.859f);   // db
                band(187.0f, 2.0f, 0.827f);   // d3 foot
                band(189.0f, 2.0f, 0.859f);   // db (interior runs to y190)
                // blue frame corners punched into the interior (p11 top / p9 bottom edge cols)
                float ec = fmaxf(1.0f, S(1.0f));
                drawQuad(ix,               Y(172.0f), ec, ec, 0.475f, 0.796f, 0.984f, 1.0f);
                drawQuad(ix + iw - ec,     Y(172.0f), ec, ec, 0.475f, 0.796f, 0.984f, 1.0f);
                drawQuad(ix,               Y(190.0f), ec, ec, 0.286f, 0.604f, 0.984f, 1.0f);
                drawQuad(ix + iw - ec,     Y(190.0f), ec, ec, 0.286f, 0.604f, 0.984f, 1.0f);
            }
            // ticks through the window: redraw the ones under the pill on top (web clips to it).
            for (int i = 0; i < nItems; i++) {
                float cxi = 33.0f + 5.0f * (float)i;
                if (cxi + 2.0f < ixDS || cxi - 1.0f > ixDS + iwDS) continue;     // fully outside the window
                drawTick(i);
            }
        }
        offY = ndsSbSave;   // restore the content-band origin for the tiles / name box below
    }

    // sliding tiles, centred on the panel centre (device px); the blue frame is a
    // stationary cursor at the centre. dcx = device-px centre of the tile. yoffDS lifts
    // the tile in DS px (negative = up): the launch rise + the boot/return intro cascade.
    auto drawTile = [&](float dcx, const Ps3Item* it, float yoffDS){
        float ty = Y(82.0f) + S(yoffDS);
        if (mNdsTileTex) drawIconTex(mNdsTileTex, dcx - S(32), ty, S(64), S(64), 1.0f, 1.0f, 1.0f, 1.0f);   // tile_white sprite
        else             drawRoundedRect(dcx - S(32), ty, S(64), S(64), S(9), 1.0f, 1.0f, 1.0f, 1.0f);
        if (!it) return;
        // DSi tile icon (iconTop 98 / iconSize 32, config.js). The DSi draws FLAT icons,
        // not the XMB's relit glass. Games/apps carry a full-colour iconTex (nmapTex==0)
        // drawn as-is; system items carry a mono-white iconTex + a glass nmap - draw that
        // mask as a flat DARK silhouette so it reads on the white tile (DSi-style), not
        // the wave-refracting glass. nmap-only items fall back to the glass glyph.
        // #66: a scraped ROM cover replaces the generic cartridge glyph on the tile
        // (user: boxart on the cards too). Aspect-fit into a 44x44 box centred on the
        // 64x64 pillow. romBoxartTex is async/cached and shares the Game free lifecycle.
        if (mScrapeBoxartOn && (it->kind == PS3_ROM || it->kind == PS3_RECENT)) {
            std::string rp;
            if (it->kind == PS3_ROM && it->a >= 0 && it->a < (int)mXmbSystems.size()
                && it->b >= 0 && it->b < (int)mXmbSystems[it->a].roms.size())
                rp = mXmbSystems[it->a].roms[it->b];
            else if (it->kind == PS3_RECENT && it->a >= 0 && it->a < (int)mXmbRecent.size())
                rp = mXmbRecent[it->a].romPath;
            if (!rp.empty()) {
                float bar = 1.0f; GLuint bt = romBoxartTex(rp, &bar);
                if (bt) {
                    float box = S(44.0f), cxt = dcx, cyt = Y(114.0f) + S(yoffDS);
                    float bw = box, bh = box;
                    if (bar >= 1.0f) bh = box / bar; else bw = box * bar;
                    drawIconTex(bt, cxt - bw * 0.5f, cyt - bh * 0.5f, bw, bh, 1.0f, 1.0f, 1.0f, 1.0f);
                    return;
                }
            }
        }
        float ix = dcx - S(16), iy = Y(98.0f) + S(yoffDS), sz = S(32);
        if (it->iconTex) {
            if (it->nmapTex) drawIconTex(it->iconTex, ix, iy, sz, sz, 0.28f, 0.30f, 0.36f, 1.0f);  // dark glyph
            else             drawIconTex(it->iconTex, ix, iy, sz, sz, 1.0f, 1.0f, 1.0f, 1.0f);      // colour art
        } else if (it->nmapTex && mIconGlassReady && ps3bg::workTex()) {
            drawGlassIcon(it->nmapTex, ix, iy, sz, sz, it->iconR, it->iconG, it->iconB, 1.0f);
        }
    };
    // end-of-list boundary brackets (web _drawEndCaps): light-grey #dbdbdb rounded "["/"]"
    // just outside the first/last item (virtual slots -0.9 and totalSlots-1+0.9), y82..159,
    // scrolling with the carousel. Hidden during the intro cascade and the launch (web).
    if (nItems > 0 && !introActive && !launchFx) {
        auto drawCap = [&](float vslot, bool isRight){
            float bcx = cx + S(slotOffX(vslot - camera));
            if (bcx < rx - S(30.0f) || bcx > rx + rw + S(30.0f)) return;
            float top = Y(82.0f), bot = Y(159.0f), armW = S(6.0f), t = fmaxf(1.0f, S(3.0f));
            drawQuad(bcx - t * 0.5f, top, t, bot - top, 0.859f, 0.859f, 0.859f, 1.0f);   // vertical edge
            float armX = isRight ? bcx - armW : bcx - t * 0.5f;                          // arms toward centre
            drawQuad(armX, top,         armW, t, 0.859f, 0.859f, 0.859f, 1.0f);          // top arm
            drawQuad(armX, bot - t,     armW, t, 0.859f, 0.859f, 0.859f, 1.0f);          // bottom arm
        };
        drawCap(-0.9f, false);                              // left "["
        drawCap((float)(nItems - 1) + 0.9f, true);          // right "]"
    }
    // enter/back level transition: the new line of cards slides into focus - on a drill the
    // old row falls up and the child row rises from below (dir +1 -> start below), on Back the
    // parent row drops back in from above (dir -1 -> start above), ~240ms ease-out. Replaces
    // the old black fade; all cards move together like a fresh row taking the focus.
    float transFall = 0.0f;
    if (mNdsSubTransStart > 0) {
        float el = (float)((int64_t)uptimeMillis() - mNdsSubTransStart);
        const float DUR = 240.0f, H = 130.0f;
        if (el >= DUR) mNdsSubTransStart = 0;
        else { float p = el / DUR; float ease = 1.0f - powf(1.0f - p, 3.0f);
               transFall = (mNdsTransDir >= 0 ? H : -H) * (1.0f - ease); mDisplayDirty = true; }
    }
    int base = (int)floorf(camera);
    int span = (int)(W / (2.0f * fmaxf(1.0f, S(58.0f)))) + 3;        // enough slots to fill the width
    for (int idx = base - span; idx <= base + span; idx++) {
        if (nItems == 0 || idx < 0 || idx >= nItems) continue;
        float dcx = cx + S(slotOffX((float)idx - camera));
        if (dcx < -S(50.0f) || dcx > W + S(50.0f)) continue;
        float yoff = transFall;                             // the level-transition card drop/rise
        if (introActive) {                                  // spring-fall cascade in from off-top
            float io = ndsIntroFall(idx - centerSlot, introFrame);
            if (io <= -999.0f) continue;                    // not yet visible
            yoff = io;
        }
        if (launchFx && idx == centerSlot) yoff = -launchRise;   // the launching tile lifts off the top
        drawTile(dcx, &(*items)[idx], yoff);
    }
    // sparkle ring at the fixed launch origin (128,114): one firmware cell per frame
    // (launcher_d cell_53..88), playing while the tile rises through it (launcher._drawLaunch).
    if (launchFx) {
        int rf = (int)(lFrames - 3.0f);
        if (rf >= 0 && rf <= 35 && mNdsRingTex[rf]) {       // 128x144 cell, origin baked at (64,72)
            drawIconTex(mNdsRingTex[rf], X(128.0f - 64.0f), Y(114.0f - 72.0f), S(128.0f), S(144.0f), 1.0f, 1.0f, 1.0f, 1.0f);
        }
    }
    // centre-chrome visibility: the blue frame + START + name box hide entirely during a
    // launch, a between-slots scrub, or while the scrollbar pill is held (launcher.draw),
    // and fade up over the boot/return intro (chromeA). The "Nintendo DSi Menu" watermark
    // cross-fades in their place (launcher._drawWatermark).
    const bool  hideChrome  = launchFx || scrubHide || mNdsThumbHeld;
    const float chromeAlpha = hideChrome ? 0.0f : chromeA;
    // The blue selection frame + START HARD-POP in one frame once the cascade settles (web
    // _drawCenterChrome: frameChrome = intro.frame >= 59 ? 1 : 0), NOT a fade - only the name
    // box cross-fades up with chromeA. Outside the intro both are full.
    const float frameChrome = hideChrome ? 0.0f : (introActive ? (introFrame >= 59.0f ? 1.0f : 0.0f) : 1.0f);
    const float wmA = launchFx ? 1.0f
                    : (scrubHide || mNdsThumbHeld) ? 1.0f
                    : introActive ? (1.0f - chromeA) : 0.0f;
    if (wmA > 0.004f) {                                       // faint 1.5x title-font watermark, top-centre
        const char* w = "GammaOS";                            // debranded (web app draws "Nintendo DSi Menu")
        float fs = S(20.0f) / (float)FONT_CHAR_H;
        float tw = measureText(w, fs);
        drawText(w, cx - tw * 0.5f, Y(30.0f), fs, 0.827f, 0.827f, 0.827f, wmA);   // #d3d3d3
    }

    if (frameChrome > 0.004f || chromeAlpha > 0.004f) {
      const float ca = chromeAlpha;   // name box + watermark cross-fade (boot/return intro)
      const float cf = frameChrome;    // blue frame + START: hard-pop at frame 59, no fade
      // stationary centre cursor: the REAL cell_00 selection-frame sprite (glossy blue
      // border + START platform, transparent centre) over the centred tile that the loop
      // above already drew - the tile + its icon show through the frame's transparent window.
      // Firmware geometry: 64x80, top = selFrame.top(79) + 2 = 81, centred at cx.
      // Hue-rotate the blue frame sprite toward the Colour accent while keeping its gloss/shadows
      // (value-preserving, like ndsRecolor); identity at "Original". Drawn with a neutral tint so
      // the recoloured texels show verbatim, NOT the old accent/refBlue multiply that blackened them.
      GLuint frameTex = ndsFrameTexAccented();
      if (frameTex) {
          // settle squash: inset both sides + shrink the height a touch (web _drawCenterChrome).
          float fi = S(frameInset);
          drawIconTex(frameTex, cx - S(32) + fi, Y(81), S(64) - 2.0f * fi, S(80) - fi, 1.0f, 1.0f, 1.0f, cf);
      } else {   // procedural fallback (bevel from the cell_00 palette) if the sprite is missing
          float o0r = 0.000f, o0g = 0.157f, o0b = 0.729f; ndsRecolor(o0r, o0g, o0b);
          float o1r = 0.094f, o1g = 0.443f, o1b = 0.984f; ndsRecolor(o1r, o1g, o1b);
          drawRoundedRect(cx - S(37), Y(79), S(74), S(74), S(13), o0r, o0g, o0b, cf);
          drawRoundedRect(cx - S(35), Y(81), S(70), S(70), S(11), o1r, o1g, o1b, cf);
          const Ps3Item* it = (nItems > 0 && centerSlot >= 0 && centerSlot < nItems) ? &(*items)[centerSlot] : nullptr;
          drawTile(cx, it, 0.0f);
      }

      // START caption (config startY 141). Measured web cap height ~10.25 DS px; nano cap
      // ~= 0.76*S so S(13.5) matches. White over the blue frame bottom.
      { const char* s = "START"; float fs = S(13.5f) / (float)FONT_CHAR_H;
        float tw = measureText(s, fs);
        drawText(s, cx - tw * 0.5f, Y(142.0f), fs, 1.0f, 1.0f, 1.0f, cf); }

      // name box (balloon): the DSi beveled white rounded rect (launcher.js _drawNameBoxBg,
      // which is procedural, not a sprite): concentric border dark #515151 -> grey bevel ramp
      // #a2a2a2/#c3c3c3/#dbdbdb -> white #fbfbfb interior. Firmware rect DS x3..252, y3..76
      // (centred DS coords via X(); edge-to-edge on the 4:3 RG DS). The centred item's label.
      { float bx = X(3.0f), bw = X(253.0f) - X(3.0f), by = Y(3.0f), bh = S(73.0f);
        drawRoundedRect(bx,           by,           bw,            bh,            S(6.0f), 0.318f, 0.318f, 0.318f, ca);  // #515151 outer line
        drawRoundedRect(bx + S(1.0f), by + S(1.0f), bw - S(2.0f),  bh - S(2.0f),  S(5.0f), 0.635f, 0.635f, 0.635f, ca);  // #a2a2a2
        drawRoundedRect(bx + S(2.0f), by + S(2.0f), bw - S(4.0f),  bh - S(4.0f),  S(4.0f), 0.765f, 0.765f, 0.765f, ca);  // #c3c3c3
        drawRoundedRect(bx + S(3.0f), by + S(3.0f), bw - S(6.0f),  bh - S(6.0f),  S(3.0f), 0.859f, 0.859f, 0.859f, ca);  // #dbdbdb
        drawRoundedRect(bx + S(4.0f), by + S(4.0f), bw - S(8.0f),  bh - S(8.0f),  S(2.0f), 0.984f, 0.984f, 0.984f, ca);  // #fbfbfb interior
        // Two lines like the DSi (name + publisher): the centred item's label, then its
        // category for context (launcher.js _drawNameBox is multi-line, #414141, centred).
        std::string l1, l2;
        int nameSlot = mNdsDispSel;   // web _displaySelected: hard-swaps at 42/58 of a slide
        const Ps3Item* nameItem = (items && nameSlot >= 0 && nameSlot < nItems) ? &(*items)[nameSlot] : nullptr;
        if (nameItem) l1 = nameItem->label;
        // Only a genuine data-driven settings row (PS3_DATA_LEAF: Dark Theme -> On, Screen Orientation
        // -> Landscape, Time Format -> 24-Hour Clock) shows its current value as the second line, so its
        // state is readable while scrolling without opening it. Game systems (a rom count, "Game Boy
        // Color / 3") and the Game Systems editor rows (an enabled state, "NES / On") also carry a value
        // but read as nonsense next to a proper-noun label, so they keep the category context, as do
        // apps, games and drill-in groups (which have no value anyway).
        l2 = (nameItem && !nameItem->value.empty() && nameItem->kind == PS3_DATA_LEAF)
                 ? nameItem->value : ndsCtxTitle;
        if (l1.empty()) { l1 = l2; l2.clear(); }
        if (l1.empty()) l1 = "GammaOS";
        if (!l2.empty() && l2 == l1) l2.clear();
        const float baseFs = S(13.0f) / (float)FONT_CHAR_H;   // measured web title cap ~10.5 DS px
        const float maxW = bw - S(24.0f);
        auto drawLine = [&](const std::string& s, float yc, float am){
            if (am <= 0.004f || s.empty()) return;
            float f = baseFs, tw = measureText(s.c_str(), f);
            if (tw > maxW) { f *= maxW / tw; tw = measureText(s.c_str(), f); }
            drawText(s.c_str(), cx - tw * 0.5f, yc, f, 0.255f, 0.255f, 0.255f, ca * am);   // #414141
        };
        // HARD-SWAP the label (launcher.js hard-swaps game->game via _displaySelected; only
        // populated<->empty cross-fades). ca carries the boot-intro/watermark fade only.
        float y1 = l2.empty() ? Y(31.0f) : Y(22.0f);
        drawLine(l1, y1, 1.0f);
        if (!l2.empty()) drawLine(l2, Y(40.0f), 1.0f);
      }

      // down-tab: the name box's balloon tail, drawn OVER the name box bottom (launcher._drawTab).
      // Reconstructed pixel-exact from idle.bot (y73..79): a dark #130 inverted-triangle core
      // flanked by bright #251 bevel edges with AA. Was a flat triangle pair partly hidden under
      // the name box; now the full bevelled tail shows, centred on cx (web x centred on 127.5).
      { struct TP { short y, x, v; };
        static const TP tab[] = {
          {73,121,251},{73,122,251},{73,123,251},{73,124,130},{73,125,130},{73,126,130},{73,127,130},{73,128,130},{73,129,130},{73,130,130},{73,131,251},{73,132,251},{73,133,251},
          {74,121,211},{74,122,251},{74,123,235},{74,124,162},{74,125,130},{74,126,130},{74,127,130},{74,128,130},{74,129,130},{74,130,162},{74,131,235},{74,132,251},{74,133,211},
          {75,122,211},{75,123,251},{75,124,178},{75,125,130},{75,126,130},{75,127,130},{75,128,130},{75,129,130},{75,130,178},{75,131,251},{75,132,211},
          {76,120,105},{76,121,130},{76,122,170},{76,123,251},{76,124,235},{76,125,162},{76,126,130},{76,127,130},{76,128,130},{76,129,162},{76,130,235},{76,131,251},{76,132,170},{76,133,130},{76,134,105},
          {77,125,178},{77,126,130},{77,127,130},{77,128,130},{77,129,178},
          {78,126,162},{78,127,130},{78,128,162},
          {79,126,178},{79,127,130},{79,128,178},
        };
        float pw = S(1.0f) + 0.6f;
        for (const TP& p : tab) { float v = (float)p.v / 255.0f;
          drawQuad(X((float)p.x), Y((float)p.y), pw, pw, v, v, v, ca); }
      }

      // Back button (top-left) when inside a category/submenu: a favColour arrow pill with a
      // left chevron - a visible, tappable way to escape a level (hit-tested in ndsTouchFrame,
      // drag mode 7). Hidden at the DSi category root; fades with the chrome. Half-size (user).
      if (!mNdsAtRoot && ca > 0.4f)
          drawNdsArrowBtn(X(4.0f), Y(4.0f), S(13.0f), S(13.0f), -1);

      // frame notch: the grey downward chevron that continues from the tail into the frame
      // top-centre (launcher._drawFrameNotch, y77..87), pointing at the selected item. Per-
      // pixel exact, drawn over the frame; complementary to the tab (tab = centre, notch =
      // edges then the point). Was entirely missing (the tail met a bare frame edge).
      { struct NP { short y, x, v; };
        static const NP notch[] = {
          {77,120,178},{77,121,81},{77,122,162},{77,123,211},{77,131,211},{77,132,162},{77,133,81},{77,134,178},
          {78,121,105},{78,122,130},{78,123,178},{78,124,251},{78,130,251},{78,131,178},{78,132,130},{78,133,105},
          {79,122,81},{79,123,162},{79,124,211},{79,130,211},{79,131,162},{79,132,81},
          {80,122,105},{80,123,130},{80,124,178},{80,125,251},{80,126,235},{80,127,170},{80,128,235},{80,129,251},{80,130,178},{80,131,130},{80,132,105},
          {81,123,81},{81,124,162},{81,125,211},{81,126,251},{81,127,235},{81,128,251},{81,129,211},{81,130,162},{81,131,81},
          {82,123,105},{82,124,130},{82,125,178},{82,126,235},{82,127,251},{82,128,235},{82,129,178},{82,130,130},{82,131,105},
          {83,124,81},{83,125,162},{83,126,195},{83,127,235},{83,128,195},{83,129,162},{83,130,81},
          {84,124,105},{84,125,130},{84,126,162},{84,127,178},{84,128,162},{84,129,130},{84,130,105},
          {85,125,105},{85,126,170},{85,127,162},{85,128,170},{85,129,105},
          {86,125,105},{86,126,130},{86,127,170},{86,128,130},{86,129,105},
          {87,126,105},{87,127,105},{87,128,105},
        };
        float pw = S(1.0f) + 0.6f;
        for (const NP& p : notch) { float v = (float)p.v / 255.0f;
          drawQuad(X((float)p.x), Y((float)p.y), pw, pw, v, v, v, ca); }
      }
    }

    // (the enter/back transition is now the card drop/rise above, not a black fade.)
    // A game Information page: on a device that HAS a top panel (dual, or auto-stacked single),
    // the top screen shows the cover + metadata summary and THIS (bottom) screen shows the full
    // description with L/R pagination in the bottom corners. On a true single non-stacked panel
    // there is no top, so the whole page (summary + description) renders here.
    if (ndsGameInfoActive()) {
        bool topShowsInfo = mNdsStack || mNdsHadSecondary;
        renderNdsInfoPage(rx, ry, rw, rh, topShowsInfo ? /*bottom=*/2 : /*full=*/0);
    }
    // Confirm dialog (System Update, exit settings, ...) over the carousel: the DSi message box.
    // Side-panel choosers/sliders already returned early above; this is the Yes/No button style.
    else if ((mPs3DlgActive || mPs3DlgClosing) && !ndsDlgIsSidePanel())
        renderNdsDialog(rx, ry, rw, rh);
    // launch white-wash: the DSi washes BOTH screens to white on launch. The bottom
    // (carousel) ramps over frames 3..47 (44f), linear, matching launcher.launchWhiteAlpha;
    // it stays white until nano exits and the app comes forward. mLaunchFadeStart is the
    // effect origin (shared with pollInput's exit gate; see the theme-aware threshold there).
    if (ndsLaunching) {
        float fa = (lFrames - 3.0f) / 44.0f;
        if (fa < 0.0f) fa = 0.0f;
        if (fa > 1.0f) fa = 1.0f;
        drawQuad(rx, ry, rw, rh, 1.0f, 1.0f, 1.0f, fa);
        mDisplayDirty = true;
    }
    // Boot hand-off: the carousel FADES IN from white over the first ~21 intro frames
    // (the boot's enter cover ended on full white; this continues it out) so the menu
    // dissolves in from white as the cards drop, matching the web menu-enter.
    if (introActive && introFrame < 21.0f) {
        float wf = 1.0f - introFrame / 21.0f;
        if (wf > 0.0f) { drawQuad(rx, ry, rw, rh, 1.0f, 1.0f, 1.0f, wf); mDisplayDirty = true; }
    }
    mTextOutlineMode = ndsPrevOutline;   // restore the caller's text outline mode
    mNdsFontPref = ndsPrevFont;   // restore the caller's font preference
}

// A scrollbar L/R arrow button (device-px rect): a favColor-blue glossy pill with a white
// embossed chevron pointing toward `dir` (-1 = left, +1 = right). Approximates the web
// _scrollArrowBtnVec (favColor gradient pill + white chevron over an accent shadow face).
// The exact 21-stop vertical favColour gradient pill (launcher.js _scrollArrowBtnVec /
// _drawThumbVec: grad = LAUNCHER_UC0B[favColor] indices [9,10,11,10,9,8,8,7,7,7,6,4,4,4,
// 4,4,5,5,6,7,8]). Blue (bank 11, the DSi default): light specular top, dark body, a
// lighter bottom rim. Drawn as 21 horizontal rows with the corner rows inset for the pill.
void NanoMenu::drawNdsPillGrad(float x0, float y0, float wpx, float hpx,
                               float radLeftDS, float radRightDS,
                               bool flat, float fr, float fg, float fb) {
    static const float g[21][3] = {
        {0.286f,0.604f,0.984f},{0.380f,0.698f,0.984f},{0.475f,0.796f,0.984f},  // p9 p10 p11
        {0.380f,0.698f,0.984f},{0.286f,0.604f,0.984f},{0.188f,0.510f,0.984f},  // p10 p9 p8
        {0.188f,0.510f,0.984f},{0.094f,0.443f,0.984f},{0.094f,0.443f,0.984f},  // p8 p7 p7
        {0.094f,0.443f,0.984f},{0.000f,0.349f,0.953f},{0.000f,0.220f,0.827f},  // p7 p6 p4
        {0.000f,0.220f,0.827f},{0.000f,0.220f,0.827f},{0.000f,0.220f,0.827f},  // p4 p4 p4
        {0.000f,0.220f,0.827f},{0.000f,0.286f,0.890f},{0.000f,0.286f,0.890f},  // p4 p5 p5
        {0.000f,0.349f,0.953f},{0.094f,0.443f,0.984f},{0.188f,0.510f,0.984f},  // p6 p7 p8
    };
    // Recolour the favColour gradient toward the Colour accent (identity at "Original"); the flat
    // grey-rim pass keeps its literal grey. Done once into a local copy, before the interpolation.
    float gAcc[21][3];
    const float (*gs)[3] = g;
    if (!flat) {
        for (int i = 0; i < 21; i++) {
            gAcc[i][0] = g[i][0]; gAcc[i][1] = g[i][1]; gAcc[i][2] = g[i][2];
            ndsRecolor(gAcc[i][0], gAcc[i][1], gAcc[i][2]);
        }
        gs = gAcc;
    }
    // Interpolate the 21 stops across ~4x sub-rows for a SMOOTH gradient (the web uses a
    // real linear gradient; discrete 1-DS-px rows band visibly at panel scale). The corners
    // are a crisp 3-DS-px radius arc (launcher._rrPath ro=3) - NOT the old aggressive
    // percentage-of-width cut, which read as over-rounded blue buttons on hardware.
    const int rows = 81;
    float rowH = hpx / (float)rows;
    const float radL = hpx * (radLeftDS  / 21.0f);        // left-corner radius in device px
    const float radR = hpx * (radRightDS / 21.0f);        // right-corner radius in device px (independent)
    auto cornerInset = [&](float yc, float rad) -> float { // circular inset for this row's top/bottom corner
        if (rad <= 0.0f) return 0.0f;
        float ins = 0.0f, dTop = yc, dBot = hpx - yc;
        if (dTop < rad) { float e = rad - dTop; ins = fmaxf(ins, rad - sqrtf(fmaxf(0.0f, rad * rad - e * e))); }
        if (dBot < rad) { float e = rad - dBot; ins = fmaxf(ins, rad - sqrtf(fmaxf(0.0f, rad * rad - e * e))); }
        return ins;
    };
    for (int r = 0; r < rows; r++) {
        float t = (float)r / (float)(rows - 1) * 20.0f;   // position across the 21 stops
        int i = (int)t; if (i > 19) i = 19; float f = t - (float)i;
        float R = gs[i][0] * (1.0f - f) + gs[i + 1][0] * f;
        float G = gs[i][1] * (1.0f - f) + gs[i + 1][1] * f;
        float B = gs[i][2] * (1.0f - f) + gs[i + 1][2] * f;
        if (flat) { R = fr; G = fg; B = fb; }             // grey-rim / flat-fill mode
        float yc = ((float)r + 0.5f) * rowH;              // row centre from the top
        float insL = cornerInset(yc, radL);              // left  corner inset (outer=3 rounded, inner=1.5 square)
        float insR = cornerInset(yc, radR);              // right corner inset (independent per side)
        drawQuad(x0 + insL, y0 + (float)r * rowH, wpx - insL - insR, rowH + 0.6f, R, G, B, 1.0f);
    }
}

// A scrollbar L/R arrow button: the exact 21-stop favColour gradient pill with a white
// embossed chevron pointing toward `dir` (-1=left, +1=right). Chevron geometry is the exact
// launcher.js _scrollArrowBtnVec: base edge at DS x5/x14, tip at x15/x4, top y177 / mid y181
// / bottom y185 within the 19x21 button; white top face over an accent shadow face (p12).
void NanoMenu::drawNdsArrowBtn(float x0, float y0, float wpx, float hpx, int dir,
                               float outerDS, float innerDS) {
    // grey #105 outer rim (launcher._scrollArrowBtnVec strokes a 0.6px rgb(105) hairline
    // around the rounded pill; the thumb has NO rim, only the arrows do). Draw it as a
    // slightly-LARGER grey rounded-rect BEHIND the FULL-SIZE gradient pill so the blue
    // button keeps its exact 21-DS height (matching the thumb + web) and the grey reads
    // as a thin outline. Insetting the pill instead shrank the arrow ~1.2 DS shorter than
    // the thumb (user report: the arrow button height did not match).
    // per-direction corner radii (DS px): OUTER = 3 (rounded), INNER track-facing = 1.5 (reads square/flush).
    // Matches launcher.js _scrollArrowBtnVec _rrPath([ro,ri,ri,ro]) for the left arrow / [ri,ro,ro,ri] for the right,
    // so the arrow reads as part of the bar (only the outer corners round; the track-facing edge is square).
    const float radLeftDS  = (dir < 0) ? outerDS : innerDS;   // left arrow: left side is outer
    const float radRightDS = (dir < 0) ? innerDS : outerDS;   // right arrow: right side is outer
    // grey #105 rim (the 0.6px rgb(105) hairline the web strokes around the pill), drawn as a flat-grey
    // copy of the SAME asymmetric pill shape BEHIND the full-size blue pill, grown outward ONLY on the
    // outer (rounded) side + top/bottom. The inner (track-facing) side stays flush so no grey seam appears
    // against the rail. The blue pill stays full 21-DS height so the arrow height still matches the thumb.
    float g = fmaxf(1.0f, hpx * (0.6f / 21.0f));
    float gx0 = (dir < 0) ? (x0 - g) : x0;                    // expand on the outer (rounded) side ONLY -
    float gw  = wpx + g;                                      // NOT vertically, so the arrow never pokes
    drawNdsPillGrad(gx0, y0, gw, hpx, radLeftDS, radRightDS, true, 0.412f, 0.412f, 0.412f);   // above/below the bar. grey rim
    drawNdsPillGrad(x0, y0, wpx, hpx, radLeftDS, radRightDS);                                  // blue pill on top
    bool lb = !mSolidBatchActive; if (lb) beginSolidBatch();
    float bx   = x0 + wpx * ((dir < 0 ? 14.0f : 5.0f) / 19.0f);   // base vertical edge
    float tx   = x0 + wpx * ((dir < 0 ?  4.0f : 15.0f) / 19.0f);  // tip toward dir
    float ytop = y0 + hpx * (6.0f  / 21.0f);
    float ymid = y0 + hpx * (10.0f / 21.0f);
    float ybot = y0 + hpx * (14.0f / 21.0f);
    drawTriangle(bx, ytop, bx, ymid, tx, ymid, 0.984f, 0.984f, 0.984f, 1.0f);   // white top face
    { float ar = 0.573f, ag = 0.859f, ab = 0.984f; ndsRecolor(ar, ag, ab);      // accent shadow (p12) -> accent
      drawTriangle(bx, ymid, bx, ybot, tx, ymid, ar, ag, ab, 1.0f); }
    if (lb) endSolidBatch();
}

// DSi game Information page, drawn into a device-px rect (the top-screen mint canvas on a
// dual-screen device, the whole screen on single). Mirrors the PS3 XMB rich ROM Information
// (cover + scraped metadata + wrapped description + file facts) but adapted to DSi sizes and
// the mint canvas, and PAGINATED with L/R instead of a scroll bar. The unscraped case shows
// the file-facts body. mNdsInfoPageCount is recomputed here so the input clamp and the L/R
// indicator stay in sync. topScreen just tints the caption colours to the DSi teal set.
void NanoMenu::renderNdsInfoPage(float rx, float ry, float rw, float rh, int part) {
    // part: 0 = full (single screen), 1 = top summary (cover + metadata), 2 = bottom (description).
    const bool wantSummary = (part != 2);   // cover + metadata + fanart
    const bool wantDesc    = (part != 1);   // description + L/R pager
    setUiBlend();
    ensureNdsAssets();
    const bool ndsPrevFont = mNdsFontPref; mNdsFontPref = true;
    const int ndsPrevOutline = mTextOutlineMode; mTextOutlineMode = 2;
    float dt = mFrameDt; if (dt < 0.0f) dt = 0.0f; if (dt > 0.1f) dt = 0.1f;
    mPs3DlgClosing = false;
    mPs3DlgAnim += (1.0f - mPs3DlgAnim) * (1.0f - expf(-15.0f * dt));
    if (mPs3DlgAnim > 0.999f) mPs3DlgAnim = 1.0f; else mDisplayDirty = true;
    const float ap = mPs3DlgAnim;

    float scale = rh / 192.0f;
    if (256.0f * scale > rw + 0.5f) scale = rw / 256.0f;
    const float offY = ry + (rh - 192.0f * scale) * 0.5f;
    const float cx = rx + rw * 0.5f;
    auto S = [&](float v){ return v * scale; };
    auto X = [&](float d){ return cx + (d - 128.0f) * scale; };
    auto Y = [&](float d){ return offY + d * scale; };
    const float FCH = (float)FONT_CHAR_H;
    const float hR = 0.235f, hG = 0.463f, hB = 0.427f;   // #3b766d label teal
    const float vR = 0.145f, vG = 0.325f, vB = 0.298f;   // darker value/body
    auto fit = [&](const char* s, float wantFs, float maxW){ float f = wantFs; float w = measureText(s, f); if (w > maxW && w > 0) f *= maxW / w; return f; };

    // light upper-screen background + side edge shading (matches renderNdsTop).
    drawQuad(rx, ry, rw, rh, 0.965f, 0.965f, 0.965f, 1.0f);
    { float ec = fmaxf(1.0f, S(1.0f));
      drawQuad(X(0.0f), ry, ec, rh, 0.859f, 0.859f, 0.859f, 1.0f);
      drawQuad(X(255.0f), ry, ec, rh, 0.859f, 0.859f, 0.859f, 1.0f); }
    // mint canvas: procedural bevel -> white inset -> mint field (no photo_U camera glyph).
    const float cvL = 18.0f, cvT = 18.0f, cvRr = 240.0f, cvB = 188.0f;
    { float px = X(cvL), pw = X(cvRr) - X(cvL), py = Y(cvT), ph = Y(cvB) - Y(cvT);
      drawRoundedRect(px - S(2), py - S(2), pw + S(4), ph + S(4), S(4), 0.812f, 0.812f, 0.812f, ap);
      drawRoundedRect(px, py, pw, ph, S(3), 1.0f, 1.0f, 1.0f, ap);
      drawRoundedRect(px + S(3), py + S(3), pw - S(6), ph - S(6), S(2), 0.678f, 0.839f, 0.808f, ap); }
    // Faint scraped fanart CONTAINED inside the mint field (fit-inside, never overflowing the
    // green canvas), like the PS3 info backdrop.
    if (wantSummary && mPs3DlgFanTex && mPs3DlgFanW > 0 && mPs3DlgFanH > 0) {
        float fx = X(22.0f), fy = Y(22.0f), fw = X(236.0f) - X(22.0f), fh = Y(184.0f) - Y(22.0f);
        float ar = (float)mPs3DlgFanW / (float)mPs3DlgFanH, tw = fw, th = tw / ar;
        if (th > fh) { th = fh; tw = th * ar; }   // contain: shrink to fit inside the canvas
        drawIconTex(mPs3DlgFanTex, fx + (fw - tw) * 0.5f, fy + (fh - th) * 0.5f, tw, th, 1.0f, 1.0f, 1.0f, 0.16f * ap);
    }

    // ---- title (game name) ----
    { const char* t = mPs3DlgTitle.empty() ? "Information" : mPs3DlgTitle.c_str();
      float f = fit(t, S(13.0f) / FCH, S(210.0f)); float tw = measureText(t, f);
      drawText(t, cx - tw * 0.5f, Y(27.0f), f, hR, hG, hB, ap); }
    // Touch Back chevron (top-left, half-size to match the carousel) on the interactive panel
    // (bottom/full), so the page closes without the physical B button.
    if (part != 1) drawNdsArrowBtn(X(4.0f), Y(4.0f), S(13.0f), S(13.0f), -1);

    // ================= SUMMARY (cover + metadata), top/full =================
    if (wantSummary) {
        bool hasCover = mPs3DlgRomInfo && !mPs3DlgPendingBox.empty();
        float contentL = 24.0f;
        if (hasCover) {
            float bxL = 24.0f, bxT = 42.0f, bxW = 58.0f, bxH = 92.0f;   // fits inside the canvas
            if (mPs3DlgBoxTex && mPs3DlgBoxW > 0 && mPs3DlgBoxH > 0) {
                float car = (float)mPs3DlgBoxW / (float)mPs3DlgBoxH;
                float cwd = S(bxW), chd = cwd / car; if (chd > S(bxH)) { chd = S(bxH); cwd = chd * car; }
                float cvx = X(bxL) + (S(bxW) - cwd) * 0.5f, cvy = Y(bxT) + (S(bxH) - chd) * 0.5f;
                drawQuad(cvx - S(1.5f), cvy - S(1.5f), cwd + S(3.0f), chd + S(3.0f), 1.0f, 1.0f, 1.0f, 0.9f * ap);
                drawIconTex(mPs3DlgBoxTex, cvx, cvy, cwd, chd, 1.0f, 1.0f, 1.0f, ap);
            }
            contentL = 92.0f;
        }
        const float metaLabelFs = S(10.0f) / FCH, metaValFs = S(11.0f) / FCH;
        float vy = 46.0f;
        if (mPs3DlgRomInfo) {
            auto mrow = [&](const char* label, const std::string& val){
                if (val.empty()) return;
                drawText(label, X(contentL), Y(vy), metaLabelFs, hR, hG, hB, 0.85f * ap);
                float f = fit(val.c_str(), metaValFs, X(232.0f) - X(contentL + 54.0f));   // value stays inside canvas
                drawText(val.c_str(), X(contentL + 54.0f), Y(vy), f, vR, vG, vB, ap);
                vy += 13.0f;
            };
            mrow("Genre",     mPs3RomInfoGenre);
            mrow("Players",   mPs3RomInfoPlayers);
            mrow("Rating",    mPs3RomInfoRating);
            mrow("Released",  mPs3RomInfoDate);
            mrow("Developer", mPs3RomInfoDev);
            mrow("Publisher", mPs3RomInfoPub);
            // File facts (small, dim) under the metadata when there is room (full/top summary).
            struct FF { const char* l; const std::string* v; };
            const char* coreL = mPs3RomInfoCoreIsApp ? "App" : "Core";
            FF ff[] = { {"File", &mPs3RomInfoFileName}, {"Size", &mPs3RomInfoSize}, {coreL, &mPs3RomInfoCore}, {"System", &mPs3RomInfoSystem} };
            float fyy = fmaxf(vy + 3.0f, 132.0f);
            for (auto& e : ff) { if (e.v->empty() || fyy > 182.0f) continue;
                drawText(e.l, X(contentL), Y(fyy), S(8.0f)/FCH, hR, hG, hB, 0.7f*ap);
                float f = fit(e.v->c_str(), S(8.0f)/FCH, X(232.0f) - X(contentL + 40.0f));
                drawText(e.v->c_str(), X(contentL + 40.0f), Y(fyy), f, vR, vG, vB, 0.85f*ap); fyy += 10.0f; }
        }
    }

    // ================= DESCRIPTION (bottom/full) =================
    if (wantDesc) {
        std::string bodyText = mPs3DlgRomInfo ? mPs3RomInfoSyn : mPs3DlgBody;
        // Bottom screen gives the description the whole canvas and a larger, readable font.
        bool bottomOnly = (part == 2);
        float bodyFs = (bottomOnly ? S(12.0f) : S(10.0f)) / FCH;
        float lineH  = bottomOnly ? 16.0f : 13.0f;
        float capY   = bottomOnly ? 42.0f : 116.0f;
        float bodyTop = bottomOnly ? 58.0f : 116.0f;
        float bodyBot = 178.0f;
        float bodyLeft = 24.0f, contentR = 236.0f;
        float wrapW = X(contentR) - X(bodyLeft);
        std::vector<std::string> lines;
        if (!bodyText.empty()) {
            std::string line, word;
            auto commit = [&](){ if (word.empty()) return;
                std::string trial = line.empty() ? word : line + " " + word;
                if (!line.empty() && measureText(trial.c_str(), bodyFs) > wrapW) { lines.push_back(line); line = word; }
                else line = trial; word.clear(); };
            for (const char* q = bodyText.c_str(); ; ++q) {
                if (*q == ' ' || *q == '\n' || *q == '\0') { commit(); if (*q == '\n') lines.push_back(""); if (*q == '\0') break; }
                else word.push_back(*q);
            }
            if (!line.empty()) lines.push_back(line);
        }
        int linesPerPage = (int)((bodyBot - bodyTop) / lineH); if (linesPerPage < 1) linesPerPage = 1;
        int total = (int)lines.size();
        int pages = (total + linesPerPage - 1) / linesPerPage; if (pages < 1) pages = 1;
        mNdsInfoPageCount = pages;
        if (mNdsInfoPage < 0) mNdsInfoPage = 0;
        if (mNdsInfoPage > pages - 1) mNdsInfoPage = pages - 1;
        if (total > 0) drawText("Description", X(bodyLeft), Y(capY), S(9.0f) / FCH, hR, hG, hB, 0.85f * ap);
        float ly = bodyTop;
        int firstL = mNdsInfoPage * linesPerPage, lastL = firstL + linesPerPage; if (lastL > total) lastL = total;
        for (int i = firstL; i < lastL; i++) { if (!lines[i].empty()) drawText(lines[i].c_str(), X(bodyLeft), Y(ly), bodyFs, vR, vG, vB, ap); ly += lineH; }

        // ---- L/R pager at the BOTTOM CORNERS of the screen (only when there is >1 page) ----
        if (pages > 1) {
            float by = Y(181.0f);
            char pg[24]; snprintf(pg, sizeof(pg), "%d / %d", mNdsInfoPage + 1, pages);
            float pf0 = S(9.0f) / FCH, pgw = measureText(pg, pf0);
            drawText(pg, cx - pgw * 0.5f, by, pf0, hR, hG, hB, ap);
            float lAct = (mNdsInfoPage > 0) ? 1.0f : 0.3f;
            float rAct = (mNdsInfoPage < pages - 1) ? 1.0f : 0.3f;
            auto pill = [&](float dxc, const char* g, float act){
                float pf = S(9.0f) / FCH, gw = measureText(g, pf);
                drawRoundedRect(X(dxc) - S(9.0f), by - S(1.5f), S(18.0f), S(13.0f), S(3.0f), hR, hG, hB, act * ap);
                drawText(g, X(dxc) - gw * 0.5f, by, pf, 1.0f, 1.0f, 1.0f, act * ap);
            };
            pill(14.0f, "L", lAct);    // bottom-left corner
            pill(242.0f, "R", rAct);   // bottom-right corner
        }
    }

    mTextOutlineMode = ndsPrevOutline;
    mNdsFontPref = ndsPrevFont;
}

void NanoMenu::ndsInfoPage(int dir) {
    int p = mNdsInfoPage + dir;
    if (p < 0) p = 0;
    if (p > mNdsInfoPageCount - 1) p = mNdsInfoPageCount - 1;
    if (p != mNdsInfoPage) { mNdsInfoPage = p; mDisplayDirty = true; }
}
// DSi status bar (topscreen.js): radio/audio glyphs on the left, date/time + battery on
// the right, at DS y2..17. Factored out of renderNdsTop so the single-screen carousel
// can draw the same bar pinned to its top strip. cx/offY/scale map DS -> device px
// exactly as the caller's X()/Y()/S().
void NanoMenu::drawNdsStatusBar(float cx, float offY, float scale) {
    auto X = [&](float dx){ return cx + (dx - 128.0f) * scale; };
    auto Y = [&](float dy){ return offY + dy * scale; };
    auto S = [&](float v){ return v * scale; };
    // ---- status bar (DS y2..17): a row of four consistent indicator glyphs on the left
    // (volume / wifi / bluetooth / audio), then date/time + battery on the right. Every
    // glyph shares ONE centre line (cy) and ONE stroke weight, is drawn to the same ~9px
    // box height, and its cell centre is evenly spaced (uniform pitch) so the row reads as
    // a single tidy cluster. Radios/audio reflect the REAL state (pollNdsStatus). ----
    { pollVolume();       // system volume -> arc count
      pollNdsStatus();    // wifi / bluetooth / audio active
      const float cy = 10.5f;          // shared icon centre line (DS)
      const float lw = S(1.25f);       // shared stroke weight
      const float on_r = 0.255f, on_g = 0.255f, on_b = 0.255f;   // #414141 active
      const float of_r = 0.741f, of_g = 0.741f, of_b = 0.741f;   // #bdbdbd inactive
      // Even cell centres (uniform pitch). Each glyph is built symmetric about its centre.
      const float cVol = 12.0f, cWifi = 27.0f, cBt = 41.0f, cNote = 55.0f;

      // Crisp framework SystemUI vector glyphs (rasterised to mono PNGs), tinted per state.
      // Falls back to the procedural glyphs below if the PNGs did not load (first boot before
      // the assets bundle, or a decode miss). User: the old hand-drawn icons were too low quality.
      if (mNdsSbIconsLoaded) {
          auto sbIcon = [&](GLuint tex, float cX, float sz, float r, float g, float b){
              if (!tex) return;
              drawIconTex(tex, X(cX) - S(sz) * 0.5f, Y(cy) - S(sz) * 0.5f, S(sz), S(sz), r, g, b, 1.0f);
          };
          // Volume speaker (mute variant when silenced); always the active ink.
          sbIcon((mVolume <= 0) ? mNdsSbSpeakerMute : mNdsSbSpeaker, cVol, 13.5f, on_r, on_g, on_b);
          // WiFi: dark when connected, a mid grey when on-not-associated, dimmed when off.
          { int ws = mNdsWifiState; bool conn = ws >= 2, on = ws >= 1;
            float r = conn ? on_r : (on ? 0.5f : of_r), g = conn ? on_g : (on ? 0.5f : of_g), b = conn ? on_b : (on ? 0.5f : of_b);
            sbIcon(mNdsSbWifi, cWifi, 12.0f, r, g, b); }
          // Bluetooth: dark when the radio is on, dimmed when off.
          { float r = mNdsBtOn ? on_r : of_r, g = mNdsBtOn ? on_g : of_g, b = mNdsBtOn ? on_b : of_b;
            sbIcon(mNdsSbBt, cBt, 12.5f, r, g, b); }
          // Music note: dark when audio genuinely plays (nano's players, or a fg app over the overlay).
          { bool musicOn = mMusicPlayer.isPlaying() || mVidAudio.isPlaying()
                           || (mOverlayMode && !mOverlayWallpaper && mNdsAudioActive);
            float r = musicOn ? on_r : of_r, g = musicOn ? on_g : of_g, b = musicOn ? on_b : of_b;
            sbIcon(mNdsSbNote, cNote, 12.0f, r, g, b); }
      } else {

      // --- Volume: speaker (base box + cone) with 1..3 level arcs, mute = red slash. The
      // arcs are capped to r5.4 so a loud level no longer balloons the icon far wider than
      // its neighbours. Built symmetric about cVol.
      { int vmax = (mMaxVolume > 0) ? mMaxVolume : 15;
        float vratio = (float)mVolume / (float)vmax; if (vratio > 1.0f) vratio = 1.0f;
        bool muted = (mVolume <= 0);
        int arcs = muted ? 0 : (int)ceilf(vratio * 3.0f); if (arcs > 3) arcs = 3; if (!muted && arcs < 1) arcs = 1;
        float ax = cVol - 1.0f;   // arc/cone origin x
        bool lb = !mSolidBatchActive; if (lb) beginSolidBatch();
        drawTriangle(X(ax - 2.5f), Y(cy - 1.5f), X(ax), Y(cy - 4.2f), X(ax), Y(cy + 4.2f), on_r, on_g, on_b, 1.0f); // cone
        drawTriangle(X(ax - 2.5f), Y(cy - 1.5f), X(ax), Y(cy + 4.2f), X(ax - 2.5f), Y(cy + 1.5f), on_r, on_g, on_b, 1.0f);
        if (lb) endSolidBatch();
        drawQuad(X(ax - 4.5f), Y(cy - 1.5f), S(2.0f), S(3.0f), on_r, on_g, on_b, 1.0f);   // speaker base box
        // Feather the speaker silhouette (drawTriangle/drawQuad are hard-edged): stroke the
        // outline with the AA ps3ThickLine in the same ink so the slanted cone edges match
        // the smoothness of the wifi/bt/note glyphs. A thin width keeps the shape unchanged.
        { const float sw = S(1.0f); auto e = [&](float x0,float y0,float x1,float y1){ ps3ThickLine(X(x0),Y(y0),X(x1),Y(y1),sw,on_r,on_g,on_b,1.0f); };
          e(ax - 2.5f, cy - 1.5f, ax, cy - 4.2f);   // cone slant top
          e(ax, cy - 4.2f, ax, cy + 4.2f);          // cone mouth
          e(ax, cy + 4.2f, ax - 2.5f, cy + 1.5f);   // cone slant bottom
          e(ax - 2.5f, cy + 1.5f, ax - 4.5f, cy + 1.5f);   // base bottom
          e(ax - 4.5f, cy + 1.5f, ax - 4.5f, cy - 1.5f);   // base left
          e(ax - 4.5f, cy - 1.5f, ax - 2.5f, cy - 1.5f); } // base top
        auto arc = [&](float r){
            const int N = 6; const float a0 = -0.85f, a1 = 0.85f;
            float px = X(ax + r * cosf(a0)), py = Y(cy + r * sinf(a0));
            for (int i = 1; i <= N; i++) {
                float a = a0 + (a1 - a0) * (float)i / (float)N;
                float nx = X(ax + r * cosf(a)), ny = Y(cy + r * sinf(a));
                ps3ThickLine(px, py, nx, ny, lw, on_r, on_g, on_b, 1.0f); px = nx; py = ny;
            } };
        if (muted) ps3ThickLine(X(ax + 1.0f), Y(cy - 3.8f), X(ax + 5.0f), Y(cy + 3.8f), lw, 0.85f, 0.25f, 0.25f, 1.0f);
        else { if (arcs >= 1) arc(2.4f); if (arcs >= 2) arc(3.9f); if (arcs >= 3) arc(5.4f); }
      }
      // --- WiFi: a source dot with concentric arcs opening upward, symmetric about cWifi.
      // State 2 = connected (dark), 1 = on-not-associated (inner arc only), 0 = off (dot).
      { float wcy = cy + 3.2f; int ws = mNdsWifiState;
        auto warc = [&](float rad, bool active){
            float r = active ? on_r : of_r, g = active ? on_g : of_g, b = active ? on_b : of_b;
            const int N = 8; const float a0 = -2.36f, a1 = -0.78f;   // ~90 deg upward fan
            float px = X(cWifi + rad * cosf(a0)), py = Y(wcy + rad * sinf(a0));
            for (int i = 1; i <= N; i++) {
                float a = a0 + (a1 - a0) * (float)i / (float)N;
                float nx = X(cWifi + rad * cosf(a)), ny = Y(wcy + rad * sinf(a));
                ps3ThickLine(px, py, nx, ny, lw, r, g, b, 1.0f); px = nx; py = ny;
            } };
        warc(6.6f, ws >= 2);
        warc(4.5f, ws >= 2);
        warc(2.4f, ws >= 1);
        bool srcOn = ws >= 1;
        ps3FillCircle(X(cWifi), Y(wcy), S(1.2f), srcOn ? on_r : of_r, srcOn ? on_g : of_g, srcOn ? on_b : of_b, 1.0f);
      }
      // --- Bluetooth rune: vertical staff, two right knuckles, two crossing diagonals whose
      // tips poke left. Symmetric about cBt. Dark when the radio is on, dimmed when off.
      { float hh = 4.3f, xr = cBt + 2.7f, xl = cBt - 2.7f;
        float y0 = cy - hh, y1 = cy + hh, yq = cy - hh * 0.5f, yl = cy + hh * 0.5f;
        float r = mNdsBtOn ? on_r : of_r, g = mNdsBtOn ? on_g : of_g, b = mNdsBtOn ? on_b : of_b;
        auto seg = [&](float ax, float ay, float bx2, float by2){ ps3ThickLine(X(ax), Y(ay), X(bx2), Y(by2), lw, r, g, b, 1.0f); };
        seg(cBt, y0, cBt, y1);   // staff
        seg(cBt, y0, xr,  yq);   // top -> upper-right knuckle
        seg(xr,  yq, xl,  yl);   // upper-right -> lower-left (crosses staff)
        seg(xl,  yq, xr,  yl);   // upper-left  -> lower-right (crosses staff)
        seg(xr,  yl, cBt, y1);   // lower-right -> bottom
      }
      // --- Music note (eighth note): filled head, vertical stem, flag; symmetric about cNote.
      // Dark when audio is genuinely playing (nano's own players, or a foreground app while
      // nano overlays it); dimmed when silent. Ambiance-only home audio is not counted.
      { bool musicOn = mMusicPlayer.isPlaying() || mVidAudio.isPlaying()
                       || (mOverlayMode && !mOverlayWallpaper && mNdsAudioActive);
        float r = musicOn ? on_r : of_r, g = musicOn ? on_g : of_g, b = musicOn ? on_b : of_b;
        float hx = cNote - 1.8f, hy = cy + 3.4f, sx = cNote + 0.4f;
        ps3ThickLine(X(sx), Y(hy), X(sx), Y(cy - 4.3f), lw, r, g, b, 1.0f);            // stem
        ps3ThickLine(X(sx), Y(cy - 4.3f), X(sx + 2.8f), Y(cy - 1.6f), lw, r, g, b, 1.0f); // flag
        ps3FillCircle(X(hx), Y(hy), S(1.8f), r, g, b, 1.0f);                            // note head
      } }
    }  // end procedural status-glyph fallback (else of mNdsSbIconsLoaded)
    // date/time (topscreen.js): the SMALL font, two right-aligned fields (date, 5px gap, time)
    // ending 6px before the battery. Drawn with FIXED per-glyph advances (digit 7, ':'/space 4,
    // '/' 5 DS px) so the 1 Hz colon blink never shifts the digits (the web reserves the same
    // 4px cell for ':' and ' '). Baseline y15 (cell-top y5 + Fonts.s baseline 10).
    { time_t tt = time(nullptr); struct tm lt; localtime_r(&tt, &lt);
      clockRefreshMaybe();
      bool blinkOff = (lt.tm_sec & 1);
      char ds[16], ts[16];
      // Honor the Date Format setting (mPs3DateFormatIdx 2 = DD/MM, else MM/DD), same as the XMB clock.
      if (mPs3DateFormatIdx == 2) snprintf(ds, sizeof(ds), "%02d/%02d", lt.tm_mday, lt.tm_mon + 1);
      else                        snprintf(ds, sizeof(ds), "%02d/%02d", lt.tm_mon + 1, lt.tm_mday);
      int hr = lt.tm_hour;
      if (mClock12h.load()) { hr %= 12; if (hr == 0) hr = 12; }   // honor the 12/24-hour setting (fixed layout, no AM/PM)
      snprintf(ts, sizeof(ts), "%02d%c%02d", hr, blinkOff ? ' ' : ':', lt.tm_min);
      float fs = S(9.0f) / (float)FONT_CHAR_H;
      // centre the date/time ink on the icon centre (DS y10.5), level with the battery/volume.
      float cy = Y(10.5f - 0.415f * 9.0f);
      auto adv = [](char c){ return (c == ':' || c == ' ') ? 4.0f : (c == '/') ? 5.0f : 7.0f; };
      auto fieldW = [&](const char* s){ float w = 0.0f; for (const char* p = s; *p; ++p) w += adv(*p); return w; };
      auto drawField = [&](const char* s, float rightXpx){
          float x = rightXpx - S(fieldW(s));
          for (const char* p = s; *p; ++p) {
              float cellPx = S(adv(*p));
              if (*p != ' ') { char buf[2] = { *p, 0 }; float gw = measureText(buf, fs);
                  drawText(buf, x + (cellPx - gw) * 0.5f, cy, fs, 0.255f, 0.255f, 0.255f, 1.0f); }   // centre in the fixed cell
              x += cellPx;
          } };
      float clockRight = X(231.0f);                          // battInkX(237) - 6
      drawField(ts, clockRight);
      drawField(ds, clockRight - S(fieldW(ts)) - S(5.0f));
      // battery: a DSi-styled indicator with a PROPORTIONAL fill so it reflects the real
      // charge level, not just full/low/charge states (user request). Dark frame + terminal
      // nub, light empty track, fill width = level%, coloured by state (green while charging,
      // red <=15%, DSi orange otherwise). Real host battery via pollBattery (HAL/sysfs).
      pollBattery();
      { int pct = mBatteryPercent;
        if (pct < 0) {                                   // unknown: static full sprite fallback
            if (mNdsBattTex) drawIconTex(mNdsBattTex, X(235.0f), Y(5.0f), S(17.0f), S(11.0f), 1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            if (pct > 100) pct = 100;
            float bx = X(236.0f), by = Y(6.0f), bw = S(12.5f), bh = S(9.0f);
            drawRoundedRect(bx, by, bw, bh, S(1.5f), 0.255f, 0.255f, 0.255f, 1.0f);               // dark frame
            drawQuad(bx + bw, by + S(2.0f), S(2.0f), bh - S(4.0f), 0.255f, 0.255f, 0.255f, 1.0f); // terminal nub
            float ix = bx + S(1.5f), iy = by + S(1.5f), iw = bw - S(3.0f), ih = bh - S(3.0f);
            drawQuad(ix, iy, iw, ih, 0.902f, 0.902f, 0.902f, 1.0f);                               // empty track
            float fr, fg, fb;
            if (mBatteryCharging)   { fr = 0.30f; fg = 0.78f; fb = 0.36f; }   // charging -> green
            else if (pct <= 15)     { fr = 0.93f; fg = 0.26f; fb = 0.20f; }   // low -> red
            else                    { fr = 1.00f; fg = 0.55f; fb = 0.16f; }   // DSi orange
            float fw = iw * ((float)pct / 100.0f);
            if (fw > 0.5f) drawQuad(ix, iy, fw, ih, fr, fg, fb, 1.0f);                             // proportional fill
        } } }
}


// The DSi top screen drawn into a device-px rect: the light upper-screen background, the
// status bar (username left, date + time and battery right, per topscreen.js) and a content
// panel. The firmware top screen shows a photo/camera widget; the GammaOS adaptation shows
// the current category and selected item in the same DSi style. Contain-fit, edges filled.
void NanoMenu::renderNdsTop(float rx, float ry, float rw, float rh) {
    setUiBlend();
    ensureNdsAssets();
    if (!mPs3MenuBuilt) initPs3Menu();   // ensure the XMB hierarchy exists (top screen reads it)
    // Game-launch whiteout for the TOP screen (web main.js applies launcher.drawLaunchWhite(top,
    // true) right after topscreen.draw). The top ramps from f6 over 41f, a touch behind the bottom
    // carousel (f3/44f), so BOTH screens brighten together on launch (RE'd out/wfa/launch_*). Drawn
    // last, over whatever the top showed (mint panel or the game Information page), exactly as the
    // bottom carousel applies its own launchWhiteAlpha at the end of renderNdsCarousel.
    auto ndsTopLaunchWhite = [&]() {
        if (mOverlayMode || mLaunchFadeStart <= 0) return;
        float lf = (float)((int64_t)uptimeMillis() - mLaunchFadeStart) / (1000.0f / 60.0f);
        float fa = (lf - 6.0f) / 41.0f;
        if (fa < 0.0f) fa = 0.0f; if (fa > 1.0f) fa = 1.0f;
        if (fa > 0.0f) { drawQuad(rx, ry, rw, rh, 1.0f, 1.0f, 1.0f, fa); mDisplayDirty = true; }
    };
    // A game Information page owns the top screen (dual) / this panel (stacked): the cover +
    // metadata summary shows here; the description goes on the bottom screen (renderNdsCarousel).
    if (ndsGameInfoActive()) { renderNdsInfoPage(rx, ry, rw, rh, /*part=*/1); ndsTopLaunchWhite(); return; }
    const bool ndsPrevFont = mNdsFontPref; mNdsFontPref = true;   // DSi text uses the DSVec faces
    const int ndsPrevOutline = mTextOutlineMode; mTextOutlineMode = 2;   // DSi menu text is flat (no drop shadow / outline)
    float scale = rh / 192.0f;
    if (256.0f * scale > rw + 0.5f) scale = rw / 256.0f;
    const float offY = ry + (rh - 192.0f * scale) * 0.5f;
    const float cx = rx + rw * 0.5f;
    auto Y = [&](float dy){ return offY + dy * scale; };
    auto S = [&](float v){ return v * scale; };

    auto X = [&](float dx){ return cx + (dx - 128.0f) * scale; };   // centred DS-x -> px

    // light DSi upper-screen background (#f6f6f6) + #dbdbdb edge columns. Skip the opaque fill in
    // a translucent in-game overlay so the darkened live app shows through (the overlay scrim),
    // like the PS3 XMB overlay (user request); the opaque post-game launcher + home keep it.
    const bool ndsTopScrim = mOverlayMode && !mOverlayWallpaper;
    if (!ndsTopScrim) {
        if (wallpaperActive(mRenderingPanel)) {
            // Custom wallpaper fills the whole DSi top screen behind all the chrome, replacing the flat
            // light field: a looping video on the top panel, else a still. The chrome still draws over it.
            if (!(mRenderingPanel == 0 && drawTopVideoWallpaper()))
                drawWallpaperFill(mRenderingPanel);
        } else {
            drawQuad(rx, ry, rw, rh, 0.965f, 0.965f, 0.965f, 1.0f);
            float ec = fmaxf(1.0f, S(1.0f));
            drawQuad(X(0.0f), ry, ec, rh, 0.859f, 0.859f, 0.859f, 1.0f);
            drawQuad(X(255.0f), ry, ec, rh, 0.859f, 0.859f, 0.859f, 1.0f);
        }
    }

    // ---- photo panel (DSi bg_photo_u, topscreen.js:58): the REAL firmware panel - a
    // beveled grey+white frame around a mint field - blitted from photo_U cropped to its
    // opaque bounds (DS x18..239 y18..187 -> 222x170). This restores the frame border and
    // the correct 4:3 proportions the procedural mint rect was missing. GammaOS overlays
    // the current category + selected item in the same DSi teal. ----
    // Draw the mint canvas PROCEDURALLY (grey bevel -> white inset -> mint field), the same
    // camera-glyph-free frame renderNdsInfoPage uses, instead of blitting photo_U (which has a
    // firmware CAMERA glyph baked into the mint field). The highlighted card's own icon is drawn
    // into this clean field below, so the camera never shows through for non-game items.
    { float px = X(18.0f), pw = X(240.0f) - X(18.0f), py = Y(18.0f), ph = Y(188.0f) - Y(18.0f);
      drawRoundedRect(px - S(2), py - S(2), pw + S(4), ph + S(4), S(4), 0.812f, 0.812f, 0.812f, 1.0f);   // grey bevel
      drawRoundedRect(px, py, pw, ph, S(3), 1.0f, 1.0f, 1.0f, 1.0f);                                     // white inset
      drawRoundedRect(px + S(3), py + S(3), pw - S(6), ph - S(6), S(2), 0.678f, 0.839f, 0.808f, 1.0f); } // mint field
    // panel content: current category (head) + selected item (sub), DSi teal, centred.
    // Top screen shows the current level / parent context (head) + the focused selection (sub).
    // The focused item follows the SAME hard-swap selection as the bottom name box
    // (mNdsDispSel, web _displaySelected) so both screens flip together at 42/58 of a slide.
    std::string head, sub;
    const Ps3Item* selItem = nullptr;
    // Icon of the currently highlighted card, resolved at whatever level we are on, so the mint
    // field shows the SAME glyph as the focused bottom-screen card (root = the category icon,
    // inside = the focused item/app/game icon). Follows the mNdsDispSel hard-swap.
    GLuint hlIconTex = 0, hlNmapTex = 0; float hlIconR = 1.0f, hlIconG = 1.0f, hlIconB = 1.0f;
    if (mNdsAtRoot) {                                     // categories root
        head = "GammaOS";
        int d = mNdsDispSel; if (d < 0) d = 0; if (d >= (int)mPs3Cats.size()) d = (int)mPs3Cats.size() - 1;
        if (d >= 0) { sub = mPs3Cats[d].name; hlIconTex = mPs3Cats[d].iconTex; hlNmapTex = mPs3Cats[d].nmapTex; }
    } else if (!mPs3Stack.empty()) {
        head = mPs3Stack.back().title;
        const auto& its = mPs3Stack.back().items;
        // A LIST level (renderNdsSubmenu) returns before the carousel updates mNdsDispSel, so that
        // index is stale here - use the list's real selection so the top panel tracks the highlighted
        // row exactly (a carousel level keeps the animated hard-swap index).
        int s = ndsCurLevelIsList() ? mPs3Stack.back().sel : mNdsDispSel;
        if (s < 0) s = 0; if (s >= (int)its.size()) s = (int)its.size() - 1;
        if (s >= 0 && s < (int)its.size()) { sub = its[s].label; selItem = &its[s]; }
    } else if (mPs3CatIdx >= 0 && mPs3CatIdx < (int)mPs3Cats.size()) {
        head = mPs3Cats[mPs3CatIdx].name;
        const auto& its = mPs3Cats[mPs3CatIdx].items;
        int s = ndsCurLevelIsList() ? mPs3ItemIdx : mNdsDispSel;
        if (s < 0) s = 0; if (s >= (int)its.size()) s = (int)its.size() - 1;
        if (s >= 0 && s < (int)its.size()) { sub = its[s].label; selItem = &its[s]; }
    }
    // For a focused item/app/game inside a category or submenu, use that item's own icon.
    if (selItem) { hlIconTex = selItem->iconTex; hlNmapTex = selItem->nmapTex;
                   hlIconR = selItem->iconR; hlIconG = selItem->iconG; hlIconB = selItem->iconB; }
    if (head.empty()) head = "GammaOS";

    // ---- game preview (#66) -------------------------------------------------
    // When a scraped ROM (or Recently Played entry) is focused, show its boxart in
    // the photo panel; if the game also has scraped fanart/background art, CROSS-FADE
    // between the two on a slow cycle (user request). The art is resolved + decoded
    // exactly like the XMB column boxart (romBoxartTex / SA_NDS_FAN), sharing the
    // Game-category free lifecycle. Non-game items keep the DSi text preview.
    std::string romPath;
    if (selItem) {
        if (selItem->kind == PS3_ROM && selItem->a >= 0 && selItem->a < (int)mXmbSystems.size()
            && selItem->b >= 0 && selItem->b < (int)mXmbSystems[selItem->a].roms.size())
            romPath = mXmbSystems[selItem->a].roms[selItem->b];
        else if (selItem->kind == PS3_RECENT && selItem->a >= 0 && selItem->a < (int)mXmbRecent.size())
            romPath = mXmbRecent[selItem->a].romPath;
    }
    GLuint boxTex = 0; float boxAR = 1.0f, fanAR = 1.0f; GLuint fanTex = 0;
    if (!romPath.empty() && scraperBoxartEnabled()) {
        boxTex = romBoxartTex(romPath, &boxAR);   // async; 0 until the cover lands
        const ScrapeEntry* se = scrapeEntryFor(romPath);
        std::string fanFile = (se && scraperFanartEnabled()) ? se->fan : std::string();
        if (!fanFile.empty()) {
            if (mNdsFanPath != fanFile) {          // focus changed to a game with fanart: (re)load it
                mNdsFanPath = fanFile;
                if (mNdsFanTex) { glDeleteTextures(1, &mNdsFanTex); mNdsFanTex = 0; mNdsFanW = mNdsFanH = 0; }
                saRequestArt(fanFile, 1024, SA_NDS_FAN, "");
            }
            fanTex = mNdsFanTex; fanAR = (mNdsFanH > 0) ? (float)mNdsFanW / (float)mNdsFanH : 1.0f;
        } else if (!mNdsFanPath.empty()) {          // game without fanart: drop the old preview art
            mNdsFanPath.clear();
            if (mNdsFanTex) { glDeleteTextures(1, &mNdsFanTex); mNdsFanTex = 0; mNdsFanW = mNdsFanH = 0; }
        }
    } else if (!mNdsFanPath.empty()) {              // left games entirely: clear the preview fanart focus
        mNdsFanPath.clear();
    }
    // Re-arm the cross-fade phase when the focused ROM changes; and on a game->game
    // change, hold the outgoing game's cover so the preview DISSOLVES between the two
    // instead of hard-swapping (user: "fade animation between cards").
    if (romPath != mNdsPreviewRom) {
        if (!mNdsPreviewRom.empty() && !romPath.empty()) {   // game -> game
            mNdsPrevPreviewRom = mNdsPreviewRom;
            mNdsGameXfadeStart = mEffectTime;
        } else {                                             // to/from a non-game: no dissolve
            mNdsPrevPreviewRom.clear();
            mNdsGameXfadeStart = -1.0f;
        }
        mNdsPreviewRom = romPath;
        mNdsPreviewT0 = mEffectTime;
    }
    float gt = 1.0f;                                 // game-transition alpha (0 = just changed)
    if (mNdsGameXfadeStart >= 0.0f) {
        float e2 = mEffectTime - mNdsGameXfadeStart; if (e2 < 0.0f) e2 += 500.0f;
        gt = e2 / 0.22f;                             // ~220ms dissolve
        if (gt >= 1.0f) { gt = 1.0f; mNdsGameXfadeStart = -1.0f; mNdsPrevPreviewRom.clear(); }
        else mDisplayDirty = true;
    }
    // Outgoing game's cover (still cached until Game is left), drawn under the new art.
    GLuint oldBoxTex = 0; float oldBoxAR = 1.0f;
    if (gt < 1.0f && !mNdsPrevPreviewRom.empty()) {
        auto itc = mRomBoxartCache.find(mNdsPrevPreviewRom);
        if (itc != mRomBoxartCache.end()) { oldBoxTex = itc->second.tex; oldBoxAR = itc->second.ar; }
    }
    float xf = 0.0f;                                 // 0 = show boxart, 1 = show fanart
    if (boxTex && fanTex) {
        float t = mEffectTime - mNdsPreviewT0; if (t < 0.0f) t += 500.0f;   // mEffectTime wraps at 500s
        t = fmodf(t, 8.0f);                          // 8s cycle: 3s box, 1s fade, 3s fan, 1s fade
        if      (t < 3.0f) xf = 0.0f;
        else if (t < 4.0f) xf = t - 3.0f;
        else if (t < 7.0f) xf = 1.0f;
        else               xf = 8.0f - t;
        mDisplayDirty = true;                        // keep the cross-fade animating
    } else if (fanTex && !boxTex) {
        xf = 1.0f;
    }

    if (boxTex || fanTex || oldBoxTex) {
        // Art sits directly over the panel's existing mint field (no extra backplate -
        // user: do not expand the green canvas). Contain-fit inside this rect, with a bit
        // of top margin so it clears the panel frame. During a game->game switch the
        // outgoing cover fades out (1-gt) while the incoming art fades in (gt).
        float ax = X(24.0f), ay = Y(31.0f), aw = X(234.0f) - X(24.0f), ah = Y(154.0f) - Y(31.0f);
        auto drawContain = [&](GLuint tex, float ar, float alpha) {
            if (!tex || alpha <= 0.001f) return;
            float bw = aw, bh = ah;
            if (ar >= aw / ah) bh = aw / ar; else bw = ah * ar;   // contain-fit
            float bx = ax + (aw - bw) * 0.5f, by = ay + (ah - bh) * 0.5f;
            drawIconTex(tex, bx, by, bw, bh, 1.0f, 1.0f, 1.0f, alpha);
        };
        if (oldBoxTex) drawContain(oldBoxTex, oldBoxAR, 1.0f - gt);
        if (boxTex) drawContain(boxTex, boxAR, (1.0f - xf) * gt);
        if (fanTex) drawContain(fanTex, fanAR, (boxTex ? xf : 1.0f) * gt);
        // game name below the art, cross-faded like the rest of the DSi selection text.
        // Long titles (e.g. "Tobu Tobu Girl Deluxe") are kept inside the panel by shrinking
        // the caption to fit one line, and word-wrapping to two shrink-to-fit lines only once
        // a single line would be too small to read (user request: adaptive scale + wrap).
        const float subMaxW = aw - S(6.0f);
        auto drawSub = [&](const std::string& s, float am){
            if (am <= 0.004f || s.empty()) return;
            float fs = S(13.0f) / (float)FONT_CHAR_H;
            float w = measureText(s.c_str(), fs);
            if (w <= subMaxW) {                                   // fits at full size
                drawText(s.c_str(), cx - w * 0.5f, Y(160.0f), fs, 0.235f, 0.463f, 0.427f, am);
                return;
            }
            float minFs = S(9.0f) / (float)FONT_CHAR_H;           // readability floor
            float scaled = fs * (subMaxW / w);
            if (scaled >= minFs) {                                // one line, shrunk to fit
                float w2 = measureText(s.c_str(), scaled);
                drawText(s.c_str(), cx - w2 * 0.5f, Y(160.0f), scaled, 0.235f, 0.463f, 0.427f, am);
                return;
            }
            // two lines: split at the space nearest the middle (hard split if no space).
            int mid = (int)s.size() / 2, split = -1, best = 1 << 30;
            for (int i = 0; i < (int)s.size(); i++) if (s[i] == ' ') {
                int d = i > mid ? i - mid : mid - i; if (d < best) { best = d; split = i; }
            }
            std::string l1, l2;
            if (split > 0) { l1 = s.substr(0, split); l2 = s.substr(split + 1); }
            else           { l1 = s.substr(0, s.size() / 2); l2 = s.substr(s.size() / 2); }
            auto drawFit = [&](const std::string& tstr, float yc){
                float f = S(11.0f) / (float)FONT_CHAR_H; float lw = measureText(tstr.c_str(), f);
                if (lw > subMaxW) { f *= subMaxW / lw; lw = measureText(tstr.c_str(), f); }
                drawText(tstr.c_str(), cx - lw * 0.5f, yc, f, 0.235f, 0.463f, 0.427f, am);
            };
            drawFit(l1, Y(154.0f));
            drawFit(l2, Y(167.0f));
        };
        drawSub(sub, 1.0f);   // hard-swap (matches the bottom name box)
    } else {
        // context = DSi mint-panel text = Fonts.m (cap-height 10 DS) -> S(13), matching the rest of
        // the DSi theme (the web photo-panel text is all cap-10; S(18) was a cap-14 outlier). Head
        // (category/parent) and item stay distinguished by the two teal shades, not size, and sit
        // as a centred two-line block in the panel. Both cross-fade on a selection change.
        // shrink-to-fit so a long category/item name (e.g. "Settings and Connection
        // Status List") stays inside the panel instead of running past the frame.
        const float ctxMaxW = (X(234.0f) - X(24.0f)) - S(6.0f);
        auto drawCtx = [&](const std::string& s, float yc, float am, float r, float g, float b){
            if (am <= 0.004f || s.empty()) return; float fs = S(13.0f) / (float)FONT_CHAR_H;
            float tw = measureText(s.c_str(), fs);
            if (tw > ctxMaxW) { fs *= ctxMaxW / tw; tw = measureText(s.c_str(), fs); }
            drawText(s.c_str(), cx - tw * 0.5f, yc, fs, r, g, b, am); };
        // The focused settings item's DESCRIPTION (help text), like the PS3 XMB context. The current
        // VALUE is deliberately NOT shown while browsing (user 2026-07-11: "don't show the current
        // value, the user should enter the menu to see it") - so val stays empty and only the name +
        // help + icon appear on the top screen. Games (selItem with art) show art above, not this.
        std::string val;
        std::string dsc = selItem ? selItem->desc : std::string();
        if (!val.empty() && val == sub) val.clear();   // don't echo the label as a value
        if (val.empty() && dsc.empty()) {
            // ---- highlighted card icon in the mint field (replaces the firmware camera glyph) ----
            // Drawn large + centred in the FLAT DSi style the carousel tiles use (drawTile): a dark
            // silhouette for glass/system glyphs, full-colour for game/app art, or the relit glass
            // glyph as a last resort. Only in the plain-label case so settings value/description
            // readouts keep their space; game boxart/fanart is the if-branch above, untouched.
            { const float isz = S(72.0f); const float ixI = cx - isz * 0.5f; const float iyI = Y(46.0f);
              if (hlIconTex) {
                  if (hlNmapTex) drawIconTex(hlIconTex, ixI, iyI, isz, isz, 0.28f, 0.30f, 0.36f, 1.0f);   // dark glyph (system/glass)
                  else           drawIconTex(hlIconTex, ixI, iyI, isz, isz, 1.0f, 1.0f, 1.0f, 1.0f);        // colour art (game/app)
              } else if (hlNmapTex && mIconGlassReady && ps3bg::workTex()) {
                  drawGlassIcon(hlNmapTex, ixI, iyI, isz, isz, hlIconR, hlIconG, hlIconB, 1.0f);            // relit glass fallback
              } }
            drawCtx(head, Y(132.0f), 1.0f, 0.235f, 0.463f, 0.427f);   // #3b766d (hard-swap), below the icon
            drawCtx(sub,  Y(148.0f), 1.0f, 0.349f, 0.635f, 0.604f);   // #59a29a teal
        } else {
            drawCtx(head, Y(64.0f), 1.0f, 0.235f, 0.463f, 0.427f);
            drawCtx(sub,  Y(80.0f), 1.0f, 0.349f, 0.635f, 0.604f);
            if (!val.empty()) drawCtx(val, Y(98.0f), 1.0f, 0.145f, 0.325f, 0.298f);   // current value, darker
            if (!dsc.empty()) {                                                       // wrapped description
                const char* d = trDyn(dsc.c_str());
                float fs = S(9.0f) / (float)FONT_CHAR_H;
                std::vector<std::string> ln; std::string line, word;
                auto commit = [&](){ if (word.empty()) return;
                    std::string tr = line.empty() ? word : line + " " + word;
                    if (!line.empty() && measureText(tr.c_str(), fs) > ctxMaxW) { ln.push_back(line); line = word; }
                    else line = tr; word.clear(); };
                for (const char* q = d; ; ++q) { if (*q==' '||*q=='\n'||*q=='\0'){ commit(); if(*q=='\n') ln.push_back(""); if(*q=='\0') break; } else word.push_back(*q); }
                if (!line.empty()) ln.push_back(line);
                int maxL = 5; if ((int)ln.size() > maxL) ln.resize(maxL);
                float ly = Y(118.0f);
                for (auto& s : ln) { if (!s.empty()) { float tw = measureText(s.c_str(), fs); drawText(s.c_str(), cx - tw*0.5f, ly, fs, 0.30f, 0.42f, 0.40f, 1.0f); } ly += S(11.0f); }
            }
        }
    }

    drawNdsStatusBar(cx, offY, scale);

    // (The DSi camera-scrim L/R shoulder bar is intentionally omitted: the L/R buttons only
    // surface where they DO something - the game Information page's page turn. Showing them on
    // the idle top screen implied an action that does not exist here.)
    // launch white-wash (top screen): ramps over frames 6..47 (launcher.launchWhiteAlpha:
    // the top screen lags the bottom by 3f), holding white until nano exits to the app. The
    // generic render() fade is gated off for the NDS theme so each screen washes at its own rate.
    if (!mOverlayMode && mLaunchFadeStart > 0) {
        float lf = (float)((int64_t)uptimeMillis() - mLaunchFadeStart) / (1000.0f / 60.0f);
        float fa = (lf - 6.0f) / 41.0f;
        if (fa < 0.0f) fa = 0.0f;
        if (fa > 1.0f) fa = 1.0f;
        if (fa > 0.0f) { drawQuad(rx, ry, rw, rh, 1.0f, 1.0f, 1.0f, fa); mDisplayDirty = true; }
    }
    // Boot hand-off: the TOP screen fades in from white in lock-step with the carousel
    // (both panels dissolve in from the boot's enter-white as the cards drop). Keyed on the
    // carousel's intro clock (mNdsIntroStart) so the two screens stay synchronised.
    if (mNdsIntroStart > 0) {
        float introFrame = (float)((int64_t)uptimeMillis() - mNdsIntroStart) / (1000.0f / 60.0f);
        if (introFrame < 21.0f) {
            float wf = 1.0f - introFrame / 21.0f;
            if (wf > 0.0f) { drawQuad(rx, ry, rw, rh, 1.0f, 1.0f, 1.0f, wf); mDisplayDirty = true; }
        }
    }
    ndsTopLaunchWhite();                  // both screens wash to white on a game launch
    mTextOutlineMode = ndsPrevOutline;   // restore the caller's text outline mode
    mNdsFontPref = ndsPrevFont;   // restore the caller's font preference
}

void NanoMenu::triAA(float x0, float y0, float a0,
                     float x1, float y1, float a1,
                     float x2, float y2, float a2,
                     float r, float g, float b) {
    if (mSolidBatchActive) {
        if (sSolidN + 3 > SOLID_BATCH_MAX_VERTS) flushSolidBatch();
        solidPush((x0 / mWidth) * 2.0f - 1.0f, 1.0f - (y0 / mHeight) * 2.0f, r, g, b, a0);
        solidPush((x1 / mWidth) * 2.0f - 1.0f, 1.0f - (y1 / mHeight) * 2.0f, r, g, b, a1);
        solidPush((x2 / mWidth) * 2.0f - 1.0f, 1.0f - (y2 / mHeight) * 2.0f, r, g, b, a2);
        return;
    }
    // No per-vertex-colour batch open (rare: the clock and dialog glyphs always
    // run inside one). Fall back to a flat solid triangle so nothing vanishes.
    drawTriangle(x0, y0, x1, y1, x2, y2, r, g, b, (a0 + a1 + a2) * (1.0f / 3.0f));
}

// Snapshot the WHOLE framebuffer into mGlassTex, then blur it. We capture the
// full viewport (not just the panel rect) so this is rotation/flip agnostic:
// under DRM rotation the panel-rect-to-FB mapping is rotated and a per-region
// copy grabbed the wrong pixels (the old code just bailed via sDrmGlRotation,
// which is why the frosted glass never appeared on rotated panels like the
// 180-degree Brick). drawFrostedGlass maps each panel vertex into FB-NDC via
// sDrmRotMat so it samples the correct screen region behind the panel.
// Free the glass-blur scratch buffers (downsample pyramid + Gaussian ping-pong +
// the full-screen capture snapshot). These are pure scratch: the overlay bakes its
// backdrop once into mOverlayBgTex and samples only that, so while parked behind a
// game none of this is needed. All paths re-allocate lazily (ensureFbo on tex==0,
// captureGlass gen-on-zero), so this is safe to call on the park transition and the
// next blur/capture rebuilds transparently.
void NanoMenu::freeGlassScratch() {
    for (int i = 0; i < 4; i++) {
        if (mGlassDownTex[i]) { glDeleteTextures(1, &mGlassDownTex[i]); mGlassDownTex[i] = 0; }
        if (mGlassFbo[i])     { glDeleteFramebuffers(1, &mGlassFbo[i]); mGlassFbo[i] = 0; }
        mGlassDownW[i] = mGlassDownH[i] = 0;
    }
    for (int i = 0; i < 2; i++) {
        if (mGlassGaussTex[i]) { glDeleteTextures(1, &mGlassGaussTex[i]); mGlassGaussTex[i] = 0; }
        if (mGlassGaussFbo[i]) { glDeleteFramebuffers(1, &mGlassGaussFbo[i]); mGlassGaussFbo[i] = 0; }
        mGlassGaussW[i] = mGlassGaussH[i] = 0;
    }
    mGlassBlurTex = 0;
    if (mGlassTex) { glDeleteTextures(1, &mGlassTex); mGlassTex = 0; mGlassTexW = mGlassTexH = 0; }
}

bool NanoMenu::captureGlass(float /*x*/, float /*y*/, float /*w*/, float /*h*/) {
    GLint vp[4] = {0, 0, 0, 0};
    glGetIntegerv(GL_VIEWPORT, vp);
    int fbX = vp[0], fbY = vp[1], fbW = vp[2], fbH = vp[3];
    if (fbW <= 0 || fbH <= 0) return false;
    // Self-heal if the snapshot texture was freed while parked (freeGlassScratch).
    if (mGlassTex == 0) { glGenTextures(1, &mGlassTex); mGlassTexW = mGlassTexH = 0; }
    glBindTexture(GL_TEXTURE_2D, mGlassTex);
    if (mGlassTexW != fbW || mGlassTexH != fbH) {
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, fbW, fbH, 0,
                     GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        mGlassTexW = fbW; mGlassTexH = fbH;
    }
    glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, fbX, fbY, fbW, fbH);
    // Downsample + separable-Gaussian blur the captured screen.
    blurGlassChain(mGlassTex, mGlassTexW, mGlassTexH);
    return true;
}

// Blur the PS3 XMB live wave/gradient scene (ps3bg::workTex) WITHOUT a full
// framebuffer capture. workTex is already rendered into its own FBO each frame
// (the gradient + additive wave), so reading it costs no mid-frame tile flush -
// unlike captureGlass's glCopyTexSubImage2D, which forces a resolve on this
// tiler GPU (~20ms) and dropped submenus to ~40fps. The blur result lands in
// mGlassBlurTex in LOGICAL (un-rotated) orientation spanning the frame, so the
// frosted panel must sample it with drawFrostedGlass(..., waveSpace=true).
// Returns false if the wave is not ready yet (caller keeps the prior blur).
bool NanoMenu::captureGlassFromWave() {
    GLuint wt = ps3bg::workTex();
    if (wt == 0) return false;
    int fw = (int)(ps3::gFrameW + 0.5f);
    int fh = (int)(ps3::gFrameH + 0.5f);
    if (fw < 8 || fh < 8) return false;
    // 2 downsample levels (~1/4 res) box pyramid + NO Gaussian. A bit more frost
    // than 1/2-res but a SMALLER blur source, so the per-frame tent upsample is
    // cheaper (fewer texture-cache misses) and there is no Gaussian pass: more
    // blur AND faster, which is the perf-positive direction.
    blurGlassChain(wt, fw, fh, 2, 0);
    return true;
}

// Render the full-screen capture (mGlassTex) down through THREE box-filter
// passes (1/2 -> 1/4 -> 1/8) into mGlassDownTex[0..2], then run a TRUE separable
// Gaussian (horizontal then vertical) twice on the 1/8 texture using the two
// scratch buffers mGlassGaussTex[0]/[1]. Leaves the final smooth result in
// mGlassBlurTex / mGlassBlurW/H for drawFrostedGlass to tent-upsample. The 1/8
// downsample plus the side-lobe-free Gaussian collapse the background into
// smooth blurred color with no readable text.
// On any failure (no program, FBO
// incomplete) it falls back to the smallest level it reached (or the full-res
// capture) so the panel still draws. Saves/restores FBO binding, viewport, and
// blend; no pass ever samples the texture it renders to; guards tiny captures.
void NanoMenu::blurGlassChain(GLuint srcTexIn, int srcWIn, int srcHIn,
                              int downLevels, int gaussIters) {
    mGlassBlurTex = srcTexIn;
    mGlassBlurW = srcWIn;
    mGlassBlurH = srcHIn;
    if (mGlassDownProgram == 0 || srcWIn < 8 || srcHIn < 8) return;
    if (downLevels < 1) downLevels = 1;
    if (gaussIters < 0) gaussIters = 0;

    GLint prevFbo = 0;
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
    GLint vp[4];
    glGetIntegerv(GL_VIEWPORT, vp);
    GLboolean wasBlend = glIsEnabled(GL_BLEND);
    glDisable(GL_BLEND);

    static const GLfloat quad[]  = { -1,-1, 1,-1, 1,1, 1,1, -1,1, -1,-1 };
    static const GLfloat quadT[] = {  0, 0, 1, 0, 1,1, 1,1,  0,1,  0, 0 };

    // (Re)allocate an RGBA FBO texture of size (dw,dh). NPOT + GL_LINEAR +
    // GL_CLAMP_TO_EDGE + no mipmaps (valid in ES2). Returns false if the FBO is
    // incomplete after attaching.
    auto ensureFbo = [&](GLuint* tex, GLuint* fbo, int* tw, int* th,
                         int dw, int dh) -> bool {
        if (*tex == 0 || *tw != dw || *th != dh) {
            if (*tex == 0) glGenTextures(1, tex);
            glBindTexture(GL_TEXTURE_2D, *tex);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, dw, dh, 0,
                         GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            *tw = dw; *th = dh;
        }
        if (*fbo == 0) glGenFramebuffers(1, fbo);
        glBindFramebuffer(GL_FRAMEBUFFER, *fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                               GL_TEXTURE_2D, *tex, 0);
        return glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
    };

    // ---- Stage 1: 3-level box downsample pyramid (1/2, 1/4, 1/8) ----
    glUseProgram(mGlassDownProgram);
    glActiveTexture(GL_TEXTURE0);
    glUniform1i(mGlassDownLocTexture, 0);
    glUniform1f(mGlassDownLocOffset, 1.0f);
    glBindBuffer(GL_ARRAY_BUFFER, 0);

    const int DOWN_LEVELS = downLevels;    // 3 -> ~1/8 res (strong); 2 -> ~1/4 (half)
    int   srcW = srcWIn, srcH = srcHIn;
    GLuint srcTex = srcTexIn;
    bool   pyramidOk = true;
    for (int i = 0; i < DOWN_LEVELS; i++) {
        int dw = srcW / 2; if (dw < 1) dw = 1;
        int dh = srcH / 2; if (dh < 1) dh = 1;
        if (!ensureFbo(&mGlassDownTex[i], &mGlassFbo[i],
                       &mGlassDownW[i], &mGlassDownH[i], dw, dh)) {
            pyramidOk = false;
            break;                         // keep whatever level we reached
        }
        glViewport(0, 0, dw, dh);
        glBindTexture(GL_TEXTURE_2D, srcTex);
        glUniform2f(mGlassDownLocHalfpixel, 1.0f / (float)srcW, 1.0f / (float)srcH);
        glVertexAttribPointer(mGlassDownLocPosition, 2, GL_FLOAT, GL_FALSE, 0, quad);
        glEnableVertexAttribArray(mGlassDownLocPosition);
        glVertexAttribPointer(mGlassDownLocTexCoord, 2, GL_FLOAT, GL_FALSE, 0, quadT);
        glEnableVertexAttribArray(mGlassDownLocTexCoord);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glDisableVertexAttribArray(mGlassDownLocPosition);
        glDisableVertexAttribArray(mGlassDownLocTexCoord);
        srcTex = mGlassDownTex[i]; srcW = dw; srcH = dh;
        mGlassBlurTex = srcTex; mGlassBlurW = srcW; mGlassBlurH = srcH;
    }

    // ---- Stage 2: separable Gaussian (H then V), run GAUSS_ITERS times ----
    // Each H+V iteration multiplies the effective sigma by ~sqrt(2); two passes
    // give a wide, smooth frost that erases even bright large title text. The
    // >=2 size check prevents 1/srcH div issues / degenerate 1px gauss textures
    // on odd captures (falls back cleanly to the box-pyramid result). Both
    // ping-pong buffers are allocated up front so the loop just swaps targets.
    // gaussIters == 0 skips the Gaussian entirely: mGlassBlurTex keeps the box
    // downsample-pyramid result (cheaper, lighter frost). The downsample loop
    // already left mGlassBlurTex pointing at the last (smallest) level.
    bool gaussOk = (gaussIters > 0 && mGlassGaussProgram != 0 && pyramidOk && srcW >= 2 && srcH >= 2);
    if (gaussOk)
        gaussOk = ensureFbo(&mGlassGaussTex[0], &mGlassGaussFbo[0],
                            &mGlassGaussW[0], &mGlassGaussH[0], srcW, srcH);
    if (gaussOk)
        gaussOk = ensureFbo(&mGlassGaussTex[1], &mGlassGaussFbo[1],
                            &mGlassGaussW[1], &mGlassGaussH[1], srcW, srcH);
    if (gaussOk) {
        glUseProgram(mGlassGaussProgram);
        glActiveTexture(GL_TEXTURE0);
        glUniform1i(mGlassGaussLocTexture, 0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glVertexAttribPointer(mGlassGaussLocPosition, 2, GL_FLOAT, GL_FALSE, 0, quad);
        glEnableVertexAttribArray(mGlassGaussLocPosition);
        glVertexAttribPointer(mGlassGaussLocTexCoord, 2, GL_FLOAT, GL_FALSE, 0, quadT);
        glEnableVertexAttribArray(mGlassGaussLocTexCoord);

        const int GAUSS_ITERS = gaussIters;
        GLuint gsrc = srcTex;                 // first read = pyramid result
        for (int it = 0; it < GAUSS_ITERS; it++) {
            // Horizontal: gsrc -> gauss[0].
            glBindFramebuffer(GL_FRAMEBUFFER, mGlassGaussFbo[0]);
            glViewport(0, 0, srcW, srcH);
            glBindTexture(GL_TEXTURE_2D, gsrc);
            glUniform2f(mGlassGaussLocDir, 1.0f / (float)srcW, 0.0f);
            glDrawArrays(GL_TRIANGLES, 0, 6);
            // Vertical: gauss[0] -> gauss[1] (ping-pong, never read==write).
            glBindFramebuffer(GL_FRAMEBUFFER, mGlassGaussFbo[1]);
            glViewport(0, 0, srcW, srcH);
            glBindTexture(GL_TEXTURE_2D, mGlassGaussTex[0]);
            glUniform2f(mGlassGaussLocDir, 0.0f, 1.0f / (float)srcH);
            glDrawArrays(GL_TRIANGLES, 0, 6);
            gsrc = mGlassGaussTex[1];         // next iteration blurs the result
        }
        glDisableVertexAttribArray(mGlassGaussLocPosition);
        glDisableVertexAttribArray(mGlassGaussLocTexCoord);
        mGlassBlurTex = mGlassGaussTex[1];
        mGlassBlurW = srcW; mGlassBlurH = srcH;
    }

    glBindFramebuffer(GL_FRAMEBUFFER, prevFbo);
    glViewport(vp[0], vp[1], vp[2], vp[3]);
    if (wasBlend) glEnable(GL_BLEND);
}

// Enable a scissor covering the LOGICAL rect (x,y,w,h) in menu coordinates,
// valid for any panel rotation/flip. The call sites this replaces hand-rolled
// a switch on sDrmRotationDeg alone, which ignored the flips composed into
// sDrmRotMat (the persist.gammaos.nano.drm_flip_h/v device props, and the
// DRM PRIME scanout Y-flip on non-rotated installs): on a rotated+flipped
// panel (RG Vita Pro: 270 install + drm_flip_v=1, matrix [0,-1,-1,0]) every
// band landed MIRRORED, clipping content that should be visible --
// highlighted labels truncated mid-string, the boot-logo wipe scissoring
// away the entire logo. Transform the rect's two opposite corners
// through the SAME composed matrix the vertex shaders apply (uRotation =
// sDrmRotMat); it is always a signed permutation of the axes, so min/max of
// two opposite corners is the exact window-space rect. NDC maps to pixels via
// the CURRENT viewport, which is authoritative: under rotation the main pass
// renders into the panel-native AHB FBO while mWidth/mHeight stay logical.
// Only valid during the main full-target pass (true for every caller); do not
// call inside offscreen sub-viewport passes (blur/capture) without rework.
void NanoMenu::scissorLogicalRect(float x, float y, float w, float h) {
    float lx0 = (x / mWidth) * 2.0f - 1.0f;
    float lx1 = ((x + w) / mWidth) * 2.0f - 1.0f;
    float ly0 = 1.0f - ((y + h) / mHeight) * 2.0f;
    float ly1 = 1.0f - (y / mHeight) * 2.0f;
    float ax = lx0, ay = ly0, bx = lx1, by = ly1;
    if (sDrmGlRotation) {
        ax = sDrmRotMat[0] * lx0 + sDrmRotMat[2] * ly0;
        ay = sDrmRotMat[1] * lx0 + sDrmRotMat[3] * ly0;
        bx = sDrmRotMat[0] * lx1 + sDrmRotMat[2] * ly1;
        by = sDrmRotMat[1] * lx1 + sDrmRotMat[3] * ly1;
    }
    float loX = fminf(ax, bx), hiX = fmaxf(ax, bx);
    float loY = fminf(ay, by), hiY = fmaxf(ay, by);
    GLint vp[4];
    glGetIntegerv(GL_VIEWPORT, vp);
    int sx = vp[0] + (int)floorf((loX + 1.0f) * 0.5f * (float)vp[2]);
    int sy = vp[1] + (int)floorf((loY + 1.0f) * 0.5f * (float)vp[3]);
    int sw = (int)ceilf((hiX - loX) * 0.5f * (float)vp[2]);
    int sh = (int)ceilf((hiY - loY) * 0.5f * (float)vp[3]);
    if (sw < 0) sw = 0;
    if (sh < 0) sh = 0;
    glEnable(GL_SCISSOR_TEST);
    glScissor(sx, sy, sw, sh);
}

// Draw the frosted panel sampling mGlassTex (captured by captureGlass with the
// same rect). texcoords are v-flipped because the FB snapshot is y-up.
void NanoMenu::drawFrostedGlass(float x, float y, float w, float h, float radius,
                                float tr, float tg, float tb, float tintA, float fade,
                                bool waveSpace, float tonemapOverride,
                                GLuint srcTexOverride, int srcOverrideW, int srcOverrideH) {
    float x0 = (x / mWidth) * 2.0f - 1.0f;
    float y0 = 1.0f - ((y + h) / mHeight) * 2.0f;
    float x1 = ((x + w) / mWidth) * 2.0f - 1.0f;
    float y1 = 1.0f - (y / mHeight) * 2.0f;
    GLfloat verts[] = { x0,y0, x1,y0, x1,y1, x1,y1, x0,y1, x0,y0 };
    float hw = w * 0.5f, hh = h * 0.5f;
    GLfloat local[] = { -hw,hh, hw,hh, hw,-hh, hw,-hh, -hw,-hh, -hw,hh };
    // Texcoords: map each panel vertex through the SAME transform the vertex
    // shader applies (uRotation = sDrmRotMat), into FB-NDC, then to [0,1]. This
    // samples the full-screen blur texture at exactly the screen pixels behind
    // the panel for any rotation/flip (identity matrix => the usual 0..1 map).
    // sDrmRotMat is column-major: (rx,ry) = (m0*lx+m2*ly, m1*lx+m3*ly).
    //
    // waveSpace: the blur source is ps3bg::workTex (captureGlassFromWave), which
    // is already in LOGICAL (un-rotated) orientation, GL y-up, spanning the frame.
    // So map logical NDC straight to [0,1] with NO rotation - the vertex position
    // still goes through uRotation for display, but the texcoord stays logical.
    auto fbTex = [waveSpace](float lx, float ly, float& tu, float& tv) {
        if (waveSpace) { tu = lx * 0.5f + 0.5f; tv = ly * 0.5f + 0.5f; return; }
        float rx = sDrmRotMat[0] * lx + sDrmRotMat[2] * ly;
        float ry = sDrmRotMat[1] * lx + sDrmRotMat[3] * ly;
        tu = rx * 0.5f + 0.5f;
        tv = ry * 0.5f + 0.5f;
    };
    GLfloat tex[12];
    fbTex(x0, y0, tex[0],  tex[1]);
    fbTex(x1, y0, tex[2],  tex[3]);
    fbTex(x1, y1, tex[4],  tex[5]);
    fbTex(x1, y1, tex[6],  tex[7]);
    fbTex(x0, y1, tex[8],  tex[9]);
    fbTex(x0, y0, tex[10], tex[11]);
    float mh = (hw < hh ? hw : hh);
    if (radius > mh) radius = mh;
    if (radius < 0.0f) radius = 0.0f;
    // Tent-upsample spread, in texels of the 1/8-res Gaussian-blurred texture.
    // The heavy frost already comes from the 1/8 downsample + separable Gaussian;
    // this is just a ~1-texel tent for smooth bilinear magnification without
    // over-spreading past the rounded panel edge.
    // srcTexOverride lets a caller sample its OWN persistent blur texture instead of the
    // shared mGlassBlurTex (used by the clock backdrop, whose 30Hz-throttled blur must not
    // be clobbered by the later chrome-glow blurGlassChain). 0 = the usual shared source.
    int   bw = srcTexOverride ? srcOverrideW : ((mGlassBlurW > 0) ? mGlassBlurW : mGlassTexW);
    int   bh = srcTexOverride ? srcOverrideH : ((mGlassBlurH > 0) ? mGlassBlurH : mGlassTexH);
    float texelX = (bw > 0) ? 1.0f / (float)bw : 0.02f;
    float texelY = (bh > 0) ? 1.0f / (float)bh : 0.02f;
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glUseProgram(mGlassProgram);
    glUniformMatrix2fv(mGlassLocRotation, 1, GL_FALSE, sDrmRotMat);
    glUniform2f(mGlassLocHalf, hw, hh);
    glUniform1f(mGlassLocRadius, radius);
    glUniform2f(mGlassLocTexel, texelX, texelY);
    glUniform4f(mGlassLocTint, tr, tg, tb, tintA);
    glUniform1f(mGlassLocAlpha, fade);
    // waveSpace blur samples the LINEAR scene (ps3bg::workTex); tonemap it to
    // display space so the frosted backdrop matches the on-screen background.
    // 1.6846 = uExposure(1.05)/uWhiteLevel(0.899181) * LOG2E(1.442695).
    // tonemapOverride >= 0 forces the exp2 tonemap amount (the live-app backdrop passes 0:
    // the captured app is already display sRGB, so it must NOT be tonemapped like the LINEAR wave).
    float glassTm = (tonemapOverride >= 0.0f) ? tonemapOverride : (waveSpace ? 1.6846f : 0.0f);
    if (mGlassLocTonemap >= 0) glUniform1f(mGlassLocTonemap, glassTm);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, srcTexOverride ? srcTexOverride
                                                : ((mGlassBlurTex != 0) ? mGlassBlurTex : mGlassTex));
    glUniform1i(mGlassLocTexture, 0);
    glVertexAttribPointer(mGlassLocPosition, 2, GL_FLOAT, GL_FALSE, 0, verts);
    glEnableVertexAttribArray(mGlassLocPosition);
    glVertexAttribPointer(mGlassLocLocal, 2, GL_FLOAT, GL_FALSE, 0, local);
    glEnableVertexAttribArray(mGlassLocLocal);
    glVertexAttribPointer(mGlassLocTexCoord, 2, GL_FLOAT, GL_FALSE, 0, tex);
    glEnableVertexAttribArray(mGlassLocTexCoord);
    glDrawArrays(GL_TRIANGLES, 0, 6);
    glDisableVertexAttribArray(mGlassLocPosition);
    glDisableVertexAttribArray(mGlassLocLocal);
    glDisableVertexAttribArray(mGlassLocTexCoord);
}

// ---------------------------------------------------------------------------
// FreeType font initialization
// ---------------------------------------------------------------------------

void NanoMenu::initFonts() {
    if (FT_Init_FreeType(&mFtLib) != 0) {
        ALOGE("NanoMenu: FreeType init failed");
        return;
    }
    mFtNumFaces = 0;
    // Theme override: a theme can replace the primary (Latin/UI) typeface by
    // pointing persist.gammaos.nano.font at any .ttf/.otf (absolute path). This is
    // the font-themeability hook; it loads as the first face so it wins for Latin,
    // while CJK / Arabic / Thai / Hebrew / emoji still fall through to the Noto
    // faces below. Empty / unloadable -> the bundled Rodin path runs as normal.
    char fontProp[PROPERTY_VALUE_MAX] = {0};
    property_get("persist.gammaos.nano.font", fontProp, "");
    if (fontProp[0] && FT_New_Face(mFtLib, fontProp, 0, &mFtFaces[mFtNumFaces]) == 0) {
        ALOGD("NanoMenu: loaded theme font: %s", fontProp);
        mFtNumFaces++;
    }
    // PS3 Rodin so Latin text uses the authentic XMB font when no theme font is
    // set. Prefer the dev push dir, then the shipped asset.
    const char* rodinPaths[] = {
        "/data/system/nano_xmb/fonts/ps3-rodin-regular.ttf",
        "/system/etc/nano_xmb/fonts/ps3-rodin-regular.ttf",
    };
    if (mFtNumFaces == 0) {
        for (const char* rp : rodinPaths) {
            if (FT_New_Face(mFtLib, rp, 0, &mFtFaces[mFtNumFaces]) == 0) {
                ALOGD("NanoMenu: loaded PS3 Rodin: %s", rp);
                mFtNumFaces++;
                break;
            }
        }
    }
    const char* fontPaths[] = {
        "/system/fonts/Roboto-Regular.ttf",
        "/system/fonts/DroidSans.ttf",
        "/system/fonts/NotoSansCJK-Regular.ttc",
        "/system/fonts/NotoNaskhArabic-Regular.ttf",
        "/system/fonts/NotoSansThai-Regular.ttf",
        "/system/fonts/NotoSansHebrew-Regular.ttf",
        "/system/fonts/NotoColorEmoji.ttf",
    };
    for (const char* path : fontPaths) {
        if (mFtNumFaces >= MAX_FT_FACES) break;
        if (FT_New_Face(mFtLib, path, 0, &mFtFaces[mFtNumFaces]) == 0) {
            ALOGD("NanoMenu: loaded font: %s", path);
            mFtNumFaces++;
        } else {
            ALOGW("NanoMenu: failed to load font: %s", path);
        }
    }
    // DSi System Menu theme fonts: the redrawn resolution-independent "4x" faces the web
    // app uses at scale>1 (DSVec = letters/Mirsany, DSVecNum = digits/M PLUS Rounded 1c).
    // Loaded as extra faces; ensureGlyph prefers them only while mNdsFontPref is set, so
    // normal menu text is untouched. Dev-override under /data first, then the bundled path.
    { struct { const char* dev; const char* sys; int* idx; } kNdsFonts[] = {
        { "/data/system/nano_xmb/nds/dsvec.ttf",    "/system/etc/nano_xmb/nds/dsvec.ttf",    &mNdsFontIdx },
        { "/data/system/nano_xmb/nds/dsvecnum.ttf", "/system/etc/nano_xmb/nds/dsvecnum.ttf", &mNdsNumIdx  },
      };
      for (auto& f : kNdsFonts) {
          if (mFtNumFaces >= MAX_FT_FACES) break;
          const char* p = (access(f.dev, R_OK) == 0) ? f.dev : f.sys;
          if (FT_New_Face(mFtLib, p, 0, &mFtFaces[mFtNumFaces]) == 0) {
              *f.idx = mFtNumFaces; mFtNumFaces++;
              ALOGD("NanoMenu: loaded NDS font: %s (face %d)", p, *f.idx);
          }
      }
    }
    // Base atlas render size. Higher = sharper LARGE text (the setup-wizard
    // welcome greeting is drawn at ~57-85px and was upscaling/blurring from a
    // 48px atlas). Displayed text size is FONT_CHAR_H*scale (independent of
    // mFontSize), so this only raises the source resolution: crisper big text
    // and crisper minified text, with no layout change and no outline change.
    mFontSize = 64;
    for (int i = 0; i < mFtNumFaces; i++) {
        if (!FT_HAS_COLOR(mFtFaces[i])) {
            FT_Set_Pixel_Sizes(mFtFaces[i], 0, mFontSize);
        }
    }
    // Create RGBA glyph atlas. 1024x2048 (8 MB) rather than 2048x2048 (16 MB):
    // the live render working set is only a few hundred glyphs (item labels +
    // descriptions visible at once), packed into the top-left, so half the area
    // is ample. If a single frame ever exceeds it the existing resetGlyphAtlas()
    // recycle path re-rasterizes lazily (a one-off startup-prewarm hitch hidden
    // behind the boot intro, never a steady-state cost). A further ~4 MB is
    // available by moving mono glyphs to LUMINANCE_ALPHA with a separate small
    // RGBA color-emoji atlas, which needs the text batch split per atlas.
    mAtlasW = 1024;
    mAtlasH = 2048;
    mAtlasCurX = 1; // start at 1 to avoid bleeding from edge
    mAtlasCurY = 1;
    mAtlasRowH = 0;
    glGenTextures(1, &mGlyphAtlasTex);
    glBindTexture(GL_TEXTURE_2D, mGlyphAtlasTex);
    // Plain GL_LINEAR everywhere. The old mipmap-minification AA path
    // (setGlyphAtlasAA) is a permanent no-op now that glyphs rasterize at their
    // exact display pixel size, so the atlas needs no mip chain: keeping only
    // level 0 saves ~5 MB of GPU memory per process (it is mlocked-resident on
    // this RAM-tight panel) and avoids a full-atlas glGenerateMipmap on recycle.
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    // White-transparent (not black-transparent) gutters: mono glyphs store
    // RGB=255/A=coverage, so when a mip level averages a glyph edge against the
    // gutter the RGB stays 255 and only the coverage falls off -> no dark fringe
    // on minified text (the text shader multiplies texel.rgb * colour).
    // Allocate the atlas, then clear it to white-transparent (RGB=255, A=0) in
    // horizontal strips. Uploading strips from a small reusable buffer caps the
    // transient CPU allocation at ~1 MB instead of the full mAtlasW*mAtlasH*4
    // (8 MB) one-shot vector, which avoids an 8 MB heap high-water spike at init.
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, mAtlasW, mAtlasH, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    {
        const int stripRows = 256;
        std::vector<uint8_t> strip((size_t)mAtlasW * stripRows * 4, 255);
        for (size_t i = 3; i < strip.size(); i += 4) strip[i] = 0;
        for (int y = 0; y < mAtlasH; y += stripRows) {
            int rows = (y + stripRows <= mAtlasH) ? stripRows : (mAtlasH - y);
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, y, mAtlasW, rows,
                            GL_RGBA, GL_UNSIGNED_BYTE, strip.data());
        }
    }
    // No mip chain: MIN_FILTER is GL_LINEAR (level 0 only), so the texture is
    // complete without one.
    ALOGD("NanoMenu: font atlas %dx%d, %d faces loaded", mAtlasW, mAtlasH, mFtNumFaces);
}

// Toggle crisp anti-aliased text on the glyph atlas. On: trilinear minification
// (GL_LINEAR_MIPMAP_LINEAR) gives a clean, shimmer-free coverage sample, and the
// text shader's uSharp pass re-sharpens the glyph edge to ~1px so the result is
// crisp, not the soft trilinear blur. Off: plain GL_LINEAR + uSharp 0 (byte
// identical to the original text path). Scoped per region: the home-XMB menu
// content, the dialogs and the setup wizard (after the Hello screen) turn it on;
// the OSK, the welcome greeting and legacy menus stay off. MIN_FILTER is
// texture-object state (one call covers the region); mTextSharp is uploaded by
// drawText. mGlyphAtlasMinFilter skips redundant GL calls.
void NanoMenu::setGlyphAtlasAA(bool /*on*/) {
    // Superseded by per-size glyph rasterization: glyphs are now rendered at their
    // exact display pixel size and blitted 1:1, which is inherently crisp, so the
    // mipmap-minification + shader edge-sharpen workarounds are disabled (they
    // would re-threshold already-crisp native glyphs). Kept as a no-op so the
    // per-region call sites need no churn. Plain GL_LINEAR + uSharp 0 everywhere.
    mTextSharp = 0.0f;
    if (mGlyphAtlasMinFilter == GL_LINEAR) return;
    glBindTexture(GL_TEXTURE_2D, mGlyphAtlasTex);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    mGlyphAtlasMinFilter = GL_LINEAR;
}

// ---------------------------------------------------------------------------
// Glyph caching
// ---------------------------------------------------------------------------

// Recycle the glyph atlas: drop every cached glyph and rewind the packing
// cursor so the next rasterizations refill from the top. Called when the atlas
// fills mid-frame. The visible working set (the menu plus an open keyboard) is a
// few hundred glyphs and packs into a small corner of the 2048x2048 atlas, so a
// recycle reclaims all the space taken by glyphs from screens no longer shown.
// The texture itself is not cleared: stale pixels are simply never referenced
// again once their cache entries are gone, and new packs overwrite them.
void NanoMenu::resetGlyphAtlas() {
    mGlyphCache.clear();
    mTextWidthCache.clear();   // widths reference glyph advances we just dropped
    mAtlasCurX = 1;
    mAtlasCurY = 1;
    mAtlasRowH = 0;
}

const GlyphInfo* NanoMenu::ensureGlyph(uint32_t cp, int rasterPx) {
    if (rasterPx < 6) rasterPx = 6;
    if (rasterPx > mFontSize) rasterPx = mFontSize;   // upscale beyond the master in drawText
    // Fast path: already cached at this raster size (mono), or as a color strike
    // (cached once under mFontSize regardless of the requested size).
    // When rendering DSi-theme text, the DSVec faces cache in a separate slot (a spare
    // high bit) so they never collide with the primary font's glyph at the same size.
    const uint64_t ndsBit  = mNdsFontPref ? ((uint64_t)1 << 40) : 0;
    const uint64_t monoKey = (((uint64_t)(uint32_t)rasterPx << 32) | cp) | ndsBit;
    {
        auto it = mGlyphCache.find(monoKey);
        if (it != mGlyphCache.end()) return &it->second;
    }
    const uint64_t colorKey = ((uint64_t)(uint32_t)mFontSize << 32) | cp;
    if (rasterPx != mFontSize) {
        auto it = mGlyphCache.find(colorKey);
        if (it != mGlyphCache.end() && it->second.color) return &it->second;
    }
    if (mFtNumFaces == 0) return nullptr;

    FT_Face face = nullptr;
    FT_UInt gi = 0;
    bool isColorFace = false;
    // DSi theme: try the DSVec faces first (digits -> DSVecNum, else DSVec letters).
    if (mNdsFontPref) {
        int pref = (cp >= '0' && cp <= '9' && mNdsNumIdx >= 0) ? mNdsNumIdx : mNdsFontIdx;
        if (pref >= 0) { gi = FT_Get_Char_Index(mFtFaces[pref], cp); if (gi != 0) face = mFtFaces[pref]; }
    }
    for (int i = 0; !face && i < mFtNumFaces; i++) {
        gi = FT_Get_Char_Index(mFtFaces[i], cp);
        if (gi != 0) {
            face = mFtFaces[i];
            isColorFace = FT_HAS_COLOR(face);
            break;
        }
    }
    if (!face) {
        // Fallback to '?' in primary font
        face = mFtFaces[0];
        gi = FT_Get_Char_Index(face, '?');
        isColorFace = false;
    }
    if (!face || gi == 0) {
        // No glyph anywhere (not even '?'): cache a blank so we do not re-probe
        // every face for this codepoint every frame.
        GlyphInfo blank = {};
        blank.scaleW = 1.0f;
        blank.scaleH = 1.0f;
        auto r = mGlyphCache.emplace(monoKey, blank);
        return &r.first->second;
    }

    // Mono glyphs rasterize at the exact display size (crisp); color emoji use a
    // fixed strike normalized to mFontSize. The cache key picks the matching slot.
    const int px = isColorFace ? mFontSize : rasterPx;
    const uint64_t key = isColorFace ? colorKey : monoKey;

    if (isColorFace && FT_HAS_FIXED_SIZES(face)) {
        int bestIdx = 0;
        int bestDiff = 99999;
        for (int i = 0; i < face->num_fixed_sizes; i++) {
            int diff = abs(face->available_sizes[i].height - mFontSize);
            if (diff < bestDiff) { bestDiff = diff; bestIdx = i; }
        }
        FT_Select_Size(face, bestIdx);
    } else if (!isColorFace) {
        FT_Set_Pixel_Sizes(face, 0, px);
    }

    FT_Int32 loadFlags = FT_LOAD_RENDER;
    if (isColorFace) loadFlags |= FT_LOAD_COLOR;
    if (FT_Load_Glyph(face, gi, loadFlags) != 0) {
        // Cache a blank so an unloadable glyph is not re-attempted (and re-read
        // off EROFS) every frame.
        GlyphInfo blank = {};
        blank.scaleW = 1.0f;
        blank.scaleH = 1.0f;
        auto r = mGlyphCache.emplace(key, blank);
        return &r.first->second;
    }

    FT_Bitmap* bmp = &face->glyph->bitmap;
    int bw = (int)bmp->width;
    int bh = (int)bmp->rows;
    bool isColor = (bmp->pixel_mode == FT_PIXEL_MODE_BGRA);

    // Pack into atlas (row-based, simple packer)
    if (bw > 0 && bh > 0) {
        // 2px gutters (was 1) so the first couple of mip levels do not bleed
        // neighbouring glyphs into the home-XMB anti-aliased minification path.
        if (mAtlasCurX + bw + 2 > mAtlasW) {
            mAtlasCurX = 1;
            mAtlasCurY += mAtlasRowH + 2;
            mAtlasRowH = 0;
        }
        if (mAtlasCurY + bh + 2 > mAtlasH) {
            // Atlas full. Returning nullptr without caching would make the next
            // frame re-run FT_Load_Glyph for this glyph again (an on-demand read
            // of the lz4-compressed font off EROFS), and so on every frame for
            // every glyph that no longer fits: a sustained decompress storm that
            // pins memory until the kernel OOM-kills. Instead recycle the atlas
            // once per frame and retry into the freshly-rewound space.
            if (!mGlyphAtlasReset) {
                mGlyphAtlasReset = true;
                resetGlyphAtlas();
                return ensureGlyph(cp, rasterPx);
            }
            // Already recycled this frame. Two ways here: a single frame whose
            // visible glyphs exceed the whole atlas (pathological, never happens
            // for the menu/keyboard), or the boot prewarm warming more of the
            // DATA tree than fits. Return nullptr WITHOUT caching: a real render
            // frame fits comfortably after one recycle so this is unreachable
            // there, and the prewarm tail simply rasterizes lazily on first draw
            // (a one-off hitch, not a permanent blank, not a per-frame storm).
            ALOGW("NanoMenu: glyph atlas full at cp=%u px=%d after recycle", cp, px);
            return nullptr;
        }

        // Convert to RGBA
        std::vector<uint8_t> rgba(bw * bh * 4, 0);
        for (int y = 0; y < bh; y++) {
            for (int x = 0; x < bw; x++) {
                int di = (y * bw + x) * 4;
                if (isColor) {
                    int si = y * bmp->pitch + x * 4;
                    rgba[di + 0] = bmp->buffer[si + 2]; // B->R
                    rgba[di + 1] = bmp->buffer[si + 1]; // G->G
                    rgba[di + 2] = bmp->buffer[si + 0]; // R->B
                    rgba[di + 3] = bmp->buffer[si + 3]; // A
                } else {
                    uint8_t a = bmp->buffer[y * bmp->pitch + x];
                    rgba[di + 0] = 255;
                    rgba[di + 1] = 255;
                    rgba[di + 2] = 255;
                    rgba[di + 3] = a;
                }
            }
        }
        glBindTexture(GL_TEXTURE_2D, mGlyphAtlasTex);
        glTexSubImage2D(GL_TEXTURE_2D, 0, mAtlasCurX, mAtlasCurY,
                        bw, bh, GL_RGBA, GL_UNSIGNED_BYTE, rgba.data());
        // No mip regen: per-size glyphs are sampled 1:1 with GL_LINEAR (mipmaps
        // unused now), so rebuilding the chain per glyph pack would be wasted work.
    }

    GlyphInfo info = {};
    if (bw > 0 && bh > 0) {
        info.u0 = (float)mAtlasCurX / mAtlasW;
        info.v0 = (float)mAtlasCurY / mAtlasH;
        info.u1 = (float)(mAtlasCurX + bw) / mAtlasW;
        info.v1 = (float)(mAtlasCurY + bh) / mAtlasH;
    }
    info.bmpW = bw;
    info.bmpH = bh;
    info.bearingX = face->glyph->bitmap_left;
    info.bearingY = face->glyph->bitmap_top;
    info.advance = (int)(face->glyph->advance.x >> 6);
    info.color = isColor;

    // For emoji, compute scale factor to normalize to mFontSize
    if (isColor && bh > 0) {
        float s = (float)mFontSize / (float)bh;
        info.scaleW = s;
        info.scaleH = s;
        info.advance = mFontSize; // square emoji advance
    } else {
        info.scaleW = 1.0f;
        info.scaleH = 1.0f;
    }

    auto res = mGlyphCache.emplace(key, info);
    GlyphInfo* stored = &res.first->second;

    if (bw > 0 && bh > 0) {
        mAtlasCurX += bw + 1;
        if (bh + 1 > mAtlasRowH) mAtlasRowH = bh + 1;
    }
    return stored;
}

// ---------------------------------------------------------------------------
// Text measurement and rendering
// ---------------------------------------------------------------------------

float NanoMenu::measureText(const char* str, float scale) {
    if (!str || !*str) return 0.0f;
    // Match drawText's per-size layout: glyphs are rasterized at the integer
    // display pixel size and (for mono <= the master) drawn 1:1, so the summed
    // advances are already the device-px width. strResidual covers the upscaled
    // case (text bigger than the master atlas size).
    float displayEm = (float)FONT_CHAR_H * scale;
    int rasterPx = (int)lroundf(displayEm);
    if (rasterPx < 6) rasterPx = 6;
    if (rasterPx > mFontSize) rasterPx = mFontSize;
    float strResidual = (displayEm > (float)mFontSize) ? (displayEm / (float)mFontSize) : 1.0f;
    // Width cache keyed by raster size + string (advances now differ per size).
    // drawList measures every visible label AND value every frame, so this keeps
    // the UTF-8 decode + per-glyph walk off the steady-state path.
    std::string key(1, (char)rasterPx);
    key += (char)(mNdsFontPref ? 1 : 0);   // DSVec advances differ; keep a separate cache slot
    key += str;
    auto cached = mTextWidthCache.find(key);
    if (cached != mTextWidthCache.end()) return cached->second * strResidual;
    float unit = 0.0f;
    for (const char* p = str; *p; ) {
        uint32_t cp;
        uint8_t b0 = (uint8_t)*p;
        if (b0 < 0x80) { cp = b0; p++; }
        else if ((b0 & 0xE0) == 0xC0) { cp = ((b0 & 0x1F) << 6) | (p[1] & 0x3F); p += 2; }
        else if ((b0 & 0xF0) == 0xE0) { cp = ((b0 & 0x0F) << 12) | ((p[1] & 0x3F) << 6) | (p[2] & 0x3F); p += 3; }
        else if ((b0 & 0xF8) == 0xF0) { cp = ((b0 & 0x07) << 18) | ((p[1] & 0x3F) << 12) | ((p[2] & 0x3F) << 6) | (p[3] & 0x3F); p += 4; }
        else { p++; continue; }
        const GlyphInfo* gi = ensureGlyph(cp, rasterPx);
        if (gi) unit += gi->advance * gi->scaleW;
    }
    mTextWidthCache.emplace(std::move(key), unit);
    return unit * strResidual;
}

// 5x capacity: 4 shadow passes + 1 main pass batched into one draw
static const int TEXT_MAX_CHARS = 256;
static const int TEXT_BUF_QUADS = TEXT_MAX_CHARS * 5;
static GLfloat sTextVerts[TEXT_BUF_QUADS * 6 * 2];
static GLfloat sTextUVs[TEXT_BUF_QUADS * 6 * 2];
static GLfloat sTextColors[TEXT_BUF_QUADS * 6 * 4];

// Helper: emit one glyph quad into the batch buffers at position n.
static inline void emitGlyph(int n, float x0, float y0, float x1, float y1,
                              float u0, float v0, float u1, float v1,
                              float cr, float cg, float cb, float ca) {
    int vi = n * 12;
    sTextVerts[vi]= x0; sTextVerts[vi+1]= y0;
    sTextVerts[vi+2]= x1; sTextVerts[vi+3]= y0;
    sTextVerts[vi+4]= x1; sTextVerts[vi+5]= y1;
    sTextVerts[vi+6]= x1; sTextVerts[vi+7]= y1;
    sTextVerts[vi+8]= x0; sTextVerts[vi+9]= y1;
    sTextVerts[vi+10]= x0; sTextVerts[vi+11]= y0;
    int ui = n * 12;
    sTextUVs[ui]= u0; sTextUVs[ui+1]= v1;
    sTextUVs[ui+2]= u1; sTextUVs[ui+3]= v1;
    sTextUVs[ui+4]= u1; sTextUVs[ui+5]= v0;
    sTextUVs[ui+6]= u1; sTextUVs[ui+7]= v0;
    sTextUVs[ui+8]= u0; sTextUVs[ui+9]= v0;
    sTextUVs[ui+10]= u0; sTextUVs[ui+11]= v1;
    int ci = n * 24;
    for (int v = 0; v < 6; v++) {
        sTextColors[ci + v*4] = cr;
        sTextColors[ci + v*4 + 1] = cg;
        sTextColors[ci + v*4 + 2] = cb;
        sTextColors[ci + v*4 + 3] = ca;
    }
}

void NanoMenu::drawText(const char* str, float px, float py, float scale,
                        float r, float g, float b, float a) {
    if (!str || !*str || mFtNumFaces == 0) return;
    flushSolidBatch();   // submit any pending batched solids first so this glyph pass keeps painter order
    // Per-size: rasterize glyphs at the integer display pixel size and blit them
    // 1:1 (for mono text at/below the master) so strokes are crisp and evenly
    // scaled instead of a fractional downscale of the 64px master atlas.
    float displayEm = (float)FONT_CHAR_H * scale;   // device-px em height
    int rasterPx = (int)lroundf(displayEm);
    if (rasterPx < 6) rasterPx = 6;
    if (rasterPx > mFontSize) rasterPx = mFontSize;
    float invW = 2.0f / mWidth, invH = 2.0f / mHeight;
    float baseline = py + displayEm * 0.8f;
    float off = fmaxf(1.0f, scale * 0.4f) * mTextOutlineWidthMul;
    // Pixel offset in NDC
    float offX = off * invW;
    float offY = off * invH;

    // First pass: parse glyphs and compute base positions
    struct GlyphPos { float x0, y0, x1, y1, u0, v0, u1, v1; bool color; };
    GlyphPos glyphs[TEXT_MAX_CHARS];
    int nGlyphs = 0;
    float curX = px;
    for (const char* p = str; *p && nGlyphs < TEXT_MAX_CHARS; ) {
        uint32_t cp;
        uint8_t b0 = (uint8_t)*p;
        if (b0 < 0x80) { cp = b0; p++; }
        else if ((b0 & 0xE0) == 0xC0) { cp = ((b0 & 0x1F) << 6) | (p[1] & 0x3F); p += 2; }
        else if ((b0 & 0xF0) == 0xE0) { cp = ((b0 & 0x0F) << 12) | ((p[1] & 0x3F) << 6) | (p[2] & 0x3F); p += 3; }
        else if ((b0 & 0xF8) == 0xF0) { cp = ((b0 & 0x07) << 18) | ((p[1] & 0x3F) << 12) | ((p[2] & 0x3F) << 6) | (p[3] & 0x3F); p += 4; }
        else { p++; continue; }

        const GlyphInfo* git = ensureGlyph(cp, rasterPx);
        if (!git) continue;
        const GlyphInfo& gi = *git;
        // residual = 1.0 for crisp 1:1 mono at its rasterized size; > 1 only when
        // the text is bigger than the master (upscaled) or a color emoji strike.
        float residual = (gi.color || displayEm > (float)mFontSize)
                       ? (displayEm / (float)mFontSize) : 1.0f;
        if (gi.bmpW == 0 || gi.bmpH == 0) {
            curX += gi.advance * gi.scaleW * residual;
            continue;
        }

        float gw = gi.bmpW * gi.scaleW * residual;
        float gh = gi.bmpH * gi.scaleH * residual;
        float gx = curX + gi.bearingX * gi.scaleW * residual;
        float gy = baseline - gi.bearingY * gi.scaleH * residual;
        // Snap crisp 1:1 glyphs to the device pixel grid so the rasterized-at-size
        // bitmap maps pixel-for-pixel (no sub-pixel blur). The pen advance stays
        // fractional (even spacing); only the blit origin snaps. Upscaled/color
        // glyphs keep sub-pixel positions (smooth).
        if (residual == 1.0f) { gx = floorf(gx + 0.5f); gy = floorf(gy + 0.5f); }

        GlyphPos& gp = glyphs[nGlyphs];
        gp.x0 = gx * invW - 1.0f;
        gp.y0 = 1.0f - (gy + gh) * invH;
        gp.x1 = (gx + gw) * invW - 1.0f;
        gp.y1 = 1.0f - gy * invH;
        gp.u0 = gi.u0; gp.v0 = gi.v0;
        gp.u1 = gi.u1; gp.v1 = gi.v1;
        gp.color = gi.color;
        curX += gi.advance * gi.scaleW * residual;
        nGlyphs++;
    }
    if (nGlyphs == 0) return;

    // Shadow pass selection:
    // - XMB mode uses a single drop shadow (+1,+1). The XMB ribbon background
    //   is dark and moving, so one offset is enough for readability and it
    //   cuts text geometry by 60% (2 passes vs 5). This is a hot path on
    //   Mali-G52: every visible game item emits one drawText call per frame,
    //   and the footer string alone is 76 glyphs.
    // - Normal menu mode keeps the 4-offset outline shadow since the flat
    //   menu background benefits from an omnidirectional outline for
    //   legibility against the blue selection bar.
    // Outline / shadow pass. mTextOutlineMode (see NanoMenu.h): 0 = default
    // (single down-right drop in XMB mode, else 4-offset outline) at 0.8*alpha;
    // 1 = even 4-offset outline at mTextOutlineRatio*alpha (PS3 XMB - symmetric on
    // all four sides, subtle, brightness-scaled, all in this one draw call);
    // 2 = none (glow/halo white copies). The whole pass is one batched glDrawArrays
    // with the main glyphs, so the even outline costs no extra draw calls.
    int n = 0;
    if (mTextOutlineMode != 2) {
        const float ratio = (mTextOutlineMode == 1) ? mTextOutlineRatio : 0.8f;
        const float shadowA = a * ratio;
        const bool single = mXmbMode && (mTextOutlineMode == 0);
        if (single) {
            // Single drop shadow (down-right) — emits nGlyphs quads.
            const float sdx = offX;
            const float sdy = offY;
            for (int i = 0; i < nGlyphs && n < TEXT_BUF_QUADS; i++, n++) {
                const GlyphPos& gp = glyphs[i];
                emitGlyph(n, gp.x0 + sdx, gp.y0 + sdy, gp.x1 + sdx, gp.y1 + sdy,
                          gp.u0, gp.v0, gp.u1, gp.v1,
                          0.0f, 0.0f, 0.0f, shadowA);
            }
        } else {
            // Even 4-offset outline (left/right/up/down).
            static const float dirs[4][2] = {{-1,0},{1,0},{0,-1},{0,1}};
            for (int d = 0; d < 4; d++) {
                float dx = dirs[d][0] * offX;
                float dy = dirs[d][1] * offY;
                for (int i = 0; i < nGlyphs && n < TEXT_BUF_QUADS; i++, n++) {
                    const GlyphPos& gp = glyphs[i];
                    emitGlyph(n, gp.x0 + dx, gp.y0 + dy, gp.x1 + dx, gp.y1 + dy,
                              gp.u0, gp.v0, gp.u1, gp.v1,
                              0.0f, 0.0f, 0.0f, shadowA);
                }
            }
        }
    }
    // Main pass (on top)
    for (int i = 0; i < nGlyphs && n < TEXT_BUF_QUADS; i++, n++) {
        const GlyphPos& gp = glyphs[i];
        float cr = gp.color ? 1.0f : r;
        float cg = gp.color ? 1.0f : g;
        float cb = gp.color ? 1.0f : b;
        emitGlyph(n, gp.x0, gp.y0, gp.x1, gp.y1,
                  gp.u0, gp.v0, gp.u1, gp.v1, cr, cg, cb, a);
    }

    glUseProgram(mTextProgram);
    if (mTextLocSharp >= 0) glUniform1f(mTextLocSharp, mTextSharp);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, mGlyphAtlasTex);
    glUniform1i(mTextLocTexture, 0);
    // Unbind any VBO so the glVertexAttribPointer calls below use the
    // client memory pointers (sTextVerts/UVs/Colors). DrasticRunner's
    // drawDsQuad leaves GL_ARRAY_BUFFER bound to mQuadVbo; without this
    // unbind the driver treats our pointers as byte offsets into that
    // VBO and the glyph verts come out of garbage — visible as the
    // "Quick Resuming..." overlay missing during drastic QR preview on
    // dual-display devices where the secondary pass runs immediately
    // after drastic's drawDsQuad.
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glVertexAttribPointer(mTextLocPosition, 2, GL_FLOAT, GL_FALSE, 0, sTextVerts);
    glEnableVertexAttribArray(mTextLocPosition);
    glVertexAttribPointer(mTextLocTexCoord, 2, GL_FLOAT, GL_FALSE, 0, sTextUVs);
    glEnableVertexAttribArray(mTextLocTexCoord);
    glVertexAttribPointer(mTextLocColor, 4, GL_FLOAT, GL_FALSE, 0, sTextColors);
    glEnableVertexAttribArray(mTextLocColor);
    glDrawArrays(GL_TRIANGLES, 0, n * 6);
    glDisableVertexAttribArray(mTextLocPosition);
    glDisableVertexAttribArray(mTextLocTexCoord);
    glDisableVertexAttribArray(mTextLocColor);
}

// Selected-label glow. The pulsing halo behind the active item/category label was
// 15 separate drawText calls (8 outer ring + 6 inner ring + 1 centre), each of
// which re-decoded the UTF-8 string and re-looked-up every glyph in the cache
// before laying it out. Here the glyph cache lookups happen ONCE; each of the 15
// copies then re-runs drawText's exact advance accumulation from its own offset
// origin (px+dx, py+dy) and emits into the shared batch, flushed in as few draws
// as the buffer allows (one draw for any normal-length label). Bit-identical to
// the original loop: the per-copy arithmetic (curX accumulation, baseline, the
// gx/gy/gw/gh and NDC formulas) is byte-for-byte what drawText does, and the copies
// are emitted in the same order so the additive translucent blend is unchanged.
// All copies are white with mTextOutlineMode == 2 (no outline), matching the loop.
void NanoMenu::drawTextGlow(const char* str, float px, float py, float scale,
                            float oR, float iR, float outerA, float innerA, float mainA) {
    if (!str || !*str || mFtNumFaces == 0) return;
    float displayEm = (float)FONT_CHAR_H * scale;
    int rasterPx = (int)lroundf(displayEm);
    if (rasterPx < 6) rasterPx = 6;
    if (rasterPx > mFontSize) rasterPx = mFontSize;
    float invW = 2.0f / mWidth, invH = 2.0f / mHeight;
    // Pass 1: resolve glyphs ONCE (the expensive UTF-8 decode + cache lookup).
    const GlyphInfo* gl[TEXT_MAX_CHARS];
    int nG = 0;
    for (const char* p = str; *p && nG < TEXT_MAX_CHARS; ) {
        uint32_t cp;
        uint8_t b0 = (uint8_t)*p;
        if (b0 < 0x80) { cp = b0; p++; }
        else if ((b0 & 0xE0) == 0xC0) { cp = ((b0 & 0x1F) << 6) | (p[1] & 0x3F); p += 2; }
        else if ((b0 & 0xF0) == 0xE0) { cp = ((b0 & 0x0F) << 12) | ((p[1] & 0x3F) << 6) | (p[2] & 0x3F); p += 3; }
        else if ((b0 & 0xF8) == 0xF0) { cp = ((b0 & 0x07) << 18) | ((p[1] & 0x3F) << 12) | ((p[2] & 0x3F) << 6) | (p[3] & 0x3F); p += 4; }
        else { p++; continue; }
        const GlyphInfo* gptr = ensureGlyph(cp, rasterPx);
        if (!gptr) continue;
        gl[nG++] = gptr;
    }
    if (nG == 0) return;
    // The 15 copies, in submission order (8 outer ring, 6 inner ring, centre).
    struct Tap { float dx, dy, a; };
    Tap taps[15];
    int nT = 0;
    for (int k = 0; k < 8; k++) { float a = (float)k / 8.0f * 2.0f * (float)M_PI;
        taps[nT].dx = cosf(a) * oR; taps[nT].dy = sinf(a) * oR; taps[nT].a = outerA; nT++; }
    for (int k = 0; k < 6; k++) { float a = ((float)k + 0.5f) / 6.0f * 2.0f * (float)M_PI;
        taps[nT].dx = cosf(a) * iR; taps[nT].dy = sinf(a) * iR; taps[nT].a = innerA; nT++; }
    taps[nT].dx = 0.0f; taps[nT].dy = 0.0f; taps[nT].a = mainA; nT++;

    glUseProgram(mTextProgram);
    if (mTextLocSharp >= 0) glUniform1f(mTextLocSharp, mTextSharp);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, mGlyphAtlasTex);
    glUniform1i(mTextLocTexture, 0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    int n = 0;
    auto flush = [&]() {
        if (n == 0) return;
        glVertexAttribPointer(mTextLocPosition, 2, GL_FLOAT, GL_FALSE, 0, sTextVerts);
        glEnableVertexAttribArray(mTextLocPosition);
        glVertexAttribPointer(mTextLocTexCoord, 2, GL_FLOAT, GL_FALSE, 0, sTextUVs);
        glEnableVertexAttribArray(mTextLocTexCoord);
        glVertexAttribPointer(mTextLocColor, 4, GL_FLOAT, GL_FALSE, 0, sTextColors);
        glEnableVertexAttribArray(mTextLocColor);
        glDrawArrays(GL_TRIANGLES, 0, n * 6);
        n = 0;
    };
    for (int t = 0; t < nT; t++) {
        float curX = px + taps[t].dx;
        float baseline = (py + taps[t].dy) + displayEm * 0.8f;
        float a = taps[t].a;
        bool isCenter = (taps[t].dx == 0.0f && taps[t].dy == 0.0f);
        for (int i = 0; i < nG; i++) {
            const GlyphInfo& gi = *gl[i];
            float residual = (gi.color || displayEm > (float)mFontSize)
                           ? (displayEm / (float)mFontSize) : 1.0f;
            if (gi.bmpW == 0 || gi.bmpH == 0) { curX += gi.advance * gi.scaleW * residual; continue; }
            float gw = gi.bmpW * gi.scaleW * residual;
            float gh = gi.bmpH * gi.scaleH * residual;
            float gx = curX + gi.bearingX * gi.scaleW * residual;
            float gy = baseline - gi.bearingY * gi.scaleH * residual;
            // Snap the crisp centre copy (the actual active label) to the grid;
            // halo copies stay sub-pixel so the glow stays smooth.
            if (isCenter && residual == 1.0f) { gx = floorf(gx + 0.5f); gy = floorf(gy + 0.5f); }
            if (n >= TEXT_BUF_QUADS) flush();
            float qx0 = gx * invW - 1.0f;
            float qy0 = 1.0f - (gy + gh) * invH;
            float qx1 = (gx + gw) * invW - 1.0f;
            float qy1 = 1.0f - gy * invH;
            // Glow copies are white (the original passed r=g=b=1); colour glyphs
            // still render their own atlas colour, matching drawText's main pass.
            emitGlyph(n++, qx0, qy0, qx1, qy1, gi.u0, gi.v0, gi.u1, gi.v1, 1.0f, 1.0f, 1.0f, a);
            curX += gi.advance * gi.scaleW * residual;
        }
    }
    flush();
    glDisableVertexAttribArray(mTextLocPosition);
    glDisableVertexAttribArray(mTextLocTexCoord);
    glDisableVertexAttribArray(mTextLocColor);
}

// ---------------------------------------------------------------------------
// Secondary display setup (post-boot)
// ---------------------------------------------------------------------------

// True when this is a physical dual-screen device (e.g. the RG DS: dual DSI). Used to gate
// dual-screen-only UI such as the per-app Dual-Stack allowlist toggle in the XMB option menu.
// The physical display set does not change at runtime here, so cache the first query (a binder
// call to SurfaceFlinger) and reuse it.
bool NanoMenu::hasSecondaryDisplay() {
    if (mDualScreenCache < 0)
        mDualScreenCache =
                (SurfaceComposerClient::getPhysicalDisplayIds().size() > 1) ? 1 : 0;
    return mDualScreenCache == 1;
}

// Create EGL window surfaces for every non-primary physical display so the
// existing post-HWC render loop can drive wallpaper rendering on those panels.
// Called once after drmStop() — before that point the DRM-direct path's
// secondary AHB is feeding those displays directly.
//
// Idempotent: returns immediately if surfaces are already set up.
//
// Concretely on the RG DS (RK3568, dual DSI 640x480):
//   - persist.gammaos.nano.primary_display=1 → port 1 holds the XMB.
//   - This function creates a wallpaper SurfaceControl on port 0's
//     layerStack and wraps it in an EGLSurface. The render loop renders
//     the wallpaper effect into it every frame.
//   - Bootanim is killed once the surface is up so it stops fighting for
//     the secondary display's layer stack.
void NanoMenu::setupSecondaryEglSurfaces() {
    if (!mSecondaryEglSurfaces.empty()) return; // already set up
    if (mDisplay == EGL_NO_DISPLAY) return;

    int64_t t0 = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;

    const std::vector<PhysicalDisplayId> ids =
            SurfaceComposerClient::getPhysicalDisplayIds();
    if (ids.size() <= 1) {
        return;
    }

    // Match the same primary_display port the readyToRun() path used so we
    // skip exactly the display the XMB renders on.
    int primaryPort = 0;
    {
        char p[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.primary_display", p, "0");
        primaryPort = atoi(p);
    }

    // wantAlpha=true: the secondary layer is RGBA now (focus ring shows a bottom app through its
    // transparent centre, and the CC dashboard alpha-force needs a writable alpha plane).
    EGLConfig config = getEglConfig(mDisplay, true);
    if (config == nullptr) {
        ALOGW("NanoMenu: secondary setup failed — no EGL config");
        return;
    }

    for (const PhysicalDisplayId& pid : ids) {
        const int port = static_cast<int>(pid.getPort());
        if (port == primaryPort) continue;

        sp<IBinder> token = SurfaceComposerClient::getPhysicalDisplayToken(pid);
        if (token == nullptr) {
            ALOGW("NanoMenu: secondary port %d has no display token", port);
            continue;
        }

        // GammaOS: When nano restarts after exiting an SF/HWC-based app
        // (e.g. RetroArch), SurfaceFlinger may leave the secondary HWC
        // display in an inactive power state. dumpsys SurfaceFlinger shows
        // it as "Display 1 (inactive)" and the bottom screen stays blank
        // no matter what we render into it. Force the display ON here so
        // the HWC scans out our wallpaper surface. PowerMode::ON = 2 per
        // android.hardware.graphics.composer@2.1 IComposerClient.hal.
        SurfaceComposerClient::setDisplayPowerMode(token, 2);

        ui::DisplayState state;
        ui::LayerStack stack = ui::DEFAULT_LAYER_STACK;
        if (SurfaceComposerClient::getDisplayState(token, &state) == NO_ERROR) {
            stack = state.layerStack;
        } else {
            ALOGW("NanoMenu: secondary port %d getDisplayState failed", port);
            continue;
        }

        DisplayMode mode;
        if (SurfaceComposerClient::getActiveDisplayMode(token, &mode) != NO_ERROR) {
            ALOGW("NanoMenu: secondary port %d getActiveDisplayMode failed", port);
            continue;
        }

        ui::Size res = mode.resolution;
        // TRANSLUCENT (RGBA, no eOpaque), mirroring the top overlay layer (NanoMenu.cpp:1070): the CC
        // frames still fill the panel opaquely (they clear alpha=1 and force alpha=1 before the swap),
        // but this lets the focus-ring frame clear to alpha 0 so a bottom app shows through the ring's
        // transparent centre. With nothing behind the CC on this display, opaque vs translucent looks
        // identical for the dashboard.
        sp<SurfaceControl> sc = session()->createSurface(
                String8("GammaOSNanoWallpaper"),
                res.getWidth(), res.getHeight(),
                PIXEL_FORMAT_RGBA_8888, 0u);
        if (sc == nullptr || !sc->isValid()) {
            ALOGW("NanoMenu: secondary port %d createSurface failed", port);
            continue;
        }

        // Route to the secondary display's layer stack at top z so it
        // overrides bootanim (which uses STRATUM_BOOT_PROGRESS layers).
        SurfaceComposerClient::Transaction t;
        Rect bounds(0, 0, res.width, res.height);
        // Do NOT reset this port's projection to the physical size while a Dual-Stack app has
        // forced it to a TALL logical canvas (640x960): that clobbers DualStackController's tall
        // projection (the documented WM/SF desync), which then flips layerStackSpaceRect back to
        // 640x480 and defeats the Dual-Stack coverage stretch below (carousel drops to half). Let
        // DualStack own the projection while it is active; the coverage stretch fills the canvas.
        if (!property_get_bool("sys.gammaos.dualstack.active", false)) {
            t.setDisplayProjection(token, ui::ROTATION_0, bounds, bounds);
        }
        t.setLayer(sc, 0x40000001);
        t.setLayerStack(sc, stack);
        t.show(sc);
        t.apply();

        sp<Surface> s = sc->getSurface();
        EGLSurface eglSurf = eglCreateWindowSurface(mDisplay, config, s.get(), nullptr);
        if (eglSurf == EGL_NO_SURFACE) {
            ALOGW("NanoMenu: secondary port %d eglCreateWindowSurface failed: 0x%x",
                  port, eglGetError());
            continue;
        }

        // Set the secondary surface to swap interval 0 (no vsync wait) so its
        // swap doesn't block the primary 60fps loop. The wallpaper still
        // animates smoothly because the render loop runs every frame; we just
        // don't artificially serialize on the secondary's vsync.
        // eglSwapInterval applies to the surface that is currently bound, so
        // we need to make the secondary current temporarily.
        EGLSurface prevDraw = eglGetCurrentSurface(EGL_DRAW);
        EGLSurface prevRead = eglGetCurrentSurface(EGL_READ);
        EGLContext prevCtx  = eglGetCurrentContext();
        if (eglMakeCurrent(mDisplay, eglSurf, eglSurf, mContext) == EGL_TRUE) {
            eglSwapInterval(mDisplay, 0);
            // Restore primary as current.
            eglMakeCurrent(mDisplay, prevDraw, prevRead, prevCtx);
        }

        mSecondaryDisplayTokens.push_back(token);
        mSecondaryWallpaperControls.push_back(sc);
        mSecondarySurfaces.push_back(s);
        mSecondaryEglSurfaces.push_back(eglSurf);
        mSecondaryAppliedLayerStacks.push_back(stack.id);
        mSecondaryCreatedSize.push_back({res.width, res.height});
        mSecondaryAppliedLssH.push_back(-1);   // stretch not yet applied (Dual-Stack coverage)

        int64_t now = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
        ALOGI("NanoMenu: secondary EGL surface ready: port=%d layerStack=%u %dx%d at T+%lldms",
              port, stack.id, res.width, res.height, now);
    }

    if (!mSecondaryEglSurfaces.empty()) {
        // Tell bootanim to exit so it stops painting on the secondary display.
        // CRITICAL: do NOT set service.bootanim.exit — that property is also
        // nano's OWN exit signal, polled in threadLoop. Setting it here would
        // make nano shut itself down two frames after secondary setup. Use the
        // init ctl.stop command instead, which kills the bootanimation
        // service via init without touching the shared exit-signal property.
        property_set("ctl.stop", "bootanim");
        int64_t now = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
        ALOGI("NanoMenu: secondary wallpaper setup complete (%zu surfaces, %lldms)",
              mSecondaryEglSurfaces.size(), now - t0);
    }
}

// Control Center present: render the bottom-screen dashboard on the SECONDARY panel while a
// single-screen app owns the top. Called from the overlay park branch (NanoMenu.cpp threadLoop)
// instead of fully parking, so it must be self-contained: set up the secondary EGL surface once,
// show its layer (hidden by default during app play), render the dashboard, present, and restore
// the primary surface current. Kept lightweight (small GL footprint, capped rate by the caller).
// Pagination dots along the CC bottom edge: two pills (dashboard, apps). The active page's pill is
// bright and elongated, the other a dim dot; both cross-fade with mCcPageOffset so the indicator
// tracks the horizontal slide 1:1. Device-pixel sized so it adapts to any panel resolution.
void NanoMenu::renderCcPageDots() {
    const int nPages = 2;
    float dotR = (float)mHeight * 0.0105f;          // ~5px tall at 480; scales with the panel
    if (dotR < 2.0f) dotR = 2.0f;
    float gap  = dotR * 3.6f;                        // centre-to-centre spacing
    float cy   = (float)mHeight - dotR * 3.4f;       // just above the bottom edge
    float x0   = (float)mWidth * 0.5f - gap * (float)(nPages - 1) * 0.5f;
    setUiBlend();
    for (int i = 0; i < nPages; i++) {
        float act = (i == 0) ? (1.0f - mCcPageOffset) : mCcPageOffset;   // two-page cross-fade
        if (act < 0.0f) act = 0.0f; else if (act > 1.0f) act = 1.0f;
        float cx    = x0 + gap * (float)i;
        float br    = 0.30f + 0.62f * act;           // dim grey -> bright white
        float halfW = dotR * (1.0f + 1.4f * act);    // dot -> short pill as it becomes active
        // subtle shadow so the dots read on a light dashboard card as well as the dark app grid
        drawRoundedRect(cx - halfW, cy - dotR + 1.0f, halfW * 2.0f, dotR * 2.0f, dotR, 0.0f, 0.0f, 0.0f, 0.35f);
        drawRoundedRect(cx - halfW, cy - dotR,        halfW * 2.0f, dotR * 2.0f, dotR, br,   br,   br,   0.92f);
    }
}

// Present one focus-ring frame on the TOP overlay surface (mSurface / mFlingerSurfaceControl). That
// layer is a resident, translucent (RGBA), top-z overlay created hidden; render only the ring over the
// fully-visible app (clear alpha 0) then reveal it once (deferred, like overlayShow, so no opaque flash).
// Never touched while the power-button overlay owns the layer (guarded by !mOverlayShown + the caller).
void NanoMenu::renderTopFocusRing() {
    if (mFlingerSurfaceControl == nullptr || mSurface == EGL_NO_SURFACE) return;
    eglMakeCurrent(mDisplay, mSurface, mSurface, mContext);
    glViewport(0, 0, mWidth, mHeight);
    {   // upload the overlay (identity) rotation to the draw programs so the ring lands upright
        const GLuint progs[] = {mShaderProgram, mTextProgram, mParticleProgram, mFxProgram, mXmbProgram};
        const GLint  locs[]  = {mLocRotation, mTextLocRotation, mParticleLocRotation, mFxLocRotation, mXmbLocRotation};
        for (int i = 0; i < 5; i++) { if (progs[i]) { glUseProgram(progs[i]); glUniformMatrix2fv(locs[i], 1, GL_FALSE, sDrmRotMat); } }
    }
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);   // transparent: the app shows through everywhere but the ring
    glClear(GL_COLOR_BUFFER_BIT);
    drawFocusRing(ccRingT());
    eglSwapBuffers(mDisplay, mSurface);
    if (!mTopRingShown) {                    // reveal AFTER the first transparent+ring frame is composited
        SurfaceComposerClient::Transaction t;
        t.show(mFlingerSurfaceControl);
        t.apply();
        mTopRingShown = true;
    }
}

void NanoMenu::hideTopFocusRing() {
    if (!mTopRingShown) return;
    if (mOverlayShown) { mTopRingShown = false; return; }   // never hide a layer the real overlay owns
    SurfaceComposerClient::Transaction t;
    t.hide(mFlingerSurfaceControl);
    t.apply();
    mTopRingShown = false;
}

// Present one focus-ring frame on the BOTTOM secondary surface. Reused over the CC dashboard (already
// shown) and over a bottom app (shown here; the secondary is translucent so the alpha-0 clear lets the
// app through the ring's centre). Restores the primary current after.
void NanoMenu::renderBottomFocusRing() {
    if (mSecondaryEglSurfaces.empty()) return;
    eglMakeCurrent(mDisplay, mSecondaryEglSurfaces[0], mSecondaryEglSurfaces[0], mContext);
    glViewport(0, 0, mWidth, mHeight);
    {
        const GLuint progs[] = {mShaderProgram, mTextProgram, mParticleProgram, mFxProgram, mXmbProgram};
        const GLint  locs[]  = {mLocRotation, mTextLocRotation, mParticleLocRotation, mFxLocRotation, mXmbLocRotation};
        for (int i = 0; i < 5; i++) { if (progs[i]) { glUseProgram(progs[i]); glUniformMatrix2fv(locs[i], 1, GL_FALSE, sDrmRotMat); } }
    }
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    drawFocusRing(ccRingT());
    eglSwapBuffers(mDisplay, mSecondaryEglSurfaces[0]);
    // Reveal AFTER the transparent ring frame is queued (deferred, like the top path): otherwise the
    // secondary's last opaque dashboard buffer (from before the bottom app hid it) would flash over the
    // app for one frame on show.
    if (!mSecondaryWallpaperControls.empty() && !mNdsSecondaryShown) {
        SurfaceComposerClient::Transaction t;
        for (const auto& sc : mSecondaryWallpaperControls) if (sc != nullptr) t.show(sc);
        t.apply();
        mNdsSecondaryShown = true;
    }
    eglMakeCurrent(mDisplay, mSurface, mSurface, mContext);   // restore primary current
}

// Hide the Control Center's secondary layer so the pinned bottom-panel IME (system soft keyboard, routed
// to display 0 on a dual-screen device) shows through and receives the bottom digitizer. The CC surface is
// a high-layer SF layer over display 0 that visually occludes the IME window beneath it; hiding it reveals
// the keyboard. Idempotent - the next renderControlCenterFrame re-shows it via its !mNdsSecondaryShown
// t.show once the IME hides. Called from the park loop when sys.gammaos.nano.ime_visible=1.
void NanoMenu::ccHideForIme() {
    if (!mNdsSecondaryShown || mSecondaryWallpaperControls.empty()) return;
    SurfaceComposerClient::Transaction t;
    for (const auto& sc : mSecondaryWallpaperControls) if (sc != nullptr) t.hide(sc);
    t.apply();
    mNdsSecondaryShown = false;
}

void NanoMenu::renderControlCenterFrame() {
    if (mSecondaryEglSurfaces.empty()) {
        setupSecondaryEglSurfaces();
        if (mSecondaryEglSurfaces.empty()) return;   // genuine single-screen / setup failed
    }
    // Make the secondary layer visible so the dashboard covers the bottom panel (the running
    // single-screen app does not draw there). Hidden again on teardown (see the park branch).
    if (!mSecondaryWallpaperControls.empty() && !mNdsSecondaryShown) {
        SurfaceComposerClient::Transaction t;
        for (const auto& sc : mSecondaryWallpaperControls) if (sc != nullptr) t.show(sc);
        t.apply();
        mNdsSecondaryShown = true;
    }
    // Stacked-canvas coverage for the Control Center (RG DS): the CC's secondary display (port 0,
    // the bottom panel) presents a TALL logical canvas (640x960 - top+bottom stacked into one
    // display), but the CC surface is created at the PHYSICAL mode size (640x480). With the surface
    // matrix at identity it maps to only the top 640x480 of the 640x960 canvas; SF down-projects the
    // full canvas onto the 640x480 physical panel, so the CC reads squished into the top half ("like
    // dualstack mode"). Stretching the CC LAYER by sy=canvasH/createdH fills the tall canvas, and the
    // panel's own down-projection cancels the stretch so the CC reads at its natural size. This is the
    // SAME transform as the overlay ds-cover block in the main secondary loop, but that block lives
    // PAST the CC park-branch `continue`, so it never runs while the CC renders over an app - the CC
    // path must (re)assert it here. A display reconfigure on app launch resets the layer transform back
    // to identity while the logical size is unchanged, so re-apply every CC frame; setMatrix is a cheap
    // idempotent transaction at the CC's ~20fps and only the port-0 (index 0) surface is the CC's.
    if (!mSecondaryWallpaperControls.empty()
            && !mSecondaryDisplayTokens.empty()
            && !mSecondaryCreatedSize.empty()
            && mSecondaryWallpaperControls[0] != nullptr
            && mSecondaryDisplayTokens[0] != nullptr) {
        ui::DisplayState st;
        if (SurfaceComposerClient::getDisplayState(mSecondaryDisplayTokens[0], &st) == NO_ERROR) {
            const int lssW = (int)st.layerStackSpaceRect.getWidth();
            const int lssH = (int)st.layerStackSpaceRect.getHeight();
            const int cw = mSecondaryCreatedSize[0].first;
            const int ch = mSecondaryCreatedSize[0].second;
            if (lssW > 0 && lssH > 0 && cw > 0 && ch > 0) {
                SurfaceComposerClient::Transaction t;
                t.setMatrix(mSecondaryWallpaperControls[0],
                            (float)lssW / (float)cw, 0.0f, 0.0f, (float)lssH / (float)ch);
                t.apply();
            }
        }
    }
    eglMakeCurrent(mDisplay, mSecondaryEglSurfaces[0], mSecondaryEglSurfaces[0], mContext);
    glViewport(0, 0, mWidth, mHeight);   // secondary is the same resolution as the primary
    // Upload the (overlay = identity) panel rotation to the draw programs so drawText/quads land
    // right on the secondary (render()'s uploadRotationMatrices lambda is out of scope here).
    {
        const GLuint progs[] = {mShaderProgram, mTextProgram, mParticleProgram, mFxProgram, mXmbProgram};
        const GLint  locs[]  = {mLocRotation, mTextLocRotation, mParticleLocRotation, mFxLocRotation, mXmbLocRotation};
        for (int i = 0; i < 5; i++) { glUseProgram(progs[i]); glUniformMatrix2fv(locs[i], 1, GL_FALSE, sDrmRotMat); }
    }
    pollControlCenterStats();   // once per frame here (renderCcPass no longer polls) so the cache
                                // signature and the dynamic numbers both read the same fresh sCc.
    // Static-layer cache: bake the frame-invariant dashboard into mCcStaticTex (only when its signature
    // changes), composite it as one opaque full-panel quad, then redraw just the live elements over it.
    // Gate/fallback: persist.gammaos.nano.cc.cache=0 or an FBO-incomplete GPU uses the all-immediate path
    // (pixel-identical by construction). Read the gate once.
    static int sCcCacheOn = -1;
    if (sCcCacheOn < 0) sCcCacheOn = property_get_bool("persist.gammaos.nano.cc.cache", true) ? 1 : 0;
    // Page slide: ease mCcPageOffset toward the target page (0 = dashboard, 1 = app grid) over ~0.28s.
    float pgTarget = (float)mCcPage;
    if (mCcPageOffset != pgTarget) {
        float step = (mFrameDt > 0.0f ? mFrameDt : 0.016f) / 0.28f;
        if (mCcPageOffset < pgTarget) { mCcPageOffset += step; if (mCcPageOffset > pgTarget) mCcPageOffset = pgTarget; }
        else                          { mCcPageOffset -= step; if (mCcPageOffset < pgTarget) mCcPageOffset = pgTarget; }
    }
    const bool onDashboard = (mCcPageOffset <= 0.001f);
    mCcPassXoff = 0.0f;
    if (onDashboard && sCcCacheOn) {
        ccEnsureStaticCache();
        glViewport(0, 0, mWidth, mHeight);   // ccEnsureStaticCache restored the caller viewport; re-assert
    }
    if (onDashboard && sCcCacheOn && mCcStaticValid && mCcStaticTex) {
        // Dashboard settled: composite the static cache + dynamic overlay (the cheap steady-state path).
        glDisable(GL_BLEND);   // opaque 1:1 composite of the cache (texel.a irrelevant)
        // flipV: the cache is an FBO render (GL bottom-left origin), so sample it V-flipped to stay upright.
        drawIconTex(mCcStaticTex, 0.0f, 0.0f, (float)mWidth, (float)mHeight, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f, true);
        renderCcDynamic();     // re-issues setUiBlend(); draws hands/arcs/numbers/fills/status/date over the cache
    } else {
        // Mid-slide or on the app page (or cache disabled): render immediate, each page translated by the
        // eased offset so the dashboard slides left as the app grid slides in from the right.
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        float e = mCcPageOffset * mCcPageOffset * (3.0f - 2.0f * mCcPageOffset);   // smoothstep slide
        if (e < 0.999f) { mCcPassXoff = -e * (float)mWidth;          renderControlCenterUI(); }  // dashboard
        if (e > 0.001f) { mCcPassXoff = (1.0f - e) * (float)mWidth;  renderCcApps(true, true); } // app grid
        mCcPassXoff = 0.0f;
    }
    // Pagination dots: which CC page is showing (dashboard <-> apps). Drawn after both pages so they
    // sit fixed at the bottom edge (they do not slide with the pages) and animate with mCcPageOffset.
    renderCcPageDots();
    // Fade the dashboard in from black each time the CC comes up (reset on the activation edge in the
    // park branch). A shrinking full-panel black quad over the composited frame; smoothstep for a soft
    // ease. ~0.45s. Costs nothing once done (mCcFadeIn latches at 1 -> the quad is skipped).
    if (mCcFadeIn < 1.0f) {
        mCcFadeIn += (mFrameDt > 0.0f ? mFrameDt : 0.016f) / 0.45f;
        if (mCcFadeIn > 1.0f) mCcFadeIn = 1.0f;
        float e = mCcFadeIn * mCcFadeIn * (3.0f - 2.0f * mCcFadeIn);   // smoothstep reveal
        float black = 1.0f - e;
        if (black > 0.001f) {
            setUiBlend();
            drawQuad(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f, 0.0f, 0.0f, black);
        }
    }
    // Focus ring over the CC dashboard (bottom took the controller). Drawn opaquely over the CC (the
    // app is not behind the CC on this display), so no transparency is needed here.
    if (ccRingActive() && mCcRingDisp == property_get_int32("persist.gammaos.nano.cc.bottomdisplay", 0))
        drawFocusRing(ccRingT());
    // The secondary layer is RGBA (translucent) now so the bottom-app ring can show the app through.
    // The dashboard must stay opaque though: force the whole panel's alpha to 1 (RGB untouched) so any
    // drawn alpha<1 never becomes a see-through hole on the dashboard.
    glColorMask(GL_FALSE, GL_FALSE, GL_FALSE, GL_TRUE);
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    eglSwapBuffers(mDisplay, mSecondaryEglSurfaces[0]);
    eglMakeCurrent(mDisplay, mSurface, mSurface, mContext);   // restore the primary current
}

// Hide the control-center secondary layer again (on teardown: app exit / overlay raised / feature
// off) so a subsequent game's bottom screen is not covered by a stale nano surface.
void NanoMenu::hideControlCenterLayer() {
    if (!mSecondaryWallpaperControls.empty() && mNdsSecondaryShown) {
        SurfaceComposerClient::Transaction t;
        for (const auto& sc : mSecondaryWallpaperControls) if (sc != nullptr) t.hide(sc);
        t.apply();
        mNdsSecondaryShown = false;
    }
    // Free the static-layer cache so a later CC activation re-bakes at the then-current size/state and we
    // do not leak an FBO + full-panel RGBA8 texture while hidden. This runs from the park loop's NOT-showing
    // branch with the PRIMARY surface current, but the GL objects live on the shared mContext, so make a
    // secondary surface current to delete them, then restore the previous surface/context.
    if ((mCcStaticFbo || mCcStaticTex) && !mSecondaryEglSurfaces.empty()) {
        EGLSurface prevDraw = eglGetCurrentSurface(EGL_DRAW);
        EGLSurface prevRead = eglGetCurrentSurface(EGL_READ);
        EGLContext prevCtx  = eglGetCurrentContext();
        if (eglMakeCurrent(mDisplay, mSecondaryEglSurfaces[0], mSecondaryEglSurfaces[0], mContext) == EGL_TRUE) {
            ccFreeStaticCache();
            eglMakeCurrent(mDisplay, prevDraw, prevRead, prevCtx);
        }
    }
}

// ---------------------------------------------------------------------------
// Render
// ---------------------------------------------------------------------------

// GammaOS Nano debug capture: when sys.gammaos.nano.shot is set, read the
// just-composited frame straight out of the bound framebuffer and write it as a
// binary PPM. This is the only capture that works on the DRM-direct path, where
// SurfaceFlinger (screencap) and fbdev (/dev/graphics/fb0) see only black.
// Set the prop to "1" for the default path, or to an absolute file path; it is
// cleared after one capture.
// Non-static so the Quick Resume splash loop (NanoMenu.cpp), which renders
// outside the normal render() path, can capture its live game preview too.
static void nanoScreenshotProp(const char* prop, const char* defPath) {
    char val[PROPERTY_VALUE_MAX] = {};
    property_get(prop, val, "");
    if (!val[0]) return;
    GLint vp[4] = {0, 0, 0, 0};
    glGetIntegerv(GL_VIEWPORT, vp);
    int w = vp[2], h = vp[3];
    if (w <= 0 || h <= 0) { property_set(prop, ""); return; }
    std::vector<unsigned char> px((size_t)w * h * 4);
    glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, px.data());
    const char* path = (val[0] == '1' && !val[1]) ? defPath : val;
    FILE* f = fopen(path, "wb");
    if (f) {
        fprintf(f, "P6\n%d %d\n255\n", w, h);
        std::vector<unsigned char> row((size_t)w * 3);
        for (int y = h - 1; y >= 0; y--) {     // glReadPixels is bottom-up
            const unsigned char* src = px.data() + (size_t)y * w * 4;
            for (int x = 0; x < w; x++) {
                row[x * 3 + 0] = src[x * 4 + 0];
                row[x * 3 + 1] = src[x * 4 + 1];
                row[x * 3 + 2] = src[x * 4 + 2];
            }
            fwrite(row.data(), 1, row.size(), f);
        }
        fclose(f);
        ALOGI("nano: screenshot %dx%d -> %s", w, h, path);
    } else {
        ALOGE("nano: screenshot open failed: %s", path);
    }
    property_set(prop, "");
}

// Capture the just-composited SECONDARY (bottom DS) panel: sys.gammaos.nano.shot2.
// Must be called while the secondary EGL surface is current (in the secondary pass).
void maybeNanoScreenshotSecondary() {
    nanoScreenshotProp("sys.gammaos.nano.shot2", "/data/local/tmp/nano_shot2.ppm");
}

void maybeNanoScreenshot() {
    nanoScreenshotProp("sys.gammaos.nano.shot", "/data/local/tmp/nano_shot.ppm");
}

// Background watchdog: if render() stops bumping mRenderHeartbeat for ~8s the
// render thread is hung (infinite loop or a stuck GL/IPC call). We abort() from
// here, which debuggerd turns into a tombstone containing EVERY thread's stack
// (so the hung render thread's exact location is captured under /data/tombstones),
// and init then restarts gammaos-nano - turning a silent permanent freeze into a
// diagnosable, self-recovering event. Generous timeout so legitimate slow asset
// loads never trip it; only a true stall (no frame for 8s) aborts.
void NanoMenu::startRenderWatchdog() {
    std::thread([this]() {
        uint64_t last = 0;
        int stuck = 0;
        for (;;) {
            usleep(2000000);   // 2s
            // enterDrmSleep() intentionally parks the render thread (screen off /
            // waiting for the wake press), so the heartbeat legitimately stops.
            // Aborting then kills the oneshot home process: the panel never
            // relights, the power button looks dead, and background music dies.
            // Skip the stall check while parked.
            //
            // A foreground app owns the whole screen (sys.gammaos.nano.app_launched=1):
            // nano is parked behind it (render() skipped) and its teardown/idle path makes
            // GL/EGL calls (videoHardFree/freeMusicVisGl in the park loop, or the overlay
            // instance drawing the clock/CC over the app). Those calls SHARE the one Mali
            // GPU with the app. When the app's own RenderThread faults on this GPU/driver
            // (e.g. the streaming players' 960x544 hwui AHardwareBuffer-import abort), the
            // shared GPU/driver wedges and nano's next GL call blocks indefinitely - the
            // heartbeat freezes and this watchdog would abort nano too, leaving BOTH dead
            // (a black "limbo" panel) instead of just the app crashing. nano renders
            // nothing useful while an app is foreground, so do NOT self-kill on an external
            // GPU wedge: skip the stall check while an app is up. On app exit app_launched
            // clears, the exemption lifts, and a still-stalled nano recovers normally.
            if (mInDrmSleep.load(std::memory_order_relaxed)
                || mVidTeardownExempt.load(std::memory_order_relaxed)
                || property_get_bool("sys.gammaos.nano.app_launched", false)) {
                // Parked for sleep, joining a wedged video-open worker during a forced
                // teardown (sleep/occlusion), or parked behind a foreground app whose crash
                // may have wedged the shared GPU: the heartbeat legitimately stalls; don't
                // abort. NOTE: a NORMAL video open no longer exempts the watchdog - the
                // render thread stays responsive (it spins the loading spinner); only this
                // rare blocking teardown join is exempt.
                stuck = 0;
                last = mRenderHeartbeat.load(std::memory_order_relaxed);
                continue;
            }
            uint64_t cur = mRenderHeartbeat.load(std::memory_order_relaxed);
            if (cur != 0 && cur == last) {
                if (++stuck >= 4) {   // ~8s with no new frame
                    ALOGE("NanoMenu WATCHDOG: render thread stalled ~8s "
                          "(heartbeat=%llu) - aborting for a stack tombstone",
                          (unsigned long long)cur);
                    abort();   // -> debuggerd tombstone (all thread stacks) + restart
                }
            } else {
                stuck = 0;
            }
            last = cur;
        }
    }).detach();
}

void NanoMenu::render() {
    static bool sFirstFrame = true;
    if (sFirstFrame) {
        sFirstFrame = false;
    }
    // Render-thread watchdog heartbeat: bumped every frame so a background thread
    // can detect a hang (e.g. an infinite loop or a stuck GL call inside a render
    // path) and abort into a tombstone instead of leaving the device frozen.
    mRenderHeartbeat.fetch_add(1, std::memory_order_relaxed);
    if (!mWatchdogStarted) { mWatchdogStarted = true; startRenderWatchdog(); }

    // GammaOS System Update (OTA): pump the check/download state machine + live download %
    // while the flow owns the shared dialog (NanoMenuOta.cpp). A handoff exits the process.
    if (mOtaFlowActive) otaFlowTick();

    // Allow at most one glyph-atlas recycle per frame (see ensureGlyph): the
    // first overflow rewinds the atlas, later overflows in the same frame fall
    // back to blank glyphs rather than recycling in a loop.
    mGlyphAtlasReset = false;

    // (The glyph atlas has no mip chain: setGlyphAtlasAA is a no-op and all text
    // samples level 0 via GL_LINEAR, so there is nothing to regenerate here.)
    // Default text AA off each frame; the home-XMB content and the post-Hello
    // setup-wizard steps opt in, and renderOsk() forces it back off so the
    // keyboard never gets it. This also covers legacy/text-menu modes that do
    // not manage the flag.
    setGlyphAtlasAA(false);

    // Once-per-frame state update for background effects (particle motion,
    // XMB ribbon time advance). Must run before either pass below so both
    // AHBs render the same effect state.
    updateEffect();
    // Same per-frame gate for the PS3 wave background: the first ps3bg::render
    // call this frame advances the wave/glitter/theme time and rebuilds the
    // work texture; the wallpaper passes for any additional displays then
    // composite that same texture (identical state on every panel, single
    // wave build per frame).
    ps3bg::newFrame();
    // Reap async-freed decoders every frame on BOTH themes (renderPs3Xmb reaps only on the XMB path; the
    // DSi carousel home never calls it). Without this, a video-wallpaper teardown on the DSi theme leaves
    // mVidPrevCodecFreed stuck false, so the wallpaper never re-opens and the decoder leaks. Idempotent.
    vidReapDying();
    wpVideoTick();   // video wallpaper: adopt a finished open + loop / re-open, once per frame (both themes)
    vidPreviewTick();   // Video Wallpaper picker: manage the focused-cell live preview (owns the single decoder there)
    videoThumbTick();   // auto-thumbnail: decode one frame per fresh video into the 'v' poster cache (hard-gated on a free HW decoder)
    wallpaperRetryIfNeeded();   // cold boot may run the theme load before /storage is ready; retry until it loads

    // GammaOS: Helper lambda that uploads the DRM rotation matrix to all
    // shader programs. Called at the start of each render pass since the
    // rotation is global state that every program reads.
    auto uploadRotationMatrices = [this]() {
        // Upload-on-change: the rotation matrix is identity in overlay mode and
        // constant per orientation on the DRM home, so skip the 5 program re-binds
        // + uniform uploads when it has not moved since the last upload. The
        // programs retain their last-uploaded value, so this stays correct - a
        // naive unconditional skip would leave the default-zero uRotation and
        // collapse all text/icons (which rely on this global upload) to the origin.
        static float sLastRot[4] = {2.f, 2.f, 2.f, 2.f};   // impossible -> first upload runs
        if (sLastRot[0] == sDrmRotMat[0] && sLastRot[1] == sDrmRotMat[1] &&
            sLastRot[2] == sDrmRotMat[2] && sLastRot[3] == sDrmRotMat[3])
            return;
        sLastRot[0] = sDrmRotMat[0]; sLastRot[1] = sDrmRotMat[1];
        sLastRot[2] = sDrmRotMat[2]; sLastRot[3] = sDrmRotMat[3];
        const GLuint progs[] = {mShaderProgram, mTextProgram, mParticleProgram,
                                mFxProgram, mXmbProgram};
        const GLint  locs[]  = {mLocRotation, mTextLocRotation, mParticleLocRotation,
                                mFxLocRotation, mXmbLocRotation};
        for (int i = 0; i < 5; i++) {
            glUseProgram(progs[i]);
            glUniformMatrix2fv(locs[i], 1, GL_FALSE, sDrmRotMat);
        }
    };

    // GammaOS: Drastic quick-resume dual-screen split.
    //
    // When DrasticRunner is active (smoke test path or production QR
    // for NDS ROMs), both displays are repurposed to show the two DS
    // screens full-size:
    //   primary display  (port 1 on RG DS) -> TOP DS screen
    //   secondary display (port 0)          -> BOTTOM DS screen
    // The wallpaper + XMB are suppressed on both passes. A gradient +
    // "Quick Resuming..." text overlay matches the LibretroRunner QR
    // look, with a fade from desaturated+dark to full color once
    // boot_completed fires.
    DrasticRunner* drastic = DrasticRunner::getInstance();
    const bool drasticActive = drastic && drastic->isInitialized();
    static float sDrasticSaturation = 0.15f;
    static float sDrasticGradient   = 1.0f;
    if (drasticActive) {
        // Idempotent: initSurface is a no-op after the first call.
        drastic->initSurface(mWidth, mHeight, false);
        // Push the DRM rotation matrix so our DS quads come out in
        // panel-native orientation (matching NanoMenu's XMB).
        drastic->setRotationMatrix(sDrmRotMat);
        // Pull fresh pixels ONCE per frame, then reuse the textures
        // across both display passes.
        drastic->updatePixels();

        // Advance the fade. Mirror LibretroRunner's QR transition:
        // creep gently during boot, ramp fast once home_launching or
        // boot_completed fires.
        char val[PROPERTY_VALUE_MAX] = {};
        bool ready = false;
        property_get("sys.gammaos.nano.home_launching", val, "");
        ready = (strcmp(val, "1") == 0);
        if (!ready) {
            property_get("sys.boot_completed", val, "0");
            ready = (strcmp(val, "1") == 0);
        }
        if (ready) {
            sDrasticSaturation = fminf(sDrasticSaturation + 0.01f, 1.0f);
            sDrasticGradient   = fmaxf(sDrasticGradient   - 0.01f, 0.0f);
        } else {
            sDrasticSaturation = fminf(sDrasticSaturation + 0.0004f, 0.35f);
            sDrasticGradient   = fmaxf(sDrasticGradient   - 0.0003f, 0.7f);
        }
    }

    // Small inline "Quick Resuming..." + ROM name overlay used by both
    // drastic passes. Matches LibretroRunner's libretro QR loop.
    auto drawDrasticQrOverlay = [this](int vpW, int vpH,
                                        float saturation, float gradient) {
        (void)gradient;
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        float textScale = fminf((float)vpW / 1080.0f, (float)vpH / 720.0f);
        if (textScale < 0.5f) textScale = 0.5f;
        float loadScale = 2.5f * textScale;
        const char* msg = "Quick Resuming...";
        float msgW = measureText(msg, loadScale);
        float msgX = ((float)vpW - msgW) / 2.0f;
        float msgY = (float)vpH * 0.78f;
        float pulse = 0.7f + 0.3f * sinf((float)elapsedRealtime() * 0.004f);
        float textAlpha = pulse * fmaxf(1.2f - saturation, 0.0f);
        if (textAlpha > 0.05f) {
            if (textAlpha > 1.0f) textAlpha = 1.0f;
            drawText(msg, msgX, msgY, loadScale,
                     1.0f, 1.0f, 1.0f, textAlpha);
        }
        glDisable(GL_BLEND);
    };

    // Resolve the effective DSi screen-stacking for this frame from the requested mode and
    // whether a live secondary panel exists (DRM AHB or SF EGL). Auto (default) stacks both
    // DSi screens onto the one panel of a single-screen device so the top screen is not lost,
    // and keeps the dual-panel split on a two-screen device (RG DS). Computed here, before any
    // NDS branch reads mNdsStack. mNdsTexLoaded gate: mNdsStackMode is valid after first load.
    if (mNdsTheme) {
        // Re-read the stack mode live so the Theme Settings "Dual Screen" chooser applies without a
        // restart (ensureNdsAssets reads it once at load; this refreshes it every frame). Cheap.
        { char sk[PROPERTY_VALUE_MAX] = {}; property_get("persist.gammaos.nano.ndstheme.stack", sk, "auto");
          if (sk[0] == '1' || sk[0] == 't' || (sk[0] == 'o' && sk[1] == 'n')) mNdsStackMode = 1;
          else if (sk[0] == '0' || sk[0] == 'f' || (sk[0] == 'o' && sk[1] == 'f')) mNdsStackMode = 2;
          else mNdsStackMode = 0; }
        // Latch: a secondary panel, once seen, stays seen for the session. glFbo can read 0 on
        // a handoff frame (boot -> carousel) which would otherwise briefly flip a dual device
        // into single-screen stacked mode and flash the wave on the second panel.
        if (sAhbTargetSecondary.glFbo != 0 || !mSecondaryEglSurfaces.empty()) mNdsHadSecondary = true;
        // A real dual-panel device (RG DS) always renders the two DSi screens on their own
        // panels (the dual-panel split), never stacked. On a SINGLE-screen device the default
        // is now carousel-only - just the bottom screen, letterboxed to the panel's aspect ratio
        // (user request) - and the DSi top screen is only stacked above it when the user opts in
        // via persist.gammaos.nano.ndstheme.stack. Mode 1 = force stack, mode 2 = force carousel-
        // only, mode 0 = Auto: stack on a TALL single-screen PORTRAIT panel (e.g. 480x800), where
        // the DS's native top+bottom layout fills the screen far better than a letterboxed single
        // carousel, and carousel-only on 4:3/square/landscape where a stack would squash. A real
        // dual-panel device (RG DS) never stacks - it uses its two physical panels.
        const bool ndsTallSingle = (mHeight >= (int)(mWidth * 1.35f));
        mNdsStack = mNdsHadSecondary ? false
                  : (mNdsStackMode == 1 || (mNdsStackMode == 0 && ndsTallSingle));
    }

    // Dual-screen bottom PSP clock: drive its own reveal (mPspBottomReveal), independent of the F12
    // summon. Runs every frame (during + after cold boot) so it can observe the boot->done edge. On
    // cold boot the clock plays its drop-in transition AFTER the XMB icons float in (mPs3BootActive
    // clears ~as the icons finish, then a short settle hold); on a plain home / app-return (no boot
    // sequence was ever seen) it snaps to fully revealed so it is just statically present.
    if (mPs3BottomClock && !mNdsTheme) {
        mPspBottomFrameCtr++;
        const float dtMs = (mFrameDt > 0.0f) ? mFrameDt * 1000.0f : 16.0f;
        if (mPs3BootActive) {
            mPspBottomReveal = 0.0f; mPspBottomBootPhase = 1; mPspBottomHoldMs = 0.0f;
        } else if (mPspBottomBootPhase == 0) {
            mPspBottomReveal = 1.0f; mPspBottomBootPhase = 4;      // never saw boot: static, no transition
        } else if (mPspBottomBootPhase == 1) {
            mPspBottomBootPhase = 2; mPspBottomHoldMs = 0.0f;      // boot just cleared: begin settle hold
        } else if (mPspBottomBootPhase == 2) {
            mPspBottomHoldMs += dtMs;
            if (mPspBottomHoldMs >= 500.0f) mPspBottomBootPhase = 3;   // icons settled -> start the reveal
        } else if (mPspBottomBootPhase == 3) {
            mPspBottomReveal += dtMs / 5000.0f;                   // open over ~5s, matching the F12 summon
            if (mPspBottomReveal >= 1.0f) { mPspBottomReveal = 1.0f; mPspBottomBootPhase = 4; }
        }
        // Advance the comet trail EVERY frame (even 30fps-cap skip frames) so it stays smooth and
        // correctly timed independent of the render cadence. renderPspClockSecondary just reads it.
        if (mPspBottomReveal > 0.0f) advanceBottomTrail(dtMs);
        // Invalidate the 30fps cache while not fully revealed (boot reveal / disabled) so the first
        // static frame re-snapshots fresh instead of ever blitting a stale cache.
        if (mPspBottomReveal < 0.999f) mPspBottomCacheValid = false;
    }

    // GammaOS: Secondary display pass — wallpaper only, no menu/icons/text.
    // Runs only in DRM direct mode when a secondary AHB was allocated.
    // Renders into sAhbTargetSecondary which drmFlipAll() will blit to every
    // non-primary DRM display. This gives the secondary screen a clean
    // wallpaper view without paying for the menu geometry.
    //
    // The XMB ribbon / procedural effects are cheap fullscreen shaders on
    // Mali-G52, so rendering them twice (once here, once on the primary AHB
    // below) costs well under a millisecond total on 640x480.
    if (sDrmActive && sDrmZeroCopy && sAhbTargetSecondary.glFbo != 0) {
        glBindFramebuffer(GL_FRAMEBUFFER, sAhbTargetSecondary.glFbo);
        glViewport(0, 0, sAhbTargetSecondary.w, sAhbTargetSecondary.h);
        uploadRotationMatrices();
        glClearColor(0.0f, 0.0f, 0.0f, nanoSecondaryClearAlpha(mOverlayMode, mOverlayWallpaper));
        glClear(GL_COLOR_BUFFER_BIT);
        mRenderingPanel = 1;   // this whole pass targets the BOTTOM panel: pick the bottom wallpaper
        if (drasticActive) {
            // Secondary display -> BOTTOM DS screen fullscreen.
            drastic->renderBottomScreen(sDrasticSaturation, sDrasticGradient);
            // drawText inside the overlay lambda uses mWidth/mHeight for
            // pixel->NDC; panel-native AHB dims would mis-project the text
            // on rotated single-display devices (RK3576 1080x1920).
            drawDrasticQrOverlay(mWidth, mHeight,
                                 sDrasticSaturation, sDrasticGradient);
        } else if (mNdsTheme && mPs3BootActive) {
            // DSi cold boot on DRM: the bottom panel must show the boot's white field /
            // notice (renderNdsBootOverlay), NOT the PS3 wave the generic renderEffect()
            // else-branch would draw. The NDS helpers project via mWidth/mHeight, so map
            // them to the secondary AHB dims for this pass, then restore.
            int sw = mWidth, sh = mHeight;
            mWidth = sAhbTargetSecondary.w; mHeight = sAhbTargetSecondary.h;
            renderNdsBootOverlay(/*primary=*/false);
            mWidth = sw; mHeight = sh;
        } else if (mMinimaTheme && mPs3BootActive) {
            // Minima cold boot on the bottom panel: the Minima black-field intro, not the PS3 wave.
            int sw = mWidth, sh = mHeight;
            mWidth = sAhbTargetSecondary.w; mHeight = sAhbTargetSecondary.h;
            renderMinimaBootOverlay(/*primary=*/false);
            mWidth = sw; mHeight = sh;
        } else if (mNdsTheme && !mPs3BootActive) {
            // DSi theme dual-panel: the carousel (bottom DS screen) renders onto the
            // secondary panel. A live secondary always shows DSi content (never the PS3 wave),
            // independent of the primary's stacking mode. The NDS draw helpers project via
            // mWidth/mHeight, so map them to the secondary AHB dims for this pass, then restore.
            int sw = mWidth, sh = mHeight;
            mWidth = sAhbTargetSecondary.w; mHeight = sAhbTargetSecondary.h;
            if (mSetupWizardActive) {
                // First-run setup: do NOT expose the home carousel on the bottom panel yet
                // (the nano home must stay hidden until setup completes). Paint the same DSi
                // setup backdrop (field + dim/blue) the top panel wears, so the bottom reads as
                // "setup in progress"; the net wizard (WiFi/BT) + OSK still draw on this touch
                // panel below.
                renderSetupNdsBackdrop();
            } else {
                renderNdsCarousel(0.0f, 0.0f, (float)mWidth, (float)mHeight);
            }
            // The Internet Connection / Bluetooth setup wizard (mPs3WizActive, renderNetWizard) was
            // only dispatched from renderPs3Xmb (skipped in the DSi theme), so the WiFi/BT flow never
            // drew even though nav worked - draw it here on the BOTTOM touch panel (its password/PIN
            // OSK also lives here). renderNetWizard early-returns when the wizard is not active.
            if (mPs3WizActive) renderNetWizard();
            // Global search (SELECT): the results overlay + its keyboard both live on the BOTTOM
            // touch panel. renderGlobalSearch was only dispatched from renderPs3Xmb (skipped in the
            // DSi theme), so the results never appeared - draw them here on the secondary panel.
            if (mGSearchActive) renderGlobalSearch();
            renderOsk();   // DSi keyboard/OSK on the bottom touch panel (self-gates on mOskActive)
            mWidth = sw; mHeight = sh;
        } else if (mMinimaTheme && !mPs3BootActive) {
            // Minima dual-panel: the interactive list is on the PRIMARY (top) panel; the secondary
            // (bottom) panel wears the Minima backdrop + big clock, not the XMB wave. The setup
            // wizard / global search / OSK still draw on this touch panel.
            int sw = mWidth, sh = mHeight;
            mWidth = sAhbTargetSecondary.w; mHeight = sAhbTargetSecondary.h;
            renderMinimaSecondary(0.0f, 0.0f, (float)mWidth, (float)mHeight);
            if (mPs3WizActive) renderNetWizard();
            if (mGSearchActive) renderGlobalSearch();
            renderOsk();
            mWidth = sw; mHeight = sh;
        } else {
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            const bool bottomClock = mPs3BottomClock && !mPs3BootActive &&
                                     (!mOverlayMode || mOverlayWallpaper);
            // 30fps cap (RG DS load): once the clock is fully revealed, render the heavy passes only
            // every 2nd frame and re-present the cached last frame in between (prop .fps: 30 default,
            // 60 = off). Never cap during the boot reveal so the entrance stays smooth. The secondary
            // ring rotates, so we write the CURRENT slot every frame (fresh or cached) - no stale slot.
            // While the OSK is up on this panel, DISABLE the 30fps cache: the cached-frame blit alternates
            // with fresh frames and re-presents a snapshot taken BEFORE the keyboard, so the OSK backdrop
            // (and its glyphs) flicker between the two. Rendering fresh every frame keeps the scene stable.
            const bool bcCap = bottomClock && mPspBottomReveal >= 0.999f && !mOskActive &&
                property_get_int32("persist.gammaos.nano.ps3xmb.bottomclock.fps", 30) <= 45;
            const bool bcSkip = bcCap && (mPspBottomFrameCtr & 1) && mPspBottomCacheValid &&
                mPspBottomCacheW == sAhbTargetSecondary.w && mPspBottomCacheH == sAhbTargetSecondary.h;
            if (bcSkip) {
                bottomClockCacheBlit();   // re-present the cached wave+clock (skips renderEffect + clock)
            } else {
                renderEffect();
                // PS3 XMB cold boot: match the primary panel's fade-in + frosted-wave blur on the
                // SECONDARY panel too (user: it was showing the unfiltered wave). primary=false skips
                // the logo + text.
                if (!mNdsTheme && mPs3BootActive) {
                    int sw = mWidth, sh = mHeight;
                    mWidth = sAhbTargetSecondary.w; mHeight = sAhbTargetSecondary.h;
                    renderPs3BootOverlay(/*primary=*/false);
                    mWidth = sw; mHeight = sh;
                }
                // Dual-screen XMB: PSP clock on the bottom panel (opt-in), over the wave, not during
                // cold boot. Remap mWidth/mHeight to the secondary AHB dims for the px->NDC projection.
                if (bottomClock) {
                    int sw = mWidth, sh = mHeight;
                    mWidth = sAhbTargetSecondary.w; mHeight = sAhbTargetSecondary.h;
                    renderPspClockSecondary();
                    // Snapshot the composited panel for the next 30fps skip frame (FBO still bound).
                    if (bcCap) bottomClockCacheSnapshot(sAhbTargetSecondary.w, sAhbTargetSecondary.h);
                    mWidth = sw; mHeight = sh;
                }
            }
            // Dual-screen XMB: the search / Wi-Fi password / System Name keyboard belongs on the BOTTOM
            // touch panel too (like the DSi theme above). Remap to the secondary AHB dims so the OSK layout
            // scales to the bottom panel; drawn after the bcSkip/else converge so it sits on top of the
            // wave/clock in both the cached and fresh frames. Self-gates on mOskActive.
            {
                int sw = mWidth, sh = mHeight;
                mWidth = sAhbTargetSecondary.w; mHeight = sAhbTargetSecondary.h;
                renderOsk();
                mWidth = sw; mHeight = sh;
            }
            glDisable(GL_BLEND);
        }
        mRenderingPanel = 0;   // back to the primary/top panel for the main pass below
        maybeNanoScreenshotSecondary();   // debug capture of the DRM bottom (AHB) panel
    }

    // GammaOS: Primary pass — wallpaper + full menu (XMB or normal). When
    // DRM zero-copy is active, binds sAhbTarget (primary AHB). When post-
    // boot, the default SurfaceFlinger-backed FBO is used via the EGL path.
    if (sDrmActive && sDrmZeroCopy) {
        drmBindNextFbo();
    }
    // GammaOS: When GL rotation is active AND we are rendering into the DRM zero-copy
    // AHB, use the AHB (panel-native) dimensions for the viewport, not the logical
    // mWidth/mHeight. The rotation matrix in the vertex shaders maps logical NDC to the
    // panel-native viewport. CRUCIAL: sAhbTarget is sAhbRingPrimary[...], which is only
    // allocated on the DRM zero-copy path; in force-SF overlay mode (drm_active=0) it is
    // 0x0. The hardware-rotate feature sets sDrmGlRotation=true via
    // nanoSetOverlayRenderRotation() even in force-SF mode, so gating the AHB viewport on
    // sDrmGlRotation alone would collapse the viewport to glViewport(0,0,0,0) - every
    // primitive clipped away while glClear still paints (the "wallpaper blanks to blue /
    // overlay icons vanish but scrim stays" rotation bug). Require sDrmZeroCopy so the
    // SF-overlay self-rotate renders into the full mWidth x mHeight surface.
    if (sDrmGlRotation && sDrmZeroCopy) {
        glViewport(0, 0, sAhbTarget.w, sAhbTarget.h);
    } else {
        glViewport(0, 0, mWidth, mHeight);
    }
    uploadRotationMatrices();
    // Overlay: show the FULL PS3 WALLPAPER (opaque) ONLY in launcher/no-app state
    // (mOverlayWallpaper). When there is a LIVE APP behind us (the in-game overlay),
    // keep the translucent scrim at EVERY level - top AND submenus - so the user
    // always sees the dimmed running app, never the wallpaper (user request). The
    // scrim is dark but the live app stays visible (persist.gammaos.nano.overlay.dim
    // default 0.90 = 90% black, 10% app showing through - user request).
    const bool ovWallpaper = mOverlayMode && mOverlayWallpaper;
    if (mOverlayMode && !ovWallpaper) {
        // In-game XMB top level: clear to a BLACK scrim baked into the alpha
        // channel (0,0,0, dim). SurfaceFlinger shows the LIVE app at (1-dim)
        // through the translucent layer; opaque XMB chrome on top reaches alpha 1.
        // Baking the scrim into the clear avoids GL_SRC_ALPHA under-accumulating
        // the framebuffer alpha. Tunable via persist.gammaos.nano.overlay.dim (0.9).
        static float sOvDim = -1.0f;
        if (sOvDim < 0.0f) {
            char d[PROPERTY_VALUE_MAX] = {};
            property_get("persist.gammaos.nano.overlay.dim", d, "0.90");
            sOvDim = atof(d);
            if (sOvDim < 0.0f) sOvDim = 0.0f;
            if (sOvDim > 1.0f) sOvDim = 1.0f;
        }
        // PSP clock (F12) over a live app: the clock draws its OWN backdrop darken
        // (pspClockBackdropBlur, 0.38*reveal) which is meant to be the only dimming
        // behind the clock. The overlay scrim is baked into this clear's alpha, so fade
        // it out on the SAME reveal ramp the clock backdrop fades in on (clockReveal =
        // clamp01((reveal-0.3)/0.7)) - a continuous handoff, no double-dim, no pop (user
        // bug #8). PS3 XMB only (DSi untouched).
        float ovScrim = sOvDim;
        if (mPspClockStandalone) {
            // Summoned over an app with NO overlay open first: keep the scrim at 0 for the
            // WHOLE summon INCLUDING reveal 0 (the first deferred-shown frame). The layer is
            // shown only after this first frame composites, so if it were full black here the
            // user sees a black flash before the clock drops. At 0 the game is fully visible
            // and the clock's own backdrop-blur darken (ramps with clockReveal) fades the game
            // gently into the scrim as the disc drops in (user: no black on summon).
            ovScrim = 0.0f;
        } else if (mPs3Xmb && mPspClockEnabled && mPspClockReveal > 0.0f) {
            float clkReveal = (mPspClockReveal - 0.3f) / 0.7f;
            if (clkReveal < 0.0f) clkReveal = 0.0f;
            if (clkReveal > 1.0f) clkReveal = 1.0f;
            ovScrim = sOvDim * (1.0f - clkReveal);
        }
        glClearColor(0.0f, 0.0f, 0.0f, ovScrim);
    } else {
        // Home XMB, overlay wallpaper/submenu mode, or drastic: OPAQUE clear
        // (alpha 1) so the layer fully covers whatever is behind it.
        glClearColor(drasticActive ? 0.0f : 0.05f,
                     drasticActive ? 0.0f : 0.05f,
                     drasticActive ? 0.0f : 0.10f, 1.0f);
    }
    glClear(GL_COLOR_BUFFER_BIT);

    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    // Primary pass -> TOP DS screen fullscreen + QR overlay when
    // drastic quick-resume is active. Skip the wallpaper + XMB.
    if (drasticActive) {
        drastic->renderTopScreen(sDrasticSaturation, sDrasticGradient);
        // drawText in the overlay uses mWidth/mHeight internally; the
        // shader's uRotation uniform handles the panel rotation. Passing
        // AHB dims here drops the text off-NDC on rotated panels.
        drawDrasticQrOverlay(mWidth, mHeight,
                             sDrasticSaturation, sDrasticGradient);
    } else {

    if (mOverlayMode) {
        if (ovWallpaper) {
            // Launcher / no-app state: render the user's CHOSEN wallpaper exactly
            // like the home XMB instead of a hardcoded wave. renderEffect() honours
            // mCurrentEffect (wave 22, procedural ribbon 21, particles/FX 1-20); in
            // the overlay sDrmActive is false so it composites in logical
            // orientation, which is what the SF layer wants. For any non-wave
            // wallpaper also keep the wave work-texture updated OFFSCREEN so the
            // glass icons (gated on ps3bg::workTex()) still refract - same decouple
            // as the home path below.
            renderEffect();
            if (mPs3Xmb && mCurrentEffect != 22) {
                ps3::layoutComputeNative(mWidth, mHeight);
                ps3bg::render(mWidth, mHeight, mFrameDt, sDrmRotMat,
                              sDrmActive && sDrmGlRotation, /*compositeToScreen=*/false);
            }
        } else {
            // Scrim over a live app: render the wave OFFSCREEN only (never
            // composited) so the glass icons refract it without painting over the
            // dark-scrim view of the running app. persist.gammaos.nano.overlay.wave
            // =0 skips it (glass goes flat) as a perf lever. EXCEPTION: while the
            // Now-Playing screen is open on the XMB Waves visualizer, COMPOSITE the
            // wave morph to the screen so the visualizer shows over the app (the
            // Canyon/Globe already composite directly; this brings Waves in line so
            // the music player's default visualizer is the morph, not the dim app).
            static int sOvWave = -1;
            if (sOvWave < 0) sOvWave = property_get_bool("persist.gammaos.nano.overlay.wave", true) ? 1 : 0;
            bool mpWavesVis = (mMpActive && mMpVis == 0);
            if (sOvWave || mpWavesVis) {
                ps3::layoutComputeNative(mWidth, mHeight);
                ps3bg::render(mWidth, mHeight, mFrameDt, sDrmRotMat,
                              sDrmActive && sDrmGlRotation, /*compositeToScreen=*/mpWavesVis);
            }
        }
        // Standard chrome blend (separate-alpha in overlay so opaque white chrome
        // reaches framebuffer alpha 1 and the live app cannot bleed through it).
        setUiBlend();
    } else {
        // Background effect on primary AHB.
        renderEffect();
        // Decouple the glass icons from the wave WALLPAPER. The glass-icon shader
        // refracts the PS3 wave's offscreen work-texture (ps3bg::workTex), but
        // renderEffect only produces that texture for the wave effect (22). With
        // ANY other wallpaper (the default is even effect 21) the work-texture is
        // never created, and the glass icons - gated on workTex()!=0 - silently
        // vanish. Keep the wave work-texture updated OFFSCREEN every frame (no draw
        // to screen) whenever the PS3 chrome is active but the wave is not the
        // visible wallpaper, so the glass icons always render regardless of the
        // wallpaper the user picked.
        // Skip the offscreen wave work-texture (only feeds glass-icon refraction,
        // which the Now-Playing screen does not use) while a full-screen visualizer
        // (Canyon or Globe) fully covers.
        bool visCovers = mMpActive && ((mMpVis == 1 && mMpCanyonAlpha >= 0.999f) ||
                                       (mMpVis == 2 && mMpGlobeAlpha >= 0.999f));
        // The full-screen video player paints an opaque black backdrop over the whole
        // screen for the entire time it is up (enter fade, playback and leave fade), so
        // the home wallpaper/wave behind it is never visible. Skip the wave work-texture
        // render whenever the video player covers (it does not use glass-icon refraction).
        if (mVidActive || mVidEnterT > 0.001f) visCovers = true;
        // The music player's XMB Waves visualizer (vis 0) IS the wave morph, so it
        // must show the wave regardless of the home wallpaper. When it is active and
        // the wallpaper is not already the wave, composite the (morphing) wave to the
        // screen over the rendered wallpaper; otherwise keep the wave OFFSCREEN only
        // (it just feeds glass-icon refraction).
        bool mpWavesVis = (mMpActive && mMpVis == 0);
        if (mPs3Xmb && mCurrentEffect != 22 && !visCovers) {
            ps3::layoutComputeNative(mWidth, mHeight);
            ps3bg::render(mWidth, mHeight, mFrameDt, sDrmRotMat,
                          sDrmActive && sDrmGlRotation, /*compositeToScreen=*/mpWavesVis);
        }
    }

    // Dual-screen (RG DS): the nano OSK belongs on the BOTTOM touch panel, not the untouchable TOP one, in
    // EVERY state that draws it (setup wizard, over-app, home). Compute the routing ONCE and gate all three
    // primary (top) draws with it; the secondary (bottom) passes draw the OSK when this is true, so it is
    // never drawn on both panels. mPs3Xmb covers the XMB and DSi themes; a single-panel device has no
    // secondary target so this stays false and the OSK still draws on the only (primary) panel.
    const bool oskOnSecondary = mPs3Xmb &&
        (sAhbTargetSecondary.glFbo != 0 || !mSecondaryEglSurfaces.empty());

    if (mSetupWizardActive && !mPs3BootActive) {
        // During a PS3 cold boot the wizard is held back so the full intro
        // (anim -> epilepsy warning) plays first; it cuts in once the intro ends.
        renderSetupWizard();
        if (!oskOnSecondary) renderOsk();   // dual-screen: keyboard on the bottom (secondary pass)
    } else if (mPs3Xmb && mOskOverApp) {
        // OSK-only over a live app (an app requested text entry; see overlayOskPoll).
        // The app shows through the translucent overlay layer - dim it with a scrim
        // and draw just the keyboard, no Quick Menu behind it. The scrim stays on the
        // top (over the app); the keyboard itself moves to the bottom on dual-screen.
        setUiBlend();
        drawQuad(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f, 0.0f, 0.0f, 0.55f);
        if (!oskOnSecondary) renderOsk();   // dual-screen: keyboard on the bottom (secondary pass)
    } else if (mPs3Xmb) {
        // PS3 XMB layout (NanoMenuPS3Menu.cpp). renderPs3Xmb() draws the Wi-Fi /
        // Bluetooth sub-screens itself when mMenuState is MENU_WIFI / MENU_BT, and
        // drives + renders the cold-boot intro when mPs3BootActive.
        // The DSi System Menu theme (persist.gammaos.nano.ndstheme) swaps the home
        // render for the DSi launcher carousel. It still reuses the PS3 XMB boot intro
        // for now (the DSi boot animation is prop-gated in separately), and the whole
        // overlay / OSK / launch-fade tail below is shared.
        // DSi home background ambiance: loop while the carousel home is up (not during boot,
        // a media player, or the in-game scrim overlay); stopped otherwise.
        // Also silence the home BGM the instant a launch begins (the home launch fade, or an
        // overlay launch) so it does not bleed into the game/app (user: stop the bgm when we
        // launch into overlay mode). The in-game overlay case is already covered by the
        // mOverlayWallpaper clause above.
        // app_launched is the LIVE "a nano app is running" signal; mOverlayWallpaper is only
        // re-evaluated on overlay show, so after a launch FROM the overlay it stays stale-true
        // and the ambiance would keep playing over the game. Gate on app_launched directly.
        ndsAmbianceTick(mNdsTheme && !mPs3BootActive && !ndsPlayerActive()
                        && !(mOverlayMode && !mOverlayWallpaper)
                        && mLaunchFadeStart == 0 && !mOverlayLaunchPending
                        && !property_get_bool("sys.gammaos.nano.app_launched", false));
        // PS3 XMB theme: hold card0 open through the pre-boot-complete window so nav SFX are audible
        // in the early menu (self-gates to !mNdsTheme; the DSi hold is ndsAmbianceTick above). Runs
        // every frame including the boot intro, so nano owns card0 before the audio HAL can grab it.
        ps3EarlyAudioTick();
        // GammaOS: the PSP Go slide clock (drawPspClock) is normally rendered only by the XMB
        // path. When it is summoned (slide-close, mPspClockOn / the framework's over-app
        // pspclock_summon -> mPspClockStandalone) in a DSi/Minima theme, route the primary render
        // through renderPs3Xmb for the duration of the summon so the clock draws over the theme.
        const bool pspClockActive = !mPs3BootActive &&
            (mPspClockStandalone || mPspClockOn || mPspClockReveal > 0.0f);
        mPspClockThemeBackdrop = false;   // reset each frame; drawPspClockThemeBackdrop() re-sets it below
        if (mNdsTheme && mPs3BootActive) {
            // DSi cold boot: drive the shared boot clock (advances mPs3BootElapsedMs and
            // clears mPs3BootActive at the end -> the carousel intro cascade takes over the
            // next frame) and render the DSi-styled white boot instead of the PS3 intro.
            ps3BootUpdate(mFrameDt);
            renderNdsBootOverlay(/*primary=*/true);
        } else if (mMinimaTheme && mPs3BootActive) {
            // Minima cold boot: the SAME shared boot clock/phase machine, but the Minima black-field
            // intro + jingle, handing straight into the Minima menu - the PS3/XMB intro is never shown.
            ps3BootUpdate(mFrameDt);
            renderMinimaBootOverlay(/*primary=*/true);
        } else if ((mNdsTheme || mMinimaTheme) && pspClockActive) {
            // GammaOS: PSP Go slide clock summoned (slide-close) in a non-XMB theme. Render via the
            // XMB path for the summon's duration so drawPspClock (inside renderPs3Xmb, which also
            // fills the wave FBO/workTex + rotation the clock's glass samples) runs; the full-screen
            // clock covers the DSi/Minima menu. Reverts to the theme render the frame the clock
            // finishes retracting (pspClockActive -> false at reveal 0).
            // Gate on the visual theme (mNdsTheme / mMinimaTheme), NOT on !mPs3Xmb: DSi and Minima
            // reuse the XMB home infrastructure, so mPs3Xmb is forced true for them in the theme
            // setup. The old "!mPs3Xmb" test was therefore always false in exactly these themes,
            // leaving this branch dead - the slide-shut summon fell through to the DSi/Minima home
            // render and the menu appeared over the app instead of the clock.
            // Backdrop: renderPs3Xmb draws the XMB wave, which is wrong for the DSi/Minima HOME (Minima
            // is black by default; both honour a custom photo/video wallpaper). Paint the THEME's own
            // home backdrop to screen AND into the clock's work texture first, so the surround and the
            // glass disc show it instead of the wave. Over a live app this no-ops (the captured app is
            // used) - see drawPspClockThemeBackdrop.
            drawPspClockThemeBackdrop();
            renderPs3Xmb();
        } else if (mNdsTheme && !mPs3BootActive && ndsPlayerActive()) {
            // A media player is up: show the existing full-screen XMB video / music / photo
            // player on the primary (top) screen (user: "show the XMB ones when actually
            // playing"). The carousel keeps rendering on the secondary (bottom) panel.
            renderPs3Xmb();
        } else if (mNdsTheme && !mPs3BootActive) {
            // On a true dual-panel device (RG DS) the DSi TOP screen fills the primary
            // panel and the carousel renders onto the secondary panel via the secondary
            // passes (DRM AHB above / SF EGL below). Detect a live secondary render target.
            // Run the scraper-art lifecycle here (renderPs3Xmb is skipped in this theme):
            // caches the boxart toggle, frees on leaving Game, and uploads async cover /
            // fanart decodes so the DSi tiles + top-screen preview actually get their art.
            scraperArtTick();
            appInfoTick();   // App Information submenu: renderPs3Xmb (which normally ticks it) is
                             // skipped in this theme, so drive the async framework fill here too -
                             // otherwise an app's Information hangs forever on "Loading...".
            fbTick();        // File Explorer / folder browser: adopt a directory listing the worker
                             // finished (same reason as appInfoTick - without this the browser is
                             // stuck on "Loading..." forever in the DSi theme).
            feTick();        // File Explorer: reap a finished copy / move / delete op + its result dialog.
            nsTick();        // Network Shares: follow a mount coming up / going away while the list is open.
            ensureNdsAssets();
            bool ndsDual = !mNdsStack &&
                (sAhbTargetSecondary.glFbo != 0 || !mSecondaryEglSurfaces.empty());
            if (ndsDual) renderNdsTop(0.0f, 0.0f, (float)mWidth, (float)mHeight);
            else         renderNds();
        } else if (mMinimaTheme && !mPs3BootActive && ndsPlayerActive()) {
            // Minima: media players show the existing full-screen XMB video / music / photo player.
            renderPs3Xmb();
        } else if (mMinimaTheme && !mPs3BootActive) {
            // Minima home + its own modals. The option menu / list+slider choosers render as a Minima
            // side panel; confirm/message dialogs as a Minima dialog. Other modals (tz/lang pickers,
            // net wizard, global search, game info / boxart page, photo grid) still use the XMB chrome
            // for now. renderPs3Xmb is skipped for the home, so drive its lifecycle ticks here.
            const bool minSidePanel = mPs3OptActive || mPs3OptClosing ||
                                      ((mPs3DlgActive || mPs3DlgClosing) && ndsDlgIsSidePanel());
            // A game Information dialog carries a scraped cover + metadata + synopsis that the
            // generic confirm dialog cannot show (it drew almost empty); route it to the dedicated
            // Minima info page. mPs3DlgGameInfo is set for both the rich rom-info page and its
            // plain-facts fallback, so it covers every Information dialog.
            const bool minInfoPage  = !minSidePanel && (mPs3DlgActive || mPs3DlgClosing) &&
                                      (mPs3DlgGameInfo || mPs3DlgRomInfo);
            const bool minDialog    = !minSidePanel && !minInfoPage && (mPs3DlgActive || mPs3DlgClosing);
            const bool minSearch    = mGSearchActive;   // SELECT global search: Minima results over the home
            // The OSK renders on the bottom touch panel, so it must NOT force the top to XMB. The
            // brightness/volume slider HUD and the boxart-scraper progress modal are now themed for
            // Minima and render OVER the Minima home, so they must not fall into the XMB fallback
            // either (user 2026-07-30). Remaining unskinned modals (tz/lang pickers, net wizard,
            // photo grid) still use the XMB chrome for now.
            const bool minOtherModal = ndsInModal() && !minSidePanel && !minInfoPage && !minDialog
                                       && !minSearch && !mOskActive
                                       && !mPs3BrightSlider && !mScrapeProgActive;
            if (minOtherModal) {
                renderPs3Xmb();
            } else {
                scraperArtTick();
                appInfoTick();
                fbTick();    // File Explorer / folder browser: adopt a finished directory listing
                             // (renderPs3Xmb, which normally ticks this, is skipped for the Minima
                             // home - without it the browser is stuck on "Loading..." forever).
                feTick();    // File Explorer: reap a finished copy / move / delete op + its result dialog.
                nsTick();    // Network Shares: follow a mount coming up / going away while the list is open.
                renderMinima();
                if (minSidePanel)    renderMinimaSidePanel(0.0f, 0.0f, (float)mWidth, (float)mHeight);
                else if (minInfoPage) renderMinimaInfoPage(0.0f, 0.0f, (float)mWidth, (float)mHeight);
                else if (minDialog)  renderMinimaDialog(0.0f, 0.0f, (float)mWidth, (float)mHeight);
                else if (minSearch)  renderGlobalSearch();   // Minima-styled results overlay (see renderGlobalSearch)
            }
        } else {
            renderPs3Xmb();
        }
        // Live controller Test / Calibration screens draw over the menu when open.
        if (mGpTestActive) renderGamepadTest();
        else if (mGpCalibActive) renderGamepadCalib();
        else if (mGpCaptureActive) renderGamepadCapture();
        renderScrapeProgress();   // boxart-scraper progress / result modal, over the XMB
        // Dual-screen: the home OSK (search / Wi-Fi password / System Name) draws on the BOTTOM touch
        // panel via the secondary pass (oskOnSecondary hoisted above the setup-wizard branch); a
        // single-panel device draws it here on the only panel.
        if (!oskOnSecondary) renderOsk();
        // Overlay launch transition: fade the whole XMB to black over ~300ms so the
        // app's own cold start is covered by a clean fade-out instead of a frozen,
        // still-navigable menu. The black holds (the input-freeze in pollInput keeps
        // it inert) until the overlay dismisses onto the resumed app (overlayPoll).
        // The DSi launch washes both screens to WHITE (MASTER_BRIGHT), not black.
        const float lf = mNdsTheme ? 1.0f : 0.0f;
        if (mOverlayMode && mOverlayLaunchPending) {
            int64_t el = uptimeMillis() - mOverlayLaunchStartMs;
            float fa = (el <= 0) ? 0.0f : (float)el / 300.0f;
            if (fa < 0.0f) fa = 0.0f;
            if (fa > 1.0f) fa = 1.0f;
            setUiBlend();
            drawQuad(0.0f, 0.0f, (float)mWidth, (float)mHeight, lf, lf, lf, fa);
        }
        // Home (non-overlay) launch fade-out over ~260ms after the launching select is
        // released (mLaunchFadeStart), then nano hands off to the app (gated in pollInput).
        // The NDS theme is excluded: renderNdsTop/renderNdsCarousel each draw their own
        // frame-accurate per-screen white wash (bottom 44f, top 41f), so a second full-
        // viewport ramp here would double it and desync the two panels.
        if (!mOverlayMode && mLaunchFadeStart > 0 && !mNdsTheme) {
            int64_t el = (int64_t)uptimeMillis() - mLaunchFadeStart;
            float fa = (el <= 0) ? 0.0f : (float)el / 260.0f;
            if (fa < 0.0f) fa = 0.0f;
            if (fa > 1.0f) fa = 1.0f;
            setUiBlend();
            drawQuad(0.0f, 0.0f, (float)mWidth, (float)mHeight, lf, lf, lf, fa);
        }
    } else if (mXmbMode) {
        renderXmb();
        if (mMenuState == MENU_WIFI) renderWifiScreen();
        else if (mMenuState == MENU_BT) renderBtScreen();
        else if (mMenuState == MENU_SETTINGS) renderSettingsTree();
        // OSK is drawn last so the password keyboard sits on top of the
        // Wi-Fi / BT overlays (otherwise renderWifiScreen overdraws it).
        renderOsk();
    } else {

    // Responsive scaling: fit to both width and height so the menu
    // looks correct on any aspect ratio (4:3, 16:9, 16:10, 3:2, etc.)
    float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
    if (sf < 0.5f) sf = 0.5f;

    // Font scales
    float titleScale = 4.0f * sf;
    float subScale   = 2.0f * sf;
    float footScale  = 1.5f * sf;

    // Rebuild display items only when state changes (avoids per-frame heap allocs)
    if (mDisplayDirty) rebuildDisplayItems();

    // Storage readiness is polled by the outer loop (threadLoop) every ~0.5s.
    // No per-frame access() here — that syscall was costing ~2us at 60fps which
    // adds up on Cortex-A55 and is visible in perf traces during boot.

    float menuScale = 3.0f * sf;
    int currentSelected = (mMenuState == MENU_APPS) ? mAppSelectedIndex
                        : (mMenuState == MENU_RECENT) ? mRecentSelectedIndex : mSelectedIndex;
    int numItems = (int)mDisplayItems.size();

    // Element heights
    float titleH = FONT_CHAR_H * titleScale;
    float subH   = FONT_CHAR_H * subScale;
    float itemH  = FONT_CHAR_H * menuScale;
    float footH  = FONT_CHAR_H * footScale;

    // Gaps
    float gap1 = 10.0f * sf;   // title -> subtitle
    float gap2 = 20.0f * sf;   // subtitle -> separator
    float sepH = 2.0f * sf;
    float gap3 = 30.0f * sf;   // separator -> menu
    float itemSpacing = 12.0f * sf;

    // Place heading using the main menu's item count (7) so the title,
    // subtitle, separator, and footer stay at identical positions regardless
    // of which menu state is active.
    int layoutItems = 8; // main menu item count — used as the reference layout
    float menuContentH = titleH + gap1 + subH + gap2 + sepH + gap3
                        + layoutItems * itemH + (layoutItems - 1) * itemSpacing;
    float startY = (mHeight - menuContentH) / 6.0f;
    if (startY < 10.0f) startY = 10.0f;

    // Title
    const char* titleTr = trDyn(mTitle.c_str());
    float titleW = measureText(titleTr, titleScale);
    float titleX = (mWidth - titleW) / 2.0f;
    float titleY = startY;
    drawText(titleTr, titleX, titleY, titleScale,
             0.0f, 0.85f, 1.0f, 1.0f);

    // Subtitle
    float subW = measureText(mSubtitle.c_str(), subScale);
    float subX = (mWidth - subW) / 2.0f;
    float subY = titleY + titleH + gap1;
    drawText(mSubtitle.c_str(), subX, subY, subScale,
             0.5f, 0.5f, 0.6f, 1.0f);

    // Separator
    float sepY = subY + subH + gap2;
    drawQuad(mWidth * 0.1f, sepY, mWidth * 0.8f, sepH, 0.3f, 0.3f, 0.4f, 1.0f);

    // Menu items
    float menuStartY = sepY + sepH + gap3;
    float menuX = mWidth * 0.15f;

    // Available text width for menu items (from menuX to 85% of screen)
    float maxTextW = mWidth * 0.85f - menuX;
    float charW = FONT_CHAR_W * menuScale;

    // Calculate how many items fit on screen (between menu start and footer)
    float footY = mHeight - footH - startY;
    float availableH = footY - menuStartY - 10.0f * sf;
    int maxVisibleItems = (int)(availableH / (itemH + itemSpacing));
    if (maxVisibleItems < 1) maxVisibleItems = 1;

    // Vertical scrolling for submenus with more items than fit
    if (mMenuState != MENU_MAIN && numItems > maxVisibleItems) {
        // Ensure selected item is visible
        if (currentSelected < mMenuScrollTop) {
            mMenuScrollTop = currentSelected;
        } else if (currentSelected >= mMenuScrollTop + maxVisibleItems) {
            mMenuScrollTop = currentSelected - maxVisibleItems + 1;
        }
        // Clamp
        if (mMenuScrollTop > numItems - maxVisibleItems) {
            mMenuScrollTop = numItems - maxVisibleItems;
        }
        if (mMenuScrollTop < 0) mMenuScrollTop = 0;
    } else {
        mMenuScrollTop = 0;
    }

    int renderEnd = (mMenuState != MENU_MAIN && numItems > maxVisibleItems)
                  ? mMenuScrollTop + maxVisibleItems : numItems;
    if (renderEnd > numItems) renderEnd = numItems;

    for (int i = mMenuScrollTop; i < renderEnd; i++) {
        float itemY = menuStartY + (i - mMenuScrollTop) * (itemH + itemSpacing);
        bool selected = (i == currentSelected);

        // Grey out "Recently Played" and "Applications" in main menu when storage isn't ready
        bool greyed = (mMenuState == MENU_MAIN && !mStorageReady
                       && (mDisplayItems[i] == "Recently Played"
                           || mDisplayItems[i] == "Applications"));

        if (selected && !greyed) {
            drawQuad(mWidth * 0.10f, itemY - 4.0f * sf,
                     mWidth * 0.80f, itemH + 8.0f * sf,
                     0.0f, 0.35f, 0.6f, 0.8f);
        }
        const char* prefix = (selected && !greyed) ? "> " : "  ";
        float r, g, b;
        if (greyed) {
            r = 0.35f; g = 0.35f; b = 0.4f; // dimmed
        } else if (selected) {
            r = 1.0f; g = 1.0f; b = 1.0f;
        } else {
            r = 0.7f; g = 0.7f; b = 0.75f;
        }

        // Draw prefix at fixed position
        float prefixW = 2 * charW; // "> " or "  " is always 2 chars
        drawText(prefix, menuX, itemY, menuScale, r, g, b, 1.0f);

        // Content area: from after prefix to end of blue selection bar
        float contentLeft = menuX + prefixW;
        float contentRight = mWidth * 0.90f; // right edge of selection bar
        float contentW = contentRight - contentLeft;
        float textW = measureText(mDisplayItems[i].c_str(), menuScale);

        // Horizontal scroll for selected items that overflow (Recently Played)
        float drawX = contentLeft;
        bool scrolling = false;
        if (selected && !greyed
            && (mMenuState == MENU_RECENT || mMenuState == MENU_APPS)
            && textW > contentW
            && ((mMenuState == MENU_RECENT && i < (int)mRecentEntries.size())
                || (mMenuState == MENU_APPS && i < (int)mAppEntries.size()))) {
            scrolling = true;
            // Reset scroll when selection changes
            if (mLastScrolledIdx != i) {
                mLastScrolledIdx = i;
                mScrollOffset = 0.0f;
                mScrollDir = 1;
                mScrollPause = 60; // pause ~1s at start before scrolling
            }
            float overflow = textW - contentW;
            if (mScrollPause > 0) {
                mScrollPause--;
            } else {
                mScrollOffset += mScrollDir * 1.5f * sf; // scroll speed
                if (mScrollOffset >= overflow) {
                    mScrollOffset = overflow;
                    mScrollDir = -1;
                    mScrollPause = 60;
                } else if (mScrollOffset <= 0.0f) {
                    mScrollOffset = 0.0f;
                    mScrollDir = 1;
                    mScrollPause = 60;
                }
            }
            drawX = contentLeft - mScrollOffset;
        }

        // Scissor clip: all game entries in MENU_RECENT clip at the bar's right edge.
        // Selected items clip at both left and right (for scroll), unselected only right.
        bool needsClip = scrolling
            || ((mMenuState == MENU_RECENT && i < (int)mRecentEntries.size()
                 && textW > contentW)
                || (mMenuState == MENU_APPS && i < (int)mAppEntries.size()
                    && textW > contentW));
        if (needsClip) {
            glEnable(GL_SCISSOR_TEST);
            // Scissor is applied in FBO pixel coords AFTER the vertex
            // shader's rotation, so the logical-landscape band must be mapped
            // through the COMPOSED sDrmRotMat (rotation AND flips - the old
            // rotation-only switch here mirrored the band on flipped panels).
            // RK3576 (1080x1920 portrait, 270 install + drm_flip_v=1) is the
            // device that surfaced both halves of this.
            scissorLogicalRect(contentLeft, 0.0f, contentW, (float)mHeight);
        }
        drawText(mDisplayItems[i].c_str(), drawX, itemY, menuScale,
                 r, g, b, 1.0f);
        if (needsClip) {
            glDisable(GL_SCISSOR_TEST);
        }
    }

    // Footer (footY already computed above for scroll calculations)
    float footW = measureText(mFooter.c_str(), footScale);
    float footX = (mWidth - footW) / 2.0f;
    drawText(mFooter.c_str(), footX, footY, footScale, 0.4f, 0.4f, 0.5f, 1.0f);

    } // end !mXmbMode text menu

    // Brightness bar overlay
    renderBrightnessBar();
    renderVolumeBar();
    // GammaOS Nano: launch-busy toast (centred). Drawn before the top
    // HUD so the dim black backplate doesn't fight with battery/QR
    // icons, but after volume/brightness so those still appear on top.
    renderLaunchBusyToast();

    // Top-bar HUD (battery / network / quick resume) is suppressed while
    // the user is inside a Settings sub-screen so the full row is available
    // for the toggle + device list without overlap or duplication.
    bool inSettingsModal = (mMenuState == MENU_WIFI || mMenuState == MENU_BT
                            || mMenuState == MENU_SETTINGS
                            || mSetupWizardActive);

    // Battery + Network indicators (XMB only; mirrors the Quick Resume HUD
    // on the opposite side). Text-menu mode keeps its minimal top-bar free
    // so the classic boot layout isn't visually disturbed.
    if ((mXmbMode || mPs3Xmb) && !inSettingsModal) {
        pollBattery();
        // In the PS3 XMB layout the battery percentage and Wi-Fi/Bluetooth icons
        // live inside the clock bar (drawPs3Clock), so skip the legacy top-left
        // battery + network HUD entirely there (it would double up the battery).
        if (!mPs3Xmb) {
            float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
            if (sf < 0.5f) sf = 0.5f;
            float pad = 15.0f * sf;
            float textScale = 1.5f * sf;
            float rowY = pad;
            // Match the height that renderBatteryIndicator uses internally
            // so network icons sit on the same baseline.
            float rowH = fmaxf(18.0f * sf, FONT_CHAR_H * textScale);
            float batteryRightX = renderBatteryIndicator();
            renderNetworkIndicators(batteryRightX, rowY, rowH, sf, textScale);
        }
    }

    // Quick Resume indicator (top-right corner). Hidden in the PS3 XMB layout
    // where the clock occupies that corner.
    if (!inSettingsModal && !mPs3Xmb) {
        float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
        if (sf < 0.5f) sf = 0.5f;
        float qrScale = 1.5f * sf;
        float dotSize = 10.0f * sf;
        float pad = 15.0f * sf;
        const char* qrLabel = trDyn("Quick Resume");
        float qrLabelW = measureText(qrLabel, qrScale);
        float qrX = mWidth - qrLabelW - pad;
        float dotX = qrX + qrLabelW / 2.0f - dotSize / 2.0f;
        float dotY = pad;
        float labelY = dotY + dotSize + 5.0f * sf;
        if (mQuickResumeEnabled) {
            drawQuad(dotX, dotY, dotSize, dotSize, 0.0f, 0.85f, 0.0f, 1.0f);
            drawText(qrLabel, qrX, labelY, qrScale,
                     0.4f, 0.7f, 0.4f, 0.8f);
        } else {
            drawQuad(dotX, dotY, dotSize, dotSize, 0.85f, 0.0f, 0.0f, 1.0f);
            drawText(qrLabel, qrX, labelY, qrScale,
                     0.5f, 0.35f, 0.35f, 0.6f);
        }
    }
    } // close drasticActive-else wrapper

    // Mouse cursor overlay: drawn last (over the XMB, dialogs, OSK and scrims) while blend is
    // still enabled and before the screenshot capture, so it sits on top of everything and is
    // captured in the sys.gammaos.nano.shot PPM. Self-hides after a few idle seconds.
    drawPointerCursor();

    glDisable(GL_BLEND);

    // Debug frame capture (no-op unless sys.gammaos.nano.shot is set).
    maybeNanoScreenshot();

    // GammaOS: DRM direct rendering path.
    // - Zero-copy: GPU rendered straight into the scanout FBO; just page flip.
    // - Fallback: glReadPixels → CPU copy to dumb buffer → page flip.
    // Either way, skip eglSwapBuffers (it blocks when HWC doesn't consume buffers).
    if (sDrmActive) {
        if (sDrmZeroCopy) {
            // XMB ring path: same triple-buffer mechanism used by drastic QR.
            // Decouples glFinish (GPU wait, ~11 ms on RK3576 1080x1920) from
            // the CPU-side blit+flip (~10 ms) by presenting a slot whose GPU
            // work is ~2 iterations old -- its fence is already signaled so
            // the per-slot AHB_lock returns fast, and we don't block the
            // render thread on the current slot's in-flight GPU work. Gated
            // on persist.gammaos.nano.triple_buffer (same prop as QR). Resolved
            // once per process since the prop + slot availability don't change
            // at runtime.
            static int sXmbRingEnabled = -1;
            if (sXmbRingEnabled < 0) {
                char prop[PROPERTY_VALUE_MAX] = {};
                property_get("persist.gammaos.nano.triple_buffer", prop, "1");
                const bool flagOn = (prop[0] == '1');
                bool slotsOk = (sEglCreateSyncKHR != nullptr) &&
                               (sRingEglDpy != EGL_NO_DISPLAY);
                for (int i = 0; i < AHB_RING_DEPTH && slotsOk; i++) {
                    if (sAhbRingPrimary[i].glFbo == 0) slotsOk = false;
                }
                sXmbRingEnabled = (flagOn && slotsOk) ? 1 : 0;
                if (sXmbRingEnabled) {
                    sRingRenderIdx = 0;
                    sRingPresentIdx = 0;
                    sRingPrimedCount = 0;
                }
                ALOGW("NanoMenu XMB ring %s (flag=%d slotsOk=%d)",
                      sXmbRingEnabled ? "ENABLED" : "disabled",
                      flagOn ? 1 : 0, slotsOk ? 1 : 0);
            }

            if (sXmbRingEnabled) {
                const int renderIdxNow = sRingRenderIdx;
                // Unbind the AHB FBO and insert a native fence. This implicit
                // flush kicks the GPU without waiting -- the fence will signal
                // when all commands issued for this slot complete.
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                if (sAhbRingSyncPrimary[renderIdxNow] != EGL_NO_SYNC_KHR
                        && sEglDestroySyncKHR) {
                    sEglDestroySyncKHR(sRingEglDpy,
                            sAhbRingSyncPrimary[renderIdxNow]);
                }
                sAhbRingSyncPrimary[renderIdxNow] = sEglCreateSyncKHR(
                        sRingEglDpy, EGL_SYNC_NATIVE_FENCE_ANDROID, nullptr);
                if (sAhbRingSyncPrimary[renderIdxNow] == EGL_NO_SYNC_KHR) {
                    // Fence creation failed -- explicit flush so downstream
                    // drmFlipRingSlot's glFinish fallback observes our work.
                    glFlush();
                }
                // Advance render cursor BEFORE present so the macro
                // sAhbTarget resolves to the next slot on the next render()
                // call. Present uses presentIdx which lags by 2 (ring depth
                // minus 1), reading an older slot whose fence is signaled.
                sRingRenderIdx = (renderIdxNow + 1) % AHB_RING_DEPTH;
                // Threshold = 2 (not depth-1) keeps present-lag at 2 regardless of
// ring depth. With depth N and lag L, slot M is rendered at iter M
// and re-rendered at iter M+N, but display still owns it through
// iter M+L+1. Race-free requires L <= N-2. With depth 4 and L=2
// (this threshold), we have 1 slot of headroom = no GL/scanout
// races on the DRM PRIME path.
if (sRingPrimedCount >= 2) {
                    const int presentIdxNow = sRingPresentIdx;
                    drmFlipRingSlot(presentIdxNow);
                    sRingPresentIdx =
                            (presentIdxNow + 1) % AHB_RING_DEPTH;
                } else {
                    sRingPrimedCount++;
                }
            } else {
                drmFlipAll(); // includes glFinish + CPU blit + page flip
            }
        } else {
            drmPushFrame(mWidth, mHeight);
        }

        // GammaOS: Vsync lock for DRM-direct XMB rendering.
        //
        // Without an explicit DRM_IOCTL_WAIT_VBLANK here the loop runs as
        // fast as drmFlipAll can complete, which on Mali G52 is ~8-11 ms
        // per frame. Every second call to drmModePageFlip can return
        // -EBUSY (previous flip pending), which falls through to the
        // blocking drmModeSetCrtc in drmFlipAll's fallback path and
        // produces irregular pacing. Baseline measurement (2026-04-13)
        // showed XMB at 46-48 fps in DRM-direct mode, with frame times
        // oscillating 8-35 ms.
        //
        // Relative-vblank sequence=1 blocks until the panel has
        // completed one vblank, matching the QR loop's pacing at
        // line ~6822 of this file and giving us a stable 60 fps lock
        // as long as the per-frame work fits inside 16.67 ms.
        // Render-loop sync. Two paths:
        //
        // - Working vblank (default): DRM_IOCTL_WAIT_VBLANK with relative
        //   sequence=1 blocks until the next panel vblank. Cheap (one
        //   ioctl) and ignores cross-CRTC timing on dual-display setups.
        //
        // - Broken vblank (RK3576 DSI command-mode): the kernel's vblank
        //   queue never wakes -> WAIT_VBLANK hits the 3s timeout. The
        //   first slow wait flips sDrmVblankBroken; subsequent iterations
        //   skip the ioctl and pace via drmDrainPageFlipEvents() instead
        //   (which reads the per-flip events those panels DO generate).
        //
        // The whole gate can be disabled at runtime with
        // persist.gammaos.nano.vsync=0 (default 1) -- diagnostic, lets us
        // measure vsync overhead vs other sources of jitter. Read once.
        if (sVsyncEnabled < 0) {
            char vp[PROPERTY_VALUE_MAX] = {};
            property_get("persist.gammaos.nano.vsync", vp, "1");
            sVsyncEnabled = (vp[0] == '0') ? 0 : 1;
            ALOGW("NanoMenu vsync gate %s",
                  sVsyncEnabled ? "ENABLED" : "DISABLED (no WAIT_VBLANK / no event drain)");
        }
        if (sVsyncEnabled) {
            // WAIT_VBLANK is only safe on single-CRTC setups with working
            // vblank. Multi-CRTC setups (RG DS dual DSI) pace via
            // drmDrainPageFlipEvents instead so the sync gate waits for
            // flips on BOTH displays to complete. Broken-vblank panels
            // (RK3576 DSI command-mode) also skip WAIT_VBLANK.
            if (sDrmFd >= 0 && !sDrmDisplays.empty() &&
                !sDrmVblankBroken && sDrmDisplays.size() <= 1) {
                int64_t vblT0 = systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL;
                union drm_wait_vblank vbl = {};
                vbl.request.type = (enum drm_vblank_seq_type)(
                        _DRM_VBLANK_RELATIVE
                        | ((sDrmPrimaryIdx & 0x1f)
                           << _DRM_VBLANK_HIGH_CRTC_SHIFT));
                vbl.request.sequence = 1;
                ioctl(sDrmFd, DRM_IOCTL_WAIT_VBLANK, &vbl);
                int64_t vblElapsed =
                        systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL - vblT0;
                if (vblElapsed > 100000) {
                    sDrmVblankBroken = true;
                    ALOGW("NanoMenu: DRM_IOCTL_WAIT_VBLANK took %lld us -- "
                          "switching to page-flip-event pacing",
                          (long long)vblElapsed);
                }
            }
            // Drain pending events. No-op when no events were requested
            // (single-display + working vblank). Sync gate for broken-vblank
            // and multi-display setups.
            drmDrainPageFlipEvents();
        } else {
            // Vsync disabled: still cap the render rate at 60 fps so we
            // can compare CPU/pacing fairly. Tearing is expected.
            drmPaceWithoutVsync();
        }

        // GammaOS: drmStop() is no longer called from here.
        //
        // The previous behaviour was "after 30 frames post
        // boot_completed, unconditionally switch the XMB pipeline from
        // DRM-direct to HWC". The intent was to hand displays to
        // SurfaceFlinger so a launched app could present. But the
        // transition happened whether or not the user was actually
        // about to launch an app, which meant the XMB itself ran in
        // HWC for the rest of the session -- with the extra latency
        // of eglSwapBuffers paced by HWC's compositor tick.
        //
        // For idle XMB (no app in flight) we get better and more
        // predictable pacing by staying in DRM-direct mode:
        //   - Our render thread directly controls page flips via
        //     drmModePageFlip + DRM_IOCTL_WAIT_VBLANK above.
        //   - No SurfaceFlinger compositor tick in the critical path.
        //   - No BLASTBufferQueue buffer starvation under load.
        //
        // drmStop() + setupSecondaryEglSurfaces() now run exactly
        // once, immediately after the main XMB loop exits (see the
        // post-loop section further down, gated on
        // mExitRequested). That way SurfaceFlinger is given the
        // displays at the moment we are about to launch an Android
        // app, which matches the original intent without paying the
        // DRM->HWC transition cost for XMB browsing.
    } else {
        // Overlay WALLPAPER/launcher mode (no app behind us): present exactly like
        // the non-overlay home. PS3 text / glow / anti-aliased edges and bright
        // wallpaper pixels draw with framebuffer alpha < 1 (the wallpaper effect uses
        // GL_SRC_ALPHA blending), and on this HWC SurfaceFlinger blends whatever is
        // behind through those holes - so a just-exited app bleeds through bright
        // wallpaper areas for a moment. Force the framebuffer fully OPAQUE (alpha = 1)
        // with an alpha-only masked clear (RGB untouched, so it is visually identical)
        // before presenting: the layer then occludes everything and SF composites it
        // like the opaque home layer and can drop the now-occluded app (perf + memory).
        // Scrim mode (a live app behind us) is deliberately left translucent so the
        // dimmed app keeps showing through. The surface never changes between the two,
        // so the switch is seamless and the overlay stays warm/instant.
        if (mOverlayMode && mOverlayWallpaper) {
            glColorMask(GL_FALSE, GL_FALSE, GL_FALSE, GL_TRUE);
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);
            glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
        }
        eglSwapBuffers(mDisplay, mSurface);
        // A2: deferred overlay show. overlayShow() does NOT t.show() the layer;
        // it sets mOverlayPendingShow so the FIRST composited frame is already the
        // faded-out (reveal~0) entrance frame. Now that that frame is on screen,
        // reveal the layer - so the XMB animates IN instead of flashing the full
        // (stale-buffer) chrome for one frame.
        if (mOverlayMode && mOverlayPendingShow) {
            SurfaceComposerClient::Transaction t;
            t.show(mFlingerSurfaceControl);
            t.apply();
            mOverlayPendingShow = false;
        }
        // Restart path (returning from game): readyToRun() saw boot
        // already complete and skipped DRM splash, so the drmStop()
        // branch above never runs. Set up secondary EGL surfaces here
        // on the first EGL swap so the wallpaper renders on the
        // secondary display in restart sessions too. The sFirstFrame
        // static at the top of render() is consumed on the first call,
        // so we track the setup state via the vector's emptiness
        // instead — gives exactly-once semantics without relying on
        // a flag that got reset four function-screens above.
        // Overlay mode is normally a single translucent layer over the running app on the
        // primary (no secondary). EXCEPTION: the DSi theme's OPAQUE post-game launcher
        // (overlay_home Quick Resume: the app is dead, mOverlayWallpaper=true) is a full home,
        // so it must present the bottom (carousel) screen too. Set the secondary up there; the
        // visibility toggle below hides it again if the overlay later goes translucent.
        // Gate on the REQUESTED stack mode, NOT the computed mNdsStack: in a fresh overlay
        // process (returning to the DSi home after an app exit that dropped us to SF) no
        // secondary has been seen yet, so mNdsHadSecondary is false and mNdsStack (auto) is
        // true - and !mNdsStack would then always skip the setup, so mNdsStack never clears
        // (deadlock: single-screen forever, the bottom panel stays black). Attempting the
        // setup is a no-op on a genuine single-screen device (no secondary port -> the vector
        // stays empty), so only a user-FORCED single-screen (mode 1) should skip it.
        // Set up the secondary for ANY displayed DSi overlay - the post-game launcher AND the
        // in-game overlay summoned over a live app (user: the overlay should show on BOTH screens).
        // Gate on show_overlay (overlay displayed) + the requested stack mode, NOT mOverlayWallpaper
        // or the computed mNdsStack (deadlock, see the deleted note). No-op on a genuine 1-panel
        // device (no secondary port). The visibility toggle below hides it again on dismiss.
        const bool ndsOverlayLauncher = mNdsTheme && mNdsStackMode != 1 &&
            property_get_bool("sys.gammaos.nano.show_overlay", false);
        // Dual-screen XMB bottom clock: the nds gate above only sets up the SF secondary in the DSi
        // theme, but the overlay-home also needs a secondary surface in PURE XMB to render the PSP
        // clock on the bottom panel. Gate on show_overlay so it only sets up while the overlay-home
        // is displayed, and on wallpaper mode so it is NOT set up over a live app (the clock is a
        // wallpaper-only feature; over an app the app/dual-stack owns the bottom panel). No-op on a
        // genuine 1-panel device (no secondary port).
        const bool xmbBottomClockLauncher = mPs3BottomClock && !mNdsTheme && mOverlayWallpaper &&
            property_get_bool("sys.gammaos.nano.show_overlay", false);
        // Minima needs its own SF secondary surface while the overlay-home is displayed, so the
        // bottom panel shows the Minima category+boxart view (renderMinimaSecondary) in overlay mode
        // and after returning from an app, instead of the PS3 wave.
        const bool minimaOverlayLauncher = mMinimaTheme &&
            property_get_bool("sys.gammaos.nano.show_overlay", false);
        if (mSecondaryEglSurfaces.empty()
                && (!mOverlayMode || ndsOverlayLauncher || xmbBottomClockLauncher || minimaOverlayLauncher)) {
            setupSecondaryEglSurfaces();
        }
    }

    // DSi overlay bottom panel: present the secondary (carousel) whenever the overlay is
    // DISPLAYED (show_overlay=1) - both the post-game launcher AND the in-game overlay summoned
    // over a live app, so the DSi menu shows on BOTH screens (user request). It is hidden the
    // instant the overlay is dismissed (show_overlay=0, also forced by overlayHide) so the opaque
    // RGBX surface never covers the running game's bottom screen. Toggle only on change.
    if (mOverlayMode && !mSecondaryWallpaperControls.empty()) {
        const bool wantShown = property_get_bool("sys.gammaos.nano.show_overlay", false);
        if (wantShown != mNdsSecondaryShown) {
            SurfaceComposerClient::Transaction t;
            for (const auto& sc : mSecondaryWallpaperControls) {
                if (wantShown) t.show(sc); else t.hide(sc);
            }
            t.apply();
            mNdsSecondaryShown = wantShown;
        }
    }
    // Dual-Stack coverage: when a dualstack app forces the carousel's display (port 0) to a TALL
    // logical canvas (e.g. 640x960), the carousel surface - created at the physical mode size
    // (640x480) - covers only the TOP HALF of that canvas, leaving the app visible in the bottom
    // half (user: over a dualstack app "the bottom screen is only half covered"). SF projects the
    // tall canvas back down onto the physical panel, so scaling the carousel LAYER to fill the full
    // logical canvas makes the panel show the whole carousel with no app bleed - and the panel's
    // own down-projection cancels the stretch, so the carousel still reads at its normal size. The
    // scale is a pure function of the display's current logical size vs the created buffer size, so
    // it self-adjusts: identity (1x) on a normal 640x480 canvas, 2x under a 640x960 dualstack canvas,
    // and reverts automatically when dualstack ends. Re-issue the transaction only when the logical
    // height changes; poll cheaply (~every 15 frames) since the tall size only toggles on app changes.
    if (mOverlayMode && !mSecondaryWallpaperControls.empty()) {
        static int sDsCoverCtr = 0;
        if ((sDsCoverCtr++ % 15) == 0) {
            for (size_t i = 0; i < mSecondaryWallpaperControls.size()
                            && i < mSecondaryDisplayTokens.size()
                            && i < mSecondaryCreatedSize.size()
                            && i < mSecondaryAppliedLssH.size(); i++) {
                if (mSecondaryWallpaperControls[i] == nullptr
                        || mSecondaryDisplayTokens[i] == nullptr) continue;
                ui::DisplayState st;
                if (SurfaceComposerClient::getDisplayState(mSecondaryDisplayTokens[i], &st) != NO_ERROR)
                    continue;
                const int lssW = (int)st.layerStackSpaceRect.getWidth();
                const int lssH = (int)st.layerStackSpaceRect.getHeight();
                const int cw = mSecondaryCreatedSize[i].first;
                const int ch = mSecondaryCreatedSize[i].second;
                if (lssW <= 0 || lssH <= 0 || cw <= 0 || ch <= 0) continue;
                const float sx = (float)lssW / (float)cw;
                const float sy = (float)lssH / (float)ch;
                // REASSERT the stretch every tick, not just when lssH changes. A display reconfigure
                // (an app taking display 0 on launch, or a resume from sleep) resets the secondary
                // surface's transform back to identity while its logical size is UNCHANGED; the old
                // skip-if-unchanged then never re-applied, leaving the Control Center squished into the
                // top half of the panel (user: "stretched/squished, like dualstack mode"). setMatrix is a
                // cheap idempotent ~4Hz transaction, so reassert unconditionally and only log on a real move.
                SurfaceComposerClient::Transaction t;
                t.setMatrix(mSecondaryWallpaperControls[i], sx, 0.0f, 0.0f, sy);
                t.apply();
                if (lssH != mSecondaryAppliedLssH[i]) {
                    mSecondaryAppliedLssH[i] = lssH;
                    ALOGI("nano ds-cover: secondary %zu stretch buf %dx%d -> canvas %dx%d (sx=%.2f sy=%.2f)",
                          i, cw, ch, lssW, lssH, sx, sy);
                }
            }
        }
    }

    // Skip the secondary render only while the overlay is DISMISSED (show_overlay=0) so the
    // running game owns its bottom screen; while the overlay is displayed, render the carousel
    // on the secondary (the in-game scrim path in renderNdsCarousel dims the live app behind it).
    const bool ndsSecondaryHidden = mOverlayMode &&
        !property_get_bool("sys.gammaos.nano.show_overlay", false);

    // GammaOS: Render wallpaper (or bottom DS screen when drastic QR
    // is active) to secondary display(s). Switch to each secondary
    // EGL surface, render, swap.
    for (size_t i = 0; !ndsSecondaryHidden && i < mSecondaryEglSurfaces.size(); i++) {
        eglMakeCurrent(mDisplay, mSecondaryEglSurfaces[i], mSecondaryEglSurfaces[i], mContext);
        glViewport(0, 0, mWidth, mHeight); // secondary has same resolution
        glClearColor(0.0f, 0.0f, 0.0f, nanoSecondaryClearAlpha(mOverlayMode, mOverlayWallpaper));
        glClear(GL_COLOR_BUFFER_BIT);
        mRenderingPanel = 1;   // secondary/bottom panel: pick its own wallpaper (empty -> normal bg)
        if (drasticActive) {
            // Secondary display -> bottom DS screen fullscreen.
            drastic->renderBottomScreen(sDrasticSaturation, sDrasticGradient);
            drawDrasticQrOverlay(mWidth, mHeight,
                                 sDrasticSaturation, sDrasticGradient);
        } else if (mNdsTheme && mPs3BootActive) {
            // DSi cold boot: the bottom panel shows the same white field (no logo/notice),
            // so both screens boot to white instead of one flashing the wave.
            renderNdsBootOverlay(/*primary=*/false);
        } else if (mMinimaTheme && mPs3BootActive) {
            // Minima cold boot on the SF secondary (the RG DS bottom): the Minima black-field intro,
            // not the PS3 wave - mirrors the primary dispatch and the DRM secondary pass.
            renderMinimaBootOverlay(/*primary=*/false);
        } else if (mNdsTheme && !mPs3BootActive) {
            // DSi theme dual-panel: a live secondary always shows the carousel (never the PS3
            // wave), independent of the primary's stacking mode (same resolution as primary).
            // During first-run setup, show the DSi setup backdrop instead of the home carousel
            // so the bottom panel does not expose the home before setup completes.
            if (mSetupWizardActive) renderSetupNdsBackdrop();
            else                    renderNdsCarousel(0.0f, 0.0f, (float)mWidth, (float)mHeight);
            if (i == 0 && mPs3WizActive) renderNetWizard();       // WiFi/BT setup wizard on the bottom panel
            if (i == 0 && mGSearchActive) renderGlobalSearch();   // global search results on the bottom panel
            if (i == 0) renderOsk();   // DSi keyboard/OSK on the bottom touch panel (self-gates on mOskActive)
        } else if (mMinimaTheme && !mPs3BootActive) {
            // Minima live secondary (the RG DS bottom): the category + boxart panel, NOT the PS3 wave.
            // This is the pass that runs in overlay mode and after returning from an app (the DRM
            // secondary above is skipped once HWC owns the display), so it fixes the bottom showing
            // the wave in those contexts too.
            renderMinimaSecondary(0.0f, 0.0f, (float)mWidth, (float)mHeight);
            if (i == 0 && mPs3WizActive) renderNetWizard();
            if (i == 0 && mGSearchActive) renderGlobalSearch();
            if (i == 0) renderOsk();
        } else {
            renderEffect();
            // Dual-screen XMB: static PSP clock on the bottom panel (opt-in), over the wave. No
            // mWidth/mHeight remap here - on the SF path they already equal the secondary dims.
            if (mPs3BottomClock && i == 0 && !mPs3BootActive && (!mOverlayMode || mOverlayWallpaper))
                renderPspClockSecondary();
            // XMB search / Wi-Fi password / System Name keyboard on the BOTTOM touch panel (like the DSi
            // branch above). No remap needed - both panels are 640x480 on the SF path. Self-gates on mOskActive.
            if (i == 0) renderOsk();
        }
        if (i == 0) maybeNanoScreenshotSecondary();   // debug capture of the bottom DS panel
        eglSwapBuffers(mDisplay, mSecondaryEglSurfaces[i]);
    }
    mRenderingPanel = 0;   // back to the primary/top panel
    // Switch back to primary
    if (!mSecondaryEglSurfaces.empty()) {
        eglMakeCurrent(mDisplay, mSurface, mSurface, mContext);
    }
}

} // namespace android
