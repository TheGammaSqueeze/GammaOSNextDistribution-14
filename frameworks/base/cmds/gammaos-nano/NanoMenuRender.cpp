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
#include "NanoMenuDrm.h"
#include "NanoMenuShaders.h"
#include "NanoMenuPS3.h"
#include "NanoMenuPS3Bg.h"
#include "xmb_icons.h"

namespace android {

using ui::DisplayMode;

// ---------------------------------------------------------------------------
// Icon texture rendering (monochrome 32x32 icons, tinted at draw time)
// ---------------------------------------------------------------------------

// Map system index to RetroArch XMB monochrome icon filename
// Order MUST match kXmbSystemDefs (in NanoMenuXmb.cpp): NES,SNES,GB,GBC,GBA,N64,NDS,GEN,SMS,GG,PSX,PSP,DC,NGP,P8,history,<game slot has no file>,setting
static const char* kIconPngNames[18] = {
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
    nullptr,                                              // 16: game item (embedded only)
    "setting.png",                                        // 17: Settings column
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
    for (int i = 0; i < 18; i++) {
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
    if (iconIdx < 0 || iconIdx >= 18 || mIconTextures[iconIdx] == 0) return;

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
static const int SOLID_BATCH_MAX_VERTS = 4096;
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

// Snapshot the WHOLE framebuffer into mGlassTex, then blur it. We capture the
// full viewport (not just the panel rect) so this is rotation/flip agnostic:
// under DRM rotation the panel-rect-to-FB mapping is rotated and a per-region
// copy grabbed the wrong pixels (the old code just bailed via sDrmGlRotation,
// which is why the frosted glass never appeared on rotated panels like the
// 180-degree Brick). drawFrostedGlass maps each panel vertex into FB-NDC via
// sDrmRotMat so it samples the correct screen region behind the panel.
bool NanoMenu::captureGlass(float /*x*/, float /*y*/, float /*w*/, float /*h*/) {
    GLint vp[4] = {0, 0, 0, 0};
    glGetIntegerv(GL_VIEWPORT, vp);
    int fbX = vp[0], fbY = vp[1], fbW = vp[2], fbH = vp[3];
    if (fbW <= 0 || fbH <= 0) return false;
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

// Draw the frosted panel sampling mGlassTex (captured by captureGlass with the
// same rect). texcoords are v-flipped because the FB snapshot is y-up.
void NanoMenu::drawFrostedGlass(float x, float y, float w, float h, float radius,
                                float tr, float tg, float tb, float tintA, float fade,
                                bool waveSpace) {
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
    int   bw = (mGlassBlurW > 0) ? mGlassBlurW : mGlassTexW;
    int   bh = (mGlassBlurH > 0) ? mGlassBlurH : mGlassTexH;
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
    if (mGlassLocTonemap >= 0) glUniform1f(mGlassLocTonemap, waveSpace ? 1.6846f : 0.0f);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, (mGlassBlurTex != 0) ? mGlassBlurTex : mGlassTex);
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
    std::vector<uint8_t> blank(mAtlasW * mAtlasH * 4, 255);
    for (size_t i = 3; i < blank.size(); i += 4) blank[i] = 0;
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, mAtlasW, mAtlasH, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, blank.data());
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
    const uint64_t monoKey = ((uint64_t)(uint32_t)rasterPx << 32) | cp;
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
    for (int i = 0; i < mFtNumFaces; i++) {
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

    EGLConfig config = getEglConfig(mDisplay);
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
        sp<SurfaceControl> sc = session()->createSurface(
                String8("GammaOSNanoWallpaper"),
                res.getWidth(), res.getHeight(),
                PIXEL_FORMAT_RGBX_8888, ISurfaceComposerClient::eOpaque);
        if (sc == nullptr || !sc->isValid()) {
            ALOGW("NanoMenu: secondary port %d createSurface failed", port);
            continue;
        }

        // Route to the secondary display's layer stack at top z so it
        // overrides bootanim (which uses STRATUM_BOOT_PROGRESS layers).
        SurfaceComposerClient::Transaction t;
        Rect bounds(0, 0, res.width, res.height);
        t.setDisplayProjection(token, ui::ROTATION_0, bounds, bounds);
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

// ---------------------------------------------------------------------------
// Render
// ---------------------------------------------------------------------------

// GammaOS Nano debug capture: when sys.gammaos.nano.shot is set, read the
// just-composited frame straight out of the bound framebuffer and write it as a
// binary PPM. This is the only capture that works on the DRM-direct path, where
// SurfaceFlinger (screencap) and fbdev (/dev/graphics/fb0) see only black.
// Set the prop to "1" for the default path, or to an absolute file path; it is
// cleared after one capture.
static void maybeNanoScreenshot() {
    char val[PROPERTY_VALUE_MAX] = {};
    property_get("sys.gammaos.nano.shot", val, "");
    if (!val[0]) return;
    GLint vp[4] = {0, 0, 0, 0};
    glGetIntegerv(GL_VIEWPORT, vp);
    int w = vp[2], h = vp[3];
    if (w <= 0 || h <= 0) { property_set("sys.gammaos.nano.shot", ""); return; }
    std::vector<unsigned char> px((size_t)w * h * 4);
    glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, px.data());
    const char* path = (val[0] == '1' && !val[1]) ? "/data/local/tmp/nano_shot.ppm" : val;
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
    property_set("sys.gammaos.nano.shot", "");
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
            if (mInDrmSleep.load(std::memory_order_relaxed)
                || mVidTeardownExempt.load(std::memory_order_relaxed)) {
                // Parked for sleep, or joining a wedged video-open worker during a forced
                // teardown (sleep/occlusion): the heartbeat legitimately stalls; don't abort.
                // NOTE: a NORMAL video open no longer exempts the watchdog - the render thread
                // stays responsive (it spins the loading spinner); only this rare blocking
                // teardown join is exempt.
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
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        if (drasticActive) {
            // Secondary display -> BOTTOM DS screen fullscreen.
            drastic->renderBottomScreen(sDrasticSaturation, sDrasticGradient);
            // drawText inside the overlay lambda uses mWidth/mHeight for
            // pixel->NDC; panel-native AHB dims would mis-project the text
            // on rotated single-display devices (RK3576 1080x1920).
            drawDrasticQrOverlay(mWidth, mHeight,
                                 sDrasticSaturation, sDrasticGradient);
        } else {
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            renderEffect();
            glDisable(GL_BLEND);
        }
    }

    // GammaOS: Primary pass — wallpaper + full menu (XMB or normal). When
    // DRM zero-copy is active, binds sAhbTarget (primary AHB). When post-
    // boot, the default SurfaceFlinger-backed FBO is used via the EGL path.
    if (sDrmActive && sDrmZeroCopy) {
        drmBindNextFbo();
    }
    // GammaOS: When GL rotation is active, use the AHB (panel-native) dimensions
    // for the viewport, not the logical mWidth/mHeight. The rotation matrix in the
    // vertex shaders maps logical NDC to the panel-native viewport.
    if (sDrmGlRotation) {
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
        glClearColor(0.0f, 0.0f, 0.0f, sOvDim);
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

    if (mSetupWizardActive && !mPs3BootActive) {
        // During a PS3 cold boot the wizard is held back so the full intro
        // (anim -> epilepsy warning) plays first; it cuts in once the intro ends.
        renderSetupWizard();
        renderOsk();
    } else if (mPs3Xmb && mOskOverApp) {
        // OSK-only over a live app (an app requested text entry; see overlayOskPoll).
        // The app shows through the translucent overlay layer - dim it with a scrim
        // and draw just the keyboard, no Quick Menu behind it.
        setUiBlend();
        drawQuad(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f, 0.0f, 0.0f, 0.55f);
        renderOsk();
    } else if (mPs3Xmb) {
        // PS3 XMB layout (NanoMenuPS3Menu.cpp). renderPs3Xmb() draws the Wi-Fi /
        // Bluetooth sub-screens itself when mMenuState is MENU_WIFI / MENU_BT, and
        // drives + renders the cold-boot intro when mPs3BootActive.
        renderPs3Xmb();
        renderScrapeProgress();   // boxart-scraper progress / result modal, over the XMB
        renderOsk();
        // Overlay launch transition: fade the whole XMB to black over ~300ms so the
        // app's own cold start is covered by a clean fade-out instead of a frozen,
        // still-navigable menu. The black holds (the input-freeze in pollInput keeps
        // it inert) until the overlay dismisses onto the resumed app (overlayPoll).
        if (mOverlayMode && mOverlayLaunchPending) {
            int64_t el = uptimeMillis() - mOverlayLaunchStartMs;
            float fa = (el <= 0) ? 0.0f : (float)el / 300.0f;
            if (fa < 0.0f) fa = 0.0f;
            if (fa > 1.0f) fa = 1.0f;
            setUiBlend();
            drawQuad(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f, 0.0f, 0.0f, fa);
        }
        // Home (non-overlay) launch fade-out: fade the XMB to black over ~260ms
        // after the launching select is released (mLaunchFadeStart), then the nano
        // hands off to the app (gated in pollInput). The default launch transition.
        if (!mOverlayMode && mLaunchFadeStart > 0) {
            int64_t el = (int64_t)uptimeMillis() - mLaunchFadeStart;
            float fa = (el <= 0) ? 0.0f : (float)el / 260.0f;
            if (fa < 0.0f) fa = 0.0f;
            if (fa > 1.0f) fa = 1.0f;
            setUiBlend();
            drawQuad(0.0f, 0.0f, (float)mWidth, (float)mHeight, 0.0f, 0.0f, 0.0f, fa);
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
    float titleW = measureText(mTitle.c_str(), titleScale);
    float titleX = (mWidth - titleW) / 2.0f;
    float titleY = startY;
    drawText(mTitle.c_str(), titleX, titleY, titleScale,
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
            // shader's rotation, so the logical-landscape rect we want
            // (a horizontal band across the menu column) needs to be
            // remapped to the panel-native FBO before glScissor. Without
            // this, on a 90/270-rotated panel the scissor still clips a
            // landscape band of the FBO, which only covers a fraction of
            // the rotated content -- text outside that fraction gets
            // truncated. RK3576 (1080x1920 portrait, 270° install) is
            // the device that surfaced this.
            int sx, sy, sw, sh;
            int lx = (int)contentLeft, ly = 0, lw = (int)contentW,
                lh = (int)mHeight;
            switch (sDrmGlRotation ? sDrmRotationDeg : 0) {
            case 90:
                sx = ly; sy = mWidth - lx - lw;
                sw = lh; sh = lw;
                break;
            case 180:
                sx = mWidth - lx - lw; sy = mHeight - ly - lh;
                sw = lw; sh = lh;
                break;
            case 270:
                sx = mHeight - ly - lh; sy = lx;
                sw = lh; sh = lw;
                break;
            default:
                sx = lx; sy = ly; sw = lw; sh = lh;
                break;
            }
            glScissor(sx, sy, sw, sh);
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
        const char* qrLabel = "Quick Resume";
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
        // Overlay mode never drives secondary-display wallpaper: it is a
        // single translucent layer over the running app on the primary.
        if (mSecondaryEglSurfaces.empty() && !mOverlayMode) {
            setupSecondaryEglSurfaces();
        }
    }

    // GammaOS: Render wallpaper (or bottom DS screen when drastic QR
    // is active) to secondary display(s). Switch to each secondary
    // EGL surface, render, swap.
    for (size_t i = 0; i < mSecondaryEglSurfaces.size(); i++) {
        eglMakeCurrent(mDisplay, mSecondaryEglSurfaces[i], mSecondaryEglSurfaces[i], mContext);
        glViewport(0, 0, mWidth, mHeight); // secondary has same resolution
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        if (drasticActive) {
            // Secondary display -> bottom DS screen fullscreen.
            drastic->renderBottomScreen(sDrasticSaturation, sDrasticGradient);
            drawDrasticQrOverlay(mWidth, mHeight,
                                 sDrasticSaturation, sDrasticGradient);
        } else {
            renderEffect();
        }
        eglSwapBuffers(mDisplay, mSecondaryEglSurfaces[i]);
    }
    // Switch back to primary
    if (!mSecondaryEglSurfaces.empty()) {
        eglMakeCurrent(mDisplay, mSurface, mSurface, mContext);
    }
}

} // namespace android
