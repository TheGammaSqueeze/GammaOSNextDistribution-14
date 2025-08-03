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

#include <base/strings/stringprintf.h>
#include <bluetooth/log.h>

#include <cstdint>
#include <string>

#include "macros.h"

/*
 *  Definitions for HCI Error Codes that are passed in the events
 */
typedef enum : uint8_t {
  HCI_SUCCESS = 0x00,
  HCI_ERR_ILLEGAL_COMMAND = 0x01,
  HCI_ERR_NO_CONNECTION = 0x02,
  HCI_ERR_HW_FAILURE = 0x03,
  HCI_ERR_PAGE_TIMEOUT = 0x04,
  HCI_ERR_AUTH_FAILURE = 0x05,
  HCI_ERR_KEY_MISSING = 0x06,
  HCI_ERR_MEMORY_FULL = 0x07,
  HCI_ERR_CONNECTION_TOUT = 0x08,
  HCI_ERR_MAX_NUM_OF_CONNECTIONS = 0x09,
  HCI_ERR_MAX_NUM_OF_SCOS = 0x0A,
  HCI_ERR_CONNECTION_EXISTS = 0x0B,
  HCI_ERR_COMMAND_DISALLOWED = 0x0C,
  HCI_ERR_HOST_REJECT_RESOURCES = 0x0D,
  HCI_ERR_HOST_REJECT_SECURITY = 0x0E,
  HCI_ERR_HOST_REJECT_DEVICE = 0x0F,
  HCI_ERR_HOST_TIMEOUT = 0x10,  // stack/btm/btm_ble_gap,
  HCI_ERR_ILLEGAL_PARAMETER_FMT = 0x12,
  HCI_ERR_PEER_USER = 0x13,
  HCI_ERR_REMOTE_LOW_RESOURCE = 0x14,
  HCI_ERR_REMOTE_POWER_OFF = 0x15,
  HCI_ERR_CONN_CAUSE_LOCAL_HOST = 0x16,
  HCI_ERR_REPEATED_ATTEMPTS = 0x17,
  HCI_ERR_PAIRING_NOT_ALLOWED = 0x18,
  HCI_ERR_UNSUPPORTED_REM_FEATURE = 0x1A,  // stack/btm/btm_ble_gap
  HCI_ERR_UNSPECIFIED = 0x1F,
  HCI_ERR_LMP_RESPONSE_TIMEOUT = 0x22,     // GATT_CONN_LMP_TIMEOUT
  HCI_ERR_LMP_ERR_TRANS_COLLISION = 0x23,  // TODO remove
  HCI_ERR_ENCRY_MODE_NOT_ACCEPTABLE = 0x25,
  HCI_ERR_UNIT_KEY_USED = 0x26,
  HCI_ERR_PAIRING_WITH_UNIT_KEY_NOT_SUPPORTED = 0x29,
  HCI_ERR_DIFF_TRANSACTION_COLLISION = 0x2A,  // stack/btm/btm_sec
  HCI_ERR_INSUFFCIENT_SECURITY = 0x2F,        // btif/btu
  HCI_ERR_ROLE_SWITCH_PENDING = 0x32,         // stack/btm/btm_sco
  HCI_ERR_ROLE_SWITCH_FAILED = 0x35,
  HCI_ERR_HOST_BUSY_PAIRING = 0x38,          // stack/btm/btm_sec
  HCI_ERR_UNACCEPT_CONN_INTERVAL = 0x3B,     // stack/l2cap/l2c_ble
  HCI_ERR_ADVERTISING_TIMEOUT = 0x3C,        // stack/btm/btm_ble
  HCI_ERR_CONN_FAILED_ESTABLISHMENT = 0x3E,  // GATT_CONN_FAIL_ESTABLISH
  HCI_ERR_LIMIT_REACHED = 0x43,              // stack/btm/btm_ble_multi_adv.cc

  _HCI_ERR_MAX_ERR = 0x43,
  HCI_ERR_UNDEFINED = 0xff,
} tHCI_ERROR_CODE;

#define HCI_ERR_MAX_ERR _HCI_ERR_MAX_ERR  // HACK for now for SMP

inline std::string hci_error_code_text(const tHCI_ERROR_CODE& error_code) {
  switch (error_code) {
    CASE_RETURN_TEXT(HCI_SUCCESS);
    CASE_RETURN_TEXT(HCI_ERR_ILLEGAL_COMMAND);
    CASE_RETURN_TEXT(HCI_ERR_NO_CONNECTION);
    CASE_RETURN_TEXT(HCI_ERR_HW_FAILURE);
    CASE_RETURN_TEXT(HCI_ERR_PAGE_TIMEOUT);
    CASE_RETURN_TEXT(HCI_ERR_AUTH_FAILURE);
    CASE_RETURN_TEXT(HCI_ERR_KEY_MISSING);
    CASE_RETURN_TEXT(HCI_ERR_MEMORY_FULL);
    CASE_RETURN_TEXT(HCI_ERR_CONNECTION_TOUT);
    CASE_RETURN_TEXT(HCI_ERR_MAX_NUM_OF_CONNECTIONS);
    CASE_RETURN_TEXT(HCI_ERR_MAX_NUM_OF_SCOS);
    CASE_RETURN_TEXT(HCI_ERR_CONNECTION_EXISTS);
    CASE_RETURN_TEXT(HCI_ERR_COMMAND_DISALLOWED);
    CASE_RETURN_TEXT(HCI_ERR_HOST_REJECT_RESOURCES);
    CASE_RETURN_TEXT(HCI_ERR_HOST_REJECT_SECURITY);
    CASE_RETURN_TEXT(HCI_ERR_HOST_REJECT_DEVICE);
    CASE_RETURN_TEXT(HCI_ERR_HOST_TIMEOUT);
    CASE_RETURN_TEXT(HCI_ERR_ILLEGAL_PARAMETER_FMT);
    CASE_RETURN_TEXT(HCI_ERR_PEER_USER);
    CASE_RETURN_TEXT(HCI_ERR_REMOTE_LOW_RESOURCE);
    CASE_RETURN_TEXT(HCI_ERR_REMOTE_POWER_OFF);
    CASE_RETURN_TEXT(HCI_ERR_CONN_CAUSE_LOCAL_HOST);
    CASE_RETURN_TEXT(HCI_ERR_REPEATED_ATTEMPTS);
    CASE_RETURN_TEXT(HCI_ERR_PAIRING_NOT_ALLOWED);
    CASE_RETURN_TEXT(HCI_ERR_UNSUPPORTED_REM_FEATURE);
    CASE_RETURN_TEXT(HCI_ERR_UNSPECIFIED);
    CASE_RETURN_TEXT(HCI_ERR_LMP_RESPONSE_TIMEOUT);
    CASE_RETURN_TEXT(HCI_ERR_LMP_ERR_TRANS_COLLISION);
    CASE_RETURN_TEXT(HCI_ERR_ENCRY_MODE_NOT_ACCEPTABLE);
    CASE_RETURN_TEXT(HCI_ERR_UNIT_KEY_USED);
    CASE_RETURN_TEXT(HCI_ERR_PAIRING_WITH_UNIT_KEY_NOT_SUPPORTED);
    CASE_RETURN_TEXT(HCI_ERR_DIFF_TRANSACTION_COLLISION);
    CASE_RETURN_TEXT(HCI_ERR_INSUFFCIENT_SECURITY);
    CASE_RETURN_TEXT(HCI_ERR_ROLE_SWITCH_PENDING);
    CASE_RETURN_TEXT(HCI_ERR_ROLE_SWITCH_FAILED);
    CASE_RETURN_TEXT(HCI_ERR_HOST_BUSY_PAIRING);
    CASE_RETURN_TEXT(HCI_ERR_UNACCEPT_CONN_INTERVAL);
    CASE_RETURN_TEXT(HCI_ERR_ADVERTISING_TIMEOUT);
    CASE_RETURN_TEXT(HCI_ERR_CONN_FAILED_ESTABLISHMENT);
    CASE_RETURN_TEXT(HCI_ERR_LIMIT_REACHED);
    default:
      return base::StringPrintf("UNKNOWN[0x%02hx]", error_code);
  }
}

// Context equivalence
using tHCI_STATUS = tHCI_ERROR_CODE;
inline std::string hci_status_code_text(const tHCI_STATUS& status_code) {
  return hci_error_code_text(status_code);
}

using tHCI_REASON = tHCI_ERROR_CODE;
inline std::string hci_reason_code_text(const tHCI_REASON& reason_code) {
  return hci_error_code_text(reason_code);
}

// Conversion from raw packet value
inline tHCI_ERROR_CODE to_hci_error_code(const uint8_t& error_code) {
  if (error_code > _HCI_ERR_MAX_ERR) return HCI_ERR_UNDEFINED;
  return static_cast<tHCI_ERROR_CODE>(error_code);
}

inline tHCI_STATUS to_hci_status_code(const uint8_t& status_code) {
  if (status_code > _HCI_ERR_MAX_ERR) return HCI_ERR_UNDEFINED;
  return static_cast<tHCI_STATUS>(status_code);
}

inline tHCI_REASON to_hci_reason_code(const uint8_t& reason_code) {
  if (reason_code > _HCI_ERR_MAX_ERR) return HCI_ERR_UNDEFINED;
  return static_cast<tHCI_REASON>(reason_code);
}

namespace fmt {
template <>
struct formatter<tHCI_ERROR_CODE> : enum_formatter<tHCI_ERROR_CODE> {};
}  // namespace fmt
