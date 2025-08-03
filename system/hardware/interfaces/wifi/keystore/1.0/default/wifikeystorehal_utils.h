#pragma once

#include <android-base/logging.h>
#include <openssl/base.h>
#include <openssl/bio.h>
#include <openssl/pem.h>
#include <openssl/x509.h>
#include <vector>

#define AT __func__ << ":" << __LINE__ << " "

namespace {
// Helper method to extract public key from the certificate.
std::vector<uint8_t> extractPubKey(const std::vector<uint8_t>& cert_bytes) {
    const uint8_t* p = cert_bytes.data();
    bssl::UniquePtr<X509> decoded_cert(d2i_X509(nullptr, &p, cert_bytes.size()));
    if (!decoded_cert) {
        LOG(INFO) << AT << "Could not decode the cert, trying decoding as PEM";
        bssl::UniquePtr<BIO> cert_bio(BIO_new_mem_buf(cert_bytes.data(), cert_bytes.size()));
        if (!cert_bio) {
            LOG(ERROR) << AT << "Failed to create BIO";
            return {};
        }
        decoded_cert =
            bssl::UniquePtr<X509>(PEM_read_bio_X509(cert_bio.get(), nullptr, nullptr, nullptr));
    }
    if (!decoded_cert) {
        LOG(ERROR) << AT << "Could not decode the cert.";
        return {};
    }
    bssl::UniquePtr<EVP_PKEY> pub_key(X509_get_pubkey(decoded_cert.get()));
    if (!pub_key) {
        LOG(ERROR) << AT << "Could not extract public key.";
        return {};
    }
    bssl::UniquePtr<BIO> pub_key_bio(BIO_new(BIO_s_mem()));
    if (!pub_key_bio || i2d_PUBKEY_bio(pub_key_bio.get(), pub_key.get()) <= 0) {
        LOG(ERROR) << AT << "Could not serialize public key.";
        return {};
    }
    const uint8_t* pub_key_bytes;
    size_t pub_key_len;
    if (!BIO_mem_contents(pub_key_bio.get(), &pub_key_bytes, &pub_key_len)) {
        LOG(ERROR) << AT << "Could not get bytes from BIO.";
        return {};
    }

    return {pub_key_bytes, pub_key_bytes + pub_key_len};
}

}  // namespace