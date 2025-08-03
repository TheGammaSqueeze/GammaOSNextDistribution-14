/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifdef __cplusplus
extern "C" {
#endif

#include "application.h"
#include "hals/common.h"

/****************************************************************************/
/* Magic constants
 *
 * Only Acropora knows these numbers. The AP has to ask.
 *
 * It's a pain to create multiple variable-length arrays using strictly correct
 * C, but the Weaver service is in the Nugget OS repo so we can hard-code the
 * sizes here. If it ever changes we'll use the hal.version field to distinguish
 * which one we're using.
 *
 * Still, we want to match the AIDL definitions as closely as possible, to
 * make our code easier to understand and maintain.
 */
#define NOS2_WEAVER_NUM_SLOTS 64
#define NOS2_WEAVER_KEY_BYTES (128 / 8)
#define NOS2_WEAVER_VALUE_BYTES (128 / 8)
static_assert((NOS2_WEAVER_KEY_BYTES & 0x4) == 0,
              "NOS2_WEAVER_KEY_BYTES is not a multiple of 4");
static_assert((NOS2_WEAVER_VALUE_BYTES & 0x4) == 0,
              "NOS2_WEAVER_VALUE_BYTES is not a multiple of 4");

typedef uint8_t nos2_weaver_key_t[NOS2_WEAVER_KEY_BYTES];
typedef uint8_t nos2_weaver_value_t[NOS2_WEAVER_VALUE_BYTES];

/****************************************************************************/
/* The command is sent separately from any data */

enum nos2_weaver_cmd {
  NOS2_WEAVER_GET_CONFIG,
  NOS2_WEAVER_WRITE,
  NOS2_WEAVER_READ,
  NOS2_WEAVER_ERASE_VALUE,

  NOS2_WEAVER_NUM_CMDS
};

/****************************************************************************/
/* Request/Response data. Both are optional and depend on the command. */

/** NOS2_WEAVER_GET_CONFIG */
/* There is no struct nos2_weaver_get_config_request */
struct nos2_weaver_get_config_response {
  struct nos2_cmd_hal hal;

  uint32_t slots;
  uint32_t key_size;
  uint32_t value_size;
};

/** NOS2_WEAVER_WRITE */
struct nos2_weaver_write_request {
  struct nos2_cmd_hal hal;

  uint32_t slot_id;
  nos2_weaver_key_t key;
  nos2_weaver_value_t value;
};
/* There is no struct nos2_weaver_write_response */

/** NOS2_WEAVER_READ */
struct nos2_weaver_read_request {
  struct nos2_cmd_hal hal;

  uint32_t slot_id;
  nos2_weaver_key_t key;
};

enum nos2_weaver_read_status {
    NOS2_WEAVER_READ_STATUS_OK,
    NOS2_WEAVER_READ_STATUS_FAILED,
    NOS2_WEAVER_READ_STATUS_INCORRECT_KEY,
    NOS2_WEAVER_READ_STATUS_THROTTLE,
};

struct nos2_weaver_read_response {
  struct nos2_cmd_hal hal;

  uint32_t timeout;
  uint32_t status;  /* enum nos2_weaver_read_status, but of specified size */
  /* Put potentially variable-length members at the end. It's NOT, though */
  nos2_weaver_value_t value;
};

/** NOS2_WEAVER_ERASE_VALUE */
struct nos2_weaver_erase_request {
  struct nos2_cmd_hal hal;

  uint32_t slot_id;
};
/* There is no struct nos2_weaver_erase_response */

/****************************************************************************/
#ifdef __cplusplus
}
#endif
