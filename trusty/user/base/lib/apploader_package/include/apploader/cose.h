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

#pragma once

#include <stddef.h>
#include <optional>
#include <string_view>
#include <vector>

// From https://tools.ietf.org/html/rfc8152
constexpr int COSE_LABEL_ALG = 1;
constexpr int COSE_LABEL_KID = 4;
constexpr int COSE_LABEL_IV = 5;
constexpr int COSE_LABEL_KEY_KTY = 1;
constexpr int COSE_LABEL_KEY_ALG = 3;
constexpr int COSE_LABEL_KEY_SYMMETRIC_KEY = -1;
constexpr int COSE_TAG_ENCRYPT = 96;
constexpr int COSE_TAG_SIGN1 = 18;
constexpr int COSE_KEY_TYPE_SYMMETRIC = 4;

constexpr char COSE_CONTEXT_ENCRYPT[] = "Encrypt";
constexpr char COSE_CONTEXT_ENC_RECIPIENT[] = "Enc_Recipient";

// From "COSE Algorithms" registry
// See: RFC9053 or https://www.iana.org/assignments/cose/cose.txt
constexpr int COSE_ALG_A128GCM = 1;
constexpr int COSE_ALG_A256GCM = 2;
constexpr int COSE_ALG_ECDSA_256 = -7;
constexpr int COSE_ALG_ECDSA_384 = -35;

// Trusty-specific COSE constants
constexpr int COSE_LABEL_TRUSTY = -65537;

#ifdef APPLOADER_PACKAGE_CIPHER_A256
constexpr int COSE_VAL_CIPHER_ALG = COSE_ALG_A256GCM;
#else
constexpr int COSE_VAL_CIPHER_ALG = COSE_ALG_A128GCM;
#endif

#ifdef APPLOADER_PACKAGE_SIGN_P384
constexpr int COSE_VAL_SIGN_ALG = COSE_ALG_ECDSA_384;
#else
constexpr int COSE_VAL_SIGN_ALG = COSE_ALG_ECDSA_256;
#endif

constexpr size_t kAesGcmIvSize = 12;
constexpr size_t kAesGcmTagSize = 16;
#ifdef APPLOADER_PACKAGE_CIPHER_A256
constexpr size_t kAesGcmKeySize = 32;
#else
constexpr size_t kAesGcmKeySize = 16;
#endif
constexpr size_t kCoseEncryptArrayElements = 4;

using CoseByteView = std::basic_string_view<uint8_t>;

using GetKeyFn =
        std::function<std::tuple<std::unique_ptr<uint8_t[]>, size_t>(uint8_t)>;
using DecryptFn = std::function<bool(CoseByteView key,
                                     CoseByteView nonce,
                                     uint8_t* encryptedData,
                                     size_t encryptedDataSize,
                                     CoseByteView additionalAuthenticatedData,
                                     size_t* outPlaintextSize)>;

/**
 * coseSetSilenceErrors() - Enable or disable the silencing of errors;
 * @value: New value of the flag, %true if errors should be silenced.
 *
 * Return: the old value of the flag.
 */
bool coseSetSilenceErrors(bool value);

/**
 * coseSignEcDsa() - Sign the given data using ECDSA and emit a COSE CBOR blob.
 * @key:
 *      DER-encoded private key.
 * @keyId:
 *      Key identifier, an unsigned 1-byte integer.
 * @data:
 *      Block of data to sign and optionally encode inside the COSE signature
 *      structure.
 * @protectedHeaders:
 *      Protected headers for the COSE structure. The function may add its own
 *      additional entries.
 * @unprotectedHeaders:
 *      Unprotected headers for the COSE structure. The function may add its
 *      own additional entries.
 * @detachContent:
 *      Whether to detach the data, i.e., not include @data in the returned
 *      ```COSE_Sign1``` structure.
 * @tagged:
 *      Whether to return the tagged ```COSE_Sign1_Tagged``` or the untagged
 *      ```COSE_Sign1``` structure.
 *
 * This function signs a given block of data with ECDSA-SHA256 and encodes both
 * the data and the signature using the COSE encoding from RFC 8152. The caller
 * may specify whether the data is included or detached from the returned
 * structure using the @detachContent parameter, as well as additional
 * context-specific header values with the @protectedHeaders and
 * @unprotectedHeaders parameters.
 *
 * Return: A vector containing the encoded ```COSE_Sign1``` structure if the
 *         signing algorithm succeeds, or a nullopt otherwise.
 */
std::optional<std::vector<uint8_t>> coseSignEcDsa(
        const std::vector<uint8_t>& key,
        uint8_t keyId,
        const std::vector<uint8_t>& data,
        const std::basic_string_view<uint8_t>& encodedProtectedHeaders,
        std::basic_string_view<uint8_t>&,
        bool detachContent,
        bool tagged);

/**
 * coseIsSigned() - Check if a block of bytes is a COSE signature emitted
 *                  by coseSignEcDsa().
 * @data:            Input data.
 * @signatureLength: If not NULL, output argument where the total length
 *                   of the signature structure will be stored.
 *
 * This function checks if the given data is a COSE signature structure
 * emitted by coseSignEcDsa(), and returns the size of the signature if needed.
 *
 * Return: %true if the signature structure is valid, %false otherwise.
 */
bool coseIsSigned(CoseByteView data, size_t* signatureLength);

/**
 * coseCheckEcDsaSignature() - Check if a given COSE signature structure is
 *                             valid.
 * @signatureCoseSign1: Input COSE signature structure.
 * @detachedContent:    Additional data to include in the signature.
 *                      Corresponds to the @detachedContent parameter passed to
 *                      coseSignEcDsa().
 * @publicKey:          Public key in DER encoding.
 *
 * Returns: %true if the signature verification passes, %false otherwise.
 */
bool coseCheckEcDsaSignature(const std::vector<uint8_t>& signatureCoseSign1,
                             const std::vector<uint8_t>& detachedContent,
                             const std::vector<uint8_t>& publicKey);

/**
 * strictCheckEcDsaSignature() - Check a given COSE signature in strict mode.
 * @packageStart:       Pointer to the start of the signed input package.
 * @packageSize:        Size of the signed input package.
 * @keyFn:              Function to call with a key id that returns the public
 *                      key for that id.
 * @outPackageStart:    If not NULL, output argument where the start of the
 *                      payload will be stored.
 * @outPackageSize:     If not NULL, output argument where the size of the
 *                      payload will be stored.
 *
 * This function performs a strict verification of the COSE signature of a
 * package. Instead of parsing the COSE structure, the function compares the
 * raw bytes against a set of exact patterns, and fails if the bytes do not
 * match. The actual signature and payload are also assumed to start at fixed
 * offsets from @packageStart.
 *
 * Returns: %true if the signature verification passes, %false otherwise.
 */
bool strictCheckEcDsaSignature(const uint8_t* packageStart,
                               size_t packageSize,
                               GetKeyFn keyFn,
                               const uint8_t** outPackageStart,
                               size_t* outPackageSize);

/**
 * coseEncryptAes128GcmKeyWrap() - Encrypt a block of data using AES-GCM
 *                                 and a randomly-generated wrapped CEK.
 * @key:
 *      Key encryption key (KEK), 16 or 32 bytes in size.
 * @keyId:
 *      Key identifier for the KEK, an unsigned 1-byte integer.
 * @data:
 *      Input data to encrypt.
 * @externalAad:
 *      Additional authentication data to pass to AES-GCM.
 * @protectedHeaders:
 *      Protected headers for the COSE structure. The function may add its own
 *      additional entries.
 * @unprotectedHeaders:
 *      Unprotected headers for the COSE structure. The function may add its
 *      own additional entries.
 * @tagged:
 *      Whether to return the tagged ```COSE_Encrypt_Tagged``` or the untagged
 *      ```COSE_Encrypt``` structure.
 *
 * This function generates a random key content encryption key (CEK) and wraps
 * it using AES-GCM, then encrypts a given block of data with AES-GCM
 * with the wrapped CEK and encodes both the data and CEK using the COSE
 * encoding from RFC 8152.
 *
 * The key length must be 128 or 256 depending on build-time configuration.
 * The IV and Tag lengths are fixed (128-bit and 96-bits respectively, see
 * ```kAesGcmIvSize``` and ```kAesGcmTagSize```).
 *
 * The caller may specify additional context-specific header values with the
 * @protectedHeaders and @unprotectedHeaders parameters.
 *
 * Return: A vector of bytes containing the encoded ```COSE_Encrypt``` structure
 *         if the encryption succeeds, or a nullopt otherwise.
 */
std::optional<std::vector<uint8_t>> coseEncryptAesGcmKeyWrap(
        const std::vector<uint8_t>& key,
        uint8_t keyId,
        const CoseByteView& data,
        const std::vector<uint8_t>& externalAad,
        const std::vector<uint8_t>& encodedProtectedHeaders,
        const CoseByteView& unprotectedHeaders,
        bool tagged);

/**
 * coseDecryptAesGcmKeyWrapInPlace() - Decrypt a block of data containing a
 *                                     wrapped key using AES-GCM.
 * @item:               CBOR item containing a ```COSE_Encrypt``` structure.
 * @keyFn:              Function to call with a key id that returns the key
 *                      encryption key (KEK) for that id.
 * @externalAad:        Additional authentication data to pass to AES-GCM.
 * @checkTag:           Whether to check the CBOR semantic tag of @item.
 * @outPackageStart:    The output argument where the start of the
 *                      payload will be stored. Must not be %NULL.
 * @outPackageSize:     The output argument where the size of the
 *                      payload will be stored. Must not be %NULL.
 *
 * This function decrypts a ciphertext encrypted with AES-GCM and encoded
 * in a ```COSE_Encrypt0_Tagged``` structure. The function performs in-place
 * decryption and overwrites the ciphertext with the plaintext, and returns
 * the pointer and size of the plaintext in @outPackageStart and
 * @outPackageSize, respectively.
 * The key length is 128 or 256 depending on build-time configuration.  The IV
 * and Tag lengths are fixed (128-bit and 96-bits respectively).
 *
 * Returns: %true if the decryption succeeds, %false otherwise.
 */
bool coseDecryptAesGcmKeyWrapInPlace(const CoseByteView& item,
                                     GetKeyFn keyFn,
                                     const std::vector<uint8_t>& externalAad,
                                     bool checkTag,
                                     const uint8_t** outPackageStart,
                                     size_t* outPackageSize,
                                     DecryptFn decryptFn = DecryptFn());

/**
 * coseGetCipherAlg - Get the name of the cipher for package encryption.
 * Returns: A pointer to a static string describing the cipher.
 */
const char* coseGetCipherAlg(void);

/**
 * coseGetSigningDsa() - Get the name of the signing Digital Signature Algo.
 * Returns: A pointer to a static string describing the signing method.
 */
const char* coseGetSigningDsa(void);
