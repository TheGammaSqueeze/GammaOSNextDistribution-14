/*
 * Copyright (C) 2022 The Android Open Source Project
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
#include <stdbool.h>
#include <stdint.h>
#include <uapi/trusty_uuid.h>

__BEGIN_CDECLS

/**
 * struct manifest_extracts - a subset of the manifest, that may
 *                            influence if app loading is allowed
 * @uuid:                The UUID of the app.
 * @non_critical_app:    Whether the app manifest opted-in to
 *                       NON_CRITICAL_APP.
 * @version:             Application version
 * @min_version:         Application minimum future loadable version.
 * @requires_encryption: Whether the app manifest indicated that the ELF image
 *                       must be protected by encryption.
 */
struct manifest_extracts {
    uuid_t uuid;
    bool non_critical_app;
    uint32_t version;
    uint32_t min_version;
    bool requires_encryption;
};

/**
 * struct apploader_policy_data - Data about the application and package which
 *                                can be used to determine loading eligability.
 * @manifest_extracts:       Extracts from the application package manifest.
 * @public_key:              Pointer to the application package public key.
 * @public_key_size:         Byte length of the public_key.
 * @app_stored_version:      Version of the application from storage for
 *                            rollback protection.
 * @force_store_min_version: If true, the min_verion should be written to
 *                            storage, allowing overriding of anti-rollback.
 */
struct apploader_policy_data {
    struct manifest_extracts manifest_extracts;
    const uint8_t* public_key;
    unsigned int public_key_size;
    uint32_t app_stored_version;
    bool force_store_min_version;
};

/**
 * apploader_policy_engine_get_key() - Retrieves the public key indexed
 *                                     by the key ID, if policy permits.
 * @kid: Key ID.
 * @public_key_ptr: Public key in DER encoding will be stored here, if
 *                  retrieval is successful. If the call is successful,
 *                  the caller should call apploader_policy_engine_put_key()
 *                  on @public_key_ptr to dispose of the key.
 * @public_key_size_ptr: The size of the public key will be stored here, if
 *                       retrieval is successful.
 *
 * Returns: NO_ERROR if key retrieval is successful, assorted error codes
 * otherwise.
 */
int apploader_policy_engine_get_key(uint8_t kid,
                                    const uint8_t** public_key_ptr,
                                    unsigned int* public_key_size_ptr);

/**
 * apploader_policy_engine_put_key() - Dispose of a key that was returned
 *                                     by apploader_policy_engine_get_key().
 * @public_key_ptr: The public key that was returned by a successful call
 *                  to apploader_policy_engine_get_key().
 */
void apploader_policy_engine_put_key(const uint8_t* public_key_ptr);

/**
 * apploader_policy_engine_validate() - Check if app loading is allowed
 *                                      when using the specified apploader
 *                                      policy data fields which includes
 *                                      public key, UUID,
 *                                      NON_CRITICAL_APP and version fields.
 * @data: Information about the application on which loading decisions maybe
 * made.
 *
 * Note this function may modify some aspects of policy_data to alter
 * later loading behaviour e.g. force_store_min_version.
 *
 * Forcing an update of the application version does not override the system
 * state server i.e. system_state_app_loading_skip_version_check() and
 * system_state_app_loading_skip_version_update().
 *
 * Returns: true if app loading is allowed, false otherwise.
 */
bool apploader_policy_engine_validate(struct apploader_policy_data* data);

__END_CDECLS
