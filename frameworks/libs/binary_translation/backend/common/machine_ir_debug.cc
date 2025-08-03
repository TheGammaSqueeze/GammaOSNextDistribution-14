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

#include "berberis/backend/common/machine_ir.h"

#include <string>

#include "berberis/base/stringprintf.h"

namespace berberis {

namespace {

std::string GetInsnListDebugString(const char* indent, const MachineInsnList& insn_list) {
  std::string out;
  for (const auto* insn : insn_list) {
    out += indent;
    out += insn->GetDebugString();
    out += "\n";
  }
  return out;
}

}  // namespace

std::string GetMachineRegDebugString(MachineReg r) {
  if (r.IsHardReg()) {
    return GetMachineHardRegDebugName(r);
  }
  if (r.IsVReg()) {
    return StringPrintf("v%d", r.GetVRegIndex());
  }
  if (r.IsSpilledReg()) {
    return StringPrintf("s%d", r.GetSpilledRegIndex());
  }
  return "?";
}

std::string GetRegOperandDebugString(const MachineInsn* insn, int i) {
  MachineReg r = insn->RegAt(i);
  std::string out;
  if (r.IsVReg()) {
    out += insn->RegKindAt(i).RegClass()->GetDebugName();
    out += " ";
  }
  out += GetMachineRegDebugString(r);
  return out;
}

std::string MachineBasicBlock::GetDebugString() const {
  std::string out(StringPrintf("%2d MachineBasicBlock live_in=[", id()));

  for (size_t i = 0; i < live_in().size(); ++i) {
    if (i > 0) {
      out += ", ";
    }
    out += GetMachineRegDebugString(live_in()[i]);
  }
  out += "] live_out=[";

  for (size_t i = 0; i < live_out().size(); ++i) {
    if (i > 0) {
      out += ", ";
    }
    out += GetMachineRegDebugString(live_out()[i]);
  }
  out += "]\n";

  for (const auto* edge : in_edges()) {
    out += StringPrintf("    MachineEdge %d -> %d [\n", edge->src()->id(), edge->dst()->id());
    out += GetInsnListDebugString("      ", edge->insn_list());
    out += "    ]\n";
  }

  out += GetInsnListDebugString("    ", insn_list());

  return out;
}

std::string MachineIR::GetDebugString() const {
  std::string out;
  for (const auto* bb : bb_list()) {
    out += bb->GetDebugString();
  }
  return out;
}

std::string MachineIR::GetDebugStringForDot() const {
  std::string str;
  str += "digraph MachineIR {\n";

  for (const auto* bb : bb_list()) {
    for (auto* in_edge : bb->in_edges()) {
      auto* pred_bb = in_edge->src();

      // Print edge.
      str += StringPrintf("BB%d->BB%d", pred_bb->id(), bb->id());
      str += ";\n";
    }

    // Print instructions with "\l" new-lines for left-justification.
    str += StringPrintf("BB%d [shape=box,label=\"BB%d\\l", bb->id(), bb->id());
    for (const auto* insn : bb->insn_list()) {
      str += insn->GetDebugString();
      str += "\\l";
    }
    str += "\"];\n";
  }

  str += "}\n";
  return str;
}

std::string PseudoBranch::GetDebugString() const {
  return StringPrintf("PSEUDO_BRANCH %d", then_bb()->id());
}

std::string PseudoCondBranch::GetDebugString() const {
  std::string out("PSEUDO_COND_BRANCH ");
  out += GetCondName(cond());
  out += ", ";
  out += StringPrintf("%d, ", then_bb()->id());
  out += StringPrintf("%d, ", else_bb()->id());
  out += StringPrintf("(%s)", GetRegOperandDebugString(this, 0).c_str());
  return out;
}

std::string PseudoJump::GetDebugString() const {
  const char* suffix;
  switch (kind_) {
    case Kind::kJumpWithPendingSignalsCheck:
      suffix = "_SIG_CHECK";
      break;
    case Kind::kJumpWithoutPendingSignalsCheck:
      suffix = "";
      break;
    case Kind::kSyscall:
      suffix = "_TO_SYSCALL";
      break;
    case Kind::kExitGeneratedCode:
      suffix = "_EXIT_GEN_CODE";
      break;
  }
  return StringPrintf("PSEUDO_JUMP%s 0x%" PRIxPTR, suffix, target_);
}

std::string PseudoIndirectJump::GetDebugString() const {
  std::string out("PSEUDO_INDIRECT_JUMP ");
  out += GetMachineRegDebugString(src_);
  return out;
}

std::string PseudoCopy::GetDebugString() const {
  std::string out("PSEUDO_COPY ");
  out += GetRegOperandDebugString(this, 0);
  out += ", ";
  out += GetRegOperandDebugString(this, 1);
  return out;
}

std::string PseudoDefXReg::GetDebugString() const {
  return std::string("PSEUDO_DEF ") + GetRegOperandDebugString(this, 0);
}

std::string PseudoDefReg::GetDebugString() const {
  return std::string("PSEUDO_DEF ") + GetRegOperandDebugString(this, 0);
}

std::string PseudoReadFlags::GetDebugString() const {
  std::string out("PSEUDO_READ_FLAGS ");
  out += with_overflow() ? "" : "(skip overflow) ";
  out += GetRegOperandDebugString(this, 0);
  out += ", ";
  out += GetRegOperandDebugString(this, 1);
  return out;
}

std::string PseudoWriteFlags::GetDebugString() const {
  std::string out("PSEUDO_WRITE_FLAGS ");
  out += GetRegOperandDebugString(this, 0);
  out += ", ";
  out += GetRegOperandDebugString(this, 1);
  return out;
}

}  // namespace berberis
