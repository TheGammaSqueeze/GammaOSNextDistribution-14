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

#ifndef CPP_EVS_MANAGER_AIDL_INCLUDE_HALCAMERA_H
#define CPP_EVS_MANAGER_AIDL_INCLUDE_HALCAMERA_H

#include "stats/include/CameraUsageStats.h"

#include <aidl/android/hardware/automotive/evs/BnEvsCameraStream.h>
#include <aidl/android/hardware/automotive/evs/BufferDesc.h>
#include <aidl/android/hardware/automotive/evs/CameraParam.h>
#include <aidl/android/hardware/automotive/evs/EvsEventDesc.h>
#include <aidl/android/hardware/automotive/evs/EvsResult.h>
#include <aidl/android/hardware/automotive/evs/IEvsCamera.h>
#include <aidl/android/hardware/automotive/evs/Stream.h>
#include <utils/Mutex.h>

#include <deque>
#include <list>

namespace aidl::android::automotive::evs::implementation {

namespace aidlevs = ::aidl::android::hardware::automotive::evs;

class VirtualCamera;  // From VirtualCamera.h

// This class wraps the actual hardware IEvsCamera objects.  There is a one to many
// relationship between instances of this class and instances of the VirtualCamera class.
// This class implements the IEvsCameraStream interface so that it can receive the video
// stream from the hardware camera and distribute it to the associated VirtualCamera objects.
class HalCamera : public ::aidl::android::hardware::automotive::evs::BnEvsCameraStream {
public:
    // Methods from ::aidl::android::hardware::automotive::evs::IEvsCameraStream follow.
    ::ndk::ScopedAStatus deliverFrame(const std::vector<aidlevs::BufferDesc>& buffer) override;
    ::ndk::ScopedAStatus notify(const aidlevs::EvsEventDesc& event) override;

    HalCamera(const std::shared_ptr<aidlevs::IEvsCamera>& hwCamera, std::string deviceId = "",
              int32_t recordId = 0, aidlevs::Stream cfg = {}) :
          mHwCamera(hwCamera),
          mId(deviceId),
          mStreamConfig(cfg),
          mTimeCreatedMs(::android::uptimeMillis()),
          mUsageStats(new CameraUsageStats(recordId)) {}

    virtual ~HalCamera();

    // Factory methods for client VirtualCameras
    std::shared_ptr<VirtualCamera> makeVirtualCamera();
    bool ownVirtualCamera(const std::shared_ptr<VirtualCamera>& virtualCamera);
    void disownVirtualCamera(const VirtualCamera* virtualCamera);

    // Implementation details
    std::shared_ptr<aidlevs::IEvsCamera>& getHwCamera() { return mHwCamera; }
    unsigned getClientCount() { return mClients.size(); };
    std::string getId() { return mId; }
    aidlevs::Stream& getStreamConfig() { return mStreamConfig; }
    bool changeFramesInFlight(int delta);
    bool changeFramesInFlight(const std::vector<aidlevs::BufferDesc>& buffers, int* delta);
    void requestNewFrame(std::shared_ptr<VirtualCamera> virtualCamera, int64_t timestamp);

    ::ndk::ScopedAStatus clientStreamStarting();
    void clientStreamEnding(const VirtualCamera* client);
    ::ndk::ScopedAStatus doneWithFrame(aidlevs::BufferDesc buffer);
    ::ndk::ScopedAStatus setPrimaryClient(const std::shared_ptr<VirtualCamera>& virtualCamera);
    ::ndk::ScopedAStatus forcePrimaryClient(const std::shared_ptr<VirtualCamera>& virtualCamera);
    ::ndk::ScopedAStatus unsetPrimaryClient(const VirtualCamera* virtualCamera);
    ::ndk::ScopedAStatus setParameter(const std::shared_ptr<VirtualCamera>& virtualCamera,
                                      aidlevs::CameraParam id, int32_t* value);
    ::ndk::ScopedAStatus getParameter(aidlevs::CameraParam id, int32_t* value);

    // Returns a snapshot of collected usage statistics
    CameraUsageStatsRecord getStats() const;

    // Returns active stream configuration
    aidlevs::Stream getStreamConfiguration() const;

    // Returns a string showing the current status
    std::string toString(const char* indent = "") const;

    // Returns a string showing current stream configuration
    static std::string toString(aidlevs::Stream configuration, const char* indent = "");

private:
    std::shared_ptr<aidlevs::IEvsCamera> mHwCamera;
    std::list<std::weak_ptr<VirtualCamera>> mClients;

    enum {
        STOPPED,
        RUNNING,
        STOPPING,
    } mStreamState GUARDED_BY(mFrameMutex) = STOPPED;

    struct FrameRecord {
        uint32_t frameId;
        uint32_t refCount;
        FrameRecord(uint32_t id) : frameId(id), refCount(0){};
    };
    std::vector<FrameRecord> mFrames;
    std::weak_ptr<VirtualCamera> mPrimaryClient;
    std::string mId;
    aidlevs::Stream mStreamConfig;

    struct FrameRequest {
        std::weak_ptr<VirtualCamera> client;
        int64_t timestamp = -1;
    };

    // synchronization
    mutable std::mutex mFrameMutex;
    std::deque<FrameRequest> mNextRequests GUARDED_BY(mFrameMutex);

    // Time this object was created
    int64_t mTimeCreatedMs;

    // usage statistics to collect
    ::android::sp<CameraUsageStats> mUsageStats;
};

}  // namespace aidl::android::automotive::evs::implementation

#endif  // CPP_EVS_MANAGER_AIDL_INCLUDE_HALCAMERA_H
