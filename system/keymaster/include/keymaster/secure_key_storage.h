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

#include <hardware/keymaster_defs.h>

namespace keymaster {

typedef uint64_t km_id_t;
template <typename BlobType> struct TKeymasterBlob;
typedef TKeymasterBlob<keymaster_key_blob_t> KeymasterKeyBlob;

/**
 * This is the reference implementation of secure key storage of Keymaster. It implements
 * key storage on top TEE's secure storage service. All data is stored in the secure hardware,
 * such as RPMB filesystem.
 */
class SecureKeyStorage {
  public:
    SecureKeyStorage() {}
    virtual ~SecureKeyStorage(){};

    /**
     * Writes the key blob into secure key storage and uses the key ID as the index of this
     * key blob. The key ID must be the same id created by KeymasterEnforcement.CreateKeyId,
     * which means the generated id must be stable in that the same key blob bits yield the
     * same keyid.
     */
    virtual keymaster_error_t WriteKey(const km_id_t keyid, const KeymasterKeyBlob& blob) = 0;

    /**
     * Checks if the key blob with key id exists in secure key storage. On success, writes to
     * exists.
     */
    virtual keymaster_error_t KeyExists(const km_id_t keyid, bool* exists) = 0;

    /**
     * Deletes the key blob with key id from secure key storage.
     */
    virtual keymaster_error_t DeleteKey(const km_id_t keyid) = 0;

    /**
     * Deletes all the key blob from secure key storage.
     */
    virtual keymaster_error_t DeleteAllKeys() = 0;

    /**
     * Checks if the secure key storage still has available slot. On success, writes to has_slot.
     */
    virtual keymaster_error_t HasSlot(bool* has_slot) = 0;
};

}  // namespace keymaster
