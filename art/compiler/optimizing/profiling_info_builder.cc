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

#include "profiling_info_builder.h"

#include "art_method-inl.h"
#include "code_generator.h"
#include "driver/compiler_options.h"
#include "dex/code_item_accessors-inl.h"
#include "inliner.h"
#include "jit/profiling_info.h"
#include "optimizing_compiler_stats.h"
#include "scoped_thread_state_change-inl.h"

namespace art HIDDEN {

void ProfilingInfoBuilder::Run() {
  DCHECK(GetGraph()->IsUsefulOptimizing());
  DCHECK_EQ(GetGraph()->GetProfilingInfo(), nullptr);
  // Order does not matter.
  for (HBasicBlock* block : GetGraph()->GetReversePostOrder()) {
    // No need to visit the phis.
    VisitNonPhiInstructions(block);
  }

  ScopedObjectAccess soa(Thread::Current());
  GetGraph()->SetProfilingInfo(
      ProfilingInfo::Create(soa.Self(), GetGraph()->GetArtMethod(), inline_caches_));
}


uint32_t ProfilingInfoBuilder::EncodeInlinedDexPc(const HInliner* inliner,
                                                  const CompilerOptions& compiler_options,
                                                  HInvoke* invoke) {
  DCHECK(inliner->GetCallerEnvironment() != nullptr);
  DCHECK(inliner->GetParent() != nullptr);
  std::vector<uint32_t> temp_vector;
  temp_vector.push_back(invoke->GetDexPc());
  while (inliner->GetCallerEnvironment() != nullptr) {
    temp_vector.push_back(inliner->GetCallerEnvironment()->GetDexPc());
    inliner = inliner->GetParent();
  }

  DCHECK_EQ(inliner->GetOutermostGraph(), inliner->GetGraph());
  return InlineCache::EncodeDexPc(
      inliner->GetOutermostGraph()->GetArtMethod(),
      temp_vector,
      compiler_options.GetInlineMaxCodeUnits());
}

static uint32_t EncodeDexPc(HInvoke* invoke, const CompilerOptions& compiler_options) {
  std::vector<uint32_t> dex_pcs;
  ArtMethod* outer_method = nullptr;
  for (HEnvironment* environment = invoke->GetEnvironment();
       environment != nullptr;
       environment = environment->GetParent()) {
    outer_method = environment->GetMethod();
    dex_pcs.push_back(environment->GetDexPc());
  }

  ScopedObjectAccess soa(Thread::Current());
  return InlineCache::EncodeDexPc(
      outer_method,
      dex_pcs,
      compiler_options.GetInlineMaxCodeUnits());
}

void ProfilingInfoBuilder::HandleInvoke(HInvoke* invoke) {
  if (IsInlineCacheUseful(invoke, codegen_)) {
    uint32_t dex_pc = EncodeDexPc(invoke, compiler_options_);
    if (dex_pc != kNoDexPc) {
      inline_caches_.push_back(dex_pc);
    } else {
      ScopedObjectAccess soa(Thread::Current());
      LOG(WARNING) << "Could not encode dex pc for "
                   << invoke->GetResolvedMethod()->PrettyMethod();
    }
  }
}

void ProfilingInfoBuilder::VisitInvokeInterface(HInvokeInterface* invoke) {
  HandleInvoke(invoke);
}

void ProfilingInfoBuilder::VisitInvokeVirtual(HInvokeVirtual* invoke) {
  HandleInvoke(invoke);
}

bool ProfilingInfoBuilder::IsInlineCacheUseful(HInvoke* invoke, CodeGenerator* codegen) {
  DCHECK(invoke->IsInvokeVirtual() || invoke->IsInvokeInterface());
  if (codegen->IsImplementedIntrinsic(invoke)) {
    return false;
  }
  if (!invoke->GetBlock()->GetGraph()->IsCompilingBaseline()) {
    return false;
  }
  if (Runtime::Current()->IsAotCompiler()) {
    return false;
  }
  if (invoke->InputAt(0)->GetReferenceTypeInfo().IsExact()) {
    return false;
  }
  if (invoke->GetResolvedMethod() != nullptr) {
    ScopedObjectAccess soa(Thread::Current());
    if (invoke->GetResolvedMethod()->IsFinal() ||
        invoke->GetResolvedMethod()->GetDeclaringClass()->IsFinal()) {
      return false;
    }
  }

  if (!codegen->GetGraph()->IsUsefulOptimizing()) {
    // Earlier pass knew what the calling target was. No need for an inline
    // cache.
    return false;
  }
  return true;
}

InlineCache* ProfilingInfoBuilder::GetInlineCache(ProfilingInfo* info,
                                                  const CompilerOptions& compiler_options,
                                                  HInvoke* instruction) {
  ScopedObjectAccess soa(Thread::Current());
  uint32_t dex_pc = EncodeDexPc(instruction, compiler_options);
  if (dex_pc == kNoDexPc) {
    return nullptr;
  }
  return info->GetInlineCache(dex_pc);
}

}  // namespace art
