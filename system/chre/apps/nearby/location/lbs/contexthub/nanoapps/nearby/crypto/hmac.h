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

#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_CRYPTO_HMAC_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_CRYPTO_HMAC_H_

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Supported HMAC mode:
 * - HMAC-SHA256
 *
 * External separated APIs:
 *  - hmacInit() for initializing hmac keys and hash context
 *  - hmacUpdate() for updating input data
 *  - hmacUpdateHashInit() for initializing hash context and updating input data
 *  - hmacFinish() for generating HMAC-SHA256 keyed-hash output
 *
 * External a single API:
 *  - hmacSha256() for performing the separated three APIs at a time:
 *    hmacInit(), hmacUpdate(), and hmacFinish()
 */
#include <stdbool.h>

#include "location/lbs/contexthub/nanoapps/nearby/crypto/sha2.h"

struct HmacContext {
  uint8_t k[SHA2_BLOCK_SIZE];
  uint8_t k_ipad[SHA2_BLOCK_SIZE];
  uint8_t k_opad[SHA2_BLOCK_SIZE];
  uint8_t ihash[SHA2_HASH_SIZE];
  struct Sha2Context sha2ctx;
  bool is_hmac_updated;
};

/**
 * hmacSha256:
 * @inKey: input key byte array
 * @keyLen: number of bytes of the input key array
 * @inData: input data byte array to hash
 * @dataLen: number of bytes of the input byte array
 * @outHash: output keyed-hash byte array
 * @hashLen: number of bytes of the output byte array
 *
 * Initializes hmac keys and hash context internally
 * Updates input data to the context
 * Generates 32 bytes keyed-hash and copy to the output byte array as much as
 * min(SHA2_HASH_SIZE, hashLen)
 *
 * Returns:
 */
void hmacSha256(const void *inKey, size_t keyLen, const void *inData,
                size_t dataLen, void *outHash, size_t hashLen);

/**
 * hmacInit:
 * @ctx: HMAC context
 * @inKey: input key byte array
 * @keyLen: number of bytes of the input key array
 *
 * Initializes hmac keys and hash context
 *
 * Returns:
 */
void hmacInit(struct HmacContext *ctx, const void *inKey, size_t keyLen);

/**
 * hmacUpdate:
 * @ctx: HMAC context
 * @inData: input data byte array to hash
 * @dataLen: number of bytes of the input byte array
 *
 * Updates input data to the context
 *
 * Returns:
 */
void hmacUpdate(struct HmacContext *ctx, const void *inData, size_t dataLen);

/**
 * hmacUpdateHashInit:
 * @ctx: HMAC context
 * @inData: input data byte array to hash
 * @dataLen: number of bytes of the input byte array
 *
 * Initializes hash context and updates input data to the context
 * Can be used for initializing hash context without refreshing HMAC keys
 *
 * Returns:
 */
void hmacUpdateHashInit(struct HmacContext *ctx, const void *inData,
                        size_t dataLen);

/**
 * hmacFinish:
 * @ctx: HMAC context
 * @outHash: output keyed-hash byte array
 * @hashLen: number of bytes of the output byte array
 *
 * Generates 32 bytes keyed-hash and copy to the output byte array as much as
 * min(SHA2_HASH_SIZE, hashLen)
 *
 * Returns:
 */
void hmacFinish(struct HmacContext *ctx, void *outHash, size_t hashLen);
#ifdef __cplusplus
}
#endif
#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_CRYPTO_HMAC_H_
