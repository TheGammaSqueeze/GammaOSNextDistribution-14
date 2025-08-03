/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <android-base/result.h>

#include "bpf/BpfMap.h"
#include "netd.h"

namespace android {
namespace net {

class DnsBpfHelper {
 public:
  DnsBpfHelper() = default;
  DnsBpfHelper(const DnsBpfHelper&) = delete;
  DnsBpfHelper& operator=(const DnsBpfHelper&) = delete;

  base::Result<void> init();
  base::Result<bool> isUidNetworkingBlocked(uid_t uid, bool metered);

 private:
  android::bpf::BpfMapRO<uint32_t, uint32_t> mConfigurationMap;
  android::bpf::BpfMapRO<uint32_t, UidOwnerValue> mUidOwnerMap;
  android::bpf::BpfMapRO<uint32_t, bool> mDataSaverEnabledMap;

  // For testing
  friend class DnsBpfHelperTest;
};

}  // namespace net
}  // namespace android
