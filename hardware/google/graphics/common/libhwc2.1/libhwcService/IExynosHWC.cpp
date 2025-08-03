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

#include <stdint.h>
#include <sys/types.h>

#include <utils/Errors.h>
#include <utils/RefBase.h>
#include <utils/Timers.h>

#include <binder/Parcel.h>
#include <binder/IInterface.h>

#include "IExynosHWC.h"

#include <vector>

#define RET_IF_ERR(expr)      \
    do {                      \
        auto err = (expr);    \
        if (err) [[unlikely]] \
            return err;       \
    } while (0)

namespace android {

enum {
    ADD_VIRTUAL_DISPLAY_DEVICE = 0,
    DESTROY_VIRTUAL_DISPLAY_DEVICE,
    SET_WFD_MODE,
    GET_WFD_MODE,
    SEND_WFD_COMMAND,
    SET_SECURE_VDS_MODE,
    SET_WFD_OUTPUT_RESOLUTION,
    GET_WFD_OUTPUT_RESOLUTION,
    SET_PRESENTATION_MODE,
    GET_PRESENTATION_MODE,
    SET_VDS_GLES_FORMAT,
    HWC_CONTROL,
    SET_BOOT_FINISHED,
    SET_VIRTUAL_HPD,
    GET_EXTERNAL_DISPLAY_CONFIG,
    SET_EXTERNAL_DISPLAY_CONFIG,
    ENABLE_MPP,
    SET_EXTERNAL_VSYNC,
    SET_DDISCALER,
    GET_EXTERNAL_HDR_CAPA,
    SET_SCALE_DOWN_RATIO,
    SET_HWC_DEBUG = 105,
    GET_HWC_DEBUG = 106,
    SET_HWC_FENCE_DEBUG = 107,
    GET_HWC_FENCE_DEBUG = 108,
    SET_DISPLAY_DEVICE_MODE = 1000,
    SET_PANEL_GAMMA_TABLE_SOURCE = 1001,
    SET_DISPLAY_BRIGHTNESS = 1002,
    SET_DISPLAY_LHBM = 1003,
    SET_LBE_CTRL = 1004,
    SET_MIN_IDLE_REFRESH_RATE = 1005,
    SET_REFRESH_RATE_THROTTLE = 1006,
    SET_DISPLAY_RCDLAYER_ENABLED = 1007,
    TRIGGER_DISPLAY_IDLE_ENTER = 1008,
    SET_DISPLAY_DBM = 1009,
    SET_DISPLAY_MULTI_THREADED_PRESENT = 1010,
    TRIGGER_REFRESH_RATE_INDICATOR_UPDATE = 1011,
    IGNORE_DISPLAY_BRIGHTNESS_UPDATE_REQUESTS = 1012,
    SET_DISPLAY_BRIGHTNESS_NITS = 1013,
    SET_DISPLAY_BRIGHTNESS_DBV = 1014,
    DUMP_BUFFERS = 1015,
    SET_PRESENT_TIMEOUT_PARAMETERS = 1016,
    SET_PRESENT_TIMEOUT_CONTROLLER = 1017,
};

class BpExynosHWCService : public BpInterface<IExynosHWCService> {
public:
    BpExynosHWCService(const sp<IBinder>& impl)
        : BpInterface<IExynosHWCService>(impl)
    {
    }

    virtual int addVirtualDisplayDevice()
    {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        int result = remote()->transact(ADD_VIRTUAL_DISPLAY_DEVICE, data, &reply);
        if (result == NO_ERROR)
            result = reply.readInt32();
        else
            ALOGE("ADD_VIRTUAL_DISPLAY_DEVICE transact error(%d)", result);
        return result;
    }

    virtual int destroyVirtualDisplayDevice()
    {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        int result = remote()->transact(DESTROY_VIRTUAL_DISPLAY_DEVICE, data, &reply);
        if (result == NO_ERROR)
            result = reply.readInt32();
        else
            ALOGE("DESTROY_VIRTUAL_DISPLAY_DEVICE transact error(%d)", result);
        return result;
    }

    virtual int setWFDMode(unsigned int mode)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeInt32(mode);
        int result = remote()->transact(SET_WFD_MODE, data, &reply);
        if (result == NO_ERROR)
            result = reply.readInt32();
        else
            ALOGE("SET_WFD_MODE transact error(%d)", result);
        return result;
    }

    virtual int getWFDMode()
    {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        int result = remote()->transact(GET_WFD_MODE, data, &reply);
        if (result == NO_ERROR)
            result = reply.readInt32();
        else
            ALOGE("GET_WFD_MODE transact error(%d)", result);
        return result;
    }

    virtual int sendWFDCommand(int32_t cmd, int32_t ext1, int32_t ext2)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeInt32(cmd);
        data.writeInt32(ext1);
        data.writeInt32(ext2);
        int result = remote()->transact(SEND_WFD_COMMAND, data, &reply);
        if (result == NO_ERROR)
            result = reply.readInt32();
        else
            ALOGE("SEND_WFD_COMMAND transact error(%d)", result);
        return result;
    }

    virtual int setSecureVDSMode(unsigned int mode)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeInt32(mode);
        int result = remote()->transact(SET_SECURE_VDS_MODE, data, &reply);
        if (result == NO_ERROR)
            result = reply.readInt32();
        else
            ALOGE("SET_SECURE_VDS_MODE transact error(%d)", result);
        return result;
    }

    virtual int setWFDOutputResolution(unsigned int width, unsigned int height)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeInt32(width);
        data.writeInt32(height);
        int result = remote()->transact(SET_WFD_OUTPUT_RESOLUTION, data, &reply);
        if (result == NO_ERROR)
            result = reply.readInt32();
        else
            ALOGE("SET_WFD_OUTPUT_RESOLUTION transact error(%d)", result);
        return result;
    }

    virtual int getWFDOutputResolution(unsigned int* width, unsigned int* height) {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        int result = remote()->transact(GET_WFD_OUTPUT_RESOLUTION, data, &reply);
        if (result == NO_ERROR) {
            *width  = reply.readInt32();
            *height = reply.readInt32();
            result = reply.readInt32();
        } else
            ALOGE("GET_WFD_OUTPUT_RESOLUTION transact error(%d)", result);
        return result;
    }

    virtual void setPresentationMode(bool use)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeInt32(use);
        int result = remote()->transact(SET_PRESENTATION_MODE, data, &reply);
        if (result != NO_ERROR)
            ALOGE("SET_PRESENTATION_MODE transact error(%d)", result);
    }

    virtual int getPresentationMode(void)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        int result = remote()->transact(GET_PRESENTATION_MODE, data, &reply);
        if (result == NO_ERROR)
            result = reply.readInt32();
        else
            ALOGE("GET_PRESENTATION_MODE transact error(%d)", result);
        return result;
    }

    virtual int setVDSGlesFormat(int format)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeInt32(format);
        int result = remote()->transact(SET_VDS_GLES_FORMAT, data, &reply);
        if (result == NO_ERROR)
            result = reply.readInt32();
        else
            ALOGE("SET_VDS_GLES_FORMAT transact error(%d)", result);
        return result;
    }

    virtual int getExternalDisplayConfigs()
    {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        int result = remote()->transact(GET_EXTERNAL_DISPLAY_CONFIG, data, &reply);
        if (result == NO_ERROR)
            result = reply.readInt32();
        else
            ALOGE("GET_EXTERNAL_DISPLAY_CONFIG transact error(%d)", result);
        return result;
    }

    virtual int setExternalDisplayConfig(unsigned int index)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeInt32(index);
        int result = remote()->transact(SET_EXTERNAL_DISPLAY_CONFIG, data, &reply);
        if (result == NO_ERROR)
            result = reply.readInt32();
        else
            ALOGE("SET_EXTERNAL_DISPLAY_CONFIG transact error(%d)", result);
        return result;
    }

    virtual int setExternalVsyncEnabled(unsigned int index)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeInt32(index);
        int result = remote()->transact(SET_EXTERNAL_VSYNC, data, &reply);
        if (result == NO_ERROR)
            result = reply.readInt32();
        else
            ALOGE("SET_EXTERNAL_VSYNC transact error(%d)", result);
        return result;
    }

    virtual int getExternalHdrCapabilities()
    {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        int result = remote()->transact(GET_EXTERNAL_HDR_CAPA, data, &reply);
        if (result == NO_ERROR)
            result = reply.readInt32();
        else
            ALOGE("GET_EXTERNAL_HDR_CAPA transact error(%d)", result);

        return result;
    }

    virtual void setBootFinished()
    {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        int result = remote()->transact(SET_BOOT_FINISHED, data, &reply);
        if (result != NO_ERROR)
            ALOGE("SET_BOOT_FINISHED transact error(%d)", result);
    }

    virtual void enableMPP(uint32_t physicalType, uint32_t physicalIndex, uint32_t logicalIndex, uint32_t enable)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeInt32(physicalType);
        data.writeInt32(physicalIndex);
        data.writeInt32(logicalIndex);
        data.writeInt32(enable);
        int result = remote()->transact(ENABLE_MPP, data, &reply);
        if (result != NO_ERROR)
            ALOGE("ENABLE_MPP transact error(%d)", result);
    }

    virtual void setScaleDownRatio(uint32_t physicalType, uint32_t physicalIndex,
            uint32_t logicalIndex, uint32_t scaleDownRatio)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeInt32(physicalType);
        data.writeInt32(physicalIndex);
        data.writeInt32(logicalIndex);
        data.writeInt32(scaleDownRatio);
        int result = remote()->transact(SET_SCALE_DOWN_RATIO, data, &reply);
        if (result != NO_ERROR)
            ALOGE("SET_SCALE_DOWN_RATIO transact error(%d)", result);
    }

    virtual void setLbeCtrl(uint32_t display_id, uint32_t state, uint32_t lux) {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeInt32(display_id);
        data.writeInt32(state);
        data.writeInt32(lux);
        int result = remote()->transact(SET_LBE_CTRL, data, &reply);
        if (result != NO_ERROR) ALOGE("SET_LBE_CTRL transact error(%d)", result);
    }

    virtual void setHWCDebug(int debug)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeInt32(debug);
        int result = remote()->transact(SET_HWC_DEBUG, data, &reply);
        if (result != NO_ERROR)
            ALOGE("SET_HWC_DEBUG transact error(%d)", result);
    }

    virtual uint32_t getHWCDebug()
    {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        int result = remote()->transact(GET_HWC_DEBUG, data, &reply);
        if (result == NO_ERROR)
            result = reply.readInt32();
        else {
            ALOGE("GET_HWC_DEBUG transact error(%d)", result);
        }
        return result;
    }

    virtual void setHWCFenceDebug(uint32_t fenceNum, uint32_t ipNum, uint32_t mode)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeInt32(fenceNum);
        data.writeInt32(ipNum);
        data.writeInt32(mode);
        remote()->transact(SET_HWC_FENCE_DEBUG, data, &reply);
    }

    virtual void getHWCFenceDebug()
    {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        remote()->transact(GET_HWC_FENCE_DEBUG, data, &reply);
    }

    virtual int setHWCCtl(uint32_t display, uint32_t ctrl, int32_t val) {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeInt32(display);
        data.writeInt32(ctrl);
        data.writeInt32(val);
        int result = remote()->transact(HWC_CONTROL, data, &reply);
        if (result == NO_ERROR)
            result = reply.readInt32();
        else
            ALOGE("HWC_CONTROL transact error(%d)", result);
        return result;
    };

    virtual int setDDIScaler(uint32_t displayId, uint32_t width, uint32_t height) {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeUint32(displayId);
        data.writeUint32(width);
        data.writeUint32(height);
        int result = remote()->transact(SET_DDISCALER, data, &reply);
        if (result == NO_ERROR)
            result = reply.readInt32();
        else
            ALOGE("SET_DDISCALER transact error(%d)", result);
        return result;
    }

    int32_t setDisplayDeviceMode(int32_t display_id, int32_t mode)
    {
        ALOGD("null func: %s(%d %d)", __func__, display_id, mode);
        return NO_ERROR;
    }

    virtual int32_t setPanelGammaTableSource(int32_t display_id, int32_t type, int32_t source) {
        ALOGD("null func: %s(%d %d %d)", __func__, display_id, type, source);
        return NO_ERROR;
    }

    virtual int32_t setDisplayBrightness(int32_t display_id, float brightness) {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeInt32(display_id);
        data.writeFloat(brightness);
        int result = remote()->transact(SET_DISPLAY_BRIGHTNESS, data, &reply);
        if (result)
            ALOGE("SET_DISPLAY_BRIGHTNESS transact error(%d)", result);
        return result;
    }

    virtual int32_t ignoreDisplayBrightnessUpdateRequests(int32_t displayId, bool ignore) {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeInt32(displayId);
        data.writeBool(ignore);
        int result = remote()->transact(IGNORE_DISPLAY_BRIGHTNESS_UPDATE_REQUESTS, data, &reply);
        if (result) ALOGE("IGNORE_DISPLAY_BRIGHTNESS_UPDATE_REQUESTS transact error(%d)", result);
        return result;
    }

    virtual int32_t setDisplayBrightnessNits(int32_t displayId, float nits) {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeInt32(displayId);
        data.writeFloat(nits);
        int result = remote()->transact(SET_DISPLAY_BRIGHTNESS_NITS, data, &reply);
        if (result) ALOGE("SET_DISPLAY_BRIGHTNESS_NITS transact error(%d)", result);
        return result;
    }

    virtual int32_t setDisplayBrightnessDbv(int32_t displayId, uint32_t dbv) {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeInt32(displayId);
        data.writeUint32(dbv);
        int result = remote()->transact(SET_DISPLAY_BRIGHTNESS_DBV, data, &reply);
        if (result) {
            ALOGE("SET_DISPLAY_BRIGHTNESS_DBV transact error(%d)", result);
        }
        return result;
    }

    virtual int32_t setDisplayLhbm(int32_t display_id, uint32_t on) {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeInt32(display_id);
        data.writeInt32(on);
        int result = remote()->transact(SET_DISPLAY_LHBM, data, &reply);
        if (result) ALOGE("SET_DISPLAY_LHBM transact error(%d)", result);
        return result;
    }

    virtual int32_t setMinIdleRefreshRate(uint32_t display_id, int32_t fps) {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeUint32(display_id);
        data.writeInt32(fps);
        int result = remote()->transact(SET_MIN_IDLE_REFRESH_RATE, data, &reply);
        if (result) ALOGE("SET_MIN_IDLE_REFRESH_RATE transact error(%d)", result);
        return result;
    }

    virtual int32_t setRefreshRateThrottle(uint32_t display_id, int32_t delay_ms) {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeUint32(display_id);
        data.writeInt32(delay_ms);
        int result = remote()->transact(SET_REFRESH_RATE_THROTTLE, data, &reply);
        if (result) ALOGE("SET_REFRESH_RATE_THROTTLE transact error(%d)", result);
        return result;
    }

    int32_t setDisplayRCDLayerEnabled(uint32_t displayIndex, bool enable) override {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeUint32(displayIndex);
        data.writeInt32(enable);

        auto result = remote()->transact(SET_DISPLAY_RCDLAYER_ENABLED, data, &reply);
        ALOGE_IF(result != NO_ERROR, "SET_DISPLAY_RCDLAYER_ENABLED transact error(%d)", result);
        return result;
    }

    int32_t triggerDisplayIdleEnter(uint32_t displayIndex, uint32_t idleTeRefreshRate) override {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeUint32(displayIndex);
        data.writeUint32(idleTeRefreshRate);

        auto result = remote()->transact(TRIGGER_DISPLAY_IDLE_ENTER, data, &reply);
        ALOGE_IF(result != NO_ERROR, "TRIGGER_DISPLAY_IDLE_ENTER transact error(%d)", result);
        return result;
    }

    virtual int32_t setDisplayDbm(int32_t display_id, uint32_t on) {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeInt32(display_id);
        data.writeInt32(on);
        int result = remote()->transact(SET_DISPLAY_DBM, data, &reply);
        if (result) ALOGE("SET_DISPLAY_DBM transact error(%d)", result);
        return result;
    }

    virtual int32_t setDisplayMultiThreadedPresent(const int32_t& displayId,
                                                   const bool& enable) {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeInt32(displayId);
        data.writeBool(enable);
        int result = remote()->transact(SET_DISPLAY_MULTI_THREADED_PRESENT, data, &reply);
        if (result) ALOGE("SET_DISPLAY_MULTI_THREADED_PRESENT transact error(%d)", result);
        return result;
    }

    virtual int32_t triggerRefreshRateIndicatorUpdate(uint32_t displayId,
                                                      uint32_t refreshRate) override {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeUint32(displayId);
        data.writeUint32(refreshRate);

        auto result = remote()->transact(TRIGGER_REFRESH_RATE_INDICATOR_UPDATE, data, &reply);
        ALOGE_IF(result != NO_ERROR, "TRIGGER_REFRESH_RATE_INDICATOR_UPDATE transact error(%d)",
                 result);
        return result;
    }

    virtual int32_t dumpBuffers(uint32_t displayId, int32_t count) override {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeUint32(displayId);
        data.writeInt32(count);

        auto result = remote()->transact(DUMP_BUFFERS, data, &reply);
        ALOGE_IF(result != NO_ERROR, "DUMP_BUFFERS transact error(%d)", result);
        return result;
    }

    int32_t setPresentTimeoutController(uint32_t displayId, uint32_t controllerType) override {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeInt32(displayId);
        data.writeUint32(controllerType);
        int result = remote()->transact(SET_PRESENT_TIMEOUT_CONTROLLER, data, &reply);
        ALOGE_IF(result != NO_ERROR, "SET_PRESENT_TIMEOUT_CONTROLLER transact error(%d)", result);
        return result;
    }
    int32_t setPresentTimeoutParameters(
            uint32_t displayId, int timeoutNs,
            const std::vector<std::pair<uint32_t, uint32_t>>& settings) override {
        Parcel data, reply;
        data.writeInterfaceToken(IExynosHWCService::getInterfaceDescriptor());
        data.writeInt32(displayId);
        // The format is timeout, [ i32 <frames> i32 <intervalNs> ]+.
        // E.g. if [2, 8333333], [1, 16666667] ..., the pattern is:
        // last present <timeout> 1st task <8.3 ms> 2nd task <8.3 ms> 3rd task <16.67ms> ...
        data.writeInt32(timeoutNs);
        for (const auto& it : settings) {
            data.writeUint32(it.first);  // frames
            data.writeUint32(it.second); // intervalNs
        }
        int result = remote()->transact(SET_PRESENT_TIMEOUT_PARAMETERS, data, &reply);
        ALOGE_IF(result != NO_ERROR, "SET_PRESENT_TIMEOUT_PARAMETERS transact error(%d)", result);
        return result;
    }
};

IMPLEMENT_META_INTERFACE(ExynosHWCService, "android.hal.ExynosHWCService");

status_t BnExynosHWCService::onTransact(
    uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags)
{
    switch(code) {
        case ADD_VIRTUAL_DISPLAY_DEVICE: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int res = addVirtualDisplayDevice();
            reply->writeInt32(res);
            return NO_ERROR;
        } break;
        case DESTROY_VIRTUAL_DISPLAY_DEVICE: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int res = destroyVirtualDisplayDevice();
            reply->writeInt32(res);
            return NO_ERROR;
        } break;
        case SET_WFD_MODE: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int mode = data.readInt32();
            int res = setWFDMode(mode);
            reply->writeInt32(res);
            return NO_ERROR;
        } break;
        case GET_WFD_MODE: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int res = getWFDMode();
            reply->writeInt32(res);
            return NO_ERROR;
        } break;
        case SEND_WFD_COMMAND: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int cmd = data.readInt32();
            int ext1 = data.readInt32();
            int ext2 = data.readInt32();
            int res = sendWFDCommand(cmd, ext1, ext2);
            reply->writeInt32(res);
            return NO_ERROR;
        } break;
        case SET_SECURE_VDS_MODE: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int mode = data.readInt32();
            int res = setSecureVDSMode(mode);
            reply->writeInt32(res);
            return NO_ERROR;
        } break;
        case SET_WFD_OUTPUT_RESOLUTION: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int width  = data.readInt32();
            int height = data.readInt32();
            int res = setWFDOutputResolution(width, height);
            reply->writeInt32(res);
            return NO_ERROR;
        } break;
        case GET_WFD_OUTPUT_RESOLUTION: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            uint32_t width = 0, height = 0;
            int res = getWFDOutputResolution(&width, &height);
            reply->writeInt32(width);
            reply->writeInt32(height);
            reply->writeInt32(res);
            return NO_ERROR;
        } break;
        case SET_PRESENTATION_MODE: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int use = data.readInt32();
            setPresentationMode(use);
            return NO_ERROR;
        } break;
        case GET_PRESENTATION_MODE: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int res = getPresentationMode();
            reply->writeInt32(res);
            return NO_ERROR;
        } break;
        case SET_VDS_GLES_FORMAT: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int format  = data.readInt32();
            int res = setVDSGlesFormat(format);
            reply->writeInt32(res);
            return NO_ERROR;
        } break;
       case HWC_CONTROL: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int display = data.readInt32();
            int ctrl = data.readInt32();
            int value = data.readInt32();
            int res = setHWCCtl(display, ctrl, value);
            reply->writeInt32(res);
            return NO_ERROR;
        } break;
        case GET_EXTERNAL_DISPLAY_CONFIG: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int res = getExternalDisplayConfigs();
            reply->writeInt32(res);
            return NO_ERROR;
        } break;
        case SET_EXTERNAL_DISPLAY_CONFIG: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int index = data.readInt32();
            int res = setExternalDisplayConfig(index);
            reply->writeInt32(res);
            return NO_ERROR;
        } break;
        case SET_EXTERNAL_VSYNC: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int index = data.readInt32();
            int res = setExternalVsyncEnabled(index);
            reply->writeInt32(res);
            return NO_ERROR;
        } break;
        case GET_EXTERNAL_HDR_CAPA: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int res = getExternalHdrCapabilities();
            reply->writeInt32(res);
            return NO_ERROR;
        } break;
        case SET_BOOT_FINISHED: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            setBootFinished();
            return NO_ERROR;
        } break;
        case ENABLE_MPP: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            uint32_t type = data.readInt32();
            uint32_t physicalIdx = data.readInt32();
            uint32_t logicalIdx = data.readInt32();
            uint32_t enable = data.readInt32();
            enableMPP(type, physicalIdx, logicalIdx, enable);
            return NO_ERROR;
        } break;
        case SET_SCALE_DOWN_RATIO: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            uint32_t type = data.readInt32();
            uint32_t physicalIdx = data.readInt32();
            uint32_t logicalIdx = data.readInt32();
            uint32_t scaleDownRatio = data.readInt32();
            setScaleDownRatio(type, physicalIdx, logicalIdx, scaleDownRatio);
            return NO_ERROR;
        } break;
        case SET_HWC_DEBUG: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int debug = data.readInt32();
            setHWCDebug(debug);
            reply->writeInt32(debug);
            return NO_ERROR;
        } break;
        case GET_HWC_DEBUG: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int debugFlag = getHWCDebug();
            reply->writeInt32(debugFlag);
            return NO_ERROR;
        } break;
        case SET_HWC_FENCE_DEBUG: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            uint32_t fenceNum = data.readInt32();
            uint32_t ipNum = data.readInt32();
            uint32_t mode = data.readInt32();
            setHWCFenceDebug(fenceNum, ipNum, mode);
            return NO_ERROR;
        } break;
        case SET_DDISCALER: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            uint32_t display_id = data.readUint32();
            uint32_t width = data.readUint32();
            uint32_t height = data.readUint32();
            int error = setDDIScaler(display_id, width, height);
            reply->writeInt32(error);
            return NO_ERROR;
        } break;
        case SET_DISPLAY_DEVICE_MODE: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int32_t display_id = data.readInt32();
            int32_t mode = data.readInt32();
            int32_t error = setDisplayDeviceMode(display_id, mode);
            reply->writeInt32(error);
            return NO_ERROR;
        } break;
        case SET_PANEL_GAMMA_TABLE_SOURCE: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int32_t display_id = data.readInt32();
            int32_t type = data.readInt32();
            int32_t source = data.readInt32();
            int32_t error = setPanelGammaTableSource(display_id, type, source);
            reply->writeInt32(error);
            return NO_ERROR;
        } break;

        case SET_DISPLAY_BRIGHTNESS: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int32_t display_id = data.readInt32();
            float brightness = data.readFloat();
            int32_t error = setDisplayBrightness(display_id, brightness);
            reply->writeInt32(error);
            return NO_ERROR;
        } break;

        case SET_DISPLAY_LHBM: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int32_t display_id = data.readInt32();
            uint32_t on = data.readInt32();
            int32_t error = setDisplayLhbm(display_id, on);
            reply->writeInt32(error);
            return NO_ERROR;
        } break;

        case SET_LBE_CTRL: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            uint32_t display_id = data.readInt32();
            uint32_t state = data.readInt32();
            uint32_t lux = data.readInt32();
            setLbeCtrl(display_id, state, lux);
            return NO_ERROR;
        } break;

        case SET_MIN_IDLE_REFRESH_RATE: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            uint32_t display_id = data.readUint32();
            int32_t fps = data.readInt32();
            return setMinIdleRefreshRate(display_id, fps);
        } break;

        case SET_REFRESH_RATE_THROTTLE: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            uint32_t display_id = data.readUint32();
            int32_t delay_ms = data.readInt32();
            return setRefreshRateThrottle(display_id, delay_ms);
        } break;

        case SET_DISPLAY_RCDLAYER_ENABLED: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            uint32_t displayIndex = data.readUint32();
            bool enable = data.readInt32();
            return setDisplayRCDLayerEnabled(displayIndex, enable);
        } break;

        case TRIGGER_DISPLAY_IDLE_ENTER: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            uint32_t displayIndex = data.readUint32();
            uint32_t idleTeRefreshRate = data.readUint32();
            return triggerDisplayIdleEnter(displayIndex, idleTeRefreshRate);
        } break;

        case SET_DISPLAY_DBM: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int32_t display_id = data.readInt32();
            uint32_t on = data.readInt32();
            int32_t error = setDisplayDbm(display_id, on);
            reply->writeInt32(error);
            return NO_ERROR;
        } break;

        case SET_DISPLAY_MULTI_THREADED_PRESENT: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int32_t displayId = data.readInt32();
            bool enable = data.readBool();
            int32_t error = setDisplayMultiThreadedPresent(displayId, enable);
            reply->writeInt32(error);
            return NO_ERROR;
        } break;

        case TRIGGER_REFRESH_RATE_INDICATOR_UPDATE: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            uint32_t displayId = data.readUint32();
            uint32_t refreshRate = data.readUint32();
            return triggerRefreshRateIndicatorUpdate(displayId, refreshRate);
        } break;

        case IGNORE_DISPLAY_BRIGHTNESS_UPDATE_REQUESTS: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int32_t displayId = data.readInt32();
            bool ignore = data.readBool();
            int32_t error = ignoreDisplayBrightnessUpdateRequests(displayId, ignore);
            reply->writeInt32(error);
            return NO_ERROR;
        } break;

        case SET_DISPLAY_BRIGHTNESS_NITS: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int32_t displayId = data.readInt32();
            float nits = data.readFloat();
            int32_t error = setDisplayBrightnessNits(displayId, nits);
            reply->writeInt32(error);
            return NO_ERROR;
        } break;

        case SET_DISPLAY_BRIGHTNESS_DBV: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int32_t displayId = data.readInt32();
            uint32_t dbv = data.readUint32();
            int32_t error = setDisplayBrightnessDbv(displayId, dbv);
            reply->writeInt32(error);
            return NO_ERROR;
        } break;

        case DUMP_BUFFERS: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            uint32_t displayId = data.readUint32();
            int32_t count = data.readInt32();
            return dumpBuffers(displayId, count);
        } break;

        case SET_PRESENT_TIMEOUT_CONTROLLER: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int32_t displayId = data.readInt32();
            uint32_t controllerType = data.readUint32();
            int32_t error = setPresentTimeoutController(displayId, controllerType);
            reply->writeInt32(error);
            return NO_ERROR;
        } break;

        case SET_PRESENT_TIMEOUT_PARAMETERS: {
            CHECK_INTERFACE(IExynosHWCService, data, reply);
            int32_t displayId;
            uint32_t timeoutNs;
            RET_IF_ERR(data.readInt32(&displayId));
            RET_IF_ERR(data.readUint32(&timeoutNs));
            std::vector<std::pair<uint32_t, uint32_t>> settings;
            while (true) {
                uint32_t numOfWorks, intervalNs;
                if (data.readUint32(&numOfWorks)) {
                    break;
                }
                RET_IF_ERR(data.readUint32(&intervalNs));
                settings.emplace_back(std::make_pair(numOfWorks, intervalNs));
            }
            int32_t error = setPresentTimeoutParameters(displayId, timeoutNs, settings);
            reply->writeInt32(error);
            return NO_ERROR;
        } break;

        default:
            return BBinder::onTransact(code, data, reply, flags);
    }
}
} // namespace android
