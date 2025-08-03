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

#include "VirtualCamera.h"

#include "Enumerator.h"
#include "HalCamera.h"
#include "utils/include/Utils.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <android/hardware_buffer.h>

#include <assert.h>

#include <chrono>

namespace aidl::android::automotive::evs::implementation {

using ::aidl::android::hardware::automotive::evs::BufferDesc;
using ::aidl::android::hardware::automotive::evs::CameraDesc;
using ::aidl::android::hardware::automotive::evs::CameraParam;
using ::aidl::android::hardware::automotive::evs::DisplayState;
using ::aidl::android::hardware::automotive::evs::EvsEventDesc;
using ::aidl::android::hardware::automotive::evs::EvsEventType;
using ::aidl::android::hardware::automotive::evs::EvsResult;
using ::aidl::android::hardware::automotive::evs::IEvsCameraStream;
using ::aidl::android::hardware::automotive::evs::IEvsDisplay;
using ::aidl::android::hardware::automotive::evs::ParameterRange;
using ::aidl::android::hardware::common::NativeHandle;
using ::aidl::android::hardware::graphics::common::HardwareBuffer;
using ::android::base::StringAppendF;
using ::ndk::ScopedAStatus;
using ::std::chrono_literals::operator""s;

VirtualCamera::VirtualCamera(const std::vector<std::shared_ptr<HalCamera>>& halCameras) :
      mStreamState(STOPPED) {
    for (auto&& cam : halCameras) {
        mHalCamera.insert_or_assign(cam->getId(), cam);
    }
}

VirtualCamera::~VirtualCamera() {
    shutdown();
}

ScopedAStatus VirtualCamera::doneWithFrame(const std::vector<BufferDesc>& buffers) {
    std::lock_guard lock(mMutex);

    for (auto&& buffer : buffers) {
        // Find this buffer in our "held" list
        auto it = std::find_if(mFramesHeld[buffer.deviceId].begin(),
                               mFramesHeld[buffer.deviceId].end(),
                               [id = buffer.bufferId](const BufferDesc& buffer) {
                                   return id == buffer.bufferId;
                               });
        if (it == mFramesHeld[buffer.deviceId].end()) {
            // We should always find the frame in our "held" list
            LOG(WARNING) << "Ignoring doneWithFrame called with unrecognized frame id "
                         << buffer.bufferId;
            continue;
        }

        // Take this frame out of our "held" list
        BufferDesc bufferToReturn = std::move(*it);
        mFramesHeld[buffer.deviceId].erase(it);

        // Tell our parent that we're done with this buffer
        std::shared_ptr<HalCamera> pHwCamera = mHalCamera[buffer.deviceId].lock();
        if (pHwCamera) {
            auto status = pHwCamera->doneWithFrame(std::move(bufferToReturn));
            if (!status.isOk()) {
                LOG(WARNING) << "Failed to return a buffer " << buffer.bufferId;
            }
        } else {
            LOG(WARNING) << "Possible memory leak; " << buffer.deviceId << " is not valid.";
        }
    }

    return ScopedAStatus::ok();
}

ScopedAStatus VirtualCamera::forcePrimaryClient(const std::shared_ptr<IEvsDisplay>& display) {
    if (!isValid()) {
        LOG(ERROR) << "No hardware camera is available.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    } else if (isLogicalCamera()) {
        LOG(WARNING) << "Logical camera device does not support " << __FUNCTION__;
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_SUPPORTED);
    }

    if (display == nullptr) {
        LOG(ERROR) << __FUNCTION__ << ": Passed display is invalid";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    DisplayState state = DisplayState::DEAD;
    auto status = display->getDisplayState(&state);
    if (!status.isOk()) {
        LOG(ERROR) << "Failed to read current display state";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::UNDERLYING_SERVICE_ERROR);
    }

    auto displayStateRange = ::ndk::enum_range<DisplayState>();
    if (state == DisplayState::NOT_OPEN || state == DisplayState::DEAD ||
        std::find(displayStateRange.begin(), displayStateRange.end(), state) ==
                displayStateRange.end()) {
        LOG(ERROR) << __FUNCTION__ << ": Passed display is in invalid state";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::INVALID_ARG);
    }

    // mHalCamera is guaranteed to have at least one element.
    auto pHwCamera = mHalCamera.begin()->second.lock();
    if (pHwCamera == nullptr) {
        LOG(ERROR) << "Camera device " << mHalCamera.begin()->first << " is not alive.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    return pHwCamera->forcePrimaryClient(ref<VirtualCamera>());
}

ScopedAStatus VirtualCamera::getCameraInfo(CameraDesc* _aidl_return) {
    if (!isValid()) {
        LOG(ERROR) << "No hardware camera is available.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    } else if (isLogicalCamera()) {
        // Logical camera description is stored in VirtualCamera object.
        *_aidl_return = *mDesc;
        return ScopedAStatus::ok();
    }

    // Straight pass through to hardware layer
    auto pHwCamera = mHalCamera.begin()->second.lock();
    if (pHwCamera == nullptr) {
        // Return an empty list
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    return pHwCamera->getHwCamera()->getCameraInfo(_aidl_return);
}

ScopedAStatus VirtualCamera::getExtendedInfo(int32_t opaqueIdentifier,
                                             std::vector<uint8_t>* value) {
    if (!isValid()) {
        LOG(ERROR) << "No hardware camera is available.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    } else if (isLogicalCamera()) {
        LOG(WARNING) << "Logical camera device does not support " << __FUNCTION__;
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_SUPPORTED);
    }

    auto pHwCamera = mHalCamera.begin()->second.lock();
    if (pHwCamera == nullptr) {
        LOG(ERROR) << "Camera device " << mHalCamera.begin()->first << " is not alive.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    return pHwCamera->getHwCamera()->getExtendedInfo(opaqueIdentifier, value);
}

ScopedAStatus VirtualCamera::getIntParameter(CameraParam id, std::vector<int32_t>* value) {
    if (!isValid()) {
        LOG(ERROR) << "No hardware camera is available.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    } else if (isLogicalCamera()) {
        LOG(WARNING) << "Logical camera device does not support " << __FUNCTION__;
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_SUPPORTED);
    }

    auto pHwCamera = mHalCamera.begin()->second.lock();
    if (pHwCamera == nullptr) {
        LOG(ERROR) << "Camera device " << mHalCamera.begin()->first << " is not alive.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    return pHwCamera->getHwCamera()->getIntParameter(id, value);
}

ScopedAStatus VirtualCamera::getIntParameterRange(CameraParam id, ParameterRange* _aidl_return) {
    if (!isValid()) {
        LOG(ERROR) << "No hardware camera is available.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    } else if (isLogicalCamera()) {
        LOG(WARNING) << "Logical camera device does not support " << __FUNCTION__;
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_SUPPORTED);
    }

    // Straight pass through to hardware layer
    auto pHwCamera = mHalCamera.begin()->second.lock();
    if (pHwCamera == nullptr) {
        LOG(ERROR) << "Camera device " << mHalCamera.begin()->first << " is not alive.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    return pHwCamera->getHwCamera()->getIntParameterRange(id, _aidl_return);
}

ScopedAStatus VirtualCamera::getParameterList(std::vector<CameraParam>* _aidl_return) {
    if (!isValid()) {
        LOG(ERROR) << "No hardware camera is available.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    } else if (isLogicalCamera()) {
        LOG(WARNING) << "Logical camera device does not support " << __FUNCTION__;
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_SUPPORTED);
    }

    // Straight pass through to hardware layer
    auto pHwCamera = mHalCamera.begin()->second.lock();
    if (pHwCamera == nullptr) {
        LOG(ERROR) << "Camera device " << mHalCamera.begin()->first << " is not alive.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    return pHwCamera->getHwCamera()->getParameterList(_aidl_return);
}

ScopedAStatus VirtualCamera::getPhysicalCameraInfo(const std::string& deviceId,
                                                   CameraDesc* _aidl_return) {
    auto device = mHalCamera.find(deviceId);
    if (device == mHalCamera.end()) {
        LOG(ERROR) << " Requested device " << deviceId << " does not back this device.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::INVALID_ARG);
    }

    // Straight pass through to hardware layer
    auto pHwCamera = device->second.lock();
    if (pHwCamera == nullptr) {
        LOG(ERROR) << "Camera device " << mHalCamera.begin()->first << " is not alive.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    return pHwCamera->getHwCamera()->getCameraInfo(_aidl_return);
}

ScopedAStatus VirtualCamera::importExternalBuffers(const std::vector<BufferDesc>& buffers,
                                                   int32_t* _aidl_return) {
    if (!isValid()) {
        LOG(ERROR) << "No hardware camera is available.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    } else if (isLogicalCamera()) {
        LOG(WARNING) << "Logical camera device does not support " << __FUNCTION__;
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_SUPPORTED);
    }

    auto pHwCamera = mHalCamera.begin()->second.lock();
    if (pHwCamera == nullptr) {
        LOG(ERROR) << "Camera device " << mHalCamera.begin()->first << " is not alive.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    int delta = 0;
    if (!pHwCamera->changeFramesInFlight(buffers, &delta)) {
        LOG(ERROR) << "Failed to add extenral capture buffers.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::UNDERLYING_SERVICE_ERROR);
    }

    mFramesAllowed += delta;
    *_aidl_return = delta;
    return ScopedAStatus::ok();
}

ScopedAStatus VirtualCamera::pauseVideoStream() {
    if (!isValid()) {
        LOG(ERROR) << "No hardware camera is available.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    auto pHwCamera = mHalCamera.begin()->second.lock();
    if (pHwCamera == nullptr) {
        LOG(ERROR) << "Camera device " << mHalCamera.begin()->first << " is not alive.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    return pHwCamera->getHwCamera()->pauseVideoStream();
}

ScopedAStatus VirtualCamera::resumeVideoStream() {
    if (!isValid()) {
        LOG(ERROR) << "No hardware camera is available.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    auto pHwCamera = mHalCamera.begin()->second.lock();
    if (pHwCamera == nullptr) {
        LOG(ERROR) << "Camera device " << mHalCamera.begin()->first << " is not alive.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    return pHwCamera->getHwCamera()->resumeVideoStream();
}

ScopedAStatus VirtualCamera::setExtendedInfo(int32_t opaqueIdentifier,
                                             const std::vector<uint8_t>& opaqueValue) {
    if (!isValid()) {
        LOG(ERROR) << "No hardware camera is available.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    } else if (isLogicalCamera()) {
        LOG(WARNING) << "Logical camera device does not support " << __FUNCTION__;
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_SUPPORTED);
    }

    auto pHwCamera = mHalCamera.begin()->second.lock();
    if (pHwCamera == nullptr) {
        LOG(ERROR) << "Camera device " << mHalCamera.begin()->first << " is not alive.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    return pHwCamera->getHwCamera()->setExtendedInfo(opaqueIdentifier, opaqueValue);
}

ScopedAStatus VirtualCamera::setIntParameter(CameraParam id, int32_t value,
                                             std::vector<int32_t>* effectiveValue) {
    if (!isValid()) {
        LOG(ERROR) << "No hardware camera is available.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    } else if (isLogicalCamera()) {
        LOG(WARNING) << "Logical camera device does not support " << __FUNCTION__;
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_SUPPORTED);
    }

    auto pHwCamera = mHalCamera.begin()->second.lock();
    if (pHwCamera == nullptr) {
        LOG(ERROR) << "Camera device " << mHalCamera.begin()->first << " is not alive.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    auto status = pHwCamera->setParameter(ref<VirtualCamera>(), id, &value);
    if (status.isOk()) {
        effectiveValue->push_back(value);
    }
    return status;
}

ScopedAStatus VirtualCamera::setPrimaryClient() {
    if (!isValid()) {
        LOG(ERROR) << "No hardware camera is available.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    } else if (isLogicalCamera()) {
        LOG(WARNING) << "Logical camera device does not support " << __FUNCTION__;
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_SUPPORTED);
    }

    auto pHwCamera = mHalCamera.begin()->second.lock();
    if (pHwCamera == nullptr) {
        LOG(ERROR) << "Camera device " << mHalCamera.begin()->first << " is not alive.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    return pHwCamera->setPrimaryClient(ref<VirtualCamera>());
}

ScopedAStatus VirtualCamera::setMaxFramesInFlight(int32_t bufferCount) {
    if (bufferCount < 1) {
        LOG(ERROR) << "Given bufferCount = " << bufferCount
                   << " is invalid; it must be greater than zero.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::INVALID_ARG);
    }

    // How many buffers are we trying to add (or remove if negative)
    int bufferCountChange = bufferCount - mFramesAllowed;

    // Ask our parent for more buffers
    bool result = true;
    std::vector<std::shared_ptr<HalCamera>> changedCameras;
    for (auto&& [key, hwCamera] : mHalCamera) {
        auto pHwCamera = hwCamera.lock();
        if (!pHwCamera) {
            continue;
        }

        result = pHwCamera->changeFramesInFlight(bufferCountChange);
        if (!result) {
            LOG(ERROR) << key << ": Failed to change buffer count by " << bufferCountChange
                       << " to " << bufferCount;
            break;
        }

        changedCameras.push_back(std::move(pHwCamera));
    }

    // Update our notion of how many frames we're allowed
    mFramesAllowed = bufferCount;

    if (!result) {
        // Rollback changes because we failed to update all cameras
        for (auto&& hwCamera : changedCameras) {
            LOG(WARNING) << "Rollback a change on  " << hwCamera->getId();
            hwCamera->changeFramesInFlight(-bufferCountChange);
        }

        // Restore the original buffer count
        mFramesAllowed -= bufferCountChange;
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::BUFFER_NOT_AVAILABLE);
    }

    return ScopedAStatus::ok();
}

ScopedAStatus VirtualCamera::startVideoStream(const std::shared_ptr<IEvsCameraStream>& receiver) {
    std::lock_guard lock(mMutex);

    if (!receiver) {
        LOG(ERROR) << "Given IEvsCameraStream object is invalid.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::INVALID_ARG);
    }

    // We only support a single stream at a time
    if (mStreamState != STOPPED) {
        LOG(ERROR) << "Ignoring startVideoStream call when a stream is already running.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::STREAM_ALREADY_RUNNING);
    }

    // Validate our held frame count is starting out at zero as we expect
    assert(mFramesHeld.empty());

    // Record the user's callback for use when we have a frame ready
    mStream = receiver;
    mStreamState = RUNNING;

    // Tell the underlying camera hardware that we want to stream
    for (auto iter = mHalCamera.begin(); iter != mHalCamera.end(); ++iter) {
        std::shared_ptr<HalCamera> pHwCamera = iter->second.lock();
        if (!pHwCamera) {
            LOG(ERROR) << "Failed to start a video stream on " << iter->first;
            continue;
        }

        LOG(INFO) << __FUNCTION__ << " starts a video stream on " << iter->first;
        if (!pHwCamera->clientStreamStarting().isOk()) {
            // If we failed to start the underlying stream, then we're not actually running
            mStream = nullptr;
            mStreamState = STOPPED;

            // Request to stop streams started by this client.
            auto rb = mHalCamera.begin();
            while (rb != iter) {
                auto ptr = rb->second.lock();
                if (ptr) {
                    ptr->clientStreamEnding(this);
                }
                ++rb;
            }

            return Utils::buildScopedAStatusFromEvsResult(EvsResult::UNDERLYING_SERVICE_ERROR);
        }
    }

    mCaptureThread = std::thread([this]() {
        // TODO(b/145466570): With a proper camera hang handler, we may want
        // to reduce an amount of timeout.
        constexpr auto kFrameTimeout = 5s;  // timeout in seconds.
        int64_t lastFrameTimestamp = -1;
        EvsResult status = EvsResult::OK;
        while (true) {
            std::unique_lock lock(mMutex);
            ::android::base::ScopedLockAssertion assume_lock(mMutex);

            if (mStreamState != RUNNING) {
                // A video stream is stopped while a capture thread is acquiring
                // a lock.
                LOG(DEBUG) << "Requested to stop capturing frames";
                break;
            }

            unsigned count = 0;
            for (auto&& [key, hwCamera] : mHalCamera) {
                std::shared_ptr<HalCamera> pHwCamera = hwCamera.lock();
                if (!pHwCamera) {
                    LOG(WARNING) << "Invalid camera " << key << " is ignored.";
                    continue;
                }

                pHwCamera->requestNewFrame(ref<VirtualCamera>(), lastFrameTimestamp);
                mSourceCameras.insert(pHwCamera->getId());
                ++count;
            }

            if (count < 1) {
                LOG(ERROR) << "No camera is available.";
                status = EvsResult::RESOURCE_NOT_AVAILABLE;
                break;
            }

            if (!mFramesReadySignal.wait_for(lock, kFrameTimeout, [this]() REQUIRES(mMutex) {
                    // Stops waiting if
                    // 1) we've requested to stop capturing
                    //    new frames
                    // 2) or, we've got all frames
                    return mStreamState != RUNNING || mSourceCameras.empty();
                })) {
                // This happens when either a new frame does not arrive
                // before a timer expires or we're requested to stop
                // capturing frames.
                LOG(DEBUG) << "Timer for a new frame expires";
                status = EvsResult::UNDERLYING_SERVICE_ERROR;
                break;
            }

            if (mStreamState != RUNNING || !mStream) {
                // A video stream is stopped while a capture thread is waiting
                // for a new frame or we have lost a client.
                LOG(DEBUG) << "Requested to stop capturing frames or lost a client";
                break;
            }

            // Fetch frames and forward to the client
            if (mFramesHeld.empty()) {
                // We do not have any frame to forward.
                continue;
            }

            // Pass this buffer through to our client
            std::vector<BufferDesc> frames;
            frames.resize(count);
            unsigned i = 0;
            for (auto&& [key, hwCamera] : mHalCamera) {
                std::shared_ptr<HalCamera> pHwCamera = hwCamera.lock();
                if (!pHwCamera || mFramesHeld[key].empty()) {
                    continue;
                }

                // Duplicate the latest buffer and forward it to the
                // active clients
                auto frame = Utils::dupBufferDesc(mFramesHeld[key].back(),
                                                  /* doDup= */ true);
                if (frame.timestamp > lastFrameTimestamp) {
                    lastFrameTimestamp = frame.timestamp;
                }
                frames[i++] = std::move(frame);
            }

            if (!mStream->deliverFrame(frames).isOk()) {
                LOG(WARNING) << "Failed to forward frames";
            }
        }

        LOG(DEBUG) << "Exiting a capture thread";
        if (status != EvsResult::OK && mStream) {
            EvsEventDesc event{
                    .aType = status == EvsResult::RESOURCE_NOT_AVAILABLE
                            ? EvsEventType::STREAM_ERROR
                            : EvsEventType::TIMEOUT,
                    .payload = {static_cast<int32_t>(status)},
            };
            if (!mStream->notify(std::move(event)).isOk()) {
                LOG(WARNING) << "Error delivering a stream event"
                             << static_cast<int32_t>(event.aType);
            }
        }
    });

    // TODO(b/213108625):
    // Detect and exit if we encounter a stalled stream or unresponsive driver?
    // Consider using a timer and watching for frame arrival?

    return ScopedAStatus::ok();
}

ScopedAStatus VirtualCamera::stopVideoStream() {
    {
        std::lock_guard lock(mMutex);
        if (mStreamState != RUNNING) {
            // No action is required.
            return ScopedAStatus::ok();
        }

        // Tell the frame delivery pipeline we don't want any more frames
        mStreamState = STOPPING;

        // Awake the capture thread; this thread will terminate.
        mFramesReadySignal.notify_all();

        // Deliver the stream-ending notification
        EvsEventDesc event{
                .aType = EvsEventType::STREAM_STOPPED,
        };
        if (mStream && !mStream->notify(event).isOk()) {
            LOG(WARNING) << "Error delivering end of stream event";
        }

        // Since we are single threaded, no frame can be delivered while this function is running,
        // so we can go directly to the STOPPED state here on the server.
        // Note, however, that there still might be frames already queued that client will see
        // after returning from the client side of this call.
        mStreamState = STOPPED;
    }

    // Give the underlying hardware camera the heads up that it might be time to stop
    for (auto&& [_, hwCamera] : mHalCamera) {
        auto pHwCamera = hwCamera.lock();
        if (pHwCamera) {
            pHwCamera->clientStreamEnding(this);
        }
    }

    // Signal a condition to unblock a capture thread and then join
    mSourceCameras.clear();
    mFramesReadySignal.notify_all();

    if (mCaptureThread.joinable()) {
        mCaptureThread.join();
    }

    return ScopedAStatus::ok();
}

ScopedAStatus VirtualCamera::unsetPrimaryClient() {
    if (!isValid()) {
        // Safely ignores a request if no hardware camera is active.
        return ScopedAStatus::ok();
    }

    if (isLogicalCamera()) {
        LOG(WARNING) << "Logical camera device does not support " << __FUNCTION__;
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_SUPPORTED);
    }

    auto pHwCamera = mHalCamera.begin()->second.lock();
    if (!pHwCamera) {
        LOG(ERROR) << "Camera device " << mHalCamera.begin()->first << " is not alive.";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
    }

    return pHwCamera->unsetPrimaryClient(this);
}

void VirtualCamera::shutdown() {
    {
        std::lock_guard lock(mMutex);

        // In normal operation, the stream should already be stopped by the time we get here
        if (mStreamState != RUNNING) {
            return;
        }

        // Note that if we hit this case, no terminating frame will be sent to the client,
        // but they're probably already dead anyway.
        LOG(WARNING) << "Virtual camera being shutdown while stream is running";

        // Tell the frame delivery pipeline we don't want any more frames
        mStreamState = STOPPING;

        // Returns buffers held by this client
        for (auto&& [key, hwCamera] : mHalCamera) {
            auto pHwCamera = hwCamera.lock();
            if (!pHwCamera) {
                LOG(WARNING) << "Camera device " << key << " is not alive.";
                continue;
            }

            if (!mFramesHeld[key].empty()) {
                LOG(WARNING) << "VirtualCamera destructing with frames in flight.";

                // Return to the underlying hardware camera any buffers the client was holding
                while (!mFramesHeld[key].empty()) {
                    auto it = mFramesHeld[key].begin();
                    pHwCamera->doneWithFrame(std::move(*it));
                    mFramesHeld[key].erase(it);
                }
            }

            // Retire from a primary client
            pHwCamera->unsetPrimaryClient(this);

            // Give the underlying hardware camera the heads up that it might be time to stop
            pHwCamera->clientStreamEnding(this);

            // Retire from the participating HW camera's client list
            pHwCamera->disownVirtualCamera(this);
        }

        // Awakes the capture thread; this thread will terminate.
        mFramesReadySignal.notify_all();
    }

    // Join a capture thread
    if (mCaptureThread.joinable()) {
        mCaptureThread.join();
    }

    mFramesHeld.clear();

    // Drop our reference to our associated hardware camera
    mHalCamera.clear();
}

std::vector<std::shared_ptr<HalCamera>> VirtualCamera::getHalCameras() {
    std::vector<std::shared_ptr<HalCamera>> cameras;
    for (auto&& [key, cam] : mHalCamera) {
        auto ptr = cam.lock();
        if (ptr) {
            cameras.push_back(std::move(ptr));
        }
    }

    return cameras;
}

bool VirtualCamera::deliverFrame(const BufferDesc& bufDesc) {
    std::lock_guard lock(mMutex);

    if (mStreamState == STOPPED) {
        // A stopped stream gets no frames
        LOG(ERROR) << "A stopped stream should not get any frames";
        return false;
    }

    if (mFramesHeld[bufDesc.deviceId].size() >= mFramesAllowed) {
        // Indicate that we declined to send the frame to the client because they're at quota
        LOG(INFO) << "Skipping new frame as we hold " << mFramesHeld[bufDesc.deviceId].size()
                  << " of " << mFramesAllowed;

        if (mStream) {
            // Report a frame drop to the client.
            EvsEventDesc event;
            event.deviceId = bufDesc.deviceId;
            event.aType = EvsEventType::FRAME_DROPPED;
            if (!mStream->notify(event).isOk()) {
                LOG(WARNING) << "Error delivering end of stream event";
            }
        }

        // Marks that a new frame has arrived though it was not accepted
        mSourceCameras.erase(bufDesc.deviceId);
        mFramesReadySignal.notify_all();

        return false;
    }

    // Keep a record of this frame so we can clean up if we have to in case of client death
    mFramesHeld[bufDesc.deviceId].push_back(
            std::move(Utils::dupBufferDesc(bufDesc, /* doDup= */ true)));

    // v1.0 client uses an old frame-delivery mechanism.
    if (mCaptureThread.joinable()) {
        // Keep forwarding frames as long as a capture thread is alive
        // Notify a new frame receipt
        mSourceCameras.erase(bufDesc.deviceId);
        mFramesReadySignal.notify_all();
    }

    return true;
}

bool VirtualCamera::notify(const EvsEventDesc& event) {
    switch (event.aType) {
        case EvsEventType::STREAM_STOPPED: {
            {
                std::lock_guard lock(mMutex);
                if (mStreamState != RUNNING) {
                    // We're not actively consuming a video stream or already in
                    // a process to stop a video stream.
                    return true;
                }

                // Warn if we got an unexpected stream termination
                LOG(WARNING) << "Stream unexpectedly stopped, current status " << mStreamState;
            }

            // Clean up the resource and forward an event to the client
            stopVideoStream();
            return true;
        }

        // v1.0 client will ignore all other events.
        case EvsEventType::PARAMETER_CHANGED:
            LOG(DEBUG) << "A camera parameter " << event.payload[0] << " is set to "
                       << event.payload[1];
            break;

        case EvsEventType::MASTER_RELEASED:
            LOG(DEBUG) << "The primary client has been released";
            break;

        default:
            LOG(WARNING) << "Unknown event id " << static_cast<int32_t>(event.aType);
            break;
    }

    // Forward a received event to the v1.1 client
    if (mStream && !mStream->notify(event).isOk()) {
        LOG(ERROR) << "Failed to forward an event";
        return false;
    }

    return true;
}

std::string VirtualCamera::toString(const char* indent) const {
    std::string buffer;
    StringAppendF(&buffer,
                  "%sLogical camera device: %s\n"
                  "%sFramesAllowed: %u\n"
                  "%sFrames in use:\n",
                  indent, mHalCamera.size() > 1 ? "T" : "F", indent, mFramesAllowed, indent);

    std::string next_indent(indent);
    next_indent += "\t";
    for (auto&& [id, queue] : mFramesHeld) {
        StringAppendF(&buffer, "%s%s: %d\n", next_indent.data(), id.data(),
                      static_cast<int>(queue.size()));
    }
    StringAppendF(&buffer, "%sCurrent stream state: %d\n", indent, mStreamState);

    return buffer;
}

}  // namespace aidl::android::automotive::evs::implementation
