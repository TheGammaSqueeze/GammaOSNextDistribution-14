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
 *   Functions generated:125
 *
 *  mockcify.pl ver 0.2.1
 */

#include <cstdint>
#include <functional>
#include <string>

// Original included files, if any
#include "device/include/controller.h"
#include "hci/class_of_device.h"
#include "stack/acl/acl.h"
#include "stack/btm/security_device_record.h"
#include "stack/include/acl_client_callbacks.h"
#include "stack/include/bt_hdr.h"
#include "types/raw_address.h"

// Mocked compile conditionals, if any
namespace test {
namespace mock {
namespace stack_acl {

// Name: BTM_BLE_IS_RESOLVE_BDA
// Params: const RawAddress& x
// Returns: bool
struct BTM_BLE_IS_RESOLVE_BDA {
  std::function<bool(const RawAddress& x)> body{
      [](const RawAddress& /* x */) { return false; }};
  bool operator()(const RawAddress& x) { return body(x); };
};
extern struct BTM_BLE_IS_RESOLVE_BDA BTM_BLE_IS_RESOLVE_BDA;
// Name: BTM_IsAclConnectionUp
// Params: const RawAddress& remote_bda, tBT_TRANSPORT transport
// Returns: bool
struct BTM_IsAclConnectionUp {
  std::function<bool(const RawAddress& remote_bda, tBT_TRANSPORT transport)>
      body{[](const RawAddress& /* remote_bda */,
              tBT_TRANSPORT /* transport */) { return false; }};
  bool operator()(const RawAddress& remote_bda, tBT_TRANSPORT transport) {
    return body(remote_bda, transport);
  };
};
extern struct BTM_IsAclConnectionUp BTM_IsAclConnectionUp;
// Name: BTM_IsAclConnectionUpAndHandleValid
// Params: const RawAddress& remote_bda, tBT_TRANSPORT transport
// Returns: bool
struct BTM_IsAclConnectionUpAndHandleValid {
  std::function<bool(const RawAddress& remote_bda, tBT_TRANSPORT transport)>
      body{[](const RawAddress& /* remote_bda */,
              tBT_TRANSPORT /* transport */) { return false; }};
  bool operator()(const RawAddress& remote_bda, tBT_TRANSPORT transport) {
    return body(remote_bda, transport);
  };
};
extern struct BTM_IsAclConnectionUpAndHandleValid
    BTM_IsAclConnectionUpAndHandleValid;
// Name: BTM_IsAclConnectionUpFromHandle
// Params: uint16_t hci_handle
// Returns: bool
struct BTM_IsAclConnectionUpFromHandle {
  std::function<bool(uint16_t hci_handle)> body{
      [](uint16_t /* hci_handle */) { return false; }};
  bool operator()(uint16_t hci_handle) { return body(hci_handle); };
};
extern struct BTM_IsAclConnectionUpFromHandle BTM_IsAclConnectionUpFromHandle;
// Name: BTM_IsBleConnection
// Params: uint16_t hci_handle
// Returns: bool
struct BTM_IsBleConnection {
  std::function<bool(uint16_t hci_handle)> body{
      [](uint16_t /* hci_handle */) { return false; }};
  bool operator()(uint16_t hci_handle) { return body(hci_handle); };
};
extern struct BTM_IsBleConnection BTM_IsBleConnection;
// Name: BTM_IsPhy2mSupported
// Params: const RawAddress& remote_bda, tBT_TRANSPORT transport
// Returns: bool
struct BTM_IsPhy2mSupported {
  std::function<bool(const RawAddress& remote_bda, tBT_TRANSPORT transport)>
      body{[](const RawAddress& /* remote_bda */,
              tBT_TRANSPORT /* transport */) { return false; }};
  bool operator()(const RawAddress& remote_bda, tBT_TRANSPORT transport) {
    return body(remote_bda, transport);
  };
};
extern struct BTM_IsPhy2mSupported BTM_IsPhy2mSupported;
// Name: BTM_ReadRemoteConnectionAddr
// Params: const RawAddress& pseudo_addr, RawAddress& conn_addr, bool
// ota_address tBLE_ADDR_TYPE* p_addr_type Returns: bool
struct BTM_ReadRemoteConnectionAddr {
  std::function<bool(const RawAddress& pseudo_addr, RawAddress& conn_addr,
                     tBLE_ADDR_TYPE* p_addr_type, bool ota_address)>
      body{[](const RawAddress& /* pseudo_addr */, RawAddress& /* conn_addr */,
              tBLE_ADDR_TYPE* /* p_addr_type */,
              bool /* ota_address */) { return false; }};
  bool operator()(const RawAddress& pseudo_addr, RawAddress& conn_addr,
                  tBLE_ADDR_TYPE* p_addr_type, bool ota_address) {
    return body(pseudo_addr, conn_addr, p_addr_type, ota_address);
  };
};
extern struct BTM_ReadRemoteConnectionAddr BTM_ReadRemoteConnectionAddr;
// Name: BTM_IsRemoteVersionReceived
// Params: const RawAddress& addr
// Returns: bool
struct BTM_IsRemoteVersionReceived {
  std::function<bool(const RawAddress& addr)> body{
      [](const RawAddress& /* addr */) { return false; }};
  bool operator()(const RawAddress& addr) { return body(addr); };
};
extern struct BTM_IsRemoteVersionReceived BTM_IsRemoteVersionReceived;
// Name: BTM_ReadRemoteVersion
// Params: const RawAddress& addr, uint8_t* lmp_version, uint16_t*
// manufacturer, uint16_t* lmp_sub_version
// Returns: bool
struct BTM_ReadRemoteVersion {
  std::function<bool(const RawAddress& addr, uint8_t* lmp_version,
                     uint16_t* manufacturer, uint16_t* lmp_sub_version)>
      body{[](const RawAddress& /* addr */, uint8_t* /* lmp_version */,
              uint16_t* /* manufacturer */,
              uint16_t* /* lmp_sub_version */) { return false; }};
  bool operator()(const RawAddress& addr, uint8_t* lmp_version,
                  uint16_t* manufacturer, uint16_t* lmp_sub_version) {
    return body(addr, lmp_version, manufacturer, lmp_sub_version);
  };
};
extern struct BTM_ReadRemoteVersion BTM_ReadRemoteVersion;
// Name: BTM_is_sniff_allowed_for
// Params: const RawAddress& peer_addr
// Returns: bool
struct BTM_is_sniff_allowed_for {
  std::function<bool(const RawAddress& peer_addr)> body{
      [](const RawAddress& /* peer_addr */) { return false; }};
  bool operator()(const RawAddress& peer_addr) { return body(peer_addr); };
};
extern struct BTM_is_sniff_allowed_for BTM_is_sniff_allowed_for;
// Name: acl_send_data_packet_br_edr
// Params: const RawAddress& bd_addr, BT_HDR* p_buf
// Returns: void
struct acl_send_data_packet_br_edr {
  std::function<void(const RawAddress& bd_addr, BT_HDR* p_buf)> body{
      [](const RawAddress& /* bd_addr */, BT_HDR* /* p_buf */) {}};
  void operator()(const RawAddress& bd_addr, BT_HDR* p_buf) {
    return body(bd_addr, p_buf);
  };
};
extern struct acl_send_data_packet_br_edr acl_send_data_packet_br_edr;
// Name: acl_create_le_connection
// Params: const RawAddress& bd_addr
// Returns: bool
struct acl_create_le_connection {
  std::function<bool(const RawAddress& bd_addr)> body{
      [](const RawAddress& /* bd_addr */) { return false; }};
  bool operator()(const RawAddress& bd_addr) { return body(bd_addr); };
};
extern struct acl_create_le_connection acl_create_le_connection;
// Name: acl_create_le_connection_with_id
// Params: uint8_t id, const RawAddress& bd_addr
// Returns: bool
struct acl_create_le_connection_with_id {
  std::function<bool(uint8_t id, const RawAddress& bd_addr,
                     tBLE_ADDR_TYPE addr_type)>
      body{[](uint8_t /* id */, const RawAddress& /* bd_addr */,
              tBLE_ADDR_TYPE /* addr_type */) { return false; }};
  bool operator()(uint8_t id, const RawAddress& bd_addr,
                  tBLE_ADDR_TYPE addr_type) {
    return body(id, bd_addr, addr_type);
  };
};
extern struct acl_create_le_connection_with_id acl_create_le_connection_with_id;
// Name: acl_is_role_switch_allowed
// Params:
// Returns: bool
struct acl_is_role_switch_allowed {
  std::function<bool()> body{[]() { return false; }};
  bool operator()() { return body(); };
};
extern struct acl_is_role_switch_allowed acl_is_role_switch_allowed;
// Name: acl_is_switch_role_idle
// Params: const RawAddress& bd_addr, tBT_TRANSPORT transport
// Returns: bool
struct acl_is_switch_role_idle {
  std::function<bool(const RawAddress& bd_addr, tBT_TRANSPORT transport)> body{
      [](const RawAddress& /* bd_addr */, tBT_TRANSPORT /* transport */) {
        return false;
      }};
  bool operator()(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
    return body(bd_addr, transport);
  };
};
extern struct acl_is_switch_role_idle acl_is_switch_role_idle;
// Name: acl_peer_supports_ble_2m_phy
// Params: uint16_t hci_handle
// Returns: bool
struct acl_peer_supports_ble_2m_phy {
  std::function<bool(uint16_t hci_handle)> body{
      [](uint16_t /* hci_handle */) { return false; }};
  bool operator()(uint16_t hci_handle) { return body(hci_handle); };
};
extern struct acl_peer_supports_ble_2m_phy acl_peer_supports_ble_2m_phy;
// Name: acl_peer_supports_ble_coded_phy
// Params: uint16_t hci_handle
// Returns: bool
struct acl_peer_supports_ble_coded_phy {
  std::function<bool(uint16_t hci_handle)> body{
      [](uint16_t /* hci_handle */) { return false; }};
  bool operator()(uint16_t hci_handle) { return body(hci_handle); };
};
extern struct acl_peer_supports_ble_coded_phy acl_peer_supports_ble_coded_phy;
// Name: acl_peer_supports_ble_connection_parameters_request
// Params:  const RawAddress& remote_bda
// Returns: bool
struct acl_peer_supports_ble_connection_parameters_request {
  std::function<bool(const RawAddress& remote_bda)> body{
      [](const RawAddress& /* remote_bda */) { return false; }};
  bool operator()(const RawAddress& remote_bda) { return body(remote_bda); };
};
extern struct acl_peer_supports_ble_connection_parameters_request
    acl_peer_supports_ble_connection_parameters_request;
// Name: acl_peer_supports_ble_packet_extension
// Params: uint16_t hci_handle
// Returns: bool
struct acl_peer_supports_ble_packet_extension {
  std::function<bool(uint16_t hci_handle)> body{
      [](uint16_t /* hci_handle */) { return false; }};
  bool operator()(uint16_t hci_handle) { return body(hci_handle); };
};
extern struct acl_peer_supports_ble_packet_extension
    acl_peer_supports_ble_packet_extension;
// Name: acl_peer_supports_sniff_subrating
// Params: const RawAddress& remote_bda
// Returns: bool
struct acl_peer_supports_sniff_subrating {
  std::function<bool(const RawAddress& remote_bda)> body{
      [](const RawAddress& /* remote_bda */) { return false; }};
  bool operator()(const RawAddress& remote_bda) { return body(remote_bda); };
};
extern struct acl_peer_supports_sniff_subrating
    acl_peer_supports_sniff_subrating;
// Name: acl_peer_supports_ble_connection_subrating
// Params: const RawAddress& remote_bda
// Returns: bool
struct acl_peer_supports_ble_connection_subrating {
  std::function<bool(const RawAddress& remote_bda)> body{
      [](const RawAddress& /* remote_bda */) { return false; }};
  bool operator()(const RawAddress& remote_bda) { return body(remote_bda); };
};
extern struct acl_peer_supports_ble_connection_subrating
    acl_peer_supports_ble_connection_subrating;
// Name: acl_peer_supports_ble_connection_subrating_host
// Params: const RawAddress& remote_bda
// Returns: bool
struct acl_peer_supports_ble_connection_subrating_host {
  std::function<bool(const RawAddress& remote_bda)> body{
      [](const RawAddress& /* remote_bda */) { return false; }};
  bool operator()(const RawAddress& remote_bda) { return body(remote_bda); };
};
extern struct acl_peer_supports_ble_connection_subrating_host
    acl_peer_supports_ble_connection_subrating_host;
// Name: acl_refresh_remote_address
// Params: const RawAddress& identity_address, tBLE_ADDR_TYPE
// identity_address_type, const RawAddress& bda, tBLE_ADDR_TYPE rra_type,
// const RawAddress& rpa Returns: bool
struct acl_refresh_remote_address {
  std::function<bool(const RawAddress& identity_address,
                     tBLE_ADDR_TYPE identity_address_type,
                     const RawAddress& bda, tBLE_RAND_ADDR_TYPE rra_type,
                     const RawAddress& rpa)>
      body{[](const RawAddress& /* identity_address */,
              tBLE_ADDR_TYPE /* identity_address_type */,
              const RawAddress& /* bda */, tBLE_RAND_ADDR_TYPE /* rra_type */,
              const RawAddress& /* rpa */) { return false; }};
  bool operator()(const RawAddress& identity_address,
                  tBLE_ADDR_TYPE identity_address_type, const RawAddress& bda,
                  tBLE_RAND_ADDR_TYPE rra_type, const RawAddress& rpa) {
    return body(identity_address, identity_address_type, bda, rra_type, rpa);
  };
};
extern struct acl_refresh_remote_address acl_refresh_remote_address;
// Name: acl_set_peer_le_features_from_handle
// Params: uint16_t hci_handle, const uint8_t* p
// Returns: bool
struct acl_set_peer_le_features_from_handle {
  std::function<bool(uint16_t hci_handle, const uint8_t* p)> body{
      [](uint16_t /* hci_handle */, const uint8_t* /* p */) { return false; }};
  bool operator()(uint16_t hci_handle, const uint8_t* p) {
    return body(hci_handle, p);
  };
};
extern struct acl_set_peer_le_features_from_handle
    acl_set_peer_le_features_from_handle;
// Name: acl_create_classic_connection
// Params: const RawAddress& bd_addr, bool there_are_high_priority_channels,
// bool is_bonding Returns: constexpr uint16_t kDefaultPacketTypes =
// HCI_PKT_TYPES_MASK_DM1 | HCI_PKT_TYPES_MASK_DH1 | HCI_PKT_TYPES_MASK_DM3 |
// HCI_PKT_TYPES_MASK_DH3 | HCI_PKT_TYPES_MASK_DM5 | HCI_PKT_TYPES_MASK_DH5;
// void
struct acl_create_classic_connection {
  std::function<void(const RawAddress& bd_addr,
                     bool there_are_high_priority_channels, bool is_bonding)>
      body{[](const RawAddress& /* bd_addr */,
              bool /* there_are_high_priority_channels */,
              bool /* is_bonding */) { return 0; }};
  void operator()(const RawAddress& bd_addr,
                  bool there_are_high_priority_channels, bool is_bonding) {
    return body(bd_addr, there_are_high_priority_channels, is_bonding);
  };
};
extern struct acl_create_classic_connection acl_create_classic_connection;
// Name: acl_get_connection_from_address
// Params: const RawAddress& bd_addr, tBT_TRANSPORT transport
// Returns: tACL_CONN*
struct acl_get_connection_from_address {
  std::function<tACL_CONN*(const RawAddress& bd_addr, tBT_TRANSPORT transport)>
      body{[](const RawAddress& /* bd_addr */, tBT_TRANSPORT /* transport */) {
        return nullptr;
      }};
  tACL_CONN* operator()(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
    return body(bd_addr, transport);
  };
};
extern struct acl_get_connection_from_address acl_get_connection_from_address;
// Name: btm_acl_for_bda
// Params: const RawAddress& bd_addr, tBT_TRANSPORT transport
// Returns: tACL_CONN*
struct btm_acl_for_bda {
  std::function<tACL_CONN*(const RawAddress& bd_addr, tBT_TRANSPORT transport)>
      body{[](const RawAddress& /* bd_addr */, tBT_TRANSPORT /* transport */) {
        return nullptr;
      }};
  tACL_CONN* operator()(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
    return body(bd_addr, transport);
  };
};
extern struct btm_acl_for_bda btm_acl_for_bda;
// Name: acl_get_connection_from_handle
// Params: uint16_t handle
// Returns: tACL_CONN*
struct acl_get_connection_from_handle {
  std::function<tACL_CONN*(uint16_t handle)> body{
      [](uint16_t /* handle */) { return nullptr; }};
  tACL_CONN* operator()(uint16_t handle) { return body(handle); };
};
extern struct acl_get_connection_from_handle acl_get_connection_from_handle;
// Name: BTM_GetLinkSuperTout
// Params: const RawAddress& remote_bda, uint16_t* p_timeout
// Returns: tBTM_STATUS
struct BTM_GetLinkSuperTout {
  std::function<tBTM_STATUS(const RawAddress& remote_bda, uint16_t* p_timeout)>
      body{[](const RawAddress& /* remote_bda */, uint16_t* /* p_timeout */) {
        return 0;
      }};
  tBTM_STATUS operator()(const RawAddress& remote_bda, uint16_t* p_timeout) {
    return body(remote_bda, p_timeout);
  };
};
extern struct BTM_GetLinkSuperTout BTM_GetLinkSuperTout;
// Name: BTM_GetRole
// Params: const RawAddress& remote_bd_addr, tHCI_ROLE* p_role
// Returns: tBTM_STATUS
struct BTM_GetRole {
  std::function<tBTM_STATUS(const RawAddress& remote_bd_addr,
                            tHCI_ROLE* p_role)>
      body{[](const RawAddress& /* remote_bd_addr */, tHCI_ROLE* /* p_role */) {
        return 0;
      }};
  tBTM_STATUS operator()(const RawAddress& remote_bd_addr, tHCI_ROLE* p_role) {
    return body(remote_bd_addr, p_role);
  };
};
extern struct BTM_GetRole BTM_GetRole;
// Name: BTM_ReadFailedContactCounter
// Params: const RawAddress& remote_bda, tBTM_CMPL_CB* p_cb
// Returns: tBTM_STATUS
struct BTM_ReadFailedContactCounter {
  std::function<tBTM_STATUS(const RawAddress& remote_bda, tBTM_CMPL_CB* p_cb)>
      body{[](const RawAddress& /* remote_bda */, tBTM_CMPL_CB* /* p_cb */) {
        return 0;
      }};
  tBTM_STATUS operator()(const RawAddress& remote_bda, tBTM_CMPL_CB* p_cb) {
    return body(remote_bda, p_cb);
  };
};
extern struct BTM_ReadFailedContactCounter BTM_ReadFailedContactCounter;
// Name: BTM_ReadRSSI
// Params: const RawAddress& remote_bda, tBTM_CMPL_CB* p_cb
// Returns: tBTM_STATUS
struct BTM_ReadRSSI {
  std::function<tBTM_STATUS(const RawAddress& remote_bda, tBTM_CMPL_CB* p_cb)>
      body{[](const RawAddress& /* remote_bda */, tBTM_CMPL_CB* /* p_cb */) {
        return 0;
      }};
  tBTM_STATUS operator()(const RawAddress& remote_bda, tBTM_CMPL_CB* p_cb) {
    return body(remote_bda, p_cb);
  };
};
extern struct BTM_ReadRSSI BTM_ReadRSSI;
// Name: BTM_ReadTxPower
// Params: const RawAddress& remote_bda, tBT_TRANSPORT transport,
// tBTM_CMPL_CB* p_cb Returns: tBTM_STATUS
struct BTM_ReadTxPower {
  std::function<tBTM_STATUS(const RawAddress& remote_bda,
                            tBT_TRANSPORT transport, tBTM_CMPL_CB* p_cb)>
      body{[](const RawAddress& /* remote_bda */, tBT_TRANSPORT /* transport */,
              tBTM_CMPL_CB* /* p_cb */) { return BT_TRANSPORT_BR_EDR; }};
  tBTM_STATUS operator()(const RawAddress& remote_bda, tBT_TRANSPORT transport,
                         tBTM_CMPL_CB* p_cb) {
    return body(remote_bda, transport, p_cb);
  };
};
extern struct BTM_ReadTxPower BTM_ReadTxPower;
// Name: BTM_SetLinkSuperTout
// Params: const RawAddress& remote_bda, uint16_t timeout
// Returns: tBTM_STATUS
struct BTM_SetLinkSuperTout {
  std::function<tBTM_STATUS(const RawAddress& remote_bda, uint16_t timeout)>
      body{[](const RawAddress& /* remote_bda */, uint16_t /* timeout */) {
        return 0;
      }};
  tBTM_STATUS operator()(const RawAddress& remote_bda, uint16_t timeout) {
    return body(remote_bda, timeout);
  };
};
extern struct BTM_SetLinkSuperTout BTM_SetLinkSuperTout;
// Name: BTM_SwitchRoleToCentral
// Params: const RawAddress& remote_bd_addr
// Returns: tBTM_STATUS
struct BTM_SwitchRoleToCentral {
  std::function<tBTM_STATUS(const RawAddress& remote_bd_addr)> body{
      [](const RawAddress& /* remote_bd_addr */) { return 0; }};
  tBTM_STATUS operator()(const RawAddress& remote_bd_addr) {
    return body(remote_bd_addr);
  };
};
extern struct BTM_SwitchRoleToCentral BTM_SwitchRoleToCentral;
// Name: btm_remove_acl
// Params: const RawAddress& bd_addr, tBT_TRANSPORT transport
// Returns: tBTM_STATUS
struct btm_remove_acl {
  std::function<tBTM_STATUS(const RawAddress& bd_addr, tBT_TRANSPORT transport)>
      body{[](const RawAddress& /* bd_addr */, tBT_TRANSPORT /* transport */) {
        return BT_TRANSPORT_BR_EDR;
      }};
  tBTM_STATUS operator()(const RawAddress& bd_addr, tBT_TRANSPORT transport) {
    return body(bd_addr, transport);
  };
};
extern struct btm_remove_acl btm_remove_acl;
// Name: btm_get_acl_disc_reason_code
// Params: void
// Returns: tHCI_REASON
struct btm_get_acl_disc_reason_code {
  std::function<tHCI_REASON(void)> body{[](void) { return HCI_SUCCESS; }};
  tHCI_REASON operator()(void) { return body(); };
};
extern struct btm_get_acl_disc_reason_code btm_get_acl_disc_reason_code;
// Name: btm_is_acl_locally_initiated
// Params: void
// Returns: bool
struct btm_is_acl_locally_initiated {
  std::function<bool(void)> body{[](void) { return true; }};
  bool operator()(void) { return body(); };
};
extern struct btm_is_acl_locally_initiated btm_is_acl_locally_initiated;
// Name: BTM_GetHCIConnHandle
// Params: const RawAddress& remote_bda, tBT_TRANSPORT transport
// Returns: uint16_t
struct BTM_GetHCIConnHandle {
  std::function<uint16_t(const RawAddress& remote_bda, tBT_TRANSPORT transport)>
      body{[](const RawAddress& /* remote_bda */,
              tBT_TRANSPORT /* transport */) { return BT_TRANSPORT_BR_EDR; }};
  uint16_t operator()(const RawAddress& remote_bda, tBT_TRANSPORT transport) {
    return body(remote_bda, transport);
  };
};
extern struct BTM_GetHCIConnHandle BTM_GetHCIConnHandle;
// Name: BTM_GetMaxPacketSize
// Params: const RawAddress& addr
// Returns: uint16_t
struct BTM_GetMaxPacketSize {
  std::function<uint16_t(const RawAddress& addr)> body{
      [](const RawAddress& /* addr */) { return 0; }};
  uint16_t operator()(const RawAddress& addr) { return body(addr); };
};
extern struct BTM_GetMaxPacketSize BTM_GetMaxPacketSize;
// Name: BTM_GetNumAclLinks
// Params: void
// Returns: uint16_t
struct BTM_GetNumAclLinks {
  std::function<uint16_t(void)> body{[](void) { return 0; }};
  uint16_t operator()(void) { return body(); };
};
extern struct BTM_GetNumAclLinks BTM_GetNumAclLinks;
// Name: acl_get_supported_packet_types
// Params:
// Returns: uint16_t
struct acl_get_supported_packet_types {
  std::function<uint16_t()> body{[]() { return 0; }};
  uint16_t operator()() { return body(); };
};
extern struct acl_get_supported_packet_types acl_get_supported_packet_types;
// Name: BTM_GetPeerSCA
// Params: const RawAddress& remote_bda, tBT_TRANSPORT transport
// Returns: uint8_t
struct BTM_GetPeerSCA {
  std::function<uint8_t(const RawAddress& remote_bda, tBT_TRANSPORT transport)>
      body{[](const RawAddress& /* remote_bda */,
              tBT_TRANSPORT /* transport */) { return BT_TRANSPORT_BR_EDR; }};
  uint8_t operator()(const RawAddress& remote_bda, tBT_TRANSPORT transport) {
    return body(remote_bda, transport);
  };
};
extern struct BTM_GetPeerSCA BTM_GetPeerSCA;
// Name: acl_link_role_from_handle
// Params: uint16_t handle
// Returns: uint8_t
struct acl_link_role_from_handle {
  std::function<uint8_t(uint16_t handle)> body{
      [](uint16_t /* handle */) { return 0; }};
  uint8_t operator()(uint16_t handle) { return body(handle); };
};
extern struct acl_link_role_from_handle acl_link_role_from_handle;
// Name: btm_handle_to_acl_index
// Params: uint16_t hci_handle
// Returns: uint8_t
struct btm_handle_to_acl_index {
  std::function<uint8_t(uint16_t hci_handle)> body{
      [](uint16_t /* hci_handle */) { return 0; }};
  uint8_t operator()(uint16_t hci_handle) { return body(hci_handle); };
};
extern struct btm_handle_to_acl_index btm_handle_to_acl_index;
// Name: BTM_ReadRemoteFeatures
// Params: const RawAddress& addr
// Returns: uint8_t*
struct BTM_ReadRemoteFeatures {
  std::function<uint8_t*(const RawAddress& addr)> body{
      [](const RawAddress& /* addr */) { return nullptr; }};
  uint8_t* operator()(const RawAddress& addr) { return body(addr); };
};
extern struct BTM_ReadRemoteFeatures BTM_ReadRemoteFeatures;
// Name: ACL_RegisterClient
// Params: struct acl_client_callback_s* callbacks
// Returns: void
struct ACL_RegisterClient {
  std::function<void(struct acl_client_callback_s* callbacks)> body{
      [](struct acl_client_callback_s* /* callbacks */) { ; }};
  void operator()(struct acl_client_callback_s* callbacks) { body(callbacks); };
};
extern struct ACL_RegisterClient ACL_RegisterClient;
// Name: ACL_UnregisterClient
// Params: struct acl_client_callback_s* callbacks
// Returns: void
struct ACL_UnregisterClient {
  std::function<void(struct acl_client_callback_s* callbacks)> body{
      [](struct acl_client_callback_s* /* callbacks */) { ; }};
  void operator()(struct acl_client_callback_s* callbacks) { body(callbacks); };
};
extern struct ACL_UnregisterClient ACL_UnregisterClient;
// Name: BTM_ReadConnectionAddr
// Params: const RawAddress& remote_bda, RawAddress& local_conn_addr, bool
// ota_address tBLE_ADDR_TYPE* p_addr_type Returns: void
struct BTM_ReadConnectionAddr {
  std::function<void(const RawAddress& remote_bda, RawAddress& local_conn_addr,
                     tBLE_ADDR_TYPE* p_addr_type, bool ota_address)>
      body{[](const RawAddress& /* remote_bda */,
              RawAddress& /* local_conn_addr */,
              tBLE_ADDR_TYPE* /* p_addr_type */, bool /* ota_address */) { ; }};
  void operator()(const RawAddress& remote_bda, RawAddress& local_conn_addr,
                  tBLE_ADDR_TYPE* p_addr_type, bool ota_address) {
    body(remote_bda, local_conn_addr, p_addr_type, ota_address);
  };
};
extern struct BTM_ReadConnectionAddr BTM_ReadConnectionAddr;
// Name: BTM_RequestPeerSCA
// Params: const RawAddress& remote_bda, tBT_TRANSPORT transport
// Returns: void
struct BTM_RequestPeerSCA {
  std::function<void(const RawAddress& remote_bda, tBT_TRANSPORT transport)>
      body{[](const RawAddress& /* remote_bda */,
              tBT_TRANSPORT /* transport */) { ; }};
  void operator()(const RawAddress& remote_bda, tBT_TRANSPORT transport) {
    body(remote_bda, transport);
  };
};
extern struct BTM_RequestPeerSCA BTM_RequestPeerSCA;
// Name: BTM_acl_after_controller_started
// Params: const controller_t* controller
// Returns: void
struct BTM_acl_after_controller_started {
  std::function<void(const controller_t* controller)> body{
      [](const controller_t* /* controller */) { ; }};
  void operator()(const controller_t* controller) { body(controller); };
};
extern struct BTM_acl_after_controller_started BTM_acl_after_controller_started;
// Name: BTM_block_role_switch_for
// Params: const RawAddress& peer_addr
// Returns: void
struct BTM_block_role_switch_for {
  std::function<void(const RawAddress& peer_addr)> body{
      [](const RawAddress& /* peer_addr */) { ; }};
  void operator()(const RawAddress& peer_addr) { body(peer_addr); };
};
extern struct BTM_block_role_switch_for BTM_block_role_switch_for;
// Name: BTM_block_sniff_mode_for
// Params: const RawAddress& peer_addr
// Returns: void
struct BTM_block_sniff_mode_for {
  std::function<void(const RawAddress& peer_addr)> body{
      [](const RawAddress& /* peer_addr */) { ; }};
  void operator()(const RawAddress& peer_addr) { body(peer_addr); };
};
extern struct BTM_block_sniff_mode_for BTM_block_sniff_mode_for;
// Name: BTM_default_block_role_switch
// Params:
// Returns: void
struct BTM_default_block_role_switch {
  std::function<void()> body{[]() { ; }};
  void operator()() { body(); };
};
extern struct BTM_default_block_role_switch BTM_default_block_role_switch;
// Name: BTM_default_unblock_role_switch
// Params:
// Returns: void
struct BTM_default_unblock_role_switch {
  std::function<void()> body{[]() { ; }};
  void operator()() { body(); };
};
extern struct BTM_default_unblock_role_switch BTM_default_unblock_role_switch;
// Name: BTM_unblock_role_switch_for
// Params: const RawAddress& peer_addr
// Returns: void
struct BTM_unblock_role_switch_for {
  std::function<void(const RawAddress& peer_addr)> body{
      [](const RawAddress& /* peer_addr */) { ; }};
  void operator()(const RawAddress& peer_addr) { body(peer_addr); };
};
extern struct BTM_unblock_role_switch_for BTM_unblock_role_switch_for;
// Name: BTM_unblock_sniff_mode_for
// Params: const RawAddress& peer_addr
// Returns: void
struct BTM_unblock_sniff_mode_for {
  std::function<void(const RawAddress& peer_addr)> body{
      [](const RawAddress& /* peer_addr */) { ; }};
  void operator()(const RawAddress& peer_addr) { body(peer_addr); };
};
extern struct BTM_unblock_sniff_mode_for BTM_unblock_sniff_mode_for;
// Name: acl_disconnect_after_role_switch
// Params: uint16_t conn_handle, tHCI_STATUS reason
// Returns: void
struct acl_disconnect_after_role_switch {
  std::function<void(uint16_t conn_handle, tHCI_STATUS reason,
                     std::string comment)>
      body{[](uint16_t /* conn_handle */, tHCI_STATUS /* reason */,
              std::string /* comment */) { ; }};
  void operator()(uint16_t conn_handle, tHCI_STATUS reason,
                  std::string comment) {
    body(conn_handle, reason, comment);
  };
};
extern struct acl_disconnect_after_role_switch acl_disconnect_after_role_switch;
// Name: acl_disconnect_from_handle
// Params: uint16_t handle, tHCI_STATUS reason
// Returns: void
struct acl_disconnect_from_handle {
  std::function<void(uint16_t handle, tHCI_STATUS reason, std::string comment)>
      body{[](uint16_t /* handle */, tHCI_STATUS /* reason */,
              std::string /* comment */) { ; }};
  void operator()(uint16_t handle, tHCI_STATUS reason, std::string comment) {
    body(handle, reason, comment);
  };
};
extern struct acl_disconnect_from_handle acl_disconnect_from_handle;
// Name: acl_packets_completed
// Params: uint16_t handle, uint16_t credits
// Returns: void
struct acl_packets_completed {
  std::function<void(uint16_t handle, uint16_t credits)> body{
      [](uint16_t /* handle */, uint16_t /* credits */) { ; }};
  void operator()(uint16_t handle, uint16_t credits) { body(handle, credits); };
};
extern struct acl_packets_completed acl_packets_completed;
// Name: acl_process_extended_features
// Params: uint16_t handle, uint8_t current_page_number, uint8_t
// max_page_number, uint64_t features Returns: void
struct acl_process_extended_features {
  std::function<void(uint16_t handle, uint8_t current_page_number,
                     uint8_t max_page_number, uint64_t features)>
      body{[](uint16_t /* handle */, uint8_t /* current_page_number */,
              uint8_t /* max_page_number */, uint64_t /* features */) { ; }};
  void operator()(uint16_t handle, uint8_t current_page_number,
                  uint8_t max_page_number, uint64_t features) {
    body(handle, current_page_number, max_page_number, features);
  };
};
extern struct acl_process_extended_features acl_process_extended_features;
// Name: acl_process_supported_features
// Params: uint16_t handle, uint64_t features
// Returns: void
struct acl_process_supported_features {
  std::function<void(uint16_t handle, uint64_t features)> body{
      [](uint16_t /* handle */, uint64_t /* features */) { ; }};
  void operator()(uint16_t handle, uint64_t features) {
    body(handle, features);
  };
};
extern struct acl_process_supported_features acl_process_supported_features;
// Name: acl_rcv_acl_data
// Params: BT_HDR* p_msg
// Returns: void
struct acl_rcv_acl_data {
  std::function<void(BT_HDR* p_msg)> body{[](BT_HDR* /* p_msg */) { ; }};
  void operator()(BT_HDR* p_msg) { body(p_msg); };
};
extern struct acl_rcv_acl_data acl_rcv_acl_data;
// Name: acl_send_data_packet_ble
// Params: const RawAddress& bd_addr, BT_HDR* p_buf
// Returns: void
struct acl_send_data_packet_ble {
  std::function<void(const RawAddress& bd_addr, BT_HDR* p_buf)> body{
      [](const RawAddress& /* bd_addr */, BT_HDR* /* p_buf */) { ; }};
  void operator()(const RawAddress& bd_addr, BT_HDR* p_buf) {
    body(bd_addr, p_buf);
  };
};
extern struct acl_send_data_packet_ble acl_send_data_packet_ble;
// Name: acl_set_disconnect_reason
// Params: tHCI_STATUS acl_disc_reason
// Returns: void
struct acl_set_disconnect_reason {
  std::function<void(tHCI_STATUS acl_disc_reason)> body{
      [](tHCI_STATUS /* acl_disc_reason */) { ; }};
  void operator()(tHCI_STATUS acl_disc_reason) { body(acl_disc_reason); };
};
extern struct acl_set_disconnect_reason acl_set_disconnect_reason;
// Name: acl_write_automatic_flush_timeout
// Params: const RawAddress& bd_addr, uint16_t flush_timeout_in_ticks
// Returns: void
struct acl_write_automatic_flush_timeout {
  std::function<void(const RawAddress& bd_addr,
                     uint16_t flush_timeout_in_ticks)>
      body{[](const RawAddress& /* bd_addr */,
              uint16_t /* flush_timeout_in_ticks */) { ; }};
  void operator()(const RawAddress& bd_addr, uint16_t flush_timeout_in_ticks) {
    body(bd_addr, flush_timeout_in_ticks);
  };
};
extern struct acl_write_automatic_flush_timeout
    acl_write_automatic_flush_timeout;
// Name: btm_acl_connected
// Params: const RawAddress& bda, uint16_t handle, tHCI_STATUS status, uint8_t
// enc_mode Returns: void
struct btm_acl_connected {
  std::function<void(const RawAddress& bda, uint16_t handle, tHCI_STATUS status,
                     uint8_t enc_mode)>
      body{[](const RawAddress& /* bda */, uint16_t /* handle */,
              tHCI_STATUS /* status */, uint8_t /* enc_mode */) { ; }};
  void operator()(const RawAddress& bda, uint16_t handle, tHCI_STATUS status,
                  uint8_t enc_mode) {
    body(bda, handle, status, enc_mode);
  };
};
extern struct btm_acl_connected btm_acl_connected;
// Name: btm_connection_request
// Params: const RawAddress& bda, const bluetooth::hci::ClassOfDevice& cod
// Returns: void
struct btm_connection_request {
  std::function<void(const RawAddress& bda,
                     const bluetooth::hci::ClassOfDevice& cod)>
      body{[](const RawAddress& /* bda */,
              const bluetooth::hci::ClassOfDevice& /* cod */) { ; }};
  void operator()(const RawAddress& bda,
                  const bluetooth::hci::ClassOfDevice& cod) {
    body(bda, cod);
  };
};
extern struct btm_connection_request btm_connection_request;
// Name: btm_acl_created
// Params: const RawAddress& bda, uint16_t hci_handle, tHCI_ROLE link_role,
// tBT_TRANSPORT transport Returns: void
struct btm_acl_created {
  std::function<void(const RawAddress& bda, uint16_t hci_handle,
                     tHCI_ROLE link_role, tBT_TRANSPORT transport)>
      body{[](const RawAddress& /* bda */, uint16_t /* hci_handle */,
              tHCI_ROLE /* link_role */, tBT_TRANSPORT /* transport */) { ; }};
  void operator()(const RawAddress& bda, uint16_t hci_handle,
                  tHCI_ROLE link_role, tBT_TRANSPORT transport) {
    body(bda, hci_handle, link_role, transport);
  };
};
extern struct btm_acl_created btm_acl_created;
// Name: btm_acl_device_down
// Params: void
// Returns: void
struct btm_acl_device_down {
  std::function<void(void)> body{[](void) { ; }};
  void operator()(void) { body(); };
};
extern struct btm_acl_device_down btm_acl_device_down;
// Name: btm_acl_disconnected
// Params: tHCI_STATUS status, uint16_t handle, tHCI_REASON reason
// Returns: void
struct btm_acl_disconnected {
  std::function<void(tHCI_STATUS status, uint16_t handle, tHCI_REASON reason)>
      body{[](tHCI_STATUS /* status */, uint16_t /* handle */,
              tHCI_REASON /* reason */) { ; }};
  void operator()(tHCI_STATUS status, uint16_t handle, tHCI_REASON reason) {
    body(status, handle, reason);
  };
};
extern struct btm_acl_disconnected btm_acl_disconnected;
// Name: btm_acl_encrypt_change
// Params: uint16_t handle, uint8_t status, uint8_t encr_enable
// Returns: void
struct btm_acl_encrypt_change {
  std::function<void(uint16_t handle, uint8_t status, uint8_t encr_enable)>
      body{[](uint16_t /* handle */, uint8_t /* status */,
              uint8_t /* encr_enable */) { ; }};
  void operator()(uint16_t handle, uint8_t status, uint8_t encr_enable) {
    body(handle, status, encr_enable);
  };
};
extern struct btm_acl_encrypt_change btm_acl_encrypt_change;
// Name: btm_acl_notif_conn_collision
// Params: const RawAddress& bda
// Returns: void
struct btm_acl_notif_conn_collision {
  std::function<void(const RawAddress& bda)> body{
      [](const RawAddress& /* bda */) { ; }};
  void operator()(const RawAddress& bda) { body(bda); };
};
extern struct btm_acl_notif_conn_collision btm_acl_notif_conn_collision;
// Name: btm_acl_process_sca_cmpl_pkt
// Params: uint8_t len, uint8_t* data
// Returns: void
struct btm_acl_process_sca_cmpl_pkt {
  std::function<void(uint8_t len, uint8_t* data)> body{
      [](uint8_t /* len */, uint8_t* /* data */) { ; }};
  void operator()(uint8_t len, uint8_t* data) { body(len, data); };
};
extern struct btm_acl_process_sca_cmpl_pkt btm_acl_process_sca_cmpl_pkt;
// Name: btm_acl_removed
// Params: uint16_t handle
// Returns: void
struct btm_acl_removed {
  std::function<void(uint16_t handle)> body{[](uint16_t /* handle */) { ; }};
  void operator()(uint16_t handle) { body(handle); };
};
extern struct btm_acl_removed btm_acl_removed;
// Name: btm_acl_role_changed
// Params: tHCI_STATUS hci_status, const RawAddress& bd_addr, tHCI_ROLE
// new_role Returns: void
struct btm_acl_role_changed {
  std::function<void(tHCI_STATUS hci_status, const RawAddress& bd_addr,
                     tHCI_ROLE new_role)>
      body{[](tHCI_STATUS /* hci_status */, const RawAddress& /* bd_addr */,
              tHCI_ROLE /* new_role */) { ; }};
  void operator()(tHCI_STATUS hci_status, const RawAddress& bd_addr,
                  tHCI_ROLE new_role) {
    body(hci_status, bd_addr, new_role);
  };
};
extern struct btm_acl_role_changed btm_acl_role_changed;
// Name: btm_acl_update_conn_addr
// Params: uint16_t handle, const RawAddress& address
// Returns: void
struct btm_acl_update_conn_addr {
  std::function<void(uint16_t handle, const RawAddress& address)> body{
      [](uint16_t /* handle */, const RawAddress& /* address */) { ; }};
  void operator()(uint16_t handle, const RawAddress& address) {
    body(handle, address);
  };
};
extern struct btm_acl_update_conn_addr btm_acl_update_conn_addr;
// Name: btm_ble_refresh_local_resolvable_private_addr
// Params:  const RawAddress& pseudo_addr, const RawAddress& local_rpa
// Returns: void
struct btm_ble_refresh_local_resolvable_private_addr {
  std::function<void(const RawAddress& pseudo_addr,
                     const RawAddress& local_rpa)>
      body{[](const RawAddress& /* pseudo_addr */,
              const RawAddress& /* local_rpa */) { ; }};
  void operator()(const RawAddress& pseudo_addr, const RawAddress& local_rpa) {
    body(pseudo_addr, local_rpa);
  };
};
extern struct btm_ble_refresh_local_resolvable_private_addr
    btm_ble_refresh_local_resolvable_private_addr;
// Name: btm_cont_rswitch_from_handle
// Params: uint16_t hci_handle
// Returns: void
struct btm_cont_rswitch_from_handle {
  std::function<void(uint16_t hci_handle)> body{
      [](uint16_t /* hci_handle */) { ; }};
  void operator()(uint16_t hci_handle) { body(hci_handle); };
};
extern struct btm_cont_rswitch_from_handle btm_cont_rswitch_from_handle;
// Name: btm_establish_continue_from_address
// Params: const RawAddress& bda, tBT_TRANSPORT transport
// Returns: void
struct btm_establish_continue_from_address {
  std::function<void(const RawAddress& bda, tBT_TRANSPORT transport)> body{
      [](const RawAddress& /* bda */, tBT_TRANSPORT /* transport */) { ; }};
  void operator()(const RawAddress& bda, tBT_TRANSPORT transport) {
    body(bda, transport);
  };
};
extern struct btm_establish_continue_from_address
    btm_establish_continue_from_address;
// Name: btm_process_remote_ext_features
// Params: tACL_CONN* p_acl_cb, uint8_t max_page_number
// Returns: void
struct btm_process_remote_ext_features {
  std::function<void(tACL_CONN* p_acl_cb, uint8_t max_page_number)> body{
      [](tACL_CONN* /* p_acl_cb */, uint8_t /* max_page_number */) { ; }};
  void operator()(tACL_CONN* p_acl_cb, uint8_t max_page_number) {
    body(p_acl_cb, max_page_number);
  };
};
extern struct btm_process_remote_ext_features btm_process_remote_ext_features;
// Name: btm_process_remote_version_complete
// Params: uint8_t status, uint16_t handle, uint8_t lmp_version, uint16_t
// manufacturer, uint16_t lmp_subversion Returns: void
struct btm_process_remote_version_complete {
  std::function<void(uint8_t status, uint16_t handle, uint8_t lmp_version,
                     uint16_t manufacturer, uint16_t lmp_subversion)>
      body{[](uint8_t /* status */, uint16_t /* handle */,
              uint8_t /* lmp_version */, uint16_t /* manufacturer */,
              uint16_t /* lmp_subversion */) { ; }};
  void operator()(uint8_t status, uint16_t handle, uint8_t lmp_version,
                  uint16_t manufacturer, uint16_t lmp_subversion) {
    body(status, handle, lmp_version, manufacturer, lmp_subversion);
  };
};
extern struct btm_process_remote_version_complete
    btm_process_remote_version_complete;
// Name: btm_read_automatic_flush_timeout_complete
// Params: uint8_t* p
// Returns: void
struct btm_read_automatic_flush_timeout_complete {
  std::function<void(uint8_t* p)> body{[](uint8_t* /* p */) { ; }};
  void operator()(uint8_t* p) { body(p); };
};
extern struct btm_read_automatic_flush_timeout_complete
    btm_read_automatic_flush_timeout_complete;
// Name: btm_read_failed_contact_counter_complete
// Params: uint8_t* p
// Returns: void
struct btm_read_failed_contact_counter_complete {
  std::function<void(uint8_t* p)> body{[](uint8_t* /* p */) { ; }};
  void operator()(uint8_t* p) { body(p); };
};
extern struct btm_read_failed_contact_counter_complete
    btm_read_failed_contact_counter_complete;
// Name: btm_read_failed_contact_counter_timeout
// Params: void* data
// Returns: void
struct btm_read_failed_contact_counter_timeout {
  std::function<void(void* data)> body{[](void* /* data */) { ; }};
  void operator()(void* data) { body(data); };
};
extern struct btm_read_failed_contact_counter_timeout
    btm_read_failed_contact_counter_timeout;
// Name: btm_read_link_quality_complete
// Params: uint8_t* p
// Returns: void
struct btm_read_link_quality_complete {
  std::function<void(uint8_t* p, uint16_t evt_len)> body{
      [](uint8_t* /* p */, uint16_t /* evt_len */) { ; }};
  void operator()(uint8_t* p, uint16_t evt_len) { body(p, evt_len); };
};
extern struct btm_read_link_quality_complete btm_read_link_quality_complete;
// Name: btm_read_link_quality_timeout
// Params: void* data
// Returns: void
struct btm_read_link_quality_timeout {
  std::function<void(void* data)> body{[](void* /* data */) { ; }};
  void operator()(void* data) { body(data); };
};
extern struct btm_read_link_quality_timeout btm_read_link_quality_timeout;
// Name: btm_read_remote_ext_features
// Params: uint16_t handle, uint8_t page_number
// Returns: void
struct btm_read_remote_ext_features {
  std::function<void(uint16_t handle, uint8_t page_number)> body{
      [](uint16_t /* handle */, uint8_t /* page_number */) { ; }};
  void operator()(uint16_t handle, uint8_t page_number) {
    body(handle, page_number);
  };
};
extern struct btm_read_remote_ext_features btm_read_remote_ext_features;
// Name: btm_read_remote_ext_features_complete
// Params: uint16_t handle, uint8_t page_num, uint8_t max_page, uint8_t*
// features Returns: void
struct btm_read_remote_ext_features_complete {
  std::function<void(uint16_t handle, uint8_t page_num, uint8_t max_page,
                     uint8_t* features)>
      body{[](uint16_t /* handle */, uint8_t /* page_num */,
              uint8_t /* max_page */, uint8_t* /* features */) { ; }};
  void operator()(uint16_t handle, uint8_t page_num, uint8_t max_page,
                  uint8_t* features) {
    body(handle, page_num, max_page, features);
  };
};
extern struct btm_read_remote_ext_features_complete
    btm_read_remote_ext_features_complete;
// Name: btm_read_remote_ext_features_complete_raw
// Params: uint8_t* p, uint8_t evt_len
// Returns: void
struct btm_read_remote_ext_features_complete_raw {
  std::function<void(uint8_t* p, uint8_t evt_len)> body{
      [](uint8_t* /* p */, uint8_t /* evt_len */) { ; }};
  void operator()(uint8_t* p, uint8_t evt_len) { body(p, evt_len); };
};
extern struct btm_read_remote_ext_features_complete_raw
    btm_read_remote_ext_features_complete_raw;
// Name: btm_read_remote_ext_features_failed
// Params: uint8_t status, uint16_t handle
// Returns: void
struct btm_read_remote_ext_features_failed {
  std::function<void(uint8_t status, uint16_t handle)> body{
      [](uint8_t /* status */, uint16_t /* handle */) { ; }};
  void operator()(uint8_t status, uint16_t handle) { body(status, handle); };
};
extern struct btm_read_remote_ext_features_failed
    btm_read_remote_ext_features_failed;
// Name: btm_read_remote_version_complete
// Params: tHCI_STATUS status, uint16_t handle, uint8_t lmp_version, uint16_t
// manufacturer, uint16_t lmp_subversion Returns: void
struct btm_read_remote_version_complete {
  std::function<void(tHCI_STATUS status, uint16_t handle, uint8_t lmp_version,
                     uint16_t manufacturer, uint16_t lmp_subversion)>
      body{[](tHCI_STATUS /* status */, uint16_t /* handle */,
              uint8_t /* lmp_version */, uint16_t /* manufacturer */,
              uint16_t /* lmp_subversion */) { ; }};
  void operator()(tHCI_STATUS status, uint16_t handle, uint8_t lmp_version,
                  uint16_t manufacturer, uint16_t lmp_subversion) {
    body(status, handle, lmp_version, manufacturer, lmp_subversion);
  };
};
extern struct btm_read_remote_version_complete btm_read_remote_version_complete;
// Name: btm_read_rssi_complete
// Params: uint8_t* p
// Returns: void
struct btm_read_rssi_complete {
  std::function<void(uint8_t* p, uint16_t evt_len)> body{
      [](uint8_t* /* pm */, uint16_t /* evt_len */) { ; }};
  void operator()(uint8_t* p, uint16_t evt_len) { body(p, evt_len); };
};
extern struct btm_read_rssi_complete btm_read_rssi_complete;
// Name: btm_read_rssi_timeout
// Params: void* data
// Returns: void
struct btm_read_rssi_timeout {
  std::function<void(void* data)> body{[](void* /* data */) { ; }};
  void operator()(void* data) { body(data); };
};
extern struct btm_read_rssi_timeout btm_read_rssi_timeout;
// Name: btm_read_tx_power_complete
// Params: uint8_t* p, bool is_ble
// Returns: void
struct btm_read_tx_power_complete {
  std::function<void(uint8_t* p, uint16_t evt_len, bool is_ble)> body{
      [](uint8_t* /* p */, uint16_t /* evt_len */, bool /* is_ble */) { ; }};
  void operator()(uint8_t* p, uint16_t evt_len, bool is_ble) {
    body(p, evt_len, is_ble);
  };
};
extern struct btm_read_tx_power_complete btm_read_tx_power_complete;
// Name: btm_read_tx_power_timeout
// Params: void* data
// Returns: void
struct btm_read_tx_power_timeout {
  std::function<void(void* data)> body{[](void* /* data */) { ; }};
  void operator()(void* data) { body(data); };
};
extern struct btm_read_tx_power_timeout btm_read_tx_power_timeout;
// Name: btm_rejectlist_role_change_device
// Params: const RawAddress& bd_addr, uint8_t hci_status
// Returns: void
struct btm_rejectlist_role_change_device {
  std::function<void(const RawAddress& bd_addr, uint8_t hci_status)> body{
      [](const RawAddress& /* bd_addr */, uint8_t /* hci_status */) { ; }};
  void operator()(const RawAddress& bd_addr, uint8_t hci_status) {
    body(bd_addr, hci_status);
  };
};
extern struct btm_rejectlist_role_change_device
    btm_rejectlist_role_change_device;
// Name: btm_set_link_policy
// Params: tACL_CONN* conn, tLINK_POLICY policy
// Returns: void
struct btm_set_link_policy {
  std::function<void(tACL_CONN* conn, tLINK_POLICY policy)> body{
      [](tACL_CONN* /* conn */, tLINK_POLICY /* policy */) { ; }};
  void operator()(tACL_CONN* conn, tLINK_POLICY policy) { body(conn, policy); };
};
extern struct btm_set_link_policy btm_set_link_policy;
// Name: btm_set_packet_types_from_address
// Params: const RawAddress& bd_addr, uint16_t pkt_types
// Returns: void
struct btm_set_packet_types_from_address {
  std::function<void(const RawAddress& bd_addr, uint16_t pkt_types)> body{
      [](const RawAddress& /* bd_addr */, uint16_t /* pkt_types */) { ; }};
  void operator()(const RawAddress& bd_addr, uint16_t pkt_types) {
    body(bd_addr, pkt_types);
  };
};
extern struct btm_set_packet_types_from_address
    btm_set_packet_types_from_address;
// Name: hci_btm_set_link_supervision_timeout
// Params: tACL_CONN& link, uint16_t timeout
// Returns: void
struct hci_btm_set_link_supervision_timeout {
  std::function<void(tACL_CONN& link, uint16_t timeout)> body{
      [](tACL_CONN& /* link */, uint16_t /* timeout */) { ; }};
  void operator()(tACL_CONN& link, uint16_t timeout) { body(link, timeout); };
};
extern struct hci_btm_set_link_supervision_timeout
    hci_btm_set_link_supervision_timeout;
// Name: on_acl_br_edr_connected
// Params: const RawAddress& bda, uint16_t handle, uint8_t enc_mode, bool
// locally_initiated Returns: void
struct on_acl_br_edr_connected {
  std::function<void(const RawAddress& bda, uint16_t handle, uint8_t enc_mode,
                     bool locally_initiated)>
      body{[](const RawAddress& /* bda */, uint16_t /* handle */,
              uint8_t /* enc_mode */, bool /* locally_initiated */) { ; }};
  void operator()(const RawAddress& bda, uint16_t handle, uint8_t enc_mode,
                  bool locally_initiated) {
    body(bda, handle, enc_mode, locally_initiated);
  };
};
extern struct on_acl_br_edr_connected on_acl_br_edr_connected;
// Name: on_acl_br_edr_failed
// Params: const RawAddress& bda, tHCI_STATUS status, bool locally_initiated
// Returns: void
struct on_acl_br_edr_failed {
  std::function<void(const RawAddress& bda, tHCI_STATUS status,
                     bool locally_initiated)>
      body{[](const RawAddress& /* bda */, tHCI_STATUS /* status */,
              bool /* locally_initiated */) { ; }};
  void operator()(const RawAddress& bda, tHCI_STATUS status,
                  bool locally_initiated) {
    body(bda, status, locally_initiated);
  };
};
extern struct on_acl_br_edr_failed on_acl_br_edr_failed;

// Manually added
struct BTM_unblock_role_switch_and_sniff_mode_for {
  std::function<void(const RawAddress& peer_addr)> body{
      [](const RawAddress& /* peer_addr */) {}};
  void operator()(const RawAddress& peer_addr) { body(peer_addr); };
};
extern struct BTM_unblock_role_switch_and_sniff_mode_for
    BTM_unblock_role_switch_and_sniff_mode_for;

}  // namespace stack_acl
}  // namespace mock
}  // namespace test

// END mockcify generation
