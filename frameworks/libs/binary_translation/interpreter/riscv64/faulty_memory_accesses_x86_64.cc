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

#include "faulty_memory_accesses.h"

#include <cstdint>
#include <utility>

#include "berberis/base/checks.h"
#include "berberis/runtime_primitives/recovery_code.h"

namespace berberis {

namespace {

extern "C" FaultyLoadResult FaultyLoad8(const void*);
extern "C" FaultyLoadResult FaultyLoad16(const void*);
extern "C" FaultyLoadResult FaultyLoad32(const void*);
extern "C" FaultyLoadResult FaultyLoad64(const void*);
extern "C" char g_faulty_load_recovery;

__asm__(
    R"(
   .globl FaultyLoad8
   .balign 16
FaultyLoad8:
   movzbl (%rdi), %eax
   movl $0, %edx
   ret

   .globl FaultyLoad16
   .balign 16
FaultyLoad16:
   movzwl (%rdi), %eax
   movl $0, %edx
   ret

   .globl FaultyLoad32
   .balign 16
FaultyLoad32:
   movl (%rdi), %eax
   movl $0, %edx
   ret

   .globl FaultyLoad64
   .balign 16
FaultyLoad64:
   movq (%rdi), %rax
   movl $0, %edx
   ret

   .globl g_faulty_load_recovery
g_faulty_load_recovery:
   movl $1, %edx
   ret
)");

extern "C" bool FaultyStore8(void*, uint64_t);
extern "C" bool FaultyStore16(void*, uint64_t);
extern "C" bool FaultyStore32(void*, uint64_t);
extern "C" bool FaultyStore64(void*, uint64_t);
extern "C" char g_faulty_store_recovery;

__asm__(
    R"(
   .globl FaultyStore8
   .balign 16
FaultyStore8:
   movb %sil, (%rdi)
   movl $0, %eax
   ret

   .globl FaultyStore16
   .balign 16
FaultyStore16:
   movw %si, (%rdi)
   movl $0, %eax
   ret

   .globl FaultyStore32
   .balign 16
FaultyStore32:
   movl %esi, (%rdi)
   movl $0, %eax
   ret

   .globl FaultyStore64
   .balign 16
FaultyStore64:
   movq %rsi, (%rdi)
   movl $0, %eax
   ret

   .globl g_faulty_store_recovery
g_faulty_store_recovery:
   movl $1, %eax
   ret
)");

template <typename FaultyAccessPointer>
std::pair<uintptr_t, uintptr_t> MakePairAdapter(FaultyAccessPointer fault_addr,
                                                void* recovery_addr) {
  return {reinterpret_cast<uintptr_t>(fault_addr), reinterpret_cast<uintptr_t>(recovery_addr)};
}

}  // namespace

FaultyLoadResult FaultyLoad(const void* addr, uint8_t data_bytes) {
  CHECK_LE(data_bytes, 8);

  FaultyLoadResult result;
  switch (data_bytes) {
    case 1:
      result = FaultyLoad8(addr);
      break;
    case 2:
      result = FaultyLoad16(addr);
      break;
    case 4:
      result = FaultyLoad32(addr);
      break;
    case 8:
      result = FaultyLoad64(addr);
      break;
    default:
      LOG_ALWAYS_FATAL("Unexpected FaultyLoad access size");
  }

  return result;
}

bool FaultyStore(void* addr, uint8_t data_bytes, uint64_t value) {
  CHECK_LE(data_bytes, 8);

  bool is_fault;
  switch (data_bytes) {
    case 1:
      is_fault = FaultyStore8(addr, value);
      break;
    case 2:
      is_fault = FaultyStore16(addr, value);
      break;
    case 4:
      is_fault = FaultyStore32(addr, value);
      break;
    case 8:
      is_fault = FaultyStore64(addr, value);
      break;
    default:
      LOG_ALWAYS_FATAL("Unexpected FaultyLoad access size");
  }

  return is_fault;
}

void AddFaultyMemoryAccessRecoveryCode() {
  InitExtraRecoveryCodeUnsafe({
      MakePairAdapter(&FaultyLoad8, &g_faulty_load_recovery),
      MakePairAdapter(&FaultyLoad16, &g_faulty_load_recovery),
      MakePairAdapter(&FaultyLoad32, &g_faulty_load_recovery),
      MakePairAdapter(&FaultyLoad64, &g_faulty_load_recovery),
      MakePairAdapter(&FaultyStore8, &g_faulty_store_recovery),
      MakePairAdapter(&FaultyStore16, &g_faulty_store_recovery),
      MakePairAdapter(&FaultyStore32, &g_faulty_store_recovery),
      MakePairAdapter(&FaultyStore64, &g_faulty_store_recovery),
  });
}

void* FindFaultyMemoryAccessRecoveryAddrForTesting(void* fault_addr) {
  if (fault_addr == &FaultyLoad8 || fault_addr == &FaultyLoad16 || fault_addr == &FaultyLoad32 ||
      fault_addr == &FaultyLoad64) {
    return &g_faulty_load_recovery;
  }
  if (fault_addr == &FaultyStore8 || fault_addr == &FaultyStore16 || fault_addr == &FaultyStore32 ||
      fault_addr == &FaultyStore64) {
    return &g_faulty_store_recovery;
  }
  return nullptr;
}

}  // namespace berberis
