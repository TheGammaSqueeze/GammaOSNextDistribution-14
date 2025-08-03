/*
 * Copyright 2020 The Android Open Source Project
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

#include <cstdint>
#include <future>

#include "hci/address.h"
#include "hci/address_with_type.h"
#include "stack/include/hci_error_code.h"

namespace bluetooth {
namespace shim {

class LinkConnectionInterface {
 public:
  virtual ~LinkConnectionInterface() {}

  virtual void CreateClassicConnection(
      const bluetooth::hci::Address& address) = 0;
  virtual void CancelClassicConnection(
      const bluetooth::hci::Address& address) = 0;
  virtual void AcceptLeConnectionFrom(
      const bluetooth::hci::AddressWithType& address_with_type, bool is_direct,
      std::promise<bool>) = 0;
  virtual void IgnoreLeConnectionFrom(
      const bluetooth::hci::AddressWithType& address_with_type) = 0;

  virtual void DisconnectClassic(uint16_t handle, tHCI_REASON reason,
                                 std::string comment) = 0;
  virtual void DisconnectLe(uint16_t handle, tHCI_REASON reason,
                            std::string comment) = 0;
};

}  // namespace shim
}  // namespace bluetooth
