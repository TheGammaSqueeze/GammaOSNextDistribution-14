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
 *   Functions generated:13
 *
 *  mockcify.pl ver 0.2
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_stack_btm_ble_privacy.h"

// Original included files, if any

#include "test/common/mock_functions.h"

// Mocked compile conditionals, if any
// Mocked internal structures, if any

namespace test {
namespace mock {
namespace stack_btm_ble_privacy {

// Function state capture and return values, if needed
struct btm_ble_clear_resolving_list_complete
    btm_ble_clear_resolving_list_complete;
struct btm_ble_add_resolving_list_entry_complete
    btm_ble_add_resolving_list_entry_complete;
struct btm_ble_remove_resolving_list_entry_complete
    btm_ble_remove_resolving_list_entry_complete;
struct btm_ble_read_resolving_list_entry_complete
    btm_ble_read_resolving_list_entry_complete;
struct btm_ble_remove_resolving_list_entry btm_ble_remove_resolving_list_entry;
struct btm_ble_clear_resolving_list btm_ble_clear_resolving_list;
struct btm_ble_read_resolving_list_entry btm_ble_read_resolving_list_entry;
struct btm_ble_resolving_list_load_dev btm_ble_resolving_list_load_dev;
struct btm_ble_resolving_list_remove_dev btm_ble_resolving_list_remove_dev;
struct btm_ble_resolving_list_init btm_ble_resolving_list_init;

}  // namespace stack_btm_ble_privacy
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void btm_ble_clear_resolving_list_complete(uint8_t* p, uint16_t evt_len) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble_privacy::btm_ble_clear_resolving_list_complete(
      p, evt_len);
}
void btm_ble_add_resolving_list_entry_complete(uint8_t* p, uint16_t evt_len) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble_privacy::btm_ble_add_resolving_list_entry_complete(
      p, evt_len);
}
void btm_ble_remove_resolving_list_entry_complete(uint8_t* p,
                                                  uint16_t evt_len) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble_privacy::
      btm_ble_remove_resolving_list_entry_complete(p, evt_len);
}
void btm_ble_read_resolving_list_entry_complete(const uint8_t* p,
                                                uint16_t evt_len) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble_privacy::btm_ble_read_resolving_list_entry_complete(
      p, evt_len);
}
tBTM_STATUS btm_ble_remove_resolving_list_entry(tBTM_SEC_DEV_REC* p_dev_rec) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble_privacy::btm_ble_remove_resolving_list_entry(
      p_dev_rec);
}
void btm_ble_clear_resolving_list(void) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble_privacy::btm_ble_clear_resolving_list();
}
bool btm_ble_read_resolving_list_entry(tBTM_SEC_DEV_REC* p_dev_rec) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_ble_privacy::btm_ble_read_resolving_list_entry(
      p_dev_rec);
}
void btm_ble_resolving_list_load_dev(tBTM_SEC_DEV_REC& /* p_dev_rec */) {
  inc_func_call_count(__func__);
}
void btm_ble_resolving_list_remove_dev(tBTM_SEC_DEV_REC* p_dev_rec) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble_privacy::btm_ble_resolving_list_remove_dev(
      p_dev_rec);
}
void btm_ble_resolving_list_init(uint8_t max_irk_list_sz) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_ble_privacy::btm_ble_resolving_list_init(
      max_irk_list_sz);
}

// END mockcify generation
