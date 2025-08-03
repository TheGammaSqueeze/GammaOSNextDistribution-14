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
#include <keymaster/secure_deletion_secret_storage.h>

namespace keymaster {

class RandomSource;

class TrustySecureDeletionSecretStorage : public SecureDeletionSecretStorage {
public:
    TrustySecureDeletionSecretStorage(const RandomSource& random)
            : SecureDeletionSecretStorage(random) {}

    std::optional<SecureDeletionData> CreateDataForNewKey(
            bool secure_deletion,
            bool is_upgrade) const override;
    SecureDeletionData GetDataForKey(uint32_t key_slot) const override;
    void DeleteKey(uint32_t key_slot) const override;
    void DeleteAllKeys() const override;

private:
    bool LoadOrCreateFactoryResetSecret(bool wait_for_port) const;

    // Holds the factory reset secret.  If not std::nullopt, also indicates that
    // secure storage has been read successfully at least once.
    mutable std::optional<Buffer> factory_reset_secret_;
};

}  // namespace keymaster
