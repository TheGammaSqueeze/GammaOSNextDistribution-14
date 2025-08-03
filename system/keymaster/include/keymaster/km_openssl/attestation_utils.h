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

#ifndef KM_OPENSSL_ATTESTATION_UTILS_H_
#define KM_OPENSSL_ATTESTATION_UTILS_H_

#include <hardware/keymaster_defs.h>
#include <keymaster/android_keymaster_utils.h>
#include <keymaster/attestation_context.h>

#include <openssl/x509v3.h>

#include "openssl_utils.h"

namespace keymaster {

class AuthorizationSet;
class AttestationContext;
class AsymmetricKey;
class Key;

struct AttestKeyInfo {
    AttestKeyInfo() = default;
    AttestKeyInfo(const UniquePtr<Key>& key, const KeymasterBlob* issuer_subject,
                  keymaster_error_t* error);
    AttestKeyInfo(AttestKeyInfo&&) = default;
    AttestKeyInfo(const AttestKeyInfo&) = delete;

    void operator=(const AttestKeyInfo&) = delete;

    explicit operator bool() const { return signing_key.get() != nullptr; }

    EVP_PKEY_Ptr signing_key;
    const KeymasterBlob* issuer_subject;
};

/**
 * Generate attestation certificate.
 *
 * @param key is an AsymmetricKey object containing the key to be attested.  The contained
 *        authorization lists are used for the key description.
 *
 * @param attest_params contains parameters for the attestation, including:
 *
 *         TAG_ATTESTATION_CHALLENGE
 *         TAG_ATTESTATION_APPLICATION_ID
 *         TAG_ATTESTATION_ID_*
 *         TAG_DEVICE_UNIQUE_ATTESTATION
 *         TAG_RESET_SINCE_ROTATION
 *
 * @param attest_key optionally contains the attestation key and issuer subject to use.  If
 *        attest_key.signing_key is non-null, that key will be used to sign the attestation and the
 *        issuer subject that will be placed in the attestation certificate.  This is only used for
 *        KeyMint.  If attest_key.signing_key is null, the signing key will be obtained from the
 *        AttestationContext, and the issuer subject will be obtained from the cetificate chain
 *        provided by the AttestationContext.
 *
 * @param context is the attestation context, used to generate unique IDs and obtain boot parameters
 *        as well as provide information about the SecurityLevel and KM version.
 *
 * @param error must be non-null and will be used to return any errors.  In the event of an error
 *        the returned certificate chain will be empty.
 *
 * @return Returns the completed certificate chain, ordered from leaf (which is the generated
 *         attestation certificate) to root.
 */
CertificateChain generate_attestation(const AsymmetricKey& key,
                                      const AuthorizationSet& attest_params,
                                      AttestKeyInfo attest_key,
                                      const AttestationContext& context,  //
                                      keymaster_error_t* error);

/**
 * Generate attestation certificate.
 *
 * @param evp_key contains the key to be attested.  Must not be null.
 *
 * @param sw_enforced contains the software-enforced elements of the key description.
 *
 * @param hw_enforced contains the hardware-enforced elements of the key description.
 *
 * @param attest_params contains parameters for the attestation, including:
 *
 *         TAG_ATTESTATION_CHALLENGE
 *         TAG_ATTESTATION_APPLICATION_ID
 *         TAG_ATTESTATION_ID_*
 *         TAG_DEVICE_UNIQUE_ATTESTATION
 *         TAG_RESET_SINCE_ROTATION
 *
 * @param attest_key optionally contains the attestation key and issuer subject to use.  If
 *        attest_key.signing_key is non-null, that key will be used to sign the attestation and the
 *        issuer subject that will be placed in the attestation certificate.  This is only used for
 *        KeyMint.  If attest_key.signign_key is null, the signing key will be obtained from the
 *        AttestationContext, and the issuer subject will be obtained from the cetificate chain
 *        provided by the AttestationContext.
 *
 * @param context is the attestation context, used to generate unique IDs and obtain boot parameters
 *        as well as provide information about the SecurityLevel and KM version.
 *
 * @param error must be non-null and will be used to return any errors.  In the event of an error
 *        the returned certificate chain will be empty.
 *
 * @return Returns the completed certificate chain, ordered from leaf (which is the generated
 *         attestation certificate) to root.
 */
CertificateChain generate_attestation(const EVP_PKEY* evp_key,              //
                                      const AuthorizationSet& sw_enforced,  //
                                      const AuthorizationSet& hw_enforced,  //
                                      const AuthorizationSet& attest_params,
                                      AttestKeyInfo attest_key,
                                      const AttestationContext& context,  //
                                      keymaster_error_t* error);

}  // namespace keymaster

#endif  // KM_OPENSSL_ATTESTATION_UTILS_H_
