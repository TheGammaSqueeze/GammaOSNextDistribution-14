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

#ifndef BERBERIS_LOADER_PROXY_LIBRARY_BUILDER_H_
#define BERBERIS_LOADER_PROXY_LIBRARY_BUILDER_H_

#include "berberis/guest_state/guest_addr.h"
#include "berberis/runtime_primitives/host_function_wrapper_impl.h"  // TrampolineFunc

namespace berberis {

struct ThreadState;

struct KnownTrampoline {
  const char* name;
  TrampolineFunc marshal_and_call;
  void* thunk;
};

struct KnownVariable {
  const char* name;
  size_t size;
};

// TODO(eaeltsin): these are stubs used by known trampolines, consider killing!
void DoBadThunk();
void DoBadTrampoline(HostCode callee, ThreadState* state);

class ProxyLibraryBuilder {
 public:
  ProxyLibraryBuilder() = default;

  void Build(const char* library_name,
             size_t size_translation,
             const KnownTrampoline* translations,
             size_t size_data_symbols,
             const KnownVariable* variables);

  void InterceptSymbol(GuestAddr guest_addr, const char* name);

 private:
  const char* library_name_ = nullptr;
  size_t num_functions_ = 0;
  const KnownTrampoline* functions_ = nullptr;
  size_t num_variables_ = 0;
  const KnownVariable* variables_ = nullptr;
  void* handle_ = nullptr;
};

// Assume kKnownTrampolines and kKnownVariables defined.
#define DEFINE_INIT_PROXY_LIBRARY(soname)                                    \
  extern "C" void InitProxyLibrary(ProxyLibraryBuilder* builder) {           \
    builder->Build(soname,                                                   \
                   sizeof(kKnownTrampolines) / sizeof(kKnownTrampolines[0]), \
                   kKnownTrampolines,                                        \
                   sizeof(kKnownVariables) / sizeof(kKnownVariables[0]),     \
                   kKnownVariables);                                         \
  }

}  // namespace berberis

#endif  // BERBERIS_LOADER_PROXY_LIBRARY_BUILDER_H_
