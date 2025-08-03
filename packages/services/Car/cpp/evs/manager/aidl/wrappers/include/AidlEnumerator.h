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

#ifndef CPP_EVS_MANAGER_AIDL_WRAPPERS_INCLUDE_AIDLENUMERATOR_H
#define CPP_EVS_MANAGER_AIDL_WRAPPERS_INCLUDE_AIDLENUMERATOR_H

#include <aidl/android/hardware/automotive/evs/BnEvsEnumerator.h>
#include <aidl/android/hardware/automotive/evs/IEvsCamera.h>
#include <aidl/android/hardware/automotive/evs/IEvsDisplay.h>
#include <aidl/android/hardware/automotive/evs/IEvsEnumeratorStatusCallback.h>
#include <android/hardware/automotive/evs/1.1/IEvsDisplay.h>
#include <android/hardware/automotive/evs/1.1/IEvsEnumerator.h>
#include <system/camera_metadata.h>

namespace aidl::android::automotive::evs::implementation {

namespace aidlevs = ::aidl::android::hardware::automotive::evs;
namespace hidlevs = ::android::hardware::automotive::evs;

class AidlEnumerator final : public ::aidl::android::hardware::automotive::evs::BnEvsEnumerator {
public:
    // Methods from ::aidl::android::hardware::automotive::evs::IEvsEnumerator
    ::ndk::ScopedAStatus isHardware(bool* flag) override;
    ::ndk::ScopedAStatus openCamera(const std::string& cameraId,
                                    const aidlevs::Stream& streamConfig,
                                    std::shared_ptr<aidlevs::IEvsCamera>* obj) override;
    ::ndk::ScopedAStatus closeCamera(const std::shared_ptr<aidlevs::IEvsCamera>& obj) override;
    ::ndk::ScopedAStatus getCameraList(std::vector<aidlevs::CameraDesc>* _aidl_return) override;
    ::ndk::ScopedAStatus getStreamList(const aidlevs::CameraDesc& desc,
                                       std::vector<aidlevs::Stream>* _aidl_return) override;
    ::ndk::ScopedAStatus openDisplay(int32_t displayId,
                                     std::shared_ptr<aidlevs::IEvsDisplay>* obj) override;
    ::ndk::ScopedAStatus closeDisplay(const std::shared_ptr<aidlevs::IEvsDisplay>& obj) override;
    ::ndk::ScopedAStatus getDisplayIdList(std::vector<uint8_t>* list) override;
    ::ndk::ScopedAStatus getDisplayState(aidlevs::DisplayState* state) override;
    ::ndk::ScopedAStatus getDisplayStateById(int32_t displayId,
                                             aidlevs::DisplayState* state) override;
    ::ndk::ScopedAStatus openUltrasonicsArray(
            const std::string& id, std::shared_ptr<aidlevs::IEvsUltrasonicsArray>* obj) override;
    ::ndk::ScopedAStatus closeUltrasonicsArray(
            const std::shared_ptr<aidlevs::IEvsUltrasonicsArray>& obj) override;
    ::ndk::ScopedAStatus getUltrasonicsArrayList(
            std::vector<aidlevs::UltrasonicsArrayDesc>* list) override;
    ::ndk::ScopedAStatus registerStatusCallback(
            const std::shared_ptr<aidlevs::IEvsEnumeratorStatusCallback>& callback) override;

    explicit AidlEnumerator(const ::android::sp<hidlevs::V1_0::IEvsEnumerator>& svc,
                            bool forceV1_0 = false);
    virtual ~AidlEnumerator() { mImpl = nullptr; }

    // Implementation details
    bool init(const char* hardwareServiceName);

private:
    class IHidlEnumerator;
    class ImplV0;
    class ImplV1;

    std::shared_ptr<IHidlEnumerator> mImpl;
    ::android::wp<hidlevs::V1_0::IEvsDisplay> mHidlDisplay;
    std::weak_ptr<aidlevs::IEvsDisplay> mAidlDisplay;
};

class AidlEnumerator::IHidlEnumerator {
public:
    virtual ::ndk::ScopedAStatus closeCamera(
            const ::android::sp<hidlevs::V1_0::IEvsCamera>& cameraObj) = 0;
    virtual ::ndk::ScopedAStatus closeDisplay(
            const ::android::sp<hidlevs::V1_0::IEvsDisplay>& display) = 0;
    virtual ::ndk::ScopedAStatus getCameraList(std::vector<aidlevs::CameraDesc>* _aidl_return) = 0;
    virtual ::ndk::ScopedAStatus getDisplayIdList(std::vector<uint8_t>* list) = 0;
    virtual ::ndk::ScopedAStatus openCamera(const std::string& cameraId,
                                            const aidlevs::Stream& streamConfig,
                                            std::shared_ptr<aidlevs::IEvsCamera>* obj) = 0;
    virtual ::android::sp<hidlevs::V1_0::IEvsDisplay> openDisplay(int32_t displayId) = 0;

    explicit IHidlEnumerator(const ::android::sp<hidlevs::V1_0::IEvsEnumerator>& svc) :
          mHidlEnumerator(svc) {}
    virtual ~IHidlEnumerator() { mHidlEnumerator = nullptr; }

protected:
    ::android::sp<hidlevs::V1_0::IEvsEnumerator> mHidlEnumerator;
    ::android::wp<hidlevs::V1_0::IEvsDisplay> mActiveHidlDisplay;
};

class AidlEnumerator::ImplV0 final : public IHidlEnumerator {
public:
    virtual ::ndk::ScopedAStatus closeCamera(
            const ::android::sp<hidlevs::V1_0::IEvsCamera>& cameraObj) override;
    virtual ::ndk::ScopedAStatus closeDisplay(
            const ::android::sp<hidlevs::V1_0::IEvsDisplay>& display) override;
    virtual ::ndk::ScopedAStatus getCameraList(
            std::vector<aidlevs::CameraDesc>* _aidl_return) override;
    virtual ::ndk::ScopedAStatus getDisplayIdList(std::vector<uint8_t>* list) override;
    virtual ::ndk::ScopedAStatus openCamera(const std::string& cameraId,
                                            const aidlevs::Stream& streamConfig,
                                            std::shared_ptr<aidlevs::IEvsCamera>* obj) override;
    virtual ::android::sp<hidlevs::V1_0::IEvsDisplay> openDisplay(int32_t displayId) override;

    explicit ImplV0(const ::android::sp<hidlevs::V1_0::IEvsEnumerator>& svc) :
          IHidlEnumerator(svc) {}
};

class AidlEnumerator::ImplV1 final : public IHidlEnumerator {
public:
    virtual ::ndk::ScopedAStatus closeCamera(
            const ::android::sp<hidlevs::V1_0::IEvsCamera>& cameraObj) override;
    virtual ::ndk::ScopedAStatus closeDisplay(
            const ::android::sp<hidlevs::V1_0::IEvsDisplay>& display) override;
    virtual ::ndk::ScopedAStatus getCameraList(
            std::vector<aidlevs::CameraDesc>* _aidl_return) override;
    virtual ::ndk::ScopedAStatus getDisplayIdList(std::vector<uint8_t>* list) override;
    virtual ::ndk::ScopedAStatus openCamera(const std::string& cameraId,
                                            const aidlevs::Stream& streamConfig,
                                            std::shared_ptr<aidlevs::IEvsCamera>* obj) override;
    virtual ::android::sp<hidlevs::V1_0::IEvsDisplay> openDisplay(int32_t displayId) override;

    explicit ImplV1(const ::android::sp<hidlevs::V1_1::IEvsEnumerator>& svc) :
          IHidlEnumerator(svc), mHidlEnumerator(svc) {}
    virtual ~ImplV1() { mHidlEnumerator = nullptr; }

private:
    ::android::sp<hidlevs::V1_1::IEvsEnumerator> mHidlEnumerator;
};

}  // namespace aidl::android::automotive::evs::implementation

#endif  // CPP_EVS_MANAGER_AIDL_WRAPPERS_INCLUDE_AIDLENUMERATOR_H
