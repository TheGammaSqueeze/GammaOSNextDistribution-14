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

#include "HalCamera.h"

#include "Enumerator.h"
#include "VirtualCamera.h"
#include "utils/include/Utils.h"

#include <android-base/file.h>
#include <android-base/logging.h>

namespace aidl::android::automotive::evs::implementation {

using ::aidl::android::hardware::automotive::evs::BufferDesc;
using ::aidl::android::hardware::automotive::evs::CameraParam;
using ::aidl::android::hardware::automotive::evs::EvsEventDesc;
using ::aidl::android::hardware::automotive::evs::EvsEventType;
using ::aidl::android::hardware::automotive::evs::EvsResult;
using ::aidl::android::hardware::automotive::evs::Stream;
using ::android::base::StringAppendF;
using ::ndk::ScopedAStatus;

// TODO(b/213108625):
// We need to hook up death monitoring to detect stream death so we can attempt a reconnect

HalCamera::~HalCamera() {
    // Reports the usage statistics before the destruction
    // EvsUsageStatsReported atom is defined in
    // frameworks/proto_logging/stats/atoms.proto
    mUsageStats->writeStats();
}

std::shared_ptr<VirtualCamera> HalCamera::makeVirtualCamera() {
    // Create the client camera interface object
    std::vector<std::shared_ptr<HalCamera>> sourceCameras;
    sourceCameras.reserve(1);
    sourceCameras.push_back(std::move(ref<HalCamera>()));
    std::shared_ptr<VirtualCamera> client =
            ::ndk::SharedRefBase::make<VirtualCamera>(sourceCameras);
    if (!client || !ownVirtualCamera(client)) {
        LOG(ERROR) << "Failed to create client camera object";
        return nullptr;
    }

    return std::move(client);
}

bool HalCamera::ownVirtualCamera(const std::shared_ptr<VirtualCamera>& virtualCamera) {
    if (!virtualCamera) {
        LOG(ERROR) << "A virtual camera object is invalid";
        return false;
    }

    // Make sure we have enough buffers available for all our clients
    if (!changeFramesInFlight(virtualCamera->getAllowedBuffers())) {
        // Gah!  We couldn't get enough buffers, so we can't support this virtualCamera
        // Null the pointer, dropping our reference, thus destroying the virtualCamera object
        return false;
    }

    // Add this virtualCamera to our ownership list via weak pointer
    mClients.push_back(virtualCamera);

    // Update statistics
    mUsageStats->updateNumClients(mClients.size());

    return true;
}

void HalCamera::disownVirtualCamera(const VirtualCamera* clientToDisown) {
    // Ignore calls with null pointers
    if (!clientToDisown) {
        LOG(WARNING) << "Ignoring disownVirtualCamera call with null pointer";
        return;
    }

    // Remove the virtual camera from our client list
    const auto clientCount = mClients.size();
    mClients.remove_if([clientToDisown](std::weak_ptr<VirtualCamera>& client) {
        auto current = client.lock();
        return current == nullptr || current.get() == clientToDisown;
    });

    if (clientCount == mClients.size()) {
        LOG(WARNING) << "Couldn't find camera in our client list to remove it; "
                     << "this client may be removed already.";
    }

    // Recompute the number of buffers required with the target camera removed from the list
    if (!changeFramesInFlight(/* delta= */ 0)) {
        LOG(WARNING) << "Error when trying to reduce the in flight buffer count";
    }

    // Update statistics
    mUsageStats->updateNumClients(mClients.size());
}

bool HalCamera::changeFramesInFlight(int delta) {
    // Walk all our clients and count their currently required frames
    unsigned bufferCount = 0;
    for (auto&& client : mClients) {
        std::shared_ptr<VirtualCamera> virtCam = client.lock();
        if (virtCam) {
            bufferCount += virtCam->getAllowedBuffers();
        }
    }

    // Add the requested delta
    bufferCount += delta;

    // Never drop below 1 buffer -- even if all client cameras get closed
    if (bufferCount < 1) {
        bufferCount = 1;
    }

    // Ask the hardware for the resulting buffer count
    if (!mHwCamera->setMaxFramesInFlight(bufferCount).isOk()) {
        return false;
    }

    // Update the size of our array of outstanding frame records
    std::vector<FrameRecord> newRecords;
    newRecords.reserve(bufferCount);

    // Copy and compact the old records that are still active
    for (const auto& rec : mFrames) {
        if (rec.refCount > 0) {
            newRecords.push_back(std::move(rec));
        }
    }
    if (newRecords.size() > static_cast<unsigned>(bufferCount)) {
        LOG(WARNING) << "We found more frames in use than requested.";
    }

    mFrames.swap(newRecords);
    return true;
}

bool HalCamera::changeFramesInFlight(const std::vector<BufferDesc>& buffers, int* delta) {
    // Return immediately if a list is empty.
    if (buffers.empty()) {
        LOG(DEBUG) << "No external buffers to add.";
        return true;
    }

    // Walk all our clients and count their currently required frames
    auto bufferCount = 0;
    for (auto&& client : mClients) {
        std::shared_ptr<VirtualCamera> virtCam = client.lock();
        if (virtCam) {
            bufferCount += virtCam->getAllowedBuffers();
        }
    }

    // Ask the hardware for the resulting buffer count
    if (!mHwCamera->importExternalBuffers(buffers, delta).isOk()) {
        LOG(ERROR) << "Failed to add external capture buffers.";
        return false;
    }

    bufferCount += *delta;

    // Update the size of our array of outstanding frame records
    std::vector<FrameRecord> newRecords;
    newRecords.reserve(bufferCount);

    // Copy and compact the old records that are still active
    for (const auto& rec : mFrames) {
        if (rec.refCount > 0) {
            newRecords.push_back(std::move(rec));
        }
    }

    if (newRecords.size() > static_cast<unsigned>(bufferCount)) {
        LOG(WARNING) << "We found more frames in use than requested.";
    }

    mFrames.swap(newRecords);

    return true;
}

void HalCamera::requestNewFrame(std::shared_ptr<VirtualCamera> client, int64_t lastTimestamp) {
    FrameRequest req;
    req.client = client;
    req.timestamp = lastTimestamp;

    std::lock_guard<std::mutex> lock(mFrameMutex);
    mNextRequests.push_back(req);
}

ScopedAStatus HalCamera::clientStreamStarting() {
    {
        std::lock_guard lock(mFrameMutex);
        if (mStreamState == RUNNING) {
            // This camera device is already active.
            return ScopedAStatus::ok();
        }

        if (mStreamState == STOPPED) {
            // Try to start a video stream.
            ScopedAStatus status = mHwCamera->startVideoStream(ref<HalCamera>());
            if (status.isOk()) {
                mStreamState = RUNNING;
            }
            return status;
        }

        // We cannot start a video stream.
        return Utils::buildScopedAStatusFromEvsResult(
                mStreamState == STOPPING ? EvsResult::RESOURCE_BUSY
                                         : EvsResult::UNDERLYING_SERVICE_ERROR);
    }
}

void HalCamera::clientStreamEnding(const VirtualCamera* client) {
    {
        std::lock_guard<std::mutex> lock(mFrameMutex);
        if (mStreamState != RUNNING) {
            // We are being stopped or stopped already.
            return;
        }

        mNextRequests.erase(std::remove_if(mNextRequests.begin(), mNextRequests.end(),
                                           [client](const auto& r) {
                                               return r.client.lock().get() == client;
                                           }),
                            mNextRequests.end());
    }

    // Do we still have a running client?
    bool stillRunning = false;
    for (auto&& client : mClients) {
        std::shared_ptr<VirtualCamera> virtCam = client.lock();
        if (virtCam) {
            stillRunning |= virtCam->isStreaming();
        }
    }

    // If not, then stop the hardware stream
    if (!stillRunning) {
        {
            std::lock_guard lock(mFrameMutex);
            mStreamState = STOPPING;
        }
        auto status = mHwCamera->stopVideoStream();
        if (!status.isOk()) {
            LOG(WARNING) << "Failed to stop a video stream, error = "
                         << status.getServiceSpecificError();
        }
    }
}

ScopedAStatus HalCamera::doneWithFrame(BufferDesc buffer) {
    std::lock_guard<std::mutex> lock(mFrameMutex);

    // Find this frame in our list of outstanding frames
    auto it = std::find_if(mFrames.begin(), mFrames.end(),
                           [id = buffer.bufferId](const FrameRecord& rec) {
                               return rec.frameId == id;
                           });
    if (it == mFrames.end()) {
        LOG(WARNING) << "We got a frame back with an ID we don't recognize!";
        return ScopedAStatus::ok();
    }

    if (it->refCount < 1) {
        LOG(WARNING) << "Buffer " << buffer.bufferId
                     << " is returned with a zero reference counter.";
        return ScopedAStatus::ok();
    }

    // Are there still clients using this buffer?
    it->refCount = it->refCount - 1;
    if (it->refCount > 0) {
        LOG(DEBUG) << "Buffer " << buffer.bufferId << " is still being used by " << it->refCount
                   << " other client(s).";
        return ScopedAStatus::ok();
    }

    // Since all our clients are done with this buffer, return it to the device layer
    std::vector<BufferDesc> buffersToReturn(1);
    buffersToReturn[0] = std::move(buffer);
    auto status = mHwCamera->doneWithFrame(buffersToReturn);
    if (!status.isOk()) {
        LOG(WARNING) << "Failed to return a buffer";
    }

    // Counts a returned buffer
    mUsageStats->framesReturned(buffersToReturn);

    return status;
}

// Methods from ::aidl::android::hardware::automotive::evs::IEvsCameraStream follow.
ScopedAStatus HalCamera::deliverFrame(const std::vector<BufferDesc>& buffers) {
    LOG(VERBOSE) << "Received a frame";

    // Reports the number of received buffers
    mUsageStats->framesReceived(buffers);

    // Frames are being forwarded to HIDL v1.1 and AIDL clients only who requested new frame.
    const auto timestamp = buffers[0].timestamp;
    // TODO(b/145750636): For now, we are using a approximately half of 1 seconds / 30 frames = 33ms
    //           but this must be derived from current framerate.
    constexpr int64_t kThreshold = 16'000;  // ms
    unsigned frameDeliveries = 0;
    std::deque<FrameRequest> currentRequests;
    std::deque<FrameRequest> puntedRequests;
    {
        std::lock_guard<std::mutex> lock(mFrameMutex);
        currentRequests.insert(currentRequests.end(),
                               std::make_move_iterator(mNextRequests.begin()),
                               std::make_move_iterator(mNextRequests.end()));
        mNextRequests.clear();
    }

    while (!currentRequests.empty()) {
        auto req = currentRequests.front();
        currentRequests.pop_front();
        std::shared_ptr<VirtualCamera> vCam = req.client.lock();
        if (!vCam) {
            // Ignore a client already dead.
            continue;
        }

        if (timestamp - req.timestamp < kThreshold) {
            // Skip current frame because it arrives too soon.
            LOG(DEBUG) << "Skips a frame from " << getId();
            mUsageStats->framesSkippedToSync();
            puntedRequests.push_back(req);
            continue;
        }

        if (!vCam->deliverFrame(buffers[0])) {
            LOG(WARNING) << getId() << " failed to forward the buffer to " << vCam.get();
        } else {
            LOG(DEBUG) << getId() << " forwarded the buffer #" << buffers[0].bufferId << " to "
                       << vCam.get() << " from " << this;
            ++frameDeliveries;
        }
    }

    if (frameDeliveries < 1) {
        // If none of our clients could accept the frame, then return it
        // right away.
        LOG(INFO) << "Trivially rejecting frame (" << buffers[0].bufferId << ") from " << getId()
                  << " with no acceptance";
        if (!mHwCamera->doneWithFrame(buffers).isOk()) {
            LOG(WARNING) << "Failed to return buffers";
        }

        // Reports a returned buffer
        mUsageStats->framesReturned(buffers);
    } else {
        // Add an entry for this frame in our tracking list.
        unsigned i;
        for (i = 0; i < mFrames.size(); ++i) {
            if (mFrames[i].refCount == 0) {
                break;
            }
        }

        if (i == mFrames.size()) {
            mFrames.push_back(buffers[0].bufferId);
        } else {
            mFrames[i].frameId = buffers[0].bufferId;
        }
        mFrames[i].refCount = frameDeliveries;
    }

    {
        // Adding skipped capture requests back to the queue.
        std::lock_guard<std::mutex> lock(mFrameMutex);
        mNextRequests.insert(mNextRequests.end(),
                             std::make_move_iterator(puntedRequests.begin()),
                             std::make_move_iterator(puntedRequests.end()));
    }

    return ScopedAStatus::ok();
}

ScopedAStatus HalCamera::notify(const EvsEventDesc& event) {
    LOG(DEBUG) << "Received an event id: " << static_cast<int32_t>(event.aType);
    if (event.aType == EvsEventType::STREAM_STOPPED) {
        // This event happens only when there is no more active client.
        std::lock_guard lock(mFrameMutex);
        if (mStreamState != STOPPING) {
            LOG(WARNING) << "Stream stopped unexpectedly";
        }

        mStreamState = STOPPED;
    }

    // Forward all other events to the clients
    for (auto&& client : mClients) {
        std::shared_ptr<VirtualCamera> virtCam = client.lock();
        if (virtCam) {
            if (!virtCam->notify(event)) {
                LOG(WARNING) << "Failed to forward an event";
            }
        }
    }

    return ScopedAStatus::ok();
}

ScopedAStatus HalCamera::setPrimaryClient(const std::shared_ptr<VirtualCamera>& virtualCamera) {
    if (mPrimaryClient.lock() == nullptr) {
        LOG(DEBUG) << __FUNCTION__ << ": " << virtualCamera.get() << " becomes a primary client.";
        mPrimaryClient = virtualCamera;
        return ScopedAStatus::ok();
    } else {
        LOG(INFO) << "This camera already has a primary client.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::PERMISSION_DENIED);
    }
}

ScopedAStatus HalCamera::forcePrimaryClient(const std::shared_ptr<VirtualCamera>& virtualCamera) {
    std::shared_ptr<VirtualCamera> prevPrimary = mPrimaryClient.lock();
    if (prevPrimary == virtualCamera) {
        LOG(DEBUG) << "Client " << virtualCamera.get() << " is already a primary client";
        return ScopedAStatus::ok();
    }

    mPrimaryClient = virtualCamera;
    if (prevPrimary) {
        LOG(INFO) << "High priority client " << virtualCamera.get()
                  << " steals a primary role from " << prevPrimary.get();

        /* Notify a previous primary client the loss of a primary role */
        EvsEventDesc event;
        event.aType = EvsEventType::MASTER_RELEASED;
        auto cbResult = prevPrimary->notify(event);
        if (!cbResult) {
            LOG(WARNING) << "Fail to deliver a primary role lost notification";
        }
    }

    return ScopedAStatus::ok();
}

ScopedAStatus HalCamera::unsetPrimaryClient(const VirtualCamera* virtualCamera) {
    if (mPrimaryClient.lock().get() != virtualCamera) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::INVALID_ARG);
    }

    LOG(INFO) << "Unset a primary camera client";
    mPrimaryClient.reset();

    /* Notify other clients that a primary role becomes available. */
    EvsEventDesc event;
    event.aType = EvsEventType::MASTER_RELEASED;
    if (!notify(event).isOk()) {
        LOG(WARNING) << "Fail to deliver a parameter change notification";
    }

    return ScopedAStatus::ok();
}

ScopedAStatus HalCamera::setParameter(const std::shared_ptr<VirtualCamera>& virtualCamera,
                                      CameraParam id, int32_t* value) {
    if (virtualCamera != mPrimaryClient.lock()) {
        LOG(WARNING) << "A parameter change request from the non-primary client is declined.";

        /* Read a current value of a requested camera parameter */
        getParameter(id, value);
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::PERMISSION_DENIED);
    }

    std::vector<int32_t> effectiveValues;
    auto result = mHwCamera->setIntParameter(id, *value, &effectiveValues);
    if (result.isOk()) {
        /* Notify a parameter change */
        EvsEventDesc event;
        event.aType = EvsEventType::PARAMETER_CHANGED;
        event.payload.push_back(static_cast<int32_t>(id));
        event.payload.push_back(effectiveValues[0]);
        if (!notify(event).isOk()) {
            LOG(WARNING) << "Fail to deliver a parameter change notification";
        }

        *value = effectiveValues[0];
    }

    return result;
}

ScopedAStatus HalCamera::getParameter(CameraParam id, int32_t* value) {
    std::vector<int32_t> effectiveValues;
    auto result = mHwCamera->getIntParameter(id, &effectiveValues);
    if (result.isOk()) {
        *value = effectiveValues[0];
    }

    return result;
}

CameraUsageStatsRecord HalCamera::getStats() const {
    return mUsageStats->snapshot();
}

Stream HalCamera::getStreamConfiguration() const {
    return mStreamConfig;
}

std::string HalCamera::toString(const char* indent) const {
    std::string buffer;

    const auto timeElapsedMs = ::android::uptimeMillis() - mTimeCreatedMs;
    StringAppendF(&buffer, "%sCreated: @%" PRId64 " (elapsed %" PRId64 " ms)\n", indent,
                  mTimeCreatedMs, timeElapsedMs);

    std::string double_indent(indent);
    double_indent += indent;
    buffer += CameraUsageStats::toString(getStats(), double_indent.data());
    for (auto&& client : mClients) {
        auto handle = client.lock();
        if (!handle) {
            continue;
        }

        StringAppendF(&buffer, "%sClient %p\n", indent, handle.get());
        buffer += handle->toString(double_indent.data());
    }

    StringAppendF(&buffer, "%sPrimary client: %p\n", indent, mPrimaryClient.lock().get());

    buffer += HalCamera::toString(mStreamConfig, indent);

    return buffer;
}

std::string HalCamera::toString(Stream configuration, const char* indent) {
    std::string streamInfo;
    std::string double_indent(indent);
    double_indent += indent;
    StringAppendF(&streamInfo,
                  "%sActive Stream Configuration\n"
                  "%sid: %d\n"
                  "%swidth: %d\n"
                  "%sheight: %d\n"
                  "%sformat: 0x%X\n"
                  "%susage: 0x%" PRIx64 "\n"
                  "%srotation: 0x%X\n\n",
                  indent, double_indent.data(), configuration.id, double_indent.data(),
                  configuration.width, double_indent.data(), configuration.height,
                  double_indent.data(), configuration.format, double_indent.data(),
                  configuration.usage, double_indent.data(), configuration.rotation);

    return streamInfo;
}

}  // namespace aidl::android::automotive::evs::implementation
