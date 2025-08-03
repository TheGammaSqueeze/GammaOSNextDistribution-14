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

#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_PRESENCE_CRYPTO_IDENTITY_V1_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_PRESENCE_CRYPTO_IDENTITY_V1_H_
#include "location/lbs/contexthub/nanoapps/nearby/crypto.h"
namespace nearby {
// Implements Crypto interface for Identity in Presence v1 specification.
// Crypto algorithms: AES/CTR, HMAC, HKDF, SHA256.
class PresenceCryptoIdentityV1Impl : public Crypto {
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
  static constexpr size_t kHmacTagSize = 8;
  static constexpr size_t kSaltSize = 2;
  static constexpr uint8_t kEkIv[] = {0x0E, 0x85, 0xD9, 0x2A, 0x6D, 0x7F,
                                      0x53, 0x1B, 0x1B, 0x0B, 0x5B, 0xDA,
                                      0x5C, 0x11, 0xAC, 0x42};
  static constexpr uint8_t kEsaltIv[] = {0x2E, 0x53, 0xED, 0x0A, 0x81, 0xE1,
                                         0xE1, 0x0C, 0x1F, 0x4C, 0x3F, 0xF7,
                                         0x21, 0xBE, 0x0F, 0xF6};
  static constexpr uint8_t kKtagIv[] = {0xEA, 0xAD, 0xFA, 0x43, 0x10, 0x9D,
                                        0xF3, 0xF7, 0x08, 0xFD, 0xF0, 0x25,
                                        0xB5, 0x2F, 0x01, 0xC8};
};
}  // namespace nearby
#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_PRESENCE_CRYPTO_IDENTITY_V1_H_
