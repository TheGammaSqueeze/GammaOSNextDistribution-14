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

#include <keymaster/km_openssl/openssl_utils.h>

#include <keymaster/android_keymaster_utils.h>
#include <openssl/mem.h>
#include <openssl/rand.h>

#include <keymaster/km_openssl/openssl_err.h>

namespace keymaster {

constexpr uint32_t kAffinePointLength = 32;

keymaster_error_t ec_get_group_size(const EC_GROUP* group, size_t* key_size_bits) {
    switch (EC_GROUP_get_curve_name(group)) {
    case NID_secp224r1:
        *key_size_bits = 224;
        break;
    case NID_X9_62_prime256v1:
        *key_size_bits = 256;
        break;
    case NID_secp384r1:
        *key_size_bits = 384;
        break;
    case NID_secp521r1:
        *key_size_bits = 521;
        break;
    default:
        return KM_ERROR_UNSUPPORTED_EC_FIELD;
    }
    return KM_ERROR_OK;
}

EC_GROUP* ec_get_group(keymaster_ec_curve_t curve) {
    switch (curve) {
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

keymaster_error_t convert_pkcs8_blob_to_evp(const uint8_t* key_data, size_t key_length,
                                            keymaster_algorithm_t expected_algorithm,
                                            UniquePtr<EVP_PKEY, EVP_PKEY_Delete>* pkey) {
    if (key_data == nullptr || key_length <= 0) return KM_ERROR_INVALID_KEY_BLOB;

    UniquePtr<PKCS8_PRIV_KEY_INFO, PKCS8_PRIV_KEY_INFO_Delete> pkcs8(
        d2i_PKCS8_PRIV_KEY_INFO(nullptr, &key_data, key_length));
    if (pkcs8.get() == nullptr) return TranslateLastOpenSslError(true /* log_message */);

    pkey->reset(EVP_PKCS82PKEY(pkcs8.get()));
    if (!pkey->get()) return TranslateLastOpenSslError(true /* log_message */);

    // Check the key type detected from the PKCS8 blob matches the KM algorithm we expect.
    keymaster_algorithm_t got_algorithm;
    switch (EVP_PKEY_id(pkey->get())) {
    case EVP_PKEY_RSA:
        got_algorithm = KM_ALGORITHM_RSA;
        break;
    case EVP_PKEY_EC:
    case EVP_PKEY_ED25519:
    case EVP_PKEY_X25519:
        got_algorithm = KM_ALGORITHM_EC;
        break;
    default:
        LOG_E("EVP key algorithm was unknown (type %d), not the expected %d",
              EVP_PKEY_id(pkey->get()), expected_algorithm);
        return KM_ERROR_INVALID_KEY_BLOB;
    }

    if (expected_algorithm != got_algorithm) {
        LOG_E("EVP key algorithm was %d (from type %d), not the expected %d", got_algorithm,
              EVP_PKEY_id(pkey->get()), expected_algorithm);
        return KM_ERROR_INVALID_KEY_BLOB;
    }

    return KM_ERROR_OK;
}

keymaster_error_t KeyMaterialToEvpKey(keymaster_key_format_t key_format,
                                      const KeymasterKeyBlob& key_material,
                                      keymaster_algorithm_t expected_algorithm,
                                      EVP_PKEY_Ptr* pkey) {
    if (key_format != KM_KEY_FORMAT_PKCS8) return KM_ERROR_UNSUPPORTED_KEY_FORMAT;

    return convert_pkcs8_blob_to_evp(key_material.key_material, key_material.key_material_size,
                                     expected_algorithm, pkey);
}

keymaster_error_t EvpKeyToKeyMaterial(const EVP_PKEY* pkey, KeymasterKeyBlob* key_blob) {
    switch (EVP_PKEY_id(pkey)) {
    case EVP_PKEY_ED25519:
    case EVP_PKEY_X25519: {
        // BoringSSL's i2d_PrivateKey does not handle curve 25519 keys.
        uint8_t* data = nullptr;
        size_t data_len;
        CBB cbb;
        if (!CBB_init(&cbb, 0) || !EVP_marshal_private_key(&cbb, pkey) ||
            !CBB_finish(&cbb, &data, &data_len)) {
            CBB_cleanup(&cbb);
            OPENSSL_free(data);
            return KM_ERROR_UNKNOWN_ERROR;
        }

        if (!key_blob->Reset(data_len)) {
            OPENSSL_free(data);
            return KM_ERROR_MEMORY_ALLOCATION_FAILED;
        }
        memcpy(key_blob->writable_data(), data, data_len);
        OPENSSL_free(data);
        break;
    }
    default: {
        int key_data_size = i2d_PrivateKey(pkey, nullptr /* key_data*/);
        if (key_data_size <= 0) return TranslateLastOpenSslError();

        if (!key_blob->Reset(key_data_size)) return KM_ERROR_MEMORY_ALLOCATION_FAILED;

        uint8_t* tmp = key_blob->writable_data();
        i2d_PrivateKey(pkey, &tmp);
        break;
    }
    }

    return KM_ERROR_OK;
}

// Remote provisioning helper function
keymaster_error_t GetEcdsa256KeyFromCert(const keymaster_blob_t* km_cert, uint8_t* x_coord,
                                         size_t x_length, uint8_t* y_coord, size_t y_length) {
    if (km_cert == nullptr || x_coord == nullptr || y_coord == nullptr) {
        return KM_ERROR_UNEXPECTED_NULL_POINTER;
    }
    if (x_length != kAffinePointLength || y_length != kAffinePointLength) {
        return KM_ERROR_INVALID_ARGUMENT;
    }
    const uint8_t* temp = km_cert->data;
    X509_Ptr cert(d2i_X509(NULL, &temp, km_cert->data_length));
    if (!cert.get()) return TranslateLastOpenSslError();
    EVP_PKEY_Ptr pubKey(X509_get_pubkey(cert.get()));
    if (!pubKey.get()) return TranslateLastOpenSslError();
    EC_KEY* ecKey = EVP_PKEY_get0_EC_KEY(pubKey.get());
    if (!ecKey) return TranslateLastOpenSslError();
    const EC_POINT* jacobian_coords = EC_KEY_get0_public_key(ecKey);
    if (!jacobian_coords) return TranslateLastOpenSslError();
    bssl::UniquePtr<BIGNUM> x(BN_new());
    bssl::UniquePtr<BIGNUM> y(BN_new());
    BN_CTX_Ptr ctx(BN_CTX_new());
    if (!ctx.get()) return TranslateLastOpenSslError();
    if (!EC_POINT_get_affine_coordinates_GFp(EC_KEY_get0_group(ecKey), jacobian_coords, x.get(),
                                             y.get(), ctx.get())) {
        return TranslateLastOpenSslError();
    }
    uint8_t* tmp_x = x_coord;
    if (BN_bn2binpad(x.get(), tmp_x, kAffinePointLength) != kAffinePointLength) {
        return TranslateLastOpenSslError();
    }
    uint8_t* tmp_y = y_coord;
    if (BN_bn2binpad(y.get(), tmp_y, kAffinePointLength) != kAffinePointLength) {
        return TranslateLastOpenSslError();
    }
    return KM_ERROR_OK;
}

size_t ec_group_size_bits(EC_KEY* ec_key) {
    const EC_GROUP* group = EC_KEY_get0_group(ec_key);
    UniquePtr<BN_CTX, BN_CTX_Delete> bn_ctx(BN_CTX_new());
    UniquePtr<BIGNUM, BIGNUM_Delete> order(BN_new());
    if (!EC_GROUP_get_order(group, order.get(), bn_ctx.get())) {
        LOG_E("Failed to get EC group order");
        return 0;
    }
    return BN_num_bits(order.get());
}

keymaster_error_t GenerateRandom(uint8_t* buf, size_t length) {
    if (RAND_bytes(buf, length) != 1) return KM_ERROR_UNKNOWN_ERROR;
    return KM_ERROR_OK;
}

}  // namespace keymaster
