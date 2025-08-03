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

#ifndef BERBERIS_BACKEND_X86_64_MACHINE_INSN_INTRINSICS_H_
#define BERBERIS_BACKEND_X86_64_MACHINE_INSN_INTRINSICS_H_

#include <string>
#include <tuple>
#include <type_traits>
#include <variant>

#include "berberis/backend/code_emitter.h"
#include "berberis/backend/common/machine_ir.h"
#include "berberis/backend/x86_64/code_debug.h"
#include "berberis/backend/x86_64/code_emit.h"
#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir_builder.h"
#include "berberis/base/dependent_false.h"
#include "berberis/base/stringprintf.h"
#include "berberis/intrinsics/intrinsics_args.h"
#include "berberis/intrinsics/intrinsics_bindings.h"

namespace berberis::x86_64 {

// tuple_cat for types, to help remove filtered out types below.
template <typename... Ts>
using tuple_cat_t = decltype(std::tuple_cat(std::declval<Ts>()...));

// Predicate to determine whether type T has a RegisterClass alias.
template <class, class = void>
struct has_reg_class_impl : std::false_type {};
template <class T>
struct has_reg_class_impl<T, std::void_t<typename T::RegisterClass>> : std::true_type {};
template <typename T>
using has_reg_class_t = has_reg_class_impl<T>;

// Filter out types from Ts... that do not satisfy the predicate, collect them
// into a tuple.
template <template <typename> typename Predicate, typename... Ts>
using filter_t =
    tuple_cat_t<std::conditional_t<Predicate<Ts>::value, std::tuple<Ts>, std::tuple<>>...>;

// Convert Binding into constructor argument(s).
template <typename T, typename = void>
struct ConstructorArg;

// Immediates expand into their class type.
template <typename T>
struct ConstructorArg<ArgTraits<T>, std::enable_if_t<ArgTraits<T>::Class::kIsImmediate, void>> {
  using type = std::tuple<typename ArgTraits<T>::Class::Type>;
};

// Mem ops expand into base register and disp.
template <typename T>
struct ConstructorArg<ArgTraits<T>,
                      std::enable_if_t<!ArgTraits<T>::Class::kIsImmediate &&
                                           ArgTraits<T>::RegisterClass::kAsRegister == 'm',
                                       void>> {
  static_assert(
      std::is_same_v<typename ArgTraits<T>::Usage, intrinsics::bindings::DefEarlyClobber>);
  // Need to emit base register AND disp.
  using type = std::tuple<MachineReg, int32_t>;
};

// Everything else expands into a MachineReg.
template <typename T>
struct ConstructorArg<ArgTraits<T>,
                      std::enable_if_t<!ArgTraits<T>::Class::kIsImmediate &&
                                           ArgTraits<T>::RegisterClass::kAsRegister != 'm',
                                       void>> {
  using type = std::tuple<MachineReg>;
};

template <typename T>
using constructor_one_arg_t = typename ConstructorArg<ArgTraits<T>>::type;

// Use this alias to generate constructor Args from bindings via the AsmCallInfo::MachineInsn
// alias. The tuple args will be extracted by the tuple specialization on MachineInsn below.
template <typename... T>
using constructor_args_t = tuple_cat_t<constructor_one_arg_t<T>...>;

// Predicate to determine whether type T is a memory access arg.
template <class, class = void>
struct is_mem_impl : std::false_type {};
template <class T>
struct is_mem_impl<
    T,
    std::enable_if_t<!T::Class::kIsImmediate && T::RegisterClass::kAsRegister == 'm', void>>
    : std::true_type {};
template <typename T>
using is_mem_t = is_mem_impl<T>;

template <typename... Bindings>
constexpr size_t mem_count_v = std::tuple_size_v<filter_t<is_mem_t, ArgTraits<Bindings>...>>;

template <size_t N, typename... Bindings>
constexpr bool has_n_mem_v = mem_count_v<Bindings...> > (N - 1);

template <typename AsmCallInfo, auto kMnemo, auto kOpcode, typename Args, typename... Bindings>
class MachineInsn;

// Use specialization to extract the tuple parameter pack generated from constructor_args_t above.
template <typename AsmCallInfo,
          auto kMnemo,
          auto kOpcode,
          typename... CtorArgs,
          typename... Bindings>
class MachineInsn<AsmCallInfo, kMnemo, kOpcode, std::tuple<CtorArgs...>, Bindings...> final
    : public MachineInsnX86_64 {
 private:
  template <typename>
  struct GenMachineInsnInfoT;
  // We want to filter out any bindings that are not used for Register args.
  using RegBindings = filter_t<has_reg_class_t, ArgTraits<Bindings>...>;

 public:
  // This static simplifies constructing this MachineInsn in intrinsic implementations.
  static constexpr MachineInsn* (MachineIRBuilder::*kGenFunc)(CtorArgs...) =
      &MachineIRBuilder::template Gen<MachineInsn>;

  explicit MachineInsn(CtorArgs... args) : MachineInsnX86_64(&kInfo) {
    ProcessArgs<0 /* reg_idx */, 0 /* mem_idx */, false /* is_disp */, Bindings...>(args...);
  }

  static constexpr MachineInsnInfo kInfo = GenMachineInsnInfoT<RegBindings>::value;

  static constexpr int NumRegOperands() { return kInfo.num_reg_operands; }
  static constexpr const MachineRegKind& RegKindAt(int i) { return kInfo.reg_kinds[i]; }

  std::string GetDebugString() const override {
    std::string s(kMnemo);
    ProcessDebugString<Bindings...>(&s);
    return s;
  }

  void Emit(CodeEmitter* as) const override {
    std::apply(AsmCallInfo::kMacroInstruction,
               std::tuple_cat(std::tuple<CodeEmitter&>{*as},
                              EmitArgs<0 /* reg_idx */, 0 /* mem_idx */, Bindings...>()));
  }

  int32_t disp2() const { return disp2_; }
  void set_disp2(int32_t val) { disp2_ = val; }

 private:
  int32_t disp2_;

  // TODO(b/260725458): Use inline template lambda instead after C++20 becomes available.
  template <size_t, size_t, bool, typename...>
  void ProcessArgs() {}

  template <size_t reg_idx,
            size_t mem_idx,
            bool is_disp,
            typename B,
            typename... BindingsRest,
            typename T,
            typename... Args>
  void ProcessArgs(T arg, Args... args) {
    if constexpr (ArgTraits<B>::Class::kIsImmediate) {
      this->set_imm(arg);
      ProcessArgs<reg_idx, mem_idx, false, BindingsRest..., Args...>(args...);
    } else if constexpr (ArgTraits<B>::RegisterClass::kAsRegister == 'm' && !is_disp) {
      // Only tmp memory args are supported.
      static_assert(ArgTraits<B>::arg_info.arg_type == ArgInfo::TMP_ARG);
      this->SetRegAt(reg_idx, arg);
      // Note that mem is non incr'ed. We want to process the disp portion next. This is
      // why `is_disp` is set to `true` here and we keep the binding `B`. This can't be done
      // in a single pass because they're each a different `arg`.
      ProcessArgs<reg_idx + 1, mem_idx, true, B, BindingsRest..., Args...>(args...);
    } else if constexpr (ArgTraits<B>::RegisterClass::kAsRegister == 'm' && is_disp) {
      static_assert(ArgTraits<B>::arg_info.arg_type == ArgInfo::TMP_ARG);
      if constexpr (mem_idx == 0) {
        this->set_disp(arg);
      } else if constexpr (mem_idx == 1) {
        this->set_disp2(arg);
      }
      // We finished processing the mem binding, reset is_disp and incr mem_idx.
      ProcessArgs<reg_idx, mem_idx + 1, false, BindingsRest..., Args...>(args...);
    } else if constexpr (std::is_same_v<MachineReg, T>) {
      this->SetRegAt(reg_idx, arg);
      ProcessArgs<reg_idx + 1, mem_idx, false, BindingsRest..., Args...>(args...);
    } else {
      static_assert(kDependentTypeFalse<T>);
    }
  }

  static constexpr auto GetInsnKind() {
    if constexpr (AsmCallInfo::kSideEffects) {
      return kMachineInsnSideEffects;
    } else {
      return kMachineInsnDefault;
    }
  }

  template <typename T, typename = void>
  struct RegInfo;
  template <typename T>
  struct RegInfo<T, std::enable_if_t<T::RegisterClass::kAsRegister != 'm', void>> {
    static constexpr auto kRegClass = &T::RegisterClass::template kRegClass<MachineInsnX86_64>;
    static constexpr auto kRegKind =
        intrinsics::bindings::kRegKind<typename T::Usage, berberis::MachineRegKind>;
  };
  template <typename T>
  struct RegInfo<T, std::enable_if_t<T::RegisterClass::kAsRegister == 'm', void>> {
    static_assert(std::is_same_v<typename T::Usage, intrinsics::bindings::DefEarlyClobber>);
    static constexpr auto kRegClass = &kGeneralReg32;
    static constexpr auto kRegKind = MachineRegKind::kUse;
  };

  template <typename... T>
  struct GenMachineInsnInfoT<std::tuple<T...>> {
    static constexpr MachineInsnInfo value = MachineInsnInfo(
        {kOpcode, sizeof...(T), {{RegInfo<T>::kRegClass, RegInfo<T>::kRegKind}...}, GetInsnKind()});
  };

  template <typename... Args>
  void ProcessDebugString(std::string* s) const {
    *s +=
        " " + ProcessDebugStringArgs<0 /* arg_idx */, 0 /* reg_idx */, 0 /* mem_idx */, Args...>();
    if (this->recovery_pc()) {
      *s += StringPrintf(" <0x%" PRIxPTR ">", this->recovery_pc());
    }
  }

  // TODO(b/260725458): Use inline template lambda instead after C++20 becomes available.
  template <>
  void ProcessDebugString<>(std::string*) const {}

  template <size_t arg_idx, size_t reg_idx, size_t mem_idx, typename T, typename... Args>
  std::string ProcessDebugStringArgs() const {
    std::string prefix;
    if constexpr (arg_idx > 0) {
      prefix = ", ";
    }
    if constexpr (ArgTraits<T>::Class::kIsImmediate) {
      return prefix + GetImmOperandDebugString(this) +
             ProcessDebugStringArgs<arg_idx + 1, reg_idx, mem_idx, Args...>();
    } else if constexpr (ArgTraits<T>::Class::kAsRegister == 'm') {
      if constexpr (mem_idx == 0) {
        return prefix + GetBaseDispMemOperandDebugString(this, reg_idx) +
               ProcessDebugStringArgs<arg_idx + 1, reg_idx + 1, mem_idx + 1, Args...>();
      } else if constexpr (mem_idx == 1) {
        return prefix +
               StringPrintf(
                   "[%s + 0x%x]", GetRegOperandDebugString(this, reg_idx).c_str(), disp2()) +
               ProcessDebugStringArgs<arg_idx + 1, reg_idx + 1, mem_idx + 1, Args...>();
      } else {
        static_assert(kDependentValueFalse<mem_idx>);
      }
    } else if constexpr (ArgTraits<T>::RegisterClass::kIsImplicitReg) {
      return prefix + GetImplicitRegOperandDebugString(this, reg_idx) +
             ProcessDebugStringArgs<arg_idx + 1, reg_idx + 1, mem_idx, Args...>();
    } else {
      return prefix + GetRegOperandDebugString(this, reg_idx) +
             ProcessDebugStringArgs<arg_idx + 1, reg_idx + 1, mem_idx, Args...>();
    }
  }

  template <size_t, size_t, size_t>
  std::string ProcessDebugStringArgs() const {
    return "";
  }

  // TODO(b/260725458): Use inline template lambda instead after C++20 becomes available.
  template <size_t, size_t>
  auto EmitArgs() const {
    return std::tuple{};
  }

  template <size_t reg_idx, size_t mem_idx, typename T, typename... Args>
  auto EmitArgs() const {
    if constexpr (ArgTraits<T>::Class::kIsImmediate) {
      return std::tuple_cat(
          std::tuple{static_cast<constructor_one_arg_t<T>>(MachineInsnX86_64::imm())},
          EmitArgs<reg_idx, mem_idx, Args...>());
    } else if constexpr (ArgTraits<T>::RegisterClass::kAsRegister == 'x') {
      return std::tuple_cat(std::tuple{GetXReg(this->RegAt(reg_idx))},
                            EmitArgs<reg_idx + 1, mem_idx, Args...>());
    } else if constexpr (ArgTraits<T>::RegisterClass::kAsRegister == 'r' ||
                         ArgTraits<T>::RegisterClass::kAsRegister == 'q') {
      return std::tuple_cat(std::tuple{GetGReg(this->RegAt(reg_idx))},
                            EmitArgs<reg_idx + 1, mem_idx, Args...>());
    } else if constexpr (ArgTraits<T>::RegisterClass::kAsRegister == 'm' &&
                         std::is_same_v<typename ArgTraits<T>::Usage,
                                        intrinsics::bindings::DefEarlyClobber>) {
      if constexpr (mem_idx == 0) {
        return std::tuple_cat(std::tuple{Assembler::Operand{.base = GetGReg(this->RegAt(reg_idx)),
                                                            .disp = static_cast<int32_t>(disp())}},
                              EmitArgs<reg_idx + 1, mem_idx + 1, Args...>());
      } else if constexpr (mem_idx == 1) {
        return std::tuple_cat(std::tuple{Assembler::Operand{.base = GetGReg(this->RegAt(reg_idx)),
                                                            .disp = static_cast<int32_t>(disp2())}},
                              EmitArgs<reg_idx + 1, mem_idx + 1, Args...>());
      } else {
        static_assert(kDependentTypeFalse<T>);
      }
    } else if constexpr (ArgTraits<T>::RegisterClass::kIsImplicitReg) {
      return EmitArgs<reg_idx, mem_idx, Args...>();
    } else {
      static_assert(kDependentTypeFalse<T>);
    }
  }
};

}  // namespace berberis::x86_64

#endif  // BERBERIS_BACKEND_X86_64_MACHINE_INSN_INTRINSICS_H_
