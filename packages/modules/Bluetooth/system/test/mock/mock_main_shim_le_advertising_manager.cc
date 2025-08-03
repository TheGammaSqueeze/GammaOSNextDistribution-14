/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

#include "mock_main_shim_le_advertising_manager.h"

#include <base/memory/weak_ptr.h>

#include "main/shim/le_advertising_manager.h"

namespace {
MockBleAdvertisingManager* bt_le_advertiser_instance;
}  // namespace

void MockBleAdvertisingManager::Initialize() {
  if (bt_le_advertiser_instance == nullptr) {
    bt_le_advertiser_instance = new MockBleAdvertisingManager();
  }
}

void MockBleAdvertisingManager::CleanUp() {
  delete bt_le_advertiser_instance;
  bt_le_advertiser_instance = nullptr;
}

MockBleAdvertisingManager* MockBleAdvertisingManager::Get() {
  return bt_le_advertiser_instance;
}

BleAdvertiserInterface* bluetooth::shim::get_ble_advertiser_instance() {
  return static_cast<BleAdvertiserInterface*>(bt_le_advertiser_instance);
}
