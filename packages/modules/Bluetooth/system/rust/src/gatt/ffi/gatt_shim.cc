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

#include "gatt_shim.h"

#include <base/functional/bind.h>
#include <base/location.h>

#include <cstdint>
#include <optional>

#include "include/hardware/bluetooth.h"
#include "include/hardware/bt_common_types.h"
#include "include/hardware/bt_gatt_client.h"
#include "include/hardware/bt_gatt_server.h"
#include "os/log.h"
#include "rust/cxx.h"
#include "stack/include/gatt_api.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

bt_status_t do_in_jni_thread(const base::Location& from_here,
                             base::OnceClosure task);

namespace {
std::optional<RawAddress> AddressOfConnection(uint16_t conn_id) {
  tGATT_IF gatt_if;
  RawAddress remote_bda;
  tBT_TRANSPORT transport;
  auto valid =
      GATT_GetConnectionInfor(conn_id, &gatt_if, remote_bda, &transport);
  if (!valid) {
    return std::nullopt;
  }
  return remote_bda;
}
}  // namespace

namespace bluetooth {
namespace gatt {

void GattServerCallbacks::OnServerRead(uint16_t conn_id, uint32_t trans_id,
                                       uint16_t attr_handle,
                                       AttributeBackingType attr_type,
                                       uint32_t offset, bool is_long) const {
  auto addr = AddressOfConnection(conn_id);
  if (!addr.has_value()) {
    LOG_WARN(
        "Dropping server read characteristic since connection %d not found",
        conn_id);
    return;
  }

  switch (attr_type) {
    case AttributeBackingType::CHARACTERISTIC:
      do_in_jni_thread(
          FROM_HERE,
          base::BindOnce(callbacks.request_read_characteristic_cb, conn_id,
                         trans_id, addr.value(), attr_handle, offset, is_long));
      break;
    case AttributeBackingType::DESCRIPTOR:
      do_in_jni_thread(
          FROM_HERE,
          base::BindOnce(callbacks.request_read_descriptor_cb, conn_id,
                         trans_id, addr.value(), attr_handle, offset, is_long));
      break;
    default:
      LOG_ALWAYS_FATAL("Unexpected backing type %d", attr_type);
  }
}

void GattServerCallbacks::OnServerWrite(
    uint16_t conn_id, uint32_t trans_id, uint16_t attr_handle,
    AttributeBackingType attr_type, uint32_t offset, bool need_response,
    bool is_prepare, ::rust::Slice<const uint8_t> value) const {
  auto addr = AddressOfConnection(conn_id);
  if (!addr.has_value()) {
    LOG_WARN(
        "Dropping server write characteristic since connection %d not found",
        conn_id);
    return;
  }

  auto buf = new uint8_t[value.size()];
  std::copy(value.begin(), value.end(), buf);

  switch (attr_type) {
    case AttributeBackingType::CHARACTERISTIC:
      do_in_jni_thread(
          FROM_HERE,
          base::BindOnce(callbacks.request_write_characteristic_cb, conn_id,
                         trans_id, addr.value(), attr_handle, offset,
                         need_response, is_prepare, base::Owned(buf),
                         value.size()));
      break;
    case AttributeBackingType::DESCRIPTOR:
      do_in_jni_thread(
          FROM_HERE,
          base::BindOnce(callbacks.request_write_descriptor_cb, conn_id,
                         trans_id, addr.value(), attr_handle, offset,
                         need_response, is_prepare, base::Owned(buf),
                         value.size()));
      break;
    default:
      LOG_ALWAYS_FATAL("Unexpected backing type %hhu", attr_type);
  }
}

void GattServerCallbacks::OnIndicationSentConfirmation(uint16_t conn_id,
                                                       int status) const {
  do_in_jni_thread(
      FROM_HERE, base::BindOnce(callbacks.indication_sent_cb, conn_id, status));
}

void GattServerCallbacks::OnExecute(uint16_t conn_id, uint32_t trans_id,
                                    bool execute) const {
  auto addr = AddressOfConnection(conn_id);
  if (!addr.has_value()) {
    LOG_WARN("Dropping server execute write since connection %d not found",
             conn_id);
    return;
  }

  do_in_jni_thread(FROM_HERE,
                   base::BindOnce(callbacks.request_exec_write_cb, conn_id,
                                  trans_id, addr.value(), execute));
}

}  // namespace gatt
}  // namespace bluetooth
