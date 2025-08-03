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

#include <apploader/cbor.h>
#include <apploader/cose.h>
#include <dice/cbor_reader.h>
#include <dice/cbor_writer.h>
#include <endian.h>
#include <fcntl.h>
#include <getopt.h>
#include <interface/apploader/apploader_package.h>
#include <inttypes.h>
#ifdef WITH_APPLOADER_POLICY_ENGINE
#include <lib/apploader_policy_engine/apploader_policy_engine.h>
#endif
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <uapi/err.h>
#include <unistd.h>
#include <array>
#include <fstream>
#include <memory>
#include <sstream>
#include <string>
#include <vector>

#include "../app_manifest_parser.h"

#ifdef WITH_APPLOADER_POLICY_ENGINE
/* Global variable to support apploader policy mocking */
bool host_mock_state_app_loading_unlocked = false;
#endif

enum class Mode {
    UNKNOWN,
    BUILD,
    SIGN,
    VERIFY,
    ENCRYPT,
    DECRYPT,
    INFO,
#ifdef WITH_APPLOADER_POLICY_ENGINE
    KEYS,
#endif
};

static Mode mode = Mode::UNKNOWN;
static bool strict = false;

static const char* _sopts = "hm:s";
static const struct option _lopts[] = {
        {"help", no_argument, 0, 'h'},
        {"mode", required_argument, 0, 'm'},
        {"strict", no_argument, 0, 's'},
        {0, 0, 0, 0},
};

static void print_usage_and_exit(const char* prog, int code) {
    fprintf(stderr, "Usage:\n");
    fprintf(stderr, "\t%s --mode <mode> [options] ...\n", prog);
    fprintf(stderr, "\t%s --mode build [options] <output> <ELF> <manifest>\n",
            prog);
    fprintf(stderr,
            "\t%s --mode sign [options] <output> <input> <key> <key id>\n",
            prog);
    fprintf(stderr, "\t%s --mode verify [options] <input> <key>\n", prog);
    fprintf(stderr,
            "\t%s --mode encrypt [options] <output> <input> <key> <key id>\n",
            prog);
    fprintf(stderr, "\t%s --mode decrypt [options] <output> <input> <key>\n",
            prog);
    fprintf(stderr, "\t%s --mode info [options] <input>\n", prog);
#ifdef WITH_APPLOADER_POLICY_ENGINE
    fprintf(stderr, "\t%s --mode keys\n", prog);
#endif
    fprintf(stderr, "\n");
    fprintf(stderr, "Options:\n");
    fprintf(stderr, "\t-h, --help            prints this message and exit\n");
    fprintf(stderr,
            "\t-m, --mode            mode; one of: build, sign, verify, encrypt, decrypt, info\n");
#ifdef WITH_APPLOADER_POLICY_ENGINE
    fprintf(stderr, "\t                                    keys\n");

#endif
    fprintf(stderr,
            "\t-s, --strict          verify signature in strict mode\n");
    fprintf(stderr, "\n");
    fprintf(stderr, "Exit Code:\n");
    fprintf(stderr, "  0 on success, otherwise a non-zero error code.\n");
    fprintf(stderr,
            "\n"
            "  For info mode, it is considered an error if a package manifest requires\n"
            "  app encryption, but the package is not encrypted.\n");
    fprintf(stderr, "\n");
    fprintf(stderr, "Build:\n");
    fprintf(stderr, "  Cipher:  %s\n", coseGetCipherAlg());
    fprintf(stderr, "  Signing: %s\n", coseGetSigningDsa());
    fprintf(stderr, "\n");
    exit(code);
}

static void parse_options(int argc, char** argv) {
    int c;
    int oidx = 0;

    while (1) {
        c = getopt_long(argc, argv, _sopts, _lopts, &oidx);
        if (c == -1) {
            break; /* done */
        }

        switch (c) {
        case 'h':
            print_usage_and_exit(argv[0], EXIT_SUCCESS);
            break;

        case 'm':
            if (!strcmp(optarg, "build")) {
                mode = Mode::BUILD;
            } else if (!strcmp(optarg, "sign")) {
                mode = Mode::SIGN;
            } else if (!strcmp(optarg, "verify")) {
                mode = Mode::VERIFY;
            } else if (!strcmp(optarg, "encrypt")) {
                mode = Mode::ENCRYPT;
            } else if (!strcmp(optarg, "decrypt")) {
                mode = Mode::DECRYPT;
            } else if (!strcmp(optarg, "info")) {
                mode = Mode::INFO;
#ifdef WITH_APPLOADER_POLICY_ENGINE
            } else if (!strcmp(optarg, "keys")) {
                mode = Mode::KEYS;
#endif
            } else {
                fprintf(stderr, "Unrecognized command mode: %s\n", optarg);
                /*
                 * Set the mode to UNKNOWN so main prints the usage and exits
                 */
                mode = Mode::UNKNOWN;
            }
            break;

        case 's':
            strict = true;
            break;

        default:
            print_usage_and_exit(argv[0], EXIT_FAILURE);
        }
    }
}

static std::string read_entire_file(const char* file_name) {
    /*
     * Disable synchronization between C++ streams and FILE* functions for a
     * performance boost
     */
    std::ios::sync_with_stdio(false);

    std::ifstream ifs(file_name, std::ios::in | std::ios::binary);
    if (!ifs || !ifs.is_open()) {
        fprintf(stderr, "Failed to open file '%s'\n", file_name);
        exit(EXIT_FAILURE);
    }

    std::ostringstream ss;
    ss << ifs.rdbuf();
    if (!ss) {
        fprintf(stderr, "Failed to read file '%s'\n", file_name);
        exit(EXIT_FAILURE);
    }

    return ss.str();
}

static void write_entire_file(const char* file_name,
                              const std::vector<uint8_t>& data) {
    /*
     * Disable synchronization between C++ streams and FILE* functions for a
     * performance boost
     */
    std::ios::sync_with_stdio(false);

    std::ofstream ofs(file_name,
                      std::ios::out | std::ios::binary | std::ios::trunc);
    if (!ofs || !ofs.is_open()) {
        fprintf(stderr, "Failed to create file '%s'\n", file_name);
        exit(EXIT_FAILURE);
    }

    ofs.write(reinterpret_cast<const char*>(data.data()), data.size());
    if (!ofs) {
        fprintf(stderr, "Failed to write to file '%s'\n", file_name);
        exit(EXIT_FAILURE);
    }
}

static void build_package(const char* output_path,
                          const char* elf_path,
                          const char* manifest_path) {
    auto elf = read_entire_file(elf_path);
    auto manifest = read_entire_file(manifest_path);

    cbor::VectorCborEncoder encoded_package;
    encoded_package.encodeTag(APPLOADER_PACKAGE_CBOR_TAG_APP, [&](auto& enc) {
        enc.encodeArray([&](auto& enc) {
            enc.encodeUint(APPLOADER_PACKAGE_FORMAT_VERSION_CURRENT);
            enc.encodeMap([&](auto& enc) { /* no elements */ });
            enc.encodeBstr(elf);
            enc.encodeBstr(manifest);
        });
    });

    write_entire_file(output_path, encoded_package.intoVec());
}

static std::vector<uint8_t> string_to_vector(std::string s) {
    auto* start_ptr = reinterpret_cast<uint8_t*>(s.data());
    return {start_ptr, start_ptr + s.size()};
}

static uint8_t parse_key_id(const char* key_id) {
    std::string key_id_str{key_id};
    size_t key_id_end;
    int int_key_id = std::stoi(key_id_str, &key_id_end);
    if (key_id_end < key_id_str.size()) {
        fprintf(stderr, "Invalid key id: %s\n", key_id);
        exit(EXIT_FAILURE);
    }
    if (int_key_id < std::numeric_limits<uint8_t>::min() ||
        int_key_id > std::numeric_limits<uint8_t>::max()) {
        fprintf(stderr, "Key id out of range: %d\n", int_key_id);
        exit(EXIT_FAILURE);
    }
    return static_cast<uint8_t>(int_key_id);
}

static void sign_package(const char* output_path,
                         const char* input_path,
                         const char* key_path,
                         uint8_t key_id) {
    auto input = string_to_vector(read_entire_file(input_path));
    if (coseIsSigned({input.data(), input.size()}, nullptr)) {
        fprintf(stderr, "Input file is already signed\n");
        exit(EXIT_FAILURE);
    }

    cbor::VectorCborEncoder enc;
    enc.encodeMap([&](auto& enc) {
        enc.encodeKeyValue(COSE_LABEL_ALG, COSE_VAL_SIGN_ALG);
        enc.encodeKeyValue(COSE_LABEL_TRUSTY, [&](auto& enc) {
            enc.encodeArray([&](auto& enc) {
                enc.encodeTstr("TrustyApp");
                enc.encodeUint(APPLOADER_SIGNATURE_FORMAT_VERSION_CURRENT);
            });
        });
    });

    auto key = string_to_vector(read_entire_file(key_path));
    CoseByteView protectedHeadersView = enc.view();
    std::basic_string_view<uint8_t> unprotectedHeadersView = {};
    auto sig = coseSignEcDsa(key, key_id, input, protectedHeadersView,
                             unprotectedHeadersView, true, true);
    if (!sig) {
        fprintf(stderr, "Failed to sign package\n");
        exit(EXIT_FAILURE);
    }

    auto full_sig = sig.value();
    full_sig.insert(full_sig.end(), input.begin(), input.end());
    write_entire_file(output_path, full_sig);
}

static void verify_package(const char* input_path, const char* key_path) {
    auto input = string_to_vector(read_entire_file(input_path));
    size_t signature_length;
    if (!coseIsSigned({input.data(), input.size()}, &signature_length)) {
        fprintf(stderr, "Input file is not signed\n");
        exit(EXIT_FAILURE);
    }

    auto key = string_to_vector(read_entire_file(key_path));
    bool signature_ok;
    if (strict) {
        auto get_key = [&key](uint8_t key_id)
                -> std::tuple<std::unique_ptr<uint8_t[]>, size_t> {
            auto key_data = std::make_unique<uint8_t[]>(key.size());
            if (!key_data) {
                return {};
            }

            memcpy(key_data.get(), key.data(), key.size());
            return {std::move(key_data), key.size()};
        };
        signature_ok = strictCheckEcDsaSignature(input.data(), input.size(),
                                                 get_key, nullptr, nullptr);
    } else {
        std::vector<uint8_t> payload(input.begin() + signature_length,
                                     input.end());
        input.resize(signature_length);
        signature_ok = coseCheckEcDsaSignature(input, payload, key);
    }

    if (!signature_ok) {
        fprintf(stderr, "Signature verification failed\n");
        exit(EXIT_FAILURE);
    }

    fprintf(stderr, "Signature verification passed\n");
}

struct ContentIsCoseEncrypt {
    struct CborOut cursor;
    bool value;
};

static std::optional<ContentIsCoseEncrypt> find_content_is_cose_encrypt(
        const CoseByteView& headers) {
    struct CborIn in;
    size_t num_headers;
    uint64_t label;
    CborInInit(headers.data(), headers.size(), &in);
    CborReadMap(&in, &num_headers);
    std::optional<ContentIsCoseEncrypt> res;
    for (size_t i = 0; i < num_headers; i++) {
        if (CborReadUint(&in, &label) != CBOR_READ_RESULT_OK) {
            fprintf(stderr, "Invalid COSE header label.\n");
            exit(EXIT_FAILURE);
        }

        if (label == APPLOADER_PACKAGE_HEADER_LABEL_CONTENT_IS_COSE_ENCRYPT) {
            if (res.has_value()) {
                fprintf(stderr,
                        "Duplicate content_is_cose_encrypt header fields\n");
                exit(EXIT_FAILURE);
            }

            /*
             * CborIn and CborOut may be layout compatible but we should not
             * assume that will always be true so copy each field explicitly.
             */
            struct CborOut cursor = {.buffer = (uint8_t*)in.buffer,
                                     .buffer_size = in.buffer_size,
                                     .cursor = in.cursor};
            auto val = cbor::readCborBoolean(in);
            if (!val.has_value()) {
                fprintf(stderr,
                        "Invalid value for content_is_cose_encrypt header\n");
                exit(EXIT_FAILURE);
            }

            res = {cursor, *val};
        } else if (CborReadSkip(&in) != CBOR_READ_RESULT_OK) {
            fprintf(stderr, "Failed to parse COSE headers\n");
            exit(EXIT_FAILURE);
        }
    }

    return res;
}

static void update_header_content_is_cose_encrypt(std::vector<uint8_t>& headers,
                                                  bool new_value) {
    auto content_is_cose_encrypt =
            find_content_is_cose_encrypt({headers.data(), headers.size()});
    if (content_is_cose_encrypt.has_value()) {
        if (content_is_cose_encrypt->value == new_value) {
            fprintf(stderr, "Invalid content_is_cose_encrypt value\n");
            exit(EXIT_FAILURE);
        }

        // Update the content flag
        if (new_value) {
            CborWriteTrue(&content_is_cose_encrypt->cursor);
        } else {
            CborWriteFalse(&content_is_cose_encrypt->cursor);
        }
        assert(!CborOutOverflowed(&content_is_cose_encrypt->cursor));
    } else if (new_value) {
        cbor::VectorCborEncoder enc;
        enc.encodeMap([&](auto& enc) {
            enc.encodeKeyValue(
                    APPLOADER_PACKAGE_HEADER_LABEL_CONTENT_IS_COSE_ENCRYPT,
                    true);
        });
        const auto newHeaders = enc.view();

        auto updatedHeaders =
                cbor::mergeMaps({headers.data(), headers.size()}, newHeaders);
        assert(updatedHeaders.has_value() && "Failed to update COSE headers");

        headers.assign(updatedHeaders->begin(), updatedHeaders->end());
    }
}

struct PackageInfo {
    // Application package format version
    uint64_t version;

    // Application metadata as a map of headers
    CoseByteView headers;

    // ELF image or COSE_Encrypt structure
    CoseByteView elf_item;

    // Application manifest
    CoseByteView manifest;
};

static void parse_cose_recipient(struct CborIn* in,
                                 struct PackageInfo* package) {
    size_t num_elements, num_pairs;

    if (CborReadArray(in, &num_elements) != CBOR_READ_RESULT_OK) {
        fprintf(stderr,
                "Failed to read COSE_Recipient "
                "from COSE encryption structure\n");
        exit(EXIT_FAILURE);
    }

    if (num_elements != 3) {
        fprintf(stderr, "Invalid COSE_Recipient array size, got %zu\n",
                num_elements);
        exit(EXIT_FAILURE);
    }

    const uint8_t* enc_protected_headers_data;
    size_t enc_protected_headers_size;
    if (CborReadBstr(in, &enc_protected_headers_size,
                     &enc_protected_headers_data) != CBOR_READ_RESULT_OK) {
        fprintf(stderr,
                "Invalid COSE_Recipient. "
                "Encrypted protected headers is not a binary string\n");
        exit(EXIT_FAILURE);
    }

    if (CborReadMap(in, &num_pairs) != CBOR_READ_RESULT_OK) {
        fprintf(stderr, "Invalid COSE_Recipient. Failed to read map\n");
        exit(EXIT_FAILURE);
    }

    for (size_t i = 0; i < 2 * num_pairs; i++) {
        if (CborReadSkip(in) != CBOR_READ_RESULT_OK) {
            fprintf(stderr,
                    "Invalid COSE_Recipient. Failed to skip map element\n");
            exit(EXIT_FAILURE);
        }
    }

    const uint8_t* ciphertext_data;
    size_t ciphertext_size;
    if (CborReadBstr(in, &ciphertext_size, &ciphertext_data) !=
        CBOR_READ_RESULT_OK) {
        fprintf(stderr,
                "Invalid COSE_Recipient. "
                "Ciphertext is not a binary string\n");
        exit(EXIT_FAILURE);
    }
}

static PackageInfo parse_package(std::string_view input, bool check_sign_tag) {
    struct CborIn in;
    uint64_t tag;
    size_t num_elements, num_pairs;
    struct PackageInfo package;

    CborInInit(reinterpret_cast<const uint8_t*>(input.data()), input.size(),
               &in);

    if (CborReadTag(&in, &tag) != CBOR_READ_RESULT_OK) {
        fprintf(stderr, "Failed to parse input file as CBOR\n");
        exit(EXIT_FAILURE);
    }

    if (check_sign_tag && tag == COSE_TAG_SIGN1) {
        fprintf(stderr, "Input file is already signed\n");
        exit(EXIT_FAILURE);
    }

    if (tag != APPLOADER_PACKAGE_CBOR_TAG_APP) {
        fprintf(stderr, "Input file is not a Trusty application package\n");
        exit(EXIT_FAILURE);
    }

    if (CborReadArray(&in, &num_elements) != CBOR_READ_RESULT_OK) {
        fprintf(stderr, "Invalid input file format\n");
        exit(EXIT_FAILURE);
    }

    if (num_elements != APPLOADER_PACKAGE_CBOR_ARRAY_SZ) {
        fprintf(stderr, "Invalid number of CBOR array elements: %zd\n",
                num_elements);
        exit(EXIT_FAILURE);
    }

    if (CborReadUint(&in, &package.version) != CBOR_READ_RESULT_OK) {
        fprintf(stderr, "Invalid input file format\n");
        exit(EXIT_FAILURE);
    }

    if (package.version != APPLOADER_PACKAGE_FORMAT_VERSION_CURRENT) {
        fprintf(stderr,
                "Invalid package version, expected %" PRIu64 " got %" PRIu64
                "\n",
                APPLOADER_PACKAGE_FORMAT_VERSION_CURRENT, package.version);
        exit(EXIT_FAILURE);
    }

    const size_t headers_offset = CborInOffset(&in);

    if (CborReadMap(&in, &num_pairs) != CBOR_READ_RESULT_OK) {
        fprintf(stderr, "Invalid input file format\n");
        exit(EXIT_FAILURE);
    }

    uint64_t label;
    bool content_is_cose_encrypt = false;
    for (size_t i = 0; i < num_pairs; i++) {
        // read key
        if (CborReadUint(&in, &label) != CBOR_READ_RESULT_OK) {
            fprintf(stderr, "Invalid package headers\n");
            exit(EXIT_FAILURE);
        }

        // read value
        switch (label) {
        case APPLOADER_PACKAGE_HEADER_LABEL_CONTENT_IS_COSE_ENCRYPT: {
            auto val = cbor::readCborBoolean(in);
            if (!val.has_value()) {
                fprintf(stderr, "Invalid value for content_is_cose_encrypt\n");
                exit(EXIT_FAILURE);
            }
            content_is_cose_encrypt = *val;
            break;
        }

        default:
            fprintf(stderr,
                    "Package headers contain invalid label: %" PRIu64 "\n",
                    label);
            exit(EXIT_FAILURE);
        }
    }

    const size_t elf_offset = CborInOffset(&in);
    package.headers = {(const uint8_t*)input.data() + headers_offset,
                       elf_offset - headers_offset};

    const uint8_t* elf_data;
    size_t elf_size;
    if (content_is_cose_encrypt) {
        if (CborReadArray(&in, &num_elements) != CBOR_READ_RESULT_OK) {
            fprintf(stderr, "Invalid COSE encryption array\n");
            exit(EXIT_FAILURE);
        }

        /* content is COSE_Encrypt */
        if (num_elements < 3 || num_elements > 4) {
            fprintf(stderr, "Invalid COSE encryption array size, got %zu\n",
                    num_elements);
            exit(EXIT_FAILURE);
        }

        const uint8_t* enc_protected_headers_data;
        size_t enc_protected_headers_size;
        if (CborReadBstr(&in, &enc_protected_headers_size,
                         &enc_protected_headers_data) != CBOR_READ_RESULT_OK) {
            fprintf(stderr,
                    "Failed to retrieve protected headers from COSE "
                    "encryption structure\n");
            exit(EXIT_FAILURE);
        }

        /* TODO: parse and validate protected headers */
        if (CborReadMap(&in, &num_pairs) != CBOR_READ_RESULT_OK) {
            fprintf(stderr,
                    "Failed to retrieve unprotected headers from COSE "
                    "encryption structure\n");
            exit(EXIT_FAILURE);
        }

        /* TODO: parse and validate unprotected headers */
        for (size_t i = 0; i < 2 * num_pairs; i++) {
            if (CborReadSkip(&in) != CBOR_READ_RESULT_OK) {
                fprintf(stderr, "Invalid input file format\n");
                exit(EXIT_FAILURE);
            }
        }

        const uint8_t* ciphertext_data;
        size_t ciphertext_size;
        if (CborReadBstr(&in, &ciphertext_size, &ciphertext_data) !=
            CBOR_READ_RESULT_OK) {
            fprintf(stderr,
                    "Failed to retrieve ciphertext "
                    "from COSE encryption structure\n");
            exit(EXIT_FAILURE);
        }

        if (num_elements == 4) {
            size_t num_recipients;
            if (CborReadArray(&in, &num_recipients) != CBOR_READ_RESULT_OK) {
                fprintf(stderr,
                        "Failed to read recipients array "
                        "from COSE encryption structure\n");
                exit(EXIT_FAILURE);
            }

            while (num_recipients--) {
                parse_cose_recipient(&in, &package);
            }
        }

        package.elf_item = {(const uint8_t*)input.data() + elf_offset,
                            CborInOffset(&in) - elf_offset};
    } else { /* content is unencrypted */
        if (CborReadBstr(&in, &elf_size, &elf_data) != CBOR_READ_RESULT_OK) {
            fprintf(stderr,
                    "Failed to read ELF content from application package\n");
            exit(EXIT_FAILURE);
        }

        package.elf_item = {elf_data, elf_size};
    }

    const uint8_t* manifest_data;
    size_t manifest_size;
    if (CborReadBstr(&in, &manifest_size, &manifest_data) !=
        CBOR_READ_RESULT_OK) {
        fprintf(stderr, "Invalid CBOR type. Failed to read manifest as Bstr\n");
        exit(EXIT_FAILURE);
    }
    package.manifest = {manifest_data, manifest_size};

    assert(CborInAtEnd(&in));

    return package;
}

static void encrypt_package(const char* output_path,
                            const char* input_path,
                            const char* key_path,
                            uint8_t key_id) {
    auto input = read_entire_file(input_path);
    auto pkg_info = parse_package(input, true);

    auto key = string_to_vector(read_entire_file(key_path));
    if (key.size() != kAesGcmKeySize) {
        fprintf(stderr, "Wrong AES-GCM key size: %zu\n", key.size());
        exit(EXIT_FAILURE);
    }

    cbor::VectorCborEncoder enc;
    enc.encodeMap([&](auto& enc) {
        enc.encodeKeyValue(COSE_LABEL_ALG, COSE_VAL_CIPHER_ALG);
        enc.encodeKeyValue(COSE_LABEL_TRUSTY, "TrustyApp");
    });

    auto encodedProtectedHeaders = enc.intoVec();
    auto cose_encrypt =
            coseEncryptAesGcmKeyWrap(key, key_id, pkg_info.elf_item, {},
                                     encodedProtectedHeaders, {}, false);
    if (!cose_encrypt) {
        fprintf(stderr, "Failed to encrypt ELF file\n");
        exit(EXIT_FAILURE);
    }

    std::vector<uint8_t> enc_headers(pkg_info.headers.size());
    pkg_info.headers.copy(enc_headers.data(), pkg_info.headers.size());

    update_header_content_is_cose_encrypt(enc_headers, true);

    // Build a new encrypted array since the original array has a semantic
    // tag that we do not want to preserve.
    enc = cbor::VectorCborEncoder();
    enc.encodeTag(APPLOADER_PACKAGE_CBOR_TAG_APP, [&](auto& enc) {
        enc.encodeArray([&](auto& enc) {
            enc.encodeInt(pkg_info.version);
            enc.copyBytes(enc_headers);
            enc.copyBytes(cose_encrypt.value());
            enc.encodeBstr(pkg_info.manifest);
        });
    });
    auto encoded_package = enc.intoVec();
    write_entire_file(output_path, encoded_package);
}

static void decrypt_package(const char* output_path,
                            const char* input_path,
                            const char* key_path) {
    auto input = read_entire_file(input_path);
    auto pkg_info = parse_package(input, true);

    auto key = string_to_vector(read_entire_file(key_path));
    if (key.size() != kAesGcmKeySize) {
        fprintf(stderr, "Wrong AES-GCM key size: %zu, expected %zu\n",
                key.size(), kAesGcmKeySize);
        exit(EXIT_FAILURE);
    }

    auto get_key = [&key](
            uint8_t key_id) -> std::tuple<std::unique_ptr<uint8_t[]>, size_t> {
        auto key_data = std::make_unique<uint8_t[]>(key.size());
        if (!key_data) {
            return {};
        }

        memcpy(key_data.get(), key.data(), key.size());
        return {std::move(key_data), key.size()};
    };

    const uint8_t* package_start;
    size_t package_size;
    if (!coseDecryptAesGcmKeyWrapInPlace(pkg_info.elf_item, get_key, {}, false,
                                         &package_start, &package_size)) {
        fprintf(stderr, "Failed to decrypt ELF file\n");
        exit(EXIT_FAILURE);
    }

    std::vector<uint8_t> dec_headers(pkg_info.headers.size());
    pkg_info.headers.copy(dec_headers.data(), pkg_info.headers.size());
    update_header_content_is_cose_encrypt(dec_headers, false);

    // Build a new decrypted array since the original array has a semantic
    // tag that we do not want to preserve.
    cbor::VectorCborEncoder enc;
    enc.encodeTag(APPLOADER_PACKAGE_CBOR_TAG_APP, [&](auto& enc) {
        enc.encodeArray([&](auto& enc) {
            enc.encodeInt(pkg_info.version);
            enc.copyBytes(dec_headers);
            enc.encodeBstr({package_start, package_size});
            enc.copyBytes(pkg_info.manifest);
        });
    });

    auto encoded_package = enc.intoVec();
    write_entire_file(output_path, encoded_package);
}

static void print_package_info(const char* input_path) {
    struct apploader_policy_data ap_data = {.force_store_min_version = false};
    uint8_t package_key_id = UINT8_MAX;

    // We call into some COSE functions to retrieve the
    // key ids, and we don't want them to print any errors
    // (which they do since we pass them invalid keys)
    bool oldSilenceErrors = coseSetSilenceErrors(true);

    printf("File: %s\n", input_path);

    auto input = read_entire_file(input_path);
    size_t signature_length = 0;
    if (coseIsSigned({reinterpret_cast<uint8_t*>(input.data()), input.size()},
                     &signature_length)) {
        printf("Signed: YES\n");

        // Call into cose.cpp with a callback that prints and saves the key id
        auto print_key_id = [&](uint8_t key_id)
                -> std::tuple<std::unique_ptr<uint8_t[]>, size_t> {
            printf("Signature key id: %" PRIu8 "\n", key_id);
            package_key_id = key_id;
            return {};
        };
        strictCheckEcDsaSignature(
                reinterpret_cast<const uint8_t*>(input.data()), input.size(),
                print_key_id, nullptr, nullptr);
    } else {
        printf("Signed: NO\n");
    }

    std::string_view signed_package{input.data() + signature_length,
                                    input.size() - signature_length};
    auto pkg_info = parse_package(signed_package, false);
    auto content_is_cose_encrypt =
            find_content_is_cose_encrypt(pkg_info.headers);

    // Get manifest to check encryption requirement
    if (pkg_info.manifest.size() == 0) {
        fprintf(stderr, "Package did not contain a valid manifest\n");
        exit(EXIT_FAILURE);
    }

    if (!apploader_parse_manifest(
                reinterpret_cast<const char*>(pkg_info.manifest.data()),
                pkg_info.manifest.size(), &ap_data.manifest_extracts)) {
        fprintf(stderr, "Unable to extract manifest fields\n");
        exit(EXIT_FAILURE);
    }

    bool package_error = false;

    if (content_is_cose_encrypt && content_is_cose_encrypt->value) {
        if (ap_data.manifest_extracts.requires_encryption) {
            printf("Encrypted: YES, REQUIRED\n");
        } else {
            printf("Encrypted: YES, OPTIONAL\n");
        }

        // Call into cose.cpp with a callback that prints the key id
        auto print_key_id = [
        ](uint8_t key_id) -> std::tuple<std::unique_ptr<uint8_t[]>, size_t> {
            printf("Encryption key id: %" PRIu8 "\n", key_id);
            return {};
        };

        const uint8_t* package_start;
        size_t package_size;
        coseDecryptAesGcmKeyWrapInPlace(pkg_info.elf_item, print_key_id, {},
                                        false, &package_start, &package_size);
    } else if (ap_data.manifest_extracts.requires_encryption) {
        printf("Encrypted: NO, REQUIRED\n");
        fprintf(stderr,
                "Error: app is not encrypted, contrary to manifest requirement.\n");
        fprintf(stderr,
                "Either encrypt the app, or remove the manifest requirement.\n");
        package_error = true;
    } else {
        printf("Encrypted: NO, OPTIONAL\n");
    }

    const uuid_t* uuid = &ap_data.manifest_extracts.uuid;
    printf("UUID: %08" PRIx32 "-%04" PRIx16 "-%04" PRIx16 "-%02" PRIx8
           "%02" PRIx8 "-%02" PRIx8 "%02" PRIx8 "%02" PRIx8 "%02" PRIx8
           "%02" PRIx8 "%02" PRIx8 "\n",
           uuid->time_low, uuid->time_mid, uuid->time_hi_and_version,
           uuid->clock_seq_and_node[0], uuid->clock_seq_and_node[1],
           uuid->clock_seq_and_node[2], uuid->clock_seq_and_node[3],
           uuid->clock_seq_and_node[4], uuid->clock_seq_and_node[5],
           uuid->clock_seq_and_node[6], uuid->clock_seq_and_node[7]);

    printf("Version: %" PRIu32 "\n", ap_data.manifest_extracts.version);
    printf("Min version: %" PRIu32 "\n", ap_data.manifest_extracts.min_version);

#ifdef WITH_APPLOADER_POLICY_ENGINE
    printf("App loading policy checks\n");

    for (int i = 0; i < 2; i++) {
        bool can_load = false;

        // Set the mocked apploading state
        host_mock_state_app_loading_unlocked = !i;

        // Check if the key can be retrieved
        int rc = apploader_policy_engine_get_key(
                package_key_id, &ap_data.public_key, &ap_data.public_key_size);
        if (rc != NO_ERROR) {
            printf("Apploading policy: FAILED, the signing key is invalid.\n");
        } else {
            if (!apploader_policy_engine_validate(&ap_data)) {
                printf("Apploading policy: FAILED, policy did not pass\n");
            } else {
                printf("Apploading policy: PASS (apploading %s)\n",
                       host_mock_state_app_loading_unlocked ? "unlocked"
                                                            : "locked");
                can_load = true;
            }

            apploader_policy_engine_put_key(ap_data.public_key);
        }

        if (!can_load && !host_mock_state_app_loading_unlocked) {
            fprintf(stderr,
                    "Error: package cannot load on a production device.\n");
            package_error = true;
        }
    }
#endif
    // Restore the old silence flag
    coseSetSilenceErrors(oldSilenceErrors);

    if (package_error) {
        fprintf(stderr, "Exiting with error status.\n");
        exit(EXIT_FAILURE);
    }
}

#ifdef WITH_APPLOADER_POLICY_ENGINE
static void print_key_info(void) {
    for (uint8_t kid = 0; kid < UINT8_MAX; kid++) {
        const uint8_t* public_key;
        unsigned int public_key_size;

        if (apploader_policy_engine_get_key(kid, &public_key,
                                            &public_key_size) == NO_ERROR) {
            printf("Key %" PRIu8 "\n", kid);

            apploader_policy_engine_put_key(public_key);
        }
    }
}
#endif

int main(int argc, char** argv) {
    parse_options(argc, argv);

    switch (mode) {
    case Mode::BUILD:
        if (optind + 3 != argc) {
            print_usage_and_exit(argv[0], EXIT_FAILURE);
        }
        build_package(argv[optind], argv[optind + 1], argv[optind + 2]);
        break;

    case Mode::SIGN:
        if (optind + 4 != argc) {
            print_usage_and_exit(argv[0], EXIT_FAILURE);
        }
        sign_package(argv[optind], argv[optind + 1], argv[optind + 2],
                     parse_key_id(argv[optind + 3]));
        break;

    case Mode::VERIFY:
        if (optind + 2 != argc) {
            print_usage_and_exit(argv[0], EXIT_FAILURE);
        }
        verify_package(argv[optind], argv[optind + 1]);
        break;

    case Mode::ENCRYPT:
        if (optind + 4 != argc) {
            print_usage_and_exit(argv[0], EXIT_FAILURE);
        }
        encrypt_package(argv[optind], argv[optind + 1], argv[optind + 2],
                        parse_key_id(argv[optind + 3]));
        break;

    case Mode::DECRYPT:
        if (optind + 3 != argc) {
            print_usage_and_exit(argv[0], EXIT_FAILURE);
        }
        decrypt_package(argv[optind], argv[optind + 1], argv[optind + 2]);
        break;

    case Mode::INFO:
        if (optind + 1 != argc) {
            print_usage_and_exit(argv[0], EXIT_FAILURE);
        }
        print_package_info(argv[optind]);
        break;
#ifdef WITH_APPLOADER_POLICY_ENGINE
    case Mode::KEYS:
        if (optind != argc) {
            print_usage_and_exit(argv[0], EXIT_FAILURE);
        }
        print_key_info();
        break;
#endif
    default:
        print_usage_and_exit(argv[0], EXIT_FAILURE);
        break;
    }

    return 0;
}
