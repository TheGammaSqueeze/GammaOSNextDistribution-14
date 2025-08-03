/*
 * Copyright (C) 2014-2015 The Android Open Source Project
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
#include <sys/types.h>

#include <interface/hwkey/hwkey.h>
#include <trusty_ipc.h>

__BEGIN_CDECLS

typedef handle_t hwkey_session_t;

/**
 * hwkey_open() - Opens a trusty hwkey session.
 *
 * Return: a hwkey_session_t > 0 on success, * or an error code < 0 on
 * failure.
 */
long hwkey_open(void);

/**
 * hwkey_get_keyslot_data() - Gets the keyslot data referenced by slot_id.
 * @session:    session handle retrieved from hwkey_open
 * @slot_id:    string identifier for the requested keyslot
 * @data:       buffer for retrieved data
 * @data_size:  pointer to allocated size of data buffer. Updated to actual
 *              retrieved size if different from allocated size.
 *
 * Fills *data with result if size is sufficient. If actual size is less than
 * data_size, data_size is updated with the * actual returned size.
 *
 * Return: NO_ERROR on success, error code less than 0 on error. Possible error
 * codes include:
 * - ERR_NOT_VALID: if input is NULL
 * - ERR_IO: if there's an issue communicating with the server
 * - ERR_TOO_BIG: if keyslot does not fit in data buffer
 * - ERR_NOT_FOUND: if keyslot is not found
 */
long hwkey_get_keyslot_data(hwkey_session_t session,
                            const char* slot_id,
                            uint8_t* data,
                            uint32_t* data_size);

/**
 * hwkey_derive() - Derives a cryptographic key based on input values.
 * @session:        session handle retrieved from hwkey_open
 * @kdf_version:    key derivation function version. If most recent supported
 *                  version is desired, pass HWKEY_KDF_VERSION_BEST. Actual KDF
 *                  used is written to field as out param.
 * @src:            the input for the KDF (key-derivation function)
 * @dest:           The result buffer into which the derived key is written.
 *                  Must be at least *src_buf_len bytes.
 * @buf_size:       The size of src and dest.
 *
 * Return: NO_ERROR on success, error code less than 0 on error. Possible error
 * codes include:
 * - ERR_NOT_VALID: if input is NULL or if kdf_version is not supported
 * - ERR_IO: if there's an issue communicating with the server
 * - ERR_BAD_LEN: if buf_size is not valid
 *
 */
long hwkey_derive(hwkey_session_t session,
                  uint32_t* kdf_version,
                  const uint8_t* src,
                  uint8_t* dest,
                  uint32_t buf_size);

/**
 * struct hwkey_versioned_key_options - Options that control how a versioned
 *                                      key will be derived.
 * @kdf_version:
 *     (in/out) the version of the KDF to use. If set to %HWKEY_KDF_VERSION_BEST
 *     the latest version will be used and will be written back to the struct.
 * @shared_key:
 *     if true, the derived key will be consistent and shared across the entire
 *     family of devices, given the same input. If false, the derived key will
 *     be unique to the particular device it was derived on.
 * @rollback_version_source:
 *     specifies whether the @rollback_version must have been committed. If
 *     %HWKEY_ROLLBACK_COMMITTED_VERSION is specified, the system must guarantee
 *     that software with a lower rollback version cannot ever run on a future
 *     boot. (see &enum hwkey_rollback_version_source)
 * @os_rollback_version:
 *     (in/out) the OS rollback version to be incorporated into the key
 *     derivation. Must be less than or equal to the current Trusty OS rollback
 *     version from @rollback_version_source. If set to
 *     %HWKEY_ROLLBACK_VERSION_CURRENT the latest available version will be used
 *     and will be written back to the struct.
 * @context:
 *     an arbitrary set of bytes incorporated into the key derivation. May have
 *     an implementation-specific maximum length, but it is guaranteed to accept
 *     at least 32 bytes. May not be null unless @key_len is zero.
 * @context_len:
 *     length of @context. May not be zero unless @key_len is also zero.
 * @key:
 *     destination of the derived key. Contains the derived key on success. May
 *     have an implementation-specific maximum length, but it is guaranteed to
 *     produce at least 32 bytes. May be null if @key_len is zero.
 * @key_len:
 *     length of the @key buffer. @key_len bytes will be derived or an error
 *     will be returned. May be zero to query the current OS rollback version
 *     without deriving a key.
 *
 * Default field semantics (i.e. if zeroed):
 * * @kdf_version: %HWKEY_KDF_VERSION_BEST, version used will be passed back in
 *                 this field.
 * * @shared_key: Device-unique key
 * * @rollback_version_source: %HWKEY_ROLLBACK_COMMITTED_VERSION
 * * @os_rollback_version: Version 0
 * * @context: Null, i.e. no user-supplied context will be added. If null,
 *             @context_len and @key_len must be 0.
 * * @context_len: 0
 * * @key: Null, i.e. no key will be generated, only the current versions may be
 *         queried. If null, @key_len must be 0.
 * * @key_len: 0
 *
 * Additional fields may be added in the future, e.g. rollback versions for
 * additional system components. Additional future fields will not change the
 * resulting key when set to zero, so they will not affect existing key
 * derivations as long as any unspecified fields in the struct are zeroed.
 */
struct hwkey_versioned_key_options {
    uint32_t kdf_version;
    bool shared_key;
    enum hwkey_rollback_version_source rollback_version_source;
    int32_t os_rollback_version;
    const uint8_t* context;
    size_t context_len;
    uint8_t* key;
    size_t key_len;
};

/**
 * hwkey_derive_versioned() - Derive a versioned, device-specific key from
 *                            provided context.
 * @session: session handle retrieved from hwkey_open
 * @args:    arguments controlling key derivation, see &enum
 *           hwkey_derive_versioned_key_args.
 *
 * Derives a versioned key from provided context input. Gates access to keys
 * based on the rollback version of Trusty, so that an app may not derive keys
 * for future rollback versions. The intent is that after an update, a previous,
 * potentially compromised app cannot derive keys tied to the newly updated
 * rollback version. These new keys may then be used to re-secure the device
 * after the vulnerability has been fixed without danger of unpatched software
 * being able to forward compromise the device.
 *
 * Changing any of the input parameters should result in a completely different
 * key being derived. You will need to keep the parameters stable for
 * compatibility with previously generated keys. You may need to prefix wrapped
 * data blobs with the parameters used for key derivation (os_rollback_version,
 * for example) so they can be unwrapped correctly. One exception is that
 * different values of key_len may not change all of the derived key material. A
 * shorter key may be the prefix of a longer key.
 *
 * Apps with different UUIDs will derive different keys, even with the same
 * parameters.
 *
 * If @args->shared_key is false, a key unique to the specific device it was
 * derived on will be generated. A device unique key should be considered the
 * secure default and used if at all possible.
 *
 * If @args->shared_key is true, this function will generated the same key when
 * invoked with the same input parameters across devices in the same family.
 * This kind of key is useful when it is necessary to agree on a shared secret
 * with a remote server and there is no channel to transfer the secret, e.g.,
 * encryption key for OTAed data. Shared keys may not be available on all
 * platforms, and are generally less secure than local keys. The definition of a
 * device family is vendor-specific. If possible, set @args->shared_key to
 * false.
 *
 * If @args->shared_key is false and @args->os_rollback_version is 0, this
 * function will be backwards compatible with the key derivation in
 * hwkey_derive(). This allows a client to migrate away from the old
 * hwkey_derive() API without changing the derived key output. When backwards
 * compatibility is required, @rollback_version_source is ignored and the same
 * key is generated regardless of source, since that parameter is not available
 * in the hwkey_derive() API.
 *
 * If @args->os_rollback_version is %HWKEY_ROLLBACK_VERSION_CURRENT and the
 * current version is 0, compatibility will be provided as if 0 was passed
 * explicitly.
 *
 * We plan to deprecate and remove hwkey_derive(); on devices that never
 * supported hwkey_derive(), the versioned derive API will not support backwards
 * compatibility.
 *
 * Return: NO_ERROR on success, error code less than 0 on error. Possible error
 * codes (see &enum hwkey_err):
 * * ERR_NOT_VALID - invalid parameters
 * * ERR_BAD_LEN   - a buffer size was invalid
 * * ERR_NOT_IMPLEMENTED - the requested version source or KDF mode is
 *   not implemented
 * * ERR_NOT_FOUND - requested key version does not exist
 */
long hwkey_derive_versioned(hwkey_session_t session,
                            struct hwkey_versioned_key_options* args);

/**
 * hwkey_close() - Closes the session.
 */
void hwkey_close(hwkey_session_t session);

__END_CDECLS
