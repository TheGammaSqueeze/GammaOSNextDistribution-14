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

#include <interface/hwbcc/hwbcc.h>
#include <lk/compiler.h>
#include <stddef.h>
#include <stdint.h>

__BEGIN_CDECLS

/**
 * hwbcc_get_protected_data() - Retrieves protected data.
 * @test_mode:           Whether or not a to return test values.
 * @cose_algorithm:      COSE encoding of which signing algorithm to use.
 * @data:                Pointer to data.
 * @data_size:           Size of @data.
 * @aad:                 Pointer to AAD.
 * @aad_size:            Size of @aad.
 * @cose_sign1:          Buffer to push the formatted Sign1 msg into.
 * @cose_sign1_buf_size: Size of the buffer.
 * @cose_sign1_size:     Out parameter for actual size of the buffer used.
 * @bcc:                 Pointer to a buffer to store the BCC in.
 * @bcc_buf_size:        Size of the @bcc buffer.
 * @bcc_size:            Actual size of the buffer used.
 *
 * Protected data returned to the client is comprised of two parts:
 * 1. Boot certificate chain (BCC). Client may request test values.
 * 2. COSE_Sign1 message containing the input data signed with either device
 * private key or test key, which is also the leaf in the BCC.
 *
 * Return: 0 on success, or an error code < 0 on failure.
 */
int hwbcc_get_protected_data(uint8_t test_mode,
                             int32_t cose_algorithm,
                             const uint8_t* data,
                             uint32_t data_size,
                             const uint8_t* aad,
                             size_t aad_size,
                             uint8_t* cose_sign1,
                             size_t cose_sign1_buf_size,
                             size_t* cose_sign1_size,
                             uint8_t* bcc,
                             size_t bcc_buf_size,
                             size_t* bcc_size);

/**
 * hwbcc_get_dice_artifacts() - Retrieves DICE artifacts for a child node in the
 * DICE chain/tree.
 * @context:                    Device specific context information passed
 *                              in by the client.
 * @dice_artifacts:             Pointer to a buffer to store the CBOR encoded
 *                              DICE artifacts.
 * @dice_artifacts_buf_size:    Size of the buffer pointed by @dice_artifacts.
 * @dice_artifacts_size:        Actual size of the buffer used.
 *
 * Return: 0 on success, or an error code < 0 on failure.
 */
int hwbcc_get_dice_artifacts(uint64_t context,
                             uint8_t* dice_artifacts,
                             size_t dice_artifacts_buf_size,
                             size_t* dice_artifacts_size);

/**
 * hwbcc_ns_deprivilege() - Deprivileges hwbcc from serving calls to non-secure
 * clients.
 *
 * Return: 0 on success, or an error code < 0 on failure.
 */
int hwbcc_ns_deprivilege(void);

__END_CDECLS
