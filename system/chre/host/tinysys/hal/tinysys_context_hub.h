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

#ifndef ANDROID_HARDWARE_CONTEXTHUB_AIDL_CONTEXTHUB_H
#define ANDROID_HARDWARE_CONTEXTHUB_AIDL_CONTEXTHUB_H

#include "chre_host/preloaded_nanoapp_loader.h"
#include "chre_host/time_syncer.h"
#include "multi_client_context_hub_base.h"
#include "tinysys_chre_connection.h"

namespace aidl::android::hardware::contexthub {

using namespace ::android::hardware::contexthub::common::implementation;
using namespace ::android::chre;

/** The implementation of HAL for Tinysys. */
class TinysysContextHub : public MultiClientContextHubBase {
 public:
  TinysysContextHub();

 protected:
  void onChreRestarted() override;
  bool isMetricEnabled() override {
    return false;
  }
  const std::string kPreloadedNanoappsConfigPath =
      "/vendor/etc/chre/preloaded_nanoapps.json";
  const std::string kClientIdMappingFilePath =
      "/data/vendor/chre/chre_hal_clients.json";
};
}  // namespace aidl::android::hardware::contexthub
#endif  // ANDROID_HARDWARE_CONTEXTHUB_AIDL_CONTEXTHUB_H
