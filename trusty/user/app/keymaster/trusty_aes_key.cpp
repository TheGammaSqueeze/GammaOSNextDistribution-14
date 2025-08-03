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

#include "trusty_aes_key.h"

#include <utility>

#include <assert.h>

#include <keymaster/logger.h>

#include <lib/hwwsk/client.h>
#include <lib/tipc/tipc.h>
#include <uapi/err.h>

namespace keymaster {

handle_t TrustyAesKeyFactory::get_hwwsk_chan(void) const {
    handle_t hchan;

    if (hwwsk_chan_ == INVALID_IPC_HANDLE) {
        // open new connection
        int rc = tipc_connect(&hchan, HWWSK_PORT);
        if (rc < 0) {
            LOG_E("HWWSK: connect failed (%d)", rc);
            return (handle_t)rc;
        }
        hwwsk_chan_ = hchan;
    }
    return hwwsk_chan_;
}

void TrustyAesKeyFactory::reset_hwwsk_chan(void) const {
    if (hwwsk_chan_ != INVALID_IPC_HANDLE) {
        close(hwwsk_chan_);
        hwwsk_chan_ = INVALID_IPC_HANDLE;
    }
}

keymaster_error_t TrustyAesKeyFactory::CreateHwStorageKeyBlob(
        const AuthorizationSet& key_description,
        const KeymasterKeyBlob& input_key_material,
        KeymasterKeyBlob* output_key_blob,
        AuthorizationSet* hw_enforced,
        AuthorizationSet* sw_enforced) const {
    int rc;
    handle_t hchan;
    uint32_t key_size;
    uint32_t key_flags;
    uint8_t sk_blob[HWWSK_MAX_MSG_SIZE];

    if (!output_key_blob || !hw_enforced || !sw_enforced) {
        return KM_ERROR_OUTPUT_PARAMETER_NULL;
    }

    // If there is a TAG_BLOCK_MODE reject such request.
    //
    // Hw wrapped storage key is not intended to work for normal
    // SW encryption/decryption operations. Removing supported
    // block mode tag effectively achieves that.
    //
    if (key_description.find(TAG_BLOCK_MODE) != -1) {
        LOG_E("HWWSK: unsupported tag");
        return KM_ERROR_UNSUPPORTED_TAG;
    }

    // Get requested key size
    if (!key_description.GetTagValue(TAG_KEY_SIZE, &key_size)) {
        LOG_E("HWWSK: missing key size tag");
        return KM_ERROR_UNSUPPORTED_KEY_SIZE;
    }

    // HWWSK service handle
    hchan = get_hwwsk_chan();
    if (hchan < 0) {
        return KM_ERROR_UNKNOWN_ERROR;
    }

    // Build key flags
    key_flags = 0;
    if (key_description.GetTagValue(TAG_ROLLBACK_RESISTANCE)) {
        key_flags |= HWWSK_FLAGS_ROLLBACK_RESISTANCE;
    }

    // call server to generate hardware wrapped key blob
    rc = hwwsk_generate_key(hchan, sk_blob, sizeof(sk_blob), key_size,
                            key_flags, input_key_material.key_material,
                            input_key_material.key_material_size);
    if (rc < 0) {
        if (rc == ERR_NOT_SUPPORTED &&
            (key_flags & HWWSK_FLAGS_ROLLBACK_RESISTANCE)) {
            key_flags &= ~HWWSK_FLAGS_ROLLBACK_RESISTANCE;
            rc = hwwsk_generate_key(hchan, sk_blob, sizeof(sk_blob), key_size,
                                    key_flags, input_key_material.key_material,
                                    input_key_material.key_material_size);
        }
        if (rc < 0) {
            if (rc != ERR_NOT_SUPPORTED) {
                // Reset IPC connection for any error other then
                // ERR_NOT_SUPPORTED
                reset_hwwsk_chan();
            }
            LOG_E("HWWSK: generate key blob failed(%d)", rc);
            return KM_ERROR_UNKNOWN_ERROR;
        }
    }

    KeymasterKeyBlob hwwsk_key_blob(sk_blob, rc);

    // wrap it with keymaster
    keymaster_key_origin_t key_origin = input_key_material.key_material_size
                                                ? KM_ORIGIN_IMPORTED
                                                : KM_ORIGIN_GENERATED;

    return blob_maker_.CreateKeyBlob(key_description, key_origin,
                                     hwwsk_key_blob, output_key_blob,
                                     hw_enforced, sw_enforced);
}

keymaster_error_t TrustyAesKeyFactory::GenerateKey(
        const AuthorizationSet& key_description,
        UniquePtr<Key> /* attestation_signing_key */,
        const KeymasterBlob& /* issuer_subject */,
        KeymasterKeyBlob* output_key_blob,
        AuthorizationSet* hw_enforced,
        AuthorizationSet* sw_enforced,
        CertificateChain* cert_chain) const {
    if (key_description.GetTagValue(TAG_STORAGE_KEY)) {
#if WITH_HWWSK_SUPPORT
        KeymasterKeyBlob input_key_material;  // no input data

        return CreateHwStorageKeyBlob(key_description, input_key_material,
                                      output_key_blob, hw_enforced,
                                      sw_enforced);
#else
        return KM_ERROR_UNSUPPORTED_TAG;
#endif
    }

    return AesKeyFactory::GenerateKey(key_description,
                                      {} /* attestation_signing_key */,
                                      {} /* issuer_subject */, output_key_blob,
                                      hw_enforced, sw_enforced, cert_chain);
}

keymaster_error_t TrustyAesKeyFactory::ImportKey(
        const AuthorizationSet& key_description,
        keymaster_key_format_t input_key_material_format,
        const KeymasterKeyBlob& input_key_material,
        UniquePtr<Key> /* attestation_signing_key */,
        const KeymasterBlob& /* issuer_subject */,
        KeymasterKeyBlob* output_key_blob,
        AuthorizationSet* hw_enforced,
        AuthorizationSet* sw_enforced,
        CertificateChain* cert_chain) const {
    if (key_description.GetTagValue(TAG_STORAGE_KEY)) {
#if WITH_HWWSK_SUPPORT
        // We expect input data in RAW format
        if (input_key_material_format != KM_KEY_FORMAT_RAW) {
            return KM_ERROR_UNSUPPORTED_KEY_FORMAT;
        }

        return CreateHwStorageKeyBlob(key_description, input_key_material,
                                      output_key_blob, hw_enforced,
                                      sw_enforced);
#else
        return KM_ERROR_UNSUPPORTED_TAG;
#endif
    }

    return AesKeyFactory::ImportKey(
            key_description, input_key_material_format, input_key_material,
            {} /* attestation_signing_key */, {} /* issuer_subject */,
            output_key_blob, hw_enforced, sw_enforced, cert_chain);
}

keymaster_error_t TrustyAesKeyFactory::LoadKey(
        KeymasterKeyBlob&& key_material,
        const AuthorizationSet& additional_params,
        AuthorizationSet&& hw_enforced,
        AuthorizationSet&& sw_enforced,
        UniquePtr<Key>* key) const {
    if (hw_enforced.GetTagValue(TAG_STORAGE_KEY)) {
#if WITH_HWWSK_SUPPORT
        // for storage key

        if (!key) {
            return KM_ERROR_OUTPUT_PARAMETER_NULL;
        }

        // If there is a TAG_BLOCK_MODE reject such key
        if ((hw_enforced.find(TAG_BLOCK_MODE) != -1) ||
            (sw_enforced.find(TAG_BLOCK_MODE) != -1)) {
            return KM_ERROR_UNSUPPORTED_TAG;
        }

        key->reset(new (std::nothrow)
                           HwStorageKey(std::move(key_material), std::move(hw_enforced),
                                        std::move(sw_enforced), this));
        if (!key->get()) {
            return KM_ERROR_MEMORY_ALLOCATION_FAILED;
        }

        return KM_ERROR_OK;
#else
        return KM_ERROR_UNSUPPORTED_TAG;
#endif
    }

    return AesKeyFactory::LoadKey(std::move(key_material), additional_params,
                                  std::move(hw_enforced), std::move(sw_enforced), key);
}

keymaster_error_t HwStorageKey::formatted_key_material(
        keymaster_key_format_t fmt,
        UniquePtr<uint8_t[]>* material,
        size_t* sz) const {
    int rc;
    handle_t hchan;

    if (fmt != KM_KEY_FORMAT_RAW) {
        return KM_ERROR_UNSUPPORTED_KEY_FORMAT;
    }

    material->reset(new (std::nothrow) uint8_t[HWWSK_MAX_MSG_SIZE]);

    if (material->get() == nullptr) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // get HWWSK service handle
    hchan = ((TrustyAesKeyFactory*)key_factory())->get_hwwsk_chan();
    if (hchan < 0) {
        return KM_ERROR_UNKNOWN_ERROR;
    }

    rc = hwwsk_export_key(hchan, material->get(), HWWSK_MAX_MSG_SIZE,
                          key_material_.key_material,
                          key_material_.key_material_size);
    if (rc < 0) {
        if (rc != ERR_NOT_SUPPORTED) {
            // Reset IPC connection for any error other then ERR_NOT_SUPPORTED
            ((TrustyAesKeyFactory*)key_factory())->reset_hwwsk_chan();
        }
        LOG_E("HWWSK: export key failed (%d)", rc);
        return KM_ERROR_UNKNOWN_ERROR;
    }

    *sz = (size_t)rc;

    return KM_ERROR_OK;
}

}  // namespace keymaster
