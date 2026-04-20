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

#define LOG_TAG "GammaOSNano"

#include <algorithm>
#include <thread>
#include <mutex>
#include <fcntl.h>
#include <dirent.h>
#include <unistd.h>
#include <sys/stat.h>
#include <math.h>
#include <stdlib.h>
#include <linux/input.h>
#include <sys/inotify.h>
#include <signal.h>
#include <strings.h>

#include <binder/IPCThreadState.h>
#include <cutils/properties.h>
#include <android-base/properties.h>
#include <utils/Log.h>
#include <utils/SystemClock.h>
#include <sched.h>
#include <pthread.h>
#include <sys/resource.h>
#include <sys/syscall.h>

#include <ui/DisplayMode.h>
#include <ui/DisplayState.h>
#include <ui/LayerStack.h>
#include <ui/PixelFormat.h>
#include <ui/Rect.h>

#include <gui/ISurfaceComposer.h>
#include <gui/Surface.h>
#include <gui/SurfaceComposerClient.h>

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <EGL/eglext.h>

// DRM direct rendering subsystem (structs, variables, functions)
#include "NanoMenuDrm.h"
// Shared utility functions (path helpers, containsInsensitive)
#include "NanoMenuUtils.h"

#include <aidl/android/hardware/light/ILights.h>
#include <aidl/android/hardware/light/HwLight.h>

#include "LibretroRunner.h"
#include "DrasticRunner.h"
#include <aidl/android/hardware/light/HwLightState.h>
#include <aidl/android/hardware/light/LightType.h>
#include <android/binder_manager.h>
#include <android/hardware/light/2.0/ILight.h>

#include "NanoMenu.h"
#include "NanoMenuShaders.h"

namespace android {

using ui::DisplayMode;

NanoMenu::NanoMenu()
    : Thread(false),
      mWidth(0), mHeight(0),
      mDisplay(EGL_NO_DISPLAY),
      mContext(EGL_NO_CONTEXT),
      mSurface(EGL_NO_SURFACE),
      mAppliedLayerStack(UINT32_MAX),
      mShaderProgram(0), mLocPosition(-1), mLocColor(-1),
      mParticleProgram(0), mParticleLocPosition(-1), mParticleLocColor(-1),
      mFxProgram(0), mFxLocPosition(-1), mFxLocTime(-1),
      mFxLocResolution(-1), mFxLocEffect(-1), mFxLocYFlip(-1),
      mXmbLocYFlip(-1),
      mSelectedIndex(0),
      mDisplayDirty(true),
      mInotifyFd(-1),
      mExitRequested(false),
      mWaitForRelease(false),
      mDrasticNanoPending(false),
      mMenuState(MENU_MAIN),
      mRecentSelectedIndex(0),
      mRecentLoaded(false),
      mStorageReady(false),
      mAppSelectedIndex(0),
      mAppsLoaded(false),
      mScrollOffset(0.0f),
      mScrollDir(1),
      mScrollPause(0),
      mLastScrolledIdx(-1),
      mMenuScrollTop(0),
      mStickYTriggered(false),
      mStickXTriggered(false),
      mSelectHeld(false), mPowerPressTime(0),
      mBrightness(128), mMaxBrightness(255),
      mShowBrightnessBar(false), mBrightnessBarTimer(0),
      mVolume(10), mMaxVolume(15),
      mShowVolumeBar(false), mVolumeBarTimer(0),
      mBatteryPercent(-1), mBatteryCharging(false),
      mBatteryPollTicks(0),
      mWifiLevel(kWifiLevel_Unknown), mWifiBars(0),
      mBtLevel(kBtLevel_Unknown), mBtConnectedCount(0),
      mNetPollInitialised(false),
      mNetPollThreadRunning(false), mNetPollExitRequested(false),
      mLastFrameNs(0),
      mFrameDt(1.0f / 60.0f),
      mCurrentEffect(1),
      mEffectTime(0.0f),
      mQuickResumeEnabled(false),
      mXmbMode(false), mXmbRecentMax(50), mXmbSystemIndex(0), mXmbGameIndex(0),
      mXmbAnimX(0.0f), mXmbAnimY(0.0f),
      mXmbGameScrollTop(0), mXmbRomScanDone(false),
      mXmbBootCompleted(false),
      mBgScanResultReady(false), mBgScanThreadRunning(false),
      mSettingsSelectedIndex(0),
      mWifiEntrySelected(0), mWifiScrollTop(0),
      mWifiLastScanMs(0), mWifiScanInProgress(false), mWifiListDirty(false),
      mWifiStatusMsgUntilMs(0), mWifiPendingSecurity(2),
      mBtEntrySelected(0), mBtScrollTop(0),
      mBtLastScanMs(0), mBtScanInProgress(false),
      mBtDiscoveryInProgress(false), mBtListDirty(false),
      mBtStatusMsgUntilMs(0),
      mOskPasswordMode(false),
      mOskActive(false), mOskShift(true), mOskCursorX(0), mOskCursorY(0),
      mSearchSelectedIndex(0), mSearchActive(false),
      mFtLib(nullptr),
      mFtNumFaces(0),
      mFontSize(48),
      mGlyphAtlasTex(0),
      mAtlasW(0), mAtlasH(0),
      mAtlasCurX(0), mAtlasCurY(0), mAtlasRowH(0),
      mTextProgram(0), mTextLocPosition(-1), mTextLocTexCoord(-1),
      mTextLocColor(-1), mTextLocTexture(-1) {
    mSession = new SurfaceComposerClient();
    srand(elapsedRealtime());
    memset(mParticles, 0, sizeof(mParticles));
    memset(mFtFaces, 0, sizeof(mFtFaces));
    // Restore persisted wallpaper effect, default to XMB (21)
    char wallpaper[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.wallpaper", wallpaper, "21");
    int savedEffect = atoi(wallpaper);
    bool found = false;
    for (int i = 0; i < kNumActiveEffects; i++) {
        if (kActiveEffects[i] == savedEffect) {
            sActiveEffectIdx = i;
            mCurrentEffect = savedEffect;
            found = true;
            break;
        }
    }
    if (!found) {
        sActiveEffectIdx = kNumActiveEffects - 1; // XMB
        mCurrentEffect = kActiveEffects[sActiveEffectIdx];
    }
    // Load Quick Resume toggle from persistent property
    mQuickResumeEnabled = android::base::GetBoolProperty(
            "persist.gammaos.nano.quick_resume", false);
}

NanoMenu::~NanoMenu() {
    // Stop the network HUD poller first so its worker thread can't
    // race with teardown of other state.
    stopNetPollThread();

    // GammaOS: Clean up secondary display wallpaper resources.
    for (size_t i = 0; i < mSecondaryEglSurfaces.size(); i++) {
        eglDestroySurface(mDisplay, mSecondaryEglSurfaces[i]);
    }
    mSecondaryEglSurfaces.clear();
    mSecondarySurfaces.clear();
    if (!mSecondaryWallpaperControls.empty()) {
        SurfaceComposerClient::Transaction t;
        for (size_t i = 0; i < mSecondaryWallpaperControls.size(); i++) {
            t.reparent(mSecondaryWallpaperControls[i], nullptr);
        }
        t.apply();
        mSecondaryWallpaperControls.clear();
    }
    mSecondaryDisplayTokens.clear();
    mSecondaryAppliedLayerStacks.clear();
    for (int fd : mInputFds) {
        ioctl(fd, EVIOCGRAB, 0); // release grab (always, in case exit-grab was applied)
        close(fd);
    }
    if (mInotifyFd >= 0) close(mInotifyFd);
}

void NanoMenu::onFirstRef() {
    status_t err = mSession->linkToComposerDeath(this);
    SLOGE_IF(err, "linkToComposerDeath failed (%s)", strerror(-err));
}

sp<SurfaceComposerClient> NanoMenu::session() const { return mSession; }

void NanoMenu::binderDied(const wp<IBinder>&) {
    ALOGD("SurfaceFlinger died, exiting...");
    kill(getpid(), SIGKILL);
    requestExit();
}
status_t NanoMenu::readyToRun() {
    int64_t t0 = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
    auto tlog = [&](const char* label) {
        int64_t now = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
        ALOGW("NanoMenu BOOT TIMING: %s at T+%lldms (delta %lldms)", label, now, now - t0);
        t0 = now;
    };
    tlog("readyToRun enter");

    // GammaOS: Before doing ANYTHING that touches the display (DRM master grab,
    // SF transactions), confirm we are actually in nano boot mode. StartPropertySetThread
    // already gates this, but gammaos-nano can also be (re)started by other init triggers
    // (ctl restarts, userspace reboot, app exit relaunch), so a defensive check here
    // prevents DRM takeover in normal boot if any of those paths fire unexpectedly.
    //
    // persist.* properties may not be available yet (loaded after /data mount), so
    // ALWAYS wait for init to signal they are ready before reading persist.bootanim.skip_nano.
    // Reading it too early would return an empty string and we would proceed into the
    // nano path in normal boot — blanking the display via drmEarlySplash() and causing
    // a multi-second black gap between bootloader and bootanim.
    {
        char ready[PROPERTY_VALUE_MAX] = {};
        property_get("ro.persistent_properties.ready", ready, "");
        if (strcmp(ready, "true") != 0) {
            ALOGI("GammaOS Nano: persist props not ready, waiting...");
            for (int i = 0; i < 500; i++) { // max 5s
                usleep(10000); // 10ms
                property_get("ro.persistent_properties.ready", ready, "");
                if (!strcmp(ready, "true")) break;
            }
        }
    }

    char skip[PROPERTY_VALUE_MAX] = {};
    property_get("persist.bootanim.skip_nano", skip, "");
    if (strcmp(skip, "0") != 0) {
        ALOGI("GammaOS Nano: skip_nano='%s' (not '0'), starting bootanim (no DRM touch)", skip);
        property_set("ctl.start", "bootanim");
        _exit(0); // terminate — bootanim takes over, stock boot flow continues
    }

    tlog("persist props resolved");

    // GammaOS: Detect drastic QR fast-path early so subsequent init
    // stages can skip heavy work (particle/fx/XMB shader compiles,
    // ROM scanning, icon texture load) and go straight to the drastic
    // render loop. This is the analog of the libretro minimal boot
    // path. Read the props directly; we don't want a race with
    // waiting for NanoMenu's other props.
    //
    // Two trigger conditions:
    //   1) persist.gammaos.nano.drastic_smoke=1 -- debug smoke-test
    //      knob, still used by runDrasticInitIfNeeded() in main.cpp
    //      for A/B service-wait experiments.
    //   2) persist.gammaos.nano.qr_prepared=1 AND qr_core="drastic"
    //      -- real QR path primed by launchXmbGame() when the user
    //      opens a Nintendo DS game. The "drastic" sentinel in
    //      qr_core distinguishes drastic QR from libretro QR (which
    //      stores a full core .so path).
    {
        char smoke[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.drastic_smoke", smoke, "0");
        bool smokeActive = (strcmp(smoke, "1") == 0);

        char qp[PROPERTY_VALUE_MAX] = {};
        char qc[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.qr_prepared", qp, "0");
        property_get("persist.gammaos.nano.qr_core", qc, "");
        bool drasticQrPrimed = (strcmp(qp, "1") == 0) &&
                               (strcmp(qc, "drastic") == 0);

        sDrasticQrFastPath = smokeActive || drasticQrPrimed;
        if (sDrasticQrFastPath) {
            ALOGW("NanoMenu: drastic QR fast-path ACTIVE "
                  "(smoke=%d qr_primed=%d), skipping heavy init",
                  smokeActive ? 1 : 0, drasticQrPrimed ? 1 : 0);
        }
    }

    // GammaOS: Nano mode is confirmed active. Show DRM splash now — before any
    // SF/HWC setup. This replaces the U-Boot logo with a dark screen within
    // milliseconds on devices where HWC composition isn't ready yet (e.g.
    // dual-DSI RG DS RK3568). Skip DRM on restarts (returning from app) —
    // HWC is already active by then.
    {
        char bootDone[PROPERTY_VALUE_MAX] = {};
        property_get("sys.boot_completed", bootDone, "0");
        // GammaOS: force_drm=1 re-grabs DRM master post-boot for
        // drastic nano mode (XMB -> drastic nano restart path). The
        // XMB launch path sets this before restarting NanoMenu so the
        // new instance takes DRM even though boot is already complete.
        char forceDrm[PROPERTY_VALUE_MAX] = {};
        property_get("sys.gammaos.nano.force_drm", forceDrm, "0");
        if (strcmp(bootDone, "1") != 0 || strcmp(forceDrm, "1") == 0) {
            if (strcmp(forceDrm, "1") == 0) {
                ALOGW("NanoMenu: force_drm=1, grabbing DRM master "
                      "post-boot for drastic nano");
            }
            drmEarlySplash();
        } else {
            ALOGI("NanoMenu: skipping DRM splash (already booted)");
        }
    }
    tlog("drmEarlySplash done");
    // Nano mode is active — tell any boot animation instance to exit.
    // Vendor init may start bootanim independently (e.g. in on late-fs),
    // so it can be running alongside us with the same z-layer.
    property_set("service.bootanim.exit", "1");

    const std::vector<PhysicalDisplayId> ids = SurfaceComposerClient::getPhysicalDisplayIds();
    tlog("getPhysicalDisplayIds returned");
    if (ids.empty()) { ALOGE("No displays found"); return NAME_NOT_FOUND; }

    // GammaOS: persist.gammaos.nano.primary_display selects which physical display
    // port NanoMenu renders on (and which display nano-launched apps target).
    // Value is a port number (0 = first physical port, 1 = second, ...). Falls back
    // to ids.front() if the configured port is not found. Dualstack-whitelisted apps
    // still launch on the real primary — that routing is enforced in RootWindowContainer.
    PhysicalDisplayId chosenId = ids.front();
    {
        char primaryProp[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.primary_display", primaryProp, "0");
        const int wantPort = atoi(primaryProp);
        for (const PhysicalDisplayId& pid : ids) {
            if (static_cast<int>(pid.getPort()) == wantPort) {
                chosenId = pid;
                break;
            }
        }
        ALOGI("NanoMenu: primary_display prop='%s' chosen port=%d",
              primaryProp, static_cast<int>(chosenId.getPort()));
    }

    mDisplayToken = SurfaceComposerClient::getPhysicalDisplayToken(chosenId);
    if (mDisplayToken == nullptr) return NAME_NOT_FOUND;
    tlog("getPhysicalDisplayToken");

    // GammaOS: Look up the chosen display's layer stack so the NanoMenu surface
    // can be attached to it. Layers only show up on a display whose layerStack
    // matches the layer's layerStack, so without this the surface stays on the
    // default (port 0) display regardless of which token we targeted above.
    // NOTE: SurfaceFlinger's layerStack for a given display can change after
    // DisplayManagerService finishes assigning logical display IDs (seen on
    // dual-DSI RK3568: port 1 starts at layerStack=1, becomes 2 after DMS).
    // The render loop re-queries and re-applies setLayerStack to handle that.
    ui::DisplayState chosenDisplayState;
    ui::LayerStack chosenLayerStack = ui::DEFAULT_LAYER_STACK;
    if (SurfaceComposerClient::getDisplayState(mDisplayToken, &chosenDisplayState) == NO_ERROR) {
        chosenLayerStack = chosenDisplayState.layerStack;
        ALOGI("NanoMenu: chosen display layerStack=%u", chosenLayerStack.id);
    } else {
        ALOGW("NanoMenu: getDisplayState failed, using default layerStack");
    }
    mAppliedLayerStack = chosenLayerStack.id;

    DisplayMode displayMode;
    const status_t error = SurfaceComposerClient::getActiveDisplayMode(mDisplayToken, &displayMode);
    if (error != NO_ERROR) return error;
    tlog("getActiveDisplayMode");

    ui::Size resolution = displayMode.resolution;
    // GammaOS: Use RGBX_8888 (opaque 32bpp) to match the EGL config (8:8:8
    // with alpha=0) and the AHB/DRM pixel format (R8G8B8A8 / XRGB8888).
    // The previous RGB_565 surface forced the driver to do a 16→32 bpp
    // conversion on every present, visible as extra GPU/CPU cost on Mali-G52.
    sp<SurfaceControl> control = session()->createSurface(
        String8("GammaOSNano"), resolution.getWidth(), resolution.getHeight(),
        PIXEL_FORMAT_RGBX_8888, ISurfaceComposerClient::eOpaque);
    tlog("createSurface (primary)");

    SurfaceComposerClient::Transaction t;
    Rect forcedRes(0, 0, resolution.width, resolution.height);
    Rect physRes(0, 0, displayMode.resolution.width, displayMode.resolution.height);
    // GammaOS: Always set the primary display projection to physical resolution.
    // NanoMenu renders at 640x480 and needs the display projection to match.
    // If DualStack left it at 640x960, NanoMenu would appear compressed.
    // DualStack invalidates the Java-side cache when it later re-applies 640x960.
    t.setDisplayProjection(mDisplayToken, ui::ROTATION_0, forcedRes, physRes);
    t.setLayer(control, 0x40000001);
    // GammaOS: Route the NanoMenu surface to the chosen display's layer stack.
    t.setLayerStack(control, chosenLayerStack);

    // GammaOS: Signal that NanoMenu is active. DualStackController checks this
    // to suppress DualStack while NanoMenu is rendering.
    property_set("sys.gammaos.nano.menu_active", "1");
    // Only clear forced display size if DualStack was actually active. Sending the
    // clear signal unconditionally triggers DualStack teardown + display reconfig
    // events that interfere with subsequent app launches.
    {
        char dsActive[PROPERTY_VALUE_MAX] = {};
        property_get("sys.gammaos.dualstack.active", dsActive, "0");
        if (!strcmp(dsActive, "1")) {
            property_set("sys.gammaos.dualstack.active", "0");
            property_set("sys.gammaos.nano.clear_forced_size", "1");
            ALOGD("NanoMenu: signaled DualStack clear (was active)");
        }
    }

    // GammaOS: Signal the framework to kill the previous foreground app.
    // NanoMenu runs as graphics user and can't call am force-stop directly.
    // The framework (RootWindowContainer) picks up this property and kills the app.
    {
        char lastApp[PROPERTY_VALUE_MAX] = {};
        property_get("sys.gammaos.nano.launched_pkg", lastApp, "");
        if (lastApp[0] != '\0') {
            property_set("sys.gammaos.nano.kill_pkg", lastApp);
            ALOGD("NanoMenu: signaled framework to kill: %s", lastApp);
            property_set("sys.gammaos.nano.launched_pkg", "");
        }
    }
    tlog("primary transaction applied");
    ALOGD("NanoMenu: signaled framework to clear forced display size");

    // GammaOS: Defer secondary display surface creation until after the first frame
    // is rendered on the primary. On dual-DSI devices the secondary surface/transaction
    // can trigger SF display reconfiguration that delays the primary scanout.
    t.apply();
    tlog("primary transaction applied (secondary deferred)");

    sp<Surface> s = control->getSurface();
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(display, nullptr, nullptr);
    EGLConfig config = getEglConfig(display);
    EGLSurface surface = eglCreateWindowSurface(display, config, s.get(), nullptr);
    EGLint contextAttributes[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    EGLContext context = eglCreateContext(display, config, nullptr, contextAttributes);
    EGLint w, h;
    eglQuerySurface(display, surface, EGL_WIDTH, &w);
    eglQuerySurface(display, surface, EGL_HEIGHT, &h);
    if (eglMakeCurrent(display, surface, surface, context) == EGL_FALSE) return NO_INIT;

    mDisplay = display; mContext = context; mSurface = surface;
    mWidth = w; mHeight = h;
    // NOTE: eglQuerySurface returns logical (rotation-applied) dimensions
    // because SurfaceFlinger reports the active display mode with install
    // orientation already factored in. Do NOT swap mWidth/mHeight here —
    // the AHB swap in drmSetupZeroCopy ensures the render target matches.
    mFlingerSurfaceControl = control; mFlingerSurface = s;

    ALOGD("NanoMenu: display %dx%d", mWidth, mHeight);
    tlog("EGL init + primary surface ready");

    // GammaOS: Set up zero-copy DRM rendering via DMA-BUF/EGLImage/FBO.
    // GPU writes straight to the DRM scanout buffer — no glReadPixels, no CPU copy.
    drmSetupZeroCopy(display);
    tlog("DRM zero-copy setup");

    // Secondary EGL surfaces deferred — created later in threadLoop after first frame.
    tlog("EGL ready (secondary deferred)");
    initShaders();
    tlog("shaders compiled");
    buildMenu();
    if (!sDrasticQrFastPath) {
        initXmbSystems();
        loadXmbRecent();
    }
    openInputDevices();
    if (!sDrasticQrFastPath) {
        initEffects();
    }
    if (sDrasticQrFastPath) {
        tlog("drastic fast-path skipped XMB+effects");
    }

    // Initialize brightness — restore from persist property (synced with Android),
    // falling back to current sysfs value
    mMaxBrightness = readSysfsInt("/sys/class/leds/lcd-backlight/max_brightness", 255);
    char savedBrightness[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.brightness", savedBrightness, "");
    if (savedBrightness[0] != '\0') {
        mBrightness = atoi(savedBrightness);
        if (mBrightness < 1) mBrightness = 1;
        if (mBrightness > mMaxBrightness) mBrightness = mMaxBrightness;
    } else {
        mBrightness = readSysfsInt("/sys/class/leds/lcd-backlight/brightness", mMaxBrightness / 2);
    }
    // GammaOS: Immediately write brightness to sysfs for instant backlight during early boot.
    // The Lights HAL (vendor.light-rockchip) may not be up for seconds, so write directly
    // to ensure the screen is visible as soon as NanoMenu starts rendering.
    {
        char brightnessStr[16];
        snprintf(brightnessStr, sizeof(brightnessStr), "%d", mBrightness);
        const char* backlightPaths[] = {
            "/sys/class/backlight/backlight/brightness",
            "/sys/class/backlight/backlight1/brightness",
            "/sys/class/leds/lcd-backlight/brightness",
        };
        for (const char* path : backlightPaths) {
            int fd = open(path, O_WRONLY);
            if (fd >= 0) {
                write(fd, brightnessStr, strlen(brightnessStr));
                close(fd);
                ALOGI("NanoMenu: early sysfs brightness %d → %s", mBrightness, path);
            }
        }
    }

    // Apply brightness async via HAL — also sets it through the proper Android path
    // so DMS/PowerManager stay in sync.
    {
        int brightness = mBrightness;
        std::thread([brightness]() {
            // Try AIDL first
            {
                using aidl::android::hardware::light::ILights;
                using aidl::android::hardware::light::HwLight;
                using aidl::android::hardware::light::HwLightState;
                using aidl::android::hardware::light::LightType;

                ndk::SpAIBinder binder(AServiceManager_checkService(
                        "android.hardware.light.ILights/default"));
                if (binder.get()) {
                    std::shared_ptr<ILights> hal = ILights::fromBinder(binder);
                    if (hal) {
                        std::vector<HwLight> lights;
                        hal->getLights(&lights);
                        for (const auto& light : lights) {
                            if (light.type == LightType::BACKLIGHT) {
                                HwLightState state{};
                                state.color = 0xFF000000 | (brightness << 16) | (brightness << 8) | brightness;
                                hal->setLightState(light.id, state);
                                ALOGI("NanoMenu: brightness %d via AIDL ILights", brightness);
                                return;
                            }
                        }
                    }
                }
            }

            // AIDL not available yet — wait for HIDL or AIDL, whichever comes first
            using HidlLight = ::android::hardware::light::V2_0::ILight;
            using HidlType = ::android::hardware::light::V2_0::Type;
            using HidlLightState = ::android::hardware::light::V2_0::LightState;
            using HidlBrightness = ::android::hardware::light::V2_0::Brightness;
            using HidlFlash = ::android::hardware::light::V2_0::Flash;

            // Poll for either HAL (100ms intervals, up to 5s)
            for (int i = 0; i < 50; i++) {
                // Try AIDL
                {
                    using aidl::android::hardware::light::ILights;
                    using aidl::android::hardware::light::HwLight;
                    using aidl::android::hardware::light::HwLightState;
                    using aidl::android::hardware::light::LightType;

                    ndk::SpAIBinder binder(AServiceManager_checkService(
                            "android.hardware.light.ILights/default"));
                    if (binder.get()) {
                        std::shared_ptr<ILights> hal = ILights::fromBinder(binder);
                        if (hal) {
                            std::vector<HwLight> lights;
                            hal->getLights(&lights);
                            for (const auto& light : lights) {
                                if (light.type == LightType::BACKLIGHT) {
                                    HwLightState state{};
                                    state.color = 0xFF000000 | (brightness << 16) | (brightness << 8) | brightness;
                                    hal->setLightState(light.id, state);
                                    ALOGI("NanoMenu: brightness %d via AIDL ILights", brightness);
                                    return;
                                }
                            }
                        }
                    }
                }
                // Try HIDL
                {
                    android::sp<HidlLight> hal = HidlLight::getService();
                    if (hal != nullptr) {
                        HidlLightState state{};
                        state.color = 0xFF000000 | (brightness << 16) | (brightness << 8) | brightness;
                        state.flashMode = HidlFlash::NONE;
                        state.brightnessMode = HidlBrightness::USER;
                        hal->setLight(HidlType::BACKLIGHT, state);
                        ALOGI("NanoMenu: brightness %d via HIDL ILight@2.0", brightness);
                        return;
                    }
                }
                usleep(100000); // 100ms
            }
            ALOGW("NanoMenu: lights HAL not available after 5s, brightness not set");
        }).detach();
    }

    // Restore volume from persist property
    {
        char savedVolume[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.volume", savedVolume, "10");
        mVolume = atoi(savedVolume);
        if (mVolume < 0) mVolume = 0;
        if (mVolume > mMaxVolume) mVolume = mMaxVolume;
    }

    // Zygote + SystemServer preload is triggered by init.rc on nonencrypted,
    // before gammaos-nano even starts.  By the time the user sees the menu,
    // Android is already booting in the background.

    // Kick the network HUD poller. The thread spins even if wifi/bt
    // services aren't up yet -- shell-outs return empty and we keep
    // the "Unknown" state, so the HUD simply does not draw until a
    // real reply lands.
    startNetPollThread();

    // Initialise Settings column items (WiFi + Bluetooth entries).
    initSettingsItems();

    return NO_ERROR;
}

bool NanoMenu::threadLoop() {
    ALOGD("NanoMenu: entering main loop");
    {
        int64_t nowMs = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
        ALOGW("NanoMenu BOOT TIMING: main loop entry at T+%lldms", nowMs);
    }

    // GammaOS: Real-time boost for the render thread.
    //
    // Measured behaviour on a 4-core RK3566 (2026-04-13):
    //   post-boot steady state: 59.9 fps locked via DRM_IOCTL_WAIT_VBLANK
    //   boot window (~15 s):    occasional 33-100+ ms frame spikes
    //                           caused by vendor HAL init
    //                           (vendor.usb_gadget_default,
    //                           vendor.rockit-hal, vendor.power-aidl,
    //                           vendor.outputmanager, etc.) and
    //                           kernel interrupt activity preempting
    //                           our render thread.
    //
    // SCHED_FIFO prio 80: comfortably above every SCHED_OTHER thread,
    // above SurfaceFlinger / vndbinder / Mali helpers (FIFO 2), above
    // the Android audio/input tier (~FIFO 50), and below the kernel
    // migration / RCU tier (FIFO 99).
    //
    // FIFO over RR because drastic's internal threads also run at RR 5.
    // Under SCHED_RR peers at equal priority time-slice between each
    // other; a slice expiry mid-flip could push the render thread past
    // vblank. SCHED_FIFO never gets sliced out. The render thread
    // spends most of its budget blocked in poll() on the DRM fd
    // (drmDrainPageFlipEvents) or WAIT_VBLANK, so even at prio 80 it
    // yields enough wall-clock time for drastic's RR threads to
    // produce frames.
    //
    // History: 2026-04-13 iteration log on RK3566 RG DS:
    //   RR 5          -> 92% at >=59.5 fps, avg 59.37 (XMB);
    //                   drastic QR avg 58.5, ~40% frames with 30-50 ms spikes
    //   FIFO 5        -> same order as RR 5, ~12 catastrophic frames per
    //                   3 min on Sonic Rush drastic QR
    //   FIFO 80       -> 0 catastrophic frames per 3 min, p99=17.0 ms,
    //                   peak 17.2 ms (essentially one vblank)
    // The earlier "FIFO 90 regression to 40 fps" observation from RR
    // experiments predates the drmDrainPageFlipEvents sync gate; the
    // old pacing path had the render thread busy-waiting in some
    // cases, which starved peer threads at equal prio. The current
    // drain blocks in poll(), so the FIFO task yields cleanly and high
    // priority is safe.
    //
    // Nice=-20 is layered on top so the SCHED_OTHER fallback (below,
    // when RT is denied) still dominates normal threads.
    //
    // Applied at threadLoop entry so it covers both the DRM-direct XMB
    // path and the drastic QR fast-path (same render thread).
    {
        sched_param sp = {};
        sp.sched_priority = 80;
        int rc = pthread_setschedparam(pthread_self(), SCHED_FIFO, &sp);
        pid_t selfTid = (pid_t)syscall(SYS_gettid);
        setpriority(PRIO_PROCESS, selfTid, -20);
        if (rc == 0) {
            ALOGW("NanoMenu: render thread SCHED_FIFO prio 80 + nice -20 ok");
        } else {
            ALOGW("NanoMenu: SCHED_FIFO denied (%s), nice -20 applied",
                  strerror(rc));
        }
    }

    // GammaOS: Render thread is NOT pinned to a specific CPU.
    //
    // Tried pinning to CPU 3 (2026-04-13) as an attempt to isolate
    // from drastic's rasterizer threads floating on 0-2. Measured
    // result was WORSE: avg 58.5 vs 59.2 fps unpinned, 71 % at 60 fps
    // vs 82 %, 29 % spike rate vs 18 %. Conclusion: on this SMP
    // device the kernel's load balancer works in our favour -- when
    // CPU 3 has a transient IRQ burst the unpinned render thread
    // migrates away; pinning locks us to the stalled core. The
    // FIFO 5 priority boost alone is sufficient.

    // GammaOS: Lock all pages into RAM.
    //
    // Even with SCHED_RR prio 5, the 50-100 ms spikes during the first
    // ~25 seconds of boot persisted -- those durations rule out
    // scheduler preemption and point at kernel-side stalls. The two
    // most likely causes at that timescale are page-fault I/O (kernel
    // pulls a demand-paged page off storage while the render thread is
    // blocked) and dirty-page writeback stealing memory bandwidth.
    //
    // mlockall(MCL_CURRENT | MCL_FUTURE) pins the process's current
    // working set and every future allocation into physical memory,
    // so no subsequent access triggers a fault. Paired with
    // IPC_LOCK + SYS_RESOURCE + rlimit memlock in gammaos-nano.rc so
    // the 64 KB default cap doesn't cause EPERM/ENOMEM. The trade is
    // ~50 ms of up-front fault cost at startup for predictable frame
    // timing thereafter.
    if (mlockall(MCL_CURRENT | MCL_FUTURE) == 0) {
        ALOGW("NanoMenu: mlockall done");
    } else {
        ALOGW("NanoMenu: mlockall failed (%s) -- check caps/rlimit in "
              "gammaos-nano.rc", strerror(errno));
    }

    // Clear any stale drop_input/fence from a previous instance.
    property_set("sys.gammaos.nano.drop_input", "0");
    property_set("sys.gammaos.nano.drop_fence_ns", "0");

    // readyToRun() sets service.bootanim.exit=1 to kill the vendor bootanim.
    // Reset it here so our own exit check (further below) doesn't immediately
    // terminate the menu on restarts.
    property_set("service.bootanim.exit", "0");

    // Re-read quick resume flag — the constructor runs before persist props
    // are loaded, so the value read there may be stale (always false).
    mQuickResumeEnabled = android::base::GetBoolProperty(
            "persist.gammaos.nano.quick_resume", false);
    mXmbMode = android::base::GetBoolProperty(
            "persist.gammaos.nano.xmb_mode", false);
    // Re-read wallpaper effect (constructor ran before persist props loaded)
    {
        char wallpaper[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.wallpaper", wallpaper, "21");
        int savedEffect = atoi(wallpaper);
        for (int i = 0; i < kNumActiveEffects; i++) {
            if (kActiveEffects[i] == savedEffect) {
                sActiveEffectIdx = i;
                mCurrentEffect = savedEffect;
                if (mCurrentEffect >= 1 && mCurrentEffect <= 10) initEffects();
                break;
            }
        }
    }
    // If returning from a game, restore the XMB position + color
    {
        std::string retSys = android::base::GetProperty(
                "sys.gammaos.nano.xmb_return_sys", "");
        if (mXmbMode && !retSys.empty()) {
            int returnSysIdx = atoi(retSys.c_str());
            int returnGameIdx = atoi(android::base::GetProperty(
                    "sys.gammaos.nano.xmb_return_game", "0").c_str());
            // Clear so we don't re-apply on next restart
            property_set("sys.gammaos.nano.xmb_return_sys", "");
            property_set("sys.gammaos.nano.xmb_return_game", "");

            if (returnSysIdx == -1) {
                // Return to Recently Played
                loadXmbRecent();
                if (!mXmbRecent.empty()) {
                    mXmbSystemIndex = -1;
                    mXmbGameIndex = 0;
                    mXmbAnimX = -1.0f;
                    mXmbAnimY = 0.0f;
                }
            } else if (returnSysIdx >= 0 && returnSysIdx < (int)mXmbSystems.size()) {
                // Return to specific system + game
                mXmbSystemIndex = returnSysIdx;
                mXmbGameIndex = returnGameIdx;
                mXmbAnimX = (float)returnSysIdx;
                mXmbAnimY = (float)returnGameIdx;
                ALOGD("NanoMenu: returning to system %d game %d",
                      returnSysIdx, returnGameIdx);
            }
            mXmbGameScrollTop = 0;
        }
        // GammaOS: mEffectTime is derived from CLOCK_BOOTTIME per frame in
        // the main loop, so the wallpaper hue cycle advances continuously
        // across nano restarts (returning from an SF/HWC app, force_drm
        // relaunch, etc.) instead of snapping back to a stale persisted
        // value. Still honour an explicit sys-prop override if something
        // set one (diagnostic/test knob only).
        std::string colorPhase = android::base::GetProperty(
                "sys.gammaos.nano.xmb_color_phase", "");
        if (!colorPhase.empty()) {
            mEffectTime = atof(colorPhase.c_str());
            property_set("sys.gammaos.nano.xmb_color_phase", "");
            ALOGD("NanoMenu: color phase override %.2f", mEffectTime);
        }
    }

    // GammaOS: Drastic QR fast-path dedicated render loop.
    //
    // When sDrasticQrFastPath is set (debug smoke-test prop OR the
    // real QR path primed via qr_core="drastic"), NanoMenu takes over
    // the render pipeline directly with a tight loop that drives
    // drastic's getScreenBuffers output into both displays via
    // drmFrameBegin/End. This mirrors the libretro QR loop below but
    // with the dual-screen split (primary = top DS, secondary =
    // bottom DS) and without the normal XMB cycle.
    //
    // Key timing win: the loop starts here at threadLoop entry (right
    // after readyToRun finishes), NOT via NanoMenu::render() which
    // would add another pass of boot-timing overhead. Combined with
    // skipping particle/fx/XMB shader compiles in readyToRun, this
    // brings the drastic first frame ~800ms closer to boot.
    if (sDrasticQrFastPath) {
        DrasticRunner* drastic = DrasticRunner::getInstance();
        if (drastic && drastic->isInitialized()) {
            ALOGW("NanoMenu: drastic QR fast-path loop entered");
            int64_t loopStart = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
            ALOGW("NanoMenu BOOT TIMING: drastic QR loop entry at T+%lldms",
                  loopStart);

            // Max clocks for smooth DS emulation during QR preview.
            system("/vendor/bin/setclock_max.sh");

            bool hasDualDisplay = (sDrmActive && sDrmZeroCopy &&
                                    sAhbTargetSecondary.glFbo != 0);
            drastic->initSurface(mWidth, mHeight, hasDualDisplay);
            drastic->setRotationMatrix(sDrmRotMat);

            // Determine whether this is the real QR path (primed by
            // launchXmbGame) or the smoke-test debug path. The real
            // path will trigger an app handoff when the fade finishes;
            // the smoke path keeps rendering indefinitely.
            bool drasticQrHandoff = false;
            {
                char qc[PROPERTY_VALUE_MAX] = {};
                char qp[PROPERTY_VALUE_MAX] = {};
                property_get("persist.gammaos.nano.qr_core", qc, "");
                property_get("persist.gammaos.nano.qr_prepared", qp, "0");
                drasticQrHandoff = (strcmp(qc, "drastic") == 0) &&
                                   (strcmp(qp, "1") == 0);

                // GammaOS: Development-only toggle to keep the drastic
                // QR loop running indefinitely instead of handing off to
                // the real com.dsemu.drastic activity. Used to profile
                // and tune the preview-mode framerate in isolation from
                // the Android app transition. When set to "1" we pretend
                // handoff is not requested; the per-frame check at
                // `if (t >= 1.0f && drasticQrHandoff ...)` never fires.
                //
                // Uses persist.* so it survives reboot -- the QR loop
                // starts before any post-boot script has a chance to
                // set a volatile sys.* prop.
                char blockHandoff[PROPERTY_VALUE_MAX] = {};
                property_get("persist.gammaos.nano.qr_block_handoff",
                             blockHandoff, "0");
                if (blockHandoff[0] == '1') {
                    drasticQrHandoff = false;
                    ALOGW("drastic QR: handoff blocked via "
                          "persist.gammaos.nano.qr_block_handoff=1 -- "
                          "loop will stay in preview mode");
                }
            }

            // Load the game name once so the overlay has a stable
            // label across the loop. Matches the libretro QR pattern.
            std::string drasticGameName = android::base::GetProperty(
                    "persist.gammaos.nano.qr_game_name", "");
            if (drasticGameName.empty()) {
                // Fallback: scan the cached drastic rom dir for the
                // single staged .nds file. populate_drastic only
                // keeps one ROM at a time.
                DIR* d = opendir(
                        "/data/system/nano_cache/drastic/rom");
                if (d) {
                    struct dirent* e;
                    while ((e = readdir(d)) != nullptr) {
                        std::string name(e->d_name);
                        if (name == "." || name == "..") continue;
                        if (name.size() >= 4 &&
                            name.compare(name.size() - 4, 4, ".nds") == 0) {
                            size_t dotPos = name.rfind('.');
                            drasticGameName = (dotPos != std::string::npos)
                                    ? name.substr(0, dotPos) : name;
                            break;
                        }
                    }
                    closedir(d);
                }
            }
            ALOGI("drastic QR: overlay name=\"%s\" handoff=%d",
                  drasticGameName.c_str(), drasticQrHandoff ? 1 : 0);

            // GammaOS: smoke-test mode (persist.gammaos.nano.drastic_smoke=1)
            // is the dev/profiling path -- no overlay text, full color
            // immediately, and SELECT is forwarded to drastic as a real
            // DS button instead of being eaten as the "drop to XMB"
            // sentinel. Lets us measure the rendering pipeline without
            // any of the boot-time UX overlay.
            bool smokeActive = false;
            {
                char sm[PROPERTY_VALUE_MAX] = {};
                property_get("persist.gammaos.nano.drastic_smoke", sm, "0");
                smokeActive = (sm[0] == '1');
            }

            // GammaOS: Drastic Nano mode. When enabled, drastic runs
            // entirely through DrasticRunner in DRM-direct rendering
            // mode -- the real com.dsemu.drastic app is never launched.
            // Behavior: full color immediately (no desaturation fade),
            // no overlay text, no handoff to the real drastic app.
            // Long-press BACK (3s) exits to XMB. DRM master is held
            // for the entire session for lowest latency.
            bool drasticNanoActive = false;
            {
                char dn[PROPERTY_VALUE_MAX] = {};
                property_get("persist.gammaos.nano.drastic_nano",
                             dn, "0");
                drasticNanoActive = (dn[0] == '1');
                if (drasticNanoActive) {
                    drasticQrHandoff = false;
                    ALOGW("drastic nano: mode active, handoff "
                          "suppressed, DRM-direct rendering");
                }
            }

            float saturation = (smokeActive || drasticNanoActive)
                    ? 1.0f : 0.15f;
            float gradient = (smokeActive || drasticNanoActive)
                    ? 0.0f : 1.0f;
            float textScale = fminf((float)mWidth / 1080.0f,
                                     (float)mHeight / 720.0f);
            if (textScale < 0.5f) textScale = 0.5f;
            float loadScale = 2.5f * textScale;
            float nameScale = 1.5f * textScale;

            // Upload rotation matrices once -- they persist on the
            // text shader until we change programs.
            if (sDrmGlRotation || mTextLocRotation >= 0) {
                glUseProgram(mTextProgram);
                glUniformMatrix2fv(mTextLocRotation, 1, GL_FALSE, sDrmRotMat);
            }

            bool handoffPaused = false;
            bool firstFrameLogged = false;
            int64_t bootCompleteTime = 0;
            bool bootComplete = false;
            bool handoffFired = false;
            // Storage-readiness gate for the QR handoff. We hold the
            // handoff until the QR ROM's underlying volume is mounted
            // (vold defers external SD scan ~3-5s after boot start),
            // otherwise drastic resolves the content URI to "not found"
            // and falls back to its main menu.
            bool handoffStorageWaitLogged = false;
            int64_t handoffWaitStartMs = 0;
            const int64_t handoffWaitTimeoutMs = 10000; // 10s ceiling

            // GammaOS: Drastic QR preview input + handoff control.
            //
            // The preview loop is fully playable -- all buttons
            // including SELECT reach the running DS core via
            // DrasticRunner::setInput. Long-press BACK (3s) exits
            // to the NanoMenu XMB. The handoff fires automatically
            // when the color transition completes and storage is
            // ready.
            int dsBtnMask = 0;
            bool qrCancelled = false;
            bool backWasDown = false;

            // Long-press BACK (3s) to exit to XMB. Track when BACK
            // was first pressed and fire qrCancelled when the hold
            // duration exceeds the threshold.
            int64_t backPressStartMs = 0;
            const int64_t kNanoBackHoldMs = 3000;

            // Shared overlay draw — "Quick Resuming..." + ROM name.
            // Matches the libretro QR loop's pattern at 6687-6702.
            auto drawOverlay = [&](int vpW, int vpH) {
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                const char* msg = "Quick Resuming...";
                float msgW = measureText(msg, loadScale);
                float msgX = ((float)vpW - msgW) / 2.0f;
                float msgY = (float)vpH * 0.78f;
                float pulse = 0.7f + 0.3f * sinf(
                        (float)elapsedRealtime() * 0.004f);
                float textAlpha = pulse * fmaxf(1.2f - saturation, 0.0f);
                if (textAlpha > 0.05f) {
                    if (textAlpha > 1.0f) textAlpha = 1.0f;
                    drawText(msg, msgX, msgY, loadScale,
                             1.0f, 1.0f, 1.0f, textAlpha);
                    if (!drasticGameName.empty()) {
                        float nameW = measureText(
                                drasticGameName.c_str(), nameScale);
                        float nameX = ((float)vpW - nameW) / 2.0f;
                        float nameY = msgY +
                                FONT_CHAR_H * loadScale + 12.0f * textScale;
                        drawText(drasticGameName.c_str(),
                                 nameX, nameY, nameScale,
                                 0.7f, 0.7f, 0.8f, textAlpha * 0.8f);
                    }
                }
                glDisable(GL_BLEND);
            };

            // GammaOS: Track frames for periodic hotplug check. The
            // retrogame_joypad / Xbox Wireless Controller device on
            // RG DS can appear after openInputDevices() runs (~T+7s)
            // because the driver module loads late. Without a hotplug
            // check here, the QR loop never picks up that fd and all
            // face-button events are lost.
            int hotplugCounter = 0;

            // GammaOS: Triple-buffer AHB ring opt-in for this QR session.
            // Read persist.gammaos.nano.triple_buffer ONCE at loop entry so
            // toggling it mid-run doesn't desync ring cursors. When on, the
            // QR render body writes into sAhbRingPrimary[renderIdx] and the
            // glFinish() in drmFlipRingSlot presents slot (renderIdx-2) --
            // see docs/triple-buffer-plan.md for rationale. Falls back to
            // single-buffered slot 0 if any required slot wasn't allocated
            // (OOM path), so the prop is safe to leave on by default.
            bool qrUseTripleBuffer = false;
            {
                char prop[PROPERTY_VALUE_MAX] = {};
                property_get("persist.gammaos.nano.triple_buffer", prop, "0");
                qrUseTripleBuffer = (prop[0] == '1');
                if (qrUseTripleBuffer) {
                    // Every primary slot must exist. When hasDualDisplay,
                    // every secondary slot must exist too.
                    for (int i = 0; i < AHB_RING_DEPTH; i++) {
                        if (sAhbRingPrimary[i].glFbo == 0 ||
                            (hasDualDisplay && sAhbRingSecondary[i].glFbo == 0)) {
                            qrUseTripleBuffer = false;
                            ALOGW("drastic QR: triple_buffer disabled, "
                                  "slot %d not fully allocated", i);
                            break;
                        }
                    }
                }
                if (qrUseTripleBuffer) {
                    sRingRenderIdx = 0;
                    sRingPresentIdx = 0;
                    sRingPrimedCount = 0;
                }
                ALOGW("drastic QR: triple_buffer=%d",
                      qrUseTripleBuffer ? 1 : 0);
            }

            while (!exitPending() && !qrCancelled) {
                // Check for new input devices every ~0.5s (30 frames
                // at 60fps). Cheap: inotify_read is non-blocking.
                if (++hotplugCounter >= 30) {
                    hotplugCounter = 0;
                    checkInputHotplug();
                }

                // GammaOS: Poll input → DS button mask. Matches the
                // libretro QR input block at NanoMenu.cpp:~6968. We
                // drain each gamepad fd with non-blocking reads and
                // accumulate a sticky mask (dsBtnMask) so held buttons
                // stay held across frames. Axis D-pad (ABS_HAT0X/Y) is
                // handled alongside key-code D-pad so generic controllers
                // and the internal RG DS pad both work.
                //
                // Special control keys (not forwarded to drastic):
                //   KEY_BACK    → long-press (3s) exits to XMB
                // All other buttons (including SELECT) are forwarded
                // to drastic as normal DS inputs.
                for (int fd : mInputFds) {
                    struct input_event ev;
                    while (read(fd, &ev, sizeof(ev)) == sizeof(ev)) {
                        if (ev.type == EV_KEY) {
                            const bool pressed = (ev.value != 0);
                            // BACK: short-press toggles overlay
                            // + handoff pause. Long-press (3s)
                            // exits to XMB.
                            if (ev.code == KEY_BACK) {
                                if (pressed && !backWasDown) {
                                    backPressStartMs =
                                            elapsedRealtime();
                                }
                                if (!pressed && backWasDown) {
                                    int64_t held = elapsedRealtime()
                                            - backPressStartMs;
                                    if (held < kNanoBackHoldMs) {
                                        handoffPaused = !handoffPaused;
                                        if (handoffPaused) {
                                            saturation = 1.0f;
                                            gradient = 0.0f;
                                        }
                                    }
                                    backPressStartMs = 0;
                                }
                                backWasDown = pressed;
                                continue;
                            }
                            // Gamepad buttons → DS bitmask. Nintendo
                            // face-layout mapping matches what libretro
                            // QR uses (evdev BTN_A/B/X/Y are xbox-style
                            // south/east/north/west — Nintendo layout
                            // swaps A<->B and X<->Y).
                            auto bit = [&](int mask) {
                                if (pressed) dsBtnMask |=  mask;
                                else         dsBtnMask &= ~mask;
                            };
                            switch (ev.code) {
                            // Label mapping matching NanoMenu XMB:
                            // BTN_SOUTH=A(confirm), BTN_EAST=B(back),
                            // BTN_NORTH=X, BTN_WEST=Y.
                            case BTN_SOUTH:   bit(DrasticRunner::kDsBtnA);     break;
                            case BTN_EAST:    bit(DrasticRunner::kDsBtnB);     break;
                            case BTN_NORTH:   bit(DrasticRunner::kDsBtnX);     break;
                            case BTN_WEST:    bit(DrasticRunner::kDsBtnY);     break;
                            case BTN_TL:
                            case KEY_L:       bit(DrasticRunner::kDsBtnL);     break;
                            case BTN_TR:
                            case KEY_R:       bit(DrasticRunner::kDsBtnR);     break;
                            case BTN_START:   bit(DrasticRunner::kDsBtnStart); break;
                            case BTN_SELECT:  bit(DrasticRunner::kDsBtnSelect); break;
                            case KEY_UP:      bit(DrasticRunner::kDsBtnUp);    break;
                            case KEY_DOWN:    bit(DrasticRunner::kDsBtnDown);  break;
                            case KEY_LEFT:    bit(DrasticRunner::kDsBtnLeft);  break;
                            case KEY_RIGHT:   bit(DrasticRunner::kDsBtnRight); break;
                            default: break;
                            }
                        } else if (ev.type == EV_ABS) {
                            // D-pad hat axes → discrete DS D-pad bits.
                            // Touchscreen ABS_X/Y is filtered out by
                            // the non-HAT code (we don't plumb touch
                            // into drastic in this phase).
                            if (ev.code == ABS_HAT0X) {
                                dsBtnMask &= ~(DrasticRunner::kDsBtnLeft |
                                               DrasticRunner::kDsBtnRight);
                                if (ev.value < 0) dsBtnMask |= DrasticRunner::kDsBtnLeft;
                                if (ev.value > 0) dsBtnMask |= DrasticRunner::kDsBtnRight;
                            } else if (ev.code == ABS_HAT0Y) {
                                dsBtnMask &= ~(DrasticRunner::kDsBtnUp |
                                               DrasticRunner::kDsBtnDown);
                                if (ev.value < 0) dsBtnMask |= DrasticRunner::kDsBtnUp;
                                if (ev.value > 0) dsBtnMask |= DrasticRunner::kDsBtnDown;
                            }
                        }
                    }
                    if (qrCancelled) break;
                }
                if (qrCancelled) break;

                // Long-press BACK (3s) exits to XMB. Checked every
                // frame so the exit fires promptly once the hold
                // threshold is crossed.
                if (backPressStartMs > 0) {
                    int64_t held = elapsedRealtime() - backPressStartMs;
                    if (held >= kNanoBackHoldMs) {
                        qrCancelled = true;
                        ALOGW("drastic QR: BACK held %lldms, "
                              "exiting to XMB", (long long)held);
                        break;
                    }
                }

                // Push the accumulated button state to drastic.
                // All buttons including SELECT go through as normal
                // DS inputs. Exit is via long-press BACK (3s).
                drastic->setInput(dsBtnMask);

                // GammaOS: Per-phase timing for the drastic QR render
                // loop. Only emits a log line when the overall frame
                // exceeded the 16.67 ms vblank budget, so it stays
                // quiet on the 97%+ of frames that hit vsync cleanly.
                // Used to pinpoint which phase is eating time during
                // the occasional microhitch.
                auto nowUs = []() {
                    struct timespec ts;
                    clock_gettime(CLOCK_MONOTONIC, &ts);
                    return (int64_t)ts.tv_sec * 1000000LL
                            + ts.tv_nsec / 1000LL;
                };
                const int64_t phaseT0 = nowUs();

                // Render both DS screens into the offscreen FBO via
                // drastic's renderFrame (hi-res 3D, all layers).
                // Returns immediately (no-op) until the DS producer
                // has generated its first frame.
                drastic->renderDsToOffscreen();
                const int64_t phaseT1 = nowUs();

                // GammaOS: Draw the "Quick Resuming... / ROM name"
                // overlay in real QR mode. In smoke mode (debug /
                // pipeline-profiling path) we suppress it -- no overlay
                // text and no fade gradient -- so the visible output
                // is exactly what drastic produced.
                const bool showOverlay = !smokeActive && !drasticNanoActive && !handoffPaused;

                // GammaOS: secondary always renders + flips. An earlier
                // half-rate optimization saved CPU by alternating
                // secondary work, but that aliasing meant some present
                // slots had no fresh secondary content and the bottom
                // screen flashed black. Once DRM PRIME landed the
                // per-iter secondary cost dropped (~1 ms instead of
                // ~5 ms with the CPU blit), so the savings weren't
                // worth the bug. If we want to halve secondary again
                // we need to render INTO the slots that will actually
                // be presented to secondary -- not every-other render
                // iter -- which requires syncing render parity to the
                // 3-iter present lag.
                bool secondaryThisIter = true;

                // GammaOS: Triple-buffer ring slot for this frame's render
                // pass. When qrUseTripleBuffer is on, rotates through the 3
                // slots so the present-side glFinish() inside drmFlipRingSlot
                // observes work submitted ~2 frames earlier -- almost always
                // complete, so the wait is microseconds instead of 7-17 ms
                // when Mali kbase housekeeping hits. When off, always slot 0
                // (exactly the classic single-buffered path).
                const int renderIdx = qrUseTripleBuffer ? sRingRenderIdx : 0;
                AhbRenderTarget& primTgt = sAhbRingPrimary[renderIdx];
                AhbRenderTarget& secTgt  = sAhbRingSecondary[renderIdx];

                // GammaOS: Each render pass draws drastic's full-viewport
                // blit quad (DrasticRunner::drawDsQuad uses NDC -1..+1
                // vertices, which still cover the entire viewport after
                // any 90/180/270 rotation). The previous glClearColor +
                // glClear before each drawDsQuad was therefore redundant
                // -- the blit overwrites every pixel. Removing the clear
                // cuts ~0.5-1 ms of GPU work per pass on RG DS, ~2-3 ms
                // on 1080p panels.
                if (hasDualDisplay) {
                    // Pass 1: secondary display -> bottom DS screen.
                    // Gated on secondaryThisIter so we do half the work
                    // on dual-display setups; secondary then runs at
                    // 30 fps which is fine for the bottom DS screen.
                    if (secondaryThisIter) {
                        glBindFramebuffer(GL_FRAMEBUFFER, secTgt.glFbo);
                        glViewport(0, 0, secTgt.w, secTgt.h);
                        drastic->renderBottomScreen(saturation, gradient);
                        if (showOverlay) {
                            // drawText hard-codes mWidth/mHeight for
                            // pixel->NDC (the logical landscape space;
                            // rotation handled by the text shader's
                            // uRotation uniform). Using AHB dims here
                            // would mis-project text on any device
                            // where primary AHB size != mWidth/mHeight
                            // (e.g. portrait panels pushed through a
                            // landscape logical surface, like RK3576
                            // 1080x1920).
                            drawOverlay(mWidth, mHeight);
                        }
                    }

                    // Pass 2: primary display -> top DS screen.
                    glBindFramebuffer(GL_FRAMEBUFFER, primTgt.glFbo);
                    if (sDrmGlRotation) {
                        glViewport(0, 0, primTgt.w, primTgt.h);
                    } else {
                        glViewport(0, 0, mWidth, mHeight);
                    }
                    drastic->renderTopScreen(saturation, gradient);
                } else {
                    // Single display: both screens stacked.
                    glBindFramebuffer(GL_FRAMEBUFFER, primTgt.glFbo);
                    if (sDrmGlRotation) {
                        glViewport(0, 0, primTgt.w, primTgt.h);
                    } else {
                        glViewport(0, 0, mWidth, mHeight);
                    }
                    drastic->renderBothScreens(saturation, gradient);
                }
                if (showOverlay) {
                    // drawText always uses mWidth/mHeight internally for
                    // pixel->NDC. Passing AHB dims here drops the text off
                    // the visible NDC region on any device where the AHB
                    // is panel-native (portrait) but the logical surface
                    // is landscape (RK3576 case: AHB 1080x1920, mWidth/H
                    // 1920x1080 -> text msgY=0.78*1920=1497 becomes NDC
                    // y=-1.77, clipped). The rotation matrix already maps
                    // mWidth/mHeight-space NDC to the panel via the text
                    // shader's uRotation uniform.
                    drawOverlay(mWidth, mHeight);
                }
                const int64_t phaseT2 = nowUs();

                if (qrUseTripleBuffer) {
                    // Unbind so subsequent state doesn't accidentally land
                    // on the AHB FBO. eglCreateSyncKHR with NATIVE_FENCE
                    // flushes implicitly, so we do NOT call glFlush --
                    // the fence object IS our kick-and-record.
                    glBindFramebuffer(GL_FRAMEBUFFER, 0);
                    // Record a per-slot EGL native fence. Signals when
                    // every GL command issued to this point (including
                    // this slot's AHB renders) has completed on the GPU.
                    // At present time -- 2 iters later, when this slot
                    // rotates back into presentIdx -- drmFlipRingSlot
                    // will dup this fence's fd and pass it to
                    // AHardwareBuffer_lock for a per-slot dma-fence wait.
                    // That sidesteps the global glFinish drain that was
                    // the original ring's bottleneck.
                    // ITER4 diagnostic: skip fence creation and rely on
                    // kernel implicit dma-fence sync. DRM page_flip is
                    // supposed to wait on the AHB dma-buf's implicit
                    // write fence before starting scanout, so the
                    // explicit EGL fence might be redundant on this
                    // stack. Gated behind a prop so we can A/B without
                    // re-flash.
                    static int sPrimeNoFence = -1;
                    if (sPrimeNoFence < 0) {
                        char p[PROPERTY_VALUE_MAX] = {};
                        property_get("persist.gammaos.nano.prime_no_fence",
                                     p, "0");
                        sPrimeNoFence = (p[0] == '1') ? 1 : 0;
                        ALOGW("NanoMenu QR: prime_no_fence=%d",
                              sPrimeNoFence);
                    }

                    if (sPrimeNoFence) {
                        // Skip fence. Just kick GPU commands into flight
                        // and let kernel handle sync via the dma-buf
                        // implicit fence.
                        glFlush();
                    } else if (sEglCreateSyncKHR && sRingEglDpy != EGL_NO_DISPLAY) {
                        // If a previous fence is still hanging around
                        // (e.g. lock failed earlier and didn't consume
                        // it), destroy it before overwriting.
                        if (sAhbRingSyncPrimary[renderIdx] != EGL_NO_SYNC_KHR
                            && sEglDestroySyncKHR) {
                            sEglDestroySyncKHR(sRingEglDpy,
                                    sAhbRingSyncPrimary[renderIdx]);
                        }
                        sAhbRingSyncPrimary[renderIdx] = sEglCreateSyncKHR(
                                sRingEglDpy,
                                EGL_SYNC_NATIVE_FENCE_ANDROID, nullptr);
                        if (sAhbRingSyncPrimary[renderIdx] == EGL_NO_SYNC_KHR) {
                            // Fence creation failed -- glFinish fallback
                            // in drmFlipRingSlot will kick in.
                            glFlush();
                        }
                    } else {
                        glFlush();
                    }
                    sRingRenderIdx = (renderIdx + 1) % AHB_RING_DEPTH;
                    // Bootstrap: first AHB_RING_DEPTH-1 iterations just
                    // render and don't present, so the ring gets primed
                    // with real content before we start rotating.
                    // Threshold = 2 (not depth-1) keeps present-lag at 2 regardless of
// ring depth. With depth N and lag L, slot M is rendered at iter M
// and re-rendered at iter M+N, but display still owns it through
// iter M+L+1. Race-free requires L <= N-2. With depth 4 and L=2
// (this threshold), we have 1 slot of headroom = no GL/scanout
// races on the DRM PRIME path.
if (sRingPrimedCount >= 2) {
                        const int presentIdx = sRingPresentIdx;
                        // skipNonPrimary mirrors the secondary render
                        // gating above: secondary is rendered AND
                        // flipped only on alternating iters, halving
                        // the dual-display work without leaving stale
                        // content (skipped iter just keeps the last
                        // good frame on screen).
                        drmFlipRingSlot(presentIdx, !secondaryThisIter);
                        sRingPresentIdx =
                                (presentIdx + 1) % AHB_RING_DEPTH;
                    } else {
                        sRingPrimedCount++;
                    }
                } else {
                    drmFrameEnd(mDisplay, mSurface);
                }
                const int64_t phaseT3 = nowUs();

                // Vsync: block until the primary display's next
                // vertical blank. Without this, the loop runs
                // unthrottled (~2ms/frame on Mali G52) and the DRM
                // page flip with flags=0 is fire-and-forget, causing
                // severe jitter/tearing at ~45fps effective. The
                // vblank wait gates the loop to exactly 60fps (or
                // whatever the panel refresh rate is).
                // Render-loop sync. Try DRM_IOCTL_WAIT_VBLANK first -- works
                // on every panel with periodic vblank interrupts (RG DS /
                // RK3568) and avoids the cross-CRTC event timing skew that
                // page-flip-event drain hits on dual-display setups. On
                // panels where the kernel's vblank queue never wakes
                // (RK3576 DSI command-mode), the first call hits the 3s
                // timeout, sDrmVblankBroken flips, and from then on
                // drmDrainPageFlipEvents() is the sync gate.
                //
                // The whole gate can be disabled at runtime with
                // persist.gammaos.nano.vsync=0 -- diagnostic only, lets us
                // measure vsync overhead vs other sources of jitter.
                if (sVsyncEnabled < 0) {
                    char vp[PROPERTY_VALUE_MAX] = {};
                    property_get("persist.gammaos.nano.vsync", vp, "1");
                    sVsyncEnabled = (vp[0] == '0') ? 0 : 1;
                    ALOGW("NanoMenu QR vsync gate %s",
                          sVsyncEnabled ? "ENABLED" : "DISABLED");
                }
                if (sVsyncEnabled) {
                    // Multi-CRTC setups (RG DS dual DSI) and broken-vblank
                    // panels pace via drmDrainPageFlipEvents instead of
                    // WAIT_VBLANK so the sync gate waits for flips on every
                    // display to complete. See drmFlipRingSlot for why.
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
                            ALOGW("NanoMenu QR: DRM_IOCTL_WAIT_VBLANK took %lld us "
                                  "-- switching to page-flip-event pacing",
                                  (long long)vblElapsed);
                        }
                    }
                    drmDrainPageFlipEvents();
                } else {
                    // Vsync disabled: cap loop at 60 fps via usleep so we
                    // compare CPU fairly. Tearing expected.
                    drmPaceWithoutVsync();
                }
                const int64_t phaseT4 = nowUs();

                // Log per-phase breakdown for every frame that takes
                // longer than one vblank. No rate limit -- we need to
                // see the full cadence of stutter events during 6s
                // jitter debugging.
                {
                    int64_t total = phaseT4 - phaseT0;
                    if (total > 17000) {
                        ALOGW("drastic QR slow frame: "
                              "total=%lldus | renderDs=%lldus "
                              "gl=%lldus flip=%lldus vblank=%lldus",
                              (long long)total,
                              (long long)(phaseT1 - phaseT0),
                              (long long)(phaseT2 - phaseT1),
                              (long long)(phaseT3 - phaseT2),
                              (long long)(phaseT4 - phaseT3));
                    }
                }

                // GammaOS: Per-second FPS counter for the drastic QR loop.
                // Mirrors the XMB FPS counter further down threadLoop.
                // Zero runtime overhead when handoff is imminent and the
                // loop is about to exit, so always on. Min/max frame
                // time (us) is reported alongside so we can see how
                // tight the vsync lock is.
                {
                    static int64_t sQrFpsWindowStartNs = 0;
                    static int sQrFpsFrames = 0;
                    static int64_t sQrFpsMinFrameUs = 0;
                    static int64_t sQrFpsMaxFrameUs = 0;
                    static int64_t sQrFpsLastFrameNs = 0;
                    int64_t nowNsFps;
                    {
                        struct timespec ts;
                        clock_gettime(CLOCK_MONOTONIC, &ts);
                        nowNsFps = (int64_t)ts.tv_sec * 1000000000LL
                                   + ts.tv_nsec;
                    }
                    if (sQrFpsLastFrameNs != 0) {
                        int64_t frameUs =
                                (nowNsFps - sQrFpsLastFrameNs) / 1000LL;
                        // Catch stalls that land BETWEEN phaseT4 of one
                        // iteration and phaseT0 of the next (outside the
                        // phase-timed region). Compares full iter-to-iter
                        // time to the 17ms vblank budget.
                        if (frameUs > 17500) {
                            ALOGW("drastic QR iter gap: frameUs=%lldus "
                                  "(interiter stall not in phase log)",
                                  (long long)frameUs);
                        }
                        sQrFpsFrames++;
                        if (sQrFpsFrames == 1 ||
                                frameUs < sQrFpsMinFrameUs) {
                            sQrFpsMinFrameUs = frameUs;
                        }
                        if (frameUs > sQrFpsMaxFrameUs) {
                            sQrFpsMaxFrameUs = frameUs;
                        }
                        if (sQrFpsWindowStartNs == 0) {
                            sQrFpsWindowStartNs = nowNsFps;
                        }
                        int64_t elapsedNs =
                                nowNsFps - sQrFpsWindowStartNs;
                        if (elapsedNs >= 1000000000LL) {
                            float fps = (float)sQrFpsFrames * 1e9f
                                        / (float)elapsedNs;
                            ALOGW("drastic QR FPS: %.1f "
                                  "(%d frames / %lld.%03lld s, "
                                  "min=%lldus max=%lldus)",
                                  fps, sQrFpsFrames,
                                  elapsedNs / 1000000000LL,
                                  (elapsedNs / 1000000LL) % 1000,
                                  sQrFpsMinFrameUs, sQrFpsMaxFrameUs);
                            sQrFpsWindowStartNs = nowNsFps;
                            sQrFpsFrames = 0;
                            sQrFpsMinFrameUs = 0;
                            sQrFpsMaxFrameUs = 0;
                        }
                    }
                    sQrFpsLastFrameNs = nowNsFps;
                }

                if (!firstFrameLogged) {
                    int64_t now = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
                    ALOGW("NanoMenu BOOT TIMING: drastic first frame at T+%lldms",
                          (long long)now);
                    firstFrameLogged = true;
                }

                // Check for handoff conditions
                char val[PROPERTY_VALUE_MAX] = {};
                bool ready = false;
                property_get("sys.gammaos.nano.home_launching", val, "");
                ready = (strcmp(val, "1") == 0);
                if (!ready) {
                    property_get("sys.boot_completed", val, "0");
                    ready = (strcmp(val, "1") == 0);
                }
                if (ready && !bootComplete) {
                    bootComplete = true;
                    bootCompleteTime = elapsedRealtime();
                    ALOGI("drastic QR: transitioning to full color");
                }

                if (bootComplete && !drasticNanoActive && !handoffPaused) {
                    // Fade from desaturated to full color over 800ms.
                    // Drastic nano skips this entirely (starts at full
                    // color, no transition period).
                    int64_t elapsed = elapsedRealtime() - bootCompleteTime;
                    float t = fminf((float)elapsed / 800.0f, 1.0f);
                    t = 1.0f - (1.0f - t) * (1.0f - t);
                    saturation = 0.35f + t * 0.65f;
                    gradient = 0.7f * (1.0f - t);
                    // When fade completes, hand off to the real
                    // drastic activity — identical pattern to
                    // LibretroRunner's handoff to RetroArch at line
                    // ~6645. The intent file was already written by
                    // launchXmbGame() when the user selected the NDS
                    // game; we just need to kick
                    // NanoRelaunchMonitor via do_launch so it reads
                    // the file and starts DraSticActivity.
                    //
                    // Smoke-test mode (qr_core != "drastic") never
                    // fires handoff -- the smoke test runs the DS
                    // indefinitely for interactive debugging.
                    //
                    if (t >= 1.0f && drasticQrHandoff && !handoffFired) {
                        // Gate handoff on the QR ROM's storage being
                        // ready. If the ROM lives on external SD, vold
                        // mounts the volume ~3-5s after boot starts,
                        // and the handoff content URI cannot resolve
                        // before then. Hold the handoff (keep rendering
                        // the loading screen) until the mount is up, or
                        // until handoffWaitTimeoutMs has elapsed as a
                        // safety net.
                        bool storageReady = isQrRomStorageReady();
                        if (!storageReady) {
                            if (handoffWaitStartMs == 0) {
                                handoffWaitStartMs = elapsedRealtime();
                            }
                            int64_t waited = elapsedRealtime() - handoffWaitStartMs;
                            if (!handoffStorageWaitLogged) {
                                ALOGI("drastic QR: handoff held -- "
                                      "waiting for QR ROM storage mount");
                                handoffStorageWaitLogged = true;
                            }
                            if (waited < handoffWaitTimeoutMs) {
                                // Skip the handoff this frame; the next
                                // frame will retry. Continue rendering
                                // the loading-style overlay so the user
                                // sees a steady screen.
                                continue;
                            }
                            ALOGW("drastic QR: storage wait timed out "
                                  "after %lldms -- firing handoff anyway",
                                  (long long)waited);
                        } else if (handoffStorageWaitLogged) {
                            int64_t waited = elapsedRealtime() - handoffWaitStartMs;
                            ALOGI("drastic QR: storage ready after %lldms wait",
                                  (long long)waited);
                        }
                        handoffFired = true;

                        // GammaOS: drastic-nano gate -- route the
                        // handoff to the standalone binary instead of
                        // the com.dsemu.drastic APK activity when the
                        // user has opted into drastic-nano mode. The
                        // binary uses the real (unpatched) libdrastic
                        // so audio works.
                        bool handoffToDrasticNano = false;
                        {
                            char dn[PROPERTY_VALUE_MAX] = {};
                            property_get(
                                "persist.gammaos.nano.drastic_nano",
                                dn, "0");
                            handoffToDrasticNano = (dn[0] == '1');
                        }
                        if (handoffToDrasticNano) {
                            ALOGI("drastic QR: handoff to "
                                  "drastic-nano binary");
                            // Reuse the QR ROM path as the drastic-nano
                            // ROM path -- the binary reads from the
                            // nano_drastic_nano_rom.txt file.
                            std::string qrRom = getQrRomPath();
                            if (!qrRom.empty()) {
                                setDrasticNanoRomPath(qrRom);
                            }
                            // Clear QR primed state so when
                            // gammaos-nano restarts after drastic-nano
                            // exits, it comes up in plain XMB mode
                            // instead of re-entering the preview.
                            property_set(
                                "persist.gammaos.nano.qr_prepared", "0");
                            property_set(
                                "sys.gammaos.drastic_nano.start", "1");
                            property_set(
                                "sys.gammaos.nano.drop_input", "1");
                            mExitRequested = true;
                            break;
                        }
                        ALOGI("drastic QR: handoff to com.dsemu.drastic");
                        // Recreate /data/system/nano_launch_intent.txt
                        // from the persistent copy written by
                        // launchXmbGame's drastic QR prime path. The
                        // original intent file is consumed and deleted
                        // by RootWindowContainer on first launch; the
                        // persistent stash is our source of truth for
                        // every subsequent QR-triggered launch. Without
                        // this, the framework falls back to the plain
                        // LAUNCHER intent and drastic opens on its own
                        // main menu instead of the primed game.
                        {
                            const char* src =
                                    "/data/system/nano_drastic_qr_intent.txt";
                            const char* dst =
                                    "/data/system/nano_launch_intent.txt";
                            int sfd = open(src, O_RDONLY);
                            if (sfd >= 0) {
                                char buf[4096];
                                ssize_t n = read(sfd, buf, sizeof(buf));
                                close(sfd);
                                if (n > 0) {
                                    int dfd = open(
                                            dst,
                                            O_WRONLY | O_CREAT | O_TRUNC,
                                            0666);
                                    if (dfd >= 0) {
                                        write(dfd, buf, (size_t)n);
                                        close(dfd);
                                        chmod(dst, 0644);
                                        ALOGI("drastic QR: restored intent "
                                              "file from QR stash (%zd bytes)",
                                              n);
                                    } else {
                                        ALOGW("drastic QR: failed to open "
                                              "%s for write: %s",
                                              dst, strerror(errno));
                                    }
                                } else {
                                    ALOGW("drastic QR: QR intent stash "
                                          "empty or unreadable");
                                }
                            } else {
                                ALOGW("drastic QR: no QR intent stash at "
                                      "%s, framework will fall back to "
                                      "LAUNCHER", src);
                            }
                        }
                        android::base::SetProperty(
                                "sys.gammaos.nano.launch_app",
                                "com.dsemu.drastic");
                        android::base::SetProperty(
                                "sys.gammaos.nano.launch_intent", "file");
                        android::base::SetProperty(
                                "sys.gammaos.nano.launch_rom", "");
                        android::base::SetProperty(
                                "sys.gammaos.nano.launch_core", "");
                        // mXmbSystems is empty under the fast path so
                        // we can't compute a precise return position;
                        // default to recently-played (sys=-1).
                        property_set(
                                "sys.gammaos.nano.xmb_return_sys", "-1");
                        property_set(
                                "sys.gammaos.nano.xmb_return_game", "0");
                        property_set(
                                "sys.gammaos.nano.return_recent", "0");
                        property_set(
                                "sys.gammaos.nano.drop_input", "1");
                        // Fire both the direct do_launch trigger and
                        // the legacy nano_retroarch property. The
                        // direct trigger is the primary mechanism;
                        // the legacy prop keeps init-driven
                        // side-effects firing (bootanim exit etc).
                        // Set handoff_fired first so the home-launch
                        // gate in startHomeOnTaskDisplayArea opens
                        // immediately; otherwise we race the init.rc
                        // action that sets bootanim.exit=1.
                        property_set(
                                "sys.gammaos.nano.handoff_fired", "1");
                        property_set(
                                "sys.gammaos.nano.pending_exit", "0");
                        property_set(
                                "sys.gammaos.nano.do_launch", "1");
                        property_set(
                                "service.bootanim.nano_retroarch", "1");
                        // DrasticRunner keeps running in the
                        // background until the process exits -- the
                        // real drastic activity will launch from a
                        // cold DS BIOS but resume via drastic's own
                        // autosave, which is identical UX to
                        // launching drastic through the framework.
                        mExitRequested = true;
                        break;
                    }
                } else if (!smokeActive && !drasticNanoActive && !handoffPaused) {
                    // Slow creep toward color while we're still in the
                    // pre-handoff "preview" period. Smoke/drastic-nano
                    // modes skip this -- saturation/gradient stay at
                    // 1.0/0.0 so the output is exactly what drastic
                    // produced (no fade overlay).
                    saturation = fminf(saturation + 0.0003f, 0.35f);
                    gradient = fmaxf(gradient - 0.0002f, 0.7f);
                }

            }

            // GammaOS: BACK long-press cancel flow. The user held BACK
            // during QR preview; we need to drop them to the NanoMenu
            // XMB, but readyToRun() skipped XMB shader/texture/system
            // init because sDrasticQrFastPath was active, so falling
            // through to the XMB render loop would crash on a null
            // program. The simplest safe path is to exit this nano
            // process and let init respawn it with qr_prepared=0 so
            // the next instance takes the normal (non-QR) startup.
            //
            // We can't just setprop sys.gammaos.nano.restart=1 before
            // exit: init's `start gammaos-nano` fires while we're
            // still alive and sees the service already running (no-op).
            // Instead spawn a backgrounded shell helper via system()
            // that waits ~500 ms (so init sees us fully exited) and
            // then sets the restart prop. The helper is reparented to
            // init on our exit, so it outlives the current process.
            if (qrCancelled) {
                if (drasticNanoActive) {
                    ALOGW("drastic nano: exit -- clearing QR state, "
                          "releasing DRM, restarting to XMB");
                } else {
                    ALOGI("drastic QR: cancel flow -- clearing QR, "
                          "asking init to respawn nano");
                }
                property_set("persist.gammaos.nano.qr_prepared", "0");
                property_set("persist.gammaos.nano.qr_core", "");
                // Clear drastic nano ROM path so stale paths don't
                // persist across sessions.
                setDrasticNanoRomPath("");
                // Clear force_drm so the restarted NanoMenu instance
                // does NOT grab DRM master post-boot. HWC/SF resume
                // compositing normally once DRM master is released by
                // our process exit.
                property_set("sys.gammaos.nano.force_drm", "0");
                // Don't call pauseDrastic() here -- drastic's
                // pauseSystem JNI entry tries to synchronize with
                // internal worker threads and deadlocks when called
                // from the render thread. The process is about to
                // exit anyway; drastic's threads will be killed by
                // the kernel on process teardown.
                //
                // Trigger a deferred restart via init.rc. The trigger
                // sleeps 1s (so init sees us fully exited) then sets
                // sys.gammaos.nano.restart=1. We can't use system()
                // or fork() here because drastic's worker threads
                // hold mutexes that deadlock the forked child.
                property_set(
                        "sys.gammaos.nano.restart_after_cancel", "1");
                // _exit() terminates the entire process immediately.
                // return false only kills the NanoMenu thread but
                // main() is stuck in IPCThreadState::joinThreadPool
                // which never returns, so the process stays alive
                // and init can't restart it.
                _exit(0);
            }

            ALOGI("NanoMenu: drastic QR loop exiting "
                  "(handoff=%d exitRequested=%d)",
                  handoffFired ? 1 : 0,
                  mExitRequested ? 1 : 0);
            // Intentionally fall through to the rest of threadLoop()
            // instead of returning false immediately. The
            // libretro-QR block below is guarded against
            // qr_core="drastic" (just logs and no-ops), and the main
            // XMB render loop at ~7049 is gated on !mExitRequested
            // (which we already set during handoff). By falling
            // through, control reaches the common cleanup path at
            // ~7325 which clears sys.gammaos.nano.menu_active=0 AND
            // runs the "Loading..." screen until the real drastic
            // activity binds. Both are critical:
            //   1) Clearing menu_active unblocks DualStackController
            //      so it can mirror drastic's window from the primary
            //      display to the secondary, matching the retroarch
            //      dualstack behavior. Without this, the secondary
            //      display stays frozen on NanoMenu's last QR-preview
            //      frame while drastic only draws to the primary.
            //   2) The loading-screen render loop keeps gammaos-nano
            //      painting both displays via DRM while the real
            //      drastic is coming up, so there is no visible gap
            //      between NanoMenu exit and drastic's first frame.
        } else {
            ALOGW("NanoMenu: drastic QR fast-path active but DrasticRunner "
                  "not initialized -- exiting to avoid shader crash");
            // The fast path skipped compiling particle/fx/XMB shaders,
            // so the normal render() would crash on glUseProgram(0).
            // Safer to exit and let init restart us.
            mExitRequested = true;
            return false;
        }
    }

    // Quick Resume: auto-launch into saved game on boot if prepared
    if (mQuickResumeEnabled) {
        std::string qrPrepared = android::base::GetProperty(
                "persist.gammaos.nano.qr_prepared", "0");
        if (qrPrepared == "1") {
            std::string qrRom = getQrRomPath();
            std::string qrCore = android::base::GetProperty(
                    "persist.gammaos.nano.qr_core", "");

            // If a prior NanoMenu instance already fired the handoff
            // (exited after setting do_launch=1), skip QR entirely.
            // Re-running QR after handoff confuses the relaunch
            // monitor and leaves RetroArch dead. Guard with a sys
            // property: auto-cleared on reboot, but survives the
            // NanoMenu respawn that init triggers mid-boot after
            // handoff. File-based flags are unreliable because the
            // graphics-uid NanoMenu can create them but not always
            // unlink across boots.
            {
                char hf[PROPERTY_VALUE_MAX] = {};
                property_get("sys.gammaos.nano.handoff_fired", hf, "0");
                if (strcmp(hf, "1") == 0) {
                    ALOGW("Quick Resume: handoff already fired "
                          "(handoff_fired=1), skipping QR");
                    qrRom.clear();
                    qrCore.clear();
                }
            }

            // GammaOS: Skip the libretro QR path for the drastic
            // sentinel. The drastic QR render loop above handles
            // qr_core="drastic" entirely (and returns false before
            // we reach here in the normal flow). This guard is a
            // safety net for races where sDrasticQrFastPath was not
            // set at readyToRun time but the prop is now "drastic".
            if (qrCore == "drastic") {
                ALOGW("Quick Resume: qr_core=drastic reached libretro "
                      "branch unexpectedly (sDrasticQrFastPath=%d) -- "
                      "skipping libretro launch", sDrasticQrFastPath ? 1 : 0);
            } else if (!qrRom.empty() && !qrCore.empty()) {
                // GammaOS: Ensure rotation uniforms are set for the QR screens.
                // The QR path runs before render(), which uploads the matrix
                // every frame. Without this, the text shader's uRotation is
                // the GL default zero matrix in HWC mode, collapsing all text
                // vertices to origin. Always upload — identity in HWC mode,
                // rotation matrix in DRM mode.
                {
                    const GLuint progs[] = {mShaderProgram, mTextProgram};
                    const GLint  locs[]  = {mLocRotation, mTextLocRotation};
                    for (int i = 0; i < 2; i++) {
                        glUseProgram(progs[i]);
                        glUniformMatrix2fv(locs[i], 1, GL_FALSE, sDrmRotMat);
                    }
                }

                    ALOGI("Quick Resume: launching ROM=%s CORE=%s",
                          qrRom.c_str(), qrCore.c_str());

                    // Find matching system + game for return navigation.
                    // Extract ROM filename and parent dir from qrRom path.
                    int returnSysIdx = -1, returnGameIdx = 0;
                    {
                        std::string romFile = qrRom;
                        size_t lastSlash = romFile.rfind('/');
                        std::string romFilename = (lastSlash != std::string::npos)
                                ? romFile.substr(lastSlash + 1) : romFile;
                        // Parent dir is the system dir (e.g. "snes")
                        std::string parentDir;
                        if (lastSlash != std::string::npos && lastSlash > 0) {
                            size_t prevSlash = romFile.rfind('/', lastSlash - 1);
                            if (prevSlash != std::string::npos)
                                parentDir = romFile.substr(prevSlash + 1,
                                        lastSlash - prevSlash - 1);
                        }
                        for (int si = 0; si < (int)mXmbSystems.size(); si++) {
                            if (mXmbSystems[si].romDir == parentDir) {
                                returnSysIdx = si;
                                // Find game in this system's rom list
                                for (int gi = 0; gi < (int)mXmbSystems[si].roms.size(); gi++) {
                                    std::string romBase = mXmbSystems[si].roms[gi];
                                    { size_t ls = romBase.rfind('/');
                                      if (ls != std::string::npos) romBase = romBase.substr(ls + 1); }
                                    if (romBase == romFilename) {
                                        returnGameIdx = gi;
                                        break;
                                    }
                                }
                                break;
                            }
                        }
                        ALOGI("Quick Resume: return sys=%d game=%d (dir=%s file=%s)",
                              returnSysIdx, returnGameIdx,
                              parentDir.c_str(), romFilename.c_str());
                    }

                    // Try native libretro launch from DE cache first.
                    // This runs the game directly in NanoMenu's EGL context,
                    // bypassing the Android framework entirely (~T+3.5s).
                    std::string cacheDir = "/data/system/nano_cache";
                    std::string cachedCore = cacheDir + "/cores/";
                    std::string cachedRom = cacheDir + "/rom/";
                    std::string romFile, coreFile;

                    // Find cached ROM and core (match core from QR property)
                    {
                        // Extract expected ROM basename from qrRom so we
                        // don't accidentally load a stale ROM left by a
                        // previous session (e.g. drastic nano caching an
                        // NDS ROM into the sibling drastic/rom/ dir while
                        // the retroarch rom/ dir still holds an old file).
                        std::string expectedBase;
                        {
                            size_t sl = qrRom.rfind('/');
                            expectedBase = (sl != std::string::npos)
                                    ? qrRom.substr(sl + 1) : qrRom;
                        }

                        DIR* d = opendir((cacheDir + "/rom").c_str());
                        if (d) {
                            struct dirent* e;
                            while ((e = readdir(d)) != nullptr) {
                                std::string name(e->d_name);
                                if (name == "." || name == "..") continue;
                                // ROM file (not .srm, .state, .sav, .png, .brm)
                                if (name.find(".srm") == std::string::npos &&
                                    name.find(".state") == std::string::npos &&
                                    name.find(".sav") == std::string::npos &&
                                    name.find(".brm") == std::string::npos &&
                                    name.find(".png") == std::string::npos) {
                                    romFile = cacheDir + "/rom/" + name;
                                }
                            }
                            closedir(d);
                        }

                        // Verify cached ROM matches QR prop. If the cache
                        // holds a different ROM (stale from a prior session
                        // or a different emulator), skip the native launch
                        // and let populate refresh the cache first.
                        if (!romFile.empty() && !expectedBase.empty()) {
                            std::string cachedBase = romFile;
                            size_t sl = cachedBase.rfind('/');
                            if (sl != std::string::npos)
                                cachedBase = cachedBase.substr(sl + 1);
                            if (cachedBase != expectedBase) {
                                ALOGW("Quick Resume: cached ROM mismatch "
                                      "(have=%s want=%s), skipping native "
                                      "launch until cache refreshes",
                                      cachedBase.c_str(),
                                      expectedBase.c_str());
                                romFile.clear();
                            }
                        }
                        // Match core from QR property (basename), not blindly first .so
                        std::string qrCoreBase = qrCore;
                        size_t lastSlash = qrCoreBase.rfind('/');
                        if (lastSlash != std::string::npos)
                            qrCoreBase = qrCoreBase.substr(lastSlash + 1);
                        std::string candidateCore = cacheDir + "/cores/" + qrCoreBase;
                        struct stat cst;
                        if (!qrCoreBase.empty() && stat(candidateCore.c_str(), &cst) == 0) {
                            coreFile = candidateCore;
                        } else {
                            // Fallback: first .so in cache
                            d = opendir((cacheDir + "/cores").c_str());
                            if (d) {
                                struct dirent* e;
                                while ((e = readdir(d)) != nullptr) {
                                    std::string name(e->d_name);
                                    if (name.find(".so") != std::string::npos) {
                                        coreFile = cacheDir + "/cores/" + name;
                                        break;
                                    }
                                }
                                closedir(d);
                            }
                        }
                    }

                    bool nativeLaunch = false;
                    bool qrCancelled = false;
                    bool backWasDown = false;
                    int64_t backPressStartMs = 0;
                    const int64_t kBackHoldMs = 3000;
                    if (!romFile.empty() && !coreFile.empty()) {
                        // Find save state and SRAM — check states/saves subdirs
                        // first, then fall back to rom/ dir (depends on RA config)
                        std::string romBase = romFile;
                        size_t slashPos = romBase.rfind('/');
                        if (slashPos != std::string::npos)
                            romBase = romBase.substr(slashPos + 1);
                        size_t dotPos = romBase.rfind('.');
                        if (dotPos != std::string::npos)
                            romBase = romBase.substr(0, dotPos);
                        std::string statePath, sramPath;
                        struct stat st;
                        // State: try states/ then rom/
                        std::string s1 = cacheDir + "/states/" + romBase + ".state.auto";
                        std::string s2 = cacheDir + "/rom/" + romBase + ".state.auto";
                        if (stat(s1.c_str(), &st) == 0) statePath = s1;
                        else if (stat(s2.c_str(), &st) == 0) statePath = s2;
                        // SRAM: try saves/ then rom/
                        std::string r1 = cacheDir + "/saves/" + romBase + ".srm";
                        std::string r2 = cacheDir + "/rom/" + romBase + ".srm";
                        if (stat(r1.c_str(), &st) == 0) sramPath = r1;
                        else if (stat(r2.c_str(), &st) == 0) sramPath = r2;

                        ALOGI("Quick Resume: trying native libretro launch");
                        ALOGI("  core=%s", coreFile.c_str());
                        ALOGI("  rom=%s", romFile.c_str());
                        ALOGI("  state=%s", statePath.c_str());
                        ALOGI("  sram=%s", sramPath.c_str());

                        LibretroRunner runner;
                        if (sDrmGlRotation) {
                            runner.setRotationMatrix(sDrmRotMat);
                        }
                        if (runner.init(coreFile, romFile, statePath, sramPath)) {
                            ALOGI("Quick Resume: native libretro loading screen active!");
                            nativeLaunch = true;

                            // Set properties for RetroArch handoff
                            android::base::SetProperty(
                                    "sys.gammaos.nano.launch_rom", qrRom);
                            android::base::SetProperty(
                                    "sys.gammaos.nano.launch_core", qrCore);
                            property_set("sys.gammaos.nano.cache_ready", "0");
                            property_set("sys.gammaos.nano.cache_op", "populate");

                            // Live loading screen: core runs in real-time with
                            // grayscale→color transition. The game is actually
                            // playing behind the desaturation + gradient overlay.
                            // User can play while "loading." When RetroArch takes
                            // over, the game is already running — seamless handoff.
                            float saturation = 0.15f;  // start slightly colorized
                            float gradient = 1.0f;     // strong gradient (full black at bottom)
                            // Extract ROM display name (strip path + extension)
                            std::string romName = romFile;
                            size_t sl = romName.rfind('/');
                            if (sl != std::string::npos) romName = romName.substr(sl + 1);
                            size_t dot = romName.rfind('.');
                            if (dot != std::string::npos) romName = romName.substr(0, dot);
                            bool bootComplete = false;
                            int64_t bootCompleteTime = 0;
                            float textScale = fminf((float)mWidth / 1080.0f,
                                                    (float)mHeight / 720.0f);
                            if (textScale < 0.5f) textScale = 0.5f;
                            float loadScale = 2.5f * textScale;
                            int hotplugCounter = 0;
                            bool handoffPaused = false;

                            while (!exitPending() && !qrCancelled) {
                                // Check for new/swapped input devices every
                                // ~0.5s (30 frames at 60fps). gammapad
                                // recreates device nodes during boot --
                                // without this, we lose the fd and all
                                // button events stop working.
                                if (++hotplugCounter >= 30) {
                                    hotplugCounter = 0;
                                    checkInputHotplug();
                                }

                                // Poll input -- game is live, user can play.
                                // Long-press BACK (3s) exits to XMB.
                                // All buttons including SELECT are forwarded
                                // to the libretro core.
                                for (int fd : mInputFds) {
                                    struct input_event ev;
                                    while (read(fd, &ev, sizeof(ev)) == sizeof(ev)) {
                                        if (ev.type == EV_KEY) {
                                            bool pressed = (ev.value != 0);
                                            // BACK: short-press toggles
                                            // overlay + handoff pause.
                                            // Long-press (3s) exits to XMB.
                                            if (ev.code == KEY_BACK) {
                                                if (pressed && !backWasDown) {
                                                    backPressStartMs =
                                                            elapsedRealtime();
                                                }
                                                if (!pressed && backWasDown) {
                                                    int64_t held = elapsedRealtime()
                                                            - backPressStartMs;
                                                    if (held < kBackHoldMs) {
                                                        handoffPaused = !handoffPaused;
                                                        ALOGI("Quick Resume: handoff %s",
                                                              handoffPaused ? "paused" : "unpaused");
                                                        if (handoffPaused) {
                                                            saturation = 1.0f;
                                                            gradient = 0.0f;
                                                        }
                                                    }
                                                    backPressStartMs = 0;
                                                }
                                                backWasDown = pressed;
                                                continue;
                                            }
                                            switch (ev.code) {
                                            case BTN_A:      runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_A, pressed); break;
                                            case BTN_B:      runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_B, pressed); break;
                                            case BTN_X:      runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_X, pressed); break;
                                            case BTN_Y:      runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_Y, pressed); break;
                                            case BTN_TL:     runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_L, pressed); break;
                                            case BTN_TR:     runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_R, pressed); break;
                                            case BTN_TL2:    runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_L2, pressed); break;
                                            case BTN_TR2:    runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_R2, pressed); break;
                                            case BTN_START:  runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_START, pressed); break;
                                            case BTN_SELECT: runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_SELECT, pressed); break;
                                            case BTN_THUMBL: runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_L3, pressed); break;
                                            case BTN_THUMBR: runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_R3, pressed); break;
                                            case KEY_UP:     runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_UP, pressed); break;
                                            case KEY_DOWN:   runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_DOWN, pressed); break;
                                            case KEY_LEFT:   runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_LEFT, pressed); break;
                                            case KEY_RIGHT:  runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_RIGHT, pressed); break;
                                            }
                                        } else if (ev.type == EV_ABS) {
                                            if (ev.code == ABS_HAT0X) {
                                                runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_LEFT, ev.value < 0);
                                                runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_RIGHT, ev.value > 0);
                                            } else if (ev.code == ABS_HAT0Y) {
                                                runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_UP, ev.value < 0);
                                                runner.setButton(0, RETRO_DEVICE_ID_JOYPAD_DOWN, ev.value > 0);
                                            } else if (ev.code == ABS_X) {
                                                runner.setAnalog(0, RETRO_DEVICE_INDEX_ANALOG_LEFT,
                                                    RETRO_DEVICE_ID_ANALOG_X, (int16_t)((ev.value - 128) * 256));
                                            } else if (ev.code == ABS_Y) {
                                                runner.setAnalog(0, RETRO_DEVICE_INDEX_ANALOG_LEFT,
                                                    RETRO_DEVICE_ID_ANALOG_Y, (int16_t)((ev.value - 128) * 256));
                                            } else if (ev.code == ABS_RX) {
                                                runner.setAnalog(0, RETRO_DEVICE_INDEX_ANALOG_RIGHT,
                                                    RETRO_DEVICE_ID_ANALOG_X, (int16_t)((ev.value - 128) * 256));
                                            } else if (ev.code == ABS_RY) {
                                                runner.setAnalog(0, RETRO_DEVICE_INDEX_ANALOG_RIGHT,
                                                    RETRO_DEVICE_ID_ANALOG_Y, (int16_t)((ev.value - 128) * 256));
                                            }
                                        }
                                    }
                                    if (qrCancelled) break;
                                }

                                // Long-press BACK (3s) exits to XMB.
                                if (backPressStartMs > 0) {
                                    int64_t held = elapsedRealtime()
                                            - backPressStartMs;
                                    if (held >= kBackHoldMs) {
                                        qrCancelled = true;
                                        ALOGW("Quick Resume: BACK held "
                                              "%lldms, exiting to XMB",
                                              (long long)held);
                                        break;
                                    }
                                }

                                // Check boot progress. Trigger the color transition as soon as
                                // possible using the earliest valid signal:
                                //   1. sys.gammaos.nano.home_launching=1 — UserController has
                                //      called startHomeActivity(nanoUnlocked). Earliest signal.
                                //   2. sys.user.0.ce_available=true — user CE storage mounted.
                                //   3. sys.boot_completed=1 — full boot (fallback).
                                if (!bootComplete) {
                                    char val[PROPERTY_VALUE_MAX] = {};
                                    property_get("sys.gammaos.nano.home_launching", val, "");
                                    bool ready = (strcmp(val, "1") == 0);
                                    if (!ready) {
                                        property_get("sys.user.0.ce_available", val, "");
                                        ready = (strcmp(val, "true") == 0);
                                    }
                                    if (!ready) {
                                        property_get("sys.boot_completed", val, "0");
                                        ready = (strcmp(val, "1") == 0);
                                    }
                                    if (ready) {
                                        bootComplete = true;
                                        bootCompleteTime = elapsedRealtime();
                                        ALOGI("Quick Resume: user ready, "
                                              "transitioning to full color");
                                    } else if (!handoffPaused) {
                                        // Slow creep toward color during boot
                                        saturation = fminf(saturation + 0.0003f, 0.35f);
                                        gradient = fmaxf(gradient - 0.0002f, 0.7f);
                                    }
                                }

                                if (bootComplete && !handoffPaused) {
                                    // Ramp to full color over ~0.8s (ease-out)
                                    int64_t elapsed = elapsedRealtime() - bootCompleteTime;
                                    float t = fminf((float)elapsed / 800.0f, 1.0f);
                                    t = 1.0f - (1.0f - t) * (1.0f - t);
                                    saturation = 0.35f + t * 0.65f;
                                    gradient = 0.7f * (1.0f - t);

                                    if (t >= 1.0f && isQrRomStorageReady()) {
                                        // Fully saturated AND the ROM's backing
                                        // storage is mounted. On cold boot, vold
                                        // defers external SD mounting until after
                                        // keyguard, so /storage/<UUID>/ may not
                                        // exist yet. Without this gate, RetroArch
                                        // gets a ROM path it cannot open and hangs.
                                        // Fire do_launch FIRST so NanoRelaunchMonitor can start
                                        // the home activity in parallel while we save state.
                                        // This also bypasses the slow init property trigger
                                        // chain (service.bootanim.nano_retroarch → do_launch
                                        // via init.rc action, which can queue behind boot_completed
                                        // actions for several seconds).
                                        ALOGI("Quick Resume: handoff to RetroArch");
                                        // Mark handoff as done so respawned
                                        // NanoMenu instances skip QR. Uses
                                        // a sys property so it auto-clears
                                        // on reboot while surviving respawn.
                                        property_set("sys.gammaos.nano.handoff_fired", "1");
                                        // Clear pending_exit defensively.
                                        // If the user pressed BACK during QR
                                        // to pause, some code path still sets
                                        // pending_exit=1 and the relaunch
                                        // monitor would then take the
                                        // "immediate cleanup" branch --
                                        // force-stopping the preloaded
                                        // RetroArch and leaving a black
                                        // screen. Clearing it here makes the
                                        // handoff robust regardless of prior
                                        // pause/unpause state.
                                        property_set("sys.gammaos.nano.pending_exit", "0");
                                        { char buf[32];
                                          snprintf(buf, sizeof(buf), "%d", returnSysIdx);
                                          property_set("sys.gammaos.nano.xmb_return_sys", buf);
                                          snprintf(buf, sizeof(buf), "%d", returnGameIdx);
                                          property_set("sys.gammaos.nano.xmb_return_game", buf);
                                        }
                                        property_set("sys.gammaos.nano.return_recent", "0");
                                        property_set("sys.gammaos.nano.drop_input", "1");
                                        // Direct: trigger the relaunch monitor immediately.
                                        property_set("sys.gammaos.nano.do_launch", "1");
                                        // Also set legacy nano_retroarch for init side-effects
                                        // (service.bootanim.exit, etc) but don't depend on it.
                                        property_set("service.bootanim.nano_retroarch", "1");

                                        // Now save state + SRAM in parallel with RetroArch launch
                                        runner.saveState(cacheDir + "/states/" + romBase + ".state.auto");
                                        runner.saveSRAM(cacheDir + "/saves/" + romBase + ".srm");
                                        runner.shutdown();
                                        mExitRequested = true;
                                        break;
                                    }
                                }

                                // Bind AHB FBO so libretro + overlay render through DRM path
                                drmFrameBegin();

                                // Run core + render with desaturation + gradient.
                                // Pass LOGICAL dims for aspect ratio correction;
                                // the rotation matrix maps logical NDC → panel NDC.
                                runner.runFrame(mWidth, mHeight, saturation, gradient);

                                // Text overlay -- fades naturally as saturation
                                // approaches 1.0 (textAlpha -> 0). Game is
                                // always playable underneath.
                                if (sDrmGlRotation) {
                                    glViewport(0, 0, sAhbTarget.w, sAhbTarget.h);
                                } else {
                                    glViewport(0, 0, mWidth, mHeight);
                                }
                                glEnable(GL_BLEND);
                                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                                const char* msg = "Quick Resuming...";
                                float msgW = measureText(msg, loadScale);
                                float msgX = (mWidth - msgW) / 2.0f;
                                float msgY = mHeight * 0.75f;
                                float pulse = 0.7f + 0.3f * sinf(
                                        (float)elapsedRealtime() * 0.004f);
                                float textAlpha = pulse * fmaxf(1.2f - saturation, 0.0f);
                                if (textAlpha > 0.05f) {
                                    if (textAlpha > 1.0f) textAlpha = 1.0f;
                                    drawText(msg, msgX, msgY, loadScale,
                                             1.0f, 1.0f, 1.0f, textAlpha);
                                    float nameScale = 1.5f * textScale;
                                    float nameW = measureText(romName.c_str(), nameScale);
                                    float nameX = (mWidth - nameW) / 2.0f;
                                    float nameY = msgY + FONT_CHAR_H * loadScale + 12.0f * textScale;
                                    drawText(romName.c_str(), nameX, nameY, nameScale,
                                             0.7f, 0.7f, 0.8f, textAlpha * 0.8f);
                                }
                                glDisable(GL_BLEND);

                                drmFrameEnd(mDisplay, mSurface);
                                // Vsync gate: match drastic QR pacing.
                                // DRM path uses vblank wait or page-flip
                                // drain; EGL path falls back to 16ms sleep.
                                if (sDrmActive) {
                                    if (sVsyncEnabled < 0) {
                                        char vp[PROPERTY_VALUE_MAX] = {};
                                        property_get("persist.gammaos.nano.vsync", vp, "1");
                                        sVsyncEnabled = (vp[0] == '0') ? 0 : 1;
                                    }
                                    if (sVsyncEnabled) {
                                        if (sDrmFd >= 0 && !sDrmDisplays.empty() &&
                                            !sDrmVblankBroken && sDrmDisplays.size() <= 1) {
                                            union drm_wait_vblank vbl = {};
                                            vbl.request.type = (enum drm_vblank_seq_type)(
                                                    _DRM_VBLANK_RELATIVE
                                                    | ((sDrmPrimaryIdx & 0x1f)
                                                       << _DRM_VBLANK_HIGH_CRTC_SHIFT));
                                            vbl.request.sequence = 1;
                                            int64_t vblT0 = systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL;
                                            ioctl(sDrmFd, DRM_IOCTL_WAIT_VBLANK, &vbl);
                                            int64_t vblElapsed =
                                                    systemTime(SYSTEM_TIME_MONOTONIC) / 1000LL - vblT0;
                                            if (vblElapsed > 100000) {
                                                sDrmVblankBroken = true;
                                            }
                                        }
                                        drmDrainPageFlipEvents();
                                    } else {
                                        drmPaceWithoutVsync();
                                    }
                                } else {
                                    usleep(16666);
                                }
                            }

                            if (qrCancelled) {
                                ALOGI("Quick Resume: cancelled by BACK hold, "
                                      "returning to menu");
                                runner.shutdown();
                                property_set("persist.gammaos.nano.qr_prepared", "0");
                                // Don't set mExitRequested — fall through to NanoMenu
                            }
                        } else {
                            ALOGW("Quick Resume: native libretro init failed, "
                                  "falling back to RetroArch APK");
                        }
                    }

                    // Fallback: normal RetroArch APK launch (only if native
                    // didn't launch and user didn't cancel)
                    if (!nativeLaunch && !qrCancelled) {
                        android::base::SetProperty(
                                "sys.gammaos.nano.launch_rom", qrRom);
                        android::base::SetProperty(
                                "sys.gammaos.nano.launch_core", qrCore);
                        property_set("sys.gammaos.nano.cache_ready", "0");
                        property_set("sys.gammaos.nano.cache_op", "populate");
                        // Set return navigation to matching system/game
                        { char buf[32];
                          snprintf(buf, sizeof(buf), "%d", returnSysIdx);
                          property_set("sys.gammaos.nano.xmb_return_sys", buf);
                          snprintf(buf, sizeof(buf), "%d", returnGameIdx);
                          property_set("sys.gammaos.nano.xmb_return_game", buf);
                        }
                        property_set("sys.gammaos.nano.return_recent", "0");
                        property_set("service.bootanim.nano_retroarch", "1");
                        property_set("sys.gammaos.nano.drop_input", "1");
                        mExitRequested = true;
                    }
            } else {
                // ROM or core path empty/invalid — stale data
                property_set("persist.gammaos.nano.qr_prepared", "0");
            }
        }
    }

    int exitCheckCounter = 0;
    bool stockClocksApplied = false;
    int64_t bootCompletedDetectedMs = 0;
    while (!exitPending() && !mExitRequested) {
        // GammaOS: Drastic Nano cache-wait + restart. When
        // launchXmbGame() sets mDrasticNanoPending, show
        // "Preparing..." while polling cache_ready. Once the
        // drastic cache is populated, set force_drm=1 and restart
        // NanoMenu into the QR fast path with DRM-direct rendering.
        // DrasticRunner dlopen is one-shot per process lifetime, so
        // we must restart rather than re-init in the same process.
        if (mDrasticNanoPending) {
            // GammaOS: hand off to the drastic-nano binary instead of
            // re-entering the in-process DrasticRunner preview path.
            // drastic-nano loads libdrastic from the installed APK's
            // nativeLibraryDir, so the real initialize_audio runs and
            // the OpenSL ES engine comes up with actual sound. The
            // init trigger sys.gammaos.drastic_nano.start=1 stops
            // SurfaceFlinger, starts the drastic-nano service, and
            // (when drastic-nano later exits) brings nano back up via
            // session_done=1.
            ALOGW("drastic nano: starting drastic-nano binary");
            // Clear QR primed state so when gammaos-nano restarts
            // after drastic-nano exits, it comes up in plain XMB mode
            // rather than re-entering the QR preview for a ROM that
            // is now a stale reference.
            property_set("persist.gammaos.nano.qr_prepared", "0");
            // setDrasticNanoRomPath() already wrote the ROM file; we
            // just need to pull the trigger.
            property_set("sys.gammaos.drastic_nano.start", "1");
            _exit(0);
        }

        // Apply stock clocks once boot is fully complete, with a 1 s
        // margin for PerformanceTile to re-sync performance_mode.
        //
        // Was an inline `usleep(1000000) + system("setclock_stock.sh")`.
        // The usleep parked the render thread for a full second right at
        // boot_completed, and the subsequent system() fork+exec stalled
        // it for another ~50-200 ms. Both showed up in the XMB FPS log
        // as a massive drop to single-digit fps for a 1-2 s window.
        //
        // Replaced with a deferred, non-blocking pattern: record the
        // time when boot_completed was first observed, then when 1 s has
        // passed issue the script run in the background so the fork+exec
        // does not block the render thread.
        if (!stockClocksApplied) {
            if (bootCompletedDetectedMs == 0) {
                char bootDone[PROPERTY_VALUE_MAX] = {};
                property_get("sys.boot_completed", bootDone, "0");
                if (!strcmp(bootDone, "1")) {
                    bootCompletedDetectedMs = elapsedRealtime();
                }
            } else if (elapsedRealtime() - bootCompletedDetectedMs
                       >= 1000) {
                system("/vendor/bin/setclock_stock.sh &");
                stockClocksApplied = true;
                ALOGD("NanoMenu: spawned setclock_stock.sh "
                      "(background) after boot_completed + 1 s");
            }
        }
        pollInput();
        checkInputHotplug();

        // Adaptive framerate:
        //   60fps for DRM, XMB, or procedural effects (vsync-locked, no usleep)
        //   20fps for particle effects
        //   ~10fps idle
        bool xmbActive = (mCurrentEffect == 21);
        bool proceduralFx = (mCurrentEffect >= 11 && mCurrentEffect <= 20);
        bool xmbAnimating = mXmbMode && (fabsf(mXmbAnimX - mXmbSystemIndex) > 0.01f
                                         || fabsf(mXmbAnimY - (mSearchActive
                                             ? (float)mSearchSelectedIndex
                                             : (float)mXmbGameIndex)) > 0.01f);
        bool animating = (mCurrentEffect != 0) || mShowBrightnessBar
                         || mWaitForRelease
                         || ((mMenuState == MENU_RECENT || mMenuState == MENU_APPS)
                             && mScrollOffset > 0.0f);
        int frameTimeUs;
        if (sDrmActive || xmbActive || mXmbMode || proceduralFx) {
            frameTimeUs = 16666; // 60fps — vsync-locked, no usleep
        } else if (animating) {
            frameTimeUs = 50000; // 20fps for particles
        } else {
            frameTimeUs = 100000; // 10fps idle
        }
        // Measure real frame delta for animations
        {
            struct timespec ts;
            clock_gettime(CLOCK_MONOTONIC, &ts);
            int64_t nowNs = (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
            if (mLastFrameNs > 0) {
                float dt = (float)(nowNs - mLastFrameNs) / 1e9f;
                // Clamp to avoid huge jumps on stalls (e.g. first frame, suspend)
                if (dt < 0.001f) dt = 0.001f;
                if (dt > 0.1f) dt = 0.1f;
                mFrameDt = dt;
            }
            mLastFrameNs = nowNs;
        }
        // GammaOS: Drive mEffectTime from CLOCK_BOOTTIME so the XMB hue
        // phase and all wallpaper animations advance continuously across
        // nano process restarts (exiting an SF/HWC app, force_drm
        // relaunch, etc.). Previously we incremented per-frame and relied
        // on persist.gammaos.nano.xmb_color_phase to survive restarts,
        // but that property is only written on XMB game launch — nano can
        // restart via several other paths in which case the restored
        // phase was stale and the hue appeared to snap backward to
        // wherever the last saved launch happened. CLOCK_BOOTTIME is
        // monotonic across suspend and across restarts within the same
        // boot session, so a derived phase is seamless. Wrapping at 500s
        // keeps mediump float precision in shaders and aligns with the
        // XMB hue cycle (rate 0.002 → period 500s).
        {
            struct timespec bt;
            clock_gettime(CLOCK_BOOTTIME, &bt);
            double tb = (double)bt.tv_sec + (double)bt.tv_nsec * 1e-9;
            mEffectTime = (float)fmod(tb, 500.0);
        }
        render();

        // GammaOS: XMB FPS counter. Logs once per second when in XMB mode so
        // we can verify the menu is actually hitting the 60fps target post-
        // optimization. Zero overhead when disabled. Uses monotonic clock
        // already measured for mFrameDt.
        {
            static int64_t sFpsWindowStartNs = 0;
            static int sFpsFrames = 0;
            static int64_t sFpsMinFrameUs = 0;
            static int64_t sFpsMaxFrameUs = 0;
            if (mXmbMode) {
                sFpsFrames++;
                int64_t frameUs = (int64_t)(mFrameDt * 1e6f);
                if (sFpsFrames == 1 || frameUs < sFpsMinFrameUs) sFpsMinFrameUs = frameUs;
                if (frameUs > sFpsMaxFrameUs) sFpsMaxFrameUs = frameUs;
                if (sFpsWindowStartNs == 0) sFpsWindowStartNs = mLastFrameNs;
                int64_t elapsedNs = mLastFrameNs - sFpsWindowStartNs;
                if (elapsedNs >= 1000000000LL) {
                    float fps = (float)sFpsFrames * 1e9f / (float)elapsedNs;
                    ALOGW("NanoMenu XMB FPS: %.1f (%d frames / %lld.%03lld s, min=%lldus max=%lldus)",
                          fps, sFpsFrames, (long long)(elapsedNs / 1000000000LL),
                          (long long)((elapsedNs / 1000000LL) % 1000),
                          (long long)sFpsMinFrameUs, (long long)sFpsMaxFrameUs);
                    sFpsWindowStartNs = mLastFrameNs;
                    sFpsFrames = 0;
                    sFpsMinFrameUs = 0;
                    sFpsMaxFrameUs = 0;
                }
            } else {
                sFpsWindowStartNs = 0;
                sFpsFrames = 0;
            }
        }

        // Frame pacing: at 60fps, eglSwapBuffers vsync-blocks — no sleep needed.
        // For lower rates, sleep the remaining time to hit the target frame period.
        // Clock-based: measure actual elapsed time so variable swap durations
        // don't cause frame-to-frame jitter.
        if (frameTimeUs > 16666) {
            struct timespec tsNow;
            clock_gettime(CLOCK_MONOTONIC, &tsNow);
            int64_t nowUs = (int64_t)tsNow.tv_sec * 1000000LL + tsNow.tv_nsec / 1000LL;
            int64_t frameStartUs = mLastFrameNs / 1000LL;
            int64_t elapsedUs = nowUs - frameStartUs;
            int64_t remainUs = (int64_t)frameTimeUs - elapsedUs;
            if (remainUs > 1000) usleep((useconds_t)remainUs);
        }

        // Check every ~0.5s if an external trigger requested exit
        int exitCheckInterval = animating ? 30 : 5; // 30*16ms or 5*100ms
        if (++exitCheckCounter >= exitCheckInterval) {
            exitCheckCounter = 0;
            char val[PROPERTY_VALUE_MAX] = {};
            property_get("service.bootanim.exit", val, "0");
            if (!strcmp(val, "1") && !mWaitForRelease) {
                ALOGI("GammaOS Nano: service.bootanim.exit=1, exiting");
                break;
            }
            // Probe storage readiness (CE unlock) until it becomes available.
            // This is the only place that polls — render() used to probe every
            // frame but that was wasteful once the 0.5s cadence here exists.
            // When storage comes up, auto-load the active submenu's content
            // so the user sees the entries as soon as they are available.
            if (!mStorageReady) {
                if (access("/data/media/0", R_OK) == 0) {
                    mStorageReady = true;
                    mDisplayDirty = true;
                    if (mMenuState == MENU_RECENT) loadRecentPlaylist();
                    else if (mMenuState == MENU_APPS) loadInstalledApps();
                    ALOGI("GammaOS Nano: storage is now accessible");
                }
            }

            // GammaOS: Late-display re-probe. Any DRM CRTC that wasn't ready at
            // splash time gets a second chance here. Bounded to a 5-second boot
            // window by drmRescanDisplays itself. No-op post-boot (sDrmFd = -1).
            drmRescanDisplays();
            // GammaOS Nano: Keep the surface's layer stack in sync with the
            // chosen display. SurfaceFlinger's initial layerStack for the display
            // can change once DisplayManagerService finishes assigning logical
            // display IDs (e.g. port 1 starts at layerStack=1 but becomes 2). We
            // re-query and re-apply here so the NanoMenu surface follows the
            // chosen physical display even after DMS reassigns.
            if (mDisplayToken != nullptr && mFlingerSurfaceControl != nullptr) {
                ui::DisplayState cur;
                if (SurfaceComposerClient::getDisplayState(mDisplayToken, &cur)
                        == NO_ERROR
                        && cur.layerStack.id != mAppliedLayerStack) {
                    SurfaceComposerClient::Transaction lt;
                    lt.setLayerStack(mFlingerSurfaceControl, cur.layerStack);
                    lt.apply();
                    ALOGI("NanoMenu: layerStack changed %u → %u, reapplied",
                          mAppliedLayerStack, cur.layerStack.id);
                    mAppliedLayerStack = cur.layerStack.id;
                }
            }
            // GammaOS: Defensive recovery for the secondary (wallpaper-only)
            // displays. After exiting a DualStack app, DualStackController
            // tears down its forced tall size via clearForcedDisplaySize,
            // which triggers a display reconfiguration on DEFAULT_DISPLAY.
            // The reconfiguration applies the orientation policy of whatever
            // is still considered the "top resumed activity" — usually the
            // dying portrait emulator — and rotates the secondary display
            // to ROTATION_270 a few seconds after we returned to nano. The
            // wallpaper then renders sideways (480x640 instead of 640x480).
            // We can't suppress that race from this side, so just poll the
            // secondary display states each ~0.5s and re-apply ROTATION_0
            // whenever the rotation drifts. Cheap: bounded by the number of
            // secondary displays (typically one).
            for (size_t i = 0; i < mSecondaryDisplayTokens.size(); i++) {
                const sp<IBinder>& token = mSecondaryDisplayTokens[i];
                if (token == nullptr) continue;
                ui::DisplayState state;
                if (SurfaceComposerClient::getDisplayState(token, &state) != NO_ERROR) {
                    continue;
                }
                // GammaOS: Re-assert PowerMode::ON on the secondary display.
                // Cheap idempotent call; cheap insurance against SF putting
                // the display to sleep mid-session (observed when returning
                // from RetroArch, dreamManager, or any policy that touches
                // non-default display power state). Without this, the
                // secondary display renders into a black HWC output even
                // though our EGL surface is swapping correctly.
                SurfaceComposerClient::setDisplayPowerMode(token, 2);
                // GammaOS: When returning to nano from an SF/HWC-based app
                // (e.g. RetroArch), DualStackController's teardown may
                // re-assign the secondary display's layer stack after our
                // setupSecondaryEglSurfaces() already read and applied the
                // stack on our SurfaceControl. The secondary display then
                // scans out a stack that has no layer, and the bottom
                // screen goes blank. Detect the drift and re-route our
                // wallpaper SurfaceControl to whatever stack the secondary
                // display is currently on. Same shape as the primary's
                // self-healing layer-stack reapply a few dozen lines up.
                if (i < mSecondaryAppliedLayerStacks.size()
                        && i < mSecondaryWallpaperControls.size()
                        && mSecondaryWallpaperControls[i] != nullptr
                        && state.layerStack.id != mSecondaryAppliedLayerStacks[i]) {
                    SurfaceComposerClient::Transaction lt;
                    lt.setLayerStack(mSecondaryWallpaperControls[i],
                                     state.layerStack);
                    lt.apply();
                    ALOGI("NanoMenu: secondary %zu layerStack %u → %u, reapplied",
                          i, mSecondaryAppliedLayerStacks[i],
                          state.layerStack.id);
                    mSecondaryAppliedLayerStacks[i] = state.layerStack.id;
                }
                if (state.orientation == ui::ROTATION_0) continue;
                DisplayMode mode;
                if (SurfaceComposerClient::getActiveDisplayMode(token, &mode) != NO_ERROR) {
                    continue;
                }
                ui::Size res = mode.resolution;
                Rect bounds(0, 0, res.width, res.height);
                SurfaceComposerClient::Transaction t;
                t.setDisplayProjection(token, ui::ROTATION_0, bounds, bounds);
                t.apply();
                ALOGI("NanoMenu: secondary display %zu rotated to %d, "
                      "reset to ROTATION_0 (%dx%d)",
                      i, static_cast<int>(state.orientation),
                      res.width, res.height);
            }
            // Background ROM scanning — all I/O runs on a separate thread.
            // The render thread only does a quick lock-free check + swap.
            if (mStorageReady) {
                // Check boot_completed once → trigger initial background scan
                if (!mXmbBootCompleted) {
                    char val[PROPERTY_VALUE_MAX] = {};
                    property_get("sys.boot_completed", val, "0");
                    if (!strcmp(val, "1")) {
                        mXmbBootCompleted = true;
                        forceRescanAllSystems();
                    }
                }

                // Pick up results from background scan thread (lock-free check)
                if (mBgScanResultReady) {
                    std::lock_guard<std::mutex> lock(mBgScanMutex);
                    if (mBgScanResultReady) {
                        for (int i = 0; i < (int)mXmbSystems.size()
                                 && i < (int)mBgScanResults.size(); i++) {
                            auto& sys = mXmbSystems[i];
                            auto& res = mBgScanResults[i];
                            // Guard: don't replace with fewer ROMs when storage
                            // is partially mounted. Two checks:
                            // 1. Path count: if fewer source dirs, storage not ready
                            // 2. Time: never remove entries within 60s of boot_completed
                            if (res.roms.size() < sys.roms.size() && sys.scanned) {
                                // Time guard: always block within 60s of boot
                                static int64_t sBootCompletedTime = 0;
                                if (sBootCompletedTime == 0)
                                    sBootCompletedTime = elapsedRealtime();
                                if ((elapsedRealtime() - sBootCompletedTime) < 60000)
                                    continue;
                                // Path guard: block if fewer paths accessible
                                size_t curPaths = sys.activePaths.size();
                                if (curPaths == 0 && !sys.roms.empty()) {
                                    std::set<std::string> dirs;
                                    for (const auto& r : sys.roms) {
                                        size_t sl = r.rfind('/');
                                        if (sl != std::string::npos)
                                            dirs.insert(r.substr(0, sl));
                                    }
                                    curPaths = dirs.size();
                                }
                                if (res.activePaths.size() < curPaths) continue;
                            }
                            if (res.roms != sys.roms || !sys.scanned) {
                                sys.roms = std::move(res.roms);
                                sys.displayNames = std::move(res.displayNames);
                                sys.activePaths = std::move(res.activePaths);
                                sys.activePath = std::move(res.activePath);
                                sys.pathExists = !sys.roms.empty();
                                mDisplayDirty = true;
                                // Update cache file
                                std::string cacheDir = "/data/system/nano_xmb_cache";
                                mkdir(cacheDir.c_str(), 0755);
                                std::string cp = cacheDir + "/" + sys.romDir + ".list";
                                int cfd = open(cp.c_str(), O_WRONLY|O_CREAT|O_TRUNC, 0644);
                                if (cfd >= 0) {
                                    for (const auto& r : sys.roms) {
                                        std::string l = r + "\n";
                                        write(cfd, l.c_str(), l.size());
                                    }
                                    close(cfd);
                                }
                            }
                            sys.scanned = true;
                            sys.lastScanTime = elapsedRealtime();
                        }
                        mBgScanResultReady = false;
                        mXmbRomScanDone = true;
                    }
                }

                // Periodic rescan every 30s (runs on background thread,
                // zero impact on render)
                if (mXmbBootCompleted && !mBgScanThreadRunning
                    && !mXmbSystems.empty()) {
                    int64_t now = elapsedRealtime();
                    if (mXmbSystems[0].lastScanTime > 0 &&
                        (now - mXmbSystems[0].lastScanTime) > 30000) {
                        forceRescanAllSystems();
                    }
                }
            }
        }
    }

    // GammaOS: Clear menu_active flag so DualStack can re-enable when app launches.
    property_set("sys.gammaos.nano.menu_active", "0");

    // GammaOS: Hand displays to SurfaceFlinger now that XMB is done.
    //
    // We kept DRM-direct rendering active for the entire XMB loop to
    // avoid the HWC compositor tick on every frame. Now that the user
    // has committed to launching an app (mExitRequested is set via
    // handleSelect/launchXmbGame/etc.), SurfaceFlinger needs to be the
    // DRM master so it can composite the app's window. drmStop()
    // releases the DRM resources nano was holding, and
    // setupSecondaryEglSurfaces() then reclaims wallpaper ownership on
    // the secondary display(s) so the bootanim logo does not linger
    // there while the app is loading.
    //
    // We only do this when mExitRequested is set -- that way the
    // bootanim.exit "die quietly" path at the start of threadLoop also
    // exits cleanly without disturbing DRM state.
    if (mExitRequested && sDrmActive) {
        drmStop();
        setupSecondaryEglSurfaces();
    }

    // Only re-apply performance clocks when launching an app (not on bootanim.exit)
    // Run in background — setclock_max.sh has a 20s retry loop that must not block exit.
    if (mExitRequested) {
        char mode[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.performance_mode", mode, "stock");
        char cmd[128];
        snprintf(cmd, sizeof(cmd), "/vendor/bin/setclock_%s.sh &", mode);
        ALOGI("NanoMenu: re-applying performance mode '%s' (background)", mode);
        system(cmd);
    }

    // Transition: grab input devices. drop_input was already set in handleSelect()
    // to block InputDispatcher from the moment the user pressed A.
    for (int fd : mInputFds) {
        ioctl(fd, EVIOCGRAB, 1);
    }
    // Do NOT clear drop_input here. The A-DOWN may still be sitting in
    // InputDispatcher's queue waiting for a focused window. InputDispatcher
    // will clear drop_input itself when it processes a FOCUS entry (which
    // arrives after all stale events have been dropped).
    ALOGD("NanoMenu: showing loading screen, waiting for RetroArch");
    // GammaOS: Upload the rotation matrix unconditionally. After drmStop()
    // above, sDrmGlRotation is false and sDrmRotMat is the identity matrix,
    // but the text program's uRotation uniform still holds the previous
    // DRM rotation from the QR/XMB session. Without this upload the
    // loading text renders with the stale rotation while the viewport is
    // logical (mWidth/mHeight) — the net effect is the "Loading..." string
    // appears upside-down or sideways on panels installed at 90/180/270.
    // Gating on sDrmGlRotation (the previous behavior) skipped the reset.
    {
        const GLuint progs[] = {mShaderProgram, mTextProgram};
        const GLint  locs[]  = {mLocRotation, mTextLocRotation};
        for (int i = 0; i < 2; i++) {
            glUseProgram(progs[i]);
            glUniformMatrix2fv(locs[i], 1, GL_FALSE, sDrmRotMat);
        }
    }
    {
        float sf = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
        if (sf < 0.5f) sf = 0.5f;
        float loadScale = 3.0f * sf;
        struct input_event drain_ev;
        char launched[PROPERTY_VALUE_MAX] = {};
        for (int wait = 0; wait < 600; wait++) { // max ~10s
            // Drain all queued input events
            for (int fd : mInputFds) {
                while (read(fd, &drain_ev, sizeof(drain_ev)) == sizeof(drain_ev)) {}
            }
            // Render loading screen — route through DRM if active
            drmFrameBegin();
            if (sDrmGlRotation) {
                glViewport(0, 0, sAhbTarget.w, sAhbTarget.h);
            } else {
                glViewport(0, 0, mWidth, mHeight);
            }
            glClearColor(0.05f, 0.05f, 0.10f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            const char* loadMsg = "Loading...";
            float loadW = measureText(loadMsg, loadScale);
            float loadX = (mWidth - loadW) / 2.0f;
            float loadY = (mHeight - FONT_CHAR_H * loadScale) / 2.0f;
            drawText(loadMsg, loadX, loadY, loadScale,
                     0.6f, 0.6f, 0.7f, 1.0f);
            glDisable(GL_BLEND);
            drmFrameEnd(mDisplay, mSurface);

            property_get("sys.gammaos.nano.app_launched", launched, "0");
            if (!strcmp(launched, "1")) {
                ALOGD("NanoMenu: RetroArch launched, exiting");
                break;
            }
            usleep(16666);
        }
    }

    // GammaOS: tear down secondary wallpaper EGL surfaces / SurfaceControls
    // BEFORE eglTerminate. The destructor (~NanoMenu) was previously doing
    // this cleanup, but by then eglTerminate had already invalidated the
    // EGL display, leaving the underlying BLASTBufferQueue's GraphicBuffers
    // in an inconsistent state. The buffer release path then tried to call
    // freeBuffer through the gralloc mapper after its RegisteredHandlePool
    // mutex was effectively destroyed, aborting with FORTIFY:
    //   pthread_mutex_lock called on a destroyed mutex
    // (backtrace: ~SurfaceControl -> ~BBQSurface -> ~BLASTBufferQueue ->
    //  ~GraphicBuffer -> freeBuffer -> RegisteredHandlePool::remove).
    // Cleaning up here, while the EGL display is still alive, avoids the
    // crash. The destructor's identical cleanup becomes a no-op because the
    // vectors are already empty.
    for (size_t i = 0; i < mSecondaryEglSurfaces.size(); i++) {
        eglDestroySurface(mDisplay, mSecondaryEglSurfaces[i]);
    }
    mSecondaryEglSurfaces.clear();
    mSecondarySurfaces.clear();
    if (!mSecondaryWallpaperControls.empty()) {
        SurfaceComposerClient::Transaction t;
        for (size_t i = 0; i < mSecondaryWallpaperControls.size(); i++) {
            t.reparent(mSecondaryWallpaperControls[i], nullptr);
        }
        t.apply();
        mSecondaryWallpaperControls.clear();
    }

    eglMakeCurrent(mDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(mDisplay, mContext);
    eglDestroySurface(mDisplay, mSurface);
    mFlingerSurface.clear();
    mFlingerSurfaceControl.clear();
    eglTerminate(mDisplay);
    eglReleaseThread();
    IPCThreadState::self()->stopProcess();
    return false;
}
} // namespace android
