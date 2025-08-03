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

#ifndef CPP_EVS_MANAGER_AIDL_WRAPPERS_INCLUDE_HIDLCAMERA_H
#define CPP_EVS_MANAGER_AIDL_WRAPPERS_INCLUDE_HIDLCAMERA_H

#include "AidlCameraStream.h"

#include <aidl/android/hardware/automotive/evs/BnEvsCamera.h>
#include <aidl/android/hardware/automotive/evs/BufferDesc.h>
#include <aidl/android/hardware/automotive/evs/CameraDesc.h>
#include <aidl/android/hardware/automotive/evs/CameraParam.h>
#include <aidl/android/hardware/automotive/evs/EvsEventDesc.h>
#include <aidl/android/hardware/automotive/evs/EvsResult.h>
#include <aidl/android/hardware/automotive/evs/IEvsCameraStream.h>
#include <aidl/android/hardware/automotive/evs/IEvsDisplay.h>
#include <aidl/android/hardware/automotive/evs/ParameterRange.h>
#include <aidl/android/hardware/automotive/evs/Stream.h>
#include <android/hardware/automotive/evs/1.1/IEvsCamera.h>

#include <unordered_map>

namespace aidl::android::automotive::evs::implementation {

namespace aidlevs = ::aidl::android::hardware::automotive::evs;
namespace hidlevs = ::android::hardware::automotive::evs;

class HidlCamera final : public hidlevs::V1_1::IEvsCamera {
public:
    // Methods from ::android::hardware::automotive::evs::V1_0::IEvsCamera follow.
    ::android::hardware::Return<void> getCameraInfo(getCameraInfo_cb _hidl_cb) override;
    ::android::hardware::Return<hidlevs::V1_0::EvsResult> setMaxFramesInFlight(
            uint32_t bufferCount) override;
    ::android::hardware::Return<hidlevs::V1_0::EvsResult> startVideoStream(
            const ::android::sp<::android::hardware::automotive::evs::V1_0::IEvsCameraStream>&
                    stream) override;
    ::android::hardware::Return<void> doneWithFrame(
            const hidlevs::V1_0::BufferDesc& buffer) override;
    ::android::hardware::Return<void> stopVideoStream() override;
    ::android::hardware::Return<int32_t> getExtendedInfo(uint32_t opaqueIdentifier) override;
    ::android::hardware::Return<hidlevs::V1_0::EvsResult> setExtendedInfo(
            uint32_t opaqueIdentifier, int32_t opaqueValue) override;

    // Methods from ::android::hardware::automotive::evs::V1_1::IEvsCamera follow.
    ::android::hardware::Return<void> getCameraInfo_1_1(getCameraInfo_1_1_cb _hidl_cb) override;
    ::android::hardware::Return<void> getPhysicalCameraInfo(
            const ::android::hardware::hidl_string& deviceId,
            getPhysicalCameraInfo_cb _hidl_cb) override;
    ::android::hardware::Return<hidlevs::V1_0::EvsResult> doneWithFrame_1_1(
            const ::android::hardware::hidl_vec<hidlevs::V1_1::BufferDesc>& buffer) override;
    ::android::hardware::Return<hidlevs::V1_0::EvsResult> pauseVideoStream() override {
        return hidlevs::V1_0::EvsResult::UNDERLYING_SERVICE_ERROR;
    }
    ::android::hardware::Return<hidlevs::V1_0::EvsResult> resumeVideoStream() override {
        return hidlevs::V1_0::EvsResult::UNDERLYING_SERVICE_ERROR;
    }
    ::android::hardware::Return<hidlevs::V1_0::EvsResult> setMaster() override;
    ::android::hardware::Return<hidlevs::V1_0::EvsResult> forceMaster(
            const ::android::sp<hidlevs::V1_0::IEvsDisplay>& display) override;
    ::android::hardware::Return<hidlevs::V1_0::EvsResult> unsetMaster() override;
    ::android::hardware::Return<void> getParameterList(getParameterList_cb _hidl_cb) override;
    ::android::hardware::Return<void> getIntParameterRange(
            hidlevs::V1_1::CameraParam id, getIntParameterRange_cb _hidl_cb) override;
    ::android::hardware::Return<void> setIntParameter(hidlevs::V1_1::CameraParam id, int32_t value,
                                                      setIntParameter_cb _hidl_cb) override;
    ::android::hardware::Return<void> getIntParameter(hidlevs::V1_1::CameraParam id,
                                                      getIntParameter_cb _hidl_cb) override;
    ::android::hardware::Return<hidlevs::V1_0::EvsResult> setExtendedInfo_1_1(
            uint32_t opaqueIdentifier,
            const ::android::hardware::hidl_vec<uint8_t>& opaqueValue) override;
    ::android::hardware::Return<void> getExtendedInfo_1_1(uint32_t opaqueIdentifier,
                                                          getExtendedInfo_1_1_cb _hidl_cb) override;
    ::android::hardware::Return<void> importExternalBuffers(
            const ::android::hardware::hidl_vec<hidlevs::V1_1::BufferDesc>& buffers,
            importExternalBuffers_cb _hidl_cb) override;

    explicit HidlCamera(const std::shared_ptr<aidlevs::IEvsCamera>& camera) : mAidlCamera(camera) {}
    virtual ~HidlCamera();

    const std::shared_ptr<aidlevs::IEvsCamera> getAidlCamera() const { return mAidlCamera; }

private:
    // The low level camera interface that backs this proxy
    std::shared_ptr<aidlevs::IEvsCamera> mAidlCamera;
    std::shared_ptr<AidlCameraStream> mAidlStream;
};

}  // namespace aidl::android::automotive::evs::implementation

#endif  // CPP_EVS_MANAGER_AIDL_WRAPPERS_INCLUDE_HIDLCAMERA_H
