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

#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_CRYPTO_AES_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_CRYPTO_AES_H_

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Supported AES mode:
 * - AES/CTR with 128/256-bit key
 *
 * External APIs:
 *  - aesCtrInit() for AES/CTR initialization
 *  - aesCtr() for AES/CTR encryption and decryption
 */

#include <stddef.h>
#include <stdint.h>

#define AES_128_KEY_WORDS 4
#define AES_192_KEY_WORDS 6
#define AES_256_KEY_WORDS 8
#define AES_KEY_MAX_WORDS AES_256_KEY_WORDS
#define AES_BLOCK_WORDS 4
#define AES_BLOCK_SIZE 16  // in bytes
// AES key type
enum AesKeyType { AES_128_KEY_TYPE, AES_192_KEY_TYPE, AES_256_KEY_TYPE };

// basic AES context
struct AesContext {
  uint32_t round_key[AES_KEY_MAX_WORDS * 4 + 28];
  uint32_t aes_key_words;
  uint32_t aes_num_rounds;
};

// basic AES block ops
int aesInitForEncr(struct AesContext *ctx, const uint32_t *k);
void aesEncr(struct AesContext *ctx, const uint32_t *src, uint32_t *dst);

// AES-CTR context
struct AesCtrContext {
  struct AesContext aes;
  uint32_t iv[AES_BLOCK_WORDS];
};

/**
 * aesCtrInit:
 * @ctx: AES/CTR context
 * @k: AES encryption/decryption key.
 *     the size must match with the key type.
 * @iv: AES/CTR 16 byte counter block.
 *     the size must fit exactly 16 bytes.
 * @key_type: AES encryption/decryption key type.
 *     it must be either AES_128_KEY_TYPE or AES_256_KEY_TYPE.
 *
 * Initialize AES/CTR by
 * creating round keys and copying the counter blocks
 *
 * Returns: 0 if key_type is valid otherwise -1
 */
int aesCtrInit(struct AesCtrContext *ctx, const void *k, const void *iv,
               enum AesKeyType key_type);

/**
 * aesCtr:
 * @ctx: AES/CTR context initialized
 * @src: source. plain text for encryption or cipher text for decryption.
 *       the size must be same as the size of destination.
 * @dst: destination. cipher text for encryption or plain text for decryption.
 *       the size must be same as the size of source.
 * @data_len: number of bytes of the source or destination byte array
 *
 * Encrypt/Decrypt by AES/CTR mode
 *
 * Returns:
 */
void aesCtr(struct AesCtrContext *ctx, const void *src, void *dst,
            size_t data_len);

#ifdef __cplusplus
}
#endif
#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_CRYPTO_AES_H_
