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

#include <keymaster/contexts/pure_soft_keymaster_context.h>

#include <assert.h>
#include <memory>
#include <utility>

#include <openssl/aes.h>
#include <openssl/evp.h>
#include <openssl/hmac.h>
#include <openssl/rand.h>
#include <openssl/sha.h>
#include <openssl/x509v3.h>

#include <keymaster/android_keymaster_utils.h>
#include <keymaster/key_blob_utils/auth_encrypted_key_blob.h>
#include <keymaster/key_blob_utils/integrity_assured_key_blob.h>
#include <keymaster/key_blob_utils/ocb_utils.h>
#include <keymaster/key_blob_utils/software_keyblobs.h>
#include <keymaster/km_openssl/aes_key.h>
#include <keymaster/km_openssl/asymmetric_key.h>
#include <keymaster/km_openssl/attestation_utils.h>
#include <keymaster/km_openssl/certificate_utils.h>
#include <keymaster/km_openssl/ec_key_factory.h>
#include <keymaster/km_openssl/hmac_key.h>
#include <keymaster/km_openssl/openssl_err.h>
#include <keymaster/km_openssl/openssl_utils.h>
#include <keymaster/km_openssl/rsa_key_factory.h>
#include <keymaster/km_openssl/soft_keymaster_enforcement.h>
#include <keymaster/km_openssl/triple_des_key.h>
#include <keymaster/logger.h>
#include <keymaster/operation.h>
#include <keymaster/wrapped_key.h>

#include <keymaster/contexts/soft_attestation_cert.h>

namespace keymaster {

PureSoftKeymasterContext::PureSoftKeymasterContext(KmVersion version,
                                                   keymaster_security_level_t security_level)

    : SoftAttestationContext(version),
      rsa_factory_(new (std::nothrow) RsaKeyFactory(*this /* blob_maker */, *this /* context */)),
      ec_factory_(new (std::nothrow) EcKeyFactory(*this /* blob_maker */, *this /* context */)),
      aes_factory_(new (std::nothrow)
                       AesKeyFactory(*this /* blob_maker */, *this /* random_source */)),
      tdes_factory_(new (std::nothrow)
                        TripleDesKeyFactory(*this /* blob_maker */, *this /* random_source */)),
      hmac_factory_(new (std::nothrow)
                        HmacKeyFactory(*this /* blob_maker */, *this /* random_source */)),
      os_version_(0), os_patchlevel_(0), soft_keymaster_enforcement_(64, 64),
      security_level_(security_level) {
    // We're pretending to be some sort of secure hardware which supports secure key storage,
    // this must only be used for testing.
    if (security_level != KM_SECURITY_LEVEL_SOFTWARE) {
        pure_soft_secure_key_storage_ = std::make_unique<PureSoftSecureKeyStorage>(64);
    }
    if (version >= KmVersion::KEYMINT_1) {
        pure_soft_remote_provisioning_context_ =
            std::make_unique<PureSoftRemoteProvisioningContext>(security_level_);
    }
}

PureSoftKeymasterContext::~PureSoftKeymasterContext() {}

keymaster_error_t PureSoftKeymasterContext::SetSystemVersion(uint32_t os_version,
                                                             uint32_t os_patchlevel) {
    os_version_ = os_version;
    os_patchlevel_ = os_patchlevel;
    if (pure_soft_remote_provisioning_context_ != nullptr) {
        pure_soft_remote_provisioning_context_->SetSystemVersion(os_version, os_patchlevel);
    }
    return KM_ERROR_OK;
}

void PureSoftKeymasterContext::GetSystemVersion(uint32_t* os_version,
                                                uint32_t* os_patchlevel) const {
    *os_version = os_version_;
    *os_patchlevel = os_patchlevel_;
}

keymaster_error_t
PureSoftKeymasterContext::SetVerifiedBootInfo(std::string_view boot_state,
                                              std::string_view bootloader_state,
                                              const std::vector<uint8_t>& vbmeta_digest) {
    if (verified_boot_state_.has_value() && boot_state != verified_boot_state_.value()) {
        return KM_ERROR_INVALID_ARGUMENT;
    }
    if (bootloader_state_.has_value() && bootloader_state != bootloader_state_.value()) {
        return KM_ERROR_INVALID_ARGUMENT;
    }
    if (vbmeta_digest_.has_value() && vbmeta_digest != vbmeta_digest_.value()) {
        return KM_ERROR_INVALID_ARGUMENT;
    }
    verified_boot_state_ = boot_state;
    bootloader_state_ = bootloader_state;
    vbmeta_digest_ = vbmeta_digest;
    if (pure_soft_remote_provisioning_context_ != nullptr) {
        pure_soft_remote_provisioning_context_->SetVerifiedBootInfo(boot_state, bootloader_state,
                                                                    vbmeta_digest);
    }
    return KM_ERROR_OK;
}

keymaster_error_t PureSoftKeymasterContext::SetVendorPatchlevel(uint32_t vendor_patchlevel) {
    if (vendor_patchlevel_.has_value() && vendor_patchlevel != vendor_patchlevel_.value()) {
        // Can't set patchlevel to a different value.
        return KM_ERROR_INVALID_ARGUMENT;
    }
    vendor_patchlevel_ = vendor_patchlevel;
    if (pure_soft_remote_provisioning_context_ != nullptr) {
        pure_soft_remote_provisioning_context_->SetVendorPatchlevel(vendor_patchlevel);
    }
    return KM_ERROR_OK;
}

keymaster_error_t PureSoftKeymasterContext::SetBootPatchlevel(uint32_t boot_patchlevel) {
    if (boot_patchlevel_.has_value() && boot_patchlevel != boot_patchlevel_.value()) {
        // Can't set patchlevel to a different value.
        return KM_ERROR_INVALID_ARGUMENT;
    }
    boot_patchlevel_ = boot_patchlevel;
    if (pure_soft_remote_provisioning_context_ != nullptr) {
        pure_soft_remote_provisioning_context_->SetBootPatchlevel(boot_patchlevel);
    }
    return KM_ERROR_OK;
}

KeyFactory* PureSoftKeymasterContext::GetKeyFactory(keymaster_algorithm_t algorithm) const {
    switch (algorithm) {
    case KM_ALGORITHM_RSA:
        return rsa_factory_.get();
    case KM_ALGORITHM_EC:
        return ec_factory_.get();
    case KM_ALGORITHM_AES:
        return aes_factory_.get();
    case KM_ALGORITHM_TRIPLE_DES:
        return tdes_factory_.get();
    case KM_ALGORITHM_HMAC:
        return hmac_factory_.get();
    default:
        return nullptr;
    }
}

static keymaster_algorithm_t supported_algorithms[] = {KM_ALGORITHM_RSA, KM_ALGORITHM_EC,
                                                       KM_ALGORITHM_AES, KM_ALGORITHM_HMAC};

keymaster_algorithm_t*
PureSoftKeymasterContext::GetSupportedAlgorithms(size_t* algorithms_count) const {
    *algorithms_count = array_length(supported_algorithms);
    return supported_algorithms;
}

OperationFactory* PureSoftKeymasterContext::GetOperationFactory(keymaster_algorithm_t algorithm,
                                                                keymaster_purpose_t purpose) const {
    KeyFactory* key_factory = GetKeyFactory(algorithm);
    if (!key_factory) return nullptr;
    return key_factory->GetOperationFactory(purpose);
}

keymaster_error_t PureSoftKeymasterContext::CreateKeyBlob(const AuthorizationSet& key_description,
                                                          const keymaster_key_origin_t origin,
                                                          const KeymasterKeyBlob& key_material,
                                                          KeymasterKeyBlob* blob,
                                                          AuthorizationSet* hw_enforced,
                                                          AuthorizationSet* sw_enforced) const {
    // Check whether the key blob can be securely stored by pure software secure key storage.
    bool canStoreBySecureKeyStorageIfRequired = false;
    if (GetSecurityLevel() != KM_SECURITY_LEVEL_SOFTWARE &&
        pure_soft_secure_key_storage_ != nullptr) {
        pure_soft_secure_key_storage_->HasSlot(&canStoreBySecureKeyStorageIfRequired);
    }

    bool needStoreBySecureKeyStorage = false;
    if (key_description.GetTagValue(TAG_ROLLBACK_RESISTANCE)) {
        needStoreBySecureKeyStorage = true;
        if (!canStoreBySecureKeyStorageIfRequired) return KM_ERROR_ROLLBACK_RESISTANCE_UNAVAILABLE;
    }

    if (GetSecurityLevel() != KM_SECURITY_LEVEL_SOFTWARE) {
        // We're pretending to be some sort of secure hardware.  Put relevant tags in hw_enforced.
        for (auto& entry : key_description) {
            switch (entry.tag) {
            case KM_TAG_PURPOSE:
            case KM_TAG_ALGORITHM:
            case KM_TAG_KEY_SIZE:
            case KM_TAG_RSA_PUBLIC_EXPONENT:
            case KM_TAG_BLOB_USAGE_REQUIREMENTS:
            case KM_TAG_DIGEST:
            case KM_TAG_PADDING:
            case KM_TAG_BLOCK_MODE:
            case KM_TAG_MIN_SECONDS_BETWEEN_OPS:
            case KM_TAG_MAX_USES_PER_BOOT:
            case KM_TAG_USER_SECURE_ID:
            case KM_TAG_NO_AUTH_REQUIRED:
            case KM_TAG_AUTH_TIMEOUT:
            case KM_TAG_CALLER_NONCE:
            case KM_TAG_MIN_MAC_LENGTH:
            case KM_TAG_KDF:
            case KM_TAG_EC_CURVE:
            case KM_TAG_ECIES_SINGLE_HASH_MODE:
            case KM_TAG_USER_AUTH_TYPE:
            case KM_TAG_ORIGIN:
            case KM_TAG_OS_VERSION:
            case KM_TAG_OS_PATCHLEVEL:
            case KM_TAG_EARLY_BOOT_ONLY:
            case KM_TAG_UNLOCKED_DEVICE_REQUIRED:
            case KM_TAG_RSA_OAEP_MGF_DIGEST:
            case KM_TAG_ROLLBACK_RESISTANCE:
                hw_enforced->push_back(entry);
                break;
            case KM_TAG_USAGE_COUNT_LIMIT:
                // Enforce single use key with usage count limit = 1 into secure key storage.
                if (canStoreBySecureKeyStorageIfRequired && entry.integer == 1) {
                    needStoreBySecureKeyStorage = true;
                    hw_enforced->push_back(entry);
                }
                break;
            default:
                break;
            }
        }
    }

    keymaster_error_t error =
        SetKeyBlobAuthorizations(key_description, origin, os_version_, os_patchlevel_, hw_enforced,
                                 sw_enforced, GetKmVersion());
    if (error != KM_ERROR_OK) return error;
    error =
        ExtendKeyBlobAuthorizations(hw_enforced, sw_enforced, vendor_patchlevel_, boot_patchlevel_);
    if (error != KM_ERROR_OK) return error;

    AuthorizationSet hidden;
    error = BuildHiddenAuthorizations(key_description, &hidden, softwareRootOfTrust);
    if (error != KM_ERROR_OK) return error;

    error = SerializeIntegrityAssuredBlob(key_material, hidden, *hw_enforced, *sw_enforced, blob);
    if (error != KM_ERROR_OK) return error;

    // Pretend to be some sort of secure hardware that can securely store the key blob.
    if (!needStoreBySecureKeyStorage) return KM_ERROR_OK;
    km_id_t keyid;
    if (!soft_keymaster_enforcement_.CreateKeyId(*blob, &keyid)) return KM_ERROR_UNKNOWN_ERROR;
    assert(needStoreBySecureKeyStorage && canStoreBySecureKeyStorageIfRequired);
    return pure_soft_secure_key_storage_->WriteKey(keyid, *blob);
}

keymaster_error_t PureSoftKeymasterContext::UpgradeKeyBlob(const KeymasterKeyBlob& key_to_upgrade,
                                                           const AuthorizationSet& upgrade_params,
                                                           KeymasterKeyBlob* upgraded_key) const {
    UniquePtr<Key> key;
    keymaster_error_t error = ParseKeyBlob(key_to_upgrade, upgrade_params, &key);
    if (error != KM_ERROR_OK) return error;

    return FullUpgradeSoftKeyBlob(key, os_version_, os_patchlevel_, vendor_patchlevel_,
                                  boot_patchlevel_, upgrade_params, upgraded_key);
}

keymaster_error_t PureSoftKeymasterContext::ParseKeyBlob(const KeymasterKeyBlob& blob,
                                                         const AuthorizationSet& additional_params,
                                                         UniquePtr<Key>* key) const {
    // This is a little bit complicated.
    //
    // The SoftKeymasterContext has to handle a lot of different kinds of key blobs.
    //
    // 1.  New keymaster1 software key blobs.  These are integrity-assured but not encrypted.  The
    //     raw key material and auth sets should be extracted and returned.  This is the kind
    //     produced by this context when the KeyFactory doesn't use keymaster0 to back the keys.
    //
    // 2.  Old keymaster1 software key blobs.  These are OCB-encrypted with an all-zero master key.
    //     They should be decrypted and the key material and auth sets extracted and returned.
    //
    // 3.  Old keymaster0 software key blobs.  These are raw key material with a small header tacked
    //     on the front.  They don't have auth sets, so reasonable defaults are generated and
    //     returned along with the raw key material.
    //
    // Determining what kind of blob has arrived is somewhat tricky.  What helps is that
    // integrity-assured and OCB-encrypted blobs are self-consistent and effectively impossible to
    // parse as anything else.  Old keymaster0 software key blobs have a header.  It's reasonably
    // unlikely that hardware keys would have the same header.  So anything that is neither
    // integrity-assured nor OCB-encrypted and lacks the old software key header is assumed to be
    // keymaster0 hardware.

    AuthorizationSet hw_enforced;
    AuthorizationSet sw_enforced;
    KeymasterKeyBlob key_material;
    keymaster_error_t error;

    auto constructKey = [&, this]() mutable -> keymaster_error_t {
        // GetKeyFactory
        if (error != KM_ERROR_OK) return error;
        keymaster_algorithm_t algorithm;
        if (!hw_enforced.GetTagValue(TAG_ALGORITHM, &algorithm) &&
            !sw_enforced.GetTagValue(TAG_ALGORITHM, &algorithm)) {
            return KM_ERROR_INVALID_ARGUMENT;
        }

        // Pretend to be some sort of secure hardware that can securely store
        // the key blob. Check the key blob is still securely stored now.
        if (hw_enforced.Contains(KM_TAG_ROLLBACK_RESISTANCE) ||
            hw_enforced.Contains(KM_TAG_USAGE_COUNT_LIMIT)) {
            if (pure_soft_secure_key_storage_ == nullptr) return KM_ERROR_INVALID_KEY_BLOB;
            km_id_t keyid;
            bool exists;
            if (!soft_keymaster_enforcement_.CreateKeyId(blob, &keyid))
                return KM_ERROR_INVALID_KEY_BLOB;
            error = pure_soft_secure_key_storage_->KeyExists(keyid, &exists);
            if (error != KM_ERROR_OK || !exists) return KM_ERROR_INVALID_KEY_BLOB;
        }

        auto factory = GetKeyFactory(algorithm);
        return factory->LoadKey(std::move(key_material), additional_params, std::move(hw_enforced),
                                std::move(sw_enforced), key);
    };

    AuthorizationSet hidden;
    error = BuildHiddenAuthorizations(additional_params, &hidden, softwareRootOfTrust);
    if (error != KM_ERROR_OK) return error;

    // Assume it's an integrity-assured blob (new software-only blob, or new keymaster0-backed
    // blob).
    error =
        DeserializeIntegrityAssuredBlob(blob, hidden, &key_material, &hw_enforced, &sw_enforced);
    if (error != KM_ERROR_INVALID_KEY_BLOB) return constructKey();

    // Wasn't an integrity-assured blob.  Maybe it's an auth-encrypted blob.
    error = ParseAuthEncryptedBlob(blob, hidden, &key_material, &hw_enforced, &sw_enforced);
    if (error == KM_ERROR_OK) LOG_D("Parsed an old keymaster1 software key");
    if (error != KM_ERROR_INVALID_KEY_BLOB) return constructKey();

    // Wasn't an auth-encrypted blob.  Maybe it's an old softkeymaster blob.
    error = ParseOldSoftkeymasterBlob(blob, &key_material, &hw_enforced, &sw_enforced);
    if (error == KM_ERROR_OK) LOG_D("Parsed an old sofkeymaster key");

    return constructKey();
}

keymaster_error_t PureSoftKeymasterContext::DeleteKey(const KeymasterKeyBlob& blob) const {
    // Pretend to be some secure hardware with secure storage.
    if (GetSecurityLevel() != KM_SECURITY_LEVEL_SOFTWARE &&
        pure_soft_secure_key_storage_ != nullptr) {
        km_id_t keyid;
        if (!soft_keymaster_enforcement_.CreateKeyId(blob, &keyid)) return KM_ERROR_UNKNOWN_ERROR;
        return pure_soft_secure_key_storage_->DeleteKey(keyid);
    }

    // Otherwise, nothing to do for software-only contexts.
    return KM_ERROR_OK;
}

keymaster_error_t PureSoftKeymasterContext::DeleteAllKeys() const {
    // Pretend to be some secure hardware with secure storage.
    if (GetSecurityLevel() != KM_SECURITY_LEVEL_SOFTWARE &&
        pure_soft_secure_key_storage_ != nullptr) {
        return pure_soft_secure_key_storage_->DeleteAllKeys();
    }

    // Otherwise, nothing to do for software-only contexts.
    return KM_ERROR_OK;
}

keymaster_error_t PureSoftKeymasterContext::AddRngEntropy(const uint8_t* buf, size_t length) const {
    if (length > 2 * 1024) {
        // At most 2KiB is allowed to be added at once.
        return KM_ERROR_INVALID_INPUT_LENGTH;
    }
    // XXX TODO according to boringssl openssl/rand.h RAND_add is deprecated and does
    // nothing
    RAND_add(buf, length, 0 /* Don't assume any entropy is added to the pool. */);
    return KM_ERROR_OK;
}

CertificateChain
PureSoftKeymasterContext::GenerateAttestation(const Key& key,                         //
                                              const AuthorizationSet& attest_params,  //
                                              UniquePtr<Key> attest_key,
                                              const KeymasterBlob& issuer_subject,
                                              keymaster_error_t* error) const {
    if (!error) return {};
    *error = KM_ERROR_OK;

    keymaster_algorithm_t key_algorithm;
    if (!key.authorizations().GetTagValue(TAG_ALGORITHM, &key_algorithm)) {
        *error = KM_ERROR_UNKNOWN_ERROR;
        return {};
    }

    if ((key_algorithm != KM_ALGORITHM_RSA && key_algorithm != KM_ALGORITHM_EC)) {
        *error = KM_ERROR_INCOMPATIBLE_ALGORITHM;
        return {};
    }

    if (attest_params.GetTagValue(TAG_DEVICE_UNIQUE_ATTESTATION)) {
        *error = KM_ERROR_UNIMPLEMENTED;
        return {};
    }
    // We have established that the given key has the correct algorithm, and because this is the
    // SoftKeymasterContext we can assume that the Key is an AsymmetricKey. So we can downcast.
    const AsymmetricKey& asymmetric_key = static_cast<const AsymmetricKey&>(key);

    AttestKeyInfo attest_key_info(attest_key, &issuer_subject, error);
    if (*error != KM_ERROR_OK) return {};

    return generate_attestation(asymmetric_key, attest_params, std::move(attest_key_info), *this,
                                error);
}

CertificateChain PureSoftKeymasterContext::GenerateSelfSignedCertificate(
    const Key& key, const AuthorizationSet& cert_params, bool fake_signature,
    keymaster_error_t* error) const {
    keymaster_algorithm_t key_algorithm;
    if (!key.authorizations().GetTagValue(TAG_ALGORITHM, &key_algorithm)) {
        *error = KM_ERROR_UNKNOWN_ERROR;
        return {};
    }

    if ((key_algorithm != KM_ALGORITHM_RSA && key_algorithm != KM_ALGORITHM_EC)) {
        *error = KM_ERROR_INCOMPATIBLE_ALGORITHM;
        return {};
    }

    // We have established that the given key has the correct algorithm, and because this is the
    // SoftKeymasterContext we can assume that the Key is an AsymmetricKey. So we can downcast.
    const AsymmetricKey& asymmetric_key = static_cast<const AsymmetricKey&>(key);

    return generate_self_signed_cert(asymmetric_key, cert_params, fake_signature, error);
}

keymaster::Buffer PureSoftKeymasterContext::GenerateUniqueId(uint64_t creation_date_time,
                                                             const keymaster_blob_t& application_id,
                                                             bool reset_since_rotation,
                                                             keymaster_error_t* error) const {
    *error = KM_ERROR_OK;
    // The default implementation fakes the hardware bound key with an arbitrary 128-bit value.
    // Any real implementation must follow the guidance from the interface definition
    // hardware/interfaces/security/keymint/aidl/android/hardware/security/keymint/Tag.aidl:
    // "..a unique hardware-bound secret known to the secure environment and never revealed by it.
    // The secret must contain at least 128 bits of entropy and be unique to the individual device"
    const std::vector<uint8_t> fake_hbk = {'M', 'u', 's', 't', 'B', 'e', 'R', 'a',
                                           'n', 'd', 'o', 'm', 'B', 'i', 't', 's'};
    Buffer unique_id;
    *error = keymaster::generate_unique_id(fake_hbk, creation_date_time, application_id,
                                           reset_since_rotation, &unique_id);
    return unique_id;
}

static keymaster_error_t TranslateAuthorizationSetError(AuthorizationSet::Error err) {
    switch (err) {
    case AuthorizationSet::OK:
        return KM_ERROR_OK;
    case AuthorizationSet::ALLOCATION_FAILURE:
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    case AuthorizationSet::MALFORMED_DATA:
        return KM_ERROR_UNKNOWN_ERROR;
    }
    return KM_ERROR_OK;
}

keymaster_error_t PureSoftKeymasterContext::UnwrapKey(
    const KeymasterKeyBlob& wrapped_key_blob, const KeymasterKeyBlob& wrapping_key_blob,
    const AuthorizationSet& /* wrapping_key_params */, const KeymasterKeyBlob& masking_key,
    AuthorizationSet* wrapped_key_params, keymaster_key_format_t* wrapped_key_format,
    KeymasterKeyBlob* wrapped_key_material) const {
    keymaster_error_t error = KM_ERROR_OK;

    if (!wrapped_key_material) return KM_ERROR_UNEXPECTED_NULL_POINTER;

    // Parse wrapped key data
    KeymasterBlob iv;
    KeymasterKeyBlob transit_key;
    KeymasterKeyBlob secure_key;
    KeymasterBlob tag;
    KeymasterBlob wrapped_key_description;
    error = parse_wrapped_key(wrapped_key_blob, &iv, &transit_key, &secure_key, &tag,
                              wrapped_key_params, wrapped_key_format, &wrapped_key_description);
    if (error != KM_ERROR_OK) return error;

    UniquePtr<Key> key;
    auto wrapping_key_params = AuthorizationSetBuilder()
                                   .RsaEncryptionKey(2048, 65537)
                                   .Digest(KM_DIGEST_SHA_2_256)
                                   .Padding(KM_PAD_RSA_OAEP)
                                   .Authorization(TAG_PURPOSE, KM_PURPOSE_WRAP)
                                   .build();
    error = ParseKeyBlob(wrapping_key_blob, wrapping_key_params, &key);
    if (error != KM_ERROR_OK) return error;

    // Ensure the wrapping key has the right purpose
    if (!key->hw_enforced().Contains(TAG_PURPOSE, KM_PURPOSE_WRAP) &&
        !key->sw_enforced().Contains(TAG_PURPOSE, KM_PURPOSE_WRAP)) {
        return KM_ERROR_INCOMPATIBLE_PURPOSE;
    }

    auto operation_factory = GetOperationFactory(KM_ALGORITHM_RSA, KM_PURPOSE_DECRYPT);
    if (!operation_factory) return KM_ERROR_UNKNOWN_ERROR;

    AuthorizationSet out_params;
    OperationPtr operation(
        operation_factory->CreateOperation(std::move(*key), wrapping_key_params, &error));
    if (!operation.get()) return error;

    error = operation->Begin(wrapping_key_params, &out_params);
    if (error != KM_ERROR_OK) return error;

    Buffer input;
    Buffer output;
    if (!input.Reinitialize(transit_key.key_material, transit_key.key_material_size)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    error = operation->Finish(wrapping_key_params, input, Buffer() /* signature */, &out_params,
                              &output);
    if (error != KM_ERROR_OK) return error;

    // decrypt the encrypted key material with the transit key
    KeymasterKeyBlob key_material = {output.peek_read(), output.available_read()};

    // XOR the transit key with the masking key
    if (key_material.key_material_size != masking_key.key_material_size) {
        return KM_ERROR_INVALID_ARGUMENT;
    }
    for (size_t i = 0; i < key_material.key_material_size; i++) {
        key_material.writable_data()[i] ^= masking_key.key_material[i];
    }

    auto transit_key_authorizations = AuthorizationSetBuilder()
                                          .AesEncryptionKey(256)
                                          .Padding(KM_PAD_NONE)
                                          .Authorization(TAG_BLOCK_MODE, KM_MODE_GCM)
                                          .Authorization(TAG_NONCE, iv)
                                          .Authorization(TAG_MIN_MAC_LENGTH, 128)
                                          .build();
    if (transit_key_authorizations.is_valid() != AuthorizationSet::Error::OK) {
        return TranslateAuthorizationSetError(transit_key_authorizations.is_valid());
    }
    auto gcm_params = AuthorizationSetBuilder()
                          .Padding(KM_PAD_NONE)
                          .Authorization(TAG_BLOCK_MODE, KM_MODE_GCM)
                          .Authorization(TAG_NONCE, iv)
                          .Authorization(TAG_MAC_LENGTH, 128)
                          .build();
    if (gcm_params.is_valid() != AuthorizationSet::Error::OK) {
        return TranslateAuthorizationSetError(transit_key_authorizations.is_valid());
    }

    auto aes_factory = GetKeyFactory(KM_ALGORITHM_AES);
    if (!aes_factory) return KM_ERROR_UNKNOWN_ERROR;

    UniquePtr<Key> aes_key;
    error = aes_factory->LoadKey(std::move(key_material), gcm_params,
                                 std::move(transit_key_authorizations), AuthorizationSet(),
                                 &aes_key);
    if (error != KM_ERROR_OK) return error;

    auto aes_operation_factory = GetOperationFactory(KM_ALGORITHM_AES, KM_PURPOSE_DECRYPT);
    if (!aes_operation_factory) return KM_ERROR_UNKNOWN_ERROR;

    OperationPtr aes_operation(
        aes_operation_factory->CreateOperation(std::move(*aes_key), gcm_params, &error));
    if (!aes_operation.get()) return error;

    error = aes_operation->Begin(gcm_params, &out_params);
    if (error != KM_ERROR_OK) return error;

    size_t consumed = 0;
    Buffer encrypted_key, plaintext;
    if (!plaintext.Reinitialize(secure_key.key_material_size + tag.data_length)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }
    if (!encrypted_key.Reinitialize(secure_key.key_material_size + tag.data_length)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }
    if (!encrypted_key.write(secure_key.key_material, secure_key.key_material_size)) {
        return KM_ERROR_UNKNOWN_ERROR;
    }
    if (!encrypted_key.write(tag.data, tag.data_length)) {
        return KM_ERROR_UNKNOWN_ERROR;
    }

    AuthorizationSet update_outparams;
    auto update_params = AuthorizationSetBuilder()
                             .Authorization(TAG_ASSOCIATED_DATA, wrapped_key_description.data,
                                            wrapped_key_description.data_length)
                             .build();
    if (update_params.is_valid() != AuthorizationSet::Error::OK) {
        return TranslateAuthorizationSetError(update_params.is_valid());
    }

    error = aes_operation->Update(update_params, encrypted_key, &update_outparams, &plaintext,
                                  &consumed);
    if (error != KM_ERROR_OK) return error;

    AuthorizationSet finish_params, finish_out_params;
    Buffer finish_input;
    error = aes_operation->Finish(finish_params, finish_input, Buffer() /* signature */,
                                  &finish_out_params, &plaintext);
    if (error != KM_ERROR_OK) return error;

    *wrapped_key_material = {plaintext.peek_read(), plaintext.available_read()};
    if (!wrapped_key_material->key_material && plaintext.peek_read()) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    return error;
}

const AttestationContext::VerifiedBootParams*
PureSoftKeymasterContext::GetVerifiedBootParams(keymaster_error_t* error) const {
    static VerifiedBootParams params;
    static std::string fake_vb_key(32, 0);
    params.verified_boot_key = {reinterpret_cast<uint8_t*>(fake_vb_key.data()), fake_vb_key.size()};
    params.verified_boot_hash = {reinterpret_cast<uint8_t*>(fake_vb_key.data()),
                                 fake_vb_key.size()};
    params.verified_boot_state = KM_VERIFIED_BOOT_UNVERIFIED;
    params.device_locked = false;
    *error = KM_ERROR_OK;
    return &params;
}

}  // namespace keymaster
