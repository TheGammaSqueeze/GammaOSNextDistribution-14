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

#pragma once

#include <memory>
#include <string>

#include <openssl/evp.h>

#include <hardware/keymaster1.h>

#include <keymaster/attestation_context.h>
#include <keymaster/contexts/soft_attestation_context.h>
#include <keymaster/keymaster_context.h>
#include <keymaster/km_openssl/software_random_source.h>
#include <keymaster/random_source.h>
#include <keymaster/soft_key_factory.h>

namespace keymaster {

class SoftKeymasterKeyRegistrations;
class Keymaster1Engine;
class Key;

/**
 * SoftKeymasterContext provides the context for a non-secure implementation of AndroidKeymaster
 * that can wrap a Keymaster0 implementation or an incomplete Keymaster1 implementation (one that
 * lacks support for all required digests).
 */
class SoftKeymasterContext : public KeymasterContext,
                             SoftwareKeyBlobMaker,
                             SoftwareRandomSource,
                             public SoftAttestationContext {
  public:
    explicit SoftKeymasterContext(KmVersion version, const std::string& root_of_trust = "SW");
    ~SoftKeymasterContext() override;

    KmVersion GetKmVersion() const override { return AttestationContext::GetKmVersion(); }

    /**
     * Use the specified HW keymaster1 device for performing undigested RSA and EC operations after
     * digesting has been done in software.  Takes ownership of the specified device (will call
     * keymaster1_device->common.close());
     */
    keymaster_error_t SetHardwareDevice(keymaster1_device_t* keymaster1_device);

    /*********************************************************************************************
     * Implement KeymasterContext
     */
    keymaster_error_t SetSystemVersion(uint32_t os_version, uint32_t os_patchlevel) override;
    void GetSystemVersion(uint32_t* os_version, uint32_t* os_patchlevel) const override;

    KeyFactory* GetKeyFactory(keymaster_algorithm_t algorithm) const override;
    OperationFactory* GetOperationFactory(keymaster_algorithm_t algorithm,
                                          keymaster_purpose_t purpose) const override;
    keymaster_algorithm_t* GetSupportedAlgorithms(size_t* algorithms_count) const override;
    keymaster_error_t UpgradeKeyBlob(const KeymasterKeyBlob& key_to_upgrade,
                                     const AuthorizationSet& upgrade_params,
                                     KeymasterKeyBlob* upgraded_key) const override;
    keymaster_error_t ParseKeyBlob(const KeymasterKeyBlob& blob,
                                   const AuthorizationSet& additional_params,
                                   UniquePtr<Key>* key) const override;
    keymaster_error_t DeleteKey(const KeymasterKeyBlob& blob) const override;
    keymaster_error_t DeleteAllKeys() const override;
    keymaster_error_t AddRngEntropy(const uint8_t* buf, size_t length) const override;

    CertificateChain GenerateAttestation(const Key& key, const AuthorizationSet& attest_params,
                                         UniquePtr<Key> attest_key,
                                         const KeymasterBlob& issuer_subject,
                                         keymaster_error_t* error) const override;
    CertificateChain GenerateSelfSignedCertificate(const Key& key,
                                                   const AuthorizationSet& cert_params,
                                                   bool fake_signature,
                                                   keymaster_error_t* error) const override;

    keymaster_error_t
    UnwrapKey(const KeymasterKeyBlob& wrapped_key_blob, const KeymasterKeyBlob& wrapping_key_blob,
              const AuthorizationSet& wrapping_key_params, const KeymasterKeyBlob& masking_key,
              AuthorizationSet* wrapped_key_params, keymaster_key_format_t* wrapped_key_format,
              KeymasterKeyBlob* wrapped_key_material) const override;

    KeymasterEnforcement* enforcement_policy() override {
        // SoftKeymaster does no enforcement; it's all done by Keystore.
        return nullptr;
    }

    /*********************************************************************************************
     * Implement SoftwareKeyBlobMaker
     */
    keymaster_error_t CreateKeyBlob(const AuthorizationSet& auths, keymaster_key_origin_t origin,
                                    const KeymasterKeyBlob& key_material, KeymasterKeyBlob* blob,
                                    AuthorizationSet* hw_enforced,
                                    AuthorizationSet* sw_enforced) const override;
    /*********************************************************************************************/

  private:
    keymaster_error_t ParseKeymaster1HwBlob(const KeymasterKeyBlob& blob,
                                            const AuthorizationSet& additional_params,
                                            KeymasterKeyBlob* key_material,
                                            AuthorizationSet* hw_enforced,
                                            AuthorizationSet* sw_enforced) const;

    std::unique_ptr<Keymaster1Engine> km1_engine_;
    std::unique_ptr<KeyFactory> rsa_factory_;
    std::unique_ptr<KeyFactory> ec_factory_;
    std::unique_ptr<KeyFactory> aes_factory_;
    std::unique_ptr<KeyFactory> tdes_factory_;
    std::unique_ptr<KeyFactory> hmac_factory_;
    keymaster1_device* km1_dev_;
    const KeymasterBlob root_of_trust_;
    uint32_t os_version_;
    uint32_t os_patchlevel_;
};

}  // namespace keymaster
