/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "ipc.h"

#include <lib/hwkey/hwkey.h>
#include <lib/system_state/system_state.h>
#include <string.h>
#include <uapi/err.h>

/*
 * Shims to replace Trusty components used by block_device_tipc.c when building
 * as a host test.
 */

int system_state_get_flag(enum system_state_flag flag, uint64_t* valuep) {
    switch (flag) {
    case SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED:
        *valuep = 1;
        return 0;
    default:
        return ERR_INVALID_ARGS;
    }
}

long hwkey_derive(hwkey_session_t session,
                  uint32_t* kdf_version,
                  const uint8_t* src,
                  uint8_t* dest,
                  uint32_t buf_size) {
    memset(dest, 0, buf_size);
    return NO_ERROR;
}

int client_create_port(struct ipc_port_context* client_ctx,
                       const char* port_name) {
    return NO_ERROR;
}

int ipc_port_destroy(struct ipc_port_context* ctx) {
    return NO_ERROR;
}
