/*
 * Copyright 2022 The Android Open Source Project
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

#include "bta/include/bta_api.h"
#include "btif/include/core_callbacks.h"
#include "types/raw_address.h"

void InitializeCoreInterface();
void CleanCoreInterface();

struct MockCoreInterface : bluetooth::core::CoreInterface {
  MockCoreInterface();

  void onBluetoothEnabled() override;
  bt_status_t toggleProfile(tBTA_SERVICE_ID service_id, bool enable) override;
  void removeDeviceFromProfiles(const RawAddress& bd_addr) override;
  void onLinkDown(const RawAddress& bd_addr) override;
};
