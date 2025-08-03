/*
 * Copyright 2021 The Android Open Source Project
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

#include <keymaster/km_openssl/ecdh_operation.h>

#include <utility>
#include <vector>

#include <keymaster/km_openssl/ec_key.h>
#include <keymaster/km_openssl/openssl_err.h>
#include <keymaster/km_openssl/openssl_utils.h>
#include <keymaster/logger.h>
#include <openssl/curve25519.h>
#include <openssl/err.h>

namespace keymaster {

keymaster_error_t EcdhOperation::Begin(const AuthorizationSet& /*input_params*/,
                                       AuthorizationSet* /*output_params*/) {
    auto rc = GenerateRandom(reinterpret_cast<uint8_t*>(&operation_handle_),
                             (size_t)sizeof(operation_handle_));
    if (rc != KM_ERROR_OK) {
        return rc;
    }
    return KM_ERROR_OK;
}

keymaster_error_t EcdhOperation::Update(const AuthorizationSet& /*additional_params*/,
                                        const Buffer& /*input*/,
                                        AuthorizationSet* /*output_params*/, Buffer* /*output*/,
                                        size_t* /*input_consumed*/) {
    return KM_ERROR_OK;
}

keymaster_error_t EcdhOperation::Finish(const AuthorizationSet& /*additional_params*/,
                                        const Buffer& input, const Buffer& /*signature*/,
                                        AuthorizationSet* /*output_params*/, Buffer* output) {
    const unsigned char* encodedPublicKey = input.begin();
    EVP_PKEY* pkeyRaw = d2i_PUBKEY(nullptr, &encodedPublicKey, input.available_read());
    if (pkeyRaw == nullptr) {
        LOG_E("Error decoding key");
        return KM_ERROR_INVALID_ARGUMENT;
    }
    auto pkey = EVP_PKEY_Ptr(pkeyRaw);

    auto ctx = EVP_PKEY_CTX_Ptr(EVP_PKEY_CTX_new(ecdh_key_.get(), nullptr));
    if (ctx.get() == nullptr) {
        LOG_E("Memory allocation failed");
        return TranslateLastOpenSslError();
    }
    if (EVP_PKEY_derive_init(ctx.get()) != 1) {
        LOG_E("Context initialization failed");
        return TranslateLastOpenSslError();
    }
    if (EVP_PKEY_derive_set_peer(ctx.get(), pkey.get()) != 1) {
        LOG_E("Error setting peer key");
        return KM_ERROR_INVALID_ARGUMENT;
    }
    size_t sharedSecretLen = 0;
    if (EVP_PKEY_derive(ctx.get(), nullptr, &sharedSecretLen) != 1) {
        LOG_E("Error deriving key");
        return TranslateLastOpenSslError();
    }
    if (!output->reserve(sharedSecretLen)) {
        LOG_E("Error reserving data in output buffer");
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }
    if (EVP_PKEY_derive(ctx.get(), output->peek_write(), &sharedSecretLen) != 1) {
        LOG_E("Error deriving key");
        return TranslateLastOpenSslError();
    }
    output->advance_write(sharedSecretLen);

    return KM_ERROR_OK;
}

keymaster_error_t X25519Operation::Finish(const AuthorizationSet& /*additional_params*/,
                                          const Buffer& input, const Buffer& /*signature*/,
                                          AuthorizationSet* /*output_params*/, Buffer* output) {
    // Retrieve the peer X25519 key from within the ASN.1 SubjectPublicKeyInfo.
    const unsigned char* encodedPublicKey = input.begin();
    EVP_PKEY* pkeyRaw = d2i_PUBKEY(nullptr, &encodedPublicKey, input.available_read());
    if (pkeyRaw == nullptr) {
        LOG_E("Error decoding key");
        return KM_ERROR_INVALID_ARGUMENT;
    }
    auto pkey = EVP_PKEY_Ptr(pkeyRaw);

    int pkey_type = EVP_PKEY_id(pkey.get());
    if (pkey_type != EVP_PKEY_X25519) {
        LOG_E("Unexpected peer public key type %d", pkey_type);
        return KM_ERROR_INVALID_ARGUMENT;
    }

    size_t pub_key_len = X25519_PUBLIC_VALUE_LEN;
    uint8_t pub_key[X25519_PUBLIC_VALUE_LEN];
    if (EVP_PKEY_get_raw_public_key(pkey.get(), pub_key, &pub_key_len) == 0) {
        LOG_E("Error extracting key");
        return KM_ERROR_INVALID_ARGUMENT;
    }
    if (pub_key_len != X25519_PUBLIC_VALUE_LEN) {
        LOG_E("Invalid length %zu of peer key", pub_key_len);
        return KM_ERROR_INVALID_ARGUMENT;
    }

    size_t key_len = X25519_PRIVATE_KEY_LEN;
    uint8_t priv_key[X25519_PRIVATE_KEY_LEN];
    if (EVP_PKEY_get_raw_private_key(ecdh_key_.get(), priv_key, &key_len) == 0) {
        return TranslateLastOpenSslError();
    }
    if (key_len != X25519_PRIVATE_KEY_LEN) {
        return KM_ERROR_UNKNOWN_ERROR;
    }
    if (!output->reserve(X25519_SHARED_KEY_LEN)) {
        LOG_E("Error reserving data in output buffer");
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }
    if (X25519(output->peek_write(), priv_key, pub_key) != 1) {
        LOG_E("Error deriving key");
        return TranslateLastOpenSslError();
    }
    output->advance_write(X25519_SHARED_KEY_LEN);

    return KM_ERROR_OK;
}

OperationPtr EcdhOperationFactory::CreateOperation(Key&& key,
                                                   const AuthorizationSet& /*begin_params*/,
                                                   keymaster_error_t* error) {
    const AsymmetricKey& ecdh_key = static_cast<AsymmetricKey&>(key);

    EVP_PKEY_Ptr pkey(ecdh_key.InternalToEvp());
    if (pkey.get() == nullptr) {
        *error = KM_ERROR_UNKNOWN_ERROR;
        return nullptr;
    }

    *error = KM_ERROR_OK;

    EcdhOperation* op = nullptr;
    switch (EVP_PKEY_id(pkey.get())) {
    case EVP_PKEY_X25519:
        op = new (std::nothrow) X25519Operation(std::move(key.hw_enforced_move()),
                                                std::move(key.sw_enforced_move()), pkey.release());
        break;
    case EVP_PKEY_EC:
        op = new (std::nothrow) EcdhOperation(std::move(key.hw_enforced_move()),
                                              std::move(key.sw_enforced_move()), pkey.release());
        break;
    default:
        *error = KM_ERROR_UNKNOWN_ERROR;
        return nullptr;
    }

    if (!op) {
        *error = KM_ERROR_MEMORY_ALLOCATION_FAILED;
        return nullptr;
    }
    return OperationPtr(op);
}

}  // namespace keymaster
