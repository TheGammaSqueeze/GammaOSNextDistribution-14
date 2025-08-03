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

#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_PRESENCE_CRYPTO_V1_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_PRESENCE_CRYPTO_V1_H_
#include "location/lbs/contexthub/nanoapps/nearby/crypto.h"
namespace nearby {
// Implements Crypto interface for Data Elements in Presence v1 specification.
// Crypto algorithms: AES/CTR, HMAC, HKDF, SHA256.
class PresenceCryptoV1Impl : public Crypto {
 public:
  // Decrypts input with salt and key. Places the decrypted result in output.
  bool decrypt(const ByteArray &input, const ByteArray &salt,
               const ByteArray &key, ByteArray &output) const override;
  // Verifies the computed HMAC tag is equal to the signature.
  bool verify(const ByteArray &input, const ByteArray &key,
              const ByteArray &signature) const override;

 private:
  static constexpr size_t kAuthenticityKeySize = 16;
  static constexpr size_t kEncryptionKeySize = 32;
  static constexpr size_t kAesCtrIvSize = 16;
  static constexpr size_t kHmacTagSize = 16;
  static constexpr size_t kSaltSize = 2;
  static constexpr uint8_t kAkIv[] = {0x0C, 0xC5, 0x13, 0x17, 0x60, 0x39,
                                      0xC5, 0x13, 0x75, 0xE1, 0x8C, 0xC3,
                                      0x56, 0xE7, 0xDF, 0xB2};
  static constexpr uint8_t kAsaltIv[] = {0x6F, 0x30, 0xAD, 0xB1, 0xF6, 0x9A,
                                         0xF0, 0x49, 0x2B, 0x37, 0x66, 0x81,
                                         0x3A, 0xED, 0x8F, 0x04};
  static constexpr uint8_t kHkIv[] = {0x0C, 0xC5, 0x13, 0x17, 0x60, 0x39,
                                      0xC5, 0x13, 0x75, 0xE1, 0x8C, 0xC3,
                                      0x56, 0xE7, 0xDF, 0xB2};
};
}  // namespace nearby
#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_PRESENCE_CRYPTO_V1_H_
