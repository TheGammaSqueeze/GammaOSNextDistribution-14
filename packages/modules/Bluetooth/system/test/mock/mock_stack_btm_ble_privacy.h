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

#include <cstdint>
#include <functional>

// Original included files, if any
#include "stack/btm/security_device_record.h"

// Mocked compile conditionals, if any
namespace test {
namespace mock {
namespace stack_btm_ble_privacy {

// Shared state between mocked functions and tests
// Name: btm_ble_clear_resolving_list_complete
// Params: uint8_t* p, uint16_t evt_len
// Returns: void
struct btm_ble_clear_resolving_list_complete {
  std::function<void(uint8_t* p, uint16_t evt_len)> body{
      [](uint8_t* /* p */, uint16_t /* evt_len */) {}};
  void operator()(uint8_t* p, uint16_t evt_len) { body(p, evt_len); };
};
extern struct btm_ble_clear_resolving_list_complete
    btm_ble_clear_resolving_list_complete;
// Name: btm_ble_add_resolving_list_entry_complete
// Params: uint8_t* p, uint16_t evt_len
// Returns: void
struct btm_ble_add_resolving_list_entry_complete {
  std::function<void(uint8_t* p, uint16_t evt_len)> body{
      [](uint8_t* /* p */, uint16_t /* evt_len */) {}};
  void operator()(uint8_t* p, uint16_t evt_len) { body(p, evt_len); };
};
extern struct btm_ble_add_resolving_list_entry_complete
    btm_ble_add_resolving_list_entry_complete;
// Name: btm_ble_remove_resolving_list_entry_complete
// Params: uint8_t* p, uint16_t evt_len
// Returns: void
struct btm_ble_remove_resolving_list_entry_complete {
  std::function<void(uint8_t* p, uint16_t evt_len)> body{
      [](uint8_t* /* p */, uint16_t /* evt_len */) {}};
  void operator()(uint8_t* p, uint16_t evt_len) { body(p, evt_len); };
};
extern struct btm_ble_remove_resolving_list_entry_complete
    btm_ble_remove_resolving_list_entry_complete;
// Name: btm_ble_read_resolving_list_entry_complete
// Params: uint8_t* p, uint16_t evt_len
// Returns: void
struct btm_ble_read_resolving_list_entry_complete {
  std::function<void(const uint8_t* p, uint16_t evt_len)> body{
      [](const uint8_t* /* p */, uint16_t /* evt_len */) {}};
  void operator()(const uint8_t* p, uint16_t evt_len) { body(p, evt_len); };
};
extern struct btm_ble_read_resolving_list_entry_complete
    btm_ble_read_resolving_list_entry_complete;
// Name: btm_ble_remove_resolving_list_entry
// Params: tBTM_SEC_DEV_REC* p_dev_rec
// Returns: tBTM_STATUS
struct btm_ble_remove_resolving_list_entry {
  std::function<tBTM_STATUS(tBTM_SEC_DEV_REC* p_dev_rec)> body{
      [](tBTM_SEC_DEV_REC* /* p_dev_rec */) { return 0; }};
  tBTM_STATUS operator()(tBTM_SEC_DEV_REC* p_dev_rec) {
    return body(p_dev_rec);
  };
};
extern struct btm_ble_remove_resolving_list_entry
    btm_ble_remove_resolving_list_entry;
// Name: btm_ble_clear_resolving_list
// Params: void
// Returns: void
struct btm_ble_clear_resolving_list {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btm_ble_clear_resolving_list btm_ble_clear_resolving_list;
// Name: btm_ble_read_resolving_list_entry
// Params: tBTM_SEC_DEV_REC* p_dev_rec
// Returns: bool
struct btm_ble_read_resolving_list_entry {
  std::function<bool(tBTM_SEC_DEV_REC* p_dev_rec)> body{
      [](tBTM_SEC_DEV_REC* /* p_dev_rec */) { return false; }};
  bool operator()(tBTM_SEC_DEV_REC* p_dev_rec) { return body(p_dev_rec); };
};
extern struct btm_ble_read_resolving_list_entry
    btm_ble_read_resolving_list_entry;
// Name: btm_ble_resolving_list_load_dev
// Params: tBTM_SEC_DEV_REC* p_dev_rec
// Returns: void
struct btm_ble_resolving_list_load_dev {
  std::function<void(const tBTM_SEC_DEV_REC& p_dev_rec)> body{
      [](const tBTM_SEC_DEV_REC& /* p_dev_rec */) {}};
  void operator()(const tBTM_SEC_DEV_REC& p_dev_rec) { body(p_dev_rec); };
};
extern struct btm_ble_resolving_list_load_dev btm_ble_resolving_list_load_dev;
// Name: btm_ble_resolving_list_remove_dev
// Params: tBTM_SEC_DEV_REC* p_dev_rec
// Returns: void
struct btm_ble_resolving_list_remove_dev {
  std::function<void(tBTM_SEC_DEV_REC* p_dev_rec)> body{
      [](tBTM_SEC_DEV_REC* /* p_dev_rec */) {}};
  void operator()(tBTM_SEC_DEV_REC* p_dev_rec) { body(p_dev_rec); };
};
extern struct btm_ble_resolving_list_remove_dev
    btm_ble_resolving_list_remove_dev;
// Name: btm_ble_enable_resolving_list_for_platform
// Params: uint8_t rl_mask
// Returns: void
struct btm_ble_enable_resolving_list_for_platform {
  std::function<void(uint8_t rl_mask)> body{[](uint8_t /* rl_mask */) {}};
  void operator()(uint8_t rl_mask) { body(rl_mask); };
};
extern struct btm_ble_enable_resolving_list_for_platform
    btm_ble_enable_resolving_list_for_platform;
// Name: btm_ble_resolving_list_init
// Params: uint8_t max_irk_list_sz
// Returns: void
struct btm_ble_resolving_list_init {
  std::function<void(uint8_t max_irk_list_sz)> body{
      [](uint8_t /* max_irk_list_sz */) {}};
  void operator()(uint8_t max_irk_list_sz) { body(max_irk_list_sz); };
};
extern struct btm_ble_resolving_list_init btm_ble_resolving_list_init;

}  // namespace stack_btm_ble_privacy
}  // namespace mock
}  // namespace test

// END mockcify generation
