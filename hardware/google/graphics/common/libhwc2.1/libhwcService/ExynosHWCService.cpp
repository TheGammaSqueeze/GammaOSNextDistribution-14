/*
 * Copyright (C) 2012 The Android Open Source Project
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

#include "ExynosHWCService.h"

#include <chrono>

#include "ExynosExternalDisplay.h"
#include "ExynosVirtualDisplay.h"
#include "ExynosVirtualDisplayModule.h"
#include "android-base/macros.h"
#define HWC_SERVICE_DEBUG 0

namespace android {

ANDROID_SINGLETON_STATIC_INSTANCE(ExynosHWCService);

ExynosHWCService::ExynosHWCService()
      : mHWCService(NULL), mHWCCtx(NULL), bootFinishedCallback(NULL) {
    ALOGD_IF(HWC_SERVICE_DEBUG, "ExynosHWCService Constructor is called");
}

ExynosHWCService::~ExynosHWCService()
{
   ALOGD_IF(HWC_SERVICE_DEBUG, "ExynosHWCService Destructor is called");
}

int ExynosHWCService::addVirtualDisplayDevice()
{
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s", __func__);

    mHWCCtx->device->mNumVirtualDisplay++;

    return NO_ERROR;
}

int ExynosHWCService::destroyVirtualDisplayDevice()
{
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s", __func__);

    mHWCCtx->device->mNumVirtualDisplay--;

    return NO_ERROR;
}

int ExynosHWCService::setWFDMode(unsigned int mode)
{
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s::mode=%d", __func__, mode);
    for (uint32_t i = 0; i < mHWCCtx->device->mDisplays.size(); i++) {
        if (mHWCCtx->device->mDisplays[i]->mType == HWC_DISPLAY_VIRTUAL) {
            ExynosVirtualDisplay *virtualdisplay =
                (ExynosVirtualDisplay *)mHWCCtx->device->mDisplays[i];
            return virtualdisplay->setWFDMode(mode);
        }
    }
    return INVALID_OPERATION;
}

int ExynosHWCService::getWFDMode()
{
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s", __func__);
    for (uint32_t i = 0; i < mHWCCtx->device->mDisplays.size(); i++) {
        if (mHWCCtx->device->mDisplays[i]->mType == HWC_DISPLAY_VIRTUAL) {
            ExynosVirtualDisplay *virtualdisplay =
                (ExynosVirtualDisplay *)mHWCCtx->device->mDisplays[i];
            return virtualdisplay->getWFDMode();
        }
    }
    return INVALID_OPERATION;
}

int ExynosHWCService::sendWFDCommand(int32_t cmd, int32_t ext1, int32_t ext2)
{
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s::cmd=%d, ext1=%d, ext2=%d", __func__, cmd, ext1, ext2);
    for (uint32_t i = 0; i < mHWCCtx->device->mDisplays.size(); i++) {
        if (mHWCCtx->device->mDisplays[i]->mType == HWC_DISPLAY_VIRTUAL) {
            ExynosVirtualDisplay *virtualdisplay =
                (ExynosVirtualDisplay *)mHWCCtx->device->mDisplays[i];
            return virtualdisplay->sendWFDCommand(cmd, ext1, ext2);
        }
    }
    return INVALID_OPERATION;
}

int ExynosHWCService::setSecureVDSMode(unsigned int mode)
{
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s::mode=%d", __func__, mode);
    for (uint32_t i = 0; i < mHWCCtx->device->mDisplays.size(); i++) {
        if (mHWCCtx->device->mDisplays[i]->mType == HWC_DISPLAY_VIRTUAL) {
            ExynosVirtualDisplay *virtualdisplay =
                (ExynosVirtualDisplay *)mHWCCtx->device->mDisplays[i];
            return virtualdisplay->setSecureVDSMode(mode);
        }
    }
    return INVALID_OPERATION;
}

int ExynosHWCService::setWFDOutputResolution(unsigned int width, unsigned int height)
{
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s::width=%d, height=%d", __func__, width, height);

    for (uint32_t i = 0; i < mHWCCtx->device->mDisplays.size(); i++) {
        if (mHWCCtx->device->mDisplays[i]->mType == HWC_DISPLAY_VIRTUAL) {
            ExynosVirtualDisplay *virtualdisplay =
                (ExynosVirtualDisplay *)mHWCCtx->device->mDisplays[i];
            return virtualdisplay->setWFDOutputResolution(width, height);
        }
    }
    return INVALID_OPERATION;
}

int ExynosHWCService::getWFDOutputResolution(unsigned int* width, unsigned int* height) {
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s", __func__);
    if (UNLIKELY(width == nullptr || height == nullptr)) {
        ALOGE("%s: does not accept null pointers", __func__);
        return INVALID_OPERATION;
    }
    for (uint32_t i = 0; i < mHWCCtx->device->mDisplays.size(); i++) {
        if (mHWCCtx->device->mDisplays[i]->mType == HWC_DISPLAY_VIRTUAL) {
            ExynosVirtualDisplay *virtualdisplay =
                (ExynosVirtualDisplay *)mHWCCtx->device->mDisplays[i];
            virtualdisplay->getWFDOutputResolution(width, height);
            return NO_ERROR;
        }
    }
    *width = *height = 0;
    ALOGE("%s: no virtual display found", __func__);
    return INVALID_OPERATION;
}

void ExynosHWCService::setPresentationMode(bool use)
{
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s::PresentationMode=%s", __func__, use == false ? "false" : "true");
    for (uint32_t i = 0; i < mHWCCtx->device->mDisplays.size(); i++) {
        if (mHWCCtx->device->mDisplays[i]->mType == HWC_DISPLAY_VIRTUAL) {
            ExynosVirtualDisplay *virtualdisplay =
                (ExynosVirtualDisplay *)mHWCCtx->device->mDisplays[i];
            virtualdisplay->setPresentationMode(!!use);
            return;
        }
    }
}

int ExynosHWCService::getPresentationMode()
{
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s", __func__);
    for (uint32_t i = 0; i < mHWCCtx->device->mDisplays.size(); i++) {
        if (mHWCCtx->device->mDisplays[i]->mType == HWC_DISPLAY_VIRTUAL) {
            ExynosVirtualDisplay *virtualdisplay =
                (ExynosVirtualDisplay *)mHWCCtx->device->mDisplays[i];
            return virtualdisplay->getPresentationMode();
        }
    }
    return INVALID_OPERATION;
}

int ExynosHWCService::setVDSGlesFormat(int format)
{
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s::format=%d", __func__, format);

    for (uint32_t i = 0; i < mHWCCtx->device->mDisplays.size(); i++) {
        if (mHWCCtx->device->mDisplays[i]->mType == HWC_DISPLAY_VIRTUAL) {
            ExynosVirtualDisplay *virtualdisplay =
                (ExynosVirtualDisplay *)mHWCCtx->device->mDisplays[i];
            return virtualdisplay->setVDSGlesFormat(format);
        }
    }

    return INVALID_OPERATION;
}

int ExynosHWCService::getExternalDisplayConfigs()
{
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s", __func__);

    ExynosExternalDisplay *external_display =
        (ExynosExternalDisplay *)mHWCCtx->device->getDisplay(getDisplayId(HWC_DISPLAY_EXTERNAL, 0));

    if ((external_display != nullptr) &&
        (external_display->mHpdStatus == true)) {
        external_display->mDisplayInterface->dumpDisplayConfigs();
    }

    return NO_ERROR;
}

int ExynosHWCService::setExternalDisplayConfig(unsigned int index)
{
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s::config=%d", __func__, index);

    ExynosExternalDisplay *external_display =
        (ExynosExternalDisplay *)mHWCCtx->device->getDisplay(getDisplayId(HWC_DISPLAY_EXTERNAL, 0));

    if ((external_display != nullptr) &&
        (external_display->mHpdStatus == true)) {
        external_display->setActiveConfig(index);
    }

    return NO_ERROR;
}

int ExynosHWCService::setExternalVsyncEnabled(unsigned int index)
{
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s::config=%d", __func__, index);

    mHWCCtx->device->mVsyncDisplayId = index;
    ExynosExternalDisplay *external_display =
        (ExynosExternalDisplay *)mHWCCtx->device->getDisplay(getDisplayId(HWC_DISPLAY_EXTERNAL, 0));
    if (external_display != nullptr)
        external_display->setVsyncEnabled(index);

    return NO_ERROR;
}

int ExynosHWCService::getExternalHdrCapabilities()
{
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s", __func__);

    ExynosExternalDisplay *external_display =
        (ExynosExternalDisplay *)mHWCCtx->device->getDisplay(getDisplayId(HWC_DISPLAY_EXTERNAL, 0));

    if (external_display != nullptr)
        return external_display->mExternalHdrSupported;
    return 0;
}

void ExynosHWCService::setBootFinishedCallback(void (*callback)(ExynosHWCCtx *))
{
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s, callback %p", __func__, callback);
    bootFinishedCallback = callback;
}

void ExynosHWCService::setBootFinished() {
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s", __func__);
    if (bootFinishedCallback != NULL)
        bootFinishedCallback(mHWCCtx);
}

void ExynosHWCService::enableMPP(uint32_t physicalType, uint32_t physicalIndex, uint32_t logicalIndex, uint32_t enable)
{
    ALOGD("%s:: type(%d), index(%d, %d), enable(%d)",
            __func__, physicalType, physicalIndex, logicalIndex, enable);
    ExynosResourceManager::enableMPP(physicalType, physicalIndex, logicalIndex, enable);
    mHWCCtx->device->setGeometryChanged(GEOMETRY_DEVICE_CONFIG_CHANGED);
    mHWCCtx->device->onRefreshDisplays();
}

void ExynosHWCService::setScaleDownRatio(uint32_t physicalType,
        uint32_t physicalIndex, uint32_t logicalIndex, uint32_t scaleDownRatio)
{
    ALOGD("%s:: type(%d), index(%d, %d), scaleDownRatio(%d)",
            __func__, physicalType, physicalIndex, logicalIndex, scaleDownRatio);
    ExynosResourceManager::setScaleDownRatio(physicalType, physicalIndex, logicalIndex, scaleDownRatio);
    mHWCCtx->device->setGeometryChanged(GEOMETRY_DEVICE_CONFIG_CHANGED);
    mHWCCtx->device->onRefreshDisplays();
}

void ExynosHWCService::setLbeCtrl(uint32_t display_id, uint32_t state, uint32_t lux) {
    ALOGD("%s:: display_id(%d), state(%d), lux(%d)", __func__, display_id, state, lux);
    if (mHWCCtx) {
        auto display = mHWCCtx->device->getDisplay(display_id);

        if (display != nullptr) {
            display->setLbeState(static_cast<LbeState>(state));
            display->setLbeAmbientLight(lux);
        }
    }
}

void ExynosHWCService::setHWCDebug(int debug)
{
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s, debug %d", __func__, debug);
    mHWCCtx->device->setHWCDebug(debug);
}

uint32_t ExynosHWCService::getHWCDebug()
{
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s", __func__);
    return mHWCCtx->device->getHWCDebug();
}

void ExynosHWCService::setHWCFenceDebug(uint32_t fenceNum, uint32_t ipNum, uint32_t mode)
{
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s", __func__);
    mHWCCtx->device->setHWCFenceDebug(fenceNum, ipNum, mode);
}

void ExynosHWCService::getHWCFenceDebug()
{
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s", __func__);
    mHWCCtx->device->getHWCFenceDebug();
}

int ExynosHWCService::setHWCCtl(uint32_t display, uint32_t ctrl, int32_t val)
{
    int err = 0;
    switch (ctrl) {
    case HWC_CTL_FORCE_GPU:
    case HWC_CTL_WINDOW_UPDATE:
    case HWC_CTL_FORCE_PANIC:
    case HWC_CTL_SKIP_STATIC:
    case HWC_CTL_SKIP_M2M_PROCESSING:
    case HWC_CTL_SKIP_RESOURCE_ASSIGN:
    case HWC_CTL_SKIP_VALIDATE:
    case HWC_CTL_DUMP_MID_BUF:
    case HWC_CTL_CAPTURE_READBACK:
    case HWC_CTL_ENABLE_COMPOSITION_CROP:
    case HWC_CTL_ENABLE_EXYNOSCOMPOSITION_OPT:
    case HWC_CTL_ENABLE_CLIENTCOMPOSITION_OPT:
    case HWC_CTL_USE_MAX_G2D_SRC:
    case HWC_CTL_ENABLE_HANDLE_LOW_FPS:
    case HWC_CTL_ENABLE_EARLY_START_MPP:
    case HWC_CTL_DISPLAY_MODE:
    case HWC_CTL_DDI_RESOLUTION_CHANGE:
    case HWC_CTL_DYNAMIC_RECOMP:
    case HWC_CTL_ENABLE_FENCE_TRACER:
    case HWC_CTL_SYS_FENCE_LOGGING:
    case HWC_CTL_DO_FENCE_FILE_DUMP:
        ALOGI("%s::%d on/off=%d", __func__, ctrl, val);
        mHWCCtx->device->setHWCControl(display, ctrl, val);
        break;
    default:
        ALOGE("%s: unsupported HWC_CTL, (%d)", __func__, ctrl);
        err = -1;
        break;
    }
    return err;
}

int ExynosHWCService::setDDIScaler(uint32_t display_id, uint32_t width, uint32_t height) {
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s, width=%d, height=%d", __func__, width, height);
    if (mHWCCtx) {
        ExynosDisplay *display = (ExynosDisplay *)mHWCCtx->device->getDisplay(display_id);

        if (display == NULL)
            return -EINVAL;

        display->setDDIScalerEnable(width, height);
        return NO_ERROR;
    } else {
        ALOGE_IF(HWC_SERVICE_DEBUG, "Service is not exist");
        return -EINVAL;
    }
}

int ExynosHWCService::createServiceLocked()
{
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s::", __func__);
    sp<IServiceManager> sm = defaultServiceManager();
    sm->addService(String16("Exynos.HWCService"), mHWCService, false);
    if (sm->checkService(String16("Exynos.HWCService")) != NULL) {
        ALOGD_IF(HWC_SERVICE_DEBUG, "adding Exynos.HWCService succeeded");
        return 0;
    } else {
        ALOGE_IF(HWC_SERVICE_DEBUG, "adding Exynos.HWCService failed");
        return -1;
    }
}

ExynosHWCService *ExynosHWCService::getExynosHWCService()
{
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s::", __func__);
    ExynosHWCService& instance = ExynosHWCService::getInstance();
    Mutex::Autolock _l(instance.mLock);
    if (instance.mHWCService == NULL) {
        instance.mHWCService = &instance;
        int status = ExynosHWCService::getInstance().createServiceLocked();
        if (status != 0) {
            ALOGE_IF(HWC_SERVICE_DEBUG, "getExynosHWCService failed");
        }
    }
    return instance.mHWCService;
}

void ExynosHWCService::setExynosHWCCtx(ExynosHWCCtx *HWCCtx)
{
    ALOGD_IF(HWC_SERVICE_DEBUG, "%s, HWCCtx=%p", __func__, HWCCtx);
    if(HWCCtx) {
        mHWCCtx = HWCCtx;
    }
}

int32_t ExynosHWCService::setDisplayDeviceMode(int32_t display_id, int32_t mode)
{
    return mHWCCtx->device->setDisplayDeviceMode(display_id, mode);
}

int32_t ExynosHWCService::setPanelGammaTableSource(int32_t display_id, int32_t type,
                                                   int32_t source) {
    return mHWCCtx->device->setPanelGammaTableSource(display_id, type, source);
}

int32_t ExynosHWCService::setDisplayBrightness(int32_t display_id, float brightness) {
    if (brightness < 0 || brightness > 1.0)
        return -EINVAL;

    auto display = mHWCCtx->device->getDisplay(display_id);

    if (display != nullptr)
        return display->setDisplayBrightness(brightness);

    return -EINVAL;
}

int32_t ExynosHWCService::ignoreDisplayBrightnessUpdateRequests(int32_t displayId, bool ignore) {
    ALOGD("ExynosHWCService::%s() displayId(%u) ignore(%u)", __func__, displayId, ignore);

    auto display = mHWCCtx->device->getDisplay(displayId);

    if (display != nullptr)
        return display->ignoreBrightnessUpdateRequests(ignore);

    return -EINVAL;
}

int32_t ExynosHWCService::setDisplayBrightnessNits(const int32_t display_id, const float nits) {
    if (nits < 0)
        return -EINVAL;

    auto display = mHWCCtx->device->getDisplay(display_id);

    if (display != nullptr)
        return display->setBrightnessNits(nits);

    return -EINVAL;
}

int32_t ExynosHWCService::setDisplayBrightnessDbv(int32_t display_id, uint32_t dbv) {
    auto display = mHWCCtx->device->getDisplay(display_id);

    if (display != nullptr) {
        return display->setBrightnessDbv(dbv);
    } else {
        ALOGE("ExynosHWCService::%s() invalid display id: %d\n", __func__, display_id);
    }

    return -EINVAL;
}

int32_t ExynosHWCService::setDisplayLhbm(int32_t display_id, uint32_t on) {
    if (on > 1) return -EINVAL;

    auto display = mHWCCtx->device->getDisplay(display_id);

    if (display != nullptr) {
        display->setLhbmState(!!on);
        return NO_ERROR;
    }

    return -EINVAL;
}

int32_t ExynosHWCService::setMinIdleRefreshRate(uint32_t display_id, int32_t fps) {
    ALOGD("ExynosHWCService::%s() display_id(%u) fps(%d)", __func__, display_id, fps);

    auto display = mHWCCtx->device->getDisplay(display_id);

    if (display != nullptr) {
        return display->setMinIdleRefreshRate(fps, RrThrottleRequester::TEST);
    }

    return -EINVAL;
}

int32_t ExynosHWCService::setRefreshRateThrottle(uint32_t display_id, int32_t delayMs) {
    ALOGD("ExynosHWCService::%s() display_id(%u) delayMs(%d)", __func__, display_id, delayMs);

    auto display = mHWCCtx->device->getDisplay(display_id);

    if (display != nullptr) {
        return display
                ->setRefreshRateThrottleNanos(std::chrono::duration_cast<std::chrono::nanoseconds>(
                                                      std::chrono::milliseconds(delayMs))
                                                      .count(),
                                              RrThrottleRequester::TEST);
    }

    return -EINVAL;
}

int32_t ExynosHWCService::setDisplayRCDLayerEnabled(uint32_t displayIndex, bool enable) {
    ALOGD("ExynosHWCService::%s() displayIndex(%u) enable(%u)", __func__, displayIndex, enable);

    auto primaryDisplay =
            mHWCCtx->device->getDisplay(getDisplayId(HWC_DISPLAY_PRIMARY, displayIndex));
    if (primaryDisplay == nullptr) return -EINVAL;

    auto ret = primaryDisplay->setDebugRCDLayerEnabled(enable);

    mHWCCtx->device->setGeometryChanged(GEOMETRY_DEVICE_CONFIG_CHANGED);
    mHWCCtx->device->onRefresh(getDisplayId(HWC_DISPLAY_PRIMARY, displayIndex));

    return ret;
}

int32_t ExynosHWCService::triggerDisplayIdleEnter(uint32_t displayIndex,
                                                  uint32_t idleTeRefreshRate) {
    ALOGD("ExynosHWCService::%s() displayIndex(%u) idleTeRefreshRate(%u)", __func__, displayIndex,
          idleTeRefreshRate);

    auto primaryDisplay =
            mHWCCtx->device->getDisplay(getDisplayId(HWC_DISPLAY_PRIMARY, displayIndex));
    if (primaryDisplay == nullptr) return -EINVAL;

    mHWCCtx->device->onVsyncIdle(primaryDisplay->getId());
    primaryDisplay->handleDisplayIdleEnter(idleTeRefreshRate);

    return NO_ERROR;
}

int32_t ExynosHWCService::setDisplayDbm(int32_t display_id, uint32_t on) {
    if (on > 1) return -EINVAL;

    auto display = mHWCCtx->device->getDisplay(display_id);

    if (display == nullptr) return -EINVAL;

    ALOGD("ExynosHWCService::%s() display(%u) on=%d", __func__, display_id, on);
    display->setDbmState(!!on);
    mHWCCtx->device->onRefresh(display_id);
    return NO_ERROR;
}

int32_t ExynosHWCService::setDisplayMultiThreadedPresent(const int32_t& displayId,
                                                         const bool& enable) {
    auto display = mHWCCtx->device->getDisplay(displayId);

    if (display == nullptr) return -EINVAL;

    display->mDisplayControl.multiThreadedPresent = enable;
    ALOGD("ExynosHWCService::%s() display(%u) enable=%d", __func__, displayId, enable);
    return NO_ERROR;
}

int32_t ExynosHWCService::triggerRefreshRateIndicatorUpdate(uint32_t displayId,
                                                            uint32_t refreshRate) {
    auto display = mHWCCtx->device->getDisplay(displayId);

    if (display == nullptr) return -EINVAL;

    ALOGD("ExynosHWCService::%s() displayID(%u) refreshRate(%u)", __func__, displayId, refreshRate);
    if (display->mRefreshRateIndicatorHandler) {
        display->mRefreshRateIndicatorHandler->updateRefreshRate(refreshRate);
    }
    return NO_ERROR;
}

int32_t ExynosHWCService::dumpBuffers(uint32_t displayId, int32_t count) {
    auto display = mHWCCtx->device->getDisplay(displayId);

    if (display == nullptr) return -EINVAL;

    ALOGD("ExynosHWCService::%s() displayID(%u) count(%u)", __func__, displayId, count);
    display->mBufferDumpCount = count;
    display->mBufferDumpNum = 0;
    return NO_ERROR;
}

int32_t ExynosHWCService::setPresentTimeoutController(uint32_t displayId, uint32_t controllerType) {
    auto display = mHWCCtx->device->getDisplay(displayId);

    if (display == nullptr) return -EINVAL;
    display->setPresentTimeoutController(controllerType);

    return NO_ERROR;
}

int32_t ExynosHWCService::setPresentTimeoutParameters(
        uint32_t displayId, int __unused timeoutNs,
        const std::vector<std::pair<uint32_t, uint32_t>>& settings) {
    auto display = mHWCCtx->device->getDisplay(displayId);

    if (display == nullptr) return -EINVAL;
    display->setPresentTimeoutParameters(timeoutNs, settings);

    return NO_ERROR;
}

} //namespace android
