/******************************************************************************
 *
 *  Copyright 2023 NXP
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
#pragma once

#include <aidl/android/hardware/weaver/BnWeaver.h>
#include <weaver-impl.h>
#include <weaver_interface.h>

namespace aidl {
namespace android {
namespace hardware {
namespace weaver {

using ::aidl::android::hardware::weaver::WeaverConfig;
using ::aidl::android::hardware::weaver::WeaverReadResponse;
using ::ndk::ScopedAStatus;
using std::vector;

struct Weaver : public BnWeaver {
public:
  Weaver();
  // Methods from ::android::hardware::weaver::IWeaver follow.
  ScopedAStatus getConfig(WeaverConfig *_aidl_return) override;
  ScopedAStatus read(int32_t in_slotId, const vector<uint8_t> &in_key,
                     WeaverReadResponse *_aidl_return) override;
  ScopedAStatus write(int32_t in_slotId, const vector<uint8_t> &in_key,
                      const vector<uint8_t> &in_value) override;

private:
  WeaverInterface *pInterface = nullptr;
};

} // namespace weaver
} // namespace hardware
} // namespace android
} // namespace aidl
