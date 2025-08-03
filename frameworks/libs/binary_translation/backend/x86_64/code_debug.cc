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

// x86_64 machine IR insns debugging.

#include "berberis/backend/x86_64/code_debug.h"

#include <cinttypes>
#include <string>

#include "berberis/base/logging.h"
#include "berberis/base/stringprintf.h"

// TODO(b/179708579): share this code with 32-bit backend.

using std::string;

namespace berberis {

const char* GetMachineHardRegDebugName(MachineReg r) {
  static const char* kHardRegs[] = {
      "?",    "r8",     "r9",   "r10",   "r11",   "rsi",   "rdi",   "rax",   "rbx",
      "rcx",  "rdx",    "rbp",  "rsp",   "r12",   "r13",   "r14",   "r15",   "?",
      "?",    "eflags", "xmm0", "xmm1",  "xmm2",  "xmm3",  "xmm4",  "xmm5",  "xmm6",
      "xmm7", "xmm8",   "xmm9", "xmm10", "xmm11", "xmm12", "xmm13", "xmm14", "xmm15",
  };
  CHECK_LT(static_cast<unsigned>(r.reg()), std::size(kHardRegs));
  return kHardRegs[r.reg()];
}

namespace x86_64 {

namespace {

int ScaleToInt(MachineMemOperandScale scale) {
  switch (scale) {
    case MachineMemOperandScale::kOne:
      return 1;
    case MachineMemOperandScale::kTwo:
      return 2;
    case MachineMemOperandScale::kFour:
      return 4;
    case MachineMemOperandScale::kEight:
      return 8;
  }
}

}  // namespace

string GetImplicitRegOperandDebugString(const MachineInsnX86_64* insn, int i) {
  return StringPrintf("(%s)", GetRegOperandDebugString(insn, i).c_str());
}

string GetAbsoluteMemOperandDebugString(const MachineInsnX86_64* insn) {
  return StringPrintf("[0x%x]", insn->disp());
}

string GetBaseDispMemOperandDebugString(const MachineInsnX86_64* insn, int i) {
  return StringPrintf("[%s + 0x%x]", GetRegOperandDebugString(insn, i).c_str(), insn->disp());
}

string GetIndexDispMemOperandDebugString(const MachineInsnX86_64* insn, int i) {
  return StringPrintf("[%s * %d + 0x%x]",
                      GetRegOperandDebugString(insn, i).c_str(),
                      ScaleToInt(insn->scale()),
                      insn->disp());
}

string GetBaseIndexDispMemOperandDebugString(const MachineInsnX86_64* insn, int i) {
  return StringPrintf("[%s + %s * %d + 0x%x]",
                      GetRegOperandDebugString(insn, i).c_str(),
                      GetRegOperandDebugString(insn, i + 1).c_str(),
                      ScaleToInt(insn->scale()),
                      insn->disp());
}

string GetImmOperandDebugString(const MachineInsnX86_64* insn) {
  return StringPrintf("0x%" PRIx64, insn->imm());
}

string GetCondOperandDebugString(const MachineInsnX86_64* insn) {
  return GetCondName(insn->cond());
}

string GetLabelOperandDebugString(const MachineInsnX86_64* insn) {
  return GetImmOperandDebugString(insn);
}

string CallImm::GetDebugString() const {
  string out(StringPrintf("CALL 0x%" PRIx64, imm()));
  for (int i = 0; i < NumRegOperands(); ++i) {
    out += ", ";
    out += GetRegOperandDebugString(this, i);
  }
  return out;
}

string CallImmArg::GetDebugString() const {
  return "CALL_ARG " + GetRegOperandDebugString(this, 0);
}

}  // namespace x86_64

}  // namespace berberis
