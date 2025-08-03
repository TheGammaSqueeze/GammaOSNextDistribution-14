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

#ifndef BERBERIS_BACKEND_X86_64_MACHINE_IR_TEST_CORPUS_H_
#define BERBERIS_BACKEND_X86_64_MACHINE_IR_TEST_CORPUS_H_

// TODO(b/232598137) Only intended for use by tests.

#include <tuple>

#include "berberis/backend/x86_64/machine_ir.h"

namespace berberis {

std::tuple<const MachineBasicBlock*,
           const MachineBasicBlock*,
           const MachineBasicBlock*,
           MachineReg,
           MachineReg>
BuildDataFlowAcrossBasicBlocks(x86_64::MachineIR* machine_ir);

std::tuple<const MachineBasicBlock*, const MachineBasicBlock*, const MachineBasicBlock*, MachineReg>
BuildDataFlowFromTwoPreds(x86_64::MachineIR* machine_ir);

std::tuple<const MachineBasicBlock*, const MachineBasicBlock*, const MachineBasicBlock*, MachineReg>
BuildDataFlowToTwoSuccs(x86_64::MachineIR* machine_ir);

std::tuple<const MachineBasicBlock*,
           const MachineBasicBlock*,
           const MachineBasicBlock*,
           const MachineBasicBlock*>
BuildDiamondControlFlow(x86_64::MachineIR* machine_ir);

std::tuple<const MachineBasicBlock*,
           const MachineBasicBlock*,
           const MachineBasicBlock*,
           const MachineBasicBlock*,
           MachineReg>
BuildDataFlowAcrossEmptyLoop(x86_64::MachineIR* machine_ir);

}  // namespace berberis

#endif  // BERBERIS_BACKEND_X86_64_MACHINE_IR_TEST_CORPUS_H_
