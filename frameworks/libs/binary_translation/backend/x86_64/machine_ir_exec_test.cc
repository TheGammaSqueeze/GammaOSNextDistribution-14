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

#include "gtest/gtest.h"

#include <csignal>
#include <cstdint>
#include <cstring>

#include "berberis/assembler/machine_code.h"
#include "berberis/backend/code_emitter.h"
#include "berberis/backend/common/reg_alloc.h"
#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir_builder.h"
#include "berberis/backend/x86_64/machine_ir_check.h"
#include "berberis/base/arena_alloc.h"
#include "berberis/base/bit_util.h"
#include "berberis/code_gen_lib/code_gen_lib.h"  // EmitFreeStackFrame
#include "berberis/test_utils/scoped_exec_region.h"

#include "x86_64/mem_operand.h"

namespace berberis {

namespace {

// TODO(b/232598137): Maybe share with
// heavy_optimizer/<guest>_to_<host>/call_intrinsic_tests.cc.
class ExecTest {
 public:
  ExecTest() = default;

  void Init(x86_64::MachineIR& machine_ir) {
    // Add exiting jump if not already.
    auto* last_insn = machine_ir.bb_list().back()->insn_list().back();
    if (!machine_ir.IsControlTransfer(last_insn)) {
      auto* jump = machine_ir.template NewInsn<PseudoJump>(0);
      machine_ir.bb_list().back()->insn_list().push_back(jump);
    }

    EXPECT_EQ(x86_64::CheckMachineIR(machine_ir), x86_64::kMachineIRCheckSuccess);

    MachineCode machine_code;
    CodeEmitter as(
        &machine_code, machine_ir.FrameSize(), machine_ir.bb_list().size(), machine_ir.arena());

    // We need to set exit_label_for_testing before Emit, which checks it.
    auto* exit_label = as.MakeLabel();
    as.set_exit_label_for_testing(exit_label);

    // Save callee saved regs.
    as.Push(as.rbp);
    as.Push(as.rbx);
    as.Push(as.r12);
    as.Push(as.r13);
    as.Push(as.r14);
    as.Push(as.r15);
    // Align stack for calls.
    as.Subq(as.rsp, 8);

    machine_ir.Emit(&as);

    as.Bind(exit_label);
    // Memorize returned rax.
    as.Movq(as.rbp, bit_cast<int64_t>(&returned_rax_));
    as.Movq({.base = as.rbp}, as.rax);

    as.Addq(as.rsp, 8);
    // Restore callee saved regs.
    as.Pop(as.r15);
    as.Pop(as.r14);
    as.Pop(as.r13);
    as.Pop(as.r12);
    as.Pop(as.rbx);
    as.Pop(as.rbp);

    as.Ret();

    as.Finalize();

    exec_.Init(&machine_code);
  }

  void Exec() const { exec_.get<void()>()(); }

  const RecoveryMap& recovery_map() const { return exec_.recovery_map(); }

  uint64_t returned_rax() const { return returned_rax_; }

 private:
  ScopedExecRegion exec_;
  uint64_t returned_rax_;
};

// Convert flags to LAHF-compatible format.
inline uint16_t MakeFlags(bool n, bool z, bool c, bool o) {
  return (n ? (1 << 15) : 0) | (z ? (1 << 14) : 0) | (c ? (1 << 8) : 0) | (o ? 1 : 0);
}

inline uint16_t MakeFlags(uint8_t nzco_bits) {
  return MakeFlags(nzco_bits & 0b1000, nzco_bits & 0b0100, nzco_bits & 0b0010, nzco_bits & 0b0001);
}

TEST(ExecMachineIR, Smoke) {
  struct Data {
    uint64_t x;
    uint64_t y;
  } data;

  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  builder.StartBasicBlock(machine_ir.NewBasicBlock());

  // Let RBP point to 'data'.
  builder.Gen<x86_64::MovqRegImm>(x86_64::kMachineRegRBP, reinterpret_cast<uintptr_t>(&data));

  // data.y = data.x;
  builder.Gen<x86_64::MovqRegMemBaseDisp>(
      x86_64::kMachineRegRAX, x86_64::kMachineRegRBP, offsetof(Data, x));
  builder.Gen<x86_64::MovqMemBaseDispReg>(
      x86_64::kMachineRegRBP, offsetof(Data, y), x86_64::kMachineRegRAX);

  ExecTest test;
  test.Init(machine_ir);

  data.x = 1;
  data.y = 2;
  test.Exec();
  EXPECT_EQ(1ULL, data.x);
  EXPECT_EQ(1ULL, data.y);
}

TEST(ExecMachineIR, CallImm) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  builder.StartBasicBlock(machine_ir.NewBasicBlock());

  uint64_t data = 0xfeedf00d'feedf00dULL;
  builder.Gen<x86_64::MovqRegImm>(x86_64::kMachineRegRDI, data);
  auto* invert_func_ptr = +[](uint64_t arg) { return ~arg; };

  MachineReg flag_register = machine_ir.AllocVReg();
  builder.GenCallImm(bit_cast<uintptr_t>(invert_func_ptr), flag_register);

  uint64_t result = 0;
  builder.Gen<x86_64::MovqRegImm>(x86_64::kMachineRegRBP, reinterpret_cast<uintptr_t>(&result));
  builder.Gen<x86_64::MovqMemBaseDispReg>(x86_64::kMachineRegRBP, 0, x86_64::kMachineRegRAX);

  ExecTest test;
  test.Init(machine_ir);
  test.Exec();
  EXPECT_EQ(result, ~data);
}

TEST(ExecMachineIR, CallImmAllocIntOperands) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  builder.StartBasicBlock(machine_ir.NewBasicBlock());

  uint64_t data = 0xfeedf00d'feedf00dULL;
  struct Result {
    uint64_t x;
    uint64_t y;
  } result = {0, 0};
  MachineReg data_reg = machine_ir.AllocVReg();
  MachineReg flag_register = machine_ir.AllocVReg();
  auto* func_ptr = +[](uint64_t arg0,
                       uint64_t arg1,
                       uint64_t arg2,
                       uint64_t arg3,
                       uint64_t arg4,
                       uint64_t arg5) {
    uint64_t res = arg0 + arg1 + arg2 + arg3 + arg4 + arg5;
    return Result{res, res * 2};
  };

  builder.Gen<x86_64::MovqRegImm>(data_reg, data);
  std::array<x86_64::CallImm::Arg, 6> args = {{
      {data_reg, x86_64::CallImm::kIntRegType},
      {data_reg, x86_64::CallImm::kIntRegType},
      {data_reg, x86_64::CallImm::kIntRegType},
      {data_reg, x86_64::CallImm::kIntRegType},
      {data_reg, x86_64::CallImm::kIntRegType},
      {data_reg, x86_64::CallImm::kIntRegType},
  }};
  auto* call = builder.GenCallImm(bit_cast<uintptr_t>(func_ptr), flag_register, args);
  builder.Gen<x86_64::MovqRegImm>(x86_64::kMachineRegRBP, bit_cast<uintptr_t>(&result));
  builder.Gen<x86_64::MovqMemBaseDispReg>(x86_64::kMachineRegRBP, 0, call->IntResultAt(0));
  builder.Gen<x86_64::MovqMemBaseDispReg>(x86_64::kMachineRegRBP, 8, call->IntResultAt(1));

  AllocRegs(&machine_ir);

  ExecTest test;
  test.Init(machine_ir);
  test.Exec();
  EXPECT_EQ(result.x, data * 6);
  EXPECT_EQ(result.y, data * 12);
}

TEST(ExecMachineIR, CallImmAllocIntOperandsTupleResult) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  builder.StartBasicBlock(machine_ir.NewBasicBlock());

  uint64_t data = 0xfeedf00d'feedf00dULL;
  using Result = std::tuple<uint64_t, uint64_t, uint64_t>;
  Result result = std::make_tuple(0, 0, 0);
  MachineReg data_reg = machine_ir.AllocVReg();
  MachineReg result_ptr_reg = machine_ir.AllocVReg();
  MachineReg flag_register = machine_ir.AllocVReg();
  auto* func_ptr = +[](uint64_t arg0, uint64_t arg1, uint64_t arg2, uint64_t arg3, uint64_t arg4) {
    uint64_t one = arg0 + arg1 + arg2 + arg3 + arg4;
    uint64_t two = one * 2;
    uint64_t three = one * 3;
    return std::make_tuple(one, two, three);
  };

  builder.Gen<x86_64::MovqRegImm>(data_reg, data);
  builder.Gen<x86_64::MovqRegImm>(result_ptr_reg, bit_cast<uintptr_t>(&result));
  std::array<x86_64::CallImm::Arg, 6> args = {{
      {result_ptr_reg, x86_64::CallImm::kIntRegType},
      {data_reg, x86_64::CallImm::kIntRegType},
      {data_reg, x86_64::CallImm::kIntRegType},
      {data_reg, x86_64::CallImm::kIntRegType},
      {data_reg, x86_64::CallImm::kIntRegType},
      {data_reg, x86_64::CallImm::kIntRegType},
  }};
  builder.GenCallImm(bit_cast<uintptr_t>(func_ptr), flag_register, args);

  AllocRegs(&machine_ir);

  ExecTest test;
  test.Init(machine_ir);
  test.Exec();
  EXPECT_EQ(std::get<0>(result), data * 5);
  EXPECT_EQ(std::get<1>(result), data * 10);
  EXPECT_EQ(std::get<2>(result), data * 15);
}

TEST(ExecMachineIR, CallImmAllocXmmOperands) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  builder.StartBasicBlock(machine_ir.NewBasicBlock());

  double data = 42.0;
  struct Result {
    double x;
    double y;
  } result = {0, 0};
  MachineReg data_reg = machine_ir.AllocVReg();
  MachineReg data_xreg = machine_ir.AllocVReg();
  MachineReg flag_register = machine_ir.AllocVReg();
  auto* func_ptr = +[](double arg0,
                       double arg1,
                       double arg2,
                       double arg3,
                       double arg4,
                       double arg5,
                       double arg6,
                       double arg7) {
    double res = arg0 + arg1 + arg2 + arg3 + arg4 + arg5 + arg6 + arg7;
    return Result{res, res * 2};
  };

  builder.Gen<x86_64::MovqRegImm>(data_reg, bit_cast<uint64_t>(data));
  builder.Gen<x86_64::MovqXRegReg>(data_xreg, data_reg);

  std::array<x86_64::CallImm::Arg, 8> args = {{
      {data_xreg, x86_64::CallImm::kXmmRegType},
      {data_xreg, x86_64::CallImm::kXmmRegType},
      {data_xreg, x86_64::CallImm::kXmmRegType},
      {data_xreg, x86_64::CallImm::kXmmRegType},
      {data_xreg, x86_64::CallImm::kXmmRegType},
      {data_xreg, x86_64::CallImm::kXmmRegType},
      {data_xreg, x86_64::CallImm::kXmmRegType},
      {data_xreg, x86_64::CallImm::kXmmRegType},
  }};
  auto* call = builder.GenCallImm(bit_cast<uintptr_t>(func_ptr), flag_register, args);
  builder.Gen<x86_64::MovqRegImm>(x86_64::kMachineRegRBP, bit_cast<uintptr_t>(&result));
  builder.Gen<x86_64::MovqRegXReg>(data_reg, call->XmmResultAt(0));
  builder.Gen<x86_64::MovqMemBaseDispReg>(x86_64::kMachineRegRBP, 0, data_reg);
  builder.Gen<x86_64::MovqRegXReg>(data_reg, call->XmmResultAt(1));
  builder.Gen<x86_64::MovqMemBaseDispReg>(x86_64::kMachineRegRBP, 8, data_reg);

  AllocRegs(&machine_ir);

  ExecTest test;
  test.Init(machine_ir);
  test.Exec();
  EXPECT_EQ(result.x, data * 8);
  EXPECT_EQ(result.y, data * 16);
}

void ClobberAllCallerSaved() {
  constexpr uint64_t kClobberValue = 0xdeadbeef'deadbeefULL;
  asm volatile(
      "Movq %0, %%rax\n"
      "Movq %0, %%rcx\n"
      "Movq %0, %%rdx\n"
      "Movq %0, %%rdi\n"
      "Movq %0, %%rsi\n"
      "Movq %0, %%r8\n"
      "Movq %0, %%r9\n"
      "Movq %0, %%r10\n"
      "Movq %0, %%r11\n"
      "Movq %%rax, %%xmm0\n"
      "Movq %%rax, %%xmm1\n"
      "Movq %%rax, %%xmm2\n"
      "Movq %%rax, %%xmm3\n"
      "Movq %%rax, %%xmm4\n"
      "Movq %%rax, %%xmm5\n"
      "Movq %%rax, %%xmm6\n"
      "Movq %%rax, %%xmm7\n"
      "Movq %%rax, %%xmm8\n"
      "Movq %%rax, %%xmm9\n"
      "Movq %%rax, %%xmm10\n"
      "Movq %%rax, %%xmm11\n"
      "Movq %%rax, %%xmm12\n"
      "Movq %%rax, %%xmm13\n"
      "Movq %%rax, %%xmm14\n"
      "Movq %%rax, %%xmm15\n"
      :
      : "r"(kClobberValue)
      : "rax",
        "rcx",
        "rdx",
        "rdi",
        "rsi",
        "r8",
        "r9",
        "r10",
        "r11",
        "xmm0",
        "xmm1",
        "xmm2",
        "xmm3",
        "xmm4",
        "xmm5",
        "xmm6",
        "xmm7",
        "xmm8",
        "xmm9",
        "xmm10",
        "xmm11",
        "xmm12",
        "xmm13",
        "xmm14",
        "xmm15");
}

template <bool kWithCallImm>
void TestRegAlloc() {
  constexpr int N = 128;

  struct Data {
    uint64_t in_array[N];
    uint64_t out;
  } data{};

  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  builder.StartBasicBlock(machine_ir.NewBasicBlock());

  // Let rbp point to 'data'.
  builder.Gen<x86_64::MovqRegImm>(x86_64::kMachineRegRBP, reinterpret_cast<uintptr_t>(&data));

  // Read data.in_array into vregs, xor and write to data.out.

  MachineReg vregs[N];
  MachineReg xmm_vregs[N];

  for (int i = 0; i < N; ++i) {
    MachineReg v = machine_ir.AllocVReg();
    vregs[i] = v;
    builder.Gen<x86_64::MovqRegMemBaseDisp>(
        v, x86_64::kMachineRegRBP, offsetof(Data, in_array) + i * sizeof(data.in_array[0]));
    MachineReg vx = machine_ir.AllocVReg();
    xmm_vregs[i] = vx;
    builder.Gen<x86_64::MovqXRegReg>(vx, v);
  }

  if (kWithCallImm) {
    // If there is no CallImm reg-alloc assigns vregs to hard-regs until available.
    // When CallImm is here it must not allocate caller-saved regs to live across function call.
    // Ideally we should have allocated hard-regs around the call explicitly and verify that
    // reg-alloc would spill/fill them, but reg-alloc doesn't support that.
    MachineReg flag_register = machine_ir.AllocVReg();
    builder.GenCallImm(bit_cast<uintptr_t>(&ClobberAllCallerSaved), flag_register);
  }

  MachineReg v0 = machine_ir.AllocVReg();
  builder.Gen<x86_64::MovqRegImm>(v0, 0);
  MachineReg vx0 = machine_ir.AllocVReg();
  builder.Gen<x86_64::XorpdXRegXReg>(vx0, vx0);

  for (int i = 0; i < N; ++i) {
    MachineReg vflags = machine_ir.AllocVReg();
    builder.Gen<x86_64::XorqRegReg>(v0, vregs[i], vflags);
    builder.Gen<x86_64::XorpdXRegXReg>(vx0, xmm_vregs[i]);
  }

  MachineReg v1 = machine_ir.AllocVReg();
  builder.Gen<x86_64::MovqRegXReg>(v1, vx0);
  MachineReg vflags = machine_ir.AllocVReg();
  builder.Gen<x86_64::AddqRegReg>(v1, v0, vflags);
  builder.Gen<x86_64::MovqMemBaseDispReg>(x86_64::kMachineRegRBP, offsetof(Data, out), v1);

  AllocRegs(&machine_ir);

  ExecTest test;
  test.Init(machine_ir);

  uint64_t res = 0;
  for (int i = 0; i < N; ++i) {
    // Add some irregularity to ensure the result isn't zero.
    data.in_array[i] = i + (res << 4);
    res ^= data.in_array[i];
  }
  // Sum for vregs and xmm_regs.
  res *= 2;
  test.Exec();
  EXPECT_EQ(res, data.out);
}

TEST(ExecMachineIR, SmokeRegAlloc) {
  TestRegAlloc<false>();
}

TEST(ExecMachineIR, RegAllocWithCallImm) {
  TestRegAlloc<true>();
}

TEST(ExecMachineIR, MemOperand) {
  struct Data {
    uint64_t in_base_disp;
    uint64_t in_index_disp;
    uint64_t in_base_index_disp[3];

    uint64_t out_base_disp;
    uint64_t out_index_disp;
    uint64_t out_base_index_disp;
  } data = {};

  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  builder.StartBasicBlock(machine_ir.NewBasicBlock());

  data.in_base_disp = 0xaaaabbbbccccddddULL;
  data.in_index_disp = 0xdeadbeefdeadbeefULL;
  data.in_base_index_disp[2] = 0xcafefeedf00dfeedULL;

  // Base address.
  MachineReg base_reg = machine_ir.AllocVReg();
  builder.Gen<x86_64::MovqRegImm>(base_reg, reinterpret_cast<uintptr_t>(&data));

  MachineReg data_reg;

  // BaseDisp
  x86_64::MemOperand mem_base_disp =
      x86_64::MemOperand::MakeBaseDisp(base_reg, offsetof(Data, in_base_disp));
  data_reg = machine_ir.AllocVReg();
  x86_64::GenArgsMem<x86_64::MovzxblRegMemInsns>(&builder, mem_base_disp, data_reg);
  builder.Gen<x86_64::MovqMemBaseDispReg>(base_reg, offsetof(Data, out_base_disp), data_reg);

  // IndexDisp
  MachineReg index_reg = machine_ir.AllocVReg();
  static_assert(alignof(struct Data) >= 2);
  builder.Gen<x86_64::MovqRegImm>(index_reg, reinterpret_cast<uintptr_t>(&data) / 2);
  x86_64::MemOperand mem_index_disp =
      x86_64::MemOperand::MakeIndexDisp<x86_64::MachineMemOperandScale::kTwo>(
          index_reg, offsetof(Data, in_index_disp));
  data_reg = machine_ir.AllocVReg();
  x86_64::GenArgsMem<x86_64::MovzxblRegMemInsns>(&builder, mem_index_disp, data_reg);
  builder.Gen<x86_64::MovqMemBaseDispReg>(base_reg, offsetof(Data, out_index_disp), data_reg);

  // BaseIndexDisp
  MachineReg tmp_base_reg = machine_ir.AllocVReg();
  builder.Gen<x86_64::MovqRegImm>(tmp_base_reg,
                                  reinterpret_cast<uintptr_t>(&data.in_base_index_disp[0]));
  MachineReg tmp_index_reg = machine_ir.AllocVReg();
  builder.Gen<x86_64::MovqRegImm>(tmp_index_reg, 2);
  x86_64::MemOperand mem_base_index_disp =
      x86_64::MemOperand::MakeBaseIndexDisp<x86_64::MachineMemOperandScale::kFour>(
          tmp_base_reg, tmp_index_reg, 8);
  data_reg = machine_ir.AllocVReg();
  x86_64::GenArgsMem<x86_64::MovzxblRegMemInsns>(&builder, mem_base_index_disp, data_reg);
  builder.Gen<x86_64::MovqMemBaseDispReg>(base_reg, offsetof(Data, out_base_index_disp), data_reg);

  AllocRegs(&machine_ir);

  ExecTest test;
  test.Init(machine_ir);

  test.Exec();
  EXPECT_EQ(data.out_base_disp, 0xddU);
  EXPECT_EQ(data.out_index_disp, 0xefU);
  EXPECT_EQ(data.out_base_index_disp, 0xedU);
}

const MachineReg kGRegs[]{
    x86_64::kMachineRegR8,
    x86_64::kMachineRegR9,
    x86_64::kMachineRegR10,
    x86_64::kMachineRegR11,
    x86_64::kMachineRegRSI,
    x86_64::kMachineRegRDI,
    x86_64::kMachineRegRAX,
    x86_64::kMachineRegRBX,
    x86_64::kMachineRegRCX,
    x86_64::kMachineRegRDX,
    x86_64::kMachineRegR12,
    x86_64::kMachineRegR13,
    x86_64::kMachineRegR14,
    x86_64::kMachineRegR15,
};

const MachineReg kXmms[]{
    x86_64::kMachineRegXMM0,
    x86_64::kMachineRegXMM1,
    x86_64::kMachineRegXMM2,
    x86_64::kMachineRegXMM3,
    x86_64::kMachineRegXMM4,
    x86_64::kMachineRegXMM5,
    x86_64::kMachineRegXMM6,
    x86_64::kMachineRegXMM7,
    x86_64::kMachineRegXMM8,
    x86_64::kMachineRegXMM9,
    x86_64::kMachineRegXMM10,
    x86_64::kMachineRegXMM11,
    x86_64::kMachineRegXMM12,
    x86_64::kMachineRegXMM13,
    x86_64::kMachineRegXMM14,
    x86_64::kMachineRegXMM15,
};

class ExecMachineIRTest : public ::testing::Test {
 protected:
  struct Xmm {
    uint64_t lo;
    uint64_t hi;
  };
  static_assert(sizeof(Xmm) == 16, "bad xmm type");

  struct Data {
    uint64_t gregs[std::size(kGRegs)];
    Xmm xmms[std::size(kXmms)];
    Xmm slots[16];
  };
  static_assert(sizeof(Data) % sizeof(uint64_t) == 0, "bad data type");

  static void InitData(Data* data) {
    // Try to have all 4-byte pieces different. This way we ensure that the
    // upper half of gregs is also meaningful.
    char* p = reinterpret_cast<char*>(data);
    constexpr size_t kUnitSize = 4;
    static_assert((sizeof(Data) % kUnitSize) == 0);
    for (size_t i = 0; i < (sizeof(Data) / kUnitSize); ++i) {
      static_assert(sizeof(i) >= kUnitSize);
      memcpy(p + kUnitSize * i, &i, kUnitSize);
    }
  }

  static void ExpectEqualData(const Data& x, const Data& y) {
    for (size_t i = 0; i < std::size(x.gregs); ++i) {
      EXPECT_EQ(x.gregs[i], y.gregs[i]) << "gregs differ at index " << i;
    }
    for (size_t i = 0; i < std::size(x.xmms); ++i) {
      EXPECT_EQ(x.xmms[i].lo, y.xmms[i].lo) << "xmms lo differ at index " << i;
      EXPECT_EQ(x.xmms[i].hi, y.xmms[i].hi) << "xmms hi differ at index " << i;
    }
    for (size_t i = 0; i < std::size(x.slots); ++i) {
      EXPECT_EQ(x.slots[i].lo, y.slots[i].lo) << "slots lo differ at index " << i;
      EXPECT_EQ(x.slots[i].hi, y.slots[i].hi) << "slots hi differ at index " << i;
    }
  }

  ExecMachineIRTest() : machine_ir_(&arena_), builder_(&machine_ir_), data_{} {
    bb_ = machine_ir_.NewBasicBlock();
    builder_.StartBasicBlock(bb_);

    // Let rbp point to 'data'.
    builder_.Gen<x86_64::MovqRegImm>(x86_64::kMachineRegRBP, reinterpret_cast<uintptr_t>(&data_));

    for (size_t i = 0; i < std::size(data_.slots); ++i) {
      slots_[i] = MachineReg::CreateSpilledRegFromIndex(
          machine_ir_.SpillSlotOffset(machine_ir_.AllocSpill()));

      builder_.Gen<x86_64::MovdquXRegMemBaseDisp>(
          x86_64::kMachineRegXMM0,
          x86_64::kMachineRegRBP,
          offsetof(Data, slots) + i * sizeof(data_.slots[0]));
      builder_.Gen<PseudoCopy>(slots_[i], x86_64::kMachineRegXMM0, 16);
    }

    for (size_t i = 0; i < std::size(kXmms); ++i) {
      builder_.Gen<x86_64::MovdquXRegMemBaseDisp>(
          kXmms[i], x86_64::kMachineRegRBP, offsetof(Data, xmms) + i * sizeof(data_.xmms[0]));
    }

    for (size_t i = 0; i < std::size(kGRegs); ++i) {
      builder_.Gen<x86_64::MovqRegMemBaseDisp>(
          kGRegs[i], x86_64::kMachineRegRBP, offsetof(Data, gregs) + i * sizeof(data_.gregs[0]));
    }
  }

  void Finalize() {
    for (size_t i = 0; i < std::size(kGRegs); ++i) {
      builder_.Gen<x86_64::MovqMemBaseDispReg>(
          x86_64::kMachineRegRBP, offsetof(Data, gregs) + i * sizeof(data_.gregs[0]), kGRegs[i]);
    }

    for (size_t i = 0; i < std::size(kXmms); ++i) {
      builder_.Gen<x86_64::MovdquMemBaseDispXReg>(
          x86_64::kMachineRegRBP, offsetof(Data, xmms) + i * sizeof(data_.xmms[0]), kXmms[i]);
    }

    for (size_t i = 0; i < std::size(data_.slots); ++i) {
      builder_.Gen<PseudoCopy>(x86_64::kMachineRegXMM0, slots_[i], 16);
      builder_.Gen<x86_64::MovdquMemBaseDispXReg>(
          x86_64::kMachineRegRBP,
          offsetof(Data, slots) + i * sizeof(data_.slots[0]),
          x86_64::kMachineRegXMM0);
    }

    test_.Init(machine_ir_);
  }

  Arena arena_;
  x86_64::MachineIR machine_ir_;
  x86_64::MachineIRBuilder builder_;
  MachineBasicBlock* bb_;
  Data data_;
  MachineReg slots_[std::size(Data{}.slots)];
  ExecTest test_;
};

TEST_F(ExecMachineIRTest, Copy) {
  InitData(&data_);
  Data dst_data = data_;

  builder_.Gen<PseudoCopy>(kGRegs[1], kGRegs[0], 8);
  dst_data.gregs[1] = data_.gregs[0];

  builder_.Gen<PseudoCopy>(slots_[0], kXmms[0], 8);
  dst_data.slots[0].lo = data_.xmms[0].lo;

  builder_.Gen<PseudoCopy>(slots_[1], kXmms[1], 16);
  dst_data.slots[1] = data_.xmms[1];

  builder_.Gen<PseudoCopy>(kXmms[3], kXmms[2], 16);
  dst_data.xmms[3] = data_.xmms[2];

  // The minimum copy amount is 8 bytes. PseudoCopy of a smaller size will copy
  // garbage in upper bytes. This is in compliance with MachineIR assumptions,
  // but we cannot reliably test it.
  builder_.Gen<PseudoCopy>(slots_[5], slots_[4], 8);
  dst_data.slots[5].lo = data_.slots[4].lo;

  builder_.Gen<PseudoCopy>(slots_[7], slots_[6], 16);
  dst_data.slots[7] = data_.slots[6];

  Finalize();
  test_.Exec();
  ExpectEqualData(data_, dst_data);
}

// TODO(b/200327919): Share with tests in runtime.
class ScopedSignalHandler {
 public:
  ScopedSignalHandler(int sig, void (*action)(int, siginfo_t*, void*)) : sig_(sig) {
    struct sigaction act {};
    act.sa_sigaction = action;
    act.sa_flags = SA_SIGINFO;
    sigaction(sig_, &act, &old_act_);
  }

  ~ScopedSignalHandler() { sigaction(sig_, &old_act_, nullptr); }

 private:
  int sig_;
  struct sigaction old_act_;
};

const RecoveryMap* g_recovery_map;

void SigsegvHandler(int sig, siginfo_t*, void* context) {
  ASSERT_EQ(sig, SIGSEGV);

  ucontext_t* ucontext = reinterpret_cast<ucontext_t*>(context);
  uintptr_t rip = ucontext->uc_mcontext.gregs[REG_RIP];
  auto it = g_recovery_map->find(rip);
  ASSERT_TRUE(it != g_recovery_map->end());
  ucontext->uc_mcontext.gregs[REG_RIP] = it->second;
}

TEST(ExecMachineIR, RecoveryBlock) {
  ScopedSignalHandler handler(SIGSEGV, SigsegvHandler);

  Arena arena;
  x86_64::MachineIR machine_ir(&arena);
  constexpr auto kScratchReg = x86_64::kMachineRegRBP;
  auto* main_bb = machine_ir.NewBasicBlock();
  auto* recovery_bb = machine_ir.NewBasicBlock();

  x86_64::MachineIRBuilder builder(&machine_ir);
  builder.StartBasicBlock(main_bb);
  // Cause a SIGSEGV.
  builder.Gen<x86_64::XorqRegReg>(kScratchReg, kScratchReg, x86_64::kMachineRegFLAGS);
  builder.Gen<x86_64::MovqMemBaseDispReg>(kScratchReg, 0, kScratchReg);
  builder.SetRecoveryPointAtLastInsn(recovery_bb);
  builder.Gen<PseudoJump>(21ULL);

  builder.StartBasicBlock(recovery_bb);
  builder.Gen<PseudoJump>(42ULL);

  machine_ir.AddEdge(main_bb, recovery_bb);

  ExecTest test;
  test.Init(machine_ir);
  g_recovery_map = &test.recovery_map();

  test.Exec();

  // Guest PC for recovery is set in RAX.
  EXPECT_EQ(test.returned_rax(), 42ULL);
}

TEST(ExecMachineIR, RecoveryWithGuestPC) {
  ScopedSignalHandler handler(SIGSEGV, SigsegvHandler);

  Arena arena;
  x86_64::MachineIR machine_ir(&arena);
  constexpr auto kScratchReg = x86_64::kMachineRegRBP;

  x86_64::MachineIRBuilder builder(&machine_ir);
  builder.StartBasicBlock(machine_ir.NewBasicBlock());
  // Cause a SIGSEGV.
  builder.Gen<x86_64::XorqRegReg>(kScratchReg, kScratchReg, x86_64::kMachineRegFLAGS);
  builder.Gen<x86_64::MovqMemBaseDispReg>(kScratchReg, 0, kScratchReg);
  builder.SetRecoveryWithGuestPCAtLastInsn(42ULL);

  ExecTest test;
  test.Init(machine_ir);
  g_recovery_map = &test.recovery_map();

  test.Exec();

  // Guest PC for recovery is set to RAX.
  EXPECT_EQ(test.returned_rax(), 42ULL);
}

TEST(ExecMachineIR, PseudoReadFlags) {
  struct Data {
    uint64_t x;
    uint64_t y;
  } data{};
  uint64_t res_flags;

  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  builder.StartBasicBlock(machine_ir.NewBasicBlock());

  // Let RBP point to 'data'.
  builder.Gen<x86_64::MovqRegImm>(x86_64::kMachineRegRBP, reinterpret_cast<uintptr_t>(&data));
  builder.Gen<x86_64::MovqRegMemBaseDisp>(
      x86_64::kMachineRegRAX, x86_64::kMachineRegRBP, offsetof(Data, x));
  builder.Gen<x86_64::AddqRegMemBaseDisp>(
      x86_64::kMachineRegRAX, x86_64::kMachineRegRBP, offsetof(Data, y), x86_64::kMachineRegFLAGS);
  builder.Gen<PseudoReadFlags>(
      PseudoReadFlags::kWithOverflow, x86_64::kMachineRegRAX, x86_64::kMachineRegFLAGS);
  builder.Gen<x86_64::MovqRegImm>(x86_64::kMachineRegRBP, reinterpret_cast<uintptr_t>(&res_flags));
  builder.Gen<x86_64::MovqMemBaseDispReg>(x86_64::kMachineRegRBP, 0, x86_64::kMachineRegRAX);

  ExecTest test;
  test.Init(machine_ir);

  data.x = 1;
  data.y = 1;
  test.Exec();
  EXPECT_EQ(res_flags & MakeFlags(0b1111), MakeFlags(0b0000));

  data.x = ~0ULL;
  data.y = 1;
  test.Exec();
  EXPECT_EQ(res_flags & MakeFlags(0b1111), MakeFlags(0b0110));

  data.x = (~0ULL) >> 1;
  data.y = 1;
  test.Exec();
  EXPECT_EQ(res_flags & MakeFlags(0b1111), MakeFlags(0b1001));
}

TEST(ExecMachineIR, PseudoReadFlagsWithoutOverflow) {
  struct Data {
    uint64_t x;
    uint64_t y;
  } data{};
  uint64_t res_flags;

  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  builder.StartBasicBlock(machine_ir.NewBasicBlock());

  // Let RBP point to 'data'.
  builder.Gen<x86_64::MovqRegImm>(x86_64::kMachineRegRBP, reinterpret_cast<uintptr_t>(&data));
  builder.Gen<x86_64::MovqRegMemBaseDisp>(
      x86_64::kMachineRegRAX, x86_64::kMachineRegRBP, offsetof(Data, x));
  builder.Gen<x86_64::AddqRegMemBaseDisp>(
      x86_64::kMachineRegRAX, x86_64::kMachineRegRBP, offsetof(Data, y), x86_64::kMachineRegFLAGS);
  // ReadFlags must reset overflow to zero, even if it's set in RAX.
  builder.Gen<x86_64::MovqRegImm>(x86_64::kMachineRegRAX, MakeFlags(0b0001));
  builder.Gen<PseudoReadFlags>(
      PseudoReadFlags::kWithoutOverflow, x86_64::kMachineRegRAX, x86_64::kMachineRegFLAGS);
  builder.Gen<x86_64::MovqRegImm>(x86_64::kMachineRegRBP, reinterpret_cast<uintptr_t>(&res_flags));
  builder.Gen<x86_64::MovqMemBaseDispReg>(x86_64::kMachineRegRBP, 0, x86_64::kMachineRegRAX);

  ExecTest test;
  test.Init(machine_ir);

  data.x = (~0ULL) >> 1;
  data.y = 1;
  test.Exec();
  // Overflow happens but is not returned.
  EXPECT_EQ(res_flags & MakeFlags(0b1111), MakeFlags(0b1000));
}

TEST(ExecMachineIR, PseudoWriteFlags) {
  uint64_t arg_flags;
  uint64_t res_flags;

  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  builder.StartBasicBlock(machine_ir.NewBasicBlock());

  builder.Gen<x86_64::MovqRegImm>(x86_64::kMachineRegRBP, reinterpret_cast<uintptr_t>(&arg_flags));
  builder.Gen<x86_64::MovqRegMemBaseDisp>(x86_64::kMachineRegRAX, x86_64::kMachineRegRBP, 0);
  builder.Gen<PseudoWriteFlags>(x86_64::kMachineRegRAX, x86_64::kMachineRegFLAGS);
  // Assume PseudoReadFlags is verified by another test.
  builder.Gen<PseudoReadFlags>(
      PseudoReadFlags::kWithOverflow, x86_64::kMachineRegRAX, x86_64::kMachineRegFLAGS);
  builder.Gen<x86_64::MovqRegImm>(x86_64::kMachineRegRBP, reinterpret_cast<uintptr_t>(&res_flags));
  builder.Gen<x86_64::MovqMemBaseDispReg>(x86_64::kMachineRegRBP, 0, x86_64::kMachineRegRAX);

  ExecTest test;
  test.Init(machine_ir);

  arg_flags = MakeFlags(0b1111);
  res_flags = 0;
  test.Exec();
  EXPECT_EQ(res_flags & MakeFlags(0b1111), MakeFlags(0b1111));

  arg_flags = MakeFlags(0b1010);
  res_flags = 0;
  test.Exec();
  EXPECT_EQ(res_flags & MakeFlags(0b1111), MakeFlags(0b1010));

  arg_flags = MakeFlags(0b0101);
  res_flags = 0;
  test.Exec();
  EXPECT_EQ(res_flags & MakeFlags(0b1111), MakeFlags(0b0101));
}

}  // namespace

}  // namespace berberis
