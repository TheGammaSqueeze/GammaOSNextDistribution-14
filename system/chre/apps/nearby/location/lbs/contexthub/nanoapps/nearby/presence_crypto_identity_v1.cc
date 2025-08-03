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

#include "location/lbs/contexthub/nanoapps/nearby/presence_crypto_identity_v1.h"

#include "location/lbs/contexthub/nanoapps/nearby/crypto/aes.h"
#include "location/lbs/contexthub/nanoapps/nearby/crypto/hkdf.h"
#include "third_party/contexthub/chre/util/include/chre/util/macros.h"
#include "third_party/contexthub/chre/util/include/chre/util/nanoapp/log.h"
#define LOG_TAG "[NEARBY][PRESENCE_CRYPTO_V1]"
namespace nearby {
bool PresenceCryptoIdentityV1Impl::decrypt(const ByteArray &input,
                                           const ByteArray &salt,
                                           const ByteArray &key,
                                           ByteArray &output) const {
  if (key.length != kAuthenticityKeySize) {
    LOGE("Invalid authenticity key size");
    return false;
  }
  if (salt.length != kSaltSize) {
    LOGE("Invalid salt size");
    return false;
  }
  if (input.length != output.length) {
    LOGE("Input and output data length are different");
    return false;
  }
  // Generate a 32 bytes decryption key from authenticity_key
  uint8_t decryption_key[kEncryptionKeySize] = {0};
  hkdf(kEkIv, ARRAY_SIZE(kEkIv), key.data, key.length, decryption_key,
       ARRAY_SIZE(decryption_key));
  // Decrypt the input cipher text using the decryption key
  uint8_t e_salt[kAesCtrIvSize] = {0};
  hkdf(kEsaltIv, ARRAY_SIZE(kEsaltIv), salt.data, salt.length, e_salt,
       ARRAY_SIZE(e_salt));
  struct AesCtrContext ctx;
  if (aesCtrInit(&ctx, decryption_key, e_salt, AES_256_KEY_TYPE) < 0) {
    LOGE("aesCtrInit() is failed");
    return false;
  }
  aesCtr(&ctx, input.data, output.data, output.length);
  return true;
}

bool PresenceCryptoIdentityV1Impl::verify(const ByteArray &input,
                                          const ByteArray &key,
                                          const ByteArray &signature) const {
  UNUSED_VAR(key);
  if (input.data == nullptr || signature.data == nullptr) {
    LOGE("Null pointer was found in input parameter");
    return false;
  }
  if (signature.length != kHmacTagSize) {
    LOGE("Invalid signature size");
    return false;
  }
  // Generates a 8 bytes HMAC tag from the data
  uint8_t hmac_tag[kHmacTagSize] = {0};
  hkdf(kKtagIv, ARRAY_SIZE(kKtagIv), input.data, input.length, hmac_tag,
       ARRAY_SIZE(hmac_tag));
  // Verifies the generated HMAC tag matching the signature
  return memcmp(hmac_tag, signature.data, signature.length) == 0;
}
}  // namespace nearby
