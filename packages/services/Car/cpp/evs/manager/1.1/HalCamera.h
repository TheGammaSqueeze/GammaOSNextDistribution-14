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

#ifndef CPP_EVS_MANAGER_1_1_HALCAMERA_H_
#define CPP_EVS_MANAGER_1_1_HALCAMERA_H_

#include "stats/CameraUsageStats.h"

#include <android/hardware/automotive/evs/1.1/IEvsCamera.h>
#include <android/hardware/automotive/evs/1.1/IEvsCameraStream.h>
#include <android/hardware/automotive/evs/1.1/types.h>
#include <utils/Mutex.h>
#include <utils/SystemClock.h>

#include <deque>
#include <list>
#include <thread>  // NOLINT
#include <unordered_map>

namespace android::automotive::evs::V1_1::implementation {

class VirtualCamera;  // From VirtualCamera.h

// This class wraps the actual hardware IEvsCamera objects.  There is a one to many
// relationship between instances of this class and instances of the VirtualCamera class.
// This class implements the IEvsCameraStream interface so that it can receive the video
// stream from the hardware camera and distribute it to the associated VirtualCamera objects.
class HalCamera : public ::android::hardware::automotive::evs::V1_1::IEvsCameraStream {
public:
    HalCamera(sp<hardware::automotive::evs::V1_1::IEvsCamera> hwCamera, std::string deviceId = "",
              int32_t recordId = 0, hardware::camera::device::V3_2::Stream cfg = {}) :
          mHwCamera(hwCamera),
          mId(deviceId),
          mStreamConfig(cfg),
          mTimeCreatedMs(android::uptimeMillis()),
          mUsageStats(new CameraUsageStats(recordId)) {
        mCurrentRequests = &mFrameRequests[0];
        mNextRequests = &mFrameRequests[1];
    }

    virtual ~HalCamera();

    // Factory methods for client VirtualCameras
    sp<VirtualCamera> makeVirtualCamera();
    bool ownVirtualCamera(sp<VirtualCamera> virtualCamera);
    void disownVirtualCamera(sp<VirtualCamera> virtualCamera);
    void disownVirtualCamera(const VirtualCamera* virtualCamera);

    // Implementation details
    sp<hardware::automotive::evs::V1_0::IEvsCamera> getHwCamera() { return mHwCamera; }
    unsigned getClientCount() { return mClients.size(); }
    std::string getId() { return mId; }
    hardware::camera::device::V3_2::Stream& getStreamConfig() { return mStreamConfig; }
    bool changeFramesInFlight(int delta);
    bool changeFramesInFlight(
            const hardware::hidl_vec<hardware::automotive::evs::V1_1::BufferDesc>& buffers,
            int* delta);
    void requestNewFrame(sp<VirtualCamera> virtualCamera, const int64_t timestamp);

    hardware::Return<hardware::automotive::evs::V1_0::EvsResult> clientStreamStarting();
    void clientStreamEnding(const VirtualCamera* client);
    hardware::Return<void> doneWithFrame(const hardware::automotive::evs::V1_0::BufferDesc& buffer);
    hardware::Return<void> doneWithFrame(const hardware::automotive::evs::V1_1::BufferDesc& buffer);
    hardware::Return<hardware::automotive::evs::V1_0::EvsResult> setMaster(
            sp<VirtualCamera> virtualCamera);
    hardware::Return<hardware::automotive::evs::V1_0::EvsResult> forceMaster(
            sp<VirtualCamera> virtualCamera);
    hardware::Return<hardware::automotive::evs::V1_0::EvsResult> unsetMaster(
            const VirtualCamera* virtualCamera);
    hardware::Return<hardware::automotive::evs::V1_0::EvsResult> setParameter(
            sp<VirtualCamera> virtualCamera, hardware::automotive::evs::V1_1::CameraParam id,
            int32_t* value);
    hardware::Return<hardware::automotive::evs::V1_0::EvsResult> getParameter(
            hardware::automotive::evs::V1_1::CameraParam id, int32_t* value);

    // Returns a snapshot of collected usage statistics
    CameraUsageStatsRecord getStats() const;

    // Returns active stream configuration
    hardware::camera::device::V3_2::Stream getStreamConfiguration() const;

    // Returns a string showing the current status
    std::string toString(const char* indent = "") const;

    // Returns a string showing current stream configuration
    static std::string toString(hardware::camera::device::V3_2::Stream configuration,
                                const char* indent = "");

    // Methods from ::android::hardware::automotive::evs::V1_0::IEvsCameraStream follow.
    hardware::Return<void> deliverFrame(
            const hardware::automotive::evs::V1_0::BufferDesc& buffer) override;

    // Methods from ::android::hardware::automotive::evs::V1_1::IEvsCameraStream follow.
    hardware::Return<void> deliverFrame_1_1(
            const hardware::hidl_vec<hardware::automotive::evs::V1_1::BufferDesc>& buffer) override;
    hardware::Return<void> notify(
            const hardware::automotive::evs::V1_1::EvsEventDesc& event) override;

private:
    sp<hardware::automotive::evs::V1_1::IEvsCamera> mHwCamera;
    std::list<wp<VirtualCamera>> mClients;  // Weak pointers -> objects destruct if client dies

    enum {
        STOPPED,
        RUNNING,
        STOPPING,
    } mStreamState GUARDED_BY(mFrameMutex) = STOPPED;

    struct FrameRecord {
        uint32_t frameId;
        uint32_t refCount;
        explicit FrameRecord(uint32_t id) : frameId(id), refCount(0) {}
    };

    std::vector<FrameRecord> mFrames;
    wp<VirtualCamera> mPrimaryClient = nullptr;
    std::string mId;
    hardware::camera::device::V3_2::Stream mStreamConfig;

    struct FrameRequest {
        wp<VirtualCamera> client = nullptr;
        int64_t timestamp = -1;
    };

    void cancelCaptureRequestFromClientLocked(std::deque<FrameRequest>* requests,
                                              const VirtualCamera* client) REQUIRES(mFrameMutex);

    // synchronization
    mutable std::mutex mFrameMutex;
    std::deque<FrameRequest> mFrameRequests[2] GUARDED_BY(mFrameMutex);
    std::deque<FrameRequest>* mCurrentRequests PT_GUARDED_BY(mFrameMutex);
    std::deque<FrameRequest>* mNextRequests PT_GUARDED_BY(mFrameMutex);

    // Time this object was created
    int64_t mTimeCreatedMs;

    // usage statistics to collect
    android::sp<CameraUsageStats> mUsageStats;
};

}  // namespace android::automotive::evs::V1_1::implementation

#endif  // CPP_EVS_MANAGER_1_1_HALCAMERA_H_
