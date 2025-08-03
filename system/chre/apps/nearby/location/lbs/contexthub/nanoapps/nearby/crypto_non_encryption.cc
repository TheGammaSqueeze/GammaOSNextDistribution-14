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

#include "location/lbs/contexthub/nanoapps/nearby/crypto_non_encryption.h"

#include <chre/util/macros.h>

#include "third_party/contexthub/chre/util/include/chre/util/nanoapp/log.h"

#define LOG_TAG "[NEARBY][CRYPTO]"

namespace nearby {

bool CryptoNonEncryption::decrypt(const ByteArray &input,
                                  const ByteArray & /*salt*/,
                                  const ByteArray & /*key*/,
                                  ByteArray &output) const {
  if (output.length < input.length) {
    LOGE("output length %d  less than input length %d",
         (unsigned int)output.length, (unsigned int)input.length);
    return false;
  }
  memcpy(output.data, input.data, input.length);
  output.length = input.length;
  return true;
}

bool CryptoNonEncryption::verify(const ByteArray & /*input*/,
                                 const ByteArray & /*key*/,
                                 const ByteArray & /*signature*/) const {
  return true;
}

}  // namespace nearby
