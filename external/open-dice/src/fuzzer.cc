// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License"); you may not
// use this file except in compliance with the License. You may obtain a copy of
// the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
// WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
// License for the specific language governing permissions and limitations under
// the License.

#include "dice/dice.h"
#include "dice/fuzz_utils.h"
#include "dice/utils.h"
#include "fuzzer/FuzzedDataProvider.h"

using dice::fuzz::ConsumeRandomLengthStringAsBytesFrom;
using dice::fuzz::FuzzedInputValues;

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  // Exit early if there might not be enough data to fill buffers.
  if (size < 512) {
    return 0;
  }

  FuzzedDataProvider fdp(data, size);

  // Prepare the fuzzed inputs.
  auto input_values = FuzzedInputValues::ConsumeFrom(fdp);
  uint8_t current_cdi_attest[DICE_CDI_SIZE] = {};
  uint8_t current_cdi_seal[DICE_CDI_SIZE] = {};

  fdp.ConsumeData(&current_cdi_attest, sizeof(current_cdi_attest));
  fdp.ConsumeData(&current_cdi_seal, sizeof(current_cdi_seal));

  // Initialize output parameters with fuzz data in case they are wrongly being
  // read from.
  constexpr size_t kNextCdiCertificateBufferSize = 1024;
  auto next_cdi_certificate_actual_size = fdp.ConsumeIntegral<size_t>();
  uint8_t next_cdi_certificate[kNextCdiCertificateBufferSize] = {};
  uint8_t next_cdi_attest[DICE_CDI_SIZE] = {};
  uint8_t next_cdi_seal[DICE_CDI_SIZE] = {};

  fdp.ConsumeData(&next_cdi_certificate, kNextCdiCertificateBufferSize);
  fdp.ConsumeData(&next_cdi_attest, DICE_CDI_SIZE);
  fdp.ConsumeData(&next_cdi_seal, DICE_CDI_SIZE);

  // Fuzz the main flow.
  DiceMainFlow(/*context=*/NULL, current_cdi_attest, current_cdi_seal,
               input_values, kNextCdiCertificateBufferSize,
               next_cdi_certificate, &next_cdi_certificate_actual_size,
               next_cdi_attest, next_cdi_seal);
  return 0;
}
