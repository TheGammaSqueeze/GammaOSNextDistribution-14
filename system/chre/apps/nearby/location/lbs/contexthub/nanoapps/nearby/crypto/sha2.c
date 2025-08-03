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

#include "location/lbs/contexthub/nanoapps/nearby/crypto/sha2.h"

#include <string.h>

inline static uint32_t BSWAP32(uint32_t value) {
#if defined(__clang__) || \
    (defined(__GNUC__) && \
     ((__GNUC__ == 4 && __GNUC_MINOR__ >= 8) || __GNUC__ >= 5))
  return __builtin_bswap32(value);
#else
  uint32_t Byte0 = value & 0x000000FF;
  uint32_t Byte1 = value & 0x0000FF00;
  uint32_t Byte2 = value & 0x00FF0000;
  uint32_t Byte3 = value & 0xFF000000;
  return (Byte0 << 24) | (Byte1 << 8) | (Byte2 >> 8) | (Byte3 >> 24);
#endif
}

void sha2init(struct Sha2Context *ctx) {
  ctx->h[0] = 0x6a09e667;
  ctx->h[1] = 0xbb67ae85;
  ctx->h[2] = 0x3c6ef372;
  ctx->h[3] = 0xa54ff53a;
  ctx->h[4] = 0x510e527f;
  ctx->h[5] = 0x9b05688c;
  ctx->h[6] = 0x1f83d9ab;
  ctx->h[7] = 0x5be0cd19;
  ctx->msgLen = 0;
  ctx->bufBytesUsed = 0;
}

#ifdef ARM

#define STRINFIGY2(b) #b
#define STRINGIFY(b) STRINFIGY2(b)
#define ror(v, b)                                         \
  ({                                                      \
    uint32_t ret;                                         \
    if (b)                                                \
      asm("ror %0, #" STRINGIFY(b) : "=r"(ret) : "0"(v)); \
    else                                                  \
      ret = v;                                            \
    ret;                                                  \
  })

#else

inline static uint32_t ror(uint32_t val, uint32_t by) {
  if (!by) return val;

  val = (val >> by) | (val << (32 - by));

  return val;
}

#endif

static void sha2processBlock(struct Sha2Context *ctx) {
  static const uint32_t k[] = {
      0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1,
      0x923f82a4, 0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
      0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786,
      0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
      0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147,
      0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
      0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b,
      0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
      0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a,
      0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
      0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
  };
  uint32_t i, a, b, c, d, e, f, g, h;

  // input and output streams are little-endian
  // SHA specification uses big-endian
  // byteswap the input
  for (i = 0; i < SHA2_BLOCK_SIZE / sizeof(uint32_t); i++)
    ctx->w[i] = BSWAP32(ctx->w[i]);

  // expand input
  for (; i < SHA2_WORDS_CTX_SIZE; i++) {
    uint32_t s0 = ror(ctx->w[i - 15], 7) ^ ror(ctx->w[i - 15], 18) ^
                  (ctx->w[i - 15] >> 3);
    uint32_t s1 =
        ror(ctx->w[i - 2], 17) ^ ror(ctx->w[i - 2], 19) ^ (ctx->w[i - 2] >> 10);
    ctx->w[i] = ctx->w[i - 16] + s0 + ctx->w[i - 7] + s1;
  }

  // init working variables
  a = ctx->h[0];
  b = ctx->h[1];
  c = ctx->h[2];
  d = ctx->h[3];
  e = ctx->h[4];
  f = ctx->h[5];
  g = ctx->h[6];
  h = ctx->h[7];

  // 64 rounds
  for (i = 0; i < 64; i++) {
    uint32_t s1 = ror(e, 6) ^ ror(e, 11) ^ ror(e, 25);
    uint32_t ch = (e & f) ^ ((~e) & g);
    uint32_t temp1 = h + s1 + ch + k[i] + ctx->w[i];
    uint32_t s0 = ror(a, 2) ^ ror(a, 13) ^ ror(a, 22);
    uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
    uint32_t temp2 = s0 + maj;

    h = g;
    g = f;
    f = e;
    e = d + temp1;
    d = c;
    c = b;
    b = a;
    a = temp1 + temp2;
  }

  // put result back into context
  ctx->h[0] += a;
  ctx->h[1] += b;
  ctx->h[2] += c;
  ctx->h[3] += d;
  ctx->h[4] += e;
  ctx->h[5] += f;
  ctx->h[6] += g;
  ctx->h[7] += h;
}

void sha2processBytes(struct Sha2Context *ctx, const void *inData,
                      size_t dataLen) {
  const uint8_t *inBytes = (const uint8_t *)inData;

  ctx->msgLen += dataLen;
  while (dataLen) {
    size_t bytesToCopy;

    // step 1: copy data into context if there is space & there is data
    bytesToCopy = dataLen;
    if (bytesToCopy > SHA2_BLOCK_SIZE - ctx->bufBytesUsed)
      bytesToCopy = SHA2_BLOCK_SIZE - ctx->bufBytesUsed;
    memcpy(ctx->b + ctx->bufBytesUsed, inBytes, bytesToCopy);
    inBytes += bytesToCopy;
    dataLen -= bytesToCopy;
    ctx->bufBytesUsed += bytesToCopy;

    // step 2: if there is a full block, process it
    if (ctx->bufBytesUsed == SHA2_BLOCK_SIZE) {
      sha2processBlock(ctx);
      ctx->bufBytesUsed = 0;
    }
  }
}

void sha2finish(struct Sha2Context *ctx, void *outHash, uint32_t hashLen) {
  uint8_t appendend = 0x80;
  uint64_t dataLenInBits = ctx->msgLen * 8;
  uint32_t minHashLen;

  // append the one
  sha2processBytes(ctx, &appendend, 1);

  // append the zeroes
  appendend = 0;
  while (ctx->bufBytesUsed != 56) sha2processBytes(ctx, &appendend, 1);

  // append the length in bits (we can safely write into context since we're
  // sure where to write to (we're definitely 56-bytes into a block)
  for (uint32_t i = 0; i < 8; i++, dataLenInBits >>= 8)
    ctx->b[63 - i] = (uint8_t)(dataLenInBits);

  // process last block
  sha2processBlock(ctx);

  // input and output streams are little-endian
  // SHA specification uses big-endian
  // copy the final hash to the output after byteswap
  for (uint32_t i = 0; i < sizeof(ctx->h) / sizeof(uint32_t); i++)
    ctx->h[i] = BSWAP32(ctx->h[i]);

  minHashLen = (hashLen > SHA2_HASH_SIZE) ? SHA2_HASH_SIZE : hashLen;
  memcpy(outHash, ctx->h, minHashLen);
}

void sha256(const void *inData, const uint32_t dataLen, void *outHash,
            const uint32_t hashLen) {
  struct Sha2Context ctx;
  sha2init(&ctx);
  sha2processBytes(&ctx, inData, dataLen);
  sha2finish(&ctx, outHash, hashLen);
}
