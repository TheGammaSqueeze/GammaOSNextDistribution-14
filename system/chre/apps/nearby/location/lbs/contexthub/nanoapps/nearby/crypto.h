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

#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_CRYPTO_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_CRYPTO_H_

#include <cstddef>
#include <cstdint>

#include "location/lbs/contexthub/nanoapps/nearby/byte_array.h"

namespace nearby {

// Interface for crypto algorithms. Default to no encryption implementation,
// i.e. echoing back the input as plain text.
// This class will be overridden by implementations with different encryption
// algorithms such as AES.
class Crypto {
 public:
  // Decrypts input with salt and key. Places the decrypted result in output.
  // Returns true if decryption succeeds.
  // Caller needs to allocates the output memory and set the buffer size.
  virtual bool decrypt(const ByteArray &input, const ByteArray &salt,
                       const ByteArray &key, ByteArray &output) const = 0;

  // Verifies the computed signature is equal to the expected signature.
  virtual bool verify(const ByteArray &input, const ByteArray &key,
                      const ByteArray &signature) const = 0;

  virtual ~Crypto() = default;
};

}  // namespace nearby

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_CRYPTO_H_
