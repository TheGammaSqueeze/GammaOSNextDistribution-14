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

#include "trusty_keymaster_context.h"

#include <array>
#include <utility>

#include <stdlib.h>
#include <string.h>

#include <keymaster/android_keymaster_utils.h>
#include <keymaster/contexts/soft_attestation_cert.h>
#include <keymaster/key_blob_utils/auth_encrypted_key_blob.h>
#include <keymaster/key_blob_utils/ocb_utils.h>
#include <keymaster/km_openssl/aes_key.h>
#include <keymaster/km_openssl/asymmetric_key.h>
#include <keymaster/km_openssl/attestation_record.h>
#include <keymaster/km_openssl/attestation_utils.h>
#include <keymaster/km_openssl/certificate_utils.h>
#include <keymaster/km_openssl/ec_key_factory.h>
#include <keymaster/km_openssl/hmac_key.h>
#include <keymaster/km_openssl/openssl_err.h>
#include <keymaster/km_openssl/rsa_key_factory.h>
#include <keymaster/km_openssl/triple_des_key.h>
#include <keymaster/logger.h>
#include <keymaster/operation.h>
#include <keymaster/wrapped_key.h>
#include <lib/hwkey/hwkey.h>
#include <lib/rng/trusty_rng.h>
#include <openssl/hmac.h>

#include "second_imei_attestation.h"
#include "secure_storage_manager.h"
#include "trusty_aes_key.h"

constexpr bool kUseSecureDeletion = true;
uint8_t allZerosOrHashOfVerifiedBootKey[32] = {};

#ifdef KEYMASTER_DEBUG
#pragma message \
        "Compiling with fake Keymaster Root of Trust values! DO NOT SHIP THIS!"
#endif

// TRUSTY_KM_WRAPPING_KEY_SIZE controls the size of the AES key that is used
// to wrap keys before allowing NS to hold on to them.
// Previously, it had a hardcoded value of 16 bytes, but current guidance is to
// expand this to a 256-bit (32-byte) key.
//
// The plan is to leave old devices as they are, and issue new devices with a
// 32-byte key to ensure compatibility. New devices should set
// TRUSTY_WRAPPING_KEY_SIZE to 32 in their device Makefile to control this.

#ifndef TRUSTY_KM_WRAPPING_KEY_SIZE
#define TRUSTY_KM_WRAPPING_KEY_SIZE 16
#endif

namespace keymaster {

namespace {
static const int kAesKeySize = TRUSTY_KM_WRAPPING_KEY_SIZE;
static const int kCallsBetweenRngReseeds = 32;
static const int kRngReseedSize = 64;
static const uint8_t kMasterKeyDerivationData[kAesKeySize] = "KeymasterMaster";

bool UpgradeIntegerTag(keymaster_tag_t tag,
                       uint32_t value,
                       AuthorizationSet* set,
                       bool* set_changed) {
    int index = set->find(tag);
    if (index == -1) {
        *set_changed = true;
        set->push_back(keymaster_key_param_t{.tag = tag, .integer = value});
        return true;
    }

    if (set->params[index].integer > value) {
        return false;
    }

    if (set->params[index].integer != value) {
        *set_changed = true;
        set->params[index].integer = value;
    }
    return true;
}

}  // anonymous namespace

TrustyKeymasterContext::TrustyKeymasterContext()
        : AttestationContext(KmVersion::KEYMASTER_4),
          enforcement_policy_(this),
          secure_deletion_secret_storage_(*this /* random_source */),
          rng_initialized_(false),
          calls_since_reseed_(0) {
    LOG_D("Creating TrustyKeymaster");
    rsa_factory_.reset(new (std::nothrow) RsaKeyFactory(*this /* blob_maker */,
                                                        *this /* context */));
    tdes_factory_.reset(new (std::nothrow) TripleDesKeyFactory(
            *this /* blob_maker */, *this /* random_source */));
    ec_factory_.reset(new (std::nothrow) EcKeyFactory(*this /* blob_maker */,
                                                      *this /* context */));
    aes_factory_.reset(new (std::nothrow) TrustyAesKeyFactory(
            *this /* blob_maker */, *this /* random_source */));
    hmac_factory_.reset(new (std::nothrow) HmacKeyFactory(
            *this /* blob_maker */, *this /* random_source */));
    boot_params_.verified_boot_key.Reinitialize("Unbound", 7);
    trusty_remote_provisioning_context_.reset(
            new (std::nothrow) TrustyRemoteProvisioningContext());
}

const KeyFactory* TrustyKeymasterContext::GetKeyFactory(
        keymaster_algorithm_t algorithm) const {
    switch (algorithm) {
    case KM_ALGORITHM_RSA:
        return rsa_factory_.get();
    case KM_ALGORITHM_EC:
        return ec_factory_.get();
    case KM_ALGORITHM_AES:
        return aes_factory_.get();
    case KM_ALGORITHM_HMAC:
        return hmac_factory_.get();
    case KM_ALGORITHM_TRIPLE_DES:
        return tdes_factory_.get();
    default:
        return nullptr;
    }
}

static keymaster_algorithm_t supported_algorithms[] = {
        KM_ALGORITHM_RSA, KM_ALGORITHM_EC, KM_ALGORITHM_AES, KM_ALGORITHM_HMAC,
        KM_ALGORITHM_TRIPLE_DES};

const keymaster_algorithm_t* TrustyKeymasterContext::GetSupportedAlgorithms(
        size_t* algorithms_count) const {
    *algorithms_count = array_length(supported_algorithms);
    return supported_algorithms;
}

OperationFactory* TrustyKeymasterContext::GetOperationFactory(
        keymaster_algorithm_t algorithm,
        keymaster_purpose_t purpose) const {
    const KeyFactory* key_factory = GetKeyFactory(algorithm);
    if (!key_factory)
        return nullptr;
    return key_factory->GetOperationFactory(purpose);
}

static keymaster_error_t TranslateAuthorizationSetError(
        AuthorizationSet::Error err) {
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

keymaster_error_t TrustyKeymasterContext::SetAuthorizations(
        const AuthorizationSet& key_description,
        keymaster_key_origin_t origin,
        AuthorizationSet* hw_enforced,
        AuthorizationSet* sw_enforced,
        bool has_secure_deletion) const {
    sw_enforced->Clear();
    hw_enforced->Clear();

    for (auto& entry : key_description) {
        switch (entry.tag) {
        // Tags that should never appear in key descriptions.
        case KM_TAG_ASSOCIATED_DATA:
        case KM_TAG_AUTH_TOKEN:
        case KM_TAG_BOOTLOADER_ONLY:
        case KM_TAG_INVALID:
        case KM_TAG_MAC_LENGTH:
        case KM_TAG_NONCE:
        case KM_TAG_ROOT_OF_TRUST:
        case KM_TAG_UNIQUE_ID:
        case KM_TAG_IDENTITY_CREDENTIAL_KEY:
            return KM_ERROR_INVALID_KEY_BLOB;

        // Tags used only to provide information for certificate creation, but
        // which should not be included in blobs.
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
        case KM_TAG_CERTIFICATE_NOT_AFTER:
        case KM_TAG_CERTIFICATE_NOT_BEFORE:
        case KM_TAG_CERTIFICATE_SERIAL:
        case KM_TAG_CERTIFICATE_SUBJECT:
        case KM_TAG_RESET_SINCE_ID_ROTATION:
            break;

        // Unimplemented tags for which we return an error.
        case KM_TAG_DEVICE_UNIQUE_ATTESTATION:
            return KM_ERROR_INVALID_ARGUMENT;

        // Unimplemented tags we silently ignore.
        case KM_TAG_ALLOW_WHILE_ON_BODY:
            break;

        // Obsolete tags we silently ignore.
        case KM_TAG_ALL_APPLICATIONS:
        case KM_TAG_ROLLBACK_RESISTANT:
        case KM_TAG_CONFIRMATION_TOKEN:

        // Tags that should not be added to blobs.
        case KM_TAG_APPLICATION_ID:
        case KM_TAG_APPLICATION_DATA:
            break;

        // Tags we ignore because they'll be set below.
        case KM_TAG_BOOT_PATCHLEVEL:
        case KM_TAG_ORIGIN:
        case KM_TAG_OS_PATCHLEVEL:
        case KM_TAG_OS_VERSION:
        case KM_TAG_VENDOR_PATCHLEVEL:
            break;

        // Tags that are hardware-enforced
        case KM_TAG_ALGORITHM:
        case KM_TAG_AUTH_TIMEOUT:
        case KM_TAG_BLOB_USAGE_REQUIREMENTS:
        case KM_TAG_BLOCK_MODE:
        case KM_TAG_CALLER_NONCE:
        case KM_TAG_DIGEST:
        case KM_TAG_EARLY_BOOT_ONLY:
        case KM_TAG_ECIES_SINGLE_HASH_MODE:
        case KM_TAG_EC_CURVE:
        case KM_TAG_KDF:
        case KM_TAG_KEY_SIZE:
        case KM_TAG_MAX_USES_PER_BOOT:
        case KM_TAG_MIN_MAC_LENGTH:
        case KM_TAG_MIN_SECONDS_BETWEEN_OPS:
        case KM_TAG_NO_AUTH_REQUIRED:
        case KM_TAG_PADDING:
        case KM_TAG_PURPOSE:
        case KM_TAG_ROLLBACK_RESISTANCE:
        case KM_TAG_RSA_OAEP_MGF_DIGEST:
        case KM_TAG_RSA_PUBLIC_EXPONENT:
        case KM_TAG_TRUSTED_CONFIRMATION_REQUIRED:
        case KM_TAG_TRUSTED_USER_PRESENCE_REQUIRED:
        case KM_TAG_UNLOCKED_DEVICE_REQUIRED:
        case KM_TAG_USER_SECURE_ID:
            hw_enforced->push_back(entry);
            break;

        // KM_TAG_STORAGE_KEY handling depends if the feature is enabled.
        case KM_TAG_STORAGE_KEY:
#if WITH_HWWSK_SUPPORT
            hw_enforced->push_back(entry);
            break;
#else
            return KM_ERROR_UNIMPLEMENTED;
#endif

        case KM_TAG_USER_AUTH_TYPE: {
            keymaster_key_param_t elem = entry;

            // This implementation does support TEE enforced password auth
            elem.enumerated = entry.enumerated & HW_AUTH_PASSWORD;

#if TEE_FINGERPRINT_AUTH_SUPPORTED
            // If HW_AUTH_FINGERPRINT is supported it needs to be included too
            elem.enumerated |= entry.enumerated & HW_AUTH_FINGERPRINT;
#endif
            hw_enforced->push_back(elem);
        } break;

        case KM_TAG_USAGE_COUNT_LIMIT:
            LOG_D("Found usage count limit tag: %u", entry.integer);
            if (entry.integer == 1 && has_secure_deletion) {
                // We can enforce a usage count of 1 in HW.
                hw_enforced->push_back(entry);
            } else {
                // Otherwise we delegate to keystore.
                sw_enforced->push_back(entry);
            }
            break;

        // Keystore-enforced tags
        case KM_TAG_ACTIVE_DATETIME:
        case KM_TAG_ALL_USERS:
        case KM_TAG_CREATION_DATETIME:
        case KM_TAG_EXPORTABLE:
        case KM_TAG_INCLUDE_UNIQUE_ID:
        case KM_TAG_MAX_BOOT_LEVEL:
        case KM_TAG_ORIGINATION_EXPIRE_DATETIME:
        case KM_TAG_USAGE_EXPIRE_DATETIME:
        case KM_TAG_USER_ID:
            sw_enforced->push_back(entry);
            break;
        }
    }

    hw_enforced->push_back(TAG_ORIGIN, origin);

    // these values will be 0 if not set by bootloader
    hw_enforced->push_back(TAG_OS_VERSION, boot_params_.boot_os_version);
    hw_enforced->push_back(TAG_OS_PATCHLEVEL, boot_params_.boot_os_patchlevel);

    if (vendor_patchlevel_.has_value()) {
        hw_enforced->push_back(TAG_VENDOR_PATCHLEVEL,
                               vendor_patchlevel_.value());
    }
    if (boot_patchlevel_.has_value()) {
        hw_enforced->push_back(TAG_BOOT_PATCHLEVEL, boot_patchlevel_.value());
    }

    if (sw_enforced->is_valid() != AuthorizationSet::OK)
        return TranslateAuthorizationSetError(sw_enforced->is_valid());
    if (hw_enforced->is_valid() != AuthorizationSet::OK)
        return TranslateAuthorizationSetError(hw_enforced->is_valid());
    return KM_ERROR_OK;
}

keymaster_error_t TrustyKeymasterContext::BuildHiddenAuthorizations(
        const AuthorizationSet& input_set,
        AuthorizationSet* hidden) const {
    keymaster_blob_t entry;
    if (input_set.GetTagValue(TAG_APPLICATION_ID, &entry))
        hidden->push_back(TAG_APPLICATION_ID, entry.data, entry.data_length);
    if (input_set.GetTagValue(TAG_APPLICATION_DATA, &entry))
        hidden->push_back(TAG_APPLICATION_DATA, entry.data, entry.data_length);

    // Copy verified boot key, verified boot state, and device lock state to
    // hidden authorization set for binding to key.
    keymaster_key_param_t root_of_trust;
    root_of_trust.tag = KM_TAG_ROOT_OF_TRUST;
    root_of_trust.blob.data = boot_params_.verified_boot_key.begin();
    root_of_trust.blob.data_length =
            boot_params_.verified_boot_key.buffer_size();
    hidden->push_back(root_of_trust);

    root_of_trust.blob.data =
            reinterpret_cast<const uint8_t*>(&boot_params_.verified_boot_state);
    root_of_trust.blob.data_length = sizeof(boot_params_.verified_boot_state);
    hidden->push_back(root_of_trust);

    root_of_trust.blob.data =
            reinterpret_cast<const uint8_t*>(&boot_params_.device_locked);
    root_of_trust.blob.data_length = sizeof(boot_params_.device_locked);
    hidden->push_back(root_of_trust);

    return TranslateAuthorizationSetError(hidden->is_valid());
}

keymaster_error_t TrustyKeymasterContext::GetKdfState(
        EncryptedKey* info) const {
    long rc = hwkey_open();
    if (rc < 0) {
        LOG_S("Error failing to open a connection to hwkey: %ld", rc);
        return KM_ERROR_UNKNOWN_ERROR;
    }

    hwkey_session_t session = (hwkey_session_t)rc;
    struct hwkey_versioned_key_options opt = {
            .kdf_version = HWKEY_KDF_VERSION_BEST,
            .shared_key = false,
            .rollback_version_source = HWKEY_ROLLBACK_COMMITTED_VERSION,
            .os_rollback_version = HWKEY_ROLLBACK_VERSION_CURRENT,
            .context = NULL,
            .context_len = 0,
            .key = NULL,
            .key_len = 0,
    };
    rc = hwkey_derive_versioned(session, &opt);
    if (rc < 0) {
        LOG_S("Error deriving versioned master key: %ld", rc);
        hwkey_close(session);
        return KM_ERROR_UNKNOWN_ERROR;
    }
    hwkey_close(session);
    // Any versioned format will put the KDF selection into the correct mode.
    info->format = AES_GCM_WITH_SW_ENFORCED_VERSIONED;
    info->kdf_version = opt.kdf_version;
    info->addl_info = opt.os_rollback_version;
    return KM_ERROR_OK;
}

keymaster_error_t TrustyKeymasterContext::CreateAuthEncryptedKeyBlob(
        const AuthorizationSet& key_description,
        const KeymasterKeyBlob& key_material,
        const AuthorizationSet& hw_enforced,
        const AuthorizationSet& sw_enforced,
        const std::optional<SecureDeletionData>& secure_deletion_data,
        KeymasterKeyBlob* blob) const {
    AuthorizationSet hidden;
    keymaster_error_t error =
            BuildHiddenAuthorizations(key_description, &hidden);
    if (error != KM_ERROR_OK)
        return error;

    KeymasterKeyBlob master_key;
    EncryptedKey info;
    error = GetKdfState(&info);
    if (error != KM_ERROR_OK) {
        return error;
    }
    error = DeriveMasterKey(&master_key, info);
    if (error != KM_ERROR_OK) {
        return error;
    }

    KmErrorOr<EncryptedKey> encrypted_key;
    if (secure_deletion_data) {
        encrypted_key = EncryptKey(
                key_material, AES_GCM_WITH_SECURE_DELETION_VERSIONED,
                hw_enforced, sw_enforced, hidden, *secure_deletion_data,
                master_key, *this /* random */);
    } else {
        encrypted_key = EncryptKey(
                key_material, AES_GCM_WITH_SW_ENFORCED_VERSIONED, hw_enforced,
                sw_enforced, hidden, SecureDeletionData{}, master_key,
                *this /* random */);
    }
    if (!encrypted_key) {
        return encrypted_key.error();
    }

    encrypted_key->kdf_version = info.kdf_version;
    encrypted_key->addl_info = info.addl_info;
    KmErrorOr<KeymasterKeyBlob> serialized_key = SerializeAuthEncryptedBlob(
            *encrypted_key, hw_enforced, sw_enforced,
            secure_deletion_data ? secure_deletion_data->key_slot : 0);

    if (!serialized_key) {
        return serialized_key.error();
    }

    *blob = std::move(*serialized_key);
    return KM_ERROR_OK;
}

class KeySlotCleanup {
public:
    KeySlotCleanup(const SecureDeletionSecretStorage& storage,
                   uint32_t key_slot)
            : storage_(storage), key_slot_(key_slot) {}
    ~KeySlotCleanup() {
        if (key_slot_ != 0) {
            storage_.DeleteKey(key_slot_);
        }
    }

    void release() { key_slot_ = 0; }

private:
    const SecureDeletionSecretStorage& storage_;
    uint32_t key_slot_;
};

keymaster_error_t TrustyKeymasterContext::CreateKeyBlob(
        const AuthorizationSet& key_description,
        keymaster_key_origin_t origin,
        const KeymasterKeyBlob& key_material,
        KeymasterKeyBlob* blob,
        AuthorizationSet* hw_enforced,
        AuthorizationSet* sw_enforced) const {
    bool request_rollback_resistance =
            key_description.Contains(TAG_ROLLBACK_RESISTANCE);
    bool request_usage_limit =
            key_description.Contains(TAG_USAGE_COUNT_LIMIT, 1);
    bool request_secure_deletion =
            request_rollback_resistance || request_usage_limit;

    LOG_D("Getting secure deletion data");
    std::optional<SecureDeletionData> sdd;
    if (kUseSecureDeletion) {
        sdd = secure_deletion_secret_storage_.CreateDataForNewKey(
                request_secure_deletion,
                /* is_upgrade */ false);
    }

    if (sdd) {
        LOG_D("Got secure deletion data, FR size = %zu, SD size = %zu, slot = %u",
              sdd->factory_reset_secret.buffer_size(),
              sdd->secure_deletion_secret.buffer_size(), sdd->key_slot);
    } else if (!kUseSecureDeletion) {
        LOG_I("Not using secure deletion");
    } else {
        LOG_W("Failed to get secure deletion data. storageproxy not up?");
    }

    uint32_t key_slot = sdd ? sdd->key_slot : 0;
    bool has_secure_deletion = key_slot != 0;
    if (request_secure_deletion && !has_secure_deletion) {
        LOG_E("Secure deletion requested (rollback_resistance:%d, usage_limit:%d) but no slot available!",
              request_rollback_resistance, request_usage_limit);
        return KM_ERROR_ROLLBACK_RESISTANCE_UNAVAILABLE;
    }

    // At this point we may have stored a secure deletion secret for this key.
    // If something goes wrong before we return the blob, that slot will leak.
    // Create an object to clean up on the error paths.
    KeySlotCleanup key_slot_cleanup(secure_deletion_secret_storage_, key_slot);

    keymaster_error_t error =
            SetAuthorizations(key_description, origin, hw_enforced, sw_enforced,
                              has_secure_deletion);

    if (error != KM_ERROR_OK) {
        return error;
    }

    error = CreateAuthEncryptedKeyBlob(key_description, key_material,
                                       *hw_enforced, *sw_enforced,
                                       std::move(sdd), blob);
    if (error != KM_ERROR_OK) {
        return error;
    }

    key_slot_cleanup.release();
    return KM_ERROR_OK;
}

keymaster_error_t TrustyKeymasterContext::UpgradeKeyBlob(
        const KeymasterKeyBlob& key_to_upgrade,
        const AuthorizationSet& upgrade_params,
        KeymasterKeyBlob* upgraded_key) const {
    UniquePtr<Key> key;
    keymaster_error_t error =
            ParseKeyBlob(key_to_upgrade, upgrade_params, &key);
    LOG_I("Upgrading key blob");
    if (error != KM_ERROR_OK) {
        return error;
    }

    bool set_changed = false;
    if (boot_params_.boot_os_version == 0) {
        // We need to allow "upgrading" OS version to zero, to support upgrading
        // from proper numbered releases to unnumbered development and preview
        // releases.

        if (int pos = key->sw_enforced().find(TAG_OS_VERSION);
            pos != -1 &&
            key->sw_enforced()[pos].integer != boot_params_.boot_os_version) {
            set_changed = true;
            key->sw_enforced()[pos].integer = boot_params_.boot_os_version;
        }
    }

    if (!UpgradeIntegerTag(TAG_OS_VERSION, boot_params_.boot_os_version,
                           &key->hw_enforced(), &set_changed) ||
        !UpgradeIntegerTag(TAG_OS_PATCHLEVEL, boot_params_.boot_os_patchlevel,
                           &key->hw_enforced(), &set_changed) ||
        (vendor_patchlevel_.has_value() &&
         !UpgradeIntegerTag(TAG_VENDOR_PATCHLEVEL, vendor_patchlevel_.value(),
                            &key->hw_enforced(), &set_changed)) ||
        (boot_patchlevel_.has_value() &&
         !UpgradeIntegerTag(TAG_BOOT_PATCHLEVEL, boot_patchlevel_.value(),
                            &key->hw_enforced(), &set_changed))) {
        // One of the version fields would have been a downgrade. Not allowed.
        return KM_ERROR_INVALID_ARGUMENT;
    }

    if (!set_changed) {
        return KM_ERROR_OK;
    }

    bool has_secure_deletion = false;
    if (key->secure_deletion_slot() != 0) {
        LOG_D("Upgrading rollback-protected key blob in slot %u",
              key->secure_deletion_slot());
        has_secure_deletion = true;
    }
    if (!has_secure_deletion &&
        upgrade_params.Contains(TAG_ROLLBACK_RESISTANCE)) {
        LOG_D("Upgrading non rollback-protected key, adding rollback protection");
        has_secure_deletion = true;
    }

    std::optional<SecureDeletionData> sdd;
    if (kUseSecureDeletion) {
        sdd = secure_deletion_secret_storage_.CreateDataForNewKey(
                has_secure_deletion, true /* is_upgrade */);
    }

    // At this point we may have stored a secure deletion secret for this key.
    // If something goes wrong before we return the blob, that slot will leak.
    // Create an object to clean up on the error paths.
    KeySlotCleanup key_slot_cleanup(secure_deletion_secret_storage_,
                                    sdd ? sdd->key_slot : 0);

    error = CreateAuthEncryptedKeyBlob(upgrade_params, key->key_material(),
                                       key->hw_enforced(), key->sw_enforced(),
                                       std::move(sdd), upgraded_key);
    if (error != KM_ERROR_OK) {
        return error;
    }

    key_slot_cleanup.release();
    return KM_ERROR_OK;
}

constexpr std::array<uint8_t, 7> kKeystoreKeyBlobMagic = {'p', 'K', 'M', 'b',
                                                          'l', 'o', 'b'};
constexpr size_t kKeystoreKeyTypeOffset = kKeystoreKeyBlobMagic.size();
constexpr size_t kKeystoreKeyBlobPrefixSize = kKeystoreKeyTypeOffset + 1;

KmErrorOr<DeserializedKey> TrustyKeymasterContext::DeserializeKmCompatKeyBlob(
        const KeymasterKeyBlob& blob) const {
    // This blob has a keystore km_compat prefix.  This means that it was
    // created by keystore calling TrustyKeymaster through the km_compat layer.
    // The km_compat layer adds this prefix to determine whether it's actually a
    // hardware blob that should be passed through to Keymaster, or whether it's
    // a software only key and should be used by the emulation layer.
    //
    // In the case of hardware blobs, km_compat strips the prefix before handing
    // the blob to Keymaster.  In the case of software blobs, km_compat never
    // hands the blob to Keymaster.
    //
    // The fact that we've received this prefixed blob means that it was created
    // through km_compat... but the device has now been upgraded from
    // TrustyKeymaster to TrustyKeyMint, and so keystore is no longer using the
    // km_compat layer, and the blob is just passed through with its prefix
    // intact.
    auto keyType = *(blob.begin() + kKeystoreKeyTypeOffset);
    switch (keyType) {
    case 0:
        // This is a hardware blob. Strip the prefix and use the blob.
        return DeserializeAuthEncryptedBlob(
                KeymasterKeyBlob(blob.begin() + kKeystoreKeyBlobPrefixSize,
                                 blob.size() - kKeystoreKeyBlobPrefixSize));

    case 1:
        LOG_E("Software key blobs are not supported.");
        return KM_ERROR_INVALID_KEY_BLOB;

    default:
        LOG_E("Invalid keystore blob prefix value %d", keyType);
        return KM_ERROR_INVALID_KEY_BLOB;
    }
}

bool is_km_compat_blob(const KeymasterKeyBlob& blob) {
    return blob.size() >= kKeystoreKeyBlobPrefixSize &&
           std::equal(kKeystoreKeyBlobMagic.begin(),
                      kKeystoreKeyBlobMagic.end(), blob.begin());
}

KmErrorOr<DeserializedKey> TrustyKeymasterContext::DeserializeKeyBlob(
        const KeymasterKeyBlob& blob) const {
    if (is_km_compat_blob(blob)) {
        return DeserializeKmCompatKeyBlob(blob);
    } else {
        return DeserializeAuthEncryptedBlob(blob);
    }
}

keymaster_error_t TrustyKeymasterContext::ParseKeyBlob(
        const KeymasterKeyBlob& blob,
        const AuthorizationSet& additional_params,
        UniquePtr<Key>* key) const {
    keymaster_error_t error;

    if (!key) {
        return KM_ERROR_UNEXPECTED_NULL_POINTER;
    }

    KmErrorOr<DeserializedKey> deserialized_key = DeserializeKeyBlob(blob);
    if (!deserialized_key) {
        return deserialized_key.error();
    }
    LOG_D("Deserialized blob with format: %d",
          deserialized_key->encrypted_key.format);

    KeymasterKeyBlob master_key;
    error = DeriveMasterKey(&master_key, deserialized_key->encrypted_key);
    if (error != KM_ERROR_OK) {
        return error;
    }

    AuthorizationSet hidden;
    error = BuildHiddenAuthorizations(additional_params, &hidden);
    if (error != KM_ERROR_OK) {
        return error;
    }

    SecureDeletionData sdd;
    if (deserialized_key->encrypted_key.format ==
                AES_GCM_WITH_SECURE_DELETION ||
        deserialized_key->encrypted_key.format ==
                AES_GCM_WITH_SECURE_DELETION_VERSIONED) {
        // This key requires secure deletion data.
        sdd = secure_deletion_secret_storage_.GetDataForKey(
                deserialized_key->key_slot);
    }

    LOG_D("Decrypting blob with format: %d",
          deserialized_key->encrypted_key.format);
    KmErrorOr<KeymasterKeyBlob> key_material =
            DecryptKey(*deserialized_key, hidden, sdd, master_key);
    if (!key_material) {
        return key_material.error();
    }

    keymaster_algorithm_t algorithm;
    if (!deserialized_key->hw_enforced.GetTagValue(TAG_ALGORITHM, &algorithm)) {
        return KM_ERROR_INVALID_KEY_BLOB;
    }

    auto factory = GetKeyFactory(algorithm);
    error = factory->LoadKey(std::move(*key_material), additional_params,
                             std::move(deserialized_key->hw_enforced),
                             std::move(deserialized_key->sw_enforced), key);
    if (key && key->get()) {
        (*key)->set_secure_deletion_slot(deserialized_key->key_slot);
    }

    return error;
}

keymaster_error_t TrustyKeymasterContext::DeleteKey(
        const KeymasterKeyBlob& blob) const {
    KmErrorOr<DeserializedKey> deserialized_key = DeserializeKeyBlob(blob);
    if (deserialized_key) {
        LOG_D("Deserialized blob with format: %u",
              deserialized_key->encrypted_key.format);
        secure_deletion_secret_storage_.DeleteKey(deserialized_key->key_slot);
    }

    return KM_ERROR_OK;
}

keymaster_error_t TrustyKeymasterContext::DeleteAllKeys() const {
    secure_deletion_secret_storage_.DeleteAllKeys();
    return KM_ERROR_OK;
}

keymaster_error_t TrustyKeymasterContext::AddRngEntropy(const uint8_t* buf,
                                                        size_t length) const {
    if (trusty_rng_add_entropy(buf, length) != 0)
        return KM_ERROR_UNKNOWN_ERROR;
    return KM_ERROR_OK;
}

bool TrustyKeymasterContext::SeedRngIfNeeded() const {
    if (ShouldReseedRng())
        const_cast<TrustyKeymasterContext*>(this)->ReseedRng();
    return rng_initialized_;
}

bool TrustyKeymasterContext::ShouldReseedRng() const {
    if (!rng_initialized_) {
        LOG_I("RNG not initialized, reseed");
        return true;
    }

    if (++calls_since_reseed_ % kCallsBetweenRngReseeds == 0) {
        LOG_I("Periodic reseed");
        return true;
    }
    return false;
}

bool TrustyKeymasterContext::ReseedRng() {
    uint8_t rand_seed[kRngReseedSize];
    memset(rand_seed, 0, kRngReseedSize);
    if (trusty_rng_hw_rand(rand_seed, kRngReseedSize) != 0) {
        LOG_E("Failed to get bytes from HW RNG");
        return false;
    }
    LOG_I("Reseeding with %d bytes from HW RNG", kRngReseedSize);
    trusty_rng_add_entropy(rand_seed, kRngReseedSize);

    rng_initialized_ = true;
    return true;
}

// Gee wouldn't it be nice if the crypto service headers defined this.
enum DerivationParams {
    DERIVATION_DATA_PARAM = 0,
    OUTPUT_BUFFER_PARAM = 1,
};

keymaster_error_t TrustyKeymasterContext::DeriveMasterKey(
        KeymasterKeyBlob* master_key,
        const EncryptedKey& enc_key) const {
    LOG_D("Deriving master key");

    long rc = hwkey_open();
    if (rc < 0) {
        return KM_ERROR_UNKNOWN_ERROR;
    }

    hwkey_session_t session = (hwkey_session_t)rc;

    if (!master_key->Reset(kAesKeySize)) {
        LOG_S("Could not allocate memory for master key buffer");
        hwkey_close(session);
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    if (enc_key.format < AES_GCM_WITH_SW_ENFORCED_VERSIONED) {
        uint32_t kdf_version = HWKEY_KDF_VERSION_1;
        rc = hwkey_derive(session, &kdf_version, kMasterKeyDerivationData,
                          master_key->writable_data(), kAesKeySize);
        if (rc < 0) {
            LOG_S("Error deriving legacy master key: %ld", rc);
            hwkey_close(session);
            return KM_ERROR_UNKNOWN_ERROR;
        }
    } else {
        struct hwkey_versioned_key_options opt = {
                .kdf_version = enc_key.kdf_version,
                .shared_key = false,
                .rollback_version_source = HWKEY_ROLLBACK_COMMITTED_VERSION,
                .os_rollback_version = enc_key.addl_info,
                .context = kMasterKeyDerivationData,
                .context_len = sizeof(kMasterKeyDerivationData),
                .key = master_key->writable_data(),
                .key_len = kAesKeySize,
        };
        rc = hwkey_derive_versioned(session, &opt);
        if (rc < 0) {
            LOG_S("Error deriving versioned master key: %ld", rc);
            hwkey_close(session);
            return KM_ERROR_UNKNOWN_ERROR;
        }
    }

    hwkey_close(session);
    LOG_D("Key derivation complete");
    return KM_ERROR_OK;
}

bool TrustyKeymasterContext::InitializeAuthTokenKey() {
    if (auth_token_key_initialized_)
        return true;

    keymaster_key_blob_t key;
    key.key_material = auth_token_key_;
    key.key_material_size = kAuthTokenKeySize;
    keymaster_error_t error = enforcement_policy_.GetHmacKey(&key);
    if (error == KM_ERROR_OK)
        auth_token_key_initialized_ = true;
    else
        auth_token_key_initialized_ = false;

    return auth_token_key_initialized_;
}

keymaster_error_t TrustyKeymasterContext::GetAuthTokenKey(
        keymaster_key_blob_t* key) const {
    if (!auth_token_key_initialized_ &&
        !const_cast<TrustyKeymasterContext*>(this)->InitializeAuthTokenKey())
        return KM_ERROR_UNKNOWN_ERROR;

    key->key_material = auth_token_key_;
    key->key_material_size = kAuthTokenKeySize;
    return KM_ERROR_OK;
}

keymaster_error_t TrustyKeymasterContext::SetSystemVersion(
        uint32_t os_version,
        uint32_t os_patchlevel) {
    if (!version_info_set_) {
        // Note that version info is now set by Configure, rather than by the
        // bootloader.  This is to ensure that system-only updates can be done,
        // to avoid breaking Project Treble.
        boot_params_.boot_os_version = os_version;
        boot_params_.boot_os_patchlevel = os_patchlevel;
        version_info_set_ = true;
    }

#ifdef KEYMASTER_DEBUG
    Buffer fake_root_of_trust("000111222333444555666777888999000", 32);
    Buffer verified_boot_hash_none;
    if (!root_of_trust_set_) {
        /* Sets bootloader parameters to what is expected on a 'good' device,
         * will pass attestation CTS tests. FOR DEBUGGING ONLY.
         */
        SetBootParams(os_version, os_patchlevel, fake_root_of_trust,
                      KM_VERIFIED_BOOT_VERIFIED, true, verified_boot_hash_none);
    }
#endif

    return KM_ERROR_OK;
}

void TrustyKeymasterContext::GetSystemVersion(uint32_t* os_version,
                                              uint32_t* os_patchlevel) const {
    *os_version = boot_params_.boot_os_version;
    *os_patchlevel = boot_params_.boot_os_patchlevel;
}

const AttestationContext::VerifiedBootParams*
TrustyKeymasterContext::GetVerifiedBootParams(keymaster_error_t* error) const {
    VerifiedBootParams& vb_parms =
            const_cast<VerifiedBootParams&>(verified_boot_params_);

    if (boot_params_.verified_boot_key.buffer_size() == 0) {
        // If an empty verified boot key was passed by the boot loader, set the
        // verfified boot key in attestation parameters to 32 bytes of all
        // zeros.
        vb_parms.verified_boot_key = {allZerosOrHashOfVerifiedBootKey,
                                      sizeof(allZerosOrHashOfVerifiedBootKey)};
    } else if (boot_params_.verified_boot_key.buffer_size() > 0 &&
               boot_params_.verified_boot_key.buffer_size() <= 32) {
        vb_parms.verified_boot_key = {
                boot_params_.verified_boot_key.begin(),
                boot_params_.verified_boot_key.buffer_size()};
    } else if (boot_params_.verified_boot_key.buffer_size() > 32) {
        // If the verified boot key itself was passed by the boot loader, set
        // SHA-256 hash of it to the verified boot key parameter of the
        // attetation information.
        vb_parms.verified_boot_key = {
                SHA256(boot_params_.verified_boot_key.begin(),
                       boot_params_.verified_boot_key.buffer_size(),
                       allZerosOrHashOfVerifiedBootKey),
                SHA256_DIGEST_LENGTH};
    }

    vb_parms.verified_boot_hash = {
            boot_params_.verified_boot_hash.begin(),
            boot_params_.verified_boot_hash.buffer_size()};
    vb_parms.verified_boot_state = boot_params_.verified_boot_state;
    vb_parms.device_locked = boot_params_.device_locked;

    *error = KM_ERROR_OK;
    return &verified_boot_params_;
}

#define PROTO_BYTES_DOES_NOT_MATCH_BLOB(blob, proto) \
    ((blob).data_length != (proto).size) ||          \
            (memcmp((blob).data, (proto).bytes, (proto).size) != 0)

keymaster_error_t TrustyKeymasterContext::VerifyAndCopyDeviceIds(
        const AuthorizationSet& attestation_params,
        AuthorizationSet* values_to_attest) const {
    SecureStorageManager* ss_manager = SecureStorageManager::get_instance();
    if (ss_manager == nullptr) {
        LOG_E("Failed to open secure storage session.");
        return KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
    }

    AttestationIds ids;
    auto err = ss_manager->ReadAttestationIds(&ids);
    if (err != KM_ERROR_OK) {
        return err;
    }

    bool found_mismatch = false;
    for (auto& entry : attestation_params) {
        switch (entry.tag) {
        case KM_TAG_ATTESTATION_ID_BRAND:
            found_mismatch |=
                    PROTO_BYTES_DOES_NOT_MATCH_BLOB(entry.blob, ids.brand);
            values_to_attest->push_back(entry);
            break;

        case KM_TAG_ATTESTATION_ID_DEVICE:
            found_mismatch |=
                    PROTO_BYTES_DOES_NOT_MATCH_BLOB(entry.blob, ids.device);
            values_to_attest->push_back(entry);
            break;

        case KM_TAG_ATTESTATION_ID_PRODUCT:
            found_mismatch |=
                    PROTO_BYTES_DOES_NOT_MATCH_BLOB(entry.blob, ids.product);
            values_to_attest->push_back(entry);
            break;

        case KM_TAG_ATTESTATION_ID_SERIAL:
            found_mismatch |=
                    PROTO_BYTES_DOES_NOT_MATCH_BLOB(entry.blob, ids.serial);
            values_to_attest->push_back(entry);
            break;

        case KM_TAG_ATTESTATION_ID_IMEI:
            found_mismatch |=
                    PROTO_BYTES_DOES_NOT_MATCH_BLOB(entry.blob, ids.imei);
            values_to_attest->push_back(entry);
            break;

        case KM_TAG_ATTESTATION_ID_SECOND_IMEI: {
            // Validate directly against storage if it is present.
            if (ids.second_imei.size > 0) {
                found_mismatch |= PROTO_BYTES_DOES_NOT_MATCH_BLOB(
                        entry.blob, ids.second_imei);
                values_to_attest->push_back(entry);
            } else {
#ifndef KEYMASTER_NO_AUTO_SECOND_IMEI
                // Typically dual-SIM devices ship with two sequential IMEIs.
                // As the second IMEI was not provisioned to the KeyMint
                // instance, it is still possible to attest to the second IMEI
                // by validating that the second IMEI is the one after the first
                // IMEI, which was provisoned to the KeyMint instance.
                std::string imei_str(
                        reinterpret_cast<const char*>(ids.imei.bytes),
                        ids.imei.size);

                long imei_numeric = strtol(imei_str.c_str(), NULL, 10);
                bool second_imei_mismatch =
                        !validate_second_imei(entry.blob, imei_numeric);
                if (second_imei_mismatch) {
                    LOG_E("Mismatch in second IMEI.");
                }
                found_mismatch |= second_imei_mismatch;
                values_to_attest->push_back(entry);
#endif
            }
        } break;

        case KM_TAG_ATTESTATION_ID_MEID:
            found_mismatch |=
                    PROTO_BYTES_DOES_NOT_MATCH_BLOB(entry.blob, ids.meid);
            values_to_attest->push_back(entry);
            break;

        case KM_TAG_ATTESTATION_ID_MANUFACTURER:
            found_mismatch |= PROTO_BYTES_DOES_NOT_MATCH_BLOB(entry.blob,
                                                              ids.manufacturer);
            values_to_attest->push_back(entry);
            break;

        case KM_TAG_ATTESTATION_ID_MODEL:
            found_mismatch |=
                    PROTO_BYTES_DOES_NOT_MATCH_BLOB(entry.blob, ids.model);
            values_to_attest->push_back(entry);
            break;

        default:
            // Ignore non-ID tags.
            break;
        }
    }

    if (found_mismatch) {
        values_to_attest->Clear();
        return KM_ERROR_CANNOT_ATTEST_IDS;
    }

    return KM_ERROR_OK;
}

Buffer TrustyKeymasterContext::GenerateUniqueId(
        uint64_t creation_date_time,
        const keymaster_blob_t& application_id,
        bool reset_since_rotation,
        keymaster_error_t* error) const {
    if (unique_id_hbk_.empty()) {
        KeymasterKeyBlob hbk;
        keymaster_error_t derive_error =
                enforcement_policy_.GetUniqueIdKey(&hbk);
        if (derive_error != KM_ERROR_OK) {
            LOG_E("Failed to derive unique ID HBK: %d", derive_error);
            *error = derive_error;
            return {};
        }
        unique_id_hbk_ = std::vector(hbk.begin(), hbk.end());
    }

    Buffer unique_id;
    *error = keymaster::generate_unique_id(unique_id_hbk_, creation_date_time,
                                           application_id, reset_since_rotation,
                                           &unique_id);
    return unique_id;
}

KeymasterKeyBlob TrustyKeymasterContext::GetAttestationKey(
        keymaster_algorithm_t algorithm,
        keymaster_error_t* error) const {
    AttestationKeySlot key_slot;

    switch (algorithm) {
    case KM_ALGORITHM_RSA:
        key_slot = AttestationKeySlot::kRsa;
        break;

    case KM_ALGORITHM_EC:
        key_slot = AttestationKeySlot::kEcdsa;
        break;

    default:
        *error = KM_ERROR_UNSUPPORTED_ALGORITHM;
        return {};
    }

    SecureStorageManager* ss_manager = SecureStorageManager::get_instance();
    if (ss_manager == nullptr) {
        LOG_E("Failed to open secure storage session.");
        *error = KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
        return {};
    }
    auto result = ss_manager->ReadKeyFromStorage(key_slot, error);
#if KEYMASTER_SOFT_ATTESTATION_FALLBACK
    if (*error != KM_ERROR_OK) {
        LOG_I("Failed to read attestation key from RPMB, falling back to test key");
        auto key = getAttestationKey(algorithm, error);
        if (*error != KM_ERROR_OK) {
            LOG_D("Software attestation key missing: %d", *error);
            return {};
        }
        result = KeymasterKeyBlob(*key);
        if (!result.key_material)
            *error = KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }
#endif
    return result;
}

CertificateChain TrustyKeymasterContext::GetAttestationChain(
        keymaster_algorithm_t algorithm,
        keymaster_error_t* error) const {
    AttestationKeySlot key_slot;
    switch (algorithm) {
    case KM_ALGORITHM_RSA:
        key_slot = AttestationKeySlot::kRsa;
        break;
    case KM_ALGORITHM_EC:
        key_slot = AttestationKeySlot::kEcdsa;
        break;
    default:
        *error = KM_ERROR_UNSUPPORTED_ALGORITHM;
        return {};
    }

    CertificateChain chain;
    SecureStorageManager* ss_manager = SecureStorageManager::get_instance();
    if (ss_manager == nullptr) {
        LOG_E("Failed to open secure storage session.");
        *error = KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
    } else {
        *error = ss_manager->ReadCertChainFromStorage(key_slot, &chain);
    }
#if KEYMASTER_SOFT_ATTESTATION_FALLBACK
    if ((*error != KM_ERROR_OK) || (chain.entry_count == 0)) {
        LOG_I("Failed to read attestation chain from RPMB, falling back to test chain");
        chain = getAttestationChain(algorithm, error);
    }
#endif
    return chain;
}

CertificateChain TrustyKeymasterContext::GenerateAttestation(
        const Key& key,
        const AuthorizationSet& attest_params,
        UniquePtr<Key> attest_key,
        const KeymasterBlob& issuer_subject,
        keymaster_error_t* error) const {
    *error = KM_ERROR_OK;
    keymaster_algorithm_t key_algorithm;
    if (!key.authorizations().GetTagValue(TAG_ALGORITHM, &key_algorithm)) {
        *error = KM_ERROR_UNKNOWN_ERROR;
        return {};
    }

    if ((key_algorithm != KM_ALGORITHM_RSA &&
         key_algorithm != KM_ALGORITHM_EC)) {
        *error = KM_ERROR_INCOMPATIBLE_ALGORITHM;
        return {};
    }

    // We have established that the given key has the correct algorithm, and
    // because this is the TrustyKeymasterContext we can assume that the Key is
    // an AsymmetricKey. So we can downcast.
    const AsymmetricKey& asymmetric_key =
            static_cast<const AsymmetricKey&>(key);

    AttestKeyInfo attest_key_info(attest_key, &issuer_subject, error);
    if (*error != KM_ERROR_OK) {
        return {};
    }

    return generate_attestation(asymmetric_key, attest_params,
                                std::move(attest_key_info), *this, error);
}

CertificateChain TrustyKeymasterContext::GenerateSelfSignedCertificate(
        const Key& key,
        const AuthorizationSet& cert_params,
        bool fake_signature,
        keymaster_error_t* error) const {
    keymaster_algorithm_t key_algorithm;
    if (!key.authorizations().GetTagValue(TAG_ALGORITHM, &key_algorithm)) {
        *error = KM_ERROR_UNKNOWN_ERROR;
        return {};
    }

    if ((key_algorithm != KM_ALGORITHM_RSA &&
         key_algorithm != KM_ALGORITHM_EC)) {
        *error = KM_ERROR_INCOMPATIBLE_ALGORITHM;
        return {};
    }

    const AsymmetricKey& asymmetric_key =
            static_cast<const AsymmetricKey&>(key);

    return generate_self_signed_cert(asymmetric_key, cert_params,
                                     fake_signature, error);
}

keymaster_error_t TrustyKeymasterContext::SetBootParams(
        uint32_t /* os_version */,
        uint32_t /* os_patchlevel */,
        const Buffer& verified_boot_key,
        keymaster_verified_boot_t verified_boot_state,
        bool device_locked,
        const Buffer& verified_boot_hash) {
    if (root_of_trust_set_)
        return KM_ERROR_ROOT_OF_TRUST_ALREADY_SET;
    boot_params_.verified_boot_hash.Reinitialize(verified_boot_hash);
    root_of_trust_set_ = true;
    boot_params_.verified_boot_state = verified_boot_state;
    boot_params_.device_locked = device_locked;
    boot_params_.verified_boot_key.Reinitialize("", 0);

    if (verified_boot_key.buffer_size()) {
        boot_params_.verified_boot_key.Reinitialize(verified_boot_key);
    } else {
        // If no boot key was passed, default to unverified/unlocked
        boot_params_.verified_boot_state = KM_VERIFIED_BOOT_UNVERIFIED;
    }

    if ((verified_boot_state != KM_VERIFIED_BOOT_VERIFIED) &&
        (verified_boot_state != KM_VERIFIED_BOOT_SELF_SIGNED)) {
        // If the device image was not verified or self signed, it cannot be
        // locked
        boot_params_.device_locked = false;
    }

    trusty_remote_provisioning_context_->SetBootParams(&boot_params_);

    return KM_ERROR_OK;
}

// Mostly adapted from pure_soft_keymaster_context.cpp
keymaster_error_t TrustyKeymasterContext::UnwrapKey(
        const KeymasterKeyBlob& wrapped_key_blob,
        const KeymasterKeyBlob& wrapping_key_blob,
        const AuthorizationSet& wrapping_key_params,
        const KeymasterKeyBlob& masking_key,
        AuthorizationSet* wrapped_key_params,
        keymaster_key_format_t* wrapped_key_format,
        KeymasterKeyBlob* wrapped_key_material) const {
    LOG_D("UnwrapKey:0");

    keymaster_error_t error = KM_ERROR_OK;

    if (wrapped_key_material == NULL) {
        return KM_ERROR_UNEXPECTED_NULL_POINTER;
    }

    LOG_D("UnwrapKey:1");
    // Step 1 from IKeymasterDevice.hal file spec
    // Parse wrapping key
    UniquePtr<Key> wrapping_key;
    error = ParseKeyBlob(wrapping_key_blob, wrapping_key_params, &wrapping_key);
    if (error != KM_ERROR_OK) {
        LOG_E("Failed to parse wrapping key");
        return error;
    }

    AuthProxy wrapping_key_auths(wrapping_key->hw_enforced(),
                                 wrapping_key->sw_enforced());

    // Check Wrapping Key Purpose
    if (!wrapping_key_auths.Contains(TAG_PURPOSE, KM_PURPOSE_WRAP)) {
        LOG_E("Wrapping key did not have KM_PURPOSE_WRAP");
        return KM_ERROR_INCOMPATIBLE_PURPOSE;
    }

    // Check Padding mode is RSA_OAEP and digest is SHA_2_256 (spec
    // mandated)
    if (!wrapping_key_auths.Contains(TAG_DIGEST, KM_DIGEST_SHA_2_256)) {
        LOG_E("Wrapping key lacks authorization for SHA2-256");
        return KM_ERROR_INCOMPATIBLE_DIGEST;
    }
    if (!wrapping_key_auths.Contains(TAG_PADDING, KM_PAD_RSA_OAEP)) {
        LOG_E("Wrapping key lacks authorization for padding OAEP");
        return KM_ERROR_INCOMPATIBLE_PADDING_MODE;
    }

    // Check that that was also the padding mode and digest specified
    if (!wrapping_key_params.Contains(TAG_DIGEST, KM_DIGEST_SHA_2_256)) {
        LOG_E("Wrapping key must use SHA2-256");
        return KM_ERROR_INCOMPATIBLE_DIGEST;
    }
    if (!wrapping_key_params.Contains(TAG_PADDING, KM_PAD_RSA_OAEP)) {
        LOG_E("Wrapping key must use OAEP padding");
        return KM_ERROR_INCOMPATIBLE_PADDING_MODE;
    }

    LOG_D("UnwrapKey:2");
    // Step 2 from IKeymasterDevice.hal spec
    // Parse wrapped key
    KeymasterBlob iv;
    KeymasterKeyBlob transit_key;
    KeymasterKeyBlob secure_key;
    KeymasterBlob tag;
    KeymasterBlob wrapped_key_description;
    error = parse_wrapped_key(wrapped_key_blob, &iv, &transit_key, &secure_key,
                              &tag, wrapped_key_params, wrapped_key_format,
                              &wrapped_key_description);
    if (error != KM_ERROR_OK) {
        return error;
    }

    // Decrypt encryptedTransportKey (transit_key) with wrapping_key
    auto operation_factory = wrapping_key->key_factory()->GetOperationFactory(
            KM_PURPOSE_DECRYPT);
    if (operation_factory == NULL) {
        return KM_ERROR_UNKNOWN_ERROR;
    }

    AuthorizationSet out_params;
    OperationPtr operation(operation_factory->CreateOperation(
            std::move(*wrapping_key), wrapping_key_params, &error));
    if ((operation.get() == NULL) || (error != KM_ERROR_OK)) {
        return error;
    }

    error = operation->Begin(wrapping_key_params, &out_params);
    if (error != KM_ERROR_OK) {
        return error;
    }

    Buffer input;
    Buffer output;
    // Explicitly reinitialize rather than constructing in order to report
    // allocation failure.
    if (!input.Reinitialize(transit_key.key_material,
                            transit_key.key_material_size)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    error = operation->Finish(wrapping_key_params, input,
                              Buffer() /* signature */, &out_params, &output);
    if (error != KM_ERROR_OK) {
        return error;
    }

    KeymasterKeyBlob transport_key = {
            output.peek_read(),
            output.available_read(),
    };

    LOG_D("UnwrapKey:3");
    // Step 3 of IKeymasterDevice.hal
    // XOR the transit key with the masking key
    if (transport_key.key_material_size != masking_key.key_material_size) {
        return KM_ERROR_INVALID_ARGUMENT;
    }
    for (size_t i = 0; i < transport_key.key_material_size; i++) {
        transport_key.writable_data()[i] ^= masking_key.key_material[i];
    }

    LOG_D("UnwrapKey:4");
    // Step 4 of IKeymasterDevice.hal
    // transit_key_authorizations is defined by spec
    // TODO the mac len is NOT in the spec, but probably should be
    auto transport_key_authorizations =
            AuthorizationSetBuilder()
                    .AesEncryptionKey(256)
                    .Padding(KM_PAD_NONE)
                    .Authorization(TAG_BLOCK_MODE, KM_MODE_GCM)
                    .Authorization(TAG_NONCE, iv)
                    .Authorization(TAG_MIN_MAC_LENGTH, 128)
                    .build();
    auto validity = transport_key_authorizations.is_valid();
    if (validity != AuthorizationSet::Error::OK) {
        return TranslateAuthorizationSetError(validity);
    }

    // gcm_params is also defined by spec
    // TODO same problem with mac len not being specced
    auto gcm_params = AuthorizationSetBuilder()
                              .Padding(KM_PAD_NONE)
                              .Authorization(TAG_BLOCK_MODE, KM_MODE_GCM)
                              .Authorization(TAG_NONCE, iv)
                              .Authorization(TAG_MAC_LENGTH, 128)
                              .build();
    validity = gcm_params.is_valid();
    if (validity != AuthorizationSet::Error::OK) {
        return TranslateAuthorizationSetError(validity);
    }

    auto aes_factory = GetKeyFactory(KM_ALGORITHM_AES);
    if (aes_factory == NULL) {
        return KM_ERROR_UNKNOWN_ERROR;
    }

    UniquePtr<Key> aes_transport_key;
    error = aes_factory->LoadKey(std::move(transport_key), gcm_params,
                                 std::move(transport_key_authorizations),
                                 AuthorizationSet(), &aes_transport_key);
    if (error != KM_ERROR_OK) {
        return error;
    }

    auto aes_operation_factory =
            GetOperationFactory(KM_ALGORITHM_AES, KM_PURPOSE_DECRYPT);
    if (aes_operation_factory == NULL) {
        return KM_ERROR_UNKNOWN_ERROR;
    }

    OperationPtr aes_operation(aes_operation_factory->CreateOperation(
            std::move(*aes_transport_key), gcm_params, &error));
    if ((aes_operation.get() == NULL) || (error != KM_ERROR_OK)) {
        return error;
    }

    error = aes_operation->Begin(gcm_params, &out_params);
    if (error != KM_ERROR_OK) {
        return error;
    }

    size_t update_consumed = 0;
    AuthorizationSet update_outparams;

    Buffer encrypted_key;
    Buffer plaintext_key;

    // Separate initialization to catch memory errors
    size_t total_key_size = secure_key.key_material_size + tag.data_length;
    if (!plaintext_key.Reinitialize(total_key_size)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }
    if (!encrypted_key.Reinitialize(total_key_size)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Concatenate key data
    if (!encrypted_key.write(secure_key.key_material,
                             secure_key.key_material_size)) {
        return KM_ERROR_UNKNOWN_ERROR;
    }
    if (!encrypted_key.write(tag.data, tag.data_length)) {
        return KM_ERROR_UNKNOWN_ERROR;
    }

    auto update_params =
            AuthorizationSetBuilder()
                    .Authorization(TAG_ASSOCIATED_DATA,
                                   wrapped_key_description.data,
                                   wrapped_key_description.data_length)
                    .build();
    validity = update_params.is_valid();
    if (validity != AuthorizationSet::Error::OK) {
        return TranslateAuthorizationSetError(validity);
    }

    error = aes_operation->Update(update_params, encrypted_key,
                                  &update_outparams, &plaintext_key,
                                  &update_consumed);
    if (error != KM_ERROR_OK) {
        return error;
    }

    AuthorizationSet finish_params;
    AuthorizationSet finish_out_params;
    Buffer finish_input;
    error = aes_operation->Finish(finish_params, finish_input,
                                  Buffer() /* signature */, &finish_out_params,
                                  &plaintext_key);
    if (error != KM_ERROR_OK) {
        return error;
    }

    *wrapped_key_material = {plaintext_key.peek_read(),
                             plaintext_key.available_read()};

    if (!wrapped_key_material->key_material && plaintext_key.peek_read()) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    LOG_D("UnwrapKey:Done");
    return error;
}

keymaster_error_t TrustyKeymasterContext::CheckConfirmationToken(
        const uint8_t* input_data,
        size_t input_data_size,
        const uint8_t confirmation_token[kConfirmationTokenSize]) const {
    // Note: ConfirmationUI is using the same secret key as auth tokens, the
    // difference is that messages are prefixed using the message tag
    // "confirmation token".
    keymaster_key_blob_t auth_token_key;
    keymaster_error_t error = GetAuthTokenKey(&auth_token_key);
    if (error != KM_ERROR_OK) {
        return error;
    }

    uint8_t computed_hash[EVP_MAX_MD_SIZE];
    unsigned int computed_hash_length;
    if (!HMAC(EVP_sha256(), auth_token_key.key_material,
              auth_token_key.key_material_size, input_data, input_data_size,
              computed_hash, &computed_hash_length)) {
        return KM_ERROR_UNKNOWN_ERROR;
    }

    if (computed_hash_length != kConfirmationTokenSize ||
        memcmp_s(computed_hash, confirmation_token, kConfirmationTokenSize) !=
                0) {
        return KM_ERROR_NO_USER_CONFIRMATION;
    }

    return KM_ERROR_OK;
}

std::unique_ptr<cppbor::Map> TrustyKeymasterContext::GetDeviceIds() const {
    // Use the most up to date version of device info, back compat is
    // unnecessary here.
    return trusty_remote_provisioning_context_->CreateDeviceInfo(
            3 /* csrVersion */);
}

}  // namespace keymaster
