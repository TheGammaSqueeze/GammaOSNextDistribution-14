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

#pragma once

#include <inttypes.h>
#include <lk/compiler.h>
#include <stdbool.h>
#include <stddef.h>

#define APPLOADER_PACKAGE_CBOR_ARRAY_SZ 4

__BEGIN_CDECLS

/**
 * struct apploader_package_metadata - Package metadata, parsed from the package
 * @elf_start:              Pointer to the start of the ELF image
 * @elf_size:               Size of the embedded ELF image
 * @elf_is_cose_encrypt:    True iff package contained encrypted ELF image
 * @manifest_start:         Pointer to the start of the manifest
 * @manifest_size:          Size of the manifest
 * @public_key:             Pointer to public key in DER encoding
 * @public_key_size:        Size of the public key
 *
 * This structure contains metadata about the package, parsed from the package
 * data by the apploader_parse_package_metadata() function.
 */
struct apploader_package_metadata {
    const uint8_t* elf_start;
    size_t elf_size;
    bool elf_is_cose_encrypt;

    const uint8_t* manifest_start;
    size_t manifest_size;

    const uint8_t* public_key;
    unsigned int public_key_size;
};

bool apploader_parse_package_metadata(
        uint8_t* package_start,
        size_t package_size,
        struct apploader_package_metadata* metadata);

__END_CDECLS
