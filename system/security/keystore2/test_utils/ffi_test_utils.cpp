#include "ffi_test_utils.hpp"

#include <iostream>
#include <vector>

#include <android-base/logging.h>
#include <keymaster/km_openssl/attestation_record.h>
#include <keymaster/km_openssl/openssl_err.h>
#include <keymaster/km_openssl/openssl_utils.h>
#include <keymint_support/attestation_record.h>
#include <keymint_support/keymint_utils.h>
#include <openssl/mem.h>

using keymaster::ASN1_OBJECT_Ptr;
using keymaster::EVP_PKEY_Ptr;
using keymaster::X509_Ptr;
using std::endl;
using std::string;
using std::vector;

#define TAG_SEQUENCE 0x30
#define LENGTH_MASK 0x80
#define LENGTH_VALUE_MASK 0x7F

/* EVP_PKEY_from_keystore is from system/security/keystore-engine. */
extern "C" EVP_PKEY* EVP_PKEY_from_keystore(const char* key_id);

typedef std::vector<uint8_t> certificate_t;

/**
 * ASN.1 structure for `KeyDescription` Schema.
 * See `IKeyMintDevice.aidl` for documentation of the `KeyDescription` schema.
 *    KeyDescription ::= SEQUENCE(
 *        keyFormat INTEGER,                   # Values from KeyFormat enum.
 *        keyParams AuthorizationList,
 *    )
 */
typedef struct key_description {
    ASN1_INTEGER* key_format;
    keymaster::KM_AUTH_LIST* key_params;
} TEST_KEY_DESCRIPTION;

ASN1_SEQUENCE(TEST_KEY_DESCRIPTION) = {
    ASN1_SIMPLE(TEST_KEY_DESCRIPTION, key_format, ASN1_INTEGER),
    ASN1_SIMPLE(TEST_KEY_DESCRIPTION, key_params, keymaster::KM_AUTH_LIST),
} ASN1_SEQUENCE_END(TEST_KEY_DESCRIPTION);
DECLARE_ASN1_FUNCTIONS(TEST_KEY_DESCRIPTION);

/**
 * ASN.1 structure for `SecureKeyWrapper` Schema.
 * See `IKeyMintDevice.aidl` for documentation of the `SecureKeyWrapper` schema.
 *    SecureKeyWrapper ::= SEQUENCE(
 *        version INTEGER,                     # Contains value 0
 *        encryptedTransportKey OCTET_STRING,
 *        initializationVector OCTET_STRING,
 *        keyDescription KeyDescription,
 *        encryptedKey OCTET_STRING,
 *        tag OCTET_STRING
 *    )
 */
typedef struct secure_key_wrapper {
    ASN1_INTEGER* version;
    ASN1_OCTET_STRING* encrypted_transport_key;
    ASN1_OCTET_STRING* initialization_vector;
    TEST_KEY_DESCRIPTION* key_desc;
    ASN1_OCTET_STRING* encrypted_key;
    ASN1_OCTET_STRING* tag;
} TEST_SECURE_KEY_WRAPPER;

ASN1_SEQUENCE(TEST_SECURE_KEY_WRAPPER) = {
    ASN1_SIMPLE(TEST_SECURE_KEY_WRAPPER, version, ASN1_INTEGER),
    ASN1_SIMPLE(TEST_SECURE_KEY_WRAPPER, encrypted_transport_key, ASN1_OCTET_STRING),
    ASN1_SIMPLE(TEST_SECURE_KEY_WRAPPER, initialization_vector, ASN1_OCTET_STRING),
    ASN1_SIMPLE(TEST_SECURE_KEY_WRAPPER, key_desc, TEST_KEY_DESCRIPTION),
    ASN1_SIMPLE(TEST_SECURE_KEY_WRAPPER, encrypted_key, ASN1_OCTET_STRING),
    ASN1_SIMPLE(TEST_SECURE_KEY_WRAPPER, tag, ASN1_OCTET_STRING),
} ASN1_SEQUENCE_END(TEST_SECURE_KEY_WRAPPER);
DECLARE_ASN1_FUNCTIONS(TEST_SECURE_KEY_WRAPPER);

IMPLEMENT_ASN1_FUNCTIONS(TEST_SECURE_KEY_WRAPPER);
IMPLEMENT_ASN1_FUNCTIONS(TEST_KEY_DESCRIPTION);

struct TEST_KEY_DESCRIPTION_Delete {
    void operator()(TEST_KEY_DESCRIPTION* p) { TEST_KEY_DESCRIPTION_free(p); }
};
struct TEST_SECURE_KEY_WRAPPER_Delete {
    void operator()(TEST_SECURE_KEY_WRAPPER* p) { TEST_SECURE_KEY_WRAPPER_free(p); }
};

const std::string keystore2_grant_id_prefix("ks2_keystore-engine_grant_id:");

string bin2hex(const vector<uint8_t>& data) {
    string retval;
    char nibble2hex[16] = {'0', '1', '2', '3', '4', '5', '6', '7',
                           '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    retval.reserve(data.size() * 2 + 1);
    for (uint8_t byte : data) {
        retval.push_back(nibble2hex[0x0F & (byte >> 4)]);
        retval.push_back(nibble2hex[0x0F & byte]);
    }
    return retval;
}

string x509NameToStr(X509_NAME* name) {
    char* s = X509_NAME_oneline(name, nullptr, 0);
    string retval(s);
    OPENSSL_free(s);
    return retval;
}

X509_Ptr parseCertBlob(const vector<uint8_t>& blob) {
    const uint8_t* p = blob.data();
    return X509_Ptr(d2i_X509(nullptr /* allocate new */, &p, blob.size()));
}

// Extract attestation record from cert. Returned object is still part of cert; don't free it
// separately.
ASN1_OCTET_STRING* getAttestationRecord(X509* certificate) {
    ASN1_OBJECT_Ptr oid(OBJ_txt2obj(aidl::android::hardware::security::keymint::kAttestionRecordOid,
                                    1 /* dotted string format */));
    if (!oid.get()) return nullptr;

    int location = X509_get_ext_by_OBJ(certificate, oid.get(), -1 /* search from beginning */);
    if (location == -1) return nullptr;

    X509_EXTENSION* attest_rec_ext = X509_get_ext(certificate, location);
    if (!attest_rec_ext) return nullptr;

    ASN1_OCTET_STRING* attest_rec = X509_EXTENSION_get_data(attest_rec_ext);
    return attest_rec;
}

bool ChainSignaturesAreValid(const vector<certificate_t>& chain, bool strict_issuer_check) {
    std::stringstream cert_data;

    for (size_t i = 0; i < chain.size(); ++i) {
        cert_data << bin2hex(chain[i]) << std::endl;

        X509_Ptr key_cert(parseCertBlob(chain[i]));
        X509_Ptr signing_cert;
        if (i < chain.size() - 1) {
            signing_cert = parseCertBlob(chain[i + 1]);
        } else {
            signing_cert = parseCertBlob(chain[i]);
        }
        if (!key_cert.get() || !signing_cert.get()) {
            LOG(ERROR) << cert_data.str();
            return false;
        }

        EVP_PKEY_Ptr signing_pubkey(X509_get_pubkey(signing_cert.get()));
        if (!signing_pubkey.get()) {
            LOG(ERROR) << cert_data.str();
            return false;
        }

        if (!X509_verify(key_cert.get(), signing_pubkey.get())) {
            // Handles the case of device-unique attestation chain which is not expected to be
            // self-signed - b/191361618
            // For device-unique attestation chain `strict_issuer_check` is not set, so ignore the
            // root certificate signature verification result and in all other cases return the
            // error.
            bool is_root_cert = (i == chain.size() - 1);
            if (strict_issuer_check || !is_root_cert) {
                LOG(ERROR) << "Verification of certificate " << i << " failed "
                           << "OpenSSL error string: " << ERR_error_string(ERR_get_error(), NULL)
                           << '\n'
                           << cert_data.str();
                return false;
            }
        }

        string cert_issuer = x509NameToStr(X509_get_issuer_name(key_cert.get()));
        string signer_subj = x509NameToStr(X509_get_subject_name(signing_cert.get()));
        if (cert_issuer != signer_subj && strict_issuer_check) {
            LOG(ERROR) << "Cert " << i << " has wrong issuer.\n"
                       << " Signer subject is " << signer_subj << " Issuer subject is "
                       << cert_issuer << endl
                       << cert_data.str();
        }
    }

    // Dump cert data.
    LOG(ERROR) << cert_data.str();
    return true;
}

/* This function extracts a certificate from the certs_chain_buffer at the given
 * offset. Each DER encoded certificate starts with TAG_SEQUENCE followed by the
 * total length of the certificate. The length of the certificate is determined
 * as per ASN.1 encoding rules for the length octets.
 *
 * @param certs_chain_buffer: buffer containing DER encoded X.509 certificates
 *                            arranged sequentially.
 * @data_size: Length of the DER encoded X.509 certificates buffer.
 * @index: DER encoded X.509 certificates buffer offset.
 * @cert: Encoded certificate to be extracted from buffer as outcome.
 * @return: true on success, otherwise false.
 */
bool extractCertFromCertChainBuffer(uint8_t* certs_chain_buffer, int certs_chain_buffer_size,
                                    int& index, certificate_t& cert) {
    if (index >= certs_chain_buffer_size) {
        return false;
    }

    uint32_t length = 0;
    std::vector<uint8_t> cert_bytes;
    if (certs_chain_buffer[index] == TAG_SEQUENCE) {
        // Short form. One octet. Bit 8 has value "0" and bits 7-1 give the length.
        if (0 == (certs_chain_buffer[index + 1] & LENGTH_MASK)) {
            length = (uint32_t)certs_chain_buffer[index];
            // Add SEQ and Length fields
            length += 2;
        } else {
            // Long form. Two to 127 octets. Bit 8 of first octet has value "1" and
            // bits 7-1 give the number of additional length octets. Second and following
            // octets give the actual length.
            int additionalBytes = certs_chain_buffer[index + 1] & LENGTH_VALUE_MASK;
            if (additionalBytes == 0x01) {
                length = certs_chain_buffer[index + 2];
                // Add SEQ and Length fields
                length += 3;
            } else if (additionalBytes == 0x02) {
                length = (certs_chain_buffer[index + 2] << 8 | certs_chain_buffer[index + 3]);
                // Add SEQ and Length fields
                length += 4;
            } else if (additionalBytes == 0x04) {
                length = certs_chain_buffer[index + 2] << 24;
                length |= certs_chain_buffer[index + 3] << 16;
                length |= certs_chain_buffer[index + 4] << 8;
                length |= certs_chain_buffer[index + 5];
                // Add SEQ and Length fields
                length += 6;
            } else {
                // Length is larger than uint32_t max limit.
                return false;
            }
        }
        cert_bytes.insert(cert_bytes.end(), (certs_chain_buffer + index),
                          (certs_chain_buffer + index + length));
        index += length;

        for (int i = 0; i < cert_bytes.size(); i++) {
            cert = std::move(cert_bytes);
        }
    } else {
        // SEQUENCE TAG MISSING.
        return false;
    }

    return true;
}

bool getCertificateChain(rust::Vec<rust::u8>& chainBuffer, std::vector<certificate_t>& certChain) {
    uint8_t* data = chainBuffer.data();
    int index = 0;
    int data_size = chainBuffer.size();

    while (index < data_size) {
        certificate_t cert;
        if (!extractCertFromCertChainBuffer(data, data_size, index, cert)) {
            return false;
        }
        certChain.push_back(std::move(cert));
    }
    return true;
}

bool validateCertChain(rust::Vec<rust::u8> cert_buf, uint32_t cert_len, bool strict_issuer_check) {
    std::vector<certificate_t> cert_chain = std::vector<certificate_t>();
    if (cert_len <= 0) {
        return false;
    }
    if (!getCertificateChain(cert_buf, cert_chain)) {
        return false;
    }

    std::stringstream cert_data;
    for (int i = 0; i < cert_chain.size(); i++) {
        cert_data << bin2hex(cert_chain[i]) << std::endl;
    }
    LOG(INFO) << cert_data.str() << "\n";

    return ChainSignaturesAreValid(cert_chain, strict_issuer_check);
}

/**
 * Below mentioned key parameters are used to create authorization list of
 * secure key.
 *    Algorithm: AES-256
 *    Padding: PKCS7
 *    Blockmode: ECB
 *    Purpose: Encrypt, Decrypt
 */
keymaster::AuthorizationSet build_wrapped_key_auth_list() {
    return keymaster::AuthorizationSet(keymaster::AuthorizationSetBuilder()
                                           .AesEncryptionKey(256)
                                           .Authorization(keymaster::TAG_BLOCK_MODE, KM_MODE_ECB)
                                           .Authorization(keymaster::TAG_PADDING, KM_PAD_PKCS7)
                                           .Authorization(keymaster::TAG_NO_AUTH_REQUIRED));
}

/**
 * Creates ASN.1 DER-encoded data corresponding to `KeyDescription` schema as
 * AAD. See `IKeyMintDevice.aidl` for documentation of the `KeyDescription` schema.
 */
CxxResult buildAsn1DerEncodedWrappedKeyDescription() {
    CxxResult cxx_result{};
    keymaster_error_t error;
    cxx_result.error = KM_ERROR_OK;

    keymaster::UniquePtr<TEST_KEY_DESCRIPTION, TEST_KEY_DESCRIPTION_Delete> key_description(
        TEST_KEY_DESCRIPTION_new());
    if (!key_description.get()) {
        cxx_result.error = KM_ERROR_MEMORY_ALLOCATION_FAILED;
        return cxx_result;
    }

    // Fill secure key authorizations.
    keymaster::AuthorizationSet auth_list = build_wrapped_key_auth_list();
    error = build_auth_list(auth_list, key_description->key_params);
    if (error != KM_ERROR_OK) {
        cxx_result.error = error;
        return cxx_result;
    }

    // Fill secure key format.
    if (!ASN1_INTEGER_set(key_description->key_format, KM_KEY_FORMAT_RAW)) {
        cxx_result.error = keymaster::TranslateLastOpenSslError();
        return cxx_result;
    }

    // Perform ASN.1 DER encoding of KeyDescription.
    int asn1_data_len = i2d_TEST_KEY_DESCRIPTION(key_description.get(), nullptr);
    if (asn1_data_len < 0) {
        cxx_result.error = keymaster::TranslateLastOpenSslError();
        return cxx_result;
    }
    std::vector<uint8_t> asn1_data(asn1_data_len, 0);

    if (!asn1_data.data()) {
        cxx_result.error = KM_ERROR_MEMORY_ALLOCATION_FAILED;
        return cxx_result;
    }

    uint8_t* p = asn1_data.data();
    asn1_data_len = i2d_TEST_KEY_DESCRIPTION(key_description.get(), &p);
    if (asn1_data_len < 0) {
        cxx_result.error = keymaster::TranslateLastOpenSslError();
        return cxx_result;
    }

    std::move(asn1_data.begin(), asn1_data.end(), std::back_inserter(cxx_result.data));

    return cxx_result;
}

/**
 * Creates wrapped key material to import in ASN.1 DER-encoded data corresponding to
 * `SecureKeyWrapper` schema. See `IKeyMintDevice.aidl` for documentation of the `SecureKeyWrapper`
 * schema.
 */
CxxResult createWrappedKey(rust::Vec<rust::u8> encrypted_secure_key,
                           rust::Vec<rust::u8> encrypted_transport_key, rust::Vec<rust::u8> iv,
                           rust::Vec<rust::u8> tag) {
    CxxResult cxx_result{};
    keymaster_error_t error;
    cxx_result.error = false;

    uint8_t* enc_secure_key_data = encrypted_secure_key.data();
    int enc_secure_key_size = encrypted_secure_key.size();

    uint8_t* iv_data = iv.data();
    int iv_size = iv.size();

    uint8_t* tag_data = tag.data();
    int tag_size = tag.size();

    uint8_t* enc_transport_key_data = encrypted_transport_key.data();
    int enc_transport_key_size = encrypted_transport_key.size();

    keymaster::UniquePtr<TEST_SECURE_KEY_WRAPPER, TEST_SECURE_KEY_WRAPPER_Delete> sec_key_wrapper(
        TEST_SECURE_KEY_WRAPPER_new());
    if (!sec_key_wrapper.get()) {
        LOG(ERROR) << "createWrappedKey - Failed to allocate a memory";
        cxx_result.error = true;
        return cxx_result;
    }

    // Fill version = 0
    if (!ASN1_INTEGER_set(sec_key_wrapper->version, 0)) {
        LOG(ERROR) << "createWrappedKey - Error while filling version: "
                   << keymaster::TranslateLastOpenSslError();
        cxx_result.error = true;
        return cxx_result;
    }

    // Fill encrypted transport key.
    if (enc_transport_key_size &&
        !ASN1_OCTET_STRING_set(sec_key_wrapper->encrypted_transport_key, enc_transport_key_data,
                               enc_transport_key_size)) {
        LOG(ERROR) << "createWrappedKey - Error while filling encrypted transport key: "
                   << keymaster::TranslateLastOpenSslError();
        cxx_result.error = true;
        return cxx_result;
    }

    // Fill encrypted secure key.
    if (enc_secure_key_size && !ASN1_OCTET_STRING_set(sec_key_wrapper->encrypted_key,
                                                      enc_secure_key_data, enc_secure_key_size)) {
        LOG(ERROR) << "createWrappedKey - Error while filling encrypted secure key: "
                   << keymaster::TranslateLastOpenSslError();
        cxx_result.error = true;
        return cxx_result;
    }

    // Fill secure key authorization list.
    keymaster::AuthorizationSet auth_list = build_wrapped_key_auth_list();
    error = build_auth_list(auth_list, sec_key_wrapper->key_desc->key_params);
    if (error != KM_ERROR_OK) {
        cxx_result.error = true;
        return cxx_result;
    }

    // Fill secure key format.
    if (!ASN1_INTEGER_set(sec_key_wrapper->key_desc->key_format, KM_KEY_FORMAT_RAW)) {
        LOG(ERROR) << "createWrappedKey - Error while filling secure key format: "
                   << keymaster::TranslateLastOpenSslError();
        cxx_result.error = true;
        return cxx_result;
    }

    // Fill initialization vector used for encrypting secure key.
    if (iv_size &&
        !ASN1_OCTET_STRING_set(sec_key_wrapper->initialization_vector, iv_data, iv_size)) {
        LOG(ERROR) << "createWrappedKey - Error while filling IV: "
                   << keymaster::TranslateLastOpenSslError();
        cxx_result.error = true;
        return cxx_result;
    }

    // Fill GCM-tag, extracted during secure key encryption.
    if (tag_size && !ASN1_OCTET_STRING_set(sec_key_wrapper->tag, tag_data, tag_size)) {
        LOG(ERROR) << "createWrappedKey - Error while filling GCM-tag: "
                   << keymaster::TranslateLastOpenSslError();
        cxx_result.error = true;
        return cxx_result;
    }

    // ASN.1 DER-encoding of secure key wrapper.
    int asn1_data_len = i2d_TEST_SECURE_KEY_WRAPPER(sec_key_wrapper.get(), nullptr);
    if (asn1_data_len < 0) {
        LOG(ERROR) << "createWrappedKey - Error while performing DER encode: "
                   << keymaster::TranslateLastOpenSslError();
        cxx_result.error = true;
        return cxx_result;
    }
    std::vector<uint8_t> asn1_data(asn1_data_len, 0);

    if (!asn1_data.data()) {
        LOG(ERROR) << "createWrappedKey - Failed to allocate a memory for asn1_data";
        cxx_result.error = true;
        return cxx_result;
    }

    uint8_t* p = asn1_data.data();
    asn1_data_len = i2d_TEST_SECURE_KEY_WRAPPER(sec_key_wrapper.get(), &p);
    if (asn1_data_len < 0) {
        cxx_result.error = true;
        return cxx_result;
    }

    std::move(asn1_data.begin(), asn1_data.end(), std::back_inserter(cxx_result.data));

    return cxx_result;
}

/**
 * Perform EC/RSA sign operation using `EVP_PKEY`.
 */
bool performSignData(const char* data, size_t data_len, EVP_PKEY* pkey, unsigned char** signature,
                     size_t* signature_len) {
    // Create the signing context
    EVP_MD_CTX* md_ctx = EVP_MD_CTX_new();
    if (md_ctx == NULL) {
        LOG(ERROR) << "Failed to create signing context";
        return false;
    }

    // Initialize the signing operation
    if (EVP_DigestSignInit(md_ctx, NULL, EVP_sha256(), NULL, pkey) != 1) {
        LOG(ERROR) << "Failed to initialize signing operation";
        EVP_MD_CTX_free(md_ctx);
        return false;
    }

    // Sign the data
    if (EVP_DigestSignUpdate(md_ctx, data, data_len) != 1) {
        LOG(ERROR) << "Failed to sign data";
        EVP_MD_CTX_free(md_ctx);
        return false;
    }

    // Determine the length of the signature
    if (EVP_DigestSignFinal(md_ctx, NULL, signature_len) != 1) {
        LOG(ERROR) << "Failed to determine signature length";
        EVP_MD_CTX_free(md_ctx);
        return false;
    }

    // Allocate memory for the signature
    *signature = (unsigned char*)malloc(*signature_len);
    if (*signature == NULL) {
        LOG(ERROR) << "Failed to allocate memory for the signature";
        EVP_MD_CTX_free(md_ctx);
        return false;
    }

    // Perform the final signing operation
    if (EVP_DigestSignFinal(md_ctx, *signature, signature_len) != 1) {
        LOG(ERROR) << "Failed to perform signing operation";
        free(*signature);
        EVP_MD_CTX_free(md_ctx);
        return false;
    }

    EVP_MD_CTX_free(md_ctx);
    return true;
}

/**
 * Perform EC/RSA verify operation using `EVP_PKEY`.
 */
int performVerifySignature(const char* data, size_t data_len, EVP_PKEY* pkey,
                           const unsigned char* signature, size_t signature_len) {
    // Create the verification context
    EVP_MD_CTX* md_ctx = EVP_MD_CTX_new();
    if (md_ctx == NULL) {
        LOG(ERROR) << "Failed to create verification context";
        return false;
    }

    // Initialize the verification operation
    if (EVP_DigestVerifyInit(md_ctx, NULL, EVP_sha256(), NULL, pkey) != 1) {
        LOG(ERROR) << "Failed to initialize verification operation";
        EVP_MD_CTX_free(md_ctx);
        return false;
    }

    // Verify the data
    if (EVP_DigestVerifyUpdate(md_ctx, data, data_len) != 1) {
        LOG(ERROR) << "Failed to verify data";
        EVP_MD_CTX_free(md_ctx);
        return false;
    }

    // Perform the verification operation
    int ret = EVP_DigestVerifyFinal(md_ctx, signature, signature_len);
    EVP_MD_CTX_free(md_ctx);

    return ret == 1;
}

/**
 * Extract the `EVP_PKEY` for the given KeyMint Key and perform Sign/Verify operations
 * using extracted `EVP_PKEY`.
 */
bool performCryptoOpUsingKeystoreEngine(int64_t grant_id) {
    const int KEY_ID_LEN = 20;
    char key_id[KEY_ID_LEN] = "";
    snprintf(key_id, KEY_ID_LEN, "%" PRIx64, grant_id);
    std::string str_key = std::string(keystore2_grant_id_prefix) + key_id;
    bool result = false;

#if defined(OPENSSL_IS_BORINGSSL)
    EVP_PKEY* evp = EVP_PKEY_from_keystore(str_key.c_str());
    if (!evp) {
        LOG(ERROR) << "Error while loading a key from keystore-engine";
        return false;
    }

    int algo_type = EVP_PKEY_id(evp);
    if (algo_type != EVP_PKEY_RSA && algo_type != EVP_PKEY_EC) {
        LOG(ERROR) << "Unsupported Algorithm. Only RSA and EC are allowed.";
        EVP_PKEY_free(evp);
        return false;
    }

    unsigned char* signature = NULL;
    size_t signature_len = 0;
    const char* INPUT_DATA = "MY MESSAGE FOR SIGN";
    size_t data_len = strlen(INPUT_DATA);
    if (!performSignData(INPUT_DATA, data_len, evp, &signature, &signature_len)) {
        LOG(ERROR) << "Failed to sign data";
        EVP_PKEY_free(evp);
        return false;
    }

    result = performVerifySignature(INPUT_DATA, data_len, evp, signature, signature_len);
    if (!result) {
        LOG(ERROR) << "Signature verification failed";
    } else {
        LOG(INFO) << "Signature verification success";
    }

    free(signature);
    EVP_PKEY_free(evp);
#endif
    return result;
}

CxxResult getValueFromAttestRecord(rust::Vec<rust::u8> cert_buf, int32_t tag,
                                   int32_t expected_sec_level) {
    CxxResult cxx_result{};
    cxx_result.error = false;

    uint8_t* cert_data = cert_buf.data();
    int cert_data_size = cert_buf.size();

    std::vector<uint8_t> cert_bytes;
    cert_bytes.insert(cert_bytes.end(), cert_data, (cert_data + cert_data_size));

    X509_Ptr cert(parseCertBlob(cert_bytes));
    if (!cert.get()) {
        LOG(ERROR) << "getValueFromAttestRecord - Failed to allocate a memory for certificate";
        cxx_result.error = true;
        return cxx_result;
    }

    ASN1_OCTET_STRING* attest_rec = getAttestationRecord(cert.get());
    if (!attest_rec) {
        LOG(ERROR) << "getValueFromAttestRecord - Error in getAttestationRecord: "
                   << keymaster::TranslateLastOpenSslError();
        cxx_result.error = true;
        return cxx_result;
    }

    aidl::android::hardware::security::keymint::AuthorizationSet att_sw_enforced;
    aidl::android::hardware::security::keymint::AuthorizationSet att_hw_enforced;
    uint32_t att_attestation_version;
    uint32_t att_keymint_version;
    aidl::android::hardware::security::keymint::SecurityLevel att_attestation_security_level;
    aidl::android::hardware::security::keymint::SecurityLevel att_keymint_security_level;
    std::vector<uint8_t> att_challenge;
    std::vector<uint8_t> att_unique_id;
    std::vector<uint8_t> att_app_id;

    int32_t error =
        static_cast<int32_t>(aidl::android::hardware::security::keymint::parse_attestation_record(
            attest_rec->data, attest_rec->length, &att_attestation_version,
            &att_attestation_security_level, &att_keymint_version, &att_keymint_security_level,
            &att_challenge, &att_sw_enforced, &att_hw_enforced, &att_unique_id));
    if (error) {
        LOG(ERROR) << "getValueFromAttestRecord - Error in parse_attestation_record: " << error;
        cxx_result.error = true;
        return cxx_result;
    }

    aidl::android::hardware::security::keymint::Tag auth_tag =
        static_cast<aidl::android::hardware::security::keymint::Tag>(tag);
    aidl::android::hardware::security::keymint::SecurityLevel tag_security_level =
        static_cast<aidl::android::hardware::security::keymint::SecurityLevel>(expected_sec_level);

    if (auth_tag == aidl::android::hardware::security::keymint::Tag::ATTESTATION_APPLICATION_ID) {
        int pos = att_sw_enforced.find(
            aidl::android::hardware::security::keymint::Tag::ATTESTATION_APPLICATION_ID);
        if (pos == -1) {
            LOG(ERROR) << "getValueFromAttestRecord - Attestation-application-id missing.";
            cxx_result.error = true;
            return cxx_result;
        }
        aidl::android::hardware::security::keymint::KeyParameter param = att_sw_enforced[pos];
        std::vector<uint8_t> val =
            param.value.get<aidl::android::hardware::security::keymint::KeyParameterValue::blob>();
        std::move(val.begin(), val.end(), std::back_inserter(cxx_result.data));
        return cxx_result;
    }

    if (auth_tag == aidl::android::hardware::security::keymint::Tag::ATTESTATION_CHALLENGE) {
        if (att_challenge.size() == 0) {
            LOG(ERROR) << "getValueFromAttestRecord - Attestation-challenge missing.";
            cxx_result.error = true;
            return cxx_result;
        }
        std::move(att_challenge.begin(), att_challenge.end(), std::back_inserter(cxx_result.data));
        return cxx_result;
    }

    if (auth_tag == aidl::android::hardware::security::keymint::Tag::UNIQUE_ID) {
        if (att_unique_id.size() == 0) {
            LOG(ERROR) << "getValueFromAttestRecord - unsupported tag - UNIQUE_ID.";
            cxx_result.error = true;
            return cxx_result;
        }
        std::move(att_unique_id.begin(), att_unique_id.end(), std::back_inserter(cxx_result.data));
        return cxx_result;
    }

    if (auth_tag == aidl::android::hardware::security::keymint::Tag::USAGE_COUNT_LIMIT) {
        aidl::android::hardware::security::keymint::KeyParameter param;
        int pos = att_hw_enforced.find(auth_tag);
        if (tag_security_level ==
                aidl::android::hardware::security::keymint::SecurityLevel::SOFTWARE ||
            tag_security_level ==
                aidl::android::hardware::security::keymint::SecurityLevel::KEYSTORE) {
            pos = att_sw_enforced.find(auth_tag);
            if (pos == -1) {
                LOG(ERROR) << "USAGE_COUNT_LIMIT not found in software enforced auth list";
                cxx_result.error = KM_ERROR_INVALID_TAG;
                return cxx_result;
            }
            param = att_sw_enforced[pos];
        } else {
            pos = att_hw_enforced.find(auth_tag);
            if (pos == -1) {
                LOG(ERROR) << "USAGE_COUNT_LIMIT not found in hardware enforced auth list";
                cxx_result.error = KM_ERROR_INVALID_TAG;
                return cxx_result;
            }
            param = att_hw_enforced[pos];
        }
        std::string val = std::to_string(
            param.value
                .get<aidl::android::hardware::security::keymint::KeyParameterValue::integer>());
        std::move(val.begin(), val.end(), std::back_inserter(cxx_result.data));
        return cxx_result;
    }

    int pos = att_hw_enforced.find(auth_tag);
    if (pos == -1) {
        LOG(ERROR) << "getValueFromAttestRecord - unsupported tag.";
        cxx_result.error = true;
        return cxx_result;
    }
    aidl::android::hardware::security::keymint::KeyParameter param = att_hw_enforced[pos];
    std::vector<uint8_t> val =
        param.value.get<aidl::android::hardware::security::keymint::KeyParameterValue::blob>();
    std::move(val.begin(), val.end(), std::back_inserter(cxx_result.data));
    return cxx_result;
}

uint32_t getOsVersion() {
    return aidl::android::hardware::security::keymint::getOsVersion();
}

uint32_t getOsPatchlevel() {
    return aidl::android::hardware::security::keymint::getOsPatchlevel();
}

uint32_t getVendorPatchlevel() {
    return aidl::android::hardware::security::keymint::getVendorPatchlevel();
}
