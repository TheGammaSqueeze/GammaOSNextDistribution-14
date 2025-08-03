/*
 * Copyright (C) 2024 The Android Open Source Project
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

#define AVB_CARRIER_LOCK_NAME_MAX_SIZE 256

typedef uint8_t nos2_avb_carrier_lock_name_t[AVB_CARRIER_LOCK_NAME_MAX_SIZE];

/****************************************************************************/
/* The command is sent separately from any data */

enum nos2_avb_cmd {
  NOS2_AVB_CMD_GET_STATE,
  NOS2_AVB_CMD_LOAD,
  NOS2_AVB_CMD_STORE,
  NOS2_AVB_CMD_GET_LOCK,
  NOS2_AVB_CMD_CARRIER_LOCK,
  NOS2_AVB_CMD_CARRIER_UNLOCK,
  NOS2_AVB_CMD_SET_DEVICE_LOCK,
  NOS2_AVB_CMD_SET_BOOT_LOCK,
  NOS2_AVB_CMD_SET_OWNER_LOCK,
  NOS2_AVB_CMD_SET_PRODUCTION,
  NOS2_AVB_CMD_CARRIER_LOCK_TEST,
  NOS2_AVB_CMD_RESET,
  NOS2_AVB_CMD_BOOT_LOADER_DONE,
  NOS2_AVB_CMD_GET_OWNER_KEY,
  NOS2_AVB_CMD_GET_RESET_CHALLENGE,
  NOS2_AVB_CMD_PRODUCTION_RESET_TEST,
  NOS2_AVB_CMD_GET_CARRIER_LOCK_NAME,

  NOS2_AVB_NUM_CMDS
};

/****************************************************************************/
/* Request/Response data. Both are optional and depend on the command. */

/**   NOS2_AVB_CMD_GET_CARRIER_LOCK_NAME */
struct nos2_avb_get_carrier_lock_name_request {
  struct nos2_cmd_hal hal;
};

struct nos2_avb_get_carrier_lock_name_response {
  struct nos2_cmd_hal hal;

  nos2_avb_carrier_lock_name_t name;
};

/****************************************************************************/
#ifdef __cplusplus
}
#endif
