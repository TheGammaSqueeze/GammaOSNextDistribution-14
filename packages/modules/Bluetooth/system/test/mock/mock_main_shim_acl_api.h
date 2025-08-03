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
#pragma once

/*
 * Generated mock file from original source file
 *   Functions generated:14
 *
 *  mockcify.pl ver 0.5.0
 */

#include <cstdint>
#include <functional>
#include <optional>
#include <string>

// Original included files, if any
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_octets.h"
#include "stack/include/hci_error_code.h"
#include "types/ble_address_with_type.h"
#include "types/raw_address.h"

// Original usings

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace main_shim_acl_api {

// Shared state between mocked functions and tests
// Name: ACL_AcceptLeConnectionFrom
// Params: const tBLE_BD_ADDR& legacy_address_with_type, bool is_direct
// Return: bool
struct ACL_AcceptLeConnectionFrom {
  static bool return_value;
  std::function<bool(const tBLE_BD_ADDR& legacy_address_with_type,
                     bool is_direct)>
      body{[](const tBLE_BD_ADDR& legacy_address_with_type, bool is_direct) {
        return return_value;
      }};
  bool operator()(const tBLE_BD_ADDR& legacy_address_with_type,
                  bool is_direct) {
    return body(legacy_address_with_type, is_direct);
  };
};
extern struct ACL_AcceptLeConnectionFrom ACL_AcceptLeConnectionFrom;

// Name: ACL_AddToAddressResolution
// Params: const tBLE_BD_ADDR& legacy_address_with_type, const Octet16&
// peer_irk, const Octet16& local_irk Return: void
struct ACL_AddToAddressResolution {
  std::function<void(const tBLE_BD_ADDR& legacy_address_with_type,
                     const Octet16& peer_irk, const Octet16& local_irk)>
      body{[](const tBLE_BD_ADDR& legacy_address_with_type,
              const Octet16& peer_irk, const Octet16& local_irk) {}};
  void operator()(const tBLE_BD_ADDR& legacy_address_with_type,
                  const Octet16& peer_irk, const Octet16& local_irk) {
    body(legacy_address_with_type, peer_irk, local_irk);
  };
};
extern struct ACL_AddToAddressResolution ACL_AddToAddressResolution;

// Name: ACL_CancelClassicConnection
// Params: const RawAddress& raw_address
// Return: void
struct ACL_CancelClassicConnection {
  std::function<void(const RawAddress& raw_address)> body{
      [](const RawAddress& raw_address) {}};
  void operator()(const RawAddress& raw_address) { body(raw_address); };
};
extern struct ACL_CancelClassicConnection ACL_CancelClassicConnection;

// Name: ACL_ClearAddressResolution
// Params:
// Return: void
struct ACL_ClearAddressResolution {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct ACL_ClearAddressResolution ACL_ClearAddressResolution;

// Name: ACL_ClearFilterAcceptList
// Params:
// Return: void
struct ACL_ClearFilterAcceptList {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct ACL_ClearFilterAcceptList ACL_ClearFilterAcceptList;

// Name: ACL_LeSetDefaultSubrate
// Params:
// Return: void
struct ACL_LeSetDefaultSubrate {
  std::function<void(uint16_t subrate_min, uint16_t subrate_max,
                     uint16_t max_latency, uint16_t cont_num,
                     uint16_t sup_tout)>
      body{[](uint16_t subrate_min, uint16_t subrate_max, uint16_t max_latency,
              uint16_t cont_num, uint16_t sup_tout) {}};
  void operator()(uint16_t subrate_min, uint16_t subrate_max,
                  uint16_t max_latency, uint16_t cont_num, uint16_t sup_tout) {
    body(subrate_min, subrate_max, max_latency, cont_num, sup_tout);
  };
};
extern struct ACL_LeSetDefaultSubrate ACL_LeSetDefaultSubrate;

// Name: ACL_LeSubrateRequest
// Params:
// Return: void
struct ACL_LeSubrateRequest {
  std::function<void(uint16_t hci_handle, uint16_t subrate_min,
                     uint16_t subrate_max, uint16_t max_latency,
                     uint16_t cont_num, uint16_t sup_tout)>
      body{[](uint16_t hci_handle, uint16_t subrate_min, uint16_t subrate_max,
              uint16_t max_latency, uint16_t cont_num, uint16_t sup_tout) {}};
  void operator()(uint16_t hci_handle, uint16_t subrate_min,
                  uint16_t subrate_max, uint16_t max_latency, uint16_t cont_num,
                  uint16_t sup_tout) {
    body(hci_handle, subrate_min, subrate_max, max_latency, cont_num, sup_tout);
  };
};
extern struct ACL_LeSubrateRequest ACL_LeSubrateRequest;

// Name: ACL_ConfigureLePrivacy
// Params: bool is_le_privacy_enabled
// Return: void
struct ACL_ConfigureLePrivacy {
  std::function<void(bool is_le_privacy_enabled)> body{
      [](bool is_le_privacy_enabled) {}};
  void operator()(bool is_le_privacy_enabled) { body(is_le_privacy_enabled); };
};
extern struct ACL_ConfigureLePrivacy ACL_ConfigureLePrivacy;

// Name: ACL_CreateClassicConnection
// Params: const RawAddress& raw_address
// Return: void
struct ACL_CreateClassicConnection {
  std::function<void(const RawAddress& raw_address)> body{
      [](const RawAddress& raw_address) {}};
  void operator()(const RawAddress& raw_address) { body(raw_address); };
};
extern struct ACL_CreateClassicConnection ACL_CreateClassicConnection;

// Name: ACL_Disconnect
// Params: uint16_t handle, bool is_classic, tHCI_STATUS reason, std::string
// comment Return: void
struct ACL_Disconnect {
  std::function<void(uint16_t handle, bool is_classic, tHCI_STATUS reason,
                     std::string comment)>
      body{[](uint16_t handle, bool is_classic, tHCI_STATUS reason,
              std::string comment) {}};
  void operator()(uint16_t handle, bool is_classic, tHCI_STATUS reason,
                  std::string comment) {
    body(handle, is_classic, reason, comment);
  };
};
extern struct ACL_Disconnect ACL_Disconnect;

// Name: ACL_IgnoreAllLeConnections
// Params:
// Return: void
struct ACL_IgnoreAllLeConnections {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct ACL_IgnoreAllLeConnections ACL_IgnoreAllLeConnections;

// Name: ACL_IgnoreLeConnectionFrom
// Params: const tBLE_BD_ADDR& legacy_address_with_type
// Return: void
struct ACL_IgnoreLeConnectionFrom {
  std::function<void(const tBLE_BD_ADDR& legacy_address_with_type)> body{
      [](const tBLE_BD_ADDR& legacy_address_with_type) {}};
  void operator()(const tBLE_BD_ADDR& legacy_address_with_type) {
    body(legacy_address_with_type);
  };
};
extern struct ACL_IgnoreLeConnectionFrom ACL_IgnoreLeConnectionFrom;

// Name: ACL_ReadConnectionAddress
// Params: uint16_t handle, RawAddress& conn_addr, tBLE_ADDR_TYPE*, bool
// p_addr_type Return: void
struct ACL_ReadConnectionAddress {
  std::function<void(uint16_t handle, RawAddress& conn_addr,
                     tBLE_ADDR_TYPE* p_addr_type, bool ota_address)>
      body{[](uint16_t handle, RawAddress& conn_addr,
              tBLE_ADDR_TYPE* p_addr_type, bool ota_address) {}};
  void operator()(uint16_t handle, RawAddress& conn_addr,
                  tBLE_ADDR_TYPE* p_addr_type, bool ota_address) {
    body(handle, conn_addr, p_addr_type, ota_address);
  };
};
extern struct ACL_ReadConnectionAddress ACL_ReadConnectionAddress;

// Name: ACL_ReadPeerConnectionAddress
// Params: uint16_t handle, RawAddress& conn_addr, tBLE_ADDR_TYPE*, bool
// p_addr_type Return: void
struct ACL_ReadPeerConnectionAddress {
  std::function<void(uint16_t handle, RawAddress& conn_addr,
                     tBLE_ADDR_TYPE* p_addr_type, bool ota_address)>
      body{[](uint16_t handle, RawAddress& conn_addr,
              tBLE_ADDR_TYPE* p_addr_type, bool ota_address) {}};
  void operator()(uint16_t handle, RawAddress& conn_addr,
                  tBLE_ADDR_TYPE* p_addr_type, bool ota_address) {
    body(handle, conn_addr, p_addr_type, ota_address);
  };
};
extern struct ACL_ReadPeerConnectionAddress ACL_ReadPeerConnectionAddress;

// Name: ACL_GetAdvertisingSetConnectedTo
// Params: const RawAddress& addr
// Return: std::optional<uint8_t>
struct ACL_GetAdvertisingSetConnectedTo {
  static std::optional<uint8_t> return_value;
  std::function<std::optional<uint8_t>(const RawAddress& addr)> body{
      [](const RawAddress& addr) { return return_value; }};
  std::optional<uint8_t> operator()(const RawAddress& addr) {
    return body(addr);
  };
};
extern struct ACL_GetAdvertisingSetConnectedTo ACL_GetAdvertisingSetConnectedTo;

// Name: ACL_RemoveFromAddressResolution
// Params: const tBLE_BD_ADDR& legacy_address_with_type
// Return: void
struct ACL_RemoveFromAddressResolution {
  std::function<void(const tBLE_BD_ADDR& legacy_address_with_type)> body{
      [](const tBLE_BD_ADDR& legacy_address_with_type) {}};
  void operator()(const tBLE_BD_ADDR& legacy_address_with_type) {
    body(legacy_address_with_type);
  };
};
extern struct ACL_RemoveFromAddressResolution ACL_RemoveFromAddressResolution;

// Name: ACL_Shutdown
// Params:
// Return: void
struct ACL_Shutdown {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct ACL_Shutdown ACL_Shutdown;

// Name: ACL_WriteData
// Params: uint16_t handle, BT_HDR* p_buf
// Return: void
struct ACL_WriteData {
  std::function<void(uint16_t handle, BT_HDR* p_buf)> body{
      [](uint16_t handle, BT_HDR* p_buf) {}};
  void operator()(uint16_t handle, BT_HDR* p_buf) { body(handle, p_buf); };
};
extern struct ACL_WriteData ACL_WriteData;

}  // namespace main_shim_acl_api
}  // namespace mock
}  // namespace test

// END mockcify generation
