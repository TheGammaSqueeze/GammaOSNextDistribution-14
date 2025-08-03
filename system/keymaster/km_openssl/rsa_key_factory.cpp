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

#include <keymaster/km_openssl/rsa_key_factory.h>

#include <inttypes.h>
#include <utility>

#include <keymaster/keymaster_context.h>
#include <keymaster/km_openssl/openssl_err.h>
#include <keymaster/km_openssl/openssl_utils.h>
#include <keymaster/km_openssl/rsa_key.h>
#include <keymaster/km_openssl/rsa_operation.h>

namespace keymaster {

const int kMaximumRsaKeySize = 4096;  // OpenSSL fails above 4096.
const int kMinimumRsaKeySize = 16;    // OpenSSL goes into an infinite loop if key size < 10
const int kMinimumRsaExponent = 3;

static RsaSigningOperationFactory sign_factory;
static RsaVerificationOperationFactory verify_factory;
static RsaEncryptionOperationFactory encrypt_factory;
static RsaDecryptionOperationFactory decrypt_factory;

OperationFactory* RsaKeyFactory::GetOperationFactory(keymaster_purpose_t purpose) const {
    switch (purpose) {
    case KM_PURPOSE_SIGN:
        return &sign_factory;
    case KM_PURPOSE_VERIFY:
        return &verify_factory;
    case KM_PURPOSE_ENCRYPT:
        return &encrypt_factory;
    case KM_PURPOSE_DECRYPT:
        return &decrypt_factory;
    default:
        return nullptr;
    }
}

keymaster_error_t RsaKeyFactory::GenerateKey(const AuthorizationSet& key_description,
                                             UniquePtr<Key> attest_key,  //
                                             const KeymasterBlob& issuer_subject,
                                             KeymasterKeyBlob* key_blob,
                                             AuthorizationSet* hw_enforced,
                                             AuthorizationSet* sw_enforced,
                                             CertificateChain* cert_chain) const {
    if (!key_blob || !hw_enforced || !sw_enforced) return KM_ERROR_OUTPUT_PARAMETER_NULL;

    uint64_t public_exponent;
    if (!key_description.GetTagValue(TAG_RSA_PUBLIC_EXPONENT, &public_exponent)) {
        LOG_E("No public exponent specified for RSA key generation");
        return KM_ERROR_INVALID_ARGUMENT;
    }
    if (public_exponent < kMinimumRsaExponent || public_exponent % 2 != 1) {
        LOG_E("Invalid public exponent specified for RSA key generation");
        return KM_ERROR_INVALID_ARGUMENT;
    }

    uint32_t key_size;
    if (!key_description.GetTagValue(TAG_KEY_SIZE, &key_size)) {
        LOG_E("No key size specified for RSA key generation");
        return KM_ERROR_UNSUPPORTED_KEY_SIZE;
    }
    if (key_size % 8 != 0 || key_size > kMaximumRsaKeySize || key_size < kMinimumRsaKeySize) {
        LOG_E("Invalid key size of %" PRIu32 " bits specified for RSA key generation", key_size);
        return KM_ERROR_UNSUPPORTED_KEY_SIZE;
    }

    BIGNUM_Ptr exponent(BN_new());
    RSA_Ptr rsa_key(RSA_new());
    EVP_PKEY_Ptr pkey(EVP_PKEY_new());
    if (exponent.get() == nullptr || rsa_key.get() == nullptr || pkey.get() == nullptr) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    if (!BN_set_word(exponent.get(), public_exponent) ||
        !RSA_generate_key_ex(rsa_key.get(), key_size, exponent.get(), nullptr /* callback */))
        return TranslateLastOpenSslError();

    if (EVP_PKEY_set1_RSA(pkey.get(), rsa_key.get()) != 1) return TranslateLastOpenSslError();

    KeymasterKeyBlob key_material;
    keymaster_error_t error = EvpKeyToKeyMaterial(pkey.get(), &key_material);
    if (error != KM_ERROR_OK) return error;

    error = blob_maker_.CreateKeyBlob(key_description, KM_ORIGIN_GENERATED, key_material, key_blob,
                                      hw_enforced, sw_enforced);
    if (error != KM_ERROR_OK) return error;

    if (context_.GetKmVersion() < KmVersion::KEYMINT_1) return KM_ERROR_OK;
    if (!cert_chain) return KM_ERROR_UNEXPECTED_NULL_POINTER;

    RsaKey key(*hw_enforced, *sw_enforced, this, std::move(rsa_key));
    if (key_description.Contains(TAG_ATTESTATION_CHALLENGE)) {
        *cert_chain = context_.GenerateAttestation(key, key_description, std::move(attest_key),
                                                   issuer_subject, &error);
    } else if (attest_key.get() != nullptr) {
        return KM_ERROR_ATTESTATION_CHALLENGE_MISSING;
    } else {
        bool fake_signature = key_size < 1024 || !IsCertSigningKey(key_description);
        *cert_chain =
            context_.GenerateSelfSignedCertificate(key, key_description, fake_signature, &error);
    }

    return error;
}

keymaster_error_t RsaKeyFactory::ImportKey(const AuthorizationSet& key_description,  //
                                           keymaster_key_format_t input_key_material_format,
                                           const KeymasterKeyBlob& input_key_material,
                                           UniquePtr<Key> attest_key,  //
                                           const KeymasterBlob& issuer_subject,
                                           KeymasterKeyBlob* output_key_blob,
                                           AuthorizationSet* hw_enforced,
                                           AuthorizationSet* sw_enforced,
                                           CertificateChain* cert_chain) const {
    if (!output_key_blob || !hw_enforced || !sw_enforced) return KM_ERROR_OUTPUT_PARAMETER_NULL;

    AuthorizationSet authorizations;
    uint64_t public_exponent;
    uint32_t key_size;
    keymaster_error_t error =
        UpdateImportKeyDescription(key_description, input_key_material_format, input_key_material,
                                   &authorizations, &public_exponent, &key_size);
    if (error != KM_ERROR_OK) return error;
    error = blob_maker_.CreateKeyBlob(authorizations, KM_ORIGIN_IMPORTED, input_key_material,
                                      output_key_blob, hw_enforced, sw_enforced);
    if (error != KM_ERROR_OK) return error;

    if (context_.GetKmVersion() < KmVersion::KEYMINT_1) return KM_ERROR_OK;
    if (!cert_chain) return KM_ERROR_UNEXPECTED_NULL_POINTER;

    EVP_PKEY_Ptr pkey;
    error = KeyMaterialToEvpKey(KM_KEY_FORMAT_PKCS8, input_key_material, KM_ALGORITHM_RSA, &pkey);
    if (error != KM_ERROR_OK) return error;

    RSA_Ptr rsa_key(EVP_PKEY_get1_RSA(pkey.get()));
    if (!rsa_key.get()) return KM_ERROR_INVALID_ARGUMENT;

    RsaKey key(*hw_enforced, *sw_enforced, this, std::move(rsa_key));
    if (key_description.Contains(KM_TAG_ATTESTATION_CHALLENGE)) {
        *cert_chain = context_.GenerateAttestation(key, key_description, std::move(attest_key),
                                                   issuer_subject, &error);
    } else if (attest_key.get() != nullptr) {
        return KM_ERROR_ATTESTATION_CHALLENGE_MISSING;
    } else {
        bool fake_signature = key_size < 1024 || !IsCertSigningKey(key_description);
        *cert_chain =
            context_.GenerateSelfSignedCertificate(key, key_description, fake_signature, &error);
    }

    return error;
}

keymaster_error_t RsaKeyFactory::UpdateImportKeyDescription(const AuthorizationSet& key_description,
                                                            keymaster_key_format_t key_format,
                                                            const KeymasterKeyBlob& key_material,
                                                            AuthorizationSet* updated_description,
                                                            uint64_t* public_exponent,
                                                            uint32_t* key_size) const {
    if (!updated_description || !public_exponent || !key_size)
        return KM_ERROR_OUTPUT_PARAMETER_NULL;

    EVP_PKEY_Ptr pkey;
    keymaster_error_t error =
        KeyMaterialToEvpKey(key_format, key_material, keymaster_key_type(), &pkey);
    if (error != KM_ERROR_OK) return error;

    RSA_Ptr rsa_key(EVP_PKEY_get1_RSA(pkey.get()));
    if (!rsa_key.get()) return TranslateLastOpenSslError();

    updated_description->Reinitialize(key_description);

    uint64_t e;
    if (!BN_get_u64(RSA_get0_e(rsa_key.get()), &e)) return KM_ERROR_INVALID_KEY_BLOB;
    if (!updated_description->GetTagValue(TAG_RSA_PUBLIC_EXPONENT, public_exponent)) {
        *public_exponent = e;
        updated_description->push_back(TAG_RSA_PUBLIC_EXPONENT, *public_exponent);
    }
    if (*public_exponent != e) {
        LOG_E("Imported public exponent (%" PRIu64 ") does not match specified "
              "public exponent (%" PRIu64 ")",
              *public_exponent, e);
        return KM_ERROR_IMPORT_PARAMETER_MISMATCH;
    }

    *key_size = RSA_size(rsa_key.get()) * 8;
    if (!updated_description->GetTagValue(TAG_KEY_SIZE, key_size))
        updated_description->push_back(TAG_KEY_SIZE, *key_size);
    if (RSA_size(rsa_key.get()) * 8 != *key_size) {
        LOG_E("Imported key size (%u bits) does not match specified key size (%u bits)",
              RSA_size(rsa_key.get()) * 8, *key_size);
        return KM_ERROR_IMPORT_PARAMETER_MISMATCH;
    }

    keymaster_algorithm_t algorithm = KM_ALGORITHM_RSA;
    if (!updated_description->GetTagValue(TAG_ALGORITHM, &algorithm))
        updated_description->push_back(TAG_ALGORITHM, KM_ALGORITHM_RSA);
    if (algorithm != KM_ALGORITHM_RSA) return KM_ERROR_IMPORT_PARAMETER_MISMATCH;

    return KM_ERROR_OK;
}

keymaster_error_t RsaKeyFactory::CreateEmptyKey(AuthorizationSet&& hw_enforced,
                                                AuthorizationSet&& sw_enforced,
                                                UniquePtr<AsymmetricKey>* key) const {
    key->reset(new (std::nothrow) RsaKey(std::move(hw_enforced), std::move(sw_enforced), this));
    if (!(*key)) return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    return KM_ERROR_OK;
}

}  // namespace keymaster
