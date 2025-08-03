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

#include <keymaster/secure_key_storage.h>

namespace keymaster {

class PureSoftSecureStorageMap;

/**
 * This is the pure software emulation of secure key storage.
 */
class PureSoftSecureKeyStorage : public SecureKeyStorage {
  public:
    explicit PureSoftSecureKeyStorage(uint32_t max_slot);
    ~PureSoftSecureKeyStorage() override;

    /**
     * Writes the key blob along with the keyid as the index into pure software emulated secure
     * key storage.
     */
    keymaster_error_t WriteKey(const km_id_t keyid, const KeymasterKeyBlob& blob) override;

    /**
     * Checks if the key blob with key id exists in pure software secure key storage.
     */
    keymaster_error_t KeyExists(const km_id_t keyid, bool* exists) override;

    /**
     * Deletes the key blob with key id from pure software secure key storage.
     */
    keymaster_error_t DeleteKey(const km_id_t keyid) override;

    /**
     * Deletes all the key blob from pure software secure key storage.
     */
    keymaster_error_t DeleteAllKeys() override;

    /**
     * Checks if the pure software secure key storage still has available slot.
     */
    keymaster_error_t HasSlot(bool* has_slot) override;

  private:
    PureSoftSecureStorageMap* pure_soft_secure_storage_map_;
};

}  // namespace keymaster
