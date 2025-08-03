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

#ifndef BERBERIS_TEST_UTILS_TESTING_RUN_GENERATED_CODE_H_
#define BERBERIS_TEST_UTILS_TESTING_RUN_GENERATED_CODE_H_

#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state.h"
#include "berberis/runtime_primitives/host_code.h"
#include "berberis/runtime_primitives/runtime_library.h"
#include "berberis/runtime_primitives/translation_cache.h"

namespace berberis {

inline void TestingRunGeneratedCode(ThreadState* state, HostCode code, GuestAddr stop_pc) {
  auto cache = TranslationCache::GetInstance();
  cache->SetStop(stop_pc);
  berberis_RunGeneratedCode(state, code);
  cache->TestingClearStop(stop_pc);
}

}  // namespace berberis

#endif  // BERBERIS_TEST_UTILS_TESTING_RUN_GENERATED_CODE_H_
