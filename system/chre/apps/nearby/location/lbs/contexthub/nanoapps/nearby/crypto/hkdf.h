/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_CRYPTO_HKDF_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_CRYPTO_HKDF_H_

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Supported HKDF mode:
 * - HKDF-HMAC-SHA256
 *
 * External a single API:
 *  - hkdf() for extracting and expanding keys derivation function
 */

#include <stddef.h>

/**
 * hkdf:
 * @inSalt: input salt byte array
 * @saltLen: number of bytes of the input salt array
 * @inKm: input key material byte array
 * @ikmLen: number of bytes of the input key material byte array
 * @outKm: output key material byte array
 * @okmLen: number of bytes of the output key material byte array
 *          okmLen should be less than 8161
 *          (= 256 * SHA2_HASH_SIZE - SHA2_HASH_SIZE + 1)
 *
 * Initializes hmac keys and hash context internally
 * Updates input data to the context
 * Generates 32 bytes keyed-hash and copy to the output byte array as much as
 * min(SHA2_HASH_SIZE, hashLen)
 *
 * Returns:
 */
void hkdf(const void *inSalt, size_t saltLen, const void *inKm, size_t ikmLen,
          void *outKm, size_t okmLen);
#ifdef __cplusplus
}
#endif
#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_CRYPTO_HKDF_H_
