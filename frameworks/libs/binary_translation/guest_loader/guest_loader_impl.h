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

#ifndef BERBERIS_GUEST_LOADER_GUEST_LOADER_IMPL_H_
#define BERBERIS_GUEST_LOADER_GUEST_LOADER_IMPL_H_

#include <cstddef>  // size_t
#include <cstdint>  // uint8_t

#include "berberis/base/stringprintf.h"
#include "berberis/guest_abi/guest_function_wrapper.h"
#include "berberis/guest_abi/guest_type.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state_opaque.h"
#include "berberis/runtime_primitives/host_code.h"
#include "berberis/tiny_loader/loaded_elf_file.h"

namespace berberis {

// TODO(b/280544942): Consider moving these paths to native_bridge_support.
// Define these path constants for the target guest architecture.
extern const char* kAppProcessPath;
extern const char* kPtInterpPath;
extern const char* kVdsoPath;
extern const char* kProxyPrefix;

GuestAddr InitKernelArgs(GuestAddr guest_sp,
                         size_t argc,
                         const char* argv[],
                         char* envp[],
                         GuestAddr linker_base_addr,
                         GuestAddr main_executable_entry_point,
                         GuestAddr phdr,
                         size_t phdr_count,
                         GuestAddr ehdr_vdso,
                         const uint8_t (*random_bytes)[16]);

bool MakeElfSymbolTrampolineCallable(const LoadedElfFile& elf_file,
                                     const char* elf_file_label,
                                     const char* symbol_name,
                                     void (*callback)(HostCode, ThreadState*),
                                     HostCode arg,
                                     std::string* error_msg);

void InitializeLinkerCallbacksToStubs(LinkerCallbacks* linker_callbacks);
// Registers architecture-agnostic linker callbacks.
bool InitializeLinkerCallbacks(LinkerCallbacks* linker_callbacks,
                               const LoadedElfFile& linker_elf_file,
                               std::string* error_msg);
// Registers guest architecture-specific callbacks.
bool InitializeLinkerCallbacksArch(LinkerCallbacks* linker_callbacks,
                                   const LoadedElfFile& linker_elf_file,
                                   std::string* error_msg);

void InitLinkerDebug(const LoadedElfFile& linker_elf_file);

// For InitializeLinkerCallbacks implementations.
template <typename T>
bool FindSymbol(const LoadedElfFile& elf_file,
                const char* symbol_name,
                T* fn,
                std::string* error_msg) {
  T guest_fn = reinterpret_cast<T>(elf_file.FindSymbol(symbol_name));
  if (guest_fn == nullptr) {
    *error_msg = StringPrintf("symbol not found: %s", symbol_name);
    return false;
  }

  *fn = WrapGuestFunction(GuestType(guest_fn), symbol_name);

  return true;
}

}  // namespace berberis

#endif  // BERBERIS_GUEST_LOADER_GUEST_LOADER_IMPL_H_