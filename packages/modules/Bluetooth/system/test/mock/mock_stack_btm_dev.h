/*
 * Copyright 2022 The Android Open Source Project
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

#include <functional>

#include "stack/btm/btm_dev.h"
#include "types/raw_address.h"

namespace test {
namespace mock {
namespace stack_btm_dev {

// Function state capture and return values, if needed
struct btm_find_dev {
  std::function<tBTM_SEC_DEV_REC*(const RawAddress& bd_addr)> body{
      [](const RawAddress&) { return nullptr; }};
  tBTM_SEC_DEV_REC* operator()(const RawAddress& bd_addr) {
    return body(bd_addr);
  };
};
extern struct btm_find_dev btm_find_dev;

struct BTM_Sec_AddressKnown {
  std::function<bool(const RawAddress& address)> body{
      [](const RawAddress& /* address */) { return false; }};
  bool operator()(const RawAddress& address) { return body(address); };
};
extern struct BTM_Sec_AddressKnown BTM_Sec_AddressKnown;

// Name: maybe_resolve_address
// Params: RawAddress* bda, tBLE_ADDR_TYPE* bda_type
// Returns: bool
struct maybe_resolve_address {
  std::function<bool(RawAddress* bda, tBLE_ADDR_TYPE* bda_type)> body{
      [](RawAddress* /* bda */, tBLE_ADDR_TYPE* /* bda_type */) {
        return false;
      }};
  bool operator()(RawAddress* bda, tBLE_ADDR_TYPE* bda_type) {
    return body(bda, bda_type);
  };
};
extern struct maybe_resolve_address maybe_resolve_address;

}  // namespace stack_btm_dev
}  // namespace mock
}  // namespace test
