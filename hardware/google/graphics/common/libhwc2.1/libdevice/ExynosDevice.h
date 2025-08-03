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

#ifndef _EXYNOSDEVICE_H
#define _EXYNOSDEVICE_H

#include <aidl/android/hardware/drm/HdcpLevels.h>
#include <aidl/android/hardware/graphics/composer3/OverlayProperties.h>
#include <aidl/com/google/hardware/pixel/display/BnDisplay.h>
#include <cutils/atomic.h>
#include <displaycolor/displaycolor.h>
#include <hardware/hwcomposer2.h>
#include <hardware_legacy/uevent.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <utils/Condition.h>
#include <utils/Mutex.h>
#include <utils/Trace.h>
#include <utils/Vector.h>

#include <atomic>
#include <map>
#include <thread>

#include "ExynosDeviceInterface.h"
#include "ExynosHWC.h"
#include "ExynosHWCHelper.h"
#include "ExynosHWCModule.h"

#define MAX_DEV_NAME 128
#define ERROR_LOG_PATH0 "/data/vendor/log/hwc"
#define ERROR_LOG_PATH1 "/data/log"
#define ERR_LOG_SIZE    (1024*1024)     // 1MB
#define FENCE_ERR_LOG_SIZE    (1024*1024)     // 1MB

#ifndef DOZE_VSYNC_PERIOD
#define DOZE_VSYNC_PERIOD 33333333 // 30fps
#endif

#ifndef DRM_DEVICE_PATH
#define DRM_DEVICE_PATH "/dev/dri/card0"
#endif

#ifndef WRITEBACK_CAPTURE_PATH
#define WRITEBACK_CAPTURE_PATH "/data/vendor/log/hwc"
#endif

using ::aidl::android::hardware::drm::HdcpLevels;
using HbmState = ::aidl::com::google::hardware::pixel::display::HbmState;
using LbeState = ::aidl::com::google::hardware::pixel::display::LbeState;
using PanelCalibrationStatus = ::aidl::com::google::hardware::pixel::display::PanelCalibrationStatus;

using OverlayProperties = aidl::android::hardware::graphics::composer3::OverlayProperties;
using namespace android;

struct exynos_callback_info_t {
    hwc2_callback_data_t callbackData;
    hwc2_function_pointer_t funcPointer;
};

typedef struct exynos_hwc_control {
    uint32_t forceGpu;
    uint32_t windowUpdate;
    uint32_t forcePanic;
    uint32_t skipStaticLayers;
    uint32_t skipM2mProcessing;
    uint32_t skipResourceAssign;
    uint32_t multiResolution;
    uint32_t dumpMidBuf;
    uint32_t displayMode;
    uint32_t setDDIScaler;
    uint32_t useDynamicRecomp;
    uint32_t skipWinConfig;
    uint32_t skipValidate;
    uint32_t doFenceFileDump;
    uint32_t fenceTracer;
    uint32_t sysFenceLogging;
} exynos_hwc_control_t;

typedef struct update_time_info {
    struct timeval lastUeventTime;
    struct timeval lastEnableVsyncTime;
    struct timeval lastDisableVsyncTime;
    struct timeval lastValidateTime;
    struct timeval lastPresentTime;
} update_time_info_t;

enum {
    GEOMETRY_LAYER_TYPE_CHANGED               = 1ULL << 0,
    GEOMETRY_LAYER_DATASPACE_CHANGED          = 1ULL << 1,
    GEOMETRY_LAYER_DISPLAYFRAME_CHANGED       = 1ULL << 2,
    GEOMETRY_LAYER_SOURCECROP_CHANGED         = 1ULL << 3,
    GEOMETRY_LAYER_TRANSFORM_CHANGED          = 1ULL << 4,
    GEOMETRY_LAYER_ZORDER_CHANGED             = 1ULL << 5,
    GEOMETRY_LAYER_FPS_CHANGED                = 1ULL << 6,
    GEOMETRY_LAYER_FLAG_CHANGED               = 1ULL << 7,
    GEOMETRY_LAYER_PRIORITY_CHANGED           = 1ULL << 8,
    GEOMETRY_LAYER_COMPRESSED_CHANGED         = 1ULL << 9,
    GEOMETRY_LAYER_BLEND_CHANGED              = 1ULL << 10,
    GEOMETRY_LAYER_FORMAT_CHANGED             = 1ULL << 11,
    GEOMETRY_LAYER_DRM_CHANGED                = 1ULL << 12,
    GEOMETRY_LAYER_IGNORE_CHANGED             = 1ULL << 13,
    GEOMETRY_LAYER_WHITEPOINT_CHANGED         = 1ULL << 14,
    GEOMETRY_LAYER_FRONT_BUFFER_USAGE_CHANGED = 1ULL << 15,
    GEOMETRY_LAYER_UNKNOWN_CHANGED            = 1ULL << 16,
    /* 1ULL << 17 */
    /* 1ULL << 18 */
    /* 1ULL << 19 */
    GEOMETRY_DISPLAY_LAYER_ADDED              = 1ULL << 20,
    GEOMETRY_DISPLAY_LAYER_REMOVED            = 1ULL << 21,
    GEOMETRY_DISPLAY_CONFIG_CHANGED           = 1ULL << 22,
    GEOMETRY_DISPLAY_RESOLUTION_CHANGED       = 1ULL << 23,
    GEOMETRY_DISPLAY_SINGLEBUF_CHANGED        = 1ULL << 24,
    GEOMETRY_DISPLAY_FORCE_VALIDATE           = 1ULL << 25,
    GEOMETRY_DISPLAY_COLOR_MODE_CHANGED       = 1ULL << 26,
    GEOMETRY_DISPLAY_DYNAMIC_RECOMPOSITION    = 1ULL << 27,
    GEOMETRY_DISPLAY_POWER_ON                 = 1ULL << 28,
    GEOMETRY_DISPLAY_POWER_OFF                = 1ULL << 29,
    GEOMETRY_DISPLAY_COLOR_TRANSFORM_CHANGED  = 1ULL << 30,
    GEOMETRY_DISPLAY_DATASPACE_CHANGED        = 1ULL << 31,
    /* 1ULL << 32 */
    /* 1ULL << 33 */
    /* 1ULL << 34 */
    /* 1ULL << 35 */
    GEOMETRY_DEVICE_DISPLAY_ADDED             = 1ULL << 36,
    GEOMETRY_DEVICE_DISPLAY_REMOVED           = 1ULL << 37,
    GEOMETRY_DEVICE_CONFIG_CHANGED            = 1ULL << 38,
    GEOMETRY_DEVICE_DISP_MODE_CHAGED          = 1ULL << 39,
    GEOMETRY_DEVICE_SCENARIO_CHANGED          = 1ULL << 40,

    GEOMETRY_ERROR_CASE                       = 1ULL << 63,
};

class ExynosDisplay;
class ExynosResourceManager;

class ExynosDevice {
    public:
        /**
         * TODO : Should be defined as ExynosDisplay type
         * Display list that managed by Device.
         */
        android::Vector< ExynosDisplay* > mDisplays;
        std::map<uint32_t, ExynosDisplay *> mDisplayMap;

        int mNumVirtualDisplay;

        /**
         * Resource manager object that is used to manage HW resources and assign resources to each layers
         */
        ExynosResourceManager *mResourceManager;

        /**
         * Geometry change will be saved by bit map.
         * ex) Display create/destory.
         */
        uint64_t mGeometryChanged;

        /**
         * If Panel has not self-refresh feature, dynamic recomposition will be enabled.
         */
        std::thread mDRThread;
        volatile int32_t mDRThreadStatus;
        std::atomic<bool> mDRLoopStatus;
        bool mPrimaryBlank;
        std::mutex mDRWakeUpMutex;
        std::condition_variable mDRWakeUpCondition;

        /**
         * Callback informations those are used by SurfaceFlinger.
         * - VsyncCallback: Vsync detect callback.
         * - RefreshCallback: Callback by refresh request from HWC.
         * - HotplugCallback: Hot plug event by new display hardware.
         */

        /** TODO : Array size shuld be checked */
        exynos_callback_info_t mCallbackInfos[HWC2_CALLBACK_SEAMLESS_POSSIBLE + 1];

        std::map<uint32_t, exynos_callback_info_t> mHwc3CallbackInfos;
        Mutex mDeviceCallbackMutex;

        /**
         * Thread variables
         */
        int mVsyncFd;
        int mExtVsyncFd;

        /**
         * mDisplayId of display that has the slowest fps.
         * HWC uses vsync of display that has the slowest fps to main vsync.
         */
        uint32_t mVsyncDisplayId;
        uint64_t mTimestamp;

        uint32_t mDisplayMode;

        FenceTracker mFenceTracker;

        /**
         * This will be initialized with differnt class
         * that inherits ExynosDeviceInterface according to
         * interface type.
         */
        std::unique_ptr<ExynosDeviceInterface> mDeviceInterface;

        // Con/Destructors
        ExynosDevice(bool vrrApiSupported);
        virtual ~ExynosDevice();

        bool isFirstValidate();
        bool isLastValidate(ExynosDisplay *display);

        /**
         * @param outSize
         * @param * outBuffer
         */

        void dynamicRecompositionThreadCreate();
        static void* dynamicRecompositionThreadLoop(void *data);


        /**
         * @param display
         */
        ExynosDisplay* getDisplay(uint32_t display) { return mDisplayMap[display]; }

        /**
         * Device Functions for HWC 2.0
         */

        /**
         * Descriptor: HWC2_FUNCTION_CREATE_VIRTUAL_DISPLAY
         * HWC2_PFN_CREATE_VIRTUAL_DISPLAY
         */
        int32_t createVirtualDisplay(
                uint32_t width, uint32_t height, int32_t *format, ExynosDisplay *display);

        /**
         * Descriptor: HWC2_FUNCTION_DESTROY_VIRTUAL_DISPLAY
         * HWC2_PFN_DESTROY_VIRTUAL_DISPLAY
         */
        int32_t destroyVirtualDisplay(
                ExynosDisplay *display);

        /**
         * Descriptor: HWC2_FUNCTION_DUMP
         * HWC2_PFN_DUMP
         */
        void dump(uint32_t *outSize, char *outBuffer);

        /**
         * Descriptor: HWC2_FUNCTION_GET_MAX_VIRTUAL_DISPLAY_COUNT
         * HWC2_PFN_GET_MAX_VIRTUAL_DISPLAY_COUNT
         */
        /* TODO overide check!! */
        uint32_t getMaxVirtualDisplayCount();

         /* Descriptor: HWC2_FUNCTION_GET_LAYER_GENERIC_METADATA_KEY
         */
        void getLayerGenericMetadataKey(uint32_t __unused keyIndex,
                uint32_t* __unused outKeyLength, char* __unused outKey, bool* __unused outMandatory);

        /**
         * Descriptor: HWC2_FUNCTION_REGISTER_CALLBACK
         * HWC2_PFN_REGISTER_CALLBACK
         */
        int32_t registerCallback (
                int32_t descriptor, hwc2_callback_data_t callbackData, hwc2_function_pointer_t point);
        bool isCallbackAvailable(int32_t descriptor);
        void onHotPlug(uint32_t displayId, bool status, int hotplugErrorCode);
        void onRefresh(uint32_t displayId);
        void onRefreshDisplays();

        void onVsync(uint32_t displayId, int64_t timestamp);
        bool onVsync_2_4(uint32_t displayId, int64_t timestamp, uint32_t vsyncPeriod);
        void onVsyncPeriodTimingChanged(uint32_t displayId,
                                        hwc_vsync_period_change_timeline_t *timeline);

        void onContentProtectionUpdated(uint32_t displayId, HdcpLevels hdcpLevels);

        void setHWCDebug(unsigned int debug);
        uint32_t getHWCDebug();
        void setHWCFenceDebug(uint32_t ipNum, uint32_t typeNum, uint32_t mode);
        void getHWCFenceDebug();
        void setHWCControl(uint32_t display, uint32_t ctrl, int32_t val);
        void setDisplayMode(uint32_t displayMode);
        bool checkDisplayConnection(uint32_t displayId);
        bool checkNonInternalConnection();
        void getCapabilitiesLegacy(uint32_t *outCount, int32_t *outCapabilities);
        void getCapabilities(uint32_t *outCount, int32_t* outCapabilities);
        void setGeometryChanged(uint64_t changedBit) { mGeometryChanged|= changedBit;};
        void clearGeometryChanged();
        void setDynamicRecomposition(uint32_t displayId, unsigned int on);
        bool canSkipValidate();
        bool validateFences(ExynosDisplay *display);
        void compareVsyncPeriod();
        bool isDynamicRecompositionThreadAlive();
        void checkDynamicRecompositionThread();
        int32_t setDisplayDeviceMode(int32_t display_id, int32_t mode);
        int32_t setPanelGammaTableSource(int32_t display_id, int32_t type, int32_t source);
        void dump(String8 &result);

        class captureReadbackClass {
            public:
                captureReadbackClass(ExynosDevice *device);
                ~captureReadbackClass();
                int32_t allocBuffer(uint32_t format, uint32_t w, uint32_t h);
                buffer_handle_t& getBuffer() { return mBuffer; };
                void saveToFile(const String8 &fileName);
            private:
                buffer_handle_t mBuffer = nullptr;
                ExynosDevice* mDevice = nullptr;
        };
        void captureScreenWithReadback(uint32_t displayType);
        void cleanupCaptureScreen(void *buffer);
        void signalReadbackDone();
        void clearWaitingReadbackReqDone() {
            mIsWaitingReadbackReqDone = false;
        };

        uint32_t getWindowPlaneNum();
        uint32_t getSpecialPlaneNum();
        uint32_t getSpecialPlaneNum(uint32_t displayId);
        uint32_t getSpecialPlaneId(uint32_t index);
        uint64_t getSpecialPlaneAttr(uint32_t index);

        int32_t registerHwc3Callback(uint32_t descriptor, hwc2_callback_data_t callbackData,
                                     hwc2_function_pointer_t point);
        void onVsyncIdle(hwc2_display_t displayId);
        bool isDispOffAsyncSupported() { return mDisplayOffAsync; };
        bool hasOtherDisplayOn(ExynosDisplay *display);
        virtual int32_t getOverlaySupport([[maybe_unused]] OverlayProperties* caps){
            return HWC2_ERROR_UNSUPPORTED;
        }

        void onRefreshRateChangedDebug(hwc2_display_t displayId, uint32_t vsyncPeriod,
                                       uint32_t refreshPeriod = 0);

        bool isVrrApiSupported() const { return mVrrApiSupported; };
        void setVBlankOffDelay(const int vblankOffDelay);

    protected:
        void initDeviceInterface(uint32_t interfaceType);
    protected:
        uint32_t mInterfaceType;
    private:
        Mutex mCaptureMutex;
        Condition mCaptureCondition;
        std::atomic<bool> mIsWaitingReadbackReqDone = false;
        bool isCallbackRegisteredLocked(int32_t descriptor);

    public:
        void enterToTUI() { mIsInTUI = true; };
        void exitFromTUI() { mIsInTUI = false; };
        bool isInTUI() { return mIsInTUI; };

    private:
        bool mIsInTUI;
        bool mDisplayOffAsync;
        bool mVrrApiSupported = false;

    public:
        void handleHotplug();
};

#endif //_EXYNOSDEVICE_H
