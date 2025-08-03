/*
 * Copyright 2014 The Android Open Source Project
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

#pragma once

#include <utility>

#include <keymaster/km_openssl/aes_key.h>
#include <trusty_ipc.h>

namespace keymaster {

class TrustyAesKeyFactory : public AesKeyFactory {
public:
    explicit TrustyAesKeyFactory(const SoftwareKeyBlobMaker& blob_maker,
                                 const RandomSource& random_source)
            : AesKeyFactory(blob_maker, random_source),
              hwwsk_chan_(INVALID_IPC_HANDLE) {}

    ~TrustyAesKeyFactory() { reset_hwwsk_chan(); }

    keymaster_error_t GenerateKey(const AuthorizationSet& key_description,
                                  UniquePtr<Key> attestation_signing_key,
                                  const KeymasterBlob& issuer_subject,
                                  KeymasterKeyBlob* key_blob,
                                  AuthorizationSet* hw_enforced,
                                  AuthorizationSet* sw_enforced,
                                  CertificateChain* cert_chain) const override;

    keymaster_error_t ImportKey(
            const AuthorizationSet& key_description,
            keymaster_key_format_t input_key_material_format,
            const KeymasterKeyBlob& input_key_material,
            UniquePtr<Key> attestation_signing_key,
            const KeymasterBlob& issuer_subject,
            KeymasterKeyBlob* output_key_blob,
            AuthorizationSet* hw_enforced,
            AuthorizationSet* sw_enforced,
            CertificateChain* cert_chain) const override;

    keymaster_error_t LoadKey(KeymasterKeyBlob&& key_material,
                              const AuthorizationSet& additional_params,
                              AuthorizationSet&& hw_enforced,
                              AuthorizationSet&& sw_enforced,
                              UniquePtr<Key>* key) const override;

    handle_t get_hwwsk_chan(void) const;
    void reset_hwwsk_chan(void) const;

private:
    keymaster_error_t CreateHwStorageKeyBlob(
            const AuthorizationSet& key_description,
            const KeymasterKeyBlob& input_key_material,
            KeymasterKeyBlob* output_key_blob,
            AuthorizationSet* hw_enforced,
            AuthorizationSet* sw_enforced) const;

    mutable handle_t hwwsk_chan_;
};

class HwStorageKey : public AesKey {
public:
    HwStorageKey(KeymasterKeyBlob&& key_material,
                 AuthorizationSet&& hw_enforced,
                 AuthorizationSet&& sw_enforced,
                 const KeyFactory* key_factory)
            : AesKey(std::move(key_material),
                     std::move(hw_enforced),
                     std::move(sw_enforced),
                     key_factory) {}

    keymaster_error_t formatted_key_material(keymaster_key_format_t,
                                             UniquePtr<uint8_t[]>*,
                                             size_t*) const override;
};

}  // namespace keymaster
