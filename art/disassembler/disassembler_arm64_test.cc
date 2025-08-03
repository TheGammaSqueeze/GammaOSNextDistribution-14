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

#include <inttypes.h>

#include <regex>

#include <sstream>

#include "base/common_art_test.h"
#include "disassembler_arm64.h"
#include "thread.h"

#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wshadow"
#include "aarch64/disasm-aarch64.h"
#include "aarch64/macro-assembler-aarch64.h"
#pragma GCC diagnostic pop


using namespace vixl::aarch64;  // NOLINT(build/namespaces)

namespace art {
namespace arm64 {

/**
 * Fixture class for the ArtDisassemblerTest tests.
 */
class ArtDisassemblerTest : public CommonArtTest {
 public:
  ArtDisassemblerTest() {
  }

  void SetupAssembly(uint64_t end_address) {
    masm.GetCPUFeatures()->Combine(vixl::CPUFeatures::All());

    disamOptions.reset(new DisassemblerOptions(/* absolute_addresses= */ true,
                                               reinterpret_cast<uint8_t*>(0x0),
                                               reinterpret_cast<uint8_t*>(end_address),
                                               /* can_read_literals_= */ true,
                                               &Thread::DumpThreadOffset<PointerSize::k64>));
    disasm.reset(new CustomDisassembler(&*disamOptions));
    decoder.AppendVisitor(disasm.get());
    masm.SetGenerateSimulatorCode(false);
  }

  static constexpr size_t kMaxSizeGenerated = 1024;

  template <typename LamdaType>
  void ImplantInstruction(LamdaType fn) {
    vixl::ExactAssemblyScope guard(&masm,
                                   kMaxSizeGenerated,
                                   vixl::ExactAssemblyScope::kMaximumSize);
    fn();
  }

  // Appends an instruction to the existing buffer and then
  // attempts to match the output of that instructions disassembly
  // against a regex expression. Fails if no match is found.
  template <typename LamdaType>
  void CompareInstruction(LamdaType fn, const char* EXP) {
    ImplantInstruction(fn);
    masm.FinalizeCode();

    // This gets the last instruction in the buffer.
    // The end address of the buffer is at the end of the last instruction.
    // sizeof(Instruction) is 1 byte as it in an empty class.
    // Therefore we need to go back kInstructionSize * sizeof(Instruction) bytes
    // in order to get to the start of the last instruction.
    const Instruction* targetInstruction =
        masm.GetBuffer()->GetEndAddress<Instruction*>()->
            GetInstructionAtOffset(-static_cast<signed>(kInstructionSize));

    decoder.Decode(targetInstruction);

    const char* disassembly = disasm->GetOutput();

    if (!std::regex_match(disassembly, std::regex(EXP))) {
      const uint32_t encoding = static_cast<uint32_t>(targetInstruction->GetInstructionBits());

      printf("\nEncoding: %08" PRIx32 "\nExpected: %s\nFound:    %s\n",
             encoding,
             EXP,
             disassembly);

      ADD_FAILURE();
    }
    printf("----\n%s\n", disassembly);
  }

  std::unique_ptr<CustomDisassembler> disasm;
  std::unique_ptr<DisassemblerOptions> disamOptions;
  Decoder decoder;
  MacroAssembler masm;
};

#define IMPLANT(fn)                                                          \
  do {                                                                       \
    ImplantInstruction([&]() { this->masm.fn; });                            \
  } while (0)

#define COMPARE(fn, output)                                                  \
  do {                                                                       \
    CompareInstruction([&]() { this->masm.fn; }, (output));                  \
  } while (0)

// These tests map onto the named per instruction instrumentation functions in:
// ART/art/disassembler/disassembler_arm.cc
// Context can be found in the logic conditional on incoming instruction types and sequences in the
// ART disassembler. As of writing the functionality we are testing for that of additional
// diagnostic info being appended to the end of the ART disassembly output.
TEST_F(ArtDisassemblerTest, LoadLiteralVisitBadAddress) {
  SetupAssembly(0xffffff);

  // Check we append an erroneous hint "(?)" for literal load instructions with
  // out of scope literal pool value addresses.
  COMPARE(ldr(x0, vixl::aarch64::Assembler::ImmLLiteral(1000)),
      "ldr x0, pc\\+128000 \\(addr -?0x[0-9a-fA-F]+\\) \\(\\?\\)");
}

TEST_F(ArtDisassemblerTest, LoadLiteralVisit) {
  SetupAssembly(0xffffffffffffffff);

  // Test that we do not append anything for ineligible instruction.
  COMPARE(ldr(x0, MemOperand(x18, 0)), "ldr x0, \\[x18\\]$");

  // Check we do append some extra info in the right text format for valid literal load instruction.
  COMPARE(ldr(w0, vixl::aarch64::Assembler::ImmLLiteral(0)),
      "ldr w0, pc\\+0 \\(addr -?0x[0-9a-f]+\\) \\(0x18000000 / 402653184\\)");
  // We don't compare with exact value even though it's a known literal (the encoding of the
  // instruction itself) since the precision of printed floating point values could change.
  COMPARE(ldr(s0, vixl::aarch64::Assembler::ImmLLiteral(0)),
      "ldr s0, pc\\+0 \\(addr -?0x[0-9a-f]+\\) \\([0-9]+.[0-9]+e(\\+|-)[0-9]+\\)");
}

TEST_F(ArtDisassemblerTest, LoadStoreUnsignedOffsetVisit) {
  SetupAssembly(0xffffffffffffffff);

  // Test that we do not append anything for ineligible instruction.
  COMPARE(ldr(x0, MemOperand(x18, 8)), "ldr x0, \\[x18, #8\\]$");
  // Test that we do append the function name if the instruction is a load from the address
  // stored in the TR register.
  COMPARE(ldr(x0, MemOperand(x19, 8)), "ldr x0, \\[tr, #8\\] ; thin_lock_thread_id");
}

TEST_F(ArtDisassemblerTest, UnconditionalBranchNoAppendVisit) {
  SetupAssembly(0xffffffffffffffff);

  vixl::aarch64::Label destination;
  masm.Bind(&destination);

  IMPLANT(ldr(x16, MemOperand(x18, 0)));

  // Test that we do not append anything for ineligible instruction.
  COMPARE(bl(&destination),
      "bl #-0x4 \\(addr -?0x[0-9a-f]+\\)$");
}

TEST_F(ArtDisassemblerTest, UnconditionalBranchVisit) {
  SetupAssembly(0xffffffffffffffff);

  vixl::aarch64::Label destination;
  masm.Bind(&destination);

  IMPLANT(ldr(x16, MemOperand(x19, 0)));
  IMPLANT(br(x16));

  // Test that we do append the function name if the instruction is a branch
  // to a load that reads data from the address in the TR register, into the IPO register
  // followed by a BR branching using the IPO register.
  COMPARE(bl(&destination),
      "bl #-0x8 \\(addr -?0x[0-9a-f]+\\) ; state_and_flags");
}


}  // namespace arm64
}  // namespace art
