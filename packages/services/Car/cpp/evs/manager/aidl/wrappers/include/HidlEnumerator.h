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

#ifndef CPP_EVS_MANAGER_AIDL_WRAPPERS_INCLUDE_HIDLENUMERATOR_H
#define CPP_EVS_MANAGER_AIDL_WRAPPERS_INCLUDE_HIDLENUMERATOR_H

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

class HidlEnumerator final : public hidlevs::V1_1::IEvsEnumerator {
public:
    // Methods from ::android::hardware::automotive::evs::V1_0::IEvsEnumerator follow.
    ::android::hardware::Return<void> getCameraList(getCameraList_cb _hidl_cb) override;
    ::android::hardware::Return<::android::sp<hidlevs::V1_0::IEvsCamera>> openCamera(
            const ::android::hardware::hidl_string& cameraId) override;
    ::android::hardware::Return<void> closeCamera(
            const ::android::sp<hidlevs::V1_0::IEvsCamera>& virtualCamera) override;
    ::android::hardware::Return<::android::sp<hidlevs::V1_0::IEvsDisplay>> openDisplay() override;
    ::android::hardware::Return<void> closeDisplay(
            const ::android::sp<hidlevs::V1_0::IEvsDisplay>& display) override;
    ::android::hardware::Return<hidlevs::V1_0::DisplayState> getDisplayState() override;

    // Methods from hardware::automotive::evs::V1_1::IEvsEnumerator follow.
    ::android::hardware::Return<void> getCameraList_1_1(getCameraList_1_1_cb _hidl_cb) override;
    ::android::hardware::Return<::android::sp<hidlevs::V1_1::IEvsCamera>> openCamera_1_1(
            const ::android::hardware::hidl_string& cameraId,
            const ::android::hardware::camera::device::V3_2::Stream& streamCfg) override;
    ::android::hardware::Return<bool> isHardware() override { return false; }
    ::android::hardware::Return<void> getDisplayIdList(getDisplayIdList_cb _list_cb) override;
    ::android::hardware::Return<::android::sp<hidlevs::V1_1::IEvsDisplay>> openDisplay_1_1(
            uint8_t id) override;
    ::android::hardware::Return<void> getUltrasonicsArrayList(
            getUltrasonicsArrayList_cb _hidl_cb) override;
    ::android::hardware::Return<::android::sp<hidlevs::V1_1::IEvsUltrasonicsArray>>
    openUltrasonicsArray(const ::android::hardware::hidl_string& ultrasonicsArrayId) override;
    ::android::hardware::Return<void> closeUltrasonicsArray(
            const ::android::sp<hidlevs::V1_1::IEvsUltrasonicsArray>& evsUltrasonicsArray) override;

    explicit HidlEnumerator(const std::shared_ptr<aidlevs::IEvsEnumerator>& service) :
          mEnumerator(service) {}
    virtual ~HidlEnumerator();

    // Implementation details
    bool init(const char* hardwareServiceName);

private:
    std::shared_ptr<aidlevs::IEvsEnumerator> mEnumerator;
    std::weak_ptr<aidlevs::IEvsDisplay> mAidlDisplay;
    std::vector<uint8_t> mAidlDisplayIds;
    ::android::wp<hidlevs::V1_0::IEvsDisplay> mHidlDisplay;
};

}  // namespace aidl::android::automotive::evs::implementation

#endif  // CPP_EVS_MANAGER_AIDL_WRAPPERS_INCLUDE_HIDLENUMERATOR_H
