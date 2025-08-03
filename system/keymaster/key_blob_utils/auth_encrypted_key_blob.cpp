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

#include <keymaster/key_blob_utils/auth_encrypted_key_blob.h>

#include <utility>

#include <openssl/digest.h>
#include <openssl/evp.h>
#include <openssl/hkdf.h>

#include <keymaster/android_keymaster_utils.h>
#include <keymaster/authorization_set.h>
#include <keymaster/key_blob_utils/ocb_utils.h>
#include <keymaster/km_openssl/openssl_err.h>
#include <keymaster/logger.h>
#include <keymaster/random_source.h>

namespace keymaster {

namespace {

constexpr uint8_t kAesGcmDescriptor1[] = "AES-256-GCM-HKDF-SHA-256, version 1";
constexpr uint8_t kAesGcmDescriptor2[] = "AES-256-GCM-HKDF-SHA-256, version 2";
constexpr size_t kAesGcmNonceLength = 12;
constexpr size_t kAesGcmTagLength = 16;
constexpr size_t kAes256KeyLength = 256 / 8;

KmErrorOr<Buffer> generate_nonce(const RandomSource& random, size_t size) {
    Buffer nonce;
    if (!nonce.Reinitialize(size)) return KM_ERROR_MEMORY_ALLOCATION_FAILED;

    random.GenerateRandom(nonce.peek_write(), size);
    nonce.advance_write(size);
    return nonce;
}

KmErrorOr<Buffer> BuildDerivationInfo(const AuthEncryptedBlobFormat format,  //
                                      const AuthorizationSet& hw_enforced,   //
                                      const AuthorizationSet& sw_enforced,   //
                                      const AuthorizationSet& hidden,
                                      const SecureDeletionData& secure_deletion_data) {
    bool use_sdd = requiresSecureDeletion(format);

    size_t info_len =
        hidden.SerializedSize() + hw_enforced.SerializedSize() + sw_enforced.SerializedSize();
    if (use_sdd) {
        info_len += sizeof(kAesGcmDescriptor2) +
                    secure_deletion_data.factory_reset_secret.SerializedSize() +
                    secure_deletion_data.secure_deletion_secret.SerializedSize() +
                    sizeof(secure_deletion_data.key_slot);
    } else {
        info_len += sizeof(kAesGcmDescriptor1);
    }

    Buffer info(info_len);
    info.write(use_sdd ? kAesGcmDescriptor2 : kAesGcmDescriptor1);
    uint8_t* buf = info.peek_write();
    const uint8_t* end = info.peek_write() + info.available_write();
    buf = hidden.Serialize(buf, end);
    buf = hw_enforced.Serialize(buf, end);
    buf = sw_enforced.Serialize(buf, end);

    if (use_sdd) {
        buf = secure_deletion_data.factory_reset_secret.Serialize(buf, end);
        buf = secure_deletion_data.secure_deletion_secret.Serialize(buf, end);
        static_assert(std::is_same_v<decltype(secure_deletion_data.key_slot), uint32_t>);
        buf = append_uint32_to_buf(buf, end, secure_deletion_data.key_slot);
    }

    if (!buf || buf != end || !info.advance_write(buf - info.peek_write())) {
        LOG_S("Buffer management error");
        return KM_ERROR_UNKNOWN_ERROR;
    }

    return info;
}

KmErrorOr<Buffer> DeriveAesGcmKeyEncryptionKey(const AuthEncryptedBlobFormat format,            //
                                               const AuthorizationSet& hw_enforced,             //
                                               const AuthorizationSet& sw_enforced,             //
                                               const AuthorizationSet& hidden,                  //
                                               const SecureDeletionData& secure_deletion_data,  //
                                               const KeymasterKeyBlob& master_key) {
    Buffer prk(EVP_MAX_MD_SIZE);
    size_t out_len = EVP_MAX_MD_SIZE;
    if (!HKDF_extract(prk.peek_write(), &out_len, EVP_sha256(), master_key.key_material,
                      master_key.key_material_size, nullptr /* salt */, 0 /* salt_len */)) {
        return TranslateLastOpenSslError();
    }

    KmErrorOr<Buffer> info =
        BuildDerivationInfo(format, hw_enforced, sw_enforced, hidden, secure_deletion_data);
    if (!info) return info.error();

    if (!prk.advance_write(out_len) || !prk.available_read() || !info->available_read()) {
        return KM_ERROR_UNKNOWN_ERROR;
    }

    Buffer keyEncryptionKey(kAes256KeyLength);
    if (!HKDF_expand(keyEncryptionKey.peek_write(), keyEncryptionKey.available_write(),  //
                     EVP_sha256(),                                                       //
                     prk.peek_read(), prk.available_read(),                              //
                     info->peek_read(), info->available_read())) {
        return TranslateLastOpenSslError();
    }

    return keyEncryptionKey;
}

KmErrorOr<EncryptedKey> AesGcmEncryptKey(const AuthorizationSet& hw_enforced,             //
                                         const AuthorizationSet& sw_enforced,             //
                                         const AuthorizationSet& hidden,                  //
                                         const SecureDeletionData& secure_deletion_data,  //
                                         const KeymasterKeyBlob& master_key,              //
                                         const KeymasterKeyBlob& plaintext,               //
                                         const AuthEncryptedBlobFormat format,            //
                                         Buffer nonce) {
    KmErrorOr<Buffer> kek = DeriveAesGcmKeyEncryptionKey(format, hw_enforced, sw_enforced, hidden,
                                                         secure_deletion_data, master_key);
    if (!kek) return kek.error();

    bssl::UniquePtr<EVP_CIPHER_CTX> ctx(EVP_CIPHER_CTX_new());
    if (!ctx) return KM_ERROR_MEMORY_ALLOCATION_FAILED;

    int ciphertext_len = plaintext.size();
    int unused_len = 0;
    EncryptedKey retval;
    retval.format = format;
    retval.ciphertext = KeymasterKeyBlob(ciphertext_len);
    retval.nonce = std::move(nonce);
    retval.tag = Buffer(kAesGcmTagLength);

    if (!(EVP_EncryptInit_ex(ctx.get(), EVP_aes_256_gcm(), nullptr /* engine */, kek->peek_read(),
                             retval.nonce.peek_read()) &&
          EVP_EncryptUpdate(ctx.get(), retval.ciphertext.writable_data(), &ciphertext_len,
                            plaintext.key_material, plaintext.size()) &&
          EVP_EncryptFinal_ex(ctx.get(), retval.ciphertext.writable_data() /* not written to */,
                              &unused_len) &&
          EVP_CIPHER_CTX_ctrl(ctx.get(), EVP_CTRL_GCM_GET_TAG, kAesGcmTagLength,
                              retval.tag.peek_write()))) {
        return TranslateLastOpenSslError();
    }

    if (plaintext.size() != static_cast<size_t>(ciphertext_len) || 0 != unused_len ||
        !retval.tag.advance_write(kAesGcmTagLength)) {
        return KM_ERROR_UNKNOWN_ERROR;
    }

    return retval;
}

KmErrorOr<KeymasterKeyBlob> AesGcmDecryptKey(const DeserializedKey& key,
                                             const AuthorizationSet& hidden,
                                             const SecureDeletionData& secure_deletion_data,
                                             const KeymasterKeyBlob& master_key) {
    KmErrorOr<Buffer> kek =
        DeriveAesGcmKeyEncryptionKey(key.encrypted_key.format, key.hw_enforced, key.sw_enforced,
                                     hidden, secure_deletion_data, master_key);
    if (!kek) return kek.error();

    bssl::UniquePtr<EVP_CIPHER_CTX> ctx(EVP_CIPHER_CTX_new());
    if (!ctx) return KM_ERROR_MEMORY_ALLOCATION_FAILED;

    int plaintext_len = key.encrypted_key.ciphertext.size();
    int unused_len = 0;
    KeymasterKeyBlob plaintext(plaintext_len);
    if (!(EVP_DecryptInit_ex(ctx.get(), EVP_aes_256_gcm(), nullptr /* engine */, kek->peek_read(),
                             key.encrypted_key.nonce.peek_read()) &&
          EVP_DecryptUpdate(ctx.get(), plaintext.writable_data(), &plaintext_len,
                            key.encrypted_key.ciphertext.key_material,
                            key.encrypted_key.ciphertext.size()) &&
          EVP_CIPHER_CTX_ctrl(ctx.get(), EVP_CTRL_GCM_SET_TAG, kAesGcmTagLength,
                              const_cast<uint8_t*>(key.encrypted_key.tag.peek_read())))) {
        return TranslateLastOpenSslError();
    }

    if (!EVP_DecryptFinal_ex(ctx.get(), plaintext.writable_data() /* not written to */,
                             &unused_len)) {
        return KM_ERROR_INVALID_KEY_BLOB;
    }

    if (key.encrypted_key.ciphertext.size() != plaintext.size() || 0 != unused_len) {
        return KM_ERROR_UNKNOWN_ERROR;
    }

    return plaintext;
}

}  // namespace

KmErrorOr<KeymasterKeyBlob> SerializeAuthEncryptedBlob(const EncryptedKey& encrypted_key,
                                                       const AuthorizationSet& hw_enforced,
                                                       const AuthorizationSet& sw_enforced,
                                                       uint32_t key_slot) {
    bool use_key_slot = requiresSecureDeletion(encrypted_key.format);

    size_t size = 1 /* version byte */ + encrypted_key.nonce.SerializedSize() +
                  encrypted_key.ciphertext.SerializedSize() + encrypted_key.tag.SerializedSize() +
                  hw_enforced.SerializedSize() + sw_enforced.SerializedSize();
    if (use_key_slot) size += sizeof(key_slot);
    if (isVersionedFormat(encrypted_key.format)) {
        size += sizeof(encrypted_key.kdf_version);
        size += sizeof(encrypted_key.addl_info);
    }
    KeymasterKeyBlob retval;
    if (!retval.Reset(size)) return KM_ERROR_MEMORY_ALLOCATION_FAILED;

    uint8_t* buf = retval.writable_data();
    const uint8_t* end = retval.end();

    *buf++ = encrypted_key.format;
    buf = encrypted_key.nonce.Serialize(buf, end);
    buf = encrypted_key.ciphertext.Serialize(buf, end);
    buf = encrypted_key.tag.Serialize(buf, end);
    if (isVersionedFormat(encrypted_key.format)) {
        buf = append_uint32_to_buf(buf, end, encrypted_key.kdf_version);
        buf = append_uint32_to_buf(buf, end, encrypted_key.addl_info);
    }
    buf = hw_enforced.Serialize(buf, end);
    buf = sw_enforced.Serialize(buf, end);
    if (use_key_slot) buf = append_uint32_to_buf(buf, end, key_slot);

    if (buf != retval.end()) return KM_ERROR_UNKNOWN_ERROR;

    return retval;
}

KmErrorOr<DeserializedKey> DeserializeAuthEncryptedBlob(const KeymasterKeyBlob& key_blob) {
    if (!key_blob.key_material || key_blob.key_material_size == 0) return KM_ERROR_INVALID_KEY_BLOB;

    const uint8_t* tmp = key_blob.key_material;
    const uint8_t** buf_ptr = &tmp;
    const uint8_t* end = tmp + key_blob.key_material_size;

    if (end <= *buf_ptr) return KM_ERROR_INVALID_KEY_BLOB;

    DeserializedKey retval{};
    retval.encrypted_key.format = static_cast<AuthEncryptedBlobFormat>(*(*buf_ptr)++);
    if (!retval.encrypted_key.nonce.Deserialize(buf_ptr, end) ||       //
        !retval.encrypted_key.ciphertext.Deserialize(buf_ptr, end) ||  //
        !retval.encrypted_key.tag.Deserialize(buf_ptr, end)) {
        return KM_ERROR_INVALID_KEY_BLOB;
    }

    if (isVersionedFormat(retval.encrypted_key.format)) {
        if (!copy_uint32_from_buf(buf_ptr, end, &retval.encrypted_key.kdf_version) ||
            !copy_uint32_from_buf(buf_ptr, end, &retval.encrypted_key.addl_info)) {
            return KM_ERROR_INVALID_KEY_BLOB;
        }
    }

    if (!retval.hw_enforced.Deserialize(buf_ptr, end) ||  //
        !retval.sw_enforced.Deserialize(buf_ptr, end)) {
        return KM_ERROR_INVALID_KEY_BLOB;
    }

    if (requiresSecureDeletion(retval.encrypted_key.format)) {
        if (!copy_uint32_from_buf(buf_ptr, end, &retval.key_slot)) {
            return KM_ERROR_INVALID_KEY_BLOB;
        }
    }

    if (*buf_ptr != end) return KM_ERROR_INVALID_KEY_BLOB;

    switch (retval.encrypted_key.format) {
    case AES_OCB:
        if (retval.encrypted_key.nonce.available_read() != OCB_NONCE_LENGTH ||
            retval.encrypted_key.tag.available_read() != OCB_TAG_LENGTH) {
            return KM_ERROR_INVALID_KEY_BLOB;
        }
        return retval;

    case AES_GCM_WITH_SW_ENFORCED:
    case AES_GCM_WITH_SECURE_DELETION:
    case AES_GCM_WITH_SW_ENFORCED_VERSIONED:
    case AES_GCM_WITH_SECURE_DELETION_VERSIONED:
        if (retval.encrypted_key.nonce.available_read() != kAesGcmNonceLength ||
            retval.encrypted_key.tag.available_read() != kAesGcmTagLength) {
            return KM_ERROR_INVALID_KEY_BLOB;
        }
        return retval;
    }

    LOG_E("Invalid key blob format %d", retval.encrypted_key.format);
    return KM_ERROR_INVALID_KEY_BLOB;
}

KmErrorOr<EncryptedKey>
EncryptKey(const KeymasterKeyBlob& plaintext, AuthEncryptedBlobFormat format,
           const AuthorizationSet& hw_enforced, const AuthorizationSet& sw_enforced,
           const AuthorizationSet& hidden, const SecureDeletionData& secure_deletion_data,
           const KeymasterKeyBlob& master_key, const RandomSource& random) {
    switch (format) {
    case AES_OCB: {
        EncryptedKey retval;
        retval.format = format;
        auto nonce = generate_nonce(random, OCB_NONCE_LENGTH);
        if (!nonce) return nonce.error();
        retval.nonce = std::move(*nonce);
        retval.tag.Reinitialize(OCB_TAG_LENGTH);
        keymaster_error_t error =
            OcbEncryptKey(hw_enforced, sw_enforced, hidden, master_key, plaintext, retval.nonce,
                          &retval.ciphertext, &retval.tag);
        if (error != KM_ERROR_OK) return error;
        return retval;
    }

    case AES_GCM_WITH_SW_ENFORCED:
    case AES_GCM_WITH_SECURE_DELETION:
    case AES_GCM_WITH_SW_ENFORCED_VERSIONED:
    case AES_GCM_WITH_SECURE_DELETION_VERSIONED: {
        auto nonce = generate_nonce(random, kAesGcmNonceLength);
        if (!nonce) return nonce.error();
        return AesGcmEncryptKey(hw_enforced, sw_enforced, hidden, secure_deletion_data, master_key,
                                plaintext, format, std::move(*nonce));
    }
    }

    LOG_E("Invalid key blob format %d", format);
    return KM_ERROR_UNKNOWN_ERROR;
}

KmErrorOr<KeymasterKeyBlob> DecryptKey(const DeserializedKey& key, const AuthorizationSet& hidden,
                                       const SecureDeletionData& secure_deletion_data,
                                       const KeymasterKeyBlob& master_key) {
    KeymasterKeyBlob retval;
    switch (key.encrypted_key.format) {
    case AES_OCB: {
        keymaster_error_t error = OcbDecryptKey(
            key.hw_enforced, key.sw_enforced, hidden, master_key, key.encrypted_key.ciphertext,
            key.encrypted_key.nonce, key.encrypted_key.tag, &retval);
        if (error != KM_ERROR_OK) return error;
        return retval;
    }

    case AES_GCM_WITH_SW_ENFORCED:
    case AES_GCM_WITH_SECURE_DELETION:
    case AES_GCM_WITH_SW_ENFORCED_VERSIONED:
    case AES_GCM_WITH_SECURE_DELETION_VERSIONED:
        return AesGcmDecryptKey(key, hidden, secure_deletion_data, master_key);
    }

    LOG_E("Invalid key blob format %d", key.encrypted_key.format);
    return KM_ERROR_INVALID_KEY_BLOB;
}

bool requiresSecureDeletion(const AuthEncryptedBlobFormat& fmt) {
    return fmt == AES_GCM_WITH_SECURE_DELETION || fmt == AES_GCM_WITH_SECURE_DELETION_VERSIONED;
}

bool isVersionedFormat(const AuthEncryptedBlobFormat& fmt) {
    return fmt == AES_GCM_WITH_SW_ENFORCED_VERSIONED ||
           fmt == AES_GCM_WITH_SECURE_DELETION_VERSIONED;
}

}  // namespace keymaster
