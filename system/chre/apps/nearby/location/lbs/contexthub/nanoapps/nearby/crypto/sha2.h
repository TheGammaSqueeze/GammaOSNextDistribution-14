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

#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_CRYPTO_SHA2_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_CRYPTO_SHA2_H_

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Supported SHA2 mode:
 * - SHA256
 *
 * External separated APIs:
 *  - sha2init() for SHA256 initialization
 *  - sha2processBytes() for updating input data
 *  - sha2finish() for generating SHA256 hash output
 *
 * External a single API:
 *  - sha256() for performing the separated three APIs at a time
 */

#include <stddef.h>
#include <stdint.h>

#define SHA2_BLOCK_SIZE 64U      // in bytes
#define SHA2_WORDS_CTX_SIZE 64U  // in words

#define SHA2_HASH_SIZE 32U  // in bytes
#define SHA2_HASH_WORDS 8U  // in words

struct Sha2Context {
  uint32_t h[SHA2_HASH_WORDS];
  size_t msgLen;
  union {
    uint32_t w[SHA2_WORDS_CTX_SIZE];
    uint8_t b[SHA2_BLOCK_SIZE];
  };
  uint8_t bufBytesUsed;
};

/**
 * sha256:
 * @inData: input data byte array to hash
 * @dataLen: number of bytes of the input byte array
 * @outHash: output hash byte array
 * @hashLen: number of bytes of the output byte array
 *
 * Initializes SHA256 context internally
 * Updates input data to the context
 * Generates 32 bytes hash and copy to the output byte array as much as
 * min(SHA2_HASH_SIZE, hash_len)
 *
 * Returns:
 */
void sha256(const void *inData, uint32_t dataLen, void *outHash,
            uint32_t hashLen);

/**
 * sha2init:
 * @ctx: SHA256 context
 *
 * Initializes the SHA256 context
 *
 * Returns:
 */
void sha2init(struct Sha2Context *ctx);

/**
 * sha2processBytes:
 * @ctx: SHA256 context initialized
 * @inData: input data byte array to hash
 * @dataLen: number of bytes of the input byte array
 *
 * Updates input data to the context
 *
 * Returns:
 */
void sha2processBytes(struct Sha2Context *ctx, const void *inData,
                      size_t dataLen);

/**
 * sha2finish:
 * @ctx: SHA256 context initialized
 * @outHash: output hash byte array
 * @hashLen: number of bytes of the output byte array
 *
 * Generates 32 bytes hash and copy to the output byte array as much as
 * min(SHA2_HASH_SIZE, hash_len)
 *
 * Returns:
 */
void sha2finish(struct Sha2Context *ctx, void *outHash, uint32_t hashLen);
#ifdef __cplusplus
}
#endif
#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_CRYPTO_SHA2_H_
