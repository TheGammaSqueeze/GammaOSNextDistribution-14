/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef BERBERIS_INTRINSICS_INTRINSICS_ARGS_H_
#define BERBERIS_INTRINSICS_INTRINSICS_ARGS_H_

#include <cstddef>
#include <cstdio>

#include "berberis/base/checks.h"

namespace berberis {

// Helper classes for the EmbedAsmInstruction "construction class".
//
// Constructor of this class takes prescribed arguments from IR insn and
// calls a single assembler [macro]instruction.  It creates required scratch
// register allocations and proper MOVs to preserve semantic of the IR insn.
//
// Name of a helper class describes argument of the assembler
// [macro]instruction.
//
// You could find many examples in intrinsics_x86.h file but EmbedAsmInstruction
// is not x86-specific: in theory it should work with any MachineIRBuilder.
//
// Each argument must by of one of the following types:
//
//   InArg<N> - argument comes from <N>th source of the IR insn.
//              Must be "use" argument of the assembler [macro]instruction.
//              Note: please don't use this argument for specific register
//              classes (such as "RDX" or "RCX").  If one operation returns
//              result, e.g., in "RCX" and another one accepts it in "RCX"
//              then register allocator will not be able to satisfy such
//              requirements.  Use InTmpArg<N> for such instructions.
//
//   OutArg<N> - argument comes from <N>th destination of the IR insn.
//               Must be "def" or "def_early_clobber" argument of the assembler
//               [macro]instruction.
//
//   OutTmpArg<N> - argument is copied from temporary register to <N>th
//                  destination of the IR insn.
//                  Must be "def" or "def_early_clobber" argument of the
//                  assembler [macro]instruction.
//
//   InOutArg<N, M> - argument is copied from <N>th source of the IR insn to
//                    <M>th destination of the IR insn, then is passed it to the
//                    [macro]instruction.
//                    Must be "use_def" argument of the assembler
//                    [macro]instruction.
//
//   InOutTmpArg<N, M> - argument is copied from <N>th source of the IR insn to
//                       temporary register, then it's passed to the
//                       [macro]instruction. Result is copied to the
//                       <M>th destination of the IR insn.
//                       Must be "use_def" argument of the assembler
//                       [macro]instruction.
//
//   InTmpArg<N> - argument is copied from <N>th source of the IR insn to the
//                 temporary register, then is passed to the [macro]instruction.
//                 Must be "use_def" argument of the assembler
//                 [macro]instruction.
//
//   ImmArg<N, uintXX_t> - argument is 8bit/16bit/32bit/64bit immediate and
//                         comes as <N>th source of IR insn.
//
//   TmpArg - argument is temporary register allocated for the
//            [macro]instruction.
//
// TODO(khim): investigate feasibility of adding unconditional copying of
// arguments and results. This way we could remove classes InOutTmpArg/InTmpArg
// and, more importantly, make sure InArg vs InTmpArg mixup will not lead to
// hard to debug errors.

struct ArgInfo {
 public:
  enum ArgType {
    IN_ARG,
    IN_TMP_ARG,
    OUT_ARG,
    OUT_TMP_ARG,
    IN_OUT_ARG,
    IN_OUT_TMP_ARG,
    TMP_ARG,
    IMM_ARG
  } arg_type;
  friend constexpr bool HaveInput(const ArgInfo& arg) {
    return arg.arg_type == ArgInfo::IN_ARG ||
           arg.arg_type == ArgInfo::IN_TMP_ARG ||
           arg.arg_type == ArgInfo::IN_OUT_ARG ||
           arg.arg_type == ArgInfo::IN_OUT_TMP_ARG;
  }
  friend constexpr bool HaveOutput(const ArgInfo& arg) {
    return arg.arg_type == ArgInfo::IN_OUT_ARG ||
           arg.arg_type == ArgInfo::IN_OUT_TMP_ARG ||
           arg.arg_type == OUT_ARG ||
           arg.arg_type == OUT_TMP_ARG;
  }
  friend constexpr bool IsImmediate(const ArgInfo& arg) {
    return arg.arg_type == IMM_ARG;
  }
  friend constexpr bool IsTemporary(const ArgInfo& arg) {
    return arg.arg_type == TMP_ARG;
  }
  const int from = 0;
  const int to = 0;
};

template <int N, typename RegisterClass = void, typename Usage = void>
class InArg;

template <int N, typename RegisterClass = void, typename Usage = void>
class OutArg;

template <int N, typename RegisterClass = void, typename Usage = void>
class OutTmpArg;

template <int N, int M, typename RegisterClass = void, typename Usage = void>
class InOutArg;

template <int N, int M, typename RegisterClass = void, typename Usage = void>
class InOutTmpArg;

template <int N, typename RegisterClass = void, typename Usage = void>
class InTmpArg;

template <int N, typename ImmType, typename ImmediateClass = void>
class ImmArg;

template <typename RegisterClass = void, typename Usage = void>
class TmpArg;

template <typename ArgInfo>
class ArgTraits;

template <int N, typename RegisterClassType, typename UsageType>
class ArgTraits<InArg<N, RegisterClassType, UsageType>> {
 public:
  using Class = RegisterClassType;
  using RegisterClass = RegisterClassType;
  using Usage = UsageType;
  static constexpr ArgInfo arg_info{.arg_type = ArgInfo::IN_ARG, .from = N};
};

template <int N, typename RegisterClassType, typename UsageType>
class ArgTraits<OutArg<N, RegisterClassType, UsageType>> {
 public:
  using Class = RegisterClassType;
  using RegisterClass = RegisterClassType;
  using Usage = UsageType;
  static constexpr ArgInfo arg_info{.arg_type = ArgInfo::OUT_ARG, .to = N};
};

template <int N, typename RegisterClassType, typename UsageType>
class ArgTraits<OutTmpArg<N, RegisterClassType, UsageType>> {
 public:
  using Class = RegisterClassType;
  using RegisterClass = RegisterClassType;
  using Usage = UsageType;
  static constexpr ArgInfo arg_info{.arg_type = ArgInfo::OUT_TMP_ARG, .to = N};
};

template <int N, int M, typename RegisterClassType, typename UsageType>
class ArgTraits<InOutArg<N, M, RegisterClassType, UsageType>> {
 public:
  using Class = RegisterClassType;
  using RegisterClass = RegisterClassType;
  using Usage = UsageType;
  static constexpr ArgInfo arg_info{.arg_type = ArgInfo::IN_OUT_ARG, .from = N, .to = M};
};

template <int N, int M, typename RegisterClassType, typename UsageType>
class ArgTraits<InOutTmpArg<N, M, RegisterClassType, UsageType>> {
 public:
  using Class = RegisterClassType;
  using RegisterClass = RegisterClassType;
  using Usage = UsageType;
  static constexpr ArgInfo arg_info{.arg_type = ArgInfo::IN_OUT_TMP_ARG, .from = N, .to = M};
};

template <int N, typename RegisterClassType, typename UsageType>
class ArgTraits<InTmpArg<N, RegisterClassType, UsageType>> {
 public:
  using Class = RegisterClassType;
  using RegisterClass = RegisterClassType;
  using Usage = UsageType;
  static constexpr ArgInfo arg_info{.arg_type = ArgInfo::IN_TMP_ARG, .from = N};
};

template <int N, typename ImmType, typename ImmediateClassType>
class ArgTraits<ImmArg<N, ImmType, ImmediateClassType>> {
 public:
  using Class = ImmediateClassType;
  using ImmediateClass = ImmediateClassType;
  static constexpr ArgInfo arg_info{.arg_type = ArgInfo::IMM_ARG, .from = N};
};

template <typename RegisterClassType, typename UsageType>
class ArgTraits<TmpArg<RegisterClassType, UsageType>> {
 public:
  using Class = RegisterClassType;
  using RegisterClass = RegisterClassType;
  using Usage = UsageType;
  static constexpr ArgInfo arg_info{.arg_type = ArgInfo::TMP_ARG};
};

// We couldn't use standard "throw std::logic_error(...)" approach here because that code is
// compiled with -fno-exceptions.  Thankfully printf(...) produces very similar error messages.
//
// See https://stackoverflow.com/questions/8626055/c11-static-assert-within-constexpr-function
// is you need an explanation for how basic technique works.
template <typename MachineInsn, int arguments_count>
constexpr bool IsCompatible(const ArgInfo* arguments) {
  int reg_arguments = 0;
  for (size_t argument = 0; argument < arguments_count; ++argument) {
    if (arguments[argument].arg_type != ArgInfo::IMM_ARG) {
      if ((arguments[argument].arg_type == ArgInfo::IN_ARG) &&
          MachineInsn::RegKindAt(reg_arguments).IsDef()) {
        fprintf(stderr, "Incorrect use of InArg for argument %d", argument);
        return false;
      } else if ((arguments[argument].arg_type == ArgInfo::IN_TMP_ARG) &&
                 !MachineInsn::RegKindAt(reg_arguments).IsDef() &&
                 !IsFixedRegClass(MachineInsn::RegKindAt(reg_arguments).RegClass())) {
        fprintf(stderr, "Inefficient use of InTmpArg for argument %d", argument);
        return false;
      } else if ((arguments[argument].arg_type == ArgInfo::OUT_ARG) &&
                 IsFixedRegClass(MachineInsn::RegKindAt(reg_arguments).RegClass())) {
        fprintf(stderr, "Incorrect use of OutArg for argument %d", argument);
        return false;
      } else if ((arguments[argument].arg_type == ArgInfo::OUT_TMP_ARG) &&
                 !IsFixedRegClass(MachineInsn::RegKindAt(reg_arguments).RegClass())) {
        fprintf(stderr, "Inefficient use of OutTmpArg for argument %d", argument);
        return false;
      } else if ((arguments[argument].arg_type == ArgInfo::IN_OUT_ARG) &&
                 IsFixedRegClass(MachineInsn::RegKindAt(reg_arguments).RegClass())) {
        fprintf(stderr, "Incorrect use of InOutArg for argument %d", argument);
        return false;
      } else if ((arguments[argument].arg_type == ArgInfo::IN_OUT_TMP_ARG) &&
                 !IsFixedRegClass(MachineInsn::RegKindAt(reg_arguments).RegClass())) {
        fprintf(stderr, "Inefficient use of InOutTmpArg for argument %d", argument);
        return false;
      }
      if (HaveInput(arguments[argument]) &&
          !MachineInsn::RegKindAt(reg_arguments).IsInput()) {
        fprintf(stderr, "Argument %d does not accept input!", argument);
        return false;
      } else if (!HaveInput(arguments[argument]) &&
                 MachineInsn::RegKindAt(reg_arguments).IsInput()) {
        fprintf(stderr, "Argument %d requires valid input!", argument);
        return false;
      }
      ++reg_arguments;
    }
  }
  if (MachineInsn::NumRegOperands() != reg_arguments) {
    fprintf(stderr,
            "expected %d arguments, got %d arguments",
            MachineInsn::NumRegOperands(),
            reg_arguments);
    return false;
  }
  return true;
}

template <typename MachineInsn, typename... Args>
constexpr bool IsCompatible() {
  const ArgInfo arguments[] = {ArgTraits<Args>::arg_info...};
  // Note: we couldn't pass arguments as an array into IsCompatible by reference
  // because this would cause compilation error in case where we have no arguments.
  //
  // Pass pointer and element count instead.
  return IsCompatible<MachineInsn, sizeof...(Args)>(arguments);
}

template <typename MachineIRBuilder, typename Arg>
class ArgGetterSetter;

template <typename Instruction, typename... Args>
class EmbedAsmInstruction {
 public:
  template <typename MachineIRBuilder, typename IntrinsicInsn>
  EmbedAsmInstruction(MachineIRBuilder* builder, const IntrinsicInsn* insn) {
    static_assert(IsCompatible<Instruction, Args...>(), "Incompatible intrinsic embedding");
    builder->template Gen<Instruction>(ArgGetterSetter<MachineIRBuilder, Args>(builder, insn)...);
  }
};

}  // namespace berberis

#endif  // BERBERIS_INTRINSICS_INTRINSICS_ARGS_H_
