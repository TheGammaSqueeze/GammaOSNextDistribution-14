/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file excenaupt in compliance with the License.
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

#include "berberis/interpreter/riscv64/interpreter.h"

#include <atomic>
#include <cfenv>
#include <cstdint>
#include <cstring>

#include "berberis/base/bit_util.h"
#include "berberis/base/checks.h"
#include "berberis/base/macros.h"
#include "berberis/decoder/riscv64/decoder.h"
#include "berberis/decoder/riscv64/semantics_player.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state.h"
#include "berberis/intrinsics/guest_fp_flags.h"  // ToHostRoundingMode
#include "berberis/intrinsics/intrinsics.h"
#include "berberis/intrinsics/intrinsics_float.h"
#include "berberis/intrinsics/riscv64/vector_intrinsics.h"
#include "berberis/intrinsics/simd_register.h"
#include "berberis/intrinsics/type_traits.h"
#include "berberis/kernel_api/run_guest_syscall.h"
#include "berberis/runtime_primitives/interpret_helpers.h"
#include "berberis/runtime_primitives/memory_region_reservation.h"
#include "berberis/runtime_primitives/recovery_code.h"

#include "faulty_memory_accesses.h"
#include "regs.h"

namespace berberis {

inline constexpr std::memory_order AqRlToStdMemoryOrder(bool aq, bool rl) {
  if (aq) {
    if (rl) {
      return std::memory_order_acq_rel;
    } else {
      return std::memory_order_acquire;
    }
  } else {
    if (rl) {
      return std::memory_order_release;
    } else {
      return std::memory_order_relaxed;
    }
  }
}

class Interpreter {
 public:
  using CsrName = berberis::CsrName;
  using Decoder = Decoder<SemanticsPlayer<Interpreter>>;
  using Register = uint64_t;
  using FpRegister = uint64_t;
  using Float32 = intrinsics::Float32;
  using Float64 = intrinsics::Float64;

  explicit Interpreter(ThreadState* state)
      : state_(state), branch_taken_(false), exception_raised_(false) {}

  //
  // Instruction implementations.
  //

  Register UpdateCsr(Decoder::CsrOpcode opcode, Register arg, Register csr) {
    switch (opcode) {
      case Decoder::CsrOpcode::kCsrrs:
        return arg | csr;
      case Decoder::CsrOpcode::kCsrrc:
        return ~arg & csr;
      default:
        Unimplemented();
        return {};
    }
  }

  Register UpdateCsr(Decoder::CsrImmOpcode opcode, uint8_t imm, Register csr) {
    return UpdateCsr(static_cast<Decoder::CsrOpcode>(opcode), imm, csr);
  }

  // Note: we prefer not to use C11/C++ atomic_thread_fence or even gcc/clang builtin
  // __atomic_thread_fence because all these function rely on the fact that compiler never uses
  // non-temporal loads and stores and only issue “mfence” when sequentially consistent ordering is
  // requested. They never issue “lfence” or “sfence”.
  // Instead we pull the page from Linux's kernel book and map read ordereding to “lfence”, write
  // ordering to “sfence” and read-write ordering to “mfence”.
  // This can be important in the future if we would start using nontemporal moves in manually
  // created assembly code.
  // Ordering affecting I/O devices is not relevant to user-space code thus we just ignore bits
  // related to devices I/O.
  void Fence(Decoder::FenceOpcode /*opcode*/,
             Register /*src*/,
             bool sw,
             bool sr,
             bool /*so*/,
             bool /*si*/,
             bool pw,
             bool pr,
             bool /*po*/,
             bool /*pi*/) {
    bool read_fence = sr | pr;
    bool write_fence = sw | pw;
    // Two types of fences (total store ordering fence and normal fence) are supposed to be
    // processed differently, but only for the “read_fence && write_fence” case (otherwise total
    // store ordering fence becomes normal fence for the “forward compatibility”), yet because x86
    // doesn't distinguish between these two types of fences and since we are supposed to map all
    // not-yet defined fences to normal fence (again, for the “forward compatibility”) it's Ok to
    // just ignore opcode field.
    if (read_fence) {
      if (write_fence) {
        asm volatile("mfence" ::: "memory");
      } else {
        asm volatile("lfence" ::: "memory");
      }
    } else if (write_fence) {
      asm volatile("sfence" ::: "memory");
    }
    return;
  }

  void FenceI(Register /*arg*/, int16_t /*imm*/) {
    // For interpreter-only mode we don't need to do anything here, but when we will have a
    // translator we would need to flush caches here.
  }

  template <typename IntType, bool aq, bool rl>
  Register Lr(int64_t addr) {
    static_assert(std::is_integral_v<IntType>, "Lr: IntType must be integral");
    static_assert(std::is_signed_v<IntType>, "Lr: IntType must be signed");
    CHECK(!exception_raised_);
    // Address must be aligned on size of IntType.
    CHECK((addr % sizeof(IntType)) == 0ULL);
    return MemoryRegionReservation::Load<IntType>(&state_->cpu, addr, AqRlToStdMemoryOrder(aq, rl));
  }

  template <typename IntType, bool aq, bool rl>
  Register Sc(int64_t addr, IntType val) {
    static_assert(std::is_integral_v<IntType>, "Sc: IntType must be integral");
    static_assert(std::is_signed_v<IntType>, "Sc: IntType must be signed");
    CHECK(!exception_raised_);
    // Address must be aligned on size of IntType.
    CHECK((addr % sizeof(IntType)) == 0ULL);
    return static_cast<Register>(MemoryRegionReservation::Store<IntType>(
        &state_->cpu, addr, val, AqRlToStdMemoryOrder(aq, rl)));
  }

  Register Op(Decoder::OpOpcode opcode, Register arg1, Register arg2) {
    switch (opcode) {
      case Decoder::OpOpcode::kAdd:
        return Int64(arg1) + Int64(arg2);
      case Decoder::OpOpcode::kSub:
        return Int64(arg1) - Int64(arg2);
      case Decoder::OpOpcode::kAnd:
        return Int64(arg1) & Int64(arg2);
      case Decoder::OpOpcode::kOr:
        return Int64(arg1) | Int64(arg2);
      case Decoder::OpOpcode::kXor:
        return Int64(arg1) ^ Int64(arg2);
      case Decoder::OpOpcode::kSll:
        return Int64(arg1) << Int64(arg2);
      case Decoder::OpOpcode::kSrl:
        return UInt64(arg1) >> Int64(arg2);
      case Decoder::OpOpcode::kSra:
        return Int64(arg1) >> Int64(arg2);
      case Decoder::OpOpcode::kSlt:
        return Int64(arg1) < Int64(arg2) ? 1 : 0;
      case Decoder::OpOpcode::kSltu:
        return UInt64(arg1) < UInt64(arg2) ? 1 : 0;
      case Decoder::OpOpcode::kMul:
        return Int64(arg1) * Int64(arg2);
      case Decoder::OpOpcode::kMulh:
        return NarrowTopHalf(Widen(Int64(arg1)) * Widen(Int64(arg2)));
      case Decoder::OpOpcode::kMulhsu:
        return NarrowTopHalf(Widen(Int64(arg1)) * BitCastToSigned(Widen(UInt64(arg2))));
      case Decoder::OpOpcode::kMulhu:
        return NarrowTopHalf(Widen(UInt64(arg1)) * Widen(UInt64(arg2)));
      case Decoder::OpOpcode::kDiv:
        return Int64(arg1) / Int64(arg2);
      case Decoder::OpOpcode::kDivu:
        return UInt64(arg1) / UInt64(arg2);
      case Decoder::OpOpcode::kRem:
        return Int64(arg1) % Int64(arg2);
      case Decoder::OpOpcode::kRemu:
        return UInt64(arg1) % UInt64(arg2);
      case Decoder::OpOpcode::kAndn:
        return Int64(arg1) & (~Int64(arg2));
      case Decoder::OpOpcode::kOrn:
        return Int64(arg1) | (~Int64(arg2));
      case Decoder::OpOpcode::kXnor:
        return ~(Int64(arg1) ^ Int64(arg2));
      default:
        Unimplemented();
        return {};
    }
  }

  Register Op32(Decoder::Op32Opcode opcode, Register arg1, Register arg2) {
    switch (opcode) {
      case Decoder::Op32Opcode::kAddw:
        return Widen(TruncateTo<Int32>(arg1) + TruncateTo<Int32>(arg2));
      case Decoder::Op32Opcode::kSubw:
        return Widen(TruncateTo<Int32>(arg1) - TruncateTo<Int32>(arg2));
      case Decoder::Op32Opcode::kSllw:
        return Widen(TruncateTo<Int32>(arg1) << TruncateTo<Int32>(arg2));
      case Decoder::Op32Opcode::kSrlw:
        return Widen(BitCastToSigned(TruncateTo<UInt32>(arg1) >> TruncateTo<Int32>(arg2)));
      case Decoder::Op32Opcode::kSraw:
        return Widen(TruncateTo<Int32>(arg1) >> TruncateTo<Int32>(arg2));
      case Decoder::Op32Opcode::kMulw:
        return Widen(TruncateTo<Int32>(arg1) * TruncateTo<Int32>(arg2));
      case Decoder::Op32Opcode::kDivw:
        return Widen(TruncateTo<Int32>(arg1) / TruncateTo<Int32>(arg2));
      case Decoder::Op32Opcode::kDivuw:
        return Widen(BitCastToSigned(TruncateTo<UInt32>(arg1) / TruncateTo<UInt32>(arg2)));
      case Decoder::Op32Opcode::kRemw:
        return Widen(TruncateTo<Int32>(arg1) % TruncateTo<Int32>(arg2));
      case Decoder::Op32Opcode::kRemuw:
        return Widen(BitCastToSigned(TruncateTo<UInt32>(arg1) % TruncateTo<UInt32>(arg2)));
      default:
        Unimplemented();
        return {};
    }
  }

  Register Load(Decoder::LoadOperandType operand_type, Register arg, int16_t offset) {
    void* ptr = ToHostAddr<void>(arg + offset);
    switch (operand_type) {
      case Decoder::LoadOperandType::k8bitUnsigned:
        return Load<uint8_t>(ptr);
      case Decoder::LoadOperandType::k16bitUnsigned:
        return Load<uint16_t>(ptr);
      case Decoder::LoadOperandType::k32bitUnsigned:
        return Load<uint32_t>(ptr);
      case Decoder::LoadOperandType::k64bit:
        return Load<uint64_t>(ptr);
      case Decoder::LoadOperandType::k8bitSigned:
        return Load<int8_t>(ptr);
      case Decoder::LoadOperandType::k16bitSigned:
        return Load<int16_t>(ptr);
      case Decoder::LoadOperandType::k32bitSigned:
        return Load<int32_t>(ptr);
      default:
        Unimplemented();
        return {};
    }
  }

  template <typename DataType>
  FpRegister LoadFp(Register arg, int16_t offset) {
    static_assert(std::is_same_v<DataType, Float32> || std::is_same_v<DataType, Float64>);
    CHECK(!exception_raised_);
    DataType* ptr = ToHostAddr<DataType>(arg + offset);
    FaultyLoadResult result = FaultyLoad(ptr, sizeof(DataType));
    if (result.is_fault) {
      exception_raised_ = true;
      return {};
    }
    return result.value;
  }

  Register OpImm(Decoder::OpImmOpcode opcode, Register arg, int16_t imm) {
    switch (opcode) {
      case Decoder::OpImmOpcode::kAddi:
        return arg + int64_t{imm};
      case Decoder::OpImmOpcode::kSlti:
        return bit_cast<int64_t>(arg) < int64_t{imm} ? 1 : 0;
      case Decoder::OpImmOpcode::kSltiu:
        return arg < bit_cast<uint64_t>(int64_t{imm}) ? 1 : 0;
      case Decoder::OpImmOpcode::kXori:
        return arg ^ int64_t { imm };
      case Decoder::OpImmOpcode::kOri:
        return arg | int64_t{imm};
      case Decoder::OpImmOpcode::kAndi:
        return arg & int64_t{imm};
      default:
        Unimplemented();
        return {};
    }
  }

  Register Lui(int32_t imm) { return int64_t{imm}; }

  Register Auipc(int32_t imm) {
    uint64_t pc = state_->cpu.insn_addr;
    return pc + int64_t{imm};
  }

  Register OpImm32(Decoder::OpImm32Opcode opcode, Register arg, int16_t imm) {
    switch (opcode) {
      case Decoder::OpImm32Opcode::kAddiw:
        return int32_t(arg) + int32_t{imm};
      default:
        Unimplemented();
        return {};
    }
  }

  Register Ecall(Register syscall_nr,
                 Register arg0,
                 Register arg1,
                 Register arg2,
                 Register arg3,
                 Register arg4,
                 Register arg5) {
    CHECK(!exception_raised_);
    return RunGuestSyscall(syscall_nr, arg0, arg1, arg2, arg3, arg4, arg5);
  }

  Register Slli(Register arg, int8_t imm) { return arg << imm; }

  Register Srli(Register arg, int8_t imm) { return arg >> imm; }

  Register Srai(Register arg, int8_t imm) { return bit_cast<int64_t>(arg) >> imm; }

  Register ShiftImm32(Decoder::ShiftImm32Opcode opcode, Register arg, uint16_t imm) {
    switch (opcode) {
      case Decoder::ShiftImm32Opcode::kSlliw:
        return int32_t(arg) << int32_t{imm};
      case Decoder::ShiftImm32Opcode::kSrliw:
        return bit_cast<int32_t>(uint32_t(arg) >> uint32_t{imm});
      case Decoder::ShiftImm32Opcode::kSraiw:
        return int32_t(arg) >> int32_t{imm};
      default:
        Unimplemented();
        return {};
    }
  }

  Register Rori(Register arg, int8_t shamt) {
    CheckShamtIsValid(shamt);
    return (((uint64_t(arg) >> shamt)) | (uint64_t(arg) << (64 - shamt)));
  }

  Register Roriw(Register arg, int8_t shamt) {
    CheckShamt32IsValid(shamt);
    return int32_t(((uint32_t(arg) >> shamt)) | (uint32_t(arg) << (32 - shamt)));
  }

  void Store(Decoder::MemoryDataOperandType operand_type,
             Register arg,
             int16_t offset,
             Register data) {
    void* ptr = ToHostAddr<void>(arg + offset);
    switch (operand_type) {
      case Decoder::MemoryDataOperandType::k8bit:
        Store<uint8_t>(ptr, data);
        break;
      case Decoder::MemoryDataOperandType::k16bit:
        Store<uint16_t>(ptr, data);
        break;
      case Decoder::MemoryDataOperandType::k32bit:
        Store<uint32_t>(ptr, data);
        break;
      case Decoder::MemoryDataOperandType::k64bit:
        Store<uint64_t>(ptr, data);
        break;
      default:
        return Unimplemented();
    }
  }

  template <typename DataType>
  void StoreFp(Register arg, int16_t offset, FpRegister data) {
    static_assert(std::is_same_v<DataType, Float32> || std::is_same_v<DataType, Float64>);
    CHECK(!exception_raised_);
    DataType* ptr = ToHostAddr<DataType>(arg + offset);
    exception_raised_ = FaultyStore(ptr, sizeof(DataType), data);
  }

  void CompareAndBranch(Decoder::BranchOpcode opcode,
                        Register arg1,
                        Register arg2,
                        int16_t offset) {
    bool cond_value;
    switch (opcode) {
      case Decoder::BranchOpcode::kBeq:
        cond_value = arg1 == arg2;
        break;
      case Decoder::BranchOpcode::kBne:
        cond_value = arg1 != arg2;
        break;
      case Decoder::BranchOpcode::kBltu:
        cond_value = arg1 < arg2;
        break;
      case Decoder::BranchOpcode::kBgeu:
        cond_value = arg1 >= arg2;
        break;
      case Decoder::BranchOpcode::kBlt:
        cond_value = bit_cast<int64_t>(arg1) < bit_cast<int64_t>(arg2);
        break;
      case Decoder::BranchOpcode::kBge:
        cond_value = bit_cast<int64_t>(arg1) >= bit_cast<int64_t>(arg2);
        break;
      default:
        return Unimplemented();
    }

    if (cond_value) {
      Branch(offset);
    }
  }

  void Branch(int32_t offset) {
    CHECK(!exception_raised_);
    state_->cpu.insn_addr += offset;
    branch_taken_ = true;
  }

  void BranchRegister(Register base, int16_t offset) {
    CHECK(!exception_raised_);
    state_->cpu.insn_addr = (base + offset) & ~uint64_t{1};
    branch_taken_ = true;
  }

  FpRegister Fmv(FpRegister arg) { return arg; }

  //
  // V extensions.
  //

  using TailProcessing = intrinsics::TailProcessing;
  using InactiveProcessing = intrinsics::InactiveProcessing;

  enum class VectorSelectElementWidth {
    k8bit = 0b000,
    k16bit = 0b001,
    k32bit = 0b010,
    k64bit = 0b011,
    kMaxValue = 0b111,
  };

  enum class VectorRegisterGroupMultiplier {
    k1register = 0b000,
    k2registers = 0b001,
    k4registers = 0b010,
    k8registers = 0b011,
    kEigthOfRegister = 0b101,
    kQuarterOfRegister = 0b110,
    kHalfOfRegister = 0b111,
    kMaxValue = 0b111,
  };

  static constexpr size_t NumberOfRegistersInvolved(VectorRegisterGroupMultiplier vlmul) {
    switch (vlmul) {
      case VectorRegisterGroupMultiplier::k2registers:
        return 2;
      case VectorRegisterGroupMultiplier::k4registers:
        return 4;
      case VectorRegisterGroupMultiplier::k8registers:
        return 8;
      default:
        return 1;
    }
  }

  static constexpr size_t NumRegistersInvolvedForWideOperand(VectorRegisterGroupMultiplier vlmul) {
    switch (vlmul) {
      case VectorRegisterGroupMultiplier::k1register:
        return 2;
      case VectorRegisterGroupMultiplier::k2registers:
        return 4;
      case VectorRegisterGroupMultiplier::k4registers:
        return 8;
      default:
        return 1;
    }
  }

  template <typename VOpArgs, typename... ExtraArgs>
  void OpVector(const VOpArgs& args, ExtraArgs... extra_args) {
    // Note: whole register instructions are not dependent on vtype and are supposed to work even
    // if vill is set!  Handle them before processing other instructions.
    // Note: other tupes of loads and store are not special and would be processed as usual.
    // TODO(khim): Handle vstart properly.
    if constexpr (std::is_same_v<VOpArgs, Decoder::VLoadUnitStrideArgs>) {
      if (args.opcode == Decoder::VLUmOpOpcode::kVlXreXX) {
        if (!IsPowerOf2(args.nf + 1)) {
          return Unimplemented();
        }
        if ((args.dst & args.nf) != 0) {
          return Unimplemented();
        }
        auto [src] = std::tuple{extra_args...};
        __uint128_t* ptr = bit_cast<__uint128_t*>(src);
        for (size_t index = 0; index <= args.nf; index++) {
          state_->cpu.v[args.dst + index] = ptr[index];
        }
        return;
      }
    }

    if constexpr (std::is_same_v<VOpArgs, Decoder::VStoreUnitStrideArgs>) {
      if (args.opcode == Decoder::VSUmOpOpcode::kVsX) {
        if (args.width != Decoder::MemoryDataOperandType::k8bit) {
          return Unimplemented();
        }
        if (!IsPowerOf2(args.nf + 1)) {
          return Unimplemented();
        }
        if ((args.data & args.nf) != 0) {
          return Unimplemented();
        }
        auto [src] = std::tuple{extra_args...};
        __uint128_t* ptr = bit_cast<__uint128_t*>(src);
        for (size_t index = 0; index <= args.nf; index++) {
          ptr[index] = state_->cpu.v[args.data + index];
        }
        return;
      }
    }

    // RISC-V V extensions are using 8bit “opcode extension” vtype Csr to make sure 32bit encoding
    // would be usable.
    //
    // Great care is made to ensure that vector code wouldn't need to change vtype Csr often (e.g.
    // there are special mask instructions which allow one to manipulate on masks without the need
    // to change the CPU mode.
    //
    // Currently we don't have support for multiple CPU mode in Berberis thus we can only handle
    // these instrtuctions in the interpreter.
    //
    // TODO(b/300690740): develop and implement strategy which would allow us to support vector
    // intrinsics not just in the interpreter. Move code from this function to semantics player.
    Register vtype = GetCsr<CsrName::kVtype>();
    if (static_cast<std::make_signed_t<Register>>(vtype) < 0) {
      return Unimplemented();
    }
    if constexpr (std::is_same_v<VOpArgs, Decoder::VLoadIndexedArgs> ||
                  std::is_same_v<VOpArgs, Decoder::VLoadStrideArgs> ||
                  std::is_same_v<VOpArgs, Decoder::VLoadUnitStrideArgs> ||
                  std::is_same_v<VOpArgs, Decoder::VStoreIndexedArgs> ||
                  std::is_same_v<VOpArgs, Decoder::VStoreStrideArgs> ||
                  std::is_same_v<VOpArgs, Decoder::VStoreUnitStrideArgs>) {
      switch (args.width) {
        case Decoder::MemoryDataOperandType::k8bit:
          return OpVector<UInt8>(args, vtype, extra_args...);
        case Decoder::MemoryDataOperandType::k16bit:
          return OpVector<UInt16>(args, vtype, extra_args...);
        case Decoder::MemoryDataOperandType::k32bit:
          return OpVector<UInt32>(args, vtype, extra_args...);
        case Decoder::MemoryDataOperandType::k64bit:
          return OpVector<UInt64>(args, vtype, extra_args...);
        default:
          return Unimplemented();
      }
    } else {
      VectorRegisterGroupMultiplier vlmul = static_cast<VectorRegisterGroupMultiplier>(vtype & 0x7);
      if constexpr (std::is_same_v<VOpArgs, Decoder::VOpFVfArgs> ||
                    std::is_same_v<VOpArgs, Decoder::VOpFVvArgs>) {
        switch (static_cast<VectorSelectElementWidth>((vtype >> 3) & 0b111)) {
          case VectorSelectElementWidth::k16bit:
            if constexpr (sizeof...(extra_args) == 0) {
              return OpVector<intrinsics::Float16>(args, vlmul, vtype);
            } else {
              return Unimplemented();
            }
          case VectorSelectElementWidth::k32bit:
            return OpVector<Float32>(
                args,
                vlmul,
                vtype,
                std::get<0>(intrinsics::UnboxNan<Float32>(bit_cast<Float64>(extra_args)))...);
          case VectorSelectElementWidth::k64bit:
            // Note: if arguments are 64bit floats then we don't need to do any unboxing.
            return OpVector<Float64>(args, vlmul, vtype, bit_cast<Float64>(extra_args)...);
          default:
            return Unimplemented();
        }
      } else {
        switch (static_cast<VectorSelectElementWidth>((vtype >> 3) & 0b111)) {
          case VectorSelectElementWidth::k8bit:
            return OpVector<UInt8>(args, vlmul, vtype, extra_args...);
          case VectorSelectElementWidth::k16bit:
            return OpVector<UInt16>(args, vlmul, vtype, extra_args...);
          case VectorSelectElementWidth::k32bit:
            return OpVector<UInt32>(args, vlmul, vtype, extra_args...);
          case VectorSelectElementWidth::k64bit:
            return OpVector<UInt64>(args, vlmul, vtype, extra_args...);
          default:
            return Unimplemented();
        }
      }
    }
  }

  template <typename ElementType, typename VOpArgs, typename... ExtraArgs>
  void OpVector(const VOpArgs& args, Register vtype, ExtraArgs... extra_args) {
    auto vemul = Decoder::SignExtend<3>(vtype & 0b111);
    vemul -= ((vtype >> 3) & 0b111);        // Divide by SEW.
    vemul +=
        static_cast<std::underlying_type_t<decltype(args.width)>>(args.width);  // Multiply by EEW.
    if (vemul < -3 || vemul > 3) [[unlikely]] {
      return Unimplemented();
    }
    // Note: whole register loads and stores treat args.nf differently, but they are processed
    // separately above anyway, because they also ignore vtype and all the information in it!
    // For other loads and stores affected number of registers (EMUL * NF) should be 8 or less.
    if ((vemul > 0) && ((args.nf + 1) * (1 << vemul) > 8)) {
      return Unimplemented();
    }
    return OpVector<ElementType>(
        args, static_cast<VectorRegisterGroupMultiplier>(vemul & 0b111), vtype, extra_args...);
  }

  template <typename ElementType, typename VOpArgs, typename... ExtraArgs>
  void OpVector(const VOpArgs& args,
                VectorRegisterGroupMultiplier vlmul,
                Register vtype,
                ExtraArgs... extra_args) {
    switch (vlmul) {
      case VectorRegisterGroupMultiplier::k1register:
        return OpVector<ElementType, VectorRegisterGroupMultiplier::k1register>(
            args, vtype, extra_args...);
      case VectorRegisterGroupMultiplier::k2registers:
        return OpVector<ElementType, VectorRegisterGroupMultiplier::k2registers>(
            args, vtype, extra_args...);
      case VectorRegisterGroupMultiplier::k4registers:
        return OpVector<ElementType, VectorRegisterGroupMultiplier::k4registers>(
            args, vtype, extra_args...);
      case VectorRegisterGroupMultiplier::k8registers:
        return OpVector<ElementType, VectorRegisterGroupMultiplier::k8registers>(
            args, vtype, extra_args...);
      case VectorRegisterGroupMultiplier::kEigthOfRegister:
        return OpVector<ElementType, VectorRegisterGroupMultiplier::kEigthOfRegister>(
            args, vtype, extra_args...);
      case VectorRegisterGroupMultiplier::kQuarterOfRegister:
        return OpVector<ElementType, VectorRegisterGroupMultiplier::kQuarterOfRegister>(
            args, vtype, extra_args...);
      case VectorRegisterGroupMultiplier::kHalfOfRegister:
        return OpVector<ElementType, VectorRegisterGroupMultiplier::kHalfOfRegister>(
            args, vtype, extra_args...);
      default:
        return Unimplemented();
    }
  }

  template <typename ElementType,
            VectorRegisterGroupMultiplier vlmul,
            typename VOpArgs,
            typename... ExtraArgs>
  void OpVector(const VOpArgs& args, Register vtype, ExtraArgs... extra_args) {
    if (args.vm) {
      return OpVector<ElementType, vlmul, intrinsics::NoInactiveProcessing{}>(
          args, vtype, extra_args...);
    }
    if (vtype >> 7) {
      return OpVector<ElementType, vlmul, InactiveProcessing::kAgnostic>(
          args, vtype, extra_args...);
    }
    return OpVector<ElementType, vlmul, InactiveProcessing::kUndisturbed>(
        args, vtype, extra_args...);
  }

  template <typename ElementType,
            VectorRegisterGroupMultiplier vlmul,
            auto vma,
            typename VOpArgs,
            typename... ExtraArgs>
  void OpVector(const VOpArgs& args, Register vtype, ExtraArgs... extra_args) {
    if constexpr (std::is_same_v<VOpArgs, Decoder::VLoadIndexedArgs> ||
                  std::is_same_v<VOpArgs, Decoder::VLoadStrideArgs> ||
                  std::is_same_v<VOpArgs, Decoder::VLoadUnitStrideArgs> ||
                  std::is_same_v<VOpArgs, Decoder::VStoreIndexedArgs> ||
                  std::is_same_v<VOpArgs, Decoder::VStoreStrideArgs> ||
                  std::is_same_v<VOpArgs, Decoder::VStoreUnitStrideArgs>) {
      constexpr size_t kRegistersInvolved = NumberOfRegistersInvolved(vlmul);
      // Note: whole register loads and stores treat args.nf differently, but they are processed
      // separately above anyway, because they also ignore vtype and all the information in it!
      switch (args.nf) {
        case 0:
          return OpVector<ElementType, 1, vlmul, vma>(args, vtype, extra_args...);
        case 1:
          if constexpr (kRegistersInvolved > 4) {
            return Unimplemented();
          } else {
            return OpVector<ElementType, 2, vlmul, vma>(args, vtype, extra_args...);
          }
        case 2:
          if constexpr (kRegistersInvolved > 2) {
            return Unimplemented();
          } else {
            return OpVector<ElementType, 3, vlmul, vma>(args, vtype, extra_args...);
          }
        case 3:
          if constexpr (kRegistersInvolved > 2) {
            return Unimplemented();
          } else {
            return OpVector<ElementType, 4, vlmul, vma>(args, vtype, extra_args...);
          }
        case 4:
          if constexpr (kRegistersInvolved > 1) {
            return Unimplemented();
          } else {
            return OpVector<ElementType, 5, vlmul, vma>(args, vtype, extra_args...);
          }
        case 5:
          if constexpr (kRegistersInvolved > 1) {
            return Unimplemented();
          } else {
            return OpVector<ElementType, 6, vlmul, vma>(args, vtype, extra_args...);
          }
        case 6:
          if constexpr (kRegistersInvolved > 1) {
            return Unimplemented();
          } else {
            return OpVector<ElementType, 7, vlmul, vma>(args, vtype, extra_args...);
          }
        case 7:
          if constexpr (kRegistersInvolved > 1) {
            return Unimplemented();
          } else {
            return OpVector<ElementType, 8, vlmul, vma>(args, vtype, extra_args...);
          }
      }
    } else {
      if ((vtype >> 6) & 1) {
        return OpVector<ElementType, vlmul, TailProcessing::kAgnostic, vma>(args, extra_args...);
      }
      return OpVector<ElementType, vlmul, TailProcessing::kUndisturbed, vma>(args, extra_args...);
    }
  }

  template <typename ElementType,
            size_t kSegmentSize,
            VectorRegisterGroupMultiplier vlmul,
            auto vma,
            typename VOpArgs,
            typename... ExtraArgs>
  void OpVector(const VOpArgs& args, Register vtype, ExtraArgs... extra_args) {
    // Indexed loads and stores have two operands with different ElementType's and lmul sizes,
    // pass vtype to do further selection.
    if constexpr (std::is_same_v<VOpArgs, Decoder::VLoadIndexedArgs> ||
                  std::is_same_v<VOpArgs, Decoder::VStoreIndexedArgs>) {
      // Because we know that we are dealing with indexed loads and stores and wouldn't need to
      // convert elmul to anything else we can immediately turn it into kIndexRegistersInvolved
      // here.
      if ((vtype >> 6) & 1) {
        return OpVector<kSegmentSize,
                        ElementType,
                        NumberOfRegistersInvolved(vlmul),
                        TailProcessing::kAgnostic,
                        vma>(args, vtype, extra_args...);
      }
      return OpVector<kSegmentSize,
                      ElementType,
                      NumberOfRegistersInvolved(vlmul),
                      TailProcessing::kUndisturbed,
                      vma>(args, vtype, extra_args...);
    } else {
      // For other instruction we have parsed all the information from vtype and only need to pass
      // args and extra_args.
      if ((vtype >> 6) & 1) {
        return OpVector<ElementType, kSegmentSize, vlmul, TailProcessing::kAgnostic, vma>(
            args, extra_args...);
      }
      return OpVector<ElementType, kSegmentSize, vlmul, TailProcessing::kUndisturbed, vma>(
          args, extra_args...);
    }
  }

  template <size_t kSegmentSize,
            typename IndexElementType,
            size_t kIndexRegistersInvolved,
            TailProcessing vta,
            auto vma,
            typename VOpArgs,
            typename... ExtraArgs>
  void OpVector(const VOpArgs& args, Register vtype, ExtraArgs... extra_args) {
    VectorRegisterGroupMultiplier vlmul = static_cast<VectorRegisterGroupMultiplier>(vtype & 0b111);
    switch (static_cast<VectorSelectElementWidth>((vtype >> 3) & 0b111)) {
      case VectorSelectElementWidth::k8bit:
        return OpVector<UInt8, kSegmentSize, IndexElementType, kIndexRegistersInvolved, vta, vma>(
            args, vlmul, extra_args...);
      case VectorSelectElementWidth::k16bit:
        return OpVector<UInt16, kSegmentSize, IndexElementType, kIndexRegistersInvolved, vta, vma>(
            args, vlmul, extra_args...);
      case VectorSelectElementWidth::k32bit:
        return OpVector<UInt32, kSegmentSize, IndexElementType, kIndexRegistersInvolved, vta, vma>(
            args, vlmul, extra_args...);
      case VectorSelectElementWidth::k64bit:
        return OpVector<UInt64, kSegmentSize, IndexElementType, kIndexRegistersInvolved, vta, vma>(
            args, vlmul, extra_args...);
      default:
        return Unimplemented();
    }
  }

  template <typename DataElementType,
            size_t kSegmentSize,
            typename IndexElementType,
            size_t kIndexRegistersInvolved,
            TailProcessing vta,
            auto vma,
            typename VOpArgs,
            typename... ExtraArgs>
  void OpVector(const VOpArgs& args, VectorRegisterGroupMultiplier vlmul, ExtraArgs... extra_args) {
    switch (vlmul) {
      case VectorRegisterGroupMultiplier::k1register:
        return OpVector<DataElementType,
                        VectorRegisterGroupMultiplier::k1register,
                        IndexElementType,
                        kSegmentSize,
                        kIndexRegistersInvolved,
                        vta,
                        vma>(args, extra_args...);
      case VectorRegisterGroupMultiplier::k2registers:
        return OpVector<DataElementType,
                        VectorRegisterGroupMultiplier::k2registers,
                        IndexElementType,
                        kSegmentSize,
                        kIndexRegistersInvolved,
                        vta,
                        vma>(args, extra_args...);
      case VectorRegisterGroupMultiplier::k4registers:
        return OpVector<DataElementType,
                        VectorRegisterGroupMultiplier::k4registers,
                        IndexElementType,
                        kSegmentSize,
                        kIndexRegistersInvolved,
                        vta,
                        vma>(args, extra_args...);
      case VectorRegisterGroupMultiplier::k8registers:
        return OpVector<DataElementType,
                        VectorRegisterGroupMultiplier::k8registers,
                        IndexElementType,
                        kSegmentSize,
                        kIndexRegistersInvolved,
                        vta,
                        vma>(args, extra_args...);
      case VectorRegisterGroupMultiplier::kEigthOfRegister:
        return OpVector<DataElementType,
                        VectorRegisterGroupMultiplier::kEigthOfRegister,
                        IndexElementType,
                        kSegmentSize,
                        kIndexRegistersInvolved,
                        vta,
                        vma>(args, extra_args...);
      case VectorRegisterGroupMultiplier::kQuarterOfRegister:
        return OpVector<DataElementType,
                        VectorRegisterGroupMultiplier::kQuarterOfRegister,
                        IndexElementType,
                        kSegmentSize,
                        kIndexRegistersInvolved,
                        vta,
                        vma>(args, extra_args...);
      case VectorRegisterGroupMultiplier::kHalfOfRegister:
        return OpVector<DataElementType,
                        VectorRegisterGroupMultiplier::kHalfOfRegister,
                        IndexElementType,
                        kSegmentSize,
                        kIndexRegistersInvolved,
                        vta,
                        vma>(args, extra_args...);
      default:
        return Unimplemented();
    }
  }

  template <typename DataElementType,
            VectorRegisterGroupMultiplier vlmul,
            typename IndexElementType,
            size_t kSegmentSize,
            size_t kIndexRegistersInvolved,
            TailProcessing vta,
            auto vma>
  void OpVector(const Decoder::VLoadIndexedArgs& args, Register src) {
    return OpVector<DataElementType,
                    kSegmentSize,
                    NumberOfRegistersInvolved(vlmul),
                    IndexElementType,
                    kIndexRegistersInvolved,
                    vta,
                    vma>(args, src);
  }

  template <typename DataElementType,
            size_t kSegmentSize,
            size_t kNumRegistersInGroup,
            typename IndexElementType,
            size_t kIndexRegistersInvolved,
            TailProcessing vta,
            auto vma>
  void OpVector(const Decoder::VLoadIndexedArgs& args, Register src) {
    if (!IsAligned<kIndexRegistersInvolved>(args.idx)) {
      return Unimplemented();
    }
    constexpr size_t kElementsCount =
        static_cast<int>(sizeof(SIMD128Register) / sizeof(IndexElementType));
    alignas(alignof(SIMD128Register))
        IndexElementType indexes[kElementsCount * kIndexRegistersInvolved];
    memcpy(indexes, state_->cpu.v + args.idx, sizeof(SIMD128Register) * kIndexRegistersInvolved);
    return OpVectorLoad<DataElementType, kSegmentSize, kNumRegistersInGroup, vta, vma>(
        args.dst, src, [&indexes](size_t index) { return indexes[index]; });
  }

  template <typename ElementType,
            size_t kSegmentSize,
            VectorRegisterGroupMultiplier vlmul,
            TailProcessing vta,
            auto vma>
  void OpVector(const Decoder::VLoadStrideArgs& args, Register src, Register stride) {
    return OpVector<ElementType, kSegmentSize, NumberOfRegistersInvolved(vlmul), vta, vma>(
        args, src, stride);
  }

  template <typename ElementType,
            size_t kSegmentSize,
            size_t kNumRegistersInGroup,
            TailProcessing vta,
            auto vma>
  void OpVector(const Decoder::VLoadStrideArgs& args, Register src, Register stride) {
    return OpVectorLoad<ElementType, kSegmentSize, kNumRegistersInGroup, vta, vma>(
        args.dst, src, [stride](size_t index) { return stride * index; });
  }

  template <typename ElementType,
            size_t kSegmentSize,
            VectorRegisterGroupMultiplier vlmul,
            TailProcessing vta,
            auto vma>
  void OpVector(const Decoder::VLoadUnitStrideArgs& args, Register src) {
    return OpVector<ElementType, kSegmentSize, NumberOfRegistersInvolved(vlmul), vta, vma>(args,
                                                                                           src);
  }

  template <typename ElementType,
            size_t kSegmentSize,
            size_t kNumRegistersInGroup,
            TailProcessing vta,
            auto vma>
  void OpVector(const Decoder::VLoadUnitStrideArgs& args, Register src) {
    switch (args.opcode) {
      case Decoder::VLUmOpOpcode::kVleXXff:
        return OpVectorLoad<ElementType,
                            kSegmentSize,
                            kNumRegistersInGroup,
                            vta,
                            vma,
                            Decoder::VLUmOpOpcode::kVleXXff>(
            args.dst, src, [](size_t index) { return kSegmentSize * sizeof(ElementType) * index; });
      case Decoder::VLUmOpOpcode::kVleXX:
        return OpVectorLoad<ElementType,
                            kSegmentSize,
                            kNumRegistersInGroup,
                            vta,
                            vma,
                            Decoder::VLUmOpOpcode::kVleXX>(
            args.dst, src, [](size_t index) { return kSegmentSize * sizeof(ElementType) * index; });
      case Decoder::VLUmOpOpcode::kVlm:
        if constexpr (kSegmentSize == 1 &&
                      std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>) {
          return OpVectorLoad<UInt8,
                              1,
                              1,
                              TailProcessing::kAgnostic,
                              vma,
                              Decoder::VLUmOpOpcode::kVlm>(
              args.dst, src, [](size_t index) { return index; });
        }
        return Unimplemented();
      default:
        return Unimplemented();
    }
  }

  // The strided version of segmented load sounds like something very convoluted and complicated
  // that no one may ever want to use, but it's not rare and may be illustrated with simple RGB
  // bitmap window.
  //
  // Suppose it's in memory like this (doubles are 8 bytes in size as per IEEE 754)):
  //   {R: 0.01}{G: 0.11}{B: 0.21} {R: 1.01}{G: 1.11}{B: 1.21}, {R: 2.01}{G: 2.11}{B: 2.21}
  //   {R:10.01}{G:10.11}{B:10.21} {R:11.01}{G:11.11}{B:11.21}, {R:12.01}{G:12.11}{B:12.21}
  //   {R:20.01}{G:20.11}{B:20.21} {R:21.01}{G:21.11}{B:21.21}, {R:22.01}{G:22.11}{B:22.21}
  //   {R:30.01}{G:30.11}{B:30.21} {R:31.01}{G:31.11}{B:31.21}, {R:32.01}{G:32.11}{B:32.21}
  // This is very tiny 3x4 image with 3 components: red, green, blue.
  //
  // Let's assume that x1 is loaded with address of first element and x2 with 72 (that's how much
  // one row of this image takes).
  //
  // Then we may use the following command to load values in memory (with LMUL = 2, ELEN = 4):
  //   vlsseg3e64.v v0, (x1), x2
  //
  // They would be loaded like this:
  //   v0: {R: 0.01}{R:10.01} (first group of 2 registers)
  //   v1: {R:20.01}{R:30.01}
  //   v2: {G: 0.11}{G:10.11} (second group of 2 registers)
  //   v3: {G:20.11}{G:30.11}
  //   v4: {B: 0.21}{B:10.21} (third group of 3 registers)
  //   v5: {B:20.21}{B:30.21}
  // Now we have loaded a column from memory and all three colors are put into a different register
  // groups for further processing.
  template <typename ElementType,
            size_t kSegmentSize,
            size_t kNumRegistersInGroup,
            TailProcessing vta,
            auto vma,
            typename Decoder::VLUmOpOpcode opcode = typename Decoder::VLUmOpOpcode{},
            typename GetElementOffsetLambdaType>
  void OpVectorLoad(uint8_t dst, Register src, GetElementOffsetLambdaType GetElementOffset) {
    using MaskType = std::conditional_t<sizeof(ElementType) == sizeof(Int8), UInt16, UInt8>;
    if (!IsAligned<kNumRegistersInGroup>(dst)) {
      return Unimplemented();
    }
    if (dst + kNumRegistersInGroup * kSegmentSize >= 32) {
      return Unimplemented();
    }
    constexpr size_t kElementsCount = static_cast<int>(16 / sizeof(ElementType));
    size_t vstart = GetCsr<CsrName::kVstart>();
    size_t vl = GetCsr<CsrName::kVl>();
    if constexpr (opcode == Decoder::VLUmOpOpcode::kVlm) {
      vl = AlignUp<CHAR_BIT>(vl) / CHAR_BIT;
    }
    // In case of memory access fault we may set vstart to non-zero value, set it to zero here to
    // simplify the logic below.
    SetCsr<CsrName::kVstart>(0);
    // When vstart >= vl, there are no body elements, and no elements are updated in any destination
    // vector register group, including that no tail elements are updated with agnostic values.
    if (vstart >= vl) [[unlikely]] {
      return;
    }
    if constexpr (vta == TailProcessing::kAgnostic) {
      vstart = std::min(vstart, vl);
    }
    // Note: within_group_id is the current register id within a register group. During one
    // iteration of this loop we compute results for all registers with the current id in all
    // groups. E.g. for the example above we'd compute v0, v2, v4 during the first iteration (id
    // within group = 0), and v1, v3, v5 during the second iteration (id within group = 1). This
    // ensures that memory is always accessed in ordered fashion.
    std::array<SIMD128Register, kSegmentSize> result;
    char* ptr = ToHostAddr<char>(src);
    auto mask = GetMaskForVectorOperations<vma>();
    for (size_t within_group_id = vstart / kElementsCount; within_group_id < kNumRegistersInGroup;
         ++within_group_id) {
      // No need to continue if we have kUndisturbed vta strategy.
      if constexpr (vta == TailProcessing::kUndisturbed) {
        if (within_group_id * kElementsCount >= vl) {
          break;
        }
      }
      // If we have elements that won't be overwritten then load these from registers.
      // For interpreter we could have filled all the registers unconditionally but we'll want to
      // reuse this code JITs later.
      auto register_mask =
          std::get<0>(intrinsics::MaskForRegisterInSequence<ElementType>(mask, within_group_id));
      auto full_mask = std::get<0>(intrinsics::FullMaskForRegister<ElementType>(mask));
      if (vstart ||
          (vl < (within_group_id + 1) * kElementsCount && vta == TailProcessing::kUndisturbed) ||
          !(std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing> ||
            static_cast<InactiveProcessing>(vma) != InactiveProcessing::kUndisturbed ||
            register_mask == full_mask)) {
        for (size_t field = 0; field < kSegmentSize; ++field) {
          result[field].Set(state_->cpu.v[dst + within_group_id + field * kNumRegistersInGroup]);
        }
      }
      // Read elements from memory, but only if there are any active ones.
      for (size_t within_register_id = vstart % kElementsCount; within_register_id < kElementsCount;
           ++within_register_id) {
        size_t element_index = kElementsCount * within_group_id + within_register_id;
        // Stop if we reached the vl limit.
        if (vl <= element_index) {
          break;
        }
        // Don't touch masked-out elements.
        if constexpr (!std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>) {
          if ((MaskType(register_mask) & MaskType{static_cast<typename MaskType::BaseType>(
                                             1 << within_register_id)}) == MaskType{0}) {
            continue;
          }
        }
        // Load segment from memory.
        for (size_t field = 0; field < kSegmentSize; ++field) {
          FaultyLoadResult mem_access_result =
              FaultyLoad(ptr + field * sizeof(ElementType) + GetElementOffset(element_index),
                         sizeof(ElementType));
          if (mem_access_result.is_fault) {
            // Documentation doesn't tell us what we are supposed to do to remaining elements when
            // access fault happens but let's trigger an exception and treat the remaining elements
            // using vta-specified strategy by simply just adjusting the vl.
            vl = element_index;
            if constexpr (opcode == Decoder::VLUmOpOpcode::kVleXXff) {
              // Fail-first load only triggers exceptions for the first element, otherwise it
              // changes vl to ensure that other operations would only process elements that are
              // successfully loaded.
              if (element_index == 0) [[unlikely]] {
                exception_raised_ = true;
              } else {
                // TODO(b/323994286): Write a test case to verify vl changes correctly.
                SetCsr<CsrName::kVl>(element_index);
              }
            } else {
              // Most load instructions set vstart to failing element which then may be processed
              // by exception handler.
              exception_raised_ = true;
              SetCsr<CsrName::kVstart>(element_index);
            }
            break;
          }
          result[field].template Set<ElementType>(static_cast<ElementType>(mem_access_result.value),
                                                  within_register_id);
        }
      }
      // Lambda to generate tail mask. We don't want to call MakeBitmaskFromVl eagerly because it's
      // not needed, most of the time, and compiler couldn't eliminate access to mmap-backed memory.
      auto GetTailMask = [vl, within_group_id] {
        return std::get<0>(intrinsics::MakeBitmaskFromVl<ElementType>(
            (vl <= within_group_id * kElementsCount) ? 0 : vl - within_group_id * kElementsCount));
      };
      // If mask has inactive elements and InactiveProcessing::kAgnostic mode is used then set them
      // to ~0.
      if constexpr (!std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>) {
        if (register_mask != full_mask) {
          auto [simd_mask] =
              intrinsics::BitMaskToSimdMaskForTests<ElementType>(Int64{MaskType{register_mask}});
          for (size_t field = 0; field < kSegmentSize; ++field) {
            if constexpr (vma == InactiveProcessing::kAgnostic) {
              // vstart equal to zero is supposed to be exceptional. From RISV-V V manual (page 14):
              // The vstart CSR is writable by unprivileged code, but non-zero vstart values may
              // cause vector instructions to run substantially slower on some implementations, so
              // vstart should not be used by application programmers. A few vector instructions
              // cannot be executed with a non-zero vstart value and will raise an illegal
              // instruction exception as dened below.
              // TODO(b/300690740): decide whether to merge two cases after support for vectors in
              // heavy optimizer would be implemented.
              if (vstart) [[unlikely]] {
                SIMD128Register vstart_mask = std::get<0>(
                    intrinsics::MakeBitmaskFromVl<ElementType>(vstart % kElementsCount));
                if constexpr (vta == TailProcessing::kAgnostic) {
                  result[field] |= vstart_mask & ~simd_mask;
                } else if (vl < (within_group_id + 1) * kElementsCount) {
                  result[field] |= vstart_mask & ~simd_mask & ~GetTailMask();
                } else {
                  result[field] |= vstart_mask & ~simd_mask;
                }
              } else if constexpr (vta == TailProcessing::kAgnostic) {
                result[field] |= ~simd_mask;
              } else {
                if (vl < (within_group_id + 1) * kElementsCount) {
                  result[field] |= ~simd_mask & ~GetTailMask();
                } else {
                  result[field] |= ~simd_mask;
                }
              }
            }
          }
        }
      }
      // If we have tail elements and TailProcessing::kAgnostic mode then set them to ~0.
      if constexpr (vta == TailProcessing::kAgnostic) {
        for (size_t field = 0; field < kSegmentSize; ++field) {
          if (vl < (within_group_id + 1) * kElementsCount) {
            result[field] |= GetTailMask();
          }
        }
      }
      // Put values back into register file.
      for (size_t field = 0; field < kSegmentSize; ++field) {
        state_->cpu.v[dst + within_group_id + field * kNumRegistersInGroup] =
            result[field].template Get<__uint128_t>();
      }
      // Next group should be fully processed.
      vstart = 0;
    }
  }

  template <typename ElementType, VectorRegisterGroupMultiplier vlmul, TailProcessing vta, auto vma>
  void OpVector(const Decoder::VOpFVfArgs& args, ElementType arg2) {
    switch (args.opcode) {
      case Decoder::VOpFVfOpcode::kVfmvsf:
        if constexpr (!std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>) {
          return Unimplemented();
        }
        if (args.src1 != 0) {
          return Unimplemented();
        }
        return OpVectorVmvsx<ElementType, vta>(args.dst, arg2);
      case Decoder::VOpFVfOpcode::kVfmergevf:
        if constexpr (std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>) {
          if (args.src1 != 0) {
            return Unimplemented();
          }
          return OpVectorx<intrinsics::Vcopyx<ElementType>, ElementType, vlmul, vta, vma>(args.dst,
                                                                                          arg2);
        } else {
          return OpVectorx<intrinsics::Vcopyx<ElementType>,
                           ElementType,
                           vlmul,
                           vta,
                           // Always use "undisturbed" value from source register.
                           InactiveProcessing::kUndisturbed>(
              args.dst, arg2, /*dst_mask=*/args.src1);
        }
      case Decoder::VOpFVfOpcode::kVfmaxvf:
        return OpVectorvx<intrinsics::Vfmaxvx<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, arg2);
      case Decoder::VOpFVfOpcode::kVfminvf:
        return OpVectorvx<intrinsics::Vfminvx<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, arg2);
      default:
        return Unimplemented();
    }
  }

  template <typename ElementType, VectorRegisterGroupMultiplier vlmul, TailProcessing vta, auto vma>
  void OpVector(const Decoder::VOpFVvArgs& args) {
    using SignedType = std::make_signed_t<typename TypeTraits<ElementType>::Int>;
    using UnsignedType = std::make_unsigned_t<typename TypeTraits<ElementType>::Int>;
    // We currently don't support Float16 operations, but conversion routines that deal with
    // double-width floats use these encodings to produce regular Float32 types.
    if constexpr (sizeof(ElementType) <= sizeof(Float32)) {
      using WideElementType = typename TypeTraits<ElementType>::Wide;
      switch (args.opcode) {
        case Decoder::VOpFVvOpcode::kVFUnary0:
          switch (args.vfunary0_opcode) {
            case Decoder::VFUnary0Opcode::kVfwcvtfxuv:
              return OpVectorWidenvr<[](int8_t frm, SIMD128Register src) {
                return intrinsics::Vfcvtv<WideElementType, UnsignedType>(FPFlags::DYN, frm, src);
              },
                                     WideElementType,
                                     UnsignedType,
                                     vlmul,
                                     vta,
                                     vma>(args.dst, args.src1);
            case Decoder::VFUnary0Opcode::kVfwcvtfxv:
              return OpVectorWidenvr<[](int8_t frm, SIMD128Register src) {
                return intrinsics::Vfcvtv<WideElementType, SignedType>(FPFlags::DYN, frm, src);
              },
                                     WideElementType,
                                     SignedType,
                                     vlmul,
                                     vta,
                                     vma>(args.dst, args.src1);
            case Decoder::VFUnary0Opcode::kVfncvtxufw:
              return OpVectorNarrowwr<[](int8_t frm, SIMD128Register src) {
                return intrinsics::Vfcvtv<UnsignedType, WideElementType>(FPFlags::DYN, frm, src);
              },
                                      UnsignedType,
                                      vlmul,
                                      vta,
                                      vma>(args.dst, args.src1);
            case Decoder::VFUnary0Opcode::kVfncvtxfw:
              return OpVectorNarrowwr<[](int8_t frm, SIMD128Register src) {
                return intrinsics::Vfcvtv<SignedType, WideElementType>(FPFlags::DYN, frm, src);
              },
                                      SignedType,
                                      vlmul,
                                      vta,
                                      vma>(args.dst, args.src1);
            case Decoder::VFUnary0Opcode::kVfncvtrtzxufw:
              return OpVectorNarrowwr<[](int8_t frm, SIMD128Register src) {
                return intrinsics::Vfcvtv<UnsignedType, WideElementType>(FPFlags::RTZ, frm, src);
              },
                                      UnsignedType,
                                      vlmul,
                                      vta,
                                      vma>(args.dst, args.src1);
            case Decoder::VFUnary0Opcode::kVfncvtrtzxfw:
              return OpVectorNarrowwr<[](int8_t frm, SIMD128Register src) {
                return intrinsics::Vfcvtv<SignedType, WideElementType>(FPFlags::RTZ, frm, src);
              },
                                      SignedType,
                                      vlmul,
                                      vta,
                                      vma>(args.dst, args.src1);
            default:
              break;  // Make compiler happy.
          }
          break;
        default:
          break;  // Make compiler happy.
      }
    }
    // Widening and narrowing opeation which take floating point “narrow” operand may only work
    // correctly with Float32 input: Float16 is not supported yet, while Float64 input would produce
    // 128bit output which is currently reserver in RISC-V V.
    if constexpr (sizeof(ElementType) == sizeof(Float32)) {
      using WideElementType = typename TypeTraits<ElementType>::Wide;
      using WideSignedType = typename TypeTraits<SignedType>::Wide;
      using WideUnsignedType = typename TypeTraits<UnsignedType>::Wide;
      switch (args.opcode) {
        case Decoder::VOpFVvOpcode::kVFUnary0:
          switch (args.vfunary0_opcode) {
            case Decoder::VFUnary0Opcode::kVfwcvtxufv:
              return OpVectorWidenvr<[](int8_t frm, SIMD128Register src) {
                return intrinsics::Vfcvtv<WideUnsignedType, ElementType>(FPFlags::DYN, frm, src);
              },
                                     WideUnsignedType,
                                     ElementType,
                                     vlmul,
                                     vta,
                                     vma>(args.dst, args.src1);
            case Decoder::VFUnary0Opcode::kVfwcvtxfv:
              return OpVectorWidenvr<[](int8_t frm, SIMD128Register src) {
                return intrinsics::Vfcvtv<WideSignedType, ElementType>(FPFlags::DYN, frm, src);
              },
                                     WideSignedType,
                                     ElementType,
                                     vlmul,
                                     vta,
                                     vma>(args.dst, args.src1);
            case Decoder::VFUnary0Opcode::kVfwcvtffv:
              return OpVectorWidenvr<[](int8_t frm, SIMD128Register src) {
                return intrinsics::Vfcvtv<WideElementType, ElementType>(FPFlags::DYN, frm, src);
              },
                                     WideElementType,
                                     ElementType,
                                     vlmul,
                                     vta,
                                     vma>(args.dst, args.src1);
            case Decoder::VFUnary0Opcode::kVfwcvtrtzxufv:
              return OpVectorWidenvr<[](int8_t frm, SIMD128Register src) {
                return intrinsics::Vfcvtv<WideUnsignedType, ElementType>(FPFlags::RTZ, frm, src);
              },
                                     WideUnsignedType,
                                     ElementType,
                                     vlmul,
                                     vta,
                                     vma>(args.dst, args.src1);
            case Decoder::VFUnary0Opcode::kVfwcvtrtzxfv:
              return OpVectorWidenvr<[](int8_t frm, SIMD128Register src) {
                return intrinsics::Vfcvtv<WideSignedType, ElementType>(FPFlags::RTZ, frm, src);
              },
                                     WideSignedType,
                                     ElementType,
                                     vlmul,
                                     vta,
                                     vma>(args.dst, args.src1);
            case Decoder::VFUnary0Opcode::kVfncvtfxuw:
              return OpVectorNarrowwr<[](int8_t frm, SIMD128Register src) {
                return intrinsics::Vfcvtv<ElementType, WideUnsignedType>(FPFlags::DYN, frm, src);
              },
                                      ElementType,
                                      vlmul,
                                      vta,
                                      vma>(args.dst, args.src1);
            case Decoder::VFUnary0Opcode::kVfncvtffw:
              return OpVectorNarrowwr<[](int8_t frm, SIMD128Register src) {
                return intrinsics::Vfcvtv<ElementType, WideElementType>(FPFlags::DYN, frm, src);
              },
                                      ElementType,
                                      vlmul,
                                      vta,
                                      vma>(args.dst, args.src1);
            case Decoder::VFUnary0Opcode::kVfncvtfxw:
              return OpVectorNarrowwr<[](int8_t frm, SIMD128Register src) {
                return intrinsics::Vfcvtv<ElementType, WideSignedType>(FPFlags::DYN, frm, src);
              },
                                      ElementType,
                                      vlmul,
                                      vta,
                                      vma>(args.dst, args.src1);
            default:
              break;  // Make compiler happy.
          }
          break;
        default:
          break;  // Make compiler happy.
      }
    }
    // If our ElementType is Float16 then “straight” operations are unsupported and we whouldn't try
    // instantiate any functions since this would lead to compilke-time error.
    if constexpr (sizeof(ElementType) >= sizeof(Float32)) {
      switch (args.opcode) {
        case Decoder::VOpFVvOpcode::kVfmvfs:
          if constexpr (!std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>) {
            return Unimplemented();
          }
          if (args.src2 != 0) {
            return Unimplemented();
          }
          return OpVectorVmvfs<ElementType>(args.dst, args.src1);
        case Decoder::VOpFVvOpcode::kVFUnary0:
          switch (args.vfunary0_opcode) {
            case Decoder::VFUnary0Opcode::kVfcvtxufv:
              return OpVectorvr<[](int8_t frm, SIMD128Register src) {
                return intrinsics::Vfcvtv<UnsignedType, ElementType>(FPFlags::DYN, frm, src);
              },
                                ElementType,
                                vlmul,
                                vta,
                                vma>(args.dst, args.src1);
            case Decoder::VFUnary0Opcode::kVfcvtxfv:
              return OpVectorvr<[](int8_t frm, SIMD128Register src) {
                return intrinsics::Vfcvtv<SignedType, ElementType>(FPFlags::DYN, frm, src);
              },
                                ElementType,
                                vlmul,
                                vta,
                                vma>(args.dst, args.src1);
            case Decoder::VFUnary0Opcode::kVfcvtfxuv:
              return OpVectorvr<[](int8_t frm, SIMD128Register src) {
                return intrinsics::Vfcvtv<ElementType, UnsignedType>(FPFlags::DYN, frm, src);
              },
                                UnsignedType,
                                vlmul,
                                vta,
                                vma>(args.dst, args.src1);
            case Decoder::VFUnary0Opcode::kVfcvtfxv:
              return OpVectorvr<[](int8_t frm, SIMD128Register src) {
                return intrinsics::Vfcvtv<ElementType, SignedType>(FPFlags::DYN, frm, src);
              },
                                SignedType,
                                vlmul,
                                vta,
                                vma>(args.dst, args.src1);
            case Decoder::VFUnary0Opcode::kVfcvtrtzxufv:
              return OpVectorvr<[](int8_t frm, SIMD128Register src) {
                return intrinsics::Vfcvtv<UnsignedType, ElementType>(FPFlags::RTZ, frm, src);
              },
                                ElementType,
                                vlmul,
                                vta,
                                vma>(args.dst, args.src1);
            case Decoder::VFUnary0Opcode::kVfcvtrtzxfv:
              return OpVectorvr<[](int8_t frm, SIMD128Register src) {
                return intrinsics::Vfcvtv<SignedType, ElementType>(FPFlags::RTZ, frm, src);
              },
                                ElementType,
                                vlmul,
                                vta,
                                vma>(args.dst, args.src1);
            default:
              break;  // Make compiler happy.
          }
          break;
        default:
          break;  // Make compiler happy.
      }
    }
    return Unimplemented();
  }

  template <typename ElementType, VectorRegisterGroupMultiplier vlmul, TailProcessing vta, auto vma>
  void OpVector(const Decoder::VOpIViArgs& args) {
    using SignedType = berberis::SignedType<ElementType>;
    using UnsignedType = berberis::UnsignedType<ElementType>;
    switch (args.opcode) {
      case Decoder::VOpIViOpcode::kVaddvi:
        return OpVectorvx<intrinsics::Vaddvx<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src, BitCastToUnsigned(SignedType{args.imm}));
      case Decoder::VOpIViOpcode::kVrsubvi:
        return OpVectorvx<intrinsics::Vrsubvx<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src, BitCastToUnsigned(SignedType{args.imm}));
      case Decoder::VOpIViOpcode::kVandvi:
        return OpVectorvx<intrinsics::Vandvx<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src, BitCastToUnsigned(SignedType{args.imm}));
      case Decoder::VOpIViOpcode::kVorvi:
        return OpVectorvx<intrinsics::Vorvx<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src, BitCastToUnsigned(SignedType{args.imm}));
      case Decoder::VOpIViOpcode::kVxorvi:
        return OpVectorvx<intrinsics::Vxorvx<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src, BitCastToUnsigned(SignedType{args.imm}));
      case Decoder::VOpIViOpcode::kVmseqvi:
        return OpVectormvx<intrinsics::Vseqvx<ElementType>, ElementType, vlmul, vma>(
            args.dst, args.src, BitCastToUnsigned(SignedType{args.imm}));
      case Decoder::VOpIViOpcode::kVmsnevi:
        return OpVectormvx<intrinsics::Vsnevx<ElementType>, ElementType, vlmul, vma>(
            args.dst, args.src, BitCastToUnsigned(SignedType{args.imm}));
      case Decoder::VOpIViOpcode::kVmsleuvi:
        return OpVectormvx<intrinsics::Vslevx<UnsignedType>, UnsignedType, vlmul, vma>(
            args.dst, args.src, BitCastToUnsigned(SignedType{args.imm}));
      case Decoder::VOpIViOpcode::kVmslevi:
        return OpVectormvx<intrinsics::Vslevx<SignedType>, SignedType, vlmul, vma>(
            args.dst, args.src, SignedType{args.imm});
      case Decoder::VOpIViOpcode::kVmsgtuvi:
        return OpVectormvx<intrinsics::Vsgtvx<UnsignedType>, UnsignedType, vlmul, vma>(
            args.dst, args.src, BitCastToUnsigned(SignedType{args.imm}));
      case Decoder::VOpIViOpcode::kVmsgtvi:
        return OpVectormvx<intrinsics::Vsgtvx<SignedType>, SignedType, vlmul, vma>(
            args.dst, args.src, SignedType{args.imm});
      case Decoder::VOpIViOpcode::kVsllvi:
        return OpVectorvx<intrinsics::Vslvx<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src, BitCastToUnsigned(SignedType{args.imm}));
      case Decoder::VOpIViOpcode::kVsrlvi:
        return OpVectorvx<intrinsics::Vsrvx<UnsignedType>, UnsignedType, vlmul, vta, vma>(
            args.dst, args.src, BitCastToUnsigned(SignedType{args.imm}));
      case Decoder::VOpIViOpcode::kVsravi:
        return OpVectorvx<intrinsics::Vsrvx<SignedType>, SignedType, vlmul, vta, vma>(
            args.dst, args.src, SignedType{args.imm});
      case Decoder::VOpIViOpcode::kVmergevi:
        if constexpr (std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>) {
          if (args.src != 0) {
            return Unimplemented();
          }
          return OpVectorx<intrinsics::Vcopyx<ElementType>, ElementType, vlmul, vta, vma>(
              args.dst, BitCastToUnsigned(SignedType{args.imm}));
        } else {
          return OpVectorx<intrinsics::Vcopyx<ElementType>,
                           ElementType,
                           vlmul,
                           vta,
                           // Always use "undisturbed" value from source register.
                           InactiveProcessing::kUndisturbed>(
              args.dst, BitCastToUnsigned(SignedType{args.imm}), /*dst_mask=*/args.src);
        }
      case Decoder::VOpIViOpcode::kVmvXrv:
        // kVmv<nr>rv instruction
        if constexpr (std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>) {
          switch (args.imm) {
            case 0:
              return OpVectorVmvXrv<ElementType, 1>(args.dst, args.src);
            case 1:
              return OpVectorVmvXrv<ElementType, 2>(args.dst, args.src);
            case 3:
              return OpVectorVmvXrv<ElementType, 4>(args.dst, args.src);
            case 7:
              return OpVectorVmvXrv<ElementType, 8>(args.dst, args.src);
            default:
              return Unimplemented();
          }
        } else {
          return Unimplemented();
        }
      case Decoder::VOpIViOpcode::kVnsrawi:
        return OpVectorNarrowwx<intrinsics::Vnsrwx<SignedType>, SignedType, vlmul, vta, vma>(
            args.dst, args.src, SignedType{args.imm});
      case Decoder::VOpIViOpcode::kVnsrlwi:
        return OpVectorNarrowwx<intrinsics::Vnsrwx<UnsignedType>, UnsignedType, vlmul, vta, vma>(
            args.dst, args.src, BitCastToUnsigned(SignedType{args.imm}));
      case Decoder::VOpIViOpcode::kVslideupvi:
        return OpVectorslideup<ElementType, vlmul, vta, vma>(
            args.dst, args.src, BitCastToUnsigned(SignedType{args.imm}));
      case Decoder::VOpIViOpcode::kVslidedownvi:
        return OpVectorslidedown<ElementType, vlmul, vta, vma>(
            args.dst, args.src, BitCastToUnsigned(SignedType{args.imm}));
      default:
        Unimplemented();
    }
  }

  template <typename ElementType, VectorRegisterGroupMultiplier vlmul, TailProcessing vta, auto vma>
  void OpVector(const Decoder::VOpIVvArgs& args) {
    using SignedType = berberis::SignedType<ElementType>;
    using UnsignedType = berberis::UnsignedType<ElementType>;
    switch (args.opcode) {
      case Decoder::VOpIVvOpcode::kVaddvv:
        return OpVectorvv<intrinsics::Vaddvv<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpIVvOpcode::kVsubvv:
        return OpVectorvv<intrinsics::Vsubvv<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpIVvOpcode::kVandvv:
        return OpVectorvv<intrinsics::Vandvv<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpIVvOpcode::kVorvv:
        return OpVectorvv<intrinsics::Vorvv<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpIVvOpcode::kVxorvv:
        return OpVectorvv<intrinsics::Vxorvv<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpIVvOpcode::kVmseqvv:
        return OpVectormvv<intrinsics::Vseqvv<ElementType>, ElementType, vlmul, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpIVvOpcode::kVmsnevv:
        return OpVectormvv<intrinsics::Vsnevv<ElementType>, ElementType, vlmul, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpIVvOpcode::kVmsltuvv:
        return OpVectormvv<intrinsics::Vsltvv<UnsignedType>, ElementType, vlmul, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpIVvOpcode::kVmsltvv:
        return OpVectormvv<intrinsics::Vsltvv<SignedType>, ElementType, vlmul, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpIVvOpcode::kVmsleuvv:
        return OpVectormvv<intrinsics::Vslevv<UnsignedType>, ElementType, vlmul, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpIVvOpcode::kVmslevv:
        return OpVectormvv<intrinsics::Vslevv<SignedType>, ElementType, vlmul, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpIVvOpcode::kVsllvv:
        return OpVectorvv<intrinsics::Vslvv<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpIVvOpcode::kVsrlvv:
        return OpVectorvv<intrinsics::Vsrvv<UnsignedType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpIVvOpcode::kVsravv:
        return OpVectorvv<intrinsics::Vsrvv<SignedType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpIVvOpcode::kVminuvv:
        return OpVectorvv<intrinsics::Vminvv<UnsignedType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpIVvOpcode::kVminvv:
        return OpVectorvv<intrinsics::Vminvv<SignedType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpIVvOpcode::kVmaxuvv:
        return OpVectorvv<intrinsics::Vmaxvv<UnsignedType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpIVvOpcode::kVmaxvv:
        return OpVectorvv<intrinsics::Vmaxvv<SignedType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpIVvOpcode::kVmergevv:
        if constexpr (std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>) {
          if (args.src1 != 0) {
            return Unimplemented();
          }
          return OpVectorv<intrinsics::Vcopyv<ElementType>, ElementType, vlmul, vta, vma>(
              args.dst, args.src2);
        } else {
          return OpVectorv<intrinsics::Vcopyv<ElementType>,
                           ElementType,
                           vlmul,
                           vta,
                           // Always use "undisturbed" value from source register.
                           InactiveProcessing::kUndisturbed>(
              args.dst, args.src2, /*dst_mask=*/args.src1);
        }
      case Decoder::VOpIVvOpcode::kVnsrawv:
        return OpVectorNarrowwv<intrinsics::Vnsrwv<SignedType>, SignedType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpIVvOpcode::kVnsrlwv:
        return OpVectorNarrowwv<intrinsics::Vnsrwv<UnsignedType>, UnsignedType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      default:
        Unimplemented();
    }
  }

  template <typename ElementType, VectorRegisterGroupMultiplier vlmul, TailProcessing vta, auto vma>
  void OpVector(const Decoder::VOpMVvArgs& args) {
    using SignedType = berberis::SignedType<ElementType>;
    using UnsignedType = berberis::UnsignedType<ElementType>;
    if constexpr (std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>) {
      switch (args.opcode) {
        case Decoder::VOpMVvOpcode::kVmandnmm:
          return OpVectormm<[](SIMD128Register lhs, SIMD128Register rhs) { return lhs & ~rhs; }>(
              args.dst, args.src1, args.src2);
        case Decoder::VOpMVvOpcode::kVmandmm:
          return OpVectormm<[](SIMD128Register lhs, SIMD128Register rhs) { return lhs & rhs; }>(
              args.dst, args.src1, args.src2);
        case Decoder::VOpMVvOpcode::kVmormm:
          return OpVectormm<[](SIMD128Register lhs, SIMD128Register rhs) { return lhs | rhs; }>(
              args.dst, args.src1, args.src2);
        case Decoder::VOpMVvOpcode::kVmxormm:
          return OpVectormm<[](SIMD128Register lhs, SIMD128Register rhs) { return lhs ^ rhs; }>(
              args.dst, args.src1, args.src2);
        case Decoder::VOpMVvOpcode::kVmornmm:
          return OpVectormm<[](SIMD128Register lhs, SIMD128Register rhs) { return lhs | ~rhs; }>(
              args.dst, args.src1, args.src2);
        case Decoder::VOpMVvOpcode::kVmnandmm:
          return OpVectormm<[](SIMD128Register lhs, SIMD128Register rhs) { return ~(lhs & rhs); }>(
              args.dst, args.src1, args.src2);
        case Decoder::VOpMVvOpcode::kVmnormm:
          return OpVectormm<[](SIMD128Register lhs, SIMD128Register rhs) { return ~(lhs | rhs); }>(
              args.dst, args.src1, args.src2);
        case Decoder::VOpMVvOpcode::kVmxnormm:
          return OpVectormm<[](SIMD128Register lhs, SIMD128Register rhs) { return ~(lhs ^ rhs); }>(
              args.dst, args.src1, args.src2);
        default:;  // Do nothing: handled in next switch.
      }
    }
    switch (args.opcode) {
      case Decoder::VOpMVvOpcode::kVredsumvs:
        return OpVectorvs<intrinsics::Vredsumvs<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpMVvOpcode::kVredandvs:
        return OpVectorvs<intrinsics::Vredandvs<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpMVvOpcode::kVredorvs:
        return OpVectorvs<intrinsics::Vredorvs<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpMVvOpcode::kVredxorvs:
        return OpVectorvs<intrinsics::Vredxorvs<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpMVvOpcode::kVredminuvs:
        return OpVectorvs<intrinsics::Vredminvs<UnsignedType>, UnsignedType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpMVvOpcode::kVredminvs:
        return OpVectorvs<intrinsics::Vredminvs<SignedType>, SignedType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpMVvOpcode::kVredmaxuvs:
        return OpVectorvs<intrinsics::Vredmaxvs<UnsignedType>, UnsignedType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpMVvOpcode::kVredmaxvs:
        return OpVectorvs<intrinsics::Vredmaxvs<SignedType>, SignedType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpMVvOpcode::kVWXUnary0:
        switch (args.vwxunary0_opcode) {
          case Decoder::VWXUnary0Opcode::kVmvxs:
            if constexpr (!std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>) {
              return Unimplemented();
            }
            return OpVectorVmvxs<SignedType>(args.dst, args.src1);
          case Decoder::VWXUnary0Opcode::kVcpopm:
            return OpVectorVWXUnary0<intrinsics::Vcpopm<Int128>, vma>(args.dst, args.src1);
          case Decoder::VWXUnary0Opcode::kVfirstm:
            return OpVectorVWXUnary0<intrinsics::Vfirstm<Int128>, vma>(args.dst, args.src1);
          default:
            return Unimplemented();
        }
      case Decoder::VOpMVvOpcode::kVFUnary0:
        switch (args.vxunary0_opcode) {
          case Decoder::VXUnary0Opcode::kVzextvf2m:
            if constexpr (sizeof(UnsignedType) >= 2) {
              return OpVectorVXUnary0<intrinsics::Vextf2<UnsignedType>,
                                      UnsignedType,
                                      2,
                                      vlmul,
                                      vta,
                                      vma>(args.dst, args.src1);
            }
            break;
          case Decoder::VXUnary0Opcode::kVsextvf2m:
            if constexpr (sizeof(SignedType) >= 2) {
              return OpVectorVXUnary0<intrinsics::Vextf2<SignedType>,
                                      SignedType,
                                      2,
                                      vlmul,
                                      vta,
                                      vma>(args.dst, args.src1);
            }
            break;
          case Decoder::VXUnary0Opcode::kVzextvf4m:
            if constexpr (sizeof(UnsignedType) >= 4) {
              return OpVectorVXUnary0<intrinsics::Vextf4<UnsignedType>,
                                      UnsignedType,
                                      4,
                                      vlmul,
                                      vta,
                                      vma>(args.dst, args.src1);
            }
            break;
          case Decoder::VXUnary0Opcode::kVsextvf4m:
            if constexpr (sizeof(SignedType) >= 4) {
              return OpVectorVXUnary0<intrinsics::Vextf4<SignedType>,
                                      SignedType,
                                      4,
                                      vlmul,
                                      vta,
                                      vma>(args.dst, args.src1);
            }
            break;
          case Decoder::VXUnary0Opcode::kVzextvf8m:
            if constexpr (sizeof(UnsignedType) >= 8) {
              return OpVectorVXUnary0<intrinsics::Vextf8<UnsignedType>,
                                      UnsignedType,
                                      8,
                                      vlmul,
                                      vta,
                                      vma>(args.dst, args.src1);
            }
            break;
          case Decoder::VXUnary0Opcode::kVsextvf8m:
            if constexpr (sizeof(SignedType) >= 8) {
              return OpVectorVXUnary0<intrinsics::Vextf8<SignedType>,
                                      SignedType,
                                      8,
                                      vlmul,
                                      vta,
                                      vma>(args.dst, args.src1);
            }
            break;
          default:
            return Unimplemented();
        }
        return Unimplemented();
      case Decoder::VOpMVvOpcode::kVMUnary0:
        switch (args.vmunary0_opcode) {
          case Decoder::VMUnary0Opcode::kVmsbfm:
            return OpVectorVMUnary0<intrinsics::Vmsbfm<>, vma>(args.dst, args.src1);
          case Decoder::VMUnary0Opcode::kVmsofm:
            return OpVectorVMUnary0<intrinsics::Vmsofm<>, vma>(args.dst, args.src1);
          case Decoder::VMUnary0Opcode::kVmsifm:
            return OpVectorVMUnary0<intrinsics::Vmsifm<>, vma>(args.dst, args.src1);
          case Decoder::VMUnary0Opcode::kVidv:
            if (args.src1) {
              return Unimplemented();
            }
            return OpVectorVidv<ElementType, vlmul, vta, vma>(args.dst);
          default:
            return Unimplemented();
        }
      case Decoder::VOpMVvOpcode::kVmaddvv:
        return OpVectorvvv<intrinsics::Vmaddvv<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpMVvOpcode::kVnmsubvv:
        return OpVectorvvv<intrinsics::Vnmsubvv<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpMVvOpcode::kVmaccvv:
        return OpVectorvvv<intrinsics::Vmaccvv<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpMVvOpcode::kVnmsacvv:
        return OpVectorvvv<intrinsics::Vnmsacvv<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpMVvOpcode::kVmulhuvv:
        return OpVectorvv<intrinsics::Vmulhvv<UnsignedType>, UnsignedType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpMVvOpcode::kVmulvv:
        return OpVectorvv<intrinsics::Vmulvv<SignedType>, SignedType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpMVvOpcode::kVmulhsuvv:
        return OpVectorvv<intrinsics::Vmulhsuvv<SignedType>, SignedType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpMVvOpcode::kVmulhvv:
        return OpVectorvv<intrinsics::Vmulhvv<SignedType>, SignedType, vlmul, vta, vma>(
            args.dst, args.src1, args.src2);
      case Decoder::VOpMVvOpcode::kVwaddvv:
        if constexpr (sizeof(ElementType) == sizeof(Int64) ||
                      vlmul == VectorRegisterGroupMultiplier::k8registers) {
          return Unimplemented();
        } else {
          return OpVectorWidenvv<intrinsics::Vwaddvv<SignedType>, SignedType, vlmul, vta, vma>(
              args.dst, args.src1, args.src2);
        }
      case Decoder::VOpMVvOpcode::kVwadduvv:
        if constexpr (sizeof(ElementType) == sizeof(Int64) ||
                      vlmul == VectorRegisterGroupMultiplier::k8registers) {
          return Unimplemented();
        } else {
          return OpVectorWidenvv<intrinsics::Vwaddvv<UnsignedType>, UnsignedType, vlmul, vta, vma>(
              args.dst, args.src1, args.src2);
        }
      case Decoder::VOpMVvOpcode::kVwsubuvv:
        if constexpr (sizeof(ElementType) == sizeof(Int64) ||
                      vlmul == VectorRegisterGroupMultiplier::k8registers) {
          return Unimplemented();
        } else {
          return OpVectorWidenvv<intrinsics::Vwsubvv<UnsignedType>, UnsignedType, vlmul, vta, vma>(
              args.dst, args.src1, args.src2);
        }
      default:
        Unimplemented();
    }
  }

  template <typename ElementType, VectorRegisterGroupMultiplier vlmul, TailProcessing vta, auto vma>
  void OpVector(const Decoder::VOpIVxArgs& args, Register arg2) {
    using SignedType = berberis::SignedType<ElementType>;
    using UnsignedType = berberis::UnsignedType<ElementType>;
    switch (args.opcode) {
      case Decoder::VOpIVxOpcode::kVaddvx:
        return OpVectorvx<intrinsics::Vaddvx<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<ElementType>(arg2));
      case Decoder::VOpIVxOpcode::kVsubvx:
        return OpVectorvx<intrinsics::Vsubvx<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<ElementType>(arg2));
      case Decoder::VOpIVxOpcode::kVrsubvx:
        return OpVectorvx<intrinsics::Vrsubvx<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<ElementType>(arg2));
      case Decoder::VOpIVxOpcode::kVandvx:
        return OpVectorvx<intrinsics::Vandvx<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<ElementType>(arg2));
      case Decoder::VOpIVxOpcode::kVorvx:
        return OpVectorvx<intrinsics::Vorvx<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<ElementType>(arg2));
      case Decoder::VOpIVxOpcode::kVxorvx:
        return OpVectorvx<intrinsics::Vxorvx<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<ElementType>(arg2));
      case Decoder::VOpIVxOpcode::kVmseqvx:
        return OpVectormvx<intrinsics::Vseqvx<ElementType>, ElementType, vlmul, vma>(
            args.dst, args.src1, MaybeTruncateTo<ElementType>(arg2));
      case Decoder::VOpIVxOpcode::kVmsnevx:
        return OpVectormvx<intrinsics::Vsnevx<ElementType>, ElementType, vlmul, vma>(
            args.dst, args.src1, MaybeTruncateTo<ElementType>(arg2));
      case Decoder::VOpIVxOpcode::kVmsltuvx:
        return OpVectormvx<intrinsics::Vsltvx<UnsignedType>, UnsignedType, vlmul, vma>(
            args.dst, args.src1, MaybeTruncateTo<UnsignedType>(arg2));
      case Decoder::VOpIVxOpcode::kVmsltvx:
        return OpVectormvx<intrinsics::Vsltvx<SignedType>, SignedType, vlmul, vma>(
            args.dst, args.src1, MaybeTruncateTo<SignedType>(arg2));
      case Decoder::VOpIVxOpcode::kVmsleuvx:
        return OpVectormvx<intrinsics::Vslevx<UnsignedType>, UnsignedType, vlmul, vma>(
            args.dst, args.src1, MaybeTruncateTo<UnsignedType>(arg2));
      case Decoder::VOpIVxOpcode::kVmslevx:
        return OpVectormvx<intrinsics::Vslevx<SignedType>, SignedType, vlmul, vma>(
            args.dst, args.src1, MaybeTruncateTo<SignedType>(arg2));
      case Decoder::VOpIVxOpcode::kVmsgtuvx:
        return OpVectormvx<intrinsics::Vsgtvx<UnsignedType>, UnsignedType, vlmul, vma>(
            args.dst, args.src1, MaybeTruncateTo<UnsignedType>(arg2));
      case Decoder::VOpIVxOpcode::kVmsgtvx:
        return OpVectormvx<intrinsics::Vsgtvx<SignedType>, SignedType, vlmul, vma>(
            args.dst, args.src1, MaybeTruncateTo<SignedType>(arg2));
      case Decoder::VOpIVxOpcode::kVsllvx:
        return OpVectorvx<intrinsics::Vslvx<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<ElementType>(arg2));
      case Decoder::VOpIVxOpcode::kVsrlvx:
        return OpVectorvx<intrinsics::Vsrvx<UnsignedType>, UnsignedType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<UnsignedType>(arg2));
      case Decoder::VOpIVxOpcode::kVsravx:
        return OpVectorvx<intrinsics::Vsrvx<SignedType>, SignedType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<SignedType>(arg2));
      case Decoder::VOpIVxOpcode::kVminuvx:
        return OpVectorvx<intrinsics::Vminvx<UnsignedType>, UnsignedType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<UnsignedType>(arg2));
      case Decoder::VOpIVxOpcode::kVminvx:
        return OpVectorvx<intrinsics::Vminvx<SignedType>, SignedType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<SignedType>(arg2));
      case Decoder::VOpIVxOpcode::kVmaxuvx:
        return OpVectorvx<intrinsics::Vmaxvx<UnsignedType>, UnsignedType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<UnsignedType>(arg2));
      case Decoder::VOpIVxOpcode::kVmaxvx:
        return OpVectorvx<intrinsics::Vmaxvx<SignedType>, SignedType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<SignedType>(arg2));
      case Decoder::VOpIVxOpcode::kVmergevx:
        if constexpr (std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>) {
          if (args.src1 != 0) {
            return Unimplemented();
          }
          return OpVectorx<intrinsics::Vcopyx<ElementType>, ElementType, vlmul, vta, vma>(
              args.dst, MaybeTruncateTo<ElementType>(arg2));
        } else {
          return OpVectorx<intrinsics::Vcopyx<ElementType>,
                           ElementType,
                           vlmul,
                           vta,
                           // Always use "undisturbed" value from source register.
                           InactiveProcessing::kUndisturbed>(
              args.dst, MaybeTruncateTo<ElementType>(arg2), /*dst_mask=*/args.src1);
        }
      case Decoder::VOpIVxOpcode::kVnsrawx:
        return OpVectorNarrowwx<intrinsics::Vnsrwx<SignedType>, SignedType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<SignedType>(arg2));
      case Decoder::VOpIVxOpcode::kVnsrlwx:
        return OpVectorNarrowwx<intrinsics::Vnsrwx<UnsignedType>, UnsignedType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<UnsignedType>(arg2));
      case Decoder::VOpIVxOpcode::kVslideupvx:
        return OpVectorslideup<ElementType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<UnsignedType>(arg2));
      case Decoder::VOpIVxOpcode::kVslidedownvx:
        return OpVectorslidedown<ElementType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<UnsignedType>(arg2));
      default:
        Unimplemented();
    }
  }

  template <typename ElementType, VectorRegisterGroupMultiplier vlmul, TailProcessing vta, auto vma>
  void OpVector(const Decoder::VOpMVxArgs& args, Register arg2) {
    using SignedType = berberis::SignedType<ElementType>;
    using UnsignedType = berberis::UnsignedType<ElementType>;
    switch (args.opcode) {
      case Decoder::VOpMVxOpcode::kVRXUnary0:
        switch (args.vrxunary0_opcode) {
          case Decoder::VRXUnary0Opcode::kVmvsx:
            if constexpr (!std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>) {
              return Unimplemented();
            }
            return OpVectorVmvsx<SignedType, vta>(args.dst, MaybeTruncateTo<SignedType>(arg2));
          default:
            return Unimplemented();
        }
      case Decoder::VOpMVxOpcode::kVmaddvx:
        return OpVectorvxv<intrinsics::Vmaddvx<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<ElementType>(arg2));
      case Decoder::VOpMVxOpcode::kVnmsubvx:
        return OpVectorvxv<intrinsics::Vnmsubvx<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<ElementType>(arg2));
      case Decoder::VOpMVxOpcode::kVmaccvx:
        return OpVectorvxv<intrinsics::Vmaccvx<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<ElementType>(arg2));
      case Decoder::VOpMVxOpcode::kVnmsacvx:
        return OpVectorvxv<intrinsics::Vnmsacvx<ElementType>, ElementType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<ElementType>(arg2));
      case Decoder::VOpMVxOpcode::kVmulhuvx:
        return OpVectorvx<intrinsics::Vmulhvx<UnsignedType>, UnsignedType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<UnsignedType>(arg2));
      case Decoder::VOpMVxOpcode::kVmulvx:
        return OpVectorvx<intrinsics::Vmulvx<SignedType>, SignedType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<SignedType>(arg2));
      case Decoder::VOpMVxOpcode::kVmulhsuvx:
        return OpVectorvx<intrinsics::Vmulhsuvx<SignedType>, SignedType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<SignedType>(arg2));
      case Decoder::VOpMVxOpcode::kVmulhvx:
        return OpVectorvx<intrinsics::Vmulhvx<SignedType>, SignedType, vlmul, vta, vma>(
            args.dst, args.src1, MaybeTruncateTo<SignedType>(arg2));
      default:
        Unimplemented();
    }
  }

  template <typename DataElementType,
            VectorRegisterGroupMultiplier vlmul,
            typename IndexElementType,
            size_t kSegmentSize,
            size_t kIndexRegistersInvolved,
            TailProcessing vta,
            auto vma>
  void OpVector(const Decoder::VStoreIndexedArgs& args, Register src) {
    return OpVector<DataElementType,
                    kSegmentSize,
                    NumberOfRegistersInvolved(vlmul),
                    IndexElementType,
                    kIndexRegistersInvolved,
                    !std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>>(args, src);
  }

  template <typename DataElementType,
            size_t kSegmentSize,
            size_t kNumRegistersInGroup,
            typename IndexElementType,
            size_t kIndexRegistersInvolved,
            bool kUseMasking>
  void OpVector(const Decoder::VStoreIndexedArgs& args, Register src) {
    if (!IsAligned<kIndexRegistersInvolved>(args.idx)) {
      return Unimplemented();
    }
    constexpr size_t kElementsCount =
        static_cast<int>(sizeof(SIMD128Register) / sizeof(IndexElementType));
    alignas(alignof(SIMD128Register))
        IndexElementType indexes[kElementsCount * kIndexRegistersInvolved];
    memcpy(indexes, state_->cpu.v + args.idx, sizeof(SIMD128Register) * kIndexRegistersInvolved);
    return OpVectorStore<DataElementType, kSegmentSize, kNumRegistersInGroup, kUseMasking>(
        args.data, src, [&indexes](size_t index) { return indexes[index]; });
  }

  template <typename ElementType,
            size_t kSegmentSize,
            VectorRegisterGroupMultiplier vlmul,
            TailProcessing vta,
            auto vma>
  void OpVector(const Decoder::VStoreStrideArgs& args, Register src, Register stride) {
    return OpVectorStore<ElementType,
                         kSegmentSize,
                         NumberOfRegistersInvolved(vlmul),
                         !std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>>(
        args.data, src, [stride](size_t index) { return stride * index; });
  }

  template <typename ElementType,
            size_t kSegmentSize,
            VectorRegisterGroupMultiplier vlmul,
            TailProcessing vta,
            auto vma>
  void OpVector(const Decoder::VStoreUnitStrideArgs& args, Register src) {
    switch (args.opcode) {
      case Decoder::VSUmOpOpcode::kVseXX:
        return OpVectorStore<ElementType,
                             kSegmentSize,
                             NumberOfRegistersInvolved(vlmul),
                             !std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>,
                             Decoder::VSUmOpOpcode::kVseXX>(args.data, src, [](size_t index) {
          return kSegmentSize * sizeof(ElementType) * index;
        });
      case Decoder::VSUmOpOpcode::kVsm:
        if constexpr (kSegmentSize == 1 &&
                      std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>) {
          return OpVectorStore<UInt8,
                               1,
                               1,
                               /*kUseMasking=*/false,
                               Decoder::VSUmOpOpcode::kVsm>(
              args.data, src, [](size_t index) { return index; });
        }
        return Unimplemented();
      default:
        return Unimplemented();
    }
  }

  // Look for VLoadStrideArgs for explanation about semantics: VStoreStrideArgs is almost symmetric,
  // except it ignores vta and vma modes and never alters inactive elements in memory.
  template <typename ElementType,
            size_t kSegmentSize,
            size_t kNumRegistersInGroup,
            bool kUseMasking,
            typename Decoder::VSUmOpOpcode opcode = typename Decoder::VSUmOpOpcode{},
            typename GetElementOffsetLambdaType>
  void OpVectorStore(uint8_t data, Register src, GetElementOffsetLambdaType GetElementOffset) {
    using MaskType = std::conditional_t<sizeof(ElementType) == sizeof(Int8), UInt16, UInt8>;
    if (!IsAligned<kNumRegistersInGroup>(data)) {
      return Unimplemented();
    }
    if (data + kNumRegistersInGroup * kSegmentSize > 32) {
      return Unimplemented();
    }
    constexpr size_t kElementsCount = static_cast<int>(16 / sizeof(ElementType));
    size_t vstart = GetCsr<CsrName::kVstart>();
    size_t vl = GetCsr<CsrName::kVl>();
    if constexpr (opcode == Decoder::VSUmOpOpcode::kVsm) {
      vl = AlignUp<CHAR_BIT>(vl) / CHAR_BIT;
    }
    // In case of memory access fault we may set vstart to non-zero value, set it to zero here to
    // simplify the logic below.
    SetCsr<CsrName::kVstart>(0);
    // When vstart >= vl, there are no body elements, and no elements are updated in any destination
    // vector register group, including that no tail elements are updated with agnostic values.
    if (vstart >= vl) [[unlikely]] {
      // Technically, since stores never touch tail elements it's not needed, but makes it easier to
      // reason about the rest of function.
      return;
    }
    char* ptr = ToHostAddr<char>(src);
    // Note: within_group_id is the current register id within a register group. During one
    // iteration of this loop we store results for all registers with the current id in all
    // groups. E.g. for the example above we'd store data from v0, v2, v4 during the first iteration
    // (id within group = 0), and v1, v3, v5 during the second iteration (id within group = 1). This
    // ensures that memory is always accessed in ordered fashion.
    auto mask = GetMaskForVectorOperationsIfNeeded<kUseMasking>();
    for (size_t within_group_id = vstart / kElementsCount; within_group_id < kNumRegistersInGroup;
         ++within_group_id) {
      // No need to continue if we no longer have elements to store.
      if (within_group_id * kElementsCount >= vl) {
        break;
      }
      auto register_mask =
          std::get<0>(intrinsics::MaskForRegisterInSequence<ElementType>(mask, within_group_id));
      // Store elements to memory, but only if there are any active ones.
      for (size_t within_register_id = vstart % kElementsCount; within_register_id < kElementsCount;
           ++within_register_id) {
        size_t element_index = kElementsCount * within_group_id + within_register_id;
        // Stop if we reached the vl limit.
        if (vl <= element_index) {
          break;
        }
        // Don't touch masked-out elements.
        if constexpr (kUseMasking) {
          if ((MaskType(register_mask) & MaskType{static_cast<typename MaskType::BaseType>(
                                             1 << within_register_id)}) == MaskType{0}) {
            continue;
          }
        }
        // Store segment to memory.
        for (size_t field = 0; field < kSegmentSize; ++field) {
          bool exception_raised = FaultyStore(
              ptr + field * sizeof(ElementType) + GetElementOffset(element_index),
              sizeof(ElementType),
              SIMD128Register{state_->cpu.v[data + within_group_id + field * kNumRegistersInGroup]}
                  .Get<ElementType>(within_register_id));
          // Stop processing if memory is inaccessible. It's also the only case where we have to set
          // vstart to non-zero value!
          if (exception_raised) {
            SetCsr<CsrName::kVstart>(element_index);
            return;
          }
        }
      }
      // Next group should be fully processed.
      vstart = 0;
    }
  }

  template <typename ElementType, VectorRegisterGroupMultiplier vlmul, TailProcessing vta, auto vma>
  void OpVectorVidv(uint8_t dst) {
    return OpVectorVidv<ElementType, NumberOfRegistersInvolved(vlmul), vta, vma>(dst);
  }

  template <typename ElementType, size_t kRegistersInvolved, TailProcessing vta, auto vma>
  void OpVectorVidv(uint8_t dst) {
    if (!IsAligned<kRegistersInvolved>(dst)) {
      return Unimplemented();
    }
    size_t vstart = GetCsr<CsrName::kVstart>();
    size_t vl = GetCsr<CsrName::kVl>();
    SetCsr<CsrName::kVstart>(0);
    // When vstart >= vl, there are no body elements, and no elements are updated in any destination
    // vector register group, including that no tail elements are updated with agnostic values.
    if (vstart >= vl) [[unlikely]] {
      return;
    }
    auto mask = GetMaskForVectorOperations<vma>();
    for (size_t index = 0; index < kRegistersInvolved; ++index) {
      SIMD128Register result{state_->cpu.v[dst + index]};
      result = VectorMasking<ElementType, vta, vma>(
          result, std::get<0>(intrinsics::Vidv<ElementType>(index)), vstart, vl, index, mask);
      state_->cpu.v[dst + index] = result.Get<__uint128_t>();
    }
  }

  template <typename ElementType>
  void OpVectorVmvfs(uint8_t dst, uint8_t src) {
    // Note: intrinsics::NanBox always received Float64 argument, even if it processes Float32 value
    // to not cause recursion in interinsics handling.
    // NanBox in the interpreter takes FpRegister and returns FpRegister which is probably the
    // cleanest way of processing that data (at least on x86-64 this produces code that's close to
    // optimal).
    NanBoxAndSetFpReg<ElementType>(dst, SIMD128Register{state_->cpu.v[src]}.Get<FpRegister>(0));
    SetCsr<CsrName::kVstart>(0);
  }

  template <typename ElementType, TailProcessing vta>
  void OpVectorVmvsx(uint8_t dst, ElementType element) {
    size_t vstart = GetCsr<CsrName::kVstart>();
    size_t vl = GetCsr<CsrName::kVl>();
    // Documentation doesn't specify what happenes when vstart is non-zero but less than vl.
    // But at least one hardware implementation treats it as NOP:
    //   https://github.com/riscv/riscv-v-spec/issues/937
    // We are doing the same here.
    if (vstart == 0 && vl != 0) [[likely]] {
      SIMD128Register result;
      if constexpr (vta == intrinsics::TailProcessing::kAgnostic) {
        result = ~SIMD128Register{};
      } else {
        result.Set(state_->cpu.v[dst]);
      }
      result.Set(element, 0);
      state_->cpu.v[dst] = result.Get<Int128>();
    }
    SetCsr<CsrName::kVstart>(0);
  }

  template <typename ElementType>
  void OpVectorVmvxs(uint8_t dst, uint8_t src1) {
    static_assert(ElementType::kIsSigned);
    // Conversion to Int64 would perform sign-extension if source element is signed.
    Register element = Int64{SIMD128Register{state_->cpu.v[src1]}.Get<ElementType>(0)};
    SetRegOrIgnore(dst, element);
    SetCsr<CsrName::kVstart>(0);
  }

  template <auto Intrinsic, auto vma>
  void OpVectorVWXUnary0(uint8_t dst, uint8_t src1) {
    size_t vstart = GetCsr<CsrName::kVstart>();
    size_t vl = GetCsr<CsrName::kVl>();
    if (vstart != 0) [[unlikely]] {
      return Unimplemented();
    }
    // Note: vcpop.m  and vfirst.m are explicit exception to the rule that vstart >= vl doesn't
    // perform any operations, and they are explicitly defined to perform write even if vl == 0.
    SIMD128Register arg1(state_->cpu.v[src1]);
    if constexpr (!std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>) {
      SIMD128Register mask(state_->cpu.v[0]);
      arg1 &= mask;
    }
    const auto [tail_mask] = intrinsics::MakeBitmaskFromVl(vl);
    arg1 &= ~tail_mask;
    SIMD128Register result = std::get<0>(Intrinsic(arg1.Get<Int128>()));
    SetRegOrIgnore(dst, TruncateTo<UInt64>(BitCastToUnsigned(result.Get<Int128>())));
  }

  template <auto Intrinsic>
  void OpVectormm(uint8_t dst, uint8_t src1, uint8_t src2) {
    size_t vstart = GetCsr<CsrName::kVstart>();
    size_t vl = GetCsr<CsrName::kVl>();
    SetCsr<CsrName::kVstart>(0);
    // When vstart >= vl, there are no body elements, and no elements are updated in any destination
    // vector register group, including that no tail elements are updated with agnostic values.
    if (vstart >= vl) [[unlikely]] {
      return;
    }
    SIMD128Register arg1(state_->cpu.v[src1]);
    SIMD128Register arg2(state_->cpu.v[src2]);
    SIMD128Register result;
    if (vstart > 0) [[unlikely]] {
      const auto [start_mask] = intrinsics::MakeBitmaskFromVl(vstart);
      result.Set(state_->cpu.v[dst]);
      result = (result & ~start_mask) | (Intrinsic(arg1, arg2) & start_mask);
    } else {
      result = Intrinsic(arg1, arg2);
    }
    const auto [tail_mask] = intrinsics::MakeBitmaskFromVl(vl);
    result = result | tail_mask;
    state_->cpu.v[dst] = result.Get<__uint128_t>();
  }

  template <auto Intrinsic, auto vma>
  void OpVectorVMUnary0(uint8_t dst, uint8_t src1) {
    size_t vstart = GetCsr<CsrName::kVstart>();
    size_t vl = GetCsr<CsrName::kVl>();
    if (vstart != 0) {
      return Unimplemented();
    }
    // When vstart >= vl, there are no body elements, and no elements are updated in any destination
    // vector register group, including that no tail elements are updated with agnostic values.
    if (vl == 0) [[unlikely]] {
      return;
    }
    SIMD128Register arg1(state_->cpu.v[src1]);
    SIMD128Register mask;
    if constexpr (!std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>) {
      mask.Set<__uint128_t>(state_->cpu.v[0]);
      arg1 &= mask;
    }
    const auto [tail_mask] = intrinsics::MakeBitmaskFromVl(vl);
    arg1 &= ~tail_mask;
    SIMD128Register result = std::get<0>(Intrinsic(arg1.Get<Int128>()));
    if constexpr (!std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>) {
      arg1 &= mask;
      if (vma == InactiveProcessing::kUndisturbed) {
        result = (result & mask) | (SIMD128Register(state_->cpu.v[dst]) & ~mask);
      } else {
        result |= ~mask;
      }
    }
    result |= tail_mask;
    state_->cpu.v[dst] = result.Get<__uint128_t>();
  }

  template <typename ElementType, size_t kRegistersInvolved>
  void OpVectorVmvXrv(uint8_t dst, uint8_t src) {
    if (!IsAligned<kRegistersInvolved>(dst | src)) {
      return Unimplemented();
    }
    constexpr size_t kElementsCount = static_cast<int>(16 / sizeof(ElementType));
    size_t vstart = GetCsr<CsrName::kVstart>();
    SetCsr<CsrName::kVstart>(0);
    // The usual property that no elements are written if vstart >= vl does not apply to these
    // instructions. Instead, no elements are written if vstart >= evl.
    if (vstart >= kElementsCount * kRegistersInvolved) [[unlikely]] {
      return;
    }
    if (vstart == 0) [[likely]] {
      for (size_t index = 0; index < kRegistersInvolved; ++index) {
        state_->cpu.v[dst + index] = state_->cpu.v[src + index];
      }
      return;
    }
    size_t index = vstart / kElementsCount;
    SIMD128Register destination{state_->cpu.v[dst + index]};
    SIMD128Register source{state_->cpu.v[src + index]};
    for (size_t element_index = vstart % kElementsCount; element_index < kElementsCount;
         ++element_index) {
      destination.Set(source.Get<ElementType>(element_index), element_index);
    }
    state_->cpu.v[dst + index] = destination.Get<__uint128_t>();
    for (index++; index < kRegistersInvolved; ++index) {
      state_->cpu.v[dst + index] = state_->cpu.v[src + index];
    }
  }

  template <auto Intrinsic, typename ElementType, VectorRegisterGroupMultiplier vlmul, auto vma>
  void OpVectormvv(uint8_t dst, uint8_t src1, uint8_t src2) {
    return OpVectormvv<Intrinsic, ElementType, NumberOfRegistersInvolved(vlmul), vma>(
        dst, src1, src2);
  }

  template <auto Intrinsic, typename ElementType, size_t kRegistersInvolved, auto vma>
  void OpVectormvv(uint8_t dst, uint8_t src1, uint8_t src2) {
    if (!IsAligned<kRegistersInvolved>(src1 | src2)) {
      return Unimplemented();
    }
    SIMD128Register original_result(state_->cpu.v[dst]);
    size_t vstart = GetCsr<CsrName::kVstart>();
    size_t vl = GetCsr<CsrName::kVl>();
    SetCsr<CsrName::kVstart>(0);
    SIMD128Register result_before_vl_masking;
    // When vstart >= vl, there are no body elements, and no elements are updated in any destination
    // vector register group, including that no tail elements are updated with agnostic values.
    if (vstart >= vl) [[unlikely]] {
      result_before_vl_masking = original_result;
    } else {
      result_before_vl_masking =
          CollectBitmaskResult<ElementType, kRegistersInvolved>([this, src1, src2](auto index) {
            SIMD128Register arg1(state_->cpu.v[src1 + static_cast<size_t>(index)]);
            SIMD128Register arg2(state_->cpu.v[src2 + static_cast<size_t>(index)]);
            return Intrinsic(arg1, arg2);
          });
      SIMD128Register mask(state_->cpu.v[0]);
      if constexpr (!std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>) {
        if constexpr (vma == InactiveProcessing::kAgnostic) {
          result_before_vl_masking |= ~mask;
        } else {
          result_before_vl_masking = (mask & result_before_vl_masking) | (original_result & ~mask);
        }
      }
      if (vstart > 0) [[unlikely]] {
        const auto [start_mask] = intrinsics::MakeBitmaskFromVl(vstart);
        result_before_vl_masking =
            (original_result & ~start_mask) | (result_before_vl_masking & start_mask);
      }
    }
    const auto [tail_mask] = intrinsics::MakeBitmaskFromVl(vl);
    state_->cpu.v[dst] = (result_before_vl_masking | tail_mask).Get<__uint128_t>();
  }

  template <auto Intrinsic, typename ElementType, VectorRegisterGroupMultiplier vlmul, auto vma>
  void OpVectormvx(uint8_t dst, uint8_t src1, ElementType arg2) {
    return OpVectormvx<Intrinsic, ElementType, NumberOfRegistersInvolved(vlmul), vma>(
        dst, src1, arg2);
  }

  template <auto Intrinsic, typename ElementType, size_t kRegistersInvolved, auto vma>
  void OpVectormvx(uint8_t dst, uint8_t src1, ElementType arg2) {
    if (!IsAligned<kRegistersInvolved>(src1)) {
      return Unimplemented();
    }
    SIMD128Register original_result(state_->cpu.v[dst]);
    size_t vstart = GetCsr<CsrName::kVstart>();
    size_t vl = GetCsr<CsrName::kVl>();
    SetCsr<CsrName::kVstart>(0);
    SIMD128Register result_before_vl_masking;
    // When vstart >= vl, there are no body elements, and no elements are updated in any destination
    // vector register group, including that no tail elements are updated with agnostic values.
    if (vstart >= vl) [[unlikely]] {
      result_before_vl_masking = original_result;
    } else {
      result_before_vl_masking =
          CollectBitmaskResult<ElementType, kRegistersInvolved>([this, src1, arg2](auto index) {
            SIMD128Register arg1(state_->cpu.v[src1 + static_cast<size_t>(index)]);
            return Intrinsic(arg1, arg2);
          });
      if constexpr (!std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>) {
        SIMD128Register mask(state_->cpu.v[0]);
        if constexpr (vma == InactiveProcessing::kAgnostic) {
          result_before_vl_masking |= ~mask;
        } else {
          result_before_vl_masking = (mask & result_before_vl_masking) | (original_result & ~mask);
        }
      }
      if (vstart > 0) [[unlikely]] {
        const auto [start_mask] = intrinsics::MakeBitmaskFromVl(vstart);
        result_before_vl_masking =
            (original_result & ~start_mask) | (result_before_vl_masking & start_mask);
      }
    }
    const auto [tail_mask] = intrinsics::MakeBitmaskFromVl(vl);
    state_->cpu.v[dst] = (result_before_vl_masking | tail_mask).Get<__uint128_t>();
  }

  template <auto Intrinsic,
            typename ElementType,
            VectorRegisterGroupMultiplier vlmul,
            TailProcessing vta,
            auto vma,
            typename... DstMaskType>
  void OpVectorv(uint8_t dst, uint8_t src1, DstMaskType... dst_mask) {
    return OpVectorv<Intrinsic, ElementType, NumberOfRegistersInvolved(vlmul), vta, vma>(
        dst, src1, dst_mask...);
  }

  template <auto Intrinsic,
            typename ElementType,
            size_t kRegistersInvolved,
            TailProcessing vta,
            auto vma,
            typename... DstMaskType>
  void OpVectorv(uint8_t dst, uint8_t src, DstMaskType... dst_mask) {
    static_assert(sizeof...(dst_mask) <= 1);
    if (!IsAligned<kRegistersInvolved>(dst | src | (dst_mask | ... | 0))) {
      return Unimplemented();
    }
    size_t vstart = GetCsr<CsrName::kVstart>();
    size_t vl = GetCsr<CsrName::kVl>();
    SetCsr<CsrName::kVstart>(0);
    // When vstart >= vl, there are no body elements, and no elements are updated in any destination
    // vector register group, including that no tail elements are updated with agnostic values.
    if (vstart >= vl) [[unlikely]] {
      return;
    }
    auto mask = GetMaskForVectorOperations<vma>();
    for (size_t index = 0; index < kRegistersInvolved; ++index) {
      SIMD128Register result{state_->cpu.v[dst + index]};
      SIMD128Register result_mask;
      if constexpr (sizeof...(DstMaskType) == 0) {
        result_mask.Set(state_->cpu.v[dst + index]);
      } else {
        uint8_t dst_mask_unpacked[1] = {dst_mask...};
        result_mask.Set(state_->cpu.v[dst_mask_unpacked[0] + index]);
      }
      SIMD128Register arg{state_->cpu.v[src + index]};
      result = VectorMasking<ElementType, vta, vma>(
          result, std::get<0>(Intrinsic(arg)), result_mask, vstart, vl, index, mask);
      state_->cpu.v[dst + index] = result.Get<__uint128_t>();
    }
  }

  template <auto Intrinsic,
            typename ElementType,
            VectorRegisterGroupMultiplier vlmul,
            TailProcessing vta,
            auto vma>
  void OpVectorvr(uint8_t dst, uint8_t src1) {
    return OpVectorvr<Intrinsic, ElementType, NumberOfRegistersInvolved(vlmul), vta, vma>(dst,
                                                                                          src1);
  }

  template <auto Intrinsic,
            typename ElementType,
            size_t kRegistersInvolved,
            TailProcessing vta,
            auto vma>
  void OpVectorvr(uint8_t dst, uint8_t src) {
    if (!IsAligned<kRegistersInvolved>(dst | src)) {
      return Unimplemented();
    }
    size_t vstart = GetCsr<CsrName::kVstart>();
    size_t vl = GetCsr<CsrName::kVl>();
    SetCsr<CsrName::kVstart>(0);
    // When vstart >= vl, there are no body elements, and no elements are updated in any destination
    // vector register group, including that no tail elements are updated with agnostic values.
    if (vstart >= vl) [[unlikely]] {
      return;
    }
    int8_t frm = GetCsr<CsrName::kFrm>();
    auto mask = GetMaskForVectorOperations<vma>();
    for (size_t index = 0; index < kRegistersInvolved; ++index) {
      SIMD128Register result{state_->cpu.v[dst + index]};
      SIMD128Register arg{state_->cpu.v[src + index]};
      result = VectorMasking<ElementType, vta, vma>(
          result, std::get<0>(Intrinsic(frm, arg)), vstart, vl, index, mask);
      state_->cpu.v[dst + index] = result.Get<__uint128_t>();
    }
  }

  template <auto Intrinsic,
            typename ElementType,
            VectorRegisterGroupMultiplier vlmul,
            TailProcessing vta,
            auto vma>
  void OpVectorvs(uint8_t dst, uint8_t src1, uint8_t src2) {
    return OpVectorvs<Intrinsic, ElementType, NumberOfRegistersInvolved(vlmul), vta, vma>(
        dst, src1, src2);
  }

  template <auto Intrinsic,
            typename ElementType,
            size_t kRegistersInvolved,
            TailProcessing vta,
            auto vma>
  void OpVectorvs(uint8_t dst, uint8_t src1, uint8_t src2) {
    if (!IsAligned<kRegistersInvolved>(dst | src1 | src2)) {
      return Unimplemented();
    }
    size_t vstart = GetCsr<CsrName::kVstart>();
    size_t vl = GetCsr<CsrName::kVl>();
    if (vstart != 0) {
      return Unimplemented();
    }
    SetCsr<CsrName::kVstart>(0);
    // When vstart >= vl, there are no body elements, and no elements are updated in any destination
    // vector register group, including that no tail elements are updated with agnostic values.
    if (vl == 0) [[unlikely]] {
      return;
    }
    SIMD128Register result;
    auto mask = GetMaskForVectorOperations<vma>();
    ElementType arg1 = SIMD128Register{state_->cpu.v[src1]}.Get<ElementType>(0);
    for (size_t index = 0; index < kRegistersInvolved; ++index) {
      using MaskType = std::conditional_t<sizeof(ElementType) == sizeof(Int8), UInt16, UInt8>;
      const MaskType element_count{
          static_cast<typename MaskType::BaseType>(std::min(16 / sizeof(ElementType), vl))};
      auto mask_bits = std::get<0>(intrinsics::MaskForRegisterInSequence<ElementType>(mask, index));
      SIMD128Register arg2(state_->cpu.v[src2 + index]);
      for (MaskType element_index = MaskType{0}; element_index < element_count;
           element_index += MaskType{1}) {
        if constexpr (!std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>) {
          if ((MaskType{mask_bits} & (MaskType{1} << element_index)) == MaskType{0}) {
            continue;
          }
        }
        result = std::get<0>(Intrinsic(arg1, arg2.Get<ElementType>(element_index)));
        arg1 = result.Get<ElementType>(0);
      }
    }
    result.Set(state_->cpu.v[dst]);
    result.Set(arg1, 0);
    result = std::get<0>(intrinsics::VectorMasking<ElementType, vta>(result, result, 0, 1));
    state_->cpu.v[dst] = result.Get<__uint128_t>();
  }

  template <auto Intrinsic,
            typename ElementType,
            VectorRegisterGroupMultiplier vlmul,
            TailProcessing vta,
            auto vma>
  void OpVectorvv(uint8_t dst, uint8_t src1, uint8_t src2) {
    return OpVectorvv<Intrinsic, ElementType, NumberOfRegistersInvolved(vlmul), vta, vma>(
        dst, src1, src2);
  }

  template <auto Intrinsic,
            typename ElementType,
            size_t kRegistersInvolved,
            TailProcessing vta,
            auto vma>
  void OpVectorvv(uint8_t dst, uint8_t src1, uint8_t src2) {
    if (!IsAligned<kRegistersInvolved>(dst | src1 | src2)) {
      return Unimplemented();
    }
    size_t vstart = GetCsr<CsrName::kVstart>();
    size_t vl = GetCsr<CsrName::kVl>();
    SetCsr<CsrName::kVstart>(0);
    // When vstart >= vl, there are no body elements, and no elements are updated in any destination
    // vector register group, including that no tail elements are updated with agnostic values.
    if (vstart >= vl) [[unlikely]] {
      return;
    }
    auto mask = GetMaskForVectorOperations<vma>();
    for (size_t index = 0; index < kRegistersInvolved; ++index) {
      SIMD128Register result{state_->cpu.v[dst + index]};
      SIMD128Register arg1{state_->cpu.v[src1 + index]};
      SIMD128Register arg2{state_->cpu.v[src2 + index]};
      result = VectorMasking<ElementType, vta, vma>(
          result, std::get<0>(Intrinsic(arg1, arg2)), vstart, vl, index, mask);
      state_->cpu.v[dst + index] = result.Get<__uint128_t>();
    }
  }

  template <auto Intrinsic,
            typename ElementType,
            VectorRegisterGroupMultiplier vlmul,
            TailProcessing vta,
            auto vma>
  void OpVectorvvv(uint8_t dst, uint8_t src1, uint8_t src2) {
    return OpVectorvvv<Intrinsic, ElementType, NumberOfRegistersInvolved(vlmul), vta, vma>(
        dst, src1, src2);
  }

  template <auto Intrinsic,
            typename ElementType,
            size_t kRegistersInvolved,
            TailProcessing vta,
            auto vma>
  void OpVectorvvv(uint8_t dst, uint8_t src1, uint8_t src2) {
    if (!IsAligned<kRegistersInvolved>(dst | src1 | src2)) {
      return Unimplemented();
    }
    size_t vstart = GetCsr<CsrName::kVstart>();
    size_t vl = GetCsr<CsrName::kVl>();
    SetCsr<CsrName::kVstart>(0);
    // When vstart >= vl, there are no body elements, and no elements are updated in any destination
    // vector register group, including that no tail elements are updated with agnostic values.
    if (vstart >= vl) [[unlikely]] {
      return;
    }
    auto mask = GetMaskForVectorOperations<vma>();
    for (size_t index = 0; index < kRegistersInvolved; ++index) {
      SIMD128Register result(state_->cpu.v[dst + index]);
      SIMD128Register arg1(state_->cpu.v[src1 + index]);
      SIMD128Register arg2(state_->cpu.v[src2 + index]);
      result = VectorMasking<ElementType, vta, vma>(
          result, std::get<0>(Intrinsic(arg1, arg2, result)), vstart, vl, index, mask);
      state_->cpu.v[dst + index] = result.Get<__uint128_t>();
    }
  }

  template <auto Intrinsic,
            typename TargetElementType,
            typename SourceElementType,
            VectorRegisterGroupMultiplier vlmul,
            TailProcessing vta,
            auto vma>
  void OpVectorWidenvr(uint8_t dst, uint8_t src) {
    return OpVectorWidenvr<Intrinsic,
                           TargetElementType,
                           SourceElementType,
                           NumRegistersInvolvedForWideOperand(vlmul),
                           NumberOfRegistersInvolved(vlmul),
                           vta,
                           vma>(dst, src);
  }

  template <auto Intrinsic,
            typename TargetElementType,
            typename SourceElementType,
            size_t kDestRegistersInvolved,
            size_t kRegistersInvolved,
            TailProcessing vta,
            auto vma>
  void OpVectorWidenvr(uint8_t dst, uint8_t src) {
    if (!IsAligned<kDestRegistersInvolved>(dst) || !IsAligned<kRegistersInvolved>(src)) {
      return Unimplemented();
    }
    size_t vstart = GetCsr<CsrName::kVstart>();
    size_t vl = GetCsr<CsrName::kVl>();
    SetCsr<CsrName::kVstart>(0);
    // When vstart >= vl, there are no body elements, and no elements are updated in any destination
    // vector register group, including that no tail elements are updated with agnostic values.
    if (vstart >= vl) [[unlikely]] {
      return;
    }
    int8_t frm = GetCsr<CsrName::kFrm>();
    auto mask = GetMaskForVectorOperations<vma>();
    for (size_t index = 0; index < kRegistersInvolved; ++index) {
      SIMD128Register result(state_->cpu.v[dst + 2 * index]);
      SIMD128Register arg(state_->cpu.v[src + index]);
      result = VectorMasking<TargetElementType, vta, vma>(
          result, std::get<0>(Intrinsic(frm, arg)), vstart, vl, 2 * index, mask);
      state_->cpu.v[dst + 2 * index] = result.Get<__uint128_t>();
      if constexpr (kDestRegistersInvolved > 1) {  // if lmul is one full register or more
        result.Set(state_->cpu.v[dst + 2 * index + 1]);
        std::tie(arg) = intrinsics::VMovTopHalfToBottom<SourceElementType>(arg);
        result = VectorMasking<TargetElementType, vta, vma>(
            result, std::get<0>(Intrinsic(frm, arg)), vstart, vl, 2 * index + 1, mask);
        state_->cpu.v[dst + 2 * index + 1] = result.Get<__uint128_t>();
      }
    }
  }

  // 2*SEW = SEW op SEW
  // Attention: not to confuse with to be done OpVectorWidenwv with 2*SEW = 2*SEW op SEW
  template <auto Intrinsic,
            typename ElementType,
            VectorRegisterGroupMultiplier vlmul,
            TailProcessing vta,
            auto vma>
  void OpVectorWidenvv(uint8_t dst, uint8_t src1, uint8_t src2) {
    return OpVectorWidenvv<Intrinsic,
                           ElementType,
                           NumRegistersInvolvedForWideOperand(vlmul),
                           NumberOfRegistersInvolved(vlmul),
                           vta,
                           vma>(dst, src1, src2);
  }

  template <auto Intrinsic,
            typename ElementType,
            size_t kDestRegistersInvolved,
            size_t kRegistersInvolved,
            TailProcessing vta,
            auto vma>
  void OpVectorWidenvv(uint8_t dst, uint8_t src1, uint8_t src2) {
    if (!IsAligned<kDestRegistersInvolved>(dst) || !IsAligned<kRegistersInvolved>(src1 | src2)) {
      return Unimplemented();
    }
    size_t vstart = GetCsr<CsrName::kVstart>();
    size_t vl = GetCsr<CsrName::kVl>();
    SetCsr<CsrName::kVstart>(0);
    // When vstart >= vl, there are no body elements, and no elements are updated in any destination
    // vector register group, including that no tail elements are updated with agnostic values.
    if (vstart >= vl) [[unlikely]] {
      return;
    }
    auto mask = GetMaskForVectorOperations<vma>();
    for (size_t index = 0; index < kRegistersInvolved; ++index) {
      SIMD128Register result(state_->cpu.v[dst + 2 * index]);
      SIMD128Register arg1(state_->cpu.v[src1 + index]);
      SIMD128Register arg2(state_->cpu.v[src2 + index]);
      result = VectorMasking<decltype(Widen(ElementType{})), vta, vma>(
          result, std::get<0>(Intrinsic(arg1, arg2)), vstart, vl, 2 * index, mask);
      state_->cpu.v[dst + 2 * index] = result.Get<__uint128_t>();
      if constexpr (kDestRegistersInvolved > 1) {  // if lmul is one full register or more
        result.Set(state_->cpu.v[dst + 2 * index + 1]);
        std::tie(arg1) = intrinsics::VMovTopHalfToBottom<ElementType>(arg1);
        std::tie(arg2) = intrinsics::VMovTopHalfToBottom<ElementType>(arg2);
        result = VectorMasking<decltype(Widen(ElementType{})), vta, vma>(
            result, std::get<0>(Intrinsic(arg1, arg2)), vstart, vl, 2 * index + 1, mask);
        state_->cpu.v[dst + 2 * index + 1] = result.Get<__uint128_t>();
      }
    }
  }

  template <auto Intrinsic,
            typename ElementType,
            VectorRegisterGroupMultiplier vlmul,
            TailProcessing vta,
            auto vma,
            typename... DstMaskType>
  void OpVectorvx(uint8_t dst, uint8_t src1, ElementType arg2, DstMaskType... dst_mask) {
    return OpVectorvx<Intrinsic, ElementType, NumberOfRegistersInvolved(vlmul), vta, vma>(
        dst, src1, arg2, dst_mask...);
  }

  template <auto Intrinsic,
            typename ElementType,
            size_t kRegistersInvolved,
            TailProcessing vta,
            auto vma>
  void OpVectorvx(uint8_t dst, uint8_t src1, ElementType arg2) {
    if (!IsAligned<kRegistersInvolved>(dst | src1)) {
      return Unimplemented();
    }
    size_t vstart = GetCsr<CsrName::kVstart>();
    size_t vl = GetCsr<CsrName::kVl>();
    SetCsr<CsrName::kVstart>(0);
    // When vstart >= vl, there are no body elements, and no elements are updated in any destination
    // vector register group, including that no tail elements are updated with agnostic values.
    if (vstart >= vl) [[unlikely]] {
      return;
    }
    auto mask = GetMaskForVectorOperations<vma>();
    for (size_t index = 0; index < kRegistersInvolved; ++index) {
      SIMD128Register result(state_->cpu.v[dst + index]);
      SIMD128Register arg1(state_->cpu.v[src1 + index]);
      result = VectorMasking<ElementType, vta, vma>(
          result, std::get<0>(Intrinsic(arg1, arg2)), vstart, vl, index, mask);
      state_->cpu.v[dst + index] = result.Get<__uint128_t>();
    }
  }

  template <auto Intrinsic,
            typename TargetElementType,
            VectorRegisterGroupMultiplier vlmul,
            TailProcessing vta,
            auto vma>
  void OpVectorNarrowwr(uint8_t dst, uint8_t src) {
    return OpVectorNarrowwr<Intrinsic,
                            TargetElementType,
                            NumberOfRegistersInvolved(vlmul),
                            NumRegistersInvolvedForWideOperand(vlmul),
                            vta,
                            vma>(dst, src);
  }

  template <auto Intrinsic,
            typename TargetElementType,
            size_t kDestRegistersInvolved,
            size_t kSrcRegistersInvolved,
            TailProcessing vta,
            auto vma>
  void OpVectorNarrowwr(uint8_t dst, uint8_t src) {
    if constexpr (kDestRegistersInvolved == kSrcRegistersInvolved) {
      if (!IsAligned<kDestRegistersInvolved>(dst | src)) {
        return Unimplemented();
      }
    } else if (!IsAligned<kDestRegistersInvolved>(dst) || !IsAligned<kSrcRegistersInvolved>(src)) {
      return Unimplemented();
    }
    size_t vstart = GetCsr<CsrName::kVstart>();
    size_t vl = GetCsr<CsrName::kVl>();
    SetCsr<CsrName::kVstart>(0);
    // When vstart >= vl, there are no body elements, and no elements are updated in any destination
    // vector register group, including that no tail elements are updated with agnostic values.
    if (vstart >= vl) [[unlikely]] {
      return;
    }
    int8_t frm = GetCsr<CsrName::kFrm>();
    auto mask = GetMaskForVectorOperations<vma>();
    for (size_t index = 0; index < kDestRegistersInvolved; index++) {
      SIMD128Register orig_result(state_->cpu.v[dst + index]);
      SIMD128Register arg_low(state_->cpu.v[src + 2 * index]);
      SIMD128Register intrinsic_result = std::get<0>(Intrinsic(frm, arg_low));
      if constexpr (kSrcRegistersInvolved > 1) {
        SIMD128Register arg_high(state_->cpu.v[src + 2 * index + 1]);
        SIMD128Register result_high = std::get<0>(Intrinsic(frm, arg_high));
        intrinsic_result = std::get<0>(
            intrinsics::VMergeBottomHalfToTop<TargetElementType>(intrinsic_result, result_high));
      }
      auto result = VectorMasking<TargetElementType, vta, vma>(
          orig_result, intrinsic_result, vstart, vl, index, mask);
      state_->cpu.v[dst + index] = result.template Get<__uint128_t>();
    }
  }

  // SEW = 2*SEW op SEW
  template <auto Intrinsic,
            typename ElementType,
            VectorRegisterGroupMultiplier vlmul,
            TailProcessing vta,
            auto vma>
  void OpVectorNarrowwx(uint8_t dst, uint8_t src1, ElementType arg2) {
    return OpVectorNarrowwx<Intrinsic,
                            ElementType,
                            NumberOfRegistersInvolved(vlmul),
                            NumRegistersInvolvedForWideOperand(vlmul),
                            vta,
                            vma>(dst, src1, arg2);
  }

  template <auto Intrinsic,
            typename ElementType,
            size_t kDestRegistersInvolved,
            size_t kSrcRegistersInvolved,
            TailProcessing vta,
            auto vma>
  void OpVectorNarrowwx(uint8_t dst, uint8_t src1, ElementType arg2) {
    if constexpr (kDestRegistersInvolved == kSrcRegistersInvolved) {
      if (!IsAligned<kDestRegistersInvolved>(dst | src1)) {
        return Unimplemented();
      }
    } else if (!IsAligned<kDestRegistersInvolved>(dst) || !IsAligned<kSrcRegistersInvolved>(src1)) {
      return Unimplemented();
    }
    size_t vstart = GetCsr<CsrName::kVstart>();
    size_t vl = GetCsr<CsrName::kVl>();
    SetCsr<CsrName::kVstart>(0);
    // When vstart >= vl, there are no body elements, and no elements are updated in any destination
    // vector register group, including that no tail elements are updated with agnostic values.
    if (vstart >= vl) [[unlikely]] {
      return;
    }
    auto mask = GetMaskForVectorOperations<vma>();
    for (size_t index = 0; index < kDestRegistersInvolved; index++) {
      SIMD128Register orig_result(state_->cpu.v[dst + index]);
      SIMD128Register arg1_low(state_->cpu.v[src1 + 2 * index]);
      SIMD128Register intrinsic_result = std::get<0>(Intrinsic(arg1_low, arg2));

      if constexpr (kSrcRegistersInvolved > 1) {
        SIMD128Register arg1_high(state_->cpu.v[src1 + 2 * index + 1]);
        SIMD128Register result_high = std::get<0>(Intrinsic(arg1_high, arg2));
        intrinsic_result = std::get<0>(
            intrinsics::VMergeBottomHalfToTop<ElementType>(intrinsic_result, result_high));
      }

      auto result = VectorMasking<ElementType, vta, vma>(
          orig_result, intrinsic_result, vstart, vl, index, mask);
      state_->cpu.v[dst + index] = result.template Get<__uint128_t>();
    }
  }

  // SEW = 2*SEW op SEW
  template <auto Intrinsic,
            typename ElementType,
            VectorRegisterGroupMultiplier vlmul,
            TailProcessing vta,
            auto vma>
  void OpVectorNarrowwv(uint8_t dst, uint8_t src1, uint8_t src2) {
    return OpVectorNarrowwv<Intrinsic,
                            ElementType,
                            NumberOfRegistersInvolved(vlmul),
                            NumRegistersInvolvedForWideOperand(vlmul),
                            vta,
                            vma>(dst, src1, src2);
  }

  template <auto Intrinsic,
            typename ElementType,
            size_t kRegistersInvolved,
            size_t kFirstSrcRegistersInvolved,
            TailProcessing vta,
            auto vma>
  void OpVectorNarrowwv(uint8_t dst, uint8_t src1, uint8_t src2) {
    if constexpr (kRegistersInvolved == kFirstSrcRegistersInvolved) {
      if (!IsAligned<kRegistersInvolved>(dst | src1 | src2)) {
        return Unimplemented();
      }
    } else if (!IsAligned<kRegistersInvolved>(dst | src2) ||
               !IsAligned<kFirstSrcRegistersInvolved>(src1)) {
      return Unimplemented();
    }
    size_t vstart = GetCsr<CsrName::kVstart>();
    size_t vl = GetCsr<CsrName::kVl>();
    SetCsr<CsrName::kVstart>(0);
    // When vstart >= vl, there are no body elements, and no elements are updated in any destination
    // vector register group, including that no tail elements are updated with agnostic values.
    if (vstart >= vl) [[unlikely]] {
      return;
    }
    auto mask = GetMaskForVectorOperations<vma>();
    for (size_t index = 0; index < kRegistersInvolved; index++) {
      SIMD128Register orig_result(state_->cpu.v[dst + index]);
      SIMD128Register arg1_low(state_->cpu.v[src1 + 2 * index]);
      SIMD128Register arg2_low(state_->cpu.v[src2 + index]);
      SIMD128Register intrinsic_result = std::get<0>(Intrinsic(arg1_low, arg2_low));

      if constexpr (kFirstSrcRegistersInvolved > 1) {
        SIMD128Register arg1_high(state_->cpu.v[src1 + 2 * index + 1]);
        SIMD128Register arg2_high(state_->cpu.v[src2 + index] >> 64);
        SIMD128Register result_high = std::get<0>(Intrinsic(arg1_high, arg2_high));
        intrinsic_result = std::get<0>(
            intrinsics::VMergeBottomHalfToTop<ElementType>(intrinsic_result, result_high));
      }

      auto result = VectorMasking<ElementType, vta, vma>(
          orig_result, intrinsic_result, vstart, vl, index, mask);
      state_->cpu.v[dst + index] = result.template Get<__uint128_t>();
    }
  }

  template <auto Intrinsic,
            typename DestElementType,
            const uint8_t kFactor,
            VectorRegisterGroupMultiplier vlmul,
            TailProcessing vta,
            auto vma>
  void OpVectorVXUnary0(uint8_t dst, uint8_t src) {
    static_assert(kFactor == 2 || kFactor == 4 || kFactor == 8);
    constexpr size_t kDestRegistersInvolved = NumberOfRegistersInvolved(vlmul);
    constexpr size_t kSourceRegistersInvolved = (kDestRegistersInvolved / kFactor) ?: 1;
    if (!IsAligned<kDestRegistersInvolved>(dst) || !IsAligned<kSourceRegistersInvolved>(src)) {
      return Unimplemented();
    }
    int vstart = GetCsr<CsrName::kVstart>();
    int vl = GetCsr<CsrName::kVl>();
    // When vstart >= vl, there are no body elements, and no elements are updated in any destination
    // vector register group, including that no tail elements are updated with agnostic values.
    if (vstart >= vl) [[unlikely]] {
      SetCsr<CsrName::kVstart>(0);
      return;
    }
    auto mask = GetMaskForVectorOperations<vma>();
    for (size_t dst_index = 0; dst_index < kDestRegistersInvolved; dst_index++) {
      size_t src_index = dst_index / kFactor;
      size_t src_elem = dst_index % kFactor;
      SIMD128Register result{state_->cpu.v[dst + dst_index]};
      SIMD128Register arg{state_->cpu.v[src + src_index] >> ((128 / kFactor) * src_elem)};

      result = VectorMasking<DestElementType, vta, vma>(
          result, std::get<0>(Intrinsic(arg)), vstart, vl, dst_index, mask);
      state_->cpu.v[dst + dst_index] = result.Get<__uint128_t>();
    }
    SetCsr<CsrName::kVstart>(0);
  }

  template <auto Intrinsic,
            typename ElementType,
            VectorRegisterGroupMultiplier vlmul,
            TailProcessing vta,
            auto vma>
  void OpVectorvxv(uint8_t dst, uint8_t src1, ElementType arg2) {
    return OpVectorvxv<Intrinsic, ElementType, NumberOfRegistersInvolved(vlmul), vta, vma>(
        dst, src1, arg2);
  }

  template <auto Intrinsic,
            typename ElementType,
            size_t kRegistersInvolved,
            TailProcessing vta,
            auto vma>
  void OpVectorvxv(uint8_t dst, uint8_t src1, ElementType arg2) {
    if (!IsAligned<kRegistersInvolved>(dst | src1)) {
      return Unimplemented();
    }
    size_t vstart = GetCsr<CsrName::kVstart>();
    size_t vl = GetCsr<CsrName::kVl>();
    SetCsr<CsrName::kVstart>(0);
    // When vstart >= vl, there are no body elements, and no elements are updated in any destination
    // vector register group, including that no tail elements are updated with agnostic values.
    if (vstart >= vl) [[unlikely]] {
      return;
    }
    auto mask = GetMaskForVectorOperations<vma>();
    for (size_t index = 0; index < kRegistersInvolved; ++index) {
      SIMD128Register result(state_->cpu.v[dst + index]);
      SIMD128Register arg1(state_->cpu.v[src1 + index]);
      result = VectorMasking<ElementType, vta, vma>(
          result, std::get<0>(Intrinsic(arg1, arg2, result)), vstart, vl, index, mask);
      state_->cpu.v[dst + index] = result.Get<__uint128_t>();
    }
  }

  template <auto Intrinsic,
            typename ElementType,
            VectorRegisterGroupMultiplier vlmul,
            TailProcessing vta,
            auto vma,
            typename... DstMaskType>
  void OpVectorx(uint8_t dst, ElementType arg2, DstMaskType... dst_mask) {
    return OpVectorx<Intrinsic, ElementType, NumberOfRegistersInvolved(vlmul), vta, vma>(
        dst, arg2, dst_mask...);
  }

  template <auto Intrinsic,
            typename ElementType,
            size_t kRegistersInvolved,
            TailProcessing vta,
            auto vma,
            typename... DstMaskType>
  void OpVectorx(uint8_t dst, ElementType arg2, DstMaskType... dst_mask) {
    static_assert(sizeof...(dst_mask) <= 1);
    if (!IsAligned<kRegistersInvolved>(dst | (dst_mask | ... | 0))) {
      return Unimplemented();
    }
    size_t vstart = GetCsr<CsrName::kVstart>();
    size_t vl = GetCsr<CsrName::kVl>();
    SetCsr<CsrName::kVstart>(0);
    // When vstart >= vl, there are no body elements, and no elements are updated in any destination
    // vector register group, including that no tail elements are updated with agnostic values.
    if (vstart >= vl) [[unlikely]] {
      return;
    }
    auto mask = GetMaskForVectorOperations<vma>();
    for (size_t index = 0; index < kRegistersInvolved; ++index) {
      SIMD128Register result(state_->cpu.v[dst + index]);
      SIMD128Register result_mask;
      if constexpr (sizeof...(DstMaskType) == 0) {
        result_mask.Set(state_->cpu.v[dst + index]);
      } else {
        uint8_t dst_mask_unpacked[1] = {dst_mask...};
        result_mask.Set(state_->cpu.v[dst_mask_unpacked[0] + index]);
      }
      result = VectorMasking<ElementType, vta, vma>(
          result, std::get<0>(Intrinsic(arg2)), result_mask, vstart, vl, index, mask);
      state_->cpu.v[dst + index] = result.Get<__uint128_t>();
    }
  }

  template <typename ElementType, VectorRegisterGroupMultiplier vlmul, TailProcessing vta, auto vma>
  void OpVectorslideup(uint8_t dst, uint8_t src, Register offset) {
    return OpVectorslideup<ElementType, NumberOfRegistersInvolved(vlmul), vta, vma>(
        dst, src, offset);
  }

  template <typename ElementType, size_t kRegistersInvolved, TailProcessing vta, auto vma>
  void OpVectorslideup(uint8_t dst, uint8_t src, Register offset) {
    constexpr size_t kElementsPerRegister = 16 / sizeof(ElementType);
    if (!IsAligned<kRegistersInvolved>(dst | src)) {
      return Unimplemented();
    }
    // Source and destination must not intersect.
    if (dst < (src + kRegistersInvolved) && src < (dst + kRegistersInvolved)) {
      return Unimplemented();
    }
    size_t vstart = GetCsr<CsrName::kVstart>();
    size_t vl = GetCsr<CsrName::kVl>();
    SetCsr<CsrName::kVstart>(0);
    if (vstart >= vl) [[unlikely]] {
      // From 16.3: For all of the [slide instructions], if vstart >= vl, the
      // instruction performs no operation and leaves the destination vector
      // register unchanged.
      return;
    }
    auto mask = GetMaskForVectorOperations<vma>();
    // The slideup operation leaves Elements 0 through MAX(vstart, OFFSET) unchanged.
    const size_t start_elem_index = std::max<Register>(vstart, offset);

    // From 16.3.1: Destination elements OFFSET through vl-1 are written if
    // unmasked and if OFFSET < vl.
    // However if OFFSET > vl, we still need to apply the tail policy (as
    // clarified in https://github.com/riscv/riscv-v-spec/issues/263). Given
    // that OFFSET could be well past vl we start at vl rather than OFFSET in
    // that case.
    for (size_t index = std::min(start_elem_index, vl) / kElementsPerRegister;
         index < kRegistersInvolved;
         ++index) {
      SIMD128Register result(state_->cpu.v[dst + index]);

      // Arguments falling before the input group correspond to the first offset-amount
      // result elements, which must remain undisturbed. We zero-initialize them here,
      // but their values are eventually ignored by vstart masking in VectorMasking.
      ssize_t first_arg_disp = index - 1 - offset / kElementsPerRegister;
      SIMD128Register arg1 =
          (first_arg_disp < 0) ? SIMD128Register{0} : state_->cpu.v[src + first_arg_disp];
      SIMD128Register arg2 =
          (first_arg_disp + 1 < 0) ? SIMD128Register{0} : state_->cpu.v[src + first_arg_disp + 1];

      result =
          VectorMasking<ElementType, vta, vma>(result,
                                               std::get<0>(intrinsics::VectorSlideUp<ElementType>(
                                                   offset % kElementsPerRegister, arg1, arg2)),
                                               start_elem_index,
                                               vl,
                                               index,
                                               mask);
      state_->cpu.v[dst + index] = result.Get<__uint128_t>();
    }
  }

  template <typename ElementType, VectorRegisterGroupMultiplier vlmul, TailProcessing vta, auto vma>
  void OpVectorslidedown(uint8_t dst, uint8_t src, Register offset) {
    return OpVectorslidedown<ElementType, NumberOfRegistersInvolved(vlmul), vta, vma>(
        dst, src, offset);
  }

  template <typename ElementType, size_t kRegistersInvolved, TailProcessing vta, auto vma>
  void OpVectorslidedown(uint8_t dst, uint8_t src, Register offset) {
    constexpr size_t kElementsPerRegister = 16 / sizeof(ElementType);
    if (!IsAligned<kRegistersInvolved>(dst | src)) {
      return Unimplemented();
    }
    // Source and destination must not intersect.
    if (dst < (src + kRegistersInvolved) && src < (dst + kRegistersInvolved)) {
      return Unimplemented();
    }
    size_t vstart = GetCsr<CsrName::kVstart>();
    size_t vl = GetCsr<CsrName::kVl>();
    SetCsr<CsrName::kVstart>(0);
    if (vstart >= vl) [[unlikely]] {
      // From 16.3: For all of the [slide instructions], if vstart >= vl, the
      // instruction performs no operation and leaves the destination vector
      // register unchanged.
      return;
    }
    auto mask = GetMaskForVectorOperations<vma>();
    for (size_t index = 0; index < kRegistersInvolved; ++index) {
      SIMD128Register result(state_->cpu.v[dst + index]);

      size_t first_arg_disp = index + offset / kElementsPerRegister;
      SIMD128Register arg1 = (first_arg_disp >= kRegistersInvolved)
                                 ? SIMD128Register{0}
                                 : state_->cpu.v[src + first_arg_disp];
      SIMD128Register arg2 = (first_arg_disp + 1 >= kRegistersInvolved)
                                 ? SIMD128Register{0}
                                 : state_->cpu.v[src + first_arg_disp + 1];

      result =
          VectorMasking<ElementType, vta, vma>(result,
                                               std::get<0>(intrinsics::VectorSlideDown<ElementType>(
                                                   offset % kElementsPerRegister, arg1, arg2)),
                                               vstart,
                                               vl,
                                               index,
                                               mask);
      state_->cpu.v[dst + index] = result.Get<__uint128_t>();
    }
  }

  // Helper function needed to generate bitmak result from non-bitmask inputs.
  // We are processing between 1 and 8 registers here and each register produces between 2 bits
  // (for 64 bit inputs) and 16 bits (for 8 bit inputs) bitmasks which are then combined into
  // final result (between 2 and 128 bits long).
  // Note that we are not handling tail here! These bits remain undefined and should be handled
  // later.
  // TODO(b/317757595): Add separate tests to verify the logic.
  template <typename ElementType, size_t kRegistersInvolved, typename Intrinsic>
  SIMD128Register CollectBitmaskResult(Intrinsic intrinsic) {
    // We employ two distinct tactics to handle all possibilities:
    //   1. For 8bit/16bit types we get full UInt8/UInt16 result and thus use SIMD128Register.Set.
    //   2. For 32bit/64bit types we only get 2bit or 4bit from each call and thus need to use
    //      shifts to accumulate the result.
    //      But since each of up to 8 results is at most 4bits total bitmask is 32bit (or less).
    std::conditional_t<sizeof(ElementType) < sizeof(UInt32), SIMD128Register, UInt32>
        bitmask_result{};
    for (UInt32 index = UInt32{0}; index < UInt32(kRegistersInvolved); index += UInt32{1}) {
      const auto [raw_result] =
          intrinsics::SimdMaskToBitMask<ElementType>(std::get<0>(intrinsic(index)));
      if constexpr (sizeof(ElementType) < sizeof(Int32)) {
        bitmask_result.Set(raw_result, index);
      } else {
        constexpr UInt32 kElemNum =
            UInt32{static_cast<uint32_t>((sizeof(SIMD128Register) / sizeof(ElementType)))};
        bitmask_result |= UInt32(UInt8(raw_result)) << (index * kElemNum);
      }
    }
    return SIMD128Register(bitmask_result);
  }

  void Nop() {}

  void Unimplemented() {
    UndefinedInsn(GetInsnAddr());
    // If there is a guest handler registered for SIGILL we'll delay its processing until the next
    // sync point (likely the main dispatching loop) due to enabled pending signals. Thus we must
    // ensure that insn_addr isn't automatically advanced in FinalizeInsn.
    exception_raised_ = true;
  }

  //
  // Guest state getters/setters.
  //

  Register GetReg(uint8_t reg) const {
    CheckRegIsValid(reg);
    return state_->cpu.x[reg];
  }

  Register GetRegOrZero(uint8_t reg) { return reg == 0 ? 0 : GetReg(reg); }

  void SetReg(uint8_t reg, Register value) {
    if (exception_raised_) {
      // Do not produce side effects.
      return;
    }
    CheckRegIsValid(reg);
    state_->cpu.x[reg] = value;
  }

  void SetRegOrIgnore(uint8_t reg, Register value) {
    if (reg != 0) {
      SetReg(reg, value);
    }
  }

  FpRegister GetFpReg(uint8_t reg) const {
    CheckFpRegIsValid(reg);
    return state_->cpu.f[reg];
  }

  template <typename FloatType>
  FpRegister GetFRegAndUnboxNan(uint8_t reg);

  template <typename FloatType>
  void NanBoxAndSetFpReg(uint8_t reg, FpRegister value);

  //
  // Various helper methods.
  //

  template <CsrName kName>
  [[nodiscard]] Register GetCsr() const {
    return state_->cpu.*CsrFieldAddr<kName>;
  }

  template <CsrName kName>
  void SetCsr(Register arg) {
    if (exception_raised_) {
      return;
    }
    state_->cpu.*CsrFieldAddr<kName> = arg & kCsrMask<kName>;
  }

  [[nodiscard]] uint64_t GetImm(uint64_t imm) const { return imm; }

  [[nodiscard]] Register Copy(Register value) const { return value; }

  [[nodiscard]] GuestAddr GetInsnAddr() const { return state_->cpu.insn_addr; }

  void FinalizeInsn(uint8_t insn_len) {
    if (!branch_taken_ && !exception_raised_) {
      state_->cpu.insn_addr += insn_len;
    }
  }

#include "berberis/intrinsics/interpreter_intrinsics_hooks-inl.h"

 private:
  template <typename DataType>
  Register Load(const void* ptr) {
    static_assert(std::is_integral_v<DataType>);
    CHECK(!exception_raised_);
    FaultyLoadResult result = FaultyLoad(ptr, sizeof(DataType));
    if (result.is_fault) {
      exception_raised_ = true;
      return {};
    }
    return static_cast<DataType>(result.value);
  }

  template <typename DataType>
  void Store(void* ptr, uint64_t data) {
    static_assert(std::is_integral_v<DataType>);
    CHECK(!exception_raised_);
    exception_raised_ = FaultyStore(ptr, sizeof(DataType), data);
  }

  void CheckShamtIsValid(int8_t shamt) const {
    CHECK_GE(shamt, 0);
    CHECK_LT(shamt, 64);
  }

  void CheckShamt32IsValid(int8_t shamt) const {
    CHECK_GE(shamt, 0);
    CHECK_LT(shamt, 32);
  }

  void CheckRegIsValid(uint8_t reg) const {
    CHECK_GT(reg, 0u);
    CHECK_LE(reg, std::size(state_->cpu.x));
  }

  void CheckFpRegIsValid(uint8_t reg) const { CHECK_LT(reg, std::size(state_->cpu.f)); }

  template <bool kUseMasking>
  std::conditional_t<kUseMasking, SIMD128Register, intrinsics::NoInactiveProcessing>
  GetMaskForVectorOperationsIfNeeded() {
    if constexpr (kUseMasking) {
      return {state_->cpu.v[0]};
    } else {
      return intrinsics::NoInactiveProcessing{};
    }
  }

  template <auto vma>
  std::conditional_t<std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>,
                     intrinsics::NoInactiveProcessing,
                     SIMD128Register>
  GetMaskForVectorOperations() {
    return GetMaskForVectorOperationsIfNeeded<
        !std::is_same_v<decltype(vma), intrinsics::NoInactiveProcessing>>();
  }

  template <typename ElementType,
            TailProcessing vta,
            auto vma = intrinsics::NoInactiveProcessing{},
            typename MaskType = intrinsics::NoInactiveProcessing>
  SIMD128Register VectorMasking(SIMD128Register dest,
                                SIMD128Register result,
                                size_t vstart,
                                size_t vl,
                                size_t index,
                                MaskType mask = intrinsics::NoInactiveProcessing{}) {
    return std::get<0>(intrinsics::VectorMasking<ElementType, vta, vma>(
        dest,
        result,
        vstart - index * (sizeof(SIMD128Register) / sizeof(ElementType)),
        vl - index * (sizeof(SIMD128Register) / sizeof(ElementType)),
        std::get<0>(intrinsics::MaskForRegisterInSequence<ElementType>(mask, index))));
  }

  template <typename ElementType,
            TailProcessing vta,
            auto vma = intrinsics::NoInactiveProcessing{},
            typename MaskType = intrinsics::NoInactiveProcessing>
  SIMD128Register VectorMasking(SIMD128Register dest,
                                SIMD128Register result,
                                SIMD128Register result_mask,
                                size_t vstart,
                                size_t vl,
                                size_t index,
                                MaskType mask = intrinsics::NoInactiveProcessing{}) {
    return std::get<0>(intrinsics::VectorMasking<ElementType, vta, vma>(
        dest,
        result,
        result_mask,
        vstart - index * (sizeof(SIMD128Register) / sizeof(ElementType)),
        vl - index * (sizeof(SIMD128Register) / sizeof(ElementType)),
        std::get<0>(intrinsics::MaskForRegisterInSequence<ElementType>(mask, index))));
  }

  ThreadState* state_;
  bool branch_taken_;
  // This flag is set by illegal instructions and faulted memory accesses. The former must always
  // stop the playback of the current instruction, so we don't need to do anything special. The
  // latter may result in having more operations with side effects called before the end of the
  // current instruction:
  //   Load (faulted)    -> SetReg
  //   LoadFp (faulted)  -> NanBoxAndSetFpReg
  // If an exception is raised before these operations, we skip them. For all other operations with
  // side-effects we check that this flag is never raised.
  bool exception_raised_;
};

template <>
[[nodiscard]] Interpreter::Register inline Interpreter::GetCsr<CsrName::kFCsr>() const {
  return FeGetExceptions() | (state_->cpu.frm << 5);
}

template <>
[[nodiscard]] Interpreter::Register inline Interpreter::GetCsr<CsrName::kFFlags>() const {
  return FeGetExceptions();
}

template <>
[[nodiscard]] Interpreter::Register inline Interpreter::GetCsr<CsrName::kVlenb>() const {
  return 16;
}

template <>
[[nodiscard]] Interpreter::Register inline Interpreter::GetCsr<CsrName::kVxrm>() const {
  return state_->cpu.*CsrFieldAddr<CsrName::kVcsr> & 0b11;
}

template <>
[[nodiscard]] Interpreter::Register inline Interpreter::GetCsr<CsrName::kVxsat>() const {
  return state_->cpu.*CsrFieldAddr<CsrName::kVcsr> >> 2;
}

template <>
void inline Interpreter::SetCsr<CsrName::kFCsr>(Register arg) {
  CHECK(!exception_raised_);
  FeSetExceptions(arg & 0b1'1111);
  arg = (arg >> 5) & kCsrMask<CsrName::kFrm>;
  state_->cpu.frm = arg;
  FeSetRound(arg);
}

template <>
void inline Interpreter::SetCsr<CsrName::kFFlags>(Register arg) {
  CHECK(!exception_raised_);
  FeSetExceptions(arg & 0b1'1111);
}

template <>
void inline Interpreter::SetCsr<CsrName::kFrm>(Register arg) {
  CHECK(!exception_raised_);
  arg &= kCsrMask<CsrName::kFrm>;
  state_->cpu.frm = arg;
  FeSetRound(arg);
}

template <>
void inline Interpreter::SetCsr<CsrName::kVxrm>(Register arg) {
  CHECK(!exception_raised_);
  state_->cpu.*CsrFieldAddr<CsrName::kVcsr> =
      (state_->cpu.*CsrFieldAddr<CsrName::kVcsr> & 0b100) | (arg & 0b11);
}

template <>
void inline Interpreter::SetCsr<CsrName::kVxsat>(Register arg) {
  CHECK(!exception_raised_);
  state_->cpu.*CsrFieldAddr<CsrName::kVcsr> =
      (state_->cpu.*CsrFieldAddr<CsrName::kVcsr> & 0b11) | ((arg & 0b1) << 2);
}

template <>
[[nodiscard]] Interpreter::FpRegister inline Interpreter::GetFRegAndUnboxNan<Interpreter::Float32>(
    uint8_t reg) {
  CheckFpRegIsValid(reg);
  FpRegister value = state_->cpu.f[reg];
  return UnboxNan<Float32>(value);
}

template <>
[[nodiscard]] Interpreter::FpRegister inline Interpreter::GetFRegAndUnboxNan<Interpreter::Float64>(
    uint8_t reg) {
  CheckFpRegIsValid(reg);
  return state_->cpu.f[reg];
}

template <>
void inline Interpreter::NanBoxAndSetFpReg<Interpreter::Float32>(uint8_t reg, FpRegister value) {
  if (exception_raised_) {
    // Do not produce side effects.
    return;
  }
  CheckFpRegIsValid(reg);
  state_->cpu.f[reg] = NanBox<Float32>(value);
}

template <>
void inline Interpreter::NanBoxAndSetFpReg<Interpreter::Float64>(uint8_t reg, FpRegister value) {
  if (exception_raised_) {
    // Do not produce side effects.
    return;
  }
  CheckFpRegIsValid(reg);
  state_->cpu.f[reg] = value;
}

#ifdef BERBERIS_RISCV64_INTERPRETER_SEPARATE_INSTANTIATION_OF_VECTOR_OPERATIONS
template <>
extern void SemanticsPlayer<Interpreter>::OpVector(const Decoder::VLoadIndexedArgs& args);
template <>
extern void SemanticsPlayer<Interpreter>::OpVector(const Decoder::VLoadStrideArgs& args);
template <>
extern void SemanticsPlayer<Interpreter>::OpVector(const Decoder::VLoadUnitStrideArgs& args);
template <>
extern void SemanticsPlayer<Interpreter>::OpVector(const Decoder::VOpFVfArgs& args);
template <>
extern void SemanticsPlayer<Interpreter>::OpVector(const Decoder::VOpFVvArgs& args);
template <>
extern void SemanticsPlayer<Interpreter>::OpVector(const Decoder::VOpIViArgs& args);
template <>
extern void SemanticsPlayer<Interpreter>::OpVector(const Decoder::VOpIVvArgs& args);
template <>
extern void SemanticsPlayer<Interpreter>::OpVector(const Decoder::VOpIVxArgs& args);
template <>
extern void SemanticsPlayer<Interpreter>::OpVector(const Decoder::VOpMVvArgs& args);
template <>
extern void SemanticsPlayer<Interpreter>::OpVector(const Decoder::VOpMVxArgs& args);
template <>
extern void SemanticsPlayer<Interpreter>::OpVector(const Decoder::VStoreIndexedArgs& args);
template <>
extern void SemanticsPlayer<Interpreter>::OpVector(const Decoder::VStoreStrideArgs& args);
template <>
extern void SemanticsPlayer<Interpreter>::OpVector(const Decoder::VStoreUnitStrideArgs& args);
#endif

}  // namespace berberis
