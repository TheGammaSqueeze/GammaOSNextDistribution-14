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

#ifndef GAMMAOS_OTA_MENU_H
#define GAMMAOS_OTA_MENU_H

#include <string>
#include <vector>
#include <set>
#include <utility>
#include <unordered_map>
#include <mutex>
#include <pthread.h>

#include <ft2build.h>
#include FT_FREETYPE_H

#include <utils/Thread.h>
#include <binder/IBinder.h>

#include <EGL/egl.h>
#include <GLES2/gl2.h>

#include "OtaManifest.h"
#include "OtaFlasher.h"

namespace android {

class Surface;
class SurfaceComposerClient;
class SurfaceControl;

struct GlyphInfoOta {
    float u0, v0, u1, v1;
    int bmpW, bmpH;
    int bearingX, bearingY;
    int advance;
};

class OtaMenu : public Thread, public IBinder::DeathRecipient {
public:
    OtaMenu();
    virtual ~OtaMenu();

    sp<SurfaceComposerClient> session() const;

    // Set auto-install package path (Mode A). Empty = Mode B (file browser).
    void setPackagePath(const std::string& path) { mPackagePath = path; }

    // Called by flash thread (public for pthread entry)
    void runFlashSequence();

    enum MenuState {
        STATE_FILE_BROWSER,     // Mode B: select a file
        STATE_CONFIRM,          // Show partition list + backup option, confirm install
        STATE_STAGING,          // Copying binaries to tmpfs
        STATE_PREFLIGHT,        // Running checks
        STATE_BACKUP,           // Optional backup in progress
        STATE_FLASHING,         // Flashing partitions
        STATE_VERIFYING,        // Post-flash verification
        STATE_SUCCESS,          // Done, reboot prompt
        STATE_FAILED,           // Error with retry/restore options
    };

    // Visual theme for the full-screen OTA UI. Selected via the
    // sys.gammaos.ota.theme property ("ps3" / "dsi", else default).
    enum OtaTheme {
        THEME_DEFAULT,          // Original plain flat UI
        THEME_PS3,              // PlayStation 3 XMB look
        THEME_DSI,              // Nintendo DSi look
    };

private:
    virtual bool        threadLoop();
    virtual status_t    readyToRun();
    virtual void        onFirstRef();
    virtual void        binderDied(const wp<IBinder>& who);

    // Input
    void openInputDevices();
    void pollInput();
    void handleUp();
    void handleDown();
    void handleSelect();
    void handleBack();

    // Rendering
    void initShaders();
    void initFonts();
    void ensureGlyph(uint32_t codepoint);
    void drawText(const char* str, float px, float py, float scale,
                  float r, float g, float b, float a);
    float measureText(const char* str, float scale);
    void render();
    void drawQuad(float x, float y, float w, float h,
                  float r, float g, float b, float a);
    void drawProgressBar(float x, float y, float w, float h, float progress);

    // Themed rendering (PS3 XMB / Nintendo DSi). Each draws the complete frame
    // for the current mState. The plain default path stays in render().
    void renderPs3();
    void renderDsi();
    // Themed progress meter shared by the themed frames.
    void drawThemedProgressBar(float x, float y, float w, float h, float progress,
                               float troughR, float troughG, float troughB,
                               float fillR, float fillG, float fillB,
                               float borderR, float borderG, float borderB,
                               bool drawBorder);
    // Shared helper: current phase label + progress percent for the flashing
    // screens, derived from mCurrentStatus (thread-safe read).
    void themedFlashInfo(std::string* phaseLabel, int* percent, bool* isWrite);

    // File browser
    void scanForPackages();
    // Extract a package .zip to /data/gammaos_ota/package (shared by the file browser and the
    // nano-handed-off auto-install path). Returns {pkgDir,""} on success, {"",error} on failure.
    std::pair<std::string, std::string> extractZipHelper(const std::string& zipPath);
    static bool isZipFile(const std::string& path);
    struct FileEntry {
        std::string path;
        std::string displayName;
        uint64_t size;
    };

    // Members ordered to match constructor initialization order
    // File browser
    std::vector<FileEntry> mFileEntries;
    int mFileSelectedIndex;

    // Confirm screen
    bool mBackupRequested;
    int mConfirmSelectedIndex;

    // Error screen
    int mErrorSelectedIndex;

    // State machine (must be after file browser / confirm / error fields)
    MenuState mState;
    std::string mPackagePath;
    OtaManifest mManifest;
    OtaFlasher mFlasher;
    std::string mErrorMessage;
    bool mHasBackup;

    // Flash status
    std::mutex mStatusMutex;
    FlashStatus mCurrentStatus;
    std::vector<std::string> mFailedPartitions;

    // Flash thread
    void startFlashThread();
    pthread_t mFlashThread;

    // Display
    sp<SurfaceComposerClient> mSession;
    int         mWidth;
    int         mHeight;
    EGLDisplay  mDisplay;
    EGLContext  mContext;
    EGLSurface  mSurface;
    sp<IBinder> mDisplayToken;
    sp<SurfaceControl> mFlingerSurfaceControl;
    sp<Surface> mFlingerSurface;

    // Shaders
    GLuint mShaderProgram;
    GLint  mLocPosition;
    GLint  mLocColor;

    // FreeType (must be before text shader)
    FT_Library mFtLib;
    FT_Face mFtFace;
    int mFontSize;
    GLuint mGlyphAtlasTex;
    int mAtlasW, mAtlasH;
    int mAtlasCurX, mAtlasCurY, mAtlasRowH;
    std::unordered_map<uint32_t, GlyphInfoOta> mGlyphCache;

    // Text shader
    GLuint mTextProgram;
    GLint  mTextLocPosition;
    GLint  mTextLocTexCoord;
    GLint  mTextLocColor;
    GLint  mTextLocTexture;

    // Input
    std::vector<int> mInputFds;
    std::set<std::string> mOpenedDevices;
    int mInotifyFd;
    bool mExitRequested;

    // Auto-reboot timer (frames at 30fps)
    int mSuccessTimer;

    // Touch tracking
    int mTouchX;
    int mTouchY;
    bool mTouchDown;
    void handleTouch(int x, int y);

    // Layout metrics (set during render, used by touch)
    float mItemStartY;   // Y position where first menu item starts
    float mItemHeight;   // height per menu item

    // Visual theme (default / PS3 XMB / DSi). Read once in readyToRun() from
    // the sys.gammaos.ota.theme property. Placed last with a default member
    // initializer so it does not disturb the ordered constructor init list.
    OtaTheme mTheme = THEME_DEFAULT;
};

} // namespace android

#endif // GAMMAOS_OTA_MENU_H
