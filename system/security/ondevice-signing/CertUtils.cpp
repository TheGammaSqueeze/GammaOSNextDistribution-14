/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "CertUtils.h"

#include <android-base/logging.h>
#include <android-base/result.h>

#include <openssl/bn.h>
#include <openssl/crypto.h>
#include <openssl/rsa.h>
#include <openssl/x509.h>
#include <openssl/x509v3.h>

#include <vector>

#include "KeyConstants.h"

// Common properties for all of our certificates.
constexpr int kCertLifetimeSeconds = 10 * 365 * 24 * 60 * 60;
const char* const kIssuerCountry = "US";
const char* const kIssuerOrg = "Android";

using android::base::ErrnoError;
using android::base::Error;
using android::base::Result;

static Result<bssl::UniquePtr<X509>> loadX509(const std::string& path) {
    X509* rawCert;
    auto f = fopen(path.c_str(), "re");
    if (f == nullptr) {
        return Error() << "Failed to open " << path;
    }
    if (!d2i_X509_fp(f, &rawCert)) {
        fclose(f);
        return Error() << "Unable to decode x509 cert at " << path;
    }
    bssl::UniquePtr<X509> cert(rawCert);

    fclose(f);
    return cert;
}

static bool add_ext(X509V3_CTX* context, X509* cert, int nid, const char* value) {
    bssl::UniquePtr<X509_EXTENSION> ex(X509V3_EXT_nconf_nid(nullptr, context, nid, value));
    if (!ex) {
        return false;
    }

    X509_add_ext(cert, ex.get(), -1);
    return true;
}

static void addNameEntry(X509_NAME* name, const char* field, const char* value) {
    X509_NAME_add_entry_by_txt(name, field, MBSTRING_ASC,
                               reinterpret_cast<const unsigned char*>(value), -1, -1, 0);
}

static Result<bssl::UniquePtr<RSA>> getRsaFromModulus(const std::vector<uint8_t>& publicKey) {
    bssl::UniquePtr<BIGNUM> n(BN_new());
    bssl::UniquePtr<BIGNUM> e(BN_new());
    bssl::UniquePtr<RSA> rsaPubkey(RSA_new());
    if (!n || !e || !rsaPubkey || !BN_bin2bn(publicKey.data(), publicKey.size(), n.get()) ||
        !BN_set_word(e.get(), kRsaKeyExponent) ||
        !RSA_set0_key(rsaPubkey.get(), n.get(), e.get(), /*d=*/nullptr)) {
        return Error() << "Failed to create RSA key";
    }
    // RSA_set0_key takes ownership of |n| and |e| on success.
    (void)n.release();
    (void)e.release();

    return rsaPubkey;
}

static Result<bssl::UniquePtr<EVP_PKEY>> modulusToRsaPkey(const std::vector<uint8_t>& publicKey) {
    // "publicKey" corresponds to the raw public key bytes - need to create
    // a new RSA key with the correct exponent.
    auto rsaPubkey = getRsaFromModulus(publicKey);
    if (!rsaPubkey.ok()) {
        return rsaPubkey.error();
    }

    bssl::UniquePtr<EVP_PKEY> public_key(EVP_PKEY_new());
    if (!EVP_PKEY_assign_RSA(public_key.get(), rsaPubkey->release())) {
        return Error() << "Failed to assign key";
    }
    return public_key;
}

Result<void> verifySignature(const std::string& message, const std::string& signature,
                             const std::vector<uint8_t>& publicKey) {
    auto rsaKey = getRsaFromModulus(publicKey);
    if (!rsaKey.ok()) {
        return rsaKey.error();
    }
    uint8_t hashBuf[SHA256_DIGEST_LENGTH];
    SHA256(const_cast<uint8_t*>(reinterpret_cast<const uint8_t*>(message.c_str())),
           message.length(), hashBuf);

    bool success = RSA_verify(NID_sha256, hashBuf, sizeof(hashBuf),
                              (const uint8_t*)signature.c_str(), signature.length(), rsaKey->get());

    if (!success) {
        return Error() << "Failed to verify signature";
    }
    return {};
}

Result<void> createSelfSignedCertificate(
    const std::vector<uint8_t>& publicKey,
    const std::function<Result<std::string>(const std::string&)>& signFunction,
    const std::string& path) {
    auto rsa_pkey = modulusToRsaPkey(publicKey);
    if (!rsa_pkey.ok()) {
        return rsa_pkey.error();
    }
    bssl::UniquePtr<X509> x509(X509_new());
    if (!x509) {
        return Error() << "Unable to allocate x509 container";
    }
    X509_set_version(x509.get(), 2);
    X509_gmtime_adj(X509_get_notBefore(x509.get()), 0);
    X509_gmtime_adj(X509_get_notAfter(x509.get()), kCertLifetimeSeconds);
    ASN1_INTEGER_set(X509_get_serialNumber(x509.get()), kRootSubject.serialNumber);

    bssl::UniquePtr<X509_ALGOR> algor(X509_ALGOR_new());
    if (!algor ||
        !X509_ALGOR_set0(algor.get(), OBJ_nid2obj(NID_sha256WithRSAEncryption), V_ASN1_NULL,
                         NULL) ||
        !X509_set1_signature_algo(x509.get(), algor.get())) {
        return Error() << "Unable to set x509 signature algorithm";
    }

    if (!X509_set_pubkey(x509.get(), rsa_pkey.value().get())) {
        return Error() << "Unable to set x509 public key";
    }

    X509_NAME* subjectName = X509_get_subject_name(x509.get());
    if (!subjectName) {
        return Error() << "Unable to get x509 subject name";
    }
    addNameEntry(subjectName, "C", kIssuerCountry);
    addNameEntry(subjectName, "O", kIssuerOrg);
    addNameEntry(subjectName, "CN", kRootSubject.commonName);
    if (!X509_set_issuer_name(x509.get(), subjectName)) {
        return Error() << "Unable to set x509 issuer name";
    }

    X509V3_CTX context = {};
    X509V3_set_ctx(&context, x509.get(), x509.get(), nullptr, nullptr, 0);
    add_ext(&context, x509.get(), NID_basic_constraints, "CA:TRUE");
    add_ext(&context, x509.get(), NID_key_usage, "critical,keyCertSign,cRLSign,digitalSignature");
    add_ext(&context, x509.get(), NID_subject_key_identifier, "hash");
    add_ext(&context, x509.get(), NID_authority_key_identifier, "keyid:always");

    // Get the data to be signed
    unsigned char* to_be_signed_buf(nullptr);
    size_t to_be_signed_length = i2d_re_X509_tbs(x509.get(), &to_be_signed_buf);

    auto signed_data = signFunction(
        std::string(reinterpret_cast<const char*>(to_be_signed_buf), to_be_signed_length));
    if (!signed_data.ok()) {
        return signed_data.error();
    }

    if (!X509_set1_signature_value(x509.get(),
                                   reinterpret_cast<const uint8_t*>(signed_data->data()),
                                   signed_data->size())) {
        return Error() << "Unable to set x509 signature";
    }

    auto f = fopen(path.c_str(), "wbe");
    if (f == nullptr) {
        return ErrnoError() << "Failed to open " << path;
    }
    i2d_X509_fp(f, x509.get());
    if (fclose(f) != 0) {
        return ErrnoError() << "Failed to close " << path;
    }

    return {};
}

static Result<std::vector<uint8_t>> extractPublicKey(EVP_PKEY* pkey) {
    if (pkey == nullptr) {
        return Error() << "Failed to extract public key from x509 cert";
    }

    if (EVP_PKEY_id(pkey) != EVP_PKEY_RSA) {
        return Error() << "The public key is not an RSA key";
    }

    RSA* rsa = EVP_PKEY_get0_RSA(pkey);
    auto num_bytes = BN_num_bytes(RSA_get0_n(rsa));
    std::vector<uint8_t> pubKey(num_bytes);
    int res = BN_bn2bin(RSA_get0_n(rsa), pubKey.data());

    if (!res) {
        return Error() << "Failed to convert public key to bytes";
    }

    return pubKey;
}

Result<std::vector<uint8_t>> extractPublicKeyFromX509(const std::vector<uint8_t>& derCert) {
    auto derCertBytes = derCert.data();
    bssl::UniquePtr<X509> decoded_cert(d2i_X509(nullptr, &derCertBytes, derCert.size()));
    if (decoded_cert.get() == nullptr) {
        return Error() << "Failed to decode X509 certificate.";
    }
    bssl::UniquePtr<EVP_PKEY> decoded_pkey(X509_get_pubkey(decoded_cert.get()));

    return extractPublicKey(decoded_pkey.get());
}

Result<std::vector<uint8_t>> extractPublicKeyFromX509(const std::string& path) {
    auto cert = loadX509(path);
    if (!cert.ok()) {
        return cert.error();
    }
    return extractPublicKey(X509_get_pubkey(cert.value().get()));
}
