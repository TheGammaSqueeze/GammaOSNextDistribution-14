/*
 * Copyright (C) 2021 The Android Open Source Project
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

#define TLOG_TAG "apploader-cose"

#include <apploader/cbor.h>
#include <apploader/cose.h>
#include <assert.h>
#include <inttypes.h>
#include <openssl/bn.h>
#include <openssl/crypto.h>
#include <openssl/ec.h>
#include <openssl/err.h>
#include <openssl/evp.h>
#include <openssl/rand.h>
#include <openssl/x509.h>
#include <stddef.h>
#include <trusty_log.h>
#include <array>
#include <optional>
#include <vector>

#ifdef __COSE_HOST__
#define COSE_PRINT_ERROR(...)         \
    if (!gSilenceErrors) {            \
        fprintf(stderr, __VA_ARGS__); \
    }
#else
#define COSE_PRINT_ERROR(...) \
    if (!gSilenceErrors) {    \
        TLOGE(__VA_ARGS__);   \
    }
#endif

#ifdef APPLOADER_PACKAGE_SIGN_P384
#define APPLOADER_DSA_LENGTH SHA384_DIGEST_LENGTH
#define APPLOADER_DSA_NID NID_secp384r1
#else
#define APPLOADER_DSA_LENGTH SHA256_DIGEST_LENGTH
#define APPLOADER_DSA_NID NID_X9_62_prime256v1
#endif

#ifdef APPLOADER_PACKAGE_CIPHER_A256
#define EVP_aes_trusty_gcm() EVP_aes_256_gcm()
#else
#define EVP_aes_trusty_gcm() EVP_aes_128_gcm()
#endif

static bool gSilenceErrors = false;

constexpr size_t kEcdsaValueSize = APPLOADER_DSA_LENGTH;
constexpr size_t kEcdsaSignatureSize = 2 * kEcdsaValueSize;

bool coseSetSilenceErrors(bool value) {
    bool old = gSilenceErrors;
    gSilenceErrors = value;
    return old;
}

using BIGNUM_Ptr = std::unique_ptr<BIGNUM, std::function<void(BIGNUM*)>>;
using EC_KEY_Ptr = std::unique_ptr<EC_KEY, std::function<void(EC_KEY*)>>;
using ECDSA_SIG_Ptr =
        std::unique_ptr<ECDSA_SIG, std::function<void(ECDSA_SIG*)>>;
using EVP_CIPHER_CTX_Ptr =
        std::unique_ptr<EVP_CIPHER_CTX, std::function<void(EVP_CIPHER_CTX*)>>;

using SHADigest = std::array<uint8_t, kEcdsaValueSize>;

static std::vector<uint8_t> coseBuildToBeSigned(
        const std::basic_string_view<uint8_t>& encodedProtectedHeaders,
        const std::vector<uint8_t>& data) {
    cbor::VectorCborEncoder enc;
    enc.encodeArray([&](auto& enc) {
        enc.encodeTstr("Signature1");
        enc.encodeBstr(encodedProtectedHeaders);
        // We currently don't support Externally Supplied Data (RFC 8152
        // section 4.3) so external_aad is the empty bstr
        enc.encodeEmptyBstr();
        enc.encodeBstr(data);
    });

    return enc.intoVec();
}

static std::optional<std::vector<uint8_t>> getRandom(size_t numBytes) {
    std::vector<uint8_t> output;
    output.resize(numBytes);
    if (RAND_bytes(output.data(), numBytes) != 1) {
        COSE_PRINT_ERROR("RAND_bytes: failed getting %zu random\n", numBytes);
        return {};
    }
    return output;
}

#ifdef APPLOADER_PACKAGE_SIGN_P384
static SHADigest sha(
        const std::vector<std::tuple<const void*, size_t>>& data_list) {
    SHADigest ret;
    SHA512_CTX ctx;  // Note that SHA384 functions use a SHA512 context

    SHA384_Init(&ctx);

    for (auto data : data_list) {
        SHA384_Update(&ctx, std::get<0>(data), std::get<1>(data));
    }

    SHA384_Final((unsigned char*)ret.data(), &ctx);

    return ret;
}
#else
static SHADigest sha(
        const std::vector<std::tuple<const void*, size_t>>& data_list) {
    SHADigest ret;
    SHA256_CTX ctx;

    SHA256_Init(&ctx);

    for (auto data : data_list) {
        SHA256_Update(&ctx, std::get<0>(data), std::get<1>(data));
    }

    SHA256_Final((unsigned char*)ret.data(), &ctx);

    return ret;
}
#endif

static SHADigest sha(const std::vector<uint8_t>& data) {
    return sha({{data.data(), data.size()}});
}

static std::optional<std::vector<uint8_t>> signEcDsaDigest(
        const std::vector<uint8_t>& key,
        const SHADigest& dataDigest) {
    const unsigned char* k = key.data();
    auto ecKey =
            EC_KEY_Ptr(d2i_ECPrivateKey(nullptr, &k, key.size()), EC_KEY_free);
    if (!ecKey) {
        COSE_PRINT_ERROR("Error parsing EC private key\n");
        return {};
    }

    if (EC_KEY_check_key(ecKey.get()) == 0) {
        COSE_PRINT_ERROR("Error checking EC private key\n");
        return {};
    }

    const EC_GROUP* ecGroup = EC_KEY_get0_group(ecKey.get());
    if (EC_GROUP_get_curve_name(ecGroup) != APPLOADER_DSA_NID) {
        COSE_PRINT_ERROR("Error checking EC group (not secp384r1)\n");
        return {};
    }

    auto sig = ECDSA_SIG_Ptr(
            ECDSA_do_sign(dataDigest.data(), dataDigest.size(), ecKey.get()),
            ECDSA_SIG_free);
    if (!sig) {
        COSE_PRINT_ERROR("Error signing digest:\n");
        return {};
    }
    size_t len = i2d_ECDSA_SIG(sig.get(), nullptr);
    std::vector<uint8_t> signature;
    signature.resize(len);
    unsigned char* p = (unsigned char*)signature.data();
    i2d_ECDSA_SIG(sig.get(), &p);
    return signature;
}

static std::optional<std::vector<uint8_t>> signEcDsa(
        const std::vector<uint8_t>& key,
        const std::vector<uint8_t>& data) {
    return signEcDsaDigest(key, sha(data));
}

static bool ecdsaSignatureDerToCose(
        const std::vector<uint8_t>& ecdsaDerSignature,
        std::vector<uint8_t>& ecdsaCoseSignature) {
    const unsigned char* p = ecdsaDerSignature.data();
    auto sig =
            ECDSA_SIG_Ptr(d2i_ECDSA_SIG(nullptr, &p, ecdsaDerSignature.size()),
                          ECDSA_SIG_free);
    if (!sig) {
        COSE_PRINT_ERROR("Error decoding DER signature\n");
        return false;
    }

    const BIGNUM* rBn;
    const BIGNUM* sBn;
    ECDSA_SIG_get0(sig.get(), &rBn, &sBn);

    /*
     * Older versions of OpenSSL also do not have BN_bn2binpad,
     * so we need to use BN_bn2bin with the correct offsets.
     * Each of the output values is a 32-byte big-endian number,
     * while the inputs are BIGNUMs stored in host format.
     * We can insert the padding ourselves by zeroing the output array,
     * then placing the output of BN_bn2bin so its end aligns
     * with the end of the 32-byte big-endian number.
     */
    auto rBnSize = BN_num_bytes(rBn);
    if (rBnSize < 0 || static_cast<size_t>(rBnSize) > kEcdsaValueSize) {
        COSE_PRINT_ERROR("Invalid ECDSA r value size (%d)\n", rBnSize);
        return false;
    }
    auto sBnSize = BN_num_bytes(sBn);
    if (sBnSize < 0 || static_cast<size_t>(sBnSize) > kEcdsaValueSize) {
        COSE_PRINT_ERROR("Invalid ECDSA s value size (%d)\n", sBnSize);
        return false;
    }

    ecdsaCoseSignature.clear();
    ecdsaCoseSignature.resize(kEcdsaSignatureSize, 0);
    if (BN_bn2bin(rBn, ecdsaCoseSignature.data() + kEcdsaValueSize - rBnSize) !=
        rBnSize) {
        COSE_PRINT_ERROR("Error encoding r\n");
        return false;
    }
    if (BN_bn2bin(sBn, ecdsaCoseSignature.data() + kEcdsaSignatureSize -
                               sBnSize) != sBnSize) {
        COSE_PRINT_ERROR("Error encoding s\n");
        return false;
    }
    return true;
}

std::optional<std::vector<uint8_t>> coseSignEcDsa(
        const std::vector<uint8_t>& key,
        uint8_t keyId,
        const std::vector<uint8_t>& data,
        const std::basic_string_view<uint8_t>& encodedProtectedHeaders,
        std::basic_string_view<uint8_t>& unprotectedHeaders,
        bool detachContent,
        bool tagged) {
    cbor::VectorCborEncoder addnHeadersEnc;
    addnHeadersEnc.encodeMap([&](auto& enc) {
        enc.encodeKeyValue(COSE_LABEL_KID, [&](auto& enc) {
            enc.encodeBstr(std::basic_string_view(&keyId, 1));
        });
    });
    auto updatedUnprotectedHeaders =
            cbor::mergeMaps(unprotectedHeaders, addnHeadersEnc.view());
    if (!updatedUnprotectedHeaders.has_value()) {
        COSE_PRINT_ERROR("Error updating unprotected headers\n");
        return {};
    }

    std::vector<uint8_t> toBeSigned =
            coseBuildToBeSigned(encodedProtectedHeaders, data);

    std::optional<std::vector<uint8_t>> derSignature =
            signEcDsa(key, toBeSigned);
    if (!derSignature) {
        COSE_PRINT_ERROR("Error signing toBeSigned data\n");
        return {};
    }
    std::vector<uint8_t> coseSignature;
    if (!ecdsaSignatureDerToCose(derSignature.value(), coseSignature)) {
        COSE_PRINT_ERROR(
                "Error converting ECDSA signature from DER to COSE format\n");
        return {};
    }

    auto arrayEncodingFn = [&](auto& enc) {
        enc.encodeArray([&](auto& enc) {
            /* 1: protected:empty_or_serialized_map */
            enc.encodeBstr(encodedProtectedHeaders);

            /* 2: unprotected:map */
            enc.copyBytes(updatedUnprotectedHeaders.value());

            /* 3: payload:bstr_or_nil */
            if (detachContent) {
                enc.encodeNull();
            } else {
                enc.encodeBstr(data);
            }

            /* 4: signature:bstr */
            enc.encodeBstr(coseSignature);
        });
    };

    cbor::VectorCborEncoder enc;
    if (tagged) {
        enc.encodeTag(COSE_TAG_SIGN1, arrayEncodingFn);
    } else {
        arrayEncodingFn(enc);
    }

    return enc.intoVec();
}

bool coseIsSigned(CoseByteView data, size_t* signatureLength) {
    struct CborIn in;
    uint64_t tag;

    CborInInit(data.data(), data.size(), &in);
    while (!CborInAtEnd(&in)) {
        if (CborReadTag(&in, &tag) == CBOR_READ_RESULT_OK) {
            if (tag == COSE_TAG_SIGN1) {
                if (signatureLength) {
                    /* read tag item to get its size */
                    CborReadSkip(&in);
                    *signatureLength = CborInOffset(&in);
                }
                return true;
            }
        } else if (CborReadSkip(&in) != CBOR_READ_RESULT_OK) {
            /*
             * CborReadSkip uses a stack to track nested content so parsing can
             * fail if nesting of CBOR items causes stack exhaustion. The COSE
             * format does not cause stack exhaustion so the input must be bad.
             */
            return false;
        }
    }

    return false;
}

static bool checkEcDsaSignature(const SHADigest& digest,
                                const uint8_t* signature,
                                const uint8_t* publicKey,
                                size_t publicKeySize) {
    auto rBn =
            BIGNUM_Ptr(BN_bin2bn(signature, kEcdsaValueSize, nullptr), BN_free);
    if (rBn.get() == nullptr) {
        COSE_PRINT_ERROR("Error creating BIGNUM for r\n");
        return false;
    }

    auto sBn = BIGNUM_Ptr(
            BN_bin2bn(signature + kEcdsaValueSize, kEcdsaValueSize, nullptr),
            BN_free);
    if (sBn.get() == nullptr) {
        COSE_PRINT_ERROR("Error creating BIGNUM for s\n");
        return false;
    }

    auto sig = ECDSA_SIG_Ptr(ECDSA_SIG_new(), ECDSA_SIG_free);
    if (!sig) {
        COSE_PRINT_ERROR("Error allocating ECDSA_SIG\n");
        return false;
    }

    ECDSA_SIG_set0(sig.get(), rBn.release(), sBn.release());

    const unsigned char* k = publicKey;
    auto ecKey =
            EC_KEY_Ptr(d2i_EC_PUBKEY(nullptr, &k, publicKeySize), EC_KEY_free);
    if (!ecKey) {
        COSE_PRINT_ERROR("Error parsing EC public key\n");
        return false;
    }

    int rc = ECDSA_do_verify(digest.data(), digest.size(), sig.get(),
                             ecKey.get());
    if (rc != 1) {
        COSE_PRINT_ERROR("Error verifying signature (rc=%d)\n", rc);
        return false;
    }

    return true;
}

bool coseCheckEcDsaSignature(const std::vector<uint8_t>& signatureCoseSign1,
                             const std::vector<uint8_t>& detachedContent,
                             const std::vector<uint8_t>& publicKey) {
    struct CborIn in;
    CborInInit(signatureCoseSign1.data(), signatureCoseSign1.size(), &in);

    uint64_t tag;
    /* COSE message tag is optional */
    if (CborReadTag(&in, &tag) == CBOR_READ_RESULT_OK) {
        if (tag != COSE_TAG_SIGN1) {
            COSE_PRINT_ERROR("Passed-in COSE_Sign1 contained invalid tag\n");
            return false;
        }
    }

    size_t arraySize;
    if (CborReadArray(&in, &arraySize) != CBOR_READ_RESULT_OK) {
        COSE_PRINT_ERROR("Value for COSE_Sign1 is not an array\n");
        return false;
    }

    if (arraySize != 4) {
        COSE_PRINT_ERROR("Value for COSE_Sign1 is not an array of size 4\n");
        return false;
    }

    const uint8_t* encodedProtectedHeadersPtr;
    size_t encodedProtectedHeadersSize;
    if (CborReadBstr(&in, &encodedProtectedHeadersSize,
                     &encodedProtectedHeadersPtr) != CBOR_READ_RESULT_OK) {
        COSE_PRINT_ERROR("Value for encodedProtectedHeaders is not a bstr\n");
        return false;
    }
    std::basic_string_view<uint8_t> encodedProtectedHeaders{
            encodedProtectedHeadersPtr, encodedProtectedHeadersSize};

    size_t unprotectedHeadersSize;
    if (CborReadMap(&in, &unprotectedHeadersSize) != CBOR_READ_RESULT_OK) {
        COSE_PRINT_ERROR("Value for unprotectedHeaders is not a map\n");
        return false;
    }

    /* skip past unprotected headers by reading two items per map entry */
    for (size_t item = 0; item < 2 * unprotectedHeadersSize; item++) {
        if (CborReadSkip(&in) != CBOR_READ_RESULT_OK) {
            COSE_PRINT_ERROR("Passed-in COSE_Sign1 is not valid CBOR\n");
            return false;
        }
    }

    const uint8_t* dataPtr;
    size_t dataSize = 0;
    if (CborReadBstr(&in, &dataSize, &dataPtr) != CBOR_READ_RESULT_OK) {
        if (CborReadNull(&in) != CBOR_READ_RESULT_OK) {
            COSE_PRINT_ERROR("Value for payload is not null or a bstr\n");
            return false;
        }
    }
    std::vector<uint8_t> data(dataPtr, dataPtr + dataSize);

    if (data.size() > 0 && detachedContent.size() > 0) {
        COSE_PRINT_ERROR("data and detachedContent cannot both be non-empty\n");
        return false;
    }

    const uint8_t* coseSignatureData;
    size_t coseSignatureSize;
    if (CborReadBstr(&in, &coseSignatureSize, &coseSignatureData) !=
        CBOR_READ_RESULT_OK) {
        COSE_PRINT_ERROR("Value for signature is not a bstr\n");
        return false;
    }

    if (coseSignatureSize != kEcdsaSignatureSize) {
        COSE_PRINT_ERROR("COSE signature length is %zu, expected %zu\n",
                         coseSignatureSize, kEcdsaSignatureSize);
        return false;
    }

    // The last field is the payload, independently of how it's transported (RFC
    // 8152 section 4.4). Since our API specifies only one of |data| and
    // |detachedContent| can be non-empty, it's simply just the non-empty one.
    auto& signaturePayload = data.size() > 0 ? data : detachedContent;

    std::vector<uint8_t> toBeSigned =
            coseBuildToBeSigned(encodedProtectedHeaders, signaturePayload);
    if (!checkEcDsaSignature(sha(toBeSigned), coseSignatureData,
                             publicKey.data(), publicKey.size())) {
        COSE_PRINT_ERROR("Signature check failed\n");
        return false;
    }

    return true;
}

/*
 * Strict signature verification code
 */
static const uint8_t kSignatureHeader[] = {
        /* clang-format off */
    0xD2,       // 0xc0 = Tagged item | tag = 18 = COSE_TAG_SIGN1
    0x84,       // 0x80 = Array       | len = 4

    // Array item 1
#ifdef APPLOADER_PACKAGE_SIGN_P384
    0x55,       // 0x20 = Byte string | len = 21
#else
    0x54,       // 0x20 = Byte string | len = 20
#endif

        0xA2,       // 0xa0 = Map         | items = 2

        // Map entry 1: key, value
        0x01,       // 0x0 = unsigned int | val = 1 = COSE_LABEL_ALG
#ifdef APPLOADER_PACKAGE_SIGN_P384
        0x38,       // 0x2 = Negative int | additional = 24 (1 byte val)
        0x22,       // Value = 34
                    // == -1 - 34 = -35 = COSE_ALG_ECDSA_384
#else
        0x26,       // 0x2 = Negative int | value = 6
                    // == -1 - 6 = -7 = COSE_ALG_ECDSA_256
#endif
        // Map entry 2: key, value
        0x3A,       // 0x3 = Negative int | additional = 26 = next 4 bytes
        0x00,       // 0x00010000 = 65536
        0x01,       //              -1 - 65536 = -65535 = COSE_LABEL_TRUSTY
        0x00,
        0x00,

        0x82,       // 0x80 = Array       | len = 2
            0x69,       // 0x30 = Text string | len = 9
            0x54,       // T
            0x72,       // r
            0x75,       // u
            0x73,       // s
            0x74,       // t
            0x79,       // y
            0x41,       // A
            0x70,       // p
            0x70,       // p
            // Version
            0x01,       // 0x00 = Small value | value = 1 = APPLOADER_SIGNATURE_FORMAT_VERSION_CURRENT

    // Array Item 2
    0xA1,       // 0xa = Map          | items = 1
    0x04,       // 0x0 = unsigned int | value = 4 = COSE_LABEL_KID
    0x41,       // 0x4 = byte string  | len = 1
    /* Next octet is the key Id */

        /* clang-format on */
};

static const uint8_t kSignatureHeaderPart2[] = {
        /* clang-format off */
    0xF6,       // 0x7 = simple value | value = 22 = null
    0x58,       // 0x2 = bytes string | additional = 24 = next 1 byte
#ifdef APPLOADER_PACKAGE_SIGN_P384
    0x60        // length = 96
#else
    0x40        // length = 64
#endif
        /* clang-format on */
};

static const uint8_t kSignature1Header[] = {
        /* clang-format off */
    0x84,       // 0x8 = array       | length = 4

    // Array item 1
    0x6A,       // 0x6 = text string | length = 10
        0x53,       // S
        0x69,       // i
        0x67,       // g
        0x6E,       // n
        0x61,       // a
        0x74,       // t
        0x75,       // u
        0x72,       // r
        0x65,       // e
        0x31,       // 1

    // Array item 2
#ifdef APPLOADER_PACKAGE_SIGN_P384
    0x55,       // 0x20 = Byte string | len = 21
#else
    0x54,       // 0x20 = Byte string | len = 20
#endif
        0xA2,       // 0xa0 = Map         | items = 2

        // Map entry 1: key, value
        0x01,       // 0x0 = unsigned int | val = 1 = COSE_LABEL_ALG
#ifdef APPLOADER_PACKAGE_SIGN_P384
        0x38,       // 0x2 = Negative int | additional = 24 (1 byte val)
        0x22,       // Value = 34
                    // == -1 - 34 = -35 = COSE_ALG_ECDSA_384
#else
        0x26,       // 0x2 = Negative int | value = 6
                    // == -1 - 6 = -7 = COSE_ALG_ECDSA_256
#endif
        // Map entry 2: key, value
        0x3A,       // 0x3 = Negative int | additional = 26 = next 4 bytes
        0x00,       // 0x00010000 = 65536
        0x01,       //              -1 - 65536 = -65535 = COSE_LABEL_TRUSTY
        0x00,
        0x00,

        0x82,       // 0x8 = Array       | len = 2
                0x69,       // 0x30 = Text string | len = 9
                0x54,       // T
                0x72,       // r
                0x75,       // u
                0x73,       // s
                0x74,       // t
                0x79,       // y
                0x41,       // A
                0x70,       // p
                0x70,       // p
                // Version
                0x01,       // 0x00 = Small value | value = 1
                            //    = APPLOADER_SIGNATURE_FORMAT_VERSION_CURRENT

    // Array item 3
    0x40,       // 0x4 = byte string    | len = 0

        /* clang-format on */
};

/*
 * Fixed offset constants
 */
constexpr size_t kSignatureKeyIdOffset = sizeof(kSignatureHeader);
constexpr size_t kSignatureHeaderPart2Offset = kSignatureKeyIdOffset + 1;
constexpr size_t kSignatureOffset =
        kSignatureHeaderPart2Offset + sizeof(kSignatureHeaderPart2);
constexpr size_t kPayloadOffset = kSignatureOffset + kEcdsaSignatureSize;

bool strictCheckEcDsaSignature(const uint8_t* packageStart,
                               size_t packageSize,
                               GetKeyFn keyFn,
                               const uint8_t** outPackageStart,
                               size_t* outPackageSize) {
    if (packageSize < kPayloadOffset) {
        COSE_PRINT_ERROR("Passed-in COSE_Sign1 is not large enough\n");
        return false;
    }

    if (CRYPTO_memcmp(packageStart, kSignatureHeader,
                      sizeof(kSignatureHeader))) {
        COSE_PRINT_ERROR("Passed-in COSE_Sign1 is not valid CBOR\n");
        return false;
    }

    uint8_t kid = packageStart[kSignatureKeyIdOffset];
    auto [publicKey, publicKeySize] = keyFn(kid);
    if (!publicKey) {
        COSE_PRINT_ERROR("Failed to retrieve public key\n");
        return false;
    }

    if (CRYPTO_memcmp(packageStart + kSignatureHeaderPart2Offset,
                      kSignatureHeaderPart2, sizeof(kSignatureHeaderPart2))) {
        COSE_PRINT_ERROR("Passed-in COSE_Sign1 is not valid CBOR\n");
        return false;
    }

    // The Signature1 structure encodes the payload as a bstr wrapping the
    // actual contents (even if they already are CBOR), so we need to manually
    // prepend a CBOR bstr header to the payload
    constexpr size_t kMaxPayloadSizeHeaderSize = 9;
    size_t payloadSize = packageSize - kPayloadOffset;
    size_t payloadSizeHeaderSize = cbor::encodedSizeOf(payloadSize);
    assert(payloadSizeHeaderSize <= kMaxPayloadSizeHeaderSize);

    uint8_t payloadSizeHeader[kMaxPayloadSizeHeaderSize];

    cbor::encodeBstrHeader(payloadSize, kMaxPayloadSizeHeaderSize,
                           payloadSizeHeader);

    SHADigest digest = sha({{kSignature1Header, sizeof(kSignature1Header)},
                            {payloadSizeHeader, payloadSizeHeaderSize},
                            {packageStart + kPayloadOffset, payloadSize}});

    if (!checkEcDsaSignature(digest, packageStart + kSignatureOffset,
                             publicKey.get(), publicKeySize)) {
        COSE_PRINT_ERROR("Signature check failed\n");
        return false;
    }

    if (outPackageStart != nullptr) {
        *outPackageStart = packageStart + kPayloadOffset;
    }
    if (outPackageSize != nullptr) {
        *outPackageSize = payloadSize;
    }
    return true;
}

static std::tuple<std::unique_ptr<uint8_t[]>, size_t> coseBuildGcmAad(
        const std::string_view context,
        const std::basic_string_view<uint8_t> encodedProtectedHeaders,
        const std::basic_string_view<uint8_t> externalAad) {
    cbor::ArrayCborEncoder enc;
    enc.encodeArray([&](auto& enc) {
        enc.encodeTstr(context);
        enc.encodeBstr(encodedProtectedHeaders);
        enc.encodeBstr(externalAad);
    });

    return {enc.intoVec().arr(), enc.size()};
}

static std::optional<std::vector<uint8_t>> encryptAesGcm(
        const std::vector<uint8_t>& key,
        const std::vector<uint8_t>& nonce,
        const CoseByteView& data,
        std::basic_string_view<uint8_t> additionalAuthenticatedData) {
    if (key.size() != kAesGcmKeySize) {
        COSE_PRINT_ERROR("key is not kAesGcmKeySize (%zu) bytes, got %zu\n",
                         kAesGcmKeySize, key.size());
        return {};
    }
    if (nonce.size() != kAesGcmIvSize) {
        COSE_PRINT_ERROR("nonce is not kAesGcmIvSize bytes, got %zu\n",
                         nonce.size());
        return {};
    }

    // The result is the ciphertext followed by the tag (kAesGcmTagSize bytes).
    std::vector<uint8_t> encryptedData;
    encryptedData.resize(data.size() + kAesGcmTagSize);
    unsigned char* ciphertext = (unsigned char*)encryptedData.data();
    unsigned char* tag = ciphertext + data.size();

    auto ctx = EVP_CIPHER_CTX_Ptr(EVP_CIPHER_CTX_new(), EVP_CIPHER_CTX_free);
    if (ctx.get() == nullptr) {
        COSE_PRINT_ERROR("EVP_CIPHER_CTX_new: failed, error 0x%lx\n",
                         static_cast<unsigned long>(ERR_get_error()));
        return {};
    }

    if (EVP_EncryptInit_ex(ctx.get(), EVP_aes_trusty_gcm(), NULL, NULL, NULL) !=
        1) {
        COSE_PRINT_ERROR("EVP_EncryptInit_ex: failed, error 0x%lx\n",
                         static_cast<unsigned long>(ERR_get_error()));
        return {};
    }

    if (EVP_CIPHER_CTX_ctrl(ctx.get(), EVP_CTRL_GCM_SET_IVLEN, kAesGcmIvSize,
                            NULL) != 1) {
        COSE_PRINT_ERROR(
                "EVP_CIPHER_CTX_ctrl: failed setting nonce length, "
                "error 0x%lx\n",
                static_cast<unsigned long>(ERR_get_error()));
        return {};
    }

    if (EVP_EncryptInit_ex(ctx.get(), NULL, NULL, key.data(), nonce.data()) !=
        1) {
        COSE_PRINT_ERROR("EVP_EncryptInit_ex: failed, error 0x%lx\n",
                         static_cast<unsigned long>(ERR_get_error()));
        return {};
    }

    int numWritten;
    if (additionalAuthenticatedData.size() > 0) {
        if (EVP_EncryptUpdate(ctx.get(), NULL, &numWritten,
                              additionalAuthenticatedData.data(),
                              additionalAuthenticatedData.size()) != 1) {
            fprintf(stderr,
                    "EVP_EncryptUpdate: failed for "
                    "additionalAuthenticatedData, error 0x%lx\n",
                    static_cast<unsigned long>(ERR_get_error()));
            return {};
        }
        if ((size_t)numWritten != additionalAuthenticatedData.size()) {
            fprintf(stderr,
                    "EVP_EncryptUpdate: Unexpected outl=%d (expected %zu) "
                    "for additionalAuthenticatedData\n",
                    numWritten, additionalAuthenticatedData.size());
            return {};
        }
    }

    if (data.size() > 0) {
        if (EVP_EncryptUpdate(ctx.get(), ciphertext, &numWritten, data.data(),
                              data.size()) != 1) {
            COSE_PRINT_ERROR("EVP_EncryptUpdate: failed, error 0x%lx\n",
                             static_cast<unsigned long>(ERR_get_error()));
            return {};
        }
        if ((size_t)numWritten != data.size()) {
            fprintf(stderr,
                    "EVP_EncryptUpdate: Unexpected outl=%d (expected %zu)\n",
                    numWritten, data.size());
            ;
            return {};
        }
    }

    if (EVP_EncryptFinal_ex(ctx.get(), ciphertext + numWritten, &numWritten) !=
        1) {
        COSE_PRINT_ERROR("EVP_EncryptFinal_ex: failed, error 0x%lx\n",
                         static_cast<unsigned long>(ERR_get_error()));
        return {};
    }
    if (numWritten != 0) {
        COSE_PRINT_ERROR("EVP_EncryptFinal_ex: Unexpected non-zero outl=%d\n",
                         numWritten);
        return {};
    }

    if (EVP_CIPHER_CTX_ctrl(ctx.get(), EVP_CTRL_GCM_GET_TAG, kAesGcmTagSize,
                            tag) != 1) {
        COSE_PRINT_ERROR(
                "EVP_CIPHER_CTX_ctrl: failed getting tag, "
                "error 0x%lx\n",
                static_cast<unsigned long>(ERR_get_error()));
        return {};
    }

    return encryptedData;
}

static std::optional<std::vector<uint8_t>> coseEncryptAesGcm(
        const std::string_view context,
        const std::vector<uint8_t>& key,
        const CoseByteView& data,
        const std::vector<uint8_t>& externalAad,
        const std::vector<uint8_t>& encodedProtectedHeaders,
        const CoseByteView& unprotectedHeaders,
        std::optional<std::vector<uint8_t>> recipients) {
    std::optional<std::vector<uint8_t>> iv = getRandom(kAesGcmIvSize);
    if (!iv) {
        COSE_PRINT_ERROR("Error generating encryption IV\n");
        return {};
    }

    cbor::VectorCborEncoder ivEnc;
    ivEnc.encodeMap([&](auto& enc) {
        enc.encodeKeyValue(COSE_LABEL_IV,
                           [&](auto& enc) { enc.encodeBstr(iv.value()); });
    });

    auto finalUnprotectedHeaders =
            cbor::mergeMaps(unprotectedHeaders, ivEnc.view());
    if (!finalUnprotectedHeaders) {
        COSE_PRINT_ERROR("Error updating unprotected headers with IV\n");
        return {};
    }

    std::basic_string_view encodedProtectedHeadersView{
            encodedProtectedHeaders.data(), encodedProtectedHeaders.size()};
    std::basic_string_view externalAadView{externalAad.data(),
                                           externalAad.size()};
    auto [gcmAad, gcmAadSize] = coseBuildGcmAad(
            context, encodedProtectedHeadersView, externalAadView);
    std::basic_string_view gcmAadView{gcmAad.get(), gcmAadSize};

    std::optional<std::vector<uint8_t>> ciphertext =
            encryptAesGcm(key, iv.value(), data, gcmAadView);
    if (!ciphertext) {
        COSE_PRINT_ERROR("Error encrypting data\n");
        return {};
    }

    cbor::VectorCborEncoder enc;
    enc.encodeArray([&](auto& enc) {
        enc.encodeBstr(encodedProtectedHeaders);
        enc.copyBytes(finalUnprotectedHeaders.value());
        enc.encodeBstr(ciphertext.value());
        if (recipients) {
            enc.copyBytes(recipients.value());
        }
    });

    return enc.intoVec();
}

std::optional<std::vector<uint8_t>> coseEncryptAesGcmKeyWrap(
        const std::vector<uint8_t>& key,
        uint8_t keyId,
        const CoseByteView& data,
        const std::vector<uint8_t>& externalAad,
        const std::vector<uint8_t>& encodedProtectedHeaders,
        const CoseByteView& unprotectedHeaders,
        bool tagged) {
    /* Generate and encrypt the CEK */
    std::optional<std::vector<uint8_t>> contentEncryptionKey =
            getRandom(kAesGcmKeySize);
    if (!contentEncryptionKey) {
        COSE_PRINT_ERROR("Error generating encryption key\n");
        return {};
    }

    cbor::VectorCborEncoder coseKeyEnc;
    coseKeyEnc.encodeMap([&](auto& enc) {
        enc.encodeKeyValue(COSE_LABEL_KEY_KTY, COSE_KEY_TYPE_SYMMETRIC);
        enc.encodeKeyValue(COSE_LABEL_KEY_ALG, COSE_VAL_CIPHER_ALG);
        enc.encodeKeyValue(COSE_LABEL_KEY_SYMMETRIC_KEY, [&](auto& enc) {
            enc.encodeBstr(contentEncryptionKey.value());
        });
    });
    CoseByteView coseKeyByteView = coseKeyEnc.view();

    cbor::VectorCborEncoder keyUnprotectedHeadersEnc;
    keyUnprotectedHeadersEnc.encodeMap([&](auto& enc) {
        enc.encodeKeyValue(COSE_LABEL_KID, [&](auto& enc) {
            enc.encodeBstr(std::basic_string_view<uint8_t>(&keyId, 1));
        });
    });
    auto keyUnprotectedHeaders = keyUnprotectedHeadersEnc.view();

    cbor::VectorCborEncoder encodedProtectedHeadersForEncKey;
    encodedProtectedHeadersForEncKey.encodeMap([&](auto& enc) {
        enc.encodeKeyValue(COSE_LABEL_ALG, COSE_VAL_CIPHER_ALG);
    });

    auto encContentEncryptionKey =
            coseEncryptAesGcm(COSE_CONTEXT_ENC_RECIPIENT, key, coseKeyByteView,
                              {}, encodedProtectedHeadersForEncKey.intoVec(),
                              keyUnprotectedHeaders, {});
    if (!encContentEncryptionKey.has_value()) {
        COSE_PRINT_ERROR("Error wrapping encryption key\n");
        return {};
    }

    cbor::VectorCborEncoder recipientsEnc;
    recipientsEnc.encodeArray(
            [&](auto& enc) { enc.copyBytes(encContentEncryptionKey.value()); });
    auto recipients = recipientsEnc.intoVec();

    auto coseEncrypt = coseEncryptAesGcm(
            COSE_CONTEXT_ENCRYPT, std::move(contentEncryptionKey.value()), data,
            externalAad, encodedProtectedHeaders, unprotectedHeaders,
            std::move(recipients));
    if (!coseEncrypt.has_value()) {
        COSE_PRINT_ERROR("Error encrypting application package\n");
        return {};
    }

    if (tagged) {
        cbor::VectorCborEncoder enc;
        enc.encodeTag(COSE_TAG_ENCRYPT,
                      [&](auto& enc) { enc.copyBytes(coseEncrypt.value()); });
        return enc.intoVec();
    } else {
        return coseEncrypt;
    }
}

static bool decryptAesGcmInPlace(
        std::basic_string_view<uint8_t> key,
        std::basic_string_view<uint8_t> nonce,
        uint8_t* encryptedData,
        size_t encryptedDataSize,
        std::basic_string_view<uint8_t> additionalAuthenticatedData,
        size_t* outPlaintextSize) {
    assert(outPlaintextSize != nullptr);

    int ciphertextSize = int(encryptedDataSize) - kAesGcmTagSize;
    if (ciphertextSize < 0) {
        COSE_PRINT_ERROR("encryptedData too small\n");
        return false;
    }
    if (key.size() != kAesGcmKeySize) {
        COSE_PRINT_ERROR("key is not kAesGcmKeySize (%zu) bytes, got %zu\n",
                         kAesGcmKeySize, key.size());
        return {};
    }
    if (nonce.size() != kAesGcmIvSize) {
        COSE_PRINT_ERROR("nonce is not kAesGcmIvSize bytes, got %zu\n",
                         nonce.size());
        return false;
    }
    unsigned char* ciphertext = encryptedData;
    unsigned char* tag = ciphertext + ciphertextSize;

    /*
     * Decrypt the data in place. OpenSSL and BoringSSL support this as long as
     * the plaintext buffer completely overlaps the ciphertext.
     */
    unsigned char* plaintext = encryptedData;

    auto ctx = EVP_CIPHER_CTX_Ptr(EVP_CIPHER_CTX_new(), EVP_CIPHER_CTX_free);
    if (ctx.get() == nullptr) {
        COSE_PRINT_ERROR("EVP_CIPHER_CTX_new: failed, error 0x%lx\n",
                         static_cast<unsigned long>(ERR_get_error()));
        return false;
    }

    if (EVP_DecryptInit_ex(ctx.get(), EVP_aes_trusty_gcm(), NULL, NULL, NULL) !=
        1) {
        COSE_PRINT_ERROR("EVP_DecryptInit_ex: failed, error 0x%lx\n",
                         static_cast<unsigned long>(ERR_get_error()));
        return false;
    }

    if (EVP_CIPHER_CTX_ctrl(ctx.get(), EVP_CTRL_GCM_SET_IVLEN, kAesGcmIvSize,
                            NULL) != 1) {
        COSE_PRINT_ERROR(
                "EVP_CIPHER_CTX_ctrl: failed setting nonce length, "
                "error 0x%lx\n",
                static_cast<unsigned long>(ERR_get_error()));
        return false;
    }

    if (EVP_DecryptInit_ex(ctx.get(), NULL, NULL, key.data(), nonce.data()) !=
        1) {
        COSE_PRINT_ERROR("EVP_DecryptInit_ex: failed, error 0x%lx\n",
                         static_cast<unsigned long>(ERR_get_error()));
        return false;
    }

    int numWritten;
    if (additionalAuthenticatedData.size() > 0) {
        if (EVP_DecryptUpdate(ctx.get(), NULL, &numWritten,
                              additionalAuthenticatedData.data(),
                              additionalAuthenticatedData.size()) != 1) {
            COSE_PRINT_ERROR(
                    "EVP_DecryptUpdate: failed for "
                    "additionalAuthenticatedData, error 0x%lx\n",
                    static_cast<unsigned long>(ERR_get_error()));
            return false;
        }
        if ((size_t)numWritten != additionalAuthenticatedData.size()) {
            COSE_PRINT_ERROR(
                    "EVP_DecryptUpdate: Unexpected outl=%d "
                    "(expected %zd) for additionalAuthenticatedData\n",
                    numWritten, additionalAuthenticatedData.size());
            return false;
        }
    }

    if (EVP_DecryptUpdate(ctx.get(), plaintext, &numWritten, ciphertext,
                          ciphertextSize) != 1) {
        COSE_PRINT_ERROR("EVP_DecryptUpdate: failed, error 0x%lx\n",
                         static_cast<unsigned long>(ERR_get_error()));
        return false;
    }
    if (numWritten != ciphertextSize) {
        COSE_PRINT_ERROR(
                "EVP_DecryptUpdate: Unexpected outl=%d "
                "(expected %d)\n",
                numWritten, ciphertextSize);
        return false;
    }

    if (!EVP_CIPHER_CTX_ctrl(ctx.get(), EVP_CTRL_GCM_SET_TAG, kAesGcmTagSize,
                             tag)) {
        COSE_PRINT_ERROR(
                "EVP_CIPHER_CTX_ctrl: failed setting expected tag, "
                "error 0x%lx\n",
                static_cast<unsigned long>(ERR_get_error()));
        return false;
    }

    int ret =
            EVP_DecryptFinal_ex(ctx.get(), plaintext + numWritten, &numWritten);
    if (ret != 1) {
        COSE_PRINT_ERROR("EVP_DecryptFinal_ex: failed, error 0x%lx\n",
                         static_cast<unsigned long>(ERR_get_error()));
        return false;
    }
    if (numWritten != 0) {
        COSE_PRINT_ERROR("EVP_DecryptFinal_ex: Unexpected non-zero outl=%d\n",
                         numWritten);
        return false;
    }

    *outPlaintextSize = ciphertextSize;
    return true;
}

static bool coseDecryptAesGcmInPlace(const std::string_view context,
                                     const CoseByteView& item,
                                     const std::basic_string_view<uint8_t> key,
                                     const std::vector<uint8_t>& externalAad,
                                     const uint8_t** outPlaintextStart,
                                     size_t* outPlaintextSize,
                                     DecryptFn keyDecryptFn) {
    assert(outPlaintextStart != nullptr);
    assert(outPlaintextSize != nullptr);

    struct CborIn in;
    CborInInit(item.data(), item.size(), &in);

    size_t num_elements;
    if (CborReadArray(&in, &num_elements) != CBOR_READ_RESULT_OK) {
        COSE_PRINT_ERROR("Encrypted data is not a CBOR array\n");
        return false;
    }

    if (num_elements < 3 || num_elements > 4) {
        COSE_PRINT_ERROR("Invalid COSE encryption array size, got %zu\n",
                         num_elements);
        return false;
    }

    const uint8_t* enc_protected_headers_data;
    size_t enc_protected_headers_size;
    if (CborReadBstr(&in, &enc_protected_headers_size,
                     &enc_protected_headers_data) != CBOR_READ_RESULT_OK) {
        COSE_PRINT_ERROR(
                "Failed to retrieve protected headers "
                "from COSE encryption structure\n");
        return false;
    }

    struct CborIn protHdrIn;
    CborInInit(enc_protected_headers_data, enc_protected_headers_size,
               &protHdrIn);

    size_t numPairs;
    if (CborReadMap(&protHdrIn, &numPairs) != CBOR_READ_RESULT_OK) {
        COSE_PRINT_ERROR("Invalid protected headers CBOR type\n");
        return false;
    }

    int64_t label;
    std::optional<uint64_t> alg;
    for (size_t i = 0; i < numPairs; i++) {
        // Read key
        if (CborReadInt(&protHdrIn, &label) != CBOR_READ_RESULT_OK) {
            COSE_PRINT_ERROR(
                    "Failed to read protected headers "
                    "in COSE encryption structure\n");
            return false;
        }

        // Read value
        if (label == COSE_LABEL_ALG) {
            uint64_t algVal;
            if (CborReadUint(&protHdrIn, &algVal) != CBOR_READ_RESULT_OK) {
                COSE_PRINT_ERROR(
                        "Wrong CBOR type for alg value in unprotected headers\n");
                return false;
            }

            if (algVal != COSE_VAL_CIPHER_ALG) {
                COSE_PRINT_ERROR("Invalid COSE algorithm, got %" PRId64 "\n",
                                 algVal);
                return false;
            }

            alg = algVal;
        } else if (CborReadSkip(&protHdrIn) != CBOR_READ_RESULT_OK) {
            COSE_PRINT_ERROR(
                    "Failed to read protected headers "
                    "in COSE encryption structure\n");
            return false;
        }
    }

    if (CborReadMap(&in, &numPairs) != CBOR_READ_RESULT_OK) {
        COSE_PRINT_ERROR(
                "Failed to retrieve unprotected headers "
                "from COSE encryption structure\n");
        return false;
    }

    const uint8_t* ivData = nullptr;
    size_t ivSize;
    for (size_t i = 0; i < numPairs; i++) {
        // Read key
        if (CborReadInt(&in, &label) != CBOR_READ_RESULT_OK) {
            COSE_PRINT_ERROR(
                    "Failed to read unprotected headers "
                    "in COSE encryption structure\n");
            return false;
        }

        // Read value
        if (label == COSE_LABEL_IV) {
            if (CborReadBstr(&in, &ivSize, &ivData) != CBOR_READ_RESULT_OK) {
                COSE_PRINT_ERROR(
                        "Wrong CBOR type for IV value in unprotected headers\n");
                return false;
            }
        } else if (CborReadSkip(&in) != CBOR_READ_RESULT_OK) {
            COSE_PRINT_ERROR(
                    "Failed to read unprotected headers "
                    "in COSE encryption structure\n");
            return false;
        }
    }

    if (ivData == nullptr) {
        COSE_PRINT_ERROR("Missing IV field in COSE encryption structure\n");
        return false;
    }

    const uint8_t* ciphertextData;
    size_t ciphertextSize;
    if (CborReadBstr(&in, &ciphertextSize, &ciphertextData) !=
        CBOR_READ_RESULT_OK) {
        COSE_PRINT_ERROR(
                "Failed to retrieve ciphertext "
                "from COSE encryption structure\n");
        return false;
    }

    std::basic_string_view externalAadView{externalAad.data(),
                                           externalAad.size()};
    std::basic_string_view encodedProtectedHeaders{enc_protected_headers_data,
                                                   enc_protected_headers_size};
    auto [gcmAad, gcmAadSize] =
            coseBuildGcmAad(context, encodedProtectedHeaders, externalAadView);

    std::basic_string_view gcmAadView{gcmAad.get(), gcmAadSize};
    std::basic_string_view ivView{ivData, ivSize};
    if (!keyDecryptFn(key, ivView, const_cast<uint8_t*>(ciphertextData),
                      ciphertextSize, gcmAadView, outPlaintextSize)) {
        return false;
    }

    *outPlaintextStart = ciphertextData;

    return true;
}

bool coseDecryptAesGcmKeyWrapInPlace(const CoseByteView& cose_encrypt,
                                     GetKeyFn keyFn,
                                     const std::vector<uint8_t>& externalAad,
                                     bool checkTag,
                                     const uint8_t** outPackageStart,
                                     size_t* outPackageSize,
                                     DecryptFn keyDecryptFn) {
    assert(outPackageStart != nullptr);
    assert(outPackageSize != nullptr);

    if (!keyDecryptFn) {
        keyDecryptFn = &decryptAesGcmInPlace;
    }

    struct CborIn in;
    CborInInit(cose_encrypt.data(), cose_encrypt.size(), &in);

    uint64_t tag;
    if (CborReadTag(&in, &tag) == CBOR_READ_RESULT_OK) {
        if (checkTag && tag != COSE_TAG_ENCRYPT) {
            TLOGE("Invalid COSE_Encrypt semantic tag: %" PRIu64 "\n", tag);
            return false;
        }
    } else if (checkTag) {
        TLOGE("Expected COSE_Encrypt semantic tag\n");
        return false;
    }

    size_t num_elements;
    if (CborReadArray(&in, &num_elements) != CBOR_READ_RESULT_OK) {
        COSE_PRINT_ERROR("Encrypted data is not a CBOR array\n");
        return false;
    }

    if (num_elements != kCoseEncryptArrayElements) {
        COSE_PRINT_ERROR("Invalid COSE_Encrypt array size, got %zu\n",
                         num_elements);
        return false;
    }

    // Skip past the first three array elemements
    while (num_elements-- > 1) {
        if (CborReadSkip(&in) != CBOR_READ_RESULT_OK) {
            COSE_PRINT_ERROR(
                    "Failed to retrieve recipients "
                    "from COSE_Encrypt structure\n");
            return false;
        }
    }

    // Read recipients array
    if (CborReadArray(&in, &num_elements) != CBOR_READ_RESULT_OK) {
        COSE_PRINT_ERROR(
                "Failed to retrieve recipients "
                "from COSE_Encrypt structure\n");
        return false;
    }

    if (num_elements != 1) {
        COSE_PRINT_ERROR("Invalid recipients array size, got %zu\n",
                         num_elements);
        return false;
    }

    const size_t recipientOffset = CborInOffset(&in);
    // Read singleton recipient
    if (CborReadArray(&in, &num_elements) != CBOR_READ_RESULT_OK) {
        COSE_PRINT_ERROR("COSE_Recipient is not a CBOR array\n");
        return false;
    }

    if (num_elements != 3) {
        COSE_PRINT_ERROR(
                "Invalid COSE_Recipient structure array size, "
                "got %zu\n",
                num_elements);
        return false;
    }

    // Skip to unprotected headers array element
    if (CborReadSkip(&in) != CBOR_READ_RESULT_OK) {
        COSE_PRINT_ERROR("Failed to read COSE_Recipient structure\n");
        return false;
    }

    size_t numPairs;
    if (CborReadMap(&in, &numPairs) != CBOR_READ_RESULT_OK) {
        COSE_PRINT_ERROR(
                "Failed to retrieve unprotected headers "
                "from COSE_Recipient structure\n");
        return false;
    }

    uint64_t label;
    const uint8_t* keyIdBytes = nullptr;
    size_t keyIdSize;
    for (size_t i = 0; i < numPairs; i++) {
        // Read key
        if (CborReadUint(&in, &label) != CBOR_READ_RESULT_OK) {
            COSE_PRINT_ERROR(
                    "Failed to read unprotected headers "
                    "in COSE_Recipient structure\n");
            return false;
        }

        // Read value
        if (label == COSE_LABEL_KID) {
            if (CborReadBstr(&in, &keyIdSize, &keyIdBytes) !=
                CBOR_READ_RESULT_OK) {
                COSE_PRINT_ERROR(
                        "Failed to extract key id from unprotected headers "
                        "in COSE_Recipient structure\n");
                return false;
            }
        } else if (CborReadSkip(&in) != CBOR_READ_RESULT_OK) {
            COSE_PRINT_ERROR(
                    "Failed to read unprotected headers "
                    "in COSE_Recipient structure\n");
            return false;
        }
    }

    // Skip over ciphertext
    if (CborReadSkip(&in) != CBOR_READ_RESULT_OK) {
        COSE_PRINT_ERROR("Failed to read COSE_Recipient structure\n");
        return false;
    }

    if (!CborInAtEnd(&in)) {
        COSE_PRINT_ERROR("Failed to read COSE_Recipient structure\n");
        return false;
    }

    CoseByteView recipient = {cose_encrypt.data() + recipientOffset,
                              CborInOffset(&in) - recipientOffset};

    if (keyIdBytes == nullptr) {
        COSE_PRINT_ERROR("Missing key id field in COSE_Recipient\n");
        return false;
    }

    if (keyIdSize != 1) {
        COSE_PRINT_ERROR("Invalid key id field length, got %zu\n", keyIdSize);
        return false;
    }

    auto [keyEncryptionKeyStart, keyEncryptionKeySize] = keyFn(keyIdBytes[0]);
    if (!keyEncryptionKeyStart) {
        COSE_PRINT_ERROR("Failed to retrieve decryption key\n");
        return false;
    }

    std::basic_string_view<uint8_t> keyEncryptionKey{
            keyEncryptionKeyStart.get(), keyEncryptionKeySize};

    const uint8_t* coseKeyStart;
    size_t coseKeySize;
    if (!coseDecryptAesGcmInPlace(COSE_CONTEXT_ENC_RECIPIENT, recipient,
                                  keyEncryptionKey, {}, &coseKeyStart,
                                  &coseKeySize, keyDecryptFn)) {
        COSE_PRINT_ERROR("Failed to decrypt COSE_Key structure\n");
        return false;
    }

    CborInInit(coseKeyStart, coseKeySize, &in);
    if (CborReadMap(&in, &numPairs) != CBOR_READ_RESULT_OK) {
        COSE_PRINT_ERROR("COSE_Key structure is not a map\n");
        return false;
    }

    int64_t keyLabel;
    int64_t value;
    bool ktyValidated = false;
    bool algValidated = false;
    const uint8_t* contentEncryptionKeyStart = nullptr;
    size_t contentEncryptionKeySize = 0;
    for (size_t i = 0; i < numPairs; i++) {
        if (CborReadInt(&in, &keyLabel) != CBOR_READ_RESULT_OK) {
            COSE_PRINT_ERROR("Failed to parse key in COSE_Key structure\n");
            return false;
        }

        switch (keyLabel) {
        case COSE_LABEL_KEY_KTY:
            if (CborReadInt(&in, &value) != CBOR_READ_RESULT_OK) {
                COSE_PRINT_ERROR("Wrong CBOR type for kty field of COSE_Key\n");
                return false;
            }
            if (value != COSE_KEY_TYPE_SYMMETRIC) {
                COSE_PRINT_ERROR("Invalid COSE_Key key type: %" PRId64 "\n",
                                 value);
                return false;
            }
            ktyValidated = true;
            break;
        case COSE_LABEL_KEY_ALG:
            if (CborReadInt(&in, &value) != CBOR_READ_RESULT_OK) {
                COSE_PRINT_ERROR("Wrong CBOR type for kty field of COSE_Key\n");
                return false;
            }
            if (value != COSE_VAL_CIPHER_ALG) {
                COSE_PRINT_ERROR("Invalid COSE_Key algorithm value: %" PRId64
                                 "\n",
                                 value);
                return false;
            }
            algValidated = true;
            break;
        case COSE_LABEL_KEY_SYMMETRIC_KEY:
            if (CborReadBstr(&in, &contentEncryptionKeySize,
                             &contentEncryptionKeyStart)) {
                COSE_PRINT_ERROR("Wrong CBOR type for key field of COSE_Key\n");
                return false;
            }
            if (contentEncryptionKeySize != kAesGcmKeySize) {
                COSE_PRINT_ERROR(
                        "Invalid content encryption key size, got %zu\n",
                        contentEncryptionKeySize);
                return false;
            }
            break;
        default:
            COSE_PRINT_ERROR("Invalid key field in COSE_Key: %" PRId64 "\n",
                             label);
            return false;
            break;
        }
    }

    if (!ktyValidated) {
        COSE_PRINT_ERROR("Missing kty field of COSE_Key\n");
        return false;
    } else if (!algValidated) {
        COSE_PRINT_ERROR("Missing alg field of COSE_Key\n");
        return false;
    } else if (!contentEncryptionKeyStart) {
        COSE_PRINT_ERROR("Missing key field in COSE_Key\n");
        return false;
    }

    const CoseByteView contentEncryptionKey = {contentEncryptionKeyStart,
                                               contentEncryptionKeySize};
    if (!coseDecryptAesGcmInPlace(COSE_CONTEXT_ENCRYPT, cose_encrypt,
                                  contentEncryptionKey, externalAad,
                                  outPackageStart, outPackageSize,
                                  decryptAesGcmInPlace)) {
        COSE_PRINT_ERROR("Failed to decrypt payload\n");
        return false;
    }

    return true;
}

const char* coseGetCipherAlg(void) {
#ifdef APPLOADER_PACKAGE_CIPHER_A256
    return "AES-GCM with 256-bit key, 128-bit tag";
#else
    return "AES-GCM with 128-bit key, 128-bit tag";
#endif
}

const char* coseGetSigningDsa(void) {
#ifdef APPLOADER_PACKAGE_SIGN_P384
    return "ECDA P-384 + SHA-384 signatures";
#else
    return "ECDA P-256 + SHA-256 signatures";
#endif
}
