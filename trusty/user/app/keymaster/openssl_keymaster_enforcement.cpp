/*
**
** Copyright 2018, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#include "openssl_keymaster_enforcement.h"

#include <assert.h>
#include <inttypes.h>
#include <utility>

#include <openssl/cmac.h>
#include <openssl/evp.h>
#include <openssl/hmac.h>
#include <openssl/rand.h>

#include <keymaster/km_openssl/ckdf.h>
#include <keymaster/km_openssl/openssl_err.h>
#include <keymaster/km_openssl/openssl_utils.h>

#include <lib/hwkey/hwkey.h>

namespace keymaster {

namespace {

constexpr const char* kSharedHmacLabel = "KeymasterSharedMac";
constexpr const char* kMacVerificationString = "Keymaster HMAC Verification";
constexpr const char* kAuthVerificationLabel = "Auth Verification";

// Size (in bytes) of the HBK used for UNIQUE_ID generation.
static const int kUniqueIdHbkSize = 32;

// Label used for CKDF derivation of UNIQUE_ID HBK from KAK.
constexpr const char* kUniqueIdLabel = "UniqueID HBK 32B";

class EvpMdCtx {
public:
    EvpMdCtx() { EVP_MD_CTX_init(&ctx_); }
    ~EvpMdCtx() { EVP_MD_CTX_cleanup(&ctx_); }

    EVP_MD_CTX* get() { return &ctx_; }

private:
    EVP_MD_CTX ctx_;
};

}  // anonymous namespace

bool OpenSSLKeymasterEnforcement::CreateKeyId(
        const keymaster_key_blob_t& key_blob,
        km_id_t* keyid) const {
    EvpMdCtx ctx;

    uint8_t hash[EVP_MAX_MD_SIZE];
    unsigned int hash_len;
    if (EVP_DigestInit_ex(ctx.get(), EVP_sha256(), nullptr /* ENGINE */) &&
        EVP_DigestUpdate(ctx.get(), key_blob.key_material,
                         key_blob.key_material_size) &&
        EVP_DigestFinal_ex(ctx.get(), hash, &hash_len)) {
        assert(hash_len >= sizeof(*keyid));
        memcpy(keyid, hash, sizeof(*keyid));
        return true;
    }

    return false;
}

keymaster_error_t OpenSSLKeymasterEnforcement::GetHmacSharingParameters(
        HmacSharingParameters* params) {
    if (!have_saved_params_) {
        saved_params_.seed = {};
        RAND_bytes(saved_params_.nonce, 32);
        have_saved_params_ = true;
    }
    params->seed = saved_params_.seed;
    memcpy(params->nonce, saved_params_.nonce, sizeof(params->nonce));
    return KM_ERROR_OK;
}

namespace {

DEFINE_OPENSSL_OBJECT_POINTER(HMAC_CTX);

keymaster_error_t hmacSha256(const keymaster_key_blob_t& key,
                             const keymaster_blob_t data_chunks[],
                             size_t data_chunk_count,
                             KeymasterBlob* output) {
    if (!output)
        return KM_ERROR_UNEXPECTED_NULL_POINTER;

    unsigned digest_len = SHA256_DIGEST_LENGTH;
    if (!output->Reset(digest_len))
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;

    HMAC_CTX_Ptr ctx(HMAC_CTX_new());
    if (!HMAC_Init_ex(ctx.get(), key.key_material, key.key_material_size,
                      EVP_sha256(), nullptr /* engine*/)) {
        return TranslateLastOpenSslError();
    }

    for (size_t i = 0; i < data_chunk_count; i++) {
        auto& chunk = data_chunks[i];
        if (!HMAC_Update(ctx.get(), chunk.data, chunk.data_length)) {
            return TranslateLastOpenSslError();
        }
    }

    if (!HMAC_Final(ctx.get(), output->writable_data(), &digest_len)) {
        return TranslateLastOpenSslError();
    }

    if (digest_len != output->data_length)
        return KM_ERROR_UNKNOWN_ERROR;

    return KM_ERROR_OK;
}

// Helpers for converting types to keymaster_blob_t, for easy feeding of
// hmacSha256.
template <typename T>
inline keymaster_blob_t toBlob(const T& t) {
    return {reinterpret_cast<const uint8_t*>(&t), sizeof(t)};
}
inline keymaster_blob_t toBlob(const char* str) {
    return {reinterpret_cast<const uint8_t*>(str), strlen(str)};
}
inline keymaster_blob_t toBlob(const std::vector<uint8_t>& data) {
    return {data.data(), data.size()};
}

// Perhaps these shoud be in utils, but the impact of that needs to be
// considered carefully.  For now, just define it here.
inline bool operator==(const keymaster_blob_t& a, const keymaster_blob_t& b) {
    if (!a.data_length && !b.data_length)
        return true;
    if (!(a.data && b.data))
        return a.data == b.data;
    return (a.data_length == b.data_length &&
            !memcmp(a.data, b.data, a.data_length));
}

bool operator==(const HmacSharingParameters& a,
                const HmacSharingParameters& b) {
    return a.seed == b.seed && !memcmp(a.nonce, b.nonce, sizeof(a.nonce));
}

}  // namespace

keymaster_error_t OpenSSLKeymasterEnforcement::ComputeSharedHmac(
        const HmacSharingParametersArray& params_array,
        KeymasterBlob* sharingCheck) {
    KeymasterKeyBlob kak;
    keymaster_error_t kakError = GetKeyAgreementKey(&kak);
    if (kakError != KM_ERROR_OK) {
        return kakError;
    }

    size_t num_chunks = params_array.num_params * 2;
    UniquePtr<keymaster_blob_t[]> context_chunks(
            new (std::nothrow) keymaster_blob_t[num_chunks]);
    if (!context_chunks.get())
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;

    bool found_mine = false;
    auto context_chunks_pos = context_chunks.get();
    for (auto& params :
         array_range(params_array.params_array, params_array.num_params)) {
        *context_chunks_pos++ = params.seed;
        *context_chunks_pos++ = {params.nonce, sizeof(params.nonce)};
        found_mine = found_mine || params == saved_params_;
    }
    assert(context_chunks_pos - num_chunks == context_chunks.get());

    if (!found_mine)
        return KM_ERROR_INVALID_ARGUMENT;

    if (!hmac_key_.Reset(SHA256_DIGEST_LENGTH))
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    keymaster_error_t error = ckdf(
            std::move(kak),
            KeymasterBlob(reinterpret_cast<const uint8_t*>(kSharedHmacLabel),
                          strlen(kSharedHmacLabel)),
            context_chunks.get(), num_chunks,  //
            &hmac_key_);
    if (error != KM_ERROR_OK)
        return error;

    keymaster_blob_t data = {
            reinterpret_cast<const uint8_t*>(kMacVerificationString),
            strlen(kMacVerificationString)};
    keymaster_blob_t data_chunks[] = {data};
    return hmacSha256(hmac_key_, data_chunks, 1, sharingCheck);
}

VerifyAuthorizationResponse OpenSSLKeymasterEnforcement::VerifyAuthorization(
        const VerifyAuthorizationRequest& request) {
    // The only thing this implementation provides is timestamp and security
    // level.  Note that this is an acceptable implementation strategy for
    // production use as well.  Additional verification need only be provided by
    // an implementation if it is interoperating with another implementation
    // that requires more.
    VerifyAuthorizationResponse response(request.message_version);
    response.token.challenge = request.challenge;
    response.token.timestamp = get_current_time_ms();
    response.token.security_level = SecurityLevel();
    keymaster_blob_t data_chunks[] = {
            toBlob(kAuthVerificationLabel),
            toBlob(response.token.challenge),
            toBlob(response.token.timestamp),
            toBlob(response.token.security_level),
            {},  // parametersVerified
    };
    response.error = hmacSha256(hmac_key_, data_chunks, 5, &response.token.mac);

    return response;
}

keymaster_error_t OpenSSLKeymasterEnforcement::GetKeyAgreementKey(
        KeymasterKeyBlob* kak) const {
    uint32_t keySize = kKeyAgreementKeySize;

    if (!kak->Reset(keySize)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    int rc = hwkey_open();
    if (rc < 0) {
        LOG_E("Failed to connect to hwkey: %d", rc);
        return KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
    }
    hwkey_session_t hwkey_session = (hwkey_session_t)rc;

    rc = hwkey_get_keyslot_data(hwkey_session, "com.android.trusty.keymint.kak",
                                kak->writable_data(), &keySize);

    hwkey_close(hwkey_session);

    if (rc < 0) {
        LOG_E("Getting KAK failed.\n");
        return KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
    }

    if (keySize != kKeyAgreementKeySize) {
        LOG_E("KAK has the wrong size: %" PRIu32 " != %zu.\n", keySize,
              kKeyAgreementKeySize);
        return KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
    }

    return KM_ERROR_OK;
}

KmErrorOr<std::array<uint8_t, 32>> OpenSSLKeymasterEnforcement::ComputeHmac(
        const std::vector<uint8_t>& data_to_mac) const {
    keymaster_blob_t data_chunks[] = {toBlob(data_to_mac)};
    KeymasterBlob mac;
    auto error = hmacSha256(hmac_key_, data_chunks, 1, &mac);
    if (error != KM_ERROR_OK) {
        return error;
    }

    std::array<uint8_t, 32> retval;
    if (mac.size() != retval.size()) {
        return KM_ERROR_UNKNOWN_ERROR;
    }
    std::copy(mac.begin(), mac.end(), retval.begin());
    return retval;
}

keymaster_error_t OpenSSLKeymasterEnforcement::GetHmacKey(
        keymaster_key_blob_t* key) const {
    if ((key == nullptr) || (key->key_material == nullptr)) {
        return KM_ERROR_UNEXPECTED_NULL_POINTER;
    }

    if (hmac_key_.key_material_size != SHA256_DIGEST_LENGTH) {
        return KM_ERROR_INVALID_ARGUMENT;
    }

    memcpy((void*)key->key_material, hmac_key_.key_material,
           hmac_key_.key_material_size);
    key->key_material_size = hmac_key_.key_material_size;

    return KM_ERROR_OK;
}

keymaster_error_t OpenSSLKeymasterEnforcement::GetUniqueIdKey(
        KeymasterKeyBlob* key) const {
    // Derive a unique ID HBK from the key agreement key.
    KeymasterKeyBlob kak;
    keymaster_error_t kakError = GetKeyAgreementKey(&kak);
    if (kakError != KM_ERROR_OK) {
        return kakError;
    }
    if (!key->Reset(kUniqueIdHbkSize)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    return ckdf(std::move(kak),
                KeymasterBlob(reinterpret_cast<const uint8_t*>(kUniqueIdLabel),
                              strlen(kUniqueIdLabel)),
                nullptr, 0, key);
}

}  // namespace keymaster
