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

#include "MockHidlEvsHal.h"

#include "Constants.h"

#include <android-base/logging.h>
#include <camera/CameraMetadata.h>
#include <hardware/gralloc.h>
#include <ui/DisplayMode.h>
#include <utils/SystemClock.h>

#include <functional>
#include <future>

#include <system/graphics-base.h>

namespace {

using ::android::hardware::automotive::evs::V1_0::DisplayDesc;
using ::android::hardware::automotive::evs::V1_0::DisplayState;
using ::android::hardware::automotive::evs::V1_0::EvsResult;
using ::android::hardware::automotive::evs::V1_1::BufferDesc;
using ::android::hardware::automotive::evs::V1_1::CameraDesc;
using ::android::hardware::automotive::evs::V1_1::CameraParam;
using ::android::hardware::automotive::evs::V1_1::EvsEventDesc;
using ::android::hardware::automotive::evs::V1_1::EvsEventType;
using ::android::hardware::automotive::evs::V1_1::IEvsCamera;
using ::android::hardware::automotive::evs::V1_1::IEvsCameraStream;
using ::android::hardware::automotive::evs::V1_1::IEvsDisplay;
using ::android::hardware::automotive::evs::V1_1::IEvsEnumerator;
using ::android::hardware::automotive::evs::V1_1::IEvsUltrasonicsArray;
using ::android::hardware::camera::device::V3_2::Stream;
using ::std::chrono_literals::operator""ms;
using ::std::chrono_literals::operator""s;

inline constexpr char kMockCameraDeviceNamePrefix[] = "/dev/mockcamera";
inline constexpr int32_t kCameraParamDefaultMinValue = -255;
inline constexpr int32_t kCameraParamDefaultMaxValue = 255;
inline constexpr int32_t kCameraParamDefaultStepValue = 3;
inline constexpr size_t kMinimumNumBuffers = 2;
inline constexpr size_t kMaximumNumBuffers = 10;

}  // namespace

namespace aidl::android::automotive::evs::implementation {

MockHidlEvsHal::~MockHidlEvsHal() {
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

::android::sp<IEvsEnumerator> MockHidlEvsHal::getEnumerator() {
    if (!mMockHidlEvsEnumerator) {
        LOG(ERROR) << "MockHidlEvsHal has not initialized yet.";
        return nullptr;
    }

    return mMockHidlEvsEnumerator;
}

void MockHidlEvsHal::initialize() {
    initializeBufferPool(kMaximumNumBuffers);
    configureCameras(mNumCameras);
    configureDisplays(mNumDisplays);
    configureEnumerator();
}

bool MockHidlEvsHal::buildCameraMetadata(int32_t width, int32_t height, int32_t format,
                                         ::android::hardware::hidl_vec<uint8_t>* out) {
    ::android::CameraMetadata metadata;

    const std::vector<int32_t> availableStreamConfigurations =
            {format, width, height, ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS_OUTPUT};

    metadata.update(ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS,
                    availableStreamConfigurations.data(), availableStreamConfigurations.size());

    camera_metadata_t* p = metadata.release();
    if (validate_camera_metadata_structure(p, /* expected_size= */ nullptr) != ::android::OK) {
        LOG(ERROR) << "Failed to build a camera metadata.";
        return false;
    }

    size_t n = get_camera_metadata_size(p);
    out->resize(n);
    memcpy(out->data(), p, n);

    return true;
}

void MockHidlEvsHal::forwardFrames(size_t numberOfFramesToForward, const std::string& deviceId) {
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

        bufferToForward.timestamp = static_cast<int64_t>(::android::elapsedRealtimeNano() * 1e+3);
        bufferToForward.deviceId = deviceId;

        // Mark a buffer in-use.
        mBuffersInUse.push_back(bufferToForward);
        l.unlock();

        // Forward a duplicated buffer.  This must be done without a lock
        // because a shared data will be modified in doneWithFrame().
        ::android::hardware::hidl_vec<BufferDesc> packet;
        packet.resize(1);
        packet[0] = bufferToForward;
        it->second->deliverFrame_1_1(packet);

        LOG(DEBUG) << deviceId << ": " << (count + 1) << "/" << numberOfFramesToForward
                   << " frames are sent";
        std::this_thread::sleep_for(33ms);  // 30 frames per seconds
        l.lock();
    }

    if (mStreamState.find(deviceId) != mStreamState.end()) {
        mStreamState[deviceId] = StreamState::kStopped;
    }
}

size_t MockHidlEvsHal::initializeBufferPool(size_t requested) {
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
        buffer_handle_t memHandle = AHardwareBuffer_getNativeHandle(ahwb);
        BufferDesc aBuffer = {
                .buffer =
                        {
                                .nativeHandle = memHandle,
                        },
                .pixelSize = 4,
                .bufferId = static_cast<uint32_t>(count),
                .deviceId = "Mock EvsCamera",
        };

        *(reinterpret_cast<AHardwareBuffer_Desc*>(&aBuffer.buffer.description)) = desc;
        mBufferRecord.insert_or_assign(count, ahwb);
        mBufferPool.push_back(std::move(aBuffer));
    }

    return mBufferPool.size();
}

void MockHidlEvsHal::deinitializeBufferPoolLocked() {
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

void MockHidlEvsHal::configureCameras(size_t n) {
    // Initializes a list of the camera parameters each mock camera
    // supports with their default values.
    mCameraParams = {{CameraParam::BRIGHTNESS, 80},
                     {CameraParam::CONTRAST, 60},
                     {CameraParam::AUTOGAIN, 3},
                     {CameraParam::AUTO_EXPOSURE, 1}};

    for (auto i = 0; i < n; ++i) {
        (void)addMockCameraDevice(kMockCameraDeviceNamePrefix + std::to_string(i));
    }
}

bool MockHidlEvsHal::addMockCameraDevice(const std::string& deviceId) {
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

    ON_CALL(*mockCamera, doneWithFrame_1_1)
            .WillByDefault([this](const ::android::hardware::hidl_vec<BufferDesc>& buffers) {
                size_t returned = 0;
                std::lock_guard lock(mLock);
                for (auto& b : buffers) {
                    auto it = std::find_if(mBuffersInUse.begin(), mBuffersInUse.end(),
                                           [id = b.bufferId](const BufferDesc& desc) {
                                               return id == desc.bufferId;
                                           });
                    if (it == mBuffersInUse.end()) {
                        continue;
                    }

                    ++returned;
                    mBufferPool.push_back(std::move(*it));
                    mBuffersInUse.erase(it);
                }

                if (returned > 0) {
                    mBufferAvailableSignal.notify_all();
                    return EvsResult::OK;
                } else {
                    return EvsResult::INVALID_ARG;
                }
            });

    // EVS HAL accepts only a single client; therefore, this method
    // returns a success always.
    ON_CALL(*mockCamera, forceMaster)
            .WillByDefault(
                    [](const ::android::sp<hidlevs::V1_0::IEvsDisplay>&) { return EvsResult::OK; });

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

    ON_CALL(*mockCamera, getCameraInfo_1_1)
            .WillByDefault([deviceId, this](MockHidlEvsCamera::getCameraInfo_1_1_cb callback) {
                CameraDesc mockDesc = {
                        .v1 =
                                {
                                        .cameraId = deviceId,
                                        .vendorFlags = 0x0,
                                },
                        .metadata = {},
                };

                if (!buildCameraMetadata(/* width= */ 640, /* height= */ 480,
                                         /* format= */ HAL_PIXEL_FORMAT_RGBA_8888,
                                         &mockDesc.metadata)) {
                    return ::android::hardware::Void();
                }

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

    ON_CALL(*mockCamera, getExtendedInfo_1_1)
            .WillByDefault([this](uint32_t id, MockHidlEvsCamera::getExtendedInfo_1_1_cb callback) {
                auto it = mCameraExtendedInfo.find(id);
                if (it == mCameraExtendedInfo.end()) {
                    // A requested information does not exist.
                    callback(EvsResult::INVALID_ARG, 0);
                    return ::android::hardware::Void();
                }

                ::android::hardware::hidl_vec<uint8_t> value = it->second;
                callback(EvsResult::OK, value);
                return ::android::hardware::Void();
            });

    // This method will return a value of a requested camera parameter
    // if it is supported by a mock EVS camera.
    ON_CALL(*mockCamera, getIntParameter)
            .WillByDefault([this](CameraParam id, MockHidlEvsCamera::getIntParameter_cb callback) {
                auto it = mCameraParams.find(id);
                if (it == mCameraParams.end()) {
                    LOG(ERROR) << "Ignore a request to read an unsupported parameter, " << (int)id;
                    callback(EvsResult::INVALID_ARG, 0);
                    return ::android::hardware::Void();
                }

                // EVS HAL always returns a single integer value.
                ::android::hardware::hidl_vec<int32_t> values;
                values.resize(1);
                values[0] = it->second;
                callback(EvsResult::OK, values);
                return ::android::hardware::Void();
            });

    // This method returns the same range values if a requested camera
    // parameter is supported by a mock EVS camera.
    ON_CALL(*mockCamera, getIntParameterRange)
            .WillByDefault(
                    [this](CameraParam id, MockHidlEvsCamera::getIntParameterRange_cb callback) {
                        auto it = mCameraParams.find(id);
                        if (it == mCameraParams.end()) {
                            callback(0, 0, 0);
                            return ::android::hardware::Void();
                        }

                        // For the testing purpose, this mock EVS HAL always returns the
                        // same values.
                        callback(kCameraParamDefaultMinValue, kCameraParamDefaultMaxValue,
                                 kCameraParamDefaultStepValue);
                        return ::android::hardware::Void();
                    });

    // This method returns a list of camera parameters supported by a
    // mock EVS camera.
    ON_CALL(*mockCamera, getParameterList)
            .WillByDefault([this](MockHidlEvsCamera::getParameterList_cb callback) {
                ::android::hardware::hidl_vec<CameraParam> list;
                list.resize(mCameraParams.size());
                unsigned idx = 0;
                for (auto& [k, _] : mCameraParams) {
                    list[idx++] = k;
                }

                callback(list);
                return ::android::hardware::Void();
            });

    // This method behaves exactly the same as getCameraInfo() because
    // the EVS HAL does not support a concept of the group (or logical)
    // camera.
    ON_CALL(*mockCamera, getPhysicalCameraInfo)
            .WillByDefault([deviceId]([[maybe_unused]] const ::android::hardware::hidl_string& id,
                                      MockHidlEvsCamera::getPhysicalCameraInfo_cb callback) {
                CameraDesc mockDesc = {
                        .v1 =
                                {
                                        .cameraId = deviceId,
                                        .vendorFlags = 0x0,
                                },
                        .metadata = {},
                };

                callback(mockDesc);
                return ::android::hardware::Void();
            });

    // This method adds given buffer descriptors to the internal buffer
    // pool if their identifiers do not conflict to existing ones.
    ON_CALL(*mockCamera, importExternalBuffers)
            .WillByDefault([this](const ::android::hardware::hidl_vec<BufferDesc>& buffers,
                                  MockHidlEvsCamera::importExternalBuffers_cb callback) {
                std::lock_guard l(mLock);
                size_t count = 0;
                for (auto i = 0; i < buffers.size(); ++i) {
                    auto it = std::find_if(mBufferPool.begin(), mBufferPool.end(),
                                           [&](const BufferDesc& b) {
                                               return b.bufferId == buffers[i].bufferId;
                                           });
                    if (it != mBufferPool.end()) {
                        // Ignores external buffers with a conflicting
                        // identifier.
                        continue;
                    }

                    // TODO(b/235110887): Add external buffers to the pool.
                    //
                    // Temporarily, we count the number of buffers that
                    // identifiers do not conflict with existing buffers.
                    ++count;
                }

                callback(EvsResult::OK, count);
                return ::android::hardware::Void();
            });

    ON_CALL(*mockCamera, pauseVideoStream).WillByDefault([]() {
        return EvsResult::UNDERLYING_SERVICE_ERROR;
    });

    ON_CALL(*mockCamera, resumeVideoStream).WillByDefault([]() {
        return EvsResult::UNDERLYING_SERVICE_ERROR;
    });

    // This method stores a given vector with id.
    ON_CALL(*mockCamera, setExtendedInfo).WillByDefault([this](uint32_t id, int32_t v) {
        ::android::hardware::hidl_vec<uint8_t> value;
        value.resize(sizeof(v));
        *(reinterpret_cast<int32_t*>(value.data())) = v;
        mCameraExtendedInfo.insert_or_assign(id, value);
        return EvsResult::OK;
    });

    ON_CALL(*mockCamera, setExtendedInfo_1_1)
            .WillByDefault([this](uint32_t id, const ::android::hardware::hidl_vec<uint8_t>& v) {
                mCameraExtendedInfo.insert_or_assign(id, v);
                return EvsResult::OK;
            });

    // This method updates a parameter value if exists.
    ON_CALL(*mockCamera, setIntParameter)
            .WillByDefault([this](CameraParam id, int32_t in,
                                  MockHidlEvsCamera::setIntParameter_cb callback) {
                auto it = mCameraParams.find(id);
                if (it == mCameraParams.end()) {
                    LOG(ERROR) << "Ignore a request to program an unsupported parameter, "
                               << (int)id;
                    callback(EvsResult::INVALID_ARG, {});
                    return ::android::hardware::Void();
                }

                in = in > kCameraParamDefaultMaxValue      ? kCameraParamDefaultMaxValue
                        : in < kCameraParamDefaultMinValue ? kCameraParamDefaultMinValue
                                                           : in;
                mCameraParams.insert_or_assign(id, in);
                ::android::hardware::hidl_vec<int32_t> values;
                values.resize(1);
                values[0] = in;
                callback(EvsResult::OK, values);
                return ::android::hardware::Void();
            });

    // We always return a success because EVS HAL does not allow
    // multiple camera clients exist.
    ON_CALL(*mockCamera, setMaster).WillByDefault([]() { return EvsResult::OK; });

    // Because EVS HAL does allow multiple camera clients exist, we simply
    // set the size of the buffer pool.
    ON_CALL(*mockCamera, setMaxFramesInFlight)
            .WillByDefault([this, id = mockCamera->getId()](uint32_t bufferCount) {
                std::lock_guard l(mLock);
                if (bufferCount < kMinimumNumBuffers) {
                    LOG(WARNING) << "Requested buffer pool size is too small to run a camera; "
                                    "adjusting the pool size to "
                                 << kMinimumNumBuffers;
                    bufferCount = kMinimumNumBuffers;
                }

                int64_t delta = bufferCount;
                auto it = mCameraBufferPoolSize.find(id);
                if (it != mCameraBufferPoolSize.end()) {
                    delta -= it->second;
                }

                if (!delta) {
                    // No further action required.
                    return EvsResult::OK;
                }

                size_t totalSize = mBufferPoolSize + delta;
                if (totalSize > kMaximumNumBuffers) {
                    LOG(ERROR) << "Requested size, " << totalSize << ", exceeds the limitation.";
                    return EvsResult::INVALID_ARG;
                }

                mBufferPoolSize = totalSize;
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
                    auto client = IEvsCameraStream::castFrom(cb).withDefault(nullptr);
                    if (!client) {
                        // This mock implementation supports v1.1 hidl
                        // client only.
                        return EvsResult::INVALID_ARG;
                    }
                    mCameraClient.insert_or_assign(id, client);
                    n = mNumberOfFramesToSend;
                }

                std::packaged_task<void(MockHidlEvsHal*, size_t, const std::string&)> task(
                        &MockHidlEvsHal::forwardFrames);
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
            EvsEventDesc e = {
                    .deviceId = id,
                    .aType = EvsEventType::STREAM_STOPPED,
            };
            cb->notify(e);
        }

        // Join a frame-forward thread
        threadToJoin.join();
        return ::android::hardware::Void();
    });

    // We don't take any action because EVS HAL allows only a single camera
    // client exists at a time.
    ON_CALL(*mockCamera, unsetMaster).WillByDefault([]() { return EvsResult::OK; });

    std::lock_guard l(mLock);
    mMockHidlEvsCameras.push_back(std::move(mockCamera));
    mMockDeviceStatus.insert_or_assign(deviceId, true);

    return true;
}

void MockHidlEvsHal::removeMockCameraDevice(const std::string& deviceId) {
    std::lock_guard l(mLock);
    auto it = mMockDeviceStatus.find(deviceId);
    if (it == mMockDeviceStatus.end()) {
        // Nothing to do.
        return;
    }

    mMockDeviceStatus[deviceId] = false;
}

void MockHidlEvsHal::configureDisplays(size_t n) {
    // Build mock IEvsDisplcy instances
    std::vector<::android::sp<NiceMockHidlEvsDisplay>> displays(n);

    for (auto i = 0; i < n; ++i) {
        (void)addMockDisplayDevice(i);
    }
}

bool MockHidlEvsHal::addMockDisplayDevice(int id) {
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

    ON_CALL(*mockDisplay, getDisplayInfo_1_1)
            .WillByDefault([id, this](MockHidlEvsDisplay::getDisplayInfo_1_1_cb callback) {
                DisplayDesc desc = {
                        .displayId = "MockDisplay" + std::to_string(id),
                        // For the testing purpose, we put a display id in the vendor
                        // flag field.
                        .vendorFlags = static_cast<uint32_t>(id),
                };

                ::android::hardware::hidl_vec<uint8_t> config;
                config.resize(sizeof(::android::ui::DisplayMode));
                ::android::ui::DisplayMode* p =
                        reinterpret_cast<::android::ui::DisplayMode*>(config.data());
                p->resolution = ::android::ui::Size(64, 32);

                ::android::hardware::hidl_vec<uint8_t> state;
                state.resize(sizeof(mCurrentDisplayState));
                *(reinterpret_cast<DisplayState*>(state.data())) = mCurrentDisplayState;

                callback(config, state);
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

void MockHidlEvsHal::removeMockDisplayDevice(int id) {
    std::lock_guard l(mLock);
    auto key = std::to_string(id);
    auto it = mMockDeviceStatus.find(key);
    if (it == mMockDeviceStatus.end()) {
        // Nothing to do.
        return;
    }

    mMockDeviceStatus[key] = false;
}

size_t MockHidlEvsHal::setNumberOfFramesToSend(size_t n) {
    std::lock_guard l(mLock);
    return mNumberOfFramesToSend = n;
}

void MockHidlEvsHal::configureEnumerator() {
    ::android::sp<NiceMockHidlEvsEnumerator> mockEnumerator =
            new (std::nothrow) NiceMockHidlEvsEnumerator();

    ON_CALL(*mockEnumerator, closeCamera)
            .WillByDefault([this](const ::android::sp<hidlevs::V1_0::IEvsCamera>& handle) {
                ::android::sp<IEvsCamera> c = IEvsCamera::castFrom(handle);
                CameraDesc desc;
                c->getCameraInfo_1_1([&desc](auto& read) { desc = read; });

                std::lock_guard l(mLock);
                auto it = mCameraBufferPoolSize.find(desc.v1.cameraId);
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
                mCameraBufferPoolSize.insert_or_assign(desc.v1.cameraId, 0);
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

    ON_CALL(*mockEnumerator, closeUltrasonicsArray)
            .WillByDefault([](const ::android::sp<IEvsUltrasonicsArray>&) {
                // Mock EVS HAL does not support IEvsUltrasonicsArray.
                return ::android::hardware::Void();
            });

    ON_CALL(*mockEnumerator, getCameraList)
            .WillByDefault([this](MockHidlEvsEnumerator::getCameraList_cb callback) {
                ::android::hardware::hidl_vec<hidlevs::V1_0::CameraDesc> list(
                        mMockHidlEvsCameras.size());

                for (auto i = 0; i < mMockHidlEvsCameras.size(); ++i) {
                    mMockHidlEvsCameras[i]->getCameraInfo([&](auto& desc) { list[i] = desc; });

                    // Inserts a camera record if it does not exist.
                    if (mCameraList.find(list[i].cameraId) == mCameraList.end()) {
                        CameraDesc newDevice = {
                                .v1 = list[i],
                        };
                        mCameraList.insert_or_assign(list[i].cameraId, newDevice);
                    }
                }

                callback(list);
                return ::android::hardware::Void();
            });

    ON_CALL(*mockEnumerator, getCameraList_1_1)
            .WillByDefault([this](MockHidlEvsEnumerator::getCameraList_1_1_cb callback) {
                std::vector<CameraDesc> list(mMockHidlEvsCameras.size());

                for (auto i = 0; i < mMockHidlEvsCameras.size(); ++i) {
                    mMockHidlEvsCameras[i]->getCameraInfo_1_1([&](auto& desc) { list[i] = desc; });

                    // Inserts a camera record if it does not exist.
                    if (mCameraList.find(list[i].v1.cameraId) == mCameraList.end()) {
                        mCameraList.insert_or_assign(list[i].v1.cameraId, list[i]);
                    }
                }

                callback(list);
                return ::android::hardware::Void();
            });

    ON_CALL(*mockEnumerator, getDisplayIdList)
            .WillByDefault([this](MockHidlEvsEnumerator::getDisplayIdList_cb callback) {
                ::android::hardware::hidl_vec<uint8_t> list;
                list.resize(mMockHidlEvsDisplays.size());

                for (auto i = 0; i < mMockHidlEvsDisplays.size(); ++i) {
                    mMockHidlEvsDisplays[i]->getDisplayInfo([&](auto& desc) {
                        // MockHidlEvsDisplay contains a display ID in its vendor flags.
                        list[i] = desc.vendorFlags;
                    });
                }

                callback(list);
                return ::android::hardware::Void();
            });

    ON_CALL(*mockEnumerator, getDisplayState).WillByDefault([this]() {
        return mCurrentDisplayState;
    });

    ON_CALL(*mockEnumerator, getUltrasonicsArrayList)
            .WillByDefault([]([[maybe_unused]] MockHidlEvsEnumerator::getUltrasonicsArrayList_cb
                                      callback) {
                // Mock EVS HAL does not support IEvsUltrasonicsArray yet.
                return ::android::hardware::Void();
            });

    ON_CALL(*mockEnumerator, isHardware).WillByDefault([]() { return false; });

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

    ON_CALL(*mockEnumerator, openCamera_1_1)
            .WillByDefault(
                    [this](const ::android::hardware::hidl_string& id,
                           [[maybe_unused]] const Stream& config) -> ::android::sp<IEvsCamera> {
                        auto it =
                                std::find_if(mMockHidlEvsCameras.begin(), mMockHidlEvsCameras.end(),
                                             [id](const ::android::sp<NiceMockHidlEvsCamera>& c) {
                                                 CameraDesc desc;
                                                 c->getCameraInfo_1_1(
                                                         [&desc](auto& read) { desc = read; });
                                                 return desc.v1.cameraId == id;
                                             });

                        if (it == mMockHidlEvsCameras.end()) {
                            return nullptr;
                        }

                        auto instance = mCameraList.find(id);  // Guaranteed to exist always.
                        instance->second.activeInstance = *it;
                        return *it;
                    });

    ON_CALL(*mockEnumerator, openDisplay).WillByDefault([]() {
        // TODO(b/263438927): implement this method.
        return nullptr;
    });

    ON_CALL(*mockEnumerator, openDisplay_1_1)
            .WillByDefault([this](uint8_t id) -> ::android::sp<IEvsDisplay> {
                if (id == kExclusiveDisplayId) {
                    if (mDisplayOwnedExclusively && !mActiveDisplay.promote()) {
                        return nullptr;
                    }

                    DisplayDesc desc;
                    mMockHidlEvsDisplays[0]->getDisplayInfo([&desc](auto& read) { desc = read; });
                    id = desc.vendorFlags;  // the first display in the list is
                                            // the main display.
                    mDisplayOwnedExclusively = true;
                }

                auto it = std::find_if(mMockHidlEvsDisplays.begin(), mMockHidlEvsDisplays.end(),
                                       [id](const ::android::sp<NiceMockHidlEvsDisplay>& d) {
                                           DisplayDesc desc;
                                           d->getDisplayInfo([&desc](auto& read) { desc = read; });
                                           return desc.vendorFlags == id;
                                       });

                if (it == mMockHidlEvsDisplays.end()) {
                    return nullptr;
                }

                mActiveDisplay = *it;
                mCurrentDisplayState = DisplayState::NOT_VISIBLE;
                return *it;
            });

    ON_CALL(*mockEnumerator, openUltrasonicsArray)
            .WillByDefault([]([[maybe_unused]] const ::android::hardware::hidl_string& id) {
                // Mock EVS HAL does not support IEvsUltrasonicsArray yet.
                return nullptr;
            });

    mMockHidlEvsEnumerator = std::move(mockEnumerator);
}

}  // namespace aidl::android::automotive::evs::implementation
