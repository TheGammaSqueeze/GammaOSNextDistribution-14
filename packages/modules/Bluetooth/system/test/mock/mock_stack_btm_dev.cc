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

#include "test/mock/mock_stack_btm_dev.h"

#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>

#include <string>

#include "btm_api.h"
#include "stack/btm/btm_dev.h"
#include "stack/include/bt_octets.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

namespace test {
namespace mock {
namespace stack_btm_dev {

struct btm_find_dev btm_find_dev;
struct BTM_Sec_AddressKnown BTM_Sec_AddressKnown;

struct maybe_resolve_address maybe_resolve_address;
}
}  // namespace mock
}  // namespace test

bool BTM_SecAddDevice(const RawAddress& /* bd_addr */,
                      const DEV_CLASS /* dev_class */,
                      const BD_NAME& /* bd_name */, uint8_t* /* features */,
                      LinkKey* /* p_link_key */, uint8_t /* key_type */,
                      uint8_t /* pin_length */) {
  inc_func_call_count(__func__);
  return false;
}
bool BTM_SecDeleteDevice(const RawAddress& /* bd_addr */) {
  inc_func_call_count(__func__);
  return false;
}
bool btm_dev_support_role_switch(const RawAddress& /* bd_addr */) {
  inc_func_call_count(__func__);
  return false;
}
bool btm_set_bond_type_dev(const RawAddress& /* bd_addr */,
                           tBTM_BOND_TYPE /* bond_type */) {
  inc_func_call_count(__func__);
  return false;
}
const char* BTM_SecReadDevName(const RawAddress& /* bd_addr */) {
  inc_func_call_count(__func__);
  return nullptr;
}
tBTM_SEC_DEV_REC* btm_find_dev(const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_dev::btm_find_dev.body(bd_addr);
}
tBTM_SEC_DEV_REC* btm_find_dev_by_handle(uint16_t /* handle */) {
  inc_func_call_count(__func__);
  return nullptr;
}
tBTM_SEC_DEV_REC* btm_find_or_alloc_dev(const RawAddress& /* bd_addr */) {
  inc_func_call_count(__func__);
  return nullptr;
}
tBTM_SEC_DEV_REC* btm_sec_alloc_dev(const RawAddress& /* bd_addr */) {
  inc_func_call_count(__func__);
  return nullptr;
}
tBTM_SEC_DEV_REC* btm_sec_allocate_dev_rec(void) {
  inc_func_call_count(__func__);
  return nullptr;
}
tBTM_BOND_TYPE btm_get_bond_type_dev(const RawAddress& /* bd_addr */) {
  inc_func_call_count(__func__);
  return BOND_TYPE_UNKNOWN;
}
void BTM_SecClearSecurityFlags(const RawAddress& /* bd_addr */) {
  inc_func_call_count(__func__);
}
void btm_consolidate_dev(tBTM_SEC_DEV_REC* /* p_target_rec */) {
  inc_func_call_count(__func__);
}
void btm_dev_consolidate_existing_connections(const RawAddress& /* bd_addr */) {
  inc_func_call_count(__func__);
}
void BTM_SecDump(const std::string& /* label */) {
  inc_func_call_count(__func__);
}
void BTM_SecDumpDev(const RawAddress& /* bd_addr */) {
  inc_func_call_count(__func__);
}
std::vector<tBTM_SEC_DEV_REC*> btm_get_sec_dev_rec() {
  inc_func_call_count(__func__);
  return {};
}

void BTM_SetConsolidationCallback(BTM_CONSOLIDATION_CB* /* cb */) {
  inc_func_call_count(__func__);
}

bool BTM_Sec_AddressKnown(const RawAddress& address) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_dev::BTM_Sec_AddressKnown(address);
}

bool maybe_resolve_address(RawAddress* bda, tBLE_ADDR_TYPE* bda_type) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_dev::maybe_resolve_address(bda, bda_type);
}
const tBLE_BD_ADDR BTM_Sec_GetAddressWithType(const RawAddress& /* bd_addr */) {
  return {};
}
