/*
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef CHRE_EXTERNAL_MBEDTLS_CONFIG_H_
#define CHRE_EXTERNAL_MBEDTLS_CONFIG_H_

#include <limits.h>
#include "mbedtls_memory.h"

/**
 * System support
 */
#define MBEDTLS_HAVE_ASM
#define MBEDTLS_PLATFORM_C
#define MBEDTLS_PLATFORM_MEMORY
#define MBEDTLS_PLATFORM_NO_STD_FUNCTIONS
#define MBEDTLS_DEPRECATED_WARNING
#define MBEDTLS_NO_PLATFORM_ENTROPY

/**
 * Feature support
 */
#define MBEDTLS_ECP_DP_SECP256R1_ENABLED
#define MBEDTLS_ECP_NIST_OPTIM
#define MBEDTLS_PK_PARSE_EC_EXTENDED

/**
 * MbedTLS modules
 */
#define MBEDTLS_ASN1_PARSE_C
#define MBEDTLS_ASN1_WRITE_C
#define MBEDTLS_BIGNUM_C
#define MBEDTLS_ECDSA_C
#define MBEDTLS_ECP_C
#define MBEDTLS_MD_C
#define MBEDTLS_OID_C
#define MBEDTLS_PK_C
#define MBEDTLS_PK_PARSE_C
#define MBEDTLS_SHA224_C
#define MBEDTLS_SHA256_C

/**
 * Platform specific defines
 */
#define MBEDTLS_PLATFORM_CALLOC_MACRO mbedtlsMemoryCalloc
#define MBEDTLS_PLATFORM_FREE_MACRO mbedtlsMemoryFree
#define MBEDTLS_PLATFORM_SNPRINTF_MACRO snprintf
#define MBEDTLS_PLATFORM_FPRINTF_MACRO(fp, fmt, ...) \
  ({                                                 \
    static_assert(fp == stderr);                     \
    LOGE(fmt, ##__VA_ARGS__);                        \
    -1;                                              \
  })

#endif  // CHRE_EXTERNAL_MBEDTLS_CONFIG_H_
