/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include <interface/hwkey/hwkey.h>
#include <lk/compiler.h>
#include <stdbool.h>
#include <sys/types.h>
#include <uapi/trusty_uuid.h>

struct hwkey_keyslot {
    const char* key_id;
    const uuid_t* uuid;
    const void* priv;
    uint32_t (*handler)(const struct hwkey_keyslot* slot,
                        uint8_t* kbuf,
                        size_t kbuf_len,
                        size_t* klen);
};

/**
 * struct hwkey_derived_keyslot_data - data for a keyslot which derives its key
 * by decrypting a fixed key
 *
 * This slot data is used by hwkey_get_derived_key() which will decrypt the
 * encrypted data using the key from retriever().
 *
 * @encrypted_key_data:
 *     Block-sized IV followed by encrypted key data
 */
struct hwkey_derived_keyslot_data {
    const uint8_t* encrypted_key_data;
    const unsigned int* encrypted_key_size_ptr;
    const void* priv;
    uint32_t (*retriever)(const struct hwkey_derived_keyslot_data* data,
                          uint8_t* kbuf,
                          size_t kbuf_len,
                          size_t* klen);
};

/*
 * Max size (in bytes) of a key returned by &struct
 * hwkey_derived_keyslot_data.retriever
 */
#define HWKEY_DERIVED_KEY_MAX_SIZE 32

#define HWKEY_OPAQUE_HANDLE_SIZE 32
STATIC_ASSERT(HWKEY_OPAQUE_HANDLE_SIZE <= HWKEY_OPAQUE_HANDLE_MAX_SIZE);

/**
 * struct hwkey_opaque_handle_data - Opaque handle data for keyslots that allow
 * opaque usage in hwaes.
 *
 * Intended for use in the @hwkey_keyslot.priv field. The retriever function is
 * equivalent to the generic &hwkey_keyslot->handler but is called only when a
 * service allowed to unwrap opaque requests this handle.
 *
 * @token:             The access token used as an opaque handle to
 *                     reference this keyslot
 * @allowed_uuids:     Array of UUIDs that are allowed to retrieve the
 *                     plaintext key corresponding to an opaque handle
 *                     for this slot
 * @allowed_uuids_len: Length of the @allowed_reader_uuids array
 * @priv:              Opaque pointer to keyslot-specific data
 * @retriever:         Keyslot-specific callback which retrieves the
 *                     actual key corresponding to this opaque handle.
 */
struct hwkey_opaque_handle_data {
    const uuid_t** allowed_uuids;
    size_t allowed_uuids_len;
    const void* priv;
    uint32_t (*retriever)(const struct hwkey_opaque_handle_data* data,
                          uint8_t* kbuf,
                          size_t kbuf_len,
                          size_t* klen);
};

__BEGIN_CDECLS

/**
 * hwkey_get_derived_key() - Return a slot-specific key using the key data from
 * hwkey_derived_keyslot_data
 *
 * Some devices may store a shared encryption key in hardware. However, we do
 * not want to alllow multiple clients to directly use this key, as they would
 * then be able to decrypt each other's data. To solve this, we want to be able
 * to derive unique, client-specific keys from the shared encryption key.
 *
 * To use this handler for key derivation from a common shared key, the
 * encrypting entity should generate a unique, random key for a particular
 * client, then encrypt that unique key using the common shared key resulting in
 * a wrapped, client-specific key. This wrapped key can then be safely embedded
 * in the hwkey service in the &struct
 * hwkey_derived_keyslot_data.encrypted_key_data field and will only be
 * accessible using the shared key which is retrieved via the &struct
 * hwkey_derived_keyslot_data.retriever callback.
 */
uint32_t hwkey_get_derived_key(const struct hwkey_derived_keyslot_data* data,
                               uint8_t* kbuf,
                               size_t kbuf_len,
                               size_t* klen);

/**
 * get_key_handle() - Handler for opaque keys
 *
 * Create and return an access token for a key slot. This key slot must contain
 * a pointer to a &struct hwkey_opaque_handle_data in the &hwkey_keyslot.priv
 * field.
 */
uint32_t get_key_handle(const struct hwkey_keyslot* slot,
                        uint8_t* kbuf,
                        size_t kbuf_len,
                        size_t* klen);

/**
 * get_opaque_key() - Get an opaque key given an access handle
 *
 * @access_token: pointer to an access_token_t
 */
uint32_t get_opaque_key(const uuid_t* uuid,
                        const char* access_token,
                        uint8_t* kbuf,
                        size_t kbuf_len,
                        size_t* klen);

void hwkey_init_srv_provider(void);

void hwkey_install_keys(const struct hwkey_keyslot* keys, unsigned int kcnt);

int hwkey_start_service(void);

bool hwkey_client_allowed(const uuid_t* uuid);

uint32_t derive_key_v1(const uuid_t* uuid,
                       const uint8_t* ikm_data,
                       size_t ikm_len,
                       uint8_t* key_data,
                       size_t key_len);

/**
 * get_current_os_rollback_versions() - Get the current OS rollback version
 * @source: Source of the rollback version, one of &enum
 *          hwkey_rollback_version_source.
 *
 * Return: Negative error code on failure, current rollback version otherwise
 */
int32_t get_current_os_rollback_version(uint32_t source);

/*
 * This sample service supports only the first version element in the
 * rollback_versions array in struct hwkey_derive_versioned_msg.
 */
#define HWKEY_ROLLBACK_VERSION_SUPPORTED_COUNT 1

uint32_t derive_key_versioned_v1(
        const uuid_t* uuid,
        bool shared,
        uint32_t rollback_version_source,
        int32_t rollback_versions[HWKEY_ROLLBACK_VERSION_INDEX_COUNT],
        const uint8_t* context,
        size_t context_len,
        uint8_t* key_data,
        size_t key_len);

__END_CDECLS
