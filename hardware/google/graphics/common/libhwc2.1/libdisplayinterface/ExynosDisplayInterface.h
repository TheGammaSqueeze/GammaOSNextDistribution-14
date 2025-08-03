/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef _EXYNOSDISPLAYINTERFACE_H
#define _EXYNOSDISPLAYINTERFACE_H

#include <hardware/hwcomposer2.h>
#include <sys/types.h>
#include <utils/Errors.h>

#include "ExynosHWCHelper.h"

class ExynosDisplay;

struct VrrSettings;
typedef struct VrrSettings VrrSettings_t;

using namespace android;
class ExynosDisplayInterface {
    protected:
        ExynosDisplay *mExynosDisplay = NULL;
    public:
        virtual ~ExynosDisplayInterface();
        virtual void init(ExynosDisplay* __unused exynosDisplay) {};
        virtual int32_t setPowerMode(int32_t __unused mode) {return NO_ERROR;};
        virtual int32_t setLowPowerMode() { return HWC2_ERROR_UNSUPPORTED; };
        virtual bool isDozeModeAvailable() const { return false; };
        virtual int32_t setVsyncEnabled(uint32_t __unused enabled) {return NO_ERROR;};
        virtual int32_t getDisplayConfigs(
                uint32_t* outNumConfigs,
                hwc2_config_t* outConfigs);
        virtual void dumpDisplayConfigs() {};
        virtual bool supportDataspace(int32_t __unused dataspace) { return true; };
        virtual int32_t getColorModes(uint32_t* outNumModes, int32_t* outModes);
        virtual int32_t setColorMode(int32_t __unused mode) {return NO_ERROR;};
        virtual int32_t setActiveConfig(hwc2_config_t __unused config) {return NO_ERROR;};
        virtual int32_t setActiveConfigWithConstraints(
                hwc2_config_t __unused config, bool __unused test = false)
        {return NO_ERROR;};
        virtual int32_t getDisplayVsyncPeriod(hwc2_vsync_period_t* outVsyncPeriod);
        virtual int32_t getConfigChangeDuration() {return 0;};
        virtual bool needRefreshOnLP() { return false; };
        virtual int32_t setCursorPositionAsync(uint32_t __unused x_pos,
                uint32_t __unused y_pos) {return NO_ERROR;};
        virtual int32_t updateHdrCapabilities();
        virtual int32_t deliverWinConfigData() {return NO_ERROR;};
        virtual int32_t clearDisplay(bool __unused needModeClear = false) {return NO_ERROR;};
        virtual int32_t triggerClearDisplayPlanes() { return NO_ERROR; }
        virtual int32_t disableSelfRefresh(uint32_t __unused disable) {return NO_ERROR;};
        virtual int32_t setForcePanic() {return NO_ERROR;};
        virtual int getDisplayFd() {return -1;};
        virtual uint32_t getMaxWindowNum() {return 0;};
        virtual int32_t setColorTransform(const float* __unused matrix,
                int32_t __unused hint) {return HWC2_ERROR_UNSUPPORTED;}
        virtual int32_t getRenderIntents(int32_t __unused mode, uint32_t* __unused outNumIntents,
                int32_t* __unused outIntents) {return 0;}
        virtual int32_t setColorModeWithRenderIntent(int32_t __unused mode, int32_t __unused intent) {return 0;}
        virtual int32_t getReadbackBufferAttributes(int32_t* /*android_pixel_format_t*/ outFormat,
                int32_t* /*android_dataspace_t*/ outDataspace);
        /* HWC 2.3 APIs */
        virtual int32_t getDisplayIdentificationData(uint8_t* __unused outPort,
                uint32_t* __unused outDataSize, uint8_t* __unused outData) {return 0;}
        bool isPrimary();
        /* For HWC 2.4 APIs */
        virtual int32_t getVsyncAppliedTime(hwc2_config_t __unused config, int64_t* __unused actualChangeTime) {return NO_ERROR;}
        virtual void destroyLayer(ExynosLayer* __unused layer){};
        /* For HWC 3.0 APIs */
        virtual int32_t getDisplayIdleTimerSupport(bool& outSupport) {
            outSupport = false;
            return NO_ERROR;
        }
        virtual int32_t getDefaultModeId(int32_t* __unused modeId) {
            return HWC2_ERROR_UNSUPPORTED;
        }
        virtual uint32_t getActiveModeId() { return UINT_MAX; }

        virtual int32_t waitVBlank() { return 0; };

        virtual bool readHotplugStatus() { return true; };
        virtual int readHotplugErrorCode() { return 0; };
        virtual void resetHotplugErrorCode(){};

        virtual void setVrrSettings(const VrrSettings_t& vrrSettings);

        virtual void setManufacturerInfo(uint8_t __unused edid8, uint8_t __unused edid9){};
        virtual uint32_t getManufacturerInfo() { return 0; }
        virtual void setProductId(uint8_t __unused edid10, uint8_t __unused edid11){};
        virtual uint32_t getProductId() { return 0; }

    public:
        uint32_t mType = INTERFACE_TYPE_NONE;
};

#endif
