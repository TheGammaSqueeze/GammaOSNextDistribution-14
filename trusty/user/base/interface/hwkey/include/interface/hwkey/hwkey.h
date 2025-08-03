/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <lk/compiler.h>
#include <stdbool.h>
#include <stdint.h>

#define HWKEY_PORT "com.android.trusty.hwkey"

#define HWKEY_KDF_VERSION_BEST 0
#define HWKEY_KDF_VERSION_1 1

/**
 * HWKEY_OPAQUE_HANDLE_MAX_SIZE: The maximum size of an opaque handle returned
 * by the hwkey service.
 */
#define HWKEY_OPAQUE_HANDLE_MAX_SIZE 128

/* Maximum valid size of a hwkey message, including context or key material. */
#define HWKEY_MAX_MSG_SIZE 2048

/**
 * enum hwkey_cmd - command identifiers for hwkey functions
 */
enum hwkey_cmd {
    HWKEY_RESP_BIT = 1,
    HWKEY_REQ_SHIFT = 1,

    HWKEY_GET_KEYSLOT = (0 << HWKEY_REQ_SHIFT),
    HWKEY_DERIVE = (1 << HWKEY_REQ_SHIFT),

    /*
     * commands for &struct hwkey_derive_versioned_msg
     */
    HWKEY_DERIVE_VERSIONED = (2 << HWKEY_REQ_SHIFT),
};

/**
 * enum hwkey_err - error codes for hwkey protocol
 * @HWKEY_NO_ERROR:             all OK
 * @HWKEY_ERR_GENERIC:          unknown error. Can occur when there's an
 *                              internal server error, e.g. the server runs out
 *                              of memory or is in a bad state.
 * @HWKEY_ERR_NOT_VALID:        input not valid. May occur if the non-buffer
 *                              arguments passed into the command are not valid,
 *                              for example if the KDF version passed to derive
 *                              is not any supported version.
 * @HWKEY_ERR_BAD_LEN:          buffer is unexpected or unaccepted length.
 *                              May occur if received message is not at least
 *                              the length of the header, or if the payload
 *                              length does not meet constraints for the
 *                              function.
 * @HWKEY_ERR_NOT_IMPLEMENTED:  requested command not implemented
 * @HWKEY_ERR_NOT_FOUND:        requested keyslot not found
 * @HWKEY_ERR_ALREADY_EXISTS:   requested opaque handle has already been
 *                              retrieved. Close the connection and reconnect
 *                              to clear this handle and retrieve a new handle.
 */
enum hwkey_err {
    HWKEY_NO_ERROR = 0,
    HWKEY_ERR_GENERIC = 1,
    HWKEY_ERR_NOT_VALID = 2,
    HWKEY_ERR_BAD_LEN = 3,
    HWKEY_ERR_NOT_IMPLEMENTED = 4,
    HWKEY_ERR_NOT_FOUND = 5,
    HWKEY_ERR_ALREADY_EXISTS = 6,
};

/**
 * struct hwkey_msg_header - common header for hwkey messages
 * @cmd:     command identifier
 * @op_id:   operation identifier, set by client and echoed by server.
 *           Used to identify a single operation. Only used if required
 *           by the client.
 * @status:  operation result. Should be set to 0 by client, set to
 *           a enum hwkey_err value by server.
 *
 * Common header shared between &struct hwkey_msg and &struct
 * hwkey_derive_versioned_msg. Which message struct is used depends on the
 * &struct hwkey_msg_header.cmd field, see each message struct for details.
 */
struct hwkey_msg_header {
    uint32_t cmd;
    uint32_t op_id;
    uint32_t status;
} __PACKED;

/**
 * DOC: hwkey protocol
 * -  Client opens channel to the server, then sends one or more
 *    requests and receives replies.
 *
 * -  Client is allowed to keep the channel opened for the duration
 *    of the session.
 *
 * -  Client is allowed to open multiple channels, all such channels
 *    should be treated independently.
 *
 * -  Client is allowed to issue multiple requests over the same channel
 *    and may receive responses in any order. Client must check op_id
 *    to determine corresponding request.
 *
 * - The request and response structure is shared among all API calls.
 *   The data required for each call is as follows:
 *
 * hwkey_get_keyslot:
 *
 * Request:
 * @cmd:     HWKEY_REQ_GET_KEYSLOT
 * @op_id:   client specified operation identifier. Echoed
 *           in response.
 * @status:  must be 0.
 * @arg1:    unused
 * @arg2:    unused
 * @payload: string identifier of requested keyslot, not null-terminated
 *
 * Response:
 * @cmd:     HWKEY_RESP_GET_KEYSLOT
 * @op_id:   echoed from request
 * @status:  operation result, one of enum hwkey_err
 * @arg1:    unused
 * @arg2:    unused
 * @payload: unencrypted keyslot data, or empty on error
 *
 * hwkey_derive:
 *
 * Request:
 * @cmd:     HWKEY_REQ_DERIVE
 * @op_id:   client specified operation identifier. Echoed
 *           in response.
 * @status:  must be 0.
 * @arg1:    requested key derivation function (KDF) version.
 *           Use HWKEY_KDF_VERSION_BEST for best version.
 * @arg2:    unused
 * @payload: seed data for key derivation. Size must be equal
 *           to size of requested key.
 *
 * Response:
 * @cmd:     HWKEY_RESP_DERIVE
 * @op_id:   echoed from request
 * @status:  operation result, one of enum hwkey_err.
 * @arg1:    KDF version used. Always different from request if
 *           request contained HWKEY_KDF_VERSION_BEST.
 * @arg2:    unused
 * @payload: derived key
 */

/**
 * struct hwkey_msg - common request/response structure for hwkey
 * @header:  message header. @header.cmd must be either %HWKEY_GET_KEYSLOT or
 *           %HWKEY_DERIVE (optionally ORed with %HWKEY_RESP_BIT).
 * @arg1:    first argument, meaning determined by command issued.
 *           Must be set to 0 if unused.
 * @arg2:    second argument, meaning determined by command issued
 *           Must be set to 0 if unused.
 * @payload: payload buffer, meaning determined by command issued
 */
struct hwkey_msg {
    struct hwkey_msg_header header;
    uint32_t arg1;
    uint32_t arg2;
    uint8_t payload[0];
};
STATIC_ASSERT(sizeof(struct hwkey_msg) == 20);

/**
 * enum hwkey_rollback_version_source - Trusty rollback version source.
 * @HWKEY_ROLLBACK_COMMITTED_VERSION:
 *     Gate the derived key based on the anti-rollback counter that has been
 *     committed to fuses or stored. A version of Trusty with a version smaller
 *     than this value should never run on the device again. The latest key may
 *     not be available the first few times a new version of Trusty runs on the
 *     device, because the counter may not be committed immediately. This
 *     version source may not allow versions > 0 on some devices (i.e. rollback
 *     versions cannot be committed).
 * @HWKEY_ROLLBACK_RUNNING_VERSION:
 *     Gate the derived key based on the anti-rollback version in the signed
 *     image of Trusty that is currently running. The latest key should be
 *     available immediately, but the Trusty image may be rolled back on a
 *     future boot. Care should be taken that Trusty still works if the image is
 *     rolled back and access to this key is lost. Care should also be taken
 *     that Trusty cannot infer this key if it rolls back to a previous version.
 *     For example, storing the latest version of this key in Trusty’s storage
 *     would allow it to be retrieved after rollback.
 */
enum hwkey_rollback_version_source {
    HWKEY_ROLLBACK_COMMITTED_VERSION = 0,
    HWKEY_ROLLBACK_RUNNING_VERSION = 1,
};

#define HWKEY_ROLLBACK_VERSION_CURRENT (-1)

/**
 * enum hwkey_derived_key_options - Options for derived versioned keys
 * @HWKEY_DEVICE_UNIQUE_KEY_TYPE: A key unique to the device it was derived on.
 *                                This key should never be available outside of
 *                                this device. This key type is the default.
 * @HWKEY_SHARED_KEY_TYPE: A key shared across a family of devices. May not be
 *                         supported on all device families. This derived key
 *                         should be identical on all devices of a particular
 *                         family given identical inputs, if supported.
 *
 * @HWKEY_DEVICE_UNIQUE_KEY_TYPE and @HWKEY_SHARED_KEY_TYPE conflict and cannot
 * both be used at the same time.
 */
enum hwkey_derived_key_options {
    HWKEY_DEVICE_UNIQUE_KEY_TYPE = 0,
    HWKEY_SHARED_KEY_TYPE = 1,
};

/**
 * enum hwkey_rollback_version_indices - Index descriptions for &struct
 *                                       hwkey_derive_versioned_msg.rollback_versions
 * @HWKEY_ROLLBACK_VERSION_OS_INDEX: Index for the Trusty OS rollback version
 *
 * This interface allows up to %HWKEY_ROLLBACK_VERSION_INDEX_COUNT distinct
 * versions, not all of which are currently used. Allowed version types have an
 * allocation index in this enum. We may add additional version gates, e.g., app
 * version.
 */
enum hwkey_rollback_version_indices {
    HWKEY_ROLLBACK_VERSION_OS_INDEX = 0,

    HWKEY_ROLLBACK_VERSION_INDEX_COUNT = 8,
};

/**
 * struct hwkey_derive_versioned_msg - request/response structure for versioned
 *                                     hwkey
 * @header:  message header. @header.cmd must be %HWKEY_DERIVE_VERSIONED
 * @kdf_version: version of the KDF algorithm to use. Use
 *               %HWKEY_KDF_VERSION_BEST for the current best version. Set to
 *               the actual KDF version used in the server response.
 * @rollback_version_source: one of &enum hwkey_kdf_version_source, echoed back
 *                           in the server response.
 * @rollback_versions: versions of the key requested. The version at
 *                     %HWKEY_ROLLBACK_VERSION_OS_INDEX must be less than or
 *                     equal to the current Trusty rollback version. Use
 *                     %HWKEY_ROLLBACK_VERSION_CURRENT for the most recent
 *                     version. Each element set to
 *                     %HWKEY_ROLLBACK_VERSION_CURRENT will be replaced with the
 *                     actual rollback version used for the generated key in the
 *                     server response.
 * @key_options: indicates whether the key should be device-unique or the same
 *               across a family of devices. See &enum hwkey_derived_key_options
 *               for details.
 * @key_len: number of bytes of key material requested, set to the length of
 *           payload in the server response.
 *
 * If @key_options includes %HWKEY_DEVICE_UNIQUE_KEY_TYPE and
 * @rollback_versions[HWKEY_ROLLBACK_VERSION_OS_INDEX] is 0, the service will be
 * backwards compatible and use the same key derivation function as for
 * %HWKEY_DERIVE. This allows a client to migrate away from the old
 * hwkey_derive() API without changing the derived key output. When backwards
 * compatibility is required, @rollback_version_source is ignored and the same
 * key is generated regardless of source, since that parameter is not available
 * in the hwkey_derive() API.
 *
 * If %HWKEY_ROLLBACK_VERSION_CURRENT is provided for the OS rollback version
 * and the current version is 0, compatibility will be provided as if 0 was
 * passed explicitly.
 *
 * We plan to deprecate and remove %HWKEY_DERIVE; on devices that never
 * supported %HWKEY_DERIVE, the versioned derive will not support backwards
 * compatibility.
 *
 * This message header should (optionally) be followed by user-provided context
 * input in requests and will be followed by the derived key material in the
 * response packet.
 */
struct hwkey_derive_versioned_msg {
    struct hwkey_msg_header header;
    uint32_t kdf_version;
    uint32_t rollback_version_source;
    int32_t rollback_versions[HWKEY_ROLLBACK_VERSION_INDEX_COUNT];
    uint32_t key_options;
    uint32_t key_len;
};

/**
 * hwkey_derive_versioned_msg_compatible_with_unversioned() - Should this derive
 * request be handled as if it was a %HWKEY_DERIVE command?
 * @msg: request message
 *
 * Determines if a versioned key derivation request should be implemented to be
 * compatible with the older, unversioned %HWKEY_DERIVE request type.
 *
 * Return: true if this message must return identical key material as the
 * unversioned API.
 */
static inline bool hwkey_derive_versioned_msg_compatible_with_unversioned(
        const struct hwkey_derive_versioned_msg* msg) {
    return msg->rollback_versions[HWKEY_ROLLBACK_VERSION_OS_INDEX] == 0 &&
           (msg->key_options & HWKEY_SHARED_KEY_TYPE) == 0;
}
