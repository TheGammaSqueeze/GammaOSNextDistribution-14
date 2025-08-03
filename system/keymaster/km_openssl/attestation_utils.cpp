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

#include <utility>

#include <openssl/evp.h>
#include <openssl/x509v3.h>

#include <hardware/keymaster_defs.h>

#include <keymaster/authorization_set.h>
#include <keymaster/km_openssl/asymmetric_key.h>
#include <keymaster/km_openssl/attestation_record.h>
#include <keymaster/km_openssl/attestation_utils.h>
#include <keymaster/km_openssl/certificate_utils.h>
#include <keymaster/km_openssl/openssl_err.h>
#include <keymaster/km_openssl/openssl_utils.h>

namespace keymaster {

namespace {

CertificateChain make_cert_chain(X509* certificate, CertificateChain chain,
                                 keymaster_error_t* error) {
    keymaster_blob_t blob{};
    *error = encode_certificate(certificate, &blob);
    if (*error != KM_ERROR_OK) return {};

    if (!chain.push_front(blob)) {
        *error = KM_ERROR_MEMORY_ALLOCATION_FAILED;
        return {};
    }
    return chain;
}

keymaster_error_t build_attestation_extension(const AuthorizationSet& attest_params,
                                              const AuthorizationSet& tee_enforced,
                                              const AuthorizationSet& sw_enforced,
                                              const AttestationContext& context,
                                              X509_EXTENSION_Ptr* extension) {
    ASN1_OBJECT_Ptr oid(
        OBJ_txt2obj(kAsn1TokenOid, 1 /* accept numerical dotted string form only */));
    if (!oid.get()) return TranslateLastOpenSslError();

    UniquePtr<uint8_t[]> attest_bytes;
    size_t attest_bytes_len;
    keymaster_error_t error = build_attestation_record(attest_params, sw_enforced, tee_enforced,
                                                       context, &attest_bytes, &attest_bytes_len);
    if (error != KM_ERROR_OK) return error;

    ASN1_OCTET_STRING_Ptr attest_str(ASN1_OCTET_STRING_new());
    if (!attest_str.get() ||
        !ASN1_OCTET_STRING_set(attest_str.get(), attest_bytes.get(), attest_bytes_len)) {
        return TranslateLastOpenSslError();
    }

    extension->reset(
        X509_EXTENSION_create_by_OBJ(nullptr, oid.get(), 0 /* not critical */, attest_str.get()));
    if (!extension->get()) {
        return TranslateLastOpenSslError();
    }

    return KM_ERROR_OK;
}

#ifdef KEYMINT_EAT_EXTENSION_SUPPORT
keymaster_error_t build_eat_extension(const AuthorizationSet& attest_params,
                                      const AuthorizationSet& tee_enforced,
                                      const AuthorizationSet& sw_enforced,
                                      const AttestationContext& context,  //
                                      X509_EXTENSION_Ptr* extension) {
    ASN1_OBJECT_Ptr oid(
        OBJ_txt2obj(kEatTokenOid, 1 /* accept numerical dotted string form only */));
    if (!oid.get()) {
        return TranslateLastOpenSslError();
    }

    std::vector<uint8_t> eat_bytes;
    keymaster_error_t error =
        build_eat_record(attest_params, sw_enforced, tee_enforced, context, &eat_bytes);
    if (error != KM_ERROR_OK) return error;

    ASN1_OCTET_STRING_Ptr eat_str(ASN1_OCTET_STRING_new());
    if (!eat_str.get() ||
        !ASN1_OCTET_STRING_set(eat_str.get(), eat_bytes.data(), eat_bytes.size())) {
        return TranslateLastOpenSslError();
    }

    extension->reset(
        X509_EXTENSION_create_by_OBJ(nullptr, oid.get(), 0 /* not critical */, eat_str.get()));
    if (!extension->get()) {
        return TranslateLastOpenSslError();
    }

    return KM_ERROR_OK;
}
#endif

keymaster_error_t add_attestation_extension(const AuthorizationSet& attest_params,
                                            const AuthorizationSet& tee_enforced,
                                            const AuthorizationSet& sw_enforced,
                                            const AttestationContext& context,  //
                                            X509* certificate) {
    X509_EXTENSION_Ptr attest_extension;
    if (auto error = build_attestation_extension(attest_params, tee_enforced, sw_enforced, context,
                                                 &attest_extension)) {
        return error;
    }

    if (!X509_add_ext(certificate, attest_extension.get() /* Don't release; copied */,
                      -1 /* insert at end */)) {
        return TranslateLastOpenSslError();
    }

    return KM_ERROR_OK;
}

keymaster_error_t make_attestation_cert(const EVP_PKEY* evp_pkey, const X509_NAME* issuer,
                                        const CertificateCallerParams& cert_params,
                                        const AuthorizationSet& attest_params,
                                        const AuthorizationSet& tee_enforced,
                                        const AuthorizationSet& sw_enforced,
                                        const AttestationContext& context, X509_Ptr* cert_out) {

    // First make the basic certificate with usage extension.
    X509_Ptr certificate;
    if (auto error = make_cert(evp_pkey, issuer, cert_params, &certificate)) {
        return error;
    }

    // Add attestation extension.
    if (auto error = add_attestation_extension(attest_params, tee_enforced, sw_enforced, context,
                                               certificate.get())) {
        return error;
    }

    *cert_out = std::move(certificate);
    return KM_ERROR_OK;
}

X509_NAME_Ptr get_issuer_subject(const AttestKeyInfo& attest_key, keymaster_error_t* error) {
    // Use subject from attest_key.
    const uint8_t* p = attest_key.issuer_subject->data;
    if (!p || !attest_key.issuer_subject->data_length) {
        *error = KM_ERROR_MISSING_ISSUER_SUBJECT;
        return {};
    }
    X509_NAME_Ptr retval(d2i_X509_NAME(nullptr /* Allocate X509_NAME */, &p,
                                       attest_key.issuer_subject->data_length));
    if (!retval) *error = KM_ERROR_INVALID_ISSUER_SUBJECT;
    return retval;
}

X509_NAME_Ptr get_issuer_subject(const keymaster_blob_t& signing_cert_der,
                                 keymaster_error_t* error) {
    const uint8_t* p = signing_cert_der.data;
    if (!p) {
        *error = KM_ERROR_UNEXPECTED_NULL_POINTER;
        return {};
    }
    X509_Ptr signing_cert(d2i_X509(nullptr /* Allocate X509 */, &p, signing_cert_der.data_length));
    if (!signing_cert) {
        *error = TranslateLastOpenSslError();
        return {};
    }

    X509_NAME* issuer_subject = X509_get_subject_name(signing_cert.get());
    if (!issuer_subject) {
        *error = TranslateLastOpenSslError();
        return {};
    }

    X509_NAME_Ptr retval(X509_NAME_dup(issuer_subject));
    if (!retval) *error = TranslateLastOpenSslError();

    return retval;
}

// Return subject from attest_key, if non-null, otherwise extract from cert_chain.
X509_NAME_Ptr get_issuer_subject(const AttestKeyInfo& attest_key,
                                 const CertificateChain& cert_chain, keymaster_error_t* error) {
    if (attest_key) {
        return get_issuer_subject(attest_key, error);
    }

    // Need to extract issuer from cert chain.  First cert in the chain is the signing key cert.
    if (cert_chain.entry_count >= 1) return get_issuer_subject(cert_chain.entries[0], error);

    *error = KM_ERROR_UNKNOWN_ERROR;
    return {};
}

keymaster_error_t check_attest_key_auths(const Key& key) {
    auto auths = key.authorizations();

    if (!auths.Contains(TAG_ALGORITHM, KM_ALGORITHM_RSA) &&
        !auths.Contains(TAG_ALGORITHM, KM_ALGORITHM_EC)) {
        return KM_ERROR_INCOMPATIBLE_ALGORITHM;
    }
    if (!auths.Contains(TAG_PURPOSE, KM_PURPOSE_ATTEST_KEY)) {
        return KM_ERROR_INCOMPATIBLE_PURPOSE;
    }
    return KM_ERROR_OK;
}

EVP_PKEY_Ptr get_attestation_key(keymaster_algorithm_t algorithm, const AttestationContext& context,
                                 keymaster_error_t* error) {
    KeymasterKeyBlob signing_key_blob = context.GetAttestationKey(algorithm, error);
    if (*error != KM_ERROR_OK) return {};

    const uint8_t* p = signing_key_blob.key_material;
    EVP_PKEY_Ptr retval(
        d2i_AutoPrivateKey(nullptr /* Allocate key */, &p, signing_key_blob.key_material_size));
    if (!retval) *error = TranslateLastOpenSslError();
    return retval;
}

}  // namespace

AttestKeyInfo::AttestKeyInfo(const UniquePtr<Key>& key, const KeymasterBlob* issuer_subject_,
                             keymaster_error_t* error)
    : issuer_subject(issuer_subject_) {
    if (!error) return;

    if (!key) {
        // No key... so this is just an empty AttestKeyInfo.
        issuer_subject = nullptr;
        return;
    }

    if (!issuer_subject) {
        *error = KM_ERROR_UNEXPECTED_NULL_POINTER;
        return;
    }

    *error = check_attest_key_auths(*key);
    if (*error != KM_ERROR_OK) return;

    signing_key = static_cast<const AsymmetricKey&>(*key).InternalToEvp();
    if (signing_key.get() == nullptr) {
        *error = KM_ERROR_UNKNOWN_ERROR;
    }
}

CertificateChain generate_attestation(const AsymmetricKey& key,
                                      const AuthorizationSet& attest_params,
                                      AttestKeyInfo attest_key,
                                      const AttestationContext& context,  //
                                      keymaster_error_t* error) {
    EVP_PKEY_Ptr pkey(key.InternalToEvp());
    if (pkey.get() == nullptr) {
        *error = TranslateLastOpenSslError();
        return {};
    }

    return generate_attestation(pkey.get(), key.sw_enforced(), key.hw_enforced(), attest_params,
                                std::move(attest_key), context, error);
}

CertificateChain generate_attestation(const EVP_PKEY* evp_key,              //
                                      const AuthorizationSet& sw_enforced,  //
                                      const AuthorizationSet& tee_enforced,
                                      const AuthorizationSet& attest_params,
                                      AttestKeyInfo attest_key,
                                      const AttestationContext& context,  //
                                      keymaster_error_t* error) {
    if (!error) return {};

    CertificateCallerParams cert_params{};
    *error = get_certificate_params(attest_params, &cert_params, context.GetKmVersion());
    if (*error != KM_ERROR_OK) return {};

    AuthProxy proxy(tee_enforced, sw_enforced);
    cert_params.is_signing_key = (proxy.Contains(TAG_PURPOSE, KM_PURPOSE_SIGN) ||
                                  proxy.Contains(TAG_PURPOSE, KM_PURPOSE_ATTEST_KEY));
    cert_params.is_encryption_key = proxy.Contains(TAG_PURPOSE, KM_PURPOSE_DECRYPT);
    cert_params.is_agreement_key = proxy.Contains(TAG_PURPOSE, KM_PURPOSE_AGREE_KEY);

    keymaster_algorithm_t algorithm;
    if (!proxy.GetTagValue(TAG_ALGORITHM, &algorithm)) {
        *error = KM_ERROR_UNSUPPORTED_PURPOSE;
        return {};
    }

    CertificateChain cert_chain =
        attest_key ? CertificateChain() : context.GetAttestationChain(algorithm, error);
    if (*error != KM_ERROR_OK) return {};

    X509_NAME_Ptr issuer_subject = get_issuer_subject(attest_key, cert_chain, error);
    if (*error != KM_ERROR_OK) return {};

    X509_Ptr certificate;
    *error = make_attestation_cert(evp_key, issuer_subject.get(), cert_params, attest_params,
                                   tee_enforced, sw_enforced, context, &certificate);
    if (*error != KM_ERROR_OK) return {};

    EVP_PKEY_Ptr signing_key;
    const EVP_PKEY* signing_key_ptr = attest_key.signing_key.get();
    if (!signing_key_ptr) {
        signing_key = get_attestation_key(algorithm, context, error);
        if (*error != KM_ERROR_OK) return {};
        signing_key_ptr = signing_key.get();
    }

    *error = sign_cert(certificate.get(), signing_key_ptr);
    if (*error != KM_ERROR_OK) return {};

    return make_cert_chain(certificate.get(), std::move(cert_chain), error);
}

}  // namespace keymaster
