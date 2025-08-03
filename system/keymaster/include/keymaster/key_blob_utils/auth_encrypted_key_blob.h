/*
 * Copyright 2015 The Android Open Source Project
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

#include <hardware/keymaster_defs.h>

#include <keymaster/android_keymaster_utils.h>
#include <keymaster/authorization_set.h>
#include <keymaster/keymaster_utils.h>

namespace keymaster {

class Buffer;
class RandomSource;

// Define the formats this code knows about.  Note that "format" here implies both structure and KEK
// derivation and encryption algorithm, though the KEK derivation and encryption is performed prior
// to serialization.
enum AuthEncryptedBlobFormat : uint8_t {
    AES_OCB = 0,
    AES_GCM_WITH_SW_ENFORCED = 1,
    AES_GCM_WITH_SECURE_DELETION = 2,
    AES_GCM_WITH_SW_ENFORCED_VERSIONED = 3,
    AES_GCM_WITH_SECURE_DELETION_VERSIONED = 4,
};

/**
 * SecureDeletionData provides additional secrets that are mixed into key encryption key derivation
 * for AES_GCM_WITH_SECURE_DELETION key blobs.  Loss of these secrets ensures that the blobs
 * encrypt3ed with keys derived from them cannot be decrypted.
 */
struct SecureDeletionData {
    /**
     * The factory reset secret is intended to be erased and randomly re-generated on every factory
     * reset.  It should provide a large amount of entropy, 256 bits is recommended.
     */
    Buffer factory_reset_secret;

    /**
     * The secure deletion secret is intended to be randomly-generated for every key that requires
     * secure deletion (e.g. KM_TAG_ROLLBACK_RESISTANT and KM_TAG_USAGE_COUNT_LIMIT) and when the
     * key is deleted the secure deletion secret should be securely erased.  It should provide a
     * significant amount of entropy, but this must be balanced against size, since there may be
     * many such secrets that need to be stored.  128 bits is recommended.
     */
    Buffer secure_deletion_secret;

    /**
     * `key_slot` is the secure storage slot in which the `secure_deletion_secret` is found, if any.
     * 0 if unused.
     */
    uint32_t key_slot = 0;
};

struct EncryptedKey {
    AuthEncryptedBlobFormat format;
    KeymasterKeyBlob ciphertext;
    Buffer nonce;
    Buffer tag;
    uint32_t kdf_version;
    int32_t addl_info;
};

struct DeserializedKey {
    EncryptedKey encrypted_key;
    AuthorizationSet hw_enforced;
    AuthorizationSet sw_enforced;
    uint32_t key_slot;
};

/**
 * Encrypt the provided plaintext with format `format`, using the provided authorization lists and
 * master_key to derive the key encryption key.
 *
 * The `secure_deletion_data` argument is used for format AES_GCM_WITH_SECURE_DELETION.  It contains
 * additional high-entropy secrets used in key encryption key derivation which are erased on factory
 * reset and key deletion, respectively.
 */
KmErrorOr<EncryptedKey>
EncryptKey(const KeymasterKeyBlob& plaintext, AuthEncryptedBlobFormat format,
           const AuthorizationSet& hw_enforced, const AuthorizationSet& sw_enforced,
           const AuthorizationSet& hidden, const SecureDeletionData& secure_deletion_data,
           const KeymasterKeyBlob& master_key, const RandomSource& random);

/**
 * Serialize `encrypted_key` (which contains necessary nonce & tag information), along with the
 * associated authorization data into a blob.
 *
 * The `key_slot` is used for format AES_GCM_WITH_SECURE_DELETION. It indicates the slot in the
 * secure deletion file at which a secure deletion key for this encrypted key may be found.  It
 * should be set to zero when unused.
 */
KmErrorOr<KeymasterKeyBlob> SerializeAuthEncryptedBlob(const EncryptedKey& encrypted_key,
                                                       const AuthorizationSet& hw_enforced,
                                                       const AuthorizationSet& sw_enforced,
                                                       uint32_t key_slot);

/**
 * Deserialize a blob, retrieving the key ciphertext, decryption parameters and associated
 * authorization lists.
 */
KmErrorOr<DeserializedKey> DeserializeAuthEncryptedBlob(const KeymasterKeyBlob& key_blob);

/**
 * Decrypt key material from the Deserialized data in `key'.
 */
KmErrorOr<KeymasterKeyBlob> DecryptKey(const DeserializedKey& key, const AuthorizationSet& hidden,
                                       const SecureDeletionData& secure_deletion_data,
                                       const KeymasterKeyBlob& master_key);

bool requiresSecureDeletion(const AuthEncryptedBlobFormat& fmt);

bool isVersionedFormat(const AuthEncryptedBlobFormat& fmt);

}  // namespace keymaster
