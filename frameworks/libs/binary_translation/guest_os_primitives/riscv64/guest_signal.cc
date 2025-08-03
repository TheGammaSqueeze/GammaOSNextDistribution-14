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

#include "berberis/guest_os_primitives/guest_signal.h"

#include "berberis/base/tracing.h"

namespace berberis {

size_t GetGuest_MINSIGSTKSZ() {
  // See bionic/libc/kernel/uapi/asm-riscv/asm/signal.h
  return 2048;
}

void CheckSigactionRestorer(const Guest_sigaction* /*guest_sa*/) {
  TRACE("Ignoring riscv sa_restorer in guest sigaction");
}

void ResetSigactionRestorer(Guest_sigaction* /*guest_sa*/) {
  // sa_restorer is absent in sigaction for riscv.
}

}  // namespace berberis