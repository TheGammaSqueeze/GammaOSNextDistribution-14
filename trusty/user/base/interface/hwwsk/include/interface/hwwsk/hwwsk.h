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

#include <stdint.h>

/**
 * DOC: Theory of operation
 *
 * The Hardware wrapped Storage Key IPC interface (HWWSK) is designed to
 * operate in conjunction with Keymaster application implementing support for
 * AES keys having TAG_STORAGE_KEY attribute set.
 *
 * This interface provides a facility to implement such support in a hardware
 * dependant way without making modification to Keymaster app itself. When
 * support for HWWSK service is enabled for Keymaster app, the Keymaster
 * requests to generate/import/export AES key with TAG_STORAGE_KEY will be
 * redirected to this interface to produce opaque key blob which will be in
 * turn wrapped by Keymaster.
 *
 * This interface supports the following commands:
 *
 * @HWWSK_GENERATE_KEY: to create a new hardware wrapped key blob containing
 * HWWSK key. The resulting key blob must be persistent, it is suitable to be
 * stored offline and should reusable across reset.
 *
 * @HWWSK_EXPORT_KEY: convert the persistent HWWSK key obtained by invoking
 * %HWWSK_GENERATE_KEY command to format suitable for loading into some sort of
 * HW key slot by appropriate SW component. It is beyond the scope of this
 * interface how such transformation is performed or how the resulting key blob
 * is delivered to SW component that is capable to load it into HW block. It is
 * not expected that resulting key blob has persistent property, as a matter of
 * fact it is highly desirable to design this format and transformation in such
 * way that it is only good for current session and becomes invalid across
 * reboots.
 *
 * The client interact with service implementing this interface by sending IPC
 * message over connection opened to %HWWSK_PORT then waiting for and receiving
 * response message. All commands defined by this interface follow the same
 * structure. The message starts with common  &struct hwwsk_req_hdr header,
 * followed by (optional depending on command) request specific header,
 * followed by request specific data. The server sends reply  message started
 * with &struct hwwsk_rsp_hdr header followed by command specify opaque key
 * blob.
 */

/* Port name for HWWSK service */
#define HWWSK_PORT "com.android.trusty.hwwsk"

/* Max message size supported by this service */
#define HWWSK_MAX_MSG_SIZE 1024

/**
 * enum hwwsk_cmd - command ID
 *
 * @HWWSK_CMD_RESP: added to cmd field of &struct hwwsk_rsp_hdr structure
 * when sending response message.
 *
 * @HWWSK_CMD_GENERATE_KEY: creates new persistent hardware wrapped storage key
 * by either creating new random key or importing (mostly for test purpose)
 * caller specified raw key data. The server should expect a request message in
 * the following format: the message starts with &struct hwwsk_req_hdr header
 * followed by &struct hwwsk_generate_key_req header followed by raw key
 * data for import operation or no data for create operation. The server shall
 * send a response message in the following format: the message starts with
 * &struct hwwsk_rsp_hdr header followed by created blob. The server should
 * only send blob if operation is successful.
 *
 * @HWWSK_CMD_EXPORT_KEY: converts specified persistent HWWSK key to format
 * suitable for loading into underlying hardware block. The server should
 * expect a request in the following format: the message starts with &struct
 * hwwsk_req_hdr header followed by blob of data previously obtained by
 * @HWWSK_CMD_GENERATE_KEY command. The server shall send a response message
 * in the following format: the message starts with &struct hwwsk_rsp_hdr
 * header followed by created blob.
 */
enum hwwsk_cmd {
    HWWSK_CMD_RESP = (1U << 31),
    HWWSK_CMD_GENERATE_KEY = 1,
    HWWSK_CMD_EXPORT_KEY = 2,
};

/**
 * enum hwwsk_err - error codes for HWWSK protocol
 * @HWWSK_NO_ERROR: no error
 * @HWWSK_ERR_GENERIC: unknown error. Can occur when there's an  internal
 *                     server error, e.g. the server runs out  of memory or is
 *                     in a bad state.
 * @HWWSK_ERR_INVALID_ARGS: an invalid command or command parameter specified
 * @HWWSK_ERR_BAD_LEN: unexpected or unaccepted buffer or data length.
 * @HWWSK_ERR_NOT_SUPPORTED: requested command or specified parameter is not
 *                           supported
 */
enum hwwsk_err {
    HWWSK_NO_ERROR = 0,
    HWWSK_ERR_GENERIC = 1,
    HWWSK_ERR_INVALID_ARGS = 2,
    HWWSK_ERR_BAD_LEN = 3,
    HWWSK_ERR_NOT_SUPPORTED = 4,
};

/**
 * enum hwwsk_key_flags - additional key attributes
 * @HWWSK_FLAGS_ROLLBACK_RESISTANCE: indicates that resulting key must be
 *                                   rollback resistant
 *
 * A combinations of flags defined here can be passed to @HWWSK_GENERATE_KEY
 * request to specify additional properties of generated key blob. The
 * underlying implementation must return an error if specified property is not
 * supported.
 */
enum hwwsk_key_flags {
    HWWSK_FLAGS_ROLLBACK_RESISTANCE = (0x1 << 0),
};

/**
 * struct hwwsk_req_hdr - common header for all HWWSK requests
 * @cmd: one of @enum hwwsk_cmd values (excluding HWWSK_CMD_RESP)
 * @flags: reserved should be 0
 */
struct hwwsk_req_hdr {
    uint32_t cmd;
    uint32_t flags;
};

/**
 * struct hwwsk_rsp_hdr - common header for all HWWSK responses
 * @cmd: command server is replying to with HWWSK_CMD_RESP bit set
 * @status: one of &enum hwwsk_err value indicating command execution result
 */
struct hwwsk_rsp_hdr {
    uint32_t cmd;
    uint32_t status;
};

/**
 * struct hwwsk_generate_key_req - generate HWWSK key request
 * @key_size: underlying key size (in bits) to generate
 * @key_flags: a combination of &enum hwwsk_key_flags specifying
 *             additional properties of generated key.
 */
struct hwwsk_generate_key_req {
    uint32_t key_size;
    uint32_t key_flags;
};
