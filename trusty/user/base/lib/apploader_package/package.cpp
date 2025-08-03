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

#define TLOG_TAG "apploader-package"

#include <apploader/cbor.h>
#include <apploader/cose.h>
#include <apploader/package.h>
#include <assert.h>
#include <dice/cbor_reader.h>
#include <dice/cbor_writer.h>
#include <interface/apploader/apploader_package.h>
#include <interface/hwkey/hwkey.h>
#include <inttypes.h>
#include <lib/apploader_policy_engine/apploader_policy_engine.h>
#include <lib/hwaes/hwaes.h>
#include <lib/hwkey/hwkey.h>
#include <lk/compiler.h>
#include <stdbool.h>
#include <stddef.h>
#include <sys/mman.h>
#include <trusty/memref.h>
#include <trusty_log.h>
#include <uapi/err.h>
#include <optional>

/*
 * Maximum size of any key we could possibly get from hwkey.
 * If the latter returns a key larger than this, validation fails.
 * For now, 128 bytes should be enough since the apploader only
 * supports 256-bit (P-256) ECDSA signatures which only need
 * about 90 bytes for their public keys. If other curves or algorithms
 * e.g., P-521 or RSS, are supported by the apploader at a later time,
 * this value will need to increase.
 */
constexpr uint32_t kMaximumKeySize =
        std::max(128, HWKEY_OPAQUE_HANDLE_MAX_SIZE);

static std::tuple<std::unique_ptr<uint8_t[]>, size_t>
get_key(hwkey_session_t hwkey_session, std::string_view op, uint8_t key_id) {
    std::string key_slot{"com.android.trusty.apploader."};
    key_slot += op;
    key_slot += ".key.";
    key_slot += std::to_string(static_cast<unsigned>(key_id));

    uint32_t key_size = kMaximumKeySize;
    std::unique_ptr<uint8_t[]> result(new (std::nothrow) uint8_t[key_size]());
    if (!result) {
        TLOGE("Failed to allocate memory for key\n");
        return {};
    }

    long rc = hwkey_get_keyslot_data(hwkey_session, key_slot.c_str(),
                                     result.get(), &key_size);
    if (rc < 0) {
        TLOGE("Failed to get key %" PRIu8 " from hwkey (%ld)\n", key_id, rc);
        return {};
    }

    return {std::move(result), static_cast<size_t>(key_size)};
}

/*
 * strictCheckEcDsaSignature requires a function pointer that returns a
 * unique_ptr, so we wrap app_policy_engine_get_key().
 * This will store the key into two places: in *publicKeyPtr, and as a
 * unique_ptr (which wraps a second copy of the key). The caller must free
 * *publicKeyPtr.
 */
static std::tuple<std::unique_ptr<uint8_t[]>, size_t> get_sign_key(
        uint8_t key_id,
        const uint8_t** public_key_ptr,
        unsigned int* public_key_size_ptr) {
    int rc = apploader_policy_engine_get_key(key_id, public_key_ptr,
                                             public_key_size_ptr);
    if (rc < 0) {
        TLOGE("Failed to get key %" PRIu8 " from policy engine (%d)\n", key_id,
              rc);
        return {};
    }

    std::unique_ptr<uint8_t[]> result(new (std::nothrow)
                                              uint8_t[*public_key_size_ptr]());
    if (!result) {
        TLOGE("Failed to allocate memory for key\n");
        return {};
    }

    memcpy(result.get(), *public_key_ptr, *public_key_size_ptr);

    return {std::move(result), static_cast<size_t>(*public_key_size_ptr)};
}

static bool hwaesDecryptAesGcmInPlace(
        std::basic_string_view<uint8_t> key,
        std::basic_string_view<uint8_t> nonce,
        uint8_t* encryptedData,
        size_t encryptedDataSize,
        std::basic_string_view<uint8_t> additionalAuthenticatedData,
        size_t* outPlaintextSize) {
    assert(outPlaintextSize != nullptr);
    if (encryptedDataSize <= kAesGcmTagSize) {
        TLOGE("encryptedData too small\n");
        return false;
    }

    if (nonce.size() != kAesGcmIvSize) {
        TLOGE("nonce is not kAesGcmIvSize bytes, got %zu\n", nonce.size());
        return false;
    }

    size_t ciphertextSize = encryptedDataSize - kAesGcmTagSize;
    unsigned char* tag = encryptedData + ciphertextSize;

    struct hwcrypt_args cryptArgs = {};
    cryptArgs.key.data_ptr = key.data();
    cryptArgs.key.len = key.size();
    cryptArgs.iv.data_ptr = nonce.data();
    cryptArgs.iv.len = nonce.size();
    cryptArgs.aad.data_ptr = additionalAuthenticatedData.data();
    cryptArgs.aad.len = additionalAuthenticatedData.size();
    cryptArgs.tag_in.data_ptr = tag;
    cryptArgs.tag_in.len = kAesGcmTagSize;
    cryptArgs.text_in.data_ptr = encryptedData;
    cryptArgs.text_in.len = ciphertextSize;
    cryptArgs.text_out.data_ptr = encryptedData;
    cryptArgs.text_out.len = ciphertextSize;
    cryptArgs.key_type = HWAES_OPAQUE_HANDLE;
    cryptArgs.padding = HWAES_NO_PADDING;
    cryptArgs.mode = HWAES_GCM_MODE;

    hwaes_session_t sess;
    auto ret = hwaes_open(&sess);
    if (ret != NO_ERROR) {
        return false;
    }

    ret = hwaes_decrypt(sess, &cryptArgs);
    if (ret == NO_ERROR) {
        *outPlaintextSize = ciphertextSize;
    }
    hwaes_close(sess);

    return ret == NO_ERROR;
}

/**
 * apploader_parse_package_metadata - Parse an apploader package into a
 *                                    metadata structure
 * @package:        Pointer to the start of the package
 * @package_size:   Size of the package in bytes
 * @metadata:       Pointer to output &struct apploader_package_metadata
 *                  structure
 *
 * This function parses an apploader package and fills the contents of a given
 * &struct apploader_package_metadata.
 *
 * The function expects an application package encoded using CBOR. The concrete
 * format of the package is as follows: each package is encoded as a CBOR array
 * with tag %APPLOADER_PACKAGE_CBOR_TAG_APP and the following elements:
 * * ```version:int```:
 *      Version number of the package format.
 *      Equal to %APPLOADER_PACKAGE_FORMAT_VERSION_CURRENT.
 * * ```headers:map```:
 *      Map containing a series of optional values and flags.
 *      The keys are labels from &enum apploader_package_header_label.
 * * ```contents```:
 *      The contents of the ELF file. This element is a CBOR ```bstr```
 *      if the ELF file is not encrypted, or a ```COSE_Encrypt``` structure
 *      if it is encrypted.
 * * ```manifest:bstr```:
 *      The contents of the manifest file.
 *
 * Return: %false is an error is detected, %true otherwise.
 */
bool apploader_parse_package_metadata(
        uint8_t* package_start,
        size_t package_size,
        struct apploader_package_metadata* metadata) {
    /*
     * This lambda will store the signing key into metadata->publicKey, and
     * also return a separate copy (wrapped in a unique_ptr) that is consumed
     * by strictCheckEcDsaSignature.
     */
    auto local_get_sign_key = [metadata](int key_id) {
        return get_sign_key(key_id, &(metadata->public_key),
                            &(metadata->public_key_size));
    };

    const uint8_t* unsigned_package_start;
    size_t unsigned_package_size;
    if (!strictCheckEcDsaSignature(package_start, package_size,
                                   local_get_sign_key, &unsigned_package_start,
                                   &unsigned_package_size)) {
        TLOGE("Package signature verification failed\n");
        return false;
    }

    struct CborIn in;
    CborInInit(unsigned_package_start, unsigned_package_size, &in);

    uint64_t tag;
    if (CborReadTag(&in, &tag) != CBOR_READ_RESULT_OK) {
        TLOGE("Invalid package, failed to read semantic tag\n");
        return false;
    }

    if (tag != APPLOADER_PACKAGE_CBOR_TAG_APP) {
        TLOGE("Invalid package semantic tag: %" PRIu64 "\n", tag);
        return false;
    }

    size_t num_elements;
    if (CborReadArray(&in, &num_elements) != CBOR_READ_RESULT_OK) {
        TLOGE("Expected CBOR array\n");
        return false;
    }

    if (num_elements == 0) {
        TLOGE("Application package array is empty\n");
        return false;
    }

    uint64_t version;
    if (CborReadUint(&in, &version) != CBOR_READ_RESULT_OK) {
        TLOGE("Invalid version field CBOR type\n");
        return false;
    }

    if (version != APPLOADER_PACKAGE_FORMAT_VERSION_CURRENT) {
        TLOGE("Invalid package version, expected %" PRIu64 " got %" PRIu64 "\n",
              APPLOADER_PACKAGE_FORMAT_VERSION_CURRENT, version);
        return false;
    }

    if (num_elements != APPLOADER_PACKAGE_CBOR_ARRAY_SZ) {
        TLOGE("Invalid number of CBOR array elements: %zd\n", num_elements);
        return false;
    }

    /* Read headers and reject packages with invalid header labels */
    metadata->elf_is_cose_encrypt = false;

    size_t num_pairs;
    if (CborReadMap(&in, &num_pairs) != CBOR_READ_RESULT_OK) {
        TLOGE("Invalid headers CBOR type, expected map\n");
        return false;
    }

    uint64_t label;
    for (size_t i = 0; i < num_pairs; i++) {
        /* Read key */
        if (CborReadUint(&in, &label) != CBOR_READ_RESULT_OK) {
            fprintf(stderr, "Invalid headers CBOR type, expected uint\n");
            exit(EXIT_FAILURE);
        }

        /* Read value */
        switch (label) {
        case APPLOADER_PACKAGE_HEADER_LABEL_CONTENT_IS_COSE_ENCRYPT: {
            auto val = cbor::readCborBoolean(in);
            if (!val.has_value()) {
                fprintf(stderr,
                        "Invalid headers CBOR type, expected boolean\n");
                exit(EXIT_FAILURE);
            }
            metadata->elf_is_cose_encrypt = *val;
            break;
        }

        default:
            TLOGE("Package headers contain invalid label: %" PRIu64 "\n",
                  label);
            return false;
        }
    }

    const uint8_t* elf_start;
    size_t elf_size;
    if (metadata->elf_is_cose_encrypt) {
        long rc = hwkey_open();
        if (rc < 0) {
            TLOGE("Failed to connect to hwkey (%ld)\n", rc);
            return false;
        }

        hwkey_session_t hwkey_session = static_cast<hwkey_session_t>(rc);

        /*
         * get the encryption key handle but keep the hwkey connection open
         * until we've finished decrypting with it
         */
        auto get_encrypt_key_handle = [hwkey_session](uint8_t key_id) {
            return get_key(hwkey_session, "encrypt", key_id);
        };

        const size_t cose_encrypt_offset = CborInOffset(&in);
        const uint8_t* cose_encrypt_start =
                unsigned_package_start + cose_encrypt_offset;

        /*
         * The COSE_Encrypt structure can be encoded as either tagged or
         * untagged depending on the context it will be used in.
         */
        if (CborReadTag(&in, &tag) != CBOR_READ_RESULT_OK) {
            TLOGD("COSE_Encrypt content did not contain a semantic tag\n");
        }

        if (CborReadArray(&in, &num_elements) != CBOR_READ_RESULT_OK) {
            TLOGE("Invalid COSE_Encrypt, expected an array\n");
            return false;
        }

        if (num_elements != kCoseEncryptArrayElements) {
            TLOGE("Invalid COSE_Encrypt, number of CBOR array elements: %zd\n",
                  num_elements);
            return false;
        }

        /* Skip to the end of the four element array */
        for (size_t i = 0; i < num_elements; i++) {
            if (CborReadSkip(&in) != CBOR_READ_RESULT_OK) {
                TLOGE("Failed to skip to the end of COSE_Encrypt structure\n");
                return false;
            }
        }

        auto cose_encrypt_size = CborInOffset(&in) - cose_encrypt_offset;

        const CoseByteView cose_encrypt = {cose_encrypt_start,
                                           cose_encrypt_size};
        bool success = coseDecryptAesGcmKeyWrapInPlace(
                cose_encrypt, get_encrypt_key_handle, {}, false, &elf_start,
                &elf_size, hwaesDecryptAesGcmInPlace);

        hwkey_close(hwkey_session);

        if (!success) {
            TLOGE("Failed to decrypt ELF file\n");
            return false;
        }
    } else {
        if (CborReadBstr(&in, &elf_size, &elf_start) != CBOR_READ_RESULT_OK) {
            TLOGE("Invalid ELF CBOR type\n");
            return false;
        }
    }

    if (CborReadBstr(&in, &metadata->manifest_size,
                     &metadata->manifest_start) != CBOR_READ_RESULT_OK) {
        TLOGE("Invalid manifest CBOR type\n");
        return false;
    }

    metadata->elf_start = elf_start;
    metadata->elf_size = elf_size;

    return true;
}
