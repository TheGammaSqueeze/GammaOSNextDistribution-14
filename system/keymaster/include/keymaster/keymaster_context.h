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

#ifndef SYSTEM_KEYMASTER_KEYMASTER_CONTEXT_H_
#define SYSTEM_KEYMASTER_KEYMASTER_CONTEXT_H_

#include <optional>
#include <string_view>

#include <assert.h>

#include <hardware/keymaster_defs.h>

#include <keymaster/android_keymaster_messages.h>
#include <keymaster/android_keymaster_utils.h>
#include <keymaster/keymaster_enforcement.h>
#include <keymaster/km_version.h>
#include <keymaster/remote_provisioning_context.h>
#include <keymaster/secure_key_storage.h>

namespace keymaster {

class AuthorizationSet;
class AttestationContext;
class KeyFactory;
class OperationFactory;
class SecureDeletionSecretStorage;
template <typename BlobType> struct TKeymasterBlob;
typedef TKeymasterBlob<keymaster_key_blob_t> KeymasterKeyBlob;
class Key;

/**
 * KeymasterContext provides a singleton abstract interface that encapsulates various
 * environment-dependent elements of AndroidKeymaster.
 *
 * AndroidKeymaster runs in multiple contexts.  Primarily:
 *
 * - In a trusted execution environment (TEE) as a "secure hardware" implementation.  In this
 *   context keys are wrapped with an master key that never leaves the TEE, TEE-specific routines
 *   are used for random number generation, all AndroidKeymaster-enforced authorizations are
 *   considered hardware-enforced, and there's a bootloader-provided root of trust.
 *
 * - In the non-secure world as a software-only implementation.  In this context keys are not
 *   encrypted (though they are integrity-checked) because there is no place to securely store a
 *   key, OpenSSL is used for random number generation, no AndroidKeymaster-enforced authorizations
 *   are considered hardware enforced and the root of trust is a static string.
 *
 * - In the non-secure world as a hybrid implementation fronting a less-capable hardware
 *   implementation.  For example, a keymaster0 hardware implementation.  In this context keys are
 *   not encrypted by AndroidKeymaster, but some may be opaque blobs provided by the backing
 *   hardware, but blobs that lack the extended authorization lists of keymaster1.  In addition,
 *   keymaster0 lacks many features of keymaster1, including modes of operation related to the
 *   backing keymaster0 keys.  AndroidKeymaster must extend the blobs to add authorization lists,
 *   and must provide the missing operation mode implementations in software, which means that
 *   authorization lists are partially hardware-enforced (the bits that are enforced by the
 *   underlying keymaster0) and partially software-enforced (the rest). OpenSSL is used for number
 *   generation and the root of trust is a static string.
 *
 * More contexts are possible.
 */
class KeymasterContext {
  public:
    KeymasterContext() {}
    virtual ~KeymasterContext(){};

    /**
     * Returns the Keymaster/KeyMint version we're currently implementing.
     *
     * Because AndroidKeymaster supports multiple versions of Keymaster/KeyMint, with slightly
     * different behavior, we sometimes need to branch based on the version currently being
     * implemented.  This method provides the currently-implemented version.
     */
    virtual KmVersion GetKmVersion() const = 0;

    /**
     * Sets the system version as reported by the system *itself*.  This is used to verify that the
     * system believes itself to be running the same version that is reported by the bootloader, in
     * hardware implementations.  For SoftKeymasterDevice, this sets the version information used.
     *
     * If the specified values don't match the bootloader-provided values, this method must return
     * KM_ERROR_INVALID_ARGUMENT;
     */
    virtual keymaster_error_t SetSystemVersion(uint32_t os_version, uint32_t os_patchlevel) = 0;

    /**
     * Returns the system version.  For hardware-based implementations this will be the value
     * reported by the bootloader.  For SoftKeymasterDevice it will be the verion information set by
     * SetSystemVersion above.
     */
    virtual void GetSystemVersion(uint32_t* os_version, uint32_t* os_patchlevel) const = 0;

    virtual const KeyFactory* GetKeyFactory(keymaster_algorithm_t algorithm) const = 0;
    virtual const OperationFactory* GetOperationFactory(keymaster_algorithm_t algorithm,
                                                        keymaster_purpose_t purpose) const = 0;
    virtual const keymaster_algorithm_t* GetSupportedAlgorithms(size_t* algorithms_count) const = 0;

    /**
     * UpgradeKeyBlob takes an existing blob, parses out key material and constructs a new blob with
     * the current format and OS version info.
     */
    virtual keymaster_error_t UpgradeKeyBlob(const KeymasterKeyBlob& key_to_upgrade,
                                             const AuthorizationSet& upgrade_params,
                                             KeymasterKeyBlob* upgraded_key) const = 0;

    /**
     * ParseKeyBlob takes a blob and extracts authorization sets and key material, returning an
     * error if the blob fails integrity checking or decryption.  Note that the returned key
     * material may itself be an opaque blob usable only by secure hardware (in the hybrid case).
     *
     * This method is called by AndroidKeymaster.
     */
    virtual keymaster_error_t ParseKeyBlob(const KeymasterKeyBlob& blob,
                                           const AuthorizationSet& additional_params,
                                           UniquePtr<Key>* key) const = 0;

    /**
     * Take whatever environment-specific action is appropriate (if any) to delete the specified
     * key.
     */
    virtual keymaster_error_t DeleteKey(const KeymasterKeyBlob& /* blob */) const {
        return KM_ERROR_OK;
    }

    /**
     * Take whatever environment-specific action is appropriate to delete all keys.
     */
    virtual keymaster_error_t DeleteAllKeys() const { return KM_ERROR_OK; }

    /**
     * Adds entropy to the Cryptographic Pseudo Random Number Generator used to generate key
     * material, and other cryptographic protocol elements.  Note that if the underlying CPRNG
     * tracks the size of its entropy pool, it should not assume that the provided data contributes
     * any entropy, and it should also ensure that data provided through this interface cannot
     * "poison" the CPRNG outputs, making them predictable.
     */
    virtual keymaster_error_t AddRngEntropy(const uint8_t* buf, size_t length) const = 0;

    /**
     * Return the enforcement policy for this context, or null if no enforcement should be done.
     */
    virtual KeymasterEnforcement* enforcement_policy() = 0;

    /**
     * Return the attestation context for this context.
     */
    virtual AttestationContext* attestation_context() { return nullptr; }

    /**
     * Generate an attestation certificate, with chain.
     *
     * If attest_key is null, the certificate will be signed with the factory attestation key (from
     * AttestationContext) and have the issuer subject set to the subject name from the signing key
     * certificate.  If attest_key is non-null, it will be used to sign the certificate and the
     * provided issuer subject will be used (must contain a DER-encoded X.509 NAME).
     */
    virtual CertificateChain GenerateAttestation(const Key& key,
                                                 const AuthorizationSet& attest_params,
                                                 UniquePtr<Key> attest_key,
                                                 const KeymasterBlob& issuer_subject,
                                                 keymaster_error_t* error) const = 0;

    /**
     * Generate a self-signed certificate.  If fake_signature is true, a fake signature is installed
     * in the certificate, rather than an actual self-signature.  The fake signature will not
     * verify, of course.  In this case the certificate is primarily a way to convey the public key.
     *
     * Note that although the return type is CertificateChain, this is for convenience and
     * consistency with GenerateAttestation, the chain never contains more than a single
     * certificate.
     */
    virtual CertificateChain GenerateSelfSignedCertificate(const Key& key,
                                                           const AuthorizationSet& cert_params,
                                                           bool fake_signature,
                                                           keymaster_error_t* error) const = 0;

    virtual keymaster_error_t
    UnwrapKey(const KeymasterKeyBlob& wrapped_key_blob, const KeymasterKeyBlob& wrapping_key_blob,
              const AuthorizationSet& wrapping_key_params, const KeymasterKeyBlob& masking_key,
              AuthorizationSet* wrapped_key_params, keymaster_key_format_t* wrapped_key_format,
              KeymasterKeyBlob* wrapped_key_material) const = 0;

    /**
     * Return the secure key storage for this context, or null if there is no available secure key
     * storage.
     */
    virtual SecureKeyStorage* secure_key_storage() { return nullptr; }

    /**
     * Return the secure deletion secret storage for this context, or null if none is available.
     *
     * Note that SecureDeletionSecretStorage obsoletes SecureKeyStorage (see method above).  The
     * latter will be removed in the future.
     */
    virtual SecureDeletionSecretStorage* secure_deletion_secret_storage() { return nullptr; }

    /**
     * Checks that the data in |input_data| of size |input_data_size| matches the
     * confirmation token given by |confirmation_token|.
     *
     * Note that |input_data| will already contain the prefixed message tag
     * "confirmation token" (not including NUL byte) so all the implementation
     * of this method needs to do is to calculate HMAC-SHA256 over |input_data|
     * and compare it with |confirmation_token|. To do this the implementation
     * needs access to the secret key shared with the ConfirmationUI TA.
     *
     * Returns KM_ERROR_OK if |input_data| matches |confirmation_token|,
     * KM_ERROR_NO_USER_CONFIRMATION if it doesn't, and if memory allocation
     * fails KM_ERROR_MEMORY_ALLOCATION_FAILED. If not implemented then
     * KM_ERROR_UNIMPLEMENTED is returned.
     */
    virtual keymaster_error_t
    CheckConfirmationToken(const uint8_t* /*input_data*/, size_t /*input_data_size*/,
                           const uint8_t /*confirmation_token*/[kConfirmationTokenSize]) const {
        return KM_ERROR_UNIMPLEMENTED;
    }

    /**
     * Return the remote provisioning context object, or null if remote provisioning is not
     * supported.
     */
    virtual RemoteProvisioningContext* GetRemoteProvisioningContext() const { return nullptr; }

    /**
     * Sets the verified boot metadata. This value should be set by the bootloader.
     * A subsequent to set a different value will return KM_ERROR_INVALID_ARGUMENT.
     */
    virtual keymaster_error_t SetVerifiedBootInfo(std::string_view /*verified_boot_state*/,
                                                  std::string_view /*bootloader_state*/,
                                                  const std::vector<uint8_t>& /*vbmeta_digest*/) {
        return KM_ERROR_UNIMPLEMENTED;
    }

    /**
     * Sets the vendor patchlevel (format YYYYMMDD) for the implementation. This value should
     * be set by the HAL service at start of day.  A subsequent attempt to set a different
     * value will return KM_ERROR_INVALID_ARGUMENT.
     */
    virtual keymaster_error_t SetVendorPatchlevel(uint32_t /* vendor_patchlevel */) {
        return KM_ERROR_UNIMPLEMENTED;
    }

    /**
     * Sets the boot patchlevel (format YYYYMMDD) for the implementation. This value should be set
     * by the bootloader.  A subsequent to set a different value will return
     * KM_ERROR_INVALID_ARGUMENT.
     */
    virtual keymaster_error_t SetBootPatchlevel(uint32_t /* boot_patchlevel */) {
        return KM_ERROR_UNIMPLEMENTED;
    }

    /**
     * Returns the vendor patchlevel, as set by the HAL service using SetVendorPatchlevel.
     */
    virtual std::optional<uint32_t> GetVendorPatchlevel() const { return std::nullopt; }

    /**
     * Returns the boot patchlevel. For hardware-based implementations this will be the value set by
     * the bootloader. For software implementations this will be the information set by
     * SetBootPatchLevel.
     */
    virtual std::optional<uint32_t> GetBootPatchlevel() const { return std::nullopt; }

    /**
     * Sets attestation IDs for the implementation. On physical devices (as opposed to emulators)
     * attestation IDs should only be set during provisioning process.
     */
    virtual keymaster_error_t SetAttestationIds(const SetAttestationIdsRequest& /* request */) {
        return KM_ERROR_UNIMPLEMENTED;
    }

    /**
     * Sets KM3 attestation IDs for the implementation. On physical
     * devices (as opposed to emulators) attestation ID should only be set
     * during provisioning process.
     */
    virtual keymaster_error_t
    SetAttestationIdsKM3(const SetAttestationIdsKM3Request& /* request */) {
        return KM_ERROR_UNIMPLEMENTED;
    }

  private:
    // Uncopyable.
    KeymasterContext(const KeymasterContext&);
    void operator=(const KeymasterContext&);
};

}  // namespace keymaster

#endif  // SYSTEM_KEYMASTER_KEYMASTER_CONTEXT_H_
