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
#include <string_view>

#include <keymaster/attestation_context.h>
#include <keymaster/contexts/pure_soft_remote_provisioning_context.h>
#include <keymaster/contexts/soft_attestation_context.h>
#include <keymaster/keymaster_context.h>
#include <keymaster/km_openssl/attestation_record.h>
#include <keymaster/km_openssl/soft_keymaster_enforcement.h>
#include <keymaster/km_openssl/software_random_source.h>
#include <keymaster/pure_soft_secure_key_storage.h>
#include <keymaster/random_source.h>
#include <keymaster/soft_key_factory.h>

namespace keymaster {

class SoftKeymasterKeyRegistrations;
class Keymaster0Engine;
class Keymaster1Engine;
class Key;

/**
 * SoftKeymasterContext provides the context for a non-secure implementation of AndroidKeymaster.
 */
class PureSoftKeymasterContext : public KeymasterContext,
                                 protected SoftwareKeyBlobMaker,
                                 public SoftAttestationContext,
                                 SoftwareRandomSource {
  public:
    // Security level must only be used for testing.
    explicit PureSoftKeymasterContext(
        KmVersion version, keymaster_security_level_t security_level = KM_SECURITY_LEVEL_SOFTWARE);
    ~PureSoftKeymasterContext() override;

    KmVersion GetKmVersion() const override { return AttestationContext::GetKmVersion(); }

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
    Buffer GenerateUniqueId(uint64_t creation_date_time, const keymaster_blob_t& application_id,
                            bool reset_since_rotation, keymaster_error_t* error) const override;

    KeymasterEnforcement* enforcement_policy() override {
        // SoftKeymaster does no enforcement; it's all done by Keystore.
        return &soft_keymaster_enforcement_;
    }

    SecureKeyStorage* secure_key_storage() override { return pure_soft_secure_key_storage_.get(); }

    RemoteProvisioningContext* GetRemoteProvisioningContext() const override {
        return pure_soft_remote_provisioning_context_.get();
    }

    keymaster_error_t SetVerifiedBootInfo(std::string_view boot_state,
                                          std::string_view bootloader_state,
                                          const std::vector<uint8_t>& vbmeta_digest) override;

    keymaster_error_t SetVendorPatchlevel(uint32_t vendor_patchlevel) override;

    keymaster_error_t SetBootPatchlevel(uint32_t boot_patchlevel) override;

    std::optional<uint32_t> GetVendorPatchlevel() const override { return vendor_patchlevel_; }

    std::optional<uint32_t> GetBootPatchlevel() const override { return boot_patchlevel_; }

    /*********************************************************************************************
     * Implement SoftwareKeyBlobMaker
     */
    keymaster_error_t CreateKeyBlob(const AuthorizationSet& auths, keymaster_key_origin_t origin,
                                    const KeymasterKeyBlob& key_material, KeymasterKeyBlob* blob,
                                    AuthorizationSet* hw_enforced,
                                    AuthorizationSet* sw_enforced) const override;

    keymaster_error_t
    UnwrapKey(const KeymasterKeyBlob& wrapped_key_blob, const KeymasterKeyBlob& wrapping_key_blob,
              const AuthorizationSet& wrapping_key_params, const KeymasterKeyBlob& masking_key,
              AuthorizationSet* wrapped_key_params, keymaster_key_format_t* wrapped_key_format,
              KeymasterKeyBlob* wrapped_key_material) const override;

    /*********************************************************************************************
     * Implement AttestationContext
     */

    const VerifiedBootParams* GetVerifiedBootParams(keymaster_error_t* error) const override;

    keymaster_security_level_t GetSecurityLevel() const override { return security_level_; }

  protected:
    std::unique_ptr<KeyFactory> rsa_factory_;
    std::unique_ptr<KeyFactory> ec_factory_;
    std::unique_ptr<KeyFactory> aes_factory_;
    std::unique_ptr<KeyFactory> tdes_factory_;
    std::unique_ptr<KeyFactory> hmac_factory_;
    uint32_t os_version_;
    uint32_t os_patchlevel_;
    std::optional<std::string> bootloader_state_;
    std::optional<std::string> verified_boot_state_;
    std::optional<std::vector<uint8_t>> vbmeta_digest_;
    std::optional<uint32_t> vendor_patchlevel_;
    std::optional<uint32_t> boot_patchlevel_;
    SoftKeymasterEnforcement soft_keymaster_enforcement_;
    const keymaster_security_level_t security_level_;
    std::unique_ptr<SecureKeyStorage> pure_soft_secure_key_storage_;
    std::unique_ptr<PureSoftRemoteProvisioningContext> pure_soft_remote_provisioning_context_;
};

}  // namespace keymaster
