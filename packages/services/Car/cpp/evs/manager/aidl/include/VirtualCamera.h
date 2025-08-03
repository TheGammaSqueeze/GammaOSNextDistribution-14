/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef CPP_EVS_MANAGER_AIDL_INCLUDE_VIRTUALCAMERA_H
#define CPP_EVS_MANAGER_AIDL_INCLUDE_VIRTUALCAMERA_H

#include <aidl/android/hardware/automotive/evs/BnEvsCamera.h>
#include <aidl/android/hardware/automotive/evs/BufferDesc.h>
#include <aidl/android/hardware/automotive/evs/CameraDesc.h>
#include <aidl/android/hardware/automotive/evs/CameraParam.h>
#include <aidl/android/hardware/automotive/evs/EvsEventDesc.h>
#include <aidl/android/hardware/automotive/evs/EvsResult.h>
#include <aidl/android/hardware/automotive/evs/IEvsCameraStream.h>
#include <aidl/android/hardware/automotive/evs/IEvsDisplay.h>
#include <aidl/android/hardware/automotive/evs/ParameterRange.h>
#include <aidl/android/hardware/automotive/evs/Stream.h>
#include <android-base/thread_annotations.h>

#include <condition_variable>
#include <deque>
#include <set>
#include <thread>  // NO_LINT
#include <unordered_map>

namespace aidl::android::automotive::evs::implementation {

namespace aidlevs = ::aidl::android::hardware::automotive::evs;

class HalCamera;  // From HalCamera.h

// This class represents an EVS camera to the client application.  As such it presents
// the IEvsCamera interface, and also proxies the frame delivery to the client's
// IEvsCameraStream object.
class VirtualCamera : public ::aidl::android::hardware::automotive::evs::BnEvsCamera {
public:
    // Methods from ::android::hardware::automotive::evs::IEvsCamera follow.
    ::ndk::ScopedAStatus doneWithFrame(const std::vector<aidlevs::BufferDesc>& buffers) override;
    ::ndk::ScopedAStatus forcePrimaryClient(
            const std::shared_ptr<aidlevs::IEvsDisplay>& display) override;
    ::ndk::ScopedAStatus getCameraInfo(aidlevs::CameraDesc* _aidl_return) override;
    ::ndk::ScopedAStatus getExtendedInfo(int32_t opaqueIdentifier,
                                         std::vector<uint8_t>* value) override;
    ::ndk::ScopedAStatus getIntParameter(aidlevs::CameraParam id,
                                         std::vector<int32_t>* value) override;
    ::ndk::ScopedAStatus getIntParameterRange(aidlevs::CameraParam id,
                                              aidlevs::ParameterRange* _aidl_return) override;
    ::ndk::ScopedAStatus getParameterList(std::vector<aidlevs::CameraParam>* _aidl_return) override;
    ::ndk::ScopedAStatus getPhysicalCameraInfo(const std::string& deviceId,
                                               aidlevs::CameraDesc* _aidl_return) override;
    ::ndk::ScopedAStatus importExternalBuffers(const std::vector<aidlevs::BufferDesc>& buffers,
                                               int32_t* _aidl_return) override;
    ::ndk::ScopedAStatus pauseVideoStream() override;
    ::ndk::ScopedAStatus resumeVideoStream() override;
    ::ndk::ScopedAStatus setExtendedInfo(int32_t opaqueIdentifier,
                                         const std::vector<uint8_t>& opaqueValue) override;
    ::ndk::ScopedAStatus setIntParameter(aidlevs::CameraParam id, int32_t value,
                                         std::vector<int32_t>* effectiveValue) override;
    ::ndk::ScopedAStatus setPrimaryClient() override;
    ::ndk::ScopedAStatus setMaxFramesInFlight(int32_t bufferCount) override;
    ::ndk::ScopedAStatus startVideoStream(
            const std::shared_ptr<aidlevs::IEvsCameraStream>& receiver) override;
    ::ndk::ScopedAStatus stopVideoStream() override;
    ::ndk::ScopedAStatus unsetPrimaryClient() override;

    explicit VirtualCamera(const std::vector<std::shared_ptr<HalCamera>>& halCameras);
    virtual ~VirtualCamera();

    unsigned getAllowedBuffers() { return mFramesAllowed; };
    bool isStreaming() {
        std::lock_guard lock(mMutex);
        return mStreamState == RUNNING;
    }
    std::vector<std::shared_ptr<HalCamera>> getHalCameras();
    void setDescriptor(aidlevs::CameraDesc* desc) { mDesc = desc; }

    // Proxy to receive frames and forward them to the client's stream
    bool notify(const aidlevs::EvsEventDesc& event);
    bool deliverFrame(const aidlevs::BufferDesc& bufDesc);

    // Dump current status to a given file descriptor
    std::string toString(const char* indent = "") const NO_THREAD_SAFETY_ANALYSIS;

private:
    void shutdown();
    bool isLogicalCamera() const { return mHalCamera.size() > 1; }
    bool isValid() const { return !mHalCamera.empty(); }

    // The low level camera interface that backs this proxy
    std::unordered_map<std::string, std::weak_ptr<HalCamera>> mHalCamera;

    std::shared_ptr<aidlevs::IEvsCameraStream> mStream;

    unsigned mFramesAllowed = 1;
    enum {
        STOPPED,
        RUNNING,
        STOPPING,
    } mStreamState GUARDED_BY(mMutex) = STOPPED;

    std::unordered_map<std::string, std::deque<aidlevs::BufferDesc>> mFramesHeld;
    std::thread mCaptureThread;
    aidlevs::CameraDesc* mDesc;

    mutable std::mutex mMutex;
    std::condition_variable mFramesReadySignal;
    std::set<std::string> mSourceCameras;
};

}  // namespace aidl::android::automotive::evs::implementation

#endif  // CPP_EVS_MANAGER_AIDL_INCLUDE_VIRTUALCAMERA_H
