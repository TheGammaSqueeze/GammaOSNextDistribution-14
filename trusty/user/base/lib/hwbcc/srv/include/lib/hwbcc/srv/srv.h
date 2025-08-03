/*
 * Copyright 2021, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <lib/tipc/tipc_srv.h>
#include <lk/compiler.h>
#include <stddef.h>
#include <stdint.h>
#include <uapi/trusty_uuid.h>

__BEGIN_CDECLS

/**
 * typedef hwbcc_session_t - Opaque session token.
 *
 * This token is used to identify a HWBCC session and can be used to save
 * session-specific state, e.g. client UUID.
 */
typedef void* hwbcc_session_t;

/**
 * struct hwbcc_ops - HWBCC callbacks
 * @init:     Initializes a new session.
 * @close:    Closes a session previously initialized by @init.
 * @sign_key: Signs a key and returns a COSE_Sign1 message.
 * @get_bcc:  Retrieves the Boot Certificate Chain for the device.
 * @get_dice_artifacts: Retrieves DICE artifacts for
 * a child node in the DICE chain/tree.
 * @ns_deprivilege: Deprivilege hwbcc from serving calls to
 * non-secure clients.
 *
 * Callbacks defined here are meant to be implemented by the "backend" of HWBCC
 * service. See the "frontend" interface for more details:
 * trusty/user/base/lib/hwbcc/client/include/lib/hwbcc/client/hwbcc.h
 */
struct hwbcc_ops {
    int (*init)(hwbcc_session_t* s, const struct uuid* client);
    void (*close)(hwbcc_session_t s);
    int (*sign_key)(hwbcc_session_t s,
                    uint32_t test_mode,
                    int32_t algorithm,
                    const uint8_t* key,
                    uint32_t key_size,
                    const uint8_t* aad,
                    size_t aad_size,
                    uint8_t* cose_sign1,
                    size_t cose_sign1_buf_size,
                    size_t* cose_sign1_size);
    int (*get_bcc)(hwbcc_session_t s,
                   uint32_t test_mode,
                   uint8_t* bcc,
                   size_t bcc_buf_size,
                   size_t* bcc_size);
    int (*get_dice_artifacts)(hwbcc_session_t s,
                              uint64_t context,
                              uint8_t* dice_artifacts,
                              size_t dice_artifacts_buf_size,
                              size_t* dice_artifacts_size);
    int (*ns_deprivilege)(hwbcc_session_t s);
};

/**
 * add_hwbcc_service() - Add HWBCC service.
 * @hset: Handle set created by tipc_hset_create().
 * @ops:  HWBCC operations.
 *
 * The caller should call tipc_run_event_loop() at some point after this call
 * returns.
 *
 * Return: 0 on success, or an error code < 0 on failure.
 */
int add_hwbcc_service(struct tipc_hset* hset, const struct hwbcc_ops* ops);

__END_CDECLS
