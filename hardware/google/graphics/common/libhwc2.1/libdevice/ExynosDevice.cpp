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

#define ATRACE_TAG (ATRACE_TAG_GRAPHICS | ATRACE_TAG_HAL)

#include "ExynosDevice.h"

#include <aidl/android/hardware/graphics/composer3/IComposerCallback.h>
#include <sync/sync.h>
#include <sys/mman.h>
#include <unistd.h>

#include "BrightnessController.h"
#include "ExynosDeviceDrmInterface.h"
#include "ExynosDisplay.h"
#include "ExynosExternalDisplayModule.h"
#include "ExynosHWCDebug.h"
#include "ExynosHWCHelper.h"
#include "ExynosLayer.h"
#include "ExynosPrimaryDisplayModule.h"
#include "ExynosResourceManagerModule.h"
#include "ExynosVirtualDisplayModule.h"
#include "VendorGraphicBuffer.h"

using namespace vendor::graphics;
using namespace SOC_VERSION;
using aidl::android::hardware::graphics::composer3::IComposerCallback;

/**
 * ExynosDevice implementation
 */

class ExynosDevice;

extern void PixelDisplayInit(ExynosDisplay *exynos_display, const std::string_view instance_str);

static const std::map<const uint32_t, const std::string_view> pixelDisplayIntfName =
        {{getDisplayId(HWC_DISPLAY_PRIMARY, 0), "default"},
#ifdef USES_IDISPLAY_INTF_SEC
         {getDisplayId(HWC_DISPLAY_PRIMARY, 1), "secondary"}
#endif
};

int hwcDebug;
int hwcFenceDebug[FENCE_IP_ALL];
struct exynos_hwc_control exynosHWCControl;
struct update_time_info updateTimeInfo;
char fence_names[FENCE_MAX][32];

uint32_t getDeviceInterfaceType()
{
    if (access(DRM_DEVICE_PATH, F_OK) == NO_ERROR)
        return INTERFACE_TYPE_DRM;
    else
        return INTERFACE_TYPE_FB;
}

ExynosDevice::ExynosDevice(bool vrrApiSupported)
      : mGeometryChanged(0),
        mVsyncFd(-1),
        mExtVsyncFd(-1),
        mVsyncDisplayId(getDisplayId(HWC_DISPLAY_PRIMARY, 0)),
        mTimestamp(0),
        mDisplayMode(0),
        mInterfaceType(INTERFACE_TYPE_FB),
        mIsInTUI(false),
        mVrrApiSupported(vrrApiSupported) {
    exynosHWCControl.forceGpu = false;
    exynosHWCControl.windowUpdate = true;
    exynosHWCControl.forcePanic = false;
    exynosHWCControl.skipStaticLayers = true;
    exynosHWCControl.skipM2mProcessing = true;
    exynosHWCControl.skipResourceAssign = true;
    exynosHWCControl.multiResolution = true;
    exynosHWCControl.dumpMidBuf = false;
    exynosHWCControl.displayMode = DISPLAY_MODE_NUM;
    exynosHWCControl.setDDIScaler = false;
    exynosHWCControl.skipWinConfig = false;
    exynosHWCControl.skipValidate = true;
    exynosHWCControl.doFenceFileDump = false;
    exynosHWCControl.fenceTracer = 0;
    exynosHWCControl.sysFenceLogging = false;
    exynosHWCControl.useDynamicRecomp = false;

    hwcDebug = 0;

    mInterfaceType = getDeviceInterfaceType();
    ALOGD("HWC2 : %s : interface type(%d)", __func__, mInterfaceType);

    /*
     * This order should not be changed
     * new ExynosResourceManager ->
     * create displays and add them to the list ->
     * initDeviceInterface() ->
     * ExynosResourceManager::updateRestrictions()
     */
    mResourceManager = new ExynosResourceManagerModule(this);

    for (size_t i = 0; i < AVAILABLE_DISPLAY_UNITS.size(); i++) {
        exynos_display_t display_t = AVAILABLE_DISPLAY_UNITS[i];
        ExynosDisplay *exynos_display = NULL;
        ALOGD("Create display[%zu] type: %d, index: %d", i, display_t.type, display_t.index);
        switch(display_t.type) {
            case HWC_DISPLAY_PRIMARY:
                exynos_display =
                        (ExynosDisplay *)(new ExynosPrimaryDisplayModule(display_t.index, this,
                                                                         display_t.display_name));
                if(display_t.index == 0) {
                    exynos_display->mPlugState = true;
                    ExynosMPP::mainDisplayWidth = exynos_display->mXres;
                    if (ExynosMPP::mainDisplayWidth <= 0) {
                        ExynosMPP::mainDisplayWidth = 1440;
                    }
                    ExynosMPP::mainDisplayHeight = exynos_display->mYres;
                    if (ExynosMPP::mainDisplayHeight <= 0) {
                        ExynosMPP::mainDisplayHeight = 2560;
                    }
                }
                break;
            case HWC_DISPLAY_EXTERNAL:
                exynos_display =
                        (ExynosDisplay *)(new ExynosExternalDisplayModule(display_t.index, this,
                                                                          display_t.display_name));
                break;
            case HWC_DISPLAY_VIRTUAL:
                exynos_display =
                        (ExynosDisplay *)(new ExynosVirtualDisplayModule(display_t.index, this,
                                                                         display_t.display_name));
                mNumVirtualDisplay = 0;
                break;
            default:
                ALOGE("Unsupported display type(%d)", display_t.type);
                break;
        }
        exynos_display->mDeconNodeName.appendFormat("%s", display_t.decon_node_name.c_str());
        mDisplays.add(exynos_display);
        mDisplayMap.insert(std::make_pair(exynos_display->mDisplayId, exynos_display));

#ifndef FORCE_DISABLE_DR
        if (exynos_display->mDRDefault) exynosHWCControl.useDynamicRecomp = true;
#endif
    }

    memset(mCallbackInfos, 0, sizeof(mCallbackInfos));

    dynamicRecompositionThreadCreate();

    for (uint32_t i = 0; i < FENCE_IP_ALL; i++)
        hwcFenceDebug[i] = 0;

    for (uint32_t i = 0; i < FENCE_MAX; i++) {
        memset(fence_names[i], 0, sizeof(fence_names[0]));
        sprintf(fence_names[i], "_%2dh", i);
    }

    for (auto it : mDisplays) {
        std::string displayName = std::string(it->mDisplayName.c_str());
        it->mErrLogFileWriter.setPrefixName(displayName + "_hwc_error_log");
        it->mDebugDumpFileWriter.setPrefixName(displayName + "_hwc_debug");
        it->mFenceFileWriter.setPrefixName(displayName + "_hwc_fence_state");
        String8 saveString;
        saveString.appendFormat("ExynosDisplay %s is initialized", it->mDisplayName.c_str());
        saveErrorLog(saveString, it);
    }

    if (mInterfaceType == INTERFACE_TYPE_DRM) {
        setVBlankOffDelay(1);
    }

    initDeviceInterface(mInterfaceType);

    // registerRestrictions();
    mResourceManager->updateRestrictions();
    mResourceManager->initDisplays(mDisplays, mDisplayMap);

    char value[PROPERTY_VALUE_MAX];
    property_get("vendor.display.lbe.supported", value, "0");
    const bool lbe_supported = atoi(value) ? true : false;

    for (size_t i = 0; i < mDisplays.size(); i++) {
        if (mDisplays[i]->mType == HWC_DISPLAY_PRIMARY) {
            auto iter = pixelDisplayIntfName.find(getDisplayId(HWC_DISPLAY_PRIMARY, i));
            if (iter != pixelDisplayIntfName.end()) {
                PixelDisplayInit(mDisplays[i], iter->second);
                if (lbe_supported) {
                    mDisplays[i]->initLbe();
                }
            }
        }
    }

    mDisplayOffAsync = property_get_bool("vendor.display.async_off.supported", false);
}

void ExynosDevice::initDeviceInterface(uint32_t interfaceType)
{
    if (interfaceType == INTERFACE_TYPE_DRM) {
        mDeviceInterface = std::make_unique<ExynosDeviceDrmInterface>(this);
    } else {
        LOG_ALWAYS_FATAL("%s::Unknown interface type(%d)",
                __func__, interfaceType);
    }

    mDeviceInterface->init(this);

    /* Remove display when display interface is not valid */
    for (uint32_t i = 0; i < mDisplays.size();) {
        ExynosDisplay* display = mDisplays[i];
        display->initDisplayInterface(interfaceType);
        if (mDeviceInterface->initDisplayInterface(display->mDisplayInterface) != NO_ERROR) {
            ALOGD("Remove display[%d], Failed to initialize display interface", i);
            mDisplays.removeAt(i);
            mDisplayMap.erase(display->mDisplayId);
            delete display;
        } else {
            i++;
        }
    }

    // Call handleHotplug() to capture the initial coldplug state of each display.
    // This is necessary because the hotplug uevent handler created in postInit()
    // below does not get always triggered when HWC is restarting.
    handleHotplug();
    mDeviceInterface->postInit();
}

ExynosDevice::~ExynosDevice() {
    mDRLoopStatus = false;
    mDRThread.join();
    for(auto& display : mDisplays) {
        delete display;
    }
    mDisplays.clear();
}

bool ExynosDevice::isFirstValidate()
{
    for (uint32_t i = 0; i < mDisplays.size(); i++) {
        if ((mDisplays[i]->mType != HWC_DISPLAY_VIRTUAL) &&
            (!mDisplays[i]->mPowerModeState.has_value() ||
             (mDisplays[i]->mPowerModeState.value() == (hwc2_power_mode_t)HWC_POWER_MODE_OFF)))
            continue;
        if ((mDisplays[i]->mPlugState == true) &&
            ((mDisplays[i]->mRenderingState != RENDERING_STATE_NONE) &&
             (mDisplays[i]->mRenderingState != RENDERING_STATE_PRESENTED)))
            return false;
    }

    return true;
}

bool ExynosDevice::isLastValidate(ExynosDisplay *display)
{
    for (uint32_t i = 0; i < mDisplays.size(); i++) {
        if (mDisplays[i] == display)
            continue;
        if ((mDisplays[i]->mType != HWC_DISPLAY_VIRTUAL) &&
            (!mDisplays[i]->mPowerModeState.has_value() ||
             (mDisplays[i]->mPowerModeState.value() == (hwc2_power_mode_t)HWC_POWER_MODE_OFF)))
            continue;
        if ((mDisplays[i]->mPlugState == true) &&
            (mDisplays[i]->mRenderingState != RENDERING_STATE_VALIDATED) &&
            (mDisplays[i]->mRenderingState != RENDERING_STATE_ACCEPTED_CHANGE))
            return false;
    }
    return true;
}

bool ExynosDevice::hasOtherDisplayOn(ExynosDisplay *display) {
    for (uint32_t i = 0; i < mDisplays.size(); i++) {
        if (mDisplays[i] == display) continue;
        if ((mDisplays[i]->mType != HWC_DISPLAY_VIRTUAL) &&
            mDisplays[i]->mPowerModeState.has_value() &&
            (mDisplays[i]->mPowerModeState.value() != (hwc2_power_mode_t)HWC_POWER_MODE_OFF))
            return true;
    }
    return false;
}

bool ExynosDevice::isDynamicRecompositionThreadAlive()
{
    android_atomic_acquire_load(&mDRThreadStatus);
    return (mDRThreadStatus > 0);
}

void ExynosDevice::checkDynamicRecompositionThread()
{
    ATRACE_CALL();
    // If thread was destroyed, create thread and run. (resume status)
    if (isDynamicRecompositionThreadAlive() == false) {
        for (uint32_t i = 0; i < mDisplays.size(); i++) {
            if (mDisplays[i]->mDREnable) {
                dynamicRecompositionThreadCreate();
                return;
            }
        }
    } else {
    // If thread is running and all displays turnned off DR, destroy the thread.
        for (uint32_t i = 0; i < mDisplays.size(); i++) {
            if (mDisplays[i]->mDREnable)
                return;
        }
        ALOGI("Destroying dynamic recomposition thread");
        mDRLoopStatus = false;
        mDRWakeUpCondition.notify_one();
        mDRThread.join();
    }
}

void ExynosDevice::dynamicRecompositionThreadCreate()
{
    if (exynosHWCControl.useDynamicRecomp == true) {
        ALOGI("Creating dynamic recomposition thread");
        mDRLoopStatus = true;
        mDRThread = std::thread(&dynamicRecompositionThreadLoop, this);
    }
}

void *ExynosDevice::dynamicRecompositionThreadLoop(void *data)
{
    ExynosDevice *dev = (ExynosDevice *)data;
    ExynosDisplay *display[dev->mDisplays.size()];
    uint64_t event_cnt[dev->mDisplays.size()];

    for (uint32_t i = 0; i < dev->mDisplays.size(); i++) {
        display[i] = dev->mDisplays[i];
        event_cnt[i] = 0;
    }
    android_atomic_inc(&(dev->mDRThreadStatus));

    while (dev->mDRLoopStatus) {
        for (uint32_t i = 0; i < dev->mDisplays.size(); i++)
            event_cnt[i] = display[i]->mUpdateEventCnt;

        /*
         * If there is no update for more than 5s, favor the client composition mode.
         * If all other conditions are met, mode will be switched to client composition.
         */
        {
            std::unique_lock<std::mutex> lock(dev->mDRWakeUpMutex);
            dev->mDRWakeUpCondition.wait_for(lock, std::chrono::seconds(5));
            if (!dev->mDRLoopStatus) {
                break;
            }
        }
        for (uint32_t i = 0; i < dev->mDisplays.size(); i++) {
            if (display[i]->mDREnable &&
                display[i]->mPlugState == true &&
                event_cnt[i] == display[i]->mUpdateEventCnt) {
                if (display[i]->checkDynamicReCompMode() == DEVICE_2_CLIENT) {
                    display[i]->mUpdateEventCnt = 0;
                    display[i]->setGeometryChanged(GEOMETRY_DISPLAY_DYNAMIC_RECOMPOSITION);
                    dev->onRefresh(display[i]->mDisplayId);
                }
            }
        }
    }

    android_atomic_dec(&(dev->mDRThreadStatus));

    return NULL;
}

/**
 * Device Functions for HWC 2.0
 */

int32_t ExynosDevice::createVirtualDisplay(
        uint32_t width, uint32_t height, int32_t* /*android_pixel_format_t*/ format, ExynosDisplay* display) {
    ((ExynosVirtualDisplay*)display)->createVirtualDisplay(width, height, format);
    return 0;
}

/**
 * @param *display
 * @return int32_t
 */
int32_t ExynosDevice::destroyVirtualDisplay(ExynosDisplay* display) {
    ((ExynosVirtualDisplay *)display)->destroyVirtualDisplay();
    return 0;
}

void ExynosDevice::dump(uint32_t *outSize, char *outBuffer) {
    if (outSize == NULL) {
        ALOGE("%s:: outSize is null", __func__);
        return;
    }

    String8 result;
    dump(result);

    if (outBuffer == NULL) {
        *outSize = static_cast<uint32_t>(result.length());
    } else {
        if (*outSize == 0) {
            ALOGE("%s:: outSize is 0", __func__);
            return;
        }
        size_t copySize = min(static_cast<size_t>(*outSize), result.size());
        ALOGI("HWC dump:: resultSize(%zu), outSize(%d), copySize(%zu)", result.size(), *outSize,
              copySize);
        strlcpy(outBuffer, result.c_str(), copySize);
    }
}

void ExynosDevice::dump(String8 &result) {
    result.append("\n\n");

    struct tm* localTime = (struct tm*)localtime((time_t*)&updateTimeInfo.lastUeventTime.tv_sec);
    result.appendFormat("lastUeventTime(%02d:%02d:%02d.%03lu) lastTimestamp(%" PRIu64 ")\n",
            localTime->tm_hour, localTime->tm_min,
            localTime->tm_sec, updateTimeInfo.lastUeventTime.tv_usec/1000, mTimestamp);

    localTime = (struct tm*)localtime((time_t*)&updateTimeInfo.lastEnableVsyncTime.tv_sec);
    result.appendFormat("lastEnableVsyncTime(%02d:%02d:%02d.%03lu)\n",
            localTime->tm_hour, localTime->tm_min,
            localTime->tm_sec, updateTimeInfo.lastEnableVsyncTime.tv_usec/1000);

    localTime = (struct tm*)localtime((time_t*)&updateTimeInfo.lastDisableVsyncTime.tv_sec);
    result.appendFormat("lastDisableVsyncTime(%02d:%02d:%02d.%03lu)\n",
            localTime->tm_hour, localTime->tm_min,
            localTime->tm_sec, updateTimeInfo.lastDisableVsyncTime.tv_usec/1000);

    localTime = (struct tm*)localtime((time_t*)&updateTimeInfo.lastValidateTime.tv_sec);
    result.appendFormat("lastValidateTime(%02d:%02d:%02d.%03lu)\n",
            localTime->tm_hour, localTime->tm_min,
            localTime->tm_sec, updateTimeInfo.lastValidateTime.tv_usec/1000);

    localTime = (struct tm*)localtime((time_t*)&updateTimeInfo.lastPresentTime.tv_sec);
    result.appendFormat("lastPresentTime(%02d:%02d:%02d.%03lu)\n",
            localTime->tm_hour, localTime->tm_min,
            localTime->tm_sec, updateTimeInfo.lastPresentTime.tv_usec/1000);

    result.appendFormat("\n");
    mResourceManager->dump(result);

    result.appendFormat("special plane num: %d:\n", getSpecialPlaneNum());
    for (uint32_t index = 0; index < getSpecialPlaneNum(); index++) {
        result.appendFormat("\tindex: %d attribute 0x%" PRIx64 "\n", getSpecialPlaneId(index),
                            getSpecialPlaneAttr(index));
    }
    result.append("\n");

    for (size_t i = 0;i < mDisplays.size(); i++) {
        ExynosDisplay *display = mDisplays[i];
        if (display->mPlugState == true)
            display->dump(result);
    }
}

uint32_t ExynosDevice::getMaxVirtualDisplayCount() {
#ifdef USES_VIRTUAL_DISPLAY
    return 1;
#else
    return 0;
#endif
}

int32_t ExynosDevice::registerCallback (
        int32_t descriptor, hwc2_callback_data_t callbackData,
        hwc2_function_pointer_t point) {
    if (descriptor < 0 || descriptor > HWC2_CALLBACK_SEAMLESS_POSSIBLE)
        return HWC2_ERROR_BAD_PARAMETER;

    Mutex::Autolock lock(mDeviceCallbackMutex);
    mCallbackInfos[descriptor].callbackData = callbackData;
    mCallbackInfos[descriptor].funcPointer = point;

    /* Call hotplug callback for primary display*/
    if (descriptor == HWC2_CALLBACK_HOTPLUG) {
        HWC2_PFN_HOTPLUG callbackFunc =
                reinterpret_cast<HWC2_PFN_HOTPLUG>(mCallbackInfos[descriptor].funcPointer);
        if (callbackFunc != nullptr) {
            for (auto it : mDisplays) {
                if (it->mPlugState)
                    callbackFunc(callbackData, getDisplayId(it->mType, it->mIndex),
                            HWC2_CONNECTION_CONNECTED);
            }
        } else {
            // unregistering callback can be used as a sign of ComposerClient's death
            for (auto it : mDisplays) {
                it->cleanupAfterClientDeath();
            }
        }
    }
    /* TODO(b/265244856): called by register callback vsync. it's only hwc2. */
    if (descriptor == HWC2_CALLBACK_VSYNC)
        mResourceManager->doPreProcessing();

    return HWC2_ERROR_NONE;
}

bool ExynosDevice::isCallbackRegisteredLocked(int32_t descriptor) {
    if (descriptor < 0 || descriptor > HWC2_CALLBACK_SEAMLESS_POSSIBLE) {
        ALOGE("%s:: %d callback is unknown", __func__, descriptor);
        return false;
    }

    if (mCallbackInfos[descriptor].callbackData == nullptr ||
        mCallbackInfos[descriptor].funcPointer == nullptr) {
        ALOGE("%s:: %d callback is not registered", __func__, descriptor);
        return false;
    }

    return true;
}

bool ExynosDevice::isCallbackAvailable(int32_t descriptor) {
    Mutex::Autolock lock(mDeviceCallbackMutex);
    return isCallbackRegisteredLocked(descriptor);
}

using DisplayHotplugEvent = aidl::android::hardware::graphics::common::DisplayHotplugEvent;
DisplayHotplugEvent hotplug_event_to_aidl(bool connected, int hotplugErrorCode) {
    switch (hotplugErrorCode) {
        case 0:
            return connected ? DisplayHotplugEvent::CONNECTED : DisplayHotplugEvent::DISCONNECTED;
        case 1:
            return DisplayHotplugEvent::ERROR_INCOMPATIBLE_CABLE;
        default:
            return DisplayHotplugEvent::ERROR_UNKNOWN;
    }
}

void ExynosDevice::onHotPlug(uint32_t displayId, bool status, int hotplugErrorCode) {
    Mutex::Autolock lock(mDeviceCallbackMutex);

    // If the new HotplugEvent API is available, use it, otherwise fall back
    // to the old V2 API with onVsync hack, if necessary.
    const auto& hotplugEventCallback =
            mHwc3CallbackInfos.find(IComposerCallback::TRANSACTION_onHotplugEvent);
    if (hotplugEventCallback != mHwc3CallbackInfos.end()) {
        const auto& callbackInfo = hotplugEventCallback->second;
        if (callbackInfo.funcPointer != nullptr && callbackInfo.callbackData != nullptr) {
            auto callbackFunc = reinterpret_cast<
                    void (*)(hwc2_callback_data_t callbackData, hwc2_display_t hwcDisplay,
                             aidl::android::hardware::graphics::common::DisplayHotplugEvent)>(
                    callbackInfo.funcPointer);
            callbackFunc(callbackInfo.callbackData, displayId,
                         hotplug_event_to_aidl(status, hotplugErrorCode));
            return;
        }
    }

    if (!isCallbackRegisteredLocked(HWC2_CALLBACK_HOTPLUG)) return;

    if (hotplugErrorCode) {
        // We need to pass the error code to SurfaceFlinger, but we cannot modify the HWC
        // HAL interface, so for now we'll send the hotplug error via a onVsync callback with
        // a negative time value indicating the hotplug error.
        if (isCallbackRegisteredLocked(HWC2_CALLBACK_VSYNC_2_4)) {
            ALOGD("%s: hotplugErrorCode=%d sending to SF via onVsync_2_4", __func__,
                  hotplugErrorCode);
            hwc2_callback_data_t vsyncCallbackData =
                    mCallbackInfos[HWC2_CALLBACK_VSYNC_2_4].callbackData;
            HWC2_PFN_VSYNC_2_4 vsyncCallbackFunc = reinterpret_cast<HWC2_PFN_VSYNC_2_4>(
                    mCallbackInfos[HWC2_CALLBACK_VSYNC_2_4].funcPointer);
            vsyncCallbackFunc(vsyncCallbackData, displayId, -hotplugErrorCode, ~0);
            return;
        } else {
            ALOGW("%s: onVsync_2_4 is not registered, ignoring hotplugErrorCode=%d", __func__,
                  hotplugErrorCode);
            return;
        }
    }

    hwc2_callback_data_t callbackData = mCallbackInfos[HWC2_CALLBACK_HOTPLUG].callbackData;
    HWC2_PFN_HOTPLUG callbackFunc =
            reinterpret_cast<HWC2_PFN_HOTPLUG>(mCallbackInfos[HWC2_CALLBACK_HOTPLUG].funcPointer);
    callbackFunc(callbackData, displayId,
                 status ? HWC2_CONNECTION_CONNECTED : HWC2_CONNECTION_DISCONNECTED);
}

void ExynosDevice::onRefreshDisplays() {
    for (auto& display : mDisplays) {
         onRefresh(display->mDisplayId);
    }
}

void ExynosDevice::onRefresh(uint32_t displayId) {
    Mutex::Autolock lock(mDeviceCallbackMutex);

    if (!isCallbackRegisteredLocked(HWC2_CALLBACK_REFRESH)) return;

    if (!checkDisplayConnection(displayId)) return;

    ExynosDisplay *display = (ExynosDisplay *)getDisplay(displayId);

    if (!display->mPowerModeState.has_value() ||
             (display->mPowerModeState.value() == (hwc2_power_mode_t)HWC_POWER_MODE_OFF))
        return;

    hwc2_callback_data_t callbackData = mCallbackInfos[HWC2_CALLBACK_REFRESH].callbackData;
    HWC2_PFN_REFRESH callbackFunc =
            reinterpret_cast<HWC2_PFN_REFRESH>(mCallbackInfos[HWC2_CALLBACK_REFRESH].funcPointer);
    callbackFunc(callbackData, displayId);
}

void ExynosDevice::onVsync(uint32_t displayId, int64_t timestamp) {
    Mutex::Autolock lock(mDeviceCallbackMutex);

    if (!isCallbackRegisteredLocked(HWC2_CALLBACK_VSYNC)) return;

    hwc2_callback_data_t callbackData = mCallbackInfos[HWC2_CALLBACK_VSYNC].callbackData;
    HWC2_PFN_VSYNC callbackFunc =
            reinterpret_cast<HWC2_PFN_VSYNC>(mCallbackInfos[HWC2_CALLBACK_VSYNC].funcPointer);
    callbackFunc(callbackData, displayId, timestamp);
}

bool ExynosDevice::onVsync_2_4(uint32_t displayId, int64_t timestamp, uint32_t vsyncPeriod) {
    Mutex::Autolock lock(mDeviceCallbackMutex);

    if (!isCallbackRegisteredLocked(HWC2_CALLBACK_VSYNC_2_4)) return false;

    hwc2_callback_data_t callbackData = mCallbackInfos[HWC2_CALLBACK_VSYNC_2_4].callbackData;
    HWC2_PFN_VSYNC_2_4 callbackFunc = reinterpret_cast<HWC2_PFN_VSYNC_2_4>(
            mCallbackInfos[HWC2_CALLBACK_VSYNC_2_4].funcPointer);
    callbackFunc(callbackData, displayId, timestamp, vsyncPeriod);

    return true;
}

void ExynosDevice::onVsyncPeriodTimingChanged(uint32_t displayId,
                                              hwc_vsync_period_change_timeline_t *timeline) {
    Mutex::Autolock lock(mDeviceCallbackMutex);

    if (!timeline) {
        ALOGE("vsync period change timeline is null");
        return;
    }

    if (!isCallbackRegisteredLocked(HWC2_CALLBACK_VSYNC_PERIOD_TIMING_CHANGED)) return;

    hwc2_callback_data_t callbackData =
            mCallbackInfos[HWC2_CALLBACK_VSYNC_PERIOD_TIMING_CHANGED].callbackData;
    HWC2_PFN_VSYNC_PERIOD_TIMING_CHANGED callbackFunc =
            reinterpret_cast<HWC2_PFN_VSYNC_PERIOD_TIMING_CHANGED>(
                    mCallbackInfos[HWC2_CALLBACK_VSYNC_PERIOD_TIMING_CHANGED].funcPointer);
    callbackFunc(callbackData, displayId, timeline);
}

void ExynosDevice::onContentProtectionUpdated(uint32_t displayId, HdcpLevels hdcpLevels) {
    Mutex::Autolock lock(mDeviceCallbackMutex);

    // Workaround to pass content protection updates to SurfaceFlinger
    // without changing HWC HAL interface.
    if (isCallbackRegisteredLocked(HWC2_CALLBACK_VSYNC_2_4)) {
        ALOGI("%s: displayId=%u hdcpLevels=%s sending to SF via onVsync_2_4", __func__, displayId,
              hdcpLevels.toString().c_str());
        hwc2_callback_data_t vsyncCallbackData =
                mCallbackInfos[HWC2_CALLBACK_VSYNC_2_4].callbackData;
        HWC2_PFN_VSYNC_2_4 vsyncCallbackFunc = reinterpret_cast<HWC2_PFN_VSYNC_2_4>(
                mCallbackInfos[HWC2_CALLBACK_VSYNC_2_4].funcPointer);
        int32_t connectedLevel = static_cast<int32_t>(hdcpLevels.connectedLevel);
        int32_t maxLevel = static_cast<int32_t>(hdcpLevels.maxLevel);
        int32_t timestampValue = (connectedLevel & 0xFF) | ((maxLevel & 0xFF) << 8);
        vsyncCallbackFunc(vsyncCallbackData, displayId, -timestampValue, ~1);
    } else
        ALOGW("%s: onVsync_2_4 is not registered, ignoring onContentProtectionUpdated", __func__);
}

void ExynosDevice::setHWCDebug(unsigned int debug)
{
    hwcDebug = debug;
}

uint32_t ExynosDevice::getHWCDebug()
{
    return hwcDebug;
}

void ExynosDevice::setHWCFenceDebug(uint32_t typeNum, uint32_t ipNum, uint32_t mode)
{
    if (typeNum > FENCE_TYPE_ALL || typeNum < 0 || ipNum > FENCE_IP_ALL || ipNum < 0
            || mode > 1 || mode < 0) {
        ALOGE("%s:: input is not valid type(%u), IP(%u), mode(%d)", __func__, typeNum, ipNum, mode);
        return;
    }

    uint32_t value = 0;

    if (typeNum == FENCE_TYPE_ALL)
        value = (1 << FENCE_TYPE_ALL) - 1;
    else
        value = 1 << typeNum;

    if (ipNum == FENCE_IP_ALL) {
        for (uint32_t i = 0; i < FENCE_IP_ALL; i++) {
            if (mode)
                hwcFenceDebug[i] |= value;
            else
                hwcFenceDebug[i] &= (~value);
        }
    } else {
        if (mode)
            hwcFenceDebug[ipNum] |= value;
        else
            hwcFenceDebug[ipNum] &= (~value);
    }

}

void ExynosDevice::getHWCFenceDebug()
{
    for (uint32_t i = 0; i < FENCE_IP_ALL; i++)
        ALOGE("[HWCFenceDebug] IP_Number(%d) : Debug(%x)", i, hwcFenceDebug[i]);
}

void ExynosDevice::setHWCControl(uint32_t displayId, uint32_t ctrl, int32_t val) {
    ExynosDisplay *exynosDisplay = NULL;
    switch (ctrl) {
        case HWC_CTL_FORCE_GPU:
            ALOGI("%s::HWC_CTL_FORCE_GPU on/off=%d", __func__, val);
            exynosHWCControl.forceGpu = (unsigned int)val;
            setGeometryChanged(GEOMETRY_DEVICE_CONFIG_CHANGED);
            onRefresh(displayId);
            break;
        case HWC_CTL_WINDOW_UPDATE:
            ALOGI("%s::HWC_CTL_WINDOW_UPDATE on/off=%d", __func__, val);
            exynosHWCControl.windowUpdate = (unsigned int)val;
            setGeometryChanged(GEOMETRY_DEVICE_CONFIG_CHANGED);
            onRefresh(displayId);
            break;
        case HWC_CTL_FORCE_PANIC:
            ALOGI("%s::HWC_CTL_FORCE_PANIC on/off=%d", __func__, val);
            exynosHWCControl.forcePanic = (unsigned int)val;
            setGeometryChanged(GEOMETRY_DEVICE_CONFIG_CHANGED);
            break;
        case HWC_CTL_SKIP_STATIC:
            ALOGI("%s::HWC_CTL_SKIP_STATIC on/off=%d", __func__, val);
            exynosHWCControl.skipStaticLayers = (unsigned int)val;
            setGeometryChanged(GEOMETRY_DEVICE_CONFIG_CHANGED);
            break;
        case HWC_CTL_SKIP_M2M_PROCESSING:
            ALOGI("%s::HWC_CTL_SKIP_M2M_PROCESSING on/off=%d", __func__, val);
            exynosHWCControl.skipM2mProcessing = (unsigned int)val;
            setGeometryChanged(GEOMETRY_DEVICE_CONFIG_CHANGED);
            break;
        case HWC_CTL_SKIP_RESOURCE_ASSIGN:
            ALOGI("%s::HWC_CTL_SKIP_RESOURCE_ASSIGN on/off=%d", __func__, val);
            exynosHWCControl.skipResourceAssign = (unsigned int)val;
            setGeometryChanged(GEOMETRY_DEVICE_CONFIG_CHANGED);
            onRefreshDisplays();
            break;
        case HWC_CTL_SKIP_VALIDATE:
            ALOGI("%s::HWC_CTL_SKIP_VALIDATE on/off=%d", __func__, val);
            exynosHWCControl.skipValidate = (unsigned int)val;
            setGeometryChanged(GEOMETRY_DEVICE_CONFIG_CHANGED);
            onRefreshDisplays();
            break;
        case HWC_CTL_DUMP_MID_BUF:
            ALOGI("%s::HWC_CTL_DUMP_MID_BUF on/off=%d", __func__, val);
            exynosHWCControl.dumpMidBuf = (unsigned int)val;
            setGeometryChanged(GEOMETRY_DEVICE_CONFIG_CHANGED);
            onRefreshDisplays();
            break;
        case HWC_CTL_CAPTURE_READBACK:
            captureScreenWithReadback(displayId);
            break;
        case HWC_CTL_DISPLAY_MODE:
            ALOGI("%s::HWC_CTL_DISPLAY_MODE mode=%d", __func__, val);
            setDisplayMode((uint32_t)val);
            setGeometryChanged(GEOMETRY_DEVICE_CONFIG_CHANGED);
            onRefreshDisplays();
            break;
        // Support DDI scalser {
        case HWC_CTL_DDI_RESOLUTION_CHANGE:
            ALOGI("%s::HWC_CTL_DDI_RESOLUTION_CHANGE mode=%d", __func__, val);
            exynosDisplay = (ExynosDisplay *)getDisplay(displayId);
            uint32_t width, height;

            /* TODO: Add branch here for each resolution/index */
            switch(val) {
            case 1:
            case 2:
            case 3:
            default:
                width = 1440; height = 2960;
                break;
            }

            if (exynosDisplay == NULL) {
                for (uint32_t i = 0; i < mDisplays.size(); i++) {
                    mDisplays[i]->setDDIScalerEnable(width, height);
                }
            } else {
                exynosDisplay->setDDIScalerEnable(width, height);
            }
            setGeometryChanged(GEOMETRY_DISPLAY_RESOLUTION_CHANGED);
            onRefreshDisplays();
            break;
        // } Support DDI scaler
        case HWC_CTL_ENABLE_COMPOSITION_CROP:
        case HWC_CTL_ENABLE_EXYNOSCOMPOSITION_OPT:
        case HWC_CTL_ENABLE_CLIENTCOMPOSITION_OPT:
        case HWC_CTL_USE_MAX_G2D_SRC:
        case HWC_CTL_ENABLE_HANDLE_LOW_FPS:
        case HWC_CTL_ENABLE_EARLY_START_MPP:
            exynosDisplay = (ExynosDisplay *)getDisplay(displayId);
            if (exynosDisplay == NULL) {
                for (uint32_t i = 0; i < mDisplays.size(); i++) {
                    mDisplays[i]->setHWCControl(ctrl, val);
                }
            } else {
                exynosDisplay->setHWCControl(ctrl, val);
            }
            setGeometryChanged(GEOMETRY_DEVICE_CONFIG_CHANGED);
            onRefreshDisplays();
            break;
        case HWC_CTL_DYNAMIC_RECOMP:
            ALOGI("%s::HWC_CTL_DYNAMIC_RECOMP on/off = %d", __func__, val);
            setDynamicRecomposition(displayId, (unsigned int)val);
            break;
        case HWC_CTL_ENABLE_FENCE_TRACER:
            ALOGI("%s::HWC_CTL_ENABLE_FENCE_TRACER on/off=%d", __func__, val);
            exynosHWCControl.fenceTracer = (unsigned int)val;
            break;
        case HWC_CTL_SYS_FENCE_LOGGING:
            ALOGI("%s::HWC_CTL_SYS_FENCE_LOGGING on/off=%d", __func__, val);
            exynosHWCControl.sysFenceLogging = (unsigned int)val;
            break;
        case HWC_CTL_DO_FENCE_FILE_DUMP:
            ALOGI("%s::HWC_CTL_DO_FENCE_FILE_DUMP on/off=%d", __func__, val);
            exynosHWCControl.doFenceFileDump = (unsigned int)val;
            break;
        default:
            ALOGE("%s: unsupported HWC_CTL (%d)", __func__, ctrl);
            break;
    }
}

void ExynosDevice::setDisplayMode(uint32_t displayMode)
{
    exynosHWCControl.displayMode = displayMode;
}

void ExynosDevice::setDynamicRecomposition(uint32_t displayId, unsigned int on) {
    exynosHWCControl.useDynamicRecomp = on;
    ExynosDisplay *display = getDisplay(displayId);
    if (display) {
        display->mDRDefault = on;
        display->mDREnable = on;
        onRefresh(displayId);
    }
}

bool ExynosDevice::checkDisplayConnection(uint32_t displayId)
{
	ExynosDisplay *display = getDisplay(displayId);

    if (!display)
        return false;
    else
        return display->mPlugState;
}

bool ExynosDevice::checkNonInternalConnection()
{
    for (uint32_t i = 0; i < mDisplays.size(); i++) {
        switch(mDisplays[i]->mType) {
            case HWC_DISPLAY_PRIMARY:
                break;
            case HWC_DISPLAY_EXTERNAL:
            case HWC_DISPLAY_VIRTUAL:
                if (mDisplays[i]->mPlugState)
                    return true;
                break;
            default:
                break;
        }
    }
    return false;
}

void ExynosDevice::getCapabilitiesLegacy(uint32_t *outCount, int32_t *outCapabilities) {
    uint32_t capabilityNum = 0;
#ifdef HWC_SUPPORT_COLOR_TRANSFORM
    capabilityNum++;
#endif
#ifndef HWC_NO_SUPPORT_SKIP_VALIDATE
    capabilityNum++;
#endif
    if (outCapabilities == NULL) {
        *outCount = capabilityNum;
        return;
    }
    if (capabilityNum != *outCount) {
        ALOGE("%s:: invalid outCount(%d), should be(%d)", __func__, *outCount, capabilityNum);
        return;
    }
#if defined(HWC_SUPPORT_COLOR_TRANSFORM) || !defined(HWC_NO_SUPPORT_SKIP_VALIDATE)
    uint32_t index = 0;
#endif
#ifdef HWC_SUPPORT_COLOR_TRANSFORM
    outCapabilities[index++] = HWC2_CAPABILITY_SKIP_CLIENT_COLOR_TRANSFORM;
#endif
#ifndef HWC_NO_SUPPORT_SKIP_VALIDATE
    outCapabilities[index++] = HWC2_CAPABILITY_SKIP_VALIDATE;
#endif
    return;
}

void ExynosDevice::getCapabilities(uint32_t *outCount, int32_t *outCapabilities) {
    uint32_t capabilityNum = 0;
#ifdef HWC_SUPPORT_COLOR_TRANSFORM
    capabilityNum++;
#endif
    if (outCapabilities == NULL) {
        *outCount = capabilityNum;
        return;
    }
    if (capabilityNum != *outCount) {
        ALOGE("%s:: invalid outCount(%d), should be(%d)", __func__, *outCount, capabilityNum);
        return;
    }
#if defined(HWC_SUPPORT_COLOR_TRANSFORM)
    uint32_t index = 0;
    outCapabilities[index++] = HWC2_CAPABILITY_SKIP_CLIENT_COLOR_TRANSFORM;
#endif
    return;
}

void ExynosDevice::clearGeometryChanged()
{
    mGeometryChanged = 0;
}

bool ExynosDevice::canSkipValidate()
{
    /*
     * This should be called by presentDisplay()
     * when presentDisplay() is called without validateDisplay() call
     */

    int ret = 0;
    if (exynosHWCControl.skipValidate == false)
        return false;

    for (uint32_t i = 0; i < mDisplays.size(); i++) {
        /*
         * Check all displays.
         * Resource assignment can have problem if validateDisplay is skipped
         * on only some displays.
         * All display's validateDisplay should be skipped or all display's validateDisplay
         * should not be skipped.
         */
        if (mDisplays[i]->mPlugState && mDisplays[i]->mPowerModeState.has_value() &&
            mDisplays[i]->mPowerModeState.value() != HWC2_POWER_MODE_OFF) {
            /*
             * presentDisplay is called without validateDisplay.
             * Call functions that should be called in validateDiplay
             */
            mDisplays[i]->doPreProcessing();
            mDisplays[i]->checkLayerFps();

            if ((ret = mDisplays[i]->canSkipValidate()) != NO_ERROR) {
                ALOGD_AND_ATRACE_NAME(eDebugSkipValidate,
                                      "Display[%d] can't skip validate (%d), renderingState(%d), "
                                      "geometryChanged(0x%" PRIx64 ")",
                                      mDisplays[i]->mDisplayId, ret, mDisplays[i]->mRenderingState,
                                      mGeometryChanged);
                return false;
            } else {
                HDEBUGLOGD(eDebugSkipValidate, "Display[%d] can skip validate (%d), renderingState(%d), geometryChanged(0x%" PRIx64 ")",
                        mDisplays[i]->mDisplayId, ret,
                        mDisplays[i]->mRenderingState, mGeometryChanged);
            }
        }
    }
    return true;
}

bool ExynosDevice::validateFences(ExynosDisplay *display) {
    return mFenceTracker.validateFences(display);
}

void ExynosDevice::compareVsyncPeriod() {
    /* TODO(b/265244856): to clarify what purpose of the function */
    ExynosDisplay *primary_display = getDisplay(getDisplayId(HWC_DISPLAY_PRIMARY, 0));
    ExynosDisplay *external_display = getDisplay(getDisplayId(HWC_DISPLAY_EXTERNAL, 0));

    mVsyncDisplayId = getDisplayId(HWC_DISPLAY_PRIMARY, 0);

    if ((external_display == nullptr) ||
        (!external_display->mPowerModeState.has_value() ||
         (external_display->mPowerModeState.value() == HWC2_POWER_MODE_OFF))) {
        return;
    } else if (!primary_display->mPowerModeState.has_value() ||
               (primary_display->mPowerModeState.value() == HWC2_POWER_MODE_OFF)) {
        mVsyncDisplayId = getDisplayId(HWC_DISPLAY_EXTERNAL, 0);
        return;
    } else if (primary_display->mPowerModeState.has_value() &&
               ((primary_display->mPowerModeState.value() == HWC2_POWER_MODE_DOZE) ||
                (primary_display->mPowerModeState.value() == HWC2_POWER_MODE_DOZE_SUSPEND)) &&
               (external_display->mVsyncPeriod >= DOZE_VSYNC_PERIOD)) { /*30fps*/
        mVsyncDisplayId = getDisplayId(HWC_DISPLAY_EXTERNAL, 0);
        return;
    } else if (primary_display->mVsyncPeriod <= external_display->mVsyncPeriod) {
        mVsyncDisplayId = getDisplayId(HWC_DISPLAY_EXTERNAL, 0);
        return;
    }

    return;
}

ExynosDevice::captureReadbackClass::captureReadbackClass(
        ExynosDevice *device) :
    mDevice(device)
{
    if (device == nullptr)
        return;
}

ExynosDevice::captureReadbackClass::~captureReadbackClass()
{
    VendorGraphicBufferMapper& gMapper(VendorGraphicBufferMapper::get());
    if (mBuffer != nullptr)
        gMapper.freeBuffer(mBuffer);

    if (mDevice != nullptr)
        mDevice->clearWaitingReadbackReqDone();
}


int32_t ExynosDevice::captureReadbackClass::allocBuffer(
        uint32_t format, uint32_t w, uint32_t h)
{
    VendorGraphicBufferAllocator& gAllocator(VendorGraphicBufferAllocator::get());

    uint32_t dstStride = 0;
    uint64_t usage = static_cast<uint64_t>(GRALLOC1_CONSUMER_USAGE_HWCOMPOSER |
            GRALLOC1_CONSUMER_USAGE_CPU_READ_OFTEN);

    status_t error = NO_ERROR;
    error = gAllocator.allocate(w, h, format, 1, usage, &mBuffer, &dstStride, "HWC");
    if ((error != NO_ERROR) || (mBuffer == nullptr)) {
        ALOGE("failed to allocate destination buffer(%dx%d): %d",
                w, h, error);
        return static_cast<int32_t>(error);
    }
    return NO_ERROR;
}

void  ExynosDevice::captureReadbackClass::saveToFile(const String8 &fileName)
{
    if (mBuffer == nullptr) {
        ALOGE("%s:: buffer is null", __func__);
        return;
    }

    char filePath[MAX_DEV_NAME] = {0};
    VendorGraphicBufferMeta gmeta(mBuffer);

    snprintf(filePath, MAX_DEV_NAME,
            "%s/%s", WRITEBACK_CAPTURE_PATH, fileName.c_str());
    FILE *fp = fopen(filePath, "w");
    if (fp) {
        uint32_t writeSize =
            gmeta.stride * gmeta.vstride * formatToBpp(gmeta.format)/8;
        void *writebackData = mmap(0, writeSize,
                PROT_READ|PROT_WRITE, MAP_SHARED, gmeta.fd, 0);
        if (writebackData != MAP_FAILED && writebackData != NULL) {
            size_t result = fwrite(writebackData, writeSize, 1, fp);
            munmap(writebackData, writeSize);
            ALOGD("Success to write %zu data, size(%d)", result, writeSize);
        } else {
            ALOGE("Fail to mmap");
        }
        fclose(fp);
    } else {
        ALOGE("Fail to open %s", filePath);
    }
}

void ExynosDevice::signalReadbackDone()
{
    if (mIsWaitingReadbackReqDone) {
        Mutex::Autolock lock(mCaptureMutex);
        mCaptureCondition.signal();
    }
}

void ExynosDevice::captureScreenWithReadback(uint32_t displayId) {
    ExynosDisplay *display = getDisplay(displayId);
    if (display == nullptr) {
        ALOGE("There is no display(%d)", displayId);
        return;
    }

    int32_t outFormat;
    int32_t outDataspace;
    int32_t ret = 0;
    if ((ret = display->getReadbackBufferAttributes(
                &outFormat, &outDataspace)) != HWC2_ERROR_NONE) {
        ALOGE("getReadbackBufferAttributes fail, ret(%d)", ret);
        return;
    }

    captureReadbackClass captureClass(this);
    if ((ret = captureClass.allocBuffer(outFormat, display->mXres, display->mYres))
            != NO_ERROR) {
        return;
    }

    mIsWaitingReadbackReqDone = true;

    if (display->setReadbackBuffer(captureClass.getBuffer(), -1, true) != HWC2_ERROR_NONE) {
        ALOGE("setReadbackBuffer fail");
        return;
    }

    /* Update screen */
    onRefresh(displayId);

    /* Wait for handling readback */
    uint32_t waitPeriod = display->mVsyncPeriod * 3;
    {
        Mutex::Autolock lock(mCaptureMutex);
        status_t err = mCaptureCondition.waitRelative(
                mCaptureMutex, us2ns(waitPeriod));
        if (err == TIMED_OUT) {
            ALOGE("timeout, readback is not requested");
            return;
        } else if (err != NO_ERROR) {
            ALOGE("error waiting for readback request: %s (%d)", strerror(-err), err);
            return;
        } else {
            ALOGD("readback request is done");
        }
    }

    int32_t fence = -1;
    if (display->getReadbackBufferFence(&fence) != HWC2_ERROR_NONE) {
        ALOGE("getReadbackBufferFence fail");
        return;
    }
    if (sync_wait(fence, 1000) < 0) {
        ALOGE("sync wait error, fence(%d)", fence);
    }
    hwcFdClose(fence);

    String8 fileName;
    time_t curTime = time(NULL);
    struct tm *tm = localtime(&curTime);
    fileName.appendFormat("capture_format%d_%dx%d_%04d-%02d-%02d_%02d_%02d_%02d.raw",
            outFormat, display->mXres, display->mYres,
            tm->tm_year + 1900, tm->tm_mon + 1, tm->tm_mday,
            tm->tm_hour, tm->tm_min, tm->tm_sec);
    captureClass.saveToFile(fileName);
}

int32_t ExynosDevice::setDisplayDeviceMode(int32_t display_id, int32_t mode)
{
    int32_t ret = HWC2_ERROR_NONE;

    for (size_t i = 0; i < mDisplays.size(); i++) {
        if (mDisplays[i]->mType == HWC_DISPLAY_PRIMARY && mDisplays[i]->mDisplayId == display_id) {
            if (mode == static_cast<int32_t>(ext_hwc2_power_mode_t::PAUSE) ||
                mode == static_cast<int32_t>(ext_hwc2_power_mode_t::RESUME)) {
                ret = mDisplays[i]->setPowerMode(mode);
                if (mode == static_cast<int32_t>(ext_hwc2_power_mode_t::RESUME) &&
                    ret == HWC2_ERROR_NONE) {
                    onRefresh(display_id);
                }
                return ret;
            } else {
                return HWC2_ERROR_UNSUPPORTED;
            }
        }
    }
    return HWC2_ERROR_UNSUPPORTED;
}

int32_t ExynosDevice::setPanelGammaTableSource(int32_t display_id, int32_t type, int32_t source) {
    if (display_id < HWC_DISPLAY_PRIMARY || display_id >= HWC_NUM_DISPLAY_TYPES) {
        ALOGE("invalid display %d", display_id);
        return HWC2_ERROR_BAD_DISPLAY;
    }

    if (type < static_cast<int32_t>(DisplayType::DISPLAY_PRIMARY) ||
        type >= static_cast<int32_t>(DisplayType::DISPLAY_MAX)) {
        ALOGE("invalid display type %d", type);
        return HWC2_ERROR_BAD_PARAMETER;
    }

    if (source < static_cast<int32_t>(PanelGammaSource::GAMMA_DEFAULT) ||
        source >= static_cast<int32_t>(PanelGammaSource::GAMMA_TYPES)) {
        ALOGE("invalid gamma source %d", source);
        return HWC2_ERROR_BAD_PARAMETER;
    }

    return mDisplays[display_id]->SetCurrentPanelGammaSource(static_cast<DisplayType>(type),
                                                             static_cast<PanelGammaSource>(source));
}

void ExynosDevice::getLayerGenericMetadataKey(uint32_t __unused keyIndex,
        uint32_t* outKeyLength, char* __unused outKey, bool* __unused outMandatory)
{
    *outKeyLength = 0;
    return;
}

void ExynosDevice::setVBlankOffDelay(const int vblankOffDelay) {
    static constexpr const char *kVblankOffDelayPath = "/sys/module/drm/parameters/vblankoffdelay";

    writeIntToFile(kVblankOffDelayPath, vblankOffDelay);
}

uint32_t ExynosDevice::getWindowPlaneNum()
{
    /*
     * ExynosDevice supports DPU Window Composition.
     * The number of windows can be composited is depends on the number of DPP planes.
     */
    return mDeviceInterface->getNumDPPChs();
}

uint32_t ExynosDevice::getSpecialPlaneNum()
{
    /*
     * ExynosDevice might support something special purpose planes.
     * These planes are different with DPP planes.
     */
    return mDeviceInterface->getNumSPPChs();
}

uint32_t ExynosDevice::getSpecialPlaneNum(uint32_t /*displayId*/) {
    /*
     * TODO: create the query function for each display
     */
    return mDeviceInterface->getNumSPPChs();
}

uint32_t ExynosDevice::getSpecialPlaneId(uint32_t index)
{
    return mDeviceInterface->getSPPChId(index);
}

uint64_t ExynosDevice::getSpecialPlaneAttr(uint32_t index)
{
    return mDeviceInterface->getSPPChAttr(index);
}

int32_t ExynosDevice::registerHwc3Callback(uint32_t descriptor, hwc2_callback_data_t callbackData,
                                           hwc2_function_pointer_t point) {
    Mutex::Autolock lock(mDeviceCallbackMutex);
    mHwc3CallbackInfos[descriptor].callbackData = callbackData;
    mHwc3CallbackInfos[descriptor].funcPointer = point;

    return HWC2_ERROR_NONE;
}

void ExynosDevice::onVsyncIdle(hwc2_display_t displayId) {
    Mutex::Autolock lock(mDeviceCallbackMutex);
    const auto &idleCallback = mHwc3CallbackInfos.find(IComposerCallback::TRANSACTION_onVsyncIdle);

    if (idleCallback == mHwc3CallbackInfos.end()) return;

    const auto &callbackInfo = idleCallback->second;
    if (callbackInfo.funcPointer == nullptr || callbackInfo.callbackData == nullptr) return;

    auto callbackFunc =
            reinterpret_cast<void (*)(hwc2_callback_data_t callbackData,
                                      hwc2_display_t hwcDisplay)>(callbackInfo.funcPointer);
    callbackFunc(callbackInfo.callbackData, displayId);
}

void ExynosDevice::handleHotplug() {
    bool hpdStatus = false;

    for (size_t i = 0; i < mDisplays.size(); i++) {
        if (mDisplays[i] == nullptr) {
            continue;
        }

        // Lock mDisplayMutex during hotplug processing.
        // Must-have for unplug handling so that in-flight calls to
        // validateDisplay() and presentDisplay() don't race with
        // the display being removed.
        Mutex::Autolock lock(mDisplays[i]->mDisplayMutex);

        if (mDisplays[i]->checkHotplugEventUpdated(hpdStatus)) {
            mDisplays[i]->handleHotplugEvent(hpdStatus);
            mDisplays[i]->hotplug();
            mDisplays[i]->invalidate();
        }
    }
}

void ExynosDevice::onRefreshRateChangedDebug(hwc2_display_t displayId, uint32_t vsyncPeriod,
                                             uint32_t refreshPeriod) {
    Mutex::Autolock lock(mDeviceCallbackMutex);
    const auto &refreshRateCallback =
            mHwc3CallbackInfos.find(IComposerCallback::TRANSACTION_onRefreshRateChangedDebug);

    if (refreshRateCallback == mHwc3CallbackInfos.end()) return;

    const auto &callbackInfo = refreshRateCallback->second;
    if (callbackInfo.funcPointer == nullptr || callbackInfo.callbackData == nullptr) return;

    auto callbackFunc =
            reinterpret_cast<void (*)(hwc2_callback_data_t callbackData, hwc2_display_t hwcDisplay,
                                      hwc2_vsync_period_t, int32_t)>(callbackInfo.funcPointer);
    callbackFunc(callbackInfo.callbackData, displayId, vsyncPeriod, refreshPeriod ?: vsyncPeriod);
}
