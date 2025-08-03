/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include <stdio.h>
#include <stdlib.h>

#include "log.h"
#include "sigchain.h"

// We cannot annotate the declarations, as they are not no-return in the non-fake version.
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunknown-pragmas"
#pragma GCC diagnostic ignored "-Wmissing-noreturn"

namespace art {

extern "C" void EnsureFrontOfChain([[maybe_unused]] int signal) {
  LogError("EnsureFrontOfChain is not exported by the main executable.");
  abort();
}

extern "C" void AddSpecialSignalHandlerFn([[maybe_unused]] int signal,
                                          [[maybe_unused]] SigchainAction* sa) {
  LogError("SetSpecialSignalHandlerFn is not exported by the main executable.");
  abort();
}

extern "C" void RemoveSpecialSignalHandlerFn([[maybe_unused]] int signal,
                                             [[maybe_unused]] bool (*fn)(int, siginfo_t*, void*)) {
  LogError("SetSpecialSignalHandlerFn is not exported by the main executable.");
  abort();
}

extern "C" void SkipAddSignalHandler([[maybe_unused]] bool value) {
  LogError("SkipAddSignalHandler is not exported by the main executable.");
  abort();
}

#pragma GCC diagnostic pop

}  // namespace art
