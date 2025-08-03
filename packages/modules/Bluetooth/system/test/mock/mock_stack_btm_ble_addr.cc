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
 *   Functions generated:10
 *
 *  mockcify.pl ver 0.2
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_stack_btm_ble_addr.h"

// Original included files, if any

#include "test/common/mock_functions.h"
#include "types/ble_address_with_type.h"
#include "types/raw_address.h"

// Mocked compile conditionals, if any
// Mocked internal structures, if any

namespace test {
namespace mock {
namespace stack_btm_ble_addr {

// Function state capture and return values, if needed
struct btm_gen_resolve_paddr_low btm_gen_resolve_paddr_low;
struct btm_gen_resolvable_private_addr btm_gen_resolvable_private_addr;
struct btm_ble_init_pseudo_addr btm_ble_init_pseudo_addr;
struct btm_ble_addr_resolvable btm_ble_addr_resolvable;
struct btm_ble_resolve_random_addr btm_ble_resolve_random_addr;
struct btm_identity_addr_to_random_pseudo btm_identity_addr_to_random_pseudo;
struct btm_identity_addr_to_random_pseudo_from_address_with_type
    btm_identity_addr_to_random_pseudo_from_address_with_type;
struct btm_random_pseudo_to_identity_addr btm_random_pseudo_to_identity_addr;
struct btm_ble_refresh_peer_resolvable_private_addr
    btm_ble_refresh_peer_resolvable_private_addr;

}  // namespace stack_btm_ble_addr
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void btm_gen_resolve_paddr_low(const RawAddress& address) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble_addr::btm_gen_resolve_paddr_low(address);
}
void btm_gen_resolvable_private_addr(
    base::Callback<void(const RawAddress&)> cb) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble_addr::btm_gen_resolvable_private_addr(cb);
}
bool btm_ble_init_pseudo_addr(tBTM_SEC_DEV_REC* p_dev_rec,
                              const RawAddress& new_pseudo_addr) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble_addr::btm_ble_init_pseudo_addr(
      p_dev_rec, new_pseudo_addr);
}
bool btm_ble_addr_resolvable(const RawAddress& rpa,
                             tBTM_SEC_DEV_REC* p_dev_rec) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble_addr::btm_ble_addr_resolvable(rpa,
                                                                 p_dev_rec);
}
tBTM_SEC_DEV_REC* btm_ble_resolve_random_addr(const RawAddress& random_bda) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble_addr::btm_ble_resolve_random_addr(
      random_bda);
}
bool btm_identity_addr_to_random_pseudo(RawAddress* bd_addr,
                                        tBLE_ADDR_TYPE* p_addr_type,
                                        bool refresh) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble_addr::btm_identity_addr_to_random_pseudo(
      bd_addr, p_addr_type, refresh);
}
bool btm_identity_addr_to_random_pseudo_from_address_with_type(
    tBLE_BD_ADDR* address_with_type, bool refresh) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble_addr::
      btm_identity_addr_to_random_pseudo_from_address_with_type(
          address_with_type, refresh);
}
bool btm_random_pseudo_to_identity_addr(RawAddress* random_pseudo,
                                        tBLE_ADDR_TYPE* p_identity_addr_type) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble_addr::btm_random_pseudo_to_identity_addr(
      random_pseudo, p_identity_addr_type);
}
void btm_ble_refresh_peer_resolvable_private_addr(
    const RawAddress& pseudo_bda, const RawAddress& rpa,
    tBLE_RAND_ADDR_TYPE rra_type) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble_addr::btm_ble_refresh_peer_resolvable_private_addr(
      pseudo_bda, rpa, rra_type);
}

// END mockcify generation
