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

#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_CRYPTO_NON_ENCRYPTION_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_CRYPTO_NON_ENCRYPTION_H_

#include <cstring>

#include "location/lbs/contexthub/nanoapps/nearby/crypto.h"

namespace nearby {

// Implements Crypto interface without encryption, i.e. echoing back the input.
class CryptoNonEncryption : public Crypto {
 public:
  // Echos input back in output. Returns the size of input. Returns 0 if
  // output_size is less than input_size.
  bool decrypt(const ByteArray &input, const ByteArray &salt,
               const ByteArray &key, ByteArray &output) const override;

  bool verify(const ByteArray &input, const ByteArray &key,
              const ByteArray &signature) const override;
};

}  // namespace nearby

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_CRYPTO_NON_ENCRYPTION_H_
