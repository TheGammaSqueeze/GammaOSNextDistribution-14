/*
 * Copyright 2021 The Android Open Source Project
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

/*
 * Generated mock file from original source file
 *   Functions generated:16
 */

#include <set>

#include "stack/btm/btm_ble_bgconn.h"
#include "stack/gatt/connection_manager.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

using namespace connection_manager;

bool connection_manager::background_connect_add(
    uint8_t /* app_id */, const RawAddress& /* address */) {
  inc_func_call_count(__func__);
  return false;
}
bool connection_manager::background_connect_remove(
    uint8_t /* app_id */, const RawAddress& /* address */) {
  inc_func_call_count(__func__);
  return false;
}
bool connection_manager::direct_connect_add(uint8_t /* app_id */,
                                            const RawAddress& /* address */) {
  inc_func_call_count(__func__);
  return false;
}
bool connection_manager::direct_connect_remove(uint8_t /* app_id */,
                                               const RawAddress& /* address */,
                                               bool /* connection_timeout */) {
  inc_func_call_count(__func__);
  return false;
}
bool connection_manager::remove_unconditional(const RawAddress& /* address */) {
  inc_func_call_count(__func__);
  return false;
}
std::set<tAPP_ID> connection_manager::get_apps_connecting_to(
    const RawAddress& /* address */) {
  inc_func_call_count(__func__);
  return std::set<tAPP_ID>();
}
void connection_manager::dump(int /* fd */) { inc_func_call_count(__func__); }
void connection_manager::on_app_deregistered(uint8_t /* app_id */) {
  inc_func_call_count(__func__);
}
void connection_manager::on_connection_complete(
    const RawAddress& /* address */) {
  inc_func_call_count(__func__);
}

void connection_manager::on_connection_timed_out_from_shim(
    const RawAddress& /* address */) {
  inc_func_call_count(__func__);
}

void connection_manager::reset(bool /* after_reset */) {
  inc_func_call_count(__func__);
}

bool connection_manager::is_background_connection(
    const RawAddress& /* address */) {
  inc_func_call_count(__func__);
  return false;
}
