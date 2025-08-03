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

#include <keymaster/km_openssl/ec_key_factory.h>

#include <inttypes.h>
#include <utility>

#include <openssl/curve25519.h>
#include <openssl/evp.h>

#include <keymaster/keymaster_context.h>
#include <keymaster/km_openssl/curve25519_key.h>
#include <keymaster/km_openssl/ec_key.h>
#include <keymaster/km_openssl/ecdh_operation.h>
#include <keymaster/km_openssl/ecdsa_operation.h>
#include <keymaster/km_openssl/openssl_err.h>

#include <keymaster/operation.h>

namespace keymaster {

static EcdsaSignOperationFactory sign_factory;
static EcdsaVerifyOperationFactory verify_factory;
static EcdhOperationFactory agree_key_factory;

OperationFactory* EcKeyFactory::GetOperationFactory(keymaster_purpose_t purpose) const {
    switch (purpose) {
    case KM_PURPOSE_SIGN:
        return &sign_factory;
    case KM_PURPOSE_VERIFY:
        return &verify_factory;
    case KM_PURPOSE_AGREE_KEY:
        return &agree_key_factory;
    default:
        return nullptr;
    }
}

/* static */
keymaster_error_t EcKeyFactory::GetCurveAndSize(const AuthorizationSet& key_description,
                                                keymaster_ec_curve_t* curve,
                                                uint32_t* key_size_bits) {
    if (!key_description.GetTagValue(TAG_EC_CURVE, curve)) {
        // Curve not specified. Fall back to deducing curve from key size.
        if (!key_description.GetTagValue(TAG_KEY_SIZE, key_size_bits)) {
            LOG_E("%s", "No curve or key size specified for EC key generation");
            return KM_ERROR_UNSUPPORTED_KEY_SIZE;
        }
        keymaster_error_t error = EllipticKeySizeToCurve(*key_size_bits, curve);
        if (error != KM_ERROR_OK) {
            return KM_ERROR_UNSUPPORTED_KEY_SIZE;
        }
    } else {
        keymaster_error_t error = EcCurveToKeySize(*curve, key_size_bits);
        if (error != KM_ERROR_OK) {
            return error;
        }
        uint32_t tag_key_size_bits;
        if (key_description.GetTagValue(TAG_KEY_SIZE, &tag_key_size_bits) &&
            *key_size_bits != tag_key_size_bits) {
            LOG_E("Curve key size %" PRIu32 " and specified key size %" PRIu32 " don't match",
                  *key_size_bits, tag_key_size_bits);
            return KM_ERROR_INVALID_ARGUMENT;
        }
    }

    return KM_ERROR_OK;
}

keymaster_error_t EcKeyFactory::GenerateKey(const AuthorizationSet& key_description,
                                            UniquePtr<Key> attest_key,  //
                                            const KeymasterBlob& issuer_subject,
                                            KeymasterKeyBlob* key_blob,
                                            AuthorizationSet* hw_enforced,
                                            AuthorizationSet* sw_enforced,
                                            CertificateChain* cert_chain) const {
    if (!key_blob || !hw_enforced || !sw_enforced) return KM_ERROR_OUTPUT_PARAMETER_NULL;

    AuthorizationSet authorizations(key_description);

    keymaster_ec_curve_t ec_curve;
    uint32_t key_size;
    keymaster_error_t error = GetCurveAndSize(authorizations, &ec_curve, &key_size);
    if (error != KM_ERROR_OK) {
        return error;
    } else if (!authorizations.Contains(TAG_KEY_SIZE, key_size)) {
        authorizations.push_back(TAG_KEY_SIZE, key_size);
    } else if (!authorizations.Contains(TAG_EC_CURVE, ec_curve)) {
        authorizations.push_back(TAG_EC_CURVE, ec_curve);
    }

    bool is_ed25519 = false;
    bool is_x25519 = false;
    UniquePtr<EVP_PKEY, EVP_PKEY_Delete> pkey;
    UniquePtr<EC_KEY, EC_KEY_Delete> ec_key(EC_KEY_new());
    KeymasterKeyBlob key_material;
    if (ec_curve == KM_EC_CURVE_CURVE_25519) {
        // Curve 25519 keys do not fall under OpenSSL's EC_KEY category.
        is_ed25519 = (key_description.Contains(TAG_PURPOSE, KM_PURPOSE_SIGN) ||
                      key_description.Contains(TAG_PURPOSE, KM_PURPOSE_ATTEST_KEY));
        is_x25519 = key_description.Contains(TAG_PURPOSE, KM_PURPOSE_AGREE_KEY);
        if (is_ed25519 && is_x25519) {
            // Cannot have both SIGN (Ed25519) and AGREE_KEY (X25519).
            return KM_ERROR_INCOMPATIBLE_PURPOSE;
        }

        if (is_ed25519) {
            uint8_t priv_key[ED25519_PRIVATE_KEY_LEN];
            uint8_t pub_key[ED25519_PUBLIC_KEY_LEN];
            ED25519_keypair(pub_key, priv_key);

            // Only feed in the first 32 bytes of the generated private key.
            pkey.reset(EVP_PKEY_new_raw_private_key(EVP_PKEY_ED25519, nullptr, priv_key,
                                                    ED25519_SEED_LEN));
        } else if (is_x25519) {
            uint8_t priv_key[X25519_PRIVATE_KEY_LEN];
            uint8_t pub_key[X25519_PUBLIC_VALUE_LEN];
            X25519_keypair(pub_key, priv_key);

            pkey.reset(EVP_PKEY_new_raw_private_key(EVP_PKEY_X25519, nullptr, priv_key,
                                                    X25519_PRIVATE_KEY_LEN));
        } else {
            return KM_ERROR_UNSUPPORTED_PURPOSE;
        }
        if (pkey.get() == nullptr) {
            return KM_ERROR_UNKNOWN_ERROR;
        }
    } else {
        pkey.reset(EVP_PKEY_new());
        if (ec_key.get() == nullptr || pkey.get() == nullptr)
            return KM_ERROR_MEMORY_ALLOCATION_FAILED;

        UniquePtr<EC_GROUP, EC_GROUP_Delete> group(ChooseGroup(ec_curve));
        if (group.get() == nullptr) {
            LOG_E("Unable to get EC group for curve %d", ec_curve);
            return KM_ERROR_UNSUPPORTED_KEY_SIZE;
        }

#if !defined(OPENSSL_IS_BORINGSSL)
        EC_GROUP_set_point_conversion_form(group.get(), POINT_CONVERSION_UNCOMPRESSED);
        EC_GROUP_set_asn1_flag(group.get(), OPENSSL_EC_NAMED_CURVE);
#endif

        if (EC_KEY_set_group(ec_key.get(), group.get()) != 1 ||
            EC_KEY_generate_key(ec_key.get()) != 1 || EC_KEY_check_key(ec_key.get()) < 0) {
            return TranslateLastOpenSslError();
        }

        if (EVP_PKEY_set1_EC_KEY(pkey.get(), ec_key.get()) != 1) return TranslateLastOpenSslError();
    }

    error = EvpKeyToKeyMaterial(pkey.get(), &key_material);
    if (error != KM_ERROR_OK) return error;

    error = blob_maker_.CreateKeyBlob(authorizations, KM_ORIGIN_GENERATED, key_material, key_blob,
                                      hw_enforced, sw_enforced);
    if (error != KM_ERROR_OK) return error;

    // Only generate attestation certificates for KeyMint (KeyMaster uses an attestKey()
    // entrypoint that is separate from generateKey()).
    if (context_.GetKmVersion() < KmVersion::KEYMINT_1) return KM_ERROR_OK;
    if (!cert_chain) return KM_ERROR_UNEXPECTED_NULL_POINTER;

    std::unique_ptr<AsymmetricKey> key;
    if (is_ed25519) {
        key.reset(new (std::nothrow) Ed25519Key(*hw_enforced, *sw_enforced, this, key_material));
    } else if (is_x25519) {
        key.reset(new (std::nothrow) X25519Key(*hw_enforced, *sw_enforced, this, key_material));
    } else {
        key.reset(new (std::nothrow) EcKey(*hw_enforced, *sw_enforced, this, std::move(ec_key)));
    }
    if (key == nullptr) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    if (key_description.Contains(TAG_ATTESTATION_CHALLENGE)) {
        *cert_chain = context_.GenerateAttestation(*key, key_description, std::move(attest_key),
                                                   issuer_subject, &error);
    } else if (attest_key.get() != nullptr) {
        return KM_ERROR_ATTESTATION_CHALLENGE_MISSING;
    } else {
        *cert_chain = context_.GenerateSelfSignedCertificate(
            *key, key_description, !IsCertSigningKey(key_description) /* fake_signature */, &error);
    }

    return error;
}

keymaster_error_t EcKeyFactory::ImportKey(const AuthorizationSet& key_description,  //
                                          keymaster_key_format_t input_key_material_format,
                                          const KeymasterKeyBlob& input_key_material,
                                          UniquePtr<Key> attest_key,  //
                                          const KeymasterBlob& issuer_subject,
                                          KeymasterKeyBlob* output_key_blob,
                                          AuthorizationSet* hw_enforced,
                                          AuthorizationSet* sw_enforced,
                                          CertificateChain* cert_chain) const {
    if (input_key_material_format == KM_KEY_FORMAT_RAW) {
        return ImportRawKey(key_description, input_key_material, std::move(attest_key),
                            issuer_subject, output_key_blob, hw_enforced, sw_enforced, cert_chain);
    }

    if (!output_key_blob || !hw_enforced || !sw_enforced) return KM_ERROR_OUTPUT_PARAMETER_NULL;

    AuthorizationSet authorizations;
    uint32_t key_size;
    keymaster_error_t error = UpdateImportKeyDescription(
        key_description, input_key_material_format, input_key_material, &authorizations, &key_size);
    if (error != KM_ERROR_OK) return error;

    error = blob_maker_.CreateKeyBlob(authorizations, KM_ORIGIN_IMPORTED, input_key_material,
                                      output_key_blob, hw_enforced, sw_enforced);
    if (error != KM_ERROR_OK) return error;

    if (context_.GetKmVersion() < KmVersion::KEYMINT_1) return KM_ERROR_OK;
    if (!cert_chain) return KM_ERROR_UNEXPECTED_NULL_POINTER;

    EVP_PKEY_Ptr pkey;
    error = KeyMaterialToEvpKey(KM_KEY_FORMAT_PKCS8, input_key_material, KM_ALGORITHM_EC, &pkey);
    if (error != KM_ERROR_OK) return error;

    std::unique_ptr<AsymmetricKey> key;
    switch (EVP_PKEY_id(pkey.get())) {
    case EVP_PKEY_ED25519:
        key.reset(new (std::nothrow) Ed25519Key(*hw_enforced, *sw_enforced, this));
        if (key.get() == nullptr) {
            return KM_ERROR_MEMORY_ALLOCATION_FAILED;
        }
        if (!key->EvpToInternal(pkey.get())) {
            return KM_ERROR_UNSUPPORTED_KEY_FORMAT;
        }
        break;
    case EVP_PKEY_X25519:
        key.reset(new (std::nothrow) X25519Key(*hw_enforced, *sw_enforced, this));
        if (key.get() == nullptr) {
            return KM_ERROR_MEMORY_ALLOCATION_FAILED;
        }
        if (!key->EvpToInternal(pkey.get())) {
            return KM_ERROR_UNSUPPORTED_KEY_FORMAT;
        }
        break;
    case EVP_PKEY_EC: {
        EC_KEY_Ptr ec_key(EVP_PKEY_get1_EC_KEY(pkey.get()));
        if (!ec_key.get()) return KM_ERROR_INVALID_ARGUMENT;

        key.reset(new (std::nothrow) EcKey(*hw_enforced, *sw_enforced, this, std::move(ec_key)));
        if (key.get() == nullptr) {
            return KM_ERROR_MEMORY_ALLOCATION_FAILED;
        }
        break;
    }
    default:
        return KM_ERROR_UNSUPPORTED_KEY_FORMAT;
    }
    if (key == nullptr) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    if (key_description.Contains(KM_TAG_ATTESTATION_CHALLENGE)) {
        *cert_chain = context_.GenerateAttestation(*key, key_description, std::move(attest_key),
                                                   issuer_subject, &error);
    } else if (attest_key.get() != nullptr) {
        return KM_ERROR_ATTESTATION_CHALLENGE_MISSING;
    } else {
        *cert_chain = context_.GenerateSelfSignedCertificate(
            *key, key_description, !IsCertSigningKey(key_description) /* fake_signature */, &error);
    }

    return error;
}

keymaster_error_t EcKeyFactory::ImportRawKey(const AuthorizationSet& key_description,  //
                                             const KeymasterKeyBlob& input_key_material,
                                             UniquePtr<Key> attest_key,  //
                                             const KeymasterBlob& issuer_subject,
                                             KeymasterKeyBlob* output_key_blob,
                                             AuthorizationSet* hw_enforced,
                                             AuthorizationSet* sw_enforced,
                                             CertificateChain* cert_chain) const {
    if (!output_key_blob || !hw_enforced || !sw_enforced) return KM_ERROR_OUTPUT_PARAMETER_NULL;

    // Curve 25519 keys may arrive in raw form, but if they do the key_description must include
    // enough information to allow the key material to be identified. This means that the
    // following tags must already be present in key_description:
    // - TAG_ALGORITHM: KM_ALGORITHM_EC
    // - TAG_EC_CURVE: KM_EC_CURVE_CURVE_25519
    // - TAG_PURPOSE: exactly one of:
    //    - KM_SIGN (Ed25519)
    //    - KM_ATTEST_KEY (Ed25519)
    //    - KM_AGREE (X25519)
    keymaster_ec_curve_t curve;
    if (!key_description.GetTagValue(TAG_EC_CURVE, &curve) || curve != KM_EC_CURVE_CURVE_25519) {
        return KM_ERROR_UNSUPPORTED_KEY_FORMAT;
    }
    bool is_ed25519 = (key_description.Contains(TAG_PURPOSE, KM_PURPOSE_SIGN) ||
                       key_description.Contains(TAG_PURPOSE, KM_PURPOSE_ATTEST_KEY));
    bool is_x25519 = key_description.Contains(TAG_PURPOSE, KM_PURPOSE_AGREE_KEY);
    if (is_ed25519 && is_x25519) {
        // Cannot have both SIGN (Ed25519) and AGREE_KEY (X25519).
        return KM_ERROR_INCOMPATIBLE_PURPOSE;
    }
    if (key_description.Contains(TAG_PURPOSE, KM_PURPOSE_ATTEST_KEY) &&
        key_description.GetTagCount(TAG_PURPOSE) > 1) {
        // ATTEST_KEY cannot be combined with another purpose.
        return KM_ERROR_INCOMPATIBLE_PURPOSE;
    }

    // First convert the raw key data into an EVP_PKEY.
    EVP_PKEY_Ptr pkey;
    if (is_ed25519) {
        pkey.reset(EVP_PKEY_new_raw_private_key(EVP_PKEY_ED25519, /* unused*/ nullptr,
                                                input_key_material.key_material,
                                                input_key_material.key_material_size));
    } else if (is_x25519) {
        pkey.reset(EVP_PKEY_new_raw_private_key(EVP_PKEY_X25519, /* unused*/ nullptr,
                                                input_key_material.key_material,
                                                input_key_material.key_material_size));
    } else {
        return KM_ERROR_UNSUPPORTED_KEY_FORMAT;
    }
    if (pkey.get() == nullptr) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Now extract PKCS#8 formatted private key material from the EVP_PKEY.
    KeymasterKeyBlob pkcs8_key_material;
    keymaster_error_t error = EvpKeyToKeyMaterial(pkey.get(), &pkcs8_key_material);
    if (error != KM_ERROR_OK) return error;

    // Store the PKCS#8 private key material in the key blob.
    error = blob_maker_.CreateKeyBlob(key_description, KM_ORIGIN_IMPORTED, pkcs8_key_material,
                                      output_key_blob, hw_enforced, sw_enforced);
    if (error != KM_ERROR_OK) return error;

    if (context_.GetKmVersion() < KmVersion::KEYMINT_1) return KM_ERROR_OK;
    if (!cert_chain) return KM_ERROR_UNEXPECTED_NULL_POINTER;

    std::unique_ptr<AsymmetricKey> key;
    if (is_ed25519) {
        key.reset(new (std::nothrow)
                      Ed25519Key(*hw_enforced, *sw_enforced, this, pkcs8_key_material));
    } else /* is_x25519 */ {
        key.reset(new (std::nothrow)
                      X25519Key(*hw_enforced, *sw_enforced, this, pkcs8_key_material));
    }
    if (key == nullptr) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    if (key_description.Contains(KM_TAG_ATTESTATION_CHALLENGE)) {
        *cert_chain = context_.GenerateAttestation(*key, key_description, std::move(attest_key),
                                                   issuer_subject, &error);
    } else if (attest_key.get() != nullptr) {
        return KM_ERROR_ATTESTATION_CHALLENGE_MISSING;
    } else {
        *cert_chain = context_.GenerateSelfSignedCertificate(
            *key, key_description, !IsCertSigningKey(key_description) /* fake_signature */, &error);
    }

    return error;
}

keymaster_error_t EcKeyFactory::UpdateImportKeyDescription(const AuthorizationSet& key_description,
                                                           keymaster_key_format_t key_format,
                                                           const KeymasterKeyBlob& key_material,
                                                           AuthorizationSet* updated_description,
                                                           uint32_t* key_size_bits) const {
    if (!updated_description || !key_size_bits) return KM_ERROR_OUTPUT_PARAMETER_NULL;

    UniquePtr<EVP_PKEY, EVP_PKEY_Delete> pkey;
    keymaster_error_t error =
        KeyMaterialToEvpKey(key_format, key_material, keymaster_key_type(), &pkey);
    if (error != KM_ERROR_OK) return error;

    updated_description->Reinitialize(key_description);

    keymaster_algorithm_t algorithm = KM_ALGORITHM_EC;
    if (!updated_description->GetTagValue(TAG_ALGORITHM, &algorithm)) {
        updated_description->push_back(TAG_ALGORITHM, KM_ALGORITHM_EC);
    } else if (algorithm != KM_ALGORITHM_EC) {
        return KM_ERROR_IMPORT_PARAMETER_MISMATCH;
    }

    switch (EVP_PKEY_id(pkey.get())) {
    case EVP_PKEY_EC: {
        UniquePtr<EC_KEY, EC_KEY_Delete> ec_key(EVP_PKEY_get1_EC_KEY(pkey.get()));
        if (!ec_key.get()) return TranslateLastOpenSslError();

        size_t extracted_key_size_bits;
        error = ec_get_group_size(EC_KEY_get0_group(ec_key.get()), &extracted_key_size_bits);
        if (error != KM_ERROR_OK) return error;

        *key_size_bits = extracted_key_size_bits;
        if (!updated_description->GetTagValue(TAG_KEY_SIZE, key_size_bits)) {
            updated_description->push_back(TAG_KEY_SIZE, extracted_key_size_bits);
        } else if (*key_size_bits != extracted_key_size_bits) {
            return KM_ERROR_IMPORT_PARAMETER_MISMATCH;
        }

        keymaster_ec_curve_t curve_from_size;
        error = EcKeySizeToCurve(*key_size_bits, &curve_from_size);
        if (error != KM_ERROR_OK) return error;
        keymaster_ec_curve_t curve;
        if (!updated_description->GetTagValue(TAG_EC_CURVE, &curve)) {
            updated_description->push_back(TAG_EC_CURVE, curve_from_size);
        } else if (curve_from_size != curve) {
            return KM_ERROR_IMPORT_PARAMETER_MISMATCH;
        }
        break;
    }
    case EVP_PKEY_ED25519: {
        keymaster_ec_curve_t curve;
        if (!updated_description->GetTagValue(TAG_EC_CURVE, &curve)) {
            updated_description->push_back(TAG_EC_CURVE, KM_EC_CURVE_CURVE_25519);
        } else if (curve != KM_EC_CURVE_CURVE_25519) {
            return KM_ERROR_IMPORT_PARAMETER_MISMATCH;
        }
        if (updated_description->Contains(TAG_PURPOSE, KM_PURPOSE_AGREE_KEY)) {
            // Purpose is for X25519, key is Ed25519.
            return KM_ERROR_IMPORT_PARAMETER_MISMATCH;
        }
        if (updated_description->Contains(TAG_PURPOSE, KM_PURPOSE_ATTEST_KEY) &&
            updated_description->GetTagCount(TAG_PURPOSE) > 1) {
            // ATTEST_KEY cannot be combined with another purpose.
            return KM_ERROR_INCOMPATIBLE_PURPOSE;
        }
        break;
    }
    case EVP_PKEY_X25519: {
        keymaster_ec_curve_t curve;
        if (!updated_description->GetTagValue(TAG_EC_CURVE, &curve)) {
            updated_description->push_back(TAG_EC_CURVE, KM_EC_CURVE_CURVE_25519);
        } else if (curve != KM_EC_CURVE_CURVE_25519) {
            return KM_ERROR_IMPORT_PARAMETER_MISMATCH;
        }
        if (updated_description->Contains(TAG_PURPOSE, KM_PURPOSE_SIGN) ||
            updated_description->Contains(TAG_PURPOSE, KM_PURPOSE_ATTEST_KEY)) {
            // Purpose is for Ed25519, key is X25519.
            return KM_ERROR_IMPORT_PARAMETER_MISMATCH;
        }
        break;
    }
    default:
        return KM_ERROR_INVALID_KEY_BLOB;
    }

    return KM_ERROR_OK;
}

/* static */
EC_GROUP* EcKeyFactory::ChooseGroup(size_t key_size_bits) {
    switch (key_size_bits) {
    case 224:
        return EC_GROUP_new_by_curve_name(NID_secp224r1);
        break;
    case 256:
        return EC_GROUP_new_by_curve_name(NID_X9_62_prime256v1);
        break;
    case 384:
        return EC_GROUP_new_by_curve_name(NID_secp384r1);
        break;
    case 521:
        return EC_GROUP_new_by_curve_name(NID_secp521r1);
        break;
    default:
        return nullptr;
        break;
    }
}

/* static */
EC_GROUP* EcKeyFactory::ChooseGroup(keymaster_ec_curve_t ec_curve) {
    switch (ec_curve) {
    case KM_EC_CURVE_P_224:
        return EC_GROUP_new_by_curve_name(NID_secp224r1);
        break;
    case KM_EC_CURVE_P_256:
        return EC_GROUP_new_by_curve_name(NID_X9_62_prime256v1);
        break;
    case KM_EC_CURVE_P_384:
        return EC_GROUP_new_by_curve_name(NID_secp384r1);
        break;
    case KM_EC_CURVE_P_521:
        return EC_GROUP_new_by_curve_name(NID_secp521r1);
        break;
    default:
        return nullptr;
        break;
    }
}

keymaster_error_t EcKeyFactory::CreateEmptyKey(AuthorizationSet&& hw_enforced,
                                               AuthorizationSet&& sw_enforced,
                                               UniquePtr<AsymmetricKey>* key) const {
    bool is_ed25519 = IsEd25519Key(hw_enforced, sw_enforced);
    bool is_x25519 = IsX25519Key(hw_enforced, sw_enforced);
    if (is_ed25519) {
        if (is_x25519) {
            return KM_ERROR_INCOMPATIBLE_PURPOSE;
        }
        key->reset(new (std::nothrow) Ed25519Key(std::move(hw_enforced), std::move(sw_enforced),
                                                 this));
    } else if (is_x25519) {
        key->reset(new (std::nothrow) X25519Key(std::move(hw_enforced), std::move(sw_enforced),
                                                this));
    } else {
        key->reset(new (std::nothrow) EcKey(std::move(hw_enforced), std::move(sw_enforced), this));
    }
    if (!(*key)) return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    return KM_ERROR_OK;
}

}  // namespace keymaster
