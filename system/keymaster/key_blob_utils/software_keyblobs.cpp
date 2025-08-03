/*
**
** Copyright 2017, The Android Open Source Project
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

#include <keymaster/key_blob_utils/software_keyblobs.h>

#include <stdint.h>

#include <hardware/keymaster_defs.h>

#include <keymaster/UniquePtr.h>
#include <keymaster/android_keymaster_utils.h>
#include <keymaster/authorization_set.h>
#include <keymaster/key.h>
#include <keymaster/key_blob_utils/auth_encrypted_key_blob.h>
#include <keymaster/key_blob_utils/integrity_assured_key_blob.h>
#include <keymaster/key_blob_utils/ocb_utils.h>
#include <keymaster/km_openssl/openssl_err.h>
#include <keymaster/km_openssl/openssl_utils.h>
#include <keymaster/logger.h>

#include <openssl/aes.h>

namespace keymaster {

static uint8_t SWROT[2] = {'S', 'W'};
KeymasterBlob softwareRootOfTrust(SWROT);

namespace {

bool UpgradeIntegerTag(keymaster_tag_t tag, uint32_t value, AuthorizationSet* set,
                       bool* set_changed) {
    int index = set->find(tag);
    if (index == -1) {
        keymaster_key_param_t param;
        param.tag = tag;
        param.integer = value;
        set->push_back(param);
        *set_changed = true;
        return true;
    }

    if (set->params[index].integer > value) return false;

    if (set->params[index].integer != value) {
        set->params[index].integer = value;
        *set_changed = true;
    }
    return true;
}

keymaster_error_t TranslateAuthorizationSetError(AuthorizationSet::Error err) {
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

}  // anonymous namespace

keymaster_error_t BuildHiddenAuthorizations(const AuthorizationSet& input_set,
                                            AuthorizationSet* hidden,
                                            const KeymasterBlob& root_of_trust) {
    keymaster_blob_t entry;
    if (input_set.GetTagValue(TAG_APPLICATION_ID, &entry))
        hidden->push_back(TAG_APPLICATION_ID, entry.data, entry.data_length);
    if (input_set.GetTagValue(TAG_APPLICATION_DATA, &entry))
        hidden->push_back(TAG_APPLICATION_DATA, entry.data, entry.data_length);

    hidden->push_back(TAG_ROOT_OF_TRUST, root_of_trust);

    return TranslateAuthorizationSetError(hidden->is_valid());
}

keymaster_error_t FakeKeyAuthorizations(EVP_PKEY* pubkey, AuthorizationSet* hw_enforced,
                                        AuthorizationSet* sw_enforced) {
    hw_enforced->Clear();
    sw_enforced->Clear();

    switch (EVP_PKEY_id(pubkey)) {
    case EVP_PKEY_RSA: {
        hw_enforced->push_back(TAG_ALGORITHM, KM_ALGORITHM_RSA);
        hw_enforced->push_back(TAG_DIGEST, KM_DIGEST_NONE);
        hw_enforced->push_back(TAG_DIGEST, KM_DIGEST_MD5);
        hw_enforced->push_back(TAG_DIGEST, KM_DIGEST_SHA1);
        hw_enforced->push_back(TAG_DIGEST, KM_DIGEST_SHA_2_224);
        hw_enforced->push_back(TAG_DIGEST, KM_DIGEST_SHA_2_256);
        hw_enforced->push_back(TAG_DIGEST, KM_DIGEST_SHA_2_384);
        hw_enforced->push_back(TAG_DIGEST, KM_DIGEST_SHA_2_512);
        hw_enforced->push_back(TAG_PADDING, KM_PAD_NONE);
        hw_enforced->push_back(TAG_PADDING, KM_PAD_RSA_PKCS1_1_5_SIGN);
        hw_enforced->push_back(TAG_PADDING, KM_PAD_RSA_PKCS1_1_5_ENCRYPT);
        hw_enforced->push_back(TAG_PADDING, KM_PAD_RSA_PSS);
        hw_enforced->push_back(TAG_PADDING, KM_PAD_RSA_OAEP);

        sw_enforced->push_back(TAG_PURPOSE, KM_PURPOSE_SIGN);
        sw_enforced->push_back(TAG_PURPOSE, KM_PURPOSE_VERIFY);
        sw_enforced->push_back(TAG_PURPOSE, KM_PURPOSE_ENCRYPT);
        sw_enforced->push_back(TAG_PURPOSE, KM_PURPOSE_DECRYPT);

        RSA_Ptr rsa(EVP_PKEY_get1_RSA(pubkey));
        if (!rsa) return TranslateLastOpenSslError();
        hw_enforced->push_back(TAG_KEY_SIZE, RSA_size(rsa.get()) * 8);
        uint64_t public_exponent;
        if (!BN_get_u64(RSA_get0_e(rsa.get()), &public_exponent)) return KM_ERROR_INVALID_KEY_BLOB;
        hw_enforced->push_back(TAG_RSA_PUBLIC_EXPONENT, public_exponent);
        break;
    }

    case EVP_PKEY_EC: {
        hw_enforced->push_back(TAG_ALGORITHM, KM_ALGORITHM_RSA);
        hw_enforced->push_back(TAG_DIGEST, KM_DIGEST_NONE);
        hw_enforced->push_back(TAG_DIGEST, KM_DIGEST_MD5);
        hw_enforced->push_back(TAG_DIGEST, KM_DIGEST_SHA1);
        hw_enforced->push_back(TAG_DIGEST, KM_DIGEST_SHA_2_224);
        hw_enforced->push_back(TAG_DIGEST, KM_DIGEST_SHA_2_256);
        hw_enforced->push_back(TAG_DIGEST, KM_DIGEST_SHA_2_384);
        hw_enforced->push_back(TAG_DIGEST, KM_DIGEST_SHA_2_512);

        sw_enforced->push_back(TAG_PURPOSE, KM_PURPOSE_SIGN);
        sw_enforced->push_back(TAG_PURPOSE, KM_PURPOSE_VERIFY);

        UniquePtr<EC_KEY, EC_KEY_Delete> ec_key(EVP_PKEY_get1_EC_KEY(pubkey));
        if (!ec_key.get()) return TranslateLastOpenSslError();
        size_t key_size_bits;
        keymaster_error_t error =
            ec_get_group_size(EC_KEY_get0_group(ec_key.get()), &key_size_bits);
        if (error != KM_ERROR_OK) return error;
        hw_enforced->push_back(TAG_KEY_SIZE, key_size_bits);
        break;
    }

    default:
        return KM_ERROR_UNSUPPORTED_ALGORITHM;
    }

    sw_enforced->push_back(TAG_ALL_USERS);
    sw_enforced->push_back(TAG_NO_AUTH_REQUIRED);

    return KM_ERROR_OK;
}

// Note: This parsing code in below is from system/security/softkeymaster/keymaster_openssl.cpp's
// unwrap_key function, modified for the preferred function signature and formatting.  It does some
// odd things, but they have been left unchanged to avoid breaking compatibility.
static const uint8_t SOFT_KEY_MAGIC[] = {'P', 'K', '#', '8'};
keymaster_error_t ParseOldSoftkeymasterBlob(const KeymasterKeyBlob& blob,
                                            KeymasterKeyBlob* key_material,
                                            AuthorizationSet* hw_enforced,
                                            AuthorizationSet* sw_enforced) {
    long publicLen = 0;   // NOLINT(google-runtime-int)
    long privateLen = 0;  // NOLINT(google-runtime-int)
    const uint8_t* p = blob.key_material;
    const uint8_t* end = blob.key_material + blob.key_material_size;

    int type = 0;
    ptrdiff_t min_size =
        sizeof(SOFT_KEY_MAGIC) + sizeof(type) + sizeof(publicLen) + 1 + sizeof(privateLen) + 1;
    if (end - p < min_size) {
        LOG_W("key blob appears to be truncated (if an old SW key)");
        return KM_ERROR_INVALID_KEY_BLOB;
    }

    if (memcmp(p, SOFT_KEY_MAGIC, sizeof(SOFT_KEY_MAGIC)) != 0) return KM_ERROR_INVALID_KEY_BLOB;
    p += sizeof(SOFT_KEY_MAGIC);

    for (size_t i = 0; i < sizeof(type); i++) {
        type = (type << 8) | *p++;
    }

    for (size_t i = 0; i < sizeof(type); i++) {
        publicLen = (publicLen << 8) | *p++;
    }

    if (p + publicLen > end) {
        LOG_W("public key length encoding error: size=%ld, end=%td", publicLen, end - p);
        return KM_ERROR_INVALID_KEY_BLOB;
    }
    p += publicLen;

    if (end - p < sizeof(type)) {
        LOG_W("key blob appears to be truncated (if an old SW key)");
        return KM_ERROR_INVALID_KEY_BLOB;
    }

    for (size_t i = 0; i < sizeof(type); i++)
        privateLen = (privateLen << 8) | *p++;

    if (p + privateLen > end) {
        LOG_W("private key length encoding error: size=%ld, end=%td", privateLen, end - p);
        return KM_ERROR_INVALID_KEY_BLOB;
    }

    // Just to be sure, make sure that the ASN.1 structure parses correctly.  We don't actually use
    // the EVP_PKEY here.
    const uint8_t* key_start = p;
    EVP_PKEY_Ptr pkey(d2i_PrivateKey(type, nullptr, &p, privateLen));
    if (pkey.get() == nullptr) {
        LOG_W("Failed to parse PKCS#8 key material (if old SW key)");
        return KM_ERROR_INVALID_KEY_BLOB;
    }

    // All auths go into sw_enforced, including those that would be HW-enforced if we were faking
    // auths for a HW-backed key.
    hw_enforced->Clear();
    keymaster_error_t error = FakeKeyAuthorizations(pkey.get(), sw_enforced, sw_enforced);
    if (error != KM_ERROR_OK) return error;

    if (!key_material->Reset(privateLen)) return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    memcpy(key_material->writable_data(), key_start, privateLen);

    return KM_ERROR_OK;
}

static uint8_t master_key_bytes[AES_BLOCK_SIZE] = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
const KeymasterKeyBlob MASTER_KEY(master_key_bytes, array_length(master_key_bytes));

keymaster_error_t ParseAuthEncryptedBlob(const KeymasterKeyBlob& blob,
                                         const AuthorizationSet& hidden,
                                         KeymasterKeyBlob* key_material,
                                         AuthorizationSet* hw_enforced,
                                         AuthorizationSet* sw_enforced) {
    KmErrorOr<DeserializedKey> key = DeserializeAuthEncryptedBlob(blob);
    if (!key) return key.error();

    KmErrorOr<KeymasterKeyBlob> decrypted =
        DecryptKey(*key, hidden, SecureDeletionData(), MASTER_KEY);
    if (!decrypted) return decrypted.error();

    *key_material = std::move(*decrypted);
    *hw_enforced = std::move(key->hw_enforced);
    *sw_enforced = std::move(key->sw_enforced);

    return KM_ERROR_OK;
}

keymaster_error_t SetKeyBlobAuthorizations(const AuthorizationSet& key_description,
                                           keymaster_key_origin_t origin, uint32_t os_version,
                                           uint32_t os_patchlevel, AuthorizationSet* hw_enforced,
                                           AuthorizationSet* sw_enforced, KmVersion version) {
    sw_enforced->Clear();

    for (auto& entry : key_description) {
        switch (entry.tag) {
        // These cannot be specified by the client.
        case KM_TAG_BOOT_PATCHLEVEL:
        case KM_TAG_ORIGIN:
        case KM_TAG_OS_PATCHLEVEL:
        case KM_TAG_OS_VERSION:
        case KM_TAG_ROOT_OF_TRUST:
        case KM_TAG_VENDOR_PATCHLEVEL:
            LOG_E("Root of trust and origin tags may not be specified");
            return KM_ERROR_INVALID_TAG;

        case KM_TAG_ALLOW_WHILE_ON_BODY:
            // Not supported, but is specified to noop in that case (vs error).
            LOG_W("No on-body detection supported, skipping tag %d", entry.tag);
            break;

        // These aren't supported by SoftKeymaster.
        case KM_TAG_DEVICE_UNIQUE_ATTESTATION:
        case KM_TAG_ECIES_SINGLE_HASH_MODE:
        case KM_TAG_EXPORTABLE:
        case KM_TAG_IDENTITY_CREDENTIAL_KEY:
        case KM_TAG_KDF:
        case KM_TAG_ROLLBACK_RESISTANT:
        case KM_TAG_STORAGE_KEY:
            LOG_E("Tag %d not supported by SoftKeymaster", entry.tag);
            return KM_ERROR_UNSUPPORTED_TAG;

        // If the hardware enforce list contains this tag, means we are
        // pretending to be some secure hardware which has secure storage.
        case KM_TAG_ROLLBACK_RESISTANCE:
            if (hw_enforced->GetTagCount(entry.tag) != 0)
                break;
            else {
                LOG_E("Tag %d not supported by SoftKeymaster", entry.tag);
                return KM_ERROR_UNSUPPORTED_TAG;
            }

        // These are hidden.
        case KM_TAG_APPLICATION_DATA:
        case KM_TAG_APPLICATION_ID:
            break;

        // These should not be in key descriptions because they're for operation parameters.
        case KM_TAG_ASSOCIATED_DATA:
        case KM_TAG_AUTH_TOKEN:
        case KM_TAG_CONFIRMATION_TOKEN:
        case KM_TAG_INVALID:
        case KM_TAG_MAC_LENGTH:
        case KM_TAG_NONCE:
            LOG_E("Tag %d not allowed in key generation/import", entry.tag);
            break;

        // These are provided to support attestation key generation, but should not be included in
        // the key characteristics.
        case KM_TAG_ATTESTATION_APPLICATION_ID:
        case KM_TAG_ATTESTATION_CHALLENGE:
        case KM_TAG_ATTESTATION_ID_BRAND:
        case KM_TAG_ATTESTATION_ID_DEVICE:
        case KM_TAG_ATTESTATION_ID_IMEI:
        case KM_TAG_ATTESTATION_ID_SECOND_IMEI:
        case KM_TAG_ATTESTATION_ID_MANUFACTURER:
        case KM_TAG_ATTESTATION_ID_MEID:
        case KM_TAG_ATTESTATION_ID_MODEL:
        case KM_TAG_ATTESTATION_ID_PRODUCT:
        case KM_TAG_ATTESTATION_ID_SERIAL:
        case KM_TAG_CERTIFICATE_SERIAL:
        case KM_TAG_CERTIFICATE_SUBJECT:
        case KM_TAG_CERTIFICATE_NOT_BEFORE:
        case KM_TAG_CERTIFICATE_NOT_AFTER:
        case KM_TAG_INCLUDE_UNIQUE_ID:
        case KM_TAG_RESET_SINCE_ID_ROTATION:
            break;

        // Everything else we just copy into sw_enforced, unless the KeyFactory has placed it in
        // hw_enforced, in which case we defer to its decision.
        case KM_TAG_ACTIVE_DATETIME:
        case KM_TAG_ALGORITHM:
        case KM_TAG_ALL_APPLICATIONS:
        case KM_TAG_ALL_USERS:
        case KM_TAG_AUTH_TIMEOUT:
        case KM_TAG_BLOB_USAGE_REQUIREMENTS:
        case KM_TAG_BLOCK_MODE:
        case KM_TAG_BOOTLOADER_ONLY:
        case KM_TAG_CALLER_NONCE:
        case KM_TAG_CREATION_DATETIME:
        case KM_TAG_DIGEST:
        case KM_TAG_EARLY_BOOT_ONLY:
        case KM_TAG_EC_CURVE:
        case KM_TAG_KEY_SIZE:
        case KM_TAG_MAX_BOOT_LEVEL:
        case KM_TAG_MAX_USES_PER_BOOT:
        case KM_TAG_MIN_MAC_LENGTH:
        case KM_TAG_MIN_SECONDS_BETWEEN_OPS:
        case KM_TAG_NO_AUTH_REQUIRED:
        case KM_TAG_ORIGINATION_EXPIRE_DATETIME:
        case KM_TAG_PADDING:
        case KM_TAG_PURPOSE:
        case KM_TAG_RSA_OAEP_MGF_DIGEST:
        case KM_TAG_RSA_PUBLIC_EXPONENT:
        case KM_TAG_TRUSTED_CONFIRMATION_REQUIRED:
        case KM_TAG_TRUSTED_USER_PRESENCE_REQUIRED:
        case KM_TAG_UNIQUE_ID:
        case KM_TAG_UNLOCKED_DEVICE_REQUIRED:
        case KM_TAG_USAGE_COUNT_LIMIT:
        case KM_TAG_USAGE_EXPIRE_DATETIME:
        case KM_TAG_USER_AUTH_TYPE:
        case KM_TAG_USER_ID:
        case KM_TAG_USER_SECURE_ID:
            if (hw_enforced->GetTagCount(entry.tag) == 0) sw_enforced->push_back(entry);
            break;
        }
    }

    // If hw_enforced is non-empty, we're pretending to be some sort of secure hardware.
    AuthorizationSet* pseudo_hw_enforced = (hw_enforced->empty()) ? sw_enforced : hw_enforced;
    pseudo_hw_enforced->push_back(TAG_ORIGIN, origin);
    pseudo_hw_enforced->push_back(TAG_OS_VERSION, os_version);
    pseudo_hw_enforced->push_back(TAG_OS_PATCHLEVEL, os_patchlevel);

    // For KeyMaster implementations (but not KeyMint implementations), we need to add a
    // CREATION_DATETIME into software-enforced if one was not provided.
    if (version < KmVersion::KEYMINT_1 && !sw_enforced->Contains(TAG_CREATION_DATETIME)) {
        sw_enforced->push_back(TAG_CREATION_DATETIME, java_time(time(nullptr)));
    }

    return TranslateAuthorizationSetError(sw_enforced->is_valid());
}

keymaster_error_t ExtendKeyBlobAuthorizations(AuthorizationSet* hw_enforced,
                                              AuthorizationSet* sw_enforced,
                                              std::optional<uint32_t> vendor_patchlevel,
                                              std::optional<uint32_t> boot_patchlevel) {
    // If hw_enforced is non-empty, we're pretending to be some sort of secure hardware.
    AuthorizationSet* pseudo_hw_enforced = (hw_enforced->empty()) ? sw_enforced : hw_enforced;
    if (vendor_patchlevel.has_value()) {
        pseudo_hw_enforced->push_back(TAG_VENDOR_PATCHLEVEL, vendor_patchlevel.value());
    }
    if (boot_patchlevel.has_value()) {
        pseudo_hw_enforced->push_back(TAG_BOOT_PATCHLEVEL, boot_patchlevel.value());
    }
    return TranslateAuthorizationSetError(sw_enforced->is_valid());
}

keymaster_error_t UpgradeSoftKeyBlob(const UniquePtr<Key>& key, const uint32_t os_version,
                                     const uint32_t os_patchlevel,
                                     const AuthorizationSet& upgrade_params,
                                     KeymasterKeyBlob* upgraded_key) {
    return FullUpgradeSoftKeyBlob(key, os_version, os_patchlevel,
                                  /* vendor_patchlevel= */ std::nullopt,
                                  /* boot_patchlevel= */ std::nullopt,  //
                                  upgrade_params, upgraded_key);
}

keymaster_error_t FullUpgradeSoftKeyBlob(const UniquePtr<Key>& key, const uint32_t os_version,
                                         uint32_t os_patchlevel,
                                         std::optional<uint32_t> vendor_patchlevel,
                                         std::optional<uint32_t> boot_patchlevel,
                                         const AuthorizationSet& upgrade_params,
                                         KeymasterKeyBlob* upgraded_key) {
    bool set_changed = false;

    if (os_version == 0) {
        // We need to allow "upgrading" OS version to zero, to support upgrading from proper
        // numbered releases to unnumbered development and preview releases.

        int key_os_version_pos = key->sw_enforced().find(TAG_OS_VERSION);
        if (key_os_version_pos != -1) {
            uint32_t key_os_version = key->sw_enforced()[key_os_version_pos].integer;
            if (key_os_version != 0) {
                key->sw_enforced()[key_os_version_pos].integer = os_version;
                set_changed = true;
            }
        }
    }

    if (!UpgradeIntegerTag(TAG_OS_VERSION, os_version, &key->sw_enforced(), &set_changed) ||
        !UpgradeIntegerTag(TAG_OS_PATCHLEVEL, os_patchlevel, &key->sw_enforced(), &set_changed) ||
        (vendor_patchlevel.has_value() &&
         !UpgradeIntegerTag(TAG_VENDOR_PATCHLEVEL, vendor_patchlevel.value(), &key->sw_enforced(),
                            &set_changed)) ||
        (boot_patchlevel.has_value() &&
         !UpgradeIntegerTag(TAG_BOOT_PATCHLEVEL, boot_patchlevel.value(), &key->sw_enforced(),
                            &set_changed))) {
        // One of the version fields would have been a downgrade. Not allowed.
        return KM_ERROR_INVALID_ARGUMENT;
    }

    if (!set_changed) {
        // Dont' need an upgrade.
        return KM_ERROR_OK;
    }

    AuthorizationSet hidden;
    auto error = BuildHiddenAuthorizations(upgrade_params, &hidden, softwareRootOfTrust);
    if (error != KM_ERROR_OK) return error;
    return SerializeIntegrityAssuredBlob(key->key_material(), hidden, key->hw_enforced(),
                                         key->sw_enforced(), upgraded_key);
}

}  // namespace keymaster
