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
#include <malloc.h>
#include <linux/input.h>
#include <sys/inotify.h>
#include <signal.h>
#include <strings.h>
#include <drm.h>

#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <cutils/properties.h>
#include <android-base/properties.h>
#include <sys/system_properties.h>
// __system_property_serial is exported by libc (libc.map.txt) but declared only in
// the internal <sys/_system_properties.h>; forward-declare it so the render loop can
// detect a property change via its serial (a cheap pointer-deref) instead of a full
// name lookup every frame. prop_info comes from <sys/system_properties.h> above.
extern "C" uint32_t __system_property_serial(const prop_info* __pi);
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
#include "NanoBacklight.h"
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
#include <android/performance_hint.h>   // ADPF render-thread hint session
#include <android/native_window.h>      // ANATIVEWINDOW_FRAME_RATE_* for setFrameRate
#include <android/hardware/light/2.0/ILight.h>

#include "NanoMenu.h"
#include "NanoMenuShaders.h"
#include "NanoMenuStrings.h"
#include "NanoI18n.h"      // trDyn() runtime translation of hardcoded UI strings
#include "NanoBtStable.h"
#include "NanoMenuPS3Bg.h"   // ps3bg::themeFading() for the adaptive idle frame-rate

extern int gEarlyDrmFd;

namespace android {

using ui::DisplayMode;

// Snapshot hook (sys.gammaos.nano.shot), defined in NanoMenuRender.cpp.
// Declared at file scope so every QR preview/splash loop can call it.
void maybeNanoScreenshot();

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
      mFxLocXFlip(-1),
      mXmbLocYFlip(-1), mXmbLocXFlip(-1),
      mSelectedIndex(0),
      mDisplayDirty(true),
      mInotifyFd(-1),
      mExitRequested(false),
      mWaitForRelease(false),
      mDrasticNanoPending(false),
      mDrmBootPath(false),
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
      mShowLaunchBusy(false), mLaunchBusyTimer(0), mLaunchPending(false),
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
      mQuickResumeEnabled(true),
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
      mOskActive(false),
      mSearchSelectedIndex(0), mSearchActive(false),
      mFtLib(nullptr),
      mFtNumFaces(0),
      mFontSize(48),
      mGlyphAtlasTex(0),
      mAtlasW(0), mAtlasH(0),
      mAtlasCurX(0), mAtlasCurY(0), mAtlasRowH(0),
      mTextProgram(0), mTextLocPosition(-1), mTextLocTexCoord(-1),
      mTextLocColor(-1), mTextLocTexture(-1),
      mSetupWizardActive(false), mSetupStep(SETUP_WELCOME),
      mSetupTransitionAlpha(1.0f), mSetupSlideOffset(0.0f),
      mSetupTransitioning(false), mSetupTransitionTarget(SETUP_WELCOME),
      mSetupBootWaited(false),
      mLangSelected(0), mLangScrollTop(0),
      mGreetingIndex(0), mGreetingTimer(0.0f),
      mGreetingFade(0.0f), mGreetingFadingOut(false),
      mGreetingTransType(0),
      mTzSelected(0), mTzScrollTop(0),
      mSetupLogScrollTop(0), mSetupScriptRunning(false),
      mSetupScriptDone(false), mSetupLogExitRequested(false) {
    // mSession creation deferred to readyToRun() -- the SurfaceComposerClient
    // constructor calls waitForService("SurfaceFlingerAIDL") which blocks
    // until SF is up.  On the DRM boot path we don't need SF at all.
    srand(elapsedRealtime());
    memset(mParticles, 0, sizeof(mParticles));
    memset(mFtFaces, 0, sizeof(mFtFaces));
    // Seed the idle-fps input stamp with "now": uptimeMillis() is system-wide,
    // and nano respawns constantly (the DRM home exits on every game launch),
    // so a fresh process must not inherit the whole boot uptime as "idle" and
    // settle straight into the 30fps idle rate right after a game exit.
    mLastInputMs = uptimeMillis();
    // Restore persisted wallpaper effect, default to XMB (21)
    char wallpaper[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.wallpaper", wallpaper, "22");
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
            "persist.gammaos.nano.quick_resume", true);   // default ON (user decision 2026-07-01)
}

void NanoMenu::initSurfaceFlingerPath() {
    if (!mDrmBootPath) return;

    {
        sp<IServiceManager> sm = defaultServiceManager();
        const String16 name("SurfaceFlinger");
        while (sm->checkService(name) == nullptr) {
            usleep(10000);
        }
    }

    mSession = new SurfaceComposerClient();
    mSession->linkToComposerDeath(this);

    const std::vector<PhysicalDisplayId> ids = SurfaceComposerClient::getPhysicalDisplayIds();
    if (ids.empty()) {
        ALOGE("initSurfaceFlingerPath: no displays");
        return;
    }

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
    }

    mDisplayToken = SurfaceComposerClient::getPhysicalDisplayToken(chosenId);
    if (mDisplayToken == nullptr) {
        ALOGE("initSurfaceFlingerPath: no display token");
        return;
    }

    ui::DisplayState chosenDisplayState;
    ui::LayerStack chosenLayerStack = ui::DEFAULT_LAYER_STACK;
    if (SurfaceComposerClient::getDisplayState(mDisplayToken, &chosenDisplayState) == NO_ERROR) {
        chosenLayerStack = chosenDisplayState.layerStack;
    }
    mAppliedLayerStack = chosenLayerStack.id;

    DisplayMode displayMode;
    if (SurfaceComposerClient::getActiveDisplayMode(mDisplayToken, &displayMode) != NO_ERROR) {
        ALOGE("initSurfaceFlingerPath: getActiveDisplayMode failed");
        return;
    }

    ui::Size resolution = displayMode.resolution;
    sp<SurfaceControl> control = session()->createSurface(
        String8("GammaOSNano"), resolution.getWidth(), resolution.getHeight(),
        PIXEL_FORMAT_RGBX_8888, ISurfaceComposerClient::eOpaque);

    SurfaceComposerClient::Transaction t;
    Rect forcedRes(0, 0, resolution.width, resolution.height);
    Rect physRes(0, 0, displayMode.resolution.width, displayMode.resolution.height);
    t.setDisplayProjection(mDisplayToken, ui::ROTATION_0, forcedRes, physRes);
    t.setLayer(control, 0x40000001);
    t.setLayerStack(control, chosenLayerStack);
    t.apply();

    sp<Surface> s = control->getSurface();
    // Overlay: extra buffer depth - see the twin call in readyToRun's SF block
    // for the full rationale (phase-bistable dequeue pacing under the SF client
    // composite).
    if (mOverlayMode) s->setMaxDequeuedBufferCount(3);
    EGLConfig config = getEglConfig(mDisplay);
    EGLSurface sfSurface = eglCreateWindowSurface(mDisplay, config, s.get(), nullptr);
    eglMakeCurrent(mDisplay, sfSurface, sfSurface, mContext);
    // Overlay: don't vsync-block in eglSwapBuffers. SurfaceFlinger latches on
    // its own vsync regardless (no tearing); the clock-based top-up sleep in
    // threadLoop paces submission. With interval 1 a frame that took a hair
    // over 16.7ms stalled to the NEXT vsync (33ms) - the scroll judder.
    // overlayShow switches to interval 1 in wallpaper mode.
    if (mOverlayMode) eglSwapInterval(mDisplay, 0);
    eglDestroySurface(mDisplay, mSurface);
    mSurface = sfSurface;
    mFlingerSurfaceControl = control;
    mFlingerSurface = s;

    EGLint w, h;
    eglQuerySurface(mDisplay, mSurface, EGL_WIDTH, &w);
    eglQuerySurface(mDisplay, mSurface, EGL_HEIGHT, &h);
    mWidth = w; mHeight = h;

    mDrmBootPath = false;
    ALOGD("NanoMenu: deferred SF init done, display %dx%d", mWidth, mHeight);
}

NanoMenu::~NanoMenu() {
    // Shutdown: the render loop has stopped bumping the watchdog heartbeat, and this
    // destructor makes several blocking teardown joins (net/photo/scraper workers and, the
    // slow one, videoHardFree -> NanoVideo::finishRelease, which joins the async-release
    // thread while the Allwinner HW codec stops/frees - that can take well over the ~8s
    // watchdog window). Without an exemption the watchdog aborts mid-destruction and the
    // process exits via SIGABRT (seen as ~NanoMenu -> videoHardFree -> finishRelease ->
    // pthread_join tombstones on app-launch handoff while a video was active). Exempt the
    // render watchdog for the whole destruction - the same scoped exemption used for the
    // sleep/occlusion teardown join. The teardown is designed to complete (release stops the
    // codec first so the wedged worker unblocks and the join finishes), so this only turns a
    // spurious abort into a clean, if slightly slow, exit; it is never reset (we are exiting).
    mVidTeardownExempt.store(true, std::memory_order_relaxed);
    // Stop the PSP live-app capture worker (detached; join-free). Signal + bounded
    // wait so it is out of its binder call before the rest of teardown / stopProcess.
    // Bounded because the listener wait is bounded; can never deadlock.
    pspClockStopCaptureWorker(300);
    // Stop the GammaEQ audio preview stream if it is still playing.
    stopEqPreview();
    // Stop the setup log tailer if running.
    stopSetupLogThread();
    // Stop the network HUD poller first so its worker thread can't
    // race with teardown of other state.
    stopNetPollThread();
    // Stop the photo viewer async decode worker (join the thread).
    pvStopDecodeWorker();
    // Stop the scraper-art async decode worker (join the thread; no GL in dtor).
    saStopArtWorker();
    // Tear down the video WALLPAPER first (joins its open worker; a joinable std::thread member would
    // otherwise std::terminate at destruction). It async-frees into mVidDying, which the synchronous
    // videoHardFree(true) below then drains. Watchdog already exempt above.
    if (mWpVideoTop || mWpVideoThread.joinable()) wpVideoStop();
    // Tear down the video decoder (joins its worker, frees codec/extractor/surface/texture).
    videoHardFree(true);   // dtor: synchronous (no render loop left to reap an async teardown)

    // GammaOS: Clean up secondary display wallpaper resources.
    for (size_t i = 0; i < mSecondaryEglSurfaces.size(); i++) {
        eglDestroySurface(mDisplay, mSecondaryEglSurfaces[i]);
    }
    mSecondaryEglSurfaces.clear();
    mSecondarySurfaces.clear();
    if (!mSecondaryWallpaperControls.empty() && mSession != nullptr) {
        SurfaceComposerClient::Transaction t;
        for (size_t i = 0; i < mSecondaryWallpaperControls.size(); i++) {
            t.reparent(mSecondaryWallpaperControls[i], nullptr);
        }
        t.apply();
        mSecondaryWallpaperControls.clear();
    }
    mSecondaryDisplayTokens.clear();
    mSecondaryAppliedLayerStacks.clear();
    mSecondaryCreatedSize.clear();
    mSecondaryAppliedLssH.clear();
    for (int fd : mInputFds) {
        ioctl(fd, EVIOCGRAB, 0); // release grab (always, in case exit-grab was applied)
        close(fd);
    }
    if (mInotifyFd >= 0) close(mInotifyFd);
}

void NanoMenu::onFirstRef() {
    if (mSession != nullptr) {
        status_t err = mSession->linkToComposerDeath(this);
        SLOGE_IF(err, "linkToComposerDeath failed (%s)", strerror(-err));
    }
}

sp<SurfaceComposerClient> NanoMenu::session() const { return mSession; }

void NanoMenu::binderDied(const wp<IBinder>&) {
    ALOGD("SurfaceFlinger died, exiting...");
    kill(getpid(), SIGKILL);
    requestExit();
}
// GammaOS Nano: true when nano must run its home through SurfaceFlinger rather than
// grabbing DRM master directly. On Unisoc/Spreadtrum SoCs (ums*/sc9*/sharkl*) the
// vendor HWComposer relies on IMPLICIT DRM master and never calls drmSetMaster, so it
// cannot reclaim master after a DRM-direct nano home drops it at the app handoff (the
// panel then freezes on nano's last frame). nano must never take master there.
// persist.gammaos.nano.force_sf=1 forces it on any SoC. Keyed on ro.board.platform so
// it is available even before persist props load (main() uses the same rule).
static bool nanoForceSfPath() {
    if (property_get_bool("persist.gammaos.nano.force_sf", false)) return true;
    char platform[PROPERTY_VALUE_MAX] = {};
    property_get("ro.board.platform", platform, "");
    return strstr(platform, "ums") != nullptr ||
           strstr(platform, "sc98") != nullptr ||
           strstr(platform, "sc99") != nullptr ||
           strstr(platform, "sharkl") != nullptr;
}

status_t NanoMenu::readyToRun() {
    int64_t t0 = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
    auto tlog = [&](const char*) {
        t0 = systemTime(SYSTEM_TIME_MONOTONIC) / 1000000LL;
    };

    // Release any stale "nano_music" kernel wakelock left behind by a previous nano
    // instance that died (LMK / watchdog / crash) while holding it for screen-off
    // playback. Named /sys/power/wake_lock entries persist past process death, and a
    // fresh process's in-memory "held" flags reset to false, so without this a leaked
    // lock would block suspend forever. Safe here: at startup this process has played
    // nothing (the music library is lazy-loaded), and the cold-boot home + resident
    // overlay both reach this before any track can play, so it never races real
    // playback. The lock is re-acquired only while a track is actually playing
    // screen-off (enterDrmSleep / the overlay screen-off path) and released on
    // pause / stop / queue-end / wake.
    {
        int wl = open("/sys/power/wake_unlock", O_WRONLY | O_CLOEXEC);
        if (wl >= 0) { ssize_t n = write(wl, "nano_music", 10); (void)n; close(wl); }
    }

    // On non-Qualcomm SoCs, main() already grabbed DRM master early.
    // On Qualcomm, gEarlyDrmFd is -1 (grab deferred to after skip_nano).
    int earlyDrmFd = gEarlyDrmFd;
    gEarlyDrmFd = -1;
    if (earlyDrmFd >= 0) {
        ALOGI("GammaOS Nano: using early DRM master fd=%d from main()", earlyDrmFd);
    }

    // GammaOS Nano: decide whether to run NanoMenu or bail to stock
    // bootanim. The durable source of truth is
    // persist.bootanim.skip_nano (loaded from /data/property/
    // persistent_properties by init's load_persist_props), but that
    // load completes after /data is decrypted and after this readyToRun
    // call. To avoid a long wait on ro.persistent_properties.ready
    // every boot, we cache the same value in /data/misc/bootanim/
    // nano_skip whenever it changes through the on-device action
    // dispatchers in init.rc. /data/misc/bootanim is in DE storage so
    // it is readable as soon as /data is mounted, no FBE unlock
    // required.
    //
    // Fast path: read the file. "0" = nano, "1" = bootanim. Anything
    // else (file missing on first boot, malformed content) drops to
    // the property fallback with a wait, mirroring the original
    // behavior.
    bool decided = false;
    bool fileBootanim = false;
    {
        int fd = open("/data/misc/bootanim/nano_skip",
                      O_RDONLY | O_CLOEXEC);
        if (fd >= 0) {
            char buf[8] = {};
            ssize_t n = read(fd, buf, sizeof(buf) - 1);
            close(fd);
            if (n > 0) {
                // Strip trailing newline/whitespace from init's
                // `write` directive output.
                for (ssize_t i = 0; i < n; i++) {
                    if (buf[i] == '\n' || buf[i] == '\r' ||
                        buf[i] == ' ') { buf[i] = 0; break; }
                }
                if (strcmp(buf, "0") == 0) {
                    ALOGI("GammaOS Nano: DE state file = '0', "
                          "running nano");
                    decided = true;
                } else if (strcmp(buf, "1") == 0) {
                    ALOGI("GammaOS Nano: DE state file = '1', "
                          "starting bootanim");
                    decided = true;
                    fileBootanim = true;
                } else {
                    ALOGW("GammaOS Nano: DE state file has "
                          "unexpected content '%s', falling back to "
                          "persist property", buf);
                }
            }
        }
    }

    if (!decided) {
        // Slow path: wait up to 10 s for persist props, then read.
        char ready[PROPERTY_VALUE_MAX] = {};
        property_get("ro.persistent_properties.ready", ready, "");
        if (strcmp(ready, "true") != 0) {
            ALOGI("GammaOS Nano: persist props not ready, waiting...");
            for (int i = 0; i < 1000; i++) {
                usleep(10000);
                property_get("ro.persistent_properties.ready", ready, "");
                if (strcmp(ready, "true") == 0) break;
            }
        }
        char skip[PROPERTY_VALUE_MAX] = {};
        property_get("persist.bootanim.skip_nano", skip, "");
        if (strcmp(skip, "0") == 0) {
            ALOGI("GammaOS Nano: skip_nano='0', running nano");
        } else {
            ALOGI("GammaOS Nano: skip_nano='%s' (not '0'), "
                  "starting bootanim", skip);
            fileBootanim = true;
        }
    }

    if (fileBootanim) {
        if (earlyDrmFd >= 0) {
            ioctl(earlyDrmFd, DRM_IOCTL_DROP_MASTER, 0);
            close(earlyDrmFd);
        }
        // The overlay process is NEVER the boot-animation owner. In normal Android
        // (skip_nano != '0') persist.gammaos.nano.overlay stays 1 so the rc trigger
        // can still start the overlay service; if it reaches here, starting bootanim
        // is exactly what made the boot animation reappear at random. Just exit.
        if (!mOverlayMode) {
            property_set("ctl.start", "bootanim");
        } else {
            ALOGI("GammaOS Nano overlay: not nano mode (skip_nano!='0'), exiting without bootanim");
        }
        _exit(0);
    }
    tlog("skip_nano check passed");

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
    //
    // Persist props can race with our early start. gammaos-nano begins
    // around T+7 s on the Brick while persistent_properties loads at
    // ~T+9 to 10 s. Without a brief wait here, the qr_prepared read
    // below comes back "0" on every cold boot and the drastic fast
    // path is silently skipped (NanoMenu falls into the XMB render
    // loop instead). Wait up to 2 s, breaking out as soon as the
    // property service publishes ro.persistent_properties.ready=true.
    {
        char ready[PROPERTY_VALUE_MAX] = {};
        property_get("ro.persistent_properties.ready", ready, "");
        if (strcmp(ready, "true") != 0) {
            int waited = 0;
            for (int i = 0; i < 200; i++) {
                usleep(10000);
                waited += 10;
                property_get("ro.persistent_properties.ready",
                             ready, "");
                if (!strcmp(ready, "true")) break;
            }
            if (strcmp(ready, "true") == 0) {
                ALOGI("NanoMenu: persist props ready after %d ms "
                      "(drastic QR pre-check)", waited);
            } else {
                ALOGW("NanoMenu: persist props still not ready after "
                      "%d ms (drastic QR pre-check), continuing",
                      waited);
            }
        }
    }
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

        // The resident OVERLAY process must NEVER take the QR fast-path or touch
        // the launch_app/launch_intent props: it runs at every boot and setting
        // launch_intent would make RootWindowContainer auto-launch an app on boot
        // (the user only wants quick-resume to launch, and only when they set it).
        sDrasticQrFastPath = (smokeActive || drasticQrPrimed) && !mOverlayMode;
        if (sDrasticQrFastPath) {
            ALOGW("NanoMenu: drastic QR fast-path ACTIVE "
                  "(smoke=%d qr_primed=%d), skipping heavy init",
                  smokeActive ? 1 : 0, drasticQrPrimed ? 1 : 0);
            // Set launch_app early so SystemServer's relaunch monitor
            // sees drastic (not retroarch) when it reads the prop.
            // Previously this was set at handoff time (~30s later),
            // racing with the monitor's first read.
            property_set("sys.gammaos.nano.launch_app",
                         "com.dsemu.drastic");
            property_set("sys.gammaos.nano.launch_intent", "file");
        }
    }

    // GammaOS: Nano mode is confirmed active (skip_nano check passed).
    // Run DRM splash now - before any SF/HWC setup. This replaces the
    // bootloader logo with NanoMenu's DRM direct rendering. Skip on
    // restarts (returning from app) since HWC is already active.
    {
        char bootDone[PROPERTY_VALUE_MAX] = {};
        property_get("sys.boot_completed", bootDone, "0");
        // force_drm=1 re-grabs DRM master post-boot for drastic nano
        // mode (XMB -> drastic nano restart path).
        char forceDrm[PROPERTY_VALUE_MAX] = {};
        property_get("sys.gammaos.nano.force_drm", forceDrm, "0");
        // Overlay mode never grabs DRM master: it coexists with SurfaceFlinger
        // and the running app as a translucent layer. Force the SF path.
        if (mOverlayMode || nanoForceSfPath()) {
            ALOGI("NanoMenu: %s, skipping DRM splash (SF path)",
                  mOverlayMode ? "overlay mode" : "force-SF home");
        } else if (strcmp(bootDone, "1") != 0 || strcmp(forceDrm, "1") == 0) {
            if (strcmp(forceDrm, "1") == 0) {
                ALOGW("NanoMenu: force_drm=1, grabbing DRM master "
                      "post-boot for drastic nano");
            }
            drmEarlySplash(earlyDrmFd);
            earlyDrmFd = -1;
        } else {
            ALOGI("NanoMenu: skipping DRM splash (already booted)");
        }
    }
    if (earlyDrmFd >= 0 && !sDrmActive) {
        // DRM splash failed - release the fd so HWC can use it
        ioctl(earlyDrmFd, DRM_IOCTL_DROP_MASTER, 0);
        close(earlyDrmFd);
        earlyDrmFd = -1;
    }
    tlog("drmEarlySplash done");

    // Diagnostic: write DRM splash result to file (logcat overflows)
    {
        int logfd = open("/data/local/tmp/drm_grab.log", O_WRONLY|O_APPEND|O_CREAT, 0644);
        if (logfd >= 0) {
            char buf[256];
            int n = snprintf(buf, sizeof(buf),
                "readyToRun: sDrmActive=%d earlyDrmFd=%d displays=%zu\n",
                sDrmActive ? 1 : 0, earlyDrmFd, sDrmDisplays.size());
            write(logfd, buf, n);
            close(logfd);
        }
    }

    // Nano mode is active — tell any boot animation instance to exit.
    // Vendor init may start bootanim independently (e.g. in on late-fs),
    // so it can be running alongside us with the same z-layer.
    //
    // CRITICAL: only the HOME nano may do this. The resident OVERLAY process also
    // runs readyToRun() (it starts at boot_completed), and the HOME nano watches
    // service.bootanim.exit and EXITS when it flips to 1 (it reads that as the
    // app-handoff signal). If the overlay set it, the home nano would exit into a
    // blank screen the moment the overlay service starts. The overlay is not the
    // boot-animation owner, so it must leave this flag alone.
    if (!mOverlayMode) {
        property_set("service.bootanim.exit", "1");
    }

    if (sDrmActive) {
        // DRM boot path: headless EGL, no SurfaceFlinger dependency.
        // The DRM infrastructure is already set up by drmEarlySplash().
        // Create a pbuffer EGL context so GL calls work, but all actual
        // rendering goes through AHB-backed FBOs flipped to DRM scanout.
        mDrmBootPath = true;

        const DrmDisplay& prim = sDrmDisplays[sDrmPrimaryIdx];
        if (sDrmRotationDeg == 90 || sDrmRotationDeg == 270) {
            mWidth = (int)prim.h;
            mHeight = (int)prim.w;
        } else {
            mWidth = (int)prim.w;
            mHeight = (int)prim.h;
        }

        EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        eglInitialize(display, nullptr, nullptr);
        EGLConfig config = getEglConfig(display);
        EGLint pbufAttrs[] = { EGL_WIDTH, 16, EGL_HEIGHT, 16, EGL_NONE };
        EGLSurface surface = (config != nullptr)
                ? eglCreatePbufferSurface(display, config, pbufAttrs)
                : EGL_NO_SURFACE;
        EGLint contextAttributes[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
        EGLContext context = (config != nullptr)
                ? eglCreateContext(display, config, nullptr, contextAttributes)
                : EGL_NO_CONTEXT;

        // Validate the pbuffer EGL setup before committing to the DRM-direct
        // path. On EX8 (Mali-G57 MC2 + MTK MT6789) the libEGL Mali wrapper
        // returns no usable config when called from a process that has not
        // established a SurfaceFlinger binder connection yet, so config /
        // surface / context all come back NULL even though eglInitialize
        // returned EGL_TRUE. Without this guard the DRM-direct path commits
        // to a broken GL context (GL_VERSION='(null)'), drmSetupZeroCopy
        // bails because EGL_ANDROID_image_native_buffer is missing, and the
        // panel is left showing the dark dumb-buffer fill from drmEarlySplash
        // until something else (RetroArch launching) forces HWC to take
        // master back. Detect the failure here and fall through to the SF
        // window-surface path (which waits for SF binder via the retry in
        // the SF block below and then gets a real EGL config) by destroying
        // the partial EGL state, releasing DRM master + closing the fd via
        // drmReleaseEarly, and clearing mDrmBootPath / sDrmActive. Devices
        // where the initial getEglConfig already succeeds (AIR X Adreno,
        // Rockchip Mali) keep taking the DRM-direct branch unchanged.
        if (config == nullptr || surface == EGL_NO_SURFACE ||
            context == EGL_NO_CONTEXT) {
            ALOGW("NanoMenu: DRM-direct pbuffer EGL setup failed on this "
                  "device (config=%p surface=%p context=%p) - releasing DRM "
                  "master and falling back to SurfaceFlinger window-surface path",
                  (void*)config, (void*)surface, (void*)context);
            if (surface != EGL_NO_SURFACE) eglDestroySurface(display, surface);
            if (context != EGL_NO_CONTEXT) eglDestroyContext(display, context);
            if (display != EGL_NO_DISPLAY) eglTerminate(display);
            drmReleaseEarly();
            mDrmBootPath = false;
            // Falls through to the if (!sDrmActive) SF block below.
        } else {
            if (eglMakeCurrent(display, surface, surface, context) == EGL_FALSE)
                return NO_INIT;

            mDisplay = display; mContext = context; mSurface = surface;
            mFlingerSurfaceControl = nullptr; mFlingerSurface = nullptr;

            ALOGD("NanoMenu: DRM boot path %dx%d (headless EGL, no SF)", mWidth, mHeight);
            tlog("headless EGL init");

            property_set("sys.gammaos.nano.menu_active", "1");
            {
                char lastApp[PROPERTY_VALUE_MAX] = {};
                property_get("sys.gammaos.nano.launched_pkg", lastApp, "");
                if (lastApp[0] != '\0') {
                    property_set("sys.gammaos.nano.kill_pkg", lastApp);
                    ALOGD("NanoMenu: signaled framework to kill: %s", lastApp);
                    property_set("sys.gammaos.nano.launched_pkg", "");
                }
            }

            drmSetupZeroCopy(display);
            {
                char buf[PROPERTY_VALUE_MAX];
                snprintf(buf, sizeof(buf), "zc%d_ahbW%u_ahbH%u_fbo%u",
                         sDrmZeroCopy ? 1 : 0,
                         sDrmZeroCopy ? sAhbRingPrimary[0].w : 0,
                         sDrmZeroCopy ? sAhbRingPrimary[0].h : 0,
                         sDrmZeroCopy ? sAhbRingPrimary[0].glFbo : 0);
                property_set("sys.gammaos.nano.drm_zc", buf);
            }

            // DRM zero-copy rendering active - no SF needed.
            tlog("DRM zero-copy setup");
        }
    }
    if (!sDrmActive) {
        // SF path with headless pre-init: create a pbuffer EGL context
        // immediately so shaders/fonts/icons can compile while SF is
        // still starting up. Then switch to the SF window surface once
        // SF is ready. This overlaps ~1s of GL init with SF startup.
        EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        eglInitialize(display, nullptr, nullptr);
        // Overlay needs an alpha-capable config so its window surface is truly
        // translucent (the context + window surface configs must match).
        EGLConfig config = getEglConfig(display, mOverlayMode);

        // First eglChooseConfig may return null because:
        //   1) the GPU userspace driver (e.g. pvrsrvinit on Allwinner A133
        //      PowerVR Rogue) is still initializing in parallel and the
        //      libEGL wrapper has not yet been able to load the per-vendor
        //      libEGL_*.so successfully, or
        //   2) the libEGL Mali wrapper (MT6789 / Mali-G57 MC2 EX8) refuses
        //      to return a usable config until the SurfaceFlinger binder
        //      service is registered, regardless of GPU readiness.
        //
        // Both cases recover within ~1-3 s of driver/SF coming up. Poll
        // eglTerminate + eglGetDisplay + eglInitialize + getEglConfig
        // directly, every 50 ms, capped at 5 s. This avoids the previous
        // "wait for SF binder, retry once" strategy, which on the A133
        // (case 1 above) burned ~5 s of wall time because the SF binder
        // wait satisfied first but PVR EGL was still warming, leaving a
        // useless re-init that then had to be done again on the next
        // pass through this function. Devices that already had a valid
        // config on the first call (Adreno, Rockchip Mali) take the loop
        // body never.
        if (config == nullptr) {
            int waitMs = 0;
            const int stepMs = 50;
            const int capMs = 5000;
            while (config == nullptr && waitMs < capMs) {
                usleep(stepMs * 1000);
                waitMs += stepMs;
                eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
                eglTerminate(display);
                display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
                eglInitialize(display, nullptr, nullptr);
                config = getEglConfig(display);
            }
            if (config == nullptr) {
                ALOGE("NanoMenu: EGL config still null after polling %dms, "
                      "cannot continue", waitMs);
                return NO_INIT;
            }
            ALOGW("NanoMenu: polled %dms before libEGL returned a usable "
                  "config (GPU driver and/or SurfaceFlinger warm-up)", waitMs);
        }

        EGLint pbufAttrs[] = { EGL_WIDTH, 16, EGL_HEIGHT, 16, EGL_NONE };
        EGLSurface pbufSurface = eglCreatePbufferSurface(display, config, pbufAttrs);
        // Overlay: request a HIGH-priority GPU context so nano's compositing is
        // scheduled ahead of the live app's rendering (smoother XMB over the app).
        // Guarded on EGL_IMG_context_priority so it can never EGL_BAD_ATTRIBUTE on
        // drivers that lack it (Mali supports it). 0x3100/0x3101 = the IMG enums.
        EGLint contextAttributes[] = {EGL_CONTEXT_CLIENT_VERSION, 2,
                                      EGL_NONE, EGL_NONE, EGL_NONE};
        if (mOverlayMode) {
            const char* eglExts = eglQueryString(display, EGL_EXTENSIONS);
            if (eglExts && strstr(eglExts, "EGL_IMG_context_priority")) {
                contextAttributes[2] = 0x3100;   // EGL_CONTEXT_PRIORITY_LEVEL_IMG
                contextAttributes[3] = 0x3101;   // EGL_CONTEXT_PRIORITY_HIGH_IMG
                ALOGI("NanoMenu: overlay requesting HIGH-priority GPU context");
            }
        }
        EGLContext context = eglCreateContext(display, config, nullptr, contextAttributes);
        if (eglMakeCurrent(display, pbufSurface, pbufSurface, context) == EGL_FALSE)
            return NO_INIT;
        mDisplay = display; mContext = context; mSurface = pbufSurface;
        // Temporary dimensions from DRM connector mode (if available) or
        // fallback. These get updated once SF tells us the real resolution.
        mWidth = 1920; mHeight = 1080;
        ALOGD("NanoMenu: headless EGL pre-init (SF path, %dx%d assumed)", mWidth, mHeight);
        tlog("headless EGL pre-init");

        // Overlay mode coexists with the running app: do NOT claim menu_active
        // or signal a kill of the foreground package (that would tear down the
        // very app we are overlaying).
        if (!mOverlayMode) {
            property_set("sys.gammaos.nano.menu_active", "1");
            char lastApp[PROPERTY_VALUE_MAX] = {};
            property_get("sys.gammaos.nano.launched_pkg", lastApp, "");
            if (lastApp[0] != '\0') {
                property_set("sys.gammaos.nano.kill_pkg", lastApp);
                ALOGD("NanoMenu: signaled framework to kill: %s", lastApp);
                property_set("sys.gammaos.nano.launched_pkg", "");
            }
        }
    }

    // Shader/font/icon init works on any EGL context (pbuffer or SF window).
    // On the SF pre-init path, this runs in parallel with SF startup.
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

    // Initialize brightness. Priority:
    // 1. Android settings (authoritative, but not available during early boot)
    // 2. Persist property (available at /data mount, before settings provider)
    // 3. Sysfs current value (last resort - may be bootloader default)
    mMaxBrightness = readSysfsInt("/sys/class/backlight/panel0-backlight/max_brightness",
                     readSysfsInt("/sys/class/leds/lcd-backlight/max_brightness", 255));
    int androidBrt = readAndroidBrightness();
    if (androidBrt > 0) {
        mBrightness = androidBrt;
    } else {
        char saved[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.brightness", saved, "");
        if (saved[0] != '\0') {
            mBrightness = atoi(saved);
        } else {
            mBrightness = 128; // safe default (~50%)
        }
    }
    if (mBrightness < 1) mBrightness = 1;
    if (mBrightness > 255) mBrightness = 255;
    // Write to sysfs for instant backlight during early boot. The shared
    // enumerator scales per node max (the old fixed-path loop wrote one
    // device-wide sysfs value to every node).
    nanobl::nanoBacklightSet(mBrightness);
    ALOGI("NanoMenu: early sysfs brightness (android %d) applied to %zu backlight nodes",
          mBrightness, nanobl::nanoBacklightNodes().size());

    // Apply brightness async via HAL (expects sysfs-range value)
    {
        int brightness = mBrightness * mMaxBrightness / 255;
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

    // Restore volume from the persist properties PhoneWindowManager publishes (the
    // actual STREAM index + range), so the slider matches real output from boot.
    {
        char savedMax[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.volmax", savedMax, "");
        if (savedMax[0]) { int m = atoi(savedMax); if (m > 0) mMaxVolume = m; }
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

    // Deferred SF surface creation: on the non-DRM path, all GL init
    // was done on a headless pbuffer while SF was starting up. Now
    // create the real SF surface and switch EGL to it.
    if (!sDrmActive && !mDrmBootPath && mFlingerSurface == nullptr) {
        tlog("waiting for SF");
        // Overlay mode can start before SurfaceFlinger is up (e.g. the resident
        // service launches at boot_completed while the home nano still owns the
        // DRM-direct display and SF has not been needed yet). Wait for SF rather
        // than failing, so the overlay is ready by the time an app launches and
        // SF takes over the display.
        if (mOverlayMode || nanoForceSfPath()) {
            sp<IServiceManager> sm = defaultServiceManager();
            const String16 sfName("SurfaceFlinger");
            int waited = 0;
            while (sm->checkService(sfName) == nullptr) {
                usleep(100000);
                waited += 100;
                if ((waited % 5000) == 0)
                    ALOGI("overlay: waiting for SurfaceFlinger (%dms)", waited);
            }
        }
        mSession = new SurfaceComposerClient();
        mSession->linkToComposerDeath(this);
        tlog("SurfaceComposerClient ready");

        std::vector<PhysicalDisplayId> ids = SurfaceComposerClient::getPhysicalDisplayIds();
        if (ids.empty() && (mOverlayMode || nanoForceSfPath())) {
            // Displays not enumerated yet; poll until SF reports them.
            int waited = 0;
            while (ids.empty() && waited < 30000) {
                usleep(100000);
                waited += 100;
                ids = SurfaceComposerClient::getPhysicalDisplayIds();
            }
        }
        if (ids.empty()) { ALOGE("No displays found"); return NAME_NOT_FOUND; }

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
        }

        mDisplayToken = SurfaceComposerClient::getPhysicalDisplayToken(chosenId);
        if (mDisplayToken == nullptr) return NAME_NOT_FOUND;

        ui::DisplayState chosenDisplayState;
        ui::LayerStack chosenLayerStack = ui::DEFAULT_LAYER_STACK;
        if (SurfaceComposerClient::getDisplayState(mDisplayToken, &chosenDisplayState) == NO_ERROR) {
            chosenLayerStack = chosenDisplayState.layerStack;
        }
        mAppliedLayerStack = chosenLayerStack.id;

        DisplayMode displayMode;
        if (SurfaceComposerClient::getActiveDisplayMode(mDisplayToken, &displayMode) != NO_ERROR)
            return NO_INIT;

        ui::Size resolution = displayMode.resolution;
        // Overlay mode: a TRANSLUCENT layer (RGBA, no eOpaque) so the
        // SurfaceFlinger-blurred running app shows through the transparent
        // regions of the XMB. Home mode keeps the opaque RGBX fast path.
        sp<SurfaceControl> control = session()->createSurface(
            String8(mOverlayMode ? "GammaOSNanoOverlay" : "GammaOSNano"),
            resolution.getWidth(), resolution.getHeight(),
            mOverlayMode ? PIXEL_FORMAT_RGBA_8888 : PIXEL_FORMAT_RGBX_8888,
            mOverlayMode ? 0u : ISurfaceComposerClient::eOpaque);

        SurfaceComposerClient::Transaction t;
        Rect forcedRes(0, 0, resolution.width, resolution.height);
        Rect physRes(0, 0, displayMode.resolution.width, displayMode.resolution.height);
        // Overlay must not retarget the display projection (the running app
        // owns it); only the home instance forces the projection/size.
        if (!mOverlayMode) {
            t.setDisplayProjection(mDisplayToken, ui::ROTATION_0, forcedRes, physRes);
        }
        // A very high Z so the overlay sits above app + system windows.
        t.setLayer(control, mOverlayMode ? 0x7FFFFFF0 : 0x40000001);
        t.setLayerStack(control, chosenLayerStack);
        // Explicitly show the layer. createSurface usually creates a
        // visible SurfaceControl on Android 14, but the 2026-05-13 boot
        // path change (StartPropertySetThread no longer fires bootanim
        // on the nano route) leaves the primary display with no boot-time
        // layer chain. On the Brick (Allwinner A133 + sunxi HWC) the
        // SurfaceFlinger composition output then shows pure black for the
        // QR libretro loop even though NanoMenu is calling
        // eglSwapBuffers, because the layer's visibility flag never got
        // flipped on by anything else. Mirrors what
        // setupSecondaryEglSurfaces() already does for the wallpaper
        // SurfaceControl.
        // Overlay starts HIDDEN (resident, shown on power-hold). The home
        // instance shows immediately.
        if (mOverlayMode) {
            t.hide(control);
        } else {
            t.show(control);
            char dsActive[PROPERTY_VALUE_MAX] = {};
            property_get("sys.gammaos.dualstack.active", dsActive, "0");
            if (!strcmp(dsActive, "1")) {
                property_set("sys.gammaos.dualstack.active", "0");
                property_set("sys.gammaos.nano.clear_forced_size", "1");
            }
        }
        // Universal perf hint: vote 60fps on the overlay layer so the vendor's
        // frame-deadline DVFS (e.g. MTK FPSGo) can boost for it like a foreground
        // app. Overlay only (the home is DRM-direct). No-op where unhonored.
        if (mOverlayMode && property_get_bool("persist.gammaos.nano.perf.framerate", true)) {
            t.setFrameRate(control, 60.0f,
                           ANATIVEWINDOW_FRAME_RATE_COMPATIBILITY_DEFAULT,
                           ANATIVEWINDOW_CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);
            ALOGI("perf: setFrameRate(60) voted on overlay layer");
        }
        t.apply();

        sp<Surface> s = control->getSurface();
        // Overlay: one extra dequeueable buffer (quadruple buffering). With the
        // GammaOS color transform active, SF client-composites every frame and
        // releases the producer's buffer only when its GPU pass finishes, so
        // nano's dequeue is paced by that release. Traced on the Brick: the
        // pipeline has two stable phases - queue-ahead (presents 60fps) and
        // release-paced (~50fps, dequeueBuffer blocking 7-18ms mid-frame) - and
        // which one the post-game raise lands in was a per-run coin toss. The
        // extra buffer absorbs the phase offset so the queue-ahead state is the
        // only equilibrium. Costs one frame of input latency on the menu only.
        if (mOverlayMode) s->setMaxDequeuedBufferCount(3);
        EGLConfig config = getEglConfig(mDisplay, mOverlayMode);
        EGLSurface sfSurface = eglCreateWindowSurface(mDisplay, config, s.get(), nullptr);
        eglMakeCurrent(mDisplay, sfSurface, sfSurface, mContext);
        // Overlay: swap interval 0 + the threadLoop top-up sleep paces frames;
        // a vsync-blocking swap turned any frame a hair over 16.7ms into a
        // 33ms one (see the pacing comment in threadLoop). overlayShow switches
        // to interval 1 in wallpaper mode (overlayApplyPresentMode).
        if (mOverlayMode) eglSwapInterval(mDisplay, 0);
        eglDestroySurface(mDisplay, mSurface);
        mSurface = sfSurface;
        mFlingerSurfaceControl = control;
        mFlingerSurface = s;

        EGLint w, h;
        eglQuerySurface(mDisplay, mSurface, EGL_WIDTH, &w);
        eglQuerySurface(mDisplay, mSurface, EGL_HEIGHT, &h);
        mWidth = w; mHeight = h;

        drmSetupZeroCopy(mDisplay);

        ALOGD("NanoMenu: SF surface ready %dx%d (pre-init complete)", mWidth, mHeight);
        tlog("SF surface switch done");
    }

    return NO_ERROR;
}

// ADPF (Android Dynamic Performance Framework) hint session for the render
// thread. This is the universal, vendor-agnostic way to ask the power HAL to run
// a thread at a performance level that meets a target frame time; the vendor
// implementation (CPU on A14, and GPU where supported) boosts accordingly. We
// create one session for the render thread with a 60fps target and report the
// per-frame work duration. Prop-gated for A/B testing; a no-op if the platform
// has no PerformanceHintManager.
void NanoMenu::perfHintInit(int tid) {
    if (mHintTried) return;
    mHintTried = true;
    if (!property_get_bool("persist.gammaos.nano.perf.adpf", true)) return;
    APerformanceHintManager* mgr = APerformanceHint_getManager();
    if (mgr == nullptr) { ALOGW("perf: ADPF PerformanceHintManager unavailable"); return; }
    int32_t tids[1] = { (int32_t)tid };
    mHintSession = (void*)APerformanceHint_createSession(mgr, tids, 1, 16666666LL);  // 60fps target
    ALOGI("perf: ADPF hint session %s (render tid=%d, target=16.67ms)",
          mHintSession ? "created" : "FAILED", tid);
}

void NanoMenu::perfHintReport() {
    if (mHintSession == nullptr || mLastFrameNs <= 0) return;
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
    int64_t nowNs = (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
    int64_t workNs = nowNs - mLastFrameNs;   // frame start -> after render + present
    if (workNs > 0 && workNs < 200000000LL)
        APerformanceHint_reportActualWorkDuration(
            (APerformanceHintSession*)mHintSession, workNs);
}

// Run /vendor/bin/setclock_<mode>.sh in the background to switch the CPU
// governor/clocks. `mode` is validated against the known set (the scripts that
// actually ship) so the value can never inject into the shell command.
void NanoMenu::nanoApplyPerfClock(const char* mode) {
    const char* m = "stock";
    if (mode) {
        if (!strcmp(mode, "max")) m = "max";
        else if (!strcmp(mode, "powersave")) m = "powersave";
        else m = "stock";
    }
    char cmd[96];
    snprintf(cmd, sizeof(cmd), "/vendor/bin/setclock_%s.sh &", m);
    system(cmd);
}

// Re-apply the user's persisted performance mode (used on wake to restore whatever
// was active before the screen turned off).
void NanoMenu::nanoRestorePerfClock() {
    char mode[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.performance_mode", mode, "stock");
    nanoApplyPerfClock(mode);
}

bool NanoMenu::threadLoop() {
    ALOGD("NanoMenu: entering main loop");

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
        // Register this render thread with ADPF so the power HAL can meet the
        // 60fps target (universal; no-op where unsupported).
        perfHintInit((int)selfTid);
        if (rc == 0) {
            ALOGW("NanoMenu: render thread SCHED_FIFO prio 80 + nice -20 ok");
        } else {
            ALOGW("NanoMenu: SCHED_FIFO denied (%s), nice -20 applied",
                  strerror(rc));
        }
    }

    // Launch the Bluetooth A2DP stability monitor on its own background thread.
    // While a BT sink is the active audio route it keeps the controller awake and,
    // while audio is streaming, pins the CPU so software A2DP encoding never
    // starves (stability over battery, gated by persist.gammaos.nano.btstable).
    // Idempotent + self-electing across the two nano processes; see NanoBtStable.
    nanoStartBtStability();

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
    // Disable scudo's secondary (>64 KB) allocation cache BEFORE locking
    // memory, and drain anything already cached. mlockall makes every
    // anonymous mapping VM_LOCKED, and madvise(MADV_DONTNEED) fails with
    // EINVAL on locked pages. scudo's cache release path ignores that
    // error and still marks the cached entry as zeroed, so any later
    // calloc served from the cache SKIPS its memset and returns the
    // previous owner's dirty bytes. The Mali Bifrost blob (RK356x) trusts
    // calloc zero-fill for a lazy-init pointer slot in the framebuffer
    // object it allocates on the first glBindFramebuffer of a new name,
    // dereferences the stale garbage and crashes the render thread
    // (reproduced 100% on the RG DS the moment the PS3 wave background
    // created its first FBO after the wave shader links had churned the
    // heap; same root cause for ps3xmb=1 and the wave wallpaper).
    // With the cache off, every large allocation is a fresh kernel-zeroed
    // mmap, restoring calloc's contract. Nano's steady-state frame loop
    // does no large allocations, so the only cost is a few one-time mmaps
    // during asset loads. Order matters: cache off, purge, THEN mlockall,
    // so no dirty entry can be cached in between (loader threads free
    // large buffers concurrently during startup).
    mallopt(M_CACHE_COUNT_MAX, 0);
    mallopt(M_PURGE_ALL, 0);
    // MCL_CURRENT | MCL_FUTURE: keep EVERY mapping (fonts, glyph atlases, textures)
    // pinned resident. This is REQUIRED on this EROFS/loop panel: dropping
    // MCL_FUTURE left the fonts reclaimable, so every on-screen-keyboard open then
    // demand-faulted font glyph pages back off the lz4-compressed system image,
    // and that decompress thrashed/OOMed (kernel panic in minimal boot, OOM-kill
    // of system_server under full Android). With the pages locked, glyph rendering
    // never faults. The cost is the resident working set; the right way to shrink
    // it is to load fewer/smaller assets (see the memory-footprint audit), NOT to
    // unlock them. Paired with the mallopt above for the scudo/Mali calloc fix.
    if (mlockall(MCL_CURRENT | MCL_FUTURE) == 0) {
        ALOGW("NanoMenu: mlockall done (scudo secondary cache disabled)");
    } else {
        ALOGW("NanoMenu: mlockall failed (%s) -- check caps/rlimit in "
              "gammaos-nano.rc", strerror(errno));
    }

    // Clear any stale drop_input/fence from a previous instance.
    property_set("sys.gammaos.nano.drop_input", "0");
    property_set("sys.gammaos.nano.drop_fence_ns", "0");
    // Clear the CC display-0 rotation pin too, so a crash/restart never strands display 0 locked to
    // ROTATION_0 (the render loop re-asserts it while the CC is actually up).
    property_set("sys.gammaos.nano.cc.active", "0");

    // RG DS (dual-screen): the system soft keyboard must appear on the SAME physical panel as the
    // focused app. A top-screen app (display 2) is SurfaceFlinger-composited on the top panel while
    // nano renders the bottom panel DRM-direct, so any IME routed to the bottom (default) display is
    // fully occluded by nano's output and never seen. The old ime.pin subsystem forced every keyboard
    // onto one fixed display (0) and could NEVER show for that reason. The real fix lives in the
    // framework: DisplayContent.getImePolicy() now returns DISPLAY_IME_POLICY_LOCAL for trusted
    // internal secondary displays (gated persist.gammaos.ime.localdisplay, default off), so each panel
    // hosts its own IME over its own app - exactly like the default display already does on the bottom.
    // Here we opt in to that policy (persist.gammaos.ime.localdisplay, DEFAULT OFF in the framework so
    // stock Android / non-nano contexts are never touched) and retire the broken fixed-display pin so
    // IMMS stops force-routing to display 0. The localdisplay flag is persisted, so on later boots it is
    // loaded before WindowManager builds its display IME-policy cache and the top panel is LOCAL from the
    // start. Single-panel devices (the shared GSI also serves the Brick) have no secondary display, so
    // neither flag is set and the framework gate stays inert there.
    if (hasSecondaryDisplay()) {
        property_set("persist.gammaos.ime.localdisplay", "1");
        property_set("persist.gammaos.ime.pin.enabled", "0");
    }

    // readyToRun() sets service.bootanim.exit=1 to kill the vendor bootanim.
    // Reset it here so our own exit check (further below) doesn't immediately
    // terminate the menu on restarts.
    property_set("service.bootanim.exit", "0");

    // Wait for persistent properties to load before reading any persist.*
    // values. readyToRun() used to wait for ro.persistent_properties.ready
    // up top, but the 2026-05-13 boot-speed work moved that wait out of
    // the EGL hot path so first paint is not delayed. We still need the
    // wait here, otherwise the re-reads below (and the Quick Resume gate
    // further down at "if (mQuickResumeEnabled)") fall back to defaults
    // when persist props have not loaded yet, silently disabling QR auto
    // resume on every cold boot. Cap at 3 s, which is plenty given persist
    // props normally settle within ~200 ms of /data being mounted.
    {
        char ready[PROPERTY_VALUE_MAX] = {};
        property_get("ro.persistent_properties.ready", ready, "");
        if (strcmp(ready, "true") != 0) {
            int waited = 0;
            for (int i = 0; i < 300; i++) {
                usleep(10000);
                waited += 10;
                property_get("ro.persistent_properties.ready", ready, "");
                if (!strcmp(ready, "true")) break;
            }
            if (strcmp(ready, "true") == 0) {
                ALOGI("NanoMenu: persist props ready after %d ms", waited);
            } else {
                ALOGW("NanoMenu: persist props still not ready after "
                      "%d ms, continuing with possibly stale defaults",
                      waited);
            }
        }
    }

    // Initialize locale from system property
    nanoInitLocaleFromSystem();

    // Check if setup wizard is needed. persist.gammaos.nano.setup_done is
    // nano's own fast-path completion flag, but a device that already ran
    // SetupWizard on the normal Android side (before ever booting into nano
    // mode, or after a nano<->Android mode switch) will never have set it.
    // persist.sys.device_provisioned is a persisted mirror of
    // Settings.Global.DEVICE_PROVISIONED that ActivityManagerService keeps
    // up to date (see watchDeviceProvisioned() in ActivityManagerService.java)
    // regardless of which side completed setup, and unlike the settings
    // provider it is safe to read this early since it is a plain persisted
    // property, not something backed by a running service. Treat it as an
    // equally valid "already provisioned" signal so switching between the
    // two modes never re-triggers a wizard that already ran on the other side.
    {
        char setupDone[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.setup_done", setupDone, "");
        if (strcmp(setupDone, "1") != 0) {
            char devProvisioned[PROPERTY_VALUE_MAX] = {};
            property_get("persist.sys.device_provisioned", devProvisioned, "");
            if (strcmp(devProvisioned, "1") == 0) {
                property_set("persist.gammaos.nano.setup_done", "1");
                ALOGI("NanoMenu: setup wizard skipped (already provisioned via "
                      "persist.sys.device_provisioned)");
            } else {
                startSetupWizard();
                ALOGI("NanoMenu: setup wizard active (persist.gammaos.nano.setup_done != 1)");
            }
        }
    }

    // Re-read quick resume flag — the constructor runs before persist props
    // are loaded, so the value read there may be stale (always false).
    mQuickResumeEnabled = android::base::GetBoolProperty(
            "persist.gammaos.nano.quick_resume", true);   // default ON (user decision 2026-07-01)
    mXmbMode = android::base::GetBoolProperty(
            "persist.gammaos.nano.xmb_mode", false);
    // PS3 XMB layout (NanoMenuPS3Menu.cpp). Dev-gated during build-up; takes
    // priority over the carousel (mXmbMode) when set.
    mPs3Xmb = android::base::GetBoolProperty(
            "persist.gammaos.nano.ps3xmb", true);   // default on when the prop is unset
    // DSi System Menu theme: an optional home theme that takes priority over the PS3
    // XMB when set (renders the DSi launcher carousel, plays the DSi boot animation).
    mNdsTheme = android::base::GetBoolProperty(
            "persist.gammaos.nano.ndstheme", false);
    if (mNdsTheme) mPs3Xmb = true;   // reuse the PS3 XMB home infrastructure (boot, input, overlay), swap the render
    // Dual-screen XMB: render a static PSP clock on the bottom panel instead of a second wave.
    // Cached once (read on the render hot path otherwise); only meaningful in pure XMB (!mNdsTheme)
    // on a device with a secondary panel (the render call sites are gated accordingly).
    // Enabled by DEFAULT (the RG DS bottom-screen PSP clock; toggle under Settings > Theme Settings
    // > Bottom Clock). No-op on a single-screen device: the render sites are gated on a secondary
    // panel, so defaulting on is inert there and only lights up the RG DS's bottom panel.
    mPs3BottomClock = android::base::GetBoolProperty(
            "persist.gammaos.nano.ps3xmb.bottomclock", true);
    // Bottom-screen Control Center: live dashboard on the bottom panel while a single-screen
    // (non-dual-stack) app runs on top. Dual-screen XMB only; the render gate re-checks context.
    mControlCenterEnabled = android::base::GetBoolProperty(
            "persist.gammaos.nano.ps3xmb.controlcenter", false);
    ALOGI("NanoMenu: persist read quick_resume=%d xmb_mode=%d ps3xmb=%d nds=%d",
          mQuickResumeEnabled ? 1 : 0, mXmbMode ? 1 : 0, mPs3Xmb ? 1 : 0, mNdsTheme ? 1 : 0);
    // PS3 cold-boot intro: play the full intro (wave/gradient reveal from black,
    // the white logo plate, the photosensitivity warning, then the XMB icon
    // pop-in) on a NORMAL cold boot only. App-return restarts (boot_completed /
    // force_drm) and QR / minimal-boot fast paths skip it. A dev knob
    // (persist.gammaos.nano.ps3boot_skip) skips it for fast iteration.
    if (mPs3Xmb && !sDrasticQrFastPath) {
        char bc[PROPERTY_VALUE_MAX] = {}, fd[PROPERTY_VALUE_MAX] = {};
        char sd[PROPERTY_VALUE_MAX] = {}, sk[PROPERTY_VALUE_MAX] = {};
        property_get("sys.boot_completed", bc, "0");
        property_get("sys.gammaos.nano.force_drm", fd, "0");
        property_get("persist.gammaos.nano.setup_done", sd, "");
        property_get("persist.gammaos.nano.ps3boot_skip", sk, "0");
        // This arming runs on the render thread's first iteration. On the force-SF path
        // (no DRM-direct home, e.g. Unisoc) nano cannot present until SurfaceFlinger is up,
        // so this runs ~2s later than on the DRM-direct path -- by which point
        // sys.gammaos.minimal_boot has already flipped to 1 (it is raised during every nano
        // boot). So minimal_boot must NOT be part of the cold-boot test, or the themed intro
        // (and its boot chime, fired from inside the intro) would never arm in force-SF.
        // sys.boot_completed is the reliable "this is an app-return restart, skip the intro"
        // signal (and stays 0 until long after nano's first frame on any path).
        bool coldBoot = (strcmp(bc, "1") != 0 && strcmp(fd, "1") != 0);
        bool fresh = (strcmp(sd, "1") != 0);
        // Play the intro on a normal cold boot, AND always before the setup wizard
        // on a fresh device (fresh=true) even when this is a restart rather than a
        // cold boot, so the boot animation always precedes the setup wizard. The
        // QR / minimal-boot / force_drm fast paths and the dev skip still bypass it.
        if ((coldBoot || fresh) && strcmp(sk, "1") != 0) {
            ps3BootReset(fresh);
            ALOGI("NanoMenu: PS3 boot intro armed (coldBoot=%d fresh=%d)",
                  coldBoot ? 1 : 0, fresh ? 1 : 0);
        }
    }
    // Re-read wallpaper effect (constructor ran before persist props loaded)
    {
        char wallpaper[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.nano.wallpaper", wallpaper, "22");
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
        // The in-process DRM-direct DrasticRunner preview only scans out on a true
        // DRM panel; on a SurfaceFlinger-owned panel it renders solid red. nano
        // cannot predict a device's mode when backend=="auto" (the binary resolves
        // it at runtime by trying to grab DRM master), so key strictly on backend:
        //   - "drm" : render the in-process DRM-direct preview (the else below).
        //   - "sf"  : show a "Quick Resuming..." splash, then hand off to the
        //             DrasticSf host activity (the SF preview + overlay keeper).
        //   - "auto": show the splash, then hand off via the normal DRM handshake
        //             (start=1) and let the binary self-resolve (DRM, or own-layer
        //             SF on a pure-SF device). No device guess in nano.
        std::string dnBackend = android::base::GetProperty(
                "persist.gammaos.drastic_nano.backend", "auto");
        bool dnGate = (android::base::GetProperty(
                "persist.gammaos.nano.drastic_nano", "0") == "1");
        // Force-SF devices (Unisoc/Spreadtrum) never own DRM master, so the in-process
        // DRM-direct DrasticRunner preview in the else branch below cannot scan out (it
        // renders solid red / garbled). Always take the SF route there: render the
        // "Quick Resuming..." splash (drmFrameEnd falls back to eglSwapBuffers on the SF
        // window) and hand off to the DrasticSf host activity, which renders the real
        // preview through SurfaceFlinger. This does not depend on the (persist, factory-
        // reset-clearable) backend/gate props being set. No effect on DRM-direct SoCs.
        if (nanoForceSfPath()) dnBackend = "sf";
        bool splashHandoff = nanoForceSfPath() || (dnGate && (dnBackend == "sf" || dnBackend == "auto"));
        void maybeNanoScreenshot();   // defined in NanoMenuRender.cpp
        if (splashHandoff) {
            ALOGW("drastic nano QR: backend=%s -- splash then binary handoff (slot 9)",
                  dnBackend.c_str());
            std::string romName = android::base::GetProperty(
                    "persist.gammaos.nano.qr_game_name", "");
            float sfS = fminf((float)mWidth / 1080.0f, (float)mHeight / 720.0f);
            if (sfS < 0.5f) sfS = 0.5f;
            float loadScale = 3.0f * sfS;
            struct input_event drain_ev;
            // Prime the rotation-matrix uniform on both shaders so drawText maps to
            // the (possibly rotated) DRM panel. Without this the text renders with a
            // zero matrix and is invisible -- just the dark-blue clear shows.
            // Mirrors the exit "Loading..." splash setup.
            {
                const GLuint progs[] = {mShaderProgram, mTextProgram};
                const GLint  locs[]  = {mLocRotation, mTextLocRotation};
                for (int i = 0; i < 2; i++) {
                    glUseProgram(progs[i]);
                    glUniformMatrix2fv(locs[i], 1, GL_FALSE, sDrmRotMat);
                }
            }
            // Live DS game preview: the in-process DrasticRunner (already loaded at
            // boot for this fast-path by main.cpp runDrasticInitIfNeeded) renders the
            // resumed game into an OFFSCREEN texture, which renderBothScreens then
            // blits into the present FBO. Unlike the DRM-direct AHB scanout (the else
            // branch below) this goes through the SAME drmFrameBegin/drmFrameEnd path
            // the text splash uses, so it does not render red on an SF panel. Falls
            // back to the text-only splash when the core is not initialized/ready.
            DrasticRunner* previewDs = DrasticRunner::getInstance();
            bool haveCore = (previewDs && previewDs->isInitialized());
            // RG DS: when a second DRM panel is present, render the resume splash
            // (preview + "Quick Resuming..." overlay + scrim) to BOTH panels. The old
            // single-panel splash only filled the primary AHB, so the secondary panel
            // stayed stale and the overlay + scrim never appeared on the dual-screen
            // resume (the panel scanned out drastic-nano's game at full colour with no
            // caption). Mirrors the in-process DRM preview's dual-panel split.
            bool splashDual = (sDrmActive && sDrmZeroCopy && sAhbTargetSecondary.glFbo != 0);
            if (haveCore) {
                system("/vendor/bin/setclock_max.sh");
                previewDs->initSurface(mWidth, mHeight, /*dualDisplay=*/splashDual);
                previewDs->setRotationMatrix(sDrmRotMat);
            }
            float dsSat = 0.15f, dsGrad = 1.0f;   // desaturated + gradient scrim, HELD until handoff
            bool handoffFade = false;             // once ready, fade to full colour then hand off
            // Show the splash until the framework can launch an activity (user
            // unlock -> home_launching) AND the ROM's storage is mounted, then hand
            // off. Drain input so a held button does not leak into the game. Bump
            // the render heartbeat each frame so the watchdog does not abort a slow
            // cold boot.
            // Preview input: forward gamepad buttons + the bottom-panel touch to the
            // live in-process DS core (previewDs) so the resumed game is actually
            // playable during the "Quick Resuming..." wait -- the finger drives the DS
            // touchscreen. Earlier this loop just DRAINED input; now every button and
            // the touch reach the game shown on the panels until the binary handoff.
            int  dsBtnMask   = 0;
            int  dsTouchX    = 128, dsTouchY = 96;   // last stylus pos, DS coords
            bool dsTouchHeld = false;
            int  dsRawX = -1, dsRawY = -1;           // latest raw digitizer sample
            int  dsTouchMinX = -1, dsTouchMaxX = -1; // ABS range (lazy EVIOCGABS)
            int  dsTouchMinY = -1, dsTouchMaxY = -1;
            // The bottom-panel digitizer (RG DS: gt9xx-0) is the DS touchscreen; reuse
            // the Control Center's device name + axis calibration so the touch lines up
            // with what already works on this panel.
            char touchDev[PROPERTY_VALUE_MAX] = {};
            property_get("persist.gammaos.nano.cc.touchdev", touchDev, "gt9xx-0");
            const bool tsSwap  = android::base::GetBoolProperty("persist.gammaos.nano.cc.touch_swap",  false);
            const bool tsFlipX = android::base::GetBoolProperty("persist.gammaos.nano.cc.touch_flipx", false);
            const bool tsFlipY = android::base::GetBoolProperty("persist.gammaos.nano.cc.touch_flipy", false);
            // Hoisted out of the loop: the resume ROM path is stable for the whole
            // wait, so read it once instead of per frame.
            std::string goRom = getQrRomPath();
            for (int wait = 0; wait < 2400; wait++) {   // up to ~40s (covers the CE-ROM unlock wait)
                // Read gamepad + the bottom-panel touch and forward both to the DS
                // core. A touch frame closes on SYN_REPORT; recompute the stylus
                // position then. Gamepad buttons accumulate into a sticky mask.
                bool dsTouchNewFrame = false;
                for (int fd : mInputFds) {
                    auto itn = mInputFdNames.find(fd);
                    const bool isTouch = (itn != mInputFdNames.end()
                                          && itn->second == touchDev);
                    while (read(fd, &drain_ev, sizeof(drain_ev)) == sizeof(drain_ev)) {
                        const struct input_event& ie = drain_ev;
                        if (isTouch) {
                            // Bottom digitizer -> DS touchscreen.
                            if (ie.type == EV_ABS) {
                                if (ie.code == ABS_MT_POSITION_X || ie.code == ABS_X) {
                                    dsRawX = ie.value;
                                    if (dsTouchMaxX < 0) {
                                        struct input_absinfo a;
                                        if (ioctl(fd, EVIOCGABS(ie.code), &a) == 0
                                                && a.maximum > a.minimum) {
                                            dsTouchMinX = a.minimum; dsTouchMaxX = a.maximum;
                                        } else { dsTouchMinX = 0; dsTouchMaxX = 640; }
                                    }
                                } else if (ie.code == ABS_MT_POSITION_Y || ie.code == ABS_Y) {
                                    dsRawY = ie.value;
                                    if (dsTouchMaxY < 0) {
                                        struct input_absinfo a;
                                        if (ioctl(fd, EVIOCGABS(ie.code), &a) == 0
                                                && a.maximum > a.minimum) {
                                            dsTouchMinY = a.minimum; dsTouchMaxY = a.maximum;
                                        } else { dsTouchMinY = 0; dsTouchMaxY = 480; }
                                    }
                                } else if (ie.code == ABS_MT_TRACKING_ID) {
                                    dsTouchHeld = (ie.value >= 0);
                                }
                            } else if (ie.type == EV_KEY && ie.code == BTN_TOUCH) {
                                dsTouchHeld = (ie.value != 0);
                            } else if (ie.type == EV_SYN && ie.code == SYN_REPORT) {
                                dsTouchNewFrame = true;
                            }
                            continue;
                        }
                        // Gamepad -> DS buttons (Nintendo face layout, same mapping as
                        // the playable-preview else-branch: BTN_SOUTH=A, BTN_EAST=B).
                        if (ie.type == EV_KEY) {
                            const bool pressed = (ie.value != 0);
                            auto bit = [&](int mask) {
                                if (pressed) dsBtnMask |=  mask;
                                else         dsBtnMask &= ~mask;
                            };
                            switch (ie.code) {
                            case BTN_SOUTH:  bit(DrasticRunner::kDsBtnA);      break;
                            case BTN_EAST:   bit(DrasticRunner::kDsBtnB);      break;
                            case BTN_NORTH:  bit(DrasticRunner::kDsBtnX);      break;
                            case BTN_WEST:   bit(DrasticRunner::kDsBtnY);      break;
                            case BTN_TL:
                            case KEY_L:      bit(DrasticRunner::kDsBtnL);      break;
                            case BTN_TR:
                            case KEY_R:      bit(DrasticRunner::kDsBtnR);      break;
                            case BTN_START:  bit(DrasticRunner::kDsBtnStart);  break;
                            case BTN_SELECT: bit(DrasticRunner::kDsBtnSelect); break;
                            case KEY_UP:     bit(DrasticRunner::kDsBtnUp);     break;
                            case KEY_DOWN:   bit(DrasticRunner::kDsBtnDown);   break;
                            case KEY_LEFT:   bit(DrasticRunner::kDsBtnLeft);   break;
                            case KEY_RIGHT:  bit(DrasticRunner::kDsBtnRight);  break;
                            default: break;
                            }
                        } else if (ie.type == EV_ABS) {
                            if (ie.code == ABS_HAT0X) {
                                dsBtnMask &= ~(DrasticRunner::kDsBtnLeft |
                                               DrasticRunner::kDsBtnRight);
                                if (ie.value < 0) dsBtnMask |= DrasticRunner::kDsBtnLeft;
                                if (ie.value > 0) dsBtnMask |= DrasticRunner::kDsBtnRight;
                            } else if (ie.code == ABS_HAT0Y) {
                                dsBtnMask &= ~(DrasticRunner::kDsBtnUp |
                                               DrasticRunner::kDsBtnDown);
                                if (ie.value < 0) dsBtnMask |= DrasticRunner::kDsBtnUp;
                                if (ie.value > 0) dsBtnMask |= DrasticRunner::kDsBtnDown;
                            }
                        }
                    }
                }
                // Recompute the DS stylus position when a touch frame closed. Map the
                // raw digitizer (its real ABS range) to DS coords 0..255 x 0..191,
                // applying the CC's per-panel swap/flip calibration.
                if (dsTouchNewFrame && dsRawX >= 0 && dsRawY >= 0
                        && dsTouchMaxX > dsTouchMinX && dsTouchMaxY > dsTouchMinY) {
                    float nx = (float)(dsRawX - dsTouchMinX) / (float)(dsTouchMaxX - dsTouchMinX);
                    float ny = (float)(dsRawY - dsTouchMinY) / (float)(dsTouchMaxY - dsTouchMinY);
                    if (tsSwap)  { float t = nx; nx = ny; ny = t; }
                    if (tsFlipX) nx = 1.0f - nx;
                    if (tsFlipY) ny = 1.0f - ny;
                    if (nx < 0.0f) nx = 0.0f; else if (nx > 1.0f) nx = 1.0f;
                    if (ny < 0.0f) ny = 0.0f; else if (ny > 1.0f) ny = 1.0f;
                    dsTouchX = (int)(nx * 255.0f + 0.5f);
                    dsTouchY = (int)(ny * 191.0f + 0.5f);
                }
                char val[PROPERTY_VALUE_MAX] = {};
                property_get("sys.gammaos.nano.home_launching", val, "");
                bool ready = (strcmp(val, "1") == 0);
                if (!ready) { property_get("sys.boot_completed", val, "0");
                              ready = (strcmp(val, "1") == 0); }
                // Also require the resume ROM to be actually READABLE before handing
                // off. On a boot-time QR resume the ROM lives on CE storage (/sdcard =
                // /storage/emulated/0) that is not accessible until user 0 unlocks;
                // handing off before then makes the drastic-nano binary time out
                // waiting for the ROM and exit, leaving a frozen splash and no game.
                // Holding the splash until the ROM is readable keeps the caption +
                // gradient animating through the unlock wait and lets the binary load
                // the ROM immediately after handoff.
                bool go = ready && isQrRomStorageReady()
                        && (goRom.empty() || access(goRom.c_str(), R_OK) == 0);
                // Keep the scrim + caption up for the whole "Quick Resuming..." wait;
                // only start the fade-to-full-colour once we are ready to hand off.
                if (go) handoffFade = true;
                // Push the accumulated pad + touch state into the live DS core so the
                // preview responds this frame. setInputWithTouch sets the pointer-down
                // bit from dsTouchHeld internally, so dsBtnMask never carries bit 31.
                if (haveCore)
                    previewDs->setInputWithTouch(dsBtnMask, dsTouchX, dsTouchY, dsTouchHeld);
                // Advance one DS frame into the offscreen texture BEFORE binding the
                // present target (renderDsToOffscreen leaves FBO 0 bound); then
                // drmFrameBegin binds the real present FBO so the DS quad + the text
                // both land in what actually gets presented (works whether that is
                // the AHB scanout FBO or the EGL window surface).
                if (haveCore) previewDs->renderDsToOffscreen();
                bool showingGame = (haveCore && previewDs->isFrameReady());
                // Caption follows the scrim: full while dsGrad is up (the whole wait),
                // fading out with it during the handoff transition. Same value for both
                // panels so it stays in sync.
                float capA = dsGrad;
                // "Quick Resuming..." + ROM name + system line into the currently-bound
                // FBO. drawText uses mWidth/mHeight for pixel->NDC and the text shader's
                // uRotation maps to the panel, so the same call is correct for either
                // panel (matches the in-process preview's drawOverlay).
                auto drawSplashOverlay = [&]() {
                    glEnable(GL_BLEND);
                    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                    const char* msg = trDyn("Quick Resuming...");
                    float msgW = measureText(msg, loadScale);
                    float msgY = showingGame ? mHeight * 0.80f : mHeight * 0.42f;
                    drawText(msg, (mWidth - msgW) / 2.0f, msgY, loadScale, 1.0f, 1.0f, 1.0f, capA);
                    float nameScale = loadScale * 0.5f;
                    float nameY = msgY + FONT_CHAR_H * loadScale + 12.0f * sfS;
                    if (!romName.empty()) {
                        float nameW = measureText(romName.c_str(), nameScale);
                        drawText(romName.c_str(), (mWidth - nameW) / 2.0f, nameY,
                                 nameScale, 0.85f, 0.85f, 0.95f, capA);
                    }
                    // System line (this fast-path is the drastic core -> Nintendo DS).
                    const char* sysL = trDyn("Nintendo DS");
                    float sysScale = loadScale * 0.4f;
                    float sysW = measureText(sysL, sysScale);
                    drawText(sysL, (mWidth - sysW) / 2.0f,
                             nameY + FONT_CHAR_H * nameScale + 8.0f * sfS,
                             sysScale, 0.6f, 0.65f, 0.75f, capA);
                    glDisable(GL_BLEND);
                };
                if (splashDual) {
                    // Pass 1: secondary panel (bottom DS screen). drmFrameEnd ->
                    // drmFlipAll presents sAhbTargetSecondary; without rendering here it
                    // stayed stale and the overlay never showed on the RG DS.
                    glBindFramebuffer(GL_FRAMEBUFFER, sAhbTargetSecondary.glFbo);
                    glViewport(0, 0, sAhbTargetSecondary.w, sAhbTargetSecondary.h);
                    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                    glClear(GL_COLOR_BUFFER_BIT);
                    if (showingGame) previewDs->renderBottomScreen(dsSat, dsGrad);
                    drawSplashOverlay();
                    // Pass 2: primary panel (top DS screen).
                    drmFrameBegin();
                    if (sDrmGlRotation && sDrmZeroCopy) glViewport(0, 0, sAhbTarget.w, sAhbTarget.h);
                    else                                glViewport(0, 0, mWidth, mHeight);
                    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                    glClear(GL_COLOR_BUFFER_BIT);
                    if (showingGame) previewDs->renderTopScreen(dsSat, dsGrad);
                    drawSplashOverlay();
                } else {
                    // Single panel (force-SF, or single-screen DRM): unchanged path.
                    drmFrameBegin();
                    // sAhbTarget is only valid on the DRM zero-copy path; gate on sDrmZeroCopy
                    // so a force-SF self-rotate (sDrmGlRotation=true, no AHB) does not collapse
                    // the viewport to 0x0. See the note in NanoMenuRender.cpp render().
                    if (sDrmGlRotation && sDrmZeroCopy) glViewport(0, 0, sAhbTarget.w, sAhbTarget.h);
                    else                                glViewport(0, 0, mWidth, mHeight);
                    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);   // black behind the DS
                    glClear(GL_COLOR_BUFFER_BIT);
                    if (showingGame) {
                        previewDs->renderBothScreens(dsSat, dsGrad);   // DS quad -> present FBO
                    }
                    drawSplashOverlay();
                }
                // Advance the handoff fade (scrim off, full colour) only once we are
                // handing off; the scrim + caption stay at full strength for the whole
                // "Quick Resuming..." wait, then fade out here as the game takes over.
                if (handoffFade) {
                    dsSat = fminf(dsSat + 0.05f, 1.0f);
                    dsGrad = fmaxf(dsGrad - 0.05f, 0.0f);
                }
                // Capture the composited splash+preview frame before the flip
                // (glReadPixels needs the content still bound); no-op unless
                // sys.gammaos.nano.shot is set. This is the only way to snapshot
                // the QR preview on the DRM-direct scanout path.
                maybeNanoScreenshot();
                drmFrameEnd(mDisplay, mSurface);
                mRenderHeartbeat.fetch_add(1, std::memory_order_relaxed);
                // Hand off once the fade-out has fully played (scrim gone), so the
                // drastic-nano game appears right as the preview reaches full colour.
                if (handoffFade && dsGrad <= 0.0f) break;
                usleep(16666);
            }
            // Hand off to the binary. setDrasticNanoRomPath writes the ROM it reads;
            // qr_resume forces the slot-9 load. Clear the stale com.dsemu.drastic
            // launch_app/launch_intent readyToRun set early (else RootWindowContainer
            // would relaunch the stock DraStic APK on the SF path).
            std::string qrRom = getQrRomPath();
            if (!qrRom.empty()) setDrasticNanoRomPath(qrRom);
            property_set("persist.gammaos.nano.qr_prepared", "0");
            property_set("sys.gammaos.drastic_nano.qr_resume", "1");
            if (dnBackend == "sf") {
                // Explicit SF: launch the DrasticSf host activity, exactly like the
                // normal SF launch (the mDrasticNanoPending block ~3352). It fires
                // start_sf and the overlay stays up as the SF panel keeper; the exit
                // flow drops DRM master and the DRM-boot-path waits for app_launched.
                android::base::SetProperty("sys.gammaos.nano.launch_app", "com.gammaos.drasticsf");
                android::base::SetProperty("sys.gammaos.nano.launched_pkg", "com.gammaos.drasticsf");
                android::base::SetProperty("sys.gammaos.nano.launch_intent", "");
                setLaunchRomPath("");
                android::base::SetProperty("sys.gammaos.nano.launch_core", "");
                property_set("sys.gammaos.nano.return_recent", "1");
                property_set("service.bootanim.nano_retroarch", "1");
                property_set("sys.gammaos.nano.drop_input", "1");
                mExitRequested = true;
            } else {
                // auto: the normal DRM handshake. The start trigger stops nano and
                // starts the binary, which grabs DRM master (RG DS) or falls back to
                // own-layer SF (pure-SF panel). Mirrors the normal launch's else
                // branch (~3415). Exiting releases DRM master for the binary / SF.
                property_set("sys.gammaos.nano.drop_input", "1");
                property_set("sys.gammaos.drastic_nano.start", "1");
                _exit(0);
            }
        } else {
        DrasticRunner* drastic = DrasticRunner::getInstance();
        if (drastic && drastic->isInitialized()) {
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

            // GammaOS: QR preview layout.
            //   0 (default) = one DS screen per display on dual-display
            //                 devices, stacked top+bot on single-display.
            //   1           = both DS screens side-by-side (top on left
            //                 half, bot on right half) on every display.
            // Read once at QR entry; changes require a nano restart.
            bool qrSideBySide = false;
            {
                char sb[PROPERTY_VALUE_MAX] = {};
                property_get("persist.gammaos.nano.drastic_qr_layout",
                             sb, "0");
                qrSideBySide = (sb[0] == '1');
                if (qrSideBySide) {
                    ALOGI("drastic QR: side-by-side layout enabled");
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
            int64_t handoffFiredAtMs = 0;
            // Max time to keep rendering preview after handoff fires
            // while waiting for drastic's DraSticEmuActivity to draw
            // its first frame (sys.gammaos.nano.app_drawn=1). On a 1GB
            // device with cold ART/zygote, drastic can take 30-50s to
            // get from intent receipt to first game frame on QR cold
            // boot (FUSE mount race adds ~20s of system_server defers).
            const int64_t kPostHandoffTimeoutMs = 90000;
            // Timestamp when the visual fade-out (desaturated -> full
            // color, overlay removal, text fade) begins. 0 = not yet
            // triggered. Set when DraSticEmuActivity reports drawn so
            // the visual handoff happens right as drastic is ready to
            // appear on screen.
            int64_t fadeOutStartMs = 0;
            const int64_t kFadeOutDurationMs = 500;
            // Saturation/gradient values captured at the moment the
            // fade-out starts; used as the lerp origin so the fade
            // doesn't visually jump if the preview was at e.g. 0.35
            // saturation when the fade begins.
            float fadeStartSat = -1.0f;
            float fadeStartGrad = -1.0f;
            // Storage-readiness gate for the QR handoff. We hold the
            // handoff until the QR ROM's underlying volume is mounted
            // (vold defers external SD scan ~3-5s after boot start),
            // otherwise drastic resolves the content URI to "not found"
            // and falls back to its main menu.
            bool handoffStorageWaitLogged = false;
            int64_t handoffWaitStartMs = 0;
            const int64_t handoffWaitTimeoutMs = 30000; // 30s ceiling

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
                const char* msg = trDyn("Quick Resuming...");
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

                // Post-handoff exit gate. Once handoff has fired,
                // continue rendering the desaturated/text-visible
                // preview until drastic's DraSticEmuActivity reports
                // its first drawn frame (ActivityMetricsLogger sets
                // app_drawn=1). At THAT point, start a fast 500ms
                // fade-out (desaturated -> full color, text/overlay
                // disappearing) and exit at the end of the fade.
                // This aligns the visual transition with drastic
                // actually being ready to appear.
                if (handoffFired) {
                    if (fadeOutStartMs == 0) {
                        char drawn[PROPERTY_VALUE_MAX] = {};
                        property_get("sys.gammaos.nano.app_drawn",
                                     drawn, "0");
                        int64_t postHandoff = elapsedRealtime()
                                              - handoffFiredAtMs;
                        if (!strcmp(drawn, "1")) {
                            fadeOutStartMs = elapsedRealtime();
                            ALOGI("drastic QR: app_drawn=1 after "
                                  "%lldms post-handoff, starting "
                                  "%lldms fade-out",
                                  (long long)postHandoff,
                                  (long long)kFadeOutDurationMs);
                        } else if (postHandoff > kPostHandoffTimeoutMs) {
                            // Safety: drastic took too long to draw.
                            // Start the fade anyway so the user
                            // doesn't get stuck on the preview.
                            fadeOutStartMs = elapsedRealtime();
                            ALOGW("drastic QR: %lldms post-handoff "
                                  "without app_drawn -- starting "
                                  "fade-out anyway",
                                  (long long)postHandoff);
                        }
                    }
                    if (fadeOutStartMs > 0 && !drasticNanoActive) {
                        if (fadeStartSat < 0.0f) {
                            fadeStartSat = saturation;
                            fadeStartGrad = gradient;
                        }
                        int64_t elapsed = elapsedRealtime()
                                          - fadeOutStartMs;
                        float t = fminf(
                                (float)elapsed
                                        / (float)kFadeOutDurationMs,
                                1.0f);
                        t = 1.0f - (1.0f - t) * (1.0f - t);
                        // Lerp from where the preview was when fade
                        // started, to full color (sat=1.0, grad=0.0).
                        saturation = fadeStartSat
                                + t * (1.0f - fadeStartSat);
                        gradient = fadeStartGrad * (1.0f - t);
                        if (t >= 1.0f) {
                            ALOGI("drastic QR: fade-out complete, "
                                  "exiting preview");
                            property_set(
                                "service.bootanim.nano_retroarch",
                                "1");
                            mExitRequested = true;
                            break;
                        }
                    }
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
                // GammaOS: Viewport dims. With sDrmGlRotation we render
                // into the raw AHB buffer (panel-native size); otherwise
                // we use the logical mWidth/mHeight space that the shader
                // rotates into panel coords. Same axis convention is
                // needed for the side-by-side split below.
                auto viewportDims = [&](const AhbRenderTarget& tgt,
                                        int* outW, int* outH) {
                    // Only the DRM zero-copy path has a valid AHB target; force-SF
                    // self-rotate (sDrmGlRotation without an AHB) must use mWidth/mHeight.
                    if (sDrmGlRotation && sDrmZeroCopy) {
                        *outW = tgt.w; *outH = tgt.h;
                    } else {
                        *outW = mWidth; *outH = mHeight;
                    }
                };

                // Draw top+bot side-by-side into the currently-bound FBO
                // by splitting the viewport's W axis. Each half is an
                // independent glViewport + renderTopScreen / Bottom call;
                // DrasticRunner::drawDsQuad fills its viewport with a
                // full NDC quad, so halving W gives us left=top, right=bot.
                auto drawSideBySide = [&](int vpW, int vpH) {
                    const int halfW = vpW / 2;
                    glViewport(0, 0, halfW, vpH);
                    drastic->renderTopScreen(saturation, gradient);
                    glViewport(halfW, 0, vpW - halfW, vpH);
                    drastic->renderBottomScreen(saturation, gradient);
                };

                if (hasDualDisplay) {
                    // Pass 1: secondary display.
                    // Gated on secondaryThisIter so we do half the work
                    // on dual-display setups; secondary then runs at
                    // 30 fps which is fine for the bottom DS screen.
                    if (secondaryThisIter) {
                        glBindFramebuffer(GL_FRAMEBUFFER, secTgt.glFbo);
                        int vpW, vpH;
                        viewportDims(secTgt, &vpW, &vpH);
                        if (qrSideBySide) {
                            drawSideBySide(vpW, vpH);
                        } else {
                            glViewport(0, 0, vpW, vpH);
                            drastic->renderBottomScreen(saturation, gradient);
                        }
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

                    // Pass 2: primary display.
                    glBindFramebuffer(GL_FRAMEBUFFER, primTgt.glFbo);
                    int pvpW, pvpH;
                    viewportDims(primTgt, &pvpW, &pvpH);
                    if (qrSideBySide) {
                        drawSideBySide(pvpW, pvpH);
                    } else {
                        glViewport(0, 0, pvpW, pvpH);
                        drastic->renderTopScreen(saturation, gradient);
                    }
                } else {
                    // Single display.
                    glBindFramebuffer(GL_FRAMEBUFFER, primTgt.glFbo);
                    int vpW, vpH;
                    viewportDims(primTgt, &vpW, &vpH);
                    if (qrSideBySide) {
                        drawSideBySide(vpW, vpH);
                    } else {
                        glViewport(0, 0, vpW, vpH);
                        drastic->renderBothScreens(saturation, gradient);
                    }
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
                    // GammaOS Nano: the fade-out (desaturated preview
                    // -> full color, overlay/text removal) is deferred
                    // until drastic's DraSticEmuActivity reports drawn
                    // (sys.gammaos.nano.app_drawn=1). At THAT point we
                    // do a fast 500ms fade and exit, so the visual
                    // handoff happens right as drastic appears on
                    // screen. Pre-handoff and during drastic load, we
                    // hold at the desaturated/text-visible state so
                    // the "Quick Resuming" overlay remains visible.
                    //
                    // The fade timing logic is below (after handoff
                    // fires) so we can reference fadeOutStartMs.
                    //
                    // Smoke-test mode (qr_core != "drastic") never
                    // fires handoff -- the smoke test runs the DS
                    // indefinitely for interactive debugging.
                    //
                    if (drasticQrHandoff && !handoffFired) {
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
                            // Tell drastic-nano this launch is a Quick Resume so
                            // it force-loads slot 9 (over boot_fresh/hardcore) and
                            // drops RA hardcore for the resumed session. Volatile;
                            // drastic-nano clears it on exit. Set before .start.
                            property_set(
                                "sys.gammaos.drastic_nano.qr_resume", "1");
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
                        // Clear app_drawn before kicking the launch so
                        // stale state from a previous boot doesn't make
                        // us exit immediately. ActivityMetricsLogger sets
                        // it=1 when DraSticEmuActivity reports drawn.
                        property_set(
                                "sys.gammaos.nano.app_drawn", "0");
                        property_set(
                                "sys.gammaos.nano.handoff_fired", "1");
                        property_set(
                                "sys.gammaos.nano.pending_exit", "0");
                        property_set(
                                "sys.gammaos.nano.do_launch", "1");
                        // Hold off setting service.bootanim.nano_retroarch
                        // until drastic's game window is actually drawn.
                        // This prop is the legacy bootanim-exit trigger;
                        // setting it kicks init's bootanim teardown which
                        // forces NanoMenu out of its render loop before
                        // drastic is ready to display.
                        //
                        // GammaOS Nano: KEEP RENDERING the QR preview
                        // instead of exiting at handoff time. The user
                        // sees a continuous DS emulation feed all the
                        // way through drastic's startup. We watch for
                        // sys.gammaos.nano.app_drawn (set by
                        // ActivityMetricsLogger when DraSticEmuActivity
                        // reports drawn) and exit only then, so the
                        // transition from preview to game is seamless
                        // (no Loading screen, no blank screen).
                        handoffFiredAtMs = elapsedRealtime();
                        ALOGI("drastic QR: handoff fired, continuing "
                              "preview until app_drawn=1 or %lldms",
                              (long long)kPostHandoffTimeoutMs);
                        // DO NOT set mExitRequested=true or break here.
                        // Fall through to the next loop iteration and
                        // keep rendering preview frames.
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
                  "not initialized -- clearing qr_prepared and falling "
                  "through to loading screen");
            // DrasticRunner failed to init (likely persist prop race).
            // Clear qr_prepared so we don't loop, disable the fast-path
            // flag, and fall through to the normal boot path which will
            // show a loading screen until the system is ready. The
            // basic shaders (mShaderProgram, mTextProgram) were compiled
            // in readyToRun regardless of sDrasticQrFastPath.
            property_set("persist.gammaos.nano.qr_prepared", "0");
            sDrasticQrFastPath = false;
        }
        }   // end else (non-SF: in-process DRM-direct DrasticRunner preview)
    }

    // Quick Resume: auto-launch into saved game on boot if prepared.
    // Never in the resident overlay instance: it runs this same
    // threadLoop mid-session (started at the first app-launch handoff,
    // when overlayLaunchGame has just primed qr_prepared=1 with
    // handoff_fired still 0), and without the guard it could dlopen a
    // libretro core inside the SF overlay process and fire a spurious
    // handoff over the running game. Mirrors the drastic QR guard in
    // readyToRun.
    if (mQuickResumeEnabled && !mOverlayMode) {
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

                        std::string zipFile;
                        DIR* d = opendir((cacheDir + "/rom").c_str());
                        if (d) {
                            struct dirent* e;
                            while ((e = readdir(d)) != nullptr) {
                                std::string name(e->d_name);
                                if (name == "." || name == "..") continue;
                                if (name.find(".srm") != std::string::npos ||
                                    name.find(".state") != std::string::npos ||
                                    name.find(".sav") != std::string::npos ||
                                    name.find(".brm") != std::string::npos ||
                                    name.find(".png") != std::string::npos)
                                    continue;
                                bool isZip = (name.size() > 4 &&
                                    (name.compare(name.size()-4, 4, ".zip") == 0 ||
                                     name.compare(name.size()-4, 4, ".ZIP") == 0));
                                if (isZip) {
                                    zipFile = cacheDir + "/rom/" + name;
                                } else {
                                    romFile = cacheDir + "/rom/" + name;
                                }
                            }
                            closedir(d);
                        }
                        if (romFile.empty() && !zipFile.empty())
                            romFile = zipFile;

                        if (!romFile.empty() && !expectedBase.empty()) {
                            std::string cachedBase = romFile;
                            size_t sl = cachedBase.rfind('/');
                            if (sl != std::string::npos)
                                cachedBase = cachedBase.substr(sl + 1);
                            if (cachedBase != expectedBase) {
                                // nano_cache.sh extracts zips during
                                // populate, so the extracted filename
                                // differs from the zip. Verify the zip
                                // is in the cache to confirm validity.
                                bool zipOk = false;
                                bool expectZip = (expectedBase.size() > 4 &&
                                    (expectedBase.compare(expectedBase.size()-4, 4, ".zip") == 0 ||
                                     expectedBase.compare(expectedBase.size()-4, 4, ".ZIP") == 0));
                                if (expectZip && !zipFile.empty()) {
                                    std::string zipBase = zipFile;
                                    sl = zipBase.rfind('/');
                                    if (sl != std::string::npos)
                                        zipBase = zipBase.substr(sl + 1);
                                    if (zipBase == expectedBase) {
                                        zipOk = true;
                                        ALOGI("Quick Resume: zip ROM "
                                              "verified (zip=%s "
                                              "extracted=%s)",
                                              expectedBase.c_str(),
                                              cachedBase.c_str());
                                    }
                                }
                                if (!zipOk) {
                                    ALOGW("Quick Resume: cached ROM "
                                          "mismatch (have=%s want=%s)"
                                          ", skipping native launch "
                                          "until cache refreshes",
                                          cachedBase.c_str(),
                                          expectedBase.c_str());
                                    romFile.clear();
                                }
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
                        bool coreUp = runner.init(coreFile, romFile,
                                                  statePath, sramPath);
                        // Run the very first core frame under the sigsetjmp
                        // crash guard. A core that faults on frame 1 (missing
                        // BIOS, bad dynarec mapping) would otherwise kill the
                        // whole nano process here, leaving the panel black
                        // until reboot; caught, it degrades to the rendered
                        // fallback splash + RetroArch APK launch below.
                        if (coreUp && !runner.trySafeFirstFrame()) {
                            ALOGW("Quick Resume: core crashed on first frame "
                                  "-- falling back to RetroArch APK");
                            // abandon(), not shutdown(): re-entering the
                            // faulted core to unload it can crash again,
                            // this time unguarded. Leak it deliberately.
                            runner.abandon();
                            coreUp = false;
                        }
                        if (coreUp) {
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
                            // Once the ROM storage is ready, fade the scrim out then hand
                            // off. Held false for the whole "Quick Resuming..." wait so the
                            // scrim stays up until the real handoff (not during boot).
                            bool handoffFade = false;
                            // Extract ROM display name (strip path + extension)
                            std::string romName = romFile;
                            size_t sl = romName.rfind('/');
                            if (sl != std::string::npos) romName = romName.substr(sl + 1);
                            size_t dot = romName.rfind('.');
                            if (dot != std::string::npos) romName = romName.substr(0, dot);
                            bool bootComplete = false;
                            // Storage-gate wait start (0 = ramp not done
                            // yet). Caps the isQrRomStorageReady() wait so
                            // a framework that never publishes the storage
                            // signal cannot hold the handoff forever.
                            int64_t handoffStorageWaitMs = 0;
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
                                        ALOGI("Quick Resume: user ready, "
                                              "waiting for ROM storage before handoff");
                                    }
                                    // No creep: hold the scrim (saturation 0.15,
                                    // gradient 1.0) for the whole "Quick Resuming..."
                                    // wait; it only fades once we actually hand off.
                                }

                                if (bootComplete && !handoffPaused) {
                                    // Hold the scrim up until the ROM's backing storage is
                                    // actually mounted, then fade it out and hand off. The
                                    // fade plays only at the real handoff, not during the
                                    // (possibly long) storage wait, so the "Quick Resuming..."
                                    // scrim stays up the whole time (matches the drastic path).
                                    if (!handoffFade) {
                                        if (handoffStorageWaitMs == 0)
                                            handoffStorageWaitMs = elapsedRealtime();
                                        bool handoffStorageOk = isQrRomStorageReady();
                                        // Bounded: after 30s of storage never
                                        // reporting ready, fade + hand off anyway. The
                                        // relaunch monitor in SystemServer holds
                                        // the actual app start on its own
                                        // storage gate, so this only stops the
                                        // preview from waiting forever when
                                        // that signal is broken.
                                        if (!handoffStorageOk &&
                                            elapsedRealtime() - handoffStorageWaitMs
                                                    > 30000) {
                                            ALOGW("Quick Resume: storage not "
                                                  "ready after 30s -- handing "
                                                  "off anyway");
                                            handoffStorageOk = true;
                                        }
                                        if (handoffStorageOk) handoffFade = true;
                                    }
                                    if (handoffFade) {
                                        // Fade the scrim out (~0.8s, 5%/frame at 60fps):
                                        // desaturate up to full colour and lift the gradient.
                                        saturation = fminf(saturation + 0.05f, 1.0f);
                                        gradient = fmaxf(gradient - 0.05f, 0.0f);
                                    }
                                    if (handoffFade && gradient <= 0.0f) {
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
                                if (sDrmGlRotation && sDrmZeroCopy) {
                                    glViewport(0, 0, sAhbTarget.w, sAhbTarget.h);
                                } else {
                                    glViewport(0, 0, mWidth, mHeight);
                                }
                                glEnable(GL_BLEND);
                                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                                const char* msg = trDyn("Quick Resuming...");
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

                                // Snapshot hook (sys.gammaos.nano.shot), like
                                // the drastic QR loop: the only way to capture
                                // this preview on the DRM-direct scanout path.
                                maybeNanoScreenshot();
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

                        // Never exit frame-less. This branch used to set the
                        // launch props and quit immediately: the panel stayed
                        // on drmEarlySplash's kernel-zeroed black buffer and
                        // RetroArch was cold-started through the init trigger
                        // chain with NO storage gate, before FUSE served the
                        // app's storage view -- it then resolved its paths to
                        // garbage and hung on that black screen forever.
                        // Render the same "Quick Resuming..." splash as the
                        // native preview and hold the handoff until boot +
                        // storage are actually ready (the same gates the
                        // native handoff uses), with a frame cap so a storage
                        // failure still degrades to launching.
                        {
                            std::string gameName = android::base::GetProperty(
                                    "persist.gammaos.nano.qr_game_name", "");
                            if (gameName.empty()) {
                                gameName = qrRom;
                                size_t sl = gameName.rfind('/');
                                if (sl != std::string::npos)
                                    gameName = gameName.substr(sl + 1);
                                size_t dot = gameName.rfind('.');
                                if (dot != std::string::npos)
                                    gameName.erase(dot);
                            }
                            float textScale = fminf((float)mWidth / 1080.0f,
                                                    (float)mHeight / 720.0f);
                            if (textScale < 0.5f) textScale = 0.5f;
                            float loadScale = 2.5f * textScale;
                            for (int frame = 0; frame < 1800; frame++) {
                                mRenderHeartbeat.fetch_add(
                                        1, std::memory_order_relaxed);
                                {
                                    char bc[PROPERTY_VALUE_MAX] = {};
                                    property_get("sys.boot_completed", bc, "0");
                                    if (bc[0] == '1' && isQrRomStorageReady())
                                        break;
                                }
                                // Drain input; the splash is inert.
                                struct input_event iev;
                                for (int fd : mInputFds) {
                                    if (fd < 0) continue;
                                    while (read(fd, &iev, sizeof(iev)) ==
                                           (ssize_t)sizeof(iev)) { }
                                }
                                drmFrameBegin();
                                glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                                glClear(GL_COLOR_BUFFER_BIT);
                                if (sDrmGlRotation && sDrmZeroCopy) {
                                    glViewport(0, 0, sAhbTarget.w, sAhbTarget.h);
                                } else {
                                    glViewport(0, 0, mWidth, mHeight);
                                }
                                glEnable(GL_BLEND);
                                glBlendFunc(GL_SRC_ALPHA,
                                            GL_ONE_MINUS_SRC_ALPHA);
                                const char* msg = trDyn("Quick Resuming...");
                                float msgW = measureText(msg, loadScale);
                                float msgX = (mWidth - msgW) / 2.0f;
                                float msgY = mHeight * 0.75f;
                                float pulse = 0.7f + 0.3f * sinf(
                                        (float)elapsedRealtime() * 0.004f);
                                drawText(msg, msgX, msgY, loadScale,
                                         1.0f, 1.0f, 1.0f, pulse);
                                if (!gameName.empty()) {
                                    float nameScale = 1.5f * textScale;
                                    float nameW = measureText(
                                            gameName.c_str(), nameScale);
                                    float nameX = (mWidth - nameW) / 2.0f;
                                    float nameY = msgY
                                            + FONT_CHAR_H * loadScale
                                            + 12.0f * textScale;
                                    drawText(gameName.c_str(), nameX, nameY,
                                             nameScale, 0.7f, 0.7f, 0.8f,
                                             pulse * 0.8f);
                                }
                                glDisable(GL_BLEND);
                                maybeNanoScreenshot();
                                drmFrameEnd(mDisplay, mSurface);
                                usleep(16666);
                            }
                        }

                        // Fire the DIRECT handoff exactly like the native
                        // path: handoff_fired stops a respawned nano from
                        // re-running QR, do_launch skips the several-second
                        // init action-queue latency, and the legacy
                        // nano_retroarch prop keeps its init side effects
                        // (bootanim exit).
                        property_set("sys.gammaos.nano.handoff_fired", "1");
                        property_set("sys.gammaos.nano.pending_exit", "0");
                        property_set("sys.gammaos.nano.drop_input", "1");
                        property_set("sys.gammaos.nano.do_launch", "1");
                        property_set("service.bootanim.nano_retroarch", "1");
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
        // Render-thread liveness for the watchdog. Bump the heartbeat once per loop
        // iteration (not only inside render()). EVERY intentional render-thread park
        // below - the overlay screen-off idle poll, the hidden-overlay prop-wait, the
        // DRM occlusion guard, frame pacing, idle-fps - exits via `continue` back to
        // here, so this single bump proves the thread is parked-but-alive and the
        // watchdog never aborts it. A TRUE hang (stuck in render()/pollInput()) never
        // returns to the loop top, so the heartbeat still freezes and the watchdog
        // still fires. The one indefinite block that does NOT loop back is
        // enterDrmSleep's nested poll, which stays guarded by mInDrmSleep.
        // (This is the structural fix for the overlay audio dying ~8s into sleep:
        // the screen-off park at ~3455 used to skip render() and freeze the heartbeat.)
        mRenderHeartbeat.fetch_add(1, std::memory_order_relaxed);
        if (!mWatchdogStarted) { mWatchdogStarted = true; startRenderWatchdog(); }

        // GammaOS hardware-rotate (force-SF): SurfaceFlinger is pinned at ROTATION_0 for nano's
        // layer (DisplayRotation forces the angle from sys.gammaos.rotate.state whenever nano is
        // the top surface), so nano must render the FULL physical rotation itself. Apply it here,
        // once per loop iteration, so EVERY nano render path is covered: the overlay wallpaper and
        // the scrim-over-app (overlayUpdateSurfaceSize below re-applies the same value with an SF
        // compensation that is a no-op while SF is pinned), AND the pre-first-app interactive home,
        // which renders through the main path and otherwise never self-rotates - it used to rely on
        // SurfaceFlinger rotating its layer, which the pin now prevents (the "fresh home stays
        // upright / sideways after rotate" gap). Touch un-rotation (mOverlayRotation) is kept in
        // lockstep. No-op on DRM-direct devices: the rotate feature is disabled there and
        // nanoSetOverlayRenderRotation() early-returns when sDrmActive.
        //
        // CRUCIAL: only self-rotate while nano is the TOP surface, i.e. exactly when DisplayRotation
        // pins SurfaceFlinger at 0 (nanoOnTop = no foreground app OR our overlay is raised). When a
        // real app is foreground with no overlay, DisplayRotation does NOT pin SF - it lets SF rotate
        // the display for the app - so nano must NOT also self-rotate or it DOUBLE-rotates (SF 90 +
        // nano 90 = 180). That is the "touch-launch: game runs but nano is stuck on top rotated an
        // extra 90" bug: while the home instance fades out to hand off, the app has already flipped
        // SF, so nano's fade must render un-rotated (SF supplies the rotation) to stay single.
        if (property_get_bool("persist.gammaos.rotate.enabled", false)) {
            int physical = 0;
            if (property_get_int32("sys.gammaos.rotate.state", 0) == 1) {
                const int deg = property_get_int32("persist.gammaos.rotate.degrees", 90);
                physical = (deg == 270) ? 3 : (deg == 180) ? 2 : 1;
            }
            const bool nanoOnTop =
                    !property_get_bool("sys.gammaos.nano.app_launched", false)
                    || property_get_bool("sys.gammaos.nano.show_overlay", false);
            const int selfRot = nanoOnTop ? physical : 0;
            mOverlayRotation = selfRot;
            nanoSetOverlayRenderRotation(selfRot);
        }

        // Overlay XMB: resident-hidden power-hold overlay. One-time blur/hide
        // setup, then each tick poll sys.gammaos.nano.show_overlay to raise or
        // dismiss. While hidden, the layer is invisible and input is NOT
        // grabbed (the running app owns it), so we render nothing and just
        // watch the trigger cheaply.
        if (mOverlayMode) {
            if (!mOverlayInited) overlayInitLayer();
            overlayPoll();
            // A power-hold raises the overlay via overlayPoll() above (mOverlayShown -> true) BEFORE the
            // !mOverlayShown block below, so the CC teardown inside it is skipped. If the CC had slept the
            // bottom panel, restore its backlight here so the raised overlay is not left on a dark panel;
            // idempotent (ccRestoreBacklightIfSlept early-returns once settled), and re-arm the idle timer.
            if (mOverlayShown) { ccRestoreBacklightIfSlept(); mCcActiveSeeded = false; }
            if (!mOverlayShown) {
                // Parked behind a foreground app: the overlay renders nothing here, so
                // release its mlockall pin (~122MB) and let those idle pages swap to zram
                // for the running game (this is the single biggest RAM lever for in-game
                // stutter). Re-locked in overlayShow() BEFORE the overlay draws again, so
                // the visible home/OSK keep the no-glyph-fault guarantee. Latched so it
                // runs once per park transition, not on every 250ms wait wakeup. Does NOT
                // touch the wake path: overlayPoll() above still runs each tick and the
                // property-wait below still fires the instant power-hold flips the trigger.
                if (mOverlayPagesLocked) {
                    munlockall();
                    mOverlayPagesLocked = false;
                    ALOGW("NanoMenu: overlay parked -- munlockall (idle pages reclaimable)");
                    // Hand GPU memory back to the foreground game too (GPU pages are
                    // not swappable, so munlockall alone does not reclaim them). The
                    // GL context is current on this render thread. Both are rebuilt
                    // lazily/at next raise, spread by their own demand paths (never a
                    // synchronous stall here): the 21MB wave keyframe VBO (rebuilt on
                    // the next live home frame) and the baked overlay backdrop (re-
                    // captured by overlayShow on every raise).
                    ps3bg::freeWaveSeq();
                    if (mOverlayBgTex) { glDeleteTextures(1, &mOverlayBgTex); mOverlayBgTex = 0; }
                    freeGlassScratch();
                }
                // KEY_ALL_APPLICATIONS: toggle the Control Center visible over ANY running app. Drained here
                // (before the controlCenterActive() gate) so it works from every park sub-case, including a
                // dual-stack app where the CC is otherwise inactive - toggling it on flips controlCenterActive()
                // true on this same iteration so the CC comes up at once. On a real down-edge, flip the override
                // and set drop_input to match who now owns the bottom digitizer: the CC (grab) when summoned, or
                // the running app when dismissed (a dual-stack app or a grid-launched bottom app both want touch).
                if (ccPollAllAppsKey()) {
                    mCcForceVisible = !mCcForceVisible;
                    if (mCcForceVisible) {
                        property_set("sys.gammaos.nano.drop_input", "1");   // CC grabs the bottom digitizer
                        mCcActiveSeeded = false;   // re-seed the idle timer so the summoned CC does not auto-sleep at once
                    } else {
                        char la[PROPERTY_VALUE_MAX] = {};
                        property_get("sys.gammaos.nano.launch_app", la, "");
                        bool appOwnsBottom = !mCcBottomApp.empty() || (la[0] && dualstackHas(la));
                        property_set("sys.gammaos.nano.drop_input", appOwnsBottom ? "0" : "1");
                        // If the force-visible CC had dimmed/slept the bottom panel, restore its backlight so
                        // the app it hands the panel back to is not left dark. Idempotent when already awake.
                        if (appOwnsBottom) ccRestoreBacklightIfSlept();
                    }
                }
                // Bottom-screen Control Center: while a single-screen (non-dual-stack) app is
                // fullscreen on top, render the live dashboard on the BOTTOM panel instead of fully
                // parking. The munlockall above already handed the game its RAM; the dashboard's small
                // working set (glyph atlas, shaders, the secondary surface) faults back in on demand.
                // Rate-capped so the game keeps the SoC, and a power-hold still raises the overlay
                // promptly (the next iteration sees show_overlay=1 and falls through to the full path).
                if (controlCenterActive()) {
                    int64_t ccT0 = systemTime(SYSTEM_TIME_MONOTONIC);
                    int64_t ccNowMs = ccT0 / 1000000;   // monotonic ms (nowMs() is file-static to NanoControlCenter.cpp)
                    // Real elapsed time since the previous CC park frame. The main loop's mFrameDt
                    // update lives AFTER this branch's continue, so on the CC path mFrameDt would
                    // otherwise be stale; feed the measured delta in so ccUpdateSleep's 0.6s dim/wake
                    // ramp is paced by wall-clock (at 20fps a stale ~16ms dt would run the ramp ~3x slow).
                    {
                        static int64_t sCcLastNs = 0;
                        if (sCcLastNs > 0) {
                            float dt = (float)(ccT0 - sCcLastNs) / 1e9f;
                            if (dt < 0.001f) dt = 0.001f;
                            if (dt > 0.1f)   dt = 0.1f;
                            mFrameDt = dt;
                        }
                        sCcLastNs = ccT0;
                    }
                    // Device sleep/wake: the framework blanks both panels on power-off (sys.screen.state=off)
                    // without nano ever seeing a power-press. Pause the CC (skip render + polls) while the
                    // screen is off, and WAKE the CC on the transition back to on - ramp the bottom backlight
                    // back up and reset the idle timer - so the bottom panel returns together with the top
                    // instead of staying dark until the user touches it.
                    {
                        char ss[PROPERTY_VALUE_MAX] = {};
                        property_get("sys.screen.state", ss, "on");
                        bool scrOff = (strcmp(ss, "off") == 0);
                        static bool sCcScrWasOff = false;
                        if (scrOff) {
                            sCcScrWasOff = true;
                            // Release the display-0 rotation pin while the panel is blanked (the CC is not
                            // rendering); it re-arms on the next active frame after wake.
                            if (property_get_int32("sys.gammaos.nano.cc.active", 0) != 0)
                                property_set("sys.gammaos.nano.cc.active", "0");
                            // Drop any in-flight focus ring so it is not left composited over the panel the
                            // framework is blanking (nowMs() does not advance across suspend, so the pulse
                            // cannot self-expire). mOverlayShown is false here, so this does the real hide.
                            mCcRingDisp = -1;
                            if (mTopRingShown) hideTopFocusRing();
                            mRenderHeartbeat.fetch_add(1, std::memory_order_relaxed);
                            int64_t restUs = 100000 - (systemTime(SYSTEM_TIME_MONOTONIC) - ccT0) / 1000;
                            if (restUs > 500) usleep((useconds_t)restUs);
                            continue;
                        }
                        if (sCcScrWasOff) {
                            sCcScrWasOff = false;
                            // Wake: only arm the ramp if the CC itself dimmed the panel; otherwise
                            // ccUpdateSleep would write a stale mCcSleepFromBri and fight the framework's
                            // own wake-brightness restore. Always reset the idle window.
                            if (mCcSleeping || mCcSleepDir != 0 || mCcSleepRamp < 1.0f) {
                                mCcSleeping = false; mCcSleepDir = +1;
                            }
                            mCcLastTouchMs = ccNowMs;
                        }
                    }
                    // The exit watcher (off-thread) flagged that the bottom app the user launched has closed:
                    // clear it and re-seed so the CC fades back in on the bottom panel.
                    if (mCcBottomAppGone.load(std::memory_order_acquire)) {
                        ccEndBottomApp(false);       // app already exited: clear state + restore drop_input (no force-stop)
                        mCcActiveSeeded = false;     // re-seed the idle timer + fade the CC back in
                    }
                    // A bottom-screen app launched from the app grid owns the bottom panel: hide the CC so it
                    // does not occlude the app, and just watch for the app to exit (no CC touch/render). Drain
                    // and discard the bottom digitizer so a session-long evdev backlog cannot replay a stale
                    // tap when the CC returns. Loosely paced (~10Hz) so the game keeps the SoC.
                    if (!mCcBottomApp.empty()) {
                        // Always keep watching for the bottom app's exit, whether the CC is hidden behind it
                        // or (force-visible) shown over it.
                        ccPollBottomAppExit();
                        // KEY_ALL_APPLICATIONS summoned the CC over the running bottom app: render it (the
                        // "Close App" tile is now live) by falling through to the normal CC render path below,
                        // instead of hiding. drop_input was set to 1 at the toggle so CC touches do not reach
                        // the occluded app; the close-app tile force-stops it and returns to the plain CC.
                        if (!mCcForceVisible) {
                        // Tap-to-switch controller focus between the two running apps: a touch-down on the
                        // bottom app hands it the gamepad, a touch-down on the top screen hands it back to
                        // the top app. (Draining both digitizers here also keeps their backlogs clear so no
                        // stale event replays a CC action on return.)
                        if (ccDrainBottomTouch())
                            ccSetFocusDisplay(property_get_int32("persist.gammaos.nano.cc.bottomdisplay", 0));
                        if (ccPollTopTapDown())
                            ccSetFocusDisplay(property_get_int32("persist.gammaos.nano.cc.topdisplay", 2));
                        // The bottom app owns the panel here (CC hidden): keep input flowing to both apps.
                        // Re-assert only on drift so a stray drop_input=1 cannot strand the bottom app's touch.
                        if (property_get_int32("sys.gammaos.nano.drop_input", 0) != 0)
                            property_set("sys.gammaos.nano.drop_input", "0");
                        // The bottom app owns display 0 (the CC is hidden): release the rotation pin so the app
                        // controls its own orientation.
                        if (property_get_int32("sys.gammaos.nano.cc.active", 0) != 0)
                            property_set("sys.gammaos.nano.cc.active", "0");
                        int64_t budgetUs;
                        if (ccRingActive()) {
                            // Pulse the focus ring over the app that just took the controller.
                            if (mCcRingDisp == property_get_int32("persist.gammaos.nano.cc.topdisplay", 2)) {
                                hideControlCenterLayer();   // the bottom app owns the bottom panel
                                renderTopFocusRing();       // ring over the top app (translucent overlay)
                            } else {
                                if (mTopRingShown) hideTopFocusRing();   // retargeted top->bottom: drop the top ring
                                renderBottomFocusRing();    // ring over the bottom app (translucent secondary)
                            }
                            budgetUs = 33333;               // ~30fps for a smooth pulse
                        } else {
                            if (mTopRingShown) hideTopFocusRing();
                            hideControlCenterLayer();        // pulse over: both apps own their panels again
                            budgetUs = 100000;               // ~10Hz at rest
                        }
                        mRenderHeartbeat.fetch_add(1, std::memory_order_relaxed);
                        int64_t spentUs = (systemTime(SYSTEM_TIME_MONOTONIC) - ccT0) / 1000;
                        int64_t restUs = budgetUs - spentUs;
                        if (restUs > 500) usleep((useconds_t)restUs);
                        continue;
                        }   // end !mCcForceVisible: CC hidden behind the bottom app
                        // force-visible: fall through to render the CC over the bottom app.
                    }
                    // Control Center up (no bottom app, or force-visible over one): the controller belongs to
                    // the TOP panel
                    // (the app the CC accompanies). The CC is touch-only and must never take focus, so
                    // pin the top display; the framework returns the gamepad there (this is what fixes
                    // focus being stranded on the bottom after a bottom app exits).
                    ccSetFocusDisplay(property_get_int32("persist.gammaos.nano.cc.topdisplay", 2));
                    // Input isolation, reconciled every iteration (the framework clears drop_input to 0 on some
                    // transitions - app focus changes, which the focus pin above can provoke, and home/overlay
                    // handoffs). drop_input is a GLOBAL drop of keys+motion, so it must be raised ONLY when the CC
                    // actually occludes an app on the bottom panel it reads touch from:
                    //  - Force-visible CC OVER a grid-launched bottom app (mCcBottomApp set, reached via the
                    //    fall-through above): the bottom digitizer would reach that bottom app on display 0, so
                    //    drop_input=1 blocks the CC's taps from leaking to it. The gamepad is not needed by the
                    //    hidden bottom app while the CC is up, and the top app is paused behind the force-visible CC.
                    //  - Force-visible CC OVER a dual-stack app (mCcForceVisible + launch_app is dual-stack): that
                    //    app spans BOTH panels, so it has a window on the bottom (display 0) too - the bottom
                    //    digitizer would reach it. drop_input=1 blocks the CC's taps from leaking to the bottom
                    //    window; the app is paused behind the summoned CC so it needs no input meanwhile.
                    //  - Plain single-app CC (no bottom app, top app not dual-stack): the ONE app is fullscreen on
                    //    the TOP panel (display 2) and the CC's bottom digitizer targets display 0, which has no app
                    //    window. Per-display input already isolates the top app from bottom touches (same as the
                    //    two-app branch above uses drop_input=0), so drop_input=1 is NOT needed here - and because it
                    //    is a global drop it also swallows the gamepad (BTN_A/dpad etc.) that must reach the top game.
                    //    Keep it 0.
                    bool ccOccludesBottomApp = !mCcBottomApp.empty();   // force-visible CC over a grid-launched app
                    if (!ccOccludesBottomApp && mCcForceVisible) {
                        // Force-visible over a running app with no grid-launched bottom app: only a DUAL-STACK app
                        // has a bottom-panel window to isolate; a top-only single app does not.
                        char la[PROPERTY_VALUE_MAX] = {};
                        property_get("sys.gammaos.nano.launch_app", la, "");
                        if (la[0] && dualstackHas(la)) ccOccludesBottomApp = true;
                    }
                    int wantDrop = ccOccludesBottomApp ? 1 : 0;
                    if (property_get_int32("sys.gammaos.nano.drop_input", 0) != wantDrop)
                        property_set("sys.gammaos.nano.drop_input", wantDrop ? "1" : "0");
                    // Pin the CC's bottom panel (display cc.bottomdisplay) to its natural rotation while the CC
                    // renders on it: DisplayRotation honours sys.gammaos.nano.cc.active. Without this, launching an
                    // app lets stock AOSP rotate the default display to landscape (960x640 rotation-90) and the CC,
                    // which draws 640x480, lands rotated + squished in a corner. Drift-checked (write only on change).
                    if (property_get_int32("sys.gammaos.nano.cc.active", 0) != 1)
                        property_set("sys.gammaos.nano.cc.active", "1");
                    // Seed the idle timer on the activation edge so a freshly shown CC does not instantly
                    // auto-sleep. mCcActiveSeeded is cleared on teardown, so it re-arms per activation.
                    if (!mCcActiveSeeded) { mCcLastTouchMs = ccNowMs; mCcActiveSeeded = true; mCcFadeIn = 0.0f; }
                    // System IME up on the bottom panel: on a dual-screen device the soft keyboard is pinned
                    // to display 0, exactly where the CC renders, so the CC occludes it. HIDE the CC and stop
                    // rendering it while the keyboard is shown so it appears and is touchable (drop_input is
                    // already 0 for the single-app CC, so the bottom digitizer reaches the keyboard; cc.active
                    // stays 1 so DisplayRotation keeps the panel upright for it). Drain + discard the bottom
                    // digitizer so its fd does not backlog and the hidden CC does not act on taps meant for
                    // the keyboard. The first frame after the IME hides re-shows + re-renders the CC.
                    if (property_get_int32("sys.gammaos.nano.ime_visible", 0) == 1) {
                        ccHideForIme();
                        // The keyboard being up means the user is on the bottom panel: keep the 30s idle
                        // window fresh so the CC does not auto-sleep/dim the instant the keyboard closes.
                        mCcLastTouchMs = ccNowMs;
                        (void)ccDrainBottomTouch();
                        (void)ccPollTopTapDown();
                        if (mTopRingShown) hideTopFocusRing();
                        mRenderHeartbeat.fetch_add(1, std::memory_order_relaxed);
                        int64_t restUs = 50000 - (systemTime(SYSTEM_TIME_MONOTONIC) - ccT0) / 1000;
                        if (restUs > 500) usleep((useconds_t)restUs);
                        continue;
                    }
                    ccPollTouch();     // read the bottom digitizer (tiles / sliders / wake); refreshes mCcLastTouchMs on touch
                    (void)ccPollTopTapDown();   // keep the top digitizer drained so its fd does not fill (SYN_DROPPED) this session
                    // A finger held motionless stops emitting SYN frames (the gt9xx only reports on
                    // change), so ccTouchFrame would not refresh the timer and auto-sleep could dim the
                    // panel under a resting finger. Keep the 30s window open on the durable down state;
                    // lift-off then starts the countdown.
                    if (mCcTouchDownRaw || mCcHeldSlider >= 0) mCcLastTouchMs = ccNowMs;
                    ccUpdateSleep();   // advance the graceful bottom-screen dim/wake ramp
                    static unsigned sCcOrientCtr = 0;
                    // Fully slept: pause the control center completely. Skip render AND the live stats poll
                    // (the expensive parts) so the game keeps the SoC and temps drop; only the light touch
                    // drain + ramp above keep running so a tap still wakes it. Paced at 50ms (~20Hz) so wake
                    // still feels immediate while the parked panel costs almost nothing.
                    bool ccSlept = (mCcSleeping && mCcSleepDir == 0 && mCcSleepRamp <= 0.0f);
                    if (ccSlept) {
                        mRenderHeartbeat.fetch_add(1, std::memory_order_relaxed);
                        int64_t spentUs = (systemTime(SYSTEM_TIME_MONOTONIC) - ccT0) / 1000;
                        int64_t restUs  = 50000 - spentUs;
                        if (restUs > 500) usleep((useconds_t)restUs);
                        if ((sCcOrientCtr++ % 20) == 0) orientationTick();
                        continue;
                    }
                    // Awake: auto-sleep once 30s pass with nothing touching the bottom screen, using the
                    // SAME graceful ramp as the Sleep tile. A manual wake tap is a touch, so it refreshes
                    // mCcLastTouchMs and restarts this window; the awake gate stops it re-firing while
                    // dimming or slept until a touch re-arms it.
                    if (!mCcSleeping && mCcSleepDir >= 0 && (ccNowMs - mCcLastTouchMs) >= 30000) {
                        ccBeginSleep();
                    }
                    renderControlCenterFrame();
                    // Focus ring over the TOP app: here the CC pins the controller to the top, so a focus
                    // change fires a top-panel ring. The CC just rendered the bottom; present the ring on
                    // the top overlay surface (renderControlCenterFrame restored the primary current). Hide
                    // it once the pulse ends.
                    if (ccRingActive() && mCcRingDisp == property_get_int32("persist.gammaos.nano.cc.topdisplay", 2))
                        renderTopFocusRing();
                    else if (mTopRingShown) hideTopFocusRing();
                    mRenderHeartbeat.fetch_add(1, std::memory_order_relaxed);
                    // Pace for low heat: 20fps (50ms) at rest so the game keeps the SoC and temps stay
                    // down. While a finger or a grabbed slider is live, step up to ~30fps (33ms) so a drag
                    // tracks without visible lag (touch is drained at the top of every iteration regardless
                    // of the render budget, so the value applies within one poll either way; the bump just
                    // keeps the on-screen fill smooth). Heavy sysfs/popen reads are wall-clock throttled
                    // inside the draw, so the slower rate also cuts poll frequency, not just render frequency.
                    bool ccTouchActive = (mCcTouchDownRaw || mCcHeldSlider >= 0);
                    // The page-swipe slide (dashboard <-> app grid) eases mCcPageOffset toward mCcPage over
                    // ~0.28s AFTER the finger lifts, so with no finger down it would run at the 20fps rest
                    // budget and look choppy. Render the slide at 60fps (16.6ms) while it is in flight - the
                    // tween is dt-normalized so the duration is unchanged, only smoother. The offset snaps
                    // exactly to (float)mCcPage when settled, so the exact compare cleanly ends the bump. The
                    // at-rest / touch / ring pacing is untouched (the CC without swipes stays as it is).
                    bool ccPageAnimating = (mCcPageOffset != (float)mCcPage);
                    int64_t budgetUs = ccPageAnimating ? 16666
                                     : (ccTouchActive || ccRingActive()) ? 33333 : 50000;
                    int64_t spentUs  = (systemTime(SYSTEM_TIME_MONOTONIC) - ccT0) / 1000;
                    int64_t restUs   = budgetUs - spentUs;
                    if (restUs > 500) usleep((useconds_t)restUs);
                    if ((sCcOrientCtr++ % 20) == 0) orientationTick();
                    continue;
                }
                // Not showing the control center: hide its secondary layer if we had raised it, so the
                // running app owns its bottom screen again (no stale nano surface over the game).
                // If a bottom-screen app was still outstanding when the CC left the active state (e.g. the
                // TOP app was quit so controlCenterActive() went false), tear it down: force-stop it and
                // restore drop_input=1. Otherwise it would run orphaned with input isolation stuck off,
                // and the next CC session would immediately hide behind the stale package. Force-stop here
                // is off-thread and idempotent.
                ccEndBottomApp(true);
                ccSetFocusDisplay(-1);        // CC not active: release the focus pin, normal focus resumes
                if (property_get_int32("sys.gammaos.nano.cc.active", 0) != 0)
                    property_set("sys.gammaos.nano.cc.active", "0");   // release the display-0 rotation pin
                mCcRingDisp = -1;             // cancel any in-flight focus-ring pulse
                if (mTopRingShown) hideTopFocusRing();
                ccRestoreBacklightIfSlept();  // don't leave the bottom panel dark if the CC tore down while slept
                mCcActiveSeeded = false;      // re-seed the 30s idle timer on the next CC activation
                mCcForceVisible = false;      // the KEY_ALL_APPLICATIONS override is per-session: drop it when the
                                              // CC deactivates (app exited), so it cannot leak onto the next app
                                              // (e.g. force-showing the CC over a dual-stack app it was never
                                              // summoned over). Reached only when a higher-priority gate in
                                              // controlCenterActive() (app_launched=0) trips, never mid-session.
                hideControlCenterLayer();
                // Hidden overlay: block on the show_overlay trigger instead of
                // spin-polling at 30Hz. A spin-poll wakes this thread 30x/sec
                // even with nothing to do, and while a 3D game is foreground
                // those wakeups steal scheduler time on a weak SoC. Waiting on
                // the property's serial parks the thread at ~0% CPU and still
                // raises the overlay instantly when PhoneWindowManager flips
                // show_overlay on a power-hold (the wait returns the moment the
                // serial changes). A short timeout re-polls as a safety net so
                // we never miss a state change the wait did not observe.
                static const prop_info* sShowPi = nullptr;
                static uint32_t sShowSerial = 0;
                if (!sShowPi)
                    sShowPi = __system_property_find("sys.gammaos.nano.show_overlay");
                if (sShowPi) {
                    // Block until the property's serial advances past the last one
                    // we observed (or 250ms). sShowSerial starts at 0, so the first
                    // wait returns at once and hands back the live serial via the
                    // out-param; thereafter the thread parks at ~0% CPU and wakes the
                    // instant show_overlay changes. We rely on the wait's out-param for
                    // the serial because this bionic does not export
                    // __system_property_serial.
                    struct timespec to = { 0, 250000000 };  // 250ms safety re-poll
                    __system_property_wait(sShowPi, sShowSerial, &sShowSerial, &to);
                } else {
                    usleep(100000);  // prop not created yet; poll at 10Hz until it appears
                }
                // The render thread is intentionally parked while the overlay is
                // hidden (an app owns the screen), so render() is skipped and the
                // heartbeat would freeze. Bump it: the thread is alive and waiting,
                // not hung. Without this the watchdog aborts this process after 8s,
                // killing background music and cascading to the foreground app.
                mRenderHeartbeat.fetch_add(1, std::memory_order_relaxed);
                // Keep the orientation token live while the overlay is parked behind
                // a foreground app: the main-loop orientationTick() further down is
                // skipped by this continue, so an app would otherwise stay clamped to
                // whatever the home set. Run it here too, throttled to ~1s (this path
                // wakes ~every 250ms) so the foreground-package dumpsys probe does not
                // steal CPU from the running game.
                {
                    static unsigned sParkOrientCounter = 0;
                    if ((sParkOrientCounter++ & 3u) == 0) orientationTick();
                }
                continue;
            }
            // Overlay is shown: keep our render surface matched to the display's
            // current logical size so a forced portrait orientation reflows the XMB
            // to a real portrait layout (not a rotated/truncated landscape one).
            overlayUpdateSurfaceSize();
        }

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
            // SurfaceFlinger mode: instead of the DRM-direct handshake, launch
            // the DrasticSf host activity first. Starting any normal app already
            // hands the panel from this DRM home to SurfaceFlinger seamlessly,
            // and the foreground host keeps SF presenting while drastic-nano
            // renders its SF layer. Opt-in via the backend property; the
            // dual-display RG DS and every DRM-direct default keep
            // backend=auto/drm and take the unchanged handshake below.
            if (android::base::GetProperty(
                        "persist.gammaos.drastic_nano.backend", "auto") == "sf") {
                ALOGW("drastic nano: SF mode -- launching DrasticSf via launch_app");
                // Launch the DrasticSf host activity through the SAME path a
                // normal app (e.g. the store) uses: set launch_app and let the
                // home's existing app-launch handoff run. That handoff is what
                // cleanly exits DRM mode (drops DRM master) and brings the overlay
                // up to present through SurfaceFlinger; an abrupt am-start + _exit
                // here skips it and leaves DRM holding the panel (black). The
                // DrasticSf activity starts the drastic-nano binary itself
                // (start_sf) once it is foreground, and drastic renders into its
                // SurfaceView. setDrasticNanoRomPath() already wrote the ROM file.
                //
                // CRITICAL: the standalone XMB launch path already wrote a
                // launch_intent="file" pointing at the STOCK DraStic am-start
                // intent (nano_launch_intent.txt) and set launch_app to
                // com.dsemu.drastic. We must clear that intent and the framework
                // ROM so RootWindowContainer does a plain generic LAUNCHER start
                // of DrasticSf -- otherwise it relaunches stock DraStic from the
                // stale intent file (the "original drastic launches" bug).
                android::base::SetProperty("sys.gammaos.nano.launch_app",
                                           "com.gammaos.drasticsf");
                android::base::SetProperty("sys.gammaos.nano.launched_pkg",
                                           "com.gammaos.drasticsf");
                android::base::SetProperty("sys.gammaos.nano.launch_intent", "");
                setLaunchRomPath("");
                android::base::SetProperty("sys.gammaos.nano.launch_core", "");
                property_set("sys.gammaos.nano.return_recent", "1");
                property_set("service.bootanim.nano_retroarch", "1");
                property_set("sys.gammaos.nano.drop_input", "1");
                mDrasticNanoPending = false;
                // Drive the SAME graceful DRM->SurfaceFlinger handoff a normal app
                // launch uses, but kick it off directly. A button-press launch sets
                // mWaitForRelease and the fade (mLaunchFadeStart) is stamped by the
                // release handler in pollInput; we are in the render loop with no
                // pending release event, so stamp the fade ourselves. With
                // mLaunchFadeStart set the occlusion guard above does NOT park the
                // home, pollInput runs each frame, and once the ~260ms fade
                // completes it sets mExitRequested -> the post-loop path drops DRM
                // master and exits, so SurfaceFlinger (driven by the overlay
                // panel-takeover keeper) presents the DrasticSf surface. Without
                // this the home parks holding DRM master and the panel freezes on
                // the last XMB frame while drastic renders unseen into its surface.
                if (mLaunchFadeStart == 0) mLaunchFadeStart = uptimeMillis();
                // Do NOT _exit and do NOT leave the render loop here: keep rendering
                // the fade (continue back to the loop top); the fade -> mExitRequested
                // path performs the clean exit + DRM-master drop, exactly as it does
                // for any launched app. Exiting abruptly here would strand the panel.
                continue;
            }
            // Fade to black first, like a normal app launch (the SF branch above
            // uses the same mLaunchFadeStart). Render the fade inline -- render()
            // draws the black ramp while mLaunchFadeStart is set -- and keep the
            // heartbeat alive; once the ~260ms fade completes, pull the start
            // trigger and exit. We stay in this block (continue, mDrasticNanoPending
            // not cleared) so pollInput and the normal fade->mExitRequested handoff
            // never run, and the occlusion guard below is not reached.
            if (mLaunchFadeStart == 0) mLaunchFadeStart = uptimeMillis();
            if ((int64_t)uptimeMillis() - mLaunchFadeStart < 260) {
                render();
                mRenderHeartbeat.fetch_add(1, std::memory_order_relaxed);
                continue;
            }
            // setDrasticNanoRomPath() already wrote the ROM file; pull the trigger.
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
                    // Set DEVICE_PROVISIONED early during setup wizard so
                    // framework services (DownloadProvider, AppStateTracker)
                    // initialize properly. USER_SETUP_COMPLETE stays 0 until
                    // finishSetupWizard().
                    if (mSetupWizardActive && !mSetupBootWaited) {
                        mSetupBootWaited = true;
                        system("settings put global device_provisioned 1 "
                               "2>/dev/null &");
                        ALOGI("NanoMenu: setup wizard - set "
                              "device_provisioned=1 at boot_completed");
                    }
                }
            } else if (elapsedRealtime() - bootCompletedDetectedMs
                       >= 1000) {
                // setclock_stock.sh is intentionally NOT run on boot (user
                // request): the nano menu no longer forces stock CPU/GPU clocks.
                // Performance modes selected from the Quick Menu still apply.
                stockClocksApplied = true;
                // Push NanoMenu's brightness TO Android settings now
                // that the settings provider is available. NanoMenu's
                // persist property is the source of truth during boot.
                syncBrightnessToAndroid();
                ALOGD("NanoMenu: brightness pushed to Android: %d", mBrightness);
            }
        }
        // GammaOS: DRM-home occlusion guard (defense-in-depth). The single-
        // instance handover in main() exits a stray DRM-home before it ever runs
        // this loop, but if one slips through a race (a respawn in the brief
        // app-exit window before show_overlay flips), idle it here BEFORE
        // pollInput() so it never reads the shared ungrabbed evdev nodes. A
        // second input reader would navigate/select on its hidden menu and grab
        // the gamepad via the post-loop EVIOCGRAB, freezing the overlay. Draw
        // nothing and poll the trigger cheaply; resume the instant we are the
        // visible surface again. (Only the DRM home; the overlay has its own
        // !mOverlayShown idle above and is the visible surface in these states.)
        //
        // CRITICAL: do NOT fire while THIS instance is mid-launch
        // (mWaitForRelease / mLaunchFadeStart). The cold-boot home sets
        // app_launched=1 as it launches the first game, but the code that
        // completes its hand-off and exit (mLaunchFadeStart -> mExitRequested)
        // lives inside pollInput(). Gating before pollInput then would strand the
        // launcher alive-but-idle instead of letting it exit, leaving two
        // instances. Excluding the launch window lets it finish exiting; a true
        // race-stray is never mid-launch, so it is still caught.
        // Both props are empty/false for the entire life of a normal home session,
        // yet a full property_get_bool name-lookup of both ran every frame (it showed
        // up in the idle profile). Cache each via its serial: read the serial (a cheap
        // pointer-deref) every frame, re-parse the bool only when it advances. The
        // guard still reacts the same frame either prop flips. BOTH serials are
        // refreshed every frame (the || only short-circuits the cached-bool eval, not
        // the refresh) so show_overlay staleness can never hide behind app_launched.
        // Render-thread-only statics.
        {
            static const prop_info* sAlPi = nullptr; static uint32_t sAlSer = 0; static bool sAlVal = false;
            static const prop_info* sSoPi = nullptr; static uint32_t sSoSer = 0; static bool sSoVal = false;
            static const prop_info* sPcPi = nullptr; static uint32_t sPcSer = 0; static bool sPcVal = false;
            if (!sAlPi) sAlPi = __system_property_find("sys.gammaos.nano.app_launched");
            if (sAlPi) { uint32_t s = __system_property_serial(sAlPi); if (s != sAlSer) { sAlSer = s; sAlVal = property_get_bool("sys.gammaos.nano.app_launched", false); } }
            else sAlVal = false;
            if (!sSoPi) sSoPi = __system_property_find("sys.gammaos.nano.show_overlay");
            if (sSoPi) { uint32_t s = __system_property_serial(sSoPi); if (s != sSoSer) { sSoSer = s; sSoVal = property_get_bool("sys.gammaos.nano.show_overlay", false); } }
            else sSoVal = false;
            // GammaOS PSP slide clock: do NOT self-park the non-overlay home when the only
            // reason to park is a clock summon this instance itself must render.
            //
            // Background - two device topologies drive the clock:
            //   * RG Rotate (and any DRM-direct device): TWO nano instances. A DRM-direct home
            //     (mOverlayMode=false) owns the panel at the cold-boot menu and draws the clock
            //     IN-PLACE off the raw swivel (show_overlay stays 0 there, so this park gate,
            //     which keys off app_launched||show_overlay, never trips). Once an app launches,
            //     the resident `gammaos-nano --overlay` instance (mOverlayMode=true) takes over
            //     and draws the clock over the app via the show_overlay/pspclock_summon path.
            //   * TrimUI Brick (and any SF-composited-home device, drm_active=0): only ONE
            //     instance exists at the cold-boot menu - the non-overlay home, composited
            //     through SurfaceFlinger. There is no resident overlay to hand the summon to.
            //
            // The freeze: PhoneWindowManager::gammaClockSummon raises sys.gammaos.nano.show_overlay
            // on the swivel (its app_launched gate was dropped in e8d1246ef58 so a single swivel
            // summons), intending the resident overlay to draw the clock. On the Brick there is no
            // resident overlay, and this non-overlay home then saw show_overlay=1, concluded it was
            // "occluded by a foreground app", and parked - render() skipped, the render thread
            // wedged in the 33ms usleep below at 0% CPU. Nothing else drew the clock, so the screen
            // froze on the last frame; and because drawPspClock (which ramps the reveal and clears
            // the summon state) lives inside the skipped render(), releasing the swivel never
            // recovered it. Device-confirmed: sys.gammaos.nano.shot was never consumed and
            // NanoMenu::threadLoop() sat in usleep. Verified the exact same clock renders fine in
            // the --overlay instance, so this is purely the instance/park interaction, not the GL.
            //
            // The fix: when no app is actually running (app_launched=0) but show_overlay is up and
            // the pspclock feature is enabled, the raised overlay can ONLY be a clock summon that
            // this home is the sole instance able to service - so keep rendering (drawPspClock runs
            // in-place, exactly like the DRM-direct home does). A real app occlusion still sets
            // app_launched=1 and parks as before, and on that path the --overlay instance (when one
            // exists) handles the clock. pspclock is read serial-cached like the two props above so
            // the added lookup stays a pointer-deref per frame. drawPspClock lowers show_overlay
            // itself once the reveal fully retracts (see NanoMenuPS3Clock.cpp) so this does not latch.
            if (!sPcPi) sPcPi = __system_property_find("persist.gammaos.nano.pspclock");
            if (sPcPi) { uint32_t s = __system_property_serial(sPcPi); if (s != sPcSer) { sPcSer = s; sPcVal = property_get_bool("persist.gammaos.nano.pspclock", false); } }
            else sPcVal = false;
            const bool pspClockSummonHome = sSoVal && !sAlVal && sPcVal;
            if (!mOverlayMode && mLaunchFadeStart == 0 && !mWaitForRelease
                    && (sAlVal || sSoVal) && !pspClockSummonHome) {
                // Parked (occluded by the foreground app): render() is skipped, so
                // keep the watchdog heartbeat alive or it aborts this process after 8s.
                mRenderHeartbeat.fetch_add(1, std::memory_order_relaxed);
                // Occluded by a foreground app. If the music player was minimized into the
                // background, free any Canyon/Globe visualizer GL now (a leave fade may have
                // been interrupted mid-ramp before musicTick could free it), so the vis GL
                // never lingers while an app runs. Idempotent (ready()-gated).
                if (!mMpActive) freeMusicVisGl();
                // Likewise hard-free the video decoder if one is still alive (the player is
                // full-screen on the home, but never let a codec/worker/surface linger behind
                // a foreground app). Idempotent.
                videoHardFree();
                scraperFreeBoxart();   // never let scraped cover textures linger behind an app
                usleep(33000);   // ~30Hz; no input, no render while occluded
                continue;
            }
        }
        pollInput();
        checkInputHotplug();

        // GammaOS Nano: re-fire a deferred game/app launch as soon as
        // the system is far enough through boot to accept it. The
        // toast set mLaunchPending=true when the user pressed A
        // before isLaunchReady() was true; handleSelect() re-enters
        // the same launch path the original press hit. Navigation
        // handlers (handleUp/Down/Left/Right/Back) clear the pending
        // flag, so this only fires if the user is still parked on
        // the same item they originally selected.
        if (mLaunchPending && isLaunchReady()) {
            ALOGI("NanoMenu: deferred launch firing -- system ready");
            mLaunchPending = false;
            mShowLaunchBusy = false;
            mLaunchBusyTimer = 0;
            handleSelect();
        }

        // Adaptive framerate:
        //   60fps for DRM, XMB, or procedural effects (vsync-locked, no usleep)
        //   20fps for particle effects
        //   ~10fps idle
        bool xmbActive = (mCurrentEffect == 21);
        bool proceduralFx = (mCurrentEffect >= 11 && mCurrentEffect <= 20);
        // The PS3 XMB (mPs3Xmb) animates the wave/clock/transitions continuously
        // and must run at 60fps. It does not key off mCurrentEffect==21, so without
        // this it fell through to the 20fps "animating" tier and was usleep-capped
        // to 50ms/frame (~20fps) regardless of how cheap the actual frame was.
        bool xmbAnimating = mXmbMode && (fabsf(mXmbAnimX - mXmbSystemIndex) > 0.01f
                                         || fabsf(mXmbAnimY - (mSearchActive
                                             ? (float)mSearchSelectedIndex
                                             : (float)mXmbGameIndex)) > 0.01f);
        bool animating = (mCurrentEffect != 0) || mShowBrightnessBar
                         || mWaitForRelease
                         || mShowLaunchBusy
                         || ((mMenuState == MENU_RECENT || mMenuState == MENU_APPS)
                             && mScrollOffset > 0.0f);
        // GammaOS: adaptive idle frame-rate for the PS3 XMB. When the menu is
        // fully settled (nothing navigating or transitioning - only the slow
        // selected-label glow pulse and the clock tick still animate) it still
        // re-renders the ~16-20 glass icons, the 15-copy label glow, the
        // ~250-call clock and all glyphs at a hard 60fps. Dropping to ~30fps
        // halves both the CPU and the GPU of that floor, but a 30fps WAVE is
        // visibly choppier than 60 and reads as "the menu got laggy" if it
        // happens anywhere near an interaction - so the drop only kicks in
        // after a full MINUTE with no input. Any input snaps the next frame
        // back to 60 (pollInput stamps mLastInputMs before this runs, and it
        // clears the animation flags, so there is no snap-back jank). Tunable
        // via persist.gammaos.nano.ps3xmb.idlefps (the rate used once the
        // minute elapses, default 30; set 60 to disable the drop entirely).
        // The PSP slide clock is a continuous animation (spring second hand, gyro parallax,
        // and a live 60fps mirrored app backdrop). While it is open or animating we are NOT
        // settled: in the standalone over-app summon nano receives no input events, so the
        // 60s idle timer would otherwise fire and pace the clock to 30fps - which reads as a
        // choppy game-behind-the-glass even though the mirror feeds 60fps. Force 60fps here.
        const bool pspClockActive = mPspClockEnabled
                                 && (mPspClockOn || mPspClockReveal > 0.0f);
        bool ps3Settled = mPs3Xmb && !mPs3CatAnimActive
                       && mPs3ItemAnimStart < 0.0f && mPs3SubAnimStart < 0.0f
                       && mOverlayEnterStart < 0.0f
                       && !mPs3BootActive && !mPs3WizActive && !mPs3DlgActive
                       && !mPs3TzActive && !mShowBrightnessBar && !mShowVolumeBar
                       && mLaunchFadeStart == 0 && !mOverlayLaunchPending
                       && !mXmbItemFling && !mXmbTouchTracking
                       && !pspClockActive
                       && !ps3bg::themeFading();
        int frameTimeUs;
        if (sDrmActive || xmbActive || mXmbMode || mPs3Xmb || proceduralFx) {
            frameTimeUs = 16666; // 60fps — vsync-locked, no usleep
            if (ps3Settled) {
                static int sIdleFpsProp = -2;
                if (sIdleFpsProp == -2) {
                    char b[PROPERTY_VALUE_MAX] = {};
                    property_get("persist.gammaos.nano.ps3xmb.idlefps", b, "");
                    sIdleFpsProp = b[0] ? atoi(b) : -1;
                    if (sIdleFpsProp > 60) sIdleFpsProp = 60;
                    if (sIdleFpsProp == 0) sIdleFpsProp = 30;
                }
                bool longIdle =
                    (int64_t)android::uptimeMillis() - mLastInputMs >= 60000;
                int idleFps = longIdle ? (sIdleFpsProp > 0 ? sIdleFpsProp : 30)
                                       : 60;
                if (idleFps < 60) frameTimeUs = 1000000 / idleFps;   // e.g. 30fps -> 33333us
            }
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

        // Screen-off pause (overlay process). In overlay mode the framework owns
        // the power button and blanks the panel on sleep (PowerManager goToSleep
        // sets sys.screen.state=off) WITHOUT nano ever seeing a power-press, so
        // the run loop would otherwise keep rendering the wave wallpaper at full
        // rate behind a dark screen - pegging a core with the display off. Skip
        // the frame and idle-poll while the screen is off; any wake flips
        // sys.screen.state back to on and rendering resumes next pass. (The DRM
        // home does not need this: it blanks via enterDrmSleep, which blocks the
        // render thread itself.)
        if (mOverlayMode) {
            char ss[PROPERTY_VALUE_MAX] = {};
            property_get("sys.screen.state", ss, "on");
            bool screenOff = !strcmp(ss, "off");
            // The DSi home BGM (menu_ambiance) must NOT keep playing behind a dark screen
            // (user: suspend properly). Stop it on screen-off; the idle-poll below never
            // restarts it (ndsAmbianceTick only runs while rendering with the screen on).
            if (screenOff && mAmbiancePlaying) { mAmbiancePlayer.stop(); mAmbiancePlaying = false; }
            // Drop to the powersave governor while the panel is off and restore the
            // user's mode when it returns (the framework drives display standby for the
            // overlay, but not the CPU clocks).
            static bool sOvlPwrSave = false;
            if (screenOff && !sOvlPwrSave) { nanoApplyPerfClock("powersave"); property_set("sys.gammaos.nano.screenoff", "1"); sOvlPwrSave = true; }
            else if (!screenOff && sOvlPwrSave) { property_set("sys.gammaos.nano.screenoff", "0"); nanoRestorePerfClock(); sOvlPwrSave = false; }
            // The framework drives suspend for the overlay; release the BT bluesleep
            // wakelock (BT off) so it is not blocked. Restored when the panel returns.
            static bool sOvlBtLpm = false;
            nanoBtLpmSuspendGate(screenOff, sOvlBtLpm);
            // Keep background music alive across screen-off, including a real
            // suspend on battery: the framework drives standby for the overlay, but
            // nothing holds the SoC up for the in-process decode/AAudio threads, so
            // on battery they freeze when the device suspends. While audio is active
            // hold the nano_music kernel wakelock (overlay has CAP_BLOCK_SUSPEND) and
            // run the audio-only auto-advance so the album keeps flowing; release the
            // lock the moment the screen returns or the queue ends, or the device
            // would never sleep. "active" excludes the queue-end paused state but
            // includes the brief async track-change gap (mMpAdvancing, not paused).
            bool audioActive = mMusicPlayer.isPlaying() ||
                               (mMpAdvancing && !mMusicPlayer.isPaused());
            static bool sOvlAudioWake = false;
            if (screenOff && audioActive) {
                if (!sOvlAudioWake) {
                    int wl = open("/sys/power/wake_lock", O_WRONLY | O_CLOEXEC);
                    if (wl >= 0) { ssize_t n = write(wl, "nano_music", 10); (void)n; close(wl); }
                    sOvlAudioWake = true;
                }
                // audio-only auto-advance (no GL; mirrors musicTick's gate)
                if (mMpAdvancing && !mMusicPlayer.ended()) mMpAdvancing = false;
                if (!mMpQueue.empty() && mMusicPlayer.ended() && !mMpAdvancing) {
                    mMpAdvancing = true;
                    if (mMpRepeat == 2) mpPlayCurrent();
                    else mpStep(1, true);
                }
                usleep(1000000);   // 1Hz while playing screen-off (catch end-of-track promptly)
                continue;
            }
            if (sOvlAudioWake) {   // screen back on, or audio stopped / queue ended -> release
                int wl = open("/sys/power/wake_unlock", O_WRONLY | O_CLOEXEC);
                if (wl >= 0) { ssize_t n = write(wl, "nano_music", 10); (void)n; close(wl); }
                sOvlAudioWake = false;
            }
            if (screenOff) {
                usleep(250000);   // 4Hz idle poll while the panel is off (no audio)
                continue;
            }
        }
        // Screen-off pause for the NON-overlay home on devices without a DRM-direct
        // path (this Brick renders the home through SurfaceFlinger). There the framework
        // owns the display + power button, so a display timeout / framework sleep blanks
        // the panel via sys.screen.state=off WITHOUT nano ever running enterDrmSleep
        // (which only fires on nano's own power handling, and on this device the
        // framework can sleep us first). The cold-boot home would otherwise keep
        // rendering the wave wallpaper at full rate behind a black screen, pegging a core
        // (measured ~25% during sleep = battery drain). Mirror the overlay: drop to
        // powersave, hold the music wakelock only while a track is actually playing, run
        // the audio-only auto-advance, and idle-poll instead of rendering. pollInput()
        // already ran above this point, so input stays live while parked; the framework
        // owns wake and flips sys.screen.state back on. (DRM-direct homes keep using
        // enterDrmSleep, which blocks the render thread itself, so this is gated off there.)
        else if (!sDrmActive) {
            char ss[PROPERTY_VALUE_MAX] = {};
            property_get("sys.screen.state", ss, "on");
            bool screenOff = !strcmp(ss, "off");
            // Stop the DSi home BGM behind a dark screen (user: suspend properly).
            if (screenOff && mAmbiancePlaying) { mAmbiancePlayer.stop(); mAmbiancePlaying = false; }
            static bool sSfPwrSave = false;
            if (screenOff && !sSfPwrSave) { nanoApplyPerfClock("powersave"); property_set("sys.gammaos.nano.screenoff", "1"); sSfPwrSave = true; }
            else if (!screenOff && sSfPwrSave) { property_set("sys.gammaos.nano.screenoff", "0"); nanoRestorePerfClock(); sSfPwrSave = false; }
            // Release the BT bluesleep wakelock (BT off) on framework-driven screen-off
            // so suspend-to-RAM is not blocked; restored when the panel returns.
            static bool sSfBtLpm = false;
            nanoBtLpmSuspendGate(screenOff, sSfBtLpm);
            bool audioActive = mMusicPlayer.isPlaying() ||
                               (mMpAdvancing && !mMusicPlayer.isPaused());
            static bool sSfAudioWake = false;
            if (screenOff && audioActive) {
                if (!sSfAudioWake) {
                    int wl = open("/sys/power/wake_lock", O_WRONLY | O_CLOEXEC);
                    if (wl >= 0) { ssize_t n = write(wl, "nano_music", 10); (void)n; close(wl); }
                    sSfAudioWake = true;
                }
                if (mMpAdvancing && !mMusicPlayer.ended()) mMpAdvancing = false;
                if (!mMpQueue.empty() && mMusicPlayer.ended() && !mMpAdvancing) {
                    mMpAdvancing = true;
                    if (mMpRepeat == 2) mpPlayCurrent();
                    else mpStep(1, true);
                }
                usleep(1000000);   // 1Hz while playing screen-off (catch end-of-track)
                continue;
            }
            if (sSfAudioWake) {    // screen back on, or audio stopped / queue ended -> release
                int wl = open("/sys/power/wake_unlock", O_WRONLY | O_CLOEXEC);
                if (wl >= 0) { ssize_t n = write(wl, "nano_music", 10); (void)n; close(wl); }
                sSfAudioWake = false;
            }
            if (screenOff) {
                usleep(250000);   // 4Hz idle while the framework holds the panel off
                continue;
            }
        }

        render();

        // GammaRGB Follow-Screen: in DRM mode nano owns the panel, so SF's
        // sampler is blind; sample our own just-presented frame and drive the
        // LED colour prop (no-op unless rgb.effect=follow). Cheap, fps-paced.
        nanoRgbFollowSample();

        // ADPF: tell the power HAL how long this frame's work took, so it can
        // scale to hold the 60fps target.
        perfHintReport();

        // GammaOS: XMB FPS counter. Logs once per second when in XMB mode so
        // we can verify the menu is actually hitting the 60fps target post-
        // optimization. Gated on the fpslog opt-in prop so production boots
        // do not chat to logd at all.
        {
            static int64_t sFpsWindowStartNs = 0;
            static int sFpsFrames = 0;
            static int64_t sFpsMinFrameUs = 0;
            static int64_t sFpsMaxFrameUs = 0;
            static bool sFpsLog =
                property_get_bool("persist.gammaos.nano.ps3xmb.fpslog", false);
            if (sFpsLog && (mXmbMode || mPs3Xmb)) {
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

        // Frame pacing, clock-based: measure actual elapsed time so variable
        // swap durations don't cause frame-to-frame jitter. This now ALSO runs
        // at the 60fps target (>=, not >): on the SurfaceFlinger overlay path
        // the swap interval is 0 (see the eglSwapInterval calls), so this sleep
        // is what paces submission - a frame that finishes early gets topped up
        // to the frame period instead of blocking inside eglSwapBuffers until
        // the NEXT vsync, which used to turn every 17-18ms frame into a 33ms
        // one. On the DRM path the swap blocks on the page flip as before and
        // elapsed >= the period, so the sleep stays a no-op there.
        if (frameTimeUs >= 16666) {
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
            // GammaOS Nano orientation: publish the foreground-aware orientation
            // token every tick (idempotent; only writes on change). Both the home
            // and overlay processes run this and agree on the token from shared
            // props, so the display holds nano's orientation while nano/overlay is
            // foreground and hands back to the app otherwise.
            orientationTick();
            char val[PROPERTY_VALUE_MAX] = {};
            property_get("service.bootanim.exit", val, "0");
            // The resident overlay process is the persistent launcher: it must
            // never self-exit on bootanim.exit (home nano sets that to hand off
            // the boot screen). Only the home/boot nano honours it.
            if (!strcmp(val, "1") && !mWaitForRelease && !mOverlayMode) {
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

            // GammaOS Nano: live-refresh the Applications list on install / remove /
            // update. SystemServer's package receiver rewrites nano_app_icons/<pkg>.png
            // and nano_app_labels.txt, THEN bumps sys.gammaos.nano.apps_generation. We
            // watch only that prop's serial (the value is for logging): any advance
            // means the app set changed, including the first appearance when the boot
            // cache lands, so even the cold-boot home picks up real labels/icons without
            // a manual re-open. This runs on the render thread with the GL context
            // current (see glDeleteTextures below), so the file read, the icon-texture
            // invalidate, and the rebuild are all safe here, at the ~0.5s cadence.
            {
                static const prop_info* sAgPi = nullptr; static uint32_t sAgSer = 0;
                if (!sAgPi) sAgPi = __system_property_find("sys.gammaos.nano.apps_generation");
                if (sAgPi) {
                    uint32_t s = __system_property_serial(sAgPi);
                    if (s != sAgSer) {
                        sAgSer = s;
                        ALOGI("GammaOS Nano: apps_generation bumped, refreshing Applications");
                        // Snapshot the current package set so a freshly installed app can be
                        // spotted after the reload and the cursor moved to it (the list stays
                        // alphabetical, so a new app otherwise lands mid-list out of view).
                        std::vector<std::string> prevPkgs;
                        prevPkgs.reserve(mAppEntries.size());
                        for (auto& a : mAppEntries) prevPkgs.push_back(a.packageName);
                        // Re-read packages.list + the label cache (sets mAppsLoaded=true).
                        loadInstalledApps();
                        // First package present now but not before = the new install (if any).
                        std::string newPkg;
                        for (auto& a : mAppEntries) {
                            bool had = false;
                            for (auto& p : prevPkgs) if (p == a.packageName) { had = true; break; }
                            if (!had) { newPkg = a.packageName; break; }
                        }
                        // Free the cached real-icon GL textures before clearing the map, so
                        // an updated icon is re-decoded and no texture leaks. buildAppSubmenu
                        // re-lazy-loads each icon on the next build (it caches successes only).
                        for (auto& kv : mPs3AppIcons)
                            if (kv.second) { GLuint t = kv.second; glDeleteTextures(1, &t); }
                        mPs3AppIcons.clear();
                        // Rebuild the visible Applications level in place. If an app was just
                        // installed while this list is open, put the cursor on it so it scrolls
                        // into view; otherwise keep the cursor where it was.
                        if (!mPs3Stack.empty() && mPs3Stack.back().title == "Applications") {
                            int keep = mPs3Stack.back().sel;
                            buildAppSubmenu(mPs3Stack.back());
                            int n = (int)mPs3Stack.back().items.size();
                            int target = -1;
                            if (!newPkg.empty())
                                for (int i = 0; i < n; i++)
                                    if (mPs3Stack.back().items[i].payloadStr == newPkg) {
                                        target = i; break;
                                    }
                            if (target >= 0) {
                                // Smooth-glide to the new app "as if the nav button were
                                // held": keep the cursor where it visually is (buildAppSubmenu
                                // reset sel to 0) and arm tickAutoScroll to step to target.
                                if (keep < 0) keep = 0; if (keep > n - 1) keep = n - 1;
                                mPs3Stack.back().sel = keep;
                                mPs3AutoScrollTarget = target;
                                mPs3AutoScrollLastMs = 0;   // first step fires immediately
                                mPs3AutoScrollCount  = 0;
                            }
                            else if (n <= 0) mPs3Stack.back().sel = 0;
                            else { if (keep < 0) keep = 0; if (keep > n - 1) keep = n - 1;
                                   mPs3Stack.back().sel = keep; }
                        }
                        // Legacy (non-ps3xmb) Applications list: same jump-to-new-app, else clamp.
                        if (mMenuState == MENU_APPS) {
                            int target = -1;
                            if (!newPkg.empty())
                                for (int i = 0; i < (int)mAppEntries.size(); i++)
                                    if (mAppEntries[i].packageName == newPkg) { target = i; break; }
                            if (target >= 0) mAppSelectedIndex = target;
                            else if (mAppSelectedIndex >= (int)mAppEntries.size())
                                mAppSelectedIndex = mAppEntries.empty()
                                        ? 0 : (int)mAppEntries.size() - 1;
                        }
                        // Uninstall complete: this refresh was triggered by the removal, so
                        // the pending package is now gone from the list. Close the
                        // "Uninstalling..." progress modal (see applyThemeSetting case 31).
                        if (!mNanoUninstallPending.empty()) {
                            bool stillThere = false;
                            for (auto& a : mAppEntries)
                                if (a.packageName == mNanoUninstallPending) { stillThere = true; break; }
                            if (!stillThere) {
                                mNanoUninstallPending.clear();
                                if (mPs3DlgActive) closePs3Dialog(false);
                            }
                        }
                        mDisplayDirty = true;
                    }
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
                std::vector<std::pair<std::string, std::vector<std::string>>>
                    pendingCacheWrites;
                if (mBgScanResultReady) {
                    std::lock_guard<std::mutex> lock(mBgScanMutex);
                    if (mBgScanResultReady) {
                        for (int i = 0; i < (int)mXmbSystems.size()
                                 && i < (int)mBgScanResults.size(); i++) {
                            auto& sys = mXmbSystems[i];
                            auto& res = mBgScanResults[i];
                            if (!sys.enabled) continue;   // disabled systems are never scanned
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
                            bool romsChanged = (res.roms != sys.roms);
                            if (romsChanged || !sys.scanned) {
                                sys.roms = std::move(res.roms);
                                sys.displayNames = std::move(res.displayNames);
                                sys.activePaths = std::move(res.activePaths);
                                sys.activePath = std::move(res.activePath);
                                sys.pathExists = !sys.roms.empty();
                                mDisplayDirty = true;
                                // Game tiles show per-system ROM counts (and
                                // appear/disappear with them): refresh the PS3
                                // cats once the user is at the settled root.
                                if (romsChanged) mPs3CatsStale = true;
                                // Queue the cache write for the writer thread
                                // below: the file I/O (mkdir/open/write/fsync
                                // latency on f2fs) used to run right here ON
                                // the render thread and showed up as sporadic
                                // mid-scroll frame spikes when a 30s rescan
                                // published changes during navigation. The
                                // roms snapshot is copied only for ACCEPTED
                                // changes (rare), never per frame.
                                pendingCacheWrites.emplace_back(sys.id, sys.roms);
                            }
                            sys.scanned = true;
                            sys.lastScanTime = elapsedRealtime();
                        }
                        mBgScanResultReady = false;
                        mXmbRomScanDone = true;
                    }
                }

                // Flush queued ROM-cache writes on a detached low-priority
                // thread, fully off the render thread. Writes only reflect
                // results that passed the accept guards above.
                if (!pendingCacheWrites.empty()) {
                    std::thread([writes = std::move(pendingCacheWrites)]() {
                        for (const auto& wr : writes) {
                            mkdir("/data/system/nano_xmb_cache", 0755);
                            std::string cp = "/data/system/nano_xmb_cache/" + wr.first + ".list";
                            int cfd = open(cp.c_str(), O_WRONLY|O_CREAT|O_TRUNC, 0644);
                            if (cfd < 0) continue;
                            for (const auto& r : wr.second) {
                                std::string l = r + "\n";
                                write(cfd, l.c_str(), l.size());
                            }
                            close(cfd);
                        }
                    }).detach();
                    pendingCacheWrites.clear();
                }

                // Boxart scraper: merge finished cover/fanart into the manifest +
                // save. Must run every frame (not only at the settled XMB root like
                // the media drains below) because a scrape runs while the user is
                // deep in the Settings submenu with the progress modal up.
                scraperDrainResults();

                // IPTV: rebuild the open channel-groups screen when the background fetch/
                // parse finishes. Must run every frame (not only at the settled XMB root)
                // because the groups screen is a pushed submenu (mPs3Stack non-empty).
                iptvDrain();
                // Internet Radio: same, for the open stations screen (Music category).
                radioDrain();

                // Periodic rescan every 30s (runs on background thread).
                // SUPPRESSED while a full-screen media player owns the screen
                // (video/stream, music Now-Playing, photo viewer): the rescan
                // worker walks the entire ROM tree on storage, and that I/O +
                // allocation competes with live streaming (HLS network buffering
                // + HW decoder). On a long-running stream that pressure stalled
                // the render thread >8s and tripped the render watchdog, aborting
                // nano. The Game column is not even visible behind a player, so
                // there is nothing to refresh; the timer stays due and a single
                // rescan fires the moment the player closes.
                if (mXmbBootCompleted && !mBgScanThreadRunning
                    && !mXmbSystems.empty()
                    && !mVidActive && !mMpActive && !mPvActive) {
                    int64_t now = elapsedRealtime();
                    if (mXmbSystems[0].lastScanTime > 0 &&
                        (now - mXmbSystems[0].lastScanTime) > 30000) {
                        forceRescanAllSystems();
                    }
                }

                // Deferred Game-column refreshes, applied only at the settled
                // XMB root (never mid-navigation, mid-animation or with UI on
                // top, so the user's position is preserved):
                //  - mPs3CatsStale: a background rescan changed ROM lists
                //    (tile presence / counts).
                //  - config stamp moved: the OTHER nano process (resident
                //    overlay vs DRM home share nano_systems.json) edited the
                //    systems config; reload it and rescan so both converge
                //    without a restart.
                // Also suppressed behind a full-screen media player (video/stream,
                // music Now-Playing, photo viewer): this block kicks the game/music/
                // photo/video library scans (forceRescanAllSystems/musicScanAsync/
                // photoScanAsync/videoScanAsync) and rebuilds the XMB columns - all
                // heavy storage I/O that is invisible behind a player and competes
                // with live playback (the ROM/video tree-walk under streaming
                // pressure stalled the render thread and tripped the watchdog).
                if (mPs3Xmb && mPs3Stack.empty()
                    && !mVidActive && !mMpActive && !mPvActive
                    && !mPs3DlgActive && !mOskActive && !mPs3WizActive
                    && !mPs3BootActive && !mPs3TzActive && !mPs3CatAnimActive
                    && mPs3ItemAnimStart < 0.0f && mPs3SubAnimStart < 0.0f) {
                    // The reload waits out a running scan thread (it reads
                    // mXmbSystems unlocked); the stamp stays unequal so the
                    // next tick retries. Pending scan results are dropped: they
                    // are indexed against the pre-reload systems vector.
                    if (!mBgScanThreadRunning
                        && systemsConfigStamp() != mSystemsCfgStamp) {
                        ALOGI("NanoMenu: nano_systems.json changed externally, reloading");
                        {
                            std::lock_guard<std::mutex> lock(mBgScanMutex);
                            mBgScanResults.clear();
                            mBgScanResultReady = false;
                        }
                        initXmbSystems();
                        mPs3CatsStale = true;
                        forceRescanAllSystems();
                    }
                    // Music library: swap in finished scan results, and reload
                    // nano_music.json if the OTHER nano process edited it. Both set
                    // mMusicCatsStale so the Music column rebuilds below.
                    musicDrainScanResults();
                    // A scan deferred because external storage was not mounted yet
                    // retries here once the volume becomes reachable.
                    if (mMusicScanPending && !mMusicScanRunning && musicStorageReady())
                        musicScanAsync();
                    if (mMusicLoaded && !mMusicScanRunning
                        && musicConfigStamp() != mMusicCfgStamp) {
                        loadMusicConfig();
                        mMusicCatsStale = true;
                    }
                    // Photo library: same drain / pending-retry / external-reload as music.
                    photoDrainScanResults();
                    if (mPhotoScanPending && !mPhotoScanRunning && photoStorageReady())
                        photoScanAsync();
                    if (mPhotoLoaded && !mPhotoScanRunning
                        && photoConfigStamp() != mPhotoCfgStamp) {
                        loadPhotoConfig();
                        mPhotoCatsStale = true;
                    }
                    // Video library: same drain / pending-retry / external-reload as music.
                    videoDrainScanResults();
                    if (mVideoScanPending && !mVideoScanRunning && videoStorageReady())
                        videoScanAsync();
                    if (mVideoLoaded && !mVideoScanRunning
                        && videoConfigStamp() != mVideoCfgStamp) {
                        loadVideoConfig();
                        videoSortApply();
                        mVideoCatsStale = true;
                    }
                    if (mPs3CatsStale || mMusicCatsStale || mPhotoCatsStale || mVideoCatsStale) {
                        rebuildPs3CatsPreserveSel();
                        mPs3CatsStale = false;
                        mMusicCatsStale = false;
                        mPhotoCatsStale = false;
                        mVideoCatsStale = false;
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
        // Launching an app parks the home: hand the single HW video decoder back from the video
        // wallpaper so the app (or a later video clip) can use it, and stop burning power decoding a
        // background the user cannot see.
        if (mWpVideoTop) wpVideoStop();
        drmStop();
        if (!mDrmBootPath) {
            setupSecondaryEglSurfaces();
        }
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
    if (mDrmBootPath) {
        // DRM boot path: no SF surface to render loading screen on.
        // Just wait for the app to launch, then exit immediately.
        // HWC will re-acquire DRM master when our process exits.
        ALOGD("NanoMenu: DRM boot path exit, waiting for app launch");
        char launched[PROPERTY_VALUE_MAX] = {};
        for (int wait = 0; wait < 600; wait++) {
            property_get("sys.gammaos.nano.app_launched", launched, "0");
            if (!strcmp(launched, "1")) break;
            usleep(16666);
        }
    } else {
        ALOGD("NanoMenu: showing loading screen, waiting for RetroArch");
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
            for (int wait = 0; wait < 600; wait++) {
                for (int fd : mInputFds) {
                    while (read(fd, &drain_ev, sizeof(drain_ev)) == sizeof(drain_ev)) {}
                }
                drmFrameBegin();
                if (sDrmGlRotation && sDrmZeroCopy) {
                    glViewport(0, 0, sAhbTarget.w, sAhbTarget.h);
                } else {
                    glViewport(0, 0, mWidth, mHeight);
                }
                glClearColor(0.05f, 0.05f, 0.10f, 1.0f);
                glClear(GL_COLOR_BUFFER_BIT);
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
                const char* loadMsg = trDyn("Loading...");
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
    mSecondaryCreatedSize.clear();
    mSecondaryAppliedLssH.clear();

    // Signal the PSP live-app capture worker to stop now (drained before stopProcess
    // below). Then free its texture while the EGL context is still current. Freeing is
    // safe on this (render) thread because the worker never touches GL - it only writes
    // the CPU staging vector - and pspClockAppCaptureTick (the only GL uploader) runs on
    // this same thread, which is here in teardown, not mid-upload. No-op if never used.
    pspClockStopCaptureWorker(0);   // signal only; the bounded drain is before stopProcess
    pspClockMirrorStop();           // tear down the virtual-display mirror + un-flag the overlay
    if (mPspClockAppTex) { glDeleteTextures(1, &mPspClockAppTex); mPspClockAppTex = 0; }
    mPspClockAppTexW = mPspClockAppTexH = 0;
    mPspClockAppTexValid = false;
    // #5 dynamic-darken 8x8 luminance FBO (allocated lazily in pspClockSampleAppDim).
    if (mPspAppLumTex) { glDeleteTextures(1, &mPspAppLumTex); mPspAppLumTex = 0; }
    if (mPspAppLumFbo) { glDeleteFramebuffers(1, &mPspAppLumFbo); mPspAppLumFbo = 0; }

    eglMakeCurrent(mDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(mDisplay, mContext);
    eglDestroySurface(mDisplay, mSurface);
    mFlingerSurface.clear();
    mFlingerSurfaceControl.clear();
    eglTerminate(mDisplay);
    eglReleaseThread();
    // Ensure the PSP live-app capture worker is fully out of its binder call before
    // we tear down the binder threadpool below (a live captureDisplay racing
    // stopProcess() is the residual hazard). Bounded wait (listener wait is bounded);
    // worst case covers one callback+fence timeout. No-op if the worker never ran.
    pspClockStopCaptureWorker(1300);
    IPCThreadState::self()->stopProcess();
    return false;
}
} // namespace android
