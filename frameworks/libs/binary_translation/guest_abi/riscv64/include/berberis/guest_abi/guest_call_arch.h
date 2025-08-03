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

#ifndef BERBERIS_GUEST_ABI_GUEST_CALL_ARCH_H_
#define BERBERIS_GUEST_ABI_GUEST_CALL_ARCH_H_

#include <cstddef>
#include <cstdint>

#include "berberis/guest_abi/guest_arguments_arch.h"
#include "berberis/guest_state/guest_addr.h"

namespace berberis {

class GuestCall {
 public:
  GuestCall() : buf_{} {}

  void AddArgInt32(uint32_t arg);
  void AddArgInt64(uint64_t arg);

  void RunVoid(GuestAddr func_addr);
  uint32_t RunResInt32(GuestAddr func_addr);
  uint64_t RunResInt64(GuestAddr func_addr);

  static_assert(sizeof(GuestAddr) == sizeof(uint64_t), "unexpected sizeof(GuestAddr)");
  void AddArgGuestAddr(GuestAddr arg) { AddArgInt64(arg); }
  GuestAddr RunResGuestAddr(GuestAddr func_addr) { return RunResInt64(func_addr); }

  static_assert(sizeof(size_t) == sizeof(uint64_t), "unexpected sizeof(size_t)");
  void AddArgGuestSize(size_t arg) { AddArgInt64(arg); }

 private:
  GuestArgumentBuffer buf_;
};

}  // namespace berberis

#endif  // BERBERIS_GUEST_ABI_GUEST_CALL_ARCH_H_
