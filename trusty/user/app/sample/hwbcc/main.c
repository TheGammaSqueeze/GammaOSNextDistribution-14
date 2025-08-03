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

#define TLOG_TAG "hwbcc-srv-impl"

#include <dice/android/bcc.h>
#include <lib/hwbcc/common/swbcc.h>
#include <lib/hwbcc/srv/srv.h>
#include <lib/tipc/tipc_srv.h>
#include <lk/err_ptr.h>
#include <string.h>
#include <trusty_log.h>
#include <uapi/err.h>

int main(void) {
    int rc;
    struct tipc_hset* hset;
    struct hwbcc_ops ops;

    hset = tipc_hset_create();
    if (IS_ERR(hset)) {
        TLOGE("failed (%d) to create handle set\n", PTR_ERR(hset));
        return PTR_ERR(hset);
    }

    ops.init = swbcc_init;
    ops.close = swbcc_close;
    ops.sign_key = swbcc_sign_key;
    ops.get_bcc = swbcc_get_bcc;
    ops.get_dice_artifacts = swbcc_get_dice_artifacts;
    ops.ns_deprivilege = swbcc_ns_deprivilege;

    uint8_t FRS[DICE_HIDDEN_SIZE];
    /* FRS, code hash and authority hash are set to all zeros in the sample
     * hwbcc app. */
    memset(FRS, 0, DICE_HIDDEN_SIZE);

    uint8_t code_hash[DICE_HASH_SIZE];
    memset(code_hash, 0, DICE_HASH_SIZE);

    uint8_t authority_hash[DICE_HASH_SIZE];
    memset(authority_hash, 0, DICE_HASH_SIZE);

    /* Initialize DICE information of the child node in non-secure world. */
    BccConfigValues config_descriptor = {
            /* TODO: add BCC_INPUT_RESETTABLE when the FRS is reliable */
            .inputs = BCC_INPUT_COMPONENT_NAME | BCC_INPUT_COMPONENT_VERSION,
            .component_name = "ABL",
            .component_version = 1,
    };

    swbcc_glob_init(FRS, code_hash, authority_hash, &config_descriptor);
    rc = add_hwbcc_service(hset, &ops);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to initialize system state service\n", rc);
        return rc;
    }

    return tipc_run_event_loop(hset);
}
