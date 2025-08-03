/*
 * Copyright 2022 The Android Open Source Project
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

#include "MockHidlEvsHal_1_0.h"

#include <android-base/logging.h>
#include <hardware/gralloc.h>
#include <ui/DisplayMode.h>
#include <utils/SystemClock.h>

#include <functional>
#include <future>

#include <system/graphics-base.h>

namespace {

using ::android::hardware::automotive::evs::V1_0::BufferDesc;
using ::android::hardware::automotive::evs::V1_0::CameraDesc;
using ::android::hardware::automotive::evs::V1_0::DisplayDesc;
using ::android::hardware::automotive::evs::V1_0::DisplayState;
using ::android::hardware::automotive::evs::V1_0::EvsResult;
using ::android::hardware::automotive::evs::V1_0::IEvsCamera;
using ::android::hardware::automotive::evs::V1_0::IEvsCameraStream;
using ::android::hardware::automotive::evs::V1_0::IEvsDisplay;
using ::android::hardware::automotive::evs::V1_0::IEvsEnumerator;
using ::std::chrono_literals::operator""ms;
using ::std::chrono_literals::operator""s;

inline constexpr char kMockCameraDeviceNamePrefix[] = "/dev/mockcamera";
inline constexpr size_t kMinimumNumBuffers = 2;
inline constexpr size_t kMaximumNumBuffers = 10;

}  // namespace

namespace aidl::android::automotive::evs::implementation {

MockHidlEvsHal_1_0::~MockHidlEvsHal_1_0() {
    std::lock_guard lock(mLock);
    for (auto& [id, state] : mStreamState) {
        auto it = mCameraFrameThread.find(id);
        if (it == mCameraFrameThread.end() || !it->second.joinable()) {
            continue;
        }

        state = StreamState::kStopping;
        it->second.join();
    }

    deinitializeBufferPoolLocked();
    mCameraClient.clear();
}

::android::sp<IEvsEnumerator> MockHidlEvsHal_1_0::getEnumerator() {
    if (!mMockHidlEvsEnumerator) {
        LOG(ERROR) << "MockHidlEvsHal_1_0 has not initialized yet.";
        return nullptr;
    }

    return mMockHidlEvsEnumerator;
}

void MockHidlEvsHal_1_0::initialize() {
    initializeBufferPool(kMaximumNumBuffers);
    configureCameras(mNumCameras);
    configureDisplays(mNumDisplays);
    configureEnumerator();
}

void MockHidlEvsHal_1_0::forwardFrames(size_t numberOfFramesToForward,
                                       const std::string& deviceId) {
    std::unique_lock l(mLock);
    ::android::base::ScopedLockAssertion lock_assertion(mLock);
    auto it = mStreamState.find(deviceId);
    if (it != mStreamState.end() && it->second != StreamState::kStopped) {
        LOG(WARNING) << "A mock video stream is already active.";
        return;
    }
    mStreamState.insert_or_assign(deviceId, StreamState::kRunning);

    for (size_t count = 0;
         mStreamState[deviceId] == StreamState::kRunning && count < numberOfFramesToForward;
         ++count) {
        if (mBufferPool.empty()) {
            if (!mBufferAvailableSignal.wait_for(l, /* rel_time= */ 10s, [this]() REQUIRES(mLock) {
                    // Waiting for a buffer to use.
                    return !mBufferPool.empty();
                })) {
                LOG(ERROR) << "Buffer timeout; " << count << "/" << numberOfFramesToForward
                           << " are sent.";
                break;
            }
        }

        auto it = mCameraClient.find(deviceId);
        if (it == mCameraClient.end() || it->second == nullptr) {
            LOG(ERROR) << "Failed to forward a frame as no active recipient exists; " << count
                       << "/" << numberOfFramesToForward << " are sent.";
            break;
        }

        BufferDesc bufferToForward = mBufferPool.back();
        mBufferPool.pop_back();

        // Mark a buffer in-use.
        mBuffersInUse.push_back(bufferToForward);
        l.unlock();

        // Forward a duplicated buffer.  This must be done without a lock
        // because a shared data will be modified in doneWithFrame().
        it->second->deliverFrame(bufferToForward);

        LOG(DEBUG) << deviceId << ": " << (count + 1) << "/" << numberOfFramesToForward
                   << " frames are sent";
        std::this_thread::sleep_for(33ms);  // 30 frames per seconds
        l.lock();
    }

    if (mStreamState.find(deviceId) != mStreamState.end()) {
        mStreamState[deviceId] = StreamState::kStopped;
    }
}

size_t MockHidlEvsHal_1_0::initializeBufferPool(size_t requested) {
    std::lock_guard lock(mLock);
    for (auto count = 0; count < requested; ++count) {
        AHardwareBuffer_Desc desc = {
                .width = 64,
                .height = 32,
                .layers = 1,
                .usage = AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN,
                .format = HAL_PIXEL_FORMAT_RGBA_8888,
        };
        AHardwareBuffer* ahwb;
        if (AHardwareBuffer_allocate(&desc, &ahwb) != ::android::NO_ERROR) {
            LOG(ERROR) << "Failed to allocate AHardwareBuffer";
            return count;
        }

        BufferDesc aBuffer = {
                .width = desc.width,
                .height = desc.height,
                .stride = 256,  // 4 bytes per pixel.
                .pixelSize = 4,
                .format = desc.format,
                .usage = static_cast<uint32_t>(desc.usage),
                .bufferId = static_cast<uint32_t>(count),
                .memHandle = AHardwareBuffer_getNativeHandle(ahwb),
        };
        mBufferRecord.insert_or_assign(count, ahwb);
        mBufferPool.push_back(std::move(aBuffer));
    }

    return mBufferPool.size();
}

void MockHidlEvsHal_1_0::deinitializeBufferPoolLocked() {
    for (auto&& descriptor : mBuffersInUse) {
        auto it = mBufferRecord.find(descriptor.bufferId);
        if (it == mBufferRecord.end()) {
            LOG(WARNING) << "Ignoring unknown buffer id, " << descriptor.bufferId;
        } else {
            LOG(WARNING) << "Releasing buffer in use, id = " << descriptor.bufferId;
            AHardwareBuffer_release(it->second);
            mBufferRecord.erase(it);
        }
    }

    for (auto&& descriptor : mBufferPool) {
        auto it = mBufferRecord.find(descriptor.bufferId);
        if (it == mBufferRecord.end()) {
            LOG(WARNING) << "Ignoring unknown buffer id, " << descriptor.bufferId;
        } else {
            AHardwareBuffer_release(it->second);
            mBufferRecord.erase(it);
        }
    }

    mBuffersInUse.clear();
    mBufferPool.clear();
}

void MockHidlEvsHal_1_0::configureCameras(size_t n) {
    // Initializes a list of the camera parameters each mock camera
    // supports with their default values.
    for (auto i = 0; i < n; ++i) {
        (void)addMockCameraDevice(kMockCameraDeviceNamePrefix + std::to_string(i));
    }
}

bool MockHidlEvsHal_1_0::addMockCameraDevice(const std::string& deviceId) {
    ::android::sp<NiceMockHidlEvsCamera> mockCamera =
            new (std::nothrow) NiceMockHidlEvsCamera(deviceId);

    // For the testing purpose, this method will return
    // EvsResult::INVALID_ARG if the client returns any buffer with
    // unknown identifier.
    ON_CALL(*mockCamera, doneWithFrame)
            .WillByDefault([this](const hidlevs::V1_0::BufferDesc& buffer) {
                std::lock_guard lock(mLock);
                auto it = std::find_if(mBuffersInUse.begin(), mBuffersInUse.end(),
                                       [id = buffer.bufferId](const BufferDesc& desc) {
                                           return id == desc.bufferId;
                                       });
                if (it == mBuffersInUse.end()) {
                    return ::android::hardware::Void();
                }

                mBufferPool.push_back(std::move(*it));
                mBuffersInUse.erase(it);

                return ::android::hardware::Void();
            });

    // We return a mock camera descriptor with the metadata but empty vendor
    // flag.
    ON_CALL(*mockCamera, getCameraInfo)
            .WillByDefault([deviceId](MockHidlEvsCamera::getCameraInfo_cb callback) {
                hidlevs::V1_0::CameraDesc mockDesc = {
                        .cameraId = deviceId,
                        .vendorFlags = 0x0,
                };

                callback(mockDesc);
                return ::android::hardware::Void();
            });

    // This method will return a value associated with a given
    // identifier if exists.
    ON_CALL(*mockCamera, getExtendedInfo).WillByDefault([this](uint32_t id) {
        auto it = mCameraExtendedInfo.find(id);
        if (it == mCameraExtendedInfo.end()) {
            // A requested information does not exist.
            return 0;
        }

        if (it->second.size() < 4) {
            // Stored information is in an invalid size.
            return 0;
        }

        int value = *(reinterpret_cast<int32_t*>(it->second.data()));
        return value;
    });

    // This method stores a given vector with id.
    ON_CALL(*mockCamera, setExtendedInfo).WillByDefault([this](uint32_t id, int32_t v) {
        ::android::hardware::hidl_vec<uint8_t> value;
        value.resize(sizeof(v));
        *(reinterpret_cast<int32_t*>(value.data())) = v;
        mCameraExtendedInfo.insert_or_assign(id, value);
        return EvsResult::OK;
    });

    // Because EVS HAL does allow multiple camera clients exist, we simply
    // set the size of the buffer pool.
    ON_CALL(*mockCamera, setMaxFramesInFlight)
            .WillByDefault([this, id = mockCamera->getId()](uint32_t bufferCount) {
                std::lock_guard l(mLock);
                size_t totalSize = mBufferPoolSize + bufferCount;
                if (totalSize < kMinimumNumBuffers) {
                    LOG(WARNING) << "Requested buffer pool size is too small to run a camera; "
                                    "adjusting the pool size to "
                                 << kMinimumNumBuffers;
                    totalSize = kMinimumNumBuffers;
                } else if (totalSize > kMaximumNumBuffers) {
                    LOG(ERROR) << "Requested size, " << totalSize << ", exceeds the limitation.";
                    return EvsResult::INVALID_ARG;
                }

                mBufferPoolSize = totalSize;
                auto it = mCameraBufferPoolSize.find(id);
                if (it != mCameraBufferPoolSize.end()) {
                    bufferCount += it->second;
                }
                mCameraBufferPoolSize.insert_or_assign(id, bufferCount);
                return EvsResult::OK;
            });

    // We manage the camera ownership on recency-basis; therefore we simply
    // replace the client in this method.
    ON_CALL(*mockCamera, startVideoStream)
            .WillByDefault([this, id = mockCamera->getId()](
                                   const ::android::sp<hidlevs::V1_0::IEvsCameraStream>& cb) {
                // TODO(b/235110887): Notifies a camera loss to the current
                //                    client.
                size_t n = 0;
                {
                    std::lock_guard l(mLock);
                    mCameraClient.insert_or_assign(id, cb);
                    n = mNumberOfFramesToSend;
                }

                std::packaged_task<void(MockHidlEvsHal_1_0*, size_t, const std::string&)> task(
                        &MockHidlEvsHal_1_0::forwardFrames);
                std::thread t(std::move(task), this, /* numberOfFramesForward= */ n, id);
                std::lock_guard l(mLock);
                mCameraFrameThread.insert_or_assign(id, std::move(t));

                return EvsResult::OK;
            });

    // We simply drop a current client.
    ON_CALL(*mockCamera, stopVideoStream).WillByDefault([this, id = mockCamera->getId()]() {
        ::android::sp<IEvsCameraStream> cb;
        std::thread threadToJoin;
        {
            std::lock_guard l(mLock);
            auto state = mStreamState.find(id);
            if (state == mStreamState.end() || state->second != StreamState::kRunning) {
                return ::android::hardware::Void();
            }

            auto callback = mCameraClient.find(id);
            if (callback == mCameraClient.end()) {
                return ::android::hardware::Void();
            }

            cb = callback->second;
            callback->second = nullptr;
            state->second = StreamState::kStopping;

            auto it = mCameraFrameThread.find(id);
            if (it == mCameraFrameThread.end() || !it->second.joinable()) {
                return ::android::hardware::Void();
            }

            threadToJoin = std::move(it->second);
            mCameraFrameThread.erase(it);
        }

        if (cb) {
            // TODO(b/263438927): notify the end of a video stream by sending a null buffer.
        }

        // Join a frame-forward thread
        threadToJoin.join();
        return ::android::hardware::Void();
    });

    std::lock_guard l(mLock);
    mMockHidlEvsCameras.push_back(std::move(mockCamera));
    mMockDeviceStatus.insert_or_assign(deviceId, true);

    return true;
}

void MockHidlEvsHal_1_0::removeMockCameraDevice(const std::string& deviceId) {
    std::lock_guard l(mLock);
    auto it = mMockDeviceStatus.find(deviceId);
    if (it == mMockDeviceStatus.end()) {
        // Nothing to do.
        return;
    }

    mMockDeviceStatus[deviceId] = false;
}

void MockHidlEvsHal_1_0::configureDisplays(size_t n) {
    // Build mock IEvsDisplcy instances
    std::vector<::android::sp<NiceMockHidlEvsDisplay>> displays(n);

    for (auto i = 0; i < n; ++i) {
        (void)addMockDisplayDevice(i);
    }
}

bool MockHidlEvsHal_1_0::addMockDisplayDevice(int id) {
    ::android::sp<NiceMockHidlEvsDisplay> mockDisplay = new (std::nothrow) NiceMockHidlEvsDisplay();

    ON_CALL(*mockDisplay, getDisplayInfo)
            .WillByDefault([id](MockHidlEvsDisplay::getDisplayInfo_cb callback) {
                DisplayDesc desc = {
                        .displayId = "MockDisplay" + std::to_string(id),
                        // For the testing purpose, we put a display id in the vendor
                        // flag field.
                        .vendorFlags = static_cast<uint32_t>(id),
                };

                callback(desc);
                return ::android::hardware::Void();
            });

    ON_CALL(*mockDisplay, getDisplayState).WillByDefault([this]() { return mCurrentDisplayState; });

    ON_CALL(*mockDisplay, getTargetBuffer)
            .WillByDefault([](MockHidlEvsDisplay::getTargetBuffer_cb callback) {
                // TODO(b/263438927): implement this method.
                callback({});
                return ::android::hardware::Void();
            });

    ON_CALL(*mockDisplay, returnTargetBufferForDisplay)
            .WillByDefault([](const hidlevs::V1_0::BufferDesc& in) {
                // TODO(b/263438927): implement this method.
                (void)in;
                return EvsResult::OK;
            });

    ON_CALL(*mockDisplay, setDisplayState).WillByDefault([this](DisplayState in) {
        mCurrentDisplayState = in;
        return EvsResult::OK;
    });

    std::lock_guard l(mLock);
    mMockHidlEvsDisplays.push_back(std::move(mockDisplay));
    mMockDeviceStatus.insert_or_assign(std::to_string(id), true);

    return true;
}

void MockHidlEvsHal_1_0::removeMockDisplayDevice(int id) {
    std::lock_guard l(mLock);
    auto key = std::to_string(id);
    auto it = mMockDeviceStatus.find(key);
    if (it == mMockDeviceStatus.end()) {
        // Nothing to do.
        return;
    }

    mMockDeviceStatus[key] = false;
}

size_t MockHidlEvsHal_1_0::setNumberOfFramesToSend(size_t n) {
    std::lock_guard l(mLock);
    return mNumberOfFramesToSend = n;
}

void MockHidlEvsHal_1_0::configureEnumerator() {
    ::android::sp<NiceMockHidlEvsEnumerator_1_0> mockEnumerator =
            new (std::nothrow) NiceMockHidlEvsEnumerator_1_0();

    ON_CALL(*mockEnumerator, closeCamera)
            .WillByDefault([this](const ::android::sp<hidlevs::V1_0::IEvsCamera>& handle) {
                ::android::sp<IEvsCamera> c = IEvsCamera::castFrom(handle);
                CameraDesc desc;
                c->getCameraInfo([&desc](auto& read) { desc = read; });

                std::lock_guard l(mLock);
                auto it = mCameraBufferPoolSize.find(desc.cameraId);
                if (it == mCameraBufferPoolSize.end()) {
                    // Safely ignore a request if we fail to find a corresponding mock
                    // camera.
                    return ::android::hardware::Void();
                }

                mBufferPoolSize -= it->second;
                if (mBufferPoolSize < 0) {
                    LOG(WARNING) << "mBuffeRPoolSize should not have a negative value, "
                                 << mBufferPoolSize;
                    mBufferPoolSize = 0;
                }
                mCameraBufferPoolSize.insert_or_assign(desc.cameraId, 0);
                return ::android::hardware::Void();
            });

    ON_CALL(*mockEnumerator, closeDisplay)
            .WillByDefault([this]([[maybe_unused]] const ::android::sp<hidlevs::V1_0::IEvsDisplay>&
                                          displayObj) {
                auto pActiveDisplay = mActiveDisplay.promote();
                if (!pActiveDisplay) {
                    LOG(WARNING) << "Got a request to close a display already destroyed.";
                }

                // Nothing else to do.
                return ::android::hardware::Void();
            });

    ON_CALL(*mockEnumerator, getCameraList)
            .WillByDefault([this](MockHidlEvsEnumerator_1_0::getCameraList_cb callback) {
                ::android::hardware::hidl_vec<hidlevs::V1_0::CameraDesc> list(
                        mMockHidlEvsCameras.size());

                for (auto i = 0; i < mMockHidlEvsCameras.size(); ++i) {
                    mMockHidlEvsCameras[i]->getCameraInfo([&](auto& desc) { list[i] = desc; });

                    // Inserts a camera record if it does not exist.
                    if (mCameraList.find(list[i].cameraId) == mCameraList.end()) {
                        mCameraList.insert_or_assign(list[i].cameraId, list[i]);
                    }
                }

                callback(list);
                return ::android::hardware::Void();
            });

    ON_CALL(*mockEnumerator, getDisplayState).WillByDefault([this]() {
        return mCurrentDisplayState;
    });

    ON_CALL(*mockEnumerator, openCamera)
            .WillByDefault([this](const ::android::hardware::hidl_string& id)
                                   -> ::android::sp<hidlevs::V1_0::IEvsCamera> {
                auto it = std::find_if(mMockHidlEvsCameras.begin(), mMockHidlEvsCameras.end(),
                                       [id](const ::android::sp<NiceMockHidlEvsCamera>& c) {
                                           hidlevs::V1_0::CameraDesc desc;
                                           c->getCameraInfo([&desc](auto& read) { desc = read; });
                                           return desc.cameraId == id;
                                       });

                if (it == mMockHidlEvsCameras.end()) {
                    return nullptr;
                }

                auto instance = mCameraList.find(id);  // Guaranteed to exist always.
                instance->second.activeInstance = *it;
                return *it;
            });

    ON_CALL(*mockEnumerator, openDisplay).WillByDefault([this]() -> ::android::sp<IEvsDisplay> {
        // This method returns the first display always.
        if (mMockHidlEvsDisplays.empty()) {
            return nullptr;
        }

        mActiveDisplay = mMockHidlEvsDisplays[0];
        mCurrentDisplayState = DisplayState::NOT_VISIBLE;
        return mMockHidlEvsDisplays[0];
    });

    mMockHidlEvsEnumerator = std::move(mockEnumerator);
}

}  // namespace aidl::android::automotive::evs::implementation
