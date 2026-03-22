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

#define LOG_TAG "GammaOSOta"

#include "OtaDisplay.h"
#include "OtaFlasher.h"

#include <fcntl.h>
#include <linux/fb.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <unistd.h>
#include <errno.h>

#include <utils/Log.h>

// Simple 8x16 bitmap font (ASCII 32-126) embedded directly
// Each char is 8 pixels wide, 16 pixels tall, 1 bit per pixel
static const uint8_t FONT_8x16[95][16] = {
    // Space (32)
    {0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00},
    // ! (33)
    {0x00,0x00,0x18,0x3C,0x3C,0x3C,0x18,0x18,0x18,0x00,0x18,0x18,0x00,0x00,0x00,0x00},
    // " (34)
    {0x00,0x63,0x63,0x63,0x22,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00},
    // # (35)
    {0x00,0x00,0x00,0x36,0x36,0x7F,0x36,0x36,0x7F,0x36,0x36,0x00,0x00,0x00,0x00,0x00},
    // $ - ~ (36-126): simplified - fill with basic patterns
    // For brevity, we'll use a minimal approach: render with rectangles for unimplemented chars
};

namespace android {

OtaDisplay::OtaDisplay() {}

OtaDisplay::~OtaDisplay() {
    close();
}

bool OtaDisplay::init() {
    // Try DRM first (modern devices), fall back to fbdev
    if (initDrm()) {
        mBackend = DRM;
        OtaFlasher::logToFile("INFO", "OtaDisplay: initialized via DRM/KMS (%dx%d, %dbpp)",
                              mWidth, mHeight, mBpp);
        return true;
    }

    if (initFbdev()) {
        mBackend = FBDEV;
        OtaFlasher::logToFile("INFO", "OtaDisplay: initialized via fbdev (%dx%d, %dbpp, stride=%d)",
                              mWidth, mHeight, mBpp, mStride);
        return true;
    }

    OtaFlasher::logToFile("ERROR", "OtaDisplay: failed to initialize any display backend");
    return false;
}

bool OtaDisplay::initFbdev() {
    const char* fbPaths[] = {"/dev/graphics/fb0", "/dev/fb0", nullptr};

    for (const char** path = fbPaths; *path; path++) {
        mFbFd = open(*path, O_RDWR | O_CLOEXEC);
        if (mFbFd >= 0) break;
    }
    if (mFbFd < 0) return false;

    struct fb_var_screeninfo vi;
    struct fb_fix_screeninfo fi;

    if (ioctl(mFbFd, FBIOGET_VSCREENINFO, &vi) < 0) {
        ::close(mFbFd);
        mFbFd = -1;
        return false;
    }
    if (ioctl(mFbFd, FBIOGET_FSCREENINFO, &fi) < 0) {
        ::close(mFbFd);
        mFbFd = -1;
        return false;
    }

    mWidth = vi.xres;
    mHeight = vi.yres;
    mBpp = vi.bits_per_pixel;
    mStride = fi.line_length;

    // Map the framebuffer
    mFbMmapSize = vi.yres_virtual * fi.line_length;
    mFbMmap = (uint8_t*)mmap(nullptr, mFbMmapSize, PROT_READ | PROT_WRITE, MAP_SHARED, mFbFd, 0);
    if (mFbMmap == MAP_FAILED) {
        mFbMmap = nullptr;
        ::close(mFbFd);
        mFbFd = -1;
        return false;
    }

    // Use the first buffer
    mBuffer = mFbMmap;
    mFbYOffset = 0;

    // Unblank the display
    ioctl(mFbFd, FBIOBLANK, FB_BLANK_UNBLANK);

    return true;
}

void OtaDisplay::closeFbdev() {
    if (mFbMmap) {
        munmap(mFbMmap, mFbMmapSize);
        mFbMmap = nullptr;
    }
    if (mFbFd >= 0) {
        ::close(mFbFd);
        mFbFd = -1;
    }
    mBuffer = nullptr;
}

bool OtaDisplay::initDrm() {
    // Try common DRM device paths
    const char* drmPaths[] = {"/dev/dri/card0", "/dev/dri/card1", nullptr};

    for (const char** path = drmPaths; *path; path++) {
        mDrmFd = open(*path, O_RDWR | O_CLOEXEC);
        if (mDrmFd >= 0) break;
    }
    if (mDrmFd < 0) return false;

    // DRM/KMS setup would go here — requires libdrm which may not be available
    // on all builds. For now, close and fall back to fbdev.
    // TODO: Implement full DRM/KMS backend using drmModeGetResources etc.
    ::close(mDrmFd);
    mDrmFd = -1;
    return false;
}

void OtaDisplay::closeDrm() {
    if (mDrmFd >= 0) {
        ::close(mDrmFd);
        mDrmFd = -1;
    }
}

void OtaDisplay::close() {
    if (mBackend == FBDEV) closeFbdev();
    else if (mBackend == DRM) closeDrm();
    mBackend = NONE;
}

void OtaDisplay::fillRect(int x, int y, int w, int h, uint32_t color) {
    if (!mBuffer || mBpp != 32) return;

    for (int row = y; row < y + h && row < mHeight; row++) {
        if (row < 0) continue;
        uint32_t* line = (uint32_t*)(mBuffer + row * mStride);
        for (int col = x; col < x + w && col < mWidth; col++) {
            if (col < 0) continue;
            line[col] = color;
        }
    }
}

void OtaDisplay::drawChar(int x, int y, char c, uint32_t color) {
    // Simple 8x16 character rendering using basic block patterns
    // For characters we don't have bitmaps for, draw a filled block
    if (!mBuffer || mBpp != 32) return;
    if (c < 32 || c > 126) return;

    // Simple approach: render each character as a 6x10 filled rectangle
    // This is readable at the sizes we need (status text + percentage)
    // A proper font would need FreeType or embedded bitmaps
    int charW = 8;
    int charH = 14;

    // Draw character as simple filled block (placeholder - works for progress display)
    // For digits and basic chars, this is sufficient
    if (c != ' ') {
        // Simple block character rendering
        for (int row = 0; row < charH && (y + row) < mHeight; row++) {
            if (y + row < 0) continue;
            uint32_t* line = (uint32_t*)(mBuffer + (y + row) * mStride);
            for (int col = 0; col < charW - 2 && (x + col) < mWidth; col++) {
                if (x + col < 0) continue;
                // Simple bitmap patterns for digits
                bool pixel = false;
                if (c >= '0' && c <= '9') {
                    // Crude digit rendering - just show filled blocks with gaps
                    pixel = (col >= 1 && col <= 5 && row >= 1 && row <= 12);
                    // Create digit-like patterns by cutting holes
                    if (c == '0' && col >= 2 && col <= 4 && row >= 3 && row <= 10) pixel = false;
                    if (c == '1' && (col < 2 || col > 3)) pixel = false;
                } else if (c == '%') {
                    pixel = (row < 4 && col < 3) || (row > 9 && col > 2) ||
                            (row >= 2 && row <= 11 && col == (row - 2));
                } else if (c == '.') {
                    pixel = (row >= 10 && row <= 12 && col >= 2 && col <= 4);
                } else if (c == ':') {
                    pixel = ((row >= 3 && row <= 5) || (row >= 8 && row <= 10)) &&
                            (col >= 2 && col <= 4);
                } else {
                    // Generic letter - filled block
                    pixel = (col >= 0 && col <= 5 && row >= 0 && row <= 12);
                }
                if (pixel) line[x + col] = color;
            }
        }
    }
}

void OtaDisplay::drawString(int x, int y, const char* str, uint32_t color) {
    int cx = x;
    while (*str) {
        drawChar(cx, y, *str, color);
        cx += 9; // 8px char + 1px gap
        str++;
    }
}

void OtaDisplay::flip() {
    if (mBackend == FBDEV && mFbFd >= 0) {
        // Pan display to show the buffer we wrote to
        struct fb_var_screeninfo vi;
        if (ioctl(mFbFd, FBIOGET_VSCREENINFO, &vi) == 0) {
            vi.yoffset = mFbYOffset;
            vi.activate = FB_ACTIVATE_NOW | FB_ACTIVATE_FORCE;
            // Try FBIOPAN_DISPLAY first (preferred for buffer flipping)
            if (ioctl(mFbFd, FBIOPAN_DISPLAY, &vi) < 0) {
                // Fallback to FBIOPUT_VSCREENINFO
                ioctl(mFbFd, FBIOPUT_VSCREENINFO, &vi);
            }
        }

        // On MTK devices, we may also need to trigger a refresh via the
        // MTK display manager. Try writing to the fb to trigger an update.
        // Some MTK drivers need MTKFB_SET_OVERLAY_LAYER or similar ioctl
        // but those are device-specific. The FBIOPAN should work for basic fb.
    }
    // DRM would use drmModePageFlip here
}

void OtaDisplay::drawProgress(int percent, const std::string& status) {
    if (!mBuffer) return;

    // Colors (ARGB8888)
    const uint32_t BG_COLOR    = 0xFF141420;  // Dark background
    const uint32_t BAR_BG      = 0xFF2A2A3A;  // Progress bar background
    const uint32_t BAR_FILL    = 0xFF4488CC;  // Progress bar fill (blue)
    const uint32_t TEXT_COLOR  = 0xFFE0E0E0;  // Light text
    const uint32_t TITLE_COLOR = 0xFFFFFFFF;  // White title

    // Clear screen
    fillRect(0, 0, mWidth, mHeight, BG_COLOR);

    // Layout calculations
    int centerY = mHeight / 2;
    int margin = mWidth / 10;
    int barHeight = mHeight / 20;
    if (barHeight < 20) barHeight = 20;
    int barY = centerY - barHeight / 2;
    int barWidth = mWidth - 2 * margin;

    // Title: "GammaOS System Update"
    int titleY = barY - 80;
    if (titleY < 20) titleY = 20;
    drawString(margin, titleY, "GammaOS System Update", TITLE_COLOR);

    // Status text
    drawString(margin, barY - 30, status.c_str(), TEXT_COLOR);

    // Progress bar background
    fillRect(margin, barY, barWidth, barHeight, BAR_BG);

    // Progress bar fill
    int fillWidth = (barWidth * percent) / 100;
    if (fillWidth > 0) {
        fillRect(margin, barY, fillWidth, barHeight, BAR_FILL);
    }

    // Percentage text
    char pctStr[16];
    snprintf(pctStr, sizeof(pctStr), "%d%%", percent);
    int pctX = margin + barWidth / 2 - 20;
    drawString(pctX, barY + barHeight + 15, pctStr, TEXT_COLOR);

    flip();
}

} // namespace android
