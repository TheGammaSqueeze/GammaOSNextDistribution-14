/*
 * Copyright (C) 2020 The Android Open Source Project
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

#define TLOG_TAG "system_state_server_static"

#include <lib/system_state_server/system_state_server.h>
#include <lib/tipc/tipc_srv.h>
#include <lk/err_ptr.h>
#include <trusty_log.h>
#include <uapi/err.h>

/**
 * system_state_server_get_flag() - Get the value of a system flag
 * @flag:   Identifier for flag to get. One of @enum system_state_flag.
 * @valuep: Pointer to return value in.
 *
 * @flag values %SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED and
 * %SYSTEM_STATE_FLAG_APP_LOADING_UNLOCKED are handled by returning the
 * corresponding %STATIC_SYSTEM_STATE_FLAG_... build flag. This is a simple
 * implementation of the system state server that either always allows or always
 * disallows provisioning and unlocked app loading, respectively. Real devices
 * should base these state flags on fuses, or something similar.
 *
 * Return: 0 on success if @flag is supported, ERR_INVALID_ARGS is @flag is
 * unknown.
 */
int system_state_server_get_flag(uint32_t flag, uint64_t* valuep) {
    switch (flag) {
    case SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED:
        *valuep = STATIC_SYSTEM_STATE_FLAG_PROVISIONING_ALLOWED;
        return 0;
    case SYSTEM_STATE_FLAG_APP_LOADING_UNLOCKED:
        *valuep = STATIC_SYSTEM_STATE_FLAG_APP_LOADING_UNLOCKED;
        return 0;
    case SYSTEM_STATE_FLAG_APP_LOADING_VERSION_CHECK:
        *valuep = STATIC_SYSTEM_STATE_FLAG_APP_LOADING_VERSION_CHECK;
        return 0;
    default:
        return ERR_INVALID_ARGS;
    }
}

int main(void) {
    int rc;
    struct tipc_hset* hset;

    hset = tipc_hset_create();
    if (IS_ERR(hset)) {
        TLOGE("failed (%d) to create handle set\n", PTR_ERR(hset));
        return PTR_ERR(hset);
    }

    rc = add_system_state_service(hset);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to initialize system state service\n", rc);
        return rc;
    }

    return tipc_run_event_loop(hset);
}
