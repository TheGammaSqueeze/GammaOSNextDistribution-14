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

#include "location/lbs/contexthub/nanoapps/nearby/crypto/aes.h"

#include <stdalign.h>
#include <stdbool.h>
#include <string.h>

#define AES_128_KEY_NUM_ROUNDS 10
#define AES_192_KEY_NUM_ROUNDS 12
#define AES_256_KEY_NUM_ROUNDS 14

#define IS_ALIGNED(ptr, type) (((uintptr_t)(ptr) & (alignof(type) - 1)) == 0)
static const uint8_t FwdSbox[] = {
    0x63, 0x7C, 0x77, 0x7B, 0xF2, 0x6B, 0x6F, 0xC5, 0x30, 0x01, 0x67, 0x2B,
    0xFE, 0xD7, 0xAB, 0x76, 0xCA, 0x82, 0xC9, 0x7D, 0xFA, 0x59, 0x47, 0xF0,
    0xAD, 0xD4, 0xA2, 0xAF, 0x9C, 0xA4, 0x72, 0xC0, 0xB7, 0xFD, 0x93, 0x26,
    0x36, 0x3F, 0xF7, 0xCC, 0x34, 0xA5, 0xE5, 0xF1, 0x71, 0xD8, 0x31, 0x15,
    0x04, 0xC7, 0x23, 0xC3, 0x18, 0x96, 0x05, 0x9A, 0x07, 0x12, 0x80, 0xE2,
    0xEB, 0x27, 0xB2, 0x75, 0x09, 0x83, 0x2C, 0x1A, 0x1B, 0x6E, 0x5A, 0xA0,
    0x52, 0x3B, 0xD6, 0xB3, 0x29, 0xE3, 0x2F, 0x84, 0x53, 0xD1, 0x00, 0xED,
    0x20, 0xFC, 0xB1, 0x5B, 0x6A, 0xCB, 0xBE, 0x39, 0x4A, 0x4C, 0x58, 0xCF,
    0xD0, 0xEF, 0xAA, 0xFB, 0x43, 0x4D, 0x33, 0x85, 0x45, 0xF9, 0x02, 0x7F,
    0x50, 0x3C, 0x9F, 0xA8, 0x51, 0xA3, 0x40, 0x8F, 0x92, 0x9D, 0x38, 0xF5,
    0xBC, 0xB6, 0xDA, 0x21, 0x10, 0xFF, 0xF3, 0xD2, 0xCD, 0x0C, 0x13, 0xEC,
    0x5F, 0x97, 0x44, 0x17, 0xC4, 0xA7, 0x7E, 0x3D, 0x64, 0x5D, 0x19, 0x73,
    0x60, 0x81, 0x4F, 0xDC, 0x22, 0x2A, 0x90, 0x88, 0x46, 0xEE, 0xB8, 0x14,
    0xDE, 0x5E, 0x0B, 0xDB, 0xE0, 0x32, 0x3A, 0x0A, 0x49, 0x06, 0x24, 0x5C,
    0xC2, 0xD3, 0xAC, 0x62, 0x91, 0x95, 0xE4, 0x79, 0xE7, 0xC8, 0x37, 0x6D,
    0x8D, 0xD5, 0x4E, 0xA9, 0x6C, 0x56, 0xF4, 0xEA, 0x65, 0x7A, 0xAE, 0x08,
    0xBA, 0x78, 0x25, 0x2E, 0x1C, 0xA6, 0xB4, 0xC6, 0xE8, 0xDD, 0x74, 0x1F,
    0x4B, 0xBD, 0x8B, 0x8A, 0x70, 0x3E, 0xB5, 0x66, 0x48, 0x03, 0xF6, 0x0E,
    0x61, 0x35, 0x57, 0xB9, 0x86, 0xC1, 0x1D, 0x9E, 0xE1, 0xF8, 0x98, 0x11,
    0x69, 0xD9, 0x8E, 0x94, 0x9B, 0x1E, 0x87, 0xE9, 0xCE, 0x55, 0x28, 0xDF,
    0x8C, 0xA1, 0x89, 0x0D, 0xBF, 0xE6, 0x42, 0x68, 0x41, 0x99, 0x2D, 0x0F,
    0xB0, 0x54, 0xBB, 0x16,
};

static const uint32_t FwdTab0[] = {
    0xC66363A5, 0xF87C7C84, 0xEE777799, 0xF67B7B8D, 0xFFF2F20D, 0xD66B6BBD,
    0xDE6F6FB1, 0x91C5C554, 0x60303050, 0x02010103, 0xCE6767A9, 0x562B2B7D,
    0xE7FEFE19, 0xB5D7D762, 0x4DABABE6, 0xEC76769A, 0x8FCACA45, 0x1F82829D,
    0x89C9C940, 0xFA7D7D87, 0xEFFAFA15, 0xB25959EB, 0x8E4747C9, 0xFBF0F00B,
    0x41ADADEC, 0xB3D4D467, 0x5FA2A2FD, 0x45AFAFEA, 0x239C9CBF, 0x53A4A4F7,
    0xE4727296, 0x9BC0C05B, 0x75B7B7C2, 0xE1FDFD1C, 0x3D9393AE, 0x4C26266A,
    0x6C36365A, 0x7E3F3F41, 0xF5F7F702, 0x83CCCC4F, 0x6834345C, 0x51A5A5F4,
    0xD1E5E534, 0xF9F1F108, 0xE2717193, 0xABD8D873, 0x62313153, 0x2A15153F,
    0x0804040C, 0x95C7C752, 0x46232365, 0x9DC3C35E, 0x30181828, 0x379696A1,
    0x0A05050F, 0x2F9A9AB5, 0x0E070709, 0x24121236, 0x1B80809B, 0xDFE2E23D,
    0xCDEBEB26, 0x4E272769, 0x7FB2B2CD, 0xEA75759F, 0x1209091B, 0x1D83839E,
    0x582C2C74, 0x341A1A2E, 0x361B1B2D, 0xDC6E6EB2, 0xB45A5AEE, 0x5BA0A0FB,
    0xA45252F6, 0x763B3B4D, 0xB7D6D661, 0x7DB3B3CE, 0x5229297B, 0xDDE3E33E,
    0x5E2F2F71, 0x13848497, 0xA65353F5, 0xB9D1D168, 0x00000000, 0xC1EDED2C,
    0x40202060, 0xE3FCFC1F, 0x79B1B1C8, 0xB65B5BED, 0xD46A6ABE, 0x8DCBCB46,
    0x67BEBED9, 0x7239394B, 0x944A4ADE, 0x984C4CD4, 0xB05858E8, 0x85CFCF4A,
    0xBBD0D06B, 0xC5EFEF2A, 0x4FAAAAE5, 0xEDFBFB16, 0x864343C5, 0x9A4D4DD7,
    0x66333355, 0x11858594, 0x8A4545CF, 0xE9F9F910, 0x04020206, 0xFE7F7F81,
    0xA05050F0, 0x783C3C44, 0x259F9FBA, 0x4BA8A8E3, 0xA25151F3, 0x5DA3A3FE,
    0x804040C0, 0x058F8F8A, 0x3F9292AD, 0x219D9DBC, 0x70383848, 0xF1F5F504,
    0x63BCBCDF, 0x77B6B6C1, 0xAFDADA75, 0x42212163, 0x20101030, 0xE5FFFF1A,
    0xFDF3F30E, 0xBFD2D26D, 0x81CDCD4C, 0x180C0C14, 0x26131335, 0xC3ECEC2F,
    0xBE5F5FE1, 0x359797A2, 0x884444CC, 0x2E171739, 0x93C4C457, 0x55A7A7F2,
    0xFC7E7E82, 0x7A3D3D47, 0xC86464AC, 0xBA5D5DE7, 0x3219192B, 0xE6737395,
    0xC06060A0, 0x19818198, 0x9E4F4FD1, 0xA3DCDC7F, 0x44222266, 0x542A2A7E,
    0x3B9090AB, 0x0B888883, 0x8C4646CA, 0xC7EEEE29, 0x6BB8B8D3, 0x2814143C,
    0xA7DEDE79, 0xBC5E5EE2, 0x160B0B1D, 0xADDBDB76, 0xDBE0E03B, 0x64323256,
    0x743A3A4E, 0x140A0A1E, 0x924949DB, 0x0C06060A, 0x4824246C, 0xB85C5CE4,
    0x9FC2C25D, 0xBDD3D36E, 0x43ACACEF, 0xC46262A6, 0x399191A8, 0x319595A4,
    0xD3E4E437, 0xF279798B, 0xD5E7E732, 0x8BC8C843, 0x6E373759, 0xDA6D6DB7,
    0x018D8D8C, 0xB1D5D564, 0x9C4E4ED2, 0x49A9A9E0, 0xD86C6CB4, 0xAC5656FA,
    0xF3F4F407, 0xCFEAEA25, 0xCA6565AF, 0xF47A7A8E, 0x47AEAEE9, 0x10080818,
    0x6FBABAD5, 0xF0787888, 0x4A25256F, 0x5C2E2E72, 0x381C1C24, 0x57A6A6F1,
    0x73B4B4C7, 0x97C6C651, 0xCBE8E823, 0xA1DDDD7C, 0xE874749C, 0x3E1F1F21,
    0x964B4BDD, 0x61BDBDDC, 0x0D8B8B86, 0x0F8A8A85, 0xE0707090, 0x7C3E3E42,
    0x71B5B5C4, 0xCC6666AA, 0x904848D8, 0x06030305, 0xF7F6F601, 0x1C0E0E12,
    0xC26161A3, 0x6A35355F, 0xAE5757F9, 0x69B9B9D0, 0x17868691, 0x99C1C158,
    0x3A1D1D27, 0x279E9EB9, 0xD9E1E138, 0xEBF8F813, 0x2B9898B3, 0x22111133,
    0xD26969BB, 0xA9D9D970, 0x078E8E89, 0x339494A7, 0x2D9B9BB6, 0x3C1E1E22,
    0x15878792, 0xC9E9E920, 0x87CECE49, 0xAA5555FF, 0x50282878, 0xA5DFDF7A,
    0x038C8C8F, 0x59A1A1F8, 0x09898980, 0x1A0D0D17, 0x65BFBFDA, 0xD7E6E631,
    0x844242C6, 0xD06868B8, 0x824141C3, 0x299999B0, 0x5A2D2D77, 0x1E0F0F11,
    0x7BB0B0CB, 0xA85454FC, 0x6DBBBBD6, 0x2C16163A,
};

static const uint32_t rcon[] = {
    0x01000000, 0x02000000, 0x04000000, 0x08000000, 0x10000000,
    0x20000000, 0x40000000, 0x80000000, 0x1B000000, 0x36000000,
    // for 128-bit blocks, Rijndael never uses more than 10 rcon values
};

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

int aesInitForEncr(struct AesContext *ctx, const uint32_t *k) {
  uint32_t i, *ks = ctx->round_key;

  for (i = 0; i < ctx->aes_key_words; i++) {
    ks[i] = BSWAP32(k[i]);
  }

  // create round keys for encryption
  if (AES_128_KEY_WORDS == ctx->aes_key_words) {
    for (i = 0; i < 10; i++, ks += 4) {
      ks[4] = ks[0] ^ rcon[i] ^
              (((uint32_t)FwdSbox[(ks[3] >> 16) & 0xff]) << 24) ^
              (((uint32_t)FwdSbox[(ks[3] >> 8) & 0xff]) << 16) ^
              (((uint32_t)FwdSbox[(ks[3] >> 0) & 0xff]) << 8) ^
              (((uint32_t)FwdSbox[(ks[3] >> 24) & 0xff]) << 0);
      ks[5] = ks[1] ^ ks[4];
      ks[6] = ks[2] ^ ks[5];
      ks[7] = ks[3] ^ ks[6];
    }
  } else if (AES_256_KEY_WORDS == ctx->aes_key_words) {
    for (i = 0; i < 7; i++, ks += 8) {
      ks[8] = ks[0] ^ rcon[i] ^
              (((uint32_t)FwdSbox[(ks[7] >> 16) & 0xff]) << 24) ^
              (((uint32_t)FwdSbox[(ks[7] >> 8) & 0xff]) << 16) ^
              (((uint32_t)FwdSbox[(ks[7] >> 0) & 0xff]) << 8) ^
              (((uint32_t)FwdSbox[(ks[7] >> 24) & 0xff]) << 0);
      ks[9] = ks[1] ^ ks[8];
      ks[10] = ks[2] ^ ks[9];
      ks[11] = ks[3] ^ ks[10];
      if (i == 6) break;
      ks[12] = ks[4] ^ (((uint32_t)FwdSbox[(ks[11] >> 24) & 0xff]) << 24) ^
               (((uint32_t)FwdSbox[(ks[11] >> 16) & 0xff]) << 16) ^
               (((uint32_t)FwdSbox[(ks[11] >> 8) & 0xff]) << 8) ^
               (((uint32_t)FwdSbox[(ks[11] >> 0) & 0xff]) << 0);
      ks[13] = ks[5] ^ ks[12];
      ks[14] = ks[6] ^ ks[13];
      ks[15] = ks[7] ^ ks[14];
    }
  } else {
    return -1;
  }
  return 0;
}

void aesEncr(struct AesContext *ctx, const uint32_t *src, uint32_t *dst) {
  uint32_t x0, x1, x2, x3;  // we CAN use an array, but then GCC will not use
                            // registers. so we use separate vars. sigh...
  uint32_t *k = ctx->round_key, i;

  // setup
  x0 = BSWAP32(*src++) ^ *k++;
  x1 = BSWAP32(*src++) ^ *k++;
  x2 = BSWAP32(*src++) ^ *k++;
  x3 = BSWAP32(*src++) ^ *k++;

  // all-but-last round
  for (i = 0; i < ctx->aes_num_rounds - 1; i++) {
    uint32_t t0, t1, t2;

    t0 = *k++ ^ ror(FwdTab0[(x0 >> 24) & 0xff], 0) ^
         ror(FwdTab0[(x1 >> 16) & 0xff], 8) ^
         ror(FwdTab0[(x2 >> 8) & 0xff], 16) ^
         ror(FwdTab0[(x3 >> 0) & 0xff], 24);

    t1 = *k++ ^ ror(FwdTab0[(x1 >> 24) & 0xff], 0) ^
         ror(FwdTab0[(x2 >> 16) & 0xff], 8) ^
         ror(FwdTab0[(x3 >> 8) & 0xff], 16) ^
         ror(FwdTab0[(x0 >> 0) & 0xff], 24);

    t2 = *k++ ^ ror(FwdTab0[(x2 >> 24) & 0xff], 0) ^
         ror(FwdTab0[(x3 >> 16) & 0xff], 8) ^
         ror(FwdTab0[(x0 >> 8) & 0xff], 16) ^
         ror(FwdTab0[(x1 >> 0) & 0xff], 24);

    x3 = *k++ ^ ror(FwdTab0[(x3 >> 24) & 0xff], 0) ^
         ror(FwdTab0[(x0 >> 16) & 0xff], 8) ^
         ror(FwdTab0[(x1 >> 8) & 0xff], 16) ^
         ror(FwdTab0[(x2 >> 0) & 0xff], 24);

    x0 = t0;
    x1 = t1;
    x2 = t2;
  }

  // last round
  *dst++ = BSWAP32(*k++ ^ (((uint32_t)(FwdSbox[(x0 >> 24) & 0xff])) << 24) ^
                   (((uint32_t)(FwdSbox[(x1 >> 16) & 0xff])) << 16) ^
                   (((uint32_t)(FwdSbox[(x2 >> 8) & 0xff])) << 8) ^
                   (((uint32_t)(FwdSbox[(x3 >> 0) & 0xff])) << 0));

  *dst++ = BSWAP32(*k++ ^ (((uint32_t)(FwdSbox[(x1 >> 24) & 0xff])) << 24) ^
                   (((uint32_t)(FwdSbox[(x2 >> 16) & 0xff])) << 16) ^
                   (((uint32_t)(FwdSbox[(x3 >> 8) & 0xff])) << 8) ^
                   (((uint32_t)(FwdSbox[(x0 >> 0) & 0xff])) << 0));

  *dst++ = BSWAP32(*k++ ^ (((uint32_t)(FwdSbox[(x2 >> 24) & 0xff])) << 24) ^
                   (((uint32_t)(FwdSbox[(x3 >> 16) & 0xff])) << 16) ^
                   (((uint32_t)(FwdSbox[(x0 >> 8) & 0xff])) << 8) ^
                   (((uint32_t)(FwdSbox[(x1 >> 0) & 0xff])) << 0));

  *dst++ = BSWAP32(*k++ ^ (((uint32_t)(FwdSbox[(x3 >> 24) & 0xff])) << 24) ^
                   (((uint32_t)(FwdSbox[(x0 >> 16) & 0xff])) << 16) ^
                   (((uint32_t)(FwdSbox[(x1 >> 8) & 0xff])) << 8) ^
                   (((uint32_t)(FwdSbox[(x2 >> 0) & 0xff])) << 0));
}

int aesCtrInit(struct AesCtrContext *ctx, const void *k, const void *iv,
               enum AesKeyType key_type) {
  const uint32_t *p_k;
  uint32_t aligned_k[AES_BLOCK_WORDS];

  if (AES_128_KEY_TYPE == key_type) {
    ctx->aes.aes_key_words = AES_128_KEY_WORDS;
    ctx->aes.aes_num_rounds = AES_128_KEY_NUM_ROUNDS;
  } else if (AES_256_KEY_TYPE == key_type) {
    ctx->aes.aes_key_words = AES_256_KEY_WORDS;
    ctx->aes.aes_num_rounds = AES_256_KEY_NUM_ROUNDS;
  } else {
    return -1;
  }
  // if key is not aligned, copy it to stack
  if (IS_ALIGNED(k, uint32_t)) {
    p_k = (const uint32_t *)k;
  } else {
    memcpy(aligned_k, k, sizeof(aligned_k));
    p_k = aligned_k;
  }

  memcpy(ctx->iv, iv, sizeof(ctx->iv));
  return aesInitForEncr(&ctx->aes, p_k);
}

void aesCtr(struct AesCtrContext *ctx, const void *src, void *dst,
            size_t data_len) {
  const uint8_t *p_src_pos = (const uint8_t *)src;
  uint8_t *p_dst_pos = (uint8_t *)dst;
  const bool is_src_aligned = IS_ALIGNED(src, uint32_t);
  const bool is_dst_aligned = IS_ALIGNED(dst, uint32_t);
  const uint32_t *p_src;
  uint32_t *p_dst;
  uint32_t aligned_src[AES_BLOCK_WORDS];
  uint32_t aligned_dst[AES_BLOCK_WORDS];
  size_t bytes_to_process = data_len;

  while (bytes_to_process > 0) {
    size_t chunk_bytes_len =
        (bytes_to_process < AES_BLOCK_SIZE) ? bytes_to_process : AES_BLOCK_SIZE;

    // if source is not aligned or size is not multiple of words,
    // copy it to stack
    if (is_src_aligned && (chunk_bytes_len % sizeof(uint32_t) == 0)) {
      // Cast to "void *" first to prevent the -Wcast-align compiling
      // error when casting the uint8_t pointer to the uint32_t pointer.
      // Note, we already verified the alignment above before casting.
      p_src = (const uint32_t *)(void *)p_src_pos;
    } else {
      memcpy(aligned_src, p_src_pos, chunk_bytes_len);
      p_src = aligned_src;
    }

    // if destination is not aligned or full block size,
    // copy results to stack
    if (is_dst_aligned && chunk_bytes_len == AES_BLOCK_SIZE) {
      // Cast to "void *" first to prevent the -Wcast-align compiling
      // error when casting the uint8_t pointer to the uint32_t pointer.
      // Note, we already verified the alignment above before casting.
      p_dst = (uint32_t *)(void *)p_dst_pos;
    } else {
      p_dst = aligned_dst;
    }

    // encrypt/decrypt by AES/CTR mode
    // encryption and decryption are same operation in AES/CTR mode
    size_t num_words =
        (chunk_bytes_len + (sizeof(uint32_t) - 1)) / sizeof(uint32_t);
    aesEncr(&ctx->aes, ctx->iv, p_dst);
    for (size_t i = 0; i < num_words; i++) {
      p_dst[i] ^= p_src[i];
    }

    // if p_dst is aligned_dst, we used stack
    // then, copy stack to destination by safe way
    if (p_dst == aligned_dst) {
      memcpy(p_dst_pos, aligned_dst, chunk_bytes_len);
    }

    // update position and left bytes
    p_dst_pos += chunk_bytes_len;
    p_src_pos += chunk_bytes_len;
    bytes_to_process -= chunk_bytes_len;

    // increase AES block counter
    for (int i = AES_BLOCK_SIZE - 1; i >= 0; i--) {
      ((uint8_t *)ctx->iv)[i]++;
      if (((uint8_t *)ctx->iv)[i]) break;
    }
  }
}
