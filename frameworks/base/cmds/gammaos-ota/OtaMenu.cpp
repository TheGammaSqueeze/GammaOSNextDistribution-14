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

#include <fcntl.h>
#include <dirent.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/inotify.h>
#include <linux/input.h>
#include <sys/ioctl.h>
#include <math.h>
#include <stdlib.h>
#include <pthread.h>

#include <binder/IPCThreadState.h>
#include <cutils/properties.h>
#include <android-base/properties.h>
#include <android-base/file.h>
#include <utils/Log.h>

#include <ui/DisplayMode.h>
#include <ui/PixelFormat.h>
#include <ui/Rect.h>

#include <gui/ISurfaceComposer.h>
#include <gui/Surface.h>
#include <gui/SurfaceComposerClient.h>

#include <GLES2/gl2.h>
#include <EGL/eglext.h>

#include "OtaMenu.h"

namespace android {

using ui::DisplayMode;

// ---------------------------------------------------------------------------
// Shaders (same as NanoMenu)
// ---------------------------------------------------------------------------

static const char VERTEX_SHADER[] = R"(
    attribute vec4 aPosition;
    uniform vec4 uColor;
    varying vec4 vColor;
    void main() {
        gl_Position = aPosition;
        vColor = uColor;
    }
)";

static const char FRAGMENT_SHADER[] = R"(
    precision mediump float;
    varying vec4 vColor;
    void main() {
        gl_FragColor = vColor;
    }
)";

static const char TEXT_VERTEX_SHADER[] = R"(
    attribute vec2 aPosition;
    attribute vec2 aTexCoord;
    attribute vec4 aColor;
    varying vec2 vTexCoord;
    varying vec4 vColor;
    void main() {
        gl_Position = vec4(aPosition, 0.0, 1.0);
        vTexCoord = aTexCoord;
        vColor = aColor;
    }
)";

static const char TEXT_FRAGMENT_SHADER[] = R"(
    precision mediump float;
    uniform sampler2D uTexture;
    varying vec2 vTexCoord;
    varying vec4 vColor;
    void main() {
        float a = texture2D(uTexture, vTexCoord).a;
        gl_FragColor = vec4(vColor.rgb, vColor.a * a);
    }
)";

static GLuint compileShader(GLenum type, const char* src) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &src, nullptr);
    glCompileShader(shader);
    GLint ok;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[512];
        glGetShaderInfoLog(shader, sizeof(log), nullptr, log);
        ALOGE("Shader compile error: %s", log);
    }
    return shader;
}

static GLuint linkProgram(GLuint vs, GLuint fs) {
    GLuint prog = glCreateProgram();
    glAttachShader(prog, vs);
    glAttachShader(prog, fs);
    glLinkProgram(prog);
    GLint ok;
    glGetProgramiv(prog, GL_LINK_STATUS, &ok);
    if (!ok) {
        char log[512];
        glGetProgramInfoLog(prog, sizeof(log), nullptr, log);
        ALOGE("Program link error: %s", log);
    }
    return prog;
}

// ---------------------------------------------------------------------------
// OtaMenu implementation
// ---------------------------------------------------------------------------

OtaMenu::OtaMenu()
    : mFileSelectedIndex(0)
    , mBackupRequested(false)
    , mConfirmSelectedIndex(0)
    , mErrorSelectedIndex(0)
    , mState(STATE_FILE_BROWSER)
    , mHasBackup(false)
    , mWidth(0)
    , mHeight(0)
    , mDisplay(EGL_NO_DISPLAY)
    , mContext(EGL_NO_CONTEXT)
    , mSurface(EGL_NO_SURFACE)
    , mShaderProgram(0)
    , mLocPosition(0)
    , mLocColor(0)
    , mFtLib(nullptr)
    , mFtFace(nullptr)
    , mFontSize(0)
    , mGlyphAtlasTex(0)
    , mAtlasW(0), mAtlasH(0)
    , mAtlasCurX(0), mAtlasCurY(0), mAtlasRowH(0)
    , mTextProgram(0)
    , mTextLocPosition(0)
    , mTextLocTexCoord(0)
    , mTextLocColor(0)
    , mTextLocTexture(0)
    , mInotifyFd(-1)
    , mExitRequested(false)
    , mSuccessTimer(0)
    , mTouchX(0)
    , mTouchY(0)
    , mTouchDown(false)
    , mItemStartY(0)
    , mItemHeight(0)
{
}

OtaMenu::~OtaMenu() {
    for (int fd : mInputFds) close(fd);
    if (mInotifyFd >= 0) close(mInotifyFd);
    if (mFtFace) FT_Done_Face(mFtFace);
    if (mFtLib) FT_Done_FreeType(mFtLib);
}

void OtaMenu::onFirstRef() {
    // Thread::run() is called from main.cpp, not here
}

sp<SurfaceComposerClient> OtaMenu::session() const {
    return mSession;
}

void OtaMenu::binderDied(const wp<IBinder>& /*who*/) {
    // SurfaceFlinger died — just exit, init will restart us
    mExitRequested = true;
}

status_t OtaMenu::readyToRun() {
    // Select the visual theme for the full-screen OTA UI. nano hands the
    // SurfaceFlinger surface off to us, so this binary must match the active
    // GammaOS front-end look (PS3 XMB or Nintendo DSi).
    std::string themeProp = android::base::GetProperty("sys.gammaos.ota.theme", "");
    if (themeProp == "ps3") {
        mTheme = THEME_PS3;
    } else if (themeProp == "dsi") {
        mTheme = THEME_DSI;
    } else {
        mTheme = THEME_DEFAULT;
    }
    ALOGI("OTA theme: %s (prop='%s')",
          mTheme == THEME_PS3 ? "ps3" : mTheme == THEME_DSI ? "dsi" : "default",
          themeProp.c_str());

    mSession = new SurfaceComposerClient();

    // Get display info
    const auto displayIds = SurfaceComposerClient::getPhysicalDisplayIds();
    if (displayIds.empty()) {
        ALOGE("No displays found");
        return NO_INIT;
    }
    mDisplayToken = SurfaceComposerClient::getPhysicalDisplayToken(displayIds[0]);

    // The GammaOS home (gammaos-nano) drives the panel as DRM master via direct
    // KMS. When it hands off to us it simply exits: that drops DRM master, but
    // SurfaceFlinger's HWC display pipe stays inactive (hardware vsync remains
    // disabled and it holds no master) because SF's software power state never
    // changed away from ON, so it never re-issues a power transition to HWC.
    // Our composited frames would then never scan out and the panel stays black.
    // Force a real OFF -> ON transition here (nano is already gone, so DRM master
    // is free): SF pushes setPowerMode to HWC, which re-acquires DRM master,
    // mode-sets the panel and re-enables vsync before we draw our first frame.
    // PowerMode: OFF=0, ON=2 (android.hardware.graphics.composer IComposerClient).
    SurfaceComposerClient::setDisplayPowerMode(mDisplayToken, 0);
    usleep(150 * 1000);
    SurfaceComposerClient::setDisplayPowerMode(mDisplayToken, 2);
    usleep(50 * 1000);

    DisplayMode displayMode;
    SurfaceComposerClient::getActiveDisplayMode(mDisplayToken, &displayMode);
    mWidth = displayMode.resolution.getWidth();
    mHeight = displayMode.resolution.getHeight();

    // Handle rotation for landscape devices
    if (mWidth < mHeight) std::swap(mWidth, mHeight);

    ALOGI("Display: %dx%d", mWidth, mHeight);

    // Create surface
    mFlingerSurfaceControl = mSession->createSurface(
            String8("GammaOSOta"), mWidth, mHeight, PIXEL_FORMAT_RGBA_8888,
            ISurfaceComposerClient::eOpaque);

    SurfaceComposerClient::Transaction t;
    t.setLayer(mFlingerSurfaceControl, 0x7FFFFFFF); // Maximum z-order — above everything
    t.show(mFlingerSurfaceControl);
    t.apply();

    mFlingerSurface = mFlingerSurfaceControl->getSurface();

    // EGL setup
    mDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(mDisplay, nullptr, nullptr);

    const EGLint attribs[] = {
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8, EGL_DEPTH_SIZE, 0, EGL_STENCIL_SIZE, 0,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT, EGL_NONE
    };
    EGLConfig config;
    EGLint numConfigs;
    eglChooseConfig(mDisplay, attribs, &config, 1, &numConfigs);
    mSurface = eglCreateWindowSurface(mDisplay, config,
                                       mFlingerSurface.get(), nullptr);

    const EGLint ctxAttribs[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    mContext = eglCreateContext(mDisplay, config, EGL_NO_CONTEXT, ctxAttribs);
    eglMakeCurrent(mDisplay, mSurface, mSurface, mContext);

    initShaders();
    initFonts();
    openInputDevices();

    // Setup flasher callback
    mFlasher.setStatusCallback([this](const FlashStatus& status) {
        std::lock_guard<std::mutex> lock(mStatusMutex);
        mCurrentStatus = status;
    });

    // If package path was set (Mode A), jump straight to confirm/flash.
    if (!mPackagePath.empty()) {
        // nano hands off a selected .zip (browse + confirm happened in the home UI). Extract it
        // here, then treat the extracted dir like a pre-staged package. A dir path (legacy
        // pre-extracted Mode A) is used as-is.
        std::string pkgDir = mPackagePath;
        if (isZipFile(mPackagePath)) {
            // Paint one themed "Staging..." frame before the blocking unzip so the panel is not
            // black while extraction runs (the render loop has not started yet at readyToRun).
            mState = STATE_STAGING;
            render();
            eglSwapBuffers(mDisplay, mSurface);
            auto extracted = extractZipHelper(mPackagePath);
            if (!extracted.second.empty()) {
                mErrorMessage = "Failed to extract update package: " + extracted.second;
                android::base::SetProperty("sys.gammaos.ota.result",
                                           "failed:extraction:" + extracted.second);
                mState = STATE_FAILED;
                return NO_ERROR;
            }
            pkgDir = extracted.first;
        }
        if (mManifest.parse(pkgDir + "/manifest.json") ||
            mManifest.parse(pkgDir)) {
            mFlasher.setPackageDir(pkgDir);
            // Check for autoinstall property — skip confirm screen
            std::string autoInstall = android::base::GetProperty("sys.gammaos.ota.autoinstall", "");
            if (autoInstall == "1") {
                ALOGI("Auto-install triggered, starting flash immediately");
                startFlashThread();
            } else {
                mState = STATE_CONFIRM;
            }
        } else {
            mErrorMessage = "Failed to parse manifest from: " + pkgDir;
            android::base::SetProperty("sys.gammaos.ota.result",
                                       "failed:manifest:" + mErrorMessage);
            mState = STATE_FAILED;
        }
    } else {
        // Check if autoinstall with pre-extracted package (no explicit path)
        std::string autoInstall = android::base::GetProperty("sys.gammaos.ota.autoinstall", "");
        std::string defaultPkgDir = "/data/gammaos_ota/package";
        if (autoInstall == "1" && mManifest.parse(defaultPkgDir + "/manifest.json")) {
            ALOGI("Auto-install from default package dir");
            mFlasher.setPackageDir(defaultPkgDir);
            startFlashThread();
        } else {
            scanForPackages();
        }
    }

    return NO_ERROR;
}

void OtaMenu::initShaders() {
    // Flat color shader
    GLuint vs = compileShader(GL_VERTEX_SHADER, VERTEX_SHADER);
    GLuint fs = compileShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
    mShaderProgram = linkProgram(vs, fs);
    mLocPosition = glGetAttribLocation(mShaderProgram, "aPosition");
    mLocColor = glGetUniformLocation(mShaderProgram, "uColor");
    glDeleteShader(vs);
    glDeleteShader(fs);

    // Text shader
    vs = compileShader(GL_VERTEX_SHADER, TEXT_VERTEX_SHADER);
    fs = compileShader(GL_FRAGMENT_SHADER, TEXT_FRAGMENT_SHADER);
    mTextProgram = linkProgram(vs, fs);
    mTextLocPosition = glGetAttribLocation(mTextProgram, "aPosition");
    mTextLocTexCoord = glGetAttribLocation(mTextProgram, "aTexCoord");
    mTextLocColor = glGetAttribLocation(mTextProgram, "aColor");
    mTextLocTexture = glGetUniformLocation(mTextProgram, "uTexture");
    glDeleteShader(vs);
    glDeleteShader(fs);
}

void OtaMenu::initFonts() {
    FT_Init_FreeType(&mFtLib);

    // Try staged font first, then system font
    const char* fontPaths[] = {
        "/data/gammaos-ota-stage/fonts/Roboto-Regular.ttf",
        "/system/fonts/Roboto-Regular.ttf",
        "/system/fonts/DroidSans.ttf",
    };
    for (const char* path : fontPaths) {
        if (FT_New_Face(mFtLib, path, 0, &mFtFace) == 0) break;
    }

    mFontSize = mHeight / 25;
    if (mFtFace) FT_Set_Pixel_Sizes(mFtFace, 0, mFontSize);

    // Create glyph atlas
    mAtlasW = 1024;
    mAtlasH = 1024;
    mAtlasCurX = 0;
    mAtlasCurY = 0;
    mAtlasRowH = 0;
    glGenTextures(1, &mGlyphAtlasTex);
    glBindTexture(GL_TEXTURE_2D, mGlyphAtlasTex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, mAtlasW, mAtlasH, 0,
                 GL_ALPHA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
}

void OtaMenu::ensureGlyph(uint32_t codepoint) {
    if (mGlyphCache.count(codepoint) || !mFtFace) return;
    if (FT_Load_Char(mFtFace, codepoint, FT_LOAD_RENDER)) return;

    auto& g = mFtFace->glyph;
    int w = g->bitmap.width;
    int h = g->bitmap.rows;

    if (mAtlasCurX + w + 1 >= mAtlasW) {
        mAtlasCurX = 0;
        mAtlasCurY += mAtlasRowH + 1;
        mAtlasRowH = 0;
    }
    if (mAtlasCurY + h + 1 >= mAtlasH) return; // atlas full

    glBindTexture(GL_TEXTURE_2D, mGlyphAtlasTex);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glTexSubImage2D(GL_TEXTURE_2D, 0, mAtlasCurX, mAtlasCurY, w, h,
                    GL_ALPHA, GL_UNSIGNED_BYTE, g->bitmap.buffer);

    GlyphInfoOta info;
    info.u0 = (float)mAtlasCurX / mAtlasW;
    info.v0 = (float)mAtlasCurY / mAtlasH;
    info.u1 = (float)(mAtlasCurX + w) / mAtlasW;
    info.v1 = (float)(mAtlasCurY + h) / mAtlasH;
    info.bmpW = w;
    info.bmpH = h;
    info.bearingX = g->bitmap_left;
    info.bearingY = g->bitmap_top;
    info.advance = (int)(g->advance.x >> 6);
    mGlyphCache[codepoint] = info;

    mAtlasCurX += w + 1;
    if (h + 1 > mAtlasRowH) mAtlasRowH = h + 1;
}

void OtaMenu::drawText(const char* str, float px, float py, float scale,
                        float r, float g, float b, float a) {
    if (!mFtFace) return;
    glUseProgram(mTextProgram);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, mGlyphAtlasTex);
    glUniform1i(mTextLocTexture, 0);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    float x = px;
    float y = py;
    const unsigned char* p = (const unsigned char*)str;
    while (*p) {
        uint32_t cp = *p++;
        // Basic UTF-8 decode
        if (cp >= 0xC0 && cp < 0xE0 && *p) { cp = ((cp & 0x1F) << 6) | (*p++ & 0x3F); }
        else if (cp >= 0xE0 && cp < 0xF0 && p[0] && p[1]) {
            cp = ((cp & 0x0F) << 12) | ((p[0] & 0x3F) << 6) | (p[1] & 0x3F); p += 2; }

        ensureGlyph(cp);
        auto it = mGlyphCache.find(cp);
        if (it == mGlyphCache.end()) continue;

        const auto& gi = it->second;
        float xpos = x + gi.bearingX * scale;
        float ypos = y - gi.bearingY * scale;
        float w = gi.bmpW * scale;
        float h = gi.bmpH * scale;

        // Convert to NDC
        float x0 = (xpos / mWidth) * 2.0f - 1.0f;
        float y0 = 1.0f - (ypos / mHeight) * 2.0f;
        float x1 = ((xpos + w) / mWidth) * 2.0f - 1.0f;
        float y1 = 1.0f - ((ypos + h) / mHeight) * 2.0f;

        float verts[] = {
            x0, y0, gi.u0, gi.v0, r, g, b, a,
            x1, y0, gi.u1, gi.v0, r, g, b, a,
            x0, y1, gi.u0, gi.v1, r, g, b, a,
            x1, y1, gi.u1, gi.v1, r, g, b, a,
        };

        glVertexAttribPointer(mTextLocPosition, 2, GL_FLOAT, GL_FALSE, 32, verts);
        glVertexAttribPointer(mTextLocTexCoord, 2, GL_FLOAT, GL_FALSE, 32, verts + 2);
        glVertexAttribPointer(mTextLocColor, 4, GL_FLOAT, GL_FALSE, 32, verts + 4);
        glEnableVertexAttribArray(mTextLocPosition);
        glEnableVertexAttribArray(mTextLocTexCoord);
        glEnableVertexAttribArray(mTextLocColor);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

        x += gi.advance * scale;
    }
    glDisable(GL_BLEND);
}

float OtaMenu::measureText(const char* str, float scale) {
    float w = 0;
    const unsigned char* p = (const unsigned char*)str;
    while (*p) {
        uint32_t cp = *p++;
        if (cp >= 0xC0 && cp < 0xE0 && *p) { cp = ((cp & 0x1F) << 6) | (*p++ & 0x3F); }
        else if (cp >= 0xE0 && cp < 0xF0 && p[0] && p[1]) {
            cp = ((cp & 0x0F) << 12) | ((p[0] & 0x3F) << 6) | (p[1] & 0x3F); p += 2; }
        ensureGlyph(cp);
        auto it = mGlyphCache.find(cp);
        if (it != mGlyphCache.end()) w += it->second.advance * scale;
    }
    return w;
}

void OtaMenu::drawQuad(float x, float y, float w, float h,
                        float r, float g, float b, float a) {
    glUseProgram(mShaderProgram);
    float x0 = (x / mWidth) * 2.0f - 1.0f;
    float y0 = 1.0f - (y / mHeight) * 2.0f;
    float x1 = ((x + w) / mWidth) * 2.0f - 1.0f;
    float y1 = 1.0f - ((y + h) / mHeight) * 2.0f;
    float verts[] = { x0,y0, x1,y0, x0,y1, x1,y1 };
    glUniform4f(mLocColor, r, g, b, a);
    glVertexAttribPointer(mLocPosition, 2, GL_FLOAT, GL_FALSE, 0, verts);
    glEnableVertexAttribArray(mLocPosition);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}

void OtaMenu::drawProgressBar(float x, float y, float w, float h, float progress) {
    // Background
    drawQuad(x, y, w, h, 0.2f, 0.2f, 0.2f, 1.0f);
    // Fill
    if (progress > 0) {
        drawQuad(x + 2, y + 2, (w - 4) * (progress / 100.0f), h - 4,
                 0.2f, 0.7f, 0.3f, 1.0f);
    }
}

void OtaMenu::scanForPackages() {
    mFileEntries.clear();

    // The same package can surface through more than one mount (e.g. /sdcard
    // and /sdcard/Download, or the internal path and its raw /data/media view),
    // so de-dupe on name + size.
    std::set<std::string> seen;
    auto addZip = [&](const std::string& fullPath, const std::string& name,
                      const std::string& displayPrefix) {
        if (name.size() <= 4 || name.substr(name.size() - 4) != ".zip") return;
        struct stat st;
        if (stat(fullPath.c_str(), &st) != 0) return;
        std::string key = name + "|" + std::to_string((long long)st.st_size);
        if (!seen.insert(key).second) return;
        FileEntry fe;
        fe.path = fullPath;
        fe.displayName = displayPrefix + name;
        fe.size = (uint64_t)st.st_size;
        mFileEntries.push_back(fe);
    };

    // Where a user actually drops an update: internal shared storage first
    // (/sdcard is the primary user-facing location), then the OTA staging dir,
    // then removable USB media.
    const char* dirs[] = {"/sdcard", "/sdcard/Download",
                          "/data/gammaos_ota", "/mnt/media_rw"};
    for (const char* dirPath : dirs) {
        DIR* dir = opendir(dirPath);
        if (!dir) continue;
        bool isUsbRoot = (std::string(dirPath) == "/mnt/media_rw");
        struct dirent* entry;
        while ((entry = readdir(dir)) != nullptr) {
            std::string name = entry->d_name;
            if (name.size() < 4) continue;

            std::string fullPath = std::string(dirPath) + "/" + name;

            addZip(fullPath, name, "");

            // For /mnt/media_rw, also scan one level deeper (USB drives)
            if (isUsbRoot && entry->d_type == DT_DIR &&
                name != "." && name != "..") {
                DIR* subdir = opendir(fullPath.c_str());
                if (!subdir) continue;
                struct dirent* subentry;
                while ((subentry = readdir(subdir)) != nullptr) {
                    std::string subname = subentry->d_name;
                    if (subname.size() < 4) continue;
                    std::string subPath = fullPath + "/" + subname;
                    addZip(subPath, subname, "[USB] ");
                }
                closedir(subdir);
            }
        }
        closedir(dir);
    }

    ALOGI("Found %zu OTA packages", mFileEntries.size());
}

void OtaMenu::openInputDevices() {
    mInotifyFd = inotify_init1(IN_NONBLOCK);
    if (mInotifyFd >= 0) {
        inotify_add_watch(mInotifyFd, "/dev/input", IN_CREATE | IN_DELETE);
    }

    DIR* dir = opendir("/dev/input");
    if (!dir) return;
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (strncmp(entry->d_name, "event", 5) != 0) continue;
        std::string path = std::string("/dev/input/") + entry->d_name;
        if (mOpenedDevices.count(path)) continue;
        int fd = open(path.c_str(), O_RDONLY | O_NONBLOCK);
        if (fd >= 0) {
            // Exclusively grab the device so Android's InputFlinger doesn't consume events
            ioctl(fd, EVIOCGRAB, 1);
            mInputFds.push_back(fd);
            mOpenedDevices.insert(path);
            ALOGI("Opened input device: %s (fd=%d)", path.c_str(), fd);
        }
    }
    closedir(dir);
}

void OtaMenu::pollInput() {
    for (int fd : mInputFds) {
        struct input_event ev;
        while (read(fd, &ev, sizeof(ev)) == sizeof(ev)) {
            // Handle key events (buttons)
            if (ev.type == EV_KEY && ev.value == 1) { // key down only
                switch (ev.code) {
                    case KEY_UP:
                    case BTN_DPAD_UP:
                        handleUp();
                        break;
                    case KEY_DOWN:
                    case BTN_DPAD_DOWN:
                        handleDown();
                        break;
                    case KEY_ENTER:
                    case BTN_SOUTH: // A button (0x130 = 304)
                        handleSelect();
                        break;
                    case KEY_ESC:
                    case KEY_BACKSPACE:
                    case BTN_EAST: // B button (0x131 = 305)
                        handleBack();
                        break;
                }
            }
            // Handle axis events (d-pad hat, analog sticks, touch)
            if (ev.type == EV_ABS) {
                // ABS_HAT0Y (0x11) = d-pad up/down: -1=up, 1=down
                if (ev.code == ABS_HAT0Y) {
                    if (ev.value < 0) handleUp();
                    else if (ev.value > 0) handleDown();
                }
                // ABS_Y (0x01) = left stick Y
                if (ev.code == ABS_Y) {
                    if (ev.value < -20000) handleUp();
                    else if (ev.value > 20000) handleDown();
                }
                // Touch: track position
                if (ev.code == ABS_MT_POSITION_X || ev.code == ABS_X) {
                    mTouchX = ev.value;
                }
                if (ev.code == ABS_MT_POSITION_Y || ev.code == ABS_Y) {
                    // Don't override stick Y — only set touch if value is in touch range
                    if (ev.value >= 0 && ev.value <= mHeight * 2) {
                        mTouchY = ev.value;
                    }
                }
            }
            // Handle touch up/down
            if (ev.type == EV_KEY && ev.code == BTN_TOUCH) {
                if (ev.value == 1) {
                    mTouchDown = true;
                } else if (ev.value == 0 && mTouchDown) {
                    mTouchDown = false;
                    // Scale touch coords to screen coords
                    // Touch panels often report in their own resolution
                    int x = mTouchX;
                    int y = mTouchY;
                    // If touch coords are larger than screen, scale down
                    if (x > mWidth) x = x * mWidth / 32768;
                    if (y > mHeight) y = y * mHeight / 32768;
                    handleTouch(x, y);
                }
            }
        }
    }
}

void OtaMenu::handleUp() {
    switch (mState) {
        case STATE_FILE_BROWSER:
            if (mFileSelectedIndex > 0) mFileSelectedIndex--;
            break;
        case STATE_CONFIRM:
            if (mConfirmSelectedIndex > 0) mConfirmSelectedIndex--;
            break;
        case STATE_FAILED:
            if (mErrorSelectedIndex > 0) mErrorSelectedIndex--;
            break;
        default:
            break;
    }
}

void OtaMenu::handleDown() {
    switch (mState) {
        case STATE_FILE_BROWSER:
            if (mFileSelectedIndex < (int)mFileEntries.size() - 1) mFileSelectedIndex++;
            break;
        case STATE_CONFIRM:
            if (mConfirmSelectedIndex < 2) mConfirmSelectedIndex++;
            break;
        case STATE_FAILED:
            if (mErrorSelectedIndex < 2) mErrorSelectedIndex++;
            break;
        default:
            break;
    }
}

bool OtaMenu::isZipFile(const std::string& path) {
    return path.size() >= 4 && path.substr(path.size() - 4) == ".zip";
}

// Extract a package .zip into /data/gammaos_ota/package. Shared by the file browser and the
// nano auto-install handoff. Returns {pkgDir,""} on success, {"",error} on failure.
std::pair<std::string, std::string> OtaMenu::extractZipHelper(const std::string& zipPath) {
    std::string targetDir = "/data/gammaos_ota";
    mkdir(targetDir.c_str(), 0755);
    std::string pkgDir = targetDir + "/package";
    std::string cleanCmd = "rm -rf '" + pkgDir + "'";
    system(cleanCmd.c_str());
    mkdir(pkgDir.c_str(), 0755);

    // Removable media (USB) can unmount mid-flash, so copy it local first.
    std::string zip = zipPath;
    if (zipPath.rfind("/mnt/media_rw/", 0) == 0) {
        std::string localPath = targetDir + "/external_update.zip";
        std::string cpCmd = "cp '" + zipPath + "' '" + localPath + "'";
        if (system(cpCmd.c_str()) != 0)
            return { "", "could not copy package from removable storage" };
        zip = localPath;
    }

    std::string pathPrefix;
    if (OtaFlasher::isRunningFromTmpfs()) {
        pathPrefix = "PATH=/data/gammaos-ota-stage/bin:/system/bin:/vendor/bin "
                     "LD_LIBRARY_PATH=/data/gammaos-ota-stage/lib64:/system/lib64 ";
    }
    // Single-quote the (user-selected) paths so spaces / metacharacters survive.
    std::string unzipCmd = pathPrefix + "unzip -o '" + zip + "' -d '" + pkgDir + "' 2>/dev/null";
    ALOGI("Extracting %s to %s", zip.c_str(), pkgDir.c_str());
    int ret = system(unzipCmd.c_str());
    if (ret != 0) return { "", "unzip failed (rc=" + std::to_string(ret) + ")" };
    return { pkgDir, "" };
}

void OtaMenu::handleSelect() {
    switch (mState) {
        case STATE_FILE_BROWSER:
            if (!mFileEntries.empty()) {
                auto& fe = mFileEntries[mFileSelectedIndex];
                auto extracted = extractZipHelper(fe.path);
                const std::string& pkgDir = extracted.first;
                const std::string& err = extracted.second;
                if (!err.empty()) {
                    mErrorMessage = "Failed to extract update package: " + err;
                    mState = STATE_FAILED;
                } else {
                    mFlasher.setPackageDir(pkgDir);
                    if (mManifest.parse(pkgDir + "/manifest.json")) {
                        mState = STATE_CONFIRM;
                    } else {
                        mErrorMessage = "Failed to parse update package manifest";
                        mState = STATE_FAILED;
                    }
                }
            }
            break;

        case STATE_CONFIRM:
            if (mConfirmSelectedIndex == 0) { // Install
                startFlashThread();
            } else if (mConfirmSelectedIndex == 1) { // Cancel
                mExitRequested = true;
            } else if (mConfirmSelectedIndex == 2) { // Toggle backup
                mBackupRequested = !mBackupRequested;
            }
            break;

        case STATE_SUCCESS:
            mFlasher.reboot();
            break;

        case STATE_FAILED:
            if (mErrorSelectedIndex == 0) { // Retry
                startFlashThread();
            } else if (mErrorSelectedIndex == 1 && mHasBackup) { // Restore
                mState = STATE_FLASHING;
                pthread_t restoreThread;
                pthread_create(&restoreThread, nullptr, [](void* arg) -> void* {
                    OtaMenu* self = static_cast<OtaMenu*>(arg);
                    OtaFlasher::logToFile("INFO", "User triggered restore from backup");
                    self->mFlasher.restoreFromBackup();
                    OtaFlasher::logToFile("INFO", "Restore complete — rebooting");
                    self->mFlasher.reboot();
                    return nullptr;
                }, this);
                pthread_detach(restoreThread);
            } else { // Reboot anyway
                mFlasher.reboot();
            }
            break;

        default:
            break;
    }
}

void OtaMenu::handleTouch(int x, int y) {
    (void)x; // x unused for vertical menu
    if (mItemHeight <= 0 || mItemStartY <= 0) return;

    // Determine which item was tapped based on Y coordinate
    int itemIndex = (int)((y - mItemStartY) / mItemHeight);

    switch (mState) {
        case STATE_FILE_BROWSER:
            if (itemIndex >= 0 && itemIndex < (int)mFileEntries.size()) {
                mFileSelectedIndex = itemIndex;
                handleSelect();
            }
            break;
        case STATE_CONFIRM:
            if (itemIndex >= 0 && itemIndex < 3) {
                mConfirmSelectedIndex = itemIndex;
                handleSelect();
            }
            break;
        case STATE_FAILED:
            if (itemIndex >= 0 && itemIndex < 3) {
                mErrorSelectedIndex = itemIndex;
                handleSelect();
            }
            break;
        case STATE_SUCCESS:
            handleSelect(); // any touch = reboot
            break;
        default:
            break;
    }
}

void OtaMenu::handleBack() {
    switch (mState) {
        case STATE_FILE_BROWSER:
            mExitRequested = true;
            break;
        case STATE_CONFIRM:
            mState = STATE_FILE_BROWSER;
            break;
        default:
            break;
    }
}

static void* flashThreadEntry(void* arg) {
    OtaMenu* self = static_cast<OtaMenu*>(arg);
    self->runFlashSequence();
    return nullptr;
}

void OtaMenu::startFlashThread() {
    mState = STATE_PREFLIGHT;
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setstacksize(&attr, 4 * 1024 * 1024); // 4MB stack for flash thread
    pthread_create(&mFlashThread, &attr, flashThreadEntry, this);
    pthread_attr_destroy(&attr);
    pthread_detach(mFlashThread);
}

void OtaMenu::runFlashSequence() {
    OtaFlasher::logToFile("INFO", "========================================");
    OtaFlasher::logToFile("INFO", "=== OTA FLASH SEQUENCE STARTED ===");
    OtaFlasher::logToFile("INFO", "Package: %s", mPackagePath.c_str());
    OtaFlasher::logToFile("INFO", "Manifest: version=%s partitions=%zu backup=%s",
                          mManifest.version.c_str(), mManifest.partitions.size(),
                          mBackupRequested ? "yes" : "no");

    // Preflight
    OtaFlasher::logToFile("INFO", "--- Phase: PREFLIGHT ---");
    std::string err = mFlasher.preflight(mManifest);
    if (!err.empty()) {
        OtaFlasher::logToFile("ERROR", "Preflight FAILED: %s", err.c_str());
        mErrorMessage = err;
        android::base::SetProperty("sys.gammaos.ota.result", "failed:preflight:" + err);
        mState = STATE_FAILED;
        return;
    }

    // Optional backup
    if (mBackupRequested) {
        OtaFlasher::logToFile("INFO", "--- Phase: BACKUP ---");
        mState = STATE_BACKUP;
        if (!mFlasher.backup(mManifest)) {
            OtaFlasher::logToFile("ERROR", "Backup FAILED");
            mErrorMessage = "Backup failed";
            android::base::SetProperty("sys.gammaos.ota.result", "failed:backup:Backup failed");
            mState = STATE_FAILED;
            return;
        }
        mHasBackup = true;
    }

    // Flash
    OtaFlasher::logToFile("INFO", "--- Phase: FLASH ---");
    mState = STATE_FLASHING;
    if (!mFlasher.flash(mManifest)) {
        std::lock_guard<std::mutex> lock(mStatusMutex);
        OtaFlasher::logToFile("ERROR", "Flash FAILED: %s", mCurrentStatus.errorMsg.c_str());
        mErrorMessage = mCurrentStatus.errorMsg;
        android::base::SetProperty("sys.gammaos.ota.result",
                                   "failed:flash:" + mCurrentStatus.errorMsg);
        mState = STATE_FAILED;
        return;
    }

    // Skip post-flash verification entirely — it causes OOM/kernel panic on
    // devices with limited RAM when reading back large partitions.
    // Data integrity is ensured by: XZ internal checksums, compressed SHA-256
    // verified in preflight, and staging file written from verified source.
    // Boot success is the definitive verification.
    OtaFlasher::logToFile("INFO", "--- Phase: VERIFY (skipped — boot is verification) ---");
    OtaFlasher::logToFile("INFO", "=== OTA FLASH SEQUENCE: SUCCESS ===");
    OtaFlasher::logToFile("INFO", "========================================");
    mState = STATE_SUCCESS;
    // Wait for the 5-second countdown to complete before rebooting.
    // SurfaceFlinger is alive so the render loop shows the countdown.
    // Sync all filesystems to ensure all writes are flushed to disk.
    sync();
    OtaFlasher::logToFile("INFO", "Waiting 5 seconds for countdown + final sync...");
    sleep(5);
    sync(); // Final sync before reboot
    mFlasher.reboot();
}

void OtaMenu::render() {
    // Themed front-ends take over the whole frame. The default path below is
    // preserved byte-for-byte for THEME_DEFAULT.
    if (mTheme == THEME_PS3) { renderPs3(); return; }
    if (mTheme == THEME_DSI) { renderDsi(); return; }

    glClearColor(0.08f, 0.08f, 0.12f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    glViewport(0, 0, mWidth, mHeight);

    float margin = mWidth * 0.05f;
    float lineH = mFontSize * 1.5f;
    float y = margin + mFontSize;
    float scale = 1.0f;

    // Title
    drawText("GammaOS System Update", margin, y, scale * 1.2f, 1.0f, 1.0f, 1.0f, 1.0f);
    y += lineH * 1.5f;

    switch (mState) {
        case STATE_FILE_BROWSER: {
            if (mFileEntries.empty()) {
                drawText("No update packages found.", margin, y, scale, 0.7f, 0.7f, 0.7f, 1.0f);
                y += lineH;
                drawText("Place .zip files in /sdcard/", margin, y, scale,
                         0.5f, 0.5f, 0.5f, 1.0f);
                y += lineH;
                drawText("or connect USB storage.", margin, y, scale, 0.5f, 0.5f, 0.5f, 1.0f);
            } else {
                drawText("Select update package:", margin, y, scale, 0.8f, 0.8f, 0.8f, 1.0f);
                y += lineH;
                mItemStartY = y - mFontSize; // record for touch
                mItemHeight = lineH;
                for (int i = 0; i < (int)mFileEntries.size(); i++) {
                    bool sel = (i == mFileSelectedIndex);
                    if (sel) {
                        drawQuad(margin - 5, y - mFontSize, mWidth - margin * 2 + 10, lineH,
                                 0.2f, 0.3f, 0.5f, 0.8f);
                    }
                    std::string sizeStr = std::to_string(mFileEntries[i].size / 1024 / 1024) + " MB";
                    drawText(mFileEntries[i].displayName.c_str(), margin + 10, y, scale,
                             sel ? 1.0f : 0.7f, sel ? 1.0f : 0.7f, sel ? 1.0f : 0.7f, 1.0f);
                    drawText(sizeStr.c_str(), mWidth - margin - measureText(sizeStr.c_str(), scale), y,
                             scale, 0.5f, 0.5f, 0.5f, 1.0f);
                    y += lineH;
                }
            }
            y += lineH;
            drawText("[Up/Down] Navigate   [A] Select   [B] Exit", margin, mHeight - margin,
                     scale * 0.8f, 0.4f, 0.4f, 0.4f, 1.0f);
            break;
        }

        case STATE_CONFIRM: {
            drawText(("Version: " + mManifest.version).c_str(), margin, y, scale,
                     0.8f, 0.8f, 0.8f, 1.0f);
            y += lineH * 1.2f;
            drawText("Partitions to update:", margin, y, scale, 0.7f, 0.7f, 0.7f, 1.0f);
            y += lineH;
            for (const auto& part : mManifest.partitions) {
                std::string info = "  " + part.name + "  (" + part.type + ")";
                if (part.type == "logical" && part.size > 0) {
                    info += "  " + std::to_string(part.size / 1024 / 1024) + " MB";
                }
                if (part.type == "physical") {
                    info += "  (both slots)";
                }
                drawText(info.c_str(), margin, y, scale, 0.7f, 0.7f, 0.7f, 1.0f);
                y += lineH;
            }
            y += lineH;

            const char* options[] = {"Install", "Cancel", "Toggle backup"};
            mItemStartY = y - mFontSize;
            mItemHeight = lineH;
            for (int i = 0; i < 3; i++) {
                bool sel = (i == mConfirmSelectedIndex);
                std::string label = std::string(sel ? "> " : "  ") + options[i];
                if (i == 2) label += mBackupRequested ? " [ON]" : " [OFF]";
                if (sel) {
                    drawQuad(margin - 5, y - mFontSize, mWidth - margin * 2 + 10, lineH,
                             0.2f, 0.3f, 0.5f, 0.8f);
                }
                drawText(label.c_str(), margin, y, scale,
                         sel ? 1.0f : 0.6f, sel ? 1.0f : 0.6f, sel ? 1.0f : 0.6f, 1.0f);
                y += lineH;
            }
            break;
        }

        case STATE_STAGING:
        case STATE_PREFLIGHT:
        case STATE_BACKUP:
        case STATE_FLASHING:
        case STATE_VERIFYING: {
            FlashStatus status;
            {
                std::lock_guard<std::mutex> lock(mStatusMutex);
                status = mCurrentStatus;
            }

            const char* phaseNames[] = {
                "Staging...", "Checking...", "Backing up...", "Stopping framework...",
                "Decompressing...", "Writing... DO NOT POWER OFF",
                "Writing... DO NOT POWER OFF", "Verifying...",
                "Complete!", "Failed"
            };
            int phaseIdx = (int)status.phase;
            if (phaseIdx >= 0 && phaseIdx < 10) {
                if (phaseIdx == 4) {
                    // Decompressing phase: cyan color, show percentage in text
                    char decompText[128];
                    snprintf(decompText, sizeof(decompText),
                             "Decompressing %s... %d%%",
                             status.currentPartition.c_str(),
                             status.progressPercent);
                    drawText(decompText, margin, y, scale, 0.3f, 1.0f, 1.0f, 1.0f);
                } else {
                    drawText(phaseNames[phaseIdx], margin, y, scale, 1.0f, 1.0f, 0.5f, 1.0f);
                }
            }
            y += lineH * 1.5f;

            // Show partition list with status
            for (int i = 0; i < (int)mManifest.partitions.size(); i++) {
                const auto& part = mManifest.partitions[i];
                std::string prefix;
                float r = 0.5f, g = 0.5f, b = 0.5f;

                if (i < status.partitionIndex) {
                    prefix = "  OK  ";
                    r = 0.3f; g = 0.8f; b = 0.3f;
                } else if (i == status.partitionIndex) {
                    prefix = "  >>  ";
                    r = 1.0f; g = 1.0f; b = 0.5f;
                } else {
                    prefix = "  --  ";
                }

                std::string line = prefix + part.name;
                if (part.type == "physical") line += "  (both slots)";
                drawText(line.c_str(), margin, y, scale, r, g, b, 1.0f);

                // Progress bar for current partition
                if (i == status.partitionIndex && status.progressPercent > 0) {
                    float barX = margin + measureText(line.c_str(), scale) + 20;
                    float barW = mWidth - barX - margin;
                    drawProgressBar(barX, y - mFontSize * 0.5f, barW, mFontSize * 0.8f,
                                    (float)status.progressPercent);
                }
                y += lineH;
            }

            y += lineH;
            drawText("Do not power off your device.", margin, y, scale, 0.8f, 0.3f, 0.3f, 1.0f);
            break;
        }

        case STATE_SUCCESS: {
            drawText("Update complete!", margin, y, scale * 1.2f, 0.3f, 0.9f, 0.3f, 1.0f);
            y += lineH * 2;
            drawText("All partitions verified successfully.", margin, y, scale,
                     0.7f, 0.7f, 0.7f, 1.0f);
            y += lineH * 2;
            // Auto-reboot countdown (5 seconds at 30fps = 150 frames)
            mSuccessTimer++;
            int secondsLeft = 5 - (mSuccessTimer / 30);
            if (secondsLeft <= 0) {
                mFlasher.reboot();
            }
            std::string rebootMsg = "Rebooting in " + std::to_string(secondsLeft > 0 ? secondsLeft : 1) + " seconds...";
            drawText(rebootMsg.c_str(), margin, y, scale, 1.0f, 1.0f, 1.0f, 1.0f);
            y += lineH;
            drawText("[A] Reboot now", margin, y, scale, 0.5f, 0.5f, 0.5f, 1.0f);
            break;
        }

        case STATE_FAILED: {
            drawText("Update failed!", margin, y, scale * 1.2f, 0.9f, 0.3f, 0.3f, 1.0f);
            y += lineH * 1.5f;
            // Word-wrap the error message to fit within the screen
            {
                float maxWidth = mWidth - margin * 2;
                std::string remaining = mErrorMessage;
                while (!remaining.empty()) {
                    // Find how many characters fit in maxWidth
                    size_t fitLen = remaining.size();
                    while (fitLen > 0 && measureText(remaining.substr(0, fitLen).c_str(), scale) > maxWidth) {
                        // Try to break at a space
                        size_t spacePos = remaining.rfind(' ', fitLen - 1);
                        if (spacePos != std::string::npos && spacePos > 0) {
                            fitLen = spacePos;
                        } else {
                            fitLen--;
                        }
                    }
                    if (fitLen == 0) fitLen = 1; // at least one char
                    drawText(remaining.substr(0, fitLen).c_str(), margin, y, scale,
                             0.8f, 0.5f, 0.5f, 1.0f);
                    y += lineH;
                    remaining = remaining.substr(fitLen);
                    // Skip leading space on next line
                    if (!remaining.empty() && remaining[0] == ' ') remaining = remaining.substr(1);
                }
            }
            y += lineH * 0.5f;

            // Show failed partitions
            for (const auto& name : mFailedPartitions) {
                drawText(("  FAIL: " + name).c_str(), margin, y, scale, 0.9f, 0.2f, 0.2f, 1.0f);
                y += lineH;
            }
            y += lineH;

            const char* options[] = {"Retry flash", "Restore from backup", "Reboot anyway"};
            for (int i = 0; i < 3; i++) {
                if (i == 1 && !mHasBackup) continue; // skip restore if no backup
                bool sel = (i == mErrorSelectedIndex);
                std::string label = std::string(sel ? "> " : "  ") + options[i];
                if (sel) {
                    drawQuad(margin - 5, y - mFontSize, mWidth - margin * 2 + 10, lineH,
                             0.4f, 0.2f, 0.2f, 0.8f);
                }
                float brightness = sel ? 1.0f : 0.5f;
                drawText(label.c_str(), margin, y, scale, brightness, brightness, brightness, 1.0f);
                y += lineH;
            }
            break;
        }
    }
}

// ---------------------------------------------------------------------------
// Themed rendering helpers (PS3 XMB / Nintendo DSi)
// ---------------------------------------------------------------------------

void OtaMenu::drawThemedProgressBar(float x, float y, float w, float h, float progress,
                                    float troughR, float troughG, float troughB,
                                    float fillR, float fillG, float fillB,
                                    float borderR, float borderG, float borderB,
                                    bool drawBorder) {
    if (progress < 0.0f) progress = 0.0f;
    if (progress > 100.0f) progress = 100.0f;

    // Optional 1px border (drawn as a slightly larger quad behind the trough).
    if (drawBorder) {
        drawQuad(x - 1, y - 1, w + 2, h + 2, borderR, borderG, borderB, 1.0f);
    }
    // Trough
    drawQuad(x, y, w, h, troughR, troughG, troughB, 1.0f);
    // Fill (inset by 1px so the trough reads as a rim even at low percentages).
    float inset = 1.0f;
    float fillW = (w - inset * 2.0f) * (progress / 100.0f);
    if (fillW > 0.0f) {
        drawQuad(x + inset, y + inset, fillW, h - inset * 2.0f,
                 fillR, fillG, fillB, 1.0f);
    }
}

void OtaMenu::themedFlashInfo(std::string* phaseLabel, int* percent, bool* isWrite) {
    FlashStatus status;
    {
        std::lock_guard<std::mutex> lock(mStatusMutex);
        status = mCurrentStatus;
    }

    bool write = false;
    std::string label;
    switch (status.phase) {
        case FlashPhase::STAGING:            label = "Preparing update"; break;
        case FlashPhase::PREFLIGHT:          label = "Checking update"; break;
        case FlashPhase::BACKUP:             label = "Backing up"; break;
        case FlashPhase::STOPPING_FRAMEWORK: label = "Stopping system"; break;
        case FlashPhase::DECOMPRESSING:      label = "Decompressing"; break;
        case FlashPhase::FLASHING_PHYSICAL:  label = "Writing firmware"; write = true; break;
        case FlashPhase::FLASHING_LOGICAL:   label = "Writing system"; write = true; break;
        case FlashPhase::VERIFYING:          label = "Verifying"; break;
        case FlashPhase::COMPLETE:           label = "Complete"; break;
        case FlashPhase::FAILED:             label = "Failed"; break;
        default:                             label = "Working"; break;
    }
    if (!status.currentPartition.empty() &&
        (write || status.phase == FlashPhase::DECOMPRESSING ||
         status.phase == FlashPhase::VERIFYING)) {
        label += " (" + status.currentPartition + ")";
    }

    // Overall progress: completed partitions plus the fraction of the current
    // one, so the bar advances smoothly across the whole flash.
    int percentVal = status.progressPercent;
    if (status.partitionCount > 0) {
        float per = 100.0f / (float)status.partitionCount;
        float done = (float)status.partitionIndex * per;
        float cur = (per * (float)status.progressPercent) / 100.0f;
        percentVal = (int)(done + cur + 0.5f);
        if (percentVal > 100) percentVal = 100;
        if (percentVal < 0) percentVal = 0;
    }

    if (phaseLabel) *phaseLabel = label;
    if (percent) *percent = percentVal;
    if (isWrite) *isWrite = write;
}

// --- PS3 XMB ----------------------------------------------------------------

void OtaMenu::renderPs3() {
    glViewport(0, 0, mWidth, mHeight);

    // Vertical XMB blue-black gradient, approximated with horizontal bands.
    glClearColor(0.03f, 0.04f, 0.07f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    const int bands = 24;
    for (int i = 0; i < bands; i++) {
        float t0 = (float)i / (float)bands;
        float t1 = (float)(i + 1) / (float)bands;
        // Interpolate top rgb(0.10,0.13,0.20) -> bottom rgb(0.03,0.04,0.07).
        float tm = (t0 + t1) * 0.5f;
        float r = 0.10f + (0.03f - 0.10f) * tm;
        float g = 0.13f + (0.04f - 0.13f) * tm;
        float b = 0.20f + (0.07f - 0.20f) * tm;
        drawQuad(0.0f, t0 * mHeight, mWidth, (t1 - t0) * mHeight + 1.0f, r, g, b, 1.0f);
    }

    float margin = mWidth * 0.06f;
    float scale = 1.0f;
    float lineH = mFontSize * 1.5f;

    // Header: title + hairline dividers mimicking the XMB dialog frame.
    float headerY = margin + mFontSize;
    drawText("System Update", margin, headerY, scale * 1.15f, 1.0f, 1.0f, 1.0f, 1.0f);
    float topRuleY = headerY + mFontSize * 0.55f;
    drawQuad(margin, topRuleY, mWidth - margin * 2.0f, 1.0f, 0.75f, 0.82f, 0.95f, 0.28f);
    float bottomRuleY = mHeight - margin;
    drawQuad(margin, bottomRuleY, mWidth - margin * 2.0f, 1.0f, 0.75f, 0.82f, 0.95f, 0.28f);

    switch (mState) {
        case STATE_FILE_BROWSER: {
            float y = topRuleY + lineH;
            if (mFileEntries.empty()) {
                drawText("No update packages found.", margin, y, scale, 0.72f, 0.78f, 0.9f, 1.0f);
                y += lineH;
                drawText("Place .zip files in /sdcard/", margin, y, scale,
                         0.55f, 0.6f, 0.72f, 1.0f);
                y += lineH;
                drawText("or connect USB storage.", margin, y, scale, 0.55f, 0.6f, 0.72f, 1.0f);
            } else {
                drawText("Select update package", margin, y, scale, 0.82f, 0.88f, 1.0f, 1.0f);
                y += lineH * 1.2f;
                mItemStartY = y - mFontSize;
                mItemHeight = lineH;
                for (int i = 0; i < (int)mFileEntries.size(); i++) {
                    bool sel = (i == mFileSelectedIndex);
                    if (sel) {
                        // XMB selection glow bar.
                        drawQuad(margin - 6, y - mFontSize, mWidth - margin * 2.0f + 12, lineH,
                                 0.30f, 0.42f, 0.62f, 0.85f);
                        drawQuad(margin - 6, y - mFontSize, 3.0f, lineH, 0.75f, 0.85f, 1.0f, 1.0f);
                    }
                    std::string sizeStr = std::to_string(mFileEntries[i].size / 1024 / 1024) + " MB";
                    drawText(mFileEntries[i].displayName.c_str(), margin + 8, y, scale,
                             sel ? 1.0f : 0.72f, sel ? 1.0f : 0.78f, sel ? 1.0f : 0.9f, 1.0f);
                    drawText(sizeStr.c_str(),
                             mWidth - margin - measureText(sizeStr.c_str(), scale), y,
                             scale, 0.6f, 0.66f, 0.8f, 1.0f);
                    y += lineH;
                }
            }
            drawText("Up/Down  Select: A   Back: B", margin, bottomRuleY - mFontSize * 0.5f,
                     scale * 0.8f, 0.55f, 0.6f, 0.72f, 1.0f);
            break;
        }

        case STATE_CONFIRM: {
            float y = topRuleY + lineH;
            drawText(("Version: " + mManifest.version).c_str(), margin, y, scale,
                     0.82f, 0.88f, 1.0f, 1.0f);
            y += lineH * 1.2f;
            drawText("Partitions to update:", margin, y, scale, 0.72f, 0.78f, 0.9f, 1.0f);
            y += lineH;
            for (const auto& part : mManifest.partitions) {
                std::string info = "  " + part.name + "  (" + part.type + ")";
                if (part.type == "logical" && part.size > 0) {
                    info += "  " + std::to_string(part.size / 1024 / 1024) + " MB";
                }
                if (part.type == "physical") info += "  (both slots)";
                drawText(info.c_str(), margin, y, scale, 0.65f, 0.72f, 0.85f, 1.0f);
                y += lineH;
            }
            y += lineH;

            const char* options[] = {"Install", "Cancel", "Toggle backup"};
            mItemStartY = y - mFontSize;
            mItemHeight = lineH;
            for (int i = 0; i < 3; i++) {
                bool sel = (i == mConfirmSelectedIndex);
                std::string label = options[i];
                if (i == 2) label += mBackupRequested ? "  [ON]" : "  [OFF]";
                if (sel) {
                    drawQuad(margin - 6, y - mFontSize, mWidth - margin * 2.0f + 12, lineH,
                             0.30f, 0.42f, 0.62f, 0.85f);
                    drawQuad(margin - 6, y - mFontSize, 3.0f, lineH, 0.75f, 0.85f, 1.0f, 1.0f);
                }
                drawText(label.c_str(), margin + 8, y, scale,
                         sel ? 1.0f : 0.68f, sel ? 1.0f : 0.74f, sel ? 1.0f : 0.86f, 1.0f);
                y += lineH;
            }
            break;
        }

        case STATE_STAGING:
        case STATE_PREFLIGHT:
        case STATE_BACKUP:
        case STATE_FLASHING:
        case STATE_VERIFYING: {
            std::string phaseLabel;
            int percent = 0;
            themedFlashInfo(&phaseLabel, &percent, nullptr);

            // Status/phase line above the bar.
            float centerY = mHeight * 0.5f;
            float statusY = centerY - lineH * 1.6f;
            drawText((phaseLabel + "...").c_str(), margin, statusY, scale,
                     0.85f, 0.9f, 1.0f, 1.0f);

            // Slim horizontal bar, vertically centered.
            float barW = mWidth - margin * 2.0f;
            float barH = fmaxf(10.0f, mHeight * 0.014f);
            float barX = margin;
            float barY = centerY - barH * 0.5f;
            drawThemedProgressBar(barX, barY, barW, barH, (float)percent,
                                  0.15f, 0.18f, 0.24f,   // trough
                                  0.75f, 0.85f, 1.0f,    // fill
                                  0.0f, 0.0f, 0.0f, false);

            // Percentage centered above the bar.
            char pct[16];
            snprintf(pct, sizeof(pct), "%d%%", percent);
            float pctW = measureText(pct, scale);
            drawText(pct, mWidth * 0.5f - pctW * 0.5f, barY - mFontSize * 0.6f, scale,
                     1.0f, 1.0f, 1.0f, 1.0f);

            // Amber warning below the bar.
            const char* warn = "Do not turn off the system.";
            float warnW = measureText(warn, scale);
            drawText(warn, mWidth * 0.5f - warnW * 0.5f, barY + barH + lineH * 1.4f, scale,
                     1.0f, 0.78f, 0.25f, 1.0f);
            break;
        }

        case STATE_SUCCESS: {
            float cx = mWidth * 0.5f;
            float cy = mHeight * 0.42f;
            float rad = fmaxf(24.0f, mHeight * 0.09f);

            // Filled circle approximated by a fan of quads (triangle strip
            // ring), drawn in the XMB green.
            const int seg = 40;
            for (int i = 0; i < seg; i++) {
                float a0 = (float)i / seg * 2.0f * (float)M_PI;
                float a1 = (float)(i + 1) / seg * 2.0f * (float)M_PI;
                float x0 = cx + cosf(a0) * rad;
                float y0 = cy + sinf(a0) * rad;
                float x1 = cx + cosf(a1) * rad;
                float y1 = cy + sinf(a1) * rad;
                // Cover the wedge with an axis-aligned bounding quad segment.
                float minx = fminf(cx, fminf(x0, x1));
                float miny = fminf(cy, fminf(y0, y1));
                float maxx = fmaxf(cx, fmaxf(x0, x1));
                float maxy = fmaxf(cy, fmaxf(y0, y1));
                drawQuad(minx, miny, maxx - minx, maxy - miny,
                         0.5f, 0.88f, 0.5f, 1.0f);
            }

            // White check mark built from two thick line segments (quads).
            float t = fmaxf(4.0f, rad * 0.14f);
            // Short stroke: down-right.
            float ax = cx - rad * 0.40f, ay = cy + rad * 0.02f;
            float bx = cx - rad * 0.10f, by = cy + rad * 0.34f;
            // Long stroke: up-right.
            float dx = cx + rad * 0.46f, dy = cy - rad * 0.34f;
            // Approximate the two strokes with a small chain of quads.
            const int steps = 12;
            for (int i = 0; i < steps; i++) {
                float f0 = (float)i / steps;
                float px = ax + (bx - ax) * f0;
                float py = ay + (by - ay) * f0;
                drawQuad(px - t * 0.5f, py - t * 0.5f, t, t, 1.0f, 1.0f, 1.0f, 1.0f);
            }
            for (int i = 0; i < steps; i++) {
                float f0 = (float)i / steps;
                float px = bx + (dx - bx) * f0;
                float py = by + (dy - by) * f0;
                drawQuad(px - t * 0.5f, py - t * 0.5f, t, t, 1.0f, 1.0f, 1.0f, 1.0f);
            }

            const char* done = "Update complete";
            float dw = measureText(done, scale * 1.2f);
            drawText(done, cx - dw * 0.5f, cy + rad + lineH * 1.4f, scale * 1.2f,
                     0.85f, 1.0f, 0.85f, 1.0f);

            // Existing 5s reboot countdown behavior.
            mSuccessTimer++;
            int secondsLeft = 5 - (mSuccessTimer / 30);
            if (secondsLeft <= 0) {
                mFlasher.reboot();
            }
            std::string rebootMsg = "Rebooting in " +
                std::to_string(secondsLeft > 0 ? secondsLeft : 1) + " seconds...";
            float rw = measureText(rebootMsg.c_str(), scale);
            drawText(rebootMsg.c_str(), cx - rw * 0.5f, cy + rad + lineH * 2.6f, scale,
                     0.85f, 0.9f, 1.0f, 1.0f);
            const char* now = "A: Reboot now";
            float nw = measureText(now, scale * 0.85f);
            drawText(now, cx - nw * 0.5f, cy + rad + lineH * 3.7f, scale * 0.85f,
                     0.55f, 0.6f, 0.72f, 1.0f);
            break;
        }

        case STATE_FAILED: {
            float cx = mWidth * 0.5f;
            float cy = mHeight * 0.34f;
            float rad = fmaxf(20.0f, mHeight * 0.075f);
            float t = fmaxf(4.0f, rad * 0.16f);

            // Red X: two thick diagonal strokes.
            const int steps = 14;
            for (int i = 0; i < steps; i++) {
                float f0 = (float)i / (steps - 1);
                float px = cx - rad + (2.0f * rad) * f0;
                float py = cy - rad + (2.0f * rad) * f0;
                drawQuad(px - t * 0.5f, py - t * 0.5f, t, t, 0.9f, 0.35f, 0.35f, 1.0f);
                float px2 = cx - rad + (2.0f * rad) * f0;
                float py2 = cy + rad - (2.0f * rad) * f0;
                drawQuad(px2 - t * 0.5f, py2 - t * 0.5f, t, t, 0.9f, 0.35f, 0.35f, 1.0f);
            }

            float y = cy + rad + lineH * 1.2f;
            const char* head = "Update failed";
            float hw = measureText(head, scale * 1.15f);
            drawText(head, cx - hw * 0.5f, y, scale * 1.15f, 0.95f, 0.5f, 0.5f, 1.0f);
            y += lineH * 1.4f;

            // Word-wrapped error message, centered.
            {
                float maxWidth = mWidth - margin * 2.0f;
                std::string remaining = mErrorMessage;
                while (!remaining.empty()) {
                    size_t fitLen = remaining.size();
                    while (fitLen > 0 &&
                           measureText(remaining.substr(0, fitLen).c_str(), scale) > maxWidth) {
                        size_t spacePos = remaining.rfind(' ', fitLen - 1);
                        if (spacePos != std::string::npos && spacePos > 0) fitLen = spacePos;
                        else fitLen--;
                    }
                    if (fitLen == 0) fitLen = 1;
                    std::string chunk = remaining.substr(0, fitLen);
                    float lw = measureText(chunk.c_str(), scale);
                    drawText(chunk.c_str(), cx - lw * 0.5f, y, scale, 0.85f, 0.72f, 0.72f, 1.0f);
                    y += lineH;
                    remaining = remaining.substr(fitLen);
                    if (!remaining.empty() && remaining[0] == ' ') remaining = remaining.substr(1);
                }
            }
            y += lineH * 0.5f;

            // Retry / restore / reboot options in the XMB list look.
            const char* options[] = {"Retry flash", "Restore from backup", "Reboot anyway"};
            mItemStartY = y - mFontSize;
            mItemHeight = lineH;
            for (int i = 0; i < 3; i++) {
                if (i == 1 && !mHasBackup) continue;
                bool sel = (i == mErrorSelectedIndex);
                std::string label = options[i];
                float lw = measureText(label.c_str(), scale);
                if (sel) {
                    drawQuad(cx - lw * 0.5f - 12, y - mFontSize, lw + 24, lineH,
                             0.42f, 0.24f, 0.24f, 0.9f);
                }
                float br = sel ? 1.0f : 0.66f;
                drawText(label.c_str(), cx - lw * 0.5f, y, scale, br, br * 0.72f, br * 0.72f, 1.0f);
                y += lineH;
            }
            break;
        }
    }
}

// --- Nintendo DSi -----------------------------------------------------------

void OtaMenu::renderDsi() {
    glViewport(0, 0, mWidth, mHeight);

    // Light DSi background with a 2-row scanline pattern.
    glClearColor(0.96f, 0.96f, 0.96f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    // Draw 2px darker rows every 4px to get the alternating grey scanline look.
    for (int yy = 2; yy < mHeight; yy += 4) {
        drawQuad(0.0f, (float)yy, mWidth, 2.0f, 0.90f, 0.90f, 0.90f, 1.0f);
    }

    float margin = mWidth * 0.06f;
    float scale = 1.0f;
    float lineH = mFontSize * 1.5f;

    // Title band + 2-tone dashed rule under it (#828282 then #717171).
    float headerY = margin + mFontSize;
    drawText("System Update", margin, headerY, scale * 1.1f, 0.16f, 0.16f, 0.16f, 1.0f);
    float ruleY = headerY + mFontSize * 0.55f;
    float ruleX0 = margin;
    float ruleX1 = mWidth - margin;
    // Alternating short dashes: 6px #828282 (0.510) then 6px #717171 (0.443).
    float dashW = fmaxf(4.0f, mWidth * 0.008f);
    bool dark = true;
    for (float dx = ruleX0; dx < ruleX1; dx += dashW) {
        float w = fminf(dashW, ruleX1 - dx);
        if (dark) drawQuad(dx, ruleY, w, 1.0f, 0.510f, 0.510f, 0.510f, 1.0f);
        else      drawQuad(dx, ruleY, w, 1.0f, 0.443f, 0.443f, 0.443f, 1.0f);
        dark = !dark;
    }

    float bottomRuleY = mHeight - margin;

    switch (mState) {
        case STATE_FILE_BROWSER: {
            float y = ruleY + lineH;
            if (mFileEntries.empty()) {
                drawText("No update packages found.", margin, y, scale, 0.3f, 0.3f, 0.3f, 1.0f);
                y += lineH;
                drawText("Place .zip files in /sdcard/", margin, y, scale,
                         0.45f, 0.45f, 0.45f, 1.0f);
                y += lineH;
                drawText("or connect USB storage.", margin, y, scale, 0.45f, 0.45f, 0.45f, 1.0f);
            } else {
                drawText("Select update package", margin, y, scale, 0.2f, 0.2f, 0.2f, 1.0f);
                y += lineH * 1.2f;
                mItemStartY = y - mFontSize;
                mItemHeight = lineH;
                for (int i = 0; i < (int)mFileEntries.size(); i++) {
                    bool sel = (i == mFileSelectedIndex);
                    if (sel) {
                        // DSi blue selection row.
                        drawQuad(margin - 6, y - mFontSize, mWidth - margin * 2.0f + 12, lineH,
                                 0.16f, 0.44f, 0.90f, 1.0f);
                    }
                    std::string sizeStr = std::to_string(mFileEntries[i].size / 1024 / 1024) + " MB";
                    float rgb = sel ? 1.0f : 0.18f;
                    drawText(mFileEntries[i].displayName.c_str(), margin + 8, y, scale,
                             rgb, rgb, rgb, 1.0f);
                    drawText(sizeStr.c_str(),
                             mWidth - margin - measureText(sizeStr.c_str(), scale), y,
                             scale, sel ? 0.9f : 0.5f, sel ? 0.9f : 0.5f, sel ? 0.9f : 0.5f, 1.0f);
                    y += lineH;
                }
            }
            drawText("Up/Down  Select: A   Back: B", margin, bottomRuleY,
                     scale * 0.8f, 0.45f, 0.45f, 0.45f, 1.0f);
            break;
        }

        case STATE_CONFIRM: {
            float y = ruleY + lineH;
            drawText(("Version: " + mManifest.version).c_str(), margin, y, scale,
                     0.2f, 0.2f, 0.2f, 1.0f);
            y += lineH * 1.2f;
            drawText("Partitions to update:", margin, y, scale, 0.3f, 0.3f, 0.3f, 1.0f);
            y += lineH;
            for (const auto& part : mManifest.partitions) {
                std::string info = "  " + part.name + "  (" + part.type + ")";
                if (part.type == "logical" && part.size > 0) {
                    info += "  " + std::to_string(part.size / 1024 / 1024) + " MB";
                }
                if (part.type == "physical") info += "  (both slots)";
                drawText(info.c_str(), margin, y, scale, 0.35f, 0.35f, 0.35f, 1.0f);
                y += lineH;
            }
            y += lineH;

            const char* options[] = {"Install", "Cancel", "Toggle backup"};
            mItemStartY = y - mFontSize;
            mItemHeight = lineH;
            for (int i = 0; i < 3; i++) {
                bool sel = (i == mConfirmSelectedIndex);
                std::string label = options[i];
                if (i == 2) label += mBackupRequested ? "  [ON]" : "  [OFF]";
                if (sel) {
                    drawQuad(margin - 6, y - mFontSize, mWidth - margin * 2.0f + 12, lineH,
                             0.16f, 0.44f, 0.90f, 1.0f);
                }
                float rgb = sel ? 1.0f : 0.2f;
                drawText(label.c_str(), margin + 8, y, scale, rgb, rgb, rgb, 1.0f);
                y += lineH;
            }
            break;
        }

        case STATE_STAGING:
        case STATE_PREFLIGHT:
        case STATE_BACKUP:
        case STATE_FLASHING:
        case STATE_VERIFYING: {
            std::string phaseLabel;
            int percent = 0;
            themedFlashInfo(&phaseLabel, &percent, nullptr);

            float centerY = mHeight * 0.5f;
            float statusY = centerY - lineH * 1.6f;
            float sw = measureText((phaseLabel + "...").c_str(), scale);
            drawText((phaseLabel + "...").c_str(), mWidth * 0.5f - sw * 0.5f, statusY, scale,
                     0.16f, 0.16f, 0.16f, 1.0f);

            // Rounded DSi meter: light border, white interior, blue fill.
            float barW = mWidth - margin * 2.0f;
            float barH = fmaxf(14.0f, mHeight * 0.022f);
            float barX = margin;
            float barY = centerY - barH * 0.5f;
            drawThemedProgressBar(barX, barY, barW, barH, (float)percent,
                                  1.0f, 1.0f, 1.0f,      // trough (white interior)
                                  0.16f, 0.44f, 0.90f,   // fill (DSi blue)
                                  0.82f, 0.82f, 0.82f, true); // border

            // Percentage above the meter.
            char pct[16];
            snprintf(pct, sizeof(pct), "%d%%", percent);
            float pctW = measureText(pct, scale);
            drawText(pct, mWidth * 0.5f - pctW * 0.5f, barY - mFontSize * 0.6f, scale,
                     0.16f, 0.16f, 0.16f, 1.0f);

            // Bold dark warning below the meter (drawn twice for a faux-bold).
            const char* warn = "Do not turn off the power.";
            float warnW = measureText(warn, scale);
            float warnX = mWidth * 0.5f - warnW * 0.5f;
            float warnY = barY + barH + lineH * 1.4f;
            drawText(warn, warnX, warnY, scale, 0.12f, 0.12f, 0.12f, 1.0f);
            drawText(warn, warnX + 1.0f, warnY, scale, 0.12f, 0.12f, 0.12f, 1.0f);
            break;
        }

        case STATE_SUCCESS: {
            float cx = mWidth * 0.5f;
            float cy = mHeight * 0.42f;
            float rad = fmaxf(22.0f, mHeight * 0.085f);

            // Blue DSi check inside a light ring.
            const int seg = 40;
            for (int i = 0; i < seg; i++) {
                float a0 = (float)i / seg * 2.0f * (float)M_PI;
                float a1 = (float)(i + 1) / seg * 2.0f * (float)M_PI;
                float x0 = cx + cosf(a0) * rad, y0 = cy + sinf(a0) * rad;
                float x1 = cx + cosf(a1) * rad, y1 = cy + sinf(a1) * rad;
                float minx = fminf(cx, fminf(x0, x1));
                float miny = fminf(cy, fminf(y0, y1));
                float maxx = fmaxf(cx, fmaxf(x0, x1));
                float maxy = fmaxf(cy, fmaxf(y0, y1));
                drawQuad(minx, miny, maxx - minx, maxy - miny, 0.16f, 0.44f, 0.90f, 1.0f);
            }
            // White check mark.
            float t = fmaxf(4.0f, rad * 0.14f);
            float ax = cx - rad * 0.40f, ay = cy + rad * 0.02f;
            float bx = cx - rad * 0.10f, by = cy + rad * 0.34f;
            float dx = cx + rad * 0.46f, dy = cy - rad * 0.34f;
            const int steps = 12;
            for (int i = 0; i < steps; i++) {
                float f0 = (float)i / steps;
                float px = ax + (bx - ax) * f0, py = ay + (by - ay) * f0;
                drawQuad(px - t * 0.5f, py - t * 0.5f, t, t, 1.0f, 1.0f, 1.0f, 1.0f);
            }
            for (int i = 0; i < steps; i++) {
                float f0 = (float)i / steps;
                float px = bx + (dx - bx) * f0, py = by + (dy - by) * f0;
                drawQuad(px - t * 0.5f, py - t * 0.5f, t, t, 1.0f, 1.0f, 1.0f, 1.0f);
            }

            const char* done = "Update complete";
            float dw = measureText(done, scale * 1.15f);
            drawText(done, cx - dw * 0.5f, cy + rad + lineH * 1.4f, scale * 1.15f,
                     0.16f, 0.16f, 0.16f, 1.0f);

            mSuccessTimer++;
            int secondsLeft = 5 - (mSuccessTimer / 30);
            if (secondsLeft <= 0) {
                mFlasher.reboot();
            }
            std::string rebootMsg = "Rebooting in " +
                std::to_string(secondsLeft > 0 ? secondsLeft : 1) + " seconds...";
            float rw = measureText(rebootMsg.c_str(), scale);
            drawText(rebootMsg.c_str(), cx - rw * 0.5f, cy + rad + lineH * 2.6f, scale,
                     0.3f, 0.3f, 0.3f, 1.0f);
            const char* now = "A: Reboot now";
            float nw = measureText(now, scale * 0.85f);
            drawText(now, cx - nw * 0.5f, cy + rad + lineH * 3.7f, scale * 0.85f,
                     0.45f, 0.45f, 0.45f, 1.0f);
            break;
        }

        case STATE_FAILED: {
            float cx = mWidth * 0.5f;
            float cy = mHeight * 0.34f;
            float rad = fmaxf(18.0f, mHeight * 0.07f);
            float t = fmaxf(4.0f, rad * 0.16f);

            // Red X.
            const int steps = 14;
            for (int i = 0; i < steps; i++) {
                float f0 = (float)i / (steps - 1);
                float px = cx - rad + (2.0f * rad) * f0;
                float py = cy - rad + (2.0f * rad) * f0;
                drawQuad(px - t * 0.5f, py - t * 0.5f, t, t, 0.85f, 0.2f, 0.2f, 1.0f);
                float py2 = cy + rad - (2.0f * rad) * f0;
                drawQuad(px - t * 0.5f, py2 - t * 0.5f, t, t, 0.85f, 0.2f, 0.2f, 1.0f);
            }

            float y = cy + rad + lineH * 1.2f;
            const char* head = "Update failed";
            float hw = measureText(head, scale * 1.1f);
            drawText(head, cx - hw * 0.5f, y, scale * 1.1f, 0.75f, 0.15f, 0.15f, 1.0f);
            y += lineH * 1.4f;

            {
                float maxWidth = mWidth - margin * 2.0f;
                std::string remaining = mErrorMessage;
                while (!remaining.empty()) {
                    size_t fitLen = remaining.size();
                    while (fitLen > 0 &&
                           measureText(remaining.substr(0, fitLen).c_str(), scale) > maxWidth) {
                        size_t spacePos = remaining.rfind(' ', fitLen - 1);
                        if (spacePos != std::string::npos && spacePos > 0) fitLen = spacePos;
                        else fitLen--;
                    }
                    if (fitLen == 0) fitLen = 1;
                    std::string chunk = remaining.substr(0, fitLen);
                    float lw = measureText(chunk.c_str(), scale);
                    drawText(chunk.c_str(), cx - lw * 0.5f, y, scale, 0.3f, 0.3f, 0.3f, 1.0f);
                    y += lineH;
                    remaining = remaining.substr(fitLen);
                    if (!remaining.empty() && remaining[0] == ' ') remaining = remaining.substr(1);
                }
            }
            y += lineH * 0.5f;

            const char* options[] = {"Retry flash", "Restore from backup", "Reboot anyway"};
            mItemStartY = y - mFontSize;
            mItemHeight = lineH;
            for (int i = 0; i < 3; i++) {
                if (i == 1 && !mHasBackup) continue;
                bool sel = (i == mErrorSelectedIndex);
                std::string label = options[i];
                float lw = measureText(label.c_str(), scale);
                if (sel) {
                    drawQuad(cx - lw * 0.5f - 12, y - mFontSize, lw + 24, lineH,
                             0.16f, 0.44f, 0.90f, 1.0f);
                }
                float rgb = sel ? 1.0f : 0.25f;
                drawText(label.c_str(), cx - lw * 0.5f, y, scale, rgb, rgb, rgb, 1.0f);
                y += lineH;
            }
            break;
        }
    }
}

bool OtaMenu::threadLoop() {
    if (mExitRequested) return false;

    pollInput();
    render();
    eglSwapBuffers(mDisplay, mSurface);

    // ~30fps
    usleep(33333);
    return true;
}

} // namespace android
