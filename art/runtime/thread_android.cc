/*
 * Copyright (C) 2011 The Android Open Source Project
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

#include <signal.h>
#include <sys/mman.h>

#include "base/globals.h"
#include "base/bit_utils.h"
#include "thread.h"

namespace art HIDDEN {

void Thread::SetUpAlternateSignalStack() {
  // Bionic does this for us.
}

void Thread::TearDownAlternateSignalStack() {
  // Bionic does this for us.
}

void Thread::MadviseAwayAlternateSignalStack() {
  stack_t old_ss;
  int result = sigaltstack(nullptr, &old_ss);
  CHECK_EQ(result, 0);
  // Only call `madvise()` on enabled page-aligned alternate signal stack. Processes can
  // create different arbitrary alternate signal stacks and we do not want to erroneously
  // `madvise()` away pages that may hold data other than the alternate signal stack.
  if ((old_ss.ss_flags & SS_DISABLE) == 0 &&
      IsAlignedParam(old_ss.ss_sp, gPageSize) &&
      IsAlignedParam(old_ss.ss_size, gPageSize)) {
    CHECK_EQ(old_ss.ss_flags & SS_ONSTACK, 0);
    // Note: We're testing and benchmarking ART on devices with old kernels
    // which may not support `MADV_FREE`, so we do not check the result.
    // It should succeed on devices with Android 12+.
    madvise(old_ss.ss_sp, old_ss.ss_size, MADV_FREE);
  }
}

}  // namespace art
