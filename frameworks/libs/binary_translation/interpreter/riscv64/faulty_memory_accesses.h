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

#ifndef BERBERIS_INTERPRETER_RISCV64_FAULTY_MEMORY_ACCESSES_H_
#define BERBERIS_INTERPRETER_RISCV64_FAULTY_MEMORY_ACCESSES_H_

#include <cstdint>

namespace berberis {

struct FaultyLoadResult {
  uint64_t value;
  uint64_t is_fault;
};

FaultyLoadResult FaultyLoad(const void* addr, uint8_t data_bytes);
bool FaultyStore(void* addr, uint8_t data_bytes, uint64_t value);

void AddFaultyMemoryAccessRecoveryCode();
void* FindFaultyMemoryAccessRecoveryAddrForTesting(void* fault_addr);

}  // namespace berberis

#endif  // BERBERIS_INTERPRETER_RISCV64_FAULTY_MEMORY_ACCESSES_H_
