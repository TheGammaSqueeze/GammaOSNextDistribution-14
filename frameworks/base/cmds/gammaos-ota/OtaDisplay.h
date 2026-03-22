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

#ifndef GAMMAOS_OTA_DISPLAY_H
#define GAMMAOS_OTA_DISPLAY_H

#include <stdint.h>
#include <string>

namespace android {

// Minimal direct display renderer for OTA progress.
// Uses DRM/KMS if available, falls back to fbdev.
// No dependency on SurfaceFlinger or EGL — renders directly via kernel.
class OtaDisplay {
public:
    OtaDisplay();
    ~OtaDisplay();

    // Initialize the display. Returns true on success.
    // Call AFTER stopping SurfaceFlinger (it holds the display).
    bool init();

    // Draw a progress screen with the given percentage (0-100) and status text.
    void drawProgress(int percent, const std::string& status);

    // Release the display.
    void close();

    int width() const { return mWidth; }
    int height() const { return mHeight; }

private:
    // Framebuffer backend
    bool initFbdev();
    void closeFbdev();

    // DRM/KMS backend
    bool initDrm();
    void closeDrm();

    // Draw helpers (work on mBuffer)
    void fillRect(int x, int y, int w, int h, uint32_t color);
    void drawChar(int x, int y, char c, uint32_t color);
    void drawString(int x, int y, const char* str, uint32_t color);
    void flip();

    enum Backend { NONE, FBDEV, DRM };
    Backend mBackend = NONE;

    int mWidth = 0;
    int mHeight = 0;
    int mStride = 0;      // bytes per row
    int mBpp = 0;          // bits per pixel
    uint8_t* mBuffer = nullptr;

    // Fbdev state
    int mFbFd = -1;
    uint8_t* mFbMmap = nullptr;
    size_t mFbMmapSize = 0;
    int mFbYOffset = 0;    // for double buffering

    // DRM state
    int mDrmFd = -1;
    // TODO: DRM/KMS fields (crtc, connector, fb, handle) will be added
    // when full DRM backend is implemented
};

} // namespace android

#endif // GAMMAOS_OTA_DISPLAY_H
