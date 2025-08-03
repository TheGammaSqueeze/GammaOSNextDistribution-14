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

#ifndef CPP_EVS_MANAGER_AIDL_WRAPPERS_INCLUDE_HIDLCAMERASTREAM_H
#define CPP_EVS_MANAGER_AIDL_WRAPPERS_INCLUDE_HIDLCAMERASTREAM_H

#include <aidl/android/hardware/automotive/evs/IEvsCameraStream.h>
#include <android/hardware/automotive/evs/1.1/IEvsCameraStream.h>
#include <android/hardware/automotive/evs/1.1/types.h>

#include <list>

namespace aidl::android::automotive::evs::implementation {

namespace aidlevs = ::aidl::android::hardware::automotive::evs;
namespace hidlevs = ::android::hardware::automotive::evs;

class HidlCameraStream final : public ::android::hardware::automotive::evs::V1_1::IEvsCameraStream {
public:
    // Methods from ::android::hardware::automotive::evs::V1_0::IEvsCameraStream follow.
    ::android::hardware::Return<void> deliverFrame(
            const hidlevs::V1_0::BufferDesc& buffer) override;

    // Methods from ::android::hardware::automotive::evs::V1_1::IEvsCameraStream follow.
    ::android::hardware::Return<void> deliverFrame_1_1(
            const ::android::hardware::hidl_vec<hidlevs::V1_1::BufferDesc>& buffers) override;
    ::android::hardware::Return<void> notify(const hidlevs::V1_1::EvsEventDesc& event) override;

    HidlCameraStream(const std::string& id,
                     const std::shared_ptr<aidlevs::IEvsCameraStream>& camera) :
          mSourceDeviceId(id), mAidlStream(camera) {}

    bool getHidlBuffer(int id, hidlevs::V1_0::BufferDesc* _return);
    bool getHidlBuffer(int id, hidlevs::V1_1::BufferDesc* _return);

private:
    std::string mSourceDeviceId;
    std::shared_ptr<aidlevs::IEvsCameraStream> mAidlStream;
    std::list<hidlevs::V1_0::BufferDesc> mHidlV0Buffers;
    std::list<hidlevs::V1_1::BufferDesc> mHidlV1Buffers;
};

}  // namespace aidl::android::automotive::evs::implementation

#endif  // CPP_EVS_MANAGER_AIDL_WRAPPERS_INCLUDE_HIDLCAMERASTREAM_H
