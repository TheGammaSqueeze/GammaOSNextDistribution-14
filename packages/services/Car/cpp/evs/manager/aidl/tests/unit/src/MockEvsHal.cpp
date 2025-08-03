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

#include "MockEvsHal.h"

#include "Constants.h"

#include <aidl/android/hardware/automotive/evs/Rotation.h>
#include <aidl/android/hardware/automotive/evs/StreamType.h>
#include <aidl/android/hardware/common/NativeHandle.h>
#include <aidlcommonsupport/NativeHandle.h>
#include <android-base/logging.h>
#include <camera/CameraMetadata.h>
#include <hardware/gralloc.h>
#include <utils/SystemClock.h>

#include <functional>
#include <future>

#include <system/graphics-base.h>

namespace {

using ::aidl::android::hardware::automotive::evs::BufferDesc;
using ::aidl::android::hardware::automotive::evs::CameraDesc;
using ::aidl::android::hardware::automotive::evs::CameraParam;
using ::aidl::android::hardware::automotive::evs::DeviceStatus;
using ::aidl::android::hardware::automotive::evs::DeviceStatusType;
using ::aidl::android::hardware::automotive::evs::DisplayDesc;
using ::aidl::android::hardware::automotive::evs::DisplayState;
using ::aidl::android::hardware::automotive::evs::EvsEventDesc;
using ::aidl::android::hardware::automotive::evs::EvsEventType;
using ::aidl::android::hardware::automotive::evs::EvsResult;
using ::aidl::android::hardware::automotive::evs::IEvsCamera;
using ::aidl::android::hardware::automotive::evs::IEvsCameraStream;
using ::aidl::android::hardware::automotive::evs::IEvsDisplay;
using ::aidl::android::hardware::automotive::evs::IEvsEnumerator;
using ::aidl::android::hardware::automotive::evs::IEvsEnumeratorStatusCallback;
using ::aidl::android::hardware::automotive::evs::IEvsUltrasonicsArray;
using ::aidl::android::hardware::automotive::evs::ParameterRange;
using ::aidl::android::hardware::automotive::evs::Rotation;
using ::aidl::android::hardware::automotive::evs::Stream;
using ::aidl::android::hardware::automotive::evs::StreamType;
using ::aidl::android::hardware::common::NativeHandle;
using ::aidl::android::hardware::graphics::common::BufferUsage;
using ::aidl::android::hardware::graphics::common::HardwareBuffer;
using ::aidl::android::hardware::graphics::common::PixelFormat;
using ::std::chrono_literals::operator""ms;
using ::std::chrono_literals::operator""s;

inline constexpr char kMockCameraDeviceNamePrefix[] = "/dev/mockcamera";
inline constexpr int32_t kCameraParamDefaultMinValue = -255;
inline constexpr int32_t kCameraParamDefaultMaxValue = 255;
inline constexpr int32_t kCameraParamDefaultStepValue = 3;
inline constexpr size_t kMinimumNumBuffers = 2;
inline constexpr size_t kMaximumNumBuffers = 10;

NativeHandle copyNativeHandle(const NativeHandle& handle, bool doDup) {
    NativeHandle dup;

    dup.fds = std::vector<ndk::ScopedFileDescriptor>(handle.fds.size());
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

HardwareBuffer copyHardwareBuffer(const HardwareBuffer& buffer, bool doDup) {
    HardwareBuffer copied = {
            .description = buffer.description,
            .handle = copyNativeHandle(buffer.handle, doDup),
    };

    return std::move(copied);
}

BufferDesc copyBufferDesc(const BufferDesc& src, bool doDup) {
    BufferDesc copied = {
            .buffer = copyHardwareBuffer(src.buffer, doDup),
            .pixelSizeBytes = src.pixelSizeBytes,
            .bufferId = src.bufferId,
            .deviceId = src.deviceId,
            .timestamp = src.timestamp,
            .metadata = src.metadata,
    };

    return std::move(copied);
}

}  // namespace

namespace aidl::android::automotive::evs::implementation {

MockEvsHal::~MockEvsHal() {
    std::unordered_map<std::string, std::thread> threads;
    {
        std::lock_guard lock(mLock);
        for (auto& [id, t] : mCameraFrameThread) {
            auto it = mStreamState.find(id);
            if (it != mStreamState.end() && it->second == StreamState::kRunning) {
                it->second = StreamState::kStopping;
            }

            threads.insert_or_assign(id, std::move(t));
        }
        mCameraFrameThread.clear();
    }

    // Join all frame-forwarding threads.
    for (auto& [_, t] : threads) {
        if (!t.joinable()) {
            continue;
        }

        t.join();
    }

    {
        std::lock_guard lock(mLock);
        deinitializeBufferPoolLocked();
        mStreamState.clear();
        mCameraClient.clear();
    }
}

std::shared_ptr<IEvsEnumerator> MockEvsHal::getEnumerator() {
    if (!mMockEvsEnumerator) {
        LOG(ERROR) << "MockEvsHal has not initialized yet.";
        return nullptr;
    }

    return IEvsEnumerator::fromBinder(mMockEvsEnumerator->asBinder());
}

void MockEvsHal::initialize() {
    initializeBufferPool(kMaximumNumBuffers);
    configureCameras(mNumCameras);
    configureDisplays(mNumDisplays);
    configureEnumerator();
}

bool MockEvsHal::buildCameraMetadata(int32_t width, int32_t height, int32_t format,
                                     std::vector<uint8_t>* out) {
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

void MockEvsHal::forwardFrames(size_t numberOfFramesToForward, const std::string& deviceId) {
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

        // Duplicate a buffer.
        BufferDesc bufferToUse = std::move(mBufferPool.back());
        mBufferPool.pop_back();

        BufferDesc bufferToForward = copyBufferDesc(bufferToUse, /* doDup= */ true);
        bufferToForward.timestamp = static_cast<int64_t>(::android::elapsedRealtimeNano() * 1e+3);
        bufferToForward.deviceId = deviceId;

        // Mark a buffer in-use.
        mBuffersInUse.push_back(std::move(bufferToUse));

        // Store a recipient before releasing a lock.
        auto client = it->second;
        l.unlock();

        // Forward a duplicated buffer.  This must be done without a lock
        // because a shared data will be modified in doneWithFrame().
        std::vector<BufferDesc> packet;
        packet.push_back(std::move(bufferToForward));
        client->deliverFrame(packet);

        LOG(DEBUG) << deviceId << ": " << (count + 1) << "/" << numberOfFramesToForward
                   << " frames are sent";
        std::this_thread::sleep_for(33ms);  // 30 frames per seconds
        l.lock();
    }

    if (mStreamState.find(deviceId) != mStreamState.end()) {
        mStreamState[deviceId] = StreamState::kStopped;
    }
}

size_t MockEvsHal::initializeBufferPool(size_t requested) {
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
                                .description =
                                        {
                                                .width = 64,
                                                .height = 32,
                                                .layers = 1,
                                                .usage = BufferUsage::CPU_READ_OFTEN,
                                                .format = PixelFormat::RGBA_8888,
                                                .stride = 64,
                                        },
                                .handle = ::android::dupToAidl(memHandle),
                        },
                .pixelSizeBytes = 1,
                .bufferId = count,
                .deviceId = "Mock EvsCamera",
        };

        mBufferRecord.insert_or_assign(count, ahwb);
        mBufferPool.push_back(std::move(aBuffer));
    }

    return mBufferPool.size();
}

void MockEvsHal::deinitializeBufferPoolLocked() {
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

void MockEvsHal::configureCameras(size_t n) {
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

bool MockEvsHal::addMockCameraDevice(const std::string& deviceId) {
    std::shared_ptr<NiceMockEvsCamera> mockCamera =
            ndk::SharedRefBase::make<NiceMockEvsCamera>(deviceId);

    // For the testing purpose, this method will return
    // EvsResult::INVALID_ARG if the client returns any buffer with
    // unknown identifier.
    ON_CALL(*mockCamera, doneWithFrame)
            .WillByDefault([this](const std::vector<BufferDesc>& buffers) {
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
                    return ndk::ScopedAStatus::ok();
                } else {
                    return ndk::ScopedAStatus::fromServiceSpecificError(
                            static_cast<int>(EvsResult::INVALID_ARG));
                }
            });

    // EVS HAL accepts only a single client; therefore, this method
    // returns a success always.
    ON_CALL(*mockCamera, forcePrimaryClient).WillByDefault([](const std::shared_ptr<IEvsDisplay>&) {
        return ndk::ScopedAStatus::ok();
    });

    // We return a mock camera descriptor with the metadata but empty vendor
    // flag.
    ON_CALL(*mockCamera, getCameraInfo).WillByDefault([deviceId, this](CameraDesc* desc) {
        CameraDesc mockDesc = {
                .id = deviceId,
                .vendorFlags = 0x0,
        };

        if (!buildCameraMetadata(/* width= */ 640, /* height= */ 480,
                                 /* format= */ HAL_PIXEL_FORMAT_RGBA_8888, &mockDesc.metadata)) {
            return ndk::ScopedAStatus::fromServiceSpecificError(
                    static_cast<int>(EvsResult::UNDERLYING_SERVICE_ERROR));
        }

        *desc = std::move(mockDesc);
        return ndk::ScopedAStatus::ok();
    });

    // This method will return a value associated with a given
    // identifier if exists.
    ON_CALL(*mockCamera, getExtendedInfo)
            .WillByDefault([this](int32_t id, std::vector<uint8_t>* v) {
                auto it = mCameraExtendedInfo.find(id);
                if (it == mCameraExtendedInfo.end()) {
                    // A requested information does not exist.
                    return ndk::ScopedAStatus::fromServiceSpecificError(
                            static_cast<int>(EvsResult::INVALID_ARG));
                }

                *v = it->second;
                return ndk::ScopedAStatus::ok();
            });

    // This method will return a value of a requested camera parameter
    // if it is supported by a mock EVS camera.
    ON_CALL(*mockCamera, getIntParameter)
            .WillByDefault([this](CameraParam id, std::vector<int32_t>* v) {
                auto it = mCameraParams.find(id);
                if (it == mCameraParams.end()) {
                    LOG(ERROR) << "Ignore a request to read an unsupported parameter, " << (int)id;
                    return ndk::ScopedAStatus::fromServiceSpecificError(
                            static_cast<int>(EvsResult::INVALID_ARG));
                }

                // EVS HAL always returns a single integer value.
                v->push_back(it->second);
                return ndk::ScopedAStatus::ok();
            });

    // This method returns the same range values if a requested camera
    // parameter is supported by a mock EVS camera.
    ON_CALL(*mockCamera, getIntParameterRange)
            .WillByDefault([this](CameraParam id, ParameterRange* range) {
                auto it = mCameraParams.find(id);
                if (it == mCameraParams.end()) {
                    return ndk::ScopedAStatus::fromServiceSpecificError(
                            static_cast<int>(EvsResult::INVALID_ARG));
                }

                // For the testing purpose, this mock EVS HAL always returns the
                // same values.
                range->min = kCameraParamDefaultMinValue;
                range->max = kCameraParamDefaultMaxValue;
                range->step = kCameraParamDefaultStepValue;
                return ndk::ScopedAStatus::ok();
            });

    // This method returns a list of camera parameters supported by a
    // mock EVS camera.
    ON_CALL(*mockCamera, getParameterList).WillByDefault([this](std::vector<CameraParam>* list) {
        for (auto& [k, _] : mCameraParams) {
            list->push_back(k);
        }
        return ndk::ScopedAStatus::ok();
    });

    // This method behaves exactly the same as getCameraInfo() because
    // the EVS HAL does not support a concept of the group (or logical)
    // camera.
    ON_CALL(*mockCamera, getPhysicalCameraInfo)
            .WillByDefault([deviceId](const std::string&, CameraDesc* desc) {
                CameraDesc mockDesc = {
                        .id = deviceId,
                        .vendorFlags = 0x0,
                        .metadata = {},
                };

                *desc = std::move(mockDesc);
                return ndk::ScopedAStatus::ok();
            });

    // This method adds given buffer descriptors to the internal buffer
    // pool if their identifiers do not conflict to existing ones.
    ON_CALL(*mockCamera, importExternalBuffers)
            .WillByDefault([this](const std::vector<BufferDesc>& buffers, int32_t* num) {
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

                    // TODO(b/235110887): Explicitly copies external buffers
                    //                    stores them in mBufferPool.
                    //
                    // Temporarily, we count the number of buffers that
                    // identifiers do not conflict with existing buffers.
                    ++count;
                }

                *num = count;
                return ndk::ScopedAStatus::ok();
            });

    ON_CALL(*mockCamera, pauseVideoStream).WillByDefault([]() { return ndk::ScopedAStatus::ok(); });

    ON_CALL(*mockCamera, resumeVideoStream).WillByDefault([]() {
        return ndk::ScopedAStatus::ok();
    });

    // This method stores a given vector with id.
    ON_CALL(*mockCamera, setExtendedInfo)
            .WillByDefault([this](int32_t id, const std::vector<uint8_t>& v) {
                mCameraExtendedInfo.insert_or_assign(id, v);
                return ndk::ScopedAStatus::ok();
            });

    // This method updates a parameter value if exists.
    ON_CALL(*mockCamera, setIntParameter)
            .WillByDefault([this](CameraParam id, int32_t in, std::vector<int32_t>* out) {
                auto it = mCameraParams.find(id);
                if (it == mCameraParams.end()) {
                    LOG(ERROR) << "Ignore a request to program an unsupported parameter, "
                               << (int)id;
                    return ndk::ScopedAStatus::fromServiceSpecificError(
                            static_cast<int>(EvsResult::INVALID_ARG));
                }

                in = in > kCameraParamDefaultMaxValue      ? kCameraParamDefaultMaxValue
                        : in < kCameraParamDefaultMinValue ? kCameraParamDefaultMinValue
                                                           : in;
                mCameraParams.insert_or_assign(id, in);
                out->push_back(in);

                return ndk::ScopedAStatus::ok();
            });

    // We always return a success because EVS HAL does not allow
    // multiple camera clients exist.
    ON_CALL(*mockCamera, setPrimaryClient).WillByDefault([]() { return ndk::ScopedAStatus::ok(); });

    // Because EVS HAL does allow multiple camera clients exist, we simply
    // set the size of the buffer pool.
    ON_CALL(*mockCamera, setMaxFramesInFlight)
            .WillByDefault([this, id = mockCamera->getId()](int32_t bufferCount) {
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
                    return ndk::ScopedAStatus::ok();
                }

                size_t totalSize = mBufferPoolSize + delta;
                if (totalSize > kMaximumNumBuffers) {
                    LOG(ERROR) << "Requested size, " << totalSize << ", exceeds the limitation.";
                    return ndk::ScopedAStatus::fromServiceSpecificError(
                            static_cast<int>(EvsResult::INVALID_ARG));
                }

                mBufferPoolSize = totalSize;
                mCameraBufferPoolSize.insert_or_assign(id, bufferCount);
                return ndk::ScopedAStatus::ok();
            });

    // We manage the camera ownership on recency-basis; therefore we simply
    // replace the client in this method.
    ON_CALL(*mockCamera, startVideoStream)
            .WillByDefault(
                    [this, id = mockCamera->getId()](const std::shared_ptr<IEvsCameraStream>& cb) {
                        // TODO(b/235110887): Notifies a camera loss to the current
                        //                    client.
                        size_t n = 0;
                        {
                            std::lock_guard l(mLock);
                            mCameraClient.insert_or_assign(id, cb);
                            n = mNumberOfFramesToSend;
                        }

                        std::lock_guard l(mLock);
                        std::packaged_task<void(MockEvsHal*, size_t, const std::string&)> task(
                                &MockEvsHal::forwardFrames);
                        std::thread t(std::move(task), this, /* numberOfFramesForward= */ n, id);
                        mCameraFrameThread.insert_or_assign(id, std::move(t));

                        return ndk::ScopedAStatus::ok();
                    });

    // We simply drop a current client.
    ON_CALL(*mockCamera, stopVideoStream).WillByDefault([this, id = mockCamera->getId()]() {
        std::shared_ptr<aidlevs::IEvsCameraStream> cb;
        std::thread threadToJoin;
        {
            std::lock_guard l(mLock);
            auto state = mStreamState.find(id);
            if (state == mStreamState.end() || state->second != StreamState::kRunning) {
                return ndk::ScopedAStatus::ok();
            }

            auto callback = mCameraClient.find(id);
            if (callback == mCameraClient.end()) {
                return ndk::ScopedAStatus::ok();
            }

            cb = callback->second;
            callback->second = nullptr;
            state->second = StreamState::kStopping;

            auto it = mCameraFrameThread.find(id);
            if (it == mCameraFrameThread.end() || !it->second.joinable()) {
                return ndk::ScopedAStatus::ok();
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
        return ndk::ScopedAStatus::ok();
    });

    // We don't take any action because EVS HAL allows only a single camera
    // client exists at a time.
    ON_CALL(*mockCamera, unsetPrimaryClient).WillByDefault([]() {
        return ndk::ScopedAStatus::ok();
    });

    std::lock_guard l(mLock);
    mMockEvsCameras.push_back(std::move(mockCamera));
    mMockDeviceStatus.insert_or_assign(deviceId, DeviceStatusType::CAMERA_AVAILABLE);

    std::vector<DeviceStatus> msg(1);
    msg[0] = {
            .id = deviceId,
            .status = DeviceStatusType::CAMERA_AVAILABLE,
    };
    for (auto callback : mDeviceStatusCallbacks) {
        callback->deviceStatusChanged(msg);
    }

    return true;
}

void MockEvsHal::removeMockCameraDevice(const std::string& deviceId) {
    std::lock_guard l(mLock);
    auto it = mMockDeviceStatus.find(deviceId);
    if (it == mMockDeviceStatus.end()) {
        // Nothing to do.
        return;
    }

    mMockDeviceStatus[deviceId] = DeviceStatusType::CAMERA_NOT_AVAILABLE;

    std::vector<DeviceStatus> msg(1);
    msg[0] = {
            .id = deviceId,
            .status = DeviceStatusType::CAMERA_NOT_AVAILABLE,
    };
    for (auto callback : mDeviceStatusCallbacks) {
        callback->deviceStatusChanged(msg);
    }
}

void MockEvsHal::configureDisplays(size_t n) {
    // Build mock IEvsDisplcy instances
    std::vector<std::shared_ptr<NiceMockEvsDisplay>> displays(n);

    for (auto i = 0; i < n; ++i) {
        (void)addMockDisplayDevice(i);
    }
}

bool MockEvsHal::addMockDisplayDevice(int id) {
    std::shared_ptr<NiceMockEvsDisplay> mockDisplay =
            ndk::SharedRefBase::make<NiceMockEvsDisplay>();

    ON_CALL(*mockDisplay, getDisplayInfo).WillByDefault([id](DisplayDesc* out) {
        DisplayDesc desc = {
                .width = 1920,
                .height = 1080,
                .orientation = Rotation::ROTATION_0,
                .id = "MockDisplay" + std::to_string(id),
                .vendorFlags = id,  // For the testing purpose, we put a display id in the vendor
                                    // flag field.
        };
        *out = std::move(desc);
        return ndk::ScopedAStatus::ok();
    });

    ON_CALL(*mockDisplay, getDisplayState).WillByDefault([this](DisplayState* out) {
        *out = mCurrentDisplayState;
        return ndk::ScopedAStatus::ok();
    });

    ON_CALL(*mockDisplay, getTargetBuffer).WillByDefault([](BufferDesc* out) {
        (void)out;
        return ndk::ScopedAStatus::ok();
    });

    ON_CALL(*mockDisplay, returnTargetBufferForDisplay).WillByDefault([](const BufferDesc& in) {
        (void)in;
        return ndk::ScopedAStatus::ok();
    });

    ON_CALL(*mockDisplay, setDisplayState).WillByDefault([this](DisplayState in) {
        mCurrentDisplayState = in;
        return ndk::ScopedAStatus::ok();
    });

    std::lock_guard l(mLock);
    mMockEvsDisplays.push_back(std::move(mockDisplay));
    mMockDeviceStatus.insert_or_assign(std::to_string(id), DeviceStatusType::DISPLAY_AVAILABLE);

    std::vector<DeviceStatus> msg(1);
    msg[0] = {
            .id = std::to_string(id),
            .status = DeviceStatusType::DISPLAY_AVAILABLE,
    };
    for (auto callback : mDeviceStatusCallbacks) {
        callback->deviceStatusChanged(msg);
    }

    return true;
}

void MockEvsHal::removeMockDisplayDevice(int id) {
    std::lock_guard l(mLock);
    auto key = std::to_string(id);
    auto it = mMockDeviceStatus.find(key);
    if (it == mMockDeviceStatus.end()) {
        // Nothing to do.
        return;
    }

    mMockDeviceStatus[key] = DeviceStatusType::DISPLAY_NOT_AVAILABLE;

    std::vector<DeviceStatus> msg(1);
    msg[0] = {
            .id = key,
            .status = DeviceStatusType::DISPLAY_NOT_AVAILABLE,
    };
    for (auto callback : mDeviceStatusCallbacks) {
        callback->deviceStatusChanged(msg);
    }
}

size_t MockEvsHal::setNumberOfFramesToSend(size_t n) {
    std::lock_guard l(mLock);
    return mNumberOfFramesToSend = n;
}

void MockEvsHal::configureEnumerator() {
    std::shared_ptr<NiceMockEvsEnumerator> mockEnumerator =
            ndk::SharedRefBase::make<NiceMockEvsEnumerator>();

    ON_CALL(*mockEnumerator, closeCamera)
            .WillByDefault([this](const std::shared_ptr<IEvsCamera>& c) {
                CameraDesc desc;
                if (!c->getCameraInfo(&desc).isOk()) {
                    // Safely ignore a request to close a camera if we fail to read a
                    // camera descriptor.
                    return ndk::ScopedAStatus::ok();
                }

                std::lock_guard l(mLock);
                auto it = mCameraBufferPoolSize.find(desc.id);
                if (it == mCameraBufferPoolSize.end()) {
                    // Safely ignore a request if we fail to find a corresponding mock
                    // camera.
                    return ndk::ScopedAStatus::ok();
                }

                mBufferPoolSize -= it->second;
                if (mBufferPoolSize < 0) {
                    LOG(WARNING) << "mBuffeRPoolSize should not have a negative value, "
                                 << mBufferPoolSize;
                    mBufferPoolSize = 0;
                }
                mCameraBufferPoolSize.insert_or_assign(desc.id, 0);
                return ndk::ScopedAStatus::ok();
            });

    ON_CALL(*mockEnumerator, closeDisplay)
            .WillByDefault([this]([[maybe_unused]] const std::shared_ptr<IEvsDisplay>& displayObj) {
                auto pActiveDisplay = mActiveDisplay.lock();
                if (!pActiveDisplay) {
                    return ndk::ScopedAStatus::fromServiceSpecificError(
                            static_cast<int>(EvsResult::OWNERSHIP_LOST));
                }

                // Nothing else to do.

                return ndk::ScopedAStatus::ok();
            });

    ON_CALL(*mockEnumerator, closeUltrasonicsArray)
            .WillByDefault([](const std::shared_ptr<IEvsUltrasonicsArray>&) {
                // Mock EVS HAL does not support IEvsUltrasonicsArray.
                return ndk::ScopedAStatus::ok();
            });

    ON_CALL(*mockEnumerator, getCameraList).WillByDefault([this](std::vector<CameraDesc>* out) {
        out->resize(mMockEvsCameras.size());

        for (auto i = 0; i < mMockEvsCameras.size(); ++i) {
            CameraDesc desc;
            if (!mMockEvsCameras[i]->getCameraInfo(&desc).isOk()) {
                LOG(ERROR) << "Failed to retrieve a camera desc";
                continue;
            }

            // Inserts a camera record if it does not exist.
            if (mCameraList.find(desc.id) == mCameraList.end()) {
                mCameraList.insert_or_assign(desc.id, desc);
            }

            (*out)[i] = std::move(desc);
        }

        return ndk::ScopedAStatus::ok();
    });

    ON_CALL(*mockEnumerator, getDisplayIdList).WillByDefault([this](std::vector<uint8_t>* out) {
        out->resize(mMockEvsDisplays.size());

        for (auto i = 0; i < mMockEvsDisplays.size(); ++i) {
            DisplayDesc desc;
            if (!mMockEvsDisplays[i]->getDisplayInfo(&desc).isOk()) {
                continue;
            }

            // MockEvsDisplay contains a display ID in its vendor flags.
            (*out)[i] = static_cast<uint8_t>(desc.vendorFlags);
        }

        return ndk::ScopedAStatus::ok();
    });

    ON_CALL(*mockEnumerator, getDisplayState).WillByDefault([this](DisplayState* out) {
        *out = mCurrentDisplayState;
        return ndk::ScopedAStatus::ok();
    });

    ON_CALL(*mockEnumerator, getStreamList)
            .WillByDefault([](const CameraDesc& desc, std::vector<Stream>* out) {
                if (desc.metadata.empty()) {
                    return ndk::ScopedAStatus::ok();
                }

                camera_metadata_t* p = const_cast<camera_metadata_t*>(
                        reinterpret_cast<const camera_metadata_t*>(desc.metadata.data()));
                camera_metadata_entry_t entry;
                if (find_camera_metadata_entry(p, ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS,
                                               &entry)) {
                    return ndk::ScopedAStatus::ok();
                }

                const auto n = calculate_camera_metadata_entry_data_size(
                        get_camera_metadata_tag_type(
                                ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS),
                        entry.count);
                out->resize(n);

                for (auto i = 0; i < n; ++i) {
                    // ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS is a set of 5
                    // int32_t words.
                    Stream s = {
                            .id = i,
                            .streamType = entry.data.i32[3] ==
                                            ANDROID_SCALER_AVAILABLE_STREAM_CONFIGURATIONS
                                    ? StreamType::OUTPUT
                                    : StreamType::INPUT,
                            .width = entry.data.i32[1],
                            .height = entry.data.i32[2],
                            .format = static_cast<PixelFormat>(entry.data.i32[0]),
                            .usage = BufferUsage::CAMERA_INPUT,
                            .rotation = Rotation::ROTATION_0,
                    };

                    (*out)[i] = s;
                }

                return ndk::ScopedAStatus::ok();
            });

    ON_CALL(*mockEnumerator, getUltrasonicsArrayList)
            .WillByDefault([](std::vector<aidlevs::UltrasonicsArrayDesc>*) {
                // Mock EVS HAL does not support IEvsUltrasonicsArray yet.
                return ndk::ScopedAStatus::ok();
            });

    ON_CALL(*mockEnumerator, isHardware).WillByDefault([](bool* flag) {
        *flag = false;
        return ndk::ScopedAStatus::ok();
    });

    ON_CALL(*mockEnumerator, openCamera)
            .WillByDefault([this](const std::string& id, [[maybe_unused]] const Stream& config,
                                  std::shared_ptr<IEvsCamera>* out) {
                auto it = std::find_if(mMockEvsCameras.begin(), mMockEvsCameras.end(),
                                       [id](const std::shared_ptr<NiceMockEvsCamera>& c) {
                                           CameraDesc desc;
                                           return c->getCameraInfo(&desc).isOk() && desc.id == id;
                                       });

                if (it == mMockEvsCameras.end()) {
                    return ndk::ScopedAStatus::fromServiceSpecificError(
                            static_cast<int>(EvsResult::INVALID_ARG));
                }

                auto instance = mCameraList.find(id);  // Guaranteed to exist always.
                instance->second.activeInstance = *it;
                *out = IEvsCamera::fromBinder((*it)->asBinder());
                return ndk::ScopedAStatus::ok();
            });

    ON_CALL(*mockEnumerator, openDisplay)
            .WillByDefault([this](int32_t id, std::shared_ptr<IEvsDisplay>* out) {
                if (id == kExclusiveDisplayId) {
                    if (mDisplayOwnedExclusively && !mActiveDisplay.expired()) {
                        return ndk::ScopedAStatus::fromServiceSpecificError(
                                static_cast<int>(EvsResult::RESOURCE_BUSY));
                    }

                    DisplayDesc desc;
                    if (!mMockEvsDisplays[0]->getDisplayInfo(&desc).isOk()) {
                        return ndk::ScopedAStatus::fromServiceSpecificError(
                                static_cast<int>(EvsResult::UNDERLYING_SERVICE_ERROR));
                    }
                    id = desc.vendorFlags;  // the first display in the list is
                                            // the main display.
                    mDisplayOwnedExclusively = true;
                }

                auto it = std::find_if(mMockEvsDisplays.begin(), mMockEvsDisplays.end(),
                                       [id](const std::shared_ptr<NiceMockEvsDisplay>& d) {
                                           DisplayDesc desc;
                                           return d->getDisplayInfo(&desc).isOk() &&
                                                   desc.vendorFlags == id;
                                       });

                if (it == mMockEvsDisplays.end()) {
                    return ndk::ScopedAStatus::fromServiceSpecificError(
                            static_cast<int>(EvsResult::INVALID_ARG));
                }

                mActiveDisplay = *it;
                mCurrentDisplayState = DisplayState::NOT_VISIBLE;
                *out = IEvsDisplay::fromBinder((*it)->asBinder());
                return ndk::ScopedAStatus::ok();
            });

    ON_CALL(*mockEnumerator, openUltrasonicsArray)
            .WillByDefault([](const std::string&, std::shared_ptr<IEvsUltrasonicsArray>*) {
                // Mock EVS HAL does not support IEvsUltrasonicsArray yet.
                return ndk::ScopedAStatus::ok();
            });

    ON_CALL(*mockEnumerator, registerStatusCallback)
            .WillByDefault([this](const std::shared_ptr<IEvsEnumeratorStatusCallback>& cb) {
                if (!cb) {
                    return ndk::ScopedAStatus::ok();
                }

                std::lock_guard l(mLock);
                mDeviceStatusCallbacks.insert(cb);
                return ndk::ScopedAStatus::ok();
            });

    mMockEvsEnumerator = std::move(mockEnumerator);
}

}  // namespace aidl::android::automotive::evs::implementation
