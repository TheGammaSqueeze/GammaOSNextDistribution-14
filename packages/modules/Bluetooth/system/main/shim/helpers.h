/*
 * Copyright 2020 The Android Open Source Project
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

#include <vector>

#include "common/init_flags.h"
#include "hci/address_with_type.h"
#include "osi/include/allocator.h"
#include "packet/raw_builder.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/hci_error_code.h"
#include "stack/include/hci_mode.h"
#include "stack/include/hcidefs.h"
#include "types/ble_address_with_type.h"
#include "types/hci_role.h"
#include "types/raw_address.h"

namespace bluetooth {

inline RawAddress ToRawAddress(const hci::Address& address) {
  RawAddress ret;
  ret.address[0] = address.address[5];
  ret.address[1] = address.address[4];
  ret.address[2] = address.address[3];
  ret.address[3] = address.address[2];
  ret.address[4] = address.address[1];
  ret.address[5] = address.address[0];
  return ret;
}

inline hci::Address ToGdAddress(const RawAddress& address) {
  hci::Address ret;
  ret.address[0] = address.address[5];
  ret.address[1] = address.address[4];
  ret.address[2] = address.address[3];
  ret.address[3] = address.address[2];
  ret.address[4] = address.address[1];
  ret.address[5] = address.address[0];
  return ret;
}

inline hci::AddressWithType ToAddressWithType(
    const RawAddress& legacy_address, const tBLE_ADDR_TYPE& legacy_type) {
  hci::Address address = ToGdAddress(legacy_address);

  hci::AddressType type;
  if (legacy_type == BLE_ADDR_PUBLIC)
    type = hci::AddressType::PUBLIC_DEVICE_ADDRESS;
  else if (legacy_type == BLE_ADDR_RANDOM)
    type = hci::AddressType::RANDOM_DEVICE_ADDRESS;
  else if (legacy_type == BLE_ADDR_PUBLIC_ID)
    type = hci::AddressType::PUBLIC_IDENTITY_ADDRESS;
  else if (legacy_type == BLE_ADDR_RANDOM_ID)
    type = hci::AddressType::RANDOM_IDENTITY_ADDRESS;
  else {
    LOG_ALWAYS_FATAL("Bad address type %02x", legacy_type);
    return hci::AddressWithType{address,
                                hci::AddressType::PUBLIC_DEVICE_ADDRESS};
  }

  return hci::AddressWithType{address, type};
}

inline hci::AddressWithType ToAddressWithTypeFromLegacy(
    const tBLE_BD_ADDR& legacy_address_with_type) {
  return ToAddressWithType(legacy_address_with_type.bda,
                           legacy_address_with_type.type);
}

inline tBLE_BD_ADDR ToLegacyAddressWithType(
    const hci::AddressWithType& address_with_type) {
  tBLE_BD_ADDR legacy_address_with_type;
  legacy_address_with_type.bda = ToRawAddress(address_with_type.GetAddress());

  if (address_with_type.GetAddressType() ==
      hci::AddressType::PUBLIC_DEVICE_ADDRESS) {
    legacy_address_with_type.type = BLE_ADDR_PUBLIC;
  } else if (address_with_type.GetAddressType() ==
             hci::AddressType::RANDOM_DEVICE_ADDRESS) {
    legacy_address_with_type.type = BLE_ADDR_RANDOM;
  } else if (address_with_type.GetAddressType() ==
             hci::AddressType::PUBLIC_IDENTITY_ADDRESS) {
    legacy_address_with_type.type = BLE_ADDR_PUBLIC_ID;
  } else if (address_with_type.GetAddressType() ==
             hci::AddressType::RANDOM_IDENTITY_ADDRESS) {
    legacy_address_with_type.type = BLE_ADDR_RANDOM_ID;
  } else {
    LOG_ALWAYS_FATAL("%s Bad address type %02x", __func__,
                     static_cast<uint8_t>(address_with_type.GetAddressType()));
    legacy_address_with_type.type = BLE_ADDR_PUBLIC;
  }
  return legacy_address_with_type;
}

inline std::unique_ptr<bluetooth::packet::RawBuilder> MakeUniquePacket(
    const uint8_t* data, size_t len, bool is_flushable) {
  bluetooth::packet::RawBuilder builder;
  std::vector<uint8_t> bytes(data, data + len);
  auto payload = std::make_unique<bluetooth::packet::RawBuilder>();
  payload->AddOctets(bytes);
  payload->SetFlushable(is_flushable);
  return payload;
}

inline BT_HDR* MakeLegacyBtHdrPacket(
    std::unique_ptr<bluetooth::hci::PacketView<bluetooth::hci::kLittleEndian>>
        packet,
    const std::vector<uint8_t>& preamble) {
  std::vector<uint8_t> packet_vector(packet->begin(), packet->end());
  BT_HDR* buffer = static_cast<BT_HDR*>(
      osi_calloc(packet_vector.size() + preamble.size() + sizeof(BT_HDR)));
  std::copy(preamble.begin(), preamble.end(), buffer->data);
  std::copy(packet_vector.begin(), packet_vector.end(),
            buffer->data + preamble.size());
  buffer->len = preamble.size() + packet_vector.size();
  return buffer;
}

inline tHCI_ROLE ToLegacyRole(hci::Role role) {
  return to_hci_role(static_cast<uint8_t>(role));
}

inline hci::Role ToHciRole(const hci_role_t& role) {
  switch (role) {
    case HCI_ROLE_CENTRAL:
      return hci::Role::CENTRAL;
    case HCI_ROLE_PERIPHERAL:
      return hci::Role::PERIPHERAL;
    default:
      ASSERT_LOG(false, "Unable to determine legacy role:%u", role);
  }
}

inline tHCI_STATUS ToLegacyHciErrorCode(const hci::ErrorCode& reason) {
  switch (reason) {
    case hci::ErrorCode::SUCCESS:
      return HCI_SUCCESS;
    case hci::ErrorCode::UNKNOWN_HCI_COMMAND:
      return HCI_ERR_ILLEGAL_COMMAND;
    case hci::ErrorCode::UNKNOWN_CONNECTION:
      return HCI_ERR_NO_CONNECTION;
    case hci::ErrorCode::HARDWARE_FAILURE:
      return HCI_ERR_HW_FAILURE;
    case hci::ErrorCode::PAGE_TIMEOUT:
      return HCI_ERR_PAGE_TIMEOUT;
    case hci::ErrorCode::AUTHENTICATION_FAILURE:
      return HCI_ERR_AUTH_FAILURE;
    case hci::ErrorCode::PIN_OR_KEY_MISSING:
      return HCI_ERR_KEY_MISSING;
    case hci::ErrorCode::MEMORY_CAPACITY_EXCEEDED:
      return HCI_ERR_MEMORY_FULL;
    case hci::ErrorCode::CONNECTION_TIMEOUT:
      return HCI_ERR_CONNECTION_TOUT;
    case hci::ErrorCode::CONNECTION_LIMIT_EXCEEDED:
      return HCI_ERR_MAX_NUM_OF_CONNECTIONS;
    case hci::ErrorCode::SYNCHRONOUS_CONNECTION_LIMIT_EXCEEDED:
      return HCI_ERR_MAX_NUM_OF_SCOS;
    case hci::ErrorCode::CONNECTION_ALREADY_EXISTS:
      return HCI_ERR_CONNECTION_EXISTS;
    case hci::ErrorCode::COMMAND_DISALLOWED:
      return HCI_ERR_COMMAND_DISALLOWED;
    case hci::ErrorCode::CONNECTION_REJECTED_LIMITED_RESOURCES:
      return HCI_ERR_HOST_REJECT_RESOURCES;
    case hci::ErrorCode::CONNECTION_REJECTED_SECURITY_REASONS:
      return HCI_ERR_HOST_REJECT_SECURITY;
    case hci::ErrorCode::CONNECTION_REJECTED_UNACCEPTABLE_BD_ADDR:
      return HCI_ERR_HOST_REJECT_DEVICE;
    case hci::ErrorCode::CONNECTION_ACCEPT_TIMEOUT:
      return HCI_ERR_HOST_TIMEOUT;
    case hci::ErrorCode::UNSUPPORTED_FEATURE_OR_PARAMETER_VALUE:
      return static_cast<tHCI_STATUS>(
          hci::ErrorCode::UNSUPPORTED_FEATURE_OR_PARAMETER_VALUE);
    case hci::ErrorCode::INVALID_HCI_COMMAND_PARAMETERS:
      return HCI_ERR_ILLEGAL_PARAMETER_FMT;
    case hci::ErrorCode::REMOTE_USER_TERMINATED_CONNECTION:
      return HCI_ERR_PEER_USER;
    case hci::ErrorCode::REMOTE_DEVICE_TERMINATED_CONNECTION_LOW_RESOURCES:
      return static_cast<tHCI_STATUS>(
          hci::ErrorCode::REMOTE_DEVICE_TERMINATED_CONNECTION_LOW_RESOURCES);
    case hci::ErrorCode::REMOTE_DEVICE_TERMINATED_CONNECTION_POWER_OFF:
      return static_cast<tHCI_STATUS>(
          hci::ErrorCode::REMOTE_DEVICE_TERMINATED_CONNECTION_POWER_OFF);
    case hci::ErrorCode::CONNECTION_TERMINATED_BY_LOCAL_HOST:
      return HCI_ERR_CONN_CAUSE_LOCAL_HOST;
    case hci::ErrorCode::REPEATED_ATTEMPTS:
      return HCI_ERR_REPEATED_ATTEMPTS;
    case hci::ErrorCode::PAIRING_NOT_ALLOWED:
      return HCI_ERR_PAIRING_NOT_ALLOWED;
    case hci::ErrorCode::UNKNOWN_LMP_PDU:
      return static_cast<tHCI_STATUS>(hci::ErrorCode::UNKNOWN_LMP_PDU);
    case hci::ErrorCode::UNSUPPORTED_REMOTE_OR_LMP_FEATURE:
      return HCI_ERR_UNSUPPORTED_REM_FEATURE;
    case hci::ErrorCode::SCO_OFFSET_REJECTED:
      return static_cast<tHCI_STATUS>(hci::ErrorCode::SCO_OFFSET_REJECTED);
    case hci::ErrorCode::SCO_INTERVAL_REJECTED:
      return static_cast<tHCI_STATUS>(hci::ErrorCode::SCO_INTERVAL_REJECTED);
    case hci::ErrorCode::SCO_AIR_MODE_REJECTED:
      return static_cast<tHCI_STATUS>(hci::ErrorCode::SCO_AIR_MODE_REJECTED);
    case hci::ErrorCode::INVALID_LMP_OR_LL_PARAMETERS:
      return static_cast<tHCI_STATUS>(
          hci::ErrorCode::INVALID_LMP_OR_LL_PARAMETERS);
    case hci::ErrorCode::UNSPECIFIED_ERROR:
      return HCI_ERR_UNSPECIFIED;
    case hci::ErrorCode::UNSUPPORTED_LMP_OR_LL_PARAMETER:
      return static_cast<tHCI_STATUS>(
          hci::ErrorCode::UNSUPPORTED_LMP_OR_LL_PARAMETER);
    case hci::ErrorCode::ROLE_CHANGE_NOT_ALLOWED:
      return static_cast<tHCI_STATUS>(hci::ErrorCode::ROLE_CHANGE_NOT_ALLOWED);
    case hci::ErrorCode::LINK_LAYER_COLLISION:
      return HCI_ERR_LMP_ERR_TRANS_COLLISION;
    case hci::ErrorCode::ENCRYPTION_MODE_NOT_ACCEPTABLE:
      return HCI_ERR_ENCRY_MODE_NOT_ACCEPTABLE;
    case hci::ErrorCode::ROLE_SWITCH_FAILED:
      return static_cast<tHCI_STATUS>(hci::ErrorCode::ROLE_SWITCH_FAILED);
    case hci::ErrorCode::CONTROLLER_BUSY:
      return static_cast<tHCI_STATUS>(hci::ErrorCode::CONTROLLER_BUSY);
    case hci::ErrorCode::CONNECTION_FAILED_ESTABLISHMENT:
      return HCI_ERR_CONN_FAILED_ESTABLISHMENT;
    case hci::ErrorCode::STATUS_UNKNOWN:
      return HCI_ERR_UNDEFINED;
    default:
      return static_cast<tHCI_REASON>(reason);
  }
}

inline tHCI_MODE ToLegacyHciMode(const hci::Mode& mode) {
  return static_cast<tHCI_MODE>(mode);
}

inline hci::DisconnectReason ToDisconnectReasonFromLegacy(
    const tHCI_STATUS& reason) {
  return static_cast<hci::DisconnectReason>(reason);
}

inline bool IsPacketFlushable(const BT_HDR* p_buf) {
  ASSERT(p_buf != nullptr);
  return ToPacketData<const HciDataPreamble>(p_buf)->IsFlushable();
}

namespace debug {

inline void DumpBtHdr(const BT_HDR* p_buf, const char* token) {
  uint16_t len = p_buf->len;
  char buf[255];
  const uint8_t* data = p_buf->data + p_buf->offset;
  int cnt = 0;
  while (len > 0) {
    memset(buf, 0, sizeof(buf));
    char* pbuf = buf;
    pbuf += sprintf(pbuf, "len:%5u %5d: ", p_buf->len, cnt);
    for (int j = 0; j < 16; j++, --len, data++, cnt++) {
      if (len == 0) break;
      pbuf += sprintf(pbuf, "0x%02x ", *data);
    }
    LOG_DEBUG("%s %s", token, buf);
  }
}

}  // namespace debug
}  // namespace bluetooth
