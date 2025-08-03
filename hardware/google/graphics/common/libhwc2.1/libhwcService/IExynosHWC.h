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

#ifndef ANDROID_EXYNOS_IHWC_H_
#define ANDROID_EXYNOS_IHWC_H_

#include <stdint.h>
#include <sys/types.h>
#include <vector>

#include <utils/Errors.h>
#include <utils/RefBase.h>
#include <binder/IInterface.h>

namespace android {

class IExynosHWCService : public IInterface {
public:

    DECLARE_META_INTERFACE(ExynosHWCService);

    virtual int addVirtualDisplayDevice() = 0;
    virtual int destroyVirtualDisplayDevice() = 0;

    /*
     * setWFDMode() function sets the WFD operation Mode.
     * It enables / disables the WFD.
     */
    virtual int setWFDMode(unsigned int mode) = 0;
    virtual int getWFDMode() = 0;
    virtual int sendWFDCommand(int32_t cmd, int32_t ext1, int32_t ext2) = 0;
    virtual int setSecureVDSMode(unsigned int mode) = 0;
    virtual int setWFDOutputResolution(unsigned int width, unsigned int height) = 0;
    virtual int getWFDOutputResolution(unsigned int* width, unsigned int* height) = 0;
    virtual void setPresentationMode(bool use) = 0;
    virtual int getPresentationMode(void) = 0;
    virtual int setVDSGlesFormat(int format) = 0;
    virtual int getExternalDisplayConfigs() = 0;
    virtual int setExternalDisplayConfig(unsigned int index) = 0;
    virtual int setExternalVsyncEnabled(unsigned int index) = 0;
    virtual int getExternalHdrCapabilities() = 0;
    virtual void setBootFinished(void) = 0;
    virtual void enableMPP(uint32_t physicalType, uint32_t physicalIndex, uint32_t logicalIndex, uint32_t enable) = 0;
    virtual void setScaleDownRatio(uint32_t physicalType, uint32_t physicalIndex,
            uint32_t logicalIndex, uint32_t scaleDownRatio) = 0;
    virtual void setHWCDebug(int debug) = 0;
    virtual uint32_t getHWCDebug() = 0;
    virtual void setHWCFenceDebug(uint32_t ipNum, uint32_t fenceNum, uint32_t mode) = 0;
    virtual void getHWCFenceDebug() = 0;
    virtual int setHWCCtl(uint32_t display, uint32_t ctrl, int32_t val) = 0;

    virtual int setDDIScaler(uint32_t display_id, uint32_t width, uint32_t height) = 0;

    virtual int32_t setDisplayDeviceMode(int32_t display_id, int32_t mode) = 0;
    virtual int32_t setPanelGammaTableSource(int32_t display_id, int32_t type, int32_t source) = 0;
    virtual void setLbeCtrl(uint32_t display_id, uint32_t state, uint32_t lux) = 0;
    virtual int32_t setDisplayBrightness(int32_t display_id, float brightness) = 0;
    virtual int32_t ignoreDisplayBrightnessUpdateRequests(int32_t displayId, bool ignore) = 0;
    virtual int32_t setDisplayBrightnessNits(int32_t displayId, float nits) = 0;
    virtual int32_t setDisplayBrightnessDbv(int32_t displayId, uint32_t dbv) = 0;
    virtual int32_t setDisplayLhbm(int32_t display_id, uint32_t on) = 0;
    virtual int32_t setMinIdleRefreshRate(uint32_t display_id, int32_t refresh_rate) = 0;
    virtual int32_t setRefreshRateThrottle(uint32_t display_id, int32_t throttle) = 0;
    virtual int32_t setDisplayRCDLayerEnabled(uint32_t displayIndex, bool enable) = 0;
    virtual int32_t triggerDisplayIdleEnter(uint32_t displayIndex, uint32_t idleTeRefreshRate) = 0;
    virtual int32_t setDisplayDbm(int32_t display_id, uint32_t on) = 0;
    virtual int32_t setDisplayMultiThreadedPresent(const int32_t& displayId,
                                                   const bool& enable) = 0;
    virtual int32_t triggerRefreshRateIndicatorUpdate(uint32_t displayId, uint32_t refreshRate) = 0;
    virtual int32_t dumpBuffers(uint32_t displayId, int32_t count) = 0;
    virtual int32_t setPresentTimeoutController(uint32_t displayId, uint32_t controllerType) = 0;
    virtual int32_t setPresentTimeoutParameters(
            uint32_t displayId, int timeoutNs,
            const std::vector<std::pair<uint32_t, uint32_t>>& settings) = 0;
};

/* Native Interface */
class BnExynosHWCService : public BnInterface<IExynosHWCService> {
public:
    virtual status_t    onTransact( uint32_t code,
                                    const Parcel& data,
                                    Parcel* reply,
                                    uint32_t flags = 0);

};
}
#endif
