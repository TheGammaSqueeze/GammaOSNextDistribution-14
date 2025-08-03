/*
 * Copyright 2020 HIMSA II K/S - www.himsa.com.
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
#pragma once

#include <gmock/gmock.h>

#include "bind_helpers.h"
#include "stack/gatt/gatt_int.h"
#include "types/raw_address.h"

namespace bluetooth {
namespace gatt {

class GattInterface {
 public:
  virtual void ClientInitServerStatus(tGATT_TCB& tcb) = 0;
  virtual bool ClientReadSupportedFeatures(
      const RawAddress& peer_bda,
      base::OnceCallback<void(const RawAddress&, uint8_t)> cb) = 0;
  virtual bool GetEattSupport(const RawAddress& peer_bda) = 0;
  virtual ~GattInterface() = default;
};

class MockGattInterface : public GattInterface {
 public:
  MOCK_METHOD1(ClientInitServerStatus, void(tGATT_TCB& tcb));
  MOCK_METHOD2(ClientReadSupportedFeatures,
               bool(const RawAddress& peer_bda,
                    base::OnceCallback<void(const RawAddress&, uint8_t)> cb));
  MOCK_METHOD1(GetEattSupport, bool(const RawAddress& peer_bda));
};

/**
 * Set the {@link MockGattInterface} for testing
 *
 * @param mock_gatt_interface pointer to mock gatt interface, could be null
 */
void SetMockGattInterface(MockGattInterface* mock_gatt_interface);

}  // namespace gatt
}  // namespace bluetooth
