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

#ifndef BERBERIS_GUEST_STATE_GUEST_STATE_ARCH_H_
#define BERBERIS_GUEST_STATE_GUEST_STATE_ARCH_H_

#include <array>
#include <atomic>
#include <cstdint>
#include <type_traits>

#include "berberis/base/config.h"
#include "berberis/base/dependent_false.h"
#include "berberis/base/macros.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state_opaque.h"
#include "native_bridge_support/riscv64/guest_state/guest_state_cpu_state.h"

namespace berberis {

enum class CsrName {
  kFFlags = 0b00'00'0000'0001,
  kFrm = 0b00'00'0000'0010,
  kFCsr = 0b00'00'0000'0011,
  kVstart = 0b00'00'0000'1000,
  kVxsat = 0b00'00'0000'1001,
  kVxrm = 0b00'00'0000'1010,
  kVcsr = 0b00'00'0000'1111,
  kVl = 0b11'00'0010'0000,
  kVtype = 0b11'00'0010'0001,
  kVlenb = 0b11'00'0010'0010,
  kMaxValue = 0b11'11'1111'1111,
};

// Only for CSRs listed below helper defines would be defined.
// Define BERBERIS_RISV64_PROCESS_CSR before use. It would receive three arguments:
//   • CamelCaseName, suitable for functions and enums.
//   • snake_case_name, suitable for fields of data structures.
//   • mask value, suitable for masking operations during write to register.
#define BERBERIS_RISV64_PROCESS_SUPPORTED_CSRS            \
  BERBERIS_RISV64_PROCESS_CSR(Frm, frm, 0b111)            \
  BERBERIS_RISV64_PROCESS_CSR(Vstart, vstart, 0b01111111) \
  BERBERIS_RISV64_PROCESS_CSR(Vcsr, vcsr, 0b111)          \
  BERBERIS_RISV64_PROCESS_CSR(Vl, vl, 0b11111111)         \
  BERBERIS_RISV64_PROCESS_CSR(                            \
      Vtype,                                              \
      vtype,                                              \
      0b1000'0000'0000'0000'0000'0000'0000'0000'0000'0000'0000'0000'0000'0000'1'1'111'111)
// Only CSRs listed below will be processed. All others are treated as undefined instruction.
// Define BERBERIS_RISV64_PROCESS_CSR before use. It would receive three arguments (see above).
// Define BERBERIS_RISV64_PROCESS_NOSTORAGE_CSR. It would receive one argument.
#define BERBERIS_RISV64_PROCESS_ALL_SUPPORTED_CSRS                                               \
  BERBERIS_RISV64_PROCESS_SUPPORTED_CSRS                                                         \
  BERBERIS_RISV64_PROCESS_NOSTORAGE_CSR(FCsr), BERBERIS_RISV64_PROCESS_NOSTORAGE_CSR(FFlags),    \
      BERBERIS_RISV64_PROCESS_NOSTORAGE_CSR(Vxsat), BERBERIS_RISV64_PROCESS_NOSTORAGE_CSR(Vxrm), \
      BERBERIS_RISV64_PROCESS_NOSTORAGE_CSR(Vlenb)

static_assert(std::is_standard_layout_v<CPUState>);

constexpr uint32_t kNumGuestRegs = std::size(CPUState{}.x);
constexpr uint32_t kNumGuestFpRegs = std::size(CPUState{}.f);

template <uint8_t kIndex>
inline uint64_t GetXReg(const CPUState& state) {
  static_assert(kIndex > 0);
  static_assert(kIndex < std::size(CPUState{}.x));
  return state.x[kIndex];
}

template <uint8_t kIndex>
inline void SetXReg(CPUState& state, uint64_t val) {
  static_assert(kIndex > 0);
  static_assert(kIndex < std::size(CPUState{}.x));
  state.x[kIndex] = val;
}

template <uint8_t kIndex>
inline uint64_t GetFReg(const CPUState& state) {
  static_assert((kIndex) < std::size(CPUState{}.f));
  return state.f[kIndex];
}

template <uint8_t kIndex>
inline void SetFReg(CPUState& state, uint64_t val) {
  static_assert((kIndex) < std::size(CPUState{}.f));
  state.f[kIndex] = val;
}

enum class RegisterType {
  kReg,
  kFpReg,
};

template <RegisterType register_type, uint8_t kIndex>
inline auto GetReg(const CPUState& state) {
  if constexpr (register_type == RegisterType::kReg) {
    return GetXReg<kIndex>(state);
  } else if constexpr (register_type == RegisterType::kFpReg) {
    return GetFReg<kIndex>(state);
  } else {
    static_assert(kDependentValueFalse<register_type>, "Unsupported register type");
  }
}

template <RegisterType register_type, uint8_t kIndex, typename Register>
inline auto SetReg(CPUState& state, Register val) {
  if constexpr (register_type == RegisterType::kReg) {
    return SetXReg<kIndex>(state, val);
  } else if constexpr (register_type == RegisterType::kFpReg) {
    return SetFReg<kIndex>(state, val);
  } else {
    static_assert(kDependentValueFalse<register_type>, "Unsupported register type");
  }
}

class GuestThread;

struct ThreadState {
  CPUState cpu;

  // Scratch space for x87 use and MXCSR.
  // These operations can only read/write values from memory for historical reasons.
  alignas(config::kScratchAreaAlign) uint8_t intrinsics_scratch_area[config::kScratchAreaSize];

  // Guest thread pointer.
  GuestThread* thread;

  // Keep pending signals status here for fast checking in generated code.
  // Uses enum values from PendingSignalsStatus.
  // TODO(b/28058920): Refactor into GuestThread.
  std::atomic<uint_least8_t> pending_signals_status;

  GuestThreadResidence residence;

  // Arbitrary per-thread data added by instrumentation.
  void* instrument_data;
};

template <CsrName>
class CsrField;

#define BERBERIS_RISV64_PROCESS_CSR(EnumName, field_name, field_mask)        \
  template <>                                                                \
  class CsrField<CsrName::k##EnumName> {                                     \
   public:                                                                   \
    using Type = decltype(std::declval<CPUState>().field_name);              \
    static constexpr Type(CPUState::*Addr) = &CPUState::field_name;          \
    static constexpr size_t kOffset = offsetof(ThreadState, cpu.field_name); \
    static constexpr Type kMask{field_mask};                                 \
  };

BERBERIS_RISV64_PROCESS_SUPPORTED_CSRS
#undef BERBERIS_RISV64_PROCESS_CSR

template <CsrName kName>
using CsrFieldType = typename CsrField<kName>::Type;

template <CsrName kName>
inline constexpr CsrFieldType<kName>(CPUState::*CsrFieldAddr) = CsrField<kName>::Addr;

template <CsrName... kName, typename Processor>
bool ProcessCsrNameAsTemplateParameterImpl(CsrName name, Processor& processor) {
  return ((kName == name ? processor.template operator()<kName>(), true : false) || ...);
}

template <CsrName kName>
inline constexpr size_t kCsrFieldOffset = CsrField<kName>::kOffset;

template <CsrName kName>
inline constexpr CsrFieldType<kName> kCsrMask = CsrField<kName>::kMask;

inline constexpr bool CsrWritable(CsrName name) {
  return (static_cast<typename std::underlying_type_t<CsrName>>(name) & 0b11'00'0000'0000) !=
         0b11'00'0000'0000;
}

template <typename Processor>
bool ProcessCsrNameAsTemplateParameter(CsrName name, Processor& processor) {
#define BERBERIS_RISV64_PROCESS_CSR(EnumName, field_name, field_mask) CsrName::k##EnumName,
#define BERBERIS_RISV64_PROCESS_NOSTORAGE_CSR(EnumName) CsrName::k##EnumName
  return ProcessCsrNameAsTemplateParameterImpl<BERBERIS_RISV64_PROCESS_ALL_SUPPORTED_CSRS>(
      name, processor);
#undef BERBERIS_RISV64_PROCESS_NOSTORAGE_CSR
#undef BERBERIS_RISV64_PROCESS_CSR
}

#undef BERBERIS_RISV64_PROCESS_ALL_SUPPORTED_CSRS
#undef BERBERIS_RISV64_PROCESS_SUPPORTED_CSRS

// The ABI names come from
// https://github.com/riscv-non-isa/riscv-elf-psabi-doc/blob/master/riscv-cc.adoc.

// Integer register ABI names.

constexpr uint8_t RA = 1;    // Return address - caller saved.
constexpr uint8_t SP = 2;    // Stack pointer - callee saved.
constexpr uint8_t GP = 3;    // Global pointer.
constexpr uint8_t TP = 4;    // Thread pointer.
constexpr uint8_t T0 = 5;    // Temporary register 0 - caller saved.
constexpr uint8_t T1 = 6;    // Temporary register 1 - caller saved.
constexpr uint8_t T2 = 7;    // Temporary register 2 - caller saved.
constexpr uint8_t FP = 8;    // Frame pointer - callee saved.
constexpr uint8_t S0 = 8;    // Saved register 0 - callee saved.
constexpr uint8_t S1 = 9;    // Saved register 1 - callee saved.
constexpr uint8_t A0 = 10;   // Argument register / return value 0 - caller saved.
constexpr uint8_t A1 = 11;   // Argument register / return value 1 - caller saved.
constexpr uint8_t A2 = 12;   // Argument register 2 - caller saved.
constexpr uint8_t A3 = 13;   // Argument register 3 - caller saved.
constexpr uint8_t A4 = 14;   // Argument register 4 - caller saved.
constexpr uint8_t A5 = 15;   // Argument register 5 - caller saved.
constexpr uint8_t A6 = 16;   // Argument register 6 - caller saved.
constexpr uint8_t A7 = 17;   // Argument register 7 - caller saved.
constexpr uint8_t S2 = 18;   // Saved register 2 - callee saved.
constexpr uint8_t S3 = 19;   // Saved register 3 - callee saved.
constexpr uint8_t S4 = 20;   // Saved register 4 - callee saved.
constexpr uint8_t S5 = 21;   // Saved register 5 - callee saved.
constexpr uint8_t S6 = 22;   // Saved register 6 - callee saved.
constexpr uint8_t S7 = 23;   // Saved register 7 - callee saved.
constexpr uint8_t S8 = 24;   // Saved register 8 - callee saved.
constexpr uint8_t S9 = 25;   // Saved register 9 - callee saved.
constexpr uint8_t S10 = 26;  // Saved register 10 - callee saved.
constexpr uint8_t S11 = 27;  // Saved register 11 - callee saved.
constexpr uint8_t T3 = 28;   // Temporary register 3 - caller saved.
constexpr uint8_t T4 = 29;   // Temporary register 4 - caller saved.
constexpr uint8_t T5 = 30;   // Temporary register 5 - caller saved.
constexpr uint8_t T6 = 31;   // Temporary register 6 - caller saved.

// Floating point register ABI names.

constexpr uint8_t FT0 = 0;    // FP Temporary register 0 - caller saved.
constexpr uint8_t FT1 = 1;    // FP Temporary register 1 - caller saved.
constexpr uint8_t FT2 = 2;    // FP Temporary register 2 - caller saved.
constexpr uint8_t FT3 = 3;    // FP Temporary register 3 - caller saved.
constexpr uint8_t FT4 = 4;    // FP Temporary register 4 - caller saved.
constexpr uint8_t FT5 = 5;    // FP Temporary register 5 - caller saved.
constexpr uint8_t FT6 = 6;    // FP Temporary register 6 - caller saved.
constexpr uint8_t FT7 = 7;    // FP Temporary register 7 - caller saved.
constexpr uint8_t FS0 = 8;    // FP Saved register 0 - callee saved.
constexpr uint8_t FS1 = 9;    // FP Saved register 1 - callee saved.
constexpr uint8_t FA0 = 10;   // FP Argument register / return value 0 - caller saved.
constexpr uint8_t FA1 = 11;   // FP Argument register / return value 1 - caller saved.
constexpr uint8_t FA2 = 12;   // FP Argument register 2 - caller saved.
constexpr uint8_t FA3 = 13;   // FP Argument register 3 - caller saved.
constexpr uint8_t FA4 = 14;   // FP Argument register 4 - caller saved.
constexpr uint8_t FA5 = 15;   // FP Argument register 5 - caller saved.
constexpr uint8_t FA6 = 16;   // FP Argument register 6 - caller saved.
constexpr uint8_t FA7 = 17;   // FP Argument register 7 - caller saved.
constexpr uint8_t FS2 = 18;   // FP Saved register 2 - calle saved.
constexpr uint8_t FS3 = 19;   // FP Saved register 3 - callee saved.
constexpr uint8_t FS4 = 20;   // FP Saved register 4 - callee saved.
constexpr uint8_t FS5 = 21;   // FP Saved register 5 - callee saved.
constexpr uint8_t FS6 = 22;   // FP Saved register 6 - callee saved.
constexpr uint8_t FS7 = 23;   // FP Saved register 7 - callee saved.
constexpr uint8_t FS8 = 24;   // FP Saved register 8 - callee saved.
constexpr uint8_t FS9 = 25;   // FP Saved register 9 - callee saved.
constexpr uint8_t FS10 = 26;  // FP Saved register 10 - callee saved.
constexpr uint8_t FS11 = 27;  // FP Saved register 11 - callee saved.
constexpr uint8_t FT8 = 28;   // FP Temporary register 8 - caller saved.
constexpr uint8_t FT9 = 29;   // FP Temporary register 9 - caller saved.
constexpr uint8_t FT10 = 30;  // FP Temporary register 10 - caller saved.
constexpr uint8_t FT11 = 31;  // FP Temporary register 11 - caller saved.

}  // namespace berberis

#endif  // BERBERIS_GUEST_STATE_GUEST_STATE_ARCH_H_
