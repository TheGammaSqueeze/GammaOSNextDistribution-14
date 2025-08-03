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
#ifndef CPP_EVS_MANAGER_1_1_IENUMERATORMANAGER_H_
#define CPP_EVS_MANAGER_1_1_IENUMERATORMANAGER_H_

#include <android/hardware/automotive/evs/1.1/IEvsEnumerator.h>
#include <android/hardware/automotive/evs/1.1/IEvsUltrasonicsArray.h>
#include <android/hardware/camera/device/3.2/ICameraDevice.h>

#include <cstdint>
#include <memory>
#include <string_view>
#include <vector>

namespace android::automotive::evs::V1_1::implementation {

class IEnumeratorManager {
public:
    virtual ~IEnumeratorManager() = default;

    virtual std::vector<::android::hardware::automotive::evs::V1_0::CameraDesc> getCameraList() = 0;
    virtual std::unique_ptr<::android::hardware::automotive::evs::V1_0::IEvsCamera> openCamera(
            std::string_view cameraId) = 0;
    virtual void closeCamera(
            const ::android::hardware::automotive::evs::V1_0::IEvsCamera& camera) = 0;

    virtual std::unique_ptr<::android::hardware::automotive::evs::V1_0::IEvsDisplay>
    openDisplay() = 0;
    virtual void closeDisplay(::android::hardware::automotive::evs::V1_0::IEvsDisplay* display) = 0;

    virtual ::android::hardware::automotive::evs::V1_0::DisplayState getDisplayState() = 0;
    virtual std::vector<::android::hardware::automotive::evs::V1_1::CameraDesc>
    getCameraList_1_1() = 0;

    virtual std::unique_ptr<::android::hardware::automotive::evs::V1_1::IEvsCamera> openCamera_1_1(
            std::string cameraId, const hardware::camera::device::V3_2::Stream& streamCfg) = 0;
    virtual bool isHardware() = 0;
    virtual std::vector<std::uint8_t> getDisplayIdList() = 0;
    virtual std::unique_ptr<::android::hardware::automotive::evs::V1_1::IEvsDisplay>
    openDisplay_1_1(std::uint8_t id) = 0;

    virtual std::vector<::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc>
    getUltrasonicsArrayList() = 0;
    virtual std::unique_ptr<::android::hardware::automotive::evs::V1_1::IEvsUltrasonicsArray>
    openUltrasonicsArray(std::string id) = 0;
    virtual void closeUltrasonicsArray(
            const ::android::hardware::automotive::evs::V1_1::IEvsUltrasonicsArray&
                    ultrasonicsArray) = 0;

    virtual std::string debug(::android::hardware::hidl_handle fileDescriptor,
                              std::vector<std::string> options) = 0;
};

}  // namespace android::automotive::evs::V1_1::implementation

#endif  // CPP_EVS_MANAGER_1_1_IENUMERATORMANAGER_H_
