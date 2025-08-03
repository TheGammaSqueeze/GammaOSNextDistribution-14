// Copyright 2022, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

#include <cstdint>

#include "include/hardware/bluetooth.h"
#include "include/hardware/bt_common_types.h"
#include "include/hardware/bt_gatt_client.h"
#include "include/hardware/bt_gatt_server.h"
#include "rust/cxx.h"

namespace bluetooth {
namespace gatt {

/// The GATT entity backing the value of a user-controlled
/// attribute
enum class AttributeBackingType {
  /// A GATT characteristic
  CHARACTERISTIC,
  /// A GATT descriptor
  DESCRIPTOR,
};

class GattServerCallbacks {
 public:
  GattServerCallbacks(const btgatt_server_callbacks_t& callbacks)
      : callbacks(callbacks){};

  void OnServerRead(uint16_t conn_id, uint32_t trans_id, uint16_t attr_handle,
                    AttributeBackingType attr_type, uint32_t offset,
                    bool is_long) const;

  void OnServerWrite(uint16_t conn_id, uint32_t trans_id, uint16_t attr_handle,
                     AttributeBackingType attr_type, uint32_t offset,
                     bool need_response, bool is_prepare,
                     ::rust::Slice<const uint8_t> value) const;

  void OnIndicationSentConfirmation(uint16_t conn_id, int status) const;

  void OnExecute(uint16_t conn_id, uint32_t trans_id, bool execute) const;

 private:
  const btgatt_server_callbacks_t& callbacks;
};

}  // namespace gatt
}  // namespace bluetooth
