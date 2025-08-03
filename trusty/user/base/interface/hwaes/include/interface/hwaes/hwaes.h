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

#include <arch/defines.h>
#include <lk/compiler.h>
#include <stdint.h>

#define HWAES_PORT "com.android.trusty.hwaes"
#define AES_KEY_MAX_SIZE 32
#define AES_BLOCK_SIZE 16
#define HWAES_MAX_NUM_HANDLES 8
#define HWAES_MAX_MSG_SIZE 0x1000
#define HWAES_INVALID_INDEX UINT32_MAX

/* For input buffers it is enough to align by AES_BLOCK_SIZE. */
#define HWAES_TEXT_IN_BUF_ALIGNMENT AES_BLOCK_SIZE

/* For output buffers it should be also CACHE_LINE aligned
 * to avoid cache issues in prepare_dma.
 */
#define HWAES_TEXT_OUT_BUF_ALIGNMENT CACHE_LINE

/*
 * The number of parts on tipc request message:
 * hwaes_req, hwaes_aes_req, array of hwaes_shm_desc,
 * and input payloads for key, iv, aad, aad_padd, text_in, text_in_padd, tag_in.
 */
#define TIPC_REQ_MSG_PARTS (1 + 1 + 1 + 7)

/*
 * The number of parts on tipc response message:
 * hwaes_resp and input payloads for text_out, test_out_padd, tag_out.
 */
#define TIPC_RESP_MSG_PARTS (1 + 3)

/*
 * The max number of TIPC_REQ_MSG_PARTS and TIPC_RESP_MSG_PARTS
 */
#if TIPC_REQ_MSG_PARTS > TIPC_RESP_MSG_PARTS
#define TIPC_MAX_MSG_PARTS TIPC_REQ_MSG_PARTS
#else
#define TIPC_MAX_MSG_PARTS TIPC_RESP_MSG_PARTS
#endif

/**
 * enum hwaes_mode - mode types for hwaes
 * @HWAES_ECB_MODE:      ECB mode.
 * @HWAES_CBC_MODE:      CBC mode.
 * @HWAES_CBC_CTS_MODE:  CBC mode with ciphertext stealing (CTS).
 * @HWAES_CTR_MODE:      CTR mode.
 * @HWAES_GCM_MODE:      GCM mode.
 */
enum hwaes_mode {
    HWAES_ECB_MODE = 0,
    HWAES_CBC_MODE = 1,
    HWAES_CBC_CTS_MODE = 2,
    HWAES_CTR_MODE = 3,
    HWAES_GCM_MODE = 4,
};

/**
 * enum hwaes_padding - padding types for hwaes
 * @HWAES_NO_PADDING:    No padding.
 * @HWAES_PKCS_PADDING:  PKCS padding.
 * @HWAES_CTS_PADDING:   Ciphertext stealing (CTS) padding.
 */
enum hwaes_padding {
    HWAES_NO_PADDING = 0,
    HWAES_PKCS_PADDING = 1,
    HWAES_CTS_PADDING = 2,
};

/**
 * enum hwaes_key_type - key types for hwaes
 * @HWAES_PLAINTEXT_KEY: Plaintext key, directly usable by hardware.
 * @HWAES_OPAQUE_HANDLE: Opaque handle to a key from hwkey service.
 *
 * Opaque handles are created by the hwkey service and provide proxied access to
 * key material that is not directly exposed to the client. The hwaes service
 * will fetch the real key from hwkey when performing a cyptographic operation
 * on behalf of the client.
 */
enum hwaes_key_type {
    HWAES_PLAINTEXT_KEY = 0,
    HWAES_OPAQUE_HANDLE = 1,
};

/**
 * enum hwaes_cmd - command identifiers for hwaes
 * @HWAES_RESP_BIT:   Response bit set as part of response.
 * @HWAES_REQ_SHIFT:  Number of bits used by response bit.
 * @HWAES_AES:        Command to run plain encryption.
 */
enum hwaes_cmd {
    HWAES_RESP_BIT = 1,
    HWAES_REQ_SHIFT = 1,

    HWAES_AES = (1 << HWAES_REQ_SHIFT),
};

/**
 * enum hwaes_err - error codes for hwaes protocol
 * @HWAES_NO_ERROR:             All OK.
 * @HWAES_ERR_GENERIC:          Unknown error. Can occur when there's an
 *                              internal server error, e.g. the server runs out
 *                              of memory or is in a bad state.
 * @HWAES_ERR_INVALID_ARGS:     Arguments are invalid.
 *                              If padding is not enabled, the unaligned data
 *                              length will also cause this error code.
 * @HWAES_ERR_IO:               Protocol error between client lib and server.
 * @HWAES_ERR_BAD_HANDLE:       Fails to map the shared memory through the
 *                              handle.
 * @HWAES_ERR_NOT_IMPLEMENTED:  Requested command or specified parameter is not
 *                              implemented.
 */
enum hwaes_err {
    HWAES_NO_ERROR = 0,
    HWAES_ERR_GENERIC = 1,
    HWAES_ERR_INVALID_ARGS = 2,
    HWAES_ERR_IO = 3,
    HWAES_ERR_BAD_HANDLE = 4,
    HWAES_ERR_NOT_IMPLEMENTED = 5,
};

/**
 * struct hwaes_data_desc - data descriptor for the data transferred between
 *                          client and server.
 * @offset:   The offset of the data.
 *            If the data is transferred through tipc message, it's offset from
 *            the of start of the tipc message. The offset needs to follow the
 *            order of entries in &struct hwaes_aes_req. No padding is allowed
 *            between entries.
 *            Otherwise, it's the offset from the start of the shared memory,
 *            whereby the data is transferred between client and server.
 * @len:      The length of the data.
 * @shm_idx:  The shm_idx is HWAES_INVALID_INDEX if the data is transferred
 *            through tipc message.
 *            Otherwise, it's the index of shared memory handle info array.
 * @reserved: Reserved to make 64 bit alignment, must be 0.
 */
struct hwaes_data_desc {
    uint64_t offset;
    uint64_t len;
    uint32_t shm_idx;
    uint32_t reserved;
};
STATIC_ASSERT(sizeof(struct hwaes_data_desc) == 8 + 8 + 4 + 4);

/**
 * struct hwaes_shm_desc - shared memory descriptor
 * @size:  The size of the shared memory.
 * @write: Flag to indicate whether the shared memory is writeable (value 1)
 *         or not (value 0).
 * @reserved: Reserved to make 64 bit alignment, must be 0.
 */
struct hwaes_shm_desc {
    uint64_t size;
    uint32_t write;
    uint32_t reserved;
};
STATIC_ASSERT(sizeof(struct hwaes_shm_desc) == 8 + 4 + 4);

/**
 * struct hwaes_req - request structure for hwaes
 * @cmd:      Command identifier.
 * @reserved: Reserved to make 64 bit alignment, must be 0.
 */
struct hwaes_req {
    uint32_t cmd;
    uint32_t reserved;
};
STATIC_ASSERT(sizeof(struct hwaes_req) == 4 + 4);

/**
 * struct hwaes_resp - response structure for hwaes
 * @cmd:    Command identifier.
 * @result: Operation result, one of enum hwaes_err.
 */
struct hwaes_resp {
    uint32_t cmd;
    uint32_t result;
};
STATIC_ASSERT(sizeof(struct hwaes_resp) == 4 + 4);

/**
 * struct hwaes_aes_req - request header for HWAES_AES command
 * @key:         The data descriptor for key.
 * @iv:          The data descriptor for IV.
 * @aad:         The data descriptor for AAD.
 * @text_in:     The data descriptor for input text.
 * @tag_in:      The data descriptor for input tag
 * @text_out:    The data descriptor for output text.
 * @tag_out:     The data descriptor for output tag.
 * @key_type:    The key_type, one of instances &enum hwaes_key_type.
 * @padding:     The padding type, one of instances &enum hwaes_padding.
 * @mode:        The AES mode, one of instances &enum hwaes_mode.
 * @num_handles: The number of handles to shared memory.
 *               These handles are transferred from the client to the server.
 * @encrypt:     Flag for encryption (value 1) or decryption (value 0).
 * @reserved:    Reserved to make 64 bit alignment, must be 0.
 *
 * A array of shared memory descriptor follows this header in tipc message.
 * The length of the shm_desc array is equal to num_handles.
 * The order of each &struct hwaes_data_desc entry is the same as the
 * corresponding data in the tipc message.
 */
struct hwaes_aes_req {
    struct hwaes_data_desc key;
    struct hwaes_data_desc iv;
    struct hwaes_data_desc aad;
    struct hwaes_data_desc text_in;
    struct hwaes_data_desc tag_in;
    struct hwaes_data_desc text_out;
    struct hwaes_data_desc tag_out;
    uint32_t key_type;
    uint32_t padding;
    uint32_t mode;
    uint32_t num_handles;
    uint32_t encrypt;
    uint32_t reserved;
};
STATIC_ASSERT(sizeof(struct hwaes_aes_req) ==
              sizeof(struct hwaes_data_desc) * 7 + 4 * 6);
