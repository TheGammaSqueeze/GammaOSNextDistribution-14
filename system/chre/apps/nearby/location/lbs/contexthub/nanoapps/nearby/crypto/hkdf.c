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

#include "location/lbs/contexthub/nanoapps/nearby/crypto/hkdf.h"

#include <string.h>

#include "location/lbs/contexthub/nanoapps/nearby/crypto/hmac.h"
#include "location/lbs/contexthub/nanoapps/nearby/crypto/sha2.h"

static void hkdfExpand(const void *inPrk, const size_t prkLen, void *outKm,
                       const size_t okmLen) {
  uint8_t buf[SHA2_HASH_SIZE + 1];
  uint8_t exp_hmac[SHA2_HASH_SIZE];
  memset(buf, 0, sizeof(buf));
  memset(exp_hmac, 0, sizeof(exp_hmac));

  // calculates how many sha256 hash blocks are required for the output length
  const size_t num_blocks = (okmLen + SHA2_HASH_SIZE - 1) / SHA2_HASH_SIZE;
  if (num_blocks >= 256u) return;

  // initializes hmac context with the input pseudorandom key
  struct HmacContext hmacCtx;
  hmacInit(&hmacCtx, inPrk, prkLen);

  /**
   * https://tools.ietf.org/html/rfc5869#section-2.3
   * The output OKM is calculated as follows:
   *    N = ceil(L/HashLen)
   *    T = T(1) | T(2) | T(3) | ... | T(N)
   *    OKM = first L octets of T
   *    where:
   *    T(0) = empty string (zero length)
   *    T(1) = HMAC-Hash(PRK, T(0) | info | 0x01)
   *    T(2) = HMAC-Hash(PRK, T(1) | info | 0x02)
   *    T(3) = HMAC-Hash(PRK, T(2) | info | 0x03)
   */

  for (size_t i = 0; i < num_blocks; i++) {
    size_t block_input_len = 0;
    size_t block_output_len;

    if (i != 0) {
      memcpy(buf, exp_hmac, sizeof(exp_hmac));
      block_input_len = sizeof(exp_hmac);
    }
    *(buf + block_input_len++) = (uint8_t)(i + 1);
    // initializes hash context without refreshing HMAC keys
    hmacUpdateHashInit(&hmacCtx, buf, block_input_len);
    hmacFinish(&hmacCtx, exp_hmac, sizeof(exp_hmac));

    if (SHA2_HASH_SIZE < okmLen - i * SHA2_HASH_SIZE) {
      block_output_len = SHA2_HASH_SIZE;
    } else {
      block_output_len = okmLen - i * SHA2_HASH_SIZE;
    }
    memcpy((uint8_t *)outKm + i * SHA2_HASH_SIZE, exp_hmac, block_output_len);
  }
}

void hkdf(const void *inSalt, const size_t saltLen, const void *inKm,
          const size_t ikmLen, void *outKm, const size_t okmLen) {
  // refers to hkdf implementation in key master
  // https://source.corp.google.com/aosp-android11/system/keymaster/km_openssl/hkdf.cpp

  if (outKm == NULL || okmLen == 0) return;

  // pseudorandom key
  uint8_t prk_hmac[SHA2_HASH_SIZE];

  /**
   * Step 1. Extract: PRK = HMAC-SHA256(salt, IKM)
   * https://tools.ietf.org/html/rfc5869#section-2.2
   * Generates a pseudorandom key by HMAC-SHA256
   */
  hmacSha256(inSalt, saltLen, inKm, ikmLen, prk_hmac, sizeof(prk_hmac));

  /**
   * Step 2. Expand: OUTPUT = HKDF-Expand(PRK, L)
   * https://tools.ietf.org/html/rfc5869#section-2.3
   * Note: the optional info is not used for Nearby
   */
  hkdfExpand(prk_hmac, sizeof(prk_hmac), outKm, okmLen);
}
