/*
 * Copyright 2023 The Android Open Source Project
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

#include "bta/dm/bta_dm_gatt_client.h"

#include <base/strings/stringprintf.h>

#include <cstdint>
#include <string>
#include <vector>

#include "bta/include/bta_gatt_api.h"
#include "common/strings.h"
#include "main/shim/dumpsys.h"
#include "stack/btm/btm_int_types.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

namespace {
TimestampedStringCircularBuffer gatt_history_{50};
constexpr char kTimeFormatString[] = "%Y-%m-%d %H:%M:%S";

constexpr unsigned MillisPerSecond = 1000;
std::string EpochMillisToString(long long time_ms) {
  time_t time_sec = time_ms / MillisPerSecond;
  struct tm tm;
  localtime_r(&time_sec, &tm);
  std::string s = bluetooth::common::StringFormatTime(kTimeFormatString, tm);
  return base::StringPrintf(
      "%s.%03u", s.c_str(),
      static_cast<unsigned int>(time_ms % MillisPerSecond));
}
}  // namespace

gatt_interface_t default_gatt_interface = {
    .BTA_GATTC_CancelOpen =
        [](tGATT_IF client_if, const RawAddress& remote_bda, bool is_direct) {
          gatt_history_.Push(base::StringPrintf(
              "%-32s bd_addr:%s client_if:%hu is_direct:%c", "GATTC_CancelOpen",
              ADDRESS_TO_LOGGABLE_CSTR(remote_bda), client_if,
              (is_direct) ? 'T' : 'F'));
          BTA_GATTC_CancelOpen(client_if, remote_bda, is_direct);
        },
    .BTA_GATTC_Refresh =
        [](const RawAddress& remote_bda) {
          gatt_history_.Push(
              base::StringPrintf("%-32s bd_addr:%s", "GATTC_Refresh",
                                 ADDRESS_TO_LOGGABLE_CSTR(remote_bda)));
          BTA_GATTC_Refresh(remote_bda);
        },
    .BTA_GATTC_GetGattDb =
        [](uint16_t conn_id, uint16_t start_handle, uint16_t end_handle,
           btgatt_db_element_t** db, int* count) {
          gatt_history_.Push(base::StringPrintf(
              "%-32s conn_id:%hu start_handle:%hu end:handle:%hu",
              "GATTC_GetGattDb", conn_id, start_handle, end_handle));
          BTA_GATTC_GetGattDb(conn_id, start_handle, end_handle, db, count);
        },
    .BTA_GATTC_AppRegister =
        [](tBTA_GATTC_CBACK* p_client_cb, BtaAppRegisterCallback cb,
           bool eatt_support) {
          gatt_history_.Push(base::StringPrintf("%-32s eatt_support:%c",
                                                "GATTC_AppRegister",
                                                (eatt_support) ? 'T' : 'F'));
          BTA_GATTC_AppRegister(p_client_cb, cb, eatt_support);
        },
    .BTA_GATTC_Close =
        [](uint16_t conn_id) {
          gatt_history_.Push(
              base::StringPrintf("%-32s conn_id:%hu", "GATTC_Close", conn_id));
          BTA_GATTC_Close(conn_id);
        },
    .BTA_GATTC_ServiceSearchRequest =
        [](uint16_t conn_id, const bluetooth::Uuid* p_srvc_uuid) {
          gatt_history_.Push(base::StringPrintf(
              "%-32s conn_id:%hu", "GATTC_ServiceSearchRequest", conn_id));
          BTA_GATTC_ServiceSearchRequest(conn_id, p_srvc_uuid);
        },
    .BTA_GATTC_Open =
        [](tGATT_IF client_if, const RawAddress& remote_bda,
           tBTM_BLE_CONN_TYPE connection_type, bool opportunistic) {
          gatt_history_.Push(base::StringPrintf(
              "%-32s bd_addr:%s client_if:%hu type:0x%x opportunistic:%c",
              "GATTC_Open", ADDRESS_TO_LOGGABLE_CSTR(remote_bda), client_if,
              connection_type, (opportunistic) ? 'T' : 'F'));
          BTA_GATTC_Open(client_if, remote_bda, connection_type, opportunistic);
        },
};

gatt_interface_t* gatt_interface = &default_gatt_interface;

gatt_interface_t& get_gatt_interface() { return *gatt_interface; }

void gatt_history_callback(const std::string& entry) {
  gatt_history_.Push(entry);
}

#define DUMPSYS_TAG "shim::legacy::bta::dm"
void DumpsysBtaDmGattClient(int fd) {
  auto gatt_history = gatt_history_.Pull();
  LOG_DUMPSYS(fd, " last %zu gatt history entries", gatt_history.size());
  for (const auto& it : gatt_history) {
    LOG_DUMPSYS(fd, "   %s %s", EpochMillisToString(it.timestamp).c_str(),
                it.entry.c_str());
  }
}
#undef DUMPSYS_TAG

void bluetooth::testing::set_gatt_interface(const gatt_interface_t& interface) {
  *gatt_interface = interface;
}

namespace bluetooth {
namespace legacy {
namespace testing {

std::vector<bluetooth::common::TimestampedEntry<std::string>>
PullCopyOfGattHistory() {
  return gatt_history_.Pull();
}

}  // namespace testing
}  // namespace legacy
}  // namespace bluetooth
