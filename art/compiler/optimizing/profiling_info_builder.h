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

#ifndef ART_COMPILER_OPTIMIZING_PROFILING_INFO_BUILDER_H_
#define ART_COMPILER_OPTIMIZING_PROFILING_INFO_BUILDER_H_

#include "base/macros.h"
#include "nodes.h"

namespace art HIDDEN {

class CodeGenerator;
class CompilerOptions;
class HInliner;
class InlineCache;
class ProfilingInfo;

class ProfilingInfoBuilder final : public HGraphDelegateVisitor {
 public:
  ProfilingInfoBuilder(HGraph* graph,
                       const CompilerOptions& compiler_options,
                       CodeGenerator* codegen,
                       OptimizingCompilerStats* stats = nullptr)
      : HGraphDelegateVisitor(graph, stats),
        codegen_(codegen),
        compiler_options_(compiler_options) {}

  void Run();

  static constexpr const char* kProfilingInfoBuilderPassName =
      "profiling_info_builder";

  static InlineCache* GetInlineCache(ProfilingInfo* info,
                                     const CompilerOptions& compiler_options,
                                     HInvoke* invoke);
  static bool IsInlineCacheUseful(HInvoke* invoke, CodeGenerator* codegen);
  static uint32_t EncodeInlinedDexPc(
      const HInliner* inliner, const CompilerOptions& compiler_options, HInvoke* invoke)
      REQUIRES_SHARED(Locks::mutator_lock_);

 private:
  void VisitInvokeVirtual(HInvokeVirtual* invoke) override;
  void VisitInvokeInterface(HInvokeInterface* invoke) override;

  void HandleInvoke(HInvoke* invoke);

  CodeGenerator* codegen_;
  const CompilerOptions& compiler_options_;
  std::vector<uint32_t> inline_caches_;

  DISALLOW_COPY_AND_ASSIGN(ProfilingInfoBuilder);
};

}  // namespace art


#endif  // ART_COMPILER_OPTIMIZING_PROFILING_INFO_BUILDER_H_
