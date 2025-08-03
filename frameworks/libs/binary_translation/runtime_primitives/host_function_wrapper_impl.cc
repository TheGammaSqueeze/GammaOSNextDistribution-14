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

#include "berberis/runtime_primitives/host_function_wrapper_impl.h"

#include "berberis/assembler/machine_code.h"
#include "berberis/base/exec_region_anonymous.h"
#include "berberis/base/tracing.h"
#include "berberis/code_gen_lib/gen_adaptor.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/runtime_primitives/checks.h"
#include "berberis/runtime_primitives/code_pool.h"
#include "berberis/runtime_primitives/host_code.h"
#include "berberis/runtime_primitives/translation_cache.h"

namespace berberis {

void MakeTrampolineCallable(GuestAddr pc,
                            bool is_host_func,
                            TrampolineFunc func,
                            HostCode arg,
                            const char* name) {
  if (!pc) {
    return;
  }

  // Guest address for wrapped host function must be properly aligned, otherwise
  // the guest simply can't encode it to call by immediate. We are unlikely affected,
  // as calling an external symbol by immediate requires text relocation, but
  // we should still issue an error.
  if (!IsProgramCounterProperlyAlignedForArch(pc)) {
    TRACE("address %p of wrapped host function '%s' is not aligned", ToHostAddr<void>(pc), name);
  }
  TranslationCache* cache = TranslationCache::GetInstance();
  GuestCodeEntry* entry = cache->AddAndLockForWrapping(pc);
  if (entry) {
    MachineCode mc;
    GenTrampolineAdaptor(&mc, pc, AsHostCode(func), arg, name);
    cache->SetWrappedAndUnlock(
        pc, entry, is_host_func, {GetDefaultCodePoolInstance()->Add(&mc), mc.install_size()});
  }
}

void* UnwrapHostFunction(GuestAddr pc) {
  if (TranslationCache::GetInstance()->IsHostFunctionWrapped(pc)) {
    // Wrapped entry.
    return ToHostAddr<void>(pc);
  }
  return nullptr;
}

}  // namespace berberis
