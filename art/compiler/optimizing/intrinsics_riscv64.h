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

#ifndef ART_COMPILER_OPTIMIZING_INTRINSICS_RISCV64_H_
#define ART_COMPILER_OPTIMIZING_INTRINSICS_RISCV64_H_

#include "base/macros.h"
#include "intrinsics.h"
#include "intrinsics_list.h"

namespace art HIDDEN {

class ArenaAllocator;
class HInvokeStaticOrDirect;
class HInvokeVirtual;

namespace riscv64 {

class CodeGeneratorRISCV64;
class Riscv64Assembler;

class IntrinsicLocationsBuilderRISCV64 final : public IntrinsicVisitor {
 public:
  explicit IntrinsicLocationsBuilderRISCV64(ArenaAllocator* allocator,
                                            CodeGeneratorRISCV64* codegen)
      : allocator_(allocator), codegen_(codegen) {}

  // Define visitor methods.

#define OPTIMIZING_INTRINSICS(Name, ...) \
  void Visit##Name(HInvoke* invoke) override;
  ART_INTRINSICS_WITH_HINVOKE_LIST(OPTIMIZING_INTRINSICS)
#undef OPTIMIZING_INTRINSICS

  // Check whether an invoke is an intrinsic, and if so, create a location summary. Returns whether
  // a corresponding LocationSummary with the intrinsified_ flag set was generated and attached to
  // the invoke.
  bool TryDispatch(HInvoke* invoke);

 private:
  ArenaAllocator* const allocator_;
  CodeGeneratorRISCV64* const codegen_;

  DISALLOW_COPY_AND_ASSIGN(IntrinsicLocationsBuilderRISCV64);
};

class IntrinsicCodeGeneratorRISCV64 final : public IntrinsicVisitor {
 public:
  explicit IntrinsicCodeGeneratorRISCV64(CodeGeneratorRISCV64* codegen) : codegen_(codegen) {}

  // Define visitor methods.

#define OPTIMIZING_INTRINSICS(Name, ...) \
  void Visit##Name(HInvoke* invoke);
  ART_INTRINSICS_WITH_HINVOKE_LIST(OPTIMIZING_INTRINSICS)
#undef OPTIMIZING_INTRINSICS

 private:
  Riscv64Assembler* GetAssembler();
  ArenaAllocator* GetAllocator();

  void HandleValueOf(HInvoke* invoke,
                     const IntrinsicVisitor::ValueOfInfo& info,
                     DataType::Type type);

  CodeGeneratorRISCV64* const codegen_;

  DISALLOW_COPY_AND_ASSIGN(IntrinsicCodeGeneratorRISCV64);
};

}  // namespace riscv64
}  // namespace art

#endif  // ART_COMPILER_OPTIMIZING_INTRINSICS_RISCV64_H_
