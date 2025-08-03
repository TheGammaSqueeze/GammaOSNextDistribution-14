/*
 * Copyright 2020 The Android Open Source Project
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

#include <keymaster/authorization_set.h>
#include <keymaster/km_version.h>

namespace keymaster {

class Key;

/**
 * AttestationContext provides an abstract interface to the information needed
 * to generate attestation certificates.
 */
class AttestationContext {
  protected:
    virtual ~AttestationContext() {}

  public:
    explicit AttestationContext(KmVersion version) : version_(version) {}

    KmVersion GetKmVersion() const { return version_; }

    /**
     * Returns the security level (SW or TEE) of this keymaster implementation.
     */
    virtual keymaster_security_level_t GetSecurityLevel() const = 0;

    /**
     * Verify that the device IDs provided in `attestation_params` match the device's actual IDs and
     * copy the verified IDs into `attestation`.  If *any* of the IDs do not match or verification
     * is not possible, return KM_ERROR_CANNOT_ATTEST_IDS.  If device ID attestation is unsupported,
     * ignore all arguments and return KM_ERROR_UNIMPLEMENTED.  If ID attestation is supported and
     * no ID mismatches are found, return KM_ERROR_OK;
     */
    virtual keymaster_error_t
    VerifyAndCopyDeviceIds(const AuthorizationSet& /* attestation_params */,
                           AuthorizationSet* /* attestation */) const {
        return KM_ERROR_UNIMPLEMENTED;
    }

    /**
     * Generate the current unique ID.  If unique IDs are not supported, set `error` to
     * KM_ERROR_UNIMPLEMENTED.
     */
    virtual Buffer GenerateUniqueId(uint64_t /*creation_date_time*/,
                                    const keymaster_blob_t& /*application_id*/,
                                    bool /*reset_since_rotation*/, keymaster_error_t* error) const {
        if (error) *error = KM_ERROR_UNIMPLEMENTED;
        return {};
    }

    struct VerifiedBootParams {
        keymaster_blob_t verified_boot_key;
        keymaster_blob_t verified_boot_hash;
        keymaster_verified_boot_t verified_boot_state;
        bool device_locked;
    };

    /**
     * Returns verified boot parameters for the Attestation Extension.  For hardware-based
     * implementations, these will be the values reported by the bootloader. By default, verified
     * boot state is unknown, and KM_ERROR_UNIMPLEMENTED is returned.
     *
     * The AttestationContext retains ownership of the VerifiedBootParams.
     */
    virtual const VerifiedBootParams* GetVerifiedBootParams(keymaster_error_t* error) const {
        *error = KM_ERROR_UNIMPLEMENTED;
        return nullptr;
    }

    /**
     * Return the factory attestation signing key.  If not available, set `error` to
     * KM_ERROR_UNIMPLEMENTED.
     */
    virtual KeymasterKeyBlob GetAttestationKey(keymaster_algorithm_t algorithm,
                                               keymaster_error_t* error) const = 0;

    /**
     * Return the factory attestation signing key certificate chain.  If not available, set `error`
     * to KM_ERROR_UNIMPLEMENTED.
     */
    virtual CertificateChain GetAttestationChain(keymaster_algorithm_t algorithm,
                                                 keymaster_error_t* error) const = 0;

  protected:
    KmVersion version_;
};

}  // namespace keymaster
