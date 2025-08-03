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

#pragma once

#include "MockHidlEvsCamera.h"
#include "MockHidlEvsDisplay.h"
#include "MockHidlEvsEnumerator_1_0.h"

#include <android-base/thread_annotations.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <vndk/hardware_buffer.h>

#include <thread>
#include <unordered_map>
#include <unordered_set>

namespace aidl::android::automotive::evs::implementation {

namespace hidlevs = ::android::hardware::automotive::evs;

class MockHidlEvsHal_1_0 {
public:
    MockHidlEvsHal_1_0(size_t numCameras, size_t numDisplays) :
          mNumCameras(numCameras), mNumDisplays(numDisplays) {}
    ~MockHidlEvsHal_1_0();

    void initialize();
    ::android::sp<hidlevs::V1_0::IEvsEnumerator> getEnumerator();

    bool addMockCameraDevice(const std::string& deviceId);
    void removeMockCameraDevice(const std::string& deviceId);
    bool addMockDisplayDevice(int id);
    void removeMockDisplayDevice(int id);
    size_t setNumberOfFramesToSend(size_t n);

private:
    void configureCameras(size_t n);
    void configureDisplays(size_t n);
    void configureEnumerator();
    void forwardFrames(size_t numberOfFramesToForward, const std::string& deviceId);
    size_t initializeBufferPool(size_t size);
    void deinitializeBufferPoolLocked() REQUIRES(mLock);

    ::android::sp<NiceMockHidlEvsEnumerator_1_0> mMockHidlEvsEnumerator;
    std::vector<::android::sp<NiceMockHidlEvsCamera>> mMockHidlEvsCameras;
    std::vector<::android::sp<NiceMockHidlEvsDisplay>> mMockHidlEvsDisplays;
    std::unordered_map<std::string, ::android::sp<hidlevs::V1_0::IEvsCameraStream>> mCameraClient;

    struct CameraRecord {
        hidlevs::V1_0::CameraDesc desc;
        ::android::wp<hidlevs::V1_0::IEvsCamera> activeInstance;

        CameraRecord(hidlevs::V1_0::CameraDesc& desc) : desc(desc) {}
    };

    mutable std::mutex mLock;
    std::condition_variable mBufferAvailableSignal;

    std::map<std::string, CameraRecord> mCameraList;
    std::map<int32_t, ::android::hardware::hidl_vec<uint8_t>> mCameraExtendedInfo;
    std::vector<hidlevs::V1_0::BufferDesc> mBufferPool GUARDED_BY(mLock);
    std::vector<hidlevs::V1_0::BufferDesc> mBuffersInUse GUARDED_BY(mLock);
    std::unordered_map<size_t, AHardwareBuffer*> mBufferRecord GUARDED_BY(mLock);
    ::android::wp<hidlevs::V1_0::IEvsDisplay> mActiveDisplay;

    hidlevs::V1_0::DisplayState mCurrentDisplayState = hidlevs::V1_0::DisplayState::NOT_OPEN;

    size_t mNumCameras = 0;
    size_t mNumDisplays = 0;
    size_t mBufferPoolSize = 0;
    size_t mNumberOfFramesToSend = 5;

    enum class StreamState { kStopped, kRunning, kStopping };
    std::unordered_map<std::string, std::atomic<StreamState>> mStreamState GUARDED_BY(mLock);
    std::unordered_map<std::string, bool> mMockDeviceStatus GUARDED_BY(mLock);
    std::unordered_map<std::string, size_t> mCameraBufferPoolSize GUARDED_BY(mLock);
    std::unordered_map<std::string, std::thread> mCameraFrameThread GUARDED_BY(mLock);
};

}  // namespace aidl::android::automotive::evs::implementation
