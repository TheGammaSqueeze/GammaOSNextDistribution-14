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

#include <base/functional/callback.h>

#include <functional>

// Original included files, if any
#include "stack/btm/security_device_record.h"
#include "types/ble_address_with_type.h"
#include "types/raw_address.h"

// Mocked compile conditionals, if any
namespace test {
namespace mock {
namespace stack_btm_ble_addr {

// Shared state between mocked functions and tests
// Name: btm_gen_resolve_paddr_low
// Params: const RawAddress& address
// Returns: void
struct btm_gen_resolve_paddr_low {
  std::function<void(const RawAddress& address)> body{
      [](const RawAddress& /* address */) {}};
  void operator()(const RawAddress& address) { body(address); };
};
extern struct btm_gen_resolve_paddr_low btm_gen_resolve_paddr_low;
// Name: btm_gen_resolvable_private_addr
// Params:  base::Callback<void(const RawAddress&)> cb
// Returns: void
struct btm_gen_resolvable_private_addr {
  std::function<void(base::Callback<void(const RawAddress&)> cb)> body{
      [](base::Callback<void(const RawAddress&)> /* cb */) {}};
  void operator()(base::Callback<void(const RawAddress&)> cb) { body(cb); };
};
extern struct btm_gen_resolvable_private_addr btm_gen_resolvable_private_addr;

// Name: btm_ble_init_pseudo_addr
// Params: tBTM_SEC_DEV_REC* p_dev_rec, const RawAddress& new_pseudo_addr
// Returns: bool
struct btm_ble_init_pseudo_addr {
  std::function<bool(tBTM_SEC_DEV_REC* p_dev_rec,
                     const RawAddress& new_pseudo_addr)>
      body{[](tBTM_SEC_DEV_REC* /* p_dev_rec */,
              const RawAddress& /* new_pseudo_addr */) { return false; }};
  bool operator()(tBTM_SEC_DEV_REC* p_dev_rec,
                  const RawAddress& new_pseudo_addr) {
    return body(p_dev_rec, new_pseudo_addr);
  };
};
extern struct btm_ble_init_pseudo_addr btm_ble_init_pseudo_addr;
// Name: btm_ble_addr_resolvable
// Params: const RawAddress& rpa, tBTM_SEC_DEV_REC* p_dev_rec
// Returns: bool
struct btm_ble_addr_resolvable {
  std::function<bool(const RawAddress& rpa, tBTM_SEC_DEV_REC* p_dev_rec)> body{
      [](const RawAddress& /* rpa */, tBTM_SEC_DEV_REC* /* p_dev_rec */) {
        return false;
      }};
  bool operator()(const RawAddress& rpa, tBTM_SEC_DEV_REC* p_dev_rec) {
    return body(rpa, p_dev_rec);
  };
};
extern struct btm_ble_addr_resolvable btm_ble_addr_resolvable;
// Name: btm_ble_resolve_random_addr
// Params: const RawAddress& random_bda
// Returns: tBTM_SEC_DEV_REC*
struct btm_ble_resolve_random_addr {
  std::function<tBTM_SEC_DEV_REC*(const RawAddress& random_bda)> body{
      [](const RawAddress& /* random_bda */) { return nullptr; }};
  tBTM_SEC_DEV_REC* operator()(const RawAddress& random_bda) {
    return body(random_bda);
  };
};
extern struct btm_ble_resolve_random_addr btm_ble_resolve_random_addr;
// Name: btm_identity_addr_to_random_pseudo
// Params: RawAddress* bd_addr, uint8_t* p_addr_type, bool refresh
// Returns: bool
struct btm_identity_addr_to_random_pseudo {
  std::function<bool(RawAddress* bd_addr, tBLE_ADDR_TYPE* p_addr_type,
                     bool refresh)>
      body{[](RawAddress* /* bd_addr */, tBLE_ADDR_TYPE* /* p_addr_type */,
              bool /* refresh */) { return false; }};
  bool operator()(RawAddress* bd_addr, tBLE_ADDR_TYPE* p_addr_type,
                  bool refresh) {
    return body(bd_addr, p_addr_type, refresh);
  };
};
extern struct btm_identity_addr_to_random_pseudo
    btm_identity_addr_to_random_pseudo;
// Name: btm_identity_addr_to_random_pseudo_from_address_with_type
// Params:  tBLE_BD_ADDR* address_with_type, bool refresh
// Returns: bool
struct btm_identity_addr_to_random_pseudo_from_address_with_type {
  std::function<bool(tBLE_BD_ADDR* address_with_type, bool refresh)> body{
      [](tBLE_BD_ADDR* /* address_with_type */, bool /* refresh */) {
        return false;
      }};
  bool operator()(tBLE_BD_ADDR* address_with_type, bool refresh) {
    return body(address_with_type, refresh);
  };
};
extern struct btm_identity_addr_to_random_pseudo_from_address_with_type
    btm_identity_addr_to_random_pseudo_from_address_with_type;
// Name: btm_random_pseudo_to_identity_addr
// Params: RawAddress* random_pseudo, uint8_t* p_identity_addr_type
// Returns: bool
struct btm_random_pseudo_to_identity_addr {
  std::function<bool(RawAddress* random_pseudo,
                     tBLE_ADDR_TYPE* p_identity_addr_type)>
      body{[](RawAddress* /* random_pseudo */,
              tBLE_ADDR_TYPE* /* p_identity_addr_type */) { return false; }};
  bool operator()(RawAddress* random_pseudo,
                  tBLE_ADDR_TYPE* p_identity_addr_type) {
    return body(random_pseudo, p_identity_addr_type);
  };
};
extern struct btm_random_pseudo_to_identity_addr
    btm_random_pseudo_to_identity_addr;
// Name: btm_ble_refresh_peer_resolvable_private_addr
// Params:  const RawAddress& pseudo_bda, const RawAddress& rpa,
// tBLE_RAND_ADDR_TYPE rra_type Returns: void
struct btm_ble_refresh_peer_resolvable_private_addr {
  std::function<void(const RawAddress& pseudo_bda, const RawAddress& rpa,
                     tBLE_RAND_ADDR_TYPE rra_type)>
      body{[](const RawAddress& /* pseudo_bda */, const RawAddress& /* rpa */,
              tBLE_RAND_ADDR_TYPE /* rra_type */) {}};
  void operator()(const RawAddress& pseudo_bda, const RawAddress& rpa,
                  tBLE_RAND_ADDR_TYPE rra_type) {
    body(pseudo_bda, rpa, rra_type);
  };
};
extern struct btm_ble_refresh_peer_resolvable_private_addr
    btm_ble_refresh_peer_resolvable_private_addr;

}  // namespace stack_btm_ble_addr
}  // namespace mock
}  // namespace test

// END mockcify generation
