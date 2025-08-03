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

#pragma once

#include <dice/android.h>
#include <dice/dice.h>
#include <lk/compiler.h>
#include <stddef.h>
#include <stdint.h>
#include <uapi/trusty_uuid.h>

__BEGIN_CDECLS

typedef void* swbcc_session_t;

/**
 * swbcc_glob_init()  - Initialize the global state in hwbcc TA.
 * @code_hash:        - Code hash of the child node in DICE chain.
 * @authority_hash:   - Hash of the key used to verify the signature over the
 *                      code hash of the child node in DICE chain.
 * @FRS:              - A secret (of size: DICE_HIDDEN_SIZE) with factory reset
 *                      life time, which is stored in tamper-evident storage.
 * @child_node_info:  - Information about the child node of Trusty in the DICE
 *                      chain in non-secure world (e.g. ABL). This is required
 *                      to derive DICE artifacts for the child node.
 *
 * Return: 0 on success, or an error code < 0 on failure.
 */
int swbcc_glob_init(const uint8_t FRS[DICE_HIDDEN_SIZE],
                    const uint8_t code_hash[DICE_HASH_SIZE],
                    const uint8_t authority_hash[DICE_HASH_SIZE],
                    const DiceAndroidConfigValues* config_descriptor);

int swbcc_init(swbcc_session_t* s, const struct uuid* client);

void swbcc_close(swbcc_session_t s);

int swbcc_sign_key(swbcc_session_t s,
                   uint32_t test_mode,
                   int32_t cose_algorithm,
                   const uint8_t* key,
                   uint32_t key_size,
                   const uint8_t* aad,
                   size_t aad_size,
                   uint8_t* cose_sign1,
                   size_t cose_sign1_buf_size,
                   size_t* cose_sign1_size);

int swbcc_get_bcc(swbcc_session_t s,
                  uint32_t test_mode,
                  uint8_t* bcc,
                  size_t bcc_buf_size,
                  size_t* bcc_size);

int swbcc_get_dice_artifacts(swbcc_session_t s,
                             uint64_t context,
                             uint8_t* dice_artifacts,
                             size_t dice_artifacts_buf_size,
                             size_t* dice_artifacts_size);

int swbcc_ns_deprivilege(swbcc_session_t s);

__END_CDECLS
