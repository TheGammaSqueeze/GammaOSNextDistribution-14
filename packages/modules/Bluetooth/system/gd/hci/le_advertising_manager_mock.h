/*
 * Copyright 2019 The Android Open Source Project
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

#include "hci/le_advertising_manager.h"

#include <gmock/gmock.h>

// Unit test interfaces
namespace bluetooth {
namespace hci {

struct LeAdvertisingManager::impl : public bluetooth::hci::LeAddressManagerCallback {};

namespace testing {

using hci::AdvertiserId;
using hci::LeAdvertisingManager;

class MockLeAdvertisingManager : public LeAdvertisingManager {
 public:
  MOCK_METHOD(size_t, GetNumberOfAdvertisingInstances, (), (const));
  MOCK_METHOD(
      AdvertiserId,
      ExtendedCreateAdvertiser,
      (uint8_t client_id,
       int regId,
       const AdvertisingConfig,
       const common::Callback<void(Address, AddressType)>&,
       const common::Callback<void(ErrorCode, uint8_t, uint8_t)>&,
       uint16_t,
       uint8_t,
       os::Handler*));
  MOCK_METHOD(void, RemoveAdvertiser, (AdvertiserId));

};

}  // namespace testing
}  // namespace hci
}  // namespace bluetooth
