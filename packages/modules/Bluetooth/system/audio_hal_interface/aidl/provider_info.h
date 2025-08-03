/*
 * Copyright 2024 The Android Open Source Project
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

#include <unordered_map>
#include <vector>

#include "audio_aidl_interfaces.h"
#include "bta/ag/bta_ag_int.h"

namespace bluetooth::audio::aidl {

using ::aidl::android::hardware::bluetooth::audio::CodecId;
using ::aidl::android::hardware::bluetooth::audio::CodecInfo;
using ::aidl::android::hardware::bluetooth::audio::SessionType;

class ProviderInfo {
 public:
  static std::unique_ptr<ProviderInfo> GetProviderInfo(SessionType sessionType);

  ProviderInfo(SessionType sessionType, std::vector<CodecInfo> codecs);

  ~ProviderInfo() = default;

  const std::unordered_map<int, ::hfp::sco_config>& GetHfpScoConfig();

 private:
  const std::vector<CodecInfo> codecInfos;
  std::unordered_map<int /* HFP CODEC in UUID_CODEC_XXX */, ::hfp::sco_config>
      hfpScoConfigMap;
};
}  // namespace bluetooth::audio::aidl
