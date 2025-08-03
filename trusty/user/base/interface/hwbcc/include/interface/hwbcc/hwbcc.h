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

#include <lk/compiler.h>
#include <stdint.h>

__BEGIN_CDECLS

#define HWBCC_PORT "com.android.trusty.hwbcc"

/**
 * enum hwbcc_cmd - BCC service commands.
 * @HWBCC_CMD_REQ_SHIFT:          Bitshift of the command index.
 * @HWBCC_CMD_RESP_BIT:           Bit indicating that this is a response.
 * @HWBCC_CMD_SIGN_DATA:          Sign the provided data.
 * @HWBCC_CMD_GET_BCC:            Get BCC.
 * @HWBCC_CMD_GET_DICE_ARTIFACTS: Retrieves DICE artifacts for
 *                                a child node in the DICE chain/tree.
 * @HWBCC_CMD_NS_DEPRIVILEGE:     Deprivilege hwbcc from serving calls
 *                                to non-secure clients.
 */
enum hwbcc_cmd {
    HWBCC_CMD_REQ_SHIFT = 1,
    HWBCC_CMD_RESP_BIT = 1,
    HWBCC_CMD_SIGN_DATA = 1 << HWBCC_CMD_REQ_SHIFT,
    HWBCC_CMD_GET_BCC = 2 << HWBCC_CMD_REQ_SHIFT,
    HWBCC_CMD_GET_DICE_ARTIFACTS = 3 << HWBCC_CMD_REQ_SHIFT,
    HWBCC_CMD_NS_DEPRIVILEGE = 4 << HWBCC_CMD_REQ_SHIFT,
};

/**
 * struct hwbcc_req_hdr - Generic header for all hwbcc requests.
 * @cmd:       The command to be run. Commands are described in hwbcc_cmd.
 * @test_mode: Whether or not RKP is making a test request.
 * @context:   Device specific context information passed in by the client.
 *             This is opaque to the generic Trusty code. This is required
 *             to make decisions about device specific behavior in the
 *             implementations of certain hwbcc interface methods. For e.g.
 *             w.r.t get_dice_artifacts, context can supply information
 *             about which secure/non-secure DICE child node is requesting
 *             the dice_artifacts and the implementations can use such
 *             information to derive dice artifacts specific to the
 *             particular child node.
 */
struct hwbcc_req_hdr {
    uint32_t cmd;
    uint32_t test_mode;
    uint64_t context;
};
STATIC_ASSERT(sizeof(struct hwbcc_req_hdr) == 16);

#define HWBCC_MAX_AAD_SIZE 512
#define HWBCC_MAX_DATA_TO_SIGN_SIZE 2048
#define HWBCC_MAX_ENCODED_KEY_SIZE HWBCC_MAX_DATA_TO_SIGN_SIZE
#define HWBCC_MAC_KEY_SIZE 32

/**
 * enum hwbcc_algorithm - Signing algorithm options
 * @HWBCC_ALGORITHM_ED25519: Ed25519
 *
 * We use COSE encodings.
 */
enum hwbcc_algorithm {
    HWBCC_ALGORITHM_ED25519 = -8,
};

/**
 * struct hwbcc_req_sign_data - Request to sign data. Followed by a buffer
 * containing (data || aad)
 * @algorithm: Choice of signing algorithm, one of &enum hwbcc_algorithm.
 * @data_size: Length of payload to be signed. Maximum size is bounded by
 *             HWBCC_MAX_DATA_TO_SIGN_SIZE.
 * @aad_size:  Size of AAD portion of the buffer that follows this struct.
 */
struct hwbcc_req_sign_data {
    int16_t algorithm;
    uint16_t data_size;
    uint32_t aad_size;
};
STATIC_ASSERT(sizeof(struct hwbcc_req_sign_data) == 8);

/**
 * struct hwbcc_resp_hdr - Generic header for all hwbcc requests.
 * @cmd:          Command identifier - %HWBCC_CMD_RSP_BIT or'ed with the command
 *                identifier of the corresponding request.
 * @status:       Whether or not the cmd succeeded, or how it failed.
 * @payload_size: Size of response payload that follows this struct.
 */
struct hwbcc_resp_hdr {
    uint32_t cmd;
    int32_t status;
    uint32_t payload_size;
};
STATIC_ASSERT(sizeof(struct hwbcc_resp_hdr) == 12);

#define HWBCC_MAX_RESP_PAYLOAD_SIZE 3072

__END_CDECLS
