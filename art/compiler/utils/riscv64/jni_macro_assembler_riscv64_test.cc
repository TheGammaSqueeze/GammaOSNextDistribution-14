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

#include <dirent.h>
#include <errno.h>
#include <string.h>
#include <sys/types.h>

#include <fstream>
#include <map>
#include <regex>

#include "gtest/gtest.h"

#include "indirect_reference_table.h"
#include "lock_word.h"
#include "jni/quick/calling_convention.h"
#include "utils/riscv64/jni_macro_assembler_riscv64.h"
#include "utils/assembler_test_base.h"

#include "base/macros.h"
#include "base/malloc_arena_pool.h"

namespace art HIDDEN {
namespace riscv64 {

#define __ assembler_.

class JniMacroAssemblerRiscv64Test : public AssemblerTestBase {
 public:
  JniMacroAssemblerRiscv64Test() : pool_(), allocator_(&pool_), assembler_(&allocator_) { }

 protected:
  InstructionSet GetIsa() override { return InstructionSet::kRiscv64; }

  std::vector<std::string> GetAssemblerCommand() override {
    std::vector<std::string> result = AssemblerTestBase::GetAssemblerCommand();
    if (march_override_.has_value()) {
      auto it = std::find_if(result.begin(),
                             result.end(),
                             [](const std::string& s) { return StartsWith(s, "-march="); });
      CHECK(it != result.end());
      *it = march_override_.value();
    }
    return result;
  }

  void DriverStr(const std::string& assembly_text, const std::string& test_name) {
    assembler_.FinalizeCode();
    size_t cs = assembler_.CodeSize();
    std::vector<uint8_t> data(cs);
    MemoryRegion code(&data[0], data.size());
    assembler_.CopyInstructions(code);
    Driver(data, assembly_text, test_name);
  }

  static Riscv64ManagedRegister AsManaged(XRegister reg) {
    return Riscv64ManagedRegister::FromXRegister(reg);
  }

  static Riscv64ManagedRegister AsManaged(FRegister reg) {
    return Riscv64ManagedRegister::FromFRegister(reg);
  }

  std::string EmitRet() {
    __ RemoveFrame(/*frame_size=*/ 0u,
                   /*callee_save_regs=*/ ArrayRef<const ManagedRegister>(),
                   /*may_suspend=*/ false);
    return "ret\n";
  }

  static const size_t kWordSize = 4u;
  static const size_t kDoubleWordSize = 8u;

  MallocArenaPool pool_;
  ArenaAllocator allocator_;
  Riscv64JNIMacroAssembler assembler_;

  // TODO: Implement auto-compression and remove this override.
  std::optional<std::string> march_override_ = "-march=rv64imafdv_zba_zbb";
};

TEST_F(JniMacroAssemblerRiscv64Test, StackFrame) {
  std::string expected;

  std::unique_ptr<JniCallingConvention> jni_conv = JniCallingConvention::Create(
      &allocator_,
      /*is_static=*/ false,
      /*is_synchronized=*/ false,
      /*is_fast_native=*/ false,
      /*is_critical_native=*/ false,
      /*shorty=*/ "V",
      InstructionSet::kRiscv64);
  size_t frame_size = jni_conv->FrameSize();
  ManagedRegister method_reg = AsManaged(A0);
  ArrayRef<const ManagedRegister> callee_save_regs = jni_conv->CalleeSaveRegisters();

  __ BuildFrame(frame_size, method_reg, callee_save_regs);
  expected += "addi sp, sp, -208\n"
              "sd ra, 200(sp)\n"
              "sd s11, 192(sp)\n"
              "sd s10, 184(sp)\n"
              "sd s9, 176(sp)\n"
              "sd s8, 168(sp)\n"
              "sd s7, 160(sp)\n"
              "sd s6, 152(sp)\n"
              "sd s5, 144(sp)\n"
              "sd s4, 136(sp)\n"
              "sd s3, 128(sp)\n"
              "sd s2, 120(sp)\n"
              "sd s0, 112(sp)\n"
              "fsd fs11, 104(sp)\n"
              "fsd fs10, 96(sp)\n"
              "fsd fs9, 88(sp)\n"
              "fsd fs8, 80(sp)\n"
              "fsd fs7, 72(sp)\n"
              "fsd fs6, 64(sp)\n"
              "fsd fs5, 56(sp)\n"
              "fsd fs4, 48(sp)\n"
              "fsd fs3, 40(sp)\n"
              "fsd fs2, 32(sp)\n"
              "fsd fs1, 24(sp)\n"
              "fsd fs0, 16(sp)\n"
              "sd a0, 0(sp)\n";

  __ RemoveFrame(frame_size, callee_save_regs, /*may_suspend=*/ false);
  expected += "fld fs0, 16(sp)\n"
              "fld fs1, 24(sp)\n"
              "fld fs2, 32(sp)\n"
              "fld fs3, 40(sp)\n"
              "fld fs4, 48(sp)\n"
              "fld fs5, 56(sp)\n"
              "fld fs6, 64(sp)\n"
              "fld fs7, 72(sp)\n"
              "fld fs8, 80(sp)\n"
              "fld fs9, 88(sp)\n"
              "fld fs10, 96(sp)\n"
              "fld fs11, 104(sp)\n"
              "ld s0, 112(sp)\n"
              "ld s2, 120(sp)\n"
              "ld s3, 128(sp)\n"
              "ld s4, 136(sp)\n"
              "ld s5, 144(sp)\n"
              "ld s6, 152(sp)\n"
              "ld s7, 160(sp)\n"
              "ld s8, 168(sp)\n"
              "ld s9, 176(sp)\n"
              "ld s10, 184(sp)\n"
              "ld s11, 192(sp)\n"
              "ld ra, 200(sp)\n"
              "addi sp, sp, 208\n"
              "ret\n";

  DriverStr(expected, "StackFrame");
}

TEST_F(JniMacroAssemblerRiscv64Test, ChangeFrameSize) {
  std::string expected;

  __ IncreaseFrameSize(128);
  expected += "addi sp, sp, -128\n";
  __ DecreaseFrameSize(128);
  expected += "addi sp, sp, 128\n";

  __ IncreaseFrameSize(0);  // No-op
  __ DecreaseFrameSize(0);  // No-op

  __ IncreaseFrameSize(2048);
  expected += "addi sp, sp, -2048\n";
  __ DecreaseFrameSize(2048);
  expected += "addi t6, sp, 2047\n"
              "addi sp, t6, 1\n";

  __ IncreaseFrameSize(4096);
  expected += "addi t6, sp, -2048\n"
              "addi sp, t6, -2048\n";
  __ DecreaseFrameSize(4096);
  expected += "lui t6, 1\n"
              "add sp, sp, t6\n";

  __ IncreaseFrameSize(6 * KB);
  expected += "addi t6, zero, -3\n"
              "slli t6, t6, 11\n"
              "add sp, sp, t6\n";
  __ DecreaseFrameSize(6 * KB);
  expected += "addi t6, zero, 3\n"
              "slli t6, t6, 11\n"
              "add sp, sp, t6\n";

  __ IncreaseFrameSize(6 * KB + 16);
  expected += "lui t6, 0xffffe\n"
              "addiw t6, t6, 2048-16\n"
              "add sp, sp, t6\n";
  __ DecreaseFrameSize(6 * KB + 16);
  expected += "lui t6, 2\n"
              "addiw t6, t6, 16-2048\n"
              "add sp, sp, t6\n";

  DriverStr(expected, "ChangeFrameSize");
}

TEST_F(JniMacroAssemblerRiscv64Test, Store) {
  std::string expected;

  __ Store(FrameOffset(0), AsManaged(A0), kWordSize);
  expected += "sw a0, 0(sp)\n";
  __ Store(FrameOffset(2048), AsManaged(S0), kDoubleWordSize);
  expected += "addi t6, sp, 0x7f8\n"
              "sd s0, 8(t6)\n";

  __ Store(AsManaged(A1), MemberOffset(256), AsManaged(S2), kDoubleWordSize);
  expected += "sd s2, 256(a1)\n";
  __ Store(AsManaged(S3), MemberOffset(4 * KB), AsManaged(T1), kWordSize);
  expected += "lui t6, 1\n"
              "add t6, t6, s3\n"
              "sw t1, 0(t6)\n";

  __ Store(AsManaged(A3), MemberOffset(384), AsManaged(FA5), kDoubleWordSize);
  expected += "fsd fa5, 384(a3)\n";
  __ Store(AsManaged(S4), MemberOffset(4 * KB + 16), AsManaged(FT10), kWordSize);
  expected += "lui t6, 1\n"
              "add t6, t6, s4\n"
              "fsw ft10, 16(t6)\n";

  __ StoreRawPtr(FrameOffset(128), AsManaged(A7));
  expected += "sd a7, 128(sp)\n";
  __ StoreRawPtr(FrameOffset(6 * KB), AsManaged(S11));
  expected += "lui t6, 2\n"
              "add t6, t6, sp\n"
              "sd s11, -2048(t6)\n";

  __ StoreStackPointerToThread(ThreadOffset64(512), /*tag_sp=*/ false);
  expected += "sd sp, 512(s1)\n";
  __ StoreStackPointerToThread(ThreadOffset64(3 * KB), /*tag_sp=*/ true);
  expected += "ori t6, sp, 0x2\n"
              "addi t5, s1, 0x7f8\n"
              "sd t6, 0x408(t5)\n";

  DriverStr(expected, "Store");
}

TEST_F(JniMacroAssemblerRiscv64Test, Load) {
  std::string expected;

  __ Load(AsManaged(A0), FrameOffset(0), kWordSize);
  expected += "lw a0, 0(sp)\n";
  __ Load(AsManaged(S0), FrameOffset(2048), kDoubleWordSize);
  expected += "addi t6, sp, 0x7f8\n"
              "ld s0, 8(t6)\n";

  __ Load(AsManaged(S2), AsManaged(A1), MemberOffset(256), kDoubleWordSize);
  expected += "ld s2, 256(a1)\n";
  __ Load(AsManaged(T1), AsManaged(S3), MemberOffset(4 * KB), kWordSize);
  expected += "lui t6, 1\n"
              "add t6, t6, s3\n"
              "lw t1, 0(t6)\n";

  __ Load(AsManaged(FA5), AsManaged(A3), MemberOffset(384), kDoubleWordSize);
  expected += "fld fa5, 384(a3)\n";
  __ Load(AsManaged(FT10), AsManaged(S4), MemberOffset(4 * KB + 16), kWordSize);
  expected += "lui t6, 1\n"
              "add t6, t6, s4\n"
              "flw ft10, 16(t6)\n";

  __ LoadRawPtrFromThread(AsManaged(A7), ThreadOffset64(512));
  expected += "ld a7, 512(s1)\n";
  __ LoadRawPtrFromThread(AsManaged(S11), ThreadOffset64(3 * KB));
  expected += "addi t6, s1, 0x7f8\n"
              "ld s11, 0x408(t6)\n";

  __ LoadGcRootWithoutReadBarrier(AsManaged(T0), AsManaged(A0), MemberOffset(0));
  expected += "lwu t0, 0(a0)\n";
  __ LoadGcRootWithoutReadBarrier(AsManaged(T1), AsManaged(S2), MemberOffset(0x800));
  expected += "addi t6, s2, 0x7f8\n"
              "lwu t1, 8(t6)\n";

  __ LoadStackReference(AsManaged(T0), FrameOffset(0));
  expected += "lwu t0, 0(sp)\n";
  __ LoadStackReference(AsManaged(T1), FrameOffset(0x800));
  expected += "addi t6, sp, 0x7f8\n"
              "lwu t1, 8(t6)\n";

  DriverStr(expected, "Load");
}

TEST_F(JniMacroAssemblerRiscv64Test, CreateJObject) {
  std::string expected;

  __ CreateJObject(AsManaged(A0), FrameOffset(8), AsManaged(A0), /*null_allowed=*/ true);
  expected += "beqz a0, 1f\n"
              "addi a0, sp, 8\n"
              "1:\n";
  __ CreateJObject(AsManaged(A1), FrameOffset(12), AsManaged(A1), /*null_allowed=*/ false);
  expected += "addi a1, sp, 12\n";
  __ CreateJObject(AsManaged(A2), FrameOffset(16), AsManaged(A3), /*null_allowed=*/ true);
  expected += "li a2, 0\n"
              "beqz a3, 2f\n"
              "addi a2, sp, 16\n"
              "2:\n";
  __ CreateJObject(AsManaged(A4), FrameOffset(2048), AsManaged(A5), /*null_allowed=*/ false);
  expected += "addi t6, sp, 2047\n"
              "addi a4, t6, 1\n";

  DriverStr(expected, "CreateJObject");
}

TEST_F(JniMacroAssemblerRiscv64Test, MoveArguments) {
  std::string expected;

  static constexpr FrameOffset kInvalidReferenceOffset =
      JNIMacroAssembler<kArmPointerSize>::kInvalidReferenceOffset;
  static constexpr size_t kNativePointerSize = static_cast<size_t>(kRiscv64PointerSize);
  static constexpr size_t kFloatSize = 4u;
  static constexpr size_t kXlenInBytes = 8u;  // Used for integral args and `double`.

  // Normal or @FastNative static with parameters "LIJIJILJI".
  // Note: This shall not spill references to the stack. The JNI compiler spills
  // references in an separate initial pass before moving arguments and creating `jobject`s.
  ArgumentLocation move_dests1[] = {
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A1), kNativePointerSize),  // `jclass`
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A2), kNativePointerSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A3), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A4), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A5), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A6), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A7), kXlenInBytes),
      ArgumentLocation(FrameOffset(0), kNativePointerSize),
      ArgumentLocation(FrameOffset(8), kXlenInBytes),
      ArgumentLocation(FrameOffset(16), kXlenInBytes),
  };
  ArgumentLocation move_srcs1[] = {
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A0), kNativePointerSize),  // `jclass`
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A1), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A2), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A3), 2 * kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A4), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A5), 2 * kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A6), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A7), kVRegSize),
      ArgumentLocation(FrameOffset(76), 2 * kVRegSize),
      ArgumentLocation(FrameOffset(84), kVRegSize),
  };
  FrameOffset move_refs1[] {
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(40),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(72),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
  };
  __ MoveArguments(ArrayRef<ArgumentLocation>(move_dests1),
                   ArrayRef<ArgumentLocation>(move_srcs1),
                   ArrayRef<FrameOffset>(move_refs1));
  expected += "beqz a7, 1f\n"
              "addi a7, sp, 72\n"
              "1:\n"
              "sd a7, 0(sp)\n"
              "ld t6, 76(sp)\n"
              "sd t6, 8(sp)\n"
              "lw t6, 84(sp)\n"
              "sd t6, 16(sp)\n"
              "mv a7, a6\n"
              "mv a6, a5\n"
              "mv a5, a4\n"
              "mv a4, a3\n"
              "mv a3, a2\n"
              "li a2, 0\n"
              "beqz a1, 2f\n"
              "add a2, sp, 40\n"
              "2:\n"
              "mv a1, a0\n";

  // Normal or @FastNative static with parameters "LIJIJILJI" - spill references.
  ArgumentLocation move_dests1_spill_refs[] = {
      ArgumentLocation(FrameOffset(40), kVRegSize),
      ArgumentLocation(FrameOffset(72), kVRegSize),
  };
  ArgumentLocation move_srcs1_spill_refs[] = {
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A1), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A7), kVRegSize),
  };
  FrameOffset move_refs1_spill_refs[] {
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
  };
  __ MoveArguments(ArrayRef<ArgumentLocation>(move_dests1_spill_refs),
                   ArrayRef<ArgumentLocation>(move_srcs1_spill_refs),
                   ArrayRef<FrameOffset>(move_refs1_spill_refs));
  expected += "sw a1, 40(sp)\n"
              "sw a7, 72(sp)\n";

  // Normal or @FastNative with parameters "LLIJIJIJLI" (first is `this`).
  // Note: This shall not spill references to the stack. The JNI compiler spills
  // references in an separate initial pass before moving arguments and creating `jobject`s.
  ArgumentLocation move_dests2[] = {
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A1), kNativePointerSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A2), kNativePointerSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A3), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A4), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A5), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A6), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A7), kXlenInBytes),
      ArgumentLocation(FrameOffset(0), kXlenInBytes),
      ArgumentLocation(FrameOffset(8), kNativePointerSize),
      ArgumentLocation(FrameOffset(16), kXlenInBytes),
  };
  ArgumentLocation move_srcs2[] = {
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A1), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A2), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A3), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A4), 2 * kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A5), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A6), 2 * kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A7), kVRegSize),
      ArgumentLocation(FrameOffset(76), 2 * kVRegSize),
      ArgumentLocation(FrameOffset(84), kVRegSize),
      ArgumentLocation(FrameOffset(88), kVRegSize),
  };
  FrameOffset move_refs2[] {
      FrameOffset(40),
      FrameOffset(44),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(84),
      FrameOffset(kInvalidReferenceOffset),
  };
  __ MoveArguments(ArrayRef<ArgumentLocation>(move_dests2),
                   ArrayRef<ArgumentLocation>(move_srcs2),
                   ArrayRef<FrameOffset>(move_refs2));
  // Args in A1-A7 do not move but references are converted to `jobject`.
  expected += "addi a1, sp, 40\n"
              "beqz a2, 1f\n"
              "addi a2, sp, 44\n"
              "1:\n"
              "ld t6, 76(sp)\n"
              "sd t6, 0(sp)\n"
              "lwu t6, 84(sp)\n"
              "beqz t6, 2f\n"
              "addi t6, sp, 84\n"
              "2:\n"
              "sd t6, 8(sp)\n"
              "lw t6, 88(sp)\n"
              "sd t6, 16(sp)\n";

  // Normal or @FastNative static with parameters "FDFDFDFDFDIJIJIJL".
  ArgumentLocation move_dests3[] = {
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A1), kNativePointerSize),  // `jclass`
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA0), kFloatSize),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA1), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA2), kFloatSize),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA3), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA4), kFloatSize),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA5), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA6), kFloatSize),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA7), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A2), kFloatSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A3), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A4), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A5), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A6), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A7), kXlenInBytes),
      ArgumentLocation(FrameOffset(0), kXlenInBytes),
      ArgumentLocation(FrameOffset(8), kXlenInBytes),
      ArgumentLocation(FrameOffset(16), kNativePointerSize),
  };
  ArgumentLocation move_srcs3[] = {
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A0), kNativePointerSize),  // `jclass`
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA0), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA1), 2 * kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA2), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA3), 2 * kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA4), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA5), 2 * kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA6), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA7), 2 * kVRegSize),
      ArgumentLocation(FrameOffset(88), kVRegSize),
      ArgumentLocation(FrameOffset(92), 2 * kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A1), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A2), 2 * kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A3), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A4), 2 * kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A5), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A6), 2 * kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A7), kVRegSize),
  };
  FrameOffset move_refs3[] {
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(88),
  };
  __ MoveArguments(ArrayRef<ArgumentLocation>(move_dests3),
                   ArrayRef<ArgumentLocation>(move_srcs3),
                   ArrayRef<FrameOffset>(move_refs3));
  // FP args in FA0-FA7 do not move.
  expected += "sd a5, 0(sp)\n"
              "sd a6, 8(sp)\n"
              "beqz a7, 1f\n"
              "addi a7, sp, 88\n"
              "1:\n"
              "sd a7, 16(sp)\n"
              "mv a5, a2\n"
              "mv a6, a3\n"
              "mv a7, a4\n"
              "lw a2, 88(sp)\n"
              "ld a3, 92(sp)\n"
              "mv a4, a1\n"
              "mv a1, a0\n";

  // @CriticalNative with parameters "DFDFDFDFIDJIJFDIIJ".
  ArgumentLocation move_dests4[] = {
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA0), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA1), kFloatSize),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA2), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA3), kFloatSize),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA4), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA5), kFloatSize),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA6), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA7), kFloatSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A0), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A1), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A2), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A3), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A4), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A5), kFloatSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A6), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A7), kXlenInBytes),
      ArgumentLocation(FrameOffset(0), kXlenInBytes),
      ArgumentLocation(FrameOffset(8), kXlenInBytes),
  };
  ArgumentLocation move_srcs4[] = {
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA0), 2 * kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA1), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA2), 2 * kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA3), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA4), 2 * kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA5), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA6), 2 * kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromFRegister(FA7), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A1), kVRegSize),
      ArgumentLocation(FrameOffset(92), 2 * kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A2), 2 * kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A3), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A4), 2 * kVRegSize),
      ArgumentLocation(FrameOffset(112), kVRegSize),
      ArgumentLocation(FrameOffset(116), 2 * kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A5), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A6), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A7), 2 * kVRegSize),
  };
  FrameOffset move_refs4[] {
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
  };
  __ MoveArguments(ArrayRef<ArgumentLocation>(move_dests4),
                   ArrayRef<ArgumentLocation>(move_srcs4),
                   ArrayRef<FrameOffset>(move_refs4));
  // FP args in FA0-FA7 and integral args in A2-A4 do not move.
  expected += "sd a6, 0(sp)\n"
              "sd a7, 8(sp)\n"
              "mv a0, a1\n"
              "ld a1, 92(sp)\n"
              "ld a6, 116(sp)\n"
              "mv a7, a5\n"
              "lw a5, 112(sp)\n";

  // @CriticalNative with parameters "JIJIJIJIJI".
  ArgumentLocation move_dests5[] = {
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A0), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A1), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A2), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A3), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A4), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A5), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A6), kXlenInBytes),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A7), kXlenInBytes),
      ArgumentLocation(FrameOffset(0), kXlenInBytes),
      ArgumentLocation(FrameOffset(8), kXlenInBytes),
  };
  ArgumentLocation move_srcs5[] = {
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A1), 2 * kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A2), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A3), 2 * kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A4), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A5), 2 * kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A6), kVRegSize),
      ArgumentLocation(Riscv64ManagedRegister::FromXRegister(A7), 2 * kVRegSize),
      ArgumentLocation(FrameOffset(84), kVRegSize),
      ArgumentLocation(FrameOffset(88), 2 * kVRegSize),
      ArgumentLocation(FrameOffset(96), kVRegSize),
  };
  FrameOffset move_refs5[] {
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
      FrameOffset(kInvalidReferenceOffset),
  };
  __ MoveArguments(ArrayRef<ArgumentLocation>(move_dests5),
                   ArrayRef<ArgumentLocation>(move_srcs5),
                   ArrayRef<FrameOffset>(move_refs5));
  expected += "ld t6, 88(sp)\n"
              "sd t6, 0(sp)\n"
              "lw t6, 96(sp)\n"
              "sd t6, 8(sp)\n"
              "mv a0, a1\n"
              "mv a1, a2\n"
              "mv a2, a3\n"
              "mv a3, a4\n"
              "mv a4, a5\n"
              "mv a5, a6\n"
              "mv a6, a7\n"
              "lw a7, 84(sp)\n";

  DriverStr(expected, "MoveArguments");
}

TEST_F(JniMacroAssemblerRiscv64Test, Move) {
  std::string expected;

  __ Move(AsManaged(A0), AsManaged(A1), kWordSize);
  expected += "mv a0, a1\n";
  __ Move(AsManaged(A2), AsManaged(A3), kDoubleWordSize);
  expected += "mv a2, a3\n";

  __ Move(AsManaged(A4), AsManaged(A4), kWordSize);  // No-op.
  __ Move(AsManaged(A5), AsManaged(A5), kDoubleWordSize);  // No-op.

  DriverStr(expected, "Move");
}

TEST_F(JniMacroAssemblerRiscv64Test, GetCurrentThread) {
  std::string expected;

  __ GetCurrentThread(AsManaged(A0));
  expected += "mv a0, s1\n";

  __ GetCurrentThread(FrameOffset(256));
  expected += "sd s1, 256(sp)\n";
  __ GetCurrentThread(FrameOffset(3 * KB));
  expected += "addi t6, sp, 0x7f8\n"
              "sd s1, 0x408(t6)\n";

  DriverStr(expected, "GetCurrentThread");
}

TEST_F(JniMacroAssemblerRiscv64Test, DecodeJNITransitionOrLocalJObject) {
  std::string expected;

  constexpr int64_t kGlobalOrWeakGlobalMask = IndirectReferenceTable::GetGlobalOrWeakGlobalMask();
  constexpr int64_t kIndirectRefKindMask = IndirectReferenceTable::GetIndirectRefKindMask();

  std::unique_ptr<JNIMacroLabel> slow_path = __ CreateLabel();
  std::unique_ptr<JNIMacroLabel> resume = __ CreateLabel();

  __ DecodeJNITransitionOrLocalJObject(AsManaged(A0), slow_path.get(), resume.get());
  expected += "beqz a0, 1f\n"
              "andi t6, a0, " + std::to_string(kGlobalOrWeakGlobalMask) + "\n"
              "bnez t6, 2f\n"
              "andi a0, a0, ~" + std::to_string(kIndirectRefKindMask) + "\n"
              "lwu a0, (a0)\n";

  __ Bind(resume.get());
  expected += "1:\n";

  expected += EmitRet();

  __ Bind(slow_path.get());
  expected += "2:\n";

  __ Jump(resume.get());
  expected += "j 1b\n";

  DriverStr(expected, "DecodeJNITransitionOrLocalJObject");
}

TEST_F(JniMacroAssemblerRiscv64Test, JumpCodePointer) {
  std::string expected;

  __ Jump(AsManaged(A0), Offset(24));
  expected += "ld t6, 24(a0)\n"
              "jr t6\n";

  __ Jump(AsManaged(S2), Offset(2048));
  expected += "addi t6, s2, 0x7f8\n"
              "ld t6, 8(t6)\n"
              "jr t6\n";

  DriverStr(expected, "JumpCodePointer");
}

TEST_F(JniMacroAssemblerRiscv64Test, Call) {
  std::string expected;

  __ Call(AsManaged(A0), Offset(32));
  expected += "ld ra, 32(a0)\n"
              "jalr ra\n";

  __ Call(AsManaged(S2), Offset(2048));
  expected += "addi t6, s2, 0x7f8\n"
              "ld ra, 8(t6)\n"
              "jalr ra\n";

  __ CallFromThread(ThreadOffset64(256));
  expected += "ld ra, 256(s1)\n"
              "jalr ra\n";

  __ CallFromThread(ThreadOffset64(3 * KB));
  expected += "addi t6, s1, 0x7f8\n"
              "ld ra, 0x408(t6)\n"
              "jalr ra\n";

  DriverStr(expected, "Call");
}

TEST_F(JniMacroAssemblerRiscv64Test, Transitions) {
  std::string expected;

  constexpr uint32_t kNativeStateValue = Thread::StoredThreadStateValue(ThreadState::kNative);
  constexpr uint32_t kRunnableStateValue = Thread::StoredThreadStateValue(ThreadState::kRunnable);
  static_assert(kRunnableStateValue == 0u);
  constexpr ThreadOffset64 thread_flags_offset = Thread::ThreadFlagsOffset<kRiscv64PointerSize>();
  static_assert(thread_flags_offset.SizeValue() == 0u);
  constexpr size_t thread_held_mutex_mutator_lock_offset =
      Thread::HeldMutexOffset<kRiscv64PointerSize>(kMutatorLock).SizeValue();
  constexpr size_t thread_mutator_lock_offset =
      Thread::MutatorLockOffset<kRiscv64PointerSize>().SizeValue();

  std::unique_ptr<JNIMacroLabel> slow_path = __ CreateLabel();
  std::unique_ptr<JNIMacroLabel> resume = __ CreateLabel();

  const ManagedRegister raw_scratch_regs[] = { AsManaged(T0), AsManaged(T1) };
  const ArrayRef<const ManagedRegister> scratch_regs(raw_scratch_regs);

  __ TryToTransitionFromRunnableToNative(slow_path.get(), scratch_regs);
  expected += "1:\n"
              "lr.w t0, (s1)\n"
              "li t1, " + std::to_string(kNativeStateValue) + "\n"
              "bnez t0, 4f\n"
              "sc.w.rl t0, t1, (s1)\n"
              "bnez t0, 1b\n"
              "addi t6, s1, 0x7f8\n"
              "sd x0, " + std::to_string(thread_held_mutex_mutator_lock_offset - 0x7f8u) + "(t6)\n";

  __ TryToTransitionFromNativeToRunnable(slow_path.get(), scratch_regs, AsManaged(A0));
  expected += "2:\n"
              "lr.w.aq t0, (s1)\n"
              "li t1, " + std::to_string(kNativeStateValue) + "\n"
              "bne t0, t1, 4f\n"
              "sc.w t0, x0, (s1)\n"
              "bnez t0, 2b\n"
              "ld t0, " + std::to_string(thread_mutator_lock_offset) + "(s1)\n"
              "addi t6, s1, 0x7f8\n"
              "sd t0, " + std::to_string(thread_held_mutex_mutator_lock_offset - 0x7f8u) + "(t6)\n";

  __ Bind(resume.get());
  expected += "3:\n";

  expected += EmitRet();

  __ Bind(slow_path.get());
  expected += "4:\n";

  __ Jump(resume.get());
  expected += "j 3b";

  DriverStr(expected, "SuspendCheck");
}

TEST_F(JniMacroAssemblerRiscv64Test, SuspendCheck) {
  std::string expected;

  ThreadOffset64 thread_flags_offet = Thread::ThreadFlagsOffset<kRiscv64PointerSize>();

  std::unique_ptr<JNIMacroLabel> slow_path = __ CreateLabel();
  std::unique_ptr<JNIMacroLabel> resume = __ CreateLabel();

  __ SuspendCheck(slow_path.get());
  expected += "lw t6, " + std::to_string(thread_flags_offet.Int32Value()) + "(s1)\n"
              "andi t6, t6, " + std::to_string(Thread::SuspendOrCheckpointRequestFlags()) + "\n"
              "bnez t6, 2f\n";

  __ Bind(resume.get());
  expected += "1:\n";

  expected += EmitRet();

  __ Bind(slow_path.get());
  expected += "2:\n";

  __ Jump(resume.get());
  expected += "j 1b";

  DriverStr(expected, "SuspendCheck");
}

TEST_F(JniMacroAssemblerRiscv64Test, Exception) {
  std::string expected;

  ThreadOffset64 exception_offset = Thread::ExceptionOffset<kArm64PointerSize>();
  ThreadOffset64 deliver_offset = QUICK_ENTRYPOINT_OFFSET(kArm64PointerSize, pDeliverException);

  std::unique_ptr<JNIMacroLabel> slow_path = __ CreateLabel();

  __ ExceptionPoll(slow_path.get());
  expected += "ld t6, " + std::to_string(exception_offset.Int32Value()) + "(s1)\n"
              "bnez t6, 1f\n";

  expected += EmitRet();

  __ Bind(slow_path.get());
  expected += "1:\n";

  __ DeliverPendingException();
  expected += "ld a0, " + std::to_string(exception_offset.Int32Value()) + "(s1)\n"
              "ld ra, " + std::to_string(deliver_offset.Int32Value()) + "(s1)\n"
              "jalr ra\n"
              "unimp\n";

  DriverStr(expected, "Exception");
}

TEST_F(JniMacroAssemblerRiscv64Test, JumpLabel) {
  std::string expected;

  std::unique_ptr<JNIMacroLabel> target = __ CreateLabel();
  std::unique_ptr<JNIMacroLabel> back = __ CreateLabel();

  __ Jump(target.get());
  expected += "j 2f\n";

  __ Bind(back.get());
  expected += "1:\n";

  __ Move(AsManaged(A0), AsManaged(A1), static_cast<size_t>(kRiscv64PointerSize));
  expected += "mv a0, a1\n";

  __ Bind(target.get());
  expected += "2:\n";

  __ Jump(back.get());
  expected += "j 1b\n";

  DriverStr(expected, "JumpLabel");
}

TEST_F(JniMacroAssemblerRiscv64Test, ReadBarrier) {
  std::string expected;

  ThreadOffset64 is_gc_marking_offset = Thread::IsGcMarkingOffset<kRiscv64PointerSize>();
  MemberOffset monitor_offset = mirror::Object::MonitorOffset();

  std::unique_ptr<JNIMacroLabel> slow_path = __ CreateLabel();
  std::unique_ptr<JNIMacroLabel> resume = __ CreateLabel();

  __ TestGcMarking(slow_path.get(), JNIMacroUnaryCondition::kNotZero);
  expected += "lw t6, " + std::to_string(is_gc_marking_offset.Int32Value()) + "(s1)\n"
              "bnez t6, 2f\n";

  __ TestGcMarking(slow_path.get(), JNIMacroUnaryCondition::kZero);
  expected += "lw t6, " + std::to_string(is_gc_marking_offset.Int32Value()) + "(s1)\n"
              "beqz t6, 2f\n";

  __ Bind(resume.get());
  expected += "1:\n";

  expected += EmitRet();

  __ Bind(slow_path.get());
  expected += "2:\n";

  __ TestMarkBit(AsManaged(A0), resume.get(), JNIMacroUnaryCondition::kNotZero);
  expected += "lw t6, " + std::to_string(monitor_offset.Int32Value()) + "(a0)\n"
              "slliw t6, t6, " + std::to_string(31 - LockWord::kMarkBitStateShift) + "\n"
              "bltz t6, 1b\n";

  __ TestMarkBit(AsManaged(T0), resume.get(), JNIMacroUnaryCondition::kZero);
  expected += "lw t6, " + std::to_string(monitor_offset.Int32Value()) + "(t0)\n"
              "slliw t6, t6, " + std::to_string(31 - LockWord::kMarkBitStateShift) + "\n"
              "bgez t6, 1b\n";

  DriverStr(expected, "ReadBarrier");
}

TEST_F(JniMacroAssemblerRiscv64Test, TestByteAndJumpIfNotZero) {
  // Note: The `TestByteAndJumpIfNotZero()` takes the address as a `uintptr_t`.
  // Use 32-bit addresses, so that we can include this test in 32-bit host tests.

  std::string expected;

  std::unique_ptr<JNIMacroLabel> slow_path = __ CreateLabel();
  std::unique_ptr<JNIMacroLabel> resume = __ CreateLabel();

  __ TestByteAndJumpIfNotZero(0x12345678u, slow_path.get());
  expected += "lui t6, 0x12345\n"
              "lb t6, 0x678(t6)\n"
              "bnez t6, 2f\n";

  __ TestByteAndJumpIfNotZero(0x87654321u, slow_path.get());
  expected += "lui t6, 0x87654/4\n"
              "slli t6, t6, 2\n"
              "lb t6, 0x321(t6)\n"
              "bnez t6, 2f\n";

  __ Bind(resume.get());
  expected += "1:\n";

  expected += EmitRet();

  __ Bind(slow_path.get());
  expected += "2:\n";

  __ TestByteAndJumpIfNotZero(0x456789abu, resume.get());
  expected += "lui t6, 0x45678+1\n"
              "lb t6, 0x9ab-0x1000(t6)\n"
              "bnez t6, 1b\n";

  DriverStr(expected, "TestByteAndJumpIfNotZero");
}

#undef __

}  // namespace riscv64
}  // namespace art
