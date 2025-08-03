/******************************************************************************
 *
 * Copyright 2023 The Android Open Source Project
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
#include <string>

#include "macros.h"
#include "types/raw_address.h"

/*****************************************************************************
 *  Constants and data types
 ****************************************************************************/

/* Status Return Value */
typedef enum : uint8_t {
  BTA_SUCCESS = 0, /* Successful operation. */
  BTA_FAILURE = 1, /* Generic failure. */
  BTA_PENDING = 2, /* API cannot be completed right now */
  BTA_BUSY = 3,
  BTA_NO_RESOURCES = 4,
  BTA_WRONG_MODE = 5,
} tBTA_STATUS;

inline std::string bta_status_text(const tBTA_STATUS& status) {
  switch (status) {
    CASE_RETURN_TEXT(BTA_SUCCESS);
    CASE_RETURN_TEXT(BTA_FAILURE);
    CASE_RETURN_TEXT(BTA_PENDING);
    CASE_RETURN_TEXT(BTA_BUSY);
    CASE_RETURN_TEXT(BTA_NO_RESOURCES);
    CASE_RETURN_TEXT(BTA_WRONG_MODE);
    default:
      return base::StringPrintf("UNKNOWN[%d]", status);
  }
}

typedef struct {
  RawAddress pairing_bda;
  RawAddress id_addr;
} tBTA_DM_PROC_ID_ADDR;

typedef struct {
  RawAddress bd_addr;
} tBTA_DM_KEY_MISSING;

namespace fmt {
template <>
struct formatter<tBTA_STATUS> : enum_formatter<tBTA_STATUS> {};
}  // namespace fmt
