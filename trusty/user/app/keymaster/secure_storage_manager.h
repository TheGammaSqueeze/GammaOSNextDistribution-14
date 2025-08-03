/*
 * Copyright 2017 The Android Open Source Project
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

#ifndef TRUSTY_APP_KEYMASTER_ACCESS_STORAGE_H_
#define TRUSTY_APP_KEYMASTER_ACCESS_STORAGE_H_

#include <keymaster/UniquePtr.h>
#include <keymaster/android_keymaster_utils.h>
#include <lib/storage/storage.h>

extern "C" {
#include <hardware/keymaster_defs.h>
}
#include "keymaster_attributes.pb.h"
#include "trusty_keymaster_messages.h"

namespace keymaster {

/* The uuid size matches, by design, ATAP_HEX_UUID_LEN in
 * system/iot/attestation/atap. */
static const size_t kAttestationUuidSize = 32;
/* ATAP_PRODUCT_ID_LEN in system/iot/attestation/atap. */
static const size_t kProductIdSize = 16;

static const int kMaxCertChainLength = 3;

// RSA and ECDSA are set to be the same as keymaster_algorithm_t.
enum class AttestationKeySlot {
    kInvalid = 0,
    kRsa = 1,
    kEcdsa = 3,
    kEddsa = 4,
    kEpid = 5,
    // 'Claimable slots are for use with the claim_key HAL method.
    kClaimable0 = 128,
    // 'Som' slots are for Android Things SoM keys. These are generic, that is
    // they are not associated with a particular model or product.
    kSomRsa = 257,
    kSomEcdsa = 259,
    kSomEddsa = 260,
    kSomEpid = 261,
};

struct Certificate {
    uint32_t cert_size;
    UniquePtr<uint8_t[]> cert_data;
};

class SecureStorageManager {
public:
    /**
     * Get a SecureStorageManager instance. The instance returned is shared with
     * all other callers, so it is not safe to call any api that does not commit
     * the transaction and then let other clients use the api. get_instance will
     * also discard any previous transaction to detect if the session is still
     * alive, and to make the starting state more predictable.
     */
    static SecureStorageManager* get_instance(bool translate_format = true);

    /**
     * These functions implement key and certificate chain storage on top
     * Trusty's secure storage service. All data is stored in the RPMB
     * filesystem.
     */

    /**
     * Writes |key_size| bytes at |key| to key/cert file associated with
     * |key_slot|.
     */
    keymaster_error_t WriteKeyToStorage(AttestationKeySlot key_slot,
                                        const uint8_t* key,
                                        uint32_t key_size);

    /**
     * Reads key associated with |key_slot|.
     */
    KeymasterKeyBlob ReadKeyFromStorage(AttestationKeySlot key_slot,
                                        keymaster_error_t* error);

    /**
     * Checks if |key_slot| attestation key exists in RPMB. On success, writes
     * to |exists|.
     */
    keymaster_error_t AttestationKeyExists(AttestationKeySlot key_slot,
                                           bool* exists);

    /**
     * Writes |cert_size| bytes at |cert| to key/cert file associated with
     * |key_slot| and |index|. The caller can either write to an exising
     * certificate entry, or one past the end of the chain to extend the chain
     * length by 1 (|index| = chain length). Fails when |index| > chain length.
     */
    keymaster_error_t WriteCertToStorage(AttestationKeySlot key_slot,
                                         const uint8_t* cert,
                                         uint32_t cert_size,
                                         uint32_t index);

    /**
     * Reads cert chain associated with |key_slot|. Stores certificate chain in
     * |cert_chain| and caller takes ownership of all allocated memory.
     */
    keymaster_error_t ReadCertChainFromStorage(
            AttestationKeySlot key_slot,
            keymaster_cert_chain_t* cert_chain);

    /**
     * Delete cert chain associated with |key_slot|.
     */
    keymaster_error_t DeleteCertChainFromStorage(AttestationKeySlot key_slot);

    /*
     * Writes the new length of the stored |key_slot| attestation certificate
     * chain. If less than the existing certificate chain length, the chain is
     * truncated. Input cannot be larger than the current certificate chain
     * length + 1.
     */
    keymaster_error_t WriteCertChainLength(AttestationKeySlot key_slot,
                                           uint32_t cert_chain_length);

    /**
     * Reads the current length of the stored |key_slot| attestation certificate
     * chain. On success, writes the length to |cert_chain_length|.
     */
    keymaster_error_t ReadCertChainLength(AttestationKeySlot key_slot,
                                          uint32_t* cert_chain_length);

    /**
     * Writes the |attestation_uuid|.
     */
    keymaster_error_t WriteAttestationUuid(
            const uint8_t attestation_uuid[kAttestationUuidSize]);

    /**
     * Reads the |attestation_uuid|. If none exists, sets the uuid to all ascii
     * zeros.
     */
    keymaster_error_t ReadAttestationUuid(
            uint8_t attestation_uuid[kAttestationUuidSize]);

    /**
     * Delete the |attestation_uuid|. This function is for test only.
     */
    keymaster_error_t DeleteAttestationUuid();

    /**
     * Read the |product_id|. If none exists, sets it to all zeros.
     */
    keymaster_error_t ReadProductId(uint8_t product_id[kProductIdSize]);

    /**
     * Set the |product_id|.
     */
    keymaster_error_t SetProductId(const uint8_t product_id[kProductIdSize]);

    /**
     * Clear all attestation IDs for the device.
     */
    keymaster_error_t ClearAttestationIds();

    /**
     * Set the attestation IDs for the device. This function can only be used
     * once unless Keymaster is in debug mode.
     */
    keymaster_error_t SetAttestationIds(
            const SetAttestationIdsRequest& request);

    /**
     * Reads the attestations IDs for the device.
     */
    keymaster_error_t ReadAttestationIds(AttestationIds* attestation_ids_p);

    /* Sets the KeyMint3 Attestation IDs for the device. This function can only
     * be used once unless Keymaster is in debug mode.
     */
    keymaster_error_t SetAttestationIdsKM3(
            const SetAttestationIdsKM3Request& request);

    /**
     * Delete the |product_id|.
     */
    keymaster_error_t DeleteProductId();

    /**
     * Deletes |key_slot| attestation key and associated cert chain from RPMB.
     */
    keymaster_error_t DeleteKey(AttestationKeySlot key_slot, bool commit);

    /**
     * Delete all attestation keys and certificate chains from RPMB.
     */
    keymaster_error_t DeleteAllAttestationData();

#ifdef KEYMASTER_LEGACY_FORMAT

    /**
     * Deprecated, for unit tests only.
     */
    keymaster_error_t LegacyWriteKeyToStorage(AttestationKeySlot key_slot,
                                              const uint8_t* key,
                                              uint32_t key_size);
    /**
     * Deprecated, for unit tests only.
     */
    keymaster_error_t LegacyWriteCertToStorage(AttestationKeySlot key_slot,
                                               const uint8_t* cert,
                                               uint32_t cert_size,
                                               uint32_t index);
    /**
     * Deprecated, for unit tests only.
     */
    keymaster_error_t LegacyWriteAttestationUuid(
            const uint8_t attestation_uuid[kAttestationUuidSize]);
    /**
     * Deprecated, for unit tests only.
     */
    keymaster_error_t LegacySetProductId(
            const uint8_t product_id[kProductIdSize]);
#endif  // #define KEYMASTER_LEGACY_FORMAT

private:
    bool SecureStorageGetFileSize(const char* filename, uint64_t* size);
    bool SecureStorageDeleteFile(const char* filename);
    keymaster_error_t ReadKeymasterAttributes(
            KeymasterAttributes** km_attributes_p);
    keymaster_error_t WriteKeymasterAttributes(
            const KeymasterAttributes* km_attributes,
            bool commit);
    keymaster_error_t WriteAttestationIds(const AttestationIds* attestation_ids,
                                          bool commit);
    keymaster_error_t ReadAttestationKey(AttestationKeySlot key_slot,
                                         AttestationKey** attestation_key_p);
    keymaster_error_t WriteAttestationKey(AttestationKeySlot key_slot,
                                          const AttestationKey* attestation_key,
                                          bool commit);
    keymaster_error_t EncodeToFile(const pb_field_t fields[],
                                   const void* dest_struct,
                                   const char filename[],
                                   bool commit);
    keymaster_error_t DecodeFromFile(const pb_field_t fields[],
                                     void* dest_struct,
                                     const char filename[]);
    /**
     * Translate file format from key/cert per file to new protobuf format.
     */
    keymaster_error_t TranslateLegacyFormat();

    int StorageOpenSession(const char* type);
    void CloseSession();

    SecureStorageManager();
    ~SecureStorageManager();
    storage_session_t session_handle_;

#ifdef KEYMASTER_LEGACY_FORMAT
    keymaster_error_t LegacySecureStorageRead(const char* filename,
                                              void* data,
                                              uint32_t* size,
                                              uint32_t max_size);
    keymaster_error_t LegacySecureStorageWrite(const char* filename,
                                               const uint8_t* data,
                                               uint32_t data_size);
    bool legacy_format = true;
#endif  // #define KEYMASTER_LEGACY_FORMAT
};

}  // namespace keymaster

#endif  // TRUSTY_APP_KEYMASTER_SECURE_STORAGE_H_
