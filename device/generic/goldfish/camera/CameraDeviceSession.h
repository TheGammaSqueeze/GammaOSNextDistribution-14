/*
 * Copyright (C) 2023 The Android Open Source Project
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

#pragma once

#include <chrono>
#include <deque>
#include <memory>
#include <mutex>
#include <optional>
#include <thread>

#include <aidl/android/hardware/camera/common/Status.h>
#include <aidl/android/hardware/camera/device/BnCameraDeviceSession.h>

#include <fmq/AidlMessageQueue.h>

#include "BlockingQueue.h"
#include "HwCamera.h"
#include "StreamBufferCache.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {

using aidl::android::hardware::camera::common::Status;

using aidl::android::hardware::camera::device::BnCameraDeviceSession;
using aidl::android::hardware::camera::device::BufferCache;
using aidl::android::hardware::camera::device::CameraMetadata;
using aidl::android::hardware::camera::device::CameraOfflineSessionInfo;
using aidl::android::hardware::camera::device::CaptureRequest;
using aidl::android::hardware::camera::device::CaptureResult;
using aidl::android::hardware::camera::device::HalStream;
using aidl::android::hardware::camera::device::ICameraDeviceCallback;
using aidl::android::hardware::camera::device::ICameraOfflineSession;
using aidl::android::hardware::camera::device::RequestTemplate;
using aidl::android::hardware::camera::device::StreamBuffer;
using aidl::android::hardware::camera::device::StreamConfiguration;

using aidl::android::hardware::common::fmq::MQDescriptor;
using aidl::android::hardware::common::fmq::SynchronizedReadWrite;

using aidl::android::hardware::graphics::common::BufferUsage;
using aidl::android::hardware::graphics::common::PixelFormat;

using ndk::ScopedAStatus;

struct CameraDevice;

struct CameraDeviceSession : public BnCameraDeviceSession {
    CameraDeviceSession(std::shared_ptr<CameraDevice> parent,
                        std::shared_ptr<ICameraDeviceCallback> cb,
                        hw::HwCamera& hwCamera);
    ~CameraDeviceSession() override;

    ScopedAStatus close() override;
    ScopedAStatus configureStreams(const StreamConfiguration& cfg,
                                   std::vector<HalStream>* halStreamsOut) override;
    ScopedAStatus constructDefaultRequestSettings(RequestTemplate tpl,
                                                  CameraMetadata* metadata) override;
    ScopedAStatus flush() override;
    ScopedAStatus getCaptureRequestMetadataQueue(
        MQDescriptor<int8_t, SynchronizedReadWrite>* desc) override;
    ScopedAStatus getCaptureResultMetadataQueue(
        MQDescriptor<int8_t, SynchronizedReadWrite>* desc) override;
    ScopedAStatus isReconfigurationRequired(const CameraMetadata& oldSessionParams,
                                            const CameraMetadata& newSessionParams,
                                            bool* result) override;
    ScopedAStatus processCaptureRequest(const std::vector<CaptureRequest>& requests,
                                        const std::vector<BufferCache>& cachesToRemove,
                                        int32_t* count) override;
    ScopedAStatus signalStreamFlush(const std::vector<int32_t>& streamIds,
                                    int32_t streamConfigCounter) override;
    ScopedAStatus switchToOffline(const std::vector<int32_t>& streamsToKeep,
                                  CameraOfflineSessionInfo* offlineSessionInfo,
                                  std::shared_ptr<ICameraOfflineSession>* session) override;
    ScopedAStatus repeatingRequestEnd(int32_t frameNumber,
                                      const std::vector<int32_t>& streamIds) override;

    static bool isStreamCombinationSupported(const StreamConfiguration& cfg,
                                             hw::HwCamera& hwCamera);

private:
    using MetadataQueue = AidlMessageQueue<int8_t, SynchronizedReadWrite>;
    using HwCaptureRequest = hw::HwCaptureRequest;

    struct DelayedCaptureResult {
        hw::DelayedStreamBuffer delayedBuffer;
        int frameNumber;
    };

    void closeImpl();
    void flushImpl(std::chrono::steady_clock::time_point start);
    int waitFlushingDone(std::chrono::steady_clock::time_point start);
    static std::pair<Status, std::vector<HalStream>>
        configureStreamsStatic(const StreamConfiguration& cfg,
                               hw::HwCamera& hwCamera);
    Status processOneCaptureRequest(const CaptureRequest& request);
    void captureThreadLoop();
    void delayedCaptureThreadLoop();
    bool popCaptureRequest(HwCaptureRequest* req);
    struct timespec captureOneFrame(struct timespec nextFrameT, HwCaptureRequest req);
    void disposeCaptureRequest(HwCaptureRequest req);
    void consumeCaptureResult(CaptureResult cr);
    void notifyBuffersReturned(size_t n);

    const std::shared_ptr<CameraDevice> mParent;
    const std::shared_ptr<ICameraDeviceCallback> mCb;
    hw::HwCamera& mHwCamera;
    MetadataQueue mRequestQueue;
    MetadataQueue mResultQueue;
    std::mutex mResultQueueMutex;

    StreamBufferCache mStreamBufferCache;

    BlockingQueue<HwCaptureRequest> mCaptureRequests;
    BlockingQueue<DelayedCaptureResult> mDelayedCaptureResults;

    size_t mNumBuffersInFlight = 0;
    std::condition_variable mNoBuffersInFlight;
    std::mutex mNumBuffersInFlightMtx;

    std::thread mCaptureThread;
    std::thread mDelayedCaptureThread;

    std::atomic<bool> mFlushing = false;
};

}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
