/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef ANDROID_HARDWARE_CONTEXTHUB_V1_2_CONTEXTHUB_H
#define ANDROID_HARDWARE_CONTEXTHUB_V1_2_CONTEXTHUB_H

#include "generic_context_hub_base.h"

#include <condition_variable>
#include <functional>
#include <mutex>
#include <optional>

#include <android/hardware/contexthub/1.2/IContexthub.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>

#include "chre_host/fragmented_load_transaction.h"
#include "chre_host/host_protocol_host.h"
#include "chre_host/socket_client.h"

namespace android {
namespace hardware {
namespace contexthub {
namespace V1_2 {
namespace implementation {

using ::android::hardware::contexthub::common::implementation::
    GenericContextHubBase;
using ::android::hardware::contexthub::V1_0::Result;

class GenericContextHubV1_2 : public GenericContextHubBase<V1_2::IContexthub> {
 public:
  Return<void> getHubs_1_2(V1_2::IContexthub::getHubs_1_2_cb _hidl_cb) override;

  Return<Result> registerCallback_1_2(
      uint32_t hubId, const sp<IContexthubCallback> &cb) override;

  Return<void> onSettingChanged(V1_1::Setting setting,
                                V1_1::SettingValue newValue) override;

  Return<void> onSettingChanged_1_2(V1_2::Setting setting,
                                    V1_1::SettingValue newValue) override;
};

}  // namespace implementation
}  // namespace V1_2
}  // namespace contexthub
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_CONTEXTHUB_V1_2_CONTEXTHUB_H
