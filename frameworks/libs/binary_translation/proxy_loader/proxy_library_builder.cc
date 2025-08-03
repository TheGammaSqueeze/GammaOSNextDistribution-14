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

#include "berberis/proxy_loader/proxy_library_builder.h"

#include <dlfcn.h>

#include <cstring>

#include "berberis/base/checks.h"
#include "berberis/base/logging.h"
#include "berberis/base/tracing.h"
#include "berberis/guest_state/guest_state_opaque.h"
#include "berberis/runtime_primitives/host_function_wrapper_impl.h"

namespace berberis {

void DoBadThunk() {
  LOG_ALWAYS_FATAL("Bad thunk call before %p", __builtin_return_address(0));
}

void DoBadTrampoline(HostCode callee, ThreadState* state) {
  CHECK(state);
  const char* name = static_cast<const char*>(callee);
  LOG_ALWAYS_FATAL("Bad '%s' call from %p",
                   name ? name : "[unknown name]",
                   ToHostAddr<void>(GetLinkRegister(GetCPUState(*state))));
}

void ProxyLibraryBuilder::InterceptSymbol(GuestAddr guest_addr, const char* name) {
  CHECK(guest_addr);

  // TODO(b/287342829): functions_ are sorted, use binary search!
  for (size_t i = 0; i < num_functions_; ++i) {
    const auto& function = functions_[i];
    if (strcmp(name, function.name) == 0) {
      void* thunk = function.thunk;
      if (!thunk) {
        // Default thunk.
        thunk = dlsym(handle_, name);
      }
      if (!thunk) {
        // Assume no thunk needed, all work is done by trampoline.
        thunk = reinterpret_cast<void*>(DoBadThunk);
      }
      if (function.marshal_and_call == DoBadTrampoline) {
        // HACK: DoBadTrampoline needs function name passed as callee!
        MakeTrampolineCallable(guest_addr, false, DoBadTrampoline, name, name);
      } else {
        MakeTrampolineCallable(guest_addr, false, function.marshal_and_call, thunk, name);
      }
      return;
    }
  }

  // TODO(b/287342829): variables_ are sorted, use binary search!
  for (size_t i = 0; i < num_variables_; ++i) {
    const auto& variable = variables_[i];
    if (strcmp(name, variable.name) == 0) {
      if (variable.size != sizeof(GuestAddr)) {
        // TODO(b/287342829): at the moment, all intercepted variables are assumed to be pointers!
        TRACE("proxy library \"%s\": size mismatch for variable \"%s\"", library_name_, name);
      }
      void* addr = dlsym(handle_, name);
      if (!addr) {
        TRACE("proxy library \"%s\": symbol for variable \"%s\" is NULL", library_name_, name);
      } else {
        // TODO(b/287342829): copy variable.size bytes instead!
        memcpy(ToHostAddr<void>(guest_addr), addr, sizeof(GuestAddr));
      }
      return;
    }
  }

  TRACE("proxy library \"%s\": symbol \"%s\" not found", library_name_, name);
}

void ProxyLibraryBuilder::Build(const char* library_name,
                                size_t size_translation,
                                const KnownTrampoline* translations,
                                size_t size_data_symbols,
                                const KnownVariable* variables) {
  handle_ = dlopen(library_name, RTLD_GLOBAL);
  if (!handle_) {
    LOG_ALWAYS_FATAL("dlopen failed: %s: %s", library_name, dlerror());
  }

  library_name_ = library_name;
  num_functions_ = size_translation;
  functions_ = translations;
  num_variables_ = size_data_symbols;
  variables_ = variables;
}

}  // namespace berberis
