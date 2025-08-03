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

/*****************************************************************************
 *  Constants
 ****************************************************************************/

/* Success code and error codes */
typedef enum : uint16_t {
  SDP_SUCCESS = 0x0000,
  SDP_INVALID_VERSION = 0x0001,
  SDP_INVALID_SERV_REC_HDL = 0x0002,
  SDP_INVALID_REQ_SYNTAX = 0x0003,
  SDP_INVALID_PDU_SIZE = 0x0004,
  SDP_INVALID_CONT_STATE = 0x0005,
  SDP_NO_RESOURCES = 0x0006,
  SDP_DI_REG_FAILED = 0x0007,
  SDP_DI_DISC_FAILED = 0x0008,
  SDP_NO_DI_RECORD_FOUND = 0x0009,
  SDP_ERR_ATTR_NOT_PRESENT = 0x000A,
  SDP_ILLEGAL_PARAMETER = 0x000B,

  HID_SDP_NO_SERV_UUID = (SDP_ILLEGAL_PARAMETER + 1),
  HID_SDP_MANDATORY_MISSING,

  SDP_NO_RECS_MATCH = 0xFFF0,
  SDP_CONN_FAILED = 0xFFF1,
  SDP_CFG_FAILED = 0xFFF2,
  SDP_GENERIC_ERROR = 0xFFF3,
  SDP_DB_FULL = 0xFFF4,
  SDP_CANCEL = 0xFFF8,
} tSDP_STATUS;
using tSDP_RESULT = tSDP_STATUS;
using tSDP_REASON = tSDP_STATUS;

inline std::string sdp_status_text(const tSDP_STATUS& status) {
  switch (status) {
    CASE_RETURN_TEXT(SDP_SUCCESS);
    CASE_RETURN_TEXT(SDP_INVALID_VERSION);
    CASE_RETURN_TEXT(SDP_INVALID_SERV_REC_HDL);
    CASE_RETURN_TEXT(SDP_INVALID_REQ_SYNTAX);
    CASE_RETURN_TEXT(SDP_INVALID_PDU_SIZE);
    CASE_RETURN_TEXT(SDP_INVALID_CONT_STATE);
    CASE_RETURN_TEXT(SDP_NO_RESOURCES);
    CASE_RETURN_TEXT(SDP_DI_REG_FAILED);
    CASE_RETURN_TEXT(SDP_DI_DISC_FAILED);
    CASE_RETURN_TEXT(SDP_NO_DI_RECORD_FOUND);
    CASE_RETURN_TEXT(SDP_ERR_ATTR_NOT_PRESENT);
    CASE_RETURN_TEXT(SDP_ILLEGAL_PARAMETER);

    CASE_RETURN_TEXT(HID_SDP_NO_SERV_UUID);
    CASE_RETURN_TEXT(HID_SDP_MANDATORY_MISSING);

    CASE_RETURN_TEXT(SDP_NO_RECS_MATCH);
    CASE_RETURN_TEXT(SDP_CONN_FAILED);
    CASE_RETURN_TEXT(SDP_CFG_FAILED);
    CASE_RETURN_TEXT(SDP_GENERIC_ERROR);
    CASE_RETURN_TEXT(SDP_DB_FULL);
    CASE_RETURN_TEXT(SDP_CANCEL);
    default:
      return base::StringPrintf("UNKNOWN[%hu]", status);
  }
}
const auto sdp_result_text = sdp_status_text;

namespace fmt {
template <>
struct formatter<tSDP_STATUS> : enum_formatter<tSDP_STATUS> {};
}  // namespace fmt
