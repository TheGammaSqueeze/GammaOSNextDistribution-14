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

#include <optional>

#include <keymaster/key_blob_utils/auth_encrypted_key_blob.h>

namespace keymaster {

class RandomSource;

/**
 * SecureDeletionSecretStorage stores secure deletion secrets for KeyMint keys.  These secrets are
 * mixed into the key encryption key derivation process, so once the secure deletion secrets
 * associated with a key blob are destroyed, the key blob can never be decrypted again.
 */
class SecureDeletionSecretStorage {
  public:
    explicit SecureDeletionSecretStorage(const RandomSource& random) : random_(random) {}
    virtual ~SecureDeletionSecretStorage() {}

    /**
     * Create secure deletion data for a new key, and return it.
     *
     * If `secure_deletion` is true, a random key is generated and stored in an unused key slot, and
     * the key slot is returned.  If no unused key slot exists or if `secure_deletion` is false, the
     * returned `key_slot` is zero, indicating that secure deletion is not available for the new
     * key.
     *
     * If `secure_deletion` and `is_upgrade` are both true, the random key will be stored in an
     * "upgrade-only" slot, if no normal slots are available.  The upgrade-only slots reduce the
     * probability that upgrading blobs can lose secure deletion.
     *
     * Whether or not secure deletion is requested, this method must read secure storage to obtain
     * the factory reset secret.  This read may fail for one of three reasons:
     *
     * 1.  Secure storage is not yet available.  In this case the return value is std::nullopt.
     *
     * 2.  Secure storage is available, but the secure deletion data file doesn't exist.  In this
     *     case the method creates the file, generates and stores the factory reset secret (and
     *     possibly secure deletion secret, if requested), and returns a populated result.
     *
     * 3.  Secure storage is not available but was available previously.  In this case the method
     *     blocks until secure storage is available, possibly forever, then processes the request
     *     and returns a populated result.
     *
     * @retval is empty if no secure deletion data (factory reset or per-key) is available.
     *
     *         If the return value is not empty, the secureDeletionData field contains data that can
     *         be used for key derivation.  If the keySlot field is 0, the key does not have secure
     *         deletion support.
     */
    virtual std::optional<SecureDeletionData> CreateDataForNewKey(bool secure_deletion,
                                                                  bool is_upgrade) const = 0;

    /**
     * Get the secure deletion data for a key.
     *
     * If the `key_slot` argument is non-zero, this method will retrieve data from the specified
     * slot and return it in the secure_deletion_secret field, otherwise the `key_slot` field will
     * be an empty buffer.  Whether `key_slot` is zero or not, this method will populate the
     * factory_reset_secret field.
     *
     * This method blocks until secure storage can be read.  Possibly forever.
     */
    virtual SecureDeletionData GetDataForKey(uint32_t key_slot) const = 0;

    /**
     * Delete the secure deletion data in a key slot.
     */
    virtual void DeleteKey(uint32_t key_slot) const = 0;

    /**
     * Deletes the secure deletion data file, deleting all secure deletion secrets and the factory
     * reset secret.
     */
    virtual void DeleteAllKeys() const = 0;

  protected:
    const RandomSource& random_;
};

}  // namespace keymaster
