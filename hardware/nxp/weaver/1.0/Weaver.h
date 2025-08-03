/******************************************************************************
 *
 *  Copyright 2020 NXP
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/
#ifndef ANDROID_HARDWARE_WEAVER_V1_0_WEAVER_H
#define ANDROID_HARDWARE_WEAVER_V1_0_WEAVER_H

#include <android/hardware/weaver/1.0/IWeaver.h>
#include <android/hardware/weaver/1.0/types.h>
#include <hardware/hardware.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <android-base/stringprintf.h>

namespace android {
namespace hardware {
namespace weaver {
namespace V1_0 {
namespace implementation {

using android::hardware::weaver::V1_0::IWeaver;
using ::android::hidl::base::V1_0::IBase;
using ::android::hardware::hidl_array;
using ::android::hardware::hidl_memory;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;
using android::base::StringPrintf;

struct Weaver : public IWeaver, public hidl_death_recipient {
  Weaver();
  Return<void>
    getConfig(getConfig_cb _hidl_cb) override;

  Return<::android::hardware::weaver::V1_0::WeaverStatus>
   write(uint32_t slotId, const hidl_vec<uint8_t>& key, const hidl_vec<uint8_t>& value) override;
  Return<void>
  read(uint32_t slotId, const hidl_vec<uint8_t>& key, read_cb _hidl_cb) override;

  void serviceDied(uint64_t /*cookie*/, const wp<IBase>& /*who*/);
};

}  // namespace implementation
}  // namespace V1_0
}  // namespace weaver
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_WEAVER_V1_0_WEAVER_H
