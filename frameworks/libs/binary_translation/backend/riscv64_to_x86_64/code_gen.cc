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

#include "berberis/backend/x86_64/code_gen.h"

#include "berberis/assembler/machine_code.h"
#include "berberis/backend/code_emitter.h"
#include "berberis/backend/common/machine_ir_opt.h"
#include "berberis/backend/common/reg_alloc.h"
#include "berberis/backend/x86_64/insn_folding.h"
#include "berberis/backend/x86_64/local_guest_context_optimizer.h"
#include "berberis/backend/x86_64/loop_guest_context_optimizer.h"
#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir_check.h"
#include "berberis/backend/x86_64/machine_ir_opt.h"
#include "berberis/backend/x86_64/rename_copy_uses.h"
#include "berberis/backend/x86_64/rename_vregs.h"
#include "berberis/base/config_globals.h"
#include "berberis/base/logging.h"
#include "berberis/base/tracing.h"

namespace berberis::x86_64 {

void GenCode(MachineIR* machine_ir, MachineCode* machine_code, const GenCodeParams& params) {
  CHECK_EQ(CheckMachineIR(*machine_ir), kMachineIRCheckSuccess);
  if (IsConfigFlagSet(kVerboseTranslation)) {
    TRACE("MachineIR before optimizations {\n");
    TRACE("%s", machine_ir->GetDebugString().c_str());
    TRACE("}\n\n");
  }

  RemoveCriticalEdges(machine_ir);

  ReorderBasicBlocksInReversePostOrder(machine_ir);
  MoveColdBlocksToEnd(machine_ir);

  RemoveLoopGuestContextAccesses(machine_ir);
  RenameVRegs(machine_ir);

  RemoveLocalGuestContextAccesses(machine_ir);
  RemoveRedundantPut(machine_ir);
  FoldInsns(machine_ir);
  // Call this after all phases that create copy instructions.
  RenameCopyUses(machine_ir);
  RemoveDeadCode(machine_ir);

  AllocRegs(machine_ir);

  RemoveNopPseudoCopy(machine_ir);
  x86_64::RemoveForwarderBlocks(machine_ir);

  CHECK_EQ(CheckMachineIR(*machine_ir), kMachineIRCheckSuccess);

  if (IsConfigFlagSet(kVerboseTranslation)) {
    TRACE("MachineIR before emit {\n");
    TRACE("%s", machine_ir->GetDebugString().c_str());
    TRACE("}\n\n");
  }

  if (!params.skip_emit) {
    CodeEmitter emitter(
        machine_code, machine_ir->FrameSize(), machine_ir->NumBasicBlocks(), machine_ir->arena());
    machine_ir->Emit(&emitter);
    emitter.Finalize();
  }
}

}  // namespace berberis::x86_64
