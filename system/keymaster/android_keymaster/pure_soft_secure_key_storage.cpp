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

#include <keymaster/pure_soft_secure_key_storage.h>

#include <vector>

#include <keymaster/logger.h>

namespace keymaster {

class PureSoftSecureStorageMap {
  public:
    explicit PureSoftSecureStorageMap(uint32_t max_size) : max_size_(max_size) {}

    /* Writes key id into pure software secure storage. Returns false if the list has
     * already reached maximum size. */
    bool WriteKey(km_id_t keyid);

    /* Checks if the key id exists in the list. */
    bool KeyExists(km_id_t keyid) const;

    /* Deletes the key id from the list, if it is not existed then do nothing. */
    void DeleteKey(km_id_t keyid);

    /* Deletes all key ids from the list. */
    void DeleteAllKeys();

    /* Checks if there is still available slot to write key. */
    bool HasSlot() const;

  private:
    std::vector<km_id_t> keyid_list_;
    const uint32_t max_size_;
};

bool PureSoftSecureStorageMap::WriteKey(km_id_t keyid) {
    if (keyid_list_.size() >= max_size_) return false;
    keyid_list_.push_back(keyid);
    return true;
}

bool PureSoftSecureStorageMap::KeyExists(km_id_t keyid) const {
    for (km_id_t key_id : keyid_list_)
        if (key_id == keyid) return true;
    return false;
}

void PureSoftSecureStorageMap::DeleteKey(km_id_t keyid) {
    std::vector<km_id_t>::iterator iter;
    for (iter = keyid_list_.begin(); iter != keyid_list_.end();) {
        if (*iter == keyid)
            iter = keyid_list_.erase(iter);
        else
            ++iter;
    }
}

void PureSoftSecureStorageMap::DeleteAllKeys() {
    keyid_list_.clear();
}

bool PureSoftSecureStorageMap::HasSlot() const {
    return keyid_list_.size() < max_size_;
}

PureSoftSecureKeyStorage::PureSoftSecureKeyStorage(uint32_t max_slot)
    : pure_soft_secure_storage_map_(new (std::nothrow) PureSoftSecureStorageMap(max_slot)) {}

PureSoftSecureKeyStorage::~PureSoftSecureKeyStorage() {
    delete pure_soft_secure_storage_map_;
}

keymaster_error_t PureSoftSecureKeyStorage::WriteKey(const km_id_t keyid,
                                                     const KeymasterKeyBlob& /* blob */) {
    if (!pure_soft_secure_storage_map_) {
        LOG_S("Pure software secure key storage table not allocated.");
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    if (!pure_soft_secure_storage_map_->WriteKey(keyid)) {
        LOG_E("Pure software secure key storage slot full.");
        return KM_ERROR_UNKNOWN_ERROR;
    }

    return KM_ERROR_OK;
}

keymaster_error_t PureSoftSecureKeyStorage::KeyExists(const km_id_t keyid, bool* exists) {
    if (!pure_soft_secure_storage_map_) {
        LOG_S("Pure software secure key storage table not allocated.");
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    *exists = pure_soft_secure_storage_map_->KeyExists(keyid);
    return KM_ERROR_OK;
}

keymaster_error_t PureSoftSecureKeyStorage::DeleteKey(const km_id_t keyid) {
    if (!pure_soft_secure_storage_map_) {
        LOG_S("Pure software secure key storage table not allocated.");
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    pure_soft_secure_storage_map_->DeleteKey(keyid);
    return KM_ERROR_OK;
}

keymaster_error_t PureSoftSecureKeyStorage::DeleteAllKeys() {
    if (!pure_soft_secure_storage_map_) {
        LOG_S("Pure software secure key storage table not allocated.");
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    pure_soft_secure_storage_map_->DeleteAllKeys();
    return KM_ERROR_OK;
}

keymaster_error_t PureSoftSecureKeyStorage::HasSlot(bool* has_slot) {
    if (!pure_soft_secure_storage_map_) {
        LOG_S("Pure software secure key storage table not allocated.");
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    *has_slot = pure_soft_secure_storage_map_->HasSlot();
    return KM_ERROR_OK;
}

}  // namespace keymaster
