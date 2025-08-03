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

#ifndef BERBERIS_HEAVY_OPTIMIZER_RISCV64_INLINE_INTRINSIC_H_
#define BERBERIS_HEAVY_OPTIMIZER_RISCV64_INLINE_INTRINSIC_H_

#include <cfenv>
#include <cstdint>
#include <tuple>
#include <type_traits>
#include <utility>
#include <variant>

#include "berberis/assembler/x86_64.h"
#include "berberis/backend/common/machine_ir.h"
#include "berberis/backend/x86_64/machine_insn_intrinsics.h"
#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir_builder.h"
#include "berberis/base/checks.h"
#include "berberis/base/config.h"
#include "berberis/base/dependent_false.h"
#include "berberis/intrinsics/common_to_x86/intrinsics_bindings.h"
#include "berberis/intrinsics/intrinsics.h"
#include "berberis/intrinsics/intrinsics_args.h"
#include "berberis/intrinsics/intrinsics_process_bindings.h"
#include "berberis/intrinsics/macro_assembler.h"
#include "berberis/runtime_primitives/platform.h"

#include "simd_register.h"

namespace berberis {

template <auto kFunc>
class InlineIntrinsic {
 public:
  template <typename ResType, typename FlagRegister, typename... ArgType>
  static bool TryInline(x86_64::MachineIRBuilder* /* builder */,
                        ResType /* result */,
                        FlagRegister /* flag_register */,
                        ArgType... /* args */) {
    // TODO(b/232598137) Implement intrinsics
    return false;
  }

  template <typename FlagRegister, typename... ArgType>
  static bool TryInline(x86_64::MachineIRBuilder* /* builder */,
                        FlagRegister /* flag_register */,
                        ArgType... /* args */) {
    // TODO(b/232598137) Implement intrinsics
    return false;
  }

 private:
  // Comparison of pointers which point to different functions is generally not a
  // constexpr since such functions can be merged in object code (comparing
  // pointers to the same function is constexpr). This helper compares them using
  // templates explicitly telling that we are not worried about such subtleties here.
  template <auto kFunction>
  class FunctionCompareTag;

  // Note, if we define it as a variable clang doesn't consider it a constexpr in TryInline().
  template <auto kOtherFunction>
  static constexpr bool IsTagEq() {
    return std::is_same_v<FunctionCompareTag<kFunc>, FunctionCompareTag<kOtherFunction>>;
  }
};

template <typename DestRegClass, typename SrcRegClass>
void Mov(x86_64::MachineIRBuilder* builder, MachineReg dest, MachineReg src) {
  using DestType = typename DestRegClass::Type;
  using SrcType = typename SrcRegClass::Type;
  constexpr const auto src_reg_class = SrcRegClass::template kRegClass<x86_64::MachineInsnX86_64>;
  if constexpr (std::is_integral_v<DestType>) {
    if constexpr (std::is_integral_v<SrcType>) {
      builder->Gen<PseudoCopy>(dest, src, src_reg_class.RegSize());
    } else if constexpr (SrcRegClass::kAsRegister == 'x') {
      if constexpr (src_reg_class.RegSize() == 4) {
        if (host_platform::kHasAVX) {
          builder->Gen<x86_64::VmovdRegXReg>(dest, src);
        } else {
          builder->Gen<x86_64::MovdRegXReg>(dest, src);
        }
      } else {
        static_assert(src_reg_class.RegSize() >= 8);
        if (host_platform::kHasAVX) {
          builder->Gen<x86_64::VmovqRegXReg>(dest, src);
        } else {
          builder->Gen<x86_64::MovqRegXReg>(dest, src);
        }
      }
    } else {
      static_assert(kDependentTypeFalse<std::tuple<DestRegClass, SrcRegClass>>);
    }
  } else if (DestRegClass::kAsRegister == 'x') {
    if constexpr (src_reg_class.RegSize() == 4) {
      if constexpr (std::is_integral_v<SrcType>) {
        if (host_platform::kHasAVX) {
          builder->Gen<x86_64::VmovdXRegReg>(dest, src);
        } else {
          builder->Gen<x86_64::MovdXRegReg>(dest, src);
        }
      } else if constexpr (SrcRegClass::kAsRegister == 'x') {
        builder->Gen<PseudoCopy>(dest, src, 16);
      } else {
        static_assert(kDependentTypeFalse<std::tuple<DestRegClass, SrcRegClass>>);
      }
    } else {
      static_assert(src_reg_class.RegSize() >= 8);
      if constexpr (std::is_integral_v<SrcType>) {
        if (host_platform::kHasAVX) {
          builder->Gen<x86_64::VmovqXRegReg>(dest, src);
        } else {
          builder->Gen<x86_64::MovqXRegReg>(dest, src);
        }
      } else if constexpr (SrcRegClass::kAsRegister == 'x') {
        builder->Gen<PseudoCopy>(dest, src, 16);
      } else {
        static_assert(kDependentTypeFalse<std::tuple<DestRegClass, SrcRegClass>>);
      }
    }
  }
}

template <typename DestRegClass, typename SrcReg>
void MovFromInput(x86_64::MachineIRBuilder* builder, MachineReg dest, SrcReg src) {
  if constexpr (std::is_same_v<SrcReg, SimdReg>) {
    Mov<DestRegClass, intrinsics::bindings::XmmReg>(builder, dest, src.machine_reg());
  } else {
    Mov<DestRegClass, intrinsics::bindings::GeneralReg64>(builder, dest, src);
  }
}
template <typename SrcRegClass, typename DestReg>
void MovToResult(x86_64::MachineIRBuilder* builder, DestReg dest, MachineReg src) {
  if constexpr (std::is_same_v<DestReg, SimdReg>) {
    Mov<intrinsics::bindings::XmmReg, SrcRegClass>(builder, dest.machine_reg(), src);
  } else {
    Mov<intrinsics::bindings::GeneralReg64, SrcRegClass>(builder, dest, src);
  }
}

template <auto kFunction, typename ResType, typename FlagRegister, typename... ArgType>
class TryBindingBasedInlineIntrinsicForHeavyOptimizer {
  template <auto kFunctionForFriend,
            typename ResTypeForFriend,
            typename FlagRegisterForFriend,
            typename... ArgTypeForFriend>
  friend bool TryInlineIntrinsicForHeavyOptimizer(x86_64::MachineIRBuilder* builder,
                                                  ResTypeForFriend result,
                                                  FlagRegisterForFriend flag_register,
                                                  ArgTypeForFriend... args);
  template <auto kFunctionForFriend, typename FlagRegisterForFriend, typename... ArgTypeForFriend>
  friend bool TryInlineIntrinsicForHeavyOptimizerVoid(x86_64::MachineIRBuilder* builder,
                                                      FlagRegisterForFriend flag_register,
                                                      ArgTypeForFriend... args);

  template <auto kFunc,
            typename Assembler_common_x86,
            typename Assembler_x86_64,
            typename MacroAssembler,
            typename Result,
            typename Callback,
            typename... Args>
  friend Result intrinsics::bindings::ProcessBindings(Callback callback,
                                                      Result def_result,
                                                      Args&&... args);

  template <
      auto kIntrinsicTemplateName,
      auto kMacroInstructionTemplateName,
      auto kMnemo,
      typename GetOpcode,
      intrinsics::bindings::CPUIDRestriction kCPUIDRestrictionTemplateValue,
      intrinsics::bindings::PreciseNanOperationsHandling kPreciseNanOperationsHandlingTemplateValue,
      bool kSideEffectsTemplateValue,
      typename... Types>
  friend class intrinsics::bindings::AsmCallInfo;

  TryBindingBasedInlineIntrinsicForHeavyOptimizer() = delete;
  TryBindingBasedInlineIntrinsicForHeavyOptimizer(
      const TryBindingBasedInlineIntrinsicForHeavyOptimizer&) = delete;
  TryBindingBasedInlineIntrinsicForHeavyOptimizer(
      TryBindingBasedInlineIntrinsicForHeavyOptimizer&&) = delete;
  TryBindingBasedInlineIntrinsicForHeavyOptimizer& operator=(
      const TryBindingBasedInlineIntrinsicForHeavyOptimizer&) = delete;
  TryBindingBasedInlineIntrinsicForHeavyOptimizer& operator=(
      TryBindingBasedInlineIntrinsicForHeavyOptimizer&&) = delete;

  TryBindingBasedInlineIntrinsicForHeavyOptimizer(x86_64::MachineIRBuilder* builder,
                                                  ResType result,
                                                  FlagRegister flag_register,
                                                  ArgType... args)
      : builder_(builder),
        result_{result},
        xmm_result_reg_{},
        flag_register_{flag_register},
        input_args_(std::tuple{args...}),
        success_(
            intrinsics::bindings::ProcessBindings<kFunction,
                                                  AssemblerX86<x86_64::Assembler>,
                                                  x86_64::Assembler,
                                                  std::tuple<MacroAssembler<x86_64::Assembler>>,
                                                  bool,
                                                  TryBindingBasedInlineIntrinsicForHeavyOptimizer&>(
                *this,
                false)) {}

  operator bool() { return success_; }

  // TODO(b/232598137) The MachineIR bindings for some macros can't be instantiated yet. This should
  // be removed once they're supported.
  template <typename AsmCallInfo,
            std::enable_if_t<AsmCallInfo::template kOpcode<MachineOpcode> ==
                                 MachineOpcode::kMachineOpUndefined,
                             bool> = true>
  std::optional<bool> /*ProcessBindingsClient*/ operator()(AsmCallInfo /* asm_call_info */) {
    return false;
  }

  template <typename AsmCallInfo,
            std::enable_if_t<AsmCallInfo::template kOpcode<MachineOpcode> !=
                                 MachineOpcode::kMachineOpUndefined,
                             bool> = true>
  std::optional<bool> /*ProcessBindingsClient*/ operator()(AsmCallInfo asm_call_info) {
    static_assert(std::is_same_v<decltype(kFunction), typename AsmCallInfo::IntrinsicType>);
    if constexpr (AsmCallInfo::kPreciseNanOperationsHandling !=
                  intrinsics::bindings::kNoNansOperation) {
      return false;
    }

    if constexpr (AsmCallInfo::kCPUIDRestriction == intrinsics::bindings::kHasAVX) {
      if (!host_platform::kHasAVX) {
        return false;
      }
    } else if constexpr (AsmCallInfo::kCPUIDRestriction == intrinsics::bindings::kHasBMI) {
      if (!host_platform::kHasBMI) {
        return false;
      }
    } else if constexpr (AsmCallInfo::kCPUIDRestriction == intrinsics::bindings::kHasLZCNT) {
      if (!host_platform::kHasLZCNT) {
        return false;
      }
    } else if constexpr (AsmCallInfo::kCPUIDRestriction == intrinsics::bindings::kHasPOPCNT) {
      if (!host_platform::kHasPOPCNT) {
        return false;
      }
    } else if constexpr (AsmCallInfo::kCPUIDRestriction ==
                         intrinsics::bindings::kNoCPUIDRestriction) {
      // No restrictions. Do nothing.
    } else {
      static_assert(berberis::kDependentValueFalse<AsmCallInfo::kCPUIDRestriction>);
    }

    // constructor_args_t here is used to generate a tuple of constructor args from the AsmCallInfo
    // bindings. The tuple parameter pack will be expanded by the tuple specialization on the
    // MachineInsn in machine_insn_intrinsics.h.
    using MachineInsn = typename AsmCallInfo::template MachineInsn<berberis::x86_64::MachineInsn,
                                                                   x86_64::constructor_args_t,
                                                                   MachineOpcode>;
    std::apply(MachineInsn::kGenFunc,
               std::tuple_cat(std::tuple<x86_64::MachineIRBuilder&>{*builder_},
                              UnwrapSimdReg(AsmCallInfo::template MakeTuplefromBindings<
                                            TryBindingBasedInlineIntrinsicForHeavyOptimizer&>(
                                  *this, asm_call_info))));
    ProcessBindingsResults<AsmCallInfo>(type_wrapper<typename AsmCallInfo::Bindings>());
    return true;
  }

  template <typename ArgBinding, typename AsmCallInfo>
  auto /*MakeTuplefromBindingsClient*/ operator()(ArgTraits<ArgBinding>, AsmCallInfo) {
    static constexpr const auto& arg_info = ArgTraits<ArgBinding>::arg_info;
    if constexpr (arg_info.arg_type == ArgInfo::IMM_ARG) {
      auto imm = std::get<arg_info.from>(input_args_);
      return std::tuple{imm};
    } else {
      return ProcessArgInput<ArgBinding, AsmCallInfo>();
    }
  }

  template <typename ArgBinding, typename AsmCallInfo>
  auto ProcessArgInput() {
    static constexpr const auto& arg_info = ArgTraits<ArgBinding>::arg_info;
    using RegisterClass = typename ArgTraits<ArgBinding>::RegisterClass;
    using Usage = typename ArgTraits<ArgBinding>::Usage;
    static constexpr const auto kNumOut = std::tuple_size_v<typename AsmCallInfo::OutputArguments>;

    if constexpr (arg_info.arg_type == ArgInfo::IN_ARG) {
      static_assert(std::is_same_v<Usage, intrinsics::bindings::Use>);
      static_assert(!RegisterClass::kIsImplicitReg);
      if constexpr (RegisterClass::kAsRegister == 'x' &&
                    std::is_same_v<std::tuple_element_t<arg_info.from, std::tuple<ArgType...>>,
                                   MachineReg>) {
        auto xmm_reg = AllocVReg();
        MovFromInput<RegisterClass>(builder_, xmm_reg, std::get<arg_info.from>(input_args_));
        return std::tuple{xmm_reg};
      } else {
        return std::tuple{std::get<arg_info.from>(input_args_)};
      }
    } else if constexpr (arg_info.arg_type == ArgInfo::IN_OUT_ARG) {
      static_assert(!std::is_same_v<ResType, std::monostate>);
      static_assert(std::is_same_v<Usage, intrinsics::bindings::UseDef>);
      static_assert(!RegisterClass::kIsImplicitReg);
      if constexpr (RegisterClass::kAsRegister == 'x') {
        if constexpr (kNumOut > 1) {
          static_assert(kDependentTypeFalse<ArgTraits<ArgBinding>>);
        } else {
          CHECK(xmm_result_reg_.IsInvalidReg());
          xmm_result_reg_ = AllocVReg();
          MovFromInput<RegisterClass>(
              builder_, xmm_result_reg_, std::get<arg_info.from>(input_args_));
          return std::tuple{xmm_result_reg_};
        }
      } else if constexpr (kNumOut > 1) {
        auto res = std::get<arg_info.to>(result_);
        MovFromInput<RegisterClass>(builder_, res, std::get<arg_info.from>(input_args_));
        return std::tuple{res};
      } else {
        MovFromInput<RegisterClass>(builder_, result_, std::get<arg_info.from>(input_args_));
        return std::tuple{result_};
      }
    } else if constexpr (arg_info.arg_type == ArgInfo::IN_TMP_ARG) {
      if constexpr (RegisterClass::kIsImplicitReg) {
        auto implicit_reg = AllocVReg();
        MovFromInput<RegisterClass>(builder_, implicit_reg, std::get<arg_info.from>(input_args_));
        return std::tuple{implicit_reg};
      } else {
        static_assert(std::is_same_v<Usage, intrinsics::bindings::UseDef>);
        return std::tuple{std::get<arg_info.from>(input_args_)};
      }
    } else if constexpr (arg_info.arg_type == ArgInfo::OUT_ARG) {
      static_assert(!std::is_same_v<ResType, std::monostate>);
      static_assert(std::is_same_v<Usage, intrinsics::bindings::Def> ||
                    std::is_same_v<Usage, intrinsics::bindings::DefEarlyClobber>);
      if constexpr (RegisterClass::kAsRegister == 'x') {
        CHECK(xmm_result_reg_.IsInvalidReg());
        xmm_result_reg_ = AllocVReg();
        return std::tuple{xmm_result_reg_};
      } else if constexpr (kNumOut > 1) {
        return std::tuple{std::get<arg_info.to>(result_)};
      } else if constexpr (RegisterClass::kIsImplicitReg) {
        if constexpr (RegisterClass::kAsRegister == 0) {
          return std::tuple{flag_register_};
        } else {
          CHECK(implicit_result_reg_.IsInvalidReg());
          implicit_result_reg_ = AllocVReg();
          return std::tuple{implicit_result_reg_};
        }
      } else {
        return std::tuple{result_};
      }
    } else if constexpr (arg_info.arg_type == ArgInfo::TMP_ARG) {
      static_assert(std::is_same_v<Usage, intrinsics::bindings::Def> ||
                    std::is_same_v<Usage, intrinsics::bindings::DefEarlyClobber>);
      if constexpr (RegisterClass::kAsRegister == 'm') {
        static_assert(std::is_same_v<Usage, intrinsics::bindings::DefEarlyClobber>);
        if (scratch_arg_ >= 2) {
          FATAL("Only two scratch registers are supported for now");
        }
        return std::tuple{x86_64::kMachineRegRBP,
                          static_cast<int32_t>(offsetof(ThreadState, intrinsics_scratch_area) +
                                               config::kScratchAreaSlotSize * scratch_arg_++)};
      } else if constexpr (RegisterClass::kIsImplicitReg) {
        if constexpr (RegisterClass::kAsRegister == 0) {
          return std::tuple{flag_register_};
        } else {
          return std::tuple{};
        }
      } else {
        auto reg = AllocVReg();
        return std::tuple{reg};
      }
    } else {
      static_assert(berberis::kDependentValueFalse<arg_info.arg_type>);
    }
  }

  template <typename T>
  struct type_wrapper {
    using type = T;
  };

  template <typename AsmCallInfo, typename... ArgBinding>
  void ProcessBindingsResults(type_wrapper<std::tuple<ArgBinding...>>) {
    (ProcessBindingResult<ArgBinding, AsmCallInfo>(), ...);
    if constexpr (std::tuple_size_v<typename AsmCallInfo::OutputArguments> == 0) {
      // No return value. Do nothing.
    } else if constexpr (std::tuple_size_v<typename AsmCallInfo::OutputArguments> == 1) {
      using ReturnType = std::tuple_element_t<0, typename AsmCallInfo::OutputArguments>;
      if constexpr (std::is_integral_v<ReturnType> && sizeof(ReturnType) < sizeof(int32_t)) {
        // Don't handle these types just yet. We are not sure how to expand them and there
        // are no examples.
        static_assert(kDependentTypeFalse<ReturnType>);
      }
      if constexpr (std::is_same_v<ReturnType, int32_t> || std::is_same_v<ReturnType, uint32_t>) {
        // Expands 32 bit values as signed. Even if actual results are processed as unsigned!
        // TODO(b/308951522) replace with Expand node when it's created.
        builder_->Gen<x86_64::MovsxlqRegReg>(result_, result_);
      } else if constexpr (std::is_integral_v<ReturnType> &&
                           sizeof(ReturnType) == sizeof(int64_t)) {
        // Do nothing, we have already produced expanded value.
      } else if constexpr (std::is_same_v<ReturnType, intrinsics::Float32> ||
                           std::is_same_v<ReturnType, intrinsics::Float64>) {
        // Do nothing, NaN boxing is handled by semantics player.
      } else {
        static_assert(kDependentTypeFalse<ReturnType>);
      }
    } else {
      static_assert(kDependentTypeFalse<typename AsmCallInfo::OutputArguments>);
    }
  }

  template <typename ArgBinding, typename AsmCallInfo>
  void ProcessBindingResult() {
    if constexpr (ArgTraits<ArgBinding>::Class::kIsImmediate) {
      return;
    } else {
      using RegisterClass = typename ArgTraits<ArgBinding>::RegisterClass;
      static constexpr const auto& arg_info = ArgTraits<ArgBinding>::arg_info;
      if constexpr (RegisterClass::kAsRegister == 'm' || RegisterClass::kAsRegister == 0) {
        return;
      } else if constexpr ((arg_info.arg_type == ArgInfo::IN_OUT_ARG ||
                            arg_info.arg_type == ArgInfo::OUT_ARG) &&
                           RegisterClass::kAsRegister == 'x') {
        CHECK(!xmm_result_reg_.IsInvalidReg());
        MovToResult<RegisterClass>(builder_, result_, xmm_result_reg_);
      } else if constexpr (arg_info.arg_type == ArgInfo::OUT_ARG && RegisterClass::kIsImplicitReg) {
        CHECK(!implicit_result_reg_.IsInvalidReg());
        MovToResult<RegisterClass>(builder_, result_, implicit_result_reg_);
      }
    }
  }

  MachineReg AllocVReg() { return builder_->ir()->AllocVReg(); }

  template <typename T>
  static constexpr auto UnwrapSimdReg(T r) {
    if constexpr (std::is_same_v<T, SimdReg>) {
      return r.machine_reg();
    } else {
      return r;
    }
  }

  template <typename... T>
  static constexpr auto UnwrapSimdReg(std::tuple<T...> regs) {
    constexpr const auto num_args = std::tuple_size<std::tuple<T...>>::value;
    return UnwrapSimdReg(std::make_index_sequence<num_args>(), regs);
  }

  template <typename... T, auto... I>
  static constexpr auto UnwrapSimdReg(std::index_sequence<I...>, std::tuple<T...> regs) {
    return std::make_tuple(UnwrapSimdReg(std::get<I>(regs))...);
  }

 private:
  x86_64::MachineIRBuilder* builder_;
  ResType result_;
  MachineReg xmm_result_reg_;
  MachineReg implicit_result_reg_;
  FlagRegister flag_register_;
  std::tuple<ArgType...> input_args_;
  uint32_t scratch_arg_ = 0;
  bool success_;
};

template <auto kFunction, typename ResType, typename FlagRegister, typename... ArgType>
bool TryInlineIntrinsicForHeavyOptimizer(x86_64::MachineIRBuilder* builder,
                                         ResType result,
                                         FlagRegister flag_register,
                                         ArgType... args) {
  if (InlineIntrinsic<kFunction>::TryInline(builder, result, flag_register, args...)) {
    return true;
  }

  return TryBindingBasedInlineIntrinsicForHeavyOptimizer<kFunction,
                                                         ResType,
                                                         FlagRegister,
                                                         ArgType...>(
      builder, result, flag_register, args...);
}

template <auto kFunction, typename ResType, typename FlagRegister, typename... ArgType>
void InlineIntrinsicForHeavyOptimizer(x86_64::MachineIRBuilder* builder,
                                      ResType result,
                                      FlagRegister flag_register,
                                      ArgType... args) {
  bool success = TryInlineIntrinsicForHeavyOptimizer<kFunction, ResType, FlagRegister, ArgType...>(
      builder, result, flag_register, args...);
  CHECK(success);
}

template <auto kFunction, typename FlagRegister, typename... ArgType>
bool TryInlineIntrinsicForHeavyOptimizerVoid(x86_64::MachineIRBuilder* builder,
                                             FlagRegister flag_register,
                                             ArgType... args) {
  if (InlineIntrinsic<kFunction>::TryInline(builder, flag_register, args...)) {
    return true;
  }

  return TryBindingBasedInlineIntrinsicForHeavyOptimizer<kFunction,
                                                         std::monostate,
                                                         FlagRegister,
                                                         ArgType...>(
      builder, std::monostate{}, flag_register, args...);
}

template <auto kFunction, typename FlagRegister, typename... ArgType>
void InlineIntrinsicForHeavyOptimizerVoid(x86_64::MachineIRBuilder* builder,
                                          FlagRegister flag_register,
                                          ArgType... args) {
  bool success = TryInlineIntrinsicForHeavyOptimizerVoid<kFunction, FlagRegister, ArgType...>(
      builder, flag_register, args...);
  CHECK(success);
}

}  // namespace berberis

#endif  // BERBERIS_HEAVY_OPTIMIZER_RISCV64_INLINE_INTRINSIC_H_
