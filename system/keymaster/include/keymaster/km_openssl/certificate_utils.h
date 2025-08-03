/*
 * Copyright 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <openssl/x509v3.h>

#include <hardware/keymaster_defs.h>

#include <keymaster/km_openssl/openssl_utils.h>
#include <keymaster/km_version.h>

namespace keymaster {

class AsymmetricKey;
class AuthorizationSet;

keymaster_error_t make_name_from_str(const char name[], X509_NAME_Ptr* name_out);

keymaster_error_t make_name_from_der(const keymaster_blob_t& name, X509_NAME_Ptr* name_out);

keymaster_error_t get_common_name(X509_NAME* name, UniquePtr<const char[]>* name_out);

// CertificateParams encapsulates a set of certificate parameters that may be provided by the
// caller, or may be defaulted.
struct CertificateCallerParams {
    BIGNUM_Ptr serial;
    X509_NAME_Ptr subject_name;
    int64_t active_date_time;  // Time since epoch in ms
    int64_t expire_date_time;  // Time since epoch in ms
    bool is_signing_key = false;
    bool is_encryption_key = false;
    bool is_agreement_key = false;
};

keymaster_error_t get_certificate_params(const AuthorizationSet& caller_params,
                                         CertificateCallerParams* cert_params, KmVersion kmVersion);

keymaster_error_t make_key_usage_extension(bool is_signing_key, bool is_encryption_key,
                                           bool is_key_agreement_key,
                                           X509_EXTENSION_Ptr* usage_extension_out);

// Creates a rump certificate structure with serial, subject and issuer names, as well as activation
// and expiry date.  Callers should pass an empty X509_Ptr and check the return value for
// KM_ERROR_OK (0) before accessing the result.
keymaster_error_t make_cert_rump(const uint32_t serial, const X509_NAME* issuer,
                                 const CertificateCallerParams& cert_params, X509_Ptr* cert_out);

keymaster_error_t make_cert(const EVP_PKEY* evp_pkey, const X509_NAME* issuer,
                            const CertificateCallerParams& cert_params, X509_Ptr* cert_out);

// Sign the certificate with the provided signing key.
keymaster_error_t sign_cert(X509* certificate, const EVP_PKEY* signing_key);

// Generate a certificate for the provided asymmetric key, with params.  The certificate will be
// self-signed unless `fake_signature` is set, in which case a fake signature will be placed in the
// certificate.  Specifically, the signature algorithm will be set to RSA PKCS#1 v1.5 with digest
// SHA-256, but the signature field will contain a single zero byte.
CertificateChain generate_self_signed_cert(const AsymmetricKey& key, const AuthorizationSet& params,
                                           bool fake_signature, keymaster_error_t* error);

keymaster_error_t encode_certificate(X509* certificate, keymaster_blob_t* derCert);

}  // namespace keymaster
