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
    mSession = new SurfaceComposerClient();

    // Get display info
    const auto displayIds = SurfaceComposerClient::getPhysicalDisplayIds();
    if (displayIds.empty()) {
        ALOGE("No displays found");
        return NO_INIT;
    }
    mDisplayToken = SurfaceComposerClient::getPhysicalDisplayToken(displayIds[0]);

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

    // If package path was set (Mode A), jump straight to confirm
    if (!mPackagePath.empty()) {
        if (mManifest.parse(mPackagePath + "/manifest.json") ||
            mManifest.parse(mPackagePath)) {
            mFlasher.setPackageDir(mPackagePath);
            // Check for autoinstall property — skip confirm screen
            std::string autoInstall = android::base::GetProperty("sys.gammaos.ota.autoinstall", "");
            if (autoInstall == "1") {
                ALOGI("Auto-install triggered, starting flash immediately");
                startFlashThread();
            } else {
                mState = STATE_CONFIRM;
            }
        } else {
            mErrorMessage = "Failed to parse manifest from: " + mPackagePath;
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
        "/dev/gammaos-ota-stage/fonts/Roboto-Regular.ttf",
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

    // Scan /data/gammaos_ota/
    const char* dirs[] = {"/data/gammaos_ota", "/mnt/media_rw"};
    for (const char* dirPath : dirs) {
        DIR* dir = opendir(dirPath);
        if (!dir) continue;
        struct dirent* entry;
        while ((entry = readdir(dir)) != nullptr) {
            std::string name = entry->d_name;
            if (name.size() < 4) continue;

            std::string fullPath = std::string(dirPath) + "/" + name;

            // Check for .zip files
            if (name.size() > 4 && name.substr(name.size() - 4) == ".zip") {
                struct stat st;
                if (stat(fullPath.c_str(), &st) == 0) {
                    FileEntry fe;
                    fe.path = fullPath;
                    fe.displayName = name;
                    fe.size = (uint64_t)st.st_size;
                    mFileEntries.push_back(fe);
                }
            }

            // For /mnt/media_rw, also scan one level deeper (USB drives)
            if (std::string(dirPath) == "/mnt/media_rw" && entry->d_type == DT_DIR) {
                DIR* subdir = opendir(fullPath.c_str());
                if (!subdir) continue;
                struct dirent* subentry;
                while ((subentry = readdir(subdir)) != nullptr) {
                    std::string subname = subentry->d_name;
                    if (subname.size() > 4 && subname.substr(subname.size() - 4) == ".zip") {
                        std::string subPath = fullPath + "/" + subname;
                        struct stat st;
                        if (stat(subPath.c_str(), &st) == 0) {
                            FileEntry fe;
                            fe.path = subPath;
                            fe.displayName = "[USB] " + subname;
                            fe.size = (uint64_t)st.st_size;
                            mFileEntries.push_back(fe);
                        }
                    }
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

void OtaMenu::handleSelect() {
    switch (mState) {
        case STATE_FILE_BROWSER:
            if (!mFileEntries.empty()) {
                auto& fe = mFileEntries[mFileSelectedIndex];
                std::string targetDir = "/data/gammaos_ota";
                mkdir(targetDir.c_str(), 0755);

                std::string pkgDir = targetDir + "/package";
                // Clean and recreate package dir
                std::string cleanCmd = "rm -rf " + pkgDir;
                system(cleanCmd.c_str());
                mkdir(pkgDir.c_str(), 0755);

                // If from external storage, copy to /data first
                std::string zipPath = fe.path;
                if (fe.path.find("/mnt/media_rw/") == 0) {
                    std::string localPath = targetDir + "/external_update.zip";
                    std::string cpCmd = "cp " + fe.path + " " + localPath;
                    system(cpCmd.c_str());
                    zipPath = localPath;
                }

                // Extract zip to package dir (use staged binaries if available)
                ALOGI("Extracting %s to %s", zipPath.c_str(), pkgDir.c_str());
                std::string pathPrefix;
                if (OtaFlasher::isRunningFromTmpfs()) {
                    pathPrefix = "PATH=/dev/gammaos-ota-stage/bin:/system/bin:/vendor/bin "
                                 "LD_LIBRARY_PATH=/dev/gammaos-ota-stage/lib64:/system/lib64 ";
                }
                std::string unzipCmd = pathPrefix + "unzip -o " + zipPath + " -d " + pkgDir + " 2>/dev/null";
                int ret = system(unzipCmd.c_str());

                mFlasher.setPackageDir(pkgDir);
                if (ret == 0 && mManifest.parse(pkgDir + "/manifest.json")) {
                    mState = STATE_CONFIRM;
                } else {
                    mErrorMessage = "Failed to extract or parse update package";
                    mState = STATE_FAILED;
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
        mState = STATE_FAILED;
        return;
    }

    // Verify
    OtaFlasher::logToFile("INFO", "--- Phase: VERIFY ---");
    mState = STATE_VERIFYING;
    mFailedPartitions = mFlasher.verify(mManifest);
    if (mFailedPartitions.empty()) {
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
    } else {
        OtaFlasher::logToFile("ERROR", "Verification failed for %zu partition(s)",
                              mFailedPartitions.size());
        for (const auto& p : mFailedPartitions) {
            OtaFlasher::logToFile("ERROR", "  Failed: %s", p.c_str());
        }
        OtaFlasher::logToFile("INFO", "=== OTA FLASH SEQUENCE: FAILED ===");
        mErrorMessage = "Verification failed for " +
                        std::to_string(mFailedPartitions.size()) + " partition(s)";
        mState = STATE_FAILED;
    }
}

void OtaMenu::render() {
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
                drawText("Place .zip files in /data/gammaos_ota/", margin, y, scale,
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
