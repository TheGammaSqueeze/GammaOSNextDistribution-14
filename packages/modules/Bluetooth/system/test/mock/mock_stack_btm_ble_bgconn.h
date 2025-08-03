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
 *   Functions generated:11
 *
 *  mockcify.pl ver 0.2
 */

#include <functional>

// Original included files, if any
#include <base/functional/bind.h>

#include "stack/include/btm_ble_api_types.h"
#include "types/raw_address.h"

// Mocked compile conditionals, if any
namespace test {
namespace mock {
namespace stack_btm_ble_bgconn {

// Name: btm_update_scanner_filter_policy
// Params: tBTM_BLE_SFP scan_policy
// Returns: void
struct btm_update_scanner_filter_policy {
  std::function<void(tBTM_BLE_SFP scan_policy)> body{
      [](tBTM_BLE_SFP /* scan_policy */) {}};
  void operator()(tBTM_BLE_SFP scan_policy) { body(scan_policy); };
};
extern struct btm_update_scanner_filter_policy btm_update_scanner_filter_policy;
// Name: BTM_SetLeConnectionModeToFast
// Params:
// Returns: bool
struct BTM_SetLeConnectionModeToFast {
  std::function<bool()> body{[]() { return false; }};
  bool operator()() { return body(); };
};
extern struct BTM_SetLeConnectionModeToFast BTM_SetLeConnectionModeToFast;
// Name: BTM_SetLeConnectionModeToSlow
// Params:
// Returns: void
struct BTM_SetLeConnectionModeToSlow {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct BTM_SetLeConnectionModeToSlow BTM_SetLeConnectionModeToSlow;
// Name: BTM_AcceptlistAdd
// Params: const RawAddress& address
// Returns: bool
struct BTM_AcceptlistAdd {
  std::function<bool(const RawAddress& address)> body{
      [](const RawAddress& /* address */) { return false; }};
  bool operator()(const RawAddress& address) { return body(address); };
};
extern struct BTM_AcceptlistAdd BTM_AcceptlistAdd;
// Name: BTM_AcceptlistAddDirect
// Params: const RawAddress& address, bool is_direct
// Returns: bool
struct BTM_AcceptlistAddDirect {
  std::function<bool(const RawAddress& address, bool is_direct)> body{
      [](const RawAddress& /* address */, bool /* is_direct */) {
        return false;
      }};
  bool operator()(const RawAddress& address, bool is_direct) {
    return body(address, is_direct);
  };
};
extern struct BTM_AcceptlistAddDirect BTM_AcceptlistAddDirect;
// Name: BTM_AcceptlistRemove
// Params: const RawAddress& address
// Returns: void
struct BTM_AcceptlistRemove {
  std::function<void(const RawAddress& address)> body{
      [](const RawAddress& /* address */) {}};
  void operator()(const RawAddress& address) { body(address); };
};
extern struct BTM_AcceptlistRemove BTM_AcceptlistRemove;
// Name: BTM_AcceptlistClear
// Params:
// Returns: void
struct BTM_AcceptlistClear {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct BTM_AcceptlistClear BTM_AcceptlistClear;

}  // namespace stack_btm_ble_bgconn
}  // namespace mock
}  // namespace test

// END mockcify generation
