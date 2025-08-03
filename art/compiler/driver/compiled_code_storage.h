/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef ART_COMPILER_DRIVER_COMPILED_CODE_STORAGE_H_
#define ART_COMPILER_DRIVER_COMPILED_CODE_STORAGE_H_

#include <string>

#include "base/array_ref.h"
#include "base/macros.h"

namespace art HIDDEN {

namespace linker {
class LinkerPatch;
}  // namespace linker

class CompiledMethod;
enum class InstructionSet;

// Interface for storing AOT-compiled artifacts.
// These artifacts include compiled method code and related stack maps and
// linker patches as well as the compiled thunk code required for some kinds
// of linker patches.
//
// This interface is used for passing AOT-compiled code and metadata produced
// by the `libart-compiler` to `dex2oat`. The `CompiledMethod` created by
// `dex2oat` is completely opaque to the `libart-compiler`.
class CompiledCodeStorage {
 public:
  virtual CompiledMethod* CreateCompiledMethod(InstructionSet instruction_set,
                                               ArrayRef<const uint8_t> code,
                                               ArrayRef<const uint8_t> stack_map,
                                               ArrayRef<const uint8_t> cfi,
                                               ArrayRef<const linker::LinkerPatch> patches,
                                               bool is_intrinsic) = 0;

  // TODO: Rewrite the interface for passing thunks to the `dex2oat` to reduce
  // locking. The `OptimizingCompiler` is currently calling `GetThunkCode()`
  // and locking a mutex there for every `LinkerPatch` that needs a thunk to
  // check whether we need to compile it. Using a thunk compiler interface,
  // we could drive this from the `dex2oat` side and lock the mutex at most
  // once per `CreateCompiledMethod()` for any number of patches.
  virtual ArrayRef<const uint8_t> GetThunkCode(const linker::LinkerPatch& patch,
                                               /*out*/ std::string* debug_name = nullptr) = 0;
  virtual void SetThunkCode(const linker::LinkerPatch& patch,
                            ArrayRef<const uint8_t> code,
                            const std::string& debug_name) = 0;

 protected:
  CompiledCodeStorage() {}
  ~CompiledCodeStorage() {}

 private:
  DISALLOW_COPY_AND_ASSIGN(CompiledCodeStorage);
};

}  // namespace art

#endif  // ART_COMPILER_DRIVER_COMPILED_CODE_STORAGE_H_
