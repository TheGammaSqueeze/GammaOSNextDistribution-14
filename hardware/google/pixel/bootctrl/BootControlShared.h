/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <android/hardware/boot/1.2/IBootControl.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>

namespace android {
namespace hardware {
namespace boot {
namespace V1_2 {
namespace implementation {

using ::android::hardware::Return;
using ::android::hardware::boot::V1_1::MergeStatus;

struct BootControlShared : public IBootControl {
    BootControlShared();

    bool Init();

    Return<bool> setSnapshotMergeStatus(MergeStatus status) override;
    Return<MergeStatus> getSnapshotMergeStatus() override;
};

extern "C" IBootControl *HIDL_FETCH_IBootControl(const char *name);

}  // namespace implementation
}  // namespace V1_2
}  // namespace boot
}  // namespace hardware
}  // namespace android
