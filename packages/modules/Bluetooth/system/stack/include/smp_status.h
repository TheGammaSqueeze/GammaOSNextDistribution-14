/******************************************************************************
 *
 *  Copyright 1999-2012 Broadcom Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#pragma once

#include <base/strings/stringprintf.h>
#include <bluetooth/log.h>

#include <cstdint>

#include "macros.h"

/* pairing failure reason code */
typedef enum : uint8_t {
  SMP_SUCCESS = 0,
  SMP_PASSKEY_ENTRY_FAIL = 0x01,
  SMP_OOB_FAIL = 0x02,
  SMP_PAIR_AUTH_FAIL = 0x03,
  SMP_CONFIRM_VALUE_ERR = 0x04,
  SMP_PAIR_NOT_SUPPORT = 0x05,
  SMP_ENC_KEY_SIZE = 0x06,
  SMP_INVALID_CMD = 0x07,
  SMP_PAIR_FAIL_UNKNOWN = 0x08,
  SMP_REPEATED_ATTEMPTS = 0x09,
  SMP_INVALID_PARAMETERS = 0x0A,
  SMP_DHKEY_CHK_FAIL = 0x0B,
  SMP_NUMERIC_COMPAR_FAIL = 0x0C,
  SMP_BR_PARING_IN_PROGR = 0x0D,
  SMP_XTRANS_DERIVE_NOT_ALLOW = 0x0E,
  SMP_MAX_FAIL_RSN_PER_SPEC = SMP_XTRANS_DERIVE_NOT_ALLOW,

  /* self defined error code */
  SMP_PAIR_INTERNAL_ERR = (SMP_MAX_FAIL_RSN_PER_SPEC + 0x01), /* 0x0F */

  /* Unknown IO capability, unable to decide association model */
  SMP_UNKNOWN_IO_CAP = (SMP_MAX_FAIL_RSN_PER_SPEC + 0x02), /* 0x10 */

  SMP_BUSY = (SMP_MAX_FAIL_RSN_PER_SPEC + 0x05),        /* 0x13 */
  SMP_ENC_FAIL = (SMP_MAX_FAIL_RSN_PER_SPEC + 0x06),    /* 0x14 */
  SMP_STARTED = (SMP_MAX_FAIL_RSN_PER_SPEC + 0x07),     /* 0x15 */
  SMP_RSP_TIMEOUT = (SMP_MAX_FAIL_RSN_PER_SPEC + 0x08), /* 0x16 */

  /* Unspecified failure reason */
  SMP_FAIL = (SMP_MAX_FAIL_RSN_PER_SPEC + 0x0A), /* 0x18 */

  SMP_CONN_TOUT = (SMP_MAX_FAIL_RSN_PER_SPEC + 0x0B),           /* 0x19 */
  SMP_SIRK_DEVICE_INVALID = (SMP_MAX_FAIL_RSN_PER_SPEC + 0x0C), /* 0x1a */
  SMP_USER_CANCELLED = (SMP_MAX_FAIL_RSN_PER_SPEC + 0x0D),   /* 0x1b */
} tSMP_STATUS;

inline std::string smp_status_text(const tSMP_STATUS& status) {
  switch (status) {
    CASE_RETURN_TEXT(SMP_SUCCESS);
    CASE_RETURN_TEXT(SMP_PASSKEY_ENTRY_FAIL);
    CASE_RETURN_TEXT(SMP_OOB_FAIL);
    CASE_RETURN_TEXT(SMP_PAIR_AUTH_FAIL);
    CASE_RETURN_TEXT(SMP_CONFIRM_VALUE_ERR);
    CASE_RETURN_TEXT(SMP_PAIR_NOT_SUPPORT);
    CASE_RETURN_TEXT(SMP_ENC_KEY_SIZE);
    CASE_RETURN_TEXT(SMP_INVALID_CMD);
    CASE_RETURN_TEXT(SMP_PAIR_FAIL_UNKNOWN);
    CASE_RETURN_TEXT(SMP_REPEATED_ATTEMPTS);
    CASE_RETURN_TEXT(SMP_INVALID_PARAMETERS);
    CASE_RETURN_TEXT(SMP_DHKEY_CHK_FAIL);
    CASE_RETURN_TEXT(SMP_NUMERIC_COMPAR_FAIL);
    CASE_RETURN_TEXT(SMP_BR_PARING_IN_PROGR);
    CASE_RETURN_TEXT(SMP_XTRANS_DERIVE_NOT_ALLOW);
    CASE_RETURN_TEXT(SMP_PAIR_INTERNAL_ERR);
    CASE_RETURN_TEXT(SMP_UNKNOWN_IO_CAP);
    CASE_RETURN_TEXT(SMP_BUSY);
    CASE_RETURN_TEXT(SMP_ENC_FAIL);
    CASE_RETURN_TEXT(SMP_STARTED);
    CASE_RETURN_TEXT(SMP_RSP_TIMEOUT);
    CASE_RETURN_TEXT(SMP_FAIL);
    CASE_RETURN_TEXT(SMP_CONN_TOUT);
    CASE_RETURN_TEXT(SMP_SIRK_DEVICE_INVALID);
    CASE_RETURN_TEXT(SMP_USER_CANCELLED);
    default:
      return base::StringPrintf("UNKNOWN[%hhu]", status);
  }
}

namespace fmt {
template <>
struct formatter<tSMP_STATUS> : enum_formatter<tSMP_STATUS> {};
}  // namespace fmt
