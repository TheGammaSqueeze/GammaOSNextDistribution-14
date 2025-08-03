/*
 * Copyright 2021 The Android Open Source Project
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

#include "StreamHandler.h"

#include <aidl/android/hardware/automotive/evs/EvsEventType.h>
#include <aidl/android/hardware/automotive/evs/EvsResult.h>
#include <aidl/android/hardware/common/NativeHandle.h>
#include <android-base/chrono_utils.h>
#include <android-base/logging.h>
#include <android/hardware_buffer.h>
#include <android/hardware_buffer_jni.h>
#include <vndk/hardware_buffer.h>

namespace {

using ::aidl::android::hardware::automotive::evs::BufferDesc;
using ::aidl::android::hardware::automotive::evs::EvsEventDesc;
using ::aidl::android::hardware::automotive::evs::EvsEventType;
using ::aidl::android::hardware::automotive::evs::EvsResult;
using ::aidl::android::hardware::automotive::evs::IEvsCamera;
using ::aidl::android::hardware::common::NativeHandle;
using ::aidl::android::hardware::graphics::common::HardwareBuffer;
using ::android::base::ScopedLockAssertion;

NativeHandle dupNativeHandle(const NativeHandle& handle, bool doDup) {
    NativeHandle dup;

    dup.fds = std::vector<::ndk::ScopedFileDescriptor>(handle.fds.size());
    if (!doDup) {
        for (auto i = 0; i < handle.fds.size(); ++i) {
            dup.fds.at(i).set(handle.fds[i].get());
        }
    } else {
        for (auto i = 0; i < handle.fds.size(); ++i) {
            dup.fds[i] = std::move(handle.fds[i].dup());
        }
    }
    dup.ints = handle.ints;

    return std::move(dup);
}

HardwareBuffer dupHardwareBuffer(const HardwareBuffer& buffer, bool doDup) {
    HardwareBuffer dup = {
            .description = buffer.description,
            .handle = dupNativeHandle(buffer.handle, doDup),
    };

    return std::move(dup);
}

BufferDesc dupBufferDesc(const BufferDesc& src, bool doDup) {
    BufferDesc dup = {
            .buffer = dupHardwareBuffer(src.buffer, doDup),
            .pixelSizeBytes = src.pixelSizeBytes,
            .bufferId = src.bufferId,
            .deviceId = src.deviceId,
            .timestamp = src.timestamp,
            .metadata = src.metadata,
    };

    return std::move(dup);
}

}  // namespace

namespace android::automotive::evs {

StreamHandler::StreamHandler(const std::shared_ptr<IEvsCamera>& camObj,
                             EvsServiceCallback* callback, int maxNumFramesInFlight) :
      mEvsCamera(camObj), mCallback(callback), mMaxNumFramesInFlightPerClient(maxNumFramesInFlight),
      mNumClients(0) {
    if (!camObj) {
        LOG(ERROR) << "IEvsCamera is invalid.";
    } else {
        // We rely on the camera having at least two buffers available since we'll hold one and
        // expect the camera to be able to capture a new image in the background.
        auto status = camObj->setMaxFramesInFlight(maxNumFramesInFlight);
        if (!status.isOk()) {
            LOG(ERROR) << "Failed to adjust the maximum number of frames in flight: "
                       << status.getServiceSpecificError();
        }
    }
}

/*
 * Shuts down a stream handler
 */
StreamHandler::~StreamHandler() {
    shutdown();
}

/*
 * Stops an active stream and releases the camera device in use
 */
void StreamHandler::shutdown() {
    // Make sure we're not still streaming
    blockingStopStream();

    // At this point, the receiver thread is no longer running, so we can safely drop
    // our remote object references so they can be freed
    mEvsCamera = nullptr;
}

/*
 * Requests EVS to start a video stream
 */
bool StreamHandler::startStream() {
    std::lock_guard<std::mutex> lock(mLock);
    if (!mRunning) {
        auto status = mEvsCamera->startVideoStream(ref<StreamHandler>());
        if (!status.isOk()) {
            LOG(ERROR) << "StreamHandler failed to start a video stream: "
                       << status.getServiceSpecificError();
            return false;
        }

        // This is the first client of this stream.
        mNumClients = 1;

        // Marks ourselves as running
        mRunning = true;
    } else {
        // Increase a number of active clients and the max. number of frames in
        // flight.
        int desired = (++mNumClients) * mMaxNumFramesInFlightPerClient;
        auto status = mEvsCamera->setMaxFramesInFlight(desired);
        if (!status.isOk()) {
            LOG(ERROR) << "Failed to adjust the maximum number of frames in flight for "
                       << mNumClients << " clients. Error: " << status.getServiceSpecificError();
            // Decrease a number of clients back.
            mNumClients -= 1;
            return false;
        }
    }

    return true;
}

/*
 * Requests to stop a video stream and waits for a confirmation
 */
void StreamHandler::blockingStopStream() {
    {
        std::lock_guard lock(mLock);
        if (!mRunning) {
            // Nothing to do.
            return;
        }

        if (mNumClients > 1) {
            // Decrease a number of active clients and return.
            --mNumClients;
            return;

        }

        // Return all buffers currently held by us.
        auto it = mReceivedBuffers.begin();
        while (it != mReceivedBuffers.end()) {
            // Packages a returned buffer and sends it back to the camera
            std::vector<BufferDesc> frames(1);
            frames[0] = std::move(*it);
            auto status = mEvsCamera->doneWithFrame(frames);
            if (!status.isOk()) {
                LOG(WARNING) << "Failed to return a frame to EVS service; "
                             << "this may leak the memory: " << status.getServiceSpecificError();
            }

            it = mReceivedBuffers.erase(it);
        }
    }

    auto status = mEvsCamera->stopVideoStream();
    if (!status.isOk()) {
        LOG(WARNING) << "stopVideoStream() failed but ignored.";
    }

    // Now, we are waiting for the ack from EvsManager service.
    {
        std::unique_lock<std::mutex> lock(mLock);
        ScopedLockAssertion lock_assertion(mLock);
        while (mRunning) {
            if (!mCondition.wait_for(lock, 1s, [this]() REQUIRES(mLock) { return !mRunning; })) {
                LOG(WARNING) << "STREAM_STOPPED event timer expired.  EVS service may die.";
                break;
            }
        }

        // Decrease a number of active clients.
        --mNumClients;
    }
}

bool StreamHandler::isRunning() {
    std::lock_guard<std::mutex> lock(mLock);
    return mRunning;
}

void StreamHandler::doneWithFrame(int bufferId) {
    BufferDesc bufferToReturn;
    {
        std::lock_guard<std::mutex> lock(mLock);
        auto it = std::find_if(mReceivedBuffers.begin(), mReceivedBuffers.end(),
                               [bufferId](BufferDesc& b) { return b.bufferId == bufferId; });
        if (it == mReceivedBuffers.end()) {
            LOG(DEBUG) << "Ignores a request to return unknown buffer";
            return;
        }

        bufferToReturn = std::move(*it);
        mReceivedBuffers.erase(it);
    }

    // Packages a returned buffer and sends it back to the camera
    std::vector<BufferDesc> frames(1);
    frames[0] = std::move(bufferToReturn);
    if (auto status = mEvsCamera->doneWithFrame(frames); !status.isOk()) {
        LOG(ERROR) << "Status = " << status.getStatus();
        LOG(ERROR) << "Failed to return a frame (id = " << bufferId
                   << " to EVS service; this may leak the memory: "
                   << status.getServiceSpecificError();
    }
}

void StreamHandler::doneWithFrame(const BufferDesc& buffer) {
    return doneWithFrame(buffer.bufferId);
}

::ndk::ScopedAStatus StreamHandler::deliverFrame(const std::vector<BufferDesc>& buffers) {
    LOG(DEBUG) << "Received frames from the camera, bufferId = " << buffers[0].bufferId;

    const BufferDesc& bufferToUse = buffers[0];
    size_t numBuffersInUse;
    {
        std::lock_guard<std::mutex> lock(mLock);
        numBuffersInUse = mReceivedBuffers.size();
    }

    if (numBuffersInUse >= mMaxNumFramesInFlightPerClient) {
        // We're holding more than what allowed; returns this buffer
        // immediately.
        doneWithFrame(bufferToUse);
        return ::ndk::ScopedAStatus::ok();
    }

    {
        std::lock_guard<std::mutex> lock(mLock);
        // Records a new frameDesc and forwards to clients
        mReceivedBuffers.push_back(dupBufferDesc(bufferToUse, /* dup= */ true));
        LOG(DEBUG) << "Got buffer " << bufferToUse.bufferId
                   << ", total = " << mReceivedBuffers.size();

        // Notify anybody who cares that things have changed
        mCondition.notify_all();
    }

    // Forwards a new frame
    if (!mCallback->onNewFrame(bufferToUse)) {
        doneWithFrame(bufferToUse);
        return ::ndk::ScopedAStatus::fromServiceSpecificError(
                static_cast<int32_t>(EvsResult::INVALID_ARG));
    }

    return ::ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus StreamHandler::notify(const EvsEventDesc& event) {
    switch (event.aType) {
        case EvsEventType::STREAM_STOPPED: {
            {
                std::lock_guard<std::mutex> lock(mLock);
                // Signal that the last frame has been received and the stream is stopped
                mRunning = false;
            }
            LOG(DEBUG) << "Received a STREAM_STOPPED event";
            break;
        }
        case EvsEventType::PARAMETER_CHANGED:
            LOG(DEBUG) << "Camera parameter 0x" << std::hex << event.payload[0] << " is set to 0x"
                       << std::hex << event.payload[1];
            break;
        // Below events are ignored in reference implementation.
        case EvsEventType::STREAM_STARTED:
            [[fallthrough]];
        case EvsEventType::FRAME_DROPPED:
            [[fallthrough]];
        case EvsEventType::TIMEOUT:
            LOG(INFO) << "Event 0x" << std::hex << static_cast<int32_t>(event.aType)
                      << " from " << (event.deviceId.empty() ? "Unknown" : event.deviceId)
                      << " is received but ignored";
            break;
        default:
            LOG(ERROR) << "Unknown event id 0x" << std::hex << static_cast<int32_t>(event.aType);
            break;
    }

    mCallback->onNewEvent(event);
    return ::ndk::ScopedAStatus::ok();
}

}  // namespace android::automotive::evs
