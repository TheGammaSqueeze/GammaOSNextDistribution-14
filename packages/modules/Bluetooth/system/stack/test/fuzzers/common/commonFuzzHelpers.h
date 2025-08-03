/*
 * Copyright 2020 The Android Open Source Project
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

#ifndef BT_STACK_FUZZ_COMMON_HELPERS_H_
#define BT_STACK_FUZZ_COMMON_HELPERS_H_

#include <fuzzer/FuzzedDataProvider.h>

#include <cstring>  // For memcpy
#include <functional>
#include <vector>

#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

// Calls a function from the ops_vector
void callArbitraryFunction(
    FuzzedDataProvider* fdp,
    std::vector<std::function<void(FuzzedDataProvider*)>> ops_vector) {
  // Choose which function we'll be calling
  uint8_t function_id =
      fdp->ConsumeIntegralInRange<uint8_t>(0, ops_vector.size() - 1);

  // Call the function we've chosen
  ops_vector[function_id](fdp);
}

template <class T>
T getArbitraryVectorElement(FuzzedDataProvider* fdp, std::vector<T> vect,
                            bool allow_null) {
  // If we're allowing null, give it a 50:50 shot at returning a zero element
  // (Or if the vector's empty)
  if (vect.empty() || (allow_null && fdp->ConsumeBool())) {
    return static_cast<T>(0);
  }

  // Otherwise, return an element from our vector
  return vect.at(fdp->ConsumeIntegralInRange<size_t>(0, vect.size() - 1));
}

RawAddress generateRawAddress(FuzzedDataProvider* fdp) {
  RawAddress retval;

  // Zero address
  for (int i = 0; i < 6; i++) {
    retval.address[i] = 0;
  }

  // Read as much as we can from the buffer and copy it in
  std::vector<uint8_t> bytes = fdp->ConsumeBytes<uint8_t>(retval.kLength);
  memcpy(retval.address, bytes.data(), bytes.size());

  return retval;
}

bluetooth::Uuid generateArbitraryUuid(FuzzedDataProvider* fdp) {
  std::vector<uint8_t> bytes_vect =
      fdp->ConsumeBytes<uint8_t>(bluetooth::Uuid::kNumBytes128);
  // We need it to be the correct size regardless of if fdp ran out of bytes
  while (bytes_vect.size() < bluetooth::Uuid::kNumBytes128) {
    bytes_vect.push_back('\0');
  }

  return bluetooth::Uuid::From128BitBE(bytes_vect.data());
}

#endif  // BT_STACK_FUZZ_COMMON_HELPERS_H_
