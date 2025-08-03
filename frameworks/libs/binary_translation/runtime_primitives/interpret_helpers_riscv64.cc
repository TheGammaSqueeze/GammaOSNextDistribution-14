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

#include "berberis/runtime_primitives/interpret_helpers.h"

#include <csignal>
#include <cstdint>

#include "berberis/base/checks.h"
#include "berberis/base/logging.h"
#include "berberis/guest_state/guest_addr.h"

namespace berberis {

namespace {

uint8_t GetRiscv64InsnSize(GuestAddr pc) {
  constexpr uint16_t kInsnLenMask = uint16_t{0b11};
  if ((*ToHostAddr<const uint16_t>(pc) & kInsnLenMask) != kInsnLenMask) {
    return 2;
  }
  return 4;
}

}  // namespace

void UndefinedInsn(GuestAddr pc) {
  auto* addr = ToHostAddr<const uint16_t>(pc);
  uint8_t size = GetRiscv64InsnSize(pc);
  if (size == 2) {
    ALOGE("Unimplemented riscv64 instruction 0x%" PRIx16 " at %p", *addr, addr);
  } else {
    CHECK_EQ(size, 4);
    // Warning: do not cast and dereference the pointer since the address may not be 4-bytes
    // aligned.
    uint32_t code;
    memcpy(&code, addr, sizeof(code));
    ALOGE("Unimplemented riscv64 instruction 0x%" PRIx32 " at %p", code, addr);
  }
  raise(SIGILL);
}

}  // namespace berberis
