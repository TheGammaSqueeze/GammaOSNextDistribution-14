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

#ifndef CPP_EVS_MANAGER_1_1_VIRTUALCAMERA_H_
#define CPP_EVS_MANAGER_1_1_VIRTUALCAMERA_H_

#include <android/hardware/automotive/evs/1.1/IEvsCamera.h>
#include <android/hardware/automotive/evs/1.1/IEvsCameraStream.h>
#include <android/hardware/automotive/evs/1.1/IEvsDisplay.h>
#include <android/hardware/automotive/evs/1.1/types.h>
#include <utils/Mutex.h>

#include <deque>
#include <set>
#include <thread>  // NOLINT
#include <unordered_map>

namespace android::automotive::evs::V1_1::implementation {

class HalCamera;  // From HalCamera.h

// This class represents an EVS camera to the client application.  As such it presents
// the IEvsCamera interface, and also proxies the frame delivery to the client's
// IEvsCameraStream object.
class VirtualCamera : public hardware::automotive::evs::V1_1::IEvsCamera {
public:
    explicit VirtualCamera(const std::vector<sp<HalCamera>>& halCameras);
    virtual ~VirtualCamera();

    unsigned getAllowedBuffers() { return mFramesAllowed; }
    bool isStreaming() { return mStreamState == RUNNING; }
    bool getVersion() const { return static_cast<int>(mStream_1_1 != nullptr); }
    std::vector<sp<HalCamera>> getHalCameras();
    void setDescriptor(hardware::automotive::evs::V1_1::CameraDesc* desc) { mDesc = desc; }

    // Proxy to receive frames and forward them to the client's stream
    bool notify(const hardware::automotive::evs::V1_1::EvsEventDesc& event);
    bool deliverFrame(const hardware::automotive::evs::V1_1::BufferDesc& bufDesc);

    // Methods from hardware::automotive::evs::V1_0::IEvsCamera follow.
    hardware::Return<void> getCameraInfo(getCameraInfo_cb _hidl_cb) override;
    hardware::Return<hardware::automotive::evs::V1_0::EvsResult> setMaxFramesInFlight(
            uint32_t bufferCount) override;
    hardware::Return<hardware::automotive::evs::V1_0::EvsResult> startVideoStream(
            const ::android::sp<hardware::automotive::evs::V1_0::IEvsCameraStream>& stream)
            override;
    hardware::Return<void> doneWithFrame(
            const hardware::automotive::evs::V1_0::BufferDesc& buffer) override;
    hardware::Return<void> stopVideoStream() override;
    hardware::Return<int32_t> getExtendedInfo(uint32_t opaqueIdentifier) override;
    hardware::Return<hardware::automotive::evs::V1_0::EvsResult> setExtendedInfo(
            uint32_t opaqueIdentifier, int32_t opaqueValue) override;

    // Methods from hardware::automotive::evs::V1_1::IEvsCamera follow.
    hardware::Return<void> getCameraInfo_1_1(getCameraInfo_1_1_cb _hidl_cb) override;
    hardware::Return<void> getPhysicalCameraInfo(const hardware::hidl_string& deviceId,
                                                 getPhysicalCameraInfo_cb _hidl_cb) override;
    hardware::Return<hardware::automotive::evs::V1_0::EvsResult> doneWithFrame_1_1(
            const hardware::hidl_vec<hardware::automotive::evs::V1_1::BufferDesc>& buffer) override;
    hardware::Return<hardware::automotive::evs::V1_0::EvsResult> pauseVideoStream() override {
        return hardware::automotive::evs::V1_0::EvsResult::UNDERLYING_SERVICE_ERROR;
    }
    hardware::Return<hardware::automotive::evs::V1_0::EvsResult> resumeVideoStream() override {
        return hardware::automotive::evs::V1_0::EvsResult::UNDERLYING_SERVICE_ERROR;
    }
    hardware::Return<hardware::automotive::evs::V1_0::EvsResult> setMaster() override;
    hardware::Return<hardware::automotive::evs::V1_0::EvsResult> forceMaster(
            const sp<hardware::automotive::evs::V1_0::IEvsDisplay>& display) override;
    hardware::Return<hardware::automotive::evs::V1_0::EvsResult> unsetMaster() override;
    hardware::Return<void> getParameterList(getParameterList_cb _hidl_cb) override;
    hardware::Return<void> getIntParameterRange(hardware::automotive::evs::V1_1::CameraParam id,
                                                getIntParameterRange_cb _hidl_cb) override;
    hardware::Return<void> setIntParameter(hardware::automotive::evs::V1_1::CameraParam id,
                                           int32_t value, setIntParameter_cb _hidl_cb) override;
    hardware::Return<void> getIntParameter(hardware::automotive::evs::V1_1::CameraParam id,
                                           getIntParameter_cb _hidl_cb) override;
    hardware::Return<hardware::automotive::evs::V1_0::EvsResult> setExtendedInfo_1_1(
            uint32_t opaqueIdentifier, const hardware::hidl_vec<uint8_t>& opaqueValue) override;
    hardware::Return<void> getExtendedInfo_1_1(uint32_t opaqueIdentifier,
                                               getExtendedInfo_1_1_cb _hidl_cb) override;
    hardware::Return<void> importExternalBuffers(
            const hardware::hidl_vec<hardware::automotive::evs::V1_1::BufferDesc>& buffers,
            importExternalBuffers_cb _hidl_cb) override;

    // Dump current status to a given file descriptor
    std::string toString(const char* indent = "") const;

private:
    void shutdown();

    // The low level camera interface that backs this proxy
    // Use by camera thread, once constructed, do not update/change it
    // until camera thread quits.
    std::unordered_map<std::string, wp<HalCamera>> mHalCamera;

    sp<hardware::automotive::evs::V1_0::IEvsCameraStream> mStream;
    sp<hardware::automotive::evs::V1_1::IEvsCameraStream> mStream_1_1;

    unsigned mFramesAllowed = 1;

    // Access by camera thread also, use with cautions for race conditions.
    enum {
        STOPPED,
        RUNNING,
        STOPPING,
    } mStreamState;
    mutable std::recursive_mutex mFramesHeldMutex;
    std::unordered_map<std::string, std::deque<hardware::automotive::evs::V1_1::BufferDesc>>
            mFramesHeld;
    hardware::automotive::evs::V1_1::CameraDesc* mDesc;
    std::thread mCaptureThread;

    mutable std::mutex mFrameDeliveryMutex;
    std::condition_variable mFramesReadySignal;
    std::set<std::string> mSourceCameras GUARDED_BY(mFrameDeliveryMutex);
};

}  // namespace android::automotive::evs::V1_1::implementation

#endif  // CPP_EVS_MANAGER_1_1_VIRTUALCAMERA_H_
