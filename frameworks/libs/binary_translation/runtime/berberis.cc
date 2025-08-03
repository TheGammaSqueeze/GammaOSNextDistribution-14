/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include "berberis/base/large_mmap.h"
#include "berberis/base/macros.h"
#include "berberis/base/tracing.h"
#include "berberis/guest_os_primitives/guest_map_shadow.h"
#include "berberis/guest_os_primitives/guest_thread_manager.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/runtime/init_guest_arch.h"
#include "berberis/runtime/translator.h"
#include "berberis/runtime_primitives/crash_reporter.h"
#include "berberis/runtime_primitives/guest_function_wrapper_impl.h"
#include "berberis/runtime_primitives/translation_cache.h"

namespace berberis {

namespace {

bool IsAddressGuestExecutable(GuestAddr pc) {
  return GuestMapShadow::GetInstance()->IsExecutable(pc, 1);
}

bool InitBerberisUnsafe() {
  InitLargeMmap();
  Tracing::Init();
  InitGuestThreadManager();
  InitGuestFunctionWrapper(&IsAddressGuestExecutable);
  InitTranslator();
  InitCrashReporter();
  InitGuestArch();
  return true;
}

}  // namespace

void InitBerberis() {
  // C++11 guarantees this is called only once and is thread-safe.
  static bool initialized = InitBerberisUnsafe();
  UNUSED(initialized);
}

void PreZygoteForkUnsafe() {
  TranslationCache::GetInstance()->PreZygoteForkUnsafe();
}

}  // namespace berberis
