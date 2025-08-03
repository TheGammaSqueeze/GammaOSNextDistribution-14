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

#define TLOG_TAG "coverage-srv"

#include "coverage.h"

#include <lib/coverage/common/cov_shm.h>
#include <lib/tipc/tipc_srv.h>
#include <lk/err_ptr.h>
#include <trusty_log.h>

int main(void) {
    int rc;
    struct tipc_hset* hset;
    struct srv_state state;

    hset = tipc_hset_create();
    if (IS_ERR(hset)) {
        TLOGE("failed (%d) to create handle set\n", PTR_ERR(hset));
        return PTR_ERR(hset);
    }
    state.hset = hset;

    list_initialize(&state.coverage_record_list);

    rc = cov_shm_alloc(&state.mailbox, MAX_NUM_APPS);
    if (rc != NO_ERROR) {
        TLOGE("failed to allocate shared memory for mailbox\n");
        return ERR_NO_MEMORY;
    }

    rc = coverage_aggregator_init(&state);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to initialize coverage aggregator service\n", rc);
        return rc;
    }

    rc = coverage_client_init(&state);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to initialize coverage client service\n", rc);
        return rc;
    }

    return tipc_run_event_loop(hset);
}
