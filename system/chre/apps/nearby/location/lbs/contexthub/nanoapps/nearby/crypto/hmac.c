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

#include "location/lbs/contexthub/nanoapps/nearby/crypto/hmac.h"

#include <string.h>

static void sha2InitHmacKeyUpdate(struct HmacContext *ctx) {
  // initialize sha2 context and update hmac keys to the sha2 context
  sha2init(&ctx->sha2ctx);
  sha2processBytes(&ctx->sha2ctx, ctx->k_ipad, sizeof(ctx->k_ipad));
}

void hmacInit(struct HmacContext *ctx, const void *inKey, const size_t keyLen) {
  // initialize hmac keys
  memset(ctx->k, 0, sizeof(ctx->k));
  memset(ctx->k_ipad, 0x36, sizeof(ctx->k_ipad));
  memset(ctx->k_opad, 0x5c, sizeof(ctx->k_opad));
  memcpy(ctx->k, inKey, keyLen);
  ctx->is_hmac_updated = false;

  // XOR key with ipad and opad values
  for (size_t i = 0; i < SHA2_BLOCK_SIZE; i++) {
    ctx->k_ipad[i] ^= ctx->k[i];
    ctx->k_opad[i] ^= ctx->k[i];
  }
  // initialize sha2 context and update hmac keys to the sha2 context
  sha2InitHmacKeyUpdate(ctx);
}

void hmacUpdate(struct HmacContext *ctx, const void *inData,
                const size_t dataLen) {
  // update the input data to the sha2 context
  sha2processBytes(&ctx->sha2ctx, inData, dataLen);

  if (!ctx->is_hmac_updated) {
    ctx->is_hmac_updated = true;
  }
}

void hmacUpdateHashInit(struct HmacContext *ctx, const void *inData,
                        const size_t dataLen) {
  // initialize sha2 context and update hmac keys to the sha2 context if it was
  // updated
  if (ctx->is_hmac_updated) {
    sha2InitHmacKeyUpdate(ctx);
  }
  hmacUpdate(ctx, inData, dataLen);
}

void hmacFinish(struct HmacContext *ctx, void *outHash, const size_t hashLen) {
  // finish inner sha256
  sha2finish(&ctx->sha2ctx, ctx->ihash, sizeof(ctx->ihash));

  // perform outer sha256
  sha2init(&ctx->sha2ctx);
  sha2processBytes(&ctx->sha2ctx, ctx->k_opad, sizeof(ctx->k_opad));
  sha2processBytes(&ctx->sha2ctx, ctx->ihash, sizeof(ctx->ihash));
  sha2finish(&ctx->sha2ctx, outHash, (uint32_t)hashLen);
}

void hmacSha256(const void *inKey, const size_t keyLen, const void *inData,
                const size_t dataLen, void *outHash, const size_t hashLen) {
  struct HmacContext ctx;
  hmacInit(&ctx, inKey, keyLen);
  hmacUpdate(&ctx, inData, dataLen);
  hmacFinish(&ctx, outHash, hashLen);
}
